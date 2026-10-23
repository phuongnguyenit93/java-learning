<a id="back-to-top"></a>

# Kiểu phần tử

## Menu
- [Generic trong Collection&lt;E&gt; và Map&lt;K,V&gt;](#element-types-001)
- [Autoboxing, unboxing và chi phí của kiểu bao](#element-types-002)
- [Tính bất biến của generic: List&lt;Integer&gt; và List&lt;Number&gt;](#element-types-003)
- [Wildcard giới hạn trên khi đọc dữ liệu](#element-types-004)
- [Wildcard giới hạn dưới khi ghi dữ liệu và quy tắc PECS](#element-types-005)
- [Raw type, ép kiểu và ô nhiễm kiểu trong collection](#element-types-006)
- [Thiết kế phương thức nhận collection linh hoạt nhưng an toàn kiểu](#element-types-007)

## <a id="element-types-001">Generic trong Collection&lt;E&gt; và Map&lt;K,V&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Generic trong `Collection<E>` và `Map<K,V>`

## 1. Vì sao collection cần generics?

Giả sử một nhóm dữ liệu dùng để lưu tên người. Mã xử lý mong đợi mỗi phần tử là chuỗi để gọi `length()` hoặc `toUpperCase()`. Nếu nhóm đó nhận lẫn số nguyên, lỗi có thể chỉ xuất hiện khi đọc và ép kiểu.

Generics giúp diễn đạt điều kiện về **kiểu dữ liệu** ngay trong khai báo:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Collection<String> names = new ArrayList<>();
names.add("Lan");
// names.add(123); // Không biên dịch: Integer không phù hợp String.

String first = names.iterator().next(); // Không cần ép kiểu thủ công.
```

Với map, cần biểu đạt hai phía của một ánh xạ:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Map<String, Integer> stock = new HashMap<>();
stock.put("BOOK", 10);
// stock.put(123, 10);     // Sai kiểu khóa.
// stock.put("PEN", "ten"); // Sai kiểu giá trị.

Integer quantity = stock.get("BOOK");
```

Generics cho phép dùng cùng một API collection cho nhiều loại dữ liệu, trong khi trình biên dịch kiểm tra tính nhất quán của mỗi cách sử dụng.

Trong bài này, cần phân biệt:

- Tham số kiểu: ký hiệu trong khai báo generic, như `E`, `K`, `V`.
- Đối số kiểu: kiểu được truyền vào khi sử dụng, như `String`, `Integer`.
- Kiểu được tham số hóa: toàn bộ kiểu như `Collection<String>`.
- Đối tượng cài đặt: chẳng hạn một `ArrayList<String>` hoặc `HashMap<String, Integer>`.

Bài dùng Java 17 và tập trung vào generics trong collection/map, không yêu cầu tự xây dựng một thư viện cấu trúc dữ liệu.

## 2. Đọc E, K và V đúng ý nghĩa

### 2.1. E là kiểu phần tử

Trong tên `Collection<E>`, `E` thường được hiểu là “element”. Khi thay `E` bằng `String`, các thao tác liên quan tới phần tử được kiểm tra với kiểu tương ứng.

Ví dụ các chữ ký API, được rút gọn để đọc:

```java
// Phác thảo chữ ký, không phải chương trình độc lập.
interface Collection<E> extends Iterable<E> {
    boolean add(E element);
    Iterator<E> iterator();
}
```

Với `Collection<String>`:

- `add` nhận một tham chiếu phù hợp với `String`.
- `iterator` trả `Iterator<String>`.
- `next` trên iterator trả kiểu `String` tại nơi sử dụng có kiểm tra kiểu.

`List<E>`, `Set<E>`, `Queue<E>` và các cài đặt liên quan tiếp tục truyền kiểu phần tử qua hệ thống API. [Collection API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html)

### 2.2. K và V là hai tham số độc lập

Trong `Map<K, V>`:

- `K`: kiểu khóa (key).
- `V`: kiểu giá trị (value).

`Map<String, Integer>` diễn đạt “tra một giá trị `Integer` bằng khóa dạng `String`”. Khóa và giá trị không bắt buộc có cùng kiểu. Có thể dùng `Map<String, String>` nếu cả hai phía đều là chuỗi; đó là lựa chọn cụ thể, không phải giới hạn của `Map`.

Một số chữ ký:

```java
// Phác thảo chữ ký, không phải khai báo đầy đủ của Map.
V put(K key, V value);
V get(Object key);
Set<K> keySet();
Collection<V> values();
Set<Map.Entry<K, V>> entrySet();
```

`put` dùng `K` và `V` để kiểm tra dữ liệu được đưa vào. `get` trả `V` nhưng đối số truy vấn là `Object`. Đừng tự sửa chữ ký trong suy nghĩ thành `get(K)`. [Map API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)

### 2.3. Chữ E, K, V là quy ước đặt tên

Các chữ này không phải từ khóa với quyền năng riêng. Chúng là tên tham số kiểu trong phạm vi khai báo. `T` cũng thường được dùng cho một kiểu tổng quát.

Không viết `Collection<E>` trong mã sử dụng nếu `E` chưa được khai báo trong phạm vi tương ứng. Khi sử dụng bình thường, truyền kiểu thật:

```java
Collection<String> words = new ArrayList<>();
Map<Long, String> userNames = new HashMap<>();
```

Hai dòng trên là các khai báo minh họa trong phương thức hoặc trường dữ liệu, cần import các kiểu trong `java.util`.

Xem [Generic Types](https://docs.oracle.com/javase/tutorial/java/generics/types.html) để đối chiếu tham số kiểu với đối số kiểu.

## 3. Kiểu phần tử và lớp cài đặt là hai quyết định khác nhau

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<String> first = new ArrayList<>();
List<String> second = new LinkedList<>();
```

Hai đối tượng có lớp cài đặt khác nhau nhưng cùng được nhìn qua hợp đồng `List<String>`.

- `List` biểu đạt danh sách và API được sử dụng.
- `String` biểu đạt kiểu phần tử.
- `ArrayList` hoặc `LinkedList` quyết định cách cài đặt và nhiều đặc tính khi chạy.

Đổi lớp cài đặt không tự đổi phần tử từ `String` sang một kiểu khác. Đổi đối số kiểu cũng không chỉ là đổi cách lưu: `List<String>` và `List<Integer>` là hai kiểu tham số hóa khác nhau ở thời điểm kiểm tra mã.

Tương tự:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Map<String, Integer> counts = new LinkedHashMap<>();
```

`Map` xác định mô hình khóa–giá trị; `String, Integer` xác định hai kiểu dữ liệu; `LinkedHashMap` quyết định thêm các đặc tính như thứ tự duyệt theo chèn trong cấu hình mặc định.

Generic không tự quy định tính không trùng, thứ tự, khả năng sửa hay tính an toàn nhiều luồng. Những thuộc tính đó vẫn phải đọc từ hợp đồng giao diện, cài đặt và cách tạo đối tượng.

## 4. Diamond và suy luận kiểu

### 4.1. Không cần lặp lại đối số kiểu ở hai vế

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<String> names = new ArrayList<>();
Map<String, Integer> counts = new HashMap<>();
```

`<>` được gọi là diamond. Trình biên dịch suy luận các đối số kiểu cho constructor từ ngữ cảnh.

Có thể viết dài hơn:

```java
List<String> names = new ArrayList<String>();
```

Nhưng trong tình huống này phần `String` ở vế phải là lặp lại không cần thiết. Diamond không tắt generics và không làm mất kiểm tra kiểu.

### 4.2. var không suy luận từ các lần thêm về sau

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
var values = new ArrayList<>();
values.add("text");
values.add(99);

System.out.println(values); // [text, 99]
```

Ở đây không có kiểu đích `List<String>` và constructor không nhận dữ liệu giúp ràng buộc kiểu. Kết quả suy luận là `ArrayList<Object>`. Lần thêm `"text"` sau đó không làm biến thành `ArrayList<String>`.

Nếu muốn phần tử là chuỗi:

```java
// Hai lựa chọn khai báo minh họa; cần import java.util.*.
List<String> explicitContract = new ArrayList<>();
var explicitArgument = new ArrayList<String>();
```

`var` vẫn là suy luận kiểu tĩnh cho biến cục bộ; nó không có nghĩa biến nhận mọi kiểu động. [Local Variable Type Inference](https://docs.oracle.com/en/java/javase/17/language/local-variable-type-inference.html)

Đọc kỹ cả kiểu đích, đối số constructor và đối số kiểu trước khi kết luận compiler đã suy luận điều gì.

## 5. Kiểu tham chiếu, boxing và null

Đối số kiểu generic phải là kiểu tham chiếu. Không viết:

```java
// Ví dụ cố ý không biên dịch.
// Collection<int> numbers;
// Map<String, double> prices;
```

Dùng wrapper:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Collection<Integer> numbers = new ArrayList<>();
numbers.add(10); // Autoboxing int thành Integer.

Integer boxed = numbers.iterator().next();
int primitive = boxed; // Unboxing Integer thành int.
```

Các đối số kiểu cũng có thể là kiểu mảng hoặc kiểu tham số hóa khác, vì chúng là kiểu tham chiếu. Ví dụ `List<int[]>` là hợp lệ; `int[]` là một kiểu mảng tham chiếu, khác với kiểu nguyên thủy `int`.

Generics không mã hóa bảo đảm khác `null` trong Java thông thường:

- `Collection<String>` có thể nhận `null` về mặt kiểm tra kiểu.
- Cài đặt có thể cho hoặc không cho `null`.
- Unboxing một `Integer` là `null` gây `NullPointerException`.
- Giá trị `null` không trở thành một đối tượng `String` hay `Integer`.

Xem [Restrictions on Generics](https://docs.oracle.com/javase/tutorial/java/generics/restrictions.html) về giới hạn đối số nguyên thủy. Chính sách chấp nhận giá trị cụ thể vẫn là một lớp hợp đồng riêng.

## 6. Vì sao `Collection<Integer>` không phải `Collection<Number>`?

`Integer` là kiểu con của `Number`, nhưng quan hệ đó không tự lan vào tham số kiểu:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Collection<Integer> integers = new ArrayList<>();
// Collection<Number> numbers = integers; // Không biên dịch.
```

Nếu phép gán này được phép, mã qua biến `numbers` có thể thêm `Double` vào cùng đối tượng. Khi mã khác đọc collection đó như chỉ chứa `Integer`, giả định ban đầu bị phá vỡ.

Đó là lý do các tham số kiểu này có tính bất biến (invariance). Không dùng ép kiểu để lách lỗi rồi coi dữ liệu đã an toàn.

Điều vẫn hợp lệ là quan hệ kế thừa giữ nguyên đối số kiểu:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
ArrayList<Integer> implementation = new ArrayList<>();
List<Integer> list = implementation;
Collection<Integer> collection = list;
```

Các biến đang cùng giữ loại phần tử `Integer`.

Quy tắc cũng áp dụng độc lập cho hai phía map:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Map<String, Integer> source = new HashMap<>();
// Map<Object, Number> target = source; // Không biên dịch.

Map<Object, Number> copy = new HashMap<>();
copy.putAll(source); // Sao chép dữ liệu vào đối tượng khác có kiểu phù hợp.
```

Phép gán và sao chép phần tử không phải cùng một hành động. `putAll` được thiết kế nhận nguồn có khóa/giá trị là kiểu con phù hợp. [Generics, Inheritance, and Subtypes](https://docs.oracle.com/javase/tutorial/java/generics/inheritance.html)

## 7. `Collection<Object>`, `Collection<?>` và raw Collection

Ba cách viết này không tương đương:

| Kiểu | Ý nghĩa | Đọc phần tử | Thêm phần tử khác null |
| --- | --- | --- | --- |
| `Collection<Object>` | Collection có kiểu phần tử là `Object` | Nhận `Object` | Có thể thêm tham chiếu của các kiểu phù hợp với `Object` nếu đối tượng hỗ trợ |
| `Collection<?>` | Collection của một kiểu chưa biết | Nhận `Object` | Không tự thêm một kiểu cụ thể vì không biết kiểu thật |
| `Collection` raw | Bỏ đối số kiểu trong tương tác mã cũ | Nhận `Object` | Có thể tạo lời gọi unchecked, làm mất bảo đảm kiểu |

`Collection<Object>` không phải “biến nhận mọi collection”. Nó không nhận gán từ `Collection<String>` vì invariance.

`Collection<?>` có thể tham chiếu `Collection<String>` hoặc `Collection<Integer>` để thực hiện thao tác không cần biết kiểu cụ thể:

```java
// Đoạn khai báo phương thức; cần import java.util.Collection.
static void printElements(Collection<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

Không được gọi `values.add("A")` trong phương thức đó, vì `values` có thể thực tế là collection của `Integer`.

Tuy nhiên, `?` không tự tạo một view bất biến. Những thao tác như `clear` hoặc `remove(Object)` vẫn có thể được gọi nếu đối tượng hỗ trợ. `add(null)` có thể hợp lệ về kiểu, nhưng chính sách `null` của đối tượng vẫn có thể từ chối. Đừng dùng wildcard như từ đồng nghĩa với “không thể thay đổi”.

Tham khảo [Wildcards](https://docs.oracle.com/javase/tutorial/java/generics/wildcards.html).

## 8. Wildcard khi đọc từ nguồn và ghi vào đích

### 8.1. extends: nhận nguồn thuộc một kiểu con chưa biết

Một hàm tính tổng không cần sửa các phần tử thành kiểu khác; nó chỉ cần đọc mỗi phần tử dưới dạng `Number`:

```java
// Đoạn khai báo phương thức; cần import java.util.Collection.
static double sum(Collection<? extends Number> values) {
    double total = 0;
    for (Number value : values) {
        total += value.doubleValue();
    }
    return total;
}
```

Có thể truyền `Collection<Integer>`, `Collection<Double>` hoặc `Collection<Number>`. Hợp đồng của ví dụ yêu cầu nguồn và các phần tử khác `null`.

Không thêm `Double` vào `values`: kiểu thật có thể là `Integer`. `? extends Number` có nghĩa “một kiểu cụ thể nào đó là Number hoặc kiểu con của Number”, không phải “mọi loại Number đều được ghi vào đây”. [Upper Bounded Wildcards](https://docs.oracle.com/javase/tutorial/java/generics/upperBounded.html)

### 8.2. super: nhận đích có thể chứa kiểu cần ghi

```java
// Đoạn khai báo phương thức; cần import java.util.Collection.
static void appendIntegers(Collection<? super Integer> destination) {
    destination.add(10);
    destination.add(20);
}
```

Hàm có thể nhận `Collection<Integer>`, `Collection<Number>` hoặc `Collection<Object>`. Cả ba đều có thể giữ các `Integer` được thêm vào, miễn đối tượng hỗ trợ thao tác và các ràng buộc khác.

Khi đọc qua biến `destination`, kiểu an toàn tổng quát là `Object`. Không thể khẳng định mọi phần tử đã có là `Integer`: một `Collection<Number>` có thể chứa `Double` trước đó. [Lower Bounded Wildcards](https://docs.oracle.com/javase/tutorial/java/generics/lowerBounded.html)

### 8.3. Áp dụng vừa đủ

Quy tắc nhớ thường dùng là PECS: nguồn cung cấp phần tử dùng `extends`, đích nhận phần tử dùng `super`.

Nó hỗ trợ thiết kế tham số, không phải lệnh buộc mọi biến hay mọi giá trị trả về phải dùng wildcard. Một API trả `List<String>` thường dễ dùng hơn một giá trị trả `List<? extends String>` không có lý do cụ thể.

Wildcard giải quyết quan hệ kiểu tại lời gọi. Nó không tự cấp quyền sửa, không bỏ giới hạn `null` và không làm thao tác nguyên tử.

## 9. Generic method liên kết kiểu giữa đầu vào và kết quả

Nếu cần bảo toàn quan hệ kiểu, có thể khai báo tham số kiểu của phương thức:

```java
// Đoạn khai báo phương thức; cần import java.util.*.
static <T> List<T> copyOfElements(Collection<? extends T> source) {
    return new ArrayList<>(source);
}
```

Đọc từ trái sang phải:

- `<T>` khai báo tham số kiểu của riêng phương thức.
- `List<T>` là kiểu kết quả.
- Nguồn cung cấp phần tử kiểu `T` hoặc kiểu con của `T`.
- Kết quả là danh sách mới có thể chứa `T`.

Ví dụ:

```java
// Đoạn minh họa trong phương thức, với copyOfElements đã khai báo.
Collection<Integer> source = List.of(1, 2, 3);
List<Number> result = copyOfElements(source);
result.add(2.5);
```

Ngữ cảnh đích góp phần cho suy luận kiểu `T` là `Number`. Nguồn `Integer` phù hợp, và danh sách mới giữ các tham chiếu phần tử đó. Thêm `Double` vào `result` không sửa collection nguồn.

Không phải mỗi lần gọi đều tạo ra một lớp hay một bản phương thức riêng cho `Integer` và `Number` ở runtime. Đây là cơ chế kiểm tra và suy luận kiểu của mã generic.

Xem [Generic Methods](https://docs.oracle.com/javase/tutorial/java/generics/methods.html).

## 10. Generic trong các view của Map

Với `Map<String, Integer> counts`:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Set<String> keys = counts.keySet();
Collection<Integer> values = counts.values();
Set<Map.Entry<String, Integer>> entries = counts.entrySet();
```

Ba kiểu kết quả diễn đạt ba góc nhìn:

- `keySet`: các khóa không trùng theo hợp đồng map.
- `values`: các giá trị có thể trùng, nên không phải `Set<V>`.
- `entrySet`: các entry giữ cả khóa và giá trị với đúng hai kiểu.

Duyệt entry:

```java
// Đoạn minh họa với counts đã được khởi tạo.
for (Map.Entry<String, Integer> entry : counts.entrySet()) {
    String word = entry.getKey();
    Integer count = entry.getValue();
    System.out.println(word + "=" + count);
}
```

`Map.Entry<K, V>` tiếp tục mang quan hệ kiểu của map. Không cần ép kết quả `getKey` thành `String` hoặc `getValue` thành `Integer`. [Map.Entry API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html)

Các view này gắn với map nguồn, không mặc nhiên là bản sao. Generics mô tả kiểu phần tử, không thay đổi quan hệ nguồn–view hay mức hỗ trợ các thao tác sửa.

### get nhận Object có nghĩa gì?

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Map<String, Integer> values = new HashMap<>();
values.put("A", 1);

Integer found = values.get("A"); // 1
Integer absent = values.get(123); // null với HashMap trong ví dụ này.
```

Việc `get(123)` biên dịch không làm `123` trở thành khóa hợp lệ để `put` vào `Map<String, Integer>`. Truy vấn và đưa dữ liệu vào có chữ ký khác nhau. Một cài đặt khác có thể đưa ra ràng buộc hoặc ngoại lệ khác cho đối số truy vấn.

`get` có thể trả `null` khi thiếu khóa; một số map còn cho giá trị `null`. Vì vậy, gán trực tiếp vào `int` có thể gây `NullPointerException` khi unboxing. Kiểu `V` không loại bỏ trường hợp không có kết quả.

## 11. Kiểu lồng nhau: đọc từ ngoài vào trong

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Map<String, List<Integer>> scores = new LinkedHashMap<>();
scores.put("An", List.of(8, 9));

List<Integer> studentScores = scores.get("An");
Integer first = studentScores.get(0);
```

Ý nghĩa:

1. Map có khóa `String`.
2. Mỗi giá trị là một `List<Integer>`.
3. Mỗi phần tử trong danh sách là `Integer`.

Không được viết `scores.put("An", 8)`: giá trị cần là cả danh sách. Cũng không được dùng `List<String>` làm giá trị.

Mỗi tầng có hợp đồng riêng. Map sửa được không có nghĩa danh sách giá trị sửa được; ở đây `List.of` không hỗ trợ thêm phần tử. Map không có khóa cũng có thể làm `scores.get(...)` trả `null` trước khi tới thao tác danh sách.

Khi khai báo lồng nhau quá dài hoặc khó gắn với ý nghĩa nghiệp vụ, cân nhắc tạo một kiểu dữ liệu có tên, chẳng hạn bản ghi điểm của học viên. Đừng dùng `Map<String, Object>` để né thiết kế rồi phân tán ép kiểu khắp ứng dụng.

## 12. Raw type, type erasure và giới hạn runtime

### 12.1. Raw type làm yếu kiểm tra kiểu

```java
// Minh họa cố ý không an toàn trong một phương thức.
// Cần import java.util.*; có cảnh báo raw/unchecked.
List<String> words = new ArrayList<>();
List raw = words;
raw.add(123);

// String first = words.get(0); // ClassCastException khi đọc thành String.
```

Lỗi có thể xuất hiện xa nơi chèn dữ liệu sai. Dùng generics xuyên suốt giúp đưa lỗi về gần chỗ viết mã hơn.

Không đổi `List<String>` thành raw `List` chỉ để một phương thức chấp nhận nhiều loại danh sách. Dùng tham số kiểu hoặc wildcard đúng mục đích. [Raw Types](https://docs.oracle.com/javase/tutorial/java/generics/rawTypes.html)

### 12.2. Không có một lớp ArrayList riêng cho mỗi đối số kiểu

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<String> words = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();

System.out.println(words.getClass().equals(numbers.getClass())); // true
```

Với type erasure, các cách sử dụng này dựa trên cùng lớp runtime. Compiler kiểm tra ràng buộc generic và thêm các phép chuyển kiểu cần thiết tại nơi phù hợp.

Điều đó không có nghĩa mọi thông tin generic đều bị xóa khỏi mọi metadata hoặc generics “không có tác dụng”. Nó có nghĩa đối tượng `ArrayList` thông thường không giữ một lớp runtime riêng chứng minh tham số phần tử là `String` hay `Integer`. [Type Erasure](https://docs.oracle.com/javase/tutorial/java/generics/erasure.html)

### 12.3. Kiểm tra cấu trúc khác kiểm tra từng phần tử

Với một nguồn chưa biết được khai báo bằng `Object`:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
Object candidate = List.of("A", "B");

if (candidate instanceof List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
// candidate instanceof List<String> không dùng được để chứng minh
// một Object bất kỳ là danh sách chỉ chứa String.
```

`instanceof List<?>` kiểm tra đây là một đối tượng thuộc họ danh sách, không xác minh toàn bộ nội dung là `String`. Nếu dữ liệu đến từ nguồn ngoài, cần kiểm tra và chuyển đổi từng phần tử để tạo kết quả có hợp đồng mong muốn.

Tương tự, không tạo mảng bằng `new List<String>[10]`. Những giới hạn liên quan tới kiểu không hiện thực đầy đủ ở runtime được giải thích trong [Non-Reifiable Types](https://docs.oracle.com/javase/tutorial/java/generics/nonReifiableVarargsType.html).

Bài không dùng unchecked cast như một cách “xác minh” dữ liệu: ép kiểu không duyệt từng phần tử để làm dữ liệu an toàn.

## 13. Chương trình hoàn chỉnh

Lưu thành `GenericCollectionMapDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GenericCollectionMapDemo {
    private static <T> List<T> copyOfElements(
            Collection<? extends T> source) {
        return new ArrayList<>(source);
    }

    private static void appendIntegers(
            Collection<? super Integer> destination) {
        destination.add(10);
        destination.add(20);
    }

    private static double sum(Collection<? extends Number> values) {
        double total = 0;
        for (Number value : values) {
            total += value.doubleValue();
        }
        return total;
    }

    private static Map<String, Integer> countWords(
            Collection<String> words) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String word : words) {
            counts.merge(word, 1, Integer::sum);
        }
        return counts;
    }

    public static void main(String[] args) {
        Collection<String> words =
                new ArrayList<>(List.of("java", "map", "java"));
        String first = words.iterator().next();
        System.out.println("First word: " + first);

        Map<String, Integer> counts = countWords(words);
        System.out.println("Counts: " + counts);
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }
        System.out.println("Keys: " + counts.keySet());
        System.out.println("Values: " + counts.values());

        Collection<Integer> integers = List.of(1, 2, 3);
        List<Number> numbers = copyOfElements(integers);
        numbers.add(2.5);
        System.out.println("Numbers: " + numbers);
        System.out.println("Sum: " + sum(numbers));

        List<Object> destination = new ArrayList<>();
        appendIntegers(destination);
        System.out.println("Destination: " + destination);

        Map<String, List<Integer>> scores = new LinkedHashMap<>();
        scores.put("An", List.of(8, 9));
        scores.put("Binh", List.of(7, 10));
        System.out.println("Nested: " + scores);
        int firstScore = scores.get("An").get(0);
        System.out.println("First score of An: " + firstScore);

        var inferredObjects = new ArrayList<>();
        inferredObjects.add("text");
        inferredObjects.add(99);
        System.out.println("Diamond with var: " + inferredObjects);

        List<String> strings = new ArrayList<>();
        List<Integer> otherIntegers = new ArrayList<>();
        System.out.println("Same runtime class: "
                + strings.getClass().equals(otherIntegers.getClass()));

        Collection<?> unknown = List.of("A", "B");
        for (Object value : unknown) {
            System.out.println("Unknown element: " + value);
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:unchecked GenericCollectionMapDemo.java
java GenericCollectionMapDemo
```

Mã đã được kiểm tra với `--release 17` trên JDK mới hơn, không có cảnh báo unchecked. Các đoạn cố ý không biên dịch hoặc raw type trong phần giải thích không nằm trong chương trình này.

Stdout:

```text
First word: java
Counts: {java=2, map=1}
java=2
map=1
Keys: [java, map]
Values: [2, 1]
Numbers: [1, 2, 3, 2.5]
Sum: 8.5
Destination: [10, 20]
Nested: {An=[8, 9], Binh=[7, 10]}
First score of An: 8
Diamond with var: [text, 99]
Same runtime class: true
Unknown element: A
Unknown element: B
```

### Giải thích kết quả

- `Collection<String>` làm iterator trả `String` mà không cần ép kiểu thủ công.
- Hàm đếm dùng `Map<String, Integer>`: từ là khóa, số lần xuất hiện là giá trị.
- `merge(word, 1, Integer::sum)` đặt `1` nếu chưa có giá trị; nếu có giá trị phù hợp trong ví dụ, cộng thêm `1`. Kết quả là `java=2` và `map=1`.
- `entrySet`, `keySet` và `values` truyền đúng kiểu dữ liệu của map ra ngoài.
- `copyOfElements` tạo danh sách `Number` mới từ nguồn `Integer`. Sau khi thêm `2.5`, tổng là `8.5`.
- Đích `List<Object>` nhận được các `Integer` từ hàm có tham số `? super Integer`.
- Map lồng nhau giữ danh sách điểm; hai lần `get` đi qua hai tầng kiểu.
- `var` kết hợp `new ArrayList<>()` trong ngữ cảnh này tạo `ArrayList<Object>`, nên có cả chuỗi và số.
- Hai `ArrayList` tham số hóa khác nhau vẫn có cùng lớp runtime.
- `Collection<?>` cho duyệt dưới dạng `Object` mà không cần biết đối số kiểu cụ thể.

Chương trình dùng `LinkedHashMap` để thứ tự in theo chèn có thể đối chiếu; không dựa vào thứ tự không được cam kết của `HashMap`. [LinkedHashMap API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html)

## 14. Những điều generics không tự bảo đảm

| Mong đợi | Generics có tự bảo đảm không? | Cần thêm điều gì? |
| --- | --- | --- |
| Phần tử đúng kiểu qua lời gọi có kiểm tra kiểu | Có, trong phạm vi các quy tắc kiểu của chương trình | Tránh raw type và unchecked cast không an toàn |
| Phần tử khác `null` | Không | Chính sách và kiểm tra đầu vào |
| Collection sửa được | Không | Hợp đồng đối tượng/factory |
| Phần tử không trùng | Không | Chọn mô hình `Set` hoặc quy tắc nghiệp vụ |
| Thứ tự duyệt ổn định | Không | Giao diện/cài đặt có bảo đảm thích hợp |
| Nhiều luồng truy cập an toàn | Không | Đồng bộ và cấu trúc concurrent phù hợp |
| Các phần tử so sánh được | Không chỉ nhờ cùng một kiểu cha | `Comparable` hoặc comparator phù hợp |
| Giá trị nghiệp vụ hợp lệ | Không | Kiểu dữ liệu và validation |
| Bản sao sâu | Không | Thiết kế sao chép phần tử riêng |
| Nguồn ngoài được xác minh sau một cast | Không | Kiểm tra/chuyển đổi dữ liệu có chủ đích |

Generics là một lớp bảo đảm quan trọng, nhưng không thay thế toàn bộ hợp đồng dữ liệu.

## 15. Lỗi thường gặp

| Lỗi | Vì sao sai | Hướng sửa |
| --- | --- | --- |
| Viết `Collection<E>` khi `E` chưa được khai báo | `E` không tự có sẵn | Truyền kiểu thật hoặc khai báo tham số kiểu |
| Viết `List<int>` | Generic không nhận kiểu nguyên thủy | Dùng `Integer` |
| Gán `List<Integer>` sang `List<Number>` | Tham số kiểu bất biến | Dùng wildcard hoặc tạo bản sao phù hợp |
| Dùng `Collection<Object>` để nhận mọi collection | Không nhận `Collection<String>` | Dùng `Collection<?>` nếu chỉ cần quan sát |
| Dùng raw `Map` để né lỗi kiểu | Mất liên kết kiểu khóa–giá trị | Khai báo `Map<K, V>` phù hợp |
| Coi `? extends Number` cho thêm mọi `Number` | Kiểu thật có thể hẹp hơn | Dùng nguồn chỉ đọc kiểu Number; chọn `super` cho đích |
| Coi `? super Integer` đọc ra chắc chắn là `Integer` | Nguồn có thể giữ dữ liệu của kiểu cha | Đọc dưới dạng `Object` nếu chưa có bảo đảm khác |
| Cho rằng `?` tạo tính bất biến | Wildcard không ngăn mọi thao tác sửa | Chọn quyền sửa theo hợp đồng riêng |
| Dùng `var x = new ArrayList<>()` rồi kỳ vọng `String` | Không có ngữ cảnh ràng buộc đó | Nêu đối số kiểu hoặc kiểu đích |
| Coi `get(123)` biên dịch nghĩa `123` là khóa được `put` | Chữ ký truy vấn dùng `Object` | Phân biệt API truy vấn và API đưa dữ liệu vào |
| Dùng cast để chứng minh mọi phần tử đúng kiểu | Cast không xác minh nội dung từng phần tử | Chuyển đổi/kiểm tra tại ranh giới nguồn |

## 16. Tự kiểm tra và thực hành

### Câu hỏi

1. Trong `Collection<String>`, đâu là tham số kiểu và đâu là đối số kiểu?
2. `Map<String, List<Integer>>` có kiểu khóa và giá trị là gì?
3. Vì sao `Collection<Integer>` không gán trực tiếp được cho `Collection<Number>`?
4. `Collection<Object>` khác `Collection<?>` ở khả năng nhận gán và thêm phần tử thế nào?
5. Khi nào dùng `? extends Number`, khi nào dùng `? super Integer`?
6. `var x = new ArrayList<>();` trong ví dụ không có ngữ cảnh bổ sung suy luận kiểu gì?
7. Vì sao `Map.get` có thể nhận đối số không cùng `K` mà vẫn biên dịch?
8. `List<String>` có bảo đảm không có `null` và sửa được không?
9. Tại sao raw type làm lỗi có thể xuất hiện muộn?
10. Hai `ArrayList` có đối số kiểu khác nhau có cần lớp runtime khác nhau không?

### Bài tập

- Thêm một lời gọi sai kiểu vào chương trình hoàn chỉnh, biên dịch và giải thích lỗi trước khi bỏ lời gọi đó.
- Viết hàm in mọi phần tử bằng `Collection<?>`; kiểm tra với danh sách chuỗi và tập số nguyên.
- Cho hàm `sum` nhận `List<Integer>` rồi `List<Double>` mà không đổi chữ ký.
- Cho `appendIntegers` nhận `List<Integer>`, `List<Number>` và `List<Object>`; giải thích vì sao đều phù hợp về kiểu.
- Viết hàm sao chép map có chữ ký `static <K, V> Map<K, V> copyMap(Map<? extends K, ? extends V> source)`, trả một `LinkedHashMap` mới.
- Tạo `Map<String, List<Integer>>` cho hai học viên và duyệt bằng `Map.Entry` mà không ép kiểu.
- Bật `-Xlint:unchecked` trong một bài thử raw type, xác định ranh giới tạo cảnh báo và sửa để không còn cảnh báo.

### Gợi ý đối chiếu

1. `E` là tham số trong khai báo `Collection<E>`; `String` là đối số được truyền khi sử dụng.
2. Khóa `String`, giá trị `List<Integer>`; bên trong danh sách là `Integer`.
3. Nếu cho gán, mã có thể đưa phần tử kiểu Number khác Integer vào cùng đối tượng.
4. `Collection<Object>` giữ Object, còn `Collection<?>` quan sát một kiểu chưa biết; không phải cùng một kiểu.
5. Dùng `extends` cho nguồn cần đọc như Number; `super` cho đích cần nhận Integer.
6. `ArrayList<Object>`.
7. Chữ ký là `get(Object)`; điều đó không đổi kiểu khóa nhận bởi `put`.
8. Không; đó là các thuộc tính hợp đồng khác.
9. Bỏ qua kiểm tra kiểu có thể cho phần tử sai lọt vào rồi bị phát hiện lúc đọc.
10. Không; các đối số kiểu này được xử lý qua cơ chế type erasure.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="element-types-002">Autoboxing, unboxing và chi phí của kiểu bao</a>

<details>
<summary>Xem chi tiết</summary>

# Autoboxing, unboxing và chi phí của kiểu bao

Trong Java Collections Framework, đoạn mã sau trông như đang lưu trực tiếp các số nguyên:

```java
List<Integer> scores = new ArrayList<>();
scores.add(10);
int first = scores.get(0);
```

Nhưng kiểu phần tử của danh sách là `Integer`, còn literal `10` và biến `first` có kiểu `int`. Java cho phép viết gọn nhờ chuyển đổi tự động giữa kiểu nguyên thủy và kiểu bao.

Sự tiện lợi này không xóa khác biệt giữa giá trị nguyên thủy và tham chiếu đối tượng. Những khác biệt đó thể hiện ở khả năng nhận `null`, phép so sánh, lựa chọn overload, dung lượng dữ liệu và chi phí xử lý.

Bài này dùng Java 17, tập trung vào cách boxing và unboxing xuất hiện khi dùng collection. Chương trình đầy đủ có lệnh biên dịch và kết quả chuẩn ở phần thực nghiệm. Các đoạn ngắn khác chỉ minh họa từng điểm; đoạn được ghi rõ “không biên dịch” là ví dụ lỗi có chủ ý.

## 1. Vì sao collection số cần kiểu bao?

### 1.1. Kiểu nguyên thủy và kiểu tham chiếu

`int` biểu diễn một giá trị số nguyên. `Integer` là lớp bao biểu diễn một giá trị `int` trong một đối tượng.

Với generic của Java 17, tham số kiểu phải là kiểu tham chiếu. Do đó:

```java
List<Integer> scores = new ArrayList<>();
```

là hợp lệ, còn khai báo dưới đây không biên dịch:

```java
List<int> scores;
```

Collection như `ArrayList<Integer>` tổ chức các phần tử dưới dạng tham chiếu đến `Integer`, không phải một mảng chuyên biệt chứa trực tiếp các giá trị `int`.

### 1.2. Các cặp kiểu thường gặp

| Kiểu nguyên thủy | Kiểu bao |
| --- | --- |
| `boolean` | `Boolean` |
| `byte` | `Byte` |
| `short` | `Short` |
| `char` | `Character` |
| `int` | `Integer` |
| `long` | `Long` |
| `float` | `Float` |
| `double` | `Double` |

Tên lớp không phải lúc nào cũng chỉ là viết hoa ký tự đầu: `int` đi với `Integer`, `char` đi với `Character`.

### 1.3. Vì sao không chỉ dùng mảng nguyên thủy?

`int[]` phù hợp với nhiều bài toán số học, nhưng không có cùng API và mô hình tổ chức dữ liệu như `List<Integer>`, `Set<Integer>` hay `Map<String, Integer>`.

Kiểu bao giúp các giá trị số tham gia một framework chung: có thể làm phần tử, khóa, giá trị và được truyền qua các API nhận đối tượng. Lợi ích là tính tương tác cùng khả năng diễn đạt dữ liệu; chi phí là các lớp chuyển đổi và biểu diễn đối tượng cần được hiểu rõ.

## 2. Boxing và autoboxing

**Boxing** là chuyển từ kiểu nguyên thủy sang kiểu bao tương ứng. **Autoboxing** là cách gọi thông dụng khi Java thực hiện chuyển đổi này tự động trong một ngữ cảnh cho phép.

Ví dụ:

```java
int raw = 42;
Integer boxed = raw;
```

Có thể hình dung cách viết tương đương về giá trị:

```java
Integer boxed = Integer.valueOf(raw);
```

Đây là cách diễn đạt để hiểu chuyển đổi; hợp đồng ngôn ngữ không nên được thay bằng một giả định rằng mọi compiler phải sinh đúng một chuỗi bytecode cố định.

### 2.1. Khi thêm số vào collection

Đoạn minh họa:

```java
List<Integer> values = new ArrayList<>();
values.add(42);
```

Tham số phần tử cần `Integer`, còn `42` có kiểu `int`; boxing xảy ra tại ranh giới này.

Với map:

```java
Map<Integer, Integer> counts = new HashMap<>();
counts.put(7, 1);
```

Cả khóa và giá trị đều được biểu diễn bằng kiểu bao. Không nên nhìn hai literal nguyên thủy rồi kết luận map đang lưu trực tiếp hai giá trị `int` trong cấu trúc chuyên biệt.

### 2.2. Boxing không đồng nghĩa luôn cấp phát một đối tượng mới

Một giá trị có thể dùng đối tượng đã được lưu đệm. Trong một số hoàn cảnh, JVM cũng có thể tối ưu các đối tượng trung gian nếu vẫn giữ đúng hành vi quan sát được.

Vì vậy, phát biểu “mỗi lần viết một số vào `List<Integer>` chắc chắn tạo một đối tượng mới” là quá mạnh. Cần phân biệt:

- Có chuyển đổi boxing theo ngữ nghĩa.
- Có lời gọi hoặc cơ chế tạo giá trị bao.
- Có đối tượng mới thực sự được cấp phát khi chạy.
- Đối tượng đó còn tồn tại bao lâu.

Các quy tắc chuyển đổi được nêu trong [JLS 17, mục Boxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.7).

## 3. Unboxing: từ tham chiếu về giá trị nguyên thủy

**Unboxing** lấy giá trị nguyên thủy từ kiểu bao tương ứng.

Ví dụ:

```java
Integer boxed = Integer.valueOf(42);
int raw = boxed;
```

Có thể hiểu tương tự:

```java
int raw = boxed.intValue();
```

Khi đọc collection:

```java
int score = scores.get(0);
```

`get` trả `Integer`; phép gán sang `int` yêu cầu unboxing.

### 3.1. Unboxing trong vòng lặp

Đoạn minh họa:

```java
int sum = 0;
for (int value : values) {
    sum += value;
}
```

Nếu `values` là `List<Integer>`, mỗi phần tử được đọc dưới dạng tham chiếu rồi được dùng như `int`.

Đổi biến vòng lặp thành `Integer` không tự loại bỏ unboxing nếu thân vòng lặp vẫn làm số học:

```java
for (Integer value : values) {
    sum += value;
}
```

Ở đây, việc dùng `value` trong phép cộng vẫn yêu cầu giá trị nguyên thủy.

### 3.2. Unboxing không tạo một wrapper mới

Unboxing đọc giá trị từ wrapper; bản thân chuyển đổi này không phải bước cấp phát wrapper.

Chi phí thực tế có thể gồm truy cập tham chiếu, lấy giá trị và kiểm tra điều kiện cần thiết. JVM có thể nội tuyến hoặc tối ưu một số bước. Không nên gộp toàn bộ chi phí của một đoạn mã số học thành “chi phí unboxing” nếu đoạn đó còn có cấp phát, tra cứu collection hoặc thuật toán khác.

### 3.3. Có thể unbox rồi mở rộng kiểu nguyên thủy

Ví dụ:

```java
Integer boxed = 42;
long total = boxed;
```

Trường hợp này có thể unbox `Integer` thành `int`, rồi mở rộng sang `long`.

Điều đó khác với tự chuyển từ wrapper này sang wrapper khác. `Integer` không tự biến thành `Long` chỉ vì hai lớp đều biểu diễn số.

## 4. null: lỗi quan trọng nhất khi unboxing

### 4.1. Wrapper có thể là null, primitive thì không

Một biến `Integer` có thể không trỏ tới đối tượng nào:

```java
Integer count = null;
int primitive = count;
```

Lời gán thứ hai ném `NullPointerException`. Không tồn tại quy tắc tự chuyển `null` thành 0.

Điều tương tự áp dụng cho các cặp khác, ví dụ `Boolean` sang `boolean`. Quy tắc được nêu trực tiếp trong [JLS 17, mục Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.8).

### 4.2. Map.get có thể trả null

Với `Map<String, Integer>`:

```java
int count = counts.get("missing");
```

có thể lỗi tại bước unboxing nếu không có ánh xạ hoặc nếu map cho phép giá trị `null` và khóa đang gắn với `null`.

Nên tách bước đọc và chính sách:

```java
Integer result = counts.get("missing");
int count = result == null ? 0 : result;
```

Ví dụ trên chủ động coi cả thiếu giá trị và giá trị `null` là 0. Đây là quyết định nghiệp vụ, không phải quy tắc đúng cho mọi chương trình.

Nếu cần phân biệt “chưa có khóa” với “khóa có nhưng chưa biết số lượng”, phải giữ sự phân biệt đó bằng dữ liệu hoặc kiểm tra bổ sung.

### 4.3. getOrDefault không thay mọi null bằng giá trị mặc định

Đoạn minh họa:

```java
Map<String, Integer> counts = new HashMap<>();
counts.put("unknown", null);

int count = counts.getOrDefault("unknown", 0);
```

Khóa `"unknown"` đã có ánh xạ với `null`, nên `getOrDefault` trả giá trị đang gắn với khóa. Gán kết quả sang `int` tiếp tục gây lỗi unboxing.

Đừng dùng tên “default” để suy ra nó là thao tác thay mọi `null`. Xem [Map.getOrDefault trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#getOrDefault(java.lang.Object,V)).

### 4.4. Boolean trong điều kiện

Đoạn minh họa:

```java
Boolean enabled = settings.get("enabled");
if (enabled) {
    startFeature();
}
```

Điều kiện `if` cần `boolean`; nếu `enabled` là `null`, có lỗi unboxing.

Nếu chính sách thực sự là “chỉ bật khi rõ ràng là true”, có thể dùng `Boolean.TRUE.equals(enabled)`. Cách đó cố ý coi `null` như chưa bật; không nên áp dụng khi thiếu cấu hình phải là lỗi cần báo.

### 4.5. Ternary có thể unbox trước khi gán sang wrapper

Đoạn minh họa:

```java
Integer nullable = null;
boolean useNullable = true;
Integer chosen = useNullable ? nullable : 0;
```

Mặc dù `chosen` là `Integer`, hai nhánh của toán tử điều kiện có kiểu khác nhau: một nhánh là `Integer`, một nhánh là `int`. Quy tắc của biểu thức số có thể yêu cầu unboxing nhánh `nullable`, rồi mới boxing kết quả cho phép gán. Khi nhánh đó được chọn và giá trị là `null`, chương trình ném `NullPointerException`.

Muốn giữ kiểu tham chiếu và cho phép `null`, phải viết các nhánh phù hợp với ý định, chẳng hạn dùng giá trị mặc định đã có kiểu `Integer`, hoặc tách thành `if`–`else` rõ ràng.

Muốn thay `null` bằng 0, kiểm tra chính `nullable == null` trước khi unbox. Quy tắc xác định kiểu của `?:` nằm trong [JLS 17, Conditional Operator](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.25).

## 5. Kiểu bao bất biến và phép tăng, cộng

### 5.1. Integer không phải một ô số có thể sửa tại chỗ

Đoạn minh họa:

```java
Integer counter = 1000;
Integer previous = counter;
counter++;
```

Sau đó `previous` vẫn biểu diễn 1000, còn `counter` biểu diễn 1001.

Để hiểu, có thể tách ý nghĩa của phép tăng:

1. Lấy giá trị `int` từ `counter`.
2. Tính kết quả tăng.
3. Chuyển kết quả sang kiểu bao cần thiết.
4. Gán lại tham chiếu cho biến `counter`.

Phép tăng không sửa giá trị bên trong đối tượng mà `previous` đang giữ.

### 5.2. Biến cộng dồn kiểu bao tạo thêm ranh giới chuyển đổi

So sánh hai đoạn minh họa:

```java
Integer boxedSum = 0;
for (int value : values) {
    boxedSum += value;
}
```

và:

```java
int primitiveSum = 0;
for (int value : values) {
    primitiveSum += value;
}
```

Nếu `values` là `List<Integer>`, cả hai vẫn unbox phần tử đầu vào.

Đoạn đầu còn phải xử lý biến tổng dưới dạng wrapper: lấy giá trị hiện tại để cộng rồi biểu diễn kết quả lại thành `Integer`. Đoạn sau giữ tổng ở kiểu nguyên thủy và chỉ cần boxing khi thực sự đi qua API yêu cầu đối tượng.

Đây là lý do nên ưu tiên biến tính toán cục bộ kiểu nguyên thủy khi không cần ý nghĩa `null` hoặc một API bắt buộc wrapper. Không khẳng định số lượng đối tượng cấp phát chỉ từ số vòng lặp, vì cache và tối ưu JVM có thể tác động.

### 5.3. Wrapper không mở rộng miền số của primitive tương ứng

Phép cộng sau khi unbox `Integer` vẫn có quy tắc số học của `int`. Dùng `Integer` không tự chống tràn số.

Với tổng lớn, cần chọn kiểu phép tính phù hợp, chẳng hạn cộng dồn bằng `long`, hoặc kiểm tra tràn theo yêu cầu. Chỉ thay kiểu biến nhận ở cuối không luôn sửa được một phép tính đã tràn ở bước trước.

## 6. ==, equals và bộ nhớ đệm

### 6.1. Hai toán hạng wrapper có thể đang được so sánh tham chiếu

Đoạn minh họa:

```java
Integer a = 100;
Integer b = 100;
boolean sameReference = a == b;
```

Ở đây `==` so sánh tham chiếu vì cả hai toán hạng đều có kiểu `Integer`.

Với hai literal hằng 100, có bảo đảm dùng chung tham chiếu theo quy tắc boxing của những giá trị được quy định. Kết quả `true` không biến `==` thành phép so sánh giá trị tổng quát cho mọi wrapper.

### 6.2. Không dựa vào kết quả của giá trị ngoài vùng bảo đảm

`Integer.valueOf(int)` luôn lưu đệm các giá trị từ -128 đến 127 và có thể lưu đệm thêm. Vì vậy, không viết bài kiểm tra hay nghiệp vụ phụ thuộc kết quả `==` của hai wrapper có cùng giá trị 1000 là `false`.

Có hai lớp bảo đảm cần phân biệt:

- JLS quy định một số điều kiện dùng chung tham chiếu khi boxing các biểu thức hằng.
- API `Integer.valueOf` mô tả chính sách lưu đệm của phương thức.

Cả hai không cho phép dùng danh tính đối tượng làm thay thế cho so sánh giá trị. Xem [Integer.valueOf trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#valueOf(int)).

### 6.3. So sánh giá trị cùng kiểu

Nếu hai giá trị có kiểu `Integer` và muốn so sánh giá trị có xét `null`, có thể dùng:

```java
boolean sameValue = Objects.equals(a, b);
```

Hoặc gọi `a.equals(b)` khi đã bảo đảm `a` không `null`.

`Integer.equals` yêu cầu đối số là `Integer` có cùng giá trị, nên:

```java
Integer.valueOf(1).equals(Long.valueOf(1L))
```

là `false`. `Objects.equals` không tự chuẩn hóa mọi kiểu số về một kiểu chung.

### 6.4. Khi một phía là primitive

Đoạn minh họa:

```java
Integer boxed = 10;
boolean equal = boxed == 10;
```

Ở biểu thức số này, giá trị bao được unbox để so sánh. Nếu `boxed` là `null`, có thể ném `NullPointerException`.

Vì thế, thay một phía của phép so sánh từ `Integer` sang `int` có thể đổi cả loại phép so sánh lẫn khả năng phát sinh lỗi.

### 6.5. Không dùng wrapper làm khóa đồng bộ dựa vào danh tính

Các đối tượng bao có thể được chia sẻ và được thiết kế như giá trị. Dùng chúng làm đối tượng khóa hoặc dựa vào danh tính để đại diện quyền sở hữu tạo phụ thuộc không đúng với mục đích kiểu dữ liệu.

Chọn một cơ chế đồng bộ hoặc một đối tượng khóa có ý nghĩa rõ ràng thay vì tận dụng một giá trị số được boxing.

## 7. Overload: Java chọn phương thức trước khi chạy

### 7.1. Bẫy remove của `List<Integer>`

`List` có cả:

```java
E remove(int index);
boolean remove(Object value);
```

Đây là hai khai báo để đọc hợp đồng, không phải nội dung đặt trực tiếp vào `main`.

Với:

```java
List<Integer> values = new ArrayList<>(List.of(10, 20, 30));
values.remove(1);
```

đối số `1` có kiểu `int`, nên phương thức xóa theo chỉ số được chọn. Phần tử bị xóa là 20.

Muốn xóa giá trị 30:

```java
boolean removed = values.remove(Integer.valueOf(30));
```

Không viết `values.remove(30)` rồi kỳ vọng xóa số 30; đó là yêu cầu xóa chỉ số 30 và có thể vượt phạm vi.

### 7.2. Thay kiểu biến có thể đổi overload

Đoạn minh họa:

```java
int index = 1;
Integer value = 1;
```

Truyền `index` vào `remove` khác truyền `value`. Khi đọc mã, cần xem kiểu tại thời điểm biên dịch, không chỉ xem giá trị in ra đều là 1.

### 7.3. Mở rộng primitive không giống mở rộng rồi boxing tùy ý

Đoạn hợp lệ:

```java
List<Long> values = new ArrayList<>();
values.add(1L);
```

Đoạn dưới không biên dịch:

```java
List<Long> values = new ArrayList<>();
values.add(1);
```

Literal `1` là `int`; cơ chế chuyển đổi đối số không tự thực hiện chuỗi tùy ý `int → long → Long` để đáp ứng yêu cầu này.

Có thể dùng literal `1L`, chuyển biểu thức sang `long` trước khi boxing, hoặc gọi hàm tạo giá trị phù hợp. Đọc ngữ cảnh chuyển đổi thay vì coi autoboxing là một bộ chuyển đổi số vạn năng.

## 8. Khóa số trong Map và phần tử trong Set

### 8.1. Tra cứu bằng Object vẫn cần đúng kiểu giá trị

Với:

```java
Map<Integer, String> names = Map.of(1, "one");
```

`names.get(1)` có thể boxing đối số thành `Integer` phù hợp với khóa đang có.

Nhưng:

```java
names.get(1L);
```

truyền một `Long` qua tham số kiểu `Object`. Nó không tự biến thành `Integer` để phù hợp ý định tra cứu.

Với map trong ví dụ, không có khóa `Long` tương ứng; kết quả là `null`. Phép tra cứu biên dịch được vì chữ ký nhận `Object`, không phải vì hai kiểu số đã được thống nhất.

### 8.2. Cùng số học chưa chắc cùng equals

Một tập `Set<Number>` có thể chứa cả `Integer.valueOf(1)` và `Long.valueOf(1)`. Đối với bình đẳng của hai kiểu bao này, chúng không phải cùng một giá trị đối tượng.

Nếu miền nghiệp vụ cần coi các biểu diễn số khác nhau là tương đương, phải chuẩn hóa kiểu hoặc xây dựng quan hệ đối chiếu rõ ràng. Không dựa vào boxing để làm việc đó.

### 8.3. Chi phí tại mỗi lần tra cứu

Một lời gọi tra cứu bằng khóa primitive vào map dùng khóa wrapper có ranh giới boxing ở cấp mã nguồn. Tùy giá trị và JVM, đối tượng tạm có thể dùng cache hoặc được tối ưu.

Không kết luận chỉ nhìn lời gọi `get` rằng chắc chắn có một đối tượng mới tồn tại lâu dài. Nhưng cũng không bỏ qua chi phí này khi đường nóng thực hiện hàng triệu phép tra cứu và số liệu đo cho thấy nó đáng kể.

## 9. Chi phí của kiểu bao gồm những gì?

### 9.1. Biểu diễn dữ liệu

Có thể hình dung khác biệt giữa mảng primitive và danh sách wrapper:

```text
int[]:
    một đối tượng mảng chứa các giá trị int

ArrayList<Integer>:
    đối tượng ArrayList
        -> mảng lưu các tham chiếu
              -> các đối tượng Integer được tham chiếu
```

Đây là mô hình khái niệm về các thành phần biểu diễn, không phải sơ đồ byte chính xác cho mọi JVM.

`ArrayList` có sức chứa có thể lớn hơn số phần tử đang dùng. Ngoài đối tượng bao, còn phải tính cấu trúc list và vùng tham chiếu. `Integer[]` cũng là mảng tham chiếu đến wrapper; nó không giống `int[]`.

### 9.2. Bộ nhớ không có một tỉ lệ cố định cho mọi JVM

`int` có miền biểu diễn 32 bit, nhưng từ đó không thể kết luận chính xác một triệu `Integer` sẽ chiếm bao nhiêu byte trên mọi cấu hình.

Kích thước thực tế phụ thuộc:

- Header đối tượng và header mảng.
- Độ rộng tham chiếu.
- Căn chỉnh đối tượng.
- Sức chứa chưa dùng của collection.
- Số đối tượng wrapper thực sự khác nhau được tham chiếu.
- Đối tượng dùng chung từ cache hoặc từ nơi khác.
- Cách JVM tối ưu và bố trí bộ nhớ.

Hai vị trí cùng tham chiếu một `Integer` không có nghĩa tồn tại hai đối tượng bao riêng. Ngược lại, cùng một giá trị ngoài cache có thể được biểu diễn bằng nhiều đối tượng khác nhau. Phải phân biệt số phần tử, số giá trị khác nhau và số đối tượng khác nhau.

### 9.3. Cấp phát và thu gom rác

Nếu một phép tính tạo nhiều đối tượng bao không được giữ lâu, có thể tăng tốc độ cấp phát và lượng dữ liệu cần thu gom.

Nếu một collection giữ nhiều wrapper lâu dài, chúng có thể ảnh hưởng tập đối tượng sống và dung lượng heap. Hai vấn đề này khác nhau: cấp phát nhiều trong thời gian ngắn không giống giữ dữ liệu lớn lâu dài.

Không phải mọi đối tượng tạm đều còn tồn tại sau tối ưu. Tuy nhiên, đối tượng đã được đưa vào một cấu trúc có thể quan sát được không thể mặc nhiên coi là đã bị loại bỏ.

### 9.4. Truy cập dữ liệu và tính cục bộ

Với dữ liệu số lớn, đi qua các tham chiếu và đối tượng riêng có thể có đặc điểm truy cập bộ nhớ khác mảng primitive. Cách sử dụng bộ nhớ đệm CPU và lượng dữ liệu phải đọc có thể ảnh hưởng tổng thời gian.

Đây là lý do chi phí kiểu bao không chỉ là một lời gọi `intValue()`. Nó có thể nằm ở toàn bộ hình dạng dữ liệu.

### 9.5. Big-O không mô tả mọi chi phí

Một vòng lặp cộng `int[]` và một vòng lặp cộng `List<Integer>` có thể đều là `O(n)`. Điều đó không bảo đảm thời gian, dung lượng hoặc lượng cấp phát giống nhau.

Ngược lại, thuật toán tìm kiếm tốt trên map wrapper có thể hiệu quả hơn thuật toán tuyến tính trên mảng primitive. Trước khi tối ưu boxing, phải chọn đúng cấu trúc và thuật toán cho yêu cầu.

## 10. Giảm chi phí mà vẫn giữ đúng mô hình dữ liệu

### 10.1. Giữ phép tính cục bộ ở primitive

Khi không cần biểu diễn `null`, biến đếm và cộng dồn thường nên là `int` hoặc `long` phù hợp miền giá trị.

Chỉ boxing khi phải đi qua API đối tượng. Cách đó làm ranh giới rõ hơn và tránh đặt biến trung gian trong một chu trình unbox–tính toán–box không cần thiết.

### 10.2. Chọn mảng primitive khi bài toán phù hợp

`int[]`, `long[]` hoặc `double[]` phù hợp khi:

- Dữ liệu chủ yếu phục vụ tính toán số.
- Có thể quản lý kích thước và cập nhật theo mô hình mảng.
- Không cần `null` cho từng phần tử.
- Không cần trực tiếp các ngữ nghĩa tập hợp hoặc ánh xạ của collection.

Không đổi mọi `List<Integer>` thành mảng chỉ vì nghe nói boxing tốn chi phí. Phải tính cả độ rõ ràng, tính linh hoạt và chi phí chuyển đổi ở ranh giới API.

### 10.3. Dùng primitive stream đúng chỗ

`IntStream`, `LongStream`, `DoubleStream` cung cấp luồng chuyên biệt cho số nguyên thủy.

Ví dụ:

```java
int sum = IntStream.of(1, 2, 3).sum();
```

Không cần biểu diễn từng phần tử trung gian bằng `Integer` trong một `Stream<Integer>` chỉ để cộng.

Nếu nguồn đã là danh sách wrapper:

```java
int sum = values.stream().mapToInt(Integer::intValue).sum();
```

vẫn cần unbox khi đi từ phần tử `Integer` sang `int`. Cách viết không tự xóa các wrapper đã nằm trong nguồn, và vẫn cần chính sách nếu có `null`.

Nếu tổng cần `long`, chọn dạng tính tổng tương ứng; `IntStream.sum()` trả `int` và không tự giải quyết mọi vấn đề tràn.

### 10.4. boxed đưa dữ liệu trở lại dạng đối tượng

Đoạn minh họa:

```java
List<Integer> result = IntStream.of(1, 2, 3).boxed().toList();
```

Phương thức `boxed()` chuyển luồng primitive sang luồng wrapper để tạo collection yêu cầu tham chiếu.

Không vừa chuyển sang primitive để tránh boxing, vừa gọi `boxed` ngay sau đó mà không có nhu cầu. Nhưng nếu API đầu ra cần `List<Integer>`, đây có thể là ranh giới hợp lý. Xem [IntStream Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/IntStream.html).

### 10.5. Dung lượng khởi tạo không loại boxing

`new ArrayList<Integer>(expectedSize)` có thể giảm số lần tăng sức chứa và sao chép vùng tham chiếu khi thêm nhiều phần tử.

Nó không biến vùng lưu trữ thành mảng `int`, không tự tạo trước các wrapper và không loại bỏ các chuyển đổi tại ranh giới phần tử.

Cần tách tối ưu sức chứa collection khỏi tối ưu kiểu biểu diễn phần tử.

### 10.6. Cấu trúc chuyên biệt primitive là một lựa chọn riêng

Khi dữ liệu số lớn và số liệu đo cho thấy wrapper là nút thắt, có thể đánh giá các cấu trúc chuyên biệt primitive. Đó có thể là mảng hoặc thư viện ngoài phù hợp.

Việc chọn thư viện cần xét API, ngữ nghĩa thiếu giá trị, khả năng tích hợp và chi phí chuyển đổi. Chuyên biệt primitive không tự bảo đảm phù hợp mọi yêu cầu nghiệp vụ.

## 11. Đo hiệu năng sao cho kết luận có giá trị

### 11.1. Đặt câu hỏi cụ thể

Không chỉ hỏi “boxing chậm bao nhiêu?”. Hãy chọn một công việc:

- Xây collection một lần rồi đọc nhiều lần.
- Thêm và xóa liên tục.
- Tính tổng trên dữ liệu đã có.
- Tra cứu khóa số.
- Cập nhật bộ đếm.
- Chuyển dữ liệu qua ranh giới API.

Mỗi công việc có thể có nguồn chi phí khác nhau.

### 11.2. Phân biệt dựng dữ liệu với xử lý dữ liệu

Nếu đang đo vòng lặp tính tổng, dựng `List<Integer>` bên ngoài hoặc bên trong phép đo là hai thí nghiệm khác nhau.

Nếu mục tiêu là toàn bộ đường xử lý của ứng dụng, chi phí dựng dữ liệu có thể phải được tính vào. Nếu mục tiêu là so sánh thao tác đọc, cần tách phần đó ra rõ ràng.

### 11.3. Dùng dữ liệu đại diện

Các yếu tố cần thay đổi có chủ đích:

- Kích thước dữ liệu.
- Giá trị nằm trong hoặc ngoài vùng cache thông dụng.
- Số lần lặp và mức tái sử dụng đối tượng.
- Có hoặc không có `null`.
- Tần suất đọc so với ghi.
- Kết quả có được giữ hoặc trả ra ngoài không.

Một thí nghiệm chỉ dùng các số nhỏ được lưu đệm dễ khiến kết luận về cấp phát không đại diện cho dữ liệu thật.

### 11.4. Tránh phép đo thủ công gây hiểu nhầm

Thời gian lần chạy đầu chịu ảnh hưởng nạp lớp, khởi tạo và biên dịch tối ưu. Mã không dùng kết quả có thể bị tối ưu theo cách làm thí nghiệm không còn đo công việc mong muốn.

Khi cần microbenchmark trên JVM, dùng công cụ chuyên dụng như [OpenJDK JMH](https://github.com/openjdk/jmh), với khởi động, các lượt chạy và cách tiêu thụ kết quả phù hợp. Theo dõi thêm lượng cấp phát hoặc GC nếu câu hỏi liên quan bộ nhớ.

Bài này không đưa ra con số chênh lệch thời gian hoặc dung lượng vì chúng cần môi trường, cấu hình JVM và khối lượng công việc cụ thể. Đo mới giúp quyết định thay đổi có đáng làm hay không.

## 12. Chương trình Java hoàn chỉnh

Chương trình sau quan sát boxing, unboxing, lựa chọn overload và các lỗi `null`. Nó không phải benchmark và không dùng thời gian chạy để suy ra chi phí.

Lưu vào tệp `BoxingInCollectionsDemo.java`:

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

public class BoxingInCollectionsDemo {
    public static void main(String[] args) {
        List<Integer> scores = new ArrayList<>();
        scores.add(10);
        scores.add(20);

        int sum = 0;
        for (int score : scores) {
            sum += score;
        }
        System.out.println("scores: " + scores);
        System.out.println("primitive sum: " + sum);

        Integer smallA = 100;
        Integer smallB = 100;
        System.out.println("small cached identity: " + (smallA == smallB));

        Integer largeA = 1000;
        Integer largeB = 1000;
        System.out.println("large value equality: "
                + Objects.equals(largeA, largeB));

        Integer counter = 1000;
        Integer previous = counter;
        counter++;
        System.out.println("previous: " + previous + ", counter: " + counter);

        List<Integer> ids = new ArrayList<>(List.of(10, 20, 30));
        int removedByIndex = ids.remove(1);
        boolean removedByValue = ids.remove(Integer.valueOf(30));
        System.out.println("removed by index: " + removedByIndex);
        System.out.println("removed by value: " + removedByValue);
        System.out.println("remaining ids: " + ids);

        Map<String, Integer> counts = new HashMap<>();
        counts.put("known", 2);

        Integer missing = counts.get("missing");
        try {
            int count = missing;
            System.out.println("unexpected count: " + count);
        } catch (NullPointerException ex) {
            System.out.println("missing value unboxing: NullPointerException");
        }

        counts.put("unknown", null);
        try {
            int count = counts.getOrDefault("unknown", 0);
            System.out.println("unexpected default: " + count);
        } catch (NullPointerException ex) {
            System.out.println("mapped null with default: NullPointerException");
        }

        Integer optionalCount = counts.get("unknown");
        int safeCount = optionalCount == null ? 0 : optionalCount;
        System.out.println("explicit null policy: " + safeCount);

        Map<Integer, String> names = Map.of(1, "one");
        System.out.println("Integer key: " + names.get(1));
        System.out.println("Long key present: " + names.containsKey(1L));

        int streamSum = IntStream.of(1, 2, 3).sum();
        List<Integer> boxedValues = IntStream.of(1, 2, 3).boxed().toList();
        System.out.println("IntStream sum: " + streamSum);
        System.out.println("boxed stream values: " + boxedValues);

        List<Long> longValues = new ArrayList<>();
        longValues.add(1L);
        System.out.println("Long elements: " + longValues);

        Integer nullable = null;
        boolean useNullable = true;
        try {
            Integer chosen = useNullable ? nullable : 0;
            System.out.println("unexpected chosen: " + chosen);
        } catch (NullPointerException ex) {
            System.out.println("mixed conditional: NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 BoxingInCollectionsDemo.java
java BoxingInCollectionsDemo
```

Kết quả chuẩn:

```text
scores: [10, 20]
primitive sum: 30
small cached identity: true
large value equality: true
previous: 1000, counter: 1001
removed by index: 20
removed by value: true
remaining ids: [10]
missing value unboxing: NullPointerException
mapped null with default: NullPointerException
explicit null policy: 0
Integer key: one
Long key present: false
IntStream sum: 6
boxed stream values: [1, 2, 3]
Long elements: [1]
mixed conditional: NullPointerException
```

### 12.1. Ranh giới chuyển đổi

`scores.add(10)` và `scores.add(20)` cần boxing. Vòng lặp dùng biến `int score` cần unboxing phần tử đọc được.

Biến `sum` giữ giá trị nguyên thủy trong phép cộng, không cần giữ tổng bằng `Integer` ở mỗi lượt.

### 12.2. So sánh có chủ đích

Dòng `small cached identity` dùng hai hằng 100 nằm trong trường hợp được bảo đảm dùng chung tham chiếu.

Với 1000, chương trình chỉ in bình đẳng giá trị qua `Objects.equals`; nó không đóng đinh kết quả `==` vốn không nên được dùng làm quy tắc nghiệp vụ cho trường hợp này.

### 12.3. Lỗi null ở những vị trí khác nhau

Ba khối `try` tách riêng:

- Unbox kết quả thiếu từ map.
- Unbox giá trị `null` mà `getOrDefault` giữ lại vì khóa đã có.
- Unbox trong biểu thức điều kiện hỗn hợp wrapper–primitive.

Việc bắt ngoại lệ chỉ phục vụ thực nghiệm để chương trình tiếp tục in các trường hợp khác. Trong mã nghiệp vụ, nên quyết định chính sách dữ liệu trước và kiểm tra phù hợp, không dùng bắt `NullPointerException` làm cách xử lý thiếu giá trị thông thường.

### 12.4. API nhận Object không thống nhất các kiểu số

Map có khóa `Integer` tìm được `1`, nhưng không có khóa `Long` cho `1L`. Cả hai lời gọi đều biên dịch; kết quả khác nhau đến từ ngữ nghĩa kiểu và bình đẳng.

### 12.5. Primitive stream và ranh giới đầu ra

Tính tổng bằng `IntStream` làm việc trên luồng primitive. Khi gọi `boxed().toList()`, chương trình chủ động chuyển sang các phần tử wrapper cho kết quả dạng danh sách.

Không dùng output `[1, 2, 3]` để suy ra số đối tượng thực sự được cấp phát. Cần công cụ đo mới trả lời được câu hỏi đó trên JVM cụ thể.

## 13. Những lỗi cần nhận ra khi đọc mã collection số

### 13.1. Dùng wrapper chỉ vì collection dùng wrapper

Một danh sách cần `Integer` không có nghĩa mọi biến đếm, chỉ số hoặc kết quả tính toán trung gian cũng nên là `Integer`.

Giữ primitive ở nơi giá trị luôn tồn tại và chỉ boxing khi cần đi qua API tham chiếu.

### 13.2. Thay null bằng 0 mà không xét nghĩa

0 có thể là số lượng thật; `null` có thể là chưa biết, thiếu dữ liệu hoặc không áp dụng.

Nếu gộp các trạng thái này, chương trình có thể tránh ngoại lệ nhưng đưa ra kết quả nghiệp vụ sai. Chọn chính sách dữ liệu trước khi chọn biểu thức viết ngắn.

### 13.3. Dùng == vì một ví dụ số nhỏ chạy đúng

Cache khiến lỗi khó lộ trên dữ liệu nhỏ. Cần chọn phép so sánh theo mục đích: danh tính tham chiếu, giá trị cùng kiểu, hay so sánh số sau chuẩn hóa.

Không lấy kết quả của một giá trị làm quy tắc cho mọi wrapper.

### 13.4. Nghĩ ++ làm đối tượng Integer thay đổi tại chỗ

Wrapper là giá trị bất biến. Tăng biến wrapper có nghĩa cập nhật biến hoặc vị trí nhận kết quả, không sửa mọi tham chiếu đang cùng trỏ đối tượng cũ.

Trong một vòng `for-each` với biến `Integer value`, gọi `value++` không tự cập nhật phần tử trong list. Muốn thay nội dung collection phải dùng thao tác cập nhật phù hợp.

### 13.5. Tối ưu sai nguồn chi phí

Một chương trình chậm vì tìm tuyến tính lặp lại không nhất thiết được sửa bằng loại boxing. Một chương trình dùng quá nhiều bộ nhớ có thể còn do sức chứa lớn, các cấu trúc bao khác hoặc giữ dữ liệu không cần thiết.

Đo và phân tích toàn đường xử lý, đồng thời giữ nguyên kết quả nghiệp vụ khi thay biểu diễn.

## 14. Bài tập và tiêu chí tự kiểm tra

### Bài 1: Đánh dấu vị trí chuyển đổi

Với đoạn sau:

```java
List<Integer> values = new ArrayList<>();
values.add(5);
values.add(8);

long sum = 0;
for (int value : values) {
    sum += value;
}

Integer result = values.get(0) + values.get(1);
```

Hãy chỉ ra nơi boxing và unboxing.

Đáp án định hướng:

- Hai lần thêm literal cần boxing để phù hợp phần tử `Integer`.
- Biến vòng lặp `int` yêu cầu unboxing.
- Phép cộng cuối unbox hai giá trị, cộng theo kiểu số tương ứng rồi boxing kết quả cho biến `Integer`.
- Không suy ra số đối tượng cấp phát thực tế chỉ từ số chuyển đổi.

### Bài 2: Xây chính sách cho dữ liệu thiếu

Cho map có:

```text
"known"   -> 3
"unknown" -> null
"missing" -> không có ánh xạ
```

Thiết kế hai phiên bản:

1. Coi `unknown` và `missing` đều là 0.
2. Phân biệt thiếu khóa với có khóa nhưng chưa biết giá trị.

Cả hai phiên bản cần tránh unbox `null` ngoài ý muốn. Hãy giải thích vì sao một lời gọi `getOrDefault` chưa đủ để hiện thực cả hai chính sách.

### Bài 3: Xóa đúng số cần xóa

Từ danh sách `[1, 2, 3]`:

- Xóa phần tử ở chỉ số 1.
- Trên một bản danh sách mới, xóa giá trị 1.

Kết quả lần lượt phải là `[1, 3]` và `[2, 3]`. Ghi rõ kiểu của đối số cho từng overload.

### Bài 4: So sánh có xét null

Thiết kế kiểm tra hai `Integer` có cùng giá trị khi một hoặc cả hai có thể `null`.

Sau đó thử một `Integer` và một `Long` cùng giá trị số. Giải thích vì sao `Objects.equals` không tự biến chúng thành cùng kiểu.

### Bài 5: Phân tích thay vì đoán chi phí

Có một triệu giá trị số cần cộng nhiều lần. Hãy so sánh phương án:

- `int[]`.
- `List<Integer>`.
- Primitive stream trên mảng.
- Đọc danh sách bằng `mapToInt`.

Trước khi đo, ghi rõ có tính chi phí dựng dữ liệu không, kết quả dùng kiểu gì, có cần `null` không và dữ liệu nằm trong hay ngoài cache thông dụng.

Không viết kết luận nhanh hơn một tỉ lệ cố định nếu chưa có phép đo đại diện.

## 15. Câu hỏi cần tự trả lời được

1. Vì sao `List<Integer>` hợp lệ còn `List<int>` không hợp lệ trong Java 17?
2. Chỗ nào xảy ra chuyển đổi khi thêm `int` rồi đọc ra thành `int`?
3. Vì sao boxing không chắc luôn tạo đối tượng mới?
4. Vì sao unboxing `null` là lỗi thay vì nhận 0?
5. Tại sao `getOrDefault` vẫn có thể dẫn tới lỗi unboxing?
6. Vì sao hai wrapper so sánh bằng `==` khác wrapper so với primitive?
7. Cache có làm `==` trở thành phép so sánh giá trị đáng tin không?
8. `Integer` và `Long` cùng biểu diễn 1 có bằng nhau theo `equals` không?
9. Vì sao `remove(1)` trên `List<Integer>` xóa theo chỉ số?
10. Vì sao `List<Long>.add(1)` không được chuyển tự động theo chuỗi mở rộng rồi boxing?
11. Tại sao `Integer sum` trong vòng lặp có thêm ranh giới chuyển đổi so với `int sum`?
12. Những thành phần nào làm bộ nhớ của collection wrapper khác mảng primitive?
13. Khi nào `mapToInt` giúp giữ phần xử lý ở primitive, và khi nào `boxed` đưa dữ liệu trở lại dạng đối tượng?
14. Một microbenchmark cần kiểm soát điều gì để không đưa ra kết luận sai?

Điểm cần đạt là nhận ra ranh giới giữa giá trị nguyên thủy và tham chiếu đối tượng, xử lý `null` theo nghĩa dữ liệu, rồi đánh giá chi phí bằng toàn bộ cách tổ chức và xử lý dữ liệu. Autoboxing giúp viết gọn; nó không làm hai mô hình trở thành một.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="element-types-003">Tính bất biến của generic: List&lt;Integer&gt; và List&lt;Number&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Tính bất biến của generic: `List<Integer>` và `List<Number>`

`Integer` là kiểu con của `Number`, nên một giá trị `Integer` có thể được dùng ở nơi nhận `Number`. Nhưng từ đó không suy ra `List<Integer>` là kiểu con của `List<Number>`.

Đây là **tính bất biến trong quan hệ kiểu (invariance)** của tham số generic. Từ “bất biến” ở đây không nói danh sách có được thêm hoặc xóa phần tử hay không. Một `ArrayList<Integer>` vẫn có thể sửa nội dung; điều bị giới hạn là quan hệ gán giữa các kiểu danh sách có đối số kiểu khác nhau.

Bài này giải thích quy tắc qua nhu cầu thực tế: muốn đọc các số từ nhiều loại danh sách, muốn thêm số nguyên vào một nơi nhận, hoặc muốn tạo danh sách số mới từ danh sách số nguyên. Ví dụ dùng Java 17 trở lên.

## 1. Hai tầng kiểu phải được xét riêng

### 1.1. Quan hệ giữa kiểu phần tử

Đoạn sau hợp lệ:

```java
Integer integer = 7;
Number number = integer;
```

Một `Integer` là một `Number`. Qua tham chiếu `number`, ta có thể gọi các phương thức được `Number` công bố, chẳng hạn `doubleValue()`.

`Double` cũng là một kiểu con của `Number`. Vì vậy, một vị trí cho phép chứa `Number` có thể nhận cả `Integer` lẫn `Double`.

### 1.2. Quan hệ giữa kiểu danh sách

Đoạn sau **không biên dịch**:

```java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = integers; // Lỗi kiểu.
```

`List<Integer>` và `List<Number>` là hai kiểu tham số hóa khác nhau. Quan hệ `Integer`–`Number` không được tự động nâng thành cùng quan hệ giữa hai danh sách.

Trong Java, một kiểu generic như `List<E>` không được khai báo là đồng biến theo `E`. Wildcard là công cụ để diễn đạt quan hệ phù hợp ở nơi sử dụng, khi ta thực sự cần chấp nhận nhiều kiểu danh sách. [Oracle: generics, kế thừa và kiểu con](https://docs.oracle.com/javase/tutorial/java/generics/inheritance.html)

### Bảng đối chiếu nhanh

| Phép gán | Hợp lệ? | Lý do |
| --- | --- | --- |
| `Integer` sang `Number` | Có | Quan hệ kiểu con thông thường |
| `ArrayList<Integer>` sang `List<Integer>` | Có | `ArrayList<E>` triển khai `List<E>` |
| `List<Integer>` sang `Collection<Integer>` | Có | `List<E>` kế thừa `Collection<E>` |
| `List<Integer>` sang `List<Number>` | Không | Thay đối số generic không tự tạo quan hệ kiểu con |
| `List<Integer>` sang `List<Object>` | Không | Cùng lý do, dù `Integer` là một `Object` |
| `List<Integer>` sang `List<? extends Number>` | Có | Wildcard cho phép một kiểu phần tử chưa biết nhưng là `Number` hoặc kiểu con |

Điểm quan trọng là phân biệt việc đổi **lớp chứa theo quan hệ kế thừa** với việc đổi **đối số kiểu phần tử**.

## 2. Vì sao Java phải từ chối `List<Integer>` sang `List<Number>`?

Hãy giả sử Java cho phép phép gán ấy:

```java
// Mã giả định để giải thích nguy cơ; Java không cho phép dòng gán thứ hai.
List<Integer> integers = new ArrayList<>();
List<Number> numbers = integers;

numbers.add(3.5);
Integer first = integers.get(0);
```

Hai biến `integers` và `numbers` sẽ cùng trỏ tới một danh sách. Qua kiểu `List<Number>`, lời gọi `add(3.5)` là hợp lệ vì `3.5` được boxing thành `Double`, một kiểu con của `Number`.

Nhưng tham chiếu ban đầu vẫn có kiểu `List<Integer>`. Khi đọc, bên sử dụng kỳ vọng phần tử là `Integer`, trong khi dữ liệu vừa bị chèn là `Double`. Cam kết kiểu của danh sách đã bị phá.

Java chặn ngay phép gán để không tạo đường ghi nguy hiểm này.

Lập luận không phụ thuộc việc danh sách hiện tại đang rỗng hay chỉ chứa số nguyên. `List<Number>` là một hợp đồng cho phép làm việc với phần tử kiểu `Number`; nó không chỉ có nghĩa “một danh sách mà ta định đọc như số”.

### Vì sao chiều ngược lại cũng không hợp lệ?

```java
List<Number> numbers = new ArrayList<>();
numbers.add(3.5);

// Không biên dịch:
List<Integer> integers = numbers;
```

Một `List<Number>` có thể chứa `Double`, `Long` hoặc những kiểu `Number` khác. Không thể bảo đảm mọi phần tử đều là `Integer`.

Vì vậy, `List<Integer>` không phải kiểu con của `List<Number>`, và `List<Number>` cũng không phải kiểu con của `List<Integer>`. [Java Language Specification 17: kiểu con của kiểu tham chiếu](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.10.2)

## 3. Invariance không phải immutability

Hai khái niệm trả lời hai câu hỏi khác nhau:

| Khái niệm | Câu hỏi |
| --- | --- |
| Invariance | Các kiểu như `List<Integer>` và `List<Number>` có được gán thay thế nhau không? |
| Không sửa được / bất biến dữ liệu | Đối tượng danh sách hoặc trạng thái phần tử có thể thay đổi không? |

Ví dụ:

```java
List<Integer> editable = new ArrayList<>();
editable.add(1);
editable.add(2);
editable.remove(Integer.valueOf(1));
```

Danh sách này có thể sửa, nhưng `List<Integer>` vẫn không gán được sang `List<Number>`.

Ngược lại:

```java
List<Integer> fixed = List.of(1, 2, 3);

// Vẫn không biên dịch:
List<Number> numbers = fixed;
```

Dù đối tượng do `List.of` tạo không cho sửa, quy tắc quan hệ kiểu vẫn áp dụng. Trình biên dịch không đổi quan hệ giữa `List<Integer>` và `List<Number>` chỉ vì biết một cách tạo hiện tại không hỗ trợ `add`.

Cũng không liên quan đến từ khóa `final`:

```java
final List<Integer> values = new ArrayList<>();
values.add(1); // Hợp lệ.
```

`final` ngăn gán lại biến `values`; nó không chuyển kiểu generic và không tự đóng băng nội dung danh sách.

## 4. Chỉ cần đọc số: dùng `List<? extends Number>`

Nếu một phương thức chỉ cần lấy các phần tử như `Number`, yêu cầu `List<Number>` thường quá hẹp.

Một chữ ký phù hợp hơn:

```java
static double sum(List<? extends Number> source) {
    double result = 0;
    for (Number value : source) {
        result += value.doubleValue();
    }
    return result;
}
```

Đây là phương thức minh họa cần đặt trong một lớp và import `List`. Nó giả định các phần tử không phải `null`.

Phương thức nhận được:

```java
sum(List.of(1, 2, 3));
sum(List.of(1.5, 2.5));
sum(List.<Number>of(1, 2.5));
```

`List<? extends Number>` có thể hiểu là:

> Một danh sách có kiểu phần tử cụ thể chưa biết; kiểu ấy là `Number` hoặc một kiểu con của `Number`.

Ta chưa biết danh sách thật là `List<Integer>`, `List<Double>` hay `List<Number>`, nhưng khi đọc một phần tử, có thể an toàn nhìn nó như `Number`. [Oracle: upper-bounded wildcard](https://docs.oracle.com/javase/tutorial/java/generics/upperBounded.html)

### 4.1. Vì sao không được thêm một Integer?

```java
List<? extends Number> source = new ArrayList<Double>();

// Không biên dịch:
source.add(1);
```

Giá trị 1 là một `Integer`. Nhưng đối tượng được gán vào `source` trong ví dụ là danh sách `Double`. Thêm `Integer` sẽ phá quy tắc của danh sách thật.

Tương tự, không được thêm một `Double` tùy ý, vì `source` có thể đang trỏ đến `List<Integer>`.

Quy tắc không đổi ngay cả ở trường hợp này:

```java
List<? extends Number> source =
        new ArrayList<Integer>();

// Vẫn không biên dịch:
source.add(1);
```

Khi sử dụng qua biến `source`, kiểu phần tử được xem là một kiểu chưa biết được giới hạn bởi `Number`. Trình biên dịch không cho dùng suy đoán bên ngoài để bỏ qua giới hạn đó.

### 4.2. Extends không tạo một danh sách chỉ đọc

`List<? extends Number>` hạn chế các phép ghi cần biết kiểu phần tử; nó không tạo wrapper mới và không tự cấm mọi thay đổi.

Ví dụ:

```java
List<Integer> original =
        new ArrayList<>(List.of(8, 9));

List<? extends Number> reference = original;
reference.clear();

System.out.println(original); // []
```

`clear()` không phải đưa một giá trị không rõ kiểu vào danh sách. Nó được phép về kiểu; có thực hiện được hay không còn tùy quyền sửa của đối tượng.

Ngoài ra, `null` có thể được truyền về mặt kiểu vào `add` của wildcard này, nhưng lớp cụ thể có thể từ chối `null` hoặc không hỗ trợ thêm. Không dùng điều đó làm cách lách quy tắc hoặc làm cơ sở thiết kế dữ liệu.

Nếu thật sự cần không cho sửa qua tham chiếu, hãy dùng API có hợp đồng không sửa được phù hợp. Wildcard chỉ điều chỉnh khả năng biểu đạt thao tác theo kiểu. [Oracle: hướng dẫn dùng wildcard](https://docs.oracle.com/javase/tutorial/java/generics/wildcardGuidelines.html)

## 5. Cần thêm Integer: dùng `List<? super Integer>`

Giả sử phương thức cần đưa các số nguyên vào một danh sách nhận. `List<? super Integer>` diễn đạt:

> Một danh sách có kiểu phần tử cụ thể là `Integer` hoặc một kiểu cha của `Integer`.

Ví dụ:

```java
static void appendIntegers(List<? super Integer> target) {
    target.add(10);
    target.add(20);
}
```

Có thể truyền:

```java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();

appendIntegers(integers);
appendIntegers(numbers);
appendIntegers(objects);
```

Cả ba kiểu danh sách đều có thể chấp nhận `Integer` mà không vi phạm kiểu phần tử. `List<Double>` không phù hợp. [Oracle: lower-bounded wildcard](https://docs.oracle.com/javase/tutorial/java/generics/lowerBounded.html)

### 5.1. Đọc lại chỉ bảo đảm Object

```java
List<Object> objects = new ArrayList<>();
objects.add("label");

List<? super Integer> target = objects;
target.add(10);

Object first = target.get(0);
```

`target` có thể đang trỏ đến `List<Object>` chứa chuỗi trước đó. Vì vậy, không được kết luận `get(0)` luôn là `Integer` hoặc thậm chí luôn là `Number`.

Đoạn sau không biên dịch:

```java
Integer first = target.get(0);
```

Nếu xử lý yêu cầu đọc với kiểu mạnh hơn, hãy thiết kế chữ ký phù hợp thay vì thêm ép kiểu chỉ để dập lỗi.

### 5.2. Không thêm được mọi Number

```java
List<? super Integer> target = new ArrayList<Integer>();

// Không biên dịch:
target.add(2.5);
```

Wildcard `super Integer` bảo đảm nhận `Integer`, không bảo đảm nhận mọi `Number`. Một `Double` không phải `Integer`.

Có thể ghi nhớ theo vai trò dữ liệu: nơi cung cấp số để đọc dùng `extends`; nơi nhận số nguyên được thêm vào dùng `super`. Quy tắc thường được gọi là PECS: Producer Extends, Consumer Super. Hãy xác định rõ kiểu đang được cung cấp/tiêu thụ, không áp dụng khẩu hiệu theo tên biến.

## 6. Khi thực sự cần `List<Number>`, hãy tạo cấu trúc mới

Có những trường hợp ứng dụng cần danh sách chứa được nhiều loại `Number`, bắt đầu từ dữ liệu số nguyên.

Cách an toàn:

```java
List<Integer> integers =
        new ArrayList<>(List.of(1, 2, 3));

List<Number> numbers = new ArrayList<>(integers);
numbers.add(4.5);

System.out.println(integers); // [1, 2, 3]
System.out.println(numbers);  // [1, 2, 3, 4.5]
```

Không có phép đổi kiểu toàn bộ một đối tượng `List<Integer>` thành `List<Number>` ở đây. Chương trình tạo **một đối tượng danh sách mới** có kiểu phần tử `Number`, rồi đưa các phần tử `Integer` hợp lệ vào.

Constructor nhận nguồn dạng `Collection<? extends E>`, nên khi `E` là `Number`, nguồn `Collection<Integer>` được chấp nhận. [Java 17: ArrayList(Collection)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html#%3Cinit%3E(java.util.Collection))

Nếu đã có đích:

```java
List<Number> destination = new ArrayList<>();
destination.addAll(integers);
```

Đây cũng là sao chép các phần tử vào cấu trúc phù hợp, không phải gán hai biến cùng trỏ đến một danh sách với hai cam kết phần tử mâu thuẫn.

### Sao chép cấu trúc không phải sao chép sâu

Danh sách mới chứa các tham chiếu phần tử được lấy từ nguồn. Với `Integer` bất biến, điều đó thường dễ suy luận. Với đối tượng có thể sửa, hai danh sách vẫn có thể cùng tham chiếu một đối tượng phần tử.

Tạo bản sao danh sách giải quyết việc thêm/xóa trên hai cấu trúc độc lập; nó không tự sao chép toàn bộ đồ thị đối tượng.

## 7. `List<?>` khác với `List<Object>`

Hai kiểu này rất dễ bị nhầm:

| Kiểu | Ý nghĩa | Có thể thêm một chuỗi tùy ý về mặt kiểu? |
| --- | --- | --- |
| `List<Object>` | Danh sách có kiểu phần tử là `Object` | Có |
| `List<?>` | Danh sách có kiểu phần tử cụ thể chưa biết | Không |
| `List<? extends Number>` | Kiểu phần tử chưa biết nhưng thuộc hệ `Number` | Không |
| `List<? super Integer>` | Kiểu phần tử là `Integer` hoặc kiểu cha phù hợp | Không; chỉ bảo đảm nhận `Integer` |

Cột cuối nói về kiểm tra kiểu khi biên dịch. Đối tượng danh sách thực tế vẫn có thể không hỗ trợ `add`.

Một hàm chỉ muốn in phần tử bất kỳ có thể nhận:

```java
static void printAll(List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

Hàm nhận được cả `List<Integer>` lẫn `List<String>` mà không cần biến chúng thành `List<Object>`.

Vì vậy, nếu trình biên dịch từ chối truyền `List<Integer>` vào phương thức nhận `List<Object>`, hãy hỏi phương thức thực sự có cần thêm mọi `Object` không. Nếu chỉ đọc như `Object`, `List<?>` có thể mới là hợp đồng đúng. [Oracle: wildcard và quan hệ kiểu con](https://docs.oracle.com/javase/tutorial/java/generics/subtyping.html)

## 8. Suy luận kiểu không thay đổi quy tắc invariance

Đoạn này hợp lệ:

```java
List<Number> numbers = List.of(1, 2);
```

Nó không chứng minh `List<Integer>` gán được cho `List<Number>`. `List.of` là phương thức generic; kiểu đích tham gia vào suy luận đối số kiểu. Trong ngữ cảnh trên, trình biên dịch có thể chọn kiểu phần tử `Number` ngay khi gọi factory.

Có thể viết ý định rõ hơn:

```java
List<Number> numbers = List.<Number>of(1, 2);
```

Trong khi đó:

```java
List<Integer> integers = List.of(1, 2);

// Không biên dịch:
List<Number> numbers = integers;
```

Ở đây biến nguồn đã có kiểu `List<Integer>`; không còn lời gọi factory đang được suy luận theo kiểu đích để thay đổi ý nghĩa.

Tương tự, `var` không làm kiểu trở nên linh hoạt:

```java
var integers = List.of(1, 2);

// Không biên dịch:
List<Number> numbers = integers;
```

Kiểu của biến `integers` được suy luận lúc khai báo. `var` không phải “một biến nhận danh sách với bất kỳ kiểu phần tử nào”.

Lưu ý riêng: danh sách từ `List.of` không sửa được. Quyền sửa của factory và quy tắc suy luận generic là hai vấn đề độc lập.

## 9. Vì sao mảng lại cho phép Number[] = Integer[]?

Java cho phép:

```java
Number[] numbers = new Integer[]{1, 2};
```

Mảng tham chiếu có quan hệ đồng biến. Nhưng đối tượng mảng lúc chạy vẫn biết kiểu phần tử thật là `Integer`.

Vì vậy:

```java
numbers[0] = 3.5; // Biên dịch được, nhưng ném ArrayStoreException.
```

Lời gọi gán được nhìn qua kiểu `Number[]`, nhưng khi thực thi, mảng từ chối một `Double` trong vùng dành cho `Integer`. [Java Language Specification 17: kiểm tra lưu phần tử mảng](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html#jls-10.5)

So sánh:

| Cấu trúc | Phép nâng kiểu đang xét | Cách chặn trường hợp ghi sai |
| --- | --- | --- |
| Mảng | `Integer[]` sang `Number[]` được phép | Kiểm tra kiểu phần tử mảng khi chạy |
| Generic List | `List<Integer>` sang `List<Number>` không được phép | Từ chối quan hệ gán khi biên dịch |

Không lấy quy tắc mảng để đoán quy tắc generic. Hai cơ chế được thiết kế và kiểm tra theo cách khác nhau.

## 10. Không giải quyết bằng raw type hoặc ép kiểu không kiểm tra

Một raw type như `List` bỏ đối số kiểu khỏi phần kiểm tra generic. Những ép kiểu qua `List<?>` hoặc raw type có thể làm trình biên dịch chấp nhận một phép gán với cảnh báo unchecked, nhưng không tạo một danh sách mới và không biến mọi phần tử thành `Number` theo một hợp đồng an toàn.

Nếu một alias cho phép thêm `Double` vào đối tượng mà nơi khác vẫn giữ dưới kiểu `List<Integer>`, dữ liệu bị ô nhiễm kiểu. Lỗi có thể chỉ xuất hiện về sau khi một nơi đọc và ép phần tử thành `Integer`.

Không dùng `@SuppressWarnings("unchecked")` như cách sửa mặc định. Nó chỉ tắt cảnh báo, không làm giả định trở thành đúng.

Hãy chọn một trong các cách phù hợp:

- Chỉ cần đọc các số: tham số `? extends Number`.
- Chỉ cần thêm số nguyên: tham số `? super Integer`.
- Cần giữ đúng một kiểu phần tử dùng ở nhiều vị trí: thiết kế phương thức generic với tham số kiểu.
- Cần một danh sách `Number` độc lập: sao chép phần tử vào cấu trúc mới.
- Không cần biết loại phần tử: cân nhắc `List<?>`.

Kiểu generic kiểm tra tại thời điểm biên dịch; không có thao tác tự động “nâng cấp” đối tượng chỉ vì người lập trình viết một phép cast.

## 11. Ví dụ hoàn chỉnh chạy được trên Java 17+

Lưu vào `GenericInvarianceDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class GenericInvarianceDemo {
    private static double sum(List<? extends Number> source) {
        double result = 0;
        for (Number value : source) {
            result += value.doubleValue();
        }
        return result;
    }

    private static void appendIntegers(List<? super Integer> target) {
        target.add(10);
        target.add(20);
    }

    public static void main(String[] args) {
        Integer integer = 7;
        Number number = integer;
        System.out.println("number = " + number);

        List<Integer> integers = new ArrayList<>(List.of(1, 2, 3));
        List<? extends Number> source = integers;
        Number first = source.get(0);
        System.out.println("first = " + first);
        System.out.println("integer sum = " + sum(integers));
        System.out.println("double sum = " + sum(List.of(1.5, 2.5)));

        List<Number> mixed = new ArrayList<>();
        mixed.add(2.5);
        List<? super Integer> sink = mixed;
        appendIntegers(sink);
        Object firstFromSink = sink.get(0);
        System.out.println("mixed = " + mixed);
        System.out.println("first sink value = " + firstFromSink);

        List<Number> copy = new ArrayList<>(integers);
        copy.add(4.5);
        System.out.println("original integers = " + integers);
        System.out.println("independent list structure = " + copy);

        List<Integer> removable = new ArrayList<>(List.of(8, 9));
        List<? extends Number> restricted = removable;
        restricted.clear();
        System.out.println("extends view after clear = " + removable);

        List<Number> targetTyped = List.of(1, 2);
        System.out.println("target-typed list = " + targetTyped);

        Number[] covariantArray = new Integer[]{1, 2};
        try {
            covariantArray[0] = 3.5;
        } catch (ArrayStoreException expected) {
            System.out.println("array rejects Double at runtime");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 GenericInvarianceDemo.java
java GenericInvarianceDemo
```

Stdout:

```text
number = 7
first = 1
integer sum = 6.0
double sum = 4.0
mixed = [2.5, 10, 20]
first sink value = 2.5
original integers = [1, 2, 3]
independent list structure = [1, 2, 3, 4.5]
extends view after clear = []
target-typed list = [1, 2]
array rejects Double at runtime
```

Cách đọc kết quả:

1. Giá trị `Integer` được dùng dưới kiểu `Number`.
2. Nguồn `? extends Number` cho đọc một `Number`, nên cùng hàm tính tổng xử lý được danh sách số nguyên hoặc số thực.
3. Đích `? super Integer` nhận thêm số nguyên vào một danh sách `Number` đã có `Double`; khi đọc qua tham chiếu wildcard, kiểu bảo đảm chung là `Object`.
4. Bản sao `List<Number>` có thể thêm `4.5` mà không sửa cấu trúc của danh sách số nguyên gốc.
5. Tham chiếu `? extends Number` vẫn có thể gọi `clear` trên đối tượng có hỗ trợ sửa, chứng minh wildcard không làm dữ liệu bất biến.
6. Kiểu đích tham gia suy luận ở lời gọi `List.of`.
7. Mảng đồng biến cho phép phép gán tham chiếu nhưng chặn một phần tử sai kiểu bằng ngoại lệ lúc chạy.

Hàm `sum` trong ví dụ chỉ minh họa khả năng đọc các `Number`; nó dùng `double` và không được thiết kế để tính tài chính hoặc bảo toàn chính xác mọi kiểu số.

## 12. Ca cố ý không biên dịch

Một quy tắc về kiểu cần được kiểm chứng cả bằng mã hợp lệ lẫn mã bị từ chối.

Lưu riêng tệp `InvarianceMustFail.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class InvarianceMustFail {
    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>();
        List<Number> numbers = integers;
    }
}
```

Chạy:

```shell
javac --release 17 -encoding UTF-8 InvarianceMustFail.java
```

**Kỳ vọng:** biên dịch thất bại tại phép gán từ `List<Integer>` sang `List<Number>`.

Thông báo sẽ thể hiện lỗi kiểu không tương thích; văn bản hoặc cách trình bày cụ thể có thể khác giữa các phiên bản trình biên dịch. Không đưa tệp cố ý lỗi này vào cùng tập mã cần build thành công của ứng dụng.

Nếu sửa dòng gán thành `List<? extends Number> numbers = integers;`, phần khai báo sẽ hợp lệ. Sau đó, thử thêm `numbers.add(3.5)` để thấy hệ thống kiểu tiếp tục chặn đường ghi không an toàn.

## 13. Chọn chữ ký theo vai trò của dữ liệu

| Việc phương thức cần làm | Kiểu nên bắt đầu cân nhắc |
| --- | --- |
| Đọc các phần tử như `Number` | `List<? extends Number>` |
| Thêm các `Integer` vào nơi nhận | `List<? super Integer>` |
| Đọc và ghi một kiểu `E` cần liên kết với tham số/kết quả khác | Tham số kiểu generic `E` |
| Chỉ duyệt để hiển thị giá trị bất kỳ | `List<?>` |
| Thêm cả `Integer`, `Double` và các `Number` khác | Một `List<Number>` thực sự phù hợp |
| Chuyển dữ liệu số nguyên sang danh sách số có thể mở rộng | Tạo `List<Number>` mới và sao chép phần tử |

Không dùng wildcard chỉ để hết lỗi biên dịch. Trước hết xác định bên nhận sẽ đọc gì, ghi gì và có cần giữ quan hệ kiểu với giá trị khác hay không. Chữ ký nên mô tả đúng trách nhiệm ấy.

## 14. Lỗi thường gặp

| Hiểu nhầm | Điều cần sửa |
| --- | --- |
| `Integer` là `Number` nên `List<Integer>` là `List<Number>` | Quan hệ kiểu phần tử không tự truyền qua generic |
| Invariance nghĩa là không được sửa danh sách | Đây là quy tắc quan hệ kiểu, khác quyền sửa dữ liệu |
| `? extends Number` nhận mọi `Number` để thêm | Nó cho đọc dưới kiểu `Number`, không cho chèn một số bất kỳ |
| `? super Integer` đọc ra luôn là `Integer` | Có thể là `List<Object>` chứa giá trị khác; chỉ bảo đảm `Object` |
| `List<?>` đồng nghĩa `List<Object>` | Một bên chưa biết kiểu; một bên có kiểu phần tử là `Object` |
| Danh sách không sửa được thì quan hệ kiểu có thể đổi | Quan hệ gán vẫn do kiểu tĩnh quyết định |
| `new ArrayList<>(integers)` chỉ là cast | Nó tạo cấu trúc mới rồi đưa phần tử hợp lệ vào |
| Cast unchecked hoặc suppress warning sẽ chuyển đổi dữ liệu | Không; chỉ bỏ qua hoặc tắt một phần kiểm tra |
| `var` cho phép đổi kiểu phần tử sau đó | Kiểu vẫn được xác định lúc khai báo |
| Mảng cho phép đồng biến nên List cũng phải vậy | Mảng và generic có cơ chế khác nhau |

## 15. Tự luyện

1. `List<Double>` có truyền được vào phương thức nhận `List<Number>` không?  
   **Đáp án:** không. Nếu phương thức chỉ đọc như số, cân nhắc `List<? extends Number>`.

2. Vì sao không được thêm `1` vào một biến `List<? extends Number>`?  
   **Đáp án:** danh sách thật có thể là `List<Double>`; kiểu phần tử cụ thể chưa biết.

3. Một `List<Object>` có thể truyền cho phương thức nhận `List<? super Integer>` không?  
   **Đáp án:** có, vì nó có thể nhận `Integer`. Khi đọc qua wildcard đó chỉ bảo đảm `Object`.

4. Muốn bắt đầu từ `List<Integer>`, sau đó thêm `Double` mà không ảnh hưởng danh sách gốc thì làm gì?  
   **Đáp án:** tạo `List<Number> copy = new ArrayList<>(integers)`, rồi thêm vào bản mới.

5. Tại sao `List<Number> values = List.of(1, 2)` hợp lệ nhưng gán biến `List<Integer>` sang `List<Number>` lại không?  
   **Đáp án:** trường hợp đầu là suy luận kiểu cho lời gọi generic theo ngữ cảnh đích; trường hợp sau là quan hệ gán giữa hai kiểu đã xác định.

Tính bất biến của generic giữ cho mỗi tham chiếu danh sách duy trì cam kết về loại phần tử. Khi cần linh hoạt, hãy nới hợp đồng đúng theo vai trò đọc hoặc ghi, thay vì làm mất kiểm tra kiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="element-types-004">Wildcard giới hạn trên khi đọc dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Wildcard giới hạn trên khi đọc dữ liệu

## 1. Bài toán: một thuật toán đọc nhiều kiểu phần tử liên quan

Ta muốn tính tổng một nhóm số. Dữ liệu đầu vào có thể là `List<Integer>`, `List<Double>` hoặc một collection chứa các đối tượng `Number`.

Một tham số `Collection<Number>` chưa đáp ứng được tất cả các đầu vào đó:

```java
// Phác thảo chữ ký, không phải chương trình độc lập.
static double sum(Collection<Number> values)
```

`Integer` là kiểu con của `Number`, nhưng `Collection<Integer>` không phải kiểu con của `Collection<Number>`. Nếu cho phép gán như vậy, mã qua tham chiếu `Collection<Number>` có thể thêm một `Double` vào đối tượng vốn được mong đợi chỉ chứa `Integer`.

Wildcard giới hạn trên (upper-bounded wildcard) giải quyết trường hợp thuật toán cần **đọc dữ liệu qua một kiểu chung**, không cần tùy ý chèn phần tử mới vào nguồn:

```java
// Phác thảo chữ ký; cần import java.util.Collection.
static double sum(Collection<? extends Number> values)
```

Cách đọc:

> Nhận một collection có kiểu phần tử cụ thể nào đó là `Number` hoặc một kiểu con của `Number`.

Từ đó, mỗi phần tử đọc ra đều dùng được như `Number`. Ta không cần biết kiểu con chính xác để gọi `doubleValue()`.

Bài sử dụng Java 17. Cần biết generics cơ bản, tính bất biến của tham số kiểu và quan hệ kiểu cha–con. Mục tiêu là hiểu tại sao đọc được, tại sao một số lời gọi ghi không biên dịch và những giới hạn vẫn còn ngoài wildcard.

## 2. Ý nghĩa chính xác của ? extends T

Với:

```java
List<? extends Number> source;
```

`?` đại diện cho **một kiểu chưa biết**, bị ràng buộc bởi giới hạn trên `Number`.

Những đối tượng sau đều có thể được gán cho biến đó:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<Integer> integers = List.of(1, 2);
List<Double> decimals = List.of(0.5, 1.5);
List<Number> mixed = List.<Number>of(1, 2.5);

List<? extends Number> a = integers;
List<? extends Number> b = decimals;
List<? extends Number> c = mixed;
```

Không nên diễn giải `List<? extends Number>` thành “danh sách cho phép thêm mọi loại `Number`”. Điều đó là đặc tính của một `List<Number>` hỗ trợ thêm, không phải của tham chiếu có kiểu phần tử chưa biết.

Có thể hình dung kiểu chưa biết là `CAP`:

```text
CAP là một kiểu cụ thể chưa biết
CAP là Number hoặc kiểu con của Number

đọc CAP -> dùng như Number: an toàn
đưa một Number bất kỳ vào nơi cần CAP: chưa chứng minh được
```

`extends` ở đây bao gồm chính kiểu giới hạn, không chỉ kiểu con nghiêm ngặt. Nó cũng dùng cho giới hạn là giao diện, chẳng hạn `Collection<? extends CharSequence>` có thể nhận collection của `String` hoặc `StringBuilder` phù hợp.

Tham khảo [Upper Bounded Wildcards](https://docs.oracle.com/javase/tutorial/java/generics/upperBounded.html) và [Wildcards and Subtyping](https://docs.oracle.com/javase/tutorial/java/generics/subtyping.html).

## 3. Đọc dữ liệu an toàn qua giới hạn trên

### 3.1. Đọc ra kiểu Number

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<? extends Number> source = List.of(1, 2, 3);

Number first = source.get(0);
double value = first.doubleValue();

System.out.println(first); // 1
System.out.println(value); // 1.0
```

Không cần ép kiểu `(Number)`. Trình biên dịch biết phần tử thuộc một kiểu không rộng hơn `Number`.

Tương tự với vòng lặp:

```java
// Đoạn minh họa với source đã được khởi tạo.
for (Number number : source) {
    System.out.println(number.doubleValue());
}
```

Có thể dùng những phương thức được công bố trên giới hạn trên, như các phương thức chuyển đổi số của `Number`. Xem [Number API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Number.html).

### 3.2. Không tự đọc thành một kiểu con tùy ý

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<? extends Number> source = List.of(1.5, 2.5);

// Integer first = source.get(0); // Không biên dịch.
Number first = source.get(0);     // Hợp lệ.
```

Giới hạn `Number` không hứa phần tử là `Integer`. Ép kiểu thủ công sang `Integer` có thể chuyển lỗi sang lúc chạy.

Nếu thuật toán thật sự đòi tính chất chỉ có ở `Integer`, cần thiết kế tham số hoặc kiểm tra dữ liệu cho yêu cầu đó. Không dùng wildcard rộng rồi giả định ngầm rằng dữ liệu luôn là một kiểu con cụ thể.

### 3.3. var cũng không khôi phục kiểu con đã bị ẩn

Nếu viết `var first = source.get(0);` với `source` khai báo là `List<? extends Number>`, ta không có cơ sở để coi `first` là `Integer`. Kiểu có thể dùng an toàn vẫn dựa trên giới hạn `Number`.

Suy luận kiểu không phải cơ chế theo dõi “mọi lần chạy thực tế đều dùng Integer” để cho phép một API chưa được hợp đồng bảo đảm.

## 4. Vì sao không được thêm Number, Integer hoặc Double?

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<Integer> integers = new ArrayList<>();
List<? extends Number> source = integers;

// source.add(1);   // Không biên dịch.
// source.add(2.5); // Không biên dịch.
```

`source` có thể trỏ tới một `List<Integer>`. Thêm `Double` rõ ràng không an toàn.

Nhưng tại sao thêm `Integer` cũng bị chặn? Vì cùng kiểu khai báo `List<? extends Number>` cũng có thể đang trỏ tới một `List<Double>` hoặc một kiểu `Number` khác. Tại nơi sử dụng, compiler không biết kiểu chính xác mà `add` cần nhận.

Quy tắc bảo vệ tất cả các giá trị có thể gán theo kiểu khai báo, không chỉ trường hợp khởi tạo mà người viết đang nhìn thấy trong ví dụ.

Tương tự:

```java
// Đoạn minh họa với source kiểu List<? extends Number>.
// source.set(0, 1);              // Không biên dịch.
// source.addAll(List.of(1, 2));  // Không biên dịch.
```

Đổi `add` thành `set` không giải quyết vấn đề: việc thay phần tử vẫn phải đưa vào đúng kiểu chưa biết.

### null là một trường hợp riêng

`null` có thể chuyển sang kiểu tham chiếu chưa biết, nên lời gọi sau hợp lệ về mặt kiểu:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<Integer> backing = new ArrayList<>(List.of(9));
List<? extends Number> source = backing;
source.add(null);

System.out.println(backing); // [9, null]
```

Việc chạy thành công ở đây còn dựa vào `ArrayList` cho thêm và cho `null`. Nếu đối tượng là `List.of(...)`, thao tác sửa bị từ chối. Nếu cài đặt cấm `null`, nó có thể báo lỗi giá trị.

Vì vậy, không dùng câu tuyệt đối “không thể thêm bất cứ thứ gì” để mô tả wildcard. Cần phân biệt kiểm tra kiểu với chính sách của đối tượng.

## 5. Wildcard giới hạn trên không tạo tính bất biến

Một cách nhớ nhanh là dùng `extends` cho nguồn đọc. Nhưng “nguồn để đọc” không phải một cơ chế cấm mọi thay đổi.

| Thao tác qua `List<? extends Number>` | Về kiểm tra kiểu | Điều kiện khi chạy |
| --- | --- | --- |
| `get(index)` và đọc thành `Number` | Được | Chỉ số hợp lệ |
| Duyệt bằng `Number` | Được | Nguồn và phần tử phù hợp với thuật toán |
| `add` một `Integer` hoặc `Double` mới | Không được | Bị chặn trước khi chạy |
| `set` bằng một `Number` tùy ý | Không được | Bị chặn trước khi chạy |
| `remove(index)` hoặc `remove(Object)` | Có thể gọi | Đối tượng phải hỗ trợ xóa |
| `clear()` | Có thể gọi | Đối tượng phải hỗ trợ làm rỗng |
| `add(null)` | Có thể hợp lệ về kiểu | Đối tượng phải cho thêm và cho `null` |
| Sửa qua một tham chiếu khác tới cùng đối tượng | Wildcard không chặn | Phụ thuộc hợp đồng tham chiếu/đối tượng đó |

Ví dụ:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<Integer> original = new ArrayList<>(List.of(1, 2, 3));
List<? extends Number> source = original;

source.remove(0);
System.out.println(original); // [2, 3]

source.clear();
System.out.println(original); // []
```

Các lời gọi xóa không cần cung cấp một phần tử mới thuộc kiểu capture. Chúng vẫn có thể thay đổi dữ liệu nguồn.

Ngoài ra:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<Integer> original = new ArrayList<>(List.of(1, 2));
List<? extends Number> source = original;

original.add(3);
System.out.println(source); // [1, 2, 3]
```

Phép gán không sao chép danh sách và không tạo wrapper. Hai biến tham chiếu cùng đối tượng.

Nếu phần tử là đối tượng có trạng thái thay đổi, việc đọc tham chiếu còn có thể cho phép gọi các phương thức thay đổi trạng thái được công bố trên kiểu giới hạn. Wildcard không làm bản thân phần tử bất biến.

Để ngăn sửa qua một API, cần thiết kế hợp đồng và cơ chế thích hợp như wrapper không cho sửa hoặc bản sao không cho sửa. Ngay cả wrapper cũng có thể quan sát thay đổi từ nguồn, và bản sao nông vẫn dùng chung các đối tượng phần tử.

Xem [Guidelines for Wildcard Use](https://docs.oracle.com/javase/tutorial/java/generics/wildcardGuidelines.html), [Collection API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html) và [Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)).

## 6. Viết một thuật toán đọc thực sự

```java
// Đoạn khai báo phương thức trong một lớp; cần import java.util.Collection.
static double sum(Collection<? extends Number> values) {
    double total = 0;
    for (Number value : values) {
        total += value.doubleValue();
    }
    return total;
}
```

Hợp đồng của ví dụ:

- Nhận nguồn và các phần tử khác `null`.
- Không thêm, xóa hoặc thay phần tử trong nguồn.
- Nguồn rỗng cho kết quả `0.0`.
- Dùng biểu diễn `double` để minh họa, không cam kết tính toán thập phân chính xác cho tiền tệ.
- Không hỗ trợ việc nguồn bị sửa đồng thời ngoài một chính sách đồng bộ thích hợp.

Lời gọi:

```java
// Đặt trong một phương thức, với sum đã khai báo; cần import java.util.*.
System.out.println(sum(List.of(1, 2, 3)));   // 6.0
System.out.println(sum(List.of(0.5, 1.5))); // 2.0
```

Ở đây chọn `Collection` vì chỉ cần duyệt, không cần vị trí. Nếu phương thức cần `get(0)`, `List` mới diễn đạt đủ API cần dùng.

Nếu chỉ cần duyệt và không cần các thao tác collection khác, `Iterable<? extends Number>` cũng có thể là tham số phù hợp. Chọn giao diện theo thao tác thật sự, rồi dùng wildcard theo quan hệ kiểu cần chấp nhận.

Không phải thuật toán đọc nào cũng nên dùng `Number`: một miền nghiệp vụ có thể cần kiểu chính xác hơn hoặc phép tính được định nghĩa riêng.

## 7. Phân biệt với `Collection<Number>`, `Collection<?>` và `Collection<Object>`

| Tham số | Đầu vào điển hình phù hợp | Đọc được chắc chắn như kiểu gì? | Ý nghĩa |
| --- | --- | --- | --- |
| `Collection<Number>` | `Collection<Number>` | `Number` | Có kiểu phần tử Number cụ thể |
| `Collection<? extends Number>` | `Collection<Integer>`, `Collection<Double>`, `Collection<Number>` | `Number` | Kiểu phần tử chưa biết nhưng không rộng hơn Number |
| `Collection<?>` | Collection của nhiều đối số kiểu khác nhau | `Object` | Không có yêu cầu riêng ngoài những gì biết về Object |
| `Collection<Object>` | `Collection<Object>` | `Object` | Collection có kiểu phần tử Object, không phải chỗ nhận mọi collection |

`Collection<?>` cũng có giới hạn trên ngầm định là `Object`. Nếu thuật toán chỉ in phần tử hoặc lấy `size`, nó có thể đủ. Nếu cần gọi `doubleValue`, giới hạn `Number` mang lại thông tin hữu ích.

`Collection<Object>` không nhận gán trực tiếp một `Collection<Integer>`. Không dùng `Object` như cách tránh quy tắc bất biến của generics.

Tương tự, `Collection<? extends Number>` không nhận `Collection<String>`. Nó mở rộng tập đầu vào có kiểm soát, không bỏ kiểm tra kiểu.

Các quy tắc tham số hóa và wildcard nằm trong [JLS Chapter 4](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html).

## 8. Wildcard hay tham số kiểu có tên?

### 8.1. Chỉ cần đọc qua một hợp đồng chung: wildcard thường đủ

```java
// Phác thảo chữ ký; cần import java.util.Collection.
static double sum(Collection<? extends Number> values)
```

Phương thức không cần nói “kết quả có cùng kiểu phần tử với đầu vào”. Kết quả luôn là `double`, nên chưa cần đặt tên kiểu chưa biết.

### 8.2. Cần liên kết kiểu với kết quả: dùng T

```java
// Đoạn khai báo phương thức; cần import java.util.List.
static <T extends Number> T firstTyped(List<T> values) {
    if (values.isEmpty()) {
        throw new IllegalArgumentException("values must not be empty");
    }
    return values.get(0);
}
```

Với `List<Integer>`, người gọi có thể nhận `Integer` mà không ép kiểu:

```java
// Đặt trong một phương thức, với firstTyped đã khai báo; cần import java.util.List.
Integer first = firstTyped(List.of(10, 20));
```

So sánh với:

```java
// Đoạn khai báo phương thức; cần import java.util.List.
static Number first(List<? extends Number> values) {
    if (values.isEmpty()) {
        throw new IllegalArgumentException("values must not be empty");
    }
    return values.get(0);
}
```

Hợp đồng kết quả thứ hai chỉ hứa `Number`, không giữ quan hệ “chính là kiểu phần tử của đầu vào” cho người gọi.

Tên `T` hữu ích khi cần liên kết nhiều vị trí kiểu: nguồn, đích, kết quả hoặc các đối số có quan hệ. Không thêm tham số kiểu chỉ để làm chữ ký trông tổng quát hơn. [Generic Methods](https://docs.oracle.com/javase/tutorial/java/generics/methods.html)

### 8.3. Không ưu tiên wildcard ở kiểu trả về nếu không cần

Một API trả `List<? extends Number>` khiến người gọi phải làm việc với kiểu chưa biết. Nếu API tạo một danh sách số mà hợp đồng mong muốn là `List<Number>`, thường nên công bố đúng kiểu đó.

Ngược lại, có API có lý do giữ phần tử theo một họ kiểu; khi ấy wildcard có thể có giá trị. Quyết định nên dựa trên thông tin người gọi cần, không chỉ lặp lại quy tắc “đọc thì extends” vào mọi vị trí trong chữ ký.

## 9. Sao chép nguồn để có một đích với kiểu rõ ràng

Nếu cần nhận nhiều nguồn kiểu con rồi thêm một giá trị `Number` khác, đừng ép nguồn thành `Collection<Number>`. Hãy tạo collection mới:

```java
// Đoạn minh họa trong một phương thức; cần import java.util.*.
List<Integer> original = List.of(1, 2, 3);
Collection<? extends Number> source = original;

List<Number> copy = new ArrayList<>(source);
copy.add(2.5);

System.out.println(original); // [1, 2, 3]
System.out.println(copy);     // [1, 2, 3, 2.5]
```

Constructor `ArrayList(Collection<? extends E>)` đọc phần tử từ nguồn để đặt vào danh sách có kiểu đích `E`. Trong ví dụ `E` là `Number`. Nguồn vẫn giữ hợp đồng của nó. [ArrayList API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

Có thể đóng gói quan hệ này:

```java
// Đoạn khai báo phương thức; cần import java.util.*.
static <T> List<T> copySource(Collection<? extends T> source) {
    return new ArrayList<>(source);
}
```

Đây là bản sao cấu trúc và các tham chiếu phần tử, không phải nhân bản sâu. Với phần tử có trạng thái thay đổi, nguồn và bản sao có thể cùng tham chiếu một đối tượng.

Nếu muốn ghi vào một đích đã được cung cấp, đó là bài toán thiết kế tham số đích, thường liên quan `? super T`. Không thay `extends` bằng `super` ở nguồn một cách máy móc: mỗi phía có vai trò khác nhau.

## 10. Wildcard capture và thao tác giữ nguyên kiểu

### 10.1. Thông báo capture of ? nói gì?

Đoạn sau dễ khiến người học bối rối:

```java
// Minh họa có lỗi biên dịch; cần import java.util.List.
static void rewriteFirst(List<? extends Number> values) {
    // values.set(0, values.get(0));
}
```

Nếu bỏ chú thích, compiler có thể báo lỗi liên quan `capture of ? extends Number`. Về mặt ý định, ta đang đọc rồi ghi lại một phần tử. Nhưng kiểu đọc nhìn qua giới hạn không tự được xác nhận là cùng kiểu capture cần cho lời gọi ghi tại vị trí khác.

Không giải quyết bằng raw `List`. Khi thao tác thật sự an toàn vì chỉ dùng lại các phần tử của cùng một danh sách, có thể đặt tên cho capture bằng một helper generic.

### 10.2. Helper giữ cùng một T cho cả đọc và ghi

```java
// Đoạn khai báo hai phương thức trong một lớp; cần import java.util.List.
static void swapFirstTwo(List<? extends Number> values) {
    swapCaptured(values);
}

private static <T extends Number> void swapCaptured(List<T> values) {
    if (values.size() < 2) {
        throw new IllegalArgumentException("need two elements");
    }

    T first = values.get(0);
    values.set(0, values.get(1));
    values.set(1, first);
}
```

Trong helper, kiểu `T` liên kết phần tử đọc và phần tử ghi. Không đưa một `Number` mới tùy ý vào danh sách. Chỉ đổi vị trí hai phần tử vốn thuộc cùng kiểu phần tử đã được capture.

Tuy vậy, phương thức **có sửa nguồn**. Đối tượng phải hỗ trợ `set`; truyền một danh sách không cho sửa vẫn có thể gây `UnsupportedOperationException`. Wildcard capture giải quyết vấn đề chứng minh kiểu, không giải quyết quyền sửa. [Wildcard Capture and Helper Methods](https://docs.oracle.com/javase/tutorial/java/generics/capture.html)

### 10.3. Hai wildcard không có nghĩa là cùng một kiểu

Nếu nhận hai tham số `List<? extends Number>`, kiểu phần tử chưa biết của mỗi tham số có thể khác nhau: một là `Integer`, một là `Double`.

Không lấy phần tử từ danh sách thứ nhất rồi chèn sang danh sách thứ hai chỉ vì cả hai có cùng giới hạn `Number`. Cùng giới hạn trên không đồng nghĩa cùng capture.

Muốn truyền dữ liệu giữa hai nơi, chữ ký cần diễn đạt quan hệ kiểu an toàn giữa nguồn và đích. Nếu chỉ muốn đọc cả hai, không có vấn đề này.

Capture conversion được mô tả chính xác trong [JLS 5.1.10](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.10).

## 11. Áp dụng cho Map và dữ liệu lồng nhau

### 11.1. Chỉ đặt wildcard ở phía cần mở rộng

```java
// Đoạn khai báo phương thức; cần import java.util.Map.
static double sumMappedValues(Map<String, ? extends Number> values) {
    double total = 0;
    for (Number value : values.values()) {
        total += value.doubleValue();
    }
    return total;
}
```

Hàm nhận `Map<String, Integer>` hoặc `Map<String, Double>` vì kiểu giá trị được mở rộng. Kiểu khóa vẫn được yêu cầu là `String`.

Nó không biến thành một map cho phép `put("new", 1.5)`: giá trị cần ghi vẫn thuộc kiểu chưa biết. Đọc kết quả `get` hoặc `values` như `Number` thì an toàn về kiểu, nhưng vẫn phải xét trường hợp `null`.

Nếu chỉ cần các giá trị mà không dùng khóa, có thể nhận `Collection<? extends Number>` và truyền `map.values()`. Chọn tham số theo khả năng thuật toán thật sự cần. [Map API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)

### 11.2. Wildcard bên ngoài không tự làm mọi tầng biến thiên

`List<Integer>` không phải `List<Number>`, nên với dữ liệu lồng nhau phải đọc từng tầng.

Ví dụ ý định “một nhóm gồm các collection, mỗi collection cung cấp Number” có thể được biểu đạt bằng:

```java
// Phác thảo chữ ký, không phải chương trình độc lập.
Collection<? extends Collection<? extends Number>> groups
```

Wildcard ngoài mô tả các collection con; wildcard trong mô tả phần tử số của mỗi collection con.

Không cần dùng chữ ký dài này nếu bài toán chỉ nhận một collection phẳng. Việc lồng wildcard nên xuất phát từ mô hình dữ liệu, không phải một thói quen làm API phức tạp.

## 12. Giới hạn ngoài hệ thống kiểu

Wildcard giới hạn trên không tự xử lý các vấn đề sau:

- **Nguồn `null`:** gọi phương thức trên nguồn vẫn có thể gây `NullPointerException`.
- **Phần tử `null`:** đọc được thành `Number` không bảo đảm gọi `doubleValue` an toàn.
- **Nguồn rỗng:** `get(0)` vẫn cần hợp đồng cho trường hợp không có phần tử.
- **Số học:** chuyển mọi `Number` sang `double` có thể mất độ chính xác.
- **Đồng thời:** tham chiếu khác có thể sửa cấu trúc trong lúc duyệt.
- **Dữ liệu sai kiểu từ raw type:** wildcard không khôi phục bảo đảm đã bị phá vỡ ở ranh giới unchecked.
- **Quyền sở hữu:** nó không tạo bản sao hoặc chuyển quyền sửa nguồn.
- **Bất biến sâu:** nó không khóa trạng thái của các đối tượng phần tử.

Vì vậy, chữ ký là một phần hợp đồng; tài liệu, validation và chính sách vận hành dữ liệu vẫn cần phù hợp.

Không dùng cast từ `List<? extends Number>` sang `List<Number>` để làm `add` biên dịch. Cast không biến một `List<Integer>` thành danh sách có thể an toàn chứa `Double`.

## 13. Chương trình hoàn chỉnh

Lưu thành `UpperBoundedReadDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class UpperBoundedReadDemo {
    private static double sum(Collection<? extends Number> values) {
        double total = 0;
        for (Number value : values) {
            total += value.doubleValue();
        }
        return total;
    }

    private static Number first(List<? extends Number> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        return values.get(0);
    }

    private static <T> List<T> copySource(
            Collection<? extends T> source) {
        return new ArrayList<>(source);
    }

    private static void swapFirstTwo(List<? extends Number> values) {
        swapCaptured(values);
    }

    private static <T extends Number> void swapCaptured(List<T> values) {
        if (values.size() < 2) {
            throw new IllegalArgumentException("need two elements");
        }
        T first = values.get(0);
        values.set(0, values.get(1));
        values.set(1, first);
    }

    private static double sumMappedValues(
            Map<String, ? extends Number> values) {
        double total = 0;
        for (Number value : values.values()) {
            total += value.doubleValue();
        }
        return total;
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>(List.of(1, 2, 3));
        List<Double> decimals = List.of(0.5, 1.5);
        System.out.println("Integer sum: " + sum(integers));
        System.out.println("Double sum: " + sum(decimals));

        List<? extends Number> source = integers;
        Number firstValue = first(source);
        System.out.println("First value: " + firstValue);

        integers.add(4);
        System.out.println("View after alias add: " + source);
        source.remove(0);
        System.out.println("After source remove: " + integers);

        List<Number> copy = copySource(source);
        copy.add(2.5);
        System.out.println("Copy: " + copy);
        System.out.println("Original: " + integers);

        List<Integer> pair = new ArrayList<>(List.of(10, 20));
        swapFirstTwo(pair);
        System.out.println("Captured swap: " + pair);

        List<Integer> toClear = new ArrayList<>(List.of(7, 8));
        Collection<? extends Number> unknownSubtype = toClear;
        unknownSubtype.clear();
        System.out.println("After wildcard clear: " + toClear);

        List<Integer> nullable = new ArrayList<>(List.of(9));
        List<? extends Number> nullAllowedByType = nullable;
        nullAllowedByType.add(null);
        System.out.println("Null insertion: " + nullable);

        Map<String, Integer> counts = Map.of("a", 2, "b", 3);
        System.out.println("Map value sum: " + sumMappedValues(counts));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:unchecked UpperBoundedReadDemo.java
java UpperBoundedReadDemo
```

Mã đã được kiểm tra với nền API Java 17 trên JDK mới hơn, không có cảnh báo unchecked. Chương trình không sửa danh sách `List.of`; những thao tác sửa dùng `ArrayList`.

Stdout:

```text
Integer sum: 6.0
Double sum: 2.0
First value: 1
View after alias add: [1, 2, 3, 4]
After source remove: [2, 3, 4]
Copy: [2, 3, 4, 2.5]
Original: [2, 3, 4]
Captured swap: [20, 10]
After wildcard clear: []
Null insertion: [9, null]
Map value sum: 5.0
```

### Giải thích theo các ranh giới cần học

- `sum` đọc được cả nguồn `Integer` lẫn `Double` qua `Number`.
- `first` chỉ trả hợp đồng `Number`, nên biến nhận không tự trở thành `Integer`.
- `source` quan sát phần tử được thêm qua `integers` vì hai tham chiếu cùng trỏ một đối tượng.
- `source.remove(0)` biên dịch và thay đổi nguồn: wildcard không tạo bất biến.
- `copySource` tạo một `List<Number>` mới; thêm `2.5` vào bản sao không đổi cấu trúc nguồn.
- Helper capture đổi hai phần tử trong cùng kiểu `T`, không ép kiểu.
- `clear` qua wildcard vẫn làm rỗng một nguồn sửa được.
- `add(null)` hợp lệ về kiểu và được `ArrayList` chấp nhận; sau đó không đưa danh sách này vào `sum` vốn yêu cầu phần tử khác `null`.
- Wildcard ở phía giá trị của map cho phép đọc map số nguyên qua `Number`.

## 14. Lỗi phổ biến và cách sửa

| Cách làm | Vấn đề | Hướng sửa |
| --- | --- | --- |
| Dùng `Collection<Number>` rồi muốn nhận `List<Integer>` | Không có quan hệ kiểu mong muốn | Dùng `? extends Number` cho nguồn đọc |
| Hiểu `? extends Number` là cho thêm mọi `Number` | Kiểu thật có thể hẹp hơn | Chỉ đọc qua giới hạn hoặc tạo đích có kiểu rõ ràng |
| Đọc thành `Integer` không kiểm tra | Nguồn có thể chứa kiểu Number khác | Dùng API của `Number` hoặc thiết kế yêu cầu kiểu hẹp hơn |
| Coi wildcard là collection bất biến | `remove`, `clear` và alias vẫn có thể sửa | Chọn cơ chế không cho sửa/copy và tài liệu hóa quyền sở hữu |
| Coi `null` là bị chặn bởi wildcard | `null` hợp lệ với kiểu tham chiếu | Kiểm tra chính sách giá trị riêng |
| Dùng raw type để hết lỗi capture | Làm mất bảo đảm kiểu | Dùng helper generic khi quan hệ thật sự an toàn |
| Chuyển phần tử giữa hai wildcard cùng bound | Hai capture có thể khác nhau | Diễn đạt quan hệ nguồn–đích bằng tham số kiểu |
| Dùng wildcard cho mọi kiểu trả về | Đẩy khó khăn không cần thiết sang người gọi | Công bố kiểu kết quả đủ chính xác |
| Cho rằng wildcard xử lý rỗng hoặc `null` | Đây là điều kiện dữ liệu, không phải chỉ kiểu | Nêu và kiểm tra hợp đồng đầu vào |
| Cast nguồn thành `List<Number>` để thêm Double | Có thể làm sai dữ liệu một `List<Integer>` | Tạo bản sao có kiểu đích phù hợp |

## 15. Tự kiểm tra và thực hành

### Câu hỏi

1. `Collection<? extends Number>` có nhận `Collection<Number>` không?
2. Vì sao nó cũng nhận `Collection<Integer>` nhưng không cho `add(1)`?
3. Đọc phần tử thành `Number` khác với ép thành `Integer` thế nào?
4. Có được `clear` qua tham chiếu wildcard không?
5. `add(null)` có bị chặn lúc biên dịch không, và lúc chạy còn phụ thuộc điều gì?
6. Hai tham số `List<? extends Number>` có bắt buộc cùng kiểu phần tử không?
7. Khi nào cần `<T extends Number>` thay vì chỉ wildcard?
8. Helper capture có làm một danh sách không cho sửa trở thành sửa được không?
9. Sao chép sang `List<Number>` khác với cast nguồn sang kiểu đó thế nào?
10. Thuật toán dùng `doubleValue` còn cần công bố những giới hạn dữ liệu nào?

### Bài tập

- Viết hàm đếm số phần tử có giá trị `doubleValue()` dương, nhận `Collection<? extends Number>` và không sửa nguồn.
- Thử truyền `List<Integer>`, `List<Double>` và `List<Number>` vào cùng hàm.
- Thêm một dòng `source.add(1)` vào bài thử để quan sát lỗi biên dịch; giải thích bằng kiểu phần tử chưa biết.
- Chứng minh bằng một ví dụ nhỏ rằng sửa qua alias được quan sát qua biến wildcard.
- Tạo một bản sao `List<Number>` từ nguồn số nguyên rồi thêm một số thập phân vào bản sao.
- Gọi helper đổi chỗ với một `ArrayList`, sau đó thử một danh sách không cho sửa để phân biệt lỗi kiểu và lỗi thao tác.
- Viết hai hàm lấy phần tử đầu: một trả `Number`, một generic trả `T`; đối chiếu kiểu biến nhận tại nơi gọi.
- Với map có khóa `String` và giá trị số nguyên, viết hàm chỉ đọc giá trị qua giới hạn `Number`.

### Gợi ý đối chiếu

1. Có; giới hạn bao gồm chính `Number`.
2. Nhận được một kiểu con cụ thể nhưng không biết nó là kiểu nào tại chỗ ghi.
3. Giới hạn trên bảo đảm dùng được như `Number`; không bảo đảm một kiểu con tùy ý.
4. Có thể gọi về mặt kiểu; đối tượng phải hỗ trợ thao tác.
5. Có thể hợp lệ về kiểu; đối tượng phải hỗ trợ thêm và chấp nhận `null`.
6. Không; một nguồn có thể là `Integer`, nguồn kia là `Double`.
7. Khi cần giữ quan hệ kiểu giữa nhiều vị trí, nhất là kiểu kết quả.
8. Không; nó chỉ giúp chứng minh quan hệ kiểu cho lời gọi.
9. Bản sao có cấu trúc và kiểu đích riêng; cast không thay đổi đối tượng nguồn.
10. Nguồn/phần tử khác `null`, trường hợp rỗng, độ chính xác số học và việc sửa đồng thời.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="element-types-005">Wildcard giới hạn dưới khi ghi dữ liệu và quy tắc PECS</a>

<details>
<summary>Xem chi tiết</summary>

# Wildcard giới hạn dưới khi ghi dữ liệu và quy tắc PECS

Một phương thức muốn thêm các số nguyên vào danh sách không nhất thiết phải yêu cầu đúng `List<Integer>`. Một `List<Number>` cũng nhận được `Integer`, và một `List<Object>` cũng vậy. Nếu chữ ký chỉ nhận `List<Integer>`, nó loại bỏ những nơi nhận hoàn toàn an toàn đó.

Wildcard giới hạn dưới, viết bằng `? super T`, giải quyết vấn đề này: chấp nhận một nơi có thể **tiếp nhận giá trị kiểu T** mà vẫn giữ kiểm tra kiểu của Java.

Bài này tập trung vào chiều ghi dữ liệu và cách phối hợp với chiều đọc trong quy tắc PECS. Ví dụ sử dụng Java 17 trở lên.

## 1. Vấn đề cần giải quyết: nhận đúng loại nơi ghi

Giả sử cần thêm các mã mẫu kiểu `Integer`:

```java
static void addSampleIds(List<Integer> target) {
    target.add(10);
    target.add(20);
}
```

Phương thức nhận được `List<Integer>`, nhưng không nhận một biến `List<Number>` hoặc `List<Object>`. Generic là bất biến: `List<Integer>` không trở thành kiểu con của `List<Number>` chỉ vì `Integer` là kiểu con của `Number`.

Trong trường hợp này, phương thức không cần biết loại phần tử chính xác của đích. Nó chỉ cần bảo đảm đích chấp nhận `Integer`.

Chữ ký phù hợp hơn:

```java
static void addSampleIds(List<? super Integer> target) {
    target.add(10);
    target.add(20);
}
```

Đây là các phương thức minh họa cần đặt trong một lớp có import `java.util.List`.

Nhờ giới hạn dưới, có thể gọi cùng phương thức với các nơi nhận khác nhau:

```java
List<Integer> ids = new ArrayList<>();
List<Number> measurements = new ArrayList<>();
List<Object> values = new ArrayList<>();

addSampleIds(ids);
addSampleIds(measurements);
addSampleIds(values);
```

`ArrayList` cũng cần được import. Kiểu phần tử của các danh sách đích đều cho phép nhận `Integer`. [Oracle: wildcard giới hạn dưới](https://docs.oracle.com/javase/tutorial/java/generics/lowerBounded.html)

## 2. Đọc chính xác ý nghĩa ? super Integer

`List<? super Integer>` không phải “danh sách chỉ chứa lớp cha của Integer”.

Nó biểu thị một danh sách có **đối số kiểu cụ thể chưa biết**, nhưng đối số ấy là `Integer` hoặc một kiểu cha phù hợp của `Integer`.

Có thể hình dung kiểu chưa biết là `S` với quan hệ:

```text
Integer là kiểu con của S.
```

Khi đưa một `Integer` vào nơi nhận `S`, phép ghi an toàn. Nhưng ta không biết chính xác `S` là gì chỉ từ tham chiếu wildcard.

Các ví dụ sau hợp lệ:

| Đối tượng có kiểu | Gán cho `List<? super Integer>`? | Lý do |
| --- | --- | --- |
| `List<Integer>` | Có | Nhận đúng `Integer` |
| `List<Number>` | Có | `Integer` là `Number` |
| `List<Object>` | Có | `Integer` là `Object` |
| `List<Double>` | Không | `Double` không phải kiểu cha của `Integer` |
| `List<String>` | Không | Không có quan hệ nhận `Integer` phù hợp |

Ba dòng hợp lệ đầu là ví dụ quen thuộc, không phải tuyên bố chỉ có đúng ba kiểu được chấp nhận. Quan hệ kiểu cha còn có thể bao gồm interface mà `Integer` triển khai.

Wildcard giới hạn dưới được dùng ở vị trí sử dụng generic. Nó không khai báo một lớp generic mới và không sao chép dữ liệu.

```java
List<Object> original = new ArrayList<>();
List<? super Integer> target = original;

target.add(10);
System.out.println(original); // [10]
```

Hai biến vẫn trỏ đến cùng một đối tượng danh sách; thay đổi qua `target` được thấy qua `original`. [Java Language Specification 17: đối số kiểu và wildcard](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1)

## 3. Có thể ghi những giá trị nào?

### 3.1. Ghi giá trị của giới hạn dưới

Qua `List<? super Integer>`, có thể thêm `Integer`:

```java
List<? super Integer> target = new ArrayList<Number>();
target.add(Integer.valueOf(7));
target.add(8);
```

Ở dòng cuối, giá trị `int` được boxing thành `Integer`.

Với một giới hạn dưới tổng quát `? super Dog`, cũng có thể thêm một đối tượng của lớp con `Dog`, vì nó vẫn dùng được ở nơi nhận `Dog`. Trong ví dụ số nguyên, `Integer` là lớp final nên không có lớp con do người dùng định nghĩa.

### 3.2. Không phải được thêm mọi Number

Đoạn sau **không biên dịch**:

```java
List<? super Integer> target = new ArrayList<Integer>();
target.add(2.5); // Double không được bảo đảm chấp nhận.
```

`2.5` trở thành `Double`. Nhưng danh sách thực tế có thể là `List<Integer>`, nên thêm `Double` sẽ phá cam kết kiểu.

Cũng không được thêm một biến chỉ có kiểu tĩnh `Number`:

```java
Number value = Integer.valueOf(7);
List<? super Integer> target = new ArrayList<Number>();

// Không biên dịch:
target.add(value);
```

Dù đối tượng hiện tại của `value` là `Integer`, biểu thức `value` được kiểm tra dưới kiểu `Number`. Giới hạn dưới không cho phép bỏ qua hợp đồng kiểu của biểu thức.

Nếu dữ liệu vốn phải là `Integer`, hãy giữ thông tin kiểu đó trong thiết kế. Không thêm cast chỉ để vượt lỗi mà chưa có cơ sở.

### 3.3. Thay phần tử cũng chịu quy tắc cùng kiểu

Với một danh sách không rỗng và có hỗ trợ `set`:

```java
List<Number> values = new ArrayList<>(List.<Number>of(2.5));
List<? super Integer> target = values;

Object old = target.set(0, 10);
```

Ghi `Integer` là an toàn, nhưng giá trị cũ chỉ được bảo đảm là `Object`: nó có thể là `Double` như trong ví dụ.

Wildcard giới hạn dưới nói về loại dữ liệu an toàn khi ghi, không chỉ riêng phương thức `add`.

## 4. Vì sao đọc chỉ bảo đảm Object?

Một danh sách nhận `Integer` không nhất thiết chỉ chứa `Integer`:

```java
List<Object> values = new ArrayList<>();
values.add("header");

List<? super Integer> target = values;
target.add(10);

Object first = target.get(0);
System.out.println(first); // header
```

Nếu `get(0)` được coi là `Integer`, ví dụ trên sẽ không an toàn. Vì đích có thể là một `List<Object>` với dữ liệu cũ bất kỳ, kiểu đọc chung chỉ là `Object`.

Các dòng sau **không biên dịch**:

```java
Integer firstInteger = target.get(0);
Number firstNumber = target.get(0);
```

Từ “consumer” trong PECS không có nghĩa đối tượng không đọc được. Nó có nghĩa ta dùng kiểu này để bảo đảm chiều nhận giá trị; thông tin kiểu của dữ liệu đọc ra yếu hơn.

Đừng mô tả `? super Integer` là “write-only”. Ta vẫn có thể đọc dưới kiểu `Object`, hỏi kích thước, duyệt, xóa hoặc thực hiện các thao tác được đối tượng hỗ trợ.

Một phép ép kiểu khi đọc chỉ an toàn nếu có bằng chứng độc lập rằng phần tử phù hợp. Sự hiện diện của wildcard không cung cấp bằng chứng ấy.

## 5. PECS: nhìn luồng dữ liệu từ phương thức

PECS là cách ghi nhớ:

```text
Producer Extends
Consumer Super
```

Dịch theo vai trò:

- Nơi **cung cấp T để phương thức đọc**: dùng `? extends T`.
- Nơi **nhận T do phương thức ghi**: dùng `? super T`.

Xét một phương thức chuyển phần tử từ nguồn sang đích:

```java
static <T> void appendAll(
        Iterable<? extends T> source,
        Collection<? super T> target) {

    for (T value : source) {
        target.add(value);
    }
}
```

Phương thức cần import `java.util.Collection`; `Iterable` thuộc `java.lang`.

- `source` cung cấp các giá trị dùng được như `T`, nên dùng `extends`.
- `target` nhận những giá trị `T`, nên dùng `super`.
- Tham số kiểu `T` liên kết hai phía trong cùng một lời gọi.

Đây là một phương thức **thêm các phần tử vào đích theo thao tác add**, không phải giao dịch di chuyển dữ liệu. Nó không xóa nguồn và không hoàn tác nếu có lỗi giữa chừng. Thứ tự và khả năng giữ phần tử trùng phụ thuộc loại collection đích; chẳng hạn một tập không giữ các phần tử bằng nhau lặp lại.

Điểm nhìn là phương thức đang thiết kế. Một collection có thể cung cấp dữ liệu trong phương thức này nhưng nhận dữ liệu trong phương thức khác; vai trò không gắn vĩnh viễn với tên lớp hay tên biến. [Oracle: hướng dẫn thiết kế wildcard](https://docs.oracle.com/javase/tutorial/java/generics/wildcardGuidelines.html)

### Các cặp nguồn–đích thường gặp

| Kiểu nguồn | Kiểu đích | Có thể chuyển an toàn theo phương thức trên? |
| --- | --- | --- |
| `List<Integer>` | `List<Integer>` | Có |
| `List<Integer>` | `List<Number>` | Có |
| `List<Integer>` | `List<Object>` | Có |
| `List<Double>` | `List<Number>` | Có |
| `List<Number>` | `List<Integer>` | Không |
| `List<String>` | `List<Number>` | Không |

Dòng `List<Number>` sang `List<Integer>` bị từ chối dù nguồn hiện tại tình cờ chỉ có số nguyên. Kiểu nguồn cho phép có những `Number` khác; chữ ký không có bằng chứng rằng mọi phần tử đều là `Integer`.

Khi dùng phương thức minh họa, nguồn không được bị sửa cấu trúc không hợp lệ trong lúc đang duyệt. Đặc biệt, không mặc định an toàn khi nguồn và đích thực chất là cùng một collection hoặc các view chung dữ liệu.

## 6. So sánh ba kiểu dễ nhầm

| Kiểu tham chiếu | Điều biết về phần tử | Ghi `Integer` tùy ý? | Kiểu đọc bảo đảm |
| --- | --- | --- | --- |
| `List<Integer>` | Chính xác kiểu phần tử là `Integer` | Có | `Integer` |
| `List<? extends Number>` | Một kiểu chưa biết thuộc hệ `Number` | Không | `Number` |
| `List<? super Integer>` | Một kiểu chưa biết nhận được `Integer` | Có | `Object` |
| `List<?>` | Một kiểu phần tử chưa biết | Không | `Object` |
| `List<Object>` | Kiểu phần tử là `Object` | Có | `Object` |

Các cột “ghi” nói về kiểm tra kiểu. Danh sách có thể vẫn không cho sửa hoặc có hạn chế giá trị khi chạy.

`List<? extends Number>` không bảo đảm chỉ đọc theo nghĩa bất biến: chẳng hạn `clear()` có thể hợp lệ và thay đổi nguồn. `List<? super Integer>` cũng không bảo đảm chỉ ghi. Wildcard giới hạn những phép sử dụng cần biết loại phần tử; nó không tạo một cơ chế phân quyền sửa ở thời điểm chạy. [Oracle: quan hệ kiểu với wildcard](https://docs.oracle.com/javase/tutorial/java/generics/subtyping.html)

## 7. PECS xuất hiện cả trong callback

### 7.1. Consumer nhận được phần tử

`Iterable.forEach` nhận đối tượng dạng:

```java
Consumer<? super T>
```

Nếu danh sách có phần tử `Integer`, một `Consumer<Number>` hoàn toàn có thể nhận chúng:

```java
List<Integer> ids = List.of(1, 2, 3);

Consumer<Number> printer =
        value -> System.out.println(value.doubleValue());

ids.forEach(printer);
```

Đoạn minh họa cần import `List` và `java.util.function.Consumer`.

Consumer có khả năng xử lý `Number` cũng xử lý được `Integer`. Không bắt người dùng viết lại thành `Consumer<Integer>` khi năng lực hiện có đã đủ.

Trong trường hợp này, đối tượng callback là bên tiêu thụ `T`. Đừng chỉ nhìn tên phương thức `forEach` rồi đoán chiều wildcard. [Java 17: Iterable.forEach](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Iterable.html#forEach(java.util.function.Consumer)), [Consumer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/Consumer.html)

### 7.2. Comparator tiêu thụ hai giá trị để so sánh

`List<E>.sort` nhận `Comparator<? super E>`. Một comparator so sánh được `Number` có thể dùng để sắp danh sách `Integer`:

```java
Comparator<Number> byNumber =
        Comparator.comparingDouble(Number::doubleValue);

List<Integer> ids = new ArrayList<>(List.of(3, 1, 2));
ids.sort(byNumber);

System.out.println(ids); // [1, 2, 3]
```

Đây là minh họa cần import các kiểu được dùng. Comparator tiếp nhận hai phần tử; giá trị `int` trả về của phép so sánh không biến `T` thành dữ liệu đầu ra.

Comparator dùng `doubleValue` chỉ phù hợp với mục tiêu minh họa và miền dữ liệu này. Nó không bảo toàn chính xác thứ tự của mọi loại `Number` có độ chính xác lớn; kiểu generic phù hợp không tự bảo đảm quy tắc so sánh nghiệp vụ đúng. [Java 17: List.sort](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator))

## 8. Đọc chữ ký Collections.copy bằng PECS

Một API tiêu biểu:

```java
static <T> void copy(
        List<? super T> dest,
        List<? extends T> src)
```

Đích nhận giá trị `T`; nguồn cung cấp giá trị dùng được như `T`. Đây là cùng hướng lập luận, dù thứ tự tham số là đích trước và nguồn sau.

Nhưng PECS chỉ giải quyết quan hệ kiểu. Hợp đồng thực thi của `Collections.copy` còn quy định đích phải có **kích thước ít nhất bằng nguồn**. Nó thay các vị trí có sẵn, không tự thêm chỗ như phương thức `appendAll` ở trên.

```java
List<Number> dest =
        new ArrayList<>(List.<Number>of(9.0, 9.0, 9.0));

Collections.copy(dest, List.of(1, 2));
System.out.println(dest); // [1, 2, 9.0]
```

Nếu chỉ viết:

```java
List<Number> dest = new ArrayList<>(10);
Collections.copy(dest, List.of(1, 2)); // IndexOutOfBoundsException.
```

Dung lượng 10 không tạo 10 phần tử; kích thước đích vẫn bằng 0.

Đọc wildcard để hiểu bên nào đọc và bên nào ghi, rồi tiếp tục đọc yêu cầu về kích thước, quyền sửa và ngoại lệ. Một chữ ký generic đúng không thay thế toàn bộ hợp đồng API. [Java 17: Collections.copy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#copy(java.util.List,java.util.List))

## 9. Khi nào không nên chỉ dùng wildcard?

### 9.1. Cần giữ một kiểu chính xác qua nhiều vị trí

Nếu phương thức vừa đọc một giá trị kiểu cụ thể vừa trả nó về, hoặc liên kết nó với tham số khác, một tham số kiểu có tên thường biểu đạt tốt hơn.

Ví dụ:

```java
static <T> T addAndReturn(List<T> target, T value) {
    target.add(value);
    return value;
}
```

Đây là phương thức minh họa. `T` liên kết giá trị được nhận, danh sách và kết quả.

Không biến mọi `List<T>` thành `List<? super T>` theo thói quen. Cần xem hợp đồng mong muốn, cách suy luận kiểu và loại kết quả người gọi cần.

### 9.2. Trả về wildcard khiến bên gọi tiếp tục mang giới hạn ấy

Nếu một phương thức tạo mới một `List<Integer>`, trả `List<? super Integer>` thường làm bên gọi mất khả năng đọc trực tiếp ra `Integer` mà không có lợi ích tương xứng.

Wildcard là công cụ nới điều kiện nhận đầu vào theo vai trò, không phải một mặc định phải xuất hiện ở mọi chỗ. Chỉ đưa wildcard vào kiểu trả về khi giới hạn ấy thực sự là chủ ý của API.

### 9.3. Lower bound không dùng cùng cú pháp với type parameter

Trong Java, không viết:

```java
// Không hợp lệ:
<T super Integer>
```

`super` ở bài này thuộc cú pháp wildcard `? super Integer`. Tham số kiểu có tên sử dụng những quy tắc khai báo khác, chẳng hạn `<T extends Number>` cho giới hạn trên.

Một wildcard không đồng thời viết cả `extends` lẫn `super` để tạo khoảng tùy ý. Đừng suy rộng cú pháp từ cách đọc nghĩa tiếng Việt.

## 10. Giới hạn kiểu không bảo đảm quyền sửa hay tính nguyên tử

### 10.1. Đối tượng có thể không hỗ trợ add

Phương thức nhận `List<? super Integer>` chấp nhận một biến `List<Integer>` về mặt kiểu, nhưng đối tượng có thể được tạo bằng `List.of`:

```java
List<Integer> target = List.of(1, 2);
addSampleIds(target); // UnsupportedOperationException.
```

Nếu phương thức cần ghi, tài liệu của nó nên nói đích phải hỗ trợ thao tác đó. Nếu phương thức sở hữu việc tạo kết quả, có thể chủ động chọn cấu trúc có thể sửa.

Tương tự, `null` có thể hợp lệ về kiểu nhưng bị collection từ chối theo chính sách riêng. Wildcard không diễn đạt đầy đủ tính null của dữ liệu.

### 10.2. Ghi nhiều lần không tự thành giao dịch

Nếu lần `add` thứ nhất thành công nhưng lần tiếp theo lỗi, các phần tử đã thêm không được hợp đồng wildcard tự hoàn tác.

`List<? super T>` cũng không có nghĩa an toàn đồng thời. Đích là `ArrayList` dùng chung giữa nhiều luồng vẫn cần cơ chế phối hợp phù hợp; kiểu generic không làm phát sinh khóa.

### 10.3. Không dùng raw type hoặc cast để bỏ qua lỗi kiểu

Nếu nguồn có kiểu `List<Number>` mà đích là `List<Integer>`, lỗi có thể phản ánh một thiết kế thật sự không an toàn. Hãy xác định có cần kiểm tra từng giá trị, chuyển đổi dữ liệu hoặc chọn đích rộng hơn không.

Ép qua raw `List` hoặc tắt cảnh báo unchecked không chuyển `Double` thành `Integer`. Nó chỉ bỏ một phần kiểm tra, khiến lỗi có thể xuất hiện muộn hơn ở nơi đọc.

## 11. Chương trình Java 17 hoàn chỉnh

Lưu vào `LowerBoundPecsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class LowerBoundPecsDemo {
    private static void addSampleIds(List<? super Integer> target) {
        target.add(10);
        target.add(20);
    }

    private static <T> void appendAll(
            Iterable<? extends T> source,
            Collection<? super T> target) {
        for (T value : source) {
            target.add(value);
        }
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>();
        List<Number> numbers = new ArrayList<>(List.<Number>of(2.5));
        List<Object> objects = new ArrayList<>(List.of("header"));

        addSampleIds(integers);
        addSampleIds(numbers);
        addSampleIds(objects);
        System.out.println("integers = " + integers);
        System.out.println("numbers = " + numbers);
        System.out.println("objects = " + objects);

        List<? super Integer> sink = objects;
        Object existing = sink.get(0);
        System.out.println("existing sink value = " + existing);

        List<Integer> source = List.of(3, 1, 2);
        List<Number> destination = new ArrayList<>();
        appendAll(source, destination);
        System.out.println("appended numbers = " + destination);
        System.out.println("source unchanged = " + source);

        Comparator<Number> byNumber =
                Comparator.comparingDouble(Number::doubleValue);
        integers = new ArrayList<>(source);
        integers.sort(byNumber);
        System.out.println("sorted integers = " + integers);

        Consumer<Number> printer =
                value -> System.out.println("consume = " + value);
        integers.forEach(printer);

        List<Number> slots = new ArrayList<>(
                List.<Number>of(9.0, 9.0, 9.0));
        Collections.copy(slots, List.of(1, 2));
        System.out.println("copied into existing slots = " + slots);

        List<Number> capacityOnly = new ArrayList<>(10);
        try {
            Collections.copy(capacityOnly, List.of(1, 2));
        } catch (IndexOutOfBoundsException expected) {
            System.out.println("capacity alone is not enough for Collections.copy");
        }

        List<Integer> unmodifiable = List.of(1, 2);
        try {
            addSampleIds(unmodifiable);
        } catch (UnsupportedOperationException expected) {
            System.out.println("compatible type does not promise mutability");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 LowerBoundPecsDemo.java
java LowerBoundPecsDemo
```

Stdout:

```text
integers = [10, 20]
numbers = [2.5, 10, 20]
objects = [header, 10, 20]
existing sink value = header
appended numbers = [3, 1, 2]
source unchanged = [3, 1, 2]
sorted integers = [1, 2, 3]
consume = 1
consume = 2
consume = 3
copied into existing slots = [1, 2, 9.0]
capacity alone is not enough for Collections.copy
compatible type does not promise mutability
```

Cách đọc minh chứng:

1. Cùng một phương thức lower bound ghi được `Integer` vào ba loại nơi nhận.
2. Đích `List<Object>` vẫn giữ phần tử cũ `"header"`; đọc qua lower bound chỉ có thể dựa vào `Object`.
3. Hàm `appendAll` phối hợp nguồn `extends` và đích `super`, tạo thêm các phần tử ở đích mà không xóa nguồn.
4. `Comparator<Number>` nhận được các phần tử `Integer` để so sánh.
5. `Consumer<Number>` nhận được từng `Integer` từ `forEach`.
6. `Collections.copy` thay các vị trí đích có sẵn và không đụng vị trí thừa.
7. Dự trữ dung lượng không đủ đáp ứng yêu cầu kích thước của `Collections.copy`.
8. Loại generic phù hợp không làm một danh sách không sửa được trở thành có thể sửa.

Ví dụ dùng các nguồn và đích riêng cho `appendAll`, tránh việc thêm vào chính collection đang được duyệt.

## 12. Ca cố ý không biên dịch

Lưu riêng tệp `LowerBoundMustFail.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class LowerBoundMustFail {
    public static void main(String[] args) {
        List<? super Integer> target = new ArrayList<Integer>();
        target.add(2.5);
    }
}
```

Chạy:

```shell
javac --release 17 -encoding UTF-8 LowerBoundMustFail.java
```

**Kỳ vọng:** trình biên dịch từ chối `target.add(2.5)`.

Nguyên nhân là `target` có thể trỏ đến `List<Integer>`, nên không thể bảo đảm nhận `Double`. Thông báo có thể nhắc đến một kiểu capture chưa biết; đó là cách trình biên dịch biểu diễn kiểu phần tử cụ thể đang bị wildcard che đi.

Không đặt tệp cố ý lỗi vào tập nguồn cần build thành công. Đây là phép kiểm chứng quy tắc kiểu, không phải ví dụ chương trình chạy.

## 13. Quy trình chọn bound cho một tham số

Với mỗi tham số generic của phương thức, làm lần lượt:

1. Chọn kiểu giá trị `T` mà phương thức thực sự cần xử lý.
2. Xác định phương thức lấy `T` ra từ tham số hay đưa `T` vào đó.
3. Nếu lấy ra như `T`, bắt đầu cân nhắc `? extends T`.
4. Nếu đưa `T` vào, bắt đầu cân nhắc `? super T`.
5. Nếu cần giữ quan hệ kiểu chính xác qua nhiều chỗ, cân nhắc tham số kiểu có tên.
6. Kiểm tra quyền sửa, `null`, kích thước và điều kiện đồng thời ngoài hệ thống generic.
7. Thử cả lời gọi cần được nhận lẫn lời gọi phải bị từ chối.

Ví dụ với danh sách nhận `Integer`, hãy kiểm tra `List<Integer>`, `List<Number>` và `List<Object>` đều dùng được, trong khi `List<Double>` bị từ chối. Đây là cách đánh giá tính linh hoạt mà không làm mất an toàn.

## 14. Lỗi thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| `super Integer` nghĩa là thêm mọi lớp cha của Integer | Nó bảo đảm đích nhận `Integer`; không bảo đảm nhận mọi `Number` hoặc `Object` |
| Đích lower bound chỉ chứa Integer | Nó có thể là `List<Object>` với dữ liệu cũ khác loại |
| Đọc từ `List<? super Integer>` được `Number` | Chỉ bảo đảm `Object` |
| PECS dựa trên tên biến source/target | Phải nhìn hướng dữ liệu từ phương thức |
| Consumer luôn là collection đích | Callback nhận phần tử cũng là consumer của kiểu đó |
| Wildcard làm collection có thể sửa | Quyền sửa vẫn do đối tượng và API quyết định |
| Có dung lượng là `Collections.copy` dùng được | Đích phải có đủ số phần tử hiện có |
| Dùng `super` cho cả nguồn lẫn đích sẽ linh hoạt hơn | Nguồn khi đó không cho đọc với kiểu `T` cần thiết |
| Dùng `extends` cho cả hai phía vẫn ghi được T | Đích có kiểu phần tử chưa biết nên không nhận một T tùy ý |
| Suppress warning là cách giải quyết bound | Nó không chứng minh dữ liệu an toàn |

## 15. Tự luyện

1. Một phương thức thêm `Long` vào collection. Nếu muốn nhận cả `Collection<Long>`, `Collection<Number>` và `Collection<Object>`, nên bắt đầu với chữ ký nào?  
   **Đáp án:** `Collection<? super Long>`, kèm yêu cầu đích hỗ trợ thêm.

2. Vì sao `List<? super Integer>` không cho thêm `Double`?  
   **Đáp án:** danh sách thật có thể có kiểu phần tử `Integer`.

3. Vì sao đọc từ đích lower bound không bảo đảm ra `Integer`?  
   **Đáp án:** đích có thể là một danh sách kiểu rộng hơn và đã chứa giá trị khác.

4. Một hàm chuyển dữ liệu số nguyên sang danh sách số nên đặt wildcard hai phía thế nào?  
   **Gợi ý:** nguồn cung cấp kiểu dùng được như `T` với `extends`; đích nhận `T` với `super`.

5. `Comparator<Object>` có thể dùng cho `List<String>.sort` về mặt kiểu không?  
   **Đáp án:** có, vì nó nhận được `String`. Nhưng logic so sánh vẫn phải thực sự hợp lệ cho các phần tử và đáp ứng hợp đồng comparator.

Giới hạn dưới giúp viết nơi nhận dữ liệu linh hoạt mà không làm mất cam kết kiểu. PECS trở nên dễ áp dụng khi luôn xác định rõ giá trị nào đi ra từ nguồn, giá trị nào đi vào đích và những yêu cầu nào còn nằm ngoài kiểm tra generic.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="element-types-006">Raw type, ép kiểu và ô nhiễm kiểu trong collection</a>

<details>
<summary>Xem chi tiết</summary>

# Raw type, ép kiểu và ô nhiễm kiểu trong collection

Generics giúp một biến như `List<String>` diễn đạt rằng các phần tử được dùng như chuỗi. Compiler có thể từ chối nhiều thao tác sai ngay khi biên dịch:

```java
List<String> names = new ArrayList<>();
names.add("An");
```

Nếu cố thêm một `Integer` qua biến này, compiler báo lỗi. Nhưng khi collection đi qua raw type, ép kiểu không được kiểm chứng hoặc một số cách dùng mảng generic, sự bảo vệ đó có thể bị phá vỡ.

Điều khó chẩn đoán là nơi gây lỗi và nơi phát hiện lỗi có thể cách xa nhau: một đoạn mã thêm sai phần tử, đoạn khác đọc dưới kiểu đã được hứa hẹn rồi mới gặp `ClassCastException`.

Bài dùng Java 17, giải thích raw type, type erasure, các loại ép kiểu, ô nhiễm kiểu và cách xử lý ranh giới dữ liệu. Chương trình đầy đủ giữ một số cảnh báo có chủ ý để quan sát vấn đề; các đoạn được ghi rõ là lỗi biên dịch không phải lời khuyên sử dụng.

## 1. Hợp đồng kiểu phần tử bảo vệ điều gì?

Với:

```java
List<String> names = new ArrayList<>();
```

compiler biết:

- `add` cần phần tử phù hợp với `String`.
- `get` được dùng như giá trị `String`.
- Các lời gọi có thể được kiểm tra theo kiểu của biến và chữ ký phương thức.

Điều này không có nghĩa đối tượng `ArrayList` tự giữ một bộ kiểm tra `String` ở mọi vị trí trong bộ nhớ. Thông tin kiểu tại thời điểm biên dịch và kiểm tra lớp thực tế khi chạy là hai lớp cơ chế khác nhau.

Nếu mọi đường ghi vào collection tuân thủ cùng hợp đồng generic, compiler giúp duy trì sự nhất quán. Nếu có đường đi bỏ qua hợp đồng đó, một biến có khai báo đẹp vẫn có thể trỏ tới dữ liệu đã bị làm sai kiểu.

## 2. Raw type là gì và vì sao vẫn tồn tại?

**Raw type** là cách dùng một kiểu generic mà bỏ các đối số kiểu, chẳng hạn dùng `List` thay vì `List<String>`.

Đoạn minh họa:

```java
List raw = new ArrayList();
raw.add("An");
raw.add(42);
```

Raw type tồn tại chủ yếu để tương thích với mã có trước generics và các API cũ. Không nên xem nó là cách viết ngắn tương đương cho `List<Object>` hoặc `List<?>`.

Khi dùng raw type, các thành viên liên quan đến tham số kiểu được nhìn qua kiểu đã xóa trong những ngữ cảnh tương ứng. Với `List`, lời gọi thêm phần tử không còn được kiểm tra như một `List<String>`.

Compiler có thể cảnh báo:

- Dùng một raw type.
- Gọi một phương thức theo cách không thể kiểm chứng hợp đồng generic.
- Chuyển từ raw type sang kiểu có tham số mà không xác minh được.

Định nghĩa raw type nằm trong [JLS 17, mục Raw Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.8).

### 2.1. Khai báo raw chưa tự làm sai dữ liệu

Có một tham chiếu raw chưa có nghĩa đã xảy ra lỗi. Rủi ro xuất hiện khi tham chiếu đó cho phép thực hiện thao tác mà các bên đang dùng kiểu tham số không thể chấp nhận.

Ví dụ, giữ một raw alias nhưng không ghi sai kiểu chưa giống với dùng nó để chèn một số vào danh sách đang được coi là danh sách chuỗi.

Tách hai câu hỏi:

1. Có mất khả năng kiểm chứng tĩnh không?
2. Dữ liệu hoặc các giả định kiểu đã bị vi phạm chưa?

Cảnh báo thường nói về nguy cơ ở câu thứ nhất; không nên đợi có lỗi thực tế mới xử lý nó.

## 3. Phân biệt List, `List<Object>`, `List<?>` và `List<String>`

| Kiểu khai báo | Ý nghĩa | Đọc phần tử như | Thêm phần tử |
| --- | --- | --- | --- |
| `List<String>` | Biết phần tử theo hợp đồng là chuỗi | `String` | Giá trị phù hợp với `String` |
| `List<Object>` | Hợp đồng cho phép các tham chiếu đối tượng khác nhau | `Object` | Các giá trị đối tượng phù hợp |
| `List<?>` | Có một kiểu phần tử nhưng chưa biết cụ thể là gì | `Object` | Không thể thêm một giá trị khác null tùy ý theo kiểm tra kiểu |
| Raw `List` | Dùng kiểu generic mà bỏ đối số kiểu | Thường là kiểu đã xóa, ở đây là `Object` | Có thể viết thao tác không được kiểm chứng generic |

Các thao tác sửa còn phải được lớp triển khai hỗ trợ. Bảng nói về khả năng theo kiểu, không hứa một danh sách bất kỳ sẽ cho thêm.

### 3.1. `List<Object>` có thể chứa nhiều kiểu một cách có chủ đích

Đoạn minh họa:

```java
List<Object> values = new ArrayList<>();
values.add("An");
values.add(42);
```

Danh sách chứa nhiều kiểu không tự đồng nghĩa ô nhiễm kiểu. Hợp đồng `List<Object>` vốn cho phép các giá trị đó; bên đọc nhận `Object` và phải quyết định xử lý phù hợp.

Ô nhiễm kiểu liên quan tới việc phá vỡ một giả định kiểu đã được khai báo, không phải cứ thấy hai lớp phần tử khác nhau là kết luận có ô nhiễm.

### 3.2. `List<String>` không phải `List<Object>`

Đoạn dưới không biên dịch:

```java
List<String> names = new ArrayList<>();
List<Object> objects = names;
```

Nếu phép gán này được cho phép, bên giữ `objects` có thể thêm `Integer`, khiến bên giữ `names` đọc sai kiểu.

Generics không có quan hệ kiểu con theo cách đó. Dùng `List<?>` nếu chỉ cần nhận một danh sách có kiểu phần tử chưa biết:

```java
List<?> unknown = names;
```

### 3.3. `List<?>` không phải danh sách bất biến

Không thể gọi `unknown.add("text")` chỉ vì chuỗi là một đối tượng: kiểu phần tử thực sự có thể là `Integer`.

Tuy nhiên, `List<?>` không cấm mọi thay đổi. Chẳng hạn, xóa hoặc `clear` có thể hợp lệ theo kiểu và được thực hiện nếu lớp triển khai hỗ trợ.

`null` có thể phù hợp theo kiểm tra kiểu với một tham chiếu phần tử chưa biết, nhưng collection thực tế có thể cấm `null` hoặc cấm thêm. Không dùng wildcard để suy ra khả năng sửa hay chính sách `null`.

## 4. Ô nhiễm kiểu trong collection

**Ô nhiễm kiểu (heap pollution)** xảy ra khi một biến có kiểu tham số tham chiếu đến đối tượng không đáp ứng kiểu tham số được giả định. Trong collection, biểu hiện thường gặp là một danh sách đang được dùng như `List<String>` lại chứa phần tử không phải chuỗi do một đường ghi không an toàn.

Ví dụ:

```java
List<String> names = new ArrayList<>();
List raw = names;
raw.add(42);
```

Đồ thị tham chiếu:

```text
names, được nhìn như List<String> ----+
                                    |
                                    v
                              cùng ArrayList
                                    |
raw, được nhìn như List -------------+
                                    |
                                    v
                               phần tử Integer 42
```

Không có hai danh sách. Phép gán chỉ tạo thêm một tham chiếu tới cùng đối tượng, với khả năng kiểm tra kiểu khác ở phía mã nguồn.

Lời gọi qua raw type có thể chèn số vào vùng dữ liệu mà bên khác đang tin là danh sách chuỗi. Khái niệm được quy định trong [JLS 17, Heap Pollution](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.2).

### 4.1. Lỗi thường xuất hiện muộn

Đoạn tiếp theo:

```java
String first = names.get(0);
```

gặp `ClassCastException` khi phần tử thực tế là `Integer`.

Nơi đọc có vẻ hoàn toàn bình thường: biến được khai báo `List<String>`, nên lập trình viên không viết một cast tường minh nào. Nhưng compiler cần thực hiện kiểm tra phù hợp khi đưa kết quả đã xóa kiểu về `String`.

Vì vậy, stack trace ở `get` chưa chắc chỉ ra nơi gốc của lỗi. Cần truy ngược các đường tạo, trả về, alias và ghi vào collection.

### 4.2. Không phải mọi thao tác đều phát hiện ngay

Các thao tác như `size()` không cần dùng phần tử như `String`. Chúng có thể tiếp tục hoạt động dù danh sách đã bị sai kiểu.

Ngay cả đọc phần tử qua một tham chiếu chỉ yêu cầu `Object` cũng chưa nhất thiết buộc kiểm tra như chuỗi.

Không dùng việc “in được”, “đếm được” hay “đã đọc vài phần tử đầu” làm bằng chứng toàn collection đáp ứng kiểu mong muốn.

## 5. Type erasure giải thích điều gì?

Java triển khai generics chủ yếu bằng **xóa kiểu (type erasure)**.

Ở mức cần cho bài này:

- Hai `ArrayList` tạo với đối số kiểu `String` và `Integer` không trở thành hai lớp runtime chuyên biệt khác nhau chỉ vì đối số generic khác.
- Với `List<E>` không có cận cụ thể hơn, kiểu đã xóa liên quan đến phần tử là `Object`.
- Compiler kiểm tra kiểu ở mã nguồn và có thể chèn cast khi cần để khôi phục cách sử dụng theo kiểu đã khai báo.

Có thể hình dung lời đọc:

```java
String value = names.get(0);
```

ở mức cơ chế gần với “đọc đối tượng rồi kiểm tra dùng như `String`”. Đây là mô hình để hiểu, không phải yêu cầu mọi compiler luôn sinh bytecode giống hệt nhau.

### 5.1. Không nói quá rằng mọi thông tin generic đều biến mất

Chữ ký generic của khai báo lớp hoặc phương thức có thể tồn tại trong metadata và được phản chiếu ở những ngữ cảnh phù hợp.

Điểm cần phân biệt là các tham số kiểu của một biến như `List<String>` không tự biến thành một kiểm tra sâu mọi phần tử trên từng đối tượng danh sách thông thường.

Biết có thể đọc metadata của khai báo không có nghĩa đã xác minh được danh sách do mã ngoài đưa vào chỉ chứa chuỗi.

### 5.2. Bound và erasure không phải luôn là Object

Một tham số kiểu có cận có thể được xóa về cận tương ứng. Bài dùng `List<E>` để giải thích vì trường hợp này dễ quan sát, không đưa ra quy tắc “mọi generic luôn xóa thành Object” cho toàn bộ Java.

Xem [JLS 17, Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6).

## 6. Ép kiểu collection kiểm tra được đến đâu?

### 6.1. Cast tham chiếu không chuyển đổi các phần tử

Giả sử có:

```java
Object candidate = List.of(1, 2, 3);
```

Ép `candidate` thành `List<String>` không biến các số thành chuỗi `"1"`, `"2"`, `"3"`. Nó chỉ yêu cầu nhìn cùng tham chiếu theo một kiểu khác, kèm những kiểm tra runtime mà hệ thống kiểu có thể thực hiện.

Nếu muốn chuyển đổi giá trị, cần thao tác chuyển đổi rõ ràng, chẳng hạn ánh xạ từng số sang biểu diễn chuỗi. Cast không thay cho chuyển đổi dữ liệu.

### 6.2. Checked cast ở lớp bên ngoài

Một cast sang một kiểu runtime có thể kiểm tra được sẽ xác minh lớp hoặc interface tương ứng.

Nếu đối tượng thực tế không phải list, cast sang `List` có thể ném `ClassCastException` ngay.

Nếu đối tượng là list chứa số, kiểm tra “có phải List không?” có thể qua được. Nhưng điều đó chưa kiểm tra phần tử bên trong là chuỗi.

### 6.3. Unchecked cast

Đoạn minh họa cố ý không an toàn:

```java
Object candidate = List.of(1, 2, 3);
List<String> names = (List<String>) candidate;
```

Compiler cảnh báo vì không thể kiểm chứng đầy đủ đối số generic `String` ở cast này. Lớp ngoài có thể được kiểm tra là `List`, nhưng thành phần generic vẫn không được chứng minh.

Đừng diễn giải “unchecked” thành “không có bất kỳ kiểm tra runtime nào”. Một cast có thể còn kiểm tra lớp ngoài nhưng không đủ để chứng minh toàn bộ kiểu tham số.

Nếu sau đó đọc phần tử như `String`, mới có thể gặp lỗi ở vị trí đọc.

### 6.4. Unchecked conversion không nhất thiết có cú pháp cast

Ví dụ:

```java
List raw = legacyResult();
List<String> names = raw;
```

Lời gán thứ hai có thể tạo cảnh báo unchecked conversion. Không có dấu ngoặc cast nhưng vẫn có một lời hứa về kiểu phần tử chưa được kiểm chứng.

Hàm `legacyResult` ở đây chỉ là ký hiệu cho API cũ, không phải phương thức được định nghĩa trong chương trình mẫu.

### 6.5. Tập trung vào nguồn bằng chứng

Muốn tin `List<String>`, cần một trong các cơ sở phù hợp:

- Luồng dữ liệu được compiler kiểm tra xuyên suốt.
- Một API có hợp đồng đáng tin và được triển khai đúng.
- Kiểm tra từng phần tử cùng việc kiểm soát các đường ghi về sau.
- Sao chép sau kiểm tra vào một cấu trúc mới có kiểu rõ ràng.

Một cast hoặc một annotation chỉ làm mã bớt cảnh báo không tự bổ sung bằng chứng này. Các loại chuyển đổi được mô tả trong [JLS 17, Conversions and Contexts](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html).

## 7. Kiểm tra dữ liệu chưa rõ kiểu bằng wildcard

### 7.1. Kiểm tra lớp ngoài trước

Khi đầu vào có kiểu `Object`, có thể kiểm tra:

```java
if (candidate instanceof List<?> items) {
    for (Object item : items) {
        // Xu ly theo kieu thuc te cua item.
    }
}
```

Khối trên là đoạn minh họa chưa có xử lý nghiệp vụ.

Với `Object` chưa rõ kiểu, không thể dùng `instanceof List<String>` như một phép kiểm tra sâu hợp lệ để chứng minh mọi phần tử là chuỗi. Kiểu `List<?>` thích hợp để kiểm tra hình dạng ngoài mà không hứa một kiểu phần tử chưa được chứng minh.

Không tổng quát thành “Java không bao giờ cho xuất hiện kiểu có tham số ở phía instanceof” trong mọi ngữ cảnh. Quy tắc phụ thuộc khả năng kiểm tra và kiểu đã biết; bài này đang nói về đối tượng đầu vào hoàn toàn chưa biết.

### 7.2. Kiểm tra từng phần tử rồi tạo bản sao

Một hàm có chính sách chỉ nhận chuỗi không null:

```java
static List<String> copyStrings(Collection<?> source) {
    List<String> result = new ArrayList<>();
    for (Object value : source) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Expected non-null String");
        }
        result.add(text);
    }
    return result;
}
```

Hàm trên cần đặt trong một lớp cùng các import phù hợp. Chương trình đầy đủ bên dưới có phiên bản bổ sung thông báo kiểu thực tế.

Cơ chế:

1. Nhận đầu vào mà không hứa trước kiểu phần tử cụ thể.
2. Đọc từng phần tử như `Object`.
3. Kiểm tra bằng `instanceof String`.
4. Chỉ thêm giá trị đã được chứng minh vào `List<String>` mới.
5. Trả kết quả không chia sẻ cấu trúc danh sách với đầu vào.

Nếu kiểm tra lỗi ở giữa, danh sách nguồn không bị sửa bởi hàm. Bản kết quả đang xây dựng chưa được trả ra.

Chính sách `null` là chủ ý: `instanceof String` trả `false` với `null`, nên hàm từ chối nó. Nếu ứng dụng chấp nhận `null`, cần thiết kế nhánh tương ứng rõ ràng.

### 7.3. Vì sao kiểm tra rồi cast cùng đối tượng vẫn có thể rủi ro?

Ngay cả khi vừa duyệt thấy mọi phần tử là chuỗi, nếu bên khác vẫn có raw alias tới chính danh sách đó, họ có thể chèn giá trị khác sau bước kiểm tra.

Sao chép vào danh sách mới giúp tách cấu trúc và đường ghi, nhưng không giải quyết mọi tình huống:

- Nguồn bị sửa đồng thời trong khi sao chép cần cơ chế phù hợp.
- Phần tử có thể thay đổi vẫn có thể được chia sẻ.
- Quy tắc kiểm tra cần khớp yêu cầu nghiệp vụ, không chỉ lớp Java.

Với ví dụ chuỗi, phần tử `String` bất biến giúp ranh giới dễ quản lý hơn.

### 7.4. Generic lồng cần kiểm tra từng tầng

Với yêu cầu `List<List<String>>`, biết mỗi phần tử là `List` chưa chứng minh các phần tử bên trong là `String`.

Tương tự, `Map<String, List<Integer>>` cần xét kiểu khóa, kiểu collection giá trị và kiểu phần tử trong mỗi danh sách.

Một `Class<List>` hoặc `List.class` không mang đầy đủ điều kiện kiểm tra `List<String>`. Cần kiểm tra có cấu trúc hoặc dùng cơ chế ánh xạ dữ liệu có hợp đồng rõ ràng.

## 8. Collections.checkedList: phát hiện một số lỗi tại nơi ghi

Java cung cấp các view kiểm tra kiểu động, ví dụ:

```java
List<String> backing = new ArrayList<>();
List<String> checked = Collections.checkedList(backing, String.class);
```

Nếu một đoạn mã raw cố chèn `Integer` **qua checked view**, nó bị từ chối bằng `ClassCastException` tại lần chèn.

Điều này đưa điểm phát hiện gần nơi gây lỗi hơn so với chờ một bên khác đọc phần tử như chuỗi.

### 8.1. Không phải cách sửa mọi raw type

Dùng wrapper không phải lý do tiếp tục thiết kế API mới bằng raw type. Nó là một công cụ bảo vệ động hoặc chẩn đoán ở ranh giới phù hợp.

Khi có thể sửa kiểu khai báo và chữ ký phương thức, đó thường là cách tốt hơn để compiler kiểm tra luồng dữ liệu.

### 8.2. Không kiểm tra toàn bộ dữ liệu đã có khi tạo view

Tạo một checked view không nên được coi là thủ tục xác minh sâu hoặc làm sạch mọi phần tử đang có trong danh sách nền.

Nếu nguồn đã sai kiểu, cần kiểm tra dữ liệu hoặc tạo bản sao hợp lệ thay vì chỉ bọc lại và tin rằng mọi thứ đã được sửa.

### 8.3. Tham chiếu đi thẳng vào backing list vẫn có thể vượt qua wrapper

Nếu bên khác giữ `backing` và dùng một raw alias để ghi thẳng vào nó, họ không đi qua kiểm tra của `checked`.

View và list nền chia sẻ dữ liệu. Tất cả đường ghi liên quan phải được kiểm soát thì wrapper mới cung cấp ranh giới có ý nghĩa.

### 8.4. Chỉ kiểm tra kiểu runtime được cung cấp

`String.class` đủ để kiểm tra một phần tử có phải chuỗi trong ví dụ này. Với kiểu generic lồng, cung cấp lớp ngoài không tự kiểm tra đối số kiểu bên trong.

Wrapper cũng không phải deep copy, cơ chế bất biến hay cơ chế đồng bộ giữa các luồng. Xem [Collections.checkedList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#checkedList(java.util.List,java.lang.Class)).

## 9. Bản sao và view không tự khôi phục tính đúng kiểu

### 9.1. Sao chép một nguồn đã bị khai báo sai

Nếu biến `names` mang kiểu `List<String>` nhưng dữ liệu đã bị ô nhiễm, viết:

```java
List<String> copy = new ArrayList<>(names);
```

không phải một phép xác minh từng phần tử là chuỗi. Compiler đang tin vào kiểu của nguồn; constructor sao chép cũng không nhận `String.class` để làm kiểm tra đó.

Bản mới có thể tiếp tục mang dữ liệu không phù hợp và lỗi chỉ lộ khi đọc theo kiểu cụ thể.

### 9.2. Unmodifiable không có nghĩa đã xác minh kiểu

Bọc nguồn bằng view không cho sửa chỉ hạn chế những thao tác sửa qua view. Nó không biến một `Integer` đã chèn sai thành `String`.

`List.copyOf` cũng không phải bộ xác minh sâu generic theo tên kiểu mà caller mong muốn. Những kiểm tra khác của API, chẳng hạn chính sách `null`, không đồng nghĩa đã kiểm tra mọi điều kiện phần tử của ứng dụng.

### 9.3. Kiểm tra đúng lớp chưa đủ đúng nghiệp vụ

Một danh sách toàn `String` vẫn có thể chứa mã rỗng, email sai định dạng hoặc giá trị ngoài miền chấp nhận.

Cần phân biệt:

- Đúng kiểu Java.
- Đúng cấu trúc dữ liệu.
- Đúng ràng buộc nghiệp vụ.

Không dùng một cast generic thay cho cả ba tầng kiểm tra.

## 10. Generic varargs và mảng: một con đường gây ô nhiễm khác

Raw type là một nguồn thường gặp, nhưng không phải nguồn duy nhất. Mảng và tham số varargs với kiểu generic cũng có thể làm phá vỡ hợp đồng.

### 10.1. Vì sao mảng là điểm nhạy cảm?

Mảng có thông tin kiểu thành phần runtime và có quan hệ kiểu con theo cách khác generics. Trong khi đó, `List<String>` không được biểu diễn như một lớp danh sách chuyên biệt runtime theo `String`.

Java không cho tạo trực tiếp:

```java
new List<String>[2]
```

Đây là ví dụ không biên dịch. Không dùng cast vòng qua raw array như một cách tổng quát để tạo mảng generic an toàn.

### 10.2. Ví dụ varargs không an toàn

Đoạn minh họa cần đặt trong một lớp:

```java
static String unsafeVarargs(List<String>... groups) {
    Object[] exposed = groups;
    exposed[0] = List.of(123);
    return groups[0].get(0);
}
```

Diễn biến:

1. Tham số varargs được dùng qua một mảng có kiểu thành phần runtime không kiểm tra được đối số `String` của `List`.
2. Mảng đó được nhìn như `Object[]`.
3. Một `List<Integer>` được đặt vào vị trí mà phía mã nguồn đang coi là `List<String>`.
4. Đọc phần tử đầu như `String` gây `ClassCastException`.

Ở đây không có một raw `List` trong thân hàm, nhưng vẫn có khả năng ô nhiễm kiểu qua mảng và các alias.

### 10.3. @SafeVarargs là lời xác nhận của tác giả

Annotation này không chèn thêm một bộ kiểm tra kiểu runtime và không làm một thân hàm nguy hiểm tự trở nên an toàn.

Không gắn `@SafeVarargs` vào ví dụ trên chỉ để tắt cảnh báo. Tác giả phải chứng minh cách dùng varargs không gây ô nhiễm, đồng thời tuân thủ những vị trí khai báo được phép dùng annotation.

Một mẫu có thể an toàn khi chỉ đọc các danh sách đầu vào và chép phần tử sang kết quả mới:

```java
@SafeVarargs
static <T> List<T> concatenate(List<? extends T>... groups) {
    List<T> result = new ArrayList<>();
    for (List<? extends T> group : groups) {
        result.addAll(group);
    }
    return result;
}
```

Hàm không ghi một danh sách không phù hợp vào mảng varargs hoặc đưa chính mảng đó ra ngoài. Lời xác nhận vẫn cần xét toàn bộ mã thực tế, không chỉ hình dạng chữ ký.

Xem [SafeVarargs Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/SafeVarargs.html).

## 11. Cảnh báo compiler là một phần của bằng chứng

### 11.1. Bật cảnh báo liên quan

Lệnh thường dùng khi xem xét loại mã này:

```shell
javac --release 17 -Xlint:rawtypes,unchecked Example.java
```

- `rawtypes` giúp thấy nơi bỏ đối số kiểu.
- `unchecked` giúp thấy nơi compiler không kiểm chứng đầy đủ chuyển đổi, lời gọi hoặc một số trường hợp generic liên quan.

Cảnh báo không nhất thiết làm biên dịch thất bại. Vì vậy, “build thành công” chưa chứng minh không có nguy cơ kiểu.

### 11.2. Không đồng nhất mọi cảnh báo với một lỗi runtime chắc chắn

Một phép cast có thể đúng nhờ một bất biến mà compiler không diễn đạt được. Ngược lại, một cast có thể sai nhưng chưa gặp dữ liệu làm nó lộ lỗi.

Mục tiêu là tìm bằng chứng cho tính đúng, không chỉ làm cảnh báo biến mất.

### 11.3. SuppressWarnings không thêm bảo vệ

`@SuppressWarnings("unchecked")` chỉ tác động cách báo cảnh báo; nó không kiểm tra các phần tử và không đổi kết quả runtime.

Nếu thật sự cần dùng:

1. Đặt ở phạm vi nhỏ nhất chứa thao tác không thể kiểm chứng.
2. Giải thích bất biến nào làm thao tác đó an toàn.
3. Giữ các đường ghi và quyền sở hữu dữ liệu phù hợp bất biến.
4. Có kiểm thử mang ý nghĩa kiểm tra bất biến.

Không tắt cảnh báo cho cả module để che một ranh giới API cũ chưa được phân tích.

### 11.4. Chú ý cảnh báo trong thư viện và ranh giới khác

Mã caller không có cast nguy hiểm không đủ chứng minh toàn bộ chương trình không bị ô nhiễm. Một thư viện, API cũ hoặc cơ chế ngoài kiểm tra generic có thể trả một đối tượng với lời hứa kiểu không đúng.

Khi lỗi xuất hiện ở một dòng đọc đơn giản, phải xét cả nguồn dữ liệu và những nơi khác giữ alias.

## 12. Chương trình Java hoàn chỉnh

Chương trình sau cố ý tạo một số tình huống không an toàn để so sánh điểm phát hiện. Không dùng các hàm `legacyAdd` hoặc `unsafeVarargs` làm mẫu API mới.

Lưu vào tệp `CollectionTypePollutionDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CollectionTypePollutionDemo {
    private static void legacyAdd(List list, Object value) {
        list.add(value);
    }

    private static List<String> copyStrings(Collection<?> source) {
        List<String> result = new ArrayList<>();
        for (Object value : source) {
            if (!(value instanceof String text)) {
                String actualType = value == null
                        ? "null" : value.getClass().getSimpleName();
                throw new IllegalArgumentException(
                        "Expected String, found " + actualType);
            }
            result.add(text);
        }
        return result;
    }

    private static String unsafeVarargs(List<String>... groups) {
        Object[] exposed = groups;
        exposed[0] = List.of(123);
        return groups[0].get(0);
    }

    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        legacyAdd(names, 42);
        System.out.println("size after legacy write: " + names.size());

        try {
            String first = names.get(0);
            System.out.println("unexpected string: " + first);
        } catch (ClassCastException ex) {
            System.out.println("typed read: ClassCastException");
        }

        List<String> backing = new ArrayList<>();
        List<String> checked = Collections.checkedList(backing, String.class);

        try {
            legacyAdd(checked, 99);
        } catch (ClassCastException ex) {
            System.out.println("checked write: ClassCastException");
        }
        System.out.println("checked size after rejected write: "
                + checked.size());

        legacyAdd(backing, 99);
        System.out.println("backing bypass size: " + checked.size());
        try {
            String first = checked.get(0);
            System.out.println("unexpected checked string: " + first);
        } catch (ClassCastException ex) {
            System.out.println("bypass typed read: ClassCastException");
        }

        List<String> valid = copyStrings(List.of("An", "Binh"));
        System.out.println("validated copy: " + valid);

        Object candidate = List.of("An", 7);
        if (candidate instanceof List<?> unknown) {
            System.out.println("runtime shape is List: true");
            try {
                copyStrings(unknown);
            } catch (IllegalArgumentException ex) {
                System.out.println("validation: " + ex.getMessage());
            }
        }

        try {
            unsafeVarargs(List.of("safe"));
        } catch (ClassCastException ex) {
            System.out.println("varargs typed read: ClassCastException");
        }
    }
}
```

Biên dịch:

```shell
javac --release 17 -Xlint:rawtypes,unchecked CollectionTypePollutionDemo.java
```

Compiler phát cảnh báo về raw type, lời gọi không được kiểm chứng và generic varargs. Các cảnh báo này là phần cố ý của thí nghiệm. Câu chữ, đường dẫn và số dòng chẩn đoán có thể khác theo JDK.

Sau khi biên dịch thành công, chạy:

```shell
java CollectionTypePollutionDemo
```

Stdout chuẩn:

```text
size after legacy write: 1
typed read: ClassCastException
checked write: ClassCastException
checked size after rejected write: 0
backing bypass size: 1
bypass typed read: ClassCastException
validated copy: [An, Binh]
runtime shape is List: true
validation: Expected String, found Integer
varargs typed read: ClassCastException
```

### 12.1. Lỗi thứ nhất được phát hiện tại lúc đọc

`legacyAdd(names, 42)` nhận raw `List`, nên có thể thêm phần tử không phù hợp với lời hứa của biến `names`.

`size()` vẫn trả 1. Khi lấy phần tử để gán sang `String`, mới có `ClassCastException`.

### 12.2. Checked view đưa lỗi về lúc ghi

Lần ghi số qua `checked` bị từ chối. Kích thước vẫn 0 vì thao tác thêm đó không được chấp nhận.

Điều này giúp phát hiện nơi gây lỗi sớm hơn; nó không làm raw API trở thành một thiết kế được khuyến nghị.

### 12.3. Backing alias có thể đi vòng

Lần ghi thẳng qua `backing` không đi qua wrapper. `checked.size()` nhìn thấy phần tử mới vì view dùng cùng dữ liệu.

Khi caller đọc phần tử như chuỗi, tiếp tục có lỗi. Ví dụ minh họa giới hạn của wrapper nếu các đường ghi không được kiểm soát.

### 12.4. Kiểm tra từng phần tử tạo kết quả mới hợp lệ

`copyStrings` nhận dữ liệu qua `Collection<?>`, kiểm tra từng phần tử rồi xây `List<String>` mới.

Nguồn hợp lệ cho kết quả `[An, Binh]`. Nguồn có chuỗi và số bị từ chối bằng thông báo do ứng dụng chủ động tạo, thay vì để lỗi cast lộ muộn tại nơi sử dụng.

### 12.5. Varargs cho thấy nguồn lỗi khác raw List

Hàm cuối làm sai kiểu của một vị trí trong mảng varargs rồi đọc theo giả định `List<String>`. Nó minh họa vì sao cần xem xét cả mảng và đường alias, không chỉ tìm từ khóa raw type.

Các khối bắt ngoại lệ giúp chương trình chạy hết các thí nghiệm. Trong hệ thống thực tế, cách xử lý đúng là bảo vệ ranh giới dữ liệu và hợp đồng, không phải bắt `ClassCastException` sau mỗi lần đọc collection.

## 13. Làm việc với API cũ hoặc dữ liệu ngoài

### 13.1. Cô lập ranh giới chưa rõ kiểu

Nếu một thư viện trả raw collection, không truyền nó sâu vào nhiều tầng rồi cast ở từng nơi.

Nên có một ranh giới:

```text
API cu hoac du lieu chua ro kieu
        |
        v
kiem tra cau truc va phan tu
        |
        v
collection co kieu ro rang cua ung dung
```

Sau ranh giới, các phần còn lại dùng kiểu generic bình thường và không cần lặp lại cast.

### 13.2. Chọn hành vi với phần tử sai

Có thể:

- Từ chối toàn bộ dữ liệu và báo lỗi.
- Thu thập lỗi từng vị trí để người dùng sửa.
- Chuyển đổi một số dạng dữ liệu theo quy tắc rõ ràng.
- Bỏ phần tử không hợp lệ nếu đó thật sự là yêu cầu đã được xác định.

Không lặng lẽ đổi mọi đối tượng thành chuỗi bằng `toString()` chỉ để đáp ứng `List<String>`. Kết quả có thể không mang ý nghĩa dữ liệu mà ứng dụng cần.

### 13.3. Bảo toàn nguồn khi kiểm tra thất bại

Kiểm tra vào một kết quả mới giúp tránh sửa dữ liệu gốc trong quá trình xác minh.

Nếu nguồn hoặc phần tử có thể bị thay đổi đồng thời, cần tổ chức sở hữu, bản chụp hoặc đồng bộ phù hợp; vòng lặp kiểm tra không tự tạo giao dịch trên dữ liệu chia sẻ.

### 13.4. Đừng dùng generic để che kiểu miền chưa rõ

Nếu dữ liệu thực sự có nhiều loại phần tử hợp lệ, nên diễn đạt rõ bằng `List<Object>`, một kiểu chung có ý nghĩa hoặc mô hình dữ liệu phân biệt từng biến thể.

Gắn `List<String>` lên dữ liệu không thuần chuỗi rồi cast không làm mô hình trở nên chính xác hơn.

## 14. Trình tự chẩn đoán ClassCastException trong collection

Khi lỗi xảy ra ở `String name = names.get(i)` hoặc trong `for (String name : names)`, nên kiểm tra:

1. Phần tử thực tế tại vị trí lỗi có lớp gì?
2. Biến có kiểu nào tại thời điểm biên dịch?
3. Collection được tạo hoặc trả về từ đâu?
4. Có raw parameter, raw return type hoặc unchecked cast không?
5. Có alias khác đang ghi vào cùng đối tượng không?
6. Có mảng generic, varargs hoặc wrapper được dùng sai không?
7. Có dùng bản sao hoặc unmodifiable view như một phép xác minh kiểu không?
8. Điểm chuyển từ dữ liệu chưa rõ kiểu sang kiểu miền đã có kiểm tra thực sự chưa?
9. Nếu có suppress cảnh báo, bất biến được ghi ở đó còn đúng không?
10. Cần sửa nguồn hợp đồng hay chỉ sửa một chỗ đọc?

Không thêm cast ở dòng đang lỗi như phản xạ đầu tiên. Cast bổ sung không biến một `Integer` thành `String` và thường chỉ chuyển chỗ báo lỗi.

## 15. Bài tập tự kiểm chứng

### Bài 1: Chọn đúng kiểu khai báo

Có ba nhu cầu:

- Một danh sách chắc chắn gồm tên dạng chuỗi.
- Một danh sách có thể chứa nhiều loại giá trị hợp lệ.
- Một hàm chỉ cần xem kích thước và đọc từng phần tử chưa biết kiểu.

Chọn lần lượt `List<String>`, `List<Object>` và `List<?>`. Giải thích vì sao raw `List` không phải lựa chọn mặc định cho nhu cầu thứ ba.

### Bài 2: Dự đoán điểm phát hiện lỗi

Tạo `List<String>`, tạo raw alias, chèn `Integer`, sau đó:

- Gọi `size()`.
- Đọc qua raw alias vào biến `Object`.
- Đọc qua biến kiểu tham số vào biến `String`.

Dự đoán thao tác nào cần kiểm tra như chuỗi và giải thích vì sao lỗi không xuất hiện ngay ở mọi thao tác.

### Bài 3: Kiểm tra nguồn hỗn hợp

Viết hàm tạo `List<String>` mới từ `Collection<?>` theo chính sách:

- Chỉ chấp nhận chuỗi không null.
- Nếu gặp phần tử sai, báo vị trí cùng kiểu thực tế.
- Không sửa nguồn.
- Không dùng unchecked cast.

Kiểm tra nguồn rỗng, toàn chuỗi, có số ở cuối và có `null`.

### Bài 4: Chứng minh giới hạn của checkedList

Tạo list nền và checked view. Thử ghi sai kiểu qua view, rồi thử qua raw alias của list nền.

Giải thích vì sao chỉ lời gọi qua view được bảo vệ và vì sao cần kiểm soát quyền sở hữu backing collection.

### Bài 5: Kiểm tra generic lồng

Nhận một `Object` được kỳ vọng là danh sách các danh sách chuỗi. Viết kiểm tra từng tầng và tạo cấu trúc kết quả mới.

Không dùng `List.class` như bằng chứng đủ cho toàn bộ `List<List<String>>`.

### Bài 6: Rà soát một suppress cảnh báo

Tìm một đoạn giả định cần `@SuppressWarnings("unchecked")`. Ghi rõ:

- Compiler không kiểm chứng được điều gì?
- Bằng chứng nào từ chương trình làm phép chuyển đổi an toàn?
- Ai còn có thể ghi vào dữ liệu?
- Cần kiểm thử gì nếu bất biến bị phá vỡ?

Nếu không trả lời được, chưa có cơ sở để coi việc tắt cảnh báo là xử lý xong.

## 16. Những kết luận cần tránh

- “Raw List chính là `List<Object>` viết ngắn.”
- “List<?> nghĩa là không được thay đổi bất kỳ điều gì.”
- “Danh sách có nhiều lớp phần tử thì luôn là heap pollution.”
- “Cast sang `List<String>` sẽ kiểm tra từng phần tử.”
- “Không viết cast tại get thì không thể có ClassCastException.”
- “Bọc unmodifiable hoặc copyOf là làm sạch dữ liệu sai kiểu.”
- “checkedList bảo vệ cả những đường ghi đi thẳng vào backing list.”
- “SafeVarargs hoặc SuppressWarnings làm thân hàm tự an toàn.”
- “Không có cảnh báo ở caller thì nguồn thư viện chắc chắn đúng kiểu.”
- “Thêm một cast nữa là cách sửa giá trị có kiểu không phù hợp.”

Mục tiêu là giữ lời hứa về kiểu phần tử có căn cứ: dùng generics xuyên suốt nơi có thể, đặt kiểm tra rõ ràng ở ranh giới chưa rõ kiểu, kiểm soát các đường ghi và coi cảnh báo chưa được giải thích là một phần công việc còn phải phân tích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="element-types-007">Thiết kế phương thức nhận collection linh hoạt nhưng an toàn kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế phương thức nhận collection linh hoạt nhưng an toàn kiểu

## 1. “Linh hoạt” cần phục vụ đúng hợp đồng

Một phương thức nhận collection tốt cho phép người gọi truyền các cấu trúc và kiểu phần tử phù hợp mà không phải ép kiểu hoặc tạo bản sao vô ích. Đồng thời, chữ ký phải giúp trình biên dịch chặn những cách sử dụng có thể đưa phần tử sai kiểu vào dữ liệu.

Ví dụ, hàm tính tổng chỉ cần đọc các số. Nhận `ArrayList<Integer>` sẽ ràng buộc cả lớp cài đặt lẫn kiểu số quá hẹp. Nhận raw `Collection` rồi ép từng phần tử lại làm mất bảo đảm kiểu.

Một lựa chọn phù hợp hơn là:

```java
// Phác thảo chữ ký, không phải chương trình độc lập.
static double sum(Iterable<? extends Number> source)
```

Chữ ký nói:

- Thuật toán chỉ cần duyệt nguồn.
- Mỗi phần tử dùng được như `Number`.
- Có thể nhận nguồn của `Integer`, `Double` hoặc `Number`.
- Kết quả có kiểu cố định `double`.

Tuy vậy, chữ ký chưa nói đầy đủ về `null`, quyền sửa nguồn, độ chính xác số học hay truy cập nhiều luồng. Thiết kế phương thức cần kết hợp **quan hệ kiểu**, **hành vi quan sát được** và **quyền sở hữu dữ liệu**.

Bài dùng Java 17, tập trung vào các mẫu thiết kế có thể áp dụng trực tiếp cho collection và map. Cần biết generics, quan hệ kiểu cha–con và các giao diện collection cơ bản.

## 2. Xác định thao tác trước khi chọn tham số

Đừng bắt đầu bằng việc thêm wildcard vào một chữ ký bất kỳ. Trước hết, viết rõ phương thức sẽ làm gì.

| Nhu cầu thực tế | Kiểu tham số nên cân nhắc | Lý do |
| --- | --- | --- |
| Chỉ duyệt phần tử | `Iterable<? extends T>` hoặc `Iterable<?>` | Không đòi hỏi `size`, chỉ số hay khả năng thêm |
| Cần `size`, `contains` hoặc dùng constructor sao chép collection | `Collection<? extends T>` | Đủ API nhóm phần tử |
| Cần truy cập và thay phần tử theo vị trí | `List<T>` | Vị trí là một phần hợp đồng |
| Cần dữ liệu có tính không trùng | `Set<T>` hoặc dạng wildcard phù hợp | Diễn đạt mô hình tập hợp |
| Nhận các phần tử để thêm vào một đích | `Collection<? super T>` | Đích giữ được `T` |
| Tra cứu theo khóa | `Map<K, V>` hoặc wildcard theo vai trò | Cần quan hệ khóa–giá trị |

Nguyên tắc là chọn giao diện **đủ diễn đạt ý nghĩa và thao tác**, không chỉ giao diện có ít phương thức nhất.

Ví dụ, nếu thứ tự danh sách là yêu cầu nghiệp vụ, đổi tham số thành `Collection` rồi ngầm dựa vào chỉ số hoặc thứ tự không được cam kết là làm yếu hợp đồng. Ngược lại, nếu chỉ tính tổng bằng vòng lặp, yêu cầu riêng `ArrayList` không mang lại giá trị.

Nguồn API nền tảng: [Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html), [List](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html) và [Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

## 3. Phân vai: nguồn, đích và dữ liệu đọc–ghi

Với mỗi tham số, hỏi:

1. Phương thức chỉ lấy phần tử từ đây?
2. Phương thức đưa phần tử vào đây?
3. Phương thức cần đọc rồi ghi lại cùng kiểu phần tử?
4. Kiểu đó có cần liên kết với kết quả hoặc tham số khác không?

Từ đó chọn:

| Vai trò | Mẫu chữ ký | Điều đã biết |
| --- | --- | --- |
| Quan sát, không cần API riêng của phần tử | `Collection<?>` | Đọc thành `Object` |
| Nguồn cung cấp phần tử dùng được như `T` | `Collection<? extends T>` | Đọc thành `T` |
| Đích nhận phần tử `T` | `Collection<? super T>` | Ghi `T`; đọc tổng quát thành `Object` |
| Cần giữ cùng kiểu phần tử khi đọc–ghi | `Collection<T>` hoặc `List<T>` | Tên `T` liên kết các vị trí kiểu |
| Kết quả giữ quan hệ với nguồn | Phương thức generic `<T>` | Có thể biểu đạt kết quả theo `T` |

Quy tắc nhớ PECS — nguồn cung cấp phần tử dùng `extends`, đích nhận phần tử dùng `super` — hữu ích ở bước này. Nó phải được hiểu theo luồng dữ liệu trong thuật toán.

`extends` không tạo bất biến; `super` cũng không cấp khả năng sửa cho một đối tượng vốn không hỗ trợ thêm. [Guidelines for Wildcard Use](https://docs.oracle.com/javase/tutorial/java/generics/wildcardGuidelines.html)

## 4. Nguồn đọc: dùng giới hạn trên đúng nhu cầu

```java
// Đoạn khai báo phương thức; cần import java.util.Objects.
static double sum(Iterable<? extends Number> source) {
    Objects.requireNonNull(source, "source");

    double total = 0;
    for (Number value : source) {
        total += Objects.requireNonNull(value, "element").doubleValue();
    }
    return total;
}
```

Các đầu vào phù hợp:

```java
// Đoạn minh họa trong một phương thức, với sum đã khai báo.
// Cần import java.util.*.
List<Integer> integers = List.of(1, 2, 3);
List<Double> decimals = List.of(0.5, 1.5);

System.out.println(sum(integers)); // 6.0
System.out.println(sum(decimals)); // 2.0
```

Vì sao không dùng `Iterable<Number>`? Generics có tính bất biến: `Iterable<Integer>` không tự thành `Iterable<Number>`. Wildcard giới hạn trên mở đúng phạm vi đầu vào mà vẫn bảo đảm đọc mỗi phần tử như `Number`. [Upper Bounded Wildcards](https://docs.oracle.com/javase/tutorial/java/generics/upperBounded.html)

Hợp đồng cần ghi thêm:

- Nguồn và các phần tử khác `null`.
- Nguồn rỗng trả `0.0`.
- Phương thức không chủ động sửa nguồn.
- Tính toán bằng `double`, không bảo đảm số thập phân chính xác cho tiền tệ.
- Nguồn phải được duyệt ổn định theo chính sách đồng thời của ứng dụng.

`Objects.requireNonNull` giúp xác định điều kiện đầu vào và vị trí lỗi rõ ràng. Nó không kiểm tra tất cả quy tắc nghiệp vụ của phần tử. [Objects API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html#requireNonNull(T,java.lang.String))

## 5. Đích nhận dữ liệu: dùng giới hạn dưới

Nếu phương thức tạo hoặc cung cấp các `Integer` để thêm vào đích, đích có thể là collection của `Integer`, `Number` hoặc `Object`.

```java
// Đoạn khai báo phương thức; cần import java.util.Collection.
static void addDefaults(Collection<? super Integer> destination) {
    destination.add(10);
    destination.add(20);
}
```

`Collection<Integer>` trong chữ ký sẽ hạn chế người gọi không cần thiết. `Collection<Object>` cũng không nhận được `Collection<Integer>` vì invariance. `? super Integer` biểu đạt đúng “một đích có thể giữ Integer”.

Tuy vậy, qua biến `destination` chỉ có thể đọc tổng quát như `Object`: một đích `Collection<Number>` có thể đã chứa `Double`.

Hợp đồng của phương thức còn yêu cầu:

- Đích tồn tại.
- Đích hỗ trợ `add`.
- Giá trị thêm đáp ứng các ràng buộc của đích.
- Người gọi cho phép sửa chính đối tượng được truyền vào.

Một `List.of(...)` có thể phù hợp về kiểu nhưng không phù hợp về thao tác. Không thể giải quyết điều đó bằng wildcard khác. [Lower Bounded Wildcards](https://docs.oracle.com/javase/tutorial/java/generics/lowerBounded.html)

## 6. Liên kết nguồn và đích bằng cùng một T

Khi truyền dữ liệu từ một collection sang collection khác, cần một tham số kiểu đặt tên cho mối quan hệ:

```java
// Phác thảo chữ ký; cần import java.util.Collection.
static <T> void addFrom(
        Collection<? super T> destination,
        Collection<? extends T> source)
```

Đọc chữ ký:

1. Nguồn cung cấp phần tử thuộc `T` hoặc kiểu con.
2. Mỗi phần tử đọc ra có thể dùng như `T`.
3. Đích nhận được `T` vì kiểu phần tử của đích là `T` hoặc kiểu cha phù hợp.

Ví dụ:

```java
// Đoạn minh họa trong một phương thức, với addFrom đã khai báo.
// Cần import java.util.*.
List<Integer> source = List.of(1, 2, 3);
List<Number> destination = new ArrayList<>();

addFrom(destination, source);
```

Các phần tử `Integer` được đưa vào đích chứa `Number` mà không ép kiểu.

Không thể dùng phương thức này để thêm các `Double` vào một `List<Integer>`. Compiler không tìm được `T` thỏa mãn cả nguồn Double lẫn đích Integer; đó là một lời gọi cần bị từ chối.

Không dùng hai wildcard không liên quan rồi giả định chúng cùng một kiểu. Cùng có giới hạn `Number` không bảo đảm một nguồn Integer và một đích Double tương thích.

Các generic method được mô tả trong [Generic Methods](https://docs.oracle.com/javase/tutorial/java/generics/methods.html).

### 6.1. Chọn chính sách alias trước khi cài đặt

Nguồn và đích có thể là cùng một đối tượng hoặc các view dùng chung dữ liệu. Một vòng lặp vừa duyệt nguồn vừa thêm vào đích có thể làm iterator mất hiệu lực.

Trong bài này, `addFrom` tạo snapshot cấu trúc nguồn trước khi sửa đích:

```java
// Đoạn khai báo phương thức; cần import java.util.*.
static <T> void addFrom(
        Collection<? super T> destination,
        Collection<? extends T> source) {

    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(source, "source");

    List<T> snapshot = new ArrayList<>(source);
    for (T value : snapshot) {
        destination.add(value);
    }
}
```

Với một danh sách sửa được, `addFrom(list, list)` sẽ thêm một lượt các phần tử ban đầu, thay vì duyệt một nguồn đang lớn lên.

Đây là quyết định của API mẫu:

- Có chi phí bộ nhớ tỷ lệ với kích thước nguồn.
- Snapshot là bản sao nông, vẫn giữ tham chiếu tới phần tử.
- Nó không biến việc đọc nguồn bị sửa đồng thời thành an toàn.
- Nó không làm toàn bộ quá trình thêm thành giao dịch.
- Đích `Set` vẫn áp dụng quy tắc không giữ trùng của chính nó.
- Đích có thể từ chối `null` hoặc một giá trị theo hợp đồng riêng.

Tên `addFrom` dùng theo nghĩa yêu cầu đích tiếp nhận phần tử. Nó không hứa kích thước đích tăng đúng bằng kích thước nguồn trong mọi cấu trúc.

## 7. Khi cần đọc và ghi cùng kiểu: giữ T

Một thao tác đổi chỗ hai phần tử cần đọc ra rồi ghi lại chính kiểu phần tử của danh sách:

```java
// Đoạn khai báo phương thức; cần import java.util.*.
static <T> void swap(List<T> values, int left, int right) {
    Objects.requireNonNull(values, "values");

    T leftValue = values.get(left);
    T rightValue = values.get(right);
    values.set(left, rightValue);
    values.set(right, leftValue);
}
```

Hai giá trị `T` được đọc từ cùng một `List<T>` và ghi lại vào cùng danh sách. Không cần biết `T` là chuỗi, số hay một kiểu nghiệp vụ.

Hợp đồng:

- Hai chỉ số phải hợp lệ.
- Danh sách phải hỗ trợ `set`.
- Phương thức sửa nguồn.
- Không hứa nguyên tử nếu việc thay phần tử bị lỗi giữa chừng.
- Không hứa cùng chi phí với mọi cài đặt danh sách.

Đọc cả hai phần tử trước khi `set` giúp một chỉ số ngoài phạm vi bị phát hiện trước thay đổi trong cài đặt minh họa. Nó không phải bảo đảm rollback cho mọi ngoại lệ khác.

Danh sách cố định kích thước có thể hỗ trợ `set`; danh sách không cho sửa thì không. Kiểu `List<T>` không mã hóa đầy đủ khác biệt đó.

Nếu API công khai nhận wildcard nhưng thao tác chỉ sắp xếp lại phần tử hiện có, helper capture có thể giúp giữ kiểu an toàn. Không cần dùng raw type để giải quyết.

## 8. Giữ quan hệ giữa nguồn và kết quả

### 8.1. Trả một bản sao có kiểu sử dụng được

```java
// Đoạn khai báo phương thức; cần import java.util.*.
static <T> List<T> editableCopy(Collection<? extends T> source) {
    Objects.requireNonNull(source, "source");
    return new ArrayList<>(source);
}
```

Với kiểu đích phù hợp:

```java
// Đoạn minh họa trong một phương thức, với editableCopy đã khai báo.
List<Integer> source = List.of(1, 2, 3);
List<Number> result = editableCopy(source);
result.add(2.5);
```

`T` liên kết kiểu kết quả với tập kiểu mà nguồn cung cấp được. Ngữ cảnh đích cho phép suy luận `T` là `Number` trong ví dụ này.

Kết quả có hợp đồng rõ:

- Một danh sách mới sửa được.
- Giữ thứ tự duyệt của nguồn.
- Thay cấu trúc kết quả không sửa cấu trúc nguồn.
- Các đối tượng phần tử vẫn được chia sẻ.
- `null` được giữ nếu nguồn chứa và constructor nhận được nó.
- Không cam kết giữ comparator hoặc mọi đặc tính của cấu trúc nguồn.

Constructor của `ArrayList` nhận `Collection<? extends E>` chính là một ví dụ thực tế về nguồn linh hoạt. [ArrayList API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

### 8.2. Tránh trả wildcard không cần thiết

Trả `List<? extends T>` có thể làm người gọi không thể thêm một giá trị `T` mới dù API định tạo danh sách sửa được. Nếu không có lý do bảo toàn một kiểu con ẩn, trả `List<T>` thường truyền đạt kết quả hữu ích hơn.

Nếu muốn kết quả không cho sửa, chọn cơ chế cụ thể như `List.copyOf` và công bố chính sách `null` cùng mức sao chép. Không dùng wildcard để thay thế cam kết không cho sửa.

Tính linh hoạt ở đầu vào và độ rõ ở đầu ra là hai quyết định liên quan nhưng khác nhau.

## 9. Callback cũng có phía đọc và phía tạo kết quả

Một phương thức chuyển từng phần tử thành giá trị mới có thể dùng:

```java
// Đoạn khai báo phương thức; cần import java.util.* và java.util.function.Function.
static <T, R> List<R> mapToList(
        Iterable<? extends T> source,
        Function<? super T, ? extends R> mapper) {

    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(mapper, "mapper");

    List<R> result = new ArrayList<>();
    for (T value : source) {
        result.add(mapper.apply(value));
    }
    return result;
}
```

Đọc chữ ký `Function<? super T, ? extends R>` theo luồng dữ liệu:

- Callback **nhận** một `T`, nên callback biết xử lý kiểu cha của `T` cũng dùng được.
- Callback **trả** kết quả dùng như `R`, nên trả một kiểu con của `R` cũng phù hợp.

Ví dụ:

```java
// Đoạn minh họa với mapToList đã khai báo.
// Cần import java.util.* và java.util.function.Function.
List<Integer> source = List.of(1, 2, 3);
Function<Number, String> label = number -> "n=" + number;

List<CharSequence> labels = mapToList(source, label);
```

Một hàm nhận `Number` xử lý được các `Integer` từ nguồn. Các `String` mà nó trả dùng được như `CharSequence`.

Hợp đồng callback còn cần nêu:

- Nguồn và callback khác `null`.
- Trong ví dụ, phần tử `null` được truyền cho callback; callback quyết định có xử lý được không.
- Kết quả `null` của callback được `ArrayList` chấp nhận.
- Kết quả giữ thứ tự duyệt của nguồn, không tự tạo bảo đảm thứ tự mà nguồn không có.
- Callback không được làm nguồn mất điều kiện duyệt an toàn.
- Ngoại lệ từ callback được truyền ra; các tác động bên ngoài do callback gây ra không tự hoàn tác.

Đây là mẫu chuyển đổi đơn giản để học chữ ký. Nếu API nghiệp vụ cần cấm `null` hoặc cấm tác dụng phụ, hãy bổ sung quy tắc và kiểm tra phù hợp. [Function API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/Function.html)

## 10. Áp dụng độc lập cho khóa và giá trị của Map

Một phương thức tạo bản sao map có thể mở rộng cả hai phía:

```java
// Đoạn khai báo phương thức; cần import java.util.*.
static <K, V> Map<K, V> editableMapCopy(
        Map<? extends K, ? extends V> source) {

    Objects.requireNonNull(source, "source");
    return new LinkedHashMap<>(source);
}
```

Ví dụ:

```java
// Đoạn minh họa với editableMapCopy đã khai báo; cần import java.util.*.
Map<String, Integer> source = new LinkedHashMap<>();
source.put("A", 1);

Map<CharSequence, Number> copy = editableMapCopy(source);
copy.put("B", 2.5);
```

Dữ liệu nguồn có khóa `String` dùng được như `CharSequence` và giá trị `Integer` dùng được như `Number`. Bản sao có kiểu riêng, nên thêm giá trị `Double` vào bản sao không làm hỏng hợp đồng của map nguồn.

Cần công bố:

- Tạo map mới theo thứ tự duyệt của nguồn.
- Không sao chép sâu khóa hoặc giá trị.
- Không giữ mọi đặc tính đặc biệt của nguồn, ví dụ comparator của sorted map.
- Số lượng và thứ tự đầu ra tuân theo hợp đồng nguồn/cài đặt được chọn.
- Không làm an toàn việc giữ khóa có trạng thái thay đổi ảnh hưởng `equals/hashCode`.

Nếu API chỉ đọc giá trị, có thể chỉ mở phía `V` bằng `Map<String, ? extends Number>`. Không cần mở rộng cả khóa khi nghiệp vụ thật sự yêu cầu `String`.

Cơ chế giữ thứ tự và constructor sao chép được mô tả trong [LinkedHashMap API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

## 11. An toàn kiểu là một phần của hợp đồng

### 11.1. Khả năng sửa và quy tắc phần tử

Một phương thức nhận `Collection<? super T>` vẫn có thể gặp `UnsupportedOperationException` từ một đích không cho sửa.

Một đích khác có thể nhận đúng kiểu generic nhưng cấm `null`, yêu cầu so sánh được hoặc giới hạn giá trị. Không coi “biên dịch được” là bằng chứng mọi dữ liệu đầu vào đều được chấp nhận.

### 11.2. Sở hữu, view và alias

Phương thức cần trả lời:

- Nó sửa đối số hay tạo kết quả mới?
- Nó giữ lại tham chiếu sau khi kết thúc không?
- View có thể phản ánh thay đổi về sau không?
- Được truyền cùng một đối tượng vào hai tham số không?
- Có tạo bản sao cấu trúc trước khi sửa không?

Một chữ ký generic đẹp không thay thế các câu trả lời này.

### 11.3. Ngoại lệ và thay đổi dở dang

Với `addFrom`, một số phần tử có thể đã được thêm trước khi đích từ chối một phần tử tiếp theo. Snapshot nguồn không tạo giao dịch cho đích.

Nếu API đòi “thành công toàn bộ hoặc không thay đổi”, cần thiết kế cơ chế phù hợp: ví dụ xây kết quả mới rồi công bố nó, hoặc dùng khả năng giao dịch do cấu trúc/hệ thống đích cung cấp. Không hứa rollback chung cho một `Collection` bất kỳ.

### 11.4. Đồng thời và hiệu năng

Một phương thức nhận giao diện rộng vẫn phải có giả định rõ:

- Dữ liệu có thể bị sửa đồng thời không?
- Iterator có đặc tính gì?
- Số lần duyệt và bộ nhớ phụ là bao nhiêu?
- Truy cập chỉ số có đang ngầm giả định chi phí hằng số không?

Wildcard không đồng bộ hóa, không chụp snapshot nguyên tử và không ngăn callback sửa dữ liệu.

## 12. Đọc chữ ký JDK để học cách thiết kế

Hai chữ ký rút gọn minh họa:

```java
// Chữ ký API để phân tích, không phải chương trình độc lập.
boolean addAll(Collection<? extends E> source);

static <T> void copy(
        List<? super T> destination,
        List<? extends T> source);
```

- `Collection.addAll` nhận các phần tử từ một nguồn mà mỗi phần tử dùng được như `E`.
- `Collections.copy` liên kết hai danh sách bằng `T`, nguồn `extends` và đích `super`.

Nhưng `Collections.copy` không phải “thêm cuối”: nó thay các vị trí đã tồn tại ở đích, và kích thước đích phải đủ. Một `ArrayList` có capacity lớn nhưng `size()` bằng `0` vẫn chưa có các vị trí để thay.

Điều này cho thấy chữ ký generic cần được đọc cùng **hành vi của phương thức**, không chỉ nhận ra mẫu PECS rồi đoán thuật toán. [Collections.copy API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#copy(java.util.List,java.util.List))

## 13. Chương trình hoàn chỉnh

Lưu thành `FlexibleCollectionApiDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class FlexibleCollectionApiDemo {
    private static double sum(Iterable<? extends Number> source) {
        Objects.requireNonNull(source, "source");
        double total = 0;
        for (Number value : source) {
            total += Objects.requireNonNull(value, "element").doubleValue();
        }
        return total;
    }

    private static <T> List<T> editableCopy(
            Collection<? extends T> source) {
        Objects.requireNonNull(source, "source");
        return new ArrayList<>(source);
    }

    private static <T> void addFrom(
            Collection<? super T> destination,
            Collection<? extends T> source) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(source, "source");
        List<T> snapshot = new ArrayList<>(source);
        for (T value : snapshot) {
            destination.add(value);
        }
    }

    private static <T> void swap(List<T> values, int left, int right) {
        Objects.requireNonNull(values, "values");
        T leftValue = values.get(left);
        T rightValue = values.get(right);
        values.set(left, rightValue);
        values.set(right, leftValue);
    }

    private static <T, R> List<R> mapToList(
            Iterable<? extends T> source,
            Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mapper, "mapper");
        List<R> result = new ArrayList<>();
        for (T value : source) {
            result.add(mapper.apply(value));
        }
        return result;
    }

    private static <K, V> Map<K, V> editableMapCopy(
            Map<? extends K, ? extends V> source) {
        Objects.requireNonNull(source, "source");
        return new LinkedHashMap<>(source);
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(1, 2, 3);
        List<Double> decimals = List.of(0.5, 1.5);
        System.out.println("Integer sum: " + sum(integers));
        System.out.println("Double sum: " + sum(decimals));

        List<Number> copy = editableCopy(integers);
        copy.add(2.5);
        System.out.println("Editable copy: " + copy);
        System.out.println("Source unchanged: " + integers);

        List<Number> destination = new ArrayList<>();
        addFrom(destination, integers);
        addFrom(destination, decimals);
        System.out.println("Mixed destination: " + destination);

        List<String> same = new ArrayList<>(List.of("a", "b"));
        addFrom(same, same);
        System.out.println("Same source and destination: " + same);

        List<Integer> pair = new ArrayList<>(List.of(10, 20));
        swap(pair, 0, 1);
        System.out.println("Swap: " + pair);

        Function<Number, String> label = number -> "n=" + number;
        List<CharSequence> labels = mapToList(integers, label);
        System.out.println("Mapped: " + labels);

        Map<String, Integer> originalMap = new LinkedHashMap<>();
        originalMap.put("A", 1);
        originalMap.put("B", 2);
        Map<CharSequence, Number> mapCopy = editableMapCopy(originalMap);
        mapCopy.put("C", 2.5);
        System.out.println("Map copy: " + mapCopy);
        System.out.println("Original map: " + originalMap);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:unchecked FlexibleCollectionApiDemo.java
java FlexibleCollectionApiDemo
```

Mã đã được kiểm tra theo nền API Java 17 trên JDK mới hơn, không có cảnh báo unchecked.

Stdout:

```text
Integer sum: 6.0
Double sum: 2.0
Editable copy: [1, 2, 3, 2.5]
Source unchanged: [1, 2, 3]
Mixed destination: [1, 2, 3, 0.5, 1.5]
Same source and destination: [a, b, a, b]
Swap: [20, 10]
Mapped: [n=1, n=2, n=3]
Map copy: {A=1, B=2, C=2.5}
Original map: {A=1, B=2}
```

### Giải thích các lời gọi

- `sum` nhận hai kiểu số khác nhau qua nguồn `Iterable<? extends Number>`.
- `editableCopy` tạo `List<Number>` mới từ `List<Integer>`; thêm `2.5` chỉ đổi bản sao.
- `addFrom` nhận đích Number và lần lượt nguồn Integer, Double.
- Snapshot nguồn làm lời gọi nguồn=đích hoàn tất với đúng một lượt dữ liệu ban đầu trong ví dụ danh sách.
- `swap` giữ cùng kiểu `T` khi đọc và ghi vị trí.
- `mapToList` nhận callback `Number -> String`, trong khi nguồn là Integer và kết quả được dùng như danh sách CharSequence.
- `editableMapCopy` mở kiểu khóa/giá trị ở bản sao mà không gán map nguồn sang một kiểu generic không tương thích.

Không có lời gọi raw type hoặc ép collection sang kiểu rộng hơn để vượt kiểm tra của compiler.

## 14. Quy trình thiết kế một phương thức mới

1. **Viết hành vi bằng ngôn ngữ thông thường.** Cần đọc, thêm, thay vị trí, lọc hay tạo bản sao?
2. **Xác định giao diện đủ dùng.** Đừng nhận lớp cụ thể chỉ vì đang dùng nó ở một nơi gọi.
3. **Gán vai trò cho từng tham số.** Nguồn, đích hay đọc–ghi cùng kiểu?
4. **Đặt tên tham số kiểu khi có quan hệ.** Dùng `T`, `K/V` hoặc `T/R` để liên kết các vị trí cần liên kết.
5. **Dùng wildcard đúng phía.** `extends` cho nguồn; `super` cho đích nhận.
6. **Chọn kết quả rõ ràng.** Tài liệu hóa sửa được, không cho sửa, view hay bản sao.
7. **Công bố điều kiện ngoài kiểu.** `null`, alias, optional operation, thứ tự, ngoại lệ, đồng thời.
8. **Kiểm tra lời gọi hợp lệ và lời gọi phải bị chặn.** Không sửa chữ ký bằng raw type chỉ để hết lỗi.
9. **Đọc lại mức tổng quát.** Bỏ tham số kiểu hoặc bound không phục vụ hành vi; giữ các ràng buộc nghiệp vụ có ý nghĩa.

Các quy tắc về parameterized type và wildcard nằm trong [JLS Chapter 4](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html).

## 15. Lỗi phổ biến

| Cách thiết kế | Vấn đề | Hướng sửa |
| --- | --- | --- |
| Nhận `ArrayList<T>` dù chỉ duyệt | Ràng buộc lớp cài đặt | Chọn `Iterable` hoặc `Collection` đủ nhu cầu |
| Nhận `Collection<Number>` để đọc mọi nhóm số | Chặn nguồn Integer/Double theo invariance | Dùng `? extends Number` |
| Nhận `Collection<Object>` để chứa đầu vào bất kỳ | Không nhận mọi collection generic | Dùng wildcard hoặc tham số kiểu theo vai trò |
| Dùng raw `Collection` rồi cast từng phần tử | Đẩy lỗi sang lúc chạy | Giữ quan hệ generic xuyên suốt |
| Đích thêm `T` lại khai báo `? extends T` | Không chứng minh được kiểu phần tử cần ghi | Xem lại vai trò đích và dùng `super` |
| Hai tham số wildcard nhưng không có T liên kết | Không bảo đảm truyền phần tử giữa chúng an toàn | Dùng generic method có quan hệ nguồn–đích |
| Trả wildcard dù tạo kết quả có kiểu rõ | Làm người gọi khó sử dụng không cần thiết | Trả `List<T>` hoặc `Map<K,V>` thích hợp |
| Coi wildcard là bất biến | Vẫn có thao tác sửa hoặc alias | Chọn cơ chế sở hữu và hạn chế sửa riêng |
| Duyệt nguồn rồi thêm vào chính nguồn | Có thể làm iterator mất hiệu lực | Quy định cấm alias hoặc snapshot có chủ đích |
| Hứa sao chép nguyên tử cho mọi collection | Đích có thể lỗi sau một số lần thêm | Công bố trạng thái khi lỗi hoặc thiết kế giao dịch riêng |
| Bỏ qua callback side effect | Nguồn có thể bị sửa giữa lúc duyệt | Nêu quy tắc callback và chọn cách duyệt phù hợp |

Raw type chủ yếu phục vụ tương thích mã cũ; không phải công cụ làm API mới tổng quát hơn. [Raw Types](https://docs.oracle.com/javase/tutorial/java/generics/rawTypes.html)

## 16. Tự kiểm tra và thực hành

### Câu hỏi

1. Vì sao một hàm chỉ duyệt không cần nhận `ArrayList`?
2. Vì sao `? extends T` thích hợp cho nguồn cung cấp phần tử?
3. Đích `? super Integer` có thể là `Collection<Number>` không?
4. Tại sao cùng một `T` cần xuất hiện ở cả nguồn lẫn đích của `addFrom`?
5. Snapshot trong `addFrom` giải quyết vấn đề nào và chưa giải quyết vấn đề nào?
6. `List<T>` có bảo đảm hỗ trợ `set` không?
7. Vì sao `Function<? super T, ? extends R>` linh hoạt hơn `Function<T,R>` ở phương thức chuyển đổi?
8. Trả `List<? extends T>` có mặc nhiên làm kết quả không cho sửa không?
9. `Collections.copy` có làm tăng kích thước đích không?
10. Các điều kiện nào cần tài liệu hóa bên cạnh quan hệ generic?

### Bài tập

- Viết hàm chỉ đọc độ dài các `CharSequence`, cho phép nhận danh sách `String` và `StringBuilder`.
- Viết hàm thêm các số nguyên vào `Collection<? super Integer>`; thử đích Integer, Number và Object.
- Thử một lời gọi thêm nguồn Double vào đích Integer bằng `addFrom` và giải thích lỗi biên dịch.
- Cho nguồn và đích là cùng một `ArrayList`, đối chiếu kết quả với chính sách snapshot.
- Thử `swap` với danh sách hỗ trợ `set` và danh sách không cho sửa; phân biệt lỗi kiểu với lỗi hợp đồng lúc chạy.
- Viết Javadoc cho `editableCopy`, nêu rõ tính sửa được, bản sao nông, thứ tự và `null`.
- Dùng `mapToList` để chuyển số nguyên sang một kiểu kết quả được dùng qua giao diện rộng hơn.
- Viết một kiểm tra rằng thêm phần tử vào map sao chép không sửa map nguồn.

### Gợi ý đối chiếu

1. Nó không dùng năng lực riêng của lớp đó, nên có thể nhận giao diện đủ để duyệt.
2. Mỗi phần tử đọc ra dùng được như `T` mà không cần biết kiểu con chính xác.
3. Có; Number giữ được Integer.
4. `T` là cầu nối chứng minh phần tử nguồn được đích chấp nhận.
5. Nó tránh vừa duyệt vừa thay đổi nguồn do alias trong cách cài đặt mẫu; không tạo thread safety, bản sao sâu hoặc giao dịch.
6. Không; đối tượng vẫn có thể từ chối thao tác sửa.
7. Callback có thể nhận kiểu cha của đầu vào và trả kiểu con của kết quả.
8. Không; wildcard không phải cơ chế bất biến.
9. Không; đích phải có sẵn đủ vị trí.
10. Quyền sửa, `null`, alias, thứ tự, quyền sở hữu, trạng thái khi lỗi, callback và đồng thời.


</details>

- [Quay lại đầu trang](#back-to-top)
