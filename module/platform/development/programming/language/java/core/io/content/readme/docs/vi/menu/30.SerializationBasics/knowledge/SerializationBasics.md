<a id="back-to-top"></a>

# Tuần tự hóa cơ bản

## Menu
- [Tuần tự hóa (serialization), Serializable và việc lưu đồ thị đối tượng](#serialization-basics-001)
- [ObjectOutputStream và ObjectInputStream](#serialization-basics-002)
- [writeObject, readObject và định dạng stream](#serialization-basics-003)
- [Tham chiếu dùng chung, vòng tham chiếu và object handle](#serialization-basics-004)
- [Trường transient, static và trạng thái mặc định được lưu](#serialization-basics-005)
- [serialVersionUID và tương thích lớp](#serialization-basics-006)
- [NotSerializableException, InvalidClassException và StreamCorruptedException](#serialization-basics-007)
- [Constructor của lớp Serializable và lớp cha không Serializable](#serialization-basics-008)
- [Ghi lại đối tượng đã thay đổi, reset và cache handle](#serialization-basics-009)
- [Stream header và lỗi khi nối tùy tiện nhiều object stream](#serialization-basics-010)
- [Khác biệt với định dạng trao đổi có schema độc lập Java](#serialization-basics-011)

## <a id="serialization-basics-001">Tuần tự hóa (serialization), Serializable và việc lưu đồ thị đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Tuần tự hóa (serialization), Serializable và lưu đồ thị đối tượng trong Java

## 1. Serialization là gì?

**Serialization (tuần tự hóa)** là quá trình chuyển một đối tượng cùng trạng thái cần lưu của nó thành một biểu diễn có thể ghi vào luồng byte. **Deserialization (giải tuần tự hóa)** là quá trình đọc biểu diễn ấy và dựng lại các đối tượng tương ứng.

Trong Java Core I/O, cơ chế tuần tự hóa đối tượng tích hợp sẵn dùng:

- `java.io.Serializable`: **marker interface** đánh dấu lớp cho phép Java serialization.
- `java.io.ObjectOutputStream`: ghi đối tượng và đồ thị tham chiếu ra `OutputStream`.
- `java.io.ObjectInputStream`: đọc byte đã được tạo theo định dạng serialization của Java và dựng lại các đối tượng.

Cần phân biệt:

- **Serialization**: dữ liệu Java object được mã hóa thành một định dạng byte có quy tắc.
- **Persistence**: trạng thái được lưu để tồn tại qua vòng đời tiến trình; Java serialization chỉ là một cách thực hiện, không phải lựa chọn luôn tốt nhất.
- **Deep copying**: có thể dùng serialize–deserialize làm cách sao chép một đồ thị phù hợp, nhưng thường tốn chi phí và không áp dụng cho mọi object.
- **Object graph**: tập các object và quan hệ tham chiếu nối chúng với nhau, có thể chia sẻ cùng object hoặc có chu trình.

Ví dụ đồ thị:

```text
team (ArrayList)
  ├── [0] ──> alice ───── friend ──> bob
  │             ^                      │
  │             └────── friend ────────┘
  ├── [1] ───────────────────────────> bob
  └── [2] ──> alice   (cùng tham chiếu với [0])
```

Khi ghi **cả đồ thị** bằng `ObjectOutputStream.writeObject(team)`, cơ chế Java serialization lưu cả thông tin **định danh object trong luồng**. Khi đọc lại, tham chiếu lặp và vòng có thể được khôi phục đúng theo đồ thị, chứ không đơn thuần tạo một bản copy cho mỗi lần gặp cùng object.

> **Lưu ý bảo mật quan trọng:** không sử dụng `ObjectInputStream` để giải tuần tự hóa byte đến từ nguồn không tin cậy. Java native deserialization có bề mặt tấn công lớn; bộ lọc `ObjectInputFilter` chỉ là lớp phòng vệ bổ sung, không biến input không tin cậy thành an toàn tuyệt đối. Với dữ liệu trao đổi công khai, cân nhắc định dạng schema/DTO được kiểm soát và parser phù hợp.

## 2. Serializable là marker interface

Một lớp muốn tham gia cơ chế tuần tự hóa chuẩn có thể triển khai `Serializable`:

```java
import java.io.Serializable;

public class Student implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;

    public Student(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return id + " - " + name;
    }
}
```

Đây là **một class minh họa độc lập**, có thể lưu `Student.java`; để chạy, cần một class chứa `main` hoặc ứng dụng khác sử dụng nó.

`Serializable` là **marker interface**: bản thân nó không định nghĩa phương thức phải override. Lời gọi `writeObject` của `ObjectOutputStream` và logic runtime xử lý lớp này theo cơ chế đặc biệt.

Điều này **không đồng nghĩa** mọi lớp tự động được tuần tự hóa:

- Một object thuộc lớp **không serializable** xuất hiện trong đồ thị có thể khiến `writeObject` thất bại với `NotSerializableException`.
- Những field được khai báo `transient` không tham gia cơ chế lưu field mặc định.
- `static` là trạng thái thuộc lớp, **không** phải trạng thái của instance được lưu theo cách mặc định.
- Một số loại object có quy tắc serialization đặc biệt, chẳng hạn `enum`, `record`; không nên áp đặt máy móc tất cả quy tắc của class thông thường cho chúng.
- Một class `Serializable` có thể tùy chỉnh hành vi serialization bằng các hook hợp lệ, nhưng điều đó không được dùng để bỏ qua bảo mật và kiểm tra bất biến.

### Serializable không chỉ là “ghi field ra text”

`ObjectOutputStream` tạo **stream nhị phân định dạng riêng của Java**, gồm header, mô tả class, dữ liệu field và thông tin tham chiếu. Đừng mở file bằng Notepad rồi mong đọc được nội dung như JSON. Đừng dùng định dạng native này làm giao thức công khai lâu dài nếu cần interoperability với ứng dụng viết bằng ngôn ngữ khác.

## 3. ObjectOutputStream và ObjectInputStream

Pipeline cơ bản:

```text
Student object
     |
     v
ObjectOutputStream.writeObject(...)
     |
     v
Java serialization stream bytes
     |
     v
FileOutputStream / ByteArrayOutputStream / OutputStream khác
     |
     v
ObjectInputStream.readObject()
     |
     v
Student object được tái tạo
```

Để ghi:

```java
try (ObjectOutputStream out = new ObjectOutputStream(
        Files.newOutputStream(Path.of("student.ser")))) {
    out.writeObject(student);
}
```

Để đọc:

```java
try (ObjectInputStream in = new ObjectInputStream(
        Files.newInputStream(Path.of("student.ser")))) {
    Student restored = (Student) in.readObject();
}
```

Hai đoạn là **mã minh họa**, cần import `ObjectOutputStream`, `ObjectInputStream`, `Files`, `Path` và khai báo object `student`. Method đọc cần xử lý `IOException` và `ClassNotFoundException`. Chỉ dùng `ObjectInputStream` trên stream được tạo bởi nguồn có thể tin cậy và được quản lý.

Một vài chi tiết:

1. Constructor `ObjectOutputStream` ghi **stream header** trước khi gọi `writeObject`.
2. Constructor `ObjectInputStream` đọc/kiểm tra header của input, có thể **block** khi chờ header từ một stream đang mở, đặc biệt ở kết nối hai chiều.
3. `writeObject` ghi biểu diễn của object và các phần liên quan trong đồ thị.
4. `readObject` trả về kiểu `Object`, caller kiểm tra/cast kiểu mong đợi và xác minh dữ liệu có hợp lệ về mặt nghiệp vụ không.
5. Try-with-resources đóng wrapper và underlying stream, đảm bảo flush/close tài nguyên theo hợp đồng API.
6. Đọc đối tượng có thể khởi tạo những object mới với **tham chiếu khác** object gốc, nhưng quan hệ chia sẻ **bên trong** đồ thị khôi phục được giữ theo semantics serialization.

`ObjectInputStream.readObject()` không phải một parser “chỉ đọc dữ liệu thuần”; nó có thể kích hoạt hành vi trong class khi giải tuần tự hóa, bao gồm hook và quá trình dựng object. Đây là một nguyên nhân chính khiến input không tin cậy nguy hiểm.

## 4. Đồ thị đối tượng được lưu như thế nào?

Một đồ thị thường có nhiều object liên kết với nhau. Ví dụ:

```java
final class Node implements Serializable {
    private static final long serialVersionUID = 1L;

    String name;
    Node next;

    Node(String name) {
        this.name = name;
    }
}
```

Đây là class minh họa nằm trong cùng package/file phù hợp và cần import `Serializable`. Nếu:

```java
Node a = new Node("A");
Node b = new Node("B");
a.next = b;
b.next = a;
```

thì đồ thị có vòng `A → B → A`.

Java serialization không đơn giản truy vết đệ quy vô hạn. `ObjectOutputStream` quản lý các **handle tham chiếu trong stream**. Khi gặp lại một object đã xuất hiện, stream có thể ghi tham chiếu tới handle của object ấy thay vì vô hạn lần ghi lại toàn bộ object.

Khi giải tuần tự hóa:

- Các object tương ứng được tạo lại.
- Các reference field được gán để tái lập quan hệ.
- Vòng tham chiếu có thể được phục hồi.
- Hai field trước đây trỏ cùng một object trong phạm vi đồ thị được viết chung sẽ lại trỏ cùng một object **mới** trong đồ thị phục hồi.

Điểm cần nắm: **object identity bên trong đồ thị được bảo toàn**, còn object phục hồi không có cùng identity với object gốc còn sống ở JVM ban đầu.

### Identity khác equality

- `a == b`: hai reference chỉ cùng object instance không?
- `a.equals(b)`: logic so sánh theo định nghĩa class, có thể bằng nhau dù hai object khác instance.

Khi học serialization đồ thị, phải dùng `==` để chứng minh chia sẻ tham chiếu được phục hồi. Gọi `equals` có thể chỉ chứng minh field giống nhau, **không** chứng minh alias.

## 5. Chương trình Java 17+ hoàn chỉnh: shared references, vòng và transient

Ví dụ này chỉ sử dụng `ByteArrayOutputStream` và `ByteArrayInputStream` để dễ chạy mà không phụ thuộc file hệ thống. Các quan hệ object graph được xây dựng rõ ràng:

- Một danh sách chứa `alice` ở index 0 và 2, chứng tỏ **cùng tham chiếu**.
- `alice.friend = bob` và `bob.friend = alice` tạo **vòng hai chiều**.
- Field `sessionToken` đánh dấu `transient`, nên sau giải tuần tự hóa nhận giá trị mặc định `null`.
- `serialVersionUID` khai báo rõ cho class tham gia serialization.
- Các dòng stdout không dựa vào kích thước file hay chi tiết byte format.

Lưu file tên **`SerializationGraphDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SerializationGraphDemo {

    private static final class Person implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String name;
        private Person friend;
        private transient String sessionToken;

        private Person(String name, String sessionToken) {
            this.name = name;
            this.sessionToken = sessionToken;
        }
    }

    public static void main(String[] args) throws Exception {
        Person alice = new Person("Alice", "secret-a");
        Person bob = new Person("Bob", "secret-b");

        alice.friend = bob;
        bob.friend = alice;

        List<Person> team = new ArrayList<>();
        team.add(alice);
        team.add(bob);
        team.add(alice); // Cùng instance với team.get(0).

        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(team);
            output.flush();
            serialized = bytes.toByteArray();
        }

        List<?> restored;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            restored = (List<?>) input.readObject();
        }

        Person restoredAlice = (Person) restored.get(0);
        Person restoredBob = (Person) restored.get(1);

        System.out.println("List size: " + restored.size());
        System.out.println("Names: "
                + restoredAlice.name + ", " + restoredBob.name);
        System.out.println("Shared reference: "
                + (restoredAlice == restored.get(2)));
        System.out.println("Cycle restored: "
                + (restoredAlice.friend == restoredBob
                    && restoredBob.friend == restoredAlice));
        System.out.println("Transient is null: "
                + (restoredAlice.sessionToken == null));
        System.out.println("Original differs: "
                + (restoredAlice != alice));
    }
}
```

Chạy với Java 17+:

```bash
javac SerializationGraphDemo.java
java SerializationGraphDemo
```

**Stdout chính xác:**

```text
List size: 3
Names: Alice, Bob
Shared reference: true
Cycle restored: true
Transient is null: true
Original differs: true
```

Tại sao kết quả như vậy?

1. `ArrayList` là serializable, vì vậy object danh sách cùng các `Person` có thể được ghi.
2. `Person` triển khai `Serializable` và chứa `name` là `String` (serializable), `friend` trỏ sang `Person` (serializable) và `sessionToken` là transient.
3. Khi `alice` gặp lại ở index 2, ObjectOutputStream lưu quan hệ handle/alias thích hợp; khi đọc lại, `restored.get(2) == restored.get(0)`.
4. Vòng `alice → bob → alice` được giải tuần tự hóa với reference kết nối đúng, không gây đệ quy vô hạn chỉ vì tồn tại cycle.
5. `sessionToken` không được lưu, vì vậy field của object được khôi phục là `null`. Điều này **không xóa** string bí mật còn tồn tại trong object gốc; đồng thời không nên coi `transient` là một hệ thống quản lý bí mật hoàn chỉnh.
6. `restoredAlice != alice` bởi đây là hai instance khác nhau. Identity chỉ được bảo toàn **trong đồ thị khôi phục**, không phải giữa hai đồ thị ở hai giai đoạn.

**Chú ý:** trong mã ví dụ, `Person` là nested **static** class. Một inner class không static có thể mang reference ngầm tới instance lớp bao; reference ẩn này có thể ảnh hưởng serialization và gây `NotSerializableException`. Khi thiết kế DTO serializable, ưu tiên các class độc lập hoặc static nested class với trạng thái tường minh.

## 6. Những field nào được lưu?

Với cơ chế serialization mặc định của một class thông thường:

| Loại thành phần | Có được ghi mặc định? | Ghi chú |
|---|---|---|
| Instance field non-static, non-transient | Có | Giá trị primitive và reference tới object có thể được xử lý |
| `transient` instance field | Không | Khi đọc lại thường nhận giá trị mặc định hoặc được khôi phục bằng hook |
| `static` field | Không | Thuộc class đang chạy, không gắn với một serialized instance |
| `serialVersionUID` | Không phải dữ liệu instance | Là UID mô tả phiên bản serialization của class |
| Phương thức và bytecode class | Không như state instance | Class tương thích phải có sẵn cho runtime giải tuần tự hóa |
| Field của object được tham chiếu | Có thể | Nếu đi theo graph và kiểu đáp ứng hợp đồng Serializable |

### Transient và giá trị mặc định

`transient` khiến field không được lưu theo cơ chế field mặc định:

```java
class Session implements Serializable {
    private static final long serialVersionUID = 1L;

    String username;
    transient String accessToken;
    transient int retryCounter;
}
```

Khi object `Session` được khôi phục theo serialization mặc định, `accessToken` có thể là `null` và `retryCounter` là `0`. Nếu class có hook `readObject` tự khởi tạo lại các field này thì kết quả có thể khác.

Cần chú ý:

- `transient` ngăn **field đó** bị đưa vào luồng theo cách mặc định, không đảm bảo giá trị nhạy cảm không bị lưu qua field khác/hook custom.
- Mặc định field của object serializable được tái tạo mà không chạy constructor thường của chính class ấy. Nếu constructor gán token mặc định, không dựa vào constructor đó để khôi phục token.
- Nếu một object liên kết với resource như socket, thread, file handle, database connection, nên loại resource ra khỏi form lưu và tạo lại khi cần theo cách có kiểm soát.

### Static không được snapshot theo mỗi object

`static int counter` là thuộc tính của class trong JVM. Ghi một instance không lưu “bản riêng” của counter; sau khi đọc lại, giá trị field static là trạng thái hiện tại của class tại runtime. Vì vậy không thể dùng serialization của một instance để backup toàn bộ global state của ứng dụng.

## 7. Đồ thị chứa đối tượng không Serializable

Đánh dấu root class `Serializable` **không** tự khiến mọi object ở phía sau graph serializable.

Ví dụ:

```java
final class ResourceHolder implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String label;
    private final Thread worker; // Thread không Serializable.

    ResourceHolder(String label, Thread worker) {
        this.label = label;
        this.worker = worker;
    }
}
```

Nếu `worker` không null và object được ghi theo cách mặc định:

```java
output.writeObject(holder);
```

có thể ném **`java.io.NotSerializableException`** vì `Thread` không đáp ứng cơ chế serialization. Hai đoạn này là minh họa, cần đặt trong class/method và khai báo `output`, `holder` tương ứng.

Giải pháp không phải “ép kiểu mọi thứ sang Serializable” một cách mù quáng. Hãy hỏi:

1. Field có thực sự cần được persist không?
2. Nó có đại diện cho **tài nguyên đang hoạt động** không? Nếu có, serialization thường không phải nơi lưu resource đó.
3. Nếu chỉ cần định danh, lưu một ID/string/DTO có thể tái tạo resource sau khi đọc.
4. Nếu field chỉ là cache/handle tạm, đánh dấu `transient` và thiết kế bước tái khởi tạo.
5. Nếu toàn bộ graph quá phức tạp, tạo **mô hình dữ liệu lưu riêng** thay vì serialize trực tiếp domain object.

### Đánh dấu transient có làm graph luôn an toàn?

Không. Các field còn lại vẫn phải serializable, và callback custom serialization có thể đưa giá trị khác vào stream. Chưa kể object bất biến về mặt nghiệp vụ có thể bị phá vỡ sau deserialization nếu kiểm tra không được tái thực hiện.

## 8. Object identity và handle trong một stream

Khi dùng **một `ObjectOutputStream`** và gọi `writeObject` nhiều lần trên cùng object, stream duy trì bảng handle. Ví dụ:

```java
ObjectOutputStream out = ...;
out.writeObject(person);
out.writeObject(person);
```

Nếu không thay đổi cơ chế chia sẻ hay reset stream, lần ghi thứ hai có thể là reference tới object đã được ghi. Khi đọc liên tiếp:

```java
Object first = in.readObject();
Object second = in.readObject();
System.out.println(first == second); // Thường là true trong mẫu này.
```

Hai đoạn chỉ là minh họa, `out`/`in` phải được khai báo, quản lý vòng đời và dùng cùng một serialization stream tương ứng.

**Bẫy “đã sửa object nhưng writeObject lần nữa”**: nếu `person.name` được thay đổi giữa hai lần `writeObject(person)` trên cùng stream, lần thứ hai có thể chỉ ghi tham chiếu, **không snapshot trạng thái mới**. Khi cần bắt đầu một chu kỳ ghi dữ liệu mới với handle table độc lập, `ObjectOutputStream.reset()` có thể được dùng một cách có chủ đích.

`reset()` khiến ObjectOutputStream quên trạng thái tham chiếu đã ghi trước đó cho các object sau reset, nhưng không biến stream thành JSON append log có versioning hoàn chỉnh. Nó cũng không thay thế việc đóng/reopen khi yêu cầu format, consistency hoặc transaction khác.

### writeUnshared/readUnshared

Java có `writeUnshared`/`readUnshared` cho một số tình huống cần tránh chia sẻ handle của object top-level. Đây là các API chuyên biệt, **không phải deep clone đơn giản**: tham chiếu bên trong graph vẫn có ngữ nghĩa riêng. Với bài toán cơ bản, hãy hiểu `writeObject` và cơ chế handle trước khi cần đến `writeUnshared`.

## 9. serialVersionUID và tương thích phiên bản

Một class serializable thông thường nên khai báo rõ:

```java
@Serial
private static final long serialVersionUID = 1L;
```

Trong Java 17, `@Serial` (`java.io.Serial`) giúp compiler/công cụ nhận diện khai báo liên quan serialization. `serialVersionUID` phải là field `static final long` đúng tên, theo quy tắc API.

UID được dùng trong quá trình kiểm tra tương thích class lúc deserialization. Nếu UID của phiên bản class hiện tại không khớp với UID ghi trong stream, runtime thường ném **`InvalidClassException`**.

Vì sao khai báo tường minh?

- Nếu không khai báo, JVM tính UID mặc định dựa trên chi tiết class. Những thay đổi source/biên dịch có thể khiến UID đổi ngoài ý muốn.
- UID rõ ràng khiến quá trình nâng cấp model có thể được kiểm soát tốt hơn.
- Nó **không** đảm bảo mọi thay đổi với cùng UID sẽ tương thích về ngữ nghĩa. Vẫn phải kiểm tra schema, invariants và quy tắc đọc object.

### Thay đổi class giữa hai phiên bản

Giả sử V1:

```java
class Account implements Serializable {
    private static final long serialVersionUID = 1L;
    String id;
}
```

Sau đó V2 thêm field:

```java
class Account implements Serializable {
    private static final long serialVersionUID = 1L;
    String id;
    String displayName;
}
```

Đây là **hai phiên bản minh họa** của cùng một class, không được khai báo cùng lúc trong một file/package. Nếu một stream cũ được đọc bởi V2 theo cơ chế field mặc định tương thích, `displayName` có thể có giá trị mặc định `null`.

Nhưng thêm/bỏ/đổi kiểu field, thay hierarchy hay thay semantic class có thể gây lỗi hoặc tạo trạng thái không hợp lệ. Ví dụ V2 kỳ vọng `displayName` không null thì cần kiểm tra/đặt fallback sau khi đọc; không được giả định constructor luôn chạy để sửa nó.

### Khi nào tăng serialVersionUID?

UID là một phần hợp đồng tương thích. Nếu một thay đổi không còn tương thích và bạn chủ ý chặn việc đọc stream cũ, có thể chọn UID mới. Nếu giữ khả năng đọc cũ, hãy kiểm thử serialization/deserialization chéo phiên bản thực tế. Đừng tăng UID tự động sau **mọi** lần sửa mã, cũng đừng giữ UID cũ mà không thử nghiệm.

### Java serialization không phải format ổn định cho mọi hệ thống

Các object model thay đổi theo version ứng dụng, tên package/class, cấu trúc kế thừa, class loader và custom hooks. Vì vậy không nên dùng serialized object stream làm định dạng lưu trữ lâu dài/công khai nếu yêu cầu tương thích đa ngôn ngữ, migration phức tạp hoặc truyền dữ liệu qua trust boundary.

## 10. Constructor và quá trình tạo object khi giải tuần tự hóa

Một hiểu nhầm phổ biến: “`readObject` gọi constructor của class y như `new`”. Với class `Serializable` thông thường, cơ chế deserialization **không gọi constructor thông thường của chính class đó** để phục hồi các field đã lưu. Constructor không đối số của superclass đầu tiên **không** Serializable trong hierarchy được gọi theo quy tắc serialization.

Ví dụ một class có constructor:

```java
public Profile(String username) {
    if (username == null || username.isBlank()) {
        throw new IllegalArgumentException("Invalid username");
    }
    this.username = username;
}
```

Constructor này chạy khi `new Profile(...)`, nhưng không được xem là lớp kiểm tra sẽ tự chạy lại khi Java phục hồi `Profile` từ serialized stream.

Hệ quả:

- Các bất biến chỉ được kiểm tra trong constructor có thể **không** được áp dụng tự động sau deserialization.
- Các field transient và field mới thường nhận giá trị mặc định trừ khi custom logic xử lý.
- Object có thể phải được validate khi đọc, đặc biệt nếu nó làm đầu vào cho thao tác quan trọng.
- Không deserialize dữ liệu không tin cậy rồi trông chờ constructor chặn mọi trạng thái nguy hiểm.

Với superclass không serializable, constructor không đối số có thể cần khả dụng theo luật truy cập để deserialization của subclass thành công. Nếu không thỏa, có thể xảy ra `InvalidClassException`.

## 11. readObject/writeObject để tùy chỉnh (mức mở rộng)

Một class có thể định nghĩa các hook đặc biệt:

```java
@Serial
private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    // Có thể ghi thêm dữ liệu theo một giao thức custom được quản lý.
}

@Serial
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    // Kiểm tra invariants và khôi phục transient state khi cần.
}
```

Đây là hai **phương thức minh họa** bên trong một class `Serializable`; cần import `Serial`, `ObjectOutputStream`, `ObjectInputStream`, `IOException` và `ClassNotFoundException`.

Chi tiết:

- Phương thức phải theo đúng chữ ký, phạm vi và hợp đồng của Java serialization; không phải override interface method.
- `defaultWriteObject()` và `defaultReadObject()` xử lý tập field serializable mặc định của class.
- Nếu ghi thêm dữ liệu custom, phần đọc phải dùng đúng thứ tự và phiên bản tương ứng.
- `readObject` nên validate bất biến sau khi nhận dữ liệu, có thể ném `InvalidObjectException` cho trạng thái không hợp lệ.
- Hook có thể chạy trong deserialization, khiến byte stream không tin cậy trở nên nguy hiểm; **không** coi custom hook là sandbox.

Đừng thêm hook chỉ để “làm cho ví dụ chạy”: với class đơn giản, default serialization thường đủ để học cơ chế.

### Ví dụ invariant sau đọc

```java
@Serial
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    if (id == null || id.isBlank()) {
        throw new InvalidObjectException("Missing id");
    }
}
```

Đoạn này chỉ là method minh họa, field `id` và các import cần có trong class thực tế. Kiểm tra này bổ sung validation khi deserialization, **không** đảm bảo việc mở stream không tin cậy là an toàn: các loại object có thể bị tạo/xử lý trước khi validation của class này kết thúc.

## 12. Serializable, Externalizable và các loại đặc biệt

### Externalizable

`Externalizable` mở rộng `Serializable` và cho phép class chủ động định nghĩa:

- `writeExternal(ObjectOutput out)`
- `readExternal(ObjectInput in)`

Nó yêu cầu trách nhiệm quản lý cấu trúc dữ liệu và thường có yêu cầu constructor public không đối số. Đây là công cụ nâng cao, không phải cách chữa mọi vấn đề về tính tương thích hay bảo mật. Đối với việc học tuần tự hóa cơ bản, `Serializable + ObjectOutputStream/ObjectInputStream` là nền tảng cần hiểu trước.

### Enum

Enum có cơ chế Java serialization đặc biệt: về mặt ngữ nghĩa, enum constant được giải tuần tự hóa thành constant tương ứng của class enum, không phải một bản sao tự do như class thường. Các hook mặc định và `serialVersionUID` của enum có quy tắc riêng. Không dùng enum làm ví dụ điển hình cho việc lưu mọi instance field theo cơ chế class thông thường.

### Record

Java record cũng có semantics serialization riêng khi triển khai `Serializable`. Quá trình dựng record khi giải tuần tự hóa liên quan canonical constructor theo đặc tả, không nên khẳng định mọi class serializable đều bỏ qua constructor trong mọi trường hợp. Nếu đang thiết kế record DTO, xem tài liệu Java phiên bản mục tiêu.

### Array và collection

Nhiều array và các collection chuẩn Java có hỗ trợ serialization, nhưng **một collection serializable không bảo đảm mọi phần tử có thể được ghi**. Ví dụ `ArrayList<Thread>` thường không serializable được khi chứa thread non-null. Xác minh cả đồ thị thực tế chứ không chỉ kiểu của collection root.

## 13. Exception thường gặp và cách chẩn đoán

| Exception | Nguyên nhân thường gặp | Hướng kiểm tra |
|---|---|---|
| `NotSerializableException` | Graph có object không serializable | Field/reference nào chưa được loại bỏ hoặc chuyển thành DTO? |
| `InvalidClassException` | UID/class descriptor không tương thích hoặc class không thỏa hợp đồng | Version class, serialVersionUID, hierarchy và field format |
| `StreamCorruptedException` | Header/token stream serialization không hợp lệ | Có dùng đúng ObjectOutputStream ghi nguồn không, stream có bị cắt/lẫn byte? |
| `ClassNotFoundException` | Runtime đọc thiếu class cần dựng object | Package/classpath/class loader có đúng version? |
| `OptionalDataException` | Đọc object/primitive không phù hợp cấu trúc stream hoặc gặp EOF trong trường hợp tương ứng | Thứ tự write/read và custom hooks |
| `EOFException` | Stream kết thúc không theo lượng dữ liệu mong đợi | File có bị cắt hoặc đọc quá số object ghi? |
| `InvalidObjectException` | Custom validation / hook reject object | Bất biến nào không được đáp ứng? |
| `IOException` | Lỗi I/O trong đọc/ghi/đóng stream | Quyền truy cập, tệp, tài nguyên, stream đã đóng... |

Đừng nuốt `NotSerializableException` rồi ghi tiếp vào cùng tệp như thể transaction thành công. Một output stream đã lỗi có thể tạo file không đầy đủ hoặc trạng thái cần bỏ, ghi lại từ đầu qua file tạm sau khi sửa nguyên nhân.

`ClassCastException` cũng có thể xuất hiện khi caller ép object đọc được sang class không đúng; đây không phải IOException nhưng vẫn cần kiểm soát kiểu dữ liệu sau deserialization.

## 14. Bảo mật: tránh deserialize dữ liệu không tin cậy

Java native serialization được thiết kế để dựng **đồ thị object Java**, không phải đọc một bản dữ liệu thuần được sandbox tự động. `readObject` có thể kích hoạt hành vi của class được nạp, kể cả các hook. Một số chuỗi object/class kết hợp có thể gây tiêu thụ CPU/bộ nhớ quá mức hoặc hành vi không mong muốn trong ứng dụng có thư viện dễ bị khai thác.

**Nguyên tắc an toàn:**

1. Không nhận trực tiếp Java serialized bytes từ client, HTTP request, queue công khai, cookie hoặc file do người ngoài kiểm soát rồi gọi `readObject`.
2. Nếu giao tiếp qua trust boundary, ưu tiên **JSON/CBOR/Protocol Buffers/Avro hoặc dạng DTO có schema** và parser với validation rõ ràng. Các định dạng này vẫn cần giới hạn tài nguyên, schema và bảo mật, nhưng tránh đưa toàn bộ cơ chế phục dựng Java object graph vào đầu vào không tin cậy.
3. Với dữ liệu lịch sử bắt buộc dùng Java serialization, chỉ đọc từ nơi tin cậy, đặt **giới hạn byte, độ sâu, số reference, kích thước array và loại class**.
4. Sử dụng `ObjectInputFilter` (JEP 290, có từ Java 9) như **defense in depth** nếu phù hợp.
5. Không xem filter đơn giản là bảo đảm tuyệt đối: filter không được gọi cho mọi trường hợp object giống nhau, và lớp được cho phép vẫn có thể có hành vi bất lợi; xem xét toàn bộ classpath, custom readObject và phiên bản thư viện.
6. Xử lý ngoại lệ deserialization và tránh tiếp tục sử dụng object đã đọc dở.

### ObjectInputFilter trong Java 17

Ví dụ minh họa cơ chế API, **không phải cấu hình production dùng nguyên xi**:

```java
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
        "maxdepth=8;maxrefs=100;maxarray=10000;maxbytes=1048576;"
      + "com.example.dto.*;!*");

try (ObjectInputStream in = new ObjectInputStream(source)) {
    in.setObjectInputFilter(filter);
    Object value = in.readObject();
    // Kiểm tra kiểu và invariants bổ sung.
}
```

Đoạn này cần `ObjectInputFilter`, `ObjectInputStream`, một `InputStream source` và xử lý checked exceptions. Bộ lọc minh họa chỉ cho phép package DTO giả định cùng các giới hạn; một đồ thị thực tế có `ArrayList` hay các class JDK khác có thể bị reject nếu không nằm trong allowlist phù hợp. Không bỏ `!*` để “cho chạy được” khi chưa xác định chính xác mô hình và trust boundary.

`ObjectInputFilter.Config.createFilter(...)` sử dụng một chuỗi pattern; tiêu chí và class allowlist phải được xác minh bằng kiểm thử với các dữ liệu hợp lệ, không tin vào chuỗi mẫu. Cần giới hạn **bytes đọc vào stream** ở tầng bao ngoài nếu yêu cầu; filter không thay thế chính sách kích thước file, timeout và quota ứng dụng.

**Kết luận bảo mật:** ngay cả khi có ObjectInputFilter, native Java deserialization không phải lựa chọn khuyến nghị để parse payload từ nguồn không tin cậy. Giải pháp tốt hơn thường là **tránh Java serialized data tại trust boundary**.

## 15. Persistence: khi nào dùng hay không dùng?

### Phù hợp để học và ứng dụng nội bộ giới hạn

- Demo cách object graph được ghi/khôi phục.
- Lưu snapshot nội bộ tạm thời mà application kiểm soát toàn bộ producer, consumer và version model.
- Di chuyển object giữa các thành phần Java tin cậy trong môi trường có hợp đồng serialization rõ ràng.

Ngay cả khi tin cậy, vẫn phải xem xét **schema/version, độ bền file, lỗi giữa chừng và resource limits**.

### Không nên mặc định dùng làm định dạng lâu dài

- File cấu hình cần con người đọc/sửa và review trên Git.
- API public đa ngôn ngữ.
- Dữ liệu liên tục nâng cấp qua nhiều phiên bản ứng dụng.
- Hệ thống cần query/index/migration dễ quản lý.
- Payload không tin cậy đến từ internet.
- Dữ liệu nhạy cảm cần kiểm soát chính xác điều gì được lưu.

Đối với các nhu cầu đó, nên cân nhắc JSON/YAML/Properties/CSV, database, hoặc schema-based binary formats tùy bài toán. Java serialization lưu **cấu trúc class Java**, nên coupling giữa data và code có thể cao.

### Khác biệt với lưu database

Java serialization không cung cấp:

- Transaction nhiều object/file theo nghĩa database.
- Index, truy vấn theo field và isolation.
- Version migration tự động.
- Quyền truy cập, sao lưu và crash recovery như một hệ DBMS.
- Kiểm soát bảo mật chỉ vì class triển khai Serializable.

Không nên dùng `ObjectOutputStream` như database đa người dùng nếu hệ thống cần tính nhất quán cao.

## 16. Ghi file an toàn hơn bằng tệp tạm

Một thao tác trực tiếp:

```java
try (ObjectOutputStream out =
         new ObjectOutputStream(Files.newOutputStream(target))) {
    out.writeObject(root);
}
```

có thể làm tệp đích bị ghi dở nếu serialize hoặc I/O thất bại. Với dữ liệu nội bộ cần giữ bản cũ khi có lỗi, nên:

1. Tạo tệp tạm riêng ở **cùng thư mục** với đích.
2. Dùng `ObjectOutputStream` ghi toàn bộ graph và đóng stream thành công.
3. Nếu có yêu cầu, đồng bộ dữ liệu tệp tạm và kiểm tra có thể đọc lại/validate.
4. Move/replace tệp đích theo khả năng filesystem và policy bảo đảm atomic đã định.
5. Dọn tệp tạm khi thất bại và có chiến lược recovery sau crash.

Ví dụ mô hình:

```java
Path target = Path.of("snapshot.ser").toAbsolutePath();
Path temporary = Files.createTempFile(
        target.getParent(), ".snapshot-", ".tmp");

try {
    try (ObjectOutputStream output = new ObjectOutputStream(
            Files.newOutputStream(temporary))) {
        output.writeObject(root);
    }

    Files.move(temporary, target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
} finally {
    Files.deleteIfExists(temporary);
}
```

Đây là **đoạn minh họa trong method**, cần imports, xử lý `IOException`, và `root` phải là object serializable. Cần chú ý:

- `ATOMIC_MOVE` có thể không được hỗ trợ; một lần gọi `Files.move` có thể ném `AtomicMoveNotSupportedException`.
- Theo API `Files.move`, khi có `ATOMIC_MOVE`, các option khác bị bỏ qua; nếu target đã tồn tại thì hành vi replace phụ thuộc provider. Không coi đoạn minh họa là bảo đảm portable atomic replacement.
- `finally` chỉ dọn temp trong tiến trình chạy bình thường; mất điện/crash bất ngờ cần cơ chế thu dọn khác.
- Khả năng sống sót qua mất điện còn liên quan đồng bộ dữ liệu và directory metadata; `ObjectOutputStream.close()` không bảo đảm đồng bộ xuống thiết bị lưu trữ.
- Không đọc thử bằng ObjectInputStream nếu nguồn temp có thể bị kẻ khác sửa; giữ thư mục ở vùng tin cậy và áp dụng giới hạn thích hợp.

### Ghi/đọc nhiều object vào một stream

Có thể ghi tuần tự:

```java
out.writeObject(header);
out.writeObject(team);
out.writeObject(metadata);
```

Và đọc đúng thứ tự:

```java
Header h = (Header) in.readObject();
Team t = (Team) in.readObject();
Metadata m = (Metadata) in.readObject();
```

Các dòng này là **mã minh họa**, yêu cầu khai báo `Header`/`Team`/`Metadata` cùng các biến. Đây là một stream chung, nên bảng handle có thể giữ alias qua nhiều lần write. Ứng dụng phải tự định nghĩa versioning và biết số object cần đọc; đừng lặp tới EOF rồi tự đoán cấu trúc vì stream không phải một định dạng message tự mô tả cho mọi bài toán.

## 17. Tối ưu kích thước và hiệu năng

Java native serialization có chi phí từ class descriptors, graph traversal, handle tables, metadata và nhiều đối tượng trung gian. Một số lưu ý:

- Đừng cho rằng kích thước serialized bytes bằng tổng sizeof fields. Nó có overhead format và thông tin graph.
- `ObjectOutputStream` lưu handle cho object đã viết. Với stream kéo dài rất lâu, bảng handle có thể giữ references tới nhiều object; `reset()` có thể giúp giải phóng trạng thái và mở một “lượt snapshot” mới, nhưng thay đổi semantics chia sẻ qua các lượt.
- Tránh serialize toàn bộ cache hay object graph lớn khi chỉ cần vài giá trị DTO.
- Nếu cần lưu nhiều record độc lập với random access, một stream tuần tự không tự có index. Có thể cần định dạng record riêng hoặc database.
- Có thể bọc stream bằng `BufferedOutputStream`/`BufferedInputStream` để giảm số lần I/O trên một số nguồn; benchmark theo use case.
- Nếu dùng `GZIPOutputStream` bọc `ObjectOutputStream`, xác định đúng thứ tự filter; ví dụ `new ObjectOutputStream(new GZIPOutputStream(raw))` làm byte serialization được nén trước khi ra raw. Đóng wrapper ngoài sẽ đóng các layer dưới và hoàn tất gzip.
- Đừng dùng nén làm bảo mật: serialized data nén vẫn có thể chứa thông tin nhạy cảm, và giải nén một stream không tin cậy có thêm rủi ro bomb.

### Nén serialization stream — ví dụ minh họa

```java
try (OutputStream file = Files.newOutputStream(path);
     GZIPOutputStream gzip = new GZIPOutputStream(file);
     ObjectOutputStream objectOut = new ObjectOutputStream(gzip)) {
    objectOut.writeObject(root);
}
```

Các biến `path` và `root` cần được khai báo. Đọc lại tương ứng `new ObjectInputStream(new GZIPInputStream(Files.newInputStream(path)))`. Cả Java object stream và gzip đều có header, và **thứ tự wrapper phải đối xứng**; không cố đọc byte GZIP trực tiếp bằng ObjectInputStream.

## 18. Lỗi thiết kế thường gặp

1. **Chỉ implements Serializable ở root rồi tưởng mọi field đều an toàn.** Một reference non-transient tới object không serializable có thể gây `NotSerializableException`.
2. **Nhầm Serialization với JSON.** Đây là định dạng byte riêng của Java, không phải file văn bản đọc bằng text editor.
3. **Nhầm equals với identity.** Dùng `==` để kiểm tra alias/reference sharing sau khi restore graph.
4. **Không xét vòng tham chiếu.** Java serialization có quản lý handles, nhưng serializer khác không nhất thiết tự giữ vòng như vậy.
5. **Lưu trực tiếp file handle, thread hoặc connection trong object graph.** Những resource đang chạy không nên được kỳ vọng phục hồi từ một byte stream.
6. **Quên transient cho state không cần persist.** Token/cache/tài nguyên ngắn hạn có thể vô tình trở thành phần dữ liệu lưu nếu Serializable.
7. **Coi transient là mã hóa.** Nó chỉ bỏ field theo cách lưu mặc định; không thay cơ chế bảo mật.
8. **Coi static là trạng thái được lưu theo instance.** Java serialization mặc định không snapshot static fields.
9. **Không khai báo serialVersionUID và không kiểm thử phiên bản.** Thay đổi class có thể làm dữ liệu cũ không đọc được.
10. **Giữ serialVersionUID giống nhau nhưng giả định mọi sửa đổi đều tương thích.** Cần validation và test schema thực tế.
11. **Dựa vào constructor để validate sau deserialization.** Constructor thường của Serializable class không được gọi theo cách `new` trong quá trình restore.
12. **Không đọc đến đúng thứ tự object đã ghi.** Object stream có cấu trúc; đọc sai thứ tự có thể gây cast lỗi hoặc dữ liệu không mong muốn.
13. **Ghi lại cùng object trên một ObjectOutputStream mà tưởng mỗi lần là snapshot mới.** Handle table có thể khiến lần sau là reference.
14. **Không xử lý NotSerializableException/InvalidClassException.** File có thể dở hoặc dữ liệu không phù hợp version.
15. **Giải tuần tự hóa input không tin cậy.** Đây là bề mặt tấn công nghiêm trọng, filter không đủ thay thế lựa chọn định dạng an toàn.
16. **Không giới hạn byte/depth/refs/arrays.** Dữ liệu lớn/phức tạp có thể gây cạn tài nguyên.
17. **Dùng serialized objects như database lâu dài.** Khó migration/query/transaction/interop.
18. **Ghi trực tiếp đè bản lưu duy nhất.** Crash giữa chừng có thể làm mất snapshot cũ.
19. **Tin rằng close là fsync.** Việc đóng stream không cam kết durability qua mất điện.
20. **Thiếu kiểm tra kiểu và invariant sau readObject.** Object phục hồi cần phù hợp trạng thái nghiệp vụ hiện hành.
21. **Sử dụng inner class không static vô ý.** Reference ngầm đến outer instance có thể khiến đồ thị khó serialize.
22. **Ghi graph chia sẻ nhưng tưởng sẽ tạo bản copy tách rời cho mỗi reference.** Cơ chế handle bảo toàn chia sẻ trong stream.
23. **Kỳ vọng một serialized object tự mang toàn bộ bytecode của class.** Runtime đọc phải có class phù hợp để dựng lại.
24. **Quên close ObjectInputStream/ObjectOutputStream.** Tài nguyên rò rỉ, byte cuối chưa flush hoặc file dở.
25. **Dùng ObjectInputFilter như bảo đảm chống mọi payload độc hại.** Cần threat model, allowlist, giới hạn và tốt nhất tránh native deserialization ở trust boundary.

## 19. Bài tập thực hành

### Bài 1: Vòng ba nút

Tạo ba `Node` A, B, C với `A.next=B`, `B.next=C`, `C.next=A`. Serialize A rồi deserialize thành A2. Kiểm tra:

```java
boolean cycle = a2.next.next.next == a2;
```

Nếu khôi phục đúng graph, `cycle == true`. Không dùng `toString` đệ quy vô hạn để in chuỗi node.

### Bài 2: Cùng một đối tượng ở hai field

Tạo object `Order` có hai field `billingAddress` và `shippingAddress` cùng trỏ một instance `Address`. Sau deserialize, kiểm tra:

```java
restored.billingAddress == restored.shippingAddress
```

Nếu cùng instance được lưu trong graph, kết quả thường là true theo semantics mặc định. Sau đó đổi bài toán thành hai `Address` khác instance nhưng nội dung bằng nhau và quan sát sự khác biệt.

### Bài 3: Bỏ qua transient

Thêm `transient String token` vào class serializable; gán token trước khi lưu. Sau khi đọc, kiểm tra token là `null` theo cơ chế mặc định; tự giải thích vì sao không thể dùng object cũ để suy ra trạng thái token mới.

### Bài 4: Lỗi non-serializable field

Thêm field `Thread worker` không transient vào một class `Serializable`, gán `new Thread()`. Dự đoán exception khi ghi. Sau đó đổi field thành `transient Thread worker` và xác định cách ứng dụng sẽ khởi tạo lại worker khi cần.

### Bài 5: Kiểm thử nâng cấp phiên bản

Lưu dữ liệu bằng version V1 có `serialVersionUID=1L`. Thêm field ở version V2 vẫn giữ UID, đọc dữ liệu V1, quan sát giá trị mặc định của field mới và bổ sung validation/migration. Sau đó thử UID khác để hiểu `InvalidClassException`. Làm trên dữ liệu thử nghiệm, không thay file production.

### Bài 6: Giới hạn input

Viết lớp DTO lưu tối đa vài trường String và số nguyên. Tạo một format lưu được kiểm soát; so sánh native serialization với JSON/format binary có schema. Đánh giá độ dễ đọc, versioning, kích thước và khả năng parse dữ liệu không tin cậy.

## 20. Câu hỏi tự kiểm tra

**Câu 1.** `Serializable` yêu cầu implement method nào?  
**Không yêu cầu method nào.** Đây là marker interface để Java serialization nhận diện lớp.

**Câu 2.** Nếu `root` serializable nhưng tham chiếu `Thread` non-null không transient, ghi root có thể xảy ra gì?  
**`NotSerializableException`**, trừ khi đã custom hóa cấu trúc lưu để không ghi field ấy.

**Câu 3.** Serialize một List chứa cùng object A ở hai vị trí. Deserialize và kiểm tra hai vị trí bằng `==` có ý nghĩa gì?  
**Chứng minh sharing trong graph phục hồi** nếu true; không phải chỉ có giá trị field bằng nhau.

**Câu 4.** Một graph chứa vòng A→B→A có làm ObjectOutputStream đệ quy mãi không?  
**Không theo cơ chế chuẩn**, vì stream quản lý handles tham chiếu khi ghi đồ thị.

**Câu 5.** Field `transient String password` có được mặc định lưu lại không?  
**Không.** Nhưng phải xem custom hooks/field khác và không coi transient là phương pháp bảo mật hoàn chỉnh.

**Câu 6.** `static` field có được snapshot riêng cho mỗi instance không?  
**Không.** Nó thuộc trạng thái class, không phải instance field được lưu mặc định.

**Câu 7.** Giữ serialVersionUID giống nhau có bảo đảm mọi phiên bản đọc được nhau không?  
**Không.** Còn phụ thuộc thay đổi field/hierarchy, custom hooks, logic ứng dụng và compatibility.

**Câu 8.** `readObject` có chạy constructor của Serializable class như gọi `new` không?  
**Không theo cơ chế class Serializable thông thường.** Constructor của superclass đầu tiên không Serializable có vai trò theo đặc tả; record/enum có quy tắc riêng.

**Câu 9.** Vì sao không nhận Java serialized bytes từ một HTTP API công khai rồi gọi `ObjectInputStream.readObject()`?  
**Vì native deserialization có thể kích hoạt dựng object và hook nguy hiểm**; cần định dạng/parsing được kiểm soát và không vượt trust boundary.

**Câu 10.** Nếu ghi cùng một object hai lần lên một ObjectOutputStream và object đã bị thay đổi giữa hai lần, bản ghi thứ hai chắc chắn là snapshot mới không?  
**Không.** Stream có bảng handle; lần ghi sau có thể chỉ là tham chiếu đến object đã ghi trước.

**Câu 11.** `ObjectOutputStream.close()` có bảo đảm file bền qua mất điện không?  
**Không.** Cần chính sách durability và filesystem phù hợp.

**Câu 12.** Lúc nào nên ưu tiên DTO + schema thay vì Serializable?  
**Khi cần public API, dữ liệu không tin cậy, phiên bản lâu dài, nhiều ngôn ngữ hoặc kiểm soát chính xác định dạng lưu.**

## 21. Tóm tắt

- **Java serialization** biến object và **đồ thị đối tượng** thành byte stream, đọc lại bằng `ObjectInputStream`; class thông thường cần triển khai marker `Serializable`.
- **Object graph bao gồm alias và cycle**. Java giữ quan hệ chia sẻ tham chiếu trong phạm vi stream; restored instances khác original instances.
- **Field non-static, non-transient** tham gia serialization mặc định; `transient` không lưu, `static` không phải state instance. Một reference non-serializable có thể khiến ghi thất bại.
- **`serialVersionUID`** quản lý một phần tương thích version; không tự thay migration/testing.
- **Deserialization có thể bỏ qua constructor thường và kích hoạt hook**, nên phải validate invariants, đặc biệt khi class thay đổi.
- **Không deserialize dữ liệu không tin cậy**. `ObjectInputFilter` là lớp bảo vệ bổ sung, không thay thế threat model và định dạng an toàn.
- Với persistence quan trọng, cần **quy trình ghi file an toàn, versioning, giới hạn tài nguyên và recovery**; native serialization không phải database hay transaction.

### Tài liệu tham khảo Java SE 17

- [Serializable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serializable.html)
- [ObjectOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectOutputStream.html)
- [ObjectInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html)
- [ObjectInputFilter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html)
- [Java Object Serialization Specification](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/index.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serialization-basics-002">ObjectOutputStream và ObjectInputStream</a>

<details>
<summary>Xem chi tiết</summary>

# ObjectOutputStream và ObjectInputStream trong Java: tuần tự hóa và khôi phục đối tượng

## 1. Tuần tự hóa (serialization) là gì?

**Tuần tự hóa đối tượng** là quá trình chuyển một đồ thị đối tượng Java sang dạng byte theo giao thức Java Object Serialization. **Giải tuần tự hóa** (deserialization) là quá trình đọc lại byte đó và khôi phục các đối tượng cùng quan hệ tham chiếu theo quy tắc của giao thức.

Java Core I/O cung cấp:

- `java.io.ObjectOutputStream`: ghi object graph, primitive values và các giá trị khác vào một `OutputStream` bên dưới.
- `java.io.ObjectInputStream`: đọc từ một `InputStream` chứa dữ liệu theo định dạng Java Object Serialization, khôi phục object graph.
- `java.io.Serializable`: marker interface (không có phương thức) thông báo lớp có thể tham gia cơ chế tuần tự hóa chuẩn.
- `java.io.Externalizable`: giao diện cho trường hợp lớp tự chịu trách nhiệm định dạng dữ liệu qua `writeExternal`/`readExternal`, khác với `Serializable` mặc định.

Bài học tập trung vào **`ObjectOutputStream` và `ObjectInputStream`**, cách kết hợp `Serializable`, metadata lớp, vòng đời stream, những trường được giữ/bỏ, xử lý lỗi, tính tương thích phiên bản và an toàn khi đọc dữ liệu từ bên ngoài.

> **Điểm quan trọng:** Java Object Serialization là định dạng nhị phân dành cho hệ sinh thái Java; nó **không** phải JSON, không phải chỉ ghi chuỗi `toString()`, không phải snapshot toàn bộ bộ nhớ JVM, và không mặc định mã hóa hoặc xác thực dữ liệu. Không dùng `ObjectInputStream` để giải tuần tự hóa dữ liệu **không đáng tin cậy** nếu không có kiến trúc, kiểm soát và chính sách bảo mật phù hợp; trong nhiều trường hợp nên chọn định dạng dữ liệu an toàn hơn.

## 2. Luồng dữ liệu đi qua các lớp nào?

```text
GHI                                              ĐỌC
---                                              ---
Đối tượng Java                                   Đối tượng Java
    |                                                 ^
    v                                                 |
ObjectOutputStream.writeObject(obj)         ObjectInputStream.readObject()
    |                                                 ^
    v                                                 |
Các byte theo Java serialization protocol       Parse stream + dựng object graph
    |                                                 ^
    v                                                 |
OutputStream                                    InputStream
    |                                                 ^
    +--> File / socket / memory ----------------------+
```

Một ứng dụng có thể kết hợp với `Files.newOutputStream(path)`, `FileOutputStream`, `BufferedOutputStream`, `ByteArrayOutputStream`, `Socket.getOutputStream()` hay các stream khác. Đọc bằng các lớp input tương ứng. **Stream bên dưới phải mang đúng định dạng mà phía đọc kỳ vọng**.

Ví dụ khuôn mẫu:

```java
try (ObjectOutputStream out =
         new ObjectOutputStream(Files.newOutputStream(file))) {
    out.writeObject(myObject);
}

try (ObjectInputStream in =
         new ObjectInputStream(Files.newInputStream(file))) {
    Object restored = in.readObject();
}
```

Đây là **đoạn minh họa trong một phương thức**, không phải class độc lập: cần `file`, `myObject`, các import thích hợp và xử lý `IOException` / `ClassNotFoundException`. Việc dùng `try-with-resources` bảo đảm stream được đóng kể cả khi có lỗi.

## 3. Điều kiện: lớp phải hỗ trợ `Serializable`

Lớp cần được lưu bằng cơ chế mặc định thường triển khai `java.io.Serializable`:

```java
import java.io.Serializable;

public class Student implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private int age;

    public Student(String name, int age) {
        this.name = name;
        this.age = age;
    }
}
```

Đây là class có thể biên dịch riêng trong `Student.java`. `Serializable` là **marker interface**: không cần implement một phương thức bắt buộc. Java serialization dùng cơ chế nội bộ để ghi dữ liệu của các field không thuộc diện loại trừ và đủ điều kiện.

### 3.1. Có phải field nào cũng được ghi?

Quy tắc mặc định cho một lớp `Serializable`:

| Loại field | Ghi theo serialization mặc định? | Giải thích |
| --- | --- | --- |
| Instance field thông thường (`int`, `String`, object serializable...) | Có | Ghi giá trị và các object tham chiếu trong graph |
| `private` instance field | Có | `private` **không** ngăn tuần tự hóa |
| `final` instance field | Có thể có | Giá trị được lưu/khôi phục theo giao thức, không phụ thuộc setter |
| `static` | Không | Thuộc về class/JVM, không phải state riêng của instance |
| `transient` | Không trong dạng mặc định | Khi đọc lại nhận giá trị mặc định nếu không có logic tùy chỉnh |
| Field tham chiếu object không serializable | Có thể gây `NotSerializableException` | Không thể lưu toàn graph bằng cơ chế mặc định nếu gặp object không serializable |

`transient` chỉ tác động cơ chế **ghi field mặc định**; không bảo đảm giá trị bí mật bị xóa khỏi RAM, không bảo đảm object đó không được ghi qua field khác hoặc qua logic `writeObject()` tùy chỉnh.

### 3.2. Serializable áp dụng cho cả đồ thị đối tượng

Nếu `Order` triển khai `Serializable` nhưng chứa field `PaymentGateway gateway` (không serializable) và field ấy không `transient`, `out.writeObject(order)` thường ném `NotSerializableException` khi tới đối tượng `gateway`.

Các hướng giải quyết có chủ đích:

- Chỉ lưu dữ liệu cần thiết; đánh dấu phụ thuộc runtime như connection, executor, logger state hoặc service client là `transient`.
- Thiết kế DTO serializable độc lập với service/runtime objects.
- Không ép một thư viện hoặc class bên thứ ba thành `Serializable` bằng thay đổi tùy tiện.
- Nếu cần giữ trạng thái dưới định dạng ổn định, cân nhắc JSON/CBOR/Protobuf hoặc schema phù hợp hơn thay vì serialize nguyên object domain.

Một object graph có thể chứa vòng tham chiếu và các object dùng chung; Java serialization theo dõi **identity của các object đã ghi** để ghi tham chiếu tương ứng. Điều đó dẫn tới hành vi quan trọng ở phần sau.

## 4. Ví dụ Java 17+ hoàn chỉnh: ghi, đọc và bảo toàn tham chiếu

Ví dụ dùng hai người cùng chia sẻ một object `Address`, ghi đối tượng người thứ nhất hai lần và người thứ hai một lần. Sau khi ghi, chương trình thay đổi một static field để chứng minh **static không nằm trong dữ liệu serialized**; field `transient` được khôi phục thành `null`.

Tạo file **`SerializationDemo.java`**:

```java
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

public class SerializationDemo {
    private static final class Address implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String city;

        private Address(String city) {
            this.city = city;
        }
    }

    private static final class Person implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private static String appVersion = "v1";

        private final String name;
        private final Address address;
        private transient String sessionToken;

        private Person(String name, Address address, String token) {
            this.name = name;
            this.address = address;
            this.sessionToken = token;
        }
    }

    public static void main(String[] args)
            throws IOException, ClassNotFoundException {

        Path file = Files.createTempFile("object-stream-", ".bin");

        try {
            Address shared = new Address("Ha Noi");
            Person an = new Person("An", shared, "secret-A");
            Person binh = new Person("Binh", shared, "secret-B");

            try (ObjectOutputStream out =
                    new ObjectOutputStream(Files.newOutputStream(file))) {
                out.writeObject(an);
                out.writeObject(an);   // Cùng identity được ghi lần hai.
                out.writeObject(binh); // Có chung Address với an.
            }

            Person.appVersion = "v2";

            try (ObjectInputStream in =
                    new ObjectInputStream(Files.newInputStream(file))) {

                Person first = (Person) in.readObject();
                Person second = (Person) in.readObject();
                Person third = (Person) in.readObject();

                System.out.println("first: " + first.name
                        + " / " + first.address.city);
                System.out.println("same object twice: " + (first == second));
                System.out.println("shared address: "
                        + (first.address == third.address));
                System.out.println("transient token: " + first.sessionToken);
                System.out.println("static app version: " + Person.appVersion);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac SerializationDemo.java
java SerializationDemo
```

Kết quả:

```text
first: An / Ha Noi
same object twice: true
shared address: true
transient token: null
static app version: v2
```

**Vì sao ra kết quả đó?**

1. `Person` và `Address` đều `implements Serializable`, vì vậy `ObjectOutputStream` có thể duyệt graph.
2. Lần ghi đầu tiên `an` bao gồm cả `shared`. Lần ghi `an` thứ hai, cùng một stream, thường được biểu diễn thành **back-reference** tới object đã ghi, không sao chép lại field như một object độc lập.
3. `binh` là object `Person` khác nhưng có field `address` trỏ đến **cùng** `Address`; khi giải tuần tự hóa, `first.address == third.address` đúng.
4. `sessionToken` được khai báo `transient`. Reader không gọi constructor `Person(name,address,token)` để gán token; field reference được khôi phục mặc định thành `null`.
5. `appVersion` thuộc class, không phải instance; thay giá trị trong JVM thành `"v2"` trước lúc đọc, kết quả sau đọc vẫn là `"v2"`.

Kết quả so sánh `==` trong ví dụ nhằm chứng minh **identity** bên trong một graph/stream, không phải khẳng định hai lần đọc từ **hai stream mới khác nhau** sẽ cho cùng object instance.

## 5. `ObjectOutputStream`: những thao tác quan trọng

### `writeObject(Object)`

Ghi object theo giao thức Java serialization; nếu object `null`, có thể ghi dấu hiệu `null`. Nếu graph chứa một object không serializable mà không được loại trừ phù hợp, có thể ném `NotSerializableException`.

```java
out.writeObject(student);
out.writeObject(null);
```

Đây là đoạn minh họa trong phạm vi đã mở `ObjectOutputStream out`.

### `writeInt`, `writeBoolean`, `writeUTF` và `DataOutput`

`ObjectOutputStream` triển khai `ObjectOutput`, có các phương thức ghi primitive và UTF string:

```java
out.writeInt(2026);
out.writeBoolean(true);
out.writeUTF("Hello");
out.writeObject(student);
```

Reader **phải đọc cùng thứ tự và đúng loại**:

```java
int year = in.readInt();
boolean flag = in.readBoolean();
String message = in.readUTF();
Student s = (Student) in.readObject();
```

Hai đoạn là code minh họa, giả định hai đầu cùng định dạng, stream và class `Student` phù hợp. Ghi bằng `writeInt` rồi đọc bằng `readObject` là sai giao thức; có thể gặp `OptionalDataException`, `StreamCorruptedException` hoặc lỗi khác tùy ngữ cảnh.

`writeUTF/readUTF` dùng **modified UTF-8** và định dạng độ dài riêng của `DataOutput`/`DataInput`, không phải UTF-8 thuần không header. `writeUTF` giới hạn phần biểu diễn mã hóa của chuỗi ở 65.535 byte và có thể ném `UTFDataFormatException`; đừng dùng nó cho text tùy ý có kích thước lớn.

### `flush()`, `close()`

`flush()` đẩy các byte đang được buffer bởi output stream xuống stream bên dưới theo hợp đồng `Flushable`; nó **không đồng nghĩa** dữ liệu đã được fsync xuống thiết bị bền vững hoặc đã nhận tại phía mạng.

`close()` đóng `ObjectOutputStream` cùng stream bên dưới. Với file đơn giản, hãy dùng `try-with-resources` để tránh tệp dở do không đóng. Nếu cần độ bền ghi qua crash, áp dụng riêng cơ chế flush/force ở tầng file/channel và thiết kế atomic write phù hợp.

### `writeUnshared(Object)` và `reset()`

`ObjectOutputStream` mặc định quản lý bảng object handles để bảo toàn reference. Các API `writeUnshared` và `reset` thay đổi cách stream xử lý chia sẻ object, nhưng **không nên diễn giải chúng là “deep clone” bảo đảm cho mọi object con**.

- `writeUnshared(obj)` yêu cầu ghi root object như giá trị chưa chia sẻ theo quy tắc giao thức; các object con mà root tham chiếu vẫn có thể được chia sẻ bằng cơ chế mặc định.
- `reset()` xóa trạng thái bảng reference của stream để các object được ghi sau đó không dùng back-reference của phần trước. Reader theo giao thức cũng nhận marker reset tương ứng.
- Không gọi `reset()` tùy tiện bên trong quá trình đang serialize một object graph đang active; API có quy tắc và có thể ném `IOException`.

## 6. `ObjectInputStream`: đọc dữ liệu và ngoại lệ

Tạo `ObjectInputStream` từ stream đã có header serialization, rồi gọi `readObject()`:

```java
try (ObjectInputStream in =
        new ObjectInputStream(Files.newInputStream(file))) {
    Object value = in.readObject();
    if (value instanceof Student student) {
        System.out.println(student);
    } else {
        throw new IOException("Unexpected object type");
    }
}
```

Đây là ví dụ trong phương thức, với biến `file` và lớp `Student` có sẵn; pattern matching `instanceof` dùng được trong Java 17.

`readObject()` khai báo `IOException` và `ClassNotFoundException`. Trước khi sử dụng, phải kiểm tra **type dự kiến**. Tuy vậy, kiểm tra `instanceof` **sau** `readObject()` không ngăn các đối tượng độc hại đã tham gia quá trình giải tuần tự hóa; kiểm soát an toàn cần diễn ra từ **trước khi** gọi `readObject()` và ở tầng thiết kế nguồn dữ liệu.

### 6.1. Đọc đúng số object

Nếu writer đã ghi ba object, reader nên đọc đúng ba object, hoặc dùng một protocol riêng khai báo số lượng. Không giả định chỉ cần gọi `readObject()` mãi rồi có một `null` sentinel tự động khi hết tệp.

Nếu gọi `readObject()` khi không còn dữ liệu hợp lệ, thường gặp `EOFException`. Đừng tự nuốt mọi `IOException` rồi coi là EOF: một stream bị hỏng hoặc bị cắt giữa object phải được xử lý như lỗi dữ liệu. Có thể chọn `writeInt(count)` trước một danh sách đối tượng nếu định dạng do chính ứng dụng kiểm soát, rồi reader đọc đúng số lượng ấy, với quota hợp lý.

### 6.2. Header và đồng bộ hai đầu socket

Constructor `ObjectOutputStream` ghi một **serialization stream header**. Constructor `ObjectInputStream` cần đọc và xác nhận header; nếu stream là socket và peer chưa ghi/flush header, quá trình tạo input stream có thể **chặn**.

Nếu hai peer đều tạo `ObjectInputStream` trước, cả hai có thể đợi nhau vì chưa ai gửi header. Với ứng dụng có giao thức đủ tin cậy để dùng serialization, tạo output stream trước và **`flush()` header**, sau đó tạo input stream:

```java
ObjectOutputStream out =
        new ObjectOutputStream(socket.getOutputStream());
out.flush(); // Cho peer đọc được serialization header.
ObjectInputStream in =
        new ObjectInputStream(socket.getInputStream());
```

Đây là đoạn minh họa, `socket` đã kết nối và ownership của stream/channel phải được quản lý rõ. Nếu lặp lại `new ObjectOutputStream` trên cùng một socket giữa chừng không có framing/protocol thống nhất, bạn có thể ghi nhiều header vào luồng và phá định dạng mà reader đang kỳ vọng.

**Lưu ý bảo mật:** gửi object Java qua socket chỉ phù hợp giữa các đầu mút **tin cậy và kiểm soát được**, vẫn cần authentication, giới hạn tài nguyên và chính sách class nếu có đọc object; không biến Object Serialization thành network protocol cho client không đáng tin cậy.

## 7. Object identity, handle table và hiệu ứng “dữ liệu cũ”

Một hành vi dễ gây bất ngờ: sau lần `writeObject(obj)` đầu, sửa field của **cùng instance** rồi gọi `writeObject(obj)` lần hai trên **cùng `ObjectOutputStream`** không nhất thiết ghi lại giá trị mới. Writer có thể chỉ phát back-reference tới object đã ghi.

```java
MutableValue value = new MutableValue("old");
out.writeObject(value);

value.setText("new");
out.writeObject(value); // Back-reference có thể được ghi.
```

Đây là snippet trong phương thức (với class `MutableValue` serializable và `out` đã mở). Reader đọc hai lần có thể nhận **cùng một object** mang giá trị ban đầu `"old"`. Đó không phải lỗi ngẫu nhiên: stream có cơ chế tham chiếu/identity.

Nếu mục tiêu là ghi các snapshot riêng theo thời gian, nên thiết kế các DTO/snapshot **instance độc lập**, hoặc cân nhắc `out.reset()` khi phù hợp với protocol trước khi viết lại. `reset()` có thể làm stream lớn hơn vì không tận dụng reference cũ; không dùng nó bừa bãi như cách chữa lỗi mọi trường hợp.

### 7.1. `readUnshared()` không phải lệnh clone chung

`ObjectInputStream.readUnshared()` có quy tắc riêng tương ứng với dữ liệu được ghi theo cơ chế unshared; nếu một root là back-reference trong trường hợp không hợp lệ, có thể ném `ObjectStreamException`. Không dùng `readUnshared` vô điều kiện để giải quyết thiết kế graph. Dữ liệu nghiệp vụ có thể cần DTO mapping với semantics độc lập rõ ràng.

## 8. `serialVersionUID` và tương thích phiên bản

`serialVersionUID` là định danh phiên bản serialization của một lớp `Serializable`, kiểu `static final long`. Nó giúp kiểm tra sự tương thích của mô tả lớp khi reader khôi phục dữ liệu.

```java
@Serial
private static final long serialVersionUID = 1L;
```

Lệnh trên được đặt bên trong class `Serializable`, sử dụng `import java.io.Serial;` (annotation này có từ Java 14). Có thể không khai báo UID, khi đó Java tính một giá trị mặc định dựa trên cấu trúc lớp; cách đó **dễ thay đổi ngoài dự kiến** khi sửa code và không phù hợp cho dữ liệu cần đọc qua nhiều phiên bản.

| Thay đổi giữa writer và reader | Rủi ro/tác động |
| --- | --- |
| Đổi `serialVersionUID` | Thường `InvalidClassException` khi định danh không khớp |
| Thêm instance field thông thường | Có thể tương thích trong serialization mặc định; field mới nhận giá trị mặc định khi đọc stream cũ |
| Bỏ một instance field | Có thể tương thích; field cũ trong stream bị bỏ qua nếu không còn trong class reader |
| Đổi kiểu field, class hierarchy hoặc semantics | Có thể không tương thích, gây lỗi hoặc khôi phục dữ liệu sai nghiệp vụ |
| Đổi class name/package | Tên class trong stream không còn resolve trực tiếp, có thể `ClassNotFoundException` |
| Bỏ `Serializable` hoặc đổi cơ chế serialize | Có thể làm stream cũ không đọc được |

**Giữ nguyên UID không biến mọi thay đổi trở thành tương thích.** Nó chỉ tham gia kiểm tra phiên bản; bạn vẫn phải kiểm thử các trường hợp đọc dữ liệu phiên bản cũ, validate invariant và xử lý dữ liệu mặc định.

Ví dụ nếu `age` là field mới được thêm, reader của lớp mới đọc stream cũ có thể nhận `age=0`, không tự chạy constructor để đặt `age=18`. Nếu `0` không hợp lệ trong nghiệp vụ, hãy có bước validate/migrate trạng thái sau khi đọc.

Serialization không thay thế cơ chế migration schema của một kho lưu trữ lâu dài. Với dữ liệu cần bảo trì qua nhiều năm, format rõ ràng và versioned DTO thường tốt hơn serialized domain objects.

## 9. Constructor, `transient` và giá trị mặc định

Điểm dễ nhầm: khi giải tuần tự hóa một object `Serializable`, Java **không gọi constructor bình thường của chính lớp serializable đó** để thiết lập các field đã serialized. Các field được khôi phục theo giao thức. Constructor không đối số của **lớp cha đầu tiên không triển khai `Serializable`** phải phù hợp với quy tắc truy cập của serialization và sẽ được gọi trong quá trình khởi tạo phần superclass không serializable.

Hệ quả:

- Code kiểm tra invariant trong constructor của class serializable **không được dựa vào** như cơ chế bảo vệ dữ liệu đọc vào.
- Field `transient String token` thường khôi phục thành `null` nếu không có logic bổ sung.
- `transient int counter` thường khôi phục thành `0`.
- `static` không được lấy từ stream; nó thuộc trạng thái lớp đang chạy ở JVM.
- `final` field thông thường có thể được điền từ serialized state mặc định, không phải do constructor chạy lúc đọc.

Cần thiết kế việc khởi tạo lại tài nguyên runtime như executor, socket, lock không serializable hoặc cache sau khi đọc theo cách an toàn. Cơ chế hook như `private void readObject(ObjectInputStream in)` và `private void writeObject(ObjectOutputStream out)` là nâng cao, có hợp đồng riêng và có thể gây rủi ro nếu validate không cẩn thận; không cần dùng hook cho bài toán lưu DTO đơn giản.

## 10. Tùy biến serialization ở mức lớp

Nếu một lớp `Serializable` cần kiểm soát cách ghi/đọc field, nó có thể khai báo các phương thức private có chữ ký quy định:

```java
private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    // Có thể ghi thêm dữ liệu ở định dạng do lớp quyết định.
}

private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    // Validate và khôi phục invariant từ dữ liệu đã được đọc.
}
```

Đây là **hai phương thức minh họa phải đặt bên trong class Serializable** cùng các import `java.io.*`. Nếu dùng, writer và reader phải thống nhất schema phụ; ghi thêm gì thì phải đọc đúng thứ tự/định dạng. Không gọi `readObject` tùy chỉnh bằng tay từ bên ngoài.

Các phương thức `defaultWriteObject()` và `defaultReadObject()` chỉ hợp lệ trong ngữ cảnh callback serialization tương ứng. Không gọi chúng như API ghi/đọc đối tượng tùy ý.

### 10.1. Validate invariant sau khi đọc

Nếu nghiệp vụ đòi `balance >= 0`, class có thể xác nhận điều kiện sau `defaultReadObject`, ném `InvalidObjectException` khi không hợp lệ. Phải nhớ deserialization **có thể không đi qua constructor** kiểm tra dữ liệu. Với dữ liệu không tin cậy, validation sau khi dựng đối tượng **không đủ** để phòng mọi deserialization gadget hoặc tấn công cấp độ runtime.

### 10.2. `readObjectNoData`, `readResolve`, `writeReplace`

Java có các hook nâng cao để tương thích khi class không có dữ liệu trong stream, thay thế object, hoặc duy trì một số pattern object identity. Những hook này ảnh hưởng đến graph/reference và lifecycle, nên chỉ dùng khi hiểu kỹ hợp đồng API và có kiểm thử. Không nên thêm vào các DTO chỉ để “tối ưu” khi chưa có yêu cầu thực sự.

## 11. An toàn: không giải tuần tự hóa byte không đáng tin cậy

**Object deserialization có rủi ro đặc biệt** vì quá trình khôi phục có thể tải lớp và kích hoạt code trong các cơ chế serialization tùy chỉnh (ví dụ `readObject`, `readResolve`, `Externalizable.readExternal`) hoặc cấu trúc gadget từ thư viện có trên classpath. Việc chỉ cast object sau `readObject()` **không ngăn được** các thao tác xảy ra trước đó.

Do đó:

1. **Tốt nhất không dùng Java native object serialization cho dữ liệu do người dùng/Internet không tin cậy gửi tới.** Chọn định dạng với schema rõ ràng, parser giới hạn và validate dữ liệu (JSON, CBOR, Protobuf...) khi phù hợp.
2. Nếu một hệ thống cũ bắt buộc nhận serialized objects, áp dụng cả kiểm soát nguồn (authentication/integrity), cập nhật thư viện, cách ly xử lý, **object deserialization filter**, giới hạn depth/reference/array/bytes và kiểm tra class được phép.
3. Không dùng một rule chỉ chặn vài tên class nguy hiểm: allowlist theo miền đối tượng được phép thường dễ kiểm toán hơn.
4. Không mở một cổng `ObjectInputStream` trực tiếp ra Internet chỉ vì giao thức dễ code; đây là cấu trúc rủi ro cao.
5. Bộ lọc giúp **giảm bề mặt tấn công**, không bảo đảm loại bỏ mọi gadget, side effect, hành vi ứng dụng hoặc lỗ hổng thư viện.

### 11.1. `ObjectInputFilter` trong Java 17+

Java 17 hỗ trợ `java.io.ObjectInputFilter` để áp dụng kiểm soát khi giải tuần tự hóa. Một ví dụ tạo filter dạng pattern **minh họa**:

```java
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;

// Chạy trước lần readObject() đầu tiên.
// "com.example.dto.*" là package của lớp DTO trong ứng dụng minh họa.
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
        "maxdepth=12;maxrefs=100;maxbytes=1048576;maxarray=10000;"
      + "com.example.dto.*;!*");
in.setObjectInputFilter(filter);
```

Đây là **snippet cấu hình**, với `ObjectInputStream in` đã tạo. Thay `com.example.dto.*` bằng **các lớp thật cần được phép** và kiểm thử đầy đủ với object graph hợp lệ; các class ngoài allowlist có thể khiến dữ liệu hợp lệ bị từ chối. Bộ lọc không được diễn giải là kiểm tra mọi byte/string/primitive một cách hoàn hảo và không thay thế schema/business validation. Dòng `!* ` (khi viết thực tế không có khoảng trắng trong pattern `!* `) đặt chính sách từ chối các lớp không được phép phù hợp với pattern.

Có thể cấu hình filter toàn JVM hoặc dùng filter factory tùy môi trường. Quan trọng là chính sách áp dụng **trước khi deserialization**, đồng nhất qua các điểm vào, được kiểm thử với phiên bản JDK/thư viện chạy thực tế. Không viết bộ lọc chỉ để “qua test” rồi cho phép mọi loại class với wildcard `*`.

### 11.2. Giới hạn kích thước tệp và số lượng object

Object serialization chứa thông tin graph/metadata, cho phép nhiều object lồng nhau, mảng lớn, reference graph sâu. Chỉ giới hạn chiều dài file cũng chưa đủ: cần giới hạn depth, số reference, kích thước array, số byte và thời gian xử lý, cộng với hạn mức tổng cho nhiều request đồng thời.

Nếu lưu xuống file, dùng quyền file hạn chế, kiểm soát integrity theo nhu cầu, hạn chế đường dẫn do người dùng cung cấp và không tin rằng file cục bộ là “trusted” khi nhiều tác nhân có thể sửa nó.

### 11.3. Không nhầm `transient` với bảo mật

`transient` hữu ích để bỏ một field khỏi dạng serialization mặc định, nhưng **không mã hóa**, không xóa bản sao trong graph, không ngăn logic tùy chỉnh ghi field ấy, không kiểm soát nơi byte stream được lưu. Secret nên tránh đưa vào object cần serialize; nếu lưu state nhạy cảm, cần cơ chế mã hóa/authentication và quản lý khóa ở tầng thích hợp.

## 12. Quản lý vòng đời stream và hiệu năng

### 12.1. Có nên bọc Buffered streams?

Có thể bọc thêm `BufferedInputStream`/`BufferedOutputStream` khi có ý nghĩa đối với nguồn dữ liệu, nhưng không phải lúc nào cũng cải thiện rõ rệt. Ví dụ:

```java
try (ObjectOutputStream out = new ObjectOutputStream(
        new BufferedOutputStream(Files.newOutputStream(file)))) {
    out.writeObject(data);
}
```

Đây là đoạn minh họa trong phương thức; import `java.io.*` và `java.nio.file.*`, cùng các biến `file`, `data` do ứng dụng cung cấp. Đóng outer stream sẽ đóng underlying stream theo chuỗi wrapper. Đừng đóng underlying stream quá sớm khi `ObjectOutputStream` còn cần ghi.

### 12.2. Không liên tục tạo stream mới trong cùng một file để append

`ObjectOutputStream` ghi **stream header** khi được tạo. Mở lại một `ObjectOutputStream` ở append mode rồi ghi thêm object vào file đã chứa serialized data có thể thêm header ở giữa và khiến `ObjectInputStream` thông thường báo `StreamCorruptedException`.

Nhu cầu append cần một format/protocol được thiết kế riêng hoặc cơ chế quản lý stream liên tục; không chỉ thêm `StandardOpenOption.APPEND` và mong reader tự phân tích nhiều stream headers.

### 12.3. Rò bộ nhớ khi ghi rất nhiều object

Bảng handles của `ObjectOutputStream` giữ thông tin về các object đã xuất hiện trong stream. Với một stream sống lâu và liên tục ghi nhiều object mới, bảng này có thể tăng và tăng chi phí bộ nhớ. Có thể xem xét snapshot batching, quay vòng stream hợp lý hoặc `reset()` ở ranh giới đối tượng **nếu protocol cho phép**, nhưng việc reset làm mất sharing giữa các vùng trước/sau và có thể tăng kích thước stream.

### 12.4. Serialization mặc định không phải format tối ưu cho RPC

Java serialization mang tên lớp và metadata vào luồng, phụ thuộc classpath và serial UID, khó giao tiếp với ngôn ngữ khác và có rủi ro bảo mật cao hơn các định dạng schema được giới hạn. Với giao tiếp dịch vụ và lưu trữ có vòng đời dài, thường nên cân nhắc schema versioned và protocol tường minh.

## 13. Ngoại lệ thường gặp và cách phân biệt

| Ngoại lệ | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| `NotSerializableException` | Object trong graph không `Serializable` | Chỉ lưu DTO hợp lệ hoặc loại trừ runtime field |
| `InvalidClassException` | Lớp cục bộ không tương thích mô tả serialized class, serialVersionUID khác | Định nghĩa schema/migration, không chỉ ép UID |
| `ClassNotFoundException` | Class trong stream không có/không tìm được ở reader | Kiểm tra classpath, version, package; tránh tin class name trong nguồn không tin cậy |
| `StreamCorruptedException` | Stream header/mã dữ liệu sai hoặc dữ liệu không đúng serialization protocol | Kiểm tra format, append, socket stream alignment |
| `EOFException` | Đọc vượt dữ liệu hiện có hoặc stream bị cắt | Phân biệt EOF kỳ vọng với truncated object |
| `OptionalDataException` | Đọc object trong khi dữ liệu primitive/block-data hoặc trạng thái định dạng khác | Khớp thứ tự `writeInt`/`readInt`, `writeObject`/`readObject` |
| `InvalidObjectException` | Object được nhận nhưng vi phạm invariant do logic validate khi đọc | Xử lý như dữ liệu không hợp lệ |
| `WriteAbortedException` | Ghi object đã abort do lỗi trước đó và reader gặp exception record | Kiểm tra exception gốc và stream integrity |
| `IOException` | Lỗi file/socket/stream nói chung | Đóng stream đúng cách và không giả làm EOF |
| `ClassCastException` | Cast object trả về sai kiểu | Kiểm tra type và hợp đồng dữ liệu |

Nếu reader phản hồi `StreamCorruptedException: invalid stream header`, không lập tức sửa thành công bằng cách bỏ qua các byte đầu: cần xác minh byte stream có thực sự thuộc Java serialization format và chỉ có đúng một header cho luồng đang dùng.

## 14. Phân biệt với các cách ghi dữ liệu khác

| API/cách tiếp cận | Phù hợp | Điểm khác Java Object Serialization |
| --- | --- | --- |
| `DataOutputStream`/`DataInputStream` | Primitive và binary protocol tự định nghĩa | Không tự ghi class metadata/object graph, ứng dụng quản lý thứ tự/schema |
| `ObjectOutputStream`/`ObjectInputStream` | Object graph Java giữa các đầu mút đáng tin, cùng hệ sinh thái | Ghi class descriptor, reference handles, cần Serializable, có rủi ro deserialization |
| `BufferedWriter`/`BufferedReader` | Văn bản thuần | Charset/line format rõ, không serialize object tự động |
| JSON + thư viện phù hợp | Dữ liệu liên hệ giữa dịch vụ và ngôn ngữ khác | Schema và validation tường minh hơn; thư viện cũng cần cấu hình an toàn |
| Protobuf/Avro hoặc schema-based binary | RPC/event/storage versioned | Schema ID/version, field mapping rõ hơn; không lưu Java object identity mặc định |
| JDBC/ORM | Lưu trạng thái nghiệp vụ vào database | Có giao dịch, truy vấn, constraint; không phải snapshot toàn graph Java |

Ngay cả khi một lớp đã implements `Serializable`, nó không mặc nhiên trở thành định dạng lưu trữ bền vững hoặc hợp đồng API ổn định. Serialization là **một cơ chế kỹ thuật**, không thay thế thiết kế dữ liệu.

## 15. Những sai lầm thường gặp

1. **Quên `implements Serializable` ở một đối tượng trong graph:** ném `NotSerializableException`.
2. **Tưởng `private` được bỏ qua:** instance fields `private` vẫn có thể được ghi; dùng `transient` có chủ đích.
3. **Không khai báo `serialVersionUID`:** thay đổi class có thể phát sinh UID mặc định khác, phá tương thích.
4. **Chỉ đổi UID để chữa `InvalidClassException`:** không giải quyết semantic/structural mismatch.
5. **Tưởng deserialization gọi constructor bình thường:** invariant có thể không được khởi tạo như mong muốn.
6. **Thay field của object rồi `writeObject` lại cùng stream:** writer có thể chỉ ghi reference cũ.
7. **Đọc sai thứ tự `writeInt`/`writeObject`:** parser stream bị lệch định dạng.
8. **Tạo `ObjectInputStream` hai phía trước output:** cả hai chờ serialization header, gây deadlock trên socket.
9. **Mở `ObjectOutputStream` mới để append:** thêm stream header giữa file, reader thông thường không xử lý đúng.
10. **Coi EOF là dấu kết thúc object an toàn:** stream có thể bị cắt dở; cần protocol về số lượng và khôi phục lỗi.
11. **Dùng `transient` như mã hóa:** dữ liệu không được bảo mật chỉ bằng marker này.
12. **Deserialize byte từ Internet mà không filter/schema:** rủi ro security gadget và resource exhaustion.
13. **Dùng `readObject()` rồi cast để “lọc class”:** class nguy hiểm có thể được giải tuần tự hóa trước khi cast.
14. **Đóng underlying stream trước outer stream:** có thể làm dữ liệu chưa được flush/ghi đủ.
15. **Serialize object chứa socket, connection, executor:** state runtime thường không serializable hoặc không khôi phục đúng.
16. **Cho rằng stream luôn bảo toàn object thay đổi giữa các lần write:** handle table và `reset` có semantics riêng.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Một lớp triển khai `Serializable` nhưng có field tham chiếu object không serializable, không `transient`. `writeObject()` có thể thất bại vì sao?

**Đáp án:** Ghi object graph phải đi qua field ấy. Khi đối tượng đích không serializable và không có cơ chế thay thế hợp lệ, có thể ném `NotSerializableException`.

**Câu 2.** `transient String token` sau khi deserialize mặc định thành gì?

**Đáp án:** `null`, trừ khi class có logic tùy chỉnh khôi phục nó. `transient` không xóa token khỏi RAM hoặc những bản sao ở field khác.

**Câu 3.** Hai field cùng trỏ vào một `Address` trước khi serialize, sau khi read graph có nhất thiết thành hai bản sao không?

**Đáp án:** Không. Java Object Serialization thông thường theo dõi object identity; hai field có thể cùng tham chiếu một instance đã được khôi phục.

**Câu 4.** Cùng một `ObjectOutputStream`, `writeObject(obj)`, sửa field, `writeObject(obj)` lần nữa. Reader có chắc thấy field mới không?

**Đáp án:** Không. Lần hai có thể ghi reference thay vì state mới. Cần thiết kế snapshot và dùng `reset()` hoặc instance độc lập đúng mục đích.

**Câu 5.** `serialVersionUID=1L` được giữ nguyên thì có chắc mọi thay đổi lớp đọc dữ liệu cũ được không?

**Đáp án:** Không. UID là một thành phần kiểm tra compatibility, không bảo đảm mọi thay đổi kiểu field, kế thừa hay invariant đều tương thích.

**Câu 6.** Reader có thể gọi `readObject()` thay cho `readInt()` để lấy integer trước đó được ghi bằng `writeInt()` không?

**Đáp án:** Không; phải đọc cùng định dạng và thứ tự.

**Câu 7.** Vì sao hai đầu socket đều tạo `ObjectInputStream` trước khi tạo output có thể treo?

**Đáp án:** Constructor `ObjectInputStream` chờ stream header, nhưng chưa đầu nào ghi/flush header.

**Câu 8.** Kiểm tra `instanceof TrustedDto` sau `readObject()` có đủ chống malicious deserialization không?

**Đáp án:** Không. Graph có thể đã được dựng và các hook có thể đã thực thi. Cần tránh deserialize untrusted input hoặc dùng filter, quota, isolation và thiết kế schema phù hợp.

**Câu 9.** `static int counter` của class có được khôi phục theo giá trị writer đang có không?

**Đáp án:** Không theo cơ chế mặc định. Static state được quản lý bởi class trong JVM đọc.

**Câu 10.** Có thể dùng Java Object Serialization làm dữ liệu chia sẻ giữa Java và Python mà không có đặc tả bổ sung không?

**Đáp án:** Không phải lựa chọn phù hợp. Đây là format riêng Java; chọn protocol/schema độc lập ngôn ngữ sẽ đơn giản và bền vững hơn.

## 17. Checklist triển khai

- **Phạm vi dữ liệu:** chỉ serialize DTO/object graph thực sự cần thiết; tránh service/runtime state.
- **Class compatibility:** định nghĩa `serialVersionUID` có chủ đích, kiểm thử dữ liệu giữa phiên bản.
- **Field semantics:** phân biệt instance, static, transient, shared reference và object identity.
- **I/O:** đóng stream bằng `try-with-resources`; dùng đúng thứ tự `writeObject` và `readObject`.
- **Protocol:** nếu truyền nhiều object, quy định số lượng, loại, thứ tự và lifecycle stream; xử lý EOF/truncated data riêng.
- **Snapshot:** không mặc định việc ghi lại cùng object sẽ tái ghi field mới; chú ý handle table và `reset()`.
- **Security:** không đọc object stream từ nguồn không tin cậy; với hệ thống cũ bắt buộc dùng, áp dụng `ObjectInputFilter`, allowlist, quota, integrity/authentication, cập nhật dependencies và isolation.
- **Memory:** kiểm soát số object, độ sâu graph, mảng và kích thước stream; tránh lưu object graph khổng lồ không cần thiết.
- **Error handling:** xử lý rõ `ClassNotFoundException`, `InvalidClassException`, `NotSerializableException`, `EOFException` và `IOException`.
- **Chọn công nghệ:** cân nhắc JSON/Protobuf/định dạng schema versioned nếu cần giao tiếp ngoài JVM, storage dài hạn hoặc client không đáng tin.

## Tài liệu chính thức

- Java SE 17 — `ObjectOutputStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectOutputStream.html
- Java SE 17 — `ObjectInputStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html
- Java SE 17 — `Serializable`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serializable.html
- Java SE 17 — `ObjectInputFilter`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html
- Java Object Serialization Specification: https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/index.html
- JEP 290 — Filter Incoming Serialization Data: https://openjdk.org/jeps/290
- JEP 415 — Context-Specific Deserialization Filters: https://openjdk.org/jeps/415


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serialization-basics-003">writeObject, readObject và định dạng stream</a>

<details>
<summary>Xem chi tiết</summary>

# writeObject, readObject và định dạng stream trong Java Object Serialization

## 1. Từ đối tượng trong bộ nhớ tới luồng byte

**Java Object Serialization** là cơ chế biến trạng thái của một đối tượng Java và các đối tượng có thể đi tới qua các trường tham chiếu của nó thành **một luồng byte có cấu trúc**; quá trình ngược lại khôi phục một đồ thị đối tượng từ luồng đó.

Hai điểm vào thường gặp:

- `ObjectOutputStream.writeObject(obj)`: ghi biểu diễn tuần tự hóa của đối tượng vào `OutputStream`.
- `ObjectInputStream.readObject()`: đọc **đối tượng tiếp theo** trong stream, khôi phục dữ liệu/quan hệ tham chiếu, rồi trả một `Object`.

Hai phương thức không chuyển đối tượng thành JSON hay văn bản. Kết quả là **định dạng nhị phân Java Object Serialization Stream Protocol**, bao gồm header, type tags, class descriptors, field values, object references và dữ liệu bổ sung khi cần.

```text
Đối tượng Java                   LUỒNG NHỊ PHÂN                  Đối tượng Java
Message("HELLO", 7) ──writeObject──▶ AC ED 00 05 ... ──readObject──▶ Message("HELLO", 7)
                                ▲                   │
                        ObjectOutputStream     ObjectInputStream
                                │                   │
                           OutputStream          InputStream
```

Mục tiêu bài: sử dụng chính xác `writeObject`/`readObject`; hiểu những gì được lưu vào stream và cách đọc lại theo đúng thứ tự; phân biệt object data với stream header/class descriptor; nhận biết handle/reference sharing, `serialVersionUID`, `transient`, ngoại lệ và các rủi ro bảo mật của `readObject` trên dữ liệu không tin cậy.

**Lưu ý bảo mật quan trọng:** tuyệt đối không mặc định rằng byte nhận qua mạng, file tải từ bên ngoài hoặc blob người dùng gửi là an toàn để đưa vào `ObjectInputStream`. Giải tuần tự hóa Java có thể kích hoạt các cơ chế khôi phục đối tượng và callback trong classpath; phải ưu tiên giao thức dữ liệu an toàn hơn hoặc áp dụng giới hạn/kiểm soát nghiêm ngặt khi thực sự cần dùng.

## 2. Các lớp và giao diện cần biết

| Thành phần | Vai trò |
|---|---|
| `java.io.Serializable` | Marker interface báo class cho phép Java serialization mặc định |
| `ObjectOutputStream` | Viết đối tượng và dữ liệu primitive thành Object Serialization Stream |
| `ObjectInputStream` | Đọc đối tượng và primitive theo cùng quy tắc stream |
| `ObjectStreamConstants` | Header magic, version và các byte đánh dấu protocol (`TC_*`) |
| `ObjectStreamClass` | Mô tả class serializable; có thể xem `serialVersionUID` và field descriptors |
| `serialVersionUID` | Định danh phiên bản phục vụ kiểm tra tương thích class khi deserialize |
| `transient` | Loại field instance khỏi quá trình lưu mặc định |
| `ObjectInputFilter` | Cơ chế lọc/giới hạn class, độ sâu, tham chiếu, array và bytes khi đọc |
| `Externalizable` | Chế độ mà class tự định nghĩa toàn bộ cách ghi/đọc bằng `writeExternal`/`readExternal` |

Ở mức cơ bản, một class triển khai `Serializable` là đủ để cơ chế mặc định có thể ghi các field phù hợp:

```java
import java.io.Serializable;

public final class Student implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final int age;

    public Student(String name, int age) {
        this.name = name;
        this.age = age;
    }
}
```

Đây là class độc lập có thể biên dịch trong `Student.java`, **không có `main`**. Vì `String` serializable và `int` là primitive, trạng thái của instance `Student` này có thể được tuần tự hóa theo cơ chế mặc định. `serialVersionUID` ở đây là field `static final long` mang ý nghĩa kiểm tra phiên bản class, **không** phải dữ liệu instance ghi như một field thông thường.

### 2.1. Serializable không phải một method cần override

`Serializable` là **marker interface**: nó không khai báo `serialize()` hay `deserialize()`. Vì thế:

```java
class Entry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    int number = 42;
}
```

đã đáp ứng điều kiện cơ bản cho object serialization. Bạn **không bắt buộc** override `writeObject`/`readObject` trong mọi class; API `ObjectOutputStream` sẽ thực hiện cơ chế mặc định.

Nếu có một field tham chiếu đến object không serializable, việc ghi cả đồ thị có thể ném `NotSerializableException`, dù class gốc đã triển khai `Serializable`.

## 3. Tạo ObjectOutputStream và ghi đối tượng

Một `ObjectOutputStream` bọc bên ngoài `OutputStream` bất kỳ như `FileOutputStream`, `ByteArrayOutputStream` hoặc stream của socket:

```java
try (ObjectOutputStream out =
        new ObjectOutputStream(
                new FileOutputStream("student.bin"))) {

    out.writeObject(new Student("Mai", 20));
}
```

Đây là đoạn minh họa trong method xử lý `IOException` và đã có class `Student`. Cần import `java.io.ObjectOutputStream`, `java.io.FileOutputStream`.

Quy trình:

1. Tạo `ObjectOutputStream`. Constructor **ghi stream header** vào output bên dưới.
2. Gọi `writeObject(obj)` để lưu đối tượng.
3. Encoder duyệt đồ thị object có thể tới được qua các field serializable, ghi mô tả class và các giá trị cần thiết.
4. Luồng giữ bảng **handles** để nhận biết những tham chiếu đã được ghi trong stream hiện tại.
5. Khi đóng `ObjectOutputStream`, stream bên dưới cũng được đóng; dùng `try-with-resources` để hạn chế rò rỉ tài nguyên.

Bạn có thể ghi nhiều đối tượng **liên tiếp** trên cùng một `ObjectOutputStream`, sau đó phải đọc lại theo đúng trình tự. Không nên tạo `ObjectOutputStream` mới mỗi lần ghi thêm một đối tượng trên cùng một file rồi hy vọng mỗi header nối tiếp đều được một `ObjectInputStream` duy nhất hiểu như một danh sách object đơn giản.

### 3.1. `writeObject` có thể ghi cả đồ thị

Giả sử:

```java
final class Address implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    String city = "Ha Noi";
}
final class Person implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    Address address = new Address();
}
```

Hai class là mã minh họa đặt ở top level trong file Java (không `public`) hoặc nơi hợp lệ tương đương. Khi `out.writeObject(new Person())`, đối tượng `Address` được tham chiếu qua field `address` cũng được ghi, **không cần** gọi `writeObject(address)` riêng.

Nếu `Address` không hỗ trợ serialization và field `address` không transient, việc ghi `Person` có thể thất bại bằng `NotSerializableException`. Cơ chế này không tự chấp nhận mọi object trong graph.

### 3.2. Ghi primitive và đối tượng trong cùng stream

`ObjectOutputStream` còn triển khai `DataOutput`:

```java
out.writeInt(7);
out.writeUTF("xin chao");
out.writeObject(student);
```

Đây là snippet trong phạm vi đã khai báo `out` và `student`. Phía đọc phải đi theo **đúng thứ tự**:

```java
int version = in.readInt();
String title = in.readUTF();
Student student = (Student) in.readObject();
```

`readObject()` **không** được dùng thay `readInt()` để nhận một primitive được ghi bằng `writeInt()`. Trình tự dữ liệu và kiểu đọc không khớp có thể gây `OptionalDataException`, `StreamCorruptedException` hoặc lỗi khác tùy trạng thái stream. Lưu ý `writeUTF` có format *modified UTF-8* thuộc `DataOutput`, không phải cách mã hóa UTF-8 thông thường của mọi byte văn bản.

## 4. ObjectInputStream và cách đọc đối tượng

Ví dụ đọc lại file đã được viết:

```java
try (ObjectInputStream in =
        new ObjectInputStream(
                new FileInputStream("student.bin"))) {

    Object value = in.readObject();
    Student student = (Student) value;
}
```

Đây là snippet nằm trong method khai báo hoặc xử lý `IOException` và `ClassNotFoundException`, đồng thời có class `Student` tương thích trên classpath. `readObject()` trả kiểu tĩnh `Object` vì dữ liệu đầu vào có thể đại diện nhiều class khác nhau; Java không thể dựa vào generic để bảo đảm class được trả về.

**Các bước khi đọc:**

1. Constructor `ObjectInputStream` đọc và kiểm tra header stream.
2. `readObject` đọc type tags và descriptors, giải mã object graph với class phù hợp trong JVM.
3. Với class Serializable mặc định, trạng thái instance được khôi phục theo quy tắc field serialization.
4. Stream duy trì bảng handles ở phía đọc để các reference lặp/cycle được dựng thành quan hệ thích hợp.
5. Caller phải kiểm tra kiểu và **xác minh tính hợp lệ nghiệp vụ** của đối tượng khôi phục; kiểu Java hợp lệ không có nghĩa giá trị luôn đáng tin.

`ObjectInputStream` phải được dùng với dữ liệu thực sự tuân theo Java Object Serialization Stream Protocol. Một file JSON, ZIP hoặc file UTF-8 bất kỳ không thể tự nhiên đọc bằng `ObjectInputStream`.

### 4.1. Constructor có thể chờ header nếu đọc từ socket

`new ObjectInputStream(input)` **có thể block chờ bytes header**. Nếu hai bên của một socket đều tạo `ObjectInputStream` và đợi header trước khi bên nào tạo/flush `ObjectOutputStream`, chương trình có thể bị **deadlock**.

Pattern khi trao đổi giữa hai phía phải được thiết kế cho phù hợp:

```java
ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
out.flush(); // Đẩy header.
ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
```

Đây là snippet với `socket` hợp lệ; không phải chương trình TCP hoàn chỉnh. `flush()` làm cho bytes header do output stream tạo có cơ hội được bên kia nhận, nhưng hai phía vẫn phải thống nhất thứ tự thiết lập và giao thức thông điệp, timeout và vòng đời socket.

## 5. Ví dụ Java 17+ hoàn chỉnh: header, tham chiếu lặp, reset và transient

Ví dụ sau dùng `ByteArrayOutputStream`/`ByteArrayInputStream` để không phụ thuộc file hệ thống. Nó kiểm chứng các khía cạnh quan trọng nhất của `writeObject` và `readObject`: header nhị phân; cùng một object được ghi hai lần; `reset()` tạo ranh giới bảng handle mới; field `transient` không nằm trong trạng thái mặc định.

**Tệp:** `ObjectStreamDemo.java`

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class ObjectStreamDemo {

    private static final class Message implements Serializable {
        private static final long serialVersionUID = 1L;

        final String text;
        final int sequence;
        transient String secret;

        Message(String text, int sequence, String secret) {
            this.text = text;
            this.sequence = sequence;
            this.secret = secret;
        }
    }

    public static void main(String[] args) throws Exception {
        Message original = new Message("Xin chao", 7, "TOKEN");
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(sink)) {
            out.writeObject(original);
            out.writeObject(original); // Bảng handle: đã ghi instance này.
            out.reset();              // Xóa các handle đã nhớ.
            out.writeObject(original); // Ghi một biểu diễn object mới.
        }

        byte[] wire = sink.toByteArray();
        System.out.printf(
                "header: %02X %02X %02X %02X%n",
                wire[0] & 0xFF,
                wire[1] & 0xFF,
                wire[2] & 0xFF,
                wire[3] & 0xFF);

        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(wire))) {

            Message first = (Message) in.readObject();
            Message second = (Message) in.readObject();
            Message third = (Message) in.readObject();

            System.out.println("text: " + first.text
                    + ", sequence: " + first.sequence);
            System.out.println("first==second: " + (first == second));
            System.out.println("first==third: " + (first == third));
            System.out.println("transient restored: " + first.secret);
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac ObjectStreamDemo.java
java ObjectStreamDemo
```

**Stdout chính xác của ví dụ:**

```text
header: AC ED 00 05
text: Xin chao, sequence: 7
first==second: true
first==third: false
transient restored: null
```

**Vì sao có kết quả đó?**

1. Java serialization stream mặc định bắt đầu bằng magic `AC ED` và version `00 05`, tức `STREAM_MAGIC=0xACED` và `STREAM_VERSION=5`.
2. Lần `writeObject(original)` đầu lưu thông tin class và object; lần thứ hai trên **cùng instance** trong **cùng handle table** sử dụng tham chiếu tới object đã ghi. Vì thế hai lần `readObject` đầu trả về **cùng một instance** phía đọc (`== true`).
3. `out.reset()` xóa trạng thái handles của stream theo contract và ghi token reset. Lần ghi thứ ba tạo biểu diễn mới; phía đọc cũng đặt lại context, nên object thứ ba **khác reference** (`== false`).
4. Field `secret` được khai báo `transient`, do đó cơ chế mặc định **không lưu giá trị `"TOKEN"`**. Sau khi deserialize, field tham chiếu có giá trị mặc định `null`. 
5. `text` và `sequence` được khôi phục vì là các field instance serializable thông thường, class tương thích và có dữ liệu hợp lệ.

Mã đã được chạy kiểm chứng trên JDK. File nguồn trong ví dụ dùng văn bản ASCII để output ổn định cả trên terminal không hỗ trợ Unicode; bài học không hàm ý Java serialization chỉ xử lý ASCII.

## 6. Định dạng Object Serialization Stream Protocol

### 6.1. Stream header: `AC ED 00 05`

Luồng được tạo bởi `ObjectOutputStream` thường có cấu trúc cấp cao:

```text
+------------------------------+
| STREAM_MAGIC                 | 2 byte: AC ED
+------------------------------+
| STREAM_VERSION               | 2 byte: 00 05
+------------------------------+
| Contents                     |
| - object/string/array tokens |
| - class descriptors          |
| - primitive / block data     |
| - reference handles          |
| - optional custom data       |
+------------------------------+
```

`ObjectInputStream` dùng header này để nhận diện định dạng. Nếu bốn byte đầu không đúng, constructor có thể ném `StreamCorruptedException`, ví dụ khi bạn vô tình dùng nó đọc file text hay JSON.

**Đừng nhầm:** “version 5” trong stream header là **phiên bản protocol stream**, không phải Java 5, không phải JDK 17/21 và không phải `serialVersionUID` của class. Một stream vẫn có version 5 dù ứng dụng biên dịch bằng JDK hiện đại.

### 6.2. Type tags chính

Các token nằm trong `ObjectStreamConstants`; khi đọc hex, bạn có thể nhận ra:

| Token | Giá trị hex | Ý nghĩa khái quát |
|---|---|---|
| `TC_NULL` | `70` | Tham chiếu null |
| `TC_REFERENCE` | `71` | Tham chiếu tới object/descriptor đã có handle |
| `TC_CLASSDESC` | `72` | Class descriptor mới |
| `TC_OBJECT` | `73` | Instance object mới |
| `TC_STRING` | `74` | String mới |
| `TC_ARRAY` | `75` | Array mới |
| `TC_CLASS` | `76` | Class object |
| `TC_BLOCKDATA` | `77` | Khối dữ liệu primitive/optional ngắn |
| `TC_ENDBLOCKDATA` | `78` | Kết thúc một phần optional block data |
| `TC_RESET` | `79` | Reset context/handles |
| `TC_BLOCKDATALONG` | `7A` | Khối data có độ dài lớn |
| `TC_EXCEPTION` | `7B` | Thông tin exception trong protocol |
| `TC_LONGSTRING` | `7C` | String dài |
| `TC_PROXYCLASSDESC` | `7D` | Descriptor cho proxy class |
| `TC_ENUM` | `7E` | Enum constant |

Chúng là **marker trong một protocol có cấu trúc**, không phải “mỗi byte bằng `73` ở file đều là object mới”. Byte giá trị `73` có thể xuất hiện **bên trong một payload hoặc tên class**; parser phải đọc theo grammar, length và ngữ cảnh thay vì tìm byte tùy tiện.

### 6.3. Class descriptor chứa gì?

Khi lần đầu gặp một serializable class trong stream, một descriptor có thể chứa:

- Tên binary của class.
- `serialVersionUID`.
- Cờ biểu diễn cách serialization của class.
- Danh sách các field được lưu với tên và kiểu.
- Mô tả superclass và những phần dữ liệu tùy biến nếu có.

Ví dụ, với `Message` ở trên, descriptor sẽ mô tả các field được lưu như `sequence` và `text`; field `secret` bị loại vì `transient`. Đây chỉ là mô tả logic, **không** phải mã byte hex đầy đủ cho tất cả JDK/đường dẫn class. Byte stream thực tế còn có tên nested class, handle, string descriptor, annotation và những phần protocol khác.

**Không ghi raw byte stream bằng tay** trừ khi bạn đang xây parser/encoder đúng Java Object Serialization Specification. `writeObject`/`readObject` được thiết kế để xử lý descriptor, đồ thị tham chiếu, block data và class matching thay bạn.

### 6.4. Object handles giữ reference identity

Java serialization không đơn giản là “dump mọi field của mỗi lần gọi”. Nó duy trì một bảng handle để:

- Không lặp lại toàn bộ object đã được ghi khi gặp **cùng reference** lần sau.
- Bảo toàn quan hệ các field cùng trỏ một object sau khi đọc.
- Tái tạo các đồ thị có **chu trình** (cyclic reference) mà không đệ quy vô hạn.
- Có thể chia sẻ instance sau deserialize trong phạm vi stream context tương ứng.

Trong protocol, handle được cấp từ giá trị base `0x7E0000` theo spec và token `TC_REFERENCE` trỏ về handle đã cấp. Không nên dùng con số handle như ID nghiệp vụ ổn định; nó là dữ liệu nội bộ của một stream.

**Ví dụ đồ thị có chu trình**:

```java
final class Node implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    Node next;
}

Node a = new Node();
Node b = new Node();
a.next = b;
b.next = a;

out.writeObject(a);
```

Đây là snippet trong method có `ObjectOutputStream out`; hai Node được ghi với cơ chế handle cho tham chiếu quay về. Đọc lại một `Node restored = (Node) in.readObject()` từ stream tương ứng sẽ giữ quan hệ `restored.next.next == restored`, nếu classes tương thích và không có custom replacement phá cấu trúc.

### 6.5. `writeObject` ghi lặp cùng reference không đồng nghĩa snapshot mới

```java
Entry entry = new Entry();
entry.number = 1;
out.writeObject(entry);

entry.number = 2;
out.writeObject(entry); // Thường chỉ viết reference cho instance đã thấy.
```

Đây là snippet với `Entry` là class Serializable ở trên, `out` đang mở. Lần đọc thứ hai **không bắt buộc** đem lại field `number=2`; với đối tượng đã ghi vào handle table, stream dùng handle cho cùng object đã serialize lần đầu. Dùng `out.reset()` giữa hai lần ghi nếu bạn thật sự cần ghi lại trạng thái object hiện tại, và hiểu rằng reset ảnh hưởng toàn bộ bảng handle của stream.

## 7. `reset()`, `writeUnshared()` và `readUnshared()`

### 7.1. Khi nào dùng `ObjectOutputStream.reset()`?

`reset()` yêu cầu ObjectOutputStream loại bỏ các handles đã lưu; những object ghi tiếp sẽ có context tham chiếu mới. Nó cũng đặt một marker `TC_RESET` trong stream để ObjectInputStream đồng bộ hóa.

```java
out.writeObject(state);
out.reset();
out.writeObject(state);
```

Đây là mã minh họa khi `out` mở và `state` Serializable. Có hai ý nghĩa:

1. Có thể gửi **snapshot mới** của cùng instance khi dữ liệu đã thay đổi giữa hai lần ghi.
2. Giảm số object references tích lũy trong bảng handles của một stream chạy lâu, với cái giá phải ghi lại descriptors/objects sau reset.

Không gọi `reset()` từ bên trong quá trình `writeObject` đang serialize một object graph; API không cho phép reset lúc đang ở giữa việc ghi graph và có thể ném `IOException`. Thực hiện tại **ranh giới giữa các lần ghi hoàn chỉnh**.

### 7.2. `writeUnshared` và `readUnshared` có phải deep copy?

Không. `writeUnshared(obj)` có semantics đặc biệt về cách **base-level object** được xem là unshared; `readUnshared()` yêu cầu đầu vào đáp ứng các điều kiện không chia sẻ tương ứng. Nhưng chúng **không bảo đảm deep copy toàn bộ object graph**, vì các object được tham chiếu trong những field con vẫn có thể được chia sẻ theo protocol.

Do đó, nếu mục tiêu là tạo **bản sao độc lập hoàn toàn**, Java serialization không phải câu trả lời ngầm định. Cần một quy trình copy với ownership và policy rõ ràng. Không dùng `writeUnshared` để giả định đã loại hết alias reference ở các object con.

## 8. Những field nào được tuần tự hóa?

Với Serializable mặc định, **các instance field không static và không transient** là các trường được cơ chế serialization xử lý, trong giới hạn class hierarchy và cách khai báo.

| Loại field | Có trong default serialized state? | Ví dụ |
|---|---|---|
| `private int count` | Có | Primitive giá trị 7 |
| `private String name` | Có | Reference tới String Serializable |
| `private Address address` | Có nếu đồ thị object tương ứng serializable | Một object graph khác |
| `private transient String password` | Không | Sau deserialize thường null |
| `private static int total` | Không | Field thuộc class/JVM, không thuộc instance |
| `private static final long serialVersionUID` | Không phải field instance; dùng trong descriptor | Kiểm tra tương thích |
| Field tham chiếu object không Serializable | Ghi có thể thất bại nếu không được loại/đổi | `NotSerializableException` |

### 8.1. `transient` không phải cơ chế mã hóa hoặc xóa bảo mật

Một field đánh dấu `transient` không được lưu **theo cơ chế mặc định**. Nó không tự động:

- Xóa giá trị đang tồn tại trong RAM.
- Ngăn custom `writeObject` chủ động ghi giá trị ấy bằng lệnh khác.
- Ngăn thông tin nhạy cảm bị lộ qua các field khác trong graph.
- Mã hóa dữ liệu đã được ghi.

Ví dụ:

```java
class Account implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    String owner;
    transient String sessionToken;
}
```

Nếu caller đặt `sessionToken="abc"` rồi serialize bằng default mechanism, giá trị đó không có trong serialized fields của Account. Nhưng object nào khác chứa cùng token trong field **không transient** vẫn có thể khiến token xuất hiện trong stream. Không xem Java serialization là kho bí mật hay một định dạng an toàn cho credentials.

### 8.2. Phân biệt constructor thường và quá trình deserialize

Với một serializable class bình thường, quá trình deserialize **không gọi constructor của chính serializable class** theo cách khởi tạo `new` thông thường. Field được khôi phục bằng cơ chế serialization; constructor không tham số của **superclass đầu tiên không Serializable** được gọi theo quy tắc tương ứng.

Điều này có hệ quả:

- Những invariant chỉ được thiết lập trong constructor của serializable class có thể không được chạy khi khôi phục.
- Các field transient sẽ có giá trị mặc định (trừ khi code khôi phục chúng chủ động), không tự được gán giá trị bởi constructor class đó.
- Không thể dựa vào constructor để bảo vệ mọi đối tượng tạo ra bởi deserialize.
- Với dữ liệu quan trọng, cần validation và kiểm soát nguồn; hook `readObject` có thể tự kiểm tra invariant nhưng cũng là lý do deserialization dữ liệu không tin cậy nguy hiểm.

## 9. `serialVersionUID` và tương thích giữa các phiên bản class

### 9.1. Vì sao Java kiểm tra serialVersionUID?

Khi serialize, thông tin class trong stream gắn với `serialVersionUID`. Khi deserialize, Java kiểm tra class tương ứng trong JVM có phiên bản được cho phép để khôi phục stream này không.

```java
public final class UserProfile implements java.io.Serializable {
    private static final long serialVersionUID = 3L;
    private String displayName;
}
```

Đây là class biên dịch độc lập `UserProfile.java`. Nếu class triển khai Serializable **không khai báo** `serialVersionUID`, Java tính giá trị mặc định dựa trên chi tiết class theo specification. Giá trị mặc định có thể thay đổi khi bạn chỉnh định nghĩa class; do đó nên khai báo tường minh để quản lý chính sách tương thích thay vì phụ thuộc vào tính toán ngầm.

Nếu stream và class local có serialVersionUID không khớp, một lỗi thường gặp là `InvalidClassException`:

```text
java.io.InvalidClassException: ... local class incompatible:
stream classdesc serialVersionUID = ..., local class serialVersionUID = ...
```

Đây là ví dụ dạng thông báo lỗi **minh họa**, không phải stdout cố định. Nó xuất hiện khi class metadata trong stream không tương thích theo kiểm tra phiên bản.

### 9.2. Cùng serialVersionUID chưa đủ để bảo đảm mọi thay đổi class tương thích

Giữ nguyên `serialVersionUID` có thể hỗ trợ **một số thay đổi** về field. Nhưng không có nghĩa bạn có thể:

- Đổi kiểu một field tùy ý.
- Chuyển class từ Serializable sang Externalizable mà không đổi contract.
- Thay đổi hierarchy hoặc cấu trúc class theo cách không tương thích.
- Xóa/đổi tên class mà không có class tương ứng lúc đọc.
- Tự do đổi `writeObject`/`readObject` custom format mà không versioning.

Các quy tắc thay đổi class được định nghĩa trong *Java Object Serialization Specification – Versioning of Serializable Objects*. Khi cần lưu tệp nhiều năm hoặc chuyển qua các bản phát hành, hãy version dữ liệu ở tầng ứng dụng, kiểm tra compatibility thực tế bằng test các file mẫu của phiên bản cũ và cân nhắc dùng định dạng dữ liệu rõ schema.

### 9.3. Tự kiểm tra serialVersionUID bằng ObjectStreamClass

```java
long uid = java.io.ObjectStreamClass
        .lookup(UserProfile.class)
        .getSerialVersionUID();
System.out.println(uid);
```

Đây là snippet khi class `UserProfile` phía trên có sẵn. Giá trị in là `3`, không phụ thuộc một thể hiện UserProfile cụ thể. `ObjectStreamClass.lookup` dành cho serializable class; đừng giả định mọi loại class đều có descriptor hữu dụng.

## 10. Tùy biến dữ liệu bằng private `writeObject` và `readObject` trong class

Đừng nhầm:

- `out.writeObject(obj)`: phương thức **public** thuộc `ObjectOutputStream`, do caller gọi để serialize object.
- `private void writeObject(ObjectOutputStream out)`: **hook đặc biệt trong chính class Serializable**, được serialization runtime nhận diện theo chữ ký.
- `private void readObject(ObjectInputStream in)`: hook khôi phục tùy biến trong chính class Serializable.

Nếu class cần thêm dữ liệu có cấu trúc hoặc kiểm tra invariant khi khôi phục, có thể khai báo:

```java
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public final class PositiveCount implements Serializable {
    private static final long serialVersionUID = 1L;

    private int count;

    public PositiveCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("negative count");
        }
        this.count = count;
    }

    private void writeObject(ObjectOutputStream out)
            throws IOException {
        out.defaultWriteObject();
    }

    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        if (count < 0) {
            throw new InvalidObjectException("negative count");
        }
    }
}
```

Đây là class **biên dịch được** (`PositiveCount.java`) nhưng không có `main`/stdout. `defaultWriteObject()` ghi các field được chọn theo cơ chế mặc định của class; `defaultReadObject()` khôi phục chúng. Sau đó `readObject` kiểm tra `count >= 0` vì constructor của serializable class không bảo đảm được gọi khi deserialize.

### 10.1. Tùy biến stream format phải có kỷ luật

Nếu hook `writeObject` ghi thêm:

```java
out.defaultWriteObject();
out.writeInt(42);
```

thì hook `readObject` tương ứng phải tiêu thụ theo đúng trình tự:

```java
in.defaultReadObject();
int extra = in.readInt();
```

Hai đoạn trên là snippets trong hooks tương ứng, không phải chương trình độc lập. Với custom serialization, Java có cơ chế optional data/block data trong protocol. Nếu muốn tương thích giữa các phiên bản, cần versioning rõ, không tự tiện đổi thứ tự hay số field tùy biến mà không có đọc tương thích.

Các hooks này không cho phép bỏ qua kiểm tra kiểu, security filter hoặc contract của ObjectInputStream. Ngoài `writeObject`/`readObject`, `writeReplace`/`readResolve` có thể thay thế object khi serialize/deserialize; đó là công cụ nâng cao và cũng tăng nguy cơ khi đọc dữ liệu không tin cậy.

### 10.2. `Externalizable` không phải cùng đường đi mặc định

`Externalizable` mở rộng `Serializable` và yêu cầu class định nghĩa `writeExternal(ObjectOutput)` / `readExternal(ObjectInput)`. Với Externalizable, class chủ động chịu trách nhiệm ghi/đọc phần nội dung object, có các yêu cầu constructor riêng (bao gồm public no-argument constructor) khi khôi phục.

Đừng coi `Externalizable` chỉ là “Serializable có thêm một method”. Nó thay đổi trách nhiệm định dạng, phiên bản và tính hợp lệ dữ liệu, nên chỉ dùng khi cần kiểm soát cấu trúc chi tiết và có test tương thích.

## 11. Stream chứa primitive block data và không phải JSON

Trong file hex, bạn có thể thấy header, token và các khối dữ liệu đan xen. Ví dụ:

```java
ByteArrayOutputStream buffer = new ByteArrayOutputStream();

try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
    out.writeInt(42);
    out.writeObject("hello");
}
```

Đây là đoạn minh họa trong method xử lý `IOException`, có import tương ứng. Trong stream, `writeInt(42)` sẽ được trình bày qua cơ chế **block data** của ObjectOutputStream, rồi tiếp theo mới đến serialized String. Bạn **không** thể đọc 4 byte đầu file và giả định đó là số `42`, vì bốn byte đầu là header `AC ED 00 05`.

### 11.1. Định dạng stream có tính cấu trúc, không phải bản dump bộ nhớ JVM

Serialization ghi **logic object graph** theo specification chứ không copy raw memory address/địa chỉ con trỏ từ JVM.

- Địa chỉ bộ nhớ của object trong process không được giữ nguyên khi đọc từ stream.
- References giữa các object được mô tả bằng handles tương ứng.
- Dữ liệu phụ thuộc serializable class schema và các hook custom, không phải layout memory cố định của HotSpot.
- Một process/JVM khác có thể deserialize khi có classes tương thích, nhưng điều đó **không** giúp đối tượng trở nên an toàn nếu bytes đến từ nguồn không tin cậy.
- Không thể dùng stream này làm chuẩn trao đổi độc lập ngôn ngữ theo cách JSON/Protocol Buffers được thiết kế.

### 11.2. Không phải tất cả field values được lặp trong mỗi lần writeObject

Trong một stream đang giữ handle table, Java có thể ghi `TC_REFERENCE` thay vì serialize lại object. Vì thế kích thước bytes sinh ra sau từng `writeObject` phụ thuộc lịch sử stream, object graph, cách gọi `reset`, và các hook, không chỉ phụ thuộc `toString()` hoặc số field hiện tại.

Nếu muốn đo kích thước serialized bytes:

```java
ByteArrayOutputStream sink = new ByteArrayOutputStream();
try (ObjectOutputStream out = new ObjectOutputStream(sink)) {
    out.writeObject(obj);
}
int byteCount = sink.toByteArray().length;
```

Đây là snippet với `obj` serializable và đã import các lớp tương ứng. Kết quả `byteCount` là số byte **được ghi cho object và overhead của stream**, bao gồm header/descriptors; không phải kích thước bộ nhớ heap của object.

## 12. Lỗi và ngoại lệ thường gặp

| Ngoại lệ | Khi nào gặp | Hướng xử lý |
|---|---|---|
| `NotSerializableException` | Trong graph có object thuộc class không serializable | Chỉ serialize DTO được thiết kế cho mục đích này, dùng `transient` khi phù hợp |
| `InvalidClassException` | Class stream/local không tương thích, thường UID khác hoặc vi phạm serialization constraints | Quản lý `serialVersionUID` và test schema/version |
| `StreamCorruptedException` | Header/control bytes không tuân theo protocol, hoặc stream bị hỏng | Kiểm tra nguồn/định dạng, tránh trộn nhiều header |
| `ClassNotFoundException` | JVM không tìm được class cần dựng lại | Đảm bảo classpath/module, tránh storage phụ thuộc class không ổn định |
| `EOFException` | Không còn đủ bytes để đọc tiếp theo yêu cầu | Quản lý framing/count, tránh dựa vào EOF làm mọi logic cấu trúc |
| `OptionalDataException` | Đọc object tại vị trí dữ liệu primitive hoặc tình huống optional data đặc biệt | Đọc cùng thứ tự và format bên ghi |
| `InvalidObjectException` | Hook validation chủ động báo object state không hợp lệ | Kiểm tra schema và dữ liệu, không bỏ qua lỗi |
| `IOException` | File/socket/stream có sự cố I/O | Đóng stream, báo lỗi; không coi bytes đã ghi/đọc là hoàn chỉnh |
| `ClassCastException` | Ép object đọc được sang class mong đợi nhưng sai type | Kiểm tra instance/type, filter, schema |

**Không cố tiếp tục đọc một ObjectInputStream đã gặp lỗi serialization nghiêm trọng và giả định offset vẫn hợp lệ.** Java API có thể để stream ở trạng thái không xác định. Khi phát hiện dữ liệu hỏng hoặc không đáng tin, thường nên đóng stream, báo lỗi và thực hiện recovery cấp ứng dụng thay vì cố gắng tìm token tiếp theo trong mảng bytes.

### 12.1. Ví dụ `NotSerializableException`

```java
final class NotSerializableHelper {
    int value = 10;
}
final class Container implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    NotSerializableHelper helper = new NotSerializableHelper();
}
```

Đây là hai class minh họa. `out.writeObject(new Container())` sẽ thất bại vì `helper` trỏ tới class không triển khai Serializable/Externalizable. Việc đổi field sang `transient` chỉ phù hợp nếu trạng thái đó không cần trong dữ liệu khôi phục; không được “sửa” bằng cách bỏ dữ liệu quan trọng mà không thiết kế cơ chế rebuild.

### 12.2. Không dùng `toString()` để đối chiếu bytes hay identity

Khi đọc một object, `System.out.println(obj)` có thể in chuỗi dễ hiểu nếu class override `toString()`, hoặc giá trị identity hash string nếu không. Nó không chứng minh object graph được khôi phục chính xác. Để kiểm thử, so sánh field cụ thể, reference identity khi cần, invariant và behavior ứng dụng; dùng `==` trong test reference sharing như ví dụ `ObjectStreamDemo`.

## 13. Rủi ro bảo mật của `readObject`

Java Object Deserialization được tài liệu JDK cảnh báo là **nguy hiểm nếu dữ liệu đến từ nguồn không tin cậy**. Khi đọc, runtime có thể nạp class, khởi tạo object theo cơ chế đặc thù và kích hoạt hook/deserialization logic trong những class có mặt trên classpath. Một chuỗi byte do đối phương điều khiển có thể cố tạo object graph rất sâu, array rất lớn hoặc kích hoạt chuỗi class/hook nguy hiểm.

**Nguyên tắc ưu tiên:**

1. Không nhận Java serialized object stream trực tiếp từ Internet/người dùng không đáng tin khi có thể dùng JSON/CBOR/Protocol Buffers có schema/validation rõ.
2. Nếu buộc dùng, **giới hạn class được phép** và các tài nguyên liên quan bằng `ObjectInputFilter`, filter factory/hệ thống theo chính sách deploy, và kiểm soát classpath.
3. Áp giới hạn kích thước byte toàn bộ trước khi đọc nếu giao thức cho phép; dùng timeouts/limits ở tầng transport.
4. Sau deserialize vẫn xác minh object type, cấu trúc, trường bắt buộc và bất biến nghiệp vụ.
5. Dùng cập nhật JDK/thư viện và tránh đưa các lớp không cần thiết vào classpath của tiến trình giải tuần tự hóa.
6. Không tin tưởng `instanceof` **sau khi** đọc như một cơ chế duy nhất, bởi các bước tạo/khôi phục đối tượng đã diễn ra trong `readObject()`.

### 13.1. `ObjectInputFilter` và các giới hạn

Từ các phiên bản Java hiện đại, `ObjectInputStream` có thể dùng filter kiểm tra class, array length, graph depth, số references và lượng byte tiêu thụ. Ví dụ policy mang tính **minh họa**:

```java
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
        "maxdepth=12;maxrefs=128;maxarray=1024;maxbytes=65536;"
      + "com.example.dto.*;!*");

try (ObjectInputStream in = new ObjectInputStream(source)) {
    in.setObjectInputFilter(filter); // Phải trước readObject().
    Object value = in.readObject();
    // Tiếp tục validate type/schema/invariant.
}
```

Đây là **đoạn minh họa**, không phải chương trình độc lập: cần import `ObjectInputFilter`, `ObjectInputStream`; `source` là InputStream; caller xử lý `IOException`/`ClassNotFoundException`. Tên `com.example.dto.*` phải được thay bằng những class thực tế được phép theo kiến trúc của ứng dụng; các loại container, arrays, hoặc class phụ thuộc hợp lệ cũng cần kiểm tra chính sách tương ứng. `!*` cuối là chặn class chưa được allowlist. Một số giá trị string/primitive có ngoại lệ trong cơ chế gọi filter, nên filter **không phải validator nội dung**.

`setObjectInputFilter` được gọi **một lần** trước khi đọc object; nó không phải cấu hình được thay đi thay lại tùy item sau khi stream đã bắt đầu đọc. Còn có policy toàn JVM thông qua filter factory/JVM properties; cách kết hợp được quy định bởi JDK và yêu cầu cấu hình thực tế.

**Filter giảm rủi ro nhưng không bảo đảm stream “đã an toàn”.** Hãy coi việc giải tuần tự hóa từ nguồn không tin cậy là một boundary bảo mật, áp dụng nhiều lớp kiểm soát và ưu tiên format được thiết kế cho trao đổi dữ liệu.

### 13.2. Không nên log/deserialise bytes chưa kiểm tra chỉ để xem chúng là gì

Một antipattern:

```java
Object object = new ObjectInputStream(untrustedInput).readObject();
// Sau đó mới kiểm tra object thuộc class nào.
```

Đây là code minh họa **không an toàn**, không nên dùng với dữ liệu không đáng tin. Rủi ro nằm **trong quá trình deserialize**, trước khi code kiểm tra class của object trả về. Dùng schema-driven parsing và giới hạn bytes/class graph trước khi tiếp nhận nội dung.

## 14. Lưu object vào file và độ bền dữ liệu

### 14.1. `flush()` không đồng nghĩa `fsync`

`out.flush()` đẩy dữ liệu đang buffer xuống OutputStream kế tiếp; `out.close()` đóng stream và xử lý flush theo contract. Cả hai **không tự tạo cam kết** rằng dữ liệu đã được ghi bền qua sự cố mất điện trên mọi filesystem.

```java
try (FileOutputStream file = new FileOutputStream("state.bin");
     ObjectOutputStream out = new ObjectOutputStream(file)) {

    out.writeObject(state);
    out.flush();
    file.getFD().sync(); // Yêu cầu sync file descriptor theo contract.
}
```

Đây là đoạn minh họa trong method có `state` Serializable, import `FileOutputStream`/`ObjectOutputStream`. `FileDescriptor.sync()` là thao tác yêu cầu đồng bộ nội dung file qua descriptor; nó có chi phí và ràng buộc phụ thuộc thiết bị/provider. Nếu ứng dụng cần cập nhật file trạng thái nguyên tử, nên ghi vào file tạm rồi công bố bằng rename với những yêu cầu độ bền filesystem phù hợp — **không phải vì có `writeObject` nên tự động đạt durability**.

### 14.2. Đọc lại file lưu dài hạn không phải lúc nào cũng thành công

Một `ObjectOutputStream` có thể tạo bytes đúng ở phiên bản ứng dụng hiện tại, nhưng sau vài năm:

- Package/class đã đổi tên.
- `serialVersionUID` khác.
- Một số fields/classes của object graph đã biến mất.
- Bộ lọc deserialization policy mới từ chối class cũ.
- Class mới có invariant khác và không còn chấp nhận data cũ.

Vì vậy Java serialization thường phù hợp khi **cả bên ghi và bên đọc cùng nằm trong hệ sinh thái Java được quản lý chặt** và phiên bản tương thích được kiểm thử; không phải lựa chọn mặc định để lưu dữ liệu nghiệp vụ cần schema ổn định xuyên năm/thế hệ triển khai.

## 15. Thứ tự đọc/ghi và protocol framing

Giả sử bên ghi:

```java
out.writeObject(user);
out.writeObject(order);
out.writeInt(2026);
```

Bên đọc đúng:

```java
User user = (User) in.readObject();
Order order = (Order) in.readObject();
int year = in.readInt();
```

Hai snippets giả định các class `User`/`Order` serializable, output/input stream cùng format, và có xử lý checked exceptions. `ObjectInputStream` không tự biết “message number 1 là User, message number 2 là Order” ngoài dữ liệu class descriptor của chính stream. Caller phải thống nhất **sequence/protocol**.

Một thiết kế ổn định nên xác định:

- Phiên bản giao thức ứng dụng ở tầng ngoài, tách với `STREAM_VERSION` của Java.
- Kiểu record được phép và phạm vi của từng message.
- Số lượng message, điều kiện kết thúc và các giới hạn byte.
- Cách xử lý disconnect/stream truncated.
- Cách chuyển đổi phiên bản class và chính sách allowlist.
- Khi nào được `reset()` để tránh reference alias qua những message logic.
- Điều kiện tin cậy của nguồn bytes và đối tượng được tái dựng.

### 15.1. Không nối stream headers tùy tiện

Nếu một process mở file với chế độ append rồi khởi tạo **ObjectOutputStream mới** và ghi object:

```java
new ObjectOutputStream(new FileOutputStream("data.bin", true))
        .writeObject(obj);
```

Đây là snippet minh họa **lỗi thiết kế**, còn không đóng stream nên cũng rò rỉ tài nguyên. Constructor tạo một **header mới** giữa file; khi đọc bằng một `ObjectInputStream` duy nhất và gọi `readObject` liên tiếp, header đó có thể bị xem là thông tin protocol không hợp lệ.

Giải pháp: giữ **một ObjectOutputStream sống trong phạm vi ghi tương ứng**, hoặc thiết kế framing rõ cho nhiều serialized streams độc lập và đọc từng frame bằng ObjectInputStream riêng. Một số kỹ thuật subclass để suppress header khi append rất dễ sai với handles/protocol, cần chứng minh correctness; không nên sao chép mẹo bỏ header vào production mà thiếu kiểm thử.

## 16. Phân biệt với định dạng lưu trữ khác

| Mục tiêu | Java Object Serialization | Lựa chọn đáng cân nhắc |
|---|---|---|
| Truyền object graph Java giữa các thành phần tin cậy | Có thể phù hợp khi version/class được kiểm soát | Giao thức binary có schema nếu muốn ổn định hơn |
| JSON API giữa nhiều ngôn ngữ | Không phù hợp: định dạng nhị phân Java-specific | JSON + validation |
| Lưu config người dùng có thể sửa tay | Khó đọc/sửa | Properties, JSON, YAML/TOML với parser an toàn |
| Dữ liệu nhị phân có cấu trúc schema lâu dài | Phụ thuộc class và UID | Protocol Buffers, Avro, CBOR/schema |
| Lưu một mảng byte/ảnh/file thô | Không cần overhead object graph | `Files.readAllBytes`/`Files.write` hoặc streams/channels |
| Lưu secret/credential | Không có encryption mặc định | Kho bí mật/keystore/encryption phù hợp |
| Nhận bytes chưa tin cậy | Rủi ro lớn khi `readObject` | Định dạng dữ liệu có parser/allowlist/schema |

Nếu cần Java serialization, dùng nó do **yêu cầu tương thích/lifecycle rõ ràng**, không chỉ vì hai dòng code tiện hơn.

## 17. Những lỗi thường gặp

1. **Quên implements `Serializable`** ở class hoặc object con được tham chiếu. Có thể ném `NotSerializableException`.
2. **Cho rằng mọi field đều được ghi.** `static` và `transient` không nằm trong trạng thái mặc định.
3. **Nghĩ `transient` là mã hóa.** Nó không lưu theo cơ chế mặc định nhưng không tự bảo vệ các field/alias khác.
4. **Nhầm `writeObject` của stream với hook private của class.** Chúng khác vai trò và chữ ký.
5. **Đổi class rồi không kiểm tra serialVersionUID.** `InvalidClassException` có thể xảy ra.
6. **Giữ UID bằng nhau rồi giả định mọi schema đều tương thích.** Một số thay đổi field/hierarchy/custom data vẫn phá format.
7. **Đọc primitive bằng `readObject`.** Phải dùng `readInt`/`readUTF`... theo thứ tự bên ghi.
8. **Đọc số object khác với số đã ghi.** Có thể gặp `EOFException` hoặc lỗi protocol.
9. **Nhầm header `AC ED 00 05` với dữ liệu object.** Đó là metadata bắt đầu stream, không phải field của object.
10. **Nghĩ lần thứ hai `writeObject(sameInstance)` gửi trạng thái mới.** Handle table có thể ghi reference tới lần đầu.
11. **Thay đổi object giữa hai lần write nhưng không `reset()` khi cần snapshot mới.** Phía đọc vẫn tham chiếu state cũ.
12. **Nghĩ `writeUnshared` deep-copy toàn bộ object graph.** Nó không bảo đảm alias-free nested references.
13. **Cố parse hex stream bằng cách tìm byte `TC_OBJECT` tùy tiện.** Token cần được hiểu theo grammar và length.
14. **Tạo ObjectOutputStream mới mỗi lần append cùng file.** Header lặp giữa stream phá việc đọc liên tục.
15. **Hai đầu socket cùng tạo ObjectInputStream trước khi có output header.** Có thể deadlock do constructor đợi bytes header.
16. **Đóng ObjectOutputStream rồi tiếp tục dùng OutputStream bên dưới.** Close truyền xuống stream bên dưới.
17. **Chỉ `flush()` rồi khẳng định file durable qua mất điện.** Flush Java khác filesystem persistence.
18. **Dùng `readObject` cho JSON/text/ZIP.** Không cùng định dạng; có thể lỗi header.
19. **Dựa vào constructor của Serializable class để validate mọi object deserialize.** Constructor của chính class có thể không được gọi.
20. **Chỉ kiểm tra `instanceof` sau `readObject` và coi dữ liệu bên ngoài an toàn.** Lúc ấy quá trình deserialize đã diễn ra.
21. **Không có ObjectInputFilter/resource limits khi đọc data không tin cậy.** Có nguy cơ tải đồ thị nguy hiểm/quá lớn.
22. **Bắt mọi `IOException` rồi cố đọc tiếp stream hỏng.** Trạng thái object stream có thể không xác định.
23. **Dùng `toString()` làm bằng chứng deep equality.** Cần test fields, references và invariant.
24. **Nghĩ version stream `00 05` là serialVersionUID của class.** Hai khái niệm độc lập.
25. **Cho rằng String luôn được mã hóa như UTF-8 bytes raw trong toàn bộ stream.** Protocol có quy tắc mã hóa và markers riêng; không đồng nhất với `"abc".getBytes(UTF_8)`.
26. **Coi serialization như sao chép raw memory.** Java lưu trạng thái logic theo protocol và class descriptors.
27. **Không xử lý class không còn trên classpath.** `ClassNotFoundException` có thể làm dữ liệu cũ không đọc được.
28. **Tin bản đọc lại luôn có invariant hợp lệ.** Cần validation và quản lý phiên bản class.
29. **Không quản lý số byte/file size tối đa trước khi deserialize.** Filter và giới hạn tài nguyên nên được thiết kế ở nhiều tầng.
30. **Dùng object serialization làm cơ chế lưu dữ liệu dài hạn mà không có test compatibility.** File hôm nay chưa chắc đọc được ở deployment tương lai.

## 18. Tự kiểm tra kiến thức

**Câu 1.** `writeObject` tạo JSON hay một định dạng binary riêng?  
**Trả lời:** Binary Java Object Serialization Stream Protocol, có header/descriptors/tokens/handles.

**Câu 2.** Bốn byte đầu của ObjectOutputStream thông thường là gì?  
**Trả lời:** `AC ED 00 05` (magic và stream version).

**Câu 3.** Class `Serializable` có cần override `writeObject` không?  
**Không.** Serializable là marker interface, cơ chế mặc định có thể tự ghi các field phù hợp.

**Câu 4.** Nếu class Serializable có field non-transient trỏ tới class không Serializable thì sao?  
**Trả lời:** Ghi graph có thể ném `NotSerializableException`.

**Câu 5.** `readObject` trả kiểu tĩnh nào và nên làm gì sau đó?  
**Trả lời:** `Object`; kiểm tra/ép kiểu hợp lệ và xác minh dữ liệu, nhưng không coi kiểm tra sau đọc là bảo vệ đầy đủ trước untrusted input.

**Câu 6.** Vì sao hai lần `writeObject(sameObj)` rồi đọc hai lần có thể tạo cùng reference?  
**Vì** ObjectOutputStream duy trì handle table và có thể ghi `TC_REFERENCE` cho object đã ghi.

**Câu 7.** `out.reset()` giữa hai lần ghi có tác dụng gì?  
**Trả lời:** Xóa handles đã nhớ và ghi reset marker; object ghi sau được serialize lại trong context mới.

**Câu 8.** `transient String token` được khôi phục thành gì với cơ chế mặc định?  
**Trả lời:** Thường là `null` nếu không có custom logic thay đổi nó.

**Câu 9.** `serialVersionUID` khác `STREAM_VERSION` ở đâu?  
**Trả lời:** UID xác định phiên bản tương thích của class; STREAM_VERSION mô tả phiên bản serialization wire protocol.

**Câu 10.** Một ObjectInputStream có thể đọc file UTF-8 thông thường không?  
**Không.** Stream phải đúng Java object serialization protocol; file text không có header/class descriptors thích hợp.

**Câu 11.** Có thể đọc dữ liệu không tin cậy rồi mới kiểm tra type để loại bỏ mọi rủi ro không?  
**Không.** Deserialization đã khởi động trước lúc kiểm tra. Ưu tiên format có schema, filter và giới hạn tài nguyên khi bắt buộc dùng object stream.

**Câu 12.** Nếu class đổi package hoặc biến mất, nhưng byte file vẫn nguyên, deserialize có chắc thành công?  
**Không.** JVM vẫn cần giải quyết tên class và các quy tắc tương thích.

**Câu 13.** `out.flush()` có bảo đảm tệp vẫn nguyên sau mất điện không?  
**Không.** Flush ở tầng Java không đồng nghĩa độ bền của thiết bị/file system.

**Câu 14.** `writeUnshared` có bảo đảm mọi nested object đều được deep clone không?  
**Không.** Nó chỉ thay semantics chia sẻ ở cấp được API định nghĩa; nested references có thể vẫn chia sẻ.

**Câu 15.** Làm sao kiểm tra file stream chứa toàn graph object, chứ không chỉ trùng chuỗi in ra?  
**Trả lời:** Test giá trị fields, reference identity, chu trình, invariants, serialization hooks và khả năng đọc qua phiên bản tương thích.

## 19. Tổng kết và tài liệu chính thức

**Bản chất:** `ObjectOutputStream.writeObject` ghi một **đồ thị đối tượng** vào Java Object Serialization Stream Protocol. `ObjectInputStream.readObject` đọc lại **đối tượng kế tiếp** và khôi phục các tham chiếu. Stream có header `AC ED 00 05`, class descriptors, type tags, handles và block data; nó không phải JSON hay mảng các object độc lập. `Serializable`, `serialVersionUID`, `transient`, `reset` và các hooks custom quyết định cách dữ liệu được lưu và khôi phục.

**Quy tắc an toàn:** chỉ deserialize nguồn tin cậy hoặc dùng chính sách lọc/giới hạn nghiêm ngặt khi buộc phải đọc Java object stream. Đừng xem byte Java serialization là định dạng lưu trữ mặc định cho hệ thống cần khả năng tương thích lâu dài, đa ngôn ngữ hoặc mức phòng vệ mạnh trước dữ liệu do đối phương kiểm soát.

Nguồn Java chính thức:

- `ObjectOutputStream` — `writeObject`, `reset`, `writeUnshared`, stream header: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectOutputStream.html
- `ObjectInputStream` — `readObject`, `readUnshared`, `setObjectInputFilter` và security warnings: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputStream.html
- `Serializable` — marker interface và custom hooks: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Serializable.html
- `ObjectInputFilter` — deserialization filtering: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputFilter.html
- `ObjectStreamConstants` — STREAM_MAGIC/STREAM_VERSION/TC_*: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectStreamConstants.html
- `ObjectStreamClass` — serialVersionUID và class descriptors: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectStreamClass.html
- Java Object Serialization Specification, Stream Protocol: https://docs.oracle.com/en/java/javase/21/docs/specs/serialization/protocol.html
- Java Object Serialization Specification, Versioning: https://docs.oracle.com/en/java/javase/21/docs/specs/serialization/version.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serialization-basics-004">Tham chiếu dùng chung, vòng tham chiếu và object handle</a>

<details>
<summary>Xem chi tiết</summary>

# Java Serialization: tham chiếu dùng chung, vòng tham chiếu và object handle

## 1. Bài toán: làm sao khôi phục đúng **đồ thị đối tượng**?

Khi dùng `ObjectOutputStream` và `ObjectInputStream`, điều quan trọng không chỉ là lưu giá trị của từng field. Một đối tượng Java có thể **trỏ đến cùng một đối tượng từ nhiều field khác nhau**, hoặc các đối tượng có thể **tham chiếu vòng về nhau**.

Ví dụ:

~~~text
               +-------------------+
               |    Person (An)    |
               |  home ----+       |
               |  office --|--+    |
               +-----------|--|----+
                           |  |
                           v  v
                      +----------+
                      | Address  |
                      |  Hà Nội  |
                      +----------+

home và office là **cùng một Address**, không phải hai Address
chỉ tình cờ có nội dung giống nhau.
~~~

Và một vòng tham chiếu:

~~~text
       +--------+   next    +--------+
       | Node A | --------> | Node B |
       +--------+           +--------+
            ^                   |
            +-------------------+
                      next
~~~

Nếu chỉ sao chép nội dung field thành các bản độc lập, sau deserialize:

- Có thể làm mất quan hệ `person.home == person.office`.
- Có thể làm vòng `A -> B -> A` thành một chuỗi sao chép vô hạn hoặc một cấu trúc sai.
- Có thể làm thay đổi ngữ nghĩa chương trình, vì **identity** (đồng nhất tham chiếu) khác với equality (bằng nhau về nội dung).

**Java Object Serialization mặc định giữ cấu trúc chia sẻ và các vòng tham chiếu** bằng cách gán **object handle** cho những đối tượng đã xuất hiện trong *cùng một luồng tuần tự hóa*. Những lần gặp lại thường được ghi thành **back-reference** thay vì serialize toàn bộ đối tượng lần nữa.

## 2. Điều kiện cơ bản để serialize một đồ thị

Một lớp được tuần tự hóa mặc định thường triển khai `java.io.Serializable`:

~~~java
import java.io.Serializable;

final class Node implements Serializable {
    private static final long serialVersionUID = 1L;

    String name;
    Node next;

    Node(String name) {
        this.name = name;
    }
}
~~~

`Serializable` là **marker interface**: không bắt buộc định nghĩa method. `ObjectOutputStream.writeObject(root)` duyệt những object reachable qua các field tham chiếu cần tuần tự hóa:

- Field instance bình thường, **không phải `transient` hoặc `static`**, được tham gia vào cơ chế default serialization (tuân theo các quy tắc của lớp/kiểu).
- Field kiểu primitive được lưu theo giá trị.
- Field tham chiếu tới object khác có thể kéo theo cả **đồ thị các object mà nó trỏ tới**.
- `null` được ghi như giá trị null, không tạo object mới.
- Mọi object cần ghi trong đồ thị phải phù hợp với cơ chế serialization; một field tham chiếu đến object không serializable thường gây `NotSerializableException`, trừ khi được loại bỏ hoặc xử lý tùy biến.

Không nên nhầm:

~~~java
Node first = new Node("X");
Node second = new Node("X");
boolean same = first == second;  // false
~~~

Dù `first.name.equals(second.name)` là true, chúng là **hai instance khác nhau** và theo cơ chế mặc định sẽ được lưu dưới hai handle khác nhau.

Ngược lại:

~~~java
Node shared = new Node("X");
Node first = shared;
Node second = shared;
boolean same = first == second;  // true
~~~

Đây chỉ là **một object** có hai tham chiếu. Serialization giữ quan hệ `==` đó khi khôi phục graph trong luồng, miễn không bị cơ chế thay thế object tùy biến thay đổi.

## 3. Object handle là gì?

`ObjectOutputStream` quản lý trạng thái của luồng, trong đó có bảng ghi nhớ những object đã được serialize. Khi gặp một object lần đầu:

1. Luồng ghi thông tin loại object (gồm class descriptor khi cần).
2. Object nhận một **handle** — mã nhận dạng nội bộ trong **stream**, không phải Java reference, địa chỉ RAM hay `hashCode()`.
3. Luồng ghi các field cần lưu; nếu field tham chiếu đến object khác thì tiếp tục xử lý object đó.
4. Nếu gặp lại object đã được ghi, luồng có thể ghi **reference đến handle** thay vì lặp lại toàn bộ object.

Ở phía đọc, `ObjectInputStream` duy trì bảng handle tương ứng để trả lại các tham chiếu tới **cùng instance đã khôi phục**.

Sơ đồ khái niệm:

~~~text
ObjectOutputStream

Lần 1: writeObject(address)
  -> NEW_OBJECT(Address, handle H1, fields ...)

Lần 2: writeObject(address) với CÙNG instance
  -> REFERENCE(H1)

ObjectInputStream

Lần 1: readObject() -> Address#1
Lần 2: readObject() -> chính Address#1

Do đó: firstRead == secondRead  ==> true
~~~

Đây chỉ là **sơ đồ logic**. Trong stream thực còn header, class descriptor, mã loại phần tử và các handle của class descriptor, string/array và object khác. Không coi `H1` là byte handle cố định hay một mã có thể đem lưu độc lập.

### Handle khác `hashCode()`, `equals()` và object ID nghiệp vụ

| Khái niệm | Dùng để làm gì? | Phạm vi/giới hạn |
|---|---|---|
| `==` trên hai reference | Kiểm tra cùng một instance | Trong một JVM |
| `equals()` | Kiểm tra tính bằng nhau do lớp định nghĩa | Không nhất thiết cùng instance |
| `hashCode()` | Hỗ trợ hashing; không phải ID duy nhất | Có thể trùng, có thể thay đổi theo implementation |
| **Serialization handle** | Nhận dạng các object đã xuất hiện trong stream | Nội bộ mỗi stream, chịu tác động bởi `reset()` |
| ID nghiệp vụ (UUID, database ID) | Nhận dạng thực thể ở mức ứng dụng | Do ứng dụng quy định, không do serialization tự tạo |

**Không dùng một handle được đọc từ stream như primary key database.** Nó chỉ là cơ chế biểu diễn tham chiếu trong định dạng tuần tự hóa.

## 4. Vì sao tham chiếu dùng chung được bảo toàn?

Giả sử:

~~~java
// Đoạn minh họa trong ngữ cảnh có lớp Address và Person Serializable.
Address address = new Address("Hà Nội");
Person person = new Person();
person.home = address;
person.office = address;
out.writeObject(person);
~~~

Khi serializer duyệt field `home`, nó lưu `address` và gán handle. Đến field `office`, nó thấy **cùng object address** nên ghi back-reference. Phía đọc tái lập:

~~~java
Person restored = (Person) in.readObject();
System.out.println(restored.home == restored.office); // true
~~~

Nếu hai field trỏ đến **hai Address khác nhau nhưng có giá trị giống nhau**, mặc định serializer không tự “gộp” chúng lại:

~~~text
Trước ghi:   home -> Address#1("HN")
             work -> Address#2("HN")
Sau đọc:     home -> Address#3("HN")
             work -> Address#4("HN")

home != work, dù thuộc tính có thể giống nhau.
~~~

Đây là lý do phải xác định bài toán có cần bảo toàn **identity** hay chỉ cần bảo toàn **giá trị**.

### Chia sẻ trong nhiều cấp lồng nhau

Không chỉ hai field của cùng object được bảo toàn. Nếu một danh sách chứa `sameNode` hai lần, hoặc nhiều đối tượng khác nhau cùng trỏ đến một đối tượng con, back-reference vẫn có thể được dùng trong một đồ thị được ghi vào cùng stream.

~~~java
Node sameNode = new Node("shared");
List<Node> nodes = new ArrayList<>();
nodes.add(sameNode);
nodes.add(sameNode);
// Sau serialize/deserialize nodes, phần tử index 0 và 1
// vẫn là cùng một Node: nodes.get(0) == nodes.get(1).
~~~

Đoạn này là mã minh họa, cần imports `java.util.*` và lớp Node serializable; không phải một chương trình độc lập.

## 5. Vòng tham chiếu: tại sao serializer không đệ quy vô hạn?

Nếu hai object trỏ về nhau:

~~~java
Node a = new Node("A");
Node b = new Node("B");
a.next = b;
b.next = a;
~~~

Quá trình lưu có thể hình dung:

~~~text
writeObject(a)
  1) gặp A lần đầu -> cấp handle HA
  2) ghi A.next = B
     3) gặp B lần đầu -> cấp handle HB
     4) ghi B.next = A
        5) A đã có HA -> ghi REFERENCE(HA), không đệ quy thêm
  6) quay lại hoàn tất B rồi A
~~~

Điểm then chốt: **handle của object được gán trước khi toàn bộ field tham chiếu của nó được viết xong**. Vì vậy khi đọc đến một liên kết quay về object đang trong quá trình serialize, serializer đã có handle để tham chiếu.

Phía đọc cũng tạo/đăng ký đối tượng trước khi hoàn tất toàn bộ field của nó theo cơ chế mặc định, cho phép khôi phục vòng:

~~~java
Node restored = (Node) in.readObject();
boolean cycle = restored.next.next == restored; // true
~~~

Vòng tham chiếu **không** cần được cắt bằng cách đặt field thành `null`. Tuy nhiên, cơ chế tuần tự hóa có hạn mức tài nguyên (độ sâu, kích thước) và có thể thất bại trên đồ thị quá lớn/phức tạp; không tự suy ra mọi đồ thị vô hạn hoặc dữ liệu hỏng đều đọc được.

### Self-reference

Một object có thể trỏ đến chính nó:

~~~java
Node self = new Node("SELF");
self.next = self;
~~~

Sau serialize/deserialize mặc định:

~~~java
Node restored = (Node) in.readObject();
System.out.println(restored.next == restored); // true
~~~

Đây là một trường hợp vòng độ dài 1. Với nhiều thư viện JSON, xử lý vòng có thể cần chiến lược riêng; **đừng lấy hành vi JSON làm mặc định cho Java Object Serialization**.

## 6. Chương trình Java 17+ hoàn chỉnh

Ví dụ này chứng minh **bốn hiện tượng** bằng cách chạy thực tế:

1. Hai lần `writeObject(root)` liên tiếp trong cùng `ObjectOutputStream` tham chiếu đến **cùng object đã được serialize trước**.
2. Sửa field `name` rồi gọi lại `writeObject(root)` **không tự lưu field mới** nếu object vẫn còn handle cũ trong stream.
3. `ObjectOutputStream.reset()` làm lần ghi tiếp theo trở thành **một lần serialize mới**; phía đọc nhận một graph mới, và nội dung field mới được ghi.
4. Các tham chiếu chia sẻ và vòng `A -> B -> A` vẫn được tái lập **bên trong từng graph**.

Lưu tệp **`ObjectHandleDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class ObjectHandleDemo {

    private static final class Node implements Serializable {
        private static final long serialVersionUID = 1L;

        String name;
        Node next;
        Node also;

        Node(String name) {
            this.name = name;
        }
    }

    public static void main(String[] args) throws Exception {
        Node a = new Node("A");
        Node b = new Node("B");

        a.next = b;
        a.also = b; // Hai field của A trỏ đến cùng B.
        b.next = a; // A -> B -> A tạo vòng.

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out =
                     new ObjectOutputStream(bytes)) {
            out.writeObject(a); // Graph đầu tiên, có handle của A/B.
            out.writeObject(a); // Back-reference tới A cũ.

            a.name = "A-updated";
            out.writeObject(a); // Vẫn back-reference, không ghi name mới.

            out.reset();        // Hủy bảng handle của stream.
            out.writeObject(a); // Ghi graph mới với name đã thay đổi.
        }

        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {

            Node first = (Node) in.readObject();
            Node second = (Node) in.readObject();
            Node third = (Node) in.readObject();
            Node afterReset = (Node) in.readObject();

            System.out.println("same-root="
                    + (first == second && second == third));
            System.out.println("old-name=" + first.name);
            System.out.println("updated-name=" + afterReset.name);
            System.out.println("new-root="
                    + (first != afterReset));

            System.out.println("shared-child="
                    + (first.next == first.also));
            System.out.println("cycle="
                    + (first.next.next == first));
            System.out.println("new-cycle="
                    + (afterReset.next.next == afterReset));

            System.out.println("original-vs-restored="
                    + (a == first));
        }
    }
}
~~~

Biên dịch và chạy trong thư mục có tệp nguồn:

~~~bash
javac ObjectHandleDemo.java
java ObjectHandleDemo
~~~

**Stdout chính xác khi chương trình chạy thành công:**

~~~text
same-root=true
old-name=A
updated-name=A-updated
new-root=true
shared-child=true
cycle=true
new-cycle=true
original-vs-restored=false
~~~

**Giải thích từng dòng:**

- `first == second == third` đều đúng vì ba lần đọc đầu phục hồi **một object handle** từ một object mới rồi hai back-reference.
- `old-name=A` vì giá trị của A đã được ghi khi nó **xuất hiện lần đầu trong stream**; thay đổi A trong bộ nhớ **không tự cập nhật bytes đã serialize**.
- `afterReset.name` là `"A-updated"` vì lần ghi sau `reset()` thực hiện serialize trạng thái hiện tại của A.
- `first != afterReset` vì sau reset, reader tạo một graph mới cho phần dữ liệu tiếp theo.
- `first.next == first.also` cho thấy shared B được giữ.
- `first.next.next == first` và `afterReset.next.next == afterReset` cho thấy cả graph cũ và graph mới có chu trình đúng.
- `a == first` là false vì `a` là object gốc trong bộ nhớ trước khi ghi, còn `first` là một object được khôi phục bởi deserialization trong cùng JVM.

**Lưu ý:** `ByteArrayOutputStream` chỉ giữ bytes trong bộ nhớ, vì vậy demo không phụ thuộc filesystem. Nó kiểm chứng identity và nội dung, **không mô phỏng persistence/durability trên đĩa**.

## 7. Object handle tồn tại trong phạm vi nào?

Bảng handle thuộc **mỗi `ObjectOutputStream`** và được phía `ObjectInputStream` tương ứng theo dõi. Nó **không phải cache toàn cục JVM**.

### Cùng stream, cùng object

~~~java
out.writeObject(shared);
out.writeObject(shared);
~~~

Thao tác thứ hai có thể chỉ ghi back-reference, vì `shared` đã có handle trong luồng đó. Khi đọc hai mục tương ứng, hai biến nhận được tham chiếu cùng object.

### Hai stream độc lập

~~~java
// Minh họa, hai ObjectOutputStream khác nhau:
outA.writeObject(shared);
outB.writeObject(shared);
~~~

Hai stream có trạng thái handle riêng. Đọc các stream độc lập thông thường tạo object mới cho mỗi stream, dù phía ghi truyền vào đúng cùng một object gốc.

### Trong một lần ghi cả graph

~~~java
// Minh họa root chứa cùng subObject qua nhiều field.
root.left = subObject;
root.right = subObject;
out.writeObject(root);
~~~

Field `left/right` cùng trỏ đến subObject trong graph được phục hồi (mặc định). Điều này hoạt động không phụ thuộc vào việc có gọi `writeObject(root)` lần thứ hai hay không.

### Hai object có nội dung bằng nhau

~~~java
out.writeObject(new Node("same"));
out.writeObject(new Node("same"));
~~~

Đây là **hai instance khác nhau**, không phải một shared reference. Kết quả đọc thường tạo hai Node riêng biệt.

**Đừng dựa vào giá trị `equals()` hoặc `System.identityHashCode()` để đoán handle serialization.** Đó là những cơ chế khác.

## 8. Vì sao sửa object rồi `writeObject()` lại có thể nhận trạng thái cũ?

Một lỗi rất phổ biến:

~~~java
// Minh họa: User là lớp Serializable.
User user = new User("first");
out.writeObject(user);

user.name = "second";
out.writeObject(user); // Không tự phát lại toàn bộ trạng thái mới.
~~~

Luồng đã biết handle của `user` nên lần hai có thể là back-reference. Reader trả lại object có dữ liệu được khôi phục từ **lần ghi đầu tiên**.

### Các cách giải quyết tùy mục tiêu

- **Muốn giữ identity và không muốn snapshot mới:** `writeObject` như thường lệ; back-reference là hành vi cần thiết.
- **Muốn ghi trạng thái mới của toàn graph:** gọi `ObjectOutputStream.reset()` tại một ranh giới hợp lệ **trước lần ghi kế tiếp**; stream quên handle cũ, lần sau serialize lại.
- **Muốn chủ động gửi lại trạng thái của cùng object nhưng giữ những đối tượng phụ đã biết:** nghiên cứu `writeUnshared(...)` hoặc `writeObject` trên một bản snapshot riêng, hiểu rõ giới hạn phạm vi chia sẻ.
- **Muốn cập nhật trạng thái lưu bền theo transaction:** object stream không phải database; cần định dạng record/version hoặc cơ chế lưu trữ phù hợp.

Không sửa thủ công bảng handle hay dựa vào việc `flush()` để làm mới object đã được cache trong stream. **`flush()` đẩy bytes đang đệm; không xóa object handles.**

## 9. `ObjectOutputStream.reset()`: xóa trạng thái handle cho các mục ghi tiếp

`reset()` trên `ObjectOutputStream`:

1. Bỏ trạng thái ghi nhớ những object đã xuất hiện trong stream.
2. Ghi dấu reset trong stream để phía `ObjectInputStream` tương ứng **cũng xóa bảng handle tại vị trí đó**.
3. Những object đã ghi trước đó **không bị sửa hoặc mất khỏi bytes hiện có**.
4. Object cùng Java reference nếu được `writeObject` sau reset sẽ được serialize như một lần xuất hiện mới.
5. Có thể làm tăng kích thước stream vì object và class descriptor có thể được ghi lại.

Ví dụ:

~~~java
out.writeObject(root); // Root được gán handle lần đầu.
out.writeObject(root); // Back-reference.
out.reset();            // Quên bảng handle.
out.writeObject(root); // Dữ liệu của root được ghi lại.
~~~

**Không gọi `reset()` trong lúc serializer đang thực hiện ghi một object**, ví dụ gọi tùy tiện bên trong `private writeObject(ObjectOutputStream out)` để ép child mới. Đặc tả không cho phép reset trong quá trình serialize object; có thể ném `IOException`.

Reset là **mốc cho những object về sau**, không phải lệnh xóa bytes trước nó. Đừng nhầm với `ByteArrayOutputStream.reset()`: phương thức của ByteArrayOutputStream xóa **nội dung buffer bytes** trong RAM, còn `ObjectOutputStream.reset()` có nhiệm vụ đặt lại **trạng thái serialization** và ghi marker tương ứng.

### Reset có “deep copy” không?

Nếu sau reset ta serialize lại toàn bộ root graph, phía đọc có graph mới, trong đó các tham chiếu nội bộ vẫn được chia sẻ/có vòng đúng cách. Tuy nhiên:

- Dữ liệu mutable chỉ phản ánh trạng thái tại thời điểm được serialize lại.
- Nếu một field là `transient`/`static` hoặc các custom hooks thay đổi logic, graph kết quả có thể khác.
- Định nghĩa graph mới không tự đảm bảo thread-safety nếu object gốc bị thread khác sửa trong lúc serialize.

## 10. `writeUnshared()`: ghi lại object ở cấp gốc mà không dùng back-reference cũ

Ngoài `writeObject`, Java cung cấp:

~~~java
out.writeUnshared(obj);
~~~

Theo tài liệu `ObjectOutputStream`:

- Đối với **object ở cấp gốc được truyền trực tiếp**, `writeUnshared(obj)` luôn ghi object như một object mới xuất hiện, thay vì viết back-reference đến lần ghi trước.
- Những lần `writeObject(obj)` về sau **không tham chiếu ngược tới lần ghi bằng `writeUnshared`** như thể đó là một lần ghi shared thông thường.
- Quy tắc này **chỉ áp dụng trực tiếp cho root object của lời gọi**. Các object con được tham chiếu transitively vẫn có thể chia sẻ handle với object đã ghi khác.
- Vì vậy **`writeUnshared()` không phải deep copy toàn bộ graph** và không có nghĩa tắt object caching trên mọi cấp.

### Ví dụ trực tiếp với hai lần ghi unshared

Lưu thành **`UnsharedDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class UnsharedDemo {

    private static final class Box implements Serializable {
        private static final long serialVersionUID = 1L;
        int value;

        Box(int value) {
            this.value = value;
        }
    }

    public static void main(String[] args) throws Exception {
        Box box = new Box(7);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream out =
                     new ObjectOutputStream(bytes)) {
            out.writeUnshared(box);
            out.writeUnshared(box);
        }

        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            Box first = (Box) in.readUnshared();
            Box second = (Box) in.readUnshared();

            System.out.println("first=" + first.value);
            System.out.println("second=" + second.value);
            System.out.println("distinct=" + (first != second));
        }
    }
}
~~~

Chạy:

~~~bash
javac UnsharedDemo.java
java UnsharedDemo
~~~

**Stdout:**

~~~text
first=7
second=7
distinct=true
~~~

Trong ví dụ này Box đơn giản, không có `readResolve()` hoặc object con, nên hai lần đọc nhận **hai instance khác nhau**.

### `readUnshared()` khác `readObject()`

`ObjectInputStream.readUnshared()` nhận một object mới **và ngăn những lần đọc back-reference về handle đó**. Nếu `readUnshared()` gặp chính một back-reference đã có trong stream, Java ném một **`ObjectStreamException`** (thường gặp `InvalidObjectException` theo triển khai; không nên gắn logic vào một subtype cụ thể nếu chỉ dựa trên hợp đồng API).

Ví dụ sai:

~~~java
// Ghi:
out.writeObject(box);
out.writeObject(box); // Back-reference tới box.

// Đọc:
Box first = (Box) in.readObject();
Box second = (Box) in.readUnshared(); // Không hợp lệ trên back-reference.
~~~

Đây là **đoạn minh họa ở hai phía ghi/đọc**, không phải chương trình độc lập. Không dùng `readUnshared` tùy tiện thay cho tất cả `readObject`.

**Ngoại lệ tinh tế:** `readResolve()` có thể thay object bằng một singleton hoặc instance đã có ở nơi khác, và enum/Class có ngữ nghĩa đặc biệt. Vì vậy kết hợp `writeUnshared/readUnshared` không phải cam kết tuyệt đối về một instance mới cho *mọi loại object*. Với `readResolve` trả về array, `readUnshared` dùng bản clone nông theo đặc tả. Bản thân child object trong graph cũng có thể vẫn chia sẻ.

## 11. Dòng byte: `TC_OBJECT`, `TC_REFERENCE` và `TC_RESET`

Trong giao thức Java Object Serialization, các phần tử stream được đánh dấu bằng mã loại:

| Token | Ý nghĩa khái quát |
|---|---|
| `TC_OBJECT` | Dữ liệu một ordinary object mới |
| `TC_ARRAY` | Một array mới |
| `TC_STRING` / `TC_LONGSTRING` | Một String mới |
| `TC_CLASSDESC` | Class descriptor tương ứng |
| `TC_REFERENCE` | Tham chiếu đến object/descriptor/string đã có handle |
| `TC_NULL` | Tham chiếu null |
| `TC_RESET` | Đặt lại trạng thái handle của luồng tại điểm này |

Đây là cách **hình dung giao thức**, không phải danh sách mọi token. Một stream thực còn header như magic/version, các descriptor và phần tử khác. Không phải mỗi lần gọi `writeObject` đều sinh `TC_OBJECT`; lần gặp lại có thể chỉ sinh `TC_REFERENCE`.

~~~text
writeObject(root)  -> NEW root (handle Hroot), các child, field ...
writeObject(root)  -> TC_REFERENCE(Hroot)
reset()            -> TC_RESET
writeObject(root)  -> NEW root/children, handle table mới
~~~

Handle không phải memory pointer. Nó dùng để biểu diễn sự nhận dạng object **bên trong định dạng stream**.

Muốn học binary format, đọc Java Object Serialization Stream Protocol thay vì dựa vào hexdump của một ví dụ duy nhất; handle của class descriptors, strings và các thành phần phụ khiến offsets thay đổi theo đồ thị.

## 12. Khi nào quan hệ chia sẻ hoặc vòng có thể bị thay đổi?

Cơ chế mặc định bảo toàn graph, nhưng các cấu hình/hook đặc biệt có thể thay đổi kết quả:

### `transient` và `static`

~~~java
class Data implements Serializable {
    private static final long serialVersionUID = 1L;

    Node child;                // Mặc định được serialize.
    transient Node cache;      // Không lưu bằng default serialization.
    static Node registry;      // Không thuộc trạng thái instance được lưu.
}
~~~

Nếu một vòng chỉ đi qua field `transient`, cạnh tham chiếu đó **không được lưu** theo cơ chế mặc định. Khi deserialize, field transient của object nhận giá trị mặc định trừ khi có logic tùy biến khôi phục.

### `writeReplace()` và `readResolve()`

Một lớp có thể định nghĩa cơ chế **thay thế object** khi ghi/đọc. Khi `readResolve()` trả singleton (ví dụ một instance canonical), kết quả `==` có thể không còn đúng theo cách suy đoán từ object mới thông thường.

Điều này hữu ích với singleton/canonicalization nhưng phải thiết kế và kiểm thử rõ, vì dễ gây nhầm identity, đặc biệt khi kết hợp `readUnshared`. Không tự gắn các hook này vào ví dụ graph mặc định nếu không cần.

### Custom `writeObject/readObject`

Một lớp có thể tùy biến dữ liệu field lưu trong stream. Nếu custom implementation bỏ qua một field quan trọng, tự ghi object con khác thường hoặc khôi phục theo quy tắc riêng, graph kết quả có thể khác.

**Nguyên tắc:** nếu mục tiêu là bảo toàn graph như mặc định, giữ `defaultWriteObject()` / `defaultReadObject()` đúng contract trong các hook, và chỉ bổ sung logic đã kiểm chứng.

### `Externalizable`

`Externalizable` trao quyền lớn hơn cho lớp tự ghi/đọc trạng thái. Không mặc nhiên kỳ vọng mọi custom format phục hồi graph đúng nếu tác giả tự bỏ qua field hoặc không tái lập quan hệ tham chiếu. Hãy phân biệt khả năng handle của stream với dữ liệu mà lớp chọn ghi.

## 13. Lỗi phổ biến: nhầm identity, snapshot và reset

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| Sau đọc, hai field `==` dù tưởng phải là bản sao | Chúng vốn trỏ cùng object; stream bảo toàn chia sẻ | Xác định rõ cần share hay copy, tạo object mới nếu yêu cầu |
| Sau đọc, hai object `equals()` nhưng `!=` | Trước ghi có hai instance khác nhau | Đừng nhầm value equality với identity |
| Gặp vòng tham chiếu mà lo serialize vô hạn | Hiểu nhầm serializer luôn đệ quy không có memoization | Object handles phá vòng với cơ chế mặc định |
| Sửa field rồi `writeObject` lại nhưng reader vẫn thấy giá trị cũ | Stream dùng back-reference đến object đã ghi | Ghi snapshot mới hoặc `out.reset()` ở ranh giới hợp lệ |
| `flush()` xong vẫn thấy giá trị cũ | Flush bytes không xóa bảng handles | Dùng reset/strategy khác, không dựa vào flush |
| `writeUnshared()` rồi tưởng mọi object con đều riêng biệt | Chỉ root object được đảm bảo ghi như mới | Hiểu rõ transitively referenced children vẫn có thể share |
| `readUnshared()` gặp back-reference và lỗi | API cấm đọc back-reference bằng readUnshared | Ghép đúng write/read semantics |
| `reset()` trong custom serialization gây lỗi | Reset không được gọi giữa lúc ghi object | Chỉ reset khi ở ngoài lượt serialize graph |
| Deserialization ném `NotSerializableException` lúc ghi | Một reachable object không serializable | Làm nó serializable, `transient` hoặc dùng custom representation |
| `InvalidClassException` khi đọc | Class metadata/`serialVersionUID` không tương thích | Quản lý version/schema, không chỉ đổi UID tùy tiện |
| Dữ liệu bị sửa đồng thời trong lúc serialize | Object graph không được snapshot nguyên tử | Đồng bộ hoặc tạo immutable snapshot trước khi ghi |
| Stream ngày càng lớn vì reset thường xuyên | Mỗi reset làm object/class descriptors được ghi lại | Cân bằng freshness, identity và kích thước |
| Đọc lại object gốc từ stream rồi so `==` với object nguồn | Deserialization thông thường tạo object mới | So sánh identity trong graph được khôi phục, không qua hai thế giới |

## 14. Liên hệ với `serialVersionUID` và ngoại lệ

Serialization chứa thông tin lớp và trạng thái field. Nếu class được thay đổi không tương thích giữa ghi và đọc, có thể gặp `InvalidClassException`. Nên khai báo `private static final long serialVersionUID` cho lớp serializable cần duy trì định dạng ổn định, nhưng **khai báo UID không đảm bảo mọi thay đổi class đều tương thích**.

Các lỗi cần biết:

- `NotSerializableException`: một object không hỗ trợ serialization trong graph và không được thay thế/loại bỏ hợp lệ.
- `InvalidClassException`: class descriptor/schema không tương thích với phiên bản lớp hiện tại.
- `StreamCorruptedException`: dữ liệu stream không đúng định dạng hoặc đã bị hỏng.
- `EOFException`: đọc quá dữ liệu stream sẵn có.
- `ClassNotFoundException`: không tìm được lớp cần khôi phục.
- `ObjectStreamException` khi `readUnshared` gặp back-reference trái phép.
- `IOException`: lỗi I/O nền hoặc lỗi giao thức.

**Không nên tiếp tục dùng một `ObjectOutputStream` hoặc `ObjectInputStream` với giả định trạng thái vẫn an toàn sau lỗi nghiêm trọng giữa lúc ghi/đọc graph**. Đọc tài liệu về exception protocol của Java serialization và tạo stream mới khi cần phục hồi theo một ranh giới đáng tin cậy.

## 15. Bảo mật: chỉ deserialize dữ liệu đáng tin hoặc có kiểm soát

Java Object Serialization **không phải định dạng giao tiếp an toàn mặc định cho dữ liệu không tin cậy**. `ObjectInputStream.readObject()` có thể tạo một đồ thị lớp phong phú và kích hoạt các cơ chế đặc biệt khi khôi phục; stream bị sửa đổi có thể gây lạm dụng bộ nhớ/tài nguyên hoặc các nguy cơ deserialization khác.

Với đầu vào không tin cậy:

1. Cân nhắc dùng một định dạng dữ liệu đơn giản, có schema rõ thay vì Java native serialization.
2. Nếu bắt buộc dùng `ObjectInputStream`, triển khai **`ObjectInputFilter`** ở Java 17 để giới hạn lớp, graph depth, số reference, kích thước array và tổng bytes theo phạm vi cho phép.
3. Giới hạn kích thước input trước khi deserialize, xác thực nguồn và kiểm tra business invariants sau khi đọc.
4. Đừng coi `serialVersionUID` hay object handle là biện pháp xác thực hoặc mã hóa.
5. Không log trực tiếp toàn bộ object graph trong môi trường nhạy cảm; có thể tạo vòng hoặc lộ dữ liệu.

Ví dụ minh họa bộ lọc đơn giản:

~~~java
// Đoạn minh họa, input là ObjectInputStream đã tạo.
// Hãy điều chỉnh pattern theo class thực tế, không copy
// một allowlist demo sang production mà chưa kiểm tra.
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
        "maxdepth=20;maxrefs=10000;maxbytes=1048576;maxarray=100000;!*");
input.setObjectInputFilter(filter);
~~~

Pattern trên **chặn mọi lớp** không được cho phép tường minh, nên không thể đọc ví dụ Node nếu giữ nguyên; nó chỉ minh họa ý nghĩa **giới hạn tài nguyên + deny-by-default**. Trong ứng dụng thật phải thêm danh sách lớp được phép theo ngữ cảnh. Đoạn minh họa cần `import java.io.ObjectInputFilter;`.

## 16. Thiết kế hợp lý cho dữ liệu ứng dụng

### Khi nên giữ chia sẻ như mặc định

Nếu nhiều entity đang sử dụng **cùng một object** có ý nghĩa (ví dụ một address/cache/config được chia sẻ trong graph), serialization mặc định giúp khôi phục đúng topology. Dùng `writeObject` thông thường và bảo đảm mọi object cần lưu trong graph có representation phù hợp.

### Khi cần snapshot phiên bản mới

Nếu viết liên tục nhiều snapshot của cùng object mutable vào **một ObjectOutputStream**, cần chính sách làm mới:

- `reset()` giữa các snapshot nếu chấp nhận việc object mới không chia sẻ identity với snapshot trước.
- Hoặc xây dựng DTO/immutable snapshot mới cho mỗi lần ghi.
- Đừng dùng `flush()` hoặc `close()` như cách yêu cầu serializer viết lại object đã có handle.

### Khi cần tránh lặp lại cả đồ thị

Nếu graph rất lớn với nhiều shared references, việc giữ handle có thể giảm kích thước stream đáng kể. Nhưng bảng handle cũng giữ trạng thái lâu trong luồng; một stream sống rất lâu có thể tiêu thụ bộ nhớ nhiều hơn. Có thể reset tại các boundary rõ ràng, chấp nhận dữ liệu được serialize lại sau reset.

### Khi cần persistence lâu dài

Java native serialization phụ thuộc định dạng lớp, serial UID và những quy tắc tương thích. Nếu lưu bền nhiều năm, truyền qua môi trường công nghệ khác hoặc cần kiểm soát schema, cân nhắc format có version và schema độc lập (ví dụ JSON/CBOR/Protocol Buffers phù hợp ngữ cảnh), đồng thời chủ động biểu diễn object IDs/references nếu cần chia sẻ và vòng.

## 17. Tự kiểm tra

1. Hai field trước khi ghi cùng trỏ tới một object. Sau deserialize mặc định, `field1 == field2`? **True.**
2. Hai object khác nhau nhưng `equals() == true`. Serializer có tự gộp? **Không.**
3. A.next=B và B.next=A. Vì sao không đệ quy vô hạn? **Serializer gán handle trước khi ghi xong các field và dùng back-reference khi gặp lại A.**
4. Object handle có phải Java memory address hay database ID không? **Không; đó là mã trong phạm vi stream.**
5. `writeObject(obj)` hai lần trong cùng stream, không reset. Hai lần đọc thường trả object nào? **Cùng instance đã deserialize qua back-reference.**
6. Sửa field sau lần ghi đầu rồi `writeObject` lại. Có tự gửi field mới không? **Không, nếu lần hai là back-reference.**
7. `ObjectOutputStream.reset()` có xóa bytes đã ghi không? **Không. Nó đặt lại trạng thái handle và ghi reset marker.**
8. `ByteArrayOutputStream.reset()` có giống ObjectOutputStream.reset không? **Không. Một cái xóa bytes trong bộ nhớ, một cái đặt lại trạng thái serialization.**
9. `writeUnshared(root)` có bảo đảm toàn bộ child object cũng không shared với graph trước không? **Không; chỉ root ở cấp gọi.**
10. `readUnshared()` đọc một back-reference có hợp lệ không? **Không, ném ObjectStreamException.**
11. Field `static` hoặc `transient` được default serialization lưu hay không? **Không.**
12. ObjectInputStream có nên đọc stream bất kỳ từ Internet không? **Không; cần kiểm soát nguồn, filter và giới hạn tài nguyên.**
13. `serialVersionUID` có thay thế versioning/scheme migration không? **Không.**
14. Dùng cùng class nhưng tạo hai ObjectOutputStream khác nhau, có dùng chung bảng handle không? **Không.**
15. Tại sao `original == restored` thường false dù graph giữ identity? **Deserialization tạo object trong một graph mới; identity chỉ được tái lập giữa các reference bên trong graph/stream được đọc.**

## 18. Tham khảo chính thức

- [Java SE 17 — ObjectOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectOutputStream.html): `writeObject`, `writeUnshared`, `reset` và tham chiếu dùng chung.
- [Java SE 17 — ObjectInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html): `readObject`, `readUnshared`, khôi phục đồ thị, serial filtering.
- [Java SE 17 — Java Object Serialization Specification: System Architecture](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/serial-arch.html): handles, shared references và cyclic references.
- [Java SE 17 — Object Output Classes](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/output.html): thuật toán gán handle, reset, writeUnshared.
- [Java SE 17 — Object Input Classes](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/input.html): phục hồi handle, reset token và readUnshared.
- [Java SE 17 — Stream Protocol](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/protocol.html): định dạng phần tử, TC_REFERENCE, TC_RESET và class descriptors.
- [Java SE 17 — ObjectInputFilter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html): giới hạn và lọc dữ liệu khi deserialize.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serialization-basics-005">Trường transient, static và trạng thái mặc định được lưu</a>

<details>
<summary>Xem chi tiết</summary>

# Trường transient, static và trạng thái mặc định được lưu khi tuần tự hóa Java

Trong cơ chế Java Object Serialization, `ObjectOutputStream.writeObject(object)` biến trạng thái của đồ thị đối tượng được phép tuần tự hóa thành dữ liệu có thể ghi ra stream; `ObjectInputStream.readObject()` dựng lại đồ thị đối tượng khi đọc. Tuy nhiên, **không phải mọi trường nhìn thấy trong lớp đều được ghi vào stream**.

Với một lớp triển khai `Serializable` và dùng cơ chế mặc định, Java lưu các **trường instance không mang modifier `static` và không mang modifier `transient`**. Giá trị của trường `transient` không được ghi theo cơ chế mặc định; trường `static` thuộc về lớp chứ không thuộc trạng thái instance nên cũng không được lưu. Khi giải tuần tự hóa, trường `transient` có giá trị mặc định của kiểu, thay vì nhận lại giá trị đã có trước khi ghi.

Hiểu quy tắc này giúp tránh vô tình lưu dữ liệu nhạy cảm, khôi phục sai cache/session, hoặc ngộ nhận rằng serialization là một bản sao toàn bộ trạng thái của JVM.

## 1. Điều kiện nền tảng: Serializable và Object Streams

Cơ chế tuần tự hóa đối tượng chuẩn gồm:

- `java.io.Serializable`: marker interface báo lớp có thể tham gia cơ chế Java serialization mặc định. Interface này **không có phương thức cần triển khai**.
- `ObjectOutputStream`: ghi đối tượng và các đối tượng được tham chiếu hợp lệ tới một `OutputStream`.
- `ObjectInputStream`: đọc lại biểu diễn stream để tái tạo đối tượng; `readObject()` trả `Object` và caller cần kiểm tra/ép kiểu phù hợp.
- `serialVersionUID`: mã phiên bản dùng kiểm tra khả năng tương thích của lớp serializable giữa lúc ghi và đọc. Thường khai báo tường minh `private static final long serialVersionUID = 1L;`.
- `IOException`, `ClassNotFoundException`, `NotSerializableException`, `InvalidClassException`: một số lỗi cần hiểu và xử lý khi dùng object stream.

Ví dụ khung sử dụng:

```java
// Minh họa: file là Path và Account implements Serializable.
try (ObjectOutputStream out =
        new ObjectOutputStream(Files.newOutputStream(file))) {
    out.writeObject(account);
}

try (ObjectInputStream in =
        new ObjectInputStream(Files.newInputStream(file))) {
    Account restored = (Account) in.readObject();
}
```

Đây là **đoạn minh họa**, không phải chương trình Java độc lập: cần khai báo `file`, `account`, lớp `Account`, import `java.io.*` / `java.nio.file.*` và xử lý checked exceptions. Stream phải được đóng đúng vòng đời bằng `try-with-resources`.

**Quan trọng về bảo mật:** Không gọi `ObjectInputStream.readObject()` trên dữ liệu không tin cậy. Java native deserialization có thể kích hoạt code trong quá trình tái tạo đối tượng, gây nguy cơ tấn công qua object graph/gadget. Nếu buộc phải nhận dữ liệu từ nguồn bên ngoài, phải có giới hạn/filter, định dạng và mô hình tin cậy phù hợp; với giao thức công khai thường nên chọn định dạng dữ liệu tường minh như JSON hoặc Protocol Buffers thay vì native object serialization.

## 2. Chính xác trường nào thuộc trạng thái mặc định được lưu?

Một lớp như sau:

```java
import java.io.Serializable;

final class Student implements Serializable {
    private static final long serialVersionUID = 1L;

    String name;                   // Instance: được lưu mặc định.
    int score;                     // Instance: được lưu mặc định.
    final int admissionYear;       // final instance: được lưu mặc định.

    transient String loginToken;   // transient: không lưu mặc định.
    transient int failedAttempts;  // transient: không lưu mặc định.

    static String activeSemester;  // static: không thuộc instance.
    static int onlineCount;        // static: không lưu mặc định.

    Student(String name, int score, int admissionYear) {
        this.name = name;
        this.score = score;
        this.admissionYear = admissionYear;
    }
}
```

Đoạn code này là **lớp minh họa**, có thể biên dịch khi được đặt trong tệp Java phù hợp; nó chưa có phương thức `main` hay code ghi/đọc stream.

| Khai báo field | Được lưu bởi default serialization? | Kết quả khi đọc lại |
| --- | --- | --- |
| `String name` | Có | Giá trị `name` đã ghi |
| `int score` | Có | Giá trị `score` đã ghi |
| `final int admissionYear` | Có | Giá trị final đã ghi vẫn được khôi phục theo cơ chế serialization |
| `transient String loginToken` | Không | `null` nếu không được khôi phục bằng code tùy biến |
| `transient int failedAttempts` | Không | `0` nếu không được khôi phục bằng code tùy biến |
| `static String activeSemester` | Không | Giá trị đang có của **lớp** trong JVM đọc |
| `static int onlineCount` | Không | Giá trị đang có của **lớp** trong JVM đọc |
| `static final long serialVersionUID` | Không phải instance field | Được Java serialization dùng làm thông tin phiên bản trong class descriptor, không phải một giá trị field của từng đối tượng |

Đây là **quy tắc mặc định**, chưa tính các cơ chế serialization tùy biến như `private writeObject/readObject`, `Externalizable` hoặc `serialPersistentFields`. Những cơ chế đó có thể quyết định lưu thêm/khác dữ liệu. Không suy rộng “transient không bao giờ được ghi” thành lời hứa tuyệt đối nếu chính lập trình viên chủ động ghi dữ liệu đó trong phương thức tùy biến.

### Những modifier dễ gây nhầm

- `private`, `protected`, package-private, `public` **không tự quyết định** field có được lưu hay không. Private instance field vẫn được lưu mặc định nếu không static/transient.
- `final` **không loại field ra khỏi serialization**. Một instance field final vẫn có thể được lưu và khôi phục.
- `volatile` không tự loại field ra khỏi serialization; nó giải quyết quy tắc hiển thị bộ nhớ giữa luồng, không phải chính sách lưu trạng thái.
- `static` và `transient` là hai lý do độc lập khiến field không nằm trong tập instance fields mặc định được lưu.
- `serialVersionUID` là thành phần phục vụ tương thích class descriptor; nó không làm cho mọi static field khác trở thành dữ liệu của đối tượng.

## 3. transient: không lưu giá trị và nhận lại giá trị mặc định

`transient` là modifier áp dụng cho **field**, dùng khi giá trị đó không nên thuộc trạng thái tuần tự hóa mặc định của đối tượng. Trường hợp thường gặp:

1. **Thông tin phiên làm việc tạm thời:** Token đăng nhập, session ID, kết nối đang mở, context request.
2. **Cache hoặc dữ liệu có thể tính lại:** Kết quả cache, chỉ mục dẫn xuất, tổng hợp tạm.
3. **Tài nguyên không thể hoặc không nên tuần tự hóa:** Socket, stream, lock, thread pool, handle hệ điều hành.
4. **Dữ liệu chỉ có ý nghĩa trong tiến trình hiện tại:** Bộ đếm tạm, thời gian tính từ clock nội bộ, trạng thái lazy initialization.

Ví dụ:

```java
class Profile implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    String username;
    transient String sessionToken;

    Profile(String username, String sessionToken) {
        this.username = username;
        this.sessionToken = sessionToken;
    }
}
```

Nếu `Profile("an", "SECRET")` được ghi bằng `ObjectOutputStream` rồi đọc bằng `ObjectInputStream` và không có code tùy biến, `username` sẽ là `"an"`, còn `sessionToken` là `null`. Điều này chỉ nói về **stream serialization mặc định**, không có nghĩa bản thân chuỗi bí mật đã biến mất khỏi RAM, log hay những bản ghi khác.

### Mặc định của từng kiểu khi deserialize

| Kiểu field transient | Giá trị khôi phục mặc định |
| --- | --- |
| `byte`, `short`, `int`, `long` | `0` |
| `float`, `double` | `0.0` |
| `boolean` | `false` |
| `char` | `'\u0000'` (null character) |
| Mọi tham chiếu object như `String`, `List<?>` | `null` |

**Không nhầm với giá trị trước khi ghi.** Nếu `transient int retryCount = 7;` và constructor sau đó đặt nó bằng 12, giá trị 12 không được lưu mặc định. Khi đọc lại, nó thường bằng 0, **không phải 7** và không phải 12.

### Field initializer và constructor không tự chạy lại cho lớp Serializable

Đây là khác biệt rất quan trọng: Khi giải tuần tự hóa một instance của lớp triển khai `Serializable`, **constructor và instance field initializer của chính lớp serializable đó không được gọi như khi `new` một instance bình thường**. Vì vậy, field khai báo:

```java
transient String token = "new-session";
transient int cachedCount = 99;
```

sẽ **không tự nhận** `"new-session"` và 99 sau khi giải tuần tự hóa mặc định; nó có thể nhận `null` và 0. Nếu cần tái khởi tạo cache, dùng cách khôi phục rõ ràng như `readObject()` hoặc một bước khởi tạo có kiểm soát sau khi đọc.

Có ngoại lệ liên quan **non-serializable superclass**: Constructor không đối số có khả năng truy cập của lớp cha đầu tiên không triển khai `Serializable` được chạy khi giải tuần tự hóa, do đó trạng thái của phần lớp cha đó có quy tắc khởi tạo khác. Không suy luận rằng *mọi constructor của toàn bộ phân cấp* đều bị bỏ qua.

## 4. static: trạng thái của lớp không nằm trong stream của instance

`static` đại diện cho biến thuộc **lớp**, chia sẻ giữa các instance cùng lớp (trong cùng ngữ cảnh class loading), không phải trạng thái riêng của từng object. Ví dụ:

```java
class Settings implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    static String environment = "DEV"; // Không ghi theo instance.
    String owner;                       // Có ghi mặc định.
}
```

Nếu chương trình ghi một `Settings` khi `environment="DEV"`, sau đó đổi `Settings.environment="PROD"` và đọc lại object trong **chính JVM đó**, `owner` được khôi phục từ stream, nhưng `environment` vẫn là `"PROD"`. Nó không trở về `"DEV"`.

Với **JVM khác**, giá trị static phụ thuộc quá trình tải/khởi tạo lớp và các thay đổi trong JVM đọc, chứ không phụ thuộc giá trị static của JVM ghi. Không có quy tắc rằng stream chứa snapshot của các biến global/static hay cấu hình tiến trình.

Kể cả khai báo `static final` cũng không biến nó thành instance field có trong stream. `serialVersionUID` mang vai trò riêng trong giao thức serialization như đã nêu, nhưng không làm thay đổi quy tắc của những static fields thông thường.

### Một hệ quả trong ứng dụng

Nếu bạn định serialize đối tượng cấu hình:

```java
static String region = "ap-southeast-1";
String serviceName = "billing";
```

thì chỉ `serviceName` thuộc dữ liệu instance được lưu mặc định. Nếu khôi phục phải đi cùng một `region` cụ thể, hãy thiết kế nó như một instance field thực sự thuộc snapshot, hoặc lưu cấu hình độc lập bằng định dạng rõ ràng, **đừng dựa vào static serialization**.

## 5. Ví dụ Java 17+ hoàn chỉnh: so sánh trước và sau khi đọc lại

Ví dụ ghi đối tượng ra tệp tạm, đổi giá trị static sau khi ghi, rồi đọc lại trong cùng JVM. Đồng thời đếm số lần constructor được gọi để minh họa rằng quá trình `readObject()` không gọi constructor của chính lớp serializable.

Tạo tệp **`TransientStaticDemo.java`**:

```java
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TransientStaticDemo {
    private TransientStaticDemo() {}

    private static final class Snapshot implements Serializable {
        private static final long serialVersionUID = 1L;

        static String environment = "initial";
        static int constructorCalls = 0;

        String title;
        int level;
        final int version;

        transient String sessionToken = "initializer-token";
        transient int retryCount = 77;

        Snapshot(String title, int level, int version,
                 String sessionToken, int retryCount) {
            constructorCalls++;
            this.title = title;
            this.level = level;
            this.version = version;
            this.sessionToken = sessionToken;
            this.retryCount = retryCount;
        }

        String describe() {
            return "title=" + title
                    + ", level=" + level
                    + ", version=" + version
                    + ", token=" + sessionToken
                    + ", retries=" + retryCount
                    + ", environment=" + environment;
        }
    }

    public static void main(String[] args)
            throws IOException, ClassNotFoundException {
        Path file = Files.createTempFile(
                "transient-static-demo-", ".bin");

        try {
            Snapshot.environment = "before-save";
            Snapshot original =
                    new Snapshot("Java", 17, 1, "SECRET", 5);

            System.out.println("before: " + original.describe());

            try (ObjectOutputStream out = new ObjectOutputStream(
                    Files.newOutputStream(file))) {
                out.writeObject(original);
            }

            Snapshot.environment = "after-save";

            Snapshot restored;
            try (ObjectInputStream in = new ObjectInputStream(
                    Files.newInputStream(file))) {
                restored = (Snapshot) in.readObject();
            }

            System.out.println("after: " + restored.describe());
            System.out.println("constructor calls="
                    + Snapshot.constructorCalls);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac TransientStaticDemo.java
java TransientStaticDemo
```

Kết quả khi I/O tệp tạm thành công:

```text
before: title=Java, level=17, version=1, token=SECRET, retries=5, environment=before-save
after: title=Java, level=17, version=1, token=null, retries=0, environment=after-save
constructor calls=1
```

**Vì sao output như vậy?**

1. `title`, `level`, `version` là instance fields không static/transient, do đó được lưu và khôi phục. `version` có `final` nhưng vẫn được tuần tự hóa mặc định.
2. `sessionToken` và `retryCount` có `transient`, nên sau `readObject()` lần lượt là `null` và 0. Các initializer `"initializer-token"` và 77 **không chạy lại** để khôi phục đối tượng.
3. `environment` là biến `static`, nên stream không ghi giá trị `"before-save"`. Khi giải tuần tự hóa trong cùng JVM, nó là `"after-save"` theo lần gán mới nhất.
4. `constructorCalls` là biến `static` được tăng khi gọi `new Snapshot(...)` lúc tạo object đầu tiên. Nó vẫn bằng 1 sau khi `readObject()`, minh họa constructor của `Snapshot` không được gọi lần hai.
5. Chương trình đóng stream ghi trước khi mở stream đọc, đóng stream đọc và xóa tệp tạm trong `finally`. Tệp tạm chỉ phục vụ ví dụ, không chứa dữ liệu người dùng thực.

Ví dụ là phép kiểm tra hành vi serialization **trong cùng một JVM**. Trong chương trình khác hoặc lần chạy khác, static fields thuộc lớp được khởi tạo độc lập. Đừng dựa vào việc thay đổi static trong producer có tác động tới process nhận.

## 6. Default serialization lưu cả đồ thị tham chiếu, không chỉ kiểu nguyên thủy

Các instance fields không static/transient có thể là primitive, `String`, array, collections hoặc đối tượng tùy biến. Khi `ObjectOutputStream.writeObject(root)` duyệt đồ thị được phép tuần tự hóa, những đối tượng **reachable qua các field được lưu** cũng cần tương thích với serialization.

Ví dụ:

```java
final class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    String orderId;       // String serializable.
    java.util.ArrayList<String> items; // ArrayList và String serializable.
    Object helper;        // Object thường không triển khai Serializable.
}
```

Đây là phần định nghĩa minh họa, cần import `Serializable`. Nếu `helper` trỏ tới một `new Object()` thông thường và không được đánh dấu `transient`, `writeObject(order)` có thể ném `NotSerializableException` khi chạm tới object này. Không đủ nếu **lớp gốc** `Order` triển khai `Serializable` nhưng một object bên dưới đồ thị lại không serializable.

Nếu `helper` chỉ là tài nguyên runtime, có thể đánh dấu:

```java
transient Object helper;
```

Khi đó default serialization không duyệt reference qua field này, tránh cố ghi `helper`. Tuy nhiên sau khi đọc, `helper` sẽ là `null` và code phải chịu trách nhiệm khởi tạo lại nếu còn cần dùng.

### Identity và tham chiếu chia sẻ

Java serialization có cơ chế nhận diện đối tượng trong stream: nếu hai field cùng tham chiếu tới một object và serialization thành công, khi đọc lại chúng thường cùng trỏ tới một object được khôi phục (giữ sharing/cycles trong đồ thị). Đây không phải deep-copy độc lập cho từng field. Ngược lại, những object chỉ có thể tiếp cận qua `transient` field sẽ không được đưa vào đồ thị theo đường reference đó.

Một `static` field giữ một object cũng không tự khiến object ấy nằm trong stream của mọi instance. Nếu object đó còn được tham chiếu bởi một **instance field** được lưu, nó vẫn có thể được serialize theo đường tham chiếu instance.

## 7. Khôi phục các field transient bằng readObject khi thật sự cần

Có những field không nên lưu mà phải **tính lại** sau khi giải tuần tự hóa, ví dụ chiều dài tên hiển thị hoặc cache dẫn xuất. Một lớp serializable có thể khai báo phương thức `private void readObject(ObjectInputStream in)` để điều chỉnh hành vi giải tuần tự hóa.

Ví dụ:

```java
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

final class CachedName implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private transient int cachedLength;

    CachedName(String name) {
        this.name = name;
        this.cachedLength = name == null ? 0 : name.length();
    }

    int length() {
        return cachedLength;
    }

    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject(); // Khôi phục các field mặc định.
        cachedLength = name == null ? 0 : name.length();
    }
}
```

Đây là **lớp minh họa có thể biên dịch** cùng các import đã cho, nhưng không có chương trình `main`. Khi đọc lại, `defaultReadObject()` lấy `name` từ stream, rồi `cachedLength` được tính lại. Không cần lưu một cache vốn có thể suy ra từ dữ liệu đã lưu.

Những điểm cần lưu ý:

- `readObject` ở đây phải là **phương thức private có đúng chữ ký** cho cơ chế serialization mặc định, không phải override của `ObjectInputStream.readObject()`.
- `in.defaultReadObject()` thực hiện khôi phục các trường mặc định của lớp; không tự phục hồi `transient`.
- Với dữ liệu đọc từ nguồn không tin cậy, code `readObject` cũng là nơi có thể bị khai thác; **không coi việc có custom readObject là hàng rào an toàn**.
- Khi lớp có invariants, các giá trị khôi phục cần được kiểm tra chặt chẽ. Nếu vi phạm, có thể ném `InvalidObjectException` theo thiết kế.
- Tránh serialize những đối tượng phụ thuộc môi trường (socket, lock, executor) rồi giả định chúng hoạt động sau khi phục hồi; hãy cấp phát lại tài nguyên runtime khi cần.

### transient có thể bị ghi thủ công nếu code quyết định như vậy

Khi lập trình viên viết `private writeObject(ObjectOutputStream out)`, họ có thể gọi `defaultWriteObject()` để lưu các instance fields mặc định **và còn có thể tự gọi** `out.writeObject(secret)` để ghi thêm dữ liệu. Vì vậy `transient` chỉ loại field khỏi **tập field mặc định**, không ngăn một custom serializer cố ý đưa giá trị đó vào stream theo cách riêng.

Nếu mục tiêu là không để bí mật xuất hiện trong file, phải kiểm tra toàn bộ đường serialization và những lớp dữ liệu liên quan, không dựa duy nhất vào một modifier.

## 8. static, dữ liệu không tuần tự hóa và tính nhất quán sau khôi phục

Khi một object dùng cả instance fields và static fields trong nghiệp vụ, hành vi sau khi khôi phục có thể khác thời điểm ghi:

```java
// Đoạn minh họa nghiệp vụ.
class PricePolicy implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    static double taxRate = 0.10; // Cấu hình của JVM hiện tại.
    double subtotal;              // Một phần trạng thái instance.

    double total() {
        return subtotal * (1 + taxRate);
    }
}
```

Nếu `subtotal` được ghi là 100 và lúc đọc trên môi trường khác `taxRate` là 0.20, `total()` có thể trả 120 thay vì 110. Stream không sai: **thông tin static không có trong snapshot**. Nhưng hệ thống sẽ sai nếu kỳ vọng phép tính luôn được tái hiện giống lúc ghi.

Cách thiết kế tùy nghiệp vụ:

1. Nếu `taxRate` là thuộc tính của **đơn hàng tại thời điểm lập**, lưu nó như instance field `taxRateApplied` hoặc một object giá trị thuộc snapshot.
2. Nếu `taxRate` cố tình là cấu hình hiện hành lúc chạy, việc giá trị thay đổi sau khôi phục có thể đúng; nên thể hiện rõ trong logic và kiểm thử.
3. Không dùng serialization của object bất kỳ để thay thế cấu hình, schema lưu trữ hoặc cơ chế migration dữ liệu lâu dài.

Cùng nguyên tắc áp dụng cho feature flags, đường dẫn hệ thống, singleton runtime, service locator và cache static.

## 9. serialVersionUID, thay đổi lớp và dữ liệu mặc định của phiên bản mới

`serialVersionUID` hỗ trợ xác định lớp đọc có tương thích với thông tin lớp trong stream hay không. Khi không khai báo, JVM có thể tính tự động từ đặc điểm lớp, dẫn tới thay đổi giá trị không mong muốn giữa các phiên bản source/build. Với lớp dự kiến tuần tự hóa, thường nên khai báo giá trị tường minh.

Ví dụ lớp cũ:

```java
class Data implements Serializable {
    private static final long serialVersionUID = 1L;
    String name;
}
```

Và phiên bản mới thêm instance field:

```java
class Data implements Serializable {
    private static final long serialVersionUID = 1L;
    String name;
    int priority;
}
```

Đây là hai **phiên bản minh họa, không được khai báo đồng thời cùng tên trong một tệp biên dịch**. Nếu hai bên tương thích theo serialization protocol và cùng `serialVersionUID`, object cũ không có trường `priority` trong stream; khi đọc bằng lớp mới, `priority` nhận `0` (giá trị mặc định cho trường mới).

Nếu thay đổi gây bất tương thích, có thể gặp `InvalidClassException`. Giữ UID bằng nhau **không bảo đảm mọi thay đổi đều tương thích**: thay kiểu field, cấu trúc phân cấp hoặc cách custom serialization có thể làm dữ liệu không đọc được hoặc làm sai invariants.

Lưu ý phân biệt hai trường hợp nhận giá trị mặc định:

- `transient` field: không được lưu bởi default mechanism, nên sau khi deserialize nhận default nếu không có khôi phục tùy biến.
- Instance field **mới thêm** vào phiên bản class đọc: không có trong stream cũ, nên có thể nhận default nếu việc chuyển phiên bản được phép.

Trong cả hai, instance initializer của lớp serializable không tự bảo đảm chạy lại để đặt một giá trị cấu hình mong muốn.

## 10. Một số sai lầm phổ biến

| Ngộ nhận / lỗi | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Nghĩ mọi field của object đều được ghi | Static/transient bị bỏ qua, snapshot thiếu dữ liệu nghiệp vụ | Xác định field thuộc trạng thái instance cần lưu |
| Nghĩ `private` có nghĩa không serialize | Bí mật trong private field thường vẫn được lưu mặc định | Dùng thiết kế dữ liệu phù hợp; cân nhắc transient và custom serializer |
| Nghĩ `final` instance field sẽ bị loại | Đánh giá sai cấu trúc stream | Final instance field vẫn được lưu mặc định |
| Nghĩ static sẽ được khôi phục về giá trị lúc ghi | Kết quả xử lý khác nhau giữa JVM ghi/đọc | Không dựa vào static như một phần snapshot |
| Nghĩ transient giữ giá trị được gán trong constructor | Sau deserialize nhận `0/null/false` | Khởi tạo lại rõ ràng khi cần |
| Dùng transient trên object runtime nhưng không khởi tạo sau khi đọc | `NullPointerException` khi dùng cache, lock, service | Dùng `readObject` hoặc post-load initialization |
| Field thường trỏ tới đối tượng không Serializable | `NotSerializableException` khi ghi graph | Kiểm tra toàn bộ graph hoặc loại reference runtime |
| Tin rằng `transient` luôn bảo mật tuyệt đối | Code custom writeObject hoặc nguồn log khác vẫn có thể lộ | Kiểm tra toàn bộ đường xuất dữ liệu và bảo mật |
| Không khai báo `serialVersionUID` khi cần lưu dài hạn | Rủi ro bất tương thích do UID mặc định thay đổi | Quản lý UID và migration có chủ đích |
| Coi deserialize là gọi constructor bình thường | Invariants, cache/initializer có thể không hợp lệ | Validate, khôi phục trạng thái runtime sau đọc |
| Đọc object stream từ nguồn không tin cậy | Nguy cơ thực thi luồng deserialization nguy hiểm | Tránh native deserialization đối với dữ liệu không tin cậy; lọc và hạn chế nơi cần |
| Cho rằng mọi instance sau khi đọc độc lập tuyệt đối | Tham chiếu chia sẻ trong graph vẫn được bảo toàn | Hiểu identity và mutable sharing của đồ thị khôi phục |

## 11. Hướng dẫn chọn field cần lưu

Một cách thực tế là chia trạng thái của lớp thành ba nhóm:

| Nhóm trạng thái | Ví dụ | Chiến lược |
| --- | --- | --- |
| Dữ liệu nghiệp vụ cần khôi phục đúng | Mã đơn hàng, danh sách mục, giá trị tại thời điểm lập | Instance fields được lưu hoặc schema tường minh |
| Dữ liệu có thể tính lại/không nên lưu | Cache tính toán, session token, connection handle, listener, lock | `transient` và quy trình tái khởi tạo |
| Trạng thái toàn cục của môi trường | Feature flag hiện hành, cấu hình service, thống kê static | Không kỳ vọng native instance serialization lưu nó; quản lý cấu hình độc lập |

Trước khi đánh dấu một field là `transient`, hãy tự hỏi:

1. Nếu field trở thành `null` hoặc `0` khi khôi phục, code có còn hoạt động đúng không?
2. Có cần khởi tạo lại field từ dữ liệu khác, hoặc nó chỉ tồn tại trong một phiên?
3. Field có là đường duy nhất tham chiếu tới những dữ liệu cần được lưu trong object graph không?
4. Nếu field không bị đánh dấu transient, đối tượng phía sau có `Serializable` không?
5. Có yêu cầu bảo mật nào khiến bản thân native serialization không phù hợp cho mục đích lưu trữ/trao đổi không?

**Ghi nhớ:** Với Java serialization mặc định, **instance field không `static` và không `transient` mới là trạng thái được lưu**. `transient` quay về giá trị mặc định của kiểu khi đọc lại nếu không có code khôi phục; `static` thuộc trạng thái lớp trong JVM đang chạy, không được phục hồi từ stream của object. Constructor và field initializer của chính lớp `Serializable` không chạy lại như lệnh `new`. Dùng các quy tắc này để thiết kế snapshot rõ ràng, tránh phụ thuộc sai vào runtime state và chỉ deserialize nguồn dữ liệu đáng tin cậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serialization-basics-006">serialVersionUID và tương thích lớp</a>

<details>
<summary>Xem chi tiết</summary>

# serialVersionUID và tương thích lớp trong Java Serialization

Khi một đối tượng Java được ghi bằng `ObjectOutputStream`, dữ liệu có thể tồn tại trên đĩa hoặc truyền sang một chương trình khác lâu hơn vòng đời của phiên bản lớp đã tạo ra nó. Nếu cấu trúc lớp thay đổi trước khi dữ liệu được đọc lại bằng `ObjectInputStream`, Java phải quyết định liệu dữ liệu cũ còn đọc được với lớp hiện tại hay không. `serialVersionUID` là một phần của cơ chế kiểm tra đó.

Bài này áp dụng cho **Java Object Serialization** (`java.io.Serializable`), không phải cơ chế đổi phiên bản dữ liệu của JSON, JDBC, protobuf hay Java Records theo một giao thức khác.

## 1. Vai trò của serialVersionUID

Một lớp muốn tham gia Java Object Serialization thường triển khai marker interface `Serializable`. Khi ghi đối tượng, `ObjectOutputStream` ghi thông tin mô tả lớp (class descriptor), trong đó có tên lớp, mô tả các trường được tuần tự hóa và **serial version UID**. Khi đọc, Java tìm lớp hiện có tương ứng và so sánh UID trong stream với UID của lớp cục bộ.

- **UID khớp:** Java tiếp tục kiểm tra cấu trúc và giải mã dữ liệu theo các quy tắc tương thích. Khớp UID **không bảo đảm** mọi thay đổi lớp đều tương thích.
- **UID khác:** Với lớp `Serializable` thông thường, quá trình đọc thất bại với `java.io.InvalidClassException` (thông báo thường chứa “local class incompatible” cùng hai UID).
- **Lớp không tìm thấy:** Có thể nhận `ClassNotFoundException` trước khi có đối tượng; đặt cùng UID không giải quyết tên lớp/package sai hoặc thiếu classpath.

Khai báo được khuyến nghị:

```java
import java.io.Serializable;

public class Invoice implements Serializable {
    private static final long serialVersionUID = 1L;

    private String code;
    private long total;
}
```

`serialVersionUID` phải là trường có tên chính xác, kiểu `long`, `static` và `final`; thông thường dùng `private`. Đây là **metadata về phiên bản lớp**, không phải trường dữ liệu của từng đối tượng. UID không mã hóa, xác thực hay kiểm tra tính toàn vẹn dữ liệu. Không cần tăng UID sau *mọi* lần sửa mã nguồn; chỉ đổi có chủ đích khi quyết định rằng dữ liệu serialized từ phiên bản trước **không còn được phép/không thể** đọc theo cấu trúc mới.

Nếu **không** khai báo UID, JVM tính UID mặc định dựa trên một số đặc điểm của lớp (tên lớp, modifiers, interfaces, các trường/phương thức/constructor tham gia thuật toán). Vì vậy, những thay đổi mã nguồn tưởng như không liên quan đến dữ liệu có thể làm UID tính toán thay đổi và khiến việc đọc stream cũ thất bại. Khai báo UID tường minh giúp giữ ổn định hợp đồng tương thích nhưng không biến thay đổi cấu trúc nguy hiểm thành an toàn.

> Hai lớp đều ghi `serialVersionUID = 1L` không có nghĩa chúng hoán đổi đối tượng cho nhau được. Việc giải tuần tự hóa còn dựa vào tên lớp nhị phân, cấu trúc lớp và mô tả các trường.

## 2. Java thực sự lưu gì?

Với cách tuần tự hóa mặc định của một lớp `Serializable`, Java lưu các trường instance **không phải `static` và không phải `transient`** trong các lớp serializable thuộc đồ thị đối tượng, cộng thông tin nhận dạng lớp và tham chiếu đối tượng cần thiết. Các đối tượng được tham chiếu từ những trường được lưu cũng phải tuần tự hóa được; nếu không, lúc ghi có thể xảy ra `NotSerializableException`.

- `static` thuộc về lớp, nên không nằm trong trạng thái instance được lưu. Khi đọc, giá trị của nó lấy từ lớp/JVM đang chạy.
- `transient` chủ động loại trường khỏi dữ liệu mặc định. Khi đọc, trường nhận giá trị mặc định của Java (ví dụ `null`, `0`, `false`) trừ khi code khôi phục riêng.
- Constructor và field initializer của chính lớp `Serializable` **không được gọi/chạy để tạo lại trạng thái** trong quá trình giải tuần tự hóa mặc định. Constructor không đối số của superclass đầu tiên **không** Serializable được gọi, theo cơ chế của Java.
- `serialVersionUID` là trường `static final` nên không phải field instance được ghi; giá trị UID thuộc **class descriptor**.

`Serializable` không có phương thức bắt buộc. `writeObject`, `readObject`, `serialPersistentFields` hoặc `Externalizable` là các cơ chế tùy biến riêng, làm thay đổi hợp đồng dữ liệu và cần được quản lý cẩn thận.

## 3. Thực hành: ghi bằng phiên bản 1, đọc bằng phiên bản 2

Ta dùng hai bộ nguồn cùng tên lớp `Customer` và cùng UID, nhưng **biên dịch ở hai thư mục riêng và chạy trong hai tiến trình khác nhau**. Không đặt hai file `Customer.java` cùng một classpath khi chạy.

### Bước A — phiên bản 1 ghi file

Tạo `v1/Customer.java`:

```java
import java.io.Serializable;

public class Customer implements Serializable {
    private static final long serialVersionUID = 1L;

    String name;
    int points;

    Customer(String name, int points) {
        this.name = name;
        this.points = points;
    }
}
```

Tạo `v1/Writer.java`:

```java
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

public class Writer {
    public static void main(String[] args) throws Exception {
        try (ObjectOutputStream out =
                new ObjectOutputStream(new FileOutputStream("customer.ser"))) {
            out.writeObject(new Customer("Lan", 120));
        }
        System.out.println("Da ghi customer.ser");
    }
}
```

Biên dịch và chạy từ thư mục chứa `v1` (cần JDK 17+; Java 17 và 21 đều dùng được):

```bash
javac -encoding UTF-8 -d v1-classes v1/Customer.java v1/Writer.java
java -cp v1-classes Writer
```

Stdout:

```text
Da ghi customer.ser
```

Lệnh tạo `customer.ser` trong thư mục làm việc hiện tại. Đây là **định dạng serialization nhị phân của Java**, không phải file text; đừng sửa bằng text editor. Giữ nguyên file để kiểm tra bản đọc v2.

### Bước B — phiên bản 2 thêm trường nhưng giữ UID

Tạo `v2/Customer.java`:

```java
import java.io.Serializable;

public class Customer implements Serializable {
    private static final long serialVersionUID = 1L;

    String name;
    int points;
    String level = "BASIC";
}
```

Tạo `v2/Reader.java`:

```java
import java.io.FileInputStream;
import java.io.ObjectInputStream;

public class Reader {
    public static void main(String[] args) throws Exception {
        try (ObjectInputStream in =
                new ObjectInputStream(new FileInputStream("customer.ser"))) {
            Customer c = (Customer) in.readObject();
            System.out.printf("name=%s, points=%d, level=%s%n",
                    c.name, c.points, c.level);
        }
    }
}
```

Biên dịch v2 **riêng**, đọc chính file `customer.ser` đã ghi ở bước A:

```bash
javac -encoding UTF-8 -d v2-classes v2/Customer.java v2/Reader.java
java -cp v2-classes Reader
```

Stdout chính xác:

```text
name=Lan, points=120, level=null
```

Giải thích:

1. `Customer` ở hai phía có cùng tên lớp và `serialVersionUID = 1L`.
2. Hai trường `name` và `points` có tên/kiểu tương ứng nên dữ liệu cũ được khôi phục.
3. `level` **không tồn tại trong stream phiên bản 1**, vì thế khi đọc bằng v2, Java gán mặc định `null`. Dòng khởi tạo `String level = "BASIC";` của lớp Serializable không chạy lúc giải tuần tự hóa, nên không có giá trị `"BASIC"`.

Đây là ví dụ về một thay đổi **tương thích ở mức cơ chế serialization**, nhưng có thể vẫn gây lỗi nghiệp vụ nếu code mới giả định `level` luôn khác `null`.

### Bước C — cố ý đổi UID và quan sát lỗi

Trong `v2/Customer.java`, chỉ đổi:

```java
private static final long serialVersionUID = 2L;
```

Biên dịch lại rồi đọc file cũ:

```bash
javac -encoding UTF-8 -d v2-classes v2/Customer.java v2/Reader.java
java -cp v2-classes Reader
```

Lệnh thứ hai thất bại với `java.io.InvalidClassException`; thông báo sẽ nêu stream UID `1` và local UID `2`. Đây là **lỗi có chủ đích** minh họa “không chấp nhận dữ liệu phiên bản cũ”. Muốn chạy lại B, khôi phục UID v2 về `1L` rồi biên dịch lại. Chỉ đổi UID về đúng số cũng **không sửa được** mọi thay đổi field bất tương thích.

## 4. Những thay đổi lớp nào tương thích?

Các quy tắc sau nói về **default serialization của Serializable**, giả sử tên lớp được tìm thấy và UID tương ứng được giữ nguyên. “Tương thích” nghĩa là stream có thể được đọc theo các quy tắc serialization; không tự bảo đảm dữ liệu còn đúng nghĩa nghiệp vụ.

| Thay đổi ở phiên bản mới | Kết quả thông thường khi đọc dữ liệu cũ | Hệ quả cần xử lý |
| --- | --- | --- |
| Thêm trường instance được serialize | Tương thích; trường mới mang giá trị mặc định `null`/`0`/`false` | Chuẩn hóa giá trị khi đọc nếu nghiệp vụ cần |
| Xóa trường instance | Tương thích; dữ liệu của trường cũ trong stream bị bỏ qua | Mất phần trạng thái cũ khi đọc bằng lớp mới |
| Đổi tên trường instance | Tương đương xóa tên cũ và thêm tên mới | Giá trị tên cũ không tự chuyển sang tên mới |
| Đổi `private`/`public` của trường nhưng giữ tên/kiểu | Thông thường tương thích khi UID cố định | Vẫn cần xem tác động về đóng gói |
| Biến trường instance thành `static` hoặc `transient` | Tương tự xóa field khỏi serialized form | Không khôi phục giá trị cũ vào trường ấy |
| Biến trường `static`/`transient` thành trường instance được serialize | Tương tự thêm field | Dữ liệu cũ không chứa field này; nhận mặc định |
| Đổi kiểu trường (ví dụ `int` sang `long`, hoặc `String` sang `Integer`) | **Không được coi là tương thích**; có thể `InvalidClassException` | Thiết kế chuyển đổi dữ liệu có phiên bản |
| Thêm/sửa phương thức bình thường | Thường không thay đổi serialized form mặc định nếu UID cố định | Có thể thay đổi logic dùng dữ liệu cũ |
| Đổi tên lớp, package hay chuyển classpath thiếu lớp | Việc ánh xạ tên lớp thất bại; có thể `ClassNotFoundException` | Cần chiến lược migration hoặc đọc bằng code cũ |
| Đổi từ `Serializable` sang `Externalizable` (hoặc ngược lại) | Bất tương thích định dạng/cơ chế | Không áp dụng cùng quy tắc field mặc định |
| Thay superclass, cấp kế thừa, cách ghi custom data | Phụ thuộc trường hợp và có thể bất tương thích | Kiểm thử trên file thật của các phiên bản |

**Phân biệt hai tầng:** UID khác thường chặn đọc ngay ở bước nhận diện version; UID giống chỉ cho phép Java thử đối chiếu dữ liệu theo quy tắc của serialization. Vì thế *giữ UID* phải đi cùng quyết định bảo toàn cấu trúc có thể đọc được và ý nghĩa dữ liệu.

Java định nghĩa chi tiết trong tài liệu **Java Object Serialization Specification**, mục *Versioning of Serializable Objects*. Với những thay đổi phức tạp về cây thừa kế, `writeObject/readObject`, `readResolve`, proxy hoặc enum/record, cần xem chính đặc tả và kiểm chứng bằng fixture hai chiều.

## 5. Khi thêm trường cần giá trị mặc định nghiệp vụ

Một trường mới không có dữ liệu trong stream cũ nên chỉ nhận giá trị mặc định của kiểu Java, **không nhận initializer** của chính lớp Serializable. Nếu `null` không hợp lệ cho `level`, có thể sửa logic sử dụng dữ liệu cũ, hoặc tùy biến `readObject` để bù giá trị thiếu.

Ví dụ đoạn sau **thay thế logic đọc mặc định trong lớp `Customer` v2** (đặt vào thân lớp; cần `import java.io.IOException;` và `import java.io.ObjectInputStream;`). Không phải chương trình độc lập:

```java
private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    ObjectInputStream.GetField fields = in.readFields();

    name = (String) fields.get("name", null);
    points = fields.get("points", 0);
    level = fields.defaulted("level")
            ? "BASIC"
            : (String) fields.get("level", null);
}
```

`GetField.defaulted("level")` kiểm tra field có vắng mặt khỏi stream đang đọc không; nhờ vậy có thể phân biệt “bản cũ chưa có field” với “bản mới chủ động lưu `null`”. Với yêu cầu đơn giản hơn, có thể dùng `in.defaultReadObject()` rồi kiểm tra và chuẩn hóa field, nhưng khi đó không phân biệt hai trường hợp nói trên. Nếu đã có `writeObject/readObject` riêng hoặc `serialPersistentFields`, phải duy trì cẩn thận định dạng đã công bố.

Không nên âm thầm đổi UID để “sửa” dữ liệu hay dùng ép kiểu để vượt qua lỗi. Đối với dữ liệu cần sống lâu, giải pháp bền vững thường là **định dạng dữ liệu có schema/version rõ ràng** với bước migration và bài kiểm thử dữ liệu lưu từ các phiên bản đã phát hành.

## 6. Tạo, kiểm tra và quản lý UID

- **Khai báo UID tường minh** cho các lớp Serializable mà ứng dụng thật sự lưu, cache bền vững hoặc gửi qua ranh giới phiên bản. Ví dụ `private static final long serialVersionUID = 1L;`.
- **Giữ UID** khi thay đổi vẫn tương thích và chương trình mới vẫn hiểu dữ liệu cũ. Kiểm tra từ cả cấp dữ liệu và cấp nghiệp vụ.
- **Đổi UID có chủ đích** khi cần từ chối file cũ và chấp nhận `InvalidClassException`. Sau đó có kế hoạch xóa cache cũ, migration hoặc hỗ trợ phiên bản cũ nếu cần.
- **Không coi UID là số phiên bản ứng dụng** hay timestamp. Không có quy tắc bắt buộc phải dùng `1L`, `2L` theo mỗi release.
- **Công cụ `serialver`** của JDK có thể in `serialVersionUID` của class đã biên dịch, ví dụ `serialver -classpath v1-classes Customer`. API `ObjectStreamClass.lookup(Customer.class).getSerialVersionUID()` cũng cho biết UID class theo runtime.
- **Không dựa vào UID tự tính** nếu yêu cầu tương thích dữ liệu lâu dài. Một thay đổi chữ ký, modifier hoặc toolchain có thể làm UID mặc định khác.
- **Kiểm thử bằng file fixture thật:** ghi bằng v1 và đọc bằng v2 (cân nhắc cả chiều ngược nếu hỗ trợ rollback); kiểm tra cả giá trị, invariant và lỗi mong đợi. Giữ fixture trong bộ kiểm thử tương thích khi schema thay đổi.

Lưu ý: một số kiểu Java có quy tắc serialization đặc biệt, ví dụ `enum` được biểu diễn chủ yếu theo tên hằng và `record` có quy tắc versioning riêng. Không suy diễn toàn bộ ví dụ class Serializable thông thường sang những kiểu đó.

## 7. Sự cố thường gặp và cách phân biệt

| Triệu chứng | Nguyên nhân cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| `InvalidClassException: local class incompatible` | UID trong stream khác UID của lớp đang chạy | Kiểm tra classpath/build thực tế, UID hai phía và chính sách migration |
| `InvalidClassException` dù UID bằng nhau | Thay đổi cấu trúc hoặc yêu cầu serialization không hợp lệ | Đối chiếu field type, hierarchy, Serializable/Externalizable, custom format |
| `ClassNotFoundException` khi `readObject()` | Không tìm thấy tên lớp trong stream | Bổ sung đúng classpath, duy trì lớp cũ hoặc chuyển đổi định dạng |
| `NotSerializableException` khi `writeObject()` | Một đối tượng trong graph không Serializable | Sửa model hoặc đánh dấu `transient` nếu có chủ đích |
| Trường mới trả về `null` dù đã có initializer | Initializer Serializable không chạy lúc đọc object cũ | Migrate/khôi phục ở `readObject` hoặc lớp nghiệp vụ |
| Dữ liệu cũ đọc được nhưng hành vi sai | Tương thích binary không đồng nghĩa tương thích ngữ nghĩa | Validate invariant, cung cấp giá trị mặc định, migrate dữ liệu |

Vì `ObjectInputStream.readObject()` có thể tạo đối tượng, kích hoạt cơ chế khôi phục tùy biến và tiếp cận cả đồ thị đối tượng, **không bao giờ coi dữ liệu Java serialized từ nguồn không tin cậy là an toàn**. Với hệ thống mới, ưu tiên định dạng dữ liệu có schema và kiểm soát kiểu. Nếu bắt buộc đọc Java serialization, kiểm soát nguồn dữ liệu, đặt giới hạn kích thước/độ sâu/tham chiếu và allowlist class bằng `ObjectInputFilter` (Java 9+), đồng thời áp dụng quy trình bảo mật phù hợp. UID không cung cấp bảo vệ bảo mật.

## 8. Câu hỏi tự kiểm tra

1. V1 có `int points`, v2 thêm `boolean active` và giữ UID. Khi đọc dữ liệu từ v1, `active` là gì? **Đáp án:** `false` (nếu không tùy biến khôi phục).
2. V1 lưu `String fullName`, v2 đổi tên thành `name` và giữ UID. Giá trị cũ có tự gán sang `name` không? **Không**; cần migration/ánh xạ cụ thể.
3. V1 và v2 giống field nhưng đổi UID từ `1L` thành `2L`. `readObject()` sẽ làm gì? **Ném `InvalidClassException`** đối với Serializable class thông thường.
4. Có thể giữ UID để biến field `int` thành `long` rồi kỳ vọng Java tự chuyển đổi không? **Không**. Cần thiết kế thay đổi serialized form hoặc migration.
5. Vì sao ví dụ `level = "BASIC"` vẫn in `null` ở bản v2? **Vì initializer của lớp Serializable không thực thi khi đối tượng v1 được giải tuần tự hóa**.

**Tài liệu gốc:** Java Object Serialization Specification — các phần *Class Descriptors*, *Versioning of Serializable Objects* và *Input*; Java API docs cho `Serializable`, `ObjectInputStream`, `ObjectStreamClass` và `ObjectInputFilter`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serialization-basics-007">NotSerializableException, InvalidClassException và StreamCorruptedException</a>

<details>
<summary>Xem chi tiết</summary>

# NotSerializableException, InvalidClassException và StreamCorruptedException trong Java Serialization

## 1. Mục tiêu bài học và bối cảnh

Khi dùng Java Object Serialization, ba ngoại lệ `NotSerializableException`, `InvalidClassException` và `StreamCorruptedException` thường cùng xuất hiện quanh thao tác `ObjectOutputStream.writeObject(...)` / `ObjectInputStream.readObject(...)`, nhưng chúng báo **ba loại vấn đề khác nhau**:

- **`NotSerializableException`:** không thể ghi một đối tượng trong đồ thị đối tượng vì lớp của nó không hỗ trợ Java Serialization.
- **`InvalidClassException`:** mô tả lớp trong luồng và lớp đang nạp không tương thích cho quá trình khôi phục đối tượng (điển hình là `serialVersionUID` không khớp).
- **`StreamCorruptedException`:** dữ liệu đọc vào không tuân theo định dạng Java Object Serialization hoặc chứa mã định dạng không hợp lệ.

Bài này tập trung vào Java Serialization chuẩn trong `java.io` trên **Java 17+**: nguyên nhân, thời điểm phát sinh, cách tái hiện, cách đọc thông báo lỗi, biện pháp sửa và phòng tránh. Đây là các ngoại lệ *checked* thuộc nhánh `IOException`; đừng dựa vào việc chúng cùng là `IOException` để xử lý như một lỗi duy nhất.

## 2. Cơ chế nền tảng cần hiểu

`ObjectOutputStream` ghi đối tượng Java thành một **object stream**, bao gồm dữ liệu nhận dạng lớp, thông tin phiên bản và dữ liệu trạng thái của đối tượng. `ObjectInputStream` diễn giải luồng đó và tái tạo đối tượng dựa trên lớp có trong JVM đang đọc.

Quá trình này có những điều kiện riêng:

1. **Tính serializable của cả đồ thị:** lớp muốn được tuần tự hóa thông thường triển khai marker interface `java.io.Serializable`. Khi ghi một đối tượng, Java còn duyệt các trường tham chiếu không `static`, không `transient` mà nó phải ghi. Nếu gặp đối tượng có lớp không serializable, thao tác thất bại dù lớp gốc đã triển khai `Serializable`.
2. **Tương thích lớp khi đọc:** mô tả lớp đã lưu được so với lớp cục bộ. `serialVersionUID` (kiểu `long`) là khóa kiểm tra phiên bản tiêu biểu, nhưng UID giống nhau không tự bảo đảm mọi thay đổi cấu trúc lớp là tương thích.
3. **Đúng định dạng luồng:** `ObjectInputStream` mong đợi dữ liệu do cơ chế object stream tạo ra. Ví dụ luồng thường bắt đầu bằng header byte `AC ED 00 05`; đưa file văn bản, JSON hoặc byte ngẫu nhiên vào `ObjectInputStream` không biến chúng thành đối tượng Java.
4. **Vòng đời tài nguyên:** luồng phải được đóng hoặc hoàn tất ghi trước khi đọc từ byte/file đã tạo. `try-with-resources` giúp đóng luồng khi thành công hoặc có lỗi.

`Serializable` không liên quan đến `java.beans`, JSON serialization hay việc chỉ cần lớp có getter/setter. Đây là một giao thức nhị phân dành riêng cho Java Object Serialization.

## 3. Bảng phân biệt nhanh

| Ngoại lệ | Thường xuất hiện khi | Ý nghĩa trọng tâm | Hướng kiểm tra đầu tiên |
| --- | --- | --- | --- |
| `NotSerializableException` | `ObjectOutputStream.writeObject`; cũng có thể trong custom serialization | Một đối tượng cần ghi không serializable | Tên lớp trong `getMessage()`; đường đi tới trường tham chiếu gây lỗi |
| `InvalidClassException` | `ObjectInputStream.readObject` hoặc trong cơ chế kiểm tra lớp khi tuần tự hóa | Định nghĩa/mô tả lớp không tương thích hoặc không hợp lệ | Tên lớp, `serialVersionUID` ở hai phía, lịch sử thay đổi lớp |
| `StreamCorruptedException` | Khởi tạo `ObjectInputStream` (header sai) hoặc lúc `readObject` | Các byte không đúng quy tắc định dạng object stream | Nguồn byte, header, việc ghép/trộn/ghi nối luồng |

**Lưu ý:** vị trí phát sinh ở bảng là tình huống thường gặp, không phải danh sách mọi đường đi nội bộ. Khi chẩn đoán, ưu tiên exception type, message, stack trace và thao tác I/O đang chạy.

## 4. `NotSerializableException` — có đối tượng không được phép ghi

### 4.1. Nguyên nhân và đối tượng gây lỗi

Java ném `java.io.NotSerializableException` nếu một đối tượng phải được ghi nhưng lớp của nó không triển khai `Serializable` hoặc `Externalizable` theo cách hợp lệ. Nguyên nhân phổ biến:

- Lớp gốc chưa triển khai `Serializable`.
- Lớp gốc triển khai `Serializable` nhưng một trường giữ đối tượng không serializable, ví dụ `Thread`, kết nối DB, stream, socket hay một `Object` thuần.
- Một collection serializable như `ArrayList` chứa phần tử không serializable; phải kiểm tra **phần tử thực tế**, không chỉ kiểu của collection.
- Custom `writeObject` / `writeExternal` chủ động ghi một đối tượng không serializable.

Các trường `static` không nằm trong trạng thái đối tượng được serialization mặc định ghi. Trường `transient` được bỏ qua theo cơ chế mặc định, nhưng nếu custom `writeObject` tự gọi `writeObject` trên trường đó thì lỗi vẫn có thể xảy ra.

### 4.2. Ví dụ tái hiện được trên Java 17+

Tạo file `NotSerializableDemo.java`:

~~~java
import java.io.*;

public class NotSerializableDemo {
    static final class Job implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String code;
        private final Object runtimeState = new Object();

        Job(String code) {
            this.code = code;
        }
    }

    public static void main(String[] args) throws IOException {
        try (var bytes = new ByteArrayOutputStream();
             var out = new ObjectOutputStream(bytes)) {
            out.writeObject(new Job("J-01"));
        } catch (NotSerializableException e) {
            System.out.println("NotSerializableException: " + e.getMessage());
        }
    }
}
~~~

Chạy:

~~~bash
javac NotSerializableDemo.java
java NotSerializableDemo
~~~

Kết quả:

~~~text
NotSerializableException: java.lang.Object
~~~

`Job` đã implements `Serializable`, nhưng trường `runtimeState` chứa đối tượng `java.lang.Object`, vốn không triển khai interface đó. Tên lớp trong message là **lớp của đối tượng gây lỗi**, không nhất thiết là lớp được truyền trực tiếp vào `writeObject`. `code` là `String` nên không gây lỗi.

### 4.3. Sửa đúng theo ý nghĩa dữ liệu

Nếu `runtimeState` là cache hoặc tài nguyên chỉ dùng lúc chạy, đánh dấu `transient` và tạo lại khi cần. Nếu dữ liệu đó *phải tồn tại sau khi khôi phục*, đừng chỉ đánh dấu `transient` để loại bỏ lỗi: hãy chuyển nó thành trạng thái có thể ghi, hoặc thiết kế DTO riêng chỉ chứa dữ liệu cần lưu.

Ví dụ file độc lập `TransientDemo.java`:

~~~java
import java.io.*;

public class TransientDemo {
    static final class Job implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String code;
        private transient Object runtimeState = new Object();

        Job(String code) {
            this.code = code;
        }
    }

    public static void main(String[] args) throws Exception {
        var bytes = new ByteArrayOutputStream();

        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(new Job("J-01"));
        }

        try (var in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            Job restored = (Job) in.readObject();
            System.out.println(restored.code);
            System.out.println(restored.runtimeState == null);
        }
    }
}
~~~

~~~bash
javac TransientDemo.java
java TransientDemo
~~~

~~~text
J-01
true
~~~

Trường `transient` nhận giá trị mặc định (`null` với kiểu tham chiếu) khi đối tượng serializable được khôi phục theo cơ chế mặc định; initializer `= new Object()` của chính lớp serializable **không chạy lại** trong quá trình đó. Nếu muốn tái tạo cache sau khi đọc, có thể bổ sung custom `readObject` phù hợp hoặc khởi tạo lười ở phương thức truy cập. Chỉ thêm `implements Serializable` cho mọi lớp gặp lỗi cũng không an toàn: nhiều tài nguyên runtime không có ý nghĩa khi lưu ra đĩa.

## 5. `InvalidClassException` — lớp trong stream và lớp hiện tại không tương thích

### 5.1. Vai trò của `serialVersionUID`

`java.io.InvalidClassException` thường gặp khi ứng dụng ghi đối tượng bằng một bản class, sau đó đọc bằng bản class khác có `serialVersionUID` không khớp. Khi class triển khai `Serializable`, nên khai báo UID cố định và có chủ đích:

~~~java
@Serial
private static final long serialVersionUID = 1L;
~~~

`@Serial` là annotation có từ Java 14, hữu ích cho compiler kiểm tra khai báo/điểm mở rộng serialization; nó không làm class tự serializable. Nếu không khai báo UID, JVM tính giá trị mặc định từ các đặc điểm của lớp; nhiều chỉnh sửa source có thể làm UID tự tính đổi, tạo lỗi khó đoán. UID cố định cho phép chủ động quản lý tương thích nhưng **không phải cơ chế chuyển đổi schema**.

Các nguyên nhân khác có thể là class không hợp lệ theo quy tắc serialization, ví dụ thiếu constructor không tham số có thể truy cập của lớp cha đầu tiên không serializable, hoặc thay đổi cấu trúc không tương thích trong lớp. Đừng khẳng định mọi `InvalidClassException` đều do UID.

### 5.2. Tái hiện lỗi bằng hai bản của cùng một class

Ví dụ này cần **hai lần biên dịch riêng biệt** vì hai file `SavedItem.java` mang cùng tên class nhưng khác UID. Tạo cấu trúc:

~~~text
demo/
  v1/SavedItem.java
  v1/WriteItem.java
  v2/SavedItem.java
  v2/ReadItem.java
~~~

Nội dung `v1/SavedItem.java`:

~~~java
import java.io.Serial;
import java.io.Serializable;

public class SavedItem implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;

    public SavedItem(String name) {
        this.name = name;
    }
}
~~~

Nội dung `v1/WriteItem.java`:

~~~java
import java.io.*;

public class WriteItem {
    public static void main(String[] args) throws Exception {
        try (var out = new ObjectOutputStream(
                new FileOutputStream("saved-item.ser"))) {
            out.writeObject(new SavedItem("J-01"));
        }
        System.out.println("Wrote v1");
    }
}
~~~

Nội dung `v2/SavedItem.java` (cùng tên class, UID đổi thành 2):

~~~java
import java.io.Serial;
import java.io.Serializable;

public class SavedItem implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    private final String name;

    public SavedItem(String name) {
        this.name = name;
    }
}
~~~

Nội dung `v2/ReadItem.java`:

~~~java
import java.io.*;

public class ReadItem {
    public static void main(String[] args) throws Exception {
        try (var in = new ObjectInputStream(
                new FileInputStream("saved-item.ser"))) {
            in.readObject();
            System.out.println("Read OK");
        } catch (InvalidClassException e) {
            System.out.println("InvalidClassException");
        }
    }
}
~~~

Từ thư mục `demo` chứa sẵn `v1` và `v2`, chạy lần lượt:

~~~bash
javac -d v1 v1/SavedItem.java v1/WriteItem.java
java -cp v1 WriteItem
javac -d v2 v2/SavedItem.java v2/ReadItem.java
java -cp v2 ReadItem
~~~

Kết quả:

~~~text
Wrote v1
InvalidClassException
~~~

Bản v1 ghi mô tả `SavedItem` có UID `1L`. Bản v2 đang nạp class cùng tên với UID `2L`, nên `readObject` không thể khôi phục đối tượng. Nếu in `e.getMessage()`, thông báo thường cho biết tên class và hai giá trị `serialVersionUID`; không nên phụ thuộc vào toàn bộ câu chữ message giữa các bản JDK.

### 5.3. Xử lý đúng khi class tiến hóa

1. Xác định **bản ứng dụng nào đã ghi** dữ liệu và **bản nào đang đọc**; lưu lại stack trace và message.
2. Kiểm tra `serialVersionUID`, class name/package, thay đổi các trường và class hierarchy. Công cụ `serialver -classpath ... TenClass` có thể tra cứu UID của class đã biên dịch, nhưng đối chiếu với UID trong dữ liệu/phiên bản đã triển khai mới là điều cần thiết.
3. Với các thay đổi tương thích (ví dụ thêm trường thông thường), giữ UID chủ đích nếu đã xác minh hợp đồng dữ liệu; trường mới thường nhận giá trị mặc định khi đọc dữ liệu cũ.
4. Với thay đổi không tương thích hoặc có biến đổi ngữ nghĩa, tạo **quy trình migration**, đọc bằng phiên bản cũ rồi xuất định dạng mới, hoặc duy trì reader cho dữ liệu cũ. Không tùy tiện sửa UID của v2 về 1 chỉ để tắt exception.
5. Nếu phải duy trì dữ liệu lâu dài hoặc trao đổi giữa hệ thống, cân nhắc định dạng có schema/version và migration rõ ràng thay cho serialization nhị phân của Java.

UID bằng nhau **không** đảm bảo dữ liệu hợp lệ về mặt nghiệp vụ. Ví dụ chuyển tên thuộc tính sang ý nghĩa khác có thể dẫn đến giá trị mặc định hoặc dữ liệu sai mà không ném lỗi. Ngược lại, không phải mọi thay đổi trường đều bất tương thích; quy tắc tương thích serialization cụ thể quyết định.

## 6. `StreamCorruptedException` — byte stream không đúng định dạng

### 6.1. Nguyên nhân phổ biến

`java.io.StreamCorruptedException` báo rằng object stream có phần điều khiển/định dạng không hợp lệ. Một số tình huống:

- Tạo `ObjectInputStream` trên văn bản UTF-8, JSON, CSV, ảnh hoặc dữ liệu từ `DataOutputStream` thay vì luồng đã ghi bằng `ObjectOutputStream`.
- Bỏ sót hoặc đọc sai byte đầu luồng; truyền vào dữ liệu bắt đầu từ một offset không đúng.
- Trộn việc ghi `ObjectOutputStream` với ghi byte thủ công vào **cùng protocol** mà phía đọc không biết cách xử lý.
- Nối tiếp nhiều lần ghi bằng **những `ObjectOutputStream` mới** vào cùng file; mỗi lần tạo object stream mới viết thêm header, khiến một `ObjectInputStream` đang đọc liên tục có thể gặp header giữa luồng như một mã dữ liệu bất hợp lệ.
- Dữ liệu bị thay đổi/hỏng ở phần cấu trúc định dạng.

Không có quy tắc “file hỏng luôn là `StreamCorruptedException`”: file rỗng/thiếu byte có thể gây `EOFException`; luồng sai định dạng hoặc bị cắt ở vị trí khác có thể cho những ngoại lệ khác.

### 6.2. Ví dụ sai header, kết quả ổn định

Tạo file `StreamCorruptedDemo.java`:

~~~java
import java.io.*;

public class StreamCorruptedDemo {
    public static void main(String[] args) throws Exception {
        byte[] notAnObjectStream = {0x00, 0x01, 0x02, 0x03};

        try (var in = new ObjectInputStream(
                new ByteArrayInputStream(notAnObjectStream))) {
            in.readObject();
        } catch (StreamCorruptedException e) {
            System.out.println("StreamCorruptedException");
        }
    }
}
~~~

~~~bash
javac StreamCorruptedDemo.java
java StreamCorruptedDemo
~~~

~~~text
StreamCorruptedException
~~~

Lỗi phát sinh **ngay trong constructor `ObjectInputStream`** khi đọc header, trước cả lệnh `readObject()`. Nếu in message, trường hợp trên thường có `invalid stream header: 00010203`; mã hex hữu ích để nhận biết loại dữ liệu thực sự.

### 6.3. Ghi và đọc đúng cặp luồng

Một cặp tuần tự hóa tối thiểu, file `RoundTripDemo.java`:

~~~java
import java.io.*;

public class RoundTripDemo {
    public static void main(String[] args) throws Exception {
        var bytes = new ByteArrayOutputStream();

        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject("hello");
        }

        try (var in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            String value = (String) in.readObject();
            System.out.println(value);
        }
    }
}
~~~

~~~bash
javac RoundTripDemo.java
java RoundTripDemo
~~~

~~~text
hello
~~~

Phía ghi và đọc phải thống nhất thứ tự đối tượng, ranh giới thông điệp và cách mở/đóng luồng. Khi muốn ghi nhiều đối tượng trong một file, dùng **một `ObjectOutputStream` cho cả phiên ghi** và một `ObjectInputStream` tương ứng cho phiên đọc, với số lượng/ranh giới bản ghi được xác định rõ. `flush()` đẩy dữ liệu đệm xuống lớp dưới; nó không chữa lỗi định dạng hoặc việc ghi nhiều header.

Nếu giao thức có tiền tố là độ dài, version hay byte metadata tự định nghĩa, cần tách/đọc đúng phần đó trước khi tạo `ObjectInputStream` trên **đúng đoạn payload object stream**; không thể dùng `readObject()` để đọc byte tùy ý.

## 7. Tình huống dễ nhầm với ba ngoại lệ này

| Triệu chứng | Có thể là gì | Cách xác minh |
| --- | --- | --- |
| `writeObject(list)` báo `NotSerializableException: com.example.X` | Một phần tử hoặc trường con kiểu `X` không serializable | Dò object graph từ list xuống các phần tử và trường |
| `readObject()` báo `InvalidClassException` sau khi nâng phiên bản | Class đã đổi UID hoặc có thay đổi không tương thích | So bản class ghi/đọc và UID; thử đọc bằng bản cũ |
| `new ObjectInputStream(...)` báo `StreamCorruptedException` | Header sai hoặc đọc nhầm định dạng/offset | Kiểm tra nguồn, 4 byte đầu và code tạo file |
| `readObject()` báo `EOFException` | Kết thúc luồng, thiếu dữ liệu hoặc số lần đọc vượt số lần ghi | Đếm bản ghi, kiểm tra kích thước file và vòng lặp đọc |
| `readObject()` báo `ClassNotFoundException` | Class được ghi không có trong classpath/class loader phía đọc | Kiểm tra tên class/package/JAR và môi trường triển khai |
| `readObject()` báo `OptionalDataException` | Reader yêu cầu object tại vị trí đang có primitive data trong protocol | So thứ tự `writeObject`, `writeInt` và các lệnh đọc tương ứng |
| `readObject()` báo `WriteAbortedException` | Bên ghi từng abort quá trình ghi object sau lỗi và stream có dấu vết đó | Xem `getCause()` và log lỗi gốc bên ghi |

`ClassNotFoundException` không phải một trong ba loại chính và thuộc nhánh ngoại lệ khác `IOException`. `IOException`/`EOFException` mang thông tin riêng; không nên đổi mọi lỗi đọc dữ liệu thành `StreamCorruptedException` trong catch.

## 8. Quy trình chẩn đoán và sửa lỗi theo thứ tự

1. **Xác định thao tác thất bại.** Ghi (`writeObject`), tạo reader (`new ObjectInputStream`), hay đọc (`readObject`)? Ghi stack trace đầy đủ và message, bao gồm lỗi gốc nếu có `cause`.
2. **Đối với `NotSerializableException`:** lấy tên class gây lỗi từ message, tìm trường/collection giữ instance của class đó. Phân loại dữ liệu cần lưu và tài nguyên runtime. Chuyển thành DTO serializable, thay đổi cấu trúc hoặc đánh dấu `transient` với kế hoạch tái tạo dữ liệu phù hợp.
3. **Đối với `InvalidClassException`:** xác định class từ stream, class đang nạp và UID hai bên. Kiểm tra tương thích schema, constructor của lớp cha không serializable và các thay đổi class. Thực hiện migration khi cần.
4. **Đối với `StreamCorruptedException`:** xác minh bytes nguồn có thực sự do `ObjectOutputStream` sinh ra, không bị trộn text/JSON/metadata hoặc append header lặp. Đọc/ghi vào đúng vị trí và đúng protocol.
5. **Thử trên dữ liệu tối thiểu có thể lặp lại.** Tạo test ghi → đọc với **cùng phiên bản** để kiểm tra code cơ bản, rồi dùng dữ liệu/phiên bản gây lỗi để phân biệt lỗi protocol và lỗi tương thích.
6. **Sửa nguyên nhân và thêm regression test.** Lưu fixture stream của một phiên bản cũ nếu vẫn cam kết đọc được; kiểm thử object graph có collection, null, transient, dữ liệu thiếu và luồng không hợp lệ.

Khi xử lý trong ứng dụng thật, thường nên bắt ngoại lệ ở ranh giới I/O để log đủ ngữ cảnh và trả lỗi có ý nghĩa. Không nuốt exception rồi xem đối tượng khôi phục thất bại như dữ liệu thành công; cũng không xóa file serialized ngay khi lần đầu đọc lỗi nếu chưa xác định nguyên nhân và chính sách lưu trữ.

## 9. Ngăn lỗi từ khâu thiết kế

- **Thu gọn dữ liệu lưu:** tách model trao đổi/lưu trữ khỏi entity có socket, executor, thread, connection, logger, cache; serializable không đồng nghĩa phù hợp để lưu lâu dài.
- **Quản lý phiên bản:** khai báo `serialVersionUID` có chủ đích cho class serializable, kiểm thử đọc dữ liệu cũ khi phát hành bản mới và thiết kế migration khi thay đổi bất tương thích.
- **Thống nhất protocol:** xác định rõ phiên ghi/đọc, thứ tự dữ liệu, cấu trúc thông điệp và quản lý buffer/file. Đừng dùng serialization stream như một container chung cho dữ liệu bất kỳ.
- **Quan sát lỗi:** ghi tên class, version ứng dụng, vị trí I/O, tên file/tên message và cause. Với dữ liệu nhạy cảm, không dump nguyên object/stream vào log.
- **An toàn:** Java deserialization của dữ liệu không tin cậy có thể nguy hiểm vì hành vi trong quá trình tái tạo object graph. Tránh `ObjectInputStream` trên input không tin cậy; khi bắt buộc dùng, cân nhắc `ObjectInputFilter` theo JEP 290 cùng giới hạn class, độ sâu, số tham chiếu/kích thước. Bộ lọc **không thay thế** kiểm soát nguồn dữ liệu và thiết kế protocol an toàn.

## 10. Tự kiểm tra

**Câu 1.** Class `Order` triển khai `Serializable` và chứa `List<PaymentConnector>`. `ArrayList` có thể serialize nhưng `PaymentConnector` không triển khai `Serializable`. Ghi một list có phần tử loại đó sẽ ra sao?

**Đáp án:** Có khả năng ném `NotSerializableException` khi Java đi đến phần tử `PaymentConnector`. Phải sửa dữ liệu đồ thị được ghi, không chỉ kiểu khai báo của field list.

**Câu 2.** Một class thêm field `int retries` và vẫn giữ UID. Dữ liệu cũ chắc chắn gây `InvalidClassException`?

**Đáp án:** Không. Thêm field thông thường thường là thay đổi tương thích; khi đọc dữ liệu cũ, trường mới có thể nhận giá trị mặc định `0`. Vẫn phải kiểm thử logic nghiệp vụ và các điều kiện tương thích liên quan.

**Câu 3.** Đọc file JSON qua `ObjectInputStream` báo lỗi header. Có thể sửa bằng cách ép class JSON implements `Serializable`?

**Đáp án:** Không. Dạng byte JSON không phải Java Object Serialization stream. Dùng JSON parser cho JSON, hoặc đổi bên ghi sang `ObjectOutputStream` và thống nhất định dạng ở cả hai phía.

**Câu 4.** Tại sao một file chỉ có một nửa dữ liệu không nhất thiết ném `StreamCorruptedException`?

**Đáp án:** Nếu reader cần thêm byte nhưng đã tới cuối file, `EOFException` có thể là tín hiệu trực tiếp. Ngoại lệ cụ thể phụ thuộc vị trí dữ liệu thiếu và giai đoạn giải mã.

## 11. Ghi nhớ

- `NotSerializableException` → **vấn đề object graph khi ghi**: kiểm tra lớp thật và các trường/phần tử cần serialize.
- `InvalidClassException` → **vấn đề tương thích/định nghĩa class**: kiểm tra UID, phiên bản class và lịch sử tiến hóa dữ liệu.
- `StreamCorruptedException` → **vấn đề cấu trúc object stream**: kiểm tra nguồn byte, header, offset và cách phối hợp writer/reader.

Bắt đúng loại ngoại lệ giúp khoanh vùng lỗi nhanh, nhưng giải pháp đúng phải dựa vào dữ liệu cần lưu, hợp đồng tương thích và định dạng luồng đã thiết kế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serialization-basics-008">Constructor của lớp Serializable và lớp cha không Serializable</a>

<details>
<summary>Xem chi tiết</summary>

# Constructor của lớp Serializable và lớp cha không Serializable

## 1. Câu hỏi cốt lõi

Khi gọi `new` để tạo một đối tượng Java, các constructor của những lớp trong chuỗi kế thừa được chạy theo quy tắc khởi tạo đối tượng thông thường. Nhưng khi dùng **Java Object Serialization** với `ObjectInputStream.readObject()` để khôi phục một đối tượng có lớp triển khai `java.io.Serializable`, quy tắc constructor **khác đáng kể**.

**Quy tắc phải nhớ:** Trong quá trình *deserialization* mặc định của một lớp `Serializable` thông thường, **constructor của chính lớp `Serializable` và của các lớp cha cũng `Serializable` không được gọi**. Thay vào đó, Java **gọi constructor không tham số, có thể truy cập được, của lớp cha gần nhất không triển khai `Serializable`**. Từ constructor đó, các constructor của những tổ tiên không `Serializable` tiếp tục được thực thi theo quy tắc `super()` bình thường.

Ví dụ:

```text
Object                 (không Serializable)
   |
   v
Base                   (không Serializable)  <-- no-arg constructor được gọi
   |
   v
Account                (implements Serializable) <-- constructor KHÔNG chạy
   |
   v
PremiumAccount         (kế thừa Serializable) <-- constructor KHÔNG chạy

new PremiumAccount()                -> Object -> Base -> Account -> PremiumAccount
ObjectInputStream.readObject()      -> Object -> Base
                                   -> phục hồi field của Account/PremiumAccount
```

Sơ đồ được giản lược để mô tả constructor của đối tượng đang được khôi phục; quá trình thật còn giải quyết descriptor, handles, references, chuỗi đối tượng và hook serialization.

Đặc biệt, **không được nói “deserialization không chạy constructor nào”**. Đúng hơn: nó **không chạy constructor của các lớp thuộc chuỗi kế thừa `Serializable`**, nhưng **vẫn chạy constructor không tham số của lớp cha không `Serializable` gần nhất** và constructor tổ tiên mà constructor đó gọi.

Đây là chủ đề quyết định việc field nào được khôi phục, field nào có giá trị khởi tạo mới và tại sao việc thiếu constructor phù hợp ở lớp cha có thể làm `readObject()` thất bại.

## 2. `Serializable` chỉ là marker interface

`java.io.Serializable` là **marker interface**: không khai báo phương thức bắt buộc phải hiện thực. Một class trực tiếp ghi `implements Serializable` hoặc **kế thừa** từ class đã triển khai interface này đều thuộc nhóm serializable theo cơ chế mặc định, miễn những điều kiện serialization khác được đáp ứng.

```java
import java.io.Serializable;

class Parent {
    Parent() { }
}

class Child extends Parent implements Serializable {
    private static final long serialVersionUID = 1L;

    Child() {
        super();
    }
}
```

Đây là đoạn mã minh họa, không có `main`. `Parent` **không** cần implements `Serializable` để `Child` trở thành Serializable. Nhưng việc không Serializable của `Parent` có hệ quả:

- **Field khai báo tại `Parent` không được cơ chế Java serialization mặc định lưu và khôi phục tự động**.
- Khi deserialization một `Child`, Java gọi **no-arg constructor hợp lệ của `Parent`**.
- Constructor của `Child` không được gọi; field Serializable của `Child` được khôi phục từ stream thay vì khởi tạo qua constructor thông thường.

Lưu ý: `Serializable` được kế thừa như một khả năng của class. Nếu `GrandParent implements Serializable`, `Parent extends GrandParent` và `Child extends Parent`, cả `Parent` và `Child` đều là Serializable ngay cả khi chúng không lặp lại chữ `implements Serializable`.

## 3. Vì sao constructor xử lý khác với `new`?

### 3.1. Tạo đối tượng bằng `new`

Trong Java thông thường:

```java
Child child = new Child();
```

Trình tự constructor về nguyên tắc là:

1. Khởi tạo object theo cơ chế JVM.
2. Constructor của lớp trên cùng không có cha rõ ràng, chẳng hạn `Object()`, chạy theo chuỗi `super`.
3. Constructor của các lớp cha chạy từ trên xuống.
4. Constructor của class cụ thể `Child` chạy; field initializers và instance initializer blocks được thực hiện theo thứ tự khởi tạo Java.

Nếu field hoặc constructor của `Child` gọi `super(args)`, quy tắc trình tự tương ứng được áp dụng.

### 3.2. Khôi phục bằng `readObject()`

Khi:

```java
Child restored = (Child) objectInput.readObject();
```

cơ chế serialization cần tái tạo **trạng thái đã được lưu** của phần serializable, chứ không “gọi `new Child()` rồi gán vài field” theo cách ứng dụng bình thường.

Vì vậy:

- Không thực thi constructor `Child`.
- Không thực thi **instance field initializer** hoặc **instance initializer block** của những class nằm trong phần Serializable của chuỗi kế thừa.
- Không thực thi constructor của các superclass Serializable nằm giữa.
- Có thực thi constructor không tham số được chọn ở lớp cha gần nhất **không** Serializable; constructor ấy có thể gọi tiếp superclass non-serializable của nó.
- Sau đó các field thuộc những class serializable được phục hồi từ dữ liệu stream theo cơ chế Object Serialization.

**Quan trọng:** static initialization là cơ chế riêng của JVM. Khi class lần đầu được khởi tạo, static initializer có thể chạy theo quy tắc khởi tạo class; không được nhầm điều đó với việc constructor instance được gọi cho mỗi đối tượng deserialize.

## 4. Ví dụ Java 17: constructor nào chạy và field nào được giữ?

Chương trình chạy độc lập sau sử dụng lớp cha **không Serializable** và lớp con **Serializable**.

Ở lúc tạo mới, lớp cha đặt `baseValue = 10`; constructor lớp con ghi đè thành 99 và đặt `childValue = 77`. Sau khi ghi rồi đọc đối tượng bằng stream, constructor lớp cha lại chạy và đặt `baseValue = 10`, nhưng constructor lớp con không chạy. Vì `baseValue` là field của lớp cha không Serializable nên giá trị 99 **không được tự khôi phục**; `childValue` là field của lớp con Serializable và được khôi phục thành 77.

**Tệp `SerializableConstructorDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SerializableConstructorDemo {
    static class Base {
        int baseValue;

        Base() {
            System.out.println("Base constructor");
            baseValue = 10;
        }
    }

    static class Child extends Base implements Serializable {
        private static final long serialVersionUID = 1L;

        int childValue;

        Child() {
            // super() được compiler thêm mặc định.
            System.out.println("Child constructor");
            baseValue = 99;
            childValue = 77;
        }
    }

    public static void main(String[] args)
            throws IOException, ClassNotFoundException {

        Child original = new Child();
        System.out.println("Before: base=" + original.baseValue
                + ", child=" + original.childValue);

        ByteArrayOutputStream memory = new ByteArrayOutputStream();
        try (ObjectOutputStream output =
                     new ObjectOutputStream(memory)) {
            output.writeObject(original);
        }

        Child restored;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(memory.toByteArray()))) {
            restored = (Child) input.readObject();
        }

        System.out.println("After: base=" + restored.baseValue
                + ", child=" + restored.childValue);
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac SerializableConstructorDemo.java
java SerializableConstructorDemo
```

**Stdout chính xác khi chương trình thành công:**

```text
Base constructor
Child constructor
Before: base=99, child=77
Base constructor
After: base=10, child=77
```

**Phân tích:**

1. `new Child()`: chạy `Base()` rồi `Child()`, nên `baseValue=99` và `childValue=77`.
2. `ObjectOutputStream.writeObject(original)`: lưu state Serializable của `Child`. Nó không tự lưu `Base.baseValue` trong cơ chế mặc định vì `Base` không Serializable.
3. `ObjectInputStream.readObject()`: JVM tạo một instance của `Child` theo cơ chế phục hồi, **không gọi `Child()`**.
4. `Base()` được gọi vì `Base` là superclass không Serializable gần nhất có constructor không tham số hợp lệ, nên `baseValue=10`.
5. `childValue` thuộc phần Serializable và được khôi phục từ stream thành `77`.

Đây là cách kiểm chứng thực nghiệm rõ nhất: lần thứ hai output có dòng **`Base constructor`** nhưng **không có dòng `Child constructor`**.

## 5. Nếu có nhiều lớp Serializable kế thừa nhau thì sao?

Giả sử:

```text
PlainRoot              không Serializable
   |
   +-- SerialParent    implements Serializable
          |
          +-- SerialChild  extends SerialParent
```

Khi khôi phục một `SerialChild`:

- Constructor `PlainRoot()` được chạy.
- Constructor `SerialParent()` **không chạy**.
- Constructor `SerialChild()` **không chạy**.
- Các field Serializable của **cả `SerialParent` lẫn `SerialChild`** được khôi phục từ stream, trừ những field không được serialization mặc định ghi (như `transient`).

Các lớp dưới `SerialParent` tự động Serializable do kế thừa. Việc chúng có constructor rỗng hay không **không quyết định** cách gọi constructor khi deserialization mặc định.

### Ví dụ Java 17: ba cấp kế thừa

**Tệp `SerializationHierarchyDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SerializationHierarchyDemo {
    static class PlainRoot {
        int rootNumber;

        PlainRoot() {
            System.out.println("PlainRoot()");
            rootNumber = 1;
        }
    }

    static class SerialParent extends PlainRoot
            implements Serializable {
        private static final long serialVersionUID = 1L;

        int parentNumber;

        SerialParent() {
            System.out.println("SerialParent()");
            rootNumber = 99;
            parentNumber = 2;
        }
    }

    static class SerialChild extends SerialParent {
        private static final long serialVersionUID = 1L;

        int childNumber;

        SerialChild() {
            System.out.println("SerialChild()");
            childNumber = 3;
        }
    }

    public static void main(String[] args) throws Exception {
        SerialChild before = new SerialChild();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output =
                     new ObjectOutputStream(bytes)) {
            output.writeObject(before);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            SerialChild after = (SerialChild) input.readObject();
            System.out.println("Values: " + after.rootNumber
                    + ", " + after.parentNumber
                    + ", " + after.childNumber);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac SerializationHierarchyDemo.java
java SerializationHierarchyDemo
```

**Stdout chính xác nếu I/O thành công:**

```text
PlainRoot()
SerialParent()
SerialChild()
PlainRoot()
Values: 1, 2, 3
```

**Tại sao root=1 thay vì 99?** `rootNumber` là field khai báo trên lớp `PlainRoot` không Serializable. Khi tạo instance `new`, constructor `SerialParent` đổi thành 99; nhưng quá trình deserialize không gọi `SerialParent()`, mà gọi `PlainRoot()` đặt lại 1. Hai field Serializable `parentNumber` và `childNumber` được phục hồi thành 2 và 3 từ stream.

## 6. Constructor cần tồn tại ở **superclass đầu tiên không Serializable**

Giả sử `Child implements Serializable` và `Base` là superclass trực tiếp không Serializable. `ObjectInputStream` yêu cầu **constructor không tham số hợp lệ** của `Base` để khôi phục đối tượng.

### 6.1. Chấp nhận được

```java
class Base {
    protected Base() {
        // Thiết lập trạng thái nền tảng.
    }
}

class Child extends Base implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    Child(int value) {
        super();
    }
}
```

Vì `Base()` là `protected`, constructor đó có thể được lớp con truy cập theo quy tắc Java. Constructor `Child(int)` có tham số hay không **không quan trọng đối với việc constructor Child được gọi lúc deserialize**, vì constructor ấy không được dùng trong cơ chế mặc định.

### 6.2. Không có constructor không tham số

```java
class Base {
    Base(int value) {
        // Không khai báo Base() không tham số.
    }
}

class Child extends Base implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    Child() {
        super(42);
    }
}
```

**Code này có thể biên dịch**, vì constructor `Child()` chủ động gọi `super(42)` khi tạo mới. Nhưng `Base` **không có no-arg constructor**, nên deserialization một `Child` thông thường không thể thực hiện đúng bước khởi tạo superclass và sẽ thất bại với `InvalidClassException` báo không có constructor hợp lệ.

Điều đó không chỉ là lỗi lúc compile. **Việc `new Child()` và `writeObject(child)` thành công không chứng minh `readObject()` sẽ thành công.**

### 6.3. Constructor riêng tư hoặc không truy cập được

Constructor của superclass không Serializable được chọn cần **accessible to the subclass that is Serializable** theo đặc tả Java Object Serialization.

- `public` hoặc `protected` no-arg constructor nhìn chung là lựa chọn rõ ràng.
- No-arg constructor *package-private* chỉ dùng được trong bối cảnh truy cập phù hợp (ví dụ cùng package).
- Constructor `private` không thể được lớp con thường truy cập; việc có constructor rỗng private **không** giải quyết yêu cầu.
- Các rào cản module/access giữa package và module có thể tạo vấn đề quyền truy cập trong thiết kế phức tạp.

**Không nên lược giản thành “phải có public no-arg constructor”.** Với `Serializable` thông thường, `protected` hoặc no-arg constructor có khả năng truy cập theo ngữ cảnh cũng có thể hợp lệ; yêu cầu **public** áp dụng nổi bật cho `Externalizable`, một cơ chế khác được so sánh phía dưới.

## 7. Ví dụ Java 17: thiếu no-arg constructor dẫn đến InvalidClassException

Ví dụ này cố ý tạo một class có superclass chỉ có constructor một tham số. Nó chứng minh:

- Constructor lớp con dùng để tạo mới có thể hoạt động.
- Serialization bằng `writeObject` có thể hoàn thành.
- **Deserialization thất bại** vì superclass không cung cấp no-arg constructor hợp lệ.

**Tệp `MissingSuperclassConstructorDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class MissingSuperclassConstructorDemo {
    static class Base {
        int value;

        Base(int value) {
            this.value = value;
        }
    }

    static class Child extends Base implements Serializable {
        private static final long serialVersionUID = 1L;

        Child() {
            super(42);
        }
    }

    public static void main(String[] args) throws Exception {
        Child original = new Child();
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(sink)) {
            output.writeObject(original);
        }

        System.out.println("Write succeeded");

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(sink.toByteArray()))) {
            input.readObject();
            System.out.println("Read succeeded");
        } catch (InvalidClassException ex) {
            System.out.println("Read failed: "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac MissingSuperclassConstructorDemo.java
java MissingSuperclassConstructorDemo
```

**Stdout chính xác khi lỗi như mong đợi xảy ra:**

```text
Write succeeded
Read failed: InvalidClassException
```

Thông điệp chi tiết của `InvalidClassException` có thể khác nhau giữa các JDK/triển khai. Vì vậy chương trình chỉ in **tên loại ngoại lệ**, không giả định một message cố định.

Cách sửa phù hợp nếu có quyền chỉnh lớp cha:

```java
class Base {
    int value;

    protected Base() {
        value = 0;
    }

    Base(int value) {
        this.value = value;
    }
}
```

Đoạn này là phần minh họa thay thế định nghĩa `Base`, không chạy riêng. Nếu deserialize `Child`, giá trị `Base.value` được khởi tạo theo `Base()`, **không tự lấy lại giá trị 42** từng được đặt lúc tạo mới. Nếu cần phục hồi trạng thái superclass nonserializable, phải bổ sung cách lưu/khôi phục tường minh theo hợp đồng serialization.

## 8. Phân biệt **constructor lớp cha được gọi** và **field được serialize**

Đây là hai chuyện khác nhau:

| Field khai báo ở đâu? | Constructor phần class đó khi deserialize? | Field được khôi phục tự động? |
| --- | --- | --- |
| Class cha đầu tiên **không Serializable** | **Có**, gọi no-arg constructor | **Không**, không mặc định lưu field của superclass này |
| Tổ tiên không Serializable phía trên | Có theo chuỗi `super()` của constructor được gọi | **Không** theo cơ chế mặc định |
| Class đầu tiên `implements Serializable` | **Không** | Có đối với field instance thuộc dữ liệu serializable, trừ ngoại lệ như `transient` |
| Class con của một class Serializable | **Không** | Có đối với field instance phù hợp |
| `static` field | Không phải field của từng instance | **Không** lưu như instance state |
| `transient` field | Field initializer của Serializable không chạy | **Không** tự lưu/khôi phục; nhận mặc định trừ custom logic |

Những field **không được lưu** theo cơ chế mặc định không tự biết trạng thái trước đó. `Base()` có thể thiết lập lại field của superclass nonserializable, hoặc nếu không thiết lập thì chúng nhận giá trị mặc định theo quy tắc JVM/constructor chain.

Một nuance: field của lớp nonserializable có thể là đối tượng chứa socket, file descriptor hoặc state runtime. Ngay cả khi có cách serialize một phần giá trị của nó, việc khôi phục kết nối bên ngoài **không** đồng nghĩa deserialize một object có thể tự tái lập tài nguyên đó. Khi thiết kế object graph, nên mô hình hóa rõ phần trạng thái bền vững và phần tài nguyên sống.

## 9. Instance field initializers và initializer blocks có chạy lại không?

**Không đối với phần serializable của đối tượng được deserialize theo cơ chế mặc định.** Đây là điểm khiến giá trị field trông “bất ngờ”.

Ví dụ minh họa:

```java
class Base {
    int parentCounter = 100;

    Base() {
        parentCounter++;
    }
}

class Child extends Base implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    int number = 10;
    transient int cache = 20;

    {
        number += 5;
        cache += 5;
    }

    Child() {
        number += 1;
    }
}
```

Khi `new Child()`:

- `Base` field initializer đặt `parentCounter=100`, constructor tăng thành 101.
- `Child.number` bắt đầu 10, initializer block tăng thành 15, constructor tăng thành 16.
- `cache` bắt đầu 20, initializer block tăng thành 25.

Nếu serialize rồi deserialize với cơ chế mặc định:

- `Base()` chạy lại; `parentCounter` lại là **101**.
- `Child` instance initializers và constructor **không** chạy.
- `number` khôi phục giá trị đã lưu, ví dụ **16**.
- `cache` là `transient` nên được khôi phục về **0** (giá trị mặc định của `int`) nếu không có custom deserialization xử lý thêm.

**Cẩn thận:** ví dụ này là đoạn class minh họa, không có `main`. Mọi giả định về giá trị cache cần xét thêm `readObject` custom nếu lớp có triển khai hook này.

## 10. Ví dụ Java 17: transient và initializer không được gọi lại

**Tệp `TransientInitializationDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class TransientInitializationDemo {
    static class Base {
        int parentCounter = 100;

        Base() {
            parentCounter++;
        }
    }

    static class Child extends Base implements Serializable {
        private static final long serialVersionUID = 1L;

        int number = 10;
        transient int cache = 20;

        {
            number += 5;
            cache += 5;
        }

        Child() {
            number += 1;
        }
    }

    public static void main(String[] args) throws Exception {
        Child before = new Child();

        System.out.println("Before: parent="
                + before.parentCounter + ", number="
                + before.number + ", cache=" + before.cache);

        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(sink)) {
            out.writeObject(before);
        }

        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(sink.toByteArray()))) {
            Child after = (Child) in.readObject();

            System.out.println("After: parent="
                    + after.parentCounter + ", number="
                    + after.number + ", cache=" + after.cache);
        }
    }
}
```

Chạy:

```bash
javac TransientInitializationDemo.java
java TransientInitializationDemo
```

**Stdout chính xác khi thao tác thành công:**

```text
Before: parent=101, number=16, cache=25
After: parent=101, number=16, cache=0
```

`parentCounter` không tự được khôi phục từ stream nhưng no-arg constructor `Base()` thiết lập thành 101 ở cả hai thời điểm. `number` được khôi phục từ stream, còn `cache` mặc định về 0. Vì initializer của `Child` không chạy lại lúc deserialization, nó không được đặt lại thành 20 hay 25.

Trong ứng dụng thực, trường `transient` đôi khi cần được khởi tạo lại theo logic của ứng dụng sau deserialize, ví dụ dùng `readObject` custom để tính cache từ các field đã khôi phục (kèm kiểm tra invariants). Không nên mong constructor Serializable tự làm việc đó.

## 11. Khi superclass không Serializable có constructor tác dụng phụ

Constructor của lớp cha có thể chứa hành vi ngoài việc đặt field:

```java
class ResourceBase {
    protected ResourceBase() {
        // Có thể mở tài nguyên, tăng metric, đăng ký listener...
    }
}
```

Vì constructor này **chạy khi deserialization**, tác dụng phụ có thể xảy ra **một lần nữa cho mỗi object thực sự được khôi phục**. Hệ quả cần xét:

- Constructor có thể tạo tài nguyên mới, khác tài nguyên đã tồn tại khi object được serialize.
- Constructor có thể đăng ký vào singleton, listener hoặc hệ thống giám sát.
- Nếu constructor phụ thuộc môi trường hiện tại (clock, cấu hình, OS, quyền), trạng thái cha có thể khác thời điểm ghi stream.
- Constructor ném ngoại lệ có thể làm quá trình deserialization thất bại hoặc bị bao gói thành lỗi I/O/stream theo cơ chế khôi phục.

Không nên thiết kế constructor superclass có tác dụng phụ nặng mà không hiểu nó có thể được chạy trong một quy trình deserialize. Mặt khác, **constructor của lớp Serializable không chạy** nên tuyệt đối không thể dùng constructor đó làm nơi duy nhất bảo đảm kiểm tra invariants cho dữ liệu lấy từ stream.

### Điểm nguy hiểm: constructor của cha gọi phương thức có thể override

Giả sử:

```java
class Base {
    Base() {
        init();
    }

    void init() {
        // logic nền
    }
}

class Child extends Base implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private String name;

    @Override
    void init() {
        // Có thể quan sát name khi nó chưa được khôi phục.
    }
}
```

Đây là đoạn class minh họa. Gọi phương thức có thể override từ constructor superclass có thể đi vào implementation của subclass **trước khi state serializable được phục hồi**. Khi đó `name` có thể vẫn là `null` dù stream lưu tên khác null.

Đây cũng là vấn đề khởi tạo object thông thường trong Java khi superclass constructor gọi virtual method, nhưng với deserialization sự khác biệt càng dễ bị bỏ sót vì constructor của subclass không chạy và dữ liệu được phục hồi sau bước khởi tạo superclass.

**Khuyến nghị:** tránh gọi những phương thức có thể override từ constructor nền tảng, nhất là khi chúng phụ thuộc field của lớp con; nếu cần kiểm tra consistency sau deserialization, dùng hook phù hợp và validate sau khi khôi phục trạng thái.

## 12. Nếu cần khôi phục field của superclass không Serializable thì làm thế nào?

Cơ chế mặc định **không tự lưu** các field khai báo ở superclass không Serializable. Nếu ứng dụng cần giữ một số giá trị, class Serializable có thể tùy chỉnh `writeObject` và `readObject` của **chính nó** để lưu và khôi phục thêm state, miễn dữ liệu an toàn/được phiên bản hóa hợp lý.

Cấu trúc thường gặp:

```java
private void writeObject(ObjectOutputStream out)
        throws IOException {
    out.defaultWriteObject();
    out.writeInt(this.valueInheritedFromNonSerializableParent);
}

private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    this.valueInheritedFromNonSerializableParent = in.readInt();
}
```

**Đây chỉ là pseudocode gần Java:** field `valueInheritedFromNonSerializableParent` phải thực sự tồn tại và có quyền ghi, hook phải đặt trong class phù hợp, thêm import và quản lý stream format. Đặc biệt, cần cân nhắc **phạm vi truy cập**: field private của superclass không thể được subclass trực tiếp gán chỉ vì nó là Serializable; cần setter/protected API phù hợp nếu lớp cha cho phép.

Những điểm cần nhớ:

1. `defaultWriteObject()` và `defaultReadObject()` xử lý phần field thông thường của class Serializable theo cơ chế mặc định.
2. Các giá trị được ghi thêm bằng `writeInt`, `writeUTF`… cần đọc lại **đúng thứ tự và đúng kiểu**.
3. Hook `readObject` **không phải constructor**. Nó được gọi theo cơ chế Object Serialization và có thể kiểm tra lại state sau khi các field cần thiết được phục hồi.
4. Dữ liệu stream có thể thiếu, không hợp lệ hoặc bị sửa; phải kiểm tra ràng buộc nghiệp vụ và xem xét `InvalidObjectException` khi state không đáp ứng invariants.
5. Việc thay đổi định dạng dữ liệu của custom hook cần kế hoạch tương thích phiên bản.

### Ví dụ tư duy

Một superclass không Serializable `BasePerson` có field `protected int age`. Subclass `PersonRecord implements Serializable` có thể tự ghi `age` vào phần dữ liệu bổ sung. Trong deserialization, `BasePerson()` chạy trước và tạo age mặc định; sau đó `PersonRecord.readObject(...)` đọc giá trị age đã được serialize rồi cập nhật field. Kết quả là state cha có thể được phục hồi một cách **có chủ đích**, thay vì dựa vào cơ chế mặc định vốn không lưu nó.

Hãy cân nhắc tránh lưu những field trạng thái hệ thống có tính không thể tái tạo, như native handles, sockets đang mở, thread hay locks đang sống. Serialization không bảo đảm khôi phục nguyên trạng các tài nguyên hệ điều hành ấy.

## 13. `serialVersionUID` và lỗi lớp không tương thích

Nên khai báo `serialVersionUID` rõ ràng trong class Serializable:

```java
private static final long serialVersionUID = 1L;
```

ID này dùng trong kiểm tra khả năng tương thích của serialized class descriptor. Nếu class cục bộ không tương thích với descriptor trong stream, `readObject()` có thể ném `InvalidClassException` trước hoặc trong quá trình khôi phục đối tượng.

**Phải phân biệt nguyên nhân:**

| Vấn đề | Lỗi/hậu quả thường gặp |
| --- | --- |
| Superclass không Serializable thiếu no-arg constructor hợp lệ | `InvalidClassException`, ví dụ nguyên nhân “no valid constructor” |
| `serialVersionUID` không khớp hoặc class descriptor không tương thích | `InvalidClassException`, nhưng là lý do khác |
| Field chứa object không serializable trong lúc `writeObject` | `NotSerializableException` |
| Class cần khôi phục không có ở classpath | `ClassNotFoundException` |
| Dữ liệu hỏng hoặc stream không hợp lệ | `StreamCorruptedException`/`IOException` hoặc lỗi khác theo tình huống |

Không xử lý mọi `InvalidClassException` bằng cách “thêm constructor” nếu gốc lỗi là phiên bản lớp không tương thích. Ngược lại, **khai báo `serialVersionUID` không thể bù cho constructor superclass bị thiếu**.

## 14. `Serializable` khác `Externalizable`

`Externalizable` **mở rộng `Serializable`**, nhưng là một giao thức serialization khác: class chủ động ghi/đọc toàn bộ nội dung cần lưu bằng `writeExternal` và `readExternal`.

Điểm rất quan trọng đối với constructor:

- **Serializable thông thường:** khi deserialize, **không gọi constructor của class Serializable**; gọi no-arg constructor hợp lệ của superclass gần nhất **không Serializable**.
- **Externalizable:** khi deserialize, runtime gọi **public no-arg constructor của chính class Externalizable** rồi gọi `readExternal(...)`; vì constructor của class Externalizable chạy, **chuỗi `super()` thông thường cũng chạy**.

Bảng so sánh:

| Tiêu chí | `Serializable` mặc định | `Externalizable` |
| --- | --- | --- |
| Có marker interface? | Có | Có, nhưng thêm phương thức |
| Constructor class được khôi phục có chạy? | **Không** | **Có**, public no-arg constructor |
| Superclass không Serializable được khởi tạo? | Có, qua no-arg constructor được chọn | Có, qua chuỗi constructor thông thường |
| Ai định nghĩa dữ liệu được ghi? | Default serialization, có thể thêm hook | `writeExternal` / `readExternal` |
| Yêu cầu constructor đặc trưng | No-arg của superclass nonserializable gần nhất, có thể truy cập | **Public** no-arg của class Externalizable |
| Lỗi thường gặp | Superclass thiếu no-arg hợp lệ | Externalizable thiếu public no-arg hoặc custom read/write không đối xứng |

**Đừng áp dụng quy tắc “constructor Serializable không chạy” cho `Externalizable` mà không phân biệt giao thức.** Đây là ngoại lệ quan trọng dễ bị hỏi trong phỏng vấn.

## 15. Ví dụ Java 17: constructor `Externalizable` được gọi

**Tệp `ExternalizableConstructorDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

public class ExternalizableConstructorDemo {
    public static class Data implements Externalizable {
        private static final long serialVersionUID = 1L;

        private int number;

        public Data() {
            System.out.println("Data constructor");
        }

        Data(int number) {
            this.number = number;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeInt(number);
        }

        @Override
        public void readExternal(ObjectInput in)
                throws IOException {
            number = in.readInt();
        }
    }

    public static void main(String[] args) throws Exception {
        Data before = new Data(25);

        ByteArrayOutputStream memory = new ByteArrayOutputStream();
        try (ObjectOutputStream output =
                     new ObjectOutputStream(memory)) {
            output.writeObject(before);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(memory.toByteArray()))) {
            Data after = (Data) input.readObject();
            System.out.println("Value: " + after.number);
        }
    }
}
```

Biên dịch, chạy:

```bash
javac ExternalizableConstructorDemo.java
java ExternalizableConstructorDemo
```

**Stdout chính xác nếu thao tác thành công:**

```text
Data constructor
Value: 25
```

Tại sao chỉ có **một** dòng “Data constructor”? `new Data(25)` chạy constructor **một tham số**, không in gì. Khi `readObject()` khôi phục Externalizable, runtime gọi **public `Data()`**, in “Data constructor”, rồi gọi `readExternal()` để lấy số 25.

Nếu constructor một tham số cũng in log, ta sẽ thấy log của nó lúc tạo object mới, nhưng **lúc deserialize vẫn chỉ chọn public no-arg**.

Chú ý lớp ở ví dụ là **public static nested class**, có public no-arg constructor. Dùng một **non-static inner class** để serialize có nhiều bẫy về tham chiếu tới enclosing instance và yêu cầu constructor; khi minh họa nên ưu tiên static nested class hoặc top-level class.

## 16. Trường hợp lớp cha thay đổi giữa lúc ghi và đọc

Phần mô tả class nằm trong chương trình nhận stream, không chỉ trong serialized bytes. Vì vậy việc thay đổi cây kế thừa giữa lúc write và read có thể ảnh hưởng khả năng deserialize và cách khởi tạo lớp cha.

Ví dụ tư duy:

1. Phiên bản đầu có `Child implements Serializable` và `Base` là nonserializable với `Base()` hợp lệ.
2. Phiên bản mới thay `Base()` bằng một constructor chỉ nhận tham số.
3. Stream cũ có thể vẫn có `serialVersionUID` khớp với `Child`, nhưng **deserialization thất bại** vì superclass `Base` hiện không còn constructor hợp lệ.

Hành vi tương thích còn phụ thuộc những thay đổi lớp/field/serial descriptor khác. Vì vậy khi sửa thư viện có các class được serialize lâu dài, hãy thử **deserialize dữ liệu đã lưu từ phiên bản cũ bằng phiên bản mới**; đừng chỉ chạy test roundtrip `writeObject -> readObject` trong một binary duy nhất.

Nếu phải lưu dữ liệu lâu dài qua nhiều phiên bản và nhiều ngôn ngữ, Java native serialization có nhiều hạn chế so với định dạng schema có phiên bản như JSON/CBOR/Protocol Buffers hoặc các giải pháp chuyên biệt. Không nên chọn Java serialization chỉ vì muốn tự động lưu object graph mà không đánh giá yêu cầu tương thích.

## 17. Những lỗi suy luận phổ biến

| Suy luận sai | Vì sao sai | Hiểu đúng |
| --- | --- | --- |
| “Deserialization không bao giờ gọi constructor.” | Superclass nonserializable vẫn cần được khởi tạo | Chọn no-arg constructor hợp lệ của superclass gần nhất không Serializable |
| “Serializable class bắt buộc có no-arg constructor.” | Constructor của lớp đó không được gọi theo cơ chế mặc định | Yêu cầu nằm ở superclass đầu tiên không Serializable |
| “Bất kỳ no-arg constructor nào của superclass đều được.” | Private/inaccessible no-arg không đáp ứng hợp đồng | Phải accessible cho subclass Serializable |
| “Subclass không có `implements Serializable` thì constructor của nó chạy.” | Serializable được kế thừa | Mọi class trong đoạn cây serializable có constructor bị bỏ qua lúc deserialize |
| “Field của superclass không Serializable tự được lưu.” | Default serialization chỉ lưu phần serializable | Superclass state do constructor khởi tạo lại hoặc custom logic |
| “Initializer của Serializable chạy lại.” | Khôi phục không chạy instance initialization của Serializable | Serial fields từ stream; transient về mặc định |
| “Thêm `serialVersionUID` sửa được lỗi no-arg constructor.” | Hai điều kiện độc lập | Sửa cấu trúc/constructor superclass hoặc mô hình serialization |
| “`Externalizable` cũng bỏ qua constructor class.” | Giao thức Externalizable gọi public no-arg constructor | Phân biệt hai cơ chế |
| “Constructor lớp con luôn xác thực stream.” | Constructor lớp con không chạy | Xác thực qua custom readObject/readResolve hay cơ chế phù hợp |
| “Superclass constructor có thể gọi mọi method override an toàn.” | Method có thể quan sát subclass state chưa restored | Tránh virtual call từ superclass constructor |
| “Một stream không tin cậy có thể deserialize vô điều kiện.” | Object graph/hook có tác dụng phụ và nhiều rủi ro | Dùng filter hoặc tránh native serialization của input không tin cậy |

## 18. Về bảo mật và sự an toàn khi deserialize

Java native deserialization từ nguồn **không tin cậy** có rủi ro nghiêm trọng: object graph phức tạp, nội dung không mong muốn, chuỗi phương thức/hook và những class có tác dụng phụ. Ngay cả khi constructor của Serializable class **không chạy**, các **hook như `readObject`, `readResolve`**, việc khởi tạo superclass nonserializable và việc tạo những object khác trong graph vẫn có thể thực thi logic.

Vì vậy không kết luận “deserialization an toàn vì bỏ qua constructor”. Với ứng dụng nhận byte từ client hoặc nguồn bên ngoài:

- Ưu tiên **định dạng dữ liệu có schema** và decoder có kiểm tra chặt nếu có thể.
- Nếu buộc sử dụng Object Serialization, thiết lập `ObjectInputFilter` với allowlist/denylist phù hợp, giới hạn độ sâu, số lượng đối tượng và kích thước dữ liệu theo bối cảnh.
- Kiểm tra invariants sau khi khôi phục và không đưa object chưa xác thực trực tiếp vào logic đặc quyền.
- Quản lý phiên bản stream, loại class được phép nhận, giới hạn tài nguyên và cơ chế cô lập.

ObjectInputFilter giảm rủi ro nhưng **không biến việc deserialize mọi dữ liệu không tin cậy thành an toàn tuyệt đối**. Việc thiết kế loại class được khôi phục và chính sách xử lý vẫn cần rà soát riêng.

## 19. Câu hỏi tự kiểm tra

**Câu 1.** Class `Child extends Base implements Serializable`, `Base` không Serializable và có `protected Base()`. Khi `readObject()` trả về một `Child`, constructor nào chạy?

**Đáp án:** `Base()` và chuỗi constructor các tổ tiên nonserializable mà `Base()` gọi. `Child()` không chạy trong cơ chế Serializable thông thường.

**Câu 2.** `Base` chỉ có `Base(int)`, còn `Child()` gọi `super(1)` nên `new Child()` biên dịch. Deserialization có thành công không?

**Đáp án:** Không theo cơ chế default Serializable thông thường: thiếu no-arg constructor hợp lệ của superclass nonserializable. `readObject()` sẽ gặp `InvalidClassException`.

**Câu 3.** `Base` không Serializable có field `int x = 5` và no-arg constructor đặt `x = 8`. Sau khi `new Child()`, ứng dụng đổi `x = 20` rồi serialize `Child`. Sau deserialize, x có giá trị nào?

**Đáp án:** **8**, nếu không có custom serialization khác. Field của `Base` không được cơ chế mặc định lưu; no-arg constructor `Base()` chạy lại và đặt giá trị 8.

**Câu 4.** `Child` Serializable có `transient int cache = 100`. Sau khi deserialize, `cache` bằng 100 hay 0?

**Đáp án:** **0** nếu không có custom read hook. Field initializer của `Child` không chạy lại, và `transient` không được khôi phục bởi default serialization.

**Câu 5.** Một class `Data implements Externalizable` có `public Data()` và `Data(int)`. Khi đọc từ stream, constructor nào chạy?

**Đáp án:** Runtime gọi **public `Data()`**, rồi gọi `readExternal`. Trường hợp Externalizable không áp dụng quy tắc bỏ qua constructor của chính class như Serializable thông thường.

**Câu 6.** Có đúng rằng `static` field được serialize nếu khai báo ở Serializable class không?

**Đáp án:** Không. Java Object Serialization mặc định xử lý instance state; `static` là state thuộc class. Giá trị static có thể khác khi chạy chương trình đọc so với chương trình đã ghi.

**Câu 7.** Có thể dùng constructor của `Child implements Serializable` làm nơi duy nhất kiểm tra `balance >= 0` với đối tượng đọc từ stream không?

**Đáp án:** Không. Constructor `Child` không chạy khi deserialize theo cơ chế mặc định. Cần kiểm tra invariants trong hook phục hồi hoặc cơ chế xác thực phù hợp trước khi sử dụng object.

## 20. Tổng kết

Trong Java Object Serialization, cần nhớ ba “vùng” của chuỗi kế thừa:

```text
               Object (không Serializable)
                   |
               Base   (không Serializable)
                   |
            --- ranh giới ---
                   |
             Parent (Serializable)
                   |
             Child  (Serializable)

new Child():
  Constructor Object -> Base -> Parent -> Child

deserialize Child:
  Constructor Object -> Base
  KHÔNG gọi Parent/Child constructors;
  phục hồi state Serializable Parent/Child từ stream.
```

**Quy tắc vàng:** *Java chỉ yêu cầu một constructor không tham số phù hợp ở lớp cha gần nhất **không Serializable**; constructor của các class thuộc đoạn Serializable được bỏ qua khi khôi phục object thông thường. Vì vậy field của cha không Serializable cần được khởi tạo lại hoặc phục hồi tường minh, trong khi field Serializable được đọc từ stream.*

Cần kiểm tra kỹ `transient`, instance initializer, tính accessible của constructor, sự khác biệt với `Externalizable`, tương thích phiên bản và các rủi ro bảo mật. Đây là nền tảng để giải thích phần lớn câu hỏi về constructor trong Java serialization và để thiết kế class có khả năng deserialize ổn định.

**Tài liệu tham khảo Java SE 17:**

1. [Serializable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serializable.html) — yêu cầu no-arg constructor của nonserializable superclass và cơ chế khôi phục.
2. [ObjectInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html) — `readObject()` và khôi phục object graph.
3. [Externalizable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Externalizable.html) — constructor và giao thức externalization.
4. [Java Object Serialization Specification](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/index.html) — quy trình khôi phục, trạng thái lớp và stream format.
5. [ObjectInputFilter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html) — các giới hạn khi xử lý dữ liệu serialized.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serialization-basics-009">Ghi lại đối tượng đã thay đổi, reset và cache handle</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi lại đối tượng đã thay đổi, reset và cache handle

## 1. Vấn đề: sửa đối tượng nhưng dữ liệu nhận vẫn cũ

Khi dùng Java Object Serialization, gọi `ObjectOutputStream.writeObject(x)` lần đầu thường ghi trạng thái đối tượng và cấp **handle** (định danh tham chiếu trong luồng). Nếu về sau thay đổi field của **cùng instance** rồi gọi `writeObject(x)` lần nữa, stream thường chỉ ghi một **back-reference** tới handle cũ. `ObjectInputStream.readObject()` ở phía nhận có thể trả lại chính instance đã đọc lần đầu với trạng thái cũ.

Đây là tính năng bảo toàn cấu trúc **object graph**, không phải lỗi buffering. Nếu hai field cùng tham chiếu một object, reader cần tái tạo quan hệ `a.ref == b.ref`. Nếu graph có chu trình A → B → A, bảng handles giúp tránh duyệt vô hạn.

Sự khác biệt quan trọng:

- `writeObject(x)`: đối tượng chưa được ghi thì ghi định nghĩa; nếu **đã có handle** thì có thể ghi reference.
- `reset()`: tạo một ranh giới mới trong serialization stream, bỏ state về những đối tượng đã ghi; lần ghi sau sẽ tuần tự hóa graph như mới.
- `writeUnshared(x)`: buộc ghi mới **root object** của lời gọi, nhưng không nhất thiết làm mới các object con đã có handles.
- `flush()`: đẩy bytes đã encode sang output bên dưới; **không** xóa handles.

Không dùng `serialVersionUID` để theo dõi object mutation: UID phục vụ compatibility của class, không phải số phiên bản nội dung từng object.

## 2. Cơ chế bảng handles

```text
Writer, cùng một ObjectOutputStream:
    writeObject(x)  -> Object definition + handle H
    writeObject(x)  -> Reference to H

Reader, cùng một ObjectInputStream:
    readObject()    -> Tạo object y, lưu H -> y
    readObject()    -> Trả lại chính y
```

Bảng handle là **theo stream instance**, không phải global cache JVM; object identity (`==`) khác so sánh nội dung `equals()`. Hai đối tượng khác nhau có cùng field vẫn có thể được ghi độc lập.

Ví dụ `Account` được ghi một lần với `balance=10`; sau đó trong JVM gửi nó được sửa thành `balance=20`. Nếu lần thứ hai chỉ là reference đến handle, bản đọc thứ hai không tự thành 20. **ObjectOutputStream không giám sát field changes và không tự tạo delta.**

Đối tượng được encode là **toàn graph các field đủ điều kiện**, trừ trường hợp `transient`, `static` hoặc custom serialization. Một field chứa đối tượng không hỗ trợ `Serializable` có thể khiến `NotSerializableException`. Bảng handles còn ghi nhớ những object con và class descriptors đã gặp; điều đó quan trọng khi reset, writeUnshared và vận hành stream dài hạn.

## 3. Ví dụ Java 17: ghi cũ, reset, rồi ghi mới

Tạo file `SerializationHandleDemo.java`:

```java
import java.io.*;

public class SerializationHandleDemo {
  static class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    String text;
    Message(String text) { this.text = text; }
  }
  public static void main(String[] args) throws Exception {
    Message message = new Message("before");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(message);
      message.text = "after";
      out.writeObject(message);
      out.reset();
      out.writeObject(message);
    }
    try (ObjectInputStream in = new ObjectInputStream(
        new ByteArrayInputStream(bytes.toByteArray()))) {
      Message a = (Message) in.readObject();
      Message b = (Message) in.readObject();
      Message c = (Message) in.readObject();
      System.out.println("values=" + a.text + "," + b.text + "," + c.text);
      System.out.println("a==b=" + (a == b));
      System.out.println("b==c=" + (b == c));
    }
  }
}
```

Biên dịch/chạy với Java 17 trở lên:

```bash
javac --release 17 SerializationHandleDemo.java
java SerializationHandleDemo
```

Kết quả đã kiểm thử:

```text
values=before,before,after
a==b=true
b==c=false
```

Diễn giải:

1. Lần `writeObject` đầu mã hóa `Message{text="before"}` và tạo handle.
2. Phép gán `message.text="after"` thay đổi object **trong bộ nhớ bên gửi**, không sửa bytes đã ghi.
3. Lần `writeObject` thứ hai ghi reference tới handle; `b` là cùng object với `a`, có `text="before"`.
4. `out.reset()` ghi **reset marker** và xóa bảng objects đã biết của writer. Reader gặp marker đó và cũng reset bảng handles của mình.
5. Lần thứ ba mã hóa object như mới với `text="after"`. `c` có identity khác `b`.

Không cần tạo một `ObjectInputStream` mới sau `reset()`; reader hiện tại hiểu marker trong serialization protocol. `reset` cũng không xóa những object mà code reader đã giữ trong biến `a` hoặc `b`.

## 4. reset() làm gì và không làm gì?

Chữ ký: `public void reset() throws IOException`.

- Bỏ trạng thái handles của luồng đối với những objects đã ghi; các object sau marker được xử lý như chưa từng ghi trong **epoch** hiện tại.
- Ghi reset token vào stream, phối hợp với state của `ObjectInputStream` tương ứng.
- Thích hợp cho nhiều snapshot **độc lập** của cùng một graph mutable, khi muốn reader nhìn thấy trạng thái mới.
- Có thể giúp giới hạn lượng state do stream dài hạn ghi nhớ, nhưng làm tăng dung lượng do object/class descriptors cần encode lại.

**Không làm:** xóa bytes trước đó trong file, truncate, ghi lại object nếu chưa gọi `writeObject` sau reset, tự bảo vệ snapshot khỏi thread khác sửa field, xác nhận storage đã sync hoặc thực hiện transaction.

Không được gọi `reset()` **khi quá trình serialize một object đang diễn ra**, ví dụ bên trong custom `private void writeObject(ObjectOutputStream out)` của object đang được ghi. Thao tác không hợp lệ này có thể ném `IOException`. Hãy reset **giữa** các lần ghi toàn bộ graph.

So sánh:

| Lệnh | Tác động |
| --- | --- |
| `writeObject(x)` | Ghi định nghĩa hoặc back-reference. |
| `reset()` | Đánh dấu epoch mới; quên toàn bộ object handles hiện tại. |
| `flush()` | Đẩy bytes pending; **không làm mới handle**. |
| `writeUnshared(x)` | Ghi mới root x; nested graph có thể tiếp tục dùng handles cũ. |
| `close()` | Đóng stream/tài nguyên, không phải reset giữa cùng phiên giao thức. |
| `FileChannel.force(...)` | Yêu cầu đồng bộ dữ liệu lưu trữ, không liên quan handles. |

Một lỗi vận hành là tạo `new ObjectOutputStream(sameOutput)` nhiều lần lên **cùng một luồng liên tục** mà phía nhận vẫn dùng một `ObjectInputStream`. Constructor stream ghi header mới; header đột ngột ở giữa dữ liệu có thể gây `StreamCorruptedException`. Muốn nhiều snapshot trên một luồng, dùng một writer và `reset()`; muốn nhiều stream riêng thì cần ranh giới/reader riêng.

## 5. writeUnshared: root mới nhưng nested object cũ

`writeUnshared(root)` không phát back-reference tới chính **root** của lời gọi đó, nhưng các object con đã được ghi vẫn có thể được tham chiếu qua handles. Vì vậy nó **không phải deep copy**, cũng không bảo đảm snapshot của toàn graph có các field mới nhất.

Tạo file `WriteUnsharedDemo.java`:

```java
import java.io.*;

public class WriteUnsharedDemo {
  static class Item implements Serializable {
    private static final long serialVersionUID = 1L;
    String value;
    Item(String value) { this.value = value; }
  }
  static class Wrapper implements Serializable {
    private static final long serialVersionUID = 1L;
    String label;
    Item item;
    Wrapper(String label, Item item) { this.label=label; this.item=item; }
  }
  public static void main(String[] args) throws Exception {
    Wrapper w = new Wrapper("one", new Item("red"));
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(w);
      w.label="two";
      w.item.value="blue";
      out.writeUnshared(w);
      out.writeObject(w);
    }
    try (ObjectInputStream in = new ObjectInputStream(
        new ByteArrayInputStream(bytes.toByteArray()))) {
      Wrapper a=(Wrapper)in.readObject();
      Wrapper b=(Wrapper)in.readUnshared();
      Wrapper c=(Wrapper)in.readObject();
      System.out.println("labels="+a.label+","+b.label+","+c.label);
      System.out.println("nested="+a.item.value+","+b.item.value);
      System.out.println("a==b="+(a==b));
      System.out.println("a==c="+(a==c));
      System.out.println("nested-shared="+(a.item==b.item));
    }
  }
}
```

```bash
javac --release 17 WriteUnsharedDemo.java
java WriteUnsharedDemo
```

Kết quả kiểm thử:

```text
labels=one,two,one
nested=red,red
a==b=false
a==c=true
nested-shared=true
```

Lần đầu lưu root `Wrapper` và child `Item`. Sau khi thay đổi root thành "two" và child thành "blue", `writeUnshared(w)` ghi một **root mới** nhưng child vẫn có thể trỏ về `Item` cũ với "red". Lần `writeObject(w)` tiếp theo vẫn có thể tham chiếu root ban đầu (`a==c`). Muốn toàn graph hiện trạng được ghi lại, `reset(); writeObject(w)` rõ nghĩa hơn.

`ObjectInputStream.readUnshared()` không dùng như một phiên bản “tốt hơn” của `readObject()`: nếu entry cần đọc là **back-reference** thì `readUnshared` ném `ObjectStreamException`. Các cơ chế class như `readResolve` và đối tượng đặc biệt có thể tạo thêm ngoại lệ về identity; không suy ra mọi nested reference đều được clone.

## 6. Chu kỳ reset và bộ nhớ

Một stream tồn tại lâu và ghi nhiều object mới có thể làm handle table của writer/reader tăng, gây overhead và giữ references. Có thể đặt ranh giới giữa các batch:

```java
// Snippet minh họa; out và makeMessage(i) do ứng dụng định nghĩa.
for (int i = 0; i < 10_000; i++) {
    out.writeObject(makeMessage(i));
    if ((i + 1) % 1_000 == 0) out.reset();
}
```

Mốc 1.000 chỉ để minh họa; chọn theo kích thước graph, heap, throughput, yêu cầu identity, và benchmark thực tế. Reset **quá dày** khiến nhiều bytes phải serialize lại, có thể giảm hiệu năng. Reset **quá ít** làm state handles phình ra. Nếu receiver cần định danh một thực thể xuyên nhiều snapshots, hãy truyền **business ID/version**; đừng dựa vào Java object handle luôn giữ nguyên.

## 7. Snapshot nhất quán và ứng dụng thực tế

Cache handles chỉ là một phần bài toán. Nếu thread khác sửa graph **trong lúc** serialization, stream có thể chứa tổ hợp fields không hợp lệ. `Serializable` không có nghĩa tự động thread-safe, và `reset` không biến quá trình ghi thành transaction.

Giải pháp nên cân nhắc:

- Tạo immutable DTO snapshot bên trong lock hoặc giao dịch nghiệp vụ rồi serialize snapshot.
- Dùng **một owner** cho `ObjectOutputStream`, phối hợp các producers qua hàng đợi; tránh xen kẽ `writeObject`, `reset`, `flush` giữa nhiều threads.
- Định nghĩa rõ version/sequence khi gửi object liên tục. `ObjectOutputStream` không tự sinh delta hay application acknowledgement.
- Xử lý `IOException`, EOF và hỏng stream; không đoán peer đã xử lý thành công chỉ vì lệnh local `writeObject` trả về.
- Nếu dữ liệu phải sống sót sau mất điện, `flush/reset` không thay thế file sync và giao thức recovery.
- Tránh deserialize dữ liệu không tin cậy một cách tùy ý. Nếu buộc dùng, kiểm soát class được phép, giới hạn chiều sâu/bytes/references bằng `ObjectInputFilter` và chọn giao thức có hợp đồng rõ ràng.

Khi cần lưu trữ lâu dài, tương thích đa ngôn ngữ hoặc nâng cấp schema thường xuyên, DTO cùng định dạng như JSON/CBOR/Protobuf hoặc database có thể phù hợp hơn native Java Serialization.

## 8. Sai lầm phổ biến

| Sai lầm | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Mutate x rồi `writeObject(x)` lại | Reader thấy state cũ | Reset trước snapshot mới hoặc gửi object/version riêng |
| Gọi `flush()` để cập nhật field | Handle table không thay đổi | Phân biệt byte buffering với object identity |
| Dùng `writeUnshared` làm deep copy | Nested fields vẫn cũ | `reset + writeObject` cho graph mới |
| Dùng `readUnshared` cho back-reference | `ObjectStreamException` | Ghép contract writer/reader |
| Gọi reset trong custom `writeObject` | `IOException` | Reset giữa các graph |
| Tạo nhiều writer trên cùng stream liên tục | Stream header xuất hiện giữa luồng | Một writer + reset hoặc stream độc lập |
| Quên reset với stream sống lâu | Áp lực bộ nhớ | Chọn batch/epoch có đo đạc |
| Reset liên tục vô cớ | Tăng kích thước và công encode | Chọn policy theo workload |
| Dùng `serialVersionUID` làm object version | Không theo dõi mutation | Version/sequence nghiệp vụ |
| Serialize graph đang bị thread khác sửa | Snapshot không nhất quán | Immutable DTO, ownership, đồng bộ |
| Coi reset là flush xuống đĩa | Sai mức durability | Storage sync/transaction phù hợp |

## 9. Tự kiểm tra

**Vì sao lần `writeObject` thứ hai có thể không ghi fields đã đổi?** Vì root đã có handle, writer có thể chỉ gửi back-reference.

**Hai lần `readObject()` có thể trả cùng object không?** Có, nếu lần sau đọc back-reference.

**Reset có xóa byte cũ hoặc làm reader mất các references trước đó không?** Không; reset thay trạng thái lookup handles của **đoạn stream tiếp theo**, không xóa đối tượng ứng dụng đang giữ.

**Reset có làm mới cả graph?** Nó xóa state handles của stream, nên graph ở lần `writeObject` sau được xử lý như mới, miễn không có mutation/race đồng thời hoặc cơ chế tùy biến can thiệp.

**`writeUnshared(root)` có sao chép sâu graph không?** Không. Root mới; child vẫn có thể được tham chiếu từ handle cũ.

**Lúc nào không nên dùng native Java Serialization?** Khi dữ liệu đầu vào không tin cậy hoặc yêu cầu định dạng lưu trữ/giao tiếp bền vững, đa ngôn ngữ, dễ versioning.

## Tài liệu chính thức

- [Java SE 21 — ObjectOutputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectOutputStream.html)
- [Java SE 21 — ObjectInputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputStream.html)
- [Java Object Serialization Specification — Output](https://docs.oracle.com/en/java/javase/21/docs/specs/serialization/output.html)
- [Java Object Serialization Specification — Input](https://docs.oracle.com/en/java/javase/21/docs/specs/serialization/input.html)
- [Java SE 21 — ObjectInputFilter](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputFilter.html)

**Kết luận:** `ObjectOutputStream` nhớ **identity của object đã ghi**, không tự theo dõi state. `reset` tạo epoch mới để ghi lại object graph; `writeUnshared` chỉ bảo đảm root mới; `flush` không thay đổi handle cache.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serialization-basics-010">Stream header và lỗi khi nối tùy tiện nhiều object stream</a>

<details>
<summary>Xem chi tiết</summary>

# Stream header và lỗi khi nối tùy tiện nhiều object stream trong Java Serialization

## 1. Vì sao ObjectOutputStream không phải cách “cứ mở thêm là ghi nối”?

Java cung cấp `java.io.ObjectOutputStream` để tuần tự hóa (serialize) đồ thị đối tượng thành một **luồng byte theo giao thức Java Object Serialization**, và `java.io.ObjectInputStream` để đọc lại luồng theo cùng giao thức.

Một luồng đối tượng không chỉ là các byte biểu diễn từng object đặt cạnh nhau. Nó còn mang **stream header**, mô tả class, thông tin định dạng, handles dùng để tham chiếu lại object đã thấy, dấu reset và dữ liệu điều khiển khác.

~~~text
Đúng: MỘT object stream liên tục

OutputStream vật lý
  |
  +-- ObjectOutputStream được tạo một lần
        |
        +-- [stream header]
        +-- [object A / class descriptors / handles]
        +-- [object B / references / metadata]
        +-- [object C ...]
                 |
                 v
       Một ObjectInputStream tương ứng
          readObject() → A
          readObject() → B
          readObject() → C
~~~

Nếu đóng một `ObjectOutputStream` rồi tạo `ObjectOutputStream` khác để **append** vào cuối cùng file, constructor mới sẽ lại ghi **header mới**. Nhưng `ObjectInputStream` đang đọc một object stream liên tục sẽ không tự hiểu header thứ hai là điểm bắt đầu của một stream độc lập.

~~~text
Sai: nối trực tiếp hai object stream có header riêng

[HEADER 1][OBJECT A][HEADER 2][OBJECT B]
          |
          +-- Một ObjectInputStream đọc A thành công
                                  |
                                  +-- đọc tiếp gặp HEADER 2
                                      ở chỗ mong token object
                                      → StreamCorruptedException
~~~

**Ý chính:** phải phân biệt **nhiều object được ghi vào một object stream** với **nhiều object stream độc lập được nối byte tùy tiện**. Hai cấu trúc không có cùng giao thức đọc.

## 2. Stream header được ghi vào lúc nào?

Khi khởi tạo:

~~~java
ObjectOutputStream out = new ObjectOutputStream(rawOutput);
~~~

`ObjectOutputStream` ghi **serialization stream header** vào `rawOutput` trong quá trình khởi tạo. Trong định dạng Java Serialization chuẩn, bốn byte đầu của một stream thông thường là:

~~~text
AC ED 00 05
│     │
│     └── STREAM_VERSION = 0x0005
└──────── STREAM_MAGIC   = 0xACED
~~~

Các hằng số tương ứng nằm trong `java.io.ObjectStreamConstants`. Đây là magic và version của **giao thức Java Serialization**, không phải mã hóa văn bản và cũng không phải header ZIP/GZIP.

Phía đọc:

~~~java
ObjectInputStream in = new ObjectInputStream(rawInput);
~~~

Constructor `ObjectInputStream` đọc/xác minh stream header. Vì vậy:

- Nếu input không bắt đầu bằng header hợp lệ, constructor có thể ném `StreamCorruptedException`.
- Nếu input stream tương tác (ví dụ pipe/network) chưa có đủ byte header, constructor phía đọc **có thể chặn chờ**. Thông thường hãy tạo/phát header ở phía ghi và `flush()` đúng lúc trước khi phía nhận đợi header.
- Nếu đã tạo `ObjectInputStream` và đọc object A thành công, các lời gọi `readObject()` tiếp theo mong **object content token** của **cùng luồng**, không mong một header thứ hai.
- Một lần `ObjectOutputStream.flush()` đẩy byte đang đệm xuống stream bên dưới theo hợp đồng I/O, **không** tạo header mới và **không** đánh dấu một phiên serialization mới.

### Header không phải dấu phân cách từng object

Một object stream thường có **một stream header ở đầu**, không phải một header trước mỗi object:

~~~java
try (ObjectOutputStream out =
        new ObjectOutputStream(destination)) {
    out.writeObject("first");
    out.writeObject("second");
    out.writeObject("third");
}
~~~

Đây là mảnh code minh họa; `destination` là `OutputStream` đã tạo. Cả ba lần `writeObject` dùng **cùng** object stream, vì vậy phía nhận có thể dùng một `ObjectInputStream` gọi `readObject()` ba lần tương ứng.

Ngược lại, tạo ba `ObjectOutputStream` độc lập trên một `OutputStream` vật lý sẽ tạo nhiều đoạn serialization có header riêng. **ObjectInputStream không tự reset parser tại điểm nối đó.**

## 3. Điều gì xảy ra khi append bằng ObjectOutputStream mới?

Mẫu sai phổ biến khi muốn bổ sung object vào file:

~~~java
// Phiên đầu tiên:
try (ObjectOutputStream out = new ObjectOutputStream(
        new FileOutputStream(file))) {
    out.writeObject("alpha");
}

// Phiên tiếp theo: append byte vật lý vào cuối file.
try (ObjectOutputStream out = new ObjectOutputStream(
        new FileOutputStream(file, true))) {
    out.writeObject("beta");
}
~~~

Đây là **mảnh code minh họa lỗi**, với biến `file` kiểu `File` đã có. Hai lần dùng `FileOutputStream` khác nhau và lần thứ hai có `append=true`. Nhưng **`append=true` chỉ nói với hệ thống tệp rằng byte mới được ghi ở cuối file**; nó không nói với `ObjectOutputStream` rằng đang tiếp nối trạng thái serialization của phiên trước.

Nội dung vật lý về mặt ý tưởng:

~~~text
Lần 1:
[AC ED 00 05][serialized "alpha"]

Lần 2:
[AC ED 00 05][serialized "alpha"]
[AC ED 00 05][serialized "beta"]
               ^
     header thứ hai ở giữa file
~~~

Một `ObjectInputStream` mở từ đầu file có thể đọc `"alpha"`. Khi `readObject()` lần nữa, nó gặp byte magic `0xAC` ở vị trí mong đợi một serialization token hợp lệ bên trong stream, và thường ném `StreamCorruptedException` (ví dụ thông báo có thể chứa `invalid type code: AC`).

**Không nên phụ thuộc vào nguyên văn exception message** vì chi tiết thông báo có thể khác giữa JDK/triển khai. Điểm đáng tin là cấu trúc byte đã vi phạm hợp đồng của **một stream liên tục**.

### Trường hợp lỗi xuất hiện ngay trong constructor

Nếu bạn mở một `ObjectInputStream` mới nhưng bắt đầu đọc **giữa file**, tại byte của một object thay vì header, constructor có thể ném `StreamCorruptedException` ngay khi kiểm tra header. Điều này khác với tình huống đọc object đầu thành công rồi mới lỗi ở header thứ hai.

Cả hai bắt nguồn từ cùng sai lầm: **không xác định đúng ranh giới một serialization stream**.

## 4. Mô hình đúng 1: giữ một ObjectOutputStream cho toàn bộ luồng logic

Nếu chương trình chủ động ghi nhiều object trong cùng một phiên, cách đơn giản nhất là tạo writer một lần:

~~~java
try (ObjectOutputStream out =
        new ObjectOutputStream(Files.newOutputStream(path))) {
    out.writeObject("alpha");
    out.writeObject("beta");
    out.writeObject("gamma");
}
~~~

Phía đọc:

~~~java
try (ObjectInputStream in =
        new ObjectInputStream(Files.newInputStream(path))) {
    String a = (String) in.readObject();
    String b = (String) in.readObject();
    String c = (String) in.readObject();
}
~~~

Hai đoạn là **mã minh họa**, cần `Path path`, các import tương ứng và xử lý `IOException`/`ClassNotFoundException`.

Ưu điểm:

- Một header thống nhất ở đầu.
- Parser của `ObjectInputStream` đọc đúng stream đang được tiếp nối.
- Handles và thông tin class trong object stream được quản lý nhất quán.
- Dễ dùng khi tạo một snapshot gồm nhiều object biết trước, hoặc khi một process duy trì writer lâu dài.

Hạn chế:

- Nếu JVM thoát và ứng dụng khởi động lại, tạo một `ObjectOutputStream` mới không thể tự khôi phục toàn bộ **trạng thái handle table** của writer trước đó bằng cách chỉ truyền `append=true`.
- Reader phải biết **khi nào dừng**: thường là số object được lưu trong header ứng dụng, một marker cuối có thiết kế rõ ràng, hoặc EOF **chỉ khi giao thức cho phép**.
- Object streams không tự cho phép đọc ngẫu nhiên object thứ 100 bằng một offset tùy ý. Trạng thái handles/descriptors có thể phụ thuộc các object trước.

Nếu mục tiêu là lưu các bản ghi độc lập, appending xuyên nhiều lần khởi động và khôi phục khi dữ liệu cuối bị ghi dở, một định dạng có **record framing rõ ràng** thường dễ vận hành hơn.

## 5. Mô hình đúng 2: nhiều stream độc lập nhưng có framing

Nếu mỗi object/batch phải được serialize độc lập, bạn **có thể** tạo một `ObjectOutputStream` mới cho mỗi batch, **nhưng phải ghi kèm độ dài hoặc cấu trúc phân cách rõ ràng** để phía đọc biết chính xác mỗi segment bắt đầu/kết thúc ở đâu.

Ví dụ định dạng tự thiết kế:

~~~text
[int32 length L1][L1 bytes của ObjectOutputStream #1]
[int32 length L2][L2 bytes của ObjectOutputStream #2]
[int32 length L3][L3 bytes của ObjectOutputStream #3]
~~~

Mỗi segment là **một object stream hợp lệ độc lập**: có header riêng và nội dung riêng. Reader:

1. Đọc độ dài segment.
2. Kiểm tra giới hạn kích thước trước khi cấp phát.
3. Đọc **đúng số byte** của segment đó vào bộ đệm giới hạn.
4. Tạo **một ObjectInputStream riêng** trên segment.
5. Gọi `readObject()` theo số object/schema của segment.
6. Đóng segment reader rồi chuyển segment tiếp theo.

Đó là cách **nối các segment đã được định khung**, chứ không phải nối mù byte rồi đặt một `ObjectInputStream` lên toàn bộ file.

Với dữ liệu không tin cậy, chỉ độ dài segment không đủ để an toàn. Ngoài giới hạn độ dài **compressed/serialized bytes**, phải giới hạn loại class được phép deserialize, độ sâu object graph, số lượng tham chiếu và bộ nhớ theo chính sách ứng dụng.

### Không thể chỉ tạo ObjectInputStream thứ hai trên cùng FileInputStream rồi hy vọng

Nếu `ObjectInputStream` đầu tiên được tạo trên cùng một underlying `InputStream`, implementation có thể đệm hoặc đọc trước một số byte. Vì thế, **không có hợp đồng tổng quát rằng vị trí underlying stream sau một lần `readObject()` là ranh giới an toàn để tạo `ObjectInputStream` kế tiếp**.

Framing theo độ dài với `ByteArrayInputStream`/bounded input là cách làm rõ ràng: mỗi `ObjectInputStream` chỉ nhìn thấy byte của segment thuộc về nó. Khi cần stream lớn không copy toàn bộ vào RAM, dùng bounded input stream và giới hạn dữ liệu cẩn thận.

## 6. Ví dụ Java 17+ chạy độc lập: sai, đúng và framed

Chương trình sau minh họa ba trường hợp với cùng hai chuỗi `"alpha"` và `"beta"`:

- **Đúng — single stream:** một `ObjectOutputStream` ghi hai object; một `ObjectInputStream` đọc hai object.
- **Sai — concatenated streams:** serialize từng object thành một stream riêng rồi nối raw bytes; một `ObjectInputStream` đọc được object đầu nhưng lỗi tại header thứ hai.
- **Đúng — framed independent streams:** mỗi object được serialize thành stream riêng, được đặt sau trường độ dài; mỗi segment có một `ObjectInputStream` riêng.

Chương trình dùng bộ nhớ thay vì file để không phụ thuộc đường dẫn. Lỗi được in theo **tên loại ngoại lệ**, không in thông báo phụ thuộc implementation.

Lưu file `ObjectStreamHeaderDemo.java`:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;

public class ObjectStreamHeaderDemo {

    private static byte[] serializeOne(String value)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static void singleStream()
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject("alpha");
            out.writeObject("beta");
        }

        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            System.out.println("single first=" + in.readObject());
            System.out.println("single second=" + in.readObject());
        }
    }

    private static void badConcatenation()
            throws IOException, ClassNotFoundException {
        byte[] first = serializeOne("alpha");
        byte[] second = serializeOne("beta");

        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        joined.writeBytes(first);
        joined.writeBytes(second);

        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(joined.toByteArray()))) {
            System.out.println("concat first=" + in.readObject());

            try {
                in.readObject();
                System.out.println("concat unexpectedly accepted");
            } catch (StreamCorruptedException expected) {
                System.out.println("concat second error="
                        + expected.getClass().getSimpleName());
            }
        }
    }

    private static void framedSegments()
            throws IOException, ClassNotFoundException {
        byte[] first = serializeOne("alpha");
        byte[] second = serializeOne("beta");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(first.length);
            out.write(first);
            out.writeInt(second.length);
            out.write(second);
        }

        try (DataInputStream source = new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            for (int index = 1; index <= 2; index++) {
                int length = source.readInt();

                // Chính sách giới hạn ví dụ: 1 tới 1024 byte/segment.
                if (length <= 0 || length > 1024) {
                    throw new IOException("Invalid segment length");
                }

                byte[] segment = new byte[length];
                source.readFully(segment);

                try (ObjectInputStream in = new ObjectInputStream(
                        new ByteArrayInputStream(segment))) {
                    System.out.println("framed " + index
                            + "=" + in.readObject());
                }
            }
        }
    }

    public static void main(String[] args)
            throws IOException, ClassNotFoundException {
        byte[] header = serializeOne("x");

        System.out.printf("header=%02X %02X %02X %02X%n",
                header[0] & 0xFF,
                header[1] & 0xFF,
                header[2] & 0xFF,
                header[3] & 0xFF);

        singleStream();
        badConcatenation();
        framedSegments();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ObjectStreamHeaderDemo.java
java ObjectStreamHeaderDemo
~~~

Stdout trên JDK 17+ với định dạng Java Serialization chuẩn:

~~~text
header=AC ED 00 05
single first=alpha
single second=beta
concat first=alpha
concat second error=StreamCorruptedException
framed 1=alpha
framed 2=beta
~~~

Giải thích từng phần:

1. `serializeOne("x")` tạo stream mới, do đó bốn byte đầu là `AC ED 00 05`. Chương trình in hex không phụ thuộc dữ liệu string.
2. `singleStream()` giữ một writer trong cả hai lệnh `writeObject`, cho phép một reader gọi `readObject` hai lần.
3. `badConcatenation()` nối hai byte array, mỗi array đã có header riêng. Reader đầu tiên đọc `alpha`, nhưng lần đọc kế tiếp gặp header thứ hai ở giữa stream và ném `StreamCorruptedException`.
4. `framedSegments()` ghi mỗi segment kèm `int` chiều dài, giới hạn dung lượng và đọc đúng `length` byte vào một mảng độc lập. Mỗi `ObjectInputStream` đọc header ở đầu segment tương ứng, vì vậy cả `alpha` và `beta` đều được giải mã.
5. `DataInputStream.readFully(...)` đòi hỏi đủ byte của segment; nếu nguồn bị cắt cụt thì ném `EOFException`, không âm thầm xem một segment thiếu byte là hợp lệ.
6. Mã không mở file, không yêu cầu quyền truy cập filesystem. Một triển khai lưu trữ thật cần tự thiết kế ghi append atomically theo record, checksum/commit marker nếu yêu cầu, và chính sách phục hồi record cuối đang ghi dở.

**Không lấy demo này làm hướng dẫn deserialize dữ liệu tùy ý từ bên ngoài.** Dữ liệu `ObjectInputStream` từ nguồn không đáng tin có thể nguy hiểm; phần an toàn được giải thích ở mục riêng bên dưới.

## 7. Một vấn đề khác: object handles và tham chiếu lặp

Header không phải trạng thái duy nhất của object stream. `ObjectOutputStream` còn có **handle table** để biết object nào đã được ghi và có thể ghi tham chiếu tới cùng object thay vì serialize lại toàn bộ.

Ví dụ:

~~~java
out.writeObject(someObject);
out.writeObject(someObject);
~~~

Nếu là **cùng object instance**, lần thứ hai có thể ghi một **back-reference**. Khi đọc lại, hai lời gọi `readObject()` có thể trả về **chính cùng object instance** trong JVM nhận, không phải hai bản sao độc lập.

Điều này quan trọng khi nội dung object được sửa giữa hai lần ghi:

~~~text
Object trong RAM: status = "old"
writeObject(object)

Object trong RAM: status = "new"
writeObject(object)  // có thể chỉ ghi tham chiếu tới object đã serialize

Reader có thể tiếp tục thấy status = "old" ở cả hai lần đọc!
~~~

Đây là hành vi theo cơ chế giữ identity, không phải bug của charset hay flush.

### Dùng reset() khi cần loại bỏ lịch sử reference

`ObjectOutputStream.reset()` là thao tác đặc biệt: nó phát một token reset vào **cùng object stream**, đồng thời xóa trạng thái tham chiếu đối tượng đã giữ ở writer để object về sau được serialize như mới.

~~~java
out.writeObject(object);
object.setState("new");
out.reset();
out.writeObject(object);
~~~

Đây là mảnh code minh họa với một kiểu object giả định có method `setState`. Lệnh `reset()` **không ghi lại stream header `AC ED 00 05`**. Nó tạo một điểm reset hợp lệ **bên trong** giao thức serialization, và `ObjectInputStream` tương ứng hiểu token này để reset trạng thái handle khi đọc tiếp.

Hạn chế: không được `reset()` trong khi đang thực hiện một lời gọi serialize object lồng nhau (có thể bị từ chối theo hợp đồng). `reset()` còn làm giảm lợi ích chia sẻ class descriptor/reference, có thể tăng kích thước output; không nên reset sau mọi object nếu không cần.

### Phân biệt reset, flush và mở stream mới

| Hành động | Header mới? | Handle table | Công dụng |
| --- | --- | --- | --- |
| `out.flush()` | Không | Giữ nguyên | Đẩy byte đang đệm |
| `out.reset()` | Không, ghi token reset trong luồng hiện tại | Xóa trạng thái tham chiếu | Ghi lại những object đã từng được serialize |
| `out.writeUnshared(obj)` | Không | Có ngữ nghĩa riêng về root unshared | Hạn chế chia sẻ root object theo hợp đồng |
| `new ObjectOutputStream(raw)` | Có | Khởi tạo trạng thái mới | Bắt đầu **stream độc lập** |
| `new ObjectInputStream(raw)` | Đòi đọc header mới tại vị trí bắt đầu | Khởi tạo bảng tham chiếu mới | Đọc **stream độc lập** |

**Không dùng `reset()` như “cách tự động cứu mọi file append hỏng”** nếu file đã có header lặp. Reset là một phần của writer đã tồn tại và đang phát token đúng ngữ cảnh; nó không tự loại bỏ những header thừa đã ghi.

## 8. Ví dụ thứ hai: reset để không tái sử dụng trạng thái cũ

Ví dụ này tách riêng khỏi chương trình trước, minh họa object reference và snapshot sau `reset()`. Lưu file `ObjectStreamResetDemo.java`:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class ObjectStreamResetDemo {
    private static final class Box implements Serializable {
        private static final long serialVersionUID = 1L;

        String value;

        Box(String value) {
            this.value = value;
        }
    }

    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Box box = new Box("old");

        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(box);

            box.value = "new";
            out.writeObject(box); // Thường chỉ phát tham chiếu.

            out.reset();          // Không ghi header mới.
            out.writeObject(box); // Ghi snapshot mới.
        }

        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            Box first = (Box) in.readObject();
            Box second = (Box) in.readObject();
            Box third = (Box) in.readObject();

            System.out.println("first=" + first.value);
            System.out.println("second=" + second.value);
            System.out.println("third=" + third.value);
            System.out.println("same before reset="
                    + (first == second));
            System.out.println("same across reset="
                    + (first == third));
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ObjectStreamResetDemo.java
java ObjectStreamResetDemo
~~~

Stdout:

~~~text
first=old
second=old
third=new
same before reset=true
same across reset=false
~~~

Kết quả cho thấy **hai lần viết cùng instance** trước reset không đương nhiên tạo hai snapshot. Sau `reset()`, cùng đối tượng nguồn được serialize như một đối tượng mới trên stream, và reader tạo instance mới ở lần thứ ba.

`serialVersionUID` trong ví dụ giúp định nghĩa phiên bản class serialization cho `Box`. Nó **không** sửa vấn đề lặp header và không bảo đảm mọi thay đổi class tương thích khi đọc dữ liệu cũ.

## 9. Vì sao sửa file append bằng cách bỏ header mới vẫn nguy hiểm?

Một số ví dụ trên mạng tạo subclass của `ObjectOutputStream` rồi override `writeStreamHeader()` để không ghi header sau lần đầu. Cách này có thể khiến file trông như có **một header ở đầu**.

Tuy nhiên, **chỉ bỏ header không đủ để tái lập tính nhất quán của stream**. Một writer mới có bảng handles riêng bắt đầu từ đầu, trong khi reader của stream hiện hữu đã tích lũy handles của các object trước. Khi writer mới ghi back-reference, class descriptor và các object phụ thuộc nhau, các handle có thể không còn khớp với trạng thái mà reader mong đợi.

Có những biến thể override phát **reset token** để tái đồng bộ handle table, nhưng đó là một thiết kế protocol tùy biến, phụ thuộc ràng buộc vòng đời và tính tương thích cụ thể. Không nên quảng bá "override writeStreamHeader rồi append" như một **công thức mặc định an toàn cho mọi object graph, mọi phiên ghi và mọi ứng dụng**.

Để lưu lịch sử nhiều phiên độc lập, ưu tiên một trong các mô hình rõ ràng:

- **Một stream liên tục được giữ mở và quản lý bởi một owner** nếu vòng đời ghi chỉ ở trong cùng phiên.
- **Nhiều segment có length-prefix**, mỗi segment có header và `ObjectInputStream` riêng.
- **Một file riêng cho mỗi snapshot/batch**, kèm manifest/index nếu cần.
- **Một định dạng record rõ ràng** không phụ thuộc handle table của Java Serialization (ví dụ protocol có schema và framing riêng) khi dữ liệu phải được append lâu dài qua nhiều phiên bản ứng dụng.

### Append trên filesystem không phải transaction

Ngay cả khi framing đã đúng, ghi append trong lúc process crash có thể để lại record cuối chỉ có một phần header độ dài hoặc một phần payload. Reader cần phát hiện segment thiếu, từ chối hoặc bỏ qua phần chưa hoàn chỉnh theo chính sách phục hồi; không giả định mọi `write(...)` và `close()` cùng tạo thành một commit atomically.

## 10. Quản lý thứ tự khởi tạo khi truyền object qua pipe/socket

Đối với hai đầu kết nối dùng object streams hai chiều, có một tình huống dễ deadlock:

~~~text
Peer A: tạo ObjectInputStream → đợi header từ B
Peer B: tạo ObjectInputStream → đợi header từ A

Cả hai cùng chờ, chưa ai tạo hoặc flush ObjectOutputStream.
~~~

Lý do là constructor `ObjectInputStream` có thể **chặn cho tới khi nhận và kiểm tra header**.

Trong protocol đối xứng, một lựa chọn thông thường là:

1. Tạo `ObjectOutputStream` ở hai bên.
2. `flush()` writer để header được đẩy tới transport.
3. Tạo `ObjectInputStream` ở hai bên.
4. Sau đó thực hiện thứ tự gửi/nhận có giao thức rõ ràng và chính sách timeout.

Tùy stream/transport, có thể cần flush byte khi ứng dụng gửi một object mới để peer nhận được kịp thời. **Không gọi `new ObjectOutputStream` cho mỗi message gửi**: sẽ lại có header mới và làm hỏng mô hình một stream liên tục.

Giao thức mạng cần đặt giới hạn kích thước, timeout, cách đóng và xử lý mất kết nối. Không sử dụng ObjectInputStream với byte nhận từ client tùy ý mà thiếu deserialization filter/boundaries.

## 11. Ngoại lệ có thể xuất hiện và cách đọc nguyên nhân

| Ngoại lệ | Khi nào có thể gặp | Ý nghĩa thực tế |
| --- | --- | --- |
| `StreamCorruptedException` | Header sai, token serialization không hợp lệ, header thứ hai giữa luồng | Định dạng byte không đúng chỗ hoặc stream đã hỏng |
| `EOFException` | Hết byte khi đang mong dữ liệu/hay segment | Luồng bị cắt cụt hoặc chương trình đã đọc vượt cấu trúc được thiết kế |
| `ClassNotFoundException` | Reader không tìm thấy class cần deserialize | Phải giải quyết tương thích class/module và classpath |
| `InvalidClassException` | Class không tương thích theo serialization rules/serialVersionUID hoặc bị filter từ chối | Thay đổi schema, version hoặc chính sách an toàn |
| `NotSerializableException` | Writer gặp object không implements `Serializable` và không có cách serialize hợp lệ | Kiểm tra object graph và chiến lược serialization |
| `OptionalDataException` | Reader dùng readObject/read primitive không khớp thứ tự/protocol data trong trường hợp thích hợp | Sai thứ tự đọc hoặc định dạng object/primitive |
| `IOException` | Lỗi stream nền, file, socket hoặc protocol I/O | Phân biệt với lỗi class/schema |

Đừng xử lý mọi `StreamCorruptedException` bằng cách bắt rồi bỏ qua một byte `0xAC` để "tiếp tục". Serialization là định dạng có cấu trúc và tham chiếu nội bộ; bỏ byte tùy tiện làm parser càng mất đồng bộ.

Nếu đã có file bị append sai:

1. Giữ bản gốc để phục hồi; không ghi đè trực tiếp.
2. Xác định chính xác các **segment** hợp lệ nếu có ranh giới từ nguồn tin cậy (như manifest/length-prefix hoặc snapshot riêng).
3. Dùng reader **trên từng segment độc lập** để trích dữ liệu.
4. Tạo file/định dạng mới có framing rõ, sau đó đối chiếu số object và nội dung.
5. Nếu không thể xác định boundary đáng tin, đừng giả định có thể phục hồi toàn bộ object graph chỉ bằng tìm chuỗi byte magic.

## 12. Bảo mật: không deserialize dữ liệu không tin cậy tùy tiện

Java Object Serialization có thể khởi tạo đồ thị class và thực thi hook deserialization của class, như `readObject`/`readResolve`, trong quá trình khôi phục object. Vì thế **không** dùng `ObjectInputStream` để đọc byte từ nguồn không tin cậy mà không có chính sách ràng buộc chặt.

Các biện pháp thiết kế:

- Chỉ chấp nhận dữ liệu từ nguồn được xác thực và kiểm soát.
- Cấu hình `ObjectInputFilter` hoặc cơ chế filter ở phạm vi ứng dụng để giới hạn class/type, số lượng reference, độ sâu đồ thị, số byte và kích thước mảng theo các giới hạn phù hợp.
- Giới hạn kích thước segment/file, timeout và memory budget **trước khi deserialize**.
- Không coi `serialVersionUID`, stream header hay checksum là xác thực chống tấn công.
- Nếu định dạng phải công khai lâu dài hoặc cần tương thích đa ngôn ngữ, cân nhắc JSON/CBOR/Protocol Buffers/Avro (với parser và schema có giới hạn) thay vì Java native serialization.

### Ví dụ thiết lập ObjectInputFilter

Đoạn minh họa có một `ObjectInputStream in` đã tạo và chỉ mong dữ liệu `String`:

~~~java
import java.io.ObjectInputFilter;

ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
        "maxdepth=4;maxrefs=32;maxbytes=8192;!*");
in.setObjectInputFilter(filter);
~~~

Đây là mảnh code minh họa, không phải chương trình hoàn chỉnh. Mẫu `!*` từ chối những class chưa được phép theo cú pháp filter pattern; tuy nhiên **String được biểu diễn theo token đặc biệt và không phải lúc nào cũng qua cùng nhánh kiểm tra class**. Vì vậy, không dùng đúng filter trên để kết luận mọi định dạng String đều được kiểm soát hoàn toàn chỉ bằng `!*`. Với object graph thực tế, thiết kế allowlist có chủ đích và kiểm tra đúng những class/framework cần deserialize; xác minh filter bằng test trên payload hợp lệ và không hợp lệ.

**Quan trọng:** filter giới hạn rủi ro, không biến dữ liệu serialization tùy ý thành an toàn. Luôn kết hợp giới hạn đầu vào, nguồn tin cậy và kiểu dữ liệu hợp đồng rõ ràng.

## 13. Checklist thiết kế lưu trữ object bền vững

Khi chuẩn bị lưu nhiều object qua nhiều phiên:

1. **Xác định một stream hay nhiều segment:** một OOS duy trì từ đầu tới cuối hay mỗi segment có framing riêng.
2. **Định nghĩa schema của segment:** số object, loại dữ liệu mong đợi, version, field bắt buộc.
3. **Giới hạn kích thước:** cả payload serialized, graph depth và số đối tượng.
4. **Quản lý ownership:** chỉ một writer theo thiết kế; tránh hai thread tự tạo OOS trên cùng file/transport.
5. **Khả năng đọc sau crash:** kiểm tra độ dài segment, checksum/commit marker nếu cần; bỏ phần cuối bị cắt cụt theo quy tắc.
6. **Tương thích phiên bản class:** chủ động quản lý serialVersionUID hoặc định dạng có schema độc lập.
7. **Đóng/flush đúng lúc:** tránh đóng stream nền mà thành phần khác còn đang dùng; không lấy flush làm dấu kết thúc message nếu chưa có framing.
8. **Khả năng khôi phục:** lưu manifest hoặc index ranh giới nếu ứng dụng cần truy cập ngẫu nhiên.
9. **An toàn deserialization:** filter, allowlist và nguồn tin cậy.
10. **Kiểm thử append và restart:** tạo hai batch tại hai phiên khác nhau rồi kiểm tra đọc lại toàn bộ, không chỉ đọc object đầu.

## 14. Các lỗi thường gặp

**Lỗi 1 — Mở ObjectOutputStream mới với FileOutputStream(append=true).** Một header mới được ghi vào giữa file; một ObjectInputStream liên tục có thể ném `StreamCorruptedException` ở lần đọc tiếp.

**Lỗi 2 — Tưởng flush() tạo điểm bắt đầu stream mới.** `flush()` chỉ đẩy byte, không ghi header/reset handle table và không thay thế record framing.

**Lỗi 3 — Mở ObjectInputStream mới ở một offset bất kỳ.** Constructor đòi stream header hợp lệ tại offset đầu; byte object content không phải header.

**Lỗi 4 — Chỉ bỏ header khi append.** Writer mới có handle table riêng; reader cũ có thể vẫn giữ handle table trước đó, gây tham chiếu sai.

**Lỗi 5 — Nhầm cùng một object instance với hai snapshot độc lập.** `writeObject(obj)` lặp có thể chỉ ghi reference. Dùng `reset()` hoặc mô hình dữ liệu snapshot thích hợp khi thực sự cần serialize trạng thái mới.

**Lỗi 6 — Dùng EOF như giao thức không có trường hợp lỗi.** EOF cũng có thể báo dữ liệu bị cắt cụt. Với record archive/log cần length-prefix, checksum hoặc commit semantics nếu phải phát hiện ghi dở.

**Lỗi 7 — Tưởng serialize object đồng nghĩa phù hợp lâu dài hoặc đa ngôn ngữ.** Dữ liệu Java native serialization phụ thuộc định dạng/class, không phải schema cross-language ổn định mặc định.

**Lỗi 8 — Tạo ObjectInputStream ở cả hai đầu socket trước khi tạo/flush ObjectOutputStream.** Cả hai bên có thể chặn chờ header từ bên kia.

**Lỗi 9 — Bỏ qua readObject filter.** Deserialization từ nguồn không tin cậy có nguy cơ an toàn lớn; giới hạn class và cấu trúc graph.

**Lỗi 10 — Thấy StreamCorruptedException thì tự bỏ byte magic để đọc tiếp.** Không có bảo đảm rằng tìm được ranh giới hợp lệ nếu thiếu framing; cách này có thể làm hỏng dữ liệu hơn.

## 15. Tự kiểm tra

1. Một `ObjectOutputStream` mới ghi gì vào raw stream khi tạo? **Serialization stream header**, thường bắt đầu bằng `AC ED 00 05`.
2. Viết `writeObject(a)` rồi `writeObject(b)` trên **cùng OOS** có cần một header thứ hai không? **Không**.
3. `FileOutputStream(file, true)` có bảo đảm OOS mới tiếp nối handle table cũ không? **Không**; append chỉ là thêm byte vật lý.
4. Vì sao một OIS đọc object thứ nhất rồi lỗi với header thứ hai? Vì parser đang mong **object content token**, không mong khởi đầu stream mới ở giữa.
5. Nếu cần nhiều object stream độc lập trong một file, hãy tổ chức thế nào? **Ghi framing rõ (ví dụ length-prefix), rồi tạo OIS riêng cho từng segment được giới hạn**.
6. Có thể giải quyết lỗi append chỉ bằng `flush()` không? **Không**.
7. `ObjectOutputStream.reset()` có ghi header mới không? **Không**; nó phát token reset và làm mới trạng thái tham chiếu của cùng object stream.
8. Viết cùng object hai lần khi object bị sửa ở giữa có chắc tạo hai snapshot không? **Không**; lần hai có thể là reference tới snapshot cũ.
9. Vì sao OIS constructor trên pipe có thể chặn? Vì cần **đọc stream header** trước khi hoạt động; phía ghi cần tạo/flush header tương ứng.
10. Có nên deserialize bytes từ network không tin cậy với OIS mặc định không? **Không**; cần nguồn tin cậy, filter, giới hạn và cân nhắc một định dạng an toàn hơn.
11. `serialVersionUID` có sửa được header thừa không? **Không**; nó phục vụ nhận dạng/tương thích class trong serialization.
12. Nếu một segment length-prefix mới ghi được một phần rồi JVM crash, reader nên làm gì? **Phát hiện segment thiếu và áp dụng quy tắc phục hồi/bỏ đoạn cuối**, không ghép tùy ý với record tiếp theo.

**Ghi nhớ:** `ObjectOutputStream` là **một serialization stream có header và trạng thái handles**, không đơn thuần là một hàm “biến object thành vài byte”. Mở writer mới để append vào cùng file sẽ tạo **header giữa stream**, khiến reader liên tục dễ gặp `StreamCorruptedException`. Hãy **giữ một OOS cho một luồng logic**, hoặc **đóng khung các stream độc lập với độ dài rõ ràng** và đọc mỗi segment bằng OIS riêng. Phân biệt `flush()`, `reset()` và stream mới; đồng thời quản lý class compatibility, bản ghi ghi dở và an toàn deserialization.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serialization-basics-011">Khác biệt với định dạng trao đổi có schema độc lập Java</a>

<details>
<summary>Xem chi tiết</summary>

# Khác biệt với định dạng trao đổi có schema độc lập Java

## 1. Tuần tự hóa một đối tượng Java khác với thiết kế giao thức trao đổi

**Serialization** chuyển trạng thái dữ liệu thành byte/text để lưu trữ hoặc truyền đi; **deserialization** đọc biểu diễn đó trở lại dữ liệu hay đối tượng. Java có sẵn `ObjectOutputStream`, `ObjectInputStream` và interface `Serializable`. Chúng triển khai **Java Object Serialization Stream Protocol**, chủ yếu phục vụ việc khôi phục các đối tượng/đồ thị đối tượng Java.

Trong khi đó, một **định dạng trao đổi có schema độc lập Java** (chẳng hạn JSON + hợp đồng dữ liệu, Protocol Buffers, Avro hoặc binary format được đặc tả đầy đủ) nhằm định nghĩa message có thể được Java, Go, Python hay JavaScript tạo và hiểu mà không cần sử dụng chính class Java của bên gửi.

Cần phân biệt hai mục tiêu:

1. **Object persistence/transport nội bộ Java:** khôi phục state và đồ thị tham chiếu theo quy tắc Java; lớp đọc phải tương thích với serial form của lớp ghi.
2. **Interoperability và data contract:** hai ứng dụng, có thể khác ngôn ngữ và chu kỳ phát hành, trao đổi các trường nghiệp vụ thông qua field identifiers, types, optional/default semantics và wire encoding được thỏa thuận.

**Điểm chính:** việc Java ghi một `byte[]` không khiến định dạng mặc nhiên độc lập Java. Ngược lại, một codec được viết bằng Java vẫn có thể tuân thủ schema độc lập Java nếu định dạng byte được mô tả/kiểm thử ngoài ngôn ngữ ấy.

## 2. Cơ chế Java Object Serialization

Ví dụ một class:

~~~java
import java.io.Serial;
import java.io.Serializable;

final class Profile implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int id;
    private final String name;

    Profile(int id, String name) {
        this.id = id;
        this.name = name;
    }
}
~~~

Đây là class minh họa, không phải chương trình chạy độc lập. `Serializable` là marker interface; Java Object Serialization lưu dữ liệu theo serial form của class. Mặc định các instance fields đủ điều kiện được tuần tự hóa; `static` fields không phải trạng thái của instance, `transient` fields bị loại khỏi default serial form. Những class/hook đặc biệt có quy tắc riêng.

Để ghi/đọc đối tượng:

~~~java
try (ObjectOutputStream out = new ObjectOutputStream(destination)) {
    out.writeObject(profile);
}

try (ObjectInputStream in = new ObjectInputStream(source)) {
    Profile restored = (Profile) in.readObject();
}
~~~

Đây là **mã minh họa**: `destination` và `source` là hai stream liên quan tới cùng bộ bytes, `profile` đã được khai báo, có imports và xử lý `IOException`/`ClassNotFoundException`. Các `try-with-resources` sẽ đóng stream được bọc: phù hợp khi code sở hữu stream, nhưng cần xem hợp đồng nếu stream được mượn từ caller.

Khi ghi, Java protocol có thể chứa:

- Stream header và các dấu điều khiển protocol.
- Class descriptors chứa định danh/serial form của những lớp liên quan.
- Dữ liệu fields, tham chiếu object và handles duy trì cấu trúc đồ thị/alias.
- Dữ liệu tùy biến từ `writeObject` hoặc các cơ chế substitution, externalization nếu class hỗ trợ.

Khi đọc, `ObjectInputStream` giải quyết class trong Java runtime, kiểm tra tương thích serial form rồi dựng đối tượng. Những hook như `readObject`, `readResolve` có thể chạy logic được định nghĩa bởi class; không thể coi đây chỉ là “đọc JSON thành map”.

Java stream chuẩn có header `AC ED 00 05` ở đầu (hex). Header không giải quyết vấn đề đa ngôn ngữ: một consumer không chạy Java vẫn phải triển khai đầy đủ Java serialization semantics và các class/graph rules để đọc loại dữ liệu này.

### Những dữ liệu không tự được khôi phục hữu ích

Serialization không biến resource trong JVM thành resource sống ở process khác. Ví dụ socket đang kết nối, thread đang chạy, file descriptor hoặc lock native không được “nhân bản” có ý nghĩa chỉ vì một class implement `Serializable`. Các field `transient` cần được tái thiết lập khi cần. Với object graph có tham chiếu tới loại không serializable, có thể gặp `NotSerializableException`.

## 3. serialVersionUID không phải schema version của một API

`serialVersionUID` thuộc về **serializable class Java** và dùng trong kiểm tra compatibility giữa dữ liệu trong stream với class local khi giải mã. Nếu không khai báo, Java có thể tính default UID dựa trên chi tiết class; vì thế việc khai báo tường minh thường hợp lý cho class chủ động dùng Java Serialization.

Tuy nhiên:

- Giữ nguyên UID **không bảo đảm mọi thay đổi class đều tương thích**. Thay đổi loại field, class hierarchy hoặc custom serialization logic có thể phá việc đọc hoặc semantics.
- Đổi package/class name có thể làm class resolution thất bại dù dữ liệu nghiệp vụ nhìn có vẻ giống.
- UID không mô tả field number, kiểu dữ liệu và default theo một schema có thể triển khai độc lập trong Python hay Go.
- Không nên “chữa” mọi `InvalidClassException` bằng việc chỉnh UID cho trùng rồi bỏ qua nguyên nhân.

Ngược lại, **schema version** của một message là một phần của hợp đồng trao đổi mà các ngôn ngữ đều có thể hiểu. Một DTO schema `Profile v2` có thể thêm field `points` mà không đòi hỏi class Java nội bộ giữ nguyên package, superclass hay business methods.

Ngay cả schema version cũng **không tự tạo tương thích**; phải định nghĩa behavior khi dữ liệu mới có field chưa biết, dữ liệu cũ thiếu field, hoặc một trường bị đổi nghĩa.

## 4. Thế nào là schema độc lập Java?

Một schema mô tả **hình dạng dữ liệu và quy tắc diễn giải** mà không cần biết runtime class của producer. Ví dụ:

~~~text
Message: Profile
id      : signed 32-bit integer, required by business
name    : UTF-8 text, non-empty, max 1024 encoded bytes
points  : signed 64-bit integer, default 0 if omitted by agreed schema
Version : 1
Unknown-field handling, null/absence and invalid-input behavior are explicit
~~~

Đây là đặc tả khái niệm, không phải cú pháp của một schema framework cụ thể. Dữ liệu wire cần quy định rõ thêm: encoding, byte order nếu binary, length framing, numeric overflow, nullability, trường chưa biết, version, timestamp/unit và điều kiện xác thực.

Khác với một serialized object Java, **consumer không cần có class `com.example.Profile`**. Consumer Python có thể đọc ID/points/name vào struct hoặc dictionary của nó, miễn cả hai thực thi đúng schema/wire contract.

Không phải mọi định dạng tự nó đều bao gồm schema đầy đủ:

- JSON cung cấp syntax và loại dữ liệu cơ bản nhưng không tự ép schema; thường kết hợp với OpenAPI, JSON Schema và kiểm tra ứng dụng.
- Protobuf định nghĩa message/field numbers trong `.proto`, codec nhiều ngôn ngữ.
- Avro sử dụng writer/reader schemas trong cơ chế schema resolution; binary datum đơn lẻ có thể cần schema truyền riêng, trong khi object container file có định dạng header chứa schema.
- Custom binary format có thể độc lập Java nếu **bản đặc tả** được công bố và được nhiều implementation kiểm chứng. `DataOutputStream` tự nó không phải một schema language.

## 5. Bảng đối chiếu cốt lõi

| Tiêu chí | Java Object Serialization | Schema độc lập Java |
| --- | --- | --- |
| Trọng tâm | Object/graph state Java | Message/DTO contract |
| Gắn class Java của producer | Có, thông qua serial form/class resolution | Không bắt buộc |
| Wire format | Java Object Serialization protocol | JSON/Protobuf/Avro/custom specification |
| Hỗ trợ Go/Python/JS | Không mặc nhiên | Thiết kế để hỗ trợ khi codec/schema phù hợp |
| Version | `serialVersionUID`, quy tắc Java compatibility | Schema/wire version, field number/name, rules |
| Object identity/cycle | Protocol có thể bảo toàn alias/object graph | Thường cần mô hình hóa graph rõ nếu có |
| Lưu trữ lâu dài | Phụ thuộc code/serial form Java | Có thể quản lý schema/migration ổn định hơn |
| Security của input | `readObject` có rủi ro object/gadget hooks | Vẫn cần parse limits/validation, nhưng thường không tái tạo class Java tùy ý |
| Debug | Binary và phụ thuộc Java protocol | JSON dễ đọc; binary schema codecs có tooling |
| Hiệu năng/kích thước | Phụ thuộc object graph/protocol | Phụ thuộc codec và dữ liệu, phải benchmark |
| Schema evolution | Phụ thuộc thay đổi class/serial form | Có quy tắc evolution được quản lý rõ |
| Coupling | Data format dễ dính model Java nội bộ | Có thể tách external DTO khỏi domain model |

Không có định dạng nào tự động “an toàn/nhanh/tương thích tuyệt đối”. Lợi thế của schema độc lập là **hợp đồng dữ liệu có thể review và kiểm thử xuyên ngôn ngữ, phát triển tách khỏi chi tiết class Java**.

## 6. JSON có schema: dễ dùng nhưng phải kiểm soát kiểu

Một bản tin JSON:

~~~json
{
  "schemaVersion": 1,
  "id": 7,
  "name": "Lan",
  "points": 120
}
~~~

Java có thể tạo nó bằng JSON library/codec; không cần dùng `ObjectOutputStream`. Hợp đồng cần quy định:

- `id` có bắt buộc hay không, phạm vi hợp lệ là gì.
- `points` là signed 64-bit integer hay loại khác. JavaScript `Number` không biểu diễn chính xác mọi số nguyên lớn hơn `2^53-1`; schema liên ngôn ngữ cần chiến lược phù hợp cho long/decimal.
- `name` có thể null, rỗng, chứa Unicode normalization nào, giới hạn bytes/chars thế nào.
- Field chưa biết có bị reject/bỏ qua không; thiếu field có default ra sao.
- Timestamp/timezone, money units, định danh và enum values đều cần quy tắc nhất quán.
- Parser phải giới hạn payload size/nesting và validate trước khi map sang domain.

**JSON không đồng nghĩa JSON Schema.** JSON Schema/OpenAPI và contract tests mô tả/kiểm tra những yêu cầu ngoài cú pháp JSON. Decoder đọc JSON hợp lệ nhưng không thỏa business rules vẫn phải từ chối.

## 7. Protocol Buffers: field numbers thay cho Java class identity

Một file `profile.proto` có thể khai báo:

~~~proto
syntax = "proto3";
package example.profile.v1;

message Profile {
  int32 id = 1;
  string name = 2;
  int64 points = 3;
}
~~~

Compiler/runtime Protobuf tạo API cho nhiều ngôn ngữ. Ý nghĩa của field number là thiết yếu: `1` đại diện field `id` trên wire, `2` là `name`, `3` là `points`. Consumer Go/Java/Python có thể diễn giải cùng wire format bằng schema tương thích.

Để tiến hóa schema an toàn:

1. Tránh đổi field number cho field đã triển khai.
2. Khi bỏ field, dùng `reserved` cho số/tên thích hợp để tránh tái sử dụng với ý nghĩa khác.
3. Với field mới, xác định behavior của consumer cũ khi bỏ qua field chưa biết và business logic có chấp nhận không.
4. Thận trọng khi đổi loại field, enum, oneof hoặc field presence.
5. Trong proto3, phân biệt default scalar value với explicit presence; nếu cần, dùng `optional` hay loại phù hợp.
6. Duy trì golden test vectors và backward/forward compatibility tests với các consumer thật.

Protobuf không tự cung cấp authorization, input trust, transaction hay schema registry. Các bài toán đó cần giải pháp tầng ứng dụng.

## 8. Apache Avro: writer schema và reader schema

Một schema Avro đơn giản:

~~~json
{
  "type": "record",
  "name": "Profile",
  "namespace": "example.profile",
  "fields": [
    {"name": "id", "type": "int"},
    {"name": "name", "type": "string"},
    {"name": "points", "type": "long", "default": 0}
  ]
}
~~~

Avro có quy tắc đối chiếu **writer schema** (đã dùng khi ghi) và **reader schema** (được consumer mong đợi), cho phép một số kiểu thay đổi phù hợp mà không buộc các lớp Java giống nhau.

Cần hiểu giới hạn:

- Một Avro **binary datum** đơn lẻ thường không tự chứa toàn bộ writer schema. Khi lưu message trên broker có thể cần schema registry/schema ID hoặc truyền metadata theo protocol.
- Avro object container file có cấu trúc riêng, header/schema metadata; không được suy luận mọi dạng Avro data đều giống file container.
- Thêm field có default, rename/alias, unions và type promotion phải theo quy tắc cụ thể. Thay đổi không tương thích có thể làm schema resolution thất bại.
- Mọi consumer vẫn phải hiểu **ý nghĩa** field; khả năng decode không bảo đảm semantics nghiệp vụ.

JSON, Protobuf và Avro đều là phương án có thể dùng trong hệ thống Java; lựa chọn tùy ecosystem, độ dễ quan sát, kích thước, versioning và performance benchmark.

## 9. Ví dụ Java 17+ hoàn chỉnh: hai cách ghi cùng Profile

Chương trình bên dưới viết cùng dữ liệu theo hai định dạng:

1. **Java Object Serialization**: `ObjectOutputStream` viết object có UID/class descriptor; `ObjectInputStream` đọc lại trong Java. Ví dụ có filter giới hạn class và tài nguyên đọc.
2. **Binary wire format được đặc tả ngoài Java class**: `DataOutputStream`/`DataInputStream` dùng header magic, version, fixed-width integers theo big-endian và length-prefixed UTF-8. Một decoder ở ngôn ngữ khác có thể triển khai từ bảng schema ở phần tiếp theo.

**Định dạng thứ hai là giao thức minh họa do bài tự đặt**, không phải một chuẩn thương mại hay Protobuf. Tính độc lập Java đến từ đặc tả bytes/validation chứ không phải vì dùng `DataOutputStream`.

Tạo tệp **`SchemaExchangeDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

public class SchemaExchangeDemo {
    private static final byte[] MAGIC = {'P', 'R', 'F', '1'};
    private static final int VERSION = 1;

    static final class Profile implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        final int id;
        final long points;
        final String name;

        Profile(int id, long points, String name) {
            this.id = id;
            this.points = points;
            this.name = name;
        }
    }

    static byte[] writeJava(Profile p) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(p);
        }
        return bytes.toByteArray();
    }

    static Profile readJava(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                "maxdepth=8;maxrefs=20;maxbytes=4096;" +
                "SchemaExchangeDemo$Profile;java.base/*;!*"));
            return (Profile) in.readObject();
        }
    }

    static byte[] writeWire(Profile p) throws IOException {
        byte[] name = p.name.getBytes(StandardCharsets.UTF_8);
        if (name.length > 1024) throw new IOException("Name too long");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.write(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(p.id);
            out.writeLong(p.points);
            out.writeInt(name.length);
            out.write(name);
        }
        return bytes.toByteArray();
    }

    static Profile readWire(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(bytes))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) throw new IOException("Bad magic");
            int version = in.readInt();
            if (version != VERSION) throw new IOException("Unsupported version");
            int id = in.readInt();
            long points = in.readLong();
            int length = in.readInt();
            if (length < 0 || length > 1024) throw new IOException("Bad length");
            byte[] nameBytes = new byte[length];
            in.readFully(nameBytes);
            if (in.read() != -1) throw new IOException("Trailing bytes");
            return new Profile(id, points,
                    new String(nameBytes, StandardCharsets.UTF_8));
        }
    }

    public static void main(String[] args) throws Exception {
        Profile original = new Profile(7, 120, "Lan");
        byte[] javaBytes = writeJava(original);
        byte[] wireBytes = writeWire(original);
        Profile restored = readJava(javaBytes);
        Profile exchanged = readWire(wireBytes);

        System.out.println("Java header: " +
                HexFormat.of().withUpperCase().formatHex(
                        Arrays.copyOf(javaBytes, 4)));
        System.out.println("Java roundtrip: " + restored.id + "|" +
                restored.name + "|" + restored.points);
        System.out.println("Wire magic: " +
                new String(Arrays.copyOf(wireBytes, 4), StandardCharsets.US_ASCII));
        System.out.println("Wire version: 1");
        System.out.println("Wire length: " + wireBytes.length);
        System.out.println("Wire roundtrip: " + exchanged.id + "|" +
                exchanged.name + "|" + exchanged.points);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SchemaExchangeDemo.java
java SchemaExchangeDemo
~~~

**Stdout chính xác**, đã biên dịch/chạy trên JDK:

~~~text
Java header: ACED0005
Java roundtrip: 7|Lan|120
Wire magic: PRF1
Wire version: 1
Wire length: 27
Wire roundtrip: 7|Lan|120
~~~

Giải thích:

- `ACED0005` là header của Java Serialization Stream Protocol, không cho biết schema trao đổi độc lập Java.
- `serialVersionUID=1L` được gắn với serial form của `SchemaExchangeDemo.Profile`; nó không phải version của hệ thống API đa ngôn ngữ.
- `ObjectInputFilter` trong ví dụ cho phép class cụ thể và giới hạn depth/references/bytes. Dù vậy **không được coi đây là bằng chứng ObjectInputStream an toàn cho dữ liệu không tin cậy**. Thực tế nên tránh Java native deserialization qua trust boundary.
- Nhánh wire ghi `PRF1` và version `1`; consumer bất kỳ ngôn ngữ nào có thể hiểu chúng nếu tuân theo byte layout được công bố.
- `writeInt`/`writeLong` ghi số theo **big-endian**; tên được mã hóa UTF-8, độ dài tính bằng **byte**, không tính Java `String.length()`.
- Tổng dung lượng là **4 + 4 + 4 + 8 + 4 + 3 = 27 bytes** với tên `Lan` có 3 byte UTF-8.
- Decoder minh họa kiểm tra magic, version, length tối đa 1024, bytes thiếu và trailing bytes. `readFully` ném lỗi khi stream kết thúc trước số byte yêu cầu.
- Decoder ví dụ dùng `new String(bytes, UTF_8)`. Điều này có thể thay thế malformed UTF-8 bằng ký tự replacement; hệ thống cần reject dữ liệu UTF-8 sai phải dùng `CharsetDecoder` với `CodingErrorAction.REPORT`.
- Ví dụ chưa áp đặt business validation (`id` phải dương, `name` không rỗng, `points` hợp lệ) và không chứng minh interop thực tế với Python/Go. Đó là những bài test cần thêm nếu triển khai.

### Bảng schema bytes của wire format v1

| Vị trí (byte offset) | Dung lượng | Kiểu | Nội dung |
| --- | ---: | --- | --- |
| 0–3 | 4 | ASCII | Magic `PRF1` |
| 4–7 | 4 | signed int32 big-endian | Version = 1 |
| 8–11 | 4 | signed int32 big-endian | `id` |
| 12–19 | 8 | signed int64 big-endian | `points` |
| 20–23 | 4 | signed int32 big-endian | UTF-8 byte length `0..1024` |
| 24 đến hết length | Biến đổi | UTF-8 | Bytes của `name`, không có NUL terminator |

Đây là định dạng **independent of Java implementation** nếu mọi producer/consumer cùng tuân thủ bảng. Nó **không tự có field tagging, unknown-field policy hay schema registry**. Nếu thay đổi layout v1, decoder cũ có thể từ chối trailing bytes; cần nâng version và code migration hoặc đổi sang format có field tags.

**Vì sao không dùng `DataOutputStream.writeUTF`?** Nó dùng **modified UTF-8** và một length prefix theo quy tắc Java, không phải trực tiếp mã hóa UTF-8 chuẩn mà các schema cross-language thường mô tả. Một contract liên ngôn ngữ phải ghi rõ encoding bytes và length prefix như ví dụ.

## 10. Backward compatibility và forward compatibility

Khi format/schema thay đổi, cần trả lời cả hai câu hỏi:

- **Backward compatibility:** reader mới đọc được dữ liệu do writer cũ tạo hay không?
- **Forward compatibility:** reader cũ đọc được dữ liệu do writer mới tạo hay không?

Với schema v1:

~~~text
Profile v1: id, name
Profile v2: id, name, points
~~~

Bảng tư duy:

| Reader / Writer | Rủi ro | Cách giảm |
| --- | --- | --- |
| Reader v2 ← Writer v1 | Không có points | Default/optional theo codec và ý nghĩa nghiệp vụ |
| Reader v1 ← Writer v2 | Có points chưa biết | Cơ chế bỏ qua unknown fields hoặc phiên bản tương thích |
| Reader mới ← Message rất cũ | Nhiều bước migration | Transitive compatibility/migration fixtures |
| Reader cũ ← Kiểu field bị đổi | Decoder hoặc business logic sai | Không đổi field number/meaning tùy tiện, dùng version mới |

**Có thể đọc được bytes không có nghĩa nghiệp vụ đúng.** Nếu field số đang chứa cents nhưng version mới diễn giải thành dollars, cả hai bên vẫn parse thành số nhưng kết quả sai. Cần phân biệt *wire compatibility* với *semantic compatibility*.

Với Protobuf, quy tắc field number/tagged field giúp bỏ qua một số field chưa biết nhưng không cứu được mọi thay đổi. Với Avro, writer-reader schema resolution hỗ trợ những thay đổi tương thích. Với JSON, behavior khi field unknown/default phụ thuộc JSON decoder và hợp đồng. Với custom binary format trong demo, reader v1 còn từ chối trailing bytes, nên đơn giản thêm field vào cuối **không forward compatible**.

Một kế hoạch nâng phiên bản an toàn cần golden messages, upgrade/downgrade tests, và thống kê consumer còn chạy ở các phiên bản cũ trước khi phát hành breaking change.

## 11. Bảo mật: vì sao ObjectInputStream không nên nhận payload tùy ý?

Native Java deserialization không chỉ parse primitive fields. `ObjectInputStream.readObject()` có thể tái tạo các instance, xử lý object graph, gọi các hook có side effects và kích hoạt class/library code. Điều này tạo nguy cơ gadget chain, DoS hoặc hành vi ngoài ý muốn nếu byte stream do attacker kiểm soát.

Các nguyên tắc:

1. **Không dùng Java native serialized objects làm định dạng đầu vào mặc định cho public HTTP/socket/message queues không tin cậy.**
2. Thay bằng DTO/schema và codec giới hạn kiểu dữ liệu, sau đó kiểm tra business validation trước khi sử dụng.
3. Nếu buộc phải hỗ trợ dữ liệu Java Serialization legacy, dùng `ObjectInputFilter`/filter factory theo JDK, allowlist class, giới hạn depth, refs, bytes, array lengths; đánh giá toàn bộ classpath.
4. Filter không phải sandbox và không loại bỏ mọi nguy cơ; cố gắng cô lập/loại bỏ đường giải Java object từ input không tin cậy.
5. JSON/Protobuf/Avro cũng cần giới hạn payload size, nesting, time và bảo vệ parser; codec không tự tạo authentication hay authorization.
6. Đừng deserializing dữ liệu cũ không rõ nguồn chỉ để “thử xem có đọc được không”; môi trường migration phải được bảo vệ.

Một số lỗi Java Serialization:

| Ngoại lệ | Tình huống thường thấy |
| --- | --- |
| `NotSerializableException` | Ghi đối tượng không đủ điều kiện serialization |
| `InvalidClassException` | Serial form không tương thích hoặc lớp bị từ chối |
| `ClassNotFoundException` | Class Java cần giải không có ở reader |
| `StreamCorruptedException` | Stream header/protocol data không hợp lệ |
| `ClassCastException` | Object được khôi phục khác expected type |
| `EOFException`/`OptionalDataException` | Thiếu dữ liệu hoặc đọc sai thứ tự |
| `IOException` | Lỗi đọc/ghi và những trường hợp khác |

Không sửa UID bừa để che `InvalidClassException`, không tắt filter chỉ vì một dữ liệu mẫu không qua được, và không nuốt ngoại lệ rồi xem payload đã được xử lý.

## 12. Lưu trữ lâu dài và tiến hóa mô hình

Một file Java serialized object được lưu năm trước có thể không còn đọc được sau khi đổi package/class, thay field types, sửa hierarchy hoặc thay custom serialization code. Ngay cả khi runtime đọc được, semantic của field có thể đã thay đổi.

Khi cần lưu lâu hoặc có nhiều consumer:

- Tách **external message schema** khỏi internal Java class.
- Gắn schema version/schema ID vào envelope hoặc container phù hợp.
- Quản lý quy tắc thêm/xóa/rename field theo codec thực tế.
- Lưu fixtures từ các version cũ để regression tests.
- Có migration đọc dữ liệu cũ trong môi trường được kiểm soát, chuyển thành DTO mới và validate.
- Có rollback policy nếu producer mới đã ghi dữ liệu mà consumer cũ chưa hiểu.
- Tách integrity/authentication/durability/atomic publish khỏi serialization contract.

Một database schema cũng là một loại contract dữ liệu; chọn schema exchange format không thay cho quản lý migrations ở tầng database.

## 13. DTO boundary thay vì expose domain Java

Không nên tự động dùng entity JPA hoặc object domain phức tạp làm network message. Một service có thể có model nội bộ:

~~~text
Java domain: Account(id, balanceCents, riskFlags, persistenceState)
                       |
                       | mapping + validation
                       v
Public DTO: ProfileV1(id, name, points)
                       |
                       | JSON / Protobuf / Avro encoder
                       v
Wire bytes ---------> Consumer in another language
~~~

Điểm lợi:

- Đổi implementation nội bộ không đương nhiên đổi contract.
- Tránh lộ field private/sensitive do class domain được mở rộng.
- Có nơi kiểm tra giá trị, business invariant và giới hạn kích thước.
- Tách tests cho mapping, codec và cross-language integration.
- Cho phép song song nhiều API schema version trong giai đoạn migration.

Đổi lại, có thêm DTO, mapping và schema governance. Với hệ thống nhiều dịch vụ/ngôn ngữ, chi phí này thường dễ quản lý hơn việc để wire phụ thuộc trực tiếp vào class Java.

## 14. Quy trình migration từ Java Serialization

Một hệ thống đang lưu `.ser`, session, cache hoặc queue bằng Java Object Serialization không thể thay reader sang JSON/Protobuf và mong đọc thẳng byte cũ.

Các bước:

1. Xác định chính xác tất cả nơi đang `writeObject/readObject` và class/serial UID liên quan.
2. Kiểm tra trust boundary, nguồn dữ liệu, hạn lưu trữ và phiên bản deployed.
3. Thiết kế schema/DTO mới chỉ gồm trường nghiệp vụ cần thiết; không mirror mọi Java field.
4. Xác định ID, default, version, unknown-field, encoding, constraints và error behavior.
5. Tạo adapter migration riêng để đọc dữ liệu legacy được tin cậy, qua filter/resource limits; map sang DTO và validate.
6. Đo/kiểm tra compatibility bằng message fixtures, contract tests và ít nhất một consumer non-Java nếu mục tiêu là interop.
7. Deploy hỗ trợ đọc các version cần thiết; tổ chức dual-write/backfill/on-read migration với idempotency và kiểm soát rollback.
8. Theo dõi decode errors, unknown fields, message sizes, schema versions và dữ liệu tồn backlog.
9. Gỡ bỏ native deserialization khi không còn dữ liệu/consumer cần hỗ trợ.

Nếu phải phân biệt định dạng trong thời gian chuyển đổi, nên có **format discriminator** rõ (magic/version/schema ID). Không liên tục thử đọc đầu vào bằng mọi decoder trên dữ liệu không tin cậy chỉ bằng cách bắt exception.

## 15. Những nhầm lẫn phổ biến

| Nhận định sai | Giải thích |
| --- | --- |
| “Serializable thì Python tự đọc được” | Python không mặc nhiên có Java Serialization protocol/class resolution |
| “serialVersionUID là API schema version” | UID thuộc serial form của Java class |
| “Giữ UID sẽ luôn đọc mọi class thay đổi” | Tương thích còn phụ thuộc trường/kiểu/class và semantics |
| “JSON có schema tự động” | JSON là syntax; schema/validation phải quy định |
| “Protobuf đổi số field tùy ý” | Field numbers định danh field trên wire và phải giữ ổn định |
| “Avro binary datum luôn đính kèm schema” | Có thể yêu cầu writer schema/schema ID ngoài |
| “DataOutputStream là chuẩn đa ngôn ngữ” | Phải công bố byte layout, encoding và version |
| “DataOutputStream.writeUTF dùng UTF-8 chuẩn” | Sử dụng modified UTF-8 của Java |
| “Đọc được bytes tức dữ liệu hợp lệ” | Cần kiểm tra business rules và semantic compatibility |
| “Bảo vệ bằng ObjectInputFilter là đủ” | Filter không phải sandbox cho input độc hại |
| “Có schema thì không cần migration” | Breaking change vẫn cần rollout và compatibility plan |
| “Wire compatible luôn semantic compatible” | Cùng bytes có thể mang ý nghĩa nghiệp vụ khác |
| “Binary luôn nhỏ/nhanh hơn JSON” | Phải benchmark với dữ liệu và codec thực tế |

## 16. Checklist khi chọn định dạng

1. Consumer có khác Java hay khác vòng đời triển khai không?
2. Dữ liệu có cần sống nhiều năm và chịu refactor class/package không?
3. Có schema được kiểm soát độc lập với implementation Java không?
4. Có quy tắc kiểu số, encoding, timezone, null/absence và enum chưa?
5. Có giới hạn độ dài, nesting, input trust và quyền truy cập không?
6. Có field identifiers và nguyên tắc không tái sử dụng field đã bỏ không?
7. Có backward/forward compatibility tests và golden fixtures không?
8. Có semantic validation sau khi giải mã không?
9. Có xác định explicit framing/schema ID và cách từ chối unsupported version không?
10. Có tách external DTO với ORM/domain object không?
11. Có quy trình migrate và rollback khi thay schema không?
12. Nếu dùng JSON, đã tính đến 64-bit precision và decimal chưa?
13. Nếu dùng Protobuf, đã hiểu field presence/default và unknown fields chưa?
14. Nếu dùng Avro, đã xác định nơi lấy writer schema chưa?
15. Nếu dùng custom binary, đã có đặc tả endian, length, malformed input và interop tests chưa?

## 17. Câu hỏi tự kiểm tra

1. `ObjectOutputStream` mặc định xuất JSON hoặc Protobuf không? **Không**; nó dùng Java Object Serialization Stream Protocol.
2. `serialVersionUID` làm một chương trình Go tự khôi phục object Java được không? **Không**.
3. Java Serialized object có gắn với serial form/class Java không? **Có**.
4. JSON có ép `points` phải là signed 64-bit integer không? **Không**; schema/validation cần quy định.
5. Protobuf nhận biết trường bằng gì? **Field number**.
6. Avro binary datum lúc nào cũng chứa schema đầy đủ không? **Không**.
7. Format `PRF1` của ví dụ vì sao có thể được Python decode? Vì byte layout, version, big-endian integer và UTF-8 length prefix được **đặc tả rõ**.
8. `DataOutputStream.writeUTF` có phải UTF-8 chuẩn trên wire không? **Không**, là modified UTF-8.
9. Decoder wire v1 có tự hỗ trợ thêm field sau tên không? **Không**, hiện tại nó từ chối trailing bytes.
10. Reader mới đọc dữ liệu writer cũ gọi là gì? **Backward compatibility** theo thuật ngữ thường dùng.
11. Reader cũ đọc dữ liệu writer mới gọi là gì? **Forward compatibility**.
12. Một message decode được nhưng đổi đơn vị cents thành dollars có còn đúng không? **Không**, semantics bị thay đổi.
13. `ObjectInputFilter` có cho phép nhận an toàn mọi byte stream không tin cậy không? **Không**.
14. Muốn migration dữ liệu Java serialized cũ sang Protobuf, chỉ đổi codec là đủ không? **Không**, cần đọc cũ có kiểm soát, map DTO và encode mới.
15. Có cần test consumer đa ngôn ngữ không? **Có**, nếu tương tác đa ngôn ngữ là yêu cầu.
16. Schema độc lập Java có nghĩa không cần chia sẻ bất cứ contract nào không? **Không**; producer và consumer phải chia sẻ đúng schema/wire semantics.

## Tài liệu tham khảo

- [Java SE 17 — ObjectOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectOutputStream.html), [ObjectInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html): Java Serialization API.
- [Java SE 17 — Serializable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serializable.html): serial form và `serialVersionUID`.
- [Java SE 17 — ObjectInputFilter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html): kiểm soát deserialization resource/classes.
- [Java SE 17 — DataOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutputStream.html), [DataInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html): big-endian primitives và modified UTF-8.
- [Java Object Serialization Specification](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/index.html): protocol và class evolution.
- [Protobuf proto3 language guide](https://protobuf.dev/programming-guides/proto3/): schema, field numbers và wire compatibility.
- [Apache Avro Specification](https://avro.apache.org/docs/current/specification/): binary encoding và schema resolution.
- [JSON Schema](https://json-schema.org/): mô tả schema và validation.


</details>

- [Quay lại đầu trang](#back-to-top)
