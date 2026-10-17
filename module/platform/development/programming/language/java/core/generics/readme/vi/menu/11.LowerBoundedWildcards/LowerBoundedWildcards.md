<a id="back-to-top"></a>

# Wildcard super

## Menu
- [Ý nghĩa của ? super T](#lower-bounded-wildcards-001)
- [Nhận collection của T hoặc kiểu cha của T](#lower-bounded-wildcards-002)
- [Thêm T và các đối tượng kiểu con của T](#lower-bounded-wildcards-003)
- [Vì sao giá trị đọc ra chỉ được bảo đảm là Object](#lower-bounded-wildcards-004)
- [Phân biệt giới hạn dưới của wildcard với giới hạn của tham số kiểu](#lower-bounded-wildcards-005)
- [Quan hệ giữa ? super Integer và ? super Number](#lower-bounded-wildcards-006)
- [Áp dụng cho đầu ra nhận dữ liệu](#lower-bounded-wildcards-007)

## <a id="lower-bounded-wildcards-001">Ý nghĩa của ? super T</a>

<details>
<summary>Xem chi tiết</summary>

# Ý nghĩa của `? super T` trong Java Generics

## 1. `? super T` biểu diễn điều gì?

Trong Java Generics, **`? super T` là lower-bounded wildcard**, tức wildcard có **cận dưới**. Cú pháp này biểu diễn một đối số kiểu **chưa biết chính xác**, nhưng phải là **`T` hoặc một kiểu cha của `T`**. Kiểu cha ở đây được hiểu theo quan hệ kiểu con của Java, có thể là lớp cha hoặc interface mà `T` triển khai.

Ví dụ:

```java
List<? super Integer> destination;
```

Kiểu phần tử thực sự của danh sách có thể là `Integer`, `Number`, `Object`, hoặc một kiểu cha khác hợp lệ của `Integer`. Điều Java đảm bảo là: **một giá trị kiểu `Integer` có thể được đưa vào danh sách này một cách an toàn về kiểu**.

Hai thành phần của cú pháp có vai trò khác nhau:

- `?`: một đối số kiểu ẩn; mã nguồn tại vị trí này **không biết chính xác** danh sách khai báo `E` là kiểu nào.
- `super T`: ràng buộc rằng `T` là kiểu con hoặc chính là kiểu ẩn ấy; do đó một giá trị `T` có thể gán cho kiểu phần tử thực tế.

Có thể đọc `List<? super Integer>` như sau: “Một `List` có kiểu phần tử nào đó đủ rộng để **nhận giá trị `Integer`**”.

```java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();

List<? super Integer> a = integers; // Hợp lệ
List<? super Integer> b = numbers;  // Hợp lệ
List<? super Integer> c = objects;  // Hợp lệ
```

Các phép gán chỉ tạo **tham chiếu mới tới danh sách đã có**, không sao chép hoặc chuyển đổi phần tử. Nếu `a` trỏ tới `integers`, thay đổi phần tử thông qua `a` cũng được nhìn thấy qua `integers`.

Điều **không** được suy ra từ `? super Integer` là “đối số kiểu chắc chắn là `Number`” hoặc “danh sách chắc chắn là `List<Object>`”. Trong từng trường hợp, kiểu thực có thể khác nhau; việc compiler không biết nó dẫn tới những giới hạn cụ thể khi đọc.

## 2. Các danh sách nào tương thích với `? super T`?

Xem quan hệ kiểu:

```text
Object
  └── Number
       ├── Integer
       ├── Double
       └── Long
```

Với cận dưới `Integer`, các tham số kiểu `Integer`, `Number` và `Object` đều chấp nhận `Integer`. Ngược lại, `Double` là kiểu “ngang hàng” với `Integer`, không phải kiểu cha của `Integer`.

```java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();
List<Double> doubles = new ArrayList<>();

List<? super Integer> sink;

sink = integers; // Hợp lệ
sink = numbers;  // Hợp lệ
sink = objects;  // Hợp lệ
// sink = doubles; // Lỗi biên dịch
```

| Danh sách truyền vào | `List<? super Integer>` nhận được? | Vì sao? |
|---|---|---|
| `List<Integer>` | Có | `Integer` chính là cận dưới |
| `List<Number>` | Có | `Number` là kiểu cha của `Integer` |
| `List<Object>` | Có | `Object` là kiểu cha của `Integer` |
| `List<Double>` | Không | `Double` không phải kiểu cha của `Integer` |
| `List<String>` | Không | `String` không phải kiểu cha của `Integer` |
| `List<? extends Number>` | Không đảm bảo | Kiểu ẩn có thể là `Double`, không nhận được `Integer` |
| `List<?>` | Không đảm bảo | Chưa biết kiểu ẩn có nhận được `Integer` hay không |

Cột cuối của hai dòng wildcard nói về việc **không thể dựa vào kiểu tĩnh đó để gán trực tiếp** vào biến `List<? super Integer>`. Một đối tượng đứng phía sau biến có thể tình cờ có kiểu phù hợp, nhưng compiler không được phép dựa trên phỏng đoán ấy.

Đối với một `T` tổng quát, quy tắc giống hệt: nếu `T` là `Dog` thì các tham số kiểu `Dog`, `Animal`, `Object` có thể là những đích nhận `Dog`, giả sử `Dog extends Animal`. Chữ `super` trong wildcard biểu thị **hướng đi về phía kiểu cha**.

## 3. Vì sao có thể thêm `T` và các kiểu con của `T`?

Một `List<? super Integer>` có thể là `List<Integer>`, `List<Number>` hoặc `List<Object>`. **Cả ba đều chứa được `Integer`**, do đó compiler cho phép:

```java
List<Number> numbers = new ArrayList<>();
List<? super Integer> destination = numbers;

destination.add(10);
destination.add(Integer.valueOf(20));
destination.set(0, 15);

System.out.println(numbers); // [15, 20]
```

Các thao tác `add` và `set` trên đây an toàn về **kiểu phần tử**. Nếu một kiểu `T` có lớp con `S`, giá trị `S` cũng có thể được thêm vào `List<? super T>` nhờ quan hệ gán `S` → `T` → kiểu cha chưa biết. Ví dụ `? super Number` nhận được các giá trị `Integer` và `Double`, bởi cả hai đều là kiểu con của `Number`.

```java
List<Object> objects = new ArrayList<>();
List<? super Number> sink = objects;

sink.add(Integer.valueOf(7));
sink.add(Double.valueOf(2.5));
sink.add((Number) Long.valueOf(9L));

System.out.println(objects); // [7, 2.5, 9]
```

Tuy vậy:

```java
List<? super Integer> sink = new ArrayList<Number>();

// sink.add(2.5);        // Không biên dịch: Double không phải Integer
// sink.add(new Object()); // Không biên dịch: Object không được đảm bảo phù hợp
sink.add(42);             // Hợp lệ
sink.add(null);           // Hợp lệ về kiểm tra kiểu
```

Vì danh sách thực tế có thể là `List<Integer>`, thêm `Double` hoặc một `Object` tùy ý sẽ không an toàn. Một biến `List<? super Integer>` **không có quyền thêm mọi thứ chỉ vì trong một trường hợp cụ thể nó trỏ tới `List<Object>`**. Compiler đánh giá dựa trên hợp đồng kiểu tĩnh.

`null` là ngoại lệ đặc biệt về type checking, vì nó có thể gán cho mọi kiểu tham chiếu. Việc thêm null có thành công khi chạy hay không còn phụ thuộc implementation (khả năng sửa danh sách và chính sách cho phép null).

## 4. Tại sao đọc chỉ đảm bảo `Object`?

Với `List<? super Integer>`, ta **không biết** `E` thực sự là `Integer`, `Number` hay `Object`. Giả sử danh sách là `List<Object>`: nó có thể đã chứa cả chuỗi lẫn số trước khi được đưa vào phương thức. Ràng buộc cận dưới không đảm bảo các giá trị sẵn có đều là `Integer`.

```java
List<Object> mixed = new ArrayList<>();
mixed.add("existing text");
mixed.add(100);

List<? super Integer> sink = mixed;
sink.add(42);

Object first = sink.get(0);  // Hợp lệ
Object third = sink.get(2);  // Hợp lệ

// Integer number = sink.get(2); // Lỗi: get(2) chỉ đảm bảo Object
// Number value = sink.get(2);   // Cũng lỗi: có thể là Object khác Number
```

Dù phần tử ở vị trí 2 trên thực tế là `Integer`, compiler không dựa vào vị trí hay dữ liệu hiện có để thay đổi kiểu trả về của `get`.

Ví dụ hoạt động ở runtime:

```java
Object value = sink.get(2);
if (value instanceof Integer number) {
    System.out.println(number + 1); // 43
}
```

`instanceof` kiểm tra **riêng giá trị được lấy ra**. Nó không biến toàn bộ biến `sink` thành `List<Integer>` và không cam kết rằng các phần tử khác đều là `Integer`.

Sự bất đối xứng đọc/ghi là điểm cốt lõi:

- **Ghi:** có thể cung cấp một `Integer` vì mọi kiểu `E` phù hợp với `? super Integer` đều nhận được `Integer`.
- **Đọc:** chỉ có thể gán an toàn cho `Object` vì không biết giá trị gốc trong `List<E>` có phải `Integer` hay không.

Không được hiểu `? super T` thành “mọi phần tử hiện có là kiểu cha của `T`”. Wildcard ràng buộc **đối số kiểu của cấu trúc**, không ràng buộc từng giá trị runtime theo hướng “lớn hơn” nào đó.

## 5. Ví dụ Java 17 hoàn chỉnh: một API nhận nhiều loại danh sách đích

Tạo tệp `SuperWildcardDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class SuperWildcardDemo {
    static void addDefaults(List<? super Integer> destination) {
        destination.add(10);
        destination.add(20);
    }

    static <T> void appendAll(
            List<? extends T> source,
            List<? super T> destination) {
        for (T element : source) {
            destination.add(element);
        }
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>();
        List<Number> numbers = new ArrayList<>();
        List<Object> objects = new ArrayList<>();

        addDefaults(integers);
        addDefaults(numbers);
        addDefaults(objects);

        System.out.println("Integers: " + integers);
        System.out.println("Numbers: " + numbers);
        System.out.println("Objects: " + objects);

        List<? super Integer> receiver = numbers;
        receiver.set(0, 99);
        Object first = receiver.get(0);
        System.out.println("First as Object: " + first);

        List<Integer> more = List.of(3, 4);
        appendAll(more, numbers);
        System.out.println("After appendAll: " + numbers);

        List<? super Number> numberReceiver = objects;
        numberReceiver.add(1.5);
        System.out.println("Objects with double: " + objects);
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac SuperWildcardDemo.java
java SuperWildcardDemo
```

**Stdout chính xác:**

```text
Integers: [10, 20]
Numbers: [10, 20]
Objects: [10, 20]
First as Object: 99
After appendAll: [99, 20, 3, 4]
Objects with double: [10, 20, 1.5]
```

Vì sao ví dụ biên dịch và chạy được?

1. `addDefaults` nhận cả `List<Integer>`, `List<Number>` lẫn `List<Object>`, vì cả ba danh sách có thể nhận `Integer`.
2. `receiver.set(0, 99)` hợp lệ: `set` ghi một `Integer` vào một danh sách có kiểu ẩn là `Integer` hoặc kiểu cha của nó. `receiver.get(0)` chỉ có kiểu tĩnh an toàn là `Object`.
3. `appendAll` dùng một tham số kiểu `T` kết nối nguồn và đích. Ở lời gọi `appendAll(more, numbers)`, `T` có thể được suy luận sao cho nguồn `List<Integer>` và đích `List<Number>` đều phù hợp.
4. `numberReceiver` có cận dưới `Number`, vì vậy thêm `Double` an toàn về kiểu ngay cả khi biến đang tham chiếu `List<Object>`.

Các danh sách đều được tạo bằng `ArrayList` có thể sửa đổi, nên `add` và `set` thành công. Lệnh in danh sách dựa vào `toString()` của `ArrayList` và các phần tử số nguyên/`Double`, nên stdout đúng như trên.

## 6. PECS: “Consumer Super”

Quy tắc **PECS** là “**Producer Extends, Consumer Super**”. Đây là mẹo ghi nhớ khi thiết kế API với wildcard:

- Một **producer** tạo/cung cấp giá trị cho thuật toán, nên dùng `? extends T` để cho phép **đọc** giá trị dưới kiểu `T`.
- Một **consumer** nhận giá trị từ thuật toán, nên dùng `? super T` để cho phép **ghi** giá trị kiểu `T` vào đích.

Ví dụ khái quát:

```java
static <T> void appendAll(
        List<? extends T> source,
        List<? super T> destination) {
    for (T element : source) {
        destination.add(element);
    }
}
```

Trong vòng lặp, `source` sản xuất ra giá trị gán được cho `T`; `destination` nhận những giá trị `T` đó. Phương thức cho phép các lời gọi:

```java
List<Integer> ints = List.of(1, 2);
List<Number> nums = new ArrayList<>();
List<Object> objs = new ArrayList<>();

appendAll(ints, nums); // Hợp lệ
appendAll(ints, objs); // Hợp lệ
appendAll(nums, objs); // Hợp lệ
```

Không có phép ép kiểu không kiểm tra và cũng không cần dùng raw `List`. Nếu thay tham số đích bằng `List<T>`, API bị bó hẹp một cách không cần thiết đối với nhiều lời gọi trong đó kiểu phần tử của đích là kiểu cha của `T`.

**PECS nói về vai trò của tham số trong thuật toán**, không có nghĩa `? super T` tuyệt đối “chỉ ghi”: vẫn đọc được dưới kiểu `Object`, đếm phần tử, xóa phần tử khi implementation hỗ trợ. Đồng thời `? extends T` không có nghĩa cấu trúc immutable; nó chỉ hạn chế việc tự ý thêm giá trị có kiểu cụ thể vào một kiểu ẩn.

## 7. Khi nào nên dùng `List<? super T>` thay cho `List<T>`?

So sánh hai chữ ký:

```java
static void addCountStrict(List<Integer> values) {
    values.add(1);
}

static void addCountFlexible(List<? super Integer> values) {
    values.add(1);
}
```

`addCountStrict` chỉ nhận `List<Integer>`. `addCountFlexible` còn nhận `List<Number>` và `List<Object>`. Nếu bên trong phương thức chỉ cần **đưa một `Integer` vào danh sách**, chữ ký thứ hai mô tả đúng quyền cần có và linh hoạt hơn.

Nhưng nếu thuật toán cần **đọc kết quả từ chính danh sách với kiểu `Integer`**, `List<? super Integer>` không bảo đảm điều đó:

```java
static Integer getFirstStrict(List<Integer> values) {
    return values.get(0);
}

static Object getFirstFlexible(List<? super Integer> values) {
    return values.get(0);
}
```

Đoạn minh họa trên giả định danh sách không rỗng; nếu rỗng, `get(0)` ném `IndexOutOfBoundsException` bất kể tham số generic là gì.

Điều cần hỏi khi thiết kế chữ ký là **“Phương thức có cần biết chính xác kiểu các phần tử đang đọc, hay chỉ cần đưa giá trị `T` vào danh sách?”**. Khi tác vụ chính là ghi `T`, `super` thường là lựa chọn phù hợp.

## 8. Dùng `? super T` với interface hoặc lớp con

`super` không đòi hỏi cận dưới là lớp có quan hệ `extends` trực tiếp. Nó cũng hỗ trợ quan hệ kiểu liên quan tới interface.

Giả sử một lớp tự định nghĩa:

```java
interface Event {
    String label();
}

record LoginEvent(String user) implements Event {
    public String label() {
        return "Login " + user;
    }
}
```

Đây là **đoạn khai báo minh họa**, chưa phải chương trình độc lập. Một `List<? super LoginEvent>` có thể tham chiếu `List<LoginEvent>`, `List<Event>` hoặc `List<Object>`; vào đó có thể thêm đối tượng `LoginEvent`:

```java
List<Event> log = new ArrayList<>();
List<? super LoginEvent> sink = log;

sink.add(new LoginEvent("Lan")); // Hợp lệ
Object entry = sink.get(0);      // Đọc an toàn dưới Object
```

Mặc dù biến `sink` trỏ tới `List<Event>` trong ví dụ, compiler vẫn chỉ cho phép `get` trả về thông tin an toàn chung là `Object` ở biến có kiểu wildcard cận dưới.

Khi cận dưới là một kiểu có lớp con, ta cũng có thể thêm các đối tượng thuộc kiểu con đó. Ví dụ `List<? super Number>` cho phép thêm `Integer` và `Double` vì chúng đều là `Number`.

## 9. Wildcard cận dưới có thay đổi khả năng sửa đổi danh sách không?

Không. Generics kiểm soát **kiểu giá trị**, còn danh sách cụ thể kiểm soát **khả năng mutation** và các hạn chế runtime khác.

```java
List<Number> mutable = new ArrayList<>();
List<? super Integer> a = mutable;
a.add(1);       // Được compiler chấp nhận và ArrayList cho phép
a.add(null);    // Được compiler chấp nhận; ArrayList cho phép null
```

Trái lại:

```java
List<Integer> immutable = List.of(1, 2);
List<? super Integer> b = immutable;

// b.add(3); // Hợp lệ về kiểu nhưng khi chạy sẽ ném UnsupportedOperationException
```

Ở đây `List.of(...)` trả về danh sách không sửa đổi được. Nếu bỏ comment `b.add(3)`, chương trình biên dịch nhưng ném `UnsupportedOperationException` tại runtime.

`List<? super Integer>` cũng cho phép gọi các phương thức không phụ thuộc vào việc thêm một phần tử cụ thể như `clear()`, `remove(Object)`, `remove(int)`, `size()`, `contains(Object)`. `remove(0)` chọn overload nhận **chỉ số `int`**, không phải overload xóa một đối tượng `Integer.valueOf(0)`. Các thao tác xóa và sửa đổi vẫn chịu quy tắc runtime của implementation.

Vì thế, đừng dùng `? super T` như cơ chế bảo vệ dữ liệu khỏi thay đổi; nếu cần hợp đồng không sửa đổi, hãy cấp một cấu trúc không sửa đổi được hoặc bản sao phù hợp.

## 10. Quan hệ giữa `? super T`, `? extends T`, `?` và kiểu cụ thể

Bảng sau dùng `T = Number`:

| Kiểu khai báo | Các tham số kiểu danh sách được chấp nhận | Kiểu đọc an toàn từ `get` | Thêm một `Number` tùy ý? |
|---|---|---|---|
| `List<Number>` | Chính xác `Number` | `Number` | Có |
| `List<? super Number>` | `Number` hoặc các kiểu cha như `Object` | `Object` | Có |
| `List<? extends Number>` | `Number` hoặc các kiểu con như `Integer`, `Double` | `Number` | Không |
| `List<?>` | Bất kỳ kiểu phần tử nào | `Object` | Không |
| Raw `List` | Bỏ thông tin đối số kiểu | `Object` | Có thể, nhưng không an toàn; có cảnh báo unchecked |

Ở hàng `List<?>` và `List<? extends Number>`, `add(null)` vẫn là ngoại lệ hợp kiểu. Ở các hàng có thể thêm giá trị, ta vẫn phải xem implementation có cho phép sửa danh sách hay không.

Các nhận xét cần phân biệt:

- `List<Number>` **không** phải là kiểu cha của `List<Integer>` dù `Number` là kiểu cha của `Integer`; Generic của Java là bất biến (*invariant*).
- `List<? super Integer>` **nhận được** `List<Number>`; đó là tính linh hoạt của wildcard theo cận dưới, không phải là phép kế thừa giữa `List<Integer>` và `List<Number>`.
- `? super Number` khác với `? extends Number`: thứ nhất thể hiện khả năng **nhận giá trị `Number`**, thứ hai thể hiện khả năng **cung cấp giá trị đọc được như `Number`**.

## 11. Ví dụ ngoài `List`: `Comparator<? super T>`

Nguyên lý “đưa giá trị `T` cho một consumer có kiểu cha” còn xuất hiện trong các API thư viện Java.

`List<E>.sort` nhận `Comparator<? super E>`. Một comparator biết so sánh `Number` có thể dùng để sắp xếp danh sách `Integer`, bởi comparator ấy **nhận được** các giá trị `Integer` dưới dạng `Number`:

```java
List<Integer> values = new ArrayList<>(List.of(30, 2, 11));
Comparator<Number> byNumericValue =
        Comparator.comparingDouble(Number::doubleValue);

values.sort(byNumericValue);
System.out.println(values); // [2, 11, 30]
```

Khi đưa đoạn này vào lớp Java, cần import `java.util.ArrayList`, `java.util.List` và `java.util.Comparator`. Đây là đoạn minh họa, không phải một `main` độc lập.

Nếu `sort` đòi hỏi `Comparator<Integer>` chính xác, một comparator tổng quát nhận `Number` sẽ không được sử dụng trực tiếp. Cận dưới `? super E` cho phép API dùng một consumer nhận kiểu cha, đúng theo hướng PECS.

## 12. Lưu ý với `Collections.copy` và việc “ghi vào đích”

Một API thư viện khác biểu diễn rất rõ vai trò nguồn/đích:

```java
static <T> void copy(
        List<? super T> dest,
        List<? extends T> src)
```

Đây là **chữ ký rút gọn** của `java.util.Collections.copy`, không phải một phương thức tự định nghĩa để đặt cạnh tên này trong chương trình. `src` là nguồn có cận trên vì ta đọc `T` từ nó; `dest` có cận dưới vì ta ghi `T` vào đó.

Khác với `appendAll` ở phần trước, `Collections.copy` **thay thế** các phần tử tương ứng trong đích. Danh sách đích phải có kích thước **ít nhất bằng** danh sách nguồn; nó không tự mở rộng đích bằng cách gọi `add`.

Ví dụ Java 17:

```java
List<Integer> source = List.of(7, 8);
List<Number> destination =
        new ArrayList<>(List.of(0, 0, 0));

Collections.copy(destination, source);
System.out.println(destination); // [7, 8, 0]
```

Cần import `java.util.Collections`, `java.util.List` và `java.util.ArrayList`. Nếu `destination` rỗng, lệnh `Collections.copy(destination, source)` sẽ ném `IndexOutOfBoundsException`; nếu đích không sửa đổi được, có thể gặp `UnsupportedOperationException`. Đây là ràng buộc hành vi của API, độc lập với việc kiểu generic đã phù hợp.

## 13. Không phải khai báo nào có `super` cũng là syntax hợp lệ

`super` trong cú pháp đang học là giới hạn **lower bound cho wildcard** ở vị trí đối số kiểu:

```java
List<? super Integer> sink;
Consumer<? super Integer> handler;
Comparator<? super Integer> comparator;
```

Nhưng Java **không** cho phép khai báo type parameter có tên bằng cú pháp `<T super Integer>`:

```java
// class Box<T super Integer> { } // Lỗi cú pháp Java
// static <T super Number> void f() { } // Lỗi cú pháp Java
// List<super Integer> list; // Lỗi: thiếu dấu ? của wildcard
```

Type parameter có tên như `<T extends Number>` sử dụng ràng buộc cận trên. Khi muốn thể hiện một **consumer** của `T` tại vị trí tham số kiểu, ta viết `? super T` trong một kiểu generic, chẳng hạn `List<? super T>`.

Ngoài ra, `? super T` không phải cú pháp dùng trong phép tạo đối tượng:

```java
// List<? super Integer> bad = new ArrayList<? super Integer>(); // Lỗi
List<? super Integer> good = new ArrayList<Number>();          // Đúng
```

Vế tạo đối tượng sử dụng đối số kiểu cụ thể (`Number`) hoặc dấu diamond `<>` để compiler suy luận. Wildcard ở vế khai báo mô tả cách nhìn và quyền thao tác qua biến `good`.

## 14. Những lỗi thường gặp khi học `? super T`

**Lỗi 1: “`? super Integer` nghĩa là mỗi phần tử được đọc ra là `Integer` hoặc kiểu cha.”** Sai. Ràng buộc áp dụng cho đối số kiểu của danh sách. Nếu đối số kiểu thực là `Object`, danh sách có thể chứa `String` từ trước. Khi đọc, chỉ chắc chắn nhận được `Object`.

**Lỗi 2: “Đích `List<? super Integer>` nhận được `Double` vì `Double` và `Integer` cùng là `Number`.”** Sai. `List<? super Integer>` có thể thực chất là `List<Integer>`; chỉ bảo đảm chấp nhận `Integer` và các kiểu con của `Integer` (nếu tồn tại).

**Lỗi 3: “`List<Integer>` truyền được cho `List<Number>` vì `Integer extends Number`.”** Sai. Generics bất biến. Muốn thiết kế hàm nhận danh sách để thêm `Integer`, có thể dùng `List<? super Integer>`.

**Lỗi 4: “Dấu `super` cho biết danh sách bắt buộc có kiểu phần tử chính xác là lớp cha `Number`.”** Sai. Kiểu phần tử có thể là chính `Integer`, một lớp cha hoặc interface cha phù hợp; wildcard không nêu chính xác kiểu đó.

**Lỗi 5: “Nếu `get` chỉ trả về `Object` thì không nên dùng `? super T`.”** Sai khi API là consumer. Chữ ký consumer ưu tiên khả năng ghi `T` an toàn; nếu cần đọc ra `T`, hãy xem lại hợp đồng hoặc dùng một kiểu generic được đặt tên phù hợp.

**Lỗi 6: “`? super T` đảm bảo mọi lời gọi `add` thành công.”** Sai. `add(T)` hợp lệ về kiểu nhưng vẫn có thể ném `UnsupportedOperationException` hoặc lỗi khác do đối tượng danh sách quyết định.

**Lỗi 7: “`Collections.copy` hoạt động giống vòng lặp `add`.”** Sai. `Collections.copy` thay thế phần tử đã có, yêu cầu đích đủ lớn; vòng lặp `add` nối phần tử vào cuối (khi implementation hỗ trợ).

**Lỗi 8: “Dùng raw `List` là cách tránh giới hạn của wildcard.”** Dùng raw type bỏ đi kiểm tra generic và có thể gây *heap pollution*, rồi lỗi `ClassCastException` ở đoạn mã khác. Nên chọn hợp đồng wildcard đúng, không tắt an toàn kiểu.

## 15. Bài tự kiểm tra

Cho đoạn mã:

```java
List<Integer> integers = new ArrayList<>(List.of(1, 2));
List<Number> numbers = new ArrayList<>(List.of(5, 6));
List<Object> objects = new ArrayList<>(List.of("before"));
List<? super Integer> target = numbers;
```

Trả lời trước khi xem đáp án:

1. Có thể gán `target = integers` và `target = objects` không? `target = new ArrayList<Double>()` thì sao?
2. `target.add(7)` và `target.add(null)` có biên dịch không?
3. `target.add(7.5)` có biên dịch không, dù ngay lúc đầu `target` trỏ tới `List<Number>`?
4. Có thể viết `Integer x = target.get(0)` hoặc `Number y = target.get(0)` mà không ép kiểu không?
5. `target.set(0, 99)` có biên dịch không? Nó sửa phần tử ở đâu?
6. `List<? super Number> numberSink = objects;` có biên dịch không? Có thể gọi `numberSink.add(3.14)` không?
7. Nếu `target` tham chiếu `List.of(1, 2)` qua một biến kiểu hợp lệ, `target.add(3)` có được runtime chấp nhận không?

**Đáp án:**

1. Cả `List<Integer>` và `List<Object>` đều hợp lệ. `List<Double>` không phù hợp vì `Double` không phải kiểu cha của `Integer`.
2. Cả hai lời gọi đều hợp lệ khi biên dịch. Đối tượng thực phải hỗ trợ thêm phần tử và có thể có chính sách null riêng.
3. Không. `Double` không phải `Integer`. Compiler dựa vào kiểu tĩnh `List<? super Integer>` thay vì kiểu thực tình cờ là `List<Number>`.
4. Không. Với `get(0)`, kiểu tĩnh an toàn nhất là `Object`. Phép gán cho `Number` hoặc `Integer` cần thêm bằng chứng/kiểm tra phù hợp.
5. Có. `set` được phép nhận `Integer`, sẽ thay phần tử ở chỉ số 0 của đối tượng danh sách mà `target` đang tham chiếu; ở tình huống ban đầu đó là `numbers`.
6. Có. `List<Object>` nhận được `Number` và các kiểu con; `3.14` được boxing thành `Double` là một `Number`.
7. Không. Lời gọi hợp lệ về kiểu nhưng danh sách tạo bởi `List.of` không cho sửa, nên ném `UnsupportedOperationException`.

## 16. Quy tắc ghi nhớ

**`? super T` = “một kiểu chưa biết nhưng đủ rộng để nhận `T`”.** Đây là một ràng buộc của đối số kiểu, giúp API chấp nhận đích kiểu `T` hoặc kiểu cha của `T` và vẫn kiểm tra giá trị ghi vào ở compile time. Đọc phần tử của đích chỉ chắc chắn dưới kiểu `Object` vì dữ liệu đã có có thể thuộc một kiểu rộng hơn `T`.

Khi một tham số đóng vai trò **nơi nhận giá trị `T`**, cân nhắc `? super T`. Khi nó đóng vai trò **nguồn cung cấp giá trị `T`**, cân nhắc `? extends T`. Khi nhiều vị trí cần liên hệ với cùng một kiểu `T`, kết hợp wildcard với type parameter có tên theo nguyên tắc PECS.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lower-bounded-wildcards-002">Nhận collection của T hoặc kiểu cha của T</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận collection của T hoặc kiểu cha của T với `? super T`

Khi một phương thức cần **đưa các giá trị kiểu `T` vào một collection do bên gọi cung cấp**, collection đích không nhất thiết phải khai báo chính xác `Collection<T>`. Nó có thể lưu các phần tử dưới một kiểu rộng hơn, chẳng hạn `Number` hoặc `Object` đối với `Integer`. Java Generics biểu diễn nhu cầu này bằng **lower-bounded wildcard**:

~~~java
Collection<? super Integer> destination;
~~~

Kiểu trên chấp nhận các collection có đối số kiểu là **`Integer` hoặc một kiểu cha của `Integer`**, chẳng hạn `Collection<Integer>`, `Collection<Number>` và `Collection<Object>`. Phương thức có thể thêm `Integer` vào tất cả các collection đó một cách an toàn.

Để hiểu đúng quy tắc, hãy phân biệt **kiểu phần tử của collection thực**, **kiểu tĩnh của tham chiếu wildcard**, và **hướng mà dữ liệu di chuyển**. Cũng cần nhớ rằng `Collection` chỉ có `iterator()` để lấy phần tử; nó không cung cấp `get(index)` như `List`.

## 1. Vì sao cần nhận collection rộng hơn một `Collection<T>` cố định?

Một `Collection<Integer>` **không phải** `Collection<Number>`, dù `Integer` là subtype của `Number`. Nếu có thể gán trực tiếp, phương thức nhận `Collection<Number>` sẽ thêm được `Double` vào cấu trúc mà caller coi là chỉ chứa `Integer`.

~~~java
Collection<Integer> integers = new ArrayList<>();
// Collection<Number> wrong = integers; // Bị compiler từ chối.

Collection<Number> numbers = new ArrayList<>();
Collection<Object> objects = new ArrayList<>();
numbers.add(10);
objects.add(10);
~~~

Tuy không thể hoán đổi các kiểu collection bất biến, **cả hai đích đều nhận được `Integer`**. Một API chỉ cần chèn `Integer` nên mô tả *khả năng nhận giá trị*, thay vì đòi đúng một đối số kiểu: `Collection<? super Integer>`. Đây là lý do thực tế để dùng wildcard cận dưới với **giao diện `Collection`**, không chỉ với `List`.

## 2. `? super T` nghĩa là gì?

Trong `Collection<? super T>`:

- `Collection` là giao diện collection tổng quát trong Java.
- `?` là wildcard đại diện cho một kiểu cụ thể **chưa biết tên**.
- `super T` đặt **cận dưới** (lower bound): kiểu ẩn đó phải là `T` hoặc một **supertype** của `T`.
- Vì collection thực có thể lưu `T` dưới chính kiểu `T` hoặc kiểu rộng hơn, việc thêm một giá trị `T` vào nó là an toàn.

Ví dụ với `T = Integer`:

| Kiểu thực của collection | Gán vào `Collection<? super Integer>`? | Lý do |
| --- | --- | --- |
| `Collection<Integer>` | Có | Đối số kiểu đúng bằng cận dưới |
| `Collection<Number>` | Có | `Number` là supertype của `Integer` |
| `Collection<Object>` | Có | `Object` là supertype của `Integer` |
| `Collection<Double>` | Không | `Double` không phải supertype của `Integer` |
| `Collection<String>` | Không | `String` không phải supertype của `Integer` |

Ngoài chuỗi kế thừa lớp `Integer → Number → Object`, một supertype phù hợp cũng có thể là **interface** mà `Integer` triển khai, ví dụ `Serializable`. Vì vậy `Collection<? super Integer>` không chỉ bị giới hạn ở ba kiểu trong bảng: bảng giới thiệu các trường hợp thường gặp.

Có thể hiểu tương đương ở mức ý nghĩa:

> “Tôi không cần biết chính xác collection lưu đối số kiểu nào, miễn là đối số kiểu đó chấp nhận mọi `Integer`.”

Lưu ý: `? super T` dùng trong wildcard. Java **không** cho phép khai báo kiểu tham số bằng cú pháp `<T super Number>`. Khi cần ràng buộc type parameter, cú pháp thông thường là upper bound, ví dụ `<T extends Number>`; cận dưới được diễn đạt tại vị trí wildcard.

## 3. Ví dụ Java 17+: một phương thức thêm số vào ba loại collection

Tạo tệp **`SuperWildcardDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Collection;

public class SuperWildcardDemo {
    private static void addDefaults(Collection<? super Integer> destination) {
        destination.add(10);
        destination.add(20);
    }

    public static void main(String[] args) {
        Collection<Integer> integers = new ArrayList<>();
        Collection<Number> numbers = new ArrayList<>();
        Collection<Object> objects = new ArrayList<>();

        addDefaults(integers);
        addDefaults(numbers);
        addDefaults(objects);

        Collection<? super Integer> sink = numbers;
        sink.add(30);

        Object first = sink.iterator().next();

        System.out.println("integers = " + integers);
        System.out.println("numbers = " + numbers);
        System.out.println("objects = " + objects);
        System.out.println("first = " + first);
    }
}
~~~

Biên dịch và chạy trên Java 17 trở lên:

~~~bash
javac SuperWildcardDemo.java
java SuperWildcardDemo
~~~

Stdout chính xác:

~~~text
integers = [10, 20]
numbers = [10, 20, 30]
objects = [10, 20]
first = 10
~~~

Ở ví dụ này:

1. `addDefaults` chỉ quan tâm việc **ghi hai `Integer` vào collection**. Nó không cần biết đối số kiểu chính xác của đích.
2. Cả `Collection<Integer>`, `Collection<Number>` và `Collection<Object>` đều phù hợp với `Collection<? super Integer>`.
3. `sink` là tham chiếu tới cùng đối tượng với `numbers`; `sink.add(30)` thay đổi chính collection `numbers`.
4. `sink.iterator().next()` chỉ có kiểu tĩnh chắc chắn là `Object`. Mặc dù phần tử đầu trong ví dụ là `Integer`, compiler không thể dựa vào giả định ấy cho mọi collection phù hợp wildcard.
5. Chương trình dùng `ArrayList` để thứ tự in phần tử ổn định; `Collection` nói chung không hứa thứ tự duyệt giống thứ tự thêm.

Điểm quan trọng nhất là chữ ký `Collection<? super Integer>` **mở rộng loại đích có thể nhận** mà không làm mất kiểm tra kiểu của các phần tử được thêm.

## 4. Kiểu của giá trị đọc qua `Collection<? super T>`

Một consumer chỉ cam kết **nhận được `T`**, không cam kết mọi phần tử hiện có đều thuộc `T`. Ví dụ `Collection<Object>` có thể chứa `String` trước khi được chuyển qua tham chiếu `Collection<? super Integer>`:

~~~java
Collection<Object> original = new ArrayList<>();
original.add("existing");
Collection<? super Integer> sink = original;
sink.add(7);

Object first = sink.iterator().next(); // "existing"
// Integer wrong = sink.iterator().next(); // Không biên dịch.
~~~

Dùng `iterator().next()`, **không phải `get(0)`**, bởi `Collection` không định nghĩa truy cập chỉ số. Cận dưới chỉ cho phép `add(Integer)`; khi duyệt, kiểu an toàn chung của `Collection<E>` là `Object`. Nếu cần đọc lại thành `Integer`, phải kiểm chứng giá trị cụ thể hoặc chọn một nguồn typed khác. Phân tích hình thức về capture conversion và các trường hợp generic declaration có bound hẹp hơn thuộc chủ đề riêng về **giới hạn kiểu khi đọc**.

## 5. Chọn chữ ký `Collection` theo hướng dữ liệu

| Yêu cầu trong method (giả sử `T=Number`) | Tham số phù hợp | Phần tử đọc an toàn | Giá trị có thể thêm về mặt kiểu |
| --- | --- | --- | --- |
| Chỉ nhận đúng kiểu collection chứa `Number` | `Collection<Number>` | `Number` | `Number` |
| Đọc `Number` từ nhiều collection con | `Collection<? extends Number>` | `Number` | Không tùy ý thêm giá trị non-null |
| Thêm `Number` vào collection của `Number` hoặc kiểu cha | `Collection<? super Number>` | `Object` | `Number` và subtype |
| Không quan tâm element type | `Collection<?>` | `Object` | Không tùy ý thêm giá trị non-null |

`? extends` thiên về **nguồn**, `? super` thiên về **đích**. Với `Collection`, việc đọc thực hiện qua `iterator()` hoặc foreach; muốn `get(i)`/`set(i,...)` thì chuyển sang `List`. Các quy tắc wildcard chỉ bảo đảm **kiểu tĩnh**; `add` có thể thất bại trên unmodifiable collection, hoặc `Set.add` có thể trả `false` khi phần tử đã tồn tại.

Không nên đổi `Collection<? super T>` thành `Collection<Object>`: biến `Collection<Object>` **không** nhận trực tiếp `Collection<Integer>`, dù cả hai có thể tiêu thụ những `Integer` do method cung cấp.

## 6. Áp dụng khi `T` là type parameter của phương thức generic

Một phương thức có thể nhận `T` bất kỳ và thêm vào đích kiểu `T` hoặc supertype của `T`:

~~~java
static <T> void addTwo(Collection<? super T> destination, T a, T b) {
    destination.add(a);
    destination.add(b);
}
~~~

Đây là **một phương thức, cần đặt trong class và import `java.util.Collection`**. Khai báo `<T>` cho compiler suy luận kiểu từ toàn bộ các tham số trong **từng lời gọi**.

Ví dụ khi `T` được chọn là `Integer`:

~~~java
Collection<Number> numbers = new ArrayList<>();
Collection<Object> objects = new ArrayList<>();

addTwo(numbers, 1, 2); // Hợp lệ.
addTwo(objects, 3, 4); // Hợp lệ.
~~~

Đây là **đoạn lời gọi cần đặt trong class và import `java.util.*`**. Trong mỗi lời gọi, `T` có thể được suy luận thành `Integer`, trong khi collection đích lưu phần tử dưới kiểu `Number` hoặc `Object`.

Để thấy rõ ràng buộc, có thể chỉ định type argument tường minh:

~~~java
MyUtils.<Integer>addTwo(numbers, 1, 2); // Đúng.
MyUtils.<Integer>addTwo(objects, 3, 4); // Đúng.
~~~

Đây là ký pháp minh họa: giả sử `addTwo` là `static` trong class `MyUtils`. Với `T = Integer`, `Collection<? super T>` yêu cầu đích có đối số kiểu là `Integer` hoặc một supertype của nó.

### Điểm tinh tế: đừng gán cố định `T` khi compiler cần suy luận

Nếu `T` là một type parameter của **phương thức**, compiler có thể chọn kiểu thích hợp cho mỗi lần gọi. Nếu `T` là type parameter của **class** hoặc đã được chọn rõ là một kiểu cụ thể, các tham số phải tuân theo kiểu đã chọn.

Chẳng hạn:

~~~java
class IntegerAppender {
    void append(Collection<? super Integer> destination) {
        destination.add(1);
    }
}
~~~

Đây là **class minh họa**, cần import `java.util.Collection`. Phương thức `append` cố định cận dưới là `Integer`. Nó nhận `Collection<Integer>`, `Collection<Number>` hoặc `Collection<Object>`, nhưng không nhận `Collection<Double>`. Bản thân tên kiểu generic và cách `T` được suy luận không tự làm mất các ràng buộc subtype.

## 7. Ví dụ với class và interface tự định nghĩa

`super` không chỉ áp dụng cho ba lớp `Integer`, `Number` và `Object`. Giả sử mô hình nghiệp vụ có `Animal` và `Dog`, trong đó `Dog` là subtype của `Animal`. Một collection lưu `Animal` hay `Object` đều chứa được `Dog`.

Tạo tệp **`AnimalDestinationDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Collection;

public class AnimalDestinationDemo {
    static class Animal {
        private final String name;

        Animal(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static class Dog extends Animal {
        Dog(String name) {
            super(name);
        }
    }

    static void addDogs(Collection<? super Dog> destination) {
        destination.add(new Dog("Bim"));
        destination.add(new Dog("Milo"));
    }

    public static void main(String[] args) {
        Collection<Dog> dogs = new ArrayList<>();
        Collection<Animal> animals = new ArrayList<>();
        Collection<Object> objects = new ArrayList<>();

        addDogs(dogs);
        addDogs(animals);
        addDogs(objects);

        System.out.println("dogs = " + dogs);
        System.out.println("animals = " + animals);
        System.out.println("objects = " + objects);
    }
}
~~~

Chạy:

~~~bash
javac AnimalDestinationDemo.java
java AnimalDestinationDemo
~~~

Stdout chính xác:

~~~text
dogs = [Bim, Milo]
animals = [Bim, Milo]
objects = [Bim, Milo]
~~~

Giải thích:

- `Collection<Dog>` chấp nhận các `Dog` trực tiếp.
- `Collection<Animal>` chấp nhận `Dog` vì `Dog` mở rộng `Animal`.
- `Collection<Object>` chấp nhận `Dog` vì mọi đối tượng `Dog` đều là `Object`.
- Không có unchecked cast. Mỗi collection vẫn giữ đối số kiểu khai báo ban đầu.
- Mỗi lệnh `addDogs` tạo hai đối tượng `Dog` mới trong collection tương ứng; không có việc ba collection cùng chia sẻ cấu trúc danh sách.

Nếu có subtype `Poodle extends Dog`, biểu thức tạo `Poodle` cũng được chèn qua `Collection<? super Dog>` vì mọi `Poodle` đều là `Dog`. Nhưng không được chèn tùy tiện một đối tượng `Animal` không phải `Dog`, bởi đích có thể thực sự là `Collection<Dog>`.

## 8. Sao chép từ collection kiểu con vào collection kiểu cha

Một nhu cầu quen thuộc là **đọc dữ liệu từ nguồn và ghi vào đích**. Ví dụ, nguồn có `Integer`, còn đích lưu `Number`; hoặc nguồn có `Dog`, còn đích lưu `Animal`.

Có thể khai báo phương thức:

~~~java
static <T> void appendAll(Collection<? extends T> source,
                          Collection<? super T> destination) {
    for (T element : source) {
        destination.add(element);
    }
}
~~~

Đây là **phương thức cần đặt trong class và import `java.util.Collection`**.

- `source` là **producer**: phần tử của nó có kiểu `T` hoặc subtype của `T`; đọc thành `T` hợp lệ.
- `destination` là **consumer**: collection đó có đối số kiểu `T` hoặc supertype của `T`; thêm phần tử `T` hợp lệ.

Tạo chương trình **`CollectionTransferDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CollectionTransferDemo {
    private static <T> void appendAll(
            Collection<? extends T> source,
            Collection<? super T> destination) {
        for (T value : source) {
            destination.add(value);
        }
    }

    public static void main(String[] args) {
        Collection<Integer> small = List.of(1, 2);
        Collection<Double> decimals = List.of(3.5, 4.5);

        Collection<Number> numbers = new ArrayList<>();
        Collection<Object> objects = new ArrayList<>();

        appendAll(small, numbers);
        appendAll(decimals, numbers);
        appendAll(numbers, objects);

        System.out.println("numbers = " + numbers);
        System.out.println("objects = " + objects);
        System.out.println("small = " + small);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CollectionTransferDemo.java
java CollectionTransferDemo
~~~

Stdout:

~~~text
numbers = [1, 2, 3.5, 4.5]
objects = [1, 2, 3.5, 4.5]
small = [1, 2]
~~~

Ví dụ chứng minh rằng wildcard `super` **đưa phần tử vào đích bằng thao tác hợp lệ**. Nó không biến `Collection<Integer>` thành `Collection<Number>` và không cần ép kiểu.

Thư viện chuẩn đã hỗ trợ mẫu này. Chẳng hạn, `Collection.addAll(Collection<? extends E>)` cho phép thêm các phần tử từ nguồn có subtype phù hợp; còn tiện ích `Collections.addAll(Collection<? super T>, T...)` cho phép thêm nhiều đối tượng kiểu `T` vào một đích có đối số kiểu `T` hoặc supertype của nó.

Đối với `List`, `Collections.copy(List<? super T> dest, List<? extends T> src)` cũng thể hiện PECS, nhưng khác `appendAll`: nó **ghi đè các vị trí sẵn có** và yêu cầu `dest` đã đủ kích thước. Không dùng `Collections.copy` như một cách tự động nối vào cuối danh sách.

## 9. Các thao tác được phép và rủi ro khi đọc

Xét một biến:

~~~java
Collection<? super Integer> destination = new ArrayList<Number>();
~~~

Những thao tác sau hợp lệ về mặt generic:

~~~java
destination.add(10);
destination.add(Integer.valueOf(20));
destination.addAll(List.of(30, 40));
destination.add(null); // Biên dịch, nhưng đích thật có thể cấm null.

boolean hasTen = destination.contains(10);
int size = destination.size();

for (Object item : destination) {
    System.out.println(item);
}

destination.remove(10);
destination.clear();
~~~

Đây là **đoạn minh họa cần import `java.util.*` và đặt trong class**. `Collection.addAll` nhận nguồn có kiểu phần tử phù hợp; `contains` và `remove(Object)` không đòi xác định kiểu phần tử của collection. Vì vậy compiler cho phép.

Ngược lại:

~~~java
// destination.add(2.5); // LỖI: Double không phải Integer.
// destination.add("x"); // LỖI: String không phải Integer.

// Integer first = destination.iterator().next(); // LỖI.
// Number first = destination.iterator().next();  // LỖI.
Object first = destination.iterator().next(); // Hợp lệ nếu không rỗng.
~~~

`iterator().next()` có thể ném `NoSuchElementException` nếu collection rỗng; câu nhận xét “hợp lệ” ở trên chỉ nói **biên dịch và kiểu trả về**, không cam kết collection có phần tử. Khi cần thao tác an toàn, hãy kiểm tra `iterator.hasNext()` trước.

Ngoài ra, `? super T` **không phải phép bảo đảm collection mutable**. Ví dụ `List.of(1, 2)` có kiểu `List<Integer>` và gán được vào `Collection<? super Integer>`, nhưng thêm phần tử sẽ ném `UnsupportedOperationException`. Khả năng thêm về **kiểu** và khả năng sửa của **đối tượng thực** là hai điều độc lập.

### Vì sao kết quả đọc có thể không phải `Integer`?

Chương trình dưới đây chạy được:

~~~java
import java.util.ArrayList;
import java.util.Collection;

public class SuperReadDemo {
    public static void main(String[] args) {
        Collection<Object> objects = new ArrayList<>();
        objects.add("text");
        objects.add(99);

        Collection<? super Integer> sink = objects;
        sink.add(100);

        for (Object item : sink) {
            System.out.println(item);
        }
    }
}
~~~

Chạy `javac SuperReadDemo.java` và `java SuperReadDemo` cho stdout:

~~~text
text
99
100
~~~

Tham chiếu `sink` được phép thêm `Integer` vào `Collection<Object>`, nhưng những phần tử tồn tại trước đó vẫn có thể là `String`. Bởi vậy, đọc qua `sink` dưới kiểu `Integer` sẽ là một lời hứa không được bảo đảm.

## 10. So với `List<? super T>`: cùng quan hệ kiểu, khác API container

Bài học này sử dụng `Collection<? super T>` để nói về **collection nói chung**. Nếu API cần truy cập vị trí bằng chỉ số, có thể áp dụng cùng wildcard với `List<? super T>`:

~~~java
List<? super Integer> destination = new ArrayList<Number>();
destination.add(1);
destination.add(2);

Object first = destination.get(0); // Hợp lệ.
// Integer n = destination.get(0); // LỖI BIÊN DỊCH.
~~~

Đây là **đoạn minh họa cần import `java.util.*` và đặt trong class**. Khác biệt giữa `Collection` và `List` nằm ở phương thức giao diện: `List` có `get(index)`, `set(index, element)`; `Collection` không có `get(index)`.

Không nhầm kiểu wildcard với tên implementation:

- `Collection<? super Integer>` **có thể trỏ tới `ArrayList<Integer>`, `HashSet<Number>` hoặc collection khác** nếu đối số kiểu phù hợp.
- `List<? super Integer>` chỉ trỏ được tới đối tượng thực hiện `List` với đối số kiểu phù hợp, không trỏ trực tiếp tới một `HashSet`.

Cần thêm phần tử nhưng không quan tâm thứ tự/chỉ số: ưu tiên tham số `Collection<? super T>` để API chấp nhận nhiều implementation. Cần chức năng chỉ có ở `List`: dùng `List<? super T>`. Một collection kiểu `Set` có thể từ chối thêm một phần tử vì đã tồn tại, nên `add` trả `false`; đây là hợp đồng `Collection` chứ không phải lỗi wildcard.

## 11. Những lỗi phổ biến cần tránh

### Lỗi 1. Dùng `Collection<T>` dù chỉ cần thêm `T`

~~~java
static void addAnswer(Collection<Integer> destination) {
    destination.add(42);
}
~~~

Đây là **phương thức minh họa**. Nếu caller có `Collection<Number>`, phương thức từ chối đầu vào dù collection đó có thể lưu `Integer`. Nếu phương thức chỉ thêm số nguyên, chữ ký phù hợp hơn là:

~~~java
static void addAnswer(Collection<? super Integer> destination) {
    destination.add(42);
}
~~~

### Lỗi 2. Nhầm `super` với việc cho đọc mọi phần tử như `T`

`Collection<? super Integer>` có thể tham chiếu tới `Collection<Object>`. Các phần tử cũ của collection này có thể là bất kỳ `Object` nào, nên một vòng lặp `for (Integer x : collection)` **không biên dịch**. Dùng `Object` để đọc, hoặc thiết kế lại API nếu thực sự cần thông tin kiểu mạnh hơn.

### Lỗi 3. Tưởng `? super T` cho thêm cả mọi supertype của `T`

Khi `T = Integer`, `Double` và `Number` **không** phải các kiểu giá trị được bảo đảm thêm vào `Collection<? super Integer>`. Đích có thể là `Collection<Integer>`. Chỉ thêm các giá trị tương thích `Integer` (bao gồm `null` nếu collection hỗ trợ) là hợp lệ.

### Lỗi 4. Nhầm tính linh hoạt của wildcard với phép gán subtype generic

~~~java
Collection<Integer> xs = new ArrayList<>();
Collection<? super Integer> allowed = xs; // Có.
// Collection<Number> wrong = xs;          // Không.
~~~

`allowed` chỉ là **một góc nhìn dùng wildcard**. Nó không làm đối số kiểu của `xs` đổi thành `Number`.

### Lỗi 5. Ép kiểu unchecked để đạt cùng mục tiêu

~~~java
Collection<Integer> integers = new ArrayList<>();
@SuppressWarnings("unchecked")
Collection<Number> forged = (Collection<Number>) (Collection<?>) integers;
forged.add(2.5); // Có thể làm ô nhiễm dữ liệu kiểu Integer.
~~~

Đây là **mã cảnh báo, không phải giải pháp**. Ép kiểu unchecked có thể khiến một collection chứa các phần tử không đúng hợp đồng và gây `ClassCastException` tại thao tác đọc trong tương lai. Trong API chỉ cần chèn số nguyên, `Collection<? super Integer>` diễn đạt nhu cầu thật mà không cần cast.

### Lỗi 6. Dùng `? extends T` cho đối tượng chỉ làm đích nhận

~~~java
static void badSink(Collection<? extends Number> values) {
    // values.add(123); // LỖI BIÊN DỊCH.
}
~~~

Trong trường hợp này `extends` thiết kế cho **nguồn đọc**, không bảo đảm có thể thêm `Integer`. Nếu chỉ cần chèn `Integer`, chọn `Collection<? super Integer>`; nếu cần chèn mọi `Number`, chọn `Collection<? super Number>`.

### Lỗi 7. Bỏ qua giới hạn của container thực

Một lời gọi `destination.add(1)` có thể **biên dịch** nhưng vẫn không thành công vì collection không cho sửa, không nhận `null` hoặc có quy tắc lọc trùng. Generics bảo đảm **tương thích kiểu**, không bảo đảm mọi lệnh sửa sẽ thành công. Đọc tài liệu của implementation khi hành vi runtime quan trọng.

## 12. Tự kiểm tra

Giả sử các biến sau được khai báo bên trong một phương thức, với `import java.util.*;`:

~~~java
Collection<Integer> ints = new ArrayList<>();
Collection<Number> numbers = new ArrayList<>();
Collection<Object> objects = new ArrayList<>();
Collection<Double> doubles = new ArrayList<>();
Collection<? super Integer> sink = numbers;
~~~

Hãy dự đoán những lệnh sau **biên dịch được không**:

| Biểu thức độc lập | Biên dịch? | Giải thích |
| --- | --- | --- |
| `Collection<? super Integer> a = ints;` | Có | Đối số kiểu là `Integer` |
| `Collection<? super Integer> a = numbers;` | Có | `Number` là supertype của `Integer` |
| `Collection<? super Integer> a = objects;` | Có | `Object` là supertype của `Integer` |
| `Collection<? super Integer> a = doubles;` | Không | `Double` không phải supertype của `Integer` |
| `sink.add(123);` | Có | Autoboxing số nguyên thành `Integer` |
| `sink.add(2.5);` | Không | `Double` không được bảo đảm phù hợp |
| `sink.add((Number) 123);` | Không | Kiểu tĩnh của biểu thức là `Number`, có thể không phải `Integer` |
| `Object x = sink.iterator().next();` | Có | Kiểu bảo đảm khi đọc là `Object` |
| `Integer x = sink.iterator().next();` | Không | Không biết đối số kiểu thực của collection |
| `sink.addAll(List.of(1, 2));` | Có | Collection nguồn cung cấp `Integer` |
| `sink.size();` | Có | Không phụ thuộc kiểu phần tử |
| `sink.clear();` | Có | Hợp lệ về kiểu, runtime tùy khả năng sửa |

Các lệnh trong bảng là **các tình huống độc lập**, không được gom toàn bộ vào một phương thức biên dịch thật vì có dòng cố ý sai kiểu và tên biến `a` lặp lại.

Một câu hỏi tư duy: nếu `Collection<Object> objects` đang chứa chuỗi `"hello"`, rồi được dùng như `Collection<? super Integer>`, tại sao `add(7)` được phép trong khi `Integer i = iterator().next()` bị cấm? Vì **thêm `Integer`** là an toàn cho mọi đích có kiểu cha phù hợp, nhưng **đọc mọi phần tử thành `Integer`** là sai với những giá trị cũ.

## 13. Áp dụng quy tắc trong thiết kế API

Chọn tham số generic theo nhu cầu thao tác cụ thể:

1. **Chỉ cần thêm các giá trị `T` vào collection của caller:** chọn `Collection<? super T>`. Đây là trường hợp chính của bài.
2. **Chỉ cần đọc các giá trị như `T` từ collection của caller:** chọn `Collection<? extends T>`.
3. **Đồng thời đọc và ghi chính xác cùng một kiểu `T`, cần duy trì quan hệ kiểu giữa nhiều tham số/giá trị trả về:** cân nhắc `Collection<T>` hoặc một generic method có type parameter rõ ràng.
4. **Không quan tâm kiểu phần tử và không cần thêm phần tử cụ thể:** `Collection<?>` phù hợp để đếm, kiểm tra trạng thái hoặc duyệt như `Object`.
5. **Cần duy trì thứ tự và thao tác bằng chỉ số:** chọn `List` thay vì `Collection`, nhưng giữ nguyên nguyên tắc `? super T` khi nó là đích tiêu thụ.

**Kết luận:** `Collection<? super T>` có nghĩa “tôi có thể nhận một collection của `T` hoặc một kiểu cha của `T` để **ghi các giá trị `T` một cách an toàn**”. Đây là mảnh ghép **Consumer Super** của nguyên tắc PECS. Đổi lại, không được giả định phần tử đọc ra có kiểu `T`; mức bảo đảm chung là `Object`. Chữ ký đúng giúp API linh hoạt với caller mà vẫn giữ được hàng rào kiểm tra kiểu của Java Generics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lower-bounded-wildcards-003">Thêm T và các đối tượng kiểu con của T</a>

<details>
<summary>Xem chi tiết</summary>

# Thêm T và các đối tượng kiểu con của T vào wildcard `? super T`

## 1. Mục tiêu

Trong Java Generics, `? super T` là **wildcard có cận dưới**. Nếu một phương thức nhận `List<? super T>`, nó có thể **thêm phần tử kiểu `T` và mọi kiểu con của `T`** vào danh sách, miễn là danh sách cho phép thay đổi ở runtime.

Ví dụ, `List<? super Number>` có thể nhận `Number`, `Integer`, `Double` và các lớp con khác của `Number`. Nhưng điều đó **không** có nghĩa tham chiếu này nhận được một `Object` bất kỳ: `Object` là **kiểu cha** của `Number`, không phải kiểu con.

Bài học giải thích tại sao quy tắc này an toàn, vai trò của **kiểu tĩnh** khi biên dịch, cách viết hàm nhận dữ liệu (consumer), giới hạn khi đọc và những lỗi thường gặp.

Sau bài học, bạn có thể:
- Xác định chính xác kiểu nào được truyền vào `add` trên `Collection<? super T>`.
- Chứng minh bằng quan hệ subtype vì sao thêm `T` hoặc subtype của `T` là hợp lệ.
- Phân biệt `List<? super T>`, `List<T>`, `List<? extends T>` và `List<?>` tại điểm ghi dữ liệu.
- Dùng generic method để chuyển dữ liệu từ nguồn kiểu con vào đích có kiểu rộng hơn.
- Phân biệt lỗi compile-time với lỗi danh sách không cho phép sửa ở runtime.

## 2. Phạm vi kiểu mà `List<? super T>` cho phép ghi

Xét `List<? super Number>`: kiểu phần tử ẩn có thể là `Number` hoặc một supertype của `Number`, ví dụ `Object`. Do đó:

~~~java
List<? super Number> sink = new ArrayList<Object>();
sink.add(3.5);      // Double <: Number: đúng.
sink.add(10);       // Integer <: Number: đúng.
// sink.add("text"); // String không phải Number: lỗi biên dịch.
~~~

Không thể gán `List<Integer>` vào `List<? super Number>`, vì `List<Integer>` không chấp nhận mọi `Number` (ví dụ `Double`). Đặc điểm riêng cần phân tích ở bài này là **loại của biểu thức được thêm**: cả `T` và subtype `S <: T` đều phù hợp, nhưng một biểu thức mang kiểu tĩnh *cha hơn T* không được tự ý thêm, ngay cả khi object runtime thực chất là subtype của `T`.

## 3. Chứng minh quy tắc thêm `T` và subtype của `T`

Đặt:

- `E`: kiểu phần tử thực tế của collection, nhưng chưa biết chính xác.
- `T`: cận dưới trong `Collection<? super T>`.
- `S`: kiểu của một giá trị muốn thêm.

Định nghĩa `? super T` bảo đảm `T` là subtype của `E`: `T <: E`.

Nếu `S` là `T` hoặc subtype của `T`, thì `S <: T`.

Theo tính bắc cầu của quan hệ subtype:

```text
S <: T <: E

S là subtype của T
T là subtype của kiểu phần tử thực tế E
=> S là subtype của E
=> Có thể thêm giá trị kiểu S vào Collection<E>
```

Vì lý do này, trình biên dịch cho phép cả `add(valueOfT)` và `add(valueOfSubtypeOfT)`.

### 3.1. Ví dụ bằng `Number`

```java
List<? super Number> sink = new ArrayList<Object>();

sink.add(Integer.valueOf(10)); // Integer <: Number: hợp lệ
sink.add(Double.valueOf(2.5)); // Double <: Number: hợp lệ
sink.add(Long.valueOf(7L));    // Long <: Number: hợp lệ
sink.add((Number) 8);          // Number: hợp lệ
sink.add(null);                // null: hợp lệ về kiểm tra kiểu

// sink.add(new Object());     // SAI: Object không phải subtype của Number.
// sink.add("hello");          // SAI: String không phải subtype của Number.
```

Đây là đoạn mã **minh họa**, cần đặt trong một phương thức cùng `import java.util.*;` nếu muốn biên dịch. Đối tượng thực tế là `ArrayList<Object>`, nhưng API của biến `sink` chỉ bảo đảm phép thêm `Number` và subtype.

### 3.2. Phép boxing không làm thay đổi ý nghĩa cận dưới

```java
List<? super Number> sink = new ArrayList<Number>();
sink.add(10);     // int được boxing thành Integer, rồi upcast về Number.
sink.add(3.14);   // double được boxing thành Double, rồi upcast về Number.
```

Trong code trên, `10` và `3.14` ban đầu là primitive. Trình biên dịch có thể áp dụng **autoboxing** để biến chúng thành `Integer` và `Double`, vốn là subtype của `Number`. Đừng nhầm đây là bằng chứng rằng primitive `int` hay `double` là subtype của `Number`: quan hệ subtype ở đây là giữa các **kiểu tham chiếu** sau boxing.

## 4. Ví dụ với một cây kế thừa nhiều cấp

Giả sử có:

```text
Object
  └── Animal
        ├── Bird
        └── Mammal
              ├── Dog
              └── Cat
```

Với biến `List<? super Mammal>`, kiểu phần tử thực tế có thể là `Mammal`, `Animal` hoặc `Object`. Cả ba kiểu đó đều chứa được `Mammal`, `Dog` và `Cat`.

```java
List<? super Mammal> sink = new ArrayList<Animal>();

sink.add(new Mammal("Mia")); // ĐÚNG: chính Mammal.
sink.add(new Dog("Rex"));    // ĐÚNG: Dog <: Mammal.
sink.add(new Cat("Luna"));   // ĐÚNG: Cat <: Mammal.

// sink.add(new Animal("Any")); // SAI: Animal là supertype của Mammal.
// sink.add(new Bird("Sky"));   // SAI: Bird không phải subtype của Mammal.
// sink.add(new Object());      // SAI: Object không phải subtype của Mammal.
```

Các class trên được định nghĩa đầy đủ trong chương trình chạy ở phần 7. Đoạn này chỉ diễn tả kiểm tra kiểu. Quy tắc không phụ thuộc lớp có tên gì: nếu `S <: T` thì một giá trị có kiểu tĩnh `S` có thể truyền cho `add` của `List<? super T>`.

**Chú ý về “các đối tượng kiểu con”:** tham chiếu có thể chỉ tới đối tượng runtime là `Dog`, nhưng nếu biến được khai báo kiểu `Animal`, trình biên dịch xử lý nó như `Animal` và chưa cho thêm vào `List<? super Mammal>`. Phần 6 phân tích trường hợp này.

## 5. Ba kiểu collection thực tế có thể làm đích ghi

Với `T = Mammal`, các kiểu đích sau đều được nhận:

```java
List<Mammal> mammals = new ArrayList<>();
List<Animal> animals = new ArrayList<>();
List<Object> objects = new ArrayList<>();

List<? super Mammal> first = mammals; // E = Mammal.
List<? super Mammal> second = animals; // E = Animal.
List<? super Mammal> third = objects; // E = Object.

first.add(new Dog("D1"));
second.add(new Dog("D2"));
third.add(new Dog("D3"));
```

Điều đáng chú ý là **kiểu thực tế `E` có thể khác nhau**, nhưng mọi kiểu trong ba trường hợp đều đủ rộng để nhận `Mammal` và subtype của `Mammal`.

Không thể suy ngược rằng `List<Dog>` cũng hợp lệ:

```java
List<Dog> dogs = new ArrayList<>();
// List<? super Mammal> wrong = dogs; // LỖI BIÊN DỊCH
```

Nếu cho phép gán như thế, `wrong.add(new Cat("Luna"))` sẽ vi phạm lời hứa rằng danh sách gốc chỉ chứa `Dog`.

**Quy tắc chọn biến đích:** các `List<T>`, `List<SupertypeOfT>` có thể gán cho `List<? super T>`; `List<SubtypeOfT>` thì không.

## 6. Kiểu tĩnh quyết định phép `add`

Trình biên dịch Java kiểm tra việc thêm dựa trên **kiểu khai báo của biểu thức**, không dựa trên subclass mà đối tượng tình cờ mang ở runtime.

Giả sử `Dog extends Mammal extends Animal`:

```java
List<? super Mammal> sink = new ArrayList<Object>();

Dog dog = new Dog("Rex");
Mammal mammal = dog;
Animal animal = dog;
Object object = dog;

sink.add(dog);    // ĐÚNG: kiểu tĩnh Dog <: Mammal.
sink.add(mammal); // ĐÚNG: kiểu tĩnh Mammal.

// sink.add(animal); // SAI: kiểu tĩnh Animal, dù runtime là Dog.
// sink.add(object); // SAI: kiểu tĩnh Object, dù runtime là Dog.
```

Điều này bảo vệ chương trình. Nếu `Animal animal` được chấp nhận chỉ nhờ đối tượng hiện tại là `Dog`, cùng một dòng code sẽ không an toàn khi biến `animal` trỏ đến `Bird`.

Nếu chương trình thực sự cần thu hẹp kiểu từ `Animal` xuống `Mammal`, phải xác minh runtime:

```java
Animal candidate = new Dog("Max");
if (candidate instanceof Mammal mammalValue) {
    sink.add(mammalValue); // ĐÚNG: mammalValue có kiểu tĩnh Mammal.
}
```

`instanceof` với pattern variable được hỗ trợ chính thức từ Java 16, do đó chạy được trên Java 17+. Không nên ép kiểu mù quáng `(Mammal) candidate` vì với `Bird` sẽ ném `ClassCastException`.

### 6.1. Trường hợp subtype được suy luận trong một phương thức

```java
static <T> void appendOne(List<? super T> target, T value) {
    target.add(value);
}
```

Vì tham số `target` bảo đảm chứa được `T`, phương thức có thể thêm chính `value`. Ví dụ `appendOne(new ArrayList<Animal>(), new Dog("Rocky"))` là hợp lệ; Java có thể suy luận type argument đáp ứng cả nguồn `Dog` và đích `Animal`.

Không cần ép kiểu raw `List` để khiến lời gọi biên dịch.

## 7. Chương trình Java 17+ đầy đủ

Lưu thành file **`SuperWildcardInsertDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SuperWildcardInsertDemo {
    static class Animal {
        private final String name;

        Animal(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + name + ")";
        }
    }

    static class Mammal extends Animal {
        Mammal(String name) {
            super(name);
        }
    }

    static final class Dog extends Mammal {
        Dog(String name) {
            super(name);
        }
    }

    static final class Cat extends Mammal {
        Cat(String name) {
            super(name);
        }
    }

    static final class Bird extends Animal {
        Bird(String name) {
            super(name);
        }
    }

    static <T> void appendAll(
            Collection<? super T> destination,
            Iterable<? extends T> source) {
        for (T value : source) {
            destination.add(value);
        }
    }

    public static void main(String[] args) {
        List<Animal> animals = new ArrayList<>();
        List<? super Mammal> sink = animals;

        sink.add(new Mammal("Mia"));
        sink.add(new Dog("Rex"));
        sink.add(new Cat("Luna"));

        Mammal asMammal = new Dog("Max");
        sink.add(asMammal);

        List<Mammal> more = List.of(
                new Dog("Bolt"),
                new Cat("Coco")
        );
        appendAll(animals, more);

        Object first = sink.get(0);
        System.out.println("animals=" + animals);
        System.out.println("first=" + first);

        List<Object> mixed = new ArrayList<>();
        mixed.add("existing");
        List<? super Mammal> anotherSink = mixed;
        anotherSink.add(new Dog("Jet"));
        System.out.println("mixed=" + mixed);
    }
}
```

Biên dịch/chạy:

```bash
javac SuperWildcardInsertDemo.java
java SuperWildcardInsertDemo
```

Kết quả stdout chính xác:

```text
animals=[Mammal(Mia), Dog(Rex), Cat(Luna), Dog(Max), Dog(Bolt), Cat(Coco)]
first=Mammal(Mia)
mixed=[existing, Dog(Jet)]
```

Giải thích:

1. `sink` tham chiếu đến `List<Animal>`, được phép vì `Animal` là supertype của `Mammal`.
2. `sink.add` lần lượt nhận `Mammal`, `Dog`, `Cat` và biến kiểu tĩnh `Mammal` đang chứa đối tượng `Dog`.
3. `appendAll` nhận `List<Mammal>` làm nguồn và `List<Animal>` làm đích, vì `T = Mammal` đáp ứng cả cận trên của nguồn và cận dưới của đích.
4. Đọc `sink.get(0)` chỉ được đảm bảo là `Object` theo kiểu của biểu thức, mặc dù đối tượng runtime in ra là `Mammal(Mia)`.
5. `mixed` vốn có sẵn một `String`. Nó vẫn có thể được nhìn qua `List<? super Mammal>` và thêm `Dog`, bởi danh sách thực tế có kiểu `List<Object>`.

Chương trình không cần thư viện ngoài JDK.

## 8. Không suy ngược kiểu dữ liệu đã có từ quyền `add`

`List<? super Mammal>` có thể là `List<Object>`, nơi đã có `String` hay `Bird`. Việc thêm `new Dog(...)` thành công về mặt kiểu không làm mọi phần tử đọc ra trở thành `Mammal`:

~~~java
List<Object> actual = new ArrayList<>();
actual.add("before");
List<? super Mammal> sink = actual;
sink.add(new Dog("Rex"));
Object first = sink.get(0); // "before"
// Mammal m = sink.get(0); // Không biên dịch.
~~~

Như vậy **quyền thêm `T` và subtype** là trọng tâm của bài; khi cần đọc phần tử thành một kiểu mạnh hơn `Object`, phải chọn một producer thích hợp hoặc kiểm tra từng object. Một trường hợp cần đọc và ghi đúng cùng type parameter có thể dùng `List<T>` thay vì wildcard consumer.

## 9. So sánh khả năng thêm của các wildcard

Giả sử `Dog <: Mammal <: Animal <: Object`:

| Kiểu tham chiếu | Có thể thêm `Dog`? | Có thể thêm `Mammal`? | Kiểu đọc an toàn tối thiểu |
| --- | --- | --- | --- |
| `List<Mammal>` | Có | Có | `Mammal` |
| `List<? super Mammal>` | Có | Có | `Object` |
| `List<? super Dog>` | Có | Không được bảo đảm | `Object` |
| `List<? extends Mammal>` | Không được bảo đảm | Không được bảo đảm | `Mammal` |
| `List<?>` | Không được bảo đảm | Không được bảo đảm | `Object` |
| `List<Animal>` | Có | Có | `Animal` |

“Không được bảo đảm” có nghĩa lời gọi `add` với đối tượng non-null thuộc kiểu tương ứng **không được trình biên dịch chấp nhận**, kể cả khi đối tượng thực tế tình cờ có đủ khả năng chứa dữ liệu.

Đối với `List<? extends Mammal>`, danh sách thực tế có thể là `List<Cat>`, nên không thể thêm `Dog`. Đối với `List<?>`, kiểu phần tử thực tế có thể là bất kỳ kiểu tham chiếu nào, nên không thể tùy ý thêm một `Mammal` hoặc `Dog`.

### 9.1. Không đảo nhầm quan hệ giữa hai cận `super`

Nếu `Dog <: Mammal` thì một đích chắc chắn nhận **mọi Mammal** cũng nhận được **Dog**:

~~~java
List<? super Mammal> mammals = new ArrayList<Animal>();
List<? super Dog> dogs = mammals; // Hợp lệ.
dogs.add(new Dog("D"));
~~~

Chiều `List<? super Dog>` sang `List<? super Mammal>` không được bảo đảm: nguồn có thể thực sự là `List<Dog>`, không nhận `Cat`. Đây là một phép gán **reference kiểu wildcard**, không phải việc `List<Dog>` trở thành subtype của `List<Mammal>`. Bài chuyên về quan hệ hai wildcard cận dưới phân tích kỹ type-argument containment và cách chuyển tiếp method.

## 10. Cách ứng dụng: phương thức nhận dữ liệu từ nhiều subtype

Khi API chỉ muốn **thêm dữ liệu** vào một collection, nên chọn kiểu tham số `Collection<? super T>` hoặc `List<? super T>`:

```java
static void collectMammals(
        Collection<? super Mammal> destination,
        Mammal first,
        Mammal second) {
    destination.add(first);
    destination.add(second);
}
```

Phương thức trên chấp nhận `Collection<Mammal>`, `Collection<Animal>`, `Collection<Object>`. Caller có thể truyền cả `Dog` và `Cat` cho các tham số `Mammal` nhờ upcasting tham chiếu.

### 10.1. Hàm copy tổng quát với `extends` và `super`

Mẫu được dùng trong chương trình chạy:

```java
static <T> void appendAll(
        Collection<? super T> destination,
        Iterable<? extends T> source) {
    for (T element : source) {
        destination.add(element);
    }
}
```

- `source` là **producer**: nó cho ra một `T` hoặc subtype của `T`.
- `destination` là **consumer**: nó nhận được một `T` và mọi subtype của `T`.
- Khi foreach lấy `T element`, truyền trực tiếp `element` vào `add` là an toàn.

Mẫu này thể hiện **PECS — Producer Extends, Consumer Super**. `appendAll` ở đây **nối thêm** từng phần tử; khác với `Collections.copy(destination, source)`, vốn ghi đè các vị trí đã tồn tại và yêu cầu danh sách đích có kích thước đủ lớn.

Một số API trong thư viện Java cũng sử dụng khả năng ghi an toàn của wildcard `super`, chẳng hạn `Collections.copy(List<? super T> dest, List<? extends T> src)`. Khi đọc các chữ ký này, hãy liên hệ trực tiếp tới khả năng thêm/nhận `T`.

### 10.2. Đích có interface thay vì concrete class

Nếu chỉ cần `add`, khai báo `Collection<? super T>` thường linh hoạt hơn `ArrayList<? super T>` vì caller có thể truyền implementation khác nhau:

```java
static void saveNames(Collection<? super String> target) {
    target.add("Lan");
    target.add("Minh");
}
```

`String` là `final`, nên không có subclass `String` do người dùng tự tạo. Tuy nhiên quy tắc thêm `T` vẫn áp dụng: có thể thêm `String` vào `Collection<String>`, `Collection<CharSequence>`, `Collection<Object>`, vì các kiểu phần tử ấy là `String` hoặc supertype của `String`.

## 11. Kiểu interface và đa hình

Quy tắc không chỉ đúng với kế thừa `extends` giữa class. Nếu `Dog implements Pet`, thì `Dog` là subtype của interface `Pet`. Vì thế:

```java
interface Pet {
    String getName();
}

class Dog implements Pet {
    @Override
    public String getName() {
        return "Rex";
    }
}

List<? super Pet> pets = new ArrayList<Object>();
pets.add(new Dog()); // HỢP LỆ: Dog <: Pet.
```

Đây là ví dụ độc lập về một interface tên `Pet`, không dùng chung class `Dog` trong chương trình phần 7. Đặt các khai báo trong một tệp với `import java.util.*;` nếu chạy thử.

Một đối tượng có thể triển khai nhiều interface. Nếu `T` là một interface, mọi lớp implements hoặc kế thừa interface đó đều có thể được thêm khi kiểu tĩnh của biểu thức phù hợp.

Không thể thêm tùy tiện một kiểu khác **chỉ vì nó có những phương thức tương tự**. Java là ngôn ngữ nominal typing: quan hệ subtype phải có từ khai báo `extends`/`implements` hoặc các quy tắc subtype được ngôn ngữ công nhận.

## 12. Các giới hạn bên ngoài quy tắc kiểu

### 12.1. Compile-time cho phép không đồng nghĩa runtime cho phép sửa

```java
List<? super Number> sink = List.<Number>of(1, 2, 3);
sink.add(4); // Biên dịch được, nhưng ném UnsupportedOperationException.
```

`List.of(...)` trả về danh sách **không thể sửa**. Wildcard chỉ mô tả an toàn kiểu dữ liệu, không cấp quyền thay đổi cấu trúc danh sách.

Điều tương tự áp dụng cho `Collections.unmodifiableList(...)` hoặc một `Collection` tự viết không hỗ trợ `add`.

### 12.2. `null` là trường hợp riêng

```java
List<? super Mammal> sink = new ArrayList<Animal>();
sink.add(null); // HỢP LỆ về kiểm tra kiểu.
```

`null` tương thích với mọi kiểu tham chiếu. Nó không phải là đối tượng có subtype cụ thể của `Mammal`; vì vậy không nên coi việc thêm `null` là bằng chứng rằng mọi đối tượng bất kỳ cũng được thêm.

Collection implementation có thể cấm `null`, ví dụ nhiều implementation bất biến hoặc collection có hợp đồng không chứa null. Khi đó thao tác có thể thất bại tại runtime.

### 12.3. Tính bất biến của generic vẫn được giữ nguyên

```java
List<Dog> dogs = new ArrayList<>();
// List<Mammal> mammals = dogs; // SAI: List<T> invariant.
```

Dùng `? super T` để cung cấp **một góc nhìn ghi an toàn** vào các kiểu phần tử rộng hơn; nó không khiến `List<Dog>` tự biến thành `List<Mammal>`, và không cho phép lẫn `Cat` vào `List<Dog>`.

### 12.4. Không lạm dụng raw type và unchecked cast

```java
List<Dog> dogs = new ArrayList<>();
// List<? super Mammal> invalid = dogs; // SAI.
// List<? super Mammal> forced = (List) dogs; // Raw cast nguy hiểm.
```

Ép kiểu raw có thể che cảnh báo và khiến mã thêm `Cat` vào danh sách được khai báo `List<Dog>`. Khi lấy phần tử ra theo `Dog`, chương trình có thể ném `ClassCastException`. Cách sửa đúng là điều chỉnh kiểu collection đích (ví dụ `List<Mammal>` hoặc `List<Animal>`), hoặc điều chỉnh hợp đồng của phương thức theo dữ liệu cần hỗ trợ.

## 13. Bảng tình huống đúng/sai

Với cây kiểu `Dog, Cat <: Mammal <: Animal <: Object` và khai báo:

```java
List<? super Mammal> sink = new ArrayList<Animal>();
```

| Biểu thức | Biên dịch? | Lý do |
| --- | --- | --- |
| `sink.add(new Mammal("M"))` | Có | Kiểu chính xác `Mammal` |
| `sink.add(new Dog("D"))` | Có | `Dog <: Mammal` |
| `sink.add(new Cat("C"))` | Có | `Cat <: Mammal` |
| `sink.add(new Animal("A"))` | Không | `Animal` là kiểu cha của `Mammal` |
| `sink.add(new Bird("B"))` | Không | `Bird` không phải subtype của `Mammal` |
| `sink.add(new Object())` | Không | `Object` không phải subtype của `Mammal` |
| `sink.add(null)` | Có | `null` tương thích mọi kiểu tham chiếu |
| `Object x = sink.get(0)` | Có | Đọc ra bảo đảm là `Object` |
| `Mammal m = sink.get(0)` | Không | Kiểu đọc không được bảo đảm là `Mammal` |
| `sink.add((Mammal) animal)` | Có ở compile-time | Runtime cast chỉ an toàn nếu `animal` thực sự là `Mammal` |

Các biểu thức trong bảng giả sử class `Animal`, `Mammal`, `Dog`, `Cat`, `Bird` như phần 7, và biến `animal` ở dòng cuối đã được khai báo kiểu `Animal`.

## 14. Lỗi phổ biến khi thiết kế phương thức ghi

### Lỗi 1: Đọc `super` theo hướng ngược

Sai: “`List<? super Mammal>` là danh sách chỉ thêm `Animal` hoặc `Object`.”

Đúng: `Animal`/`Object` là những **kiểu phần tử thực tế có thể của danh sách**. Khi thao tác thông qua tham chiếu wildcard, chỉ việc thêm `Mammal` hoặc subtype của `Mammal` là được bảo đảm.

### Lỗi 2: Chọn `? extends T` cho đối số cần `add`

```java
// static void addDog(List<? extends Mammal> sink) {
//     sink.add(new Dog("Rex")); // SAI.
// }
```

Nguồn có kiểu `List<? extends Mammal>` có thể thực chất là `List<Cat>`. Nếu phương thức muốn nhận `Dog`, khai báo `List<? super Dog>` (được cả `List<Dog>`, `List<Mammal>`, `List<Animal>`, `List<Object>`). Nếu nó muốn nhận mọi `Mammal`, dùng `List<? super Mammal>`.

### Lỗi 3: Cho rằng runtime subtype luôn đủ

```java
Animal animal = new Dog("Rex");
// sink.add(animal); // SAI vì kiểu tĩnh Animal.
```

Cách sửa là biểu diễn đúng hợp đồng kiểu ngay từ đầu (`Mammal mammal = new Dog("Rex")`) hoặc dùng `instanceof Mammal m` khi phải xử lý đối tượng `Animal` không rõ subtype.

### Lỗi 4: Gắn kiểu `List<? super T>` cho mọi tham số

Nếu phương thức **chỉ đọc** phần tử là `T`, `? extends T` thường thích hợp hơn. Nếu vừa đọc thành `T` vừa ghi `T`, cần bảo toàn liên hệ kiểu bằng `List<T>` hoặc phương thức generic `<T>`.

Không có một wildcard tối ưu cho mọi hướng dữ liệu.

### Lỗi 5: Bỏ qua mutability và kiểm tra lỗi runtime

`Collection<? super T>` chỉ bảo đảm lời gọi `add(T)` **an toàn về kiểu**. Nó không buộc mọi implementation hỗ trợ thêm; caller phải truyền một collection phù hợp với hợp đồng phương thức nếu thao tác sửa dữ liệu là bắt buộc.

## 15. Bài tập tự đánh giá

Cho:

```java
class Vehicle {}
class Car extends Vehicle {}
class Taxi extends Car {}
class Bike extends Vehicle {}
```

### Bài 1: Quyết định lời gọi `add`

```java
List<? super Car> destination = new ArrayList<Vehicle>();

destination.add(new Car());  // (1)
destination.add(new Taxi()); // (2)
// destination.add(new Vehicle()); // (3)
// destination.add(new Bike());    // (4)
destination.add(null);       // (5)
```

**Đáp án:** (1), (2) hợp lệ vì `Car` là cận dưới và `Taxi <: Car`; (5) hợp lệ về kiểu. (3), (4) sai vì `Vehicle` và `Bike` không phải subtype của `Car`.

### Bài 2: Chọn danh sách nguồn hợp lệ

Cho `List<? super Car> sink`, hãy cho biết kiểu danh sách nào có thể gán cho biến này: `List<Car>`, `List<Vehicle>`, `List<Object>`, `List<Taxi>`.

**Đáp án:** `List<Car>`, `List<Vehicle>`, `List<Object>` đều hợp lệ; `List<Taxi>` không hợp lệ, vì không thể thêm một `Car` bất kỳ vào `List<Taxi>`.

### Bài 3: So sánh kiểu tĩnh và kiểu thực tế

```java
Vehicle a = new Taxi();
Car b = new Taxi();

// sink.add(a); // (1)
sink.add(b);    // (2)
```

**Đáp án:** (1) sai dù runtime là `Taxi`, vì `a` có kiểu tĩnh `Vehicle`. (2) đúng vì `b` có kiểu tĩnh `Car`.

### Bài 4: Vì sao không đọc được `Car`?

```java
List<Object> objects = new ArrayList<>();
objects.add("not a vehicle");
List<? super Car> sink = objects;

// Car c = sink.get(0);
Object value = sink.get(0);
```

**Đáp án:** `sink` có thể tham chiếu tới `List<Object>` chứa các kiểu khác. `get` chỉ bảo đảm trả về `Object`. Nếu ép kiểu thành `Car` với phần tử đầu tiên là chuỗi, chương trình ném `ClassCastException`.

### Bài 5: Điền wildcard hợp lý

Một phương thức cần nhận từng phần tử kiểu `Car` hoặc subtype từ một nguồn và thêm vào một collection có thể chứa `Car`. Chữ ký nào phù hợp?

```java
static void transfer(
        Collection<? super Car> destination,
        Iterable<? extends Car> source) {
    for (Car car : source) {
        destination.add(car);
    }
}
```

**Đáp án:** Đích dùng `? super Car` vì **tiêu thụ** `Car`; nguồn dùng `? extends Car` vì **sản xuất** `Car`. Phương thức này nhận được nguồn `Iterable<Taxi>` và đích `Collection<Vehicle>`.

## 16. Checklist khi cần thêm phần tử vào generic collection

1. Xác định `T`: kiểu tối thiểu mà collection nhận phải hỗ trợ.
2. Nếu tham số là đích ghi, dùng `Collection<? super T>` hoặc interface tổng quát phù hợp.
3. Kiểm tra **kiểu tĩnh `S` của biểu thức được thêm**: yêu cầu `S <: T` hoặc `S = T`.
4. Kiểm tra kiểu phần tử thực tế của collection (`E`) có phải `T` hoặc supertype của `T` hay không.
5. Không suy luận subtype chỉ từ đối tượng runtime khi biến khai báo kiểu rộng hơn.
6. Đừng mặc định `get()` trả về `T` trên một biến `? super T`: mặc định an toàn là `Object`.
7. Kiểm tra collection có hỗ trợ `add` và chính sách `null` ở runtime.
8. Tránh raw type hoặc unchecked cast để lách lỗi biên dịch.

## 17. Tài liệu tham khảo

- Oracle Java Tutorials — [Guidelines for Wildcard Use](https://docs.oracle.com/javase/tutorial/java/generics/wildcardGuidelines.html): quy tắc consumer và cách chọn `? super T`.
- Oracle Java Tutorials — [Lower Bounded Wildcards](https://docs.oracle.com/javase/tutorial/java/generics/lowerBounded.html): ví dụ và cách dùng wildcard cận dưới.
- Java Language Specification, Java SE 17 — [§4.5.1 Type Arguments of Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1): kiểu đối số wildcard và quan hệ cận.
- Java API — [Collection.add(E)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#add(E)): hợp đồng thêm phần tử, các trường hợp không hỗ trợ thao tác và ngoại lệ runtime.

**Ghi nhớ cốt lõi:** Với `List<? super T>`, mọi giá trị có **kiểu tĩnh là `T` hoặc subtype của `T`** đều có thể truyền vào `add` một cách an toàn về kiểu. Cận `super` mô tả kiểu phần tử thực tế của collection đủ rộng để tiếp nhận các giá trị ấy; khi đọc từ tham chiếu wildcard, trình biên dịch vẫn chỉ bảo đảm `Object`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lower-bounded-wildcards-004">Vì sao giá trị đọc ra chỉ được bảo đảm là Object</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao đọc từ `List<? super T>` chỉ được bảo đảm là `Object`?

## 1. Vấn đề cần hiểu

Trong Java generics, wildcard giới hạn dưới `? super T` (*lower-bounded wildcard*) biểu thị **một kiểu chưa biết nhưng là `T` hoặc một supertype của `T`**. Ví dụ, `List<? super Integer>` có thể tham chiếu đến `List<Integer>`, `List<Number>`, `List<Object>` hoặc danh sách tham số hóa bằng một supertype hợp lệ khác của `Integer`.

Ta có thể **thêm `Integer`** an toàn vào các danh sách đó, vì mọi kiểu phần tử khả dĩ đều chấp nhận `Integer`. Nhưng khi **đọc** bằng `get(...)`, compiler không biết danh sách thực tế chứa `Integer`, `Number`, `Object` hay giá trị thuộc một loại rộng hơn. Với `java.util.List<E>`, giới hạn trên khai báo của `E` là `Object`, nên kiểu đọc bảo đảm duy nhất là **`Object`**.

~~~java
List<Number> numbers = new ArrayList<>();
List<? super Integer> destination = numbers;

destination.add(42);            // Được: Integer phù hợp với mọi supertype của Integer.
Object item = destination.get(0); // Được: giới hạn trên an toàn là Object.
// Integer exact = destination.get(0); // Lỗi biên dịch.
~~~

Đây là điểm khác biệt quan trọng giữa **kiểu phần tử có thể ghi vào** và **kiểu phần tử có thể suy ra khi đọc ra**. Bài học giải thích chính xác nguyên nhân, các trường hợp dễ nhầm và cách dùng trong API Java 17 trở lên.

## 2. Một biến `List<? super Integer>` có thể trỏ đến những danh sách nào?

Xem các khai báo độc lập sau:

~~~java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();

List<? super Integer> a = integers;
List<? super Integer> b = numbers;
List<? super Integer> c = objects;
~~~

Ba phép gán đều hợp lệ vì `Integer` có thể được sử dụng ở nơi cần `Integer`, `Number` hoặc `Object`. Có thể có thêm các supertype khác, chẳng hạn interface mà `Integer` thực thi. Wildcard không chọn một trong các kiểu ấy tại thời điểm biên dịch; nó **che kiểu phần tử cụ thể** khỏi code đang dùng biến `List<? super Integer>`.

Hãy tưởng tượng danh sách thực sự là:

~~~java
List<Object> objects = new ArrayList<>();
objects.add("text");                // String là Object.
objects.add(Integer.valueOf(99));   // Integer cũng là Object.

List<? super Integer> view = objects;
Object first = view.get(0);         // Giá trị thực tế là String.
~~~

Nếu compiler tự cho phép `Integer first = view.get(0)` thì chương trình sẽ nhận một `String` như `Integer`, làm sai bảo đảm tĩnh của generics. Java không cho phép phép gán ấy. Khả năng **thêm** `Integer` không suy ra rằng **mọi phần tử có sẵn** đều là `Integer`.

**Điều quan trọng:** `List<? super Integer>` không có nghĩa “list chỉ chứa số nguyên”. Nó là *một góc nhìn* có thể ghi số nguyên vào các list có kiểu phần tử thuộc một supertype hợp lệ của `Integer`.

## 3. Lập luận kiểu: giới hạn dưới giúp ghi, giới hạn trên quyết định đọc

`List<E>` khai báo tham số kiểu `E` không có bound tường minh, vì vậy bound mặc định của `E` là `Object`.

Khi compiler gặp `List<? super Integer>`, quy tắc **capture conversion** của Java có thể mô hình hóa wildcard bằng một kiểu ẩn, tạm gọi `CAP#1`:

~~~text
Integer <: CAP#1 <: Object
~~~

Đây là **sơ đồ quan hệ kiểu**, không phải cú pháp khai báo Java. Ký hiệu `A <: B` nói rằng `A` là subtype của `B`.

- **Giới hạn dưới `Integer`** bảo đảm mọi giá trị `Integer` đều gán được cho `CAP#1`. Vì vậy `destination.add(42)` có thể biên dịch.
- **Giới hạn trên `Object`** bảo đảm mọi giá trị thuộc `CAP#1` đều có thể được đọc như `Object`.
- Không có ràng buộc nào bảo đảm `CAP#1 <: Number` hoặc `CAP#1 <: Integer`. Do đó `Number n = destination.get(0)` và `Integer i = destination.get(0)` không được phép.

Hãy gắn lập luận này vào chữ ký của `List<E>`:

~~~java
E get(int index);
boolean add(E element);
E set(int index, E element);
~~~

Đây là các **chữ ký rút gọn** để giải thích kiểu, không phải ba phương thức độc lập cần đem biên dịch. Sau capture conversion:

- `get` trả về `CAP#1`, có thể **nâng lên `Object`** mà không cần cast.
- `add` cần một giá trị `CAP#1`; compiler cho phép truyền `Integer` nhờ `Integer <: CAP#1`.
- `set` nhận `Integer` an toàn nhưng giá trị cũ mà nó trả về vẫn chỉ có thể được dùng như `Object`.

Chẳng hạn:

~~~java
List<? super Integer> sink = new ArrayList<Number>();
sink.add(5);
Object old = sink.set(0, 7);
Object current = sink.get(0);

// Không hợp lệ: không biết kiểu CAP#1 chính xác.
// Integer oldInt = sink.set(0, 9);
// Number currentNumber = sink.get(0);
~~~

**Quy tắc đọc thực sự phụ thuộc vào *giới hạn trên* của type variable trong generic class, không phải chỉ vào chữ `super`.** Với `List<E>`, giới hạn đó là `Object`. Một ví dụ generic class có bound `E extends Number` sẽ được xét riêng ở mục 8.

## 4. So sánh `extends`, `super` và wildcard không giới hạn

| Kiểu view | Một số kiểu nguồn phù hợp | Kiểu an toàn khi `get(0)` | Có thể `add(new Integer(1))` về mặt kiểu? |
| --- | --- | --- | --- |
| `List<Integer>` | `List<Integer>` | `Integer` | Có |
| `List<? extends Number>` | `List<Integer>`, `List<Double>`, `List<Number>` | `Number` | Không |
| `List<? super Integer>` | `List<Integer>`, `List<Number>`, `List<Object>` | `Object` | Có |
| `List<?>` | `List<String>`, `List<Integer>`, v.v. | `Object` | Không (trừ `null`) |
| `List<Object>` | `List<Object>` | `Object` | Có |

Trong bảng, “có thể add” chỉ nói **compiler cho phép thao tác theo kiểu**. Nếu implementation là danh sách không sửa được như `List.of(...)`, `add` vẫn có thể ném `UnsupportedOperationException` lúc chạy.

Đối chiếu ba ví dụ nhỏ:

~~~java
List<Integer> ints = new ArrayList<>();

List<? extends Number> producer = ints;
Number readNumber = producer.get(0); // Hợp lệ về kiểu (nhưng list phải có phần tử).
// producer.add(5);                  // Không hợp lệ.

List<? super Integer> consumer = ints;
consumer.add(5);                     // Hợp lệ.
Object readObject = consumer.get(0);  // Hợp lệ.
// Integer readInteger = consumer.get(0); // Không hợp lệ.

List<?> unknown = ints;
Object any = unknown.get(0);         // Hợp lệ.
// unknown.add(5);                   // Không hợp lệ.
~~~

Vì `ints` bắt đầu là list rỗng, **đừng chạy `get(0)` trước khi một phần tử được thêm vào**; khối trên nhằm minh họa hệ thống kiểu theo từng dòng, không phải chương trình hoàn chỉnh chạy theo thứ tự. Mã chạy độc lập với phần tử hợp lệ nằm ở mục 7.

PECS (*Producer Extends, Consumer Super*) tóm tắt hai trường hợp sử dụng thường gặp:

- Khi cần **đọc các giá trị như một loại cha cụ thể**, chọn `? extends T`.
- Khi cần **ghi các giá trị `T` hoặc subtype của `T`**, chọn `? super T`.

PECS không có nghĩa `? super T` bị cấm đọc; ta vẫn đọc được, nhưng thường chỉ có một `Object` khi generic declaration không chỉ định bound hẹp hơn.

## 5. Vì sao “tôi vừa thêm Integer” vẫn không đổi kiểu đọc thành Integer?

Một ví dụ trực giác:

~~~java
List<Number> numbers = new ArrayList<>();
numbers.add(1.5);

List<? super Integer> sink = numbers;
sink.add(42);

Object first = sink.get(0);  // Double thực tế.
Object second = sink.get(1); // Integer thực tế.
~~~

Biến `sink` có **một kiểu khai báo duy nhất**. Compiler không thiết lập quy tắc rằng các vị trí sau một lời gọi `add(Integer)` chắc chắn luôn chứa `Integer`:

- List có thể **đã có phần tử cũ** thuộc supertype rộng hơn.
- List có thể được sửa thông qua **reference khác** như `numbers`, cũng trỏ cùng đối tượng.
- Chỉ số phần tử và trạng thái list có thể thay đổi qua các thao tác khác; compiler không theo dõi xuất xứ/kiểu runtime của từng vị trí trong collection tổng quát.
- Một danh sách dưới view `List<? super Integer>` có thể chứa các đối tượng `Number`, `Object` tùy generic argument thật sự.

Nếu bạn vẫn giữ chính đối tượng `Integer` đã thêm và muốn dùng nó, hãy dùng biến đã có kiểu:

~~~java
Integer added = 42;
sink.add(added);
Integer retained = added; // Không cần đọc lại từ view.
~~~

Nếu cần đọc phần tử như `Number` và bạn **biết chắc danh sách nguồn** có kiểu `List<Number>`, hãy dùng reference có thông tin chính xác hơn:

~~~java
List<Number> typed = new ArrayList<>();
List<? super Integer> sink = typed;

sink.add(42);
Number n = typed.get(0); // Hợp lệ nhờ kiểu khai báo List<Number> của typed.
Object o = sink.get(0);  // Chỉ Object qua view List<? super Integer>.
~~~

Hai biến cùng chỉ một list, nhưng **hợp đồng kiểu tĩnh** ở từng biểu thức khác nhau. Phép đọc dùng `sink` không tự thừa hưởng mọi tri thức của biến `typed`.

## 6. Đọc an toàn: dùng `Object` rồi kiểm tra khi cần subtype

Với `List<? super Integer>`, dạng tổng quát là:

~~~java
List<? super Integer> values = new ArrayList<Object>();
values.add(10);
Object value = values.get(0);
System.out.println(value);
~~~

Nếu thuật toán thực sự cần thao tác số học, hãy **kiểm tra đối tượng được đọc tại runtime**:

~~~java
Object value = values.get(0);
if (value instanceof Integer integer) {
    System.out.println(integer + 1);
} else if (value instanceof Number number) {
    System.out.println(number.doubleValue());
} else {
    System.out.println("Không phải số nguyên hoặc Number");
}
~~~

Pattern matching cho `instanceof` ở ví dụ trên được hỗ trợ từ Java 16 và dùng ổn định với Java 17. Nó chứng minh kiểu **của đối tượng riêng lẻ đang được kiểm tra**, không chứng minh mọi phần tử của danh sách đều là `Integer`.

Có thể dùng checked cast:

~~~java
Object value = values.get(0);
Integer integer = (Integer) value; // Chỉ thành công nếu object là Integer hoặc null.
~~~

Nếu thực tế là `Double` hoặc `String`, cast ném `ClassCastException`; nếu là `null`, cast trả về `null`. Nên dùng `instanceof` khi dữ liệu có thể thuộc nhiều kiểu. **Cast không biến `List<? super Integer>` thành `List<Integer>`.**

Không viết:

~~~java
List<? super Integer> values = new ArrayList<Object>();
// KHÔNG biên dịch: kiểu trả về CAP#1 chỉ bảo đảm là Object.
Integer integer = values.get(0);

// KHÔNG biên dịch vì List<? super Integer> không bảo đảm là List<Integer>.
List<Integer> exactList = values;
~~~

Lỗi ở compile time nhằm ngăn caller tự suy ra một subtype chỉ từ giới hạn dưới.

## 7. Chương trình hoàn chỉnh Java 17+: ghi Integer và đọc dưới dạng Object

Lưu tệp với tên **SuperReadDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class SuperReadDemo {
    static final class NumberBox<T extends Number> {
        private T value;
        NumberBox(T value) { this.value = value; }
        T get() { return value; }
        void set(T value) { this.value = value; }
    }

    static void appendInteger(List<? super Integer> destination, Integer value) {
        destination.add(value);
    }

    public static void main(String[] args) {
        List<Number> numbers = new ArrayList<>();
        numbers.add(1.5);
        List<? super Integer> output = numbers;
        appendInteger(output, 10);
        Object first = output.get(0);
        Object last = output.get(1);
        System.out.println("first: " + first);
        System.out.println("last: " + last);
        System.out.println("numbers: " + numbers);

        List<Object> mixed = new ArrayList<>();
        mixed.add("text");
        List<? super Integer> sameTypeOfView = mixed;
        sameTypeOfView.add(7);
        Object item = sameTypeOfView.get(0);
        System.out.println("mixed first: " + item);
        if (sameTypeOfView.get(1) instanceof Integer value) {
            System.out.println("checked integer: " + value);
        }
        System.out.println("mixed: " + mixed);

        NumberBox<Number> numberBox = new NumberBox<>(3.5);
        NumberBox<? super Integer> boxView = numberBox;
        Number before = boxView.get();
        boxView.set(9);
        System.out.println("bounded read: " + before);
        System.out.println("bounded after: " + boxView.get());
    }
}
~~~

Biên dịch và chạy (Java 17+):

~~~bash
javac --release 17 -Xlint:all SuperReadDemo.java
java SuperReadDemo
~~~

**Stdout chính xác**:

~~~text
first: 1.5
last: 10
numbers: [1.5, 10]
mixed first: text
checked integer: 7
mixed: [text, 7]
bounded read: 3.5
bounded after: 9
~~~

Chương trình đã được kiểm tra bằng `javac --release 17 -Xlint:all` và chạy thành công, **không phát sinh unchecked warning**.

Giải thích:

1. `output` có kiểu `List<? super Integer>` và được gán từ `List<Number>`; `appendInteger` thêm `10` an toàn.
2. Dù đã thêm `Integer`, `output.get(0)` và `output.get(1)` đều được đọc vào biến `Object`. Phần tử đầu thực tế là `Double 1.5` và phần tử sau là `Integer 10`.
3. `sameTypeOfView` có **cùng kiểu tĩnh** `List<? super Integer>` nhưng trỏ đến `List<Object>` có sẵn `String "text"`. Đây là phản ví dụ trực tiếp cho suy đoán rằng đọc từ `? super Integer` luôn ra `Integer`.
4. `instanceof Integer value` kiểm tra đúng phần tử thứ hai rồi mới sử dụng như `Integer`.
5. `NumberBox<? super Integer>` được đọc như `Number` vì tham số kiểu **của chính `NumberBox<T extends Number>`** có giới hạn trên `Number`, như mục tiếp theo.

## 8. Ngoại lệ quan trọng: generic class có upper bound hẹp hơn `Object`

Nói “`? super T` luôn chỉ được đọc như `Object`” là **quá tổng quát**. Quy tắc chính xác là: kiểu capture có **giới hạn trên từ tham số kiểu của generic declaration**; kết quả đọc tối thiểu dựa trên bound ấy.

Với `List<E>`, `E` có upper bound mặc định `Object`, nên `List<? super Integer>.get()` không có đảm bảo mạnh hơn `Object`.

Nhưng với class tự khai báo:

~~~java
static final class NumberBox<T extends Number> {
    private T value;

    NumberBox(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    void set(T value) {
        this.value = value;
    }
}
~~~

`NumberBox<? super Integer>` có capture type `CAP#2` thỏa:

~~~text
Integer <: CAP#2 <: Number
~~~

Vì **upper bound là `Number`**, có thể đọc an toàn như `Number`:

~~~java
NumberBox<Number> actual = new NumberBox<>(3.5);
NumberBox<? super Integer> view = actual;

view.set(42);             // Integer là subtype của CAP#2.
Number n = view.get();    // Hợp lệ: CAP#2 chắc chắn là Number.
// Integer i = view.get(); // Không hợp lệ: CAP#2 có thể là Number.
~~~

Hai khối trên là **trích đoạn minh họa cần đặt trong cùng một lớp và import/type context phù hợp**, còn chương trình mục 7 là ví dụ chạy độc lập.

Quy tắc JLS về capture conversion còn xét bound và các ràng buộc của generic declaration khác; dạng `T extends Number` là trường hợp dễ quan sát nhất. Vì vậy **đọc ra `Object` là kết luận đúng cho `List<? super T>` và các generic type có parameter bound là `Object`**, nhưng phải kiểm tra upper bound khi dùng generic class khác.

## 9. Viết API theo hướng consumer: khi nào chọn `? super T`?

### 9.1. Hàm đẩy dữ liệu vào list đích

~~~java
static void addDefaults(List<? super Integer> target) {
    target.add(10);
    target.add(20);
}
~~~

Có thể gọi hàm này với `List<Integer>`, `List<Number>` hoặc `List<Object>`, miễn implementation cho phép sửa. Hàm chỉ cần biết rằng **Integer là kiểu được chấp nhận khi ghi**. Nó không phải đọc từ list rồi thực hiện phép toán `Integer`.

### 9.2. Đối chiếu nguồn `extends` và đích `super`

~~~java
static <T> void copyValues(List<? extends T> source, List<? super T> target) {
    for (T element : source) {
        target.add(element);
    }
}
~~~

Đây là một phương thức minh họa có thể đặt trong lớp Java sau khi import `java.util.List`. Ví dụ:

~~~java
List<Integer> source = List.of(1, 2, 3);
List<Number> target = new ArrayList<>();
copyValues(source, target);
~~~

`source` **cung cấp** các `T`; `target` **nhận** các `T`. Chữ ký tách hai mục tiêu và không yêu cầu đọc `Integer` từ một `List<? super Integer>`. Trước khi dùng mẫu này trong production, hãy xét hợp đồng về danh sách có thể sửa được, `null` và xử lý khi nguồn/đích có thể bị cập nhật đồng thời.

API chuẩn `Collections.copy(List<? super T> dest, List<? extends T> src)` sử dụng chính nguyên tắc này; nó còn có yêu cầu kích thước đích đủ lớn (khác với việc append ở ví dụ `copyValues`). Đừng tự coi mọi “copy” đều có nghĩa thêm phần tử vào cuối.

### 9.3. Không dùng `? super` khi cần trả về subtype chính xác

Nếu mục tiêu là lấy `Integer` để tính toán, hãy nhận một nguồn có kiểu thích hợp:

~~~java
static int sumIntegers(List<Integer> values) {
    int total = 0;
    for (Integer value : values) {
        total += value;
    }
    return total;
}
~~~

Hoặc nếu muốn xử lý cả các loại số khác:

~~~java
static double sumNumbers(List<? extends Number> values) {
    double total = 0.0;
    for (Number value : values) {
        total += value.doubleValue();
    }
    return total;
}
~~~

Dùng `? super Integer` cho một tham số chỉ để **đọc các số nguyên** sẽ đưa ra hợp đồng quá rộng về dữ liệu và quá yếu về kiểu kết quả.

## 10. Các thao tác khác trên `List<? super Integer>`

| Thao tác | Compiler cho phép? | Ý nghĩa |
| --- | --- | --- |
| `destination.add(3)` | Có | `Integer` gán được cho kiểu capture. |
| `destination.add(Integer.valueOf(3))` | Có | Bổ sung một `Integer`. |
| `destination.add(3.5)` | Không | `Double` không được bảo đảm phù hợp với mọi loại list đích. |
| `destination.add((Number) 3)` | Không | Biến `Number` có thể chứa loại không phải `Integer`. |
| `destination.add(null)` | Có về kiểu | `null` có thể được truyền cho kiểu tham chiếu; collection cụ thể có thể không chấp nhận null. |
| `Object x = destination.get(0)` | Có | Giới hạn trên của `List<E>` là `Object`. |
| `Integer x = destination.get(0)` | Không | Không bảo đảm capture là subtype của `Integer`. |
| `Number x = destination.get(0)` | Không | Capture có thể là `Object`. |
| `Object x = destination.set(0, 7)` | Có về kiểu | Ghi `Integer`, đọc phần tử cũ như `Object`. |
| `destination.remove(0)` | Có về kiểu | Xóa theo chỉ số, không cần cung cấp một kiểu phần tử cụ thể. |
| `destination.clear()` | Có về kiểu | Xóa toàn bộ; implementation có thể cấm sửa. |

Một số phương thức trả về kiểu không liên quan tới `E`, chẳng hạn `size()` trả về `int` và `contains(Object)` trả về `boolean`. Cụm “đọc chỉ bảo đảm là Object” đề cập cụ thể tới **đọc một giá trị thuộc type parameter `E`** bằng `get`, `iterator().next()`, `set` trả về phần tử cũ, v.v., không phải mọi phương thức của `List`.

## 11. Lỗi thường gặp và cách sửa

**Lỗi 1 — Đồng nhất `? super Integer` với `List<Integer>`.**

~~~java
List<? super Integer> target = new ArrayList<Number>();
// Integer n = target.get(0); // Không biên dịch.
~~~

Sửa bằng cách nhận giá trị như `Object`, dùng `instanceof` nếu cần phân nhánh theo kiểu thực tế, hoặc thay đổi hợp đồng method nếu thuật toán đòi `Integer`.

**Lỗi 2 — Suy ra kiểu đọc từ giá trị vừa `add`.** Java không theo dõi được mọi phần tử trong một container theo thứ tự thêm ở cấp kiểu. Giữ reference `Integer` ban đầu, hoặc dùng view chính xác hơn khi có chứng cứ.

**Lỗi 3 — Ép `(Integer) list.get(0)` mà không kiểm tra dữ liệu.** Cast này có thể phát sinh `ClassCastException` nếu phần tử là `Double` hoặc `String`. Nó cũng không thay đổi loại generic của danh sách.

**Lỗi 4 — Ép kiểu cả collection:**

~~~java
List<? super Integer> view = new ArrayList<Object>();
// Không nên cố biến toàn bộ danh sách thành List<Integer>
// qua raw cast hoặc unchecked cast để lấy kiểu phần tử chính xác hơn.
~~~

Việc dùng raw type hoặc unchecked cast chỉ che đi rủi ro, có thể tạo heap pollution. Nếu cần collection `List<Integer>`, hãy kiểm tra từng giá trị và tạo collection mới với type argument xác định.

**Lỗi 5 — Nghĩ `? super Number` có thể đọc `Number`.** Đối với `List<E>`:

~~~java
List<? super Number> list = new ArrayList<Object>();
list.add(3.5);          // Hợp lệ: Double là Number.
Object value = list.get(0); // Kiểu đọc vẫn chỉ bảo đảm Object.
// Number n = list.get(0);  // Không biên dịch.
~~~

Giới hạn dưới `Number` giúp thêm `Number` và subtype; nó **không** tự nâng giới hạn trên của `List<E>` khỏi `Object`.

**Lỗi 6 — Quên rằng generic view không quyết định list có sửa được.** `List<? super Integer> view = List.<Integer>of(1, 2);` hợp lệ, nhưng `view.add(3)` sẽ ném `UnsupportedOperationException` lúc chạy vì `List.of` tạo danh sách không sửa được. Đây là vấn đề hợp đồng mutability, không phải lỗi giới hạn kiểu.

## 12. Tự kiểm tra kiến thức

1. `List<? super Integer>` có thể nhận tham chiếu đến `List<Object>` không? **Có**, vì `Object` là supertype của `Integer`.
2. Vì sao `target.add(12)` hợp lệ? **Vì `Integer` là subtype của mọi kiểu phần tử phù hợp với `? super Integer`.**
3. Vì sao `Integer x = target.get(0)` không hợp lệ? **Vì kiểu phần tử thực tế có thể là `Number`, `Object` hay supertype khác; compiler chỉ biết giới hạn trên của `List<E>` là `Object`.**
4. Đã thêm một `Integer` thì có thể đọc mọi phần tử như `Integer` không? **Không.** Danh sách có thể đã chứa giá trị thuộc kiểu rộng hơn và được sửa qua reference khác.
5. Có thể dùng `instanceof Integer` cho giá trị đọc ra không? **Có.** Kiểm tra đó chỉ bảo đảm kiểu cho giá trị đang xét khi điều kiện đúng.
6. Có phải `? super T` luôn chỉ đọc được như `Object` trong mọi generic class? **Không.** Với `Box<E extends Number>` và `Box<? super Integer>`, đọc được dưới dạng `Number`.
7. Muốn nhận một danh sách để đọc mọi `Number`, nên chọn `List<? extends Number>` hay `List<? super Number>`? **`List<? extends Number>`**, vì bảo đảm từng phần tử đọc được như `Number`.

## 13. Tài liệu tham khảo

- [JLS Java SE 17 §4.5.1 — Type Arguments of Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1): wildcard giới hạn dưới/giới hạn trên và containment.
- [JLS Java SE 17 §5.1.10 — Capture Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.10): capture type của `? super T`, lower bound và upper bound từ generic declaration.
- [JLS Java SE 17 §4.10.2 — Subtyping Among Class and Interface Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.10.2): quan hệ subtyping khi dùng parameterized types.
- [Java SE 17 API — List<E>](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html): chữ ký `add(E)`, `get(int)`, `set(int,E)` và ngoại lệ có thể phát sinh.
- [Java SE 17 API — Collections.copy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#copy(java.util.List,java.util.List)): ví dụ thực tế dùng `? super T` ở đích và `? extends T` ở nguồn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lower-bounded-wildcards-005">Phân biệt giới hạn dưới của wildcard với giới hạn của tham số kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt giới hạn dưới của wildcard với giới hạn của tham số kiểu trong Java Generics

## 1. Vấn đề: hai cách nói “giới hạn kiểu” không tương đương

Khi đọc chữ ký Java generics, ta thường gặp hai cấu trúc:

```java
List<? super Integer> destination;
<T extends Number> void process(T value) { /* ... */ }
```

Cả hai đều sử dụng từ ngữ “giới hạn” (*bound*), nhưng chúng áp dụng cho **hai đối tượng ngữ pháp khác nhau**:

- `? super Integer` là **wildcard có giới hạn dưới** (*lower-bounded wildcard*) ở **vị trí đối số kiểu**; nó diễn đạt một kiểu phần tử chưa biết nhưng là kiểu `Integer` hoặc một **kiểu cha của `Integer`**.
- `<T extends Number>` là **khai báo tham số kiểu có giới hạn trên** (*upper-bounded type parameter*); nó đặt tên cho một kiểu `T` mà trình biên dịch phải biết là `Number` hoặc **kiểu con của `Number`** trong phạm vi khai báo.

Điểm quan trọng: Java **không hỗ trợ cú pháp khai báo tham số kiểu có giới hạn dưới** như `<T super Integer>`. Từ khóa `super` xuất hiện hợp lệ trong giới hạn dưới của **wildcard**, còn tham số kiểu khai báo bằng `<T extends ...>` dùng giới hạn trên.

Bài này tập trung phân biệt ý nghĩa, cú pháp, cách suy luận kiểu và tác dụng khi đọc/ghi dữ liệu; nó không đánh đồng tham số kiểu `T` với wildcard `?`.

## 2. Phân biệt hình thức: type parameter và wildcard

### 2.1. Tham số kiểu là một tên kiểu có thể dùng lại

Trong phương thức sau, `T` là **tham số kiểu** được khai báo và có thể xuất hiện ở nhiều vị trí:

```java
static <T extends Number> T identity(T value) {
    return value;
}
```

- `<T extends Number>` **khai báo** `T`, đồng thời ràng buộc `T` phải là một kiểu thích hợp với `Number`.
- `T value` và kiểu trả về `T` cùng tham chiếu tới **một tham số kiểu được đặt tên**.
- Trong thân phương thức, có thể gọi những thao tác được bảo đảm bởi giới hạn trên, chẳng hạn `value.doubleValue()`.
- Ở mỗi lần gọi, trình biên dịch suy luận hoặc kiểm tra đối số kiểu phù hợp (ví dụ `Integer`, `Double`).

`extends` trong giới hạn tham số kiểu bao gồm cả **kế thừa lớp** và **triển khai interface**:

```java
static <T extends CharSequence> int sizeOf(T text) {
    return text.length();
}
```

`CharSequence` là interface, nhưng từ khóa vẫn là `extends`, không phải `implements`.

Giới hạn còn có thể là giao (*intersection*) của nhiều yêu cầu:

```java
static <T extends Number & Comparable<T>> int compare(T a, T b) {
    return a.compareTo(b);
}
```

Nếu có lớp trong danh sách giới hạn, lớp phải đứng trước các interface. Đây vẫn là **giới hạn trên** đối với `T`.

### 2.2. Wildcard biểu diễn một kiểu chưa biết tại vị trí sử dụng

Khác với khai báo `T`, wildcard không đặt tên kiểu để sử dụng độc lập:

```java
List<? super Integer> receiver = new ArrayList<Number>();
```

Ở đây, dấu `?` là **đối số kiểu** của `List<...>`. Danh sách thực tế có thể được khai báo với `Integer` hoặc một kiểu cha phù hợp của `Integer`; mã sử dụng `receiver` **không biết chính xác** tham số kiểu của danh sách.

`? super Integer` khác với `? extends Integer`:

- `? super Integer`: kiểu phần tử chưa biết là **`Integer` hoặc kiểu cha của `Integer`**; an toàn để **ghi `Integer`**.
- `? extends Integer`: kiểu phần tử chưa biết là **`Integer` hoặc kiểu con của `Integer`**; an toàn để **đọc thành `Integer`**. `Integer` là `final`, nên ví dụ với nó ít thể hiện sự đa dạng của kiểu con, nhưng quy tắc vẫn áp dụng.

Khi gọi phương thức trên `List<? super Integer>`, trình biên dịch có thể tạo một kiểu capture nội bộ cho wildcard. Capture đó có giới hạn dưới là `Integer` và được bảo đảm có giới hạn trên `Object`. Không thể biến wildcard `?` thành một tên kiểu `T` rồi sử dụng tùy tiện ở các khai báo khác.

### 2.3. Bảng đối chiếu

| Thuộc tính | `List<? super Integer>` | `<T extends Number>` |
| --- | --- | --- |
| Đối tượng bị ràng buộc | Wildcard làm **đối số kiểu** của `List` | **Tham số kiểu được khai báo** tên `T` |
| Hướng giới hạn | **Dưới**: kiểu phần tử chưa biết là kiểu cha hoặc chính `Integer` | **Trên**: `T` là kiểu con hoặc chính `Number` |
| Vị trí xuất hiện | Trong kiểu tham số hóa, ví dụ `List<? super Integer>` | Trong khai báo lớp, interface, phương thức hoặc constructor generic |
| Có tên để liên hệ nhiều vị trí? | Không có tên trực tiếp; có thể dùng helper để capture | Có: cùng một `T` có thể lặp lại ở tham số, biến, kiểu trả về |
| Giá trị có thể đưa vào danh sách | Qua biến `List<? super Integer>`, có thể thêm `Integer` (và subtype tương ứng) | Chỉ riêng giới hạn `T extends Number` **không nói** có danh sách nào để thêm |
| Giá trị lấy ra | Với `List<? super Integer>`, chỉ chắc chắn nhận được `Object` | Với giá trị `T`, ít nhất có thể sử dụng nó như `Number` |
| Cú pháp tương ứng đối lập | `? extends Integer` là wildcard giới hạn trên | `<T super Integer>` **không hợp lệ** trong Java |

Ký hiệu “kiểu cha” ở đây bao gồm những **supertype** hợp lệ theo hệ thống kiểu Java, kể cả interface. Chẳng hạn `List<Number>`, `List<Object>` và các kiểu `List` với tham số là supertype thích hợp của `Integer` đều có thể được dùng tại vị trí `List<? super Integer>`. Không nên hiểu rằng chỉ có chính xác ba lựa chọn `Integer`, `Number`, `Object`.

## 3. `? super T` tạo ra giới hạn dưới như thế nào?

Xét:

```java
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();

List<? super Integer> targetA = numbers; // Đúng
List<? super Integer> targetB = objects; // Đúng
```

Ở trường hợp `targetA`, danh sách thật nhận `Number`. Ở `targetB`, danh sách thật nhận `Object`. Cả hai loại đều **chấp nhận một `Integer`**, vì mọi `Integer` đồng thời là một `Number` và một `Object`.

Các thao tác hợp lệ về kiểu:

```java
targetA.add(Integer.valueOf(10));
targetB.add(20); // int được boxing thành Integer
targetA.set(0, Integer.valueOf(30)); // Cần chỉ số hợp lệ và List hỗ trợ set
```

Ngược lại:

```java
targetA.add(3.14);                 // LỖI BIÊN DỊCH: Double không bảo đảm phù hợp
targetB.add(new Object());        // LỖI BIÊN DỊCH: Object không chắc là Integer
Integer n = targetA.get(0);       // LỖI BIÊN DỊCH: chỉ biết chắc Object
Object value = targetA.get(0);    // Hợp lệ nếu danh sách không rỗng
```

**Tại sao đọc ra chỉ an toàn dưới dạng `Object`?** Vì kiểu phần tử thực tế có thể là `Object`, và một `List<Object>` còn có thể chứa `String`, `Double` hoặc bất kỳ đối tượng khác bên cạnh các `Integer` đã thêm. Giới hạn dưới đảm bảo điều kiện **đưa `Integer` vào**, không bảo đảm mọi phần tử có thể đọc ra là `Integer`.

Lưu ý về `set`: ngoài an toàn kiểu, vị trí cần tồn tại và đối tượng `List` phải hỗ trợ thay thế. `? super Integer` không biến một danh sách không thể sửa đổi thành danh sách có thể sửa đổi.

## 4. `<T extends Number>` ràng buộc một kiểu được đặt tên

Thử phương thức chỉ thao tác với một `T`:

```java
static <T extends Number> double twice(T value) {
    return value.doubleValue() * 2;
}
```

`T` được giới hạn bởi `Number`, nên có thể gọi `doubleValue()`. Phương thức chấp nhận `Integer`, `Long`, `Double` và những kiểu phù hợp khác. Nhưng nó **không** tạo quan hệ đặc biệt giữa `List<Integer>` và `List<Number>`:

```java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = integers; // LỖI BIÊN DỊCH: generic vẫn bất biến
```

Quan trọng hơn: ngay cả khi có `T extends Number`, việc thêm phần tử vẫn cần đúng `T`:

```java
static <T extends Number> void put(List<T> list, T value) {
    list.add(value); // Đúng: tham số kiểu của list và value cùng là T
}
```

Không thể chỉ biết `T extends Number` rồi tùy ý thêm một `Double`:

```java
static <T extends Number> void wrong(List<T> list) {
    list.add(3.14); // LỖI BIÊN DỊCH: T có thể là Integer
}
```

Cùng một tên `T` giữ quan hệ nhất quán giữa nhiều vị trí. Phương thức `put` có thể nhận `List<Integer>` với `Integer`, hoặc `List<Double>` với `Double`. Giới hạn trên không đồng nghĩa mọi giá trị kiểu `Number` đều có thể đưa vào một `List<T>` bất kỳ.

## 5. Vì sao không có cú pháp `<T super Integer>`?

Các khai báo sau **sai cú pháp Java**:

```java
// KHÔNG biên dịch:
class InvalidBox<T super Integer> { }
static <T super Number> void invalid(T value) { }
```

Java chỉ có cú pháp giới hạn trên ở phần **khai báo tham số kiểu**, ví dụ:

```java
class NumberBox<T extends Number> {
    private final T value;

    NumberBox(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

Nếu API muốn nhận danh sách có tham số kiểu là `Integer` hoặc kiểu cha của `Integer`, hãy viết giới hạn dưới **ở vị trí sử dụng kiểu**:

```java
static void appendInteger(List<? super Integer> target) {
    target.add(42);
}
```

Hoặc vừa đặt tên kiểu `T`, vừa dùng wildcard giới hạn dưới:

```java
static <T> void append(List<? super T> target, T value) {
    target.add(value);
}
```

Đây là hai lớp ràng buộc khác nhau: `T` liên kết với kiểu của `value`; `? super T` bảo đảm **đích** nhận được các giá trị kiểu `T`.

**Phân biệt với suy luận kiểu:** trong quá trình *type inference*, trình biên dịch có thể dùng những quan hệ ràng buộc kiểu “dưới” hoặc “trên” để giải hệ suy luận. Điều đó không tạo ra cú pháp khai báo mới `<T super X>` trong mã nguồn Java.

## 6. Dùng cả giới hạn trên của T và giới hạn dưới wildcard trong cùng một API

Đây là tình huống rất thường gặp:

```java
static <T extends Number> void copyNumbers(
        List<? super T> destination,
        List<? extends T> source) {
    for (T number : source) {
        destination.add(number);
    }
}
```

Phân tích từng phần:

1. `<T extends Number>`: `T` là kiểu số hợp lệ; có thể dùng các thao tác do `Number` cung cấp.
2. `List<? extends T> source`: **nguồn đọc** có kiểu phần tử là `T` hoặc một kiểu con của `T`; mỗi phần tử đọc ra có thể được xử lý như `T`.
3. `List<? super T> destination`: **đích ghi** có kiểu phần tử là `T` hoặc một kiểu cha của `T`; có thể thêm an toàn mỗi giá trị `T`.
4. `for (T number : source)`: phần tử từ nguồn an toàn để đọc thành `T`, rồi có thể truyền vào `destination.add(number)`.

Ví dụ:

```java
List<Integer> integers = List.of(1, 2);
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();

copyNumbers(numbers, integers); // Đúng
copyNumbers(objects, integers); // Đúng
```

Một lời gọi không hợp lệ:

```java
List<Double> doubles = new ArrayList<>();
copyNumbers(doubles, integers); // LỖI BIÊN DỊCH
```

`List<Double>` không phải đích phù hợp để nhận các phần tử `Integer` từ nguồn này. Trình biên dịch không thể tìm một `T` thỏa đồng thời các quan hệ kiểu mà cả hai tham số yêu cầu.

Có thể viết `<T>` mà không giới hạn `extends Number` nếu phương thức chỉ cần sao chép đối tượng bất kỳ:

```java
static <T> void copyAll(
        List<? super T> destination,
        List<? extends T> source) {
    for (T element : source) {
        destination.add(element);
    }
}
```

Ở đây, giới hạn trên `Number` không cần thiết. Chỉ khai báo những ràng buộc mà thân phương thức hoặc hợp đồng API thật sự cần.

### PECS là cách nhớ hướng sử dụng

**PECS: Producer Extends, Consumer Super.**

- `? extends T` phù hợp cho nguồn cung cấp phần tử để **đọc như T**.
- `? super T` phù hợp cho đích tiêu thụ phần tử để **ghi T**.

PECS là quy tắc thiết kế chữ ký generic, không phải quy tắc cho rằng mọi nguồn `extends` đều không thể sửa đổi hoặc mọi đích `super` chắc chắn sửa được tại runtime.

## 7. Biểu đồ phạm vi kiểu

Giả sử:

```text
Object
  ↑
Number
  ↑
Integer
```

Mũi tên hướng từ kiểu con lên kiểu cha.

Đối với một biến có kiểu `List<? super Integer>`:

```text
Danh sách thực tế có thể nhận:
    List<Integer>
    List<Number>
    List<Object>
    ... hoặc List<supertype phù hợp khác của Integer>

Giá trị an toàn để thêm: Integer (hoặc giá trị kiểu con của Integer)
Kiểu bảo đảm khi đọc: Object
```

Ví dụ `Integer` là lớp `final` nên không có lớp con thực tế nào của nó; cách diễn đạt “hoặc kiểu con” là quy tắc tổng quát khi `T` không phải `final`.

Đối với khai báo `<T extends Number>`:

```text
Kiểu T được chọn:
    Number
    Integer
    Double
    Long
    ... kiểu con phù hợp khác của Number

Có thể dùng giá trị T dưới dạng Number.
Một List<T> chỉ nhận những giá trị được chứng minh tương thích với T.
```

Hai tập quan hệ nằm về **hai hướng khác nhau** đối với bound. Một bên nói về kiểu phần tử ẩn của **vị trí nhận dữ liệu**; bên kia nói về những kiểu cụ thể được phép gán cho **tham số kiểu có tên**.

## 8. Ví dụ Java 17+ độc lập, biên dịch và chạy được

Tạo file `LowerBoundDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class LowerBoundDemo {
    // T bị giới hạn trên bởi Number.
    // Wildcard đích bị giới hạn dưới bởi T.
    static <T extends Number> void copyNumbers(
            List<? super T> destination,
            List<? extends T> source) {
        for (T number : source) {
            destination.add(number);
        }
    }

    public static void main(String[] args) {
        List<Integer> ids = List.of(4, 8);
        List<Number> numbers = new ArrayList<>();
        List<Object> objects = new ArrayList<>();

        copyNumbers(numbers, ids);
        copyNumbers(objects, ids);
        System.out.println("Numbers: " + numbers);
        System.out.println("Objects: " + objects);

        List<? super Integer> sink = numbers;
        sink.add(12);
        System.out.println("After add: " + numbers);

        Object first = sink.get(0);
        System.out.println("Read as Object: " + first);

        List<? super Number> broadSink = objects;
        broadSink.add(3.5);
        System.out.println("Objects after double: " + objects);
    }
}
```

Biên dịch và chạy trong thư mục có file:

```bash
javac -encoding UTF-8 LowerBoundDemo.java
java LowerBoundDemo
```

Đầu ra chính xác:

```text
Numbers: [4, 8]
Objects: [4, 8]
After add: [4, 8, 12]
Read as Object: 4
Objects after double: [4, 8, 3.5]
```

Giải thích:

- `copyNumbers(numbers, ids)` và `copyNumbers(objects, ids)` đều hợp lệ: danh sách đích có tham số kiểu đủ rộng để tiếp nhận phần tử nguồn.
- `List<? super Integer> sink = numbers` cho phép thêm `12` vì `12` được boxing thành `Integer`.
- `sink.get(0)` chỉ có kiểu được bảo đảm là `Object`; danh sách thực tế tình cờ đang chứa số `4`, nhưng compiler không suy ngược kiểu phần tử từ nội dung runtime.
- `List<? super Number> broadSink = objects` cho phép thêm `Double` vì `Double` là `Number` và đích có thể chứa `Number`.

Chương trình không chứa ép kiểu unchecked hay raw type.

## 9. Ví dụ về lỗi thường gặp

### 9.1. Lấy `? super T` làm giới hạn trên của T

```java
// Không hợp lệ: wildcard không thể đứng trực tiếp tại vị trí bound của T.
static <T extends ? super Number> void broken(T x) {}
```

Tham số kiểu được khai báo với **một kiểu giới hạn trên hợp lệ**, không được khai báo bằng wildcard kiểu này. Nếu cần chức năng “đích nhận số”, đặt `? super Number` vào vị trí đối số kiểu của danh sách:

```java
static void addNumber(List<? super Number> list, Number value) {
    list.add(value);
}
```

### 9.2. Nhầm `List<? super Integer>` là `List<Integer>`

```java
List<? super Integer> values = new ArrayList<Object>();
values.add(1);          // Hợp lệ
Integer n = values.get(0); // LỖI BIÊN DỊCH
```

Danh sách thật có thể là `List<Object>` chứa nhiều đối tượng không phải `Integer`. Không thể rút ra `Integer` chỉ từ giới hạn dưới.

### 9.3. Nhầm giới hạn trên của T làm quyền ghi Number

```java
static <T extends Number> void wrong(List<T> values) {
    values.add(3.5); // LỖI BIÊN DỊCH: T có thể là Integer
}
```

Khi cần thêm `Double` cụ thể, hãy nhận `List<? super Double>` hoặc `List<Double>`. Khi cần giữ cùng kiểu `T`, hãy nhận thêm tham số kiểu `T value`.

### 9.4. Nghĩ `super` làm generic “hiệp biến”

```java
List<Integer> ids = new ArrayList<>();
List<Number> numbers = ids; // LỖI BIÊN DỊCH
List<? super Integer> target = ids; // Hợp lệ
```

`List<Integer>` và `List<Number>` vẫn **bất biến** theo tham số kiểu. Wildcard chỉ tạo một **kiểu nhận tham chiếu linh hoạt** với bảo đảm đọc/ghi thích hợp.

### 9.5. Đổi tên T nhưng không liên hệ các vị trí

```java
static <A, B> void unsafe(
        List<? super A> destination, B value) {
    // destination.add(value); // Không thể biên dịch: A và B không có quan hệ.
}
```

Tên tham số kiểu khác nhau không tự tạo quan hệ. Nếu mục tiêu là thêm `value`, hãy dùng cùng `T`:

```java
static <T> void safe(List<? super T> destination, T value) {
    destination.add(value);
}
```

### 9.6. Đánh đồng an toàn kiểu và khả năng sửa đổi

```java
List<? super Integer> target = List.of(1, 2);
target.add(3); // Biên dịch, nhưng ném UnsupportedOperationException
```

Wildcard giới hạn dưới chứng minh kiểu phần tử **phù hợp để chèn**. Nó không bảo đảm đối tượng danh sách cho phép thay đổi.

## 10. Tự kiểm tra

Giả sử đã `import java.util.*;`. Với từng đoạn độc lập, cho biết **biên dịch được hay không**, và nếu được, kiểu nào được bảo đảm khi đọc.

**A — Wildcard super nhận đích rộng**

```java
List<? super Integer> a = new ArrayList<Number>();
a.add(10);
Object x = a.get(0);
```

**B — Đọc thành Integer**

```java
List<? super Integer> b = new ArrayList<Object>();
Integer x = b.get(0);
```

**C — Tham số kiểu có giới hạn dưới**

```java
class Box<T super Integer> {}
```

**D — Tham số kiểu có giới hạn trên**

```java
static <T extends Number> double toDouble(T x) {
    return x.doubleValue();
}
```

**E — Ghi Number vào List<? super Integer>**

```java
List<? super Integer> e = new ArrayList<Number>();
Number n = 3.5;
e.add(n);
```

**F — Ghi Integer vào List<? super Number>**

```java
List<? super Number> f = new ArrayList<Object>();
f.add(3);
```

**G — Phối hợp type parameter và wildcard**

```java
static <T extends Number> void store(
        List<? super T> target, T value) {
    target.add(value);
}
List<Object> objects = new ArrayList<>();
store(objects, Integer.valueOf(9));
```

Trong ví dụ D và G, các phương thức `static` phải được khai báo trong một lớp hoặc ngữ cảnh phù hợp; đây là mảnh mã minh họa, không phải tệp Java chạy độc lập.

**Đáp án:**

- **A:** Hợp lệ; thêm `Integer` vào danh sách chấp nhận `Integer`, đọc an toàn dưới kiểu `Object`.
- **B:** Không biên dịch: `get` trả về kiểu chỉ được bảo đảm là `Object`.
- **C:** Không biên dịch: Java không có cú pháp `<T super ...>`.
- **D:** Hợp lệ khi đặt trong khai báo lớp thích hợp; `T` bị giới hạn trên bởi `Number`, cho phép gọi `doubleValue()`.
- **E:** Không biên dịch: một `Number` bất kỳ có thể là `Double`, trong khi `List<? super Integer>` có thể trỏ tới `List<Integer>`.
- **F:** Hợp lệ: `Integer` là `Number` và mọi danh sách `List<? super Number>` đều nhận được `Number`.
- **G:** Hợp lệ: `T` có thể được suy luận là `Integer` (hoặc kiểu phù hợp), còn `List<Object>` là đích có khả năng chứa giá trị đó.

## 11. Ghi nhớ

**`? super T` là giới hạn dưới của wildcard tại vị trí đối số kiểu**: biểu diễn một kiểu phần tử chưa biết nhưng đủ rộng để nhận các giá trị `T`; đọc ra chỉ bảo đảm `Object`. **`<T extends U>` là giới hạn trên của tham số kiểu được đặt tên**: ràng buộc `T` thuộc họ `U` và cho phép liên kết nhiều vị trí trong cùng một khai báo.

Java không hỗ trợ `<T super U>` ở vị trí khai báo tham số kiểu. Khi cần cả hai cơ chế, dùng cú pháp kết hợp như `<T extends Number> void copy(List<? super T> dest, List<? extends T> src)` để mô tả chính xác giá trị được đọc từ nguồn và được chấp nhận bởi đích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lower-bounded-wildcards-006">Quan hệ giữa ? super Integer và ? super Number</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa `? super Integer` và `? super Number` trong Java Generics

Hai kiểu `List<? super Integer>` và `List<? super Number>` trông rất giống nhau, nhưng **không tương đương**. Vì `Integer` là kiểu con của `Number`, quan hệ tương thích giữa hai wildcard cận dưới đi theo hướng:

```text
Integer <: Number

List<? super Number>  <:  List<? super Integer>
          |                         |
          +-------- gán được ------>+
```

Có thể gán một giá trị kiểu `List<? super Number>` cho biến kiểu `List<? super Integer>`. **Chiều ngược lại không hợp lệ.** Đây là một hệ quả của quy tắc *type argument containment* đối với wildcard cận dưới (`super`), thường được mô tả trực quan là hành vi phản biến (*contravariant*) ở vị trí wildcard.

Phải hiểu đúng câu này: `List<T>` thông thường vẫn **bất biến**. Không có quy tắc `List<Number>` là kiểu con của `List<Integer>`. Tính linh hoạt ở đây xuất hiện vì cả hai vế đều dùng wildcard cận dưới, không phải vì Java biến toàn bộ generic thành phản biến.

Bài học cần làm rõ ba việc: đối số kiểu nào có thể nằm sau mỗi `? super`, hai kiểu wildcard chuyển đổi được theo hướng nào, và vì sao quyền thêm/đọc qua chúng khác nhau.

## 1. `? super T` có nghĩa là gì?

Trong Java, khai báo:

```java
List<? super Integer> integerSink;
```

nói rằng biến tham chiếu một `List<X>` với một kiểu đối số `X` chưa được biết chính xác, nhưng `X` là **`Integer` hoặc một kiểu cha của `Integer`**. Ví dụ những kiểu đối số phù hợp gồm `Integer`, `Number`, `Object` và cả những giao diện cha mà `Integer` triển khai, chẳng hạn `Comparable<Integer>` hoặc `java.io.Serializable`.

Ngược lại:

```java
List<? super Number> numberSink;
```

yêu cầu `X` là **`Number` hoặc một kiểu cha của `Number`**, chẳng hạn `Number`, `Object`, hoặc `java.io.Serializable`.

Điểm quan trọng: “kiểu cha” trong quan hệ subtype của Java không chỉ là class được kế thừa qua `extends` mà còn gồm interface được triển khai qua `implements`. Một danh sách `List<Serializable>` cũng nhận được `Number` vì `Number implements Serializable`.

Ví dụ minh họa (các biến nằm trong thân một phương thức, với các import từ `java.util` và `java.io.Serializable`):

```java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();
List<Serializable> serializables = new ArrayList<>();
List<Comparable<Integer>> comparables = new ArrayList<>();

List<? super Integer> a = integers;
List<? super Integer> b = numbers;
List<? super Integer> c = objects;
List<? super Integer> d = serializables;
List<? super Integer> e = comparables;

List<? super Number> f = numbers;
List<? super Number> g = objects;
List<? super Number> h = serializables;

// List<? super Number> invalid = integers;   // Không biên dịch.
// List<? super Number> invalid2 = comparables; // Không biên dịch.
```

`List<Integer>` không phù hợp với `List<? super Number>`: `Integer` là **kiểu con**, không phải kiểu cha của `Number`. `Comparable<Integer>` cũng không là kiểu cha của `Number`, dù nó là một interface mà `Integer` thực hiện.

Trong API thực tế, loại tham số này thường được dùng như một **đích nhận dữ liệu** (consumer): `? super T` cho phép truyền các giá trị kiểu `T` vào một collection có kiểu phần tử được khai báo rộng hơn.

## 2. Tập các kiểu đối số hợp lệ giải thích chiều quan hệ

Có thể xem mỗi wildcard cận dưới như một điều kiện đặt lên đối số kiểu thực tế:

- Với `? super Integer`: đối số phải là `Integer` **hoặc một kiểu cha của `Integer`**.
- Với `? super Number`: đối số phải là `Number` **hoặc một kiểu cha của `Number`**.

Vì mọi kiểu cha của `Number` cũng là kiểu cha của `Integer` (do `Integer extends Number`), nên tập các kiểu đối số thỏa `? super Number` **nằm trong** tập thỏa `? super Integer`.

```text
Các đối số ví dụ được chấp nhận bởi ? super Integer:
    Integer, Number, Object, Serializable, Comparable<Integer>, ...

Các đối số ví dụ được chấp nhận bởi ? super Number:
             Number, Object, Serializable, ...

Tập (? super Number) là tập con của (? super Integer)
```

Đây là cách trực quan để hiểu vì sao một `List<? super Number>` có thể được nhìn qua tham chiếu `List<? super Integer>`: nếu collection được bảo đảm nhận `Number` thì tất nhiên nó cũng nhận mọi `Integer`.

Tuy nhiên, sơ đồ tập hợp trên chỉ minh họa điều kiện đối số kiểu, không phải định nghĩa hình thức rằng wildcard là một collection runtime hay hai danh sách được “chuyển đổi” thành nhau. Kiểm tra tương thích thực hiện tại **thời điểm biên dịch**, dựa trên luật bao hàm đối số kiểu (*containment*) và quy tắc gán của Java.

## 3. Gán một `List<? super Number>` cho `List<? super Integer>`

Ví dụ tối giản:

```java
List<? super Number> numberSink = new ArrayList<Number>();
List<? super Integer> integerSink = numberSink; // Hợp lệ.
```

Giải thích theo hợp đồng:

1. `numberSink` nhận được mọi đối tượng `Number`, kể cả `Integer`, `Double`, `Long`.
2. `integerSink` chỉ yêu cầu nơi nhận được đối tượng `Integer`.
3. Một danh sách nhận được `Number` đáp ứng yêu cầu nhận `Integer`, nên phép gán an toàn.

Nhưng sau phép gán, **quyền thao tác được xác định theo kiểu tĩnh của từng biến**:

```java
numberSink.add(1);
numberSink.add(2.5); // Hợp lệ: Double là Number.

integerSink.add(3);  // Hợp lệ: Integer.
// integerSink.add(4.5); // Không biên dịch: kiểu tĩnh là ? super Integer.
```

Dù cả hai biến trỏ đến cùng đối tượng danh sách, compiler không tự “nhớ” rằng `integerSink` được khởi tạo từ `numberSink`. Một khi tham chiếu được khai báo là `List<? super Integer>`, nó chỉ cung cấp hợp đồng hẹp hơn: **thêm được `Integer`**.

### Ví dụ hoàn chỉnh

Lưu thành `SuperAssignmentDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class SuperAssignmentDemo {
    public static void main(String[] args) {
        List<? super Number> numberSink = new ArrayList<Number>();
        numberSink.add(2.5);

        List<? super Integer> integerSink = numberSink;
        integerSink.add(10);
        integerSink.add(20);

        // integerSink.add(3.5); // Không biên dịch.

        System.out.println(numberSink);
        System.out.println(integerSink);

        Object item = integerSink.get(0);
        System.out.println(item);
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac SuperAssignmentDemo.java
java SuperAssignmentDemo
```

Kết quả:

```text
[2.5, 10, 20]
[2.5, 10, 20]
2.5
```

Hai biến in cùng nội dung vì trỏ đến cùng một `ArrayList`. Giá trị đọc từ `integerSink.get(0)` chỉ bảo đảm có kiểu tĩnh dùng được như `Object`, nên dù phần tử đầu là `Double`, lời gọi vẫn hoàn toàn an toàn.

## 4. Vì sao không được gán ngược lại?

Phép gán dưới đây bị từ chối:

```java
List<? super Integer> integerSink = new ArrayList<Integer>();
// List<? super Number> numberSink = integerSink; // LỖI BIÊN DỊCH.
```

Hãy thử tưởng tượng phép gán được cho phép:

```java
List<Integer> realList = new ArrayList<>();
List<? super Integer> integerSink = realList;

// GIẢ ĐỊNH SAI — Java từ chối dòng dưới:
// List<? super Number> numberSink = integerSink;

// Nếu dòng trên được phép:
 // numberSink.add(3.14); // Double là Number.
 // realList sẽ chứa Double dù phải là List<Integer>.
```

`List<? super Integer>` có thể trỏ đến `List<Integer>`. Một `List<Integer>` **không** được thêm một `Double`. Nhưng `List<? super Number>` có hợp đồng cho phép thêm `Double`. Gán ngược sẽ khiến compiler cho phép một thao tác có thể phá an toàn kiểu của danh sách thật.

Đây chính là lý do quan hệ chỉ có một chiều:

```text
List<? super Number>  --->  List<? super Integer>   ĐÚNG
List<? super Integer> --->  List<? super Number>    SAI
```

Không thể suy ra chiều gán từ tên kiểu nằm ngay sau `super` một cách máy móc. Phải hỏi: **kiểu tham chiếu đích hứa được thêm những đối tượng nào, và nguồn có bảo đảm khả năng ấy trong mọi trường hợp không?**

## 5. Bảng quyền thêm phần tử và đọc phần tử

| Kiểu biến | Một số kiểu danh sách thực tế có thể tham chiếu | Giá trị non-null có thể thêm qua biến | Kiểu an toàn khi đọc bằng `get` |
| --- | --- | --- | --- |
| `List<? super Integer>` | `List<Integer>`, `List<Number>`, `List<Object>`, `List<Serializable>`, ... | `Integer` | `Object` |
| `List<? super Number>` | `List<Number>`, `List<Object>`, `List<Serializable>`, ... | `Number` và các kiểu con (`Integer`, `Double`, ...) | `Object` |
| `List<Integer>` | `List<Integer>` | `Integer` | `Integer` |
| `List<Number>` | `List<Number>` | `Number` và các kiểu con | `Number` |
| `List<?>` | Bất kỳ `List<T>` phù hợp | Không thêm tùy ý một giá trị non-null | `Object` |

Cả hai wildcard `? super` đều cho phép truyền **literal `null`** về mặt hệ kiểu; nhưng việc collection thật có hỗ trợ `add` và chấp nhận `null` là vấn đề riêng.

### 5.1. Quyền ghi (`add`)

Với `List<? super Number>`, compiler có thể khẳng định mọi danh sách hợp lệ đều nhận `Number` và các kiểu con:

```java
static void addNumbers(List<? super Number> target) {
    target.add(1);    // Integer là Number.
    target.add(2.5);  // Double là Number.
    target.add(3L);   // Long là Number.
}
```

Với `List<? super Integer>`, compiler chỉ bảo đảm nhận `Integer`. `Integer` là class `final`, nên không có class con để bổ sung vào lựa chọn này:

```java
static void addIntegers(List<? super Integer> target) {
    target.add(1);
    target.add(2);

    // target.add(2.5); // Không biên dịch.
}
```

Hai đoạn là phương thức minh họa, cần đặt trong một class có `import java.util.List;` nếu biên dịch riêng.

Lưu ý: “biên dịch được” khác “chắc chắn thêm thành công”. Một `List.of(...)` không sửa đổi vẫn có thể phù hợp về kiểu để truyền vào phương thức trên, nhưng `add` sẽ ném `UnsupportedOperationException`. Quy tắc generic xác định tương thích kiểu, không bảo đảm tính khả biến (*mutability*) của collection.

### 5.2. Quyền đọc (`get`)

`? super Number` không có nghĩa đọc được `Number`. Một `List<? super Number>` có thể trỏ đến `List<Object>`, hoặc `List<Serializable>` chứa đối tượng không phải `Number`. Vì thế:

```java
List<? super Number> numbers = new ArrayList<Object>();
Object first = numbers.isEmpty() ? null : numbers.get(0); // Hợp lệ.

// Number n = numbers.get(0); // Không biên dịch.
```

Tương tự:

```java
List<? super Integer> integers = new ArrayList<Number>();
Object first = integers.isEmpty() ? null : integers.get(0);

// Integer i = integers.get(0); // Không biên dịch.
```

Cận dưới cung cấp bảo đảm về **đối tượng có thể đưa vào**, không cung cấp bảo đảm chặt hơn `Object` cho **đối tượng lấy ra**. Khi cần đọc số và gọi `doubleValue()`, dùng `List<? extends Number>` (producer) hoặc `List<Number>` nếu phù hợp, thay vì dựa vào `? super Number`.

## 6. Ví dụ đầy đủ: dùng hai phương thức consumer

Lưu thành `LowerBoundConsumerDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class LowerBoundConsumerDemo {
    static void putIntegers(List<? super Integer> destination) {
        destination.add(10);
        destination.add(20);
    }

    static void putNumbers(List<? super Number> destination) {
        destination.add(30);
        destination.add(4.5);
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>();
        List<Number> numbers = new ArrayList<>();
        List<Object> objects = new ArrayList<>();

        putIntegers(integers);
        putIntegers(numbers);
        putIntegers(objects);

        putNumbers(numbers);
        putNumbers(objects);

        // putNumbers(integers); // LỖI BIÊN DỊCH.

        System.out.println("integers=" + integers);
        System.out.println("numbers=" + numbers);
        System.out.println("objects=" + objects);
    }
}
```

```bash
javac LowerBoundConsumerDemo.java
java LowerBoundConsumerDemo
```

Kết quả:

```text
integers=[10, 20]
numbers=[10, 20, 30, 4.5]
objects=[10, 20, 30, 4.5]
```

Vì `putIntegers` chỉ thêm `Integer`, bất kỳ `List<Integer>`, `List<Number>` hoặc `List<Object>` đều đủ khả năng nhận. `putNumbers` còn có thể thêm `Double`, nên `List<Integer>` không đáp ứng hợp đồng.

Cùng một đối tượng `List<Number>` có thể truyền cho cả hai phương thức, vì nó đủ rộng để nhận mọi `Number`. Điều đó không làm cho `List<Integer>` và `List<Number>` trở thành kiểu con của nhau.

## 7. Phép gán theo chiều `super` là quan hệ kiểu, không phải chuyển dữ liệu

Xét:

```java
List<Number> source = new ArrayList<>();
List<? super Number> numberSink = source;
List<? super Integer> integerSink = numberSink;
```

Ba biến cùng trỏ tới **một đối tượng**, không có thao tác chuyển đổi các phần tử sang kiểu khác, không tạo danh sách mới, và không duyệt collection.

`numberSink` cho phép thêm `Number`; `integerSink` chỉ cho phép thêm `Integer`. Nếu dữ liệu được thêm qua `numberSink` thì có thể quan sát nó qua `integerSink` bằng `get`, nhưng chỉ dưới kiểu `Object`.

Đây là bản chất của **cách nhìn (view) theo kiểu tĩnh**: cùng đối tượng runtime có thể được tham chiếu qua những khai báo generic có hợp đồng khác nhau. Compiler áp dụng hợp đồng của từng biến tại nơi sử dụng.

Một ví dụ ngắn:

```java
numberSink.add(1.5);
integerSink.add(2);
System.out.println(source); // [1.5, 2]
```

Nếu khai báo `integerSink` được phép “tự hiểu” nó luôn là `List<Number>` vì từng được gán từ `numberSink`, quy tắc sẽ trở nên phụ thuộc lịch sử gán của biến và không an toàn cho các giá trị khác đến từ tham số phương thức. Java kiểm tra theo loại tĩnh của biểu thức tại vị trí dùng.

## 8. `? super Number` và `? super Integer` không phải `List<Number>` hay `List<Integer>`

Phân biệt hai nhóm:

**Kiểu tham số hóa chính xác:**

```java
List<Number> exactNumbers = new ArrayList<>();
List<Integer> exactIntegers = new ArrayList<>();

// List<Number> invalid = exactIntegers; // Không biên dịch.
```

**Kiểu tham số hóa có wildcard:**

```java
List<? super Integer> anyIntegerDestination = exactIntegers;
List<? super Integer> anotherIntegerDestination = exactNumbers;

List<? super Number> anyNumberDestination = exactNumbers;
```

Cũng không được gán wildcard ngược thành đối số cụ thể:

```java
List<? super Number> wildcard = new ArrayList<Number>();

// List<Number> exact = wildcard; // Không biên dịch.
```

Lý do: biến `wildcard` còn có thể chứa một `List<Object>` hoặc `List<Serializable>`, vì vậy compiler không thể bảo đảm đối tượng thật có kiểu chính xác `List<Number>`. Việc nó được khởi tạo ngay trong ví dụ bằng `new ArrayList<Number>()` không thay đổi hợp đồng của biến `wildcard`.

Tương tự, `List<? super Integer>` không phải `List<Integer>` và cũng không tự trở thành `List<Number>`.

## 9. Liên hệ trực tiếp với nguyên tắc PECS

PECS viết tắt của **Producer Extends, Consumer Super**:

- **Producer:** tham số cung cấp đối tượng cho chương trình (chủ yếu đọc) → dùng `? extends T` khi phù hợp.
- **Consumer:** tham số nhận những đối tượng do chương trình đưa vào (chủ yếu ghi) → dùng `? super T` khi phù hợp.

Hãy xác định loại giá trị cụ thể phương thức cần **ghi** trước khi chọn cận dưới:

| Nhu cầu của phương thức | Kiểu tham số thích hợp | Lý do |
| --- | --- | --- |
| Thêm các `Integer` | `List<? super Integer>` | Nhận được `List<Integer>`, `List<Number>`, `List<Object>` và các supertypes khác |
| Thêm nhiều loại `Number` | `List<? super Number>` | Cho phép ghi `Integer`, `Double`, `Long`, ... |
| Chỉ đọc các đối tượng `Number` | `List<? extends Number>` | Đọc ra chắc chắn dùng được như `Number` |
| Vừa đọc vừa ghi chính xác một kiểu `T` | `List<T>` hoặc generic method | Giữ quan hệ giữa kiểu phần tử đọc ra và kiểu phần tử ghi vào |

Ví dụ nếu phương thức thực tế chỉ thêm `Integer` nhưng khai báo tham số `List<? super Number>`, nó vô tình loại bỏ `List<Integer>` khỏi tập caller hợp lệ. Khi đổi sang `List<? super Integer>`, API **rộng hơn về các loại danh sách có thể nhận**, trong khi vẫn đảm bảo lời gọi `add(Integer)` an toàn.

Ngược lại, nếu nội dung phương thức cần thêm `Double` và `Integer`, chuyển nó từ `List<? super Number>` sang `List<? super Integer>` sẽ khiến code bên trong không còn biên dịch. Chọn cận dưới căn cứ vào **loại giá trị cần ghi**, không dựa vào quan hệ kế thừa giữa những kiểu được đặt tên một cách tùy tiện.

## 10. Một phương thức có thể nhận `? super Integer` nhưng truyền tiếp sang `? super Number` không?

Giả sử có hai API:

```java
static void acceptIntegers(List<? super Integer> target) {
    target.add(100);
}

static void acceptNumbers(List<? super Number> target) {
    target.add(1.25);
}
```

Ta có thể viết:

```java
static void forwardNumbers(List<? super Number> target) {
    acceptIntegers(target); // Hợp lệ.
    acceptNumbers(target);  // Hợp lệ.
}
```

Nhưng chiều ngược lại không hợp lệ:

```java
static void forwardIntegers(List<? super Integer> target) {
    acceptIntegers(target); // Hợp lệ.
    // acceptNumbers(target); // Không biên dịch.
}
```

Tại sao? `forwardIntegers` có thể nhận trực tiếp `List<Integer>`. Nếu nó gọi `acceptNumbers`, phương thức kia có thể thêm `Double` vào danh sách thật sự chỉ chứa `Integer`. Compiler cấm chuyển tiếp nguy hiểm này.

Đây là tình huống rất thường gặp khi kết nối các hàm service hoặc helper: một API dùng cận dưới **rộng hơn về loại giá trị nó ghi** yêu cầu đích có khả năng tiếp nhận nhiều kiểu hơn, vì thế chỉ có thể nhận một tập đối số caller **hẹp hơn**.

## 11. Generic method và `Collections.copy`: áp dụng hai chiều dữ liệu

Một ví dụ thực tế về kết hợp producer/consumer là sao chép phần tử từ source sang destination. Phương thức Java có thể viết:

```java
static <T> void appendAll(
        List<? extends T> source,
        List<? super T> destination) {
    for (T item : source) {
        destination.add(item);
    }
}
```

Nếu source là `List<Integer>`, destination có thể là `List<Integer>`, `List<Number>` hoặc `List<Object>`. Nếu source là `List<Number>` có chứa cả `Double`, destination cần đủ khả năng nhận các `Number`.

Lưu thành `AppendAllDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class AppendAllDemo {
    static <T> void appendAll(
            List<? extends T> source,
            List<? super T> destination) {
        for (T value : source) {
            destination.add(value);
        }
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(1, 2);
        List<Number> numbers = new ArrayList<>();
        List<Object> objects = new ArrayList<>();

        appendAll(integers, numbers);
        appendAll(numbers, objects);

        System.out.println(numbers);
        System.out.println(objects);
    }
}
```

```bash
javac AppendAllDemo.java
java AppendAllDemo
```

Kết quả:

```text
[1, 2]
[1, 2]
```

Phương thức là ví dụ minh họa cùng quy tắc với chữ ký `Collections.copy(List<? super T> dest, List<? extends T> src)`, nhưng **hành vi khác nhau**: `appendAll` dùng `add` để nối phần tử và tăng kích thước, trong khi `Collections.copy` thay thế các phần tử đang có trong đích và yêu cầu đích đủ dài. Không thay thế lẫn nhau một cách máy móc.

Chỉ cần nhớ quan hệ khi chọn `T`: destination phải có kiểu phần tử là `T` hoặc kiểu cha của `T`. Nếu `T` là `Number`, một `List<Integer>` không thể nhận toàn bộ `Number`; nếu `T` là `Integer`, `List<Number>` hoàn toàn nhận được.

## 12. Bẫy do cách hiểu “super” theo tiếng tự nhiên

Một câu suy luận sai khá phổ biến:

> “`Number` là kiểu cha của `Integer`, vậy `List<? super Number>` chắc phải bao trùm nhiều loại danh sách hơn `List<? super Integer>`.”

Thực tế, yêu cầu `X super Number` **khắt khe hơn** yêu cầu `X super Integer`. Chỉ những `X` rộng đến mức chứa được mọi `Number` mới thỏa `? super Number`, trong khi `? super Integer` chỉ cần `X` rộng đủ để chứa mọi `Integer`.

Ví dụ:

```java
List<Integer> onlyIntegers = new ArrayList<>();

List<? super Integer> accepted = onlyIntegers; // Đúng.
// List<? super Number> rejected = onlyIntegers; // Sai.
```

`List<? super Integer>` **chấp nhận nhiều loại đối số kiểu thực tế hơn** (ví dụ có thể nhận `List<Integer>` và `List<Number>`), nhưng **cho phép thêm ít loại giá trị hơn** qua biến (`Integer` thay vì mọi `Number`).

Ngược lại, `List<? super Number>` **chấp nhận ít loại danh sách thực tế hơn**, nhưng **cho phép thêm nhiều loại giá trị hơn** (`Integer`, `Double`, `Long`, ...).

Đây là cặp đánh đổi quan trọng:

| Khía cạnh | `? super Integer` | `? super Number` |
| --- | --- | --- |
| Cận dưới | `Integer` | `Number` |
| Các danh sách caller hợp lệ | Rộng hơn | Hẹp hơn |
| Giá trị non-null có thể ghi | Chỉ `Integer` | Mọi `Number` (và kiểu con) |
| Đọc ra an toàn | `Object` | `Object` |
| Chuyển thành biến kiểu kia | Không tự chuyển sang `? super Number` | Có thể chuyển sang `? super Integer` |

## 13. Các lỗi biên dịch thường gặp và cách sửa

### Lỗi 1: đảo chiều phép gán

```java
List<? super Integer> ints = new ArrayList<Integer>();
// List<? super Number> nums = ints; // Sai.
```

**Cách sửa:** nếu code cần ghi các giá trị `Number` nói chung, hãy yêu cầu ngay từ đầu một đích có kiểu `List<? super Number>`. Nếu code chỉ cần ghi `Integer`, giữ `List<? super Integer>`.

### Lỗi 2: cho rằng `List<? super Integer>` luôn là `List<Number>`

```java
List<? super Integer> target = new ArrayList<Integer>();
// List<Number> exact = target; // Sai.
```

**Cách sửa:** nếu chương trình cần một `List<Number>` thật, tạo hoặc nhận một danh sách khai báo chính xác `List<Number>`. Wildcard không cho biết kiểu đối số cụ thể của danh sách thật.

### Lỗi 3: đọc từ `? super Number` rồi gọi ngay API của `Number`

```java
List<? super Number> target = new ArrayList<Object>();
// Number value = target.get(0); // Sai.
Object value = target.isEmpty() ? null : target.get(0); // Đúng.
```

**Cách sửa:** nếu nhiệm vụ là đọc các `Number` để tính toán, cân nhắc `List<? extends Number>`; nếu cần kiểu phần tử chính xác của nguồn, dùng generic method phù hợp. Không ép kiểu một cách mù quáng.

### Lỗi 4: suy từ kiểu biến sang khả năng sửa đổi runtime

```java
List<? super Number> target = List.of(1, 2);
// target.add(3.5); // Biên dịch được nếu bỏ comment, nhưng runtime ném
                   // UnsupportedOperationException.
```

**Cách sửa:** chỉ gọi `add` khi hợp đồng nguồn dữ liệu đảm bảo collection có thể sửa được; có thể dùng một `ArrayList` đích mới khi cần sở hữu và cập nhật danh sách.

### Lỗi 5: nghĩ `? super T` bảo đảm mọi phần tử hiện hữu là `T`

```java
List<Object> rawContents = new ArrayList<>();
rawContents.add("not a number");

List<? super Number> numbers = rawContents;
numbers.add(42);

Object first = numbers.get(0); // "not a number", không phải Number.
```

**Cách sửa:** quan hệ cận dưới chỉ xác định đối tượng **có thể thêm**, không giới hạn những đối tượng đã có trong một danh sách kiểu cha. Nếu cần mọi phần tử là `Number`, một khai báo source kiểu `List<? extends Number>` hoặc `List<Number>` mới phù hợp.

### Lỗi 6: ép kiểu để lách quan hệ gán

Ví dụ cố ép `(List<? super Number>) (List<?>) integerList` đánh mất bằng chứng an toàn kiểu và có thể tạo cảnh báo `unchecked`. Nếu thêm `Double` vào danh sách thật `List<Integer>`, lỗi có thể phát sinh muộn khi đọc dưới kiểu `Integer`. Không dùng raw type hoặc cast unchecked để “sửa” lỗi gán wildcard.

## 14. Kiểm tra kiến thức

**Câu 1.** `Integer extends Number`; phép gán nào hợp lệ?

```java
List<? super Number> nums = new ArrayList<Number>();
List<? super Integer> ints = nums; // Đúng.

// List<? super Number> other = ints; // Sai.
```

Lời gán đầu tiên đúng vì mọi nơi nhận `Number` đều nhận `Integer`. Lời gán ngược sai vì `ints` có thể chỉ là `List<Integer>`.

**Câu 2.** `List<? super Integer>` có truyền được vào `void addNumbers(List<? super Number> x)` không?

**Không**, vì phương thức có thể thêm `Double`. Nhưng `List<? super Number>` có truyền được vào `void addIntegers(List<? super Integer> x)`.

**Câu 3.** `List<? super Number>` cho đọc ra một biến `Number` không?

**Không có bảo đảm đó.** Danh sách thật có thể là `List<Object>` có các đối tượng không phải `Number`. Chỉ chắc chắn đọc được như `Object`.

**Câu 4.** `List<Serializable>` có phù hợp với `List<? super Number>` không?

**Có**, vì class `Number` triển khai `Serializable`. Cận dưới xét cả quan hệ interface, không chỉ chuỗi superclass.

**Câu 5.** `List<? super Integer>` có “mạnh hơn” hay “yếu hơn” `List<? super Number>`?

Không có một từ duy nhất mô tả mọi thao tác. `? super Integer` nhận được **nhiều loại đích hơn** nhưng chỉ bảo đảm ghi `Integer`; `? super Number` nhận **ít loại đích hơn** nhưng cho ghi mọi `Number`. Về chiều gán, `List<? super Number>` tương thích với `List<? super Integer>`.

## 15. Quy tắc ghi nhớ

1. Nếu `A` là kiểu con của `B`, thì theo hướng wildcard cận dưới: `List<? super B>` có thể gán cho `List<? super A>`.
2. `List<? super Integer>` nhận các danh sách có kiểu phần tử là `Integer` hoặc một kiểu cha của `Integer`. Nó cho ghi `Integer`.
3. `List<? super Number>` nhận các danh sách có kiểu phần tử là `Number` hoặc một kiểu cha của `Number`. Nó cho ghi mọi `Number`.
4. Cả hai kiểu đều chỉ bảo đảm đọc phần tử dưới dạng `Object`.
5. Generic chính xác như `List<Integer>` và `List<Number>` vẫn bất biến, không tự có quan hệ kế thừa theo đối số kiểu.
6. Chọn cận `super` theo **loại giá trị hàm cần thêm**, đồng thời kiểm tra riêng khả năng sửa đổi của collection runtime.

Cách tự kiểm tra nhanh nhất trước mỗi phép gán là đặt câu hỏi: “Nếu chương trình chỉ biết kiểu biến ở **vế trái**, nó được phép thêm đối tượng nào, và danh sách ở **vế phải** có chắc nhận được mọi đối tượng đó không?” Nếu câu trả lời luôn có, phép gán mới an toàn về kiểu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lower-bounded-wildcards-007">Áp dụng cho đầu ra nhận dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Áp dụng `? super T` cho đầu ra nhận dữ liệu trong Java Generics

Khi một phương thức **đưa các giá trị kiểu `T` vào một đối tượng đích** (destination/consumer), tham số đích thường nên khai báo bằng **lower-bounded wildcard `? super T`**. Cách này cho phép phương thức ghi `T` vào collection có đối số kiểu chính xác là `T` **hoặc một kiểu cha của `T`**, đồng thời vẫn được Java kiểm tra an toàn kiểu khi biên dịch.

Ví dụ, một phương thức chuyên thêm `Integer` không nhất thiết phải yêu cầu `List<Integer>`: nó cũng có thể thêm `Integer` vào `List<Number>` và `List<Object>`. Chữ ký `List<? super Integer>` thể hiện chính xác nhu cầu đó.

Đây là phần **Consumer Super** trong nguyên tắc **PECS — Producer Extends, Consumer Super**. “Đầu ra nhận dữ liệu” trong bài này chỉ **tham số đích được phương thức ghi phần tử vào**, không bắt buộc là giá trị `return` của phương thức.

## 1. Thế nào là đầu ra nhận dữ liệu?

Một tham số là **consumer** trong một thao tác khi nó **nhận các đối tượng do phương thức cung cấp**, chẳng hạn:

- Danh sách đích được thêm các `Integer` mới.
- Collection đích nhận phần tử đọc từ collection nguồn.
- Hàng đợi nhận các công việc mang kiểu `T`.
- Hàm xử lý (ví dụ `Consumer<? super T>`) nhận giá trị `T` để thực hiện tác vụ.
- `Comparator<? super T>` nhận các phần tử `T` để so sánh, dù nó không lưu chúng.

Vai trò producer/consumer được xác định **theo hướng luân chuyển dữ liệu trong API**. Một `ArrayList<Number>` có thể là *consumer* khi nhận số từ nguồn, và trở thành *producer* khi một phương thức khác duyệt các số đã có.

Ví dụ phương thức chỉ ghi:

```java
static void addDefaults(List<? super Integer> destination) {
    destination.add(10);
    destination.add(20);
}
```

Các lời gọi hợp lệ về mặt kiểu:

```java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();

addDefaults(integers);
addDefaults(numbers);
addDefaults(objects);
```

`destination` chỉ cần khả năng tiếp nhận `Integer`. Không có lý do buộc người gọi xây dựng đúng một `List<Integer>` nếu danh sách đích rộng hơn vẫn lưu được `Integer` an toàn.

## 2. Vì sao một tham số consumer không nên bị cố định thành `List<Integer>`?

API chỉ cần gửi một `Integer` vào đích thì đích có thể là `List<Integer>`, `List<Number>` hoặc `List<Object>`. Java Generics bất biến nên `List<Number>` không gán trực tiếp cho `List<Integer>`; nếu dùng chữ ký quá hẹp, caller phải tạo list mới không cần thiết hoặc tìm cách cast.

~~~java
static void sendTen(List<? super Integer> destination) {
    destination.add(10);
}
~~~

Chữ ký này **mở rộng lựa chọn destination mà không nới lỏng kiểu phần tử được ghi**. Bản thân destination có thể đã chứa phần tử kiểu rộng hơn; method trên không cần biết hay đọc chúng. Đây là trường hợp consumer rõ ràng nhất trước khi xét callback, comparator và kết hợp nguồn/đích.

## 3. Chọn cận dưới theo giá trị mà đầu ra phải nhận

Đừng đọc `? super Integer` thành “cho phép gửi bất kỳ `Number` vào danh sách”. Một `List<? super Integer>` có thể chính là `List<Integer>`, nên lời gọi `destination.add(2.5)` không an toàn. Nếu cần gửi cả `Integer` lẫn `Double`, chọn `List<? super Number>` để bên nhận bảo đảm chứa được các `Number`.

~~~java
List<Number> numbers = new ArrayList<>();
List<? super Integer> integerOnly = numbers;
integerOnly.add(7);
// integerOnly.add(2.5); // Không biên dịch.

numbers.add(2.5);       // Được, nhờ kiểu tham chiếu List<Number>.
~~~

Hai tham chiếu dùng chung đối tượng nhưng **mỗi biến có quyền thao tác theo kiểu tĩnh của chính nó**. Chọn cận `T` theo loại dữ liệu method định ghi, không theo lớp runtime của một đối tượng list tình cờ được truyền vào.

## 4. Đầu ra có thể nhận `T`, nhưng không chắc sản xuất ra `T`

Giới hạn dưới của wildcard chỉ chứng minh **đích nhận được những giá trị `T`**. Nếu `List<? super Integer>` trỏ tới `List<Object>` chứa `String` cũ, `get(i)` không thể được compiler xem là `Integer` hay `Number`:

~~~java
List<Object> actual = new ArrayList<>();
actual.add("old");
List<? super Integer> destination = actual;
destination.add(42);

Object item = destination.get(0); // "old"
// Integer wrong = destination.get(0); // Lỗi biên dịch.
~~~

`set(i, 99)` được phép về kiểu nhưng cần index hợp lệ và list cho phép sửa; `add(null)` được compiler chấp nhận nhưng implementation có thể cấm null. Nếu cần kết quả đọc có kiểu mạnh, hãy dùng một tham số nguồn phù hợp hoặc xác minh **từng giá trị** bằng `instanceof`. `? super T` không phải một lời hứa về nội dung có sẵn hay mutability.

## 5. Vì sao `super` thiên về đầu ra nhận dữ liệu?

Một API nhận đích để thêm `T` cần biết **đích chấp nhận được `T`**. Nó không nhất thiết phải biết **tất cả phần tử hiện có của đích là `T`**.

Đó chính là hai chiều khác nhau:

- **Ghi:** cần bảo đảm `T` có thể đặt vào collection → `Collection<? super T>`.
- **Đọc:** cần bảo đảm phần tử lấy ra dùng được dưới dạng `T` → `Collection<? extends T>`.

Sự bất đối xứng này là lý do của quy tắc PECS:

> **Producer Extends, Consumer Super.**

PECS là hướng dẫn dựa trên cách tham số được sử dụng. Nó không có nghĩa bất kỳ đối tượng nào mang tên “output” đều phải dùng `super`, hoặc hễ gọi `add` là cần wildcard `super`. Nếu thuật toán cần **cả đọc và ghi với chính xác `T`**, `List<T>` có thể rõ ràng hơn.

Ví dụ:

```java
static void increment(List<Integer> values) {
    for (int i = 0; i < values.size(); i++) {
        values.set(i, values.get(i) + 1);
    }
}
```

Phương thức cần đọc ra `Integer` để cộng, rồi ghi `Integer` trở lại, vì vậy `List<Integer>` hợp lý. Nếu đổi thành `List<? super Integer>`, kết quả `get(i)` chỉ được bảo đảm là `Object` và phép cộng không thể áp dụng trực tiếp.

## 6. Ghép nguồn sản xuất và đầu ra nhận dữ liệu

Một pipeline thường đọc từ một iterable và ghi vào một collection khác. Chữ ký vừa đúng PECS vừa dùng abstraction tối thiểu:

~~~java
static <T> void transfer(
        Iterable<? extends T> source,
        Collection<? super T> destination) {
    for (T value : source) {
        destination.add(value);
    }
}
~~~

`Iterable<? extends T>` **sản xuất** phần tử có thể đọc như `T`; `Collection<? super T>` **tiêu thụ** `T`. Do đó có thể chuyển `List<Integer>` sang `Collection<Number>` hoặc `Collection<Object>` mà không dùng unchecked cast.

Method này **append**, không ghi đè các vị trí đã có. Nếu `source` và `destination` là cùng một `ArrayList`, việc `add` trong lúc foreach có thể gây `ConcurrentModificationException`; muốn hỗ trợ self-copy, cần snapshot phù hợp trước khi mutate. Nếu `destination.add` ném exception, những phần tử trước đó có thể đã được thêm: PECS không làm thao tác copy trở thành atomic transaction.

## 7. Ví dụ thư viện chuẩn: `Collections.copy`

Java Collections Framework sử dụng cùng ý tưởng consumer cho đích. Chữ ký khái niệm của phương thức:

```java
public static <T> void copy(
        List<? super T> dest,
        List<? extends T> src)
```

Trong đó:

- `src` cung cấp phần tử, nên dùng `? extends T`.
- `dest` nhận hoặc thay thế phần tử, nên dùng `? super T`.

Ví dụ độc lập nhỏ:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CopyExample {
    public static void main(String[] args) {
        List<Integer> source = List.of(4, 5);
        List<Number> target = new ArrayList<>(List.of(0, 0));

        Collections.copy(target, source);
        System.out.println(target);
    }
}
```

Lưu `CopyExample.java` và chạy:

```bash
javac CopyExample.java
java CopyExample
```

Stdout:

```text
[4, 5]
```

**Quan trọng:** `Collections.copy` **thay thế** phần tử tại các vị trí sẵn có của đích, không `append` vào cuối như `appendAll`. Danh sách đích phải có `size()` **ít nhất bằng** kích thước nguồn; nếu không, phương thức ném `IndexOutOfBoundsException`. Việc đích là danh sách có thể thay thế phần tử cũng cần được bảo đảm, nếu không thao tác có thể ném `UnsupportedOperationException`.

Ở ví dụ trên, `target` được tạo với hai phần tử `0`, đủ chỗ để thay thế hai vị trí bằng `4` và `5`. `new ArrayList<>(source)` là một cách khác khi thực sự muốn tạo danh sách mới, không phải ghi vào một đích có sẵn.

## 8. `? super T` không chỉ có trong collection

“Consumer” có thể là một hàm hoặc đối tượng **tiếp nhận giá trị `T`**, ngay cả khi nó không lưu phần tử.

### 8.1. `Consumer<? super T>`

Phương thức `Iterable.forEach` có chữ ký nhận một `Consumer<? super T>`. Nhờ vậy một `Consumer<Number>` có thể xử lý phần tử của `List<Integer>`:

```java
import java.util.List;
import java.util.function.Consumer;

public class ConsumerExample {
    public static void main(String[] args) {
        List<Integer> integers = List.of(1, 2, 3);
        Consumer<Number> printNumber =
                number -> System.out.println(number.intValue());

        integers.forEach(printNumber);
    }
}
```

Chạy:

```bash
javac ConsumerExample.java
java ConsumerExample
```

Stdout:

```text
1
2
3
```

`Consumer<Number>` biết nhận bất kỳ `Number` nào, vì thế có thể nhận các `Integer`. Trường hợp ngược lại, một `Consumer<Integer>` không thể được coi là consumer tổng quát của mọi `Number`, do nó không được đảm bảo xử lý `Double`.

### 8.2. `Comparator<? super T>`

`List.sort` nhận `Comparator<? super E>`, nơi `E` là kiểu phần tử của danh sách. Một `Comparator<Number>` đủ khả năng so sánh các `Integer` vì mỗi `Integer` cũng là `Number`.

```java
List<Integer> values = new ArrayList<>(List.of(10, 3, 20));
Comparator<Number> byValue =
        Comparator.comparingDouble(Number::doubleValue);
values.sort(byValue);
System.out.println(values); // [3, 10, 20]
```

Đây là **đoạn mã dùng trong ngữ cảnh đã import `java.util.List`, `java.util.ArrayList` và `java.util.Comparator`**. `Comparator` là consumer ở khía cạnh **nhận phần tử để so sánh**, không có nghĩa comparator phải ghi vào danh sách.

Điểm chung là **một API cần đưa giá trị `T` cho đối tượng nhận**; giới hạn dưới cho phép đối tượng nhận được thiết kế để xử lý cả kiểu cha của `T`.

## 9. Chương trình Java 17+ hoàn chỉnh

Chương trình sau tổng hợp các tình huống chính: nhiều loại đích nhận `Integer`, nguồn–đích có quan hệ kiểu, đọc qua `? super Integer` và comparator tiêu thụ `Number`.

Lưu thành **`SuperConsumerDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class SuperConsumerDemo {
    static void seed(Collection<? super Integer> target) {
        target.add(10);
        target.add(20);
    }

    static <T> void appendAll(
            List<? extends T> source,
            List<? super T> target) {
        for (T element : source) {
            target.add(element);
        }
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>();
        List<Number> numbers = new ArrayList<>();
        List<Object> objects = new ArrayList<>();

        seed(integers);
        seed(numbers);
        seed(objects);

        List<Integer> source = List.of(3, 5);
        appendAll(source, numbers);
        appendAll(source, objects);

        System.out.println("integers = " + integers);
        System.out.println("numbers = " + numbers);
        System.out.println("objects = " + objects);

        List<? super Integer> target = numbers;
        Object first = target.get(0);
        System.out.println("first = " + first);

        numbers.add(2.5);
        System.out.println("numbers after Double = " + numbers);

        List<Integer> sorted = new ArrayList<>(List.of(10, 3, 20));
        Comparator<Number> byValue =
                Comparator.comparingDouble(Number::doubleValue);
        sorted.sort(byValue);
        System.out.println("sorted = " + sorted);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac SuperConsumerDemo.java
java SuperConsumerDemo
```

Stdout:

```text
integers = [10, 20]
numbers = [10, 20, 3, 5]
objects = [10, 20, 3, 5]
first = 10
numbers after Double = [10, 20, 3, 5, 2.5]
sorted = [3, 10, 20]
```

Giải thích kết quả:

1. Cùng phương thức `seed` thêm `Integer` vào ba loại collection đích, vì cả ba đều chấp nhận `Integer`.
2. `appendAll` chuyển giá trị từ `List<Integer>` sang `List<Number>` và `List<Object>` mà không cần ép kiểu.
3. Qua tham chiếu `target` kiểu `List<? super Integer>`, kết quả đọc bảo đảm an toàn dưới dạng `Object`.
4. Danh sách `numbers` vốn là `List<Number>`, nên qua tham chiếu `numbers` vẫn có thể thêm `2.5` sau khi phương thức consumer đã chạy.
5. `Comparator<Number>` so sánh được các phần tử `Integer` khi `sorted.sort(byValue)` được gọi.

Đoạn mã không có chuyển đổi một `List<Number>` thành `List<Integer>`; wildcard chỉ điều chỉnh **những quan hệ tham số được phép trong hệ kiểu**, không đổi kiểu phần tử của danh sách thực.

## 10. Chọn chữ ký thích hợp theo nhu cầu

| Nhu cầu của API | Chữ ký gợi ý | Đọc được như | Ghi được |
| --- | --- | --- | --- |
| Chỉ thêm `Integer` vào đích | `Collection<? super Integer>` | `Object` khi lấy phần tử | `Integer` |
| Đọc mọi loại số từ nguồn | `Collection<? extends Number>` | `Number` | Không bảo đảm giá trị không-null cụ thể |
| Đọc và thêm mọi `Number` vào danh sách đích đã xác định | `List<Number>` | `Number` | `Number` và các subtype |
| Đọc và ghi chính xác `Integer` | `List<Integer>` | `Integer` | `Integer` |
| Chuyển giá trị từ nguồn sang đích | `<T>` + `Iterable<? extends T>` và `Collection<? super T>` | `T` từ nguồn | `T` vào đích |
| Xử lý các phần tử `Integer` bằng callback nhận `Number` | `Consumer<? super Integer>` | Không áp dụng với callback này | Callback nhận `Integer` |

“Ghi được” trong bảng nghĩa là **hợp lệ về kiểu**, không bảo đảm một collection cụ thể mutable. Lựa chọn chữ ký phải theo hợp đồng phương thức, không phải chỉ theo tên biến.

Một số API chỉ cần gọi `destination.add(...)` nên có thể nhận `Collection<? super T>`. Nếu cần truy cập vị trí hoặc thay thế phần tử bằng `set(index, value)`, khi đó chọn `List<? super T>`.

## 11. Lỗi phổ biến

### 11.1. Giả sử `? super Integer` cho phép ghi mọi `Number`

`List<? super Integer>` chấp nhận **chắc chắn `Integer`**, không chắc chắn chấp nhận `Double`. Nếu đích thật là `List<Integer>`, thêm `Double` sẽ sai. Muốn thêm các `Number` tùy ý, dùng `List<? super Number>` (cho đích có kiểu `Number` hoặc kiểu cha), hoặc `List<Number>` khi biết rõ kiểu danh sách cần dùng.

### 11.2. Đọc `Integer` trực tiếp từ đích wildcard

```java
List<Object> objects = new ArrayList<>();
objects.add("other");
List<? super Integer> destination = objects;

// Integer value = destination.get(0); // LỖI BIÊN DỊCH.
Object value = destination.get(0);      // Hợp lệ.
```

Đây là ví dụ cho thấy đọc dưới dạng `Integer` sẽ không an toàn: phần tử hiện tại thật sự là `String`. Tham chiếu lower-bounded wildcard không hứa mọi phần tử của đích đều có kiểu `Integer`.

### 11.3. Tưởng `super` tạo ra collection có thể sửa đổi

```java
List<Integer> immutable = List.of(1, 2);
List<? super Integer> target = immutable;
// target.add(3); // Biên dịch được, nhưng nếu chạy sẽ ném
// UnsupportedOperationException vì List.of không hỗ trợ thêm.
```

Từ `super` chỉ suy ra **kiểm tra kiểu cho phần tử ghi**, không suy ra khả năng thay đổi cấu trúc. Nếu API có nhiệm vụ ghi, cần hợp đồng nói rõ collection đích phải hỗ trợ thao tác tương ứng.

### 11.4. Nhầm `super` với “kiểu cha của danh sách”

`List<? super Integer>` không phải `List<Number>` và không có nghĩa nó kế thừa từ `List<Integer>`. Đây là **một kiểu tham số hóa có wildcard với giới hạn dưới**, dùng để chấp nhận nhiều đối số kiểu phù hợp. Các phép gán và quyền đọc/ghi được compiler kiểm tra dựa trên giới hạn đó.

### 11.5. Dùng raw type hoặc unchecked cast để ép dữ liệu

Đừng viết `List raw = ...` rồi thêm đối tượng không có ràng buộc kiểu, hoặc ép `List<?>` sang `List<Integer>` chỉ để gọi `add`. Các lối tắt ấy có thể làm dữ liệu không phù hợp với tham số kiểu gốc và dẫn đến `ClassCastException` khi phần tử được đọc theo kiểu khai báo.

Hãy sử dụng `? super T` khi cần **thêm `T` vào đích rộng hơn** và `? extends T` khi cần **lấy `T` từ nguồn hẹp hơn**.

### 11.6. Quên kích thước hoặc khả năng ghi của đích

Một số thao tác dùng `add` để mở rộng đích; thao tác như `Collections.copy` lại dùng `set` để thay thế vị trí đã tồn tại. Hai loại cùng có thể tận dụng `? super T`, nhưng điều kiện runtime khác nhau. Hãy đọc hợp đồng API: collection có mutable không, đích có đủ kích thước không, có chấp nhận `null` không, và nguồn/đích có được trùng nhau không.

## 12. Tự kiểm tra kiến thức

Cho các biến có kiểu khai báo rõ ràng:

```java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();
List<Double> doubles = new ArrayList<>();

List<? super Integer> destination = numbers;
```

Đánh giá từng câu:

1. `destination.add(1)` — **Hợp lệ**. `1` được boxing thành `Integer`.
2. `destination.add(Double.valueOf(2.5))` — **Lỗi biên dịch**. Đích có thể là `List<Integer>`.
3. `Number n = destination.get(0)` — **Lỗi biên dịch**. Đọc qua wildcard chỉ bảo đảm `Object`.
4. `Object o = destination.get(0)` — **Hợp lệ về kiểu**; nếu danh sách rỗng, runtime ném `IndexOutOfBoundsException`.
5. `destination = integers` — **Hợp lệ**. `List<Integer>` có thể làm consumer của `Integer`.
6. `destination = objects` — **Hợp lệ**. `List<Object>` chấp nhận `Integer`.
7. `destination = doubles` — **Lỗi biên dịch**. `Double` không phải kiểu cha của `Integer`.
8. `List<? super Number> target = numbers` — **Hợp lệ**. `Number` thỏa giới hạn dưới.
9. `List<? super Number> target = integers` — **Lỗi biên dịch**. `Integer` không là kiểu cha của `Number`.
10. `appendAll(integers, objects)` — **Hợp lệ**, với chữ ký `<T> void appendAll(List<? extends T>, List<? super T>)` trong chương trình hoàn chỉnh phía trên.

Các câu liên quan `get(0)` chỉ đánh giá **kiểu của biểu thức**; muốn chạy thành công, danh sách phải có phần tử và không vi phạm hợp đồng runtime.

## Ghi nhớ

- **Consumer** là tham số **nhận giá trị kiểu `T`** từ phương thức; khi cần ghi `T` vào nhiều loại đích, dùng `? super T`.
- `List<? super Integer>` nhận được tham chiếu `List<Integer>`, `List<Number>` và `List<Object>`; không nhận `List<Double>`.
- Với `List<? super T>`, có thể ghi **`T` hoặc subtype của `T`** nhưng đọc ra an toàn chung chỉ là `Object`.
- **PECS:** dùng `? extends T` cho nguồn cung cấp, `? super T` cho đích tiêu thụ.
- Khi cần cả đọc và ghi với đúng một kiểu cụ thể, `List<T>` hoặc `List<Integer>` thường phù hợp hơn.
- Khả năng ghi được **về mặt generic** không chứng minh collection mutable. Những điều kiện runtime của `add`, `set`, `Collections.copy` vẫn phải được tuân thủ.
- Java Generics bất biến; lower-bounded wildcard cung cấp khả năng nhận đích rộng hơn **mà không cần ép kiểu không an toàn**.

Chọn `? super T` cho đúng vai trò nhận dữ liệu giúp API Java tái sử dụng tốt hơn, cho phép chuyển phần tử lên collection đích có kiểu cha và giữ tính đúng đắn của hợp đồng generic ngay từ thời điểm biên dịch.


</details>

- [Quay lại đầu trang](#back-to-top)
