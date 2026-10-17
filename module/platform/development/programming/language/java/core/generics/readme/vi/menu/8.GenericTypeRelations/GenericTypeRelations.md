<a id="back-to-top"></a>

# Quan hệ kiểu

## Menu
- [Kiểu con của đối số không tự tạo quan hệ kiểu con cho generic](#generic-type-relations-001)
- [Vì sao List&lt;Integer&gt; không phải List&lt;Number&gt;](#generic-type-relations-002)
- [List&lt;Integer&gt;, Collection&lt;Integer&gt; và quan hệ theo khai báo kế thừa](#generic-type-relations-003)
- [Tính bất biến của generic và tính hiệp biến của mảng](#generic-type-relations-004)
- [Lỗi kiểu mà tính bất biến giúp ngăn chặn](#generic-type-relations-005)
- [Phân biệt List&lt;Object&gt;, List&lt;?&gt; và raw List](#generic-type-relations-006)
- [Quan hệ kiểu giữa các wildcard có giới hạn](#generic-type-relations-007)
- [Tách quan hệ kiểu con khỏi phép ép kiểu và chuyển đổi không kiểm tra](#generic-type-relations-008)

## <a id="generic-type-relations-001">Kiểu con của đối số không tự tạo quan hệ kiểu con cho generic</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu con của đối số không tự tạo quan hệ kiểu con cho generic

Trong Java, quan hệ kế thừa giữa hai **đối số kiểu** không tự động trở thành quan hệ kế thừa giữa hai **kiểu generic** được tạo từ chúng. Đây là tính **bất biến (invariance)** của generic. Nắm vững quy tắc này giúp tránh lỗi gán kiểu, thiết kế API đọc/ghi dữ liệu đúng và chọn wildcard phù hợp.

## 1. Phân biệt quan hệ kiểu của phần tử và của container

Vì `Integer` là kiểu con của `Number`, ta có thể gán một `Integer` cho biến `Number`:

```java
Integer integer = 42;
Number number = integer; // Hợp lệ: Integer là kiểu con của Number.
```

Nhưng quan hệ đó **không** suy ra:

```java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = integers; // LỖI BIÊN DỊCH.
```

Viết theo ký hiệu quan hệ kiểu con:

- `Integer <: Number` là đúng.
- `List<Integer> <: List<Number>` là **sai**.
- `ArrayList<Integer> <: List<Integer>` là **đúng**, vì `ArrayList<E>` triển khai `List<E>` với cùng đối số kiểu `Integer`.
- `List<Integer> <: List<? extends Number>` là **đúng**; vế phải là một kiểu có wildcard, không phải `List<Number>`.

Quy tắc áp dụng tương tự cho `Set<String>` và `Set<Object>`, `Map<String, Integer>` và `Map<String, Number>`, hoặc `Box<Dog>` và `Box<Animal>`: mỗi cách tham số hóa kiểu phải được xét riêng, dù một hoặc nhiều đối số có quan hệ kế thừa. Generic Java không mặc định hiệp biến hay phản biến theo đối số kiểu.

**Chú ý:** `List<Number>` hoàn toàn có thể chứa các đối tượng `Integer`, `Double`, `Long`... vì từng phần tử ấy là `Number`. Điều này khác với việc gán **cả một** `List<Integer>` vào biến `List<Number>`.

## 2. Vì sao Java cấm phép gán tưởng như hợp lý?

Giả sử trình biên dịch cho phép `List<Integer>` được xem là `List<Number>`:

```java
List<Integer> integers = new ArrayList<>();
// Giả sử phép gán sau được cho phép (thực tế là LỖI BIÊN DỊCH):
List<Number> numbers = integers;

numbers.add(3.14);           // Double cũng là Number, nên nhìn từ numbers là hợp lệ.
Integer value = integers.get(0); // Nhưng danh sách Integer đã chứa Double!
```

Hai biến cùng trỏ tới một danh sách. Nếu cho phép phép gán, hợp đồng “mọi phần tử của `List<Integer>` là `Integer`” có thể bị phá vỡ thông qua biến `List<Number>`. Java chặn lỗi ngay tại **thời điểm biên dịch**, thay vì cho phép kiểu phần tử của danh sách bị thay đổi sai.

Đây là lý do bản chất, không đơn thuần là hạn chế cú pháp. Bất biến bảo vệ an toàn kiểu khi cấu trúc dữ liệu có cả khả năng **đọc** lẫn **ghi**.

## 3. Ví dụ Java 17 chạy được: kiểu phần tử khác với kiểu danh sách

Lưu thành `InvarianceDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class InvarianceDemo {
    static void printNumbers(List<? extends Number> values) {
        for (Number number : values) {
            System.out.println(number);
        }
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>(List.of(1, 2));

        // List<Number> invalid = integers; // Không biên dịch.
        List<? extends Number> readable = integers; // Hợp lệ.
        System.out.println(readable.get(0));

        List<Number> numbers = new ArrayList<>();
        numbers.add(3);   // Integer được xem như Number.
        numbers.add(2.5); // Double được xem như Number.
        System.out.println(numbers);

        printNumbers(integers); // Nhận List<Integer> qua wildcard.
    }
}
```

Chạy bằng Java 17 trở lên:

```bash
javac InvarianceDemo.java
java InvarianceDemo
```

Kết quả:

```text
1
[3, 2.5]
1
2
```

Trong ví dụ này, `List<Number>` cho phép thêm nhiều lớp con của `Number` vào danh sách do chính nó quản lý. Biến `readable` chỉ mang cam kết “danh sách có một kiểu phần tử cụ thể nào đó là kiểu con của `Number`”; trình biên dịch chưa biết kiểu cụ thể đó là `Integer` hay `Double`. Vì vậy, `readable.get(0)` đọc ra dưới kiểu `Number`, nhưng `readable.add(3)` sẽ không biên dịch.

## 4. Wildcard biểu diễn quan hệ tương thích cần thiết

Java dùng **wildcard** để cho phép API làm việc với các generic có đối số kiểu khác nhau mà không phá vỡ bất biến của `List<T>`.

| Khai báo | Chấp nhận các ví dụ | Đọc ra | Có thể thêm phần tử nào? |
| --- | --- | --- | --- |
| `List<Number>` | `List<Number>` | `Number` | `Number` và các kiểu con của nó |
| `List<? extends Number>` | `List<Integer>`, `List<Double>`, `List<Number>` | `Number` | Không thêm được giá trị `Number` cụ thể; chỉ `null` được phép về mặt kiểu |
| `List<? super Integer>` | `List<Integer>`, `List<Number>`, `List<Object>` | `Object` | `Integer` và kiểu con của `Integer` (nếu có) |
| `List<?>` | `List<String>`, `List<Integer>`, v.v. | `Object` | Chỉ `null` được phép về mặt kiểu |

Bảng mô tả quyền **theo hệ kiểu tĩnh**. Danh sách thực tế còn có thể không hỗ trợ sửa đổi, ví dụ danh sách tạo bằng `List.of(...)` sẽ ném `UnsupportedOperationException` khi gọi thao tác thay đổi ngay cả khi biểu thức hợp lệ về kiểu.

### 4.1. `? extends Number`: phù hợp khi **đọc** các giá trị số

`List<? extends Number>` có nghĩa là “danh sách của một kiểu chưa biết, nhưng kiểu đó là `Number` hoặc kiểu con của `Number`”. Vì thế:

```java
List<Integer> integers = new ArrayList<>(List.of(1, 2));
List<? extends Number> source = integers;

Number n = source.get(0); // Hợp lệ.
Object o = source.get(0); // Hợp lệ.

// source.add(3);   // Không biên dịch.
// source.add(3.5); // Không biên dịch.
```

Với `source.add(3)`, compiler không thể khẳng định kiểu phần tử thực sự của `source` là `Integer`; nó cũng có thể là `List<Double>`. Việc đọc dưới kiểu `Number` thì an toàn trong mọi trường hợp. `extends` ở wildcard bao gồm chính kiểu cận trên: `List<Number>` cũng gán được cho `List<? extends Number>`.

### 4.2. `? super Integer`: phù hợp khi **ghi** `Integer`

`List<? super Integer>` có nghĩa là “danh sách của một kiểu chưa biết, nhưng kiểu đó là `Integer` hoặc kiểu cha của `Integer`”. Ta có thể thêm `Integer`, vì mọi danh sách phù hợp đều nhận giá trị này:

```java
List<Number> numbers = new ArrayList<>();
List<? super Integer> destination = numbers;

destination.add(10);    // Hợp lệ.
destination.add(20);    // Hợp lệ.
Object item = destination.get(0); // Chỉ chắc chắn đọc ra Object.

// Number n = destination.get(0); // Không biên dịch.
// destination.add(2.5);         // Không biên dịch.
```

Nếu kiểu thật là `List<Object>`, thao tác đọc cũng chỉ bảo đảm `Object`. Nếu kiểu thật là `List<Integer>`, `Double` không thể được thêm vào. Đây là hai giới hạn cần giữ để wildcard an toàn.

### 4.3. `?`: chấp nhận mọi đối số kiểu, nhưng ít khả năng thao tác

`List<?>` thích hợp khi chỉ cần duyệt, đếm, kiểm tra rỗng hoặc xem phần tử dưới kiểu `Object`:

```java
static void printSize(List<?> items) {
    System.out.println(items.size());
}
```

`List<?>` **không** tương đương `List<Object>`. Một `List<String>` có thể truyền cho `List<?>`, nhưng không thể gán cho `List<Object>`. Và `List<?>` không cho phép thêm tùy ý `Object`.

## 5. Ví dụ hoàn chỉnh: PECS, đọc từ source và ghi vào destination

Quy tắc thực hành **PECS** là *Producer Extends, Consumer Super*:

- **Producer:** tham số chủ yếu cung cấp giá trị cho hàm → `? extends T`.
- **Consumer:** tham số chủ yếu nhận giá trị do hàm thêm vào → `? super T`.

Ví dụ lưu thành `WildcardDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardDemo {
    static double sum(List<? extends Number> source) {
        double total = 0;
        for (Number number : source) {
            total += number.doubleValue();
        }
        return total;
    }

    static void addDefaults(List<? super Integer> destination) {
        destination.add(10);
        destination.add(20);
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(1, 2, 3);
        List<Number> numbers = new ArrayList<>();
        List<Object> objects = new ArrayList<>();

        System.out.println(sum(integers));
        addDefaults(numbers);
        addDefaults(objects);

        System.out.println(numbers);
        System.out.println(objects);
        Object first = objects.get(0);
        System.out.println(first);
    }
}
```

```bash
javac WildcardDemo.java
java WildcardDemo
```

Kết quả:

```text
6.0
[10, 20]
[10, 20]
10
```

`sum` có thể xử lý `List<Integer>`, `List<Double>` hoặc `List<Number>` vì nó chỉ lấy giá trị ra và dùng API của `Number`. `addDefaults` có thể ghi hai số nguyên vào `List<Integer>`, `List<Number>` hoặc `List<Object>` vì tất cả những kiểu đích này đều nhận `Integer`.

PECS là hướng dẫn chọn kiểu tham số theo chiều luân chuyển dữ liệu, **không** phải quy tắc rằng mọi danh sách luôn phải khai báo wildcard. Khi hàm vừa đọc vừa thêm cùng kiểu `T`, một `List<T>` với tham số kiểu cụ thể có thể thích hợp hơn.

## 6. Generic method giúp nối hai cấu trúc có đối số khác nhau

Khi cả đầu vào và đầu ra phải dùng chung một quan hệ kiểu, dùng **tham số kiểu của phương thức** kết hợp wildcard. Đây là cách diễn đạt trực tiếp yêu cầu “đọc các giá trị thuộc `T` hoặc kiểu con và chuyển vào nơi nhận `T` hoặc kiểu cha”.

Lưu thành `TransferDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class TransferDemo {
    static <T> void copyAll(
            List<? extends T> source,
            List<? super T> destination) {
        for (T value : source) {
            destination.add(value);
        }
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(1, 2, 3);
        List<Number> numbers = new ArrayList<>();
        copyAll(integers, numbers);
        System.out.println(numbers);

        List<String> words = List.of("Java", "Generics");
        List<Object> objects = new ArrayList<>();
        copyAll(words, objects);
        System.out.println(objects);
    }
}
```

```bash
javac TransferDemo.java
java TransferDemo
```

Kết quả:

```text
[1, 2, 3]
[Java, Generics]
```

Hai lời gọi hoạt động mà không cần ép kiểu. Trình biên dịch suy luận một `T` thỏa quan hệ cận trên/cận dưới tại mỗi lời gọi. Với `copyAll(integers, numbers)`, `Integer` phù hợp làm `T`: `List<Integer>` cung cấp phần tử kiểu `Integer` và `List<Number>` nhận được chúng.

**Giới hạn của ví dụ:** nguồn và đích nên là những danh sách thích hợp cho việc sao chép; không dùng vòng lặp này để khẳng định mọi cách đọc-ghi đồng thời trên cùng danh sách, hoặc trên những danh sách bị thay đổi đồng thời từ thread khác, đều an toàn. Các vấn đề đó độc lập với quy tắc bất biến của generic.

## 7. Kế thừa class/interface generic vẫn hợp lệ nếu đối số kiểu khớp

Đừng nhầm quy tắc bất biến với việc Java cấm kế thừa giữa các kiểu generic. Quan hệ kế thừa khai báo trong class/interface vẫn có hiệu lực:

```java
ArrayList<Integer> arrayList = new ArrayList<>();
List<Integer> list = arrayList; // Đúng: ArrayList<Integer> implements List<Integer>.
```

Tương tự, xét khai báo:

```java
class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}

class IntegerBox extends Box<Integer> {
    IntegerBox(Integer value) {
        super(value);
    }
}
```

Khi đó:

```java
Box<Integer> first = new IntegerBox(42); // Hợp lệ.
Box<? extends Number> second = new IntegerBox(42); // Hợp lệ.

// Box<Number> wrong = new IntegerBox(42); // Không biên dịch.
```

`IntegerBox` kế thừa trực tiếp `Box<Integer>`, không kế thừa `Box<Number>`. Để biên dịch riêng đoạn này, đặt hai class trong cùng nguồn với tên file tùy ý và một class có `main` nếu muốn chạy; các đoạn ở mục này nhằm minh họa quan hệ kiểu, không phải chương trình có đầu ra.

Với interface tự định nghĩa, quy tắc cũng vậy: nếu `interface Repository<T>` thì `Repository<Dog>` không tự là kiểu con của `Repository<Animal>` chỉ vì `Dog extends Animal`.

## 8. Cận kiểu của tham số generic không làm kiểu generic hiệp biến

Khai báo:

```java
class NumberBox<T extends Number> {
    private final T value;

    NumberBox(T value) {
        this.value = value;
    }

    T getValue() {
        return value;
    }
}
```

Cận `T extends Number` chỉ giới hạn **đối số kiểu được phép** khi tạo `NumberBox<T>`, ví dụ `Integer`, `Double` hoặc `Number`. Nó không tạo quan hệ kiểu con giữa `NumberBox<Integer>` và `NumberBox<Number>`:

```java
NumberBox<Integer> intBox = new NumberBox<>(10);

// NumberBox<Number> wrong = intBox; // Không biên dịch.
NumberBox<? extends Number> readOnlyView = intBox; // Hợp lệ.
Number value = readOnlyView.getValue();             // Hợp lệ.
```

Hai ý “`T` buộc phải thuộc họ `Number`” và “các bản tham số hóa `NumberBox<T>` có quan hệ kế thừa với nhau” là hai vấn đề khác nhau.

## 9. Bất biến còn áp dụng khi generic lồng nhau

Một bẫy thường gặp là cho rằng có wildcard ở **tầng trong** thì danh sách ở **tầng ngoài** cũng tự tương thích:

```java
List<List<Integer>> matrix = new ArrayList<>();

// List<List<? extends Number>> wrong = matrix; // Không biên dịch.
List<? extends List<? extends Number>> view = matrix; // Hợp lệ.
```

`List<Integer>` phù hợp với `List<? extends Number>`, nhưng điều đó không khiến `List<List<Integer>>` phù hợp với `List<List<? extends Number>>`. Tầng `List<...>` bên ngoài vẫn bất biến. Wildcard ở tầng ngoài cho phép đọc các danh sách con phù hợp mà không cho phép thêm tùy ý danh sách con mới.

Khi thiết kế API lồng nhiều generic, cần xét quan hệ kiểu **ở từng tầng**. Chỉ thêm wildcard ở những tầng mà API thực sự cần tính linh hoạt; tránh biến chữ ký thành khó đọc không cần thiết.

## 10. Phân biệt với array: array hiệp biến nhưng có kiểm tra khi chạy

Array Java cho phép `Integer[]` gán vào `Number[]`, khác với `List<Integer>` và `List<Number>`. Sự linh hoạt của array đi cùng một phép kiểm tra kiểu lúc chạy.

Lưu thành `ArrayContrast.java`:

```java
public class ArrayContrast {
    public static void main(String[] args) {
        Number[] values = new Integer[1]; // Hợp lệ với array.
        values[0] = 1;                     // Integer: hợp lệ lúc chạy.

        try {
            values[0] = 3.14; // Double không thể nằm trong Integer[] thật.
        } catch (ArrayStoreException ex) {
            System.out.println("ArrayStoreException");
        }
    }
}
```

```bash
javac ArrayContrast.java
java ArrayContrast
```

Kết quả:

```text
ArrayStoreException
```

Trong khi đó, generic của Java kiểm tra phần lớn quan hệ giữa các đối số kiểu lúc biên dịch; thông tin đối số kiểu thường bị **xóa kiểu (type erasure)** trong bytecode. Type erasure **không có nghĩa** `List<Integer>` có thể gán an toàn cho `List<Number>`. Chính quy tắc bất biến tại lúc biên dịch bảo vệ hợp đồng phần tử. Ép kiểu không kiểm tra hoặc raw type có thể phá an toàn kiểu và dẫn đến `ClassCastException` ở một chỗ đọc về sau; đó không phải cách giải quyết hợp lệ cho phép gán bị từ chối.

## 11. Lỗi phổ biến và cách sửa

### Lỗi 1: yêu cầu `List<Number>` dù hàm chỉ đọc

```java
static double average(List<Number> values) { // Quá chặt nếu chỉ cần đọc.
    return values.stream().mapToDouble(Number::doubleValue).average().orElse(0);
}
```

Nếu muốn nhận cả `List<Integer>` và `List<Double>`, đổi tham số thành:

```java
static double average(List<? extends Number> values) {
    return values.stream().mapToDouble(Number::doubleValue).average().orElse(0);
}
```

Việc đổi chữ ký không biến danh sách đầu vào thành `List<Number>`; nó chỉ mô tả chính xác nhu cầu **đọc** của hàm.

### Lỗi 2: thêm phần tử qua `? extends Number`

```java
List<? extends Number> values = new ArrayList<Integer>();
// values.add(100); // Không biên dịch dù 100 là Integer.
```

Nếu hàm cần thêm giá trị `Integer` vào các danh sách có khả năng nhận nó, cân nhắc `List<? super Integer>`. Nếu muốn sở hữu một danh sách chứa nhiều loại `Number`, tạo `List<Number>` mới:

```java
List<Number> values = new ArrayList<>();
values.add(100);
values.add(3.14);
```

### Lỗi 3: nghĩ `List<?>` là `List<Object>`

```java
List<String> strings = List.of("a", "b");
List<?> anything = strings; // Hợp lệ.

// List<Object> objects = strings; // Không biên dịch.
```

`List<Object>` có thể nhận thêm `Object`; `List<?>` thì không. Hai kiểu có lời hứa khác nhau về dữ liệu.

### Lỗi 4: ép kiểu qua raw type để lách trình biên dịch

```java
List<Integer> integers = new ArrayList<>();
// Tránh cách sau: raw type và unchecked conversion đánh mất bảo đảm an toàn.
// List<Number> unsafe = (List) integers;
```

Không dùng cách này trong code ứng dụng. Thiết kế lại chữ ký với `? extends`, `? super`, phương thức generic, hoặc tạo danh sách đích có đối số kiểu thực sự phù hợp.

### Lỗi 5: chỉ xét quan hệ kế thừa của một đối số trong generic nhiều tham số

`Map<String, Integer>` không trở thành `Map<String, Number>` chỉ vì `Integer extends Number`. Nếu API chỉ đọc giá trị, có thể cân nhắc một chữ ký theo nhu cầu cụ thể, chẳng hạn `Map<String, ? extends Number>`. Điều tương tự xảy ra ở các generic lồng nhau như mục 9.

### Lỗi 6: nhầm bất biến với khả năng thay đổi của collection

Wildcard mô tả khả năng đọc/ghi **theo kiểu**. Nó không bảo đảm collection là mutable, thread-safe, có thứ tự hay không chứa `null`. Ví dụ `List.of(1, 2)` là danh sách không sửa đổi; cố `add` sẽ thất bại lúc chạy dù khai báo là `List<Integer>`.

## 12. Cách chọn chữ ký API

Hãy tự hỏi dữ liệu được dùng theo hướng nào:

1. **Muốn một danh sách có thể chứa nhiều loại `Number` và tự thêm chúng:** dùng `List<Number>`.
2. **Muốn nhận danh sách bất kỳ có phần tử thuộc họ `Number` để đọc:** dùng `List<? extends Number>`.
3. **Muốn truyền các `Integer` vào một danh sách nhận được chúng:** dùng `List<? super Integer>`.
4. **Chỉ cần xem kích thước, duyệt dưới kiểu `Object` hoặc kiểm tra rỗng:** dùng `List<?>`.
5. **Muốn liên kết an toàn kiểu đầu vào và đầu ra:** dùng phương thức generic `<T>` kết hợp `? extends T` và `? super T` theo chiều dữ liệu.
6. **Muốn vừa đọc và ghi chính xác cùng một kiểu phần tử do caller quyết định:** cân nhắc `List<T>`; wildcard không phải lúc nào cũng cần.

Một số câu hỏi tự kiểm tra:

- `List<Integer>` có gán trực tiếp cho `List<Number>` không? **Không.**
- `List<Integer>` có truyền cho `List<? extends Number>` không? **Có.**
- Một `List<? extends Number>` có cho phép thêm `Integer` không? **Không**, vì kiểu phần tử cụ thể chưa biết.
- `List<Number>` có truyền cho `List<? super Integer>` không? **Có.**
- Một `List<? super Integer>` có cho phép đọc trực tiếp ra `Integer` không? **Không**, chỉ chắc chắn là `Object`.
- `ArrayList<Integer>` có là kiểu con của `List<Integer>` không? **Có**, do quan hệ triển khai interface cùng đối số kiểu.

**Ghi nhớ:** quan hệ `S extends T` không làm `G<S>` trở thành kiểu con của `G<T>`. Khi muốn nhận nhiều cách tham số hóa của một generic, hãy thể hiện đúng yêu cầu đọc/ghi bằng wildcard hoặc tham số kiểu của phương thức.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-relations-002">Vì sao List&lt;Integer&gt; không phải List&lt;Number&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `List<Integer>` không phải `List<Number>`? Chọn cách xử lý đúng

**`Integer extends Number` không kéo theo `List<Integer> extends List<Number>`.** Java Generics bất biến (*invariant*) đối với đối số kiểu cụ thể: một `List<Integer>` không thể tự được sử dụng như `List<Number>`, mặc dù từng Integer vẫn có thể dùng như một Number.

Bài này trả lời câu hỏi **“khi đang có `List<Integer>` mà API muốn xử lý Number, tôi thực sự cần làm gì?”** bằng cách phân biệt ba kết quả: *chỉ đọc dữ liệu*, *ghi vào nơi nhận dữ liệu*, và *sở hữu một List<Number> thật sự*. Mỗi nhu cầu đòi một chữ ký khác nhau; thay vì viết nhiều phép ép kiểu, ta chuyển đổi **thiết kế API hoặc cấu trúc lưu trữ**.

## 1. Phép gán nào hợp lệ, phép gán nào không?

Xét:

~~~java
Integer n = 7;
Number number = n; // Hợp lệ: Integer là subtype của Number.

List<Integer> ids = List.of(10, 20);

// List<Number> direct = ids; // LỖI BIÊN DỊCH.
List<? extends Number> view = ids; // Hợp lệ.
~~~

Khi thấy lỗi `incompatible types: List<Integer> cannot be converted to List<Number>`, cần tách hai câu hỏi:

1. **Phần tử có dùng được như Number không?** Có. Integer là subtype của Number.
2. **Toàn bộ danh sách có thể hứa mọi quyền của List<Number> không?** Không. List<Number> cho phép `add(3.5)`, trong khi List<Integer> không thể chấp nhận một Double.

Nếu một biến List<Number> được phép tham chiếu List<Integer>, code khác sẽ có thể đưa Double vào danh sách mà caller vốn kỳ vọng chỉ có Integer. Java từ chối **phép gán nguy hiểm ngay lúc biên dịch**, bảo toàn mối quan hệ giữa kiểu phần tử được ghi vào và kiểu mà `get` hứa trả ra.

Điều cấm này không phụ thuộc danh sách hiện rỗng hay đã có dữ liệu. Một `List<Integer>` có thể đang immutable, nhưng *invariance* và *immutability* vẫn là hai khái niệm độc lập.

## 2. Trước khi sửa lỗi, hãy xác định mục tiêu thật sự

| Nhu cầu thực tế | Lựa chọn đúng | Có tạo list mới? |
| --- | --- | --- |
| Chỉ tính toán/đọc như Number | `Collection<? extends Number>` hoặc `List<? extends Number>` | Không |
| Chỉ duyệt phần tử không cần chỉ số | `Iterable<? extends Number>` | Không |
| Đưa các Integer vào một đích do người gọi sở hữu | `Collection<? super Integer>` | Không bắt buộc |
| Có list mới chứa được cả Integer và Double | `List<Number> copy = new ArrayList<>(ids)` | Có |
| Chỉ muốn đếm phần tử của mọi loại List | `List<?>` | Không |
| Cần đọc–ghi nhất quán một kiểu chính xác do caller định | `List<T>` trong generic method | Không bắt buộc |
| Một API *thực sự* cần list có type argument Number | Tạo/duy trì một `List<Number>` | Tùy API |

Hai lựa chọn dễ gây nhầm nhất là **góc nhìn để đọc `List<? extends Number>`** và **danh sách thật `List<Number>`**. Góc nhìn không chuyển đổi hay sao chép object. Bản sao tạo ra collection mới, có thể thêm những loại Number khác mà không làm thay đổi danh sách Integer gốc.

## 3. Trường hợp A — Method chỉ cần đọc Number

Một method tính tổng không cần biết danh sách chứa Integer, Double hay Long:

~~~java
static double average(Collection<? extends Number> source) {
    double total = 0;
    int count = 0;

    for (Number value : source) {
        total += value.doubleValue();
        count++;
    }

    return count == 0 ? 0 : total / count;
}
~~~

`? extends Number` mang nghĩa **một type argument cụ thể chưa biết**, nhưng chắc chắn nằm trong họ Number. Khi duyệt, mỗi giá trị có thể dùng như Number.

Nhiều loại nguồn hợp lệ:

~~~java
average(List.of(10, 20));   // Có thể nhận List<Integer>.
average(List.of(1.5, 2.5)); // Có thể nhận List<Double>.
~~~

Chữ ký `Collection<? extends Number>` tổng quát hơn `List<? extends Number>` nếu method không cần `get(index)`, vì nó dùng được với nhiều collection implementations hơn. Khi chỉ cần iteration, `Iterable<? extends Number>` còn rộng hơn; chọn abstraction thấp nhất đủ hỗ trợ thao tác của thuật toán.

### Vì sao không dùng `List<Number>` cho hàm chỉ đọc?

~~~java
static double sumTooStrict(List<Number> values) {
    double total = 0;
    for (Number n : values) {
        total += n.doubleValue();
    }
    return total;
}

// List<Integer> ids = List.of(10, 20);
// sumTooStrict(ids); // LỖI: method đòi List<Number>.
~~~

Đây là một **hợp đồng quá chặt** so với nhiệm vụ thực tế: method không gọi `add`, nhưng yêu cầu một list mà về mặt kiểu **có thể** nhận mọi Number. Sửa chữ ký của method nếu bạn sở hữu API đó, thay vì sao chép các giá trị chỉ để vượt qua sự bất biến.

### Cận trên vẫn không cho thêm số bất kỳ

~~~java
List<Integer> ids = new ArrayList<>(List.of(10, 20));
List<? extends Number> readable = ids;

Number first = readable.get(0); // Hợp lệ.
// readable.add(30);             // LỖI BIÊN DỊCH.
// readable.add(2.5);            // LỖI BIÊN DỊCH.
~~~

Compiler không biết type argument được dấu `?` đại diện chính xác là Integer, Double hay Number. Vì vậy một Number hay Integer tùy ý **không được bảo đảm là loại có thể ghi vào** danh sách đó. `add(null)` có thể hợp lệ về kiểu nhưng còn phụ thuộc khả năng sửa đổi và chính sách null của implementation.

## 4. Trường hợp B — Method cần đưa Integer vào đích rộng hơn

Đây là chiều ngược lại:

~~~java
static void appendIds(
        Collection<? super Integer> destination,
        Collection<Integer> ids) {

    for (Integer id : ids) {
        destination.add(id);
    }
}
~~~

`? super Integer` cho phép đích có type argument Integer, Number hoặc Object. Một Integer có thể được đưa vào bất kỳ danh sách nào trong ba loại đó mà vẫn giữ đúng contract.

~~~java
List<Integer> ids = List.of(10, 20);
List<Object> audit = new ArrayList<>();

appendIds(audit, ids);
System.out.println(audit); // [10, 20]
~~~

Nhưng nếu đọc qua biến `Collection<? super Integer>`/`List<? super Integer>`, element chỉ được bảo đảm dưới kiểu Object. Một đích `List<Object>` hoàn toàn có thể chứa String hoặc những đối tượng khác do các thành phần khác thêm vào.

**PECS** (*Producer Extends, Consumer Super*) là tên gợi nhớ cho thiết kế một chiều này: producer cấp dữ liệu thì dùng upper wildcard, consumer nhận dữ liệu thì dùng lower wildcard. Nó không phải mệnh lệnh đặt wildcard ở mọi tham số.

Khi cần copy **bất kỳ loại T phù hợp** từ nguồn sang đích, thư viện hay viết generic method:

~~~java
static <T> void appendAll(
        Collection<? extends T> source,
        Collection<? super T> destination) {

    for (T item : source) {
        destination.add(item);
    }
}
~~~

Tại một lời gọi, compiler suy luận T sao cho nguồn cấp được giá trị T và đích nhận được T. Với List<Integer> nguồn và List<Number> đích, thao tác đó an toàn và không cần biến List<Integer> thành List<Number>.

## 5. Trường hợp C — Cần tạo `List<Number>` thật sự

Khi bạn thật sự **cần sở hữu** một danh sách cho phép lưu các kiểu Number khác nhau, hãy tạo collection mới:

~~~java
List<Integer> ids = List.of(10, 20);

List<Number> report = new ArrayList<>(ids);
report.add(2.5); // Hợp lệ: list đích có type argument Number.

System.out.println(report); // [10, 20, 2.5]
System.out.println(ids);    // [10, 20]
~~~

Constructor `ArrayList(Collection<? extends E>)` nhận các phần tử từ nguồn có kiểu tương thích E. Trong ví dụ này, context của vế trái xác định E là Number, nên constructor có thể copy Integer sang List<Number>.

**Đây không phải type cast.** Nó tạo một list object mới; hai list không có chung cấu trúc add/remove/set. Nếu element là mutable object, phép copy của ArrayList vẫn là **shallow copy** ở mức tham chiếu element. Với Integer (immutable), ta không gặp vấn đề nội dung phần tử bị đổi qua alias, nhưng không nên khái quát rằng copy này sao chép sâu mọi object.

Nếu cần một collection ban đầu trống chứa các loại số khác nhau:

~~~java
List<Number> numbers = new ArrayList<>();
numbers.add(1);
numbers.add(2.5);
~~~

Cách này diễn đạt ý định **sở hữu list hỗn hợp Number** rõ hơn dùng raw cast để “nâng cấp” List<Integer> đã có.

### Khi nào cần sửa chữ ký, khi nào cần sao chép?

- **Bạn kiểm soát method chỉ đọc**: thay `List<Number>` bằng `List<? extends Number>` thường tối ưu và không copy.
- **Bạn kiểm soát method chỉ nhận Integer**: nhận `List<? super Integer>` khi phù hợp với các thao tác.
- **Bạn không kiểm soát một API yêu cầu chính xác `List<Number>`**: tạo `List<Number>` riêng để thích nghi với API.
- **Bạn cần giữ và sửa hai collection độc lập**: copy là quyết định về **ownership**, không đơn thuần là chữa lỗi compiler.
- **Bạn thật sự cần trao đổi phần tử hai chiều**: dùng một named type parameter T phù hợp; two independent wildcards không hứa các element types trùng nhau.

## 6. Một chi tiết suy luận kiểu dễ gây bất ngờ: target typing

Hai biểu thức sau cần được phân biệt:

~~~java
List<Integer> ids = List.of(1, 2);

// List<Number> wrong = ids; // Lỗi: biến ids đã có type List<Integer>.

List<Number> directFactory = List.of(1, 2); // Hợp lệ.
~~~

Vì sao dòng cuối hợp lệ? Lời gọi generic factory `List.of` có thể được compiler suy luận theo **target type** của vế trái, chọn E=Number; những Integer được cung cấp đều phù hợp với Number. Điều đó **không khiến `List<Integer>` đã tồn tại bỗng trở thành `List<Number>`**.

Lưu ý `List.of` trả danh sách **không cho sửa**. Viết `List<Number> directFactory = List.of(1, 2)` không có nghĩa gọi `directFactory.add(2.5)` sẽ chạy được. Đây là vấn đề runtime mutability, khác hẳn tính bất biến *invariance* của generics.

Nếu cần `List<Number>` sửa được từ nguồn Integer, `new ArrayList<>(ids)` là lựa chọn rõ ràng hơn.

## 7. Chương trình Java 17 hoàn chỉnh: ba hướng xử lý

Lưu thành **NumberListMigrationDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NumberListMigrationDemo {
    static double average(Collection<? extends Number> source) {
        double total = 0;
        int count = 0;

        for (Number value : source) {
            total += value.doubleValue();
            count++;
        }
        return count == 0 ? 0 : total / count;
    }

    static void appendIds(
            Collection<? super Integer> destination,
            Collection<Integer> ids) {
        for (Integer id : ids) {
            destination.add(id);
        }
    }

    static List<Number> copyAsNumbers(
            Collection<? extends Number> source) {
        List<Number> answer = new ArrayList<>();
        answer.addAll(source);
        return answer;
    }

    public static void main(String[] args) {
        List<Integer> ids = List.of(10, 20);

        System.out.println("average=" + average(ids));

        List<Number> report = copyAsNumbers(ids);
        report.add(2.5);
        System.out.println("report=" + report);
        System.out.println("original=" + ids);

        List<Object> audit = new ArrayList<>();
        appendIds(audit, ids);
        System.out.println("audit=" + audit);

        List<Number> targetTyped = List.of(1, 2);
        System.out.println("targetTyped=" + targetTyped);
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -Xlint:all NumberListMigrationDemo.java
java NumberListMigrationDemo
~~~

**Stdout đã kiểm chứng:**

~~~text
average=15.0
report=[10, 20, 2.5]
original=[10, 20]
audit=[10, 20]
targetTyped=[1, 2]
~~~

Giải thích:
- `average(ids)` nhận List<Integer> mà không copy nhờ wildcard chỉ đọc.
- `copyAsNumbers(ids)` xây List<Number> mới; thêm Double chỉ sửa list mới.
- `appendIds(audit, ids)` ghi Integer vào List<Object> nhờ cận dưới.
- `targetTyped` chứng minh factory `List.of` có thể dùng target typing để tạo một expression kiểu List<Number>; nó vẫn là list không hỗ trợ add/set.

### Một caveat toán học

`average` chuyển Number sang `double`, do đó có thể mất độ chính xác với những số nguyên rất lớn hoặc những yêu cầu tài chính. **Sự phù hợp về type bounds không bảo đảm độ chính xác số học**. Nếu xử lý tiền tệ, thường nên thiết kế miền dữ liệu và phép cộng bằng BigDecimal/kiểu thích hợp.

Hàm trả 0 cho collection rỗng theo **chính sách minh họa**. Một API production có thể ném exception hoặc trả OptionalDouble thay vì quy ước đó; điều này độc lập với invariance.

## 8. Kiểm thử lỗi biên dịch trước khi sửa API

Các trường hợp sau được viết để **đọc lỗi**, không chèn cùng chương trình chạy:

~~~java
List<Integer> ids = List.of(10, 20);

// List<Number> a = ids;          // Sai: invariant List<E>.
// List<Object> b = ids;          // Sai: invariant List<E>.

List<? extends Number> readable = ids;
// readable.add(30);             // Sai: captured subtype chưa biết.

List<? super Integer> writable = new ArrayList<Number>();
writable.add(30);                 // Đúng.
// Integer x = writable.get(0);   // Sai: đọc chỉ được đảm bảo Object.

List<Number> independent = new ArrayList<>(ids);
independent.add(3.5);             // Đúng: list Number riêng.
~~~

**Không sửa bằng raw/unchecked cast** như `(List<Number>) (List<?>) ids`. Phép cast không đổi kiểu phần tử mà collection thật sự cam kết, và có thể cho phép thêm Double vào list mà code khác đọc như Integer. Một giải pháp đúng phải giữ quan hệ type ở compile time hoặc tạo collection mới với type argument thích hợp.

## 9. Bảng lựa chọn cuối bài

| Câu hỏi bạn đang gặp | Hướng giải quyết |
| --- | --- |
| `sum(List<Number>)` không nhận `List<Integer>` | Sửa API thành `List<? extends Number>` nếu chỉ đọc. |
| Muốn lưu Integer vào `List<Number>` hay `List<Object>` | Nhận `List<? super Integer>` nếu method chỉ ghi Integer. |
| Cần trộn Integer, Double, Long trong một danh sách | Khởi tạo/copy thành `List<Number>` thật. |
| Muốn đếm `List<String>` và `List<Integer>` | Dùng `List<?>`; không cần `List<Object>`. |
| Muốn chuyển collection giữa producer và consumer linh hoạt | Method `<T>` + `? extends T`/`? super T`. |
| Chữ ký đúng nhưng gọi `add` ném UnsupportedOperationException | Xem lại mutability; thay `List.of` bằng một mutable list nếu cần. |
| Thấy `List<Number> x = List.of(1,2)` biên dịch được | Target typing của factory, không phải covariance. |
| Thấy `List<Number> x = existingListOfInteger` không biên dịch | Invariance: hai parameterized types đã được xác định khác nhau. |

## 10. Tự kiểm tra

**Câu 1.** Vì sao `Number value = Integer.valueOf(10)` hợp lệ nhưng `List<Number> values = ids` (ids là List<Integer>) không hợp lệ?

**Đáp án:** Phép gán Number nhận một object Integer là subtype bình thường; trong khi List<Number> còn có quyền add mọi Number mà List<Integer> không thể đáp ứng.

**Câu 2.** Nếu chỉ lấy trung bình từ List<Double> và List<Integer>, nên dùng `List<Number>` hay `List<? extends Number>`?

**Đáp án:** `List<? extends Number>` (hoặc Collection<? extends Number>) vì phương thức chỉ đọc các giá trị tương thích Number.

**Câu 3.** Làm sao tạo list sửa được, nhận cả Integer và Double từ một List<Integer> có sẵn?

**Đáp án:** `List<Number> copy = new ArrayList<>(ints);` rồi thêm Double vào copy. Đây là list object khác, không phải phép ép kiểu.

**Câu 4.** Vì sao `List<Number> x = List.of(1,2)` có thể compile?

**Đáp án:** Generic factory method nhận target typing từ vế trái và suy luận Number; đây không phải phép gán một biến List<Integer> đã định kiểu cho List<Number>.

**Câu 5.** Một generic source có cận extends Number có cho phép tự thêm Integer vào không?

**Đáp án:** Không. Actual subtype của wildcard chưa biết, có thể là Double; chỉ null là ngoại lệ ở mức hệ kiểu, còn mutability là vấn đề riêng.

**Câu 6.** Tại sao muốn cho cả List<Number> lẫn List<Object> nhận Integer, ta dùng `? super Integer`?

**Đáp án:** Cả Number và Object đều là supertypes của Integer, nên cả hai loại collection đều chấp nhận việc ghi Integer.

**Câu 7.** Có nên dùng `List<Number> forced = (List<Number>) (List<?>) ids` để loại lỗi compiler không?

**Đáp án:** Không. Điều đó bỏ qua generic type checking mà không đổi cấu trúc dữ liệu; có thể gây heap pollution và ClassCastException trong code đọc theo kiểu Integer.

**Ghi nhớ:** vấn đề không phải “tìm cú pháp cast để List<Integer> thành List<Number>”, mà là **xác định quyền nào method thực sự cần**. Đọc dữ liệu theo Number thì dùng upper bounded wildcard, ghi Integer thì dùng lower bounded wildcard, còn muốn sở hữu list đa kiểu số thì **tạo List<Number> riêng**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-relations-003">List&lt;Integer&gt;, Collection&lt;Integer&gt; và quan hệ theo khai báo kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# `List<Integer>`, `Collection<Integer>` và quan hệ theo khai báo kế thừa

## Mục tiêu

Sau bài này, bạn cần phân biệt được hai ý độc lập: **quan hệ kế thừa giữa các kiểu khai báo** và **quan hệ giữa các đối số kiểu trong generics**. Từ đó, bạn giải thích được vì sao một `List<Integer>` gán được cho `Collection<Integer>`, nhưng không gán được cho `Collection<Number>`, đồng thời biết khi nào nên dùng wildcard.

## 1. Quan hệ kiểu bắt đầu từ khai báo API

Trong `java.util`, các giao diện khai báo quan hệ như sau (rút gọn chỉ để thấy phần kế thừa):

```java
interface Iterable<T> {
    // ...
}

interface Collection<E> extends Iterable<E> {
    // ...
}

interface List<E> extends Collection<E> {
    // ...
}
```

Đây là **sơ đồ khai báo minh họa**, không phải mã nguồn đầy đủ để thay thế các interface của JDK. Với cùng một kiểu tham số `T`, chuỗi quan hệ là:

```text
List<T>  <:  Collection<T>  <:  Iterable<T>
  ^
  |
ArrayList<T> (implements List<T>)
```

Ký hiệu `A <: B` nghĩa là `A` là subtype (kiểu con) của `B`. `ArrayList<E>` triển khai `List<E>`, còn `List<E>` kế thừa `Collection<E>`. Khi thay đồng nhất `E = Integer`, ta nhận được:

- `ArrayList<Integer>` là subtype của `List<Integer>`.
- `List<Integer>` là subtype của `Collection<Integer>`.
- `Collection<Integer>` là subtype của `Iterable<Integer>`.

Vì vậy, **phép gán hướng lên (upcast) hợp lệ**:

```java
List<Integer> list = new ArrayList<>();
Collection<Integer> collection = list;
Iterable<Integer> iterable = collection;
```

Java kiểm tra quan hệ này tại **thời điểm biên dịch** thông qua khai báo `extends`/`implements`. Cùng đối tượng `ArrayList` được tham chiếu qua các kiểu tĩnh khác nhau; không có bản sao danh sách nào được tạo ra khi gán.

Đổi kiểu tham chiếu làm thay đổi **những phương thức có thể gọi qua biến**: `collection.add(1)` và `collection.size()` có trong `Collection`, nhưng `collection.get(0)` không biên dịch vì `get(int)` thuộc `List`. Đối tượng thực tế vẫn là danh sách. Ngược chiều, không thể tự động gán một `Collection<Integer>` cho `List<Integer>`, vì một collection bất kỳ cũng có thể là `Set<Integer>`.

## 2. Ví dụ Java 17+ chạy độc lập

Lưu mã dưới tên **`GenericInheritanceDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GenericInheritanceDemo {
    private static void appendForty(Collection<? super Integer> target) {
        target.add(40);
    }

    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>(List.of(10, 20));

        Collection<Integer> collection = list; // upcast hợp lệ
        Iterable<Integer> iterable = collection;

        collection.add(30); // sửa chính đối tượng mà list đang tham chiếu
        System.out.println(list);
        System.out.println(iterable.iterator().next());

        Collection<? extends Number> readableNumbers = list;
        Number first = readableNumbers.iterator().next();
        System.out.println(first);

        Collection<Number> destination = new ArrayList<>();
        appendForty(destination);
        System.out.println(destination);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac GenericInheritanceDemo.java
java GenericInheritanceDemo
```

Kết quả chính xác:

```text
[10, 20, 30]
10
10
[40]
```

Giải thích: `collection` và `list` cùng trỏ tới một `ArrayList<Integer>` nên thêm `30` qua `collection` cũng hiện ra khi in `list`. Biến `iterable` chỉ bộc lộ API của `Iterable`, nhưng vẫn duyệt chính đối tượng đó. Wildcard `? extends Number` cho phép đọc phần tử dưới kiểu `Number`; wildcard `? super Integer` cho phép hàm thêm `Integer` vào collection có kiểu phần tử là `Integer` hoặc một siêu kiểu của nó.

**Lưu ý về mutability:** ví dụ dùng `new ArrayList<>(List.of(...))` để tạo danh sách thay đổi được. Nếu tham chiếu trỏ trực tiếp đến `List.of(10, 20)`, lời gọi `add` sẽ ném `UnsupportedOperationException`, dù phép gán `List<Integer> → Collection<Integer>` vẫn hoàn toàn hợp lệ. Quan hệ subtype không bảo đảm khả năng sửa dữ liệu.

## 3. Generics bất biến: thay đối số kiểu không tạo quan hệ kế thừa mới

`Integer` kế thừa `Number`, nhưng **`List<Integer>` không phải subtype của `List<Number>`** và **`Collection<Integer>` không phải subtype của `Collection<Number>`**. Đây là tính **invariance** (bất biến) của kiểu generic Java.

Những dòng sau cố ý minh họa **lỗi biên dịch**; không thêm chúng vào chương trình chạy ở trên:

```java
List<Integer> integers = new ArrayList<>();

// Không biên dịch:
Collection<Number> wrong1 = integers;
List<Number> wrong2 = integers;
Collection<Object> wrong3 = integers;
```

Tại sao phải cấm? Giả sử `Collection<Number> wrong1 = integers` được chấp nhận, lệnh `wrong1.add(3.14)` sẽ hợp lệ theo kiểu tĩnh `Collection<Number>`, nhưng lại đưa `Double` vào một `List<Integer>`. Java ngăn nguy cơ đó ngay tại thời điểm biên dịch.

Hãy tách rõ hai phép suy luận:

1. **Thay kiểu khai báo dọc theo chuỗi kế thừa, giữ nguyên đối số kiểu:** `List<Integer> → Collection<Integer>`: hợp lệ.
2. **Thay đối số kiểu `Integer` thành `Number` trong kiểu generic cụ thể:** `Collection<Integer> → Collection<Number>`: không hợp lệ.

Việc `Integer <: Number` **không** tự động kéo theo `G<Integer> <: G<Number>` với `G` là `List`, `Collection` hay lớp generic bất biến khác.

## 4. Wildcard biểu đạt quan hệ kiểu rộng hơn

Khi phương thức chỉ cần đọc các phần tử như `Number` hoặc chỉ cần nhận các giá trị `Integer`, bạn có thể khai báo wildcard thay vì đòi một `Collection<Number>` cố định.

### `? extends Number`: nguồn đọc

```java
static void printFirst(Collection<? extends Number> values) {
    Number first = values.iterator().next();
    System.out.println(first);
}
```

Phương thức này nhận được `List<Integer>`, `List<Double>`, `Collection<Number>` và các collection có kiểu phần tử là subtype của `Number`. Có thể đọc phần tử ra kiểu `Number`. Tuy nhiên, không thể gọi `values.add(42)` vì compiler không biết kiểu phần tử thực là `Integer`, `Double` hay subtype nào khác. Đừng hiểu `Collection<? extends Number>` là `Collection<Number>`: chúng mang các ràng buộc khác nhau.

### `? super Integer`: đích ghi

```java
static void addAnswer(Collection<? super Integer> values) {
    values.add(42);
}
```

Phương thức nhận được `Collection<Integer>`, `Collection<Number>` và `Collection<Object>`, bởi tất cả đều có khả năng chứa một `Integer`. Ngược lại, khi lấy phần tử từ `Collection<? super Integer>`, compiler chỉ có thể đảm bảo kiểu `Object`, do kiểu phần tử thực có thể là `Integer`, `Number` hoặc `Object`.

Gợi nhớ quy tắc **PECS** (*Producer Extends, Consumer Super*): nơi cung cấp dữ liệu để đọc thường dùng `? extends`; nơi nhận dữ liệu để ghi thường dùng `? super`. Nếu cần đọc và ghi chính xác `Integer`, dùng `Collection<Integer>`.

Cần phân biệt thêm: `Collection<? extends Number>` có thể **tham chiếu** một `List<Integer>`, nhưng điều đó không làm biến `Collection<Number>` nhận được `List<Integer>`. Wildcard là kiểu đích khác, mang giới hạn thao tác khác.

## 5. Bảng kiểm tra phép gán

| Nguồn (biểu thức) | Kiểu biến đích | Kết quả | Lý do |
| --- | --- | --- | --- |
| `ArrayList<Integer>` | `List<Integer>` | Hợp lệ | `ArrayList<E> implements List<E>` |
| `List<Integer>` | `Collection<Integer>` | Hợp lệ | `List<E> extends Collection<E>` |
| `Collection<Integer>` | `Iterable<Integer>` | Hợp lệ | `Collection<E> extends Iterable<E>` |
| `Collection<Integer>` | `List<Integer>` | Không tự động | Collection có thể là Set |
| `List<Integer>` | `Collection<Number>` | Không hợp lệ | Đối số generic bất biến |
| `List<Integer>` | `List<Number>` | Không hợp lệ | Đối số generic bất biến |
| `List<Integer>` | `Collection<? extends Number>` | Hợp lệ | Phần tử đọc được dưới kiểu Number |
| `List<Integer>` | `Collection<? super Integer>` | Hợp lệ | Có thể thêm Integer |
| `List<Number>` | `Collection<? super Integer>` | Hợp lệ | Number là supertype của Integer |

Các hàng mô tả **kiểm tra kiểu khi gán**, không bảo đảm rằng mọi đối tượng nhận được đều hỗ trợ thao tác sửa đổi. Việc `add` chạy thành công còn phụ thuộc implementation cụ thể.

## 6. Chọn kiểu tham số phương thức theo nhu cầu

Một hàm chỉ cần duyệt, đếm hoặc kiểm tra thành viên không nên đòi `List<Integer>` khi `Collection<Integer>` đã đủ; nhờ vậy hàm có thể chấp nhận cả `ArrayList` và `HashSet` chứa `Integer`:

```java
static boolean containsForty(Collection<Integer> values) {
    return values.contains(40);
}
```

Nếu hàm chỉ đọc các số không phụ thuộc chính xác kiểu số, mở rộng chữ ký sang `Collection<? extends Number>`. Nếu cần dùng chỉ mục hoặc giữ thứ tự truy cập bằng `get(index)`, `List<Integer>` mới phản ánh đúng yêu cầu API. Nếu hàm chỉ cần phép lặp, `Iterable<Integer>` còn tổng quát hơn.

Ví dụ khác biệt giữa hướng lên và hướng xuống (các dòng lỗi được chú thích):

```java
List<Integer> list = new ArrayList<>(List.of(1, 2));
Collection<Integer> view = list; // hợp lệ
// List<Integer> notGuaranteed = view; // không biên dịch

// Chỉ ép kiểu hướng xuống khi biết đối tượng thực sự là List:
if (view instanceof List<?>) {
    List<Integer> restored = (List<Integer>) view;
    System.out.println(restored.get(0)); // 1
}
```

Ở ví dụ cuối, chính `list` khởi tạo `view` nên đối tượng thực sự là một `List`. Đừng ép kiểu một `Collection` bất kỳ sang `List`; với `HashSet`, ép kiểu như vậy sẽ thất bại tại runtime. Không nên dùng cast để né tính bất biến của các đối số generic.

## 7. Những nhầm lẫn thường gặp

- **“`Integer extends Number`, vậy `Collection<Integer> extends Collection<Number>`.”** Sai: quan hệ kế thừa của đối số kiểu không được tự động truyền vào generic bất biến.
- **“`List<Integer>` và `Collection<Integer>` là hai collection khác nhau sau phép gán.”** Sai: upcast chỉ thay kiểu tĩnh của tham chiếu; hai biến có thể cùng trỏ một đối tượng.
- **“`Collection<Integer>` dùng được mọi phương thức của `List<Integer>`.”** Sai: gọi phương thức phụ thuộc kiểu tham chiếu tại compile time; `Collection` không có `get(index)`.
- **“Gán được `List<Integer>` cho `Collection<Integer>` nên chắc chắn thêm được phần tử.”** Sai: các implementation không sửa được vẫn có thể ném `UnsupportedOperationException`.
- **“Cứ dùng `Collection<Number>` để nhận mọi list số.”** Sai: dùng `Collection<? extends Number>` nếu đọc nhiều kiểu số, hoặc `Collection<? super Integer>` nếu cần thêm `Integer`.

## Kết luận cần ghi nhớ

Quan hệ **`List<E> extends Collection<E>`** cho phép `List<Integer> → Collection<Integer>` vì **giữ nguyên `E = Integer`** trên tuyến kế thừa. Tính **bất biến** lại ngăn `List<Integer> → Collection<Number>` dù `Integer extends Number`. Khi API cần nhận các kiểu phần tử khác nhau, wildcard `? extends` hoặc `? super` diễn đạt chính xác khả năng đọc/ghi và giúp trình biên dịch bảo toàn an toàn kiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-relations-004">Tính bất biến của generic và tính hiệp biến của mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Tính bất biến của generic và tính hiệp biến của mảng trong Java

## 1. Mục tiêu và bối cảnh

Trong Java, `Integer` kế thừa `Number`, nhưng hai khai báo sau **không** có cùng quy tắc gán:

- `Integer[]` có thể gán cho biến kiểu `Number[]`: **mảng có tính hiệp biến (covariance)** đối với kiểu phần tử tham chiếu.
- `List<Integer>` không thể gán cho biến kiểu `List<Number>`: **generic mặc định bất biến (invariance)** theo tham số kiểu.

Phân biệt hai quy tắc này giúp tránh lỗi biên dịch khi thiết kế API với `List<T>`, hiểu `ArrayStoreException`, và sử dụng `? extends` / `? super` đúng mục đích. Bài này xét **quan hệ kiểu**; bản thân từ khóa `extends` trong wildcard không biến `List<Integer>` thành `List<Number>`.

## 2. Nền tảng: quan hệ kiểu con

Giả sử `Integer` là kiểu con của `Number`, ký hiệu `Integer <: Number`. Khi đó:

```java
Number n = Integer.valueOf(42); // Hợp lệ: Integer là một Number
Object o = n;                   // Hợp lệ: Number là một Object
```

Tuy nhiên, quan hệ giữa **kiểu phần tử** không tự động quyết định quan hệ giữa các kiểu chứa phần tử:

| Biểu thức kiểu | Có phải quan hệ kiểu con hợp lệ? | Lý do |
| --- | --- | --- |
| `Integer <: Number` | Có | Kế thừa lớp |
| `Integer[] <: Number[]` | Có | Hiệp biến của mảng tham chiếu |
| `List<Integer> <: List<Number>` | Không | Bất biến của tham số generic |
| `List<Integer> <: List<? extends Number>` | Có | Wildcard giới hạn trên chấp nhận danh sách số |
| `List<Number> <: List<? super Integer>` | Có | Wildcard giới hạn dưới chấp nhận kiểu chứa Integer |

Dấu `<:` trong bảng là ký hiệu giải thích quan hệ kiểu con, không phải cú pháp Java.

## 3. Tính bất biến của generic (invariance)

Với cùng một kiểu generic, chẳng hạn `List<T>`, Java **không** coi `List<Integer>` là kiểu con của `List<Number>`, và cũng không coi chiều ngược lại là hợp lệ, dù `Integer` là kiểu con của `Number`.

```java
import java.util.List;

List<Integer> integers = List.of(1, 2, 3);
List<Number> numbers = integers; // LỖI BIÊN DỊCH: incompatible types
```

Đây là lỗi có chủ đích của hệ thống kiểu. Giả sử phép gán trên được phép:

```java
// GIẢ ĐỊNH (không biên dịch được trong Java):
List<Integer> integers = new ArrayList<>();
List<Number> numbers = integers;
numbers.add(3.14);                 // Double cũng là Number
Integer value = integers.get(0);  // Không thể an toàn nếu phần tử là Double
```

`List<Number>` cho phép thêm `Double`, `Long` hoặc `Integer`. Ngược lại, một `List<Integer>` phải giữ đúng phần tử kiểu `Integer`. Nếu cùng một đối tượng được xem qua cả hai kiểu trên, khả năng ghi qua biến `List<Number>` sẽ phá hợp đồng của biến `List<Integer>`. **Bất biến ngăn nguy cơ này ngay khi biên dịch.**

Cần phân biệt hai tình huống:

```java
List<Integer> a = new ArrayList<Integer>(); // Đúng
List<Integer> b = new ArrayList<>();        // Đúng: suy luận kiểu
List<Number>  c = new ArrayList<Integer>(); // Sai: tham số kiểu khác nhau
```

`ArrayList<Integer>` là một `List<Integer>` vì `ArrayList<T>` triển khai `List<T>` với **cùng** `T`; điều đó không làm cho `List<Integer>` trở thành `List<Number>`.

Muốn có một `List<Number>` độc lập từ các phần tử `Integer`, hãy tạo danh sách đích và sao chép giá trị:

```java
List<Integer> integers = List.of(1, 2);
List<Number> numbers = new ArrayList<>(integers); // Đúng: tạo list khác
numbers.add(3.14);                                // Đúng: list đích chứa Number
```

Điều này là **sao chép các tham chiếu phần tử vào một cấu trúc chứa mới**, không phải ép kiểu giữa `List<Integer>` và `List<Number>`.

## 4. Tính hiệp biến của mảng (array covariance)

Với các kiểu phần tử **tham chiếu**, nếu `Integer <: Number` thì `Integer[] <: Number[]`. Do đó:

```java
Integer[] integers = {1, 2};
Number[] numbers = integers; // Hợp lệ ở thời điểm biên dịch
numbers[0] = 3;              // Hợp lệ: Integer được lưu vào Integer[]
numbers[1] = 3.14;           // Biên dịch được, nhưng ném ArrayStoreException
```

Biến `numbers` có kiểu khai báo `Number[]`, song đối tượng mảng thật được tạo là `Integer[]`. JVM vẫn biết **kiểu phần tử thực tế** của mảng. Khi phép gán phần tử xảy ra, JVM kiểm tra giá trị có tương thích với kiểu phần tử thực tế không. `Double` không thể nằm trong `Integer[]`, nên câu lệnh cuối ném `ArrayStoreException` ngay tại lúc ghi; các phần tử trước đó không bị biến đổi thành `Double`.

Vì vậy, hiệp biến mảng **không bảo đảm mọi thao tác ghi đều an toàn tĩnh**. Java chấp nhận phép gán kiểu mảng nhưng có kiểm tra lúc chạy để giữ an toàn kiểu. Mảng tồn tại từ trước khi generics được bổ sung vào Java; quy tắc hiệp biến được giữ nhằm tương thích với thiết kế ngôn ngữ và API đã có.

Lưu ý phân biệt kiểu mảng:

- `String[]` có thể được nhìn qua `Object[]` vì `String` là kiểu tham chiếu con của `Object`.
- `int[]` **không** là `Number[]` hoặc `Object[]` vì `int` là kiểu nguyên thủy. Tuy vậy, chính đối tượng `int[]` vẫn có thể gán cho biến kiểu `Object`.
- Gán sai phần tử vào mảng tham chiếu có thể gây `ArrayStoreException`; ép kiểu sai một tham chiếu mảng có thể gây `ClassCastException`. Đây là hai thao tác và hai lỗi khác nhau.

## 5. Tại sao hành vi giữa generic và mảng khác nhau?

Có hai đặc điểm cốt lõi:

1. **Mảng mang thông tin kiểu phần tử lúc chạy (reified)**. Một đối tượng `new Integer[2]` biết các vị trí của nó yêu cầu `Integer`. JVM kiểm tra khi ghi.
2. **Phần lớn thông tin tham số generic bị xóa khi biên dịch (type erasure)**. Tại runtime, đối tượng `ArrayList<String>` và `ArrayList<Integer>` nhìn chung đều dùng cùng lớp `ArrayList`; JVM không có kiểm tra kiểu phần tử cụ thể cho mỗi lời gọi thêm phần tử tương tự kiểm tra cửa hàng của mảng.

Với generics, trình biên dịch chủ động chặn phép gán có thể làm hỏng kiểu từ sớm. Với mảng tham chiếu, trình biên dịch cho phép quan hệ hiệp biến và JVM có thể chặn một phép ghi không an toàn khi chạy.

Không nên suy ra rằng **mọi** kiểm tra generic đều biến mất. Trình biên dịch vẫn chèn các phép chuyển kiểu thích hợp tại vị trí sử dụng và JVM vẫn kiểm tra các cast được chèn đó. Type erasure cũng không đồng nghĩa mọi cách dùng raw type hoặc unchecked cast đều an toàn.

## 6. Dùng wildcard để mô tả API với nhiều kiểu phần tử

Wildcard làm biểu thức kiểu linh hoạt hơn mà không phá quy tắc bất biến của `List<T>`.

### 6.1. `? extends Number`: nhận nguồn để đọc

```java
List<Integer> integers = List.of(1, 2);
List<Double> doubles = List.of(1.5, 2.5);

List<? extends Number> first = integers; // Đúng
List<? extends Number> second = doubles; // Đúng

Number x = first.get(0); // Đúng: đọc dưới dạng Number
first.add(10);           // LỖI BIÊN DỊCH
```

`List<? extends Number>` nghĩa là danh sách có kiểu phần tử **một kiểu chưa biết** nằm trong họ `Number`, ví dụ `Integer` hoặc `Double`. Đọc phần tử như `Number` là an toàn; thêm `Integer` vào một danh sách có thể thực chất là `List<Double>` thì không an toàn. Vì vậy `add` không nhận giá trị phần tử thông thường; `add(null)` về mặt kiểu là ngoại lệ hợp lệ nhưng thường không có giá trị nghiệp vụ.

Ví dụ phương thức đọc tổng:

```java
static double sum(List<? extends Number> values) {
    double result = 0;
    for (Number value : values) {
        result += value.doubleValue();
    }
    return result;
}
```

Phương thức nhận `List<Integer>`, `List<Double>` hoặc `List<Number>` mà không yêu cầu người gọi sao chép dữ liệu. Kết quả là `double`, nên vẫn cần hiểu giới hạn độ chính xác của `double` nếu xử lý tiền tệ hoặc số nguyên rất lớn.

### 6.2. `? super Integer`: nhận đích để ghi

```java
List<Number> numbers = new ArrayList<>();
List<? super Integer> destination = numbers;

destination.add(10);     // Đúng: thêm Integer
destination.add(20);     // Đúng
Object item = destination.get(0); // Chỉ đảm bảo là Object
```

Kiểu phần tử thực tế của `List<? super Integer>` có thể là `Integer`, `Number` hoặc `Object`. Mọi danh sách như thế nhận được một giá trị `Integer`, nhưng kết quả đọc ra không thể tự động coi là `Integer` hoặc `Number` bởi có thể là `List<Object>` với các đối tượng khác.

### 6.3. Quy tắc PECS và phương thức sao chép

**PECS = Producer Extends, Consumer Super**: nguồn sinh phần tử để đọc dùng `extends`; đích nhận phần tử để ghi dùng `super`.

```java
static <T> void appendAll(
        List<? super T> destination,
        List<? extends T> source) {
    for (T value : source) {
        destination.add(value);
    }
}
```

Ví dụ `appendAll(List<Number>, List<Integer>)` hợp lệ. Đây là phương thức **nối thêm** vào cuối đích. Không nhầm nó với `Collections.copy(destination, source)`: phương thức của thư viện **ghi đè theo chỉ số** và yêu cầu danh sách đích có sẵn đủ số phần tử.

Nếu một API **vừa đọc vừa ghi cùng kiểu cụ thể** (chẳng hạn thay thế, hoán đổi giá trị), dùng `List<T>` hoặc helper có type parameter rõ ràng sẽ thường thích hợp hơn wildcard.

## 7. Ví dụ Java 17+ đầy đủ, chạy được

Tạo tệp `QuanHeKieuDemo.java` với nội dung:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QuanHeKieuDemo {
    static <T> void appendAll(
            List<? super T> destination,
            List<? extends T> source) {
        for (T value : source) {
            destination.add(value);
        }
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(10, 20);
        List<? extends Number> readable = integers;
        System.out.println("Extends read: " + readable.get(0));

        List<Number> numbers = new ArrayList<>();
        List<? super Integer> writable = numbers;
        writable.add(30);
        System.out.println("Super read: " + writable.get(0));

        appendAll(numbers, integers);
        System.out.println("Destination: " + numbers);

        Integer[] actual = {1, 2};
        Number[] view = actual; // Mảng hiệp biến
        System.out.println("Array read: " + view[0]);

        try {
            view[0] = 3.5; // Double không thể lưu vào Integer[]
        } catch (ArrayStoreException exception) {
            System.out.println(
                    "Array store blocked: " + exception.getClass().getSimpleName());
        }
        System.out.println("Array after: " + Arrays.toString(actual));

        List<?>[] genericViews = new List<?>[1];
        genericViews[0] = List.of("x");
        System.out.println("Unbounded wildcard: " + genericViews[0].get(0));
    }
}
```

Biên dịch và chạy trong thư mục có tệp trên:

```bash
javac -encoding UTF-8 QuanHeKieuDemo.java
java QuanHeKieuDemo
```

Đầu ra chính xác:

```text
Extends read: 10
Super read: 30
Destination: [30, 10, 20]
Array read: 1
Array store blocked: ArrayStoreException
Array after: [1, 2]
Unbounded wildcard: x
```

Phân tích thứ tự: `readable` đọc `Integer` như `Number`; `writable` thêm `Integer` vào một `List<Number>`; `appendAll` bổ sung 10 và 20. Mảng thật là `Integer[]`, nên ghi `3.5` bị chặn, còn `[1, 2]` được giữ nguyên. Phần cuối minh họa một mảng chứa các danh sách có wildcard không giới hạn.

## 8. Vì sao không tạo mảng của kiểu generic cụ thể?

Khai báo sau **không biên dịch**:

```java
List<String>[] words = new List<String>[2]; // generic array creation: không hợp lệ
```

Giả sử cách tạo trên hợp lệ. Do erasure, kiểu phần tử runtime không thể phân biệt `List<String>` và `List<Integer>`. Nếu tham chiếu mảng được đưa qua `Object[]`, kiểm tra khi gán phần tử có thể nhìn thấy cùng một kiểu thô `List` và không phát hiện sự sai lệch tham số kiểu `String`. Điều đó phá bảo đảm kiểu của `List<String>[]`. Vì vậy Java cấm tạo trực tiếp các mảng có kiểu phần tử tham số hóa không reifiable.

Ngược lại, trường hợp sau **hợp lệ**:

```java
List<?>[] lists = new List<?>[2];
lists[0] = List.of("a");
lists[1] = List.of(1, 2);
Object first = lists[0].get(0);
```

`List<?>` là kiểu có wildcard không giới hạn, thuộc các trường hợp **reifiable** được phép làm kiểu phần tử của biểu thức tạo mảng. Tuy nhiên, khi đọc danh sách qua `List<?>`, kiểu phần tử chỉ được bảo đảm là `Object`; không thể khẳng định toàn bộ danh sách chứa `String` hay `Integer`.

Trong ứng dụng, khi cần tập hợp danh sách, thường ưu tiên `List<List<String>>` hoặc cấu trúc generic tương ứng thay cho mảng generic. Tránh né bằng raw type, unchecked cast hoặc `@SuppressWarnings("unchecked")` không tự làm kiểu dữ liệu trở nên an toàn.

## 9. Lỗi thường gặp và cách sửa

| Sai lầm | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Gán `List<Integer>` cho `List<Number>` | Lỗi biên dịch | Nếu chỉ đọc, dùng `List<? extends Number>`; nếu cần danh sách độc lập, tạo `new ArrayList<Number>(integers)` |
| Tưởng `List<? extends Number>` cho phép `add(1)` | Lỗi biên dịch | Dùng `List<? super Integer>` khi mục tiêu là nhận `Integer` |
| Đọc `List<? super Integer>` thẳng thành `Integer` | Lỗi biên dịch | Đọc thành `Object`, hoặc dùng cấu trúc kiểu chính xác hơn |
| Tưởng `Number[] a = new Integer[2]` cho phép ghi `Double` | `ArrayStoreException` lúc chạy | Tạo mảng thật `new Number[2]` nếu cần chứa nhiều loại `Number` |
| Tạo `new List<String>[2]` | Lỗi biên dịch | Dùng `List<List<String>>`, hoặc `List<?>[]` khi thật sự cần mảng kiểu này |
| Dùng raw `List` để lách bất biến | Cảnh báo unchecked, nguy cơ `ClassCastException` sau này | Giữ type parameter và wildcard rõ ràng |

Đặc biệt, `Arrays.asList(1, 2)` tạo một `List<Integer>` có kích thước cố định; đổi biến tham chiếu sang wildcard không làm nó trở thành `List<Number>` hoặc trở thành danh sách có thể thêm phần tử. **Quan hệ kiểu** và **khả năng thay đổi cấu trúc** của từng triển khai danh sách là hai vấn đề độc lập.

## 10. Tự kiểm tra

Với mỗi đoạn, dự đoán **biên dịch được hay không**, và nếu biên dịch được thì có lỗi runtime hay không:

```java
// A
List<? extends Number> a = List.of(1, 2);
Number n = a.get(0);

// B
List<Number> b = List.of(1, 2);

// C
Number[] c = new Integer[1];
c[0] = 1.5;

// D
List<? super Integer> d = new ArrayList<Object>();
d.add(7);
Object o = d.get(0);

// E
Object[] e = new int[2];
```

**Đáp án giải thích:**

- **A:** Hợp lệ, đọc phần tử kiểu `Number`. `List.of(1, 2)` suy ra `List<Integer>`, chấp nhận được ở vị trí `? extends Number`.
- **B:** Hợp lệ: lời gọi `List.of` được suy luận kiểu `Number` theo **target typing** của vế trái; khác với việc lấy một biến đã có kiểu `List<Integer>` rồi gán cho `List<Number>`. Danh sách trả về là bất biến theo nghĩa **không cho thay đổi dữ liệu (unmodifiable)**, một khái niệm riêng với **invariance** của kiểu generic.
- **C:** Biên dịch được; phép ghi `Double` vào đối tượng `Integer[]` ném `ArrayStoreException`.
- **D:** Hợp lệ; `List<Object>` là một đích có thể nhận `Integer`, và khi đọc qua `? super Integer` chỉ suy ra `Object`.
- **E:** Không biên dịch: `int[]` không phải `Object[]`. Có thể viết `Object e = new int[2];` nếu cần tham chiếu tới cả đối tượng mảng.

### Ghi nhớ

`List<S>` và `List<T>` vẫn bất biến dù `S <: T`. Mảng tham chiếu `S[]` hiệp biến với `T[]`, nhưng việc ghi sai phần tử có thể bị JVM từ chối lúc chạy. Khi thiết kế API generic, dùng `? extends T` để đọc từ nguồn và `? super T` để ghi vào đích; dùng tham số kiểu cố định `T` khi phải duy trì quan hệ kiểu chặt chẽ trong cả hai chiều.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-relations-005">Lỗi kiểu mà tính bất biến giúp ngăn chặn</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử lỗi kiểu mà invariance của Java Generics ngăn chặn

Một trong những lợi ích quan trọng của **invariance** là ngăn code viết sai kiểu ngay từ giai đoạn biên dịch. `List<Integer>` không tự trở thành `List<Number>` dù Integer là subtype của Number, vì `List<Number>` có quyền nhận Double. Nếu Java cho phép alias đó, dữ liệu bên dưới một `List<Integer>` có thể bị ghi sai loại phần tử.

Bài này không chỉ lặp lại lời giải thích invariance. Trọng tâm là **thiết kế phép thử có khả năng phân biệt**:

1. Sai kiểu nào compiler phải **chặn trước khi chạy**?
2. Đường wildcard/copy nào **giữ đúng type contract**?
3. Nếu cố tình vượt compiler bằng **raw alias**, lỗi có xảy ra lúc ghi hay chỉ lúc **đọc dưới kiểu Integer**?
4. Vì sao array lại phát **ArrayStoreException tại thao tác ghi**, khác nhiều generic collection cases?
5. Có thể chuyển lỗi mơ hồ ở cuối pipeline thành **lỗi xác thực ở ranh giới dữ liệu** hay không?

Đây là một bài kiểm thử/regression chứ không phải hướng dẫn dùng raw type trong code production. Toàn bộ thao tác không an toàn dưới đây chỉ dùng trong thử nghiệm có chủ đích.

## 1. Điều compiler ngăn chặn ngay lập tức

Với `Integer <: Number`, phép gán một Integer cho Number hợp lệ. Nhưng hai parameterized types cụ thể của List lại **bất biến**:

~~~java
List<Integer> ids = new ArrayList<>(List.of(1, 2));

// LỖI BIÊN DỊCH nếu bỏ comment:
List<Number> numbers = ids;
~~~

Nếu code trên được chấp nhận, có thể tiếp tục:

~~~java
// Giả định trái luật Java, không phải chương trình runnable:
// numbers.add(3.5);
// Integer later = ids.get(2);
// Double được ghi qua List<Number> nhưng lại bị đọc như Integer.
~~~

**Giá trị của hệ thống kiểu** là loại bỏ hẳn đường ghi chéo nguy hiểm đó. Không phải lỗi được “bắt sau này”: javac từ chối ngay phép gán đầu tiên.

Các lớp triển khai **vẫn có quan hệ subtype nếu giữ đúng đối số kiểu**:

~~~java
ArrayList<Integer> backing = new ArrayList<>();
List<Integer> list = backing;
Collection<Integer> collection = list;
~~~

Đây là kế thừa giữa những **generic declarations** (`ArrayList<E> implements List<E>`, `List<E> extends Collection<E>`), không phải một phép biến đổi Integer thành Number trong vị trí type argument. Để kiểm tra bước đó riêng, hãy nhớ quy tắc invariance vẫn áp dụng cho List<Integer> và Collection<Number>.

## 2. Tạo hai nhóm test tách biệt: compile-time và runtime

Một test chỉ bọc mọi thứ bằng `try/catch(ClassCastException)` là quá rộng. Nó không chứng minh compiler đã ngăn đường gán sai, cũng không chứng minh exception xuất hiện **ở thao tác nào**.

Chia kiểm thử làm hai bộ:

| Bộ test | Đầu vào | Kết quả phải chứng minh |
| --- | --- | --- |
| **Negative compilation** | Source cố gán List<Integer> cho List<Number> | `javac` thất bại với incompatible types. |
| **Type-safe alternative** | Nhìn List<Integer> qua wildcard đọc, copy vào List<Number> mới | Biên dịch/chạy thành công, list gốc không bị ô nhiễm. |
| **Raw alias misuse** | Raw List nhận Double trong list gốc vốn typed Integer | Có unchecked warning; write có thể qua. |
| **Typed read** | Đọc Double bị ghi qua raw alias như Integer | ClassCastException tại điểm dùng phần tử dưới kiểu Integer. |
| **Reified array** | Number[] alias tới Integer[] rồi ghi Double | ArrayStoreException **ngay tại write**. |
| **Boundary validation** | List<?> chứa Integer lẫn Double | Validator từ chối sai phần tử trước khi công bố List<Integer>. |

Tách hai giai đoạn build/run là rất quan trọng: **code bị compiler chặn không thể chạy**. Cần đưa mã cố ý sai biên dịch vào **tệp thử nghiệm riêng**, không lẫn vào một chương trình dự kiến chạy thành công.

## 3. Negative compilation: kiểm tra đúng lỗi bị ngăn chặn

Tệp **CompileShouldFail.java** được thiết kế để **không** biên dịch:

~~~java
import java.util.ArrayList;
import java.util.List;

public class CompileShouldFail {
    public static void main(String[] args) {
        List<Integer> ids = new ArrayList<>(List.of(1, 2));

        // Trình biên dịch phải từ chối dòng dưới:
        List<Number> allNumbers = ids;

        allNumbers.add(3.5);
    }
}
~~~

Chạy:

~~~bash
javac --release 17 CompileShouldFail.java
~~~

Kết quả mong đợi: **exit code khác 0**, chẩn đoán đại ý `List<Integer> cannot be converted to List<Number>` hoặc `incompatible types`. Nội dung đầy đủ, vị trí dòng và định dạng thông báo phụ thuộc phiên bản javac; **không hard-code nguyên văn toàn bộ stderr** làm hợp đồng.

Ý nghĩa bài kiểm tra: phép gán sai bị từ chối trước khi `add(3.5)` có cơ hội chạy. Đây là mục tiêu cần duy trì khi refactor public APIs: đừng thay một phương thức nhận `List<Integer>` bằng raw List hay đưa unchecked cast vào chỉ để làm source “compile được”.

### Cách biến negative test thành regression test

Một dự án có test infrastructure có thể compile đoạn nguồn nhỏ bằng `javax.tools.JavaCompiler` trong test process và assert compile status là failure; hoặc dùng bài test compile-fail của build tool. Điểm cần assert là **compile fail đúng ca phân tích**, không phải một lỗi syntax hoàn toàn khác.

Đối với ví dụ Java thuần trong bài này, chạy javac bằng terminal và quan sát exit code là đủ.

## 4. Positive test: truyền qua wildcard và tạo list mới

Khi cần **chỉ đọc Number** từ một List<Integer>, dùng một tham chiếu `List<? extends Number>`:

~~~java
List<Integer> original = new ArrayList<>(List.of(1, 2));
List<? extends Number> readable = original;

Number first = readable.get(0); // OK.
// readable.add(3.5);           // Compile error.
~~~

Khi cần **một list thực sự chứa được Double**, tạo `List<Number>` khác:

~~~java
List<Number> independent = new ArrayList<>(original);
independent.add(3.5); // Chỉ sửa list mới.

System.out.println(original);    // [1, 2]
System.out.println(independent); // [1, 2, 3.5]
~~~

Điều này là proof of safety cho một **cách thiết kế khác**, không phải “cast thành List<Number>”. Hai collection có cấu trúc riêng; việc copy danh sách không làm thay đổi type argument của source.

Một biến `List<? super Integer>` có thể dùng làm **đích ghi Integer** khi cần đưa giá trị vào List<Number> hoặc List<Object>. Nhưng khi đọc qua biến này, kiểu bảo đảm chỉ là Object; không tự suy diễn rằng mọi phần tử được đọc là Integer.

**Lưu ý runtime:** một `List.of(...)` vẫn không hỗ trợ `add` dù chữ ký cho phép thao tác theo kiểu. Vì thế positive test dùng `new ArrayList<>(...)` khi thực sự có write.

## 5. Raw alias: lỗi không bắt buộc xuất hiện ngay lúc ghi

Trong một file test riêng:

~~~java
List<Integer> vulnerable = new ArrayList<>(List.of(1));

List raw = vulnerable;
raw.add(3.5); // Unchecked call, có thể ghi Double thành công.

Object observed = vulnerable.get(1); // Đọc theo Object: có thể thành công.

// Integer bad = vulnerable.get(1);
// Nếu thực thi dòng này, ClassCastException khi runtime cần Integer.
~~~

`raw` và `vulnerable` là **cùng một collection object**, không phải bản sao. Raw List làm mất kiểm tra E của `add`, vì vậy ArrayList nhận một Double trong vị trí mà code typed đang xem là Integer.

Ở bytecode, generic `List.get` có erased return type Object; khi caller cần Integer, javac có thể tạo `checkcast java/lang/Integer`. ClassCastException thường xuất hiện tại **typed read / typed usage**, không nhất thiết ngay lúc `raw.add`.

Khi test bug này, hai assertion phải **tách rời**:
1. Kiểm tra raw write thật sự đã làm thay đổi dữ liệu mà chưa ném exception.
2. Chỉ bọc typed read trong đoạn kỳ vọng ClassCastException.

Nếu bọc cả chuỗi khởi tạo–write–read trong một khối catch duy nhất, test có thể pass vì exception từ một operation khác với operation mong muốn.

### Cảnh báo javac

Dùng:

~~~bash
javac --release 17 -Xlint:rawtypes,unchecked InvarianceRegressionDemo.java
~~~

Khi code chứa `List raw = vulnerable; raw.add(3.5);`, compiler có thể phát:
- `[rawtypes]` do sử dụng raw List.
- `[unchecked]` tại thao tác add(E) qua raw receiver.

Warnings **không tự là exceptions** và không đồng nghĩa compiler từ chối build, trừ khi project cấu hình quality gate như `-Werror`.

Không dùng `@SuppressWarnings("unchecked")` như một phương thức “sửa lỗi”: suppression chỉ giấu warning, không xác minh tham số kiểu của collection.

## 6. So sánh đối chứng: array bắt lỗi khi ghi

Java arrays có runtime component type (**reified**), nên một Number[] alias có thể trỏ tới mảng thực Integer[], và Java kiểm tra phần tử được ghi có tương thích Integer không:

~~~java
Number[] view = new Integer[1];

try {
    view[0] = 3.5;
} catch (ArrayStoreException ex) {
    System.out.println("arrayWrite=ArrayStoreException");
}
~~~

Lệnh `view[0] = 3.5` biên dịch được vì kiểu tĩnh Number[] chấp nhận Number, nhưng đối tượng mảng thực Integer[] không nhận Double. JVM ném **ArrayStoreException tại write**.

| Trường hợp | Điều gì bị giới hạn/kiểm tra? | Điểm phát hiện lỗi |
| --- | --- | --- |
| `List<Integer> → List<Number>` | Generic invariant assignment | Compile time, không chạy. |
| Raw `add(Double)` vào ArrayList<Integer> | Chỉ có erased Object type ở add; mất generic E check | Có thể không lỗi ở write; dữ liệu bị ô nhiễm. |
| Typed `Integer x = vulnerable.get(1)` | JVM checkcast Integer ở typed usage | ClassCastException nếu phần tử là Double. |
| `Number[] view = new Integer[1]`, ghi Double | Runtime array component Integer | ArrayStoreException ở write. |

Không nên nói “generic type safety hoàn toàn là runtime” hoặc “mảng không an toàn”. Cả hai cơ chế đều có bảo vệ, nhưng **ở những thời điểm và bằng chứng kiểu khác nhau**.

## 7. Validator ở ranh giới dữ liệu: fail sớm có ngữ nghĩa rõ

Trong một API nhận dữ liệu không chắc chắn, không nên trả `List<Integer>` chỉ bằng một unchecked cast từ Object hoặc raw List.

Thay vào đó, nhận `List<?>` và kiểm tra **từng phần tử**:

~~~java
static List<Integer> requireIntegers(List<?> source) {
    List<Integer> checked = new ArrayList<>();

    for (Object item : source) {
        if (!(item instanceof Integer value)) {
            throw new IllegalArgumentException("expected Integer");
        }
        checked.add(value);
    }

    return checked;
}
~~~

Java 17 hỗ trợ pattern matching `instanceof Integer value`. Nếu gặp Double, String hoặc null, hàm ném IllegalArgumentException theo chính sách của ví dụ; dữ liệu sai bị từ chối **ngay tại boundary** chứ không lan sâu tới chỗ code đọc Integer.

Hàm trả một **bản sao** của cấu trúc collection, nên raw alias tới source không thể trực tiếp thêm phần tử vào list kết quả. Tuy nhiên, nếu element là object mutable, đây vẫn là shallow copy; ví dụ hiện dùng Integer bất biến.

Có thể chọn một loại exception/domain validation result khác tùy contract thật. Điều quan trọng là validation thực sự duyệt elements, không giả vờ rằng cast `(List<Integer>) someList` đã kiểm tra tất cả generic arguments.

## 8. Chương trình Java 17 hoàn chỉnh, kiểm thử theo đúng thời điểm

Lưu thành **InvarianceRegressionDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class InvarianceRegressionDemo {
    static List<Integer> requireIntegers(List<?> source) {
        List<Integer> checked = new ArrayList<>();
        for (Object item : source) {
            if (!(item instanceof Integer value)) {
                throw new IllegalArgumentException("expected Integer");
            }
            checked.add(value);
        }
        return checked;
    }

    public static void main(String[] args) {
        // Positive path: tạo List<Number> khác thay vì alias.
        List<Integer> original =
                new ArrayList<>(List.of(1, 2));
        List<Number> independent = new ArrayList<>(original);
        independent.add(3.5);

        System.out.println(
                "safe=" + original + " / " + independent
        );

        // Test-only misuse: raw alias vượt generic type checking.
        List<Integer> vulnerable =
                new ArrayList<>(List.of(1));
        List raw = vulnerable;
        raw.add(3.5);

        System.out.println("rawWrite=" + vulnerable.size());

        Object observed = vulnerable.get(1);
        System.out.println("rawAsObject=" + observed);

        try {
            Integer bad = vulnerable.get(1);
            System.out.println(bad);
        } catch (ClassCastException ex) {
            System.out.println("typedRead=ClassCastException");
        }

        // Array covariance: lỗi ngay tại thao tác ghi.
        Number[] array = new Integer[1];
        try {
            array[0] = 3.5;
        } catch (ArrayStoreException ex) {
            System.out.println("arrayWrite=ArrayStoreException");
        }

        // Validate đầu vào trước khi phát hành List<Integer>.
        try {
            requireIntegers(vulnerable);
        } catch (IllegalArgumentException ex) {
            System.out.println("boundary=IllegalArgumentException");
        }

        System.out.println(
                "validated=" + requireIntegers(List.of(3, 4))
        );
    }
}
~~~

Biên dịch, quan sát warning và chạy:

~~~bash
javac --release 17 -Xlint:rawtypes,unchecked InvarianceRegressionDemo.java
java InvarianceRegressionDemo
~~~

**Stdout đã kiểm chứng:**

~~~text
safe=[1, 2] / [1, 2, 3.5]
rawWrite=2
rawAsObject=3.5
typedRead=ClassCastException
arrayWrite=ArrayStoreException
boundary=IllegalArgumentException
validated=[3, 4]
~~~

Giải thích thứ tự:
1. **safe:** bản sao Number chứa thêm Double, source Integer giữ nguyên.
2. **rawWrite=2:** thêm Double qua raw alias thành công và tăng size; chưa có CCE.
3. **rawAsObject=3.5:** cùng phần tử được đọc như Object nên không cần cast Integer.
4. **typedRead=ClassCastException:** runtime mới phải đối xử Double như Integer.
5. **arrayWrite=ArrayStoreException:** JVM chặn ghi Double vào Integer[] thật.
6. **boundary=IllegalArgumentException:** validator phát hiện Double theo schema Integer và từ chối.
7. **validated=[3, 4]:** dữ liệu đúng được duyệt và tạo typed copy an toàn.

Đừng gộp negative compilation `CompileShouldFail.java` vào chương trình này. Nó có mục đích riêng: **biên dịch phải thất bại**, trong khi file này **phải compile thành công với một số warnings cố ý** và có output ổn định.

## 9. Những trường hợp test dễ tạo kết luận sai

### “Không có ClassCastException ở raw add, nên dữ liệu an toàn”

Sai. Raw add có thể đi qua erased Object parameter. Lỗi chỉ xuất hiện khi một operation yêu cầu typed result và runtime cast thất bại.

### “List<?> là danh sách immutable”

Sai. `List<?>` là kiểu với type argument chưa biết. Nó ngăn thêm phần tử non-null tùy ý bằng generic API nhưng không ngăn `clear` hay `remove`, và object thực tế vẫn có thể mutable.

### “Một test chỉ có ClassCastException chứng minh invariance đã hoạt động”

Chưa đủ. Invariance bảo vệ ở compile time, nên phải test **compiler reject** cho phép gán sai. ClassCastException xuất hiện sau khi code đã vượt kiểm tra generic hoặc có cast sai ở runtime.

### “Mọi exception cần xử lý bằng catch”

Không. Với một generic API mới, ưu tiên không tạo đường raw alias gây pollution. Chỉ catch exception ở boundary nghiệp vụ thích hợp, đồng thời giữ compiler contracts chính xác.

### “Java generics chặn mọi runtime type errors”

Sai. Generics phát hiện nhiều sai sót tĩnh, nhưng reflection, raw types, unchecked casts/conversions và dữ liệu bên ngoài vẫn cần xử lý có trách nhiệm. Compiler-inserted casts và runtime checks không biến một dữ liệu sai thành đúng.

### “Dùng checkedCast hoặc wrapper là khỏi cần validation”

`Class<Integer>` chỉ giúp kiểm tra từng object là Integer; không xác nhận toàn bộ `List<Integer>` bằng một cast vào List. `Collections.checkedList` có thể kiểm tra những lần insert thông qua wrapper khi có class token phù hợp, nhưng nó không ngăn mutation bypass wrapper qua alias tới backing collection.

## 10. Checklist regression test trước khi chấp nhận refactor

1. **Negative compilation:** xác nhận `List<Number> = List<Integer>` vẫn bị compiler từ chối.
2. **Positive wildcard:** nhận List<Integer> qua `? extends Number` và chỉ đọc.
3. **Positive copy:** xây `List<Number>` mới, thêm Double không sửa source.
4. **Raw warnings:** test path cố ý raw có `rawtypes/unchecked` diagnostics.
5. **Write vs read:** raw insertion đã thành công trước typed read failure.
6. **Object read:** đọc thành Object không tự chứng minh kiểu phần tử.
7. **Correct exception:** typed read phát ClassCastException ở đúng điểm.
8. **Array contrast:** array-store check phát ArrayStoreException tại write.
9. **Boundary validation:** từ chối dữ liệu sai từ List<?> và chấp nhận dữ liệu đúng.
10. **No unchecked workaround:** production API không dùng raw cast để lách invariant type arguments.
11. **Mutability:** positive write dùng collection thật sự sửa được.
12. **Type vs policy:** một List<Integer> đúng kiểu vẫn có thể vi phạm ràng buộc nghiệp vụ như giá trị âm, null policy hoặc giới hạn kích thước.

## 11. Câu hỏi tự kiểm tra

**1.** Invariance của List<T> chặn lỗi ở compile time hay runtime?

**Đáp án:** Chủ yếu ở compile time qua phép gán/call có type arguments không tương thích.

**2.** Có thể gán Integer cho Number không? Có suy ra gán List<Integer> cho List<Number> được không?

**Đáp án:** Có với từng Integer object; không với cả hai parameterized List types cụ thể.

**3.** Nếu raw add(Double) vào một List<Integer> thành công thì khi nào CCE có thể xuất hiện?

**Đáp án:** Khi code typed cần dùng Double như Integer, ví dụ `Integer i = list.get(index)`, chứ không nhất thiết tại raw add.

**4.** Nếu phải nhận List<Integer> để chỉ đọc Number thì có nên tạo List<Number> bản sao?

**Đáp án:** Thường không cần; chọn `List<? extends Number>` ở tham số nhận khi có thể sửa API.

**5.** Nếu phải thêm cả Integer và Double vào collection độc lập thì dùng gì?

**Đáp án:** `List<Number>` thực sự, có thể tạo bằng `new ArrayList<>(sourceIntegers)`.

**6.** Tại sao Number[] view tới Integer[] phát ArrayStoreException ở lệnh ghi Double?

**Đáp án:** JVM biết runtime component type Integer của array, nên chặn giá trị Double tại write.

**7.** Vì sao cast List<?> thành List<Integer> không đủ để xác thực dữ liệu?

**Đáp án:** Erasure không cho kiểm tra generic arguments của tất cả elements; phải validate từng element hoặc có nguồn invariant đáng tin.

**8.** Test chỉ assert một exception chung ở cuối workflow thiếu điều gì?

**Đáp án:** Không xác định nơi và giai đoạn lỗi thực sự xảy ra; có thể pass vì exception từ thao tác không liên quan.

**9.** Khi output của test in `rawAsObject=3.5`, điều đó chứng minh gì?

**Đáp án:** Value thật là Double và khi đọc dưới Object không yêu cầu cast thành Integer. Nó không xác nhận collection an toàn theo hợp đồng Integer.

**10.** Một List<Integer> có bảo đảm không chứa null hoặc chỉ có giá trị dương không?

**Đáp án:** Không. Generic type relationship khác với null/giá trị nghiệp vụ; cần validation và dữ liệu contract riêng.

**Ghi nhớ:** **compiler ngăn alias sai trước khi chạy**; nếu cố ý vượt generic checking, lỗi có thể lộ muộn ở typed read. Thiết kế tốt kết hợp negative compilation tests, positive wildcard/copy tests, diagnostics cho raw paths và validation tại ranh giới để ngăn dữ liệu sai lan vào core application.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-relations-006">Phân biệt List&lt;Object&gt;, List&lt;?&gt; và raw List</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `List<Object>`, `List<?>` và raw `List`

Trong Java Generics, cả ba khai báo đều liên quan đến `java.util.List`, nhưng chúng diễn tả **ba hợp đồng kiểu khác nhau**. Điểm mấu chốt là phân biệt: (1) danh sách được khai báo chứa phần tử kiểu `Object`; (2) danh sách có kiểu phần tử **chưa biết**; và (3) danh sách đã **bỏ qua kiểm tra tham số kiểu**. Nhìn ba khai báo giống nhau về hình dáng không có nghĩa chúng có cùng mức độ an toàn.

## 1. Ba khai báo có ý nghĩa gì?

```java
List<Object> objects; // Danh sách có kiểu phần tử chính xác là Object
List<?> unknown;       // Danh sách có một kiểu phần tử nào đó, chưa biết kiểu nào
List raw;              // Raw type: không khai báo tham số kiểu của List<E>
```

- **`List<Object>`**: `E` là `Object`. Ta có thể đưa vào phần tử `String`, `Integer` hoặc một đối tượng khác vì tất cả đều là đối tượng có thể gán cho `Object`. Khi đọc, kiểu tĩnh trả về là `Object`.
- **`List<?>`**: có một tham số kiểu thực sự, nhưng mã đang sử dụng danh sách **không biết** tham số đó là `String`, `Integer` hay kiểu nào khác. Ký hiệu `?` là *unbounded wildcard*. Khi đọc một phần tử, kết quả an toàn nhất là `Object`; khi thêm phần tử thông thường, trình biên dịch phải từ chối vì không biết phần tử có hợp kiểu hay không.
- **Raw `List`**: cách viết không có `<...>`, chủ yếu tồn tại để tương thích với mã có trước Generics. Trình biên dịch cho phép nhiều lời gọi thao tác với `Object` mà không bảo vệ tham số kiểu; thao tác không an toàn thường tạo cảnh báo `unchecked`. Đây **không phải** cách biểu diễn tương đương với `List<?>`.

`Object` trong `List<Object>` là **một kiểu cụ thể**, còn `?` trong `List<?>` là **một kiểu chưa biết**. Sự khác nhau này quyết định các phép gán và thao tác nào được phép.

## 2. Vì sao `List<String>` không phải là `List<Object>`?

Giả sử có một danh sách chỉ dành cho chuỗi:

```java
List<String> names = new ArrayList<>();
names.add("An");
```

Dù `String` là kiểu con của `Object`, Java Generics **bất biến** (*invariant*): từ `String extends Object` không thể suy ra `List<String> extends List<Object>`. Đoạn sau cố ý không biên dịch:

```java
List<String> names = new ArrayList<>();
List<Object> objects = names; // LỖI BIÊN DỊCH: incompatible types
objects.add(123);             // Nếu được phép, danh sách String sẽ chứa Integer!
```

Nếu phép gán ấy hợp lệ, một người dùng `objects` có thể thêm `Integer` và làm hỏng cam kết của người dùng `names`. Tính bất biến ngăn chuyện này ngay từ lúc biên dịch.

Có hai lựa chọn hợp lệ nhưng mang ý nghĩa khác nhau:

```java
List<String> names = new ArrayList<>(List.of("An", "Binh"));

List<?> view = names;                       // Cùng đối tượng List, chỉ nhìn qua wildcard
List<Object> copy = new ArrayList<>(names); // Tạo một List<Object> MỚI, chứa bản sao phần tử
copy.add(123);                              // Hợp lệ, không làm thay đổi names
```

Constructor `ArrayList(Collection<? extends E>)` cho phép sao chép phần tử `String` vào một `ArrayList<Object>` mới. Khác với `view`, `copy` **không phải alias** của danh sách gốc; cả hai vẫn có thể chứa tham chiếu đến cùng đối tượng phần tử nếu phần tử là đối tượng mutable.

## 3. So sánh phép gán, đọc và ghi

| Câu hỏi | `List<Object>` | `List<?>` | Raw `List` |
|---|---|---|---|
| Gán trực tiếp một biến `List<String>` vào? | **Không**; Generics bất biến | **Có** | **Có**, nhưng mất thông tin tham số kiểu |
| Kiểu tĩnh khi gọi `get(0)`? | `Object` | `Object` | `Object` |
| `add("text")` hoặc `add(123)`? | **Có** | **Không** | **Có**, thao tác raw không an toàn có thể bị cảnh báo `unchecked` |
| `add(null)`? | Có | Có | Có |
| `set(0, "text")`? | Có | Không | Có, có thể bị cảnh báo |
| Ngăn ghi sai kiểu cho danh sách được tham chiếu? | Có, theo kiểu `Object` của chính danh sách | Có, đối với giá trị phần tử chưa biết kiểu | **Không** |
| Mục đích phù hợp | Cần lưu nhiều loại đối tượng trong một danh sách | Chỉ cần xử lý một danh sách bất kỳ mà không phụ thuộc loại phần tử | Tương tác mã/API cũ chưa khai báo Generics |

**Lưu ý quan trọng:** hạn chế `add` / `set` của `List<?>` **không đồng nghĩa danh sách hoàn toàn bất biến**. Những thao tác không cần cung cấp giá trị kiểu `E` như `clear()`, `remove(Object)` hoặc `remove(int)` vẫn có thể làm thay đổi danh sách. `add(null)` được phép ở mức kiểm tra kiểu vì `null` có thể gán cho mọi kiểu tham chiếu; tuy vậy danh sách bất biến do `List.of(...)` hay `Collections.unmodifiableList(...)` tạo ra vẫn có thể ném `UnsupportedOperationException` khi gọi phương thức sửa đổi. Hãy phân biệt **khả năng biên dịch theo kiểu** và **khả năng sửa đổi thực tế của đối tượng**.

## 4. Ví dụ Java 17 chạy được: raw type phá vỡ hợp đồng như thế nào?

Tạo tệp `ListTypeRelationsDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class ListTypeRelationsDemo {
    public static void main(String[] args) {
        List<Object> objects = new ArrayList<>();
        objects.add("Java");
        objects.add(17);
        System.out.println("Objects: " + objects);

        List<String> words = new ArrayList<>(List.of("alpha", "beta"));

        List<?> unknown = words;
        Object first = unknown.get(0);
        System.out.println("Unknown first: " + first);
        unknown.add(null);
        System.out.println("Unknown after null: " + words);

        List raw = words;
        raw.set(1, 99);
        System.out.println("Raw second: " + raw.get(1));

        try {
            String second = words.get(1);
            System.out.println("Typed second: " + second);
        } catch (ClassCastException ex) {
            System.out.println("Typed second: ClassCastException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:rawtypes,unchecked ListTypeRelationsDemo.java
java ListTypeRelationsDemo
```

**Stdout** (không bao gồm cảnh báo biên dịch trên stderr):

```text
Objects: [Java, 17]
Unknown first: alpha
Unknown after null: [alpha, beta, null]
Raw second: 99
Typed second: ClassCastException
```

Diễn giải từng bước:

1. `objects` là `List<Object>`: cả `String` và `Integer` đều phù hợp với `Object`; không có vi phạm hợp đồng kiểu.
2. `unknown` là `List<?>` cùng trỏ tới `words`. Đọc được phần tử dưới kiểu `Object`, nhưng **không thể** viết `unknown.add(99)` hay `unknown.add("gamma")` vì kiểu phần tử thật là điều chưa biết. `unknown.add(null)` biên dịch được và ở đây `ArrayList` cho phép thêm.
3. `raw` cũng trỏ tới **chính `words`**. `raw.set(1, 99)` qua mặt kiểm tra tham số `String`, khiến danh sách thực tế chứa `Integer` ở vị trí thứ hai. Khi `words.get(1)` được gán cho `String`, compiler đã chèn phép ép kiểu thích hợp; tại thời điểm chạy việc chuyển `Integer` thành `String` thất bại và ném `ClassCastException`.
4. Với `-Xlint:rawtypes,unchecked`, `javac` cảnh báo nơi dùng raw type và lời gọi không được kiểm tra. Số dòng, nội dung cảnh báo phụ thuộc phiên bản và vị trí mã; cảnh báo **không ngăn chương trình chạy** nếu không bật cơ chế coi cảnh báo là lỗi.

Điều nguy hiểm của raw type là lỗi có thể xuất hiện **muộn** ở một vị trí đọc khác hẳn nơi ghi sai. Hiện tượng dữ liệu bên trong một cấu trúc generic không còn đáp ứng tham số kiểu đã cam kết được gọi là *heap pollution*. Raw type là một nguồn thường gặp của vấn đề này.

## 5. `List<?>` bảo vệ cái gì và giới hạn đến đâu?

Khi viết `List<?> values = someList;`, ta chấp nhận rằng `values` có thể đang tham chiếu `List<String>`, `List<Integer>` hoặc một `List<E>` bất kỳ. Vì vậy:

```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20));
List<?> values = numbers;

Object value = values.get(0); // Hợp lệ: đọc được dưới kiểu Object
int count = values.size();     // Hợp lệ: không phụ thuộc E
boolean hasTen = values.contains(10); // Hợp lệ: contains nhận Object

// values.add(30);        // Không biên dịch
// values.set(0, 30);     // Không biên dịch
values.add(null);        // Hợp lệ về kiểu, nhưng làm thay đổi numbers
```

Tại sao `values.add(30)` bị từ chối dù ví dụ này biết `numbers` là `List<Integer>`? Vì compiler xét **kiểu tĩnh `List<?>` của biến `values`**. Phương thức chấp nhận phần tử kiểu ẩn bên trong dấu `?`, trong khi `30` chỉ được chứng minh là `Integer`, không được chứng minh phù hợp với mọi kiểu ẩn có thể có.

Không thể “đổi dấu hỏi thành Object” chỉ để thêm giá trị:

```java
List<?> wildcard = new ArrayList<String>();
// List<Object> wrong = wildcard; // LỖI BIÊN DỊCH
```

Giá trị `wildcard.get(0)` có kiểu `Object` chỉ có nghĩa mọi phần tử đọc được đều là đối tượng; nó **không** làm cho kiểu phần tử mà danh sách chấp nhận trở thành `Object`.

### Wildcard capture: thay đổi an toàn khi giữ nguyên kiểu phần tử

Có trường hợp cần hoán đổi các phần tử hiện có mà không chèn một giá trị từ ngoài vào. Ta có thể dùng một phương thức generic phụ để compiler “bắt” kiểu ẩn của `?` thành `T`.

Tệp `WildcardCaptureDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardCaptureDemo {
    public static void main(String[] args) {
        List<String> labels = new ArrayList<>(List.of("first", "second"));
        swapFirstTwo(labels);
        System.out.println(labels);
    }

    static void swapFirstTwo(List<?> input) {
        swapCaptured(input);
    }

    private static <T> void swapCaptured(List<T> items) {
        if (items.size() < 2) {
            return;
        }
        T first = items.get(0);
        items.set(0, items.get(1));
        items.set(1, first);
    }
}
```

Chạy `javac WildcardCaptureDemo.java` rồi `java WildcardCaptureDemo`, stdout:

```text
[second, first]
```

Ở hàm `swapCaptured`, cả giá trị đọc và vị trí ghi đều có cùng một kiểu `T`, nên hợp lệ. Không cần raw type hay ép kiểu. Mẫu này chỉ đảm bảo **an toàn kiểu**; `set` vẫn có thể ném `UnsupportedOperationException` nếu danh sách được truyền vào không cho phép sửa.

## 6. Chọn kiểu cho tham số phương thức/API

### Chỉ cần quan sát hoặc xử lý chung: dùng `List<?>`

```java
static void printElements(List<?> elements) {
    for (Object element : elements) {
        System.out.println(element);
    }
}
```

`printElements` chấp nhận `List<String>`, `List<Integer>`, `List<Object>`... vì không cần biết hay cung cấp phần tử kiểu `E`. Đây là thiết kế phù hợp khi API không có nhu cầu liên hệ hoặc ghi một kiểu phần tử cụ thể.

### Thực sự cần đưa mọi loại đối tượng vào cùng một danh sách: dùng `List<Object>`

```java
static void recordEvent(List<Object> events, Object event) {
    events.add(event);
}
```

API này có ý nghĩa khi bên gọi **chủ động sở hữu** một danh sách có kiểu phần tử `Object`, cho phép chứa dữ liệu hỗn hợp. Không thể truyền `List<String>` vào vì phương thức có quyền thêm bất kỳ `Object` nào. Nếu dữ liệu cần đồng nhất kiểu, dùng tham số kiểu riêng như `<T>` thay vì mặc định lựa chọn `Object`.

### Cần duy trì quan hệ giữa đầu vào và đầu ra: dùng type parameter

```java
static <T> T firstOrNull(List<T> items) {
    return items.isEmpty() ? null : items.get(0);
}
```

Khi gọi với `List<String>`, kết quả có kiểu `String`; với `List<Integer>`, kết quả có kiểu `Integer`. Đối với một phép chỉ cần in các phần tử, `List<?>` dễ đọc hơn; đối với phép phải **giữ quan hệ kiểu `T`**, phương thức generic là giải pháp rõ ràng hơn.

### Khi yêu cầu đọc/ghi linh hoạt có giới hạn

`List<? extends Number>` cho phép nhận các danh sách kiểu phần tử là `Number` hoặc kiểu con, thích hợp để **đọc giá trị như `Number`**; không thể thêm một `Integer` bất kỳ vào danh sách chưa biết kiểu con. `List<? super Integer>` cho phép nhận `List<Integer>`, `List<Number>` hoặc `List<Object>` và **thêm `Integer`**; giá trị đọc ra chỉ bảo đảm là `Object`. Đây là các wildcard có giới hạn, mở rộng bài toán chứ không làm thay đổi sự khác biệt nền tảng của ba khai báo đang xét. Quy tắc gợi nhớ PECS (*Producer Extends, Consumer Super*) giúp chọn wildcard khi cần quan hệ kiểu cụ thể.

## 7. Raw `List`: khi nào tồn tại và cách đi qua ranh giới an toàn

Raw type thường gặp khi làm việc với API cũ được viết trước Java 5 hoặc các thư viện chưa khai báo kiểu. Ví dụ sau **cố ý minh họa thao tác không an toàn**, không nên dùng làm mẫu cho API mới:

```java
List raw = new ArrayList();
raw.add("ok");
raw.add(42);

List<String> assumedStrings = raw; // Unchecked conversion: compiler không xác minh được
// String second = assumedStrings.get(1); // Có thể ném ClassCastException
```

Tình huống này có **hai** chỗ rủi ro: gọi `add` trên raw `List` không kiểm tra tham số kiểu, rồi chuyển raw `List` thành `List<String>` tạo ra cam kết kiểu mà compiler không thể xác minh. Ép kiểu thành `List<String>` cũng **không** xác nhận từng phần tử do *type erasure* (tham số generic không được giữ như một kiểm tra toàn bộ nội dung ở runtime).

Một hướng xử lý đúng khi nhận danh sách không đáng tin là **xem dữ liệu qua `List<?>`, kiểm tra từng phần tử, và sao chép sang danh sách được định kiểu**:

```java
import java.util.ArrayList;
import java.util.List;

public class LegacyListValidationDemo {
    static List<String> validatedStrings(List<?> input) {
        List<String> result = new ArrayList<>();
        for (Object value : input) {
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException("Expected String element");
            }
            result.add(text);
        }
        return result;
    }

    public static void main(String[] args) {
        List<?> legacyData = List.of("one", "two");
        List<String> safe = validatedStrings(legacyData);
        System.out.println(safe);
    }
}
```

Biên dịch `javac LegacyListValidationDemo.java` và chạy `java LegacyListValidationDemo`; stdout:

```text
[one, two]
```

Hàm trên chủ động **không chấp nhận `null`** vì `null instanceof String` là `false`. Nếu yêu cầu nghiệp vụ cho phép phần tử null, hãy xử lý quy tắc đó riêng. `result` là danh sách mới được kiểm tra từng phần tử; việc về sau có ai đó thay đổi `input` cũng không đưa phần tử sai kiểu vào `result` qua alias danh sách.

Khi buộc phải xử lý raw type ở một ranh giới tương thích, hãy khoanh vùng thật nhỏ nơi dùng raw/unchecked, xem xét dữ liệu trước khi chuyển thành cấu trúc có kiểu, và chỉ dùng `@SuppressWarnings("unchecked")` nếu có lý do cụ thể chứng minh thao tác là an toàn. Annotation này **chỉ ẩn cảnh báo**, không thêm kiểm tra runtime.

## 8. Những nhầm lẫn phổ biến

1. **“`String` extends `Object` nên `List<String>` extends `List<Object>`.”** Sai: quan hệ kế thừa của phần tử không tự truyền sang `List<E>`; Generics bất biến.
2. **“`List<?>` cũng là `List<Object>`.”** Sai: `?` là tham số kiểu chưa biết, không phải tên gọi khác của `Object`. Cả hai cùng đọc được dưới kiểu `Object` nhưng khác quyền ghi và quy tắc gán.
3. **“Wildcard không cho sửa danh sách.”** Sai: `add(null)`, `clear()`, `remove(...)` và những thao tác không cần một giá trị thuộc kiểu ẩn có thể hợp lệ về mặt kiểu. Quyền sửa đổi thực tế do implementation quyết định.
4. **“Raw type và wildcard đều nhận mọi danh sách nên tương đương.”** Sai: cả hai có thể nhận `List<String>`, nhưng raw type cho phép lời gọi thêm/sửa không được kiểm tra, còn wildcard giữ rào chắn an toàn tại compile time.
5. **“Cảnh báo unchecked không sao vì code biên dịch được.”** Sai: cảnh báo biểu thị vị trí compiler không chứng minh được an toàn kiểu; lỗi có thể lộ ra tại một lần đọc khác bằng `ClassCastException`.
6. **“`(List<String>) raw` sẽ kiểm tra mọi phần tử là `String`.”** Sai: do type erasure, việc ép kiểu danh sách không tự duyệt các phần tử. Muốn chắc chắn, phải kiểm tra dữ liệu hoặc dựa vào hợp đồng đáng tin của nguồn.
7. **“Dùng `List<Object>` ở mọi tham số giúp API linh hoạt hơn.”** Sai: tham số ấy lại từ chối `List<String>` và `List<Integer>`. Nếu chỉ đọc chung, `List<?>` thường linh hoạt và an toàn hơn.

## 9. Bài tự kiểm tra

Giả sử:

```java
List<Integer> nums = new ArrayList<>(List.of(1, 2));
List<?> a = nums;
List raw = nums;
List<Object> b = new ArrayList<>(nums);
```

Hãy dự đoán:

- `a.get(0)` và `b.get(0)` có kiểu tĩnh gì?
- `a.add(3)` có biên dịch không? `b.add("x")` có biên dịch không?
- `raw.add("x")` có biên dịch không, và rủi ro là gì?
- `b.add("x")` có làm `nums` thay đổi không?

**Đáp án:** Cả hai lời gọi `get(0)` có kiểu tĩnh `Object`; `a.add(3)` không biên dịch do kiểu ẩn, còn `b.add("x")` hợp lệ. `raw.add("x")` biên dịch nhưng có thể bị cảnh báo `unchecked` và làm ô nhiễm `nums`, khiến người đọc qua `List<Integer>` gặp lỗi ép kiểu lúc chạy. Thay đổi `b` không làm thay đổi cấu trúc `nums` vì `new ArrayList<>(nums)` đã tạo một danh sách mới.

## 10. Quy tắc chọn nhanh

- Dùng **`List<Object>`** khi muốn **một danh sách thực sự được phép chứa nhiều kiểu đối tượng** và chấp nhận việc đọc qua `Object`.
- Dùng **`List<?>`** khi cần **nhận/xem một danh sách có kiểu phần tử bất kỳ**, nhưng không cần đưa một phần tử có kiểu cụ thể vào danh sách. Nếu cần giữ liên hệ kiểu giữa nhiều giá trị, cân nhắc phương thức `<T>` hoặc wildcard có giới hạn.
- Tránh tạo API mới với **raw `List`**. Chỉ giữ nó ở ranh giới tích hợp legacy thật sự cần thiết, xử lý cảnh báo có chủ đích và kiểm tra dữ liệu trước khi nâng lên một `List<T>` đáng tin.

Một cách thử khi phân vân là hỏi: **“Tôi có quyền thêm một giá trị `Object` bất kỳ vào danh sách này không?”** Nếu có, `List<Object>` thể hiện hợp đồng phù hợp. Nếu không biết danh sách chứa kiểu gì và chỉ cần thao tác chung, `List<?>` thể hiện sự thiếu thông tin một cách an toàn. Raw `List` xóa mất phần bảo vệ mà Generics vốn được thiết kế để cung cấp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-relations-007">Quan hệ kiểu giữa các wildcard có giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ kiểu giữa các wildcard có giới hạn

## 1. Mục tiêu và bối cảnh

Trong Java Generics, một quan hệ kế thừa giữa hai kiểu phần tử **không tự động tạo ra quan hệ kế thừa giữa hai kiểu generic**. Ví dụ, vì `Integer` là kiểu con của `Number`, ta có thể gán `Integer` cho biến `Number`, nhưng không thể gán `List<Integer>` cho biến `List<Number>`.

Wildcard có giới hạn giúp mô tả chính xác nhóm kiểu phần tử mà một tham số generic chấp nhận. Bài này tập trung vào **quan hệ kiểu giữa các cách sử dụng wildcard**: khi nào một `List<? extends A>` có thể gán cho `List<? extends B>`, khi nào một `List<? super A>` có thể gán cho `List<? super B>`, và tại sao hai chiều lại ngược nhau.

Sau bài học, bạn cần tự xác định được chiều gán hợp lệ, phân biệt khả năng đọc/ghi, viết API với PECS, và giải thích được các lỗi biên dịch liên quan đến wildcard.

## 2. Ba khái niệm nền tảng

Giả sử `Integer` là subtype của `Number` và `Number` là subtype của `Object`:

```text
Integer <: Number <: Object
```

Ký hiệu `S <: T` nghĩa là một giá trị có kiểu `S` có thể được dùng ở nơi cần `T` theo quan hệ subtype. Với `List`, hãy phân biệt:

- `List<Integer>`: phần tử có kiểu chính xác `Integer` theo khai báo generic.
- `List<? extends Number>`: danh sách với kiểu phần tử **chưa biết**, nhưng kiểu đó là `Number` hoặc subtype của `Number`.
- `List<? super Integer>`: danh sách với kiểu phần tử **chưa biết**, nhưng kiểu đó là `Integer` hoặc supertype của `Integer`.

Wildcard không làm thay đổi kiểu runtime của đối tượng. Nó thay đổi điều mà **trình biên dịch có thể bảo đảm** khi ta sử dụng tham chiếu generic.

### 2.1. Tính bất biến (invariance)

```java
List<Integer> integers = new ArrayList<>();
// List<Number> numbers = integers; // LỖI BIÊN DỊCH
```

Nếu dòng gán trên được phép, mã phía sau có thể gọi `numbers.add(3.14)` và phá vỡ lời hứa rằng `integers` chỉ chứa `Integer`. Do đó, `List<Integer>` và `List<Number>` không có quan hệ subtype chỉ vì `Integer <: Number`.

### 2.2. Wildcard là một kiểu phần tử chưa biết

`List<? extends Number>` không đồng nghĩa với `List<Number>`: danh sách thực tế có thể là `List<Integer>`, `List<Double>`, hoặc `List<Number>`. Tương tự, `List<? super Integer>` có thể tham chiếu đến `List<Integer>`, `List<Number>` hoặc `List<Object>`.

Dấu `?` mang ý nghĩa **có một kiểu thực tế cụ thể nhưng ta không biết chính xác là kiểu nào**. Vì vậy, không được tùy tiện xem nó là `Number`, `Integer` hay `Object` theo ý muốn.

## 3. Quy tắc quan hệ kiểu của `? extends`

Với `A <: B`, quan hệ sau là hợp lệ khi hai bên dùng cùng một kiểu generic `G`:

```text
G<? extends A> <: G<? extends B>
```

Đây là **chiều mở rộng cận trên**: một danh sách có cận trên hẹp (`Integer`) có thể được dùng như danh sách có cận trên rộng hơn (`Number`). Lý do: mọi kiểu không vượt quá `Integer` cũng không vượt quá `Number`.

```java
List<? extends Integer> source = List.of(10, 20);
List<? extends Number> target = source; // HỢP LỆ
Number value = target.get(0);           // HỢP LỆ
```

Chiều ngược lại không an toàn:

```java
List<? extends Number> source = List.of(1.5, 2.5);
// List<? extends Integer> target = source; // LỖI BIÊN DỊCH
```

`source` có thể thực chất là `List<Double>`. Java không thể bảo đảm tất cả phần tử là `Integer`.

Điểm quan trọng: **điều đang thay đổi là giới hạn wildcard**, không phải Java đang cho phép `List<Integer>` trở thành `List<Number>`.

### 3.1. Các gán hợp lệ từ kiểu cụ thể

```java
List<Integer> ints = List.of(1, 2);
List<? extends Integer> a = ints;
List<? extends Number> b = ints;
List<? extends Object> c = ints;
List<?> d = ints;
```

Tất cả đều hợp lệ. Mỗi biến `a`–`d` chứa cùng tham chiếu tới danh sách ban đầu, nhưng càng mở rộng cận trên, trình biên dịch càng biết ít về kiểu phần tử khi đọc.

### 3.2. Hệ quả khi đọc và ghi

Với `List<? extends Number>`:

- Đọc phần tử dưới dạng `Number` được, vì mọi kiểu được chấp nhận đều là subtype của `Number`.
- Không thể thêm `Integer`, `Double` hay một `Number` bất kỳ, vì kiểu phần tử thực tế có thể là một subtype khác.
- `add(null)` được phép về mặt kiểu (nếu cấu trúc danh sách hỗ trợ thao tác thêm), vì `null` tương thích với mọi kiểu tham chiếu.

```java
List<? extends Number> numbers = new ArrayList<Integer>();
Number n = numbers.get(0); // Mã chỉ minh họa kiểm tra kiểu; list rỗng nên không chạy dòng này.
// numbers.add(123);       // LỖI BIÊN DỊCH
numbers.add(null);         // Được phép về kiểu; ArrayList ở ví dụ là mutable.
```

Nhớ rằng **được phép biên dịch** không có nghĩa một implementation nhất định luôn cho phép thay đổi: `List.of(...)` là immutable và có thể ném `UnsupportedOperationException` ngay cả với thao tác thay đổi hợp lệ về kiểu.

## 4. Quy tắc quan hệ kiểu của `? super`

Vẫn giả sử `A <: B`. Với cận dưới, quan hệ đảo chiều:

```text
G<? super B> <: G<? super A>
```

Ví dụ:

```java
List<? super Number> source = new ArrayList<Object>();
List<? super Integer> target = source; // HỢP LỆ
target.add(7);                        // HỢP LỆ
```

Giải thích: mọi kiểu có thể chứa `Number` (như `Number` hay `Object`) chắc chắn cũng chứa được `Integer`. Nhưng một danh sách chỉ bảo đảm chứa được `Integer` chưa chắc chứa được mọi `Number` (ví dụ `Double`).

Chiều ngược không hợp lệ:

```java
List<? super Integer> source = new ArrayList<Integer>();
// List<? super Number> target = source; // LỖI BIÊN DỊCH
```

Nếu dòng gán được phép, `target.add(3.14)` sẽ đưa `Double` vào một `List<Integer>`.

### 4.1. Các gán hợp lệ từ kiểu cụ thể

```java
List<Integer> ints = new ArrayList<>();
List<Number> nums = new ArrayList<>();
List<Object> objects = new ArrayList<>();

List<? super Integer> a = ints;
List<? super Integer> b = nums;
List<? super Integer> c = objects;

List<? super Number> d = nums;
List<? super Number> e = objects;
// List<? super Number> f = ints; // LỖI BIÊN DỊCH
```

### 4.2. Hệ quả khi đọc và ghi

Với `List<? super Integer>`:

- Có thể thêm `Integer` và subtype của `Integer` (trong thực tế `Integer` là `final`).
- Không thể thêm `Number` tùy ý, vì danh sách thực tế có thể chỉ là `List<Integer>`.
- Giá trị đọc ra chỉ được bảo đảm có kiểu `Object`: kiểu phần tử thực tế có thể là `Integer`, `Number` hoặc `Object`.

```java
List<? super Integer> sink = new ArrayList<Number>();
sink.add(42);
Object item = sink.get(0);
// Integer value = sink.get(0); // LỖI BIÊN DỊCH: chỉ bảo đảm Object.
// sink.add(3.14);              // LỖI BIÊN DỊCH: Double không phải Integer.
```

Ép kiểu `(Integer) item` chỉ hợp lệ về runtime nếu đối tượng thực sự là `Integer`; wildcard không cho phép suy luận điều đó cho mọi phần tử.

## 5. Sơ đồ quan hệ và cách ghi nhớ

Với `Integer <: Number <: Object`, các mũi tên sau biểu thị phép gán từ trái sang phải là hợp lệ:

```text
Nhánh cận trên (? extends):
List<Integer>
    -> List<? extends Integer>
    -> List<? extends Number>
    -> List<? extends Object> (tương đương về cận với List<?>)

Nhánh cận dưới (? super):
List<Object>
    -> List<? super Number>
    -> List<? super Integer>
    -> List<?>

Ngoài ra:
List<Number> -> List<? extends Number>
List<Number> -> List<? super Number>
List<Number> -> List<? super Integer>
List<Integer> -> List<? super Integer>
```

| Bài toán | Chiều quan hệ khi `A <: B` | Điều được bảo đảm |
| --- | --- | --- |
| Cận trên `extends` | `G<? extends A> <: G<? extends B>` | Đọc được ít nhất như `B` |
| Cận dưới `super` | `G<? super B> <: G<? super A>` | Có thể ghi các giá trị kiểu `A` |
| Generic cụ thể | `G<A>` **không** suy ra `G<B>` | Bất biến đối với tham số kiểu |
| Không giới hạn `?` | Các cách dùng `G<T>`, `G<? extends T>`, `G<? super T>` đều có thể gán vào `G<?>` | Đọc an toàn như `Object` |

Khi xét sơ đồ, không suy ra `List<? extends Number>` và `List<? super Integer>` có quan hệ subtype trực tiếp với nhau. Cả hai có thể tham chiếu cùng một `List<Number>`, nhưng một biến kiểu thứ nhất không tự động gán được cho biến kiểu thứ hai: mỗi biến có những bảo đảm khác nhau.

## 6. Ví dụ Java 17+ hoàn chỉnh, có thể chạy

Lưu thành tệp `WildcardRelationsDemo.java`. Chương trình minh họa **cả hai chiều gán hợp lệ** và truyền dữ liệu từ producer sang consumer.

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardRelationsDemo {
    static <T> void copyAll(List<? extends T> source, List<? super T> target) {
        for (T element : source) {
            target.add(element);
        }
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(1, 2);

        // Cận trên: Integer -> Number.
        List<? extends Integer> integerProducer = integers;
        List<? extends Number> numberProducer = integerProducer;
        Number first = numberProducer.get(0);

        // Cận dưới: super Number -> super Integer.
        List<Object> objects = new ArrayList<>();
        List<? super Number> numberConsumer = objects;
        List<? super Integer> integerConsumer = numberConsumer;
        integerConsumer.add(3);

        Object inserted = integerConsumer.get(0);

        List<Number> numbers = new ArrayList<>();
        copyAll(integers, numbers);

        System.out.println(first);
        System.out.println(objects);
        System.out.println(inserted);
        System.out.println(numbers);
    }
}
```

Biên dịch và chạy:

```bash
javac WildcardRelationsDemo.java
java WildcardRelationsDemo
```

Kết quả stdout chính xác:

```text
1
[3]
3
[1, 2]
```

Trong `copyAll`, `source` sản xuất giá trị kiểu `T` (`? extends T`), còn `target` nhận giá trị kiểu `T` (`? super T`). Với `T = Integer`, `List<Integer>` là nguồn hợp lệ và `List<Number>` là đích hợp lệ. Ví dụ tự xây dựng phương thức `copyAll` theo nghĩa **append** vào đích; khác với `Collections.copy(dest, src)`, vốn yêu cầu danh sách đích đã có đủ vị trí phần tử để thay thế.

## 7. PECS và thiết kế API

**PECS = Producer Extends, Consumer Super**: chọn wildcard theo **cách phương thức dùng dữ liệu**.

Nếu một tham số chỉ cung cấp phần tử cho ta đọc, dùng `? extends T`:

```java
static double sum(List<? extends Number> values) {
    double total = 0;
    for (Number number : values) {
        total += number.doubleValue();
    }
    return total;
}
```

`sum` dùng được với `List<Integer>`, `List<Double>`, `List<Number>` mà không phải ép kiểu hoặc copy danh sách.

Nếu một tham số chỉ nhận phần tử ta đưa vào, dùng `? super T`:

```java
static void addDefaults(List<? super Integer> destination) {
    destination.add(10);
    destination.add(20);
}
```

`addDefaults` dùng được với `List<Integer>`, `List<Number>`, `List<Object>`.

Nếu một phương thức cần vừa **đọc thành T** vừa **ghi T** theo cùng kiểu phần tử chính xác, nhiều trường hợp nên dùng một type parameter `<T>` thay cho wildcard mơ hồ:

```java
static <T> void duplicateFirst(List<T> values) {
    if (!values.isEmpty()) {
        T first = values.get(0);
        values.add(first);
    }
}
```

Đây là đoạn khai báo phương thức minh họa, không phải chương trình độc lập. Tham số `List<T>` giữ được tương quan giữa kiểu dữ liệu đọc ra và kiểu dữ liệu được phép ghi vào.

## 8. Quan hệ giữa hai wildcard khác hướng

Không có quy tắc chung nào cho phép đổi tự do `extends` sang `super`:

```java
List<? extends Number> producer = List.of(1, 2);
// List<? super Integer> consumer = producer; // LỖI BIÊN DỊCH
```

Một `producer` hợp lệ có thể là `List<Double>`; danh sách đó không phải nơi có thể chèn `Integer`. Tương tự:

```java
List<? super Integer> consumer = new ArrayList<Object>();
// List<? extends Number> producer = consumer; // LỖI BIÊN DỊCH
```

Danh sách ở biến `consumer` có thể là `List<Object>`, trong đó có các phần tử không phải `Number`. Muốn chuyển vai trò một cách an toàn, thiết kế phương thức nhận nguồn và đích riêng, hoặc giữ một tham chiếu có kiểu cụ thể tại nơi khởi tạo thay vì ép kiểu không kiểm tra.

Một điểm tinh tế: việc **hai kiểu wildcard có thể chấp nhận cùng một đối tượng** không chứng minh chúng là subtype của nhau. Subtyping đòi hỏi phép gán phải an toàn với *mọi* đối tượng phù hợp với kiểu nguồn, không chỉ trường hợp cụ thể đang thấy.

## 9. Quan hệ khi generic và wildcard lồng nhau

Wildcard ở cấp trong không tự động làm generic cấp ngoài trở nên biến thiên.

```java
List<List<Integer>> nested = new ArrayList<>();

// List<List<? extends Number>> invalid = nested; // LỖI BIÊN DỊCH

List<? extends List<? extends Number>> valid = nested; // HỢP LỆ
```

Dòng thứ nhất sai vì tham số trực tiếp của `List` ngoài là `List<Integer>` và `List<? extends Number>`, hai kiểu khác nhau; `List` ngoài vẫn bất biến. Dòng cuối hợp lệ vì `? extends` ở lớp ngoài cho phép kiểu phần tử là subtype của `List<? extends Number>`, bao gồm `List<Integer>`.

Quy tắc thực hành: đánh giá quan hệ **tại từng cấp generic**. Đừng chỉ nhìn thấy `extends Number` ở bên trong rồi suy ra mọi lớp `List` bao ngoài đều gán được.

### 9.1. Quan hệ giữa interface generic khác nhau

Hai vế không bắt buộc cùng là `List` nếu có quan hệ interface/class thực sự. Ví dụ:

```java
List<Integer> ints = List.of(1, 2);
Collection<? extends Number> numbers = ints; // HỢP LỆ
Iterable<? extends Number> items = numbers;  // HỢP LỆ
```

Ở đây ta kết hợp hai cơ chế: `List<E>` kế thừa `Collection<E>`, `Collection<E>` kế thừa `Iterable<E>`, và cận wildcard `Integer <: Number`. Không được áp dụng điều này cho hai generic không có quan hệ kế thừa phù hợp.

## 10. Wildcard capture và giới hạn suy luận

Khi dùng một biến `List<?>`, trình biên dịch có thể tạm đặt một tên nội bộ (capture) cho kiểu phần tử chưa biết. Ta không thể dùng hai phép đọc và ghi rời rạc để giả định kiểu đó là `Object`:

```java
static void replaceFirstWithItself(List<?> list) {
    if (!list.isEmpty()) {
        // list.set(0, list.get(0)); // Thường LỖI BIÊN DỊCH do capture không khớp.
    }
}
```

Một phương thức helper có `<T>` có thể giữ kiểu capture nhất quán:

```java
static void swapFirstTwo(List<?> list) {
    swapCaptured(list);
}

private static <T> void swapCaptured(List<T> list) {
    if (list.size() >= 2) {
        T first = list.get(0);
        list.set(0, list.get(1));
        list.set(1, first);
    }
}
```

Hai phương thức trên là đoạn mã minh họa để đặt bên trong một class có `import java.util.List;`. Helper không cần biết `T` cụ thể, chỉ cần chứng minh giá trị lấy từ cùng danh sách luôn được ghi trở lại theo cùng `T`. Với danh sách immutable, thao tác `set` vẫn có thể ném `UnsupportedOperationException` khi chạy.

Wildcard capture không làm biến `List<? extends Number>` có thể thêm `Integer` tùy ý, cũng không biến `List<? super Integer>` thành nơi đọc chắc chắn được `Integer`. Nó chỉ giữ liên hệ kiểu khi thuật toán thực sự an toàn.

## 11. Lỗi phổ biến và cách sửa

| Nhận định hoặc thao tác sai | Vì sao sai | Cách sửa |
| --- | --- | --- |
| `List<Integer>` gán thẳng cho `List<Number>` | Generic bất biến | Nhận `List<? extends Number>` để đọc, hoặc copy phần tử vào một `List<Number>` mới |
| `List<? extends Number>` cho `add(1)` | Kiểu phần tử có thể là `Double` | Dùng `List<? super Integer>` nếu cần thêm `Integer` |
| Đọc `Integer` trực tiếp từ `List<? super Integer>` | Danh sách có thể chứa phần tử `Number` hoặc `Object` khác | Đọc dưới dạng `Object`; chỉ kiểm tra/ép kiểu khi có cơ sở runtime |
| Nghĩ `? super Integer` là subtype của `? super Number` | Chiều cận dưới ngược lại | Nhớ `? super Number` có thể gán cho `? super Integer` |
| Gán `List<? extends Number>` cho `List<? extends Integer>` | Cận trên bị thu hẹp, có thể chứa `Double` | Giữ cận `Number` hoặc chứng minh kiểu thực tế bằng thiết kế API |
| Dùng raw `List` để tránh lỗi | Mất kiểm tra kiểu, có nguy cơ `ClassCastException` | Dùng wildcard hoặc type parameter |
| Ép kiểu không kiểm tra `(List<Number>)(List<?>) ints` | Che lỗi bất biến, có thể phá hợp đồng kiểu | Dùng generic method, wildcard phù hợp hoặc tạo danh sách mới |
| Tưởng wildcard quyết định mutability | Kiểm tra kiểu và hỗ trợ thay đổi runtime là hai việc độc lập | Phân biệt lỗi biên dịch với `UnsupportedOperationException` |

Không thể viết wildcard có đồng thời hai cận như `? extends Number super Integer`. Nếu cần thể hiện ràng buộc phức tạp, cân nhắc type parameter và thiết kế lại API. Cú pháp cận giao nhau như `<T extends Number & Comparable<T>>` thuộc về khai báo type parameter, không đồng nghĩa với việc viết `? extends Number & Comparable<?>` như một wildcard thông thường.

Ngoài ra, generic trong Java chủ yếu được kiểm tra lúc biên dịch và bị xóa thông tin type argument khi chạy (type erasure). Bạn không thể dùng `instanceof List<? extends Number>` để kiểm tra subtype element của danh sách ở runtime; `instanceof List<?>` kiểm tra được loại raw tổng quát nhưng không xác thực kiểu phần tử.

## 12. Bài tập tự kiểm tra

Giả sử đã có:

```java
List<Integer> ints = new ArrayList<>();
List<Number> nums = new ArrayList<>();
List<Object> objs = new ArrayList<>();
```

Hãy dự đoán từng dòng độc lập có biên dịch hay không:

```java
List<? extends Number> q1 = ints;                    // (1)
List<? super Integer> q2 = nums;                      // (2)
List<? super Number> q3 = ints;                       // (3)
List<? extends Integer> q4 = nums;                    // (4)
List<? super Number> q5 = objs;                       // (5)
List<? super Integer> q6 = q5;                        // (6)
List<? extends Number> q7 = q1;                       // (7)
List<? super Integer> q8 = q1;                        // (8)
```

**Đáp án:** (1) đúng vì `Integer <: Number`; (2) đúng vì `Number` chứa được `Integer`; (3) sai vì `List<Integer>` không chứa được mọi `Number`; (4) sai vì `List<Number>` không bảo đảm chỉ có `Integer`; (5) đúng vì `Object` là supertype của `Number`; (6) đúng theo chiều cận dưới (`? super Number` sang `? super Integer`); (7) đúng vì cùng kiểu wildcard; (8) sai vì producer chưa chắc nhận được `Integer`.

**Tự trả lời thêm:** Một `List<? extends Number>` có đọc được thành `Object` không? **Có**: `Number` là subtype của `Object`. Một `List<? super Integer>` có gọi `add(null)` được không? **Có về mặt kiểu** (nếu danh sách cho phép sửa). Hai điều này không đảo ngược các quy tắc chính.

## 13. Checklist khi đọc lỗi generic của trình biên dịch

1. Xác định kiểu phần tử cơ sở `A`, `B` và quan hệ subtype thực sự (nếu có).
2. Xem đối tượng đang được **đọc**, **ghi**, hay cần cả hai.
3. Với `extends`, nhớ cận trên rộng hơn cho phép nhận nguồn có cận hẹp hơn.
4. Với `super`, nhớ cận dưới hẹp hơn cho phép nhận nguồn có cận rộng hơn.
5. Kiểm tra mỗi lớp generic lồng nhau; không bỏ qua invariance ở lớp ngoài.
6. Nếu phép gán vẫn sai, hỏi liệu cần một tham số `<T>` liên kết source/target, thay vì ép kiểu raw.
7. Sau khi biên dịch thành công, kiểm tra riêng khả năng thay đổi danh sách ở runtime.

## 14. Tài liệu đọc thêm

- Oracle Java Tutorials — [Wildcards and Subtyping](https://docs.oracle.com/javase/tutorial/java/generics/subtyping.html): sơ đồ quan hệ `extends` và `super`.
- Oracle Java Tutorials — [Guidelines for Wildcard Use](https://docs.oracle.com/javase/tutorial/java/generics/wildcardGuidelines.html): nguyên tắc producer/consumer.
- Java Language Specification (Java SE 17), [§4.5.1 Type Arguments of Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1): cú pháp wildcard và quy tắc containment.

**Điểm cần nắm:** Với `A <: B`, ta có `G<? extends A> <: G<? extends B>`, nhưng `G<? super B> <: G<? super A>`. Hai quy tắc này cho phép mô tả API đọc/ghi linh hoạt mà vẫn bảo toàn kiểm tra kiểu của Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-relations-008">Tách quan hệ kiểu con khỏi phép ép kiểu và chuyển đổi không kiểm tra</a>

<details>
<summary>Xem chi tiết</summary>

# Tách quan hệ kiểu con khỏi phép ép kiểu và chuyển đổi không kiểm tra trong Java Generics

## 1. Vì sao phải phân biệt ba khái niệm?

Khi đọc các đoạn code generics, rất dễ kết luận sai rằng “gán được” hoặc “ép kiểu được” đồng nghĩa với “là kiểu con”. Java sử dụng **ba cơ chế có ý nghĩa khác nhau**:

1. **Quan hệ kiểu con (subtyping):** một quan hệ giữa các *kiểu* do hệ thống kiểu định nghĩa, thường ký hiệu `S <: T` (“`S` là subtype của `T`”). Quan hệ này có thể giúp một biểu thức kiểu `S` được dùng ở vị trí chấp nhận `T`.
2. **Phép ép kiểu (cast):** cú pháp tường minh `(T) expression` yêu cầu chuyển giá trị biểu thức sang cách nhìn kiểu `T`. Phép cast có thể hợp lệ nhưng thất bại ở runtime; bản thân việc cast được **không chứng minh** `S <: T`.
3. **Chuyển đổi không kiểm tra (unchecked conversion):** Java cho phép một số chuyển đổi từ **raw type** sang **parameterized type** mà không thể chứng minh đầy đủ tham số kiểu. Chuyển đổi có thể xảy ra ngầm trong phép gán hoặc lời gọi phương thức và thường phát ra cảnh báo `[unchecked]`.

Ngoài ra còn có **unchecked cast**: phép cast *tường minh* đến kiểu parameterized mà runtime không thể kiểm tra đầy đủ type argument. “Unchecked conversion” và “unchecked cast” liên quan nhưng **không phải cùng một thao tác**.

Mục tiêu là nhìn một dòng Java và trả lời đúng: đang dựa vào subtyping, đang yêu cầu một cast có kiểm tra runtime, hay đang chấp nhận một thao tác không thể kiểm tra đầy đủ?

## 2. Bản đồ khái niệm

| Ví dụ | Phân loại chính | Compiler xử lý | Runtime có thể xác nhận gì? |
| --- | --- | --- | --- |
| `String text = "Java"; Object x = text;` | Subtyping `String <: Object` và widening reference conversion | Hợp lệ, không cảnh báo unchecked | Không cần kiểm tra downcast. |
| `List<String> a = new ArrayList<>(); Collection<String> b = a;` | Subtyping `List<String> <: Collection<String>` | Hợp lệ, không cảnh báo unchecked | Giữ nguyên tham chiếu. |
| `List<Integer> a = List.of(1); List<? extends Number> b = a;` | Subtyping thông qua wildcard/type-argument containment | Hợp lệ, không cảnh báo unchecked | Không cần kiểm tra type argument ở runtime. |
| `Object x = "Java"; String s = (String) x;` | Checked reference cast | Hợp lệ | Kiểm tra đối tượng thực sự là `String`. |
| `List raw = new ArrayList<Integer>(); List<String> b = raw;` | Unchecked conversion (gán ngầm) | Cảnh báo unchecked conversion | Không xác nhận phần tử là `String`. |
| `Object x = List.of(1); List<String> b = (List<String>) x;` | Unchecked cast (ép kiểu tường minh) | Cảnh báo unchecked cast | Có thể kiểm tra đối tượng là `List`, nhưng không kiểm tra `String` trong `List<String>`. |

**Đừng đồng nhất “có conversion” với “là subtype”.** Gán biến có thể được chấp nhận nhờ widening conversion hoặc, trong trường hợp đặc biệt của generics với raw type, unchecked conversion. Casting context lại cho phép thêm các phép chuyển đổi cần cast tường minh. Quan hệ subtype tự nó không thay đổi vì xuất hiện một cú pháp cast.

## 3. Subtyping với generic type: bất biến (invariance)

Trong Java, `Integer <: Number`, nhưng **`List<Integer>` không phải subtype của `List<Number>`**. Đây là tính bất biến của các type argument cụ thể trong một generic type như `List<E>`.

Ví dụ minh họa **không biên dịch**:

~~~java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = integers; // LỖI biên dịch: incompatible types
~~~

Tại sao phải cấm? Nếu gán được, người dùng biến `numbers` sẽ có thể gọi `numbers.add(3.14)` (một `Double`) và vô tình chèn `Double` vào danh sách ban đầu được khai báo `List<Integer>`. Hệ thống kiểu ngăn việc đó từ compile time.

### 3.1. Hai đường subtyping hợp lệ thường bị bỏ qua

**Thứ nhất, quan hệ kế thừa của *generic declaration* được giữ khi tham số kiểu tương thích:**

~~~java
ArrayList<String> arrayList = new ArrayList<>();
List<String> list = arrayList;
Collection<String> collection = list;
Iterable<String> iterable = collection;
~~~

`ArrayList<String> <: List<String> <: Collection<String> <: Iterable<String>` là những quan hệ hợp lệ. Không có unchecked warning và không có copy dữ liệu.

**Thứ hai, wildcard tạo một kiểu đích trừu tượng hơn:**

~~~java
List<Integer> integers = List.of(10, 20);
List<? extends Number> numericView = integers;
List<?> unknownView = integers;

Number first = numericView.get(0);
Object another = unknownView.get(1);
~~~

`List<Integer>` có thể được xem là `List<? extends Number>` và `List<?>` theo quy tắc type-argument containment của JLS. Đây là chuyển đổi an toàn dựa trên hệ thống kiểu, không phải unchecked conversion.

- `List<? extends Number>`: đọc được mỗi phần tử như `Number`, nhưng không được tùy tiện `add(Integer)` hay `add(Double)` vì loại phần tử cụ thể có thể là subtype khác của `Number`.
- `List<? super Integer>`: có thể `add(Integer)`; khi đọc chỉ bảo đảm kiểu `Object`.
- `List<?>`: có thể đọc dưới dạng `Object`; không thể thêm một giá trị khác `null` khi không biết kiểu phần tử.

Đây là nguyên tắc thường gọi là **PECS**: producer → `extends`, consumer → `super`. PECS giúp mô tả API mà không cần ép kiểu không an toàn.

### 3.2. Cần danh sách `Number` thật sự? Hãy tạo bản sao đúng kiểu

~~~java
List<Integer> integers = List.of(10, 20);
List<Number> numbers = new ArrayList<>(integers);
numbers.add(2.5); // Hợp lệ: numbers là một danh sách riêng, chứa Number
~~~

Constructor `ArrayList(Collection<? extends E>)` cho phép nhận một collection chứa phần tử subtype của `E`. Đoạn này **tạo danh sách mới**, không hề biến danh sách `List<Integer>` ban đầu thành subtype của `List<Number>`.

## 4. Cast có kiểm tra: hợp lệ về cú pháp không đồng nghĩa an toàn runtime

Ví dụ:

~~~java
Object value = "Java";
String text = (String) value; // Cast thành công

Object another = Integer.valueOf(42);
String wrong = (String) another; // ClassCastException tại dòng này
~~~

Với phép cast từ `Object` sang kiểu không parameterized như `String`, JVM kiểm tra loại đối tượng thực tế. Compiler chấp nhận cả hai cú pháp cast; thành bại phụ thuộc giá trị lúc chạy.

Các điểm cần nhớ:

- Một cast không tạo ra đối tượng mới và không chuyển một `Integer` thành `String`; nó chỉ yêu cầu tham chiếu thỏa loại đích.
- `(String) value` là cast có kiểm tra (checked reference cast). Khi đối tượng không phù hợp, runtime ném `ClassCastException`.
- Cast `null` sang một kiểu tham chiếu hợp lệ cho ra `null`, không ném `ClassCastException` tại bước cast; thao tác giải tham chiếu sau đó vẫn có thể ném `NullPointerException`.
- Không phải mọi cast đều được compiler cho phép. Ví dụ chuyển giữa hai kiểu không tương thích rõ ràng có thể bị báo lỗi ngay lúc biên dịch.

**Vì vậy, “compiler cho phép cast `(T) x`” không xác lập một quan hệ `type(x) <: T`**.

## 5. Unchecked conversion: chuyển đổi ngầm từ raw type

**Raw type** là dạng sử dụng tên generic mà không cung cấp type argument, ví dụ `List` thay vì `List<String>`. Java hỗ trợ raw type để tương thích chương trình/thư viện cũ, nhưng điều này làm mất thông tin về kiểu phần tử.

~~~java
List<Integer> integers = new ArrayList<>(List.of(10));
List raw = integers;             // Mất thông tin type argument ở biểu thức raw.
List<String> strings = raw;      // UNCHECKED CONVERSION, không cần viết cast.
~~~

Lệnh `javac -Xlint:unchecked` sẽ chỉ ra **unchecked conversion** tại phép gán cuối. Compiler không thể chứng minh `raw` chỉ chứa `String`, nhưng cho phép gán để hỗ trợ mã legacy.

Dòng `List raw = integers;` không đòi unchecked conversion: các parameterization của `List` có thể được sử dụng như raw `List`. Nguy cơ xuất hiện khi coi raw `List` là `List<String>`, hoặc khi ghi/đọc phần tử qua raw API.

Lời gọi phương thức cũng có thể phát sinh unchecked conversion nếu đối số raw được truyền vào tham số generic tương ứng:

~~~java
static void printStrings(List<String> items) {
    for (String item : items) {
        System.out.println(item);
    }
}

// Ở nơi gọi:
List raw = List.of(1, 2);
printStrings(raw); // Cảnh báo unchecked conversion; có thể lỗi khi sử dụng phần tử.
~~~

Đoạn thứ hai chỉ minh họa một lời gọi, không phải chương trình độc lập. Không nên “chữa” unchecked warning bằng cách bỏ luôn type argument hoặc sử dụng raw type ở toàn bộ hệ thống.

## 6. Unchecked cast: có toán tử `(T)` nhưng không kiểm tra đủ generic argument

Ví dụ:

~~~java
Object value = List.of(10, 20);
List<String> strings = (List<String>) value; // UNCHECKED CAST
~~~

`value` đang giữ một `List<Integer>`. Sau type erasure, runtime biết đây là một implementation của `List`, nhưng **không có bằng chứng runtime để xác nhận các phần tử là `String`**. Vì vậy phép cast có thể vượt qua bước kiểm tra runtime của kiểu `List`, còn lỗi `ClassCastException` xuất hiện khi đọc một phần tử như `String`.

Hai câu sau **khác nhau về cú pháp và loại cảnh báo**:

~~~java
List raw = List.of(1, 2);
List<String> viaConversion = raw;                 // Unchecked conversion
List<String> viaCast = (List<String>) (Object) raw; // Unchecked cast
~~~

Cast không “rửa” dữ liệu hay chứng minh một quan hệ subtyping mới. Nó có thể khiến lỗi khó phát hiện hơn vì mã tiếp tục chạy cho tới thời điểm đọc/ghi phần tử không phù hợp.

### 6.1. Type erasure và reifiable type

Trong Java generics, type arguments như `String` hoặc `Integer` thường bị xóa ở biểu diễn runtime của generic class. Hai đối tượng `new ArrayList<String>()` và `new ArrayList<Integer>()` cùng có lớp runtime `ArrayList`.

~~~java
Object unknown = List.of("a", "b");

boolean isList = unknown instanceof List<?>; // Hợp lệ.
List<?> view = (List<?>) unknown;             // Có thể kiểm tra List ở runtime.
~~~

Ngược lại, cú pháp sau **không hợp lệ**:

~~~java
// SAI: List<String> không phải kiểu reifiable dùng được cho instanceof.
if (unknown instanceof List<String>) {
    System.out.println("strings");
}
~~~

`List<?>` là reifiable theo quy định JLS, vì nó chỉ dùng wildcard không giới hạn; điều đó **không** bảo đảm mọi phần tử thuộc kiểu cụ thể nào. Nếu cần `List<String>` thật, hãy kiểm tra từng phần tử hoặc khởi tạo collection an toàn từ nguồn đã được định kiểu.

### 6.2. Heap pollution: nguyên nhân và thời điểm lỗi

**Heap pollution** xảy ra khi một biến khai báo kiểu parameterized tham chiếu đến dữ liệu thực tế không phù hợp với kiểu đó. Nó có thể được đưa vào qua raw type, unchecked conversion/cast, một số thao tác mảng có generic varargs hoặc cơ chế không an toàn khác.

~~~java
List<Integer> integers = new ArrayList<>(List.of(10));
List raw = integers;
List<String> strings = raw; // Unchecked warning; đã có nguy cơ heap pollution.
String first = strings.get(0); // ClassCastException ở bước dùng Integer như String.
~~~

Compiler chèn kiểm tra/cast cần thiết ở chỗ sử dụng kết quả `get(0)` để đáp ứng `String`. Việc thất bại ở đây không có nghĩa `List<Integer>` đã từng là subtype của `List<String>`. Ngược lại, unchecked conversion **bỏ qua được một phần bảo đảm tĩnh** và đẩy lỗi sang runtime.

Một unchecked warning không đồng nghĩa chắc chắn có lỗi; nếu dữ liệu raw tình cờ chứa đúng các giá trị cần thiết, chương trình có thể chạy. Nhưng compiler không thể chứng minh điều đó từ kiểu khai báo.

## 7. Ví dụ Java 17+ biên dịch và chạy được

Tạo tệp **GenericRelationsDemo.java** với nội dung sau:

~~~java
import java.util.ArrayList;
import java.util.List;

public class GenericRelationsDemo {
    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>(List.of(10, 20));
        List<? extends Number> readable = integers;
        List<?> anything = integers;
        System.out.println("subtype: " + readable.get(0));
        System.out.println("unknown: " + anything.get(1));

        List<Number> copied = new ArrayList<>(integers);
        copied.add(2.5);
        System.out.println("copy: " + copied);

        Object value = "Java";
        String text = (String) value;
        System.out.println("checked cast: " + text);

        try {
            String bad = (String) (Object) 42;
            System.out.println(bad);
        } catch (ClassCastException e) {
            System.out.println("checked cast: failed");
        }

        List raw = integers;
        List<String> assumedStrings = raw;
        try {
            System.out.println(assumedStrings.get(0).length());
        } catch (ClassCastException e) {
            System.out.println("unchecked conversion: failed on element read");
        }

        Object unknown = new ArrayList<>(List.of(1, 2));
        List<String> claimedStrings = (List<String>) unknown;
        try {
            System.out.println(claimedStrings.get(0).length());
        } catch (ClassCastException e) {
            System.out.println("unchecked cast: failed on element read");
        }
        System.out.println("reifiable test: " + (unknown instanceof List<?>));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -Xlint:unchecked GenericRelationsDemo.java
java GenericRelationsDemo
~~~

Lệnh `javac` **thành công** nhưng ghi hai cảnh báo vào stderr:

- `unchecked conversion` tại `List<String> assumedStrings = raw;`.
- `unchecked cast` tại `List<String> claimedStrings = (List<String>) unknown;`.

Bỏ qua vị trí đường dẫn/tên tệp trong stderr, hai cảnh báo này có nội dung khác nhau. Chương trình vẫn chạy và in ra stdout **chính xác**:

~~~text
subtype: 10
unknown: 20
copy: [10, 20, 2.5]
checked cast: Java
checked cast: failed
unchecked conversion: failed on element read
unchecked cast: failed on element read
reifiable test: true
~~~

Giải thích các điểm chính: hai phép gán sang wildcard được hệ thống kiểu chấp nhận an toàn; bản sao `List<Number>` có thể nhận `Double`; cast `Object` sang `String` được kiểm tra; hai phép thao tác generic mất an toàn không lỗi ngay khi gán/cast sang `List<String>`, mà lỗi khi runtime phải sử dụng phần tử `Integer` như `String`.

## 8. Thiết kế API tránh unchecked conversion và unchecked cast

### 8.1. Diễn đạt đúng nhu cầu bằng wildcard

Nếu chỉ đọc các số, dùng `List<? extends Number>` thay vì `List<Number>`:

~~~java
static double sum(List<? extends Number> values) {
    double result = 0.0;
    for (Number value : values) {
        result += value.doubleValue();
    }
    return result;
}

// sum(List.of(1, 2, 3)) hợp lệ với List<Integer>.
~~~

Nếu thêm các số nguyên vào bộ chứa, dùng `List<? super Integer>`:

~~~java
static void addDefaults(List<? super Integer> target) {
    target.add(10);
    target.add(20);
}
~~~

Có thể truyền `List<Integer>`, `List<Number>` hoặc `List<Object>` tới `addDefaults`. Đây là quan hệ kiểu hợp lệ, không yêu cầu unchecked cast.

### 8.2. Cần kiểm tra dữ liệu không rõ kiểu? Kiểm tra từng phần tử

~~~java
static List<String> requireStrings(List<?> source) {
    List<String> copy = new ArrayList<>();
    for (Object element : source) {
        copy.add(String.class.cast(element));
    }
    return copy;
}
~~~

Đây là ví dụ về *phương thức thành viên* (cần đặt trong lớp và import `List`, `ArrayList` để chạy độc lập). `String.class.cast(element)` kiểm tra từng phần tử thực tế. Nếu tất cả hợp lệ, kết quả là `List<String>` riêng biệt mà không cần unchecked cast. Nếu gặp phần tử không phải `String`, lỗi xuất hiện ngay tại vị trí kiểm tra, dễ chẩn đoán hơn.

Trong ứng dụng thực tế, có thể quyết định từ chối dữ liệu lỗi, thu thập danh sách lỗi, hoặc chuyển đổi kiểu có chủ đích; nhưng phải gọi đúng nó là **validation/conversion của dữ liệu**, không gọi là “ép kiểu generic an toàn” trên toàn collection.

### 8.3. Khi phải tương tác với API legacy/raw

Nếu nguồn chỉ cung cấp raw `List`, hãy cố gắng sửa chữ ký API thành generic hoặc tạo adapter kiểm tra dữ liệu. Trường hợp đã có bằng chứng bất biến từ hệ thống bên ngoài và buộc sử dụng unchecked cast, `@SuppressWarnings("unchecked")` chỉ nên đặt ở phạm vi nhỏ nhất cùng chú thích giải thích vì sao cast an toàn.

`@SuppressWarnings("unchecked")` chỉ **ẩn cảnh báo**, không thêm runtime check, không biến raw type thành parameterized subtype, và không ngăn `ClassCastException`.

## 9. Các kết luận sai thường gặp

| Kết luận sai | Điều đúng |
| --- | --- |
| “`Integer extends Number` nên `List<Integer> extends List<Number>`.” | Generic type arguments cụ thể là invariant; dùng wildcard hoặc tạo bản sao đúng kiểu. |
| “Có thể cast thì chắc chắn là subtype.” | Cast context cho phép narrowing conversion; loại thật có thể không phù hợp và ném `ClassCastException`. |
| “Cast `List<Integer>` thành `List<String>` sẽ đổi kiểu phần tử.” | Không có phép biến đổi phần tử nào xảy ra; erasure ngăn kiểm tra đầy đủ type arguments. |
| “Chỉ cast tường minh mới có `unchecked`.” | Gán một raw `List` cho `List<String>` cũng là unchecked conversion. |
| “`instanceof List<?>` chứng minh đây là `List<String>`.” | Chỉ chứng minh đối tượng thuộc một implementation của `List`, không chứng minh kiểu phần tử. |
| “Đã biên dịch được nghĩa là kiểu khai báo ở runtime luôn đúng.” | Unchecked conversion có thể dẫn tới heap pollution; lỗi có thể chỉ xuất hiện về sau khi dùng phần tử. |
| “Cứ dùng `@SuppressWarnings` là hết rủi ro.” | Annotation không thay đổi quy tắc type system hay hành vi runtime. |

## 10. Quy trình chẩn đoán khi gặp lỗi kiểu generic

1. **Viết rõ kiểu nguồn và kiểu đích** của biểu thức. Phân biệt `List<Integer>`, `List<Number>`, `List<?>` và raw `List`.
2. **Hỏi có subtype không** theo quan hệ kế thừa và wildcard containment; đừng tự suy ra từ subtype của type arguments.
3. **Nếu có `(TargetType) expr`**, xác định đây là cast tường minh, kiểm tra target có reifiable không và có cảnh báo unchecked không.
4. **Nếu không có cast mà vẫn xuất hiện `[unchecked]`**, tìm raw type trong phép gán, đối số phương thức hoặc thao tác tương tác thư viện cũ.
5. **Phân biệt thời điểm lỗi:** compile-time `incompatible types`; compile-time warning `unchecked`; runtime `ClassCastException` hoặc lỗi khác khi dùng giá trị.
6. **Chỉnh mô hình kiểu của API** (wildcard/generic type parameter, copy, validate) trước khi chọn cast/suppress warning.

### Tự kiểm tra

- `List<Integer>` có là subtype của `List<? extends Number>` không? **Có.**
- `List<Integer>` có là subtype của `List<Number>` không? **Không.**
- `Object obj; String s = (String) obj;` có phải unchecked cast không? **Không**, đây là cast có thể kiểm tra runtime.
- `List raw; List<String> strings = raw;` có cần toán tử cast mới phát cảnh báo không? **Không**, đây là unchecked conversion ngầm.
- `Object obj; List<String> xs = (List<String>) obj;` có bảo đảm các phần tử là `String` không? **Không**, vì cast không xác nhận type argument tại runtime.

## 11. Tài liệu chuẩn để tra cứu

- [JLS Java SE 17 §4.10.2 — Subtyping among Class and Interface Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.10.2): quan hệ subtype và các parameterized/wildcard types.
- [JLS Java SE 17 §4.5.1 — Type Arguments of Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1): wildcard, containment và upper/lower bounds.
- [JLS Java SE 17 §4.6–§4.8 — Type Erasure, Reifiable Types, Raw Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): bối cảnh của giới hạn kiểm tra ở runtime.
- [JLS Java SE 17 §4.12.2 — Heap Pollution](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.2): biến parameterized tham chiếu dữ liệu không phù hợp.
- [JLS Java SE 17 §5.1.6 — Narrowing Reference Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.6): kiểm tra runtime, checked và unchecked narrowing.
- [JLS Java SE 17 §5.1.9 — Unchecked Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.9): chuyển từ raw type sang parameterized type.
- [JLS Java SE 17 §5.5 — Casting Contexts](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.5): ngữ cảnh áp dụng cast.


</details>

- [Quay lại đầu trang](#back-to-top)
