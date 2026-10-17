<a id="back-to-top"></a>

# Mảng generic

## Menu
- [Đối chiếu mảng kiểm tra kiểu lúc chạy với generic kiểm tra lúc biên dịch](#generic-arrays-001)
- [Vì sao không thể trực tiếp tạo new T&#91;&#93;](#generic-arrays-002)
- [Vì sao không thể trực tiếp tạo new List&lt;String&gt;&#91;&#93;](#generic-arrays-003)
- [Khác biệt giữa khai báo biến mảng generic và biểu thức tạo mảng](#generic-arrays-004)
- [Trường hợp mảng với List&lt;?&gt; có thể tạo hợp lệ](#generic-arrays-005)
- [Dùng Collection&lt;T&gt; thay cho mảng khi phù hợp](#generic-arrays-006)
- [Tạo mảng bằng IntFunction&lt;T&#91;&#93;&gt; hoặc thông tin Class](#generic-arrays-007)
- [Giới hạn và cách khoanh vùng phép ép kiểu khi lưu trữ bằng Object&#91;&#93;](#generic-arrays-008)

## <a id="generic-arrays-001">Đối chiếu mảng kiểm tra kiểu lúc chạy với generic kiểm tra lúc biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu mảng kiểm tra kiểu lúc chạy với generic kiểm tra lúc biên dịch

## Mục tiêu học tập

Trong Java, **mảng (array)** và **generics** đều giúp mô tả kiểu phần tử, nhưng cách chúng bảo vệ tính đúng kiểu khác nhau. Mảng lưu giữ kiểu thành phần tại runtime nên JVM có thể kiểm tra phép ghi trực tiếp. Phần lớn đối số kiểu generic bị xóa (*type erasure*), vì thế compiler kiểm tra các thao tác generic tại thời điểm biên dịch; JVM không có một runtime class riêng cho `List<String>`.

Sau bài này, bạn có thể:

- Phân biệt kiểm tra kiểu **runtime của mảng** với kiểm tra kiểu **compile time của generics**.
- Giải thích vì sao `String[]` có thể gán cho `Object[]` nhưng `List<String>` không thể gán cho `List<Object>`.
- Dự đoán chính xác hai lỗi quan trọng: `ArrayStoreException` khi ghi không đúng loại vào một mảng có component type cụ thể; `ClassCastException` khi đọc một generic collection đã bị nhiễm kiểu.
- Hiểu tại sao Java cấm tạo `new List<String>[10]` và `new T[10]`, nhưng cho phép `new List<?>[10]`.
- Chọn các giải pháp như `List<T>`, array factory `IntFunction<T[]>`, `Class<T>` kết hợp `Array.newInstance` và varargs an toàn.
- Nhận diện **heap pollution**, raw types và cảnh báo unchecked/varargs khi bảo trì mã Java.

Các ví dụ đầy đủ được thiết kế cho **Java 17+**. Chỉ những đoạn được ghi rõ là “chương trình đầy đủ” mới cần lưu thành tệp tương ứng để chạy độc lập; các đoạn mã ngắn khác dùng để giải thích một quy tắc hoặc lỗi biên dịch.

## 1. Hai cơ chế nhìn giống nhau nhưng khác bản chất

Hãy bắt đầu với hai loại cấu trúc chứa chuỗi:

```java
String[] names = new String[2];
java.util.List<String> labels = new java.util.ArrayList<>();
```

Ở Java source, cả hai đều gợi ý rằng phần tử phải là `String`. Tuy nhiên, cách Java hiện thực cam kết khác nhau:

| Câu hỏi | `String[]` | `List<String>` |
| --- | --- | --- |
| Kiểu phần tử có tồn tại trong runtime class không? | **Có:** component type là `String` | **Không** có runtime class riêng `List<String>` |
| Nơi kiểm tra chính | Compiler **và** JVM khi thực hiện phép ghi mảng | Chủ yếu compiler qua đối số generic |
| Nếu “lách” bằng tham chiếu rộng hơn? | `Object[]` có thể trỏ tới `String[]`; JVM vẫn kiểm tra khi ghi | Raw `List` có thể chèn phần tử sai với cảnh báo unchecked |
| Lỗi đặc trưng | `ArrayStoreException` tại phép ghi sai | `ClassCastException` khi một giá trị sai bị ép về kiểu kỳ vọng |
| Có covariant không? | Có: `String[]` là subtype của `Object[]` | Không: `List<String>` **không** là subtype của `List<Object>` |
| Tạo trực tiếp | `new String[2]` hợp lệ | `new ArrayList<String>()` hoặc `new ArrayList<>()` hợp lệ |

Từ khóa quan trọng:

- **Reifiable type:** kiểu có thể được thể hiện đầy đủ ở runtime theo quy tắc của ngôn ngữ. Kiểu mảng có component reifiable, chẳng hạn `String[]`, có đủ thông tin để kiểm tra phép ghi.
- **Non-reifiable parameterized type:** kiểu như `List<String>` chứa đối số generic không có runtime representation tương ứng để JVM phân biệt mọi `String` trong danh sách.
- **Type erasure:** cơ chế biên dịch các khai báo/tham chiếu generic thành descriptor phù hợp với JVM, đồng thời compiler có thể thêm cast ở những vị trí cần thiết.

Lưu ý: `String[]` cũng được compiler kiểm tra kiểu tại source; không nên phát biểu “mảng *chỉ* kiểm tra runtime”. Điểm khác biệt là **mảng có thêm kiểm tra cửa ghi ở JVM dựa trên component type thực**, kể cả khi một tham chiếu `Object[]` làm che đi loại phần tử ở source.

## 2. Mảng có runtime component type

Xem đoạn minh họa:

```java
String[] names = {"Lan", "Minh"};
Object[] asObjects = names; // Hợp lệ: mảng là covariant.
```

Cả hai biến đang tham chiếu **cùng một đối tượng mảng**. Vì đối tượng được tạo là `String[]`, runtime class của nó vẫn là `String[].class` và component type vẫn là `String.class`, dù biến `asObjects` có kiểu source là `Object[]`.

Một lời gọi hợp lệ:

```java
asObjects[0] = "Hoa";
```

Một lời gọi bị JVM từ chối tại lúc ghi:

```java
// Biên dịch được vì asObjects có kiểu source Object[].
// Khi chạy với đối tượng mảng thực String[], dòng sau ném ArrayStoreException:
asObjects[0] = Integer.valueOf(42);
```

Vì `Integer` không thể lưu vào mảng có component type thực là `String`, JVM phát sinh **`ArrayStoreException`** ngay tại biểu thức gán mảng. Giá trị cũ tại vị trí đó không bị thay bởi đối tượng sai kiểu.

Chính tính **covariance** của mảng tạo ra tình huống compiler không thể kết luận tại source: phép ghi `Object` phù hợp với biến `Object[]`, nhưng runtime component type của đối tượng có thể hẹp hơn. Java cho phép quan hệ subtype mảng này vì lý do lịch sử; kiểm tra store runtime bù lại sự thiếu chặt chẽ đó.

Ngược lại, với một biến khai báo trực tiếp `String[]`:

```java
String[] direct = new String[1];
// direct[0] = 42; // Lỗi biên dịch.
```

Compiler đã thấy ngay đối số không đúng kiểu, nên không cần đợi phép kiểm tra store của JVM cho trường hợp này.

## 3. Generics không giữ một runtime `List<String>` chuyên biệt

Xem:

```java
java.util.List<String> strings = new java.util.ArrayList<>();
java.util.List<Integer> numbers = new java.util.ArrayList<>();
```

Nếu tạo trực tiếp hai `ArrayList` trên, `strings.getClass() == numbers.getClass()` là `true`: cùng runtime class `ArrayList`. Đối số kiểu `String` và `Integer` là thông tin quan trọng cho compiler tại mã nguồn, nhưng không tạo hai runtime class tương ứng.

Với biến typed, Java từ chối phép thêm sai:

```java
strings.add("Java"); // Hợp lệ.
// strings.add(42); // Lỗi biên dịch: Integer không phù hợp String.
```

Thế còn khi một tham chiếu raw được sử dụng?

```java
java.util.List raw = strings;
raw.add(42); // Compiler cảnh báo unchecked.
```

Lời gọi có thể chạy và đưa một `Integer` vào chính danh sách mà `strings` trỏ đến. Vì `ArrayList` không có runtime component type `String` để kiểm tra trong phương thức `add(Object)`, nó không tự ném `ArrayStoreException` như mảng.

Lỗi thường xảy ra khi mã typed đọc dữ liệu:

```java
String value = strings.get(1); // Có thể ClassCastException.
```

Ở source, compiler hiểu `List<String>.get(...)` cho `String`. Nhưng bytecode của `List.get(int)` trả `Object` sau erasure; compiler có thể chèn **`checkcast java/lang/String`** tại điểm sử dụng. Nếu giá trị thật là `Integer`, phép cast thất bại và ném `ClassCastException`.

Đó là một ví dụ **heap pollution**: trạng thái dữ liệu trong một đối tượng collection không phù hợp với kiểu tham số hóa mà code tin tưởng. Java generics bảo vệ tốt khi dùng API typed đúng cách; raw types và unchecked operations có thể phá bảo đảm đó.

## 4. Chương trình A — So sánh hai loại lỗi trên cùng một lần chạy

Tạo tệp **`ArraysGenericsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public final class ArraysGenericsDemo {
    public static void main(String[] args) {
        String[] names = {"Lan", "Minh"};
        Object[] asObjects = names; // covariance
        System.out.println("array component: "
                + asObjects.getClass().getComponentType().getSimpleName());
        System.out.println("same array: " + (asObjects == names));

        try {
            asObjects[0] = 42;
        } catch (ArrayStoreException ex) {
            System.out.println("array write: "
                    + ex.getClass().getSimpleName());
        }
        System.out.println("array[0]: " + names[0]);

        List<String> strings = new ArrayList<>(List.of("Java"));
        List<Integer> numbers = new ArrayList<>(List.of(7));
        System.out.println("same list class: "
                + (strings.getClass() == numbers.getClass()));
        // List<Object> objects = strings; // Compile-time error: invariant

        List raw = strings; // intentionally unsafe
        raw.add(42);        // unchecked
        System.out.println("list contents: " + strings);

        try {
            String second = strings.get(1);
            System.out.println(second);
        } catch (ClassCastException ex) {
            System.out.println("generic read: "
                    + ex.getClass().getSimpleName());
        }

        List<?>[] wildcardArray = new List<?>[1];
        wildcardArray[0] = List.of("safe", 5);
        System.out.println("wildcard read: " + wildcardArray[0].get(0));
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:all ArraysGenericsDemo.java
java ArraysGenericsDemo
```

**Stdout đã kiểm chứng:**

```text
array component: String
same array: true
array write: ArrayStoreException
array[0]: Lan
same list class: true
list contents: [Java, 42]
generic read: ClassCastException
wildcard read: safe
```

Lệnh `javac -Xlint:all` đã phát cảnh báo `[rawtypes]` cho `List raw` và `[unchecked]` cho `raw.add(42)`. Đây là **diagnostics của compiler**, không thuộc stdout của lệnh `java ArraysGenericsDemo`.

Giải thích các dòng:

1. `array component: String`: component type có thể truy cập qua `getClass().getComponentType()`, chứng minh runtime biết đây là mảng `String[]`.
2. `same array: true`: phép gán `Object[] asObjects = names` **không** tạo bản sao; mọi phép ghi đều ảnh hưởng cùng đối tượng.
3. `array write: ArrayStoreException`: JVM từ chối giá trị sai trước khi lưu; vì thế `array[0]` vẫn là `Lan`.
4. `same list class: true`: hai `ArrayList` với đối số generic khác nhau dùng một class runtime.
5. `list contents: [Java, 42]`: raw type đã bỏ qua rào chắn compile-time của `List<String>`, trong khi chính đối tượng `ArrayList` không tự phân biệt generic argument.
6. `generic read: ClassCastException`: lỗi xuất hiện khi giá trị thực được sử dụng như một `String`.
7. `wildcard read: safe`: mảng của `List<?>` được phép tạo trực tiếp; phần sau bài giải thích vì sao.

**Kết luận tạm thời:** Phép ghi sai vào mảng bị từ chối ngay ở runtime. Trong ví dụ generic bị phá qua raw type, việc thêm sai đã thành công và lỗi bị phát hiện muộn ở lúc đọc.

## 5. Vì sao mảng là covariant còn generic thường invariant?

**Array covariance**:

```java
String[] strings = new String[2];
Object[] objects = strings; // Hợp lệ.
```

Nếu `S` là kiểu tham chiếu subtype của `T`, thì `S[]` cũng là subtype của `T[]` theo quy tắc covariance mảng của Java. Quan hệ này cho phép một tham chiếu rộng hơn nhìn vào mảng hẹp, nhưng buộc JVM kiểm tra store runtime.

**Generic invariance**:

```java
List<String> strings = new ArrayList<>();
// List<Object> objects = strings; // Không hợp lệ.
```

`String` là subtype của `Object`, nhưng `List<String>` **không** là subtype của `List<Object>`. Nếu Java cho phép phép gán ấy, lời gọi `objects.add(42)` sẽ làm hỏng danh sách chuỗi mà không có runtime generic component type tương ứng để chặn ngay.

Nếu muốn đọc danh sách nhiều kiểu khác nhau, dùng wildcard:

```java
List<?> unknown = strings;
Object first = unknown.isEmpty() ? null : unknown.get(0);
// unknown.add("new"); // Không biên dịch: không biết kiểu phần tử.
```

`List<?>` vẫn là một kiểu tham số hóa có ràng buộc; **không phải raw type**. Vì kiểu phần tử chưa biết, compiler không cho thêm giá trị không phải `null` qua tham chiếu wildcard.

Đối với API cụ thể, wildcard có thể diễn tả phương hướng dữ liệu:

```java
static double sum(List<? extends Number> source) {
    double total = 0;
    for (Number n : source) {
        total += n.doubleValue();
    }
    return total;
}
```

Đoạn này là phương thức minh họa cần được đặt trong lớp có import `java.util.List`. Nó chấp nhận `List<Integer>` hoặc `List<Double>` để đọc `Number`, nhưng không cho phép tùy ý thêm một `Number` vì kiểu phần tử thực có thể hẹp hơn.

Không suy ra rằng “generic không thể đa hình”. **Invariance** nói về mối quan hệ giữa các tham số hóa `List<S>` và `List<T>`; wildcard `? extends`/`? super` cho phép biểu diễn quan hệ linh hoạt và an toàn khi cần.

## 6. Reification và giới hạn tạo generic arrays

Biểu thức tạo array `new ComponentType[n]` đòi **component type reifiable**, tức thông tin kiểu thích hợp phải có thể được runtime biểu diễn để JVM kiểm tra việc ghi. `String`, raw `List` và parameterized `List<?>` (mọi type arguments là wildcard `?` không giới hạn) đáp ứng yêu cầu; `List<String>`, `List<? extends Number>` và type variable `T` thì không.

```java
String[] words = new String[3];            // Hợp lệ.
List<?>[] lists = new List<?>[3];          // Hợp lệ.
// List<String>[] bad = new List<String>[3]; // Lỗi generic array creation.
// T[] bad = new T[3];                       // Lỗi khi T là type variable.
```

Một phần tử của `List<?>[]` có thể là `List<String>` hoặc `List<Integer>`: runtime component type chỉ là `List`, không phải một `List<String>` có thể xác minh từng phần tử. Việc cho phép trực tiếp `new List<String>[n]` sẽ tạo cam kết kiểu mà array store check không thể giữ khi người dùng ghi qua `Object[]` alias. **Không nhầm** `List<?>[]` với `List<Object>[]`, cũng không dùng unchecked cast để giả lập mảng các list chuỗi.

Quy tắc reifiable type, mảng chứa list tham số hóa và heap pollution được phân tích bằng ví dụ chạy riêng trong các bài về `new List<String>[]` và `List<?>[]`.

## 7. Khai báo `T[]` không phải cấp phát `new T[n]`

```java
class Holder<T> {
    private final T[] elements;
    Holder(T[] elements) { this.elements = elements; }
    T first() { return elements[0]; }
}
```

`Holder<String>` có thể **nhận** `new String[]{"A"}` từ caller, rồi đọc `first()` như `String`. Nhưng bên trong `Holder<T>` không thể viết `new T[n]`: `T` là type variable, không có runtime component class để tạo mảng đúng cho mọi cách tham số hóa. Kể cả `T extends Number`, mảng `Number[]` cũng không đương nhiên là `Integer[]`.

Đừng sửa bằng `(T[]) new Object[n]`: mảng thật vẫn có runtime class `Object[]`, nên caller yêu cầu `String[]` có thể gặp `ClassCastException`. Muốn tạo mảng đúng component type, hãy nhận một factory hoặc `Class<T>` đáng tin; chương trình kế tiếp minh họa hai cách, và bài chuyên sâu về `new T[]` giải thích giới hạn bytecode/erasure.

## 8. Chương trình B — Tạo typed array an toàn bằng factory hoặc `Class<T>`

Tạo tệp **`TypedArrayDemo.java`**:

```java
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

public final class TypedArrayDemo {
    static <T> T[] withFactory(IntFunction<T[]> factory, int n) {
        T[] result = factory.apply(n);
        return result;
    }

    static <T> T[] withClass(Class<T> elementType, int n) {
        Objects.requireNonNull(elementType);
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(elementType, n);
        return result;
    }

    @SafeVarargs
    static <T> List<T> concat(List<? extends T>... groups) {
        List<T> result = new ArrayList<>();
        for (List<? extends T> group : groups) {
            result.addAll(group);
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        String[] words = withFactory(String[]::new, 2);
        words[0] = "A";
        words[1] = "B";
        System.out.println("factory: " + Arrays.toString(words));

        Integer[] ids = withClass(Integer.class, 2);
        ids[0] = 10;
        System.out.println("reflection component: "
                + ids.getClass().getComponentType().getSimpleName());

        try {
            Object[] view = ids;
            view[1] = "wrong";
        } catch (ArrayStoreException ex) {
            System.out.println("reflection store: "
                    + ex.getClass().getSimpleName());
        }

        System.out.println("concat: "
                + concat(List.of("x"), List.of("y")));
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:all TypedArrayDemo.java
java TypedArrayDemo
```

**Stdout đã kiểm chứng:**

```text
factory: [A, B]
reflection component: Integer
reflection store: ArrayStoreException
concat: [x, y]
```

### Cách 1 — Factory `IntFunction<T[]>`

`String[]::new` là một **array constructor reference**: khi nhận độ dài `2`, nó tạo `String[2]` thực, không phải `Object[2]` được gán nhãn generic. Phương thức `withFactory` chỉ yêu cầu factory tạo đúng loại mảng mà caller cung cấp.

Ưu điểm: tránh phải cast `Object[]` thủ công và diễn tả rõ trách nhiệm tạo array. Với API dùng nhiều loại `T`, một factory thích hợp thường là cách giữ code generic mà vẫn bảo toàn component type tại runtime.

### Cách 2 — `Class<T>` với `Array.newInstance`

`Integer.class` được truyền vào tường minh, vì vậy reflection có đủ thông tin để cấp phát đối tượng mảng với component type là `Integer`. Kết quả thực sự là `Integer[]`, được chứng minh bởi dòng:

```text
reflection component: Integer
```

Trong `withClass`, phép cast `(T[])` được đặt trong phạm vi nhỏ có `@SuppressWarnings("unchecked")`. Do erasure, compiler không thể kiểm tra tổng quát cast từ `Object` trả bởi `Array.newInstance`; tuy nhiên ở ví dụ này việc truyền **`Class<T>` đúng kiểu**, không cho bên khác tùy tiện thay token, tạo căn cứ rằng mảng được cấp phát theo component type tương ứng.

`Class<T>` là token runtime cho lớp cụ thể, không tự khôi phục kiểu lồng nhau `List<String>` từ `List.class`. Tránh sử dụng một `Class` raw không có hợp đồng rồi tuyên bố rằng `List<String>[]` sẽ được kiểm tra đầy đủ.

### Mảng tạo đúng kiểu vẫn kiểm tra store ở runtime

Dù được tạo qua factory hay reflection, đối tượng `Integer[]` thực vẫn mang component type `Integer`. Phép viết một `String` qua biến `Object[]` rộng hơn lập tức bị từ chối bằng `ArrayStoreException`. Đây là kiểm chứng rằng cách cấp phát giữ đúng loại **mảng thực**, không chỉ giữ một chú thích generic ở source.

## 9. Generic method `T[]` trong bytecode: một chi tiết nâng cao

Bạn có thể tự xem phần chữ ký của `TypedArrayDemo` đã biên dịch:

```bash
javap -p -s TypedArrayDemo
```

Trích descriptor đã xác minh:

```text
static <T> T[] withFactory(java.util.function.IntFunction<T[]>, int);
  descriptor: (Ljava/util/function/IntFunction;I)[Ljava/lang/Object;

static <T> T[] withClass(java.lang.Class<T>, int);
  descriptor: (Ljava/lang/Class;I)[Ljava/lang/Object;
```

`[Ljava/lang/Object;` là descriptor của **`Object[]`**, tức kiểu erasure của `T[]` khi `T` không có giới hạn. Điều này **không mâu thuẫn** với việc `withFactory(String[]::new, 2)` trả một đối tượng thực là `String[]`: erased return descriptor của method và runtime class của đối tượng được trả là hai khía cạnh khác nhau.

Trình biên dịch có thể thêm `checkcast [Ljava/lang/String;` ở điểm gọi cần gán kết quả thành `String[]`. Vì factory/token tạo đúng component type, cast ấy thành công trong chương trình hợp lệ của ví dụ.

Nếu bạn thay factory bằng code luôn tạo `Object[]` rồi cast về `T[]`, phần source có thể trông giống nhau nhưng runtime class của kết quả không còn bảo đảm đúng kiểu, khiến cast ở caller có nguy cơ thất bại.

## 10. Chương trình C — Vì sao unchecked cast với mảng generic nguy hiểm

Đoạn thử nghiệm sau cố ý tạo heap pollution mà `ArrayStoreException` **không ngăn được**, vì component type runtime của mảng chỉ là `List`.

Tạo tệp **`UnsafeGenericArrayDemo.java`**:

```java
import java.util.List;

public final class UnsafeGenericArrayDemo {
    public static void main(String[] args) {
        List<?>[] backing = new List<?>[1];

        @SuppressWarnings("unchecked")
        List<String>[] strings = (List<String>[]) backing;

        Object[] objects = strings;
        objects[0] = List.of(123);

        System.out.println("array component: "
                + objects.getClass().getComponentType().getSimpleName());

        try {
            String value = strings[0].get(0);
            System.out.println(value);
        } catch (ClassCastException ex) {
            System.out.println("polluted read: "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -Xlint:all UnsafeGenericArrayDemo.java
java UnsafeGenericArrayDemo
```

**Stdout đã kiểm chứng:**

```text
array component: List
polluted read: ClassCastException
```

Các bước phân tích:

1. `new List<?>[1]` hợp lệ vì `List<?>` là reifiable; mảng thực có component type `List`.
2. Cast `(List<String>[]) backing` mang tính **unchecked** ở cấp source. Nó không xác thực rằng mỗi danh sách bên trong chỉ chứa `String`. Suppression trong ví dụ chỉ làm rõ vị trí thiếu bằng chứng, **không biến cast thành an toàn**.
3. Biến `Object[] objects` vẫn tham chiếu cùng mảng; `objects[0] = List.of(123)` được JVM cho phép vì đối tượng đưa vào là một `List`, phù hợp với component type runtime của array.
4. `strings[0].get(0)` được compiler xem như trả `String`, nhưng giá trị thực là `Integer`; `ClassCastException` xảy ra khi đọc/cast.

**Bài học:** Mảng kiểm tra được **loại đối tượng ở mỗi ô của mảng** (ở đây là `List`), không kiểm tra được **đối số generic bị xóa** của đối tượng ấy (`String` hay `Integer`). Vì vậy, dù mảng có store check runtime, nó vẫn không cứu được những cam kết generic được dựng lên bằng unchecked cast.

Không nên dùng đoạn thử nghiệm này làm thiết kế data structure trong sản phẩm. Nếu cần nhiều nhóm `List<String>`, một lựa chọn tự nhiên hơn là `List<List<String>>` với các thao tác typed.

## 11. Generic varargs cũng dùng array ở runtime

`List<T>... groups` là varargs có backing array mang component type generic thường **non-reifiable**. Caller/compiler có thể phải dùng array sau erasure và phát cảnh báo unchecked; nếu method gán một `List<Integer>` vào mảng bị người dùng xem là `List<String>[]`, một lần đọc `String` về sau có thể gây `ClassCastException`.

Trên Java 17, `@SafeVarargs` được phép ở constructor và method đủ điều kiện (static, final, private instance method). Annotation này **không** làm `List<T>` reifiable và không tự thêm runtime validation: người viết chịu trách nhiệm không ghi sai component, không để array varargs thoát ra vùng có thể mutate không kiểm soát.

```java
@SafeVarargs
static <T> List<T> concat(List<? extends T>... groups) {
    List<T> result = new ArrayList<>();
    for (List<? extends T> group : groups) {
        result.addAll(group);
    }
    return List.copyOf(result);
}
```

Ví dụ `concat` chỉ đọc các list, không sửa/lộ mảng varargs, trả collection mới; vì vậy có cơ sở cho annotation. Nếu API chỉ cần duyệt các phần tử, nhận `Collection<? extends T>` thường đơn giản hơn. Đây là một **giao điểm bổ sung** của mảng và generics, không phải đường vòng để hợp thức hóa `new T[n]` hay `new List<String>[n]`.

## 12. Runtime class và khả năng quan sát kiểu

Có một số câu hỏi riêng biệt thường bị trộn lẫn:

| Câu hỏi | Cách quan sát | Kết quả |
| --- | --- | --- |
| Mảng `String[]` thực có component type gì? | `array.getClass().getComponentType()` | `String.class` |
| Hai `ArrayList` với type arguments khác nhau có cùng runtime class không? | `list1.getClass() == list2.getClass()` | Có, nếu đều tạo trực tiếp từ cùng class `ArrayList` trong cùng ngữ cảnh class loading |
| Đối tượng nằm trong `List<String>` có kiểu thực gì? | `list.get(i).getClass()` khi phần tử khác `null` | Lớp thực của **giá trị**, không xác nhận đối số generic của mọi phần tử |
| JVM biết `ArrayList<String>` là subclass riêng không? | `list.getClass()` | Không có lớp runtime riêng cho cách tham số hóa ấy |
| `List<?>[]` kiểm tra gì khi ghi? | Runtime array store | Phần tử phải là `List` tương thích, không kiểm tra các type arguments bên trong |

Reflection có thể đọc **metadata generic trên khai báo** của lớp, field hoặc method (ví dụ `Field.getGenericType()`). Điều đó khác với việc runtime có khả năng kiểm tra toàn bộ tham số `String` trong một đối tượng `ArrayList<String>` cụ thể ở lúc gọi `add(Object)`. Không suy luận kiểu phần tử chỉ từ `getClass()` của collection.

Một chi tiết khác: `int[]` là một mảng primitive. `int[]` **không phải subtype của `Object[]`**, dù bản thân `int[]` là một `Object` và có runtime class riêng. Array covariance như ví dụ `String[] -> Object[]` áp dụng cho **mảng chứa kiểu tham chiếu** có quan hệ subtype phù hợp.

## 13. Trường hợp nào dùng array, trường hợp nào dùng collection generic?

Mảng và generic collection có ưu nhược điểm khác nhau; không nên tuyệt đối hóa một lựa chọn.

**Array thích hợp khi:**

- Số phần tử được tổ chức theo chỉ số và thường biết hoặc cố định độ dài.
- Cần giao tiếp với API yêu cầu array, đặc biệt các API cũ hoặc các giao diện cấp thấp.
- Cần mảng primitive như `int[]`, `double[]` để tránh boxing phần tử.
- Có component type cụ thể và muốn tận dụng store check runtime.

**Generic collection thích hợp khi:**

- Số phần tử thay đổi theo hoạt động chương trình.
- Cần các thao tác `add`, `remove`, traversal, transformation linh hoạt.
- Muốn mô hình hóa container lồng nhau như `List<List<String>>` mà không tạo generic arrays không an toàn.
- Muốn dùng wildcard để diễn đạt quan hệ kiểu giữa producer/consumer.

Một API có thể kết hợp cả hai. Ví dụ, hàm nhận `Collection<? extends T>` rồi trả `T[]` có thể yêu cầu một array factory hoặc type token. Quan trọng là **runtime component type của mảng trả về phải phù hợp với kiểu được hứa**.

Tránh truyền raw collection xuyên nhiều tầng ứng dụng rồi ép về generic ở cuối. Nếu phải tương tác với API legacy, kiểm tra dữ liệu tại ranh giới và chuyển về kiểu typed càng sớm càng tốt.

## 14. Những lỗi hiểu sai cần loại bỏ

**Sai 1 — “Mảng chỉ kiểm tra kiểu lúc chạy.”**

Mảng cũng được compiler kiểm tra với khai báo như `String[]`. Khác biệt là JVM biết runtime component type và có thêm kiểm tra store, cần thiết do covariance.

**Sai 2 — “Generics không an toàn vì bị xóa kiểu.”**

Trong chương trình dùng typed API đúng cách, compiler ngăn các phép ghi không phù hợp. Lỗi muộn trong ví dụ xuất hiện vì code **chủ động** dùng raw type/unchecked cast và vi phạm hợp đồng.

**Sai 3 — “`List<String>` là một subtype của `List<Object>` vì `String extends Object`.”**

Java generics invariant theo mặc định. Nếu API cần đọc nhiều loại list, dùng `List<?>` hoặc wildcard phù hợp.

**Sai 4 — “Dùng `new Object[n]` rồi cast là tạo được `T[]` thật.”**

Mảng thực vẫn là `Object[]`; phép cast ở caller khi yêu cầu `String[]` có thể thất bại. Dùng `IntFunction<T[]>` hoặc `Class<T>` để tạo đúng runtime component type khi cần.

**Sai 5 — “`new List<?>[n]` là bất hợp lệ giống `new List<String>[n]`.”**

`List<?>` là trường hợp reifiable với unbounded wildcard, nên có thể làm component type của array được tạo. `List<String>` không thỏa điều kiện đó.

**Sai 6 — “ArrayStoreException bảo đảm mọi danh sách trong mảng có đúng type arguments.”**

Runtime array kiểm tra object từng slot có phải `List` hay không. Nó không kiểm tra được `List<Integer>` khác `List<String>` ở runtime chỉ bằng array store check.

**Sai 7 — “Mọi `ClassCastException` trong generics đều xảy ra ngay khi `add`.”**

Sai kiểu có thể được thêm qua raw reference. Lỗi thường phát sinh khi code typed đọc phần tử và cast được compiler chèn để dùng dưới dạng `String`.

**Sai 8 — “`@SafeVarargs` sửa vấn đề heap pollution một cách tự động.”**

Annotation không thay đổi bytecode theo hướng bổ sung runtime component type cho generic. Nó là cam kết về tính an toàn của cách dùng varargs và cần được kiểm tra bằng lập luận/kiểm thử.

## 15. Thực hành bổ sung và đáp án

### Bài 1 — Dự đoán thời điểm lỗi

```java
String[] data = {"A"};
Object[] ref = data;
ref[0] = 9;
```

**Đáp án:** code biên dịch được, nhưng JVM ném `ArrayStoreException` tại dòng `ref[0] = 9`. `Integer` không thuộc `String[]`; phần tử cũ vẫn được giữ.

### Bài 2 — Vì sao phép gán generic không hợp lệ?

```java
List<String> a = new ArrayList<>();
// List<Object> b = a;
```

**Đáp án:** `List<String>` và `List<Object>` invariant. Cho phép sẽ tạo đường ghi `Object` không phải `String` vào danh sách chuỗi, trong khi runtime không lưu trữ generic argument để chặn như `String[]`.

### Bài 3 — Hai lỗi khác nhau

Sau khi `List raw = typedList`, code gọi `raw.add(100)`, rồi `String s = typedList.get(0)`. Lỗi có thể xuất hiện ở đâu?

**Đáp án:** compiler cảnh báo unchecked ở lời gọi raw `add`. Nếu phần tử được đọc thật là `Integer`, việc dùng nó làm `String` gây `ClassCastException`. Raw `ArrayList` không bảo đảm ném `ArrayStoreException` khi thêm giá trị.

### Bài 4 — Kiểm tra generic array creation

Trong các biểu thức sau, biểu thức nào hợp lệ?

```java
new String[3];
new List<?>[3];
// new List<String>[3];
// new T[3]; // T là type variable.
```

**Đáp án:** hai biểu thức đầu hợp lệ; hai biểu thức sau không thể dùng trực tiếp để tạo mảng trong Java source.

### Bài 5 — Phân tích một cast

Vì sao `(T[]) new Object[n]` không tương đương `String[]::new` khi `T = String`?

**Đáp án:** bản đầu tạo mảng thực `Object[]` rồi dùng unchecked cast, còn bản thứ hai tạo trực tiếp `String[]`. Runtime component type là khác nhau; `String[]` yêu cầu array thật có thành phần tương thích và `Object[]` không đáp ứng yêu cầu đó.

### Bài 6 — Thiết kế API

Bạn cần phương thức tạo một mảng của kiểu phần tử bất kỳ. Hãy đưa ra hai lựa chọn chữ ký:

```java
static <T> T[] create(java.util.function.IntFunction<T[]> factory, int size)
static <T> T[] create(Class<T> type, int size)
```

**Đáp án định hướng:** biến thể factory gọi `factory.apply(size)` (ví dụ `String[]::new`); biến thể token dùng `Array.newInstance(type, size)` và một cast được giới hạn cẩn thận. Hãy tự kiểm tra component type của mảng được trả về bằng reflection.

### Bài 7 — Vì sao mảng `List<?>[]` vẫn có thể chứa danh sách số?

```java
List<?>[] groups = new List<?>[1];
groups[0] = List.of(1, 2);
```

**Đáp án:** runtime array cần phần tử là `List`, còn wildcard không hứa loại phần tử cụ thể. Giá trị khi lấy ra được nhìn như `Object` nếu chưa kiểm tra thêm.

### Bài 8 — Chú thích varargs

Một generic varargs method có thể thêm các `List<Integer>` vào mảng varargs được caller coi là các `List<String>`, rồi vẫn gắn `@SafeVarargs` không?

**Đáp án:** không nên; thao tác đó gây heap pollution, phá lời cam kết an toàn mà annotation hàm ý. Chỉ dùng `@SafeVarargs` cho một cách hiện thực thực sự không gây các thao tác nguy hiểm trên varargs array.

## 16. Checklist tự đánh giá

Bạn có thể coi bài này hoàn thành khi giải thích được:

1. Mảng chứa kiểu tham chiếu có **covariance**, compiler vẫn kiểm tra kiểu, và JVM kiểm tra store theo runtime component type.
2. `List<String>` **invariant**, và generic arguments không tạo runtime class `ArrayList<String>` riêng.
3. `ArrayStoreException` khác `ClassCastException` ở vị trí phát hiện cũng như điều kiện gây lỗi.
4. `new List<String>[n]` và `new T[n]` không hợp lệ, còn `new List<?>[n]` hợp lệ.
5. `T[]` có thể làm kiểu trả về/field/parameter; nhưng muốn cấp phát đúng mảng runtime thì phải biết cách tạo component type.
6. Array factory hoặc `Class<T>` giúp tạo mảng đúng loại thực, thay vì dựa vào unchecked cast từ `Object[]`.
7. Generic varargs là một nơi cần thận trọng với heap pollution và `@SafeVarargs` là lời cam kết từ người viết.
8. Không coi phản ánh `getClass()` của collection là bằng chứng về type arguments của danh sách.

## Kết luận

**Mảng Java giữ runtime component type và kiểm tra phép ghi**, nên một `String[]` nhìn qua `Object[]` vẫn từ chối `Integer` bằng `ArrayStoreException`. **Generics kiểm tra kiểu chủ yếu ở compile time và bị type erasure**, nên `List<String>` không có “hàng rào store” runtime tương đương; raw type/unchecked cast có thể gây heap pollution, rồi `ClassCastException` khi đọc. Chính sự khác nhau ấy giải thích vì sao Java giữ array covariance nhưng generics invariant, và vì sao tạo array của non-reifiable generic types bị hạn chế. Khi thật sự cần `T[]`, hãy dùng factory hoặc token kiểu đáng tin; còn với cấu trúc generic linh hoạt, ưu tiên collections và các wildcard diễn đạt đúng hợp đồng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-arrays-002">Vì sao không thể trực tiếp tạo new T&#91;&#93;</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao không thể trực tiếp tạo `new T[]` trong Java Generics?

Trong Java, bạn có thể **khai báo** biến hoặc tham số kiểu mảng `T[]` trong lớp/phương thức generic, nhưng **không thể trực tiếp khởi tạo** bằng `new T[n]`:

```java
// Đoạn minh họa: dòng có chú thích lỗi không thể biên dịch.
class Buffer<T> {
    private T[] data; // Hợp lệ: khai báo tham chiếu T[]

    Buffer(int capacity) {
        // data = new T[capacity]; // LỖI: generic array creation
    }
}
```

Nguyên nhân cốt lõi: **mảng Java lưu kiểu phần tử (component type) tại runtime và kiểm tra kiểu khi ghi**, còn **type variable `T` của generics không phải kiểu có đầy đủ thông tin tại runtime (non-reifiable)**. Khi compiler xóa kiểu (type erasure), `T` có thể đại diện cho nhiều kiểu cụ thể khác nhau nhưng `new T[n]` lại đòi tạo ra một mảng có component type thực sự, ổn định và kiểm tra được.

Có nhiều cách giải quyết tùy mục tiêu:

- Nếu chỉ cần một nhóm phần tử generic: **ưu tiên `List<T>`**, ví dụ `new ArrayList<T>()`.
- Nếu phải trả về **mảng thật có runtime component type đúng**: truyền `Class<T>` để dùng `Array.newInstance`, hoặc nhận một *array factory* như `IntFunction<T[]>`.
- Nếu thao tác với API chuyển danh sách thành mảng: truyền mảng mẫu hoặc generator, chẳng hạn `toArray(new String[0])` hay `toArray(String[]::new)`.
- Không xem `(T[]) new Object[n]` là lời giải an toàn phổ quát: đoạn ép kiểu đó **không tạo một mảng runtime `String[]` khi `T = String`**.

## 1. Mảng Java khác generic ở loại thông tin được lưu lúc chạy

Xét:

```java
String[] names = new String[2];
Object[] objects = names;

objects[0] = "An"; // Hợp lệ
// objects[1] = 42; // Biên dịch được nhưng ném ArrayStoreException khi chạy
```

Cả `names` và `objects` tham chiếu **cùng một mảng runtime `String[]`**. Cho dù biến `objects` có kiểu tĩnh là `Object[]`, JVM vẫn biết mảng thực có component type `String`. Vì vậy, khi ghi một `Integer` vào mảng ấy, JVM có thể kiểm tra và từ chối bằng `ArrayStoreException`.

Đối chiếu với generic:

```java
// Snippet minh họa, trong phương thức hoặc class có import phù hợp.
java.util.List<String> text = new java.util.ArrayList<>();
java.util.List<Integer> numbers = new java.util.ArrayList<>();

boolean sameClass = text.getClass() == numbers.getClass(); // true
```

Cả hai collection dùng cùng runtime class `ArrayList`; Java không tự sinh `ArrayList<String>` và `ArrayList<Integer>` thành các class riêng. Generic type arguments được compiler kiểm tra ở mã nguồn nhưng bị xóa khỏi descriptor thông thường ở runtime.

Mảng có **covariance**: `String[]` gán được cho `Object[]`, song việc ghi sai kiểu bị kiểm tra lúc chạy. Generic `List<T>` lại **invariant**: `List<String>` không gán trực tiếp cho `List<Object>`. Java ưu tiên bảo vệ generic bằng các quy tắc kiểu ở compile time thay vì runtime component type riêng cho từng `T`.

## 2. Type variable `T` là non-reifiable: căn nguyên của lệnh cấm

Theo quy tắc về **reifiable types** của Java, `String`, `Number`, raw `List`, `List<?>` và array có component type reifiable có thể được biểu diễn đầy đủ theo nhu cầu kiểm tra runtime. Nhưng **type variable** như `T` hay `E` là non-reifiable.

```java
class GenericSample<T> {
    T value;   // Hợp lệ
    T[] items; // Hợp lệ

    void example() {
        // T element = new T();    // Không hợp lệ
        // Class<?> kind = T.class; // Không hợp lệ
        // items = new T[10];      // Không hợp lệ
    }
}
```

Ba phép `new T()`, `T.class` và `new T[10]` liên quan những giới hạn khác nhau nhưng cùng phản ánh việc compiler không biết một runtime class cụ thể đủ để thực hiện thao tác được yêu cầu.

Ví dụ, khi dùng `GenericSample<String>`, một người lập trình có thể mong `items` là `String[]`; khi dùng `GenericSample<Integer>`, lại mong `Integer[]`. Nếu một triển khai giả định âm thầm tạo `Object[]` cho mọi `T`, mảng sẽ **không có runtime component type đúng với kiểu mà bên gọi kỳ vọng**.

### Quy tắc xóa kiểu không cứu được `new T[]`

Nếu `T` không có giới hạn trên rõ ràng, erasure của `T` là `Object`. Nếu có `<T extends Number>`, erasure là `Number`. Tuy vậy:

```java
class NumericBuffer<T extends Number> {
    T[] numbers;

    void allocate(int n) {
        // numbers = new T[n]; // Vẫn là generic array creation không hợp lệ
    }
}
```

Việc biết giới hạn `Number` không cho phép compiler khẳng định người dùng yêu cầu một `Number[]` thay vì `Integer[]` hoặc `Double[]`. `T` vẫn là type variable non-reifiable, không tự trở thành một class runtime cụ thể.

## 3. Vì sao `new Object[n]` rồi ép `(T[])` không tương đương `new T[n]`?

Một mẹo thường gặp là:

```java
// Ví dụ có thể biên dịch nhưng không an toàn cho API trả T[].
@SuppressWarnings("unchecked")
static <T> T[] unsafeArray(int size) {
    return (T[]) new Object[size];
}
```

Hãy phân biệt hai điều:

1. `new Object[size]` **luôn tạo runtime mảng `Object[]`**, không phụ thuộc type argument `T` được suy luận khi gọi.
2. Cast `(T[])` trong phương thức generic có thể chỉ được kiểm tra theo **erasure của `T[]`**, nên một mảng `Object[]` có thể tạm đi qua bước cast ở đây. Điều đó không làm runtime component type đổi thành `String` hoặc `Integer`.

Ví dụ:

```java
// Snippet minh họa khi gọi phương thức unsafeArray đã khai báo:
try {
    String[] data = unsafeArray(3);
    System.out.println(data.length);
} catch (ClassCastException exception) {
    System.out.println("ClassCastException");
}
```

Bên gọi muốn nhận `String[]`; vì object thực tế vẫn là `Object[]`, runtime cast tương ứng có thể ném `ClassCastException` **ngay tại chỗ gán kết quả**. Đây là ví dụ chứng minh rằng ép kiểu tham chiếu không biến đổi đối tượng mảng đã được tạo.

**Đừng nhầm `ClassCastException` với `ArrayStoreException`:** lỗi thứ nhất xảy ra khi không thể coi object là kiểu đích của một cast; lỗi thứ hai xảy ra khi cố ghi một phần tử không phù hợp vào array có runtime component type xác định.

### Khi nào có thể dùng `Object[]` làm bộ lưu trữ nội bộ?

Nếu đang hiện thực một cấu trúc dữ liệu generic, bạn có thể giữ `Object[]` **nội bộ** rồi kiểm soát các thao tác đọc/ghi, thay vì công khai nó dưới danh nghĩa mảng `T[]` có runtime type chính xác:

```java
// Snippet class có thể biên dịch độc lập khi nằm trong file phù hợp.
class ObjectBackedBuffer<T> {
    private final Object[] values;
    private int count;

    ObjectBackedBuffer(int capacity) {
        values = new Object[capacity];
    }

    void add(T value) {
        values[count++] = value; // Cần kiểm tra capacity ở code sản xuất
    }

    @SuppressWarnings("unchecked")
    T get(int index) {
        return (T) values[index];
    }
}
```

Ở đây, việc đọc qua `T` dựa vào invariant rằng **chỉ các giá trị `T` đi vào bằng API được kiểm tra kiểu**, không có đường raw/unchecked phá ràng buộc. `get` có thể trả giá trị bằng cast gắn với erasure của `T`; ở bên gọi, compiler cũng có thể chèn cast sang kiểu cụ thể.

Đây là mô hình bộ lưu trữ generic nội bộ, **không phải một cách tạo `T[]` thật**. Nếu API phải trả mảng cho caller, không nên đưa thẳng `Object[]` ra bằng cast giả làm `T[]`.

## 4. Cách tạo mảng đúng runtime type bằng `Class<T>` và reflection

Khi caller có thể cung cấp runtime class của thành phần, `Array.newInstance` là cách phổ biến:

```java
import java.lang.reflect.Array;

static <T> T[] createArray(Class<T> elementType, int size) {
    @SuppressWarnings("unchecked")
    T[] result = (T[]) Array.newInstance(elementType, size);
    return result;
}
```

Ví dụ sử dụng:

```java
String[] names = createArray(String.class, 3);
Integer[] scores = createArray(Integer.class, 2);
```

Khác với `new Object[n]`, `Array.newInstance(String.class, n)` tạo **mảng runtime `String[]` thật**. Kết quả là `Object` theo chữ ký phản xạ và cần cast, nhưng cast có thể biện minh được vì chính đối tượng `Class<T>` đã được dùng để chọn component type khi tạo mảng.

Cần lưu ý:

- `Class<T>` mô tả một runtime class cụ thể, ví dụ `String.class` hoặc `Integer.class`; không có `List<String>.class` trong Java.
- `Array.newInstance` nhận `Class<?>` làm thành phần và kích thước; với `size < 0` sẽ có lỗi kích thước mảng âm như `NegativeArraySizeException`.
- Ví dụ generic `createArray` phù hợp với **reference type `T`** như `String` hoặc `Integer`. Nếu truyền token cho primitive như `int.class` qua API khác, kết quả có thể là `int[]` nhưng không tương thích với ý tưởng một mảng generic tham số hóa `T[]` theo kiểu tham chiếu.
- Nếu phải tạo mảng cho một kiểu tham số hóa lồng nhau như `List<String>`, token `List.class` chỉ chọn được component runtime là raw `List`, **không tạo được một runtime component type `List<String>` riêng biệt**.

Vì vậy, `Class<T>` giải quyết được nhu cầu “cần đúng runtime class của phần tử”, không biến toàn bộ generics của Java thành reified generics.

## 5. Chương trình Java 17+ so sánh các cách tạo mảng

Lưu nguyên đoạn dưới đây vào tệp **`GenericArrayDemo.java`**:

```java
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

public class GenericArrayDemo {
    static <T> T[] createArray(Class<T> elementType, int size) {
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(elementType, size);
        return result;
    }

    static <T> T[] unsafeArray(int size) {
        @SuppressWarnings("unchecked")
        T[] result = (T[]) new Object[size];
        return result;
    }

    public static void main(String[] args) {
        String[] names = createArray(String.class, 2);
        names[0] = "An";
        names[1] = "Binh";

        System.out.println("String array: " + Arrays.toString(names));
        System.out.println("Runtime array: "
                + names.getClass().getTypeName());

        Integer[] scores = createArray(Integer.class, 2);
        scores[0] = 10;
        System.out.println("Integer array: "
                + Arrays.toString(scores));

        try {
            String[] wrong = unsafeArray(2);
            System.out.println(wrong.length);
        } catch (ClassCastException exception) {
            System.out.println("Unsafe Object[] cast: ClassCastException");
        }

        Object[] asObjects = names;
        try {
            asObjects[0] = 42;
        } catch (ArrayStoreException exception) {
            System.out.println("Wrong array store: ArrayStoreException");
        }

        List<?>[] groups = new List<?>[2];
        groups[0] = List.of("Java");
        groups[1] = List.of(42);

        System.out.println("Wildcard arrays: "
                + Arrays.toString(groups));
        System.out.println("Wildcard component: "
                + groups.getClass().getComponentType().getTypeName());
    }
}
```

Biên dịch/chạy trên JDK 17 trở lên:

```bash
javac -encoding UTF-8 -Xlint:unchecked GenericArrayDemo.java
java GenericArrayDemo
```

Đầu ra standard output:

```text
String array: [An, Binh]
Runtime array: java.lang.String[]
Integer array: [10, null]
Unsafe Object[] cast: ClassCastException
Wrong array store: ArrayStoreException
Wildcard arrays: [[Java], [42]]
Wildcard component: java.util.List
```

Ý nghĩa:

1. `createArray(String.class, 2)` tạo một đối tượng runtime `String[]`, đúng với kiểu biến bên gọi.
2. `createArray(Integer.class, 2)` tạo `Integer[]`, giá trị thứ hai còn mặc định `null` vì đây là mảng tham chiếu mới.
3. `unsafeArray(2)` chỉ tạo `Object[]`. Khi caller yêu cầu `String[]`, cast ở đường gọi ném `ClassCastException`.
4. Cùng một `String[]` có thể xem qua `Object[]`, nhưng JVM chặn thao tác ghi `Integer` bằng `ArrayStoreException`.
5. `new List<?>[2]` hợp lệ vì component type `List<?>` là **reifiable**; mảng có runtime component class `java.util.List`, không giữ `String` hay `Integer` cho từng danh sách con.

Các dòng cố ý không hợp lệ như `new T[n]` chỉ nằm trong những snippet đã được chú thích, **không** xuất hiện dưới dạng code chạy của chương trình này.

## 6. Vì sao `new List<?>[n]` được nhưng `new List<String>[n]` bị cấm?

Quy tắc tạo array trong Java yêu cầu **kiểu thành phần** tại biểu thức `new ElementType[n]` phải reifiable.

```java
// Hợp lệ:
java.util.List<?>[] unknownLists = new java.util.List<?>[3];

// Không biên dịch:
// java.util.List<String>[] names = new java.util.List<String>[3];
```

Mặc dù cả `List<?>` và `List<String>` đều sử dụng runtime interface `List`, hai kiểu này phát biểu các ràng buộc khác nhau:

- `List<?>` không nói phần tử của các danh sách con có một kiểu cụ thể. Runtime chỉ cần kiểm tra mỗi giá trị đặt vào mảng là một `List`.
- `List<String>` ràng buộc kiểu phần tử `String`, điều mà runtime `List` không thể xác minh trực tiếp cho mỗi đối tượng. Vì thế việc cho phép tạo mảng `List<String>[]` có thể tạo ảo giác rằng JVM sẽ chặn mọi cách đưa list sai kiểu vào.

Ví dụ phản chứng mô phỏng nếu một `List<String>[]` tồn tại thông qua thao tác unsafe:

```java
// Snippet cố ý không an toàn, chỉ để giải thích.
java.util.List<?>[] rawArray = new java.util.List<?>[1];
Object[] objects = rawArray;
objects[0] = java.util.List.of(42); // Runtime cho phép: đây là List

// Không có đảm bảo rằng mọi phần tử trong List là String.
```

Đối tượng `List<Integer>` ở đây vẫn là một `List` hợp lệ xét theo runtime component type của mảng. Không có kiểm tra generic element type giống kiểm tra `String[]` khi ghi `Integer` trực tiếp.

**Nhớ:** `List<?>[]` không phải `List<Object>[]`; wildcard giữ phần tử kiểu chưa biết, còn `List<Object>` là một parameterized type cụ thể và không được tự động suy ra từ `List<String>`.

## 7. Vì sao khai báo `T[]` hợp lệ nhưng khởi tạo bằng `new T[n]` không?

Nên phân biệt:

```java
class Holder<T> {
    private T[] values; // Hợp lệ

    Holder(T[] values) { // Hợp lệ
        this.values = values;
    }

    T[] getValues() { // Hợp lệ
        return values;
    }
}
```

Mã này **không tạo mảng mới**. Nó chỉ nhận một tham chiếu `T[]` đã tồn tại. Ví dụ caller tạo `String[]` bên ngoài và truyền vào:

```java
Holder<String> h = new Holder<>(new String[] { "An", "Binh" });
String[] data = h.getValues();
```

Tại runtime, object mảng ban đầu thực sự là `String[]`, nên việc trả về qua phương thức generic phù hợp với tham chiếu `String[]` của bên gọi.

Ngược lại, biểu thức `new T[n]` buộc compiler phải **tự chọn runtime component type**, nhưng không có token kiểu cụ thể để lựa chọn. Đó là điều bị cấm.

Một class generic cần mảng có thể nhận mảng từ constructor, hoặc nhận `Class<T>`/factory để tự tạo mảng đúng kiểu.

### Thiết kế class nhận `Class<T>`

```java
// Snippet minh họa Java 17+, cần import java.lang.reflect.Array.
class TypedBuffer<T> {
    private final T[] data;

    @SuppressWarnings("unchecked")
    TypedBuffer(Class<T> elementType, int size) {
        data = (T[]) java.lang.reflect.Array.newInstance(elementType, size);
    }

    void set(int index, T value) {
        data[index] = value;
    }

    T[] toArray() {
        return data.clone();
    }
}
```

Caller:

```java
TypedBuffer<String> buffer = new TypedBuffer<>(String.class, 2);
buffer.set(0, "An");
String[] copy = buffer.toArray();
```

Ở đây, `toArray()` trả về bản clone có runtime component type giống mảng gốc, tức `String[]`. `clone()` là **sao chép nông**: với phần tử mutable, bản thân các object phần tử không tự được clone.

Hàm `set` trong ví dụ dùng mảng Java làm bộ lưu trữ: cần thêm kiểm tra index, capacity và các ràng buộc nghiệp vụ khác khi xây dựng thư viện thực tế.

## 8. Dùng `IntFunction<T[]>` để caller chọn cách tạo mảng

Một cách khác là yêu cầu caller cung cấp một **array factory**:

```java
// Snippet trong class/method thích hợp:
static <T> T[] allocate(int size, java.util.function.IntFunction<T[]> factory) {
    return factory.apply(size);
}
```

Khi gọi:

```java
String[] names = allocate(2, String[]::new);
Integer[] numbers = allocate(3, Integer[]::new);
```

Biểu thức method reference `String[]::new` đặt việc tạo mảng vào nơi đã biết rõ component type `String`. Phương thức generic nhận factory và sử dụng nó; nó **không phải tự viết `new T[n]`**.

Cách này hữu ích khi viết API generic không muốn phụ thuộc reflection hoặc khi một bộ sưu tập/cơ chế xử lý nhận generator có thể cung cấp mảng đúng kiểu.

Lưu ý rằng caller có trách nhiệm cung cấp factory tương thích; nếu ép raw/unchecked để truyền factory sai, những rủi ro an toàn kiểu vẫn có thể phát sinh.

## 9. Chuyển collection sang mảng: tránh tự tạo `T[]`

Nhiều API Java đã có cơ chế để caller quyết định runtime component type.

Ví dụ:

```java
java.util.List<String> list = java.util.List.of("An", "Binh");

String[] first = list.toArray(new String[0]);
String[] second = list.toArray(String[]::new);
```

Hai cách đều nhận kết quả kiểu `String[]`. Mảng `String[0]` đóng vai trò mẫu thể hiện kiểu thành phần; API có thể tạo một mảng có kích thước phù hợp theo hợp đồng `toArray(T[])`. Với `toArray(IntFunction<T[]>)`, generator `String[]::new` tạo mảng đúng runtime component type.

Nếu chỉ gọi `list.toArray()` **không truyền gì**, kiểu trả về là `Object[]`, không thể giả định đó là `String[]`:

```java
Object[] values = list.toArray(); // Hợp lệ
// String[] wrong = (String[]) list.toArray(); // Có thể ClassCastException
```

Cách tiếp cận này làm rõ trách nhiệm: thư viện generic xử lý các phần tử, còn caller cung cấp thông tin kiểu để tạo mảng đúng runtime.

## 10. Vì sao `List<T>` thường đơn giản hơn `T[]`?

Nếu mục tiêu chỉ là lưu trữ một số phần tử kiểu `T`, dùng collection generic thông thường:

```java
import java.util.ArrayList;
import java.util.List;

class DynamicBuffer<T> {
    private final List<T> elements = new ArrayList<>();

    void add(T value) {
        elements.add(value);
    }

    T get(int index) {
        return elements.get(index);
    }
}
```

Khi dùng `DynamicBuffer<String>`, compiler kiểm tra `add(String)` ở mã nguồn, và không cần dựng runtime array `String[]`. `ArrayList` có thể quản lý bộ lưu trữ nội bộ bằng một cấu trúc array phù hợp với triển khai của nó, nhưng API công khai vẫn giữ kiểu `List<T>`.

Đây thường là lựa chọn tốt vì:

- Không yêu cầu `Class<T>` hoặc factory chỉ để lưu trữ phần tử.
- Dễ thay đổi kích thước; array có độ dài cố định.
- Các phương thức `List<T>` cung cấp thao tác thêm/xóa/truy xuất có kiểm tra generic ở nguồn.
- Giảm nhu cầu ép kiểu `Object[]` thành `T[]` để trả kết quả cho caller.

Chọn `T[]` khi cần tương tác với API mảng, tận dụng cấu trúc array hoặc có yêu cầu đặc biệt về runtime component type. Không nên dùng array như lựa chọn mặc định chỉ vì từng quen với `new int[n]` trong các thuật toán không generic.

### Mảng primitive là trường hợp khác

`int[]` và `double[]` là các mảng runtime có thành phần nguyên thủy rõ ràng. Java generic thông thường không dùng `List<int>` hay `T = int` theo kiểu tham số generic thông thường; với collections cần `List<Integer>` và boxing/unboxing. Việc cần `int[]` hiệu quả về biểu diễn nên được thiết kế riêng, không kỳ vọng `new T[]` trong generic tự động tạo primitive array.

## 11. Generic varargs: có `T...` mà vẫn cấm `new T[]`?

Java cho phép khai báo phương thức varargs generic:

```java
@SafeVarargs
static <T> int count(T... values) {
    return values.length;
}
```

Khi gọi varargs, compiler có cơ chế tạo mảng cho đối số ở call site. Trong trường hợp element type **non-reifiable**, compiler có thể tạo mảng dựa trên erasure và phát **unchecked/generic array creation warning**, vì runtime component type không thể biểu diễn đầy đủ type argument.

Vì vậy, **cho phép khai báo `T...` không có nghĩa trong thân phương thức được phép viết `new T[10]`**. Varargs là một cú pháp gọi phương thức có quy tắc biên dịch riêng, và nó cũng có những rủi ro heap pollution nếu mã làm lộ hoặc ghi dữ liệu sai kiểu vào mảng varargs.

`@SafeVarargs` là một lời cam kết của người viết phương thức rằng phần thân **không thực hiện thao tác không an toàn** với mảng varargs. Annotation được phép trên constructor và những phương thức đáp ứng điều kiện, ví dụ static, final hoặc private theo phiên bản Java phù hợp. Nó **không** làm `T` reifiable và cũng không phải công cụ che cảnh báo khi mã vẫn làm hỏng dữ liệu.

Ví dụ có thể được gắn `@SafeVarargs` nếu chỉ đếm phần tử là như trên; với phương thức lưu array vào trường dùng chung, đưa nó ra ngoài hoặc ghi sai kiểu qua alias, cần đánh giá rất cẩn thận trước khi hứa `@SafeVarargs`.

## 12. Array covariance và heap pollution: lý do bảo vệ này quan trọng

Giả sử Java cho phép tạo array có component type generic không reifiable mà mọi người nhầm rằng nó sẽ chặn dữ liệu sai kiểu:

```java
// Snippet minh họa thao tác với List<?>[] vốn hợp lệ.
java.util.List<?>[] lists = new java.util.List<?>[1];
Object[] objects = lists;
objects[0] = java.util.List.of(42); // Hợp lệ: vẫn là một List
```

Mảng runtime chỉ kiểm tra phần tử được ghi có là một `List` hay không; nó không kiểm tra được `Integer` hay `String` trong từng list.

Nếu mã khác bằng unchecked cast hoặc raw type tin rằng mảng này là `List<String>[]`, một phần tử list chứa `Integer` có thể được đọc sau đó dưới giả định `String`, dẫn đến lỗi cast. Trạng thái một biến generic tuyên bố có kiểu tham số hóa không phù hợp dữ liệu thực gọi là **heap pollution**.

Đây là nguyên nhân khiến Java phân biệt nghiêm ngặt mảng và generic:

- Mảng có kiểm tra **runtime component type** và tính covariance.
- Generics kiểm tra chủ yếu ở **compile time**, dùng erasure và thường bất biến.
- Array có component type là generic non-reifiable sẽ tạo ra một ranh giới khó bảo đảm an toàn kiểu.

Do đó, các biểu thức như `new List<String>[n]` và `new T[n]` bị cấm, trong khi khai báo tham chiếu đến loại array ấy vẫn có thể hợp lệ trong những ngữ cảnh thích hợp.

## 13. Các lỗi thường gặp và cách sửa

**Lỗi 1 — “`T` sẽ được thay bằng `String` khi gọi nên `new T[n]` phải chạy.”** Sai. Type arguments của Java Generics thông thường không tạo class specialization cho từng lần gắn kiểu. Compiler không có một runtime class component duy nhất cho biểu thức này.

**Lỗi 2 — “Dùng `(T[]) new Object[n]` là mảng kiểu `T[]` thật.”** Sai. Object thật vẫn là `Object[]`; bên gọi yêu cầu `String[]` có thể gặp `ClassCastException`.

**Lỗi 3 — “`T extends Number` sẽ cho phép `new T[n]`.”** Sai. `T` vẫn non-reifiable dù erasure có thể là `Number`.

**Lỗi 4 — “Đã không tạo được `T[]` thì cũng không khai báo trường `T[]` được.”** Sai. Khai báo/nhận/trả tham chiếu `T[]` được phép; bị hạn chế là biểu thức khởi tạo array với component type non-reifiable.

**Lỗi 5 — “`new List<?>[n]` cũng bị cấm vì có dấu `<...>`.”** Sai. `List<?>` là reifiable, nên array có component type này được phép tạo. Nhưng `List<String>` không reifiable.

**Lỗi 6 — “Reflection không an toàn vì luôn trả `Object[]`.”** Sai. `Array.newInstance(String.class, n)` thực sự tạo `String[]`; giá trị phản xạ có kiểu trả về tổng quát là `Object` nhưng runtime class mảng đúng theo component type token.

**Lỗi 7 — “`List<T>` là mảng trá hình, nên nó cũng cần `new T[n]`.”** Sai. Collection generic dùng API generic; cơ chế lưu trữ nội bộ không buộc public API trả một mảng runtime `T[]`.

**Lỗi 8 — “`@SuppressWarnings("unchecked")` khiến cast từ `Object[]` thành `T[]` an toàn.”** Sai. Annotation chỉ ẩn compiler warning, không thay runtime component class.

**Lỗi 9 — “`@SafeVarargs` cho phép tạo mọi generic array mà không có rủi ro.”** Sai. Nó biểu thị cam kết về thao tác an toàn trên tham số varargs, không biến non-reifiable type thành reifiable.

**Lỗi 10 — “Nếu cast `Object[]` sang `String[]` không chạy thì mọi cast từ array đều sai.”** Sai. Cast tới mảng có runtime component type tương ứng là bình thường; vấn đề nằm ở việc object mảng thực sự được tạo bằng kiểu nào.

## 14. Bài tập tự kiểm tra

**Câu 1.** Trong `class Box<T>`, `T[] array;` có hợp lệ không?

Có. Khai báo một biến tham chiếu đến mảng `T[]` không yêu cầu tạo mảng mới.

**Câu 2.** Tại sao `array = new T[10];` không hợp lệ?

Vì `T` là type variable non-reifiable, trong khi biểu thức tạo mảng đòi một runtime component type có thể biểu diễn và kiểm tra được.

**Câu 3.** `String[] result = createArray(String.class, 5)` có runtime class gì?

Là `String[]`, vì `Array.newInstance` tạo mảng dựa trực tiếp trên `String.class`.

**Câu 4.** Hàm `<T> T[] f() { return (T[]) new Object[3]; }` có chắc an toàn khi caller cần `String[]` không?

Không. Runtime mảng vẫn là `Object[]`, và cast ở caller có thể ném `ClassCastException`.

**Câu 5.** `new List<?>[2]` và `new List<String>[2]` khác nhau thế nào?

Cái đầu hợp lệ vì `List<?>` reifiable; cái sau bị cấm vì `List<String>` non-reifiable.

**Câu 6.** Khi nào nên ưu tiên `List<T>`?

Khi chủ yếu cần tập hợp phần tử generic, không cần mảng runtime với component type cụ thể và muốn tránh cast array khó chứng minh an toàn.

**Câu 7.** `String[] data = {"A"}; Object[] objects = data; objects[0] = 42;` gây lỗi gì?

`ArrayStoreException`, vì array thực có component type `String`. Đây khác với `ClassCastException` có thể xảy ra khi cố coi một `Object[]` là `String[]`.

**Câu 8.** `List<String> names = List.of("A"); String[] values = names.toArray(String[]::new);` có hợp lệ không?

Có. Caller cung cấp generator `String[]::new`, cho phép API tạo runtime mảng đúng component type.

## 15. Tóm tắt bằng quyết định thiết kế

| Nhu cầu | Cách làm nên chọn | Điều cần tránh |
| --- | --- | --- |
| Lưu nhiều giá trị generic và thêm/xóa linh hoạt | `List<T>` / `ArrayList<T>` | Tạo `T[]` khi không cần |
| Bộ lưu trữ nội bộ với độ dài cố định | `Object[]` trong cấu trúc dữ liệu được bao kín, bảo vệ invariant | Công khai `Object[]` dưới lời hứa runtime `T[]` |
| Trả mảng đúng class phần tử | `Class<T>` + `Array.newInstance` | `(T[]) new Object[n]` khi caller có thể cần `String[]` |
| Caller đã biết kiểu phần tử | `IntFunction<T[]>`, ví dụ `String[]::new` | Giả định compiler khôi phục `T` runtime |
| Chuyển `List<String>` thành mảng | `toArray(new String[0])` hoặc `toArray(String[]::new)` | Cast `Object[]` trả về từ `toArray()` thành `String[]` |
| Cần danh sách nhiều kiểu chưa biết | `List<?>` hoặc mảng `List<?>[]` khi thực sự cần mảng | Tin `List<?>[]` xác nhận nội dung của mọi `List<String>` |

**Kết luận:** Java cấm trực tiếp `new T[]` không phải vì không thể đặt generic vào mảng, mà vì **type variable `T` không reifiable còn mảng cần biết component type tại runtime**. Một `Object[]` được cast thành `T[]` không tự hóa thành `String[]` hay `Integer[]`, gây nguy cơ lỗi runtime. Khi cần một mảng đúng kiểu, truyền `Class<T>` hoặc array factory; khi chỉ cần cấu trúc lưu trữ generic, `List<T>` thường đơn giản và ít rủi ro hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-arrays-003">Vì sao không thể trực tiếp tạo new List&lt;String&gt;&#91;&#93;</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao không thể trực tiếp tạo `new List<String>[]` trong Java Generics?

## 1. Vấn đề cụ thể

Ta có thể tạo mảng chứa `String`:

```java
String[] words = new String[3];
```

Ta cũng có thể tạo một danh sách chứa `String`:

```java
java.util.List<String> words = new java.util.ArrayList<>();
```

Nhưng ghép hai ý tưởng lại thành:

```java
// CỐ Ý KHÔNG HỢP LỆ: đây là ví dụ lỗi biên dịch.
java.util.List<String>[] groups = new java.util.List<String>[3];
```

thì `javac` báo **generic array creation**. `List<String>[]` là kiểu có thể xuất hiện trong khai báo biến, tham số hay kết quả phương thức ở những ngữ cảnh phù hợp, nhưng **không được trực tiếp tạo mảng có kiểu phần tử là `List<String>`** bằng biểu thức `new List<String>[3]`.

Vì sao? Hai cơ chế được thiết kế theo hai thời điểm khác nhau:

- **Mảng Java có kiểu phần tử được kiểm tra lúc chạy.** Một mảng thực sự biết component type runtime của nó; JVM dùng thông tin đó để kiểm tra khi gán vào một phần tử.
- **Generics thông thường được kiểm tra chủ yếu khi biên dịch.** Thông tin đối số `<String>` trên một `List<String>` bị xóa khỏi kiểu đại diện dùng cho kiểm tra runtime thông thường.

Nếu Java cho phép tạo mảng trực tiếp với component type `List<String>`, JVM sẽ không có đủ thông tin để bảo vệ lời hứa “mọi phần tử là danh sách chuỗi” khi mảng được truy cập thông qua các tham chiếu kiểu rộng hơn. Cấm trực tiếp tạo loại mảng đó là một phần của thiết kế giữ an toàn kiểu giữa **array covariance**, **runtime store checks** và **generic erasure**.

**Phân biệt quan trọng:** Việc *khai báo* `List<String>[] groups;` hợp lệ không đồng nghĩa với việc biểu thức `new List<String>[3]` hợp lệ.

## 2. Hai quy tắc nền tảng: mảng biết component type, generic không reify đối số kiểu

Một mảng được tạo như sau:

```java
String[] a = new String[2];
Number[] b = new Integer[2];
```

Object mảng thực sự mang runtime array class như `[Ljava.lang.String;` hoặc `[Ljava.lang.Integer;`. Khi JVM nhận một thao tác gán phần tử, nó có thể kiểm tra object được đưa vào có phù hợp runtime component type hay không.

Ngược lại:

```java
java.util.List<String> text = new java.util.ArrayList<>();
java.util.List<Integer> nums = new java.util.ArrayList<>();
```

Hai biến có kiểu generic khác nhau tại thời điểm biên dịch, nhưng khi thực thi, cả hai object đều có runtime class `java.util.ArrayList`. `getClass()` trên từng object không trả về `ArrayList<String>` hoặc `ArrayList<Integer>` như hai class tách biệt.

Từ Java Language Specification:

- `List<String>` là **non-reifiable type**: không thể biểu diễn đầy đủ đối số generic `String` để runtime kiểm tra như component type của mảng.
- `List<?>` với **wildcard không giới hạn** là một ví dụ kiểu **reifiable**; kiểm tra runtime chỉ cần biết raw `List`, không cần xác nhận một đối số kiểu cụ thể.
- Một biểu thức tạo mảng với `new` phải có component type **reifiable**.

Từ các quy tắc này, `new List<String>[3]` bị cấm, còn `new List<?>[3]` được phép. Điều này không phải một giới hạn tùy ý của IDE; đó là ràng buộc ngôn ngữ Java.

## 3. Đối chiếu mảng hiệp biến với generics bất biến

### 3.1. Arrays là covariant

Trong Java, nếu `String` là subtype của `Object`, thì `String[]` là subtype của `Object[]`:

```java
String[] names = new String[1];
Object[] objects = names; // Hợp lệ.
```

**Array covariance** cho phép phép gán đó ở compile time. Nhưng JVM vẫn giữ component type thực tế của mảng là `String` để kiểm tra thao tác lưu phần tử.

Nếu code sau thực hiện:

```java
objects[0] = Integer.valueOf(42);
```

thì JVM thấy một `Integer` không thể được lưu vào mảng `String[]` và ném `ArrayStoreException`.

Điều kiện runtime này ngăn mảng `String[]` bị chứa một phần tử không phải `String`, dù tham chiếu `Object[]` rộng hơn được dùng.

### 3.2. Generics thường invariant

Ngược lại:

```java
java.util.List<String> strings = new java.util.ArrayList<>();
// java.util.List<Object> objects = strings; // KHÔNG hợp lệ.
```

`List<String>` **không** phải subtype của `List<Object>`: Java Generics mặc định **invariant**. Nếu phép gán bị cấm này được cho phép, có thể gọi `objects.add(42)` rồi làm hỏng hợp đồng chuỗi của `strings`.

Để đọc những danh sách chưa biết kiểu phần tử, Java cung cấp wildcard:

```java
java.util.List<?> unknown = strings;
Object first = unknown.isEmpty() ? null : unknown.get(0);
```

`List<?>` chấp nhận danh sách với đối số kiểu chưa xác định, nhưng không cho thêm tùy tiện `Integer` hay `String` vào đó.

### 3.3. Vì sao kết hợp hai mô hình gây nguy hiểm?

Mảng có cơ chế kiểm tra **runtime component type**; danh sách generic có **compile-time type argument** bị erasure. Một mảng có component type là `List<String>` muốn bảo vệ hoàn toàn an toàn kiểu thì sẽ cần phân biệt `List<String>` với `List<Integer>` trong runtime array store check. Nhưng cơ chế type erasure không cung cấp sự phân biệt ấy.

Không được suy ra rằng “mảng không bao giờ an toàn” hoặc “generics không bao giờ an toàn”. Hai cơ chế đều an toàn trong phạm vi các quy tắc của mình; vấn đề là một biểu thức tạo mảng mới **không thể cam kết kiểm tra runtime** cho đối số generic non-reifiable.

## 4. Chương trình chạy được: `ArrayStoreException` trên mảng reifiable

Tạo tệp **`ArrayStoreCheckDemo.java`**:

```java
public class ArrayStoreCheckDemo {
    public static void main(String[] args) {
        String[] words = new String[2];
        words[0] = "Java";

        Object[] alias = words;

        try {
            alias[1] = Integer.valueOf(42);
        } catch (ArrayStoreException ex) {
            System.out.println("ArrayStoreException");
        }

        System.out.println(words[0]);
        System.out.println(words[1] == null);
        System.out.println(words.getClass().getComponentType().getName());
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac ArrayStoreCheckDemo.java
java ArrayStoreCheckDemo
```

Stdout chính xác:

```text
ArrayStoreException
Java
true
java.lang.String
```

Giải thích theo thứ tự:

1. `words` được tạo bằng `new String[2]`, nên **component type runtime** là `String`.
2. `Object[] alias = words` hợp lệ do array covariance. Cả hai biến trỏ tới **cùng mảng**.
3. `alias[1] = Integer.valueOf(42)` biên dịch được do kiểu tĩnh `Object[]`, nhưng lúc chạy JVM kiểm tra thực tế là `String[]`.
4. JVM ném `ArrayStoreException`; phần tử vị trí 1 vẫn là `null`.
5. Reflection trên object mảng trả component type là `String`.

Runtime biết đủ thông tin để ngăn thao tác sai. Đây là điểm khác cơ bản với một object `ArrayList<String>` thông thường.

## 5. Thử tạo `new List<String>[3]`: lỗi xảy ra trước khi chạy

Ví dụ **cố ý không biên dịch** trong tệp `InvalidGenericArray.java`:

```java
import java.util.List;

public class InvalidGenericArray {
    public static void main(String[] args) {
        List<String>[] groups = new List<String>[3];
    }
}
```

Nếu chạy:

```bash
javac InvalidGenericArray.java
```

Trình biên dịch từ chối biểu thức tạo mảng và thường báo:

```text
generic array creation
```

Đây là **mẩu thông điệp lỗi minh họa**, không cam kết nguyên văn toàn bộ output `javac`: vị trí mũi tên, số dòng và cách trình bày tùy compiler/version.

Đặc biệt, việc biểu thức vế trái là `List<String>[]` **không phải lỗi chính**. Phần không hợp lệ là **`new List<String>[3]`**, bởi `List<String>` là component type non-reifiable.

Các dạng tạo mảng khác cũng không hợp lệ vì component type mang thông tin generic không reifiable:

```java
// CỐ Ý KHÔNG HỢP LỆ — chỉ để phân tích.
new java.util.List<Integer>[2];
new java.util.List<? extends Number>[2];
new java.util.Map<String, Integer>[4];
```

Ngược lại, các khai báo sau **có thể hợp lệ** nếu được đặt trong ngữ cảnh Java phù hợp:

```java
java.util.List<String>[] declared;
java.util.List<String>[] parameter = null;
```

Một khai báo biến kiểu mảng generic không tự tạo đối tượng mảng runtime. Những phép gán về sau vẫn phải thỏa các quy tắc kiểu của Java, và có thể dẫn tới cảnh báo unchecked nếu dùng một đường vòng.

## 6. Kiểu reifiable: tại sao `List<?>[]` được phép?

Java định nghĩa **reifiable types** là các kiểu được biểu diễn đủ ở runtime theo quy tắc ngôn ngữ. Những ví dụ thích hợp trong bối cảnh này:

| Component type | `new ComponentType[n]` | Giải thích |
|---|---|---|
| `String` | Hợp lệ | Runtime biết `String` |
| `Integer` | Hợp lệ | Runtime biết `Integer` |
| Raw `List` | Hợp lệ, có thể có cảnh báo rawtypes | Runtime biết raw `List` |
| `List<?>` | Hợp lệ | Parameterization chỉ dùng wildcard không giới hạn |
| `List<String>` | Không hợp lệ | Không reifiable |
| `List<Integer>` | Không hợp lệ | Không reifiable |
| `List<? extends Number>` | Không hợp lệ | Wildcard có upper bound cụ thể, không thuộc dạng reifiable này |
| Type variable `T` | Không hợp lệ khi viết `new T[n]` | Không có class runtime cụ thể của `T` để tạo array component |

Vì vậy, dòng sau hợp lệ:

```java
java.util.List<?>[] groups = new java.util.List<?>[3];
```

Điều này **không** có nghĩa mảng runtime nhớ từng danh sách bên trong có phần tử kiểu gì. Thành phần của mảng là một `List<?>` — một danh sách với đối số kiểu không xác định, chỉ cần runtime kiểm tra object được lưu là một `List` hợp lệ.

Nên đọc dấu `<?>` chính xác: “một kiểu phần tử chưa biết” khác với “một kiểu phần tử là `Object`”. `List<?>` vẫn giữ các hạn chế generic có ích khi thao tác thêm phần tử.

## 7. Chương trình chạy được: mảng `List<?>[]` hợp lệ

Tạo tệp **`WildcardListArrayDemo.java`**:

```java
import java.util.List;

public class WildcardListArrayDemo {
    public static void main(String[] args) {
        List<?>[] groups = new List<?>[2];

        groups[0] = List.of("A", "B");
        groups[1] = List.of(10, 20);

        System.out.println(groups[0].size());
        System.out.println(groups[1].get(0));
        System.out.println(groups.getClass().getComponentType().getName());

        Object[] alias = groups;

        try {
            alias[0] = "not a list";
        } catch (ArrayStoreException ex) {
            System.out.println("ArrayStoreException");
        }

        System.out.println(groups[0]);
    }
}
```

Lệnh:

```bash
javac WildcardListArrayDemo.java
java WildcardListArrayDemo
```

Stdout:

```text
2
10
java.util.List
ArrayStoreException
[A, B]
```

Giải thích:

- Hai danh sách có kiểu phần tử khác nhau đều phù hợp với `List<?>`.
- Lời gọi `groups[1].get(0)` có kiểu tĩnh an toàn là `Object`, nhưng `println(Object)` vẫn in được giá trị `10`.
- `groups.getClass().getComponentType()` trả `java.util.List`: mảng runtime biết lớp/interface `List`, **không có `String` hoặc `Integer` trong component type**.
- Truy cập qua `Object[]` vẫn phải tuân thủ runtime array store check. String `"not a list"` không phải một `List`, nên bị từ chối.
- `List.of("A","B")` là danh sách không thể sửa qua thao tác cấu trúc thông thường. Ví dụ chỉ đọc, không sửa nội dung danh sách.

### Điều mảng wildcard này không bảo đảm

`List<?>[]` không có nghĩa “mọi slot chứa `List<String>`”. Trên thực tế, ví dụ chủ động đặt một `List<String>` vào slot 0 và một `List<Integer>` vào slot 1.

Nếu nhu cầu là các danh sách **đồng nhất có phần tử `String`**, `List<?>[]` không giải quyết đầy đủ hợp đồng kiểu. Hãy dùng cấu trúc generic phù hợp hơn, ví dụ `List<List<String>>`.

## 8. Vì sao cho phép `new List<String>[]` sẽ gây heap pollution?

Hãy mô phỏng tình huống mà Java đang ngăn chặn. Giả sử tồn tại một mảng `List<String>[]`, rồi tham chiếu rộng hơn `Object[]` lưu vào đó một `List<Integer>`.

Mảng runtime sẽ biết component type đã bị xóa là **raw `List`**. `List<Integer>` vẫn là một đối tượng `List`, nên **array store check không có lý do để bác bỏ**. Nhưng mã đọc qua kiểu `List<String>[]` sẽ mong `String`, tạo nguy cơ `ClassCastException` muộn hơn.

Ta có thể minh họa cơ chế bằng một **unchecked cast được khoanh vùng**; đây không phải cách khuyến nghị tạo mảng generic.

Tạo tệp **`GenericArrayPollutionDemo.java`**:

```java
import java.util.List;

public class GenericArrayPollutionDemo {

    @SuppressWarnings("unchecked")
    static List<String>[] unsafeArray() {
        return (List<String>[]) new List<?>[1];
    }

    public static void main(String[] args) {
        List<String>[] stringLists = unsafeArray();
        stringLists[0] = List.of("safe");

        Object[] arrayAlias = stringLists;

        arrayAlias[0] = List.of(123);

        System.out.println(stringLists.getClass()
                .getComponentType().getName());
        System.out.println(stringLists[0].size());

        try {
            String text = stringLists[0].get(0);
            System.out.println(text);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac GenericArrayPollutionDemo.java
java GenericArrayPollutionDemo
```

Stdout:

```text
java.util.List
1
ClassCastException
```

Phân tích chính xác:

1. `new List<?>[1]` hợp lệ vì component type `List<?>` là reifiable.
2. Ép sang `List<String>[]` là **unchecked cast**: runtime không thể xác minh `<String>` cho các phần tử.
3. `stringLists[0] = List.of("safe")` hợp lệ với kiểu tĩnh của biến.
4. `Object[] arrayAlias = stringLists` hợp lệ do array covariance.
5. `arrayAlias[0] = List.of(123)` không gây `ArrayStoreException`: object là một `List`, phù hợp với raw component runtime của mảng.
6. Truy cập `stringLists[0].get(0)` ở mã nguồn được kiểm tra theo `List<String>`, và phép cast cần thiết sang `String` thất bại khi giá trị thực là `Integer`.

Trạng thái nơi tham chiếu có kiểu `List<String>[]` nhưng phần tử trong mảng không thỏa hợp đồng generic là một dạng **heap pollution**.

`@SuppressWarnings("unchecked")` chỉ giấu cảnh báo ở đoạn chuyển kiểu đã biết nguy hiểm. Annotation này không biến phép cast thành an toàn và không bổ sung kiểm tra runtime nào. Trong ứng dụng thực, tránh mẫu `unsafeArray()` nếu có lựa chọn thiết kế khác.

### Tại sao lỗi không xuất hiện tại lúc lưu `List<Integer>`?

Runtime biết component là `List`, chứ không biết `List<String>` khác `List<Integer>`. Bởi vậy JVM có thể nhận object thuộc `List`. Lỗi phát sinh khi mã nguồn dùng hợp đồng `String` trong quá trình đọc.

Đây là lý do mấu chốt của quy tắc cấm trực tiếp `new List<String>[n]`: ngôn ngữ không muốn cấp phát hợp pháp một mảng có vẻ mang cam kết type argument mà runtime array store check không thể bảo đảm.

## 9. Khai báo `List<String>[]` hợp lệ nhưng việc khởi tạo có giới hạn

Nhiều người học Java gặp một nghịch lý:

```java
List<String>[] groups; // Hợp lệ.
```

nhưng:

```java
// Cố ý không hợp lệ:
groups = new List<String>[10];
```

Điều này hoàn toàn nhất quán: type checker cho phép **kiểu biến** là một array of parameterized type trong nhiều ngữ cảnh, nhưng biểu thức tạo mảng mới đòi **component type reifiable**.

Một biến `List<String>[]` có thể xuất hiện khi:

- Được truyền vào phương thức từ một API cũ.
- Được hình thành từ một thao tác cast không được kiểm tra đầy đủ.
- Đi qua một API generic varargs, nơi compiler xử lý bằng quy tắc riêng.
- Là phần của dữ liệu nội bộ của một thư viện phải tương thích với mảng.

Không nên suy luận rằng mọi đối tượng được gán cho biến ấy đều an toàn. Sự tồn tại của type annotation ở biến không khôi phục `<String>` trong runtime component type.

### Dùng raw array có được không?

Ví dụ:

```java
java.util.List[] raw = new java.util.List[2];
```

Là cú pháp cho phép tạo mảng có component type raw `List`, nhưng **raw type bỏ kiểm tra generic** ở các thao tác. Nếu cố gán raw array vào `List<String>[]`, compiler có thể cảnh báo unchecked conversion, và rủi ro của ví dụ heap pollution vẫn còn.

Tránh coi `new List[n]` hoặc ép `(List<String>[]) new List[n]` là “mẹo sửa triệt để”. Nó chỉ đưa trách nhiệm an toàn kiểu ra khỏi sự chứng minh của compiler.

## 10. Giải pháp thông thường: `List<List<String>>`

Đối với ứng dụng thực tế cần “một tập các danh sách chuỗi”, ta thường **không cần array**. Dùng collection lồng nhau:

```java
List<List<String>> groups = new ArrayList<>();
```

Compiler giữ được quan hệ kiểu ở cả hai cấp:

- Outer list nhận các phần tử thuộc `List<String>`.
- Inner list nhận các phần tử `String`.
- Không cần tạo array component type non-reifiable.

Tạo tệp **`ListOfListsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class ListOfListsDemo {
    public static void main(String[] args) {
        List<List<String>> groups = new ArrayList<>();

        groups.add(new ArrayList<>(List.of("Java", "Kotlin")));
        groups.add(new ArrayList<>(List.of("Spring")));

        groups.get(1).add("Micronaut");

        for (List<String> group : groups) {
            System.out.println(String.join(", ", group));
        }

        System.out.println(groups.size());
        System.out.println(groups.get(0).get(1));
    }
}
```

Biên dịch/chạy:

```bash
javac ListOfListsDemo.java
java ListOfListsDemo
```

Stdout:

```text
Java, Kotlin
Spring, Micronaut
2
Kotlin
```

Nếu thử thêm một danh sách số:

```java
// Cố ý không hợp lệ, đặt trong method trên:
groups.add(List.of(10, 20));
```

compiler không cho vì outer list yêu cầu `List<String>`, không phải `List<Integer>`.

**Ưu điểm:** Thiết kế đơn giản, generic type checking đầy đủ ở mã nguồn, thêm/bớt nhóm dễ dàng, không cần cast hay cảnh báo unchecked.

**Giới hạn:** Generic vẫn bị erasure tại runtime. Nếu dữ liệu đi qua raw type hoặc unchecked cast, vẫn có thể tạo heap pollution. Lợi thế là các thao tác thông thường trong code được kiểm tra generic không tạo ra đường đi array covariance gây xung đột.

## 11. Nếu số nhóm cố định thì sao?

Một số lý do người ta muốn dùng mảng là:

- Số slot cố định.
- Truy cập bằng chỉ số.
- Dùng API hiện có yêu cầu `Object[]` hoặc array.
- Cần một container có kích thước không đổi sau khởi tạo.

Để giữ kiểm tra kiểu mà không cần generic array creation, có thể dùng một `List<List<String>>` với cấu trúc ngoài không thể thay đổi:

```java
import java.util.List;

class FixedGroups {
    static List<List<String>> create() {
        return List.of(
                List.of("A", "B"),
                List.of("C")
        );
    }
}
```

Đây là class minh họa không có `main`. `List.of` trả các danh sách không cho sửa cấu trúc bằng API chuẩn. Điều này phù hợp với những nhóm dữ liệu ít thay đổi và không buộc bạn phải tạo mảng generic.

Nếu cần thay nội dung từng nhóm nhưng muốn giữ số lượng nhóm cố định, có thể dùng một outer list không sửa cấu trúc nhưng chứa các inner list có thể sửa, hoặc thiết kế object đại diện cho từng nhóm. Hãy phân biệt “số phần tử cố định”, “phần tử không đổi” và “nội dung collection bên trong không đổi”; chúng không đồng nghĩa.

### `List.copyOf` và kiểm tra dữ liệu

`List.copyOf(...)` tạo bản sao list không thể thay đổi qua API chuẩn và từ chối phần tử `null`. Nhưng như mọi generics Java thông thường, nó không “đọc lại” type argument runtime để xác nhận một object raw đang chứa đúng `String`. Nếu nguồn chưa tin cậy, cần kiểm tra dữ liệu thực tế trước khi xây cấu trúc.

## 12. Dùng `List<?>[]` khi chỉ cần các danh sách không biết phần tử

Trong một số API, ta thật sự cần mảng mà mỗi phần tử là một danh sách **không cần đồng nhất type argument**.

Khi đó:

```java
List<?>[] groups = new List<?>[3];
```

là một giải pháp có chủ ý. Nó mang ý nghĩa:

- Mảng có ba vị trí, mỗi vị trí có thể tham chiếu tới bất kỳ `List<?>` nào.
- Khi đọc `groups[i].get(j)`, kiểu an toàn là `Object` nếu không biết thêm thông tin.
- Không thể gọi `groups[i].add("hello")` tùy ý, bởi compiler không biết element type của inner list.
- Có thể thay cả slot bằng một danh sách khác, ví dụ `groups[0] = List.of("A")` rồi `groups[0] = List.of(1)`, vì cả hai đều là `List<?>`.

Nếu nghiệp vụ yêu cầu mỗi vị trí là `List<String>`, kiểu wildcard này **quá rộng**. Chọn container phù hợp với hợp đồng, đừng dùng `List<?>[]` và cast trở lại `List<String>[]` chỉ để compiler im lặng.

## 13. Type variable `T` cũng không là reifiable component type

```java
class GenericBuffer<T> {
    // T[] elements = new T[10]; // Lỗi generic array creation.
}
```

Một type variable `T` không trở thành runtime class cụ thể khi generic được tham số hóa. Nếu phải tạo `T[]`, hãy để caller truyền `IntFunction<T[]>` như `String[]::new`, hoặc token `Class<T>` để reflection tạo mảng đúng component. `(T[]) new Object[n]` không tương đương tạo `String[]`.

Trọng tâm ở đây vẫn là **mảng của `List<String>`**: ngay cả một factory cũng không hợp thức hóa `List<String>[]::new` do component non-reifiable. Việc thiết kế, kiểm tra length và null policy cho factory thuộc bài riêng về `IntFunction<T[]>` và `Class<T>`.

## 14. Reflection tạo mảng của `List`, không tạo runtime `List<String>`

`Array.newInstance(List.class, 2)` tạo một array có runtime component `java.util.List`. Nó có thể chứa `List.of("A")` và `List.of(123)` vì cả hai đều là `List`. Không có class literal `List<String>.class` để yêu cầu JVM kiểm tra đối số generic `String` ở mọi slot.

```java
Object array = java.lang.reflect.Array.newInstance(List.class, 2);
java.lang.reflect.Array.set(array, 0, List.of("A"));
java.lang.reflect.Array.set(array, 1, List.of(123));
System.out.println(array.getClass().getComponentType().getName());
// java.util.List
```

Chỉ dùng reflection/token để tạo **đúng runtime component class có thật**. Nếu nghiệp vụ đòi toàn bộ list con chứa chuỗi, kiểm tra dữ liệu/ownership và chọn `List<List<String>>` hoặc wrapper thay vì khẳng định mảng raw `List[]` bảo đảm `<String>`.

## 15. Liên hệ với generic varargs và cảnh báo heap pollution

Cú pháp varargs:

```java
static void receive(java.util.List<String>... groups) {
    // ...
}
```

có bề ngoài giống một tham số `List<String>[]`. Điều này không đồng nghĩa Java cho phép code của bạn gọi `new List<String>[n]`.

Tại lời gọi varargs, compiler có quy tắc sinh và sử dụng mảng cho tham số, bao gồm cảnh báo khi element type non-reifiable. Từ đó tồn tại nguy cơ **heap pollution** nếu method hoặc caller thao tác không an toàn qua mảng.

### Ví dụ không an toàn, chạy được nhưng có thể cảnh báo

Tệp **`UnsafeVarargsDemo.java`**:

```java
import java.util.List;

public class UnsafeVarargsDemo {

    static void unsafe(List<String>... groups) {
        Object[] alias = groups;
        alias[0] = List.of(99);

        String text = groups[0].get(0);
        System.out.println(text);
    }

    public static void main(String[] args) {
        try {
            unsafe(List.of("safe"));
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac UnsafeVarargsDemo.java
java UnsafeVarargsDemo
```

Stdout (không tính các cảnh báo biên dịch):

```text
ClassCastException
```

`javac` có thể in ghi chú cảnh báo generic/unchecked trong quá trình biên dịch; đó **không phải stdout của chương trình**. Để xem rõ các cảnh báo:

```bash
javac -Xlint:unchecked -Xlint:varargs UnsafeVarargsDemo.java
```

Phân tích:

- Varargs parameter được triển khai thông qua array.
- `Object[] alias = groups` hợp lệ do array covariance.
- `alias[0] = List.of(99)` lưu một `List<Integer>` vào slot mà mã nguồn method kỳ vọng là `List<String>`.
- Runtime component chỉ có thể biết `List`, nên không chặn kiểu phần tử `Integer`.
- Khi `groups[0].get(0)` được dùng như `String`, phép cast thất bại.

Đừng dùng `@SafeVarargs` để tắt cảnh báo trong method **không an toàn** này. Annotation đó là lời cam kết của người viết rằng triển khai thực sự không có thao tác gây heap pollution trên varargs parameter.

## 16. Khi nào `@SafeVarargs` phù hợp?

Một method generic varargs có thể an toàn nếu nó **không ghi sai kiểu vào array varargs**, không công khai/thả tham chiếu tới array cho nơi khác có thể sửa không an toàn, và dùng dữ liệu theo hợp đồng.

Ví dụ **`SafeVarargsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class SafeVarargsDemo {

    @SafeVarargs
    static <T> List<T> flatten(List<? extends T>... batches) {
        List<T> result = new ArrayList<>();

        for (List<? extends T> batch : batches) {
            result.addAll(batch);
        }

        return List.copyOf(result);
    }

    public static void main(String[] args) {
        List<String> words = flatten(
                List.of("A"), List.of("B", "C"));

        System.out.println(words);
        System.out.println(words.size());
    }
}
```

Biên dịch/chạy:

```bash
javac SafeVarargsDemo.java
java SafeVarargsDemo
```

Stdout:

```text
[A, B, C]
3
```

Ở đây `flatten` chỉ đọc từng list để tạo một `ArrayList<T>` mới, không gán phần tử sai vào mảng varargs và không tiết lộ array để nơi khác chỉnh sửa. `List.copyOf` trả về danh sách không cho sửa cấu trúc qua API chuẩn.

`@SafeVarargs` có những ràng buộc về vị trí được áp dụng: ví dụ Java 17 cho phép trên constructor và method static/final, cũng như private instance methods theo những điều kiện ngôn ngữ quy định. **Nó không thể được gắn tùy tiện lên bất kỳ method nào**, và người viết chịu trách nhiệm đảm bảo triển khai thật sự an toàn.

**Không dùng annotation như mẹo để hợp thức hóa `new List<String>[]`.** Nó phục vụ một ngữ cảnh riêng: lời hứa về an toàn của việc sử dụng generic varargs.

## 17. Dùng mảng thường của wrapper thay vì mảng của kiểu tham số hóa

Nếu chương trình thực sự cần hiệu năng truy cập theo chỉ số hoặc tương tác với API yêu cầu array, một cách thiết kế rõ hơn là dùng **một loại wrapper không tham số hóa với thành phần runtime reifiable**.

Ví dụ cần các nhóm chuỗi có số slot cố định. Tạo tệp **`GroupWrapperArrayDemo.java`**:

```java
import java.util.List;

public class GroupWrapperArrayDemo {

    record StringGroup(List<String> elements) {
        StringGroup {
            elements = List.copyOf(elements);
        }
    }

    public static void main(String[] args) {
        StringGroup[] groups = new StringGroup[2];

        groups[0] = new StringGroup(List.of("A", "B"));
        groups[1] = new StringGroup(List.of("C"));

        System.out.println(groups.length);
        System.out.println(groups[0].elements().get(1));
        System.out.println(groups.getClass()
                .getComponentType().getSimpleName());
    }
}
```

Biên dịch/chạy:

```bash
javac GroupWrapperArrayDemo.java
java GroupWrapperArrayDemo
```

Stdout:

```text
2
B
StringGroup
```

`StringGroup` là một record có **class runtime cụ thể**. JVM có thể tạo và kiểm tra mảng `StringGroup[]`. Bên trong wrapper, `List<String>` vẫn được compiler kiểm tra theo generic contract.

Đây không phải cách tự động xóa mọi rủi ro: raw/unchecked access vẫn có thể làm hỏng dữ liệu nếu thiết kế có lỗ hổng. Tuy vậy, wrapper tách bạch ý nghĩa nghiệp vụ và tránh nhu cầu tạo mảng với một parameterized component type bị cấm.

### Khi nào nên chọn wrapper?

- Mỗi phần tử array đại diện một khái niệm nghiệp vụ độc lập, không chỉ là một list bất kỳ.
- Cần đặt validation/copy dữ liệu ở điểm tạo object.
- API bắt buộc nhận array và muốn runtime component class cụ thể.
- Muốn code dễ đọc hơn một unchecked cast trên `List<String>[]`.

Với phần lớn ứng dụng thuần collection, `List<List<String>>` vẫn đơn giản hơn.

## 18. So sánh toàn diện các cách tổ chức dữ liệu

| Thiết kế | Tạo trực tiếp? | Kiểm tra generic ở mã nguồn | Runtime component check |
|---|---|---|---|
| `String[]` | Có | Không cần tham số generic | Kiểm tra `String` |
| `List<String>[]` với `new List<String>[n]` | **Không** | Khai báo mong `List<String>` | Không thể kiểm tra `<String>` ở array store |
| `List<?>[]` với `new List<?>[n]` | Có | Wildcard giữ hạn chế generic khi truy cập inner list | Chỉ kiểm tra đối tượng là `List` |
| Raw `List[]` | Có, không khuyến khích cho code mới | Mất kiểm tra type argument ở đường raw | Kiểm tra raw `List` |
| `List<List<String>>` | Có | Kiểm tra cả hai cấp khi dùng typed API | Không dùng array store checks |
| `StringGroup[]` với wrapper không generic | Có | Inner `List<String>` được kiểm tra ở code thông thường | Kiểm tra `StringGroup` |

Chọn theo yêu cầu thật:

1. **Cần tập các danh sách đồng nhất chuỗi?** Dùng `List<List<String>>`.
2. **Cần mảng của các danh sách bất kỳ?** Dùng `List<?>[]` và chấp nhận kiểu phần tử chưa biết.
3. **Cần array runtime với loại nghiệp vụ cụ thể?** Cân nhắc một wrapper/record không tham số hóa.
4. **Cần `T[]` trong generic utility?** Truyền factory hoặc class token cho component type reifiable.
5. **Đang xử lý API legacy buộc dùng cast?** Khoanh vùng unchecked operation, kiểm tra nguồn dữ liệu và ghi rõ invariant. Không coi cast là phép kiểm tra generic runtime.

## 19. Những sai lầm phổ biến

**Sai lầm 1 — “Không thể khai báo biến `List<String>[]`.”** Sai. Có thể khai báo kiểu biến này. Điều bị cấm là *trực tiếp tạo* mảng `new List<String>[n]` với component type non-reifiable.

**Sai lầm 2 — “Java cấm mọi mảng có kiểu generic.”** Sai. `List<?>[]` là ví dụ hợp lệ vì wildcard không giới hạn giúp component type reifiable theo đặc tả.

**Sai lầm 3 — “Nếu `List<String>` và `List<Integer>` khác nhau ở compile time, JVM chắc chắn kiểm tra được ở array store.”** Sai. Kiểu phần tử generic đã bị xóa ở runtime; mảng chỉ kiểm tra raw `List`.

**Sai lầm 4 — “`new List[n]` giải quyết đầy đủ vấn đề.”** Sai. Raw `List[]` có thể tạo được, nhưng không đảm bảo `String` và có thể dẫn tới unchecked warnings, heap pollution.

**Sai lầm 5 — “`@SuppressWarnings("unchecked")` biến cast thành an toàn.”** Sai. Annotation chỉ kiểm soát cảnh báo biên dịch; nó không thêm runtime generic type checks.

**Sai lầm 6 — “`List<?>[]` tương đương `List<String>[]`.”** Sai. Mỗi slot `List<?>` có thể nhận một danh sách với bất kỳ type argument nào; không có cam kết `String`.

**Sai lầm 7 — “Mảng của `List<?>` không còn runtime type checking.”** Sai. Runtime vẫn kiểm tra được một object có phải `List` hay không; nó chỉ không kiểm tra type argument của mỗi list.

**Sai lầm 8 — “`ArrayStoreException` và `ClassCastException` luôn xuất hiện tại cùng vị trí.”** Sai. Store check của mảng reifiable có thể báo lỗi **lúc gán**; generic heap pollution thường bộc lộ **lúc đọc/cast**.

**Sai lầm 9 — “`new T[n]` hợp lệ khi `T extends Number`.”** Sai. `T` vẫn là type variable, không phải class component reifiable trong array creation trực tiếp.

**Sai lầm 10 — “Reflection có thể tạo `List<String>[]` bằng `List<String>.class`.”** Sai. `List<String>.class` không tồn tại; `Array.newInstance(List.class,n)` chỉ dùng component raw `List`.

**Sai lầm 11 — “Varargs `List<String>...` luôn an toàn vì compiler tự tạo array.”** Sai. Generic varargs có thể tạo heap pollution nếu method gán sai kiểu hoặc làm lộ array.

**Sai lầm 12 — “`@SafeVarargs` chỉ cần thêm để tắt warning.”** Sai. Đây là cam kết an toàn của người viết method và không được dùng cho một thân method gây heap pollution.

**Sai lầm 13 — “Array covariance và generic invariance mâu thuẫn hoàn toàn.”** Không. Đây là hai quy tắc được thiết kế cho các cơ chế khác nhau. Mảng bảo vệ thao tác store thông qua runtime component checks, còn invariance góp phần giữ an toàn generic ở compile time.

**Sai lầm 14 — “Wrapper hoặc `List<List<String>>` làm generics trở thành reified.”** Không. Chúng đưa ra cấu trúc dữ liệu và hợp đồng an toàn dễ quản lý hơn, không đổi cơ chế type erasure chung của Java.

## 20. Bài tập tự kiểm tra có đáp án

**Câu 1.** Dòng nào hợp lệ?

```java
List<String>[] a;              // A
List<String>[] b = new List<String>[2]; // B
List<?>[] c = new List<?>[2];  // C
```

**Đáp án:** A và C hợp lệ; B lỗi `generic array creation`. Giả sử import `java.util.List` và đặt trong một ngữ cảnh khai báo tương ứng.

**Câu 2.** Vì sao `String[]` có thể bị `ArrayStoreException` khi gán `Integer` qua `Object[]`?

**Đáp án:** Object mảng có component type runtime `String`, nên JVM kiểm tra và từ chối `Integer` tại thời điểm store.

**Câu 3.** Vì sao object `List.of(42)` được lưu vào `Object[]` alias của một `List<?>[]` mà không gây `ArrayStoreException`?

**Đáp án:** Runtime component type chỉ là `java.util.List`; `List.of(42)` tạo object triển khai `List`, phù hợp raw runtime type. JVM không so sánh generic type argument của mỗi list.

**Câu 4.** Có thể ép `(List<String>[]) new List<?>[2]` không?

**Đáp án:** Có thể xuất hiện unchecked cast theo compiler, nhưng nó **không chứng minh** những slot chứa `List<String>`. Không dùng nó như một bảo đảm an toàn kiểu.

**Câu 5.** Dùng cấu trúc gì cho một tập động các danh sách chuỗi?

**Đáp án:** `List<List<String>>` thường là lựa chọn trực tiếp, dễ bảo trì và được compiler kiểm tra ở cả hai cấp trong code có kiểu rõ ràng.

**Câu 6.** Có thể trực tiếp `new T[10]` bên trong `class Bucket<T>` không?

**Đáp án:** Không, vì type variable `T` không phải runtime component class reifiable. Một array factory `IntFunction<T[]>` có thể cho phép caller cung cấp constructor reference của kiểu component thật.

**Câu 7.** `@SafeVarargs` có thể đặt lên method ghi `List<Integer>` vào array of `List<String>` rồi coi là an toàn không?

**Đáp án:** Không. Đó là hành vi tạo heap pollution. Annotation phải phản ánh sự an toàn thật của triển khai.

**Câu 8.** Một biến `List<?>[]` có thể chứa `List<String>` ở slot 0 và `List<Integer>` ở slot 1 không?

**Đáp án:** Có. Wildcard biểu diễn type argument chưa biết ở từng list.

**Câu 9.** `List<String>[]` và `List<?>[]` có cùng runtime component class sau erasure không?

**Đáp án:** Nếu một object array được tạo qua raw/wildcard component và một reference `List<String>[]` trỏ đến nó bằng unchecked operation, runtime component class vẫn là `List`. Điều này chính là giới hạn mà compiler ngăn khi cấm trực tiếp generic array creation.

**Câu 10.** `List<String>[]::new` có dùng làm factory cho generic array trong Java 17 không?

**Đáp án:** Không. Nó vẫn đòi tạo array có component type `List<String>` non-reifiable; hãy dùng kiểu dữ liệu khác thay vì kỳ vọng constructor reference bỏ qua quy tắc array creation.

## 21. Thực hành tổng hợp: lựa chọn giải pháp trước khi viết code

**Tình huống A — Một lớp học có nhiều nhóm, mỗi nhóm có danh sách tên học viên.** Chọn `List<List<String>>`. Khi thêm nhóm hoặc tên, compiler có thể chặn việc đưa `Integer` vào danh sách tên.

**Tình huống B — Công cụ nhận nhiều loại list và chỉ muốn thống kê số phần tử từng list.** `List<?>[]` có thể phù hợp nếu API thật sự cần mảng; khi duyệt, đọc phần tử như `Object` và không giả định kiểu cụ thể.

**Tình huống C — Một thư viện yêu cầu `StringGroup[]` vì cần mảng object có component class xác định.** Tạo record/class `StringGroup` mang `List<String>` bên trong; runtime kiểm tra được `StringGroup` khi thay phần tử mảng.

**Tình huống D — Một generic helper cần cấp phát `T[]` với `T` chưa biết trước.** Truyền `IntFunction<T[]>` hoặc `Class<T>` phù hợp. Khi type argument không reifiable, hãy đánh giá lại cấu trúc thay vì cast unchecked một mảng raw rồi kỳ vọng JVM biết đối số generic.

**Tình huống E — Một API legacy trả `Object[]` hoặc raw array và caller muốn `List<String>[]`.** Cần hiểu dữ liệu nguồn, kiểm tra từng đối tượng và từng phần tử nếu cần chứng minh hợp đồng. Dùng một collection typed mới thường tốt hơn ép nguyên array với unchecked cast.

### Quy trình quyết định

1. Bạn **có thật sự cần mảng** hay chỉ cần một collection có khả năng truy cập chỉ số?
2. Nếu cần mảng, **component type có reifiable không**?
3. Nếu muốn `List<String>[]`, bạn có thể thay bằng `List<List<String>>` không?
4. Nếu bắt buộc tương thích API nhận array, `List<?>[]` có phản ánh đúng hợp đồng **không biết kiểu phần tử** không?
5. Nếu cần đồng nhất `String`, có thể dùng wrapper reifiable hoặc một API generic không dựa trên generic array creation không?
6. Có đoạn raw/unchecked nào làm mất chứng minh kiểu không, và ranh giới kiểm tra dữ liệu ở đâu?

Chỉ dùng giải pháp có unchecked cast nếu không có một hợp đồng khác đơn giản hơn và bạn kiểm soát toàn bộ dữ liệu liên quan.

## 22. Tổng kết và nguồn chuẩn

**Không thể trực tiếp tạo `new List<String>[]` vì `List<String>` là non-reifiable type**, trong khi Java yêu cầu component type của biểu thức tạo mảng phải reifiable. Java arrays là covariant và có runtime store checks; Java Generics dùng type erasure, nên runtime mảng không thể phân biệt đầy đủ `List<String>` với `List<Integer>`. Cho phép tạo mảng đó trực tiếp sẽ khiến compiler cấp một cam kết kiểu mà JVM không thể xác minh khi các tham chiếu rộng hơn được sử dụng.

Có thể **khai báo** biến `List<String>[]`, nhưng tạo trực tiếp `new List<String>[n]` bị cấm. `new List<?>[n]` được phép vì wildcard không giới hạn là reifiable, song không hứa rằng mọi list đều chứa String. Một unchecked cast có thể tạo nguy cơ heap pollution và `ClassCastException` về sau.

Trong code mới, **`List<List<String>>` là lựa chọn mặc định tốt** cho một tập các danh sách chuỗi. Khi thật sự cần array, chọn component type reifiable (`StringGroup[]`) hoặc wildcard phù hợp với hợp đồng, thay vì tắt cảnh báo để ép kiểu không được xác minh.

Tài liệu tham khảo:

- [Java Language Specification §4.7 — Reifiable Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.7): định nghĩa loại kiểu có đủ thông tin runtime.
- [Java Language Specification §10.5 — Array Store Exception](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html#jls-10.5): runtime array store checks.
- [Java Language Specification §15.10.1 — Array Creation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.10.1): giới hạn component type của biểu thức tạo mảng.
- [Java Language Specification §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): cách tính kiểu sau xóa.
- [Java Language Specification §4.5 — Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5): quan hệ và kiểm tra parameterized types.
- [Java Language Specification §9.6.4.7 — `@SafeVarargs`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.4.7): yêu cầu đối với annotation an toàn varargs.
- [Java SE 17 `java.util.Collections`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html): tiện ích collections dùng trong các lựa chọn thay thế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-arrays-004">Khác biệt giữa khai báo biến mảng generic và biểu thức tạo mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Khác biệt giữa khai báo biến mảng generic và biểu thức tạo mảng trong Java

## 1. Vấn đề: “Khai báo được nhưng new không được” có mâu thuẫn không?

Một trong những tình huống gây ngạc nhiên khi kết hợp Java Generics với array:

~~~java
import java.util.List;

class Example {
    List<String>[] values;       // HỢP LỆ: khai báo biến/trường.

    void initialize() {
        // values = new List<String>[3]; // LỖI: generic array creation.
    }
}
~~~

Dấu ngoặc vuông `[]` đều xuất hiện trong hai chỗ, nhưng hai biểu thức làm **hai việc khác nhau**:

- `List<String>[] values;` **khai báo một biến hoặc trường có kiểu mảng**, tức phần tử của nó được mô tả ở compile time là `List<String>`. Câu lệnh chỉ thông báo kiểu cho compiler, **không tạo một đối tượng array runtime**.
- `new List<String>[3]` yêu cầu JVM **tạo một array object**, có kiểu thành phần runtime. Tham số kiểu `String` trong `List<String>` không được giữ đầy đủ ở runtime do type erasure, nên Java cấm kiểu biểu thức tạo mảng này.

Vì vậy, Java hoàn toàn có thể cho phép một **kiểu dùng trong khai báo** nhưng không cho phép **biểu thức trực tiếp tạo array của kiểu ấy**. Không có sự mâu thuẫn: quy tắc khai báo và quy tắc tạo đối tượng phục vụ những bước khác nhau.

Các câu hỏi chính sẽ được giải đáp:

1. Tại sao `List<String>[]` và `T[]` hợp lệ trong khai báo, nhưng `new List<String>[n]` và `new T[n]` lỗi?
2. Tại sao `new List<?>[n]` lại hợp lệ?
3. Có thể gán thẳng `new List<?>[n]` cho `List<String>[]` không?
4. Nếu dùng unchecked cast để tạo mảng generic, điều gì có thể sai ở runtime?
5. Những lựa chọn an toàn hơn gồm những gì, và khi nào dùng `List<List<String>>` hoặc nhận array từ caller?

## 2. Cần tách ba khái niệm: array type, biến và array creation expression

### 2.1. Array type là một kiểu

`String[]` là một array type có phần tử String.

`List<String>[]` là một array type có phần tử là một parameterized type `List<String>`.

`T[]` trong một generic class hoặc generic method là một array type có phần tử là **type variable T**.

Các kiểu này có thể xuất hiện trong chữ ký trường, biến cục bộ, tham số hoặc kiểu trả về:

~~~java
import java.util.List;

class Inventory<T> {
    T[] elements;                // Hợp lệ.
    List<String>[] groups;       // Hợp lệ.

    void accept(List<T>[] input) {  // Hợp lệ ở mức khai báo.
        // Có thể xử lý input theo kiểu đã khai báo.
    }

    List<T>[] expose() {         // Hợp lệ ở mức khai báo.
        return null;             // Chỉ minh họa chữ ký.
    }
}
~~~

Đoạn trên **không tạo** `List<T>[]` nào. Trường mảng instance khi chưa gán có giá trị mặc định null. Việc một chữ ký generic array hợp lệ không có nghĩa compiler sẽ tự biết cách tạo nó.

### 2.2. Một biến array chưa chắc có đối tượng array

~~~java
List<String>[] declared = null; // Hợp lệ.
~~~

`declared` có kiểu tĩnh `List<String>[]`, nhưng giá trị hiện tại là null. Compiler cho phép khai báo vì chưa phải sinh một array object có component type `List<String>` ở runtime.

Ngược lại, câu lệnh sau cần tạo đối tượng:

~~~java
// List<String>[] created = new List<String>[2];
// Lỗi compile-time: generic array creation.
~~~

### 2.3. Array creation expression phải có component type cho runtime

Mỗi Java array có một **runtime component type** để JVM kiểm soát việc gán phần tử. Với:

~~~java
String[] strings = new String[2];
~~~

component type runtime là `java.lang.String`. Nếu mảng thực sự là String[] mà bị gán phần tử Integer qua alias Object[], JVM từ chối bằng `ArrayStoreException`.

Với:

~~~java
// new List<String>[2]
~~~

JVM không sở hữu một runtime class riêng “List của String” để kiểm tra một phần tử List<?> vừa được gán có chứa String hay không. Vì thế Java không cho phép tạo array trực tiếp với component type không-reifiable như `List<String>`.

## 3. Quy tắc reifiable type giải thích sự khác biệt

**Reifiable type** là kiểu có đủ thông tin cần thiết ở runtime theo quy tắc Java. Các trường hợp hữu ích trong bài này:

- Kiểu không generic: `String`, `Integer`, `Runnable`.
- Raw type của generic class/interface: `List`, `Map`.
- Parameterized type chỉ có **unbounded wildcard arguments**, chẳng hạn `List<?>`, `Map<?, ?>`.
- Array type có component type reifiable, chẳng hạn `String[]` hoặc `List<?>[]`.

Trái lại:

- `List<String>` không reifiable.
- `List<Integer>` không reifiable.
- `List<? extends Number>` không reifiable vì wildcard có cận trên cụ thể, không phải unbounded `?`.
- `Map<String, List<Integer>>` không reifiable.
- Type variable `T` thông thường không phải kiểu có runtime component class cụ thể để dùng với `new T[n]`.

So sánh:

| Biểu thức | Java 17 | Lý do |
| --- | --- | --- |
| `new String[3]` | Hợp lệ | String là reifiable. |
| `new Integer[3]` | Hợp lệ | Integer là reifiable. |
| `new List[3]` | Hợp lệ, raw use | Raw List là reifiable, nhưng làm mất generic checking ở những chỗ sử dụng raw. |
| `new List<?>[3]` | Hợp lệ | List<?> là reifiable. |
| `new Map<?, ?>[3]` | Hợp lệ | Mọi type argument là unbounded wildcard. |
| `new List<String>[3]` | Không hợp lệ | List<String> không reifiable. |
| `new List<Integer>[3]` | Không hợp lệ | List<Integer> không reifiable. |
| `new List<? extends Number>[3]` | Không hợp lệ | Type argument không phải unbounded wildcard. |
| `new Map<String, Integer>[3]` | Không hợp lệ | Map<String,Integer> không reifiable. |
| `new T[3]` trong generic class | Không hợp lệ | T không có runtime component type xác định cho array creation. |

**Không nhầm `List<?>` với raw `List`.** `List<?>` là một parameterized type hợp lệ với wildcard, trong khi raw `List` bỏ type argument. Hai array creation expressions `new List<?>[3]` và `new List[3]` đều có thể biên dịch, nhưng kiểu tĩnh của kết quả khác nhau, kéo theo cách kiểm tra khi lập trình tiếp theo khác nhau.

## 4. Vì sao mảng kiểm tra kiểu ở runtime còn generics chủ yếu ở compile time?

Java array là **covariant**: nếu String là subtype của Object, thì String[] cũng là subtype của Object[].

~~~java
String[] strings = new String[1];
Object[] alias = strings; // Hợp lệ vì array covariance.
~~~

Nhưng array object vẫn mang component type runtime là String. Vì thế:

~~~java
Object[] alias = new String[1];

try {
    alias[0] = Integer.valueOf(7);
} catch (ArrayStoreException error) {
    System.out.println("Rejected non-String element");
}
~~~

JVM phát hiện đối tượng Integer không thể nằm trong String[] và ném `ArrayStoreException` **tại thời điểm gán**.

Generic parameterized types trong Java nhìn chung **invariant**:

~~~java
List<String> strings = new ArrayList<>();

// List<Object> objects = strings; // Không biên dịch.
List<?> unknown = strings;        // Hợp lệ.
~~~

Sự invariant này giúp compiler ngăn việc thêm Integer vào List<String> qua alias List<Object>. Nhưng do type erasure, runtime không phân biệt tổng quát `ArrayList<String>` và `ArrayList<Integer>` bằng hai runtime classes.

Nếu `new List<String>[n]` được cho phép, array covariance có thể tạo đường đưa một List<Integer> vào array tưởng như chỉ chứa List<String>. Với runtime component type chỉ kiểm tra được List, array store check có thể bỏ qua khác biệt type arguments. Đó là lý do **generic array creation bị cấm với component type không reifiable**.

## 5. Thí nghiệm lý thuyết: nếu new List<String>[] được phép thì sao?

Đây là **mã cố ý không hợp lệ** để giải thích thiết kế ngôn ngữ:

~~~java
// KHÔNG BIÊN DỊCH:
List<String>[] strings = new List<String>[1];

Object[] alias = strings;
alias[0] = List.of(123);

String value = strings[0].get(0);
~~~

Điểm nguy hiểm giả định:

1. Biến strings hứa rằng mọi phần tử array là List<String>.
2. Do array covariance, alias có kiểu Object[] nhưng trỏ đến cùng array.
3. Nếu JVM chỉ thấy runtime component type List, lệnh gán List<Integer> qua alias có thể vượt qua kiểm tra array store.
4. Sau đó compiler tin `strings[0].get(0)` là String.
5. Giá trị thực tế là Integer, dẫn đến `ClassCastException` ở thời điểm đọc.

Java không cho xây array trực tiếp bằng cú pháp generic không reifiable ngay từ bước đầu, qua đó giảm khả năng có mảng tuyên bố sai về type arguments.

**Lưu ý:** chương trình có thể vẫn tạo tình huống tương tự nếu chủ động dùng **unchecked cast**. Lệnh cấm generic array creation không thể ngăn mọi mã không an toàn, đặc biệt raw types và unchecked casts.

## 6. Khai báo List<String>[] hoàn toàn hợp lệ

Các khai báo:

~~~java
List<String>[] a;
List<String>[] b = null;
List<Integer>[][] matrix;
~~~

đều hợp lệ nếu đặt trong một ngữ cảnh Java tương ứng và có import `java.util.List`. Chúng chỉ thiết lập kiểu tĩnh; không phải array creation.

Một method có thể **nhận** hoặc **trả** kiểu array generic:

~~~java
static void inspect(List<String>[] rows) {
    for (List<String> row : rows) {
        System.out.println(row.size());
    }
}

static List<String>[] identity(List<String>[] rows) {
    return rows;
}
~~~

Việc những chữ ký trên hợp lệ không chứng minh method có thể tự khởi tạo `new List<String>[n]`. Để có một array object tương thích, method phải **nhận từ caller**, lấy từ API hợp lệ, hoặc dựa trên cách tạo đặc biệt có giả định và rủi ro rõ ràng.

Ví dụ với một biến chưa gán:

~~~java
List<String>[] rows;
// System.out.println(rows.length); // Không biên dịch: biến cục bộ chưa được gán.
~~~

Đừng nhầm “khai báo hợp lệ” với “biến đã có giá trị dùng được”. Trong Java, biến cục bộ phải được khởi tạo trước khi đọc.

## 7. Tại sao new List<?>[n] hợp lệ?

`List<?>` là **parameterized type với unbounded wildcard**, được Java xem là reifiable. Do đó:

~~~java
List<?>[] buckets = new List<?>[3];

buckets[0] = List.of("Java");
buckets[1] = List.of(10, 20);
buckets[2] = List.of(true);
~~~

Đoạn mã hợp lệ: mỗi phần tử của array là một List nhưng có thể có **kiểu phần tử khác nhau**. Điều đó khớp đúng với ý nghĩa wildcard: ta không tuyên bố một loại phần tử duy nhất.

Đọc dữ liệu:

~~~java
Object value = buckets[0].get(0);
System.out.println(value); // Java.
~~~

Giá trị lấy từ `List<?>` an toàn khi xem là Object. Compiler không cho kết luận ngay rằng nó là String nếu chưa kiểm tra bổ sung.

Một cách khởi tạo ngắn:

~~~java
List<?>[] buckets = {
    List.of("Java"),
    List.of(123)
};
~~~

Array initializer như trên cũng có thể tạo một array chứa List<?> theo ngữ cảnh đã khai báo, không phải `new List<String>[]`.

### Wildcard array không bảo đảm kiểu của mọi phần tử nằm sâu bên trong

~~~java
List<?>[] buckets = new List<?>[2];
buckets[0] = List.of("A");
buckets[1] = List.of(42);
~~~

Cả hai hợp lệ vì runtime component type của array là List. Mỗi List bên trong có thể mang kiểu phần tử khác nhau. `List<?>[]` vì thế **không thể dùng để thay thế an toàn cho `List<String>[]` khi nghiệp vụ yêu cầu mọi List đều chứa String**.

## 8. Không được gán trực tiếp List<?>[] thành List<String>[]

Đây là câu hỏi thường gặp sau khi học `new List<?>[n]`:

~~~java
// List<String>[] specific = new List<?>[3]; // Không biên dịch.
~~~

Compiler không cho gán vì `List<?>[]` có thể chứa List<Integer>, còn `List<String>[]` hứa các phần tử array là List<String>. Không có conversion an toàn cho lời gán trực tiếp này.

Thử trên Java 17 cho lỗi đại ý:

~~~text
incompatible types: List<?>[] cannot be converted to List<String>[]
~~~

Điều **có thể** làm nhưng không nên coi là an toàn mặc định là unchecked cast:

~~~java
@SuppressWarnings("unchecked")
List<String>[] risky = (List<String>[]) new List<?>[3];
~~~

Ở runtime, cast có thể được chấp nhận vì array object thực chất có component runtime tương ứng với List. Tuy nhiên cast không kiểm tra type argument String của từng List, vì thế `risky` có thể mang một cam kết kiểu chưa được chứng minh.

Các đoạn dùng unchecked cast như vậy chỉ nên tồn tại trong một utility rất hẹp, được document rõ bất biến, và phải bảo đảm không có đường ghi khiến các phần tử array sai type argument.

## 9. Ví dụ hoàn chỉnh Java 17+: khai báo, wildcard array, unchecked cast và lỗi runtime

Lưu chương trình sau vào **GenericArrayDemo.java**:

~~~java
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

public class GenericArrayDemo {
    static <T> T[] makeArray(Class<T> componentType, int length) {
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(componentType, length);
        return result;
    }

    static <T> T[] copy(T[] input) {
        return Arrays.copyOf(input, input.length);
    }

    public static void main(String[] args) {
        // Khai báo hợp lệ dù không tạo array.
        List<String>[] declared = null;
        System.out.println("declared=" + declared);

        // Tạo được mảng reifiable List<?>.
        List<?>[] wildcard = new List<?>[2];
        wildcard[0] = List.of("Java");
        wildcard[1] = List.of(42);
        System.out.println(
                "wildcard=" + Arrays.toString(wildcard)
        );
        System.out.println(
                "wildcardType=" +
                wildcard.getClass().getComponentType().getName()
        );

        // Tạo array từ runtime Class token khi component type reifiable.
        String[] names = makeArray(String.class, 2);
        names[0] = "Java";
        names[1] = "Kotlin";
        String[] duplicate = copy(names);
        System.out.println(
                "copy=" + Arrays.toString(duplicate)
        );

        // Unchecked cast: hợp lệ về cú pháp nhưng KHÔNG tự bảo đảm
        // mọi List trong array đều chứa String.
        @SuppressWarnings("unchecked")
        List<String>[] risky = (List<String>[]) new List<?>[2];

        risky[0] = List.of("safe");
        Object[] alias = risky;

        // Runtime array chỉ biết component là List.
        alias[1] = List.of(42);

        System.out.println(
                "riskyClass=" +
                risky.getClass().getComponentType().getName()
        );

        try {
            String bad = risky[1].get(0);
            System.out.println(bad);
        } catch (ClassCastException exception) {
            System.out.println("polluted=ClassCastException");
        }

        // Đối chứng: JVM kiểm tra component String của String[].
        Object[] actualStrings = new String[1];

        try {
            actualStrings[0] = 42;
        } catch (ArrayStoreException exception) {
            System.out.println("arrayStore=ArrayStoreException");
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac --release 17 -Xlint:unchecked GenericArrayDemo.java
java GenericArrayDemo
~~~

Stdout **chính xác**:

~~~text
declared=null
wildcard=[[Java], [42]]
wildcardType=java.util.List
copy=[Java, Kotlin]
riskyClass=java.util.List
polluted=ClassCastException
arrayStore=ArrayStoreException
~~~

### Giải thích output theo thứ tự

**`declared=null`:** biến có kiểu `List<String>[]` tồn tại nhưng chưa tham chiếu array object nào. Đây là khai báo hợp lệ.

**`wildcard=[[Java], [42]]`:** array chứa hai List có phần tử khác nhau. Kiểu `List<?>[]` diễn đạt đúng sự chưa biết này.

**`wildcardType=java.util.List`:** component runtime type là List, không phải List<String> hay List<Integer>. Điều này giúp hiểu vì sao một array store check không xác minh phần tử generic bên trong.

**`copy=[Java, Kotlin]`:** phương thức `makeArray(String.class,2)` tạo String[] bằng reflection; `Arrays.copyOf` dùng runtime array type của input để tạo bản sao thích hợp.

**`riskyClass=java.util.List`:** sau unchecked cast, array vẫn có component runtime là List. Cast không biến array thành loại runtime đặc biệt chứa List<String>.

**`polluted=ClassCastException`:** alias Object[] gán `List<Integer>` vào vị trí array vốn được biến risky nhìn như List<String>[]. JVM chấp nhận vì đối tượng được gán là List. Nhưng khi đọc phần tử đầu tiên với kiểu tĩnh String, compiler/JVM kiểm tra và ném ClassCastException vì thực tế là Integer.

**`arrayStore=ArrayStoreException`:** với array thực sự có component String, JVM từ chối ngay việc gán Integer qua alias Object[]. Đây là khác biệt giữa cơ chế array runtime và type erasure của generic arguments.

**Mục đích giáo dục:** đoạn dùng unchecked cast là ví dụ **cố ý không an toàn**. Không nên sao chép mẫu đó vào API production khi chưa chứng minh được bất biến thành phần, giới hạn alias và điểm ghi.

## 10. Các biểu thức tạo mảng bị Java từ chối: kiểm tra compile time

Đoạn sau **cố ý không biên dịch**, tách khỏi chương trình `GenericArrayDemo.java`:

~~~java
import java.util.List;

class InvalidArrays<T> {
    List<String>[] a = new List<String>[3];
    List<? extends Number>[] b =
            new List<? extends Number>[3];
    T[] c = new T[3];
    List<String>[] d = new List<?>[3];
}
~~~

Biên dịch Java 17:

~~~bash
javac --release 17 InvalidArrays.java
~~~

Lỗi tương ứng:

| Dòng | Nhóm lỗi | Vì sao |
| --- | --- | --- |
| `new List<String>[3]` | `generic array creation` | List<String> không reifiable. |
| `new List<? extends Number>[3]` | `generic array creation` | Wildcard có cận, không phải unbounded wildcard reifiable. |
| `new T[3]` | `generic array creation` | T là type variable, chưa có runtime component type. |
| `List<String>[] d = new List<?>[3]` | `incompatible types` | Không chuyển trực tiếp List<?>[] sang List<String>[] một cách an toàn. |

**Quan trọng:** không phải mọi chỗ xuất hiện kiểu generic array đều bị cấm. Các **vế trái** `List<String>[] a`, `T[] c` đều là những khai báo kiểu hợp lệ. Lỗi phát sinh ở **biểu thức khởi tạo bên phải**, hoặc vì phép gán hai array types không tương thích.

## 11. Khai báo T[] hợp lệ nhưng new T[n] không hợp lệ

Trong một generic class:

~~~java
class Buffer<T> {
    private T[] values; // Hợp lệ.

    Buffer(T[] supplied) {
        values = supplied; // Hợp lệ: nhận array do caller cung cấp.
    }

    T get(int index) {
        return values[index];
    }
}
~~~

Cách sử dụng:

~~~java
Buffer<String> buffer =
        new Buffer<>(new String[] {"A", "B"});

String first = buffer.get(0); // "A".
~~~

Caller đã tạo **String[]** bằng một component type reifiable, rồi cung cấp cho Buffer<T>. Generic class không phải tự suy diễn runtime class của T từ type erasure.

Ngược lại, không thể viết:

~~~java
class BadBuffer<T> {
    // T[] values = new T[3]; // Không biên dịch.
}
~~~

Lý do: Java không thể dùng một type variable T để tạo trực tiếp một runtime array có component type cụ thể, dù sau đó người dùng có thể tạo `BadBuffer<String>` hoặc `BadBuffer<Integer>`.

### Một cận kiểu cũng không tự làm new T[n] hợp lệ

~~~java
class BoundedBuffer<T extends Number> {
    // T[] items = new T[3]; // Không biên dịch.
}
~~~

Dù T bị xóa về Number khi biên dịch, `Number[]` không tương đương `Integer[]`, `Double[]` hoặc mọi T cụ thể. Nếu mục đích thật sự chỉ cần lưu Number, có thể cân nhắc tạo `new Number[n]` với thiết kế dựa trên Number; còn nếu API hứa T[], cần bảo đảm component type T bằng phương án thích hợp.

## 12. Giải pháp phụ thuộc nơi có thông tin component type

Bài toán này trước hết phải phân biệt **khai báo** mảng generic với **cấp phát**. Khi cần một array object thật, chọn cách mang được runtime component type phù hợp:

1. **Nhận mảng đã được caller tạo:** `static <T> T[] copy(T[] source) { return Arrays.copyOf(source, source.length); }` tận dụng component class thực của source.
2. **Nhận `Class<T>`:** dùng `Array.newInstance(componentType, length)`, cô lập cast vì reflection trả `Object`. Ví dụ `String.class` tạo `String[]`, chứ không tạo `Object[]` giả dạng.
3. **Nhận factory:** `IntFunction<T[]>` với `String[]::new` để caller trực tiếp quyết định runtime component class.
4. **Không buộc dùng mảng:** `List<List<String>>` mô tả nhiều nhóm chuỗi; `List<?>[]` chỉ hợp lý khi type arguments thật sự chưa biết và API cần array.

```java
static <T> T[] cloneOf(T[] source) {
    return java.util.Arrays.copyOf(source, source.length);
}

static <T> T[] make(int n, java.util.function.IntFunction<T[]> factory) {
    return factory.apply(n);
}
```

**Đừng đánh đồng token `List.class` với `List<String>.class` không tồn tại.** Một `Class<T>` hoặc factory có thể cung cấp runtime class của `String`, nhưng không làm type arguments của `List<String>` reifiable. Bài chuyên về array factories xử lý thêm null, factory trả sai kích thước, primitive arrays và `Collection.toArray`.

## 13. Vì sao không nên dùng (T[]) new Object[n] một cách máy móc?

Một mẹo phổ biến:

~~~java
class Risky<T> {
    @SuppressWarnings("unchecked")
    T[] create(int n) {
        return (T[]) new Object[n];
    }
}
~~~

Compiler có thể chấp nhận kèm cảnh báo unchecked bị suppress, nhưng runtime array thực tế luôn là **Object[]**. Nếu bên gọi cần `String[]`, việc JVM kiểm tra/cast kết quả cụ thể thành String[] có thể thất bại:

~~~java
Risky<String> factory = new Risky<>();
// String[] names = factory.create(2);
// Có thể ném ClassCastException: Object[] không phải String[].
~~~

**Vấn đề không chỉ ở kiểu phần tử lúc đọc:** ngay bản thân array object mang component type Object khác String. Do array runtime reification, Object[] không thể thay thế String[] khi caller yêu cầu String[].

Đôi khi thư viện dùng Object[] nội bộ và cast ở một ranh giới đã được thiết kế đặc biệt. Khi đó không được công bố tùy tiện API trả T[] nếu không đáp ứng runtime component type mà caller kỳ vọng. Một hướng dễ chứng minh hơn là lưu `Object[]` thật, kiểm tra từng giá trị lúc đọc, hoặc dùng `Class<T>` token để tạo array có component phù hợp.

## 14. Generic varargs có liên quan gì?

Một khai báo varargs generic:

~~~java
static <T> void printAll(T... values) {
    for (T value : values) {
        System.out.println(value);
    }
}
~~~

`T...` về bản chất là tham số array type `T[]`. Java cho phép khai báo đó, nhưng khi lời gọi cần tạo array chứa một **non-reifiable type**, compiler có thể cảnh báo unchecked hoặc `possible heap pollution from parameterized vararg type`.

Ví dụ:

~~~java
static void showAll(List<String>... groups) {
    for (List<String> group : groups) {
        System.out.println(group);
    }
}
~~~

Đây là khai báo hợp lệ, nhưng nó có thể làm đường truyền giá trị non-reifiable qua array. Không nên xem cú pháp varargs như một cách lách an toàn quy tắc generic array creation.

Một phương thức `@SafeVarargs` chỉ nên dùng khi tác giả **thực sự chứng minh được thân phương thức không thực hiện những thao tác không an toàn trên varargs array**. Với Java 17, annotation có thể dùng trên static/final methods, constructors và private instance methods theo các điều kiện của ngôn ngữ. Nó **không tự thêm kiểm tra runtime** và không hợp pháp hóa thao tác gây heap pollution.

Khi API chỉ cần một tập hợp các phần tử generic, tham số `List<T>` hoặc `Collection<T>` thường giúp tránh một số tình huống rủi ro và cảnh báo liên quan varargs array.

## 15. Phân biệt ArrayStoreException và ClassCastException

Hai ngoại lệ này thường bị nhầm trong bài toán generic array:

| Tình huống | Điều kiểm tra thất bại | Ngoại lệ |
| --- | --- | --- |
| Gán Integer vào String[] qua Object[] | Runtime component type String của array | `ArrayStoreException` tại thời điểm gán. |
| Gán List<Integer> vào array có component runtime List | Component List vẫn phù hợp; store được chấp nhận | Không bắt buộc phát lỗi tại thời điểm gán. |
| Đọc Integer từ List nằm trong `List<String>[]` bị nhiễm và dùng như String | Cast phần tử Object thành String khi dùng | `ClassCastException` tại thời điểm đọc/sử dụng. |
| Cast array thật `Object[]` thành `String[]` | Runtime array class không tương thích | `ClassCastException` ngay ở cast array. |

Ví dụ:

~~~java
Object[] a = new String[1];
// a[0] = 5;             // Khi chạy: ArrayStoreException.

Object[] b = new List<?>[1];
b[0] = List.of(5);       // Hợp lệ: đối tượng là List.
~~~

Kết luận: **array kiểm tra kiểu phần tử trực tiếp của array ở runtime**, còn generic arguments bên trong collection có thể không được kiểm tra sâu. Cast được compiler chèn khi sử dụng generic value có thể phát hiện sai lệch về sau.

## 16. Những sai lầm thường gặp

### “Có thể khai báo List<String>[] tức là new List<String>[] cũng phải được”

Sai. Việc khai báo kiểu mảng không đòi tạo runtime array. `new` đòi runtime component type phù hợp; `List<String>` không reifiable.

### “List<?>[] có thể gán cho List<String>[] vì dấu ? bao quát String”

Sai. `List<?>` biểu thị kiểu phần tử chưa biết, không khẳng định String. Java không cho gán trực tiếp `List<?>[]` sang `List<String>[]`.

### “Unchecked cast đã sửa vấn đề cho mảng generic”

Sai. `(List<String>[]) new List<?>[n]` không thay đổi runtime component thành List<String>. Nó chỉ tuyên bố một kiểu tĩnh cụ thể mà JVM không thể kiểm tra đầy đủ. Mã có thể ném ClassCastException khi dùng dữ liệu sai kiểu.

### “new T[n] bị từ chối thì cứ thay bằng new Object[n] và ép T[]”

Nguy hiểm. Array thật là Object[], còn caller có thể yêu cầu String[]; cast array sẽ thất bại. Cần xem hợp đồng API và runtime component type.

### “new List<?>[n] cho phép đọc mọi phần tử như String”

Sai. Wildcard cho phép đọc một List có type argument chưa biết, nhưng phần tử lấy ra chỉ an toàn ở mức Object nếu không xác minh thêm.

### “ArrayStoreException và ClassCastException là một”

Sai. ArrayStoreException kiểm tra **lúc gán vào array**. ClassCastException kiểm tra **phép ép kiểu không tương thích**, bao gồm khi dùng dữ liệu generic được kỳ vọng là String nhưng thực chất là Integer.

### “Thấy generic array trong tham số method là chắc chắn lỗi”

Sai. `void read(List<String>[] input)` và `<T> void read(T[] input)` có thể là chữ ký hợp lệ; vấn đề nằm ở cách tạo/nhận một array có runtime representation phù hợp và an toàn.

### “Generics không tạo được mảng nên Java không thể làm việc với mảng generic”

Sai. Java có thể khai báo kiểu array chứa generic elements, nhận mảng từ caller, trả mảng theo chữ ký generic, dùng reifiable wildcard arrays, hoặc tạo mảng của lớp runtime đã biết bằng Class token. Điều bị cấm là **một số array creation expressions có component type non-reifiable**, không phải mọi thao tác liên quan mảng.

## 17. Quy trình chọn cách làm

Khi gặp một yêu cầu “cần mảng chứa giá trị generic”, kiểm tra theo thứ tự:

1. **Có thực sự bắt buộc dùng mảng không?** Nếu chỉ muốn danh sách các `List<String>`, `List<List<String>>` thường an toàn và đơn giản.
2. **Component type có reifiable không?** Với String, Integer, List<?> hoặc Map<?, ?>, có thể dùng array creation trực tiếp.
3. **Chỉ đang khai báo biến/tham số/kiểu trả về?** `List<String>[]` hay `T[]` hợp lệ; không cần tạo mảng ở dòng đó.
4. **Đang gọi `new ...[n]`?** Component type phải tuân theo quy tắc array creation, không thể dùng List<String>, List<? extends Number> hay type variable T tùy tiện.
5. **Có runtime component class được caller cung cấp?** Cân nhắc `Class<T>` với `Array.newInstance`.
6. **Có thể nhận mảng từ caller không?** Dùng `T[]` input và sao chép khi cần, tránh tự dựng một array có component type giả định.
7. **Có unchecked cast không?** Xác định rõ runtime array class thật, ai được ghi qua alias, và chuyện gì xảy ra nếu đọc dữ liệu sai generic arguments.
8. **Có varargs non-reifiable không?** Kiểm tra compiler warnings; chỉ sử dụng `@SafeVarargs` nếu phương thức thực sự an toàn.
9. **Có đang dựa vào ArrayStoreException để kiểm tra generic arguments?** Điều đó không đúng: runtime array store chỉ kiểm tra component class/interface có thể nhìn thấy, không xác minh phần tử bên trong collection.
10. **Kiểm thử với compiler mục tiêu** (ví dụ `javac --release 17`) để tránh nhầm giữa lỗi biên dịch và ngoại lệ runtime.

## 18. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `List<String>[] rows;` hợp lệ nhưng `new List<String>[2]` bị từ chối?

**Đáp án:** Dòng đầu chỉ khai báo array type tại compile time; dòng sau yêu cầu tạo runtime array của non-reifiable component List<String>, không thể kiểm tra generic argument String tại array store.

**Câu 2.** `new List<?>[2]` có hợp lệ không? Điều đó cho phép thêm `List<Integer>` vào array không?

**Đáp án:** Có. List<?> là reifiable và có thể chứa các List với type arguments khác nhau. Từng List được đưa vào mảng đều tương thích với runtime component List.

**Câu 3.** Có gán trực tiếp `List<String>[] a = new List<?>[2];` được không?

**Đáp án:** Không. Kiểu List<?>[] không bảo đảm mỗi phần tử là List<String>; compiler báo incompatible types.

**Câu 4.** Nếu ép `(List<String>[]) new List<?>[2]`, lỗi chắc chắn xảy ra ngay không?

**Đáp án:** Không chắc. Cast có thể được chấp nhận vì array runtime vẫn là List[], nhưng cần unchecked conversion/cast và có nguy cơ heap pollution. Lỗi có thể xảy ra khi đọc dữ liệu không phải String.

**Câu 5.** `new T[2]` trong `class Buffer<T>` có hợp lệ không nếu T extends Number?

**Đáp án:** Không. Bound không cung cấp runtime component type cụ thể của T như Integer[] hay Double[].

**Câu 6.** Phương thức `<T> T[] copy(T[] source)` có nhất thiết sai không?

**Đáp án:** Không. Nó có thể nhận mảng thật đã được caller tạo với component type phù hợp; ví dụ `Arrays.copyOf` có thể tạo bản sao đúng runtime array class của nguồn.

**Câu 7.** Vì sao gán Integer vào `Object[] alias = new String[1]` ném ArrayStoreException, còn gán `List.of(42)` vào Object[] alias của List<?>[] có thể thành công?

**Đáp án:** Runtime component String không chấp nhận Integer. Runtime component List chấp nhận một List bất kể type argument bên trong; generic String không được xác minh tại array store.

**Câu 8.** `@SafeVarargs` có khiến array của List<String> trở nên reifiable không?

**Đáp án:** Không. Đây là annotation truyền đạt cam kết an toàn của phương thức đủ điều kiện, không bổ sung metadata runtime để kiểm tra generic arguments.

**Câu 9.** Nếu muốn cấu trúc nhiều nhóm chuỗi, nên ưu tiên `List<String>[]` hay `List<List<String>>`?

**Đáp án:** Nếu không có yêu cầu API bắt buộc là mảng, `List<List<String>>` thường phù hợp hơn vì có thể khởi tạo bình thường và compiler kiểm tra generic ở mỗi cấp; không cần generic array creation hoặc unchecked cast.

## 19. Ghi nhớ

**Kiểu khai báo không đồng nghĩa với khả năng tạo trực tiếp một đối tượng runtime của kiểu đó.** Java cho phép khai báo `List<String>[]` và `T[]`, nhưng cấm `new List<String>[n]` và `new T[n]` vì phần tử của những array creation expressions đó không có component type reifiable phù hợp.

`new List<?>[n]` hợp lệ vì unbounded wildcard là reifiable; tuy nhiên `List<?>[]` không chứng minh mỗi list chứa String. Mảng giữ runtime component type để phát `ArrayStoreException` khi cần, còn generic arguments thường bị xóa và lỗi sai kiểu có thể bộc lộ thành `ClassCastException` khi dữ liệu được sử dụng.

Trong thiết kế Java hiện đại, hãy ưu tiên **collection lồng nhau có kiểu rõ ràng**, nhận array phù hợp từ caller hoặc dùng `Class<T>` token khi bắt buộc tạo mảng của một component class reifiable. Chỉ dùng unchecked casts khi hiểu được toàn bộ ràng buộc runtime và phạm vi truy cập làm cho chúng thật sự an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-arrays-005">Trường hợp mảng với List&lt;?&gt; có thể tạo hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Trường hợp mảng với `List<?>` có thể tạo hợp lệ trong Java Generics

## 1. Câu hỏi trọng tâm

Trong Java, bạn không thể tạo trực tiếp mảng có kiểu thành phần `List<String>`:

```java
// Không biên dịch:
List<String>[] strings = new List<String>[3];
```

Nhưng đoạn sau **hợp lệ**:

```java
List<?>[] groups = new List<?>[3];
```

Vì sao cả hai đều liên quan Generics mà chỉ biểu thức thứ hai được phép? Câu trả lời là **reifiable type** (*kiểu có thể biểu diễn được tại runtime*) và cơ chế kiểm tra kiểu thành phần của mảng trên JVM.

`List<?>` là một kiểu tham số hóa mà **tất cả type arguments đều là wildcard không giới hạn**. Theo quy tắc Java, kiểu này là **reifiable**, tức đủ điều kiện làm kiểu thành phần cho biểu thức cấp phát mảng `new List<?>[n]`. Ngược lại, `List<String>` và `List<? extends Number>` không reifiable: JVM không thể xác minh các type argument cụ thể đó trong một phép kiểm tra thành phần của mảng.

Bài này giải thích chính xác:

- Vì sao `new List<?>[n]` được Java chấp nhận.
- Mảng thực sự nhớ gì tại runtime và `List<?>[]` cho phép đọc/ghi gì.
- Sự khác biệt giữa `List<?>[]`, raw `List[]`, `List<String>[]` và `List<List<String>>`.
- Rủi ro của covariance, alias kiểu `Object[]`, cast unchecked và heap pollution.
- Cách chọn cấu trúc dữ liệu và kiểm tra phần tử khi ứng dụng cần kiểu cụ thể.
- Cách biên dịch, chạy và đọc lỗi `generic array creation` trên Java 17.

Mục tiêu không phải tìm mẹo vượt qua compiler, mà là hiểu **vì sao một mảng các list không biết kiểu phần tử hợp lệ**, và giới hạn của sự bảo đảm đó.

## 2. Nhắc lại: kiểu mảng và kiểu generic được kiểm tra khác nhau

Một mảng Java có một **runtime component type**. Ví dụ:

```java
String[] names = new String[2];
```

JVM biết object được tạo là `String[]`, bất kể nó được tham chiếu qua biến `String[]`, `Object[]` hay `Object`. Nếu code cố ghi một `Integer` qua alias:

```java
Object[] alias = names;
alias[0] = 42; // ArrayStoreException khi chạy
```

runtime chặn việc ghi vì `Integer` không tương thích với component type thật `String`.

Nhưng collection generic không có một runtime class riêng cho mỗi type argument:

```java
List<String> words = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();
```

Hai object được tạo bằng `new ArrayList<>()` có cùng class runtime `ArrayList`; type arguments `String` và `Integer` được compiler dùng để kiểm tra source, không trở thành một class runtime chuyên biệt cho từng object.

Từ đây xuất hiện câu hỏi: nếu JVM chỉ nhìn thấy runtime `List`, làm sao kiểm tra mỗi phần tử của một mảng được công bố là `List<String>[]`? Nó không thể xác minh từng `String` trong những list ấy bằng array store check. Vì vậy Java cấm cấp phát trực tiếp mảng với component type generic **không reifiable**.

### 2.1. So sánh mảng và collection

| Thuộc tính | `String[]` | `List<String>` |
| --- | --- | --- |
| Kiểu thành phần/đối số tại source | `String` | `String` |
| Runtime có object class riêng thể hiện `String`? | Có: array class mang component `String` | Không có specialization riêng `List<String>` |
| Ghi qua alias rộng có được kiểm tra runtime? | Có, có thể ném `ArrayStoreException` | Không có store check theo type argument; raw alias có thể gây heap pollution |
| Quan hệ kiểu | Mảng tham chiếu là covariant | Generic Java thường invariant |
| Kiểm tra một object bằng kiểu tương ứng | `instanceof String[]` hợp lệ | Từ `Object`, `instanceof List<String>` không dùng để kiểm tra type argument runtime |
| Khi đọc sai kiểu có thể gặp lỗi gì? | Array store check thường chặn dữ liệu sai ngay khi ghi | `ClassCastException` có thể xuất hiện khi đọc dữ liệu đã bị nhiễm kiểu |

Đây là lý do không thể áp dụng một cách máy móc mọi quy tắc của `List<T>` cho `T[]`.

## 3. Reifiable type quyết định sự hợp lệ của `new List<?>[n]`

Trong Java, một số kiểu reifiable tiêu biểu gồm:

1. Primitive như `int`, `boolean`.
2. Class/interface không generic như `String`, `Runnable`.
3. Raw type như `List` và `Map`.
4. Parameterized type mà **mọi argument đều là unbounded wildcard `?`**, như `List<?>`, `Map<?, ?>`.
5. Array có component type reifiable, như `String[]`, `List<?>[]`, `List<?>[][]`.

Vì `List<?>` thuộc nhóm 4, `List<?>[]` thuộc nhóm 5. Do đó:

```java
List<?>[] groups = new List<?>[3]; // Hợp lệ
```

Compiler có thể phát sinh thao tác tạo mảng có runtime component class `java.util.List`. Không cần kiểm tra `String`, `Integer` hay subtype cụ thể nào cho type argument vì `?` **không hứa một type argument cụ thể**.

Trái lại:

```java
// Không hợp lệ:
List<String>[] words = new List<String>[3];

// Không hợp lệ:
List<? extends Number>[] numbers = new List<? extends Number>[3];

// Không hợp lệ trong method/class generic:
T[] values = new T[3];
```

Ba component types `List<String>`, `List<? extends Number>` và `T` không reifiable. Chỉ việc có `?` trong một kiểu **không đủ**: phải là unbounded wildcard theo điều kiện của reifiability.

### 3.1. Bảng phân loại

| Biểu thức tạo mảng | Biên dịch? | Lý do |
| --- | --- | --- |
| `new String[3]` | Có | `String` reifiable |
| `new Object[3]` | Có | `Object` reifiable |
| `new List[3]` | Có, có thể cảnh báo `rawtypes` | Raw `List` reifiable |
| `new List<?>[3]` | Có | `List<?>` reifiable |
| `new Map<?, ?>[3]` | Có | Tất cả type arguments đều là wildcard `?` |
| `new List<?>[2][4]` | Có | Component cuối `List<?>` reifiable |
| `new List<String>[3]` | Không | Argument `String` không phải `?` |
| `new List<Object>[3]` | Không | `List<Object>` cũng không reifiable |
| `new List<? extends Number>[3]` | Không | Wildcard có upper bound |
| `new List<? super Integer>[3]` | Không | Wildcard có lower bound |
| `new Map<String, ?>[3]` | Không | Một argument là `String`, không phải `?` |
| `new T[3]` | Không | Type variable `T` không reifiable |

**Quan trọng:** `List<?>` và `List<Object>` không đồng nghĩa. `List<?>` đại diện một list có kiểu phần tử chưa biết. `List<Object>` đại diện list mà bạn có thể thêm các object vào qua tham chiếu được khai báo đúng kiểu, nhưng Java Generics là invariant nên `List<String>` không phải `List<Object>`.

### 3.2. `List<?>[]` thực sự mang component runtime là gì?

Một thí nghiệm:

```java
List<?>[] groups = new List<?>[2];

System.out.println(groups.getClass().getTypeName());
System.out.println(groups.getClass().getComponentType().getTypeName());
```

Với Java 17, kết quả:

```text
java.util.List[]
java.util.List
```

`List<?>[]` là kiểu source giữ wildcard; array object runtime có component `List`. JVM bảo vệ việc ghi **một đối tượng có phải List không**, chứ không bảo vệ type argument của nội dung list.

Kết quả này không phải lỗi erasure; nó phù hợp chính xác với hợp đồng mà `List<?>[]` công bố: các ô mảng là những list, còn kiểu phần tử trong mỗi list **không được xác định**.

## 4. Cấp phát, gán và đọc mảng `List<?>[]`

Ví dụ:

```java
List<?>[] groups = new List<?>[3];

groups[0] = List.of("Java", "Generics");
groups[1] = List.of(10, 20);
groups[2] = List.of(true, false);
```

Cả ba phép gán hợp lệ:

- `groups[0]` chứa tham chiếu tới `List<String>`.
- `groups[1]` chứa `List<Integer>`.
- `groups[2]` chứa `List<Boolean>`.

Mỗi danh sách đều có thể được nhìn như `List<?>`. Mảng không yêu cầu tất cả list có cùng kiểu phần tử cụ thể.

### 4.1. Đọc phần tử qua wildcard

```java
Object first = groups[0].get(0);
System.out.println(first); // Java
```

Phương thức `get` của `List<?>` trả một giá trị mà compiler có thể xem là `Object`. Bạn **không** thể tự động nhận `String`:

```java
// Không biên dịch vì kết quả có kiểu chưa biết:
// String text = groups[0].get(0);
```

Mặc dù trong ví dụ bạn biết `groups[0]` vừa được gán `List.of("Java", ...)`, hợp đồng của mảng nói rằng ô này **có thể chứa một List thuộc kiểu phần tử khác** vào lúc khác. Thông tin đó không được bảo toàn ở kiểu tĩnh `List<?>`.

Nếu cần nhận `String`, hãy kiểm tra:

```java
Object value = groups[0].get(0);

if (value instanceof String text) {
    System.out.println(text.toUpperCase());
}
```

Hoặc dùng một adapter kiểm tra tất cả phần tử khi muốn tạo `List<String>` đáng tin.

### 4.2. Vì sao không thể `groups[0].add("new")`?

```java
// Không hợp lệ:
// groups[0].add("new");
```

Vì kiểu của `groups[0]` là `List<?>`: dấu `?` được compiler coi như một kiểu phần tử chưa biết. Có thể list thực là `List<Integer>`, nên compiler không cho thêm `String` tùy ý.

Một lời gọi `groups[0].add(null)` có thể được hệ thống kiểu chấp nhận vì `null` tương thích các kiểu tham chiếu, nhưng danh sách runtime có thể bất biến hoặc từ chối null. Các list tạo bằng `List.of(...)` là unmodifiable.

**Mảng có thể thay ô chứa một list khác**:

```java
groups[0] = List.of(99);
```

Điều đó hợp lệ, vì `List<Integer>` vẫn là `List<?>`. Nhưng bạn không được tùy tiện thêm `String` **vào list có kiểu phần tử chưa biết** thông qua tham chiếu `List<?>`.

### 4.3. Cấp phát và tạo nội dung ban đầu

Hai dạng đều hợp lệ:

```java
List<?>[] emptySlots = new List<?>[2];
```

Và:

```java
List<?>[] prepared = new List<?>[] {
    List.of("a", "b"),
    List.of(1, 2)
};
```

Dạng thứ nhất tạo hai ô mang giá trị `null` ban đầu. Dạng thứ hai tạo mảng chứa hai tham chiếu tới list đã có dữ liệu. Nếu dùng `List.of`, chính các list đó không cho phép sửa qua các phương thức cập nhật.

Bạn có thể dùng mảng rỗng:

```java
List<?>[] none = new List<?>[0];
```

Đây là mảng hợp lệ có độ dài 0, hữu ích khi API phải trả array mà chưa có kết quả.

## 5. `List<?>[]` khác raw `List[]` dù có cùng runtime class

Java cũng cho:

```java
List[] rawGroups = new List[2];
```

Lý do tạo mảng hợp lệ tương tự: **raw `List` là reifiable**. Tuy nhiên raw type bỏ nhiều kiểm tra generic tại source và có thể tạo cảnh báo `rawtypes` hoặc `unchecked`.

Ngược lại:

```java
List<?>[] safeGroups = new List<?>[2];
```

diễn đạt rằng mỗi ô chứa một `List` với kiểu phần tử chưa được biết; compiler vẫn bảo vệ quyền thêm phần tử khi bạn đọc list qua ô mảng.

Nếu hai mảng được tạo tương ứng:

```java
List<?>[] wildcard = new List<?>[2];
List[] raw = new List[2];

boolean sameRuntimeClass = wildcard.getClass() == raw.getClass();
```

`sameRuntimeClass` là `true` trên Java thông thường vì component runtime của cả hai đều là raw class/interface `List`.

Nhưng **giống class runtime không có nghĩa giống hợp đồng source**. Dùng `List<?>[]` khi không cần gọi member generic qua raw reference; chỉ dùng `List[]` trong ranh giới legacy với lý do rõ ràng.

### 5.1. `List<?>[]` không đồng nghĩa mảng “không có kiểu”

Cả mảng lẫn list bên trong vẫn có quy tắc:

- Ở cấp mảng, phần tử phải tương thích với component runtime `List` (hoặc `null`).
- Ở cấp `List<?>`, bạn có thể đọc phần tử dưới dạng `Object`, không thể ghi phần tử tùy ý qua tham chiếu wildcard.
- Không có cam kết về sự đồng nhất type arguments của các list ở vị trí khác nhau.
- Không có cam kết rằng list trong mảng là mutable, immutable, không null hoặc cùng độ dài.

Điều kiện reifiability cho phép **tạo** mảng; nó không tự cung cấp tất cả invariant nghiệp vụ.

## 6. Mảng có tính covariance và vẫn có store check

Mảng tham chiếu trong Java là covariant. Vì vậy:

```java
List<?>[] groups = new List<?>[2];
Object[] alias = groups;
```

Phép gán từ `List<?>[]` sang `Object[]` hợp lệ. Nhưng object runtime của `groups` vẫn là một mảng có component class `List`.

Nếu làm:

```java
alias[0] = "not a List";
```

JVM ném `ArrayStoreException`: `String` không thuộc component type `List`.

Ngược lại:

```java
alias[0] = List.of(42);
```

runtime cho phép, vì đối tượng là một `List`. Điều này vẫn đúng dù trước đó `groups[0]` từng chứa `List<String>`. Mảng không thể kiểm tra `String` trên nội dung từng list; `List<?>[]` vốn không hứa điều đó.

### 6.1. Có hai loại kiểm tra khác nhau

| Tình huống | Kiểm tra runtime | Kết quả có thể có |
| --- | --- | --- |
| Đưa `String` vào object array thực tế mang component `List` | JVM kiểm tra object là `List` | `ArrayStoreException` |
| Đưa `List<Integer>` vào mảng có component `List` | JVM kiểm tra object là `List` | Ghi được, không kiểm tra argument `Integer` |
| Đọc list chứa `Integer` qua alias khai báo sai `List<String>` | Compiler/JVM có thể cast kết quả đọc thành `String` | `ClassCastException` |
| Đọc list qua `List<?>` | Không tự cast phần tử thành `String` | Nhận `Object` |

Hai lỗi `ArrayStoreException` và `ClassCastException` không phải một. Lỗi thứ nhất liên quan kiểu phần tử **của mảng**, lỗi thứ hai có thể liên quan giả định generic sai **bên trong list**.

## 7. Cast từ `List<?>[]` sang `List<String>[]` không an toàn

Một số đoạn code cố vượt giới hạn tạo mảng generic bằng cast:

```java
@SuppressWarnings("unchecked")
static List<String>[] unsafeClaim(List<?>[] input) {
    return (List<String>[]) input;
}
```

Phép cast generic có thể sinh unchecked warning (được annotation trong ví dụ cố ý ẩn). JVM biết `input` là mảng các `List`, nhưng không thể kiểm tra mọi list có phần tử `String`.

Ví dụ:

```java
List<?>[] mixed = new List<?>[1];
mixed[0] = List.of(42);

List<String>[] claimed = unsafeClaim(mixed);
String text = claimed[0].get(0); // ClassCastException
```

Cast array không đi duyệt phần tử của từng list. Khi `get(0)` được dùng như `String`, phép kiểm tra `String` thất bại do giá trị thật là `Integer`.

Đừng dùng thủ thuật này để “sửa” lỗi `generic array creation`. Nó chỉ dời lỗi từ compile time tới runtime và có thể tạo **heap pollution** — tình trạng một biến kiểu tham số hóa không còn tham chiếu tới dữ liệu phù hợp với hợp đồng kiểu của nó.

### 7.1. Vì sao cast có thể qua mà giá trị vẫn sai?

Sau erasure, runtime component class của `List<?>[]` là `List`, cũng như biểu diễn runtime cần kiểm tra khi cast về `List<String>[]`. Phần `<String>` không có kiểm tra element type đầy đủ.

Trong mã Java đã được generic checking bảo vệ, `List<String>[]` nếu xuất hiện qua API legacy vẫn có nguy cơ được alias dưới dạng `Object[]` hoặc cast unchecked. Vì vậy chỉ có vẻ ngoài là `List<String>[]` không chứng minh từng list trong đó thật sự chứa String.

### 7.2. Nếu code cũ buộc trả `List<String>[]`?

Nếu một thư viện legacy có API yêu cầu mảng này:

1. Xác định vì sao API không thể đổi sang `List<List<String>>` hoặc một type-safe abstraction khác.
2. Xác định ai tạo, giữ và sửa mảng; có raw alias hoặc `Object[]` alias bên ngoài không?
3. Kiểm tra mọi list và mọi phần tử là `String` trước khi giao dữ liệu cho caller.
4. Nếu buộc cast unchecked, cô lập cast ở phạm vi rất nhỏ và tài liệu hóa invariant; không ẩn cảnh báo ở cấp class.
5. Ưu tiên bản sao và không đưa alias có quyền ghi không kiểm soát ra ngoài.
6. Kiểm thử cả phần tử không hợp lệ, list null và mutation sau khi trả kết quả.

Ngay cả khi validate tại một thời điểm, nếu nơi khác còn quyền sửa cùng list/mảng, invariant vẫn có thể bị phá về sau. Validation và ownership cần được xem cùng nhau.

## 8. Ví dụ Java 17 hoàn chỉnh và kết quả đã kiểm thử

Tạo file **`WildcardListArrayDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardListArrayDemo {
    static <T> List<T> checkedCopy(List<?> input, Class<T> elementType) {
        List<T> result = new ArrayList<>();
        for (Object item : input) {
            result.add(elementType.cast(item));
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    static List<String>[] unsafeClaim(List<?>[] input) {
        return (List<String>[]) input;
    }

    public static void main(String[] args) {
        List<?>[] rows = new List<?>[3];
        rows[0] = List.of("Java", "Generics");
        rows[1] = List.of(10, 20);
        rows[2] = List.of(true, false);

        System.out.println("array-type=" + rows.getClass().getTypeName());
        System.out.println("component="
                + rows.getClass().getComponentType().getTypeName());
        System.out.println("is-list-array=" + (rows instanceof List<?>[]));
        System.out.println("first-values=" + rows[0].get(0) + ","
                + rows[1].get(0) + "," + rows[2].get(0));
        System.out.println("checked=" + checkedCopy(rows[0], String.class));

        Object[] alias = rows;
        try {
            alias[1] = "not a List";
        } catch (ArrayStoreException e) {
            System.out.println("store=" + e.getClass().getSimpleName());
        }
        alias[1] = List.of(99);
        System.out.println("replaced=" + rows[1].get(0));

        List<String>[] claimed = unsafeClaim(rows);
        try {
            String value = claimed[1].get(0);
            System.out.println(value);
        } catch (ClassCastException e) {
            System.out.println("unchecked=" + e.getClass().getSimpleName());
        }

        List<?>[][] matrix = new List<?>[1][2];
        matrix[0][0] = List.of("left");
        matrix[0][1] = List.of(100);
        System.out.println("matrix=" + matrix[0][0].get(0) + ","
                + matrix[0][1].get(0));
    }
}
```

Biên dịch và chạy bằng JDK có hỗ trợ Java 17:

```bash
javac --release 17 -Xlint:all WildcardListArrayDemo.java
java WildcardListArrayDemo
```

Source trên biên dịch thành công, không có cảnh báo với lệnh lint đã nêu (unchecked cast cố ý minh họa đã được giới hạn tại `unsafeClaim`). Stdout chính xác:

```text
array-type=java.util.List[]
component=java.util.List
is-list-array=true
first-values=Java,10,true
checked=[Java, Generics]
store=ArrayStoreException
replaced=99
unchecked=ClassCastException
matrix=left,100
```

### 8.1. Đọc những dòng liên quan reifiability

`array-type=java.util.List[]` và `component=java.util.List` chứng minh rằng kiểu mảng được tạo có component runtime `List`, không phải `List<String>` hoặc `List<Integer>`.

`is-list-array=true` chứng minh kiểm tra kiểu mảng theo dạng reifiable `List<?>[]` là hợp lệ.

`first-values=Java,10,true` chứng minh một mảng có thể chứa các list với kiểu phần tử khác nhau; khi đọc qua `List<?>`, kết quả được dùng như `Object`.

### 8.2. Đọc các dòng kiểm tra an toàn

`checked=[Java, Generics]` xuất hiện do `checkedCopy` xác minh từng phần tử là `String` bằng `Class<String>.cast`. Adapter trả một list mới; không cần giả định mọi `List<?>` là `List<String>`.

`store=ArrayStoreException` là kết quả của việc đưa `String` vào mảng runtime có component `List`. Đây là lỗi trực tiếp ở **phần tử mảng**.

`replaced=99` chứng minh đưa `List<Integer>` qua cùng alias `Object[]` là hợp lệ, vì object mới vẫn là một `List`.

`unchecked=ClassCastException` cho thấy cast từ `List<?>[]` sang `List<String>[]` không hề xác minh được phần tử `String`; lỗi nổ khi đọc `Integer` như `String`.

`matrix=left,100` chứng minh mảng nhiều chiều `List<?>[][]` cũng có thể tạo, do component cuối `List<?>` reifiable.

### 8.3. Vì sao `checkedCopy` dùng `List.copyOf`?

Hàm:

```java
static <T> List<T> checkedCopy(List<?> input, Class<T> elementType) {
    List<T> result = new ArrayList<>();
    for (Object item : input) {
        result.add(elementType.cast(item));
    }
    return List.copyOf(result);
}
```

đảm bảo mọi phần tử được đưa vào result đã được runtime class token kiểm tra. `List.copyOf` tạo bản sao unmodifiable và **không chấp nhận null**. Nếu contract cho phép null hoặc cần mutable list, chọn cấu trúc copy khác và quy tắc validation rõ ràng.

`Class<T>` chỉ giúp kiểm tra kiểu runtime cụ thể như `String.class`. Nó không thể tự kiểm tra nested generic type `List<String>` ở một phần tử `List<List<String>>` mà không duyệt các cấp lồng nhau.

## 9. Ví dụ cố ý không biên dịch: giới hạn của array creation

Tạo file **`InvalidWildcardArray.java`**:

```java
import java.util.List;

class InvalidWildcardArray<T> {
    void invalid() {
        List<String>[] strings = new List<String>[2];
        List<? extends Number>[] numbers =
                new List<? extends Number>[2];
        T[] values = new T[2];
    }
}
```

Chạy:

```bash
javac --release 17 InvalidWildcardArray.java
```

`javac` từ chối cả ba biểu thức tạo mảng với thông báo cốt lõi:

```text
error: generic array creation
error: generic array creation
error: generic array creation
```

Lý do:

1. `List<String>` không reifiable vì type argument cụ thể `String`.
2. `List<? extends Number>` không reifiable vì `?` có upper bound, không phải wildcard hoàn toàn không giới hạn.
3. `T` không reifiable dù có thể có bound; erasure của `T` không xác định một component class tương ứng với từng lựa chọn `T` của caller.

Đừng nhầm **khai báo biến mảng** với **cấp phát mảng**. Java có thể cho khai báo:

```java
List<String>[] declaredOnly;
```

nhưng không cho tạo trực tiếp bằng `new List<String>[2]`. Một khai báo kiểu mảng generic chưa có object, chưa cung cấp đảm bảo nào về dữ liệu bên trong.

## 10. Dùng `List<?>[]` trong phương thức nhận và trả dữ liệu

Mảng wildcard không chỉ dùng được ở biến cục bộ:

```java
static List<?>[] createGroups() {
    return new List<?>[] {
        List.of("Java"),
        List.of(2026)
    };
}
```

Đây là method minh họa, đặt trong class có `import java.util.List;`. Mảng được tạo có component runtime `List`, và caller có thể quan sát các phần tử như `List<?>`.

Một method nhận mảng:

```java
static int countAll(List<?>[] groups) {
    int count = 0;
    for (List<?> group : groups) {
        count += group.size();
    }
    return count;
}
```

Hàm này không cần `<T>` vì chỉ dùng số phần tử của từng list, không quan tâm kiểu phần tử và không tạo quan hệ giữa các type arguments.

**Cẩn thận với `null`:** `new List<?>[n]` tạo các slot null ban đầu. Nếu gọi `group.size()` trên slot chưa gán sẽ gặp `NullPointerException`. Một API công khai có thể kiểm tra null hoặc có chính sách xem slot null là nhóm rỗng, tùy hợp đồng.

### 10.1. Varargs với `List<?>`

Một tham số varargs được biểu diễn thông qua mảng:

```java
static int total(List<?>... groups) {
    int count = 0;
    for (List<?> group : groups) {
        count += group.size();
    }
    return count;
}
```

Do kiểu thành phần `List<?>` reifiable, dạng varargs này không gặp chính cảnh báo *non-reifiable varargs parameter* thường thấy với `List<String>...` hoặc `List<T>...`.

Ví dụ gọi:

```java
int total = total(List.of("a", "b"), List.of(1, 2, 3));
// total bằng 5
```

Điều này không có nghĩa varargs array được phép sửa tùy tiện. Một varargs method còn có thể nhận một mảng đã tồn tại, trong khi list bên trong có thể immutable, null hoặc được chia sẻ với caller.

### 10.2. Đối chiếu với generic varargs

```java
static <T> void inspect(List<T>... groups) {
    // ...
}
```

Với `List<T>...`, component `List<T>` không reifiable, nên compiler có thể cảnh báo nguy cơ heap pollution. `@SafeVarargs` chỉ được dùng cho những method được Java cho phép gắn annotation và khi tác giả có cơ sở chứng minh body không thực hiện thao tác không an toàn với varargs array. Annotation **không biến** `List<T>` thành reifiable.

Nếu method không cần liên hệ một `T` cụ thể giữa các list, cân nhắc khai báo `List<?>...` thay vì `List<T>...`. Nếu cần giữ cùng một `T`, phải bảo toàn generic contract và xử lý varargs có chủ đích, không thay đại trà chỉ để xóa warning.

## 11. Khi nào nên dùng `List<List<String>>` thay vì array?

`List<?>[]` thích hợp khi yêu cầu chỉ là một **dãy cố định các list có kiểu phần tử chưa biết** hoặc phải tương tác với API cần array. Nó không phù hợp khi bạn cần đảm bảo tất cả các list con đều chứa `String`.

So sánh:

```java
List<?>[] mixed = new List<?>[2];
mixed[0] = List.of("a");
mixed[1] = List.of(42); // Hợp lệ
```

với:

```java
List<List<String>> groups = new ArrayList<>();
groups.add(List.of("a", "b"));
// groups.add(List.of(42)); // Lỗi compile-time
```

Cấu trúc `List<List<String>>` giữ type argument `String` trong hợp đồng source ở cả hai tầng. Khi code được kiểm tra generic và không có raw/unchecked alias phá hợp đồng, compiler từ chối một list chứa `Integer` tại nơi thêm.

Một số tiêu chí chọn:

| Nhu cầu | Nên cân nhắc | Lý do |
| --- | --- | --- |
| Cần đúng số slot, có thể thay cả list tại mỗi slot | `List<?>[]` | Array có kích thước cố định; slot nhận bất kỳ `List<?>` tương thích. |
| Các list có thể có nhiều kiểu phần tử khác nhau, chỉ đọc | `List<?>[]` hoặc `List<List<?>>` | Không hứa mọi phần tử cùng `T`. |
| Mọi list phải chứa String và cần thay đổi số nhóm | `List<List<String>>` | Giữ hợp đồng generic ở hai tầng; dễ thêm/bớt nhóm. |
| API bắt buộc trả array nhưng cần nhóm String type-safe | Xem xét API khác hoặc adapter có validation và ownership rõ | Không tạo trực tiếp `new List<String>[n]` bằng cú pháp Java hợp lệ. |
| Cần một mảng object thật với component `String` | `String[]` | Array runtime kiểm tra component `String`. |

`List<List<String>>` bản thân cũng **không reifiable**, nhưng có thể dùng để khai báo và tạo object `new ArrayList<>()` do không yêu cầu cấp phát một array có component `List<String>`. Tính an toàn của nó đến từ generic checking ở source, không phải JVM tạo class riêng cho `List<List<String>>`.

## 12. Những hiểu lầm phổ biến

### 12.1. “`new List<?>[n]` hợp lệ vì wildcard tắt kiểm tra kiểu”

Không đúng. `List<?>` vẫn là một kiểu generic được compiler kiểm tra. Chính vì không hứa type argument cụ thể, nó đủ điều kiện **reifiable** để tạo mảng; đồng thời compiler vẫn hạn chế thêm phần tử vào một `List<?>`.

### 12.2. “`List<?>[]` tương đương `List<Object>[]`”

Không. `List<Object>` là parameterized type không reifiable, nên không thể cấp phát `new List<Object>[n]`. Ngoài ra `List<String>` không phải subtype của `List<Object>`, nhưng có thể được nhìn như `List<?>`.

### 12.3. “`List<?>[]` chỉ được chứa list rỗng”

Không. Nó có thể chứa `List<String>`, `List<Integer>`, `List<Boolean>` hoặc các list tương ứng khác. `?` không giới hạn loại phần tử hiện có bên trong list; nó hạn chế điều bạn được phép **giả định và ghi** qua tham chiếu wildcard.

### 12.4. “ArrayStoreException sẽ kiểm tra `String` trong `List<String>`”

Không. Mảng runtime có component class `List`; store check xác minh object được gán có phù hợp `List`, chứ không duyệt các phần tử của list để kiểm tra `String`. Nếu cần bảo đảm `String`, viết validator ở mức phần tử.

### 12.5. “Một cast `(List<String>[]) array` làm dữ liệu type-safe”

Không. Cast có thể được compiler cho qua với unchecked warning nhưng không biến type arguments thành runtime component. Lỗi `ClassCastException` có thể xuất hiện muộn hơn ở chỗ đọc dữ liệu.

### 12.6. “Có wildcard `?` là luôn reifiable”

Không. `List<? extends Number>` và `List<? super Integer>` có wildcard **có giới hạn**, không thỏa điều kiện “mọi argument là unbounded wildcard”. `new List<? extends Number>[n]` bị compiler từ chối.

### 12.7. “Erasure biến mọi mảng thành `Object[]`”

Không. Runtime component type của `String[]` vẫn là `String`, của `List<?>[]` là `List`. Erasure áp dụng tới cách biểu diễn generic/type variables, không tự biến mọi array object đã tạo thành `Object[]`.

### 12.8. “Vì `new List<?>[n]` hợp lệ nên luôn là lựa chọn tốt nhất”

Không. Nếu ứng dụng muốn mọi nhóm cùng `List<String>`, một cấu trúc có generic element type rõ như `List<List<String>>` thường giúp compiler phát hiện lỗi sớm hơn. Chọn array khi thực sự cần array semantics hoặc tương thích API.

## 13. Quy trình sửa lỗi `generic array creation`

Nếu nhìn thấy:

```text
error: generic array creation
```

hãy kiểm tra có đang tạo một trong những dạng như:

```java
new List<String>[n]
new List<? extends Number>[n]
new T[n]
```

Sau đó tự hỏi:

1. **Component type có reifiable không?** `List<?>` có; `List<String>` hoặc `T` không.
2. **Có thật cần array không?** Nếu không, dùng `List<List<T>>` hoặc collection phù hợp.
3. **Các list có cần cùng một type argument cụ thể không?** Nếu có, `List<?>[]` không cung cấp invariant đó.
4. **API có thể nhận cấu trúc với wildcard không?** Nếu chỉ đọc các nhóm chưa biết kiểu, `List<?>[]` có thể diễn đạt đúng contract.
5. **Có dữ liệu từ legacy/raw hoặc unchecked cast không?** Nếu có, cần validate những phần tử mang kiểu cụ thể trước khi công bố kết quả type-safe.
6. **Ai sở hữu mảng và các list bên trong?** Nếu trả alias ra ngoài, caller có thể thay đổi slot; các list cũng có thể mutable.
7. **Có đủ test âm không?** Thử một object không phải List qua `Object[]` alias, một list có phần tử không mong đợi, slot null và mutation sau khởi tạo.

Đừng sửa lỗi bằng cách chuyển toàn bộ code sang raw type hay thêm một unchecked cast ngay tại chỗ cấp phát. Cách đó chỉ giấu vấn đề mất generic type information.

## 14. Bài tập thực hành

### Bài 1 — Phân loại cách cấp phát

Cho các dòng:

```java
List<?>[] a = new List<?>[2];
List<String>[] b = new List<String>[2];
List[] c = new List[2];
List<? extends Number>[] d = new List<? extends Number>[2];
```

Dòng nào cấp phát được?

<details>
<summary>Đáp án</summary>

`a` và `c` cấp phát được: `List<?>` và raw `List` reifiable. Dòng `c` có thể sinh warning `rawtypes`. `b` và `d` không hợp lệ vì `List<String>` và `List<? extends Number>` không reifiable.

</details>

### Bài 2 — Đọc phần tử

```java
List<?>[] rows = new List<?>[1];
rows[0] = List.of("Java");
```

Có thể gán trực tiếp `String text = rows[0].get(0);` không?

<details>
<summary>Đáp án</summary>

Không. Kiểu tĩnh của `rows[0]` là `List<?>`; `get(0)` được đọc dưới dạng `Object`. Nếu cần `String`, kiểm tra giá trị bằng `instanceof String` hoặc adapter đã kiểm tra phần tử. Việc vừa gán một `List<String>` không khiến kiểu khai báo của `rows[0]` đổi thành `List<String>`.

</details>

### Bài 3 — Runtime array store

```java
List<?>[] rows = new List<?>[1];
Object[] alias = rows;
alias[0] = "x";
```

Chuyện gì xảy ra?

<details>
<summary>Đáp án</summary>

`ArrayStoreException` tại lệnh ghi. Array object có component runtime `List`, không chấp nhận `String` như một phần tử của mảng.

</details>

### Bài 4 — Một list có `Integer` có thể vào mảng không?

```java
List<?>[] rows = new List<?>[1];
rows[0] = List.of(99);
```

<details>
<summary>Đáp án</summary>

Có, vì `List<Integer>` tương thích `List<?>`. Mảng không hứa các list trong đó phải chứa String. Đọc phần tử qua `rows[0].get(0)` cho một giá trị kiểu tĩnh là `Object`.

</details>

### Bài 5 — Vì sao `new List<?>[3]` có thể cấp phát nhưng `new List<? super Integer>[3]` không?

<details>
<summary>Đáp án</summary>

`List<?>` có unbounded wildcard, thuộc nhóm parameterized type reifiable. `List<? super Integer>` có wildcard với lower bound, không reifiable và bị từ chối khi dùng làm component của array creation.

</details>

### Bài 6 — Dữ liệu cần hai tầng String

Ứng dụng cần một tập các nhóm, mỗi nhóm chỉ có chuỗi, và số nhóm có thể thay đổi. Nên ưu tiên cấu trúc nào?

<details>
<summary>Đáp án</summary>

Thường chọn `List<List<String>>`. Nó giữ hợp đồng kiểu String qua hai tầng và dễ thêm/bớt nhóm. `List<?>[]` không hứa mọi phần tử của mỗi nhóm là String; kiểu mảng có các slot cố định, không tự thay đổi độ dài.

</details>

## 15. Kết luận

**`new List<?>[n]` hợp lệ vì `List<?>` là reifiable.** Mảng được tạo có runtime component class `List`; JVM có thể kiểm tra object được gán vào mỗi slot có thuộc `List` hay không. Dấu `?` không giới hạn không hứa type argument cụ thể nào, nên mảng có thể chứa `List<String>`, `List<Integer>` hoặc list thuộc kiểu phần tử khác.

Điều này **không** làm `List<String>[]` trở nên có thể tạo trực tiếp, không cho phép tự động đọc `String` từ `List<?>`, và không biến cast unchecked thành phép kiểm tra phần tử. Khi cần một mảng các list chưa biết kiểu, `List<?>[]` biểu đạt hợp đồng hợp lý. Khi cần invariant mỗi list đều chứa `T` cụ thể, hãy ưu tiên API giữ kiểu, collection lồng nhau hoặc adapter validate dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-arrays-006">Dùng Collection&lt;T&gt; thay cho mảng khi phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng `Collection<T>` thay cho mảng khi phù hợp trong Java Generics

## 1. Vì sao cần lựa chọn giữa Collection và mảng?

Khi học Java Generics, một tình huống hay gặp là muốn lưu nhiều phần tử cùng một kiểu `T`:

```java
class Group<T> {
    // Không hợp lệ:
    // private T[] items = new T[10];
}
```

Hoặc muốn lưu nhiều danh sách chuỗi:

```java
// Không hợp lệ:
java.util.List<String>[] groups = new java.util.List<String>[3];
```

Hai biểu thức `new T[10]` và `new List<String>[3]` đều gặp giới hạn của **generic array creation**. Type variable `T` và kiểu tham số hóa `List<String>` không phải các component type *reifiable* thích hợp để trực tiếp tạo mảng.

Nếu bài toán chỉ cần một tập phần tử, ta thường có thể thay bằng:

```java
class Group<T> {
    private final java.util.Collection<T> items =
            new java.util.ArrayList<>();
}
```

Và thay “mảng các danh sách chuỗi” bằng:

```java
java.util.List<java.util.List<String>> groups =
        new java.util.ArrayList<>();
```

Cả hai ví dụ mới đều hợp lệ. Compiler giữ được các ràng buộc generic ở mã nguồn mà không cần tạo mảng có component type không reifiable.

**Nguyên tắc của bài:** Chọn abstraction thể hiện thao tác cần thiết trước. Nếu chỉ cần thêm, duyệt, đếm hoặc kiểm tra thành viên, `Collection<T>` có thể đủ. Nếu cần thứ tự và truy cập chỉ số, dùng `List<T>`; nếu cần tính duy nhất, `Set<T>`; nếu cần hàng đợi, `Queue<T>`; và nếu thực sự cần ngữ nghĩa mảng/runtime component class hoặc API bắt buộc nhận array, tiếp tục dùng mảng với cách tạo phù hợp.

`Collection<T>` không phải một “mẹo” để làm Generics trở thành reified. Nó là một cách thiết kế dữ liệu thường **phù hợp hơn với hệ thống kiểu generic của Java** và loại bỏ nhu cầu tạo những mảng không thể được JVM kiểm tra đầy đủ.

## 2. Khác biệt cần giữ khi thay array bằng collection

Mảng `String[]` giữ component type `String` tại runtime; nó cho phép alias kiểu `Object[]` nhưng JVM kiểm tra mỗi phép ghi để từ chối `Integer` bằng `ArrayStoreException`. `Collection<String>` dùng **generic compile-time checks**; runtime `ArrayList<String>` và `ArrayList<Integer>` dùng cùng một `ArrayList` class do erasure. Raw alias vẫn có thể phá hợp đồng.

| Quyết định | Array | Collection generic |
| --- | --- | --- |
| Truy cập phần tử | Theo index, có `length` | Duyệt/iterator/`size()`; chỉ `List` mới có `get(index)` |
| Kích thước | Cố định sau tạo | Tùy implementation, có thể hỗ trợ thêm/xóa |
| Kiểu phần tử | Runtime component class và store check | Compiler theo dõi type argument ở API typed |
| Thứ tự | Theo index | `Collection` không hứa thứ tự; `List` thì có |
| Primitive | `int[]` trực tiếp | `Collection<Integer>` có boxing |
| Khởi tạo generic | Không được `new T[n]` | Được `new ArrayList<T>()` |

Đổi sang Collection phù hợp khi thao tác nghiệp vụ cần **tập phần tử generic** chứ không đòi runtime array class. Điều đó không khiến generics reified hay collection tự mutable; phải tiếp tục chọn đúng implementation, ownership và null policy.

## 3. Chọn đúng interface: `Collection` không đồng nghĩa `List`

`java.util.Collection<E>` là interface cơ sở cho nhiều loại tập hợp. Những thao tác thường gặp gồm:

- `add`, `remove`, `contains`, `size`, `isEmpty`;
- duyệt bằng enhanced `for`;
- iterator, `stream`, `toArray`;
- thao tác trên tập hợp như `addAll`, `removeAll`, `retainAll`.

Nhưng `Collection` **không có `get(int)`**, không hứa duy trì thứ tự phần tử cho mọi implementation và không bảo đảm mọi collection đều hỗ trợ `add` hoặc `remove`.

Các interface chuyên biệt:

| Nhu cầu thật | Kiểu nên cân nhắc | Điểm quan trọng |
|---|---|---|
| Một tập phần tử để duyệt hoặc đếm | `Collection<T>` | Không cần index/order ở API |
| Thứ tự, truy cập vị trí, có thể trùng phần tử | `List<T>` | Có `get(int)` và thứ tự phần tử |
| Không muốn phần tử trùng theo tiêu chí equality | `Set<T>` | Không có index; thứ tự tùy implementation |
| Thêm và lấy phần tử theo chính sách hàng đợi | `Queue<T>`/`Deque<T>` | Hợp đồng thao tác đầu/cuối phù hợp |
| Ánh xạ từ key tới value | `Map<K,V>` | `Map` **không extends** `Collection` |
| Mảng component type thật sự ở runtime | `T[]` trong ngữ cảnh tạo an toàn | Dùng khi cần hợp đồng array |

Với `Collection<T>`, API nhận được nhiều loại triển khai. Đó là ưu điểm khi thuật toán chỉ cần năng lực tối thiểu. Nếu thuật toán cần gọi `get(i)`, đừng ép `Collection` xuống `List` vô căn cứ; hãy yêu cầu `List<T>` ngay trong chữ ký.

## 4. Ví dụ chạy được: viết generic container bằng Collection

Tạo tệp **`GenericCollectionDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collection;

public class GenericCollectionDemo {

    static final class Bag<T> {
        private final Collection<T> elements = new ArrayList<>();

        void add(T value) {
            elements.add(value);
        }

        int size() {
            return elements.size();
        }

        Collection<T> snapshot() {
            return new ArrayList<>(elements);
        }
    }

    public static void main(String[] args) {
        Bag<String> names = new Bag<>();
        names.add("Lan");
        names.add("Mai");

        System.out.println(names.size());

        for (String name : names.snapshot()) {
            System.out.println(name);
        }

        Bag<Integer> points = new Bag<>();
        points.add(8);
        points.add(9);

        System.out.println(points.size());
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac GenericCollectionDemo.java
java GenericCollectionDemo
```

Stdout:

```text
2
Lan
Mai
2
```

Điểm cần phân tích:

1. `Bag<T>` không thể trực tiếp cấp phát `new T[n]`, nhưng có thể khai báo trường `Collection<T>` và gán một `ArrayList<T>`.
2. Khi dùng `Bag<String>`, compiler yêu cầu `add` nhận `String`; dùng `Bag<Integer>` thì yêu cầu `Integer`.
3. `snapshot()` tạo một **collection mới** để caller không nắm trực tiếp collection nội bộ; ví dụ không tuyên bố rằng các object phần tử được sao chép sâu.
4. Vòng `for-each` làm việc trên `Collection<T>` vì collection hỗ trợ `Iterable<T>`.
5. `ArrayList` duy trì thứ tự chèn, nên stdout của ví dụ là xác định. Nếu implementation đổi thành `HashSet`, không nên mặc định thứ tự in giống vậy.

Ví dụ dùng `Collection` để lưu trữ thay cho một generic array mà không cần cast unchecked.

### Vì sao không viết `Collection<T> elements = new Collection<>();`?

`Collection` là **interface**, không thể khởi tạo trực tiếp. Ta chọn một concrete implementation:

```java
Collection<T> elements = new ArrayList<>();
```

Hoặc nếu cần tập duy nhất:

```java
Collection<T> elements = new java.util.HashSet<>();
```

Nếu thứ tự là điều quan trọng, dùng `ArrayList` hoặc một implementation có thứ tự phù hợp; nếu chỉ cần tính duy nhất, cân nhắc `Set`.

## 5. Bài toán quan trọng: thay `List<String>[]` bằng `List<List<String>>`

Giả sử cần một số nhóm, mỗi nhóm có nhiều tên. Cách trực tiếp tạo mảng không hợp lệ:

```java
// Cố ý không biên dịch:
List<String>[] groups = new List<String>[3];
```

Hãy mô hình hóa là một danh sách chứa những danh sách chuỗi:

```java
List<List<String>> groups = new ArrayList<>();
```

Đó là **List của List**, trong đó cả hai cấp generic đều được compiler kiểm tra ở code bình thường.

Tệp **`NestedListDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class NestedListDemo {
    public static void main(String[] args) {
        List<List<String>> groups = new ArrayList<>();

        groups.add(new ArrayList<>(List.of("An", "Binh")));
        groups.add(new ArrayList<>(List.of("Chi")));

        groups.get(1).add("Dung");

        System.out.println(groups.size());
        System.out.println(groups.get(0).get(1));

        for (List<String> group : groups) {
            System.out.println(String.join(", ", group));
        }
    }
}
```

Chạy:

```bash
javac NestedListDemo.java
java NestedListDemo
```

Stdout:

```text
2
Binh
An, Binh
Chi, Dung
```

Lý do đây thường là thiết kế tốt:

- Không cần `new List<String>[n]`.
- Outer list duy trì thứ tự nhóm và có `get(index)`.
- Inner list có kiểu `List<String>`; compiler ngăn chèn `Integer` vào một nhóm trong code typed thông thường.
- Thêm/bỏ nhóm không bị giới hạn bởi số slot mảng đã tạo.
- Có thể thay outer `ArrayList` bằng implementation `List` khác mà không đổi phần lớn code client.

**Lưu ý:** Dùng `Collection<Collection<String>>` sẽ quá tổng quát nếu yêu cầu là truy cập nhóm và học viên theo index. Chọn `List<List<String>>` khi cần truy cập hai cấp bằng vị trí.

## 6. `Collection<T>` là lựa chọn cho API chỉ cần duyệt

Đừng vô tình tạo một yêu cầu “array” nếu hàm chỉ cần duyệt các phần tử.

Thay:

```java
// Khai báo hợp lệ, nhưng gắn API với array.
static int countNames(String[] names) {
    return names.length;
}
```

Có thể dùng:

```java
static int countNames(Collection<String> names) {
    return names.size();
}
```

Trong trường hợp muốn nhận cả các subtype, wildcard có thể có ích. Ví dụ:

```java
static void printAll(Collection<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

Đây là các **đoạn method độc lập để so sánh**, cần đặt trong class thích hợp và import `java.util.Collection`.

Một method nhận `Collection<?>` diễn đạt “tôi chỉ đọc các phần tử với kiểu chưa biết”. Nếu cần xử lý mỗi phần tử theo một kiểu cụ thể, giữ tham số hóa như `Collection<String>` hoặc dùng bound/callback thích hợp.

Tuy nhiên, **không dùng `Collection<T>` chỉ vì mọi thứ đều là collection**. Nếu code cần sắp xếp dựa trên index hoặc truy cập phần tử thứ 10 thường xuyên, `List<T>` là hợp đồng đúng hơn.

## 7. Một utility generic không cần `T[]`

Tạo tệp **`CollectionUtilityDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CollectionUtilityDemo {

    static <T> List<T> copyOf(Collection<? extends T> source) {
        return new ArrayList<>(source);
    }

    static <T> void addAll(
            Collection<? super T> target,
            Collection<? extends T> source) {
        for (T value : source) {
            target.add(value);
        }
    }

    public static void main(String[] args) {
        List<String> words = copyOf(List.of("red", "blue"));
        List<Object> output = new ArrayList<>();

        addAll(output, words);
        addAll(output, List.of(10, 20));

        System.out.println(words);
        System.out.println(output);
    }
}
```

Lệnh:

```bash
javac CollectionUtilityDemo.java
java CollectionUtilityDemo
```

Stdout:

```text
[red, blue]
[red, blue, 10, 20]
```

Ví dụ minh họa quy tắc **PECS** (*Producer Extends, Consumer Super*):

- `Collection<? extends T> source`: phần tử đọc từ nguồn có thể dùng như `T`.
- `Collection<? super T> target`: đích có thể nhận các phần tử kiểu `T`.
- `copyOf` trả một `List<T>` mới, không cần `T[]` ở bất kỳ nơi nào.

Khi gọi `addAll(output, words)`, tham số đích `Collection<Object>` có thể nhận `String`; các generic constraints giúp compiler xác minh quan hệ hợp lệ. Việc method nhận một `Collection` giúp callers dùng nhiều kiểu container mà không phải chuyển tạm sang mảng.

### Vì sao không dùng `Collection<T>` cho cả source lẫn target?

Khai báo:

```java
static <T> void addAll(Collection<T> target, Collection<T> source)
```

không nhận được mọi cặp phù hợp mà `<? super T>` và `<? extends T>` cho phép. Chẳng hạn `Collection<Object>` là đích hợp lý của `Collection<String>`, nhưng hai container không có **cùng** type argument `T`.

Bounded wildcards giúp giữ an toàn và giảm yêu cầu ép kiểu. Tuy nhiên không cần áp dụng PECS một cách máy móc: nếu một tham số vừa đọc vừa ghi một `T` chính xác, `Collection<T>` có thể thích hợp hơn.

## 8. Nếu muốn phần tử duy nhất, dùng `Set<T>`

Một mảng có thể chứa bản sao trùng nhau; một `List` cũng vậy. Nếu nghiệp vụ muốn các phần tử duy nhất theo `equals`/`hashCode`, chọn `Set<T>`.

Ví dụ **`SetInsteadOfArrayDemo.java`**:

```java
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class SetInsteadOfArrayDemo {
    static <T> int uniqueCount(Collection<? extends T> source) {
        Set<T> unique = new LinkedHashSet<>(source);
        return unique.size();
    }

    public static void main(String[] args) {
        System.out.println(uniqueCount(
                java.util.List.of("A", "B", "A")));
        System.out.println(uniqueCount(
                java.util.List.of(2, 2, 3, 4)));
    }
}
```

Biên dịch/chạy:

```bash
javac SetInsteadOfArrayDemo.java
java SetInsteadOfArrayDemo
```

Stdout:

```text
2
3
```

`LinkedHashSet` được dùng để hỗ trợ thứ tự lặp dựa trên thứ tự chèn khi cần, nhưng chương trình chỉ in kích thước. Còn `HashSet` không cung cấp hợp đồng thứ tự lặp tương ứng.

Nếu API phải truy cập phần tử theo vị trí, `Set` không thay thế được `List`. Chọn abstraction từ đặc tính dữ liệu thật.

## 9. Khi nào `Collection<T>` không đủ?

Đây là giới hạn quan trọng của lời khuyên “dùng Collection thay array”.

### 9.1. Cần truy cập index

```java
List<String> names = new ArrayList<>();
names.add("An");
String first = names.get(0);
```

`List` có `get(int)`, còn `Collection` không có. Đừng biến `Collection<String>` thành `List<String>` bằng cast nếu nguồn có thể là `HashSet`.

### 9.2. Cần kích thước cố định và vị trí trống

Một array `String[3]` lập tức có **ba slot**, tất cả mang giá trị mặc định `null`. `new ArrayList<String>(3)` chỉ đặt **dung lượng ban đầu** (*capacity*), không tạo ba phần tử.

```java
List<String> slots = new ArrayList<>(3);
System.out.println(slots.size()); // 0
```

Nếu muốn có ba vị trí thực sự:

```java
List<String> slots = new ArrayList<>(
        java.util.Arrays.asList(null, null, null));
System.out.println(slots.size()); // 3
```

Đây là các đoạn minh họa; đặt trong phương thức thích hợp. Một list có kích thước 3 không tự trở thành cấu trúc cố định trừ khi API chủ động giới hạn thay đổi.

Nếu cần container có số slot cố định hoặc bố cục dữ liệu sát phần cứng, array vẫn có thể phù hợp.

### 9.3. Cần primitive arrays

`int[]`, `double[]` lưu trực tiếp các giá trị primitive. `Collection<Integer>` và `Collection<Double>` dùng wrapper objects theo hệ thống kiểu của Java Generics và có autoboxing/unboxing ở các điểm thích hợp.

Với khối lượng tính toán số lớn, primitive arrays có thể giảm chi phí đối tượng và bộ nhớ; tuy nhiên hiệu năng thực tế cần đo bằng benchmark thay vì suy luận tuyệt đối từ cú pháp.

### 9.4. Cần API Java/Java native yêu cầu array

Một số API nhận hoặc trả `String[]`, `byte[]`, `char[]`, `int[]` vì contract được thiết kế theo mảng. Khi đó phải cung cấp array đúng kiểu, thay vì cố thay đổi chữ ký do bên thứ ba quy định.

### 9.5. Cần runtime component type

Nếu chương trình thực sự phải biết component class lúc chạy, mảng mang thông tin hữu ích qua `array.getClass().getComponentType()`. Một generic `Collection<T>` không tự mang `T` theo cùng cơ chế.

**Kết luận:** Dùng `Collection<T>` để tránh array không reifiable khi bài toán cho phép, **không phải loại bỏ toàn bộ array** khỏi Java.

## 10. Phân biệt kích thước `ArrayList` với độ dài mảng

Một lỗi rất phổ biến khi thay array bằng collection:

```java
int[] array = new int[5];          // array.length == 5.
ArrayList<Integer> list = new ArrayList<>(5);
                                   // list.size() == 0.
```

Ở `ArrayList<>(5)`, số `5` là initial capacity cho implementation, không có nghĩa đã tạo năm phần tử. Do đó:

```java
// Cố ý gây lỗi runtime nếu list còn rỗng:
list.set(0, 10);
```

sẽ không hợp lệ theo giới hạn chỉ số của danh sách và có thể ném `IndexOutOfBoundsException`.

Nếu muốn có các slot với giá trị mặc định:

```java
List<Integer> list = new ArrayList<>(
        java.util.Collections.nCopies(5, 0));
```

Sau đó có thể `set` trong vùng chỉ số hợp lệ. `Collections.nCopies` tạo một List có số phần tử được chỉ định; constructor `ArrayList` sao chép chúng sang danh sách có thể sửa.

Khác với mảng primitive `new int[5]`, `List<Integer>` không có default zero tự động nếu chỉ gọi constructor đặt capacity.

## 11. Tệp chạy được: đối chiếu `length`, `size()` và `get()`

Tạo **`ArrayVsListSizeDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArrayVsListSizeDemo {
    public static void main(String[] args) {
        int[] array = new int[3];
        ArrayList<Integer> empty = new ArrayList<>(3);

        System.out.println(array.length);
        System.out.println(empty.size());

        List<Integer> filled =
                new ArrayList<>(Collections.nCopies(3, 0));

        filled.set(1, 7);
        array[1] = 7;

        System.out.println(filled);
        System.out.println(array[1]);
    }
}
```

Biên dịch và chạy:

```bash
javac ArrayVsListSizeDemo.java
java ArrayVsListSizeDemo
```

Stdout:

```text
3
0
[0, 7, 0]
7
```

Câu hỏi tự kiểm tra: Vì sao `new ArrayList<>(3)` không tạo sẵn ba phần tử? Vì constructor này chỉ cung cấp **initial capacity**; số phần tử thực là một khái niệm riêng, trả về bởi `size()`.

## 12. Bảo toàn hợp đồng khi expose collection từ class

Một field kiểu `Collection<T>` hoặc `List<T>` có thể chứa collection mutable. Nếu getter trả thẳng collection nội bộ, caller có thể thêm/xóa phần tử và phá invariant nghiệp vụ.

Có một số lựa chọn:

- **Trả bản sao mutable** như `new ArrayList<>(elements)`: caller sửa bản sao không làm thay đổi tập hợp nội bộ; phần tử vẫn là cùng object reference nếu chúng mutable.
- **Trả snapshot unmodifiable** như `List.copyOf(elements)` khi chấp nhận điều kiện `null` và thứ tự phù hợp.
- **Trả unmodifiable view** như `Collections.unmodifiableList(list)`: ngăn sửa qua view, nhưng có thể phản ánh các thay đổi từ tham chiếu khác tới backing list.
- **Trả interface đọc-only về mặt ý định** như `Collection<T>` không đủ để bảo đảm immutable, vì `Collection` có các phương thức thay đổi và implementation có thể hỗ trợ chúng.

Dùng abstraction tốt và bảo vệ ownership là **hai quyết định khác nhau**. Chỉ đổi `T[]` thành `Collection<T>` không tự động che được trạng thái nội bộ.

## 13. Ví dụ chạy được: snapshot có thể đọc nhưng không sửa

Tệp **`SnapshotCollectionDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SnapshotCollectionDemo {
    static class Registry<T> {
        private final List<T> items = new ArrayList<>();

        void register(T value) {
            items.add(value);
        }

        Collection<T> snapshot() {
            return List.copyOf(items);
        }
    }

    public static void main(String[] args) {
        Registry<String> registry = new Registry<>();
        registry.register("A");

        Collection<String> oldSnapshot = registry.snapshot();
        registry.register("B");

        System.out.println(oldSnapshot);
        System.out.println(registry.snapshot());

        try {
            oldSnapshot.add("C");
        } catch (UnsupportedOperationException ex) {
            System.out.println("UnsupportedOperationException");
        }
    }
}
```

Chạy:

```bash
javac SnapshotCollectionDemo.java
java SnapshotCollectionDemo
```

Stdout:

```text
[A]
[A, B]
UnsupportedOperationException
```

`List.copyOf` là **shallow snapshot** và từ chối `null`. Mỗi lần gọi `snapshot()` ở ví dụ lấy trạng thái collection mới; snapshot cũ không tự cập nhật khi `registry` nhận thêm phần tử.

Với phần tử mutable, object bên trong vẫn có thể thay đổi trạng thái thông qua những tham chiếu bên ngoài. Nếu muốn bất biến sâu, phải tự thiết kế cơ chế copy/immutable element tương ứng.

## 14. Chuyển từ `Collection<T>` sang `T[]` khi thực sự cần

Trong nhiều trường hợp, cách tốt nhất là **lưu trữ bằng collection**, chỉ tạo mảng ở ranh giới API yêu cầu mảng.

Ví dụ `Collection.toArray(IntFunction<T[]>)` cho phép caller cung cấp array constructor reference có runtime component type cụ thể.

Tạo tệp **`CollectionToArrayDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class CollectionToArrayDemo {

    static <T> T[] asArray(
            Collection<? extends T> input,
            java.util.function.IntFunction<T[]> factory) {
        return input.toArray(factory.apply(0));
    }

    public static void main(String[] args) {
        Collection<String> words =
                new ArrayList<>(List.of("A", "B"));

        String[] direct = words.toArray(String[]::new);
        String[] fromHelper = asArray(words, String[]::new);

        System.out.println(Arrays.toString(direct));
        System.out.println(Arrays.toString(fromHelper));
        System.out.println(direct.getClass()
                .getComponentType().getName());
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac CollectionToArrayDemo.java
java CollectionToArrayDemo
```

Stdout:

```text
[A, B]
[A, B]
java.lang.String
```

**Phân tích hai cách chuyển:**

- `words.toArray(String[]::new)` gọi overload `Collection.toArray(IntFunction<T[]>)`, có từ Java 11. Constructor reference chỉ ra mảng `String[]` có component class runtime là `String`.
- `asArray` truyền một array được tạo bởi factory vào overload `toArray(T[])`. Mảng được tạo dài 0; contract của `toArray` cho phép cấp phát mảng mới khi kích thước chưa đủ. Khi trả về, kết quả vẫn là `String[]` đúng component type.
- Không cần tạo `new T[n]` bên trong generic helper.

Đoạn helper dùng `factory.apply(0)` như kỹ thuật minh họa tương thích với overload `toArray(T[])`. Trong code chỉ nhằm chuyển collection sang mảng với JDK 17+, cách trực tiếp `input.toArray(factory)` thường gọn hơn.

**Không suy ra** rằng có thể tạo `List<String>[]` bằng `List<String>[]::new`: component type của một array constructor reference vẫn phải đáp ứng quy tắc array creation. Type witness/factory không làm một parameterized type non-reifiable trở thành reifiable.

## 15. Chuyển từ mảng sang collection

Ngược lại, khi nhận một mảng có kiểu phần tử cụ thể, có thể tạo collection từ mảng.

Một số cách quen thuộc:

```java
String[] source = {"A", "B"};

List<String> backedView = java.util.Arrays.asList(source);

List<String> independent =
        new java.util.ArrayList<>(java.util.Arrays.asList(source));

List<String> unmodifiable =
        List.copyOf(java.util.Arrays.asList(source));
```

Đây là đoạn minh họa cần đặt trong phương thức có import `java.util.List`.

Những cách này **không đồng nghĩa**:

- `Arrays.asList(source)` cho **list kích thước cố định, backed by array**: gọi `set` trên list có thể ảnh hưởng mảng, và thay phần tử mảng có thể phản ánh vào list. Không thể thêm hoặc xóa qua list này.
- `new ArrayList<>(...)` tạo một list có thể thay đổi kích thước, có các phần tử tham chiếu được sao chép từ dữ liệu đầu vào.
- `List.copyOf(...)` tạo list không thể sửa qua API chuẩn và không cho phần tử `null`; đây là snapshot ở mức cấu trúc.

Ngoài ra, với **primitive arrays** phải cẩn thận:

```java
int[] values = {1, 2, 3};
List<int[]> oneElement = java.util.Arrays.asList(values);
```

`Arrays.asList(int[])` xem toàn bộ `int[]` là **một phần tử tham chiếu**, không biến primitive `int` thành ba `Integer`.

Khi cần `List<Integer>`, có thể dùng `Arrays.stream(values).boxed().toList()` trên Java 17; kết quả của `Stream.toList()` là unmodifiable. Nếu cần mutable list, thu thập sang `ArrayList` bằng collector hoặc tạo bản sao sau đó.

## 16. Bài thực hành: chuyển mảng và danh sách đúng ngữ nghĩa

Tệp **`ArraysAsListDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArraysAsListDemo {
    public static void main(String[] args) {
        String[] source = {"A", "B"};

        List<String> backed = Arrays.asList(source);
        List<String> copied = new ArrayList<>(backed);

        source[0] = "X";

        System.out.println(backed);
        System.out.println(copied);

        backed.set(1, "Y");
        System.out.println(Arrays.toString(source));

        copied.add("C");
        System.out.println(copied);
    }
}
```

Chạy:

```bash
javac ArraysAsListDemo.java
java ArraysAsListDemo
```

Stdout:

```text
[X, B]
[A, B]
[X, Y]
[A, B, C]
```

Sau `source[0] = "X"`, `backed` đổi theo vì là view backed by array, còn `copied` không đổi cấu trúc. `backed.set(1, "Y")` cập nhật chính phần tử của mảng. `copied.add("C")` thêm được phần tử do `ArrayList` là resizable.

Ví dụ giúp thấy “thay array bằng collection” cần xét **ownership**, **khả năng thay đổi số lượng**, và **liên kết dữ liệu gốc** chứ không chỉ xét cú pháp generic.

## 17. Khi cần đầu vào read-only, dùng wildcard và hợp đồng rõ

Một generic API chỉ cần đọc các đối tượng kiểu `Number` có thể khai báo:

```java
static double total(java.util.Collection<? extends Number> numbers) {
    double sum = 0;
    for (Number value : numbers) {
        sum += value.doubleValue();
    }
    return sum;
}
```

Hàm này nhận `Collection<Integer>`, `Collection<Double>` hoặc những collection có phần tử subtype của `Number`, mà không cần chuyển sang `Number[]`.

Đây là **đoạn method minh họa**, đặt trong class nếu biên dịch. Với tham số `? extends Number`, không được tùy tiện thêm `Number` vào collection vì kiểu phần tử cụ thể có thể là `Integer`, `Double`, v.v.

Tương tự, nếu cần ghi `Integer` vào một đích chấp nhận kiểu cha:

```java
static void addScores(
        java.util.Collection<? super Integer> target) {
    target.add(10);
    target.add(20);
}
```

`Collection<? super Integer>` có thể nhận `Collection<Integer>`, `Collection<Number>` hoặc `Collection<Object>` theo quy tắc generic compatibility.

Một ưu điểm lớn của collection + wildcard là diễn đạt năng lực **đọc từ producer** hoặc **ghi vào consumer** mà không tạo array alias covariant dễ bị lạm dụng.

## 18. `Object[]` là storage nội bộ, không phải mảng `T[]` công khai

```java
Object[] data = new Object[]{"A", "B"};
// String[] bad = (String[]) data; // ClassCastException tại runtime.
```

Cho dù hai phần tử hiện tại đều là `String`, mảng thực vẫn có component class `Object`; cast cả mảng **không** tạo `String[]`. Có thể giữ `private Object[]` làm backing storage của cấu trúc `Store<T>` khi mọi đường ghi được kiểm soát theo `T`, chỉ cast từng phần tử ở nơi có invariant rõ, và không lộ mảng ra ngoài. Nếu không cần low-level storage, `ArrayList<T>` thường đơn giản hơn.

Bài chuyên sâu về `Object[]` trình bày cách khoanh vùng `@SuppressWarnings("unchecked")`, kiểm tra `size` và những đường raw/reflection/alias phá invariant. Với API cần array thật, yêu cầu `T[]` factory hoặc class token, không trả `Object[]` dưới danh nghĩa `T[]`.

## 19. Collection có thể thay varargs generic khi chỉ cần duyệt dữ liệu

Varargs `List<T>... values` được truyền dưới dạng array, với element type generic thường non-reifiable; các phương thức thiếu cẩn trọng có nguy cơ heap pollution. Nếu yêu cầu chỉ là **duyệt nhiều danh sách chuỗi**, thay bằng:

```java
static void showGroups(Collection<? extends List<String>> groups) {
    for (List<String> group : groups) {
        System.out.println(group.size());
    }
}
```

Lời gọi `showGroups(List.of(List.of("A"), List.of("B","C")))` có kiểu an toàn, không buộc caller tạo array of `List<String>`. Nếu API cố ý dùng varargs, cần kiểm chứng body và chỉ sử dụng `@SafeVarargs` khi được ngôn ngữ cho phép và nội dung thực sự an toàn. Trọng tâm ở đây là **lựa chọn abstraction đầu vào**, không phải toàn bộ quy tắc varargs.

## 20. Collection không làm generic type reified

Một misunderstanding cần tránh là nghĩ `Collection<T>` “giữ” `T` như một thuộc tính runtime của mỗi instance. Thực tế:

```java
java.util.Collection<String> words =
        new java.util.ArrayList<>();

java.util.Collection<Integer> numbers =
        new java.util.ArrayList<>();
```

Hai biến có kiểu tĩnh khác nhau nhưng cả hai runtime objects đều là `ArrayList`. Java Generics vẫn dùng erasure.

Do đó:

```java
words.getClass() == numbers.getClass()
```

sẽ là `true` khi cả hai được tạo như trên.

Dùng `Collection<T>` tăng **an toàn tại compile time** bằng cách cho compiler theo dõi type arguments khi code được kiểm tra bình thường. Nó không ngăn hoàn toàn heap pollution từ raw types, unchecked casts hoặc dữ liệu reflection không an toàn.

Nếu chương trình cần xác minh thực tế rằng collection chứa toàn `String`, phải kiểm tra dữ liệu (ví dụ duyệt `Collection<?>` và dùng `instanceof String`) hoặc tạo bản sao đã xác minh với class token `String.class` theo hợp đồng rõ ràng.

## 21. Khi nhận collection từ một nguồn không tin cậy

Nếu đọc từ một API legacy, reflection hoặc dữ liệu bên ngoài, không thể chỉ cast:

```java
// Cố ý không an toàn:
@SuppressWarnings("unchecked")
Collection<String> names = (Collection<String>) unknown;
```

và kết luận phần tử đã là `String`. Runtime cast chỉ xác minh đối tượng là `Collection` có raw type phù hợp; nó không xác minh `String`.

Một cách rõ ràng hơn là kiểm tra từng phần tử và đưa vào một collection mới.

Tệp **`ValidatedCollectionDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ValidatedCollectionDemo {

    static <T> List<T> verifiedCopy(
            Collection<?> input, Class<T> elementType) {
        List<T> result = new ArrayList<>();

        for (Object element : input) {
            result.add(elementType.cast(element));
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        List<String> valid = verifiedCopy(
                List.of("A", "B"), String.class);

        System.out.println(valid);

        try {
            verifiedCopy(List.of("A", 10), String.class);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Lệnh:

```bash
javac ValidatedCollectionDemo.java
java ValidatedCollectionDemo
```

Stdout:

```text
[A, B]
ClassCastException
```

`Class<T>.cast` kiểm tra object là instance của class runtime `T`, còn `List.copyOf` tạo snapshot unmodifiable. Trong ví dụ, dữ liệu không chứa `null`; với dữ liệu cho phép `null`, cần quyết định chính sách riêng vì `List.copyOf` không chấp nhận `null`.

Đối với kiểu phần tử lồng nhau như `List<String>`, không có `List<String>.class`. Khi đó phải kiểm tra cấu trúc lồng nhau thực tế hoặc dùng schema/`Type` cho bộ giải mã phù hợp; việc có một `Class<List>` không đủ bảo đảm type arguments.

## 22. `Collection<T>` và tính bất biến: không nên nhầm interface với trạng thái

Cùng có kiểu `Collection<String>`, nhưng các giá trị sau có hành vi sửa đổi khác nhau:

```java
Collection<String> mutable = new ArrayList<>();
Collection<String> immutable = List.of("A");
Collection<String> snapshot = List.copyOf(mutable);
```

Cần import `java.util.Collection`, `java.util.ArrayList`, `java.util.List`.

- `mutable.add("B")` thường hợp lệ vì là `ArrayList`.
- `immutable.add("B")` ném `UnsupportedOperationException`.
- `snapshot.add("B")` cũng ném `UnsupportedOperationException`.

Interface `Collection` **cho phép khai báo các phương thức thay đổi** nhưng một implementation có thể không hỗ trợ một số thao tác tùy chọn. Một API chỉ nhận `Collection<T>` không tự bảo đảm collection có thể sửa.

Nếu một method cần thêm phần tử vào collection nhận được, hợp đồng phải nói rõ yêu cầu mutability, hoặc method tự tạo `new ArrayList<>(input)` trước khi sửa. Nếu method chỉ đọc, nên giữ hành vi không làm thay đổi input.

## 23. So sánh hiệu năng đúng cách

Một số người chọn array vì “array luôn nhanh”, hoặc chọn `ArrayList` vì “Generics luôn tốt hơn”. Cả hai là kết luận quá rộng.

Các cân nhắc thực tế:

- `int[]` lưu primitive liên tiếp theo biểu diễn mảng JVM; `List<Integer>` dùng wrapper objects và có thể xuất hiện chi phí boxing/unboxing.
- `ArrayList<T>` thường lưu các object references trong một mảng nội bộ và hỗ trợ truy cập index hiệu quả. Kích thước logic có thể tăng; đôi khi nó cần cấp phát vùng lưu trữ mới và sao chép tham chiếu.
- `LinkedList<T>` có đặc tính truy cập index và bộ nhớ rất khác với `ArrayList<T>`.
- `HashSet<T>` tối ưu cho yêu cầu membership/uniqueness nhưng không dùng index.
- Array có fixed length; một list có thể tăng/giảm số phần tử tùy implementation.
- Một container có API đơn giản, bảo đảm type safety và dễ bảo trì thường đáng giá hơn chênh lệch vi mô chưa được đo.

Để quyết định ở đường code nhạy hiệu năng, dùng benchmark đáng tin cậy, chẳng hạn JMH, với kích thước dữ liệu và pattern truy cập thực tế. Không dùng một vòng lặp nhỏ thiếu warm-up như bằng chứng tổng quát cho hiệu năng JVM.

## 24. Dấu hiệu nên dùng Collection thay mảng

Có thể ưu tiên generic collection khi:

1. Phần tử có kiểu generic chưa biết tại nơi viết implementation, chẳng hạn `T`.
2. Bài toán không cần runtime array component class.
3. Cần thêm/bớt phần tử theo thời gian.
4. API cần hoạt động với nhiều implementation mà không cần biết container cụ thể.
5. Cần hợp đồng an toàn kiểu cho collection lồng nhau như `List<List<String>>`.
6. Muốn dùng `Iterator`, stream, `contains`, `addAll` và các thao tác chuẩn collections.
7. Có thể tránh được unchecked casts hoặc rủi ro array covariance.
8. Không cần tối ưu primitive array theo khối lượng dữ liệu lớn.

Đồng thời, hãy cân nhắc giữ mảng khi:

- API bên ngoài yêu cầu mảng;
- số slot cố định là một phần quan trọng của mô hình;
- cần lưu primitive trực tiếp;
- code xử lý cần kiểu phần tử runtime xác định;
- đo đạc hiệu năng chứng minh mảng phù hợp hơn và không vi phạm an toàn kiểu.

Các tiêu chí không loại trừ nhau: một module có thể giữ dữ liệu bằng `List<T>` trong nghiệp vụ và chuyển thành `T[]` tại ranh giới tích hợp.

## 25. Những lỗi phổ biến khi đổi generic array thành collection

**Lỗi 1 — “`Collection<T>` có `get(index)` như array.”** Không. Hợp đồng `Collection` không có phương thức truy cập theo index; dùng `List<T>`.

**Lỗi 2 — “`new ArrayList<>(10)` tạo mười slot giống `new T[10]`.”** Không. Đó là initial capacity, `size()` ban đầu là 0.

**Lỗi 3 — “`Collection<T>` giữ `T` tại runtime.”** Không theo nghĩa reification của type argument; JVM vẫn thường thấy class runtime như `ArrayList`.

**Lỗi 4 — “`Collection<T>` luôn mutable.”** Không. `List.of`, `Set.of` hoặc `List.copyOf` có thể cung cấp collection không cho sửa.

**Lỗi 5 — “Chỉ đổi kiểu vế trái thành Collection là có an toàn kiểu.”** Không nếu code còn raw type, unchecked casts hoặc thao tác dữ liệu không kiểm tra. Cần duy trì typed contract xuyên suốt.

**Lỗi 6 — “`ArrayList` giống `HashSet` về thứ tự.”** Không. `ArrayList` giữ thứ tự phần tử; `HashSet` không cam kết iteration order.

**Lỗi 7 — “`Arrays.asList` tạo list hoàn toàn độc lập với array gốc.”** Không. Nó là fixed-size list backed by array.

**Lỗi 8 — “`List.copyOf` luôn là deep copy.”** Không. Nó tạo snapshot ở mức cấu trúc collection và không sao chép sâu các object phần tử mutable.

**Lỗi 9 — “`List<List<String>>` có thể nhận `List<List<Integer>>`.”** Không. Generic invariance ngăn thay đổi type argument không tương thích trong typed code.

**Lỗi 10 — “Thêm `@SuppressWarnings` là đã giải quyết generic array.”** Không. Annotation chỉ tắt một số cảnh báo, không tạo thông tin kiểu runtime còn thiếu.

**Lỗi 11 — “`Collection<? extends T>` thích hợp để thêm mọi `T`.”** Không. Với producer kiểu `? extends T`, compiler không biết subtype cụ thể; không thể ghi tùy ý một `T` khác vào.

**Lỗi 12 — “`Collection<? super T>` bảo đảm đọc ra `T`.”** Không. Kết quả đọc an toàn có thể chỉ được coi là `Object`, vì collection đích có thể là `Collection<Object>`.

**Lỗi 13 — “`List<T>` tốt hơn array trong mọi trường hợp.”** Không. Mảng primitive, fixed slots hoặc một API có contract array là những trường hợp tiếp tục cần mảng.

## 26. Bài tập thực hành có đáp án

### Bài 1: Container generic

Viết `class Bag<T>` dùng `Collection<T>` để lưu phần tử. Có thể tạo `Bag<String>` và `Bag<Integer>` mà không cần `new T[n]` không?

**Đáp án:** Có. Field `Collection<T>` có thể khởi tạo bằng `new ArrayList<>()`; compiler kiểm tra `T` tại các lời gọi. Không cần cấp phát array với component `T`.

### Bài 2: Nhiều danh sách chuỗi

Yêu cầu hai nhóm tên có thể thêm/xóa phần tử, đồng thời truy cập `groups.get(0).get(1)`. Dùng `Collection<Collection<String>>` hay `List<List<String>>`?

**Đáp án:** `List<List<String>>`, vì yêu cầu truy cập theo index ở cả hai cấp.

### Bài 3: Không biết kiểu phần tử

Một hàm chỉ cần in số lượng phần tử của bất kỳ collection nào. Chữ ký nào gọn và đúng?

```java
static int sizeOf(java.util.Collection<?> values) {
    return values.size();
}
```

**Đáp án:** Dạng trên phù hợp vì không cần biết `T`. Không cần raw `Collection`.

### Bài 4: Duy trì tính duy nhất

Cần loại phần tử trùng khi giữ một tập tên. Nên dùng gì?

**Đáp án:** `Set<String>`; nếu cần thứ tự chèn có thể dùng `LinkedHashSet<String>`. Không cần dựng mảng rồi tự quét trùng.

### Bài 5: Initial capacity

`new ArrayList<String>(5).size()` trả bao nhiêu ngay sau khởi tạo?

**Đáp án:** `0`. Capacity không phải số phần tử thực.

### Bài 6: Chuyển collection sang array

Có `Collection<String> words`. Cách tạo `String[]` không cần unchecked cast?

```java
String[] array = words.toArray(String[]::new);
```

**Đáp án:** Hợp lệ với Java 17. Array constructor reference cung cấp runtime component class là `String`.

### Bài 7: Chuyển mảng sang list

`Arrays.asList(source)` có bảo đảm thay `source[0]` không ảnh hưởng list không?

**Đáp án:** Không. List này backed by array và phản ánh thay đổi phần tử.

### Bài 8: Collection đầu vào là dữ liệu không tin cậy

Một object thỏa `instanceof Collection<?>` có đủ bằng chứng là `Collection<String>` không?

**Đáp án:** Không. Kiểm tra runtime này chỉ xác nhận raw container. Cần kiểm tra từng phần tử nếu hợp đồng đòi `String`.

### Bài 9: PECS

Một helper sao chép từ `Collection<String>` vào `Collection<Object>` có thể dùng chữ ký nào?

```java
static <T> void copy(
        java.util.Collection<? extends T> source,
        java.util.Collection<? super T> target) {
    for (T item : source) {
        target.add(item);
    }
}
```

**Đáp án:** Đây là một thiết kế hợp lệ. Source là producer; target là consumer. Không cần tạo mảng `T[]` trung gian.

### Bài 10: Không thể bỏ mảng khi nào?

Một API native yêu cầu `byte[]` cho dữ liệu nhị phân. Có nên ép thiết kế sang `Collection<Byte>` chỉ để dùng Generics?

**Đáp án:** Không. `byte[]` là lựa chọn tự nhiên cho contract ấy và tránh boxing thành `Byte`. Chỉ chuyển dạng ở ranh giới cần thiết nếu API khác thực sự yêu cầu.

## 27. Checklist trước khi chọn cấu trúc

Đối với một lớp generic hoặc API mới, trả lời các câu hỏi sau:

- Tôi cần **danh sách có thứ tự**, **tập duy nhất**, **queue**, hay chỉ **tập phần tử để duyệt**?
- Tôi có cần truy cập index thường xuyên không?
- Số phần tử có tăng/giảm không? Có phải giữ số slot cố định không?
- Tôi có thật sự cần một array component type runtime để tích hợp/kiểm tra không?
- Kiểu phần tử là `T` hoặc `List<String>` non-reifiable ở vị trí tạo array không?
- Có thể dùng `List<T>` hay `List<List<String>>` để loại bỏ generic array creation không?
- Cần quyền sửa collection hay snapshot unmodifiable? Có cho phép `null` không?
- Có đang giao collection nội bộ cho caller không?
- Có thể dùng `Collection<? extends T>` hoặc `Collection<? super T>` để tránh copy/cast không?
- Nếu cuối cùng cần `T[]`, caller có thể cung cấp `IntFunction<T[]>` hoặc array constructor reference hợp lệ không?
- Có đường raw/unchecked nào làm heap pollution không?
- Các yêu cầu hiệu năng (primitive, memory, latency) đã được đo chưa?

Checklist giúp chọn cấu trúc theo hợp đồng thay vì theo một mẹo vượt lỗi compiler.

## 28. Tổng kết và tài liệu đối chiếu

**`Collection<T>` là một thay thế phù hợp cho mảng khi dữ liệu cần thao tác như một tập phần tử generic, đặc biệt khi việc tạo `new T[n]` hoặc `new List<String>[n]` không hợp lệ.** Nó cho phép compiler kiểm tra kiểu qua một API tổng quát, giúp xử lý nhiều phần tử mà không yêu cầu JVM tạo array component type không reifiable.

Hãy chọn interface hẹp nhất thể hiện đúng nhu cầu: `Collection<T>` để duyệt/đếm, `List<T>` để giữ thứ tự và truy cập index, `Set<T>` để loại trùng, hoặc `Queue<T>` cho xử lý hàng đợi. `List<List<String>>` thường là lời giải tự nhiên thay cho ý định “một mảng các danh sách chuỗi”.

Tuy nhiên, `Collection<T>` không tự reify `T`, không bảo đảm mutable hay có thứ tự, và không thay thế được mọi công dụng của array. Khi một API thật sự cần mảng, có thể chuyển từ collection bằng `toArray(String[]::new)` hoặc array factory hợp lệ; khi cần primitive storage hay fixed slots, sử dụng array có thể chính xác hơn.

Tài liệu chuẩn:

- [Java Language Specification §4.7 — Reifiable Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.7): kiểu nào đủ thông tin tại runtime.
- [Java Language Specification §15.10.1 — Array Creation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.10.1): quy tắc tạo mảng với kiểu phần tử.
- [Java Language Specification §10.5 — Array Store Exception](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html#jls-10.5): kiểm tra gán phần tử mảng lúc chạy.
- [Java Language Specification §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): kiểu generic sau xóa.
- [Java SE 17 `Collection<E>`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html): hợp đồng collection, `toArray`, các thao tác tùy chọn.
- [Java SE 17 `List<E>`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html): thứ tự, index, unmodifiable factories.
- [Java SE 17 `Arrays.asList`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)): fixed-size list backed by array.
- [Java SE 17 `ArrayList<E>`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html): capacity, size và implementation linh hoạt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-arrays-007">Tạo mảng bằng IntFunction&lt;T&#91;&#93;&gt; hoặc thông tin Class</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo mảng generic bằng IntFunction<T[]> hoặc thông tin Class<T> trong Java

## Mục tiêu học tập

Java cho phép một phương thức generic xử lý các phần tử kiểu `T`, nhưng không cho viết trực tiếp `new T[n]`. Đây không phải hạn chế về cú pháp đơn thuần: JVM cần biết **kiểu phần tử thực của mảng tại runtime** để kiểm tra thao tác ghi, trong khi `T` của generics bị *type erasure* và thường không có class token riêng.

Có hai cách phổ biến để tạo đúng loại mảng mà vẫn giữ API generic:

1. **Nhận `IntFunction<T[]>`** — người gọi cung cấp hàm tạo mảng như `String[]::new`. Phương pháp này là lựa chọn tự nhiên khi Java API đã dùng `Collection.toArray(IntFunction<T[]>)` hoặc khi caller biết cách cấp phát loại mảng mong muốn.
2. **Nhận `Class<T>`** — người gọi cung cấp `String.class` hoặc `Integer.class`, còn phương thức dùng `java.lang.reflect.Array.newInstance(componentType, n)` để tạo mảng có component type đúng tại runtime.

Sau bài này, bạn sẽ:

- Giải thích được vì sao `new T[n]` và `new List<String>[n]` bị compiler từ chối.
- Viết, gọi và kiểm thử hai API tạo mảng `<T> T[]` theo factory và class token.
- Hiểu khác biệt giữa **component type runtime**, **type inference ở compile time**, và **metadata generic**.
- Sử dụng `Collection.toArray(IntFunction<T[]>)`, `Collection.toArray(T[])` và `Array.newInstance` đúng mục đích.
- Tránh những lỗi như ép `Object[]` sang `String[]`, dùng `List<String>.class` không tồn tại, hoặc bỏ qua nguy cơ khi caller cung cấp factory không phù hợp.

## 1. Điều kiện nền tảng: `new T[n]` bị cấm

```java
static <T> T[] wrong(int length) {
    // return new T[length]; // generic array creation
    return null; // Chỉ giúp snippet biên dịch khi minh họa.
}
```

Type variable `T` không có một runtime component class cụ thể; erasure của `T` (thường là `Object`) không có nghĩa caller mong nhận `Object[]`. JVM cần biết component type thật để kiểm tra `array[i] = value`; `Object[]` không phải `String[]`, kể cả khi hiện chứa toàn chuỗi.

Bài này tập trung **hai cách tạo đúng runtime component type**: nhận `IntFunction<T[]>` do caller cung cấp, hoặc nhận `Class<T>` rồi dùng `Array.newInstance`. Cơ chế erasure, array covariance và generic array creation đã được chứng minh riêng; điều quan trọng tại đây là hợp đồng của factory/token và cách kiểm tra kết quả cấp phát.

## 2. So sánh hai chiến lược chính

| Tiêu chí | `IntFunction<T[]>` | `Class<T>` kết hợp `Array.newInstance` |
| --- | --- | --- |
| Dữ liệu caller cung cấp | Một hàm nhận chiều dài và trả mảng, ví dụ `String[]::new`. | Token kiểu phần tử, ví dụ `String.class`. |
| Cách cấp phát | Gọi `factory.apply(n)`. | Gọi `Array.newInstance(componentType, n)`. |
| Có biết component type thật lúc chạy? | Có, nhờ mảng do factory tạo. | Có, nhờ `Class<T>` truyền vào reflection. |
| Có cần unchecked cast bên trong helper? | Không, nếu chỉ gọi factory và nhận `T[]`. | Có cast từ kết quả `Object` của reflection về `T[]`; nên cô lập tại một chỗ. |
| Phù hợp nhất với | API caller-side lambda/constructor reference, các tiện ích collection. | Reflection, framework, API đã truyền `Class<T>`, tạo mảng khi chỉ có token kiểu phần tử. |
| Có hỗ trợ mảng của `List<String>` như component reified không? | Không thể tạo `new List<String>[n]` trực tiếp; chỉ có thể dùng mảng có component runtime là List. | `List<String>.class` không tồn tại; `List.class` không giữ đối số String. |
| Nguy cơ chính | Factory trả `null`, độ dài khác mong đợi hoặc mảng có runtime component không phù hợp. | Truyền nhầm token, thiếu null check, kiểu primitive và cast unchecked ngoài phạm vi an toàn. |

**Nguyên tắc:** Chọn một điểm mà **thông tin kiểu thực được cung cấp rõ ràng từ caller**. Không tạo `Object[]` và kỳ vọng compiler/JVM sẽ biến nó thành mảng T nhờ một cast generic.

## 3. Cách 1: `IntFunction<T[]>` và constructor reference

`java.util.function.IntFunction<R>` là functional interface với phương thức:

```java
R apply(int value);
```

Với `IntFunction<T[]>`, giá trị `R` được chuyên biệt hóa thành `T[]`: hàm nhận một số nguyên và trả về một mảng phù hợp.

Ví dụ:

```java
java.util.function.IntFunction<String[]> maker = String[]::new;
String[] result = maker.apply(3);
result[0] = "An";
```

`String[]::new` là một *array constructor reference*: khi được gọi với số 3, nó tạo `new String[3]`. Nó không dùng `new T[3]`; caller đã xác định component runtime là String.

### 3.1. Helper cấp phát mảng

Đặt trong một lớp:

```java
static <T> T[] allocateWithFactory(
        int length, java.util.function.IntFunction<T[]> factory) {
    if (length < 0) {
        throw new IllegalArgumentException("negative length");
    }
    T[] array = java.util.Objects.requireNonNull(
            factory.apply(length), "array");
    if (array.length != length) {
        throw new IllegalArgumentException("unexpected array length");
    }
    return array;
}
```

Đây là phương thức riêng lẻ minh họa; chương trình chạy đầy đủ ở phần 7 có chính implementation này.

Sử dụng:

```java
String[] names = allocateWithFactory(3, String[]::new);
Integer[] scores = allocateWithFactory(2, Integer[]::new);
```

Compiler suy luận T từ cả loại kết quả và array constructor reference. Không có unchecked cast ở đây: factory trả về đúng biểu thức `T[]` theo hợp đồng generic của nó.

Điều kiện `array.length == length` là lựa chọn thiết kế của **helper này**, không phải nghĩa vụ mặc định của mọi API nhận `IntFunction<T[]>`. Nó giúp phát hiện factory không thực hiện đúng hợp đồng cấp phát mà helper mong đợi. Nếu muốn cho phép mảng có độ dài khác, cần thay đổi hợp đồng và cách xử lý rõ ràng.

### 3.2. Vì sao constructor reference hay hơn lambda trong trường hợp đơn giản?

Hai biểu thức sau tương đương về mục đích:

```java
java.util.function.IntFunction<String[]> a = size -> new String[size];
java.util.function.IntFunction<String[]> b = String[]::new;
```

Constructor reference thường ngắn gọn, trực tiếp thể hiện kiểu mảng cần cấp phát và không cần viết lặp lại tên tham số. Lambda vẫn hữu ích nếu cần kiểm tra kích thước, log, caching hay một chiến lược cấp phát đặc biệt.

**Không nhầm `String::new` với `String[]::new`**: cái đầu là constructor của một đối tượng String; cái sau là constructor reference cho mảng String.

## 4. Cách 2: `Class<T>` và `Array.newInstance`

Khi caller đã có token `Class<T>`, ví dụ `String.class`, có thể dùng reflection:

```java
java.lang.reflect.Array.newInstance(String.class, 3);
```

Phương thức `Array.newInstance(Class<?> componentType, int length)` nhận kiểu phần tử và chiều dài rồi trả về một đối tượng mảng có **component type runtime chính xác theo token**. Giá trị trả về được khai báo là `Object`, vì API reflection còn hỗ trợ mảng primitive và mảng nhiều chiều.

Để cung cấp một API generic trả về `T[]`, có thể viết:

```java
@SuppressWarnings("unchecked")
static <T> T[] allocateWithClass(Class<T> componentType, int length) {
    java.util.Objects.requireNonNull(componentType, "componentType");
    if (componentType.isPrimitive()) {
        throw new IllegalArgumentException("reference components only");
    }
    return (T[]) java.lang.reflect.Array.newInstance(componentType, length);
}
```

Ví dụ:

```java
String[] names = allocateWithClass(String.class, 4);
Number[] numbers = allocateWithClass(Number.class, 2);
Integer[] scores = allocateWithClass(Integer.class, 3);
```

Trong các trường hợp này, mảng thực được tạo mang lần lượt component type String, Number và Integer. Phép `@SuppressWarnings("unchecked")` được đặt **tại đúng phương thức** chứa cast, không lan ra toàn bộ ứng dụng. Sự an toàn dựa vào lập luận: token `Class<T>` chỉ định component type mà `Array.newInstance` dùng để tạo mảng; vì thế kết quả là mảng của T đối với các **reference component types**.

### 4.1. Vì sao phải cân nhắc `isPrimitive()`?

Reflection có thể tạo mảng nguyên thủy:

```java
int[] rawPrimitives =
        (int[]) java.lang.reflect.Array.newInstance(int.class, 2);
```

Nhưng một `int[]` **không phải** `Integer[]` hay `Object[]`. Generic array `T[]` trong helper thông thường được sử dụng như mảng tham chiếu, nên wrapper ở trên chủ động từ chối `componentType.isPrimitive()` để có hợp đồng rõ ràng.

Chi tiết dễ nhầm: trong biểu thức Java, `int.class` có kiểu tĩnh tương ứng `Class<Integer>`, nhưng đối tượng token vẫn **mô tả primitive int**, không mô tả wrapper Integer. Do đó:

- `Integer.class` tạo component type `Integer`.
- `int.class` tạo component type `int` nếu dùng `Array.newInstance` trực tiếp.
- Đừng xem autoboxing là cơ chế tự chuyển mảng `int[]` thành `Integer[]`; autoboxing áp dụng ở các biểu thức giá trị, không biến đổi hai loại array.

Nếu API thật sự cần mảng primitive, hãy cung cấp API rõ ràng trả về `Object` hoặc một loại mảng primitive cụ thể, thay vì cố nhét chúng vào `<T> T[]`.

### 4.2. Kiểm tra component type tại runtime

Mảng có thể tự báo loại phần tử:

```java
String[] names = allocateWithClass(String.class, 2);
System.out.println(
        names.getClass().getComponentType().getSimpleName());
```

Kết quả:

```text
String
```

Thông tin này xuất phát từ đối tượng mảng thực, không phải chỉ từ ký hiệu `T` ở mã nguồn. Điều đó cũng giải thích vì sao array store check có thể từ chối đối tượng sai kiểu ngay lúc ghi.

## 5. Trường hợp phổ biến nhất: `Collection.toArray`

Nếu đã có một `Collection<T>` hoặc `List<T>` và cần đổi sang mảng, **thường không cần viết lại helper tạo mảng**: Java Collections Framework đã có API hỗ trợ.

### 5.1. `toArray(T[] a)`

Ví dụ:

```java
java.util.List<String> values = java.util.List.of("A", "B");
String[] array = values.toArray(new String[0]);
```

`toArray(T[] a)` dùng component runtime của mảng truyền vào để tạo mảng kết quả khi mảng đầu vào không đủ sức chứa. Nếu `a` có đủ chỗ, kết quả có thể chính là `a`; khi thừa chỗ, vị trí ngay sau phần tử cuối được gán `null` theo hợp đồng API.

Có thể truyền mảng đúng kích thước:

```java
String[] exact = values.toArray(new String[values.size()]);
```

Đừng khẳng định việc truyền mảng độ dài đúng luôn nhanh hơn truyền mảng zero-length: chi phí và tối ưu hóa phụ thuộc JDK, loại collection và hoàn cảnh đo. Hãy chọn cách dễ đọc trước, dùng benchmark khi hiệu năng thật sự quan trọng.

### 5.2. `toArray(IntFunction<T[]> generator)`

Từ Java 11, `Collection` có overload thuận tiện:

```java
String[] array = java.util.List.of("A", "B").toArray(String[]::new);
```

Cách này gọn, thể hiện trực tiếp component type và thường là lựa chọn tốt khi đã có Collection.

**Một điểm quan trọng về đối số truyền cho factory:** default implementation của `Collection.toArray(IntFunction<T[]>)` gọi `generator.apply(0)` rồi chuyển mảng kết quả cho `toArray(T[])`. Vì vậy đừng viết generator với giả định rằng tham số luôn bằng `collection.size()`. Trong chương trình được kiểm chứng ở phần 7, factory thực sự nhận `0` nhưng kết quả vẫn có đủ hai phần tử.

Sự khác nhau:

```java
// Helper tự viết: chủ động gọi factory bằng kích thước yêu cầu.
T[] array = factory.apply(length);

// Collection.toArray(IntFunction): default implementation gọi generator với 0.
// Collection sau đó tự tạo/điền mảng kết quả đúng kích thước.
String[] names = List.of("A", "B").toArray(String[]::new);
```

Dòng dùng `T` ở đầu là **mô tả trong ngữ cảnh generic**, không phải chương trình độc lập. Khi thiết kế một callback dạng `IntFunction<T[]>`, phải đọc hợp đồng của **API gọi callback**, không suy ra ý nghĩa của số nguyên chỉ từ tên interface.

### 5.3. Sao chép từ `Collection<? extends T>` sang mảng T

Nếu nguồn là `Collection<Integer>` còn đích mong muốn `Number[]`, có thể dùng factory:

```java
static <T> T[] copyToArray(
        java.util.Collection<? extends T> source,
        java.util.function.IntFunction<T[]> factory) {
    return source.toArray(factory.apply(0));
}
```

Caller:

```java
Number[] values = copyToArray(
        java.util.List.of(2, 5), Number[]::new);
```

Phương thức tạo một mảng có component type runtime Number, đưa các Integer vào đó hợp lệ vì Integer là subtype của Number. Việc đưa rộng sang Number[] ở đây có chủ đích; khác với chuyện ép một mảng Object[] thành String[] không có bằng chứng.

Nếu API không cần helper riêng, dùng `Collection.toArray(Number[]::new)` trực tiếp cũng có thể đáp ứng cùng mục đích.

## 6. Sử dụng `Class<T>` để copy một collection

Có thể kết hợp hai bước: tạo mảng qua class token và đổ phần tử từ collection vào.

```java
static <T> T[] copyWithClass(
        java.util.Collection<? extends T> source,
        Class<T> componentType) {
    T[] target = allocateWithClass(componentType, source.size());
    return source.toArray(target);
}
```

Đây là phương thức thuộc cùng lớp đã có `allocateWithClass` ở phần 4, và có bản chạy đầy đủ ở phần 7.

Ví dụ:

```java
Number[] result = copyWithClass(
        java.util.List.of(3, 4), Number.class);
```

Khi nguồn có phần tử không tương thích với component type thực của mảng, `Collection.toArray` có thể ném `ArrayStoreException`. Trong một chương trình chỉ sử dụng generics an toàn và truyền token phù hợp, compiler thường ngăn các cách gọi không tương thích. Nhưng nếu dữ liệu đã bị ô nhiễm bởi raw type, unchecked cast hoặc nguồn không tin cậy, runtime array store check vẫn là lớp phòng vệ.

**Cần phân biệt:** `Class<T>` mô tả một component type runtime có thể dùng làm mảng, chứ không thể biểu diễn các đối số generic lồng sâu như `List<String>`. Cách dùng `copyWithClass(..., List.class)` chỉ mô tả mảng của các List, không kiểm tra bên trong từng List có chứa String.

## 7. Chương trình Java 17 hoàn chỉnh

**Tệp `GenericArrayFactoriesDemo.java`:**

```java
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

public class GenericArrayFactoriesDemo {
    static <T> T[] allocateWithFactory(int length, IntFunction<T[]> factory) {
        if (length < 0) throw new IllegalArgumentException("negative length");
        T[] array = Objects.requireNonNull(factory.apply(length), "array");
        if (array.length != length) {
            throw new IllegalArgumentException("unexpected array length");
        }
        return array;
    }

    @SuppressWarnings("unchecked")
    static <T> T[] allocateWithClass(Class<T> componentType, int length) {
        Objects.requireNonNull(componentType, "componentType");
        if (componentType.isPrimitive()) {
            throw new IllegalArgumentException("reference components only");
        }
        return (T[]) Array.newInstance(componentType, length);
    }

    static <T> T[] copyToArray(
            Collection<? extends T> source, IntFunction<T[]> factory) {
        return source.toArray(factory.apply(0));
    }

    static <T> T[] copyWithClass(
            Collection<? extends T> source, Class<T> componentType) {
        T[] target = allocateWithClass(componentType, source.size());
        return source.toArray(target);
    }

    static <T> List<T> newList() {
        return new ArrayList<>();
    }

    public static void main(String[] args) {
        String[] slots = allocateWithFactory(3, String[]::new);
        slots[0] = "A";
        System.out.println("factory=" + Arrays.toString(slots));
        System.out.println("factoryComponent="
                + slots.getClass().getComponentType().getSimpleName());

        Integer[] scores = allocateWithClass(Integer.class, 2);
        scores[0] = 7;
        System.out.println("class=" + Arrays.toString(scores));
        System.out.println("classComponent="
                + scores.getClass().getComponentType().getSimpleName());

        Number[] wider = copyToArray(List.of(2, 5), Number[]::new);
        System.out.println("copiedNumbers=" + Arrays.toString(wider));

        String[] names = List.of("An", "Binh").toArray(String[]::new);
        System.out.println("library=" + Arrays.toString(names));

        AtomicInteger requestedLength = new AtomicInteger(-1);
        String[] viaCollection = List.of("X", "Y").toArray(length -> {
            requestedLength.set(length);
            return new String[length];
        });
        System.out.println("collectionResult=" + Arrays.toString(viaCollection));
        System.out.println("generatorRequested=" + requestedLength.get());

        Number[] fromClass = copyWithClass(List.of(3, 4), Number.class);
        System.out.println("copyClass=" + Arrays.toString(fromClass));

        try {
            String[] words = allocateWithFactory(2, String[]::new);
            Object[] alias = words;
            alias[0] = 99;
        } catch (ArrayStoreException error) {
            System.out.println("storeCheck=" + error.getClass().getSimpleName());
        }

        try {
            allocateWithClass(int.class, 2);
        } catch (IllegalArgumentException error) {
            System.out.println("primitiveGuard=" + error.getMessage());
        }

        List<?>[] lists = new List<?>[2];
        lists[0] = List.of("one");
        lists[1] = List.of(10);
        System.out.println("wildcardArray=" + lists[0] + "|" + lists[1]);
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac --release 17 -Xlint:all GenericArrayFactoriesDemo.java
java GenericArrayFactoriesDemo
```

**Stdout đã kiểm chứng:**

```text
factory=[A, null, null]
factoryComponent=String
class=[7, null]
classComponent=Integer
copiedNumbers=[2, 5]
library=[An, Binh]
collectionResult=[X, Y]
generatorRequested=0
copyClass=[3, 4]
storeCheck=ArrayStoreException
primitiveGuard=reference components only
wildcardArray=[one]|[10]
```

Chương trình đã biên dịch với `--release 17 -Xlint:all` và chạy thành công trong kiểm thử. Các chi tiết quan trọng:

- `slots` thật sự là `String[]` có ba vị trí; vị trí chưa gán có giá trị mặc định `null`.
- `scores` thật sự là `Integer[]` do reflection tạo, không phải `int[]`.
- `copyToArray` dùng factory Number[] để nhận phần tử Integer theo quan hệ subtype.
- `Collection.toArray(String[]::new)` trực tiếp trả về `String[]`.
- Với implementation `List.of(...).toArray(generator)` đã chạy, generator được gọi bằng số 0; Collection vẫn tạo kết quả đúng độ dài sau đó.
- `alias[0] = 99` chứng minh mảng String[] vẫn được JVM kiểm tra đúng component type tại runtime.
- Helper dựa `Class<T>` từ chối primitive token theo hợp đồng đã thiết kế.
- `List<?>[]` hợp lệ nhưng không mang cam kết rằng mọi List bên trong đều có cùng đối số generic.

## 8. Các lỗi biên dịch liên quan cần nhận diện

**Tệp `InvalidArrayCreation.java` — cố ý không biên dịch:**

```java
import java.util.List;

class InvalidArrayCreation {
    static <T> T[] make(int length) {
        return new T[length];
    }

    List<String>[] lists = new List<String>[2];
}
```

Lệnh:

```bash
javac --release 17 InvalidArrayCreation.java
```

Compiler từ chối cả hai biểu thức bằng lỗi có phần cốt lõi:

```text
error: generic array creation
```

Hai trường hợp có cơ sở liên quan nhưng khác nhau:

1. `new T[length]`: T là type variable, không có class runtime độc lập để tạo mảng.
2. `new List<String>[2]`: kiểu phần tử `List<String>` không *reifiable*, do JVM không có component runtime phân biệt tham số generic String.

Trong khi đó:

```java
String[] strings = new String[2];    // Hợp lệ.
List<?>[] lists = new List<?>[2];    // Hợp lệ.
List[] rawLists = new List[2];       // Hợp lệ nhưng là raw type.
```

Đoạn này minh họa các khai báo trong phạm vi có import `List`. `List<?>` là reifiable theo quy tắc của Java do mọi đối số là wildcard không giới hạn. Tuy nhiên, `List<?>[]` có component runtime là List, và **không** kiểm tra được tham số kiểu phần tử bên trong mỗi List.

### `List<String>.class` có giúp được không?

Không. Biểu thức:

```java
// Không hợp lệ:
// Class<List<String>> token = List<String>.class;
```

không có trong cú pháp class literal của Java. `List.class` có thể tạo mảng của List ở mức raw runtime, nhưng không biến nó thành một mảng có component type `List<String>` reified.

Nếu bài toán cần lưu **nhiều List<String>**, thường có thể dùng `List<List<String>>` để tránh tạo generic array. Nếu phải làm việc với mảng do API cũ yêu cầu, cần một giải pháp được kiểm soát, xác thực dữ liệu và giới hạn cast unchecked; không thể yêu cầu JVM tự kiểm tra sâu các đối số generic chỉ bằng `List.class`.

## 9. Vì sao ép `Object[]` thành `T[]` là bẫy?

Một cách làm trông ngắn nhưng nguy hiểm:

```java
// KHÔNG NÊN DÙNG:
@SuppressWarnings("unchecked")
static <T> T[] wrongArray(int length) {
    return (T[]) new Object[length];
}
```

Cast sang `T[]` ở mã nguồn generic không thay đổi **loại mảng được cấp phát**. Đối tượng vẫn là `Object[]`. Nếu caller viết:

```java
// Dùng wrongArray ở trên:
String[] names = wrongArray(2);
```

kết quả có thể ném `ClassCastException` ở phía caller khi cần chuyển Object[] thành String[]. Đây không phải vấn đề “JVM quên generic nên mọi mảng đều dùng được”: runtime biết rất rõ Object[] khác String[].

Hai trường hợp so sánh:

```text
new Object[2] -> runtime type: Object[]
new String[2] -> runtime type: String[]

Đặt cast (T[]) trước Object[] không biến Object[] thành String[].
String[]::new và Array.newInstance(String.class, 2) thật sự tạo String[].
```

Ngoài ra, `ArrayStoreException` là một kiểu lỗi khác: nó xảy ra khi bạn **ghi phần tử sai kiểu vào một mảng tham chiếu** đã có component type thực. `ClassCastException` có thể xảy ra khi bạn **ép tham chiếu mảng** hoặc giá trị trả về sang một kiểu không tương thích.

### Factory cũng cần có hợp đồng rõ ràng

`IntFunction<T[]>` dựa vào caller cung cấp factory có kiểu đúng. Nếu caller cố tình dùng raw type hoặc unchecked cast để cung cấp một factory trả mảng không phù hợp, hệ thống có thể gặp lỗi kiểu ở một thời điểm nào đó. Một chữ ký generic không tự kiểm tra mọi trường hợp đã cố ý vượt qua type safety.

Bởi vậy nên:

- Dùng array constructor reference rõ ràng, ví dụ `String[]::new`.
- Tránh factory `length -> (T[]) new Object[length]`.
- Quy định rõ có chấp nhận `null`, chiều dài khác hay không; helper `allocateWithFactory` ở trên kiểm tra hai yếu tố này.
- Hạn chế dùng `@SuppressWarnings`; chỉ dùng khi có lập luận an toàn tại một điểm nhỏ, ví dụ phản chiếu với `Class<T>` chính xác.

## 10. Factory không biến generics thành reified

Mảng được tạo bằng `String[]::new` hoặc `Array.newInstance(String.class,n)` có component type thật là `String`; nhưng `List<String>` vẫn không có runtime class specialization. `List.class` chỉ đại diện raw `List`, còn `List<String>.class` không hợp lệ.

```java
Object[] names = new String[2];
try {
    names[0] = 12;
} catch (ArrayStoreException expected) {
    System.out.println("Rejected by runtime String[] component type");
}
```

Mảng tham chiếu vẫn covariant, trong khi `List<String>` không gán được cho `List<Object>`. Khi cần đọc từ `Collection<Integer>` để tạo `Number[]`, dùng `Collection<? extends Number>` và factory `Number[]::new`. Tránh unchecked cast `(T[]) new Object[n]`: factory/token cung cấp **bằng chứng component runtime** mà cast đơn thuần không có.

Đây là ranh giới của hai phương pháp cấp phát, không phải lời hứa kiểm tra nested generic arguments hoặc dữ liệu đầu vào thiếu tin cậy.

## 11. Sự khác nhau giữa `Class<T>` và `Class<T[]>`

Đôi khi một API nhận **token của loại phần tử**:

```java
Class<String> component = String.class;
```

Khi đó `Array.newInstance(component, length)` là cách tự nhiên để tạo mảng.

Một API khác có thể đã nhận **token của loại mảng hoàn chỉnh**:

```java
Class<String[]> arrayType = String[].class;
System.out.println(arrayType.getComponentType().getSimpleName());
```

Kết quả:

```text
String
```

`Class<T[]>` mô tả toàn bộ kiểu mảng `T[]`, còn `Class<T>` mô tả kiểu phần tử. Không được truyền `String[].class` như thể nó là `Class<String>`: hai token có ý nghĩa khác nhau.

Ngoài ra, `Class<T>` không biểu diễn đầy đủ các kiểu tham số hóa sâu như `List<String>`. Nếu một thư viện cần lưu mô tả `List<String>` để deserialize hoặc thao tác reflection generic, có thể cần `java.lang.reflect.Type`/`ParameterizedType`/type reference, **không** chỉ `Class<T>`. Nhưng `Type` là mô tả kiểu; không thể truyền một `ParameterizedType` tùy ý cho `Array.newInstance(Class, length)` để tạo component runtime không tồn tại.

## 12. Chiều dài, mảng rỗng và trường hợp biên

### 12.1. Chiều dài bằng 0

Cả `String[]::new` với n = 0 và `Array.newInstance(String.class, 0)` đều tạo được mảng String có độ dài 0. Mảng rỗng vẫn mang runtime component type String; nó không “mất kiểu” vì không có phần tử.

`Collection.toArray(String[]::new)` có thể dùng generator với độ dài 0 như một tín hiệu tạo mảng đúng loại; khi collection có nhiều phần tử hơn, overload chịu trách nhiệm cung cấp mảng kết quả phù hợp.

### 12.2. Chiều dài âm

`new String[-1]` và `Array.newInstance(String.class, -1)` theo hành vi cấp phát mảng có thể ném `NegativeArraySizeException`. Helper `allocateWithFactory` trong ví dụ **cố ý tự kiểm tra và ném `IllegalArgumentException`** để thể hiện một hợp đồng đầu vào riêng; đừng suy ra đó là hành vi mặc định của mọi constructor reference.

### 12.3. Factory trả `null`

`IntFunction<T[]>` là functional interface nên về mặt kiểu trả về có thể trả `null`. Nếu bạn định dùng mảng ngay, nên kiểm tra và báo lỗi gần ranh giới factory. `Objects.requireNonNull(factory.apply(length))` sẽ ném `NullPointerException` với thông báo thích hợp, thay vì để lỗi phát sinh muộn trong code xử lý.

### 12.4. Factory trả sai kích thước

Trong helper tự viết, có thể yêu cầu `array.length == length`. Nhưng `Collection.toArray` có các quy tắc riêng cho mảng truyền vào: nếu quá nhỏ, nó có thể cấp phát mảng mới đúng loại; nếu đủ chỗ, nó điền vào mảng sẵn có theo hợp đồng.

Vì vậy không nên thêm kiểm tra “factory luôn phải trả đúng collection.size()” khi làm việc với `Collection.toArray(IntFunction)`, vì default implementation có thể chủ động gọi factory với 0.

### 12.5. Primitive arrays

`int[]`, `double[]` là các mảng nguyên thủy, khác với `Integer[]`, `Double[]`. `Class<T>`/`T[]` trong generic helper tham chiếu không tự hỗ trợ tạo hoặc trả về mảng primitive theo cách thông thường. Nếu cần mảng primitive, dùng API cụ thể (`new int[n]`, `IntStream.toArray()`...) hoặc reflection với hợp đồng return type rõ ràng.

## 13. Các tình huống thực tế nên chọn cách nào?

| Tình huống | Lựa chọn phù hợp | Lý do |
| --- | --- | --- |
| Chuyển `List<String>` sang `String[]` | `list.toArray(String[]::new)` | Thư viện chuẩn, ít mã, component type rõ ràng. |
| Viết helper cấp phát mảng generic và caller biết loại mảng | `IntFunction<T[]>`, caller truyền `TConcrete[]::new` | Không cần unchecked cast trong helper. |
| Viết framework có sẵn `Class<T>` | `Array.newInstance(Class<T>, n)` | Token cung cấp runtime component type. |
| Sao chép `List<Integer>` sang `Number[]` | `toArray(Number[]::new)` hoặc factory tương ứng | Nhận quan hệ subtype an toàn. |
| Cần tạo `int[]` | Cấp phát primitive trực tiếp/primitive API | `int[]` không phải `Integer[]`. |
| Muốn tạo mảng các `List<String>` | Cân nhắc `List<List<String>>`; nếu cần mảng, chỉ có runtime component List | Không có `List<String>.class`. |
| Dữ liệu từ API legacy/không đáng tin | Xác thực phần tử trước khi lưu ở cấu trúc typed | Factory không kiểm tra sâu nested generics. |
| Không thật sự cần mảng | Giữ `List<T>` | Tránh hạn chế generic array creation và cast. |

Điều quan trọng là **đừng ép generic API phải trả mảng nếu consumer có thể dùng List**. Mảng phù hợp khi thư viện yêu cầu nó, khi cần component type runtime hoặc các thao tác đặc thù mảng. Nếu chỉ cần một tập hợp có kích thước thay đổi, `List<T>` thường diễn đạt nghiệp vụ rõ hơn.

## 14. Bài tập và đáp án

### Bài 1. Viết một helper cấp phát mảng String mà không dùng cast.

**Đáp án:**

```java
static <T> T[] make(int size, java.util.function.IntFunction<T[]> factory) {
    return factory.apply(size);
}

// Gọi bên trong main/method:
String[] names = make(4, String[]::new);
```

Cần bảo đảm caller đưa factory phù hợp. Phiên bản có kiểm tra đầu vào và độ dài được trình bày ở phần 3.

### Bài 2. Đâu là khác biệt giữa `String[]::new` và `String.class`?

**Đáp án:** `String[]::new` là một hàm có thể nhận kích thước và tạo String[]; `String.class` là class token mô tả kiểu phần tử String, cần truyền vào API như `Array.newInstance` để tạo mảng.

### Bài 3. Vì sao `new T[5]` không hợp lệ?

**Đáp án:** Type variable T không tự cung cấp runtime component type cụ thể cho mảng mới; Java từ chối generic array creation. Dùng factory hoặc `Class<T>` do caller cung cấp.

### Bài 4. Cho `List<Integer>`, có thể tạo `Number[]` không?

**Đáp án:** Có. Caller có thể viết `Number[] result = integers.toArray(Number[]::new)` hoặc dùng helper nhận `Collection<? extends Number>` và factory Number[]. Phần tử Integer gán được vào mảng Number[].

### Bài 5. `Collection.toArray(IntFunction)` có luôn gọi factory bằng `collection.size()` không?

**Đáp án:** Không. Default implementation sử dụng `generator.apply(0)` rồi xử lý mảng theo `toArray(T[])`. Cần tuân theo hợp đồng của API gọi factory; không mặc định tham số bằng size.

### Bài 6. Tại sao `(T[]) new Object[5]` không an toàn?

**Đáp án:** Đối tượng mảng thực vẫn là Object[]. Khi caller kỳ vọng mảng có component type như String[], runtime có thể ném ClassCastException. Cast không đổi loại mảng.

### Bài 7. `List<String>.class` có thể được truyền vào `Array.newInstance` không?

**Đáp án:** Không vì `List<String>.class` không phải class literal hợp lệ. `List.class` chỉ đại diện kiểu runtime List sau erasure, không có kiểm tra sâu đối số String.

### Bài 8. Một helper từ chối `int.class` khi nhận `Class<T>` có mâu thuẫn Java reflection hỗ trợ int[] không?

**Đáp án:** Không. Reflection hỗ trợ tạo primitive array khi gọi trực tiếp với `int.class`. Helper trả `T[]` ở ví dụ chủ động giới hạn vào reference component types để tránh hiểu nhầm với primitive arrays.

### Bài 9. Factory trả mảng String[4] khi helper yêu cầu độ dài 2. Đây là lỗi Java generics hay lỗi hợp đồng?

**Đáp án:** Không nhất thiết là lỗi kiểu; cả hai vẫn là String[]. Nhưng nếu helper cam kết mảng có đúng độ dài n, cần kiểm tra và báo lỗi hợp đồng, như `allocateWithFactory`.

### Bài 10. Nếu một List chứa phần tử sai kiểu do raw operation, việc chuyển nó bằng `toArray(String[]::new)` có tự sửa dữ liệu không?

**Đáp án:** Không. Khi chuyển dữ liệu sang mảng String, runtime có thể phát hiện phần tử không tương thích và ném ArrayStoreException. Cần sửa nguồn heap pollution hoặc xác thực/chuyển đổi phần tử theo quy tắc nghiệp vụ.

## 15. Tổng kết

- Không tạo được `new T[n]` vì generic type variable không tự cung cấp component type runtime cho mảng.
- `IntFunction<T[]>` giúp caller cung cấp factory như `String[]::new`, tránh unchecked cast trong helper.
- `Class<T>` kết hợp `Array.newInstance` dùng được khi caller có token của kiểu phần tử; hãy cô lập cast và xử lý primitive token theo hợp đồng API.
- `Collection.toArray(String[]::new)` thường là lựa chọn gọn nhất để chuyển collection thành mảng. Chú ý generator có thể nhận `0` theo default implementation, không nhất thiết nhận `size()`.
- Runtime array type được giữ thật: String[] khác Object[], và JVM thực thi array store check.
- Class token/factory cho loại container `List` không reify đối số `String` nằm sâu trong `List<String>`.
- Khi không thật sự cần mảng, `List<T>` thường giúp API đơn giản và tránh các hạn chế generic array creation.

**Quy tắc thực hành:** Hãy đưa việc tạo mảng về nơi có thông tin kiểu cụ thể (array factory hoặc class token), để mảng được tạo **đúng runtime component type** thay vì phụ thuộc vào cast không kiểm tra được.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-arrays-008">Giới hạn và cách khoanh vùng phép ép kiểu khi lưu trữ bằng Object&#91;&#93;</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn và cách khoanh vùng phép ép kiểu khi lưu trữ bằng `Object[]` trong Java Generics

Khi tự cài đặt một cấu trúc dữ liệu generic như `Stack<T>`, `Buffer<T>` hoặc `Store<T>`, ta thường cần bộ lưu trữ dạng mảng. Nhưng Java **không cho phép viết `new T[n]`** vì type variable `T` không phải kiểu có thể xác minh đầy đủ lúc chạy (*non-reifiable type*). Một hướng thực tế là giữ **`Object[]` làm chi tiết triển khai nội bộ**, chỉ nhận giá trị kiểu `T` từ API được compiler kiểm tra và thực hiện phép ép kiểu có kiểm soát tại điểm đọc:

```java
// Snippet nằm trong một lớp generic, không phải chương trình độc lập.
class SmallStore<T> {
    private final Object[] elements = new Object[10];

    void set(int index, T value) {
        elements[index] = value;
    }

    T get(int index) {
        @SuppressWarnings("unchecked")
        T value = (T) elements[index];
        return value;
    }
}
```

Đoạn trên minh họa ý tưởng, **chưa có kiểm tra chỉ số hay trạng thái phần tử**; chương trình đầy đủ có các kiểm tra này ở mục 6. Điều kiện cốt lõi là: **mọi giá trị được lưu vào mảng phải đi qua những đường ghi bảo đảm hợp đồng kiểu `T`**.

Một unchecked cast đặt gọn ở `get` có thể được biện minh bằng invariant của lớp. Tuy nhiên, annotation `@SuppressWarnings("unchecked")` **không tự tạo ra tính an toàn**: nếu dữ liệu đi qua raw type, reflection, unchecked alias hoặc một API công khai làm lộ mảng, invariant có thể bị phá.

> Nguyên tắc thiết kế: giữ `Object[]` ở trạng thái `private`, không trả nó dưới danh nghĩa `T[]`; khoanh vùng phép ép kiểu nhỏ nhất; kiểm soát toàn bộ đường ghi; khi API phải trả mảng thật, để caller cung cấp kiểu thành phần hoặc array factory.

## 1. Vì sao chọn `Object[]` thay vì `T[]`?

Với kiểu generic thông thường, compiler xóa type variable không có bound thành `Object`; nếu `T extends Number`, erasure là `Number`. Nhưng ở mã nguồn, `T` có thể lần lượt đại diện cho `String`, `Integer` hay nhiều kiểu tham chiếu khác.

```java
class GenericBox<T> {
    T[] values; // Được khai báo tham chiếu T[]

    void allocate(int n) {
        // values = new T[n]; // LỖI: generic array creation
    }
}
```

Mảng Java cần có **runtime component type** thực sự. Ví dụ, `new String[3]` tạo mảng runtime `String[]`; một cố gắng gán `Integer` vào mảng này qua tham chiếu `Object[]` sẽ bị JVM chặn.

Trong khi đó, `new Object[3]` có runtime type chính xác là `Object[]`. Nó có thể giữ nhiều loại **tham chiếu** đến đối tượng (bao gồm wrapper như `Integer`), nhưng chính mảng không tự chứng minh được mọi giá trị là `T`.

```java
Object[] values = new Object[3];
values[0] = "An";
values[1] = 42;
values[2] = null;
```

Các phép ghi trên hợp lệ đối với `Object[]`. Vì thế, dùng `Object[]` làm storage cần **invariant ở cấp cấu trúc dữ liệu**, thay vì trông chờ JVM tự kiểm tra `T` khi ghi.

## 2. Một invariant an toàn cần phát biểu chính xác thế nào?

Giả sử đang cài đặt `Store<T>` và chỉ cung cấp:

```java
void add(T value);
void set(int index, T value);
T get(int index);
```

Ta cần duy trì các điều kiện:

1. **Mảng nội bộ không thoát ra ngoài**: trường `Object[] elements` là `private` và không bị trả ra như một tham chiếu cho caller tự sửa.
2. **Mọi đường ghi đều đi qua API typed**: `add(T)` và `set(..., T)` nhận giá trị được kiểm tra theo `T` ở các lời gọi generic hợp lệ.
3. **Chỉ số hợp lệ**: giá trị trong vùng `0 <= index < size` là những giá trị đã được thêm/set theo hợp đồng; vùng capacity chưa dùng không bị xem như phần tử logic.
4. **Không lạm dụng raw/unchecked**: nếu client cast sang raw `Store` rồi gọi `add(42)` trên một `Store<String>`, điều kiện (2) đã bị phá; compiler có thể cảnh báo nhưng không cấm hoàn toàn mã cố ý không an toàn.
5. **Không có lối ghi khác**: native code, reflection đặc quyền, truy cập nội bộ không được kiểm soát hoặc thao tác không an toàn có thể phá giả định; invariant thuộc phạm vi API và thực thi thông thường mà thiết kế kiểm soát.
6. **Không bỏ qua concurrency**: nếu cấu trúc được chia sẻ giữa threads, phải cung cấp chính sách đồng bộ phù hợp. Invariant kiểu không tự bảo đảm visibility, atomicity hay thread-safety.

Nếu tất cả đường ghi bình thường đã đảm bảo `T`, thì đọc một phần tử trong vùng đã dùng và xem nó như `T` là lập luận hợp lý. Điều được chứng minh là **bất biến dữ liệu trong một thiết kế đóng**, không phải khả năng JVM xác nhận đối số generic bằng runtime cast.

### Invariant khác với kiểm tra lúc chạy

Với `Store<String>`, bytecode của `add(T)` khi `T` không bound có thể nhận tham số erasure `Object`, và `Object[]` cũng nhận `Object`. An toàn kiểu đến từ compiler kiểm tra các caller được viết bằng `Store<String>`, chứ không phải JVM phát hiện nhãn `String` trên mảng ở mọi lần `add`.

Do đó, nếu caller cố tình vượt qua hệ thống kiểu bằng raw type hoặc unchecked cast, cấu trúc có thể bị **heap pollution**. Một unchecked cast ở `get` không thể sửa dữ liệu đã sai.

## 3. Cách khoanh vùng `(T)` tại một điểm đọc

Thay vì rải các cast ở khắp nơi, nên giữ chúng ngay tại biên đọc nội bộ:

```java
T get(int index) {
    java.util.Objects.checkIndex(index, size);

    @SuppressWarnings("unchecked")
    T value = (T) elements[index];

    return value;
}
```

Ba dòng quan trọng:

- `Objects.checkIndex(index, size)` xác nhận chỉ số nằm trong vùng phần tử logic, thay vì toàn bộ capacity của mảng. Lỗi chỉ số gây `IndexOutOfBoundsException`, không liên quan generic.
- `(T) elements[index]` biểu diễn hợp đồng của cấu trúc: “phần tử đã lưu thuộc `T`”.
- Annotation được đặt ngay tại **biến local có phép cast**; không tắt cảnh báo cho cả class hay cả project.

**Cảnh báo unchecked không được giải quyết bằng annotation:** nó chỉ được **khoanh vùng** sau khi đã chứng minh các đường ghi bảo toàn invariant.

Nếu `T` không bound, erasure của `T` là `Object`; cast trong `get` có thể không kiểm tra thêm gì ở runtime. Khi caller nhận kết quả dưới kiểu cụ thể, compiler có thể chèn cast tại nơi sử dụng:

```java
Store<String> store = new Store<>(2);
store.add("Java");

String text = store.get(0); // Compiler kiểm tra lời gọi theo String
```

Nếu invariant bị phá và giá trị thật là `Integer`, lỗi `ClassCastException` có thể chỉ bộc lộ **ở caller** khi kết quả phải được xem là `String`. Vì vậy đừng khẳng định “đã cast trong `get` thì mọi sai kiểu sẽ bị phát hiện ngay trong `get`”.

Nếu `T extends Number`, cast khi dùng type variable có thể kiểm tra erasure `Number`, nhưng nó vẫn không bảo đảm kiểu chính xác `Integer` khi caller gắn `T = Integer`.

## 4. Những đường phá invariant phổ biến

### 4.1. Đưa raw type vào API

```java
// Snippet minh họa cố tình không an toàn.
Store<String> strings = new Store<>(1);
Store raw = strings;
raw.add(42); // Unchecked warning: bypass kiểm tra String của caller

// String bad = strings.get(0); // Có thể ném ClassCastException
```

Raw `Store` không còn ràng buộc `T = String` ở lần gọi `add`. Vì bytecode có thể nhận `Object`, số nguyên sau boxing có thể đi vào bộ lưu trữ `Object[]`.

Dù `Object[]` là trường private, compiler không thể ngăn mọi client cố ý bỏ qua hệ thống kiểu ở **API công khai**. Thiết kế bảo đảm an toàn đối với các lần sử dụng generic đúng kiểu, không hứa an toàn tuyệt đối trước mã tùy tiện cố tạo unchecked operations.

### 4.2. Trả thẳng mảng nội bộ

```java
// ĐỪNG làm thế nếu API cần giữ invariant:
Object[] exposed() {
    return elements; // Caller có thể sửa phần tử tùy ý
}
```

Ngay cả caller dùng `Store<String>` vẫn có thể làm:

```java
Object[] data = store.exposed();
data[0] = 123; // Không cần raw Store; invariant đã bị phá
```

Chỉ `private Object[]` **không đủ**, nếu class lại công khai một getter trả về chính mảng đó. Muốn trả snapshot dạng `Object[]` cho một API đặc thù, ít nhất phải sao chép và xác định hợp đồng rõ ràng; trong đa số API generic, nên trả một `List<T>`, phần tử `T`, hoặc mảng đúng component type qua factory.

### 4.3. Cho phép đường ghi khác không kiểm tra kiểu

Nếu có method nhận `Object` rồi ghi thẳng vào storage:

```java
// Ví dụ sai trong một Store<T>:
void addAnything(Object value) {
    elements[size++] = value;
}
```

Thì lời hứa “mọi phần tử thuộc `T`” không còn đúng. Khi lớp thực sự cần nhận dữ liệu dạng `Object` từ bên ngoài, phải kiểm tra bằng một `Class<T>` token (nếu phù hợp), validator hoặc chuyển đổi được chứng minh trước khi lưu.

### 4.4. Nhầm lỗi kiểu với lỗi quản lý mảng

Một cấu trúc dùng `Object[]` còn phải quản lý `size`, `capacity`, giới hạn chỉ số và chính sách `null`. Việc cast đúng không cứu được lỗi `ArrayIndexOutOfBoundsException`, truy cập phần tử chưa khởi tạo hoặc mất dữ liệu do resize không sao chép đủ.

Mảng Java chứa các giá trị `null` mặc định ở vùng chưa dùng. Do đó cần phân biệt **`size`** (số phần tử logic) với **`elements.length`** (dung lượng cấp phát).

## 5. Vì sao không được công khai `(T[]) elements`?

Giả sử `elements` được tạo bằng `new Object[10]`:

```java
// Cảnh báo: đây là API thiết kế sai nếu hứa trả runtime T[].
@SuppressWarnings("unchecked")
T[] unsafeArray() {
    return (T[]) elements;
}
```

Khi `T` không bound, phép cast có thể đi qua erasure là `Object[]`; nhưng object thực tế **vẫn là `Object[]`**. Nếu caller yêu cầu:

```java
Store<String> store = new Store<>(2);
// String[] strings = store.unsafeArray(); // Có thể ClassCastException
```

Thì caller trông chờ `String[]`, trong khi runtime array class không tương thích. Annotation `@SuppressWarnings` không thay đổi runtime component type.

**Khác biệt then chốt:**

- `(T) elements[i]`: có thể nằm trong một invariant kiểu được kiểm soát, vì một **giá trị phần tử** thực tế có thể là `String`.
- `(T[]) elements`: không hợp lệ làm lời hứa chung về runtime array class, vì bản thân đối tượng mảng `Object[]` không phải một `String[]`.

Đặt một cast unchecked nhỏ quanh **từng giá trị đã được bảo vệ** khác bản chất với việc gắn nhãn cho **toàn bộ object mảng** không có runtime component type tương ứng.

Một ngoại lệ về thiết kế: nếu bộ lưu trữ thực sự được khởi tạo qua `Array.newInstance(String.class, n)`, mảng runtime có thể là `String[]` đúng. Nhưng đó không còn là tình huống *backing array `Object[]` được tạo bằng `new Object[]`* đang phân tích.

## 6. Chương trình Java 17+ hoàn chỉnh với phép cast được khoanh vùng

Lưu toàn bộ nội dung sau vào **`ObjectArrayStoreDemo.java`**:

```java
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntFunction;

public class ObjectArrayStoreDemo {
    static final class Store<T> {
        private Object[] elements;
        private int size;

        Store(int capacity) {
            if (capacity < 0) {
                throw new IllegalArgumentException("capacity");
            }
            elements = new Object[capacity];
        }

        void add(T value) {
            if (size == elements.length) {
                elements = Arrays.copyOf(
                        elements,
                        Math.max(1, elements.length * 2));
            }
            elements[size++] = value;
        }

        T get(int index) {
            Objects.checkIndex(index, size);

            @SuppressWarnings("unchecked")
            T result = (T) elements[index];

            return result;
        }

        void set(int index, T value) {
            Objects.checkIndex(index, size);
            elements[index] = value;
        }

        T[] toArray(IntFunction<T[]> factory) {
            T[] result = factory.apply(size);
            if (result.length != size) {
                throw new IllegalArgumentException("Array length mismatch");
            }
            System.arraycopy(elements, 0, result, 0, size);
            return result;
        }
    }

    public static void main(String[] args) {
        Store<String> names = new Store<>(1);
        names.add("An");
        names.add("Binh");
        names.set(1, "Lan");

        System.out.println("Stored: "
                + names.get(0) + ", " + names.get(1));

        String[] copy = names.toArray(String[]::new);
        System.out.println("Typed copy: " + Arrays.toString(copy));
        System.out.println("Array runtime type: "
                + copy.getClass().getTypeName());

        Object[] genericArray = new Object[] { "A", "B" };
        try {
            String[] impossible = (String[]) genericArray;
            System.out.println(impossible.length);
        } catch (ClassCastException exception) {
            System.out.println("Object[] cast: ClassCastException");
        }

        Object[] view = copy;
        try {
            view[0] = 42;
        } catch (ArrayStoreException exception) {
            System.out.println("Array write: ArrayStoreException");
        }

        // Đoạn cuối cố ý phá invariant bằng raw type.
        Store<String> polluted = new Store<>(1);
        Store raw = polluted;
        raw.add(42);

        try {
            String item = polluted.get(0);
            System.out.println(item);
        } catch (ClassCastException exception) {
            System.out.println("Raw alias: ClassCastException");
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 -Xlint:rawtypes -Xlint:unchecked ObjectArrayStoreDemo.java
java ObjectArrayStoreDemo
```

**Standard output** (không bao gồm compiler warnings):

```text
Stored: An, Lan
Typed copy: [An, Lan]
Array runtime type: java.lang.String[]
Object[] cast: ClassCastException
Array write: ArrayStoreException
Raw alias: ClassCastException
```

Lưu ý kiểm chứng:

1. `Store<String>` nhận `"An"`, `"Binh"`, rồi cập nhật vị trí 1 thành `"Lan"`. Không có raw alias nào được dùng để sửa `names`, vì thế việc đọc theo `String` đúng với invariant.
2. Khi capacity ban đầu là 1 và gọi `add` lần thứ hai, `Arrays.copyOf` tăng kích thước mảng `Object[]`; **runtime component class của storage vẫn là `Object`**, điều này không làm lỗi vì storage được bao kín.
3. `toArray(String[]::new)` tạo `String[]` thật rồi dùng `System.arraycopy` chép các phần tử. Caller có mảng typed riêng và không sở hữu storage nội bộ.
4. Ép một mảng thực `Object[]` sang `String[]` thất bại bằng `ClassCastException`, dù toàn bộ phần tử hiện có trong nó đều là các chuỗi. **Kiểu runtime của mảng** mới là điều kiện quyết định cast này.
5. `String[]` có thể tham chiếu qua `Object[]`, nhưng ghi `Integer` vào nó bị từ chối bởi `ArrayStoreException`. Đây là cơ chế kiểm tra component type ở runtime.
6. Raw alias của `Store<String>` cho phép một lời gọi `add(42)` **có cảnh báo unchecked**. Sau đó, khi caller lấy phần tử để dùng như `String`, sẽ có `ClassCastException`. Bài thử cuối là ví dụ **cố ý làm sai**, không phải cách dùng đề xuất.

Phần `@SuppressWarnings("unchecked")` chỉ nằm ở local variable trong `get`; warning của raw alias cuối chương trình được giữ lại để bạn quan sát tại thời điểm biên dịch.

### Về tính an toàn của `toArray` trong ví dụ

Chữ ký `T[] toArray(IntFunction<T[]> factory)` yêu cầu caller cung cấp một factory phù hợp với `T`, ví dụ `String[]::new` cho `Store<String>`. Compiler kiểm tra kiểu của factory nếu caller không dùng raw/unchecked tricks.

Phương thức không trả mảng `Object[] elements`. Nó gọi factory để nhận một mảng thực có component type đã chọn, kiểm tra độ dài rồi sao chép giá trị sang đó. Nếu factory thực sự tạo mảng sai kiểu thông qua đường truy cập không an toàn, `System.arraycopy` có thể phát hiện phần tử không tương thích bằng kiểm tra runtime; nó **không** làm cho một factory không đáng tin trở nên đúng kiểu.

Trong code sản xuất, còn nên kiểm tra factory không trả `null` và xây dựng API size/capacity theo hợp đồng. Ví dụ giữ ngắn để tập trung vào câu hỏi về unchecked cast.

## 7. Ranh giới hợp lý của `@SuppressWarnings("unchecked")`

Có ba phạm vi thường thấy:

```java
// Phạm vi quá rộng: dễ che các unchecked lỗi ở nhiều chỗ.
@SuppressWarnings("unchecked")
class WholeClass<T> {
    // ...
}
```

```java
// Phạm vi hẹp hơn: chỉ một method, nhưng có thể che nhiều cast trong method.
@SuppressWarnings("unchecked")
T get(int index) {
    return (T) elements[index];
}
```

```java
// Phạm vi thường đáng ưu tiên trong ví dụ Object[]:
T get(int index) {
    Objects.checkIndex(index, size);
    @SuppressWarnings("unchecked")
    T value = (T) elements[index];
    return value;
}
```

Mục tiêu không phải “càng ít warning càng tốt” mà là **cho những cảnh báo khác vẫn xuất hiện**. Khi người bảo trì thêm một cast mới ở nơi khác, compiler cần tiếp tục báo để họ đánh giá tính an toàn của nó.

Một chú thích thiết kế hữu ích (ở mã thư viện thực tế) thường giải thích:

- Vì sao mảng chỉ chứa các giá trị được thêm qua API `T`.
- Vì sao không có đường cho caller sửa `Object[]` trực tiếp.
- Vì sao chỉ đọc các phần tử có chỉ số đã khởi tạo hợp lệ.
- Vì sao phép cast không được thực hiện ở những vị trí khác.

Nếu không đưa ra được lập luận cụ thể, không nên dùng `@SuppressWarnings` chỉ để “làm compiler im lặng”.

## 8. Hãy ưu tiên API chứa/đọc dữ liệu typed thay vì rải cast cho caller

Một API tốt làm cho caller chỉ phải làm việc với `T`:

```java
// Dạng chữ ký mong muốn:
void add(T item);
T get(int index);
void set(int index, T item);
int size();
```

Một API kém buộc client đọc `Object` rồi tự cast khắp nơi:

```java
// Thiết kế kém cho một cấu trúc tự nhận là generic:
Object getRaw(int index);
Object[] exposeStorage();
```

Khi `Store<T>` chịu trách nhiệm duy trì invariant và che storage, unchecked cast có thể được giới hạn ở **một biên đọc** bên trong class. Caller được compiler kiểm tra khi dùng `Store<String>` mà không phải thêm cast `(String)` thủ công.

Ngược lại, nếu công khai `Object[]`, mọi caller có thể phá invariant mà không cần dùng raw type. Việc phân tán cast cho caller khiến trách nhiệm an toàn bị chia nhỏ và khó kiểm toán.

### Trường hợp `T` có thể là `null`

Một `Store<T>` thông thường có thể cho phép lưu `null` vì kiểu tham chiếu generic không tự đồng nghĩa “non-null”. Phép cast `(T) null` không ném `ClassCastException`. Nếu API không chấp nhận `null`, nên thực thi quy tắc đó ngay tại `add`/`set` bằng `Objects.requireNonNull(value)` và tài liệu hóa hợp đồng.

Đừng xem `null` trong vùng capacity chưa dùng là một phần tử logic. Trường `size` tồn tại để phân biệt vùng dữ liệu đã thêm với vùng còn trống.

## 9. Khi nào dùng `Class<T>` thay vì chấp nhận unchecked cast theo invariant?

Một `Class<T>` token hữu ích khi bạn cần **kiểm tra class runtime tại ranh giới nhận dữ liệu** hoặc **tạo một mảng runtime thuộc đúng class phần tử**:

```java
// Snippet: Class<T> không mang thông tin generic lồng nhau.
static <T> T checkedValue(Object input, Class<T> type) {
    return type.cast(input);
}
```

Với `String.class`, `type.cast(input)` thật sự kiểm tra `input` có thể coi là `String`. Đây là sự khác biệt so với cast `(T)` bên trong generic method không được truyền type token: `T` không có runtime class identity riêng cho từng type argument.

Dùng token để tạo array:

```java
static <T> T[] typedArray(Class<T> elementType, int n) {
    @SuppressWarnings("unchecked")
    T[] result = (T[]) java.lang.reflect.Array.newInstance(elementType, n);
    return result;
}
```

Khi gọi `typedArray(String.class, 3)`, phản xạ tạo một mảng runtime `String[]`, không phải một `Object[]` được “ngụy trang”. Cast unchecked ở đây được biện minh bằng mối liên hệ giữa `elementType` và đối tượng array do chính method tạo.

Nhưng `Class<T>` chỉ mô tả một runtime class. Không có `List<String>.class` để bảo đảm toàn bộ phần tử của một danh sách có type argument `String`; với dữ liệu generic lồng nhau, cần validator cấu trúc hoặc API kiểu hóa đáng tin.

**Chọn giải pháp theo yêu cầu:**

- Nếu storage chỉ cần giữ `T` trong class đóng: `Object[]` + invariant + cast cục bộ có thể đủ.
- Nếu người dùng cần nhận `String[]` đúng runtime type: dùng `Class<T>`, array factory hoặc mảng mẫu.
- Nếu nguồn giá trị là `Object` không đáng tin: kiểm tra bằng `Class<T>.cast` hoặc validator thích hợp trước khi lưu.

## 10. Các lựa chọn thay thế tránh hoặc giảm cast

### 10.1. Dùng `List<T>`

```java
class ListBackedStore<T> {
    private final java.util.List<T> elements = new java.util.ArrayList<>();

    void add(T item) {
        elements.add(item);
    }

    T get(int index) {
        return elements.get(index);
    }
}
```

Đây thường là cách đơn giản nhất: sử dụng trực tiếp API generic chuẩn, không phải tự hiện thực bộ lưu trữ và invariant của `Object[]`. Việc một implementation bên dưới có thể dùng mảng không buộc code ứng dụng phải tiếp xúc với cast.

### 10.2. Nhận một array factory

```java
static <T> T[] allocate(int n, java.util.function.IntFunction<T[]> factory) {
    return factory.apply(n);
}

String[] names = allocate(3, String[]::new);
```

Phần `String[]::new` đặt việc tạo mảng ở nơi đã có component type cụ thể. Phương thức generic dùng factory, không cần viết `new T[n]`.

### 10.3. Dùng API `toArray` của collection

```java
java.util.List<String> values = java.util.List.of("An", "Binh");

String[] a = values.toArray(new String[0]);
String[] b = values.toArray(String[]::new);
Object[] c = values.toArray();
```

Hai cách đầu trả mảng `String[]` đúng runtime type vì caller cung cấp mẫu/generator. `toArray()` không tham số trả `Object[]`; đừng ép kết quả đó thành `String[]` rồi cho rằng mọi phần tử `String` sẽ làm cast thành công.

## 11. Cảnh báo về `Arrays.copyOf`, reflection và mảng trả ra ngoài

`Arrays.copyOf(Object[] original, int newLength)` trả `Object[]` với runtime component class theo overload đó và loại mảng nguồn; khi nguồn là `Object[]` thật, bản copy vẫn là `Object[]`.

```java
Object[] internal = new Object[] { "An", "Binh" };
Object[] copied = java.util.Arrays.copyOf(internal, 2);
// copied vẫn có runtime class là Object[]
```

Việc copy nội dung không tự chuyển kiểu component thành `String`. Để yêu cầu mảng đích cụ thể, dùng một API có type token/mảng mẫu/factory tương ứng.

Khi chỉ muốn snapshot tạm phục vụ duyệt dữ liệu mà không để client sửa storage gốc, bản sao `Object[]` có thể hữu ích nếu hợp đồng công khai rõ ràng rằng nó chỉ là `Object[]`. Không nên hứa trả `T[]` đúng runtime type dựa trên bản sao đó.

Khi trả `List<T>` từ storage, cần nhớ raw/unchecked alias có thể làm hỏng cả chính dữ liệu trước khi copy. Một biến `List<T>` chỉ an toàn nếu nguồn của nó giữ được invariant; với dữ liệu không đáng tin, kiểm tra phần tử ở biên nhập vẫn quan trọng.

## 12. Vấn đề đồng thời và mutation

Một class dùng `Object[]` tự tăng capacity như ví dụ có thể gặp race khi nhiều thread đồng thời gọi `add`, `set` hoặc `get`:

- `size` có thể bị cập nhật mất lượt.
- Resize có thể bị ghi đè hoặc đọc nhầm bản mảng cũ.
- Thread khác có thể không quan sát đầy đủ dữ liệu mới nếu không có quan hệ *happens-before* thích hợp.

Đây là vấn đề **concurrency**, không do unchecked cast trực tiếp gây ra. Phạm vi bài này chỉ hứa thiết kế generic đúng ở cách dùng tuần tự; để dùng đa luồng, cần đồng bộ hoặc cấu trúc dữ liệu chuyên cho concurrency.

Tương tự, một `String[]` được tạo khi `toArray(String[]::new)` là snapshot cấu trúc tại thời điểm method copy trong cách dùng tuần tự. Nó không có nghĩa mảng ấy tự cập nhật khi `Store` thay đổi sau đó.

## 13. Sai lầm phổ biến

**1. “Đã dùng `private Object[]` thì không thể sai kiểu.”** Chưa đủ. Nếu API công khai trả nguyên mảng hoặc cho phép nhận `Object` rồi ghi thẳng, invariant bị phá.

**2. “`@SuppressWarnings("unchecked")` làm cast an toàn.”** Sai. Annotation chỉ ẩn cảnh báo trong phạm vi được gắn.

**3. “Cast `(T)` luôn xác nhận class cụ thể của T tại runtime.”** Sai. Với `T` không bound, erasure là `Object`; class cụ thể có thể chỉ được kiểm tra ở caller.

**4. “Cast `(T[]) new Object[n]` sẽ tạo `String[]` khi T=String.”** Sai. Runtime class của object mảng vẫn là `Object[]`.

**5. “Vì `elements` chứa toàn String nên có thể cast cả mảng `Object[]` thành `String[]`.”** Sai. Array cast xét runtime component type của mảng, không quét phần tử để đoán class.

**6. “Khoanh vùng cast bằng cách gắn `@SuppressWarnings` trên class là tốt nhất.”** Không. Phạm vi càng lớn càng dễ che các unsafe operation mới.

**7. “Kiểm tra chỉ số theo `elements.length` là đủ.”** Không. Vùng capacity chưa sử dụng không nhất thiết là vùng phần tử logic; thường phải kiểm tra theo `size`.

**8. “Raw type không ảnh hưởng bộ lưu trữ vì mảng đang private.”** Không. Raw alias có thể truyền một giá trị sai `T` qua phương thức `add` công khai.

**9. “Có `T[]` trả về nghĩa là array thật có đúng runtime component class.”** Không, nếu implementation chỉ trả `Object[]` giả dạng bằng unchecked cast.

**10. “`List<T>` cần `new T[n]` mới hoạt động được.”** Không. Collection generic có thể dùng bộ lưu trữ nội bộ và API typed mà không tạo trực tiếp mảng `T[]` ở code ứng dụng.

## 14. Bài tập tự kiểm tra

**Câu 1.** `private Object[] elements` và `T get(int index)` có thể phối hợp trong một class generic không?

Có, nếu class kiểm soát mọi đường ghi, chỉ đọc phần tử đúng chỉ số, và phép cast tại `get` được biện minh bằng invariant.

**Câu 2.** Vì sao `(T) elements[index]` khác `(T[]) elements` về tính an toàn?

Một đối tượng phần tử thực tế có thể đúng kiểu `T` theo invariant. Nhưng một mảng runtime `Object[]` không biến thành `String[]` hay `Integer[]` nhờ cast tham chiếu.

**Câu 3.** Có nên trả chính `Object[] elements` để caller dùng trực tiếp không?

Thông thường không, nếu muốn bảo toàn invariant generic. Caller có thể sửa bất kỳ phần tử nào mà không qua `add(T)`.

**Câu 4.** Sau khi raw alias gọi `add(42)` trên một `Store<String>`, phương thức `get` có thể gặp điều gì?

Khi bên gọi cần `String`, `Integer` có thể bị cast sang `String` và gây `ClassCastException`. Phép cast trong `get` không tự sửa dữ liệu sai.

**Câu 5.** Muốn caller nhận `String[]` có runtime class đúng, nên làm gì?

Nhận `String.class`/tương ứng `Class<T>` để tạo array bằng reflection, hoặc nhận factory `String[]::new` và sao chép dữ liệu vào mảng đó.

**Câu 6.** Vì sao nên giới hạn `@SuppressWarnings("unchecked")` vào một local variable?

Để dễ kiểm toán chỗ cast đã được chứng minh an toàn và không che các unchecked warnings khác trong cùng lớp.

**Câu 7.** `Arrays.copyOf(new Object[]{"A"}, 1)` có trả `String[]` không?

Không. Nó sao chép một mảng `Object[]` thực, nên bản sao trong trường hợp này vẫn có runtime component type `Object`.

## 15. Checklist thiết kế `Object[]` backing storage

Khi xem xét một cấu trúc generic tự cài đặt, lần lượt kiểm tra:

1. **Storage được bao kín?** `private Object[]`, không có getter trả alias có thể sửa.
2. **Tất cả phương thức ghi đều typed?** Tránh đường ghi nhận `Object` thiếu kiểm tra.
3. **Vùng đọc đúng?** Chỉ số phải nhỏ hơn `size`, không phải chỉ nhỏ hơn capacity.
4. **Cast ở đâu?** Tập trung tại một điểm đọc hoặc biên chuyển đổi có invariant rõ.
5. **Suppress ở mức nào?** Chỉ đoạn nhỏ cần thiết; giải thích lý do an toàn.
6. **Có API trả array không?** Phải tạo mảng runtime đúng kiểu bằng factory/token, không làm giả từ `Object[]`.
7. **Có raw/unchecked clients không?** Nhận thức rằng caller cố ý bỏ hệ thống kiểu có thể phá invariant.
8. **Có null/concurrency không?** Tài liệu hóa và kiểm soát theo hợp đồng API.
9. **Có giải pháp đơn giản hơn không?** Nếu không bắt buộc quản lý array, dùng `ArrayList<T>` hoặc collection phù hợp.

**Kết luận:** `Object[]` là công cụ hợp lý để lưu trữ nội bộ trong một cấu trúc generic khi `new T[]` bị cấm, nhưng JVM không tự bảo đảm các phần tử của nó thuộc `T`. Tính an toàn phụ thuộc một invariant được duy trì qua các đường ghi typed và bộ lưu trữ không bị lộ. Hãy **khoanh vùng unchecked cast ở điểm đọc**, không dùng cast để giả mạo runtime `T[]`, và dùng `Class<T>` hoặc array factory khi cần một mảng public thực sự đúng kiểu.

</details>

- [Quay lại đầu trang](#back-to-top)
