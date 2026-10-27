<a id="back-to-top"></a>

# Tuần tự hóa nâng cao

## Menu
- [writeObject, readObject và defaultWriteObject, defaultReadObject](#advanced-serialization-001)
- [readObjectNoData và thay đổi hệ phân cấp](#advanced-serialization-002)
- [serialPersistentFields và điều khiển dạng lưu trữ](#advanced-serialization-003)
- [writeReplace, readResolve và thay thế đối tượng](#advanced-serialization-004)
- [Externalizable, writeExternal và readExternal](#advanced-serialization-005)
- [Yêu cầu constructor public không tham số của Externalizable](#advanced-serialization-006)
- [Serializable với enum và record](#advanced-serialization-007)
- [@Serial và kiểm tra khai báo thành viên đặc biệt](#advanced-serialization-008)
- [Kiểm tra bất biến sau khi khôi phục đối tượng](#advanced-serialization-009)
- [ObjectInputFilter: giới hạn lớp, độ sâu, số tham chiếu và độ dài mảng](#advanced-serialization-010)
- [Filter theo stream, cấu hình JVM và phụ thuộc phiên bản JDK](#advanced-serialization-011)
- [Không khôi phục trực tiếp dữ liệu đối tượng không tin cậy](#advanced-serialization-012)

## <a id="advanced-serialization-001">writeObject, readObject và defaultWriteObject, defaultReadObject</a>

<details>
<summary>Xem chi tiết</summary>

# Java Serialization nâng cao: writeObject, readObject, defaultWriteObject và defaultReadObject

Java Object Serialization chuyển một **đồ thị đối tượng** thành byte stream rồi xây dựng lại đối tượng khi đọc. Với các lớp `Serializable`, JVM thường tự lưu những instance fields đủ điều kiện. Tuy nhiên, có lúc ta muốn **bổ sung dữ liệu**, **khôi phục trạng thái `transient`**, **kiểm tra bất biến nghiệp vụ** hoặc **chủ động quản lý khả năng tương thích định dạng**. Khi ấy, Java cung cấp cơ chế tùy biến đặc biệt qua `private writeObject(ObjectOutputStream)` và `private readObject(ObjectInputStream)`, thường phối hợp với `defaultWriteObject()` và `defaultReadObject()`.

Bốn tên này rất dễ nhầm, vì **hai cái là API gọi từ bên ngoài để ghi/đọc một object**, còn hai cái có tên `default...` là **API chỉ dùng bên trong callback** của quá trình serialization. Bài học tập trung vào thứ tự thao tác, ý nghĩa dữ liệu mặc định và dữ liệu bổ sung, vòng đời đối tượng, ví dụ Java 17+ chạy được, xử lý lỗi và các giới hạn bảo mật.

> **Cảnh báo bảo mật:** Java native serialization không phù hợp cho dữ liệu không đáng tin từ Internet hoặc người dùng bất kỳ. `ObjectInputStream.readObject()` có thể kích hoạt mã của các class được giải tuần tự hóa trước khi lời gọi trả về. Nên ưu tiên định dạng có schema được kiểm soát (JSON, Protocol Buffers, CBOR...) và áp dụng allowlist/serialization filter nếu buộc phải nhận dữ liệu Java serialization.

## 1. Phân biệt hai cấp độ API có cùng tên

| Vị trí | API | Ai gọi? | Vai trò |
| --- | --- | --- | --- |
| Ngoài object | `ObjectOutputStream.writeObject(Object obj)` | Code ứng dụng | Bắt đầu ghi một đối tượng và đồ thị tham chiếu liên quan. |
| Trong class `Serializable` | `private void writeObject(ObjectOutputStream out) throws IOException` | Cơ chế serialization tự gọi nếu chữ ký hợp lệ | Tùy biến dữ liệu được ghi cho **class đó**. |
| Trong callback `writeObject` | `out.defaultWriteObject()` | Code callback | Ghi các field thuộc trạng thái serializable **mặc định** của class hiện tại. |
| Ngoài object | `ObjectInputStream.readObject()` | Code ứng dụng | Đọc object tiếp theo từ stream; trả về `Object`. |
| Trong class `Serializable` | `private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException` | Cơ chế deserialization tự gọi nếu hợp lệ | Tùy biến quá trình khôi phục và validate. |
| Trong callback `readObject` | `in.defaultReadObject()` | Code callback | Khôi phục trạng thái field mặc định đã ghi tương ứng. |

**Không được hiểu rằng `out.writeObject(this)` trong callback là tương đương `out.defaultWriteObject()`.** Ghi lại chính `this` qua object handle không có tác dụng ghi mặc định các field như bạn mong muốn; có thể tạo dữ liệu không đúng. Dùng `defaultWriteObject()` để ghi field data của class theo hợp đồng serialization.

Tương tự, `in.readObject()` trong callback không có nghĩa khôi phục field mặc định: nó đọc **một đối tượng kế tiếp** của phần dữ liệu trong stream. Để khôi phục field data của class hiện tại, dùng `in.defaultReadObject()`.

## 2. Serializable mặc định lưu những gì?

Một class triển khai `java.io.Serializable` có thể được tuần tự hóa bằng `ObjectOutputStream`. Mặc định, các field sau được xem xét:

- Các **instance fields** không mang modifier `transient` và không phải `static`.
- Giá trị primitive được ghi theo protocol serialization; tham chiếu tới object khác có thể kéo theo việc tuần tự hóa object tham chiếu.
- Object graph giữ được nhiều quan hệ định danh/tham chiếu theo cơ chế handle của stream; không phải một phép copy từng field độc lập bằng text.
- Field `transient` và `static` không được tự động lưu bởi cơ chế default.
- Nếu một instance field tham chiếu tới class không serializable và không được xử lý phù hợp, việc ghi object graph có thể ném `NotSerializableException`.

Ví dụ:

```java
class Profile implements Serializable {
    private static final long serialVersionUID = 1L;
    String name;                  // Mặc định được lưu.
    int age;                      // Mặc định được lưu.
    transient String cache;       // Mặc định bị bỏ qua.
    static String applicationId;  // Thuộc class, không phải object state.
}
```

Đây là **snippet**, cần import `java.io.Serializable`. Sau `readObject()` ở cấp ứng dụng, `name` và `age` thường được phục hồi từ dữ liệu field; `cache` mặc định là `null`, trừ khi có custom code khôi phục; `applicationId` do trạng thái hiện tại của class quyết định, không lấy từ stream của instance.

Dùng `transient` không phải bảo đảm tuyệt đối rằng giá trị không thể lọt ra ngoài. Nếu tự viết giá trị ấy qua `out.writeUTF` hoặc qua một field serializable khác, nó **vẫn xuất hiện trong stream**. Đừng serialize mật khẩu, bearer token, session secret hoặc thông tin nhạy cảm chỉ vì muốn khôi phục “đúng trạng thái”.

## 3. Chữ ký callback chính xác — không phải override bình thường

Cơ chế Java serialization nhận diện callback có dạng chuẩn:

```java
@Serial
private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    // Ghi thêm dữ liệu tùy chọn của class.
}

@Serial
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    // Đọc dữ liệu tùy chọn và xác minh trạng thái.
}
```

Đây là **snippet nằm trong một class triển khai `Serializable`**, với imports `java.io.Serial`, `java.io.IOException`, `java.io.ClassNotFoundException`, `ObjectOutputStream` và `ObjectInputStream`.

Lưu ý:

1. `writeObject` phải là **private instance method**, trả `void` và nhận đúng một tham số `ObjectOutputStream`; `readObject` tương tự với `ObjectInputStream` và khai báo checked exceptions phù hợp. Đây là các **hook đặc biệt** mà serialization runtime tìm kiếm, không phải phương thức `Serializable` yêu cầu override.
2. Một method `public writeObject(...)` hoặc `static writeObject(...)` **không phải callback tùy biến tương đương**. Không đánh dấu `@Override` cho chúng.
3. `@Serial` (Java 14+) giúp compiler kiểm tra ý định dùng phần tử phục vụ serialization; từ Java 17 có thể dùng ổn định. Không bắt buộc để cơ chế hoạt động, nhưng giúp phát hiện lỗi khai báo.
4. Callback được áp dụng cho class cụ thể trong hierarchy serializable; **private method không được kế thừa như một override thông thường**.
5. Trong callback, `defaultWriteObject/defaultReadObject` chỉ hoạt động khi đang ở **ngữ cảnh active serialization/deserialization thích hợp**. Gọi ở bên ngoài ngữ cảnh đó có thể gây `NotActiveException`.

Đặt `readObject` sai chữ ký khiến JVM không tự chạy code validate/khôi phục mà bạn dự định. Đó là lỗi nguy hiểm, vì chương trình có thể vẫn đọc thành công nhưng tạo đối tượng sai bất biến.

## 4. Thứ tự chuẩn khi ghi rồi đọc

Khi cần giữ field mặc định **và** thêm dữ liệu tùy chọn:

```text
Ứng dụng                     Lớp Serializable
─────────                    ──────────────────────────
out.writeObject(order)
                              private writeObject(out)
                               ├─ defaultWriteObject()
                               │    ghi các serializable fields mặc định
                               ├─ writeInt(version)
                               ├─ writeBoolean(hasNote)
                               └─ writeUTF(note) nếu có
                         → bytes trong ObjectOutputStream

in.readObject()
                              private readObject(in)
                               ├─ defaultReadObject()
                               │    phục hồi default field data
                               ├─ readInt() phiên bản dữ liệu tùy chọn
                               ├─ readBoolean()
                               ├─ readUTF() nếu có
                               ├─ validate các bất biến
                               └─ dựng lại transient/derived state
                         → object đã khôi phục
```

Hai đầu phải **đối xứng về thứ tự và loại dữ liệu**:

- Ghi `int` trước thì đọc `int` trước.
- Ghi `boolean` rồi mới có điều kiện ghi `UTF` thì bên đọc cũng phải theo logic đó.
- Nếu `defaultWriteObject()` đứng đầu, `defaultReadObject()` cũng phải đứng trước các thao tác đọc phần dữ liệu tùy chọn.
- Không đọc đến EOF của toàn ObjectInputStream để xác định điểm kết thúc dữ liệu một object: stream có framing và có thể còn nhiều object khác phía sau.
- Không tự tùy tiện trộn bytes ngoài `ObjectOutputStream` với bytes trong serialization stream theo kiểu phá header/protocol.

`defaultWriteObject()` **không lưu các trường transient**. Muốn khôi phục một transient được phép lưu, callback phải **chủ động ghi thêm** và đọc lại tương ứng. Với cache có thể tính lại, thường chỉ cần tái xây sau `defaultReadObject()` chứ không phải lưu byte của cache.

## 5. Ví dụ Java 17+ chạy độc lập: cả bốn phương thức làm việc cùng nhau

Ví dụ dùng một đối tượng `Order` có:

- `id` (String) và `cents` (int): được lưu bằng `defaultWriteObject`.
- `auditNote` là `transient`: **không** nằm trong phần default, nhưng được **chủ động ghi** như dữ liệu tùy chọn để minh họa hook.
- `runtimeLabel` là `transient`: không được ghi; `readObject` tạo lại nó với giá trị `restored`.
- `formatVersion` trong custom section để phát hiện định dạng không được hỗ trợ.
- Kiểm tra id không trống và số tiền không âm khi deserialization.

Lưu đúng tên **`CustomSerializationDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;

public class CustomSerializationDemo {
    private static final class Order implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String id;
        private int cents;
        private transient String auditNote;
        private transient String runtimeLabel;

        Order(String id, int cents, String auditNote) {
            this.id = id;
            this.cents = cents;
            this.auditNote = auditNote;
            this.runtimeLabel = "constructed";
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeInt(1);
            out.writeBoolean(auditNote != null);
            if (auditNote != null) {
                out.writeUTF(auditNote);
            }
        }

        @Serial
        private void readObject(ObjectInputStream in)
                throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            int formatVersion = in.readInt();
            if (formatVersion != 1) {
                throw new InvalidObjectException("Unsupported version");
            }
            auditNote = in.readBoolean() ? in.readUTF() : null;
            if (id == null || id.isBlank() || cents < 0) {
                throw new InvalidObjectException("Invalid order");
            }
            runtimeLabel = "restored";
        }

        String describe() {
            return "id=" + id + ",cents=" + cents
                    + ",note=" + auditNote + ",label=" + runtimeLabel;
        }
    }

    public static void main(String[] args)
            throws IOException, ClassNotFoundException {
        Order original = new Order("A-17", 1250, "verified");

        byte[] data;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
            out.flush();
            data = bytes.toByteArray();
        }

        Order copy;
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(data))) {
            copy = (Order) in.readObject();
        }

        System.out.println("original: " + original.describe());
        System.out.println("copy: " + copy.describe());
        System.out.println("different-instance=" + (original != copy));
        System.out.println("nonempty-stream=" + (data.length > 0));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 CustomSerializationDemo.java
java CustomSerializationDemo
```

Stdout đã được xác nhận bằng biên dịch/chạy chương trình trên JDK 17+:

```text
original: id=A-17,cents=1250,note=verified,label=constructed
copy: id=A-17,cents=1250,note=verified,label=restored
different-instance=true
nonempty-stream=true
```

**Diễn giải đúng cơ chế:**

1. Khi ứng dụng gọi `out.writeObject(original)`, runtime tìm callback `Order.writeObject` có chữ ký đúng.
2. `out.defaultWriteObject()` ghi `id` và `cents` theo serializable field data của `Order`. Nó không tự ghi `auditNote` hay `runtimeLabel` vì cả hai là `transient`.
3. Callback ghi thêm `int 1` để đánh dấu format, `boolean` báo có audit note, rồi `writeUTF("verified")`.
4. Khi ứng dụng gọi `in.readObject()`, runtime khởi tạo object theo cơ chế serialization và gọi `Order.readObject`; `in.defaultReadObject()` phục hồi `id/cents`.
5. Bên đọc nhận đúng custom version/boolean/UTF, validate id/amount, đặt `runtimeLabel = "restored"` thay vì chạy constructor của `Order`.
6. `original != copy` vì object đọc là một instance mới trong process; nhưng đồ thị tham chiếu bên trong stream vẫn có quy tắc handle/identity riêng của Java serialization.
7. Hai dòng chứa audit note chứng tỏ `transient` **có thể được serialize theo cách thủ công**. Đây chỉ là ghi chú ví dụ, **không** nên hiểu là cho phép đưa token bí mật vào byte stream.

Ở demo nhỏ, `ByteArrayOutputStream` giúp dễ đối chiếu. Với dữ liệu lớn, cần quota, streaming hợp lý và chiến lược đọc/ghi thích hợp; đừng giữ stream Java serialized bytes vô hạn trong RAM chỉ vì muốn đơn giản hóa API.

## 6. Điều gì xảy ra nếu bỏ defaultWriteObject hoặc defaultReadObject?

Khi một class có callback riêng, việc ghi/đọc mặc định **không tự diễn ra** như khi class không có custom callback. Vì vậy nếu bỏ `out.defaultWriteObject()` trong ví dụ:

- Các field không transient như `id` và `cents` không còn tự được ghi theo phần field mặc định của class đó.
- Khi đọc mà vẫn gọi `defaultReadObject()`, bạn có thể nhận giá trị default/thiếu hoặc lỗi do stream/class descriptor không tương thích với kỳ vọng.
- Code kiểm tra `id == null` có thể phát hiện trạng thái invalid, nhưng không nên xem đây là cơ chế fallback thay cho định dạng đúng.

Ngược lại, nếu viết default field data mà không gọi `in.defaultReadObject()`, reader không chủ động khôi phục các field đó theo API mặc định của class. Phần dữ liệu cần đọc và các phần tùy chọn có nguy cơ bị diễn giải sai hoặc bị bỏ qua.

Khi thực sự muốn **chỉ ghi dữ liệu tự định nghĩa**, có thể thiết kế custom format không gọi default methods, nhưng phải hiểu chính xác protocol và tương thích với mọi phiên bản reader/writer. Với đa số lớp `Serializable` cần bổ sung thông tin, mẫu dễ kiểm soát nhất là:

```java
private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    out.writeInt(customVersion);
    // Các extra fields theo thứ tự đã định nghĩa.
}

private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    int customVersion = in.readInt();
    // Đọc đúng thứ tự và kiểm tra độ dài/giá trị.
}
```

Đây là **pseudocode** trong class serializable: `customVersion` cần có giá trị trước khi ghi, không phải chương trình độc lập. Các phương thức default chỉ nên được gọi **một lần** và trong ngữ cảnh callback phù hợp.

## 7. transient, derived fields và khôi phục invariants

Một trường `transient` thường thuộc một trong ba nhóm:

| Loại | Ví dụ | Nên làm gì sau khi đọc? |
| --- | --- | --- |
| Cache tính lại được | Giá trị format sẵn, thống kê dẫn xuất, lookup cache | Khởi tạo rỗng hoặc tính lại, không cần serialize. |
| Resource runtime | File handle, socket, thread, logger với lifecycle đặc biệt | Không serialize trực tiếp; tái mở/cấp phát khi cần theo lifecycle mới. |
| Dữ liệu cần tồn tại nhưng không muốn là default field | Trường tương thích version, snapshot metadata được phép lưu | Nếu thực sự cần, chủ động viết/đọc dưới custom protocol và giới hạn dữ liệu. |

**Không dựa vào constructor hay field initializer của chính class Serializable** để phục hồi transient state: khi deserialization, constructor của các lớp serializable không được gọi theo cách tạo object thông thường. Constructor no-arg của superclass đầu tiên **không** Serializable có thể được gọi theo cơ chế riêng của Java.

Bởi vậy, nếu `runtimeLabel` cần luôn không-null, tạo lại trong `readObject()`. Nếu đối tượng chứa invariants quan trọng, ví dụ `id != null`, `amount >= 0`, thuộc tính liên quan phải kiểm tra tương hỗ, dùng validation khi khôi phục.

Mẫu:

```java
@Serial
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    if (id == null || id.isBlank() || cents < 0) {
        throw new InvalidObjectException("Invalid order");
    }
    runtimeLabel = "restored";
}
```

Đây là **snippet** dành cho class có các field `id`, `cents`, `runtimeLabel` và không ghi extra data; nếu writer có ghi custom fields, bên đọc còn phải đọc đúng phần đó trước khi hoàn tất callback. Từ chối object invalid ngay khi đọc thay vì để trạng thái xấu đi vào business logic.

Cần cẩn trọng khi class chứa nhiều tham chiếu chéo trong object graph: `readObject` có thể chạy khi các object khác trong graph chưa phục hồi hoàn chỉnh. Đừng công bố `this` cho thread khác, global registry hoặc code có side effect trước khi đồ thị được xác minh.

## 8. serialVersionUID và version của custom data là hai chuyện khác nhau

Hai dạng version:

- **`serialVersionUID`:** nhận dạng phiên bản serialization compatibility của class; nếu UID của class trong stream không tương thích với class cục bộ, `InvalidClassException` có thể được ném trước khi custom deserialization có thể xử lý.
- **`formatVersion` tự ghi:** marker do ứng dụng định nghĩa bên trong **optional data** của callback; giúp phân biệt layout custom extra data khi mọi điều kiện khác của protocol/class vẫn tương thích.

```java
@Serial
private static final long serialVersionUID = 1L;

@Serial
private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    out.writeInt(2); // Schema riêng cho phần custom data.
}
```

Đây là snippet, không phải mẫu đầy đủ. Khi đổi optional-data version từ 1 sang 2, **reader phải có nhánh xử lý cả hai** nếu yêu cầu tương thích ngược. Chỉ thêm `writeInt(2)` mà quên đổi `readObject` sẽ làm reader cũ từ chối hoặc đọc sai.

Đừng tùy tiện thay `serialVersionUID` mỗi khi bổ sung một optional field. UID có mục đích quyết định mức tương thích của **class serialization schema**. Giữ UID cố định không tự bảo đảm mọi thay đổi sẽ an toàn: đổi kiểu field, class hierarchy, chuyển một field sang `transient`, thay protocol callback hoặc serialization proxy đều phải có kiểm thử version/cross-version thực tế.

Một số thay đổi mặc định có quy tắc tiến hóa field của Java serialization, nhưng optional data là **giao thức riêng do bạn chịu trách nhiệm**. Hãy lưu fixtures chứa bytes được tạo bởi version cũ và chạy test cả writer cũ → reader mới (và ngược lại khi sản phẩm yêu cầu).

## 9. Optional data, writeObject/readObject và tính đối xứng

Sau `defaultWriteObject()`, callback có thể ghi primitives hoặc serializable objects:

```java
// Snippet: trong private writeObject(...)
out.defaultWriteObject();
out.writeInt(customSchema);
out.writeBoolean(optionalValue != null);
if (optionalValue != null) {
    out.writeUTF(optionalValue);
    out.writeObject(additionalObject);
}
```

Đầu đọc:

```java
// Snippet: trong private readObject(...)
in.defaultReadObject();
int schema = in.readInt();
boolean present = in.readBoolean();
if (present) {
    String optionalValue = in.readUTF();
    Object additionalObject = in.readObject();
}
```

Các biến `customSchema`, `optionalValue` và `additionalObject` thuộc logic ứng dụng. `readObject()` bên trong callback để đọc một đối tượng **phụ**, khác với `defaultReadObject()` dùng khôi phục mặc định. Cần xác định kiểu hợp lệ cho object phụ, đánh giá nguồn tin cậy và tránh nhét vào graph những class không được phép.

**Không dùng `readUTF()` để đọc bytes được ghi bằng `writeObject(String)` hoặc ngược lại.** Hai phương thức có encoding/protocol khác nhau. `writeUTF/readUTF` sử dụng modified UTF-8 và giới hạn byte được biểu diễn của định dạng UTF, không phải phương thức ghi chuỗi tùy ý không giới hạn.

Phần dữ liệu riêng cần có schema rõ: thứ tự, marker, optional/null, giới hạn kích thước, khả năng gặp phiên bản mới, và lỗi khi dữ liệu bị cắt. Không để reader “thử đoán” từ số byte còn lại của toàn stream; framing serialization bao trùm cả object graph.

## 10. Khi nào dùng putFields/readFields thay default methods?

Java còn cung cấp cơ chế `ObjectOutputStream.PutField` và `ObjectInputStream.GetField` cho trường hợp muốn điều khiển **giá trị field mặc định được serialize** mà không trực tiếp gán trạng thái runtime field trước khi ghi.

```java
// Snippet bên trong private writeObject(...)
ObjectOutputStream.PutField fields = out.putFields();
fields.put("name", name);
fields.put("age", age);
out.writeFields();
```

Và:

```java
// Snippet bên trong private readObject(...)
ObjectInputStream.GetField fields = in.readFields();
String restoredName = (String) fields.get("name", "");
int restoredAge = fields.get("age", 0);
```

Đây là snippet của class có serializable fields tương ứng và import phù hợp; có thể dùng `serialPersistentFields` khi cần định nghĩa tập field logic độc lập với biểu diễn trong bộ nhớ. **Không trộn `defaultWriteObject()` với `putFields/writeFields()` cho cùng phần default field data**; hai con đường thay thế nhau. Phía đọc cũng chọn `defaultReadObject()` hoặc `readFields()` tương ứng trong ngữ cảnh callback.

Dùng `PutField/GetField` khi cần schema cũ/ảo có ý nghĩa, nhưng đây là API thấp hơn và dễ sai. Với nhu cầu học cơ chế phổ biến, bắt đầu bằng cặp `defaultWriteObject/defaultReadObject`.

## 11. Lưu ý về object graph và stream handles

`ObjectOutputStream` duy trì handle table để giữ identity khi cùng một đối tượng được ghi nhiều lần trên **cùng stream**:

```java
out.writeObject(order);
out.writeObject(order);
```

Trong điều kiện object không bị thay thế bởi hook đặc biệt, phía đọc có thể nhận **cùng một instance trong graph** cho hai lời gọi tương ứng, không phải luôn tạo bản sao thứ hai. Điều này quan trọng nếu ứng dụng sửa field sau lần write thứ nhất mà nghĩ lần write thứ hai chắc chắn ghi snapshot mới.

Nếu cần stream ghi lại trạng thái mới một cách có kiểm soát, tìm hiểu `ObjectOutputStream.reset()`, `writeUnshared()` và định nghĩa cách đồng bộ của ứng dụng. **Không dùng `reset()` tùy tiện** khi đồ thị có quan hệ tham chiếu cần giữ giữa các object khác nhau; thay đổi handle semantics sẽ ảnh hưởng reader.

Không tạo `ObjectOutputStream` mới để “append” thêm object vào cuối một file serialized sẵn có một cách ngây thơ: constructor viết stream header mới, dễ làm `ObjectInputStream` đọc nối tiếp bị `StreamCorruptedException`. Nếu cần persistence có thể append/update ổn định, cân nhắc định dạng record tự mô tả hoặc giải pháp lưu trữ khác.

## 12. Hierarchy, inheritance và các hook liên quan

Một object có thể gồm nhiều đoạn dữ liệu serialization thuộc các class serializable khác nhau trong hierarchy. Mỗi class có thể có callback `writeObject/readObject` **riêng của chính nó**. Chúng không phải các method override theo kiểu polymorphism thông thường.

Các chi tiết quan trọng:

- Một subclass `Serializable` có thể có callback private riêng; superclass `Serializable` có thể có callback private khác.
- Non-serializable superclass thường được khởi tạo theo cơ chế gọi constructor không đối số của class đầu tiên không serializable trong hierarchy khi deserialization. Các field trong non-serializable superclass không tự được khôi phục như field serializable.
- `readObjectNoData()` là hook đặc biệt tùy chọn để xử lý trường hợp không có dữ liệu serialization cho class trong một số thay đổi hierarchy/version. Nó **không** thay cho `readObject()` thông thường.
- `writeReplace()` và `readResolve()` có thể thay thế object được ghi/đọc, dẫn tới mô hình **serialization proxy**. Đây là một lựa chọn mạnh cho class bất biến, nhưng có quy tắc riêng về object identity, compatibility và validation.
- `Externalizable` là cơ chế **khác**: code triển khai `writeExternal/readExternal` và vòng đời constructor theo hợp đồng Externalizable, không đồng nghĩa thêm `defaultWriteObject()` như class Serializable.

Nếu class có invariants liên quan nhiều field hoặc không muốn cho phép tạo object bypass constructor theo serialization, cân nhắc serialization proxy pattern hoặc bỏ hỗ trợ Java native serialization.

## 13. Ngoại lệ thường gặp và chẩn đoán

| Ngoại lệ / triệu chứng | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| `NotSerializableException` | Một object trong graph không triển khai Serializable, hoặc không có chiến lược serialize cho field liên quan. | Chọn dữ liệu có thể serialize; đánh dấu transient và tái tạo đúng nếu phù hợp. |
| `InvalidClassException` | UID/class descriptor không tương thích, hoặc vi phạm điều kiện tạo object. | Kiểm soát serialVersionUID, migrations và compatibility tests. |
| `StreamCorruptedException` | Stream header/format hỏng, dữ liệu bị ghép sai, hoặc ghi bytes không theo serialization protocol. | Dùng cặp Object streams đúng, không nối header tùy tiện. |
| `OptionalDataException` | Reader kỳ vọng object nhưng gặp primitive optional data hoặc dữ liệu ở phần optional không như schema. | Cân bằng thứ tự read/write và phân biệt `readObject` với primitive reads. |
| `EOFException` | Byte stream bị cắt trước khi đủ dữ liệu được reader yêu cầu. | Kiểm tra truncate, truyền tải, framing, retry/abort theo chính sách. |
| `ClassNotFoundException` | Class cần khôi phục không có trong classpath/module tương ứng. | Kiểm tra triển khai version, dependency, không cho phép class tùy ý từ nguồn ngoài. |
| `InvalidObjectException` | `readObject` chủ động từ chối object sai schema/invariants. | Giữ validation, sửa writer/nguồn dữ liệu, không bypass kiểm tra. |
| `NotActiveException` | Gọi `defaultReadObject/defaultWriteObject` ngoài callback active hợp lệ. | Chỉ gọi đúng ngữ cảnh runtime callback. |
| Field `transient` là null sau đọc | Không được ghi mặc định, constructor Serializable không chạy. | Tái tạo trong `readObject` hoặc lazy-init sau đọc. |
| Callback “không chạy” | Sai chữ ký, không private, dùng static hoặc không Serializable. | Khai báo private chính xác và dùng `@Serial` để compiler hỗ trợ kiểm tra. |
| Reader lấy sai custom field | Thứ tự `writeInt/writeUTF/...` khác `readInt/readUTF/...` hoặc version không được hỗ trợ. | Viết schema đối xứng và có test fixtures. |
| Same object viết hai lần nhưng “không cập nhật” | Stream handle table giữ object identity; lần sau có thể là reference. | Thiết kế snapshot/stream lifecycle rõ, nghiên cứu `reset()` phù hợp. |

Không cố “sửa” exception bằng cách bỏ validate hoặc catch tất cả rồi trả object chưa hoàn chỉnh. Deserialization thất bại phải được coi là lỗi parse/kiểm tra dữ liệu, với tài nguyên và trạng thái ứng dụng được dọn an toàn.

## 14. Bảo mật: tuyệt đối không coi readObject là parser an toàn cho input tùy ý

Serialization là **một cơ chế khởi tạo object graph với code có thể được gọi trong quá trình đọc**. Khi `ObjectInputStream.readObject()` xử lý bytes của nguồn không đáng tin, việc đọc class/graph có thể kích hoạt callback `readObject`, `readResolve` và logic của class khác trong dependencies trước khi bạn có cơ hội cast hay kiểm tra giá trị sau đọc.

Rủi ro gồm:

- Gadget chains và side effects không mong muốn trong classpath.
- Tài nguyên cạn do graph sâu, rất nhiều object, array lớn hoặc dữ liệu custom không giới hạn.
- Object có invariant sai (ví dụ số âm, null ở field bắt buộc) nếu reader không validate.
- Thông tin nhạy cảm vô ý lọt vào stream, hoặc custom callback chủ động ghi secrets.
- Dữ liệu phiên bản lạ khiến reader lỗi hoặc áp dụng nhầm logic cũ.

Các biện pháp tối thiểu:

1. **Không deserialize dữ liệu không đáng tin** nếu có thể thiết kế thay thế bằng format ít quyền năng hơn.
2. Nếu bắt buộc: đặt `ObjectInputFilter`/serialization filter **trước khi gọi `readObject()`**, allowlist class thật sự cần, giới hạn độ sâu, số references, kích thước bytes, độ dài arrays và các tài nguyên liên quan.
3. Bảo vệ cả transport và nguồn dữ liệu: xác thực, phân quyền, kiểm tra integrity/authenticity; filter không thay thế cơ chế này.
4. Validate mọi invariants trong callback hoặc proxy; từ chối data sai bằng exception như `InvalidObjectException`.
5. Áp dụng quota/timeout cho phần custom data; `ObjectInputFilter` không tự hiểu mọi logic nghiệp vụ của một callback tùy biến.
6. Không coi việc cast sau `readObject()` là đủ an toàn vì side effects có thể đã xảy ra.
7. Cập nhật dependencies và kiểm thử bảo mật; không tự tin chỉ vì object class của ứng dụng “đơn giản”.

**Lưu ý:** Một filter không phải giải pháp tuyệt đối cho mọi gadget/class và không giải quyết việc bytes serialized bị sửa có chủ đích. Với hệ thống cần trao đổi giữa service, định dạng schema có validation và authentication rõ ràng thường là lựa chọn tốt hơn native Java serialization.

## 15. Kiểm thử tính đúng đắn và khả năng tương thích

Một bộ test có ích nên gồm:

| Loại test | Điều cần chứng minh |
| --- | --- |
| Roundtrip cơ bản | Ghi đối tượng rồi đọc lại; field mặc định và optional data đúng. |
| Transient rebuild | Field không lưu tự động được khởi tạo lại đúng và không chứa giá trị cũ không mong muốn. |
| Null optional field | Boolean/marker xác định đúng trường hợp thiếu, không đọc thừa `readUTF`. |
| Invalid invariants | Reader từ chối id null, giá trị âm, trạng thái đối nghịch hoặc field sai. |
| Custom version không hỗ trợ | Reader ném exception có chủ đích, không tạo object dùng được. |
| UID/class mismatch | Phát hiện phiên bản class không tương thích, không báo thành công giả. |
| Truncated stream | Reader thất bại có kiểm soát, không lộ object partial ra ngoài. |
| Graph tham chiếu vòng/trùng | Identity/reference semantics giữ đúng theo hợp đồng stream. |
| Writer cũ → Reader mới | Schema optional data có khả năng backward compatibility dự kiến. |
| Reader cũ → Writer mới | Kiểm tra forward compatibility nếu nghiệp vụ cần; có thể chủ động từ chối. |
| Khối lượng lớn | Quota, latency, giới hạn kích thước và resource cleanup. |
| Nguồn không tin cậy | Verify filter và không sử dụng Java native deserialization cho bytes công khai khi không cần. |

Đối với output của bài demo, chỉ kiểm tra `different-instance=true` và các field liên quan; không so chính xác số byte serialized stream vì implementation/class details có thể khiến kích thước thay đổi và vì byte count không nói lên correctness của application schema.

## 16. Bài tập thực hành

**Bài 1 — So sánh default với custom.** Tạo một class chỉ có `implements Serializable`, không callback; roundtrip 2 field thường và 1 field transient. Sau đó thêm `writeObject/readObject`, dùng đúng `defaultWriteObject/defaultReadObject` và kiểm tra kết quả thay đổi ra sao.

**Bài 2 — Optional field null.** Với ví dụ `CustomSerializationDemo`, truyền `auditNote = null`. Theo dõi byte logic: ghi `boolean false`, không ghi UTF; reader phải nhận lại `null` mà không thực hiện `readUTF()`.

**Bài 3 — Kiểm tra invariants.** Mở rộng Order thêm `quantity` và `unitPrice`, bắt buộc cents bằng tích hai giá trị trong phạm vi không tràn số. Validate trong `readObject` bằng toán học an toàn; nêu lý do validate trước khi công bố object.

**Bài 4 — Version của dữ liệu bổ sung.** Tạo custom version 1 chỉ lưu một boolean, version 2 thêm string. Viết reader có switch theo version, xử lý v1/v2 theo đúng thứ tự, từ chối version tương lai không được hỗ trợ. Giữ fixture bytes của mỗi version.

**Bài 5 — Không được ghi secret.** Thêm transient `authToken` chỉ dùng tại runtime. Thử roundtrip và xác nhận token sau deserialize là null hoặc được cấp lại bởi hệ thống xác thực, **không** ghi nó bằng optional data.

**Bài 6 — Chữ ký hook.** Cố ý đổi callback thành `public void readObject(...)` hoặc thiếu tham số. Dùng `@Serial` và compiler để phát hiện sự khác biệt; giải thích vì sao đây không phải method override bình thường.

**Bài 7 — Đọc đồ thị.** Tạo hai field cùng trỏ một instance Address. Sau roundtrip, kiểm tra identity của các tham chiếu trong object mới, không chỉ `equals`. Thử cùng một object được `writeObject` hai lần trên một stream và nghiên cứu handle table.

**Bài 8 — Kiểm tra security boundary.** Thiết lập filter giới hạn depth, references, bytes và class được phép cho một thử nghiệm dữ liệu tự tạo. So sánh với cách chuyển DTO sang JSON có schema kiểm soát. Không dùng payload khai thác, không nhận stream serialized từ người dùng trên dịch vụ thật.

### Câu hỏi tự kiểm tra

1. `ObjectOutputStream.writeObject(obj)` khác `private writeObject(ObjectOutputStream)` ở đâu?
2. Tại sao callback cần gọi `defaultWriteObject()` nếu vẫn muốn giữ các field thông thường?
3. `defaultReadObject()` có tự phục hồi field `transient` không?
4. Sau `defaultWriteObject()` nếu ghi `int, boolean, UTF` thì reader phải gọi những gì theo đúng thứ tự?
5. Vì sao `@Serial` và modifier private quan trọng với callback?
6. Vì sao `serialVersionUID` không thay thế version riêng của optional data?
7. Tại sao constructor của Serializable class không thể đảm nhận toàn bộ khôi phục cache?
8. Vì sao không nên serialize bearer token dù field được đánh dấu `transient`?
9. Vì sao filter phải được thiết lập **trước** lần `readObject()`?
10. Lỗi nào có thể xảy ra nếu cùng một stream ghi hai lần cùng một object nhưng ứng dụng mong một snapshot mới?

## 17. Tài liệu tham khảo

- [Java SE 21 — Serializable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Serializable.html): hợp đồng callback private, serialVersionUID, readObjectNoData, writeReplace/readResolve.
- [Java SE 21 — ObjectOutputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectOutputStream.html): writeObject, defaultWriteObject, putFields, handles, reset và protocol ghi.
- [Java SE 21 — ObjectInputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputStream.html): readObject, defaultReadObject, readFields và các exception.
- [Java SE 21 — ObjectInputFilter](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputFilter.html): lọc class/object graph và giới hạn tài nguyên.
- [Java SE 21 — @Serial](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Serial.html): annotation hỗ trợ kiểm tra cấu trúc serialization.
- [Java Object Serialization Specification](https://docs.oracle.com/en/java/javase/21/docs/specs/serialization/index.html): format, class evolution và chi tiết protocol.

**Ghi nhớ:** `writeObject/readObject` là callback private của class Serializable; `defaultWriteObject/defaultReadObject` giữ phần field data mặc định, còn các lệnh `writeInt/writeUTF/readInt/readUTF` xử lý phần dữ liệu bổ sung theo thứ tự đối xứng. Khôi phục transient và validate bất biến rõ ràng, giữ version phù hợp, và không dùng Java native deserialization với nguồn không đáng tin.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-serialization-002">readObjectNoData và thay đổi hệ phân cấp</a>

<details>
<summary>Xem chi tiết</summary>

# `readObjectNoData()` và thay đổi hệ phân cấp trong Java Serialization

## 1. Vấn đề: lớp hiện tại có thể có superclass mà dữ liệu cũ chưa từng biết

`java.io.Serializable` cho phép một chương trình ghi object bằng `ObjectOutputStream` rồi đọc lại bằng `ObjectInputStream`. Tuy nhiên, giữa lúc ghi và lúc đọc, cấu trúc lớp có thể được sửa đổi. Một tình huống đặc biệt là **phiên bản mới chèn thêm một superclass có triển khai `Serializable`** vào hệ phân cấp.

Ví dụ phiên bản cũ:

```text
Object
  └── Receipt implements Serializable
        └── id
```

Phiên bản mới:

```text
Object
  └── AuditInfo implements Serializable    // MỚI thêm
        └── source
        └── Receipt implements Serializable // Cùng tên và serialVersionUID
              └── id
```

Dữ liệu đã ghi ở phiên bản cũ biết về `Receipt` nhưng **không có mô tả/lớp dữ liệu `AuditInfo`**. Khi dựng object bằng phiên bản mới, JVM vẫn phải tạo phần trạng thái của `AuditInfo`, nhưng nó không thể đọc những byte dành riêng cho lớp này từ stream cũ, bởi vì chúng **không tồn tại**.

Đó chính là hoàn cảnh mà phương thức đặc biệt **`private void readObjectNoData() throws ObjectStreamException`** được Java Serialization định nghĩa để xử lý.

Sau bài học, bạn sẽ:

1. Biết **khi nào `readObjectNoData()` được gọi** và khi nào không.
2. Phân biệt nó với `readObject(ObjectInputStream)`, `defaultReadObject()`, constructor và field initializer.
3. Hiểu vì sao thay đổi hệ phân cấp có thể làm những field mới mang giá trị mặc định `null`, `0`, `false`.
4. Viết ví dụ **hai phiên bản nguồn Java cùng tên lớp**, serialize bằng phiên bản 1 và deserialize bằng phiên bản 2 để tái hiện callback chính xác.
5. Xác định vai trò của `serialVersionUID`, giới hạn tương thích và chính sách từ chối stream không hợp lệ.
6. Thiết kế migration state an toàn, tránh suy luận rằng mọi thay đổi trong lớp đều kích hoạt callback này.

Phạm vi bài là **Java Object Serialization mặc định cho các lớp thông thường triển khai `Serializable`**, không áp dụng nguyên xi cho enum, record hay `Externalizable`.

## 2. Serial stream chứa những gì?

Khi ghi một object:

```java
try (ObjectOutputStream output = new ObjectOutputStream(
        Files.newOutputStream(path))) {
    output.writeObject(someSerializableObject);
}
```

Đây là **snippet**: `path` và `someSerializableObject` đã được khai báo, cần import và xử lý checked exceptions. `ObjectOutputStream` mã hóa object graph theo giao thức Serialization: mô tả kiểu, thông tin version và dữ liệu các field cần serialize, theo từng lớp trong hierarchy tương ứng.

Khi đọc:

```java
try (ObjectInputStream input = new ObjectInputStream(
        Files.newInputStream(path))) {
    Object value = input.readObject();
}
```

Đây cũng chỉ là snippet. `ObjectInputStream` khôi phục object bằng các **định nghĩa lớp hiện diện ở phía người đọc**. Nó so sánh thông tin của stream với các lớp local và phải xử lý tình huống stream cũ / local class mới.

Điểm dễ quên: serial stream **không** chứa toàn bộ byte của object trong heap. Nó chứa các trường/mô tả theo Serialization protocol. Các biến `static` và `transient` không được ghi theo cơ chế field mặc định; field initializer và constructor của **serializable classes** không tự động được gọi chỉ để khôi phục field. Việc khôi phục theo từng lớp rất quan trọng khi hierarchy thay đổi.

Với một hierarchy như:

```text
A (không Serializable)
  └── B implements Serializable
        └── C implements Serializable
```

Khi deserialize instance của `C` theo giao thức thông thường:

- JVM khởi tạo object với giá trị mặc định của các field.
- Constructor no-arg của lớp **không serializable gần nhất** (`A`) được gọi theo cơ chế Serialization.
- Constructor và field initializer của `B` và `C` **không chạy theo đường tạo instance thông thường**.
- State của các lớp Serializable được khôi phục theo thứ tự từ superclass xuống subclass. Với mỗi class có dữ liệu tương ứng, Java dùng default field restoration hoặc hook `readObject` của class đó.
- Với một **Serializable class hiện diện ở hierarchy local nhưng không có slot dữ liệu tương ứng trong stream**, Java có thể gọi hook `readObjectNoData` của class ấy.

## 3. Chính xác `readObjectNoData()` làm gì?

Theo Java SE 17, chữ ký đặc biệt là:

```java
@Serial
private void readObjectNoData() throws ObjectStreamException {
    // Khởi tạo các field được khai báo bởi chính lớp này,
    // nếu stream không liệt kê lớp này là superclass tương ứng.
}
```

Đây là **snippet**, phải nằm bên trong một class `Serializable`, có import `java.io.Serial` và `java.io.ObjectStreamException`. Annotation `@Serial` được hỗ trợ ở Java 17, giúp công cụ biên dịch/lint nhận diện các khai báo liên quan cơ chế Serialization; bản thân annotation **không** quyết định việc callback được gọi.

### Hợp đồng kích hoạt

Hook được gọi khi quá trình deserialize một instance của subclass và **serialization stream không liệt kê một Serializable class local là một superclass của instance đó**. Kịch bản điển hình là bên ghi dùng phiên bản có hierarchy ngắn hơn; bên đọc dùng phiên bản đã thêm một Serializable superclass mới.

Java cũng mô tả khả năng stream không đầy đủ hoặc đã bị can thiệp làm thiếu class data, nên hook này còn hữu ích để **khôi phục invariant** hoặc từ chối trạng thái không an toàn.

### Điều kiện KHÔNG đủ để gọi hook

- **Thêm field mới vào chính class cũ** không tự động kích hoạt `readObjectNoData()`; slot dữ liệu của class đó vẫn có trong stream. Field mới không có giá trị trong stream thường nhận giá trị mặc định của kiểu.
- Một field trong stream có giá trị `null` không đồng nghĩa **không có class data**.
- Khi class local vẫn có dữ liệu tương ứng và khai báo `readObject(ObjectInputStream)`, Java xử lý qua `readObject` (nếu hợp lệ), **không** dùng `readObjectNoData` chỉ vì có field thiếu.
- Gọi `new MyClass()` thông thường không chạy `readObjectNoData`.
- Không nên coi “thêm mọi superclass” đều kích hoạt: điều quan trọng là **lớp Serializable local cụ thể không có slot tương ứng trong stream**, và toàn bộ hierarchy vẫn thỏa các yêu cầu tương thích khác.
- `Externalizable` có giao thức riêng qua `readExternal` và constructor public no-arg; không thay thế `readExternal` bằng hook này.

### Phạm vi trách nhiệm

`readObjectNoData()` phải khôi phục **các field và invariant của chính class định nghĩa hook**, thay vì cố đọc data của subclass. Không có tham số `ObjectInputStream`, bởi vì **không có dữ liệu dành riêng cho class này để đọc**.

Nó có thể ném `ObjectStreamException`, thường dùng subclass `InvalidObjectException` khi không có cách an toàn để khôi phục trạng thái.

## 4. Phân biệt các Serialization hooks

| Hook / cơ chế | Khi nào liên quan | Vai trò |
| --- | --- | --- |
| `private void writeObject(ObjectOutputStream out)` | Khi serialize một Serializable class có custom write | Ghi default/custom data cho **slot của lớp đó** |
| `private void readObject(ObjectInputStream in)` | Khi stream có class data tương ứng và class khai báo hook hợp lệ | Đọc/khôi phục state của lớp, có thể gọi `defaultReadObject()` |
| `private void readObjectNoData()` | Khi local Serializable superclass có **không có slot tương ứng** trong stream | Khởi tạo/reject state của lớp vì không có data để đọc |
| `in.defaultReadObject()` | Được gọi **trong `readObject`** để khôi phục field mặc định của slot hiện tại | Không phải hàm callback độc lập |
| `private Object readResolve()` | Giai đoạn resolve object sau deserialize theo cơ chế hỗ trợ | Có thể thay object bằng object khác |
| Constructor của lớp Serializable | Tạo mới object bằng `new` | **Không** chạy khi deserialization bình thường |
| Constructor no-arg của superclass đầu tiên **không Serializable** | Khi deserialize instance thuộc lớp Serializable | Khởi tạo phần state không serializable |
| `Externalizable.readExternal(ObjectInput)` | Với lớp Externalizable | Đọc dữ liệu theo hợp đồng do lớp quản lý |

`readObjectNoData` và `readObject` là hai **cơ hội khác nhau** cho cùng một serializable class. Nếu class local xuất hiện trong stream hierarchy nhưng field mới chưa tồn tại, giải pháp có thể là dùng `readObject`/`readFields` để xử lý version của chính class; hook `readObjectNoData` **không phải** callback tổng quát cho mọi thay đổi schema.

## 5. Chữ ký và visibility phải đúng

Khai báo được khuyến nghị:

```java
import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;

public class ExampleBase implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String source;

    @Serial
    private void readObjectNoData() throws ObjectStreamException {
        source = "legacy";
    }
}
```

Đây là class **biên dịch độc lập** nếu lưu tên `ExampleBase.java`. Nó chỉ minh họa cách định nghĩa hook; một lệnh `new ExampleBase()` **không** kích hoạt hook.

Những lỗi chữ ký nên tránh:

```java
// Các dòng sau là ví dụ SAI có chủ ý, KHÔNG viết tất cả
// vào cùng ExampleBase.java để mong compiler thực thi hook:
public void readObjectNoData() { }        // Không phải private
private int readObjectNoData() { return 0; } // Không trả void
private void readObjectNoData(ObjectInputStream in) { } // Sai tham số
static void readObjectNoData() { }       // Không phải instance hook
```

Method là **private instance method, không tham số, trả `void`**, và có thể khai báo `throws ObjectStreamException`. `@Override` không phù hợp vì đây không phải method override từ interface `Serializable`; `Serializable` chỉ là **marker interface**, không khai báo method `readObjectNoData` để implement.

`@Serial` là annotation hỗ trợ nhận diện đúng hình dạng hook; viết sai visibility/return type có thể biến method thành method thông thường không được cơ chế deserialization gọi như ý muốn. Không nên dựa vào việc unit test chỉ compile thành công để khẳng định callback thực sự được nhận — cần chạy một tình huống **evolution của stream**.

## 6. Ví dụ tổng thể: ghi object v1, đọc bằng hierarchy v2

Chúng ta sử dụng **hai bản class có cùng tên đầy đủ** `evolution.Receipt` nhưng biên dịch vào **hai thư mục output riêng biệt**, để người ghi (v1) và người đọc (v2) không vô tình dùng lẫn class version.

### Bản v1: `Receipt` trực tiếp extends `Object`

Cấu trúc thư mục:

```text
v1/
└── evolution/
    ├── Receipt.java
    └── WriteV1.java
```

**File `v1/evolution/Receipt.java`**

```java
package evolution;

import java.io.Serial;
import java.io.Serializable;

public final class Receipt implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    public Receipt(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
```

**File `v1/evolution/WriteV1.java`**

```java
package evolution;

import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WriteV1 {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: WriteV1 <output-file>");
        }

        Path output = Path.of(args[0]);
        Receipt receipt = new Receipt("R-42");

        try (ObjectOutputStream out = new ObjectOutputStream(
                Files.newOutputStream(output))) {
            out.writeObject(receipt);
        }

        System.out.println("v1 wrote id=" + receipt.id());
    }
}
```

Dữ liệu object trong `receipt.ser` được tạo bởi **v1** chứa descriptor và serialized field của `evolution.Receipt`; không thể có slot `AuditInfo` vì class này **chưa tồn tại trong hierarchy v1**.

### Bản v2: chèn Serializable superclass `AuditInfo`

Cấu trúc:

```text
v2/
└── evolution/
    ├── AuditInfo.java
    ├── Receipt.java
    └── ReadV2.java
```

**File `v2/evolution/AuditInfo.java`**

```java
package evolution;

import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;

public class AuditInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 5L;

    private String source = "field-initializer";

    protected AuditInfo() {
        source = "constructor";
        System.out.println("AuditInfo constructor");
    }

    @Serial
    private void readObjectNoData() throws ObjectStreamException {
        // Stream v1 không có slot AuditInfo.
        source = "legacy";
        System.out.println("AuditInfo.readObjectNoData");
    }

    public String source() {
        return source;
    }
}
```

**File `v2/evolution/Receipt.java`**

```java
package evolution;

import java.io.Serial;
import java.io.Serializable;

public final class Receipt extends AuditInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    public Receipt(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
```

Lưu ý: tên đầy đủ của `Receipt` và giá trị `serialVersionUID = 1L` **giữ nguyên** so với v1. Trong v2, `AuditInfo` cũng Serializable, nên các constructor và field initializer của `AuditInfo` không được chạy theo cơ chế tạo instance thông thường khi deserialize `Receipt`.

**File `v2/evolution/ReadV2.java`**

```java
package evolution;

import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReadV2 {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: ReadV2 <input-file>");
        }

        try (ObjectInputStream in = new ObjectInputStream(
                Files.newInputStream(Path.of(args[0])))) {

            Receipt result = (Receipt) in.readObject();

            System.out.println("v2 read id=" + result.id());
            System.out.println("v2 read source=" + result.source());
        }
    }
}
```

### Biên dịch vào hai classpath tách biệt

Từ thư mục chứa `v1` và `v2`, với shell có lệnh `mkdir -p` (Linux/macOS/Git Bash):

```bash
mkdir -p build/v1 build/v2

javac -d build/v1 v1/evolution/Receipt.java v1/evolution/WriteV1.java
java -cp build/v1 evolution.WriteV1 receipt.ser

javac -d build/v2 v2/evolution/AuditInfo.java v2/evolution/Receipt.java v2/evolution/ReadV2.java
java -cp build/v2 evolution.ReadV2 receipt.ser
```

Với **Windows PowerShell**, tạo thư mục bằng `New-Item -ItemType Directory -Force build/v1,build/v2` (hoặc `mkdir` phù hợp), sau đó chạy `javac`/`java` tương tự; dấu `/` trong đường dẫn classpath ở đây được Java hỗ trợ trên Windows. Các file nguồn phải được lưu ở đúng thư mục `evolution` vì khai báo `package evolution;`.

**Stdout khi cả hai lệnh `java` thành công, theo đúng thứ tự chạy:**

```text
v1 wrote id=R-42
AuditInfo.readObjectNoData
v2 read id=R-42
v2 read source=legacy
```

**Không có dòng `AuditInfo constructor`.** Vì `AuditInfo` là serializable superclass của `Receipt` local, khi khôi phục object Java không gọi constructor hay field initializer của lớp Serializable đó. Thay vào đó, do stream v1 **không liệt kê `AuditInfo`**, Java gọi `readObjectNoData()` và chúng ta đặt `source = "legacy"`.

Các bước cơ chế:

1. Chạy **v1**: JVM tải `evolution.Receipt` của v1 và ghi object chứa `id="R-42"`.
2. Chạy **v2**: JVM tải `evolution.Receipt` của v2, hiện extends `AuditInfo`.
3. Stream cũ vẫn có descriptor của `Receipt` tương thích về tên và `serialVersionUID`; field `id` được khôi phục bằng default serialization.
4. JVM phát hiện hierarchy local v2 có lớp `AuditInfo` nhưng stream không có slot class này.
5. `AuditInfo.readObjectNoData()` được gọi để khởi tạo state riêng của superclass mới. Nó đặt `source` thành `legacy`.
6. Đối tượng `Receipt` hoàn chỉnh có `id=R-42` từ dữ liệu cũ và `source=legacy` từ quy tắc migration của superclass mới.

**Không chạy `WriteV1` bằng classpath v2**: như vậy file mới sẽ có slot `AuditInfo`, làm mất ý nghĩa thử nghiệm. **Không chạy `ReadV2` bằng classpath v1**: bên đọc sẽ không có superclass mới. Phải có **hai process Java riêng với hai phiên bản cùng tên `Receipt`** để quan sát đúng cơ chế schema evolution.

## 7. Nếu bỏ `readObjectNoData()` thì điều gì xảy ra?

Trong bản v2, tạm thời **xóa duy nhất method `readObjectNoData` khỏi `AuditInfo`**, biên dịch lại `build/v2` và chạy `ReadV2` trên **cùng file `receipt.ser` tạo bởi v1**. Khi đó:

- `AuditInfo` vẫn là superclass Serializable mới vắng mặt trong stream.
- Vì không có hook để khởi tạo, những field của `AuditInfo` mang giá trị mặc định của JVM.
- `source` là field tham chiếu `String` nên có giá trị **`null`**, không phải `"field-initializer"` hoặc `"constructor"`.
- `Receipt.id` vẫn có thể được khôi phục từ slot của `Receipt` tương ứng, miễn các điều kiện tương thích khác giữ nguyên.

Dự kiến dòng có liên quan là:

```text
v2 read id=R-42
v2 read source=null
```

Giải thích quan trọng: **field initializer của Serializable superclass mới không chạy khi đọc object cũ**. Nếu lớp giả định `source` luôn non-null (ví dụ gọi `source.trim()`), object sau deserialize có thể vi phạm invariant và phát sinh lỗi ở nơi khác, rất khó truy vết.

Đó là lý do `readObjectNoData()` có giá trị ở tầng **khôi phục trạng thái hợp lệ** chứ không chỉ “để log một callback”.

## 8. Thứ tự khôi phục qua hierarchy

Java Object Serialization xử lý các lớp Serializable theo hướng **superclass đến subclass** trong quá trình khôi phục class data.

Với hierarchy mới:

```text
Object
   |
 AuditInfo (Serializable, KHÔNG có class data trong stream v1)
   |
 Receipt   (Serializable, CÓ class data trong stream v1)
```

Luồng ý tưởng:

```text
readObject() trên ObjectInputStream
         |
         v
cấp phát instance Receipt theo Serialization protocol
         |
         v
khởi tạo phần superclass không Serializable phù hợp (nếu có)
         |
         v
AuditInfo: không có slot stream -> readObjectNoData()
         |
         v
Receipt: có slot stream -> đọc/khôi phục field Receipt.id
         |
         v
trả về Receipt đã khôi phục
```

Khi `AuditInfo.readObjectNoData()` chạy, **Receipt-specific state có thể chưa khôi phục xong**. Bởi vậy:

- Không gọi những method **overridable** từ `readObjectNoData` nếu chúng có thể dispatch tới subclass và đọc state chưa sẵn sàng.
- Tập trung sửa state thuộc chính superclass.
- Nếu invariant phụ thuộc đồng thời vào nhiều tầng (ví dụ `Receipt.id` và `AuditInfo.source`), nên có chiến lược **validation sau khi cả object graph được khôi phục** bằng API có hỗ trợ, chẳng hạn `ObjectInputValidation` được đăng ký từ một hook `readObject` thích hợp.
- Không đưa một tham chiếu `this` chưa được khôi phục hoàn chỉnh ra cache toàn cục, background thread hay callback công khai.

Điều này giống rủi ro gọi overridable method trong constructor: object đang trải qua quá trình khởi tạo chưa hoàn toàn.

## 9. `serialVersionUID` và thay đổi hierarchy

Trong ví dụ, v1 và v2 cùng khai báo:

```java
@Serial
private static final long serialVersionUID = 1L;
```

cho **cùng class `evolution.Receipt`**. Điều này quan trọng vì `ObjectInputStream` so sánh thông tin version để quyết định có thể sử dụng mô tả class local hay không.

| Thay đổi | Hệ quả cần xem xét |
| --- | --- |
| Giữ tên class và `serialVersionUID` tương thích | Cho phép xét những quy tắc versioning còn lại |
| Thêm một Serializable superclass local mới | Có thể kích hoạt `readObjectNoData` của superclass đó nếu stream cũ không có slot |
| Đổi `serialVersionUID` của `Receipt` | Có thể ném `InvalidClassException` khi đọc stream cũ |
| Thay đổi tên package/class `Receipt` | Bên đọc có thể không resolve đúng class; `ClassNotFoundException` hoặc lỗi khác |
| Thêm field vào chính `Receipt` | Field mới thường mặc định nếu stream không có nó; không tự gọi `readObjectNoData` cho Receipt |
| Thay đổi field type / Serializable contract / externalization | Có thể không tương thích; không thể “sửa mọi thứ” bằng hook |
| Thêm superclass **không Serializable** | Liên quan constructor và state nonserializable; không đồng nhất với trường hợp hook Serializable |
| Sửa nguồn stream hoặc trộn nhiều version class vào classpath | Có thể gây lỗi cấu trúc, versioning hoặc behavior không mong đợi |

Đặt explicit `serialVersionUID` giúp kiểm soát versioning có chủ đích; **nó không phải bảo đảm mọi thay đổi đều tương thích**. Không nên chỉ tăng `serialVersionUID` như một “schema migration” rồi kỳ vọng code có thể deserialize dữ liệu cũ. Đổi UID thường là cách tuyên bố **không tương thích** theo default protocol.

Khi `serialVersionUID` không khai báo, JVM có thể tính UID mặc định dựa vào thông tin class; nhiều thay đổi ở class definition có thể khiến UID đổi và chặn đọc dữ liệu cũ. Đối với lớp phải lưu trữ/trao đổi serialized data nhiều phiên bản, explicit UID và kiểm thử versioning là bước quan trọng.

## 10. Phân biệt với việc thêm field mới mà không thêm superclass

Giả sử v1:

```java
class Customer implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
}
```

V2 vẫn cùng class và hierarchy nhưng thêm:

```java
class Customer implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private String region = "VN"; // MỚI
}
```

Hai đoạn này là **minh họa hai phiên bản khác nhau**, không được đưa vào cùng một source file. Khi deserialize object v1 bằng v2, slot `Customer` vẫn được stream mô tả. Nếu field `region` không có trong stream, nó thường nhận **`null`** chứ không chạy initializer `"VN"`. Tuy nhiên, **`Customer.readObjectNoData()` không được gọi chỉ vì region thiếu**, bởi không phải toàn bộ class data của `Customer` bị thiếu.

Các phương án đúng khi cần migration field:

- Dùng `private void readObject(ObjectInputStream in)` và `in.defaultReadObject()`, sau đó áp default/validate cho field mới của class đó.
- Dùng `ObjectInputStream.GetField` / `readFields()` nếu cần biết cụ thể một field có mặt trong dữ liệu hay không và tránh nhầm giá trị mặc định hợp lệ với dữ liệu bị thiếu.
- Xây một phiên bản schema tường minh trong format do ứng dụng kiểm soát, có migration được kiểm thử.

Ví dụ riêng về custom read (snippet có class Serializable bao quanh):

```java
@Serial
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    if (region == null) {
        region = "VN";
    }
}
```

Đoạn này không cần làm `readObjectNoData()` và không phục hồi slot của một superclass mới. Nó chỉ là ví dụ gán default đơn giản; nếu `null` là một giá trị có nghĩa trong schema mới, muốn phân biệt dữ liệu cũ và `null` hợp lệ phải dùng chiến lược version/`GetField.defaulted` tương ứng.

## 11. Khởi tạo invariant hay từ chối dữ liệu không hợp lệ?

`readObjectNoData()` có hai hướng xử lý chính.

### 11.1. Áp dụng mặc định an toàn

Khi class mới chỉ thêm một metadata không ảnh hưởng tính đúng đắn, có thể đặt default hợp lý:

```java
@Serial
private void readObjectNoData() throws ObjectStreamException {
    source = "legacy";
    enabled = false;
}
```

Snippet giả định hai field `source` và `enabled` thuộc **cùng class Serializable**. Không dùng giá trị default mà vô tình **mở quyền** cho object cũ: ví dụ cấp `admin=true` chỉ vì stream thiếu thông tin là lỗ hổng nghiêm trọng. Default phải theo nguyên tắc an toàn, minh bạch với dữ liệu đã có.

### 11.2. Từ chối object không thể khôi phục an toàn

Nếu superclass mới đại diện cho một invariant bắt buộc, không thể suy ra trạng thái an toàn của object cũ, có thể ném `InvalidObjectException`:

```java
@Serial
private void readObjectNoData() throws ObjectStreamException {
    throw new InvalidObjectException(
            "Missing required superclass state");
}
```

Snippet cần import `java.io.InvalidObjectException` và nằm trong class Serializable. Vì `InvalidObjectException` extends `ObjectStreamException`, nó phù hợp với chữ ký. Khi exception được ném, `ObjectInputStream.readObject()` thất bại; chương trình gọi phải xử lý thay vì xem object là hợp lệ.

**Quan trọng:** một stream đã bị sửa đổi có thể cố tình làm thiếu descriptor hoặc data. `readObjectNoData()` **không phải** hàng rào bảo mật toàn diện chống deserialization attack; nó chỉ cung cấp một điểm khôi phục/kiểm tra trong hoàn cảnh cụ thể. Nên xem dữ liệu serialized không tin cậy là đầu vào nguy hiểm.

## 12. Bảo mật: deserialize không tin cậy là rủi ro

`ObjectInputStream.readObject()` có thể dẫn tới tạo nhiều object và thực thi các hooks/phương thức khôi phục state trong object graph. Stream độc hại có thể khai thác code đang có trong classpath, gây tiêu tốn tài nguyên hoặc vượt qua giả định về invariant.

Biện pháp cần cân nhắc:

1. **Không deserialize byte không tin cậy nếu có thể tránh.** Với dữ liệu trao đổi giữa các hệ thống, cân nhắc JSON, Protocol Buffers, CBOR hoặc format có schema được kiểm tra chặt và không tái tạo arbitrary object graph.
2. Nếu buộc sử dụng Java Serialization, cấu hình **`ObjectInputFilter`** theo allowlist phù hợp và giới hạn depth, reference count, array size, tổng byte. Filter có giới hạn riêng, không thay thế validate.
3. Kiểm tra class identity, phiên bản schema, nguồn tạo dữ liệu và các giá trị bất biến sau khi đọc.
4. Không để hook gọi code overridable trên object chưa khôi phục xong hoặc công bố `this` ra bên ngoài.
5. Xử lý `InvalidClassException`, `ClassNotFoundException`, `InvalidObjectException` và I/O errors ở biên nhập dữ liệu; không tiếp tục dùng stream lỗi như thể không có gì.
6. Dùng dữ liệu mẫu đã tạo bởi code đáng tin để kiểm thử evolution; không nhận những file `.ser` không rõ nguồn chỉ để “kiểm tra xem có đọc được không”.

Tài liệu Java SE 17 cảnh báo deserialize dữ liệu không đáng tin là thao tác nguy hiểm; `readObjectNoData()` không làm thay đổi cảnh báo ấy.

## 13. Khác biệt với `Externalizable`, enum và record

### `Externalizable`

`Externalizable` extends `Serializable` nhưng quản lý dữ liệu theo `writeExternal(ObjectOutput)` và `readExternal(ObjectInput)`, thông thường cần constructor public không tham số để khôi phục. Không sử dụng bảng quy tắc “thêm superclass Serializable thì hook `readObjectNoData` sẽ xử lý” như một cơ chế migration chuẩn dành cho `Externalizable`. Tùy bài toán, lớp phải tự version dữ liệu trong custom format của mình.

### Enum

Enum có hình thức serialization chuyên biệt dựa trên tên hằng enum; các hook `readObject`, `readObjectNoData`, `readResolve` do kiểu enum định nghĩa bị bỏ qua trong quá trình deserialize enum. Đừng thử hook này vào enum để sửa dữ liệu khi đổi hierarchy: enum vốn không có hierarchy tùy ý như class thông thường.

### Record (Java 17)

Serializable record được deserialize theo quy tắc riêng, bao gồm việc gọi canonical constructor để khôi phục record components. Những hook serialization nhất định không có ý nghĩa như với class Serializable thông thường. Với record, hãy dùng quy tắc record serialization tương ứng và versioning của component/schema, không dùng nguyên mẫu superclass + `readObjectNoData` ở ví dụ `Receipt`.

## 14. Các lỗi phổ biến và cách sửa

1. **Gọi `readObjectNoData()` trong constructor để “kích hoạt”.** Hook chỉ được Java Serialization gọi trong hoàn cảnh thiếu class data; gọi tay làm sai mục đích.
2. **Viết method `public`/`static` hoặc có tham số.** Không khớp chữ ký private instance method mà Serialization mong đợi.
3. **Nhầm việc thiếu một field với thiếu slot class.** Thêm field vào class không tự kích hoạt hook; dùng `readObject`/`readFields` để xử lý phiên bản field.
4. **Giữ default field initializer rồi nghĩ nó sẽ chạy khi deserialize.** Với Serializable superclass, field mới có thể vẫn là `null`/`0`/`false` nếu không được restore hoặc hook sửa.
5. **Dùng constructor của Serializable superclass để sửa dữ liệu cũ.** Constructor lớp Serializable không chạy theo quy trình bình thường của deserialize.
6. **Thay đổi `serialVersionUID` rồi kỳ vọng `readObjectNoData` cứu tương thích.** UID mismatch có thể dẫn đến `InvalidClassException` trước khi có object hợp lệ.
7. **Dùng cùng thư mục `.class` cho v1 và v2.** Một phiên bản bị ghi đè hoặc classloader tải nhầm; dùng classpath/process độc lập như ví dụ.
8. **Chạy writer phiên bản v2 rồi dùng v2 đọc để kiểm thử dữ liệu v1.** Stream đã có slot superclass mới, không tạo ra hoàn cảnh kích hoạt hook.
9. **Để hook phụ thuộc field của subclass đang khôi phục dở.** Có thể quan sát trạng thái chưa sẵn sàng; chỉ thiết lập state của class hiện tại hoặc dùng validation sau graph restoration.
10. **Trả object không có invariant an toàn sau khi thiếu superclass state.** Nên dùng default fail-safe hoặc `InvalidObjectException`.
11. **Xem thành công deserialize là chứng minh dữ liệu đáng tin.** Không đúng; kiểm tra nguồn, filter, version và invariant.
12. **Gọi hook để “đọc thêm byte” từ `ObjectInputStream`.** Hook `readObjectNoData` không có stream tham số và không có slot data tương ứng để đọc; không truy cập nhầm payload của lớp khác.
13. **Chèn superclass không Serializable và kỳ vọng `readObjectNoData` chạy.** Cần phân biệt constructor khởi tạo nonserializable superclass.
14. **Hiểu `serialVersionUID` là số phiên bản schema tự động upgrade.** Nó là cơ chế kiểm tra tương thích, không tạo migration.
15. **Dùng `readResolve` để thay chức năng của `readObjectNoData`.** `readResolve` có tác dụng thay object, không phải sự kiện “thiếu superclass data” theo class slot.
16. **Không có kiểm thử ngược xuôi giữa các bản release.** Mỗi hướng đọc/ghi có thể khác nhau: forward compatibility và backward compatibility không tự bảo đảm đối xứng.

## 15. Bài tập thực hành có đáp án định hướng

### Bài A — khôi phục field mới ở superclass

Trong ví dụ v2, thêm `private boolean verified;` vào `AuditInfo` và đặt `verified=false` ở `readObjectNoData()`. Vì sao default false cũng có thể xuất hiện nếu bỏ hook? Bạn sẽ phân biệt “cố ý xác nhận chưa verify” với “thiếu dữ liệu” ở tầng nghiệp vụ như thế nào?

**Gợi ý:** Giá trị mặc định một mình không chứa thông tin nguồn gốc. Cần quy tắc migration/version hoặc trạng thái phân biệt như enum `UNKNOWN/PENDING/VERIFIED` với default an toàn.

### Bài B — bỏ hook rồi đọc dữ liệu cũ

Xóa `readObjectNoData` trong `AuditInfo` v2, giữ `source = "field-initializer"` và constructor gán `"constructor"`. Compile lại v2, đọc file v1. Dự đoán giá trị source.

**Đáp án:** `null`, vì khi khôi phục Serializable superclass bị thiếu class data, field initializer/constructor của class ấy không chạy và không có hook khởi tạo.

### Bài C — đổi UID của Receipt

Trong `v2/evolution/Receipt.java`, đổi UID thành `2L`; đọc `receipt.ser` tạo bởi v1 UID `1L`.

**Đáp án định hướng:** Deserialization thất bại vì thông tin version không khớp, thường là `InvalidClassException`. Không mong `readObjectNoData` giúp bảo toàn tương thích.

### Bài D — thêm field trực tiếp vào Receipt

Giữ hierarchy cũ (không thêm `AuditInfo`), chỉ thêm `String status = "NEW"` vào `Receipt` v2, giữ UID 1L. Đọc object v1. Hook `Receipt.readObjectNoData()` có chạy không?

**Đáp án:** Không chỉ vì `status` là field mới. Slot class `Receipt` vẫn tồn tại trong stream; field mới thường nhận `null`, cần xử lý bằng hook `readObject` hoặc version migration riêng.

### Bài E — từ chối stream thiếu state bắt buộc

Sửa `AuditInfo.readObjectNoData()` để ném `InvalidObjectException`. Chạy reader trên dữ liệu v1 và xác nhận không có dòng “v2 read id=...”.

**Đáp án định hướng:** `ObjectInputStream.readObject()` sẽ thất bại và exception lan ra caller, nên mã sau lời gọi không tiếp tục theo luồng thành công.

### Bài F — thử thứ tự class state

Trong `Receipt` v2, thêm custom `private void readObject(ObjectInputStream in)`, gọi `in.defaultReadObject()` rồi in một dòng. Chạy reader dữ liệu v1 và so sánh thứ tự log với `AuditInfo.readObjectNoData`.

**Đáp án định hướng:** Class data được xử lý superclass trước subclass; hook của `AuditInfo` chạy trước hook của `Receipt`. Giữ đúng checked exceptions và `@Serial` trong khai báo.

## 16. Checklist khi phát triển schema evolution

Trước khi phát hành phiên bản lớp Serializable mới:

- [ ] Liệt kê hierarchy cũ và mới, xác định **class slot** nào được thêm, xóa hoặc đổi vị trí.
- [ ] Giữ nguyên tên class và `serialVersionUID` khi muốn duy trì đường tương thích có kiểm soát.
- [ ] Kiểm tra thành phần nào thực sự **Serializable**, phần nào không Serializable và constructor nào sẽ được gọi.
- [ ] Với Serializable superclass mới vắng mặt trong stream cũ, có invariant nào cần `readObjectNoData()` không?
- [ ] Nếu chỉ thêm field ở class cũ, đừng kỳ vọng `readObjectNoData`; kiểm tra `readObject`/`readFields` và defaulting.
- [ ] Thiết kế giá trị migration an toàn; với trạng thái bắt buộc không thể suy ra, từ chối bằng `InvalidObjectException`.
- [ ] Không gọi method overridable từ lúc object chưa khôi phục xong; có kế hoạch kiểm tra invariant sau khi đọc xong graph.
- [ ] Kiểm thử **serialized bytes sinh bởi bản cũ thực sự**, không chỉ round-trip trong bản mới.
- [ ] Chạy test mỗi hướng v1→v2 và v2→v1 nếu sản phẩm cần cả hai; ghi nhận rõ hướng được hỗ trợ.
- [ ] Nếu stream do bên ngoài tạo, sử dụng `ObjectInputFilter` và chính sách từ chối dữ liệu không tin cậy.
- [ ] Không thay serialized format một cách tùy tiện mà không có tài liệu và test hồi quy.

## 17. Tài liệu Java SE 17 chính thống

- [`ObjectInputStream` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html): mô tả `readObjectNoData`, thứ tự khôi phục, constructor của superclass không Serializable.
- [`Serializable` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serializable.html): các serialization hooks và versioning.
- [Java Object Serialization Specification, mục 3.5 — `readObjectNoData`](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/input.html#the-readobjectnodata-method): điều kiện kích hoạt và field default nếu không có hook.
- [Java Object Serialization Specification — Versioning](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/version.html): thay đổi field/hierarchy và các quy tắc tương thích.
- [`ObjectInputFilter` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html): lọc lớp/tham chiếu/độ sâu khi deserialize.
- [`InvalidObjectException` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InvalidObjectException.html): từ chối object không hợp lệ trong quá trình đọc.

## 18. Ghi nhớ

`readObjectNoData()` là callback **đặc biệt của từng lớp Serializable** khi local hierarchy có class cần khôi phục nhưng **stream không chứa class data của chính class đó**. Kịch bản điển hình: v2 chèn thêm Serializable superclass phía trên một subclass mà stream v1 đã ghi.

Phương thức có dạng **`private void readObjectNoData() throws ObjectStreamException`**, không có `ObjectInputStream` tham số. Nó khôi phục invariant của **chính lớp ấy** hoặc từ chối dữ liệu bằng exception. Nó **không tự kích hoạt chỉ vì thêm field** vào class vẫn có slot trong stream, và constructor/field initializer của Serializable superclass cũng không chạy theo lối `new` khi deserialize.

Để dùng an toàn: giữ compatibility của tên class và `serialVersionUID` có chủ đích, tách hai bản classpath để kiểm thử evolution, khôi phục superclass state đúng thời điểm, validate toàn bộ object graph và không deserialize dữ liệu không tin cậy thiếu kiểm soát.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-serialization-003">serialPersistentFields và điều khiển dạng lưu trữ</a>

<details>
<summary>Xem chi tiết</summary>

# serialPersistentFields và điều khiển dạng lưu trữ trong Java Serialization

Khi triển khai `java.io.Serializable`, nhiều lớp ban đầu dựa vào cơ chế mặc định: Java ghi các trường instance không `static`, không `transient`. Nhưng nếu ứng dụng lưu file `.ser` lâu dài hoặc trao đổi đối tượng giữa hai phiên bản chương trình, **tên và kiểu trường được ghi trong stream** trở thành một hợp đồng tương thích. Việc đổi tên một field trong mã nguồn có thể làm thông tin cũ không tự ánh xạ được vào field mới.

`serialPersistentFields` cho phép lớp **khai báo rõ dạng trường được lưu (serialized form)**, tách nó khỏi cấu trúc trường Java hiện tại. Kết hợp với `writeObject`/`readObject` và API `PutField`/`GetField`, ta có thể tiếp tục ghi/đọc cùng một dạng dữ liệu kể cả khi đổi cách lưu trạng thái trong bộ nhớ.

**Phạm vi:** Đây là cơ chế dành cho Java Object Serialization của các lớp Serializable thông thường. Không áp dụng trực tiếp cho JSON, JPA, protobuf, Java Records hoặc `Externalizable`. Định dạng `.ser` là stream nhị phân có class descriptor, tham chiếu đối tượng và dữ liệu của từng lớp serializable trong cây kế thừa; nó không chỉ là một bảng các field.

## 1. Tách trạng thái trong bộ nhớ khỏi serialized form

Giả sử phiên bản cũ có lớp:

```java
import java.io.Serializable;

public class Customer implements Serializable {
    private static final long serialVersionUID = 7L;

    String fullName;
    int points;
}
```

Với serialization mặc định, hai trường được ghi là `fullName:java.lang.String` và `points:int`. Nếu phiên bản mới đổi chúng thành `displayName` và `rewardBalance`, việc giữ UID `7L` **không tự đổi tên dữ liệu trên stream**. Khi đọc file cũ, tên mới không khớp nên mặc định nhận `null`/`0`, còn dữ liệu trường tên cũ bị bỏ qua. Khi phiên bản mới ghi file, phiên bản cũ cũng không tự tìm được tên trường mới.

Ta muốn lớp mới sử dụng tên trường tốt hơn trong code, nhưng tiếp tục công bố các field lịch sử `fullName`, `points` trên stream:

```text
Phiên bản 1                         Serialized form ổn định                 Phiên bản 2
fullName: String  ────────────────> fullName: String <──────────────────> displayName: String
points: int       ────────────────> points: int      <──────────────────> rewardBalance: int
```

Chiều mũi tên từ v2 vào wire format do **code ánh xạ tường minh** xử lý. Đây là lý do chính để dùng `serialPersistentFields`.

## 2. Khai báo serialPersistentFields đúng hợp đồng

Trong lớp triển khai `Serializable`, định nghĩa trường đặc biệt như sau:

```java
import java.io.ObjectStreamField;
import java.io.Serializable;

public class Customer implements Serializable {
    private static final long serialVersionUID = 7L;

    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("fullName", String.class),
        new ObjectStreamField("points", int.class)
    };

    private String displayName;
    private int rewardBalance;
}
```

Đây là **đoạn khai báo có thể biên dịch**, nhưng chưa phải ví dụ ánh xạ dữ liệu hoàn chỉnh. Vì tên các field trong mảng không giống field vật lý, phần 4 sẽ triển khai thêm `writeObject/readObject`.

Các quy tắc:

1. Tên bắt buộc là chính xác `serialPersistentFields`, kiểu `ObjectStreamField[]`, modifiers `private static final`. Nếu khai báo sai tên, thiếu modifiers, giá trị `null` hoặc kiểu không đúng, cơ chế sẽ coi như không có khai báo và quay về tính các field mặc định; vì vậy lỗi có thể âm thầm làm thay đổi schema.
2. Mỗi `new ObjectStreamField("tên", Kiểu.class)` mô tả **một field trên stream**, gồm tên và kiểu. Đối với primitive dùng `int.class`, `long.class`..., không dùng `Integer.class` để thay thế `int.class`. Java không tự ép kiểu khi giải tuần tự hóa.
3. Có thể mô tả field **không tồn tại thật** trong lớp Java mới (field ảo/virtual persistent field). Mục đích là giữ tên wire schema cũ trong khi trường bộ nhớ đã thay đổi.
4. Field vật lý không nằm trong mảng thì **không thuộc dạng lưu trữ của lớp đó** theo cơ chế này. Ngược lại, field được liệt kê có thể được ghi ngay cả khi field vật lý không tồn tại. Một field `transient` không tự bảo đảm loại trừ nếu chính sách persistent fields chủ động đưa tên đó vào schema.
5. Mảng rỗng `new ObjectStreamField[0]` có nghĩa không có field persistent cho **lớp này**, nhưng không loại bỏ thông tin class descriptor hoặc dữ liệu của các lớp serializable khác trong cây kế thừa.
6. Mảng `ObjectStreamField` không chứa giá trị dữ liệu đối tượng; nó chỉ khai báo tên/kiểu (và tùy chọn `unshared`). Những giá trị cụ thể được lấy từ object khi ghi, và gán lại khi đọc.

`serialVersionUID` và `serialPersistentFields` giải quyết hai việc khác nhau. UID phục vụ kiểm tra phiên bản lớp khi đọc; persistent fields định nghĩa tập trường serializable. **Cùng UID không bảo đảm tương thích** nếu đã đổi tên, kiểu, thứ tự thông tin tùy biến hoặc hợp đồng dữ liệu theo cách không xử lý được.

## 3. API điều khiển quá trình ghi và đọc

### Ghi: putFields → put → writeFields

Trong phương thức private `writeObject(ObjectOutputStream out)`, gọi:

```java
ObjectOutputStream.PutField fields = out.putFields();
fields.put("fullName", displayName);
fields.put("points", rewardBalance);
out.writeFields();
```

Đây là **đoạn mã đặt bên trong `writeObject`**, không phải chương trình độc lập.

- `out.putFields()` tạo bộ chứa các giá trị của field đã khai báo trong serialized form hiện tại. Chỉ được gọi từ `writeObject` hợp lệ.
- `fields.put("fullName", displayName)` đưa giá trị nội bộ `displayName` vào tên wire field cũ `fullName`. Các overload của `put` tương ứng primitive hoặc reference.
- `out.writeFields()` ghi các giá trị đã đặt theo thứ tự chuẩn do protocol quyết định, không phải thứ tự ta gọi `put`. Field bỏ qua trong `put` được ghi giá trị mặc định của kiểu.
- Không gọi lẫn `defaultWriteObject()` và `putFields()/writeFields()` cho cùng dữ liệu bắt buộc của một lần `writeObject`.

**Không gọi `out.writeObject(displayName)` thay cho `putFields`** rồi cho rằng stream sẽ có field tên `fullName`. Ghi object trực tiếp tạo *optional data* riêng, không tương đương phần field bắt buộc trong class descriptor; nếu thay đổi hợp đồng đọc/ghi tùy biến bừa bãi, các phiên bản có thể không tương thích.

### Đọc: readFields → get → kiểm tra bất biến

Trong phương thức private `readObject(ObjectInputStream in)`, gọi:

```java
ObjectInputStream.GetField fields = in.readFields();
displayName = (String) fields.get("fullName", null);
rewardBalance = fields.get("points", 0);
```

Đây cũng là **đoạn mã bên trong `readObject`**.

`in.readFields()` lấy dữ liệu persistent fields từ stream, cho phép truy vấn bằng tên với overload `get(String, <giá trị mặc định>)`. `fields.defaulted("fullName")` kiểm tra tên field có vắng trong stream hiện tại hay không. Nó hữu ích khi cần phân biệt “file cũ chưa có field” với “file có field nhưng giá trị là `null`”.

Cần validate dữ liệu sau đọc: constructor/initializer của chính lớp `Serializable` không chạy trong giải tuần tự hóa thông thường. Vì thế object có thể ở trạng thái mà constructor thường không chấp nhận. Có thể ném `InvalidObjectException` nếu bất biến không đạt.

### Khi nào dùng defaultWriteObject/defaultReadObject?

Nếu tên và kiểu các field trong `serialPersistentFields` tương ứng với field instance thực tế và không cần đổi giá trị, cơ chế mặc định vẫn có thể ánh xạ chúng. Trong một phương thức `writeObject` tùy biến, `defaultWriteObject()` ghi phần field bắt buộc theo serialized form; `defaultReadObject()` đọc và gán phần field tương ứng. Tuy nhiên **không thể trông chờ defaultWriteObject/defaultReadObject tự biết rằng `displayName` là tên mới của `fullName`**. Muốn ánh xạ tên ảo hoặc chuyển đổi giá trị, dùng `PutField/GetField`.

## 4. Ví dụ đầy đủ chạy được: thay tên field nội bộ, giữ wire schema

Ta sẽ viết bằng v1, đọc bằng v2, sau đó viết bằng v2 và đọc lại bằng v1. Để tránh hai lớp `Customer` cùng tên xung đột, hãy đặt source trong hai thư mục riêng và chạy từng phiên bản với classpath riêng. Ví dụ chạy với JDK 17+ (kể cả Java 21).

### Phiên bản 1 — lớp truyền thống dùng default serialization

Tạo `v1/Customer.java`:

```java
import java.io.Serializable;

public final class Customer implements Serializable {
    private static final long serialVersionUID = 7L;

    String fullName;
    int points;

    public Customer(String fullName, int points) {
        this.fullName = fullName;
        this.points = points;
    }
}
```

Tạo `v1/LegacyApp.java`:

```java
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class LegacyApp {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("write|read <file>");
        }

        if ("write".equals(args[0])) {
            Customer c = new Customer("Lan", 120);
            try (ObjectOutputStream out =
                    new ObjectOutputStream(new FileOutputStream(args[1]))) {
                out.writeObject(c);
            }
            System.out.println("v1 wrote: " + c.fullName + "/" + c.points);
        } else if ("read".equals(args[0])) {
            try (ObjectInputStream in =
                    new ObjectInputStream(new FileInputStream(args[1]))) {
                Customer c = (Customer) in.readObject();
                System.out.println("v1 read: " + c.fullName + "/" + c.points);
            }
        } else {
            throw new IllegalArgumentException("Unknown command");
        }
    }
}
```

### Phiên bản 2 — tên field nội bộ đã đổi nhưng tên persistent field vẫn như v1

Tạo `v2/Customer.java`:

```java
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;

public final class Customer implements Serializable {
    private static final long serialVersionUID = 7L;

    // Cam kết giữ schema đã dùng từ v1:
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("fullName", String.class),
        new ObjectStreamField("points", int.class)
    };

    // Dữ liệu nội bộ đã đổi tên, không trùng tên wire fields:
    private String displayName;
    private int rewardBalance;

    public Customer(String displayName, int rewardBalance) {
        if (displayName == null || displayName.isBlank() || rewardBalance < 0) {
            throw new IllegalArgumentException("Invalid customer");
        }
        this.displayName = displayName;
        this.rewardBalance = rewardBalance;
    }

    public String displayName() {
        return displayName;
    }

    public int rewardBalance() {
        return rewardBalance;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        ObjectOutputStream.PutField fields = out.putFields();
        fields.put("fullName", displayName);
        fields.put("points", rewardBalance);
        out.writeFields();
    }

    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        ObjectInputStream.GetField fields = in.readFields();
        displayName = (String) fields.get("fullName", null);
        rewardBalance = fields.get("points", 0);

        if (displayName == null || displayName.isBlank()
                || rewardBalance < 0) {
            throw new InvalidObjectException("Invalid customer state");
        }
    }
}
```

Tạo `v2/NewApp.java`:

```java
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class NewApp {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("write|read <file>");
        }

        if ("write".equals(args[0])) {
            Customer c = new Customer("Mai", 75);
            try (ObjectOutputStream out =
                    new ObjectOutputStream(new FileOutputStream(args[1]))) {
                out.writeObject(c);
            }
            System.out.println("v2 wrote: "
                    + c.displayName() + "/" + c.rewardBalance());
        } else if ("read".equals(args[0])) {
            try (ObjectInputStream in =
                    new ObjectInputStream(new FileInputStream(args[1]))) {
                Customer c = (Customer) in.readObject();
                System.out.println("v2 read: "
                        + c.displayName() + "/" + c.rewardBalance());
            }
        } else {
            throw new IllegalArgumentException("Unknown command");
        }
    }
}
```

Từ thư mục cha chứa `v1/` và `v2/`, chạy lần lượt:

```bash
javac -encoding UTF-8 -d v1-classes v1/Customer.java v1/LegacyApp.java
javac -encoding UTF-8 -d v2-classes v2/Customer.java v2/NewApp.java
java -cp v1-classes LegacyApp write legacy.ser
java -cp v2-classes NewApp read legacy.ser
java -cp v2-classes NewApp write current.ser
java -cp v1-classes LegacyApp read current.ser
```

Stdout của bốn lệnh `java`:

```text
v1 wrote: Lan/120
v2 read: Lan/120
v2 wrote: Mai/75
v1 read: Mai/75
```

Kết quả giải thích được cả hai chiều:

1. File `legacy.ser` do v1 tạo có tên class `Customer`, UID `7L`, persistent fields `fullName: String` và `points: int`. V2 dùng `readFields()` để đưa chúng vào `displayName` và `rewardBalance`.
2. V2 ghi `current.ser` thông qua `putFields/writeFields`; dù class v2 không hề có field instance tên `fullName` hay `points`, stream **vẫn có các trường tương thích với v1**. V1 đọc lại bằng default serialization.
3. Các lớp ở hai thư mục phải có **cùng tên lớp nhị phân** (`Customer` ở default package trong ví dụ) và UID tương thích. Thực tế nên dùng package ổn định và biên dịch mỗi phiên bản thành artifact khác nhau.
4. Hai file có thể khác nhau về bytes vì stream descriptor của v2 có cờ cho phương thức ghi tùy biến. Mục tiêu là **đọc được dữ liệu và khôi phục đúng trạng thái**, không yêu cầu `legacy.ser` và `current.ser` giống byte-for-byte.

Nếu chỉ đổi trường v2 thành `displayName/rewardBalance` mà bỏ mảng `serialPersistentFields` và bộ ánh xạ, Java sẽ mặc định ghi/đọc tên mới: bài thử trên không còn giữ được hai field cũ một cách trọn vẹn.

## 5. Kiểm tra schema thực sự mà JVM công bố

Có thể xem danh sách persistent fields bằng `ObjectStreamClass`. Trong ví dụ v2, bổ sung đoạn sau vào bất kỳ phương thức `main` nào cùng classpath v2 (đây là **đoạn mã minh họa**, cần `import java.io.ObjectStreamClass;` và `import java.io.ObjectStreamField;`):

```java
ObjectStreamField[] declared =
        ObjectStreamClass.lookup(Customer.class).getFields();

for (ObjectStreamField field : declared) {
    System.out.println(field.getName() + ":"
            + field.getType().getSimpleName());
}
```

Output tương ứng (các field primitive được sắp trước field object theo tên):

```text
points:int
fullName:String
```

Dù trường instance của lớp v2 là `displayName` và `rewardBalance`, descriptor vẫn công bố `points` và `fullName`. Đây là cách kiểm tra hữu ích sau khi refactor hoặc cập nhật thư viện. `ObjectStreamClass.lookup` có thể trả về `null` cho lớp không Serializable.

Một biến thể khác là liệt kê chính xác các field đang được serialize mà không cần giữ tên cũ. Tuy nhiên nếu dữ liệu đã phát hành dùng schema cũ, **đổi persistent schema cũng là thay đổi hợp đồng**. Cần lên kế hoạch đọc dữ liệu cũ và kiểm tra compatibility bằng fixture.

## 6. Phạm vi ảnh hưởng và các tình huống nâng cao

| Cách xử lý | Serialized fields | Khi nào phù hợp | Lưu ý |
| --- | --- | --- | --- |
| Chỉ `implements Serializable` | Các field instance không `static/transient` | Lớp đơn giản, không có schema lâu dài | Refactor tên field ảnh hưởng stream |
| `transient` | Bỏ một field khỏi tập mặc định | Dữ liệu tạm, cache có thể tạo lại | Dữ liệu mất lúc khôi phục; không đủ để kiểm soát toàn bộ schema |
| `serialPersistentFields` | Tập tên/kiểu được khai báo tường minh | Ổn định giao thức field và ẩn cấu trúc nội bộ | Field ảo cần chủ động ánh xạ giá trị |
| `writeObject/readObject` + `PutField/GetField` | Tập field tường minh và logic ánh xạ | Đổi tên field, chuẩn hóa dữ liệu, migrate dạng lưu trữ | Duy trì thứ tự và sự hiện diện của required data |
| `Externalizable` | Hoàn toàn tự định nghĩa qua `writeExternal/readExternal` | Kiểm soát protocol thấp hơn | Cơ chế và quy tắc versioning riêng; không dùng `serialPersistentFields` như Serializable |
| Serialization proxy (`writeReplace/readResolve`) | Một đối tượng proxy độc lập đại diện trạng thái | Các kiểu bất biến hoặc có bất biến chặt | Cần thiết kế tương thích với stream đã tồn tại |

### Field thiếu trong một phiên bản cũ

Nếu v3 thêm persistent field `String membershipLevel` và v3 đọc stream v1/v2 chưa có field này, `GetField` có thể kiểm tra:

```java
String level = fields.defaulted("membershipLevel")
        ? "STANDARD"
        : (String) fields.get("membershipLevel", null);
```

Đây là **đoạn đặt trong `readObject` v3**, chỉ hợp lệ khi `membershipLevel` đã được khai báo trong `serialPersistentFields` của v3. `defaulted` hỏi field có vắng trong stream hay không, **không** hỏi nó có `null`. Cần phân biệt để không ghi đè một giá trị `null` vốn có chủ ý.

Nếu v3 muốn viết file mà v1/v2 đọc được, vẫn phải bảo toàn những persistent fields mà code cũ cần. Field bổ sung thường được code cũ bỏ qua, nhưng tính đúng đắn nghiệp vụ vẫn cần kiểm thử. Nếu đổi kiểu field, ví dụ `points:int` thành `points:long`, giữ cùng tên hoặc UID **không khiến Java tự chuyển đổi**. Có thể giữ wire field `points:int` và tự xử lý `long` trong nội bộ, đồng thời kiểm soát range/overflow, hoặc thiết kế migration định dạng mới.

### Tuỳ chọn unshared của ObjectStreamField

`new ObjectStreamField("name", SomeClass.class, true)` yêu cầu xử lý field tham chiếu như unshared trong cơ chế của serialization. Nó ảnh hưởng chia sẻ reference/aliasing của trường đối tượng, không phải cách đặt tên field hay tăng UID. Chỉ dùng khi hiểu rõ đồ thị object và yêu cầu giữ hay cắt sharing; không bật tuỳ tiện trong bản refactor đơn giản.

### Đa kế thừa, inner class và lớp đặc biệt

Serialized form được xử lý theo **từng lớp Serializable** trong chuỗi kế thừa, không chỉ lớp lá. `serialPersistentFields` của lớp con không thay schema của superclass. Những thay đổi hierarchy và custom write/read data có thể ảnh hưởng mạnh đến tương thích.

Non-static inner class không phải lựa chọn tốt để xây schema serialized ổn định: chúng thường có tham chiếu ngầm tới enclosing instance, và không thể khai báo `private static final ObjectStreamField[]` theo cách cần thiết trong trường hợp phổ biến. Dùng top-level class hoặc static nested class cho mô hình cần custom serialized form. Đối với enum và record, quy tắc serialization khác với lớp thông thường; không áp dụng máy móc ví dụ này.

## 7. Lỗi phổ biến và dấu hiệu nhận biết

| Triệu chứng | Nguyên nhân khả dĩ | Kiểm tra/khắc phục |
| --- | --- | --- |
| Sau refactor, field mới đọc được `null` hoặc `0` | Chỉ đổi tên field trong code, không giữ wire field hay không có mapping | Khai báo persistent schema cũ và chuyển giá trị bằng `GetField` |
| File ghi bằng v2 bị v1 đọc thành giá trị mặc định | V2 ghi tên/kiểu field mới, bỏ các field v1 cần | Giữ tên/kiểu persistent fields cũ, kiểm thử chiều v2 → v1 |
| `InvalidClassException` | UID không khớp hoặc dạng serializable class/field không tương thích | So sánh UID, classpath, field names/types, custom protocol |
| `IllegalArgumentException` khi gọi `put/get` | Tên field không có trong declared persistent fields hoặc overload không phù hợp | Đối chiếu `ObjectStreamClass.getFields()` và đúng kiểu Java |
| `NotActiveException` | Gọi `putFields/readFields/defaultWriteObject` ngoài ngữ cảnh serialization hợp lệ | Chỉ gọi trong các hook private đúng chữ ký |
| Chạy nhưng stream không có các trường mong muốn | `serialPersistentFields` bị khai báo sai modifiers/kiểu, hoặc không ghi giá trị field ảo | Kiểm tra khai báo và lớp descriptor; thử fixture thực tế |
| Cùng một dữ liệu, object sau đọc có trạng thái vô lý | Bỏ qua kiểm tra bất biến hoặc không chạy initializer của Serializable | Validate trong `readObject`, ném `InvalidObjectException` |
| `NotSerializableException` khi ghi | Giá trị tham chiếu trong persistent fields dẫn đến object không Serializable | Điều chỉnh mô hình, lưu representation an toàn, hoặc đánh dấu không lưu |
| Dữ liệu đọc được nhưng code cũ hiểu sai | Binary compatibility khác semantic compatibility | Áp dụng quy tắc tương thích nghiệp vụ và migration nếu cần |

Một bẫy đặc biệt là **trộn phần required fields với optional custom data**. Khi sử dụng `writeObject`, đảm bảo cơ chế ghi field bắt buộc (`defaultWriteObject` hoặc `writeFields`) luôn khớp với cơ chế đọc (`defaultReadObject` hoặc `readFields`) ở những phiên bản cần tương thích. Sau phần bắt buộc mới có thể ghi optional data bổ sung; cả writer và reader phải thống nhất thứ tự, version marker và cách xử lý thiếu thông tin. Đừng chuyển toàn bộ nội dung sang các lệnh `writeInt/writeObject` tùy hứng mà vẫn kỳ vọng phiên bản cũ đọc được field như trước.

## 8. Giới hạn, an toàn và phương án thiết kế

`serialPersistentFields` **không phải công cụ bảo mật**. Nó giúp giới hạn serialized state của một lớp, nhưng không xác thực stream, không mã hóa dữ liệu và không chống khai thác đối tượng khi giải tuần tự hóa. Đừng giải tuần tự hóa dữ liệu Java từ nguồn không tin cậy. Khi bắt buộc sử dụng, giới hạn loại class/độ sâu/số tham chiếu/kích thước bằng `ObjectInputFilter` và chính sách kiểm soát đầu vào; giữ bất biến sau đọc.

Việc bỏ một thông tin bí mật khỏi `serialPersistentFields` chỉ ảnh hưởng file ghi mới; các file cũ có thể vẫn chứa dữ liệu đó. Nếu cần loại bỏ bí mật đã lưu, phải có kế hoạch xử lý file/backup cũ.

Không nên dùng Java native serialization cho hợp đồng lưu trữ liên ngôn ngữ hoặc API công khai mới. Với schema cần tồn tại lâu và migration phức tạp, ưu tiên định dạng có phiên bản/schema rõ ràng (ví dụ JSON có version kèm validation, protobuf hoặc Avro tùy yêu cầu). Nếu hệ thống đã phụ thuộc Java serialization, `serialPersistentFields` hữu ích để giảm phụ thuộc giữa wire schema và field vật lý, đi kèm fixture compatibility mỗi lần nâng cấp.

## 9. Checklist thay đổi một lớp có dữ liệu serialized đang tồn tại

1. Ghi lại tên lớp/package, `serialVersionUID` và descriptor của các phiên bản đang hỗ trợ; giữ file fixture thật.
2. Liệt kê chính xác tên/kiểu persistent fields cũ. Quyết định field nào buộc phải giữ và trường nào có migration.
3. Khai báo `serialPersistentFields` đúng modifiers và kiểu; nếu field mới không trùng field vật lý, triển khai `writeObject`/`readObject` bằng `PutField/GetField`.
4. Validate trạng thái sau `readFields()`, kể cả trường hợp field vắng, giá trị `null`, số âm, đối tượng tham chiếu hoặc dữ liệu không hợp lệ.
5. Chạy fixture **v1 → v2** và nếu có yêu cầu rollback/đa phiên bản, chạy thêm **v2 → v1**. Kiểm tra giá trị, bất biến và hành vi nghiệp vụ chứ không chỉ “không ném exception”.
6. Duy trì test khi thêm, đổi kiểu, xóa, chuyển `transient`, thay hierarchy hoặc đổi optional custom data. Nếu không thể giữ tương thích, thay giao thức một cách có chủ đích và có migration.

## 10. Câu hỏi tự kiểm tra

1. V2 đổi `fullName` thành `displayName` nhưng vẫn dùng mặc định và giữ UID. Java có tự chuyển giá trị khi đọc file v1 không? **Không.** Cần mapping explicit hoặc một bước migrate.
2. Nếu `serialPersistentFields` có `new ObjectStreamField("oldName", String.class)` nhưng class chỉ có `newName`, dùng `defaultWriteObject()` có tự lấy `newName` không? **Không.** Hãy dùng `PutField.put("oldName", newName)` rồi `writeFields()`.
3. Mảng `serialPersistentFields` chỉ định tên/kiểu hay chứa giá trị mỗi object? **Chỉ định schema.** Giá trị được ghi khi serialize.
4. `GetField.defaulted("status")` khác kiểm tra `get("status", null) == null` thế nào? **Một bên kiểm tra field vắng khỏi stream, bên kia có thể gặp field hiện diện nhưng mang `null`.**
5. Vì sao cần test v2 → v1 dù v2 đọc được dữ liệu v1? **Vì khả năng đọc dữ liệu cũ không tự chứng minh v2 vẫn ghi đúng schema mà v1 mong đợi.**

**Tài liệu chính thức:** Java Object Serialization Specification — *1.5 Defining Serializable Fields for a Class*, *1.7 Accessing Serializable Fields of a Class* và *Chapter 5: Versioning of Serializable Objects*. Xem thêm JDK API `ObjectStreamField`, `ObjectStreamClass`, `ObjectOutputStream.PutField` và `ObjectInputStream.GetField`:

- https://docs.oracle.com/en/java/javase/11/docs/specs/serialization/serial-arch.html
- https://docs.oracle.com/en/java/javase/26/docs/specs/serialization/version.html
- https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectStreamField.html
- https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.GetField.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-serialization-004">writeReplace, readResolve và thay thế đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# writeReplace, readResolve và thay thế đối tượng trong Java Serialization

## 1. Mục tiêu và phạm vi

Khi tuần tự hóa một object graph bằng `ObjectOutputStream` và khôi phục nó bằng `ObjectInputStream`, Java cho phép đối tượng **tự chỉ định một đối tượng khác thay thế nó**. Hai hook quan trọng là:

- `writeReplace()`: được xét **trước khi ghi đối tượng**; đối tượng có thể đề xuất một đại diện khác để lưu vào stream.
- `readResolve()`: được xét **sau khi đọc và khôi phục đối tượng**; đối tượng đã đọc có thể đề xuất một đối tượng khác để trả về cho nơi sử dụng.

Hai cơ chế này giúp thiết kế **serialization proxy**, bảo vệ bất biến dữ liệu, xây dựng lại tài nguyên chỉ tồn tại lúc chạy và giữ identity của các đối tượng chuẩn hóa (canonical object), chẳng hạn singleton. Cần hiểu rõ giới hạn: thay thế diễn ra trong **quá trình serialization/deserialization**, không có nghĩa biến tham chiếu trong ứng dụng tự đổi ngay khi gọi `writeObject`.

Bài học áp dụng cho Java Object Serialization trong `java.io`, ưu tiên **Java 17+**; các thư viện JSON/XML có quy tắc riêng, không tự động gọi hai hook này.

## 2. Cơ sở: Java ghi một object graph như thế nào?

`ObjectOutputStream.writeObject(obj)` xử lý đối tượng gốc và các đối tượng mà nó tham chiếu tới. Mỗi đối tượng có thể được gán một **handle** trong stream để các tham chiếu lặp lại trỏ về cùng một thực thể đã ghi. Khi đọc, `ObjectInputStream` dùng handle để bảo toàn quan hệ chia sẻ tham chiếu phù hợp với quy tắc giao thức.

Ở cơ chế thông thường:

1. Bên ghi xác định đối tượng cần serialization và kiểm tra có phương thức `writeReplace` thích hợp hay không.
2. Nếu `writeReplace` trả về đại diện khác, Java tiếp tục xử lý **đại diện đó** để ghi. Đối tượng đại diện phải hợp lệ cho serialization (hoặc là một trường hợp đặc biệt được định dạng hỗ trợ, như `null`).
3. Bên đọc khôi phục đối tượng theo mô tả trong stream; đây có thể là **lớp đại diện** chứ không phải lớp gốc.
4. Nếu lớp được khôi phục có `readResolve` thích hợp, giá trị trả về từ phương thức đó thay thế đối tượng đang đọc trong kết quả và bảng tham chiếu tương ứng, với một số giới hạn về chu trình đối tượng.
5. `readObject()` của ứng dụng nhận kết quả cuối cùng và thường cần ép kiểu về hợp đồng mà nó mong đợi.

Sơ đồ cho mẫu proxy:

~~~text
Ứng dụng ghi                         Object stream                  Ứng dụng đọc
Original o                          dạng của Proxy p                Original mới
    |                                      ^                             ^
    | writeReplace()                       |                             |
    +------------------> Proxy p ---------+                             |
                                                                         |
                         ObjectInputStream -> Proxy p' -> readResolve() --+
~~~

Dấu `'` trong sơ đồ chỉ đại diện mới được khôi phục, không phải cùng identity với proxy đã tạo ở bên ghi.

### 2.1. Chữ ký hook chuẩn

Một class triển khai `Serializable` (hoặc `Externalizable` khi phù hợp) có thể khai báo:

~~~java
@Serial
private Object writeReplace() throws ObjectStreamException {
    // Trả về đối tượng có thể lưu vào stream.
    return this;
}

@Serial
private Object readResolve() throws ObjectStreamException {
    // Trả về đối tượng ứng dụng sẽ sử dụng sau khi đọc.
    return this;
}
~~~

Đây là **mã minh họa đặt trong thân một class serializable**, không phải chương trình Java độc lập. `ObjectStreamException` thuộc `java.io`, annotation `@Serial` cũng thuộc `java.io` và được hỗ trợ từ Java 14; có thể bỏ phần `throws` khi phương thức không ném ngoại lệ checked.

Những yêu cầu quan trọng:

- Tên phải chính xác `writeReplace` / `readResolve`, **không có tham số**, kiểu trả về là `Object` trong chữ ký serialization quy định. Có thể dùng modifier truy cập phù hợp: `private`, package-private, `protected` hoặc `public`. Việc kế thừa và khả năng truy cập tuân theo quy tắc Java; hook `private` của superclass không được subclass kế thừa.
- Phương thức không phải API bạn gọi thủ công để điều khiển luồng; `ObjectOutputStream` / `ObjectInputStream` tự phát hiện và gọi chúng khi xử lý đối tượng.
- Vì không phải override một phương thức của `Serializable`, **không ghi `@Override`**. Dùng `@Serial` khi thích hợp để compiler hỗ trợ kiểm tra khai báo.
- Một class serializable có thể chỉ có `writeReplace`, chỉ có `readResolve` hoặc cả hai. Hook ở **class đang thực sự được ghi/đọc**, không nhất thiết ở class được truyền ban đầu.

## 3. `writeReplace()`: thay dạng lưu trước khi ghi

### 3.1. Dùng khi nào?

`writeReplace` thường phù hợp khi model runtime có những thứ không nên/không thể lưu trực tiếp, ví dụ `Thread`, connection, executor, tài nguyên tạm thời, hoặc cấu trúc nội bộ mà bạn không muốn đóng băng thành hợp đồng tương thích lâu dài.

Thay vì ghi mọi field của object thật, hook trả về một **serialization proxy** gồm các trường dữ liệu thiết yếu và đã được chọn lọc. Proxy này tự triển khai `Serializable` và có thể dùng `readResolve` để tái tạo đối tượng hợp lệ.

Cần phân biệt `writeReplace` với `writeObject`:

| Cơ chế | Tác động | Kết quả |
| --- | --- | --- |
| `writeObject(ObjectOutputStream)` (hook của class) | Tùy biến cách lưu field/dữ liệu của chính class | Class vẫn là class của đối tượng được ghi |
| `writeReplace()` | Đề xuất **đối tượng khác** làm dạng biểu diễn trong stream | Class ghi thực tế có thể là class proxy khác |
| `transient` | Bỏ qua field trong cơ chế lưu field mặc định | Không tự tạo proxy, không đổi class của bản ghi |

Đánh dấu `transient` có ích khi field đơn giản chỉ là cache; serialization proxy hữu ích khi cần **kiểm soát toàn bộ dạng lưu** hoặc phải khôi phục bất biến của đối tượng.

### 3.2. Ví dụ chạy được: proxy thay đối tượng chứa `Thread`

Tạo file `ReplacementProxyDemo.java`:

~~~java
import java.io.*;

public class ReplacementProxyDemo {
    static final class TaskSession implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String taskId;
        private final Thread runtimeThread = new Thread();

        TaskSession(String taskId) {
            this.taskId = taskId;
        }

        @Serial
        private Object writeReplace() throws ObjectStreamException {
            return new SessionProxy(taskId);
        }
    }

    private static final class SessionProxy implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String taskId;

        SessionProxy(String taskId) {
            this.taskId = taskId;
        }

        @Serial
        private Object readResolve() throws ObjectStreamException {
            return new TaskSession(taskId);
        }
    }

    public static void main(String[] args) throws Exception {
        TaskSession before = new TaskSession("T-17");
        var bytes = new ByteArrayOutputStream();

        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(before);
        }

        try (var in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            TaskSession after = (TaskSession) in.readObject();
            System.out.println(after.taskId);
            System.out.println(after.runtimeThread != null);
            System.out.println(after.getClass() == TaskSession.class);
        }
    }
}
~~~

Biên dịch/chạy từ thư mục có file:

~~~bash
javac ReplacementProxyDemo.java
java ReplacementProxyDemo
~~~

Kết quả chuẩn:

~~~text
T-17
true
true
~~~

Giải thích theo từng bước:

1. `TaskSession` triển khai `Serializable` nhưng có `Thread runtimeThread`, và `Thread` không được thiết kế để serialized như trạng thái thực thi.
2. Trước khi ghi trường của `TaskSession`, `writeReplace()` trả về `SessionProxy`. Dạng lưu chỉ có `taskId`, không chứa thread đang chạy.
3. Khi đọc, Java khôi phục `SessionProxy`, sau đó gọi `SessionProxy.readResolve()`.
4. `readResolve` tạo một `TaskSession` mới bằng constructor, nên `runtimeThread` được khởi tạo mới và kết quả ép kiểu về `TaskSession` hợp lệ.

Nếu bỏ `writeReplace` mà vẫn giữ field `runtimeThread` không `transient`, việc ghi `TaskSession` sẽ gặp `NotSerializableException` khi duyệt trường này. Ví dụ trên chủ động giữ field đó **không transient** để chỉ ra vai trò của proxy.

**Điểm đặc biệt:** `TaskSession.readResolve` không bắt buộc phải có trong ví dụ; đối tượng `TaskSession` không phải loại bản ghi được khôi phục từ stream. Hook cần thiết nằm ở **`SessionProxy`** — lớp thực sự được đọc.

### 3.3. Hợp đồng kiểu và khả năng tương thích

Khi `writeReplace` trả về một proxy có kiểu khác, lời gọi đọc cuối cùng phải nhận một kiểu phù hợp với nơi sử dụng. Proxy được khôi phục rồi `readResolve` trả về `TaskSession`, vì thế biến `TaskSession after` nhận đúng kiểu.

Một thiết kế sai có thể gây `ClassCastException` khi đối tượng thay thế không phù hợp với nơi được tham chiếu, ví dụ một field mong đợi `TaskSession` nhưng kết quả đọc cuối là `String`. Cần kiểm thử khi original object nằm **bên trong collection hoặc field có kiểu cụ thể**, không chỉ khi nó là root được ghi bởi `writeObject`.

Không dựa vào việc proxy che giấu type để coi đó là mã hóa hoặc bảo mật: đối tượng trong stream vẫn có cấu trúc nhị phân có thể phân tích, và code khôi phục vẫn cần kiểm tra dữ liệu.

## 4. `readResolve()`: chuẩn hóa đối tượng sau khi đọc

### 4.1. Phục hồi identity và invariant

Mặc định, đọc một đối tượng serializable thường tạo ra một instance mới khác identity với instance gốc. Với **singleton**, mô hình này làm sai ràng buộc “trong JVM chỉ có một instance”. `readResolve` cho phép thay instance tạm được dựng từ stream bằng instance chuẩn đang tồn tại.

Các tình huống thường gặp:

- Singleton dùng class có constructor riêng; `readResolve` trả về `INSTANCE`.
- Value object được intern/canonicalize theo key; `readResolve` tra registry để trả về instance chuẩn.
- Serialization proxy cần gọi factory/constructor có validation để khôi phục đối tượng thật.

Với dữ liệu phải được xác minh, validation nên nằm ở constructor/factory được gọi khi khôi phục. Nếu dữ liệu không hợp lệ, có thể ném một `InvalidObjectException` (nhánh `ObjectStreamException`), thay vì chấp nhận object sai trạng thái.

### 4.2. Ví dụ chạy được: singleton sau deserialization

Tạo file `ReadResolveSingletonDemo.java`:

~~~java
import java.io.*;

public class ReadResolveSingletonDemo {
    static final class AppConfig implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        static final AppConfig INSTANCE = new AppConfig();

        private AppConfig() {}

        @Serial
        private Object readResolve() throws ObjectStreamException {
            return INSTANCE;
        }
    }

    public static void main(String[] args) throws Exception {
        var bytes = new ByteArrayOutputStream();

        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(AppConfig.INSTANCE);
            out.writeObject(AppConfig.INSTANCE);
        }

        try (var in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            AppConfig first = (AppConfig) in.readObject();
            AppConfig second = (AppConfig) in.readObject();

            System.out.println(first == AppConfig.INSTANCE);
            System.out.println(second == AppConfig.INSTANCE);
            System.out.println(first == second);
        }
    }
}
~~~

~~~bash
javac ReadResolveSingletonDemo.java
java ReadResolveSingletonDemo
~~~

Kết quả:

~~~text
true
true
true
~~~

Lần `readObject()` đầu tiên khôi phục một instance tạm, nhưng `readResolve` trả `AppConfig.INSTANCE`. Lần đọc tiếp theo cũng tham chiếu cùng instance singleton; object stream còn có cơ chế handle đối với các tham chiếu lặp lại.

**Giới hạn của ví dụ:** việc `first == second` khi ghi lặp lại cùng object vào **một stream** có thể đúng nhờ cơ chế handle, ngay cả khi không có `readResolve`. Phép so sánh quyết định ở đây là `first == AppConfig.INSTANCE`. Để kiểm chứng kỹ, có thể thử ghi và đọc ở **hai stream độc lập**; canonical singleton vẫn là cùng `INSTANCE` trong một JVM nhờ `readResolve`.

### 4.3. Có nên dùng singleton class như vậy?

Nếu không cần hành vi tạo instance đặc biệt và chỉ muốn singleton an toàn trước các trường hợp Java Serialization phổ biến, Java `enum` với một constant thường đơn giản và mạnh hơn:

~~~java
enum AppConfig {
    INSTANCE
}
~~~

Đây là **định nghĩa enum minh họa**, chưa phải chương trình có `main`. Serialization của enum dùng cơ chế đặc biệt: khi đọc, JVM khôi phục constant đã khai báo theo tên; `writeReplace` và `readResolve` tự khai báo trong enum **không được gọi**. Với các singleton cần giữ dữ liệu runtime riêng, cần cân nhắc lifecycle và thread safety thay vì chỉ dựa vào hook.

## 5. Kết hợp `writeReplace` + `readResolve` theo Serialization Proxy Pattern

Mẫu thường dùng:

1. **Original:** class có invariant mạnh, API nghiệp vụ và trạng thái runtime; implements `Serializable` để hook hoạt động.
2. **Proxy:** class `private static final` hoặc class tách riêng; implements `Serializable`; chỉ giữ dạng dữ liệu ổn định như id, giá trị đơn giản, danh sách key.
3. **Original.writeReplace():** tạo proxy từ trạng thái hợp lệ của original.
4. **Proxy.readResolve():** gọi constructor/factory của original để kiểm tra invariant và khôi phục lại đối tượng.
5. **Original.readObject():** nếu muốn ép mọi quá trình đọc phải đi qua proxy, có thể chặn dạng serialization trực tiếp của original bằng hook ném `InvalidObjectException`.

Đoạn mã sau là **phần được thêm bên trong class Original implements Serializable**, minh họa việc từ chối đọc trực tiếp original mà không đi qua proxy:

~~~java
@Serial
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    throw new InvalidObjectException("Proxy required");
}
~~~

Đây không phải chương trình độc lập. Một phiên bản hoàn chỉnh cần có `writeReplace` trên original, proxy serializable và `readResolve` trên proxy như ví dụ ở mục 3. Trong ứng dụng dùng lâu dài, version của **proxy** mới chính là hợp đồng serialized cần bảo trì; tránh thay đổi cấu trúc proxy tùy tiện, hãy đặt `serialVersionUID` và kế hoạch migration rõ ràng.

### 5.1. Ví dụ ngắn về invariant

Giả sử đối tượng nghiệp vụ `Range` yêu cầu `min <= max`. Không nên tin rằng bytes serialized được đưa vào JVM luôn tạo ra trạng thái hợp lệ. Trong proxy, `readResolve` có thể gọi `new Range(min, max)`; constructor kiểm tra invariant và từ chối dữ liệu vô lý. Nếu cần chuyển lỗi sang ngoại lệ I/O nhất quán:

~~~java
@Serial
private Object readResolve() throws ObjectStreamException {
    try {
        return new Range(min, max);  // Constructor kiểm tra min <= max.
    } catch (IllegalArgumentException ex) {
        InvalidObjectException invalid =
                new InvalidObjectException("Invalid range");
        invalid.initCause(ex);
        throw invalid;
    }
}
~~~

Đây là **mã trong lớp proxy của mô hình Range**, với constructor `Range(int, int)` có sẵn; không phải file độc lập. Việc ném `InvalidObjectException` thông báo rõ dữ liệu đọc vi phạm hợp đồng, tránh để đối tượng lỗi đi vào nghiệp vụ.

## 6. Identity, handle và các giới hạn khó nhận ra

### 6.1. Một object được tham chiếu nhiều lần

`ObjectOutputStream` quản lý identity bằng handle. Nếu hai field trỏ đến **cùng một original object**, Java cố gắng bảo toàn quan hệ chia sẻ khi thay thế và đọc lại: cùng handle đối tượng đã ghi sẽ liên kết với cùng kết quả thay thế thích hợp. `writeReplace` không có nghĩa mỗi tham chiếu trong graph gọi hook để tạo một proxy độc lập vô hạn lần.

Tuy nhiên, không được đồng nhất những khái niệm sau:

- **Identity**: `a == b` là cùng instance trong JVM.
- **Equality**: `a.equals(b)` có thể đúng khi hai instance khác nhau.
- **Canonicalization**: nhiều đối tượng tương đương theo key được ánh xạ về một instance chuẩn khi `readResolve` dùng registry/factory.

`readResolve` có thể chủ động gom **nhiều object trong stream** về cùng một instance, làm thay đổi identity theo chính sách ứng dụng; đó là hành vi có chủ đích, không phải mặc định của mọi lần đọc.

### 6.2. Chu trình tham chiếu khi `readResolve` trả đối tượng mới

`readResolve` được gọi sau khi đối tượng đã đọc được dựng xong, do đó **các tham chiếu tới chính object tạm bên trong graph của nó có thể không được sửa thành object thay thế**. Đặc tả serialization cảnh báo đây là vấn đề với chu trình (`cycle`) hoặc self-reference. Mẫu proxy thường an toàn hơn khi proxy chứa **giá trị bất biến đơn giản**, tránh tham chiếu vòng về original.

Tương tự, nếu `writeReplace` trả proxy có graph lại trỏ về original, việc thay thế và phục hồi tham chiếu có thể tạo ra cấu trúc không như mong đợi. Không nên dùng graph proxy chứa back-reference tùy ý nếu chưa có kiểm thử round-trip.

### 6.3. `readUnshared` không phải biện pháp chống canonicalization

`ObjectInputStream.readUnshared()` kiểm soát cách dùng handle đọc lại. Nhưng đối tượng có `readResolve` vẫn có thể trả về singleton/instance từ registry vốn được chia sẻ bên ngoài stream. Vì vậy, `readUnshared` **không bảo đảm** kết quả là một instance mới duy nhất trong JVM.

### 6.4. `null` và kiểu thay thế

Một hook có thể trả về `null`, nhưng nếu caller hoặc field mong đợi một object cụ thể, hậu quả có thể là `NullPointerException` hoặc vi phạm invariant. Cần thiết kế rõ hợp đồng “được phép null” và kiểm thử. Nếu hook trả instance có kiểu khác, phải phù hợp với tất cả nơi sử dụng trong graph; nếu không, `ClassCastException` hoặc lỗi phục hồi khác có thể xảy ra.

## 7. So sánh với các hook và API liên quan

| Cơ chế | Nơi khai báo | Thời điểm / mục đích |
| --- | --- | --- |
| `writeReplace()` | Class serializable | Trước khi ghi object, đề xuất object thay thế |
| `readResolve()` | Class serializable | Sau khi khôi phục object, đề xuất giá trị cuối cùng |
| `private void writeObject(ObjectOutputStream)` | Class serializable | Tùy chỉnh phần dữ liệu của class được ghi |
| `private void readObject(ObjectInputStream)` | Class serializable | Tùy chỉnh phần dữ liệu của class được đọc |
| `ObjectOutputStream.replaceObject(Object)` | Subclass của stream | Hook mức **stream** (cần bật `enableReplaceObject`) để thay đối tượng theo chính sách riêng |
| `ObjectInputStream.resolveObject(Object)` | Subclass của stream | Hook mức **stream** (cần bật `enableResolveObject`) để thay đối tượng sau bước `readResolve` của class |

Các hook mức stream là cơ chế nâng cao và có hệ quả đối với toàn bộ dữ liệu đi qua stream; chúng khác về ownership và phạm vi so với hai hook mức class. Không cần subclass stream chỉ để tạo proxy hoặc singleton thông thường.

**Lưu ý về type:**

- `Serializable` và `Externalizable` có hỗ trợ hook thay thế theo đặc tả.
- Enum có serialization riêng; các hook thay thế khai báo trên enum bị bỏ qua.
- Record triển khai `Serializable` có thể dùng `writeReplace` / `readResolve`, dù cơ chế ghi/đọc field thông thường của record khác với class serializable truyền thống.
- `serialVersionUID` vẫn liên quan tới class thực sự được lưu/đọc. Proxy không loại bỏ nhu cầu quản lý version hoặc tương thích.

## 8. Lỗi phổ biến và cách xử lý

### 8.1. Hook không chạy

Kiểm tra lớp có thực sự triển khai `Serializable`/`Externalizable` không, tên và chữ ký có đúng không, có vô tình thêm tham số hay dùng `static` không. Với phương thức kế thừa, xét phạm vi truy cập theo quy tắc Java; một hook `private` của lớp cha không tự kế thừa cho lớp con. Kiểm tra object thực tế là enum hoặc record nếu hành vi khác dự đoán. Có thể tạm log ở hook khi làm thí nghiệm, rồi xóa log trước môi trường production.

### 8.2. `NotSerializableException` vẫn xuất hiện

Nếu `writeReplace` trả về **proxy không serializable** hoặc proxy vẫn chứa field không serializable, việc ghi vẫn lỗi. Chỉ nói rằng class gốc đã có `writeReplace` là chưa đủ; phải kiểm tra **đồ thị đối tượng của kết quả thay thế**.

### 8.3. `ClassCastException` khi đọc

Một proxy chỉ có `writeReplace` mà không có `readResolve` phù hợp có thể làm `readObject()` trả về proxy thay vì original. Hoặc `readResolve` trả sai kiểu. Kiểm tra cả kiểu root và kiểu field có tham chiếu đến original; đừng chỉ ép kiểu rồi bỏ qua vấn đề thiết kế.

### 8.4. Singleton bị nhân bản

Nếu class singleton được deserialize mà thiếu hook `readResolve` thích hợp, reader có thể trả một instance mới. Giữ `INSTANCE` ở mức `static final`, constructor không public và triển khai đúng hook; với singleton đơn giản, cân nhắc enum. Nhớ kiểm tra cả inheritance/subclass nếu singleton class không phải `final`.

### 8.5. Trạng thái bị mất hoặc khác trước khi lưu

Proxy chỉ lưu những gì nó chứa. Nếu nó chỉ có id nhưng model nghiệp vụ cần timestamp, owner hoặc trạng thái khác, bản khôi phục có thể thiếu thông tin. Cần xác định **bất biến và dữ liệu phải được bảo toàn** trước khi thiết kế proxy, thay vì lược field để tránh lỗi.

### 8.6. Phiên bản cũ không đọc được

Đổi tên class proxy, xóa field cần thiết, đổi `serialVersionUID` hoặc logic khôi phục có thể khiến bản ghi cũ không tương thích. Hãy dùng fixture serialized của phiên bản cũ và kiểm thử migration. Không thay đổi UID tùy tiện nhằm che `InvalidClassException`.

### 8.7. Deserialization dữ liệu không tin cậy

Serialization proxy và `readResolve` **không tự làm Java deserialization an toàn**. Không tạo `ObjectInputStream` trên dữ liệu không tin cậy nếu có thể tránh; khi cần thiết, đặt `ObjectInputFilter` / quy tắc allowlist, giới hạn kích thước, độ sâu, số tham chiếu và validate dữ liệu. Hook có thể gọi constructor/factory và logic ứng dụng; tuyệt đối không coi hook là lớp kiểm tra bảo mật duy nhất.

## 9. Checklist thiết kế và kiểm thử

1. Đối tượng gốc cần bảo toàn **giá trị** hay **identity**? Nếu chỉ cần các trường đơn giản, có thể không cần hook thay thế.
2. Nếu dùng proxy, đã chọn đủ field cho việc khôi phục, tránh lưu tài nguyên runtime và back-reference tạo chu trình?
3. Proxy có `Serializable`, UID ổn định, và `readResolve` trả kiểu phù hợp với mọi field/caller?
4. Đã kiểm tra input trong constructor/factory hoặc hook, và ném exception thích hợp nếu invalid?
5. Đã kiểm thử round-trip một object, nhiều tham chiếu đến cùng object, giá trị null, collection, dữ liệu lịch sử và trường hợp dữ liệu hỏng?
6. Nếu yêu cầu singleton/canonicalization, đã kiểm tra identity so với instance chuẩn, kể cả qua các stream độc lập?
7. Đã có chiến lược version/migration và kiểm soát dữ liệu không tin cậy?

Khi kiểm thử, nên tách **khả năng ghi được stream**, **khả năng khôi phục giá trị đúng** và **tính đúng đắn của identity/invariant** thành các mục riêng; việc `readObject` không ném exception chưa bảo đảm ba điều đó.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** `writeReplace` được gọi ở phía đọc hay phía ghi? Nó có làm biến `original` ngoài ứng dụng trỏ sang proxy sau `writeObject(original)` không?

**Đáp án:** Phía ghi, trước khi ghi đối tượng. Nó không thay đổi biến tham chiếu `original` trong bộ nhớ ứng dụng; chỉ chi phối dạng dữ liệu và ánh xạ tham chiếu được ghi vào stream.

**Câu 2.** Class `Employee` có `writeReplace` trả về `EmployeeProxy`. Khi đọc lại, hook `readResolve` nên đặt ở đâu để trả về `Employee`?

**Đáp án:** Thường ở `EmployeeProxy`, vì stream chứa bản biểu diễn của proxy. `EmployeeProxy.readResolve()` có thể tạo/tra cứu `Employee` hợp lệ và trả về nó.

**Câu 3.** Hai lần đọc cùng singleton từ một stream trả object bằng nhau theo `==`. Điều đó một mình có chứng minh `readResolve` hoạt động?

**Đáp án:** Không. Stream có cơ chế handle để tham chiếu lặp lại. Cần so với singleton chuẩn, hoặc kiểm thử qua nhiều stream độc lập.

**Câu 4.** `readResolve` trả một `String` cho field có kiểu `Employee`. Có rủi ro gì?

**Đáp án:** Kiểu không tương thích; quá trình phục hồi hoặc sử dụng có thể phát sinh `ClassCastException`. Phải bảo toàn hợp đồng kiểu của mọi nơi tham chiếu.

**Câu 5.** Vì sao không nên để proxy trỏ vòng về original?

**Đáp án:** Cơ chế thay thế kết hợp handle và chu trình có thể khiến graph khôi phục khác graph mong đợi; riêng `readResolve` xảy ra sau khi object tạm đã hoàn tất, nên self-reference có thể không được cập nhật. Proxy thuần giá trị giảm rủi ro này.

## 11. Nguồn kỹ thuật để tra cứu sâu

- [Java SE 17 — Serializable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serializable.html): chữ ký, phạm vi truy cập và các hook thay thế.
- [Java Object Serialization Specification — Object Output Classes, mục 2.5](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/output.html): `writeReplace`, ánh xạ đối tượng khi ghi.
- [Java Object Serialization Specification — Object Input Classes, mục 3.7](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/input.html): `readResolve`, xử lý handle và giới hạn đối với graph vòng.
- [Java Object Serialization Specification — System Architecture](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/serial-arch.html): serialization graph, enum, record và thay thế.

Hai hook đều là cơ chế **thay thế object ở ranh giới stream**. `writeReplace` chọn dạng lưu, `readResolve` chọn giá trị sử dụng sau khi đọc; khi kết hợp thành serialization proxy, chúng giúp duy trì hợp đồng dữ liệu và bất biến của đối tượng một cách có chủ đích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-serialization-005">Externalizable, writeExternal và readExternal</a>

<details>
<summary>Xem chi tiết</summary>

# Externalizable, writeExternal và readExternal trong Java Serialization

## 1. Tổng quan và mục tiêu

Trong Java Core I/O, `java.io.Externalizable` là một cơ chế tuần tự hóa đối tượng cho phép **chính lớp đối tượng chủ động ghi và đọc biểu diễn dữ liệu của mình**. Nó mở rộng `Serializable` nhưng khác đáng kể với serialization mặc định:

- `Serializable` thông thường: `ObjectOutputStream` có thể tự ghi các instance field đủ điều kiện, trừ `static` và `transient`, theo quy tắc Java serialization.
- `Externalizable`: runtime gọi **`writeExternal(ObjectOutput)`** khi ghi và **`readExternal(ObjectInput)`** khi đọc. Lập trình viên quyết định cần ghi gì, ghi theo thứ tự nào và khôi phục ra sao.
- Khi deserialize một class `Externalizable` thông thường, Java cần **constructor `public` không tham số** để tạo instance trước khi gọi `readExternal`.
- Việc tự quản lý dữ liệu **không làm mất cơ chế quản lý object handles** của `ObjectOutputStream` khi bạn gọi `writeObject`/`readObject`. Đồ thị tham chiếu và alias có thể được giữ nguyên, kể cả chu trình, nếu thiết kế giao thức đúng.
- `Externalizable` vẫn là **Java native object serialization**, không phải cơ chế giải mã dữ liệu không tin cậy an toàn.

Sau bài này, bạn sẽ hiểu hợp đồng API, viết được ví dụ Java 17+ chạy độc lập, kiểm thử thứ tự/phiên bản của biểu diễn dữ liệu, xử lý reference graph và biết khi nào không nên sử dụng cơ chế này.

## 2. Externalizable là gì?

Khai báo giao diện:

```java
public interface Externalizable extends Serializable {
    void writeExternal(ObjectOutput out) throws IOException;

    void readExternal(ObjectInput in)
            throws IOException, ClassNotFoundException;
}
```

Đây là hình thức khai báo API rút gọn để giải thích; Java đã có interface này trong `java.io`, **không tự định nghĩa lại nó trong dự án**.

Hai phương thức quan trọng:

| Phương thức | Lúc nào chạy? | Chức năng |
|---|---|---|
| `writeExternal(ObjectOutput out)` | Khi `ObjectOutputStream` ghi instance Externalizable mới | Tự viết field/phiên bản/record theo thứ tự bạn chọn |
| `readExternal(ObjectInput in)` | Sau khi instance được tạo để giải tuần tự hóa | Đọc đúng biểu diễn do `writeExternal` tạo và dựng lại trạng thái |

`ObjectOutput` có các thao tác `writeInt`, `writeLong`, `writeBoolean`, `writeUTF`, `writeObject` và `write(byte[])`. `ObjectInput` có các thao tác đối xứng `readInt`, `readLong`, `readBoolean`, `readUTF`, `readObject`, `readFully`, v.v.

**Không được coi `ObjectOutput` là bộ ghi JSON.** Bạn đang tạo một trình tự giá trị byte theo hợp đồng của chính ứng dụng, nằm trong object serialization stream của Java.

### Externalizable không có nghĩa class được ghi hoàn toàn ngoài Java serialization

`ObjectOutputStream.writeObject(externalizableObject)` vẫn quản lý luồng object, class descriptor, identity handles và cơ chế giải mã liên quan. `writeExternal` chịu trách nhiệm payload trạng thái của class, không thay thế toàn bộ định dạng stream hay việc tìm class theo tên lúc đọc.

## 3. Ví dụ tối giản một class Externalizable

```java
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class Product implements Externalizable {
    private static final long serialVersionUID = 1L;

    private String code;
    private int quantity;

    // BẮT BUỘC public và không tham số cho cơ chế deserialization.
    public Product() {
    }

    public Product(String code, int quantity) {
        this.code = code;
        this.quantity = quantity;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(code);
        out.writeInt(quantity);
    }

    @Override
    public void readExternal(ObjectInput in)
            throws IOException, ClassNotFoundException {
        code = in.readUTF();
        quantity = in.readInt();
    }

    @Override
    public String toString() {
        return code + ": " + quantity;
    }
}
```

Lưu `Product.java` nếu muốn biên dịch class này riêng. Đoạn trên là **định nghĩa lớp**, chưa có `main` để chạy. Giả định `code` không null và vừa giới hạn của `writeUTF`; ví dụ nâng cao cần kiểm tra dữ liệu đầu vào.

Điều bạn phải tự bảo đảm:

1. `writeExternal` ghi `code` **trước** `quantity`.
2. `readExternal` đọc `code` bằng `readUTF` **trước** `quantity` bằng `readInt`.
3. Nếu đổi thứ tự hoặc đổi kiểu của một thành phần mà không cập nhật giao thức đọc, có thể đọc sai, ném exception, hoặc âm thầm tạo trạng thái không hợp lệ.
4. Không có cơ chế tự động lưu thêm field mới chỉ vì bạn thêm một field vào class.
5. Constructor `public Product()` được dùng khi giải tuần tự hóa; constructor hai tham số không tự chạy trong quá trình ấy.

## 4. So sánh Serializable và Externalizable

| Khía cạnh | `Serializable` mặc định | `Externalizable` |
|---|---|---|
| Interface | Marker interface, không có method bắt buộc | Mở rộng Serializable; có hai method bắt buộc |
| Ai quyết định field được lưu? | Cơ chế serialization mặc định, có thể tùy chỉnh qua hook | Chính `writeExternal` |
| Ai đọc trạng thái? | Cơ chế mặc định, có thể tùy chỉnh `readObject` | Chính `readExternal` |
| Constructor khi restore class thông thường | Không gọi constructor thông thường của class Serializable như `new` | Gọi constructor `public` không tham số của class Externalizable |
| `transient` và `static` | Không lưu theo quy tắc field mặc định | Không tự quyết định; có thể ghi giá trị tường minh |
| Thay đổi thứ tự field trong mã nguồn | Không tự buộc bạn ghi/đọc thủ công theo thứ tự đó | Giao thức đã ghi phụ thuộc thứ tự/type do tác giả lựa chọn |
| Thêm field mới | Quy tắc tương thích của serialization mặc định có thể hỗ trợ một số thay đổi | Phải tự thiết kế cách nhận biết và đọc phiên bản cũ/mới |
| Object identity/cycle qua `writeObject` | Có cơ chế handles | Vẫn có khi dùng cơ chế object stream phù hợp |
| Tính linh hoạt | Ít mã hơn cho model đơn giản | Chủ động encoding, validation và chọn data fields |
| Rủi ro | Versioning, object graph, input không tin cậy | Các rủi ro tương tự **cộng thêm lỗi giao thức do tự viết** |

**Externalizable không mặc định nhanh hơn, nhỏ hơn, an toàn hơn Serializable.** Nó có thể giảm payload nếu chủ động không ghi những field thừa, nhưng vẫn có metadata/handle của Java serialization và thêm công sức bảo trì mã hóa thủ công. Cần benchmark nếu hiệu năng quan trọng.

## 5. Tại sao cần constructor public không tham số?

Khác với class `Serializable` thông thường, Java deserialization của Externalizable thực hiện về mặt ý tưởng:

```text
Đọc metadata class từ object stream
        |
        v
Tìm/nạp class tương thích
        |
        v
Gọi constructor public không tham số
        |
        v
Instance đã được tạo
        |
        v
Gọi readExternal(ObjectInput)
        |
        v
Instance đã khôi phục trạng thái
```

Vì constructor chạy thực sự:

- Các field initializer và logic trong constructor không tham số có thể chạy.
- Sau đó `readExternal` có thể ghi đè các giá trị vừa khởi tạo.
- Nếu một field **không được đọc** trong `readExternal`, nó có thể giữ giá trị do constructor đặt (hoặc giá trị mặc định khi constructor không gán).
- Constructor có side effect (kết nối DB, tạo thread, ghi file, thay global state...) sẽ gây rủi ro khi deserialize. **Giữ constructor này đơn giản và side-effect-free** là khuyến nghị quan trọng.
- Nếu class không có constructor public không tham số có thể truy cập như API yêu cầu, quá trình đọc sẽ thất bại, thường là `InvalidClassException` hoặc lỗi liên quan.

Ví dụ **sai**:

```java
class Bad implements Externalizable {
    private static final long serialVersionUID = 1L;

    private String value;

    public Bad(String value) {
        this.value = value;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(value);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException {
        value = in.readUTF();
    }
}
```

Class trên thiếu constructor `public Bad()`. Đoạn minh họa giả định import `Externalizable`, `ObjectOutput`, `ObjectInput` và `IOException`. Ghi instance vẫn có thể tạo bytes, nhưng đọc lại qua Java serialization không có constructor cần thiết.

### Nested class cũng cần chú ý

Nếu viết class Externalizable bên trong class khác, **`public static class ... implements Externalizable`** thường rõ ràng hơn inner class không static. Inner class không static mang reference ngầm tới outer instance và cơ chế constructor có tham số outer ngầm, khiến yêu cầu constructor không tham số dễ bị vi phạm.

## 6. Mọi dữ liệu nằm trong giao thức do bạn viết

Với `Serializable` mặc định:

```java
private int age;
private String name;
private transient String token;
```

cơ chế serialization mặc định biết xử lý `age` và `name`, bỏ `token`.

Với `Externalizable`, bạn quyết định:

```java
@Override
public void writeExternal(ObjectOutput out) throws IOException {
    out.writeInt(age);
    out.writeUTF(name);
    // token không được ghi vì ta KHÔNG gọi write cho nó.
}
```

`transient` **không tự động chặn** việc bạn trực tiếp viết field trong `writeExternal`:

```java
private transient String token;

@Override
public void writeExternal(ObjectOutput out) throws IOException {
    out.writeUTF(token); // VẪN đưa token vào luồng nếu được gọi!
}
```

Vì vậy, không dùng `transient` như biện pháp an toàn tuyệt đối trước dữ liệu nhạy cảm khi class Externalizable có custom protocol. Kiểm tra mã `writeExternal` thực sự đã ghi những gì.

Tương tự, `static` không phải instance state mặc định, nhưng lập trình viên vẫn **có thể** đọc static field và ghi giá trị của nó một cách thủ công. Điều đó không có nghĩa nên làm như vậy: state dùng chung thường không phù hợp lưu dưới dạng từng object.

## 7. Primitive, String và Object: chọn cặp phương thức

| Ghi bằng ObjectOutput | Đọc bằng ObjectInput | Kiểu dữ liệu |
|---|---|---|
| `writeInt(value)` | `readInt()` | 4 byte mã hóa int theo giao thức DataOutput/DataInput |
| `writeLong(value)` | `readLong()` | long |
| `writeBoolean(value)` | `readBoolean()` | boolean |
| `writeDouble(value)` | `readDouble()` | double |
| `writeUTF(value)` | `readUTF()` | Chuỗi dùng **modified UTF-8** có tiền tố độ dài |
| `writeObject(ref)` | `readObject()` | Java object/reference graph |
| `write(byte[])` | `readFully(byte[])` | Dãy byte với kích thước **đã biết trước** |

Một số cặp API có thể **không đối xứng** nếu dùng sai. Ví dụ `writeUTF(name)` **không** tương đương `writeObject(name)`, dù cả hai truyền cùng nội dung chuỗi.

### writeUTF không phải “ghi UTF-8 tùy độ dài”

`DataOutput.writeUTF` sử dụng **modified UTF-8** của Java cùng độ dài giới hạn bằng 16-bit unsigned. Chuỗi có biểu diễn modified UTF-8 dài quá 65.535 byte có thể làm `writeUTF` ném `UTFDataFormatException`. Nếu cần chuỗi rất lớn, bạn nên tự quy định encoding UTF-8, length prefix bằng `int`/`long` có giới hạn, rồi ghi byte; lúc đọc kiểm tra độ dài trước khi cấp phát.

Ví dụ một schema text lớn (minh họa, không phải method độc lập):

```java
byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
if (utf8.length > MAX_NAME_BYTES) {
    throw new IOException("Name too long");
}
out.writeInt(utf8.length);
out.write(utf8);

// Khi đọc:
int length = in.readInt();
if (length < 0 || length > MAX_NAME_BYTES) {
    throw new InvalidObjectException("Invalid name length");
}
byte[] raw = new byte[length];
in.readFully(raw);
name = new String(raw, StandardCharsets.UTF_8);
```

Ở đây `name`, `out`, `in`, `MAX_NAME_BYTES` phải được khai báo trong class. Ví dụ dùng `new String(..., UTF_8)` có thể thay byte UTF-8 lỗi bằng ký tự thay thế theo hành vi decoder. Nếu giao thức cần **từ chối Unicode lỗi**, dùng `CharsetDecoder` với `CodingErrorAction.REPORT` và bắt lỗi.

### Null handling

`writeUTF(null)` không phải cách ghi một chuỗi null hợp lệ. Chọn schema như:

```java
out.writeBoolean(name != null);
if (name != null) {
    out.writeUTF(name);
}

// Đọc theo đúng thứ tự:
boolean exists = in.readBoolean();
name = exists ? in.readUTF() : null;
```

Hoặc sử dụng `writeObject`/`readObject` cho một reference kiểu String, nơi null được object stream xử lý tự nhiên. Chọn theo yêu cầu kích thước, biểu diễn và kiểm soát kiểu.

## 8. Thứ tự là một phần hợp đồng dữ liệu

Giả sử writer:

```java
out.writeInt(version);
out.writeUTF(code);
out.writeLong(timestamp);
out.writeBoolean(active);
```

Reader **phải đọc cùng thứ tự và cách biểu diễn**:

```java
int version = in.readInt();
String code = in.readUTF();
long timestamp = in.readLong();
boolean active = in.readBoolean();
```

Nếu reader lại gọi `readLong()` ở vị trí đang lưu String, stream sẽ bị diễn giải sai; kết quả có thể là exception hoặc một giá trị rác trước khi gặp lỗi ở phần sau.

Một file Java object serialization có thể được đọc bởi class version khác với class đã ghi trước đó. Chính tác giả `Externalizable` phải xây giao thức cho compatibility. Không được tự dựa vào thứ tự khai báo field hay việc thêm field ở cuối class như với một số trường hợp serialization mặc định.

## 9. Chương trình Java 17+ hoàn chỉnh: chu trình Externalizable + object graph

Ví dụ tiếp theo chứng minh đồng thời:

1. `writeExternal`/`readExternal` tự viết/đọc version, tên và reference.
2. Public constructor không tham số được gọi khi giải tuần tự hóa.
3. `transient cache` **không được ghi**, nhưng giữ giá trị do constructor đọc tạo.
4. Hai node tạo vòng `A → B → A`; vòng được bảo toàn.
5. Ghi cùng một root **hai lần** trên cùng ObjectOutputStream rồi đọc hai lần vẫn có thể trả cùng reference nhờ handle của stream.
6. Các object restored là instance khác với object ban đầu ở JVM.
7. Dữ liệu tự tạo trong bộ nhớ, **không** nhận từ nguồn không tin cậy.

Lưu đúng tên **`ExternalizableGraphDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serial;

public class ExternalizableGraphDemo {

    public static class Node implements Externalizable {
        @Serial
        private static final long serialVersionUID = 1L;

        private static int publicNoArgCalls = 0;

        private String name;
        private Node next;
        private transient String cache;

        // Bắt buộc public và không tham số.
        public Node() {
            publicNoArgCalls++;
            cache = "from-no-arg";
        }

        public Node(String name) {
            this.name = name;
            this.cache = "live-only";
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeInt(1);        // Version của custom protocol.
            out.writeUTF(name);
            out.writeObject(next);  // Giữ cơ chế object identity/handles.
        }

        @Override
        public void readExternal(ObjectInput in)
                throws IOException, ClassNotFoundException {
            int formatVersion = in.readInt();
            if (formatVersion != 1) {
                throw new InvalidObjectException(
                        "Unsupported format version: " + formatVersion);
            }

            String restoredName = in.readUTF();
            Object restoredNext = in.readObject();

            if (restoredName.isBlank()) {
                throw new InvalidObjectException("Empty name");
            }
            if (restoredNext != null && !(restoredNext instanceof Node)) {
                throw new InvalidObjectException("Invalid next node");
            }

            name = restoredName;
            next = (Node) restoredNext;

            // cache vẫn là "from-no-arg": không được ghi/đọc ở đây.
        }
    }

    public static void main(String[] args) throws Exception {
        Node a = new Node("A");
        Node b = new Node("B");
        a.next = b;
        b.next = a;

        byte[] bytes;
        try (ByteArrayOutputStream memory = new ByteArrayOutputStream();
             ObjectOutputStream output = new ObjectOutputStream(memory)) {
            output.writeObject(a);
            output.writeObject(a); // Cùng instance root.
            output.flush();
            bytes = memory.toByteArray();
        }

        Node restoredFirst;
        Node restoredSecond;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes))) {
            restoredFirst = (Node) input.readObject();
            restoredSecond = (Node) input.readObject();
        }

        System.out.println("Name: " + restoredFirst.name);
        System.out.println("Cycle restored: "
                + (restoredFirst.next.next == restoredFirst));
        System.out.println("Shared root: "
                + (restoredFirst == restoredSecond));
        System.out.println("No-arg constructor calls: "
                + Node.publicNoArgCalls);
        System.out.println("Transient cache: " + restoredFirst.cache);
        System.out.println("Restored differs: " + (restoredFirst != a));
    }
}
```

Biên dịch và chạy tại thư mục chứa mã:

```bash
javac ExternalizableGraphDemo.java
java ExternalizableGraphDemo
```

**Stdout dự kiến chính xác trong một JVM chạy chương trình độc lập:**

```text
Name: A
Cycle restored: true
Shared root: true
No-arg constructor calls: 2
Transient cache: from-no-arg
Restored differs: true
```

Phân tích chi tiết:

- `new Node("A")` và `new Node("B")` gọi constructor có tham số, **không** tăng biến đếm `publicNoArgCalls`.
- Serialization root A gọi `writeExternal(A)` → ghi version 1, tên A và reference tới B; với B, `writeExternal(B)` ghi version 1, tên B và reference ngược tới A.
- `ObjectOutputStream` có cơ chế handle cho object A, nên reference B→A không khiến code lặp vô hạn.
- Khi đọc lại, A và B là hai instance Externalizable mới; constructor `public Node()` được gọi cho từng instance, nên biến đếm trở thành 2.
- `readExternal` đặt `name` và `next` theo stream; nó **không ghi đè** `cache`, nên giá trị khởi tạo từ constructor được giữ nguyên.
- `restoredFirst.next.next == restoredFirst` xác nhận chu trình đã khôi phục.
- Lần `output.writeObject(a)` thứ hai trên cùng stream sử dụng lại handle của A; hai lần `readObject` trả cùng root restored, nên `restoredFirst == restoredSecond`.
- `restoredFirst != a` cho thấy object restored không cùng identity với object gốc.

**Đừng dùng static counter như một phép đo chung cho nhiều tiến trình hoặc nhiều lượt chạy song song trong production.** Ở ví dụ độc lập, nó minh họa constructor; trong ứng dụng thật, constructor có side effect hoặc state toàn cục dễ gây lỗi concurrency/reentrancy.

## 10. Cơ chế bảo toàn tham chiếu và vòng

Khi `Externalizable.writeExternal` gọi `out.writeObject(reference)`, ObjectOutputStream tiếp tục sử dụng cơ chế lưu object handles. Điều này đặc biệt quan trọng cho:

```text
A.next = B
B.next = A

writeObject(A)
  ├── writeExternal(A)
  │      ├── writeInt(1)
  │      ├── writeUTF("A")
  │      └── writeObject(B)
  │             └── writeExternal(B)
  │                    └── writeObject(A) -> reference/handle
  └── hoàn tất root A
```

Khi đọc, Java tạo instance và quản lý reference đủ để tái tạo đồ thị có chu trình theo quy tắc của stream. Tuy nhiên, **logic `readExternal` phải tránh thao tác tùy tiện lên những object mà trạng thái chưa được đọc xong**. Trong chu trình, một tham chiếu có thể trỏ tới object đang được xây dựng dở. Ví dụ nếu `B.readExternal` cố đọc `A.name` trước khi `A.readExternal` hoàn tất, tên của A có thể vẫn chưa được gán.

Do đó:

- Ghi reference bằng `writeObject` và đọc bằng `readObject` để tận dụng handle tracking.
- Trong `readExternal`, ưu tiên lưu các reference và xác thực kiểu hợp lý, tránh gọi nghiệp vụ phức tạp trên graph chưa hoàn tất.
- Không giả định tất cả object được tham chiếu đã hoàn tất validation ở thời điểm một reference được gán.
- Nếu cần kiểm tra invariant xuyên nhiều node, có thể tổ chức bước validation sau khi toàn bộ graph root đã được đọc xong.
- Nếu thay `writeObject` bằng logic đệ quy thủ công tự gọi `next.writeExternal(out)`, bạn có thể bỏ qua handle tracking và **tạo đệ quy vô hạn** cho đồ thị có vòng.

### Object identity khác object equality

Khi kiểm tra bảo toàn tham chiếu:

- `a == b`: cùng instance trong JVM không?
- `a.equals(b)`: giá trị có tương đương theo logic của class không?

Hai instance có cùng `name` vẫn có thể không phải một object. Hãy dùng `==` để test alias/cycle, không chỉ dùng `equals`.

## 11. Externalizable không tự động ghi mọi field

Một class có:

```java
private String id;
private int count;
private List<String> tags;
private transient String sessionToken;
```

Nếu `writeExternal` chỉ gọi:

```java
out.writeUTF(id);
out.writeInt(count);
```

thì:

- `id` và `count` được ghi theo schema tùy chỉnh.
- `tags` không được ghi; sau deserialize, giá trị của nó phụ thuộc constructor `public` và logic `readExternal`.
- `sessionToken` không được ghi **vì tác giả không gọi write cho field này**. Nếu tác giả ghi nó thủ công dù transient, dữ liệu vẫn xuất hiện trong stream.
- Việc thêm `tags` vào class Java không khiến stream tự có thêm tags.

Trong trường hợp cần persist tags, bạn có thể chủ động ghi số lượng có giới hạn và từng phần tử, hoặc `out.writeObject(tags)` (chỉ khi kiểu và đồ thị an toàn, tin cậy, cùng phiên bản). Phương án ghi tự định dạng thường kiểm soát migration và kích thước tốt hơn; phương án `writeObject` dễ dùng nhưng kéo thêm class/graph Java serialization.

## 12. Versioning tường minh: tự quản lý giao thức

Khi dùng `Externalizable`, **`serialVersionUID` và phiên bản payload riêng là hai tầng khác nhau**:

- `serialVersionUID` dùng trong Java serialization để kiểm tra một phần tính tương thích class khi đọc object descriptor.
- `formatVersion` do bạn ghi bằng `writeInt` là một thành phần trong custom protocol của `writeExternal`.

Ví dụ V1:

```java
out.writeInt(1);
out.writeUTF(code);
out.writeInt(quantity);
```

V2 muốn thêm `price`:

```java
out.writeInt(2);
out.writeUTF(code);
out.writeInt(quantity);
out.writeLong(priceInCents);
```

Reader V2 có thể đọc cả hai:

```java
int version = in.readInt();
if (version != 1 && version != 2) {
    throw new InvalidObjectException("Unsupported version: " + version);
}

code = in.readUTF();
quantity = in.readInt();
priceInCents = version >= 2 ? in.readLong() : 0L;
```

Đây là **các mảnh mã minh họa của cùng custom format**, không đặt hai phiên bản writer vào một class cùng lúc. Tất cả biến cần được khai báo; reader nên validate code/quantity/price ngay sau khi đọc. Cẩn thận rằng nếu dữ liệu đã được ghi bởi V2 rồi một reader V1 đọc nó, reader V1 **có thể không biết xử lý v2** và cần từ chối thay vì đọc lầm byte.

### Gắn serialVersionUID thế nào?

Với Externalizable vẫn nên khai báo:

```java
@Serial
private static final long serialVersionUID = 1L;
```

`@Serial` dùng trong Java 17. UID tương ứng phải là `static final long` đúng tên. Khi đổi schema payload mà vẫn muốn một class nhận các phiên bản cũ theo logic `readExternal`, có thể giữ UID tương thích và dùng `formatVersion` để phân nhánh. Nếu cố tình thay đổi class không tương thích, UID mới có thể khiến đọc stream cũ bị từ chối với `InvalidClassException`.

**Giữ UID cũ không tự bảo đảm schema V1/V2 tương thích.** Reader cần thực sự phân tích đúng mọi phiên bản được hứa hỗ trợ, kiểm thử file thực tế và xử lý giá trị mặc định/validation.

### Kiểm tra lỗi version phải sớm

Đọc `version` trước rồi kiểm tra miền hỗ trợ trước khi xử lý phần còn lại giúp tránh đọc sai kiểu trong một schema hoàn toàn khác. Không nên bỏ qua version không biết rồi đoán các byte còn lại theo format hiện tại.

## 13. Thứ tự dữ liệu và lỗi khó phát hiện

Sai thứ tự có thể tạo lỗi khó chẩn đoán. Giả sử writer `writeInt(5)` rồi `writeLong(7L)`, còn reader lại `readLong()` rồi `readInt()`:

- Không nhất thiết exception xảy ra ngay tại byte đầu.
- Các giá trị có thể bị ghép từ bytes của hai field.
- Vị trí đọc tiếp theo không còn ở ranh giới mong muốn.
- Lỗi thật có thể xuất hiện rất muộn khi type token, length hoặc trailer bị diễn giải sai.

**Viết unit test round-trip là cần nhưng chưa đủ.** Nếu writer và reader cùng mắc lỗi tương ứng, một test round-trip cùng phiên bản có thể vẫn pass. Cần kiểm tra cross-version bằng **golden files** được lưu từ các phiên bản cũ và test các input không hợp lệ.

Checklist kiểm thử:

1. Object tối thiểu và object đầy đủ.
2. Null, chuỗi rỗng, Unicode, chuỗi rất dài.
3. Số âm, số lớn, overflow, boundary values.
4. Reference graph có cùng object xuất hiện nhiều lần.
5. Graph có chu trình.
6. Stream bị cắt giữa primitive hoặc giữa object.
7. `formatVersion` không hỗ trợ.
8. Class có/không có public no-arg constructor.
9. Lỗi khi ghi nhiều object liên tiếp trong cùng stream.
10. Các phiên bản cũ/mới và độ ổn định của UID.

## 14. writeExternal/readExternal có thể ném exception gì?

| Tình huống | Exception/nhóm lỗi |
|---|---|
| Stream hỏng, file bị cắt hoặc lỗi transport | `IOException`, `EOFException`, `StreamCorruptedException` tùy ngữ cảnh |
| Payload format tự đặt không hợp lệ | Có thể chủ động ném `InvalidObjectException` |
| `readObject()` gặp class không có trong classpath | `ClassNotFoundException` |
| Class Externalizable không đáp ứng yêu cầu constructor | Thường `InvalidClassException` hoặc lỗi tương ứng |
| `writeObject(ref)` gặp object không Serializable | `NotSerializableException` |
| Class descriptor/UID không tương thích | `InvalidClassException` |
| Dữ liệu modified UTF-8 không hợp lệ/quá dài | `UTFDataFormatException` |
| Ép kiểu object sai sau `readObject` | `ClassCastException` (unchecked) |
| Java input filter từ chối class/graph | Có thể dẫn đến `InvalidClassException` |

Khi `readExternal` ném lỗi:

- Không dùng object mới dựng dở như object nghiệp vụ hợp lệ.
- Không cố tiếp tục đọc bằng một vị trí tự đoán nếu không có giao thức phục hồi rõ ràng.
- Ghi log hoặc trả lỗi có ngữ cảnh file/request/format version, nhưng không in dữ liệu nhạy cảm.
- Nếu đang đọc từ nguồn không tin cậy, ưu tiên **không dùng native deserialization**; exception handling không loại bỏ rủi ro tạo object trước khi lỗi xuất hiện.

## 15. Custom format và giới hạn tài nguyên

Tính chủ động của Externalizable cho phép bạn kiểm tra dữ liệu trước khi cấp phát:

```java
int itemCount = in.readInt();
if (itemCount < 0 || itemCount > 10_000) {
    throw new InvalidObjectException("Invalid item count");
}
List<String> items = new ArrayList<>(itemCount);
for (int i = 0; i < itemCount; i++) {
    items.add(in.readUTF());
}
```

Đây là đoạn minh họa, cần imports `ArrayList`, `List`, `InvalidObjectException`, `ObjectInput` và khai báo `in`. Việc giới hạn count trước khi tạo `ArrayList` là tốt, nhưng:

- Mỗi `String` vẫn có thể lớn; cần chính sách tổng số byte/string.
- Nếu giao thức cho phép nested objects, cần kiểm soát depth/reference count.
- Nhiều requests đọc đồng thời có thể cộng dồn bộ nhớ lớn; giới hạn concurrency.
- I/O từ stream mạng có thể chậm, cần timeout/backpressure và đóng tài nguyên khi vượt quota.
- Input bị cắt phải báo lỗi rõ, không mặc định khôi phục danh sách còn thiếu.
- `readUTF` có giới hạn riêng; giao thức tự ghi raw UTF-8 cần giới hạn rõ ràng hơn.

## 16. Khác gì với hook readObject/writeObject của Serializable?

Có hai mô hình thường bị nhầm:

### A. Serializable với custom hooks

```java
private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    // Ghi phần custom nếu có.
}

private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    // Kiểm tra/khôi phục phần custom nếu có.
}
```

Các phương thức `private` đặc biệt này thuộc class `Serializable` thông thường, được nhận diện theo cơ chế serialization; bạn có thể giữ phần field mặc định thông qua `defaultWriteObject`/`defaultReadObject`.

### B. Externalizable với public contract

```java
@Override
public void writeExternal(ObjectOutput out) throws IOException {
    out.writeInt(1);
    out.writeUTF(name);
}

@Override
public void readExternal(ObjectInput in)
        throws IOException, ClassNotFoundException {
    int version = in.readInt();
    if (version != 1) {
        throw new InvalidObjectException("Bad version");
    }
    name = in.readUTF();
}
```

`writeExternal`/`readExternal` phải là **public** vì chúng implement methods của interface. Bạn quyết định payload được ghi; **`defaultWriteObject` không phải bước bắt buộc của Externalizable**, và `ObjectOutput` interface không cung cấp `defaultWriteObject()` như `ObjectOutputStream`.

**Không đặt phương thức `private readExternal` và nghĩ runtime sẽ gọi giống `readObject`.** Đó không phải implementation hợp lệ của interface.

### Khi nào chọn mô hình nào?

- Model đơn giản với nhiều field, cần ít code: `Serializable` mặc định có thể đủ trong môi trường nội bộ tin cậy.
- Cần thêm vài validations/hook mà vẫn muốn phần lớn field mặc định: custom `readObject/writeObject` có thể hợp lý.
- Muốn kiểm soát chặt thứ tự, version và encoding của payload bên trong Java object stream: `Externalizable`.
- Cần định dạng đa ngôn ngữ, version lâu dài hoặc nhận input từ client không tin cậy: **không nên mặc định chọn cả hai**; ưu tiên DTO/schema và serializer phù hợp.

## 17. Security: Externalizable không an toàn trước byte độc hại

`Externalizable` có thể khiến người viết tưởng “tự đọc field nên sẽ an toàn hơn”. Nhưng nó **vẫn được kích hoạt qua `ObjectInputStream.readObject()`**, với class descriptors, việc tạo instance và các hook liên quan. Điều đó vẫn mang rủi ro native Java deserialization.

Tình huống nguy hiểm:

```java
// KHÔNG LÀM với bytes do client không tin cậy gửi.
try (ObjectInputStream in = new ObjectInputStream(networkInput)) {
    Object value = in.readObject();
}
```

Lý do:

- Việc giải mã có thể tạo các class trên classpath với hành vi không mong muốn.
- Public no-arg constructor của Externalizable có thể chạy và gây side effect trước khi payload được validate.
- `readExternal` có thể gọi `in.readObject()` và kéo theo toàn bộ graph phức tạp.
- Dữ liệu có thể bị cố ý thiết kế để tiêu thụ CPU, bộ nhớ hoặc gây stack/resource exhaustion.
- Kiểm tra `instanceof` sau `readObject` có thể đã quá muộn để chặn hành vi trong giai đoạn deserialize.

Nguyên tắc:

1. Tránh Java native deserialization ở **trust boundary**: HTTP payload công khai, dữ liệu do bên ngoài gửi, file từ người không đáng tin.
2. Ưu tiên format dữ liệu đơn giản, DTO/schema, parser được kiểm soát, giới hạn byte/độ sâu/đối tượng.
3. Nếu có legacy dữ liệu Java serialization từ nguồn tin cậy, giới hạn class/array/depth/references qua `ObjectInputFilter` khi phù hợp.
4. Kiểm tra bất biến dữ liệu và kiểm soát version trong `readExternal`, không dựa vào riêng constructor.
5. Không để constructor hoặc `readExternal` thực hiện side effect không thể đảo ngược khi payload chưa được kiểm chứng.
6. Bảo vệ nguồn file và quyền truy cập, có checksum/chữ ký mật mã nếu cần xác thực tính toàn vẹn với bên ngoài; CRC/hash thường không tự chứng minh nguồn đáng tin.

### Ví dụ ObjectInputFilter (chỉ để nhận biết API)

```java
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
        "maxdepth=10;maxrefs=100;maxarray=10000;maxbytes=1048576;"
      + "com.example.model.*;!*");

try (ObjectInputStream in = new ObjectInputStream(source)) {
    in.setObjectInputFilter(filter);
    Object object = in.readObject();
}
```

Đây là đoạn minh họa, cần `ObjectInputFilter`, `ObjectInputStream`, một `InputStream source` và xử lý exceptions. Package `com.example.model` chỉ là ví dụ; với một đồ thị chứa `ArrayList`/`String`/các class JDK khác, allowlist thực tế cần xác định từ model và được kiểm thử theo semantics filter.

Filter giúp **giảm** rủi ro trong một số kịch bản, nhưng **không chứng minh mọi input không tin cậy là an toàn**. Nó cũng không thay thế giới hạn nguồn I/O, quota theo request, timeout hay các cơ chế bảo mật tầng ứng dụng.

## 18. Tác động của inheritance và state của superclass

Một class Externalizable có thể kế thừa field và behavior từ superclass. Nhưng **`writeExternal` phải quyết định cách lưu các field cần persist**, kể cả state được sở hữu bởi superclass mà class có thể truy cập qua API hợp lệ.

Ví dụ:

```java
class Base {
    protected int id;

    Base() {
        id = -1;
    }
}

public class Child extends Base implements Externalizable {
    private static final long serialVersionUID = 1L;

    private String name;

    public Child() {
        super();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(id);
        out.writeUTF(name);
    }

    @Override
    public void readExternal(ObjectInput in)
            throws IOException, ClassNotFoundException {
        id = in.readInt();
        name = in.readUTF();
    }
}
```

Đây là ví dụ class minh họa; đặt `Child` vào file `Child.java` cùng `Base` phù hợp và import interfaces/exceptions. `Base` không Serializable nhưng constructor `Base()` chạy thông qua `Child()` khi object Externalizable được dựng. Sau đó `readExternal` khôi phục `id` từ stream. Nếu `Child.writeExternal` **không** ghi `id`, phần state đó chỉ còn giá trị constructor đã khởi tạo hoặc logic khác của ứng dụng.

Điều này khác với việc giả định superclass state được tự động lưu đầy đủ theo serialization mặc định. Với các superclass có invariant phức tạp, nên có API rõ ràng để lấy/đặt trạng thái được phép persist.

## 19. Ghi và đọc nhiều Externalizable object trong một stream

`ObjectOutputStream` quản lý bảng handle **trên phạm vi stream**, không chỉ một lần gọi:

```java
try (ObjectOutputStream out = new ObjectOutputStream(output)) {
    out.writeObject(a);
    out.writeObject(b);
    out.writeObject(a);
}
```

Khi đọc theo đúng thứ tự:

```java
try (ObjectInputStream in = new ObjectInputStream(input)) {
    Object first = in.readObject();
    Object second = in.readObject();
    Object third = in.readObject();
    System.out.println(first == third);
}
```

Đây là hai đoạn minh họa độc lập, với `output` và `input` là cặp stream tương ứng; `a`/`b` cần được khai báo. Nếu cùng instance A được ghi lại mà không reset cơ chế handle, object thứ ba có thể là reference tới object thứ nhất, nên phép so sánh identity trả true.

### Bẫy thay đổi object rồi ghi lại

Giả sử:

```java
out.writeObject(person);
person.setName("New name");
out.writeObject(person);
```

Trong cùng stream, lần ghi thứ hai có thể **chỉ ghi reference** và không gọi lại `writeExternal` để lưu bản cập nhật. Đừng xây append log snapshot bằng cách lặp `writeObject(sameInstance)` rồi hy vọng mỗi lần đều ghi full state.

Nếu muốn một giai đoạn độc lập về handle trong cùng ObjectOutputStream, `out.reset()` có thể được dùng có chủ đích; tương tác với reader và versioned format phải được thiết kế rõ. `writeUnshared`/`readUnshared` là API khác có semantics đặc thù, không tự động deep-clone mọi reference bên trong graph.

## 20. Độ bền file và atomicity không nằm trong Externalizable

Serialization thành công không đồng nghĩa file đã được lưu bền hoặc “cập nhật snapshot” nguyên tử.

Nếu ghi trực tiếp:

```java
try (ObjectOutputStream out =
         new ObjectOutputStream(Files.newOutputStream(target))) {
    out.writeObject(root);
}
```

một lỗi giữa chừng có thể làm file đích cũ bị cắt hoặc chứa object stream dang dở. Đối với dữ liệu quan trọng:

1. Tạo file tạm độc lập, tốt nhất trong cùng thư mục với đích.
2. Ghi toàn bộ graph và đóng ObjectOutputStream thành công.
3. Nếu bài toán cần độ bền cao, đồng bộ dữ liệu/metadata theo cơ chế filesystem phù hợp và hiểu giới hạn.
4. Validate định dạng/snapshot cần thiết trước khi công bố.
5. Thay thế đích bằng move/rename theo hỗ trợ atomic của provider; nếu atomic là yêu cầu bắt buộc, **không tự động fallback** sang cách không atomic rồi giữ nguyên lời hứa.
6. Có backup/recovery và xử lý file tạm còn sót khi tiến trình sập.

`close()` của ObjectOutputStream không phải lệnh fsync toàn hệ thống; `Files.move(..., ATOMIC_MOVE)` chỉ giải quyết một phần tính nguyên tử cập nhật tên nếu được hỗ trợ và còn các giới hạn về durability/multi-writer.

## 21. Kiểm thử và cách chọn Externalizable

Chọn `Externalizable` khi **đã có lý do cụ thể**:

- Cần điều khiển chính xác dữ liệu trạng thái được lưu vào native Java object stream.
- Muốn tránh lưu những phần graph lớn không cần thiết.
- Muốn một custom payload có version, length và validation do chính class chịu trách nhiệm.
- Cần xử lý compatibility với dữ liệu do một ứng dụng Java nội bộ đáng tin đã lưu.

Không nên chọn chỉ vì:

- Muốn truyền payload mạng công khai.
- Mong tương thích đa ngôn ngữ.
- Nghĩ rằng writeExternal luôn nén tốt hơn, an toàn hơn hoặc nhanh hơn.
- Muốn “mã hóa” dữ liệu (serialization không phải encryption).
- Muốn database có transaction, query, migration hoặc indexing.

### Checklist code review

- [ ] Class thực sự triển khai `Externalizable`, có **public no-arg constructor**.
- [ ] `writeExternal` và `readExternal` đều `public` với chữ ký đúng.
- [ ] Ghi và đọc **đúng thứ tự cùng kiểu dữ liệu**.
- [ ] Có `formatVersion` tường minh khi yêu cầu tương thích lâu dài.
- [ ] Có `serialVersionUID` cố định có chủ đích.
- [ ] Xử lý `null`, empty, UTF, độ dài và giá trị ngoài miền.
- [ ] Không vô ý ghi field nhạy cảm dù nó được đánh dấu transient.
- [ ] Có validation/invariants sau đọc, và không side-effect nguy hiểm từ constructor.
- [ ] Có test graph alias/cycle và trường hợp reference đến class không Serializable.
- [ ] Xử lý `IOException`, `ClassNotFoundException`, `InvalidClassException` và stream lỗi.
- [ ] Không deserialize dữ liệu không tin cậy; giới hạn nguồn/graph khi dùng legacy dữ liệu tin cậy.
- [ ] Nếu persist vào file, có chiến lược ghi, commit và recovery tách biệt.

## 22. Các lỗi thường gặp

1. **Thiếu constructor public không tham số:** deserialization Externalizable thất bại.
2. **Định nghĩa readExternal/writeExternal private:** không implement đúng interface.
3. **Nhầm readExternal với private readObject hook:** hai giao thức/điểm mở rộng khác nhau.
4. **Không ghi đủ field cần phục hồi:** giá trị sau đọc có thể là mặc định/constructor state.
5. **Cho rằng transient tự ngăn writer custom ghi token:** tự gọi `writeUTF(token)` vẫn lưu token.
6. **Đọc sai thứ tự primitive/object:** dữ liệu sai hoặc stream không thể decode.
7. **Dùng writeUTF cho chuỗi quá dài:** có giới hạn modified UTF-8 độ dài 65.535 byte.
8. **Quên ghi rõ null:** `writeUTF(null)` không phải encoding của null.
9. **Không kiểm tra phiên bản custom payload:** reader mới/cũ đọc sai schema.
10. **Tưởng UID khớp là đủ tương thích:** reader vẫn phải hiểu payload bytes.
11. **Tự đệ quy writeExternal trên reference graph có vòng:** bỏ qua object handles của stream và có thể vô hạn.
12. **Không quản lý graph đang xây dựng:** readExternal có thể thấy reference tới object chưa hoàn tất.
13. **Nghĩ đọc object đã được validation đầy đủ:** cần kiểm tra invariant/snapshot sau khi restore.
14. **Viết cùng một instance nhiều lần và mong mỗi lần là snapshot mới:** bảng handles có thể ghi tham chiếu.
15. **Để constructor thực hiện I/O/side effects khi deserialize:** nguy hiểm về bảo mật và lifecycle.
16. **Nhận byte native serialization từ client không tin cậy:** rủi ro security và cạn tài nguyên.
17. **Ghi đè file snapshot duy nhất trực tiếp:** crash có thể làm mất dữ liệu cũ.
18. **Không kiểm thử cross-version/golden files:** đổi code reader/writer cùng lúc có thể che lỗi.
19. **Cho rằng performance tốt vì ít field:** cần đo với object graph thực tế, không suy đoán.
20. **Coi Externalizable là format trao đổi tiêu chuẩn ngoài Java:** vẫn lệ thuộc Java object serialization.

## 23. Bài tập thực hành

**Bài 1 — Tự viết protocol cho một Record.** Tạo class `Report implements Externalizable` có `id`, `createdAtEpochMillis`, `status`. Ghi version 1, String id và long timestamp, một mã byte đại diện status. Đọc lại và xác thực miền của status; thử stream sai version.

**Bài 2 — Thêm trường ở version 2.** Thêm `source` tùy chọn. Định nghĩa V1 không có source, V2 ghi thêm source với boolean presence flag. Reader V2 phải đọc được bytes V1 và V2, không đọc thừa V1.

**Bài 3 — Kiểm tra constructor.** Gán một field transient là `"initialized"` trong public no-arg constructor; không ghi nó trong `writeExternal`. Sau deserialize, kiểm tra giá trị giữ nguyên. Bỏ constructor và dự đoán ngoại lệ.

**Bài 4 — Object graph chia sẻ.** Một `Group` có hai field cùng trỏ một `Node`. Ghi cả hai qua `out.writeObject`, đọc lại và kiểm tra `first == second`. Sau đó tạo vòng hai Node để kiểm tra cycle.

**Bài 5 — Lỗi thứ tự.** Ghi `int` rồi `long`, nhưng cố đọc theo thứ tự ngược. Quan sát lỗi hoặc giá trị bất hợp lý; sửa protocol và viết test golden bytes thay vì chỉ test writer/reader cùng source.

**Bài 6 — Input không hợp lệ.** Ghi `count=-1` hoặc `count` rất lớn vào custom format. Reader phải phát hiện trước khi cấp phát list. Tiếp tục kiểm tra chuỗi dài, version không hỗ trợ và truncated stream.

**Bài 7 — Đánh giá thay thế.** Thiết kế DTO có 5 trường để lưu cấu hình. So sánh native Java Externalizable với JSON hoặc Protocol Buffers về versioning, khả năng review diff, rủi ro bảo mật và tương thích ngôn ngữ.

## 24. Câu hỏi tự kiểm tra

**Câu 1.** `Externalizable` khác `Serializable` marker ở điểm nào?  
**Đáp án:** `Externalizable` khai báo `writeExternal` và `readExternal` để class chủ động ghi/đọc payload; nó vẫn extends `Serializable`.

**Câu 2.** Vì sao class Externalizable cần constructor public không tham số?  
**Đáp án:** Deserialization sử dụng constructor đó để tạo instance trước khi gọi `readExternal`.

**Câu 3.** `writeExternal` không ghi field `age`; sau đọc nó có tự được phục hồi bằng cơ chế field mặc định không?  
**Đáp án:** Không. Giá trị tùy constructor/initializer và logic `readExternal`, vì Externalizable không tự ghi field mặc định.

**Câu 4.** Field `transient password` có thể bị ghi ra stream không?  
**Đáp án:** Có, nếu `writeExternal` chủ động ghi nó. Transient không phải cơ chế chặn writer custom.

**Câu 5.** Viết `out.writeInt(12)` thì phải đọc bằng `readLong` để giữ đủ byte không?  
**Đáp án:** Không. Phải đọc đúng `readInt` và đúng thứ tự schema.

**Câu 6.** Có thể bảo toàn A→B→A bằng Externalizable không?  
**Đáp án:** Có, nếu dùng `out.writeObject`/`in.readObject` cho reference và hợp đồng serialization phù hợp; cơ chế handle quản lý cycle.

**Câu 7.** Nếu `readExternal` trong B đọc reference tới A đang được phục hồi, mọi field của A đã hoàn tất chưa?  
**Đáp án:** Không nhất thiết. Không dựa vào trạng thái hoàn chỉnh của object trong lúc giải đồ thị vòng.

**Câu 8.** `serialVersionUID=1L` và `formatVersion=2` có mâu thuẫn không?  
**Đáp án:** Không. UID thuộc lớp tương thích Java object stream; formatVersion là phiên bản payload tự định nghĩa.

**Câu 9.** Vì sao không dùng Externalizable để deserialize request từ internet?  
**Đáp án:** Nó vẫn dựa trên Java native deserialization, có thể dựng class và chạy constructor/hook nguy hiểm từ byte không tin cậy.

**Câu 10.** `ObjectOutputStream.writeObject(a)` hai lần luôn ghi snapshot state mới ở lần sau không?  
**Đáp án:** Không. Stream có handle table; lần sau có thể chỉ ghi một reference.

**Câu 11.** `ObjectOutputStream.close()` có bảo đảm snapshot file được flush bền qua mất điện không?  
**Đáp án:** Không. Cần chiến lược durability, atomic replacement và recovery theo filesystem.

**Câu 12.** Khi nào Externalizable hợp lý hơn định dạng tự mô tả công khai?  
**Đáp án:** Khi ứng dụng Java nội bộ tin cậy có lý do cần kiểm soát payload trong Java object serialization; không phải mặc định cho API công khai/đa ngôn ngữ.

## 25. Ghi nhớ

- **`Externalizable` mở rộng `Serializable` và yêu cầu public `writeExternal(ObjectOutput)` + `readExternal(ObjectInput)`.**
- **Constructor `public` không tham số là bắt buộc** để khôi phục instance thông thường.
- **Mọi field cần persist phải được ghi/đọc do class quyết định**; `transient`/`static` không ngăn writer custom ghi dữ liệu.
- **Thứ tự, kiểu và version của payload là hợp đồng của chính ứng dụng.** `serialVersionUID` không thay thế custom format version.
- **Đồ thị đối tượng, alias và cycle vẫn có thể được bảo toàn** qua `writeObject`/`readObject` và handles của ObjectOutputStream.
- **Không dùng native Java deserialization cho byte không tin cậy.** Validation, ObjectInputFilter và giới hạn tài nguyên là bổ sung, không thay thế thiết kế trust boundary.
- Externalizable **không phải database, transaction, format đa ngôn ngữ hoặc mã hóa bảo mật**; lựa chọn API phải dựa trên nhu cầu thực tế.

### Tài liệu API Java SE 17

- [Externalizable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Externalizable.html)
- [ObjectOutput](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectOutput.html)
- [ObjectInput](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInput.html)
- [ObjectOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectOutputStream.html)
- [ObjectInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html)
- [ObjectInputFilter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html)
- [Java Serialization Specification](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/index.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-serialization-006">Yêu cầu constructor public không tham số của Externalizable</a>

<details>
<summary>Xem chi tiết</summary>

# Yêu cầu constructor public không tham số của Externalizable trong Java

## 1. Tại sao Externalizable cần constructor public không tham số?

Trong **Java Object Serialization**, interface `java.io.Externalizable` cho phép một đối tượng tự quyết định **những byte nào được viết** và **thứ tự đọc lại các byte đó**. Nó mở rộng `Serializable` và khai báo hai phương thức:

- `public void writeExternal(ObjectOutput out) throws IOException` — ghi trạng thái đối tượng.
- `public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException` — đọc dữ liệu và khôi phục trạng thái.

Đặc điểm khác thường: khi `ObjectInputStream.readObject()` giải tuần tự hóa đối tượng `Externalizable`, nó **tạo một instance bằng cách gọi constructor public không tham số của lớp đó**, sau đó mới gọi `readExternal()`.

~~~text
GHI (serialization)
  new Person("An", 20)
         |
  ObjectOutputStream.writeObject(person)
         |
     writeExternal(out)
         |
   object stream chứa class metadata + dữ liệu do ta ghi

ĐỌC (deserialization)
  ObjectInputStream.readObject()
         |
  tìm public Person()  <--- BẮT BUỘC có
         |
  chạy Person() + chuỗi constructor superclass
         |
     readExternal(in)
         |
  Person được trả về cho ứng dụng
~~~

**Điều kiện bắt buộc:** lớp được giải tuần tự hóa theo cơ chế `Externalizable` phải có **constructor không tham số (`()`), có mức truy cập `public`**. Chỉ có constructor `private`, `protected` hoặc package-private (không ghi modifier) là **không đủ**. Chỉ có constructor nhận tham số cũng không đủ.

Thiếu điều kiện này có thể **vẫn biên dịch và ghi object thành công**, nhưng khi đọc lại sẽ thất bại, thường bằng `java.io.InvalidClassException` với thông điệp như `no valid constructor`. Đừng lầm tưởng đây là quy tắc chung của mọi `Serializable`; đây là điểm riêng rất quan trọng của `Externalizable`.

## 2. Trước hết hiểu Externalizable và Serializable

Hai lựa chọn đều tham gia cơ chế object serialization của Java, nhưng khác nhau về quyền kiểm soát trạng thái và cách khởi tạo lại object.

| Khía cạnh | `Serializable` thông thường | `Externalizable` |
| --- | --- | --- |
| Interface | Marker interface `Serializable`. | `Externalizable extends Serializable`, có hai phương thức công khai. |
| Ghi mặc định | JVM tự xử lý các field đủ điều kiện; có thể tùy biến bằng `private writeObject`. | Chương trình phải tự viết nội dung trong `writeExternal`. |
| Đọc mặc định | JVM phục hồi state theo serialization protocol; có thể tùy biến bằng `private readObject`. | Chương trình phải tự phục hồi state trong `readExternal`. |
| Constructor của chính lớp serializable | **Không** chạy theo cách tạo object thông thường khi deserialize instance `Serializable` thường. | **Có chạy** public no-arg constructor của lớp `Externalizable`. |
| Superclass không serializable | Constructor không tham số truy cập được của superclass đầu tiên không serializable được chạy. | Constructor chain thông thường được gọi từ constructor public no-arg. |
| Field initialization | Field initializer/constructor của chính lớp Serializable không chạy như `new` khi đọc object thông thường. | Field initializer và constructor body của Externalizable chạy theo vòng đời constructor thông thường. |
| `transient` | Mặc định không serialize field transient. | Không tự giới hạn `writeExternal`: lập trình viên quyết định dữ liệu nào được ghi, kể cả field transient nếu cố ý. |
| Độ khó | Thường đơn giản hơn, nhưng vẫn phải quản lý version/độ tin cậy. | Nhiều quyền kiểm soát hơn, đi kèm trách nhiệm định dạng, tương thích và constructor. |

Ví dụ:

~~~java
class SimpleUser implements java.io.Serializable {
    private String name;
    private int age;
    // Không bắt buộc khai báo public SimpleUser().
}
~~~

Đoạn minh họa này không có `main`, chỉ cho thấy `Serializable` không áp dụng quy tắc **public no-arg constructor của chính lớp** như `Externalizable`. Đừng suy ra rằng lớp Serializable không cần bất kỳ constructor phù hợp nào trong hệ kế thừa: **superclass đầu tiên không Serializable** vẫn phải có constructor không tham số với quyền truy cập thích hợp.

### 2.1. Tại sao Java không dùng constructor có tham số?

Khi đọc object từ stream, `ObjectInputStream` có dữ liệu byte nhưng không có quy tắc tổng quát để chọn overload constructor như `Person(String, int)`, `Person(String)` hay `Person(int)`. `Externalizable` quy định một quy trình nhất quán:

1. Tạo object bằng constructor **public không tham số**.
2. Giao đối tượng `ObjectInput` cho `readExternal`.
3. Chính `readExternal` biết **đọc giá trị nào** và **gán vào field nào**.

Vì thế constructor no-arg phải tạo được một **trạng thái ban đầu hợp lệ để tiếp tục phục hồi**, còn `readExternal` chịu trách nhiệm chuyển instance đó thành trạng thái đã lưu.

## 3. Cú pháp đúng của constructor

Một lớp `Externalizable` cơ bản:

~~~java
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class ExternalUser implements Externalizable {

    private String username;
    private int level;

    // BẮT BUỘC: constructor public và không nhận tham số.
    public ExternalUser() {
    }

    // Constructor nhận tham số là TÙY CHỌN, chỉ tiện tạo object mới.
    public ExternalUser(String username, int level) {
        this.username = username;
        this.level = level;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(username);
        out.writeInt(level);
    }

    @Override
    public void readExternal(ObjectInput in)
            throws IOException, ClassNotFoundException {
        username = in.readUTF();
        level = in.readInt();
    }
}
~~~

Đây là **một lớp Java có thể biên dịch** khi lưu thành `ExternalUser.java`, nhưng không có `main` độc lập. Cần bổ sung xử lý trường `username == null` trong code production; `writeUTF` không chấp nhận `null`. Với dữ liệu không tin cậy, `readExternal` cũng phải giới hạn/kiểm tra giá trị của các field.

**Hai constructor cùng tồn tại được:** Java chọn overload có tham số khi ứng dụng chủ động gọi `new ExternalUser("an", 3)`; khi `ObjectInputStream` phục hồi object Externalizable, nó dùng `public ExternalUser()`.

Một constructor đúng phải thỏa cả hai điều kiện:

~~~text
public + không có tham số

public ExternalUser() {}        // ĐÚNG
protected ExternalUser() {}     // SAI đối với Externalizable deserialization
ExternalUser() {}              // SAI (package-private)
private ExternalUser() {}      // SAI
public ExternalUser(int id) {} // SAI nếu không có public ExternalUser()
~~~

**Đừng nhầm default constructor với constructor không tham số do tự viết:** nếu lớp không khai báo bất kỳ constructor nào, trình biên dịch Java tự sinh một constructor no-arg có cùng mức truy cập với lớp. Nhưng **ngay khi bạn khai báo bất kỳ constructor nào**, constructor mặc định đó không còn được tự tạo. Trong class `public ExternalUser` có `ExternalUser(String)`, bạn **phải viết thêm** `public ExternalUser()`.

## 4. Vòng đời chính xác lúc deserialize

Khi có `ObjectOutputStream` và `ObjectInputStream`:

~~~text
1) Application tạo object bằng constructor tùy chọn.
2) out.writeObject(object)
   - Kiểm tra class/serialization protocol.
   - Gọi object.writeExternal(out) để ghi custom data.
3) in.readObject()
   - Đọc descriptor và xác định class cần tái tạo.
   - Kiểm tra/chuẩn bị việc tạo instance Externalizable.
   - Gọi constructor public no-arg của Externalizable class.
   - Constructor gọi super() theo chuỗi constructor Java.
   - Gọi object.readExternal(in) để nạp custom state.
   - Hoàn tất serialization protocol rồi trả về tham chiếu object.
~~~

Vì constructor **thực sự chạy**, bạn cần để ý cả:

- **Field initializer** (ví dụ `private List<String> tags = new ArrayList<>();`) được chạy theo quy trình tạo object thông thường trước body constructor của lớp.
- **Side effects** trong constructor như log, tăng bộ đếm, gọi service hoặc đăng ký observer cũng có thể xảy ra khi deserialize.
- **Validation/invariant**: constructor có thể tạo state mặc định; `readExternal` thay thế các field bằng giá trị từ stream. Phải xác thực giá trị sau khi đọc để instance không rơi vào trạng thái bất hợp lệ.
- **Chuỗi super constructor**: `public Child() { super(); }` phải biên dịch được; nếu superclass không có constructor không tham số truy cập được, cần điều chỉnh thiết kế lớp.

Không nên thực hiện side effects nguy hiểm hoặc không thể hoàn tác trong public no-arg constructor vì bất kỳ lượt đọc object Externalizable hợp lệ nào cũng có thể kích hoạt chúng. Đó là một lý do phải coi Java deserialization là bề mặt tấn công khi nguồn byte không tin cậy.

## 5. Ví dụ Java 17+ đầy đủ: chứng minh thứ tự constructor và readExternal

Lưu toàn bộ chương trình sau trong tệp `ExternalizableConstructorDemo.java`.

~~~java
import java.io.*;

public class ExternalizableConstructorDemo {

    public static class Entry implements Externalizable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String name;
        private int count;

        // ObjectInputStream gọi constructor này khi DESERIALIZE.
        public Entry() {
            System.out.println("constructor: public no-arg");
        }

        // Ứng dụng dùng constructor này để tạo object ban đầu.
        public Entry(String name, int count) {
            this.name = name;
            this.count = count;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            System.out.println("writeExternal");
            out.writeUTF(name);
            out.writeInt(count);
        }

        @Override
        public void readExternal(ObjectInput in)
                throws IOException, ClassNotFoundException {
            System.out.println("readExternal");
            this.name = in.readUTF();
            this.count = in.readInt();
        }

        @Override
        public String toString() {
            return "Entry{name='" + name + "', count=" + count + "}";
        }
    }

    public static void main(String[] args) throws Exception {
        Entry original = new Entry("Hoa", 7);
        byte[] bytes;

        try (ByteArrayOutputStream raw = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(raw)) {

            out.writeObject(original);
            out.flush();
            bytes = raw.toByteArray();
        }

        System.out.println("afterSerialize=" + original);

        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes))) {

            Entry restored = (Entry) in.readObject();
            System.out.println("afterRead=" + restored);
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac ExternalizableConstructorDemo.java
java ExternalizableConstructorDemo
~~~

**Stdout chính xác:** 

~~~text
writeExternal
afterSerialize=Entry{name='Hoa', count=7}
constructor: public no-arg
readExternal
afterRead=Entry{name='Hoa', count=7}
~~~

### 5.1. Vì sao thứ tự in ra như vậy?

1. `new Entry("Hoa", 7)` dùng constructor **có tham số**, nên không in dòng `constructor: public no-arg`.
2. `out.writeObject(original)` gọi `writeExternal()`, in `writeExternal` rồi ghi username và count.
3. Sau khi serialize, `original` vẫn là object cũ; dòng `afterSerialize=...` không có thao tác tạo object mới.
4. `in.readObject()` cần một instance Externalizable mới nên gọi `public Entry()`, in `constructor: public no-arg`.
5. Sau constructor, Java gọi `readExternal()` để đọc lại String và int đúng thứ tự đã ghi; dòng `readExternal` được in.
6. `toString()` của instance đã phục hồi cho thấy `name` và `count` đúng như bản ban đầu.

**Kiểm nghiệm:** ví dụ này đã được biên dịch với tùy chọn `javac --release 17` và chạy thành công trên runtime tương thích Java 17+; stdout nêu trên lấy từ quá trình chạy thực tế. Tính xác định không phụ thuộc timestamp, địa chỉ đối tượng hay đường dẫn tệp.

### 5.2. Tại sao dùng `public static class Entry`?

`Entry` là một **static nested class**: không có tham chiếu ngầm đến một instance lớp bao ngoài. Constructor `public Entry()` thực sự là constructor không tham số trên lớp bytecode tương ứng, cho nên phù hợp với Externalizable.

Ngược lại, một **non-static inner class** thường mang tham số constructor ngầm biểu diễn enclosing instance. Dù người viết khai báo `public Inner()` ở mã nguồn, chữ ký constructor ở mức bytecode vẫn cần tham chiếu đối tượng ngoài. Theo Java Serialization Specification, cơ chế `Externalizable` **không phù hợp** với inner class gắn enclosing instance; nên dùng top-level class hoặc static nested class nếu cần Externalizable.

## 6. Trường hợp sai: thiếu public no-arg constructor

Đây là một lớp ví dụ **có thể biên dịch**, nhưng không thể deserialize bình thường bằng cơ chế Externalizable:

~~~java
public static class BadEntry implements Externalizable {
    private String value;

    public BadEntry(String value) {
        this.value = value;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(value);
    }

    @Override
    public void readExternal(ObjectInput in)
            throws IOException, ClassNotFoundException {
        value = in.readUTF();
    }
}
~~~

**Lưu ý:** đoạn trên dành để đặt bên trong một lớp Java có import `java.io.Externalizable`, `ObjectInput`, `ObjectOutput` và `IOException`; nó không phải top-level class độc lập do có `static`.

Vì `BadEntry` đã có `public BadEntry(String value)`, Java **không sinh** constructor mặc định no-arg. Quy trình thường là:

~~~text
BadEntry original = new BadEntry("x")   -> hợp lệ
ObjectOutputStream.writeObject(original)
                                        -> gọi writeExternal, có thể thành công
ObjectInputStream.readObject()           -> cần public BadEntry()
                                        -> không có
                                        -> InvalidClassException
~~~

Không có gì trong `writeExternal`/`readExternal` sửa được thiếu sót này. Cách khắc phục:

~~~java
public BadEntry() {
    // Cho phép ObjectInputStream khởi tạo lại instance.
}
~~~

Đảm bảo constructor thật sự được khai báo **trong đúng lớp Externalizable cần deserialize**, không phải chỉ ở lớp cha hoặc một lớp khác.

### 6.1. Constructor private/protected/package-private cũng không đạt yêu cầu

~~~java
public class OtherBadEntry implements Externalizable {
    private OtherBadEntry() {} // KHÔNG phải public
    // Cần bổ sung hai phương thức public writeExternal/readExternal.
}
~~~

Đây là **mảnh minh họa không đầy đủ**, không dùng để compile độc lập. Dù code ở cùng package gọi được một constructor package-private, Object Serialization của Externalizable yêu cầu **public no-arg constructor**. Sự khác nhau về accessibility không phải chi tiết trang trí.

### 6.2. Không cần làm mọi field public

Yêu cầu public áp dụng với **constructor không tham số** và hai method thuộc contract `Externalizable`. Các field vẫn có thể và nên là `private`, miễn `readExternal` trong class có cách phục hồi chúng. Đừng thay tất cả field thành public chỉ để serializer dùng được: đó là việc không liên quan và làm hỏng encapsulation.

## 7. Constructor phải làm gì và không nên làm gì?

### 7.1. Constructor nên tạo state tối thiểu an toàn

~~~java
public ExternalOrder() {
    this.items = new ArrayList<>();
    this.status = "NEW";
}
~~~

Đây là đoạn minh họa thuộc một class có `items` và `status` phù hợp. Khi deserialize, các giá trị này chỉ đóng vai trò **trạng thái mặc định ban đầu**; `readExternal` phải đọc dữ liệu theo định dạng đã ghi và cập nhật fields cần thiết.

Việc constructor này chạy lúc read có thể tiện để thiết lập collections hoặc invariant ban đầu. Tuy nhiên, **không** có nghĩa bạn được bỏ `readExternal` hay mặc định các field từ object gốc sẽ tự chui vào constructor. Externalizable chỉ phục hồi những gì chương trình viết/đọc theo protocol.

### 7.2. Tránh side effects trong constructor

Không nên tự động:

- Gửi email, ghi database, đặt đơn hàng hoặc gọi thanh toán.
- Khởi động thread, mở socket, đăng ký job lâu dài.
- Dùng dữ liệu nhận chưa được xác thực để truy cập tệp hay hệ thống khác.
- Dựa vào constructor có tham số để dựng invariant mà quên rằng deserialize dùng constructor **không tham số**.
- Coi việc constructor được gọi là sự kiện “object đã hoàn toàn được phục hồi”: `readExternal` còn phải chạy sau đó.

Nếu việc khởi tạo tốn nhiều chi phí và khó an toàn khi deserialize, cân nhắc dùng một mô hình DTO hoặc định dạng dữ liệu khác thay vì Externalizable.

## 8. Thứ tự writeExternal và readExternal phải khớp nhau

`Externalizable` trao toàn quyền nội dung stream cho người viết class. Nếu ghi:

~~~java
@Override
public void writeExternal(ObjectOutput out) throws IOException {
    out.writeInt(id);
    out.writeUTF(name);
    out.writeBoolean(active);
}
~~~

thì phải đọc lại cùng thứ tự và đúng kiểu:

~~~java
@Override
public void readExternal(ObjectInput in)
        throws IOException, ClassNotFoundException {
    id = in.readInt();
    name = in.readUTF();
    active = in.readBoolean();
}
~~~

Hai đoạn này là **mảnh method minh họa** bên trong một class có các field `id`, `name`, `active` và import phù hợp.

**Sai phổ biến:** gọi `readUTF()` trước `readInt()` khi writer ghi int trước. Tùy dữ liệu cụ thể, kết quả có thể là exception vì đọc sai định dạng, giá trị sai hoặc stream bị lệch vị trí.

Không có default field serialization tự cứu khi nội dung tự viết bị thiếu: nếu một field quan trọng như `balance` không được `writeExternal` ghi và `readExternal` đọc, nó sẽ giữ giá trị được thiết lập trong constructor/field initializer (hoặc mặc định kiểu Java), không tự khôi phục từ object gốc.

### 8.1. Đừng gọi defaultWriteObject/defaultReadObject trong Externalizable

`ObjectOutputStream.defaultWriteObject()` và `ObjectInputStream.defaultReadObject()` là phần của cơ chế custom serialization **`Serializable`** thông qua các private method `writeObject/readObject` thích hợp. `Externalizable` không dùng các phương thức đó như cách thay thế `writeExternal/readExternal`.

Khi triển khai Externalizable, hãy chủ động dùng `ObjectOutput.writeInt/writeUTF/writeObject` và đối xứng `ObjectInput.readInt/readUTF/readObject` theo định dạng do bạn xác định. Trường hợp superclass cũng quản lý trạng thái externalized phải có hợp đồng gọi `super.writeExternal(...)`/`super.readExternal(...)` thích hợp nếu việc kế thừa đó tồn tại.

### 8.2. Nếu field chứa object

~~~java
@Override
public void writeExternal(ObjectOutput out) throws IOException {
    out.writeObject(address);
}

@Override
public void readExternal(ObjectInput in)
        throws IOException, ClassNotFoundException {
    address = (Address) in.readObject();
}
~~~

Đây là **đoạn minh họa**; `Address` cần là một type serializable theo hợp đồng phù hợp hoặc được thay bằng dạng dữ liệu khác. Khi gọi `writeObject/readObject` lồng bên trong Externalizable, cơ chế Java object serialization vẫn xử lý metadata, object identity và đồ thị tham chiếu theo protocol; các object lồng nhau cũng phải an toàn để deserialize.

Đừng tự cast dữ liệu ngoài luồng mà không kiểm tra loại, giới hạn object graph và tính hợp lệ của nội dung.

## 9. serialVersionUID có thay thế constructor không?

**Không.** Hai thứ giải quyết hai vấn đề khác nhau:

| Thành phần | Mục đích |
| --- | --- |
| `public ClassName()` | Cung cấp cách khởi tạo instance Externalizable lúc deserialize. |
| `writeExternal/readExternal` | Xác định payload thực sự lưu và cách phục hồi. |
| `serialVersionUID` | Tham gia kiểm tra tính tương thích class descriptor khi serialize/deserialize theo quy tắc Java Serialization. |
| Version byte riêng trong external payload | Giúp chương trình phân biệt và nâng cấp định dạng custom theo thời gian. |

Ví dụ:

~~~java
@Serial
private static final long serialVersionUID = 1L;
~~~

`@Serial` có trong Java 17; annotation này giúp trình biên dịch/công cụ nhận ra thành viên dùng trong cơ chế serialization. Đặt `serialVersionUID` ổn định **không khiến một lớp thiếu public no-arg constructor trở nên hợp lệ**. Nó cũng không tự chỉnh các trường dữ liệu khi `writeExternal` đổi thứ tự hoặc định dạng.

### 9.1. Version dữ liệu tự định nghĩa

Nếu một class từng ghi `id` rồi `name`, về sau muốn thêm `active`, cần có kế hoạch tương thích. Một lựa chọn là ghi version trong custom payload:

~~~java
@Override
public void writeExternal(ObjectOutput out) throws IOException {
    out.writeInt(2); // Payload format version.
    out.writeInt(id);
    out.writeUTF(name);
    out.writeBoolean(active);
}

@Override
public void readExternal(ObjectInput in)
        throws IOException, ClassNotFoundException {

    int version = in.readInt();
    if (version == 1) {
        id = in.readInt();
        name = in.readUTF();
        active = false;
    } else if (version == 2) {
        id = in.readInt();
        name = in.readUTF();
        active = in.readBoolean();
    } else {
        throw new IOException("Unsupported version: " + version);
    }
}
~~~

Đây là **hai method minh họa**, các field thuộc class bao ngoài và protocol version 1 giả định cũng đã ghi version=1 ở đầu. Nếu version 1 trong thực tế **không** ghi trường version, không thể chỉ thêm `readInt()` này rồi tự suy ra version của stream cũ; phải có kế hoạch migration/định dạng bao bọc khác.

Version payload là lựa chọn của ứng dụng, không phải điều Java tự cung cấp cho Externalizable. Tùy mục tiêu tương thích, đôi khi nên dùng format như JSON/CBOR/Protocol Buffers thay vì Java native object serialization.

## 10. Thừa kế và yêu cầu constructor

### 10.1. Constructor của superclass

Khi class Externalizable được tạo qua constructor no-arg, Java vẫn thực hiện **normal constructor chaining**:

~~~java
class Base {
    Base() {
        System.out.println("base constructor");
    }
}

public class Child extends Base implements Externalizable {
    public Child() {
        super();
        System.out.println("child constructor");
    }

    // Cần triển khai writeExternal/readExternal.
}
~~~

Đây là **mảnh minh họa** về thứ tự constructor, chưa có method bắt buộc nên không compile độc lập. Nếu được triển khai đầy đủ, khi `Child` được deserialize, constructor `Base()` chạy trước body `Child()`.

Nếu `Base` chỉ có `Base(String required)` và không có no-arg, `Child()` không thể gọi `super()` mặc định. Muốn có constructor no-arg ở Child, bạn phải viết lời gọi constructor superclass hợp lệ, chẳng hạn `super("default")` (nếu được phép) hoặc thay đổi thiết kế superclass. Điều này là quy tắc **constructor Java thông thường**, không phải một ngoại lệ do Externalizable tạo ra.

### 10.2. Subclass có kế thừa Externalizable?

Một subclass của lớp implement `Externalizable` cũng thuộc họ Externalizable. Nếu nó được serialize/deserialize với class cụ thể đó, cơ chế phải có cách khởi tạo class đúng contract. Đừng cho rằng public no-arg constructor của **lớp cha** tự động trở thành public no-arg constructor của **lớp con** — constructor **không được kế thừa** trong Java.

Nếu subclass khai báo constructor có tham số mà không khai báo public no-arg tương ứng, nó có thể gặp cùng vấn đề lúc deserialize. Khi subclass override `writeExternal/readExternal`, nó cũng phải quản lý trạng thái superclass theo quy ước đã thiết kế.

### 10.3. Non-static inner class

~~~java
public class Outer {
    public class Inner implements Externalizable {
        public Inner() { }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException { }

        @Override
        public void readExternal(ObjectInput in)
                throws IOException, ClassNotFoundException { }
    }
}
~~~

Đây là **mảnh code minh họa**, cần import java.io và có thể compile trong bối cảnh class phù hợp, nhưng **không nên dùng làm Externalizable object**. `Inner` giữ liên kết ngầm tới `Outer`, nên constructor thực tế cần enclosing instance. Java Serialization Specification nêu rõ Externalizable không áp dụng đúng cho inner class dạng này. Hãy chuyển thành **static nested class** hoặc top-level class.

## 11. Lỗi phổ biến và cách chẩn đoán

| Triệu chứng | Nguyên nhân | Hướng sửa |
| --- | --- | --- |
| `InvalidClassException: no valid constructor` khi `readObject()` | Externalizable class thiếu public no-arg constructor hợp lệ. | Thêm `public ClassName() {}` và bảo đảm superclass constructor chain hợp lệ. |
| Code biên dịch, `writeObject` thành công nhưng `readObject` thất bại | Serializer không cần public no-arg để ghi, nhưng cần lúc đọc. | Kiểm thử cả round-trip, không chỉ ghi file. |
| Constructor no-arg không được trình biên dịch tự tạo | Đã khai báo constructor có tham số. | Tự viết overload no-arg public. |
| `readExternal` đọc sai giá trị hoặc EOF | Thứ tự/số lượng/kiểu read khác với write. | Định nghĩa rõ binary format, versioning và test round-trip. |
| Sau deserialize một field vẫn null/0 | Field chưa được write/read, hoặc constructor chỉ đặt default. | Kiểm tra các field cần lưu và logic khôi phục. |
| Một action được thực hiện “lần nữa” khi đọc object | Constructor có side effect. | Giữ constructor đơn giản và không gây tác động bên ngoài. |
| `InvalidClassException` dù constructor có tồn tại | Có thể sai access, class version, class descriptor/filter hoặc điều kiện serialization khác. | Kiểm tra exact exception/cause, class loader, `serialVersionUID`, public ctor và filter. |
| Inner Externalizable không deserialize được | Constructor bytecode của non-static inner cần outer instance. | Dùng top-level hoặc static nested class. |
| Object restored vi phạm invariant | `readExternal` đưa dữ liệu chưa validate vào field. | Kiểm tra bounds/types/required fields, throw `InvalidObjectException` nếu state không hợp lệ. |
| Stream không tương thích giữa bản phát hành | `writeExternal` đổi format nhưng `readExternal` không hỗ trợ bản cũ. | Có payload format version và migration test phù hợp. |

### 11.1. Kiểm tra public no-arg bằng Reflection

Có thể xác minh constructor mà không tự deserialize:

~~~java
import java.lang.reflect.Constructor;

Constructor<?> ctor = Entry.class.getConstructor();
System.out.println(ctor.getParameterCount() == 0);
~~~

Đây là **mảnh minh họa**, `Entry` cần là class đang kiểm tra và method bao ngoài phải xử lý `NoSuchMethodException`. `getConstructor()` chỉ trả về **public constructor** khớp signature. Nếu có constructor no-arg nhưng private/package-private, lệnh này vẫn không tìm thấy.

Reflection chỉ kiểm tra một phần yêu cầu; nó **không chứng minh** class có format externalization đúng, super constructor không ném lỗi, hoặc dữ liệu trong stream tương thích. Cách thử cuối cùng vẫn là một **round-trip serialize → deserialize** với case điển hình và case lỗi.

## 12. Bảo mật: public constructor khiến deserialize có thể chạy code

Java native deserialization của dữ liệu không tin cậy là nguy hiểm vì đọc bytes có thể:

- Tải class, tạo đối tượng và chạy constructor của Externalizable.
- Gọi `readExternal` do ứng dụng viết, trong đó có thể có side effects hoặc xử lý dữ liệu chưa kiểm tra.
- Đọc tiếp object lồng nhau qua `ObjectInput.readObject`, mở rộng object graph ngoài dự kiến.
- Làm cạn tài nguyên nếu stream khai báo/đòi dữ liệu quá lớn hoặc chứa cấu trúc sâu.
- Chạm vào các class nguy hiểm/gadget có sẵn trên classpath trong một số môi trường.

**Không coi public no-arg constructor là chứng thực nguồn dữ liệu.** Đây chỉ là điều kiện tạo instance. Cần đánh giá dữ liệu input có được xác thực và tin cậy hay chưa.

### 12.1. Giới hạn giá trị trong readExternal

~~~java
@Override
public void readExternal(ObjectInput in)
        throws IOException, ClassNotFoundException {

    int inputCount = in.readInt();

    if (inputCount < 0 || inputCount > 10_000) {
        throw new InvalidObjectException("count out of range");
    }

    this.count = inputCount;
}
~~~

Đây là **method minh họa** thuộc class có field `count` và import `java.io.InvalidObjectException`. Nó minh họa kiểm tra đầu vào trước khi gán state. Trong class có nhiều field, đừng công bố/đăng ký object cho code bên ngoài khi trạng thái mới được phục hồi một phần.

### 12.2. Serialization filter

Java 17 cung cấp `ObjectInputFilter` để giới hạn class và kích thước đồ thị đối tượng khi deserialize.

~~~java
ObjectInputFilter filter =
        ObjectInputFilter.Config.createFilter(
                "maxdepth=10;maxrefs=100;maxbytes=100000;com.example.dto.*;!*");

input.setObjectInputFilter(filter);
Object value = input.readObject();
~~~

Đây là **mảnh minh họa**: `input` là một `ObjectInputStream` do ứng dụng tạo, và phải set filter trước khi đọc. Hãy điều chỉnh allowlist cho đúng tập class dự kiến, bao gồm những class lồng cần thiết; pattern trên chỉ là ví dụ cấu hình, không phải mặc định dùng được cho mọi dự án. Filter là **defense in depth**, không đủ để biến dữ liệu không tin cậy thành an toàn tuyệt đối.

Nếu không có yêu cầu bắt buộc phải dùng Java native serialization, một protocol với schema và parse/validation rõ ràng thường dễ giới hạn rủi ro hơn.

## 13. Checklist thực hành Externalizable

Trước khi viết:

1. **Class thực sự implement `Externalizable`?** Nếu chỉ `Serializable`, quy tắc constructor khác.
2. **Có `public ClassName()` đúng signature?** Không tham số; quyền public; thuộc đúng class được phục hồi.
3. **Constructor chain của superclass chạy hợp lệ?** Constructor Java thông thường không được kế thừa.
4. **Dùng static nested/top-level class?** Tránh non-static inner class gắn enclosing object.
5. **`writeExternal` và `readExternal` có public và đúng signature?** Dùng `@Override` để compiler bắt lỗi.
6. **Thứ tự/kiểu byte viết và đọc đối xứng?** Ghi `int` thì đọc `int`, ghi `UTF` thì đọc `UTF`.
7. **Tất cả state cần thiết được lưu và phục hồi?** Field mặc định của constructor không thay thế dữ liệu ban đầu.
8. **Đã xác định cách xử lý null và optional fields?** `writeUTF(null)` không hợp lệ; cần presence marker/schema.
9. **Có `serialVersionUID` ổn định và version custom payload khi cần?** Hai thứ không thay thế nhau.
10. **Constructor và `readExternal` không có side effect nguy hiểm?** Deserialization có thể gọi cả hai.
11. **Có validate dữ liệu không tin cậy và giới hạn kích thước?** Tránh malformed stream và cạn tài nguyên.
12. **Có test round-trip và test phiên bản cũ?** Compile/serialize thành công chưa chứng minh deserialize thành công.

## 14. Câu hỏi tự kiểm tra

1. **Lớp implement Externalizable có bắt buộc public no-arg constructor không?** Có, để `ObjectInputStream` có thể khởi tạo khi deserialize.
2. **Chỉ có `public User(String name)` có đủ không?** Không, Java không tự sinh `User()` khi đã có constructor do bạn viết.
3. **`private User()` có đạt điều kiện không?** Không. Quyền truy cập phải là `public`.
4. **Constructor public no-arg chạy trước hay sau readExternal?** Trước, rồi `readExternal` khôi phục state.
5. **Constructor có tham số dùng khi ứng dụng tạo object ban đầu có được gọi lại khi deserialize không?** Không theo quy trình Externalizable mặc định; hệ thống dùng public no-arg.
6. **Có cần public tất cả field không?** Không. Field vẫn có thể private; `readExternal` chịu trách nhiệm gán.
7. **Tại sao ghi file thành công vẫn có thể đọc file thất bại?** Yêu cầu constructor phục vụ deserialize, cộng các điều kiện tương thích và định dạng stream.
8. **Serializable thường có chạy constructor của chính lớp Serializable khi deserialize không?** Không theo cơ chế thông thường; constructor của superclass đầu tiên không Serializable có thể được chạy.
9. **Vì sao non-static inner class không phù hợp Externalizable?** Có tham chiếu outer instance ngầm trong constructor bytecode, không cung cấp public no-arg constructor thực sự theo yêu cầu.
10. **`serialVersionUID` thay được constructor không?** Không, nó phục vụ kiểm tra class version chứ không tạo instance.
11. **Một field không được writeExternal ghi, readExternal cũng không đọc thì thế nào?** Nó giữ giá trị mặc định/constructor đã tạo, không tự phục hồi từ object gốc.
12. **Trật tự writeUTF rồi writeInt có thể đọc bằng readInt rồi readUTF không?** Không; định dạng sẽ lệch.
13. **Có nên dùng constructor để gửi email ngay sau khi object được tạo không?** Không, vì deserialize cũng gọi constructor và có thể kích hoạt side effect ngoài ý muốn.
14. **Làm sao phát hiện thiếu public no-arg trước khi đưa lên production?** Dùng reflection `getConstructor()` và quan trọng hơn là test serialize/deserialize round-trip.
15. **readExternal đọc data lạ có thể làm gì?** Validate schema, độ dài, giá trị; ném `IOException`/`InvalidObjectException` nếu dữ liệu sai.
16. **Có nên deserialize trực tiếp bytes từ người không tin cậy không?** Nên tránh khi có thể; nếu buộc dùng cần authentication, filtering, validation và giới hạn tài nguyên.

## 15. Tài liệu chính thức

- [Externalizable — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Externalizable.html): yêu cầu public no-arg constructor, `readExternal` và `writeExternal`.
- [ObjectInputStream — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html): khôi phục object, luồng constructor và cảnh báo dữ liệu không tin cậy.
- [Java Object Serialization Specification: Object Input](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/input.html): thứ tự cấp phát object, gọi constructor và đọc trạng thái.
- [Java Object Serialization Specification: System Architecture](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/serial-arch.html): hợp đồng Externalizable và giới hạn inner class.
- [ObjectInputFilter — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html): giới hạn class/object graph trong quá trình deserialize.

**Ghi nhớ:** `Externalizable` trao quyền ghi/đọc trạng thái cho `writeExternal` và `readExternal`. Để tạo lại object, `ObjectInputStream` **cần gọi `public ClassName()` trước `readExternal()`**. Chỉ constructor có tham số, private, protected hoặc package-private đều **không đủ**. Hãy thiết kế constructor an toàn, thực hiện định dạng ghi/đọc đối xứng, validate state và kiểm thử vòng ghi-đọc bằng Java 17.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-serialization-007">Serializable với enum và record</a>

<details>
<summary>Xem chi tiết</summary>

# Serializable với enum và record trong Java 17+: quy tắc tuần tự hóa, khôi phục và tương thích

## 1. Vì sao enum và record cần được học riêng?

Java Object Serialization cho phép ghi đối tượng thành chuỗi byte bằng `ObjectOutputStream` và khôi phục bằng `ObjectInputStream`. Giao diện đánh dấu `java.io.Serializable` là cơ sở để hầu hết đối tượng Java được xử lý theo cơ chế này.

Tuy nhiên, **`enum` và `record` không có cùng quy tắc tuần tự hóa như class thông thường**:

- `enum` **đã serializable một cách mặc định**. Định dạng đặc biệt lưu **tên hằng enum**, không lưu bộ trạng thái instance như một class thường. Khi đọc, Java tìm lại **chính hằng enum đã tồn tại**.
- `record` **không tự động implements Serializable**. Khi record có `implements Serializable`, dữ liệu serial form được xác định bởi **record components**, và lúc đọc Java gọi **canonical constructor** để tạo đối tượng, thay vì khôi phục field bằng cơ chế class thường.
- Cả hai có những giới hạn về `writeObject`/`readObject`, `serialVersionUID`, xử lý thay đổi định nghĩa lớp và bảo mật khi giải tuần tự hóa.

Bài học tập trung vào Java 17+ và **Java built-in Object Serialization**, không phải JSON, Jackson, protobuf hay mapping cơ sở dữ liệu. Mục tiêu: hiểu nội dung được lưu, điều gì xảy ra khi đọc lại, cách validate và cách tránh nhầm lẫn khi phát triển phiên bản mới.

## 2. Nhắc nhanh: Serializable, stream và object graph

Một class bình thường có thể khai báo:

~~~java
import java.io.Serializable;

class Note implements Serializable {
    private static final long serialVersionUID = 1L;
    String text;
}
~~~

Đoạn trên **chỉ minh họa khai báo**, chưa phải chương trình hoàn chỉnh. Interface `Serializable` là **marker interface**: không yêu cầu cài đặt phương thức nào. `ObjectOutputStream.writeObject(value)` ghi thông tin lớp và trạng thái cần thiết; `ObjectInputStream.readObject()` dựng lại các đối tượng trong object graph.

Hợp đồng thường gặp:

- Một object là `Serializable` **không đảm bảo toàn bộ object graph serializable**. Nếu một field/component tham chiếu tới đối tượng không thể ghi, quá trình có thể ném `NotSerializableException`.
- `static` không thuộc trạng thái riêng của từng instance để được tuần tự hóa; với class thường, `transient` đánh dấu field không lưu theo default serialization.
- Java Object Serialization dùng định dạng nhị phân dành cho Java và cần lớp tương thích khi đọc lại, **không tự biến thành giao thức trao đổi liên ngôn ngữ ổn định**.
- Stream nên được đóng bằng `try-with-resources`. Không giải tuần tự hóa byte từ nguồn không đáng tin chỉ vì chúng có extension `.ser`.
- Cơ chế duy trì tham chiếu và chu trình đối tượng trong Java Serialization có các trường hợp đặc biệt với record; phần sau sẽ giải thích.

Bảng so sánh:

| Đặc điểm | Class `implements Serializable` thông thường | `enum` | `record implements Serializable` |
| --- | --- | --- | --- |
| Có cần khai báo marker? | Có (hoặc kế thừa một kiểu serializable) | **Không**, enum vốn đã serializable | **Có**, record không tự động serializable |
| Nội dung chính trên stream | Các field serializable theo cơ chế class | **Tên của hằng enum** | **Giá trị record components** |
| Khi deserialize | Khôi phục instance và field theo cơ chế class | Tra lại hằng bằng `Enum.valueOf` | **Gọi canonical constructor** |
| Danh tính object | Thường nhận instance được dựng lại | Giữ đúng enum singleton constant (`==`) | Instance mới, so sánh nội dung bằng `equals` |
| `writeObject/readObject` riêng | Có thể dùng khi tuân hợp đồng | **Bị bỏ qua** | **Bị bỏ qua** |
| `writeReplace/readResolve` | Có thể áp dụng theo quy định | **Bị bỏ qua** | **Có thể áp dụng** |
| `serialVersionUID` | Rất quan trọng với compatibility | Cố định `0L`; khai báo bị bỏ qua | Mặc định `0L` hoặc khai báo riêng; kiểm tra UID khớp được miễn |
| Chu trình trỏ ngược về chính đối tượng | Có thể được giữ với cơ chế object handles | Enum là hằng có sẵn | Chu trình xuyên qua components trỏ lại record **không được giữ nguyên** |

## 3. Enum: lưu tên hằng, không lưu trạng thái instance

### 3.1. Tại sao không cần `implements Serializable`?

Mọi kiểu `enum` Java kế thừa `java.lang.Enum`, vốn thực thi `Serializable`. Ví dụ:

~~~java
enum Status {
    NEW, PROCESSING, DONE
}
~~~

Đoạn này đủ để những giá trị như `Status.DONE` được truyền cho `ObjectOutputStream.writeObject(...)`; không cần thêm marker hoặc một constructor đặc biệt.

**Khi ghi:** stream lưu định danh kiểu enum và tên hằng được trả bởi `name()`, chẳng hạn `"DONE"`. Giá trị ordinals hoặc các field instance do bạn khai báo trong hằng không phải phần trạng thái được lưu theo cơ chế enum đặc biệt.

**Khi đọc:** Java giải tên hằng bằng cơ chế tương đương `Enum.valueOf(Status.class, "DONE")`. Vì thế, kết quả là **chính `Status.DONE` hiện có trong JVM**, không tạo một instance enum thứ hai.

~~~java
Status original = Status.DONE;
// Sau khi writeObject rồi readObject thành công:
Status restored = Status.DONE; // Ví dụ giá trị được khôi phục.
System.out.println(original == restored); // true
~~~

Đoạn trên chỉ mô tả kết quả. Chương trình chạy hoàn chỉnh nằm ở mục 7.

### 3.2. `name()` khác `toString()` và `ordinal()`

Một enum có thể định nghĩa tên hiển thị:

~~~java
enum Level {
    LOW("Thấp"), HIGH("Cao");

    private final String label;

    Level(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
~~~

Nếu serialize `Level.HIGH`, Java lưu **tên `HIGH`**, chứ không lấy chuỗi `"Cao"` từ `toString()`. Cũng không lưu `ordinal()` làm định danh của hằng.

Hệ quả:

- Thay `toString()` hoặc tên hiển thị có thể không phá deserialization của giá trị enum cũ.
- Sắp xếp lại thứ tự khai báo các hằng không tự làm thay đổi tên `HIGH` được lưu.
- **Đổi tên/xóa hằng** đã từng xuất hiện trên stream cũ có thể làm quá trình khôi phục thất bại vì tên không còn tồn tại. Đây là khác biệt quan trọng khi nâng version.

### 3.3. Các field trong enum không được khôi phục từ stream

Xét enum có state:

~~~java
enum Mode {
    SAFE, FAST;

    private int visits;

    void markUsed() {
        visits++;
    }

    int visits() {
        return visits;
    }
}
~~~

Nếu gọi `Mode.SAFE.markUsed()` trước khi serialize, byte stream của `Mode.SAFE` vẫn chỉ đại diện cho **hằng SAFE**, không chứa bản sao `visits`.

Khi đọc enum trong **cùng JVM**, bạn nhận lại `Mode.SAFE` với state hiện tại của singleton trong JVM đó. Nếu deserialize ở một JVM khác, state do runtime đó khởi tạo/quản lý, **không phải số lượt visits từng có trên JVM nguồn**. Không dùng enum serialization để đồng bộ dữ liệu mutable gắn trong enum.

### 3.4. Custom hooks của enum không có tác dụng

Với enum, Java Object Serialization bỏ qua các phương thức tùy biến do enum định nghĩa như `writeObject`, `readObject`, `readObjectNoData`, `writeReplace` và `readResolve`. Nó cũng bỏ qua `serialPersistentFields` và bất kỳ field `serialVersionUID` nào tự khai báo; UID của enum luôn là `0L`.

Vì vậy **đừng viết `readResolve()` trong enum để bảo vệ tính duy nhất**: quy tắc khôi phục bằng tên vốn đã đảm bảo không tạo enum constant mới. Đừng nghĩ viết `writeObject` để lưu thêm field động sẽ có tác dụng. Nếu cần lưu dữ liệu thay đổi theo từng request, hãy đặt nó vào **class hoặc record serializable riêng**, còn enum chỉ mô tả loại/trạng thái có số lựa chọn hữu hạn.

## 4. Record: bất biến về cấu trúc không đồng nghĩa Serializable

### 4.1. Phải khai báo `implements Serializable`

Record có mặt chính thức từ Java 16 (sử dụng ổn định trong Java 17), giúp biểu diễn dữ liệu qua các **record components**:

~~~java
record Point(int x, int y) {
}
~~~

Record trên **chưa serializable**, dù có constructor, accessor, `equals`, `hashCode` và `toString` do compiler tạo. Nếu truyền một instance vào `ObjectOutputStream.writeObject`, có thể nhận `NotSerializableException`.

Bản hỗ trợ Java Object Serialization cần khai báo:

~~~java
import java.io.Serializable;

record Point(int x, int y) implements Serializable {
}
~~~

Chỉ thêm marker chưa đủ nếu component có kiểu tham chiếu chứa dữ liệu không serializable. Ví dụ:

~~~java
record Holder(Object value) implements Serializable {
}

Holder invalid = new Holder(new Object());
// writeObject(invalid) có thể ném NotSerializableException
// vì java.lang.Object không implements Serializable.
~~~

Hai đoạn trên là **minh họa**. Kiểu component `Object` không tự khiến mọi giá trị bên trong đều được hỗ trợ, cũng như `List` không đảm bảo mọi implementation và mọi element đều serializable.

### 4.2. Java ghi gì cho record?

Với:

~~~java
record Product(String sku, int quantity) implements Serializable {
}
~~~

Dạng serial của record dựa vào **giá trị hai component** `sku` và `quantity`. Một record không thể khai báo instance field tùy ý ngoài các field được tạo cho record components. Những biến `static` không thuộc trạng thái mỗi instance và không phải component serial form.

Khác với class thông thường, quá trình serialize record không dùng `serialPersistentFields` để chọn field tùy ý và không gọi `writeObject` do record định nghĩa. Vì thế **tập component là hợp đồng dữ liệu trực tiếp** cần xem xét khi versioning.

Điều này không phải “record serialize thành JSON”. Byte stream Java vẫn chứa metadata kiểu/class và thông tin field theo giao thức Java Object Serialization.

### 4.3. Canonical constructor chạy khi deserialize

Record được khôi phục thông qua **canonical constructor**: Java dựng các giá trị component từ stream rồi gọi constructor này.

~~~java
import java.io.Serializable;
import java.util.Objects;

record Account(String id, int balance) implements Serializable {
    public Account {
        Objects.requireNonNull(id, "id");
        if (id.isBlank() || balance < 0) {
            throw new IllegalArgumentException("Invalid account");
        }
    }
}
~~~

Đây là khai báo minh họa. `public` trong compact canonical constructor phù hợp cho record public; nếu record package-private, quyền truy cập constructor có thể không cần `public`. Nhờ việc canonical constructor được gọi lúc deserialize, các kiểm tra `id` không trống và `balance >= 0` có cơ hội được áp dụng cả khi dữ liệu đến từ một serialized stream.

Nếu constructor từ chối giá trị được khôi phục, Java có thể báo `InvalidObjectException` (nguyên nhân gốc là exception từ constructor). **Không nên dựa vào setter sau khi đọc để vá invariant**: record được xây dựng với giá trị đã đọc qua constructor.

Quan trọng: validation của record **không có nghĩa nguồn dữ liệu được tin cậy**. Byte stream có thể kích hoạt việc tạo, parse hoặc khôi phục các object component khác trước khi canonical constructor của record được gọi. Phải áp dụng chính sách an toàn cho deserialization ở phạm vi toàn bộ stream.

### 4.4. Phương thức serializer hook nào có hiệu lực?

Với record serializable, các hook tương tự class thường có sự khác biệt:

| Thành phần | Record trong Java Serialization |
| --- | --- |
| `private void writeObject(ObjectOutputStream)` | Bị bỏ qua |
| `private void readObject(ObjectInputStream)` | Bị bỏ qua |
| `readObjectNoData` | Bị bỏ qua |
| `writeExternal/readExternal` | Bị bỏ qua đối với serial form của record |
| `serialPersistentFields` | Bị bỏ qua |
| `writeReplace` | Có thể dùng cơ chế thay thế object |
| `readResolve` | Có thể dùng để chọn object thay thế sau khi khôi phục |
| Canonical constructor | **Được gọi** khi tạo record từ stream |
| `serialVersionUID` | Mặc định `0L`; có thể khai báo nhưng đối chiếu UID khớp được miễn đối với record |

Nếu cần xác thực component, ưu tiên đặt invariant trong canonical constructor. Nếu cần **định dạng tuần tự hóa riêng hoàn toàn**, record mặc định không cho tùy biến `writeObject/readObject` như class thường; hãy cân nhắc một **serialization proxy** dùng `writeReplace/readResolve` hoặc dùng định dạng tường minh như JSON/CBOR/protobuf với schema rõ ràng.

Không nên bắt đầu bằng hook để xử lý vấn đề vốn chỉ là validation; càng ít hành vi tùy biến trên stream, khả năng kiểm soát và review càng tốt.

## 5. SerialVersionUID: enum, record và class khác nhau

`serialVersionUID` là định danh phiên bản lớp trong Java Serialization. Với class thường, việc không khớp UID có thể gây `InvalidClassException` khi deserialize.

**Enum:**

- UID luôn cố định là `0L`.
- Nếu viết một field cùng tên `serialVersionUID` trong enum, serialization **bỏ qua** field đó.
- Không dùng UID để đánh số version cho tên hằng enum.

**Record:**

- UID mặc định là `0L`, trừ khi record khai báo một giá trị tường minh.
- Java **miễn yêu cầu hai UID phải khớp** trong quá trình giải tuần tự hóa record.
- Điều này **không bảo đảm mọi thay đổi cấu trúc record đều tương thích**: việc thêm/xóa/đổi tên/đổi kiểu component vẫn có thể thay đổi dữ liệu được khôi phục hoặc làm constructor từ chối.

Ví dụ:

~~~java
import java.io.Serial;
import java.io.Serializable;

record Invoice(String code, long cents) implements Serializable {
    @Serial
    private static final long serialVersionUID = 17L;
}
~~~

`@Serial` (có từ Java 14) giúp biểu đạt ý định khai báo field/method dành cho serialization và hỗ trợ compiler phát hiện một số khai báo sai ngữ nghĩa. UID ở đây là một thuộc tính serial version của record, nhưng đừng diễn giải nó thành cơ chế bắt buộc kiểm tra khớp như class bình thường.

## 6. Thay đổi định nghĩa lớp và khả năng đọc dữ liệu cũ

### 6.1. Khi enum thay đổi

Giả sử bản cũ:

~~~java
enum Phase {
    NEW, DONE
}
~~~

và bản mới:

~~~java
enum Phase {
    NEW, ACTIVE, DONE
}
~~~

Thêm `ACTIVE` thường không ngăn stream cũ có giá trị `NEW` hoặc `DONE` được giải lại, miễn những tên đã lưu còn tồn tại. Ngược lại, đổi `DONE` thành `FINISHED` sẽ khiến stream cũ chứa tên `DONE` không tìm thấy hằng tương ứng.

**Hướng xử lý nếu cần hỗ trợ lâu dài:** tránh đổi tên enum constant đã công bố trong Java serialized data; cân nhắc lưu **mã nghiệp vụ ổn định riêng** nếu giao thức cần tiến hóa qua nhiều version. Đừng giả định một enum name là mã liên hệ ngoài Java bền vững mãi mãi.

### 6.2. Khi record thay đổi

Bản đầu:

~~~java
record User(String name, int age) implements Serializable {
}
~~~

Sau đó thêm component:

~~~java
record User(String name, int age, String region)
        implements Serializable {
}
~~~

Khi local record có component không xuất hiện trong stream cũ, giá trị được truyền cho canonical constructor là **giá trị mặc định của kiểu**: `0` cho `int`, `false` cho `boolean`, `null` cho reference. Nếu component mới `region` không có trong stream, constructor nhận `null` cho `region`.

Nếu constructor mới từ chối `null`:

~~~java
record User(String name, int age, String region)
        implements Serializable {
    public User {
        if (region == null) {
            throw new IllegalArgumentException("region required");
        }
    }
}
~~~

thì dữ liệu cũ có thể không deserialize được. Nếu điều khoản nghiệp vụ cho phép, có thể chuẩn hóa thiếu thông tin trong constructor, chẳng hạn đặt `region = "UNKNOWN"`; điều đó phải là quyết định schema có chủ đích, không tự đặt mặc định cho trường nhạy cảm.

Cơ chế còn có đặc điểm:

- Dữ liệu field trong stream được khớp với component theo **tên** và được kiểm tra kiểu thích hợp; không giả định đơn giản rằng chỉ thứ tự khai báo quyết định mapping.
- Field tồn tại trong stream nhưng không còn là component của record local có thể bị bỏ qua.
- Nếu đổi kiểu component hoặc thay cấu trúc không tương thích, kết quả có thể là exception thay vì giá trị chuyển kiểu tự động.
- **Validation chạy lại** trong canonical constructor, nên việc nâng version phải tính cả invariant mới.
- Việc UID không yêu cầu khớp không phải “giấy phép” thay schema tùy ý mà vẫn đọc được mọi dữ liệu cũ.

Với dữ liệu lưu dài hạn, lưu thêm version ở **định dạng nghiệp vụ** hoặc xây cơ chế migration có kiểm thử riêng. Dùng Java Object Serialization cho cache tạm nội bộ khác về yêu cầu với dùng nó làm format lưu trữ 10 năm.

### 6.3. Vòng đời class và tên kiểu

Cả enum và record vẫn phụ thuộc việc JVM đọc stream có thể tìm được class tương ứng. Đổi package, đổi tên class, thay classpath/module visibility hoặc thiếu class có thể dẫn tới `ClassNotFoundException` hay lỗi tương thích khác, dù tên hằng hoặc component không đổi.

Đừng nhầm “tương thích dữ liệu record” với “không cần bảo trì tên class”. Byte stream Java chứa thông tin kiểu của hệ sinh thái Java.

## 7. Ví dụ Java 17+ hoàn chỉnh: round-trip enum và record

Chương trình sau không dùng tệp trên đĩa: nó ghi Java serialized bytes vào `ByteArrayOutputStream` rồi đọc lại bằng `ByteArrayInputStream`. Trong demo, mọi byte đều **do chính chương trình tạo**; đây là ví dụ học cách hoạt động, **không phải hướng dẫn chấp nhận byte deserialization không tin cậy từ mạng**.

Mã minh họa rằng enum giữ identity, record có value equality nhưng không giữ identity, và canonical constructor được gọi khi khôi phục.

Lưu tệp **`EnumRecordSerializationDemo.java`**:

~~~java
import java.io.*;
import java.util.Objects;

public class EnumRecordSerializationDemo {
    enum Phase { NEW, DONE }

    record WorkItem(String id, Phase phase, int retries)
            implements Serializable {
        @Serial
        private static final long serialVersionUID = 17L;
        private static int constructed;

        WorkItem {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(phase, "phase");
            if (id.isBlank() || retries < 0) {
                throw new IllegalArgumentException("Invalid work item");
            }
            constructed++;
        }
    }

    private static byte[] encode(Object value) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(sink)) {
            out.writeObject(value);
        }
        return sink.toByteArray();
    }

    private static Object decode(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    public static void main(String[] args) throws Exception {
        Phase state = Phase.DONE;
        Phase restoredState = (Phase) decode(encode(state));
        System.out.println("Enum identity preserved: "
                + (state == restoredState));

        WorkItem original = new WorkItem("task-42", Phase.DONE, 2);
        WorkItem restored = (WorkItem) decode(encode(original));
        System.out.println("Record equality preserved: "
                + original.equals(restored));
        System.out.println("Record same instance: "
                + (original == restored));
        System.out.println("Enum component identity: "
                + (restored.phase() == Phase.DONE));
        System.out.println("Canonical constructor calls: "
                + WorkItem.constructed);
        System.out.println("Restored: " + restored);
    }
}
~~~

Biên dịch/chạy với JDK 17+:

~~~bash
javac --release 17 -encoding UTF-8 EnumRecordSerializationDemo.java
java EnumRecordSerializationDemo
~~~

**Stdout đã được kiểm tra bằng biên dịch `--release 17` và thực thi JVM:**

~~~text
Enum identity preserved: true
Record equality preserved: true
Record same instance: false
Enum component identity: true
Canonical constructor calls: 2
Restored: WorkItem[id=task-42, phase=DONE, retries=2]
~~~

### Phân tích chính xác từng kết quả

1. `Phase` là enum, nên không cần `implements Serializable`. Giá trị `DONE` từ stream được tra về singleton `Phase.DONE`, vì vậy `state == restoredState` là `true`.
2. `WorkItem` khai báo `implements Serializable`; nếu bỏ marker, `writeObject(original)` sẽ không serialize được record này theo cơ chế Java Object Serialization thông thường.
3. `original.equals(restored)` là `true` vì record equality so sánh theo component (trong ví dụ `String`, enum và `int` có semantics phù hợp).
4. `original == restored` là `false`: record được dựng lại thành một object riêng. Không suy ra mọi record sau round-trip đều giữ identity.
5. Component `phase` vẫn là enum singleton nên `restored.phase() == Phase.DONE` là `true`.
6. Biến `constructed` là static, **không** được lưu vào byte stream. Nó tăng một lần khi gọi `new WorkItem(...)` và một lần khi canonical constructor được gọi bởi deserialization; vì vậy kết quả là 2 trong tiến trình demo này.
7. `@Serial private static final long serialVersionUID = 17L` là UID tường minh của record. Với record, việc khớp UID không được bắt buộc theo quy tắc dành riêng.
8. `ObjectOutputStream` được đóng sau khi ghi để flush và giải phóng tài nguyên; `ObjectInputStream` được đóng sau khi đọc. `ByteArrayOutputStream.toByteArray()` cung cấp bản sao byte.
9. Vì `ObjectInputStream.readObject()` trả về `Object`, mã demo cast sang kiểu mong đợi. Với dữ liệu không tin cậy, **cast sau deserialize không phải cơ chế an toàn**, do quá trình tạo object đã có thể xảy ra trước đó.

## 8. Vì sao validation trong canonical constructor có giá trị?

Một class `Serializable` thông thường có thể được dựng lại mà constructor dành cho instance serializable thông thường không chạy theo cách tạo mới. Điều này khiến invariant cần các cơ chế đặc thù như `readObject` hoặc serialization proxy.

Record khác ở chỗ **canonical constructor thực sự được gọi** khi deserialize. Bởi vậy, những invariant như số lượng không âm, mã định danh hợp lệ hoặc enum component không `null` có thể được kiểm tra tập trung tại nơi tạo object.

Ví dụ:

~~~java
record Reservation(String code, int seats)
        implements Serializable {
    public Reservation {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (seats <= 0 || seats > 20) {
            throw new IllegalArgumentException("seats out of range");
        }
    }
}
~~~

Mã minh họa này có lợi vì object sai quy tắc không được khôi phục thành record hợp lệ một cách âm thầm. Tuy nhiên:

- Constructor được gọi **sau khi đọc các component values**, do đó các object tham chiếu trong graph có thể đã được deserialized.
- Mọi object component cần validate riêng; record không tự biến mutable list/map thành immutable.
- Tránh side effect trong constructor, vì đọc một byte stream có thể khiến constructor chạy. Constructor nên ưu tiên kiểm tra giá trị, chuẩn hóa dữ liệu thuần và không tự thực hiện lệnh gửi tiền, gọi mạng hoặc thay đổi tài nguyên.

### Record là shallowly immutable

Với:

~~~java
record Batch(int[] numbers) implements Serializable {
}
~~~

Field tham chiếu `numbers` là final, nhưng **phần tử của mảng vẫn có thể sửa**. Một người giữ tham chiếu tới array có thể thay đổi giá trị sau khi tạo record; hoặc dùng accessor để lấy mảng rồi sửa.

Nếu hợp đồng cần bất biến sâu hơn, sao chép defensively trong canonical constructor và khi trả về (đối với array), hoặc dùng cấu trúc immutable thích hợp. Điều này là **vấn đề thiết kế dữ liệu**, không được cơ chế serialization tự động giải quyết.

## 9. Chu trình đối tượng và giới hạn riêng của record

Object Serialization thường dùng handles/back references để giữ quan hệ tham chiếu và nhiều chu trình trong graph. Tuy nhiên, với record, **các component được deserialize trước khi record object được dựng qua canonical constructor**.

Giả sử một `Node` thông thường có field trỏ ngược về một record đang chứa chính Node đó. Nếu graph gốc có chu trình từ record → Node → record, khi đọc, tham chiếu trỏ ngược đến record có thể thành `null`. Đây là hạn chế được đặc tả Java Serialization nêu rõ; không nên dựa vào record để biểu diễn graph phức tạp có các back-reference trở lại chính record.

**Mẫu thiết kế phù hợp hơn:** serial form dạng acyclic DTO/record, lưu ID thay vì tham chiếu chu trình, rồi xây lại các quan hệ sau khi validate theo một bước dựng graph tường minh.

Ví dụ dùng ID:

~~~java
record Edge(String fromId, String toId)
        implements Serializable {
}
~~~

Trường `fromId/toId` là dữ liệu ổn định để tái liên kết trong graph sau khi deserialize, không phải reference vòng về record đang được tái dựng.

## 10. Bảo mật: enum và record không làm deserialization trở nên an toàn

**Không deserialize byte không tin cậy chỉ vì kiểu kết quả mong đợi là enum hoặc record.** Luồng Java Serialization có thể chứa object graph với nhiều lớp khác, và quá trình đọc có thể kích hoạt hành vi trong lớp trước khi kết quả được cast hoặc validate cuối cùng.

Các quy tắc an toàn thực hành:

1. **Ưu tiên tránh Java native deserialization cho dữ liệu từ bên ngoài.** Nếu cần giao thức công khai, dùng dữ liệu có schema rõ ràng như JSON/protobuf/CBOR và bộ parser có kiểm soát.
2. Nếu bắt buộc dùng Java Serialization, **chỉ chấp nhận nguồn đáng tin cậy** và giới hạn lớp được phép deserialize. Dùng `ObjectInputFilter` (JDK 9+, sẵn trong Java 17) theo mô hình allowlist/threat model.
3. Giới hạn độ sâu graph, số references, số byte stream, độ dài array và ngân sách tài nguyên; **lọc không thay thế xác thực nguồn**.
4. Kiểm soát toàn bộ thư viện/classpath: những lớp serializable trong object graph có thể có hook đọc đặc biệt.
5. Canonical constructor của record thực hiện validation sau khi component values đã đọc, do đó **không thay thế kiểm tra deserialization input**.
6. Có chính sách lỗi rõ ràng cho `InvalidClassException`, `ClassNotFoundException`, `StreamCorruptedException`, `InvalidObjectException` và `NotSerializableException`.
7. Không log toàn bộ serialized payload hoặc dữ liệu nhạy cảm; chú ý size/quota cả khi ghi vào `ByteArrayOutputStream` trong ví dụ học tập.

Một đoạn minh họa về giới hạn dạng đồ thị bằng `ObjectInputFilter`:

~~~java
ObjectInputFilter limit = info -> {
    if (info.depth() > 12
            || info.references() > 500
            || info.streamBytes() > 1024 * 1024) {
        return ObjectInputFilter.Status.REJECTED;
    }
    return ObjectInputFilter.Status.UNDECIDED;
};

try (ObjectInputStream in = new ObjectInputStream(source)) {
    in.setObjectInputFilter(limit);
    Object value = in.readObject();
}
~~~

Đây **không phải chương trình độc lập**; cần import `ObjectInputFilter` và `ObjectInputStream`, `source` là `InputStream` đã có. Đoạn minh họa **chỉ đặt giới hạn resource**, chưa allowlist lớp, nên **chưa đủ** để deserialize input không tin cậy một cách an toàn. Trong production, ưu tiên filter kết hợp allowlist các class thực sự cần đọc cùng giới hạn byte/depth/array; kiểm thử filter với toàn bộ object graph hợp lệ.

**Lưu ý:** dữ liệu từ chính chương trình của bạn cũng có thể hết hạn/không tương thích sau một lần nâng phiên bản; “trusted source” không đồng nghĩa “đọc được ở mọi version”.

## 11. Enum và record trong hệ thống thực tế

### 11.1. Record DTO nội bộ hoặc cache tạm

Ví dụ:

~~~java
enum TaskStatus { QUEUED, DONE }

record TaskSnapshot(String id, TaskStatus status, long updatedAt)
        implements Serializable {
}
~~~

Một `TaskSnapshot` chỉ chứa primitive, `String` và enum có thể được round-trip tương đối đơn giản. Nếu sử dụng như cache tạm trong nội bộ JVM/Java ecosystem và luôn kiểm soát version tương thích, Java Serialization có thể khả thi.

Nhưng nếu lưu trong database lâu dài, gửi liên dịch vụ nhiều ngôn ngữ hay chia sẻ giữa phiên bản ứng dụng phát triển độc lập, định dạng có schema tường minh thường dễ migration, audit và kiểm soát bảo mật hơn.

### 11.2. Enum như domain code, record như payload

Một thiết kế dễ hiểu:

- Enum biểu thị tập trạng thái trong Java code.
- Record biểu thị dữ liệu của một snapshot/request.
- Khi xuất ra định dạng lâu dài, ánh xạ enum sang mã nghiệp vụ ổn định, ví dụ `"completed"`, và khai báo schema payload. Không buộc dữ liệu bên ngoài phụ thuộc mãi vào tên hằng Java.

Cách này cho phép dùng enum/record trong miền Java nhưng vẫn quản lý migration tường minh nếu đổi tên package, record type hoặc enum constant.

### 11.3. Khi cần custom serial form

Nếu cần custom hook `writeObject/readObject`, record không có đường tùy biến giống class `Serializable` thường. Một số phương án:

- Dùng class serializable thông thường với các hook theo đặc tả (và review chặt).
- Dùng **serialization proxy** tường minh qua `writeReplace` trên record và `readResolve` trên proxy; lưu payload dạng dễ versioning. Đây là kỹ thuật nâng cao cần test nhiều phiên bản và đảm bảo loại thay thế tương thích.
- Dùng codec/schema độc lập Object Serialization nếu mục tiêu là lưu/trao đổi dữ liệu ổn định.

Không dùng `readResolve` của enum làm proxy hoặc để kiểm tra rename: hook enum bị bỏ qua.

## 12. Lỗi thường gặp và cách sửa

| Hiểu nhầm | Hậu quả | Cách hiểu đúng |
| --- | --- | --- |
| “Record tự Serializable vì là data carrier” | `NotSerializableException` | Phải `implements Serializable` rõ ràng |
| “Enum cần implements Serializable” | Khai báo thừa, hiểu sai cơ chế | Enum đã serializable qua `java.lang.Enum` |
| “Enum serialize ordinal” | Thiết kế versioning sai | Enum lưu **tên hằng** |
| “Enum serialize mọi field instance” | Mong đợi mutable state được đồng bộ | Chỉ tên hằng được dùng để khôi phục constant |
| “Đổi tên enum constant vẫn đọc stream cũ” | Không tìm được tên đã lưu | Giữ tên cũ hoặc làm migration tường minh |
| “Enum readResolve chống tạo nhiều singleton” | Hook không được dùng | Enum vốn giữ singleton identity |
| “Record readObject sẽ chạy” | Validation bị đặt sai chỗ | Đặt invariant trong canonical constructor |
| “Record deserialization bỏ qua constructor” | Bỏ sót việc constructor có thể ném lỗi | Canonical constructor được gọi |
| “Record == giữ nguyên sau round-trip” | So sánh identity sai | So sánh value qua `equals` khi phù hợp |
| “UID record luôn phải khớp” | Hiểu sai tương thích | UID matching được miễn riêng cho record |
| “UID record không quan trọng tức mọi schema tương thích” | Lỗi khi thay component/invariant | Kiểm tra tên/kiểu/default/constructor |
| “Record field final nên toàn bộ dữ liệu immutable sâu” | Component mutable vẫn bị sửa | Defensive copy/schema immutable |
| “Mọi vòng tham chiếu record đều được giữ” | Back-reference có thể thành `null` | Tránh cycle trỏ ngược đến record |
| “Record/enum loại bỏ rủi ro deserialize untrusted stream” | Nguy cơ từ object graph khác | Tránh untrusted Java Serialization; filter/allowlist |
| “Cast sau readObject xác thực trước khi tạo object” | Quá muộn để chặn side effect khi deserialize | Dùng cấu trúc/định dạng/filter an toàn từ đầu |

## 13. Câu hỏi tự kiểm tra

**Câu 1.** `enum Color { RED, BLUE }` có cần `implements Serializable` mới ghi bằng `ObjectOutputStream` được không?

**Đáp án:** Không. Enum vốn đã serializable.

**Câu 2.** Java lưu `Color.RED` dưới dạng ordinal hay tên?

**Đáp án:** Tên `RED`, cùng thông tin kiểu cần thiết của stream; không dùng ordinal để xác định hằng.

**Câu 3.** Có thể thay `toString()` của enum mà không đổi định danh serialized constant không?

**Đáp án:** Có. Định danh dựa trên `name()`; cần thận trọng nếu thay chính tên constant.

**Câu 4.** `record User(String name) {}` có được serialize bằng `writeObject(new User("Lan"))` không?

**Đáp án:** Không theo Java Object Serialization mặc định vì chưa khai báo `implements Serializable`.

**Câu 5.** Record đọc lại từ stream có gọi canonical constructor không?

**Đáp án:** Có. Constructor có thể xác thực invariant và từ chối dữ liệu không hợp lệ.

**Câu 6.** `private void readObject(...)` do record khai báo có tùy biến quá trình khôi phục không?

**Đáp án:** Không. Hook đó bị bỏ qua khi serialize/deserialize record; dùng canonical constructor hoặc cách thiết kế serial form khác phù hợp.

**Câu 7.** Thêm component `String region` vào record sẽ nhận gì khi đọc stream cũ không có region?

**Đáp án:** Giá trị mặc định `null` được truyền cho canonical constructor; nếu constructor từ chối null thì dữ liệu cũ có thể không được khôi phục.

**Câu 8.** Có thể dựa vào UID record được miễn kiểm tra khớp để khẳng định việc đổi kiểu component luôn an toàn?

**Đáp án:** Không. Tên/kiểu/default và validation vẫn quyết định khả năng khôi phục.

**Câu 9.** Deserialization của một record chứa object graph có vòng trỏ ngược về record có luôn giữ nguyên reference identity?

**Đáp án:** Không. Chu trình từ components trỏ lại record có hạn chế đặc thù; tham chiếu ngược có thể thành `null`.

**Câu 10.** Với byte nhận từ HTTP request, chỉ nhận kiểu kết quả `MyRecord` là đã an toàn để gọi `readObject()`?

**Đáp án:** Không. Các object trong stream có thể được xử lý trước khi kết quả được cast. Tránh native deserialization không tin cậy hoặc áp filter/allowlist và nhiều lớp bảo vệ phù hợp.

## 14. Ghi nhớ

- **Enum:** đã `Serializable`; lưu tên hằng, đọc lại đúng enum constant, giữ `==`; không lưu state instance và không dùng custom serialization hooks.
- **Record:** phải `implements Serializable`; lưu record components; **gọi canonical constructor** khi deserialize; thông thường có value equality nhưng là object mới.
- **Tương thích:** enum phụ thuộc tên constant; record phụ thuộc tên/kiểu component, default cho component thiếu và invariant của constructor; UID matching record được miễn nhưng không đảm bảo mọi migration.
- **Giới hạn:** record không tùy biến `writeObject/readObject` kiểu class thường; có ngoại lệ `writeReplace/readResolve`; chu trình trỏ ngược về record không được bảo toàn như ordinary class.
- **Bảo mật:** Java Object Serialization không dành cho đầu vào tùy ý chưa tin cậy; enum và record không thay thế kiểm soát object graph, giới hạn tài nguyên hay schema.

### Tài liệu chính thức

- [Java SE 17 — Object Serialization Specification, System Architecture](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/serial-arch.html) — mục 1.12 (enum), 1.13 (record), 1.14 (circular references).
- [Java SE 17 — ObjectInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html) — API deserialize và các lưu ý bảo mật.
- [Java SE 17 — ObjectInputFilter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html) — filtering khi deserialization.
- [Java SE 17 — Serializable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serializable.html) — marker interface và cơ chế class serializable.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-serialization-008">@Serial và kiểm tra khai báo thành viên đặc biệt</a>

<details>
<summary>Xem chi tiết</summary>

# `@Serial` và kiểm tra khai báo thành viên đặc biệt trong Java Serialization

## 1. Bối cảnh: lỗi nhỏ trong khai báo có thể đổi hành vi tuần tự hóa

Java Object Serialization có một số **field và method đặc biệt** mà cơ chế `ObjectOutputStream`/`ObjectInputStream` tìm kiếm theo **tên, kiểu, modifier và ngữ cảnh lớp**. Những thành viên này không phải là các phương thức thông thường của interface `Serializable`: phần lớn được runtime truy cập theo quy tắc phản chiếu (reflective lookup).

Ví dụ, người viết định nghĩa:

```java
private void writeObject(java.io.OutputStream out)
        throws java.io.IOException {
    // ...
}
```

Tên `writeObject` trông có vẻ đúng, nhưng tham số phải là **`ObjectOutputStream`**, không phải `OutputStream`. Kết quả: đây chỉ là một method Java bình thường, **không phải serialization hook chuẩn**. Java có thể tiếp tục sử dụng serialization mặc định thay vì thực hiện logic bạn mong đợi.

Tương tự, `serialVersionUID` khai báo nhầm kiểu `int`, bỏ `static`/`final`, hoặc tùy biến `readObject` sai chữ ký có thể khiến runtime bỏ qua ý định của tác giả. Những lỗi này khó nhìn ra vì nhiều kiểu khai báo **vẫn hợp lệ với cú pháp Java** và đôi khi biên dịch thành công.

Từ **Java 14**, JDK có annotation `java.io.Serial` (viết `@Serial`) nhằm giúp **compiler nhận diện và cảnh báo các khai báo serialization đặc biệt sai cấu trúc hoặc không có tác dụng**. Đây là công cụ kiểm tra tại thời điểm biên dịch, tương tự tinh thần của `@Override` nhưng phục vụ một nhóm thành viên đặc biệt của Java Serialization.

Bài học thuộc **Java Core I/O → Tuần tự hóa nâng cao**, tập trung vào chính xác `@Serial`, các chữ ký được công nhận, cách bật lint và xử lý warning, hành vi với `Serializable`, `Externalizable`, enum và record, ví dụ Java 17+ có thể chạy, các tình huống lỗi và giới hạn bảo mật.

## 2. `@Serial` thực sự là gì?

Khai báo trong JDK:

```java
package java.io;

@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
public @interface Serial {}
```

Đây là **dạng mô tả cấu trúc annotation**, không phải đoạn mã để tự tạo trong project. Trong chương trình, chỉ import:

```java
import java.io.Serial;
```

Các điểm then chốt:

- `@Target({METHOD, FIELD})`: chỉ dành cho **method hoặc field** có vai trò serialization đặc biệt, không gắn lên class, constructor, parameter hay local variable như một tùy chọn chung.
- `@Retention(SOURCE)`: annotation tồn tại trong **source code** để compiler kiểm tra; bản thân annotation **không được giữ để runtime tìm bằng reflection**.
- **Không tạo ra thành viên đặc biệt**: `@Serial` không tự bổ sung `serialVersionUID`, không làm một class triển khai `Serializable`, không thực hiện `writeObject`/`readObject`.
- **Không đổi wire format**: việc thêm annotation lên chữ ký đang đúng không tự đổi byte trong serialization stream.
- **Không thay thế các quy tắc reflection của serialization runtime**: runtime vẫn nhận diện thành viên theo tên/chữ ký/ngữ cảnh được định nghĩa trong Java Object Serialization Specification.
- Compiler **được khuyến khích** cảnh báo khai báo gắn `@Serial` không đúng hoặc không có tác dụng; loại cảnh báo và thời điểm cảnh báo phụ thuộc compiler/JDK/thiết lập lint.

Đặc biệt, `@Serial` **không tương đương** `@Override`: `Serializable` là marker interface, không khai báo `writeObject` để lớp `override`. Việc viết `@Override` lên `private void writeObject(...)` không có cơ sở override thông thường và thường gây lỗi biên dịch; dùng `@Serial` mới đúng ý nghĩa.

## 3. Chính xác bảy thành viên serialization được đánh dấu

Theo Java SE 17 API `java.io.Serial`, có **năm loại method** và **hai loại field** đặc biệt:

| Thành viên | Chữ ký được tài liệu mô tả | Vai trò |
| --- | --- | --- |
| `serialVersionUID` | `private static final long serialVersionUID` | Định danh phiên bản serialization của class |
| `serialPersistentFields` | `private static final ObjectStreamField[] serialPersistentFields` | Khai báo tập field trong serial form khi tùy biến |
| `writeObject` | `private void writeObject(ObjectOutputStream out) throws IOException` | Tùy biến cách ghi state của một lớp `Serializable` |
| `readObject` | `private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException` | Tùy biến cách khôi phục state và validate dữ liệu |
| `readObjectNoData` | `private void readObjectNoData() throws ObjectStreamException` | Khởi tạo khi thiếu dữ liệu serialization cho lớp trong một số tình huống tiến hóa hierarchy |
| `writeReplace` | `Object writeReplace() throws ObjectStreamException` (access theo quy tắc tương ứng) | Thay đối tượng được ghi bằng một đối tượng thay thế |
| `readResolve` | `Object readResolve() throws ObjectStreamException` (access theo quy tắc tương ứng) | Thay đối tượng vừa được giải tuần tự hóa bằng giá trị thay thế |

Bảy dòng này mô tả **hợp đồng được tài liệu hóa**. Đối với `writeReplace` và `readResolve`, chữ ký return `Object` trong tài liệu biểu diễn vai trò kết quả; runtime và compiler có các quy tắc truy cập/kiểu trả về hợp lệ chi tiết hơn tùy JDK. Khi dạy và viết code, dùng chữ ký rõ ràng như bảng là an toàn, dễ rà soát.

Các chữ ký `throws` trong ví dụ là cách viết chuẩn để phản ánh exception có thể phát sinh. Trong ngôn ngữ Java, `throws` **không phải thành phần của JVM method descriptor**: một method không khai báo đúng danh sách `throws` chưa chắc bị runtime bỏ qua chỉ vì điều đó. Trọng tâm nhận diện là tên, số và kiểu tham số, return type/access và vị trí khai báo theo hợp đồng serialization.

### 3.1. Hai field

```java
@Serial
private static final long serialVersionUID = 1L;

@Serial
private static final ObjectStreamField[] serialPersistentFields = {
    new ObjectStreamField("id", long.class),
    new ObjectStreamField("name", String.class)
};
```

Đoạn trên là **khai báo bên trong một class `Serializable`** có import `java.io.Serial`, `java.io.ObjectStreamField`; chưa phải một ví dụ hoàn chỉnh có logic `writeObject`/`readObject`. Chỉ dùng `serialPersistentFields` khi thực sự thiết kế serial form riêng; với DTO thông thường, **không cần khai báo** field này.

Mẹo: `serialVersionUID` là **giá trị `long`**, không phải `int`; thêm hậu tố `L` giúp rõ ý. Khai báo `private` là thực hành được khuyến nghị vì UID của class không có ý nghĩa kế thừa như một API của superclass.

### 3.2. Năm method

```java
@Serial
private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
}

@Serial
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
}

@Serial
private void readObjectNoData() throws ObjectStreamException {
    throw new InvalidObjectException("Missing serialized class data");
}

@Serial
private Object writeReplace() throws ObjectStreamException {
    return this;
}

@Serial
private Object readResolve() throws ObjectStreamException {
    return this;
}
```

Đây là **tập các ví dụ chữ ký đặt bên trong class Serializable**, không phải lời khuyên gắn cả năm method vào mọi class: chỉ khai báo hook khi cần tính năng đó. Cần import `java.io.*`. Các hook có vai trò riêng và có thể thay đổi graph, identity hoặc định dạng dữ liệu; việc `return this` trong minh họa `writeReplace/readResolve` không tạo ra lợi ích thực tế.

**Hai phương thức private `writeObject`/`readObject` không được gọi trực tiếp từ code ứng dụng** như callback bình thường. Trong quá trình serialization/deserialization, runtime nhận diện chúng và gọi ở thời điểm được đặc tả. Bên trong `writeObject`, `defaultWriteObject()` chỉ hợp lệ khi đang thực hiện serialization hook tương ứng; `defaultReadObject()` có quy tắc tương tự.

## 4. `serialVersionUID`: một lỗi thường được `@Serial` giúp phát hiện

Một khai báo đúng:

```java
import java.io.Serial;
import java.io.Serializable;

public class OrderSnapshot implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long orderId;

    public OrderSnapshot(long orderId) {
        this.orderId = orderId;
    }
}
```

Nếu thay `long` thành `int`:

```java
@Serial
private static final int serialVersionUID = 1; // Sai kiểu
```

thì field có thể trở thành **một static field thường**, không phải UID được serialization runtime sử dụng. Nếu không có UID hợp lệ, runtime có thể tính UID mặc định dựa trên cấu trúc class — khác với ý định “UID là 1”.

Các lỗi khác: `private long serialVersionUID` thiếu `static final`, tên sai chữ hoa/thường như `serialVersionId`, hoặc khai báo UID trong lớp không serializable rồi nghĩ annotation biến nó thành metadata có hiệu lực.

**`@Serial` không tự định nghĩa UID**. Nó chỉ đánh dấu khai báo hiện có để compiler có cơ hội kiểm tra. `javac -Xlint:serial` còn có thể cảnh báo khi class serializable thiếu UID, dù class ấy chưa dùng `@Serial`.

Không tùy tiện đổi UID chỉ để dập `InvalidClassException`: UID là một phần kiểm tra class compatibility, không bảo đảm dữ liệu serialized ở hai phiên bản có cấu trúc và ngữ nghĩa tương thích.

## 5. `writeObject` và `readObject` phải khớp chữ ký

Hai khai báo đúng thông dụng:

```java
@Serial
private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    out.writeInt(42);
}

@Serial
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    int marker = in.readInt();
}
```

Chúng phải nằm trong class triển khai `Serializable` **và không dùng mô hình `Externalizable` tương ứng**. Mỗi hook xử lý state của lớp khai báo nó; `defaultWriteObject()`/`defaultReadObject()` ghi và khôi phục các field mặc định của chính lớp đó.

Hai lỗi rất dễ xuất hiện:

```java
@Serial
private void writeObject(OutputStream out) throws IOException {
    // Sai: tham số không phải ObjectOutputStream
}

@Serial
private void readObject(ObjectInputStream in, int version)
        throws IOException, ClassNotFoundException {
    // Sai: có thêm tham số int
}
```

Đây là **mã cố ý sai về hợp đồng serialization**; một class Java vẫn có thể biên dịch các method trên như method thường (có thể kèm warning), nhưng serialization engine **không coi chúng là hook đúng**. Dùng `@Serial` và bật `-Xlint:serial` để compiler hỗ trợ phát hiện sớm.

Chú ý trường hợp `protected void writeObject(ObjectOutputStream out)`: tính `private` của `writeObject`/`readObject` là yêu cầu có ý nghĩa. Cùng tên và tham số nhưng access khác không thay thế hook private trong giao thức mặc định của Serializable.

## 6. Ví dụ Java 17+ chạy độc lập, đã xác minh biên dịch

Ví dụ dưới đây dùng `@Serial` cho UID và ba hook có chữ ký đúng. Lớp `Profile` có field `name` được ghi theo cơ chế mặc định, còn `marker` được đánh dấu `transient` và ghi tường minh vào phần dữ liệu bổ sung. Hook `readObjectNoData` minh họa việc từ chối thiếu dữ liệu cho class, nhưng **không kích hoạt trong luồng ghi/đọc bình thường của demo**.

Tạo file `SerialAnnotationDemo.java`:

```java
import java.io.*;
import java.util.Objects;

public class SerialAnnotationDemo {
    private static class Profile implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String name;
        private transient int marker;

        Profile(String name) {
            this.name = Objects.requireNonNull(name);
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeInt(42);
        }

        @Serial
        private void readObject(ObjectInputStream in)
                throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            marker = in.readInt();
            if (name == null || name.isBlank()) {
                throw new InvalidObjectException("Invalid name");
            }
        }

        @Serial
        private void readObjectNoData() throws ObjectStreamException {
            throw new InvalidObjectException(
                    "No serialized profile data");
        }
    }

    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new Profile("Lan"));
        }

        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            Profile restored = (Profile) in.readObject();
            System.out.println("name=" + restored.name);
            System.out.println("marker=" + restored.marker);
        }
    }
}
```

Biên dịch và chạy (JDK 17+):

```bash
javac -Xlint:serial SerialAnnotationDemo.java
java SerialAnnotationDemo
```

Stdout:

```text
name=Lan
marker=42
```

**Giải thích:**

1. `Profile implements Serializable`; nếu thiếu interface này, `@Serial` không tự làm class serializable.
2. `@Serial private static final long serialVersionUID=1L` là UID hợp lệ.
3. `writeObject` được nhận diện, gọi `defaultWriteObject()` để ghi `name`. `marker` là transient, **không nằm trong phần field mặc định**.
4. `writeInt(42)` ghi thêm một giá trị theo định dạng do class định nghĩa, bên trong custom data của hook.
5. `readObject` dùng `defaultReadObject()`, rồi `readInt()` theo cùng thứ tự để gán `marker=42`. Nếu đổi thứ tự đọc hoặc bỏ phần dữ liệu tương ứng, stream có thể lỗi.
6. `InvalidObjectException` dùng để bảo vệ invariant `name`; không tin constructor `Profile(String)` sẽ chạy khi khôi phục một object Serializable thông thường.
7. `readObjectNoData` được đánh dấu đúng và làm nhiệm vụ riêng khi không có class data trong một số tình huống tiến hóa hierarchy; nó không được gọi mỗi khi `readObject()` trả về object.
8. Program thực hiện round-trip trong memory, nên không phụ thuộc đường dẫn file, bộ mã console cho tiếng Việt hoặc tốc độ hệ thống.

Đã xác minh mẫu biên dịch với `javac -Xlint:serial` và chạy cho đúng hai dòng stdout trên trong môi trường JDK hỗ trợ `@Serial`.

### 6.1. Có bắt buộc dùng dấu `@Serial` không?

**Không.** Trước Java 14, các thành viên serialization đặc biệt đã hoạt động mà không cần annotation, và trên Java 17 nó vẫn là annotation **khuyến nghị để kiểm tra**. Bỏ `@Serial` khỏi một hook có chữ ký đúng không tự tắt hook; thêm annotation vào hook sai không sửa được hook.

Với dự án chỉ hỗ trợ JDK 8 hoặc JDK 11, **`java.io.Serial` không tồn tại** trong các phiên bản đó. Không thêm import này khi đang biên dịch bằng release cũ; ví dụ `javac --release 11` không có API này. Mã dùng `@Serial` phù hợp với target Java 14+.

## 7. Thử khai báo sai và quan sát compiler warning

Ví dụ sai có chủ ý, tạo file `BadSerialDeclarations.java`:

```java
import java.io.*;

class BadSerialDeclarations implements Serializable {
    @Serial
    private static final int serialVersionUID = 1; // int, không phải long

    @Serial
    private void writeObject(OutputStream out) throws IOException {
        // Sai kiểu tham số: OutputStream thay vì ObjectOutputStream
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException {
        // Có thể là hook hợp lệ nếu những yếu tố nhận diện cần thiết đúng.
    }
}
```

Lệnh:

```bash
javac -Xlint:serial BadSerialDeclarations.java
```

Với `javac` có hỗ trợ phân tích serial annotation, sẽ có cảnh báo thuộc nhóm `[serial]` về `serialVersionUID` sai kiểu và `writeObject` nhận `OutputStream` thay cho `ObjectOutputStream`. **Văn bản thông báo và số lượng warning có thể khác theo JDK/compiler**, không nên hardcode các dòng message làm kết quả kiểm thử ổn định.

Đây là mã **cố ý sai về mục đích**, không dùng làm mẫu triển khai. Đặc biệt, `readObject` trong ví dụ chỉ khai báo `throws IOException`, không khai báo `ClassNotFoundException`; riêng điều đó không nhất thiết khiến hook bị vô hiệu, vì `throws` không phải phần của method descriptor. Tuy vậy, khi viết method thực tế, nên dùng chữ ký đã được tài liệu hóa đầy đủ và chuẩn bị xử lý `ClassNotFoundException` nếu cần gọi các API ném checked exception ấy.

Nếu muốn biến warning thành lỗi build trong CI:

```bash
javac -Xlint:serial -Werror BadSerialDeclarations.java
```

`-Werror` yêu cầu compilation thất bại khi compiler đã phát warning. Nó không tự tạo warning mới: phải bật lint thích hợp và dùng một compiler thực sự có kiểm tra tương ứng. Build tool Maven/Gradle có cách riêng để truyền `-Xlint:serial` và `-Werror` xuống `javac`.

## 8. Tại sao `@Serial` hữu ích hơn chỉ nhìn code?

Một số khai báo sai vẫn là Java hợp lệ:

| Khai báo | Compile Java thông thường? | Ý nghĩa serialization |
| --- | --- | --- |
| `private static final long serialVersionUID=1L` | Có | UID đúng định dạng |
| `private static final int serialVersionUID=1` | Có thể | **Không phải** UID đúng, compiler có thể cảnh báo |
| `private void writeObject(OutputStream x)` | Có thể | Không phải serialization hook |
| `public void writeObject(ObjectOutputStream x)` | Có thể | Không đúng hook private chuẩn |
| `private void readObject(ObjectInputStream x, int version)` | Có thể | Sai tham số, không phải hook |
| `private void readObject(ObjectInputStream x)` | Có thể | Có thể là hook hợp lệ xét theo tham số/access, cần xử lý exception đúng cách |
| `private static Object readResolve()` | Có thể | Không phải hook instance phù hợp |
| `private static final ObjectStreamField[] serialPersistentFields` | Có thể | Field định nghĩa serial form nếu ở class/ngữ cảnh hợp lệ |

Thông điệp quan trọng: **Java hợp lệ về cú pháp không đồng nghĩa được Java Object Serialization dùng như thành viên đặc biệt**. Annotation khiến mục đích của developer rõ hơn và giúp compiler phát hiện sai khác. Không gắn `@Serial` lên mọi method liên quan đến serialization theo nghĩa nghiệp vụ; nó chỉ dành cho nhóm thành viên đã được API quy định.

## 9. `serialPersistentFields` và trường hợp tùy biến serial form

`serialPersistentFields` là một array `ObjectStreamField[]` private static final dùng khai báo **field set của serial form**, không nhất thiết trùng trực tiếp với mọi field Java hiện có.

Ví dụ:

```java
import java.io.ObjectStreamField;
import java.io.Serial;
import java.io.Serializable;

class LegacyAccount implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    @Serial
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("accountId", long.class),
        new ObjectStreamField("displayName", String.class)
    };

    private long accountId;
    private String displayName;
    private transient String sessionHandle;
}
```

Ví dụ này là **class có thể biên dịch**; nếu muốn custom mapping phức tạp, phải thiết kế `writeObject`/`readObject` tương ứng với `ObjectOutputStream.PutField`/`ObjectInputStream.GetField` hoặc phương án phù hợp. `@Serial` **không tự kiểm chứng** rằng field list khớp schema nghiệp vụ hoặc dữ liệu phiên bản trước.

Một nhầm lẫn phổ biến là khai báo:

```java
@Serial
public static ObjectStreamField[] serialPersistentFields = ...;
```

Khi access/type/modifier sai, nó không đại diện cho field đặc biệt theo hợp đồng. Compiler hỗ trợ `@Serial` có thể cảnh báo nếu bạn đánh dấu như thế. Không nên sửa bằng cách bỏ annotation để tắt warning; phải sửa lại chữ ký hoặc thay thiết kế serial form.

## 10. `writeReplace` và `readResolve`: chú ý identity, substitution

Hai hook này không có tham số:

```java
@Serial
private Object writeReplace() throws ObjectStreamException {
    return this;
}

@Serial
private Object readResolve() throws ObjectStreamException {
    return this;
}
```

Đây là snippet để diễn giải chữ ký, **không phải bài học rằng luôn trả `this`**. Thực tế:

- `writeReplace`: trước khi ghi object, có thể thay bằng một object khác dùng làm proxy/đại diện serialized.
- `readResolve`: sau khi đọc một object, có thể thay kết quả bằng singleton, canonical object hoặc object bảo đảm invariant.
- Method có thể dùng modifier truy cập phù hợp, nhưng **khả năng kế thừa và truy cập của runtime** vẫn chịu quy tắc Java serialization. Trong ví dụ căn bản, dùng `private` nếu hook chỉ dành cho class hiện tại.
- Object thay thế phải tương thích với các tham chiếu dự kiến; thay type tùy tiện có thể gây `ClassCastException` hoặc phá graph.
- Những hook này có thể chạy code ứng dụng trong quá trình đọc dữ liệu và do đó cần lưu ý đặc biệt về **deserialization security**.

Một ví dụ pattern thường gặp là **serialization proxy**:

```text
Object domain -> writeReplace() -> Serializable Proxy
Serializable Proxy -> readResolve() -> Object domain hợp lệ
```

Proxy có thể giúp kiểm soát dạng lưu trữ và khôi phục invariant, nhưng cần code cẩn thận, tính tương thích và kiểm thử để tránh thay thế sai object. Annotation chỉ kiểm tra **hình thức khai báo**, không kiểm tra logic proxy.

## 11. `readObjectNoData`: khi nào có tác dụng?

`readObjectNoData()` không phải “`readObject()` trả EOF” và không phải một lời gọi tùy tiện mỗi khi field rỗng. Nó được dùng trong một số tình huống **class descriptor/hierarchy tiến hóa**, khi stream không chứa dữ liệu của một lớp local cần khởi tạo trong quá trình khôi phục object.

Một chữ ký đúng:

```java
@Serial
private void readObjectNoData() throws ObjectStreamException {
    throw new InvalidObjectException("No class data");
}
```

Nếu class phải thiết lập giá trị mặc định hợp lệ khi không có data, có thể thực hiện logic thích hợp thay vì ném. Nếu dữ liệu thiếu thể hiện bất thường hoặc tampering, từ chối rõ ràng thường dễ kiểm soát hơn.

Không dùng `readObjectNoData` thay cho kiểm tra EOF của `ObjectInputStream.readObject()`. Gọi `readObject()` khi stream đã kết thúc có thể ném `EOFException`; đây là hiện tượng khác.

## 12. Những ngữ cảnh mà `@Serial` có thể không có ý nghĩa

### 12.1. Class không triển khai `Serializable`

```java
class NonSerializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
```

Đây là mã **cố ý không hợp ngữ cảnh**. Annotation không khiến class được tuần tự hóa; compiler có thể cảnh báo vì field UID này không tham gia Java serialization. Nếu mục tiêu thật sự là serialize lớp, phải `implements Serializable` và thiết kế các field/runtime dependency phù hợp.

### 12.2. `Externalizable` khác `Serializable` mặc định

`Externalizable` **kế thừa `Serializable`** nhưng sử dụng các method public `writeExternal(ObjectOutput)`/`readExternal(ObjectInput)` để kiểm soát nội dung. Với class `Externalizable`:

- `@Serial` **không dành cho** `writeExternal`/`readExternal`: chúng không nằm trong danh sách bảy thành viên.
- Các hook `writeObject`, `readObject`, `readObjectNoData` của Serializable mặc định **không có tác dụng** theo cơ chế Externalizable.
- `serialPersistentFields` cũng **không được dùng** bởi mô hình Externalizable.
- `serialVersionUID` và một số cơ chế substitution có thể vẫn có vai trò theo giao thức; cần áp dụng đúng vào thành viên thật sự có ý nghĩa.
- Khả năng khôi phục Externalizable yêu cầu tuân thủ hợp đồng constructor public không đối số và API Externalizable; annotation không thay đổi yêu cầu đó.

Ví dụ sai về phạm vi:

```java
class ManualRecord implements java.io.Externalizable {
    @Serial
    private void writeObject(ObjectOutputStream out)
            throws IOException {
        // Không phải hook có tác dụng với Externalizable.
    }

    public ManualRecord() {}
    public void writeExternal(java.io.ObjectOutput out)
            throws IOException {}
    public void readExternal(java.io.ObjectInput in)
            throws IOException, ClassNotFoundException {}
}
```

Đây là **mã mô tả trường hợp sai**, cần import `java.io.*` để biên dịch; mục tiêu chỉ là cho thấy `writeObject` không thay thế `writeExternal`. Compiler có thể cảnh báo annotation không phù hợp với ngữ cảnh `Externalizable`.

### 12.3. Enum: serialization đặc biệt, không custom bằng các hook này

Enum mặc nhiên serializable nhưng serialization của enum lưu thông tin nhận diện constant theo quy tắc riêng, không tuần tự hóa instance fields như class thông thường. Hợp đồng Java quy định `serialVersionUID` của enum là `0L`. Các hook `writeObject`, `readObject`, `readObjectNoData`, `writeReplace`, `readResolve` do enum tự khai báo **bị bỏ qua** bởi enum serialization.

Vì vậy, đánh dấu `@Serial` lên UID hoặc các hook riêng bị bỏ qua trong enum là **sai ngữ cảnh**, dù một số khai báo vẫn biên dịch như method/field Java thường. Đừng sử dụng `@Serial` như cách tùy biến enum serialization.

### 12.4. Record: cần phân biệt hook bị bỏ qua và hook thay thế

Một `record` có thể `implements Serializable`, nhưng serialization của record xử lý **record components và canonical constructor** theo quy tắc riêng. Các `writeObject`, `readObject`, `readObjectNoData` và `serialPersistentFields` của record **không hoạt động như trong class thông thường**.

Theo Java Serialization Specification, record vẫn có thể sử dụng **`writeReplace`/`readResolve`** để thay thế object theo hợp đồng tương ứng. Record có serialVersionUID mặc định 0L nếu không khai báo, và quy tắc so khớp UID của record khác với class serializable thông thường.

Do đó, không nói đơn giản “mọi hook trong record đều được nhận” hoặc “mọi hook đều bị bỏ qua”. Khi đánh dấu `@Serial` ở record, phải xem thành viên đó có **thực sự có tác dụng** theo quy tắc riêng. Dùng các ví dụ/tài liệu serialization records cụ thể và compiler phù hợp để kiểm chứng.

## 13. Bật kiểm tra trong build/CI

Một lệnh thực hành để phát hiện vấn đề:

```bash
javac -Xlint:serial SerialAnnotationDemo.java
```

`-Xlint:serial` là nhóm cảnh báo về serialization; ngoài khai báo gắn `@Serial` sai, nó còn có thể giúp phát hiện class serializable **không khai báo UID** theo compiler/JDK.

Tùy quy định dự án, có thể dùng:

```bash
javac -Xlint:serial -Werror SerialAnnotationDemo.java
```

để biến những warning đã được phát thành lỗi build. Trong CI:

1. Chọn phiên bản `javac` và `--release` thống nhất với Java hỗ trợ.
2. Bật cảnh báo serial, xây dựng chính sách có giải thích cho các trường hợp ngoại lệ.
3. Với các class mới/đổi serialization hook, chạy unit test round-trip object và test đọc dữ liệu phiên bản cũ nếu có.
4. Đảm bảo không bỏ qua warning bằng cách gỡ `@Serial` mà không sửa logic.
5. Phân biệt warning khác biệt giữa JDK 17/21+; không ép **chính xác văn bản cảnh báo** làm một phần protocol nghiệp vụ.

**Cần nhớ:** `-Xlint:serial` không phải bộ phân tích toàn bộ rủi ro deserialization; nó giúp kiểm tra một nhóm khai báo và quy ước Java compiler. Mã đúng chữ ký vẫn có thể nguy hiểm hoặc sai logic.

## 14. Giới hạn của `@Serial`: đúng khai báo chưa chắc đúng chương trình

Annotation không bảo vệ các lỗi:

- Gọi `out.writeInt(a)`, `out.writeUTF(b)` nhưng phía `readObject` đọc ngược thứ tự.
- Quên `defaultWriteObject()` trong khi phía `defaultReadObject()` mong có field mặc định, khiến dữ liệu không khớp.
- Ghi thêm field nhạy cảm bằng custom `writeObject()` rồi tưởng nó được ẩn chỉ vì khai báo `transient`.
- Dùng `readResolve()` trả object sai type hoặc làm sai tính duy nhất của singleton.
- Không validate invariant sau `defaultReadObject()`.
- Giữ `serialVersionUID` cố định trong khi thay đổi serialized form một cách không tương thích.
- Dùng `ObjectInputStream` để deserialize byte từ nguồn không đáng tin cậy, dù tất cả hook đều mang annotation đúng.

Đây là **tooling ở thời điểm viết/biên dịch**, không phải sandbox, serialization firewall, kiểm tra schema, xác thực nguồn hoặc cơ chế chống lỗi runtime.

## 15. Bảo mật và `ObjectInputFilter`

Nếu có thể, **không dùng native Java deserialization với dữ liệu untrusted**. Quá trình `ObjectInputStream.readObject()` có thể kích hoạt code của các class trong graph, bao gồm custom `readObject`, `readResolve` hoặc `Externalizable.readExternal`, **trước khi caller có cơ hội cast/validate object kết quả**.

Trong hệ thống legacy bắt buộc đọc Java Object Serialization, cần chính sách nhiều lớp:

- Xác thực và kiểm tra integrity nguồn byte.
- Chỉ cho phép danh sách class cần thiết, dùng `ObjectInputFilter` trước lời gọi `readObject`.
- Giới hạn depth, số references, kích thước array, tổng byte, timeout, số request đồng thời.
- Kiểm toán gadget trên classpath và cập nhật thư viện.
- Cô lập tác vụ xử lý nếu mô hình rủi ro yêu cầu.
- Validate business invariants trong `readObject` hoặc lớp đối tượng thích hợp, nhưng không xem validation sau đọc là đủ để chặn exploit.

Ví dụ minh họa cơ chế filter:

```java
ObjectInputFilter filter =
        ObjectInputFilter.Config.createFilter(
            "maxdepth=10;maxrefs=100;maxbytes=1048576;"
          + "maxarray=10000;com.example.dto.*;!*");
in.setObjectInputFilter(filter);
```

Đây là **snippet**, cần `import java.io.ObjectInputFilter;` và `ObjectInputStream in` đã được khởi tạo. Thay package `com.example.dto.*` bằng các lớp thật cần được phép, và kiểm thử dữ liệu hợp lệ; nếu allowlist quá hẹp, việc đọc có thể bị từ chối. Bộ lọc không thay thế lựa chọn một định dạng an toàn hơn, cũng không bảo đảm mọi side effect đều biến mất.

**`@Serial` không can thiệp vào bộ lọc**. Một hook `@Serial` được khai báo hoàn hảo vẫn có thể thực hiện logic gây hại nếu giải tuần tự hóa dữ liệu độc hại.

## 16. Tình huống sửa lỗi theo thứ tự hợp lý

Giả sử bạn khai báo:

```java
@Serial
private void readObject(java.io.DataInputStream in)
        throws java.io.IOException {
    // ...
}
```

Đây là method Java thường, **không phải hook `readObject` của Serializable**. Khi thấy kết quả deserialize không chạy đoạn code kỳ vọng:

1. Kiểm tra class có thực sự `implements Serializable` hay không.
2. Kiểm tra đây là `Serializable` mặc định hay `Externalizable`, enum hoặc record.
3. Đối chiếu chữ ký hook chuẩn: `ObjectInputStream`, `private void`, không thêm tham số.
4. Bật `javac -Xlint:serial` và kiểm tra tất cả warning.
5. Dùng test round-trip có assertion rằng hook thực sự chạy; đừng chỉ kiểm tra compile.
6. Kiểm tra reader/writer field order và cơ chế `defaultWriteObject/defaultReadObject`.
7. Kiểm tra serialVersionUID và bản dữ liệu cũ nếu có versioning.
8. Chỉ sau khi hợp đồng khai báo đúng mới xem logic serialization và security policy.

Không nên “chữa” lỗi bằng cách thêm `@Serial` vào method sai rồi coi việc biên dịch là đủ.

## 17. Bài tập và lời giải

**Câu 1.** `@Serial` có khiến `class Account` tự triển khai `Serializable` không?

**Đáp án:** Không. Annotation kiểm tra các khai báo đặc biệt; class vẫn phải nằm trong ngữ cảnh serialization hợp lệ, thường `implements Serializable`.

**Câu 2.** Annotation `@Serial` có còn trong classfile để runtime tìm bằng reflection không?

**Đáp án:** Bản thân annotation có `RetentionPolicy.SOURCE`, nên không được giữ làm annotation runtime; serialization runtime nhận diện thành viên theo quy tắc riêng.

**Câu 3.** `@Serial private static final int serialVersionUID=1` có đúng không?

**Đáp án:** Sai kiểu. UID đúng là `long` và các modifier thích hợp, ví dụ `@Serial private static final long serialVersionUID=1L;`.

**Câu 4.** `@Serial private void writeObject(OutputStream out)` được Object Serialization gọi như hook không?

**Đáp án:** Không; tham số phải là `ObjectOutputStream`. Đây là lý do phải bật compiler check.

**Câu 5.** Nếu class `Serializable` có `private void readObject(ObjectInputStream in) throws IOException` mà không có `ClassNotFoundException`, nó có chắc bị bỏ qua không?

**Đáp án:** Không. `throws` không thuộc JVM descriptor. Tuy nhiên tài liệu mô tả chữ ký chuẩn bao gồm hai exception; trong code thực tế nên dùng chữ ký chuẩn và chỉ dùng API có checked exception theo cách Java cho phép.

**Câu 6.** Có thể gắn `@Serial` lên `writeExternal` của `Externalizable` không?

**Đáp án:** Không đúng mục đích; `writeExternal` không thuộc nhóm năm hook được `@Serial` định nghĩa. `Externalizable` dùng cơ chế riêng.

**Câu 7.** Enum có thể tùy chỉnh serialization bằng `@Serial private Object readResolve()` không?

**Đáp án:** Không. Các serialization hooks do enum khai báo bị cơ chế serialization enum bỏ qua; annotation không thay đổi điều đó.

**Câu 8.** Record `Serializable` có được dùng custom `readObject()` để đọc component như class bình thường không?

**Đáp án:** Không; serialization record có quy tắc riêng, custom `readObject` bị bỏ qua. Nhưng `writeReplace`/`readResolve` vẫn có thể có tác dụng theo specification.

**Câu 9.** Vì sao chỉ khai báo `serialVersionUID` và thêm `@Serial` không đủ bảo đảm dữ liệu cũ đọc được?

**Đáp án:** UID chỉ tham gia compatibility check; field types, class hierarchy, hooks, serialized form và invariants vẫn có thể thay đổi không tương thích.

**Câu 10.** Compiler cảnh báo `@Serial` sai ngữ cảnh. Có nên chỉ xóa annotation để yên warning không?

**Đáp án:** Không. Hãy xác định thành viên đó có thật sự được runtime dùng không; sửa chữ ký/ngữ cảnh nếu muốn dùng hook hoặc bỏ hook không cần thiết.

**Câu 11.** `-Werror` có tự phát hiện mọi lỗi serialization không?

**Đáp án:** Không. Nó chỉ chuyển warning compiler đã phát thành build error; unit test, versioning test và security review vẫn cần thiết.

**Câu 12.** Hook `readResolve` mang `@Serial` đúng có an toàn khi deserialize untrusted input không?

**Đáp án:** Không thể kết luận. Annotation chỉ hỗ trợ kiểm tra khai báo; logic hook, object graph và nguồn byte vẫn có thể gây rủi ro.

## 18. Checklist khi rà soát code serialization

1. Mỗi class `Serializable` có thực sự cần serialized form không? Nếu không, chọn DTO/định dạng khác.
2. UID khai báo bằng `private static final long` với `@Serial` và giá trị được quản lý theo compatibility policy.
3. Hook `writeObject/readObject/readObjectNoData` có tên, access, return type và tham số chính xác không?
4. `serialPersistentFields` có đúng kiểu `ObjectStreamField[]`, đúng modifier và thực sự cần custom serial form không?
5. `writeReplace/readResolve` có truy cập, kiểu kết quả, substitution và tính kế thừa hợp lệ không?
6. Có gắn `@Serial` lên method/field không có tác dụng trong `Externalizable`, enum hoặc record không?
7. Build bật `-Xlint:serial` và quản lý warning thay vì bỏ qua?
8. Unit test round-trip, class versioning, edge cases và dữ liệu sai có tồn tại không?
9. Custom hooks có giữ invariant, field order và exception semantics đúng không?
10. Không giải tuần tự hóa đầu vào không tin cậy; nếu bắt buộc, filter/quota/isolation được áp dụng **trước** lần đọc?
11. Code chạy ở phiên bản Java có `java.io.Serial` (14+) không?
12. Có tài liệu về lý do mỗi custom member tồn tại, để những lần refactor không âm thầm vô hiệu hóa hook?

## Tài liệu tham khảo chính thức

- Java SE 17 — `java.io.Serial`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serial.html
- Java SE 17 — `java.io.Serializable`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serializable.html
- Java SE 17 — `ObjectOutputStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectOutputStream.html
- Java SE 17 — `ObjectInputStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html
- Java SE 17 — `ObjectStreamField`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectStreamField.html
- Java Object Serialization Specification, System Architecture (bao gồm enum/records): https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/serial-arch.html
- Java Object Serialization Specification, Object Input Classes: https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/input.html
- `javac` compiler tool và lint options: https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-serialization-009">Kiểm tra bất biến sau khi khôi phục đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra bất biến sau khi khôi phục đối tượng trong Java Serialization

## 1. Tại sao phải kiểm tra bất biến *sau* deserialization?

Một **bất biến (invariant)** là điều kiện luôn phải đúng với một đối tượng hợp lệ trong suốt vòng đời sử dụng. Ví dụ:

- Khoảng số phải thỏa `0 <= start <= end`.
- Tài khoản không thể có mã định danh `null` hoặc rỗng.
- Danh sách thành viên phải có số phần tử bằng `expectedCount`; không có phần tử `null`.
- Một lịch hẹn phải có `startTime` trước `endTime`.
- Một graph gồm nhiều đối tượng phải giữ đúng quan hệ giữa cha, con và các tham chiếu ngược.
- Collection nội bộ không được bị sửa từ bên ngoài nếu hợp đồng class yêu cầu bất biến.

**Serialization** biến trạng thái đối tượng thành byte stream. **Deserialization** qua `ObjectInputStream.readObject()` dựng lại đối tượng từ stream đó. Điểm dễ bỏ sót: với class chỉ triển khai `Serializable`, việc khôi phục thông thường **không gọi constructor của chính class Serializable**, nên kiểm tra bất biến chỉ có ở constructor có thể bị bỏ qua.

Ví dụ, constructor bảo đảm `start <= end` nhưng một stream cũ, file hỏng hoặc stream cố tình sửa có thể mang `start=20` và `end=10`. Nếu ứng dụng chấp nhận dữ liệu mà không kiểm tra, nó sẽ đưa **đối tượng có trạng thái không hợp lệ** vào logic hệ thống.

Đây là lý do Java cung cấp các điểm kiểm tra như `private readObject(...)`, `ObjectInputValidation.validateObject()`, `readObjectNoData()`, cùng các chiến lược cấu trúc như **serialization proxy** và **serializable record**.

Mục tiêu của bài: biết **bất biến nào cần kiểm tra, kiểm tra khi nào, ném lỗi gì, và vì sao một số kiểm tra cần chờ toàn bộ object graph khôi phục xong**.

## 2. Deserialization diễn ra như thế nào?

Với class Serializable thông thường:

~~~text
ObjectInputStream.readObject()
            |
            v
Đọc class descriptor / trạng thái trong stream
            |
            v
Tạo đối tượng để khôi phục (KHÔNG gọi constructor Serializable class)
            |
            v
Khôi phục các field; nếu có private readObject,
thực thi readObject và defaultReadObject/readFields nếu dùng
            |
            v
Hoàn tất đồ thị đối tượng đang được đọc
            |
            v
Thực hiện ObjectInputValidation đã đăng ký (nếu có)
            |
            v
Trả đối tượng hợp lệ cho caller hoặc ném exception
~~~

Sơ đồ đơn giản hóa, không thể hiện mọi chi tiết handle, back-reference, `readResolve`, inheritance và exception. Nhưng nó nhấn mạnh:

1. **Constructor là cổng kiểm tra cho đối tượng tạo bằng `new`, chưa đủ cho deserialization Serializable**.
2. `defaultReadObject()` phục hồi field non-static/non-transient theo cơ chế mặc định; việc đó **không tự gọi validator tùy biến của class**.
3. `readObject()` là hook để kiểm tra **trạng thái cục bộ ngay sau khi field của class được khôi phục**.
4. `ObjectInputValidation` là hook bổ sung khi kiểm tra cần **đợi toàn bộ graph đã khôi phục** trước khi `readObject()` cấp cao trả về.
5. Nếu kiểm tra thất bại, nên **từ chối stream bằng `InvalidObjectException`** thay vì cố âm thầm dùng object sai.

### Vì sao constructor của Serializable không được gọi?

Java Object Serialization có cơ chế dựng lại field từ stream. Constructor no-arg của **superclass đầu tiên không triển khai Serializable** có thể được gọi theo quy tắc serialization, nhưng constructor của class Serializable sẽ không được chạy như khi gọi `new` thông thường.

Do đó các câu lệnh sau:

~~~java
public Range(int start, int end) {
    if (start > end) throw new IllegalArgumentException();
    this.start = start;
    this.end = end;
}
~~~

là **mã minh họa**, chỉ bảo vệ đường tạo object qua constructor. Nó không đủ chứng minh mọi object `Range` do `ObjectInputStream.readObject()` trả về đều hợp lệ.

**Ngoại lệ quan trọng:** Serializable **record** trong Java 17 được khôi phục theo cơ chế đặc thù có gọi **canonical constructor**; xem mục 10. Không nên áp dụng máy móc quy tắc constructor thông thường của Serializable class cho record.

## 3. Xác định bất biến trước khi chọn hook

Có thể chia điều kiện cần kiểm tra thành hai nhóm.

| Nhóm | Ví dụ | Vị trí kiểm tra phù hợp |
|---|---|---|
| **Bất biến cục bộ** của một đối tượng | `start >= 0` và `start <= end`, ID khác `null` | Trong `readObject` ngay sau `defaultReadObject` |
| **Bất biến toàn graph / liên đối tượng** | Parent-child links nhất quán, số lượng member bằng tổng dữ liệu sau khôi phục | `ObjectInputValidation` đăng ký từ `readObject` |
| **Đầu vào tùy biến theo định dạng** | Hai field được ghi riêng trong `writeObject` | Trong `readObject` sau khi đọc đủ custom data |
| **Luồng cũ/thiếu dữ liệu cho class** | Class được thêm vào hierarchy nhưng stream không có data cho nó | `readObjectNoData`, khôi phục trạng thái an toàn hoặc từ chối |
| **Đối tượng bất biến có constructor nghiêm ngặt** | Value object với field final, defensive copy | Serialization proxy hoặc record, tùy hợp đồng |

**Phạm vi bất biến** quyết định thời điểm kiểm tra:

- Không được dùng validator toàn graph để thay thế mọi **kiểm tra cục bộ sớm**, nếu trạng thái cục bộ nguy hiểm có thể bị dùng trong lúc graph còn đang dựng.
- Không được áp dụng một kiểm tra quan hệ liên đối tượng **quá sớm** nếu đối tượng liên quan vẫn chưa khôi phục hết hoặc có cycle/back-reference.
- Cả hai loại đều cần tránh phát sinh **side effect không thể hoàn tác** trong lúc object còn chưa được chứng thực hợp lệ.

## 4. Hook private readObject: kiểm tra field ngay sau khôi phục

Một class Serializable có thể định nghĩa phương thức đúng chữ ký:

~~~java
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    // Kiểm tra các field vừa khôi phục.
}
~~~

Các lưu ý thiết yếu:

- Phương thức phải **private**, **non-static**, trả `void` và nhận `ObjectInputStream`. Không phải override public method từ interface.
- `in.defaultReadObject()` nạp các field có thể serialize của class theo tên. Nếu class có format custom, `readObject()` có thể dùng `readFields()` hoặc đọc các giá trị tùy biến theo đúng thứ tự được ghi.
- Trong `readObject()` tùy biến, **không gọi `defaultReadObject()` và `readFields()` để đọc cùng dữ liệu hai lần**.
- Kiểm tra sau khi đã có đủ dữ liệu cần thiết; nếu sai, ném `InvalidObjectException`.
- Không nuốt `IOException`/`ClassNotFoundException` và trả về object có trạng thái chưa rõ.

### Ví dụ bất biến: khoảng số không âm

~~~java
final class Range implements Serializable {
    private static final long serialVersionUID = 1L;

    private int start;
    private int end;

    Range(int start, int end) {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid range");
        }
        this.start = start;
        this.end = end;
    }

    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        if (start < 0 || end < start) {
            throw new InvalidObjectException("Invalid range");
        }
    }
}
~~~

Đây là **mã minh họa ở mức class**, cần các import `java.io.*`. Constructor bảo vệ khi tạo bằng `new`, còn `readObject` bảo vệ khi khôi phục field trực tiếp từ stream.

Tại sao `InvalidObjectException` tốt hơn `IllegalArgumentException`? Vì nó là loại `ObjectStreamException`/`IOException` chuyên dùng khi đối tượng khôi phục **không đạt kiểm tra hợp lệ**, giúp caller xác định lỗi deserialization một cách rõ ràng.

**Không sửa âm thầm `end = start` chỉ để che dữ liệu hỏng** nếu contract không cho phép điều đó. Nếu bài toán thực sự có chính sách migrate/normalize dữ liệu cũ, cần quy tắc chuyển đổi tường minh, không được biến mọi invalid stream thành một object trông có vẻ hợp lệ.

## 5. Vì sao ObjectInputValidation cần thiết cho object graph?

Trong các graph có **tham chiếu vòng/cross-reference**, việc đọc field của một object chưa có nghĩa mọi object được tham chiếu đã hoàn toàn thiết lập trạng thái. Có thể có child trỏ về parent, hoặc nhiều phần cùng tham chiếu một object trong lúc nó còn đang deserialized.

Đối với quan hệ như `parent.children.contains(child)` và `child.parent == parent`, kiểm tra trong `parent.readObject()` có thể quá sớm trong một số kiểu graph, hoặc gây những hoạt động phụ nguy hiểm.

Java cung cấp:

~~~java
public interface ObjectInputValidation {
    void validateObject() throws InvalidObjectException;
}
~~~

và `ObjectInputStream.registerValidation(ObjectInputValidation obj, int priority)` để đăng ký callback **trong readObject của một class khi stream đang đọc object**. Callback sẽ chạy **sau khi object graph được khôi phục**, nhưng **trước khi đối tượng được trả cho caller** của lời đọc tương ứng.

Cách dùng khái niệm:

~~~java
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    in.registerValidation(this, 0);
}

@Override
public void validateObject() throws InvalidObjectException {
    if (!graphRelationshipIsValid()) {
        throw new InvalidObjectException("Inconsistent object graph");
    }
}
~~~

Đây là **mã minh họa**: class phải implements `Serializable, ObjectInputValidation`; phương thức `graphRelationshipIsValid()` là logic domain cần triển khai, không phải API JDK.

### Priority của validation

`registerValidation(obj, priority)` nhận số nguyên ưu tiên:

- **Priority lớn hơn chạy trước** priority nhỏ hơn.
- Với **cùng priority**, thứ tự callback **không xác định**.
- Dùng `0` khi không có yêu cầu thứ tự.
- Callback có thể ném `InvalidObjectException`; khi kiểm tra không đạt, việc đọc object sẽ thất bại.

**Không thiết kế bất biến dựa vào thứ tự callback trong cùng priority**. Trong một callback, kiểm tra tất cả điều kiện mà mình chịu trách nhiệm; không giả định callback khác đã sửa chữa dữ liệu trước đó nếu không có dependency có chủ đích.

### Khi nào được registerValidation?

Chỉ đăng ký đúng khi `ObjectInputStream` đang trong giai đoạn deserialization phù hợp (thông thường từ `readObject` của class). Gọi `registerValidation` ngoài ngữ cảnh đó có thể ném `NotActiveException`. Nếu đối tượng callback `null`, Javadoc quy định `InvalidObjectException`.

**Không tự gọi `validateObject()` của các class khác trong lúc graph đang dựng** để "kiểm tra sớm" khi chưa biết trạng thái của các tham chiếu liên quan.

## 6. Chương trình Java 17+ hoàn chỉnh: hai cách từ chối object sai

Ví dụ này kiểm thử:

1. `Range` kiểm tra cục bộ trong `readObject`.
2. `Roster` kiểm tra trạng thái sau khi toàn graph được restored qua `ObjectInputValidation`.
3. Cả object hợp lệ và object có trạng thái hỏng sẽ được serialize thành byte[] rồi thử deserialization.
4. Các câu lệnh cố ý làm hỏng field chỉ nằm trong **outer demo class** để tạo test data; đó không phải phương pháp sửa state trong production.

**Tệp: `InvariantDemo.java`**

~~~java
import java.io.*;
import java.util.*;

public class InvariantDemo {
    static final class Range implements Serializable {
        private static final long serialVersionUID = 1L;
        private int start;
        private int end;

        Range(int start, int end) {
            if (start < 0 || end < start) {
                throw new IllegalArgumentException("Invalid range");
            }
            this.start = start;
            this.end = end;
        }

        private void readObject(ObjectInputStream in)
                throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            if (start < 0 || end < start) {
                throw new InvalidObjectException("Invalid range");
            }
        }

        @Override
        public String toString() {
            return start + ".." + end;
        }
    }

    static final class Roster
            implements Serializable, ObjectInputValidation {
        private static final long serialVersionUID = 1L;
        private List<String> names;
        private int expectedCount;

        Roster(List<String> names) {
            this.names = new ArrayList<>(names);
            this.expectedCount = names.size();
        }

        private void readObject(ObjectInputStream in)
                throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            in.registerValidation(this, 0);
        }

        @Override
        public void validateObject() throws InvalidObjectException {
            if (names == null || names.size() != expectedCount) {
                throw new InvalidObjectException("Invalid roster count");
            }
            for (String name : names) {
                if (name == null || name.isBlank()) {
                    throw new InvalidObjectException("Invalid roster member");
                }
            }
            names = List.copyOf(names);
        }

        @Override
        public String toString() {
            return names.toString();
        }
    }

    static byte[] save(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        return bytes.toByteArray();
    }

    static Object load(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    public static void main(String[] args) throws Exception {
        Range good = (Range) load(save(new Range(4, 9)));
        System.out.println("range=" + good);

        Range broken = new Range(4, 9);
        broken.end = 1; // Chỉ outer test cố ý phá private state.
        try {
            load(save(broken));
            System.out.println("rangeRejected=false");
        } catch (InvalidObjectException expected) {
            System.out.println("rangeRejected=true");
        }

        Roster roster = (Roster) load(
                save(new Roster(List.of("Ada", "Bob"))));
        System.out.println("roster=" + roster);

        Roster badRoster = new Roster(List.of("Ada", "Bob"));
        badRoster.expectedCount = 3; // Test-only malformed state.
        try {
            load(save(badRoster));
            System.out.println("rosterRejected=false");
        } catch (InvalidObjectException expected) {
            System.out.println("rosterRejected=true");
        }
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac InvariantDemo.java
java InvariantDemo
~~~

**Stdout đã kiểm tra trên JDK:**

~~~text
range=4..9
rangeRejected=true
roster=[Ada, Bob]
rosterRejected=true
~~~

Giải thích:

- `new Range(4,9)` hợp lệ; deserialization chạy `Range.readObject` và giữ bất biến nên in `4..9`.
- `broken.end = 1` chỉ có thể thực hiện vì `Range` là nested class của `InvariantDemo`; enclosing class được quyền truy cập private field của nested class. Đây là cách **cố tình tạo serialized fixture không hợp lệ trong bài test**, không phải field setter trong API production. Constructor ban đầu vẫn kiểm tra hợp lệ, nhưng serialized bytes chứa state đã bị thay đổi.
- Khi `load(save(broken))`, `defaultReadObject` nạp `start=4`, `end=1` và validator ném `InvalidObjectException`; kết quả `rangeRejected=true`.
- `Roster` hợp lệ có 2 tên và `expectedCount=2`. Khi deserialization hoàn tất graph, `validateObject` kiểm tra count và từng tên, rồi dùng `List.copyOf` để có danh sách không thể sửa qua API List.
- `badRoster.expectedCount=3` khiến dữ liệu serializable không phù hợp với danh sách, do đó callback từ chối và in `rosterRejected=true`.

**Lưu ý về chất lượng demo:** Trong ví dụ, constructor `Roster` chỉ sao chép danh sách mà chưa kiểm tra từng tên không null/blank, để giữ trọng tâm hiển thị callback sau deserialization. **Một class production phải kiểm tra bất biến ở tất cả đường tạo object**, gồm constructor, factory, setter hoặc mutation API, và đường deserialization. Có thể tái sử dụng một hàm validation domain chung thay vì lặp quy tắc phân tán.

### Vì sao không cần sửa từng byte serialized stream để thử invalid?

Unit test muốn kiểm tra invariant phải cung cấp stream chứa state xấu. Cách làm hỏng private field ở một fixture **cùng nest** như demo dễ kiểm soát hơn việc sửa byte bằng magic offset trong serialized binary. Không dùng offset cố định giả định format Java Serialization của một class luôn giống nhau giữa các phiên bản.

## 7. Kiểm tra null, phạm vi, collection và object liên kết

Các nhóm bất biến thường cần kiểm tra:

| Đối tượng | Bất biến | Lỗi có thể có từ stream |
|---|---|---|
| `Range` | `start >= 0`, `end >= start` | Start âm, end nhỏ hơn start |
| `Account` | `accountId != null`, non-blank | Null hoặc empty ID |
| `Order` | `items != null`, item hợp lệ | Missing field hoặc null list |
| `Roster` | `expectedCount == names.size()` | Count không khớp |
| `Schedule` | `start.isBefore(end)` | Thời gian đảo ngược hoặc null |
| `GraphNode` | Quan hệ liên kết parent-child khớp | Back-reference không nhất quán |
| `CacheConfig` | Timeout dương, memory budget trong khoảng | Field âm hoặc quá lớn |

Không đủ nếu chỉ kiểm tra null của container. Phải kiểm tra **từng phần tử** và những ràng buộc liên quan đến tổng số lượng/uniqueness:

~~~java
if (members == null) {
    throw new InvalidObjectException("members missing");
}
for (Member member : members) {
    if (member == null || member.id() == null) {
        throw new InvalidObjectException("Invalid member");
    }
}
~~~

Đây là mã minh họa: `Member` là type ứng dụng tự định nghĩa có phương thức `id()`, không phải một class JDK.

### Kiểm tra số học tránh overflow

Ví dụ bất biến tổng tiền không âm:

~~~java
long total;
try {
    total = Math.addExact(unitPrice, tax);
} catch (ArithmeticException overflow) {
    throw new InvalidObjectException("Total overflow");
}
if (unitPrice < 0 || tax < 0 || total > maxAllowed) {
    throw new InvalidObjectException("Invalid total");
}
~~~

Các biến long là dữ liệu đã có từ object; đoạn code chỉ minh họa. Không tính `int sum = a + b` rồi kiểm tra `sum >= 0` mà không nghĩ tới **integer overflow**. Với dữ liệu do stream cung cấp, kích thước mảng, độ dài danh sách và số lượng nested objects cũng cần giới hạn ở nhiều lớp.

### Không tin trường derived hoặc cached state

Nếu một class serialize `items` và cả `cachedTotal`, stream có thể cung cấp `cachedTotal` không khớp với items. Hai hướng:

- **Không serialize field derived:** đánh dấu `transient` và **tái tạo** nó từ dữ liệu nguồn sau khi khôi phục.
- Nếu metadata cache cần lưu, **đối chiếu lại** với dữ liệu thực trước khi dùng hoặc bỏ cache rồi recompute theo chính sách.

Không dùng `cachedTotal` chưa xác thực để cấp phát memory, tính phí hoặc cấp quyền.

## 8. Khi graph có cycle và aliasing

Một đối tượng có thể tham chiếu lặp cùng instance ở nhiều vị trí, và các đối tượng có thể có vòng liên kết:

~~~text
Parent P ------> Child C
     ^                |
     |                |
     +----------------+
      C.parent == P
~~~

Trong `P.readObject`, `C` có thể chưa được xây dựng đầy đủ ở mọi thời điểm mà code tùy biến chạy, nhất là khi graph có cycle/back-reference. Vì vậy kiểm tra `C.parent == P` hoặc `P.children.contains(C)` thường phù hợp với **`ObjectInputValidation` sau graph hoàn tất** hơn là eager callback quá sớm.

Ví dụ pseudocode:

~~~java
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    in.registerValidation(this, 0);
}

@Override
public void validateObject() throws InvalidObjectException {
    if (children == null) {
        throw new InvalidObjectException("Missing children");
    }
    for (Node child : children) {
        if (child == null || child.parent != this) {
            throw new InvalidObjectException("Broken bidirectional link");
        }
    }
}
~~~

Đây là **pseudocode không độc lập**: `Node`, `children` và field `parent` thuộc mô hình riêng của ứng dụng. Nếu dùng `List.copyOf(children)`, phải cẩn thận aliasing và tránh biến mutable object graph sang dạng không nhất quán; một bản sao list không tự clone các `Node` bên trong.

### Không nên gọi phương thức overridable của this khi đang deserialize

Trong `readObject`, object có thể ở trạng thái chưa hoàn tất. Nếu gọi method có thể override, subclass có thể chạy trên field chưa khởi tạo và làm rò rỉ tham chiếu `this`. Nên dùng validation nội bộ, private/final methods với tác động hạn chế và tránh đăng ký `this` vào global registry, dispatch event, khởi chạy thread hoặc gọi external callbacks trước khi hợp lệ.

## 9. Dữ liệu thiếu khi class tiến hóa: readFields và readObjectNoData

Serialized data có thể được tạo bởi một phiên bản class cũ. Trong Java Serialization, `serialVersionUID` hỗ trợ nhận diện phiên bản tương thích, **không tự bảo đảm bất biến domain**.

### 9.1. Field mới không có trong stream

Nếu phiên bản mới thêm `requiredName` nhưng stream cũ không có field, cách đọc mặc định có thể đặt field tham chiếu thành `null`, primitive thành 0/false. Chương trình phải quyết định có được migrate hợp lệ hay không.

Thay vì `defaultReadObject()`, có thể dùng `ObjectInputStream.GetField`:

~~~java
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    ObjectInputStream.GetField fields = in.readFields();
    if (fields.defaulted("name")) {
        throw new InvalidObjectException("Required name missing");
    }

    String name = (String) fields.get("name", null);
    if (name == null || name.isBlank()) {
        throw new InvalidObjectException("Invalid name");
    }
    this.name = name;
}
~~~

Đoạn này minh họa một class Serializable có field `String name`; `readFields()` và `defaultReadObject()` là **hai cách thay thế**, không gọi cả hai để nạp cùng một dữ liệu. Dùng `defaulted("name")` khi cần phân biệt field thực sự vắng mặt khỏi field có giá trị mặc định. `serialVersionUID` vẫn phải phù hợp chính sách tương thích.

### 9.2. readObjectNoData()

Một class Serializable có thể khai báo:

~~~java
private void readObjectNoData() throws ObjectStreamException {
    throw new InvalidObjectException(
            "Serialized stream lacks required class data");
}
~~~

Hook được dùng trong tình huống stream **không có dữ liệu tương ứng cho class trong hierarchy** (ví dụ thay đổi hierarchy tương thích, hoặc nguồn không hoàn chỉnh/bất thường), khác với việc một field đơn lẻ của class bị thiếu.

Một số class có thể tự tái lập giá trị mặc định hợp lệ tại hook này; nếu không thể đảm bảo bất biến, **từ chối** là lựa chọn đúng. Không tự phỏng đoán giá trị của thông tin bảo mật, giá tiền, quyền truy cập hoặc định danh bị thiếu.

## 10. Object bất biến: defensive copy, serialization proxy và record

### 10.1. Không để dữ liệu mutable phá bất biến về sau

Kiểm tra `List` trong validator là một bước; nhưng nếu object giữ một tham chiếu đến collection mutable có thể bị phần khác sửa, bất biến sẽ bị phá sau khi deserialize.

Ví dụ:

~~~java
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    if (names == null || names.stream().anyMatch(
            n -> n == null || n.isBlank())) {
        throw new InvalidObjectException("Invalid names");
    }
    names = List.copyOf(names);
}
~~~

Đây là minh họa. `List.copyOf` trả danh sách không thể bị chỉnh sửa qua API List, và giúp tạo **defensive snapshot** của collection. Nhưng các **phần tử mutable** bên trong vẫn cần được kiểm soát: `List.copyOf` là **shallow copy**, không tự clone sâu object graph.

Với class Serializable có các field `final` và muốn giữ representation invariant nghiêm ngặt, việc tùy biến trường trong `readObject` không phải lúc nào là cách tốt nhất. Có thể chọn serialization proxy.

### 10.2. Serialization proxy pattern

Ý tưởng: bản thân class quan trọng không được deserialize trực tiếp. Nó serialize một **proxy** chỉ chứa dữ liệu được phép; `readResolve` của proxy tạo object mới **qua constructor/factory có kiểm tra bất biến**.

~~~text
ValueObject hợp lệ
    |
    | writeReplace()
    v
SerializationProxy(data primitives / stable values)
    |
    | byte stream
    v
SerializationProxy được deserialize
    |
    | readResolve() -> new ValueObject(validated data)
    v
ValueObject hợp lệ hoặc InvalidObjectException
~~~

Khi thiết kế theo pattern này, method `readObject` của class gốc thường **luôn ném InvalidObjectException** để cấm khôi phục trực tiếp một serialized form không qua proxy. `readResolve` của proxy xử lý đối tượng thay thế; đảm bảo mọi đường tạo object logic đi qua constructor/factory. Mẫu này có overhead và giới hạn riêng với subclass/cycle, nên cần thiết kế theo domain, không dùng bừa cho mọi class.

### 10.3. Serializable record trên Java 17

Record Serializable được khôi phục **bằng canonical constructor** theo cơ chế serialization đặc biệt. Vì vậy kiểm tra trong canonical constructor có thể dùng làm cổng xác thực ngay khi khôi phục.

~~~java
record Score(int value) implements Serializable {
    Score {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Score out of range");
        }
    }
}
~~~

Đây là **đoạn class minh họa**, cần `java.io.Serializable`. Nếu muốn trả lỗi domain ở cấp deserialization, caller cần hiểu exception có thể được bọc theo cơ chế serialization của record và xử lý thích hợp, không giả định hook `readObject` thông thường được gọi cho record. Với record, những phương thức serialization tùy biến như `readObject`/`writeObject` có semantics khác class Serializable truyền thống; hãy tham khảo đặc tả record serialization thay vì chép nguyên mẫu class thường.

## 11. Đừng xem invariant validation là giải pháp an toàn duy nhất

`InvalidObjectException` chặn một **object state không hợp lệ** trước khi object đó được trả cho caller. Nhưng nó không biến việc deserialize byte không tin cậy thành an toàn:

- **Gadget chain:** trong lúc deserialize, code các type đã được chọn có thể chạy **trước khi validator của class đích hoàn tất**.
- **DoS / object bomb:** graph có thể rất sâu, có quá nhiều đối tượng, mảng rất lớn hoặc tiêu tốn CPU trước lúc được kiểm tra.
- **Side effects sớm:** các `readObject` của class con lồng nhau có thể thực hiện hành vi nguy hiểm trước callback `ObjectInputValidation`.
- **Class evolution:** type và field từ stream có thể khác những gì ứng dụng đã giả định, dù qua được `serialVersionUID`.

**Nguyên tắc:** Không nên deserialize Java native object từ nguồn không tin cậy nếu có lựa chọn format dữ liệu an toàn hơn. Nếu phải dùng Java Serialization, áp dụng **`ObjectInputFilter`** để hạn chế class/resource, xác thực nguồn, giới hạn byte/depth/reference/array, và chỉ sử dụng những class đã được kiểm toán. Filter hỗ trợ giảm bề mặt tấn công **trước/trong quá trình đọc**, còn invariant validator bảo vệ quy tắc domain **sau khi field/graph được dựng**.

### Ví dụ policy filter ở mức ý tưởng

~~~java
ObjectInputFilter filter =
        ObjectInputFilter.Config.createFilter(
                "maxdepth=10;maxrefs=200;maxarray=1000;" +
                "maxbytes=100000;com.example.model.*;!*");
in.setObjectInputFilter(filter);
Object obj = in.readObject();
~~~

Đây là **mã minh họa**: `in` là `ObjectInputStream`, package `com.example.model` chỉ là ví dụ, và filter phải cho phép **các loại thành phần cần thiết khác** theo định dạng object graph thực tế. Không copy nguyên policy vào production mà chưa kiểm tra type sẽ được khôi phục. Filter không tự xác minh field domain và không thay `InvalidObjectException`.

### Không trả một nửa object graph cho caller

Nếu validator ném lỗi, bỏ phiên đọc và byte stream theo chính sách error handling. Không lấy các object đã được đặt tạm vào global cache trong quá trình khôi phục để dùng tiếp, vì chúng có thể chứa state chưa hợp lệ. Nên tránh callback/event đăng ký ngoài hệ thống trước khi validation thành công.

## 12. Unit test bất biến trong môi trường thực tế

Chỉ kiểm tra "serialize rồi deserialize bằng đúng class hiện tại" là chưa đủ; trường hợp đó thường cho **happy path** và không phát hiện đường bỏ qua constructor.

Bộ test nên bao gồm:

1. **Round-trip hợp lệ:** serialize rồi restore thành công, dữ liệu giữ đúng invariants.
2. **Field số sai:** start âm, end nhỏ hơn start, thời gian đảo ngược.
3. **Field nullable:** null name/ID/collection, phần tử null/blank, dữ liệu thiếu.
4. **Cross-field:** tổng không khớp, range chồng lấn trái quy tắc, enum kết hợp không hợp lệ.
5. **Cross-object/cycle:** parent-child reference không đồng bộ hoặc aliasing bất thường.
6. **Dữ liệu phiên bản cũ:** field newly required bị thiếu; hook `readObjectNoData` khi đổi hierarchy.
7. **Object graph quá lớn:** giới hạn size/depth bằng filter/quota, không chờ tới callback.
8. **Defensive copy:** sau restore, thử sửa collection truyền ra API, bảo đảm object giữ đúng invariant.
9. **Exception chính xác:** kỳ vọng `InvalidObjectException` (hoặc lỗi hợp đồng tương ứng) cho đường invalid, không chấp nhận fallback im lặng.
10. **Resource lifecycle:** `ObjectInputStream`/underlying stream được đóng đúng khi exception, không rò rỉ file descriptor.

### Ví dụ assertion mà không lệ thuộc bytes của stream

~~~java
byte[] payload = save(new Range(4, 9));
Range restored = (Range) load(payload);
// Test restored.start == 4 && restored.end == 9.

Range malformed = new Range(4, 9);
// Chỉ code test cùng nest được phép deliberately change private field:
malformed.end = 1;

// Test kỳ vọng load(save(malformed)) ném InvalidObjectException.
~~~

Đoạn minh họa sử dụng helper và nested class của `InvariantDemo` ở mục 6, không phải code độc lập. Không nên **patch bytes ở magic offset** rồi giả định serialized binary format sẽ giống nhau ở mọi build/version. Có thể dùng fixture phiên bản cũ hoặc serialization proxy fixture để tạo nguồn dữ liệu lỗi một cách có kiểm soát.

## 13. Những lỗi phổ biến

1. **"Constructor đã validate nên deserialize luôn an toàn."** Sai với Serializable class thông thường; constructor của chính class không chạy như new.
2. **"`defaultReadObject()` tự kiểm tra tất cả bất biến."** Sai; nó chủ yếu phục hồi field mặc định.
3. **"`readObject()` phải public để Java gọi được."** Sai; hook chuẩn có chữ ký private đặc biệt.
4. **"Cứ ném `IllegalArgumentException` là đủ."** `InvalidObjectException` diễn đạt lỗi khôi phục dữ liệu rõ hơn; caller cần nhận diện đúng loại lỗi.
5. **"Chỉ check field != null."** Sai nếu còn ràng buộc phạm vi, thứ tự, uniqueness, checksum, cardinality hoặc cross-object.
6. **"Đối tượng liên kết luôn hoàn tất khi parent.readObject chạy."** Không nên giả định trong graph có cycle và back-reference.
7. **"`ObjectInputValidation` chạy ngay lúc đăng ký."** Sai; callback được thực hiện sau khi graph khôi phục theo hợp đồng.
8. **"Các callback cùng priority có thứ tự cố định."** Sai; không được đảm bảo.
9. **"Gọi `registerValidation` bất kỳ lúc nào cũng được."** Sai; chỉ được dùng trong ngữ cảnh đọc object phù hợp.
10. **"Chỉ validate một lần đủ giữ bất biến mãi mãi."** Sai nếu object có mutable fields, aliasing và setter không kiểm tra.
11. **"`List.copyOf` clone sâu toàn bộ graph."** Sai; shallow snapshot của collection, element mutable vẫn có thể thay đổi.
12. **"serialVersionUID là security check."** Sai; nó không xác thực source hay domain data.
13. **"ObjectInputFilter thay thế readObject validator."** Sai; filter chủ yếu hạn chế loại object và tài nguyên, không kiểm tra mọi quan hệ giữa field.
14. **"readObjectNoData áp dụng mỗi khi một field thiếu."** Sai; nó xử lý class data vắng trong hierarchy, khác `GetField.defaulted`.
15. **"readObject của Serializable class luôn gọi constructor."** Sai; nhưng Serializable record có cách khôi phục riêng với canonical constructor.
16. **"Thấy validate thất bại cứ sửa state rồi tiếp tục."** Nếu không có quy tắc migration rõ ràng, phải từ chối dữ liệu.
17. **"Dữ liệu đang deserialize có thể tự đăng ký vào global cache ngay."** Nguy hiểm vì object chưa được kiểm chứng.
18. **"Deserialization từ nguồn bất kỳ an toàn miễn là mình bắt IOException."** Sai; validation không chặn mọi gadget/DoS trước khi callback.

## 14. Tự kiểm tra kiến thức

**Câu 1:** Tại sao constructor `Range(int start, int end)` có kiểm tra dữ liệu nhưng một object Deserialize vẫn có thể mang `start > end`?

**Đáp án:** Cơ chế `Serializable` thường khôi phục field từ byte stream mà không gọi constructor của chính Serializable class, nên phải kiểm tra ở hook deserialization.

**Câu 2:** Trong `private readObject`, nên gọi `defaultReadObject()` trước hay sau khi kiểm tra `start` và `end`?

**Đáp án:** Trước. Field cần được phục hồi rồi mới có dữ liệu đúng để kiểm tra. Nếu không có custom format, `defaultReadObject` nạp những field serializable của class.

**Câu 3:** Khi nào dùng `ObjectInputValidation` thay vì chỉ kiểm tra ở `readObject`?

**Đáp án:** Khi bất biến phụ thuộc graph hoặc các đối tượng khác có thể chưa được khôi phục xong trong lúc `readObject` đang chạy. Đăng ký validator từ `readObject` để nhận callback sau graph được khôi phục.

**Câu 4:** Vì sao cùng priority `0` không nên dùng để sắp thứ tự hai validation phụ thuộc nhau?

**Đáp án:** Java không bảo đảm thứ tự callbacks với cùng priority. Nếu cần dependency, phải thiết kế thứ tự có chủ đích hoặc hợp nhất kiểm tra phụ thuộc vào một điểm nhất quán.

**Câu 5:** Nếu stream cũ không có field `String name` mới bắt buộc, có thể phát hiện bằng cách nào?

**Đáp án:** Có thể dùng `readFields()` và `GetField.defaulted("name")`, hoặc phát hiện giá trị null sau `defaultReadObject` tùy policy. Không tự coi null là hợp lệ nếu field là bắt buộc.

**Câu 6:** `ObjectInputFilter` có thay việc kiểm tra `expectedCount == names.size()` không?

**Đáp án:** Không. Filter giúp hạn chế class/graph/resource, còn bất biến domain cần kiểm tra ở readObject hoặc ObjectInputValidation.

**Câu 7:** Object immutable đã được khôi phục và validate list. Dùng `List.copyOf` có làm mọi thành viên bên trong immutable không?

**Đáp án:** Không. Nó tạo snapshot không sửa được của collection, nhưng các phần tử có thể vẫn mutable. Cần defensive copy sâu hoặc type bất biến cho từng phần tử nếu invariant đòi hỏi.

### Tài liệu chính thức (Java 17)

- [Serializable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serializable.html)
- [ObjectInputStream: defaultReadObject, readFields, registerValidation, setObjectInputFilter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html)
- [ObjectInputValidation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputValidation.html)
- [InvalidObjectException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InvalidObjectException.html)
- [ObjectInputFilter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html)
- [Java Object Serialization Specification — Object Input Classes](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/input.html)
- [Java Object Serialization Specification — Serializable Objects](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/serial-arch.html)
- [Record Serialization (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/serialization-records.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-serialization-010">ObjectInputFilter: giới hạn lớp, độ sâu, số tham chiếu và độ dài mảng</a>

<details>
<summary>Xem chi tiết</summary>

# ObjectInputFilter trong Java 17: giới hạn lớp, độ sâu, số tham chiếu và độ dài mảng

## 1. Vấn đề: vì sao cần lọc dữ liệu khi deserialize?

`ObjectInputStream.readObject()` biến một chuỗi byte theo định dạng **Java Object Serialization** thành các instance, array và một **đồ thị đối tượng**. Nếu stream đến từ file, message broker, socket hoặc nguồn mà ứng dụng không kiểm soát hoàn toàn, việc đọc không chỉ là “parse dữ liệu”.

Một stream có thể chứa:

- Lớp không được ứng dụng mong muốn, bao gồm lớp có logic đặc biệt khi deserialize.
- Một object graph lồng nhau rất sâu, làm tăng nguy cơ cạn stack hoặc tài nguyên.
- Rất nhiều object/reference, gây tiêu tốn heap và công xử lý.
- Array có kích thước cực lớn, khiến ứng dụng cố cấp phát bộ nhớ khổng lồ.
- Dữ liệu được encode phức tạp hoặc có thể làm chương trình xử lý lâu.

`java.io.ObjectInputFilter` là một **hàng rào kiểm soát khi deserialization đang diễn ra**: nó có thể quyết định chấp nhận/từ chối lớp và giới hạn các chỉ số tài nguyên của object graph. Nó được đưa vào Java 9 và có thể dùng trên Java 17+.

Tuy nhiên, **filter không tự khiến Java native serialization trở nên an toàn tuyệt đối**. Nó không thay việc xác thực input, phân quyền, kiểm tra dữ liệu sau khi deserialize, giới hạn request size ở tầng giao thức, hay quản lý tài nguyên của ứng dụng.

### Bốn giới hạn cốt lõi

| Giới hạn | Ý nghĩa | Loại vấn đề giảm thiểu |
|---|---|---|
| **Class allowlist** | Chỉ chấp nhận những kiểu dữ liệu được phép | Deserialization của lớp ngoài dự kiến |
| **`maxdepth`** | Giới hạn độ sâu của quá trình duyệt graph | Graph quá sâu, tăng stack/chi phí |
| **`maxrefs`** | Giới hạn số lần tham chiếu được quan sát trong quá trình deserialize | Graph có quá nhiều references |
| **`maxarray`** | Giới hạn số phần tử của một array | Một array yêu cầu cấp phát quá lớn |
| **`maxbytes`** | Giới hạn số byte stream đã tiêu thụ tại các thời điểm kiểm tra | Input lớn/tốn tài nguyên |

Đề bài nhấn mạnh bốn trục class, depth, references, array length. `maxbytes` là giới hạn bổ sung rất nên có, **không phải** phép thay thế cho giới hạn kích thước payload ở tầng trước khi tạo ObjectInputStream.

## 2. Cách ObjectInputFilter được gọi

Interface có phương thức:

~~~java
ObjectInputFilter.Status checkInput(ObjectInputFilter.FilterInfo info);
~~~

Khi `ObjectInputStream` đang xử lý dữ liệu theo cơ chế Java serialization, nó tham vấn filter ở một số điểm để kiểm tra **lớp** và **thông số của object graph**. Đối tượng `FilterInfo` cung cấp thông tin hiện tại:

| `FilterInfo` | Dữ liệu trả về | Cần lưu ý |
|---|---|---|
| `serialClass()` | Lớp đang được xem xét; **có thể null** | Null không có nghĩa request chắc chắn hợp lệ |
| `arrayLength()` | Độ dài array; thường `-1` khi không có array | So sánh trực tiếp với ngưỡng maxarray |
| `depth()` | Độ sâu hiện tại của graph | Không phải inheritance depth của class |
| `references()` | Chỉ số reference của quá trình đọc | Không phải số object distinct hay ID nghiệp vụ |
| `streamBytes()` | Số byte stream đã được đọc tại điểm kiểm tra | Không phải độ dài file tổng, giới hạn xảy ra tại checkpoint |

Hình dung:

~~~text
ObjectInputStream.readObject()
        |
        v
   Đọc token / class / array / reference
        |
        v
   filter.checkInput(FilterInfo)
        |
        +--> ALLOWED   : bước kiểm tra này chấp nhận
        +--> REJECTED  : từ chối, dừng với lỗi deserialization
        +--> UNDECIDED : filter không quyết định tại bước này
        |
        v
   Nếu không bị từ chối, tiếp tục xử lý graph
~~~

### Filter có được gọi cho mọi byte và mọi String không?

**Không.** Theo hợp đồng serialization filtering, filter không phải parser được gọi sau *mỗi byte* hay cho mọi loại giá trị. Những String được encode trực tiếp trong stream và các giá trị primitive có ngoại lệ trong cơ chế gọi filter; tham chiếu đến object đã có handle có thể được kiểm tra theo kiểu khác. Tài liệu mô tả kiểm tra khi gặp các instance mới, array và một số references, nhưng **không được xem đây là phép soi xét toàn bộ semantic của dữ liệu**.

Hệ quả:

- Không dùng ObjectInputFilter để xác thực **nội dung** String, nội dung từng byte hay giá trị business field.
- Dùng bộ lọc lớp và resource limits, rồi **validate object sau khi đọc**.
- Giới hạn byte payload tại ingress (HTTP request body, file length, framed socket message) để chặn input quá lớn ngay từ đầu, không phụ thuộc hoàn toàn vào `streamBytes()`.

## 3. Ba kết quả `ALLOWED`, `REJECTED`, `UNDECIDED`

`checkInput(...)` phải trả về một trong các trạng thái:

~~~java
ObjectInputFilter.Status.ALLOWED
ObjectInputFilter.Status.REJECTED
ObjectInputFilter.Status.UNDECIDED
~~~

- **`ALLOWED`:** filter chấp nhận thông tin được kiểm tra trong lượt này. **Không phải** lời hứa object hoàn chỉnh có ngữ nghĩa hợp lệ hoặc an toàn.
- **`REJECTED`:** từ chối; `ObjectInputStream` sẽ báo lỗi deserialization, thông thường bằng `InvalidClassException`.
- **`UNDECIDED`:** filter không đưa ra quyết định ở lượt này. **Không được suy luận là “bị cấm mặc định”**.

Một vấn đề bảo mật thường gặp là filter chỉ chặn một vài lớp nguy hiểm và trả `UNDECIDED` cho tất cả lớp còn lại. Nếu không có tầng filter khác từ chối, các lớp không nằm trong denylist vẫn có thể được deserialize.

**Chính sách allowlist an toàn hơn:** kiểm tra resource limits trước; trả `ALLOWED` cho các lớp thực sự được phép; trả `REJECTED` cho lớp còn lại. Khi `serialClass()==null` vì không có thông tin class ở callback, vẫn áp dụng limit và có thể trả `UNDECIDED` cho riêng lượt kiểm tra đó.

Ví dụ về quyết định:

~~~text
Lớp Message được phép, length 4, depth 2    -> ALLOWED
Lớp Gadget không nằm trong allowlist         -> REJECTED
Lớp Message được phép, nhưng depth 100       -> REJECTED
Không có class ở callback, metrics hợp lệ     -> UNDECIDED
Không có class ở callback, maxrefs bị vượt    -> REJECTED
~~~

**Không để điều kiện allowlist ghi đè điều kiện limits.** Một lớp được phép nhưng chứa array cực lớn vẫn phải bị chặn.

## 4. Giới hạn lớp: allowlist thay vì chỉ blacklist

Khi một ứng dụng mong đợi DTO như `com.example.dto.Order`, không nên deserialize tùy ý các lớp tìm thấy trên classpath. Có hai cách triển khai:

1. **Pattern filter** dùng `ObjectInputFilter.Config.createFilter(...)`.
2. **Programmatic filter** dùng lambda/class triển khai `ObjectInputFilter`.

### Pattern filter

Ví dụ cấu hình minh họa:

~~~java
ObjectInputFilter filter =
        ObjectInputFilter.Config.createFilter(
                "maxdepth=10;maxrefs=1000;maxarray=10000;"
              + "maxbytes=1048576;"
              + "com.example.dto.*;java.base/java.util.ArrayList;!*");
~~~

Giải thích:

- Các phần cách nhau bởi dấu chấm phẩy `;`.
- Các giới hạn như `maxdepth=10`, `maxrefs=1000`, `maxarray=10000`, `maxbytes=1048576` được áp dụng độc lập với thứ tự các pattern lớp.
- `com.example.dto.*` là pattern cho các class ở package đã nêu theo quy tắc pattern của JDK.
- `java.base/java.util.ArrayList` là pattern có module prefix để chỉ class cụ thể trong module Java.
- `!*` từ chối **các lớp không được những pattern được phép trước đó khớp**.

**Cảnh báo quan trọng:** Pattern filter kiểm tra **kiểu của object** chứ không tự giới hạn toàn bộ các trường hợp dữ liệu theo đúng schema của DTO. DTO có thể chứa collection implementation, nested class hoặc array cần được xem xét riêng. Không copy allowlist trên sang production nếu chưa kiểm tra cấu trúc thực tế và các class được filter gọi trong quá trình đọc.

### Pattern nào khớp class nào?

Theo Java serialization filter pattern:

| Dạng | Ý nghĩa thường dùng |
|---|---|
| `com.acme.dto.Order` | Class đầy đủ được chỉ định |
| `com.acme.dto.*` | Các class thuộc package nhất định |
| `com.acme.dto.**` | Bao gồm package và các subpackage |
| `!com.acme.Bad` | Từ chối class được khớp |
| `!*` | Từ chối các class còn lại không được cho phép |
| `java.base/java.util.ArrayList` | Class kèm module name |
| `maxdepth=20` | Giới hạn depth, **không** phải class pattern |

Với class array, quy tắc matching dùng **element type** theo đặc tả. Class-pattern không phải một danh sách chính xác mọi loại array trong chương trình; array length vẫn được kiểm tra bằng `maxarray`.

**Đừng nhầm "package.*" với wildcard cho bất kỳ package nào, và cũng đừng nhầm class pattern là lệnh kiểm tra giá trị field.** Cần đọc pattern matching rules ở Java 17, nhất là với module prefix, nested class và array.

### Không đưa những lớp cực rộng vào allowlist tùy tiện

Ví dụ `java.base/*` hay `java.**` (tùy cú pháp pattern) có thể quá rộng nếu ứng dụng chỉ cần một vài DTO. Càng mở rộng số class được deserialize, bề mặt tấn công càng lớn.

Thường nên tạo **DTO chuyên dụng ít trường**, field types cố định, không mang runtime service, thread, class loader hoặc callback object vào stream. Hạn chế thư viện serialization trực tiếp cho object domain phức tạp.

## 5. `maxdepth`: độ sâu của đồ thị đối tượng

`depth()` là độ sâu hiện tại trong quá trình giải mã tham chiếu, không phải:

- Độ sâu kế thừa `extends` của class.
- Chiều dài package name.
- Độ sâu JSON theo parser khác.
- Số lượng object duy nhất trong toàn stream.

Ví dụ:

~~~text
Root
  |
  v
Child
  |
  v
GrandChild
  |
  v
GreatGrandChild
  ...
~~~

Nếu graph bị xâu chuỗi quá sâu, quá trình deserialize có thể tiêu tốn stack và CPU. Giới hạn `maxdepth` giúp từ chối khi graph vượt ngưỡng tại các callback.

Ví dụ:

~~~java
ObjectInputFilter filter =
        ObjectInputFilter.Config.createFilter(
                "maxdepth=8;maxrefs=100;maxarray=1000;maxbytes=65536");
~~~

Đoạn này chỉ cấu hình resource limits, **chưa có class allowlist**, nên không phải bộ lọc an toàn đầy đủ cho input không tin cậy.

**Cách chọn maxdepth:** đo và hiểu graph hợp lệ. Nếu DTO tối đa có ba tầng (Envelope → Payload → Item), không cần cho phép 10.000 tầng. Nhưng đừng đặt bằng mắt quá thấp khiến graph hợp lệ bị reject vì collection, wrapper hoặc nested references.

### Vòng tham chiếu và độ sâu

Java Object Serialization có object handles và back-reference để bảo toàn graph có vòng. Một vòng A → B → A không đồng nghĩa serializer sẽ đi sâu vô hạn: khi gặp lại A, nó dùng handle thay vì tạo mới A. Dù vậy, nhiều cấp lồng nhau trước khi gặp back-reference vẫn có thể khiến graph sâu. `maxdepth` là guardrail riêng.

## 6. `maxrefs`: giới hạn số tham chiếu được xử lý

Chỉ số `references()` phản ánh **các reference được quan sát trong quá trình deserialization** theo hợp đồng FilterInfo. Nó **không phải**:

- Số instance khác nhau sau khi đọc xong.
- Số field của class được định nghĩa.
- Số byte của stream.
- Object ID hoặc giá trị `System.identityHashCode()`.

Ví dụ một ArrayList có 100.000 phần tử trỏ cùng một object có thể tạo rất nhiều references trong stream dù chỉ có ít object khác nhau. Vì vậy cần giới hạn maxrefs độc lập với việc chỉ kiểm tra class.

~~~java
ObjectInputFilter filter =
        ObjectInputFilter.Config.createFilter(
                "maxdepth=12;maxrefs=5000;maxarray=10000;"
              + "maxbytes=2097152");
~~~

Nếu `references()` vượt `5000` tại một lần kiểm tra, filter sẽ từ chối. Chỉ số đo tại những callback, nên không được sử dụng như một promise về tổng memory footprint chính xác.

### Maxrefs và shared references

Java serialization bảo toàn việc nhiều field trỏ vào cùng object bằng stream handles. Nhưng maxrefs vẫn có ý nghĩa ngay cả khi các reference chia sẻ một object. **Không giảm nguy cơ chỉ bằng cách giới hạn số class**: một class DTO hợp lệ vẫn có thể chứa đồ thị tham chiếu khổng lồ.

## 7. `maxarray`: chặn cấp phát mảng quá lớn

Một payload chứa `byte[]`, `int[]` hoặc `Order[]` có thể yêu cầu bộ nhớ lớn trước khi chương trình kịp kiểm tra nội dung. Khi deserializing array, `FilterInfo.arrayLength()` cung cấp độ dài nếu biết, và filter có thể từ chối trước khi array được tạo.

Ví dụ:

~~~java
ObjectInputFilter f =
        ObjectInputFilter.Config.createFilter(
                "maxarray=10000;maxdepth=10;maxrefs=1000;"
              + "maxbytes=1048576");
~~~

`maxarray=10000` là **số phần tử**, không phải số byte:

- `byte[10000]` có 10.000 phần tử byte.
- `long[10000]` có 10.000 phần tử kiểu long, tiêu tốn vùng dữ liệu khác.
- `Object[10000]` có 10.000 tham chiếu, và các object mà nó trỏ tới còn tốn tài nguyên riêng.

Đừng lấy `maxarray=1000000` làm tương đương “1 MiB tối đa” trên mọi kiểu mảng. Dùng thêm `maxbytes`, `maxrefs` và hạn chế kích thước input thực tế.

### Khi không có mảng: `arrayLength() == -1`

Trong custom filter, giá trị âm thường có nghĩa **không có thông tin độ dài array ở callback**. Vì vậy:

~~~java
long length = info.arrayLength();
if (length >= 0 && length > MAX_ARRAY_LENGTH) {
    return ObjectInputFilter.Status.REJECTED;
}
~~~

Không yêu cầu mọi class phải có `arrayLength() >= 0`: nếu không, ngay cả DTO hợp lệ không có array cũng bị từ chối.

## 8. `maxbytes`: giới hạn lượng byte đã đọc, không thay ingress limit

`maxbytes` dựa trên `FilterInfo.streamBytes()`. Nếu quá ngưỡng, filter từ chối tại điểm kiểm tra. Tuy nhiên, filter **không được gọi cho mọi byte**; một luồng chứa lượng dữ liệu đặc biệt hoặc thao tác đọc ngoài ObjectInputStream có thể đòi hỏi cách kiểm soát riêng.

Bảo vệ theo lớp:

~~~text
HTTP/Socket/File input
   -> tối đa 1 MiB payload trước deserialization
   -> ObjectInputStream với allowlist + graph limits
   -> DTO được deserialize
   -> validate field values, business invariants
   -> chỉ sau đó mới xử lý nghiệp vụ
~~~

Trên network, nếu input là byte stream liên tục, dùng **length-prefix có giới hạn** hoặc framing có quy tắc, tránh đọc không giới hạn đến EOF. Trên HTTP, áp dụng max request body size. Với file, kiểm tra `Files.size` có ích nhưng file còn có thể thay đổi giữa kiểm tra và sử dụng, nên cần bounded stream/protocol.

## 9. Chương trình Java 17+ hoàn chỉnh: một payload hợp lệ và một payload bị từ chối

Ví dụ sử dụng **custom ObjectInputFilter** để nhìn rõ việc kiểm tra:

- Cho phép duy nhất DTO `Message` và array `byte[]` dùng bởi DTO.
- Giới hạn `depth <= 8`, `references <= 64`, `arrayLength <= 8`, `streamBytes <= 65536`.
- Đọc payload 4 byte thành công.
- Từ chối payload chứa array 12 byte **trước khi chấp nhận object hoàn chỉnh**.
- Không dùng nguồn mạng hoặc file thật, vì bài tập cần kết quả xác định.

Lưu thành **`InputFilterDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class InputFilterDemo {

    private static final class Message implements Serializable {
        private static final long serialVersionUID = 1L;

        final int id;
        final byte[] payload;

        Message(int id, byte[] payload) {
            this.id = id;
            this.payload = payload;
        }
    }

    private static ObjectInputFilter policy() {
        return info -> {
            // Luôn xét resource limits trước class allowlist.
            if (info.depth() > 8
                    || info.references() > 64
                    || info.streamBytes() > 65536
                    || info.arrayLength() > 8) {
                return ObjectInputFilter.Status.REJECTED;
            }

            Class<?> type = info.serialClass();
            if (type == null) {
                // Khi chỉ có metric/reference, vẫn đã check limits.
                return ObjectInputFilter.Status.UNDECIDED;
            }

            if (type == Message.class || type == byte[].class) {
                return ObjectInputFilter.Status.ALLOWED;
            }

            return ObjectInputFilter.Status.REJECTED;
        };
    }

    private static byte[] encode(Message message) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(message);
        }
        return bytes.toByteArray();
    }

    private static void decode(String caseName, byte[] bytes)
            throws Exception {
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(policy());

            try {
                Message restored = (Message) in.readObject();
                System.out.println(caseName + ": id=" + restored.id
                        + ", length=" + restored.payload.length);
            } catch (InvalidClassException rejected) {
                System.out.println(caseName + ": REJECTED");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        decode("allowed",
                encode(new Message(7, new byte[] {1, 2, 3, 4})));

        decode("too-large",
                encode(new Message(8, new byte[12])));
    }
}
~~~

Lệnh biên dịch và chạy:

~~~bash
javac InputFilterDemo.java
java InputFilterDemo
~~~

**Stdout mong đợi trên Java 17 với cơ chế serialization mặc định:**

~~~text
allowed: id=7, length=4
too-large: REJECTED
~~~

Giải thích từng bước:

1. `Message` triển khai `Serializable`, chứa `int` và `byte[]`.
2. `encode(...)` tạo bytes theo native Java serialization; giai đoạn ghi không áp dụng input filter vì filter là cơ chế **đọc**.
3. `decode(...)` tạo `ObjectInputStream`, **cài filter trước lần đầu gọi `readObject()`**.
4. Với `Message(7, byte[4])`, lớp `Message` và `byte[]` được phép; depth/references/streamBytes đều trong ngưỡng, nên tạo được object và in kết quả.
5. Với `Message(8, byte[12])`, khi bộ lọc được hỏi về array có length 12, điều kiện `arrayLength() > 8` trả `REJECTED`. Deserialization báo `InvalidClassException`, và chương trình in `too-large: REJECTED`.
6. Không in thông tin exception message vì message cụ thể có thể khác theo implementation/JDK build.

Lưu ý: ví dụ đặt `readObject()` trong `try/catch InvalidClassException` để minh họa filtering. Ở production nên phân loại riêng stream hỏng, IO, schema mismatch và filter rejection, **không mặc định mọi InvalidClassException đều do filter**. Ngoài ra, custom filter này chỉ mô tả các loại object được mong đợi trong DTO nhỏ; một DTO phức tạp cần whitelist và kiểm thử thêm nested types/arrays.

### Kiểm tra ý nghĩa của `UNDECIDED` trong ví dụ

Khi `serialClass() == null`, custom filter trả `UNDECIDED` **sau khi** đã kiểm tra giới hạn tài nguyên. Đây là cách đúng trong ngữ cảnh đã chủ động thiết kế policy cho lớp được quan sát; tuy nhiên trong ứng dụng phức tạp, việc kết hợp filter với global filter factory có thể thay đổi cách `UNDECIDED` được xử lý. Đừng coi `UNDECIDED` tự động là reject.

## 10. Viết filter theo pattern cho các hệ thống thông thường

Khi schema lớp đã biết, pattern là lựa chọn tiện lợi và ít code hơn:

~~~java
ObjectInputFilter filter =
        ObjectInputFilter.Config.createFilter(
                "maxdepth=8;maxrefs=64;maxarray=8;maxbytes=65536;"
              + "com.example.messages.Message;!*");

try (ObjectInputStream in = new ObjectInputStream(source)) {
    in.setObjectInputFilter(filter);
    Object o = in.readObject();
}
~~~

Đoạn này là **mã minh họa**, `source` là `InputStream` do ứng dụng tạo và `com.example.messages.Message` là lớp DTO giả định. Với array, nested classes và filter factory, phải chạy test trên input hợp lệ/không hợp lệ thực tế trước khi triển khai. Nếu bất cứ loại nested type nào bị loại bởi `!*`, phải **bổ sung chính xác** vào allowlist thay vì xóa deny-by-default.

**Điểm dễ sai:** `createFilter(...)` với chỉ `maxdepth/maxrefs` vẫn không tự xây class allowlist. Việc thêm `!*` có ý nghĩa đóng chính sách đối với các class không khớp pattern; ngược lại, nếu không có rule reject chung, kết quả cho class không khớp có thể là `UNDECIDED`.

### Pattern kết hợp đúng các loại giới hạn

Gợi ý quy trình xây pattern:

1. Liệt kê các DTO root được phép, dựa trên schema thực tế.
2. Tạo thử stream hợp lệ có nested collections, arrays và nullable fields.
3. Bật allowlist chặt; xem loại class nào được filter hỏi trong test.
4. Cho phép thêm **chính xác** các class cần thiết; tránh wildcard quá rộng.
5. Đặt `maxdepth` và `maxrefs` theo thống kê graph hợp lệ cộng biên an toàn nhỏ.
6. Đặt `maxarray` theo kích thước array lớn nhất mà nghiệp vụ chấp nhận.
7. Đặt `maxbytes` và giới hạn bytes của toàn request ở ingress.
8. Thêm các negative tests: class lạ, deep nesting, massive array, excessive references.

## 11. Per-stream filter, global filter và filter factory

### 11.1. Per-stream: cấu hình trên từng ObjectInputStream

~~~java
try (ObjectInputStream in = new ObjectInputStream(source)) {
    in.setObjectInputFilter(filter);
    Object result = in.readObject();
}
~~~

Thích hợp khi các loại payload hoặc mức độ tin cậy khác nhau cần allowlist khác nhau. Nên cài filter **ngay sau khi tạo stream và trước khi deserialize object đầu tiên**.

Không tạo stream rồi bắt đầu `readObject()`, sau đó mới cài filter để hy vọng các object đã đọc được kiểm soát hồi tố.

### 11.2. Global filter: chính sách toàn JVM

Java hỗ trợ filter phạm vi JVM qua **system property** hoặc API, ví dụ:

~~~text
-Djdk.serialFilter=maxdepth=20;maxrefs=10000;maxarray=100000;maxbytes=10485760
~~~

Đây là cấu hình minh họa; việc escape dấu chấm phẩy phụ thuộc shell/launch command. Có thể cấu hình runtime-wide policy khi khởi động ứng dụng. `ObjectInputFilter.Config.setSerialFilter(...)` cũng tồn tại để thiết lập static JVM-wide filter, nhưng có quy tắc về thời điểm/khả năng đặt lại và quyền hạn; không tự gọi nhiều lần giữa vòng đời ứng dụng.

**Global filter là defense-in-depth**, không nên là lý do để từng luồng đọc từ nguồn không tin cậy bỏ qua class allowlist riêng.

### 11.3. Java 17: filter factory và cách kết hợp

Java 17 hỗ trợ cơ chế **context-specific deserialization filters** thông qua filter factory (JEP 415). API:

~~~java
ObjectInputFilter.Config.setSerialFilterFactory(
        (current, requested) -> {
            // Quyết định cách kết hợp hoặc chọn policy.
            // Đây là ví dụ ý tưởng; cần hiểu rõ toàn bộ contract
            // và quyền sở hữu cấu hình toàn JVM.
            return requested != null ? requested : current;
        });
~~~

Đây là **đoạn minh họa về cấu hình**, không phải policy đủ an toàn để copy vào ứng dụng: nó có thể thay policy hiện tại bằng policy mới, khiến các hạn chế cũ không được áp dụng. **Không sử dụng factory trên để bảo đảm global + per-stream filters đều hoạt động.**

Điểm cần hiểu:

- Factory nhận filter hiện tại và filter được yêu cầu, quyết định filter kết quả cho luồng trong ngữ cảnh đó.
- Một factory cấu hình sai có thể vô tình bỏ global limits khi cài per-stream filter.
- Đặc tả cho phép cấu hình factory ở thời điểm sớm; đây là chính sách JVM-wide cần thiết kế/kiểm thử tập trung, không phải cài đặt tùy tiện sau khi server đã chạy.
- Nếu cần kết hợp allowlist và limits, hãy định nghĩa rõ phép **hợp hoặc giao** của policies, trong đó bất kỳ `REJECTED` nào từ giới hạn bắt buộc phải được tôn trọng.

**Khuyến nghị:** cho ứng dụng nhỏ, dùng filter riêng cài trước `readObject` với **cả class rules lẫn resource limits**. Với hệ thống nhiều module và thư viện, thiết kế factory chung, có integration tests chứng minh không đường nào bỏ qua policy tối thiểu.

## 12. Khi filter trả `REJECTED` thì chuyện gì xảy ra?

Khi một thông tin được filter từ chối, `ObjectInputStream` báo lỗi; thực tế phổ biến là `InvalidClassException` chứa thông điệp trạng thái filter. Đừng dựa vào string message cụ thể làm API ổn định.

Một mẫu xử lý:

~~~java
try {
    Object result = in.readObject();
    validateBusinessFields(result);
} catch (InvalidClassException e) {
    // Có thể do filter từ chối hoặc class incompatibility.
    // Ghi log an toàn, kết thúc request ở trạng thái lỗi.
} catch (StreamCorruptedException e) {
    // Binary stream không đúng định dạng.
} catch (ClassNotFoundException e) {
    // Không tìm được class mà stream yêu cầu.
} catch (IOException e) {
    // Lỗi I/O hoặc serialization protocol.
}
~~~

Đoạn trên là mã minh họa; `in` và `validateBusinessFields` được định nghĩa bên ngoài. Không tiếp tục deserialize phần còn lại của cùng input không tin cậy sau lỗi nghiêm trọng như thể stream vẫn còn alignment đúng; thường nên đóng stream/request và báo lỗi.

**Tránh log nguyên payload hoặc object graph không tin cậy.** Điều đó có thể gây lộ dữ liệu nhạy cảm hoặc kích hoạt `toString()` phức tạp sau khi object được đọc.

## 13. Những giới hạn ObjectInputFilter không giải quyết

Filter là cơ chế cần thiết khi còn sử dụng Java Object Serialization, nhưng không nên “thần thánh hóa”:

### Không xác thực business field

Object được phép có thể chứa `amount=-999999`, ngày không hợp lệ, enum không đúng state hoặc thông tin trùng lặp. Filter kiểm soát **kiểu và kích thước graph**, không đảm bảo business rules. Sau `readObject`, validate theo schema và nghiệp vụ.

### Không tự ngăn mọi nguy cơ gadget chain

Cho phép một số class không có nghĩa class đó luôn an toàn, và logic `readObject`, `readResolve`, `readExternal` hoặc các callback tùy biến của lớp có thể thực thi trong quá trình deserialize. Class allowlist giảm bề mặt nguy hiểm, nhưng việc cho phép class có side effects vẫn là rủi ro. Chỉ deserialize data class đã được kiểm toán.

### Không phải parser byte-level hoặc checksum

Filter không xác thực chữ ký số, không kiểm tra CRC, không chống input đã bị sửa theo protocol, không decrypt và không biết người gửi là ai. Cần TLS/authentication, message integrity và phân quyền phù hợp.

### Không thay timeout và giới hạn CPU/memory tổng

Mỗi callback chỉ cung cấp một số metrics. Xử lý tùy biến trong deserialization có thể tiêu tốn CPU; file/network request có thể chậm, bị đóng giữa chừng hoặc chứa nhiều byte chưa bị check bởi filter. Đặt deadline, bounded input, quota và monitoring ở tầng ngoài.

### Không làm Object Serialization phù hợp mọi hệ thống

Java native serialization gắn với kiểu Java, class descriptor và `serialVersionUID`. Trong public API hoặc giao tiếp cross-language, định dạng có schema rõ (JSON/CBOR/Protocol Buffers, v.v.) có thể thích hợp hơn; vẫn phải áp dụng validation/limits với bất kỳ parser nào.

## 14. Khác biệt giữa filter với `transient` và `serialVersionUID`

- `transient`: điều chỉnh **field nào** được default serialization ghi/đọc. Nó **không** ngăn mọi class lạ xuất hiện trong stream.
- `serialVersionUID`: hỗ trợ xác định khả năng tương thích phiên bản class; **không phải** class allowlist hoặc chữ ký xác thực.
- `readObject(...)` tùy biến: có thể quyết định dữ liệu/logic deserialization; nếu không kiểm soát, một lớp đã được phép vẫn có thể tạo object hoặc tác dụng phụ ngoài ý muốn.
- `ObjectInputFilter`: quyết định class/resource constraints tại các checkpoint của quá trình đọc.

Kết hợp cả bốn chỉ khi có mục tiêu tương ứng; không xem công cụ nào thay thế các công cụ còn lại.

## 15. Thiết kế custom filter đúng với `FilterInfo`

Với callback:

~~~java
ObjectInputFilter filter = info -> {
    if (info.depth() > 10
            || info.references() > 1000
            || info.streamBytes() > 1_048_576
            || info.arrayLength() > 10_000) {
        return ObjectInputFilter.Status.REJECTED;
    }

    Class<?> type = info.serialClass();
    if (type == null) {
        return ObjectInputFilter.Status.UNDECIDED;
    }

    if (type == MyAllowedDto.class || type == byte[].class) {
        return ObjectInputFilter.Status.ALLOWED;
    }

    return ObjectInputFilter.Status.REJECTED;
};
~~~

Đoạn này là mã minh họa vì `MyAllowedDto` do ứng dụng định nghĩa. Cần lưu ý:

1. **Limits trước allowlist**: class được phép vẫn không được dùng tài nguyên vô hạn.
2. **Class null** không làm bộ lọc bỏ qua kiểm tra depth/references/bytes/array.
3. **Array length = -1** khi không có độ dài array: so sánh `> ngưỡng dương` không vô tình reject.
4. **Không gọi phương thức phức tạp trong filter**: filter chạy ở đường deserialize, nên phải ngắn và deterministic.
5. **Không truy cập network/database trong checkInput**: dễ tạo deadlock, chậm hoặc mở bề mặt lỗi.
6. **Không trả UNDECIDED cho unknown class** nếu muốn class allowlist đóng.
7. **Không chặn cả array có type được phép** do quên `byte[].class` hoặc các array hợp lệ; viết test thật.
8. **Không coi ALLOWED là validate xong**; sau đọc vẫn kiểm tra object invariants.

## 16. Tối ưu chính sách giới hạn cho workload thật

Không có một bộ số chuẩn cho mọi ứng dụng. Quy trình thiết kế:

- Đo chiều sâu và số references của một **payload hợp lệ lớn nhất**.
- Liệt kê array types và kích thước kỳ vọng.
- Xác định giới hạn **kích thước request ở ingress** thấp hơn mức khiến heap/CPU quá tải.
- Chọn ngưỡng với khoảng dư nhỏ phù hợp thay vì vô hạn.
- Viết test cho cả trường hợp hợp lệ ngay sát ngưỡng và trường hợp vượt ngưỡng.
- Kiểm tra đầu vào có vòng tham chiếu, nhiều shared handles và nested arrays.
- Chú ý message có String lớn: filter không đo nội dung string như validator; maxbytes/checkpoint và bounded input quan trọng.
- Giám sát tỷ lệ filter rejection, nhưng không log payload không tin cậy hoặc dữ liệu nhạy cảm.

Ví dụ policy mẫu cho một DTO nhỏ có payload array tối đa 4 KiB:

| Quy tắc | Giá trị ví dụ | Vì sao |
|---|---:|---|
| Class allowlist | `OrderMessage`, các nested types chính xác | Giảm class ngoài dự kiến |
| `maxdepth` | 12 | Đủ một graph không quá sâu |
| `maxrefs` | 2000 | Giới hạn tham chiếu |
| `maxarray` | 4096 | Mảng tối đa 4096 phần tử |
| `maxbytes` | 65536 | Cản stream lớn tại các callback |
| Ingress cap | 65536 bytes | Giới hạn kích thước payload trước deserialize |

Các con số chỉ là **ví dụ cấu hình**, không phải định mức an toàn tổng quát. Nếu `OrderMessage` có String cực lớn hoặc nested map tạo nhiều class, cần điều chỉnh schema và chính sách.

## 17. Test bảo mật: phải có cả positive lẫn negative cases

| Test | Dữ liệu thử | Kỳ vọng |
|---|---|---|
| Allowed class | DTO nằm trong allowlist, kích thước bình thường | Đọc thành công, validate business |
| Unknown class | Một class Serializable khác | Bị `REJECTED` |
| Array vượt giới hạn | `byte[limit+1]` | Bị `REJECTED` |
| Nested graph sâu | Chain dài vượt maxdepth | Bị `REJECTED` |
| Excessive references | Graph có nhiều reference | Bị `REJECTED` |
| Too many bytes | Stream vượt maxbytes và kích hoạt checkpoint | Bị `REJECTED` hoặc ingress cap chặn sớm |
| Corrupted stream | Dữ liệu hỏng/mất bytes | Báo lỗi, không tiếp tục deserialize mù |
| Compatible update | Phiên bản DTO mong đợi | Đọc/validate theo policy |
| Class version incompatible | UID/schema không tương thích | Xử lý `InvalidClassException` |
| Null class info | Callback metrics-only | Vẫn kiểm limits, không suy ra unknown được allow |
| Global factory | Cài per-stream filter lên policy global | Không làm mất limits toàn JVM |

Ngoài unit tests, cần integration tests đúng JDK/runtime và thư viện serializer thực tế. Nếu có custom `readObject`/`readResolve`, test cả những nhánh đó.

## 18. Lỗi thường gặp

| Sai lầm | Hậu quả | Cách khắc phục |
|---|---|---|
| Chỉ dùng denylist class đã biết | Class nguy hiểm khác vẫn có thể đi qua | Dùng allowlist chặt |
| Không thêm `!*` ở cuối pattern mong deny-by-default | Class không khớp có thể `UNDECIDED` | Đặt rule reject rõ ràng |
| Cho phép class quá rộng | Tăng bề mặt gadget/deserialization | Cho phép đúng DTO và nested types |
| Không kiểm `maxdepth` | Graph lồng sâu | Đặt depth cap |
| Không kiểm `maxrefs` | Rất nhiều references | Đặt reference cap |
| Dùng `maxarray` như số byte | `long[]` hoặc object[] quá lớn | Hiểu đây là số phần tử |
| Chỉ đặt `maxbytes` mà không giới hạn input | Filter không phải byte-level hard cap | Bounded stream/request length |
| Trả `ALLOWED` trước khi kiểm limits | Class hợp lệ có thể tiêu thụ quá mức | Kiểm limits đầu tiên |
| Trả `UNDECIDED` cho mọi lớp lạ và cho rằng sẽ reject | Một filter khác có thể không chặn | Trả `REJECTED` cho unknown class |
| Cài filter sau khi đã đọc object | Không bảo vệ dữ liệu đã xử lý | Set trước `readObject` đầu tiên |
| Cho rằng `serialClass()!=null` trong mọi callback | NullPointerException hoặc bỏ qua metrics | Xử lý null đúng |
| Global/per-stream filter thay thế nhau ngoài ý muốn | Bỏ mất security limits | Thiết kế/test filter factory |
| Cho rằng filter validate String/content | Nhận giá trị nghiệp vụ sai | Validation ở tầng DTO |
| Catch `InvalidClassException` rồi cứ đọc tiếp stream | State có thể không còn đáng tin | Đóng stream/request, báo lỗi |
| Deserialization từ Internet mặc định an toàn nhờ filter | Vẫn nhiều bề mặt tấn công | Ưu tiên format an toàn hơn và defense-in-depth |

## 19. Câu hỏi tự kiểm tra

1. `ObjectInputFilter` được dùng khi serialize hay deserialize? **Khi deserialize** qua ObjectInputStream.
2. `UNDECIDED` có nghĩa chắc chắn bị từ chối không? **Không.** Đây là trạng thái filter chưa quyết định.
3. Ưu tiên kiểm tra class allowlist hay resource limits trong custom filter? **Kiểm resource limits trước**.
4. `serialClass() == null` có được bỏ toàn bộ kiểm tra không? **Không; vẫn kiểm depth, references, arrayLength và streamBytes**.
5. `maxdepth` giới hạn chiều sâu inheritance class không? **Không; nó giới hạn độ sâu object graph khi đọc**.
6. `maxrefs` có phải số instance duy nhất? **Không, là metric reference trong quá trình deserialize**.
7. `maxarray=500` nghĩa là tối đa 500 bytes cho mọi array? **Không, tối đa 500 phần tử array**.
8. Nếu `arrayLength() == -1`, điều gì cần làm? **Đừng xem đó là array có độ dài âm lỗi; đây là giá trị không có thông tin độ dài**.
9. `maxbytes` có thay thế được ingress size cap không? **Không.**
10. Trong pattern, `!*` dùng để làm gì? **Từ chối những class không khớp allowlist trước đó**.
11. `setObjectInputFilter` nên được gọi lúc nào? **Trước khi bắt đầu `readObject()`**.
12. Khi filter từ chối lớp, thường gặp exception gì? **`InvalidClassException`**.
13. `InvalidClassException` có phải luôn do filter không? **Không; class incompatibility cũng có thể gây ra**.
14. Global filter và per-stream filter luôn tự động cộng dồn limits không? **Không được suy đoán; phải hiểu và test filter factory**.
15. Lớp được `ALLOWED` có đồng nghĩa field values đã hợp lệ? **Không**, cần validation sau khi deserialize.
16. ObjectInputFilter có tự chống mọi gadget chain không? **Không; giảm bề mặt, không phải sandbox**.
17. Có nên dùng Java native serialization cho API nhận input công khai không? **Thường nên ưu tiên định dạng/schema khác**, nếu buộc dùng thì thực hiện defense-in-depth.

## 20. Tài liệu chính thức

- [Java SE 17 — ObjectInputFilter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html): FilterInfo, Status, class/resource limits và filter composition.
- [Java SE 17 — ObjectInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html): `setObjectInputFilter`, `readObject`, lỗi input.
- [Java SE 17 — ObjectInputFilter.Config](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.Config.html): `createFilter`, global filter và filter factory.
- [Java SE 17 — Serialization Filtering Guide](https://docs.oracle.com/en/java/javase/17/core/serialization-filtering1.html): pattern-based filter, resource limits, cách kết hợp filters.
- [JEP 290 — Filter Incoming Serialization Data](https://openjdk.org/jeps/290): cơ sở kỹ thuật, semantics lọc class và graph.
- [JEP 415 — Context-Specific Deserialization Filters](https://openjdk.org/jeps/415): chính sách filter theo context và factory trong Java 17.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-serialization-011">Filter theo stream, cấu hình JVM và phụ thuộc phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Filter theo stream, cấu hình JVM và phụ thuộc phiên bản JDK trong Java Serialization

Khi dùng `ObjectInputStream.readObject()`, ứng dụng đưa một luồng byte vào cơ chế **giải tuần tự hóa Java (deserialization)**. Một stream độc hại có thể yêu cầu khôi phục kiểu lớp không mong muốn hoặc đồ thị đối tượng rất lớn, lồng sâu, tiêu tốn tài nguyên. **`ObjectInputFilter`** (serialization filter) cho phép ứng dụng kiểm tra tên lớp và một số chỉ số của đồ thị trong quá trình đọc, rồi **`ALLOWED`**, **`REJECTED`** hoặc **`UNDECIDED`**.

Có ba tầng cấu hình dễ bị nhầm:

1. **Stream-specific filter:** thiết lập trên **một** `ObjectInputStream`, ví dụ `input.setObjectInputFilter(filter)`.
2. **JVM-wide/static filter:** cấu hình qua `-Djdk.serialFilter=...` hoặc `ObjectInputFilter.Config.setSerialFilter(...)`, áp dụng ở cấp JVM theo cơ chế chọn filter.
3. **JVM-wide filter factory (từ JDK 17):** chọn hoặc kết hợp filter cho **từng stream** khi `ObjectInputStream` được tạo và khi một filter riêng được yêu cầu; cấu hình qua `jdk.serialFilterFactory` hoặc `setSerialFilterFactory`.

Không nên nghĩ rằng chỉ cần bật một filter bất kỳ là việc deserialize dữ liệu không tin cậy đã an toàn. Bộ lọc là **lớp bảo vệ bổ sung**. Lựa chọn an toàn hơn vẫn là không dùng Java native deserialization cho dữ liệu không tin cậy, mà sử dụng định dạng dữ liệu được kiểm tra schema, xác thực và giới hạn tài nguyên.

## 1. API cốt lõi và mối nguy cần giới hạn

Các lớp liên quan thuộc package `java.io`:

| API | Vai trò |
| --- | --- |
| `ObjectInputStream` | Đọc đối tượng từ Java serialization stream |
| `ObjectInputFilter` | Giao diện đánh giá các đối tượng/lớp và chỉ số đồ thị khi deserialize |
| `ObjectInputFilter.FilterInfo` | Ngữ cảnh hiện tại: `serialClass()`, `arrayLength()`, `depth()`, `references()`, `streamBytes()` |
| `ObjectInputFilter.Status` | Ba quyết định `ALLOWED`, `REJECTED`, `UNDECIDED` |
| `ObjectInputFilter.Config` | Tạo filter từ chuỗi pattern, đọc/đặt filter tĩnh và filter factory cấp JVM |
| `ObjectInputStream.setObjectInputFilter(...)` | Yêu cầu áp dụng filter cụ thể cho một stream |
| `ObjectInputStream.getObjectInputFilter()` | Kiểm tra filter đang áp dụng trên stream theo cấu hình thực tế |

Filter có thể kiểm soát **những loại class được dựng và những chỉ số tài nguyên**. Nó không phải trình phân tích giá trị nghiệp vụ; nó cũng không xác minh danh tính người gửi, không bảo đảm một object đã được tạo có trạng thái hợp lệ và không biến dữ liệu serialization thành sandbox an toàn.

Một payload có thể nguy hiểm vì lớp được phép có `readObject()`/`readResolve()` tùy biến, vì đồ thị quá sâu, vì chuỗi tham chiếu nhiều hoặc vì dữ liệu không hợp lệ. Do đó cần cả **allowlist lớp theo use case**, **giới hạn đồ thị**, **giới hạn bytes/CPU** và **kiểm tra dữ liệu sau khi đọc**.

## 2. Vòng đời một stream với filter

Quy trình phổ biến:

```text
Nguồn bytes đáng tin cậy hoặc đã kiểm soát
          |
          v
new ObjectInputStream(inputBytes)
          |  (JVM-wide filter factory chọn filter ban đầu)
          v
setObjectInputFilter(streamPolicy)
          |  (filter factory quyết định filter đang hiệu lực)
          v
readObject()
          |  filter kiểm tra class / array / depth / refs / streamBytes
          +--- REJECTED  --> thao tác đọc thất bại
          +--- ALLOWED   --> tiếp tục theo quy trình giải tuần tự hóa
          +--- UNDECIDED --> không xác nhận cho phép; xử lý theo chính sách kết hợp
          v
Xác minh kiểu, tính hợp lệ nghiệp vụ, đóng stream
```

Ở JDK 17, `ObjectInputStream` được tạo với một filter ban đầu thông qua factory. Khi gọi `setObjectInputFilter()`, **factory lại quyết định filter cuối cùng** dựa trên filter hiện tại và filter mới được yêu cầu. Vì vậy việc gọi phương thức này không nên được hiểu là "chắc chắn chỉ còn filter vừa truyền" hoặc "chắc chắn được merge với global" trong mọi ứng dụng: **hành vi phụ thuộc factory thực tế**.

Các điểm giới hạn quan trọng:

- Filter có thể được gọi **không hoặc nhiều lần**, tùy loại nội dung stream và bước xử lý. Một số giá trị được mã hóa đặc biệt, chẳng hạn `String` và primitive ở những dạng nhất định, **không nhất thiết kích hoạt callback kiểm tra class cho từng giá trị**.
- `FilterInfo.serialClass()` có thể `null` khi lần kiểm tra chỉ xét giới hạn đồ thị/tài nguyên, vì vậy không dereference tùy tiện.
- Khi gặp array, filter có thể được gọi với class của array và có thể lấy `arrayLength()`. Pattern filter so khớp **kiểu phần tử** cho array, không đơn giản so tên `[Ljava.lang.String;`.
- `arrayLength()` có thể là `-1` khi không phải kiểm tra một array; không so nó với kích thước thật của mọi loại collection.
- Filter đánh giá **trước hoặc trong quá trình tạo graph**; nó không đảm bảo từ chối một object trước khi bất kỳ tác dụng phụ nào của thao tác deserialization khác xảy ra. Không dùng nó thay kiểm tra nguồn dữ liệu.

## 3. Bộ lọc theo stream: tách chính sách cho từng luồng

Một dịch vụ có thể có nhiều đầu vào. Ví dụ một stream chỉ nên nhận `InvoiceDTO`, stream khác nhận `CacheSnapshot`. Dùng một danh sách lớp cho tất cả dễ khiến quyền giải tuần tự hóa trở nên rộng hơn nhu cầu.

Với JDK 9+ và JDK 17, bạn có thể tạo filter bằng pattern rồi yêu cầu gắn vào `ObjectInputStream`:

```java
// Minh họa trong một phương thức đọc, trước readObject().
ObjectInputFilter policy = ObjectInputFilter.Config.createFilter(
        "maxdepth=10;maxrefs=200;maxbytes=1048576;maxarray=10000;" +
        "com.example.dto.**;java.base/java.util.*;!*");

try (ObjectInputStream in = new ObjectInputStream(source)) {
    in.setObjectInputFilter(policy);
    Object value = in.readObject();
    // Kiểm tra kiểu cụ thể và validate cấu trúc dữ liệu.
}
```

Đây là **đoạn minh họa**, chưa định nghĩa `source` và những lớp `com.example.dto`; không phải chương trình biên dịch độc lập. Nếu ứng dụng dùng `List`, `Map`, array hoặc class nội bộ khác, phải kiểm thử chính sách trên **toàn bộ object graph hợp lệ**, không mặc định pattern trên luôn phù hợp.

**Đặt filter trước lần đọc đầu tiên** là thói quen cần thiết. Với API chuẩn, không nên chờ đọc một phần object rồi mới quyết định cài filter. Nếu `ObjectInputStream` do framework/library sở hữu, việc gọi `setObjectInputFilter()` có thể không được phép hoặc đã quá muộn; cần phối hợp cấu hình ngay ở nơi tạo stream.

### Khi nào nên dùng custom filter thay vì pattern?

Khi cần kiểm tra logic ngoài tên class, viết lambda theo `ObjectInputFilter.FilterInfo`:

```java
// Minh họa trong chương trình đã import java.io.ObjectInputFilter.
ObjectInputFilter policy = info -> {
    if (info.depth() > 12
            || info.references() > 1000
            || info.streamBytes() > 1_048_576
            || info.arrayLength() > 10_000) {
        return ObjectInputFilter.Status.REJECTED;
    }

    Class<?> type = info.serialClass();
    if (type == null) {
        return ObjectInputFilter.Status.UNDECIDED;
    }
    if (type == ExampleDTO.class) {
        return ObjectInputFilter.Status.ALLOWED;
    }
    return ObjectInputFilter.Status.REJECTED;
};
```

Đây là **minh họa**, `ExampleDTO` là class ứng dụng giả định, không phải file độc lập. Bộ lọc này chỉ cho phép `ExampleDTO` ở các lần callback có class cụ thể: nếu `ExampleDTO` chứa field kiểu class/array khác cần deserialize, filter sẽ từ chối chúng. Với hệ thống thật, hãy xét toàn bộ kiểu hợp lệ và không bỏ quên primitive array, collection, proxy, nested class hay các object phụ thuộc.

## 4. Ba kết quả ALLOWED, REJECTED, UNDECIDED

| Kết quả | Ý nghĩa |
| --- | --- |
| `ALLOWED` | Kiểm tra hiện tại vượt qua chính sách filter này; tiếp tục deserialization |
| `REJECTED` | Từ chối; deserialization sẽ thất bại, thường thể hiện qua `InvalidClassException` |
| `UNDECIDED` | Filter không đưa ra quyết định cho lần kiểm tra này; **không phải ALLOWED theo nghĩa allowlist** |

Với `ObjectInputFilter.Config.createFilter(pattern)`, hệ thống xét **giới hạn tài nguyên trước**, rồi xét pattern class **từ trái sang phải**. Pattern đầu tiên khớp quyết định ALLOWED/REJECTED; nếu không khớp và không vượt ngưỡng, kết quả là UNDECIDED.

Trong trường hợp filter hiệu lực trả UNDECIDED cho một class và không có thành phần khác từ chối, quá trình đọc có thể tiếp tục. Bởi vậy **chỉ ghi tên các class được phép nhưng không thêm quy tắc từ chối phần còn lại không nhất thiết tạo ra allowlist "deny by default"**.

Một pattern phổ biến có dấu `!*` cuối chuỗi:

```text
com.example.api.**;java.base/java.util.*;!*
```

Nghĩa là chấp nhận các class khớp hai pattern đầu, rồi từ chối những class còn lại khi có kiểm tra class. Tuy nhiên không được mở cả package lớn nếu ứng dụng chỉ cần vài DTO cụ thể; mọi class thuộc package/module được cho phép đều phải nằm trong mô hình tin cậy.

Trong JDK 17 có các công cụ `ObjectInputFilter.merge(...)` để kết hợp filter và `ObjectInputFilter.rejectUndecidedClass(...)` để xử lý kết quả chưa quyết định trên các lần kiểm tra class. Cách dùng phải được đánh giá theo từng hệ thống; không nên tự suy diễn rằng “merge” luôn bằng nối các pattern hay luôn giống phép giao của hai allowlist.

## 5. Cấu hình JVM-wide bằng jdk.serialFilter

Nếu ứng dụng hoặc thư viện tạo `ObjectInputStream` tại nhiều vị trí khó sửa, bạn có thể cấu hình filter tĩnh **ngay lúc khởi động JVM**:

```bash
java '-Djdk.serialFilter=maxdepth=12;maxrefs=5000;maxbytes=10485760;maxarray=100000;com.example.dto.**;!*' com.example.Main
```

Đây là lệnh **minh họa**: class `com.example.Main` và package DTO cần có thật trong ứng dụng. Trên hệ shell cụ thể, cách quote đối số có thể khác; hãy bảo đảm **toàn bộ chuỗi pattern có dấu `;` được truyền thành một JVM option duy nhất**, vì dấu chấm phẩy trong shell có thể là ký hiệu phân tách câu lệnh.

Những ngưỡng cơ bản:

| Pattern | Ý nghĩa |
| --- | --- |
| `maxdepth=12` | Giới hạn độ sâu đồ thị khi deserialize |
| `maxrefs=5000` | Giới hạn số tham chiếu được theo dõi |
| `maxbytes=10485760` | Giới hạn số byte của serialization stream được theo dõi |
| `maxarray=100000` | Giới hạn độ dài một array, **không phải kích thước mọi collection** |
| `com.example.dto.**` | Cho phép class trong package và subpackage phù hợp |
| `java.base/java.util.*` | Cho phép class trực tiếp trong package `java.util` của module `java.base` |
| `!*` | Từ chối class còn lại khi được filter kiểm tra |

Filter tĩnh được cấu hình thông qua **system property `jdk.serialFilter`**; khi không có, JVM có thể dùng **security property cùng tên** từ cấu hình security. System property được ưu tiên so với security property theo thứ tự khởi tạo của `ObjectInputFilter.Config`.

Có thể thiết lập bằng API **nếu filter tĩnh chưa được đặt/cấu hình**:

```java
// Minh họa cho bootstrap của ứng dụng trên Java 17.
ObjectInputFilter global = ObjectInputFilter.Config.createFilter(
        "maxdepth=12;maxrefs=5000;maxbytes=10485760;maxarray=100000");
ObjectInputFilter.Config.setSerialFilter(global);
```

`setSerialFilter()` là cấu hình **một lần ở cấp JVM**, không phải lời gọi để bật/tắt theo request. Nếu filter đã được đặt, có thể nhận `IllegalStateException`; nếu một Security Manager/permissions của phiên bản đang chạy chặn thao tác, có thể nhận ngoại lệ quyền truy cập.

**Điều quan trọng:** Gọi `System.setProperty("jdk.serialFilter", "...")` sau khi cơ chế `ObjectInputFilter.Config` đã khởi tạo **không bảo đảm thay đổi bộ lọc tĩnh có hiệu lực**. Dùng JVM startup options hoặc bootstrap API đúng thời điểm.

### Cấu hình toàn JVM không giống “chắc chắn được áp dụng cùng per-stream”

Ở JDK 17, một **filter factory** được gọi để chọn filter cho từng stream. Built-in factory sử dụng global filter khi stream được tạo, nhưng khi code gọi `setObjectInputFilter(streamPolicy)`, built-in factory có thể **thay global filter bằng filter theo stream** thay vì tự động giao hai chính sách. Nếu chính sách bảo vệ toàn JVM bắt buộc luôn có hiệu lực, đừng chỉ suy diễn từ việc đã đặt `jdk.serialFilter`; cần kiểm soát/combine các filter ở factory phù hợp.

## 6. JDK 17: filter factory theo ngữ cảnh (JEP 415)

Từ JDK 17, `ObjectInputFilter.Config` có filter factory cấp JVM để chọn **một filter hiệu lực cho mỗi `ObjectInputStream`**. Nó có thể dựa trên filter toàn JVM, filter mà code yêu cầu cho stream và ngữ cảnh gọi (module, ThreadLocal, call stack, service owner…).

Đặt bằng API:

```java
// Phác thảo chỉ minh họa cách ghép hai filter.
// Phải đặt ở bootstrap, trước bất kỳ ObjectInputStream nào được tạo.
ObjectInputFilter.Config.setSerialFilterFactory((current, requested) -> {
    if (current == null) return requested;
    if (requested == null) return current;
    return ObjectInputFilter.merge(current, requested);
});
```

Đây là **đoạn minh họa cấu hình toàn JVM**, không phải chính sách production hoàn chỉnh. `current` là filter hiện tại của stream (hoặc `null` ở lúc khởi tạo), `requested` là filter mà constructor hoặc `setObjectInputFilter` yêu cầu. Nếu `current` đã khác `null`, factory không được trả `null` để vô hiệu hóa filter đang tồn tại.

Trong thực tế, cần có đội sở hữu cấu hình toàn JVM, kiểm thử hành vi merge/UNDECIDED/REJECTED, đo hiệu năng và đảm bảo policy per-stream không vô tình **nới rộng** hoặc **từ chối** các kiểu mà framework hợp lệ cần dùng.

Có thể cấu hình một class factory bằng startup property:

```bash
java -Djdk.serialFilterFactory=com.example.security.MyFilterFactory com.example.Main
```

Class factory này phải do ứng dụng cung cấp, **public**, có public constructor không tham số, triển khai `BinaryOperator<ObjectInputFilter>` và được class loader phù hợp nạp được. Thuộc tính `jdk.serialFilterFactory` là **tên lớp factory**, không phải chuỗi pattern. Nó khác với `jdk.serialFilter`.

Theo Java 17, cấu hình factory chỉ được thay đổi **một lần** và trước khi bất kỳ `ObjectInputStream` nào được tạo. Cấu hình qua system/security property có quyền ưu tiên và không thể bị tùy tiện thay thế sau đó bằng API. `System.setProperty("jdk.serialFilterFactory", "...")` sau khi Config đã khởi tạo **không tự bật factory mới**.

### Khi nào cần factory thay vì filter tĩnh?

Một ứng dụng nhiều module có thể nhận những loại payload khác nhau: thanh toán chỉ dùng `PaymentDTO`, lưu cache dùng `CacheSnapshot`, công cụ nội bộ dùng `MigrationRecord`. Một JVM-wide allowlist đủ rộng cho tất cả có thể tăng diện tấn công. Factory theo ngữ cảnh có thể ghép **giới hạn nền bắt buộc** với **allowlist cho đúng đầu vào**. Nhưng ngữ cảnh phải đáng tin, không lấy tên class tùy ý từ payload hay request của bên gửi làm chính sách tự cho phép.

## 7. Ví dụ Java 17 hoàn chỉnh: per-stream allowlist, chấp nhận một lớp và từ chối lớp khác

Chương trình này tự tạo hai serialization stream trong bộ nhớ. Nó không đọc dữ liệu từ mạng, nên an toàn để minh họa kiểm tra filter. Một lớp được cho phép là `AllowedMessage`, lớp còn lại là `BlockedMessage`. Mỗi lần đọc tạo **một ObjectInputStream riêng** và cài cùng một filter **trước `readObject()`**.

Tạo file **`SerialFilterDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public final class SerialFilterDemo {
    private SerialFilterDemo() {}

    private static final class AllowedMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int code;

        AllowedMessage(int code) {
            this.code = code;
        }

        int code() {
            return code;
        }
    }

    private static final class BlockedMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int code;

        BlockedMessage(int code) {
            this.code = code;
        }
    }

    private static byte[] encode(Serializable object) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static Object decode(byte[] bytes) throws Exception {
        ObjectInputFilter policy = ObjectInputFilter.Config.createFilter(
                "maxdepth=8;maxrefs=100;maxbytes=8192;maxarray=128;" +
                "SerialFilterDemo$AllowedMessage;!*");

        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(policy);
            return in.readObject();
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] valid = encode(new AllowedMessage(42));
        AllowedMessage accepted = (AllowedMessage) decode(valid);
        System.out.println("allowed=" + accepted.code());

        byte[] invalid = encode(new BlockedMessage(99));
        try {
            decode(invalid);
            System.out.println("unexpected acceptance");
        } catch (InvalidClassException expected) {
            System.out.println("rejected=InvalidClassException");
        }
    }
}
```

Biên dịch và chạy bằng **JDK 17**, với cấu hình JVM chuẩn không cài factory tùy biến:

```bash
javac SerialFilterDemo.java
java SerialFilterDemo
```

Kết quả dự kiến:

```text
allowed=42
rejected=InvalidClassException
```

**Tại sao kết quả như vậy?**

1. `encode` dùng `ObjectOutputStream` để biến instance hợp lệ thành byte array trong bộ nhớ; nó chưa đọc lại object.
2. `decode` tạo `ObjectInputStream` rồi cài filter bằng `setObjectInputFilter` trước khi đọc. Pattern giới hạn depth/references/bytes/array length và chỉ cho phép tên class binary `SerialFilterDemo$AllowedMessage`.
3. Phần `!*` ở cuối từ chối các class còn lại khi được kiểm tra. `AllowedMessage` thuộc tập được phép, nên `readObject()` trả về đối tượng có `code=42`.
4. `BlockedMessage` không khớp allowlist, nên khi deserialize bị `REJECTED`, thường thông qua `InvalidClassException`. Chương trình bắt đúng loại exception này và chỉ in tên exception, tránh phụ thuộc phần message/stacktrace có thể thay đổi theo JVM.
5. Vì class chỉ lưu một trường primitive `int`, ví dụ không cần allowlist thêm các class lồng nhau như `ArrayList` hay các kiểu DTO khác. Nếu bổ sung field tham chiếu serializable, phải cập nhật filter và kiểm thử lại object graph.

Đây là ví dụ minh họa cơ chế lọc, **không phải bộ lọc dùng nguyên mẫu cho dữ liệu thực tế**. Với môi trường có JVM-wide filter/factory do ứng dụng hoặc framework đặt, filter hiệu lực có thể khác và cần được kiểm tra trước khi kết luận kết quả luôn giống demo.

## 8. Pattern syntax và các bẫy cấu hình

Cú pháp pattern filter của `ObjectInputFilter.Config.createFilter` là tập các thành phần **phân tách bằng dấu chấm phẩy**. Với class pattern:

| Pattern | Ý nghĩa |
| --- | --- |
| `com.example.Foo` | Tên class đầy đủ chính xác |
| `com.example.*` | Những class trực tiếp trong package `com.example` |
| `com.example.**` | Những class trong package `com.example` và các package con |
| `com.example.Foo*` | Class có tên bắt đầu bằng tiền tố tương ứng |
| `java.base/java.util.*` | Class thuộc module `java.base` và package trực tiếp `java.util` |
| `!com.example.Dangerous` | Từ chối class này |
| `!*` | Từ chối mọi class còn lại có thể so khớp nếu đặt cuối danh sách |

Các giới hạn nhận `maxdepth`, `maxrefs`, `maxbytes`, `maxarray`. Nếu một giới hạn xuất hiện nhiều lần, giá trị **sau cùng** của giới hạn đó có hiệu lực. Whitespace là một phần của pattern và có thể làm tên không khớp; nên tránh khoảng trắng thừa khi cấu hình qua property.

Một số bẫy:

- **Thứ tự pattern quan trọng.** Nếu đặt `!*` trước allowlist, các class tiếp theo không còn cơ hội được cho phép.
- **Tên binary của lớp lồng nhau** dùng ký tự `$` (ví dụ `SerialFilterDemo$AllowedMessage`), không dùng tên nguồn `SerialFilterDemo.AllowedMessage`.
- **Pattern theo class khác kiểm tra nội dung.** Cho phép `java.util.ArrayList` không ngăn danh sách chứa dữ liệu nghiệp vụ sai hoặc có quá nhiều phần tử khi giới hạn nào đó không bao phủ đúng tình huống.
- **Giới hạn streamBytes không phải tổng ngân sách của tiến trình.** Mỗi `ObjectInputStream` có trạng thái theo dõi riêng; rất nhiều stream đồng thời vẫn có thể tiêu tốn nhiều tài nguyên.
- **UNDECIDED không tạo bảo vệ deny-by-default.** Nếu mong muốn cấm class lạ, phải thiết kế chính sách reject rõ ràng.
- **Không dùng filter kiểu "cho phép toàn bộ `java.base`" theo quán tính.** Mở rộng quyền đến nhiều class hơn nhu cầu thực tế.
- **Không đọc payload rồi mới cài filter.** Chọn chính sách trước operation đầu tiên cần được bảo vệ.

## 9. Phụ thuộc phiên bản JDK: JEP 290 và JEP 415

| Dòng JDK | Khả năng và lưu ý |
| --- | --- |
| Java 6/7/8 cũ trước các bản vá serialization filtering | Không thể giả định có API như JDK 9+ |
| Một số bản cập nhật JDK 6/7/8 từ đợt Critical Patch Update 01/2017 | Có backport cơ chế lọc; API cũ có thể nằm ở `sun.misc.ObjectInputFilter`, khác đường import chuẩn của JDK 9+ |
| JDK 9 (JEP 290) | Cơ chế `java.io.ObjectInputFilter`, filter theo stream và toàn JVM; giới hạn class/graph |
| JDK 11 | Sử dụng `java.io.ObjectInputFilter` nhưng không giả định API filter factory như JDK 17 |
| JDK 17 (JEP 415) | Bổ sung **context-specific filter factory**, `Config.setSerialFilterFactory` và `jdk.serialFilterFactory`; có helper API để ghép chính sách |
| Các JDK về sau | Kiểm tra tài liệu API, release notes, flags cấu hình và hành vi thực tế của bản JDK/vendor cụ thể; không suy đoán hoàn toàn giống JDK 17 |

Các mốc quan trọng:

- **JEP 290 – Filter Incoming Serialization Data**: được đưa vào Java 9; các bản vá JDK 8u121, 7u131, 6u141 từng nhận backport. Không tự động suy ra mã nguồn dùng `java.io.ObjectInputFilter` biên dịch nguyên trạng trên các JDK cũ: package/API ở các backport có khác biệt.
- **JEP 415 – Context-Specific Deserialization Filters**: được đưa vào JDK 17. Một chương trình cần `setSerialFilterFactory` nên khai báo và kiểm thử mức JDK tối thiểu là 17 (hoặc tính đến các backport đặc thù nếu vendor cung cấp, không mặc định).
- Các tham số khởi động và API cài filter phải được xác minh theo **JDK đích**, không chỉ JDK build ở máy phát triển. Khi chạy ứng dụng/framework trên nhiều JVM, cần kiểm thử cả trường hợp không có filter mặc định và trường hợp môi trường vận hành đặt sẵn filter/factory.

### Kiểm tra phiên bản chạy thực tế

```bash
java -version
javac -version
```

Không chỉ kiểm tra `java -version` của một terminal rồi tin rằng container, service, IDE hoặc máy chủ triển khai dùng đúng JVM đó. Theo dõi phiên bản và vendor của runtime thật sự chạy deserialize, đồng thời xem `jdk.serialFilter` / `jdk.serialFilterFactory` được truyền bằng startup arguments, system/security properties hay startup API.

## 10. Triển khai filter trong ứng dụng lớn

Một chính sách khả thi thường có các lớp bảo vệ bổ sung:

1. **Loại bỏ Java native serialization** ở điểm nhận payload không tin cậy khi có thể; dùng định dạng có schema và xác thực.
2. **Inventory các điểm deserialize:** `ObjectInputStream` trực tiếp và những thư viện/framework có thể dùng nó ngầm.
3. **Định nghĩa allowlist theo loại payload:** chỉ cho đúng class nghiệp vụ và phần phụ thuộc cần thiết, hạn chế package wildcard rộng.
4. **Giới hạn tài nguyên:** depth, references, array length, stream bytes; bổ sung giới hạn ở tầng vận chuyển, thời gian xử lý, số lượng request/stream đồng thời.
5. **Đặt chính sách sớm:** global tại khởi động, per-stream trước `readObject()`, factory trước khi tạo ObjectInputStream đầu tiên.
6. **Kiểm thử kết hợp:** xác minh filter JVM và stream không vô tình thay thế/nới rộng nhau; thử payload hợp lệ, lớp lạ, mảng lớn, đồ thị sâu, EOF/corruption.
7. **Xử lý lỗi có chủ đích:** khi `InvalidClassException` hoặc `IOException` xảy ra, đóng stream và báo dữ liệu không hợp lệ; không tự đọc lại bằng filter rộng hơn để "cố tương thích".
8. **Logging/monitoring:** ghi lại class bị chặn hoặc nguyên nhân giới hạn khi an toàn, không log thẳng dữ liệu bí mật hay toàn bộ bytes serialization.
9. **Kiểm soát version drift:** trong pipeline build/deploy, kiểm tra lớp nào đã thay đổi, dependency update có phát sinh class hợp lệ mới và chính sách có còn đúng.
10. **Threat modeling và review:** filter có giới hạn, không thay thế kiểm soát quyền, xác thực, giới hạn bộ nhớ và tránh gadget deserialization.

Trong ứng dụng nhiều nhóm cùng chạy trên một JVM, cấu hình `setSerialFilter` hoặc `setSerialFilterFactory` là tài nguyên **process-wide**: mỗi thư viện không thể tùy tiện thiết lập giá trị riêng một lần nữa. Tốt hơn là bộ phận sở hữu bootstrap chọn chiến lược hợp nhất và cung cấp giao diện cấu hình cho các module.

## 11. Lỗi thường gặp và cách sửa

| Lỗi | Hệ quả | Hướng khắc phục |
| --- | --- | --- |
| Đặt filter cho một ObjectInputStream sau khi đã deserialize | Có thể đã xử lý lớp nguy hiểm trước khi bảo vệ | Đặt trước lần `readObject()` đầu tiên |
| Cho rằng `UNDECIDED` bằng từ chối | Lớp không khớp vẫn có thể được đọc | Allowlist với quy tắc reject phần còn lại |
| Cấu hình `jdk.serialFilter` nhưng stream-specific filter thay thế nó | Một số ngưỡng baseline không còn hiệu lực theo default factory | Dùng factory phối hợp và kiểm thử filter hiệu lực |
| Coi `jdk.serialFilterFactory` là một pattern string | Không nạp được factory | Cung cấp tên class triển khai `BinaryOperator<ObjectInputFilter>` |
| Gọi `setSerialFilterFactory` sau khi đã tạo ObjectInputStream | Có thể ném `IllegalStateException` | Cấu hình từ bootstrap |
| Dùng `System.setProperty` sau lúc Config khởi tạo | Tưởng rằng thay chính sách đã chạy nhưng không có hiệu lực | Dùng startup property hoặc API đúng thời điểm |
| Sao chép code JDK 17 sang JDK 8 | Lỗi import/API hoặc thiếu phương thức | Kiểm tra bản JDK/vendor và khác biệt backport |
| Chỉ cho DTO nhưng quên array/nested/collection hợp lệ | `InvalidClassException` với input hợp lệ | Kiểm thử toàn bộ graph và các trường hợp biên |
| Mở `java.base/*` quá rộng không cân nhắc | Cho nhiều class hơn nhu cầu | Giới hạn class/module theo trust model |
| Chỉ đặt `maxarray` và nghĩ đã giới hạn RAM tổng | Có thể gặp nhiều object/stream hoặc cấu trúc không phải array | Kết hợp maxrefs/maxdepth/maxbytes và hạn mức ngoài stream |
| Bắt `InvalidClassException` rồi deserialize lại không filter | Mất hàng rào bảo vệ | Reject payload, cảnh báo và đóng stream |
| Tin filter thay được xác thực đầu vào | Gadget và lỗi dữ liệu nghiệp vụ vẫn có thể xảy ra | Tránh untrusted native deserialization, validate cấu trúc và nguồn |

## 12. Câu hỏi tự kiểm tra

**1. Khi nào nên chọn stream-specific filter?**  
Khi biết chính xác tập class hợp lệ của một đầu vào hoặc một loại dữ liệu; tạo `ObjectInputStream`, áp dụng filter trước lần đọc và xác minh tương tác với chính sách JVM-wide.

**2. Nếu muốn bảo vệ mọi điểm tạo ObjectInputStream trong JVM mà không sửa từng call site, dùng gì?**  
Có thể cấu hình `jdk.serialFilter` lúc khởi động. Tuy nhiên phải xác minh việc kết hợp với per-stream filter/factory của ứng dụng; không tự coi global filter luôn là lớp "bắt buộc" nếu factory cho phép thay thế.

**3. Tại sao pattern `com.example.**` chưa chắc là allowlist nghiêm ngặt?**  
Những class không khớp có thể nhận `UNDECIDED`. Nếu cần deny-by-default, bổ sung pattern từ chối phù hợp (chẳng hạn `!*` cuối danh sách) và kiểm thử.

**4. `maxarray` có ngăn được mọi collection khổng lồ không?**  
Không. Nó giới hạn độ dài array được kiểm tra, không trực tiếp biểu diễn kích thước mọi collection hay tổng RAM của nhiều stream.

**5. Có thể gọi `setSerialFilterFactory` trên JDK 11 chuẩn như JDK 17 không?**  
Không nên: API context-specific factory được bổ sung ở JDK 17. Với các runtime cũ, sử dụng khả năng được hỗ trợ ở JDK đích hoặc nâng cấp phiên bản khi thiết kế đòi hỏi factory.

**Ghi nhớ:** Filter theo stream giúp hạn chế đúng lớp và quy mô dữ liệu của **một ObjectInputStream**; `jdk.serialFilter` đặt chính sách toàn JVM; từ JDK 17, `jdk.serialFilterFactory` và `setSerialFilterFactory` quyết định cách chọn/kết hợp chúng. Nắm rõ `ALLOWED/REJECTED/UNDECIDED`, thời điểm đặt filter, cơ chế factory và phiên bản JDK là điều bắt buộc nếu dùng Java serialization trong hệ thống có yêu cầu bảo mật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-serialization-012">Không khôi phục trực tiếp dữ liệu đối tượng không tin cậy</a>

<details>
<summary>Xem chi tiết</summary>

# Không khôi phục trực tiếp dữ liệu đối tượng không tin cậy trong Java

## 1. Bản chất vấn đề: byte lạ không phải đối tượng an toàn

Java Object Serialization cho phép ghi một đồ thị đối tượng bằng `ObjectOutputStream.writeObject(...)` và đọc lại bằng `ObjectInputStream.readObject()`. Nhưng **đọc lại object stream không đơn thuần là giải mã dữ liệu**: JVM phải xác định class từ mô tả trong stream, dựng object graph, khôi phục trường, duy trì reference handles và có thể gọi các cơ chế đặc biệt của class.

Một antipattern rất nguy hiểm là:

```java
// KHÔNG LÀM với bytes đến từ nguồn không tin cậy.
try (ObjectInputStream in = new ObjectInputStream(networkInput)) {
    Object value = in.readObject();
    if (value instanceof ExpectedMessage) {
        process((ExpectedMessage) value);
    }
}
```

Đây là **mã minh họa không an toàn**, không phải chương trình độc lập; `networkInput`, `ExpectedMessage` và `process` giả định có sẵn. Kiểm tra `instanceof` diễn ra **sau khi `readObject()` đã làm công việc giải tuần tự hóa**, nên không phải biện pháp chặn các class không mong muốn được xử lý trong quá trình đọc.

**Nguyên tắc chính của bài:** *Không khôi phục trực tiếp Java objects từ dữ liệu không tin cậy.* Ưu tiên một định dạng dữ liệu có cấu trúc/schema rõ ràng và bộ đọc giới hạn kích thước, kiểu và tài nguyên; chuyển thành DTO hoặc domain object **sau khi** kiểm tra input. Nếu bắt buộc tương thích với serialized data tin cậy của hệ thống cũ, áp dụng bộ lọc class/độ sâu/size, xác thực nguồn, giới hạn tài nguyên và validation theo ngữ cảnh — nhưng đừng gọi đó là “an toàn tuyệt đối”.

### 1.1. “Không tin cậy” bao gồm những nguồn nào?

Không chỉ là request HTTP đến từ Internet:

- Byte lấy từ socket, message queue hoặc dịch vụ mà bên gửi có thể bị chiếm quyền.
- File upload, file tải từ URL, email attachment, nội dung do người dùng chọn.
- Cache/object blob lưu trong bucket, database, queue hoặc filesystem mà bên ghi không hoàn toàn nằm trong trust boundary của tiến trình đọc.
- Dữ liệu cũ do phiên bản chương trình khác ghi ra nhưng chưa có tính toàn vẹn, chữ ký, kiểm soát nguồn hoặc kế hoạch compatibility rõ ràng.
- Nội dung ở môi trường multi-tenant, nơi một tenant có thể tác động file/cache của tenant khác.
- Bất cứ byte stream nào mà đối phương có thể thay thế, cắt ngắn, sửa một phần hoặc phát lại.

Một file mang đuôi `.ser` hay bắt đầu bằng `AC ED 00 05` mới chỉ cho biết nó **trông giống Java serialization stream**. Nó không chứng minh bên ghi đáng tin, object graph hợp lệ hay class sẽ được nạp an toàn.

## 2. Vì sao `readObject()` có bề mặt tấn công lớn?

Khi `readObject()` xử lý một stream, nó có thể:

1. Đọc tên class và các **class descriptors** có trong stream.
2. Yêu cầu JVM tìm/khôi phục các class tương ứng trên classpath/module path.
3. Tạo các đối tượng và cấu trúc tham chiếu theo cơ chế Java serialization.
4. Khôi phục non-transient fields, kể cả các object lồng nhau trong đồ thị.
5. Chạy logic đặc biệt liên quan serialization, chẳng hạn private `readObject`, `readResolve` hoặc các callback validation theo cơ chế tương ứng.
6. Cấp phát mảng, object graph và những cấu trúc có thể lớn hơn nhiều so với kỳ vọng của người gọi.
7. Gặp lỗi loại dữ liệu, thiếu class, vượt giới hạn hoặc stream không hợp lệ **trong khi** quá trình khôi phục đang diễn ra.

Một **gadget chain** là chuỗi hành vi của những class đã có sẵn trong ứng dụng/thư viện có thể bị dữ liệu được deserialize kích hoạt theo cách ngoài dự định của developer. Rủi ro không đòi hỏi class tấn công phải được compile mới tại runtime; những class nằm sẵn trong classpath cũng có thể mang các side effects nguy hiểm khi được xử lý qua serialization.

Bài này **không trình bày payload hoặc hướng khai thác gadget chain**. Điều cần nắm là: kiểm tra kiểu đối tượng sau `readObject` không chặn được mọi hành vi có thể xảy ra *trước khi method trả về*.

### 2.1. Ba nhóm hậu quả phải phòng vệ

| Nhóm | Rủi ro ví dụ | Hướng phòng vệ |
|---|---|---|
| Thực thi logic không mong muốn | Hook/class graph có side effects hoặc hành vi không được dự tính | Tránh native deserialization; giới hạn class đã cho phép; thu hẹp classpath |
| Cạn kiệt tài nguyên | Đồ thị sâu, quá nhiều references, mảng lớn, payload kéo dài | Giới hạn bytes ở tầng transport; depth/references/array; quota CPU/thời gian/bộ nhớ |
| Phá invariant nghiệp vụ | Trường null/sai phạm vi, đối tượng ở trạng thái không thể tạo qua constructor, references không mong đợi | Parse theo schema; validate từng trường; constructor/factory kiểm tra invariant; không tin các field sau deserialize |

Định dạng có schema như JSON/CBOR/Protocol Buffers giúp **không tự động tạo graph Java tùy ý theo class name của stream** nếu triển khai đúng, nhưng **không tự miễn nhiễm** với payload sâu, input quá lớn, lỗi cấu hình kiểu đa hình hoặc thư viện parser có lỗ hổng. Chuyển định dạng là một phần của giải pháp, không phải lý do bỏ validation.

## 3. Phân biệt deserialize, parse và validate

Một tuyến dữ liệu bảo vệ tốt nên tách các bước:

```text
[Input không tin cậy]
        │
        ▼
Giới hạn kích thước/timeout/định dạng ở tầng nhận
        │
        ▼
Parse với schema/format cố định; không tạo class tùy ý
        │
        ▼
Kiểm tra phiên bản, kiểu trường, length, range, UTF-8, invariant
        │
        ▼
DTO dữ liệu thuần đã kiểm tra
        │
        ▼
Constructor/factory của domain object (quyền tối thiểu)
        │
        ▼
Tác vụ nghiệp vụ
```

**Parse** chỉ biến bytes thành representation có kiểu được xác định trước. **Validation** đảm bảo representation đó hợp lệ theo mục tiêu nghiệp vụ. **Deserialization Java object stream** có thêm khả năng dựng object graph và kích hoạt custom object logic; đó là lý do nó không phải parser nhập liệu tổng quát dành cho những nguồn đối kháng.

Một contract an toàn hơn có thể là:

```java
record IncomingMessage(String text, int sequence) {}

static IncomingMessage validate(String text, int sequence) {
    if (text == null || text.isBlank() || text.length() > 100) {
        throw new IllegalArgumentException("Invalid text");
    }
    if (sequence < 0) {
        throw new IllegalArgumentException("Negative sequence");
    }
    return new IncomingMessage(text, sequence);
}
```

Đây là **snippet minh họa** đặt trong class Java 17+; không tự đọc bytes và không có `main`. Ngay cả khi parser đã xác định `String` và `int`, vẫn phải kiểm tra phạm vi, trường bắt buộc và invariant.

## 4. Giải pháp ưu tiên: định dạng dữ liệu nhỏ, giới hạn rõ, không `readObject`

Nếu giao thức do ứng dụng kiểm soát, thường nên chuyển một record dữ liệu cố định thay vì Java object graph. Ví dụ frame:

```text
Offset   Kích thước   Nội dung
0        4 byte       magic = 0x4D534731 ("MSG1")
4        1 byte       protocolVersion = 1
5        4 byte       sequence (signed int, big-endian)
9        2 byte       textByteLength (unsigned short, big-endian)
11       N byte       text UTF-8, N <= 256
```

Lợi ích:

- Type cố định: chỉ có `sequence` và `text`, không có class name do input tự chọn.
- Bytes tối đa của chuỗi và frame đều có giới hạn.
- Decoder UTF-8 có thể cấu hình từ chối bytes sai cấu trúc.
- Protocol version do ứng dụng quản lý, độc lập với `serialVersionUID`/Object Serialization Stream Protocol.
- Có thể viết validator kiểm tra dữ liệu trước khi tạo domain object.

**Giới hạn quan trọng:** nếu đầu vào đến từ network stream, cần **cắt/giới hạn bytes ngay từ lúc nhận**, trước khi tạo mảng lớn. Việc chỉ kiểm tra `byte[].length` sau khi đã vô tình đọc toàn bộ một file 20 GB vào RAM là quá muộn đối với rủi ro cạn kiệt bộ nhớ.

## 5. Ví dụ Java 17+ chạy được: parse một frame rõ cấu trúc

Ví dụ không dùng `ObjectInputStream`. Nó ghi và đọc một frame có length bound, magic, version và UTF-8 strict. Input sai length bị từ chối **trước khi cấp phát mảng payload**.

**Tệp:** `SafeFrameDemo.java`

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class SafeFrameDemo {

    public record Message(String text, int sequence) {}

    public static byte[] encode(Message message) throws IOException {
        byte[] body = message.text()
                .getBytes(StandardCharsets.UTF_8);

        if (body.length > 256) {
            throw new IOException("text too long");
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x4D534731);   // "MSG1"
            out.writeByte(1);           // protocol version
            out.writeInt(message.sequence());
            out.writeShort(body.length);
            out.write(body);
        }
        return bytes.toByteArray();
    }

    public static Message decode(byte[] frame) throws Exception {
        // Giới hạn tổng bytes trước khi parse.
        if (frame.length < 11 || frame.length > 512) {
            throw new IOException("invalid frame size");
        }

        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(frame))) {

            if (in.readInt() != 0x4D534731) {
                throw new IOException("invalid magic");
            }
            if (in.readUnsignedByte() != 1) {
                throw new IOException("unknown version");
            }

            int sequence = in.readInt();
            int length = in.readUnsignedShort();

            if (length > 256) {
                throw new IOException("invalid length");
            }

            byte[] body = new byte[length];
            in.readFully(body); // Thất bại nếu frame thiếu byte.

            // available() chỉ được dùng để tìm trailing bytes ở đây
            // vì nguồn là ByteArrayInputStream đã có đủ frame.
            if (in.available() != 0) {
                throw new IOException("trailing bytes");
            }

            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString();

            if (text.isBlank() || text.length() > 100
                    || sequence < 0) {
                throw new IOException("invalid message fields");
            }

            return new Message(text, sequence);
        }
    }

    public static void main(String[] args) throws Exception {
        Message message = decode(
                encode(new Message("HELLO", 7)));

        System.out.println("message: " + message.text()
                + ", seq=" + message.sequence());

        byte[] invalid = encode(new Message("A", 7));

        // Hai byte độ dài ở offset 9..10 trở thành 0x03E7 = 999.
        invalid[9] = 3;
        invalid[10] = (byte) 0xE7;

        try {
            decode(invalid);
        } catch (IOException ex) {
            System.out.println("invalid length: rejected");
        }
    }
}
```

Biên dịch/chạy trên JDK 17+:

```bash
javac SafeFrameDemo.java
java SafeFrameDemo
```

**Stdout mong đợi:**

```text
message: HELLO, seq=7
invalid length: rejected
```

### 5.1. Vì sao thiết kế này hạn chế rủi ro?

1. Parser biết trước frame chỉ gồm magic, version, sequence, độ dài và UTF-8 bytes; không có chỗ cho input yêu cầu JVM tạo class Java bất kỳ.
2. `readUnsignedShort` làm rõ length là unsigned 16-bit, nhưng **vẫn kiểm tra ≤256** trước khi tạo `byte[]`, không tin vào length trong dữ liệu.
3. `readFully` từ chối frame thiếu dữ liệu thay vì dùng partial text âm thầm.
4. `ByteArrayInputStream` có frame đã nhận đủ, nên kiểm tra `available()!=0` ở **trường hợp cụ thể này** để phát hiện trailing bytes. Không áp dụng `available()` như thước đo chính xác tổng độ dài network stream.
5. `CharsetDecoder` với `CodingErrorAction.REPORT` từ chối UTF-8 không hợp lệ, thay vì đổi nó thành ký tự `�` mà không báo.
6. Sau khi decode, các quy tắc nghiệp vụ tối thiểu (`text` không rỗng, giới hạn số ký tự, `sequence` không âm) được kiểm tra **trước khi** tạo record.
7. Dữ liệu lỗi chỉ gây `IOException`/lỗi decode hợp lệ, không điều khiển class loading tùy ý qua Object Serialization.

**Phạm vi ví dụ:** input của method là một `byte[]` đã có; với socket thực cần tầng framing và **giới hạn tổng byte nhận vào**, timeout và xử lý kết nối trước khi gọi `decode`. Ví dụ không có authentication, replay protection hay integrity checksum; các yêu cầu đó phải được xây riêng theo threat model.

### 5.2. Giới hạn UTF-8 bytes khác giới hạn độ dài String

`body.length` là số **byte** sau encode UTF-8. `text.length()` là số UTF-16 code units; hai số có thể khác nhau, nhất là với tiếng Việt và emoji. Một chuẩn protocol nên nêu rõ giới hạn theo byte hay code point và kiểm tra đúng đơn vị.

Nếu text phải mang giá trị nhạy cảm hoặc phải được ký số, cần xác định canonicalization/normalization và cách encode, thay vì trông chờ parser tự chuẩn hóa Unicode.

### 5.3. Nếu chọn JSON thì vẫn phải ràng buộc schema

Một hướng khác là gửi JSON như:

```json
{"version":1,"sequence":7,"text":"HELLO"}
```

Parser JSON nên chỉ đọc vào cấu trúc dữ liệu đã biết, giới hạn depth/bytes/string length/number ranges, **không bật tính năng polymorphic class resolution mặc định** cho dữ liệu người dùng. Sau parse, dùng constructor/factory validate DTO. Nếu library cung cấp cơ chế gắn class name trong payload để tự tạo subclass, việc bật bừa các tính năng ấy có thể tái tạo rủi ro “type do input tự quyết định” dưới định dạng khác.

## 6. Khi nào việc dùng ObjectInputStream vẫn được cân nhắc?

Ví dụ ứng dụng phải đọc các file legacy do **chính hệ thống đã kiểm soát** ghi bằng `ObjectOutputStream` và cần tương thích trong giai đoạn chuyển đổi. Trước khi quyết định tiếp tục, trả lời:

- Đường đi từ nơi ghi tới nơi đọc có bảo vệ **tính toàn vẹn và xác thực nguồn** không?
- Có bất kỳ actor ít tin cậy nào có thể thay file/blob trên filesystem, database, queue hoặc backup không?
- Những class nào thực sự cần được dựng khi đọc một record?
- Từng class có hooks `readObject`/`readResolve`/`readExternal` hoặc side effects nào không?
- Có đặt upper bound về bytes, graph depth, số references, kích thước arrays và thời gian xử lý không?
- Có cần đọc cả graph tùy ý, hay chỉ cần 2–5 field có thể chuyển sang DTO?
- Nếu filter từ chối một file hợp lệ cũ, có quy trình báo lỗi/migration rõ không?

Ngay cả với dữ liệu nội bộ, việc lộ file hoặc thay đổi quyền ghi có thể biến “trusted local file” thành một attack surface. Trust là thuộc tính của **toàn bộ tuyến dữ liệu**, không phải của tên file.

## 7. `ObjectInputFilter`: giới hạn class và tài nguyên, không biến dữ liệu lạ thành an toàn

Trong JDK 17/21, `ObjectInputFilter` hỗ trợ kiểm soát quá trình khôi phục Java objects. Nó có thể đánh giá:

- Class được yêu cầu dựng khi filter được gọi.
- Độ sâu đồ thị (depth).
- Số references đã xử lý.
- Độ dài mảng.
- Số bytes đã tiêu thụ theo thông tin của filter.

Ba trạng thái trả về:

| `ObjectInputFilter.Status` | Ý nghĩa |
|---|---|
| `ALLOWED` | Điều kiện được filter này cho phép; không thay thế validation |
| `REJECTED` | Từ chối; active `readObject`/`readUnshared` thường kết thúc bằng `InvalidClassException` |
| `UNDECIDED` | Filter này không quyết định; **không đồng nghĩa REJECTED** |

Một rủi ro lớn là viết filter cho class nguy hiểm trả `UNDECIDED` rồi tưởng đã deny. Nếu có yêu cầu allowlist, phải có **chính sách reject rõ ràng** cho class ngoài danh sách. Cùng lúc cần tính tới lớp/tham chiếu/array/strings mà stream thực sự chứa.

### 7.1. `setObjectInputFilter` phải đặt trước khi đọc object

```java
try (ObjectInputStream in = new ObjectInputStream(source)) {
    in.setObjectInputFilter(filter);
    Object value = in.readObject();
}
```

Đây là snippet minh họa; `source` là `InputStream`, `filter` đã tạo, có import và xử lý `IOException`/`ClassNotFoundException`. Bộ lọc phải được thiết lập **trước lần đọc object**, không phải sau `readObject`. Trong Java 17/21, `setObjectInputFilter` chỉ có thể gọi theo quy tắc một lần trước khi đọc object, và tác dụng filter cuối cùng còn chịu cơ chế **filter factory** của JVM.

**Lưu ý rất quan trọng:** bộ lọc không được gọi cho mọi giá trị primitive và không phải biện pháp kiểm tra **nội dung** của String/field. Tài liệu `ObjectInputStream` nêu rõ các String được xử lý như primitive và không kích hoạt bộ lọc class tương ứng theo cách regular object. Vì vậy dù chỉ allow class DTO, vẫn phải kiểm tra chiều dài và nội dung các trường văn bản **sau khi khôi phục** và **trước khi dùng**.

### 7.2. Filter theo pattern

`ObjectInputFilter.Config.createFilter(...)` hỗ trợ chính sách dạng chuỗi:

```java
ObjectInputFilter filter =
        ObjectInputFilter.Config.createFilter(
            "maxdepth=5;maxrefs=32;maxarray=256;maxbytes=8192;"
          + "com.example.legacy.dto.*;!*");
```

Đây là ví dụ cấu hình policy, **không phải một chương trình độc lập**. `com.example.legacy.dto.*` chỉ là namespace minh họa; triển khai thật phải kiểm kê chính xác các class cần cho serialized graph, các arrays và các type thuộc thư viện/framework. Mẫu `!*` là deny class chưa khớp allowlist. Đừng chọn rộng `java.base/*` hoặc `java.*` chỉ vì “đó là Java standard library” nếu không cần thiết; một allowlist có nghĩa chỉ cho phép kiểu thật sự cần thiết.

Một policy quá chặt có thể từ chối cả dữ liệu hợp lệ; một policy quá rộng có thể để lại gadget surface. Cần kiểm thử với các serialized fixtures thực, không chỉ tin rằng filter string nhìn “có vẻ an toàn”.

### 7.3. Filter dựa trên class cụ thể

Có thể viết `ObjectInputFilter` theo class và các giới hạn thay vì pattern tổng quát:

```java
ObjectInputFilter filter = info -> {
    if (info.depth() > 5
            || info.references() > 20
            || info.streamBytes() > 4096
            || info.arrayLength() > 128) {
        return ObjectInputFilter.Status.REJECTED;
    }

    Class<?> type = info.serialClass();

    // Những callback liên quan reference/metrics có type null.
    if (type == null) {
        return ObjectInputFilter.Status.UNDECIDED;
    }

    if (type == TrustedRecord.class) {
        return ObjectInputFilter.Status.ALLOWED;
    }

    return ObjectInputFilter.Status.REJECTED;
};
```

Đây là snippet với một `TrustedRecord` serializable do hệ thống tin cậy quản lý. Nếu toàn bộ graph gồm những class khác, phải thiết kế allowlist phù hợp; đừng copy nguyên mẫu mà kỳ vọng đọc được mọi `List`, `Map`, array và nested DTO. Các metrics do filter cung cấp cũng không thay thế giới hạn kích thước input ở tầng receive.

## 8. Ví dụ Java 17+ chạy được: đọc dữ liệu legacy tin cậy với filter

Ví dụ này **không nhận dữ liệu do đối phương kiểm soát**. Nó tự ghi hai payload trong bộ nhớ: một `Allowed` hợp lệ và một `Unexpected` không thuộc danh sách. Reader giới hạn bytes trước khi khởi tạo `ObjectInputStream`, cài filter **trước `readObject()`**, cho phép chính xác class `Allowed`, kiểm tra graph metrics và validate field sau khi đọc.

**Tệp:** `FilteredLegacyDemo.java`

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class FilteredLegacyDemo {

    static final class Allowed implements Serializable {
        private static final long serialVersionUID = 1L;
        final String name;

        Allowed(String name) {
            this.name = name;
        }
    }

    static final class Unexpected implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(obj);
        }

        return bytes.toByteArray();
    }

    static Allowed readTrusted(byte[] wire)
            throws IOException, ClassNotFoundException {

        // Đây là guard của demo; production phải limit cả tầng đọc.
        if (wire.length > 4096) {
            throw new IOException("frame too large");
        }

        ObjectInputFilter filter = info -> {
            if (info.depth() > 5
                    || info.references() > 20
                    || info.streamBytes() > 4096
                    || info.arrayLength() > 128) {
                return ObjectInputFilter.Status.REJECTED;
            }

            Class<?> type = info.serialClass();

            if (type == null) {
                return ObjectInputFilter.Status.UNDECIDED;
            }

            return type == Allowed.class
                    ? ObjectInputFilter.Status.ALLOWED
                    : ObjectInputFilter.Status.REJECTED;
        };

        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(wire))) {

            in.setObjectInputFilter(filter);
            Object obj = in.readObject();

            if (obj.getClass() != Allowed.class) {
                throw new InvalidObjectException("unexpected type");
            }

            Allowed value = (Allowed) obj;

            if (value.name == null || value.name.length() > 40) {
                throw new InvalidObjectException("invalid name");
            }

            return value;
        }
    }

    public static void main(String[] args) throws Exception {
        Allowed record = readTrusted(
                serialize(new Allowed("trusted")));

        System.out.println("name: " + record.name);

        try {
            readTrusted(serialize(new Unexpected()));
        } catch (InvalidClassException ex) {
            System.out.println("unexpected class: rejected");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac FilteredLegacyDemo.java
java FilteredLegacyDemo
```

**Stdout:**

```text
name: trusted
unexpected class: rejected
```

### 8.1. Những điều ví dụ thật sự kiểm tra

- `Allowed` và `Unexpected` là **hai class test do chính ví dụ định nghĩa**; không có payload khai thác hay input ngoài.
- `readTrusted` kiểm tra tổng chiều dài của byte array **trước khi** dựng ObjectInputStream.
- Filter có giới hạn tài nguyên và **từ chối class bất kỳ khác Allowed**.
- `in.setObjectInputFilter(filter)` xảy ra **trước** `in.readObject()`.
- Sau đọc, code kiểm tra class **chính xác** và trường `name` có giới hạn.
- Trường `String name` vẫn cần validation vì filter không tự kiểm tra từng giá trị chuỗi.
- Đối tượng `Unexpected` bị filter từ chối, dẫn tới `InvalidClassException` được bắt có chủ đích.

### 8.2. Điều ví dụ không chứng minh

**Không** được dùng ví dụ này làm bằng chứng rằng mọi Java object stream từ Internet an toàn nếu gắn cùng filter. Filter có phạm vi giới hạn; class được cho phép vẫn phải được audit hooks/side effects, các graph phức tạp có thể cần chính sách khác, và input source/authentication/timeouts đều nằm ngoài ví dụ.

Một thay đổi class `Allowed` trong tương lai có thể thêm field mới hoặc custom `readObject`. Việc nó vẫn trùng allowlist class name **không phải** bằng chứng code đó an toàn trước dữ liệu được đối phương thiết kế. Cần code review và kiểm thử đầy đủ cho các class nằm trong allowlist.

### 8.3. Vì sao kết quả của self-test có tính xác định?

Cả hai payload được sinh bởi `ObjectOutputStream` trong cùng process nên class descriptors tương ứng với class local; không phụ thuộc file hay mạng. Kết quả chỉ thể hiện policy class cho hai object nhỏ. Nếu JVM đã cấu hình một filter factory toàn hệ thống mang policy chặt hơn, có thể có thêm hành vi từ chối theo policy đó; đây là điều cần test ở môi trường triển khai.

## 9. Filter không phải là “khử độc” mọi stream

Có vài giới hạn phải hiểu chính xác:

| Khẳng định sai | Sự thật |
|---|---|
| “Filter kiểm tra tất cả field values.” | Filter biết class/graph metrics tại các điểm callback; không phải validator schema/nội dung |
| “Chỉ cần `instanceof` sau `readObject`.” | Quá trình khôi phục đã xảy ra trước đó |
| “Class có trong allowlist thì luôn an toàn.” | Chính class đó hoặc những hoạt động liên quan vẫn có thể có side effects và bug |
| “Cứ trả `UNDECIDED` là từ chối.” | UNDECIDED có nghĩa chưa quyết định; phải cấu hình reject policy |
| “Giới hạn `maxbytes` bảo đảm request không tiêu thụ RAM ngoài filter.” | Filter hoạt động trong lúc deserialize; cần giới hạn bytes sớm và quota hệ thống |
| “Một filter global đủ cho mọi use case.” | Mỗi endpoint/context có set class/graph khác nhau, cần policy chuyên biệt |
| “String luôn đi qua filter để kiểm tra length.” | String/primitive có cách xử lý khác; cần validation payload riêng |
| “JSON/CBOR không bao giờ gây DoS.” | Parser khác cũng cần giới hạn tài nguyên và kiểm tra schema |

### 9.1. Global filter và context-specific filter factory

Java hỗ trợ JVM-wide serialization filter bằng system/security property như `jdk.serialFilter`, hoặc API cấu hình. Với Java 17+, cơ chế filter factory cho phép xây dựng chính sách theo context sử dụng từng `ObjectInputStream`.

Ví dụ cấu hình process **minh họa**:

```bash
java -Djdk.serialFilter="maxdepth=10;maxrefs=100;maxarray=1000;maxbytes=65536;com.example.dto.*;!*" MyApp
```

`MyApp` và `com.example.dto.*` chỉ là placeholder ngữ cảnh triển khai, **không phải lệnh để chạy ngay**. Một filter toàn JVM có thể ảnh hưởng tới các library sử dụng serialization nội bộ; trước khi đặt policy phải kiểm kê các use case và test thực tế. Không bật `java.base/*` toàn cục chỉ để tránh lỗi class allowlist nếu điều đó mở phạm vi không cần.

### 9.2. Thứ tự chọn filter có ý nghĩa

`ObjectInputStream` có thể nhận filter ban đầu từ **filter factory** lúc constructor chạy; gọi `setObjectInputFilter` có thể kích hoạt factory một lần nữa để chọn/kết hợp filter. Nếu ứng dụng thay filter factory tùy ý hoặc tạo policy yếu hơn, có nguy cơ làm mất bảo vệ mà một thư viện khác kỳ vọng.

Vì vậy:

- Xác định vị trí quản lý filter factory ở cấp ứng dụng.
- Không để các module tự ý thay global filter factory trong quá trình runtime.
- Với mỗi luồng dữ liệu, dùng filter thích hợp với **context** và audit chính sách effective.
- Test cả trường hợp có filter global và filter per-stream.
- Ghi log việc một stream bị từ chối mà không ghi toàn bộ dữ liệu nhạy cảm.

### 9.3. Filter có thể có lỗ hổng thiết kế trong chính policy

Ví dụ chỉ kiểm tra class root:

```java
Object root = in.readObject();
if (root.getClass() != Allowed.class) {
    throw new SecurityException("wrong root");
}
```

Đây là snippet **không an toàn nếu đứng riêng**, vì nested graph có thể đã được xử lý trước khi kiểm tra root. Một filter thích hợp cần giới hạn **các class có thể xuất hiện trong toàn graph** và giới hạn tài nguyên, không chỉ type top-level. Và ngay sau đó, vẫn phải validate invariant của root lẫn nested data.

## 10. Xác thực nguồn và tính toàn vẹn: cần nhưng không đủ

Một hệ thống có thể bảo vệ data bằng mTLS, HMAC, chữ ký số hoặc storage ACL. Những cơ chế này giúp xác nhận bytes đến từ nguồn hợp lệ hoặc không bị sửa mà không được phát hiện. Nhưng:

- Nguồn được xác thực cũng có thể bị chiếm quyền hoặc có bug ghi ra dữ liệu không hợp lệ.
- Một blob hợp lệ có thể chứa quá nhiều object/bytes vượt quota của người đọc.
- Một file cũ có chữ ký vẫn có thể không tương thích class hiện tại.
- Integrity không tự làm custom `readObject` của class an toàn.
- Chính sách xác thực phải xét replay, key rotation, thời hạn và định danh phiên bản nếu nghiệp vụ yêu cầu.

Vì vậy authentication/integrity chỉ là **một lớp**. Không thay thế input validation, allowlist, resource bounds và thiết kế dữ liệu nhỏ.

## 11. Serialization hooks và constructor bypass

Một Serializable class khi được khôi phục không chạy constructor của **chính serializable class** như khi gọi `new` thông thường. Việc khôi phục field có thể diễn ra trước validation nghiệp vụ. Những cơ chế như:

- private `readObject(ObjectInputStream)`;
- `readResolve()`;
- `readObjectNoData()`;
- `ObjectInputValidation` callbacks;
- `Externalizable.readExternal(...)`;

có thể tham gia logic dựng object theo contract tương ứng.

### 11.1. Invariant cần được kiểm tra khi khôi phục

Ví dụ class chỉ cho phép số lượng không âm:

```java
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;

public final class Quantity implements Serializable {
    private static final long serialVersionUID = 1L;
    private int amount;

    public Quantity(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("negative");
        }
        this.amount = amount;
    }

    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        if (amount < 0) {
            throw new InvalidObjectException("negative amount");
        }
    }
}
```

Đây là **class biên dịch độc lập** `Quantity.java`, không có `main`. Hook validation này có ý nghĩa đối với các dữ liệu **đã cho phép** deserialize, nhưng **không** khiến việc giải tuần tự hóa stream không tin cậy trở nên an toàn. Nó cũng không thay thế kiểm soát side effects của cả object graph.

### 11.2. Immutability không tự được bảo vệ qua serialization

`final` fields, private constructors và factory methods là công cụ thiết kế đối tượng, nhưng Java serialization có các quy tắc phục hồi đặc thù; không nên suy luận rằng chỉ vì không có public setter thì mọi object được deserialize đều thỏa invariant. Với class nhạy cảm, hãy giảm hoặc loại bỏ khả năng deserialize trực tiếp và chỉ tạo object qua factory nhận DTO đã kiểm tra.

## 12. Cách xử lý input từ network

**Không** tạo `ObjectInputStream` trực tiếp trên một socket chỉ vì bên gửi nói “đây là object hợp lệ”:

```java
// PHẢN VÍ DỤ: không dùng với socket nhận dữ liệu không tin cậy.
ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
Object request = in.readObject();
```

Ngoài bề mặt attack, constructor của ObjectInputStream có thể **chờ stream header**, và `readObject` có thể tiếp tục chờ dữ liệu chưa đủ. Một remote peer có thể gửi quá chậm, không bao giờ kết thúc frame hoặc vượt quota.

Thay bằng giao thức có:

1. Timeout đọc, giới hạn kết nối và tổng bytes tối đa.
2. Frame header (magic/version/length) được parse an toàn trước.
3. Kiểm tra length hợp lệ **trước cấp phát**.
4. Cố định type/schema; không resolve class theo tên từ input.
5. Giải mã charset có chính sách lỗi rõ, xác minh required fields và ranges.
6. Authentication/authorization khi cần, và giới hạn số message mỗi người gửi.
7. Cơ chế backpressure, telemetry lỗi, đóng connection khi protocol vi phạm.

### 12.1. Giới hạn InputStream trước khi readAllBytes

`InputStream.readAllBytes()` tiện dùng, nhưng nếu nguồn chưa tin cậy và chưa có giới hạn, nó có thể cấp phát tăng dần cho tới khi hết bộ nhớ. `ByteArrayOutputStream` cũng vậy nếu cứ copy cho đến EOF.

Trong giao thức có length-prefix, chỉ cấp phát tối đa kích thước hợp lệ. Nếu chưa tin byte length prefix, kiểm tra nó trước. Khi đọc stream chưa framing, dùng một wrapper counted/limited với hard cap và fail-closed; tránh đợi cho tới khi RAM đã cạn rồi mới kiểm tra.

### 12.2. Compression và nested content

Ngay cả khi file nén truyền tới chỉ 10 KB, khi giải nén nó có thể mở rộng thành hàng trăm MB/GB. Cần giới hạn **kích thước sau giải nén**, recursion/nesting, số entry và thời gian xử lý. Đừng đặt giới hạn chỉ lên byte compressed rồi kết luận deserializer/parser không thể gặp input khổng lồ.

## 13. Xử lý lỗi: fail closed thay vì “thử cách khác”

Các exception liên quan Java deserialization:

| Ngoại lệ | Có thể biểu thị |
|---|---|
| `StreamCorruptedException` | Header hoặc control data không hợp lệ |
| `InvalidClassException` | Class không tương thích, hoặc bị filter từ chối |
| `NotSerializableException` | Graph ghi có object không serializable |
| `ClassNotFoundException` | Class trong stream không có ở môi trường đọc |
| `OptionalDataException` | Đọc sai loại object/primitive hoặc dữ liệu đặc biệt |
| `EOFException` | Stream chưa đủ bytes/đến cuối trong lúc đọc |
| `InvalidObjectException` | Invariant/hook validation chủ động từ chối |
| `IOException` | Lỗi I/O hoặc lỗi đọc dữ liệu |
| `ClassCastException` | Root object đọc được khác kiểu ứng dụng mong đợi |

Các lỗi này không nên bị biến thành “payload trống hợp lệ”. Với `ObjectInputStream.readObject()`, tài liệu nói các exception để stream ở trạng thái **không xác định**; thường phải đóng stream và từ chối message thay vì cố skip vài byte rồi tiếp tục như chưa có gì xảy ra.

**Không fallback tự động** theo mẫu “deserialize không được thì tắt filter và thử lại”. Nếu filter từ chối payload, đó là một tín hiệu an ninh/chính sách. Cần log đủ thông tin phi nhạy cảm và điều tra theo quy trình, không hạ cấp validation chỉ để “cho chương trình chạy”.

## 14. Migrations: chuyển khỏi Java Serialization an toàn

Một hệ thống cũ có thể đã lưu nhiều file `.ser`. Chuyển định dạng không chỉ là tìm `readObject` rồi thay `parseJson`; cần có lộ trình:

### 14.1. Kiểm kê điểm đọc và phân loại nguồn

Tìm các điểm nhận serialized objects:

- `ObjectInputStream.readObject` và `readUnshared`.
- Thư viện/framework có deserialization ngầm bên trong, cache, RPC hoặc messaging.
- File lưu state, session, job data, serialized blobs trong database.
- Backups và dữ liệu chưa có chính sách xóa.
- Các consumer chạy phiên bản khác nhau trong cùng hệ thống.

Đối với từng điểm, ghi lại source, quyền ghi, class graph mong đợi, dung lượng, thời gian sống và mức tin cậy. Đừng dựa vào extension hay tên bảng database.

### 14.2. Thiết kế format thay thế

Chọn JSON/CBOR/Protocol Buffers/format byte định nghĩa rõ khi phù hợp; thống nhất:

- Version trong message/schema.
- Danh sách trường và type cố định.
- Kích thước tối đa của message và từng trường.
- Quy tắc optional/default/unknown fields.
- Charset, timezone, date/time, numeric range và chuẩn Unicode.
- Integrity/authentication theo threat model.
- Quy tắc tương thích backward/forward và kế hoạch loại version cũ.

### 14.3. Conversion tool ở môi trường kiểm soát

Nếu vẫn phải mở các file legacy:

1. Chỉ chạy trên bản copy từ nguồn có xác thực và kiểm tra tính toàn vẹn, không trực tiếp trên upload chưa tin cậy.
2. Dùng JDK và classpath đã kiểm kê, áp class/graph/resource filters chặt.
3. Chạy công cụ với quyền OS tối thiểu, hạn chế network, filesystem, CPU/memory/time và logging phù hợp; isolation giảm tác hại nhưng không đảm bảo an toàn tuyệt đối.
4. Validate dữ liệu sau deserialize rồi viết sang định dạng mới.
5. Test sample hợp lệ/corrupt và hỗ trợ rollback khi conversion sai.
6. Sau cutover, loại các đường vào `ObjectInputStream` không còn cần thiết, thay vì để endpoint legacy mở mãi.

Nếu không thể chứng minh input legacy đáng tin và không thể áp controls thích hợp, **từ chối chuyển tự động** tốt hơn là đưa nó vào tiến trình production quan trọng.

## 15. Các lỗi thiết kế phổ biến

1. **Đọc object stream không tin cậy chỉ vì class implements `Serializable`.** Đây không phải chứng nhận an toàn.
2. **Kiểm tra `instanceof` sau `readObject` rồi xem như đã chặn khai thác.** Quá trình deserialize đã thực thi trước đó.
3. **Dựa vào đuôi file `.ser` hay magic header `AC ED 00 05`.** Chúng là dấu hiệu format, không phải authenticity.
4. **Chỉ lọc root class.** Nested graph có thể chứa các class khác đã được xử lý.
5. **Filter trả `UNDECIDED` cho class ngoài danh sách mà nghĩ là deny.** Phải có reject policy.
6. **Cho phép cả package/Java modules không cần thiết.** Làm bề mặt class lớn hơn yêu cầu thật.
7. **Không giới hạn mảng, depth, references hoặc bytes.** Dễ gặp resource exhaustion.
8. **Chỉ giới hạn bytes sau khi `readAllBytes`.** Cấp phát không giới hạn đã xảy ra trước kiểm tra.
9. **Cho rằng filter xác thực nội dung String.** Vẫn phải validate độ dài/giá trị field.
10. **Cho rằng class allowlisted luôn không có side effects.** Audit hook và sự thay đổi class.
11. **Chỉ xác thực nguồn mà không hạn chế format/tài nguyên.** Nguồn bị compromise vẫn có thể gửi input lỗi.
12. **Đổi sang JSON nhưng bật generic polymorphic deserialization cho class do input chọn.** Vẫn cần schema/type whitelist.
13. **Không kiểm tra UTF-8 malformed.** Dữ liệu có thể bị thay thế ký tự âm thầm.
14. **Tin input length-prefix là đúng rồi cấp phát array ngay.** Length do attacker điều khiển phải bị ràng buộc.
15. **Không kiểm tra trailing bytes hoặc dữ liệu thiếu.** Frame không hợp lệ có thể bị chấp nhận một phần.
16. **Để parser/chuyển đổi đọc vô hạn trên socket chậm.** Cần timeout và quotas.
17. **Ngầm chuyển đổi phiên bản class mà không kiểm tra invariant.** Dữ liệu cũ không tự phù hợp model mới.
18. **Bắt lỗi filter rồi tự động tắt filter/retry.** Hạ cấp chính sách bảo vệ.
19. **Dùng `readObject` như công cụ probe file unknown.** Không deserialize để “xem thử file chứa gì”.
20. **Ghi log toàn bộ serialized bytes hoặc secret khi lỗi.** Có thể lộ dữ liệu; ghi thông tin lỗi đã giảm nhạy cảm.
21. **Cho rằng một sandbox/process luôn đủ bảo vệ.** Cần quyền tối thiểu, patch, quan sát runtime và defense-in-depth.
22. **Không kiểm tra policy filter của thư viện sử dụng `ObjectInputStream` ngầm.** Điểm vào có thể nằm ngoài source code endpoint.
23. **Dùng cùng một filter quá rộng cho mọi use case.** Context khác nhau cần class/limits khác nhau.
24. **Không test với file thiếu/truncated, graph sâu và field không hợp lệ.** Chỉ happy path không đánh giá được robustness.
25. **Tưởng parse thành Java record là đủ.** Record có field types nhưng validation nghiệp vụ vẫn cần kiểm tra.
26. **Xem mọi `IOException` khi đọc là recoverable và tiếp tục trên stream hỏng.** ObjectInputStream có thể ở trạng thái không xác định.
27. **Nhầm integrity check với confidentiality.** Dữ liệu xác thực vẫn có thể bị đọc nếu không mã hóa/kiểm soát quyền truy cập.
28. **Tin dữ liệu được lưu “nội bộ” là bất biến.** Kiểm tra ai có quyền ghi từ ứng dụng, backup, file share và queue.
29. **Không giới hạn output khi decode/decompress.** Input nhỏ có thể nở lớn.
30. **Không có kế hoạch xóa endpoint legacy.** Hệ thống vẫn giữ điểm deserialize nguy hiểm sau khi đã chuyển định dạng phần lớn.

## 16. Tự kiểm tra kiến thức

**Câu 1.** Vì sao `(Expected) in.readObject()` nguy hiểm khi bytes không tin cậy?  
**Trả lời:** Trước khi cast, JVM đã có thể dựng graph, chạy logic deserialize và cấp phát tài nguyên theo stream.

**Câu 2.** Kiểm tra file header `AC ED 00 05` có chứng minh stream an toàn không?  
**Không.** Đây là magic/version của protocol, không xác minh nguồn hoặc class graph.

**Câu 3.** Nên ưu tiên cách gì cho request API do người dùng tạo?  
**Trả lời:** Giao thức dữ liệu có schema/type cố định, parser giới hạn bytes/độ sâu và validation từng trường.

**Câu 4.** `ObjectInputFilter` có thể giới hạn những gì?  
**Trả lời:** Class, array length, graph depth, reference count và số bytes đã tiêu thụ theo contract.

**Câu 5.** Filter trả `UNDECIDED` có nghĩa là class đã bị chặn chưa?  
**Chưa.** Đây là trạng thái chưa quyết định; muốn deny rõ phải trả `REJECTED` theo policy.

**Câu 6.** `setObjectInputFilter` đặt sau `readObject` có đúng không?  
**Không.** Bộ lọc phải được thiết lập trước khi đọc object, theo quy tắc gọi của API.

**Câu 7.** Chỉ allowlist một DTO class thì có cần validate `String name` không?  
**Có.** Filter không kiểm tra mọi giá trị primitive/String; vẫn phải kiểm tra nội dung, giới hạn và invariant.

**Câu 8.** `Future`/async transport có tự hạn chế độ dài object stream không?  
**Không.** Transport API và input framing phải có quota riêng trước khi deserialize.

**Câu 9.** Nếu `InvalidClassException` báo filter từ chối, có nên đọc tiếp cùng stream không?  
**Không nên.** Hãy coi stream hỏng/không tin cậy, đóng và báo lỗi; không hạ cấp filter tự động.

**Câu 10.** Mã ví dụ `SafeFrameDemo` dùng `available()` có áp dụng để đo tổng byte network stream không?  
**Không.** Ở đây nguồn là `ByteArrayInputStream` đã nắm toàn bộ frame; network stream cần length-prefix và bounded read.

**Câu 11.** Dùng chữ ký số có đủ thay filter/schema không?  
**Không.** Chữ ký giúp xác minh tính toàn vẹn và nguồn theo threat model; vẫn cần giới hạn graph/bytes và validation.

**Câu 12.** Khi buộc convert serialized files legacy, nên chạy ở đâu?  
**Trả lời:** Môi trường kiểm soát, dữ liệu đã xác thực, classpath hạn chế, filter chặt và quyền/tài nguyên giới hạn; viết sang định dạng mới rồi bỏ điểm đọc cũ.

**Câu 13.** `InputStream.readAllBytes()` có an toàn cho file upload không giới hạn không?  
**Không.** Nó có thể cấp phát theo toàn bộ input; cần hard cap từ tầng nhận.

**Câu 14.** Filter class chặt có bảo đảm tránh mọi side effects của class allowed không?  
**Không.** Code của chính allowed class/hook vẫn cần audit; filter không phải validator hành vi.

**Câu 15.** Mục đích đúng của Java Object Serialization khi vẫn phải dùng là gì?  
**Trả lời:** Compatibility với dữ liệu/object graph Java do hệ thống kiểm soát, kèm boundaries và controls, **không** phải format nhập liệu mặc định từ đối tượng không tin cậy.

## 17. Kết luận và tài liệu chính thức

**Ghi nhớ:** “Không deserialize để kiểm tra xem object có đúng kiểu hay không; chỉ deserialize khi đã có cơ sở tin cậy, chính sách class/graph và ranh giới tài nguyên phù hợp — và khi có lựa chọn, dùng định dạng dữ liệu với parser có schema.”

Trình tự ra quyết định:

```text
Nguồn có thể bị đối phương kiểm soát?
    ├── Có hoặc chưa chứng minh được nguồn tin cậy
    │       └── KHÔNG dùng ObjectInputStream.readObject trực tiếp
    │               └── Bounded framing → schema parser → validation → DTO
    └── Dữ liệu legacy tin cậy, cần compatibility
            └── Kiểm kê classes + xác minh nguồn
                    └── Quota/input bounds + ObjectInputFilter
                            └── Validate output và audit allowed hooks
                                    └── Migration sang format dữ liệu ổn định
```

Nguồn chính thức (Java SE 21, áp dụng các APIs được trình bày cho Java 17+):

- `ObjectInputStream` — cảnh báo nguy hiểm của untrusted deserialization, hợp đồng `readObject` và `setObjectInputFilter`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputStream.html
- `ObjectInputFilter` — class/resource filtering, policy composition và filter factory: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputFilter.html
- `ObjectInputFilter.Config` — pattern filters và JVM-wide filter: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputFilter.Config.html
- `Serializable` — default serialization và class hooks: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Serializable.html
- `DataInputStream` — bounded primitive/data reading theo định dạng do ứng dụng thiết kế: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/DataInputStream.html
- `CharsetDecoder` — chính sách lỗi UTF-8: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/CharsetDecoder.html
- Oracle Secure Coding Guidelines for Java SE, phần Serialization/Deserialization: https://www.oracle.com/java/technologies/javase/seccodeguide.html


</details>

- [Quay lại đầu trang](#back-to-top)
