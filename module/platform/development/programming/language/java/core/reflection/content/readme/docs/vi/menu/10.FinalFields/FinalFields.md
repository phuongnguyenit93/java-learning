<a id="back-to-top"></a>

# Field final

## Menu
- [Phân biệt đọc field final với thay đổi field final](#final-fields-001)
- [Điều kiện giới hạn khi Field.set gặp final](#final-fields-002)
- [Static final là field không thể sửa bằng cơ chế mở truy cập thông thường](#final-fields-003)
- [Final field trong record và hidden class](#final-fields-004)
- [Giá trị hằng có thể đã được nhúng vào mã sử dụng](#final-fields-005)
- [Không xem sửa field là thay thế constructor và bất biến đối tượng](#final-fields-006)
- [Ảnh hưởng tới khả năng quan sát, tối ưu và hành vi phụ thuộc phiên bản](#final-fields-007)
- [Chọn constructor, factory hoặc API cập nhật được thiết kế sẵn](#final-fields-008)

## <a id="final-fields-001">Phân biệt đọc field final với thay đổi field final</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt đọc `final` field với thay đổi `final` field trong Java Reflection

`final` thể hiện ý định **giá trị của một biến không được gán lại sau khi hoàn tất khởi tạo theo quy tắc ngôn ngữ Java**. Khi dùng Reflection, người học thường nhầm rằng nếu đã đọc được một field `private final` bằng `Field#get(...)` thì cũng có thể thay đổi nó bằng `Field#set(...)`. Hai thao tác này có các điều kiện và giới hạn khác nhau.

**Trọng tâm của bài:** Reflection có thể cho phép **đọc** field `final` sau khi thỏa quy tắc truy cập; điều đó **không chứng minh** Reflection được phép **ghi** field `final`. Một số nhóm field `final` luôn thuộc loại không cho ghi thông qua `Field#set` dù đã bật quyền truy cập, còn việc ghi `final` instance thông thường là khả năng đặc biệt bị hạn chế ngày càng chặt hơn từ JDK 26.

## 1. `final` đảm bảo gì ở mức khai báo Java?

Một field được khai báo `final` chỉ có thể được gán theo các quy tắc khởi tạo cho phép:

~~~java
class Configuration {
    private final int timeoutSeconds;              // blank final
    private static final String PRODUCT = "Demo";  // static final

    Configuration(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    int timeoutSeconds() {
        return timeoutSeconds;
    }
}
~~~

Đây là **đoạn mã minh họa**, không có `main` độc lập. Sau khi constructor `Configuration` kết thúc, Java source thông thường không được viết `configuration.timeoutSeconds = 10;` vì field này là `final`.

Phân biệt:

- **`final` instance field:** mỗi instance có trạng thái riêng; giá trị thường được xác lập trong field initializer, instance initializer hoặc constructor.
- **`static final` field:** gắn với lớp thay vì một instance; chỉ được khởi tạo theo quy tắc cho static field.
- **`private` và `final` độc lập:** `private` chi phối quyền truy cập ở cấp Java, còn `final` chi phối khả năng gán. Một field có thể là `public final`, `private final`, `static final`, v.v.
- **`final` không làm đối tượng được tham chiếu trở nên bất biến sâu:** `private final List<String> names` giữ cố định **tham chiếu** sau khởi tạo bằng Java source, nhưng nếu danh sách có thể thay đổi thì `names.add(...)` vẫn có thể sửa nội dung.

Các đảm bảo của Java Memory Model dành cho `final` field giúp chương trình có mô hình khởi tạo đáng tin cậy. Việc thay đổi `final` bằng Reflection sau khởi tạo có thể phá vỡ các giả định của compiler/JVM và của những luồng đang đọc dữ liệu. Vì vậy không nên xem kỹ thuật mutation là cách cập nhật cấu hình thông thường.

## 2. Hai thao tác Reflection: đọc và ghi

Đối tượng `java.lang.reflect.Field` hỗ trợ cả metadata lẫn thao tác với giá trị:

| Nhu cầu | API | Điểm cần kiểm tra |
| --- | --- | --- |
| Nhận biết field có `final` | `Modifier.isFinal(field.getModifiers())` | Chỉ đọc modifier, **không** cho biết có thể ghi hay không |
| Tìm field không `public` | `type.getDeclaredField("name")` | Chỉ tìm metadata của field khai báo trực tiếp, không tự cấp quyền |
| Kiểm tra quyền truy cập hiện tại | `field.canAccess(target)` | Instance dùng object; static dùng `null` |
| Thử bật bỏ qua kiểm tra truy cập Java | `field.trySetAccessible()` | Trả `true`/`false`; không bảo đảm khả năng ghi field `final` |
| Đọc một field | `field.get(target)`, `getInt(target)`... | Cần đáp ứng quyền truy cập; trả giá trị tương ứng |
| Thử ghi một field | `field.set(target, value)`, `setInt(target, n)`... | Cần quyền ghi thực tế; `final` có thêm hạn chế và quy tắc phụ thuộc phiên bản JDK |

*Ghi nhớ:* có ít nhất **hai câu hỏi** khi dùng `Field`: có được **truy cập** thành viên qua Reflection hay không, và có được **sửa** chính field này hay không. Hai câu trả lời có thể lần lượt là "có" và "không".

### `getDeclaredField` chỉ tra cứu, chưa thao tác giá trị

~~~java
Field field = SomeType.class.getDeclaredField("value");
boolean isFinal = Modifier.isFinal(field.getModifiers());
~~~

Đây là **đoạn minh họa**, giả sử `SomeType` có field `value` và đã import `Field`, `Modifier`. Lời gọi `getDeclaredField` có thể trả đối tượng `Field` đại diện cho field `private final`. Nhưng `Field` là metadata, nên **không tự suy ra** rằng `get` hay `set` sau đó đã được cấp quyền.

## 3. Đọc `final` instance và `static final` field

Đọc field `final` qua Reflection về cơ bản tuân theo quy tắc truy cập của `Field`:

1. Lấy `Field` đúng đối tượng `Class<?>`. Dùng `getDeclaredField` nếu cần field `private` được khai báo trực tiếp.
2. Với **instance field**, truyền instance đúng loại vào `canAccess`/`get`. Với **static field**, truyền `null`.
3. Nếu chưa được phép truy cập theo trạng thái hiện tại, kiểm tra khả năng bật quyền bằng `trySetAccessible()` khi thật sự cần.
4. Khi đã được phép, gọi `get`, `getInt`, `getLong` hoặc phương thức đọc chuyên biệt. Đọc một field `final` **không làm thay đổi** giá trị của nó.

Lưu ý về Java modules: từ Java 9, `trySetAccessible()` có thể bị từ chối bởi ranh giới JPMS. Trong ví dụ của bài, tất cả lớp nằm trong **unnamed module của cùng class loader**, nên việc bật quyền sâu vào lớp ứng dụng được phép. Không nên mặc định kết quả này khi chuyển `Field` tới helper ở module khác hoặc truy cập module thư viện chưa mở package.

### Ví dụ chạy được: đọc ba loại `final` field

Tạo tệp **`ReadFinalDemo.java`**, dùng Java 17+:

~~~java
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

class FinalData {
    private final int count;
    private static final String LABEL = "fixed";

    FinalData(int count) {
        this.count = count;
    }
}

record FinalRecord(int value) {}

public class ReadFinalDemo {
    public static void main(String[] args) throws ReflectiveOperationException {
        FinalData data = new FinalData(7);
        Field count = FinalData.class.getDeclaredField("count");
        System.out.println("Instance final: "
                + Modifier.isFinal(count.getModifiers()));
        System.out.println("Read before: " + count.canAccess(data));
        System.out.println("Open for read: " + count.trySetAccessible());
        System.out.println("Instance value: " + count.getInt(data));

        Field label = FinalData.class.getDeclaredField("LABEL");
        System.out.println("Static final: "
                + Modifier.isFinal(label.getModifiers()));
        System.out.println("Open static: " + label.trySetAccessible());
        System.out.println("Static value: " + label.get(null));

        FinalRecord record = new FinalRecord(42);
        Field recordValue = FinalRecord.class.getDeclaredField("value");
        System.out.println("Record final: "
                + Modifier.isFinal(recordValue.getModifiers()));
        System.out.println("Open record: "
                + recordValue.trySetAccessible());
        System.out.println("Record value: " + recordValue.getInt(record));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 ReadFinalDemo.java
java ReadFinalDemo
~~~

**Stdout (đã kiểm chứng trên JDK 21):**

~~~text
Instance final: true
Read before: false
Open for read: true
Instance value: 7
Static final: true
Open static: true
Static value: fixed
Record final: true
Open record: true
Record value: 42
~~~

**Phân tích:**

- `Modifier.isFinal(...)` đọc cờ modifier của `count`, `LABEL` và field backing cho thành phần record `value`; cả ba đều là `final`.
- `count` là `private final` của một lớp khác `ReadFinalDemo`, nên `canAccess(data)` ban đầu là `false`. Sau khi `trySetAccessible()` thành công, `getInt(data)` đọc được số `7`.
- `LABEL` là `static final`: truyền `null` vào `get(null)` vì field gắn với lớp, không gắn với instance.
- Thành phần `value` của record cũng có field `private final` do compiler tạo; ví dụ đọc field đó sau khi bật quyền.
- Chương trình **không dùng `Field#set`**. Sự thành công của cả ba thao tác đọc **không cung cấp bằng chứng** rằng ba field có thể được ghi bằng Reflection.

**Tại sao ví dụ không dùng lớp lồng bên trong `ReadFinalDemo`?** Các lớp lồng có thể là *nestmates*, có quyền truy cập `private` của nhau theo quy tắc Java, khiến `canAccess(...)` ban đầu trả `true` và che mất ý nghĩa của việc thử mở quyền. Các lớp cấp cao nhất `FinalData` và `ReadFinalDemo` trong ví dụ là hai nest khác nhau.

## 4. Đọc thành công không cấp quyền ghi

Ví dụ ở mục 3 đã chứng minh có thể đọc ba field `final` khác nhau. Để quyết định có nên gọi `Field.set`, còn phải xét **loại field** và **runtime đang chạy**. `trySetAccessible()` chỉ xử lý bước xin bỏ qua kiểm tra truy cập Java; nó không loại bỏ giới hạn ghi của `final`.

| Field | `Field.get` khi đủ quyền | `Field.set` sau khi mở quyền |
| --- | --- | --- |
| Instance `final` của class thông thường | Có thể đọc | Trên JDK 17–25 có đường ghi đặc biệt khi đủ điều kiện; không phải API cập nhật trạng thái |
| `static final` | Có thể đọc | Không được ghi qua setter Reflection |
| `final` trong record | Có thể đọc | Không được ghi qua setter Reflection |
| `final` trong hidden class | Có thể đọc | Không được ghi qua setter Reflection |
| Field không `final` | Có thể đọc | Có thể ghi nếu quyền truy cập, receiver và kiểu dữ liệu phù hợp |

**Cách diễn giải:** nếu `label.trySetAccessible()` thành công rồi `label.get(null)` trả về `"fixed"`, ta mới biết thao tác **đọc** trên `static final LABEL` là hợp lệ trong ngữ cảnh đó. Thử `label.set(null, "changed")` vẫn bị từ chối bằng `IllegalAccessException`. Tương tự, record có thể cho đọc component field nhưng không cho viết lại field ấy.

JDK 26 bổ sung chính sách JEP 500 cho việc ghi **ordinary final instance field** bằng deep reflection: thao tác không được cấp phép có thể phát cảnh báo hoặc bị chặn tùy cấu hình. Các tùy chọn đó không biến `static final`, record-final hay hidden-class-final thành writable. Vì vậy đừng lấy kết quả ghi từ một JDK để đoán cho mọi JDK khác.

## 5. Chọn cách đọc phù hợp

- Nếu lớp đã có **getter/accessor public**, hãy ưu tiên nó. Với record, accessor như `recordValue.value()` thể hiện rõ contract của đối tượng.
- Nếu cần **metadata**, dùng `getDeclaredField`, `getModifiers` hoặc `getDeclaredFields` mà không cần tự ý mở quyền đọc giá trị.
- Nếu công cụ thật sự cần đọc **private field**, xác định đúng declaring class, receiver (instance hoặc `null` cho static) và quyền truy cập qua module. `trySetAccessible() == false` có thể là kết quả hợp lệ khi package không mở cho caller.
- Nếu yêu cầu thật sự là **cập nhật giá trị**, đừng chuyển quy trình đọc thành lời gọi `set` theo quán tính. Tìm constructor/factory, phương thức cập nhật hoặc tạo instance mới; các bài tiếp theo sẽ phân tích sâu các giới hạn và lựa chọn đó.

## 6. Tự kiểm tra

1. `getDeclaredField("count")` trả về một `Field` cho `private final int count`. Có thể khẳng định `getInt(data)` sẽ chạy ngay không? **Không**; cần kiểm tra quyền truy cập của caller.
2. `trySetAccessible()` trả `true` cho `private static final String LABEL`. Có thể `Field.set(null, "new")` không? **Không**; `static final` không có quyền ghi bằng cách mở accessible.
3. Đọc `record FinalRecord(int value)` qua `getInt(record)` thành công, có thể suy ra sửa component field không? **Không**; record final field thuộc loại không thể ghi qua `Field.set`.
4. `final List<String>` trỏ tới một `ArrayList`. Nếu gọi `add` thành công, có phải vừa gán lại field không? **Không**; chỉ nội dung object được tham chiếu thay đổi.

**Điều cần nhớ:** tra cứu metadata, xin mở quyền, đọc và ghi là **bốn thao tác khác nhau**. Bài này dạy đường **đọc** và cách nhận ra ranh giới sang **ghi**; khi cần ghi, phải kiểm tra riêng từng loại `final` cùng phiên bản Java trước khi quyết định thiết kế.

### Tài liệu tham khảo

- [Java SE 17 — Field API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html)
- [Java SE 17 — AccessibleObject API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html)
- [JEP 500 — Prepare to Make Final Mean Final](https://openjdk.org/jeps/500)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="final-fields-002">Điều kiện giới hạn khi Field.set gặp final</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện giới hạn khi Field.set gặp final trong Java Reflection

Một trường (field) có từ khóa **final** được thiết kế để chỉ nhận giá trị trong quá trình khởi tạo hợp lệ theo quy tắc Java. Khi dùng Reflection, bạn vẫn có thể lấy đối tượng java.lang.reflect.Field và đọc giá trị của trường. Tuy nhiên, **đọc được Field, thậm chí gọi thành công setAccessible(true), không đồng nghĩa Field.set có quyền sửa giá trị final**.

Trong Java 17, một số **instance final field** của class thông thường vẫn có thể bị sửa bằng deep reflection theo điều kiện rất chặt; nhưng **static final**, final trong **record** và final trong **hidden class** là các trường **non-modifiable**, không thể ghi bằng Field.set. Từ JDK 26, những lần sửa final field bằng deep reflection còn chịu chính sách cảnh báo/giới hạn mới, hướng đến cấm mặc định trong các bản Java tương lai.

Mục tiêu bài học: nhận biết trường hợp được phép hoặc bị chặn khi gọi Field.set, dự đoán exception, phân biệt quyền truy cập với quyền ghi, kiểm tra tác động của modules và chọn cách thiết kế an toàn thay cho sửa final sau khởi tạo.

## 1. final trong mã nguồn khác gì với Reflection?

Ví dụ:

~~~java
class Order {
    private final int id;

    Order(int id) {
        this.id = id;
    }

    int id() {
        return id;
    }
}
~~~

Mã Java thông thường không cho phép viết order.id = 100 sau constructor vì trường được khai báo final. Reflection có hai bước riêng:

1. Lấy metadata: Field field = Order.class.getDeclaredField("id").
2. Truy cập hoặc ghi: field.get(order), field.set(order, 100), có thể cần field.setAccessible(true).

**getDeclaredField** cho phép tìm field private đã khai báo trực tiếp trong lớp, nhưng bản thân nó không cấp quyền đọc/ghi. **setAccessible(true)** yêu cầu tắt các kiểm tra truy cập theo ngôn ngữ Java đối với Field đó, trong phạm vi quyền module cho phép. Nó **không loại bỏ các loại final bị Java đánh dấu là không thể ghi**.

Điều này phải tách thành ba câu hỏi:

- Có tìm đúng Field và đối tượng đích không?
- Caller có được phép thực hiện thao tác Reflection hay không?
- Field thuộc loại final có thể sửa trong runtime/phiên bản JDK đang chạy hay không?

Chỉ khi cả ba điều kiện đều phù hợp, Field.set mới có cơ hội hoàn thành. Việc sửa thành công không có nghĩa hành vi của chương trình sau đó luôn quan sát được giá trị mới.

## 2. Quy tắc Field.set đối với final trên Java 17

Theo API Java SE 17, để **ghi một final field bằng Field.set(Object, Object)**, các điều kiện sau phải đồng thời đúng:

1. **setAccessible(true) đã thành công trên chính Field này**.
2. Field là **instance field**, tức **không phải static**.
3. Lớp khai báo field **không phải hidden class**.
4. Lớp khai báo field **không phải record class**.
5. Object đích phù hợp, giá trị truyền vào có thể gán/chuyển đổi hợp lệ cho kiểu của field, và không có rào cản runtime bổ sung.

Khi thiếu một trong các điều kiện ghi final ở trên, Field.set có thể ném **IllegalAccessException**. Nếu quyền truy cập đã bị chặn ngay ở setAccessible(true), ngoại lệ thường gặp là **InaccessibleObjectException**. Nếu giá trị hoặc đối tượng không phù hợp, có thể gặp **IllegalArgumentException**; nếu truyền null cho instance field cần receiver, có thể gặp **NullPointerException**.

| Loại field | Ví dụ | setAccessible(true) có thể cho đọc? | Field.set ghi final trên Java 17? |
| --- | --- | --- | --- |
| Instance final trong class thông thường | private final int id | Có, nếu quyền truy cập cho phép | **Có điều kiện**, với các hạn chế trên |
| Static final của class | private static final String NAME | Có, nếu quyền truy cập cho phép | **Không** |
| Static final trong interface | String NAME = "x" | Có theo visibility/quyền truy cập | **Không** |
| Instance final của record | record User(int id) | Có, nếu quyền truy cập cho phép | **Không** |
| Final field trong hidden class | field final của lớp tạo qua Lookup.defineHiddenClass | Có, nếu quyền truy cập cho phép | **Không** |
| Instance non-final thông thường | private int count | Có, nếu quyền truy cập cho phép | Có thể, theo quyền truy cập và quy tắc kiểu |

**Chú ý:** bảng này nói về thao tác **Field.set** của Java Reflection, không kết luận về tất cả cơ chế như Java bytecode, JNI, serialization internals hoặc API đặc biệt khác. Không dùng mẹo dựa vào Unsafe hoặc sửa cờ Modifier để lách các ràng buộc: những cách đó không phải hợp đồng API ổn định.

### Non-modifiable final field nghĩa là gì?

JDK 17 xác định ba nhóm **không thể ghi qua Field.set**:

- Mọi **static final** (trong class hoặc interface).
- Mọi **final field khai báo trong hidden class**.
- Mọi **final field khai báo trong record class**.

Với các nhóm này, setAccessible(true) nếu được cho phép chỉ giúp Reflection **đọc** field không public; nó **không chuyển** field thành writable.

Một record như record Customer(int id) tự có instance component field tương ứng private final. Dù có lấy được Field("id"), Field.set vẫn không được phép sửa nó.

## 3. Ví dụ Java 17+ có thể biên dịch và chạy

Lưu toàn bộ đoạn dưới đây vào **FinalFieldRulesDemo.java**. Ví dụ cố tình thử bốn trường hợp: sửa instance final thông thường, ghi static final, ghi final của record và truyền sai kiểu dữ liệu.

~~~java
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class FinalFieldRulesDemo {
    static final class Invoice {
        private final int id;

        Invoice(int id) {
            this.id = id;
        }
    }

    static final class Settings {
        private static final String VERSION = "v1";
    }

    record Receipt(int id) {}

    public static void main(String[] args) throws ReflectiveOperationException {
        Invoice invoice = new Invoice(1);
        Field instanceField = Invoice.class.getDeclaredField("id");
        System.out.println("instance final = "
                + Modifier.isFinal(instanceField.getModifiers()));
        instanceField.setAccessible(true);
        instanceField.set(invoice, 42);
        System.out.println("instance Field.get = " + instanceField.getInt(invoice));

        Field constantField = Settings.class.getDeclaredField("VERSION");
        constantField.setAccessible(true);
        try {
            constantField.set(null, "v2");
        } catch (IllegalAccessException ex) {
            System.out.println("static final = " + ex.getClass().getSimpleName());
        }

        Field recordField = Receipt.class.getDeclaredField("id");
        recordField.setAccessible(true);
        try {
            recordField.set(new Receipt(3), 9);
        } catch (IllegalAccessException ex) {
            System.out.println("record final = " + ex.getClass().getSimpleName());
        }

        try {
            instanceField.set(invoice, "text");
        } catch (IllegalArgumentException ex) {
            System.out.println("wrong value type = " + ex.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch/chạy bằng JDK 17–25, hoặc chạy bằng JDK 21 để tái lập đúng stdout sau đây:

~~~bash
javac --release 17 -encoding UTF-8 FinalFieldRulesDemo.java
java FinalFieldRulesDemo
~~~

Stdout được kiểm tra bằng cách biên dịch với **--release 17** và chạy trên JDK 21:

~~~text
instance final = true
instance Field.get = 42
static final = IllegalAccessException
record final = IllegalAccessException
wrong value type = IllegalArgumentException
~~~

Giải thích theo từng bước:

1. Modifier.isFinal(instanceField.getModifiers()) trả true: metadata xác nhận instance field id có modifier final.
2. instanceField.setAccessible(true) thành công vì ví dụ đang nằm trong cùng chương trình/module có quyền cần thiết. Trên JDK 21, Field.set(invoice, 42) ghi được vào **instance final của class thường**, Field.getInt trả 42.
3. Settings.VERSION là **static final**. Ngay cả sau setAccessible(true), Field.set(null, "v2") ném IllegalAccessException; đối số receiver null vốn hợp lệ với static field, nên lỗi nằm ở quyền ghi final.
4. Receipt.id là **record final**. setAccessible(true) không biến nó thành writable; Field.set ném IllegalAccessException.
5. Instance id có kiểu int. Truyền chuỗi "text" không thể unbox/widen thành int, nên Field.set ném IllegalArgumentException.

**Giới hạn của ví dụ:** chỉ kết luận Field.get quan sát 42 ngay sau thao tác thành công trên JDK được kiểm thử. Đừng coi đây là lời bảo đảm mọi đoạn code đã biên dịch, thread khác hoặc JVM/JIT khác đều nhìn thấy 42 qua getter hay truy cập trực tiếp. Trên JDK 26, lệnh sửa instance final trong ví dụ có thể phát **cảnh báo trên stderr** theo chính sách mới; phần stdout được ghi riêng để tránh nhầm lẫn.

## 4. setAccessible, trySetAccessible và quyền module

Trước khi gọi Field.set, kiểm tra lớp và module thực sự chứa field. Trong **Java Platform Module System (JPMS)**, các chỉ thị có vai trò khác nhau:

- **requires** tạo quan hệ module readability; nó không tự mở private member để Reflection.
- **exports package** cho phép dùng public API ở package đó từ các module đọc thư viện; nó không tự cho quyền truy cập private field.
- **opens package** hoặc **opens package to target.module** cho phép deep reflection trong phạm vi module được mở, gồm khả năng yêu cầu setAccessible(true) trên private field.

Ví dụ module chứa entity:

~~~java
module com.example.data {
    exports com.example.data.api;
    opens com.example.data.entity to com.example.json;
}
~~~

Module com.example.json có thể yêu cầu mở quyền truy cập reflective vào các field của com.example.data.entity. Nhưng nếu field thuộc loại **static final** hoặc **record final**, opens vẫn **không biến nó thành writable**. Nếu field là ordinary instance final, opens có thể thỏa điều kiện truy cập; còn các giới hạn final của Field.set và JDK vẫn áp dụng.

Ở một package không được mở cho caller module, ví dụ:

~~~java
Field field = someClass.getDeclaredField("value");
field.setAccessible(true); // có thể ném InaccessibleObjectException
~~~

Đây là **đoạn minh họa**; someClass phải là Class<?> đã xác định ở ngữ cảnh gọi, không phải chương trình độc lập.

Thay bằng:

~~~java
if (field.trySetAccessible()) {
    // Được phép yêu cầu bỏ qua kiểm tra access; chưa chắc ghi được nếu field final.
} else {
    // Module/package hoặc kiểm soát truy cập không cho phép.
}
~~~

trySetAccessible() thường trả false thay vì ném InaccessibleObjectException khi không thể bật accessibility (nhưng các exception an ninh đặc biệt vẫn có thể tồn tại). Thành công của trySetAccessible() **không đồng nghĩa Field.set chắc chắn thành công**. Cần tách lỗi ở giai đoạn mở quyền và lỗi ở giai đoạn ghi.

Để chẩn đoán:

~~~java
Class<?> owner = field.getDeclaringClass();
Module ownerModule = owner.getModule();
Module callerModule = YourCaller.class.getModule();
System.out.println(ownerModule.isOpen(owner.getPackageName(), callerModule));
~~~

Đây cũng là một đoạn minh họa; thay YourCaller bằng class thực hiện Reflection. **Module.isOpen** giúp kiểm tra ranh giới package, không quyết định riêng một field final có ghi được không. Với class và caller ở cùng module, các quy tắc truy cập có thể khác trường hợp hai named modules riêng biệt.

Tùy chọn **--add-opens source.module/package=target.module** có thể cấp quyền deep reflection lúc khởi chạy khi phù hợp, nhưng **không** cho phép ghi các field non-modifiable final. Và từ JDK 26, **--add-opens cũng không thay thế quyền final-field mutation bổ sung** được đề cập ở mục 7.

## 5. Phân biệt từng loại lỗi khi Field.set thất bại

| Hiện tượng | Điểm thất bại | Nguyên nhân thường gặp | Hướng giải quyết |
| --- | --- | --- | --- |
| NoSuchFieldException | getDeclaredField/getField | Sai tên hoặc tìm nhầm lớp; getDeclaredField không tìm field kế thừa | Chọn đúng declaring class/tên field |
| InaccessibleObjectException | setAccessible(true) | Package trong named module không opens cho caller | Xem module-info.java và quyền opens phù hợp |
| trySetAccessible() trả false | trySetAccessible() | Không thể bật suppression theo access/module rules | Dùng API public hoặc điều chỉnh quyền có chủ đích |
| IllegalAccessException | Field.set | Không đủ quyền ghi; hoặc final thuộc loại không được sửa | Kiểm tra final/static/record/hidden và accessible flag |
| IllegalArgumentException | Field.set | Receiver sai class hoặc kiểu giá trị không thể chuyển hợp lệ | Dùng đúng object và đúng kiểu field |
| NullPointerException | Field.set | Receiver null với instance field | Truyền instance thực |
| Cảnh báo final field mutation (JDK 26) | Lúc đột biến final được phát hiện | Deep reflective mutation chưa được cho phép rõ ràng | Di chuyển logic khởi tạo; khi bắt buộc thì xem JEP 500 |

**Ví dụ:** Field f có kiểu int có thể nhận Integer qua unboxing và một số chuyển đổi nguyên thủy *widening* được hỗ trợ. Nhưng không được chuyển tùy tiện từ String thành int, cũng không mặc định thực hiện narrowing conversion như long xuống int. Có thể dùng phương thức chuyên biệt Field.setInt(...) nếu phù hợp, nhưng **nó cũng không vượt qua hạn chế final**.

### Kiểm tra modifier và declaring class

Dùng mã phụ trợ sau khi đã có Field:

~~~java
int modifiers = field.getModifiers();
boolean isFinal = Modifier.isFinal(modifiers);
boolean isStatic = Modifier.isStatic(modifiers);
boolean inRecord = field.getDeclaringClass().isRecord();
boolean inHidden = field.getDeclaringClass().isHidden();
~~~

Đây là đoạn code minh họa, cần import java.lang.reflect.Field và java.lang.reflect.Modifier và biến field. Một quy tắc tự kiểm tra là: nếu isFinal và (isStatic hoặc inRecord hoặc inHidden), **đừng gọi Field.set để mong sửa field ấy**. Ngay cả khi các điều kiện trên đều false, vẫn phải xét quyền truy cập, phiên bản Java và rủi ro mutation của instance final.

## 6. Vì sao sửa instance final thành công vẫn nguy hiểm?

Java cho phép một số ngoại lệ reflective phục vụ công cụ tái dựng đối tượng hoặc deserialization, nhưng **final không được thiết kế như biến cấu hình có thể sửa tùy thời điểm**. JDK 17 mô tả việc sửa final bằng Reflection chỉ có ý nghĩa trong các kịch bản reconstruction/deserialization phù hợp, trước khi đối tượng được các thành phần khác sử dụng.

### Giá trị có thể không được mọi code quan sát như nhau

Xét ví dụ mang tính giải thích (không dự đoán stdout):

~~~java
class Flags {
    final int mode = 1;

    int mode() {
        return mode;
    }
}
~~~

Compiler và JIT có thể tận dụng tính bất biến của final; một lệnh gọi getter hoặc mã đã tối ưu hóa có thể tiếp tục sử dụng giá trị cũ dù Reflection đã sửa ô nhớ. Đặc biệt với **compile-time constants** là final primitive/String khởi tạo bằng biểu thức hằng, Java có thể thay thế tham chiếu bằng hằng ngay từ bytecode của consumer.

Vì vậy:

- Không dùng Field.get ngay sau khi set như bằng chứng thay đổi sẽ phản ánh ổn định ở tất cả code.
- Không xây dựng tính đúng đắn nghiệp vụ dựa trên sửa một final đã công bố cho thread khác.
- Không dùng Reflection để đổi static final constant rồi trông chờ toàn bộ chương trình nhìn thấy thay đổi; Field.set thậm chí từ chối ghi loại này.
- Sửa final sau khởi tạo phá vỡ giả định tối ưu hóa, bất biến và có thể làm suy yếu các bảo đảm về an toàn công bố đối tượng.

### Vấn đề thread và Java Memory Model

Quy tắc final field của Java Memory Model có ý nghĩa đặc biệt tại lúc hoàn tất khởi tạo đối tượng qua constructor. Các bảo đảm đó không phải giấy phép cho một thread sửa final tùy ý rồi chờ mọi thread khác thấy giá trị mới. Field.set thành công là một thao tác reflective, **không phải giao thức đồng bộ hóa** thay thế happens-before, volatile hoặc synchronized.

Nếu dữ liệu thật sự cần thay đổi theo thời gian, hãy thiết kế nó bằng field thông thường kết hợp cơ chế đồng bộ thích hợp; nếu cần giữ dữ liệu bất biến, hãy tạo instance mới bằng constructor hoặc factory method. Đừng xem final thành một kiểu mutable field ẩn.

## 7. Lưu ý mới từ JDK 26: JEP 500 – Prepare to Make Final Mean Final

Quy tắc ở mục 2 mô tả khả năng của **Java SE 17**. Cần phân biệt nó với một thay đổi khi chạy trên JDK hiện đại.

Từ **JDK 26**, JEP 500 bổ sung cơ chế phát hiện việc **mutate final instance fields thông qua deep reflection**. Theo chế độ mặc định ở JDK 26, lần mutation bất hợp lệ bởi một module có thể tiếp tục chạy nhưng phát **cảnh báo**. Mục tiêu được JDK công bố là một bản phát hành sau sẽ chuyển mặc định sang **từ chối thao tác**, trừ khi ứng dụng cấp quyền tường minh cho module thực hiện mutation.

Các tham số liên quan:

- **--enable-final-field-mutation=ALL-UNNAMED**: cho phép code trong unnamed module (ứng dụng trên classpath) thực hiện kiểu mutation này khi các điều kiện truy cập khác cũng thỏa mãn.
- **--enable-final-field-mutation=module.one,module.two**: cấp cho các named module được chỉ định thực hiện final-field mutation theo chính sách JDK 26.
- **--illegal-final-field-mutation=warn**: cho phép nhưng cảnh báo, là chế độ mặc định của JDK 26 đối với mutation chưa được cấp quyền.
- **--illegal-final-field-mutation=debug**: cảnh báo kèm stack trace, hữu ích để tìm library đang sửa final.
- **--illegal-final-field-mutation=deny**: từ chối mutation không được cấp phép và dẫn đến IllegalAccessException.
- **--illegal-final-field-mutation=allow**: cho phép mà không cảnh báo, chỉ nên coi là biện pháp chuyển tiếp.

Ví dụ minh họa **chỉ dùng với Java 26+**, giả sử ứng dụng ở classpath thật sự phải thực hiện mutation sau khi đã xét quyền truy cập:

~~~bash
java --enable-final-field-mutation=ALL-UNNAMED FinalFieldRulesDemo
~~~

Hoặc chủ động kiểm thử ứng dụng theo chính sách nghiêm ngặt của JDK 26:

~~~bash
java --illegal-final-field-mutation=deny FinalFieldRulesDemo
~~~

Nếu thực hiện lệnh deny không kèm quyền --enable-final-field-mutation phù hợp, ví dụ ở mục 3 có thể thất bại sớm tại instanceField.set(invoice, 42) với IllegalAccessException, nên **không dùng stdout JDK 21 làm dự đoán cho lần chạy đó**.

**Hai lớp điều kiện độc lập:** module sở hữu field vẫn phải mở package thích hợp cho caller qua opens/--add-opens (hoặc trường hợp truy cập được phép khác); đồng thời JDK 26 có thể đòi cấp phép mutation cho module caller. --enable-final-field-mutation không tự mở package, và **không biến static final/record final/hidden-class final thành writable**.

Chính sách này giúp các thư viện serialization/DI dần bỏ phụ thuộc vào việc sửa final đã tồn tại. Nếu bảo trì dự án chạy nhiều JDK, hãy thử bản mới, xem stderr và nâng phiên bản library trước khi cân nhắc bất kỳ tùy chọn cấp quyền nào.

## 8. Cách thiết kế thay thế Reflection để giữ final đúng nghĩa

### Đặt dữ liệu tại thời điểm khởi tạo

~~~java
public record UserProfile(String name, int level) {
    public UserProfile withLevel(int nextLevel) {
        return new UserProfile(name, nextLevel);
    }
}
~~~

Đây là class record độc lập có thể dùng trong dự án; khi cần thay đổi level, tạo một UserProfile mới thay vì Field.set lên field của record.

### Constructor, factory, builder

Nếu đối tượng cần các giá trị bắt buộc, truyền chúng qua constructor/factory/builder. Các framework có thể hỗ trợ **constructor-based binding** hoặc **record canonical constructor** thay vì ghi từng private final field sau khi tạo instance. Khi framework cần truy cập non-public constructor ở module khác, vẫn có thể cần opens, nhưng không nhất thiết cần mutation final sau constructor.

### Khi dữ liệu thực sự mutable

Dùng một field non-final với setter có kiểm soát, hoặc tạo object mới khi trạng thái thay đổi. Đối với ứng dụng đa luồng, chọn volatile, AtomicReference, synchronized hay immutable snapshot theo yêu cầu nhất quán thực tế. Đừng né final bằng Reflection để đổi trạng thái âm thầm.

### Với static constants

Muốn đổi cấu hình runtime, dùng nguồn cấu hình hoặc đối tượng cấu hình mutable có cơ chế cập nhật rõ ràng. Không dựa vào chỉnh static final primitive/String, đặc biệt các compile-time constants có khả năng bị inline vào nơi sử dụng.

## 9. Quy trình chẩn đoán từng bước

1. **Tìm đúng Field.** Dùng getDeclaredField(name) trên class khai báo, không giả định nó duyệt superclasses. Với field kế thừa, tìm trên declaring class.
2. **Kiểm tra loại field.** Modifier.isFinal(), Modifier.isStatic(), getDeclaringClass().isRecord(), isHidden(). Nếu final và là static/record/hidden: xem như không thể ghi qua Field.set.
3. **Kiểm tra access.** Ai thực hiện Reflection? Package chứa field có được opens đúng module caller không? Nếu dùng setAccessible(true), lỗi tại đây thường khác lỗi tại Field.set.
4. **Kiểm tra receiver và value.** Instance field cần object thuộc class phù hợp; primitive cần kiểu chuyển đổi được; static field không cần receiver.
5. **Kiểm tra JDK đang chạy.** Khả năng Java 17 khác chính sách mutation JDK 26. Đọc warning/stack trace và thử cấu hình kiểm tra phù hợp khi bảo trì.
6. **Đánh giá tính đúng đắn sau mutation.** Nếu field là final, không dùng kết quả Field.get đơn lẻ để kết luận getter/consumer khác sẽ thấy giá trị mới; ưu tiên sửa kiến trúc đối tượng.

Một quy trình chẩn đoán tốt phân biệt rõ **metadata được tra cứu**, **quyền mở access**, **quyền ghi loại field**, **kiểu dữ liệu**, và **ngữ nghĩa bất biến**. Điều này thường chỉ ra lỗi nằm ở đâu mà không cần mở rộng quyền toàn bộ module.

## 10. Tự kiểm tra

**Câu 1.** private static final int LIMIT = 10; thuộc class thường, field.setAccessible(true) thành công. Field.setInt(null, 20) có thành công không?

**Đáp án:** Không. Đây là static final, thuộc nhóm non-modifiable trên Java 17; setter chuyên biệt không vượt qua giới hạn final.

**Câu 2.** private final int age của class thường, Field.setAccessible(true) thành công; receiver hợp lệ, kiểu truyền phù hợp. Field.set có thể chạy được trên Java 17 không?

**Đáp án:** Có điều kiện. Đây là instance final không thuộc record/hidden class. Tuy nhiên việc sửa sau khi đối tượng được công bố không được bảo đảm quan sát đồng nhất, và JDK 26 còn có chính sách cảnh báo/giới hạn mới.

**Câu 3.** record Book(String title) có field title. Nếu gọi setAccessible(true) được rồi thì Field.set(book, "new") có hợp lệ không?

**Đáp án:** Không. Instance final do record khai báo thuộc nhóm non-modifiable.

**Câu 4.** Một module exports package chứa class nhưng không opens cho module framework muốn ghi private final field. Lỗi có thể xuất hiện từ bước nào?

**Đáp án:** Từ setAccessible(true) vì không có opens (có thể ném InaccessibleObjectException). Thêm exports không thay thế opens; ngay cả khi opens được cấp, vẫn phải kiểm tra loại final và chính sách JDK.

**Câu 5.** Trên JDK 26, mở package với --add-opens liệu đủ để tránh warning final field mutation?

**Đáp án:** Không nhất thiết. Quyền deep reflection và quyền **mutate final** được xét riêng; nếu thật sự cần, phải hiểu --enable-final-field-mutation và --illegal-final-field-mutation.

**Ghi nhớ:** Reflection có thể **đọc** các final field trong phạm vi quyền phù hợp; **Field.set chỉ từng cho phép sửa một số instance final của class thông thường với điều kiện chặt chẽ**. Static final, record final và hidden-class final không thể ghi bằng Field.set. Trên JDK 26+, kiểm tra thêm chính sách final-field mutation và ưu tiên khởi tạo bất biến đúng bằng constructor/factory.

### Tài liệu tham khảo

- Java SE 17, java.lang.reflect.Field – Field.set: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html#set(java.lang.Object,java.lang.Object)
- Java SE 17, AccessibleObject – setAccessible và các final field non-modifiable: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html#setAccessible(boolean)
- Java SE 17, Class – isRecord/isHidden: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java Language Specification §17.5 – final Field Semantics: https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.5
- JEP 500 – Prepare to Make Final Mean Final: https://openjdk.org/jeps/500
- Oracle JDK 26 Migration Guide – Preparing for final Field Mutation Restrictions: https://docs.oracle.com/en/java/javase/26/migrate/preparing-final-field-mutation-restrictions.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="final-fields-003">Static final là field không thể sửa bằng cơ chế mở truy cập thông thường</a>

<details>
<summary>Xem chi tiết</summary>

# Static final: không thể ghi bằng cách mở quyền Reflection thông thường

## 1. Câu hỏi cốt lõi

Trong Java, một field khai báo `static final` là **field thuộc về class** (hoặc interface) và **tham chiếu/giá trị của field không được gán lại sau khi được khởi tạo** theo quy tắc của ngôn ngữ. Khi dùng Reflection, kể cả khi `Field.setAccessible(true)` hoặc `Field.trySetAccessible()` đã cho phép truy cập member vốn là private, các API ghi thông thường như `Field.set(...)`, `Field.setInt(...)` vẫn **không được cấp quyền ghi vào `static final`**.

Ví dụ:

~~~java
class Config {
    private static final int PORT = 8080;
}
~~~

Lệnh `Config.class.getDeclaredField("PORT")` có thể lấy được metadata về field; nếu quyền module cho phép thì `setAccessible(true)` có thể cho đọc `PORT`. Tuy nhiên, gọi `field.setInt(null, 9090)` sẽ ném `IllegalAccessException`. Đừng nhầm việc lấy được `Field` hoặc bật được quyền truy cập với quyền **sửa** field đó.

Quy tắc quan trọng trong JDK 17+ (và tiếp tục trong API JDK 26): **mọi `static final` được khai báo trong class hoặc interface là *non-modifiable final fields* đối với `java.lang.reflect.Field`**. Chính `AccessibleObject.setAccessible` và `Field.set` định nghĩa giới hạn này; không có sự khác biệt cho phép ghi chỉ vì field có `private`, `public`, kiểu primitive, `String` hay một kiểu object.

## 2. Hiểu đúng ba lớp kiểm tra

Với field `private static final`, có ba câu hỏi khác nhau:

1. **Có tìm được khai báo field?** `getDeclaredField("name")` tìm field khai báo trong class, bất kể `public` hay `private`. Nó có thể ném `NoSuchFieldException` nếu tên sai.
2. **Có được truy cập member đó không?** `setAccessible(true)` yêu cầu nới kiểm tra ngôn ngữ Java; `trySetAccessible()` trả về `true`/`false` tuỳ quyền. Java Platform Module System (JPMS) vẫn có thể từ chối deep reflection khi package không mở cho caller.
3. **Field có cho phép ghi không?** Dù câu 2 thành công, field `static final` vẫn là *non-modifiable final field*. Gọi các setter Reflection thông thường sẽ ném `IllegalAccessException`.

Các cơ chế hoạt động ở những tầng khác nhau. `setAccessible(true)` **không xóa modifier `final`**, cũng không cho phép ứng dụng bỏ qua quy tắc ghi của `Field.set`.

| Thao tác | Trên field `static final` |
| --- | --- |
| `Class.getDeclaredField("X")` | Lấy metadata thành công nếu field tồn tại; không mặc định cấp quyền đọc/ghi. |
| `Field.getModifiers()` | Có thể kiểm tra `Modifier.isStatic(...)` và `Modifier.isFinal(...)`. |
| `Field.setAccessible(true)` | Có thể thành công nếu caller có quyền mở truy cập; chỉ hỗ trợ đọc nếu field là *non-modifiable final*. |
| `Field.trySetAccessible()` | Có thể trả `true`; kết quả này **không** có nghĩa được phép ghi. |
| `Field.get(null)` / `getInt(null)` | Đọc nếu được phép; đối số đối tượng bị bỏ qua đối với field static. |
| `Field.set(null, value)` / `setInt(null, value)` | Ném `IllegalAccessException` vì không có quyền ghi `static final`, ngay cả sau khi mở quyền truy cập. |

Với setter Reflection cho field static, đối số đối tượng thường truyền `null` vì không gắn với instance. Với field instance, phải truyền đối tượng đúng kiểu. Đối số `null` **không phải nguyên nhân** khiến ghi `static final` bị từ chối.

## 3. Ví dụ đầy đủ, chạy với Java 17 trở lên

Lưu đoạn sau vào file `StaticFinalDemo.java`:

~~~java
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

class Settings {
    private static final int LIMIT = 10;
    private static final List<String> TAGS = new ArrayList<>(List.of("A"));
    private static int count = 1;

    static List<String> tags() {
        return TAGS;
    }
}

public class StaticFinalDemo {
    public static void main(String[] args) throws Exception {
        Field limit = Settings.class.getDeclaredField("LIMIT");
        System.out.println("static final: "
                + (Modifier.isStatic(limit.getModifiers())
                && Modifier.isFinal(limit.getModifiers())));
        System.out.println("accessible: " + limit.trySetAccessible());
        System.out.println("LIMIT: " + limit.getInt(null));

        try {
            limit.setInt(null, 20);
        } catch (IllegalAccessException e) {
            System.out.println("change LIMIT: " + e.getClass().getSimpleName());
        }

        Field tags = Settings.class.getDeclaredField("TAGS");
        tags.setAccessible(true);
        try {
            tags.set(null, new ArrayList<>(List.of("X")));
        } catch (IllegalAccessException e) {
            System.out.println("replace TAGS: " + e.getClass().getSimpleName());
        }

        Settings.tags().add("B");
        System.out.println("mutate TAGS object: " + Settings.tags());

        Field count = Settings.class.getDeclaredField("count");
        count.setAccessible(true);
        count.setInt(null, 2);
        System.out.println("change non-final static: " + count.getInt(null));
    }
}
~~~

Chạy bằng JDK 17+:

~~~bash
javac --release 17 StaticFinalDemo.java
java StaticFinalDemo
~~~

Stdout đã được kiểm tra với chương trình biên dịch ở chế độ tương thích Java 17:

~~~text
static final: true
accessible: true
LIMIT: 10
change LIMIT: IllegalAccessException
replace TAGS: IllegalAccessException
mutate TAGS object: [A, B]
change non-final static: 2
~~~

**Giải thích từng kết quả:**

- `LIMIT` có đồng thời cờ `static` và `final`. `trySetAccessible()` trả `true` bởi mã ví dụ chạy trên class path: class `Settings` thuộc unnamed module có package mở cho reflection. Điều đó cho phép đọc `10`.
- `limit.setInt(null, 20)` không được phép ghi vì field là `static final`. `IllegalAccessException` là kết quả phải xử lý, dù lệnh nới quyền trước đó thành công.
- `TAGS` là một **final reference** tới `ArrayList`. `tags.set(null, new ArrayList<>(...))` cố thay **tham chiếu field**, nên cũng thất bại.
- `Settings.tags().add("B")` thay **trạng thái object** đã được field tham chiếu, không gán lại chính field. Vì object là `ArrayList` có thể thay đổi, thao tác thành công và in `[A, B]`.
- `count` được khai báo `static` nhưng **không `final`**, nên Reflection có thể sửa field sau khi mở quyền. Kết quả `2` cho thấy hạn chế ghi ở trên gắn với `final`.

Bài học này không phụ thuộc vào mẹo can thiệp nội bộ JVM. Khả năng mở quyền đến từ việc mã ví dụ nằm trong unnamed module; với class trong **named module** không mở package cho caller, `trySetAccessible()` có thể trả `false` ngay trước bước đọc.

## 4. `final` giữ nguyên tham chiếu, không làm object bất biến

Xét:

~~~java
private static final List<String> ITEMS = new ArrayList<>();
~~~

Hai hành động khác bản chất:

- **Thay field:** `ITEMS = new ArrayList<>();` bị trình biên dịch chặn sau khi khởi tạo; `Field.set(null, anotherList)` cũng không được phép với `static final`.
- **Thay trạng thái object:** `ITEMS.add("x")` vẫn có thể chạy vì `ArrayList` là cấu trúc mutable. Lúc này field vẫn trỏ tới cùng object.

Tương tự, `static final StringBuilder BUILDER = new StringBuilder("A");` cho phép `BUILDER.append("B")` dù không cho phép `BUILDER = new StringBuilder("C")`. Ngược lại, `String` là immutable nên không có thao tác thay đổi nội dung của chính đối tượng `String`.

Để có collection chỉ đọc ở cấp API, cân nhắc `List.of(...)` hay bản sao bất biến `List.copyOf(...)`. Dù vậy, cần xét khả năng mutability của **các phần tử** nếu chúng là object phức tạp; `final` và collection không hỗ trợ thay đổi cấu trúc không bảo đảm deep immutability của cả đồ thị object.

## 5. Compile-time constant và field được khởi tạo tại runtime

Một số field `static final` là **constant variables** theo Java Language Specification: chúng có kiểu primitive hoặc `String`, được khai báo `final` và khởi tạo bằng *constant expression*. Ví dụ:

~~~java
public static final int TIMEOUT = 30;
public static final String MODE = "safe";
~~~

Khi mã khác tham chiếu các hằng này, trình biên dịch có thể ghi giá trị hằng trực tiếp vào bytecode của mã sử dụng (*inlining*). Thay đổi giá trị trong thư viện nhưng **không biên dịch lại** mã sử dụng có thể khiến chương trình tiếp tục thấy giá trị cũ. Vì thế, đừng thiết kế lại cấu hình runtime bằng cách tìm cách sửa field constant.

Field sau cũng là `static final` nhưng giá trị được quyết định khi class khởi tạo:

~~~java
public static final int STARTED_AT_SECOND =
        java.time.LocalTime.now().getSecond();
~~~

Đây **không phải** constant variable có giá trị xác định tại compile time. Tuy nhiên, nó **vẫn không thể bị ghi bởi setter Reflection thông thường**, vì `static final` nào cũng thuộc nhóm không cho phép ghi. Quy tắc về *inlining* và quy tắc về quyền ghi Reflection là hai điều riêng biệt.

Một field `static final` có thể được gán trong static initializer:

~~~java
static final int VALUE;

static {
    VALUE = 42;
}
~~~

Cách gán này hợp lệ **trong quá trình khởi tạo class** theo quy tắc Java source. Nó không mở ra quyền dùng Reflection gán lại sau khi khởi tạo.

## 6. So với các loại `final` khác

| Loại field | `Field.set` sau khi `setAccessible(true)` thành công | Lưu ý |
| --- | --- | --- |
| `static final` trong class/interface | **Không thể ghi.** | Không phụ thuộc kiểu field là primitive hay reference. |
| `final` instance trong class thông thường | Ở Java 17–25 có thể được Reflection cho ghi trong các điều kiện được tài liệu cho phép. | Không nên dùng ngoài các tình huống khởi tạo/tái dựng đối tượng đặc thù; code/JIT có thể tiếp tục quan sát giá trị cũ. |
| `final` instance thuộc `record` | **Không thể ghi bằng cơ chế nới quyền thông thường.** | Record được bảo vệ bởi quy tắc *non-modifiable final field*. |
| `final` thuộc hidden class | **Không thể ghi bằng cơ chế nới quyền thông thường.** | Không được coi như final instance của class thông thường. |
| `static` nhưng không `final` | Có thể ghi nếu quyền truy cập và kiểu giá trị hợp lệ. | Ví dụ `count` trong chương trình ở trên. |

Các giới hạn Reflection dành cho `final` instance đang tiếp tục được thắt chặt: **JDK 26** theo JEP 500 bắt đầu cảnh báo các lần ghi *final instance field* bằng deep reflection, có tùy chọn kiểm soát riêng (như `--illegal-final-field-mutation`). Hành vi ghi này được định hướng sẽ bị chặn mặc định trong phiên bản tương lai. Điểm quan trọng với bài này: **quy tắc cấm ghi `static final` đã có từ trước; các cờ cho phép final-field mutation của JDK mới không chuyển `static final` thành field ghi được.**

Không nên dựa vào việc từng sửa được `final instance field` ở một JDK cũ để suy ra `static final` có thể sửa theo cách tương tự.

## 7. Các API và ngoại lệ liên quan

### `Field.setAccessible(true)` và `trySetAccessible()`

- `setAccessible(true)` yêu cầu nới kiểm tra truy cập Java. Nó có thể ném `InaccessibleObjectException` nếu caller không được phép mở member theo JPMS.
- `trySetAccessible()` trả `false` nếu không thể bật quyền truy cập, thay vì ném `InaccessibleObjectException` cho tình huống quyền thông thường đó.
- **Cả hai không tạo quyền ghi** cho `static final`. Do đó chương trình ở phần 3 vẫn nhận `IllegalAccessException` tại setter sau khi `trySetAccessible()` trả `true`.

### `Field.get(...)` và `Field.set(...)`

`Field.get(null)` và các getter kiểu primitive như `Field.getInt(null)` đọc field static nếu quyền truy cập cho phép. `Field.set(null, value)` và các setter primitive phải vượt qua **kiểm tra quyền ghi**, nên `static final` bị từ chối. Các vấn đề khác như kiểu truyền sai, unboxing/narrowing không hợp lệ có thể gây `IllegalArgumentException`; chúng không phải bằng chứng rằng đang sửa được `final`.

### `VarHandle` có thay được kết luận không?

Không. Với một `VarHandle` trỏ tới `final field`, các *write access modes* như `set` không được hỗ trợ và sẽ ném `UnsupportedOperationException` khi gọi. Việc lấy handle hoặc đọc giá trị không tự cấp chế độ ghi cho `static final`. `MethodHandles.Lookup` có các quy tắc quyền truy cập riêng; không nên xem nó như cửa hậu cho phép bỏ qua bất biến của `final`.

### `Modifier.FINAL` không phải công tắc runtime

`Modifier.isFinal(field.getModifiers())` cho biết modifier. Việc tìm kiếm hoặc sửa dữ liệu nội bộ của `Field` để “gỡ bit final”, hay can thiệp bộ nhớ bằng API không được hỗ trợ, **không thuộc API ghi Reflection thông thường** và không bảo đảm tương thích các JDK. Các cách đó còn có thể xung đột với compiler optimization, class initialization và mô hình bộ nhớ. Tránh thiết kế hệ thống phụ thuộc vào chúng.

## 8. Thiết kế đúng khi thật sự cần thay đổi giá trị

Nếu giá trị phải thay đổi khi chương trình đang chạy, đừng đặt nó vào một field `static final` rồi dựa vào Reflection để sửa.

**Lựa chọn 1 — field có thể thay đổi:** Sử dụng field không `final`, giới hạn quyền truy cập và cập nhật bằng API do class cung cấp. Trong chương trình đa luồng, cân nhắc `volatile` hoặc cơ chế đồng bộ thích hợp, thay vì mặc định một setter bất kỳ sẽ bảo đảm visibility.

~~~java
final class RuntimeSettings {
    private static volatile int timeoutSeconds = 30;

    static int timeoutSeconds() {
        return timeoutSeconds;
    }

    static void updateTimeoutSeconds(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        timeoutSeconds = value;
    }
}
~~~

Đây là **đoạn class để minh họa thiết kế**, không có `main` nên không tự tạo output. `volatile` hỗ trợ visibility và ordering đối với field đó; nó không thay thế mọi nhu cầu atomicity của logic nhiều bước.

**Lựa chọn 2 — final reference tới một vùng trạng thái có API cập nhật:** Có thể giữ reference cố định nhưng thay dữ liệu trong đối tượng được tham chiếu qua API thread-safe, chẳng hạn `AtomicReference<Configuration>`. Ở đây `static final` bảo vệ **identity của container**, còn `container.set(newConfig)` thay **trạng thái container** theo contract của nó. Dùng mô hình này chỉ khi thật sự cần trạng thái chia sẻ có thể cập nhật và hiểu tác động đồng thời.

~~~java
import java.util.concurrent.atomic.AtomicReference;

final class ConfigStore {
    static final AtomicReference<String> CURRENT =
            new AtomicReference<>("development");

    static void switchTo(String value) {
        CURRENT.set(value);
    }

    static String read() {
        return CURRENT.get();
    }
}
~~~

Đây cũng là class minh họa không có `main`. Mặc dù `CURRENT` là `static final`, không có chỗ nào ghi lại **tham chiếu field `CURRENT`**; mã chỉ gọi method cập nhật của object.

**Lựa chọn 3 — dependency injection hoặc cấu hình khi khởi tạo:** Nếu cấu hình đáng lẽ cố định suốt vòng đời object, truyền nó qua constructor và khởi tạo instance mới khi thay cấu hình. Giải pháp này làm rõ sở hữu và chu kỳ sống của dữ liệu, tránh Reflection sửa cấu trúc runtime.

## 9. Lỗi thường gặp và câu hỏi tự kiểm tra

1. **“`setAccessible(true)` thành công nghĩa là ghi được.”** Sai: khi field là `static final`, chỉ có thể mở quyền đọc; setter vẫn không có write access.
2. **“Lỗi do truyền `null` cho `Field.setInt(null, x)`.”** Sai: đối với static field, đối số instance bị bỏ qua.
3. **“Chỉ `static final int` mới bị khóa vì compile-time constant.”** Sai: `static final List<?>` cũng không được gán lại bằng Reflection; field có giá trị runtime cũng bị khóa.
4. **“`static final List` nghĩa là danh sách không thể thêm phần tử.”** Sai: `final` cố định reference; tính mutable của List quyết định khả năng thay nội dung.
5. **“Truy cập được `private static final` trong module khác nếu bật accessible.”** Chưa chắc: JPMS có thể từ chối cả deep reflection khi package chưa `opens`; và nếu mở được thì vẫn không được ghi.
6. **“`Field.set` báo `IllegalAccessException` nên phải dùng `--add-opens` là sẽ sửa được.”** `--add-opens` chỉ xử lý hàng rào module trong trường hợp phù hợp, không cấp quyền ghi vào non-modifiable final.
7. **“Các tùy chọn final-field mutation mới sẽ mở được `static final`.”** Sai: chúng liên quan ghi `final` instance trong điều kiện được hỗ trợ, không loại bỏ hạn chế với static final.

**Tự kiểm tra:**

- Thay `TAGS` trong ví dụ thành `List.of("A")`. Theo bạn lời gọi `Settings.tags().add("B")` sẽ ném ngoại lệ gì? *Gợi ý:* tính bất biến của object khác với modifier `final` của field.
- Nếu bỏ `final` khỏi `LIMIT`, giữ nguyên tên và câu lệnh `setInt(null, 20)`, kết quả sẽ khác thế nào?
- Tại sao field `static final int LIMIT = 10` có thêm rủi ro liên quan inlining nhưng field `static final List<String> TAGS` không phải là constant variable?
- Nếu `trySetAccessible()` trả `false`, nên kiểm tra quyền mở package hoặc module nào trước khi thử đọc? Tại sao kể cả sau khi mở package vẫn không thể ghi `static final`?

## Tài liệu tham khảo

- [Java SE 17 API — `Field.set(Object, Object)` và `Field.setInt(Object, int)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html)
- [Java SE 17 API — `AccessibleObject.setAccessible(boolean)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html)
- [Java SE 17 API — `VarHandle`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/VarHandle.html)
- [Java Language Specification §4.12.4 — final Variables / constant variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4)
- [JEP 500 — Prepare to Make Final Mean Final (JDK 26)](https://openjdk.org/jeps/500)
- [Java SE 26 Migration Guide — Preparing for Final Field Mutation Restrictions](https://docs.oracle.com/en/java/javase/26/migrate/preparing-final-field-mutation-restrictions.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="final-fields-004">Final field trong record và hidden class</a>

<details>
<summary>Xem chi tiết</summary>

# Final field trong record và hidden class: giới hạn Reflection ở Java 17+

## 1. Vấn đề cần hiểu

Một sai lầm khi học Java Reflection là cho rằng: **nếu `Field.setAccessible(true)` thành công thì bất kỳ trường `final` nào cũng có thể bị sửa**. Điều này **không đúng**.

Kể từ Java 17 (và trong các API tương ứng đã có ở những phiên bản trước), Java phân biệt một nhóm **non-modifiable final fields**. Ba nhóm quan trọng theo tài liệu `java.lang.reflect.Field` và `AccessibleObject` là:

1. Mọi trường `static final` được khai báo ở bất kỳ class/interface nào.
2. Mọi trường `final` được khai báo trong **record class**.
3. Mọi trường `final` được khai báo trong **hidden class**.

Với nhóm này, kể cả khi mã gọi đã bật thành công quyền truy cập sâu bằng `setAccessible(true)` hoặc `trySetAccessible()`, Reflection chỉ có thể **bỏ qua kiểm tra quyền truy cập để đọc**; nó **không cấp quyền ghi** lên trường đó.

Ví dụ ý tưởng:

```java
Field field = SomeRecord.class.getDeclaredField("name");
field.setAccessible(true);      // Có thể thành công nếu module cho phép.
Object value = field.get(obj);  // Có thể đọc được.
field.set(obj, "changed");      // IllegalAccessException: final field không sửa được.
```

Đây là các dòng minh họa cần có định nghĩa `SomeRecord` và đối tượng `obj` tương ứng, không phải chương trình Java độc lập. Chương trình biên dịch/chạy đầy đủ ở mục 7 sẽ chứng minh quy tắc bằng cả record và hidden class.

Bài này tập trung vào **quyền ghi của `Field` khi declaring class là record hoặc hidden class**. Cần phân biệt với hai khái niệm khác: các mức truy cập Java (`private`/`public`) và field hiding (lớp con khai báo trường trùng tên với lớp cha). **Hidden class** là loại class do runtime định nghĩa theo cơ chế riêng; nó không có nghĩa “field bị class khác che khuất”.

## 2. Tại sao `final` quan trọng, và vì sao Reflection có ngoại lệ?

### 2.1. `final` trong lập trình Java thông thường

Trường instance `final` thường được gán khi khởi tạo đối tượng và không thể gán lại bằng cú pháp Java thông thường sau khi đã khởi tạo hợp lệ. Ví dụ:

```java
final class Ticket {
    private final String code;

    Ticket(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }
}
```

`Ticket.code` là trường `private final`: phía bên ngoài không được gán bằng `ticket.code = "x"`. Điều đó không nhất thiết có nghĩa **mọi** thao tác phản chiếu vào mọi trường final instance thông thường đều bị cấm ở cấp API.

Trên Java 17, tài liệu `Field.set` vẫn ghi nhận trường hợp rất hẹp mà Reflection có thể ghi một **instance final field trong lớp thông thường** khi `setAccessible(true)` đã thành công. Việc đó chủ yếu có ý nghĩa trong quy trình khôi phục/deserialization đối tượng có blank final field **trước khi** đối tượng được công bố cho phần khác của chương trình. Nếu thay đổi final field sau đó, kết quả quan sát có thể không dự đoán được vì tối ưu hóa và các bảo đảm đặc biệt của mô hình bộ nhớ Java.

**Không dùng khả năng lịch sử này làm cơ sở thiết kế API sửa immutable object.** Quy tắc record và hidden class còn chặt hơn: trường `final` do các class này khai báo là **non-modifiable**, dù kiểm tra truy cập đã được bỏ qua thành công.

### 2.2. Cùng là `final` nhưng điều kiện ghi khác nhau

| Declaring class / loại field | Có thể xin bypass kiểm tra quyền để đọc? | `Field.set(...)` có được ghi field `final`? |
|---|---|---|
| Trường `static final` trong class/interface bất kỳ | Có, nếu được runtime cho phép | **Không** |
| Trường instance `final` khai báo ở `record` | Có, nếu được runtime cho phép | **Không** |
| Trường `final` khai báo ở `hidden class` | Có, nếu được runtime cho phép | **Không** |
| Trường instance `final` của class thông thường, không phải record/hidden | Có, tùy quyền/module | Trên Java 17 có điều kiện hỗ trợ rất hẹp khi suppress access checks; không nên dùng để cập nhật đối tượng đang hoạt động |
| Trường instance **không** `final` | Có, tùy quyền/module | Có thể ghi nếu các điều kiện khác (receiver, kiểu dữ liệu, quyền...) hợp lệ |

Bảng diễn tả hành vi của **Java 17 Reflection API**; nó không bảo đảm quyền truy cập thành công giữa các named modules hoặc quyền ghi vào những loại field không final. Với instance field, receiver phải là đối tượng tương thích; với static field, `Field.set` bỏ qua receiver và có thể truyền `null`.

Lưu ý phạm vi quy tắc: ở **hidden class**, chỉ **field có modifier `final`** thuộc nhóm không được phép ghi; các field không final trong hidden class không tự động trở thành bất biến chỉ vì class là hidden.

## 3. Record: component tạo ra private final field

### 3.1. Record là gì trong phạm vi bài này?

Record là dạng khai báo class dành cho dữ liệu có cấu trúc; các **record components** nằm trong header. Ví dụ:

```java
record UserId(long value, String source) {
}
```

Với `UserId`, compiler tạo ra các thành phần tương ứng với record component, trong đó có:

- Hai **private final instance fields** tương ứng với `value` và `source`.
- Hai accessor methods `value()` và `source()`.
- Canonical constructor nhận các tham số tương ứng với record header (nếu không tự khai báo hình thức hợp lệ khác).
- Các triển khai cho `equals`, `hashCode` và `toString` theo contract của record.

Record là một class `final`, không thể làm superclass để kế thừa theo cách thông thường, và record không được tùy ý khai báo thêm instance fields ngoài những trường tương ứng với components theo luật ngôn ngữ. Record có thể có static fields và methods riêng. Các component field là `private final` bất kể syntax header chỉ ghi `record UserId(long value, ...)`.

Record cung cấp **shallow immutability** ở mức component references: không thể gán lại component field sau khi khởi tạo, nhưng nếu một component giữ tham chiếu tới đối tượng mutable như `List`, trạng thái bên trong đối tượng được tham chiếu vẫn có thể thay đổi nếu không được sao chép/bảo vệ. Bất biến của field và bất biến sâu của toàn bộ object graph là hai điều khác nhau.

### 3.2. Reflection thấy record field như thế nào?

`Class.isRecord()` trả `true` với record class. Đối tượng `Class` còn hỗ trợ `getRecordComponents()` để liệt kê các component ở mức semantic của record.

```java
record UserId(long value, String source) {}

Class<?> type = UserId.class;
System.out.println(type.isRecord());        // true

Field field = type.getDeclaredField("source");
System.out.println(field.getName());        // source
System.out.println(Modifier.isFinal(field.getModifiers())); // true
```

Đoạn ví dụ này giả định các import `java.lang.reflect.Field` và `java.lang.reflect.Modifier`, và có xử lý `NoSuchFieldException`. Nó minh họa việc tìm metadata mà chưa thực hiện đọc/ghi giá trị.

Có hai con đường đọc thông tin:

- **Theo API record:** `userId.source()` hoặc method accessor trả giá trị; đây là cách thuận tiện trong code ứng dụng.
- **Theo Reflection:** `getDeclaredField("source")` rồi `Field.get(userId)` nếu quyền truy cập cho phép.

Dùng `getRecordComponents()` khi muốn biết **record components theo đúng thứ tự khai báo**, loại dữ liệu, accessor và annotation liên quan. Dùng `getDeclaredFields()` khi thực sự cần metadata của các trường: một record còn có thể có trường static không phải component. Không giả định `getDeclaredFields()` bằng `getRecordComponents()` theo cả nội dung lẫn thứ tự.

### 3.3. Tại sao `setAccessible(true)` không sửa record component?

Giả sử có record:

```java
record Person(String name, int age) {}
```

`name` và `age` là những final fields do record class khai báo. Dù có metadata và quyền truy cập phù hợp:

```java
Person p = new Person("An", 25);
Field nameField = Person.class.getDeclaredField("name");

if (nameField.trySetAccessible()) {
    System.out.println(nameField.get(p)); // An
    nameField.set(p, "Bình");             // IllegalAccessException
}
```

Đây là đoạn minh họa trong ngữ cảnh đã import `Field` và xử lý checked exceptions. `trySetAccessible() == true` chỉ cho thấy runtime cho phép **bỏ qua kiểm tra mức truy cập**. Nó không biến final field trong record thành writable.

**Cách đúng khi cần giá trị mới** là tạo instance record mới thông qua constructor/factory hợp lệ:

```java
Person updated = new Person("Bình", p.age());
```

Record là một cách truyền dữ liệu theo giá trị component, không phải cơ chế cập nhật trường riêng lẻ sau khởi tạo. Nếu lưu record trong một collection/map, hãy thay reference tới record bằng instance mới theo thiết kế của ứng dụng.

## 4. Hidden class: runtime định nghĩa class nhưng không cho ghi final field

### 4.1. Hidden class không phải private class

**Hidden class** được giới thiệu qua cơ chế Java runtime/Method Handles, phục vụ triển khai động (chẳng hạn những class nội bộ do framework hoặc runtime tạo), nơi class không cần được tìm qua tên nhị phân theo cơ chế bình thường.

Các điểm khác với class khai báo và nạp thông thường:

- Có thể được định nghĩa từ class-file bytes qua `MethodHandles.Lookup.defineHiddenClass(byte[], boolean, ClassOption...)`.
- `Class.isHidden()` trả `true` với hidden class.
- Hidden class **không được phát hiện bằng cơ chế tra cứu thông thường dựa trên binary name**, ví dụ không thể dựa vào `Class.forName(className)` để tìm hidden class theo cách tìm class thông thường.
- Nó vẫn có đối tượng `Class<?>`, có constructors, methods và fields mà code giữ tham chiếu tới class có thể dùng Reflection/Method Handles để khảo sát.
- Hidden class được định nghĩa trong ngữ cảnh package/module/lookup phù hợp và có những quy tắc riêng về discoverability, liên kết và vòng đời; không tự động đồng nghĩa với `private` hoặc với việc mã ngoài không thể cầm `Class<?>` của nó.

Hidden class khác hoàn toàn với:

- **Nested class**: class khai báo bên trong class khác.
- **Anonymous class**: class không có tên đơn giản trong source.
- **Field hiding**: class con định nghĩa field cùng tên với class cha.
- **`private` member/class**: quyền truy cập ngôn ngữ.

Một nested class bình thường **không tự động là hidden class**; ví dụ `Outer.Inner.class.isHidden()` thông thường trả `false`. Muốn có hidden class thật sự phải sử dụng cơ chế định nghĩa phù hợp hoặc được runtime/framework tạo.

### 4.2. Tại sao final field của hidden class không sửa được?

Java Reflection coi mọi `final field` **khai báo trong hidden class** là non-modifiable. Điều kiện “hidden” áp dụng cho **declaring class của field**: hãy kiểm tra `field.getDeclaringClass().isHidden()` thay vì chỉ xem class của object đang được truyền làm receiver.

```java
Field f = hiddenClass.getDeclaredField("token");

System.out.println(f.getDeclaringClass().isHidden()); // true
System.out.println(Modifier.isFinal(f.getModifiers())); // true
```

Nếu `token` là final, dù `f.setAccessible(true)` thành công theo điều kiện truy cập, `f.set(hiddenObject, "new")` sẽ ném `IllegalAccessException`. Dòng này minh họa metadata; cách tạo hidden class và `hiddenObject` đầy đủ sẽ có ở mục 7.

Ràng buộc này giúp runtime dựa vào ý nghĩa ổn định của các trường final trong class được tạo động. Các class động thường tham gia thực thi tối ưu hóa hoặc liên kết đặc biệt, nên việc cho phép Reflection tự ý gán lại những giá trị final đã khởi tạo sẽ phá vỡ giả định triển khai.

Đối với **non-final field** khai báo trong hidden class, việc có thể `Field.set` hay không vẫn phải xét quyền truy cập, receiver và kiểu dữ liệu. Không coi `isHidden()` là một lệnh cấm tất cả thao tác với field.

### 4.3. Khởi tạo hidden class và sử dụng class-file bytes

`Lookup.defineHiddenClass` yêu cầu:

- Class-file bytes hợp lệ, phù hợp với package của lookup class và các ràng buộc của API.
- Một `MethodHandles.Lookup` có đủ quyền (full privilege access).
- Lựa chọn khởi tạo và các `ClassOption` nếu cần. `NESTMATE` cho phép định nghĩa hidden class là nestmate của lookup class theo API.

API trả một `Lookup` tương ứng với hidden class đã định nghĩa; `lookupClass()` lấy đối tượng `Class<?>` của hidden class đó. Chỉ gọi `Class.isHidden()` trên **class thực sự trả về từ `defineHiddenClass`** mới kiểm tra đúng trường hợp này. Không đánh đồng template class mà mình dùng để lấy bytes với hidden class mới được VM định nghĩa.

## 5. Quyền truy cập và quyền ghi là hai điều kiện độc lập

### 5.1. `trySetAccessible` có ý nghĩa gì?

`Field` kế thừa `AccessibleObject`. Một số API thường gặp:

| API | Vai trò |
|---|---|
| `field.canAccess(receiver)` | Hỏi hiện tại có thể truy cập field bằng receiver phù hợp không; không đổi trạng thái |
| `field.trySetAccessible()` | Thử bật access-check suppression; trả boolean thành công/thất bại |
| `field.setAccessible(true)` | Yêu cầu bật access-check suppression; có thể ném `InaccessibleObjectException` nếu không được mở |
| `field.get(receiver)` | Đọc giá trị khi được phép |
| `field.set(receiver, value)` | Yêu cầu ghi giá trị, còn chịu các hạn chế riêng cho final field |

Với final field non-modifiable, `setAccessible(true)` **không bật khả năng ghi**. Đây không phải mâu thuẫn: **bỏ qua kiểm tra quyền truy cập Java** và **được phép sửa trường final** là hai điều kiện khác nhau.

Một trình tự điển hình:

```text
getDeclaredField(...)                    -> Field metadata
   |
   +-- trySetAccessible() == false       -> không mở được access-check suppression
   |
   +-- trySetAccessible() == true        -> có thể bypass Java access checks
           |
           +-- get(receiver)             -> có thể đọc
           |
           +-- set(receiver, newValue)
                  |
                  +-- field final trong record/hidden?
                         |
                         +-- Có          -> IllegalAccessException
                         +-- Không       -> xét tiếp static final, loại field,
                                          receiver, kiểu dữ liệu, điều kiện khác
```

Sơ đồ chỉ minh họa nhánh quyền ghi liên quan bài học, không liệt kê mọi ngoại lệ/validation của Reflection. Một lần `get` còn có thể thất bại nếu receiver sai hoặc chương trình gọi không có quyền; một lần `set` vào trường không final cũng cần kiểm tra kiểu và receiver.

### 5.2. Module boundary vẫn có hiệu lực

Java Platform Module System (JPMS) có cơ chế strong encapsulation. Với class ở named module khác, `trySetAccessible()` có thể trả `false` nếu package không `opens` thích hợp cho module của caller. Khi `setAccessible(true)` không thể bật, nó có thể ném `InaccessibleObjectException`.

Với code ví dụ chạy trên classpath trong **unnamed module**, gọi `trySetAccessible()` trên private field cùng module có thể thành công. Nhưng không nên suy rộng rằng mọi framework truy cập private field của mọi module đều thành công.

Điểm rất quan trọng: **kể cả package đã được `opens` đầy đủ**, field `final` do record hoặc hidden class khai báo vẫn không trở thành writable bằng `Field.set`.

## 6. Quy tắc chính xác của `Field.set` trên Java 17

Theo hợp đồng của `Field.set(Object, Object)`, một field final chỉ có thể được cấp quyền ghi theo đường Reflection này nếu đồng thời thỏa tất cả điều kiện:

1. `setAccessible(true)` (hoặc `trySetAccessible` thành công với hiệu quả tương đương) đã bật suppression trên **chính đối tượng `Field` đó**.
2. Field **không phải `static`**.
3. Declaring class của field **không phải hidden class**.
4. Declaring class của field **không phải record class**.

Nếu một trong các điều kiện trên không thỏa, `Field.set` ném `IllegalAccessException` cho final field. Những điều kiện này tách biệt khỏi kiểm tra object receiver và khả năng chuyển kiểu giá trị đầu vào.

| Kiểm tra | Ví dụ | Kết luận |
|---|---|---|
| `Modifier.isFinal(f.getModifiers())` | `true` | Field có modifier final |
| `Modifier.isStatic(f.getModifiers())` | `true` | Final + static là non-modifiable |
| `f.getDeclaringClass().isRecord()` | `true` | Final field trong record là non-modifiable |
| `f.getDeclaringClass().isHidden()` | `true` | Final field trong hidden class là non-modifiable |
| `f.trySetAccessible()` | `true` | Có thể suppression access checks; **không** suy ra quyền ghi |

Có thể dùng các kiểm tra trên để xác định trước rằng code của mình **không nên gọi `set`**. Tuy nhiên mã ứng dụng vẫn nên xử lý exception nếu có thao tác Reflection thật, vì metadata, quyền module, kiểu receiver và môi trường chạy có thể tạo thêm lỗi.

## 7. Chương trình Java 17+ đầy đủ: đọc được nhưng không sửa được

Ví dụ này **thực sự tạo hidden class**, không chỉ đặt tên một class bình thường là "Hidden". Nó chứa:

- `Order` là record với component `label`, từ đó compiler tạo final field.
- `HiddenTemplate` là static nested class thường, dùng **class-file bytes** để tạo một **hidden class mới** qua `Lookup.defineHiddenClass`.
- `Ordinary` để so sánh metadata: một class thông thường có private final field nhưng không phải record/hidden class.

### 7.1. Mã nguồn

Lưu thành file **`RecordHiddenFinalDemo.java`**:

```java
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class RecordHiddenFinalDemo {
    record Order(int id, String label) {}

    public static class HiddenTemplate {
        private final String code;

        public HiddenTemplate(String code) {
            this.code = code;
        }
    }

    static class Ordinary {
        private final String code;

        Ordinary(String code) {
            this.code = code;
        }
    }

    public static void main(String[] args) throws Exception {
        Order order = new Order(7, "ready");
        Field recordField = Order.class.getDeclaredField("label");

        System.out.println("record class: " + Order.class.isRecord());
        System.out.println("record field final: "
                + Modifier.isFinal(recordField.getModifiers()));
        System.out.println("record open: " + recordField.trySetAccessible());
        System.out.println("record read: " + recordField.get(order));

        try {
            recordField.set(order, "updated");
            System.out.println("record write: success");
        } catch (IllegalAccessException ex) {
            System.out.println("record write: "
                    + ex.getClass().getSimpleName());
        }

        String resource = "RecordHiddenFinalDemo$HiddenTemplate.class";
        byte[] bytes;
        try (InputStream in =
                RecordHiddenFinalDemo.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + resource);
            }
            bytes = in.readAllBytes();
        }

        Class<?> hidden = MethodHandles.lookup()
                .defineHiddenClass(bytes, true,
                        MethodHandles.Lookup.ClassOption.NESTMATE)
                .lookupClass();

        Object hiddenObject =
                hidden.getDeclaredConstructor(String.class)
                        .newInstance("sealed");
        Field hiddenField = hidden.getDeclaredField("code");

        System.out.println("hidden class: " + hidden.isHidden());
        System.out.println("hidden field final: "
                + Modifier.isFinal(hiddenField.getModifiers()));
        System.out.println("hidden open: " + hiddenField.trySetAccessible());
        System.out.println("hidden read: " + hiddenField.get(hiddenObject));

        try {
            hiddenField.set(hiddenObject, "updated");
            System.out.println("hidden write: success");
        } catch (IllegalAccessException ex) {
            System.out.println("hidden write: "
                    + ex.getClass().getSimpleName());
        }

        Field ordinary = Ordinary.class.getDeclaredField("code");
        System.out.println("ordinary record: "
                + ordinary.getDeclaringClass().isRecord());
        System.out.println("ordinary hidden: "
                + ordinary.getDeclaringClass().isHidden());
        System.out.println("ordinary final: "
                + Modifier.isFinal(ordinary.getModifiers()));
    }
}
```

Chạy trong thư mục chứa file bằng JDK 17+:

```bash
javac --release 17 RecordHiddenFinalDemo.java
java RecordHiddenFinalDemo
```

Lệnh `javac` tạo các class files cần thiết trong cùng thư mục. Khi chạy trực tiếp với classpath hiện hành, resource `RecordHiddenFinalDemo$HiddenTemplate.class` sẽ tồn tại để `getResourceAsStream` đọc bytes. Nếu đóng gói ứng dụng theo cách đặc biệt, bạn phải bảo đảm class-file resource đó được phân phối và đọc được.

### 7.2. Kết quả chuẩn

```text
record class: true
record field final: true
record open: true
record read: ready
record write: IllegalAccessException
hidden class: true
hidden field final: true
hidden open: true
hidden read: sealed
hidden write: IllegalAccessException
ordinary record: false
ordinary hidden: false
ordinary final: true
```

Đầu ra trên được kiểm chứng bằng cách biên dịch mã nguồn với `--release 17` và chạy trên JDK tương thích. Nó giả định môi trường classpath/unnamed module thông thường, không thêm SecurityManager hoặc cơ chế đặc biệt chặn quyền Reflection.

### 7.3. Giải thích kỹ luồng record

- `Order.class.isRecord()` trả `true` vì `Order` là record.
- `getDeclaredField("label")` tìm trường do record khai báo, có modifier `final`.
- `trySetAccessible()` trả `true` trong ví dụ này vì caller có điều kiện truy cập phù hợp (cùng unnamed module).
- `get(order)` đọc thành công `"ready"`.
- `set(order, "updated")` vẫn ném `IllegalAccessException` do quy tắc **non-modifiable final field của record**. Lỗi **không phải** vì không tìm được trường, không phải vì sai kiểu `String` và không phải vì `trySetAccessible()` trả `false`.

### 7.4. Giải thích kỹ luồng hidden class

- `HiddenTemplate` khi khai báo trong source chỉ là nested class thông thường; nó **không** phải hidden class.
- `getResourceAsStream` lấy chính bytecode đã biên dịch của template. `defineHiddenClass` dùng bytes này để tạo một class mới, trả một `Lookup`; `lookupClass()` là **hidden class thực sự**.
- `hidden.isHidden()` trả `true`. Khác biệt này chứng minh ví dụ đang thao tác với hidden class, không phải chỉ class có tên chứa chữ “Hidden”.
- Constructor được gọi qua Reflection để tạo `hiddenObject`. Field `code` được khai báo với `private final` trong class tương ứng.
- `hiddenField.trySetAccessible()` thành công, `get(hiddenObject)` đọc được `"sealed"`.
- `set(hiddenObject, "updated")` ném `IllegalAccessException` vì declaring class của final field là **hidden class**.

### 7.5. So sánh với class thường

`Ordinary` có `private final String code`, nhưng `isRecord()` và `isHidden()` đều trả `false`. Chương trình **không thử sửa field** của `Ordinary` vì việc phản chiếu sửa final field của class thường sau khởi tạo không phải kỹ thuật nên áp dụng trong mã nghiệp vụ. Mục đích của nó là làm rõ: **`final` thôi chưa diễn tả đầy đủ những hạn chế đặc biệt trong record/hidden class**.

## 8. Những trường hợp biên và ngoại lệ cần phân biệt

### 8.1. Lỗi tra cứu khác lỗi ghi

| Tình huống | Kết quả thường gặp |
|---|---|
| Tên field không tồn tại trong kiểu được yêu cầu | `NoSuchFieldException` khi `getDeclaredField` |
| Đã tìm thấy field nhưng không có quyền truy cập thông thường | `IllegalAccessException` ở `Field.get/set` |
| Package module khác không cho mở deep reflection | `trySetAccessible()` có thể trả `false`; `setAccessible(true)` có thể ném `InaccessibleObjectException` |
| Final field thuộc record/hidden class, access suppression đã bật | `Field.set(...)` vẫn ném `IllegalAccessException` |
| Instance field nhưng receiver là `null` | `NullPointerException` |
| Receiver không phải instance tương thích của declaring class | `IllegalArgumentException` |
| Value sai kiểu hoặc không chuyển được theo quy tắc `Field.set` | `IllegalArgumentException` |

Việc `Field.set` thất bại với record final field **không có nghĩa** class record không tìm được bằng Reflection. Tương tự, hidden class vẫn có thể được khảo sát qua `Class<?>` của nó ngay cả khi cơ chế lookup bằng binary name không áp dụng.

### 8.2. Final field và static field

Một record có thể khai báo static fields hợp lệ. Nếu field là `static final`, nó cũng bị cấm sửa qua `Field.set` theo quy tắc chung áp dụng cho mọi class. Với non-final static fields, Reflection có thể ghi trong điều kiện được phép, nhưng điều đó **không liên quan trực tiếp** đến việc ghi component fields của record.

### 8.3. Đọc có đồng nghĩa thay được cấu trúc bất biến?

Không. `Field.get` trả giá trị của field. Với field kiểu tham chiếu, giá trị này có thể là tham chiếu tới một đối tượng mutable. Ví dụ:

```java
record Team(java.util.List<String> members) {}

var names = new java.util.ArrayList<String>();
names.add("An");
Team team = new Team(names);
names.add("Bình");
System.out.println(team.members().size()); // 2
```

Record component `members` là final và không thể gán lại bằng Reflection, nhưng `ArrayList` mà nó trỏ tới vẫn mutable. Nếu muốn record có ý nghĩa bất biến sâu hơn, dùng defensive copy, ví dụ trong compact canonical constructor:

```java
record SafeTeam(java.util.List<String> members) {
    SafeTeam {
        members = java.util.List.copyOf(members);
    }
}
```

Đây là các đoạn minh họa độc lập về **shallow immutability**, không phải thao tác ghi Field. `List.copyOf` tạo danh sách không hỗ trợ sửa qua API mutator và từ chối phần tử `null`; nếu các phần tử bản thân là đối tượng mutable thì còn cần cơ chế riêng để bảo đảm deep immutability.

## 9. Có thể dùng `VarHandle` hoặc Method Handles để sửa final field này không?

Không nên hiểu rằng một API reflection khác sẽ tự động cung cấp quyền ghi mà `Field.set` đã cấm.

- `VarHandle` dùng để truy cập biến theo các access modes được hỗ trợ. Một VarHandle cho final field thường chỉ có các mode đọc; `set` và các mode ghi không được hỗ trợ. Có thể dùng `isAccessModeSupported(...)` để kiểm tra trước.
- `MethodHandles.Lookup` giúp truy cập method/field theo quyền lookup, nhưng không nên dựa vào việc tạo setter cho final field của record/hidden class. Tham chiếu lookup không phải giấy phép sửa mọi final field.
- Bỏ qua kiểm tra truy cập Java (`setAccessible`) khác với bỏ qua đặc tính **non-modifiable** của field.
- API nội bộ/unsupported hoặc sửa bộ nhớ ngoài cơ chế chuẩn có thể phá vỡ giả định của runtime, gây hỏng dữ liệu và không tạo thành contract ổn định cho ứng dụng.

Nếu cần “cập nhật record”, **tạo record mới** từ dữ liệu cũ và dữ liệu cần thay đổi:

```java
record Position(int x, int y) {}

Position p1 = new Position(2, 3);
Position p2 = new Position(8, p1.y());
// p1 vẫn là Position(2, 3); p2 là Position(8, 3).
```

Đối với hidden class, hãy thiết kế factory/constructor và các trạng thái mutable được khai báo rõ ràng từ đầu nếu cần thay đổi sau tạo; không biến final field thành nơi cập nhật ngầm.

## 10. Khi nào giới hạn này ảnh hưởng framework?

### 10.1. Serialization và deserialization

Nhiều thư viện truyền thống tạo instance rỗng rồi gán trường qua `Field.set`. Chiến lược đó không thể áp dụng nguyên xi cho **record components final**.

Với record, serializer/deserializer hiện đại thường cần:

1. Đọc danh sách `RecordComponent`, tên và kiểu.
2. Thu thập các giá trị constructor arguments từ dữ liệu đầu vào.
3. Gọi **canonical constructor** của record theo đúng thứ tự/kiểu component.
4. Dùng accessor hoặc read-only Reflection khi xuất dữ liệu.

Cơ chế cụ thể tùy thư viện; cần kiểm tra tài liệu phiên bản đang dùng. Không tự sửa `private final` record fields sau khi constructor đã chạy.

### 10.2. Dependency injection, ORM, bytecode tooling

Công cụ tiêm phụ thuộc bằng cách gán field trực tiếp cần nhận biết final fields. Nếu field thuộc record hoặc hidden class thì không thể kỳ vọng `Field.set` hoạt động chỉ vì access suppression đã bật.

Với record, ưu tiên **constructor injection** hoặc biến đổi mô hình thành đối tượng mới theo hợp đồng bất biến. Với hidden class, framework tạo bytecode nên quyết định rõ dữ liệu nào là final trong quá trình định nghĩa, constructor/factory nào khởi tạo nó và dữ liệu nào thực sự mutable.

### 10.3. Công cụ khảo sát và debug

Nếu công cụ chỉ cần **hiển thị metadata** như tên, modifiers, kiểu hoặc declaring class, dùng `getDeclaredFields()` và các phương thức metadata. Không gọi `setAccessible` chỉ để lấy tên field.

Nếu cần đọc private final field, kiểm tra quyền truy cập và phạm vi module. Nếu không được phép, báo rõ **access blocked**. Nếu cần sửa final field của record/hidden class, nên báo **non-modifiable by Reflection** thay vì tiếp tục thử những biến thể `Field.set` khác nhau.

## 11. Những hiểu nhầm phổ biến

**Hiểu nhầm 1: “Có `Field` là sửa được.”** Sai. Có metadata chỉ chứng minh thành viên tồn tại. Khả năng đọc/ghi còn phụ thuộc quyền, receiver, kiểu và quy tắc của final field.

**Hiểu nhầm 2: “`setAccessible(true)` thành công nghĩa là được ghi.”** Sai. Nó chỉ suppress kiểm tra truy cập theo ngôn ngữ Java trong giới hạn runtime; nhóm non-modifiable final fields vẫn chỉ có thể được đọc.

**Hiểu nhầm 3: “Record là class final, nên chỉ có class mới final.”** Sai. Record components còn tương ứng với các private final instance fields; chính các fields này là trọng tâm hạn chế Reflection.

**Hiểu nhầm 4: “Hidden class là class có tên bí mật/private.”** Sai. Hidden class là kiểu do runtime định nghĩa qua cơ chế riêng, có `isHidden() == true` và đặc tính lookup riêng.

**Hiểu nhầm 5: “Chỉ `static final` bị cấm sửa.”** Sai. Trên Java 17, final fields do record và hidden class khai báo cũng bị cấm ghi qua `Field.set`, kể cả field instance.

**Hiểu nhầm 6: “Mọi field của hidden class đều không sửa được.”** Sai. Ràng buộc đặc biệt nói đến **final fields**; non-final fields vẫn xét theo các quy tắc truy cập và gán thông thường.

**Hiểu nhầm 7: “Record bảo đảm mọi thứ bên trong hoàn toàn immutable.”** Sai. Component reference final không tự đóng băng mutable object mà nó trỏ tới; có thể cần defensive copying.

**Hiểu nhầm 8: “`getDeclaringClass()` trả class của receiver là đủ.”** Sai. Field có thể được tìm qua kiểu con nhưng khai báo ở kiểu cha. Quy tắc record/hidden class dựa vào **declaring class của field**.

**Hiểu nhầm 9: “Có thể khắc phục bằng `--add-opens`.”** Sai đối với đặc tính non-modifiable. Mở package có thể giải quyết quyền deep reflection qua ranh giới module, nhưng không cho phép `Field.set` ghi final fields của record/hidden class.

**Hiểu nhầm 10: “`IllegalAccessException` lúc ghi final nghĩa là chương trình không được quyền đọc.”** Không nhất thiết. Ví dụ chạy được phía trên đã đọc thành công rồi mới bị từ chối ghi.

## 12. Checklist thực hành và câu hỏi tự kiểm tra

Khi viết công cụ xử lý Reflection fields, có thể làm theo trình tự:

1. Tìm `Field` theo class và tên phù hợp, ví dụ `getDeclaredField(name)`.
2. Đọc `f.getDeclaringClass()` để xác định **lớp thực sự khai báo** field.
3. Dùng `Modifier.isFinal(f.getModifiers())` và `Modifier.isStatic(...)` để xác định modifier.
4. Nếu `final` và (static hoặc `getDeclaringClass().isRecord()` hoặc `getDeclaringClass().isHidden()`), coi field là **non-modifiable qua `Field.set`**.
5. Nếu chỉ cần đọc giá trị, xét `canAccess(receiver)` và dùng `trySetAccessible()` nếu thật sự cần, nhưng tôn trọng ranh giới module.
6. Ưu tiên accessor/constructor/factory của record và API chính thức của đối tượng; không mặc định đọc/ghi private field.
7. Nếu một framework cần cập nhật, thiết kế lại quá trình tạo instance; đừng dựa vào mutation sau khởi tạo của final field.
8. Phân loại ngoại lệ chính xác: không tìm thấy field, không được mở quyền, receiver sai, hoặc field cố định không thể ghi.

**Câu hỏi tự kiểm tra:**

- `Field.setAccessible(true)` thành công có bảo đảm `Field.set` thành công trên `record Point(int x)` không? Vì sao?
- Sự khác biệt giữa `Class.isRecord()` và `Class.isHidden()` là gì?
- Một field `private int counter` trong hidden class (không `final`) có bị cấm sửa chỉ vì class là hidden không?
- Vì sao class `HiddenTemplate` được khai báo trong source không tự trở thành hidden class? Dòng nào tạo hidden class thật?
- `getDeclaredField` thành công nhưng `set` ném `IllegalAccessException` có thể do những nguyên nhân nào?
- `opens package` có bỏ được cấm ghi final component của record không?
- Làm thế nào đổi `Person("An", 25)` thành một bản ghi mới mang tên `"Bình"` mà không sửa field cũ?
- Tại sao một record có component kiểu `List<String>` vẫn có thể quan sát thấy thay đổi bên trong danh sách đó?

**Kết luận học tập:** Record và hidden class bảo vệ ý nghĩa của **final fields** bằng một hạn chế Reflection riêng. Có thể nhìn thấy, mở quyền để đọc và đọc được field nhưng **`Field.set` vẫn không được ghi**. Hãy xác định declaring class, modifier và mục đích thao tác trước khi chọn phương thức Reflection.

Tài liệu chuẩn để đối chiếu: [Java SE 17 — `Field`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html), [`AccessibleObject`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html), [`Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html), [`MethodHandles.Lookup`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html) và [`RecordComponent`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/RecordComponent.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="final-fields-005">Giá trị hằng có thể đã được nhúng vào mã sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị hằng có thể đã được nhúng vào mã sử dụng

## 1. Vì sao sửa `final` field mà chương trình khác vẫn thấy giá trị cũ?

Trong Java, khi một field đủ điều kiện là **hằng tại thời điểm biên dịch** (*compile-time constant variable*), compiler có thể thay thao tác đọc field bằng chính giá trị hằng ngay trong bytecode của lớp sử dụng. Hiện tượng thường gọi là **constant inlining** hoặc **constant folding tại chỗ sử dụng**.

Hệ quả quan trọng: nếu thư viện thay đổi giá trị một hằng như `public static final int LIMIT = 10` thành `20` và chỉ biên dịch lại thư viện, bytecode của ứng dụng đã được biên dịch trước đó **có thể tiếp tục dùng 10**. Đổi file `.class` chứa field không tự động sửa những giá trị đã được nhúng vào file `.class` của các chương trình sử dụng.

~~~java
public class Limits {
    public static final int MAX_ITEMS = 100;
    public static final String VERSION = "v1";
}
~~~

Với mã sử dụng:

~~~java
System.out.println(Limits.MAX_ITEMS);
System.out.println(Limits.VERSION);
~~~

Hai biểu thức trên có thể được compiler thay bằng literal `100` và `"v1"`. Ở bytecode của caller, có thể **không còn lệnh đọc field `Limits.MAX_ITEMS`/`Limits.VERSION`**. Vì vậy cần phân biệt:

- **Giá trị runtime của field:** giá trị hiện có trong lớp được JVM nạp.
- **Giá trị đã nhúng vào mã sử dụng:** literal được đặt trong bytecode của caller khi caller được biên dịch.
- **Giá trị quan sát qua Reflection:** `Field.get(...)` đọc field của lớp runtime sau khi áp dụng các quy tắc quyền truy cập, không đọc literal đã nhúng trong caller.

Điều này đặc biệt quan trọng với field `final` của API thư viện, module dùng chung, cấu hình dưới dạng constant và các dự án build nhiều module.

## 2. Field nào thực sự là compile-time constant?

Theo quy tắc ngôn ngữ Java, một **constant variable** là biến `final` có kiểu **primitive** hoặc `String`, được khởi tạo bằng **constant expression**. Trong thực tế, các field dễ gặp nhất là `static final` kiểu `int`, `long`, `boolean`, `char`, `String`... gán trực tiếp bằng literal hoặc biểu thức hằng.

| Khai báo | Là constant variable? | Vì sao |
| --- | --- | --- |
| `public static final int PORT = 8080;` | Có | Primitive, `final` và biểu thức literal |
| `public static final int TOTAL = 40 + 2;` | Có | `40 + 2` là constant expression |
| `public static final String LABEL = "core" + "-api";` | Có | Ghép các chuỗi hằng tại compile time |
| `public static final boolean ENABLED = true;` | Có | Primitive, biểu thức hằng |
| `public static final Integer COUNT = 42;` | Không | `Integer` là kiểu tham chiếu khác `String`, dù giá trị biểu diễn số nguyên |
| `public static final int PORT = Integer.parseInt("8080");` | Không | Lời gọi method thông thường không phải constant expression |
| `public static final String LABEL = new String("core");` | Không | Tạo đối tượng bằng `new` không phải constant expression |
| `public static final long CREATED_AT = System.currentTimeMillis();` | Không | Giá trị tính lúc runtime |
| `public final int id = 7;` | Có, xét định nghĩa constant variable | Field instance cũng có thể là constant variable nếu `final`, primitive/`String` và khởi tạo bằng constant expression |
| `public final int id;` được gán trong constructor | Không | Không khởi tạo bằng constant expression ngay tại khai báo |

**Không phải cứ có `final` là sẽ được nhúng.** `final` thể hiện giới hạn gán lại field theo ngôn ngữ, còn khả năng xem như compile-time constant phụ thuộc thêm **kiểu** và **biểu thức khởi tạo**. Hằng `static final` là trường hợp có ảnh hưởng lớn nhất đến API công khai vì nhiều class khác cùng tham chiếu một field.

Để tránh nhầm lẫn, `static final Integer VALUE = 42` **không** là compile-time constant theo quy tắc này. Compiler khi dùng field đó theo cơ chế thông thường phải lấy tham chiếu runtime; autoboxing số 42 ở chỗ khởi tạo không biến một field `Integer` thành constant variable.

## 3. Ví dụ Java 17+: sửa class chứa hằng nhưng không biên dịch lại caller

Bài thực hành này dùng **hai file nằm cùng thư mục**. Thực hiện qua hai vòng biên dịch để nhìn thấy sự khác nhau giữa bytecode của thư viện và bytecode của consumer.

### Bước A — Phiên bản đầu tiên

Tạo file `Constants.java`:

~~~java
public class Constants {
    static {
        System.out.println("Constants initialized");
    }

    public static final int LIMIT = 10;
    public static final String LABEL = "v1";
    public static final int RUNTIME = Integer.parseInt("10");
}
~~~

Tạo file `Consumer.java`:

~~~java
public class Consumer {
    public static void main(String[] args) throws Exception {
        System.out.println("direct LIMIT = " + Constants.LIMIT);
        System.out.println("direct LABEL = " + Constants.LABEL);
        System.out.println("direct RUNTIME = " + Constants.RUNTIME);

        System.out.println("reflection LIMIT = "
                + Constants.class.getField("LIMIT").getInt(null));
        System.out.println("reflection LABEL = "
                + Constants.class.getField("LABEL").get(null));
    }
}
~~~

Trong terminal tại thư mục chứa hai file:

~~~bash
javac --release 17 Constants.java Consumer.java
java Consumer
~~~

Kết quả:

~~~text
direct LIMIT = 10
direct LABEL = v1
Constants initialized
direct RUNTIME = 10
reflection LIMIT = 10
reflection LABEL = v1
~~~

Giải thích: `LIMIT` và `LABEL` là compile-time constants. Hai dòng `direct` đầu tiên được tạo từ giá trị có thể nhúng ngay vào bytecode của `Consumer`; chúng **không làm `Constants` được khởi tạo**. Đến dòng dùng `Constants.RUNTIME`, JVM phải đọc field runtime bằng cơ chế truy cập static field, nên lớp `Constants` được khởi tạo và static initializer in `Constants initialized`. Reflection ở cuối đọc đúng giá trị field của lớp đã nạp.

### Bước B — Đổi thư viện, không biên dịch lại `Consumer`

**Chỉ thay nội dung `Constants.java`** bằng:

~~~java
public class Constants {
    static {
        System.out.println("Constants initialized");
    }

    public static final int LIMIT = 20;
    public static final String LABEL = "v2";
    public static final int RUNTIME = Integer.parseInt("20");
}
~~~

Sau đó chỉ chạy:

~~~bash
javac --release 17 Constants.java
java Consumer
~~~

Chú ý **không** chạy lại `javac Consumer.java` ở bước này. `Consumer.class` cũ vẫn tồn tại.

Kết quả đã kiểm tra với Java 21:

~~~text
direct LIMIT = 10
direct LABEL = v1
Constants initialized
direct RUNTIME = 20
reflection LIMIT = 20
reflection LABEL = v2
~~~

Cùng một lần chạy, chương trình vừa thấy `10`, `"v1"` qua code sử dụng đã biên dịch từ trước, vừa thấy `20`, `"v2"` trong field hiện tại. Đây không phải JVM “ghi nhớ sai” field; bytecode `Consumer.class` vẫn chứa các literal cũ.

- `Constants.LIMIT` và `Constants.LABEL` ở hai câu lệnh `direct` đã được biên dịch thành các chuỗi đầu ra chứa literal cũ.
- `Constants.RUNTIME` **không** là constant variable vì được khởi tạo bằng `Integer.parseInt(...)`; caller dùng phép đọc field runtime nên thấy `20` từ `Constants.class` mới.
- `Constants.class.getField("LIMIT").getInt(null)` và `getField("LABEL").get(null)` đọc metadata/giá trị field từ lớp `Constants` thực sự được nạp tại runtime. Chúng thấy phiên bản mới.
- Trong ví dụ, `Constants` là public class và các field đều public; `getField` thành công và `getInt(null)` hợp lệ với static field primitive `int`.

### Bước C — Biên dịch lại caller

Sau khi đã cập nhật `Constants.java`, tiếp tục:

~~~bash
javac --release 17 Consumer.java
java Consumer
~~~

Giờ hai dòng `direct` đọc theo hằng mới vì `Consumer.class` được biên dịch lại:

~~~text
direct LIMIT = 20
direct LABEL = v2
Constants initialized
direct RUNTIME = 20
reflection LIMIT = 20
reflection LABEL = v2
~~~

Quy tắc thực hành: khi **thay giá trị public compile-time constant** trong thư viện, cần **biên dịch lại cả các consumer liên quan** để chắc chắn bytecode của họ không giữ literal cũ.

## 4. Nhìn vào bytecode để hiểu nguyên nhân

Sau bước B (trước khi biên dịch lại `Consumer`), chạy:

~~~bash
javap -c Consumer
~~~

Trong bytecode của `main` sẽ thấy phần tương đương:

- Lệnh `ldc` nạp chuỗi đã tạo sẵn `"direct LIMIT = 10"`.
- Lệnh `ldc` nạp chuỗi đã tạo sẵn `"direct LABEL = v1"`.
- Với `RUNTIME`, lệnh `getstatic Constants.RUNTIME:I` đọc trường số nguyên của lớp `Constants` lúc runtime.

`javap` có thể hiển thị thêm chỉ số constant pool và các lệnh `invokedynamic` phục vụ nối chuỗi; vị trí/chỉ số bytecode có thể khác tùy JDK. Điều cần kiểm chứng là **không có `getstatic` đến `Constants.LIMIT` hay `Constants.LABEL` trong hai biểu thức direct đã được compiler rút gọn**. Vì caller không thực hiện thao tác đọc static field cho hai hằng đó, thay đổi giá trị field trong class khác không sửa được các literal trong bytecode cũ.

Đây là một tối ưu hóa và ngữ nghĩa được ngôn ngữ Java cho phép; không nên quy kết chung cho JIT cache. JIT có thể thực hiện các tối ưu khác ở runtime, nhưng ví dụ trên tái hiện vấn đề ngay trong **file `Consumer.class` sau bước compile**.

## 5. Reflection nhìn thấy gì, và có nên sửa `final` bằng Reflection?

Với `public static final int LIMIT = 20`:

~~~java
java.lang.reflect.Field field = Constants.class.getField("LIMIT");
System.out.println(field.getType());  // int
System.out.println(field.getInt(null)); // 20
System.out.println(field.get(null));    // Integer chứa 20
~~~

Đây là **đoạn minh họa** dùng lại `Constants` phiên bản ở bước B, không phải chương trình độc lập. `getInt(null)` đọc primitive `int` trực tiếp; `get(null)` trả `Object` nên boxing thành `Integer`. Đối với static field, receiver có thể là `null`.

Điều Reflection **không** làm là thay literal đã nhúng trong bytecode `Consumer.class`. `getInt(null)` phản ánh giá trị field đang tồn tại; còn câu lệnh `System.out.println("direct LIMIT = " + Constants.LIMIT)` của caller cũ đã được dịch sang chuỗi chứa `10`.

**Đừng cố sửa `static final` bằng `Field.set` để “cập nhật mọi nơi”.** Trong Java hiện đại, `static final` là nhóm final field không được phép ghi qua Reflection, kể cả khi `setAccessible(true)` có thể được chấp nhận cho mục đích đọc. Thêm nữa, ngay cả một cơ chế có thể chỉnh một giá trị nào đó ở runtime cũng **không thể sửa những literal đã có sẵn trong caller bytecode**. Mở quyền truy cập không đồng nghĩa thay đổi cách compiler tạo và sử dụng compile-time constants.

Nếu cần cập nhật giá trị sau khi phát hành, hãy thiết kế điểm truy cập runtime chính thức (method, cấu hình, dịch vụ/registry có lifecycle rõ ràng) thay vì dùng một public compile-time constant như giá trị cấu hình có thể thay đổi.

## 6. Đọc `static final` có luôn kích hoạt class initialization không?

**Không.** Theo quy tắc Java về khởi tạo class, việc sử dụng một `static final` **constant variable** có thể được biên dịch thành literal và không phải thao tác kích hoạt khởi tạo class sở hữu field.

Trong ví dụ:

~~~java
System.out.println("direct LIMIT = " + Constants.LIMIT);
System.out.println("direct LABEL = " + Constants.LABEL);
~~~

Không có dòng `Constants initialized` xen giữa hai lệnh này. Lớp `Constants` chỉ được khởi tạo khi chương trình đọc `Constants.RUNTIME`, là field `static final` **không** phải constant variable vì nó được tính bằng lời gọi method lúc runtime.

Phân biệt:

| Phép sử dụng | Kết quả thường gặp |
| --- | --- |
| Tham chiếu `Constants.LIMIT` compile-time constant qua code đã biên dịch | Bytecode dùng literal; không cần đọc field runtime và không khởi tạo lớp vì phép đọc đó |
| Tham chiếu `Constants.RUNTIME` có initializer không hằng | Lệnh `getstatic`; JVM khởi tạo class trước khi đọc theo quy tắc JVM |
| Lấy `Constants.class` | Class literal không tự nó kích hoạt class initialization |
| Dùng Reflection để lấy giá trị static field bằng `Field.get` | Thao tác đọc static field có thể kích hoạt khởi tạo class nếu chưa khởi tạo |
| Gọi static method hoặc tạo instance | Thường là thao tác kích hoạt class initialization theo quy tắc Java |

Vì vậy logging trong static initializer **không phải phép đo tin cậy** cho việc caller đã đọc một compile-time constant hay chưa. Compiler có thể loại bỏ hoàn toàn truy cập field tại runtime.

## 7. Ảnh hưởng đến binary compatibility và versioning

Thay một constant từ `10` thành `20` không nhất thiết tạo lỗi liên kết (`LinkageError`). Ứng dụng vẫn có thể chạy với file `Constants.class` mới nhưng giữ giá trị cũ do đã được nhúng tại thời điểm biên dịch. Đây là nguy cơ **thay đổi hành vi khi cập nhật phiên bản** rất dễ bị bỏ sót.

Một tình huống điển hình:

1. Thư viện `api-v1.jar` công bố `public static final int MAX_RETRY = 3`.
2. Dịch vụ A biên dịch với `api-v1.jar`; bytecode A mang literal `3` tại các chỗ dùng compile-time constant.
3. Thư viện phát hành `api-v2.jar` thay `MAX_RETRY = 5`.
4. Nếu chỉ thay JAR thư viện và chạy lại dịch vụ A mà **không biên dịch lại A**, A vẫn có thể dùng `3` trong logic đã compile.
5. Recompile toàn bộ consumer với `api-v2.jar` mới làm các literal đó được cập nhật thành `5`.

Do vậy, với hằng công khai là một phần của hợp đồng giữa module, **việc thay giá trị phải được đánh giá như thay đổi API về mặt hành vi**. Build sạch/rebuild và kiểm tra bytecode giúp phát hiện trạng thái trộn giữa mã tiêu thụ cũ và thư viện mới.

Nếu một giá trị cần có thể thay đổi theo cấu hình, phiên bản triển khai hoặc môi trường, cân nhắc API runtime:

~~~java
public final class RuntimeSettings {
    private RuntimeSettings() {}

    public static int maxRetry() {
        return Integer.getInteger("app.maxRetry", 3);
    }
}
~~~

Đoạn code trên là một ví dụ API runtime; gọi `RuntimeSettings.maxRetry()` đọc property hệ thống hiện tại (nếu không phải số hợp lệ thì `Integer.getInteger` trả giá trị mặc định theo hợp đồng API). Đây không phải compile-time constant nên caller không thể nhúng sẵn kết quả như literal từ field constant. Thiết kế cấu hình thực tế phải tính đến validation, caching và thời điểm cho phép thay đổi giá trị.

Một lựa chọn khác khi dữ liệu **cố ý bất biến suốt vòng đời một phiên bản ứng dụng** là dùng `static final` như trước, nhưng phải xác định rõ consumer cần được biên dịch lại khi thay đổi giá trị.

## 8. Các hiểu lầm thường gặp

| Nhầm lẫn | Hiểu đúng |
| --- | --- |
| “`final` tức là compiler luôn nhúng giá trị.” | Chỉ constant variables đáp ứng kiểu primitive/`String` + biểu thức khởi tạo hằng mới có cơ chế này; field final khác vẫn có thể được đọc ở runtime |
| “`static final Integer N = 1` là hằng giống `static final int N = 1`.” | `Integer` không phải primitive hoặc `String`, nên `N` không phải constant variable |
| “Đã đổi JAR chứa field thì mọi chương trình lập tức thấy giá trị mới.” | Consumer cũ có thể giữ literal đã nhúng; cần recompile các consumer |
| “Dùng `final` đảm bảo mọi field access đều chạy `getstatic`.” | Compile-time constant có thể biến thành literal và không còn `getstatic` ở caller |
| “Reflection luôn trả cùng giá trị với code gọi field trực tiếp.” | `Field.get` đọc field runtime; code cũ có thể dùng literal nhúng |
| “Sửa field bằng Reflection sẽ cập nhật được hằng trong consumer.” | Không: code consumer có thể không còn đọc field. Ghi static final bằng Reflection còn bị hạn chế độc lập |
| “`static final` lúc nào cũng khởi tạo class khi truy cập.” | Tham chiếu compile-time constant không kích hoạt khởi tạo class nếu đã được biên dịch thành literal; static field không hằng được đọc runtime |
| “`String` nối bằng `+` luôn được tính ở runtime.” | Nếu mọi thành phần là constant expression, phép nối có thể được tính ngay khi compile |

### Cách chẩn đoán một lỗi thực tế

Nếu một thành phần báo giá trị cấu hình cũ sau khi cập nhật thư viện, hãy kiểm tra theo trình tự:

1. Field có phải `final`, kiểu primitive/`String` và initializer constant expression không?
2. Component đang chạy có phải bytecode cũ của consumer được biên dịch với giá trị trước đây không?
3. Dùng `javap -c Consumer` để tìm literal hoặc `getstatic` tại chính điểm sử dụng.
4. Đọc field qua Reflection ở runtime (trong điều kiện có quyền) để đối chiếu giá trị hiện tại của class.
5. Biên dịch lại tất cả consumer liên quan và triển khai phiên bản tương ứng; kiểm tra JAR/classpath để tránh trộn phiên bản.

## 9. Câu hỏi tự kiểm tra

**1.** `public static final String NAME = "A" + "B";` có thể được nhúng vào caller không? **Có**: phép nối các string literal là constant expression, kết quả `"AB"` có thể xuất hiện trong bytecode consumer.

**2.** `public static final String NAME = System.getProperty("name");` có giống vậy không? **Không**: method call được tính ở runtime, không phải constant expression.

**3.** Vì sao `Field.getInt(null)` thấy `20` trong khi `Consumer` vẫn in `10`? Reflection đọc field của class runtime mới; `Consumer.class` cũ dùng literal đã nhúng trước khi thư viện thay đổi.

**4.** Có thể chỉ cập nhật file thư viện mà không biên dịch lại caller để nhận giá trị hằng mới không? **Không thể bảo đảm** với compile-time constant: caller có thể không còn truy cập field runtime. Phương án chắc chắn là recompile consumer.

**5.** `final int id = 7` ở instance có phải không bao giờ là constant variable vì thiếu `static`? **Không**: định nghĩa constant variable không bắt buộc `static`, dù bài toán nhúng giá trị xuyên các lớp độc lập thường nổi bật nhất với `public static final`.

**Ghi nhớ:** `final` và *compile-time constant* là hai khái niệm có liên hệ nhưng không đồng nhất. Khi đã có constant inlining, thay đổi field sau đó không sửa được các literal trong bytecode consumer; vì vậy việc phát hành hằng public và việc build lại caller phải được xem xét cùng nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="final-fields-006">Không xem sửa field là thay thế constructor và bất biến đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Không xem sửa field là thay thế constructor và bất biến đối tượng

## 1. Bản chất vấn đề

Trong Java Reflection, `Field` cho phép quan sát metadata của trường và, trong những trường hợp được phép, thay đổi giá trị bằng `Field.set(Object, Object)`. Tuy nhiên, **ghi một field sau khi object đã được tạo ra không tương đương với việc khởi tạo object qua constructor**. Constructor là nơi tập trung quy tắc hợp lệ của dữ liệu, chuẩn hóa, tạo defensive copy, và thiết lập trạng thái nhất quán trước khi object được công bố để sử dụng.

Hãy tưởng tượng một đơn hàng có `amountCents >= 0`, mã đơn không rỗng và danh sách sản phẩm cố định. Nếu ai đó dùng Reflection viết trực tiếp `amountCents = -100` lên một object đã xây dựng, họ có thể làm sai invariant dù field đó khai báo `private final`. Nếu thay đổi danh sách bên trong mà không qua quy trình kiểm tra, các quy tắc liên quan cũng có thể không còn đúng.

Bài này trả lời các câu hỏi thực tế:

- Constructor tạo ra các bảo đảm gì mà `Field.set(...)` không tự tạo ra?
- `final` có nghĩa là bất biến hoàn toàn không? Có thể sửa final field bằng Reflection trong trường hợp nào?
- Vì sao “gọi Reflection thành công” không chứng minh object được khởi tạo hợp lệ hoặc an toàn giữa nhiều luồng?
- Nên thay đổi dữ liệu bằng cách nào để vẫn bảo toàn invariant?

**Nguyên tắc làm việc:** tạo object hợp lệ qua constructor/factory/builder, sau đó dùng API có kiểm soát để thay đổi trạng thái được phép; với object bất biến, tạo **instance mới** thay vì can thiệp vào field cũ.

## 2. Ba khái niệm cần phân biệt

### 2.1. Constructor thiết lập invariant

**Invariant** là điều kiện mà mọi object ở trạng thái sử dụng hợp lệ phải thỏa mãn. Ví dụ đối với `Receipt`:

- `orderId` không được `null` hoặc rỗng.
- `totalCents` không âm.
- `items` phải có ít nhất một sản phẩm.
- Danh sách sản phẩm không được thay đổi ngoài ý muốn sau khi object được tạo ra.

Constructor có thể kiểm tra tất cả điều kiện này **trước khi trả object cho caller**. Sau đó method hoặc factory của lớp có thể duy trì quy tắc tương tự khi cần thay đổi dữ liệu.

### 2.2. `final` bảo vệ phép gán vào biến/field, không tự bảo vệ toàn bộ đồ thị object

Với `private final int totalCents`, Java bình thường yêu cầu field được gán trong quá trình khởi tạo hợp lệ và không cho method Java thông thường gán lại nó về sau.

Với `private final List<String> items`, `final` **chỉ cố định tham chiếu** đến đối tượng `List`; nó không tự khóa nội dung của danh sách. Nếu nhận một `ArrayList` từ caller rồi giữ nguyên tham chiếu, caller vẫn có thể thêm/xóa phần tử, dù field chứa nó là `final`.

Bất biến cấp đối tượng cần thêm: không lộ tham chiếu mutable, defensive copy đầu vào/đầu ra, kiểm tra tất cả dữ liệu đầu vào, và không để phương thức cập nhật phá invariant.

### 2.3. `Field.set` là thao tác mức thấp lên trạng thái

`Field.set(receiver, value)` xét metadata field, receiver, quyền truy cập và khả năng gán giá trị/kiểu dữ liệu. Nó **không tự gọi lại constructor** của receiver, không tự áp dụng quy tắc nghiệp vụ, không kích hoạt validator tùy chỉnh, và không thực hiện defensive copy theo ý định thiết kế của class.

Ngay cả khi một lần ghi được Java Reflection chấp nhận, giá trị mới vẫn có thể làm object không hợp lệ theo domain. Vì vậy khả năng *thao tác kỹ thuật* không đồng nghĩa *quyền thay đổi hợp lệ theo mô hình đối tượng*.

## 3. Chương trình Java 17+: bảo toàn invariant bằng constructor và tạo instance mới

Lưu đoạn sau vào file **`FinalFieldGuidanceDemo.java`**. Đây là chương trình độc lập chỉ sử dụng thư viện chuẩn Java:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class FinalFieldGuidanceDemo {
    static final class Receipt {
        private final String orderId;
        private final List<String> items;
        private final int totalCents;

        Receipt(String orderId, List<String> items, int totalCents) {
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalArgumentException("orderId must not be blank");
            }
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("items must not be empty");
            }
            if (totalCents < 0) {
                throw new IllegalArgumentException("totalCents must be >= 0");
            }
            this.orderId = orderId;
            this.items = List.copyOf(items);
            this.totalCents = totalCents;
        }

        Receipt withTotal(int nextTotalCents) {
            return new Receipt(orderId, items, nextTotalCents);
        }

        String summary() {
            return orderId + " | " + items + " | " + totalCents;
        }
    }

    public static void main(String[] args) throws NoSuchFieldException {
        List<String> supplied = new ArrayList<>(List.of("Book"));
        Receipt original = new Receipt("ORD-1", supplied, 1200);

        supplied.add("Pen"); // Không làm thay đổi danh sách trong Receipt.
        System.out.println("Original: " + original.summary());

        Receipt updated = original.withTotal(1500);
        System.out.println("Updated: " + updated.summary());
        System.out.println("Original remains: " + original.summary());

        Field totalField = Receipt.class.getDeclaredField("totalCents");
        System.out.println("Declared final: "
                + Modifier.isFinal(totalField.getModifiers()));

        try {
            new Receipt("ORD-2", List.of("Pen"), -50);
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid total: rejected");
        }

        try {
            original.items.add("Tape");
        } catch (UnsupportedOperationException e) {
            System.out.println("Mutating items: rejected");
        }
    }
}
```

Chạy tại thư mục chứa file:

```bash
javac --release 17 FinalFieldGuidanceDemo.java
java FinalFieldGuidanceDemo
```

Kết quả chuẩn:

```text
Original: ORD-1 | [Book] | 1200
Updated: ORD-1 | [Book] | 1500
Original remains: ORD-1 | [Book] | 1200
Declared final: true
Invalid total: rejected
Mutating items: rejected
```

**Ý nghĩa của ví dụ:**

1. Constructor kiểm tra mã đơn, danh sách và tổng tiền. Giá trị âm được từ chối ngay lúc tạo, nên không tồn tại instance hợp lệ có `totalCents < 0` do đường khởi tạo thông thường tạo ra.
2. `List.copyOf(items)` tạo dữ liệu lưu trữ không thể sửa qua các phép thêm/xóa, bảo vệ trước thay đổi từ danh sách caller cung cấp. Với phần tử kiểu `String` vốn bất biến, đây là cách phòng vệ đủ cho ví dụ. Với phần tử là object có trạng thái mutable, cần thêm chính sách sao chép sâu/thiết kế bất biến thích hợp.
3. `withTotal(...)` dựng một `Receipt` **mới** thông qua constructor, nên mọi invariant vẫn được kiểm tra. `original` không thay đổi; `updated` có giá trị mới.
4. `getDeclaredField("totalCents")` và `Modifier.isFinal(...)` đọc **metadata**, không can thiệp vào dữ liệu. Field là `private final` mà chương trình vẫn tìm được metadata.
5. Việc gọi `original.items.add(...)` bị chặn bởi danh sách bất biến. Trong chương trình, class bao ngoài được phép truy cập private member của nested class theo quy tắc nestmate; caller bên ngoài không nên trực tiếp đụng vào field private này.

**Thiết kế cần nhớ:** nếu có nghiệp vụ sửa tổng tiền, quyết định API là `withTotal(...)` hoặc một phương thức cập nhật có validation, chứ không phải thao tác `Field.set(...)` trực tiếp từ code sử dụng.

## 4. Cơ chế của `final` và Reflection trên Java 17–21

### 4.1. Khi nào `Field.set(...)` có thể thay đổi một final field?

Theo hợp đồng `java.lang.reflect.Field.set` ở Java 17/21, ghi field `final` bằng Reflection chỉ được phép trong điều kiện hẹp: việc tắt kiểm tra truy cập cho `Field` bằng `setAccessible(true)` đã **thành công**, field **không static**, và lớp khai báo **không phải hidden class hoặc record class**. Nếu không đáp ứng, `set` ném `IllegalAccessException`. Hơn nữa, `setAccessible(true)` tự nó có thể không được cho phép do ranh giới package/module.

| Kiểu field | Ý nghĩa thực tế |
| --- | --- |
| `private final int n` trong instance class thông thường | Có thể được phép ghi bởi Reflection **nếu** suppression hợp lệ và các điều kiện API khác thỏa mãn; không phải cách sửa domain object |
| `static final int N` | Không thể dùng `Field.set` để ghi như một instance final field; kể cả suppression không cấp write access cho static final |
| Thành phần record (`record Receipt(...)`) | Field final của record thuộc nhóm không cho viết qua `Field.set` |
| Final field khai báo trong hidden class | Cũng thuộc nhóm không cho viết qua `Field.set` |
| Instance field không final | Có thể ghi nếu receiver, kiểu giá trị và quyền truy cập phù hợp; vẫn có thể phá invariant nếu bypass API |

Không được kết luận “`private final` thì Reflection luôn không sửa được”, cũng không được kết luận “`setAccessible(true)` sẽ luôn sửa được mọi thứ”. Cả hai phát biểu đều sai về phạm vi.

### 4.2. Ví dụ **phản mẫu**: bypass validation

Đoạn sau chỉ minh họa vấn đề; **không phải đoạn cần chạy để cập nhật object trong ứng dụng**. Giả sử đang trong `main` của chương trình ở mục 3 và có biến `original`:

```java
Field f = Receipt.class.getDeclaredField("totalCents");

// Chỉ khi được phép bởi access/module rules; có thể bị từ chối.
if (f.trySetAccessible()) {
    // Phản mẫu: bỏ qua validation trong Receipt constructor.
    f.set(original, -100);
}
```

Nếu lệnh ghi thực sự thành công trong môi trường hỗ trợ trường hợp này, dữ liệu có thể vi phạm `totalCents >= 0`. Đặc biệt, **không dựa vào các getter/instructions tiếp theo để chứng minh mọi nơi đã thấy giá trị mới**, vì cơ chế tối ưu hóa đối với `final` khiến việc sửa sau khởi tạo có những hệ quả khó dự đoán.

Đừng nhầm `trySetAccessible()` trả `true` với việc field đã được ghi thành công: nó chỉ cho biết nỗ lực bật suppression thành công, còn `Field.set(...)` có thể thất bại ở điều kiện final, receiver hoặc kiểu dữ liệu.

### 4.3. `setAccessible` và JPMS không phải cơ chế chuyển một API kín thành public

Với named module (JPMS), quyền deep reflection còn phụ thuộc package có được `opens` cho module đang gọi hay không. `exports` hỗ trợ tiếp cận public API trong các điều kiện thông thường, nhưng không tự cho phép chỉnh sửa private final field. Nhiều thư viện framework phải cấu hình `opens` rõ ràng nếu thực sự cần deep reflection.

Ngay cả khi có quyền deep reflection, **việc sửa field bỏ qua constructor vẫn không được tự động xem là hợp lệ** về nghiệp vụ và an toàn đồng thời.

## 5. Vì sao constructor có ý nghĩa đặc biệt trong Java Memory Model?

Java Language Specification (JLS `17.5) quy định **final field semantics** cho các field final được khởi tạo đúng cách. Khi constructor hoàn tất, một **freeze action** gắn với final field giúp hỗ trợ việc quan sát các giá trị đã khởi tạo đúng trong điều kiện mô hình bộ nhớ quy định. Quy tắc sử dụng cơ bản:

1. Gán các final field trong quá trình khởi tạo hợp lệ.
2. Không làm rò rỉ tham chiếu `this` tới luồng/đối tượng khác **trước khi constructor kết thúc**.
3. Sau khi khởi tạo, không xem final field là nơi lưu trạng thái cần liên tục thay đổi.

Đây là một lý do `final` hỗ trợ thiết kế immutable class an toàn giữa các luồng. Tuy nhiên, **final-field guarantees không phải phép màu bảo đảm mọi object đa luồng đều thread-safe**. Nếu final field trỏ đến một object mutable, các thay đổi sau đó trên object được trỏ tới vẫn phải tuân thủ quy tắc đồng bộ riêng.

### 5.1. Phép ghi phản chiếu sau constructor có semantics đặc biệt và hạn chế

JLS `17.5.3 thừa nhận vài trường hợp đặc biệt như deserialization/reconstruction phải điều chỉnh final field sau khi tạo object. Mô hình có ý nghĩa trong phạm vi hẹp là:

- Object đang ở giai đoạn tái dựng nội bộ, **chưa công bố cho các thành phần/luồng khác**.
- Hoàn tất tất cả cập nhật final field trước khi bất kỳ phần nào đọc những field ấy.
- Sau đó mới cho hệ thống truy cập object đã hoàn chỉnh.

JLS mô tả freeze sau việc sửa final field bằng một số cơ chế đặc biệt, nhưng cũng cảnh báo các phép đọc sau đó có thể bị tối ưu hóa/reorder làm khó dự đoán nếu dùng theo cách tùy tiện. **Không suy ra rằng ghi bằng Reflection sau khi object được sử dụng rộng rãi có semantics giống gán trong constructor**.

### 5.2. Vì sao không nên sửa final field của object đã công bố?

Nếu object đã được chia sẻ cho nhiều luồng, hoặc code đã đọc giá trị final của nó, sau đó một luồng dùng Reflection ghi lại final field, các luồng/code khác có thể đã giữ/hoặc được compiler tối ưu hóa quanh giá trị cũ. Kết quả quan sát có thể không như mong đợi; đây là lý do các API Java cảnh báo việc dùng Reflection để thay đổi final field ngoài quá trình tái dựng có thể cho kết quả khó dự đoán.

Điều này **không thể sửa chỉ bằng việc thêm `volatile` hay gọi `synchronized` một cách tùy tiện** sau hành động bypass; mô hình đúng là ngăn thực thể bất biến bị sửa sau khi nó được sử dụng. Nếu dữ liệu cần thay đổi thường xuyên, dùng trạng thái mutable được đồng bộ và thiết kế API phù hợp từ đầu, hoặc thay cả tham chiếu tới object bất biến mới bằng cơ chế công bố an toàn.

## 6. Bất biến đối tượng không chỉ là tất cả field mang `final`

### 6.1. Field final giữ tham chiếu mutable

Phản mẫu:

```java
final class BadCart {
    private final java.util.List<String> products;

    BadCart(java.util.List<String> products) {
        this.products = products; // Giữ nguyên alias mutable của caller.
    }

    java.util.List<String> products() {
        return products; // Lộ alias mutable ra ngoài.
    }
}
```

Cách khai báo này cho phép caller sửa danh sách đã truyền vào hoặc danh sách trả về, khiến nội dung thay đổi dù `products` là `final`. Sửa bằng `List.copyOf(products)` khi phù hợp (lưu ý shallow copy) và không trả lại cấu trúc mutable có thể sửa từ bên ngoài.

### 6.2. Invariant liên trường

Với khoảng giá trị `start <= end`, không thể coi cập nhật trực tiếp từng field là tương đương tạo một object hợp lệ. Ví dụ:

```java
public record Range(int start, int end) {
    public Range {
        if (start > end) {
            throw new IllegalArgumentException("start > end");
        }
    }

    public Range withEnd(int newEnd) {
        return new Range(start, newEnd);
    }
}
```

Đây là **khai báo độc lập** cho file `Range.java` trong Java 17+. Compact canonical constructor của record luôn kiểm tra quan hệ `start <= end`. `withEnd` tạo record mới và kiểm tra lại. Trường thành phần record là final và không thuộc nhóm mà `Field.set` được phép ghi.

Với object có nhiều field phụ thuộc nhau, code cập nhật cần xử lý cả invariant và trạng thái trung gian. Với immutable data, tạo instance mới thường giúp loại bỏ trạng thái “nửa cập nhật” có thể bị quan sát.

## 7. So sánh các phương án thay đổi dữ liệu

| Nhu cầu | Phương án phù hợp | Vì sao |
| --- | --- | --- |
| Tạo instance lần đầu | Constructor/factory/builder có validation | Có thể bảo đảm object hợp lệ trước khi trả ra |
| Điều chỉnh giá trị trên immutable object | `withX(...)` hoặc factory trả instance mới | Duy trì kiểm tra, giữ object cũ ổn định |
| Thay đổi trạng thái mutable object | Method nghiệp vụ có validation và đồng bộ khi cần | Giữ invariant và kiểm soát tính đồng thời |
| Hiển thị/đọc metadata | `Field.getName`, `getType`, `getModifiers`, `getDeclaringClass` | Có thể phân tích cấu trúc mà không đổi dữ liệu |
| Đọc field khi được phép | `Field.get(receiver)` | Chỉ quan sát dữ liệu; vẫn phải tôn trọng quyền truy cập |
| Framework tái dựng object đặc thù | Cơ chế serialization đã thiết kế riêng, với quy tắc lifecycle và visibility | Chỉ dùng low-level mutation trước khi công bố object và theo giới hạn runtime |
| Sửa trực tiếp private final field của object đang chạy | Tránh; thiết kế lại API cập nhật | Có nguy cơ phá invariant và final-field semantics |

**Lưu ý:** không phải mọi thao tác Reflection đều nguy hiểm hoặc không nên dùng. Lấy metadata, dependency injection hay deserialization có phạm vi hợp lệ nếu tuân theo hợp đồng API/runtime. Bài học cụ thể ở đây là không thay thế vòng đời khởi tạo và luật bất biến bằng việc tự do sửa field.

## 8. Các lỗi nhận thức và hậu quả

| Nhận định sai | Sửa lại cho đúng |
| --- | --- |
| “`Field.set` thành công thì object hợp lệ.” | Reflection không tự chạy validation của constructor/method nghiệp vụ |
| “Tất cả field đều `final` thì object chắc chắn immutable.” | Mutable object bên trong, alias và việc lộ `this` có thể phá an toàn |
| “`setAccessible(true)` luôn vượt mọi quyền truy cập.” | JPMS, loại field, record/hidden class và hạn chế runtime có thể chặn |
| “`final` luôn khiến Reflection không sửa được.” | Một số instance final field trong ordinary class có thể ghi trong điều kiện hẹp |
| “Sửa final field sau khi chia sẻ object sẽ được tất cả luồng thấy giống như cập nhật `volatile`.” | Final-field semantics và tối ưu hóa JIT không cho phép suy luận đó |
| “Serialization cần Reflection nên mọi domain update cũng nên sửa field.” | Tái dựng object trước khi công bố có lifecycle rất khác sửa object đang được sử dụng |
| “Muốn chỉnh một thuộc tính immutable thì sửa field cũ cho đỡ cấp phát.” | Dùng object mới hợp lệ và quản lý việc công bố tham chiếu mới |
| “Một constructor gán field là đủ; không cần kiểm tra liên trường.” | Constructor phải xác minh các invariant cần thiết, kể cả quan hệ giữa các field |

## 9. Quy trình ra quyết định khi gặp một yêu cầu “sửa field”

**Bước 1:** Xác định đây là field instance hay static, `final` hay mutable, của class thường, record hay hidden class.

**Bước 2:** Xác định yêu cầu là đọc metadata, đọc giá trị, tái dựng object từ dữ liệu lưu trữ, hay **cập nhật nghiệp vụ trên object đã tồn tại**. Đây là các mục đích khác nhau.

**Bước 3:** Với cập nhật nghiệp vụ, tìm constructor/factory, getter, `withX` hoặc method cập nhật được thiết kế cho invariant. Nếu chưa có, thiết kế API phù hợp và thêm validation; không để caller tự tiện ghi vào trường private.

**Bước 4:** Nếu dữ liệu có reference mutable, kiểm tra defensive copying và alias. Nếu nhiều luồng có thể truy cập object, xác định cơ chế công bố và đồng bộ trạng thái.

**Bước 5:** Chỉ khi làm công cụ tái dựng/deserialization đặc thù mới xét low-level field mutation; phải đọc hợp đồng `Field.set` của JDK đang dùng, giới hạn module, điều kiện final field và lifecycle “trước khi công bố”. Không tự suy luận từ kết quả một lần chạy hoặc từ một JVM cụ thể.

## 10. Câu hỏi tự kiểm tra

1. `private final List<String> items` có bảo đảm caller không thể sửa nội dung `items` không? Tại sao?
2. Constructor của `Receipt` từ chối `totalCents < 0`. Nếu code khác ghi trực tiếp field thành `-100`, invariant còn đúng không?
3. Tại sao Java Reflection tìm được một private final field bằng `getDeclaredField` mà chưa chắc ghi được bằng `set`?
4. `static final`, record component và instance final field của ordinary class có cùng khả năng ghi bằng `Field.set` không?
5. Vì sao việc Reflection sửa final field sau khi object đã được dùng bởi các luồng không thể coi là cập nhật bình thường?
6. Nếu cần thay `totalCents` của `Receipt` một cách hợp lệ, cách nào được dùng trong chương trình chạy độc lập?

**Đáp án:** (1) không; `final` chỉ cố định tham chiếu, phải ngăn mutable alias; (2) không; (3) tra cứu metadata và kiểm tra quyền ghi là hai giai đoạn khác nhau; (4) không; (5) final-field/JMM semantics và tối ưu hóa cho phép kết quả quan sát không như mong đợi; (6) `original.withTotal(newTotal)` tạo một `Receipt` mới qua constructor.

## 11. Kết luận và tài liệu kỹ thuật

**Constructor và factory là ranh giới tạo ra trạng thái hợp lệ; `final` hỗ trợ thiết kế trạng thái ổn định; Reflection là công cụ truy cập metadata và field theo các quy tắc nghiêm ngặt.** Việc một final instance field có thể bị sửa trong trường hợp đặc biệt không làm cho `Field.set` trở thành API thay thế constructor hoặc phương pháp chỉnh sửa immutable object. Luôn duy trì invariant, kiểm soát alias và lifecycle công bố object trước khi nghĩ tới phép sửa field ở mức thấp.

Tham khảo đặc tả chính thức:

- [Java SE 21 – `java.lang.reflect.Field.set`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html#set(java.lang.Object,java.lang.Object)): điều kiện ghi field và giới hạn đối với final field.
- [Java Language Specification 17 – `17.5 final Field Semantics`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.5): ý nghĩa final field trong Java Memory Model.
- [JLS `17.5.3 – Subsequent Modification of final Fields](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.5.3): trường hợp tái dựng đặc thù và rủi ro khi sửa final field sau constructor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="final-fields-007">Ảnh hưởng tới khả năng quan sát, tối ưu và hành vi phụ thuộc phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

# Field `final`: khả năng quan sát, tối ưu JVM và hành vi theo phiên bản Java

## 1. Vấn đề cần hiểu

Khi Java Reflection cho phép truy cập vào `Field` mang modifier `final`, có ba câu hỏi **khác nhau**:

1. **Có được phép ghi không?** Quyền ghi phụ thuộc loại field, lớp khai báo, ranh giới module, quyền truy cập sâu và phiên bản JDK.
2. **Nếu lời ghi qua Reflection thành công, lần đọc tiếp theo sẽ thấy gì?** Kết quả `Field.get(...)`, lệnh đọc trực tiếp trong bytecode, getter đã được JIT tối ưu và mã đã biên dịch ở nơi khác **không nhất thiết có cùng hành vi quan sát** nếu chương trình sửa `final` sau khi công bố object.
3. **Java/JVM có được tối ưu gì khi field là `final`?** Java Memory Model (JMM) đưa ra bảo đảm riêng cho `final` được khởi tạo đúng; trình biên dịch và JVM có thể dựa vào giả định ổn định giá trị, và hằng số compile-time còn có thể bị **inline tại lúc biên dịch**.

Bài này tập trung vào **ảnh hưởng của việc quan sát/chỉnh sửa field `final` bằng Reflection**, tối ưu và sự khác nhau giữa các phiên bản. Mục tiêu không phải xây thủ thuật cưỡng ép thay đổi mọi `final`: những thao tác như vậy làm suy yếu hợp đồng chương trình và đang bị Java siết chặt.

Sau bài học, bạn sẽ phân biệt được modifier metadata với quyền ghi thực sự, mô tả chính xác giới hạn quan sát giá trị, nhận diện tối ưu dựa trên `final`, thực hiện ví dụ Java 17+, và giải thích các thay đổi ở JDK 26–27.

## 2. `final` bảo đảm điều gì, và không bảo đảm điều gì?

### 2.1. Ngữ nghĩa tại ngôn ngữ Java

- **Biến instance `final`** chỉ được gán theo quy tắc của ngôn ngữ trong quá trình khởi tạo; một **blank final** được gán trong constructor hoặc instance initializer hợp lệ.
- **Biến `static final`** thường được gán khi khai báo hoặc trong static initializer; gán lại bằng source Java bình thường là lỗi biên dịch.
- **`final` trên tham chiếu không làm object được trỏ tới thành bất biến sâu (deeply immutable)**. Ví dụ, một field `final List<String> items` không thể đổi sang một danh sách khác bằng phép gán Java hợp lệ, nhưng có thể gọi `items.add(...)` nếu đối tượng danh sách cho phép.
- **`final` không đồng nghĩa `volatile`**, và bản thân `final` không phải cơ chế cập nhật trạng thái chia sẻ giữa các thread sau khi object đã được tạo.

Ví dụ:

```java
import java.util.ArrayList;
import java.util.List;

class Profile {
    final List<String> tags = new ArrayList<>();

    void addTag(String tag) {
        tags.add(tag); // Hợp lệ: thay đổi nội dung List.
    }
}
```

Đoạn trên là **mã minh họa**, không có `main`; vấn đề của bài là tính ổn định **tham chiếu**, không phải tính bất biến của các phần tử.

### 2.2. Bảo đảm nhìn thấy giá trị khi khởi tạo đúng

Java Memory Model có quy tắc riêng cho `final` trong JLS `17.5. Khi constructor hoàn thành, một **freeze action** cho các field `final` được thiết lập. Nếu object được xây dựng đúng, đặc biệt **không để `this` thoát ra ngoài khi constructor còn chạy**, các thread khác có thể nhận được bảo đảm khởi tạo `final` mạnh hơn so với field thông thường ngay cả trong một số tình huống không có đồng bộ hóa bổ sung.

Nhưng cần đọc đúng giới hạn:

- Quy tắc dành cho **các giá trị được gán trong quá trình khởi tạo hợp lệ** và các truy cập phù hợp với mô hình bộ nhớ.
- Nó **không làm cho mọi field khác** tự động thread-safe.
- Nó **không làm cho mọi thay đổi tiếp theo** của dữ liệu tham chiếu được bảo đảm nhìn thấy.
- Việc để `this` thoát khỏi constructor, chẳng hạn đăng ký callback chạy đồng thời và truy cập object chưa khởi tạo xong, có thể phá hỏng giả định xây dựng đúng.

Để truyền trạng thái có thể thay đổi giữa các thread, hãy dùng cơ chế thích hợp như `volatile`, synchronization, lock, atomic classes hoặc thông điệp bất biến được công bố an toàn. Đừng dùng reflective write vào `final` như một cách thay thế.

## 3. Quan sát field bằng Reflection: metadata khác giá trị

Reflection có thể xác định một field là `final` mà không sửa nó:

```java
Field field = SomeType.class.getDeclaredField("value");
boolean declaredFinal = Modifier.isFinal(field.getModifiers());
```

Đây là **đoạn minh họa**, giả sử `SomeType` có field `value`, đã import `java.lang.reflect.Field` và `java.lang.reflect.Modifier`.

Các API có vai trò riêng:

| API | Điều nó trả lời | Không nên suy ra |
| --- | --- | --- |
| `Field.getModifiers()` + `Modifier.isFinal(...)` | Class file khai báo field mang cờ `final` hay không | Không kết luận `Field.set` chắc chắn thành công hoặc thất bại |
| `Field.get(obj)` / `getInt(obj)` | Đọc giá trị runtime qua Reflection theo quyền truy cập | Không phải một phép đọc `volatile` hay bảo đảm đồng nhất mọi getter sau khi chỉnh `final` |
| `Field.set(obj, value)` / `setInt(...)` | Yêu cầu ghi vào field được phản chiếu | Thành công không làm đổi modifier `final` trong metadata hay định nghĩa bytecode |
| `Field.trySetAccessible()` / `setAccessible(true)` | Thử cho phép vượt một số kiểm tra truy cập ngôn ngữ khi hợp lệ | Không xóa ràng buộc đặc biệt của `final`, record, static field hoặc module |
| `Method.invoke(obj)` gọi getter | Quan sát hành vi mã thực thi của getter | Không mặc định phải trả cùng giá trị như lần đọc `Field.get` sau reflective mutation trái mục đích |

Điểm mấu chốt: **quyền đọc, quyền bỏ qua kiểm tra truy cập và quyền ghi là ba vấn đề tách biệt**. Một field có thể được `setAccessible(true)` để đọc mà **vẫn không được phép ghi**.

### 3.1. Những field `final` không thể mở quyền ghi bằng `setAccessible(true)`

Từ Java 17, Javadoc của `Field` quy định các trường hợp `final` **không thể sửa bằng cơ chế Reflection thông thường**, bao gồm:

- `static final` field trong class hoặc interface.
- `final` field khai báo trong **record class**, bao gồm record component.
- `final` field khai báo trong **hidden class**.

Mở quyền truy cập với `setAccessible(true)`, khi được phép, chỉ giúp **đọc** các field đó; gọi `set(...)` vẫn thất bại với `IllegalAccessException`. Dùng `Unsafe` hoặc sửa nội bộ JVM để vượt ràng buộc nằm **ngoài hợp đồng chuẩn** và không phải giải pháp portable.

Ngược lại, trên JDK 17–25, `Field` thường cho phép ghi vào **non-static `final` instance field** của class thông thường không phải record/hidden class, miễn `setAccessible(true)` thành công. **Được phép thực hiện thao tác** không đồng nghĩa với **bảo đảm chương trình quan sát giá trị mới theo cách bạn mong đợi**.

## 4. Ví dụ Java 17+ độc lập: nhận diện và thử quyền ghi

Lưu toàn bộ đoạn sau vào **`FinalFieldObservationDemo.java`**:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class FinalFieldObservationDemo {
    static final class Box {
        private final int value;

        Box(int value) {
            this.value = value;
        }

        int read() {
            return value;
        }
    }

    static final class Constants {
        static final int ANSWER = 42;
    }

    record Snapshot(int id) {}

    public static void main(String[] args) throws Exception {
        Box box = new Box(10);

        Field boxField = Box.class.getDeclaredField("value");
        Field constantField = Constants.class.getDeclaredField("ANSWER");
        Field recordField = Snapshot.class.getDeclaredField("id");

        boxField.setAccessible(true);
        constantField.setAccessible(true);
        recordField.setAccessible(true);

        System.out.println("box final="
                + Modifier.isFinal(boxField.getModifiers()));
        System.out.println("box initial=" + boxField.getInt(box));
        System.out.println("constant direct=" + Constants.ANSWER);
        System.out.println("record final="
                + Modifier.isFinal(recordField.getModifiers()));

        try {
            constantField.setInt(null, 99);
            System.out.println("static final write=allowed");
        } catch (IllegalAccessException e) {
            System.out.println("static final write=denied");
        }

        try {
            recordField.setInt(new Snapshot(5), 99);
            System.out.println("record final write=allowed");
        } catch (IllegalAccessException e) {
            System.out.println("record final write=denied");
        }

        try {
            boxField.setInt(box, 20);
            System.out.println("ordinary instance final write=accepted");
        } catch (IllegalAccessException e) {
            System.out.println("ordinary instance final write=denied");
        }

        // Cố ý không khẳng định box.read() sau khi thay đổi phải thấy 20.
    }
}
```

Biên dịch và chạy với JDK 17–25 hoặc chạy thử trên JDK 21:

```bash
javac --release 17 FinalFieldObservationDemo.java
java FinalFieldObservationDemo
```

**Stdout quan sát được khi biên dịch bằng `javac --release 17` và chạy trên OpenJDK 21:**

```text
box final=true
box initial=10
constant direct=42
record final=true
static final write=denied
record final write=denied
ordinary instance final write=accepted
```

Chương trình đã được **biên dịch và chạy thực tế trên OpenJDK 21**; stdout phía trên khớp kết quả kiểm tra. Khi chạy bằng JDK 26–27, reflective mutation của `Box.value` có thể phát cảnh báo **trên stderr** theo JEP 500 và tùy chọn JVM. Khi bật chính sách `--illegal-final-field-mutation=deny` mà không cấp quyền tương ứng, hành vi ghi `Box.value` có thể bị từ chối, khiến dòng cuối đổi thành `ordinary instance final write=denied`.

**Giải thích từng nhóm kết quả:**

1. `Modifier.isFinal` xác định đúng cả `Box.value` và `Snapshot.id` là `final`; metadata không tự cấp quyền sửa.
2. `constantField` là `static final` nên `setInt(null, 99)` bị từ chối. `null` ở đây hợp lệ để chỉ target của static field, nhưng không làm field trở nên ghi được.
3. `recordField` biểu diễn record component `id`, là `final` và không thể sửa qua `Field.setInt`.
4. `Box.value` là `final` instance field của class thường. Với JDK 21, khi đã bật `setAccessible(true)`, thao tác `setInt(box, 20)` được chấp nhận.
5. Ví dụ cố ý **không kết luận** rằng sau lời ghi, `box.read()` hay một vòng lặp trên thread khác sẽ luôn thấy `20`. Đó chính là chủ đề về **khả năng quan sát và tối ưu** của bài này.

Nếu target class nằm ở **named module khác** mà package không mở cho module caller, lời `setAccessible(true)` còn có thể thất bại với `InaccessibleObjectException` trước khi chương trình đến bước kiểm tra ghi.

## 5. Vì sao ghi `final` thành công vẫn không bảo đảm lần đọc thấy giá trị mới?

### 5.1. JLS cho phép tối ưu dựa trên `final`

JLS `17.5.3, **Subsequent Modification of `final` Fields**, thảo luận trường hợp hệ thống như deserialization cần sửa `final` sau khi object được dựng. Quy tắc này chỉ có ngữ nghĩa hợp lý trong một mô hình đặc biệt: tạo object, cập nhật field trước khi object được **công bố** cho các phần khác, và không cho mã khác đọc field cho đến khi cập nhật xong.

Đối với tình huống object **đã được sử dụng/đọc/công bố**, chương trình không thể xem reflective mutation như cập nhật trạng thái thường xuyên được bảo đảm. JLS thừa nhận compiler/JVM có thể tối ưu `final` rất mạnh, kể cả các phép biến đổi thứ tự đọc liên quan đến thao tác sửa `final` không nằm trong constructor.

Một mô hình minh họa về hậu quả:

```java
final class Counter {
    final int value;

    Counter(int value) {
        this.value = value;
    }

    int read() {
        return value;
    }
}
```

Đây chỉ là **class minh họa**, không phải chương trình độc lập. Nếu tạo `Counter(1)`, cho code đọc `read()` nhiều lần, sau đó dùng Reflection đổi `value` thành `2`, không có bảo đảm tổng quát rằng:

- Một lần gọi `read()` đã được JIT tối ưu sẽ luôn tải lại field từ bộ nhớ.
- Một thread khác sẽ thấy `2` ngay hoặc tại một thời điểm hữu hạn.
- Mọi phương thức/đường đọc có quan sát giống hệt `Field.getInt(...)`.
- Chạy interpreter và JIT tối ưu sẽ cho kết quả giống nhau.

**Đừng viết unit test khẳng định “sau `Field.set`, getter phải đọc giá trị mới” như một hợp đồng ổn định của Java.** Một lần chạy thấy giá trị mới chỉ chứng minh điều xảy ra trong điều kiện chạy đó.

### 5.2. Ba mức “quan sát” cần tách

| Lớp quan sát | Ví dụ | Kết luận có thể rút ra |
| --- | --- | --- |
| **Metadata** | `Modifier.isFinal(field.getModifiers())` | Field được khai báo `final`, không có nghĩa value không từng bị ghi sau constructor |
| **Lần đọc reflective cụ thể** | `field.getInt(instance)` | Giá trị do truy cập Reflection đó trả về tại thời điểm và execution path cụ thể |
| **Lệnh đọc trong code ứng dụng** | `instance.read()`, code JIT hoặc code biên dịch khác | Có thể chịu constant folding, hoisting, cached reads và ràng buộc JMM; không tự đồng bộ với reflective mutation |

Đặc biệt, khi kiểm thử race condition, thêm logging hoặc Reflection có thể làm thay đổi timing, đường thực thi và mức độ tối ưu. Việc **bật giám sát rồi hết lỗi** không chứng minh lỗi đa luồng ban đầu đã được sửa.

### 5.3. “Freeze” sau reflective write không biến mutation thành thiết kế hợp lệ

JLS mô tả freeze action tại cuối constructor và ngay sau một số reflective modifications hợp lệ của `final`. Tuy nhiên, đây không phải giấy phép dùng `final` như biến mutable. Phạm vi dự kiến là trường hợp rất hẹp như **tái dựng/deserialization**, khi field được đặt giá trị **trước khi object bị đọc/công bố**.

Nếu object đã được chia sẻ, đã bị getter đọc nhiều lần hoặc rơi vào một vòng lặp JIT, không thể viện dẫn freeze action để ép mọi luồng/ngữ cảnh biên dịch thấy phiên bản mới.

## 6. Tối ưu quan trọng: `static final` compile-time constant

Không phải mọi `final` đều là **constant variable** theo JLS. Một field là constant variable khi nó có kiểu primitive hoặc `String`, là `final` và được khởi tạo bằng một **constant expression** theo quy tắc ngôn ngữ.

Ví dụ:

```java
class ConstantsExample {
    static final int MAX = 42;            // Constant variable
    static final String LABEL = "READY";   // Constant variable

    static final Integer BOXED = 42;       // Không phải constant variable
    static final int COMPUTED = Integer.parseInt("42");
                                         // Không phải constant expression
}
```

Đây là **đoạn minh họa**. `MAX` và `LABEL` có thể bị **inline khi compile** tại nơi sử dụng. Nếu một class client được compile với `ConstantsExample.MAX == 42`, bytecode của client có thể chứa hẳn số `42` thay vì đọc field lúc chạy.

Do vậy:

- Ngay cả trong giả thiết dùng cách ngoài chuẩn để làm thay đổi storage của `static final`, những code path đã inline literal có thể **vẫn dùng giá trị cũ**.
- Thay đổi giá trị constant variable trong thư viện rồi chỉ deploy lại JAR của thư viện nhưng **không biên dịch lại client** có thể khiến client tiếp tục dùng hằng số cũ. Đây là hệ quả của **constant inlining lúc biên dịch**, khác với cache của JIT.
- Một `static final` được khởi tạo runtime, chẳng hạn `Integer.parseInt("42")`, không phải compile-time constant expression, dù field vẫn chỉ được gán một lần bằng Java source hợp lệ.

Bạn có thể quan sát bytecode:

```bash
javap -c -p FinalFieldObservationDemo
```

Trong ví dụ chạy phía trên, biểu thức `Constants.ANSWER` là compile-time constant. `javap` có thể hiển thị lệnh đẩy literal `42` ở call site, thay vì một lệnh `getstatic` đọc field mỗi lần; chi tiết mnemonic và trình tự lệnh tùy bytecode do compiler phát sinh.

**Lưu ý:** `static final` và compile-time constant là hai điều kiện khác nhau. Cấm reflective write đối với `static final` trong Java 17+ không đồng nghĩa **mọi `static final`** đều được inline như một literal ngay khi biên dịch.

## 7. Các tối ưu JVM và rủi ro về hiệu năng

JVM/JIT có thể tận dụng tính ổn định của `final` để giảm số lần đọc bộ nhớ, duy trì giá trị trong thanh ghi, loại phép đọc lặp lại, thực hiện constant propagation/constant folding trong những trường hợp hợp lệ, và tổ chức lại lệnh quanh các điểm không có thay đổi hợp lệ theo mô hình ngôn ngữ.

Tuy nhiên:

- **Không có quy tắc “mọi `final` đều luôn được inline”**. Kỹ thuật chính xác tùy field, escape analysis, cấu hình JIT, phiên bản JVM và code path.
- **Không có quy tắc “`final` luôn nhanh hơn”** trên mọi workload. Giá trị của `final` trước hết nằm ở tính đúng đắn, khả năng diễn đạt bất biến và cơ hội tối ưu.
- Một hệ thống cho phép sửa `final` bằng deep reflection trong lúc ứng dụng đang chạy hạn chế những giả định tối ưu mà JVM có thể áp dụng an toàn, vì phải tính đến khả năng phá vỡ bất biến.
- Việc JDK ngày càng hạn chế reflective mutation hướng tới **integrity by default**: làm cho cam kết `final` đáng tin hơn và mở thêm không gian tối ưu về lâu dài.

Khi benchmark, tránh đánh giá bằng một vòng lặp tự viết đơn giản: warm-up, inlining, dead-code elimination, constant folding và môi trường chạy có thể làm sai kết luận. Nếu cần microbenchmark có giá trị, hãy dùng JMH cùng các điều kiện kiểm thử nhất quán. Không nên suy luận hợp đồng JMM từ kết quả đo thời gian.

## 8. Hành vi theo phiên bản JDK: phần cần kiểm tra khi nâng cấp

### 8.1. Mốc phiên bản

| Dòng JDK | Những điểm liên quan tới `final` qua Reflection |
| --- | --- |
| **JDK 8** | Mô hình JMM `final` đã tồn tại; kỹ thuật reflective mutation từng được dùng bởi một số thư viện khởi tạo đối tượng. Những thủ thuật đụng tới nội bộ JVM không phải API ổn định. |
| **JDK 9–16** | JPMS bổ sung ranh giới module và kiểm tra deep reflection; record và hidden class xuất hiện trong quá trình tiến hóa platform. Không nên suy luận quyền ghi `final` chỉ từ quyền `private`. |
| **JDK 17–25** | Strong encapsulation JDK trở nên chặt chẽ; `Field` quy định rõ không cho ghi `static final`, record final và hidden-class final bằng `setAccessible`. Một số `final` instance của class thông thường vẫn có thể được sửa phản chiếu khi đủ điều kiện. |
| **JDK 26** | **JEP 500 – Prepare to Make Final Mean Final**: mặc định phát cảnh báo về reflective mutation đối với `final` instance khi không cấp phép, chuẩn bị cho hạn chế nghiêm khắc hơn ở tương lai; bổ sung chính sách và tùy chọn launch. |
| **JDK 27 (phát hành 09/2026)** | Tiếp tục **cảnh báo** đối với reflective mutation trái chính sách theo JEP 500; tài liệu migration vẫn mô tả sự chặn bằng exception là hướng cho **phiên bản tương lai**, không nên viết rằng mọi ghi đã bị cấm mặc định trong JDK 27. |

**Phạm vi thông tin về phiên bản:** bảng dựa trên Java/JDK đã công bố đến tháng 10/2026. Nếu ứng dụng chạy trên bản update/vendor JVM cụ thể hoặc JDK mới hơn, đọc **API docs và migration notes của đúng runtime đang triển khai**.

### 8.2. JDK 26–27: cảnh báo khi mutate `final`

JDK 26 thêm chính sách final-field mutation. Nếu code từ một module ghi `final` instance thông qua deep reflection trong trường hợp không được cấp năng lực này, mặc định JVM có thể ghi cảnh báo, gồm field và mã đang thực hiện mutation. Cảnh báo được báo trên **stderr** nên test chỉ so stdout có thể không phát hiện.

Các tùy chọn được JEP 500 giới thiệu:

```text
--enable-final-field-mutation=ALL-UNNAMED
--enable-final-field-mutation=module.name
--illegal-final-field-mutation=warn
--illegal-final-field-mutation=allow
--illegal-final-field-mutation=debug
--illegal-final-field-mutation=deny
```

Ý nghĩa:

- `--enable-final-field-mutation=ALL-UNNAMED`: cấp năng lực mutation cho code trong unnamed module(s), phù hợp khi cần xử lý thư viện legacy trên classpath. Điều này **không tự mở package đích** cho deep reflection.
- `--enable-final-field-mutation=module.name`: cấp năng lực cho named module tương ứng; caller phải nằm trong module được cấp phép.
- `--illegal-final-field-mutation=warn`: chính sách mặc định ở JDK 26–27, cảnh báo đối với thao tác trái chính sách.
- `allow`: cho phép mà không báo cảnh báo thuộc chính sách này; `debug`: ghi thông tin chẩn đoán chi tiết hơn; `deny`: từ chối mutation trái chính sách, hữu ích để rà soát code trước khi chính sách mặc định siết chặt.

Để kiểm tra ví dụ trên với chính sách chặn khi chạy JDK 26–27:

```bash
java --illegal-final-field-mutation=deny FinalFieldObservationDemo
```

**Dòng cuối** của stdout có thể chuyển thành `ordinary instance final write=denied`, bởi mutation từ unnamed module không được cấp phép. Điều này không liên quan đến hai thao tác `static final` và record final: chúng **vốn bị cấm** trong Reflection chuẩn trước JDK 26.

Nếu thực sự cần cho phép phần mềm cũ thực hiện mutation trong giai đoạn chuyển tiếp:

```bash
java --enable-final-field-mutation=ALL-UNNAMED FinalFieldObservationDemo
```

Tùy chọn này phù hợp với ví dụ classpath và không có module boundary phức tạp. Trong ứng dụng module thực, việc được cấp năng lực mutation còn phải đi cùng quyền truy cập thích hợp tới package đích; **`--add-opens` và `--enable-final-field-mutation` giải quyết hai quyền khác nhau**.

**Quan trọng:** chọn `allow` hoặc cấp quyền mutation chỉ là giải pháp tương thích khi bắt buộc. Nó không tạo ra bảo đảm quan sát mới sau khi sửa `final`, không sửa được `static final`/record/hidden-class final qua `Field.set`, và không biến thiết kế mutable thành thread-safe.

### 8.3. Dự báo thay đổi mà không khẳng định quá mức

JEP 500 nói rõ mục đích là **chuẩn bị** cho một phiên bản tương lai nơi hành vi illegal final field mutation sẽ chuyển từ warning sang exception mặc định. Vì vậy:

- Không hard-code giả định “trên mọi JDK, `setAccessible(true)` sẽ cho sửa `final` instance”.
- Không hard-code giả định “từ JDK 26 mọi sửa `final` đều luôn ném exception”.
- Khi nâng cấp, chạy compatibility tests với `--illegal-final-field-mutation=deny` để tìm điểm phụ thuộc vào mutation, kiểm tra cả stderr và stack trace.
- Cập nhật framework/serializer sang bản hỗ trợ cơ chế khởi tạo hợp lệ thay vì duy trì thủ thuật trên object đã đưa vào dùng.

## 9. Các dạng vấn đề hay gặp

### 9.1. Reflection đọc được nhưng không ghi được

`field.trySetAccessible()` trả `true` chỉ nói rằng yêu cầu **suppress access checks** hợp lệ. Sau đó, `field.set(...)` vẫn có thể ném `IllegalAccessException` nếu field là non-modifiable final hoặc chính sách final mutation ngăn thao tác. Nếu package trong named module không mở cho caller thì việc bật access có thể thất bại trước đó.

**Cách xử lý:** ghi log `field.getDeclaringClass().getModule()`, `field.getModifiers()`, `field.getDeclaringClass().isRecord()`, runtime `java.version` và loại exception. Không “sửa lỗi” bằng cách xóa modifier `final` một cách tùy tiện.

### 9.2. Unit test đi qua Reflection nhưng production thấy giá trị cũ

Một phép thử chạy ngắn dưới interpreter có thể thấy cập nhật, nhưng getter production sau warm-up có thể bị tối ưu khác. Kết quả benchmark còn phụ thuộc JVM, flag và kiểu khai báo field.

**Cách xử lý:** thiết kế lại để khởi tạo đủ dữ liệu trong constructor, truyền dependency qua constructor/factory hoặc dùng cơ chế mutable hợp lệ (`volatile`, atomic reference, lock) nếu nghiệp vụ thật sự cần cập nhật.

### 9.3. Thay giá trị hằng số trong library nhưng client vẫn in giá trị cũ

Nếu `static final` là constant variable, mã client có thể đã nhúng literal từ lần compile trước. Thay JAR riêng của library không nhất thiết thay đổi code client đã được biên dịch.

**Cách xử lý:** rebuild những client phụ thuộc khi thay đổi compile-time constants công khai; tránh coi chúng như cấu hình có thể cập nhật runtime.

### 9.4. Hiểu nhầm Reflection là kênh đồng bộ hóa

`Field.set`/`get` không tự tạo ra một giao thức publish-consume an toàn chỉ vì field có modifier `final`. Một người viết vào object đã công bố, người khác đọc ở loop, có thể chạm các hạn chế quan sát và data race tùy thiết kế.

**Cách xử lý:** tách cấu hình bất biến thành object mới và công bố an toàn; với trạng thái mutable, dùng primitive đồng bộ hóa được JMM công nhận.

### 9.5. Tưởng tắt JIT sẽ giải quyết tận gốc

`-Xint` hữu ích cho một **thử nghiệm chẩn đoán** (chạy interpreted mode), nhưng không thay đổi đặc tả và không biến reflective mutation của `final` thành hành vi có hợp đồng ổn định. Tương tự, `-Xcomp` hoặc các JVM optimization flags không thay thế được thiết kế đúng.

## 10. Thiết kế thay thế an toàn

### 10.1. Bất biến thật sự: truyền qua constructor

```java
final class ImmutableConfig {
    private final String endpoint;

    ImmutableConfig(String endpoint) {
        this.endpoint = java.util.Objects.requireNonNull(endpoint);
    }

    String endpoint() {
        return endpoint;
    }
}
```

Đây là **class minh họa**, không có `main`. Giá trị được đặt khi khởi tạo và sau đó không bị thay thế.

### 10.2. Nếu cần cập nhật cấu hình runtime

```java
import java.util.concurrent.atomic.AtomicReference;

final class ConfigHolder {
    private final AtomicReference<String> endpoint =
            new AtomicReference<>("https://example.invalid");

    String getEndpoint() {
        return endpoint.get();
    }

    void changeEndpoint(String replacement) {
        endpoint.set(java.util.Objects.requireNonNull(replacement));
    }
}
```

Ở ví dụ này, **tham chiếu tới `AtomicReference` là `final`**, nhưng trạng thái được quản lý bởi đối tượng atomic có thể thay đổi đúng hợp đồng JMM. Đây là **class minh họa**, không có `main`. Trong ứng dụng thật, cần thêm validation và quản lý vòng đời tương ứng với nghiệp vụ.

### 10.3. Với serialization/deserialization

Ưu tiên framework hỗ trợ constructor/factory, record constructor, builder hoặc cơ chế đọc dữ liệu phù hợp. Nếu thư viện legacy bắt buộc ghi `final` trong lúc tái dựng object, hãy giới hạn thao tác **trước khi object được công bố hoặc đọc**, kiểm tra rõ quyền/module/runtime và chuẩn bị migration theo JEP 500. Không sửa object sau khi nó đã được sử dụng rồi trông đợi mọi getter và thread tự thấy giá trị mới.

## 11. Checklist chẩn đoán và kiểm thử

1. **Xác định loại field:** instance hay static, `final` hay không, thuộc class thường, record hay hidden class? Dùng `getModifiers()` và `Modifier.isFinal(...)`.
2. **Xác định quyền:** caller cùng class/module hay khác module? Package đích có open không? `setAccessible(true)` có thành công không?
3. **Xác định phiên bản runtime:** `System.getProperty("java.version")` và các JVM flags đang dùng; không suy đoán từ source compatibility `--release 17`.
4. **Phân biệt ba phép kiểm tra:** permission to write, observed reflective read, observed compiled getter. Không xem chúng là đồng nhất.
5. **Kiểm tra hằng số compile-time:** field `static final` có thuộc kiểu primitive/`String` và constant expression không? Client có phải rebuild không?
6. **Kiểm tra điểm công bố:** object có bị truy cập trước/khi đang thay field không? Có race condition không?
7. **Dùng môi trường nâng cấp:** thử JDK 26–27 với policy `--illegal-final-field-mutation=deny`, xem exception, stderr và dependency nào thực hiện mutation.
8. **Chọn sửa thiết kế:** constructor/factory hoặc cơ chế mutable có synchronization; coi reflective final mutation là trường hợp tương thích cần giảm dần.

## 12. Nguồn đặc tả và tài liệu kiểm chứng

- Java Language Specification, `17.5 *final Field Semantics* và `17.5.3 *Subsequent Modification of final Fields*: https://docs.oracle.com/javase/specs/jls/se26/html/jls-17.html
- Java SE 17, `java.lang.reflect.Field`, mục `setAccessible` và `set`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html
- Oracle JDK 26 Migration Guide, *Preparing for final Field Mutation Restrictions*: https://docs.oracle.com/en/java/javase/26/migrate/preparing-final-field-mutation-restrictions.html
- Oracle JDK 27 Migration Guide, *Significant Changes*: https://docs.oracle.com/en/java/javase/27/migrate/significant-changes-jdk-27-release.html

**Ghi nhớ:** `final` không chỉ là cờ modifier; đó là cam kết thiết kế và cơ sở cho quy tắc bộ nhớ cùng tối ưu. Một reflective write có thể được runtime chấp nhận trong hoàn cảnh nhất định nhưng **không tạo bảo đảm quan sát đáng tin** trên object đã dùng. Vì vậy phải phân biệt khả năng ghi, khả năng quan sát và hiệu ứng tối ưu; đồng thời kiểm tra chính sách mới của JDK 26–27 khi nâng cấp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="final-fields-008">Chọn constructor, factory hoặc API cập nhật được thiết kế sẵn</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn constructor, factory hoặc API cập nhật được thiết kế sẵn cho trường `final`

Trong Java, một trường `final` được gán trong quá trình khởi tạo hợp lệ và không thể được **gán lại bằng mã Java thông thường** sau khi đối tượng đã xây dựng xong. Trong Reflection, việc tìm được `Field` và gọi `setAccessible(true)` không có nghĩa là thao tác sửa field đó luôn được phép, an toàn hay duy trì đúng hành vi chương trình.

Với **Field final**, cách xử lý được ưu tiên là thiết kế rõ vòng đời trạng thái: dùng **constructor** để truyền giá trị tại lúc khởi tạo, **static factory** hoặc **builder** khi cần cách tạo đối tượng có ý nghĩa nghiệp vụ, và **API cập nhật được thiết kế sẵn** khi dữ liệu cần đổi. API cập nhật có thể tạo ra **một đối tượng bất biến mới** hoặc thay đổi field được chủ đích cho phép mutable. Không cần sửa `final` field bằng Reflection để đạt các mục tiêu này.

## 1. Điều `final` bảo đảm và không bảo đảm

```java
final class Account {
    private final String id;
    private final int points;

    Account(String id, int points) {
        this.id = id;
        this.points = points;
    }
}
```

`id` và `points` nhận giá trị khi tạo đối tượng. Sau đó, mã Java thông thường không thể gán `account.points = 20`. Nếu muốn biểu diễn tài khoản với điểm mới, hãy tạo một `Account` khác qua API chính thức, hoặc chọn thiết kế mutable ngay từ đầu.

Phân biệt ba ý quan trọng:

- **Final field:** không thể gán lại bằng mã Java thông thường sau khi khởi tạo.
- **Final reference:** khóa tham chiếu, không tự làm object mà nó trỏ đến trở nên bất biến. Ví dụ `final List<String> tags` vẫn có thể tham chiếu một danh sách có thể sửa.
- **Immutable object:** cần thiết kế toàn bộ trạng thái không thay đổi sau khởi tạo: field phù hợp, không để lộ mutable state, kiểm tra đầu vào và sao chép phòng vệ nếu cần. `List.copyOf(input)` chặn sửa cấu trúc danh sách qua API, nhưng nếu các phần tử tự mutable thì còn phải xét từng phần tử.

Một trường `static final` thường chứa hằng số hoặc tham chiếu dùng chung; không phải chỗ để chứa cấu hình được cập nhật liên tục. Record trong Java 17 dùng field `private final` cho mỗi record component; muốn giá trị component mới thì thông thường tạo record mới.

## 2. Chọn phương án theo nhu cầu

| Tình huống | Giải pháp | Đặc điểm |
| --- | --- | --- |
| Giá trị đã biết khi đối tượng được tạo | Constructor | Đặt field một lần và kiểm tra tính hợp lệ ngay từ đầu |
| Có nhiều cách khởi tạo hoặc tên nghiệp vụ rõ ràng | Static factory | Phương thức như `register`, `of`, `from` trả object đã được khởi tạo đúng |
| Nhiều tùy chọn, tham số dễ nhầm | Builder | Thu thập cấu hình tạm rồi gọi `build()` để tạo object hoàn chỉnh |
| Một giá trị trong immutable object cần thay đổi | Copy/wither | `withName` hoặc `withPoints` trả đối tượng mới, không sửa đối tượng cũ |
| Đối tượng được thiết kế là mutable | Method cập nhật chuyên biệt | Chỉ thay đổi field không `final` và luôn kiểm tra invariant |
| Cập nhật snapshot immutable được nhiều luồng dùng chung | Thay thế tham chiếu an toàn | Ví dụ `AtomicReference`, cơ chế khóa hoặc holder có synchronization |
| Khởi tạo từ JSON, DB hoặc framework | Binding theo constructor/record/factory/builder được hỗ trợ | Dữ liệu đi qua API tạo object đúng contract |
| Muốn sửa `private final` bằng Reflection | Xem lại vòng đời và API | Tránh bypass bất biến và ràng buộc JVM/module |

**Nguyên tắc:** Nếu dữ liệu là **input lúc tạo**, đưa vào constructor/factory/builder. Nếu dữ liệu là **snapshot bất biến mới**, tạo object mới. Nếu object có **trạng thái biến đổi có chủ đích**, dùng phương thức cập nhật được kiểm soát.

## 3. Constructor: khởi tạo đủ dữ liệu và bảo vệ invariant

Constructor có thể gói các quy tắc như mã định danh không rỗng, số lượng phải dương, giá tiền không âm, trạng thái phù hợp với ngày giao hàng. Đưa các kiểm tra này vào điểm khởi tạo chung giúp tránh object “mới xây được một nửa”.

```java
public final class Ticket {
    private final String code;
    private final int seats;

    public Ticket(String code, int seats) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (seats <= 0) {
            throw new IllegalArgumentException("seats must be positive");
        }
        this.code = code;
        this.seats = seats;
    }

    public String code() { return code; }
    public int seats() { return seats; }
}
```

Đây là **một class minh họa**, không có `main`. Nếu số ghế cần đổi, đừng tìm `Ticket.seats` bằng Reflection rồi cưỡng ép gán. Hãy thiết kế cách tạo `Ticket` mới với số ghế khác, hoặc tạo một loại ticket mutable có update API rõ ràng nếu nghiệp vụ thực sự cần chỉnh cùng instance.

Một constructor tốt còn cần tránh để `this` thoát ra ngoài trước khi hoàn tất khởi tạo. Dữ liệu mutable được nhận vào constructor có thể cần defensive copy để caller không sửa ngầm trạng thái của object về sau.

## 4. Static factory và builder: tạo object có ý nghĩa nghiệp vụ

Static factory có thể dùng constructor `private` để mọi đường tạo object đều tuân thủ cùng một bộ invariant. Ví dụ:

```java
// Đoạn minh họa bên trong lớp Customer:
public static Customer register(String id, String name) {
    return new Customer(id, name, 0);
}

public static Customer restore(String id, String name, int points) {
    return new Customer(id, name, points);
}
```

Đây không phải chương trình chạy độc lập vì phụ thuộc vào constructor của `Customer`. Tên `register` biểu thị quy tắc mới có điểm bằng 0; `restore` minh họa khôi phục từ dữ liệu có điểm. Constructor dùng chung vẫn phải xác thực `id`, `name` và `points`.

Static factory **không bắt buộc** trả instance mới trong mọi thư viện: có factory cache hoặc trả instance sẵn có. Nếu identity quan trọng, cần đọc contract. Factory được ưu tiên khi làm ý nghĩa khởi tạo rõ hơn constructor quá tải, không chỉ để đổi tên `new`.

Với object có nhiều tham số tùy chọn, builder thu thập các lựa chọn rồi gọi `build()`. Builder có thể mutable trong khi chuẩn bị, nhưng **object đầu ra** vẫn được tạo một lần với các field `final`.

```text
Builder nhận các lựa chọn (trạng thái tạm)
                ↓
build() kiểm tra điều kiện nghiệp vụ
                ↓
Constructor/factory tạo object hoàn chỉnh
                ↓
Object có các field final để sử dụng
```

Nếu đối tượng chỉ có 2–3 tham số rõ ràng, constructor hoặc factory đơn giản thường đủ; đừng thêm builder không cần thiết.

## 5. API cập nhật được thiết kế sẵn

### 5.1. Copy/wither: thay đổi trạng thái nhưng giữ đối tượng cũ

Một `Customer` immutable có thể cung cấp:

```java
// Đoạn minh họa nằm trong lớp Customer:
public Customer withDisplayName(String newName) {
    return new Customer(id, newName, points);
}
```

Sau `Customer newer = old.withDisplayName("Binh");`, `old` không bị thay đổi; biến `newer` trỏ tới instance mới. Nếu bỏ qua giá trị trả về thì không có thay đổi trạng thái nào được giữ lại.

Record rất phù hợp cho snapshot:

```java
public record Configuration(String environment, int timeoutSeconds) {
    public Configuration {
        if (environment == null || environment.isBlank()) {
            throw new IllegalArgumentException("environment is required");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public Configuration withTimeoutSeconds(int seconds) {
        return new Configuration(environment, seconds);
    }
}
```

Đây là record minh họa độc lập nhưng không có `main`. Phương thức `withTimeoutSeconds` tạo `Configuration` mới qua canonical constructor và kiểm tra giá trị mới. Record không tự bất biến sâu nếu component trỏ đến object mutable; thiết kế record vẫn phải cân nhắc điều đó.

### 5.2. Method cập nhật: chỉ với trạng thái được chủ ý cho phép mutable

Không phải mọi object đều cần immutable. Ví dụ giỏ hàng có `final id` và `int quantity` có thể cung cấp `changeQuantity(int)` để sửa số lượng, đồng thời kiểm tra lớn hơn 0. Identity không đổi; số lượng thay đổi theo quy tắc nghiệp vụ. Đây là lý do **không nên bỏ `final` khỏi tất cả field** chỉ để có setter.

Nếu nhiều thread cùng cập nhật object mutable, cần giải quyết thread safety và tính nhất quán giữa các field. Một setter đơn giản hoặc thêm `volatile` cho một field không tự khiến toàn bộ thao tác nghiệp vụ trở thành atomic.

## 6. Ví dụ Java 17+ hoàn chỉnh

Lưu tệp đúng tên **`FinalFieldDesignDemo.java`**. Chương trình minh họa factory, immutable copy, record, update method và thay thế snapshot qua `AtomicReference`.

```java
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class FinalFieldDesignDemo {
    public static void main(String[] args) {
        Customer original = Customer.register("C-01", "An");
        Customer renamed = original.withDisplayName("Binh");
        Customer rewarded = renamed.withPoints(25);

        System.out.println("original=" + original);
        System.out.println("renamed=" + renamed);
        System.out.println("rewarded=" + rewarded);

        Configuration config = new Configuration("prod", 10);
        Configuration next = config.withTimeoutSeconds(20);
        System.out.println("config=" + config);
        System.out.println("next=" + next);

        MutableCart cart = new MutableCart("cart-7", 1);
        cart.changeQuantity(3);
        System.out.println("cart=" + cart);

        AtomicReference<Configuration> current =
                new AtomicReference<>(config);
        current.updateAndGet(old -> old.withTimeoutSeconds(30));
        System.out.println("published=" + current.get());
        System.out.println("sourceStill=" + config);
    }

    static final class Customer {
        private final String id;
        private final String displayName;
        private final int points;

        private Customer(String id, String name, int points) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            if (id.isBlank() || name.isBlank() || points < 0) {
                throw new IllegalArgumentException("invalid customer");
            }
            this.id = id;
            this.displayName = name;
            this.points = points;
        }

        static Customer register(String id, String name) {
            return new Customer(id, name, 0);
        }

        Customer withDisplayName(String newName) {
            return new Customer(id, newName, points);
        }

        Customer withPoints(int newPoints) {
            return new Customer(id, displayName, newPoints);
        }

        @Override
        public String toString() {
            return "Customer[id=" + id
                    + ", displayName=" + displayName
                    + ", points=" + points + "]";
        }
    }

    record Configuration(String environment, int timeoutSeconds) {
        Configuration {
            Objects.requireNonNull(environment, "environment");
            if (environment.isBlank() || timeoutSeconds <= 0) {
                throw new IllegalArgumentException("invalid configuration");
            }
        }

        Configuration withTimeoutSeconds(int seconds) {
            return new Configuration(environment, seconds);
        }
    }

    static final class MutableCart {
        private final String id;
        private int quantity;

        MutableCart(String id, int quantity) {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("id is required");
            }
            this.id = id;
            changeQuantity(quantity);
        }

        void changeQuantity(int newQuantity) {
            if (newQuantity <= 0) {
                throw new IllegalArgumentException(
                        "quantity must be positive");
            }
            this.quantity = newQuantity;
        }

        @Override
        public String toString() {
            return "MutableCart[id=" + id
                    + ", quantity=" + quantity + "]";
        }
    }
}
```

Biên dịch/chạy từ thư mục chứa tệp:

```bash
javac FinalFieldDesignDemo.java
java FinalFieldDesignDemo
```

Kết quả stdout:

```text
original=Customer[id=C-01, displayName=An, points=0]
renamed=Customer[id=C-01, displayName=Binh, points=0]
rewarded=Customer[id=C-01, displayName=Binh, points=25]
config=Configuration[environment=prod, timeoutSeconds=10]
next=Configuration[environment=prod, timeoutSeconds=20]
cart=MutableCart[id=cart-7, quantity=3]
published=Configuration[environment=prod, timeoutSeconds=30]
sourceStill=Configuration[environment=prod, timeoutSeconds=10]
```

Điểm đáng chú ý: `Customer.register` gán giá trị khởi tạo qua private constructor; `withDisplayName` và `withPoints` trả các object mới, `original` giữ nguyên. Record `Configuration` được sao chép với timeout mới. `MutableCart` cập nhật field `quantity` thông qua API kiểm tra dữ liệu nhưng giữ `id` là `final`.

`AtomicReference.updateAndGet` thay thế tham chiếu đến snapshot mới một cách atomic, **không sửa field `final` trong snapshot cũ**. Lambda cập nhật nên không có side effect vì dưới cạnh tranh giữa các thread nó có thể bị gọi lại. Ví dụ chỉ chạy một luồng nên đầu ra nêu trên là xác định.

## 7. Vì sao tránh cưỡng ép gán `final` field bằng Reflection?

Một đoạn chương trình có thể tra cứu metadata:

```java
java.lang.reflect.Field field =
        Customer.class.getDeclaredField("points");
boolean isFinal = java.lang.reflect.Modifier.isFinal(
        field.getModifiers());
System.out.println(isFinal); // true
```

Đây là **đoạn minh họa**, không độc lập: cần đặt trong ngữ cảnh có lớp `Customer` và xử lý `NoSuchFieldException`. Phát hiện modifier `final` không mang ý nghĩa “có thể sửa an toàn”.

Các rào cản gồm:

1. **Access control và JPMS.** `getDeclaredField` tìm metadata của field `private`, nhưng thao tác `Field.set` còn chịu kiểm tra quyền. `setAccessible(true)` chỉ bật bỏ qua kiểm tra khi JVM/module cho phép; named module không mở package cho caller có thể gây `InaccessibleObjectException` hoặc khiến `trySetAccessible()` trả `false`.
2. **Không phải final field nào cũng được ghi.** Các nhóm không thể sửa qua cơ chế Reflection này bao gồm `static final`, final field trong record và final field của hidden class. Không được giả định bật access override là đủ.
3. **Java Memory Model và invariant.** Final field liên quan đến các đảm bảo khởi tạo đặc biệt khi object được xây dựng đúng. Cưỡng ép sửa sau khi object đã được công bố có thể phá hỏng invariant và dẫn tới kết quả quan sát không mong đợi bởi các thread hoặc mã đã được JVM tối ưu hóa. Thay đổi thành công trong một thử nghiệm không tạo thành API được hỗ trợ cho mọi runtime.
4. **Bảo trì và đóng gói.** Mã phụ thuộc tên field `private` dễ gãy khi refactor, đổi thư viện, đổi phiên bản JDK hoặc thay cấu trúc module; constructor/factory/update API mô tả contract rõ ràng hơn.

Các phiên bản JDK mới có thể siết các thao tác deep reflection lên final field hơn Java 17. Vì vậy, ví dụ thực hành ưu tiên API Java thông thường, không dựa vào kỹ thuật sửa final field bằng Reflection.

## 8. Ứng dụng với framework, lưu trữ và dữ liệu hiện có

Khi nạp từ JSON, database, file cấu hình hoặc một framework, không nên mặc định “tạo object rỗng rồi gán private final field qua Reflection”. Tùy framework và phiên bản, có thể dùng:

- **Constructor binding** cho class bất biến; mapper đưa dữ liệu vào constructor có validation.
- **Record binding** khi thư viện hỗ trợ record components và canonical constructor.
- **Static factory hoặc builder** nếu framework hỗ trợ định cấu hình đường tạo.
- **API nghiệp vụ** để cập nhật mutable entity theo invariant; với immutable object thì tạo instance mới và lưu/thay thế instance đó.

Không giả định mọi serializer/ORM đều hỗ trợ cùng một chế độ; cần xem tài liệu của framework và kiểm chứng bằng test mapping dữ liệu thật. Khi cập nhật database, còn phải xử lý concurrency, transaction, version, cache hoặc identity theo kiến trúc thực tế. Tạo object mới trong bộ nhớ **không tự động** thay đối tượng cũ trong collection, database hay các tham chiếu đã được các thành phần khác giữ.

Đối với cấu hình immutable được nhiều thread đọc chung, holder có `AtomicReference<Configuration>` hoặc cơ chế đồng bộ phù hợp là cách thay thế snapshot. Những thread giữ tham chiếu snapshot cũ vẫn đọc giá trị cũ; caller muốn thấy snapshot mới phải đọc lại từ holder. Đây là hợp đồng nhất quán cần nêu rõ.

## 9. Lỗi phổ biến

| Lỗi | Cách làm phù hợp hơn |
| --- | --- |
| Thiếu setter cho `final` thì dùng `Field.set` | Dùng constructor/factory/copy API, hoặc thiết kế field thật sự mutable có method cập nhật |
| Viết setter định gán lại `final` field | Java không cho gán lại theo cách này; trả object mới nếu immutable |
| Gọi `old.withPoints(10)` nhưng bỏ giá trị trả về | Giữ `newState = old.withPoints(10)` và thay tham chiếu nơi cần dùng |
| Cho rằng `final List` đồng nghĩa danh sách bất biến | Cần defensive copy và kiểm tra tính mutable của phần tử |
| Cho rằng `setAccessible(true)` chắc chắn ghi được `static final` hay field của record | Đây là các nhóm final field bị hạn chế ghi rõ ràng |
| Bỏ `final` khỏi toàn bộ class để framework khởi tạo được | Xem hỗ trợ constructor/record/factory/builder binding trước |
| Factory không validate, constructor validate một kiểu khác | Tập trung invariant trong đường khởi tạo chung |
| Có nhiều thread cập nhật cùng object mutable mà chỉ thêm một setter thường | Chọn đồng bộ hóa/atomic/transaction phù hợp; kiểm tra thao tác nhiều field |
| Thay instance immutable nhưng quên cập nhật cache/repository/state holder | Công bố và lưu tham chiếu instance mới theo contract hệ thống |

## 10. Checklist quyết định khi gặp yêu cầu “sửa final field”

1. **Đó có phải dữ liệu khởi tạo?** Nếu đúng, đưa qua constructor, factory hoặc builder trước khi công bố object.
2. **Có cần giữ nguyên đối tượng cũ không?** Với snapshot bất biến, chọn `withXxx`/copy và sử dụng object mới. Với domain mutable, cân nhắc update method rõ ràng.
3. **Invariant là gì?** Xác định điều kiện hợp lệ của cả dữ liệu khởi tạo và cập nhật.
4. **Có chia sẻ giữa nhiều thread hoặc qua database/cache không?** Xác định cơ chế công bố/lưu trạng thái mới có tính nhất quán.
5. **Framework đã hỗ trợ đường khởi tạo chính thức chưa?** Tìm constructor/record/factory/builder binding trước khi phụ thuộc private field.
6. **Có đang dùng Reflection chỉ để vượt qua lỗi thiết kế?** Nếu có, sửa API ở tầng domain hoặc adapter tích hợp thay vì can thiệp final field.

**Ghi nhớ:** `final` xác định ranh giới gán giá trị trong vòng đời object. Constructor/factory/builder thiết lập trạng thái ban đầu; copy/wither tạo trạng thái bất biến mới; update API có điều kiện quản lý trạng thái mutable. Reflection có thể cung cấp metadata nhưng không nên là đường cập nhật `final` field của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)
