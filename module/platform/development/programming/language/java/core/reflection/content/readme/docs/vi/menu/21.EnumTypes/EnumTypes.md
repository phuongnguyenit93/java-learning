<a id="back-to-top"></a>

# Enum

## Menu
- [isEnum và getEnumConstants](#enum-types-001)
- [Thứ tự hằng enum và trường hợp API trả về null](#enum-types-002)
- [Field.isEnumConstant](#enum-types-003)
- [Enum.valueOf với Class của enum](#enum-types-004)
- [Enum có thân riêng trên hằng và khác biệt getClass với getDeclaringClass](#enum-types-005)
- [Không tạo thêm hằng enum bằng Constructor.newInstance](#enum-types-006)
- [Đọc method và field do compiler bổ sung mà không phụ thuộc tên nội bộ](#enum-types-007)

## <a id="enum-types-001">isEnum và getEnumConstants</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện enum bằng isEnum và lấy các hằng bằng getEnumConstants

## 1. Hai câu hỏi khác nhau khi chỉ có Class

Một enum biểu diễn một tập hằng có tên, chẳng hạn `LOW`, `NORMAL`, `HIGH`. Khi biết tên kiểu ngay trong mã nguồn, ta có thể gọi `Priority.values()` để duyệt các hằng. Nhưng một công cụ tạo biểu mẫu, đọc cấu hình hoặc kiểm tra mô hình dữ liệu có thể chỉ nhận được một `Class<?>` lúc chạy.

Trong trường hợp ấy, hai phương thức của `Class` trả lời hai câu hỏi:

- `isEnum()`: đối tượng `Class` này có mô tả **lớp được khai báo bằng enum** hay không?
- `getEnumConstants()`: các **đối tượng hằng enum thực tế** của lớp đó là những đối tượng nào?

Đừng gộp hai câu hỏi thành một. Nhận diện một lớp là enum khác với lấy các đối tượng hằng của nó. Đặc biệt, bước lấy các hằng có thể làm enum được khởi tạo nếu trước đó chưa được khởi tạo.

Bài học dùng Java 17 trở lên và tập trung vào hai API trên. Các thao tác tìm hằng theo tên, đọc annotation của hằng hoặc truy cập field bên trong enum là những nhu cầu khác.

## 2. Hợp đồng cần nhớ

| API | Kiểu trả về | Ý nghĩa |
| --- | --- | --- |
| `Class.isEnum()` | `boolean` | `true` khi lớp được khai báo là enum |
| `Class<T>.getEnumConstants()` | `T[]` | Mảng hằng theo **thứ tự khai báo**, hoặc `null` nếu lớp không phải enum |

Hai kết quả dễ nhầm:

- **Không phải enum:** `getEnumConstants()` trả `null`.
- **Là enum nhưng không có hằng:** phương thức trả một mảng có độ dài `0`.

Enum rỗng là hợp lệ, ví dụ một khai báo riêng:

~~~java
enum Empty {
}
~~~

Không suy ra “không phải enum” chỉ từ việc không có phần tử. Cần phân biệt mảng rỗng với `null`.

Các trường hợp thường gặp, giả sử đã có khai báo `enum Priority { LOW, NORMAL, HIGH }`:

| Đối tượng Class | isEnum() | getEnumConstants() |
| --- | --- | --- |
| `Priority.class` | `true` | Ba hằng `LOW`, `NORMAL`, `HIGH` |
| `Empty.class` | `true` | Mảng rỗng |
| `String.class` | `false` | `null` |
| `Enum.class` | `false` | `null` |
| `Priority[].class` | `false` | `null` |

`java.lang.Enum` là lớp cơ sở chung của các enum, nhưng bản thân nó không phải một lớp được khai báo bằng cú pháp `enum`. Một mảng chứa enum cũng là kiểu mảng, không phải enum.

## 3. Ví dụ hoàn chỉnh: nhận diện, liệt kê và xử lý enum rỗng

Lưu thành `EnumInspectionDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EnumInspectionDemo {
    enum Priority {
        LOW, NORMAL, HIGH
    }

    enum Empty {
    }

    static List<String> enumNames(Class<?> type) {
        Objects.requireNonNull(type, "type");
        if (!type.isEnum()) {
            throw new IllegalArgumentException("Expected an enum class");
        }

        List<String> names = new ArrayList<>();
        for (Object value : type.getEnumConstants()) {
            Enum<?> constant = (Enum<?>) value;
            names.add(constant.name());
        }
        return List.copyOf(names);
    }

    public static void main(String[] args) {
        Class<?> type = Priority.class;
        Object[] constants = type.getEnumConstants();

        System.out.println("isEnum=" + type.isEnum());
        System.out.println("constants=" + constants.length);
        for (Object value : constants) {
            Enum<?> constant = (Enum<?>) value;
            System.out.println(constant.name() + ":" + constant.ordinal());
        }

        System.out.println("sameFirst=" + (constants[0] == Priority.LOW));

        Priority[] typed = Priority.class.getEnumConstants();
        System.out.println("typedSecond=" + typed[1].name());

        System.out.println("empty.isEnum=" + Empty.class.isEnum());
        System.out.println("empty.length="
                + Empty.class.getEnumConstants().length);
        System.out.println("String.isEnum=" + String.class.isEnum());
        System.out.println("String.constants="
                + String.class.getEnumConstants());
        System.out.println("Enum.isEnum=" + Enum.class.isEnum());
        System.out.println("array.isEnum=" + Priority[].class.isEnum());
        System.out.println("names=" + enumNames(type));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 EnumInspectionDemo.java
java EnumInspectionDemo
~~~

Kết quả chính xác:

~~~text
isEnum=true
constants=3
LOW:0
NORMAL:1
HIGH:2
sameFirst=true
typedSecond=NORMAL
empty.isEnum=true
empty.length=0
String.isEnum=false
String.constants=null
Enum.isEnum=false
array.isEnum=false
names=[LOW, NORMAL, HIGH]
~~~

Những điều chương trình chứng minh:

1. Duyệt bằng `getEnumConstants()` giữ thứ tự `LOW`, `NORMAL`, `HIGH`, đúng thứ tự khai báo.
2. Phần tử đầu tiên chính là `Priority.LOW`, không phải một bản sao mới của hằng ấy.
3. Với `Class<Priority>`, kết quả được định kiểu thành `Priority[]`. Với `Class<?>`, có thể duyệt qua `Object[]` rồi xử lý theo hợp đồng enum.
4. Enum rỗng vẫn nhận diện được bằng `isEnum()`.
5. Hàm `enumNames` từ chối đầu vào không phải enum, thay vì biến đầu vào sai thành một danh sách rỗng khó phân biệt.

Phép ép sang `Enum<?>` trong `enumNames` dựa trên điều kiện đã kiểm tra: `type` là lớp enum và các phần tử được lấy trực tiếp từ `type.getEnumConstants()`. Không áp dụng phép ép ấy cho một mảng `Object[]` bất kỳ.

Trong `main`, `type` được gán trực tiếp từ `Priority.class`, nên chương trình biết chắc kết quả khác `null`. Nếu nhận `Class<?>` từ nơi khác, cần kiểm tra như hàm `enumNames` trước khi duyệt.

## 4. Thứ tự khai báo, tên và nhãn

`getEnumConstants()` trả các hằng theo **thứ tự khai báo**. `ordinal()` là vị trí bắt đầu từ 0; `name()` trả tên khai báo, còn `toString()` có thể bị ghi đè để tạo nhãn hiển thị. Vì thế, công cụ cần khóa tên chính xác nên dùng `name()` và công cụ cần thứ tự khác phải tự sắp xếp kết quả để hiển thị.

Không lưu `ordinal()` như mã nghiệp vụ bền vững: thêm hoặc đổi chỗ hằng có thể thay đổi các vị trí. Sắp xếp mảng `getEnumConstants()` cục bộ cũng không thay đổi thứ tự thực của enum. Bài tiếp theo kiểm tra chi tiết việc sắp xếp, thứ tự và sự khác nhau giữa mảng rỗng với `null`.

## 5. Khi hằng enum có thân lớp riêng

Hằng enum có thể khai báo một thân lớp riêng để ghi đè hành vi; lớp thực tế của đối tượng lúc đó là **lớp con ẩn danh**, nên `constant.getClass().isEnum()` có thể trả `false`. Điều này không phủ nhận việc đối tượng là một hằng enum. Khi đã có `Enum<?>`, dùng `constant.getDeclaringClass()` để lấy **lớp khai báo enum** rồi mới gọi `getEnumConstants()`.

Ví dụ độc lập về sự khác biệt:

~~~java
enum Mode {
    FAST {
        @Override public String toString() { return "Fast mode"; }
    },
    SAFE
}

Enum<?> constant = Mode.FAST;
System.out.println(constant.getClass().isEnum());          // false
System.out.println(constant.getDeclaringClass().isEnum()); // true
System.out.println(constant.getDeclaringClass() == Mode.class); // true
~~~

Các câu lệnh sau khai báo `Mode` là đoạn minh họa phải đặt trong phương thức hoặc khối thích hợp; trường hợp `SAFE` không có thân riêng sẽ có lớp thực tế là `Mode`. Nếu nhận `Object value` bất kỳ, trước tiên kiểm tra `value instanceof Enum<?> constant` để nhận diện cả các hằng có thân riêng.

Muốn khảo sát phần thực hiện riêng của hằng, `getClass()` vẫn hữu ích. Muốn phân loại và liệt kê **kiểu enum chung**, dùng `getDeclaringClass()`; bài riêng về thân lớp của hằng phân tích thêm quan hệ này và sự khác biệt giữa `Enum.getDeclaringClass()` với `Class.getDeclaringClass()`.

## 6. Lấy các hằng có thể khởi tạo enum

`isEnum()` là bước phân loại kiểu. `getEnumConstants()` phải cung cấp các hằng thực tế; nếu enum chưa được khởi tạo, việc lấy các hằng có thể làm chạy quá trình khởi tạo, gồm việc tạo các hằng và thực hiện khối `static`.

Lưu thành `EnumInitializationDemo.java`:

~~~java
public class EnumInitializationDemo {
    enum Level {
        FIRST, SECOND;

        static {
            System.out.println("Level initialized");
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        Class<?> type = Class.forName(
                "EnumInitializationDemo$Level",
                false,
                EnumInitializationDemo.class.getClassLoader());

        System.out.println("isEnum=" + type.isEnum());
        System.out.println("before constants");
        Object[] constants = type.getEnumConstants();
        System.out.println("count=" + constants.length);
    }
}
~~~

Biên dịch và chạy trong một tiến trình mới:

~~~bash
javac --release 17 EnumInitializationDemo.java
java EnumInitializationDemo
~~~

Kết quả:

~~~text
isEnum=true
before constants
Level initialized
count=2
~~~

Đối số `false` của `Class.forName` trong ví dụ yêu cầu lấy lớp mà chưa khởi tạo nó. Tên có `$` ở đây là tên nhị phân chính xác của enum lồng trong lớp minh họa, không phải thuật toán suy ra tên enum từ một hằng.

Dòng `Level initialized` xuất hiện khi lấy các hằng. Vì vậy, một công cụ chỉ muốn phân loại kiểu có thể dừng ở `isEnum()`; nếu cần danh sách đối tượng hằng thì phải chấp nhận bước lấy các giá trị có ngữ nghĩa khởi tạo.

Nếu enum đã được khởi tạo thành công trước đó, lấy lại các hằng không có nghĩa là tạo lại từng hằng hoặc chạy lại khối `static` cho mỗi lần gọi. Nếu quá trình khởi tạo thất bại, chương trình có thể nhận lỗi khởi tạo; đừng đồng nhất lỗi ấy với kết quả `null` dành cho lớp không phải enum.

Ví dụ này cũng cho thấy không nên mô tả mọi API Reflection có tên bắt đầu bằng `get` là hoàn toàn không gây tác dụng phụ.

## 7. Chọn đúng API cho từng tình huống

**Đã biết kiểu cụ thể trong mã nguồn:** gọi `Priority.values()` để duyệt trực tiếp. Không cần tìm phương thức `values` bằng Reflection rồi gọi `Method.invoke`.

**Chỉ có Class lúc chạy:** dùng `isEnum()` và `getEnumConstants()`. Đây là trường hợp một bộ tạo biểu mẫu nhận kiểu của thuộc tính nhưng không biết trước enum nào được dùng.

**Đã có một hằng:** dùng `getDeclaringClass()` để chuẩn hóa về lớp enum, nhất là khi hằng có thân lớp riêng.

**Cần tên các lựa chọn:** chuyển từng hằng bằng `Enum.name()` nếu cần tên khai báo; tạo nhãn hiển thị theo quy tắc riêng nếu ứng dụng yêu cầu bản địa hóa hoặc nhãn dễ đọc.

**Cần metadata của field khai báo hằng:** đó là công việc khác với lấy giá trị. `getDeclaredFields()` có thể bao gồm cả field thông thường và field do trình biên dịch sinh ra; không thay thế việc gọi `getEnumConstants()` bằng cách duyệt mọi field rồi cho rằng đó đều là hằng enum.

Kết quả của hai API này cũng không cho biết mọi lựa chọn đều được phép trong mọi tình huống nghiệp vụ. Ví dụ một trạng thái có trong enum chưa chắc là trạng thái được phép chuyển đến từ trạng thái hiện tại. Bước liệt kê kiểu và bước kiểm tra quy tắc nghiệp vụ cần được phân biệt.

## 8. Các lỗi cần tránh

| Sai lầm | Hậu quả hoặc cách sửa |
| --- | --- |
| Gọi `getEnumConstants().length` trên một Class chưa kiểm tra | Có thể gặp `NullPointerException` khi lớp không phải enum |
| Coi mảng rỗng là bằng chứng không phải enum | Bỏ sót enum không có hằng |
| Cho rằng `Enum.class.isEnum()` phải là `true` | Nhầm lớp cơ sở với lớp enum khai báo |
| Dùng `constant.getClass().isEnum()` cho mọi hằng | Bỏ sót hằng có thân lớp riêng; lấy `getDeclaringClass()` |
| Dùng `toString()` làm tên khai báo | Có thể nhận chuỗi đã được ghi đè |
| Dùng `ordinal()` làm mã ổn định mặc định | Mã có thể thay đổi khi thứ tự khai báo đổi |
| Cho rằng lấy hằng chỉ là đọc metadata | Bỏ qua việc khởi tạo enum |
| Ép mọi kết quả sang một enum cụ thể như `Priority[]` | Có thể sai kiểu khi Class đầu vào là enum khác |
| Duyệt mọi field để lấy danh sách hằng | Lẫn field khác và mất hợp đồng thứ tự của `getEnumConstants()` |

Nếu hàm của bạn nhận `Class<?>`, nên quyết định rõ hợp đồng: từ chối lớp không phải enum, hay trả một kết quả có trạng thái phân biệt được “không phải enum” với “enum rỗng”. Không âm thầm gộp hai trường hợp nếu mã sử dụng cần phân biệt chúng.

## 9. Tự kiểm tra

1. `Empty.class.isEnum()` là `true` hay `false` nếu enum không có hằng?
2. `String.class.getEnumConstants()` trả mảng rỗng hay `null`?
3. Khi một hằng ghi đè `toString()` trong thân lớp riêng, vì sao `getClass().isEnum()` có thể là `false`?
4. Muốn lấy cả hai hằng `FAST`, `SAFE` khi đang giữ `Mode.FAST`, nên bắt đầu bằng API nào?
5. Lấy danh sách hằng có bảo đảm không chạy mã khởi tạo hay không?

Đáp án:

1. `true`; enum rỗng vẫn là enum, còn mảng hằng có độ dài 0.
2. `null`, vì `String` không phải lớp enum.
3. Lớp thực tế lúc ấy là lớp con ẩn danh của enum, không phải lớp enum khai báo.
4. `Mode.FAST.getDeclaringClass()`, sau đó gọi `getEnumConstants()` trên lớp nhận được.
5. Không. Nếu chưa khởi tạo enum, bước lấy các hằng có thể kích hoạt việc khởi tạo.

Hãy giữ riêng ba ý: lớp enum được khai báo, lớp thực tế của một hằng và tập các đối tượng hằng. `isEnum()`, `getDeclaringClass()` và `getEnumConstants()` giải quyết ba phần ấy với những hợp đồng khác nhau.

## Tài liệu đối chiếu

- [Java 17 — Class.isEnum](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#isEnum()).
- [Java 17 — Class.getEnumConstants](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getEnumConstants()).
- [Java 17 — Enum và getDeclaringClass](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Enum.html#getDeclaringClass()).
- [JLS 17 — Enum, thân lớp riêng của hằng và values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.9).
- [JLS 17 — Khởi tạo lớp và interface](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enum-types-002">Thứ tự hằng enum và trường hợp API trả về null</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự hằng enum và khi `getEnumConstants()` trả về `null`

Khi một công cụ chỉ nhận được `Class<?>` lúc chạy, nó có thể cần biết kiểu đó có phải enum không và nếu có thì gồm những hằng nào. Ví dụ, công cụ tạo biểu mẫu cần liệt kê lựa chọn, còn bộ kiểm tra cấu hình cần biết các giá trị hợp lệ.

`Class.getEnumConstants()` giải quyết việc liệt kê này mà không cần biết tên enum khi viết công cụ. Có hai quy tắc cần nắm: **các hằng được trả về theo thứ tự khai báo**; **`null` khác với một mảng enum rỗng**. Ngoài ra, lớp thực tế của một hằng enum có phần thân riêng có thể khác lớp khai báo enum.

## 1. Hiểu đúng giá trị trả về

Chữ ký API trên `Class<T>` là:

```java
public T[] getEnumConstants()
```

Đây là chữ ký để tra cứu API, không phải một phương thức cần tự cài đặt. Nếu biến có kiểu `Class<Stage>`, lời gọi trả kiểu `Stage[]`. Nếu chỉ có `Class<?>`, có thể nhận kết quả vào `Object[]` rồi kiểm tra trước khi sử dụng.

Theo [hợp đồng của `Class.getEnumConstants()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getEnumConstants()), kết quả là các hằng theo thứ tự khai báo, hoặc `null` khi đối tượng `Class` không đại diện cho một khai báo enum.

| Kiểu được truy vấn | Kết quả | Ý nghĩa |
| --- | --- | --- |
| Enum có hằng | Mảng chứa các hằng | Có tập lựa chọn để duyệt |
| Enum không có hằng | Mảng độ dài `0` | Là enum, nhưng tập hằng rỗng |
| Lớp thông thường, interface, kiểu nguyên thủy hoặc kiểu mảng | `null` | Kiểu đang xét không phải khai báo enum |

Không gọi `.length`, dùng vòng `for` hoặc tạo luồng từ kết quả trước khi phân biệt các trường hợp này. Nếu nhận `null`, bạn chưa có một mảng để duyệt.

## 2. Thứ tự khai báo không phải thứ tự tên

Giả sử enum được khai báo như sau:

```java
enum Stage {
    REVIEW, DRAFT, PUBLISHED
}
```

Kết quả duyệt là `REVIEW`, `DRAFT`, `PUBLISHED`, không phải thứ tự bảng chữ cái. `Stage.values()` cũng trả các hằng theo thứ tự khai báo; phương thức này được khai báo ngầm cho từng enum theo [JLS về thành viên enum](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.9.3).

Mỗi hằng có `ordinal()` bắt đầu từ `0`, tương ứng vị trí khai báo. `name()` cho tên khai báo; `toString()` có thể được ghi đè để tạo nhãn hiển thị. Đó là những vai trò khác nhau trong [API `Enum`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Enum.html).

Trong ví dụ trên, `REVIEW.ordinal()` là `0` dù từ `DRAFT` đứng trước nó khi sắp xếp tên. Vì vậy, cần xác định bạn muốn bảo toàn thứ tự khai báo hay muốn tạo một thứ tự hiển thị riêng.

## 3. Thí nghiệm Java 17+ chạy độc lập

Chương trình sau kiểm tra thứ tự, enum rỗng, kiểu không phải enum, lớp riêng của hằng và cách từ chối đầu vào không phù hợp.

Lưu toàn bộ vào **`EnumConstantsDemo.java`**:

```java
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

public class EnumConstantsDemo {
    enum Stage {
        REVIEW, DRAFT, PUBLISHED
    }

    enum Empty {
    }

    enum Flavor {
        BASIC,
        CUSTOM {
            @Override
            public String toString() {
                return "Custom label";
            }
        }
    }

    private static String names(Class<?> type) {
        Object[] constants = type.getEnumConstants();
        if (constants == null) {
            return "null";
        }
        return Arrays.stream(constants)
                .map(value -> ((Enum<?>) value).name())
                .toList()
                .toString();
    }

    private static Object[] requireEnumConstants(Class<?> type) {
        Objects.requireNonNull(type, "type");
        Object[] constants = type.getEnumConstants();
        if (constants == null) {
            throw new IllegalArgumentException(
                    type.getTypeName() + " is not an enum declaration");
        }
        return constants;
    }

    public static void main(String[] args) {
        System.out.println("Stage constants = " + names(Stage.class));
        System.out.println("Stage values = " + Arrays.toString(Stage.values()));
        for (Stage stage : Stage.class.getEnumConstants()) {
            System.out.println(stage.name() + " ordinal = " + stage.ordinal());
        }

        Stage[] sorted = Stage.class.getEnumConstants();
        Arrays.sort(sorted, Comparator.comparing(Stage::name));
        System.out.println("Sorted by name = " + Arrays.toString(sorted));
        System.out.println("Declaration order = " + names(Stage.class));

        System.out.println("Empty is enum = " + Empty.class.isEnum());
        System.out.println("Empty constants = " + names(Empty.class));
        System.out.println("Empty count = " + Empty.class.getEnumConstants().length);

        System.out.println("String constants = " + names(String.class));
        System.out.println("Enum constants = " + names(Enum.class));
        System.out.println("Array constants = " + names(Stage[].class));
        System.out.println("int constants = " + names(int.class));

        Enum<?> selected = Flavor.CUSTOM;
        System.out.println("CUSTOM instanceof Enum = " + (selected instanceof Enum<?>));
        System.out.println("BASIC runtime is enum = " + Flavor.BASIC.getClass().isEnum());
        System.out.println("CUSTOM runtime is enum = " + selected.getClass().isEnum());
        System.out.println("CUSTOM runtime constants = " + names(selected.getClass()));
        System.out.println("CUSTOM declaring is Flavor = "
                + (selected.getDeclaringClass() == Flavor.class));
        System.out.println("CUSTOM declaring constants = "
                + names(selected.getDeclaringClass()));
        System.out.println("CUSTOM name = " + selected.name());
        System.out.println("CUSTOM display = " + selected);

        System.out.println("Required Stage count = "
                + requireEnumConstants(Stage.class).length);
        System.out.println("Required Empty count = "
                + requireEnumConstants(Empty.class).length);
        try {
            requireEnumConstants(String.class);
        } catch (IllegalArgumentException ex) {
            System.out.println("Required String = " + ex.getClass().getSimpleName());
        }
        try {
            Enum.valueOf(Stage.class, "MISSING");
        } catch (IllegalArgumentException ex) {
            System.out.println("Missing name = " + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 EnumConstantsDemo.java
java EnumConstantsDemo
```

Kết quả:

```text
Stage constants = [REVIEW, DRAFT, PUBLISHED]
Stage values = [REVIEW, DRAFT, PUBLISHED]
REVIEW ordinal = 0
DRAFT ordinal = 1
PUBLISHED ordinal = 2
Sorted by name = [DRAFT, PUBLISHED, REVIEW]
Declaration order = [REVIEW, DRAFT, PUBLISHED]
Empty is enum = true
Empty constants = []
Empty count = 0
String constants = null
Enum constants = null
Array constants = null
int constants = null
CUSTOM instanceof Enum = true
BASIC runtime is enum = true
CUSTOM runtime is enum = false
CUSTOM runtime constants = null
CUSTOM declaring is Flavor = true
CUSTOM declaring constants = [BASIC, CUSTOM]
CUSTOM name = CUSTOM
CUSTOM display = Custom label
Required Stage count = 3
Required Empty count = 0
Required String = IllegalArgumentException
Missing name = IllegalArgumentException
```

`names()` dùng `name()` để in tên hằng, tránh nhầm với nhãn `toString()` đã được ghi đè. Phép ép về `Enum<?>` chỉ thực hiện sau khi `getEnumConstants()` đã cho mảng không `null`; các phần tử lúc đó là các hằng enum.

## 4. Đọc kết quả và áp dụng đúng

### Bảo toàn hoặc thay đổi thứ tự một cách có chủ đích

`Stage constants` và `Stage values` cùng in `[REVIEW, DRAFT, PUBLISHED]`. Chương trình sau đó lấy mảng vào biến `sorted` và sắp xếp theo `Stage::name`, tạo `[DRAFT, PUBLISHED, REVIEW]`.

Lần đọc lại bằng `getEnumConstants()` vẫn cho thứ tự khai báo ban đầu. Việc sắp xếp mảng cục bộ không đổi khai báo enum, không thay hằng `REVIEW` thành một hằng khác và không đổi `ordinal()` của nó.

Nếu thứ tự khai báo đã là thứ tự mong muốn của biểu mẫu, hãy duyệt trực tiếp kết quả. Nếu giao diện cần một thứ tự khác, biểu diễn quy tắc đó rõ ràng bằng bộ so sánh hoặc thuộc tính thứ tự riêng.

Không dùng thứ tự của `getDeclaredFields()` để thay cho hợp đồng thứ tự của enum. Mảng trường dữ liệu có mục đích mô tả thành viên, còn bài toán ở đây cần danh sách **giá trị hằng enum**.

### Enum rỗng là đầu vào hợp lệ

`Empty.class.isEnum()` cho `true`, còn `Empty.class.getEnumConstants()` cho `[]` với độ dài `0`. Enum không bắt buộc phải có ít nhất một hằng; cú pháp thân enum cho phép không có danh sách hằng.

Với một công cụ chọn giá trị, kết quả này có thể có nghĩa là không có lựa chọn nào để hiển thị. Nó không có nghĩa kiểu đầu vào sai. Đây là lý do không nên gộp `null` với mảng rỗng ngay từ đầu.

### Tại sao `Enum.class`, kiểu mảng và kiểu nguyên thủy cho `null`?

Trong thí nghiệm, `String.class`, `Enum.class`, `Stage[].class` và `int.class` đều cho `null`.

- `String` là một lớp thông thường.
- `Enum` là lớp cơ sở chung, không phải một khai báo `enum` chứa tập hằng riêng.
- `Stage[]` là kiểu mảng, dù kiểu phần tử của nó là enum.
- `int` là kiểu nguyên thủy.

Nếu đang xử lý kiểu mảng và thực sự muốn tìm hằng của kiểu phần tử, cần xác định kiểu phần tử rồi kiểm tra nó. Không nên xem mọi kiểu chứa enum như chính enum.

## 5. Lớp riêng của hằng không phải khai báo enum

Trong thí nghiệm, `Flavor.CUSTOM` có thân lớp riêng ghi đè `toString()` nên `selected.getClass()` là một lớp con ẩn danh. Vì vậy, `selected.getClass().isEnum()` là `false`, và `getEnumConstants()` trên lớp con đó trả `null`. Trái lại, `selected.getDeclaringClass() == Flavor.class`, và gọi `getEnumConstants()` trên `Flavor.class` trả cả `BASIC` lẫn `CUSTOM`.

Khi cầm một hằng `Enum<?>`, dùng `getDeclaringClass()` để tìm tập hằng cùng kiểu. Trường hợp này củng cố việc **`null` phụ thuộc Class đang được hỏi**, không đồng nghĩa một hằng có thân riêng đã biến mất khỏi enum. Bài về `getClass` và `getDeclaringClass` phân tích chi tiết cơ chế lớp con ẩn danh và cách chuẩn hóa khóa kiểu.

## 6. Xử lý `null` theo hợp đồng của công cụ

Hai cách xử lý sau phục vụ hai nhu cầu khác nhau.

**Công cụ khám phá nhiều loại kiểu:** `names()` chấp nhận `Class<?>` bất kỳ. Nó kiểm tra `constants == null` và báo rằng kiểu không phải khai báo enum; mảng rỗng vẫn được trình bày thành `[]`.

**Công cụ chỉ chấp nhận enum:** `requireEnumConstants()` trong ví dụ từ chối kiểu khác bằng `IllegalArgumentException`. Tên hàm và lỗi thể hiện rõ điều kiện sử dụng. `Empty.class` vẫn được chấp nhận và trả số hằng bằng `0`.

Không tự chuyển mọi kết quả `null` thành mảng rỗng nếu chương trình cần phân biệt đầu vào sai với enum hợp lệ không có hằng. Nếu cố ý chuẩn hóa chúng, hãy ghi rõ quyết định đó trong hợp đồng của chính công cụ.

Cũng cần phân biệt **phương thức trả `null`** với **biến nhận lời gọi là `null`**. Nếu biến `Class<?> type` là `null`, biểu thức `type.getEnumConstants()` ném `NullPointerException` ngay khi gọi; nó không trả một kết quả để bạn kiểm tra. Vì thế hàm mẫu dùng `Objects.requireNonNull(type, "type")` cho đầu vào.

### Không nhầm với tra cứu một tên hằng

`getEnumConstants()` liệt kê tập hằng; `Enum.valueOf(Stage.class, "MISSING")` tìm một tên cụ thể. Lời gọi thứ hai trong thí nghiệm ném `IllegalArgumentException` vì không có tên đó, không trả `null`.

Tên được tra cứu phải khớp chính xác, kể cả hoa thường và không có khoảng trắng thừa. Đó là hợp đồng riêng của [API `Enum.valueOf`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Enum.html#valueOf(java.lang.Class,java.lang.String)), không được suy ra từ cách `getEnumConstants()` xử lý kiểu không phải enum.

## 7. Lưu ý về tên, số thứ tự và khả năng bảo trì

`ordinal()` mô tả vị trí khai báo tại phiên bản hiện tại. Nếu thêm một hằng ở đầu hoặc đổi vị trí, số thứ tự của các hằng phía sau thay đổi. Vì vậy, dùng nó làm mã lưu trữ lâu dài có thể khiến cùng một số được hiểu thành giá trị khác sau khi cập nhật chương trình.

Ví dụ, với `[REVIEW, DRAFT, PUBLISHED]`, số `0` trỏ đến `REVIEW`. Nếu sau này chuyển `DRAFT` lên đầu, số `0` lại trỏ đến `DRAFT`. Khi cần mã nghiệp vụ ổn định, hãy thiết kế mã riêng và quy tắc tra cứu tương ứng.

`name()` giữ tên khai báo, còn `toString()` trong thí nghiệm biến `CUSTOM` thành `Custom label`. Sự thay đổi nhãn này không đổi tên hằng, vị trí khai báo hay lớp enum sở hữu nó. Công cụ nên chọn rõ đang cần tên kỹ thuật hay nội dung hiển thị.

## 8. Lỗi thường gặp và bài tự kiểm tra

| Lỗi | Cách tránh |
| --- | --- |
| Dùng ngay `.length` mà chưa kiểm tra `null` | Phân loại kết quả trước khi duyệt |
| Xem enum rỗng là kiểu không phải enum | Phân biệt `[]` với `null` |
| Giả định thứ tự là bảng chữ cái | Dựa vào thứ tự khai báo hoặc sắp xếp tường minh |
| Kiểm tra enum chỉ bằng việc đối tượng kế thừa `Enum` | Khi cần tập hằng, xác định đúng lớp khai báo enum |
| Dùng `constant.getClass()` cho mọi hằng | Với một hằng enum, dùng `getDeclaringClass()` để tìm enum sở hữu |
| Xem `toString()` như tên hằng luôn cố định | Dùng `name()` khi cần đúng tên khai báo |
| Lưu `ordinal()` như một mã nghiệp vụ ổn định | Dùng mã riêng khi cần tính ổn định qua thay đổi |
| Nghĩ tên không có trong `valueOf` sẽ trả `null` | Xử lý `IllegalArgumentException` theo hợp đồng tra cứu |

Hãy thử các thay đổi sau và dự đoán trước khi chạy:

1. Đưa `PUBLISHED` lên đầu `Stage`: kết quả liệt kê và `ordinal()` thay đổi thế nào?
2. Thêm một hằng vào `Empty`: sự khác biệt giữa mảng rỗng và `null` còn được xác định bằng điều gì?
3. Bỏ phần thân riêng của `CUSTOM`: vì sao kết quả từ `getClass()` có thể thay đổi?
4. Đổi nhãn `Custom label`: `name()`, `getDeclaringClass()` và thứ tự hằng có đổi không?

Sau bài này, bạn cần tự xác định được: đang nhận một kiểu hay một giá trị enum, kiểu đó có phải chính khai báo enum không, và công cụ của mình muốn bảo toàn thứ tự khai báo hay tạo thứ tự khác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enum-types-003">Field.isEnumConstant</a>

<details>
<summary>Xem chi tiết</summary>

# Field.isEnumConstant: nhận diện trường biểu diễn hằng enum

## 1. Một enum có nhiều loại trường

Một lớp enum không chỉ có các hằng được liệt kê ở đầu phần khai báo. Nó còn có thể có trường dữ liệu của từng đối tượng, trường tĩnh do người viết bổ sung và trường hỗ trợ do trình biên dịch sinh ra.

Vì vậy, khi duyệt `getDeclaredFields()`, câu hỏi “trường này có thuộc một enum không?” chưa đủ. Công cụ sinh tài liệu hoặc khảo sát metadata thường cần câu hỏi chính xác hơn: **trường này có biểu diễn một hằng enum được khai báo hay không?**

`Field.isEnumConstant()` trả lời câu hỏi đó:

~~~java
// Phương thức của java.lang.reflect.Field:
public boolean isEnumConstant();
~~~

- Trả về `true` nếu trường biểu diễn một hằng enum.
- Trả về `false` với các trường khác.
- Kết quả là `boolean`, không phải đối tượng enum và không phải giá trị đang được lưu trong trường.

Định nghĩa API có trong tài liệu [Field.isEnumConstant](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html#isEnumConstant()).

Điểm cần giữ rõ ngay từ đầu: đây là phép phân loại **khai báo trường**, không phải phép kiểm tra “giá trị của trường có phải đối tượng enum hay không”.

## 2. Vì sao hằng enum được nhìn thấy như Field?

Xét khai báo:

~~~java
enum Priority {
    LOW, HIGH;

    static final Priority DEFAULT = LOW;
}
~~~

`LOW` và `HIGH` là các hằng enum. Java có các trường tương ứng tên `LOW` và `HIGH`, kiểu khai báo `Priority`, với các thuộc tính `public static final` được khai báo ngầm.

`DEFAULT` cũng là trường kiểu `Priority`, nhưng do người viết khai báo sau dấu chấm phẩy. Nó giữ tham chiếu đến `LOW`; nó không tạo thêm một hằng trong tập `LOW, HIGH`.

Có thể hình dung:

~~~text
Khai báo hằng LOW
    -> trường LOW biểu diễn hằng enum
    -> isEnumConstant() = true

Khai báo trường DEFAULT = LOW
    -> trường thông thường tham chiếu đến một hằng đã có
    -> isEnumConstant() = false
~~~

Quy tắc các trường ngầm định tương ứng hằng enum được mô tả tại [JLS §8.9.3](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.9.3).

Không nên đồng nhất “hằng enum” với thuật ngữ **biến hằng (constant variable)** trong các quy tắc biểu thức hằng của Java. Theo nghĩa này, biến hằng là biến `final` có kiểu nguyên thủy hoặc `String`, được khởi tạo bằng biểu thức hằng. Trường kiểu enum không thuộc nhóm đó; chỉ nhìn `final` không xác định nó là hằng enum.

## 3. Ví dụ đầy đủ: hằng enum, bí danh và dữ liệu thường

Tạo tệp `EnumConstantFieldDemo.java`:

~~~java
import java.lang.reflect.Field;
import java.util.Arrays;

public class EnumConstantFieldDemo {
    public enum Status {
        NEW(10),
        DONE(20) {
            @Override
            public String toString() {
                return "finished";
            }
        };

        public static final Status DEFAULT = NEW;
        public static final String LABEL = "status";
        private final int code;

        Status(int code) {
            this.code = code;
        }
    }

    static void inspect(String name) throws NoSuchFieldException {
        Field field = Status.class.getDeclaredField(name);
        System.out.println(name
                + " -> enumConstant=" + field.isEnumConstant()
                + ", synthetic=" + field.isSynthetic()
                + ", type=" + field.getType().getSimpleName());
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        inspect("NEW");
        inspect("DONE");
        inspect("DEFAULT");
        inspect("LABEL");
        inspect("code");

        System.out.println("constant fields (name order): "
                + Arrays.stream(Status.class.getDeclaredFields())
                        .filter(Field::isEnumConstant)
                        .map(Field::getName)
                        .sorted()
                        .toList());

        System.out.println("constants (declaration order): "
                + Arrays.stream(Status.class.getEnumConstants())
                        .map(Status::name)
                        .toList());

        Field newField = Status.class.getDeclaredField("NEW");
        Field aliasField = Status.class.getDeclaredField("DEFAULT");
        System.out.println("NEW and DEFAULT same object: "
                + (newField.get(null) == aliasField.get(null)));

        Status done = Status.DONE;
        System.out.println("DONE runtime class is enum: "
                + done.getClass().isEnum());
        System.out.println("DONE declaring enum: "
                + (done.getDeclaringClass() == Status.class));
        System.out.println("DONE name: " + done.name());
        System.out.println("DONE display: " + done);
    }
}
~~~

Chương trình dùng Java 17 trở lên. Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 EnumConstantFieldDemo.java
java EnumConstantFieldDemo
~~~

Kết quả chính xác:

~~~text
NEW -> enumConstant=true, synthetic=false, type=Status
DONE -> enumConstant=true, synthetic=false, type=Status
DEFAULT -> enumConstant=false, synthetic=false, type=Status
LABEL -> enumConstant=false, synthetic=false, type=String
code -> enumConstant=false, synthetic=false, type=int
constant fields (name order): [DONE, NEW]
constants (declaration order): [NEW, DONE]
NEW and DEFAULT same object: true
DONE runtime class is enum: false
DONE declaring enum: true
DONE name: DONE
DONE display: finished
~~~

Các tên trường trong `inspect(...)` được chỉ định rõ để kết quả không phụ thuộc thứ tự trả về của `getDeclaredFields()`. Khi cần in danh sách trường hằng, chương trình chủ động sắp xếp theo tên.

### 3.1. NEW và DONE là hằng enum

Hai hằng được khai báo trước dấu chấm phẩy trong `Status`. Vì vậy, hai trường tương ứng đều có `isEnumConstant() == true`.

`DONE` có phần thân riêng để ghi đè `toString()`. Điều đó không làm mất vai trò hằng enum của trường `Status.DONE`.

### 3.2. DEFAULT là bí danh tham chiếu

`DEFAULT` được khai báo như sau:

~~~java
public static final Status DEFAULT = NEW;
~~~

Nó có cùng kiểu khai báo với trường `NEW`, và giá trị của nó là cùng đối tượng với `Status.NEW`. Tuy nhiên:

- Trường `NEW` biểu diễn hằng enum `NEW`.
- Trường `DEFAULT` là trường tĩnh thông thường tham chiếu đến đối tượng đó.
- `DEFAULT` không làm tăng số hằng enum và không xuất hiện trong `Status.values()`.

Đây là lý do dòng `NEW and DEFAULT same object: true` có thể xuất hiện cùng với `DEFAULT -> enumConstant=false`. Hai kết quả nói về hai khía cạnh khác nhau, không mâu thuẫn.

### 3.3. LABEL và code đều không phải hằng enum

`LABEL` là trường tĩnh chứa một chuỗi. `code` là trường dữ liệu riêng của mỗi đối tượng `Status`. Cả hai đều được khai báo bên trong enum, nhưng đều có `isEnumConstant() == false`.

Nói “mọi field của enum đều là enum constant” sẽ khiến công cụ nhầm cả trường dữ liệu lẫn trường cấu hình vào danh sách lựa chọn.

## 4. Không thay API bằng các phép đoán

| Cách kiểm tra | Vì sao chưa đủ? |
| --- | --- |
| Tên trường viết hoa | Chỉ là quy ước đặt tên; `DEFAULT` và `LABEL` cũng viết hoa |
| Trường có `static final` | Trường thông thường cũng có hai modifier này |
| Trường là `public static final` | Bí danh `DEFAULT` trong ví dụ đáp ứng nhưng không phải hằng enum |
| `field.getType().isEnum()` | Trường `DEFAULT` có kiểu `Status` và vẫn cho kết quả `false` khi hỏi `isEnumConstant()` |
| `field.getDeclaringClass().isEnum()` | Cả `code` và `LABEL` đều được khai báo trong `Status` |
| Giá trị đọc ra là `instanceof Enum<?>` | Giá trị của bí danh cũng là đối tượng enum; lại phải đọc giá trị |
| `!field.isSynthetic()` | Nhiều trường do người viết khai báo không synthetic nhưng cũng không phải hằng enum |

Ở cấp class file, trường biểu diễn hằng enum có cờ `ACC_ENUM`. Đây là thông tin phân loại được chuẩn hóa; dùng `isEnumConstant()` giúp mã thể hiện đúng ý định mà không tự viết kiểm tra bit. Cờ của trường được mô tả tại [JVMS §4.5](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.5).

### isSynthetic không phải phép phủ định của isEnumConstant

Trong chương trình mẫu, cả năm trường được in đều có `isSynthetic() == false`, nhưng chỉ `NEW` và `DONE` là hằng enum.

Trình biên dịch cũng có thể tạo trường hỗ trợ, chẳng hạn mảng nội bộ phục vụ `values()`. Trường hỗ trợ đó không phải một hằng enum. Không nên tìm nó theo tên cố định như `$VALUES`, vì tên và chi tiết triển khai không phải giao diện để ứng dụng phụ thuộc.

Việc các trường hằng được Java khai báo ngầm không có nghĩa chúng phải được đánh dấu synthetic. Chúng đại diện cho các hằng có mặt trong khai báo ngôn ngữ; hãy hỏi từng thuộc tính bằng API tương ứng.

## 5. Phân biệt Field.isEnumConstant với Class.isEnum

Hai phương thức có đối tượng nhận lời gọi và ý nghĩa khác nhau:

- `field.isEnumConstant()`: trường này có biểu diễn một hằng enum không?
- `type.isEnum()`: token lớp này có biểu diễn chính lớp khai báo enum không?

Trong chương trình, `DONE` có phần thân riêng:

~~~java
DONE(20) {
    @Override
    public String toString() {
        return "finished";
    }
}
~~~

Đây là phần trích từ khai báo enum, không phải một chương trình Java độc lập.

Phần thân riêng tạo một lớp con vô danh cho hằng đó. Do vậy:

| Biểu thức | Kết quả trong ví dụ |
| --- | --- |
| `Status.class.isEnum()` | `true` |
| `Status.class.getDeclaredField("DONE").isEnumConstant()` | `true` |
| `Status.class.getDeclaredField("DONE").getType() == Status.class` | `true` |
| `Status.DONE.getClass().isEnum()` | `false` |
| `Status.DONE.getDeclaringClass() == Status.class` | `true` |

`getType()` trên trường đọc **kiểu được khai báo của trường**. `getClass()` trên đối tượng đọc **lớp thực tế của đối tượng**. Hai lớp này có thể khác nhau khi hằng enum có phần thân riêng.

Nếu đã có một đối tượng `Enum<?>` và muốn tìm lớp khai báo enum của nó, dùng `enumValue.getDeclaringClass()`. Không mặc định `enumValue.getClass()` luôn là token thích hợp để liệt kê các hằng.

Cũng đừng nhầm hai phương thức cùng tên:

- `Field.getDeclaringClass()` trả về lớp khai báo trường.
- `Enum.getDeclaringClass()` trả về lớp enum khai báo hằng mà đối tượng đại diện.

Trong ví dụ, chúng cùng có thể trả về `Status.class`, nhưng xuất phát từ hai loại đối tượng khác nhau.

## 6. Metadata khác với giá trị: khi nào enum được khởi tạo?

Có thể hỏi một trường có phải hằng enum mà không cần đọc đối tượng chứa trong trường:

~~~java
// Đoạn minh họa trong ngữ cảnh chương trình mẫu.
Field field = Status.class.getDeclaredField("NEW");
boolean constant = field.isEnumConstant();
~~~

Không cần `field.get(...)` hoặc `setAccessible(true)` cho phép phân loại này.

Nếu cần đọc giá trị trường tĩnh, có thể dùng `field.get(null)`. Với trường tĩnh, đối số đối tượng nhận bị bỏ qua; truyền `null` làm ý định rõ ràng. Việc đọc vẫn chịu kiểm soát truy cập. Trường là `public` không có nghĩa mọi lớp khai báo và ranh giới module đều tự động truy cập được từ mọi nơi.

Quan trọng hơn, đọc trường tĩnh qua Reflection có thể khởi tạo lớp khai báo nếu lớp chưa được khởi tạo. Thí nghiệm sau thể hiện sự khác biệt.

Tạo tệp `EnumMetadataInitializationDemo.java`:

~~~java
import java.lang.reflect.Field;

public class EnumMetadataInitializationDemo {
    public enum Mode {
        ON;

        static {
            System.out.println("Mode initialized");
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Field field = Mode.class.getDeclaredField("ON");
        System.out.println("metadata: " + field.isEnumConstant());
        System.out.println("before reading value");
        System.out.println("value: " + field.get(null));
    }
}
~~~

Biên dịch và chạy trong một tiến trình Java mới:

~~~bash
javac --release 17 -encoding UTF-8 EnumMetadataInitializationDemo.java
java EnumMetadataInitializationDemo
~~~

Kết quả:

~~~text
metadata: true
before reading value
Mode initialized
value: ON
~~~

Hai dòng đầu xuất hiện trước `Mode initialized`. Lấy token `Mode.class`, tìm metadata trường và gọi `isEnumConstant()` chưa làm chạy khối tĩnh trong thí nghiệm này. Đến `field.get(null)`, lớp enum mới được khởi tạo và giá trị `Mode.ON` được đọc.

Đây là khác biệt hữu ích với công cụ khảo sát: nếu chỉ cần phân loại trường, đừng đọc giá trị không cần thiết. Khởi tạo lớp có thể chạy mã do ứng dụng định nghĩa. Nếu quá trình khởi tạo thất bại, thao tác đọc có thể thất bại theo các quy tắc khởi tạo lớp, chứ không còn đơn thuần là truy vấn một cờ metadata.

Ví dụ không chỉnh sửa hằng enum. `isEnumConstant()` không cấp quyền thay giá trị trường, và việc tìm được `Field` không phải lý do để cố ghi vào các trường hằng `final`.

## 7. Chọn cách liệt kê theo mục đích

| Mục đích | Cách phù hợp |
| --- | --- |
| Đang có một `Field`, cần phân loại | `field.isEnumConstant()` |
| Cần các trường hằng để đọc tên hoặc annotation | Duyệt `getDeclaredFields()`, lọc `Field::isEnumConstant` |
| Biết enum ở lúc biên dịch, cần các giá trị | Gọi `Status.values()` |
| Chỉ biết token lớp khi chạy, cần các giá trị | Gọi `enumType.getEnumConstants()` |
| Cần metadata một hằng đã biết tên | `enumType.getDeclaredField(name)`, rồi xác minh bằng `isEnumConstant()` |

`getDeclaredFields()` không cam kết thứ tự khai báo. Trong ví dụ, ta sắp xếp trường theo tên để có kết quả ổn định `[DONE, NEW]`.

Ngược lại, `values()` và `Class.getEnumConstants()` trả về các hằng theo thứ tự khai báo, nên kết quả là `[NEW, DONE]`. Nếu token không biểu diễn lớp enum, `getEnumConstants()` trả về `null`; với enum không có hằng, nó trả về mảng rỗng. Hai tình huống đó khác nhau.

Nếu cần cả **thứ tự khai báo** lẫn metadata trường, hãy đi từ các giá trị enum sang tên khai báo. Ví dụ sau đặt trong `main()` của chương trình thứ nhất:

~~~java
for (Status value : Status.class.getEnumConstants()) {
    Field field = Status.class.getDeclaredField(value.name());
    System.out.println(field.getName() + ": " + field.isEnumConstant());
}
~~~

Kết quả của riêng đoạn thêm này:

~~~text
NEW: true
DONE: true
~~~

Dùng `name()` để tìm trường, không dùng `toString()`. Trong ví dụ:

- Tên khai báo của hằng là `DONE`.
- Chuỗi trình bày của đối tượng là `finished`, vì đã ghi đè `toString()`.
- Không có trường hằng tên `finished`.

Cách đi qua các giá trị có thể khởi tạo lớp enum. Hãy chọn cách này khi cần thứ tự khai báo và chấp nhận việc lấy các giá trị; nếu chỉ cần phân loại metadata, việc lọc các trường đã đủ.

`getFields()` chỉ xét các trường `public` và có thể bao gồm trường được kế thừa. `getDeclaredFields()` tập trung vào các trường được khai báo ngay trong lớp, bao gồm cả các mức truy cập khác và trường hỗ trợ. Với bài toán nhận diện các hằng của chính enum đang xét, bắt đầu từ `getDeclaredFields()` rồi dùng predicate rõ ràng thường dễ kiểm soát hơn.

## 8. Ví dụ áp dụng: lấy metadata hằng cho công cụ

Một công cụ sinh tài liệu có thể cần các `Field` để đọc annotation gắn trên từng hằng, thay vì chỉ lấy các đối tượng enum.

Phương thức sau nhận **token của lớp khai báo enum**, trả về các trường hằng sắp xếp theo tên. Đây là phương thức hỗ trợ, không phải tệp Java độc lập. Thêm các import `java.lang.reflect.Field`, `java.util.Arrays`, `java.util.Comparator`, `java.util.List` và `java.util.Objects` vào lớp chứa nó.

~~~java
static List<Field> enumConstantFields(Class<?> enumType) {
    Objects.requireNonNull(enumType, "enumType");

    if (!enumType.isEnum()) {
        throw new IllegalArgumentException(
                "Expected an enum declaration class: " + enumType.getName());
    }

    return Arrays.stream(enumType.getDeclaredFields())
            .filter(Field::isEnumConstant)
            .sorted(Comparator.comparing(Field::getName))
            .toList();
}
~~~

Ví dụ lời gọi trong ngữ cảnh chương trình mẫu:

~~~java
List<Field> fields = enumConstantFields(Status.class);
for (Field field : fields) {
    System.out.println(field.getName());
}
~~~

Kết quả của đoạn gọi:

~~~text
DONE
NEW
~~~

Nếu đầu vào là `Status.DONE.getClass()`, phương thức hỗ trợ sẽ từ chối vì đó là lớp con riêng của hằng, không phải lớp khai báo enum. Khi xuất phát từ đối tượng enum, hãy truyền `value.getDeclaringClass()` nếu mục đích là khảo sát enum chứa hằng đó.

Việc lọc `isEnumConstant()` giúp tránh đưa `DEFAULT`, `LABEL`, `code` và trường hỗ trợ vào danh sách hằng. Công cụ có thể đọc tiếp các annotation của từng `Field` bằng API annotation tương ứng; khả năng đọc annotation khi chạy còn phụ thuộc chính sách lưu giữ của annotation.

## 9. Những lỗi nên tránh

1. **Dùng kiểu trường để kết luận nó là hằng enum.** `DEFAULT` có kiểu enum nhưng chỉ là một bí danh.
2. **Đọc giá trị rồi kiểm tra đối tượng để phân loại trường.** Cách đó nhầm giá trị với vai trò khai báo và có thể gây khởi tạo lớp không cần thiết.
3. **Lọc mọi trường không synthetic.** Trường dữ liệu thông thường cũng thường không synthetic.
4. **Tin rằng mảng từ getDeclaredFields có thứ tự khai báo.** Muốn thứ tự ổn định thì sắp xếp; muốn thứ tự hằng enum thì dùng API giá trị và ánh xạ lại theo tên.
5. **Dùng toString làm tên trường.** Chuỗi trình bày có thể bị ghi đè hoặc bản địa hóa; `name()` là tên khai báo cần dùng để tìm trường hằng.
6. **Lấy getClass của hằng có phần thân riêng để quét enum.** Hãy phân biệt lớp con của hằng với lớp khai báo enum.
7. **Cho rằng nhận diện được hằng nghĩa là có thể sửa nó.** API này chỉ trả lời câu hỏi phân loại.

## 10. Tự kiểm tra

Với enum `Status` trong chương trình mẫu:

1. Vì sao `NEW` và `DEFAULT` có thể chứa cùng một đối tượng nhưng cho kết quả `isEnumConstant()` khác nhau?
2. `code` được khai báo trong enum; điều đó có làm nó thành hằng enum không?
3. `Status.DONE.getClass().isEnum()` trả về `false` có khiến trường `DONE` mất vai trò hằng enum không?
4. Vì sao danh sách trường sắp xếp theo tên bắt đầu bằng `DONE`, còn danh sách giá trị theo thứ tự khai báo bắt đầu bằng `NEW`?
5. Muốn tìm metadata trường của `Status.DONE`, nên dùng `done.name()` hay `done.toString()`?
6. Chỉ cần biết trường có phải hằng enum thì có cần gọi `field.get(null)` không?
7. Nếu `enumType.getEnumConstants()` trả về `null`, có nên hiểu là enum không có hằng không?

Đáp án:

1. `NEW` là trường biểu diễn một hằng được khai báo trong danh sách hằng; `DEFAULT` là trường thông thường tham chiếu đến hằng đã có. Predicate xét vai trò của trường.
2. Không. Nó là dữ liệu của từng đối tượng enum.
3. Không. Lớp thực tế riêng của đối tượng khác với vai trò khai báo của trường.
4. Hai danh sách dùng hai quy tắc thứ tự khác nhau; `getDeclaredFields()` tự nó không bảo đảm thứ tự khai báo.
5. Dùng `name()`, cho chuỗi `DONE`. `toString()` trong ví dụ cho `finished`.
6. Không. `isEnumConstant()` đọc thông tin phân loại trường; đọc giá trị là thao tác riêng.
7. Không. `null` cho biết token không phải lớp enum; enum không có hằng cho mảng rỗng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enum-types-004">Enum.valueOf với Class của enum</a>

<details>
<summary>Xem chi tiết</summary>

# `Enum.valueOf()` với `Class` của enum

`Enum.valueOf()` chuyển một **tên khai báo của hằng** thành hằng thuộc **một kiểu enum cụ thể**. Tham số `Class` cho biết cần tra cứu trong enum nào. Đây là công cụ hữu ích khi viết bộ đọc cấu hình, bộ chuyển đổi dữ liệu hoặc mã hạ tầng dùng chung cho nhiều enum.

Ví dụ, chuỗi `"FAST"` chưa đủ để xác định một đối tượng: nhiều enum có thể cùng khai báo tên đó. Cặp `RunMode.class` và `"FAST"` mới xác định yêu cầu tìm `RunMode.FAST`. Khi việc chọn enum diễn ra lúc chạy, `Class` chính là thông tin nối giữa tên dạng chuỗi và kiểu Java cần xử lý.

Bài này phân biệt hai tình huống: đã có `Class<E>` với kiểu enum cụ thể, và chỉ có `Class<?>` lấy được từ cơ chế khám phá kiểu.

## 1. Chữ ký và ý nghĩa của tham số kiểu

Chữ ký API:

```java
public static <T extends Enum<T>> T valueOf(
        Class<T> enumClass, String name)
```

Đây là chữ ký để đọc, không phải phương thức bạn cần tự cài đặt. Ràng buộc `<T extends Enum<T>>` gắn kiểu trả về với chính kiểu enum mà `enumClass` đại diện.

Với `RunMode` đã được khai báo, lời gọi có thể viết như sau:

```java
RunMode mode = Enum.valueOf(RunMode.class, "FAST");
```

`RunMode.class` có kiểu `Class<RunMode>`, nên kết quả được kiểm tra kiểu là `RunMode`. Không cần ép từ `Object` về enum sau lời gọi.

Lời gọi tìm **hằng đã tồn tại**, không tạo một hằng mới. Trong thí nghiệm bên dưới, kết quả so sánh `mode == RunMode.FAST` là `true`.

Theo [hợp đồng `Enum.valueOf`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Enum.html#valueOf(java.lang.Class,java.lang.String)), tên phải khớp chính xác với tên hằng được khai báo. Không tìm thấy thì ném `IllegalArgumentException`; tham số lớp hoặc tên là `null` thì ném `NullPointerException`.

### Khác gì `RunMode.valueOf("FAST")`?

Mỗi enum có phương thức tĩnh `valueOf(String)` được khai báo ngầm. Vì vậy, khi đã biết enum, `RunMode.valueOf("FAST")` thường dễ đọc hơn. Dạng `Enum.valueOf(enumType, name)` hữu ích cho hàm tổng quát nhận `Class<E>`. Quy tắc khai báo phương thức này được mô tả trong [JLS về thành viên enum](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.9.3).

Cả hai dạng đều tra cứu tên hằng; đổi cách gọi không biến nó thành bộ phân tích nhãn hiển thị hay mã nghiệp vụ.

## 2. Tên khai báo, nhãn hiển thị và mã nghiệp vụ

Giả sử hằng `FAST` có nhãn `Fast execution` và mã `fast-code`. Ba chuỗi này phục vụ những mục đích khác nhau:

| Giá trị | Vai trò trong thí nghiệm | `Enum.valueOf(RunMode.class, ...)` |
| --- | --- | --- |
| `"FAST"` | Tên khai báo của hằng | Tìm thấy `RunMode.FAST` |
| `"Fast execution"` | Kết quả `toString()` dùng làm nhãn | Không phải tên cần tra cứu |
| `"fast-code"` | Mã nghiệp vụ do ứng dụng định nghĩa | Không tự được nhận diện |
| `"1"` | Chuỗi biểu diễn một số | Không được hiểu thành `ordinal()` |

`valueOf` không tự cắt khoảng trắng, đổi hoa thường, đọc thuộc tính `code`, gọi `toString()` để so sánh hay suy ra số thứ tự. Ví dụ bên dưới kiểm chứng từng trường hợp bằng các lời gọi độc lập.

## 3. Ví dụ Java 17+ đầy đủ

Chương trình có ba hàm phục vụ ba hợp đồng rõ ràng:

- `parseTyped`: tra cứu chính xác khi đã có `Class<E>`.
- `parseNormalized`: chính sách riêng, chủ động bỏ khoảng trắng đầu cuối và đổi chữ hoa.
- `parseRuntime`: nhận `Class<?>`, xác minh là khai báo enum rồi tra cứu và trả `Enum<?>`.

Lưu toàn bộ vào **`EnumValueOfDemo.java`**:

```java
import java.util.Locale;
import java.util.Objects;

public class EnumValueOfDemo {
    enum RunMode {
        SAFE("safe-code", "Safe execution"),
        FAST("fast-code", "Fast execution"),
        TURBO("turbo-code", "Turbo execution") {
            @Override
            public String toString() {
                return "Turbo custom label";
            }
        };

        private final String code;
        private final String label;

        RunMode(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String code() {
            return code;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static <E extends Enum<E>> E parseTyped(
            Class<E> enumType, String name) {
        return Enum.valueOf(enumType, name);
    }

    public static <E extends Enum<E>> E parseNormalized(
            Class<E> enumType, String text) {
        Objects.requireNonNull(text, "text");
        String normalized = text.strip().toUpperCase(Locale.ROOT);
        return Enum.valueOf(enumType, normalized);
    }

    public static Enum<?> parseRuntime(Class<?> type, String name) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        if (!type.isEnum()) {
            throw new IllegalArgumentException(
                    "Expected an enum declaration: " + type.getTypeName());
        }
        return parseCheckedClass(type, name);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E parseCheckedClass(
            Class<?> checkedType, String name) {
        Class<E> enumType = (Class<E>) checkedType;
        return Enum.valueOf(enumType, name);
    }

    private static void attempt(String label, Runnable action) {
        try {
            action.run();
            System.out.println(label + " -> success");
        } catch (RuntimeException ex) {
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        RunMode typed = parseTyped(RunMode.class, "FAST");
        System.out.println("Typed name = " + typed.name());
        System.out.println("Same constant = " + (typed == RunMode.FAST));
        System.out.println("Generated valueOf agrees = "
                + (RunMode.valueOf("FAST") == typed));
        System.out.println("Display label = " + typed);
        System.out.println("Business code = " + typed.code());
        System.out.println("Normalized name = "
                + parseNormalized(RunMode.class, " fast ").name());

        Class<?> discovered = Class.forName("EnumValueOfDemo$RunMode");
        Enum<?> dynamic = parseRuntime(discovered, "TURBO");
        System.out.println("Runtime enum type = "
                + dynamic.getDeclaringClass().getSimpleName());
        System.out.println("Runtime name = " + dynamic.name());
        System.out.println("Runtime display = " + dynamic);

        attempt("wrong case", () -> parseTyped(RunMode.class, "fast"));
        attempt("extra spaces", () -> parseTyped(RunMode.class, " FAST "));
        attempt("display label", () -> parseTyped(RunMode.class, "Fast execution"));
        attempt("business code", () -> parseTyped(RunMode.class, "fast-code"));
        attempt("ordinal text", () -> parseTyped(RunMode.class, "1"));
        attempt("missing name", () -> parseTyped(RunMode.class, "UNKNOWN"));
        attempt("null name", () -> parseTyped(RunMode.class, null));
        attempt("not an enum", () -> parseRuntime(String.class, "FAST"));
        attempt("null class", () -> parseRuntime(null, "FAST"));

        Enum<?> selected = RunMode.TURBO;
        attempt("constant runtime class",
                () -> parseRuntime(selected.getClass(), "SAFE"));
        System.out.println("From declaring class = "
                + parseRuntime(selected.getDeclaringClass(), "SAFE").name());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 -Xlint:all -Werror EnumValueOfDemo.java
java EnumValueOfDemo
```

Kết quả:

```text
Typed name = FAST
Same constant = true
Generated valueOf agrees = true
Display label = Fast execution
Business code = fast-code
Normalized name = FAST
Runtime enum type = RunMode
Runtime name = TURBO
Runtime display = Turbo custom label
wrong case -> IllegalArgumentException
extra spaces -> IllegalArgumentException
display label -> IllegalArgumentException
business code -> IllegalArgumentException
ordinal text -> IllegalArgumentException
missing name -> IllegalArgumentException
null name -> NullPointerException
not an enum -> IllegalArgumentException
null class -> NullPointerException
constant runtime class -> IllegalArgumentException
From declaring class = SAFE
```

Hàm `attempt()` chỉ phục vụ quan sát các trường hợp lỗi trong thí nghiệm: in tên ngoại lệ rồi tiếp tục thử trường hợp kế tiếp. Nó không phải mẫu để bỏ qua mọi `RuntimeException` trong mã ứng dụng.

## 4. Đọc luồng tra cứu có kiểu rõ ràng

`parseTyped` có tham số `Class<E>` và trả về `E`. Khi truyền `RunMode.class`, bên sử dụng nhận trực tiếp `RunMode`:

1. `Enum.valueOf` tìm `FAST` trong đúng tập hằng của `RunMode`.
2. Biến `typed` nhận hằng đó; so sánh với `RunMode.FAST` cho `true`.
3. `RunMode.valueOf("FAST")` cũng cho cùng hằng.
4. In `typed.name()` được `FAST`, nhưng in trực tiếp `typed` được `Fast execution` vì enum đã ghi đè `toString()`.

Điều này lý giải vì sao dùng nhãn hiển thị làm đầu vào cho `valueOf` không khớp với chính sách tra cứu. Nếu dữ liệu bên ngoài sử dụng `fast-code`, ứng dụng cần một phép tra cứu theo mã riêng và quy định cách xử lý mã không tồn tại.

Cũng không nên đổi tên hằng tùy tiện nếu tên đó đã được dùng trong cấu hình hoặc dữ liệu trao đổi. Chuỗi cũ không tự biến thành tên mới chỉ vì enum đã được cập nhật.

## 5. Khi chỉ có `Class<?>` lúc chạy

Trong chương trình, `Class.forName("EnumValueOfDemo$RunMode")` trả `Class<?>`. Tên chứa `$` vì `RunMode` là enum lồng trong `EnumValueOfDemo`; đây là tên nhị phân dùng để tìm đúng lớp trong ví dụ.

Mặc dù đối tượng `Class` thực tế đại diện cho `RunMode`, biến `discovered` không giữ thông tin `Class<RunMode>` ở mức kiểu tĩnh. Do đó, đoạn sau **chủ đích không biên dịch**:

```java
Class<?> discovered = RunMode.class;
// Lỗi biên dịch: Class<?> chưa đáp ứng ràng buộc Class<T> với T extends Enum<T>.
Enum<?> value = Enum.valueOf(discovered, "FAST");
```

`if (discovered.isEnum())` kiểm tra điều kiện lúc chạy, nhưng không tự đổi kiểu tĩnh của biến thành `Class<RunMode>`. Đây là khác biệt giữa **xác minh giá trị metadata** và **chứng minh quan hệ kiểu cho trình biên dịch**.

### Ranh giới động trong hàm mẫu

`parseRuntime` xử lý sự khác biệt này theo một ranh giới hẹp:

1. Từ chối `null` cho cả lớp và tên.
2. Dùng `isEnum()` để bảo đảm lớp là một khai báo enum, không phải lớp thông thường hoặc lớp riêng của một hằng.
3. Chỉ sau kiểm tra mới gọi hàm riêng `parseCheckedClass`.
4. Hàm riêng cô lập phép ép kiểu chưa thể kiểm chứng đầy đủ lúc biên dịch, sau đó gọi `Enum.valueOf`.
5. Bên ngoài chỉ nhận `Enum<?>`, vì bên gọi chưa biết kiểu enum cụ thể.

`@SuppressWarnings("unchecked")` nằm ở đúng hàm có phép ép kiểu; nó **không thực hiện xác minh** và không khiến một lớp sai trở nên hợp lệ. Điều kiện `isEnum()` cùng việc chỉ gọi hàm riêng từ nhánh đã kiểm tra mới là căn cứ của thiết kế mẫu.

Không công khai hàm riêng đó như một phương thức cho phép bên gọi tùy ý chọn `E`. Nếu một người gọi được phép yêu cầu `E` khác với lớp truyền vào, kiểu trả về sẽ tạo ra một lời hứa sai. Hợp đồng công khai `Enum<?> parseRuntime(...)` tránh lời hứa đó.

Nếu lớp enum vẫn được biết trong phần lớn mã ứng dụng, hãy giữ `Class<E>` và dùng hàm có kiểu rõ ràng. Chỉ đưa phép ép kiểu vào nơi thật sự phải tiếp nhận `Class<?>`.

### Phân biệt lỗi tìm lớp và lỗi tìm hằng

`Class.forName` có thể ném `ClassNotFoundException` trước khi tìm được đối tượng `Class`. `Enum.valueOf` lại xử lý bước tìm hằng bên trong kiểu đã xác định. Trong ví dụ, `main` khai báo `throws ClassNotFoundException` vì bước khám phá lớp; không phải vì `Enum.valueOf` ném ngoại lệ đó.

Không cần tìm phương thức `valueOf` bằng `Method.invoke` chỉ để thực hiện phép tra cứu này. API `Enum.valueOf` đã nhận trực tiếp đối tượng `Class`.

## 6. Cần truyền lớp khai báo enum

`RunMode.TURBO` có phần thân riêng để ghi đè `toString()`. Vì vậy, lớp thực tế từ `selected.getClass()` là một lớp vô danh của hằng, không phải chính khai báo `RunMode`.

Trong thí nghiệm, `parseRuntime(selected.getClass(), "SAFE")` bị từ chối bởi `isEnum()`. Nhưng `parseRuntime(selected.getDeclaringClass(), "SAFE")` thành công. Với một giá trị `Enum<?>`, `getDeclaringClass()` trả lớp enum sở hữu hằng. [API `Class.isEnum()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#isEnum()) nêu rõ sự khác biệt này.

Không thay kiểm tra bằng việc chỉ hỏi lớp có kế thừa `Enum` hay không: lớp riêng của hằng vẫn kế thừa enum nhưng không phải khai báo enum cần dùng để tra cứu toàn bộ tập hằng.

## 7. Chuẩn hóa đầu vào là chính sách của ứng dụng

`parseTyped(RunMode.class, " fast ")` không được làm cho thành công một cách ngầm định. Hàm `parseNormalized` trong chương trình tạo ra hợp đồng khác:

```java
String normalized = text.strip().toUpperCase(Locale.ROOT);
return Enum.valueOf(enumType, normalized);
```

Đây là đoạn trích từ hàm đã có trong chương trình, không phải chương trình độc lập. `Locale.ROOT` giúp việc đổi hoa thường không phụ thuộc ngôn ngữ mặc định của máy; đó là mục đích của [locale gốc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Locale.html#ROOT).

Trong thí nghiệm, quy ước tên hằng là chữ hoa, nên `" fast "` được đổi thành `"FAST"` rồi tra cứu thành công. Bạn phải lựa chọn chính sách này theo định dạng dữ liệu đang xử lý.

Không áp dụng chuẩn hóa một cách phổ quát: nếu một định dạng yêu cầu tên chính xác, khoảng trắng hoặc hoa thường sai cần được báo lỗi. Tên hằng Java cũng không bị bắt buộc phải là chữ hoa; một quy tắc đổi hoa có thể làm mất khác biệt giữa những tên mà ứng dụng muốn phân biệt.

Chuẩn hóa tên vẫn không biến `fast-code` thành `FAST`, và cũng không biến nhãn nhiều từ thành tên hằng. Những ánh xạ đó cần một hợp đồng riêng.

## 8. Xử lý lỗi và lỗi thường gặp

| Tình huống | Hành vi hoặc cách xử lý |
| --- | --- |
| Tên không tồn tại, sai hoa thường hoặc có khoảng trắng thừa | `IllegalArgumentException` từ tra cứu chính xác |
| Tên là `null` | `NullPointerException` |
| Lớp là `null` | `NullPointerException` |
| `parseRuntime` nhận lớp không phải khai báo enum | Chủ động ném `IllegalArgumentException` trước khi ép kiểu |
| Chỉ có `Class<?>` nhưng gọi trực tiếp `Enum.valueOf` | Lỗi biên dịch do chưa thỏa ràng buộc kiểu |
| Truyền `String.class` vào lời gọi có kiểu chặt chẽ | Trình biên dịch không chấp nhận đó như một lớp enum |
| Dùng nhãn hiển thị, mã nghiệp vụ hoặc chuỗi số | Không tự được hiểu như tên khai báo |

Khi dữ liệu do người dùng cung cấp, hãy xử lý lỗi tra cứu ở ranh giới chuyển đổi và báo rõ giá trị không được hỗ trợ trong trường dữ liệu nào. Đừng tự chọn hằng đầu tiên làm mặc định chỉ để tránh lỗi: điều đó có thể đổi ý nghĩa yêu cầu mà bên sử dụng không biết.

Nếu ứng dụng muốn có kết quả dạng tùy chọn hoặc một kết quả chứa thông tin lỗi, hãy định nghĩa một hàm bao có hợp đồng đó. Bản thân `Enum.valueOf` không trả `null` hoặc `Optional.empty()` để báo tên không tồn tại.

Chỉ bắt ngoại lệ bạn thực sự biết cách xử lý. Lỗi do truyền lớp sai vào một bộ chuyển đổi nội bộ thường cần được sửa ở cấu hình hoặc mã gọi; nó không nên bị giấu chung với lỗi nhập sai tên hằng.

### Tự kiểm tra

1. Vì sao cần truyền cả `Class` lẫn tên hằng, thay vì chỉ truyền chuỗi `"FAST"`?
2. Vì sao `RunMode.class` dùng được trực tiếp nhưng biến `Class<?>` cần thêm xử lý?
3. Nếu đổi `toString()` thành một nhãn khác, lời gọi với `"FAST"` có bị đổi ý nghĩa không?
4. Vì sao `isEnum()` trả `true` không tự thay đổi kiểu tĩnh của biến?
5. Với một hằng có phần thân riêng, nên lấy `Class` bằng phương thức nào để tra cứu hằng cùng enum?
6. Khi nào việc tự cắt khoảng trắng và đổi chữ hoa là hợp đồng đúng, và khi nào nên báo đầu vào không hợp lệ?

Cần nhớ ba thông tin khi áp dụng: **đúng lớp khai báo enum, đúng tên khai báo của hằng, và đúng ranh giới giữa thông tin kiểu tĩnh với metadata lúc chạy**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enum-types-005">Enum có thân riêng trên hằng và khác biệt getClass với getDeclaringClass</a>

<details>
<summary>Xem chi tiết</summary>

# Enum có thân riêng trên hằng: getClass và getDeclaringClass

## 1. Một kiểu enum có thể có nhiều lớp thực tế

Một enum có thể khai báo hành vi chung và cho một số hằng ghi đè hành vi bằng **thân lớp riêng của hằng (constant-specific class body)**. Ví dụ một phép biến đổi mặc định giữ nguyên số, còn các hằng khác nhân đôi hoặc nhân ba.

Các hằng ấy vẫn thuộc cùng một kiểu enum. Tuy nhiên, một hằng có thân lớp riêng được thực hiện bằng một lớp con ẩn danh của enum. Vì thế, **lớp thực tế của đối tượng** và **lớp enum khai báo hằng** có thể khác nhau.

Java cung cấp hai API trả lời hai câu hỏi đó:

| Lời gọi trên một hằng enum | Câu hỏi được trả lời |
| --- | --- |
| `constant.getClass()` | Đối tượng hằng này có lớp thực tế nào lúc chạy? |
| `constant.getDeclaringClass()` | Hằng này thuộc enum được khai báo nào? |

`getClass()` được kế thừa từ `Object`. `getDeclaringClass()` trong bảng là phương thức của `Enum`. Cả hai đều là phương thức `final`; người viết enum không ghi đè chúng để thay đổi kết quả.

Sự phân biệt này có giá trị khi một bộ chuyển đổi, bộ kiểm tra dữ liệu hoặc nơi lưu cấu hình dùng `Class` làm khóa. Nếu các quy tắc được đăng ký cho kiểu enum chung, dùng lớp riêng của từng hằng để tra cứu có thể làm mất cấu hình.

## 2. Thân riêng tạo lớp con, còn hằng vẫn thuộc enum ban đầu

Xét cấu trúc:

~~~java
enum Transform {
    IDENTITY,
    DOUBLE {
        @Override
        public int apply(int value) {
            return value * 2;
        }
    };

    public int apply(int value) {
        return value;
    }
}
~~~

Đây là một khai báo enum hoàn chỉnh, có thể đặt trong một lớp hoặc trong tệp nguồn phù hợp; chưa phải chương trình có `main`.

- `IDENTITY` không có thân riêng, nên dùng phần thực hiện `apply` trong `Transform`.
- `DOUBLE` có thân riêng và ghi đè `apply`.
- Lớp thực tế của `IDENTITY` là `Transform`.
- Lớp thực tế của `DOUBLE` là lớp con ẩn danh của `Transform`.
- Cả hai đều có `getDeclaringClass() == Transform.class`.

Không cần có phương thức ghi đè thì thân riêng mới tạo lớp con: ngay cả một thân `{}` rỗng trên hằng cũng có ý nghĩa về lớp như vậy.

Đây là cơ chế Java dành riêng cho khai báo hằng enum. Nó không cho phép mã ứng dụng tùy ý viết một lớp khác `extends Transform` để bổ sung hằng. Các hằng vẫn là tập giá trị được khai báo của enum.

Trên Java 17, enum không có thân riêng trên hằng là `final` ngầm định; enum có ít nhất một thân như vậy là `sealed` ngầm định. Vì vậy, một công cụ Reflection không nên dựa vào giả định “mọi lớp enum đều có modifier final” để nhận diện enum; hãy dùng API đúng mục đích như `Class.isEnum()`.

## 3. Ví dụ chạy được: cùng kiểu enum, khác lớp thực tế

Lưu thành `EnumBodyIdentityDemo.java`:

~~~java
public class EnumBodyIdentityDemo {
    enum Transform {
        IDENTITY,
        DOUBLE {
            @Override
            public int apply(int value) {
                return value * 2;
            }
        },
        TRIPLE {
            @Override
            public int apply(int value) {
                return value * 3;
            }
        };

        public int apply(int value) {
            return value;
        }
    }

    private static void inspect(Transform transform) {
        System.out.println(
                transform.name()
                + " value=" + transform.apply(4)
                + " exact=" + (transform.getClass() == Transform.class)
                + " enumClass=" + transform.getClass().isEnum());
    }

    public static void main(String[] args) {
        for (Transform transform : Transform.values()) {
            inspect(transform);
        }

        System.out.println("DOUBLE.superIsTransform="
                + (Transform.DOUBLE.getClass().getSuperclass()
                   == Transform.class));
        System.out.println("IDENTITY.superIsEnum="
                + (Transform.IDENTITY.getClass().getSuperclass()
                   == Enum.class));
        System.out.println("DOUBLE.anonymous="
                + Transform.DOUBLE.getClass().isAnonymousClass());
        System.out.println("sameRuntime="
                + (Transform.DOUBLE.getClass()
                   == Transform.TRIPLE.getClass()));
        System.out.println("sameDeclaring="
                + (Transform.DOUBLE.getDeclaringClass()
                   == Transform.TRIPLE.getDeclaringClass()));
        System.out.println("sameConstant="
                + (Transform.DOUBLE == Transform.TRIPLE));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 EnumBodyIdentityDemo.java
java EnumBodyIdentityDemo
~~~

Kết quả:

~~~text
IDENTITY value=4 exact=true enumClass=true
DOUBLE value=8 exact=false enumClass=false
TRIPLE value=12 exact=false enumClass=false
DOUBLE.superIsTransform=true
IDENTITY.superIsEnum=true
DOUBLE.anonymous=true
sameRuntime=false
sameDeclaring=true
sameConstant=false
~~~

Hãy đọc kết quả thành ba lớp thông tin:

1. **Hành vi:** cùng một lời gọi `apply(4)` chọn phần thực hiện tương ứng với hằng. Đó là đa hình thông thường.
2. **Lớp thực tế:** `DOUBLE` và `TRIPLE` có hai lớp riêng khác nhau; `IDENTITY` dùng trực tiếp lớp `Transform`.
3. **Kiểu enum:** cả ba đều là hằng của `Transform`. Có cùng lớp khai báo enum không có nghĩa là hai hằng bằng nhau.

Chương trình không in tên nhị phân của các lớp ẩn danh. Tên do trình biên dịch tạo ra thường chứa `$` và số, nhưng không phải dữ liệu nghiệp vụ ổn định. So sánh đối tượng `Class` và hỏi đúng API giúp ví dụ không phụ thuộc các tên ấy.

## 4. Vì sao getClass().isEnum() có thể trả false?

`Class.isEnum()` nhận diện lớp được khai báo là enum. Lớp riêng của một hằng là lớp con ẩn danh; nó không phải chính khai báo enum ban đầu.

Do đó, với ví dụ vừa chạy:

~~~java
Transform.DOUBLE.getClass().isEnum()          // false
Transform.DOUBLE.getDeclaringClass().isEnum() // true
~~~

Hai biểu thức trên là các dòng minh họa trong phạm vi lớp `EnumBodyIdentityDemo`.

Kết quả `false` ở dòng đầu không làm `DOUBLE` mất tư cách hằng enum. Nó chỉ mô tả đúng lớp đang được hỏi.

Khi nhận một đối tượng bất kỳ, có thể kiểm tra:

~~~java
if (value instanceof Enum<?> constant) {
    Class<?> enumType = constant.getDeclaringClass();
    System.out.println(enumType.isEnum());
}
~~~

Đây là đoạn bên trong một phương thức có tham số `Object value`. `instanceof` kiểm tra đối tượng có thuộc hệ kiểu `Enum` hay không, nên bao gồm cả hằng có lớp riêng. Với `null`, điều kiện là `false`.

Không dùng `value.getClass().isEnum()` thay thế điều kiện trên nếu mục tiêu là nhận diện mọi đối tượng hằng enum. Cũng không dùng riêng `Enum.class.isAssignableFrom(type)` để khẳng định `type` là một khai báo enum: quan hệ kế thừa rộng hơn điều kiện `isEnum()`, và bản thân `Enum.class` cũng thỏa kiểm tra quan hệ với chính nó.

## 5. Áp dụng: chuẩn hóa khóa cấu hình theo kiểu enum

Giả sử cấu hình được đăng ký một lần cho enum `Shipping`. Mỗi hằng, kể cả hằng có thân riêng, đều phải tìm được cùng cấu hình ấy.

Lưu thành `EnumTypeRegistryDemo.java`:

~~~java
import java.util.Map;
import java.util.Objects;

public class EnumTypeRegistryDemo {
    enum Shipping {
        BASIC,
        PRIORITY {
            @Override
            public String label() {
                return "Priority delivery";
            }
        };

        public String label() {
            return "Basic delivery";
        }
    }

    static Class<?> enumTypeOf(Object value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof Enum<?> constant) {
            return constant.getDeclaringClass();
        }
        throw new IllegalArgumentException("Expected an enum constant");
    }

    public static void main(String[] args) {
        Map<Class<?>, String> configuration =
                Map.of(Shipping.class, "shipping-config");

        Enum<?> value = Shipping.PRIORITY;

        System.out.println("wrong="
                + configuration.get(value.getClass()));
        System.out.println("right="
                + configuration.get(value.getDeclaringClass()));
        System.out.println("sameLogicalType="
                + (enumTypeOf(Shipping.BASIC)
                   == enumTypeOf(Shipping.PRIORITY)));
        System.out.println("expectedExactClass="
                + (value.getClass() == Shipping.class));
        System.out.println("expectedIsInstance="
                + Shipping.class.isInstance(value));
        System.out.println("enumAccessor="
                + (value.getDeclaringClass() == Shipping.class));
        System.out.println("classAccessor.owner="
                + (Shipping.class.getDeclaringClass()
                   == EnumTypeRegistryDemo.class));
        System.out.println("anonymousClassAccessor="
                + value.getClass().getDeclaringClass());
        System.out.println("knownName=" + value.name());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 EnumTypeRegistryDemo.java
java EnumTypeRegistryDemo
~~~

Kết quả:

~~~text
wrong=null
right=shipping-config
sameLogicalType=true
expectedExactClass=false
expectedIsInstance=true
enumAccessor=true
classAccessor.owner=true
anonymousClassAccessor=null
knownName=PRIORITY
~~~

Khóa `Shipping.class` và lớp riêng của `PRIORITY` là hai đối tượng `Class` khác nhau. Vì vậy, phép tra cứu đầu trả `null`. Lấy `getDeclaringClass()` cho đúng khóa enum đã được đăng ký.

Một lỗi như vậy có thể khó phát hiện khi chỉ thử hằng không có thân riêng: với `BASIC`, `getClass()` tình cờ trả chính `Shipping.class`, nên phép tra cứu có vẻ hoạt động.

Hàm `enumTypeOf` xác định hợp đồng rõ ràng: chỉ nhận một đối tượng hằng enum, từ chối `null` và các đối tượng khác. Không nên âm thầm lấy một lớp cha bất kỳ cho đầu vào không phải enum.

Nếu mục tiêu là kiểm tra một đối tượng có gán được cho kiểu đích hay không, `Shipping.class.isInstance(value)` phản ánh quan hệ kiểu phù hợp, bao gồm lớp con của hằng. Kiểm tra `value.getClass() == Shipping.class` đòi hỏi lớp thực tế phải trùng tuyệt đối và sẽ từ chối hằng có thân riêng.

## 6. Hai phương thức cùng tên getDeclaringClass không có cùng ngữ nghĩa

Đây là điểm dễ đọc nhầm khi viết chuỗi lời gọi Reflection:

| Biểu thức trong ví dụ Shipping | Đối tượng nhận lời gọi | Ý nghĩa |
| --- | --- | --- |
| `value.getDeclaringClass()` | Một hằng kiểu `Enum<?>` | Lấy lớp enum `Shipping` |
| `Shipping.class.getDeclaringClass()` | Một đối tượng `Class` | Lấy lớp chứa khai báo lớp thành viên `Shipping` |
| `value.getClass().getDeclaringClass()` | Một đối tượng `Class` của lớp thực tế | Hỏi lớp khai báo của lớp thực tế ấy |

Trong chương trình, `Shipping` là enum thành viên của `EnumTypeRegistryDemo`, nên `Shipping.class.getDeclaringClass()` trả lớp ngoài `EnumTypeRegistryDemo`.

Ngược lại, lớp ẩn danh của `PRIORITY` không phải một lớp thành viên có tên; `Class.getDeclaringClass()` trả `null` cho lớp ẩn danh. Vì vậy, thêm `getClass()` trước lời gọi đã làm đổi API và đổi câu hỏi.

Khi đọc mã, hãy nhìn kiểu của đối tượng đứng trước dấu chấm:

- `Enum.getDeclaringClass()` xác định **kiểu enum của hằng**.
- `Class.getDeclaringClass()` xác định **lớp khai báo một lớp thành viên**.

Không thay thế hai API này cho nhau dù tên phương thức giống nhau. Với một enum ở mức cao nhất của tệp, `Class.getDeclaringClass()` cũng không phải cách tìm lại enum ấy; nó trả `null` vì không có lớp chứa khai báo thành viên.

## 7. Chọn lớp nào khi đọc metadata?

Chọn `Class` theo thông tin thực sự cần:

**Muốn xử lý kiểu enum chung:** lấy `constant.getDeclaringClass()`. Đây là điểm bắt đầu phù hợp để lấy danh sách hằng bằng `getEnumConstants()`, tra cấu hình theo kiểu, hoặc tìm các khai báo chung của enum.

**Muốn điều tra phần thực hiện riêng của một hằng:** dùng `constant.getClass()`. Khi đó, việc thấy một lớp ẩn danh và các phương thức ghi đè là dữ liệu mong muốn.

Hai hướng không thể tùy ý tráo đổi. Chẳng hạn, `getDeclaredMethods()` hỏi các phương thức được khai báo trực tiếp trong lớp đang xét. Hỏi lớp riêng của `DOUBLE` sẽ nhìn vào phần khai báo riêng của hằng; hỏi `Transform.class` sẽ nhìn vào khai báo enum chung.

Một phương thức chỉ được thêm trong thân riêng của hằng không tự trở thành phương thức chung có thể gọi qua mọi biến kiểu enum. Khi thiết kế hành vi đa hình, nên khai báo phương thức trong enum rồi ghi đè ở hằng cần thay đổi, như `apply` trong ví dụ.

Nếu enum khai báo một phương thức `abstract`, mỗi hằng phải cung cấp phần thực hiện theo quy tắc của enum. Khi tất cả hằng có thân riêng, có thể không có hằng nào mang trực tiếp lớp thực tế của enum. Vì vậy, chọn một hằng bất kỳ làm “mẫu” rồi lấy `getClass()` vẫn không xác định đúng kiểu enum chung.

Không cần `setAccessible(true)`, sửa field nội bộ hoặc tìm constructor để phân biệt hai loại lớp này. Các API công khai đã cung cấp quan hệ cần thiết.

## 8. Những cách suy đoán dễ sai

| Cách làm | Vì sao dễ sai | Cách phù hợp |
| --- | --- | --- |
| `constant.getClass() == ExpectedEnum.class` để nhận mọi hằng của enum | Không nhận lớp riêng của hằng | So sánh `getDeclaringClass()` hoặc dùng `ExpectedEnum.class.isInstance` theo mục đích |
| Lấy `getSuperclass()` đúng một lần cho mọi hằng | Hằng thường có lớp cha là `Enum`; hằng có thân riêng có lớp cha là enum cụ thể | Dùng `Enum.getDeclaringClass()` |
| Cắt tên lớp tại dấu `$` | Dấu này còn xuất hiện trong tên lớp lồng; tên lớp ẩn danh không phải khóa nghiệp vụ | Giữ đối tượng `Class` trả từ API |
| Dựa vào một hằng để đoán lớp của mọi hằng | Enum có thể trộn hằng có và không có thân riêng | Chuẩn hóa từng hằng về lớp enum khi cần |
| Nghĩ hai hằng khác lớp thực tế là khác kiểu enum | Lẫn lớp triển khai với kiểu enum khai báo | Kiểm tra `getDeclaringClass()` |
| Nghĩ cùng lớp enum nghĩa là cùng hằng | Các hằng khác nhau cùng thuộc một enum | So sánh hằng hoặc dùng `name()` theo hợp đồng |
| Gọi `getClass().getDeclaringClass()` thay cho `getDeclaringClass()` | Đã chuyển sang API trên `Class` | Gọi trực tiếp phương thức của `Enum` |

Ngoài ra, tên lớp giống nhau không bảo đảm đó là cùng kiểu nếu chúng được nạp bởi các bộ nạp lớp khác nhau. Khi lưu metadata trong phạm vi JVM, dùng đối tượng `Class` làm khóa giúp giữ đúng danh tính kiểu mà các API trả về; không tự rút gọn thành tên đơn giản rồi giả định mọi tên trùng nhau là cùng kiểu.

## 9. Tự kiểm tra

1. Nếu `RED` và `BLUE` cùng có thân riêng trong enum `Color`, `RED.getClass() == BLUE.getClass()` có đúng không?
2. Hai hằng ấy có cùng `getDeclaringClass()` không?
3. Nếu một hằng không có thân riêng, vì sao lấy một lần `getSuperclass()` không phải cách chuẩn hóa tổng quát?
4. `value.getClass().getDeclaringClass()` có tương đương `value.getDeclaringClass()` không?
5. Một nơi lưu cấu hình đăng ký theo `Color.class` nên dùng API nào để lấy khóa từ một hằng?
6. Có cần in hoặc phân tích các tên lớp dạng có dấu `$` để trả lời các câu hỏi trên không?

Đáp án:

1. Không. Mỗi thân riêng tương ứng với một lớp con ẩn danh riêng.
2. Có; cả hai đều trả `Color.class`.
3. Lớp thực tế của hằng thường chính là enum, nên lớp cha của nó là `Enum`, không phải enum cần tìm.
4. Không. Một lời gọi hỏi quan hệ khai báo của `Class`; lời gọi còn lại hỏi kiểu enum của hằng.
5. `constant.getDeclaringClass()`.
6. Không. Các đối tượng `Class` và API quan hệ kiểu đã trả lời trực tiếp.

Khi viết mã Reflection xử lý enum, hãy quyết định rõ đang cần lớp thực hiện hành vi của một hằng hay cần kiểu enum chung. Quyết định đó xác định lựa chọn giữa `getClass()` và `Enum.getDeclaringClass()`.

## Tài liệu đối chiếu

- [Java 17 — Object.getClass](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#getClass()).
- [Java 17 — Enum.getDeclaringClass](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Enum.html#getDeclaringClass()).
- [Java 17 — Class.getDeclaringClass](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getDeclaringClass()).
- [JLS 17 — Khai báo enum và thân riêng trên hằng](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.9).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enum-types-006">Không tạo thêm hằng enum bằng Constructor.newInstance</a>

<details>
<summary>Xem chi tiết</summary>

# Không tạo thêm hằng enum bằng Constructor.newInstance

## 1. Enum không phải lớp thường có constructor riêng tư

Một enum khai báo một tập các hằng có tên. Ví dụ:

~~~java
enum Phase {
    READY, DONE;
}
~~~

Trong khai báo này, `READY` và `DONE` là các hằng của `Phase`. Các đối tượng tương ứng được tạo trong quá trình khởi tạo lớp enum.

Khi học Reflection, ta dễ suy luận rằng:

1. Enum có constructor.
2. Constructor đó không `public`.
3. Nếu lấy constructor và mở quyền truy cập thì có thể gọi `newInstance()` để tạo `EXTRA`.

**Suy luận ở bước 3 không đúng.** `Constructor.newInstance()` có quy tắc riêng: nếu constructor thuộc một lớp enum, việc tạo instance bị từ chối bằng `IllegalArgumentException`. Đây không chỉ là vấn đề constructor có modifier `private`.

Quy tắc được ghi trong hợp đồng [Constructor.newInstance](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Constructor.html#newInstance(java.lang.Object...)). [JLS §8.9](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.9) cũng nêu rằng không được tạo instance enum bằng Reflection.

Bài học này tập trung vào việc phân biệt **khảo sát constructor**, **điều chỉnh kiểm tra truy cập** và **được phép tạo đối tượng**. Có được hai điều đầu không có nghĩa điều thứ ba được cho phép.

## 2. Vì sao enum vẫn có constructor?

Constructor của enum phục vụ việc khởi tạo các hằng được khai báo. Nó có thể nhận dữ liệu riêng, gán trường và thực hiện logic khởi tạo giống những phần mã Java thông thường.

Ví dụ sau là một khai báo enum độc lập:

~~~java
enum Severity {
    LOW(1),
    HIGH(5);

    private final int score;

    Severity(int score) {
        this.score = score;
    }
}
~~~

Khi các hằng được khởi tạo, `LOW` nhận dữ liệu `1`, còn `HIGH` nhận dữ liệu `5`. Điều đó không tạo một API để mã bên ngoài gọi `new Severity(3)`.

Một vài quy tắc cần nhớ:

- Không được dùng biểu thức `new` để tự tạo instance của lớp enum.
- Constructor enum không được khai báo `public` hoặc `protected`.
- Constructor enum không ghi modifier truy cập được xem là `private`.
- Việc Reflection nhìn thấy constructor không làm thay đổi các quy tắc của enum.

Đặc biệt, `private` và “không được tạo thêm instance enum bằng `Constructor.newInstance()`” là hai ràng buộc khác nhau. Thí nghiệm tiếp theo tách rõ chúng.

## 3. Thí nghiệm đầy đủ: cùng mở truy cập, kết quả khác nhau

Lưu toàn bộ mã dưới đây vào `EnumConstructorDemo.java`. Chỉ lớp `EnumConstructorDemo` là `public`; các lớp và enum hỗ trợ ở cấp cao nhất nằm trong cùng tệp.

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;

final class PlainItem {
    private PlainItem() {
    }
}

final class PhaseConstructionCount {
    static int count;
}

enum Phase {
    READY, DONE;

    private Phase() {
        PhaseConstructionCount.count++;
    }
}

public class EnumConstructorDemo {
    public static void main(String[] args)
            throws ReflectiveOperationException {
        Constructor<PlainItem> plain =
                PlainItem.class.getDeclaredConstructor();

        System.out.println("plain private: "
                + Modifier.isPrivate(plain.getModifiers()));
        System.out.println("plain accessible before: "
                + plain.canAccess(null));
        System.out.println("plain access enabled: "
                + plain.trySetAccessible());
        System.out.println("plain created: "
                + (plain.newInstance() != null));

        System.out.println("enum public constructors: "
                + Phase.class.getConstructors().length);

        try {
            Phase.class.getDeclaredConstructor();
            throw new AssertionError("Unexpected no-arg constructor");
        } catch (NoSuchMethodException expected) {
            System.out.println("enum no-arg lookup: NoSuchMethodException");
        }

        Constructor<Phase> constructor =
                Phase.class.getDeclaredConstructor(String.class, int.class);

        System.out.println("enum parameters: "
                + Arrays.toString(constructor.getParameterTypes()));
        System.out.println("enum private: "
                + Modifier.isPrivate(constructor.getModifiers()));
        System.out.println("enum accessible before: "
                + constructor.canAccess(null));
        System.out.println("enum access enabled: "
                + constructor.trySetAccessible());
        System.out.println("enum accessible after: "
                + constructor.canAccess(null));

        System.out.println("values before: "
                + Arrays.toString(Phase.values()));
        int callsBefore = PhaseConstructionCount.count;
        System.out.println("constructor calls before: " + callsBefore);

        try {
            constructor.newInstance("EXTRA", 2);
            throw new AssertionError("Enum construction unexpectedly succeeded");
        } catch (IllegalArgumentException expected) {
            System.out.println("enum newInstance: IllegalArgumentException");
        }

        System.out.println("constructor body unchanged: "
                + (PhaseConstructionCount.count == callsBefore));
        System.out.println("values after: "
                + Arrays.toString(Phase.values()));

        try {
            Enum.valueOf(Phase.class, "EXTRA");
            throw new AssertionError("Unexpected EXTRA constant");
        } catch (IllegalArgumentException expected) {
            System.out.println("lookup EXTRA: IllegalArgumentException");
        }

        System.out.println("lookup READY identity: "
                + (Enum.valueOf(Phase.class, "READY") == Phase.READY));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 EnumConstructorDemo.java
java EnumConstructorDemo
~~~

Kết quả chính xác khi chạy theo các lệnh trên, trên classpath thông thường:

~~~text
plain private: true
plain accessible before: false
plain access enabled: true
plain created: true
enum public constructors: 0
enum no-arg lookup: NoSuchMethodException
enum parameters: [class java.lang.String, int]
enum private: true
enum accessible before: false
enum access enabled: true
enum accessible after: true
values before: [READY, DONE]
constructor calls before: 2
enum newInstance: IllegalArgumentException
constructor body unchanged: true
values after: [READY, DONE]
lookup EXTRA: IllegalArgumentException
lookup READY identity: true
~~~

Chương trình in loại ngoại lệ, không phụ thuộc vào nguyên văn thông báo lỗi của một phiên bản JDK.

`PlainItem` và `Phase` là các kiểu ở cấp cao nhất, tách khỏi lớp chứa `main()`. Cách đặt này giúp thể hiện rõ trạng thái không truy cập được constructor `private` trước khi gọi `trySetAccessible()`.

### 3.1. Lớp thường có thể được tạo sau khi mở truy cập

Đối với `PlainItem`:

- Constructor là `private`.
- Trước khi mở truy cập, `canAccess(null)` trả về `false`.
- Trong môi trường của thí nghiệm, `trySetAccessible()` trả về `true`.
- `plain.newInstance()` tạo được một đối tượng.

Điều này làm mẫu đối chiếu cho nhận định “constructor riêng tư có thể gọi được khi điều kiện truy cập cho phép”. Nó không áp dụng riêng cho enum.

### 3.2. Enum vẫn bị từ chối dù đã mở truy cập

Đối với constructor của `Phase`, chương trình cũng xác nhận:

~~~text
enum private: true
enum accessible before: false
enum access enabled: true
enum accessible after: true
~~~

Ngay sau đó, lời gọi:

~~~java
constructor.newInstance("EXTRA", 2);
~~~

vẫn ném `IllegalArgumentException`.

Như vậy, lỗi không xuất phát từ việc chưa mở quyền truy cập constructor trong thí nghiệm này. Trạng thái truy cập đã được chứng minh là cho phép; quy tắc cấm tạo instance enum vẫn còn hiệu lực.

`canAccess(null)` chỉ hỏi người gọi hiện tại có thể truy cập đối tượng Reflection đó hay không. Nó không hỏi rằng mọi thao tác thực thi tiếp theo đều hợp lệ về ngữ nghĩa. Đối với constructor, đối số của `canAccess` phải là `null`; đây không phải một đối tượng sẽ nhận lời gọi phương thức.

### 3.3. Thân constructor không được gọi thêm

Để quan sát điều này, constructor của `Phase` tăng một bộ đếm nằm ở lớp hỗ trợ riêng:

~~~java
private Phase() {
    PhaseConstructionCount.count++;
}
~~~

Trước thí nghiệm tạo thêm instance, chương trình gọi `Phase.values()` để khởi tạo hai hằng hiện có. Vì vậy bộ đếm là `2`.

Sau lần `newInstance()` bị từ chối:

- Bộ đếm giữ nguyên.
- `Phase.values()` vẫn chỉ chứa `READY` và `DONE`.
- Tra cứu `EXTRA` bằng `Enum.valueOf(...)` vẫn thất bại.

Bộ đếm ở ngoài enum cũng giúp tránh trộn bài thí nghiệm với các quy tắc đặc biệt về truy cập trường tĩnh trong quá trình khởi tạo enum.

## 4. Vì sao tìm constructor không tham số lại thất bại?

Mã nguồn có thể cho thấy:

~~~java
private Phase() {
    PhaseConstructionCount.count++;
}
~~~

nhưng metadata constructor trong class file do `javac` tạo ở ví dụ này có các tham số:

~~~text
[class java.lang.String, int]
~~~

Chúng phục vụ tên hằng và vị trí thứ tự của hằng. Constructor của lớp cơ sở `Enum` nhận `name` và `ordinal`; các giá trị này được mã do trình biên dịch sinh ra cung cấp.

Vì vậy, trong thí nghiệm:

~~~java
Phase.class.getDeclaredConstructor();
~~~

không tìm thấy constructor có danh sách kiểu tham số rỗng và ném `NoSuchMethodException`.

Tra cứu tương ứng với metadata đã quan sát là:

~~~java
Phase.class.getDeclaredConstructor(String.class, int.class);
~~~

**Tìm được chữ ký không có nghĩa được gọi để tạo enum.** Ví dụ đã truyền đúng hai đối số có kiểu phù hợp nhưng vẫn bị chặn.

Nếu enum có tham số do người viết khai báo, chẳng hạn `Severity(int score)`, metadata do `javac` sinh thường thể hiện các tham số hỗ trợ ở trước tham số `score`. Khi khảo sát constructor, hãy xem `getParameterTypes()` và metadata thực tế của class file, không suy đoán từ số tham số nhìn thấy trong mã nguồn. Không dùng vị trí đầu tiên của `getDeclaredConstructors()` làm quy tắc chọn constructor khi enum có nhiều constructor.

Cũng không nên dựa vào tên tham số như `arg0` hay tên nội bộ do compiler đặt. Tùy chọn biên dịch và metadata có thể ảnh hưởng cách tên tham số được phản chiếu.

`String.class` và `int.class` trong ví dụ là các **kiểu dùng để tìm chữ ký**. Truyền `Integer.class` thay `int.class` cho `getDeclaredConstructor(...)` không phải cùng một tra cứu. Việc `newInstance()` có hỗ trợ chuyển đổi đối số ở bước gọi không làm API tìm constructor tự chọn lại một chữ ký khác.

## 5. Phân biệt các thao tác và nhóm lỗi

| Thao tác hoặc tình huống | Ý nghĩa và kết quả cần phân biệt |
| --- | --- |
| `getConstructors()` | Chỉ trả constructor `public`; enum trong ví dụ có mảng rỗng |
| `getDeclaredConstructor(...)` | Tìm constructor khai báo trong lớp, bao gồm constructor riêng tư; chữ ký không khớp gây `NoSuchMethodException` |
| Constructor chưa truy cập được | Khi gọi, có thể gặp `IllegalAccessException` |
| `trySetAccessible()` | Thử cho phép bỏ qua kiểm tra truy cập theo các điều kiện của Java; thành công không bỏ quy tắc cấm tạo enum |
| `setAccessible(true)` không được môi trường cho phép | Có thể gặp `InaccessibleObjectException` hoặc kiểm tra bảo mật tương ứng |
| `newInstance()` với constructor enum đã truy cập được | Việc tạo instance enum bị từ chối bằng `IllegalArgumentException` |
| Đối số gọi sai số lượng hoặc kiểu | Cũng có thể gây `IllegalArgumentException`; không được suy từ tên ngoại lệ rằng luôn do enum |
| Thân constructor hợp lệ tự ném ngoại lệ | Thông thường ngoại lệ từ thân constructor được bọc trong `InvocationTargetException` |

Bảng giúp định vị vấn đề ở đúng thao tác. Không nên gộp mọi thất bại Reflection thành “không tìm thấy constructor” hoặc “thiếu quyền”.

### IllegalArgumentException ở đây không phải ngoại lệ từ thân constructor

Trong thí nghiệm, Reflection từ chối tạo đối tượng enum; thân constructor không chạy thêm. Vì vậy không có một ngoại lệ do thân constructor `Phase()` tự ném rồi được bọc lại.

Nếu chỉ bắt:

~~~java
// Đoạn minh họa cho một khối catch chưa đủ để xử lý trường hợp enum.
catch (java.lang.reflect.InvocationTargetException ex) {
    System.out.println(ex.getCause());
}
~~~

thì chưa xử lý được `IllegalArgumentException` phát sinh do quy tắc cấm tạo enum. Đoạn trên chỉ là nhánh `catch` minh họa, phải đặt sau một khối `try` thích hợp khi dùng trong mã Java.

Trong ứng dụng, hãy đọc lỗi cùng với **kiểu đang tạo**, **constructor đã tìm**, **quyền truy cập** và **đối số thực tế**. Không chỉ kiểm tra chuỗi thông báo của ngoại lệ.

## 6. setAccessible không phải công tắc bỏ mọi ràng buộc

`setAccessible(true)` và `trySetAccessible()` làm việc với kiểm tra truy cập của Reflection, trong giới hạn mà hệ thống module và môi trường cho phép.

Chúng không:

- Thêm một hằng vào khai báo enum.
- Thay đổi lớp enum thành lớp thường.
- Thay đổi số lượng hoặc kiểu tham số của constructor.
- Biến việc tạo enum bằng `Constructor.newInstance()` thành thao tác hợp lệ.

Thí nghiệm dùng các kiểu do chính chương trình định nghĩa trên classpath, nên việc mở truy cập thành công. Khi khảo sát một kiểu ở module khác, việc mở truy cập có thể thất bại trước khi đi đến câu hỏi tạo đối tượng.

Tài liệu [AccessibleObject.trySetAccessible](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AccessibleObject.html#trySetAccessible()) mô tả giới hạn của thao tác này. Nếu mở được quyền truy cập thì quy tắc enum vẫn giữ nguyên; thay đổi cấu hình mở package không phải cách sửa lỗi cấm tạo enum.

Cũng cần phân biệt **metadata có thể đọc được** với **thao tác có thể thực hiện**. Công cụ có thể hiển thị constructor enum để phục vụ khảo sát chữ ký, annotation hoặc bytecode mà không cần và không được tạo thêm hằng bằng API này.

## 7. “Lấy hằng hiện có” khác với “tạo hằng mới”

Khi cần một giá trị enum từ dữ liệu đầu vào, thường mục tiêu thực tế là chọn một hằng đã được khai báo.

Ví dụ, trong ngữ cảnh chương trình trên:

~~~java
Phase ready = Enum.valueOf(Phase.class, "READY");
System.out.println(ready == Phase.READY);
~~~

Kết quả:

~~~text
true
~~~

`Enum.valueOf` không gọi constructor để tạo thêm `READY`. Nó trả về hằng hiện có mang đúng tên khai báo.

Nếu tên là `EXTRA`, API ném `IllegalArgumentException` vì enum không khai báo hằng đó. Không tự sinh một hằng mới và không tự thêm hằng vào `values()`.

| Nhu cầu | Cách biểu diễn phù hợp |
| --- | --- |
| Chọn hằng có tên đã biết khi viết mã | `Phase.READY` |
| Chuyển tên khai báo thành hằng hiện có | `Enum.valueOf(Phase.class, name)` hoặc `Phase.valueOf(name)` |
| Liệt kê các hằng khi biết enum lúc biên dịch | `Phase.values()` |
| Liệt kê khi chỉ có token lớp | `enumType.getEnumConstants()` |
| Bổ sung hằng cho mô hình enum | Thay đổi khai báo enum và xây dựng lại chương trình |
| Tập giá trị cần thêm hoặc xóa trong lúc chạy | Mô hình dữ liệu có thể mở rộng, chẳng hạn lớp hoặc record kết hợp danh mục đăng ký |

Khi đọc tên từ đầu vào, phải có chính sách xử lý tên không hợp lệ, khác biệt chữ hoa/chữ thường hoặc nhãn hiển thị. `Enum.valueOf` dùng đúng tên khai báo, không dùng chuỗi tùy biến từ `toString()`. Quy tắc API được nêu tại [Enum.valueOf](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Enum.html#valueOf(java.lang.Class,java.lang.String)).

Không dùng constructor Reflection làm nhánh dự phòng khi `valueOf` không tìm thấy tên. Hai việc đó không tương đương nhau.

## 8. Tác động đến công cụ tạo đối tượng tổng quát

Một bộ ánh xạ dữ liệu hoặc factory dựa trên Reflection không nên xử lý enum theo nhánh “tìm constructor không tham số rồi tạo đối tượng”.

Với enum, thông thường cần một **quy tắc chọn hằng** dựa trên dữ liệu: tên khai báo, mã nghiệp vụ đã được định nghĩa, hoặc một ánh xạ do ứng dụng cung cấp.

Một hàm minh họa cho nhánh tạo đối tượng mặc định có thể từ chối enum ngay từ đầu:

~~~java
// Phương thức hỗ trợ; đặt trong một lớp và thêm import java.util.Objects.
static <T> T createDefault(Class<T> type)
        throws ReflectiveOperationException {
    Objects.requireNonNull(type, "type");

    if (type.isEnum()) {
        throw new IllegalArgumentException(
                "Supply an existing enum constant: " + type.getName());
    }

    return type.getDeclaredConstructor().newInstance();
}
~~~

Hàm này chỉ minh họa chính sách không tạo enum bằng constructor. Với lớp thường, nó vẫn yêu cầu một constructor không tham số truy cập được; nó không tự giải quyết mọi trường hợp của một framework tạo đối tượng.

Ví dụ lời gọi khi thêm hàm vào chương trình mẫu:

~~~java
System.out.println(createDefault(StringBuilder.class).length());

try {
    createDefault(Phase.class);
} catch (IllegalArgumentException expected) {
    System.out.println("enum requires an existing constant");
}
~~~

Kết quả của riêng đoạn thêm:

~~~text
0
enum requires an existing constant
~~~

Việc kiểm tra sớm giúp công cụ đưa ra hướng xử lý đúng, thay vì liên tục thử các tổ hợp tham số hoặc mở truy cập rồi gặp lại cùng một quy tắc cấm của Reflection.

## 9. Những hiểu lầm thường gặp

**“Enum không có constructor nên Reflection không tạo được.”** Enum có constructor và có thể khảo sát được bằng Reflection. Hạn chế nằm ở việc tạo instance bằng `Constructor.newInstance()`, không phải ở sự tồn tại của constructor.

**“Chỉ cần setAccessible(true) vì constructor enum là private.”** Mở truy cập không bỏ quy tắc riêng cấm tạo enum.

**“Truyền thêm tên và ordinal là tạo được hằng mới.”** Các đối số hỗ trợ giải thích chữ ký do compiler sinh ra. Chúng không phải API mở rộng tập hằng enum.

**“NoSuchMethodException và IllegalArgumentException là cùng một lỗi.”** Một lỗi xảy ra khi không tìm thấy chữ ký constructor; lỗi còn lại trong thí nghiệm xảy ra khi gọi một constructor đã tìm được.

**“CanAccess trả true thì newInstance chắc chắn thành công.”** Quyền truy cập chỉ là một điều kiện. Enum vẫn bị từ chối; lớp thường cũng có thể thất bại vì các điều kiện khác hoặc vì thân constructor ném ngoại lệ.

**“Đổi thông báo lỗi hoặc bắt InvocationTargetException sẽ giúp tạo được enum.”** Xử lý ngoại lệ chỉ xử lý kết quả thất bại; không biến thao tác bị cấm thành hợp lệ.

**“ValueOf tạo mới enum theo tên yêu cầu.”** Nó chọn hằng đã tồn tại, không tạo hoặc đăng ký thêm hằng.

## 10. Tự kiểm tra

1. Vì sao chương trình có thể lấy được `Constructor<Phase>` nhưng không tạo được `EXTRA`?
2. Trong thí nghiệm, bằng chứng nào cho thấy việc mở quyền truy cập đã thành công?
3. Vì sao gọi `getDeclaredConstructor()` không tham số không tìm thấy constructor trong ví dụ do `javac` biên dịch?
4. Lỗi tạo enum sau khi đã mở truy cập có được bọc trong `InvocationTargetException` như một ngoại lệ từ thân constructor không?
5. Vì sao chỉ nhìn thấy `IllegalArgumentException` chưa đủ để khẳng định nguyên nhân là enum?
6. Sau lần thử tạo `EXTRA`, bộ đếm constructor và `Phase.values()` thay đổi thế nào?
7. Nếu muốn lấy `READY` từ chuỗi `"READY"`, nên gọi API nào?
8. Nếu danh mục nghiệp vụ phải bổ sung phần tử trong lúc chương trình chạy, có nên cố gọi constructor enum để làm việc đó không?

Đáp án:

1. Metadata constructor vẫn tồn tại; hợp đồng `Constructor.newInstance()` cấm tạo instance enum.
2. `trySetAccessible()` trả về `true` và `canAccess(null)` sau đó cũng trả về `true`.
3. Metadata trong ví dụ có hai tham số hỗ trợ `String` và `int`; tra cứu danh sách rỗng không khớp.
4. Không trong thí nghiệm này. Reflection từ chối bằng `IllegalArgumentException` trước khi thực thi thêm thân constructor enum.
5. Vì lỗi số lượng hoặc kiểu đối số cũng có thể gây loại ngoại lệ đó; phải đối chiếu ngữ cảnh.
6. Bộ đếm giữ nguyên ở `2`; danh sách hằng vẫn là `[READY, DONE]`.
7. Dùng `Enum.valueOf(Phase.class, "READY")` hoặc `Phase.valueOf("READY")`; kết quả là hằng hiện có.
8. Không. Hãy dùng một mô hình dữ liệu cho phép tập giá trị thay đổi trong lúc chạy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enum-types-007">Đọc method và field do compiler bổ sung mà không phụ thuộc tên nội bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc method và field do compiler bổ sung mà không phụ thuộc tên nội bộ

## 1. Reflection nhìn thấy lớp đã biên dịch

Mã nguồn một enum có thể rất ngắn, nhưng lớp sau khi biên dịch chứa thêm thành viên để thực hiện quy tắc của Java và hỗ trợ JVM. Vì vậy, danh sách trả về từ `getDeclaredFields()` hoặc `getDeclaredMethods()` không phải bản chép nguyên văn những gì lập trình viên đã gõ trong tệp nguồn.

Khi xây công cụ đọc cấu trúc enum, cần phân biệt ba nhóm:

1. **Thành viên được khai báo tường minh:** field và method do người viết mã khai báo.
2. **Thành viên được ngôn ngữ khai báo ngầm định:** chẳng hạn các field biểu diễn hằng enum và hai method `values()`, `valueOf(String)`.
3. **Thành viên hỗ trợ do trình biên dịch tạo:** có thể gồm field lưu dữ liệu nội bộ, method trợ giúp và phương thức cầu nối (`bridge method`).

Không phải mọi thành viên do compiler bổ sung đều được nhận diện bằng `isSynthetic()`. Đặc biệt, các API ngầm định của enum có vị trí riêng trong hợp đồng ngôn ngữ.

Mục tiêu của bài là đọc và phân loại metadata đúng. Việc gọi method, đọc giá trị field hoặc thay đổi dữ liệu bên trong enum là các thao tác khác.

## 2. Điều gì được Java bảo đảm, điều gì thuộc cách biên dịch?

Với enum `E`, Java quy định:

- Mỗi hằng enum tương ứng với một field `public static final` mang tên hằng và có kiểu `E`.
- Có method `public static E[] values()`.
- Có method `public static E valueOf(String name)`.

Đây là những thành viên có hợp đồng ngôn ngữ. Dùng tên `values` hoặc `valueOf` kèm đúng tham số để tìm API tương ứng không phải là phụ thuộc một tên nội bộ tùy ý.

Ngược lại, cách trình biên dịch lưu mảng hằng hay tách các bước khởi tạo có thể khác nhau. Những tên như `$VALUES` hoặc `$values` có thể xuất hiện khi khảo sát đầu ra của một trình biên dịch, nhưng không phải API công khai mà ứng dụng nên tìm để lấy danh sách hằng.

Không viết thuật toán kiểu “tìm một field có đúng tên nội bộ ấy rồi đọc nó”. Cũng không suy ra vai trò chỉ từ hình dạng tên như bắt đầu bằng `$`, viết hoa hoặc chứa từ `values`.

| Nhu cầu | Điểm dựa phù hợp |
| --- | --- |
| Nhận diện field biểu diễn hằng enum | `Field.isEnumConstant()` |
| Nhận biết field/method được đánh dấu synthetic | `isSynthetic()` trên thành viên |
| Nhận biết method cầu nối | `Method.isBridge()` |
| Nhận diện API ngầm định của enum | Lớp khai báo, tên API, tham số, kiểu trả về và modifier |
| Lấy các đối tượng hằng theo thứ tự khai báo | `Class.getEnumConstants()` |
| Tìm toàn bộ phần triển khai để chẩn đoán bytecode | Giữ metadata và ghi rõ vai trò, không loại bỏ mặc định |

Các cờ metadata trả lời câu hỏi kỹ thuật. Chính sách hiển thị của công cụ phải quyết định nhóm nào được đưa vào tài liệu hoặc màn hình kiểm tra.

## 3. Ví dụ: phân loại field và method của một enum

Lưu thành `EnumCompilerMembersDemo.java`:

~~~java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class EnumCompilerMembersDemo {
    enum TaskState {
        WAITING(10),
        RUNNING(20);

        private final int code;
        public static final TaskState DEFAULT = WAITING;
        public static final String VERSION = "v1";

        TaskState(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    private static String methodLabel(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
        return method.getName() + "(" + parameters + ")->"
                + method.getReturnType().getSimpleName();
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<TaskState> type = TaskState.class;

        String constantFields = Arrays.stream(type.getDeclaredFields())
                .filter(Field::isEnumConstant)
                .map(Field::getName)
                .sorted()
                .collect(Collectors.joining(","));

        String otherFields = Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isEnumConstant())
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .sorted()
                .collect(Collectors.joining(","));

        String methods = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> !method.isBridge())
                .map(EnumCompilerMembersDemo::methodLabel)
                .sorted()
                .collect(Collectors.joining("; "));

        System.out.println("constantFields=" + constantFields);
        System.out.println("otherFields=" + otherFields);
        System.out.println("methods=" + methods);
        System.out.println("DEFAULT.enumConstant="
                + type.getDeclaredField("DEFAULT").isEnumConstant());
        System.out.println("values.synthetic="
                + type.getDeclaredMethod("values").isSynthetic());
        System.out.println("valueOf.synthetic="
                + type.getDeclaredMethod("valueOf", String.class)
                      .isSynthetic());
        System.out.println("declarationOrder="
                + Arrays.toString(type.getEnumConstants()));
    }
}
~~~

Biên dịch và chạy bằng JDK 17:

~~~bash
javac --release 17 EnumCompilerMembersDemo.java
java EnumCompilerMembersDemo
~~~

Kết quả với `javac` của JDK 17:

~~~text
constantFields=RUNNING,WAITING
otherFields=DEFAULT,VERSION,code
methods=code()->int; valueOf(String)->TaskState; values()->TaskState[]
DEFAULT.enumConstant=false
values.synthetic=false
valueOf.synthetic=false
declarationOrder=[WAITING, RUNNING]
~~~

Ví dụ đã chủ động sắp xếp tên field và nhãn method để kết quả in ổn định. Không dựa vào thứ tự mảng metadata do Reflection trả về.

Cần phân biệt **thứ tự hiển thị đã sắp xếp** với **thứ tự hằng trong khai báo**. Danh sách field được sắp theo tên nên có `RUNNING` trước `WAITING`; `getEnumConstants()` vẫn giữ `WAITING` trước `RUNNING`.

Hằng `DEFAULT` là một field do tác giả khai báo, dù nó cũng là `public static final` và có kiểu `TaskState`. `isEnumConstant()` vẫn trả `false`. Vì vậy, chỉ kiểm tra kiểu field và modifier không đủ để nhận diện hằng enum.

Hai method `values()` và `valueOf(String)` xuất hiện dù tác giả không viết phần khai báo của chúng. Trong đầu ra được kiểm tra ở đây, chúng không mang cờ synthetic. Lọc hết thành viên synthetic vẫn giữ lại hai API này.

Chương trình không in tên hoặc số lượng thành viên hỗ trợ nội bộ. Khi đổi compiler hoặc cách biên dịch, chi tiết hỗ trợ có thể đổi mà không làm thay đổi mục đích của phần phân loại.

## 4. isSynthetic không đồng nghĩa với “không do tác giả viết”

`Field.isSynthetic()` và `Method.isSynthetic()` đọc dấu hiệu synthetic của thành viên. Dấu hiệu này hữu ích để nhận diện phần hỗ trợ kỹ thuật, nhưng không tái tạo đầy đủ lịch sử tác giả của mã nguồn.

Có hai chiều cần tránh suy diễn:

- Một thành viên được compiler bổ sung có thể tương ứng với khai báo ngầm định của Java, như các API enum vừa thấy.
- Một thành viên synthetic vẫn có thể quan trọng đối với một công cụ phân tích lớp đã biên dịch, dù không phù hợp để hiển thị như API nghiệp vụ.

Vì vậy, hãy đặt tên chính sách chính xác. Bộ lọc `!method.isSynthetic()` có nghĩa là “bỏ method được đánh dấu synthetic”, không có nghĩa là “chỉ giữ method được viết tường minh trong nguồn”.

Nếu cần xác định chính xác đoạn nào được tác giả viết trong một tệp nguồn, chỉ metadata Reflection không phải một bản đồ nguồn đầy đủ. Có thể cần đọc nguồn hoặc metadata bổ sung phục vụ mục tiêu đó. Đừng gán nhãn “do người dùng viết” cho mọi method không synthetic mà chưa xét thành viên ngầm định.

Tương tự, không mặc định ẩn mọi field `static` hoặc mọi field có kiểu mảng để loại dữ liệu hỗ trợ: enum hoàn toàn có thể khai báo field ứng dụng thuộc các dạng ấy.

## 5. Nếu muốn ẩn API ngầm định, phải kiểm tra đúng hợp đồng

Một công cụ tài liệu có thể muốn giữ API enum chuẩn; công cụ khác chỉ muốn liệt kê phần mở rộng do tác giả định nghĩa. Đó là quyết định hiển thị, cần được tách khỏi cơ chế đọc metadata.

Nếu chính sách yêu cầu ẩn `values()` và `valueOf(String)`, đừng loại mọi method chỉ vì tên là `values` hoặc `valueOf`. Enum có thể có một overload hợp lệ như `valueOf(int)` do tác giả viết.

Lưu thành `EnumApiFilterDemo.java`:

~~~java
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;

public class EnumApiFilterDemo {
    enum Tier {
        BASIC, PREMIUM;

        public static Tier valueOf(int code) {
            return switch (code) {
                case 1 -> BASIC;
                case 2 -> PREMIUM;
                default -> throw new IllegalArgumentException("Unknown code");
            };
        }
    }

    static boolean isImplicitEnumApi(Class<?> enumType, Method method) {
        if (!enumType.isEnum()
                || method.getDeclaringClass() != enumType) {
            return false;
        }

        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers)
                || !Modifier.isStatic(modifiers)) {
            return false;
        }

        if (method.getName().equals("values")) {
            return method.getParameterCount() == 0
                    && method.getReturnType().isArray()
                    && method.getReturnType().getComponentType() == enumType;
        }

        if (method.getName().equals("valueOf")) {
            return method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == String.class
                    && method.getReturnType() == enumType;
        }

        return false;
    }

    private static String methodLabel(Method method) {
        return method.getName() + "("
                + Arrays.stream(method.getParameterTypes())
                      .map(Class::getSimpleName)
                      .collect(Collectors.joining(","))
                + ")";
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<Tier> type = Tier.class;

        Method values = type.getDeclaredMethod("values");
        Method textLookup = type.getDeclaredMethod("valueOf", String.class);
        Method numericLookup = type.getDeclaredMethod("valueOf", int.class);

        System.out.println("values(): implicit="
                + isImplicitEnumApi(type, values));
        System.out.println("valueOf(String): implicit="
                + isImplicitEnumApi(type, textLookup));
        System.out.println("valueOf(int): implicit="
                + isImplicitEnumApi(type, numericLookup));

        String kept = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> !method.isBridge())
                .filter(method -> !isImplicitEnumApi(type, method))
                .map(EnumApiFilterDemo::methodLabel)
                .sorted()
                .collect(Collectors.joining("; "));
        System.out.println("kept=" + kept);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 EnumApiFilterDemo.java
java EnumApiFilterDemo
~~~

Kết quả:

~~~text
values(): implicit=true
valueOf(String): implicit=true
valueOf(int): implicit=false
kept=valueOf(int)
~~~

Hàm nhận diện dùng đối tượng `Class` để kiểm tra kiểu trả về và kiểu tham số, không so sánh các chuỗi tên kiểu đã rút gọn. Các tên đơn giản trong `methodLabel` chỉ phục vụ bản in của ví dụ.

Việc lọc theo hợp đồng này áp dụng cho một lớp enum Java hợp lệ. Nó không phải bộ nhận diện mọi thành viên do mọi bộ sinh bytecode có thể tạo ra.

Các kiểm tra về modifier và lớp khai báo còn giúp tránh nhầm một method cùng tên nhưng khác vai trò. Chính sách ẩn chỉ loại hai API đã xác định; overload `valueOf(int)` vẫn được giữ.

## 6. Bridge method: cùng tên và tham số vẫn có thể thấy hai method

Enum cũng có thể triển khai interface generic. Khi cần nối hợp đồng sau xóa kiểu với phần thực hiện có kiểu cụ thể hơn, trình biên dịch có thể tạo bridge method.

Đây không phải một API enum mới. Nó là phần hỗ trợ để lời gọi qua kiểu interface vẫn đến đúng phần thực hiện.

Lưu thành `EnumBridgeMetadataDemo.java`:

~~~java
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

public class EnumBridgeMetadataDemo {
    interface CodeSupplier<T> {
        T code();
    }

    enum Signal implements CodeSupplier<String> {
        ON;

        @Override
        public String code() {
            return "on";
        }
    }

    public static void main(String[] args) {
        Arrays.stream(Signal.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("code"))
                .sorted(Comparator.comparing(
                        method -> method.getReturnType().getName()))
                .forEach(method -> System.out.println(
                        method.getName() + " -> "
                        + method.getReturnType().getSimpleName()
                        + " synthetic=" + method.isSynthetic()
                        + " bridge=" + method.isBridge()));
    }
}
~~~

Biên dịch và chạy bằng JDK 17:

~~~bash
javac --release 17 EnumBridgeMetadataDemo.java
java EnumBridgeMetadataDemo
~~~

Kết quả với `javac` của JDK 17:

~~~text
code -> Object synthetic=true bridge=true
code -> String synthetic=false bridge=false
~~~

Tác giả chỉ viết một method `String code()`. Method trả `Object` là bridge được compiler tạo để phục vụ hợp đồng của `CodeSupplier<T>` sau xóa kiểu.

Nếu công cụ tài liệu hiển thị cả hai như hai thao tác nghiệp vụ riêng biệt, người dùng có thể thấy API bị lặp. Một chính sách tài liệu thông thường có thể loại method bridge. Một công cụ chẩn đoán bytecode nên giữ nó và ghi rõ cờ tương ứng.

Không ghép hoặc loại method chỉ theo tên. Ở mức Reflection/JVM, có thể quan sát các method trùng tên và kiểu tham số nhưng khác kiểu trả về, như ví dụ này. Khi tạo khóa metadata, cần giữ thông tin lớp khai báo, tên, tham số và kiểu trả về phù hợp với mục tiêu của công cụ.

`isBridge()` trả lời vai trò cầu nối cụ thể. `isSynthetic()` trả lời dấu hiệu synthetic rộng hơn. Bridge method là một trường hợp synthetic; không phải mọi method synthetic đều là bridge.

## 7. Chọn đúng phạm vi đọc trước khi lọc

`getDeclaredFields()` và `getDeclaredMethods()` lấy thành viên được khai báo trực tiếp trong lớp đang xét, bao gồm cả thành viên không public. Chúng không tự gộp mọi thành viên kế thừa.

Điều này giải thích vì sao `values()` và `valueOf(String)` có trong metadata trực tiếp của enum, còn các method như `name()` và `ordinal()` đến từ lớp cơ sở `Enum`.

Nếu mục tiêu là API public gồm phần kế thừa, `getMethods()` có phạm vi khác. Hãy xác định phạm vi trước, rồi mới áp dụng chính sách synthetic/bridge/enum API. Nếu không, công cụ có thể “thiếu” hoặc “thừa” method vì chọn sai phạm vi từ đầu.

Khi bắt đầu từ một đối tượng hằng enum có thân riêng, lớp thực tế của hằng còn có thể là lớp con ẩn danh. Nếu muốn khảo sát lớp enum chung, chuẩn hóa bằng `constant.getDeclaringClass()`; nếu muốn khảo sát phần riêng của hằng, dùng lớp thực tế của nó.

Không dùng thứ tự mảng field/method từ Reflection làm thứ tự nguồn. Sắp xếp bản trình bày khi cần đầu ra ổn định; lấy danh sách hằng qua API enum khi cần thứ tự khai báo của các hằng.

## 8. Quan sát metadata không đòi hỏi đọc dữ liệu nội bộ

Các ví dụ dùng tên, kiểu, modifier và cờ thành viên để phân loại. Không cần gọi `setAccessible(true)` hoặc đọc field private để biết một field có phải enum constant hay synthetic hay không.

Nếu chỉ cần các giá trị hằng, dùng `getEnumConstants()`. Nếu đã biết kiểu enum trong nguồn, dùng `values()`. Cả hai giúp mã tránh phụ thuộc cách compiler cất dữ liệu hỗ trợ.

Đọc một field nội bộ không tạo thêm một hợp đồng ổn định cho ứng dụng. Thay đổi mảng lưu trữ hỗ trợ, nếu tìm thấy và truy cập được, còn có thể phá giả định của enum hoặc của thư viện khác. Khả năng nhìn thấy metadata không phải lý do để sửa trạng thái ấy.

Cũng cần giữ ranh giới thực thi: lấy `Method` chỉ mô tả method; gọi `invoke` mới thực hiện lời gọi. Đọc giá trị field tĩnh hoặc lấy các đối tượng hằng có thể liên quan đến khởi tạo lớp. Một trình xem metadata không nên gọi thử method chỉ để quyết định thành viên thuộc nhóm nào.

## 9. Lỗi thường gặp và cách kiểm tra

| Sai lầm | Điều cần kiểm tra lại |
| --- | --- |
| Tìm field có tên nội bộ cố định để lấy các hằng | Dùng API enum thay vì dữ liệu hỗ trợ |
| Nhận diện hằng chỉ bằng public/static/final và kiểu field | Dùng `Field.isEnumConstant()`; một field bí danh có thể cùng kiểu và modifier |
| Coi mọi method không synthetic là method tác giả viết | Xét các API được khai báo ngầm định |
| Loại mọi method tên `valueOf` | Kiểm tra tham số, kiểu trả về, modifier và lớp khai báo |
| Hiển thị bridge như một thao tác nghiệp vụ thứ hai | Quyết định chính sách `isBridge()` theo mục đích |
| Loại synthetic ngay khi đọc rồi không lưu dấu vết | Công cụ chẩn đoán có thể cần chính những thành viên ấy |
| So sánh ảnh chụp metadata theo thứ tự JVM trả về | Sắp xếp khóa/nhãn trước khi so sánh |
| Dùng số lượng field/method nội bộ như một cam kết | Chỉ kiểm tra hợp đồng cần thiết, không đóng cứng chi tiết compiler |
| Gọi API đọc giá trị private chỉ để phân loại | Dùng metadata công khai và các cờ vai trò |

Một cách tổ chức rõ ràng là: thu thập metadata, phân loại vai trò, rồi chọn chính sách trình bày. Như vậy, một bộ đọc có thể phục vụ cả màn hình API lẫn chế độ chẩn đoán mà không phải đoán vai trò từ tên thành viên.

## 10. Tự kiểm tra

1. Vì sao bộ lọc `!method.isSynthetic()` vẫn có thể giữ `values()` và `valueOf(String)`?
2. Một field `public static final MyEnum DEFAULT` có chắc là hằng enum không?
3. Nếu enum khai báo thêm `valueOf(int)`, có nên loại nó chỉ vì tên method là `valueOf`?
4. Trong ví dụ generic, tại sao thấy cả `Object code()` và `String code()`?
5. Muốn danh sách hằng theo thứ tự nguồn, nên sắp xếp field hay dùng API nào?
6. Có nên ghi cứng tên hoặc số lượng field lưu trữ nội bộ trong một bài kiểm tra tính đúng của ứng dụng không?

Đáp án:

1. Chúng là API được Java khai báo ngầm định; không thể đồng nhất “compiler bổ sung” với “synthetic”.
2. Không. Dùng `Field.isEnumConstant()` để phân biệt field hằng enum với field thông thường.
3. Không. Đó có thể là overload do tác giả viết; phải xét đúng hợp đồng cần lọc.
4. Compiler tạo bridge để nối hợp đồng generic sau xóa kiểu với phần thực hiện trả `String`.
5. Dùng `Class.getEnumConstants()` hoặc `values()` khi biết kiểu cụ thể.
6. Không. Kiểm tra hành vi/hợp đồng cần thiết và tránh phụ thuộc các chi tiết hỗ trợ có thể thay đổi.

## Tài liệu đối chiếu

- [JLS 17 — Các thành viên ngầm định của enum](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.9.3).
- [JLS 17 — Dạng nhị phân, synthetic và khai báo ngầm định](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html#jls-13.1).
- [Java 17 — Field.isEnumConstant và isSynthetic](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html#isEnumConstant()).
- [Java 17 — Method.isBridge và isSynthetic](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#isBridge()).


</details>

- [Quay lại đầu trang](#back-to-top)
