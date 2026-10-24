<a id="back-to-top"></a>

# Record

## Menu
- [isRecord và getRecordComponents](#record-types-001)
- [RecordComponent: tên, kiểu, generic type và accessor](#record-types-002)
- [Thứ tự component và canonical constructor](#record-types-003)
- [Khởi tạo record qua constructor thay vì sửa field final](#record-types-004)
- [Component annotation khác annotation trên field, accessor hoặc parameter](#record-types-005)
- [Annotation được truyền sang các khai báo liên quan theo @Target](#record-types-006)
- [Accessor được khai báo tường minh và metadata thực tế](#record-types-007)
- [RecordComponent.getAnnotatedType và kiểu thành phần có annotation](#record-types-008)
- [Thiết kế công cụ ánh xạ hỗ trợ record và lớp thông thường](#record-types-009)

## <a id="record-types-001">isRecord và getRecordComponents</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện record bằng `isRecord()` và `getRecordComponents()`

Một công cụ tổng quát không phải lúc nào cũng biết trước các kiểu dữ liệu của ứng dụng. Khi nhận `Class<?>`, công cụ có thể cần phân biệt record với lớp thông thường, tìm tên các thành phần dữ liệu và đọc giá trị từ một đối tượng.

Java cung cấp hai điểm bắt đầu: **`isRecord()` xác định kiểu có phải record không**, còn **`getRecordComponents()` mô tả các thành phần được khai báo trong header của record**. Từ mô tả đó, chương trình có thể tìm phương thức truy cập tương ứng để đọc dữ liệu.

Các API này có từ Java 16; ví dụ trong bài dùng Java 17 trở lên, không cần tính năng preview.

## 1. Record và thành phần record là gì?

Xét khai báo:

```java
record Ticket(String title, int priority, boolean ready) {
}
```

`title`, `priority`, `ready` là ba **thành phần record (record component)**. Chúng được khai báo trong phần header giữa hai dấu ngoặc tròn, theo thứ tự đó.

Đối với mỗi thành phần, record có trường dữ liệu tương ứng và một phương thức truy cập công khai. Chẳng hạn, thành phần `title` liên hệ với trường `title` và phương thức `title()`. Đây là quan hệ trong mô hình [record của Java](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Record.html).

Trong Reflection, ba đối tượng có vai trò riêng:

| Đối tượng metadata | Mô tả điều gì? |
| --- | --- |
| `RecordComponent` | Một thành phần trong header của record |
| `Field` | Một trường dữ liệu của lớp |
| `Method` | Một phương thức, gồm cả accessor của record |

`RecordComponent` không phải giá trị `title` của một đối tượng cụ thể. Nó mô tả rằng kiểu record có thành phần tên `title`, kiểu gì và accessor nào.

## 2. Hai API trên `Class<?>`

Chữ ký:

```java
public boolean isRecord()
public RecordComponent[] getRecordComponents()
```

Đây là chữ ký để tra cứu API, không phải các phương thức cần tự cài đặt.

Theo [hợp đồng `Class.getRecordComponents()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getRecordComponents()), mảng kết quả giữ thứ tự khai báo trong header. Nếu là record không có thành phần, mảng có độ dài `0`; nếu không phải record, kết quả là `null`.

| Kiểu đang xét | `isRecord()` | `getRecordComponents()` |
| --- | --- | --- |
| Record có thành phần | `true` | Mảng metadata thành phần |
| Record có header rỗng | `true` | Mảng rỗng |
| Lớp thông thường, interface, enum, kiểu mảng hoặc kiểu nguyên thủy | `false` | `null` |
| Chính lớp cơ sở `java.lang.Record` | `false` | `null` |

Vì vậy, không được gọi `.length` hoặc duyệt trực tiếp kết quả khi chưa xử lý trường hợp `null`. Đồng thời, không xem mảng rỗng như bằng chứng rằng kiểu không phải record.

Bạn có thể kiểm tra `isRecord()` trước để rẽ nhánh theo loại kiểu, hoặc lấy mảng một lần rồi kiểm tra `null`. Trong một công cụ chỉ đọc thành phần record, cách thứ hai thường giúp gom điều kiện và dữ liệu cần dùng vào cùng một chỗ.

## 3. Đọc thông tin từ `RecordComponent`

Mỗi phần tử cung cấp các thông tin sau qua [API `RecordComponent`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/RecordComponent.html):

| Phương thức | Thông tin nhận được |
| --- | --- |
| `getName()` | Tên thành phần |
| `getType()` | Kiểu được biểu diễn bằng `Class<?>` |
| `getGenericType()` | Kiểu khai báo được biểu diễn bằng `Type`, gồm thông tin generic nếu có |
| `getAccessor()` | Đối tượng `Method` của phương thức truy cập |
| `getDeclaringRecord()` | Lớp record khai báo thành phần |

Ví dụ, với thành phần `List<String> labels`, `getType()` cho `List.class`, còn `getGenericType()` biểu diễn `List<String>`. Metadata kiểu khai báo và giá trị đang chứa trong một đối tượng là hai tầng thông tin khác nhau.

## 4. Thí nghiệm đầy đủ trên Java 17+

Chương trình dưới đây có record có dữ liệu, record rỗng, record generic và một lớp thông thường có cấu trúc gần giống record. Nó vừa đọc metadata của kiểu, vừa đọc giá trị từ đối tượng.

Lưu toàn bộ vào **`RecordInspectionDemo.java`**:

```java
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RecordInspectionDemo {
    public record Ticket(
            String title, int priority, boolean ready, List<String> labels) {
        public static final String KIND = "ticket";
    }

    public record Marker() {
    }

    public record Box<T>(T value) {
    }

    public static final class PlainTicket {
        private final String title;

        public PlainTicket(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    private static void describe(Class<?> type) {
        System.out.println(type.getSimpleName() + " isRecord = " + type.isRecord());
        RecordComponent[] components = type.getRecordComponents();
        if (components == null) {
            System.out.println("components = null");
            return;
        }
        System.out.println("component count = " + components.length);
        for (RecordComponent component : components) {
            System.out.println(component.getName()
                    + " | type=" + component.getType().getTypeName()
                    + " | generic=" + component.getGenericType().getTypeName()
                    + " | accessor=" + component.getAccessor().getName());
        }
    }

    public static Map<String, Object> readRecord(Object value)
            throws ReflectiveOperationException {
        Objects.requireNonNull(value, "value");
        Class<?> type = value.getClass();
        RecordComponent[] components = type.getRecordComponents();
        if (components == null) {
            throw new IllegalArgumentException("Expected record: " + type.getName());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (RecordComponent component : components) {
            Object componentValue = component.getAccessor().invoke(value);
            result.put(component.getName(), componentValue);
        }
        return result;
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        describe(Ticket.class);
        System.out.println("Declared fields, sorted = "
                + Arrays.stream(Ticket.class.getDeclaredFields())
                        .map(field -> field.getName())
                        .sorted()
                        .toList());

        Ticket ticket = new Ticket(
                "Import", 2, true, List.of("java", "reflection"));
        System.out.println("Read values = " + readRecord(ticket));
        System.out.println("First component owner = "
                + Ticket.class.getRecordComponents()[0]
                        .getDeclaringRecord().getSimpleName());
        System.out.println("ready accessor parameters = "
                + Ticket.class.getRecordComponents()[2]
                        .getAccessor().getParameterCount());
        try {
            Ticket.class.getMethod("isReady");
        } catch (NoSuchMethodException ex) {
            System.out.println("isReady lookup = " + ex.getClass().getSimpleName());
        }

        describe(Marker.class);
        System.out.println("Read empty record = " + readRecord(new Marker()));
        describe(PlainTicket.class);
        describe(Record.class);

        describe(Box.class);
        Box<String> box = new Box<>("hello");
        System.out.println("Read generic record = " + readRecord(box));
        System.out.println("Box runtime class = "
                + (box.getClass() == Box.class));

        try {
            readRecord(new PlainTicket("Import"));
        } catch (IllegalArgumentException ex) {
            System.out.println("Read ordinary class = " + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 -Xlint:all -Werror RecordInspectionDemo.java
java RecordInspectionDemo
```

Kết quả:

```text
Ticket isRecord = true
component count = 4
title | type=java.lang.String | generic=java.lang.String | accessor=title
priority | type=int | generic=int | accessor=priority
ready | type=boolean | generic=boolean | accessor=ready
labels | type=java.util.List | generic=java.util.List<java.lang.String> | accessor=labels
Declared fields, sorted = [KIND, labels, priority, ready, title]
Read values = {title=Import, priority=2, ready=true, labels=[java, reflection]}
First component owner = Ticket
ready accessor parameters = 0
isReady lookup = NoSuchMethodException
Marker isRecord = true
component count = 0
Read empty record = {}
PlainTicket isRecord = false
components = null
Record isRecord = false
components = null
Box isRecord = true
component count = 1
value | type=java.lang.Object | generic=T | accessor=value
Read generic record = {value=hello}
Box runtime class = true
Read ordinary class = IllegalArgumentException
```

## 5. Hiểu kết quả của thí nghiệm

### Thứ tự thành phần đến từ header

Header của `Ticket` khai báo `title`, `priority`, `ready`, `labels`. Vòng lặp đi theo đúng thứ tự đó. `priority` không được đưa lên trước chỉ vì kiểu của nó là `int`, và `labels` không được đưa lên đầu theo bảng chữ cái.

Thứ tự này hữu ích khi trình bày cấu trúc dữ liệu, ghép dữ liệu với chữ ký constructor chuẩn hoặc giữ thứ tự cột theo khai báo. Khi dùng metadata cho định dạng lưu trữ lâu dài, vẫn cần quản lý thay đổi cấu trúc; đổi vị trí thành phần trong mã nguồn không phải một thay đổi vô hình đối với công cụ phụ thuộc vào vị trí.

### Trường tĩnh không phải thành phần record

`Ticket` có trường tĩnh `KIND`. Vì vậy, danh sách trường dữ liệu được in trong thí nghiệm gồm `KIND` cùng bốn trường tương ứng các thành phần. Mảng `getRecordComponents()` chỉ có bốn thành phần trong header.

Danh sách trường được **chủ động sắp xếp theo tên** trước khi in, nên dòng `[KIND, labels, priority, ready, title]` không thể được dùng làm bằng chứng về thứ tự mặc định của Reflection.

Nếu mục tiêu là hiểu cấu trúc record, hãy lấy danh sách thành phần trực tiếp. Không lấy tất cả trường rồi tự giả định rằng mọi trường đều là một thành phần hoặc tự dựa vào thứ tự trường.

### Record rỗng và lớp không phải record

`Marker` có header `()` nên `isRecord()` cho `true`, số thành phần bằng `0`, và hàm đọc giá trị tạo bản đồ rỗng `{}`.

`PlainTicket` có một trường `title` và phương thức `title()`, nhưng vẫn là lớp thông thường. Nó không trở thành record chỉ vì đặt tên phương thức theo cùng quy ước. Kết quả là `isRecord()` bằng `false` và mảng thành phần bằng `null`.

`Record.class` cũng cho kết quả tương tự: đây là lớp cơ sở, không phải một record cụ thể có header. Không dùng tên lớp, việc có accessor, hoặc phép kiểm tra kế thừa quá rộng để thay cho `isRecord()`.

## 6. Metadata không tự chứa giá trị của đối tượng

`describe(Ticket.class)` không cần tạo `Ticket`, vì nó chỉ đọc cấu trúc kiểu. Ngược lại, muốn biết `title` đang bằng gì, bạn cần một đối tượng, như biến `ticket` trong ví dụ.

Hàm `readRecord` thực hiện quy trình:

1. Từ chối đối tượng `null`.
2. Lấy lớp thực tế bằng `value.getClass()`.
3. Lấy metadata thành phần và từ chối lớp không phải record.
4. Lấy accessor từ từng `RecordComponent`.
5. Gọi accessor trên đúng đối tượng `value` rồi lưu kết quả theo tên thành phần.

`LinkedHashMap` trong thí nghiệm giữ thứ tự thêm khóa, nên dòng kết quả `Read values` có thứ tự giống header.

Đoạn cốt lõi là:

```java
Object componentValue = component.getAccessor().invoke(value);
```

Đây là đoạn trích trong hàm đã có đầy đủ ở chương trình. `component` là metadata, còn `value` là đối tượng nhận lời gọi. Kiểu trả về của lời gọi động là `Object`; với thành phần nguyên thủy như `int` hoặc `boolean`, kết quả được đóng gói thành đối tượng tương ứng.

### Accessor của record không theo tiền tố JavaBean

`ready` trong ví dụ có accessor `ready()`, không phải `getReady()` hay `isReady()`. Dòng `isReady lookup = NoSuchMethodException` chứng minh không nên tự ghép tiền tố khi tìm accessor mặc định của record.

`getAccessor()` trả đúng `Method` tương ứng, giúp công cụ không phải đoán tên. Quy tắc tên, tham số rỗng và kiểu trả về của accessor được xác định trong [JLS về thành viên record](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.10.3).

### Gọi accessor vẫn là gọi phương thức

Record có thể khai báo accessor tường minh. Vì vậy, gọi accessor là thực thi hành vi của phương thức đó, không phải luôn đọc trực tiếp trường dữ liệu thô. Công cụ cần hiểu hợp đồng đọc dữ liệu của mình.

Ví dụ dùng record lồng công khai nên có thể gọi accessor mà không mở quyền truy cập đặc biệt. Khi dùng với record từ nơi khác, quyền truy cập lớp, package và module vẫn có thể ảnh hưởng đến lời gọi. Tìm được metadata không tự cấp quyền vượt mọi ranh giới truy cập.

`readRecord` khai báo `throws ReflectiveOperationException` để người gọi biết việc gọi động có thể thất bại. Trong mã thực tế, cần phân biệt lỗi truy cập với ngoại lệ do accessor ném ra và giữ thông tin nguyên nhân; không âm thầm bỏ qua một thành phần khi đọc thất bại.

## 7. Record generic và giới hạn thông tin kiểu

`Box<T>(T value)` cho kết quả:

- `getType()` của `value` là `Object` vì `T` không có giới hạn cụ thể hơn.
- `getGenericType()` biểu diễn biến kiểu `T` trong khai báo.
- Đọc đối tượng `new Box<String>("hello")` cho giá trị `hello`.

Điều đó không có nghĩa metadata của `Box.class` đã đổi `T` thành `String` theo từng đối tượng. Biểu thức `box.getClass() == Box.class` vẫn cho `true`. Cùng một metadata lớp được dùng cho các cách gán tham số kiểu khác nhau của `Box`.

Nếu một thư viện cần biết tham số kiểu cụ thể của ngữ cảnh sử dụng, nó phải nhận thêm thông tin kiểu từ ngữ cảnh đó. Chỉ nhìn vào giá trị đang chứa hoặc vào `getClass()` không phải giải pháp tổng quát để phục hồi toàn bộ thông tin generic.

Phân biệt hai câu hỏi khi thiết kế công cụ:

- Kiểu được **khai báo** của thành phần là gì?
- Giá trị của thành phần trên **đối tượng đang xét** là gì?

Hai câu hỏi bổ sung cho nhau; không thay thế được cho nhau.

## 8. Áp dụng và lỗi thường gặp

Cặp API này phù hợp cho công cụ hiển thị cấu trúc dữ liệu, ánh xạ theo tên thành phần, trình kiểm tra dữ liệu hoặc thư viện cần có một nhánh xử lý riêng cho record. Nó giúp tránh suy luận loại kiểu từ hình dáng lớp.

| Lỗi thường gặp | Cách xử lý đúng |
| --- | --- |
| Gọi `.length` trên kết quả của lớp thông thường | Kiểm tra `null` hoặc rẽ nhánh bằng `isRecord()` |
| Xem record rỗng như không phải record | Phân biệt mảng rỗng với `null` |
| Xem `Record.class` như record cụ thể | Dùng kết quả `isRecord()` |
| Lấy tất cả trường làm thành phần record | Dùng `getRecordComponents()` cho cấu trúc header |
| Tự sắp xếp thành phần rồi nghĩ đó là thứ tự khai báo | Giữ nguyên thứ tự API khi cần thứ tự header |
| Coi `RecordComponent` là dữ liệu của một instance | Dùng accessor trên một đối tượng phù hợp |
| Tự thêm tiền tố `get` hoặc `is` | Dùng `getAccessor()` |
| Cho rằng đọc metadata đồng nghĩa có quyền gọi mọi accessor | Xử lý các ràng buộc truy cập và lỗi gọi động |
| Cho rằng generic của đối tượng đã thay thế `T` trong metadata lớp | Phân biệt kiểu khai báo với ngữ cảnh tham số kiểu |

Record cũng không tự làm toàn bộ cây đối tượng bên trong trở nên bất biến. Nếu một thành phần tham chiếu tới dữ liệu có thể thay đổi, công cụ đọc không nên tự ý sửa dữ liệu đó chỉ vì đã lấy được nó qua accessor.

### Tự kiểm tra

1. Vì sao `Marker.class.getRecordComponents()` cho mảng rỗng, còn `PlainTicket.class.getRecordComponents()` cho `null`?
2. Thêm một trường tĩnh vào `Ticket` có làm số thành phần tăng lên không?
3. Đổi thứ tự `priority` và `ready` trong header ảnh hưởng đến kết quả metadata như thế nào?
4. Vì sao có `Class<?>` đã đủ để mô tả thành phần nhưng chưa đủ để biết giá trị `title`?
5. Với `Box<String>`, tại sao kiểu generic khai báo của `value` vẫn là `T`?

Cần nắm được luồng: **nhận diện record → lấy metadata thành phần theo header → chọn thông tin kiểu hoặc accessor → đọc giá trị trên đúng đối tượng khi cần**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-types-002">RecordComponent: tên, kiểu, generic type và accessor</a>

<details>
<summary>Xem chi tiết</summary>

# RecordComponent: tên, kiểu, generic type và accessor

## 1. RecordComponent mô tả điều gì?

Phần đầu một khai báo record liệt kê các **thành phần (record component)**:

~~~java
record Position(int x, int y) {
}
~~~

Ở đây có hai thành phần tên `x` và `y`, cùng kiểu `int`. Java tạo các trường lưu trữ và các phương thức truy cập tương ứng, nhưng bản thân **thành phần record**, **trường** và **phương thức** là những khái niệm khác nhau.

`java.lang.reflect.RecordComponent` cung cấp metadata của một thành phần được khai báo trong phần đầu record. Từ metadata đó, ta có thể biết:

- Tên thành phần.
- Kiểu biểu diễn bằng `Class<?>`.
- Cấu trúc kiểu generic biểu diễn bằng `Type`.
- Phương thức truy cập, gọi là **accessor**, của thành phần.
- Lớp record khai báo thành phần đó.

Điều này hữu ích cho công cụ sinh tài liệu, khảo sát mô hình dữ liệu hoặc đọc các thành phần theo thứ tự khai báo. Thay vì đoán từ mọi trường và phương thức của một lớp, công cụ có thể bắt đầu từ danh sách thành phần record.

API `RecordComponent` có từ Java 16. Các chương trình trong bài dùng Java 17 trở lên và không cần tính năng preview.

## 2. Đi từ Class đến danh sách thành phần

Điểm vào là `Class.getRecordComponents()`:

~~~java
RecordComponent[] components = Position.class.getRecordComponents();
~~~

Đoạn trên là minh họa; cần import `java.lang.reflect.RecordComponent` và khai báo `Position` khi đặt trong chương trình.

Có ba trường hợp phải phân biệt:

| Token lớp | `isRecord()` | `getRecordComponents()` |
| --- | --- | --- |
| Lớp record có thành phần | `true` | Mảng khác `null`, chứa các thành phần |
| Lớp record không có thành phần, như `record Empty() {}` | `true` | Mảng rỗng |
| Lớp không phải record | `false` | `null` |

Các thành phần được trả về **theo đúng thứ tự khai báo trong phần đầu record**. Đây là hợp đồng của [Class.getRecordComponents](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getRecordComponents()).

Ví dụ `record Position(int x, int y)` có thứ tự `x`, rồi `y`. Không nên suy ra thứ tự này bằng cách duyệt `getDeclaredFields()` hoặc `getDeclaredMethods()`, vì các API đó không bảo đảm thứ tự khai báo.

Các trường `static` bổ sung hoặc phương thức tiện ích trong thân record cũng không tự trở thành thành phần record.

## 3. Bốn nhóm thông tin chính

| API trên `RecordComponent` | Kiểu trả về | Ý nghĩa |
| --- | --- | --- |
| `getName()` | `String` | Tên thành phần được khai báo |
| `getType()` | `Class<?>` | Token kiểu; với generic, đây là kiểu sau xóa kiểu |
| `getGenericType()` | `Type` | Kiểu khai báo, có thể giữ cấu trúc tham số hóa hoặc biến kiểu |
| `getAccessor()` | `Method` | Phương thức truy cập tương ứng |
| `getDeclaringRecord()` | `Class<?>` | Record khai báo thành phần |

Tài liệu [RecordComponent](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/RecordComponent.html) mô tả các API này.

`RecordComponent` không phải là `Field`. Không có thao tác `component.get(record)` hoặc `component.set(record, value)` tương tự thao tác đọc/ghi trường. Muốn đọc giá trị thông qua accessor, lấy `Method` rồi gọi nó trên một đối tượng record.

## 4. Chương trình đầy đủ: tên, kiểu, generic và giá trị

Ví dụ sau có bốn dạng thành phần:

- `String code`: kiểu lớp thông thường.
- `int priority`: kiểu nguyên thủy.
- `T weight`, với `T extends Number`: biến kiểu có cận.
- `List<String> tags`: kiểu tham số hóa.

Lưu vào `RecordComponentDemo.java`:

~~~java
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;

public class RecordComponentDemo {
    public record Parcel<T extends Number>(
            String code, int priority, T weight, List<String> tags) {
    }

    static String kind(Type type) {
        if (type instanceof Class<?>) {
            return "Class";
        }
        if (type instanceof ParameterizedType) {
            return "ParameterizedType";
        }
        if (type instanceof TypeVariable<?>) {
            return "TypeVariable";
        }
        return "Other";
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Parcel<Integer> parcel =
                new Parcel<>("P-7", 2, 3, List.of("fragile"));

        RecordComponent[] components = Parcel.class.getRecordComponents();

        System.out.println("components: "
                + Arrays.stream(components)
                        .map(RecordComponent::getName)
                        .toList());

        for (RecordComponent component : components) {
            Method accessor = component.getAccessor();
            System.out.println(component.getName()
                    + " | type=" + component.getType().getTypeName()
                    + " | generic=" + component.getGenericType().getTypeName()
                    + " | kind=" + kind(component.getGenericType()));
            System.out.println("accessor=" + accessor.getName()
                    + "; parameters=" + accessor.getParameterCount()
                    + "; genericReturn="
                    + accessor.getGenericReturnType().getTypeName());
            System.out.println("value=" + accessor.invoke(parcel));
        }

        Object priority = components[1].getAccessor().invoke(parcel);
        System.out.println("boxed priority: "
                + priority.getClass().getSimpleName());

        TypeVariable<?> weightType =
                (TypeVariable<?>) components[2].getGenericType();
        System.out.println("weight bounds: "
                + Arrays.stream(weightType.getBounds())
                        .map(Type::getTypeName)
                        .toList());

        ParameterizedType tagsType =
                (ParameterizedType) components[3].getGenericType();
        System.out.println("tags arguments: "
                + Arrays.stream(tagsType.getActualTypeArguments())
                        .map(Type::getTypeName)
                        .toList());

        System.out.println("declaring record: "
                + (components[0].getDeclaringRecord() == Parcel.class));

        Parcel<Double> other =
                new Parcel<>("P-8", 1, 2.5, List.of("local"));
        System.out.println("same runtime class: "
                + parcel.getClass().equals(other.getClass()));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 RecordComponentDemo.java
java RecordComponentDemo
~~~

Kết quả chính xác:

~~~text
components: [code, priority, weight, tags]
code | type=java.lang.String | generic=java.lang.String | kind=Class
accessor=code; parameters=0; genericReturn=java.lang.String
value=P-7
priority | type=int | generic=int | kind=Class
accessor=priority; parameters=0; genericReturn=int
value=2
weight | type=java.lang.Number | generic=T | kind=TypeVariable
accessor=weight; parameters=0; genericReturn=T
value=3
tags | type=java.util.List | generic=java.util.List<java.lang.String> | kind=ParameterizedType
accessor=tags; parameters=0; genericReturn=java.util.List<java.lang.String>
value=[fragile]
boxed priority: Integer
weight bounds: [java.lang.Number]
tags arguments: [java.lang.String]
declaring record: true
same runtime class: true
~~~

Chương trình kiểm tra loại `Type` qua các interface công khai như `ParameterizedType` và `TypeVariable`. Nó không in tên lớp triển khai nội bộ của JDK để suy luận loại generic.

### 4.1. Tên thành phần là tên trong khai báo

`getName()` cho `code`, `priority`, `weight` và `tags`, đúng theo phần đầu của `Parcel`.

Tên này không phải:

- Giá trị chuỗi đang lưu trong thành phần, như `P-7`.
- Tên của lớp triển khai một giá trị.
- Tên getter theo quy ước JavaBeans.

Accessor record dùng tên thành phần: `code()`, `priority()`, `weight()`, `tags()`. Không mặc định chúng có dạng `getCode()` hoặc `getWeight()`.

Không cần tùy chọn `javac -parameters` để `RecordComponent.getName()` biết tên thành phần. Đây là metadata thành phần record, không phải việc đoán tên tham số của một phương thức tùy ý.

### 4.2. getType cho token Class

Với `code`, token là `String.class`. Với `priority`, token là `int.class`.

Với thành phần `weight` được khai báo là `T`, mà `T extends Number`, kiểu sau xóa kiểu là `Number`; vì vậy `getType()` cho `Number.class`.

Với `tags`, token là `List.class`. Không có một class literal `List<String>.class` để trả về.

Điểm này giúp chọn công cụ đúng: dùng `getType()` khi cần token lớp để đối chiếu hoặc tìm chữ ký Reflection; dùng `getGenericType()` khi cần hiểu cấu trúc kiểu khai báo đầy đủ hơn.

### 4.3. getGenericType giữ thông tin của khai báo

Các kết quả trong chương trình:

| Thành phần | `getType()` | `getGenericType()` |
| --- | --- | --- |
| `code` | `String.class` | Một `Class` biểu diễn `String` |
| `priority` | `int.class` | Một `Class` biểu diễn `int` |
| `weight` | `Number.class` | `TypeVariable` tên `T` |
| `tags` | `List.class` | `ParameterizedType` biểu diễn `List<String>` |

Vì `Class` cũng triển khai `Type`, kết quả `getGenericType()` không phải lúc nào cũng là `ParameterizedType`. Ép mọi kết quả sang `ParameterizedType` sẽ làm chương trình lỗi với các thành phần như `code` và `priority`.

Trong thí nghiệm, biến kiểu `T` có cận `Number`, đọc được bằng `TypeVariable.getBounds()`. Kiểu tham số hóa của `tags` có đối số `String`, đọc được bằng `ParameterizedType.getActualTypeArguments()`.

### 4.4. Metadata T không tự đổi thành Integer theo đối tượng

Đối tượng `parcel` được tạo qua khai báo `Parcel<Integer>`, nhưng metadata của thành phần `weight` vẫn là `T`, không tự trở thành `Integer`.

Cuối chương trình, đối tượng `Parcel<Double>` và đối tượng `Parcel<Integer>` có cùng lớp thực tế. `RecordComponent` được lấy từ lớp khai báo `Parcel`, nên không lưu riêng đối số generic của từng biến bên ngoài.

Nếu công cụ có ngữ cảnh bổ sung, chẳng hạn một trường được khai báo là `Parcel<Integer>`, nó có thể phân tích `Type` của ngữ cảnh đó và xây dựng ánh xạ `T -> Integer`. Đó là bước phân giải generic riêng; `component.getGenericType()` tự nó không làm bước này chỉ từ một instance.

Cũng không suy luận rằng nhìn thấy giá trị `3` chứng minh mọi giá trị của `T` là `Integer`. Giá trị đang đọc và kiểu khai báo là hai loại thông tin khác nhau.

## 5. Type có cấu trúc, không chỉ là một chuỗi để in

`getTypeName()` hữu ích khi trình bày kết quả cho người đọc. Nếu công cụ cần xử lý kiểu, hãy dùng các interface của `Type` để đi theo cấu trúc.

Một số trường hợp có thể gặp:

| Kiểu thành phần trong khai báo | Kiểu sau xóa kiểu, với `T extends Number` nếu có | Dạng `Type` thường cần xử lý |
| --- | --- | --- |
| `String` | `String` | `Class` |
| `int` | `int` | `Class` của kiểu nguyên thủy |
| `String[]` | `String[]` | `Class` của mảng |
| `List<String>` | `List` | `ParameterizedType` |
| `T` | `Number` | `TypeVariable` |
| `T[]` | `Number[]` | `GenericArrayType` |

Với một thành phần như `List<? extends Number>`, đối tượng ở cấp ngoài cùng là `ParameterizedType`; đối số kiểu bên trong là một `WildcardType`. Nếu công cụ khảo sát generic đầy đủ, cần xử lý lồng nhau thay vì chỉ kiểm tra một cấp.

Không nên phân tích chuỗi `getTypeName()` bằng cách tách dấu phẩy và dấu ngoặc nhọn để thay cho các API cấu trúc. Kiểu generic có thể lồng nhiều cấp, có wildcard, biến kiểu và mảng generic.

`getGenericSignature()` cung cấp biểu diễn chữ ký generic ở dạng chuỗi metadata. Nếu mục đích là hiểu kiểu khai báo, `getGenericType()` thường là điểm bắt đầu rõ ràng hơn.

Generic metadata cũng không thay thế việc kiểm tra dữ liệu. Việc đọc được khai báo `List<String>` không tự duyệt và xác minh mọi phần tử trong một danh sách đang chạy.

## 6. Accessor nối metadata với một đối tượng cụ thể

`getAccessor()` trả về `java.lang.reflect.Method`. Với record Java hợp lệ, đó là phương thức truy cập tương ứng, có tên thành phần, không nhận tham số và trả về kiểu của thành phần.

Ví dụ trong chương trình:

~~~java
Method accessor = component.getAccessor();
Object value = accessor.invoke(parcel);
~~~

Đoạn này phân tách hai việc:

1. `getAccessor()` lấy metadata phương thức.
2. `invoke(parcel)` thực thi phương thức trên đối tượng `parcel`.

`RecordComponent` tự nó không chứa giá trị của một instance. Hai đối tượng cùng lớp record dùng chung mô tả thành phần nhưng có thể có các giá trị khác nhau.

### Kiểu nguyên thủy được đóng hộp khi đi qua Object

Trong ví dụ, `priority()` trả về `int`. Tuy nhiên `Method.invoke(...)` trả về `Object`, nên kết quả số nguyên được đóng hộp thành `Integer`.

Vì vậy:

- `component.getType()` của `priority` vẫn là `int.class`.
- Đối tượng nhận được từ `invoke(...)` có lớp `Integer`.
- Đây không phải việc metadata của thành phần bị đổi từ `int` sang `Integer`.

Nếu thành phần có kiểu tham chiếu và accessor trả về `null`, kết quả Reflection cũng có thể là `null`. Không gọi `getClass()` trên giá trị vừa đọc khi chưa xét trường hợp đó.

### Điều kiện và lỗi khi gọi accessor

| Tình huống | Kết quả cần chú ý |
| --- | --- |
| Truyền một instance không phù hợp với lớp khai báo phương thức | Có thể ném `IllegalArgumentException` |
| Truyền `null` thay đối tượng nhận cho accessor instance | Ném `NullPointerException` |
| Gọi phương thức không truy cập được từ mã hiện tại | Có thể ném `IllegalAccessException` |
| Accessor tự ném ngoại lệ khi thực thi | Ngoại lệ được bọc trong `InvocationTargetException` |

Accessor là `public` không có nghĩa một lớp record không truy cập được hoặc một ranh giới module tự động bị bỏ qua. Ví dụ hoàn chỉnh dùng record lồng `public` trong lớp `public`, nên không cần mở truy cập bằng `setAccessible(true)`.

## 7. Accessor có thể do người viết định nghĩa

Không mặc định rằng gọi accessor luôn tương đương với lấy nguyên tham chiếu đang lưu trong trường private. Record có thể khai báo accessor tường minh có phần thân riêng.

Ví dụ sau sử dụng sao chép bảo vệ cho thành phần mảng:

- Constructor sao chép mảng đầu vào.
- Accessor trả về một bản sao.
- Reflection gọi accessor đó như một lời gọi phương thức bình thường.

Lưu vào `RecordAccessorDemo.java`:

~~~java
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

public class RecordAccessorDemo {
    public record Packet(byte[] bytes) {
        public Packet {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        byte[] original = {1, 2};
        Packet packet = new Packet(original);
        RecordComponent component =
                Packet.class.getRecordComponents()[0];

        byte[] first =
                (byte[]) component.getAccessor().invoke(packet);
        System.out.println("accessor: "
                + component.getAccessor().getName());
        System.out.println("return type: "
                + component.getType().getTypeName());
        System.out.println("first read: " + Arrays.toString(first));
        System.out.println("same as original: " + (first == original));

        first[0] = 9;
        byte[] second =
                (byte[]) component.getAccessor().invoke(packet);
        System.out.println("after changing result: "
                + Arrays.toString(second));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 RecordAccessorDemo.java
java RecordAccessorDemo
~~~

Kết quả:

~~~text
accessor: bytes
return type: byte[]
first read: [1, 2]
same as original: false
after changing result: [1, 2]
~~~

Sau khi sửa mảng nhận được ở lần đọc đầu, lần đọc sau vẫn cho `[1, 2]`. `getAccessor().invoke(packet)` đã thực thi mã `bytes.clone()`, không trả trực tiếp trường lưu trữ.

Ví dụ này không có nghĩa mọi record đều tự sao chép mảng hoặc tập hợp. Record không tự bảo đảm bất biến sâu cho dữ liệu tham chiếu. Hành vi sao chép trong chương trình đến từ constructor và accessor được viết tường minh.

Khi xây dựng công cụ đọc record, việc chọn accessor hay đọc trực tiếp trường là một quyết định về hành vi quan sát. Accessor là giao diện phương thức của record và có thể có logic; đọc trường là thao tác khác, thường còn cần xử lý truy cập private.

## 8. Chọn dữ liệu đầu ra: lược đồ thành phần và giá trị runtime

Khi thiết kế công cụ khảo sát record, nên tách hai sản phẩm có mục đích khác nhau:

| Đầu ra | Nguồn dữ liệu | Ví dụ với `Parcel<T>` |
| --- | --- | --- |
| Lược đồ (*schema*) | `getName()`, `getType()`, `getGenericType()` | Thành phần `weight` khai báo kiểu `T extends Number` |
| Giá trị của một instance | `getAccessor().invoke(instance)` | `weight` đang có giá trị `3` |

Lược đồ có thể được lập một lần từ `Parcel.class`; để đọc giá trị phải có một instance phù hợp. Nếu dùng `LinkedHashMap` ghép tên thành phần với giá trị accessor, hãy duyệt mảng `RecordComponent[]` theo thứ tự header. Đối tượng kết quả chỉ là ảnh chụp các giá trị mà accessor trả về; nó không tự sao chép sâu danh sách hoặc những đối tượng mutable trong record.

Việc xây một bộ đọc giá trị hoàn chỉnh phải quyết định thêm cách xử lý `null`, lỗi truy cập và `InvocationTargetException`. Metadata generic không thay thế các kiểm tra dữ liệu đó.

## 9. Những lỗi thường gặp

**Đồng nhất RecordComponent với Field.** Thành phần record có quan hệ với trường lưu trữ và accessor, nhưng là một loại metadata riêng. Muốn có trường theo tên phải dùng API trường; muốn đọc qua accessor phải gọi `getAccessor()`.

**Dùng getType để kết luận mọi thông tin generic đã biến mất.** `getType()` cho token sau xóa kiểu; `getGenericType()` có thể giữ biến kiểu và cấu trúc tham số hóa của khai báo.

**Ép mọi Type sang ParameterizedType.** Thành phần `int`, `String` hoặc `T` không có cùng dạng `Type` với `List<String>`.

**Cho rằng có `Parcel<Integer>` thì metadata weight phải là `Integer`.** Metadata thành phần thuộc lớp khai báo generic; muốn thay `T` bằng đối số cụ thể cần ngữ cảnh phân giải bổ sung.

**Tự tạo tên getter getX theo JavaBeans.** Accessor record dùng tên thành phần. Lấy trực tiếp `getAccessor()` tránh phụ thuộc quy tắc đặt tên sai.

**Quên rằng invoke thực thi mã.** Accessor tường minh có thể sao chép dữ liệu, kiểm tra điều kiện hoặc ném ngoại lệ.

**Dùng null như thể record không có thành phần.** `getRecordComponents() == null` nghĩa là lớp không phải record; record không có thành phần cho mảng rỗng.

**Dựa vào thứ tự các field hoặc method.** Cần thứ tự thành phần thì lấy chính danh sách `RecordComponent`.

## 10. Tự kiểm tra

1. Với `record Position(int x, int y)`, accessor của `x` tên là `x` hay `getX`?
2. Trong ví dụ `Parcel<T extends Number>`, vì sao `weight.getType()` là `Number.class` còn `weight.getGenericType()` biểu diễn `T`?
3. `getGenericType()` của `List<String> tags` có thể ép sang `Class<?>` để lấy `List<String>.class` không?
4. Kết quả `getRecordComponents()` của một lớp thường khác record không có thành phần như thế nào?
5. Vì sao giá trị đọc qua accessor của `int priority` lại là một `Integer` khi được chứa trong `Object`?
6. Nếu accessor tự sao chép một mảng, Reflection gọi accessor có tự bỏ qua logic sao chép đó không?
7. `RecordComponent` có tự cho biết giá trị của thành phần nếu chưa có một đối tượng record không?
8. Vì sao hàm `readComponents` dùng `LinkedHashMap` thay vì dựa vào thứ tự lặp tùy ý của một map khác?

Đáp án:

1. Tên là `x`, và lời gọi trong mã Java là `position.x()`.
2. `getType()` dùng kiểu sau xóa kiểu; biến `T` có cận `Number`. `getGenericType()` mô tả biến kiểu của khai báo.
3. Không. Đây là `ParameterizedType`; không có class literal cho một kiểu tham số hóa như vậy.
4. Lớp thường cho `null`; record không có thành phần cho mảng rỗng.
5. `Method.invoke` trả về `Object`, nên kết quả nguyên thủy được đóng hộp.
6. Không. Reflection thực thi chính phương thức accessor đó.
7. Không. Nó mô tả khai báo; muốn đọc giá trị qua accessor phải cung cấp một instance phù hợp.
8. Để giữ đúng thứ tự thành phần được `getRecordComponents()` cung cấp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-types-003">Thứ tự component và canonical constructor</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự thành phần record và constructor chuẩn

Khi tạo record bằng Reflection, tìm được đúng các kiểu tham số mới giải quyết một nửa bài toán. Bạn còn phải đặt **đúng giá trị vào đúng vị trí**. Nếu hai thành phần cùng kiểu, việc đảo chúng có thể tạo một đối tượng hợp lệ về kiểu nhưng sai ý nghĩa.

Constructor chuẩn (*canonical constructor*) là constructor gắn với toàn bộ các thành phần được khai báo trong header của record. Thứ tự thành phần xác định thứ tự tham số của constructor này. Nhờ quan hệ đó, một công cụ ánh xạ dữ liệu có thể tìm constructor và sắp xếp đối số một cách có căn cứ.

Bài này dùng Java 17 trở lên và tập trung vào mối liên hệ giữa metadata thành phần, chữ ký constructor và dữ liệu dùng để tạo đối tượng.

## 1. Header của record xác định điều gì?

Với khai báo:

```java
record Trip(String origin, String destination, int transfers) {
}
```

Constructor chuẩn nhận ba tham số theo thứ tự:

| Vị trí | Thành phần | Kiểu tham số | Ý nghĩa giá trị |
| --- | --- | --- | --- |
| `0` | `origin` | `String` | Điểm xuất phát |
| `1` | `destination` | `String` | Điểm đến |
| `2` | `transfers` | `int` | Số lần chuyển chặng |

Quan hệ tên, kiểu và thứ tự này thuộc quy tắc [constructor chuẩn của record](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.10.4.1). Nếu không khai báo constructor chuẩn tường minh, trình biên dịch cung cấp nó.

Record có thể có constructor khác để phục vụ cách tạo đối tượng tiện lợi hơn. Vì vậy, không chọn `getDeclaredConstructors()[0]` hoặc chỉ chọn constructor có số tham số trông phù hợp. Cần tìm **đúng dãy kiểu tham số theo thứ tự header**.

Đặc biệt, hai vị trí `origin` và `destination` đều là `String`. Reflection không biết chuỗi nào là điểm đi hay điểm đến chỉ bằng kiểu dữ liệu. Trách nhiệm gắn đúng giá trị với tên thành phần thuộc về mã ánh xạ.

## 2. Tìm constructor chuẩn từ metadata

`Class.getRecordComponents()` giữ thứ tự khai báo thành phần. Lấy `getType()` của từng thành phần theo thứ tự đó để tạo `Class<?>[]`, rồi truyền dãy kiểu vào `getDeclaredConstructor(...)`.

Quy trình:

1. Xác minh kiểu là record; kết quả thành phần khác `null`.
2. Giữ nguyên thứ tự mảng `RecordComponent[]`.
3. Chuyển từng thành phần thành `Class<?>` bằng `getType()`.
4. Tra cứu constructor có chính xác dãy kiểu đó.

[API `Class.getRecordComponents()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getRecordComponents()) mô tả cả quy tắc thứ tự và cách áp dụng để tìm constructor chuẩn.

Không sắp xếp thành phần theo tên trước bước tra cứu. Một thứ tự đẹp cho việc hiển thị không nhất thiết là thứ tự tham số hợp lệ.

`getDeclaredConstructor` tra cứu theo kiểu tham số, không tự chọn overload từ các giá trị thực tế như một lời gọi `new` được viết trong mã nguồn.

## 3. Thí nghiệm Java 17+ chạy độc lập

Chương trình có các trường hợp: record có constructor chuẩn rút gọn, constructor phụ, dữ liệu dạng bản đồ, tham số nguyên thủy, record rỗng, record generic và thành phần varargs.

Lưu toàn bộ vào **`RecordConstructorDemo.java`**:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class RecordConstructorDemo {
    public record Trip(String origin, String destination, int transfers) {
        public Trip {
            origin = Objects.requireNonNull(origin, "origin").strip();
            destination = Objects.requireNonNull(destination, "destination").strip();
            if (transfers < 0) {
                throw new IllegalArgumentException("transfers must be nonnegative");
            }
        }

        public Trip(String origin, String destination) {
            this(origin, destination, 0);
        }
    }

    public record Empty() {
    }

    public record Bundle<T>(T content) {
    }

    public record Labels(String... values) {
    }

    public static <R extends Record> Constructor<R> canonicalConstructor(
            Class<R> recordType) throws NoSuchMethodException {
        Objects.requireNonNull(recordType, "recordType");
        RecordComponent[] components = recordType.getRecordComponents();
        if (components == null) {
            throw new IllegalArgumentException("Expected a record declaration");
        }
        Class<?>[] parameterTypes = Arrays.stream(components)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
        return recordType.getDeclaredConstructor(parameterTypes);
    }

    public static <R extends Record> R constructFromMap(
            Class<R> recordType, Map<String, ?> values)
            throws ReflectiveOperationException {
        Objects.requireNonNull(values, "values");
        Constructor<R> constructor = canonicalConstructor(recordType);
        RecordComponent[] components = recordType.getRecordComponents();
        Object[] arguments = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            String name = components[i].getName();
            if (!values.containsKey(name)) {
                throw new IllegalArgumentException("Missing component: " + name);
            }
            arguments[i] = values.get(name);
        }
        if (values.size() != components.length) {
            throw new IllegalArgumentException("Unexpected component keys");
        }
        return constructor.newInstance(arguments);
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws ReflectiveOperationException;
    }

    private static void attempt(String label, CheckedAction action) {
        try {
            action.run();
            System.out.println(label + " -> success");
        } catch (InvocationTargetException ex) {
            System.out.println(label + " -> InvocationTargetException, cause="
                    + ex.getCause().getClass().getSimpleName());
        } catch (ReflectiveOperationException | IllegalArgumentException ex) {
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Constructor<Trip> constructor = canonicalConstructor(Trip.class);
        System.out.println("Components = "
                + Arrays.stream(Trip.class.getRecordComponents())
                        .map(RecordComponent::getName).toList());
        System.out.println("Canonical parameters = "
                + Arrays.stream(constructor.getParameterTypes())
                        .map(Class::getTypeName).toList());

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("destination", "Hue");
        input.put("transfers", 1);
        input.put("origin", " Hanoi ");
        System.out.println("Input key order = " + input.keySet());
        System.out.println("From map = " + constructFromMap(Trip.class, input));
        System.out.println("Same-type swap = "
                + constructor.newInstance("Hue", "Hanoi", 1));

        attempt("wrapper lookup", () -> Trip.class.getDeclaredConstructor(
                String.class, String.class, Integer.class));
        attempt("missing no-arg", () -> Trip.class.getDeclaredConstructor());
        attempt("null primitive", () -> constructor.newInstance("Hanoi", "Hue", null));
        attempt("validation failure", () -> constructor.newInstance("Hanoi", "Hue", -1));
        attempt("missing key", () -> constructFromMap(
                Trip.class, Map.of("origin", "Hanoi")));

        Constructor<Empty> emptyConstructor = canonicalConstructor(Empty.class);
        System.out.println("Empty parameter count = "
                + emptyConstructor.getParameterCount());
        System.out.println("Empty instance = " + emptyConstructor.newInstance());

        Constructor<?> bundleConstructor = canonicalConstructor(Bundle.class);
        System.out.println("Bundle parameters = "
                + Arrays.stream(bundleConstructor.getParameterTypes())
                        .map(Class::getTypeName).toList());
        System.out.println("Bundle instance = " + bundleConstructor.newInstance("book"));

        Constructor<Labels> labelsConstructor = canonicalConstructor(Labels.class);
        Labels labels = labelsConstructor.newInstance(
                (Object) new String[]{"x", "y"});
        System.out.println("Labels parameter = "
                + labelsConstructor.getParameterTypes()[0].getTypeName());
        System.out.println("Labels constructor isVarArgs = "
                + labelsConstructor.isVarArgs());
        System.out.println("Labels values = " + Arrays.toString(labels.values()));

        attempt("base Record", () -> canonicalConstructor(Record.class));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 -Xlint:all -Werror RecordConstructorDemo.java
java RecordConstructorDemo
```

Kết quả:

```text
Components = [origin, destination, transfers]
Canonical parameters = [java.lang.String, java.lang.String, int]
Input key order = [destination, transfers, origin]
From map = Trip[origin=Hanoi, destination=Hue, transfers=1]
Same-type swap = Trip[origin=Hue, destination=Hanoi, transfers=1]
wrapper lookup -> NoSuchMethodException
missing no-arg -> NoSuchMethodException
null primitive -> IllegalArgumentException
validation failure -> InvocationTargetException, cause=IllegalArgumentException
missing key -> IllegalArgumentException
Empty parameter count = 0
Empty instance = Empty[]
Bundle parameters = [java.lang.Object]
Bundle instance = Bundle[content=book]
Labels parameter = java.lang.String[]
Labels constructor isVarArgs = true
Labels values = [x, y]
base Record -> IllegalArgumentException
```

`attempt()` là hàm hỗ trợ thí nghiệm: in loại lỗi để chương trình tiếp tục quan sát các trường hợp khác. Nó không phải hợp đồng xử lý lỗi dành cho một thư viện ánh xạ dữ liệu hoàn chỉnh.

## 4. Hai dãy cần đồng bộ: kiểu tham số và giá trị đối số

Hàm `canonicalConstructor` tạo dãy kiểu `[String.class, String.class, int.class]` từ metadata của `Trip`. Đó là cơ sở tìm đúng constructor.

Hàm `constructFromMap` tạo một dãy khác: **các giá trị đối số**. Dãy này cũng phải đi theo thứ tự thành phần, không theo thứ tự duyệt khóa của bản đồ.

Trong thí nghiệm, dữ liệu được thêm theo thứ tự:

```text
destination → transfers → origin
```

Nhưng hàm ánh xạ lần lượt hỏi giá trị của `origin`, `destination`, `transfers`, nên constructor nhận:

```text
" Hanoi " → "Hue" → 1
```

Constructor chuẩn rút gọn bỏ khoảng trắng hai đầu các chuỗi, nên kết quả cuối là `Trip[origin=Hanoi, destination=Hue, transfers=1]`.

### Vì sao đổi chỗ hai giá trị cùng kiểu không phát sinh lỗi?

Lời gọi `constructor.newInstance("Hue", "Hanoi", 1)` vẫn khớp chữ ký `(String, String, int)`. Nó tạo hành trình ngược lại một cách hợp lệ về kiểu.

Đây là lỗi ngữ nghĩa mà kiểm tra kiểu của Reflection không thể tự phát hiện. Nếu lấy dữ liệu theo tên thành phần, hãy dùng chính tên đó làm khóa rồi đặt giá trị vào đúng chỉ số của thành phần.

Đừng xây đối số bằng `values.values().toArray()` và hy vọng thứ tự bản đồ luôn khớp header. Ngay cả bản đồ giữ thứ tự thêm khóa cũng có thể được tạo theo thứ tự khác, như dữ liệu trong ví dụ.

### Phân biệt thiếu khóa với giá trị `null`

Mã mẫu dùng `containsKey(name)` trước `get(name)`. Cách này phân biệt không có dữ liệu cho thành phần với việc bên gọi chủ động truyền `null`.

Hàm mẫu yêu cầu đủ tên thành phần và từ chối khóa dư. Đây là chính sách ánh xạ của hàm mẫu, không phải hành vi tự động của Reflection. Sau khi thu thập đối số, giá trị `null` có được chấp nhận hay không còn phụ thuộc kiểu tham số và kiểm tra trong constructor.

## 5. Constructor rút gọn vẫn có đủ tham số

Khai báo `public Trip { ... }` trong chương trình là **constructor chuẩn rút gọn (compact canonical constructor)**. Việc bỏ danh sách tham số trong cú pháp nguồn không biến nó thành constructor không tham số.

Reflection vẫn nhìn thấy chữ ký `(String, String, int)`. Phần thân rút gọn sử dụng các biến tham số tương ứng header để kiểm tra hoặc chuẩn hóa dữ liệu; việc gán các trường thành phần được hoàn tất theo quy tắc của record. Xem [constructor chuẩn rút gọn trong JLS](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.10.4.2).

Trong thí nghiệm:

- `origin` và `destination` được chuẩn hóa bằng `strip()`.
- `transfers` âm bị từ chối.
- Constructor phụ nhận hai chuỗi gọi `this(origin, destination, 0)`.

Dù constructor chuẩn được trình biên dịch cung cấp, viết đầy đủ hay viết rút gọn, cách tìm dựa trên metadata thành phần vẫn áp dụng. Không cần đoán kiểu cú pháp nào đã được tác giả dùng trong mã nguồn.

Record `Trip` không có constructor không tham số. Vì vậy, `Trip.class.getDeclaredConstructor()` ném `NoSuchMethodException`, dù nó có constructor phụ khác.

## 6. Tra cứu chữ ký khác với chuyển đổi đối số khi gọi

Đây là hai bước có các quy tắc khác nhau.

### Khi tra cứu

`int.class` và `Integer.class` là hai kiểu khác nhau. Vì thành phần `transfers` có kiểu `int`, tra cứu với `Integer.class` không tìm thấy constructor chuẩn và ném `NoSuchMethodException`.

Do đó, không dựng chữ ký bằng cách gọi `argument.getClass()` cho từng giá trị: giá trị `1` bên trong `Object[]` là `Integer`, còn constructor thực sự nhận `int`. Giá trị `null` thậm chí không có `getClass()` để dùng.

Hãy dựng chữ ký từ `RecordComponent.getType()`, vì nó mô tả **kiểu khai báo**, không phải lớp thực tế của đối số hiện tại.

### Khi gọi

`Constructor.newInstance(Object... arguments)` nhận các giá trị dạng đối tượng. Với tham số `int`, đối tượng `Integer` có thể được mở gói để truyền vào constructor. API cũng có các quy tắc chuyển đổi nguyên thủy được cho phép, nhưng không tự phân tích chuỗi số thành số hay tùy ý thu hẹp kiểu. Xem [hợp đồng `Constructor.newInstance`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Constructor.html#newInstance(java.lang.Object...)).

Trong thí nghiệm, truyền `null` cho vị trí `int` gây `IllegalArgumentException` trước khi phần thân constructor được thực hiện. Truyền `"1"` thay vì một giá trị số cũng không phải một bước chuyển đổi cấu hình được Reflection tự làm.

Một thư viện ánh xạ thực tế cần có bước chuyển đổi dữ liệu rõ ràng trước khi gọi constructor; thuật toán trong bài chỉ xử lý việc gắn tên với vị trí và gọi constructor.

## 7. Lỗi trước lời gọi và lỗi từ bên trong constructor

Hai trường hợp trong kết quả cần được phân biệt:

| Thí nghiệm | Kết quả | Constructor đã chạy phần kiểm tra dữ liệu chưa? |
| --- | --- | --- |
| Truyền `null` cho `transfers` kiểu `int` | `IllegalArgumentException` | Chưa vào phần thân |
| Truyền `transfers = -1` | `InvocationTargetException`, nguyên nhân là `IllegalArgumentException` | Đã vào và chủ động từ chối |

Ở trường hợp thứ hai, `InvocationTargetException` là lớp bọc của lời gọi Reflection. `getCause()` cho ngoại lệ mà constructor thực sự ném ra.

Công cụ gọi constructor nên giữ thông tin này khi báo lỗi. Một thông báo “sai kiểu tham số” không giải thích đúng trường hợp dữ liệu đã vào constructor nhưng vi phạm bất biến nghiệp vụ.

Cũng cần tách lỗi không tìm thấy constructor, lỗi không có quyền gọi và lỗi kiểm tra dữ liệu. Không dùng một thông báo chung khiến người dùng không biết cần sửa cấu trúc, quyền truy cập hay giá trị đầu vào.

## 8. Các trường hợp đặc biệt

### Record rỗng

`Empty` có header `()`. Metadata thành phần có độ dài `0`, nên dãy kiểu tham số cũng rỗng. Constructor chuẩn chính là constructor không tham số và có thể gọi bằng `newInstance()`.

Đây là kết quả từ cấu trúc record, không phải quy tắc rằng mọi record đều có constructor không tham số.

### Record generic

`Bundle<T>(T content)` có `T` không giới hạn cụ thể hơn, nên kiểu tham số dùng khi tra cứu là `Object.class`. Kết quả in `[java.lang.Object]` xác nhận điều đó.

`RecordComponent.getGenericType()` có ích để phân tích khai báo generic, nhưng `getDeclaredConstructor(...)` cần `Class<?>[]`. Vì vậy, thuật toán dùng [kiểu thành phần từ `getType()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/RecordComponent.html#getType()). Không thay dãy kiểu khai báo bằng lớp của giá trị `"book"`, tức `String.class`.

### Thành phần varargs

`Labels(String... values)` có một thành phần với kiểu `String[]`. Constructor chuẩn có **một tham số mảng**, và `isVarArgs()` cho `true`.

Cần phân biệt mảng của constructor đích với cơ chế varargs của chính `Constructor.newInstance(Object...)`. Trong ví dụ:

```java
Labels labels = labelsConstructor.newInstance(
        (Object) new String[]{"x", "y"});
```

Đây là đoạn trích từ chương trình đầy đủ. Phép ép `(Object)` làm cho toàn bộ `String[]` trở thành **một đối số**. Một cách biểu diễn tương đương là truyền mảng đối số bên ngoài chứa đúng một phần tử mảng chuỗi.

Không truyền hai chuỗi rời rồi cho rằng Reflection sẽ tự đóng gói chúng vào `String[]` của constructor. Mã gọi phải chuẩn bị đúng số đối số và hình dạng mảng.

### Lớp cơ sở và quyền truy cập

Ràng buộc `<R extends Record>` không loại bỏ được chính `Record.class`, nên hàm vẫn kiểm tra metadata thành phần có khác `null` hay không. Thí nghiệm `base Record` bị từ chối trước bước tra cứu constructor.

`getDeclaredConstructor` tìm cả constructor không công khai, nhưng việc tìm thấy không tự cấp quyền gọi. Ví dụ dùng record lồng công khai để lời gọi hợp lệ trong bối cảnh của nó. Khi áp dụng với kiểu từ package hoặc module khác, hãy xử lý quyền truy cập theo hợp đồng của ứng dụng, không mặc định mở mọi constructor.

## 9. Bảng kiểm áp dụng

| Việc cần kiểm tra | Căn cứ đúng |
| --- | --- |
| Có phải record không? | `isRecord()` hoặc mảng thành phần khác `null` |
| Thứ tự tham số chuẩn là gì? | Thứ tự của `getRecordComponents()` |
| Kiểu dùng để tra cứu là gì? | `RecordComponent.getType()` |
| Constructor nào là chuẩn? | Constructor khớp toàn bộ dãy kiểu theo header |
| Giá trị nào vào vị trí nào? | Gắn theo tên thành phần rồi đặt vào chỉ số tương ứng |
| Thiếu giá trị hay chủ động truyền `null`? | Kiểm tra sự tồn tại của khóa riêng với giá trị |
| Lỗi đến từ đâu? | Tách lỗi tra cứu, gọi Reflection và nguyên nhân của `InvocationTargetException` |
| Có constructor rút gọn không? | Cú pháp rút gọn không thay chữ ký theo header |

### Tự kiểm tra

1. Vì sao đổi chỗ `origin` và `destination` không gây lỗi kiểu?
2. Vì sao `Integer.class` không thay được `int.class` ở bước tìm constructor?
3. Nếu bản đồ dữ liệu giữ thứ tự thêm khóa, tại sao vẫn cần sắp đối số theo metadata?
4. Tại sao constructor rút gọn của `Trip` không được tìm bằng dãy kiểu rỗng?
5. Làm sao phân biệt tham số `int` không nhận được `null` với constructor chủ động từ chối giá trị âm?
6. Vì sao `Labels` có một tham số constructor dù một đối tượng có thể chứa nhiều chuỗi?

Quan hệ cần giữ xuyên suốt là **thứ tự thành phần → thứ tự kiểu tham số → thứ tự giá trị đối số**. Khi ba dãy này đồng bộ, việc tạo record qua Reflection có thể được kiểm chứng và giải thích rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-types-004">Khởi tạo record qua constructor thay vì sửa field final</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo record qua constructor thay vì sửa field final

## 1. Record được hoàn thành ngay khi tạo

Một record mô tả dữ liệu bằng các **thành phần record (record component)** trong phần đầu khai báo. Mỗi thành phần tương ứng với một field instance `private final` và một phương thức truy cập.

Ví dụ:

~~~java
record Member(String name, int level) {
}
~~~

Đây là một khai báo record, chưa phải chương trình có `main`. Constructor chuẩn của nó nhận lần lượt `String name` và `int level`.

Một cách tạo đối tượng thường gặp với JavaBean là tạo trước rồi gán các thuộc tính. Cách suy nghĩ ấy không phù hợp với các component của record: dữ liệu phải được đưa qua constructor để đối tượng được khởi tạo đầy đủ.

Khi chỉ biết lớp record lúc chạy, Reflection vẫn thực hiện cùng mô hình:

1. Đọc các component theo thứ tự khai báo.
2. Xác định constructor chuẩn.
3. Chuẩn bị đầy đủ đối số.
4. Gọi constructor để nhận một record mới.

Đừng dùng ghi field final như bước “hoàn thiện” một record đã được tạo. Ngoài giới hạn của API Reflection, cách đó còn bỏ qua nơi tập trung các quy tắc kiểm tra và chuẩn hóa dữ liệu.

## 2. Constructor chuẩn và compact constructor

**Constructor chuẩn (canonical constructor)** có các kiểu tham số tương ứng với component, giữ nguyên thứ tự trong phần đầu record.

Với `record Member(String name, int level)`, danh sách kiểu là:

~~~text
String.class, int.class
~~~

Record có thể khai báo constructor chuẩn bằng dạng đầy đủ hoặc dạng rút gọn, gọi là **compact constructor**. Dạng rút gọn thích hợp cho kiểm tra và chuẩn hóa tham số:

~~~java
public record Member(String name, int level) {
    public Member {
        name = java.util.Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name is blank");
        }
        if (level < 1) {
            throw new IllegalArgumentException("level must be positive");
        }
    }
}
~~~

Đây là một khai báo minh họa độc lập với khai báo ngắn ở phần trước; không đặt cả hai cùng tên trong một phạm vi.

Trong phần thân compact constructor, `name` và `level` là các tham số ngầm định. Có thể gán lại tham số để chuẩn hóa. Sau khi phần thân kết thúc bình thường, mã do compiler cung cấp sẽ khởi tạo các field từ các tham số tương ứng.

Không viết `this.name = ...` trong compact constructor để tự gán component field. Nếu dùng dạng constructor chuẩn đầy đủ, quy tắc viết phần gán có khác; không trộn hai cách khai báo.

Reflection tìm thấy constructor chuẩn với cùng danh sách kiểu dù nó được khai báo dưới dạng compact. Gọi constructor này vẫn chạy toàn bộ kiểm tra và chuẩn hóa.

Record không tự được cấp một constructor mặc định không tham số theo quy tắc của lớp thông thường. Nếu có constructor khác do tác giả viết, nó phải đi qua chuỗi constructor hợp lệ của record. Vì thế, không giả định mọi record đều có thể được tạo bằng một lời gọi không đối số rồi điền dữ liệu sau.

## 3. Ví dụ chạy được: tìm và gọi constructor chuẩn

Lưu thành `RecordConstructorDemo.java`:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class RecordConstructorDemo {
    public record Member(String name, int level) {
        public Member {
            name = Objects.requireNonNull(name, "name").strip();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("name is blank");
            }
            if (level < 1) {
                throw new IllegalArgumentException("level must be positive");
            }
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<Member> type = Member.class;
        RecordComponent[] components = type.getRecordComponents();

        Class<?>[] parameterTypes = Arrays.stream(components)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);

        Constructor<Member> constructor =
                type.getDeclaredConstructor(parameterTypes);

        System.out.println("components="
                + Arrays.stream(components)
                      .map(RecordComponent::getName)
                      .collect(Collectors.joining(",")));
        System.out.println("signature="
                + Arrays.stream(parameterTypes)
                      .map(Class::getSimpleName)
                      .collect(Collectors.joining(",")));

        Member member = constructor.newInstance(" Lan ", 3);
        System.out.println("name=" + member.name());
        System.out.println("level=" + member.level());
        System.out.println("sameAsDirect="
                + member.equals(new Member("Lan", 3)));

        try {
            constructor.newInstance("   ", 3);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            System.out.println("constructorCause="
                    + cause.getClass().getSimpleName()
                    + ":" + cause.getMessage());
        }

        try {
            constructor.newInstance("Lan", "3");
        } catch (IllegalArgumentException exception) {
            System.out.println("arguments="
                    + exception.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 RecordConstructorDemo.java
java RecordConstructorDemo
~~~

Kết quả:

~~~text
components=name,level
signature=String,int
name=Lan
level=3
sameAsDirect=true
constructorCause=IllegalArgumentException:name is blank
arguments=IllegalArgumentException
~~~

Các điểm cần quan sát:

- `getRecordComponents()` trả component theo thứ tự phần đầu record. Không cần đoán từ thứ tự field.
- `getType()` cung cấp kiểu để tìm đúng constructor: component `int` cần `int.class`, không phải `Integer.class`.
- `newInstance(" Lan ", 3)` chạy compact constructor, nên tên nhận được là `Lan`.
- Chuỗi chỉ có khoảng trắng bị constructor từ chối. Lỗi từ phần thân constructor được bọc trong `InvocationTargetException`.
- Chuỗi `"3"` không tự chuyển thành số `int`. Đây là lỗi đối số Reflection, được báo bằng `IllegalArgumentException` trước khi có một lời gọi constructor hợp lệ.

Tìm constructor và truyền đối số là hai bước có quy tắc khác nhau. Tìm kiếm dùng các đối tượng `Class` của kiểu khai báo chính xác; lúc gọi, Reflection có thể mở hộp giá trị `Integer` để truyền cho tham số `int`, như số `3` trong ví dụ.

## 4. Tên component quyết định vị trí đối số khi đầu vào là Map

Constructor chuẩn gắn với thứ tự component trong header. Nếu có `record Address(String city, String street) {}`, đảo hai chuỗi vẫn đúng kiểu nhưng lại tạo địa chỉ sai nghĩa. Với dữ liệu đầu vào có tên thuộc tính, hãy chọn giá trị theo **tên component**, rồi đặt vào đúng chỉ số:

~~~java
// Mảnh mã trong một phương thức: type là Class<?> của record,
// source là Map<String, ?>; đã kiểm tra type.isRecord().
RecordComponent[] components = type.getRecordComponents();
Object[] arguments = new Object[components.length];
for (int i = 0; i < components.length; i++) {
    String name = components[i].getName();
    if (!source.containsKey(name)) {
        throw new IllegalArgumentException("Missing component: " + name);
    }
    arguments[i] = source.get(name);
}
~~~

Đoạn này chỉ chuẩn bị đối số. Phần tìm và gọi canonical constructor theo `RecordComponent.getType()` đã được minh họa ở phần 3; một mapper hoàn chỉnh còn phải kiểm tra khóa lạ và chuyển đổi giá trị cho đúng kiểu trước khi gọi.

Không lấy `source.values().toArray()` làm đối số: thứ tự duyệt Map không phải hợp đồng thứ tự của header record. `containsKey` cũng giúp phân biệt thiếu tên với một khóa có mặt nhưng mang giá trị `null`. Nếu component có kiểu primitive thì `null` không thể làm đối số hợp lệ; còn `null` ở kiểu tham chiếu có thể bị chính constructor từ chối.

## 5. Vì sao setAccessible(true) không giải quyết field final của record?

Field final được khai báo trong record thuộc nhóm **final không thể sửa bằng Field.set** theo hợp đồng Reflection. `setAccessible(true)` không biến chúng thành field ghi được.

Cờ accessible liên quan đến kiểm tra quyền truy cập Java. Quyền truy cập và khả năng sửa một field final của record là hai điều kiện khác nhau.

Lưu thành `RecordFinalFieldDemo.java`:

~~~java
import java.lang.reflect.Field;

public class RecordFinalFieldDemo {
    public record Note(String text) {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Note note = new Note("before");
        Field field = Note.class.getDeclaredField("text");

        field.setAccessible(true);
        System.out.println("read=" + field.get(note));

        try {
            field.set(note, "after");
        } catch (IllegalAccessException exception) {
            System.out.println("write="
                    + exception.getClass().getSimpleName());
        }

        System.out.println("value=" + note.text());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 RecordFinalFieldDemo.java
java RecordFinalFieldDemo
~~~

Kết quả trên JDK 17:

~~~text
read=before
write=IllegalAccessException
value=before
~~~

Ví dụ được đặt trong cùng lớp ngoài để việc bật truy cập không bị vướng ranh giới module của thư viện khác. Dù bật thành công và đọc được field, thao tác ghi vẫn bị từ chối.

Không diễn giải lỗi này là “cần gọi setAccessible thêm lần nữa”. Với record, hướng khởi tạo đúng là tìm và gọi constructor phù hợp. Nếu cần dữ liệu khác, tạo một record mới qua constructor để các quy tắc của record tiếp tục được áp dụng.

## 6. Cập nhật dữ liệu bằng cách tạo giá trị mới

Một record biểu diễn một tập dữ liệu đã được khởi tạo. Nếu muốn đổi một component, hãy tạo giá trị mới, giữ lại các component còn lại cần thiết.

Đoạn sau là một phương thức đặt trong phạm vi `RecordConstructorDemo`, sử dụng record `Member` của ví dụ đầu:

~~~java
static Member withLevel(Member current, int nextLevel) {
    return new Member(current.name(), nextLevel);
}
~~~

Lời gọi vẫn đi qua constructor, nên `nextLevel < 1` bị từ chối. Đối tượng `current` giữ giá trị cũ.

Nếu kiểu chỉ được biết lúc chạy, nguyên tắc vẫn như vậy: lấy dữ liệu hiện có bằng accessor phù hợp, thay giá trị đầu vào cần đổi, sắp theo component và gọi constructor chuẩn để tạo đối tượng mới. Không sửa trực tiếp field của đối tượng cũ.

Điểm này có ý nghĩa với các **điều kiện luôn phải đúng (invariant)**. Ví dụ record của một khoảng thời gian có thể yêu cầu thời điểm bắt đầu không sau thời điểm kết thúc. Đưa toàn bộ dữ liệu qua constructor cho phép kiểm tra quan hệ giữa các component trước khi trả về một đối tượng hợp lệ.

Constructor không tự tạo ra các quy tắc nghiệp vụ: tác giả record phải viết chúng nếu cần. Reflection chỉ bảo đảm lời gọi đi qua phần constructor được chọn, không tự biết dữ liệu nào có ý nghĩa trong ứng dụng.

## 7. Các chi tiết dễ gây lỗi khi gọi constructor

### 7.1. Tìm đúng kiểu khai báo

`getDeclaredConstructor` và `getConstructor` tìm theo danh sách kiểu tham số, không tìm “gần đúng”. Với component `int level`, tìm bằng `Integer.class` sẽ không tìm thấy constructor chuẩn ấy.

Khi gọi `Constructor.newInstance`, giá trị primitive được biểu diễn trong `Object[]` bằng đối tượng đóng hộp. Reflection hỗ trợ các chuyển đổi lời gọi phù hợp, nhưng không phải một bộ chuyển đổi tùy ý từ chuỗi hoặc mọi loại số.

Ví dụ `Integer` có thể được mở hộp cho `int`; `null` không thể cung cấp giá trị cho tham số `int`. Không dựa vào Reflection để tự phân tích `"42"` thành số.

### 7.2. Một component kiểu mảng vẫn là một đối số

`Constructor.newInstance` nhận `Object...`. Nếu record có một component `String[]`, cần tránh vô tình truyền các phần tử của mảng thành nhiều đối số.

Ví dụ minh họa trong một phương thức có constructor phù hợp:

~~~java
Object[] arguments = {new String[] {"A", "B"}};
Object record = constructor.newInstance(arguments);
~~~

Biến `constructor` ở đây là `Constructor<?>` của record có đúng một component `String[]`. Mảng ngoài có một ô, nên lời gọi nhận đúng một đối số là mảng chuỗi.

Cách xây `Object[] arguments` theo component ở ví dụ ánh xạ Map cũng giữ đúng cấu trúc này. Với component varargs trong phần đầu record, constructor chuẩn vẫn có tham số kiểu mảng tương ứng; chuẩn bị một giá trị mảng ở vị trí đối số ấy.

### 7.3. Tìm thấy constructor chưa đồng nghĩa với được phép gọi

`getDeclaredConstructor` có thể tìm constructor không public, nhưng lời gọi vẫn chịu kiểm tra truy cập. Record và constructor trong ví dụ đầu đều public, nên không cần tắt kiểm tra truy cập.

Với kiểu không public hoặc ranh giới module khác, cần thiết kế đường truy cập hợp lệ của thư viện hoặc dùng API tạo đối tượng được cung cấp. Không lấy việc nhìn thấy metadata làm bằng chứng rằng mọi nơi gọi đều có quyền khởi tạo.

### 7.4. Final của component không phải bất biến sâu

Nếu một component giữ tham chiếu đến một collection có thể thay đổi, `final` không tự làm collection ấy bất biến. Constructor có thể tạo bản sao phù hợp nếu hợp đồng record cần giữ dữ liệu ổn định.

Ví dụ khai báo, với import `java.util.List`:

~~~java
record Team(List<String> members) {
    Team {
        members = List.copyOf(members);
    }
}
~~~

Bước sao chép được thực hiện khi đi qua constructor, kể cả khi gọi bằng Reflection. Đây là một lý do nữa để đặt việc chuẩn hóa dữ liệu tại đường khởi tạo, thay vì tìm cách gán field sau đó. `List.copyOf` không phải phép sao chép sâu mọi đối tượng có thể nằm trong một cấu trúc dữ liệu.

## 8. Phân biệt các nhóm lỗi

| Lỗi | Ý nghĩa thường gặp trong luồng này |
| --- | --- |
| `NoSuchMethodException` | Danh sách kiểu không khớp constructor, hoặc tìm public constructor nhưng không có constructor public tương ứng |
| `IllegalAccessException` khi gọi constructor | Không được phép truy cập constructor từ nơi gọi |
| `IllegalArgumentException` từ Reflection | Sai số đối số hoặc giá trị không chuyển được sang kiểu tham số |
| `InvocationTargetException` | Constructor đã được gọi và phần thân ném ngoại lệ; xem `getCause()` |
| `IllegalAccessException` từ `Field.set` trên component final | Đang dùng đường ghi field không được hỗ trợ cho record |

Nếu constructor từ chối dữ liệu, không nên bỏ qua lỗi rồi cố tạo đối tượng bằng ghi field. Điều đó làm mất chính điều kiện mà record đang bảo vệ.

Khi báo lỗi trong một bộ ánh xạ dữ liệu, nên giữ được tên record, tên component liên quan khi xác định được và nguyên nhân gốc từ constructor. Không thay mọi lỗi bằng một thông báo chung khiến người dùng không biết đang sai kiểu hay sai quy tắc dữ liệu.

## 9. Tự kiểm tra

1. Vì sao dùng `getRecordComponents()` để xây danh sách tham số constructor chuẩn thay vì dùng thứ tự `getDeclaredFields()`?
2. Với component `int`, tìm constructor bằng `Integer.class` có đúng không?
3. Nếu compact constructor loại bỏ khoảng trắng trong tên, lời gọi bằng Reflection có thực hiện bước đó không?
4. `InvocationTargetException` khác gì với `IllegalArgumentException` do sai kiểu đối số?
5. Bật `setAccessible(true)` thành công có cho phép sửa field final của record không?
6. Muốn một component có giá trị mới, nên sửa đối tượng cũ hay tạo đối tượng mới?

Đáp án:

1. Component có hợp đồng thứ tự theo phần đầu record; thứ tự field Reflection không phải nguồn để suy ra thứ tự constructor.
2. Không; tìm theo `int.class`. Việc mở hộp giá trị lúc gọi là quy tắc khác.
3. Có; `Constructor.newInstance` thực sự gọi constructor.
4. Ngoại lệ thứ nhất bọc lỗi từ phần thân constructor; lỗi đối số có thể ngăn lời gọi hợp lệ diễn ra ngay từ đầu.
5. Không; component field final của record không trở thành field ghi được qua `Field.set`.
6. Tạo đối tượng mới qua constructor, giữ lại các component khác và áp dụng lại kiểm tra cần thiết.

## Tài liệu đối chiếu

- [JLS 17 — Constructor của record và compact constructor](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.10.4).
- [Java 17 — Class.getRecordComponents và tìm constructor chuẩn](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getRecordComponents()).
- [Java 17 — Constructor.newInstance](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html#newInstance(java.lang.Object...)).
- [Java 17 — AccessibleObject và final field không thể sửa](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html#setAccessible(boolean)).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-types-005">Component annotation khác annotation trên field, accessor hoặc parameter</a>

<details>
<summary>Xem chi tiết</summary>

# Annotation của record component khác annotation trên field, accessor và parameter

## 1. Một dòng khai báo, nhiều vị trí metadata

Xét một record có thành phần `name`:

~~~java
record Person(String name) {
}
~~~

Từ một thành phần trong phần đầu record, ta có những đối tượng khai báo liên quan:

~~~text
record component name
    -> trường lưu trữ private final name
    -> phương thức accessor name()
    -> tham số name của canonical constructor
~~~

Các đối tượng này có quan hệ với nhau, nhưng **không phải cùng một phần tử được gắn annotation**. Reflection cung cấp một đối tượng riêng cho mỗi vị trí:

- `RecordComponent`: thành phần record.
- `Field`: trường lưu trữ.
- `Method`: accessor.
- `Parameter`: tham số của constructor.

Vì thế, không thể thấy `@SomeAnnotation` gần một thành phần trong mã nguồn rồi kết luận rằng bốn truy vấn `getAnnotation(...)` đều phải có cùng kết quả.

Muốn hiểu kết quả, phải biết **annotation được phép áp dụng ở đâu**, **thành viên hoặc tham số là ngầm định hay được viết tường minh**, và **annotation có được giữ lại cho Reflection lúc chạy hay không**.

## 2. Target và Retention trả lời hai câu hỏi khác nhau

### 2.1. Target xác định vị trí áp dụng

`@Target` trên khai báo annotation xác định các ngữ cảnh hợp lệ.

| `ElementType` | Vị trí tương ứng trong bài |
| --- | --- |
| `RECORD_COMPONENT` | Khai báo thành phần record |
| `FIELD` | Khai báo trường lưu trữ |
| `METHOD` | Khai báo phương thức accessor |
| `PARAMETER` | Khai báo tham số của constructor |
| `TYPE_USE` | Một vị trí sử dụng kiểu, ví dụ kiểu `String` của thành phần |

Các hằng này được mô tả tại [ElementType](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/annotation/ElementType.html).

Một annotation có thể cho phép nhiều target. Ví dụ, annotation có cả `RECORD_COMPONENT` lẫn `FIELD` có thể xuất hiện trên cả metadata thành phần lẫn trường tương ứng khi áp dụng qua phần đầu record.

Annotation chỉ có target `FIELD` vẫn có thể được viết tại vị trí thành phần trong phần đầu record và được truyền sang trường tương ứng. Điều đó **không** làm nó xuất hiện như annotation khai báo của `RecordComponent`. Chương trình ở phần sau sẽ kiểm chứng trường hợp này.

### 2.2. Retention xác định có quan sát được lúc chạy hay không

| Chính sách | Ảnh hưởng đối với Reflection lúc chạy |
| --- | --- |
| `SOURCE` | Annotation bị bỏ khi biên dịch; không quan sát được bằng Reflection thông thường |
| `CLASS` | Có trong class file nhưng không được giữ lại để các truy vấn annotation Reflection thông thường trả về |
| `RUNTIME` | Có thể được đọc qua Reflection ở vị trí phù hợp |

Nếu không khai báo `@Retention`, mặc định là `CLASS`.

Do đó, `getAnnotation(...)` trả về `null` có thể do đang hỏi sai vị trí, annotation không có ở vị trí đó, hoặc chính sách lưu giữ không cho quan sát lúc chạy. Không nên chỉ sửa `@Target` mà bỏ qua `@Retention`.

## 3. Chọn đúng đối tượng Reflection

Với một record có thành phần `name` và canonical constructor nhận `String name`, các điểm truy vấn tương ứng là:

| Cần hỏi annotation tại đâu? | Đối tượng cần đọc |
| --- | --- |
| Thành phần record | `recordType.getRecordComponents()[index]` |
| Trường lưu trữ | `recordType.getDeclaredField(component.getName())` |
| Accessor | `component.getAccessor()` |
| Tham số canonical constructor | `canonical.getParameters()[index]` |
| Kiểu của thành phần | `component.getAnnotatedType()` |
| Kiểu trả về của accessor | `accessor.getAnnotatedReturnType()` |

Mỗi đối tượng khai báo có thể được hỏi bằng `getAnnotation(...)`, `isAnnotationPresent(...)` hoặc các API đọc annotation tương ứng.

Đừng nhầm annotation của **tham số** với annotation của **constructor**. `parameter.getAnnotation(A.class)` hỏi một vị trí khác với `constructor.getAnnotation(A.class)`. Target `PARAMETER` không đồng nghĩa target `CONSTRUCTOR`.

Cũng không cần mở quyền truy cập để chỉ đọc annotation của một trường private. Đọc metadata bằng `Field.getAnnotation(...)` khác với đọc hoặc ghi giá trị trường bằng `Field.get(...)` hay `Field.set(...)`.

## 4. Chương trình đầy đủ: đối chiếu bốn vị trí

Chương trình sau khai báo các annotation chỉ dành cho từng vị trí, một annotation dùng chung bốn vị trí và một annotation chỉ dành cho kiểu.

Nó cũng so sánh:

- Record có accessor và canonical constructor ngầm định.
- Record viết tường minh accessor và canonical constructor có danh sách tham số.
- Record có compact constructor.
- Record đặt các giá trị annotation khác nhau tại từng vị trí.

Lưu vào `RecordAnnotationLocationsDemo.java`:

~~~java
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

public class RecordAnnotationLocationsDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.RECORD_COMPONENT)
    public @interface OnComponent {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface OnField {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface OnMethod {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface OnParameter {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.RECORD_COMPONENT, ElementType.FIELD,
             ElementType.METHOD, ElementType.PARAMETER})
    public @interface Everywhere {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface TypeMark {
    }

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.RECORD_COMPONENT)
    public @interface ClassOnly {
    }

    public record Automatic(
            @OnComponent @OnField @OnMethod @OnParameter
            @Everywhere("header") @TypeMark @ClassOnly String text) {
    }

    public record Manual(@Everywhere("header") @TypeMark String text) {
        public Manual(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    public record Compact(@Everywhere("header") @TypeMark String text) {
        public Compact {
            text = text.strip();
        }
    }

    public record ExplicitValues(@Everywhere("header") String text) {
        public ExplicitValues(@Everywhere("parameter") String text) {
            this.text = text;
        }

        @Everywhere("accessor")
        public String text() {
            return text;
        }
    }

    static String names(AnnotatedElement element) {
        return Arrays.stream(element.getDeclaredAnnotations())
                .map(Annotation::annotationType)
                .map(Class::getSimpleName)
                .sorted()
                .toList()
                .toString();
    }

    static void inspect(Class<?> type) throws NoSuchMethodException,
            NoSuchFieldException {
        RecordComponent component = type.getRecordComponents()[0];
        Field field = type.getDeclaredField(component.getName());
        Method accessor = component.getAccessor();
        Constructor<?> constructor =
                type.getDeclaredConstructor(component.getType());
        Parameter parameter = constructor.getParameters()[0];

        System.out.println("== " + type.getSimpleName() + " ==");
        System.out.println("component: " + names(component));
        System.out.println("field: " + names(field));
        System.out.println("accessor: " + names(accessor));
        System.out.println("parameter: " + names(parameter));
        System.out.println("component type: "
                + names(component.getAnnotatedType()));
        System.out.println("field type: "
                + names(field.getAnnotatedType()));
        System.out.println("accessor return type: "
                + names(accessor.getAnnotatedReturnType()));
        System.out.println("parameter type: "
                + names(parameter.getAnnotatedType()));
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        inspect(Automatic.class);
        inspect(Manual.class);
        inspect(Compact.class);

        RecordComponent component =
                Automatic.class.getRecordComponents()[0];
        System.out.println("ClassOnly visible at runtime: "
                + component.isAnnotationPresent(ClassOnly.class));

        Class<?> type = ExplicitValues.class;
        RecordComponent explicit = type.getRecordComponents()[0];
        System.out.println("explicit component value: "
                + explicit.getAnnotation(Everywhere.class).value());
        System.out.println("explicit field value: "
                + type.getDeclaredField("text")
                        .getAnnotation(Everywhere.class).value());
        System.out.println("explicit accessor value: "
                + explicit.getAccessor()
                        .getAnnotation(Everywhere.class).value());
        System.out.println("explicit parameter value: "
                + type.getDeclaredConstructor(String.class)
                        .getParameters()[0]
                        .getAnnotation(Everywhere.class).value());
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 RecordAnnotationLocationsDemo.java
java RecordAnnotationLocationsDemo
~~~

Kết quả chính xác:

~~~text
== Automatic ==
component: [Everywhere, OnComponent]
field: [Everywhere, OnField]
accessor: [Everywhere, OnMethod]
parameter: [Everywhere, OnParameter]
component type: [TypeMark]
field type: [TypeMark]
accessor return type: [TypeMark]
parameter type: [TypeMark]
== Manual ==
component: [Everywhere]
field: [Everywhere]
accessor: []
parameter: []
component type: [TypeMark]
field type: [TypeMark]
accessor return type: []
parameter type: []
== Compact ==
component: [Everywhere]
field: [Everywhere]
accessor: [Everywhere]
parameter: [Everywhere]
component type: [TypeMark]
field type: [TypeMark]
accessor return type: [TypeMark]
parameter type: [TypeMark]
ClassOnly visible at runtime: false
explicit component value: header
explicit field value: header
explicit accessor value: accessor
explicit parameter value: parameter
~~~

Hàm `names(...)` chủ động sắp xếp tên annotation để stdout ổn định. Không dựa vào thứ tự các annotation trong mảng Reflection.

Các record trong ví dụ đều có đúng một thành phần kiểu `String`. Vì vậy, mã khảo sát dùng vị trí `0` và chữ ký constructor một tham số. Với công cụ tổng quát, cần ánh xạ đúng các vị trí theo phần đầu record; phần 8 trình bày cách đó.

## 5. Đọc các kết quả quan trọng

### 5.1. Automatic: target quyết định nơi xuất hiện

Với record `Automatic`, kết quả khai báo là:

| Vị trí | Annotation quan sát được |
| --- | --- |
| Component | `Everywhere`, `OnComponent` |
| Field | `Everywhere`, `OnField` |
| Accessor | `Everywhere`, `OnMethod` |
| Parameter | `Everywhere`, `OnParameter` |

`OnField`, `OnMethod` và `OnParameter` không xuất hiện khi hỏi annotation khai báo trên component, dù đều được viết trong phần đầu record.

`Everywhere` có đủ bốn target nên xuất hiện ở cả bốn vị trí khi accessor và constructor tương ứng được tạo ngầm định. Đây là sự truyền annotation theo các ngữ cảnh áp dụng, không phải bốn đối tượng Reflection dùng chung một danh sách annotation.

Quy tắc về component và các thành viên liên quan nằm tại [JLS §8.10.1–8.10.3](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.10.1).

### 5.2. Manual: viết tường minh làm thay đổi nơi quan sát

Record `Manual` có annotation trong phần đầu:

~~~java
@Everywhere("header") @TypeMark String text
~~~

Nhưng accessor được viết tường minh mà không thêm annotation:

~~~java
public String text() {
    return text;
}
~~~

Và canonical constructor cũng có danh sách tham số viết tường minh:

~~~java
public Manual(String text) {
    this.text = text;
}
~~~

Các đoạn trên trích từ chương trình hoàn chỉnh, không phải các chương trình độc lập.

Kết quả:

- Component và trường vẫn có `Everywhere`.
- Accessor không tự nhận `Everywhere` từ component.
- Tham số viết tường minh không tự nhận `Everywhere` từ component.
- Annotation trên kiểu của component và trường vẫn có `TypeMark`.
- Kiểu trả về và kiểu tham số được viết tường minh không tự có `TypeMark`.

Vì vậy, việc refactor từ accessor ngầm định sang accessor viết tay có thể thay đổi metadata mà một công cụ đang đọc trên `Method`, ngay cả khi phương thức vẫn trả về cùng giá trị như trước.

Đừng kết luận metadata thành phần bị mất: `RecordComponent` vẫn có annotation của chính nó. Chỉ có những vị trí khác nhau đang cho kết quả khác nhau.

### 5.3. Compact: tham số vẫn được khai báo ngầm định

Record `Compact` viết:

~~~java
public Compact {
    text = text.strip();
}
~~~

Đây là compact canonical constructor. Người viết cung cấp phần thân, nhưng không viết lại danh sách tham số. Các tham số của nó vẫn được suy ra từ phần đầu record và được khai báo ngầm định.

Do đó, trong chương trình:

- Annotation `Everywhere` áp dụng cho tham số vẫn xuất hiện trên `Parameter`.
- Annotation `TypeMark` vẫn có trên kiểu tham số.
- Accessor không được viết tường minh nên vẫn nhận annotation phù hợp.

Không áp dụng máy móc quy tắc “có viết constructor thì không truyền annotation”. Cần phân biệt **canonical constructor thông thường có danh sách tham số tường minh** với **compact constructor có tham số ngầm định**. Quy tắc hai dạng constructor được mô tả ở [JLS §8.10.4](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.10.4).

### 5.4. ExplicitValues: cùng loại annotation nhưng giá trị có thể khác

Bốn dòng cuối của chương trình là:

~~~text
explicit component value: header
explicit field value: header
explicit accessor value: accessor
explicit parameter value: parameter
~~~

Chương trình hợp lệ. Annotation trên component có giá trị `header`, còn accessor và tham số canonical constructor được viết tường minh với các giá trị khác.

Vì thế, một công cụ không thể mặc định:

~~~text
Tìm thấy cùng một loại annotation
    -> mọi vị trí đều diễn đạt cùng một cấu hình
~~~

Nếu muốn kết hợp cấu hình từ nhiều nơi, công cụ phải định nghĩa rõ nơi ưu tiên và cách xử lý xung đột. Không nên nối tất cả annotation tìm được rồi xem chúng là các bản sao tương đương.

### 5.5. ClassOnly: target đúng nhưng Reflection vẫn không thấy

`ClassOnly` có target `RECORD_COMPONENT` nhưng retention là `CLASS`. Dòng kiểm tra cho:

~~~text
ClassOnly visible at runtime: false
~~~

Đây không phải bằng chứng rằng compiler không nhận annotation. Nó cho thấy annotation không được cung cấp qua API Reflection runtime ở chính sách lưu giữ đó.

Nếu cần khảo sát annotation có retention `CLASS`, đó là bài toán đọc class file bằng công cụ phù hợp, khác với việc chỉ gọi `RecordComponent.getAnnotation(...)`.

## 6. Annotation của khai báo khác annotation trên kiểu

`RecordComponent.getAnnotation(...)` trả lời về annotation **của khai báo thành phần**. Để hỏi annotation **trên vị trí sử dụng kiểu**, dùng `getAnnotatedType()`.

Trong record `Automatic`, `TypeMark` chỉ có target `TYPE_USE`. Vì vậy:

- Nó không xuất hiện trong danh sách annotation khai báo của component.
- Nó xuất hiện trên `component.getAnnotatedType()`.
- Với các phần tương ứng ngầm định, nó cũng xuất hiện trên kiểu trường, kiểu trả về accessor và kiểu tham số.

Có thể hình dung hai kênh riêng:

~~~text
RecordComponent
    -> getAnnotation(...)          : annotation của khai báo
    -> getAnnotatedType()
         -> getAnnotation(...)     : annotation trên kiểu
~~~

Một annotation có cả target `RECORD_COMPONENT` và `TYPE_USE` có thể được áp dụng theo cả hai vai trò. Khi đó hai truy vấn có thể cùng nhìn thấy một loại annotation, nhưng chúng vẫn đang đọc hai vị trí metadata khác nhau.

Đọc `getGenericType()` cũng không thay cho đọc `getAnnotatedType()`. `Type` mô tả cấu trúc kiểu; `AnnotatedType` cho phép khảo sát cấu trúc đó cùng các annotation trên vị trí sử dụng kiểu.

### Annotation lồng trong đối số generic

Với thành phần `List<@TypeMark String> values`, `TypeMark` được gắn với **lần sử dụng kiểu `String`**, không phải khai báo component hay kiểu `List` ngoài cùng. Vì thế, hai phép hỏi tại component và gốc `component.getAnnotatedType()` có thể không thấy annotation.

Mã minh họa sau giả sử đã có `RecordComponent component` của thành phần `values`, annotation `TypeMark` có `@Target(TYPE_USE)` và `@Retention(RUNTIME)`:

~~~java
java.lang.reflect.AnnotatedType root = component.getAnnotatedType();
if (root instanceof java.lang.reflect.AnnotatedParameterizedType listType) {
    boolean marked = listType.getAnnotatedActualTypeArguments()[0]
            .isAnnotationPresent(TypeMark.class);
    System.out.println(marked); // true với List<@TypeMark String>
}
~~~

Dùng `instanceof` vì không phải mọi component đều có kiểu generic. Với mảng, wildcard hoặc biến kiểu, cần chọn giao diện `AnnotatedType` tương ứng và đi tới đúng nút; không làm phẳng các annotation của những vị trí kiểu khác nhau thành một tập chung.

## 7. Một ma trận quy tắc để tự dự đoán

Giả sử annotation có retention `RUNTIME` và được viết ở thành phần trong phần đầu record:

| Target của annotation | Component | Trường lưu trữ | Accessor ngầm định | Tham số ngầm định của canonical constructor |
| --- | --- | --- | --- | --- |
| Chỉ `RECORD_COMPONENT` | Có | Không | Không | Không |
| Chỉ `FIELD` | Không | Có | Không | Không |
| Chỉ `METHOD` | Không | Không | Có | Không |
| Chỉ `PARAMETER` | Không | Không | Không | Có |
| Cả bốn target trên | Có | Có | Có | Có |
| Chỉ `TYPE_USE` | Không ở annotation khai báo | Đọc trên kiểu trường | Đọc trên kiểu trả về | Đọc trên kiểu tham số |

Với dòng `TYPE_USE`, còn phải đọc `component.getAnnotatedType()` để quan sát annotation trên kiểu thành phần. Vị trí có thể nằm sâu trong cấu trúc kiểu, như ví dụ `List<@ItemMark String>`.

Ma trận dành cho các phần ngầm định. Accessor viết tường minh và tham số của canonical constructor có danh sách tham số viết tường minh phải được xem theo khai báo của chính chúng.

Việc truyền annotation của record là quy tắc ngôn ngữ, không phải cơ chế kế thừa annotation qua `@Inherited`. Không dùng `@Inherited` như cách yêu cầu tự truyền annotation giữa component, field, accessor và parameter.

## 8. Ánh xạ đúng canonical constructor trong công cụ tổng quát

Chương trình đầu có một thành phần, nên dễ xác định tham số tương ứng. Với record có nhiều thành phần, nên bắt đầu từ thứ tự của `getRecordComponents()` và dựng chữ ký canonical constructor từ kiểu của các thành phần.

Đoạn sau là mã minh họa đặt trong một phương thức; `recordType` là token của lớp record đã biết. Cần các import Reflection tương ứng và `java.util.Arrays`; phương thức chứa đoạn này phải xử lý `NoSuchMethodException`.

~~~java
RecordComponent[] components = recordType.getRecordComponents();
if (components == null) {
    throw new IllegalArgumentException("Expected a record class");
}

Class<?>[] parameterTypes = Arrays.stream(components)
        .map(RecordComponent::getType)
        .toArray(Class<?>[]::new);

Constructor<?> canonical =
        recordType.getDeclaredConstructor(parameterTypes);
Parameter[] parameters = canonical.getParameters();

for (int i = 0; i < components.length; i++) {
    RecordComponent component = components[i];
    Parameter parameter = parameters[i];

    System.out.println(component.getName() + " -> "
            + parameter.getDeclaredAnnotations().length);
}
~~~

Các điểm cần giữ:

- Chọn canonical constructor bằng đúng danh sách kiểu và thứ tự thành phần.
- Không chọn constructor đầu tiên trong `getDeclaredConstructors()`; record có thể có constructor bổ sung.
- Dùng `getType()` để có `Class<?>` tìm chữ ký, không ép mọi `getGenericType()` thành `Class`.
- Ánh xạ tham số theo thứ tự thành phần, không đoán từ thứ tự field.
- Khi chỉ cần annotation của parameter, không cần tạo một đối tượng record hay gọi constructor.

Với canonical constructor viết tường minh, vẫn có quan hệ vị trí giữa thành phần và tham số, nhưng annotation của tham số có thể khác annotation trên component. Ánh xạ được hai vị trí không có nghĩa dữ liệu annotation của chúng giống nhau.

## 9. Cách thiết kế quy tắc đọc annotation

Một công cụ nên bắt đầu từ câu hỏi nghiệp vụ cụ thể:

- Nếu annotation mô tả thành phần trong lược đồ dữ liệu record, hãy xác định liệu nguồn cấu hình là `RecordComponent`.
- Nếu công cụ làm việc qua accessor, hãy quyết định cách xử lý annotation trên `Method` và kiểu trả về.
- Nếu công cụ chuẩn bị đối số để gọi constructor, annotation của `Parameter` là một nguồn thông tin riêng.
- Nếu annotation nói về kiểu dữ liệu hoặc phần tử trong cấu trúc generic, phải khảo sát `AnnotatedType`.

Đừng tự suy rằng annotation ở vị trí khác “chắc là cùng một annotation bị Reflection bỏ sót”. Hãy đọc từng nơi cần thiết và xác định chính sách rõ ràng.

Khi gặp nhiều vị trí có cấu hình khác nhau, công cụ có thể ưu tiên một vị trí, báo xung đột hoặc hỗ trợ một quy tắc kết hợp đã được định nghĩa. Lựa chọn đó thuộc hợp đồng của công cụ, không phải hành vi tự động của Reflection.

Nếu annotation là loại lặp được, nên dùng `getAnnotationsByType(...)` hoặc `getDeclaredAnnotationsByType(...)` theo nhu cầu để đọc các lần xuất hiện qua container. Đây vẫn là thao tác trên **một vị trí đã chọn**, không phải tự hợp nhất bốn vị trí của record.

## 10. Tự kiểm tra

1. Annotation chỉ có target `FIELD` được viết trước một thành phần record. Có thể đọc nó bằng `RecordComponent.getAnnotation(...)` không?
2. Annotation có target `RECORD_COMPONENT` và retention mặc định. Vì sao Reflection runtime có thể không thấy?
3. Khi viết tường minh accessor nhưng không chép annotation từ phần đầu record, annotation trên accessor có được tự truyền như accessor ngầm định không?
4. Compact constructor có tham số ngầm định hay danh sách tham số do người viết khai báo tường minh?
5. Annotation trên một tham số canonical constructor có đồng nghĩa annotation trên chính constructor không?
6. Với `List<@ItemMark String>`, vì sao hỏi annotation trên component và trên kiểu `List` ngoài cùng đều có thể không thấy?
7. Nếu component có `@Everywhere("header")` nhưng accessor viết tay có `@Everywhere("accessor")`, chương trình có nhất thiết lỗi biên dịch không?
8. Vì sao một công cụ không nên chọn constructor đầu tiên để đọc annotation tham số của record?

Đáp án:

1. Không với annotation chỉ có target đó. Hãy đọc trên `Field` tương ứng.
2. Retention mặc định là `CLASS`, không phải `RUNTIME`.
3. Không. Phải xem annotation của chính accessor được viết tường minh.
4. Tham số ngầm định, suy ra từ phần đầu record; annotation phù hợp được áp dụng theo các quy tắc cho tham số suy ra.
5. Không. Đây là hai vị trí khai báo khác nhau.
6. Annotation nằm trên đối số kiểu `String`; cần đi sâu qua `AnnotatedParameterizedType`.
7. Không. Chương trình mẫu cho thấy các giá trị ở những vị trí đó có thể khác nhau hợp lệ.
8. Record có thể có constructor bổ sung và mảng constructor không có thứ tự chọn mong muốn; cần tìm đúng chữ ký canonical theo các thành phần.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-types-006">Annotation được truyền sang các khai báo liên quan theo @Target</a>

<details>
<summary>Xem chi tiết</summary>

# Annotation trên record được truyền theo `@Target` như thế nào?

Khi một annotation được viết trước thành phần record, không nên hiểu rằng nó chỉ nằm trên một đối tượng `RecordComponent`, cũng không nên hiểu rằng nó được chép tới mọi thành viên liên quan. **Đích áp dụng của annotation và cách thành viên được khai báo quyết định nơi metadata xuất hiện.**

Điều này quan trọng với công cụ dùng Reflection: một công cụ đọc annotation trên trường dữ liệu có thể thấy khác công cụ đọc trên accessor hoặc tham số constructor. Hai kết quả khác nhau chưa chắc là lỗi; trước hết phải xác định đúng phần tử đang được truy vấn.

Bài này dùng Java 17 trở lên và phân biệt annotation của khai báo với annotation trên cách sử dụng kiểu dữ liệu.

## 1. Một thành phần record liên hệ với nhiều khai báo

Xét header:

```java
record Message(String text) {
}
```

`text` là một thành phần record. Nó liên hệ với:

- Metadata của chính thành phần: `RecordComponent`.
- Trường dữ liệu `text` được cung cấp cho record.
- Accessor `text()`.
- Tham số `text` của constructor chuẩn.

Đây là các vị trí metadata riêng biệt. Annotation được viết trong header có thể được trình biên dịch truyền tới những vị trí thích hợp. Cơ chế này thường được gọi là **truyền annotation (annotation propagation)**.

Đó không phải việc `getAnnotation()` đang tự đi tìm ở các vị trí khác trong lúc chạy. Khi bạn hỏi một `Field`, Reflection đọc annotation của trường đó; nó không tự hỏi thêm `RecordComponent` để ghép kết quả.

## 2. `@Target` và `@Retention` trả lời hai câu hỏi khác nhau

[`@Target`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/annotation/Target.html) mô tả những ngữ cảnh được phép áp dụng annotation. Trình biên dịch kiểm tra các giới hạn đó.

[`@Retention`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/annotation/Retention.html) mô tả thời gian lưu giữ. Muốn đọc các annotation trong thí nghiệm bằng Reflection khi chạy, chúng được khai báo với `RetentionPolicy.RUNTIME`. Nếu bỏ `@Retention`, mặc định là `CLASS`, không phải `RUNTIME`.

Vì vậy, khi không tìm thấy annotation, cần kiểm tra cả **vị trí metadata** lẫn **chính sách lưu giữ**, thay vì chỉ nhìn xem mã nguồn có dấu `@` hay không.

Với các thành viên được cung cấp ngầm định cho record, các đích thường gặp có ý nghĩa như sau:

| Đích trong `@Target` | Vị trí liên quan cần xét |
| --- | --- |
| `RECORD_COMPONENT` | Metadata của chính thành phần record |
| `FIELD` | Khai báo trường thành phần |
| `METHOD` | Khai báo accessor được cung cấp ngầm định |
| `PARAMETER` | Tham số được suy ra cho constructor chuẩn ngầm định hoặc rút gọn |
| `TYPE_USE` | Vị trí sử dụng kiểu, như kiểu thành phần, kiểu trường, kiểu trả về và kiểu tham số được suy ra |

Những tên đích này thuộc [`ElementType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/annotation/ElementType.html). `CONSTRUCTOR` nói về **khai báo constructor**, còn `PARAMETER` nói về **tham số của constructor**; không được xem chúng là một đích.

Một annotation chỉ có đích `CONSTRUCTOR` không được viết lên thành phần rồi trông đợi nó tự chuyển tới constructor. Trong ví dụ bên dưới, `Mark` có cả đích `CONSTRUCTOR`, nhưng việc dùng `@Mark` trong header vẫn không tự đánh dấu toàn bộ constructor.

## 3. Thí nghiệm Java 17+ đầy đủ

Chương trình dùng ba annotation:

- `Mark`: áp dụng cho nhiều loại khai báo để quan sát từng nơi nhận metadata.
- `FieldOnly`: chỉ áp dụng cho trường dữ liệu.
- `TypeMark`: chỉ áp dụng cho cách sử dụng kiểu dữ liệu.

Các record khác nhau ở việc để trình biên dịch cung cấp accessor hoặc constructor, tự khai báo chúng, hay tự ghi lại annotation tại những vị trí tường minh.

Lưu toàn bộ vào **`RecordAnnotationTargetsDemo.java`**:

```java
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RecordAnnotationTargetsDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({
            ElementType.RECORD_COMPONENT,
            ElementType.FIELD,
            ElementType.METHOD,
            ElementType.PARAMETER,
            ElementType.CONSTRUCTOR
    })
    public @interface Mark {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface FieldOnly {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface TypeMark {
    }

    public record Generated(@Mark @FieldOnly @TypeMark String text) {
    }

    public record Compact(@Mark @TypeMark String text) {
        public Compact {
            text = Objects.requireNonNull(text, "text");
        }
    }

    public record OwnAccessor(@Mark @TypeMark String text) {
        public String text() {
            return text;
        }
    }

    public record OwnConstructor(@Mark @TypeMark String text) {
        public OwnConstructor(String text) {
            this.text = text;
        }
    }

    public record Restated(@Mark @TypeMark String text) {
        @Mark
        public Restated(@Mark @TypeMark String text) {
            this.text = text;
        }

        @Mark
        public @TypeMark String text() {
            return text;
        }
    }

    public record NestedType(List<@TypeMark String> values) {
    }

    private static String names(Annotation[] annotations) {
        return Arrays.stream(annotations)
                .map(annotation -> annotation.annotationType().getSimpleName())
                .sorted()
                .toList()
                .toString();
    }

    private static void show(Class<?> type) throws NoSuchMethodException,
            NoSuchFieldException {
        RecordComponent component = type.getRecordComponents()[0];
        Field field = type.getDeclaredField(component.getName());
        Method accessor = component.getAccessor();
        Constructor<?> constructor = type.getDeclaredConstructor(component.getType());
        Parameter parameter = constructor.getParameters()[0];

        System.out.println(type.getSimpleName());
        System.out.println("  declarations: component="
                + names(component.getDeclaredAnnotations())
                + "; field=" + names(field.getDeclaredAnnotations())
                + "; accessor=" + names(accessor.getDeclaredAnnotations())
                + "; parameter=" + names(parameter.getDeclaredAnnotations())
                + "; constructor=" + names(constructor.getDeclaredAnnotations()));
        System.out.println("  types: component="
                + names(component.getAnnotatedType().getAnnotations())
                + "; field=" + names(field.getAnnotatedType().getAnnotations())
                + "; return=" + names(accessor.getAnnotatedReturnType().getAnnotations())
                + "; parameter=" + names(parameter.getAnnotatedType().getAnnotations()));
    }

    private static String firstTypeArgument(AnnotatedType type) {
        AnnotatedParameterizedType parameterized = (AnnotatedParameterizedType) type;
        return names(parameterized.getAnnotatedActualTypeArguments()[0].getAnnotations());
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        show(Generated.class);
        show(Compact.class);
        show(OwnAccessor.class);
        show(OwnConstructor.class);
        show(Restated.class);
        show(NestedType.class);

        RecordComponent component = NestedType.class.getRecordComponents()[0];
        Field field = NestedType.class.getDeclaredField("values");
        Method accessor = component.getAccessor();
        Constructor<?> constructor = NestedType.class.getDeclaredConstructor(List.class);
        System.out.println("Nested component argument = "
                + firstTypeArgument(component.getAnnotatedType()));
        System.out.println("Nested field argument = "
                + firstTypeArgument(field.getAnnotatedType()));
        System.out.println("Nested return argument = "
                + firstTypeArgument(accessor.getAnnotatedReturnType()));
        System.out.println("Nested parameter argument = "
                + firstTypeArgument(constructor.getParameters()[0].getAnnotatedType()));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 -Xlint:all -Werror RecordAnnotationTargetsDemo.java
java RecordAnnotationTargetsDemo
```

Kết quả:

```text
Generated
  declarations: component=[Mark]; field=[FieldOnly, Mark]; accessor=[Mark]; parameter=[Mark]; constructor=[]
  types: component=[TypeMark]; field=[TypeMark]; return=[TypeMark]; parameter=[TypeMark]
Compact
  declarations: component=[Mark]; field=[Mark]; accessor=[Mark]; parameter=[Mark]; constructor=[]
  types: component=[TypeMark]; field=[TypeMark]; return=[TypeMark]; parameter=[TypeMark]
OwnAccessor
  declarations: component=[Mark]; field=[Mark]; accessor=[]; parameter=[Mark]; constructor=[]
  types: component=[TypeMark]; field=[TypeMark]; return=[]; parameter=[TypeMark]
OwnConstructor
  declarations: component=[Mark]; field=[Mark]; accessor=[Mark]; parameter=[]; constructor=[]
  types: component=[TypeMark]; field=[TypeMark]; return=[TypeMark]; parameter=[]
Restated
  declarations: component=[Mark]; field=[Mark]; accessor=[Mark]; parameter=[Mark]; constructor=[Mark]
  types: component=[TypeMark]; field=[TypeMark]; return=[TypeMark]; parameter=[TypeMark]
NestedType
  declarations: component=[]; field=[]; accessor=[]; parameter=[]; constructor=[]
  types: component=[]; field=[]; return=[]; parameter=[]
Nested component argument = [TypeMark]
Nested field argument = [TypeMark]
Nested return argument = [TypeMark]
Nested parameter argument = [TypeMark]
```

Hàm `names()` sắp xếp tên annotation để kết quả dễ đối chiếu. Không dùng thứ tự các phần tử trong mảng annotation làm quy tắc ưu tiên.

`show()` là hàm kiểm chứng cho những record đã biết trong thí nghiệm; mỗi record ở đây có đúng một thành phần. Nó không được thiết kế như bộ quét tùy ý cho mọi loại lớp.

## 4. Đọc kết quả khi thành viên được cung cấp ngầm định

### `Generated`: một vị trí trong nguồn, nhiều đích metadata

Header có `@Mark @FieldOnly @TypeMark String text`, nhưng kết quả không phải ba annotation khai báo trên `RecordComponent`.

| Vị trí được truy vấn | Annotation khai báo nhìn thấy |
| --- | --- |
| `RecordComponent` | `Mark` |
| Trường `text` | `FieldOnly`, `Mark` |
| Accessor `text()` | `Mark` |
| Tham số constructor chuẩn | `Mark` |
| Chính khai báo constructor | Không có |

`FieldOnly` hợp lệ trong header nhờ đích trường dữ liệu, và nó xuất hiện trên trường tương ứng. Nhưng `RecordComponent.getDeclaredAnnotations()` không thấy `FieldOnly`, vì kiểu annotation đó không có đích `RECORD_COMPONENT`.

`TypeMark` không xuất hiện trong các mảng annotation khai báo ở bảng trên. Nó xuất hiện ở những đối tượng `AnnotatedType` của kiểu thành phần, trường, kiểu trả về accessor và tham số.

`Mark` có đích `CONSTRUCTOR` nhưng dòng `constructor=[]` vẫn rỗng. Điều này cho thấy “được phép dùng trên constructor” không có nghĩa “mọi lần dùng annotation ở header đều tự đánh dấu constructor”.

### `Compact`: tham số vẫn được suy ra từ header

`Compact` tự viết phần thân constructor chuẩn rút gọn nhưng không tự viết danh sách tham số. Các tham số vẫn được suy ra từ header, nên tham số `text` có `Mark` và kiểu tham số có `TypeMark`.

Việc có một phần thân constructor tường minh không đủ để kết luận metadata tham số bị mất; phải phân biệt **constructor rút gọn** với **constructor đầy đủ có danh sách tham số tự khai báo**.

## 5. Khi tự khai báo accessor hoặc constructor

### Accessor tường minh

`OwnAccessor` tự viết `public String text()`. Trong kết quả, annotation khai báo trên accessor là `[]`, và annotation trên kiểu trả về cũng là `[]`.

Annotation ở header vẫn tồn tại trên thành phần và trường; constructor chuẩn ngầm định vẫn có metadata tham số phù hợp. Chỉ accessor đã tự khai báo không nhận các annotation từ header một cách tự động.

Nếu công cụ của bạn đọc metadata trên accessor, việc thay một accessor ngầm định bằng accessor tường minh có thể làm thay đổi kết quả, dù tên và kiểu trả về của phương thức không đổi.

### Constructor chuẩn đầy đủ

`OwnConstructor` tự viết danh sách tham số `String text`. Kết quả cho thấy tham số đó không có `Mark`, và kiểu của nó không có `TypeMark` nếu không được ghi lại trong khai báo tham số.

Ngược lại, accessor vẫn được cung cấp ngầm định nên nhận metadata từ header theo các đích thích hợp.

### Ghi lại metadata khi cần

`Restated` chủ động viết annotation trên tham số và accessor tường minh. Vì thế các vị trí này lại nhìn thấy `Mark` hoặc `TypeMark`.

Annotation `@Mark` được viết riêng ngay trên khai báo constructor của `Restated` cũng làm `constructor=[Mark]`. Đây là annotation trực tiếp trên constructor, không phải kết quả của việc dùng `@Mark` trong header.

Tóm tắt hành vi của `Mark` trong các record của thí nghiệm:

| Record | Thành phần | Trường | Accessor | Tham số chuẩn | Constructor |
| --- | --- | --- | --- | --- | --- |
| `Generated` | Có | Có | Có | Có | Không |
| `Compact` | Có | Có | Có | Có | Không |
| `OwnAccessor` | Có | Có | Không | Có | Không |
| `OwnConstructor` | Có | Có | Có | Không | Không |
| `Restated` | Có | Có | Có do ghi trực tiếp | Có do ghi trực tiếp | Có do ghi trực tiếp |

Quy tắc truyền sang trường, accessor và tham số được mô tả trong [JLS về thành viên record](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.10.3) cùng phần constructor chuẩn. Cơ chế này không phải kế thừa annotation theo `@Inherited`.

## 6. `TYPE_USE` cần được đọc tại đúng vị trí trong cấu trúc kiểu

Annotation khai báo và annotation trên kiểu là hai kênh metadata khác nhau:

| Cần quan sát | API |
| --- | --- |
| Annotation khai báo của component | `component.getDeclaredAnnotations()` |
| Annotation trên kiểu ngoài cùng | `component.getAnnotatedType().getAnnotations()` |
| Annotation trên đối số generic | `((AnnotatedParameterizedType) component.getAnnotatedType()).getAnnotatedActualTypeArguments()` |

Trường hợp `NestedType(List<@TypeMark String> values)` ở thí nghiệm cho thấy `TypeMark` nằm trên `String` bên trong `List`; mảng annotation tại gốc `List` vẫn rỗng. Hàm `firstTypeArgument` trong chương trình kiểm tra trực tiếp vị trí đó cho component, field, accessor return và parameter type.

Phép ép sang `AnnotatedParameterizedType` chỉ hợp lệ khi đã xác định hình dạng kiểu như trong thí nghiệm. Với một công cụ tổng quát, hãy kiểm tra giao diện bằng `instanceof` và hỗ trợ thêm kiểu mảng, wildcard hoặc biến kiểu khi cần. Sự có mặt của `TYPE_USE` trong `@Target` chỉ xác định ngữ cảnh được phép đánh dấu; nó không khiến annotation tự thực thi kiểm tra dữ liệu.

## 7. Áp dụng trong công cụ xử lý metadata

Trước khi đọc annotation, cần quyết định nguồn metadata nào có ý nghĩa đối với công cụ:

- Xử lý cấu trúc record: có thể chọn metadata của `RecordComponent`.
- Xử lý trường dữ liệu: đọc `Field` và kiểu trường.
- Xử lý lời gọi accessor: đọc `Method` và kiểu trả về.
- Xử lý đối số tạo record: đọc `Parameter` của constructor chuẩn và kiểu tham số.

Đừng mặc định chuyển sang đọc vị trí khác chỉ vì vị trí hiện tại không có annotation. Điều đó có thể vô tình hợp nhất các hợp đồng mà tác giả chủ động tách ra, như trường hợp accessor hoặc tham số được khai báo tường minh.

Nếu công cụ thật sự muốn tổng hợp nhiều vị trí, hãy quy định thứ tự ưu tiên, cách phát hiện xung đột và cách tránh xử lý lặp cùng một ý nghĩa. Annotation xuất hiện trên nhiều khai báo do cùng một vị trí nguồn không tự nói cho công cụ biết phải thực thi một quy tắc bao nhiêu lần.

Việc truyền annotation cũng không tự thực hiện kiểm tra dữ liệu. Ví dụ, tên `TypeMark` hay một annotation tên `NonNull` chỉ là metadata nếu không có trình biên dịch, bộ xử lý hoặc mã runtime được thiết kế để sử dụng nó. Kiểm tra `Objects.requireNonNull` trong constructor `Compact` là hành vi được viết tường minh.

## 8. Lỗi thường gặp và tự kiểm tra

| Suy luận sai | Cách xác minh |
| --- | --- |
| Annotation viết trong header phải nằm trên `RecordComponent` | Kiểm tra đích `RECORD_COMPONENT` |
| Có `RUNTIME` là tìm được ở mọi vị trí | Kiểm tra cả đích áp dụng lẫn phần tử đang đọc |
| Có `METHOD` là tự truyền cả vào accessor tường minh | Phân biệt accessor ngầm định với tự khai báo |
| Mọi constructor tự viết đều làm mất metadata tham số | Phân biệt constructor rút gọn với dạng đầy đủ |
| `PARAMETER` và `CONSTRUCTOR` cùng chỉ một vị trí | Đọc `Parameter` riêng với `Constructor` |
| `TYPE_USE` được đọc bằng annotation của khai báo | Đi qua `AnnotatedType` phù hợp |
| Mức kiểu ngoài cùng không có annotation thì kiểu generic bên trong cũng không có | Duyệt cấu trúc kiểu đến đúng vị trí |
| Metadata đã được truyền thì dữ liệu tự được kiểm tra | Xác định bộ xử lý hoặc mã thực hiện quy tắc |

Hãy thử thay đổi từng yếu tố nhỏ của thí nghiệm rồi dự đoán kết quả trước khi chạy:

1. Bỏ `RECORD_COMPONENT` khỏi đích của `Mark`, giữ các đích khác: vị trí nào vẫn đọc được nó?
2. Bỏ `@Mark` trên accessor tường minh của `Restated`: annotation ở header có tự bổ sung lại không?
3. Đổi constructor đầy đủ của `OwnConstructor` thành dạng rút gọn: metadata tham số thay đổi thế nào?
4. Chuyển `@TypeMark` từ `List<@TypeMark String>` ra `@TypeMark List<String>`: vị trí trong cây kiểu cần đọc có đổi không?
5. Thay `RUNTIME` bằng `CLASS`: vì sao kết quả Reflection thay đổi dù cú pháp dùng annotation vẫn còn trong mã nguồn?

Ba câu hỏi cần giữ rõ là: **annotation được phép áp dụng ở đâu, thành viên đó được cung cấp ngầm định hay tự khai báo, và Reflection đang đọc khai báo hay đọc cách sử dụng kiểu**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-types-007">Accessor được khai báo tường minh và metadata thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# Accessor được khai báo tường minh và metadata thực tế của record

## 1. Accessor của record là một phương thức thực sự

Với `record Label(String text)`, component `text` có một phương thức truy cập cùng tên: `text()`. Nếu tác giả không khai báo nó, compiler cung cấp phần thực hiện trả về giá trị component field.

Tác giả cũng có thể khai báo accessor tường minh trong phần thân record. Khi đó, phương thức ấy chính là accessor của component; không có thêm một accessor ngầm định khác cùng chữ ký để Reflection lựa chọn.

Điều này tạo ra ba lớp thông tin cần phân biệt:

- **RecordComponent:** tên, kiểu, annotation và thông tin về component trong phần đầu record.
- **Method accessor:** phương thức truy cập thực tế, cùng metadata và phần thực hiện của nó.
- **Component field:** nơi lưu giá trị tương ứng, có đặc tính `private final`.

`RecordComponent.getAccessor()` nối component với đúng đối tượng `Method` mô tả accessor của nó. API này không bảo đảm accessor chỉ làm một phép đọc field đơn giản nếu tác giả đã tự viết phần thân.

## 2. Quy tắc của accessor tường minh

Accessor tường minh của một component phải:

- Có cùng tên với component.
- Là phương thức instance `public`.
- Không có tham số.
- Có kiểu trả về giống kiểu khai báo của component.
- Không khai báo tham số kiểu của riêng phương thức.
- Không có mệnh đề `throws`.

Ví dụ một khai báo hợp lệ về mặt ngôn ngữ:

~~~java
record Measurement(int value) {
    public int value() {
        return value;
    }
}
~~~

Đây là một khai báo record, không phải chương trình có `main`.

Không đổi accessor thành `getValue()` theo quy ước JavaBean rồi cho rằng nó thay thế `value()`. Một method khác tên chỉ là method khác của record. Tương tự, một overload như `value(int scale)` không phải accessor của component `value`.

Accessor được tự viết không nhất thiết ghi đè một phương thức từ lớp cha hoặc interface. Tuy vậy, Java có quy tắc riêng cho record: **được phép đặt `@Override` trên accessor tường minh của một component**, kể cả khi không có phương thức tương ứng trong kiểu cha. Annotation này giúp trình biên dịch phát hiện khi component bị đổi tên hoặc loại bỏ mà phương thức cũ không còn là accessor. Quy tắc được nêu tại [JLS §9.6.4.4](https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html#jls-9.6.4.4).

## 3. Ví dụ: metadata xác định accessor, lời gọi chạy phần thân thực tế

Ví dụ dưới cố ý đặt việc chuẩn hóa chuỗi trong accessor để quan sát sự khác biệt giữa giá trị lưu và giá trị trả về. Thiết kế `Label` trong ví dụ không giữ được quy tắc sao chép của record; phần sau sẽ chỉ ra cách sửa.

Lưu thành `ExplicitAccessorBehaviorDemo.java`:

~~~java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Locale;
import java.util.Objects;

public class ExplicitAccessorBehaviorDemo {
    public record Label(String text) {
        public String text() {
            return text.strip().toUpperCase(Locale.ROOT);
        }
    }

    public record NormalizedLabel(String text) {
        public NormalizedLabel {
            text = Objects.requireNonNull(text, "text")
                    .strip().toUpperCase(Locale.ROOT);
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<Label> type = Label.class;
        RecordComponent component = type.getRecordComponents()[0];
        Method accessor = component.getAccessor();
        Label original = new Label("  lan  ");

        System.out.println("component=" + component.getName());
        System.out.println("accessor=" + accessor.getName());
        System.out.println("parameters=" + accessor.getParameterCount());
        System.out.println("return=" + accessor.getReturnType().getSimpleName());
        System.out.println("sameMethod="
                + accessor.equals(type.getDeclaredMethod("text")));
        System.out.println("synthetic=" + accessor.isSynthetic());
        System.out.println("viaAccessor=[" + accessor.invoke(original) + "]");

        Field field = type.getDeclaredField(component.getName());
        field.setAccessible(true);
        System.out.println("viaField=[" + field.get(original) + "]");

        Label copy = new Label(original.text());
        System.out.println("copyEquals=" + original.equals(copy));

        NormalizedLabel normalized = new NormalizedLabel("  lan  ");
        NormalizedLabel normalizedCopy =
                new NormalizedLabel(normalized.text());
        System.out.println("normalized=[" + normalized.text() + "]");
        System.out.println("normalizedCopyEquals="
                + normalized.equals(normalizedCopy));
    }
}
~~~

Biên dịch và chạy bằng JDK 17:

~~~bash
javac --release 17 ExplicitAccessorBehaviorDemo.java
java ExplicitAccessorBehaviorDemo
~~~

Kết quả:

~~~text
component=text
accessor=text
parameters=0
return=String
sameMethod=true
synthetic=false
viaAccessor=[LAN]
viaField=[  lan  ]
copyEquals=false
normalized=[LAN]
normalizedCopyEquals=true
~~~

Các cặp dấu ngoặc vuông giúp nhìn rõ khoảng trắng trong giá trị field. Accessor tường minh trả `LAN`, còn field vẫn chứa chuỗi `"  lan  "` được đưa vào constructor.

`getAccessor()` trả metadata của phương thức `text()` thực tế. So sánh bằng `Method.equals` với phương thức tìm được từ `getDeclaredMethod("text")` cho kết quả đúng. Không dùng `==` để yêu cầu hai lần lấy metadata phải trả cùng một tham chiếu đối tượng `Method`.

Lời gọi `accessor.invoke(original)` chạy phần thân đã được tác giả viết. Reflection không bỏ qua phần chuẩn hóa để đọc thẳng field.

Việc bật truy cập field trong ví dụ chỉ phục vụ quan sát giá trị lưu trong cùng lớp ngoài; không có thao tác ghi field. Một công cụ sử dụng giao diện công khai của record không nên mặc định thay lời gọi accessor bằng đọc private field.

## 4. Hệ quả đối với hợp đồng của record

Các phần thực hiện mặc định của `equals`, `hashCode` và `toString` dựa trên component field, không gọi accessor tường minh để lấy một giá trị đã biến đổi.

Record cần giữ quy tắc sao chép: nếu tạo một record mới bằng các giá trị nhận từ accessor của record cũ, hai record phải bằng nhau theo `equals`.

Trong `Label` của ví dụ:

1. Đối tượng ban đầu lưu `"  lan  "`.
2. Accessor trả `"LAN"`.
3. Bản sao được tạo từ accessor lưu `"LAN"`.
4. `equals` mặc định so sánh hai giá trị field khác nhau, nên trả `false`.

Mã ấy biên dịch được nhưng thiết kế làm mất quy tắc sao chép. Compiler không kiểm chứng được mọi hành vi của accessor do tác giả viết.

`NormalizedLabel` chuyển bước chuẩn hóa vào compact constructor. Giá trị lưu ngay từ đầu đã là `LAN`; accessor ngầm định trả đúng giá trị đó. Bản sao đi qua cùng quy tắc chuẩn hóa và vẫn bằng đối tượng ban đầu.

Nếu chỉ cần một chuỗi hiển thị khác, một lựa chọn rõ ràng là giữ accessor trả dữ liệu component và thêm phương thức có tên riêng cho biểu diễn hiển thị. Dù chọn cách nào, tác giả phải xét sự nhất quán với constructor và các method đặc trưng của record.

Metadata như tên và kiểu trả về không đủ để chứng minh accessor “chỉ đọc field”. Một method có thể tính toán, ném ngoại lệ không kiểm tra hoặc thực hiện tác vụ khác mà vẫn có cùng chữ ký.

## 5. Annotation của component và annotation của accessor là hai nơi khác nhau

Annotation trên phần đầu record có thể được truyền sang các thành viên được khai báo ngầm định nếu `@Target` cho phép. Với **accessor được khai báo tường minh**, annotation trên component không tự được truyền sang method ấy.

Vì vậy, việc thêm một accessor tường minh có thể làm thay đổi metadata method quan sát được, dù tên component và kiểu component vẫn như cũ.

Lưu thành `ExplicitAccessorAnnotationsDemo.java`:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

public class ExplicitAccessorAnnotationsDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.RECORD_COMPONENT, ElementType.METHOD})
    public @interface HeaderTag {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface AccessorTag {
        String value();
    }

    public record Implicit(
            @HeaderTag("from-header") String text) {
    }

    public record Explicit(
            @HeaderTag("from-header") String text) {

        @AccessorTag("from-method")
        public String text() {
            return text;
        }
    }

    private static void report(Class<?> recordType) {
        RecordComponent component = recordType.getRecordComponents()[0];
        Method accessor = component.getAccessor();

        System.out.println(recordType.getSimpleName()
                + ": componentHeader="
                + component.isAnnotationPresent(HeaderTag.class)
                + ", accessorHeader="
                + accessor.isAnnotationPresent(HeaderTag.class)
                + ", accessorOwn="
                + accessor.isAnnotationPresent(AccessorTag.class));
    }

    public static void main(String[] args) {
        report(Implicit.class);
        report(Explicit.class);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 ExplicitAccessorAnnotationsDemo.java
java ExplicitAccessorAnnotationsDemo
~~~

Kết quả:

~~~text
Implicit: componentHeader=true, accessorHeader=true, accessorOwn=false
Explicit: componentHeader=true, accessorHeader=false, accessorOwn=true
~~~

Giải thích:

- `HeaderTag` được phép xuất hiện trên component và trên method, đồng thời giữ lại ở runtime.
- Cả hai component đều có `HeaderTag`.
- Accessor ngầm định của `Implicit` nhận annotation được truyền từ component.
- Accessor tường minh của `Explicit` không tự nhận `HeaderTag`; nó có `AccessorTag` được viết trực tiếp trên method.

Nếu một framework đọc annotation từ accessor, việc tác giả thêm method tường minh có thể làm framework không còn thấy annotation cũ ở method. Điều đó không có nghĩa annotation đã biến mất khỏi component.

Nếu hợp đồng yêu cầu accessor tường minh cũng có cùng annotation, tác giả có thể khai báo annotation tại method khi `@Target` cho phép. Một công cụ cũng có thể đọc đồng thời các vị trí theo chính sách đã định, nhưng không nên giả định mọi vị trí luôn chứa cùng dữ liệu.

## 6. Chọn đúng API cho loại metadata cần dùng

| Thông tin cần lấy | API phù hợp |
| --- | --- |
| Tên component trong phần đầu record | `RecordComponent.getName()` |
| Kiểu component ở mức Class | `RecordComponent.getType()` |
| Kiểu generic của component | `RecordComponent.getGenericType()` |
| Annotation khai báo trên component ở runtime | `RecordComponent.getAnnotations()` hoặc `getAnnotation(...)` |
| Metadata kiểu được chú thích của component | `RecordComponent.getAnnotatedType()` |
| Method accessor thực tế | `RecordComponent.getAccessor()` |
| Annotation của method accessor thực tế | `Method.getAnnotations()` hoặc `getAnnotation(...)` |
| Metadata kiểu trả về được chú thích của accessor | `Method.getAnnotatedReturnType()` |
| Giá trị do accessor cung cấp cho một instance | `Method.invoke(instance)` |

Annotation khai báo và annotation dùng trên kiểu (`TYPE_USE`) cũng là hai khía cạnh cần đọc bằng API tương ứng. Khi accessor được viết tường minh, hãy quan sát metadata của method và kiểu trả về thực tế; không tự suy ra rằng chúng đã được sao chép từ component.

Với generic, `getType()`/`getReturnType()` cho thông tin ở mức `Class`, còn `getGenericType()`/`getGenericReturnType()` giữ mô tả generic phù hợp. Không dùng một chuỗi tên kiểu đã rút gọn làm bằng chứng rằng toàn bộ metadata ở hai vị trí đều giống nhau.

Nên giữ thông tin nguồn của annotation khi tổng hợp: annotation đến từ component hay từ method? Nếu cả hai nơi có cùng loại annotation nhưng giá trị khác nhau, công cụ phải có quy tắc ưu tiên hoặc báo xung đột rõ ràng.

Không có một quy tắc gộp chung phù hợp cho mọi thư viện. Một bộ xử lý cấu trúc record có thể lấy component làm điểm chính; một bộ xử lý method công khai có thể dùng method làm điểm chính. Cả hai phải thực hiện chính sách của mình dựa trên dữ liệu đã đọc.

## 7. getAccessor không phải công cụ phân biệt tác giả với compiler

Accessor ngầm định cũng là method thật của lớp sau khi biên dịch. Accessor tường minh cũng được trả về qua cùng API `getAccessor()`.

Không có cờ `isExplicitAccessor()` trong `RecordComponent`. Cũng không dùng `Method.isSynthetic()` như một phép kiểm tra chắc chắn “ngầm định hay do tác giả viết”: các thành viên ngầm định được quy định bởi Java không nhất thiết là synthetic.

Trong ví dụ đầu, accessor tường minh có `synthetic=false`. Một accessor do compiler cung cấp cũng có thể có kết quả như vậy. Giá trị cờ này không chứng minh nguồn gốc tường minh của phần thân.

Nếu công cụ cần biết chính xác accessor có được viết trong tệp nguồn hay không, phải có thêm dữ liệu nguồn hoặc công cụ phân tích phù hợp. Không suy ra điều đó từ việc method có cùng tên với component, có annotation hay không, hoặc có vẻ chỉ trả một field.

Mục đích của `getAccessor()` là tìm đúng phương thức truy cập của component, không phải tái dựng lịch sử tác giả của method.

## 8. Ứng dụng trong công cụ đọc record

Một bộ đọc record có thể thực hiện theo thứ tự:

1. Kiểm tra `Class.isRecord()`.
2. Lấy `getRecordComponents()` để giữ cấu trúc và thứ tự component.
3. Với mỗi component, lấy `getAccessor()` và đọc metadata method cần thiết.
4. Giữ riêng metadata component, method và kiểu được chú thích.
5. Chỉ gọi accessor khi thực sự cần giá trị và khi đối tượng đầu vào phù hợp.

Không tìm accessor bằng cách lấy phần tử đầu tiên trong `getDeclaredMethods()` có cùng tên. Method overload hoặc bridge method có thể khiến một phép chọn đơn giản như vậy sai mục tiêu; thứ tự mảng metadata cũng không được dùng làm quy tắc chọn.

Khi gọi accessor tường minh:

- Nó có thể trả giá trị đã biến đổi, như ví dụ quan sát.
- Nó có thể ném ngoại lệ không kiểm tra dù không có `throws` trong khai báo.
- Ngoại lệ từ phần thân được `Method.invoke` bọc trong `InvocationTargetException`.
- Lời gọi vẫn chịu quy tắc truy cập của Reflection.

Nếu accessor thất bại, tự động chuyển sang đọc private field có thể làm thay đổi ngữ nghĩa của bộ đọc và bỏ qua logic công khai của record. Hãy xử lý lỗi theo hợp đồng của công cụ, thay vì âm thầm coi hai cách lấy giá trị là tương đương.

Một trình xem metadata chỉ cần tên, kiểu và annotation có thể dừng ở các bước đọc mô tả. Nó không cần chạy accessor để biết accessor nào thuộc component nào.

## 9. Các lỗi dễ gặp

| Giả định sai | Hệ quả |
| --- | --- |
| Accessor của record luôn là mã compiler sinh để đọc field | Bỏ qua phần thực hiện do tác giả viết |
| `getAccessor()` trả một method mặc định tách biệt với method tường minh | Hiểu sai method thực tế được dùng |
| Có cùng tên/kiểu nên field và accessor luôn trả cùng giá trị | Có thể đọc sai ngữ nghĩa dữ liệu |
| Annotation trên component luôn có trên accessor | Sai với accessor tường minh hoặc annotation không có target phù hợp |
| Không thấy annotation trên method nghĩa là component không có annotation | Bỏ sót metadata ở vị trí khác |
| Method không synthetic chắc chắn do tác giả viết | Phân loại sai accessor ngầm định |
| `equals` mặc định gọi accessor đã được tùy chỉnh | Bỏ qua giá trị field được dùng cho so sánh |
| Tùy chỉnh accessor không cần xét constructor/copy | Có thể làm mất quy tắc sao chép của record |

## 10. Tự kiểm tra

1. Khi record tự khai báo accessor, `RecordComponent.getAccessor()` trả method nào?
2. Vì sao một bộ đọc gọi accessor có thể nhận giá trị khác với một bộ đọc field?
3. `@Target(METHOD)` có đủ để bảo đảm annotation trên component được truyền sang một accessor tường minh không?
4. Nếu annotation còn trên component nhưng không có trên accessor, hai kết quả Reflection có mâu thuẫn không?
5. Có thể dùng `isSynthetic()` để xác định chắc chắn accessor được tác giả viết hay không?
6. Vì sao chuyển chuẩn hóa vào constructor giúp `NormalizedLabel` giữ quy tắc sao chép trong ví dụ?

Đáp án:

1. Method accessor thực tế đã được khai báo tường minh.
2. Accessor có phần thân thực thi riêng; đọc field lấy giá trị đang lưu.
3. Không. Accessor tường minh không tự nhận annotation truyền từ component; target chỉ là điều kiện về vị trí được phép dùng.
4. Không. Chúng mô tả hai phần tử được chú thích khác nhau.
5. Không; cần thêm dữ liệu nếu muốn xác định nguồn gốc phần thân.
6. Giá trị lưu đã được chuẩn hóa; accessor trả giá trị ấy và bản sao qua constructor giữ cùng giá trị.

## Tài liệu đối chiếu

- [JLS 17 — Thành viên record, accessor tường minh và annotation](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.10.3).
- [Java 17 — RecordComponent.getAccessor và metadata component](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/RecordComponent.html#getAccessor()).
- [Java 17 — Record và quy tắc sao chép](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-types-008">RecordComponent.getAnnotatedType và kiểu thành phần có annotation</a>

<details>
<summary>Xem chi tiết</summary>

# `RecordComponent.getAnnotatedType()` và annotation trên kiểu thành phần

Một thành phần record có thể mang annotation trên chính khai báo, trên kiểu ngoài cùng, hoặc sâu bên trong kiểu generic, mảng và giới hạn kiểu. Chỉ đọc `RecordComponent.getAnnotations()` không đủ để nhìn thấy tất cả những vị trí đó.

`getAnnotatedType()` đưa bạn tới **cấu trúc kiểu có thể chứa annotation** của thành phần. Từ đó, chương trình có thể đi tới đúng vị trí được đánh dấu thay vì gộp mọi annotation thành một danh sách phẳng.

Điều này hữu ích khi viết công cụ kiểm tra dữ liệu hoặc phân tích cấu trúc kiểu: “danh sách không được rỗng”, “phần tử không được null” và “mảng có một đặc tính nào đó” có thể là các yêu cầu khác nhau ở những vị trí kiểu khác nhau.

## 1. Ba cách nhìn vào kiểu của một thành phần

[API `RecordComponent`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/RecordComponent.html) cung cấp các mức thông tin khác nhau:

| API | Kết quả | Nhu cầu chính |
| --- | --- | --- |
| `getType()` | `Class<?>` | Kiểu ở dạng lớp, dùng cho nhiều thao tác tra cứu Reflection |
| `getGenericType()` | `Type` | Cấu trúc kiểu khai báo có generic, biến kiểu hoặc wildcard |
| `getAnnotatedType()` | `AnnotatedType` | Cấu trúc kiểu khai báo kèm annotation trên từng vị trí sử dụng kiểu |

Ví dụ, với `List<@TypeNote("item") String> tags`, `getType()` cho `List.class`, còn `getGenericType()` mô tả `List<String>`. Muốn biết annotation gắn với vị trí `String` bên trong danh sách, cần đi vào cấu trúc `AnnotatedType`.

`getAnnotatedType()` mô tả **kiểu được khai báo**, không đọc giá trị của một đối tượng record và không tự đổi biến kiểu generic thành kiểu thực tế của dữ liệu đang chứa.

## 2. Annotation khai báo và annotation sử dụng kiểu

Hai kiểu annotation trong thí nghiệm có các đích riêng:

- `ComponentNote` có `@Target(RECORD_COMPONENT)`: đánh dấu chính thành phần record.
- `TypeNote` có `@Target(TYPE_USE)`: đánh dấu một vị trí sử dụng kiểu.

`TYPE_USE` cho phép đặt annotation tại những vị trí như kiểu thành phần, đối số kiểu generic hoặc kiểu phần tử mảng; đây là ngữ cảnh được phân loại trong [`ElementType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/annotation/ElementType.html#TYPE_USE).

Với một thành phần vừa có `@ComponentNote` vừa có `@TypeNote`, hai lời gọi này không hỏi cùng một nơi:

```java
component.getDeclaredAnnotation(ComponentNote.class);
component.getAnnotatedType().getAnnotation(TypeNote.class);
```

Đây là đoạn trích khi đã có biến `RecordComponent component` và các kiểu annotation tương ứng. Lời gọi đầu đọc metadata khai báo của thành phần; lời gọi sau đọc annotation tại gốc của kiểu thành phần.

Cả hai annotation trong ví dụ đều có `@Retention(RUNTIME)` để đọc được khi chạy. Đích áp dụng và thời gian lưu giữ là hai điều kiện độc lập.

## 3. Cần nhìn cấu trúc kiểu theo từng vị trí

[`AnnotatedType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedType.html) biểu diễn một cách sử dụng kiểu có thể mang annotation. Nó không nhất thiết có annotation ở chính nút ngoài cùng.

| Dạng cấu trúc | Giao diện Reflection | Bước đi tiếp |
| --- | --- | --- |
| Kiểu generic như `List<String>` | `AnnotatedParameterizedType` | `getAnnotatedActualTypeArguments()` |
| Kiểu mảng | `AnnotatedArrayType` | `getAnnotatedGenericComponentType()` |
| Wildcard như `? extends Number` | `AnnotatedWildcardType` | `getAnnotatedUpperBounds()` và `getAnnotatedLowerBounds()` |
| Biến kiểu như `T` | `AnnotatedTypeVariable` | `getAnnotatedBounds()` |

Không ép mọi `AnnotatedType` thành `AnnotatedParameterizedType`. Một thành phần `String`, `String[]` hay `T` có hình dạng khác. Dùng `instanceof` để chọn cách duyệt phù hợp.

## 4. Thí nghiệm đầy đủ trên Java 17+

Chương trình đánh dấu từng vị trí bằng chuỗi riêng để thấy rõ annotation nào thuộc nút nào. Nó không cần tạo đối tượng record, vì chỉ phân tích metadata kiểu.

Lưu toàn bộ vào **`RecordAnnotatedTypeDemo.java`**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedTypeVariable;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecordAnnotatedTypeDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface TypeNote {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.RECORD_COMPONENT)
    public @interface ComponentNote {
        String value();
    }

    public record Sample<T extends @TypeNote("bound") Number>(
            @ComponentNote("declaration-only") @TypeNote("text") String name,
            @TypeNote("list") List<@TypeNote("item") String> tags,
            @TypeNote("array-element") String @TypeNote("array") [] codes,
            List<? extends @TypeNote("upper") Number> numbers,
            List<? super @TypeNote("lower") Integer> sinks,
            @TypeNote("use-T") T quantity,
            String plain) {
    }

    public record Recursive<T extends Comparable<T>>(T value) {
    }

    private static String notes(AnnotatedType type) {
        return Arrays.stream(type.getAnnotationsByType(TypeNote.class))
                .map(TypeNote::value)
                .sorted()
                .toList()
                .toString();
    }

    private static void walk(String path, AnnotatedType type,
            Set<TypeVariable<?>> activeVariables) {
        System.out.println(path + ": " + type.getType().getTypeName()
                + " notes=" + notes(type));

        if (type instanceof AnnotatedTypeVariable variableType) {
            TypeVariable<?> variable = (TypeVariable<?>) type.getType();
            if (!activeVariables.add(variable)) {
                System.out.println(path + ".bounds: cycle stopped");
                return;
            }
            AnnotatedType[] bounds = variableType.getAnnotatedBounds();
            for (int i = 0; i < bounds.length; i++) {
                walk(path + ".bound[" + i + "]", bounds[i], activeVariables);
            }
            activeVariables.remove(variable);
            return;
        }

        AnnotatedType owner = type.getAnnotatedOwnerType();
        if (owner != null) {
            walk(path + ".owner", owner, activeVariables);
        }

        if (type instanceof AnnotatedParameterizedType parameterized) {
            AnnotatedType[] arguments = parameterized.getAnnotatedActualTypeArguments();
            for (int i = 0; i < arguments.length; i++) {
                walk(path + ".arg[" + i + "]", arguments[i], activeVariables);
            }
        } else if (type instanceof AnnotatedArrayType array) {
            walk(path + ".element",
                    array.getAnnotatedGenericComponentType(), activeVariables);
        } else if (type instanceof AnnotatedWildcardType wildcard) {
            AnnotatedType[] upper = wildcard.getAnnotatedUpperBounds();
            for (int i = 0; i < upper.length; i++) {
                walk(path + ".upper[" + i + "]", upper[i], activeVariables);
            }
            AnnotatedType[] lower = wildcard.getAnnotatedLowerBounds();
            for (int i = 0; i < lower.length; i++) {
                walk(path + ".lower[" + i + "]", lower[i], activeVariables);
            }
        }
    }

    private static void inspect(Class<?> recordType) {
        RecordComponent[] components = recordType.getRecordComponents();
        if (components == null) {
            throw new IllegalArgumentException("Expected a record declaration");
        }
        System.out.println("Record " + recordType.getSimpleName());
        for (RecordComponent component : components) {
            ComponentNote declaration =
                    component.getDeclaredAnnotation(ComponentNote.class);
            System.out.println("component " + component.getName()
                    + " | raw=" + component.getType().getTypeName()
                    + " | generic=" + component.getGenericType().getTypeName()
                    + " | declaration="
                    + (declaration == null ? "none" : declaration.value()));
            walk("type", component.getAnnotatedType(), new HashSet<>());
        }
    }

    public static void main(String[] args) {
        inspect(Sample.class);
        inspect(Recursive.class);
        System.out.println("String class TypeNote = "
                + String.class.getAnnotation(TypeNote.class));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 -Xlint:all -Werror RecordAnnotatedTypeDemo.java
java RecordAnnotatedTypeDemo
```

Kết quả:

```text
Record Sample
component name | raw=java.lang.String | generic=java.lang.String | declaration=declaration-only
type: java.lang.String notes=[text]
component tags | raw=java.util.List | generic=java.util.List<java.lang.String> | declaration=none
type: java.util.List<java.lang.String> notes=[list]
type.arg[0]: java.lang.String notes=[item]
component codes | raw=java.lang.String[] | generic=java.lang.String[] | declaration=none
type: java.lang.String[] notes=[array]
type.element: java.lang.String notes=[array-element]
component numbers | raw=java.util.List | generic=java.util.List<? extends java.lang.Number> | declaration=none
type: java.util.List<? extends java.lang.Number> notes=[]
type.arg[0]: ? extends java.lang.Number notes=[]
type.arg[0].upper[0]: java.lang.Number notes=[upper]
component sinks | raw=java.util.List | generic=java.util.List<? super java.lang.Integer> | declaration=none
type: java.util.List<? super java.lang.Integer> notes=[]
type.arg[0]: ? super java.lang.Integer notes=[]
type.arg[0].upper[0]: java.lang.Object notes=[]
type.arg[0].lower[0]: java.lang.Integer notes=[lower]
component quantity | raw=java.lang.Number | generic=T | declaration=none
type: T notes=[use-T]
type.bound[0]: java.lang.Number notes=[bound]
component plain | raw=java.lang.String | generic=java.lang.String | declaration=none
type: java.lang.String notes=[]
Record Recursive
component value | raw=java.lang.Comparable | generic=T | declaration=none
type: T notes=[]
type.bound[0]: java.lang.Comparable<T> notes=[]
type.bound[0].arg[0]: T notes=[]
type.bound[0].arg[0].bounds: cycle stopped
String class TypeNote = null
```

Trong kết quả, `notes` là danh sách giá trị của riêng annotation `TypeNote` tại nút đang xét. `declaration` là giá trị của riêng `ComponentNote` trên khai báo thành phần, hoặc `none` khi không có annotation đó.

Các nhãn đường đi `type`, `arg`, `element`, `upper`, `lower`, `bound` do chương trình đặt để giải thích vị trí; chúng không phải tên do API bắt buộc cung cấp.

## 5. Đọc annotation trên kiểu thông thường và kiểu generic

### Thành phần `name`

Kết quả có `declaration=declaration-only` từ `ComponentNote`, còn `type: java.lang.String notes=[text]` từ `TypeNote`.

Hai annotation cùng được viết gần nhau trong header nhưng được đọc qua hai đối tượng metadata khác nhau. Không dùng kết quả của một truy vấn để kết luận rằng truy vấn còn lại cũng phải có cùng annotation.

Dòng cuối `String class TypeNote = null` cũng cho thấy annotation tại vị trí sử dụng `String` trong record không được gắn toàn cục lên `String.class`. Nó mô tả **lần sử dụng kiểu tại thành phần này**, không sửa khai báo lớp `String`.

### Thành phần `tags`

Kiểu ngoài cùng `List<...>` có `notes=[list]`. Đối số kiểu thứ nhất `String` có `notes=[item]`, được đọc tại `type.arg[0]`.

Để tới vị trí đó, mã dùng [`AnnotatedParameterizedType.getAnnotatedActualTypeArguments()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedParameterizedType.html). Hai annotation nằm ở hai nút độc lập; đọc annotation gốc không tự gom annotation của phần tử.

Nếu một công cụ muốn áp dụng quy tắc khác nhau cho danh sách và cho phần tử, cần giữ lại đường đi của từng annotation thay vì chỉ thu một tập tên annotation.

### Thành phần `plain`

`plain` vẫn có đối tượng `AnnotatedType` mô tả `String`, dù danh sách `TypeNote` tại đó rỗng. Từ “annotated” trong tên API không có nghĩa kiểu bắt buộc phải mang ít nhất một annotation.

Cũng không nên dùng tên lớp cài đặt nội bộ của đối tượng Reflection để suy ra hình dạng kiểu. Các giao diện `AnnotatedType` và những giao diện con mới là bề mặt cần dựa vào.

## 6. Mảng: annotation trên mảng khác annotation trên phần tử

Thành phần `codes` được khai báo:

```java
@TypeNote("array-element") String @TypeNote("array") [] codes
```

Đây là đoạn trích header trong chương trình, không phải khai báo trường độc lập cần chép nguyên vào một lớp.

Vị trí trước `String` đánh dấu kiểu phần tử; vị trí trước `[]` đánh dấu kiểu mảng. Vì vậy:

- Nút `type` có `notes=[array]`.
- Nút `type.element` có `notes=[array-element]`.

Bước đi từ mảng tới kiểu phần tử dùng [`AnnotatedArrayType.getAnnotatedGenericComponentType()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedArrayType.html). Với mảng nhiều chiều, cần tiếp tục đi qua từng tầng mảng, không coi mọi dấu ngoặc vuông là cùng một vị trí.

Phân biệt này có ý nghĩa thực tế: một quy tắc về tham chiếu mảng và một quy tắc về từng giá trị phần tử không phải cùng một yêu cầu.

## 7. Wildcard: annotation có thể nằm trên giới hạn

### Giới hạn trên

`numbers` có kiểu `List<? extends @TypeNote("upper") Number>`. Nút `List` và nút wildcard đều không có `TypeNote`; annotation được tìm thấy ở giới hạn trên `Number`, tại `type.arg[0].upper[0]`.

Nếu chỉ hỏi gốc của `AnnotatedType`, bạn sẽ thấy mảng rỗng và bỏ sót metadata cần thiết bên trong.

### Giới hạn dưới

`sinks` có kiểu `List<? super @TypeNote("lower") Integer>`. Kết quả có giới hạn trên `Object` không được đánh dấu, cùng giới hạn dưới `Integer` mang `notes=[lower]`.

Đó là lý do bộ duyệt kiểm tra cả hai dãy giới hạn qua [`AnnotatedWildcardType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedWildcardType.html). Không giả định rằng cứ thấy wildcard là phải có một annotation ở gốc wildcard, hoặc rằng chỉ cần nhìn một phía giới hạn.

## 8. Biến kiểu, giới hạn và đệ quy

### Phân biệt lần dùng `T` với giới hạn của `T`

`Sample` khai báo `T extends @TypeNote("bound") Number`. Thành phần `quantity` lại sử dụng `@TypeNote("use-T") T`.

Kết quả tách rõ:

- Nút `type: T` mang `notes=[use-T]`.
- Nút giới hạn `Number` mang `notes=[bound]`.
- `getType()` của thành phần cho `Number`, còn kiểu generic khai báo là `T`.

`getAnnotatedBounds()` trên [`AnnotatedTypeVariable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedTypeVariable.html) đưa bạn tới các giới hạn khai báo của biến kiểu. Annotation của lần dùng biến kiểu và annotation của giới hạn là các thông tin khác nhau.

Metadata này không tự thay `T` bằng `Integer` chỉ vì một đối tượng có thể được tạo với tham số kiểu `Sample<Integer>`. Nếu cần phân giải kiểu theo ngữ cảnh sử dụng cụ thể, công cụ phải nhận và xử lý thêm ngữ cảnh generic đó.

### Giới hạn tự tham chiếu

`Recursive<T extends Comparable<T>>` tạo quan hệ:

```text
T → Comparable<T> → T → Comparable<T> → ...
```

Do đó, khi đi sâu vào giới hạn, cấu trúc cần xử lý có thể là một đồ thị có chu trình, dù cách trình bày ban đầu trông như cây.

Bộ duyệt dùng `Set<TypeVariable<?>>` để theo dõi các biến kiểu đang có trên đường đi hiện tại. Khi gặp lại cùng biến kiểu trong đường đó, nó vẫn in nút nhưng dừng mở rộng giới hạn và báo `cycle stopped`.

Biến kiểu được loại khỏi tập sau khi duyệt xong nhánh. Đây không phải một tập “đã gặp ở đâu đó trong toàn bộ kiểu” để tự động bỏ mọi lần xuất hiện về sau: các lần sử dụng khác nhau vẫn có thể mang annotation khác nhau và cần được quan sát.

Không dùng riêng tên chuỗi `"T"` làm định danh tổng quát cho biến kiểu, vì các khai báo khác nhau có thể cùng đặt tên biến là `T`.

## 9. Luồng áp dụng và giới hạn của ví dụ

Khi phân tích thành phần record:

1. Xác nhận đang có metadata của record và lấy đúng `RecordComponent`.
2. Đọc annotation khai báo riêng nếu công cụ cần ngữ nghĩa của thành phần.
3. Lấy gốc `component.getAnnotatedType()`.
4. Tại mỗi nút, đọc annotation ở chính nút đó.
5. Dựa vào giao diện kiểu để đi tới các nút con.
6. Giữ đường đi và có cơ chế dừng khi mở rộng giới hạn tự tham chiếu.

Mã còn xét `getAnnotatedOwnerType()` khi kiểu có một kiểu bao tương ứng. Điều này hữu ích với cấu trúc lớp lồng có thông tin kiểu ở phần bao. Các ví dụ chính trong bài tập trung vào generic, mảng, wildcard và biến kiểu.

Đây là bộ quan sát metadata, không phải bộ kiểm tra dữ liệu. Một annotation có tên `NonNull` hay một giá trị `TypeNote` không tự khiến JVM từ chối `null` hoặc kiểm tra từng phần tử danh sách. Muốn thực thi quy tắc, cần một bộ xử lý có hợp đồng rõ ràng.

Tương tự, đọc được metadata kiểu không tự cấp quyền truy cập trường dữ liệu, gọi accessor hay thay đổi trạng thái. Bài này không cần thao tác với dữ liệu của một instance.

## 10. Lỗi thường gặp và tự kiểm tra

| Lỗi | Cách khắc phục |
| --- | --- |
| Chỉ đọc `RecordComponent.getAnnotations()` | Phân biệt khai báo thành phần với kiểu sử dụng |
| Chỉ đọc annotation ở gốc kiểu | Duyệt đúng vị trí generic, mảng hoặc giới hạn |
| Ép mọi kiểu thành `AnnotatedParameterizedType` | Kiểm tra giao diện thực tế bằng `instanceof` |
| Gộp annotation trên mảng và phần tử | Giữ từng tầng của `AnnotatedArrayType` |
| Nhìn wildcard nhưng bỏ giới hạn | Kiểm tra dãy giới hạn trên và dưới |
| Duyệt giới hạn generic mà không phát hiện chu trình | Theo dõi biến kiểu đang mở rộng trên đường đi |
| Cho rằng metadata của `String` được sử dụng là annotation trên `String.class` | Phân biệt lần sử dụng kiểu với khai báo lớp |
| Cho rằng metadata kiểu chứa giá trị thực tế của một record | Tách việc phân tích kiểu khỏi đọc dữ liệu |

Hãy thử các thay đổi nhỏ rồi dự đoán đầu ra:

1. Bỏ `@TypeNote("list")` nhưng giữ `@TypeNote("item")`: vì sao gốc rỗng mà phần tử vẫn có annotation?
2. Đổi vị trí annotation giữa `String` và `[]` của `codes`: nút nào thay đổi?
3. Chuyển `numbers` sang một wildcard không ghi giới hạn: bộ duyệt nhìn thấy kiểu giới hạn nào?
4. Bỏ cơ chế phát hiện chu trình rồi thử `Recursive`: vì sao việc chỉ đi theo các lời gọi API hợp lệ vẫn có thể không kết thúc?
5. Thêm một thành phần `T` khác với một `TypeNote` khác: vì sao không nên bỏ qua nó chỉ vì đã gặp tên `T` trước đó?

Điểm cần nắm là **annotation thuộc một vị trí trong cấu trúc kiểu**. `getAnnotatedType()` là cửa vào cấu trúc đó; nhiệm vụ của công cụ là đi tới đúng nút, đọc đúng metadata và giữ nguyên ý nghĩa của vị trí.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-types-009">Thiết kế công cụ ánh xạ hỗ trợ record và lớp thông thường</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế công cụ ánh xạ hỗ trợ record và lớp thông thường

## 1. Cùng là dữ liệu đối tượng, nhưng cách tạo khác nhau

Một công cụ ánh xạ thường nhận dữ liệu có tên thuộc tính rồi tạo một đối tượng Java. Ví dụ:

~~~text
{name: " Linh ", age: 20}
    -> đối tượng học viên
~~~

Nếu đối tượng đích là một lớp JavaBean có constructor không tham số và setter, công cụ có thể tạo đối tượng trước rồi gán từng thuộc tính.

Nếu đối tượng đích là record, cách làm phù hợp là chuẩn bị đầy đủ đối số rồi gọi **canonical constructor**. Các thành phần record không có setter để dùng theo mô hình JavaBean thông thường.

Vì vậy, không nên thiết kế một thuật toán duy nhất theo dạng:

~~~text
Tạo đối tượng rỗng
    -> tìm mọi field
    -> mở truy cập
    -> ghi giá trị vào từng field
~~~

Thuật toán đó vừa bỏ qua giao diện của lớp thường, vừa không phù hợp với trường thành phần của record. Trường thành phần record là `private final`; Reflection không cho dùng việc mở truy cập để tùy ý ghi lại các trường final của lớp record. Quy tắc ghi trường được mô tả tại [Field.set](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html#set(java.lang.Object,java.lang.Object)).

Thiết kế tốt hơn là **dùng chung mô hình thuộc tính và quy tắc kiểm tra**, nhưng có **chiến lược tạo đối tượng phù hợp với từng loại đích**.

## 2. Xác định hợp đồng hỗ trợ trước khi viết Reflection

“Lớp thông thường” bao gồm nhiều thiết kế: JavaBean có setter, lớp bất biến chỉ có constructor, lớp dùng builder, lớp có factory tĩnh hoặc lớp có trường công khai. Không có một thuật toán suy đoán duy nhất phù hợp với mọi loại.

Bài học xây dựng một mapper hai chiều:

~~~text
Map<String, ?> -> đối tượng
đối tượng      -> Map<String, Object>
~~~

Hợp đồng cụ thể của ví dụ:

| Đích | Cách khám phá | Cách tạo | Cách đọc |
| --- | --- | --- | --- |
| Record công khai | `getRecordComponents()` | Canonical constructor công khai | Accessor của từng component |
| JavaBean công khai | `Introspector` và `PropertyDescriptor` | Constructor công khai không tham số, sau đó gọi setter | Getter của từng thuộc tính |

Với nhánh JavaBean, ví dụ yêu cầu các thuộc tính được phát hiện đều đọc và ghi được. Nó từ chối thuộc tính chỉ đọc, chỉ ghi, chỉ truy cập theo chỉ số hoặc accessor tĩnh.

Các giá trị đầu vào đã ở dạng đối tượng Java có kiểu phù hợp. Ví dụ không tự chuyển chuỗi `"20"` thành số, không ánh xạ đệ quy một map lồng nhau thành đối tượng con và không xác minh từng phần tử của một collection generic.

Đó là hợp đồng có chủ đích để thấy rõ kiến trúc. Khi mở rộng, hãy bổ sung chiến lược hoặc bộ chuyển đổi tương ứng, thay vì làm nhánh Reflection âm thầm đoán nhiều cách khác nhau.

## 3. Tách giai đoạn khảo sát và giai đoạn xử lý dữ liệu

Một lần ánh xạ nên có các trách nhiệm rõ ràng:

1. **Khảo sát kiểu đích:** nhận diện record hoặc mô hình JavaBean.
2. **Lập kế hoạch:** lưu tên, kiểu và phương thức truy cập cho từng thuộc tính; xác định constructor.
3. **Kiểm tra dữ liệu đầu vào:** khóa lạ, khóa thiếu, `null`, kiểu giá trị.
4. **Tạo đối tượng:** gọi constructor theo chiến lược đã chọn.
5. **Gán thuộc tính nếu cần:** chỉ ở nhánh JavaBean.
6. **Báo lỗi có ngữ cảnh:** giữ tên thuộc tính hoặc vị trí constructor cùng nguyên nhân.

Metadata không đổi theo từng map dữ liệu. Vì vậy có thể xây một đối tượng mapper cho một `Class<T>`, lập kế hoạch một lần rồi tái sử dụng nó.

Trong ví dụ, mô hình thuộc tính chung là:

~~~java
record Property(String name, Class<?> type, Method reader, Method writer) {
}
~~~

Đây là phần trích từ chương trình sau; cần import `java.lang.reflect.Method` khi dùng độc lập trong một tệp.

- Record có `reader` là accessor và không có `writer`.
- JavaBean có `reader` là getter, `writer` là setter.
- Tên và kiểu được dùng chung cho kiểm tra dữ liệu.
- Cách tạo đối tượng được giữ ở kế hoạch của mapper, không suy ra từ việc `writer` có tồn tại cho từng thuộc tính riêng lẻ.

## 4. Nhánh record: chuẩn bị đối số rồi gọi constructor

Metadata thành phần record cho biết tên, kiểu và thứ tự khai báo. [Class.getRecordComponents](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getRecordComponents()) bảo đảm thứ tự các thành phần theo phần đầu record.

Đối với:

~~~java
public record StudentRecord(String name, int age) {
}
~~~

chữ ký canonical constructor dùng thứ tự:

~~~text
(String, int)
~~~

Dù map đầu vào duyệt `age` trước `name`, công cụ vẫn phải tạo mảng đối số theo **thứ tự thành phần record**, không theo thứ tự của map.

Quy trình là:

~~~text
component name -> lấy giá trị theo khóa "name" -> đối số 0
component age  -> lấy giá trị theo khóa "age"  -> đối số 1
    -> gọi canonical constructor
~~~

Những điểm cần tránh:

- Không chọn constructor đầu tiên trong `getDeclaredConstructors()`.
- Không yêu cầu record có constructor không tham số.
- Không suy tên thuộc tính từ `getX()`; accessor record thường tên `x()`.
- Không tạo record rồi cố ghi trực tiếp trường thành phần.
- Không bỏ qua canonical hoặc compact constructor, vì đây có thể là nơi kiểm tra và chuẩn hóa dữ liệu.

Constructor trong ví dụ chuẩn hóa tên bằng `strip()` và từ chối tuổi âm. Mapper phải thực thi logic đó khi tạo record.

## 5. Nhánh JavaBean: dùng mô tả thuộc tính thay vì đoán tên field

`Introspector` khảo sát các thuộc tính JavaBean. Mỗi `PropertyDescriptor` có thể cung cấp kiểu, phương thức đọc và phương thức ghi.

Ví dụ:

~~~text
getName() / setName(String) -> thuộc tính name
getAge()  / setAge(int)     -> thuộc tính age
~~~

Công cụ không cần ghi vào trường `private name` hoặc `private age`. Nó dùng giao diện getter/setter, nhờ đó logic kiểm tra trong setter vẫn được thực thi.

Ví dụ gọi:

~~~java
Introspector.getBeanInfo(type, Object.class)
~~~

để dừng trước `Object`, tránh coi thuộc tính suy ra từ `getClass()` là dữ liệu của mô hình.

Tài liệu [Introspector](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/beans/Introspector.html) và [PropertyDescriptor](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/beans/PropertyDescriptor.html) mô tả các điểm vào này.

Các thuộc tính JavaBean được ví dụ sắp xếp theo tên để có thứ tự ổn định. Thứ tự đó phục vụ kế hoạch và kết quả trình bày; không phải thứ tự khai báo trường của lớp.

Vì sử dụng `java.beans`, chương trình cần module `java.desktop`, có trong JDK đầy đủ. Nếu chuyển sang một dự án dùng `module-info.java`, module của chương trình cần khai báo `requires java.desktop;`.

## 6. Chương trình hoàn chỉnh

Lưu vào `DualModelMapperDemo.java`:

~~~java
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DualModelMapperDemo {
    public record StudentRecord(String name, int age) {
        public StudentRecord {
            name = Objects.requireNonNull(name, "name").strip();
            if (age < 0) {
                throw new IllegalArgumentException("age must be nonnegative");
            }
        }
    }

    public static class StudentBean {
        private String name;
        private int age;

        public StudentBean() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = Objects.requireNonNull(name, "name").strip();
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            if (age < 0) {
                throw new IllegalArgumentException("age must be nonnegative");
            }
            this.age = age;
        }
    }

    record Property(String name, Class<?> type, Method reader, Method writer) {
    }

    public static final class MappingException extends RuntimeException {
        MappingException(String path, Throwable cause) {
            super(path + ": " + cause.getClass().getSimpleName(), cause);
        }
    }

    static MappingException failure(String path, Exception error) {
        Throwable cause = error instanceof InvocationTargetException wrapped
                ? wrapped.getCause() : error;
        return new MappingException(path, cause);
    }

    public static final class Mapper<T> {
        private static final Map<Class<?>, Class<?>> BOXES = Map.of(
                boolean.class, Boolean.class, byte.class, Byte.class,
                short.class, Short.class, char.class, Character.class,
                int.class, Integer.class, long.class, Long.class,
                float.class, Float.class, double.class, Double.class);

        private final Class<T> type;
        private final Constructor<T> constructor;
        private final List<Property> properties;
        private final Set<String> names;
        private final boolean record;

        public Mapper(Class<T> type) {
            this.type = Objects.requireNonNull(type, "type");
            if (!Modifier.isPublic(type.getModifiers())
                    || Modifier.isAbstract(type.getModifiers())
                    || type.isEnum() || type.isInterface()
                    || type.isArray() || type.isPrimitive()) {
                throw new IllegalArgumentException(
                        "Unsupported target: " + type.getTypeName());
            }

            record = type.isRecord();
            List<Property> discovered = new ArrayList<>();
            try {
                if (record) {
                    RecordComponent[] components = type.getRecordComponents();
                    Class<?>[] parameterTypes = new Class<?>[components.length];
                    for (int i = 0; i < components.length; i++) {
                        RecordComponent component = components[i];
                        parameterTypes[i] = component.getType();
                        discovered.add(new Property(
                                component.getName(), component.getType(),
                                component.getAccessor(), null));
                    }
                    constructor = type.getConstructor(parameterTypes);
                } else {
                    constructor = type.getConstructor();
                    for (PropertyDescriptor descriptor :
                            Introspector.getBeanInfo(type, Object.class)
                                    .getPropertyDescriptors()) {
                        Method reader = descriptor.getReadMethod();
                        Method writer = descriptor.getWriteMethod();
                        if (descriptor.getPropertyType() == null
                                || reader == null || writer == null
                                || Modifier.isStatic(reader.getModifiers())
                                || Modifier.isStatic(writer.getModifiers())) {
                            throw new IllegalArgumentException(
                                    "Unsupported bean property: "
                                    + descriptor.getName());
                        }
                        discovered.add(new Property(
                                descriptor.getName(),
                                descriptor.getPropertyType(), reader, writer));
                    }
                    discovered.sort(Comparator.comparing(Property::name));
                }
            } catch (ReflectiveOperationException | IntrospectionException ex) {
                throw failure(type.getSimpleName() + ".metadata", ex);
            }
            properties = List.copyOf(discovered);
            names = properties.stream()
                    .map(Property::name)
                    .collect(Collectors.toUnmodifiableSet());
        }

        private Object check(Property property, Object value) {
            Class<?> required = property.type();
            if (value == null) {
                if (required.isPrimitive()) {
                    throw new IllegalArgumentException(
                            "Null primitive: " + property.name());
                }
                return null;
            }
            Class<?> boxed = BOXES.getOrDefault(required, required);
            if (!boxed.isInstance(value)) {
                throw new IllegalArgumentException(
                        "Wrong type for " + property.name()
                        + ": expected " + required.getTypeName());
            }
            return value;
        }

        public T fromMap(Map<String, ?> input) {
            Objects.requireNonNull(input, "input");
            Map<String, ?> source = new LinkedHashMap<>(input);
            for (String name : source.keySet()) {
                if (name == null || !names.contains(name)) {
                    throw new IllegalArgumentException(
                            "Unknown property: " + name);
                }
            }

            Object[] values = new Object[properties.size()];
            for (int i = 0; i < properties.size(); i++) {
                Property property = properties.get(i);
                if (!source.containsKey(property.name())) {
                    throw new IllegalArgumentException(
                            "Missing property: " + property.name());
                }
                values[i] = check(property, source.get(property.name()));
            }

            T result;
            try {
                result = record ? constructor.newInstance(values)
                        : constructor.newInstance();
            } catch (ReflectiveOperationException ex) {
                throw failure(type.getSimpleName() + ".<init>", ex);
            }

            if (!record) {
                for (int i = 0; i < properties.size(); i++) {
                    Property property = properties.get(i);
                    try {
                        property.writer().invoke(result, values[i]);
                    } catch (ReflectiveOperationException ex) {
                        throw failure(type.getSimpleName() + "."
                                + property.name(), ex);
                    }
                }
            }
            return result;
        }

        public Map<String, Object> toMap(T value) {
            Objects.requireNonNull(value, "value");
            type.cast(value);
            Map<String, Object> result = new LinkedHashMap<>();
            for (Property property : properties) {
                try {
                    result.put(property.name(),
                            property.reader().invoke(value));
                } catch (ReflectiveOperationException ex) {
                    throw failure(type.getSimpleName() + "."
                            + property.name(), ex);
                }
            }
            return result;
        }
    }

    static void showFailure(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected a failure: " + label);
        } catch (IllegalArgumentException | MappingException expected) {
            System.out.println(label + ": " + expected.getMessage());
            if (expected.getCause() != null) {
                System.out.println(label + " cause: "
                        + expected.getCause().getClass().getSimpleName());
            }
        }
    }

    public static void main(String[] args) {
        Mapper<StudentRecord> records = new Mapper<>(StudentRecord.class);
        Mapper<StudentBean> beans = new Mapper<>(StudentBean.class);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("age", 20);
        input.put("name", " Linh ");

        StudentRecord student = records.fromMap(input);
        StudentBean bean = beans.fromMap(input);
        System.out.println("record: " + student);
        System.out.println("record map: " + records.toMap(student));
        System.out.println("bean map: " + beans.toMap(bean));
        System.out.println("maps equal: "
                + records.toMap(student).equals(beans.toMap(bean)));

        showFailure("missing", () ->
                records.fromMap(Map.of("name", "Linh")));
        showFailure("unknown", () ->
                records.fromMap(Map.of(
                        "name", "Linh", "age", 20, "extra", true)));
        showFailure("wrong type", () ->
                records.fromMap(Map.of("name", "Linh", "age", "20")));

        Map<String, Object> nullAge = new LinkedHashMap<>(input);
        nullAge.put("age", null);
        showFailure("null primitive", () -> records.fromMap(nullAge));

        showFailure("record validation", () ->
                records.fromMap(Map.of("name", "Linh", "age", -1)));
        showFailure("bean validation", () ->
                beans.fromMap(Map.of("name", "Linh", "age", -1)));
    }
}
~~~

Biên dịch và chạy trên classpath thông thường với JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 DualModelMapperDemo.java
java DualModelMapperDemo
~~~

Kết quả chính xác:

~~~text
record: StudentRecord[name=Linh, age=20]
record map: {name=Linh, age=20}
bean map: {age=20, name=Linh}
maps equal: true
missing: Missing property: age
unknown: Unknown property: extra
wrong type: Wrong type for age: expected int
null primitive: Null primitive: age
record validation: StudentRecord.<init>: IllegalArgumentException
record validation cause: IllegalArgumentException
bean validation: StudentBean.age: IllegalArgumentException
bean validation cause: IllegalArgumentException
~~~

Trong ví dụ, tất cả các kiểu mô hình và phương thức mà mapper gọi đều công khai, truy cập được trong môi trường chạy. Mapper không tự mở truy cập sâu bằng `setAccessible(true)`.

## 7. Đọc thiết kế qua kết quả thực thi

### 7.1. Hai mô hình dùng chung dữ liệu đầu vào

Map được đưa vào theo thứ tự `age`, rồi `name`. Record vẫn được tạo đúng vì đối số constructor được chuẩn bị theo metadata thành phần, không theo thứ tự map.

Cả compact constructor của record và setter `setName` của JavaBean đều loại khoảng trắng ở hai đầu. Kết quả tên là `Linh`, không còn hai khoảng trắng ban đầu.

Điều này chứng minh mapper đi qua hành vi khởi tạo hoặc hành vi gán mà mô hình đã định nghĩa. Nó không bỏ qua logic đó bằng cách ghi thẳng vào trường.

### 7.2. Map xuất ra có thể khác thứ tự mà vẫn bằng nhau

Kết quả:

~~~text
record map: {name=Linh, age=20}
bean map: {age=20, name=Linh}
maps equal: true
~~~

Nhánh record giữ thứ tự thành phần `name, age`. Nhánh JavaBean sắp xếp thuộc tính theo tên `age, name`.

Hai map có cùng cặp khóa/giá trị nên `Map.equals` cho `true`. Không nên đánh đồng thứ tự trình bày với sự tương đương của dữ liệu map.

Nếu giao thức đầu ra yêu cầu một thứ tự chung cho cả hai mô hình, hãy thiết kế quy tắc đầu ra riêng. Không đổi thứ tự đối số canonical constructor để đạt mục đích trình bày.

### 7.3. Khóa thiếu và null phải được phân biệt

Mapper dùng:

~~~java
source.containsKey(property.name())
~~~

trước khi đọc giá trị.

Vì vậy, hai trường hợp sau không bị nhập làm một:

~~~text
Không có khóa age
Có khóa age nhưng giá trị là null
~~~

Ví dụ chọn chính sách nghiêm ngặt:

- Mọi thuộc tính trong kế hoạch đều phải có khóa.
- `null` bị từ chối nếu kiểu đích là primitive.
- `null` có thể qua bước kiểm tra kiểu của mapper nếu kiểu đích là tham chiếu.
- Constructor hoặc setter vẫn có thể từ chối `null` theo quy tắc của mô hình.

Không tự dùng `0` cho một khóa primitive bị thiếu, cũng không tự bỏ qua khóa thiếu ở JavaBean chỉ vì đối tượng mới có một giá trị mặc định.

### 7.4. Kiểu dữ liệu được kiểm tra trước khi tạo đối tượng

`BOXES` ánh xạ kiểu primitive sang wrapper tương ứng, chẳng hạn `int.class -> Integer.class`. Giá trị `Integer` được chấp nhận cho thuộc tính `int`, còn chuỗi `"20"` bị từ chối.

Chính sách trong ví dụ yêu cầu wrapper tương ứng chính xác cho primitive; nó không tự cho `Byte` đi vào `int` dù một số thao tác Reflection có thể hỗ trợ chuyển đổi mở rộng primitive.

Với kiểu tham chiếu, phép kiểm tra dùng `Class.isInstance(...)`. Giá trị là một lớp con hoặc một triển khai interface phù hợp có thể được chấp nhận.

Đây là kiểm tra theo token kiểu sau xóa kiểu. Nó không chứng minh rằng mọi phần tử của một `List<String>` thực sự là `String`. Muốn xử lý generic, cần giữ và phân tích thêm `Type`, rồi xác định chính sách kiểm tra hoặc chuyển đổi dữ liệu ở từng cấp.

## 8. Giữ nguyên nhân lỗi và vị trí xảy ra

Các lỗi đầu vào như khóa thiếu, khóa lạ và sai kiểu được phát hiện trước bước gọi constructor.

Với lỗi phát sinh trong hành vi của mô hình, Reflection thường bọc ngoại lệ bằng `InvocationTargetException`. Hàm `failure(...)` lấy nguyên nhân gốc và giữ nó làm `cause` của `MappingException`.

Do đó, hai lỗi kiểm tra tuổi âm được định vị khác nhau:

~~~text
StudentRecord.<init>: IllegalArgumentException
StudentBean.age: IllegalArgumentException
~~~

Ở record, lỗi xuất phát từ constructor. Ở JavaBean, lỗi xuất phát từ setter thuộc tính `age`.

Thông tin này hữu ích hơn việc chỉ báo “ánh xạ thất bại”. Khi mở rộng sang dữ liệu lồng nhau, đường dẫn lỗi nên có thêm ngữ cảnh, ví dụ `student.address.city` hoặc `students[2].age`.

Cũng cần tách các nhóm:

| Nhóm lỗi | Ví dụ |
| --- | --- |
| Mô hình đích không thuộc hợp đồng | Enum, lớp trừu tượng, JavaBean có thuộc tính chỉ đọc |
| Không lập được kế hoạch | Thiếu constructor phù hợp hoặc lỗi introspection |
| Dữ liệu đầu vào không phù hợp | Thiếu khóa, khóa lạ, sai kiểu, `null` cho primitive |
| Hành vi mô hình từ chối dữ liệu | Compact constructor hoặc setter ném ngoại lệ |
| Điều kiện truy cập không thỏa | Constructor hoặc phương thức không gọi được từ môi trường hiện tại |

Không nuốt nguyên nhân gốc và trả về một đối tượng mặc định có vẻ hợp lệ. Việc đó làm người dùng công cụ khó biết dữ liệu đã bị bỏ qua hay đối tượng chỉ được khởi tạo một phần.

## 9. Kiểm tra trước không có nghĩa thao tác là giao dịch

Ví dụ kiểm tra tên khóa và kiểu của toàn bộ dữ liệu trước khi tạo đối tượng. Nhờ đó, một chuỗi sai kiểu ở thuộc tính cuối không khiến mapper gọi trước các setter của những thuộc tính đầu.

Tuy nhiên, kiểm tra nghiệp vụ vẫn diễn ra trong constructor hoặc setter:

- Constructor có thể có tác động phụ rồi mới ném ngoại lệ.
- Một setter có thể thành công, setter sau đó thất bại.
- Getter dùng khi xuất map cũng có thể chứa logic hoặc ném ngoại lệ.

Mapper không hoàn tác các tác động phụ đó. Với JavaBean, khi setter thất bại, phương thức không trả về đối tượng đang tạo, nhưng điều này không tạo ra một bảo đảm giao dịch cho toàn bộ mã do lớp mô hình thực thi.

Nếu mô hình cần bảo đảm trạng thái hợp lệ ngay từ đầu, chiến lược tạo qua constructor hoặc factory với đầy đủ dữ liệu thường rõ ràng hơn việc gán tuần tự nhiều setter. Điều đó áp dụng cả cho lớp bất biến thông thường, không chỉ riêng record.

Không sửa quy tắc dữ liệu của mô hình để thuận tiện cho mapper. Hãy chọn cách tạo phù hợp với hợp đồng mô hình.

## 10. Các điểm mở rộng nên đặt thành chính sách riêng

### 10.1. Chuyển đổi kiểu và dữ liệu lồng nhau

Có thể bổ sung một bộ chuyển đổi nhận giá trị nguồn, kiểu đích và đường dẫn hiện tại.

Ví dụ các nhiệm vụ cần quyết định riêng:

- Chuỗi sang số: khoảng trắng, dấu, miền giá trị và tràn số.
- Chuỗi sang enum: tên khai báo hay mã nghiệp vụ, chính sách khi không khớp.
- Map sang đối tượng con: đệ quy theo đúng kiểu đích.
- Danh sách phần tử: phân tích `ParameterizedType`, chuyển đổi từng phần tử và giữ chỉ số trong lỗi.
- `Optional`, ngày giờ hoặc kiểu giá trị riêng: cách biểu diễn nguồn cụ thể.

Không chỉ đưa thêm vài nhánh ép kiểu vào đoạn gọi constructor. Khi dữ liệu phức tạp hơn, chuyển đổi phải hoàn tất và được kiểm tra trước khi chuyển đối số cho chiến lược tạo.

### 10.2. Lớp bất biến thông thường và factory

Một lớp thường có constructor nhiều tham số không nên bị coi là “record chưa được nhận diện”. Nó cần một chiến lược tạo khác, có quy tắc xác định constructor và ánh xạ tên tham số.

Có thể dùng cấu hình tường minh, annotation hoặc một factory được đăng ký. Với constructor thông thường, không mặc định tên tham số Reflection luôn là tên miền dữ liệu nếu chương trình không có metadata hoặc cấu hình phù hợp.

Builder và factory tĩnh cũng nên là chiến lược riêng. Không tự thử hàng loạt constructor theo vị trí mảng rồi chọn lần gọi đầu tiên không ném lỗi.

### 10.3. Annotation và tên ánh xạ

Một công cụ thực tế có thể hỗ trợ tên đầu vào khác tên thuộc tính Java hoặc quy tắc kiểm tra dữ liệu bằng annotation.

Khi đó phải xác định rõ đọc annotation ở đâu:

- Record component.
- Trường lưu trữ.
- Accessor.
- Tham số canonical constructor.
- Vị trí sử dụng kiểu.

Đây là những vị trí metadata khác nhau. Không gộp mọi annotation tìm thấy rồi giả định chúng luôn có cùng ý nghĩa hoặc giá trị. Chính sách ưu tiên và xử lý xung đột cần thuộc hợp đồng mapper.

### 10.4. Đối tượng lồng nhau và chu trình

Khi ánh xạ đệ quy, dữ liệu có thể chứa tham chiếu dùng chung hoặc chu trình. Cần xác định có bảo toàn định danh đối tượng hay chỉ sao chép giá trị.

Record cần đủ đối số trước khi gọi canonical constructor, nên không thể mặc định mọi chu trình đều được xử lý bằng cách tạo đối tượng rỗng rồi ghi bù các trường sau. Nếu không hỗ trợ một cấu trúc chu trình, công cụ nên phát hiện và báo rõ theo đường dẫn dữ liệu.

## 11. Kế hoạch metadata, tái sử dụng và giới hạn hiệu năng

`Mapper<T>` trong chương trình khảo sát kiểu một lần khi được tạo. Mỗi lần `fromMap` hoặc `toMap` sử dụng lại danh sách thuộc tính và constructor đã lưu.

Đó là bước tối ưu có cơ sở: không cần introspection lại cùng một kiểu cho mỗi hàng dữ liệu.

Nếu bổ sung cache dùng chung, khóa phải phản ánh đúng đối tượng `Class<?>`, không chỉ tên lớp. Hai lớp cùng tên từ các class loader khác nhau có thể là những kiểu runtime khác nhau. Vòng đời cache cũng cần phù hợp với vòng đời class loader nếu ứng dụng có nạp hoặc tháo plugin.

Không kết luận mapper Reflection nhanh hoặc chậm chỉ từ một lần chạy chương trình mẫu. Chi phí có thể nằm ở khảo sát metadata, chuyển đổi dữ liệu, kiểm tra nghiệp vụ hoặc chính phương thức accessor. Đo trên công việc thực tế trước khi thay cách gọi bằng một cơ chế khác.

Map xuất ra trong ví dụ là bản sao cấu trúc khóa/giá trị ở mức ngoài. Các đối tượng tham chiếu bên trong vẫn có thể là cùng đối tượng mà getter/accessor trả về. Công cụ không tự sao chép sâu hoặc tạo một ảnh chụp nguyên tử của toàn bộ đồ thị dữ liệu.

## 12. Tự kiểm tra

1. Vì sao không dùng cùng thuật toán “tạo rỗng rồi ghi field” cho JavaBean và record?
2. Vì sao map đầu vào trong ví dụ đưa `age` trước `name` nhưng record vẫn được tạo đúng?
3. Canonical constructor có cần được chọn theo constructor đầu tiên trong mảng Reflection không?
4. Vì sao dùng `containsKey` thay vì chỉ kiểm tra `get(...) == null`?
5. Chuỗi `"20"` có được chương trình tự chuyển thành `int` không?
6. Khi tuổi âm, lỗi của record và JavaBean được báo ở các vị trí nào?
7. Vì sao map xuất ra của record và JavaBean khác thứ tự nhưng vẫn bằng nhau?
8. Việc kiểm tra toàn bộ kiểu dữ liệu trước khi tạo có đảm bảo hoàn tác tác động phụ của setter không?
9. Nếu muốn hỗ trợ một lớp bất biến có constructor ba tham số, nên ép nó qua nhánh JavaBean hay bổ sung chiến lược tạo phù hợp?
10. Việc `List.class.isInstance(value)` trả về `true` có chứng minh mọi phần tử là `String` không?

Đáp án:

1. Record cần được tạo qua constructor phù hợp và có các trường thành phần final; JavaBean trong hợp đồng có cách gán qua setter.
2. Giá trị được lấy theo tên rồi xếp vào mảng theo thứ tự record component, không theo thứ tự map.
3. Không. Cần tìm đúng chữ ký từ danh sách kiểu thành phần theo thứ tự.
4. Để phân biệt thiếu khóa với có khóa mang giá trị `null`.
5. Không. Ví dụ yêu cầu kiểu phù hợp và không thực hiện chuyển đổi chuỗi sang số.
6. `StudentRecord.<init>` và `StudentBean.age`; nguyên nhân nghiệp vụ được giữ làm `cause`.
7. Record dùng thứ tự thành phần, JavaBean dùng thứ tự tên; `Map.equals` so sánh các cặp khóa/giá trị.
8. Không. Đó không phải một giao dịch hoàn tác mã đã được mô hình thực thi.
9. Bổ sung chiến lược tạo có ánh xạ constructor hoặc factory rõ ràng.
10. Không. Phép kiểm tra chỉ xác nhận đối tượng là một danh sách theo kiểu đã xóa; kiểm tra phần tử generic là nhiệm vụ riêng.


</details>

- [Quay lại đầu trang](#back-to-top)
