<a id="back-to-top"></a>

# Mảng động

## Menu
- [Array.newInstance với component type biết lúc chạy](#dynamic-arrays-001)
- [Tạo mảng nhiều chiều và ý nghĩa của danh sách kích thước](#dynamic-arrays-002)
- [Array.getLength và kiểm tra isArray](#dynamic-arrays-003)
- [Array.get, Array.set và các biến thể primitive](#dynamic-arrays-004)
- [Boxing khi đọc mảng primitive bằng Array.get](#dynamic-arrays-005)
- [Điều kiện widening và lỗi sai kiểu khi gán phần tử](#dynamic-arrays-006)
- [getComponentType cho từng tầng mảng](#dynamic-arrays-007)
- [ArrayIndexOutOfBoundsException, NegativeArraySizeException và đầu vào không phải mảng](#dynamic-arrays-008)
- [Mảng generic, erasure và giới hạn tạo T&#91;&#93;](#dynamic-arrays-009)
- [Không ép int&#91;&#93; thành Object&#91;&#93;](#dynamic-arrays-010)

## <a id="dynamic-arrays-001">Array.newInstance với component type biết lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Array.newInstance với kiểu thành phần xác định lúc chạy

## 1. Từ một đối tượng Class đến một mảng thật

Khi viết `new String[3]`, ta đã chỉ rõ kiểu `String` trong mã nguồn. Nhưng một bộ chuyển đổi dữ liệu, công cụ kiểm thử hoặc thư viện xử lý nhiều loại đối tượng có thể chỉ nhận được một `Class<?>` lúc chương trình đang chạy. Khi đó, `java.lang.reflect.Array.newInstance` tạo mảng dựa trên đối tượng `Class` ấy.

Ở đây, **kiểu thành phần (component type)** là kiểu của mỗi ô trực tiếp trong mảng. Với `String[]`, đó là `String`; với `String[][]`, đó là `String[]`. Bài học tập trung vào việc chọn đúng kiểu và cấp phát đúng hình dạng mảng.

“Mảng động” trong ngữ cảnh này nghĩa là **quyết định kiểu hoặc kích thước khi chạy**. Mảng được tạo vẫn có độ dài cố định. Muốn thêm phần tử vượt độ dài cũ, ta phải tạo mảng khác và sao chép dữ liệu, hoặc dùng cấu trúc như `ArrayList`.

Ví dụ minh họa trong một phương thức, với import `java.lang.reflect.Array`:

~~~java
Class<?> componentType = String.class;
Object result = Array.newInstance(componentType, 3);
~~~

Kết quả là một đối tượng có kiểu thực tế `String[]`, dù biến `result` được khai báo là `Object`. Đây là **cấp phát mảng**, không chỉ là truy vấn thông tin về một kiểu.

## 2. Hợp đồng của hai overload

Hai dạng sử dụng chính:

~~~java
Array.newInstance(Class<?> componentType, int length)
Array.newInstance(Class<?> componentType, int... dimensions)
~~~

Đoạn trên biểu diễn cách gọi API, không phải khai báo phương thức đầy đủ để biên dịch.

- `componentType` mô tả kiểu dùng để tạo mảng.
- `length` là độ dài của một chiều mới.
- `dimensions` cho biết độ dài của các chiều mới.
- Giá trị trả về có kiểu khai báo `Object`, còn kiểu mảng thực tế được quyết định bởi đầu vào.

Bảng đọc nhanh:

| Lời gọi | Kiểu thực tế | Nội dung ngay sau khi tạo |
| --- | --- | --- |
| `Array.newInstance(String.class, 3)` | `String[]` | Ba ô chứa `null` |
| `Array.newInstance(int.class, 3)` | `int[]` | Ba ô chứa `0` |
| `Array.newInstance(Integer.class, 3)` | `Integer[]` | Ba ô chứa `null` |
| `Array.newInstance(Runnable.class, 2)` | `Runnable[]` | Hai ô chứa `null` |
| `Array.newInstance(String.class, 2, 3)` | `String[][]` | Hai mảng con, mỗi mảng có ba ô `null` |
| `Array.newInstance(String[].class, 2)` | `String[][]` | Hai ô ngoài chứa `null`; chưa tạo mảng con |

Lời gọi với một số nguyên, chẳng hạn `newInstance(String.class, 3)`, dùng overload `int length`. Muốn truyền các kích thước như một giá trị, có thể dùng `newInstance(String.class, new int[] {2, 3})`.

Không viết `new componentType[3]`: một biến kiểu `Class<?>` không thể thay thế tên kiểu trong cú pháp `new T[n]`.

## 3. Ví dụ chạy được: cùng một luồng xử lý, hai kiểu mảng

Lưu thành `DynamicArrayCreationDemo.java`. Chương trình dùng một tham số dòng lệnh để chọn kiểu thành phần khi chạy:

~~~java
import java.lang.reflect.Array;

public class DynamicArrayCreationDemo {
    private static Class<?> selectComponentType(String choice) {
        return switch (choice) {
            case "text" -> String.class;
            case "count" -> int.class;
            default -> throw new IllegalArgumentException(
                    "Expected text or count");
        };
    }

    public static void main(String[] args) {
        String choice = args.length == 0 ? "text" : args[0];
        Class<?> componentType = selectComponentType(choice);
        Object array = Array.newInstance(componentType, 3);

        System.out.println("type=" + array.getClass().getTypeName());
        System.out.println("component="
                + array.getClass().getComponentType().getTypeName());
        System.out.println("length=" + Array.getLength(array));
        System.out.println("default[0]=" + Array.get(array, 0));

        if (componentType == int.class) {
            Array.setInt(array, 0, 7);
        } else {
            Array.set(array, 0, "Lan");
        }

        System.out.println("value[0]=" + Array.get(array, 0));
        System.out.println("is Object[]=" + (array instanceof Object[]));
    }
}
~~~

Biên dịch bằng JDK 17 trở lên:

~~~bash
javac --release 17 DynamicArrayCreationDemo.java
java DynamicArrayCreationDemo text
~~~

Kết quả:

~~~text
type=java.lang.String[]
component=java.lang.String
length=3
default[0]=null
value[0]=Lan
is Object[]=true
~~~

Chạy lại với lựa chọn còn lại:

~~~bash
java DynamicArrayCreationDemo count
~~~

Kết quả:

~~~text
type=int[]
component=int
length=3
default[0]=0
value[0]=7
is Object[]=false
~~~

Hãy đọc ví dụ theo ba bước:

1. `selectComponentType` quyết định đối tượng `Class` từ dữ liệu đầu vào.
2. `Array.newInstance` dùng kiểu đó để tạo mảng có ba ô, với giá trị mặc định tương ứng.
3. `Array.getLength`, `Array.get` và `Array.setInt`/`Array.set` thao tác trên mảng qua biến `Object`.

Phần ghi và đọc phần tử chỉ nhằm chứng minh mảng đã được tạo đúng kiểu. Việc tạo mảng không tự điền các giá trị nghiệp vụ như `"Lan"` hoặc `7`.

Cách chọn kiểu trong ví dụ là một danh sách lựa chọn nhỏ, dễ kiểm soát. Trong ứng dụng thật, `Class<?>` còn có thể đến từ tham số phương thức, metadata của một field hoặc kiểu thực tế của một mảng có sẵn. Cần xác định rõ nguồn nào là hợp đồng đúng cho dữ liệu đang xử lý.

## 4. Vì sao kết quả là Object, và khi nào ép kiểu được?

Mảng nguyên thủy như `int[]` cũng là một đối tượng, nên có thể gán cho biến `Object`. Nhưng `int[]` **không phải** là `Object[]`. Các phần tử của nó là giá trị `int`, không phải các tham chiếu đối tượng.

Các đoạn sau là minh họa trong một phương thức có import `java.lang.reflect.Array`:

~~~java
Object raw = Array.newInstance(int.class, 2);
int[] numbers = (int[]) raw;       // Hop le: kieu thuc te la int[].
// Object[] wrong = (Object[]) raw; // Neu chay: ClassCastException.
~~~

Ngược lại:

~~~java
Object raw = Array.newInstance(String.class, 2);
String[] names = (String[]) raw;  // Hop le: kieu thuc te la String[].
Object[] references = names;     // String[] la mot loai Object[].
~~~

Phép ép kiểu không chuyển đổi mảng này thành mảng khác. Nó chỉ kiểm tra xem đối tượng hiện có có phù hợp với kiểu đích hay không. Vì thế:

- Nếu mã gọi biết chắc hợp đồng trả về là `String[]`, có thể ép về `String[]`.
- Nếu mã gọi phải hỗ trợ cả `String[]`, `int[]`, `boolean[]` và các kiểu khác, nên giữ biến `Object` rồi dùng API `Array`.
- Muốn biến `int[]` thành `Integer[]`, phải tạo mảng đích và chuyển từng giá trị; ép kiểu không làm việc đóng hộp cho toàn bộ mảng.

`Array.get` trả về `Object`; khi đọc từ mảng nguyên thủy, giá trị được đóng hộp. Điều đó giải thích vì sao lệnh in trong ví dụ có thể xử lý cả hai loại mảng, nhưng **không biến `int[]` thành mảng tham chiếu**.

Kiểu `int.class` và `Integer.class` là hai đầu vào khác nhau. Chọn nhầm chúng sẽ tạo sai loại mảng ngay từ bước cấp phát.

## 5. Mảng nhiều chiều: phân biệt kiểu và các mảng con đã được tạo

Java biểu diễn mảng nhiều chiều bằng mảng chứa các tham chiếu đến mảng con. Vì vậy, cần trả lời riêng hai câu hỏi:

1. Đối tượng ngoài cùng có kiểu gì?
2. Những mảng con nào đã được cấp phát, với độ dài bao nhiêu?

Nếu kiểu đầu vào đã có `k` chiều và lời gọi bổ sung `d` kích thước, kiểu kết quả có `k + d` chiều. Các chiều đã có trong kiểu đầu vào không tự được cấp phát thành mảng con.

Ví dụ:

| Đầu vào | Kiểu kết quả | Phần được cấp phát |
| --- | --- | --- |
| `String.class, 2, 3` | `String[][]` | Mảng ngoài dài 2 và hai mảng `String[]` dài 3 |
| `String[].class, 2` | `String[][]` | Mảng ngoài dài 2; mỗi ô đang là `null` |
| `String[].class, 2, 3` | `String[][][]` | Mảng ngoài dài 2, các mảng tầng tiếp theo dài 3; các ô chứa `String[]` ở tầng ấy vẫn là `null` |

Đừng truyền `String[].class` khi ý định là tạo một `String[]`. Đầu vào đó đã là một kiểu mảng; thêm một chiều sẽ tạo `String[][]`.

Lưu chương trình sau thành `ArrayDimensionsDemo.java`:

~~~java
import java.lang.reflect.Array;
import java.util.Arrays;

public class ArrayDimensionsDemo {
    public static void main(String[] args) {
        Object rectangular = Array.newInstance(String.class, 2, 3);
        Object outerOnly = Array.newInstance(String[].class, 2);

        System.out.println("rect.type="
                + rectangular.getClass().getTypeName());
        System.out.println("rect.component="
                + rectangular.getClass().getComponentType().getTypeName());
        System.out.println("rect.rows=" + Array.getLength(rectangular));
        System.out.println("rect.row0.length="
                + Array.getLength(Array.get(rectangular, 0)));
        System.out.println("rect.values="
                + Arrays.deepToString((Object[]) rectangular));

        System.out.println("outer.type="
                + outerOnly.getClass().getTypeName());
        System.out.println("outer.row0=" + Array.get(outerOnly, 0));

        Array.set(outerOnly, 0, new String[] {"A"});
        System.out.println("outer.values="
                + Arrays.deepToString((Object[]) outerOnly));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 ArrayDimensionsDemo.java
java ArrayDimensionsDemo
~~~

Kết quả:

~~~text
rect.type=java.lang.String[][]
rect.component=java.lang.String[]
rect.rows=2
rect.row0.length=3
rect.values=[[null, null, null], [null, null, null]]
outer.type=java.lang.String[][]
outer.row0=null
outer.values=[[A], null]
~~~

Hai mảng có cùng kiểu thực tế `String[][]`, nhưng hình dạng dữ liệu khác nhau. Mảng thứ nhất được tạo đầy đủ theo hai kích thước. Mảng thứ hai mới có tầng ngoài; mã phải gán từng mảng con khi cần.

`getComponentType()` bóc đúng **một tầng**. Với `String[][]`, nó trả về `String[].class`, không trả ngay `String.class`. Muốn biết kiểu phần tử ở tầng sâu nhất, cần tiếp tục bóc khi `Class.isArray()` còn trả về `true`.

Độ dài bằng 0 vẫn hợp lệ: `Array.newInstance(int.class, 2, 0)` tạo một `int[][]` có hai mảng con rỗng. Độ dài và số chiều là hai thông tin khác nhau.

## 6. Lấy component type từ một mảng có sẵn

Một nhu cầu thực tế là tạo mảng mới cùng kiểu với mảng nguồn, nhưng có độ dài khác. Đoạn sau là một phương thức tiện ích; đặt trong một lớp có import `java.lang.reflect.Array` và `java.util.Objects`:

~~~java
static Object emptyLike(Object sourceArray, int newLength) {
    Objects.requireNonNull(sourceArray, "sourceArray");
    Class<?> arrayType = sourceArray.getClass();
    if (!arrayType.isArray()) {
        throw new IllegalArgumentException("Expected an array");
    }
    return Array.newInstance(arrayType.getComponentType(), newLength);
}
~~~

Ví dụ gọi trong cùng lớp:

~~~java
Object first = emptyLike(new long[] {10L, 20L}, 4);
// first la long[4], tat ca gia tri ban dau bang 0L.

Object second = emptyLike(new String[][] {{"A"}}, 3);
// second la String[3][], ba tham chieu mang con deu null.
~~~

Hàm này **chỉ tạo mảng rỗng cùng kiểu**, không sao chép phần tử. Với mảng nhiều chiều, nó cấp phát tầng ngoài và giữ kiểu của các ô bên trong; không nhân bản các mảng con.

Phải kiểm tra `isArray()` trước khi lấy component type. Với một lớp không phải mảng, `getComponentType()` trả về `null`; đưa giá trị đó cho `Array.newInstance` không thể tạo mảng mong muốn.

Cũng cần phân biệt **kiểu khai báo** và **kiểu thực tế**. Nếu biến khai báo `Number[]` đang trỏ đến một `Integer[]`, lấy từ `sourceArray.getClass()` sẽ giữ kiểu `Integer[]`. Nếu yêu cầu nghiệp vụ là tạo `Number[]`, cần truyền `Number.class` rõ ràng thay vì suy ra từ đối tượng nguồn.

## 7. Tạo mảng không phải tạo từng phần tử

Với kiểu tham chiếu, bước cấp phát tạo các ô chứa `null`. Ví dụ `Array.newInstance(Runnable.class, 2)` hợp lệ dù `Runnable` là interface. Không có đối tượng thực thi `Runnable` nào được sinh ra bởi lời gọi ấy.

Tương tự, có thể tạo mảng của một lớp trừu tượng hoặc một lớp không có constructor không tham số. Không cần gọi constructor của lớp thành phần để tạo mảng tham chiếu. Việc tạo hoặc tìm từng đối tượng để điền vào các ô là một bước riêng.

Với kiểu nguyên thủy, các ô nhận giá trị mặc định: kiểu số nhận 0 tương ứng, `boolean` nhận `false`, `char` nhận ký tự `'\u0000'`. Ký tự này không phải chữ số `'0'` và thường không nhìn thấy khi in trực tiếp.

Vì vậy, khi thiết kế một bộ chuyển đổi dữ liệu, hãy tách rõ:

1. Chọn đúng kiểu mảng đích.
2. Cấp phát đúng độ dài/hình dạng.
3. Chuyển đổi và gán từng giá trị tương thích.

Thành công ở bước 2 chưa chứng minh dữ liệu đầu vào chuyển đổi được ở bước 3.

## 8. Giới hạn đầu vào và lỗi thường gặp

| Tình huống | Kết quả cần dự kiến |
| --- | --- |
| `Array.newInstance(null, 3)` | `NullPointerException`: thiếu kiểu thành phần |
| `Array.newInstance(String.class, -1)` | `NegativeArraySizeException` |
| `Array.newInstance(String.class, 0)` | Hợp lệ: tạo mảng rỗng |
| `Array.newInstance(void.class, 1)` | `IllegalArgumentException`: không có mảng `void[]` |
| `Array.newInstance(String.class, new int[0])` | `IllegalArgumentException`: không cung cấp chiều nào |
| Tổng số chiều yêu cầu vượt 255 | `IllegalArgumentException` |
| Độ dài hợp lệ nhưng không đủ bộ nhớ để cấp phát | Có thể gặp `OutOfMemoryError` |

`new int[0]` trong bảng là **một danh sách kích thước rỗng**, không phải một chiều có độ dài 0. Một chiều rỗng phải được diễn tả bằng `new int[] {0}` hoặc overload nhận `length = 0`.

Kiểm tra độ dài không âm chưa đủ cho dữ liệu bên ngoài. Một kích thước rất lớn vẫn có thể làm ứng dụng tiêu thụ quá nhiều bộ nhớ; nên áp dụng giới hạn theo yêu cầu của ứng dụng trước khi cấp phát. Với nhiều chiều, cần xét toàn bộ hình dạng, không chỉ độ dài mảng ngoài.

Đừng bắt mọi lỗi rồi trả về `new Object[0]` như một cách “dự phòng” âm thầm. Cách đó làm mất kiểu đích và che giấu đầu vào sai; mã sử dụng sau đó sẽ khó xác định nguyên nhân.

## 9. Liên hệ với generic và lựa chọn API

Java không cho viết `new T[n]` với một biến kiểu generic thông thường, vì kiểu `T` không cung cấp đủ thông tin cụ thể để cấp phát mảng trong mã ấy. Truyền thêm một đối tượng `Class` là một cách cung cấp kiểu lúc chạy cho `Array.newInstance`.

Tuy nhiên, Reflection không khôi phục mọi thông tin generic đã bị xóa:

- Không có cú pháp `List<String>.class`.
- `Array.newInstance(List.class, n)` tạo mảng có kiểu thực tế `List[]`; JVM không vì thế kiểm chứng rằng mọi danh sách bên trong chỉ chứa `String`.
- Không được hứa rằng một hàm trả `T[]` hỗ trợ mọi đầu vào `Class`, bao gồm kiểu nguyên thủy, chỉ bằng một phép ép kiểu không kiểm tra.

Trong bài này, kiểu trả về `Object` giúp giữ đúng khả năng hỗ trợ cả mảng nguyên thủy và mảng tham chiếu. Khi thiết kế một hàm generic riêng, phải xác định hợp đồng kiểu tham chiếu và kiểm soát chỗ ép kiểu; không sao chép phép ép `(T[])` mà bỏ qua điều kiện hợp lệ.

Nên dùng `new String[n]` khi đã biết kiểu trong mã nguồn: cách này trực tiếp và được trình biên dịch kiểm tra rõ ràng. Dùng `Array.newInstance` khi việc chọn kiểu là dữ liệu của quá trình chạy. Nếu nhu cầu chính là tăng/giảm số phần tử liên tục, cân nhắc collection thay vì dùng Reflection cho mọi thao tác.

## 10. Tự kiểm tra

1. `Array.newInstance(Integer.class, 2)` tạo `Integer[]` hay `int[]`? Giá trị mặc định là gì?
2. Vì sao một `int[]` có thể gán vào `Object` nhưng không thể ép thành `Object[]`?
3. `Array.newInstance(String[].class, 4)` có tạo bốn chuỗi hay bốn mảng con hay không?
4. Muốn tạo đầy đủ một bảng `String[4][2]`, nên truyền kiểu nào và các kích thước nào?
5. Hàm `emptyLike` có sao chép giá trị từ mảng nguồn không?

Đáp án:

1. `Integer[]`, hai ô `null`; `int.class` mới tạo mảng nguyên thủy `int[]`.
2. Bản thân mảng là một đối tượng, nhưng các ô `int` không phải tham chiếu đối tượng để tạo thành `Object[]`.
3. Nó tạo `String[4][]`; cả bốn ô ngoài đều `null`, chưa có mảng con hoặc chuỗi nào được tạo.
4. `Array.newInstance(String.class, 4, 2)`.
5. Không. Hàm chỉ giữ kiểu thực tế của mảng và cấp phát một mảng mới với giá trị mặc định.

Điểm cần nhớ: đối tượng `Class` là thông tin đầu vào để chọn kiểu; `Array.newInstance` dùng thông tin ấy để cấp phát một mảng thật. Kiểu mảng, số chiều, độ dài và giá trị các ô là những khía cạnh phải kiểm tra riêng.

## Tài liệu đối chiếu

- [Java 17 — Array.newInstance và API thao tác mảng](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Array.html).
- [Java 17 — Class.getComponentType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getComponentType()).
- [JLS 17 — Chương 10: kiểu mảng và mảng nhiều chiều](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html).
- [JLS 17 — Tạo mảng](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.10.1).
- [JLS 17 — Kiểu có thông tin đầy đủ lúc chạy](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.7).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-arrays-002">Tạo mảng nhiều chiều và ý nghĩa của danh sách kích thước</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo mảng nhiều chiều và ý nghĩa của danh sách kích thước trong Java Reflection

Khi kiểu phần tử, số chiều hoặc độ dài mảng chỉ được biết lúc chạy, Java cung cấp **java.lang.reflect.Array.newInstance(Class<?> componentType, int... dimensions)**. Phương thức tạo một mảng thật ở runtime, dùng **componentType** xác định kiểu và mảng số nguyên **dimensions** xác định độ dài các chiều cần cấp phát.

Quan trọng nhất: **mỗi phần tử của dimensions là độ dài của một chiều, theo thứ tự từ ngoài vào trong**. Đây không phải danh sách chỉ số, không phải giới hạn trên bao gồm, và cũng không phải các độ dài của những hàng khác nhau. Ví dụ:

~~~java
int[][] matrix = (int[][]) java.lang.reflect.Array.newInstance(int.class, 2, 3);
~~~

Kết quả tương đương về kiểu và hình dạng ban đầu với **new int[2][3]**: 2 hàng, mỗi hàng có 3 phần tử int mặc định bằng 0.

Bài học đi sâu vào ý nghĩa dimensions, đặc biệt khi **componentType vốn đã là array class**, cách cấp phát từng phần để dựng mảng răng cưa, trường hợp kích thước 0, ngoại lệ và thao tác kiểm chứng bằng Reflection.

## 1. Hai overload của Array.newInstance

Lớp java.lang.reflect.Array cung cấp:

~~~java
static Object newInstance(Class<?> componentType, int length)
static Object newInstance(Class<?> componentType, int... dimensions)
~~~

- **newInstance(componentType, length):** tạo một mảng ngoài có độ dài length, chứa phần tử thuộc componentType. Nếu componentType là kiểu mảng, phần tử ngoài là tham chiếu đến mảng con, ban đầu null.
- **newInstance(componentType, dimensions):** nhận một hay nhiều số nguyên để tạo các chiều ngoài tương ứng. Dạng int... cho phép truyền các số rời hoặc một int[].
- **Kết quả tĩnh là Object:** vì componentType có thể thay đổi tại runtime. Đối tượng thực tế vẫn là một Java array mang class đúng kiểu; có thể cast hoặc thao tác tiếp bằng Array.get, Array.set, Array.getLength.
- **componentType hợp lệ:** kiểu primitive như int.class, tham chiếu như String.class, hoặc class của mảng như String[].class. Không thể là void.class.

Ví dụ hai biểu thức đầu tạo mảng cùng hình dạng với biểu thức cuối, nhưng mỗi lần gọi đều cấp phát đối tượng mới:

~~~java
Object first = Array.newInstance(String.class, 4, 5);
Object second = Array.newInstance(String.class, new int[] {4, 5});
String[][] third = new String[4][5];
~~~

Các ví dụ sử dụng **import java.lang.reflect.Array;**. Cả first, second và third đều có runtime class String[][], nhưng không tham chiếu cùng một mảng.

## 2. Đọc chính xác danh sách dimensions

Với componentType là một class **không phải mảng**, số chiều kết quả bằng đúng **dimensions.length**:

| Gọi Array.newInstance | Kiểu runtime | Độ dài các chiều được cấp phát | Số slot cuối |
| --- | --- | --- | --- |
| (int.class, 5) | int[] | 5 | 5 |
| (int.class, 2, 3) | int[][] | 2 rồi 3 | 6 |
| (int.class, 2, 3, 4) | int[][][] | 2 rồi 3 rồi 4 | 24 |
| (String.class, 3, 2) | String[][] | 3 rồi 2 | 6 |
| (String.class, 2, 0, 4) | String[][][] | 2 rồi 0; chiều sâu không có vị trí tiếp cận | 0 |

Nếu truyền **{2, 3, 4}**, ta đọc:

1. **dimensions[0] = 2:** mảng ngoài chứa hai phần tử.
2. **dimensions[1] = 3:** mỗi phần tử ngoài chứa một mảng có ba phần tử.
3. **dimensions[2] = 4:** mỗi phần tử ở tầng hai chứa một mảng có bốn phần tử cuối.

Với int[][] kích thước 2 × 3, cấu trúc ban đầu là:

~~~text
int[][] data, data.length = 2
    data[0] -> int[3] = [0, 0, 0]
    data[1] -> int[3] = [0, 0, 0]

data[1][2] -> phần tử thứ ba của hàng thứ hai.
~~~

Chỉ số của chiều có độ dài 2 là 0 và 1; chỉ số của chiều có độ dài 3 là 0, 1 và 2. Nếu truy cập data[2] trong mảng ngoài dài 2, sẽ nhận **ArrayIndexOutOfBoundsException**.

**Tích các kích thước** có thể cho số vị trí phần tử cuối về mặt logic nếu toàn bộ các chiều đến kiểu phần tử cuối đều được cấp phát. Nó không cho biết trực tiếp số byte heap, vì Java cấp phát các đối tượng mảng con và overhead riêng.

## 3. Mảng nhiều chiều thực chất là mảng của mảng

Trong Java, int[][] là một mảng mà mỗi phần tử có kiểu int[]. Tương tự String[][][] chứa các phần tử có kiểu String[][]. Đây không phải mô hình bắt buộc mọi hàng luôn cùng kích thước.

Khi gọi Array.newInstance(int.class, 2, 3), Java tạo hình dạng ban đầu đều nhau: hai mảng con dài 3. Nhưng sau đó ta vẫn có thể thay một mảng con bằng mảng có độ dài khác:

~~~java
int[][] rows = new int[3][];
rows[0] = new int[1];
rows[1] = new int[4];
rows[2] = new int[2];

System.out.println(rows.length);     // 3
System.out.println(rows[1].length);  // 4
~~~

Đây là **mảng răng cưa (jagged/ragged array)**, có ba hàng dài 1, 4, 2. Mỗi hàng là đối tượng riêng, nên Java không có một thuộc tính bất biến bắt buộc mọi hàng đều dài như nhau.

**Cần phân biệt:** danh sách {1, 4, 2} đưa vào Array.newInstance(int.class, 1, 4, 2) sẽ tạo **mảng ba chiều**, không phải một mảng hai chiều có ba hàng dài 1, 4, 2.

## 4. Khi componentType đã là mảng: số chiều tăng thêm

Theo Javadoc, nếu componentType **không phải array class**, số chiều kết quả là số phần tử trong dimensions. Nhưng nếu componentType **đã là array class**, số chiều cuối bằng:

**rank của componentType + dimensions.length**

Các số trong dimensions dùng để cấp phát **những tầng mảng mới ở bên ngoài**. Các tầng vốn nằm trong array-typed component **chưa được cấp phát**; phần tử đại diện cho mảng con ở mức cuối được tạo ban đầu là null.

| Lời gọi | Kiểu runtime | Đã cấp phát |
| --- | --- | --- |
| Array.newInstance(String.class, 2) | String[] | 2 ô String, mặc định null |
| Array.newInstance(String.class, 2, 3) | String[][] | Hai hàng, mỗi hàng 3 ô String |
| Array.newInstance(String[].class, 2) | String[][] | Chỉ mảng ngoài dài 2; mỗi String[] ban đầu null |
| Array.newInstance(String[].class, 2, 3) | String[][][] | Hai chiều ngoài 2 × 3; mỗi String[] cuối ban đầu null |
| Array.newInstance(int[][].class, 2, 3) | int[][][][] | Hai tầng ngoài 2 × 3; mỗi int[][] tại tầng kế tiếp ban đầu null |

Hai lời gọi khác nhau có thể cho **cùng runtime type** nhưng cấp phát các tầng khác nhau:

~~~java
String[][] full = (String[][]) Array.newInstance(String.class, 2, 3);
String[][] outerOnly = (String[][]) Array.newInstance(String[].class, 2);

System.out.println(full[0].length); // 3
System.out.println(outerOnly[0]);    // null
~~~

Với outerOnly, truy cập outerOnly[0].length khi chưa gán sẽ ném **NullPointerException**, không phải trả 0. Ngược lại, full đã có các mảng con dài 3.

Có thể hình dung **Array.newInstance(String[].class, 2, 3)** tương đương về hình dạng ban đầu với **new String[2][3][]**. Kiểu có ba chiều nhưng chỉ hai chiều đầu được cấp phát.

### Class.getComponentType chỉ bỏ một tầng

~~~java
String[][][] values = (String[][][]) Array.newInstance(String[].class, 2, 3);
System.out.println(values.getClass().getTypeName()); // java.lang.String[][][]
System.out.println(values.getClass().getComponentType().getTypeName());
                                                   // java.lang.String[][]
~~~

Class.getComponentType() trên String[][][] trả String[][], vì một phần tử của mảng ngoài là mảng hai chiều. Muốn đi đến String.class phải tiếp tục bóc từng tầng:

~~~java
Class<?> leaf = values.getClass();
int rank = 0;
while (leaf.isArray()) {
    rank++;
    leaf = leaf.getComponentType();
}
// rank là 3, leaf là String.class
~~~

Đây là fragment sử dụng biến values vừa tạo ở trên. **Không đồng nhất rank của runtime type với số độ dài truyền vào** nếu componentType ban đầu đã là array class.

## 5. Chương trình Java 17+ hoàn chỉnh

Ví dụ bao quát cấp phát đầy đủ, độ dài 0, mảng răng cưa, componentType là mảng và các lỗi về kích thước.

Lưu nguyên văn vào file **MultiArrayDemo.java**:

~~~java
import java.lang.reflect.Array;

public class MultiArrayDemo {
    public static void main(String[] args) {
        int[][] grid = (int[][]) Array.newInstance(int.class, 2, 3);
        Array.setInt(Array.get(grid, 1), 2, 17);
        System.out.println("grid type=" + grid.getClass().getTypeName());
        System.out.println("grid lengths=" + Array.getLength(grid)
                + "x" + Array.getLength(Array.get(grid, 0)));
        System.out.println("grid default=" + grid[0][0] + ", changed=" + grid[1][2]);

        String[][][] zero = (String[][][]) Array.newInstance(String.class, 2, 0, 4);
        System.out.println("zero lengths=" + zero.length + "," + zero[0].length);

        String[][] ragged = (String[][]) Array.newInstance(String[].class, 2);
        System.out.println("ragged before=" + ragged[0]);
        ragged[0] = new String[] {"A"};
        ragged[1] = new String[] {"B", "C", "D"};
        System.out.println("ragged lengths=" + ragged[0].length + "," + ragged[1].length);

        String[][][] partial =
                (String[][][]) Array.newInstance(String[].class, 2, 3);
        System.out.println("partial type=" + partial.getClass().getTypeName());
        System.out.println("partial lengths=" + partial.length + "," + partial[0].length);
        System.out.println("partial innermost=" + partial[0][0]);
        System.out.println("partial component=" +
                partial.getClass().getComponentType().getTypeName());

        try {
            Array.newInstance(int.class, 2, -1);
        } catch (NegativeArraySizeException ex) {
            System.out.println("negative=" + ex.getClass().getSimpleName());
        }
        try {
            Array.newInstance(void.class, 2);
        } catch (IllegalArgumentException ex) {
            System.out.println("void=" + ex.getClass().getSimpleName());
        }
        try {
            Array.newInstance(int.class, new int[0]);
        } catch (IllegalArgumentException ex) {
            System.out.println("zero ranks=" + ex.getClass().getSimpleName());
        }
        try {
            Array.newInstance(int[][].class, new int[254]);
        } catch (IllegalArgumentException ex) {
            System.out.println("too many ranks=" + ex.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 MultiArrayDemo.java
java MultiArrayDemo
~~~

**Stdout đã kiểm tra thực tế** với javac hỗ trợ --release 17:

~~~text
grid type=int[][]
grid lengths=2x3
grid default=0, changed=17
zero lengths=2,0
ragged before=null
ragged lengths=1,3
partial type=java.lang.String[][][]
partial lengths=2,3
partial innermost=null
partial component=java.lang.String[][]
negative=NegativeArraySizeException
void=IllegalArgumentException
zero ranks=IllegalArgumentException
too many ranks=IllegalArgumentException
~~~

Giải thích:

1. **grid:** int[][] gồm 2 × 3 slot, giá trị int mặc định bằng 0. Array.get(grid, 1) cho mảng int[] thứ hai; Array.setInt(..., 2, 17) gán slot thứ ba của mảng con.
2. **zero:** String[][][] với kích thước 2 × 0 × 4 vẫn hợp lệ. Mảng ngoài có hai slot, nhưng các mảng con thứ hai đều dài 0, không có vị trí [0][0] để tiếp tục xuống chiều cuối.
3. **ragged:** khi truyền String[].class cùng một độ dài 2, ta chỉ nhận mảng ngoài String[][] với phần tử con null. Sau đó gán lần lượt String[] dài 1 và 3.
4. **partial:** truyền String[].class với hai độ dài 2, 3 tạo String[][][]; hai tầng đầu được cấp phát, còn phần tử String[] ở tầng sâu là null. getComponentType() từ runtime class trả String[][], không phải String.
5. **negative:** độ dài bất kỳ âm dẫn đến NegativeArraySizeException.
6. **void / zero ranks / too many ranks:** tương ứng componentType không hợp lệ, không có chiều nào được cung cấp và yêu cầu 256 chiều khi đã có int[][].class thêm 254 chiều; đều ném IllegalArgumentException.

## 6. Tạo mảng răng cưa hoàn toàn bằng Reflection

Khi số hàng biết trước nhưng mỗi hàng có độ dài khác nhau, chỉ tạo chiều ngoài rồi cấp phát từng hàng:

~~~java
int[][] jagged = (int[][]) Array.newInstance(int[].class, 3);

for (int row = 0; row < jagged.length; row++) {
    int size = row + 1;
    Object inner = Array.newInstance(int.class, size);
    Array.set(jagged, row, inner);
}
~~~

Sau vòng lặp, ba hàng dài 1, 2, 3. Array.set nhận một **đối tượng int[]** để gán vào slot của int[][]; nó không nhận trực tiếp số int vì phần tử của mảng ngoài có kiểu int[].

Cú pháp Java tĩnh tương đương:

~~~java
int[][] jagged = new int[3][];
for (int row = 0; row < jagged.length; row++) {
    jagged[row] = new int[row + 1];
}
~~~

Nếu số chiều và kiểu thành phần đều biết lúc biên dịch, cú pháp **new** trực tiếp thường đơn giản và an toàn kiểu hơn. Reflection có lợi khi kiểu hoặc kích thước được xác định từ metadata/cấu hình lúc chạy.

### Tạo từ cấu hình động

Giả sử chương trình nhận danh sách cấu hình {2, 3, 4}:

~~~java
int[] sizes = {2, 3, 4};
Class<?> component = double.class;
Object data = Array.newInstance(component, sizes);
~~~

Runtime type của data là double[][][]. **sizes.length** là 3 chiều, **không** phải ba hàng dài lần lượt 2, 3, 4. Nếu muốn ba hàng răng cưa tương ứng, tạo double[3][] rồi cấp phát ba hàng riêng.

## 7. Đọc và ghi mảng nhiều chiều bằng Reflection

Các API hữu ích trong java.lang.reflect.Array:

| Phương thức | Ý nghĩa |
| --- | --- |
| **Array.getLength(array)** | Độ dài mảng tại **tầng hiện tại** |
| **Array.get(array, index)** | Đọc phần tử; primitive được boxing nếu cần trả Object |
| **Array.set(array, index, value)** | Ghi phần tử tương thích kiểu, kể cả mảng con |
| **Array.getInt(array, index)** | Đọc giá trị int từ mảng primitive tương thích |
| **Array.setInt(array, index, intValue)** | Ghi int vào mảng primitive tương thích |
| **Class.isArray()** | Runtime class là mảng? |
| **Class.getComponentType()** | Bóc đúng một cấp component type |

Khi đi qua mảng nhiều chiều bằng Reflection, mỗi lần Array.get chỉ đi xuống **một tầng**:

~~~java
Object cube = Array.newInstance(String.class, 2, 3, 4);
Object plane = Array.get(cube, 1);  // String[][]
Object row = Array.get(plane, 2);   // String[]
Array.set(row, 3, "ok");
System.out.println(Array.get(row, 3)); // ok
~~~

Đây là fragment chạy trong method sau khi import Array. Không thể gán String trực tiếp vào Array.set(cube, 0, "x"), vì phần tử ở tầng ngoài của String[][][] phải có kiểu String[][].

Lưu ý **int[] không thể cast thành Object[]** dù mảng là một Object trong Java. Tuy nhiên **int[][] có thể xem là Object[] ở tầng ngoài**, vì các phần tử của nó là các đối tượng int[]. Nếu cần code áp dụng cho mọi primitive array, giữ biến kiểu Object và dùng API Array.

## 8. Giới hạn dimensions và ngoại lệ

Theo Javadoc Java SE 17:

| Điều kiện | Kết quả |
| --- | --- |
| dimensions có ít nhất một phần tử, mọi độ dài >= 0 | Hợp lệ nếu đủ tài nguyên |
| Có một độ dài bằng 0 | Hợp lệ: mảng độ dài 0 ở tầng tương ứng |
| Có một độ dài âm | **NegativeArraySizeException** |
| componentType là null | **NullPointerException** |
| dimensions là null | **NullPointerException** |
| dimensions là mảng rỗng (0 phần tử) | **IllegalArgumentException** |
| componentType là void.class | **IllegalArgumentException** |
| Số chiều cuối vượt 255 | **IllegalArgumentException** |
| Yêu cầu cấp phát quá nhiều bộ nhớ | Có thể **OutOfMemoryError** |

Số chiều cuối **không chỉ phụ thuộc vào dimensions.length**. Nếu componentType đã là String[][].class, phải cộng thêm 2 chiều. Ví dụ Array.newInstance(int[][].class, new int[254]) yêu cầu 256 chiều nên bị từ chối.

**Danh sách kích thước rỗng khác kích thước bằng 0**:

~~~java
Array.newInstance(int.class, new int[0]); // IllegalArgumentException
Array.newInstance(int.class, 0);          // int[] dài 0, hợp lệ
Array.newInstance(int.class, 2, 0);       // int[][] có 2 hàng, mỗi hàng dài 0
~~~

Chiều 0 không có phần tử để duyệt xuống sâu hơn. Vì thế Array.getLength của mảng con ở tầng trước vẫn có thể là 0, còn truy cập chỉ số [0] của mảng con đó sẽ ném ArrayIndexOutOfBoundsException.

Các thao tác sau tạo cũng có ngoại lệ riêng:

- Array.getLength(null) ném **NullPointerException**.
- Array.getLength trên object không phải mảng ném **IllegalArgumentException**.
- Array.get/Array.set với index âm hay vượt độ dài ném **ArrayIndexOutOfBoundsException**.
- Array.set với giá trị không tương thích kiểu phần tử có thể ném **IllegalArgumentException**.
- Cast một Object sang kiểu mảng có rank/component type không phù hợp ném **ClassCastException**.
- Với mảng outer-only, đọc chiều sâu từ phần tử null gây **NullPointerException**.

Một công cụ xây mảng từ cấu hình không tin cậy nên kiểm tra số chiều, các độ dài, giới hạn cấp phát và kiểu phần tử để tránh cấp phát sai hoặc quá lớn.

## 9. Công thức kiểm tra shape và rank

Giả sử:

~~~java
Object created = Array.newInstance(componentType, dimensions);
~~~

Các quy tắc:

1. **rank(created)** = **rank(componentType) + dimensions.length**; class không phải mảng có rank 0.
2. Các tầng mới tạo có độ dài lần lượt **dimensions[0], dimensions[1], ...**.
3. Nếu componentType vốn là mảng, những tầng nằm sẵn trong componentType **chưa được cấp phát** bởi lần gọi trên.
4. Runtime type cho biết **có bao nhiêu cấp dấu []**, nhưng không tự bảo đảm mỗi nhánh hiện có đủ tất cả mảng con hay độ dài như nhau sau khi chương trình thay đổi.
5. Muốn lấy kiểu lá, gọi getComponentType lặp lại đến khi Class.isArray() trả false.

| Gọi | Rank | Trạng thái phần tử ở mức sâu nhất đã cấp phát |
| --- | --- | --- |
| newInstance(int.class, 2, 3) | 2 | int mặc định 0 |
| newInstance(int[].class, 2) | 2 | Hai int[] null |
| newInstance(int[].class, 2, 3) | 3 | Sáu int[] null |
| newInstance(String.class, 2, 3) | 2 | String mặc định null |
| newInstance(String.class, 0, 3) | 2 | Không có hàng để đọc |

Không nên ước lượng dung lượng bằng cách nhân tất cả dimensions khi componentType vốn là array class: lúc đó phần các tầng chưa cấp phát không có slot phần tử lá tương ứng.

## 10. Sai lầm thường gặp

1. **Đọc {2,3} là hai hàng có độ dài 2 và 3:** đây là 2 chiều kích thước 2 × 3; mảng răng cưa cần cấp phát từng hàng.
2. **Bỏ qua rank có sẵn trong componentType:** String[].class cộng {2,3} tạo String[][][], không phải String[][].
3. **Cho rằng tất cả các chiều trong runtime type đều được cấp phát:** với array-typed component, các chiều còn lại có phần tử mảng con null.
4. **Nhầm null với mảng độ dài 0:** null là chưa có đối tượng mảng; chiều 0 là một mảng hợp lệ có length == 0.
5. **Gọi Array.getLength một lần để lấy mọi chiều:** phương thức chỉ trả length của object mảng cụ thể.
6. **Dùng Object[] cho int[]:** kiểu mảng primitive một chiều không phải Object[].
7. **Giả định Array.newInstance tự cấp phát constructor của mọi phần tử tham chiếu:** String hay object references ban đầu là null, không tự tạo instance.
8. **Truyền dimensions âm/rỗng:** bị từ chối; kiểm tra trước khi cấp phát.
9. **Quên giới hạn 255 chiều:** tính cả rank sẵn của componentType.
10. **Tạo mảng khổng lồ từ cấu hình mà không giới hạn dung lượng:** ngay cả kích thước hợp lệ vẫn có thể gây OutOfMemoryError.

## 11. Tự kiểm tra

**Câu 1.** Array.newInstance(double.class, 4, 2) có kiểu gì và bao nhiêu phần tử cuối?

**Đáp án:** double[][], 4 hàng × 2 phần tử, tổng 8 số double mặc định 0.0.

**Câu 2.** Array.newInstance(double[].class, 4) có kiểu giống câu 1 không? Các hàng đã được cấp phát chưa?

**Đáp án:** Cùng kiểu double[][], nhưng chỉ có mảng ngoài dài 4; cả bốn double[] còn null.

**Câu 3.** Array.newInstance(String[][].class, 2, 3) có runtime type gì?

**Đáp án:** String[][][][] với tổng rank 4; hai tầng mới dài 2 và 3, các tham chiếu String[][] ở tầng cuối được tạo còn null.

**Câu 4.** {3,0,5} có hợp lệ không? Có thể truy cập [0][0] không?

**Đáp án:** Hợp lệ. Mảng ngoài dài 3, mảng con thứ hai dài 0. Truy cập [0][0] gây ArrayIndexOutOfBoundsException.

**Câu 5.** Muốn ba hàng dài 2,5,1 thì có nên gọi newInstance(int.class, 2,5,1)?

**Đáp án:** Không. Đó là mảng ba chiều 2 × 5 × 1. Hãy tạo int[3][] hoặc Array.newInstance(int[].class, 3) rồi tạo các hàng dài 2,5,1.

**Câu 6.** Vì sao newInstance(int.class, new int[0]) lỗi nhưng newInstance(int.class, 0) thành công?

**Đáp án:** Mảng kích thước thứ nhất rỗng, nghĩa là **không cung cấp chiều nào**. Lời gọi thứ hai chỉ tạo **một chiều có độ dài 0**.

**Ghi nhớ:** danh sách dimensions cho biết **độ dài các chiều mới, theo thứ tự ngoài vào trong**. Nếu componentType là array class thì số chiều kết quả tăng thêm nhưng các chiều sẵn có bên trong componentType vẫn chưa được cấp phát. Java dùng mảng của mảng nên bạn có thể tạo hình chữ nhật hoặc mảng răng cưa có độ dài từng hàng khác nhau.

### Tài liệu tham khảo

- Java SE 17, Array.newInstance/get/set/getLength: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Array.html
- Java SE 17, Class.isArray và getComponentType: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java Language Specification 17, Chapter 10 — Arrays: https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-arrays-003">Array.getLength và kiểm tra isArray</a>

<details>
<summary>Xem chi tiết</summary>

# Array.getLength và kiểm tra isArray trong Java Reflection

## 1. Vì sao cần kiểm tra mảng khi dữ liệu chỉ có kiểu Object?

Trong Java thông thường, nếu biến có kiểu mảng tại thời điểm biên dịch, ta lấy độ dài bằng thuộc tính `length`:

```java
int[] numbers = {4, 5, 6};
int count = numbers.length; // 3
```

Tuy nhiên, khi dữ liệu được nhận qua `Object`, từ một `Field.get(...)`, `Method.invoke(...)`, thư viện ánh xạ dữ liệu hoặc khi tạo mảng động, mã nguồn không biết chắc đối tượng có phải mảng hay không. Reflection cung cấp hai thao tác quan trọng:

- `value.getClass().isArray()` xác định **lớp runtime của đối tượng có phải kiểu mảng**. Cần kiểm tra `value != null` trước khi gọi `getClass()`.
- `java.lang.reflect.Array.getLength(value)` đọc **số phần tử của đối tượng mảng**, bất kể đó là mảng primitive (`int[]`, `double[]`) hay mảng tham chiếu (`String[]`, `Object[]`).

Ví dụ:

```java
Object value = new int[] {4, 5, 6};

if (value != null && value.getClass().isArray()) {
    int length = java.lang.reflect.Array.getLength(value);
    System.out.println(length); // 3
}
```

Đoạn code trên là **mảnh mã đặt trong phương thức**. Nếu sử dụng `((Object[]) value).length` thay vì `Array.getLength(value)`, mảng `int[]` sẽ gây `ClassCastException`, bởi `int[]` không phải `Object[]`.

## 2. Hai API phục vụ hai câu hỏi khác nhau

| API / biểu thức | Hỏi điều gì? | Giá trị trả về | Điều kiện |
| --- | --- | --- | --- |
| `someArray.length` | Mảng này có bao nhiêu phần tử? | `int` | Trình biên dịch đã biết biến là kiểu mảng |
| `type.isArray()` | Đối tượng `Class` này biểu diễn kiểu mảng không? | `boolean` | Đã có `Class<?>` |
| `obj.getClass().isArray()` | Kiểu thực của đối tượng có phải mảng không? | `boolean` | `obj != null` |
| `Array.getLength(obj)` | Đối tượng mảng này có bao nhiêu phần tử? | `int` | `obj` phải là **một đối tượng mảng**, khác `null` |
| `type.getComponentType()` | Thành phần của kiểu mảng là loại gì? | `Class<?>` hoặc `null` | `type.isArray() == true` để có component |
| `Array.get(array, index)` | Phần tử tại chỉ số này là gì? | `Object` | Đối tượng mảng hợp lệ; chỉ số hợp lệ |

`Class.isArray()` kiểm tra **kiểu**, không kiểm tra độ dài. `Array.getLength()` làm việc với **đối tượng mảng thật**, không nhận `Class` làm đại diện cho mảng:

```java
int[].class.isArray();                  // true
java.lang.reflect.Array.getLength(
        new int[] {1, 2});             // 2

// Sai: int[].class là đối tượng Class, không phải một mảng.
// Array.getLength(int[].class);        // IllegalArgumentException
```

Đừng nhầm `Array.getLength()` với `java.lang.reflect.Array.newInstance(...)`: cái đầu **đo kích thước mảng hiện có**; cái sau **tạo một đối tượng mảng mới**.

## 3. isArray áp dụng cho cả mảng primitive và mảng tham chiếu

```java
int[].class.isArray();          // true
Integer[].class.isArray();      // true
String[].class.isArray();       // true
int[][].class.isArray();        // true
String.class.isArray();         // false
int.class.isArray();            // false
Object.class.isArray();         // false
```

**Các kiểu mảng trong Java đều là kiểu tham chiếu**, kể cả `int[]`. Vì thế:

- `int[].class.isArray()` là `true`.
- `int[].class.isPrimitive()` là `false`.
- `int[].class.getComponentType().isPrimitive()` là `true`, vì component của `int[]` là `int`.
- `Integer[].class.getComponentType().isPrimitive()` là `false`.
- `int[]` có thể gán vào biến `Object` và tuân thủ các interface mảng như `Cloneable`, `Serializable`, nhưng **không thể gán cho `Object[]`**.
- `String[]` là mảng tham chiếu, nên có thể gán vào `Object[]`.

`Class.getComponentType()` giúp phát hiện kiểu phần tử. Với mảng nhiều chiều `int[][]`, lần gọi đầu tiên trả `int[].class`, tiếp theo trả `int.class`. `getComponentType()` trên một kiểu không phải mảng trả `null`.

Mảng primitive và wrapper như `int[]` và `Integer[]` đều có `isArray() == true`, nhưng vẫn là **hai kiểu runtime khác nhau**. Boxing/unboxing phần tử `int` không tự động chuyển nguyên mảng `int[]` thành `Integer[]`.

## 4. Array.getLength nhận gì và gây ngoại lệ nào?

Chữ ký của API:

```java
public static int getLength(Object array)
```

`Array.getLength` không yêu cầu mảng phần tử đối tượng (`Object[]`); đối số `Object` có thể giữ bất cứ mảng nào, bao gồm `boolean[]`, `char[]`, `double[]`, `String[]` hoặc mảng nhiều chiều.

| Truyền vào | Kết quả |
| --- | --- |
| `new int[] {1, 2, 3}` | `3` |
| `new String[0]` | `0` |
| `new int[2][3]` | `2` (chiều ngoài) |
| `"hello"` | `IllegalArgumentException` |
| `Integer.valueOf(3)` | `IllegalArgumentException` |
| `new java.util.ArrayList<>()` | `IllegalArgumentException`; `List` không phải Java array |
| `int[].class` | `IllegalArgumentException`; đây là đối tượng `Class` |
| `null` | `NullPointerException` |

Đây là **ngoại lệ khi gọi trực tiếp `Array.getLength`**: không phải `NoSuchMethodException`, vì không hề tra cứu một phương thức theo tên. Để tránh lỗi trên dữ liệu không chắc chắn, kiểm tra:

```java
// Mảnh mã minh họa trong phương thức đã có biến Object value.
if (value == null) {
    System.out.println("No object");
} else if (!value.getClass().isArray()) {
    System.out.println("Not an array");
} else {
    System.out.println(Array.getLength(value));
}
```

Cần `import java.lang.reflect.Array;` nếu không dùng tên lớp đầy đủ.

**Không dùng `instanceof Object[]` để nhận diện tất cả mảng**: `new int[] {1, 2}` trả `false` cho phép thử này dù thật sự là mảng; `getClass().isArray()` nhận diện được.

## 5. Mảng hai chiều: getLength không tự đo tất cả chiều

Ví dụ:

```java
int[][] ragged = {
    {1, 2, 3},
    {4},
    new int[0],
    null
};
```

- `Array.getLength(ragged)` trả **4** vì mảng ngoài có bốn phần tử, mỗi phần tử là một tham chiếu tới `int[]` (có thể `null`).
- `Array.getLength(ragged[0])` trả **3**.
- `Array.getLength(ragged[1])` trả **1**.
- `Array.getLength(ragged[2])` trả **0**.
- `Array.getLength(ragged[3])` ném `NullPointerException`, vì hàng đó là `null`.

Đây là **mảng jagged/ragged**, mỗi hàng có độ dài độc lập. Không thể suy ra mọi hàng dài 3 chỉ từ `ragged[0].length`, cũng không nên nhân `ragged.length * ragged[0].length` như thể đó luôn là số phần tử của một ma trận hình chữ nhật.

Dùng `Array.get` để duyệt từng phần tử mảng ngoài khi loại thành phần chỉ được biết ở runtime:

```java
// Mảnh mã minh họa trong phương thức; object phải là một đối tượng mảng.
int outerLength = Array.getLength(object);

for (int i = 0; i < outerLength; i++) {
    Object element = Array.get(object, i);
    if (element != null && element.getClass().isArray()) {
        System.out.println("row " + i + ": "
                + Array.getLength(element));
    }
}
```

Ở đây `Array.get` trả `Object`; nếu phần tử là mảng con, ta có thể dùng tiếp `getClass().isArray()` và `Array.getLength`. Nếu phần tử là một primitive value, `Array.get` sẽ boxing giá trị đó thành wrapper trước khi trả về `Object`; wrapper không phải mảng.

### Khi dùng Array.newInstance tạo mảng nhiều chiều

```java
Object matrix = Array.newInstance(int.class, 2, 3);
```

Đối tượng có runtime type `int[][]`, chiều ngoài dài 2 và mỗi hàng là `int[3]`. Nếu chỉ muốn một mảng ngoài gồm các hàng tùy biến, có thể tạo `int[][]` thông thường và gán từng hàng riêng. `Array.getLength(matrix)` vẫn chỉ đọc kích thước chiều ngoài, không có cơ chế tự trả tổng số ô của mọi chiều.

## 6. Chương trình Java 17+ đầy đủ và kiểm chứng được

Lưu mã sau thành tệp **`ArrayLengthLesson.java`**:

```java
import java.lang.reflect.Array;

public class ArrayLengthLesson {
    static void printInfo(String label, Object value) {
        if (value == null) {
            System.out.println(label + ": null");
            return;
        }

        Class<?> type = value.getClass();
        System.out.println(label + ": isArray=" + type.isArray()
                + ", type=" + type.getTypeName()
                + (type.isArray()
                        ? ", length=" + Array.getLength(value)
                        : ""));
    }

    public static void main(String[] args) {
        int[] ints = {4, 5, 6};
        String[] words = {"a", "b"};
        int[][] ragged = {{1, 2, 3}, {4}, new int[0], null};
        int[][] rectangle =
                (int[][]) Array.newInstance(int.class, 2, 3);

        printInfo("ints", ints);
        printInfo("words", words);
        printInfo("ragged", ragged);
        printInfo("row0", ragged[0]);
        printInfo("row1", ragged[1]);
        printInfo("row2", ragged[2]);
        printInfo("row3", ragged[3]);

        printInfo("rectangle", rectangle);
        System.out.println("rectangle row0="
                + Array.getLength(rectangle[0]));

        System.out.println("int[] class.isArray="
                + int[].class.isArray());
        System.out.println("int.class.isArray="
                + int.class.isArray());
        System.out.println("int[] component="
                + int[].class.getComponentType().getTypeName());
        System.out.println("int[] component primitive="
                + int[].class.getComponentType().isPrimitive());
        System.out.println("int[] instanceof Object[]="
                + Object[].class.isInstance(ints));
        System.out.println("int[] instanceof Object="
                + (ints instanceof Object));

        try {
            Array.getLength("abc");
        } catch (IllegalArgumentException e) {
            System.out.println("not array: IllegalArgumentException");
        }
        try {
            Array.getLength(null);
        } catch (NullPointerException e) {
            System.out.println("null: NullPointerException");
        }

        Object[] nested = {ints, words};
        System.out.println("nested length=" + Array.getLength(nested));
        System.out.println("nested first length="
                + Array.getLength(Array.get(nested, 0)));
    }
}
```

Biên dịch/chạy từ thư mục chứa tệp bằng JDK 17+:

```bash
javac ArrayLengthLesson.java
java ArrayLengthLesson
```

Stdout:

```text
ints: isArray=true, type=int[], length=3
words: isArray=true, type=java.lang.String[], length=2
ragged: isArray=true, type=int[][], length=4
row0: isArray=true, type=int[], length=3
row1: isArray=true, type=int[], length=1
row2: isArray=true, type=int[], length=0
row3: null
rectangle: isArray=true, type=int[][], length=2
rectangle row0=3
int[] class.isArray=true
int.class.isArray=false
int[] component=int
int[] component primitive=true
int[] instanceof Object[]=false
int[] instanceof Object=true
not array: IllegalArgumentException
null: NullPointerException
nested length=2
nested first length=3
```

**Phân tích kết quả:**

1. `printInfo` xử lý `null` trước, tránh `NullPointerException` từ `getClass()`. Đối tượng không-null được phân loại bằng runtime class.
2. `int[]` và `String[]` đều được `isArray()` xác nhận; `Array.getLength` đọc đúng 3 và 2.
3. `ragged` có 4 phần tử ở chiều ngoài; từng hàng có kích thước 3, 1, 0, hoặc `null`.
4. `rectangle` được tạo động với chiều dài `2 × 3`; tuy nhiên `getLength(rectangle)` chỉ trả 2.
5. `int[].class.isArray()` là `true`, còn `int.class.isArray()` là `false`; kiểu `int` là primitive nhưng `int[]` là array object.
6. `Object[].class.isInstance(ints)` là `false`; `ints instanceof Object` là `true`. Không nên dùng phép cast `Object[]` cho mảng primitive.
7. `Array.getLength("abc")` ném `IllegalArgumentException`; `Array.getLength(null)` ném `NullPointerException`.
8. `nested` là `Object[]` chứa hai đối tượng mảng ở cấp phần tử. Mảng ngoài dài 2, mảng đầu (một `int[]`) dài 3.

Chương trình chủ động **không dùng một thứ tự duyệt Reflection không được đảm bảo**; stdout chỉ phụ thuộc giá trị mảng cố định và kiểu runtime.

## 7. Khi Class đã có từ Reflection: kiểm tra trước khi lấy độ dài

Ví dụ dữ liệu được đọc từ một field:

```java
// Mảnh code đặt trong phương thức có Class<?> type và Object receiver.
java.lang.reflect.Field field = type.getDeclaredField("data");
Object value = field.get(receiver); // Cần quyền truy cập hợp lệ.

if (value != null && value.getClass().isArray()) {
    int count = Array.getLength(value);
    // Xử lý số phần tử.
}
```

`field.getType().isArray()` cũng có ích nếu muốn hỏi **kiểu khai báo của field**, trước khi đọc giá trị. Tuy nhiên cần phân biệt:

- **Kiểu khai báo:** `Object data;` → `field.getType().isArray()` là `false`, ngay cả khi hiện đang giữ `new int[3]`.
- **Kiểu runtime của giá trị:** `value.getClass().isArray()` là `true` nếu `value` thực sự là `int[]`.
- **Giá trị `null`:** kiểu field có thể là `int[]` nhưng `field.get(...)` trả `null`; không có đối tượng mảng để đo độ dài.

Lựa chọn tùy mục đích: kiểm tra **schema** thì dùng kiểu khai báo; xử lý **dữ liệu đang nhận** thì dùng lớp runtime sau khi kiểm tra null.

Điều tương tự áp dụng cho `Method.getReturnType().isArray()` (kiểu trả về khai báo) và giá trị thực tế sau `Method.invoke`; những method khai báo trả `Object` vẫn có thể trả mảng.

## 8. Thiết kế hàm lấy độ dài an toàn

Đối với ứng dụng chỉ muốn xử lý mảng, có thể viết hàm dùng chung:

```java
// Đặt trong một lớp Java, dùng import java.lang.reflect.Array.
static int requireArrayLength(Object value) {
    if (value == null) {
        throw new IllegalArgumentException("Array value must not be null");
    }
    if (!value.getClass().isArray()) {
        throw new IllegalArgumentException(
                "Expected array but got " + value.getClass().getTypeName());
    }
    return Array.getLength(value);
}
```

Đây là **hàm minh họa**, không phải chương trình độc lập. Nó chủ ý gộp trường hợp `null` và không-phải-mảng thành `IllegalArgumentException` theo hợp đồng ứng dụng, **khác** với ngoại lệ gốc `NullPointerException` của `Array.getLength(null)`. Không khẳng định rằng bản thân API Reflection tự sinh thông báo lỗi trên.

Khi đối tượng không phải mảng là một tình huống bình thường (chẳng hạn input đa kiểu), có thể trả một `OptionalInt` hoặc một kết quả có trạng thái thay vì bắt/nuốt ngoại lệ. Đừng quy ước `0` là “không phải mảng”, vì **mảng rỗng hợp lệ cũng có độ dài 0**.

## 9. Lưu ý về Array.get, Array.set và mảng động

Ngoài `getLength`, lớp `java.lang.reflect.Array` còn cung cấp:

- `Array.get(array, index)`: đọc một phần tử tại runtime, kết quả kiểu `Object`; nếu phần tử primitive, API boxing thành wrapper.
- `Array.set(array, index, value)`: ghi phần tử khi kiểu/chuyển đổi hợp lệ; không có nghĩa mọi wrapper có thể gán cho mọi mảng primitive.
- `Array.newInstance(componentType, length)`: tạo mảng động dựa trên `Class` của thành phần.
- `Array.newInstance(componentType, dimensions...)`: tạo mảng nhiều chiều với các kích thước được chỉ định.

Nếu `Array.getLength(array) == n`, các chỉ số hợp lệ cho mảng này là `0` đến `n - 1`. Truy cập `n` hoặc chỉ số âm sẽ ném `ArrayIndexOutOfBoundsException`. Đọc độ dài không tự cấp quyền vượt giới hạn chỉ số.

**Không nhầm loại API:**

- `java.lang.reflect.Array` dành cho các mảng Java, bao gồm primitive arrays.
- `java.util.Arrays` cung cấp nhiều hàm tiện ích như `toString`, `deepToString`, `copyOf`, `stream`.
- `java.util.List.size()` là kích thước của một collection, không phải `Array.getLength(list)`.
- `String.length()` là độ dài chuỗi theo đơn vị UTF-16 code unit, không phải `Array.getLength(string)`.

## 10. Những lỗi phổ biến

| Sai lầm | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| `Array.getLength(null)` | `NullPointerException` | Kiểm tra `value != null` |
| `Array.getLength("abc")` | `IllegalArgumentException` | Xác minh `value.getClass().isArray()` |
| `value.getClass().isArray()` khi `value == null` | `NullPointerException` trước cả kiểm tra `isArray` | Kiểm tra `null` trước |
| `value instanceof Object[]` để kiểm mọi mảng | Bỏ sót `int[]`, `boolean[]`, ... | Dùng `getClass().isArray()` |
| `((Object[]) intArray).length` | `ClassCastException` | Dùng `Array.getLength(intArray)` |
| Cho rằng `Array.getLength(matrix)` trả tổng phần tử 2D | Chỉ trả độ dài **chiều ngoài** | Duyệt từng hàng, kiểm tra `null` |
| Dùng `getComponentType()` trên class không phải mảng mà không kiểm tra | Trả `null`; dễ gây NPE khi dùng tiếp | Kiểm tra `Class.isArray()` |
| Dùng `Array.getLength(int[].class)` | `IllegalArgumentException`; đối số là `Class`, không phải array instance | Truyền một đối tượng `int[]` |
| Đánh đồng `field.getType().isArray()` với kiểu runtime của giá trị | Field kiểu `Object` có thể giữ mảng và field kiểu mảng có thể đang `null` | Phân biệt kiểu khai báo và kiểu runtime |
| Coi `List` hoặc `String` là mảng | `Array.getLength` không dùng cho hai loại này | `list.size()` hoặc `string.length()` |
| Trả `0` khi đầu vào không phải mảng | Không phân biệt được với mảng rỗng | Dùng trạng thái tường minh hoặc ngoại lệ |

## 11. Câu hỏi tự kiểm tra

1. **`new int[0]` có `isArray() == true` không?** Có. `Array.getLength` trả `0`; rỗng không có nghĩa “không phải mảng”.
2. **`int[].class.isArray()` và `int.class.isArray()` khác nhau thế nào?** Kết quả lần lượt là `true` và `false`.
3. **`Array.getLength(new int[2][3])` trả bao nhiêu?** `2`, chiều ngoài. Mỗi hàng có độ dài `3`.
4. **Một `Object data = new int[]{1,2}` có thể dùng `((Object[]) data).length` không?** Không; cast gây `ClassCastException`. Dùng `Array.getLength(data)`.
5. **Nếu `Object data = "hi"`, `data.getClass().isArray()` trả gì?** `false`; gọi `Array.getLength(data)` sẽ ném `IllegalArgumentException`.
6. **Làm sao xem mảng có kiểu phần tử primitive?** Trước hết xác nhận `type.isArray()`, sau đó dùng `type.getComponentType().isPrimitive()`. Với mảng nhiều chiều `int[][]`, component trực tiếp là `int[]` (không primitive); phải đi sâu thêm để đến `int`.
7. **`Array.getLength(null)` và `Array.getLength(new Object[0])` khác nhau thế nào?** Cái đầu ném `NullPointerException`, cái sau trả `0`.

**Ghi nhớ:** `Class.isArray()` là câu trả lời cho **“đây có phải một kiểu mảng?”**; `Array.getLength(value)` là câu trả lời cho **“đối tượng mảng này có bao nhiêu phần tử ở chiều hiện tại?”**. Trước khi đo độ dài dữ liệu động, kiểm tra `null` và kiểu runtime, nhất là với mảng primitive và mảng nhiều chiều.

Tài liệu chính thức: [`java.lang.reflect.Array` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Array.html), [`java.lang.Class.isArray` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#isArray()), [JLS §10.7 Array Members](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html#jls-10.7).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-arrays-004">Array.get, Array.set và các biến thể primitive</a>

<details>
<summary>Xem chi tiết</summary>

# Array.get, Array.set và các biến thể primitive trong Java Reflection

`java.lang.reflect.Array` là lớp tiện ích `final` cung cấp các **phương thức static** để tạo, đọc và ghi mảng Java khi kiểu thành phần chỉ được biết ở runtime. Hai thao tác trung tâm là `Array.get(array, index)` và `Array.set(array, index, value)`. Với mảng primitive, lớp còn cung cấp các getter/setter chuyên biệt như `getInt`, `getLong`, `getDouble`, `setInt`, `setLong`, `setDouble`.

Khác với thao tác chỉ số thông thường `ints[i]`, Reflection cho phép một biến kiểu `Object` đại diện cho `int[]`, `String[]` hay một kiểu mảng khác rồi truy cập phần tử động. Tuy nhiên, **không phải mọi phép chuyển đổi kiểu đều được phép**: API chỉ cho phép chuyển đổi đồng nhất (*identity*) hoặc mở rộng primitive (*widening*) khi đọc/ghi giá trị primitive; phép chuyển đổi thu hẹp (*narrowing*) sẽ bị từ chối.

Ví dụ:

```java
int[] scores = {10, 20};
Object array = scores;

Object boxed = Array.get(array, 0);  // Integer(10)
int exact = Array.getInt(array, 1);   // 20
long widened = Array.getLong(array, 1); // 20L

Array.set(array, 0, Byte.valueOf((byte) 7)); // byte -> int: hợp lệ
Array.setInt(array, 1, 99);                  // int -> int: hợp lệ
```

Đây là đoạn mã minh họa, cần `import java.lang.reflect.Array;`. Hai biến `boxed` và `exact` thể hiện sự khác nhau giữa **getter tổng quát trả `Object`** và **getter primitive trả kiểu nguyên thủy**. Chương trình độc lập có kết quả chính xác được trình bày ở mục 6.

## 1. “Mảng động” ở đây có nghĩa gì?

Mảng Java vẫn có **độ dài cố định sau khi được tạo**. “Mảng động” trong bối cảnh Reflection thường có nghĩa là **tạo mảng với kiểu phần tử hoặc số chiều xác định ở runtime**, không phải một cấu trúc có thể tự tăng dung lượng giống `ArrayList`.

Ví dụ nếu chương trình nhận `componentType` từ cấu hình:

```java
Class<?> componentType = int.class;
Object array = Array.newInstance(componentType, 3);
int length = Array.getLength(array); // 3
```

`Array.newInstance(int.class, 3)` tạo một đối tượng runtime thực sự là `int[]`, nhưng kiểu trả về tĩnh của phương thức là `Object`. `Array.getLength(array)` trả kích thước chiều ngoài của mảng được truyền vào. Để truy cập dữ liệu, có thể dùng `Array.get`/`Array.set` mà không cần cast thành `int[]`.

Khi đã biết tĩnh kiểu thành phần, cách thường dùng là `new int[3]` và `array[i]`: đơn giản, dễ đọc và thường phù hợp hơn. Reflection hữu ích cho mapper, serializer, factory, dependency injection, framework hoặc công cụ xử lý nhiều kiểu mảng chưa biết trước.

**Đối tượng truyền cho `Array.get/set` phải thật sự là mảng Java**, ví dụ `int[]`, `String[]`, `Object[]`, `int[][]`; một `List<Integer>` hay `ArrayList` không phải mảng và không thể dùng trực tiếp với các hàm này.

## 2. Array.get(array, index): đọc dưới dạng Object

Chữ ký:

```java
public static Object get(Object array, int index)
```

`Array.get` lấy phần tử tại vị trí `index`, không cần biết trước kiểu mảng ở compile time.

**Nếu mảng có component type primitive**, giá trị được **boxing** thành wrapper tương ứng:

| Kiểu phần tử mảng | Kiểu đối tượng nhận được khi `Array.get` |
| --- | --- |
| `boolean` | `Boolean` |
| `byte` | `Byte` |
| `short` | `Short` |
| `char` | `Character` |
| `int` | `Integer` |
| `long` | `Long` |
| `float` | `Float` |
| `double` | `Double` |

Nếu mảng có **component type tham chiếu**, `get` trả chính giá trị tham chiếu của phần tử (có thể là `null`); không chuyển đổi thành một kiểu primitive.

Ví dụ:

```java
Object a = new int[] {4};
Object v = Array.get(a, 0);
System.out.println(v.getClass().getSimpleName()); // Integer
```

Đoạn minh họa cần import `Array`, cho thấy `get` không giữ kiểu trả về `int` trên chữ ký Java: nó trả `Object` trỏ tới `Integer`.

Vì `Array.get` không nhận thêm một kiểu đích, **nó không tự chuyển `int` thành `long` rồi boxing thành `Long`**. Mảng `int[]` sẽ trả `Integer` khi dùng `get`. Muốn đọc trực tiếp dạng `long`, dùng `Array.getLong` (nếu kiểu phần tử mảng tương thích).

## 3. Các primitive getter và quy tắc widening khi đọc

Các getter chuyên biệt gồm `getBoolean`, `getByte`, `getShort`, `getChar`, `getInt`, `getLong`, `getFloat`, `getDouble`. Ví dụ:

```java
int x = Array.getInt(array, index);
double y = Array.getDouble(array, index);
```

Đối tượng `array` phải là **mảng primitive có kiểu thành phần tương thích với kiểu trả về của getter**. Quy tắc kiểm tra là:

> **Kiểu nguồn = component type của mảng. Kiểu đích = kiểu trả về của primitive getter.** Được phép nếu cùng kiểu hoặc có widening primitive conversion từ nguồn sang đích.

Bảng đầy đủ:

| Getter | Component type được đọc |
| --- | --- |
| `getBoolean` | `boolean` |
| `getByte` | `byte` |
| `getShort` | `byte`, `short` |
| `getChar` | `char` |
| `getInt` | `byte`, `short`, `char`, `int` |
| `getLong` | `byte`, `short`, `char`, `int`, `long` |
| `getFloat` | `byte`, `short`, `char`, `int`, `long`, `float` |
| `getDouble` | `byte`, `short`, `char`, `int`, `long`, `float`, `double` |

Các ví dụ hợp lệ:

- `Array.getInt(new byte[]{3}, 0)` trả `3` kiểu `int`.
- `Array.getLong(new int[]{7}, 0)` trả `7L` kiểu `long`.
- `Array.getDouble(new float[]{1.25f}, 0)` trả `1.25` kiểu `double`.
- `Array.getInt(new char[]{'A'}, 0)` trả `65`, là giá trị số UTF-16 code unit của `'A'`.

Các ví dụ bị từ chối bằng `IllegalArgumentException`:

- `Array.getInt(new long[]{7L}, 0)`: `long -> int` là narrowing.
- `Array.getInt(new double[]{7.0}, 0)`: `double -> int` là narrowing, **dù giá trị hiện tại bằng số nguyên**.
- `Array.getLong(new float[]{7.0f}, 0)`: `float -> long` không phải widening.
- `Array.getInt(new boolean[]{true}, 0)`: không có chuyển đổi boolean sang số.
- `Array.getInt(new Integer[]{7}, 0)`: **mảng wrapper `Integer[]` không phải `int[]`**; primitive getter không thực hiện unboxing trên reference array.

Mảng `char[]` không thể đọc bằng `getShort` vì `char -> short` không phải widening, mặc dù cả hai đều có 16 bit: `char` là unsigned 16-bit code unit, còn `short` là signed 16-bit integer.

### Widening có thể làm tròn số

`long -> double` và `int -> float` **hợp lệ** về quy tắc widening nhưng có thể **mất độ chính xác**. Ví dụ `Array.getDouble(new long[]{9_007_199_254_740_993L}, 0)` cho một số `double` đã làm tròn vì `2^53+1` không biểu diễn chính xác trong `double`.

Điều này không ném `IllegalArgumentException`, vì Java phân biệt **tính hợp lệ của conversion** với **việc mọi giá trị có được giữ chính xác hay không**. Không nên dùng `getDouble` với số nguyên lớn nếu yêu cầu giữ nguyên từng chữ số.

## 4. Array.set(array, index, value): ghi qua Object

Chữ ký:

```java
public static void set(Object array, int index, Object value)
```

`Array.set` quyết định tương thích theo **component type thực của mảng đích**:

**Với mảng reference** (`String[]`, `Number[]`, `Object[]`...), giá trị phải là `null` hoặc một reference **gán được cho component type**. Ví dụ:

```java
String[] strings = new String[2];
Array.set(strings, 0, "Java");
Array.set(strings, 1, null);
```

Ghi `Integer` vào `String[]` không hợp lệ. Trong môi trường Reflection, `Array.set` ném `IllegalArgumentException` nếu không thể chuyển giá trị cho component type.

**Với mảng primitive**, tham số `value` là `Object`, nên thường là wrapper. `Array.set` có thể **unbox wrapper** rồi widening primitive nếu cần, nhưng không narrowing:

```java
int[] integers = new int[2];
Array.set(integers, 0, Byte.valueOf((byte) 7)); // byte -> int: được
Array.set(integers, 1, Integer.valueOf(9));     // int -> int: được
```

Ngược lại:

- `Array.set(integers, 0, Long.valueOf(1))` thất bại vì `long -> int` là narrowing.
- `Array.set(integers, 0, Double.valueOf(1.0))` cũng thất bại.
- `Array.set(integers, 0, null)` thất bại: không thể unbox `null` thành giá trị primitive.
- `Array.set(new boolean[1], 0, Integer.valueOf(1))` thất bại: boolean không chuyển sang số.

Ghi vào mảng có kiểu `Number[]` với giá trị `Integer` là hợp lệ vì `Integer` là `Number`; nhưng điều này **không** có nghĩa `Array.set` biến một `Number` bất kỳ thành `int` theo yêu cầu tùy ý.

### Phân biệt hai loại chuyển đổi

- **Reference array:** kiểm tra reference assignability (hoặc `null`).
- **Primitive array:** kiểm tra wrapper có thể unbox thành primitive và chuyển tiếp bằng identity/widening hay không.

Trong Java Reflection, “thấy giá trị mang dạng số” chưa đủ. Chẳng hạn `Long.valueOf(3)` không trở thành đối số hợp lệ cho `int[]` chỉ vì số 3 nằm trong miền `int`.

## 5. Các primitive setter: hướng widening đảo chiều so với getter

Primitive setter gồm `setBoolean`, `setByte`, `setShort`, `setChar`, `setInt`, `setLong`, `setFloat`, `setDouble`.

Chữ ký ví dụ:

```java
Array.setInt(array, index, 10);
Array.setFloat(array, index, 1.25f);
```

Khác với getter, khi **ghi**:

> **Kiểu nguồn = tham số primitive của setter. Kiểu đích = component type của mảng.** Được phép nếu nguồn bằng đích hoặc widening primitive conversion từ nguồn sang đích.

Bảng đầy đủ:

| Setter | Component type mảng có thể ghi |
| --- | --- |
| `setBoolean` | `boolean` |
| `setByte` | `byte`, `short`, `int`, `long`, `float`, `double` |
| `setShort` | `short`, `int`, `long`, `float`, `double` |
| `setChar` | `char`, `int`, `long`, `float`, `double` |
| `setInt` | `int`, `long`, `float`, `double` |
| `setLong` | `long`, `float`, `double` |
| `setFloat` | `float`, `double` |
| `setDouble` | `double` |

**Ví dụ đối xứng để nhớ:**

- `Array.getLong(new int[1], 0)` hợp lệ: **đọc int dưới dạng long**.
- `Array.setInt(new long[1], 0, 7)` hợp lệ: **ghi int vào long[]**.
- `Array.getInt(new long[1], 0)` không hợp lệ: đọc long thành int là narrowing.
- `Array.setLong(new int[1], 0, 7L)` không hợp lệ: ghi long vào int[] là narrowing.

Tương tự, `setFloat` có thể ghi vào `double[]`, nhưng không ghi vào `long[]`; `getDouble` có thể đọc `float[]`, nhưng `getFloat` không thể đọc `double[]`.

**Getter và setter có thể cùng hợp lệ trên một mảng nhưng không nhất thiết theo cùng một cặp kiểu.** Phải xác định đúng *hướng conversion* ở mỗi thao tác.

### Không phải “kiểu lớn hơn” theo số bit

Quan hệ widening không chỉ dựa vào số bit. `char` không widening sang `short`, `float` không widening sang `long`, và `boolean` không widening sang loại số. Hãy dùng quy tắc Java Language Specification §5.1.2, đừng tự so sánh kích thước bộ nhớ của các primitive.

## 6. Ví dụ Java 17+ độc lập: đọc, ghi và lỗi tương thích

Ví dụ sau dùng `Array.newInstance` để tạo `int[]` động, dùng cả `get`/`set` tổng quát lẫn primitive getters/setters, kiểm tra một số trường hợp bị từ chối, và duyệt một mảng hai chiều.

Tạo tệp `ArrayAccessDemo.java`:

```java
import java.lang.reflect.Array;

public class ArrayAccessDemo {
    public static void main(String[] args) {
        Object numbers = Array.newInstance(int.class, 3);
        Array.set(numbers, 0, Byte.valueOf((byte) 7));
        Array.setInt(numbers, 1, 42);
        Array.setShort(numbers, 2, (short) -5);

        System.out.println(
                "ints=" + Array.getInt(numbers, 0)
                + "," + Array.getInt(numbers, 1)
                + "," + Array.getInt(numbers, 2)
        );

        Object boxed = Array.get(numbers, 0);
        System.out.println(
                "get type=" + boxed.getClass().getSimpleName()
                + ",value=" + boxed
        );
        System.out.println(
                "getLong(int[])=" + Array.getLong(numbers, 1)
        );

        long[] longs = new long[2];
        Array.setInt(longs, 0, 100);
        Array.set(longs, 1, Integer.valueOf(200));
        System.out.println(
                "longs=" + Array.getLong(longs, 0)
                + "," + Array.getLong(longs, 1)
        );

        double[] real = new double[2];
        Array.setFloat(real, 0, 1.25f);
        Array.setLong(real, 1, 300L);
        System.out.println(
                "real=" + Array.getDouble(real, 0)
                + "," + Array.getDouble(real, 1)
        );

        char[] letters = {'A'};
        System.out.println(
                "char as int=" + Array.getInt(letters, 0)
        );

        String[] words = new String[2];
        Array.set(words, 0, "java");
        Array.set(words, 1, null);
        System.out.println(
                "words=" + Array.get(words, 0)
                + "," + Array.get(words, 1)
        );

        int[][] grid = (int[][]) Array.newInstance(int.class, 2, 2);
        Object row = Array.get(grid, 0);
        Array.setInt(row, 1, 99);
        System.out.println("matrix=" + grid[0][1]);

        try {
            Array.getInt(real, 0);
        } catch (IllegalArgumentException e) {
            System.out.println("getInt(double[])=rejected");
        }

        try {
            Array.setDouble(numbers, 0, 1.0);
        } catch (IllegalArgumentException e) {
            System.out.println("setDouble(int[])=rejected");
        }

        try {
            Array.set(numbers, 0, Long.valueOf(1));
        } catch (IllegalArgumentException e) {
            System.out.println("set(Long,int[])=rejected");
        }

        try {
            Array.set(numbers, 0, null);
        } catch (IllegalArgumentException e) {
            System.out.println("set(null,int[])=rejected");
        }

        try {
            Array.set(words, 0, Integer.valueOf(3));
        } catch (IllegalArgumentException e) {
            System.out.println("set(Integer,String[])=rejected");
        }

        try {
            Array.get(numbers, 3);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("index=out of bounds");
        }
    }
}
```

Lệnh:

```bash
javac ArrayAccessDemo.java
java ArrayAccessDemo
```

Output:

```text
ints=7,42,-5
get type=Integer,value=7
getLong(int[])=42
longs=100,200
real=1.25,300.0
char as int=65
words=java,null
matrix=99
getInt(double[])=rejected
setDouble(int[])=rejected
set(Long,int[])=rejected
set(null,int[])=rejected
set(Integer,String[])=rejected
index=out of bounds
```

Những điểm cần hiểu:

1. `numbers` có kiểu biến `Object`, nhưng **đối tượng thực** là `int[]`. `Array.getInt` xử lý đúng mảng đó nhờ kiểm tra kiểu ở runtime.
2. `Array.set(numbers, 0, Byte)` unbox `Byte` rồi widening `byte -> int`. `Array.setShort` cũng widening `short -> int`.
3. `Array.get(numbers, 0)` boxing phần tử `int` thành `Integer`. `getLong` đọc một giá trị `int` dưới dạng `long` hợp lệ.
4. Ghi `int` vào `long[]` qua `setInt` và `Array.set` đều hợp lệ. Ghi `float` hoặc `long` vào `double[]` cũng hợp lệ.
5. `getInt` đọc một phần tử `char[]` thành giá trị số UTF-16 là `65` với `'A'`.
6. `Array.set(words, 1, null)` hợp lệ vì `String[]` là mảng reference. Trường hợp tương tự với `int[]` bị từ chối.
7. `grid` là `int[][]`; phần tử thứ nhất của nó là một **đối tượng `int[]`**, nên `Array.get(grid, 0)` trả một hàng, sau đó mới dùng `Array.setInt(row, 1, 99)`.
8. Các nhánh `try/catch` cố ý kiểm thử vi phạm conversion hoặc chỉ số; chúng không liên quan đến lỗi truy cập Java `private` hay vấn đề module.

Kết quả được viết rõ, không dựa vào nội dung thông điệp exception (vốn có thể khác giữa các phiên bản JVM). Ví dụ chỉ bắt loại exception dự kiến rồi in một chuỗi cố định.

## 7. Mảng nhiều chiều: Array.get không tự đi xuống mọi cấp

Mảng nhiều chiều trong Java là **mảng của các mảng**. Vì thế với `int[][]`:

- Mảng ngoài có component type là `int[]` (một **kiểu reference**, không phải primitive `int`).
- `Array.get(matrix, rowIndex)` trả một đối tượng `int[]` của hàng.
- Chỉ sau khi lấy được hàng mới gọi `Array.getInt(row, columnIndex)` hoặc `Array.setInt(row, columnIndex, value)`.

Ví dụ đoạn mã độc lập về ý tưởng:

```java
Object matrix = Array.newInstance(int.class, 2, 3);
Object firstRow = Array.get(matrix, 0);

Array.setInt(firstRow, 1, 88);
int result = Array.getInt(firstRow, 1); // 88
```

`Array.getInt(matrix, 0)` **không** hợp lệ: phần tử của `int[][]` là `int[]`, không phải một primitive `int`. Hãy dùng `Array.get` ở tầng ngoài rồi getter primitive ở tầng trong.

`Array.getLength(matrix)` trả `2`, còn `Array.getLength(firstRow)` trả `3`. Trong mảng jagged (`int[][]` có các hàng dài khác nhau), cần đọc chiều dài **riêng từng hàng**, không giả định mọi hàng đều cùng kích thước.

**Không có hàm `Array.getLength` trả toàn bộ shape nhiều chiều cùng lúc.** Chương trình phải điều hướng từng mảng con, kiểm tra `null` và `getLength` của mỗi cấp khi cần.

## 8. Kiểm tra kiểu component trước khi đọc hoặc ghi

Vì `Array.get`/`Array.set` nhận `Object`, công cụ Reflection tổng quát phải xác định liệu một đối tượng có phải mảng và kiểu thành phần của nó là gì:

```java
Class<?> arrayClass = array.getClass();

if (!arrayClass.isArray()) {
    throw new IllegalArgumentException("Expected an array");
}

Class<?> component = arrayClass.getComponentType();
int count = Array.getLength(array);
```

Đoạn minh họa giả định biến `array` không `null`. Nếu `array` là `int[]`, `component == int.class`. Nếu `array` là `Integer[]`, `component == Integer.class`. Nếu là `int[][]`, component của mảng **ngoài** là `int[].class`, và `component.isPrimitive()` bằng `false` vì bản thân `int[]` là kiểu tham chiếu.

Có thể chọn cách truy cập bằng một nhánh rõ ràng:

```java
Object value;

if (component == int.class) {
    value = Array.getInt(array, index);  // Boxing khi gán vào Object.
} else if (component == double.class) {
    value = Array.getDouble(array, index);
} else {
    value = Array.get(array, index);
}
```

Đây là đoạn mã minh họa, giả định `component`, `array`, `index` nằm trong phạm vi và đã kiểm tra receiver. Nếu công cụ chỉ cần giá trị dưới dạng `Object`, **`Array.get(array, index)` thường đã đủ**; chỉ chọn primitive getter khi cần kết quả primitive hoặc muốn giảm thao tác boxing trong thiết kế thích hợp.

Không cần phân nhánh `if (value instanceof Integer)` để quyết định dùng `setInt` trên `Integer[]`: mảng `Integer[]` vẫn là reference array. Kiểu component *của mảng* quyết định API primitive nào được áp dụng.

## 9. Ngoại lệ thường gặp khi dùng Array

| Tình huống | Lỗi/điều kiện |
| --- | --- |
| Truyền `null` thay cho đối tượng mảng vào `get`/`set` | `NullPointerException` |
| Truyền `String`, `List` hoặc object không phải mảng | `IllegalArgumentException` |
| Chỉ số âm hoặc `index >= length` | `ArrayIndexOutOfBoundsException` |
| `getInt` trên `double[]`, `Integer[]` hoặc kiểu không tương thích | `IllegalArgumentException` |
| `setLong` vào `int[]` | `IllegalArgumentException` do narrowing |
| `Array.set` đưa `Long` vào `int[]` | `IllegalArgumentException` do narrowing |
| `Array.set` đưa `null` vào mảng primitive | `IllegalArgumentException` |
| `Array.set` đưa `Integer` vào `String[]` | `IllegalArgumentException` vì không gán được cho component type |
| `Array.newInstance(componentType, -1)` | `NegativeArraySizeException` |
| `Array.newInstance(void.class, length)` | `IllegalArgumentException`: không tạo được mảng `void` |

Để tránh lỗi, hãy kiểm tra `array != null`, `array.getClass().isArray()`, `Array.getLength(array)`, `array.getClass().getComponentType()`, rồi xác định conversion có được phép hay không.

**Không bắt tất cả `IllegalArgumentException` và mặc định đó là lỗi quyền truy cập Reflection.** `java.lang.reflect.Array` không cần `setAccessible(true)` để đọc một mảng do mã có reference tới nó. Những lỗi ở đây thường là **không phải mảng**, **không đúng kiểu thành phần**, hoặc **không tương thích khi chuyển đổi**.

## 10. Lỗi tư duy phổ biến và cách khắc phục

**Nhầm 1: `Array.get` trả primitive nếu mảng là primitive.** Không. Chữ ký trả `Object`; primitive được boxing. Muốn kết quả kiểu `int`, dùng `getInt`.

**Nhầm 2: Getter `getDouble` đọc được bất kỳ đối tượng số.** Không. Nó chỉ dùng cho **mảng primitive số** có component type được widening tới `double`. `Double[]` và `Number[]` không được tự unbox để dùng primitive getter.

**Nhầm 3: `Array.set` chỉ nhận wrapper có kiểu chính xác.** Không. Với mảng primitive, nó có thể unbox và widening; ví dụ `Byte` vào `int[]` hợp lệ. Nhưng không narrowing.

**Nhầm 4: `setInt` vào `short[]` sẽ tự cắt phần dư.** Không. `int -> short` là narrowing và `Array.setInt` từ chối; muốn phép chuyển đổi thu hẹp, ứng dụng phải thực hiện có chủ đích trước khi gọi setter thích hợp.

**Nhầm 5: `getInt(long[])` và `setInt(long[])` đều hợp lệ.** Chỉ cái thứ hai đúng. Hướng conversion **đọc** và **ghi** khác nhau.

**Nhầm 6: Mảng multidimensional là vùng bộ nhớ có thể index như một vector phẳng bằng `Array.getInt`.** Không. Java dùng mảng lồng nhau; phải lấy từng subarray bằng `get`.

**Nhầm 7: `Array.newInstance` tạo mảng có thể tự tăng độ dài.** Không. Kích thước từng mảng cố định sau khởi tạo. Dùng collection như `ArrayList` nếu mục tiêu là tăng/giảm số phần tử.

**Nhầm 8: Widening bảo đảm giữ nguyên mọi chữ số.** Không. `long -> float/double` và `int -> float` có thể làm tròn dù conversion hợp lệ.

## 11. Câu hỏi tự kiểm tra

1. `Array.get(new int[]{5}, 0)` trả `int` hay `Integer`? **Trả `Object` chứa `Integer(5)`**.
2. `Array.getLong(new int[]{5}, 0)` có hợp lệ không? **Có** vì `int -> long` là widening.
3. `Array.getInt(new long[]{5L}, 0)` có hợp lệ không? **Không**, vì `long -> int` narrowing.
4. `Array.setInt(new long[1], 0, 7)` có được không? **Có**, vì ghi `int -> long`.
5. `Array.setLong(new int[1], 0, 7L)` có được không? **Không**, vì ghi `long -> int` cần narrowing.
6. `Array.set(new double[1], 0, Integer.valueOf(7))` có được không? **Có**, unbox `int` rồi widening `int -> double`.
7. `Array.set(new int[1], 0, Long.valueOf(1))` có được không? **Không**, giá trị nhỏ vẫn phải tuân thủ kiểu conversion.
8. `Array.getInt(new Integer[]{7}, 0)` có được không? **Không**, `Integer[]` là mảng reference.
9. Muốn đọc số ở `matrix[1][2]` khi `matrix` là `int[][]` nhưng có biến kiểu `Object`, làm sao? **`Array.get(matrix, 1)` lấy hàng, rồi `Array.getInt(row, 2)`.**
10. Khi mảng có kích thước 3, `Array.get(a, 3)` báo lỗi gì? **`ArrayIndexOutOfBoundsException`**.

## 12. Tài liệu chính thức

- [Oracle Java SE 21 — `java.lang.reflect.Array`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Array.html): hợp đồng `newInstance`, `getLength`, `get/set`, các getter/setter primitive và các ngoại lệ.
- [Java Language Specification SE 21 — §5.1.2 Widening Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.2): định nghĩa các chuyển đổi primitive được phép và các trường hợp làm tròn.
- [Oracle Java SE 21 — `Class.isArray()` và `getComponentType()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): kiểm tra đối tượng class biểu diễn mảng và kiểu thành phần.

**Ghi nhớ:** `Array.get` **đọc phần tử dưới dạng `Object`**, boxing nếu phần tử primitive; `Array.set` **ghi `Object`**, kiểm tra reference assignability hoặc unboxing + widening vào primitive array. Primitive getter widening từ **kiểu phần tử mảng sang kiểu trả về**, primitive setter widening từ **kiểu giá trị ghi sang kiểu phần tử mảng**. Chuyển đổi thu hẹp luôn bị API từ chối, dù giá trị thực tế trông có vẻ vừa miền kiểu đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-arrays-005">Boxing khi đọc mảng primitive bằng Array.get</a>

<details>
<summary>Xem chi tiết</summary>

# Boxing khi đọc mảng primitive bằng `Array.get` trong Java Reflection

## 1. Tình huống cần hiểu

`java.lang.reflect.Array.get(Object array, int index)` có kiểu trả về **`Object`**, kể cả khi phần tử của mảng là primitive như `int` hoặc `double`. Với mảng primitive, Reflection phải biểu diễn giá trị vừa đọc bằng wrapper tương ứng; với mảng tham chiếu, nó trả lại chính reference đang nằm ở ô đó.

~~~java
int[] scores = {42};
Object item = java.lang.reflect.Array.get(scores, 0);

System.out.println(item);                    // 42
System.out.println(item.getClass().getName()); // java.lang.Integer
~~~

Hình dung luồng dữ liệu:

~~~text
int[] -- đọc một ô --> int 42 -- boxing --> Integer(42) -- trả về Object
Integer[] -- đọc một ô --> reference Integer(42) --------> trả về Object
~~~

Hai trường hợp có thể cùng cho kết quả quan sát là một đối tượng `Integer`, nhưng **nguồn gốc khác nhau**: trong `int[]`, số nằm dưới dạng primitive; trong `Integer[]`, mảng lưu một reference tới object `Integer` đã có. Bài này tập trung vào ý nghĩa đó, khả năng gặp `null`, cách phân biệt nguồn và lúc nào nên dùng getter primitive chuyên biệt.

## 2. Quy tắc dựa vào component type trực tiếp

Bảng dưới mô tả **kiểu phần tử trực tiếp** của mảng và kiểu object trả về từ `Array.get`:

| Kiểu mảng | Component type | Kết quả khi đọc phần tử hợp lệ |
| --- | --- | --- |
| `boolean[]` | `boolean` | `Boolean` |
| `byte[]` | `byte` | `Byte` |
| `short[]` | `short` | `Short` |
| `char[]` | `char` | `Character` |
| `int[]` | `int` | `Integer` |
| `long[]` | `long` | `Long` |
| `float[]` | `float` | `Float` |
| `double[]` | `double` | `Double` |
| `Integer[]` | `Integer` | Reference `Integer` hoặc `null` |
| `String[]` | `String` | Reference `String` hoặc `null` |
| `int[][]` | `int[]` | Reference tới một `int[]` hoặc `null` |

Để kiểm tra *cơ chế đọc*, dùng:

~~~java
Object unknown = new int[] {42};
Class<?> component = unknown.getClass().getComponentType();

System.out.println(component == int.class);  // true
System.out.println(component.isPrimitive()); // true
~~~

`getComponentType()` chỉ bỏ **một tầng**. Chẳng hạn, `int[][].class.getComponentType() == int[].class`; vì `int[]` cũng là object, `int[][].class.getComponentType().isPrimitive()` trả `false`. Đọc tầng ngoài bằng `Array.get` cho một mảng con `int[]`; khi tiếp tục đọc **trong mảng con** thì mới boxing một số `int` thành `Integer`.

## 3. Khi hai object giống nhau về class nhưng khác ngữ nghĩa

~~~java
Object primitiveArray = new int[] {7};
Object wrapperArray = new Integer[] {7};

Object a = java.lang.reflect.Array.get(primitiveArray, 0);
Object b = java.lang.reflect.Array.get(wrapperArray, 0);

System.out.println(a.getClass() == b.getClass()); // true
System.out.println(a.equals(b));                  // true
~~~

Các kết quả trên **không cho phép kết luận** cả hai mảng có cùng loại. Kiểm tra thành phần sẽ cho `int.class` và `Integer.class`. Điều này quan trọng với serializer hoặc bộ ánh xạ dữ liệu: một mảng primitive không thể có phần tử `null`, trong khi một mảng wrapper có thể.

~~~java
int[] primitive = new int[1];
Integer[] reference = new Integer[1];

System.out.println(java.lang.reflect.Array.get(primitive, 0)); // 0
System.out.println(java.lang.reflect.Array.get(reference, 0)); // null
~~~

Không gọi `getClass()` trực tiếp trên một phần tử chưa kiểm tra `null`. Ngoài ra, **`Character` và `Boolean` không kế thừa `Number`**. Nếu công cụ phân loại kết quả với `item instanceof Number`, nó bỏ sót hai wrapper này. Khi cần hỗ trợ đủ tám primitive, hãy dựa vào **component type của mảng nguồn**, rồi chọn chính sách chuyển đổi phù hợp.

### Boxing không có nghĩa mảng đã được chuyển đổi

Dù `Array.get(new int[]{7}, 0)` trả `Integer`, đối tượng mảng nguồn vẫn là `int[]`. Nó không trở thành `Integer[]` và không thể ép sang `Object[]`. Nếu API thực sự cần một mảng reference, phải **cấp phát mảng mới và copy/boxing từng giá trị**. Một phép cast không thực hiện chuyển đổi toàn bộ mảng.

## 4. Chương trình độc lập: so sánh bốn trường hợp

Lưu thành `ArrayBoxingFocusDemo.java`, chạy bằng JDK 17+:

~~~java
import java.lang.reflect.Array;

public class ArrayBoxingFocusDemo {
    static void describe(String name, Object source, int index) {
        Class<?> component = source.getClass().getComponentType();
        Object item = Array.get(source, index);
        System.out.println(name
                + ": component=" + component.getTypeName()
                + ", primitive=" + component.isPrimitive()
                + ", value=" + (item instanceof int[] ? "int[]" : item)
                + ", resultType=" + (item == null
                        ? "null" : item.getClass().getTypeName()));
    }

    public static void main(String[] args) {
        describe("primitive", new int[] {42}, 0);
        describe("wrapper", new Integer[] {42}, 0);
        describe("empty-reference", new Integer[1], 0);
        describe("nested", new int[][] {{42}}, 0);

        Object row = Array.get(new int[][] {{42}}, 0);
        describe("nested-leaf", row, 0);

        System.out.println("getInt(byte[])="
                + Array.getInt(new byte[] {7}, 0));
        System.out.println("getLong(int[])="
                + Array.getLong(new int[] {7}, 0));

        try {
            Array.getInt(new Integer[] {7}, 0);
        } catch (IllegalArgumentException ex) {
            System.out.println("getInt(wrapperArray)=rejected");
        }
    }
}
~~~

~~~bash
javac --release 17 ArrayBoxingFocusDemo.java
java ArrayBoxingFocusDemo
~~~

Kết quả:

~~~text
primitive: component=int, primitive=true, value=42, resultType=java.lang.Integer
wrapper: component=java.lang.Integer, primitive=false, value=42, resultType=java.lang.Integer
empty-reference: component=java.lang.Integer, primitive=false, value=null, resultType=null
nested: component=int[], primitive=false, value=int[], resultType=int[]
nested-leaf: component=int, primitive=true, value=42, resultType=java.lang.Integer
getInt(byte[])=7
getLong(int[])=7
getInt(wrapperArray)=rejected
~~~

Khi chạy, hãy đối chiếu `primitive` và `wrapper`: `resultType` giống nhau, nhưng `component` và `primitive` khác. Ở `nested`, giá trị đọc được là mảng con, chưa phải một số; `nested-leaf` mới đọc giá trị primitive. Kết quả `null` của `empty-reference` là hợp lệ, không phải lỗi Reflection.

## 5. Vì sao `getInt` khác `get`?

Các getter chuyên biệt (`getBoolean`, `getByte`, `getShort`, `getChar`, `getInt`, `getLong`, `getFloat`, `getDouble`) có kiểu trả về **primitive** trên chữ ký API. Chẳng hạn:

~~~java
int[] source = {10};
Object boxed = Array.get(source, 0);   // Object tham chiếu Integer.
int exact = Array.getInt(source, 0);   // Giá trị int.
long wider = Array.getLong(source, 0); // int -> long hợp lệ.
~~~

Đoạn trên cần import `java.lang.reflect.Array` và được đặt bên trong một method.

Typed getter cho phép **identity hoặc widening primitive** từ component type của mảng **sang kiểu trả về**. `getInt` nhận `byte[]`, `short[]`, `char[]`, `int[]`, nhưng không đọc `long[]` vì đó là narrowing. `getDouble` nhận các mảng primitive số hợp lệ (không nhận `boolean[]`), tuy widening `long -> double` vẫn có thể mất độ chính xác. Typed getter **không tự unbox reference array**: `Array.getInt(new Integer[]{10}, 0)` bị từ chối.

Để tìm đầy đủ chiều conversion cho mọi getter/setter, xem bài `Array.get, Array.set và các biến thể primitive`; ở đây cần giữ một quy tắc: **`get` đóng hộp theo kiểu phần tử thực, còn `getXxx` trả primitive theo đường widening mà API cho phép**.

## 6. Khi nào boxing có tác động thực tế?

**API tổng quát:** Một inspector/serializer xử lý `Object unknownArray` có thể dùng `Array.get` để nhận phần tử dưới dạng `Object` mà không cần biết trước kiểu số. Đây là cách đơn giản, nhưng phải phân biệt wrapper vừa boxing với object tham chiếu vốn đã tồn tại.

**Hot loop:** Nếu mã biết nguồn là `int[]` và cần cộng hàng triệu số, truy cập `typedArray[i]` thường hợp lý hơn việc đọc thành `Object`, cast/unbox rồi cộng. `Array.getInt` làm rõ kiểu trả về `int`, song chi phí thực tế còn phụ thuộc JVM, JIT và cách người gọi sử dụng kết quả. Không suy đoán số object được cấp phát chỉ từ cú pháp; hãy đo ở đúng môi trường khi có vấn đề hiệu năng.

**So sánh:** Không dựa vào `==` giữa hai kết quả boxing; wrapper có thể được tái sử dụng/cached và việc đồng nhất object không phải hợp đồng để so giá trị mảng. So sánh giá trị bằng `equals` hoặc unbox với kiểu phù hợp; đừng ép mọi phần tử sang `Integer` khi nguồn có thể là `long[]` hay `char[]`.

**Đọc và ghi:** Khi `Array.set` ghi vào mảng primitive, `Object value` được xem xét theo wrapper nguồn, unboxing và widening hợp lệ trước khi lưu. Hướng conversion đó **khác hướng đọc** của `getXxx`; một giá trị `Integer` không tự chuyển thành object `Long` để ghi vào `Long[]`. Bài về điều kiện widening khi gán giải thích sâu phần này.

## 7. Ranh giới lỗi nên phân biệt

| Tình huống | Hậu quả | Bài học |
| --- | --- | --- |
| `Array.get(new int[]{7}, 0)` | `Integer` | Boxing đúng kiểu primitive |
| `Array.get(new Integer[]{null}, 0)` | `null` | Mảng reference cho phép phần tử null |
| `(Integer) Array.get(new long[]{7}, 0)` | `ClassCastException` | Kết quả thật là `Long` |
| `Array.getInt(new Integer[]{7}, 0)` | `IllegalArgumentException` | Typed getter không dùng cho reference array |
| `Array.get(new int[0], 0)` | `ArrayIndexOutOfBoundsException` | Mảng hợp lệ nhưng không có chỉ số 0 |
| `Array.get(null, 0)` | `NullPointerException` | Không có đối tượng mảng |
| `Array.get("text", 0)` | `IllegalArgumentException` | Đối tượng không phải mảng |

Nếu nhận đầu vào không rõ kiểu, kiểm tra `array != null`, `array.getClass().isArray()` và `Array.getLength(array)` trước khi truy cập phần tử. Sau đó kiểm tra `component.isPrimitive()` hoặc `item == null` trước khi xử lý kết quả. Việc này bảo vệ ranh giới dữ liệu nhưng không thay thế bước kiểm tra kiểu trong logic nghiệp vụ.

## 8. Câu hỏi tự kiểm tra

1. Vì sao `Array.get(new int[]{1}, 0)` và `Array.get(new Integer[]{1}, 0)` đều trả `Integer` nhưng có nguồn gốc khác nhau?
2. Vì sao đọc tầng ngoài `int[][]` qua `Array.get` **không boxing `int`**?
3. Kết quả của `Array.get(new boolean[]{false}, 0)` là wrapper nào? Tại sao `instanceof Number` không nhận diện được nó?
4. `Array.getLong(new int[]{10}, 0)` có hợp lệ không? `Array.getInt(new long[]{10}, 0)` thì sao?
5. Tại sao `getInt` không làm việc với `Integer[]`, dù phần tử đầu đang chứa `Integer(10)`?
6. Trong framework tổng quát, tại sao kiểm tra `component.isPrimitive()` đáng tin cậy hơn đoán nguồn mảng qua class của đối tượng trả về từ `Array.get`?

**Đáp án cốt lõi:** Quyết định boxing của `Array.get` phụ thuộc **component type trực tiếp của mảng**. Getter primitive chuyên biệt trả primitive qua identity/widening hợp lệ; một mảng reference vẫn là mảng reference, bất kể nó lưu wrapper gì.

Tài liệu chuẩn: [Java SE 17 — `java.lang.reflect.Array`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Array.html), [JLS §5.1.7 — Boxing](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.7).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-arrays-006">Điều kiện widening và lỗi sai kiểu khi gán phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện widening và lỗi sai kiểu khi gán phần tử mảng bằng Java Reflection

Trong Java Reflection, lớp `java.lang.reflect.Array` cho phép tạo mảng và đọc, ghi phần tử khi chỉ biết kiểu tại runtime. Điểm dễ nhầm là lời gọi `Array.set(array, index, value)` **không cho phép mọi phép chuyển đổi số**, cũng không hành xử hoàn toàn giống toán tử gán trực tiếp cho mảng tham chiếu. Một giá trị có thể được ghi vào phần tử khi **kiểu runtime của mảng, loại giá trị và các quy tắc chuyển đổi được phép** tương thích với nhau.

Ví dụ:

```java
int[] values = new int[2];
Array.set(values, 0, Byte.valueOf((byte) 7)); // Được: byte -> int
Array.set(values, 1, Character.valueOf('A')); // Được: char -> int (65)
Array.set(values, 0, Long.valueOf(7));        // IllegalArgumentException
```

Đoạn trên minh họa API khi đã `import java.lang.reflect.Array;`. Ở lời gọi thứ ba, giá trị `long` dù bằng `7` vẫn không được tự động thu hẹp (*narrowing*) thành `int`. Các kiểm tra này dựa trên **kiểu**, không dựa vào việc số hiện tại có vừa giới hạn biểu diễn của kiểu đích hay không.

Bài học nằm trong nhóm **Mảng động**; trọng tâm là `Array.set`, `Array.setXxx`, widening primitive, mảng tham chiếu, exception và các bước kiểm tra trước khi gán bằng Reflection.

## 1. Mảng động và kiểu phần tử được xác định ở runtime

Một mảng Java luôn có **kiểu thành phần (component type)** và chiều dài cố định sau khi tạo. «Mảng động» trong ngữ cảnh Reflection nghĩa là **chọn loại mảng/tạo mảng bằng metadata `Class<?>` lúc runtime**, không phải mảng tự đổi kích thước giống `ArrayList`.

```java
Class<?> componentType = int.class;
Object array = Array.newInstance(componentType, 3);

System.out.println(array.getClass().getTypeName());        // int[]
System.out.println(array.getClass().getComponentType());   // int
System.out.println(Array.getLength(array));                // 3
```

Đoạn trên cần `import java.lang.reflect.Array;`. `Array.newInstance(componentType, length)` trả về `Object` ở mặt API, nhưng đối tượng thực vẫn là **một mảng JVM đúng kiểu**, chẳng hạn `int[]`, `String[]` hoặc `Number[]`.

Điều quyết định khi gọi `Array.set` là **kiểu phần tử của đối tượng mảng thực tế**, không đơn thuần là kiểu của biến đang giữ tham chiếu:

```java
Number[] numbers = new Integer[1];
// Kiểu khai báo của biến: Number[]
// Kiểu runtime của mảng: Integer[]
```

Khi ghi, JVM vẫn bảo vệ kiểu runtime `Integer[]`. Giá trị `Long` không thể được cất vào phần tử của `Integer[]` chỉ vì biến tham chiếu có kiểu `Number[]`.

## 2. API ghi phần tử: `Array.set` và các phương thức chuyên biệt

`java.lang.reflect.Array` có các API:

| API | Giá trị truyền vào | Vai trò |
| --- | --- | --- |
| `Array.set(Object array, int index, Object value)` | `Object`, gồm đối tượng wrapper | Ghi vào mảng tham chiếu hoặc unbox/widen hợp lệ vào mảng primitive |
| `Array.setBoolean(array, index, boolean)` | `boolean` | Ghi primitive boolean |
| `Array.setByte(array, index, byte)` | `byte` | Ghi byte hoặc widening sang loại primitive tương thích |
| `Array.setChar(array, index, char)` | `char` | Ghi char hoặc widening tương thích |
| `Array.setShort(array, index, short)` | `short` | Ghi short hoặc widening tương thích |
| `Array.setInt(array, index, int)` | `int` | Ghi int hoặc widening tương thích |
| `Array.setLong(array, index, long)` | `long` | Ghi long hoặc widening tương thích |
| `Array.setFloat(array, index, float)` | `float` | Ghi float hoặc widening tương thích |
| `Array.setDouble(array, index, double)` | `double` | Ghi double |

`Array.setXxx` hỗ trợ giá trị primitive và chỉ hoạt động với **mảng primitive** có kiểu phần tử thích hợp. Nó không có mục đích tự boxing để ghi vào `Object[]`/`Number[]`. Muốn ghi wrapper vào mảng tham chiếu, dùng `Array.set` với đối tượng tương ứng.

Với `Array.set`, primitive truyền ở mã Java sẽ **được boxing trước** vì tham số cuối của API có kiểu `Object`:

```java
int[] ints = new int[1];

Array.set(ints, 0, 5);                // Compiler boxing 5 thành Integer
Array.set(ints, 0, Integer.valueOf(5)); // Tương đương kiểu đối số
Array.setInt(ints, 0, 5);             // API ghi primitive trực tiếp
```

Khi nhận `Integer` để ghi vào `int[]`, Reflection unbox thành `int`. Đối với mảng đích primitive khác, có thể có thêm bước **widening primitive**.

## 3. Quy tắc widening primitive khi ghi mảng

*Widening primitive conversion* là chuyển từ một primitive sang primitive khác theo quy tắc Java cho phép mà không cần phép ép tường minh. Chẳng hạn `byte -> int`, `int -> long`, `float -> double`. Ngược lại, `long -> int`, `double -> float` hoặc `short -> byte` là *narrowing* và không được Reflection tự thực hiện khi ghi.

Bảng dưới mô tả **nguồn primitive** và các **kiểu phần tử mảng đích được phép** khi ghi bằng phương thức `Array.setXxx` thích hợp hoặc khi `Array.set` nhận đúng wrapper nguồn:

| Kiểu nguồn | Kiểu đích hợp lệ (gồm chính kiểu nguồn) |
| --- | --- |
| `boolean` | `boolean` |
| `byte` | `byte`, `short`, `int`, `long`, `float`, `double` |
| `short` | `short`, `int`, `long`, `float`, `double` |
| `char` | `char`, `int`, `long`, `float`, `double` |
| `int` | `int`, `long`, `float`, `double` |
| `long` | `long`, `float`, `double` |
| `float` | `float`, `double` |
| `double` | `double` |

Một số hệ quả quan trọng:

- `byte -> char` **không** là widening primitive. `short -> char` và `char -> short` cũng không được phép tự động.
- `boolean` không tự chuyển sang số và số không tự chuyển sang `boolean`.
- `Integer -> long[]` hợp lệ với `Array.set`: unbox `Integer` thành `int`, rồi widening `int -> long`.
- `Character -> int[]` hợp lệ vì `char -> int`, nên ký tự `'A'` trở thành số `65`.
- `Long -> int[]` **không** hợp lệ, dù giá trị `Long.valueOf(7)` có thể biểu diễn bằng `int`.
- Không có phép chuyển đổi wrapper-sang-wrapper tự động: `Integer` không tự biến thành đối tượng `Long` để cất vào `Long[]`.

**Widening không luôn bảo toàn chính xác tất cả chữ số.** Chuyển `long -> float` hoặc `int -> float` có thể mất độ chính xác số học do giới hạn độ chính xác của số thực; quy tắc widening vẫn cho phép chuyển đổi. Vì vậy không nên đồng nhất «được ghi» với «không thể mất precision».

## 4. `Array.set` trên mảng primitive thực hiện hai bước chuyển đổi

Với API tổng quát `Array.set(array, index, value)`, giá trị truyền vào có kiểu `Object`. Nếu mảng đích có kiểu phần tử primitive:

1. `value` phải là đối tượng của **một wrapper primitive tương ứng**, ví dụ `Byte`, `Character`, `Integer`, `Long`, `Float`, `Double`, `Boolean`, `Short`.
2. Reflection unbox wrapper thành primitive nguồn.
3. Nếu primitive nguồn cùng kiểu với component type hoặc có **widening primitive** tới component type, phần tử được cập nhật.
4. Nếu đòi hỏi narrowing, chuyển đổi không hợp lệ hoặc `value == null`, `Array.set` ném `IllegalArgumentException`.

Ví dụ:

```java
int[] ints = new int[2];

Array.set(ints, 0, Byte.valueOf((byte) 7));  // byte -> int
Array.set(ints, 1, Character.valueOf('A')); // char -> int
System.out.println(java.util.Arrays.toString(ints)); // [7, 65]
```

Ngược lại:

```java
Array.set(ints, 0, Long.valueOf(7L)); // IllegalArgumentException
Array.set(ints, 0, null);             // IllegalArgumentException
```

Số `7L` không được tự thu hẹp dù nằm trong miền giá trị `int`; Reflection không áp dụng conversion tùy ý dựa trên nội dung số. Cũng không thể unbox `null` để ghi vào primitive.

Nếu một ứng dụng muốn chuyển `long -> int` theo chính sách riêng (ví dụ chỉ cho phép giá trị nằm trong phạm vi `int`), ứng dụng phải **tự kiểm tra miền giá trị, ép kiểu có chủ đích và truyền một `Integer`/`int` hợp lệ**. Không để `Array.set` tự đoán chính sách thu hẹp.

## 5. `Array.setXxx` cũng cho phép widening thích hợp

Một suy luận sai là `Array.setInt` chỉ ghi được `int[]`. Theo hợp đồng của Reflection, nó cũng có thể ghi sang mảng có component type chấp nhận widening của `int`:

```java
long[] longs = new long[1];
double[] doubles = new double[1];

Array.setInt(longs, 0, 99);       // int -> long
Array.setFloat(doubles, 0, 2.5f); // float -> double

System.out.println(longs[0]);   // 99
System.out.println(doubles[0]); // 2.5
```

Trong khi đó:

```java
int[] ints = new int[1];
Array.setLong(ints, 0, 7L); // IllegalArgumentException
```

`long -> int` là narrowing, không hợp lệ với setter chuyên biệt. `Array.setDouble(floatArray, i, value)` cũng bị từ chối vì `double -> float` là narrowing.

Các hàm getter chuyên biệt như `Array.getInt(array, index)` có quy tắc chuyển đổi riêng khi **đọc**. Không nên suy ra quy tắc ghi chỉ từ tên hàm hoặc chỉ từ việc một cách đọc thành công. Ở bài này, ta quan tâm chính xác đến chiều **source value -> array component type** khi gán phần tử.

## 6. Mảng tham chiếu: cần tương thích kiểu runtime của phần tử

Nếu component type là **reference type**, `Array.set` không sử dụng widening primitive để tạo đối tượng reference mới. Giá trị phải:

- Là `null`; hoặc
- Là đối tượng có kiểu runtime tương thích với component type của mảng, theo quan hệ gán reference hợp lệ.

Ví dụ:

```java
Number[] values = new Number[3];

Array.set(values, 0, Integer.valueOf(1)); // Integer extends Number
Array.set(values, 1, Long.valueOf(2));    // Long extends Number
Array.set(values, 2, null);               // Mảng reference nhận null
```

`Integer` và `Long` đều là subtype của `Number` nên có thể đặt vào `Number[]`. Nhưng `Integer` không là subtype của `Long` và ngược lại:

```java
Long[] longValues = new Long[1];
Array.set(longValues, 0, Integer.valueOf(1)); // IllegalArgumentException
```

Không có chuyện `Integer -> int -> long -> Long` được Reflection tự thực hiện để ghi vào `Long[]`.

### Cẩn thận với covariance của mảng

Java cho phép:

```java
Number[] numbers = new Integer[1];
```

Mảng **thực** vẫn là `Integer[]`. Vì vậy:

```java
Array.set(numbers, 0, Integer.valueOf(10)); // Thành công
Array.set(numbers, 0, Long.valueOf(20));    // IllegalArgumentException
```

Dù biến tham chiếu mang kiểu `Number[]`, thành phần runtime là `Integer`. Lời gọi qua `Array.set` với giá trị không tương thích báo `IllegalArgumentException`.

Phép gán mảng **trực tiếp** lại báo exception khác:

```java
numbers[0] = Long.valueOf(20); // ArrayStoreException tại runtime
```

Đây là một phân biệt hữu ích:

| Đường ghi | Mảng thực | Giá trị sai kiểu | Lỗi điển hình |
| --- | --- | --- | --- |
| `Array.set(numbers, 0, Long.valueOf(20))` | `Integer[]` | `Long` | `IllegalArgumentException` |
| `numbers[0] = Long.valueOf(20)` | `Integer[]` | `Long` | `ArrayStoreException` |

Đừng gom hai exception thành một loại khi bắt lỗi và hiển thị chẩn đoán. Thao tác reflection và phép gán mảng bytecode bình thường có hợp đồng báo lỗi khác nhau.

## 7. Ví dụ Java 17+ hoàn chỉnh, chạy độc lập

Lưu toàn bộ mã dưới đây vào `ArrayWideningDemo.java`. Ví dụ có mảng được tạo động bằng `Array.newInstance`, widening hợp lệ, narrowing bị từ chối, mảng tham chiếu và lỗi truy cập ngoài chỉ số.

```java
import java.lang.reflect.Array;
import java.util.Arrays;

public class ArrayWideningDemo {
    public static void main(String[] args) {
        int[] ints = (int[]) Array.newInstance(int.class, 3);

        // Byte -> byte -> int (unboxing, widening).
        Array.set(ints, 0, Byte.valueOf((byte) 7));

        // Character -> char -> int. Ký tự 'A' có mã 65.
        Array.set(ints, 1, Character.valueOf('A'));

        // Ghi primitive int bằng setter chuyên biệt.
        Array.setInt(ints, 2, 42);
        System.out.println("int[]: " + Arrays.toString(ints));

        long[] longs = (long[]) Array.newInstance(long.class, 1);
        Array.setInt(longs, 0, 99);
        System.out.println("long[] from int: " + longs[0]);

        double[] doubles =
                (double[]) Array.newInstance(double.class, 1);
        Array.set(doubles, 0, Float.valueOf(2.5f));
        System.out.println("double[] from float: " + doubles[0]);

        // Narrowing long -> int không được tự động.
        try {
            Array.set(ints, 0, Long.valueOf(6L));
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "long -> int: IllegalArgumentException");
        }

        // Primitive không thể chứa null.
        try {
            Array.set(ints, 0, null);
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "null -> int: IllegalArgumentException");
        }

        // short -> char không phải widening primitive.
        try {
            Array.set(new char[1], 0, Short.valueOf((short) 1));
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "short -> char: IllegalArgumentException");
        }

        // Kiểu khai báo Number[] không đổi kiểu mảng runtime Integer[].
        Number[] numbers = new Integer[1];
        Array.set(numbers, 0, Integer.valueOf(10));
        System.out.println("runtime array type: "
                + numbers.getClass().getTypeName());

        try {
            Array.set(numbers, 0, Long.valueOf(20));
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "Long -> Integer[] via Array.set: IllegalArgumentException");
        }

        try {
            numbers[0] = Long.valueOf(20);
        } catch (ArrayStoreException e) {
            System.out.println(
                    "Long -> Integer[] direct: ArrayStoreException");
        }

        Object[] objects = new Object[1];
        Array.set(objects, 0, Integer.valueOf(11));
        System.out.println("Object[]: "
                + objects[0].getClass().getSimpleName());

        try {
            Array.set(ints, 8, Integer.valueOf(1));
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println(
                    "out-of-bounds: ArrayIndexOutOfBoundsException");
        }

        // API get tổng quát boxing giá trị primitive thành Integer.
        System.out.println("Array.get(int[]): "
                + Array.get(ints, 0).getClass().getSimpleName());
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac ArrayWideningDemo.java
java ArrayWideningDemo
```

Kết quả:

```text
int[]: [7, 65, 42]
long[] from int: 99
double[] from float: 2.5
long -> int: IllegalArgumentException
null -> int: IllegalArgumentException
short -> char: IllegalArgumentException
runtime array type: java.lang.Integer[]
Long -> Integer[] via Array.set: IllegalArgumentException
Long -> Integer[] direct: ArrayStoreException
Object[]: Integer
out-of-bounds: ArrayIndexOutOfBoundsException
Array.get(int[]): Integer
```

Ba nhóm thông điệp đáng nhớ:

1. `byte -> int`, `char -> int`, `int -> long` và `float -> double` đều hợp lệ. Reflection áp dụng đúng đường widening primitive.
2. `long -> int`, `short -> char` và `null -> int` thất bại bằng `IllegalArgumentException` vì loại chuyển đổi/giá trị không hợp lệ, dù chỉ số mảng vẫn đúng.
3. Khi ghi `Long` vào mảng runtime `Integer[]`, `Array.set` báo `IllegalArgumentException` còn gán trực tiếp qua biến `Number[]` báo `ArrayStoreException`. Cả hai đều bảo vệ kiểu phần tử runtime.

Cuối ví dụ, `Array.get(ints, 0)` có kiểu trả về `Object` nên primitive `int` được boxing thành đối tượng `Integer`. Điều này **không biến `int[]` thành `Integer[]`**.

## 8. Kiểm tra trước khi gán bằng Reflection

Nếu viết framework nhận danh sách đối tượng từ nguồn dữ liệu không biết trước, không nên chỉ nhìn kiểu của đối số và rồi gọi `Array.set` một cách ngẫu nhiên. Trình tự tốt hơn:

1. **Xác nhận đối tượng đích là mảng:** `array != null` và `array.getClass().isArray()`.
2. **Xác định kiểu phần tử runtime:** `Class<?> component = array.getClass().getComponentType()`.
3. **Xác định chỉ số:** `0 <= index < Array.getLength(array)`.
4. **Nếu component là primitive:** `null` bị từ chối; lấy wrapper thực tế của `value`, ánh xạ sang primitive nguồn và kiểm tra widening primitive có hợp lệ.
5. **Nếu component là reference:** `null` được chấp nhận; các đối tượng khác cần `component.isInstance(value)`.
6. **Gọi `Array.set` một lần** sau khi chính sách đã rõ; vẫn xử lý exception đúng loại vì dữ liệu/đích có thể thay đổi trong môi trường concurrent.

Một helper đơn giản cho mảng **reference** có thể viết:

```java
import java.lang.reflect.Array;

public final class ReferenceArrayGuard {
    public static void set(Object array, int index, Object value) {
        if (array == null || !array.getClass().isArray()) {
            throw new IllegalArgumentException("Expected array");
        }
        Class<?> component = array.getClass().getComponentType();
        if (component.isPrimitive()) {
            throw new IllegalArgumentException(
                    "This helper only supports reference arrays");
        }
        if (index < 0 || index >= Array.getLength(array)) {
            throw new IndexOutOfBoundsException("index=" + index);
        }
        if (value != null && !component.isInstance(value)) {
            throw new IllegalArgumentException(
                    value.getClass().getTypeName()
                    + " is not an instance of "
                    + component.getTypeName());
        }
        Array.set(array, index, value);
    }
}
```

Đây là class tiện ích **không chứa `main`**; có thể biên dịch độc lập, nhưng chỉ xử lý mảng reference theo mục tiêu đã ghi. Đối với mảng primitive, helper kiểu chung cần một **bảng widening** giống phần 3. Không được tự suy ra widening từ `component.isAssignableFrom(value.getClass())`: `Class.isAssignableFrom` kiểm tra quan hệ kiểu, **không** biểu diễn boxing/unboxing hay widening primitive.

Ví dụ `int.class.isAssignableFrom(Byte.class)` là `false`, nhưng `Array.set(new int[1], 0, Byte.valueOf((byte) 1))` vẫn thành công.

## 9. Các lỗi cần phân biệt khi thao tác mảng

| Exception | Tình huống thường gặp | Hướng xử lý |
| --- | --- | --- |
| `IllegalArgumentException` | `Array.set` nhận giá trị không tương thích với component type; hoặc đối tượng truyền vào không phải array | Kiểm tra component type, wrapper nguồn và bảng widening |
| `NullPointerException` | Đối tượng `array` của API bằng `null` | Xác minh array trước lời gọi; khác với giá trị phần tử `null` |
| `ArrayIndexOutOfBoundsException` | Chỉ số âm hoặc lớn hơn/bằng độ dài mảng | Kiểm tra `Array.getLength(array)` và miền chỉ số |
| `ArrayStoreException` | Gán trực tiếp qua biến mảng tham chiếu nhưng phần tử không tương thích với kiểu mảng runtime | Xác định kiểu mảng thực tế, đặc biệt với array covariance |

Ví dụ đối tượng mảng hoàn toàn hợp lệ mà chỉ số sai:

```java
int[] data = new int[2];
Array.set(data, 2, Integer.valueOf(10)); // ArrayIndexOutOfBoundsException
```

Đây là lỗi giới hạn chỉ số, không phải lỗi widening. Mặt khác:

```java
int[] data = new int[2];
Array.set(data, 0, Long.valueOf(10L)); // IllegalArgumentException
```

Chỉ số 0 hợp lệ, nhưng `Long -> int` đòi narrowing nên lỗi nằm ở kiểu giá trị.

**Không nên bắt mọi `RuntimeException` rồi gọi chung là «sai kiểu».** Việc phân loại đúng sẽ giúp báo lỗi có thể sửa được trong framework nhập dữ liệu, mapper, serializer hoặc plugin reflection.

## 10. Các trường hợp dễ suy luận nhầm

### 10.1. Giá trị nhỏ không làm narrowing trở thành widening

`Long.valueOf(1L)` vẫn mang kiểu `Long`. Gán qua `Array.set` vào `int[]` không được, dù `1L` nằm trọn trong miền `int`. Nếu cố ý cho phép narrowing có kiểm tra phạm vi:

```java
long raw = 1L;
int converted = Math.toIntExact(raw);
Array.setInt(new int[1], 0, converted);
```

`Math.toIntExact` ném `ArithmeticException` nếu giá trị `long` nằm ngoài miền `int`. Đây là **chính sách chuyển đổi tường minh của ứng dụng**, không phải quy tắc mặc định của `Array.set`.

### 10.2. Wrapper khác không có nghĩa là primitive compatible

`Array.set(new Long[1], 0, Integer.valueOf(1))` thất bại. Mặc dù `int -> long` widening được phép với **mảng primitive `long[]`**, mục tiêu `Long[]` là mảng **đối tượng**, cần một instance `Long` hoặc `null`. Muốn ghi thành `Long`, tự tạo đối tượng:

```java
Long[] boxed = new Long[1];
Array.set(boxed, 0, Long.valueOf(1L));
```

### 10.3. Không ép kiểu cho `boolean`

Không có widening giữa `boolean` và các kiểu số. `Array.set(new boolean[1], 0, Boolean.TRUE)` hợp lệ; `Array.set(new boolean[1], 0, Integer.valueOf(1))` không hợp lệ. Tương tự `Array.set(new int[1], 0, Boolean.TRUE)` không hợp lệ.

### 10.4. Mảng nhiều chiều có component là một mảng

Với:

```java
int[][] matrix = new int[2][3];
```

Kiểu thành phần của **mảng ngoài** là `int[]`, tức một **reference type**. `Array.set(matrix, 0, new int[] {1, 2, 3})` hợp lệ, còn `Array.set(matrix, 0, Integer.valueOf(1))` không hợp lệ vì phần tử mảng ngoài phải là `int[]`, không phải một số nguyên. Nếu muốn ghi một ô số, lấy mảng con hoặc dùng hai bước:

```java
Object row = Array.get(matrix, 0);
Array.setInt(row, 1, 9);
```

Lúc ghi `row[1]`, component type của **mảng con** mới là `int`. Luôn kiểm tra đúng cấp mảng mà bạn đang gán.

### 10.5. Widening có thể mất độ chính xác

`long -> float` được cho phép nhưng `float` không thể biểu diễn chính xác mọi số nguyên 64-bit. Widening ở đây có nghĩa conversion được ngôn ngữ chấp nhận mà không cần cast tường minh; nó **không bảo đảm giữ nguyên toàn bộ giá trị số học**. Nếu hệ thống cần độ chính xác tuyệt đối, phải đặt thêm quy tắc kiểm tra hoặc dùng kiểu dữ liệu thích hợp.

## 11. Quy trình chẩn đoán khi `Array.set` thất bại

Khi thấy exception trong reflection-driven array mapper, hãy kiểm tra theo thứ tự:

1. **Array thật:** `array.getClass().getTypeName()` là gì? Có đúng là array không? Có phải `Integer[]` dù biến khai báo `Number[]`?
2. **Component thật:** `getComponentType()` là `int.class` hay `Integer.class`? Hai tình huống có quy tắc ghi khác nhau.
3. **Index:** có nằm trong `[0, Array.getLength(array))`?
4. **Value:** có phải `null`? Nếu không, `value.getClass().getTypeName()` là gì?
5. **Primitive target:** wrapper nguồn tương ứng primitive nào? Có đường widening trong bảng phần 3 không? Có lỡ yêu cầu narrowing không?
6. **Reference target:** `component.isInstance(value)` có đúng không? Có gặp array covariance không?
7. **Đường gọi nào:** đang dùng `Array.set`, setter chuyên biệt hay phép gán mảng Java trực tiếp? Exception báo ra tương ứng có thể khác.
8. **Chuyển đổi có chủ đích:** nếu nghiệp vụ muốn parse chuỗi, chuẩn hóa JSON number hoặc thu hẹp số có kiểm soát, làm bước đó **trước** `Array.set`, với chính sách lỗi rõ ràng.

Ví dụ ngắn giúp quan sát metadata:

```java
Object array = new int[2];
Object value = Long.valueOf(7L);
Class<?> component = array.getClass().getComponentType();

System.out.println("array type: "
        + array.getClass().getTypeName());
System.out.println("component: "
        + component.getTypeName());
System.out.println("value type: "
        + value.getClass().getTypeName());
```

Đầu ra:

```text
array type: int[]
component: int
value type: java.lang.Long
```

Từ đây suy ra cần `long -> int`, không được Reflection cho phép; phải sửa giá trị/kiểu mảng hoặc áp dụng chuyển đổi tường minh có kiểm tra.

## 12. Tự kiểm tra nhanh

**Câu 1.** `Array.set(new long[1], 0, Integer.valueOf(10))` có hợp lệ không?  
Có. `Integer` được unbox thành `int`, sau đó widening thành `long`.

**Câu 2.** `Array.set(new int[1], 0, Long.valueOf(10))` có hợp lệ không?  
Không. `long -> int` là narrowing; `Array.set` ném `IllegalArgumentException`.

**Câu 3.** `Array.set(new char[1], 0, Short.valueOf((short) 65))` có hợp lệ không?  
Không. `short -> char` không phải widening primitive dù số 65 tương ứng ký tự `'A'`.

**Câu 4.** `Array.set(new Number[1], 0, Integer.valueOf(1))` có hợp lệ không?  
Có. `Integer` là subtype của `Number` nên tương thích với component type reference `Number`.

**Câu 5.** Với `Number[] x = new Integer[1]`, `Array.set(x, 0, Long.valueOf(1))` ném gì?  
`IllegalArgumentException`, vì đối tượng mảng thực có component type `Integer`. Nếu thay bằng `x[0] = Long.valueOf(1)`, JVM gán mảng trực tiếp có thể ném `ArrayStoreException`.

**Câu 6.** Tại sao `Array.set(new Long[1], 0, Integer.valueOf(1))` không tương đương với `Array.set(new long[1], 0, Integer.valueOf(1))`?  
Vì `Long[]` chứa **đối tượng `Long`** và cần reference compatibility; `long[]` chứa **primitive `long`** và cho phép unboxing `Integer` rồi widening `int -> long`.

## Ghi nhớ

- Với **mảng primitive**, `Array.set` nhận wrapper rồi **unbox và widening primitive** nếu đường chuyển đổi hợp lệ. Không tự narrowing và không nhận `null`.
- Với **mảng tham chiếu**, `Array.set` yêu cầu giá trị là `null` hoặc instance tương thích với **component type runtime**; không tự biến wrapper này thành wrapper khác.
- `Array.setXxx` có thể ghi sang mảng primitive rộng hơn theo bảng widening, không chỉ mảng cùng kiểu với `Xxx`.
- **Sai kiểu trong `Array.set`** thường là `IllegalArgumentException`; gán trực tiếp vào mảng tham chiếu sai kiểu có thể là `ArrayStoreException`.
- Luôn kiểm tra riêng **đối tượng mảng, component type, index và value** để phân biệt lỗi kiểu với lỗi truy cập ngoài phạm vi.

Nắm vững điều kiện **unboxing → widening primitive** và **reference assignability theo kiểu mảng thực** giúp ghi phần tử an toàn và chẩn đoán chính xác các lỗi khi sử dụng Java Reflection với mảng động.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-arrays-007">getComponentType cho từng tầng mảng</a>

<details>
<summary>Xem chi tiết</summary>

# getComponentType() cho từng tầng mảng trong Java Reflection

Trong Java, mảng một chiều và mảng nhiều chiều đều là **đối tượng có kiểu mảng**. Java Reflection cho phép đọc cấu trúc kiểu đó ngay cả khi chương trình không biết trước số chiều hay kiểu phần tử cụ thể. Hai API quan trọng nhất là `Class.isArray()` và `Class.getComponentType()`.

Ý tưởng cốt lõi: **mỗi lần gọi `getComponentType()` chỉ bỏ đi đúng một tầng mảng**. Nếu muốn tìm kiểu phần tử gốc của `int[][][]`, ta phải gọi lặp cho đến khi kiểu hiện tại không còn là mảng. Không nhầm kiểu thành phần của tầng kế tiếp với giá trị đang nằm trong một ô của mảng.

Bài thuộc chủ đề **Mảng động** trong Java Core Reflection; trọng tâm là khám phá từng tầng kiểu mảng và áp dụng metadata đó khi tạo/truy cập mảng bằng Reflection.

## 1. Khái niệm: array class, component type và element type

Xét ba biến:

```java
int[] one = new int[4];
int[][] two = new int[2][3];
String[][][] three = new String[2][3][4];
```

Đây là đoạn minh họa Java. Mỗi biến có một `Class<?>` khác nhau:

| Biểu thức lấy `Class<?>` | `isArray()` | `getComponentType()` | Kiểu phần tử gốc |
| --- | --- | --- | --- |
| `int[].class` | `true` | `int.class` | `int` |
| `int[][].class` | `true` | `int[].class` | `int` |
| `String[][][].class` | `true` | `String[][].class` | `String` |
| `Object[].class` | `true` | `Object.class` | `Object` |
| `int.class` | `false` | `null` | Không phải kiểu mảng |
| `String.class` | `false` | `null` | Không phải kiểu mảng |

Phân biệt:

- **Array class**: kiểu của **toàn bộ đối tượng mảng**, ví dụ `int[][].class`.
- **Immediate component type**: kiểu thành phần **trực tiếp**. `int[][].class.getComponentType()` trả `int[].class`, **không** trả ngay `int.class`.
- **Ultimate element type**: kiểu phần tử gốc khi bóc hết các chiều, ví dụ `int.class` đối với `int[][][]`.
- **Number of dimensions**: số lần cần gọi `getComponentType()` từ array class đến khi gặp kiểu không còn là array.

Đây là cấu trúc kiểu, không phải kích thước runtime: `int[2][3]` và `int[100][0]` đều có kiểu `int[][].class`, mặc dù độ dài và tình trạng các hàng khác nhau.

## 2. API Class.getComponentType() hoạt động thế nào?

API Java:

```java
Class<?> component = someType.getComponentType();
```

Với một `Class<?>` biểu diễn kiểu mảng, kết quả là `Class<?>` của thành phần trực tiếp. Nếu `someType` **không biểu diễn kiểu mảng**, kết quả là `null`. Phương thức `getComponentType()` không duyệt sâu toàn bộ số chiều và không truy cập phần tử nào của mảng.

```java
System.out.println(int[][][].class.getComponentType().getTypeName());
// int[][]

System.out.println(int[][].class.getComponentType().getTypeName());
// int[]

System.out.println(int[].class.getComponentType().getTypeName());
// int

System.out.println(int.class.getComponentType());
// null
```

Đoạn code minh họa có thể đặt trong `main`. `Class.getTypeName()` dùng để trình bày tên kiểu dễ đọc: `int[][]`, `java.lang.String[]`,... Trong khi đó, `Class.getName()` của array class sử dụng tên nhị phân/descriptor kiểu như `[[I`, nên khi viết tài liệu hoặc log cho người đọc, `getTypeName()` thường phù hợp hơn.

Đừng gọi `getTypeName()` trên kết quả null:

```java
Class<?> next = String.class.getComponentType();
// next == null; next.getTypeName() sẽ gây NullPointerException.
```

Quy tắc an toàn là kiểm tra `type.isArray()` trước mỗi lần lấy tầng tiếp theo, hoặc kiểm tra `component != null`.

## 3. Bóc từng tầng: sơ đồ cho int[][][]

Tưởng tượng bắt đầu bằng `int[][][].class`:

```text
Tầng 0: int[][][]  --getComponentType()--> int[][]
Tầng 1: int[][]    --getComponentType()--> int[]
Tầng 2: int[]      --getComponentType()--> int
Tầng 3: int        --getComponentType()--> null
```

Vậy số chiều là **3**. Khi dừng ở `int.class`, ta đã tìm được kiểu phần tử gốc. `int.class.isArray()` là `false`, cho dù `int` được tìm thấy bằng cách bóc từ một mảng.

Điều tương tự đúng cho:

```text
java.lang.String[][] → java.lang.String[] → java.lang.String
boolean[][]          → boolean[]           → boolean
Object[]             → Object
```

Không được “nhảy” trực tiếp từ `String[][]` đến `String.class` nếu yêu cầu là khám phá **từng tầng**. Thông tin `String[].class` ở tầng giữa thường cần khi phản chiếu mảng đa chiều hoặc tạo các đoạn mảng không đều.

## 4. Chương trình Java 17+ chạy độc lập

Tạo tệp `ArrayComponentLayersDemo.java`:

```java
import java.lang.reflect.Array;

public class ArrayComponentLayersDemo {

    static void describe(String label, Class<?> startingType) {
        System.out.println(label + ":");
        Class<?> current = startingType;
        int dimensions = 0;

        while (current.isArray()) {
            Class<?> next = current.getComponentType();
            System.out.println("  " + current.getTypeName()
                    + " -> " + next.getTypeName());
            current = next;
            dimensions++;
        }

        System.out.println("  dimensions=" + dimensions
                + ", element=" + current.getTypeName());
        System.out.println("  non-array component="
                + current.getComponentType());
    }

    public static void main(String[] args) {
        describe("primitive 3D", int[][][].class);
        describe("reference 2D", String[][].class);
        describe("scalar", int.class);

        int[][] rectangular =
                (int[][]) Array.newInstance(int.class, 2, 3);
        rectangular[0][1] = 7;

        System.out.println("rectangular type="
                + rectangular.getClass().getTypeName());
        System.out.println("rectangular direct component="
                + rectangular.getClass().getComponentType().getTypeName());
        System.out.println("outer length=" + Array.getLength(rectangular));
        System.out.println("row length="
                + Array.getLength(Array.get(rectangular, 0)));
        System.out.println("value=" + Array.getInt(rectangular[0], 1));

        int[][] ragged = new int[2][];
        ragged[0] = new int[] {1, 2};
        ragged[1] = new int[] {3};

        System.out.println("ragged type="
                + ragged.getClass().getTypeName());
        System.out.println("ragged lengths="
                + ragged[0].length + "," + ragged[1].length);

        int[][] unfilled =
                (int[][]) Array.newInstance(int[].class, 2);
        System.out.println("unfilled type="
                + unfilled.getClass().getTypeName());
        System.out.println("unfilled row null=" + (unfilled[0] == null));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ArrayComponentLayersDemo.java
java ArrayComponentLayersDemo
```

Stdout chính xác:

```text
primitive 3D:
  int[][][] -> int[][]
  int[][] -> int[]
  int[] -> int
  dimensions=3, element=int
  non-array component=null
reference 2D:
  java.lang.String[][] -> java.lang.String[]
  java.lang.String[] -> java.lang.String
  dimensions=2, element=java.lang.String
  non-array component=null
scalar:
  dimensions=0, element=int
  non-array component=null
rectangular type=int[][]
rectangular direct component=int[]
outer length=2
row length=3
value=7
ragged type=int[][]
ragged lengths=2,1
unfilled type=int[][]
unfilled row null=true
```

Phân tích:

1. Mỗi vòng lặp `while (current.isArray())` tiến xuống **một chiều**, tăng biến `dimensions` một đơn vị. Khi `current` là kiểu gốc không phải mảng, vòng lặp kết thúc.
2. `int[][][].class` cho ba dòng chuyển tiếp và phần tử gốc `int`. `String[][].class` cho hai dòng và phần tử gốc `java.lang.String`.
3. Khi đầu vào là `int.class`, số chiều bằng 0 và `getComponentType()` của kiểu đó là null.
4. `Array.newInstance(int.class, 2, 3)` tạo mảng `int[][]` có hai hàng và mỗi hàng dài ba phần tử. Kiểu trực tiếp bên trong là `int[]`.
5. `Array.get(rectangular, 0)` lấy đối tượng mảng con ở hàng đầu; `Array.getLength(...)` sau đó đo độ dài **mảng con**, không phải toàn mảng hai chiều.
6. `Array.getInt(rectangular[0], 1)` đọc phần tử primitive int ở hàng đầu, vị trí 1. Không được truyền thẳng `rectangular` vào `Array.getInt` với chỉ số hàng rồi mong nhận một int, vì component trực tiếp của `rectangular` là mảng `int[]`.
7. `ragged` có hai hàng dài khác nhau, nhưng runtime class vẫn là `int[][].class`. Kiểu không mã hóa kích thước hoặc tính “hình chữ nhật”.
8. `Array.newInstance(int[].class, 2)` tạo mảng hai chiều theo kiểu tổng thể `int[][]` nhưng chỉ cấp phát một tầng ngoài; các phần tử `int[]` bên trong còn null.

## 5. Khác biệt giữa cấp độ kiểu và cấp độ dữ liệu

`Class.getComponentType()` là thao tác **trên kiểu**; `java.lang.reflect.Array.get(...)` và `Array.getLength(...)` là thao tác **trên đối tượng mảng cụ thể**.

Ví dụ:

```java
int[][] data = {
    {10, 20, 30},
    {40}
};

Class<?> outerType = data.getClass();                // int[][].class
Class<?> innerType = outerType.getComponentType();   // int[].class
Object firstRow = Array.get(data, 0);                // một int[]
int rowCount = Array.getLength(data);                // 2
int firstRowLength = Array.getLength(firstRow);      // 3
int secondRowLength = Array.getLength(Array.get(data, 1)); // 1
```

Đây là đoạn minh họa, cần `import java.lang.reflect.Array;`. Kiểu `int[][]` không cho biết hàng thứ nhất dài 3 và hàng thứ hai dài 1. Chỉ **đối tượng mảng cụ thể** mới chứa các độ dài thực tế này.

Đặc biệt:

- `getComponentType()` không trả **phần tử tại index 0**; nó trả một **Class** mô tả kiểu thành phần.
- `Array.get(data, 0)` trả **Object** thực tế ở slot 0, ở ví dụ này là một mảng `int[]`.
- Nếu hàng chưa khởi tạo, `Array.get(data, 0)` có thể trả null, trong khi `data.getClass().getComponentType()` vẫn trả `int[].class`.
- `Array.getLength(someArray)` nhận một đối tượng mảng; nếu truyền scalar hoặc null không phù hợp sẽ ném exception theo API, không mang ý nghĩa “số chiều của kiểu”.

Điều này quan trọng khi duyệt dữ liệu JSON, cấu trúc ma trận hoặc mảng răng cưa: thông tin từ `Class<?>` quyết định **kiểu**, còn muốn duyệt dữ liệu phải xem **mỗi mảng con**.

## 6. Tạo mảng động với Array.newInstance và component type

API `Array.newInstance` có hai dạng thường dùng:

```java
Object newInstance(Class<?> componentType, int length)
Object newInstance(Class<?> componentType, int... dimensions)
```

Đây là chữ ký rút gọn từ `java.lang.reflect.Array`; nó mô tả hai overload chứ không phải method cần tự khai báo. Cơ chế khác nhau:

### Cung cấp kiểu phần tử gốc và nhiều chiều

```java
Object created = Array.newInstance(int.class, 2, 3);
System.out.println(created.getClass().getTypeName()); // int[][]
```

Một `componentType` là `int.class` và hai số chiều trong `dimensions` tạo mảng kiểu `int[][]`, với tất cả các tầng kích thước được cấp phát theo các giá trị truyền vào.

### Cung cấp sẵn một kiểu mảng làm component

```java
Object created = Array.newInstance(int[].class, 2);
System.out.println(created.getClass().getTypeName()); // int[][]
```

Tình huống này cấp phát **một tầng bên ngoài** có hai phần tử, mỗi phần tử có kiểu `int[]` và được khởi tạo mặc định là null. Chỉ sau khi gán từng mảng con, các hàng mới có dữ liệu:

```java
int[][] values = (int[][]) Array.newInstance(int[].class, 2);
values[0] = new int[3];
values[1] = new int[1];
```

Nếu dùng `Array.newInstance(int[].class, 2, 3)`, kết quả là **`int[][][]`**, không phải `int[][]`: kiểu component đã có một chiều, rồi lời gọi thêm hai chiều nữa.

Đây là lỗi hay gặp ở mã xử lý số chiều động. Công thức tổng quát cho kiểu:

**Số chiều kiểu kết quả = số chiều vốn có của componentType + số chiều mới yêu cầu ở Array.newInstance.**

Lưu ý: overload `Array.newInstance(componentType, int length)` luôn thêm một chiều kiểu. Overload có `int... dimensions` có thể thêm nhiều chiều; mảng `dimensions` phải không rỗng. Kích thước âm gây `NegativeArraySizeException`; số chiều kết quả vượt giới hạn JVM hoặc `void.class` làm component là không hợp lệ (`IllegalArgumentException`). Không được gọi `Array.newInstance` rồi tùy tiện ép sang một kiểu mảng khác với runtime class mà nó tạo.

## 7. Primitive arrays khác reference arrays

`int[]` là mảng **primitive**, trong khi `String[]` và `int[][]` đều có component trực tiếp là **reference type**:

| Kiểu mảng | Component trực tiếp | Có thể cast toàn bộ mảng sang `Object[]`? |
| --- | --- | --- |
| `int[]` | `int` | Không |
| `double[]` | `double` | Không |
| `String[]` | `String` | Có |
| `int[][]` | `int[]` (reference type) | Có |
| `int[][][]` | `int[][]` (reference type) | Có |

Ví dụ:

```java
int[] numbers = {1, 2};
int[][] rows = {{1, 2}, {3}};

Object anyNumbers = numbers;  // Hợp lệ: mọi mảng đều là Object
Object[] outerRows = rows;    // Hợp lệ: component trực tiếp là int[]
// Object[] bad = numbers;     // Sai tại compile time
```

Mảng `int[]` không phải `Object[]` dù chính đối tượng mảng có thể gán vào biến `Object`. Trong khi đó `int[][]` là một mảng các reference `int[]`, nên tầng ngoài phù hợp với `Object[]`.

Reflection API `Array.get(anyArray, index)` giúp viết logic dùng chung cho cả primitive và reference arrays. Khi cần gán phần tử, `Array.set(array, index, value)` kiểm tra kiểu hợp lệ và chuyển đổi primitive phù hợp; nó không làm phép chuyển tùy ý.

Đối với primitive array, `Array.get(intArray, index)` trả wrapper object (ví dụ `Integer`), còn `Array.getInt(intArray, index)` trả giá trị int trực tiếp, miễn array và kiểu thành phần tương thích với getter đó.

## 8. Hỗ trợ số chiều chưa biết khi chạy

Trong công cụ generic, ta có thể không biết trước đối tượng là `String[]`, `long[][]` hay `Customer[][][]`. Một utility lấy kiểu phần tử gốc và đếm chiều:

```java
public static Class<?> deepestElementType(Class<?> type) {
    if (type == null) {
        throw new IllegalArgumentException("type must not be null");
    }

    Class<?> current = type;
    while (current.isArray()) {
        current = current.getComponentType();
    }
    return current;
}

public static int dimensions(Class<?> type) {
    if (type == null) {
        throw new IllegalArgumentException("type must not be null");
    }

    int count = 0;
    Class<?> current = type;
    while (current.isArray()) {
        count++;
        current = current.getComponentType();
    }
    return count;
}
```

Đây là hai hàm tiện ích minh họa để đặt bên trong một class Java, không phải chương trình `main` độc lập. Kết quả:

```text
deepestElementType(int[][][].class) = int.class
dimensions(int[][][].class) = 3
deepestElementType(String[][].class) = String.class
dimensions(String[][].class) = 2
deepestElementType(String.class) = String.class
dimensions(String.class) = 0
```

Dòng minh họa ở đây thể hiện **giá trị logic trả về**, không phải stdout tự động in bởi hai hàm.

Với yêu cầu “đầu vào bắt buộc là array”, hãy kiểm tra `type.isArray()` ở ranh giới tiện ích và báo lỗi nếu false. Còn các hàm ở trên chấp nhận scalar và trả kiểu đó cùng số chiều 0: cách xử lý này có thể hữu ích khi bộ phân tích hỗ trợ cả hai nhóm kiểu.

### Duyệt từng tầng và không tạo mảng vô hạn

Vòng lặp `while (type.isArray())` luôn kết thúc đối với một array class hợp lệ: mỗi bước `getComponentType()` bỏ đi đúng một chiều và cuối cùng đến kiểu phần tử gốc. Điều cần tránh là gọi `getComponentType()` mãi sau khi kiểu không còn là mảng, rồi thao tác với null.

Khi cần xây cấu trúc mới dựa trên số chiều được phát hiện, phải xác định rõ **kiểu thành phần dùng làm đầu vào Array.newInstance**. Nếu đưa nguyên kiểu mảng đã có vào đó mà lại yêu cầu thêm toàn bộ số chiều đếm được, kết quả sẽ thừa chiều.

## 9. Class.getComponentType không mang thông tin generic arguments

`Class<?>` biểu thị **runtime type** theo Java type erasure. Nếu có khai báo:

```java
java.util.List<String>[] lists;
```

thì runtime component class tương ứng của kiểu mảng là `java.util.List.class`, không phải một đối tượng `Class<List<String>>` bảo tồn `String` dưới dạng type argument.

Với một `Field` phản chiếu khai báo generic, `Field.getGenericType()` trả `java.lang.reflect.Type`, có thể là `GenericArrayType` hoặc loại `Type` khác để phân tích generic declaration tùy trường hợp. Trong khi đó, `Field.getType()` trả một `Class<?>` theo kiểu đã erasure; gọi `getComponentType()` trên nó cho biết các **tầng mảng của runtime class**, không giải mã đầy đủ kiểu generic.

Vì vậy:

- Muốn biết số chiều mảng và kiểu phần tử runtime: dùng `Class.isArray()/getComponentType()`.
- Muốn hiểu chữ ký generic đã khai báo trong source: xem `Field.getGenericType()` hoặc metadata generic tương ứng.
- Đừng giả định runtime `Class` lưu sẵn type arguments của `List<String>`.

Đây là ranh giới thông tin giữa Class và Type, không phải lỗi của `getComponentType()`.

## 10. Lỗi thường gặp

| Nhầm lẫn | Vì sao sai | Cách hiểu đúng |
| --- | --- | --- |
| `int[][].class.getComponentType()` trả `int.class` | Chỉ bóc một tầng mỗi lần | Kết quả là `int[].class` |
| `getComponentType()` trả số phần tử | Nó thao tác trên metadata của kiểu | Muốn độ dài của mảng cụ thể, dùng `Array.getLength(array)` |
| `getComponentType()` luôn trả Class khác null | Kiểu không phải mảng cho kết quả null | Kiểm tra `isArray()` trước |
| Mảng hai chiều luôn có các hàng dài bằng nhau | Java cho phép mảng răng cưa | Đo độ dài từng mảng con riêng |
| `new int[2][]` đã tạo hai hàng `int[]` | Chỉ mảng ngoài được cấp phát | Ban đầu mỗi slot hàng là null |
| `Array.newInstance(int[].class, 2, 3)` trả `int[][]` | Input `int[]` đã có một chiều | Kết quả là `int[][][]` |
| `Object[] values = new int[3]` hợp lệ | Primitive array không là `Object[]` | `int[]` chỉ gán được vào `Object`; dùng `Array.get` cho logic chung |
| `int[][]` không thể xem là `Object[]` | Component của tầng ngoài là `int[]`, một reference type | Toàn mảng hai chiều có thể gán cho `Object[]` |
| `int[2][3]` và `int[2][1]` là hai class khác nhau | Độ dài không thuộc định danh kiểu mảng | Cả hai có class `int[][].class` |
| Có thể lấy generic `String` từ `List<String>[]` bằng `getComponentType()` | Runtime erasure không lưu generic type argument trong Class | Dùng GenericType metadata của khai báo nếu cần |
| `getName()` và `getTypeName()` luôn giống nhau | Tên array class có thể là descriptor | Ưu tiên `getTypeName()` khi hiển thị dễ đọc |

## 11. Bài tập thực hành

**Bài 1.** Cho `byte[][][][]`. Hãy liệt kê kết quả của ba lần đầu gọi `getComponentType()` rồi xác định tầng cuối.

**Đáp án:** `byte[][][]`, `byte[][]`, `byte[]`; lần thứ tư trả `byte.class`. Có bốn chiều.

**Bài 2.** Cho `String[][] names = new String[2][];`. Tại sao `names.getClass().getComponentType()` vẫn trả `String[].class` dù `names[0] == null`?

**Đáp án:** Component type là metadata của **kiểu mảng**, độc lập với việc một slot có chứa đối tượng mảng con hay còn null.

**Bài 3.** `Array.newInstance(double[][].class, 4)` tạo kiểu gì?

**Đáp án:** `double[][][]`, gồm một tầng ngoài dài 4, các phần tử `double[][]` ban đầu là null.

**Bài 4.** Vì sao `int[][] matrix` có thể gán cho `Object[]` nhưng `int[] row` thì không?

**Đáp án:** Component trực tiếp của `int[][]` là reference type `int[]`. Component của `int[]` là primitive `int`, nên `int[]` không là `Object[]`.

**Bài 5.** Có thể biết số hàng/cột của một mảng nhiều chiều chỉ từ `Class.getComponentType()` không?

**Đáp án:** Không. API này chỉ mô tả kiểu từng tầng. Cần có đối tượng mảng và dùng `Array.getLength()` ở từng tầng thực tế; các mảng con có thể null hoặc dài khác nhau.

## 12. Tổng kết

**Quy tắc quan trọng nhất:** với mọi array class, **`getComponentType()` chỉ lấy một tầng thành phần trực tiếp**.

1. Bắt đầu từ `Class<?>` của đối tượng hoặc kiểu đã biết.
2. Trong khi `isArray() == true`, gọi `getComponentType()` để chuyển xuống một tầng và tăng số chiều đã đếm.
3. Khi đến kiểu không phải mảng, đó là kiểu phần tử gốc; `getComponentType()` lúc này trả null.
4. Khi tạo mảng, phân biệt `Array.newInstance(baseType, dimensions...)` với `Array.newInstance(existingArrayType, length)` để không thêm thừa chiều.
5. Khi đọc dữ liệu, dùng `Array.getLength` và `Array.get` trên **đối tượng mảng thật**, không suy độ dài từ `Class<?>`.

**Tài liệu tham khảo:** [Java SE 17 — Class.getComponentType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getComponentType()) · [Java SE 17 — java.lang.reflect.Array](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Array.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-arrays-008">ArrayIndexOutOfBoundsException, NegativeArraySizeException và đầu vào không phải mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán lỗi mảng động: chỉ số, kích thước âm và đầu vào không phải mảng

## 1. Mỗi lỗi chỉ đúng một loại vi phạm

`java.lang.reflect.Array` thao tác trên mảng thật ở runtime, kể cả khi caller chỉ có biến `Object`. Cùng một hệ thống có thể nhận kiểu mảng từ cấu hình, tạo mảng, rồi đọc/ghi phần tử. Lỗi sẽ có ý nghĩa khác nhau tùy **bước thất bại**:

| Bước | Đầu vào sai | Exception gốc của API |
| --- | --- | --- |
| Cấp phát `Array.newInstance` | Kích thước âm ở một chiều | `NegativeArraySizeException` |
| Tạo mảng | `componentType == null` | `NullPointerException` |
| Tạo mảng | `void.class`, không có chiều, hoặc tổng rank vượt 255 | `IllegalArgumentException` |
| `Array.getLength`/`get`/`set` | Tham chiếu array là `null` | `NullPointerException` |
| `Array.getLength`/`get`/`set` | Object khác `null` nhưng không phải mảng | `IllegalArgumentException` |
| `Array.get`/`set` | Index âm hoặc `index >= length` | `ArrayIndexOutOfBoundsException` |
| `Array.set` | Giá trị không tương thích với component type | `IllegalArgumentException` |

Từ bảng này, có thể nhớ ba trường hợp trọng tâm:

- **`ArrayIndexOutOfBoundsException`:** đối tượng mảng đã tồn tại, nhưng chỉ số vượt biên.
- **`NegativeArraySizeException`:** thông số cấp phát một chiều hoặc nhiều chiều là số âm.
- **`IllegalArgumentException` do non-array:** API nhận object nhưng object ấy không phải một đối tượng mảng.

`null` là trường hợp khác: `Array.getLength(null)` gây `NullPointerException`; `new int[0]` vẫn là mảng hoàn toàn hợp lệ. Hãy dựa vào **API đã gọi và dữ liệu tại bước đó**, vì một tên exception có thể biểu thị nhiều nguyên nhân khác nhau.

## 2. Kiểm tra độ dài trước khi tạo

`Array.newInstance(Class<?> componentType, int length)` tạo một mảng dài `length`; overload `int... dimensions` tạo nhiều chiều theo thứ tự ngoài vào trong.

~~~java
Array.newInstance(int.class, 0);         // int[0], hợp lệ.
Array.newInstance(int.class, -1);        // NegativeArraySizeException.
Array.newInstance(String.class, 2, -1);  // NegativeArraySizeException.
Array.newInstance(void.class, 1);        // IllegalArgumentException.
Array.newInstance(int.class, new int[0]); // IllegalArgumentException.
~~~

Đây là các lời gọi minh họa có import `java.lang.reflect.Array`; những lời gọi gây lỗi không nên chạy liên tiếp ngoài `try/catch`.

Một **chiều bằng 0** vẫn hợp lệ, khác với **không truyền chiều nào**. `Array.newInstance(String.class, 2, 0)` cấp phát hai mảng con rỗng, còn `Array.newInstance(String[].class, 2)` chỉ cấp phát mảng ngoài và để hai mảng con `null`.

Nếu component type vốn là array class, tổng số chiều kết quả bằng rank đã có cộng số chiều truyền thêm. Java giới hạn rank kiểu mảng ở 255, nên `Array.newInstance(int[][].class, new int[254])` đòi 256 chiều và bị từ chối. Để học chi tiết hình dạng mảng, xem bài tạo mảng nhiều chiều trong cùng chương; khi chẩn đoán lỗi, trước hết xác định **rank đích** và **từng kích thước**.

### Dữ liệu kích thước từ bên ngoài

Kiểm tra `length >= 0` là cần nhưng chưa đủ. Một giá trị dương rất lớn có thể gây `OutOfMemoryError` hoặc gặp giới hạn cấp phát của JVM. Nếu độ dài tính qua phép cộng/nhân, tràn `int` có thể biến đầu vào dương thành số âm hoặc số không đúng. Hãy đặt giới hạn nghiệp vụ và dùng `Math.addExact`/`multiplyExact` khi cần phát hiện overflow.

Ví dụ helper với chính sách tối đa 10.000 phần tử tầng ngoài:

~~~java
static Object newBoundedArray(Class<?> type, int length) {
    if (type == null || type == void.class) {
        throw new IllegalArgumentException("Invalid component type");
    }
    if (length < 0 || length > 10_000) {
        throw new IllegalArgumentException("Unsupported length: " + length);
    }
    return java.lang.reflect.Array.newInstance(type, length);
}
~~~

`IllegalArgumentException` trên là **do helper tự chọn** để trả lỗi cấu hình sớm, không phải `NegativeArraySizeException` của `Array.newInstance`. Giới hạn 10.000 cũng là ví dụ chính sách ứng dụng, **không phải giới hạn của Java**.

## 3. Kiểm tra mảng thật trước khi đo độ dài

`Array.getLength(Object)` nhận **một đối tượng mảng**, chẳng hạn `new int[]{1}`, `new String[0]` hoặc `new int[2][3]`. Nó không nhận `String`, `List` hay chính đối tượng `Class` mô tả kiểu mảng.

~~~java
Object actual = new int[] {1, 2};
Object metadata = int[].class;

System.out.println(actual.getClass().isArray());   // true
System.out.println(metadata.getClass().isArray()); // false
System.out.println(Array.getLength(actual));       // 2
// Array.getLength(metadata); // IllegalArgumentException.
~~~

Đoạn trên dùng `Array` đã import. `int[].class.isArray()` đúng vì token ấy **mô tả kiểu mảng**, nhưng `int[].class` vẫn là đối tượng `Class` chứ không phải một instance `int[]`.

Mẫu bảo vệ đầu vào:

~~~java
static int requireLength(Object candidate) {
    if (candidate == null) {
        throw new IllegalArgumentException("Array is required");
    }
    if (!candidate.getClass().isArray()) {
        throw new IllegalArgumentException(
                "Not an array: " + candidate.getClass().getTypeName());
    }
    return java.lang.reflect.Array.getLength(candidate);
}
~~~

Helper chủ động trả `IllegalArgumentException` cho cả `null` lẫn non-array. Đây là **hợp đồng của ứng dụng**; API nguyên thủy vẫn phân biệt `NullPointerException` cho `null` và `IllegalArgumentException` cho object không phải mảng.

Không quy ước `0` nghĩa là “không phải mảng”. `new int[0]` là mảng thật, có length 0 và không chứa index hợp lệ.

## 4. Kiểm tra chỉ số trên đúng mảng đang truy cập

Với mảng cụ thể dài `n`, điều kiện để đọc/ghi là:

\[0 \leq index < n\]

Nếu `n == 0` thì **không có index hợp lệ**. `Array.get(new int[0], 0)` ném `ArrayIndexOutOfBoundsException` dù việc tạo mảng rỗng đã thành công.

Có thể kiểm tra:

~~~java
static Object checkedGet(Object array, int index) {
    if (array == null || !array.getClass().isArray()) {
        throw new IllegalArgumentException("Expected an array");
    }
    int length = java.lang.reflect.Array.getLength(array);
    if (index < 0 || index >= length) {
        throw new IndexOutOfBoundsException(
                "index=" + index + ", length=" + length);
    }
    return java.lang.reflect.Array.get(array, index);
}
~~~

Helper dùng `IndexOutOfBoundsException` của ứng dụng để giải thích lỗi, còn `Array.get` trực tiếp dùng subtype `ArrayIndexOutOfBoundsException`. `Objects.checkIndex(index, length)` cũng giúp kiểm tra biên nhưng không cam kết đúng subclass do Reflection ném. Khi chẩn đoán, cần biết **exception phát ra từ API nào**.

### Mảng nhiều chiều có biên riêng ở từng tầng

~~~java
int[][] rows = {{1, 2, 3}, {}, null, {4}};
~~~

`Array.getLength(rows)` bằng 4; bốn hàng có độ dài 3, 0, chưa có mảng con, và 1. Muốn lấy `rows[3][0]` bằng Reflection:

1. Xác minh chỉ số hàng 3 trong độ dài mảng ngoài.
2. `Object row = Array.get(rows, 3)` rồi kiểm tra `row != null`.
3. Đo `Array.getLength(row)` bằng 1, xác minh index cột 0.
4. `Array.get(row, 0)` trả một `Integer` chứa 4 vì mảng con có component primitive `int`.

Với hàng 1, index cột 0 vượt biên; với hàng 2, gọi `Array.getLength` trên `null` sẽ gây `NullPointerException`. Hai lỗi khác nhau dù đều xuất hiện khi xử lý một ma trận.

## 5. Chương trình Java 17+ kiểm tra danh tính exception

Tạo file `ReflectArrayFailureAtlas.java`:

~~~java
import java.lang.reflect.Array;

public class ReflectArrayFailureAtlas {
    static void show(String name, Runnable action) {
        try {
            action.run();
            System.out.println(name + ": OK");
        } catch (RuntimeException ex) {
            System.out.println(name + ": "
                    + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        Object array = Array.newInstance(String.class, 2);
        Array.set(array, 0, "A");
        System.out.println("length=" + Array.getLength(array));

        show("negative", () -> Array.newInstance(int.class, -1));
        show("negative dimension",
                () -> Array.newInstance(int.class, 2, -1));
        show("void", () -> Array.newInstance(void.class, 1));
        show("no dimension",
                () -> Array.newInstance(int.class, new int[0]));

        show("null length", () -> Array.getLength(null));
        show("non-array", () -> Array.getLength("hello"));
        show("class token", () -> Array.getLength(int[].class));

        show("index -1", () -> Array.get(array, -1));
        show("index at length", () -> Array.get(array, 2));
        show("wrong element", () -> Array.set(array, 0, 123));

        Object partial = Array.newInstance(int[].class, 1);
        show("missing subarray",
                () -> Array.getLength(Array.get(partial, 0)));
        show("empty array index", () -> Array.get(new int[0], 0));
    }
}
~~~

~~~bash
javac --release 17 ReflectArrayFailureAtlas.java
java ReflectArrayFailureAtlas
~~~

Kết quả:

~~~text
length=2
negative: NegativeArraySizeException
negative dimension: NegativeArraySizeException
void: IllegalArgumentException
no dimension: IllegalArgumentException
null length: NullPointerException
non-array: IllegalArgumentException
class token: IllegalArgumentException
index -1: ArrayIndexOutOfBoundsException
index at length: ArrayIndexOutOfBoundsException
wrong element: IllegalArgumentException
missing subarray: NullPointerException
empty array index: ArrayIndexOutOfBoundsException
~~~

`show` bắt `RuntimeException` chỉ để **in loại lỗi khi thực hành**. Trong ứng dụng thực, một khối catch rộng như vậy dễ che giấu lỗi lập trình; nên kiểm tra trước, báo đúng dữ liệu sai và bắt lỗi cụ thể ở ranh giới có thể xử lý.

## 6. Không nhầm lỗi phần tử với lỗi đầu vào không phải mảng

`IllegalArgumentException` không chỉ có nghĩa “không phải mảng”. Chẳng hạn:

~~~java
Array.set(new String[1], 0, 42);     // Sai component type.
Array.set(new int[1], 0, 3L);        // long -> int là narrowing.
Array.set(new int[1], 0, null);      // Primitive không nhận null.
~~~

Các thao tác trên đều có **mảng hợp lệ và index hợp lệ**, nhưng giá trị không thể chuyển đổi theo hợp đồng `Array.set`. Với mảng primitive, Reflection có thể unbox wrapper rồi widening hợp lệ; không tự narrowing. Với mảng reference, giá trị phải là `null` hoặc tương thích với component type **runtime** của mảng.

Một khác biệt đáng nhớ:

~~~java
Object[] refs = new String[1];
refs[0] = Integer.valueOf(5); // ArrayStoreException.

Object array = new String[1];
Array.set(array, 0, Integer.valueOf(5)); // IllegalArgumentException.
~~~

Hai API ghi khác nhau và có hợp đồng báo lỗi khác nhau. Muốn tìm nguyên nhân, ghi rõ hoạt động nào đã xảy ra, kiểu runtime của mảng, component type, index và kiểu của value (nếu khác `null`).

## 7. Quy trình chẩn đoán dành cho mapper hoặc plugin

Khi xử lý dữ liệu không biết trước kiểu, hãy đi theo thứ tự:

1. **Tạo mảng:** component type có khác `null`, khác `void.class`, rank hợp lệ, mọi kích thước không âm và trong ngân sách bộ nhớ không?
2. **Xử lý mảng đã có:** giá trị có khác `null`, runtime class có `isArray() == true` không?
3. **Đọc/ghi:** index có thỏa `0 <= index < Array.getLength(array)` tại đúng tầng không?
4. **Ghi phần tử:** `array.getClass().getComponentType()` là gì, value có `null` không, và có cần conversion riêng (parse chuỗi, thu hẹp số có kiểm soát) trước khi gọi `Array.set` không?
5. **Mảng nhiều chiều:** mảng con hiện tại đã được cấp phát chưa? Nó có thể là `null` dù runtime class mảng ngoài đã có nhiều chiều.
6. **Thông báo lỗi:** nêu tên trường đầu vào, loại thao tác, index, length và component type; giữ thông tin cần thiết để sửa cấu hình nhưng tránh log cả dữ liệu phần tử nhạy cảm.

Đừng coi mọi `RuntimeException` phát sinh trong pipeline là “sai kiểu mảng”. `NegativeArraySizeException` là lỗi tạo, `ArrayIndexOutOfBoundsException` là lỗi vị trí, và `IllegalArgumentException` có thể là input không phải mảng **hoặc** lỗi chuyển đổi phần tử.

### Bảng quyết định nhanh

| Dấu hiệu | Kiểm tra tiếp |
| --- | --- |
| Không tạo được mảng | Dimension âm, số chiều, `void.class`, component `null`, bộ nhớ |
| `getLength` thất bại | Null hay object không phải array? |
| `get`/`set` thất bại | Index thực tế, length của đúng tầng, loại array |
| `set` sai kiểu | Primitive/reference component, kiểu value, widening/narrowing |
| Chỉ thất bại khi đi xuống tầng trong | Mảng con `null`, jagged length, nhầm số chiều |
| Nhầm `ArrayStoreException` với `IllegalArgumentException` | Cú pháp gán trực tiếp hay `Array.set`? |

## 8. Tự kiểm tra

1. `Array.newInstance(int.class, 0)` có lỗi không? `Array.get(new int[0], 0)` thì sao?
2. `Array.newInstance(String.class, 2, -3)` ném gì? Một helper tự ném `IllegalArgumentException` trước khi gọi thì khác thế nào?
3. Vì sao `Array.getLength(int[].class)` sai mặc dù `int[].class.isArray()` là true?
4. Với `int[][] rows = {{1}, null}`, lỗi khi đọc `rows[0][1]` khác gì lỗi đo độ dài `rows[1]`?
5. Vì sao `Array.set(new String[1], 0, Integer.valueOf(1))` không ném cùng exception với gán qua `Object[] alias = new String[1]`?
6. Nếu nguồn gửi một kích thước rất lớn nhưng không âm, việc cấp phát có chắc chắn thành công không?

**Điều cần nhớ:** phân biệt lỗi **cấp phát**, lỗi **nhận dạng mảng**, lỗi **chỉ số** và lỗi **kiểu giá trị** theo bước API đã gọi. Khi có đối tượng mảng hợp lệ, kiểm tra biên ở đúng tầng; khi chỉ có input cấu hình, xác minh component type/kích thước trước khi cấp phát. Không đánh đồng mảng rỗng với `null` và không coi `0` là mã báo “không phải mảng”.

Tài liệu chính thức: [Java SE 17 — `java.lang.reflect.Array`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Array.html), [Java SE 17 — `Class.isArray`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html), [Java SE 17 — `Objects.checkIndex`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-arrays-009">Mảng generic, erasure và giới hạn tạo T&#91;&#93;</a>

<details>
<summary>Xem chi tiết</summary>

# Mảng generic, erasure và giới hạn tạo `T[]` trong Java

## 1. Bài toán: tại sao mảng động generic không thể dùng `new T[n]`?

Khi xây dựng một mảng động (dynamic array), ta cần vùng lưu trữ có thể tăng dung lượng khi số phần tử vượt quá sức chứa ban đầu. Với kiểu cố định, việc cấp phát mảng rất trực tiếp:

```java
String[] values = new String[10];
```

Nhưng trong lớp generic, cách viết tưởng như tương đương lại không hợp lệ:

```java
class DynamicArray<T> {
    // Không biên dịch: generic array creation
    // private T[] values = new T[10];

    private T[] values; // Khai báo T[] thì hợp lệ.
}
```

Giới hạn này không xuất phát từ việc JVM không có mảng. Nguyên nhân là sự kết hợp giữa **mảng kiểm tra kiểu phần tử tại runtime** và **generic sử dụng type erasure**. Nắm rõ hai cơ chế này giúp chọn cấu trúc lưu trữ đúng, tránh ép kiểu thiếu an toàn và thiết kế API trả mảng chuẩn kiểu.

## 2. Mảng (array) và generic có hai cơ chế kiểm tra kiểu khác nhau

| Đặc điểm | Mảng `String[]` | Generic `List<String>` |
| --- | --- | --- |
| Kiểu thành phần tại runtime | Có: đối tượng mảng biết component type là `String` | Tham số `String` thường bị xóa bởi erasure |
| Quan hệ kiểu | Mảng kiểu tham chiếu có tính **covariant**: `String[]` gán được cho `Object[]` | Generic mặc định **invariant**: `List<String>` không gán được cho `List<Object>` |
| Kiểm tra thao tác lưu | JVM kiểm tra phần tử khi gán vào mảng; sai kiểu gây `ArrayStoreException` | Trình biên dịch kiểm tra tham số generic chủ yếu tại compile time |
| Ví dụ runtime | `new String[2].getClass()` biểu diễn lớp mảng `String[]` | `new ArrayList<String>().getClass()` không giữ đối số kiểu `String` như một `Class<List<String>>` riêng |

Ví dụ mảng covariance và kiểm tra tại runtime:

```java
Object[] array = new String[1]; // Hợp lệ: String[] là subtype của Object[].
array[0] = "OK";                // Hợp lệ.
array[0] = 123;                 // Ném ArrayStoreException tại runtime.
```

Biến `array` có kiểu tĩnh `Object[]`, nhưng đối tượng thực tế vẫn là `String[]`. JVM kiểm tra kiểu thực tế khi ghi, nên phần tử `Integer` bị từ chối.

Ngược lại, hai biến `List<String>` và `List<Integer>` về cơ bản đều dựa trên đối tượng có lớp runtime như `ArrayList`. JVM không có một component type runtime riêng là `List<String>` để kiểm tra phép ghi vào mảng.

### Erasure hoạt động thế nào?

**Type erasure** là quá trình Java compiler chuyển kiểu generic sang dạng không giữ đầy đủ đối số kiểu trong mã thực thi: `List<String>` và `List<Integer>` đều có erasure là `List`; biến kiểu không bị chặn `T` có erasure là `Object`; với `T extends Number`, erasure của `T` là bound bên trái `Number`.

```java
class Holder<T> {
    T value; // Về cơ bản dùng Object sau erasure nếu T không có bound.

    T get() {
        return value;
    }
}
```

Compiler có thể chèn phép ép kiểu ở nơi sử dụng để bảo toàn kỳ vọng kiểu tĩnh. Do vậy, generic tạo được kiểm tra kiểu mạnh ở compile time nhưng không biến `T` thành một lớp runtime có thể dùng trực tiếp để khởi tạo mảng.

## 3. Reifiable type và các phép tạo mảng được phép

Một kiểu được gọi là **reifiable** khi có đủ thông tin kiểu cần thiết tại runtime. Các ví dụ gồm kiểu nguyên thủy, lớp không generic, raw type, kiểu parameterized chỉ chứa wildcard không giới hạn như `List<?>`, và mảng có component type reifiable.

| Biểu thức | Hợp lệ? | Giải thích |
| --- | --- | --- |
| `new String[4]` | Có | `String` là reifiable. |
| `new int[4]` | Có | `int` là kiểu nguyên thủy. |
| `new List<?>[4]` | Có | `List<?>` là reifiable. |
| `new List[4]` | Có nhưng dùng raw type | Mất kiểm tra kiểu generic khi dùng raw. |
| `new List<String>[4]` | Không | `List<String>` không reifiable. |
| `new List<? extends Number>[4]` | Không | Wildcard có bound này không phải dạng reifiable `List<?>`. |
| `new T[4]` trong lớp/phương thức generic | Không | Type variable `T` không reifiable để làm component type của biểu thức tạo mảng. |
| `List<String>[] items;` | Có | Khai báo biến mảng khác với cấp phát mảng. |
| `T[] items;` | Có | Khai báo tham chiếu `T[]` vẫn hợp lệ. |

Nếu Java cho phép `new List<String>[1]`, một tham chiếu `Object[]` có thể đưa `List<Integer>` vào đó. Runtime chỉ nhận ra đối tượng `List` mà không đủ thông tin để từ chối `Integer` nằm bên trong; tính an toàn kiểu generic sẽ bị phá vỡ.

**Phân biệt quan trọng:** cấm *biểu thức tạo mảng* với element type không reifiable; không cấm dùng `T[]` làm kiểu biến, tham số, kết quả trả về, hoặc nhận mảng thực do bên gọi cấp phát.

## 4. Heap pollution và hai kiểu lỗi runtime khác nhau

**Heap pollution** xảy ra khi một biến khai báo bằng kiểu parameterized trỏ đến dữ liệu không phù hợp với kiểu tham số mà nó hứa hẹn. Mảng của kiểu generic kết hợp unchecked cast là một cách tạo ra tình huống này:

```java
@SuppressWarnings("unchecked")
static void demonstrateHeapPollution() {
    List<String>[] words = (List<String>[]) new List<?>[1]; // Unchecked cast.
    Object[] alias = words;  // Runtime component type chỉ là List.

    alias[0] = List.of(42);  // Qua kiểm tra mảng: đối tượng là List.
    String value = words[0].get(0); // ClassCastException.
}
```

Điểm cần chú ý:

1. `new List<?>[1]` hợp lệ, vì component type ở runtime là `List`.
2. Cast sang `List<String>[]` không thể được chứng minh hoàn toàn tại runtime; compiler phải cảnh báo `unchecked`.
3. `alias[0] = List.of(42)` vượt qua kiểm tra lưu phần tử, vì đó vẫn là một `List`.
4. Khi lấy phần tử ra theo kỳ vọng `String`, phép cast do compiler chèn vào có thể ném `ClassCastException`.

So sánh: `ArrayStoreException` xảy ra **ngay khi ghi** giá trị sai loại vào mảng có component type runtime không tương thích; `ClassCastException` thường lộ ra **khi đọc/ép kiểu** sau khi thông tin kiểu generic bị phá vỡ. Cả hai lỗi có thể xuất hiện ở những tình huống khác, nhưng phân biệt này đặc biệt hữu ích khi chẩn đoán mảng generic.

## 5. Cách 1 — Lưu bằng `Object[]`, tự kiểm soát biên generic

Đây là cách thường phù hợp để cài đặt **mảng động tổng quát** nếu API chính là `add(T)`, `get(int)` và các thao tác trên từng phần tử, không cần trả về trực tiếp `T[]`. Vùng nhớ có thể chứa tham chiếu đối tượng, còn lớp generic giới hạn đường ghi dữ liệu.

```java
import java.util.Arrays;
import java.util.Objects;

final class DynamicBuffer<T> {
    private Object[] elements = new Object[2];
    private int size;

    public void add(T value) {
        if (size == elements.length) {
            elements = Arrays.copyOf(elements, elements.length * 2);
        }
        elements[size++] = value;
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        Objects.checkIndex(index, size);
        return (T) elements[index];
    }

    public int size() {
        return size;
    }
}
```

Vì sao có thể dùng unchecked cast trong `get()`? Trường `elements` là `private`, chỉ phương thức `add(T)` ghi dữ liệu vào đó; lớp không để bên ngoài sửa mảng qua một tham chiếu alias. Khi ràng buộc được giữ vững, mỗi phần tử đọc trong các vị trí hợp lệ đều được thêm như một `T`. Annotation được đặt **cục bộ** ở phương thức thực sự cần cast; nó tắt cảnh báo compiler nhưng không tự chứng minh an toàn, nên bất biến của lớp phải được kiểm tra khi sửa code.

Khi hết chỗ, `Arrays.copyOf` tạo mảng `Object[]` lớn hơn và sao chép dữ liệu. Một lần mở rộng có chi phí O(n), nhưng nếu tăng dung lượng theo hệ số (ví dụ gấp đôi) thì chi phí thêm một phần tử được phân bổ trung bình là **O(1) amortized**. Phép đọc theo chỉ số là O(1).

Đừng vô tình làm lộ mảng nội bộ:

```java
// Không nên: người gọi có thể ghi thẳng Object sai kiểu.
public Object[] unsafeBackingArray() {
    return elements;
}
```

Cũng **không nên** hứa trả về `T[]` bằng cách ép trực tiếp `Object[]`:

```java
@SuppressWarnings("unchecked")
T[] brokenToArray() {
    return (T[]) new Object[size]; // Có thể thành công bên trong do erasure.
}

// Với Buffer<String>, phép gán kết quả sang String[] có thể
// ném ClassCastException: Object[] thực tế không phải String[].
```

Một kết quả trả về an toàn hơn cho API mảng động là `List<T>`, hoặc tạo mảng đầu ra bằng factory/class token do bên gọi cung cấp.

## 6. Cách 2 — Tạo `T[]` bằng `Array.newInstance` và `Class<T>`

Khi cần **mảng thật sự có component type runtime cụ thể** (như `String[]`), nhận `Class<T>` từ bên gọi, dùng Java Reflection để cấp phát, rồi cast cục bộ:

```java
import java.lang.reflect.Array;

static <T> T[] newTypedArray(Class<T> componentType, int length) {
    @SuppressWarnings("unchecked")
    T[] result = (T[]) Array.newInstance(componentType, length);
    return result;
}

// Sử dụng:
String[] names = newTypedArray(String.class, 3);
Integer[] scores = newTypedArray(Integer.class, 2);
```

Ở đây `Array.newInstance(String.class, 3)` thật sự tạo đối tượng `String[]`. JVM biết kiểu thành phần và tiếp tục bảo vệ phép ghi bằng kiểm tra runtime. Cast sang `T[]` vẫn là unchecked ở cấp generic; tính đúng đắn dựa trên việc ta dùng chính `componentType` do bên gọi cung cấp để tạo mảng.

Cách này hữu ích cho buffer cần trả về typed array:

```java
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;

final class TypedBuffer<T> {
    private T[] elements;
    private int size;

    @SuppressWarnings("unchecked")
    TypedBuffer(Class<T> componentType, int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity < 0");
        }
        Objects.requireNonNull(componentType);
        elements = (T[]) Array.newInstance(componentType, capacity);
    }

    void add(T value) {
        if (size == elements.length) {
            elements = Arrays.copyOf(elements, Math.max(1, elements.length * 2));
        }
        elements[size++] = value;
    }

    T[] snapshot() {
        return Arrays.copyOf(elements, size);
    }
}
```

`snapshot()` trả về một mảng mới có runtime component type phù hợp với mảng nguồn, tránh cho người gọi sửa bộ nhớ đang được `TypedBuffer` sử dụng. Một giới hạn: `String.class` là token cụ thể, còn **không có** `List<String>.class`. `List.class` chỉ mô tả `List` sau erasure nên không thể dùng cách này để kiểm tra phần tử `String` bên trong mỗi `List` tại runtime.

Trong ví dụ trên, `add(null)` hợp lệ với component type tham chiếu; các thao tác sau đó cần tự quy định cách xử lý `null`. Với tham số lớp token, API cũng cần tránh để người gọi sử dụng raw type/unchecked cast làm sai lời hứa kiểu.

## 7. Cách 3 — Nhận `IntFunction<T[]>` (array factory)

Factory giúp bên gọi quyết định chính xác loại mảng, còn lớp generic không phải biết `T.class`:

```java
import java.util.function.IntFunction;

static <T> T[] createArray(IntFunction<T[]> factory, int length) {
    return factory.apply(length);
}

// Method reference tạo mảng:
String[] labels = createArray(String[]::new, 4);
Integer[] ids = createArray(Integer[]::new, 4);

// Có thể dùng lambda:
String[] other = createArray(n -> new String[n], 4);
```

Áp dụng vào mảng động: constructor có thể nhận `IntFunction<T[]> arrayFactory` và gọi nó mỗi khi cần cấp phát. Nhớ lưu factory, giữ phần tử, sao chép dữ liệu khi tăng sức chứa và tránh trả trực tiếp mảng backing. Đây cũng là hướng mà nhiều API thư viện sử dụng khi cần mảng đúng loại từ generic.

So với `Class<T>`, factory linh hoạt hơn khi muốn tách thao tác cấp phát khỏi logic lưu trữ. Tuy vậy, nó **không thay đổi quy tắc erasure**: factory chỉ tạo được mảng theo component type reifiable mà biểu thức khởi tạo cụ thể cho phép.

## 8. Chương trình Java 17+ chạy độc lập

Lưu tệp sau thành `GenericArrayDemo.java`. Chương trình có cả mảng động qua `Object[]`, mảng typed tạo bằng Reflection, factory, và một tình huống heap pollution được bắt ngoại lệ để người học thấy dấu hiệu runtime.

```java
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

public class GenericArrayDemo {
    static final class Buffer<T> {
        private Object[] data = new Object[2];
        private int size;

        void add(T value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = value;
        }

        @SuppressWarnings("unchecked")
        T get(int index) {
            Objects.checkIndex(index, size);
            return (T) data[index];
        }

        int size() {
            return size;
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T[] newTypedArray(Class<T> componentType, int size) {
        return (T[]) Array.newInstance(componentType, size);
    }

    static <T> T[] createWithFactory(IntFunction<T[]> factory, int size) {
        return factory.apply(size);
    }

    @SuppressWarnings("unchecked")
    static void demonstrateHeapPollution() {
        List<String>[] words = (List<String>[]) new List<?>[1];
        Object[] alias = words;
        alias[0] = List.of(42);
        try {
            String s = words[0].get(0);
            System.out.println(s);
        } catch (ClassCastException e) {
            System.out.println("ClassCastException");
        }
    }

    public static void main(String[] args) {
        Buffer<String> buffer = new Buffer<>();
        buffer.add("Java");
        buffer.add("Generics");
        buffer.add("Arrays");
        System.out.println(buffer.size() + ": " + buffer.get(2));

        String[] reflected = newTypedArray(String.class, 2);
        reflected[0] = "reflection";
        System.out.println(reflected.getClass().getComponentType().getSimpleName()
                + ": " + Arrays.toString(reflected));

        String[] factoryArray = createWithFactory(String[]::new, 2);
        factoryArray[0] = "factory";
        System.out.println(Arrays.toString(factoryArray));

        demonstrateHeapPollution();
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac --release 17 -Xlint:unchecked GenericArrayDemo.java
java GenericArrayDemo
```

Stdout chính xác:

```text
3: Arrays
String: [reflection, null]
[factory, null]
ClassCastException
```

Code đã được kiểm tra biên dịch với `--release 17`. Cảnh báo unchecked trong đoạn minh họa heap pollution được khoanh vùng bằng `@SuppressWarnings("unchecked")` để người học chủ động biết vị trí mất đảm bảo kiểu; **không** nên coi annotation này là một cơ chế bảo đảm type safety.

## 9. Liên hệ với `ArrayList<T>` và API thư viện

Trong phần lớn ứng dụng, nên dùng `ArrayList<T>` thay vì tự xây mảng động nếu không có yêu cầu đặc thù về layout, quản lý cấp phát hoặc bài tập cấu trúc dữ liệu:

```java
List<String> names = new java.util.ArrayList<>();
names.add("Ada");
names.add("Grace");
String second = names.get(1);
```

`ArrayList` tự quản lý dung lượng và che giấu cách lưu trữ bên trong. API `List<T>` duy trì kiểm tra kiểu ở compile time cho `add`/`get`; việc sử dụng một cấu trúc lưu trữ kiểu `Object[]` ở tầng cài đặt không làm API trở thành `Object[]`.

Khi cần typed array từ collection, các overload thông dụng gồm:

```java
List<String> names = List.of("Ada", "Grace");

String[] a = names.toArray(String[]::new);     // Java 11+.
String[] b = names.toArray(new String[0]);      // Mẫu lâu đời.
Object[] c = names.toArray();                   // Chỉ Object[].
```

`toArray()` không tham số trả `Object[]`; không được ép mảng đó thành `String[]` bằng unchecked cast rồi kỳ vọng JVM chấp nhận. Dùng typed overload khi cần đúng component type.

## 10. Generic varargs: một trường hợp liên quan

Varargs `T...` về mặt biểu diễn là một tham số mảng `T[]`. Với kiểu phần tử không reifiable, compiler có thể cảnh báo nguy cơ heap pollution tại nơi khai báo và/hoặc gọi:

```java
static <T> void accept(T... values) {
    // values là T[]; tránh ghi sai kiểu hoặc phát tán alias.
}
```

Có thể dùng `@SafeVarargs` khi **đã chứng minh thân phương thức an toàn**: không ghi phần tử không đúng kiểu, không chuyển mảng cho mã không tin cậy, không trả/lưu alias để bị sửa ngoài ý muốn. Ví dụ sao chép dữ liệu ngay vào collection bất biến:

```java
import java.util.Arrays;
import java.util.List;

@SafeVarargs
static <T> List<T> immutableCopy(T... values) {
    return List.copyOf(Arrays.asList(values));
}
```

`List.copyOf` không chấp nhận phần tử `null`. `@SafeVarargs` là một cam kết của tác giả API, không làm cho kiểu `T` trở thành reifiable; sử dụng sai vẫn có thể gây heap pollution. Với Java 17+, annotation áp dụng được cho constructor, phương thức static/final và private instance method theo quy tắc ngôn ngữ.

## 11. Lỗi thường gặp và cách chọn giải pháp

| Tình huống | Vấn đề | Hướng giải quyết |
| --- | --- | --- |
| `new T[n]` | Compile error vì tạo mảng generic | Dùng `Object[]` nội bộ, `Array.newInstance` hoặc array factory. |
| `new List<String>[n]` | Component type không reifiable | Dùng `List<List<String>>` hoặc chỉ `List<?>[]` khi thực sự phù hợp. |
| `(T[]) new Object[n]` rồi trả về `String[]` | Runtime có thể ném `ClassCastException` | Nhận `String[]::new` hoặc `String.class` để tạo đúng kiểu mảng. |
| Lạm dụng `(List<String>[]) new List<?>[n]` | Unchecked cast, có thể heap pollution | Tránh cast; nếu phải dùng, bao kín alias và chứng minh bất biến. |
| Trả backing array cho caller | Phá encapsulation, bên gọi chỉnh dữ liệu trực tiếp | Trả bản sao/collection view thích hợp. |
| Dùng `@SuppressWarnings` toàn bộ lớp | Che giấu vị trí mất bảo đảm kiểu | Thu hẹp annotation vào đúng phép cast và ghi giải thích. |
| Nghĩ `Class<List<String>>` tồn tại như `String.class` | Erasure làm mất đối số kiểu generic | Dùng `List.class` chỉ cho lớp thô; kiểm tra phần tử bằng quy tắc riêng. |
| Nhầm `ArrayStoreException` với `ClassCastException` | Chẩn đoán sai điểm lỗi | Kiểm tra runtime component type, đường ghi mảng, unchecked cast và đường đọc. |

Quy tắc chọn nhanh:

- **Chỉ cần danh sách co giãn:** ưu tiên `ArrayList<T>`.
- **Tự cài đặt mảng động generic với `add/get`:** giữ `Object[]` ở nội bộ, bất biến kiểu rõ ràng, cast cục bộ khi đọc.
- **Cần trả typed array như `String[]`:** nhận `Class<T>` hoặc `IntFunction<T[]>`; tạo mảng có runtime component type chính xác.
- **Phần tử vốn là kiểu generic sâu như `List<String>`:** ưu tiên collection lồng nhau; không kỳ vọng runtime array kiểm tra được đối số kiểu bên trong.

## 12. Tự kiểm tra hiểu bài

1. Tại sao `List<String>[] a;` hợp lệ nhưng `new List<String>[5]` không hợp lệ? Hãy giải thích bằng reifiability và kiểm tra ghi của mảng.
2. Khi `Object[] alias = new String[2]; alias[0] = 5;`, lỗi nào xảy ra và ở thao tác nào? Khi alias là `List<String>[]` bị unchecked cast từ `List<?>[]`, lỗi có thể bộc lộ khác thế nào?
3. Trong `class Box<T>`, vì sao `(T[]) new Object[3]` có thể lọt qua phép cast nội bộ nhưng trả về `String[]` lại lỗi?
4. Hãy bổ sung `remove(int)` cho `DynamicBuffer<T>`: dồn phần tử, giảm `size` và gán `null` vào ô cuối để giải phóng tham chiếu không còn dùng.
5. Viết overload `toArray(IntFunction<T[]> factory)` cho buffer, sao chép đúng `size` phần tử và trả mảng độc lập. Kiểm tra với `String[]::new`.

**Ghi nhớ:** Java generic bảo vệ kiểu chủ yếu lúc biên dịch và dùng erasure; Java array lưu component type thật để kiểm tra lúc chạy. Thiết kế mảng động generic an toàn là quản lý ranh giới giữa hai cơ chế đó.

### Tài liệu chuẩn để tra cứu

- Java Language Specification, §4.6 (*Type Erasure*) và §4.7 (*Reifiable Types*): https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html
- Java Language Specification, §10 (*Arrays*) và §10.5 (*Array Store Exception*): https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html
- Java Tutorials, *Non-Reifiable Types / Heap Pollution / Varargs*: https://docs.oracle.com/javase/tutorial/java/generics/nonReifiableVarargsType.html
- Java API, `java.lang.reflect.Array`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Array.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-arrays-010">Không ép int&#91;&#93; thành Object&#91;&#93;</a>

<details>
<summary>Xem chi tiết</summary>

# Không ép `int[]` thành `Object[]` trong Java Reflection

## Mục tiêu học tập

Sau bài này, bạn có thể giải thích vì sao mảng số nguyên nguyên thủy `int[]` là một đối tượng Java nhưng không phải `Object[]`; nhận biết lỗi tại thời điểm biên dịch và lỗi `ClassCastException` khi ép kiểu; thao tác với mảng chưa biết kiểu phần tử thông qua `java.lang.reflect.Array`; và chủ động chuyển dữ liệu sang `Integer[]` khi một API thực sự yêu cầu mảng đối tượng.

## 1. Hiểu đúng kiểu mảng

Trong Java, **mọi mảng đều là đối tượng**: dù chứa phần tử nguyên thủy hay tham chiếu, mỗi mảng đều có kiểu runtime, thuộc tính `length`, kế thừa các hành vi của `Object` và triển khai `Cloneable`, `Serializable`. Tuy nhiên, “là một `Object`” không đồng nghĩa “là một `Object[]`”.

- `int[]` chứa các giá trị `int` nguyên thủy. Kiểu thành phần (component type) là `int`; các phần tử không phải đối tượng `Integer`.
- `Integer[]`, `String[]` và `Object[]` là **mảng tham chiếu**: mỗi vị trí giữ một tham chiếu đến đối tượng (hoặc `null`).
- `Object[]` chỉ đại diện cho mảng có **kiểu thành phần là kiểu tham chiếu** thích hợp. Nó không đại diện chung cho tất cả các loại mảng.
- `Object` có thể tham chiếu tới cả `int[]`, `long[]`, `String[]`, `Integer[]` và `int[][]`.

| Giá trị runtime | Gán vào `Object` | Gán vào `Object[]` | Kiểu phần tử |
| --- | --- | --- | --- |
| `new int[3]` | Có | Không | `int` nguyên thủy |
| `new Integer[3]` | Có | Có | `Integer` |
| `new String[3]` | Có | Có | `String` |
| `new Object[3]` | Có | Có | `Object` |
| `new int[2][3]` | Có | Có, ở **mảng ngoài** | `int[]` (kiểu tham chiếu) |

Điểm cuối dễ gây nhầm: `int[][]` là mảng của các `int[]`; mảng ngoài giữ **tham chiếu** đến các mảng con. Vì vậy `int[][]` có thể xem là `Object[]`, nhưng từng `int[]` bên trong vẫn **không** phải `Object[]`.

### Sơ đồ kiểu

~~~text
Object
├── int[]              (mảng phần tử nguyên thủy)
├── long[]             (mảng phần tử nguyên thủy)
├── Object[]           (mảng phần tử tham chiếu)
│   ├── String[]
│   ├── Integer[]
│   └── int[][]       (phần tử của mảng ngoài là int[])
└── ...
~~~

Sơ đồ thể hiện quan hệ gán kiểu có ích cho bài này; nó không phải khai báo `extends` viết được trong mã nguồn Java.

## 2. Hai dạng lỗi ép kiểu cần phân biệt

### 2.1. Lỗi biên dịch khi biết trước kiểu là `int[]`

~~~java
int[] numbers = {10, 20, 30};

Object anyArray = numbers;             // Hợp lệ: mọi mảng đều là Object.
Object[] refs = numbers;               // LỖI BIÊN DỊCH.
Object[] casted = (Object[]) numbers;   // VẪN LỖI BIÊN DỊCH.
~~~

Đây là **đoạn cố ý chứa lỗi** để nhận diện kiểu; không đưa nguyên đoạn vào chương trình cần biên dịch. Trình biên dịch biết `numbers` có kiểu tĩnh `int[]` và không có phép chuyển kiểu hợp lệ sang `Object[]`. Thêm phép cast tường minh không thể biến giá trị `int` nguyên thủy thành phần tử tham chiếu.

### 2.2. Biên dịch được qua `Object`, nhưng lỗi lúc chạy

File `ArrayCastPitfall.java`:

~~~java
public class ArrayCastPitfall {
    public static void main(String[] args) {
        int[] numbers = {10, 20, 30};
        Object unknown = numbers;

        System.out.println(unknown instanceof Object);
        System.out.println(unknown instanceof Object[]);
        System.out.println(
                unknown.getClass().getComponentType().getTypeName()
        );

        try {
            Object[] wrong = (Object[]) unknown;
            System.out.println(wrong.length); // Không chạy tới đây.
        } catch (ClassCastException ex) {
            System.out.println(ex.getClass().getSimpleName());
        }
    }
}
~~~

Chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac ArrayCastPitfall.java
java ArrayCastPitfall
~~~

Kết quả chuẩn:

~~~text
true
false
int
ClassCastException
~~~

Biến `unknown` có **kiểu khai báo** là `Object`, nên trình biên dịch cho phép thử cast xuống `Object[]`. Nhưng đối tượng thật được tạo bằng `new int[]{...}`, có kiểu runtime `int[]`; JVM kiểm tra và từ chối cast bằng `ClassCastException`. Một phép cast chỉ kiểm tra/chuyển cách nhìn về kiểu tương thích; **nó không chuyển đổi hoặc boxing từng phần tử**.

Kiểm tra `instanceof Object[]` trước khi cast chỉ giúp tránh ngoại lệ. Nó không khiến `int[]` trở thành mảng tham chiếu.

## 3. Vì sao vấn đề xuất hiện khi dùng Reflection?

Reflection thường xử lý một đối tượng mảng mà **chưa biết trước kiểu phần tử tại thời điểm biên dịch**. Chẳng hạn `Field.get(instance)` có thể trả về `int[]`, `String[]` hoặc kiểu mảng khác dưới dạng `Object`. Một hàm tiện ích nhận `Object array` rồi ép vô điều kiện thành `Object[]` sẽ hỏng ngay khi gặp mảng nguyên thủy.

Không nên viết:

~~~java
// Sai nếu field chứa int[], long[], boolean[] hoặc mảng nguyên thủy khác.
Object value = field.get(instance);
Object[] elements = (Object[]) value;
~~~

Đây là **đoạn minh họa rời**: `field` và `instance` phải được khai báo trong ngữ cảnh Reflection thực tế. Sai lầm nằm ở phép cast, không ở `Field.get`.

Java cung cấp `java.lang.reflect.Array` để thao tác với **cả mảng nguyên thủy và mảng tham chiếu** khi chỉ có biến `Object`:

| Nhu cầu | API thích hợp |
| --- | --- |
| Xác định một đối tượng có phải mảng | `value != null && value.getClass().isArray()` |
| Lấy kiểu thành phần runtime | `value.getClass().getComponentType()` |
| Nhận biết kiểu thành phần nguyên thủy | `componentType.isPrimitive()` |
| Lấy số phần tử | `Array.getLength(value)` |
| Đọc phần tử tại chỉ số `i` | `Array.get(value, i)` |
| Ghi phần tử tương thích | `Array.set(value, i, element)` |
| Tạo mảng theo kiểu phần tử xác định lúc chạy | `Array.newInstance(componentType, length)` |

`Array.get` trả về `Object`; khi đọc phần tử từ `int[]`, giá trị `int` được **boxing thành `Integer`**. Các phương thức chuyên biệt như `Array.getInt` và `Array.setInt` làm việc với giá trị nguyên thủy theo quy tắc tương thích kiểu của chúng, tránh boxing ở vị trí phù hợp. `Array.getLength` và `Array.get` yêu cầu đối số là mảng; truyền `null`, đối tượng thường hoặc chỉ số không hợp lệ sẽ gây ngoại lệ tương ứng.

## 4. Ví dụ đầy đủ: tạo và duyệt mảng động bằng Reflection

“Mảng động” ở đây nghĩa là **tạo/kiểm tra mảng khi kiểu thành phần được quyết định lúc chạy** qua Reflection. Mảng Java sau khi tạo có `length` cố định; nó không tự mở rộng kích thước như `ArrayList`.

File `ReflectiveArrayDemo.java`:

~~~java
import java.lang.reflect.Array;
import java.util.StringJoiner;

public class ReflectiveArrayDemo {
    public static void main(String[] args) {
        // Tạo int[3]; Array.newInstance trả về Object.
        Object ints = Array.newInstance(int.class, 3);
        Array.setInt(ints, 0, 7);
        Array.setInt(ints, 1, 14);
        Array.setInt(ints, 2, 21);
        printArray(ints);

        // Tạo String[2] bằng kiểu thành phần runtime.
        Object words = Array.newInstance(String.class, 2);
        Array.set(words, 0, "Java");
        Array.set(words, 1, "Reflection");
        printArray(words);

        // Chỉ mảng tham chiếu tương thích mới cast sang Object[].
        if (words instanceof Object[] refs) {
            System.out.println(refs[0]);
        }
    }

    private static void printArray(Object array) {
        if (array == null || !array.getClass().isArray()) {
            throw new IllegalArgumentException("Expected an array");
        }

        Class<?> componentType = array.getClass().getComponentType();
        int length = Array.getLength(array);
        StringJoiner joined = new StringJoiner(", ", "[", "]");

        for (int i = 0; i < length; i++) {
            joined.add(String.valueOf(Array.get(array, i)));
        }

        System.out.println(
                componentType.getTypeName() + "[" + length + "] = " + joined
        );
    }
}
~~~

Chạy:

~~~bash
javac ReflectiveArrayDemo.java
java ReflectiveArrayDemo
~~~

Kết quả chuẩn:

~~~text
int[3] = [7, 14, 21]
java.lang.String[2] = [Java, Reflection]
Java
~~~

Giải thích: `Array.newInstance(int.class, 3)` thực sự tạo `int[]`; `Array.newInstance(String.class, 2)` tạo `String[]`. Cả hai được giữ trong biến `Object`, và `printArray` duyệt qua `Array.getLength`/`Array.get` nên không cần ép sang `Object[]`. Với mảng `String[]`, kiểm tra `instanceof Object[]` đúng và cast hợp lệ. Lúc gọi `Array.get` trên `int[]`, các số được boxing phục vụ thao tác generic.

Có thể kiểm tra trước khi tạo hoặc xử lý mảng:

~~~java
Class<?> arrayClass = ints.getClass();
System.out.println(arrayClass.isArray());                       // true
System.out.println(arrayClass.getComponentType().isPrimitive()); // true
~~~

Đây là **đoạn bổ sung** dùng biến `ints` đã khai báo trong `main` ở ví dụ trên.

## 5. Khi API bắt buộc nhận `Object[]`: chuyển đổi thực sự

Nếu cần giao tiếp với API có chữ ký `consume(Object[] values)`, chỉ ép kiểu là không đủ. Phải **tạo mảng tham chiếu mới** rồi boxing từng phần tử, hoặc dùng stream để tạo `Integer[]`.

File `BoxArrayDemo.java`:

~~~java
import java.util.Arrays;

public class BoxArrayDemo {
    public static void main(String[] args) {
        int[] numbers = {1, 2, 3};

        Integer[] boxed = Arrays.stream(numbers)
                .boxed()
                .toArray(Integer[]::new);
        Object[] refs = boxed; // Hợp lệ: Integer[] là mảng tham chiếu.

        System.out.println(Arrays.toString(refs));

        refs[0] = 99;
        System.out.println(numbers[0]); // Mảng int[] gốc không đổi.
        System.out.println(boxed[0]);   // Mảng Integer[] mới đã đổi.

        int[] backToPrimitives = Arrays.stream(boxed)
                .mapToInt(Integer::intValue)
                .toArray();
        System.out.println(Arrays.toString(backToPrimitives));
    }
}
~~~

Chạy:

~~~bash
javac BoxArrayDemo.java
java BoxArrayDemo
~~~

Kết quả chuẩn:

~~~text
[1, 2, 3]
1
99
[99, 2, 3]
~~~

`boxed()` biến mỗi `int` thành đối tượng `Integer`; `toArray(Integer[]::new)` tạo **mảng mới**. Phép gán `Object[] refs = boxed` hợp lệ, nhưng mảng mới không chia sẻ các ô phần tử với `numbers`. Việc boxing có thêm chi phí bộ nhớ và thao tác đối tượng; khi tính toán số học thuần túy, thường nên tiếp tục dùng `int[]` hoặc `IntStream`.

### Chuyển mọi loại mảng thành `Object[]` khi cần

Phương thức dưới đây là **helper đặt bên trong một class**, không phải chương trình độc lập. Nó tạo bản sao một chiều; các phần tử nguyên thủy được boxing:

~~~java
import java.lang.reflect.Array;

static Object[] copyElements(Object source) {
    if (source == null || !source.getClass().isArray()) {
        throw new IllegalArgumentException("Expected an array");
    }

    int length = Array.getLength(source);
    Object[] result = new Object[length];
    for (int i = 0; i < length; i++) {
        result[i] = Array.get(source, i);
    }
    return result;
}
~~~

Ví dụ `copyElements(new int[]{4, 5})` trả về `Object[]` chứa hai `Integer`; `copyElements(new String[]{"a"})` trả về `Object[]` chứa `String`. Đây là **chuyển đổi và sao chép dữ liệu**, không phải cast `int[]`. Với mảng nhiều chiều, hàm chỉ sao chép **tầng ngoài**, không sao chép sâu các mảng con.

Nếu API hỗ trợ `Collection<Integer>`, Java 17 có thể dùng `Arrays.stream(numbers).boxed().toList()`; kết quả là danh sách không thể sửa cấu trúc qua `add`/`set`. Nếu cần tự tăng giảm số phần tử, cân nhắc `ArrayList<Integer>` thay vì trông đợi mảng Java đổi được `length`.

## 6. Bẫy liên quan thường gặp

### 6.1. `Arrays.asList(int[])` không boxing từng số

~~~java
int[] numbers = {1, 2, 3};

java.util.List<int[]> oneElement = java.util.Arrays.asList(numbers);
System.out.println(oneElement.size()); // 1

java.util.List<Integer> eachNumber =
        java.util.Arrays.stream(numbers).boxed().toList();
System.out.println(eachNumber.size()); // 3
~~~

`Arrays.asList` nhận các phần tử có **kiểu tham chiếu** qua varargs kiểu generic. Với `int[]`, nó xem toàn bộ mảng là **một đối tượng phần tử**, chứ không tách thành ba `Integer`. Đoạn trên là minh họa đặt trong một phương thức, không phải một class hoàn chỉnh.

### 6.2. Mảng hai chiều vẫn cần xét từng tầng

~~~java
int[][] matrix = {{1, 2}, {3, 4}};
Object[] outer = matrix; // Hợp lệ: phần tử ở tầng ngoài là int[].

System.out.println(outer.length);                 // 2
System.out.println(outer[0] instanceof Object[]); // false
~~~

Tầng ngoài là mảng tham chiếu; mỗi phần tử bên trong có runtime type `int[]`. Không thể dùng `(Object[]) outer[0]` để đọc các số trong mảng con. Muốn duyệt an toàn, gọi `Array.getLength(outer[0])` và `Array.get(outer[0], i)`, hoặc cast về đúng `int[]` nếu biết chắc kiểu.

### 6.3. Nhầm lẫn giữa covariance và boxing

Trong Java, `String[]` có thể gán cho `Object[]` do **array covariance**. Nhưng nếu gán một đối tượng không phải `String` qua tham chiếu `Object[]` này, JVM ném `ArrayStoreException`. Quy tắc covariance chỉ áp dụng cho **kiểu phần tử tham chiếu**, không biến `int[]` thành `Object[]`; cơ chế này cũng **không tự boxing** nguyên một mảng.

### 6.4. Dùng mảng nguyên thủy chỉ để in dữ liệu

Để in `int[]`, dùng trực tiếp `Arrays.toString(numbers)` vì thư viện đã có overload nhận `int[]`. Không cần tạo `Object[]`, không cần Reflection và không cần boxing cả mảng. `System.out.println(numbers)` tự nó không in danh sách giá trị phần tử.

## 7. Chọn giải pháp đúng theo ngữ cảnh

| Tình huống | Nên dùng | Tránh |
| --- | --- | --- |
| Truyền/giữ bất kỳ mảng nào mà chưa cần duyệt | `Object` | Tuyên bố mọi mảng là `Object[]` |
| Duyệt mảng có component type chỉ biết lúc chạy | `Array.getLength`, `Array.get` | Cast thẳng `(Object[]) array` |
| Biết chắc dữ liệu là mảng số nguyên nguyên thủy | `int[]`, `Arrays.stream(int[])` | Boxing không cần thiết |
| API buộc nhận mảng tham chiếu | `Integer[]` hoặc `Object[]` tạo từ dữ liệu đã boxing | Cast `int[]` |
| Cần danh sách có thể thay đổi kích thước | `ArrayList<Integer>` | Mong `int[]` tự tăng `length` |
| Chỉ cần in nội dung `int[]` | `Arrays.toString(int[])` | Cast để gọi `Arrays.toString(Object[])` |

## 8. Tự kiểm tra

1. Tại sao `Object a = new int[4]` hợp lệ nhưng `Object[] a = new int[4]` không hợp lệ? Vì mọi mảng là đối tượng, còn `Object[]` đòi mảng có phần tử thuộc kiểu tham chiếu.
2. Vì sao `(Object[]) (Object) new int[4]` vẫn thất bại? Kiểu tĩnh trung gian `Object` cho phép thử cast, nhưng kiểu runtime vẫn là `int[]`; JVM ném `ClassCastException`.
3. Muốn đọc giá trị thứ ba từ một `Object x` được xác định là mảng, cần gì? Dùng `Array.getLength(x)` kiểm tra chỉ số rồi `Array.get(x, 2)`; nếu thật sự là `int[]`, có thể cast sang `int[]` và đọc `[2]`.
4. Vì sao `Integer[]` tạo từ `int[]` không phản ánh thay đổi của mảng gốc? Vì đã tạo mảng khác và boxing các giá trị, không có các ô dữ liệu dùng chung.

**Ghi nhớ:** `int[]` là một `Object`, **không phải** `Object[]`. Trong Reflection, làm việc trực tiếp với `java.lang.reflect.Array` khi kiểu phần tử chưa biết; chỉ tạo `Integer[]`/`Object[]` mới khi yêu cầu API thực sự cần kiểu mảng tham chiếu.


</details>

- [Quay lại đầu trang](#back-to-top)
