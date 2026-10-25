<a id="back-to-top"></a>

# Comparator

## Menu
- [Comparator&lt;T&gt; định nghĩa thứ tự bên ngoài lớp](#comparators-001)
- [Hợp đồng dấu, bắc cầu và các nhóm có kết quả so sánh bằng 0](#comparators-002)
- [comparing() và các biến thể comparingInt(), comparingLong(), comparingDouble()](#comparators-003)
- [thenComparing() để tạo thứ tự theo nhiều khóa](#comparators-004)
- [reversed() và vị trí áp dụng trong chuỗi so sánh](#comparators-005)
- [nullsFirst() và nullsLast()](#comparators-006)
- [Chọn quy tắc so sánh chuỗi theo yêu cầu nghiệp vụ](#comparators-007)
- [Phân biệt Comparator dùng cho hiển thị với quan hệ bằng nhau của đối tượng](#comparators-008)
- [Trạng thái thay đổi trong Comparator làm kết quả sắp xếp không ổn định](#comparators-009)
- [Comparator.equals() khác với việc compare() trả về 0 cho hai phần tử](#comparators-010)

## <a id="comparators-001">Comparator&lt;T&gt; định nghĩa thứ tự bên ngoài lớp</a>

<details>
<summary>Xem chi tiết</summary>

# `Comparator<T>`: định nghĩa thứ tự bên ngoài lớp

## 1. Vì sao cần một thứ tự từ bên ngoài?

Trong Java, có những lớp mà đối tượng có thể được sắp xếp theo **nhiều tiêu chí hợp lệ**. Ví dụ, một nhân viên có mã định danh, tên và tuổi. Danh sách nhân viên có thể cần hiển thị theo tuổi tăng dần, theo tên theo bảng chữ cái hoặc theo mã nhân viên. Không có lý do nào buộc một trong các tiêu chí phải được mã hóa cố định vào chính lớp `Employee`.

`java.util.Comparator<T>` giải quyết nhu cầu đó: nó mô tả **một chiến lược so sánh hai đối tượng kiểu `T` ở bên ngoài lớp dữ liệu**. Ta có thể tạo nhiều comparator, chọn comparator tại nơi cần sử dụng và không phải thay đổi mã nguồn của `Employee`.

```java
Comparator<Employee> byAge =
        Comparator.comparingInt(Employee::age);

Comparator<Employee> byName =
        Comparator.comparing(Employee::name);
```

Hai câu lệnh là **fragment minh họa**, cần import `java.util.Comparator` và một kiểu `Employee` có accessor `age()`/`name()`. Trong phần chương trình hoàn chỉnh bên dưới sẽ có lớp dữ liệu cụ thể và ví dụ chạy được.

Với một comparator hợp lệ, phép `compare(a, b)` trả:

| Kết quả | Ý nghĩa |
| --- | --- |
| Số âm | `a` đứng **trước** `b` |
| `0` | `a` và `b` **tương đương về thứ tự** theo comparator |
| Số dương | `a` đứng **sau** `b` |

Giá trị `-1` hay `1` chỉ là các trường hợp có thể có. **Hợp đồng yêu cầu dấu**, không yêu cầu luôn trả đúng `-1, 0, 1`.

## 2. Phân biệt `Comparator`, `Comparable` và `equals()`

| API | Người định nghĩa thứ tự | Nghiệp vụ phù hợp |
| --- | --- | --- |
| `Comparable<T>` với `compareTo(T)` | **Bên trong lớp** triển khai `Comparable` | Một thứ tự tự nhiên, mặc định và ổn định cho kiểu |
| `Comparator<T>` với `compare(T, T)` | **Bên ngoài lớp**, qua object/lambda/method reference | Nhiều thứ tự, hoặc kiểu dữ liệu không thể/chưa muốn sửa |
| `Object.equals(Object)` | Bên trong lớp (hoặc kế thừa) | Xác định hai đối tượng **bằng nhau về bản sắc logic** |

`Comparable` là lựa chọn thích hợp khi có một thứ tự tự nhiên rõ ràng, ví dụ `String` so theo thứ tự từ điển, `Integer` so theo giá trị số. Ngược lại, `Comparator` phù hợp khi cần sắp xếp `Employee` theo tuổi hôm nay và theo tên ngày mai, hoặc khi thư viện cung cấp lớp `final` không thể chỉnh sửa.

**`compare(a, b) == 0` không tự động đồng nghĩa `a.equals(b)`.** Comparator có thể coi hai nhân viên cùng tuổi là tương đương **về thứ tự tuổi** mặc dù họ có mã định danh khác nhau. Việc này thường chấp nhận được khi chỉ sắp xếp một danh sách; nhưng cần chú ý với các cấu trúc sử dụng comparator để nhận diện khóa như `TreeSet` và `TreeMap`.

Một comparator không thay đổi `equals()` hoặc `hashCode()` của đối tượng. Nó chỉ cung cấp một quan hệ thứ tự được chọn trong lúc thao tác.

## 3. Cách tạo comparator không phải viết lớp riêng

Từ Java 8, `Comparator` là **functional interface**: phương thức trừu tượng chính là `int compare(T o1, T o2)`. Vì vậy có thể dùng lambda hoặc method reference.

### Viết bằng lambda

```java
Comparator<Employee> byAge =
        (left, right) -> Integer.compare(left.age(), right.age());
```

`Integer.compare` được ưu tiên so với `left.age() - right.age()`: phép trừ hai số nguyên có thể **tràn số**, gây kết quả thứ tự sai. Ví dụ `Integer.MAX_VALUE - (-1)` vượt phạm vi `int`.

### Viết bằng factory method

```java
Comparator<Employee> byAge =
        Comparator.comparingInt(Employee::age);

Comparator<Employee> byId =
        Comparator.comparingInt(Employee::id);

Comparator<Employee> byName =
        Comparator.comparing(Employee::name);
```

Các comparator này cùng có kiểu `Comparator<Employee>`, nhưng mỗi cái dùng **một khóa sắp xếp khác nhau**. `comparingInt` tránh boxing khóa `int` thành `Integer` để so sánh. Các phương thức tương tự gồm `comparingLong`, `comparingDouble`; dùng `comparing` cho khóa đối tượng như `String`.

Nếu khóa không phải kiểu có thứ tự tự nhiên phù hợp, truyền thêm comparator của khóa:

```java
Comparator<Employee> byNameIgnoringCase =
        Comparator.comparing(
                Employee::name,
                String.CASE_INSENSITIVE_ORDER);
```

Đoạn này giả định tên không null. Nếu tên có thể null, cần chỉ rõ quy tắc đặt null trước/sau hoặc bỏ qua bản ghi không hợp lệ.

### Kết hợp nhiều khóa bằng `thenComparing`

Sắp xếp theo tuổi tăng dần, **nếu bằng tuổi thì theo mã tăng dần**:

```java
Comparator<Employee> byAgeThenId =
        Comparator.comparingInt(Employee::age)
                  .thenComparingInt(Employee::id);
```

Khi hai người cùng tuổi, comparator không trả ngay `0` mà dùng mã để quyết định. Nếu mã là **duy nhất** và định nghĩa bản sắc logic của `Employee`, so sánh cuối theo mã giúp tránh coi hai người khác nhau là cùng phần tử trong `TreeSet`.

Tuy nhiên, thêm tie-breaker chỉ đúng nếu khóa cuối nhất quán với chính sách bằng nhau của lớp. Nếu một lớp có `equals()` dựa trên nhiều trường, hãy hiểu rõ các trường đó trước khi kỳ vọng `compare(a,b)==0` tương đương `equals()`.

### Đảo thứ tự bằng `reversed()`

```java
Comparator<Employee> descendingAgeThenId =
        byAgeThenId.reversed();
```

`reversed()` đảo **toàn bộ** thứ tự, bao gồm cả khóa phụ: tuổi giảm dần, nếu cùng tuổi thì mã cũng giảm dần. Nếu muốn *tuổi giảm dần nhưng mã tăng dần*, hãy đảo comparator tuổi **trước** khi thêm khóa phụ:

```java
Comparator<Employee> ageDescendingIdAscending =
        Comparator.comparingInt(Employee::age)
                  .reversed()
                  .thenComparingInt(Employee::id);
```

Hai chiến lược khác nhau về kết quả khi có người cùng tuổi.

## 4. Chương trình Java 17 chạy được

Tạo tệp `ExternalComparatorDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ExternalComparatorDemo {

    // Record chỉ giữ dữ liệu; không implements Comparable.
    record Employee(int id, String name, int age) {}

    static List<Integer> ids(List<Employee> employees) {
        return employees.stream()
                .map(e -> e == null ? -1 : e.id())
                .toList();
    }

    public static void main(String[] args) {
        Employee alice = new Employee(2, "Alice", 30);
        Employee bao = new Employee(1, "Bao", 25);
        Employee chi = new Employee(3, "Chi", 25);

        List<Employee> original = List.of(alice, bao, chi);

        Comparator<Employee> byAgeThenId =
                Comparator.comparingInt(Employee::age)
                          .thenComparingInt(Employee::id);

        Comparator<Employee> byNameThenId =
                Comparator.comparing(
                        Employee::name,
                        String.CASE_INSENSITIVE_ORDER)
                          .thenComparingInt(Employee::id);

        List<Employee> byAge = new ArrayList<>(original);
        byAge.sort(byAgeThenId);
        System.out.println("original: " + ids(original));
        System.out.println("age then id: " + ids(byAge));

        List<Employee> byName = new ArrayList<>(original);
        byName.sort(byNameThenId);
        System.out.println("name then id: " + ids(byName));

        List<Employee> reversed = new ArrayList<>(original);
        reversed.sort(byAgeThenId.reversed());
        System.out.println("reversed all keys: " + ids(reversed));

        List<Employee> onlyAgeDescending = new ArrayList<>(original);
        onlyAgeDescending.sort(
                Comparator.comparingInt(Employee::age)
                          .reversed()
                          .thenComparingInt(Employee::id));
        System.out.println("age down, id up: "
                + ids(onlyAgeDescending));

        System.out.println("stream sorted: " +
                ids(original.stream().sorted(byAgeThenId).toList()));

        Comparator<Employee> onlyAge =
                Comparator.comparingInt(Employee::age);
        System.out.println("bao equals chi: " + bao.equals(chi));
        System.out.println("age-only compare bao chi: "
                + onlyAge.compare(bao, chi));

        Set<Employee> loose = new TreeSet<>(onlyAge);
        loose.add(bao);
        loose.add(chi);
        loose.add(alice);
        System.out.println("TreeSet age-only size: " + loose.size());
        System.out.println("TreeSet age-only contains chi: "
                + loose.contains(chi));

        Set<Employee> strict = new TreeSet<>(byAgeThenId);
        strict.add(bao);
        strict.add(chi);
        strict.add(alice);
        System.out.println("TreeSet age-id size: " + strict.size());

        List<Employee> nullable =
                new ArrayList<>(Arrays.asList(chi, null, alice, bao));
        nullable.sort(Comparator.nullsLast(byAgeThenId));
        System.out.println("nulls last IDs: " + ids(nullable));
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac ExternalComparatorDemo.java
java ExternalComparatorDemo
```

Kết quả stdout chính xác:

```text
original: [2, 1, 3]
age then id: [1, 3, 2]
name then id: [2, 1, 3]
reversed all keys: [2, 3, 1]
age down, id up: [2, 1, 3]
stream sorted: [1, 3, 2]
bao equals chi: false
age-only compare bao chi: 0
TreeSet age-only size: 2
TreeSet age-only contains chi: true
TreeSet age-id size: 3
nulls last IDs: [1, 3, 2, -1]
```

Trong hàm `ids`, giá trị `-1` chỉ là **ký hiệu in ra cho phần tử null**. Người trong ví dụ đều có mã dương, nên có thể phân biệt ký hiệu này. Trong chương trình thật, không nên dùng số `-1` làm dấu hiệu null nếu `-1` là một mã hợp lệ.

### Phân tích kết quả

- Danh sách `original` giữ thứ tự được tạo: Alice (id 2), Bao (id 1), Chi (id 3). `sort` chỉ thay đổi **bản sao `ArrayList`**, không sửa `original`.
- `byAgeThenId` đặt Bao và Chi (25 tuổi) trước Alice (30 tuổi), rồi phân định Bao id 1 trước Chi id 3.
- `byAgeThenId.reversed()` đảo cả khóa tuổi và mã nên Alice trước, rồi Chi, rồi Bao. Đảo chỉ khóa tuổi trước khi ghép mã sẽ cho Alice, Bao, Chi.
- `stream().sorted(byAgeThenId)` trả một danh sách mới theo thứ tự tương ứng. Nó không sửa `original`.
- `bao.equals(chi)` là `false` vì record tự so sánh theo **tất cả thành phần**; comparator chỉ xét tuổi trả `0`.
- `TreeSet` dùng dấu của `compare` để quyết định phần tử trùng nhau. Khi chỉ so tuổi, việc thêm Chi 25 tuổi sau Bao 25 tuổi không thêm phần tử mới; `contains(chi)` vẫn `true` vì comparator coi Chi tương đương Bao.
- `byAgeThenId` phân biệt mã Bao và Chi, nên `TreeSet` thứ hai chứa đủ ba người.
- `Comparator.nullsLast(byAgeThenId)` đặt phần tử null cuối, phần tử không null vẫn theo tuổi và id. Chương trình minh họa vì sao phải chủ động xử lý null khi nguồn dữ liệu cho phép.

## 5. Cơ chế `List.sort` và `Stream.sorted`

### Sắp xếp trực tiếp trên danh sách

`List.sort(Comparator<? super E> c)` **thay đổi thứ tự phần tử của danh sách đang gọi phương thức** và không tạo danh sách mới. Vì vậy với danh sách không cho phép sửa thứ tự (chẳng hạn `List.of(...)`), gọi `sort` có thể ném `UnsupportedOperationException`.

```java
List<Employee> mutable = new ArrayList<>(employees);
mutable.sort(byAgeThenId);
```

Đoạn này là fragment giả sử có `employees` và `byAgeThenId`. Tạo bản sao có thể sửa giúp giữ nguồn đầu vào nguyên vẹn.

Theo hợp đồng, sắp xếp bằng `List.sort` là **stable**: khi comparator trả `0` cho hai phần tử, thứ tự tương đối ban đầu của chúng được bảo toàn. Tuy vậy stable sort **không thay thế khóa phụ** nếu nghiệp vụ muốn một thứ tự tổng quát có thể lặp lại từ mọi cách nhập dữ liệu. Kết hợp `thenComparing` giúp quy tắc rõ ràng.

### Sắp xếp qua stream

`stream.sorted(comparator)` trả một stream đã sắp thứ tự để thực hiện thao tác tiếp theo; có thể tạo danh sách bằng `toList()` trong Java 17:

```java
List<Employee> sorted = employees.stream()
        .sorted(byAgeThenId)
        .toList();
```

Kết quả `Stream.toList()` là danh sách **không cho phép sửa**, vì vậy muốn sắp xếp tiếp in-place thì nên sao chép ra `ArrayList`. Với các stream có thứ tự encounter order, `Stream.sorted` bảo đảm tính ổn định; với stream không có thứ tự, không có bảo đảm ổn định theo một encounter order vốn không tồn tại.

Ngoài `List` và stream, các phương thức như `Collections.sort(list, comparator)` hoặc `Arrays.sort(array, comparator)` cũng dùng comparator phù hợp với kiểu phần tử. Chọn API theo loại dữ liệu và việc có muốn thay đổi collection gốc hay không.

## 6. Hợp đồng của một `Comparator` đúng

Một comparator phải mô tả quan hệ thứ tự hợp lệ trên toàn bộ miền giá trị mà nó chấp nhận. Với `sgn` là dấu của số nguyên, ba tính chất quan trọng là:

1. **Đối dấu:** `sgn(compare(a,b)) == -sgn(compare(b,a))`.
2. **Bắc cầu:** nếu `a > b` và `b > c` theo comparator thì `a > c`.
3. **Nhóm tương đương nhất quán:** nếu `compare(a,b)==0`, so `a` và `b` với cùng một `z` phải cho kết quả cùng dấu.

Không dùng phép trừ số nguyên làm phương thức so sánh tổng quát, vì phép trừ có thể tràn. Dùng `Integer.compare`, `Long.compare`, `Double.compare` hoặc các factory `comparingInt/Long/Double` theo kiểu khóa. `Double.compare` còn định nghĩa rõ thứ tự cho `NaN`, vô cực và `-0.0`.

Nếu comparator đọc random, đồng hồ, cờ cấu hình thay đổi, hoặc thuộc tính bị sửa trong lúc sort, thứ tự có thể tự mâu thuẫn. Java có thể phát hiện một số lỗi và ném `IllegalArgumentException`, nhưng không bảo đảm phát hiện mọi comparator sai. Bài về *hợp đồng dấu, tính bắc cầu và nhóm 0* sẽ kiểm tra các điều kiện này bằng cặp, bộ ba giá trị và ca biên.
## 7. `compare(a,b) == 0` và `equals()` trong `TreeSet` / `TreeMap`

`Comparator` quyết định **thứ tự**, còn `equals()` quyết định **bằng nhau theo hợp đồng của đối tượng**. Với `List.sort`, hai nhân viên cùng tuổi vẫn được giữ trong danh sách. Nhưng `TreeSet` và `TreeMap` dùng `compare(a,b)==0` để nhận diện phần tử/khóa tương đương. Trong ví dụ:

- `bao.equals(chi)` là `false` vì hai record khác mã và tên.
- Comparator chỉ xét tuổi trả `0`, nên `TreeSet` giữ một đại diện của nhóm tuổi 25 và `contains(chi)` vẫn có thể trả `true`.
- `TreeMap` dùng cùng quy tắc có thể thay **giá trị** của mapping hiện có khi `put` một khóa mới nhưng so sánh bằng 0.

Thứ tự **nhất quán với equals** khi `compare(a,b)==0` *khi và chỉ khi* `a.equals(b)` (xét trên miền dữ liệu hợp lệ). Phải kiểm tra **cả hai chiều**. `byAgeThenId` trong ví dụ phân biệt các bản ghi đang có, nhưng chưa chắc nhất quán với `equals` của mọi `Employee`: hai record cùng tuổi và mã nhưng tên khác vẫn so bằng 0. Với `TreeSet/TreeMap`, hãy xác định đúng khóa định danh hoặc dùng cấu trúc khác nếu muốn giữ tất cả phần tử cùng thứ hạng. Bài riêng về Comparator dùng cho hiển thị và quan hệ bằng nhau phân tích sâu hai hướng sai lệch.
## 8. Xử lý `null` và khóa nullable

Mặc định các comparator từ `Comparator.comparingInt(Employee::age)` sẽ gọi getter và không xử lý object `null`, nên so sánh với phần tử null có thể ném `NullPointerException`.

Muốn đặt **toàn bộ nhân viên null** ở cuối:

```java
Comparator<Employee> nullableEmployees =
        Comparator.nullsLast(byAgeThenId);
```

Muốn đặt null ở đầu thì dùng `Comparator.nullsFirst(byAgeThenId)`. Đây là hai phương thức xử lý **đối tượng được so sánh là null**.

Khác với trường hợp **trường khóa bên trong đối tượng null**. Ví dụ `name` có thể null nhưng `Employee` vẫn là object không null. Khi đó cần xử lý comparator của khóa:

```java
Comparator<Employee> nullableNames =
        Comparator.comparing(
                Employee::name,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
```

Đoạn này cho `name == null` xếp cuối so với tên không null, trong phạm vi các object nhân viên không null. Nếu có thể null cả nhân viên và tên, kết hợp **hai lớp chính sách**:

```java
Comparator<Employee> bothNullable =
        Comparator.nullsLast(nullableNames);
```

Những đoạn trên là fragment minh họa, giả sử đã có record `Employee` và các comparator phù hợp. Không nên tự tiện coi chuỗi rỗng `""` là null hoặc thay null bằng một giá trị mặc định nếu điều đó có thể làm mất phân biệt dữ liệu nghiệp vụ.

## 9. Những lỗi thường gặp khi định nghĩa thứ tự ngoài lớp

- **Không dùng tie-breaker khi cần uniqueness.** So theo tuổi đơn thuần có thể khiến `TreeSet` bỏ một nhân viên khác có cùng tuổi.
- **Quên `reversed()` đảo cả khóa phụ.** Nếu muốn chính/phụ trái chiều, đặt `reversed()` đúng vị trí trong chuỗi factory method.
- **Viết `return a.age() - b.age()`.** Phép trừ có thể overflow; dùng `Integer.compare` hoặc `Comparator.comparingInt`.
- **Nhầm `compare() == 0` với `equals()`.** Hai quan hệ không có cùng ý nghĩa mặc định; kiểm tra riêng khi làm khóa collection.
- **Đọc nhầm `sort` là bất biến.** `List.sort` sửa list gọi nó; `stream.sorted(...).toList()` tạo danh sách kết quả.
- **Bỏ qua null.** `nullsFirst/nullsLast` cho object null không tự xử lý khóa `name` null bên trong object.
- **Cố dùng comparator cho dữ liệu biến đổi trong `TreeSet`/`TreeMap`.** Nếu trường tuổi/id dùng khi định vị trong cây thay đổi sau khi chèn, lookup và thứ tự có thể không còn hợp lệ. Ưu tiên khóa bất biến hoặc xóa rồi thêm lại với trạng thái mới.
- **Comparator trả giá trị tùy tiện hoặc phụ thuộc vào thứ tự gọi.** Có thể vi phạm đối dấu/bắc cầu, gây lỗi sắp xếp và hành vi không xác định về ngữ nghĩa nghiệp vụ.

## 10. Checklist và bài tập tự kiểm tra

### Checklist

1. Thứ tự cần thiết là tự nhiên của lớp hay một yêu cầu cụ thể của màn hình/nghiệp vụ? Nếu có nhiều phương án, tạo `Comparator<T>` bên ngoài.
2. Khóa sắp xếp là gì? Có cần nhiều khóa (`thenComparing`) để phân định trường hợp bằng nhau không?
3. Có cần tăng/giảm riêng từng khóa hay đảo toàn bộ comparator?
4. Các đối tượng/trường khóa có thể null không? Định nghĩa vị trí của null rõ ràng.
5. Comparator có thỏa đối dấu, bắc cầu và ổn định khi dữ liệu không đổi không?
6. Comparator chỉ dùng để sắp xếp một list hay còn làm bộ xác định uniqueness cho `TreeSet`/`TreeMap`?
7. Nếu dùng làm khóa sorted collection, `compare(a,b)==0` có phù hợp chính sách `equals()` và bản sắc logic mong muốn không?
8. Có đang so sánh số nguyên bằng phép trừ dễ overflow, hoặc so số thực với các quy tắc không nhất quán không?

### Bài tập

**Bài 1.** Có ba nhân viên: A tuổi 30 id 2, B tuổi 25 id 1, C tuổi 25 id 3. Với `comparingInt(age).thenComparingInt(id)`, thứ tự là gì?

**Đáp án:** B, C, A; tuổi tăng trước, id tăng khi bằng tuổi.

**Bài 2.** Gọi `reversed()` sau khi đã ghép `age` rồi `id` sẽ ra thứ tự gì?

**Đáp án:** A, C, B; cả hai tiêu chí cùng bị đảo. Muốn A, B, C phải đảo comparator tuổi trước khi ghép id tăng.

**Bài 3.** Nếu `a.equals(b)` là false nhưng `compare(a,b)` là `0`, comparator có nhất thiết sai khi dùng `List.sort` không?

**Đáp án:** Không. Nó có thể tạo một nhóm ngang thứ tự hợp lệ, ví dụ người cùng tuổi; list vẫn giữ đủ hai phần tử. Nhưng nếu dùng comparator ấy làm chính sách khóa của `TreeSet`, hai người có thể bị gộp.

**Bài 4.** `Comparator.nullsLast(comparing(Employee::name))` có tự xử lý `employee.name() == null` không?

**Đáp án:** Không. `nullsLast` ở bên ngoài chỉ xử lý object `Employee` null. Muốn xử lý trường `name` null thì áp dụng `nullsLast` cho comparator của **khóa tên** bên trong `comparing`.

## Ghi nhớ

**`Comparator<T>` cho phép đặt quy tắc sắp xếp ở bên ngoài lớp dữ liệu**, nên một kiểu có thể được sắp theo nhiều tiêu chí mà không cần thay đổi chính class. Sử dụng `comparing...`, `thenComparing...` và `reversed()` để mô tả chính sách rõ ràng, tránh phép trừ dễ tràn và định nghĩa null khi cần. Với `TreeSet`/`TreeMap`, `compare(a,b)==0` còn là tiêu chí xem phần tử/khóa trùng nhau; vì thế comparator phải tương thích với bản sắc logic mong muốn và hợp đồng của collection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparators-002">Hợp đồng dấu, bắc cầu và các nhóm có kết quả so sánh bằng 0</a>

<details>
<summary>Xem chi tiết</summary>

# Hợp đồng dấu, bắc cầu và các nhóm có kết quả so sánh bằng 0

## 1. Comparator định nghĩa một quan hệ thứ tự, không chỉ một công thức sắp xếp

`java.util.Comparator<T>` biểu diễn quy tắc so sánh hai giá trị cùng kiểu `T`. Phương thức trung tâm là:

```java
int compare(T a, T b);
```

Đó là **chữ ký minh họa từ interface**, không phải một chương trình chạy độc lập. Khi gọi `compare(a, b)`, ý nghĩa của **dấu** kết quả là:

| Giá trị trả về | Ý nghĩa |
| --- | --- |
| Nhỏ hơn `0` | `a` đứng trước `b`. |
| Bằng `0` | `a` và `b` thuộc **cùng nhóm tương đương theo comparator**. |
| Lớn hơn `0` | `a` đứng sau `b`. |

Số cụ thể không quan trọng: `-7` và `-1` đều biểu diễn “nhỏ hơn”; `42` và `1` đều biểu diễn “lớn hơn”. Vì vậy hãy kiểm tra `compare(a,b) < 0`, `== 0`, `> 0` hoặc dùng `Integer.signum(...)` khi cần so dấu. Đừng gắn ý nghĩa thứ hạng, khoảng cách hay mức chênh lệch nghiệp vụ trực tiếp vào trị tuyệt đối của kết quả.

Một `Comparator` **hợp lệ** phải cho quan hệ có tính nhất quán trên toàn bộ miền đối tượng được phép so. Nếu chỉ cho kết quả có vẻ đúng trong vài cặp nhưng vi phạm hợp đồng dấu hoặc tính bắc cầu, `List.sort`, `Collections.sort`, `Arrays.sort`, `TreeSet` và `TreeMap` có thể hoạt động không đúng điều ta mong đợi. Một số thuật toán sắp xếp có thể phát hiện vi phạm và ném `IllegalArgumentException: Comparison method violates its general contract!`; không được dựa vào việc lỗi chắc chắn bị phát hiện hay luôn xuất hiện.

## 2. Hợp đồng dấu: đảo thứ tự đối số phải đảo dấu

Với các đối tượng hợp lệ `x` và `y`, Comparator yêu cầu:

```text
sgn(compare(x, y)) == -sgn(compare(y, x))
```

Trong đó `sgn(n)` là dấu của `n`: `-1`, `0` hoặc `1`. Nói cách khác:

- Nếu `compare(x, y) < 0` thì `compare(y, x) > 0`.
- Nếu `compare(x, y) > 0` thì `compare(y, x) < 0`.
- Nếu `compare(x, y) == 0` thì `compare(y, x) == 0`.

Đây là quy tắc **đối xứng về dấu**; không phải yêu cầu hai kết quả nguyên phải đối nhau về trị số. Ví dụ `compare(x,y) == -10` và `compare(y,x) == 5` vẫn thỏa điều kiện dấu, dù thường dễ hiểu hơn nếu comparator trả `-1`, `0`, `1`. Điều kiện này cũng có hệ quả: nếu lời gọi một chiều ném exception cho một cặp, chiều đảo không được tùy tiện trả một dấu có nghĩa như thể cặp hợp lệ.

Cách triển khai chuẩn khi so trường nguyên:

```java
Comparator<Task> byPriority =
        Comparator.comparingInt(Task::priority);
```

*Đoạn này giả định kiểu `Task` có accessor `priority()` trả về `int` và được đặt trong nơi có thể khai báo biến; chương trình hoàn chỉnh ở mục 6.*

`Comparator.comparingInt` dùng phép so số nguyên đúng quy tắc; khi hai giá trị bằng nhau, kết quả bằng `0`, còn khi chênh lệch thì kết quả mang dấu tương ứng.

### Vì sao không viết phép trừ một cách máy móc?

```java
Comparator<Integer> bad = (a, b) -> a - b;
```

Đây là cách viết **không an toàn** trên toàn miền `int` vì phép trừ có thể tràn số nguyên. Ví dụ:

```java
int a = Integer.MIN_VALUE;
int b = 1;

System.out.println(a - b);             // 2147483647: tràn số
System.out.println(Integer.compare(a, b)); // -1: đúng thứ tự
```

Dùng `Integer.compare(a,b)`, `Long.compare(...)` hay các factory như `comparingInt`, `comparingLong`, `comparingDouble`. Tương tự, đừng đảo comparator bằng cách tùy tiện `-compare(a,b)`: nếu kết quả là `Integer.MIN_VALUE` thì dấu của phép phủ định vẫn có thể sai do overflow. `comparator.reversed()` hoặc đổi thứ tự đối số một cách phù hợp sẽ diễn đạt rõ ý hơn.

## 3. Tính bắc cầu: không được có vòng thứ tự

Hợp đồng Comparator yêu cầu:

```text
compare(x, y) > 0 và compare(y, z) > 0
    => compare(x, z) > 0
```

Nhờ tính đối xứng dấu, phát biểu tương đương cho chiều “nhỏ hơn” là:

```text
x < y và y < z  =>  x < z
```

Ví dụ các ưu tiên `1 < 2 < 3` thì phải có `1 < 3`. Nếu comparator kết luận `A < B`, `B < C` nhưng lại `C < A`, ta có một chu trình không thể sắp thành thứ tự nhất quán. Một comparator kiểu “kéo–búa–bao” định nghĩa A thắng B, B thắng C, C thắng A có thể hữu ích cho trò chơi nhưng **không thích hợp làm quy tắc sort**.

Tính bắc cầu phải đúng với **mọi bộ ba** giá trị hợp lệ, không chỉ ba trường hợp thường gặp. Với comparator nhiều tiêu chí, nên so lần lượt từng trường theo một thứ tự ưu tiên xác định, rồi chỉ dùng tiêu chí tiếp theo khi tiêu chí trước bằng nhau. `thenComparing(...)` giúp thể hiện quy tắc này.

Ví dụ một comparator được xây dựng từ các thành phần đã có thứ tự hợp lệ:

```java
Comparator<Task> ordering = Comparator
        .comparingInt(Task::priority)
        .thenComparing(Task::team)
        .thenComparing(Task::code);
```

*Đây là đoạn minh họa trong một lớp/phương thức có kiểu `Task`. `team()` và `code()` trả `String` không `null`.*

Thứ tự là: ưu tiên nhỏ đứng trước; nếu cùng ưu tiên thì so tên nhóm; nếu cùng cả hai thì so mã nhiệm vụ. Với `String` dùng thứ tự tự nhiên, một bộ tiêu chí như vậy thường dễ phân tích và kiểm tra tính bắc cầu hơn một chuỗi `if` trả các dấu tùy ý.

## 4. Kết quả compare == 0 tạo nhóm tương đương theo thứ tự

Một điểm thường bị bỏ qua: `compare(x,y) == 0` **không bắt buộc có nghĩa `x.equals(y)`**.

Ví dụ, comparator chỉ xét `priority`:

```java
Comparator<Task> byPriority =
        Comparator.comparingInt(Task::priority);
```

Các tác vụ sau có thể đều có ưu tiên `2`:

```text
Task(team="alpha", priority=2, code="A")
Task(team="alpha", priority=2, code="B")
Task(team="beta",  priority=2, code="C")
```

Chúng khác nhau về `team` hoặc `code` nên có thể không bằng nhau theo `equals()`, nhưng comparator trả `0` cho mọi cặp trong nhóm ưu tiên `2`. Đây là quyết định **cố ý bỏ qua các tiêu chí không thuộc thứ tự**, khiến comparator nhìn cả nhóm như một mức hạng chung.

Định nghĩa quan hệ `x ~ y` khi `compare(x,y) == 0`. Với một comparator hợp lệ, quan hệ này phải là một **quan hệ tương đương**:

1. **Phản xạ:** `compare(x,x) == 0`.
2. **Đối xứng:** `compare(x,y) == 0` thì `compare(y,x) == 0`.
3. **Bắc cầu:** nếu `compare(x,y) == 0` và `compare(y,z) == 0` thì `compare(x,z) == 0`.

Hơn nữa, hai phần tử đã nằm **cùng nhóm 0** phải có cùng quan hệ thứ tự với **mọi phần tử thứ ba**:

```text
compare(x, y) == 0
    => sgn(compare(x, z)) == sgn(compare(y, z))
       với mọi z hợp lệ
```

Ví dụ, nếu `x` và `y` cùng nhóm ưu tiên 2, còn `z` có ưu tiên 5, thì cả hai phải đứng trước `z`. Không được cho `x < z` nhưng `y > z` vì như vậy một nhóm tương đương lại tự mâu thuẫn.

Điều kiện này đôi khi gọi là **nhất quán của nhóm có compare bằng 0**; nó mạnh hơn câu “chỉ cần comparator có đối xứng dấu”. Một phép so tùy biến có thể trả `0` cho cặp đặc biệt nhưng vẫn vi phạm quy tắc khi gặp phần tử thứ ba.

### Ví dụ một nhóm 0 sai về logic

Giả sử trên số nguyên, ta đặc cách `1` và `3` “bằng nhau” (compare bằng 0), nhưng mọi cặp còn lại dùng thứ tự số tự nhiên. Khi so với `2`:

- `compare(1,3) == 0`.
- `compare(1,2) < 0` vì `1 < 2`.
- `compare(3,2) > 0` vì `3 > 2`.

`1` và `3` đã được tuyên bố cùng nhóm nhưng lại đứng hai phía khác nhau của `2`. Đó là comparator sai hợp đồng; không thể sửa bằng cách chỉ đổi cách sort. Muốn nhóm theo quy tắc khác, phải **định nghĩa thứ tự trên các nhóm** sao cho mọi thành viên của cùng nhóm có cùng kết quả dấu với mọi nhóm khác.

## 5. Quan hệ của Comparator với equals()

Java phân biệt hai quan hệ:

- `x.equals(y)`: bình đẳng logic do kiểu `x` định nghĩa.
- `comparator.compare(x,y) == 0`: tương đương theo thứ tự mà comparator cụ thể cung cấp.

Một comparator được xem là **consistent with equals** khi với mọi cặp thuộc miền so sánh, `compare(x,y) == 0` khi và chỉ khi `x.equals(y)`. Đây là đặc tính **được khuyến nghị mạnh** nếu comparator dùng trong sorted set/sorted map với ngữ nghĩa khóa của `Set`/`Map`; không phải mọi comparator đều bắt buộc phân biệt đối tượng theo `equals()`.

Ví dụ khác nhau có chủ đích:

```java
String a = "Java";
String b = "JAVA";

System.out.println(a.equals(b));                  // false
System.out.println(
        String.CASE_INSENSITIVE_ORDER.compare(a, b)); // 0
```

Với `String.CASE_INSENSITIVE_ORDER`, hai chuỗi thuộc cùng nhóm sắp xếp không phân biệt chữ hoa/thường, nhưng `String.equals()` vẫn phân biệt chữ hoa/thường. Quan hệ này **không consistent with equals**.

Đặc biệt với `BigDecimal`:

```java
BigDecimal one = new BigDecimal("1.0");
BigDecimal another = new BigDecimal("1.00");

System.out.println(one.compareTo(another)); // 0
System.out.println(one.equals(another));    // false
```

*Đoạn minh họa cần `import java.math.BigDecimal;`.* `BigDecimal.compareTo` xem hai số cùng giá trị số là tương đương theo thứ tự, còn `equals` xét cả `scale`. Comparator theo thứ tự tự nhiên của `BigDecimal` vì thế không consistent with equals.

Khi đối tượng là khóa của `TreeMap` hoặc phần tử `TreeSet`, cần biết chính xác mục đích: **muốn gộp theo một tiêu chí**, hay **muốn phân biệt tất cả các giá trị khác nhau theo equals**? Cả hai nhu cầu đều hợp lý, nhưng tạo ra hành vi khác nhau.

## 6. Ví dụ Java 17+ chạy được: dấu, bắc cầu, nhóm 0, TreeSet và TreeMap

Lưu chương trình sau vào tệp `ComparatorContractDemo.java`:

```java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ComparatorContractDemo {

    record Task(String team, int priority, String code) {}

    public static void main(String[] args) {
        Task a = new Task("alpha", 2, "A");
        Task b = new Task("alpha", 2, "B");
        Task c = new Task("beta", 4, "C");
        Task d = new Task("beta", 2, "D");
        Task e = new Task("beta", 6, "E");

        Comparator<Task> byPriority =
                Comparator.comparingInt(Task::priority);

        // Quy tắc dấu và đối xứng dấu.
        System.out.println(byPriority.compare(a, b));
        System.out.println(a.equals(b));
        System.out.println(Integer.signum(byPriority.compare(a, c)));
        System.out.println(Integer.signum(byPriority.compare(c, a)));

        // Tính bắc cầu: 2 < 4 < 6 nên 2 < 6.
        boolean transitive =
                byPriority.compare(a, c) < 0
                && byPriority.compare(c, e) < 0
                && byPriority.compare(a, e) < 0;
        System.out.println(transitive);

        // TreeSet coi các Task cùng priority là tương đương.
        Set<Task> grouped = new TreeSet<>(byPriority);
        grouped.add(a);
        grouped.add(b);
        grouped.add(c);
        grouped.add(d);
        grouped.add(e);
        System.out.println(grouped.size());
        System.out.println(grouped.contains(
                new Task("unknown", 2, "Z")));

        // TreeMap cũng xác định khóa tương đương theo comparator.
        Map<Task, String> map = new TreeMap<>(byPriority);
        map.put(a, "đầu tiên");
        map.put(b, "cập nhật");
        System.out.println(map.size());
        System.out.println(map.get(a));

        // Thêm tie-breaker để phân biệt cả ba thành phần của record.
        Comparator<Task> byAllFields = byPriority
                .thenComparing(Task::team)
                .thenComparing(Task::code);
        Set<Task> distinct = new TreeSet<>(byAllFields);
        distinct.add(a);
        distinct.add(b);
        distinct.add(c);
        distinct.add(d);
        distinct.add(e);
        System.out.println(distinct.size());
        System.out.println(Integer.signum(byAllFields.compare(a, b)));

        // Hai ví dụ compare == 0 nhưng equals() là false.
        String s1 = "Java";
        String s2 = "JAVA";
        System.out.println(s1.equals(s2));
        System.out.println(
                String.CASE_INSENSITIVE_ORDER.compare(s1, s2));

        BigDecimal x = new BigDecimal("1.0");
        BigDecimal y = new BigDecimal("1.00");
        System.out.println(x.compareTo(y));
        System.out.println(x.equals(y));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp bằng JDK 17 trở lên:

```bash
javac ComparatorContractDemo.java
java ComparatorContractDemo
```

Đầu ra chính xác:

```text
0
false
-1
1
true
3
true
1
cập nhật
5
-1
false
0
0
false
```

Diễn giải từng phần:

1. `byPriority.compare(a,b)` in `0` vì cùng ưu tiên `2`, nhưng `a.equals(b)` in `false` vì `Task` là record với ba component, trong đó `code` khác nhau.
2. `Integer.signum(compare(a,c))` in `-1`; đảo chiều thành `compare(c,a)` cho `1`. Đây là quan hệ dấu đối xứng.
3. Ba phép so 2, 4 và 6 cho `true` ở kiểm tra bắc cầu.
4. `TreeSet` với `byPriority` có ba nhóm tương đương theo các mức ưu tiên `2`, `4`, `6`, nên `size()` là `3`. `contains(new Task("unknown",2,"Z"))` là `true` dù record mới không equals với `a`, `b` hay `d`, vì comparator xem cùng ưu tiên là cùng khóa sắp xếp.
5. `TreeMap` dùng comparator để xác định khóa: thêm `a` rồi `b` (cùng ưu tiên) chỉ tạo **một mapping**; lệnh thứ hai thay giá trị thành `cập nhật`, nên `size()` là `1`.
6. `byAllFields` bổ sung `team` và `code` để tách các nhóm cùng `priority`. Năm record trong ví dụ đều khác nhau theo ba thành phần nên `TreeSet` có `5` phần tử; trong nhóm cùng ưu tiên và cùng team, `"A"` đứng trước `"B"`, trả dấu âm.
7. Hai cặp `String` và `BigDecimal` minh họa `compare == 0` nhưng `equals() == false` theo hai lý do khác nhau: bỏ qua phân biệt chữ hoa/thường và bỏ qua khác biệt scale.

## 7. Vì sao TreeSet và TreeMap coi compare == 0 là cùng phần tử/khóa?

`TreeSet` sử dụng thứ tự tự nhiên (`Comparable`) hoặc một `Comparator` được cung cấp để xác định vị trí phần tử và xem hai phần tử có tương đương trong cây hay không. `TreeMap` áp dụng nguyên tắc tương tự cho khóa. Do đó, đối với các cấu trúc này:

```text
compare(a, b) == 0
    ⇒ TreeSet xem a và b tương đương để lưu/tìm phần tử.
    ⇒ TreeMap xem a và b là cùng khóa để lưu/tìm mapping.
```

**Hệ quả thực tế:** `TreeSet` với comparator chỉ xét `priority` không giữ được nhiều `Task` khác nhau trong cùng một priority. Nó là tập hợp **mức ưu tiên** theo nghĩa comparator, không phải tập hợp tất cả tác vụ phân biệt theo record equality.

Nếu đó là chủ ý — ví dụ chỉ cần một đại diện cho mỗi nhóm ưu tiên — comparator theo một trường là hợp lý. Nếu muốn giữ **mọi tác vụ khác nhau**, hãy bổ sung tie-breaker đủ để phân biệt chúng.

Ví dụ:

```java
Comparator<Task> byPriorityThenTeamThenCode =
        Comparator.comparingInt(Task::priority)
                .thenComparing(Task::team)
                .thenComparing(Task::code);
```

*Đoạn minh họa dành cho cùng record `Task` ở mục 6.*

Với record ba thành phần của ví dụ, compare bằng 0 khi cả ba thành phần giống nhau, nên phù hợp với record `equals()`. Nhưng nếu đổi `Task` thành entity có equality **chỉ theo ID**, comparator ba trường trạng thái có thể không còn phù hợp. Cần so tiêu chí thứ tự với định nghĩa identity thật sự của lớp; chỉ thêm tie-breaker “cho chắc” mà không hiểu equality có thể gây một kiểu sai khác.

Tài liệu Java khuyến nghị lưu ý rõ khi comparator có ordering **inconsistent with equals**, bởi `SortedSet` và `SortedMap` được thiết kế để tuân theo các hợp đồng tổng quát `Set`/`Map` vốn mô tả equality theo `equals()`. Một `TreeSet` dùng comparator inconsistent vẫn vận hành theo quy tắc comparator; nhưng kết quả có thể không thỏa trực giác và hợp đồng bình đẳng của `Set` nói chung.

Phân biệt thêm: `HashSet`/`HashMap` thông thường dùng `equals()/hashCode()`, **không sử dụng Comparator** để gộp khóa. Vì vậy cùng một cặp phần tử có thể được `HashSet` giữ thành hai mục trong khi `TreeSet` có comparator xem chúng là một.

## 8. Những cách tạo Comparator hợp lệ và dễ bảo trì

Java hỗ trợ API composition để tránh tự viết dấu so sánh tùy hứng:

| API | Vai trò |
| --- | --- |
| `Comparator.comparing(keyExtractor)` | So theo khóa có thứ tự tự nhiên. |
| `Comparator.comparingInt/Long/Double(...)` | So khóa nguyên thủy theo thứ tự chuẩn và tránh phép trừ nguy hiểm. |
| `thenComparing(...)` | Dùng tiêu chí phụ khi tiêu chí trước cho kết quả `0`. |
| `reversed()` | Đảo toàn bộ thứ tự của comparator. |
| `Comparator.naturalOrder()` | Dùng thứ tự tự nhiên của kiểu có `Comparable`. |
| `Comparator.reverseOrder()` | Dùng thứ tự tự nhiên đảo chiều. |
| `Comparator.nullsFirst(...)` và `nullsLast(...)` | Quy định vị trí `null` nếu miền dữ liệu có thể chứa null. |

Ví dụ cần ưu tiên **cao trước**, sau đó mã theo thứ tự tăng:

```java
Comparator<Task> order = Comparator
        .comparingInt(Task::priority)
        .reversed()
        .thenComparing(Task::code);
```

*Đoạn minh họa áp dụng vào record `Task` ở mục 6.* Lưu ý vị trí `reversed()`: gọi **trước** `thenComparing` nghĩa là chỉ tiêu chí ưu tiên được đảo, còn `code` vẫn tăng dần. Nếu viết `Comparator.comparingInt(...).thenComparing(...).reversed()`, **toàn bộ thứ tự kết hợp** sẽ được đảo, kể cả tiêu chí phụ.

Khi trường có thể `null`, cần quyết định rõ:

```java
Comparator<String> nullableNames =
        Comparator.nullsFirst(Comparator.naturalOrder());
```

Nó cho `null` đứng trước các chuỗi khác và dùng thứ tự tự nhiên cho các chuỗi không null. `Comparator.naturalOrder()` tự thân không chấp nhận so một giá trị `null`. Không tạo quy tắc null tùy tiện dẫn đến tình trạng `compare(null,x)` trả cùng dấu với `compare(x,null)`.

Một số factory tạo comparator theo khóa của object yêu cầu key phù hợp để so. Nếu key extractor trả nullable mà chưa xử lý, `NullPointerException` có thể xuất hiện khi sort; hãy dùng comparator khóa có chính sách null rõ ràng, hoặc thiết kế dữ liệu không chứa null.

## 9. Tính ổn định của thứ tự khác với tính hợp lệ của Comparator

“Stable sort” có nghĩa các phần tử được comparator xem là bằng nhau (`compare == 0`) giữ **thứ tự tương đối ban đầu** sau khi sort. Điều này **không biến hai phần tử thành cùng object**, cũng không làm chúng `equals()` nhau.

Ví dụ danh sách chứa:

```text
Task A: priority=2
Task B: priority=1
Task C: priority=2
```

Nếu sort ổn định theo `priority` tăng dần, kết quả là `B, A, C`: A và C vẫn nằm cạnh nhau trong nhóm priority 2 theo thứ tự ban đầu. `List.sort` trong Java có cam kết sort ổn định. Tuy nhiên, **TreeSet** với cùng comparator sẽ giữ một phần tử đại diện cho nhóm priority 2, bởi đây là cấu trúc set theo thứ tự so sánh, không phải sắp xếp list.

Tính ổn định của một thuật toán sort **không sửa được** comparator sai hợp đồng. Một comparator thay kết quả theo đồng hồ, số ngẫu nhiên hoặc trạng thái mutable trong lúc sắp xếp có thể khiến kết quả không nhất quán dù thuật toán được cam kết stable.

Khi áp dụng comparator vào sorted collection dài hạn, dữ liệu tham gia so sánh cũng nên ổn định trong thời gian đối tượng là phần tử/khóa; nếu thay đổi trường sắp xếp sau khi insert vào `TreeSet`/`TreeMap`, cấu trúc cây không tự sắp lại theo vị trí mới, dẫn đến lỗi tra cứu khó hiểu.

## 10. Các lỗi Comparator phổ biến

### 10.1. Trả 1 cho mọi cặp không bằng nhau

```java
// SAI: nếu a < b và b > a đều trả 1, vi phạm đối xứng dấu.
Comparator<Integer> bad = (a, b) ->
        a.equals(b) ? 0 : 1;
```

Nếu hai giá trị khác nhau, so theo cả hai chiều đều cho dấu dương. Không có thứ tự nhất quán.

### 10.2. Tạo chu kỳ

Một luật “0 trước 1, 1 trước 2, nhưng 2 lại trước 0” vi phạm tính bắc cầu. Comparator dành cho sort phải áp dụng quan hệ thứ tự xuyên suốt trên tất cả cặp và bộ ba.

### 10.3. Cho hai giá trị bằng 0 nhưng bất đồng khi so với giá trị thứ ba

Nhóm `x ~ y` không thể đứng hai phía khác nhau của `z`. Nếu comparator dùng luật ngoại lệ cho một cặp mà không thiết kế cả lớp tương đương, dễ vi phạm điều kiện dấu cho nhóm 0.

### 10.4. Dùng phép trừ số nguyên

`(a,b) -> a.age() - b.age()` có thể sai trên miền `int` vì overflow. Sử dụng `Integer.compare` hoặc `Comparator.comparingInt`. Với `long`, phép ép hiệu thành `int` còn nguy hiểm hơn.

### 10.5. Nhầm compare bằng 0 với equals bằng true

`String.CASE_INSENSITIVE_ORDER` và `BigDecimal.compareTo` là những ví dụ compare bằng 0 không đồng nghĩa equals. Trong `TreeSet` hoặc `TreeMap`, điều này thay đổi cách gộp dữ liệu.

### 10.6. Dùng tie-breaker thiếu hoặc thừa

Comparator chỉ xét `lastName` sẽ gộp nhiều người trùng họ trong `TreeSet`. Nhưng comparator xét trạng thái mutable hoặc timestamp ngoài identity có thể làm cùng entity bị coi là khác nhau sau cập nhật. Phải xác định rõ điều kiện một “nhóm 0” có ý nghĩa gì với nghiệp vụ và collection đích.

### 10.7. Phá hợp đồng khi giá trị có null hoặc đặc biệt

So `double` bằng phép `a < b ? -1 : a > b ? 1 : 0` sẽ coi nhiều trường hợp `NaN` là tương đương với giá trị khác, dẫn đến logic kỳ lạ. `Double.compare` có quy tắc rõ cho `NaN` và số âm không (`-0.0`); dùng `Comparator.comparingDouble` thường an toàn hơn cách so bằng các toán tử quan hệ tùy biến. Nếu domain muốn coi các giá trị đặc biệt tương đương theo cách riêng, hãy xác định quy tắc chuẩn hóa trước và kiểm tra toàn bộ hợp đồng.

## 11. Kiểm tra hợp đồng Comparator bằng các tập dữ liệu đại diện

Với comparator `cmp` và tập các giá trị có ý nghĩa, có thể kiểm tra các quan hệ bằng assertions hoặc unit test:

```java
// Minh họa mã kiểm tra đặt trong phương thức test, với cmp và
// List<Task> samples đã được thiết lập.
for (Task x : samples) {
    for (Task y : samples) {
        int xy = Integer.signum(cmp.compare(x, y));
        int yx = Integer.signum(cmp.compare(y, x));
        if (xy != -yx) {
            throw new AssertionError("Vi phạm đối xứng dấu");
        }
        for (Task z : samples) {
            int yz = Integer.signum(cmp.compare(y, z));
            int xz = Integer.signum(cmp.compare(x, z));
            if (xy > 0 && yz > 0 && xz <= 0) {
                throw new AssertionError("Vi phạm bắc cầu");
            }
            if (xy == 0 && xz != yz) {
                throw new AssertionError("Nhóm compare==0 không nhất quán");
            }
        }
    }
}
```

*Đây là đoạn minh họa **không độc lập**; cần có `Task`, `samples` và `cmp` để chạy. Vòng lặp ba tầng có chi phí `O(n³)`, chỉ phù hợp với tập mẫu nhỏ trong kiểm thử.* Nó kiểm tra đối xứng dấu, bắc cầu theo chiều lớn hơn, và nhất quán với phần tử thứ ba cho những mẫu đã cung cấp.

Một bộ ca hữu ích nên bao gồm: các giá trị bằng nhau theo comparator nhưng không `equals()`, các phần tử ở gần ranh giới giữa nhóm, thứ tự đảo chiều, giá trị `Integer.MIN_VALUE/MAX_VALUE` nếu so số, dữ liệu có `null` nếu được cho phép, chuỗi khác chữ hoa/thường, và các khóa của `TreeSet`/`TreeMap` sau khi thêm. Kiểm tra trên mẫu không phải chứng minh toán học cho toàn bộ miền đầu vào, nên thiết kế comparator bằng các thành phần thứ tự chuẩn vẫn rất quan trọng.

## 12. Tự kiểm tra

**Câu 1.** Comparator trả `-20` cho `compare(a,b)` và `5` cho `compare(b,a)` có vi phạm quy tắc dấu không?  
**Đáp án:** Không; dấu lần lượt âm và dương. Không yêu cầu trị tuyệt đối trùng nhau.

**Câu 2.** Nếu `compare(a,b)==0`, `compare(a,c)<0` nhưng `compare(b,c)>0`, comparator hợp lệ không?  
**Đáp án:** Không. Hai phần tử cùng nhóm 0 phải có cùng quan hệ dấu với mọi `c`.

**Câu 3.** Có thể dùng Comparator chỉ so `priority` với `List.sort` khi muốn giữ các tác vụ cùng ưu tiên theo thứ tự đầu vào không?  
**Đáp án:** Có, với `List.sort` ổn định. Chúng vẫn là các phần tử riêng trong list; đây không phải là phép gộp.

**Câu 4.** Với `TreeSet<Task>` dùng comparator chỉ xét priority, thêm hai task khác mã nhưng cùng priority thì `size()` tăng hai lần không?  
**Đáp án:** Không. Comparator trả 0 nên `TreeSet` xem chúng tương đương.

**Câu 5.** `Comparator` có bắt buộc `compare(x,y)==0` khi và chỉ khi `x.equals(y)` không?  
**Đáp án:** Không bắt buộc trong mọi mục đích, nhưng đây là tính nhất quán với equals rất đáng quan tâm khi dùng trong sorted set/map.

**Câu 6.** `a - b` là cách triển khai comparator cho `int` có luôn đúng không?  
**Đáp án:** Không, vì overflow. Dùng `Integer.compare(a,b)` hoặc comparator factory.

**Câu 7.** Vì sao comparator có chu trình `A < B < C < A` không thể dùng an toàn để sort?  
**Đáp án:** Nó vi phạm tính bắc cầu nên không có thứ tự tổng quát nhất quán để thuật toán sắp xếp duy trì.

**Câu 8.** Khi bổ sung `thenComparing` sau tiêu chí đầu, nhóm `compare == 0` thay đổi ra sao?  
**Đáp án:** Một nhóm theo tiêu chí đầu có thể được chia thành các nhóm nhỏ theo tiêu chí tiếp. Những cặp vẫn bằng nhau theo mọi tiêu chí mới tiếp tục có `compare == 0`.

**Điểm cần nhớ:** Comparator hợp lệ tuân thủ **đối xứng về dấu, tính bắc cầu và nhất quán của các nhóm có `compare == 0`**. Kết quả bằng 0 mô tả **tương đương theo quy tắc sắp xếp**, không mặc nhiên là `equals()`; hệ quả này đặc biệt quan trọng khi dùng comparator làm chính sách khóa của `TreeSet` và `TreeMap`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparators-003">comparing() và các biến thể comparingInt(), comparingLong(), comparingDouble()</a>

<details>
<summary>Xem chi tiết</summary>

# `Comparator.comparing()` và các biến thể `comparingInt()`, `comparingLong()`, `comparingDouble()`

Khi cần sắp xếp danh sách đối tượng Java theo một thuộc tính, ta thường không cần tự viết đầy đủ `compare(a, b)`. Interface `java.util.Comparator<T>` cung cấp các **phương thức factory tĩnh** tạo comparator từ một hàm lấy khóa (*key extractor*):

- `Comparator.comparing(...)`: lấy khóa kiểu đối tượng, so sánh khóa theo thứ tự tự nhiên hoặc bằng một comparator khác.
- `Comparator.comparingInt(...)`: lấy khóa nguyên thủy `int`.
- `Comparator.comparingLong(...)`: lấy khóa nguyên thủy `long`.
- `Comparator.comparingDouble(...)`: lấy khóa nguyên thủy `double`.

Cả bốn đều tạo một `Comparator<T>` để truyền vào `List.sort(...)`, `Collections.sort(...)`, `Stream.sorted(...)`, `Arrays.sort(...)`, hoặc các API cần một thứ tự sắp xếp. Khác biệt chủ yếu nằm ở **kiểu khóa** và **cách so sánh khóa**.

## 1. `Comparator<T>` quyết định thứ tự như thế nào?

Các factory trong bài tạo một `Comparator<T>` bằng cách **lấy khóa** từ mỗi đối tượng và so sánh các khóa đó. Ví dụ:

~~~java
Comparator<Employee> byAge =
        Comparator.comparingInt(Employee::age);
~~~

Đây là mảnh mã giả định `Employee.age()` trả `int`. `Employee::age` tương đương `e -> e.age()` khi làm key extractor.

Kết quả `compare(a,b)` mang dấu âm nếu a đứng trước b, bằng 0 nếu ngang hàng theo khóa đã chọn, dương nếu a đứng sau b. Các quy tắc đối dấu, bắc cầu và nhóm so sánh bằng 0 vẫn áp dụng; phương thức factory không miễn trách nhiệm bảo đảm khóa hợp lệ và ổn định. Trọng tâm ở đây là **chọn overload đúng kiểu khóa**, bao gồm primitive, nullable và `double` có giá trị đặc biệt.
## 2. So sánh nhanh bốn phương thức factory

| Factory | Kiểu hàm lấy khóa | So sánh khóa | Trường hợp điển hình |
| --- | --- | --- | --- |
| `comparing(keyExtractor)` | `Function<? super T, ? extends U>`, với `U` có thứ tự tự nhiên | `U.compareTo(...)` qua `Comparable` | `String`, `LocalDate`, `BigDecimal`, enum… |
| `comparing(keyExtractor, keyComparator)` | `Function<? super T, ? extends U>` | Comparator riêng của khóa | Không phân biệt hoa thường, khóa nullable, thứ tự nghiệp vụ |
| `comparingInt(keyExtractor)` | `ToIntFunction<? super T>` | `Integer.compare(...)` | Tuổi, số lượng, mức ưu tiên `int` |
| `comparingLong(keyExtractor)` | `ToLongFunction<? super T>` | `Long.compare(...)` | Timestamp dạng `long`, số lượng lớn, sequence |
| `comparingDouble(keyExtractor)` | `ToDoubleFunction<? super T>` | `Double.compare(...)` | Điểm số, tỷ lệ, số đo `double` |

Ba hàm nguyên thủy cho phép extractor trả trực tiếp `int`, `long` hoặc `double`, tránh đóng hộp khóa đó thành `Integer`, `Long` hoặc `Double` để gọi `compareTo`. Điều này giúp biểu đạt rõ ý định và có thể giảm chi phí boxing khi sắp xếp nhiều phần tử. Không có nghĩa mọi phần của quá trình sort sẽ hoàn toàn không cấp phát bộ nhớ.

Lưu ý: `comparingInt`/`Long`/`Double` **không** có overload nhận thêm `Comparator` cho khóa nguyên thủy. Muốn quy tắc so khác tự nhiên, hãy cân nhắc biến đổi khóa có kiểm soát, `reversed()`, hoặc một comparator tự viết phù hợp. Riêng `comparing(...)` có overload nhận comparator thứ hai cho khóa kiểu đối tượng.

## 3. `comparing()` với khóa có `Comparable`

Giả sử `Product` có tên `String`:

~~~java
Comparator<Product> byName =
    Comparator.comparing(Product::name);
~~~

Đây là một mảnh mã giả định `Product.name()` trả `String`. Vì `String` triển khai `Comparable<String>`, comparator mới dùng thứ tự tự nhiên của chuỗi, dựa trên thứ tự so từ điển theo giá trị UTF-16; không mặc nhiên giống quy tắc sắp xếp tên tiếng Việt theo locale.

Một số ví dụ thông dụng:

~~~java
Comparator<Order> byDate =
    Comparator.comparing(Order::createdOn); // LocalDate

Comparator<Invoice> byAmount =
    Comparator.comparing(Invoice::total);   // BigDecimal
~~~

Các dòng này **chỉ minh họa cú pháp** nếu lớp `Order` và `Invoice` có phương thức tương ứng. `LocalDate` và `BigDecimal` cung cấp `compareTo()`, nên thích hợp cho overload một tham số.

Nếu kiểu khóa không triển khai `Comparable` phù hợp, overload `comparing(keyExtractor)` không thể dựa vào thứ tự tự nhiên; hãy truyền một `Comparator<U>` xác định quy tắc so khóa:

~~~java
Comparator<Product> byNameIgnoringCase =
    Comparator.comparing(
        Product::name,
        String.CASE_INSENSITIVE_ORDER
    );
~~~

Điều này cũng nhấn mạnh rằng comparator mô tả **thứ tự**, không nhất thiết định nghĩa bằng nhau giống `equals()`: hai chuỗi khác chữ hoa/thường có thể cho `compare(...) == 0` với `CASE_INSENSITIVE_ORDER` nhưng `String.equals()` vẫn trả `false`.

## 4. `comparingInt()`, `comparingLong()` và `comparingDouble()`

### `comparingInt(ToIntFunction)`

~~~java
Comparator<Employee> byAge =
    Comparator.comparingInt(Employee::age);
~~~

Nếu hai tuổi lần lượt là `22` và `30`, `byAge.compare(e22, e30)` là số âm. Không cần viết phép trừ `e22.age() - e30.age()`.

**Tránh so số nguyên bằng phép trừ:**

~~~java
// SAI: tràn số có thể đảo dấu kết quả.
Comparator<Employee> risky =
    (a, b) -> a.age() - b.age();

// ĐÚNG: Comparator.comparingInt dùng cách so sánh không tràn số.
Comparator<Employee> safe =
    Comparator.comparingInt(Employee::age);
~~~

Với `int`, phép trừ `Integer.MIN_VALUE - Integer.MAX_VALUE` có thể tràn và trả dấu sai; `Integer.compare` không mắc lỗi đó. Đoạn trên là mảnh mã minh họa trong phạm vi lớp `Employee`.

### `comparingLong(ToLongFunction)`

~~~java
Comparator<Event> byTimestamp =
    Comparator.comparingLong(Event::timestamp);
~~~

Dùng khi khóa là `long`, chẳng hạn epoch milliseconds, số bản ghi hoặc mã thứ tự lớn. Đặc biệt không ép hiệu hai `long` sang `int`:

~~~java
// SAI: vừa có nguy cơ tràn phép trừ long vừa có nguy cơ mất bit.
Comparator<Event> risky =
    (a, b) -> (int) (a.timestamp() - b.timestamp());
~~~

Cách viết `comparingLong` dùng `Long.compare` và bảo toàn thứ tự trên toàn miền `long`. Nếu dữ liệu là thời điểm, phải biết kiểu số đang lưu có cùng đơn vị và cùng quy ước thời gian; comparator không tự chuyển đổi giây sang mili giây.

### `comparingDouble(ToDoubleFunction)`

~~~java
Comparator<Measurement> byValue =
    Comparator.comparingDouble(Measurement::value);
~~~

Factory dùng `Double.compare`, có các đặc điểm cần nhớ:

- `-0.0` được xếp **trước** `+0.0` dù biểu thức số học `-0.0 == +0.0` là `true`.
- `Double.NaN` được xếp **sau** các giá trị số hữu hạn và các giá trị vô cực theo thứ tự tăng dần của `Double.compare`; các biểu diễn NaN được xem bằng nhau trong phép so này.
- `Double.NEGATIVE_INFINITY` đứng trước các số hữu hạn; `Double.POSITIVE_INFINITY` đứng sau các số hữu hạn nhưng trước NaN.

Vì vậy, không được tùy tiện thay `comparingDouble` bằng `(a, b) -> (int) (a.score() - b.score())`: ép một hiệu `double` sang `int` có thể biến chênh lệch nhỏ thành `0`, làm mất thứ tự mong muốn; NaN và vô cực cũng gây vấn đề.

Nếu nghiệp vụ cần loại `NaN` hoặc áp dụng ngưỡng gần đúng, cần nêu rõ chính sách xử lý. Đặc biệt, **không** coi hai số “chênh nhau dưới epsilon” là tương đương trong một comparator dùng để sắp xếp hay làm khóa `TreeSet` khi quan hệ đó không bắc cầu.

## 5. Ví dụ Java 17 hoàn chỉnh và đầu ra kiểm chứng được

Tạo tệp **`ComparatorFactoriesDemo.java`**:

~~~java
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ComparatorFactoriesDemo {

    record Applicant(String name, int age, long visits, double score) {}

    private static void printSorted(
            String label,
            List<Applicant> source,
            Comparator<Applicant> comparator) {
        String names = source.stream()
                .sorted(comparator)
                .map(Applicant::name)
                .collect(Collectors.joining(", "));
        System.out.println(label + ": " + names);
    }

    public static void main(String[] args) {
        List<Applicant> data = List.of(
                new Applicant("Lan", 22, 5L, 9.5),
                new Applicant("An", 30, 12L, 8.0),
                new Applicant("Binh", 22, 7L, 9.5),
                new Applicant("Mai", 30, 3L, -0.0),
                new Applicant("Hoa", 22, 15L, Double.NaN)
        );

        printSorted("name", data,
                Comparator.comparing(Applicant::name));

        printSorted("age then name", data,
                Comparator.comparingInt(Applicant::age)
                        .thenComparing(Applicant::name));

        printSorted("visits descending", data,
                Comparator.comparingLong(Applicant::visits)
                        .reversed());

        printSorted("score then name", data,
                Comparator.comparingDouble(Applicant::score)
                        .thenComparing(Applicant::name));

        List<Applicant> withNullName = List.of(
                new Applicant(null, 20, 1L, 0.0),
                new Applicant("Zed", 25, 2L, 1.0)
        );
        Comparator<Applicant> nullableName =
                Comparator.comparing(
                        Applicant::name,
                        Comparator.nullsFirst(
                                String.CASE_INSENSITIVE_ORDER));

        String nullableResult = withNullName.stream()
                .sorted(nullableName)
                .map(a -> a.name() == null ? "<null>" : a.name())
                .collect(Collectors.joining(", "));
        System.out.println("nullable names: " + nullableResult);

        Applicant low = new Applicant("MIN", 0, Long.MIN_VALUE, 0.0);
        Applicant high = new Applicant("MAX", 0, Long.MAX_VALUE, 0.0);
        Comparator<Applicant> safeLong =
                Comparator.comparingLong(Applicant::visits);
        System.out.println("long extremes ordered: "
                + (safeLong.compare(low, high) < 0));

        System.out.println("minus zero before plus zero: "
                + (Double.compare(-0.0, +0.0) < 0));
        System.out.println("NaN after positive infinity: "
                + (Double.compare(
                        Double.NaN, Double.POSITIVE_INFINITY) > 0));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ComparatorFactoriesDemo.java
java ComparatorFactoriesDemo
~~~

**Đầu ra chuẩn:**

~~~text
name: An, Binh, Hoa, Lan, Mai
age then name: Binh, Hoa, Lan, An, Mai
visits descending: Hoa, An, Binh, Lan, Mai
score then name: Mai, An, Binh, Lan, Hoa
nullable names: <null>, Zed
long extremes ordered: true
minus zero before plus zero: true
NaN after positive infinity: true
~~~

Giải thích:

1. `comparing(Applicant::name)` lấy `String` rồi so theo thứ tự tự nhiên; các tên được sắp tăng dần.
2. `comparingInt(Applicant::age)` đưa nhóm tuổi `22` lên trước nhóm `30`. `thenComparing(Applicant::name)` phá thế hòa theo tên: trong nhóm `22` là `Binh, Hoa, Lan`, trong nhóm `30` là `An, Mai`.
3. `comparingLong(Applicant::visits).reversed()` cho số lượt thăm giảm dần: `15, 12, 7, 5, 3`.
4. `comparingDouble(Applicant::score)` sắp `-0.0, 8.0, 9.5, 9.5, NaN`. `thenComparing(name)` xếp `Binh` trước `Lan` khi điểm bằng nhau.
5. Tên nullable được sắp với `Comparator.nullsFirst(...)` dành cho **khóa String**, nên khóa `null` đứng trước `"Zed"`.
6. Với hai giá trị `long` cực trị, factory dùng `Long.compare` nên nhận diện đúng thứ tự. Hai dòng cuối minh họa quy tắc `Double.compare` cho signed zero và NaN.

`record Applicant` được dùng như mô hình dữ liệu nhỏ để giảm code phụ; các phương thức accessor `name()`, `age()`, `visits()`, `score()` do Java sinh. Java 17 hỗ trợ record chính thức.

## 6. Ghép nhiều tiêu chí với `thenComparing...`

Một khóa đôi khi không phân biệt được hết các phần tử. Ví dụ sắp xếp theo `age` sẽ cho kết quả `0` với hai người cùng tuổi. Trong trường hợp đó, ta ghép tiêu chí phụ:

~~~java
Comparator<Applicant> order =
    Comparator.comparingInt(Applicant::age)
              .thenComparing(Applicant::name)
              .thenComparingLong(Applicant::visits)
              .thenComparingDouble(Applicant::score);
~~~

Đây là mảnh mã sử dụng `Applicant` từ ví dụ hoàn chỉnh. Luồng đánh giá:

1. So `age`. Nếu khác, trả ngay theo tuổi.
2. Nếu bằng tuổi, so `name`.
3. Nếu vẫn bằng tên, so `visits`.
4. Nếu vẫn bằng, so `score`.

Các phương thức `thenComparingInt`, `thenComparingLong`, `thenComparingDouble` phù hợp cho tiêu chí phụ nguyên thủy và hoạt động tương tự các factory tương ứng. Comparator sẽ không cần đến tiêu chí sau nếu tiêu chí trước đã xác định được thứ tự.

### Đảo chiều đúng phạm vi

~~~java
// Đảo toàn bộ comparator: tuổi giảm, rồi tên giảm.
Comparator<Applicant> allDescending =
    Comparator.comparingInt(Applicant::age)
              .thenComparing(Applicant::name)
              .reversed();

// Tuổi giảm nhưng tên vẫn tăng khi cùng tuổi.
Comparator<Applicant> ageDescNameAsc =
    Comparator.comparingInt(Applicant::age)
              .reversed()
              .thenComparing(Applicant::name);

// Tuổi tăng, tên giảm khi cùng tuổi.
Comparator<Applicant> ageAscNameDesc =
    Comparator.comparingInt(Applicant::age)
              .thenComparing(
                  Comparator.comparing(Applicant::name).reversed()
              );
~~~

Các đoạn dùng `Applicant` ở ví dụ trên, không phải tệp hoàn chỉnh riêng. `reversed()` đảo kết quả của **toàn bộ comparator đã ghép tại thời điểm gọi**. Muốn chỉ đảo chiều một khóa, hãy đảo comparator riêng của khóa đó rồi mới ghép. Tránh nhầm rằng `reversed()` luôn chỉ đảo “cột đầu tiên”.

Nếu chỉ cần sắp xếp một danh sách mà không thay đổi danh sách ban đầu, `stream().sorted(comparator).toList()` (Java 16+) tạo danh sách kết quả theo thứ tự; `List.sort(comparator)` thay đổi danh sách đích và không dùng được trên danh sách không cho sửa cấu trúc do `List.of(...)` tạo. Java bảo đảm `List.sort` ổn định; với các phần tử có `compare(a,b)==0`, thứ tự tương đối ban đầu được giữ. Tuy nhiên, khi nghiệp vụ cần một thứ tự hoàn toàn xác định, nên thêm tiêu chí phụ rõ ràng, đặc biệt trước khi phân trang hoặc xuất báo cáo.

## 7. Xử lý `null` cho đúng tầng

Các factory mặc định thường **không tự xử lý khóa null**. Nếu một extractor trả `null` và comparator khóa không hỗ trợ null, phép sắp xếp có thể ném `NullPointerException`.

Có hai tình huống khác nhau:

**A. Chính phần tử cần sắp xếp là `null`:**

~~~java
Comparator<Applicant> byAgeAllowNullApplicant =
    Comparator.nullsLast(
        Comparator.comparingInt(Applicant::age)
    );
~~~

Tầng `nullsLast` bên ngoài giúp phần tử `Applicant` bằng `null` đi cuối. Nếu phần tử không null, comparator bên trong mới truy cập `age()`.

**B. Phần tử không null, nhưng khóa `name()` có thể null:**

~~~java
Comparator<Applicant> byNullableName =
    Comparator.comparing(
        Applicant::name,
        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
    );
~~~

`nullsLast` ở đây xử lý **giá trị String do extractor trả về**, không tự xử lý phần tử `Applicant` null. Nếu cả bản thân phần tử và khóa đều có thể null, có thể bọc hai tầng với các chính sách thích hợp.

Với primitive extractor như `ToIntFunction`, kết quả là `int` nên không thể là `null`. Nhưng nếu code viết `Comparator.comparingInt(e -> e.getNullableAge())` và `getNullableAge()` trả `Integer`, Java phải unbox; giá trị null sẽ gây `NullPointerException`. Hãy xác định chính sách cho trường nullable trước: dùng overload `comparing(...)` với comparator khóa cho null, chuyển về giá trị mặc định có ý nghĩa, hoặc từ chối dữ liệu thiếu.

## 8. Thứ tự của comparator và `equals()` là hai khái niệm khác nhau

`Comparator.compare(a, b) == 0` nghĩa là **tương đương trong thứ tự do comparator xác định**; nó không nhất thiết đồng nghĩa `a.equals(b)`. Chẳng hạn hai `Applicant` bằng tuổi nhưng khác tên có thể cho `0` khi dùng `comparingInt(Applicant::age)`.

Điều này đặc biệt quan trọng nếu sử dụng comparator với `TreeSet` và `TreeMap`:

~~~java
Comparator<Applicant> byAge =
    Comparator.comparingInt(Applicant::age);
~~~

Đoạn trên có thể là tiêu chí sắp xếp rất hữu ích cho `List`. Nhưng nếu đặt vào `TreeSet<Applicant>`, các ứng viên **cùng tuổi** sẽ được cấu trúc cây coi là trùng nhau theo comparator, dù tên và các thuộc tính khác khác nhau. `TreeMap` cũng xác định trùng khóa bằng kết quả `compare==0` của comparator, không dựa vào `equals()` theo cách `HashMap` làm.

Nếu yêu cầu là giữ tất cả ứng viên riêng biệt trong `TreeSet`, hãy thêm tiêu chí phân biệt duy nhất, ổn định, nhất quán với ngữ nghĩa tập hợp mong muốn, ví dụ ID nghiệp vụ:

~~~java
// Giả định kiểu Applicant thực tế có id() là khóa phân biệt ổn định.
Comparator<Applicant> byAgeThenId =
    Comparator.comparingInt(Applicant::age)
              .thenComparing(Applicant::id);
~~~

Đây chỉ là mảnh mã để diễn tả quy tắc; `Applicant` của ví dụ chạy ở mục 5 chưa có trường `id()`. **Không** tạo một comparator trả kết quả khác nhau thất thường chỉ nhằm giữ mọi phần tử: comparator vẫn phải thỏa tính bắc cầu và nhất quán.

Ngoài ra, sắp xếp tăng dần theo `BigDecimal` bằng `Comparator.comparing(Invoice::total)` dùng `BigDecimal.compareTo`. Phương thức này có thể cho `0` giữa `1.0` và `1.00` dù `BigDecimal.equals()` cho `false`. Điều đó được chấp nhận khi chỉ **sort**, nhưng cần cân nhắc kỹ khi dùng comparator này làm quy tắc trùng khóa trong `TreeMap` hoặc `TreeSet`.

## 9. Lưu ý về hiệu năng và tính đúng đắn

- **Ưu tiên method reference rõ nghĩa:** `Comparator.comparingInt(Person::age)` thường dễ đọc hơn một lambda tự trừ hai tuổi.
- **Chọn đúng kiểu khóa:** nếu getter trả `long`, dùng `comparingLong` thay vì ép sang `int`; nếu trả `double`, hiểu chính sách NaN/signed zero của `Double.compare`.
- **Giữ extractor thuần và ổn định:** không sửa trạng thái object, không phát sinh giá trị ngẫu nhiên, và không phụ thuộc dữ liệu thay đổi giữa các lần so sánh trong cùng một thao tác sort. Comparator có thể gọi extractor nhiều lần và với những cặp khác nhau.
- **Không dùng phép gần đúng làm quan hệ so bằng nhau:** so theo sai số `epsilon` thường thiếu tính bắc cầu; nên tách thành phép kiểm tra riêng thay vì gài vào comparator sắp xếp.
- **Không kỳ vọng caching khóa:** factory không hứa gọi extractor đúng một lần trên mỗi phần tử. Nếu tính khóa quá tốn kém, có thể tạo bản ghi mang khóa đã tính (*decorate-sort-undecorate*) hoặc một mô hình khóa ổn định riêng.
- **Không lạm dụng `reversed()`:** ghi rõ chiều từng tiêu chí khi ghép nhiều khóa và kiểm tra bằng ví dụ có giá trị trùng ở khóa chính.

## 10. Checklist khi áp dụng

Trước khi chọn comparator factory, kiểm tra:

1. Khóa đang sắp xếp là đối tượng có `Comparable`, đối tượng cần comparator riêng, hay primitive `int`/`long`/`double`?
2. Nếu dùng `comparing(...)`, khóa có thực sự hỗ trợ so sánh tự nhiên? Nếu không, đã truyền `keyComparator` chưa?
3. Có `null` ở **phần tử** hay ở **khóa**? Đã đặt `nullsFirst`/`nullsLast` đúng tầng chưa?
4. `double` có thể mang NaN, vô cực hoặc `-0.0` không? Thứ tự của `Double.compare` có phù hợp nghiệp vụ không?
5. Có cần ghép tiêu chí phụ bằng `thenComparing(...)` để sắp xếp xác định trong nhóm trùng khóa không?
6. `reversed()` đang đảo toàn bộ comparator hay chỉ tiêu chí muốn đảo?
7. Comparator có được dùng trong `TreeSet`/`TreeMap` không? Nếu có, `compare(a,b)==0` có đúng ý nghĩa “trùng phần tử/khóa” mong muốn không?
8. Các trường lấy khóa có ổn định trong quá trình sort hoặc trong thời gian tồn tại trong cấu trúc cây không?

**Ghi nhớ:** `comparing()` dành cho **khóa đối tượng** (dùng `Comparable` hoặc một comparator khóa riêng). `comparingInt()`, `comparingLong()` và `comparingDouble()` dành cho **khóa primitive**, biểu đạt rõ kiểu và tránh boxing khóa không cần thiết. Dùng `thenComparing...` để ghép tiêu chí, `reversed()` để đảo thứ tự, và luôn xác định chính xác quy tắc null, số thực cũng như ngữ nghĩa `compare==0` trong các collection có sắp xếp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparators-004">thenComparing() để tạo thứ tự theo nhiều khóa</a>

<details>
<summary>Xem chi tiết</summary>

# `Comparator.thenComparing()`: tạo thứ tự theo nhiều khóa trong Java

## 1. Bài toán và nguyên tắc thứ tự ưu tiên

Khi sắp xếp danh sách đối tượng, một trường thường không đủ để quyết định vị trí. Ví dụ, yêu cầu của hệ thống nhân sự có thể là:

1. Sắp xếp **phòng ban tăng dần**.
2. Trong cùng phòng ban, sắp xếp **cấp bậc giảm dần**.
3. Nếu cấp bậc bằng nhau, sắp xếp **tên tăng dần**.
4. Nếu vẫn bằng nhau, sắp xếp **mã nhân viên tăng dần** để có kết quả xác định.

Đây là thứ tự **nhiều khóa** (*multi-key sorting*), hay so sánh kiểu từ điển (*lexicographic ordering*): xét khóa đầu tiên trước; chỉ khi hai giá trị bằng nhau theo comparator đang dùng mới chuyển sang khóa kế tiếp.

Trong Java, `Comparator<T>` diễn tả quy tắc thứ tự của hai đối tượng `T`. Kết quả của `compare(a, b)` là:

- Số âm: `a` đứng trước `b`.
- Số không: `a` và `b` được xem là **tương đương theo thứ tự sắp xếp**.
- Số dương: `a` đứng sau `b`.

Giá trị trả về **không** bắt buộc là `-1`, `0` hoặc `1`; chỉ dấu âm/không/dương có ý nghĩa. Quan hệ “`compare(a, b) == 0`” không tự động có nghĩa “`a.equals(b)`”.

Phương thức `thenComparing()` ghép các comparator theo quy tắc đó, giúp code giữ nguyên **thứ tự ưu tiên khóa** mà không cần nhiều khối `if`.

## 2. Cơ chế của `thenComparing()`

`Comparator.comparing(keyExtractor)` tạo comparator dựa trên một khóa có thể so sánh tự nhiên (*natural order*) như `String` hoặc `LocalDate`. `thenComparing(nextComparator)` thêm một bộ so sánh dự phòng khi comparator trước trả về `0`.

Ví dụ:

```java
Comparator<Person> byLastThenFirst =
    Comparator.comparing(Person::lastName)
              .thenComparing(Person::firstName);
```

Đây là mảnh code minh họa; giả sử có lớp `Person` với hai getter `lastName()` và `firstName()` trả giá trị `String` khác `null`, cùng `import java.util.Comparator;`.

Khi so hai người:

1. `lastName` khác nhau → dùng ngay kết quả khóa họ, **không cần xét** tên.
2. `lastName` được comparator coi bằng nhau → xét `firstName`.
3. Cả hai khóa đều tương đương → kết quả so sánh là `0`.

Diễn đạt gần với thuật toán (chỉ để hiểu cơ chế):

```java
// Logic tương đương về ý nghĩa, không phải mã bắt buộc viết tay.
int primary = byLastName.compare(a, b);
if (primary != 0) {
    return primary;
}
return byFirstName.compare(a, b);
```

Việc nối nhiều khóa tạo một comparator **mới**. `thenComparing()` không thay đổi comparator đã có, vì thế phải dùng giá trị được trả về khi xây chuỗi so sánh.

### 2.1. Các overload hay dùng

| Cách gọi | Dùng khi nào | Ví dụ ý nghĩa |
| --- | --- | --- |
| `thenComparing(Comparator<? super T>)` | Đã có comparator cho toàn đối tượng | Sau khóa chính, dùng comparator cấp bậc giảm dần |
| `thenComparing(Function<? super T, ? extends U>)` | Khóa phụ có kiểu hỗ trợ natural order | Sau họ, so tên |
| `thenComparing(Function, Comparator<? super U>)` | Khóa phụ cần quy tắc riêng: `null`, không phân biệt hoa/thường, đảo chiều | Sau đội, so tên theo `String.CASE_INSENSITIVE_ORDER` |
| `thenComparingInt(ToIntFunction<? super T>)` | Khóa số nguyên `int` | Sau tên, so ID dạng `int` |
| `thenComparingLong(ToLongFunction<? super T>)` | Khóa `long` | Sau trạng thái, so thời điểm dạng epoch |
| `thenComparingDouble(ToDoubleFunction<? super T>)` | Khóa `double` | Sau nhóm, so điểm số |

Các phương thức tiện ích cho primitive tránh boxing không cần thiết. Ở khóa đầu tiên cũng có `Comparator.comparingInt`, `comparingLong` và `comparingDouble`.

**Chú ý:** `Comparator.comparing(Person::name)` giả định khóa có `Comparable` và có chính sách cho `null` phù hợp. Nếu khóa có thể `null`, dùng comparator xử lý null cụ thể; không tự mặc định phép so sánh tự nhiên chấp nhận `null`.

## 3. Ví dụ Java 17 hoàn chỉnh: bốn khóa, hai hướng sắp xếp

Định nghĩa dữ liệu:

- `department`: String, A → Z.
- `level`: int, lớn hơn đứng trước.
- `name`: String, A → Z.
- `employeeId`: int, nhỏ hơn đứng trước.
- `salary`: long, thông tin phụ **không tham gia** thứ tự đang chọn.

Một kỹ thuật rõ nghĩa khi có khóa phụ giảm dần là truyền **comparator phụ đã đảo chiều** vào `thenComparing`, thay vì gọi `reversed()` lên toàn bộ chuỗi.

Lưu vào tệp **`MultiKeyComparatorDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class MultiKeyComparatorDemo {
    record Employee(String department, String name,
                    int level, long salary, int employeeId) {}

    public static void main(String[] args) {
        List<Employee> employees = new ArrayList<>(List.of(
            new Employee("Core", "An",   3, 100, 102),
            new Employee("Core", "Binh", 5, 125, 103),
            new Employee("Core", "An",   5, 130, 101),
            new Employee("Web",  "An",   4, 120, 104),
            new Employee("Core", "An",   5, 140, 105)
        ));

        Comparator<Employee> byDepartmentLevelNameId =
            Comparator.comparing(Employee::department)
                .thenComparing(
                    Comparator.comparingInt(Employee::level).reversed()
                )
                .thenComparing(Employee::name)
                .thenComparingInt(Employee::employeeId);

        employees.sort(byDepartmentLevelNameId);

        for (Employee employee : employees) {
            System.out.printf("%s | %d | %s | %d%n",
                employee.department(),
                employee.level(),
                employee.name(),
                employee.employeeId());
        }

        Employee original =
            new Employee("Core", "An", 5, 130, 101);
        Employee otherSalary =
            new Employee("Core", "An", 5, 999, 101);

        System.out.println("Comparator sees tie: "
            + (byDepartmentLevelNameId.compare(original, otherSalary) == 0));
        System.out.println("Record equals: " + original.equals(otherSalary));

        TreeSet<Employee> sortedSet =
            new TreeSet<>(byDepartmentLevelNameId);
        sortedSet.add(original);
        sortedSet.add(otherSalary);
        System.out.println("TreeSet size: " + sortedSet.size());
    }
}
```

Biên dịch và chạy bằng **JDK 17+** trong thư mục chứa tệp:

```bash
javac MultiKeyComparatorDemo.java
java MultiKeyComparatorDemo
```

Kết quả chuẩn:

```text
Core | 5 | An | 101
Core | 5 | An | 105
Core | 5 | Binh | 103
Core | 3 | An | 102
Web | 4 | An | 104
Comparator sees tie: true
Record equals: false
TreeSet size: 1
```

**Giải thích thứ tự:**

1. Các nhân viên `Core` đứng trước `Web` vì `department` tăng dần; cấp bậc của `Web` không được dùng để vượt lên trên khác phòng ban.
2. Trong `Core`, cấp `5` đứng trước cấp `3` do comparator `comparingInt(Employee::level).reversed()`.
3. Trong nhóm `Core` cấp `5`, tên `An` đứng trước `Binh`.
4. Hai nhân viên `An` ở cùng `Core`, cấp `5` được phân biệt bằng `employeeId`: `101` trước `105`.
5. `salary` không được đưa vào comparator. Hai record `original` và `otherSalary` chỉ khác salary nên comparator trả `0`, nhưng `record.equals` so **tất cả component** và cho `false`.
6. `TreeSet` xem hai phần tử tương đương theo comparator, vì vậy chỉ giữ một phần tử. Kết quả `TreeSet size: 1` là lời nhắc rằng **comparator còn quyết định tính trùng trong các tập hợp có thứ tự**, không chỉ thứ tự hiển thị.

Trong yêu cầu chỉ sắp xếp danh sách, salary có thể không thuộc quy tắc thứ tự. Nhưng nếu comparator được dùng làm định nghĩa khóa trong `TreeSet` hoặc `TreeMap`, bỏ qua trường quan trọng có thể vô tình gộp những đối tượng mà `equals()` coi khác nhau.

## 4. Viết khóa tăng dần và giảm dần cho đúng vị trí

### 4.1. Đảo chiều đúng **khóa phụ**

Để khóa `department` tăng dần nhưng `level` giảm dần:

```java
Comparator<Employee> order =
    Comparator.comparing(Employee::department)
        .thenComparing(
            Comparator.comparingInt(Employee::level).reversed()
        );
```

Đây là mảnh code minh họa dùng `Employee` từ chương trình hoàn chỉnh. Comparator phụ `comparingInt(...).reversed()` chỉ đảo thứ tự `level` trong nhóm có department bằng nhau.

### 4.2. `reversed()` ở cuối đảo **toàn bộ comparator**

Cần phân biệt:

```java
Comparator<Employee> allReversed =
    Comparator.comparing(Employee::department)
        .thenComparingInt(Employee::level)
        .reversed();
```

Ở đây `reversed()` được gọi trên kết quả **cả chuỗi** `department` rồi `level`, nên **cả department và level** cùng bị đảo. Nó không chỉ đảo trường gần nhất.

Nếu yêu cầu là “department tăng, level giảm”, cách viết ở 4.1 mới diễn tả đúng. Nếu yêu cầu là “cả hai khóa giảm”, `allReversed` là phù hợp.

### 4.3. Thay đổi thứ tự chuỗi sẽ đổi ưu tiên

Hai biểu thức này không tương đương:

```java
Comparator<Employee> byDepartmentThenLevel =
    Comparator.comparing(Employee::department)
        .thenComparingInt(Employee::level);

Comparator<Employee> byLevelThenDepartment =
    Comparator.comparingInt(Employee::level)
        .thenComparing(Employee::department);
```

Khóa **nằm trước** luôn có quyền ưu tiên cao hơn. Chuyển vị trí `thenComparing` không phải tái cấu trúc code vô hại mà là thay đổi luật nghiệp vụ.

### 4.4. Thêm khóa cuối để có kết quả quyết định

Nếu chỉ dùng `department`, `level` và `name`, các đối tượng có cùng ba khóa sẽ có `compare == 0`. `List.sort` và `Collections.sort` được Java quy định **sắp xếp ổn định** (*stable*): phần tử được xem bằng nhau theo comparator giữ thứ tự tương đối ban đầu. Tuy nhiên, nếu thứ tự đầu vào đến từ API, database hoặc HashSet không có thứ tự ổn định, kết quả có thể khác giữa các lần lấy dữ liệu.

Thêm một khóa ổn định, duy nhất theo miền như `employeeId` (nếu đúng là duy nhất trong phạm vi đang sắp xếp) giúp thứ tự có tính quyết định. Không tự thêm tiêu chí mà nghiệp vụ không chấp nhận, và đừng dùng `hashCode` hay `identityHashCode` làm khóa cuối để kỳ vọng duy nhất hoặc ổn định xuyên JVM.

## 5. Khóa `null`: đặt trước hay sau phải rõ ràng

Các extractor của `Comparator.comparing` có thể trả về `null`. Nếu comparator khóa không xử lý null, so sánh thường phát sinh `NullPointerException` khi gặp giá trị null.

Để giá trị `name == null` đứng **sau** tên không null:

```java
Comparator<Employee> byNameNullsLast =
    Comparator.comparing(
        Employee::name,
        Comparator.nullsLast(Comparator.naturalOrder())
    );
```

Để `department` tăng dần và `name` null đứng cuối **trong mỗi department**:

```java
Comparator<Employee> byDepartmentThenName =
    Comparator.comparing(Employee::department)
        .thenComparing(
            Employee::name,
            Comparator.nullsLast(Comparator.naturalOrder())
        );
```

Đây là mảnh code minh họa dùng `Employee` có `name()` trả String. Nếu bản thân `Employee` có thể `null` (không chỉ field bên trong null), có thể bọc **comparator toàn đối tượng** bằng `Comparator.nullsFirst(...)` hoặc `nullsLast(...)`. Cần phân biệt hai chính sách:

- `Comparator.comparing(Employee::name, nullsLast(...))` xử lý **khóa name null**.
- `Comparator.nullsLast(byDepartmentThenName)` xử lý **cả phần tử Employee null**.

Đặt `nullsLast` ở vị trí nào sẽ quyết định phạm vi áp dụng. Nếu đảo chiều một comparator đã `nullsLast` bằng `reversed()`, quy tắc null cũng bị đảo theo: `nullsLast(...).reversed()` đưa null lên đầu. Nếu mong muốn tên giảm dần nhưng null vẫn cuối, hãy viết:

```java
Comparator<String> descendingWithNullLast =
    Comparator.nullsLast(Comparator.reverseOrder());
```

Dùng comparator này cho **khóa String** phù hợp bằng overload `thenComparing(Employee::name, descendingWithNullLast)`.

Lưu ý: `Comparator.nullsFirst(null)` và `nullsLast(null)` vẫn chấp nhận comparator khóa `null` theo API Java và coi các giá trị không null là tương đương; trường hợp này chỉ hữu ích khi thật sự muốn bỏ qua thứ tự giữa các giá trị không null.

## 6. So chuỗi theo chính sách phù hợp

### 6.1. Phân biệt hay không phân biệt chữ hoa/thường?

`String` sắp xếp tự nhiên theo thứ tự từ điển dựa trên mã ký tự Unicode (UTF-16 code units), không tự động phù hợp thứ tự chữ cái tiếng Việt hay yêu cầu không phân biệt hoa/thường.

Với mã kỹ thuật hoặc tên cần so không phân biệt hoa/thường, bạn có thể dùng:

```java
Comparator<Employee> byDepartmentThenNameIgnoreCase =
    Comparator.comparing(Employee::department)
        .thenComparing(
            Employee::name,
            String.CASE_INSENSITIVE_ORDER
        );
```

Nhưng `CASE_INSENSITIVE_ORDER` không cam kết trùng với bộ quy tắc sắp xếp ngôn ngữ tự nhiên ở mọi locale. Nó cũng có thể trả `0` cho hai chuỗi khác nhau theo `String.equals`. Khi sắp xếp hiển thị, thêm một khóa phụ phân biệt chữ hoa/thường hoặc ID ổn định nếu cần tổng thứ tự cụ thể.

### 6.2. Tên tiếng Việt và `Collator`

Nếu giao diện cần thứ tự phù hợp với quy tắc ngôn ngữ, cân nhắc `java.text.Collator` và `Locale` thích hợp. Một ví dụ minh họa:

```java
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

// Ví dụ minh họa, biến Employee từ chương trình mẫu.
Collator vietnamese = Collator.getInstance(
    Locale.forLanguageTag("vi-VN")
);
Comparator<Employee> byVietnameseName =
    Comparator.comparing(Employee::name, vietnamese);
```

`Collator` có cấu hình mức độ so sánh và hành vi phụ thuộc dữ liệu locale của môi trường Java. Nếu cần kết quả hoàn toàn cố định qua môi trường, phải xác định chính sách collation và phiên bản nguồn dữ liệu; đừng dự đoán một danh sách tên có dấu sẽ ra cùng thứ tự trong mọi cấu hình.

Các đối tượng `Collator` là mutable và không đảm bảo an toàn luồng khi cùng một instance bị dùng đồng thời mà có thay đổi. Khi cần chia sẻ giữa threads, hãy dùng phương án phù hợp như mỗi luồng có bản riêng hoặc kiểm soát truy cập, đồng thời không thay đổi quy tắc trong quá trình sắp xếp.

### 6.3. Giá trị khóa được chuẩn hóa

Nếu code dùng `String.toLowerCase()` làm khóa phụ, cần xác định locale và null policy. Với mã định danh kỹ thuật có quy tắc case-insensitive, dùng `Locale.ROOT` khi phù hợp sẽ rõ hơn việc dựa vào locale mặc định máy chạy. Tránh tạo nhiều biểu diễn chuẩn hóa khác nhau giữa sorting, `equals`, hash và tra cứu nghiệp vụ.

## 7. Khóa số, ngày giờ và các comparator chuyên biệt

Với số nguyên, sử dụng `comparingInt` / `thenComparingInt`; với `long`, sử dụng `comparingLong` / `thenComparingLong`; với `double`, sử dụng `comparingDouble` / `thenComparingDouble`. Các overload primitive tiết kiệm boxing và tránh tự trừ số để so sánh.

Ví dụ mã minh họa cho class `Event` có `LocalDateTime createdAt()` và `long sequence()`:

```java
Comparator<Event> newestFirstThenSequence =
    Comparator.comparing(Event::createdAt, Comparator.reverseOrder())
        .thenComparingLong(Event::sequence);
```

Đoạn này cần `java.util.Comparator` và class `Event` tương ứng; không phải chương trình biên dịch độc lập. Với ngày giờ, cần chọn đúng kiểu biểu diễn: `LocalDateTime` không mang múi giờ; `Instant` phù hợp hơn khi mục tiêu là thứ tự thời điểm tuyệt đối.

**Tránh cách viết dễ lỗi này:**

```java
// KHÔNG nên dùng cho int lớn: có thể tràn số.
Comparator<Employee> risky =
    (left, right) -> left.employeeId() - right.employeeId();
```

Nếu `left.employeeId() == Integer.MIN_VALUE` và `right.employeeId() == 1`, phép trừ tràn `int` có thể cho dấu sai, làm comparator vi phạm thứ tự. Dùng `Integer.compare(left.employeeId(), right.employeeId())` hoặc `Comparator.comparingInt(Employee::employeeId)`.

Với `double`, `Double.compare` và `comparingDouble` có quy tắc tổng thứ tự cho NaN và số 0 có dấu phù hợp API Java; nếu miền nghiệp vụ muốn xử lý NaN hoặc `-0.0` khác đi, phải nêu rõ chính sách và kiểm thử. Comparator nhiều khóa không tự xác định quy tắc đó.

## 8. `thenComparing()` và hợp đồng của `Comparator`

Ghép nhiều khóa có ý nghĩa như so từ điển: khóa chính quyết định trước, khóa phụ chỉ được dùng khi khóa chính trả `0`. Các comparator thành phần phải có quan hệ thứ tự hợp lệ, ổn định; việc nối chúng không khắc phục một comparator có chu kỳ, phụ thuộc random hoặc dữ liệu đang bị sửa.

Hai khái niệm cần tách trong ứng dụng:

- **Khóa sắp xếp:** chuỗi khóa `department → level → name → employeeId` có thể cho `compare(a,b)==0` khi hai đối tượng ngang nhau trên tất cả các khóa ấy.
- **Định danh dữ liệu:** `equals(a,b)` của một record còn có thể xét trường khác như `salary`. Trong ví dụ ở mục 3, hai record khác salary vẫn so bằng 0.

`List.sort` giữ cả hai bản ghi ngang hàng. `TreeSet` và `TreeMap` dùng kết quả bằng 0 làm quan hệ trùng khóa, vì vậy phải quyết định **khóa cuối cùng có cần phân biệt các bản ghi theo `equals` hay không**. Nếu entity định danh chỉ bằng ID nhưng comparator dùng trường hiển thị thay đổi trước ID, thêm ID ở cuối vẫn chưa chắc nhất quán với `equals`. Bài về quan hệ bằng nhau đi sâu vào hai chiều sai lệch này.
## 9. Hiệu năng và thời điểm trích xuất khóa

Comparator dạng chuỗi có thể đọc nhiều trường qua getter. `thenComparing` chỉ xét khóa phụ **khi khóa trước hòa** nên thứ tự trường cũng có thể ảnh hưởng số lần gọi extractor, nhưng hãy ưu tiên đúng yêu cầu nghiệp vụ trước.

Với extractor tốn kém, ví dụ chuẩn hóa tên, chuyển định dạng ngày, truy vấn nguồn ngoài hoặc tính toán dài, bộ sắp xếp có thể gọi chúng **nhiều lần**. Hướng thiết kế đáng cân nhắc là tiền tính khóa vào một đối tượng bất biến/record, sau đó sort theo các khóa đã tính. Tránh gọi API bên ngoài, thay đổi trạng thái hay phát sinh hiệu ứng phụ trong comparator.

`List.sort` và `Collections.sort` không chỉ gọi comparator đúng một lần trên mỗi phần tử. Comparator cần là **pure** theo nghĩa kết quả cho một cặp không đổi trong suốt lượt sort. Sửa các field được dùng làm khóa đồng thời với sort có thể gây lỗi, thậm chí vi phạm hợp đồng comparator.

`thenComparingInt`, `thenComparingLong`, `thenComparingDouble` phù hợp cho primitive keys; không phải mọi lambda đều cần tối ưu thủ công. Nếu bộ dữ liệu nhỏ, biểu thức comparator rõ ràng thường quan trọng hơn vài cấp phát nhỏ.

## 10. Những lỗi phổ biến

| Lỗi | Kết quả có thể xảy ra | Cách sửa |
| --- | --- | --- |
| Đặt `reversed()` ở cuối chuỗi, trong khi chỉ muốn đảo khóa cuối | Đảo toàn bộ khóa đã ghép | Đảo comparator của riêng khóa tương ứng rồi truyền vào `thenComparing` |
| Đổi thứ tự các lời gọi `thenComparing` | Đổi độ ưu tiên nghiệp vụ | Viết thứ tự khóa theo yêu cầu bằng lời trước |
| Bỏ khóa phân biệt cuối | Nhiều đối tượng có `compare == 0`; TreeSet gộp dữ liệu | Cân nhắc ID ổn định phù hợp miền |
| Dùng `String.CASE_INSENSITIVE_ORDER` rồi coi `compare == 0` tương đương `String.equals` | Hai chuỗi khác viết hoa/thường bị xem là một trong sorted set | Tách semantics hiển thị khỏi semantics nhận diện |
| Quên chính sách `null` | Ném `NullPointerException` trong sort | Sử dụng `nullsFirst/nullsLast` đúng phạm vi |
| Dùng subtraction `a.id() - b.id()` | Tràn số làm đảo dấu hoặc phá hợp đồng | Dùng `comparingInt` hay `Integer.compare` |
| Dùng comparator phụ trên mutable state | Kết quả không nhất quán giữa các lượt so | Dùng khóa ổn định trong quá trình sort và lưu collection có thứ tự |
| Dùng comparator có hiệu ứng phụ/random | Vi phạm bắc cầu hoặc tính nhất quán | Biến comparator thành phép so sánh xác định |
| Mặc định thứ tự tự nhiên của String là thứ tự ngôn ngữ Việt | Kết quả hiển thị không phù hợp | Chọn collation theo locale hoặc quy tắc ứng dụng |
| Giả định `thenComparing()` sửa comparator gốc tại chỗ | Bỏ quên comparator phụ vì không giữ giá trị trả về | Gán kết quả chuỗi vào một biến comparator mới |

## 11. Bài tập tự kiểm tra

**Câu 1.** Cho một lớp `Order` có `status()` trả String, `priority()` trả int. Yêu cầu: trạng thái tăng dần, trong cùng trạng thái ưu tiên lớn hơn trước. Viết comparator thế nào?

**Đáp án (mảnh code):**

```java
Comparator<Order> order =
    Comparator.comparing(Order::status)
        .thenComparing(
            Comparator.comparingInt(Order::priority).reversed()
        );
```

### Câu 2. Vì sao `compare(a, b) == 0` không đồng nghĩa `a.equals(b)`?

Vì comparator chỉ dựa vào tập khóa được chọn; `equals` có thể dựa vào tập trường khác. Ví dụ một record có salary khác nhưng comparator chỉ xét department, level, name và employeeId. Trong `TreeSet`, điều này còn ảnh hưởng đến số phần tử được lưu.

### Câu 3. `Comparator.comparing(Employee::department).thenComparingInt(Employee::level).reversed()` đảo khóa nào?

Đảo **cả chuỗi department rồi level**, vì `reversed()` được gọi trên comparator đã ghép. Nếu chỉ muốn level giảm, hãy đảo `Comparator.comparingInt(Employee::level)` bên trong `thenComparing(...)`.

### Câu 4. Nếu tên có thể `null` và cần null cuối nhưng tên còn lại giảm dần, dùng gì?

```java
Comparator<Employee> byName =
    Comparator.comparing(
        Employee::name,
        Comparator.nullsLast(Comparator.reverseOrder())
    );
```

Với comparator cho **khóa tên** như trên, null đứng sau các tên khác; những tên không null được sắp giảm dần.

### Câu 5. Một `TreeSet` dùng comparator chỉ dựa trên `age`. Hai object tuổi `20` nhưng ID khác nhau sẽ thế nào?

Cả hai có `compare == 0`, nên `TreeSet` coi chúng tương đương và chỉ giữ một phần tử. Nếu muốn lưu cả hai, comparator cần thêm khóa phân biệt thích hợp như ID.

### Câu 6. `thenComparingInt` khác `thenComparing(Person::age)` ở điểm thực tiễn nào?

`thenComparingInt` lấy primitive `int`, phù hợp getter trả `int` và tránh boxing sang `Integer` khi so sánh. Với các getter nullable `Integer`, không thể dùng trực tiếp một primitive extractor mà vẫn giữ được ý nghĩa `null`; cần comparator cho wrapper với chính sách null.

### Câu 7. Danh sách đã được sort theo khóa `team` rồi tiếp tục gọi sort theo `name`. Có luôn giống so nhiều khóa bằng `thenComparing` không?

Không. Vì `List.sort` ổn định, nếu trước tiên sort theo **khóa phụ `name`**, sau đó sort theo **khóa chính `team`**, kết quả có thể tương đương với thứ tự `team → name` khi các comparator hợp lệ và dữ liệu không đổi. Nhưng nếu sort theo khóa chính trước rồi sort theo khóa phụ sau, khóa phụ sẽ trở thành ưu tiên chính. Dùng `thenComparing` trực tiếp rõ ràng hơn và tránh phụ thuộc thứ tự nhiều lượt sort.

---

**Ghi nhớ:** `Comparator.comparing(...)` xác định **khóa ưu tiên cao nhất**; mỗi `thenComparing(...)` thêm **khóa dự phòng** chỉ khi các khóa đứng trước hòa. Chọn rõ tăng/giảm ở từng khóa, chính sách `null` và quy tắc so chuỗi. Khi dùng comparator cho `TreeSet`/`TreeMap`, đặc biệt chú ý ý nghĩa của `compare == 0` vì nó quyết định hai khóa có bị coi là trùng hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparators-005">reversed() và vị trí áp dụng trong chuỗi so sánh</a>

<details>
<summary>Xem chi tiết</summary>

# `Comparator.reversed()` và vị trí áp dụng trong chuỗi so sánh

## 1. Quy tắc cốt lõi

`Comparator<T>` mô tả thứ tự so sánh của các đối tượng kiểu `T`. Trong Java, phương thức **`reversed()` trả về một comparator mới đảo chiều thứ tự của chính comparator mà nó được gọi trên đó**. Điều này đặc biệt quan trọng khi kết hợp nhiều tiêu chí bằng `thenComparing(...)` hoặc `thenComparingInt(...)`.

Ví dụ, sắp xếp nhân viên theo:

1. `department` (phòng ban), tăng dần.
2. Nếu cùng phòng ban, `age` (tuổi), tăng dần.

Ta có thể viết:

~~~java
Comparator<Employee> byDepartment =
        Comparator.comparing(Employee::department);
Comparator<Employee> byAge =
        Comparator.comparingInt(Employee::age);

Comparator<Employee> base = byDepartment.thenComparing(byAge);
~~~

Đây là **đoạn minh họa**, giả định lớp hoặc record `Employee` có hai accessor `department()` và `age()`. `thenComparing` chỉ dùng tiêu chí tiếp theo **khi comparator trước đó trả về `0`**. Đây là kiểu so sánh từ trái sang phải (*lexicographic ordering*).

Điểm dễ nhầm là `reversed()` **đảo toàn bộ comparator đã được xây dựng đến vị trí lời gọi**, chứ không chỉ mặc định đảo tiêu chí đứng ngay cạnh nó. Hãy nghĩ theo cách đặt dấu ngoặc:

~~~text
(c1.thenComparing(c2)).reversed()
    -> đảo thứ tự của cả chuỗi c1 rồi c2

c1.reversed().thenComparing(c2)
    -> đảo c1; nếu hòa ở c1, c2 vẫn giữ chiều cũ

c1.thenComparing(c2.reversed())
    -> giữ c1; nếu hòa ở c1, đảo riêng c2
~~~

**Thứ tự lời gọi phương thức quyết định phạm vi đảo chiều.** `reversed()` không biến đổi dữ liệu gốc, không tự sắp xếp danh sách, và không thay đổi cách `equals()` của các đối tượng hoạt động.

## 2. Hiểu dấu của `compare(a, b)`

Theo hợp đồng `Comparator.compare(a, b)`:

- Trả về **số âm**: `a` đứng trước `b`.
- Trả về **`0`**: hai đối tượng tương đương theo comparator đang xét; `thenComparing` mới cần kiểm tra tiêu chí tiếp theo.
- Trả về **số dương**: `a` đứng sau `b`.

Nếu `c` là comparator tăng dần và `r = c.reversed()`, về mặt thứ tự `r.compare(a, b)` đảo dấu so sánh so với `c.compare(a, b)`. Có thể hình dung thông qua việc hoán đổi hai đối số khi so sánh. **Không nên tự đảo bằng phép trừ hoặc bằng cách đổi dấu nguyên vẹn giá trị `compare`**: giá trị `int` nhỏ nhất (`Integer.MIN_VALUE`) có thể gây tràn khi lấy đối, còn hợp đồng chỉ dựa vào dấu của kết quả.

Với tiêu chí đầu tiên `c1`, thứ hai `c2`, comparator ghép hoạt động về mặt logic như sau:

~~~java
// Minh họa thuật toán, giả sử có Comparator<T> c1 và c2.
int result = c1.compare(a, b);
if (result != 0) {
    return result;
}
return c2.compare(a, b);
~~~

`thenComparing` không “trộn” các điểm số với nhau. Tiêu chí đầu tiên có **ưu tiên cao hơn**: chỉ khi bằng nhau theo tiêu chí trước mới đi đến tiêu chí kế tiếp.

## 3. Bốn cách đặt `reversed()`, bốn thứ tự khác nhau

Giả sử mỗi đối tượng có ba thành phần:

- `name`: nhãn để quan sát đầu ra.
- `group`: một trong hai giá trị `A` hoặc `B`.
- `score`: `10` hoặc `20`.

Ta muốn sắp xếp theo `group` trước, rồi theo `score`.

| Cách tạo comparator | Group | Score trong cùng group | Ý nghĩa |
| --- | --- | --- | --- |
| `byGroup.thenComparing(byScore)` | A → B | 10 → 20 | Cả hai tăng dần |
| `byGroup.thenComparing(byScore).reversed()` | B → A | 20 → 10 | Đảo **toàn chuỗi** đã tạo |
| `byGroup.reversed().thenComparing(byScore)` | B → A | 10 → 20 | Đảo **group** trước khi gắn score |
| `byGroup.thenComparing(byScore.reversed())` | A → B | 20 → 10 | Chỉ đảo **score** làm tiêu chí phụ |

Chú ý: `byGroup.reversed().thenComparing(byScore)` là một comparator mới được xây dựng theo thứ tự *group giảm, score tăng*. Nếu gọi thêm `.reversed()` **sau toàn bộ chuỗi này**, bạn lại đảo cả hai tiêu chí đang có, trở thành *group tăng, score giảm*.

Một cách diễn đạt toán học tiện nhớ:

~~~text
(c1 + c2).reversed()  ≈  c1.reversed() + c2.reversed()
c1.reversed() + c2    =  c1 giảm, rồi c2 theo chiều ban đầu
c1 + c2.reversed()    =  c1 theo chiều ban đầu, rồi c2 giảm
~~~

Dấu `+` ở đây chỉ cách ghép comparator bằng `thenComparing`, **không phải phép cộng Java**. Dấu `≈` diễn tả **tương đương về thứ tự so sánh**, không khẳng định hai đối tượng comparator có cùng tham chiếu hay cùng cấu trúc triển khai.

## 4. Chương trình Java 17+ chạy độc lập

Tạo tệp **`ComparatorReversedDemo.java`**:

~~~java
import java.util.Comparator;
import java.util.List;

public class ComparatorReversedDemo {
    record Row(String name, String group, int score) {}

    static void show(
            String label, List<Row> rows, Comparator<Row> comparator) {
        List<String> names = rows.stream()
                .sorted(comparator)
                .map(Row::name)
                .toList();
        System.out.println(label + ": " + names);
    }

    public static void main(String[] args) {
        List<Row> rows = List.of(
                new Row("b20", "B", 20),
                new Row("a10", "A", 10),
                new Row("b10", "B", 10),
                new Row("a20", "A", 20));

        Comparator<Row> byGroup = Comparator.comparing(Row::group);
        Comparator<Row> byScore = Comparator.comparingInt(Row::score);

        Comparator<Row> allAsc =
                byGroup.thenComparing(byScore);

        Comparator<Row> allDesc =
                byGroup.thenComparing(byScore).reversed();

        Comparator<Row> groupDescScoreAsc =
                byGroup.reversed().thenComparing(byScore);

        Comparator<Row> groupAscScoreDesc =
                byGroup.thenComparing(byScore.reversed());

        show("group ASC, score ASC", rows, allAsc);
        show("all DESC", rows, allDesc);
        show("group DESC, score ASC", rows, groupDescScoreAsc);
        show("group ASC, score DESC", rows, groupAscScoreDesc);

        List<Row> ties = List.of(
                new Row("m1", "A", 10),
                new Row("m2", "A", 10),
                new Row("x", "B", 20));

        show("reverse with equal keys", ties, allDesc);
    }
}
~~~

Biên dịch và chạy với **JDK 17 trở lên**:

~~~bash
javac ComparatorReversedDemo.java
java ComparatorReversedDemo
~~~

Đầu ra xác định:

~~~text
group ASC, score ASC: [a10, a20, b10, b20]
all DESC: [b20, b10, a20, a10]
group DESC, score ASC: [b10, b20, a10, a20]
group ASC, score DESC: [a20, a10, b20, b10]
reverse with equal keys: [x, m1, m2]
~~~

**Giải thích từng dòng:**

1. `allAsc` là chuỗi tăng dần theo `group` rồi tăng dần theo `score`: các dòng nhóm `A` đứng trước nhóm `B`; trong mỗi nhóm điểm `10` đứng trước `20`.
2. `allDesc` gọi `reversed()` **sau khi `thenComparing` đã gắn cả hai tiêu chí**. Vì thế nhóm `B` đứng trước `A`, đồng thời điểm `20` đứng trước `10` trong từng nhóm.
3. `groupDescScoreAsc` gọi `reversed()` khi comparator mới chỉ có `byGroup`. Sau đó `thenComparing(byScore)` gắn tiêu chí điểm **giữ nguyên tăng dần**.
4. `groupAscScoreDesc` đảo đúng comparator `byScore` trước khi đưa vào `thenComparing`. Nhóm vẫn tăng dần, điểm bên trong nhóm giảm dần.
5. Hai dòng `m1` và `m2` có cùng `group = A` và `score = 10`, nên `allDesc.compare(m1, m2) == 0`. `Stream.sorted` giữ tính ổn định (*stable*) đối với stream có thứ tự như `List.stream()`, nên hai dòng hòa vẫn xuất hiện theo thứ tự nhập ban đầu: `m1` trước `m2`.

**Bài học quan trọng:** đảo comparator **không đồng nghĩa đảo ngược toàn bộ danh sách đã sắp xếp**. Nếu các phần tử có khóa so sánh bằng nhau, sort ổn định giữ thứ tự tương đối của chúng; còn lệnh đảo danh sách như `Collections.reverse(list)` sẽ đổi cả thứ tự các phần tử hòa đó.

## 5. Vị trí `reversed()` khi có ba tiêu chí

Giả sử cần sắp xếp theo ba khóa lần lượt: **phòng ban, lương, tên**. Từ trái sang phải, `thenComparing` chỉ được dùng khi toàn bộ tiêu chí trước đó cho kết quả hòa.

### A. Đảo mọi tiêu chí đã ghép

~~~java
// Đoạn minh họa trong ngữ cảnh có lớp Employee và các accessor.
Comparator<Employee> allDescending =
        Comparator.comparing(Employee::department)
                .thenComparingInt(Employee::salary)
                .thenComparing(Employee::name)
                .reversed();
~~~

Thứ tự là **phòng ban giảm → lương giảm → tên giảm**. `reversed()` nằm ở cuối nên bao trùm **toàn bộ ba khóa**.

### B. Đảo tiêu chí thứ nhất, các tiêu chí sau tăng dần

~~~java
Comparator<Employee> departmentDescThenOthersAsc =
        Comparator.comparing(Employee::department,
                             Comparator.reverseOrder())
                .thenComparingInt(Employee::salary)
                .thenComparing(Employee::name);
~~~

Ở đây chỉ đảo việc so sánh `department` ngay khi tạo comparator gốc, rồi gắn các tiêu chí phụ giữ nguyên. Một biểu thức có thứ tự tương đương là:

~~~java
Comparator<Employee> byDepartment =
        Comparator.comparing(Employee::department);

Comparator<Employee> departmentDescThenOthersAsc =
        byDepartment.reversed()
                .thenComparingInt(Employee::salary)
                .thenComparing(Employee::name);
~~~

Đây là **hai phương án thay thế**, không đặt cả hai khai báo biến trùng tên trong cùng một scope.

### C. Chỉ đảo tiêu chí ở giữa

~~~java
Comparator<Employee> middleDescending =
        Comparator.comparing(Employee::department)
                .thenComparing(
                        Comparator.comparingInt(Employee::salary)
                                  .reversed())
                .thenComparing(Employee::name);
~~~

Thứ tự: **phòng ban tăng → lương giảm → tên tăng**.

Nếu Java gặp vấn đề suy luận kiểu với một lambda hoặc method reference phức tạp ở comparator bên trong, tách biến với kiểu tường minh:

~~~java
Comparator<Employee> salaryDesc =
        Comparator.comparingInt(Employee::salary).reversed();

Comparator<Employee> middleDescending =
        Comparator.comparing(Employee::department)
                .thenComparing(salaryDesc)
                .thenComparing(Employee::name);
~~~

Mỗi đoạn đều là **minh họa**, giả định `Employee` có `department()` và `name()` trả về `String`, `salary()` trả về `int`, đồng thời đã import `java.util.Comparator`. Chỉ chương trình trong mục 4 là tệp Java hoàn chỉnh để chạy độc lập.

### D. Đảo phần tiền tố rồi thêm tiêu chí mới

~~~java
Comparator<Employee> prefixDescNameAsc =
        Comparator.comparing(Employee::department)
                .thenComparingInt(Employee::salary)
                .reversed()
                .thenComparing(Employee::name);
~~~

Thứ tự: **phòng ban giảm → lương giảm → tên tăng**. `reversed()` đảo *hai tiêu chí đã ghép trước đó*, còn `thenComparing(Employee::name)` được thêm **sau bước đảo** nên vẫn tăng dần.

Đây là khác biệt quan trọng so với việc chuyển `reversed()` xuống cuối cùng: khi chuyển xuống cuối, tên cũng sẽ giảm dần.

## 6. Hai cách viết “đảo một tiêu chí”

Khi muốn đảo một khóa cụ thể, có hai lựa chọn thường rõ ràng hơn việc đặt `reversed()` vào giữa chuỗi dài:

**Cách 1: truyền comparator dành riêng cho khóa vào `comparing` / `thenComparing`.**

~~~java
Comparator<Employee> byDepartmentDescThenNameAsc =
        Comparator.comparing(
                Employee::department, Comparator.reverseOrder())
                .thenComparing(Employee::name);
~~~

`Comparator.reverseOrder()` tạo comparator theo thứ tự ngược lại **thứ tự tự nhiên** của kiểu giá trị khóa. Với `String`, thứ tự tự nhiên là từ điển theo giá trị Unicode; với `Integer`, đó là thứ tự số tăng dần. Vì `department` trả về `String` nên cách này đảo riêng khóa phòng ban.

**Cách 2: tạo comparator con rồi gọi `reversed()` đúng vào comparator con.**

~~~java
Comparator<Employee> bySalaryDesc =
        Comparator.comparingInt(Employee::salary).reversed();

Comparator<Employee> byDepartmentAscSalaryDesc =
        Comparator.comparing(Employee::department)
                .thenComparing(bySalaryDesc);
~~~

Cách này đặc biệt dễ đọc khi khóa phụ là số nguyên thủy: `comparingInt` dùng bộ trích xuất `int`, rồi `reversed()` đảo comparator kết quả.

**Lưu ý:** `Comparator.reverseOrder()` là phương thức `static` tạo comparator ngược với thứ tự tự nhiên của **chính kiểu khóa**; `someComparator.reversed()` là phương thức của comparator hiện có, đảo thứ tự do **comparator đó định nghĩa**, kể cả khi nó gồm nhiều khóa hoặc quy tắc tùy biến.

## 7. `reversed()` không làm mất ưu tiên của `thenComparing`

Xét:

~~~java
Comparator<Employee> cmp =
        Comparator.comparing(Employee::department)
                .thenComparingInt(Employee::salary);
~~~

Nếu hai nhân viên khác phòng ban, kết quả so sánh đã được quyết định tại `department`; lương của họ không được dùng làm tiêu chí để “vượt qua” thứ tự phòng ban. Gọi `cmp.reversed()` chỉ đảo thứ tự phân nhóm và đảo cách phân thứ tự lương **trong cùng phòng ban**.

Tương tự, nếu viết `cmp.reversed().thenComparing(Employee::name)`, tiêu chí `name` chỉ hoạt động khi **cả phòng ban và lương** không phân biệt được hai nhân viên. Nó không thay thế, cũng không chen lên trước các tiêu chí đó.

Vì vậy, khi kết quả sort khác mong muốn, hãy đọc chuỗi comparator như một **danh sách tiêu chí ưu tiên**: “so sánh khóa 1; nếu hòa thì khóa 2; nếu vẫn hòa thì khóa 3”. Sau đó xác định chính xác `reversed()` đã bao phủ những tiêu chí nào vào thời điểm gọi.

## 8. Các trường hợp cần lưu ý

### Khóa `null` và vị trí `nullsFirst`/`nullsLast`

Khi có khóa có thể là `null`, `Comparator.naturalOrder()` và `Comparator.reverseOrder()` thông thường không tự cho phép `null`. Có thể dùng `Comparator.nullsFirst(...)` hoặc `Comparator.nullsLast(...)` để định nghĩa vị trí của `null`.

Ví dụ:

~~~java
// Đoạn minh họa với giá trị kiểu String.
Comparator<String> nullLastAsc =
        Comparator.nullsLast(Comparator.naturalOrder());

Comparator<String> nullFirstDesc = nullLastAsc.reversed();

Comparator<String> nullLastDesc =
        Comparator.nullsLast(Comparator.reverseOrder());
~~~

- `nullLastAsc`: các chuỗi không null tăng dần; `null` đứng cuối.
- `nullFirstDesc`: đảo **toàn bộ** `nullLastAsc`; chuỗi không null giảm dần **và `null` chuyển lên đầu**.
- `nullLastDesc`: các chuỗi không null giảm dần nhưng **`null` vẫn đứng cuối**.

Điều này minh họa thêm rằng `reversed()` đảo thứ tự của **cả comparator hiện tại**, bao gồm quy tắc dành cho `null` nếu quy tắc đó đã được bọc vào comparator trước lúc đảo.

### `compare(...) == 0` và các tập hợp có thứ tự

Hai đối tượng khác nhau theo `equals()` vẫn có thể có `comparator.compare(a, b) == 0` nếu mọi khóa được chọn đều bằng nhau. `TreeSet` và `TreeMap` dựa trên comparator để xác định hai phần tử hoặc khóa tương đương trong cấu trúc; vì vậy chúng có thể coi hai đối tượng khác nhau về mặt `equals()` là cùng khóa nếu comparator trả về `0`.

Nếu muốn giữ các đối tượng phân biệt theo một khóa định danh, thêm `thenComparing(Employee::id)` hoặc tiêu chí kết thúc phù hợp với ngữ nghĩa dữ liệu. Chú ý: việc thêm tiêu chí kết thúc **trước hay sau `reversed()`** tiếp tục quyết định chiều của tiêu chí đó.

### Comparator có thứ tự không hợp lệ

Một comparator phải tuân thủ hợp đồng về dấu đối xứng, tính bắc cầu và nhất quán. Chẳng hạn viết `return a.score() - b.score();` có nguy cơ tràn số nguyên, khiến dấu không phản ánh thứ tự số thực. Nên dùng `Comparator.comparingInt(Employee::score)` hoặc `Integer.compare(a.score(), b.score())`.

`reversed()` không thể sửa một comparator gốc đã vi phạm hợp đồng. Một comparator sai có thể khiến các thuật toán sắp xếp hoặc cấu trúc có thứ tự hoạt động không như mong muốn.

### Chữ hoa, chữ thường và Locale

`Comparator.comparing(Employee::name)` dùng thứ tự tự nhiên của `String`, không tự động áp dụng quy tắc ngôn ngữ cho tên người. Nếu cần thứ tự dành cho một ngôn ngữ, hãy chọn comparator khóa phù hợp (chẳng hạn `Collator` được cấu hình đúng) rồi mới đảo chiều khi cần. Việc đảo một comparator ngôn ngữ áp dụng cho **chính thứ tự mà comparator đó tạo ra**.

## 9. So sánh nhanh các biểu thức thường gặp

| Biểu thức | Tiêu chí thứ nhất | Tiêu chí thứ hai |
| --- | --- | --- |
| `c1.thenComparing(c2)` | Chiều c1 | Chiều c2 |
| `c1.thenComparing(c2).reversed()` | Ngược c1 | Ngược c2 |
| `c1.reversed().thenComparing(c2)` | Ngược c1 | Chiều c2 |
| `c1.thenComparing(c2.reversed())` | Chiều c1 | Ngược c2 |
| `c1.reversed().thenComparing(c2).reversed()` | Chiều c1 | Ngược c2 |

Trong bảng, `c1` và `c2` là các comparator hợp lệ cho **cùng kiểu phần tử**. “Chiều” được hiểu theo quy tắc do từng comparator gốc xác định, không nhất thiết là thứ tự tự nhiên. Ví dụ dòng cuối: bước đảo thứ nhất tác động vào `c1`, sau đó ghép `c2`; bước đảo cuối cùng đảo toàn bộ chuỗi đang có, nên `c1` quay về chiều ban đầu còn `c2` trở thành ngược chiều.

Khi chuỗi dài, thường nên **đặt tên các comparator con** thay vì liên tiếp gọi nhiều `reversed()`. Tên như `byGroupDesc`, `byScoreAsc` giúp kiểm tra trực tiếp yêu cầu nghiệp vụ.

## 10. Bài tập tự kiểm tra

**Câu 1.** Kết quả của chuỗi sau theo khóa phòng ban và tuổi là gì?

~~~java
Comparator<Employee> cmp =
        Comparator.comparing(Employee::department)
                .reversed()
                .thenComparingInt(Employee::age);
~~~

**Đáp án:** Phòng ban **giảm dần**, sau đó tuổi **tăng dần** trong cùng phòng ban.

**Câu 2.** Muốn cả phòng ban và tuổi cùng giảm dần thì viết thế nào?

~~~java
Comparator<Employee> cmp =
        Comparator.comparing(Employee::department)
                .thenComparingInt(Employee::age)
                .reversed();
~~~

**Đáp án:** Đặt `reversed()` sau khi đã ghép cả hai tiêu chí như trên, hoặc đảo từng comparator con rồi ghép lại.

**Câu 3.** Muốn phòng ban tăng dần, tuổi giảm dần, tên tăng dần thì làm sao?

~~~java
Comparator<Employee> ageDesc =
        Comparator.comparingInt(Employee::age).reversed();

Comparator<Employee> cmp =
        Comparator.comparing(Employee::department)
                .thenComparing(ageDesc)
                .thenComparing(Employee::name);
~~~

**Đáp án:** Đảo comparator `ageDesc` trước khi gắn làm tiêu chí thứ hai; `department` và `name` vẫn theo chiều tự nhiên.

**Câu 4.** `cmp.reversed()` có giống `Collections.reverse(sortedList)` không?

**Đáp án:** Không hoàn toàn. Đảo comparator rồi sắp xếp lại tạo thứ tự ngược theo các khóa so sánh, nhưng các đối tượng mà comparator coi là bằng nhau có thể giữ nguyên thứ tự tương đối khi dùng thuật toán sắp xếp ổn định. `Collections.reverse` đảo vị trí tất cả phần tử, kể cả các phần tử hòa.

**Câu 5.** Nếu `Comparator.nullsLast(Comparator.naturalOrder()).reversed()` được dùng, vị trí `null` là ở đâu?

**Đáp án:** `null` chuyển lên **đầu**, vì `reversed()` đảo cả chính sách null đã được gắn trong comparator.

**Ghi nhớ:** `reversed()` tác động đến **toàn bộ comparator đã hình thành tại điểm gọi**. Muốn đảo mọi khóa đã ghép, gọi ở cuối. Muốn đảo riêng một khóa, đảo comparator con của khóa ấy trước khi ghép hoặc cung cấp comparator đảo chiều trong `comparing`/`thenComparing`. Luôn kiểm tra thứ tự ưu tiên khi có nhiều tiêu chí.

## Tham khảo

- [Java SE 17 — `Comparator` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)
- [Java SE 17 — `Stream.sorted` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#sorted(java.util.Comparator))


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparators-006">nullsFirst() và nullsLast()</a>

<details>
<summary>Xem chi tiết</summary>

# Comparator.nullsFirst() và Comparator.nullsLast() trong Java

Khi sắp xếp, dữ liệu thực tế có thể chứa `null`: một phần tử `null` trong `List`, một trường chưa được điền hoặc một khóa trích xuất từ object có giá trị `null`. Nếu chuyển thẳng các giá trị đó cho một comparator không hỗ trợ `null`, ứng dụng có thể gặp `NullPointerException`. Java cung cấp hai phương thức tĩnh **`Comparator.nullsFirst()`** và **`Comparator.nullsLast()`** để quy định vị trí của `null` một cách tường minh.

Mục tiêu của bài: hiểu hợp đồng của hai phương thức, phân biệt **phần tử null** với **thuộc tính null**, kết hợp với `comparing`/`thenComparing`/`reversed`, và tránh lỗi khi dùng `List.sort`, stream hoặc `TreeSet`.

## 1. Ý nghĩa và chữ ký API

Trong `java.util.Comparator`, hai phương thức có dạng:

~~~java
public static <T> Comparator<T> nullsFirst(
        Comparator<? super T> comparator);

public static <T> Comparator<T> nullsLast(
        Comparator<? super T> comparator);
~~~

Đây là **chữ ký API để tham khảo**, không phải đoạn khai báo cần dán vào lớp của bạn. Chúng tạo ra **comparator mới** từ comparator được cung cấp:

| Tình huống | `nullsFirst(c)` | `nullsLast(c)` |
|---|---|---|
| Cả hai đều `null` | Bằng nhau: kết quả `0` | Bằng nhau: kết quả `0` |
| Vế trái `null`, phải khác `null` | Số âm | Số dương |
| Vế trái khác `null`, phải `null` | Số dương | Số âm |
| Cả hai khác `null` | Giao cho `c.compare(a,b)` | Giao cho `c.compare(a,b)` |

Chỉ **dấu** của kết quả so sánh là phần hợp đồng: đừng viết chương trình phụ thuộc bắt buộc nhận đúng `-1` hoặc `1`. Hai phương thức không sửa danh sách và không loại phần tử; chúng chỉ tạo **quy tắc thứ tự** để truyền vào nơi cần `Comparator<T>`.

Cách đọc:

- `Comparator.nullsFirst(Comparator.naturalOrder())`: các giá trị `null` đứng **trước**, các giá trị khác `null` tăng dần theo thứ tự tự nhiên.
- `Comparator.nullsLast(Comparator.naturalOrder())`: các giá trị `null` đứng **sau**, các giá trị khác `null` tăng dần.
- Phần tử không null vẫn được so bởi comparator bên trong. Hai giá trị không null có được xem ngang hàng hay không do **comparator bên trong** quyết định.

`Comparator.naturalOrder()` đòi hỏi kiểu có thứ tự tự nhiên phù hợp (thường là triển khai `Comparable`). `nullsFirst`/`nullsLast` không tự cung cấp khả năng so sánh cho những object không có thứ tự.

## 2. Ví dụ cơ bản: sắp danh sách có phần tử null

Giả sử có các số nguyên đóng hộp `Integer`:

~~~java
List<Integer> numbers =
        new ArrayList<>(Arrays.asList(3, null, 1, null, 2));

numbers.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
System.out.println(numbers); // [null, null, 1, 2, 3]

numbers.sort(Comparator.nullsLast(Comparator.naturalOrder()));
System.out.println(numbers); // [1, 2, 3, null, null]
~~~

Đây là đoạn minh họa đặt trong `main`, cần import `List`, `ArrayList`, `Arrays`, `Comparator` trong `java.util`. `ArrayList` cho phép `null`; danh sách được tạo qua `Arrays.asList` rồi sao chép thành `ArrayList` để dễ thao tác.

Nếu gọi `numbers.sort(Comparator.naturalOrder())` trực tiếp trên danh sách có `null`, phép so sánh theo thứ tự tự nhiên không xử lý được phần tử `null` và thường ném `NullPointerException`. Cách khắc phục là **bao comparator** bằng `nullsFirst` hoặc `nullsLast`, theo yêu cầu dữ liệu.

`List.of(3, null, 1)` không dùng được để tạo fixture tương tự, vì factory `List.of` từ chối `null` ngay khi tạo. Đây là chính sách của **factory collection**, độc lập với comparator.

## 3. Hai đối tượng null và hai khóa sắp xếp null là chuyện khác nhau

Xem một bản ghi:

~~~java
record Employee(String name, Integer priority) {}
~~~

Một `List<Employee>` có hai kiểu `null`:

1. **Phần tử null**: chính biến tham chiếu `Employee` trong list là `null`.
2. **Khóa null**: phần tử là `Employee` hợp lệ nhưng `employee.priority()` là `null`.

Hai cách đặt `nullsLast` cho kết quả khác nhau:

~~~java
// Xử lý phần tử Employee null, còn Employee hợp lệ được so theo priority.
Comparator<Employee> objectNullLast =
        Comparator.nullsLast(
                Comparator.comparing(Employee::priority));

// Xử lý giá trị priority null nhưng vẫn đòi hỏi Employee khác null.
Comparator<Employee> keyNullLast =
        Comparator.comparing(
                Employee::priority,
                Comparator.nullsLast(Comparator.naturalOrder()));
~~~

Hai đoạn là snippet minh họa và cần một record `Employee` như trên. `objectNullLast` **không an toàn khi thuộc tính `priority` bị null**, vì `Comparator.comparing(Employee::priority)` mặc định so khóa theo natural ordering. `keyNullLast` xử lý được thuộc tính `priority == null`, nhưng **không an toàn nếu toàn bộ phần tử Employee là null**, vì extractor `Employee::priority` không thể gọi trên `null`.

Muốn xử lý **cả hai**, đặt `nullsLast` ở hai tầng:

~~~java
Comparator<Employee> byPriority =
        Comparator.nullsLast(
                Comparator.comparing(
                        Employee::priority,
                        Comparator.nullsLast(
                                Comparator.naturalOrder())));
~~~

Comparator phía **trong** quyết định vị trí của *khóa* `priority == null` trong số các Employee không null. Comparator phía **ngoài** quyết định vị trí của *object* `Employee == null`. Đây là mô hình quan trọng nhất khi dữ liệu lồng các tham chiếu nullable.

Tương tự, `Comparator.comparing(Employee::name, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))` cho phép **tên null**, nhưng chưa cho phép **Employee null**. Nếu cần cả hai, tiếp tục bao toàn bộ comparator bằng `Comparator.nullsFirst` hoặc `nullsLast`.

## 4. Kết hợp với thenComparing để phá hòa

Một ứng dụng thường sắp nhân viên theo `priority` tăng dần, nhưng những người cùng mức ưu tiên nên được sắp tiếp theo tên. Quy tắc cần định nghĩa:

- `Employee` null đứng cuối.
- `priority` null đứng sau các ưu tiên có số.
- Trong cùng priority (kể cả hai khóa null), so tên tăng dần.
- Nếu tên có thể null, phải thêm chính sách null riêng cho tên.

Ví dụ với tên luôn không null:

~~~java
Comparator<Employee> order =
        Comparator.nullsLast(
                Comparator.comparing(
                        Employee::priority,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Employee::name));
~~~

Đây là đoạn minh họa trong chương trình có record `Employee`. `thenComparing` chạy khi phần so sánh trước trả `0` giữa hai Employee **khác null**. Nếu cả hai priority đều null, comparator khóa trả `0`; khi ấy thứ tự theo tên mới quyết định ai đứng trước.

Nếu tên cũng nullable, thay phần tên bằng:

~~~java
.thenComparing(
        Employee::name,
        Comparator.nullsLast(Comparator.naturalOrder()))
~~~

Đây chỉ là đoạn **nối tiếp phương thức** của comparator ở trên, không phải một biểu thức Java độc lập. `thenComparing` giúp thứ tự ổn định về logic của khóa; nó **không tự xóa các bản ghi trùng**. Với `List.sort` và `Stream.sorted`, những phần tử ngang hàng theo comparator vẫn nằm trong kết quả.

## 5. reversed() có thể đảo cả vị trí null

Một nhầm lẫn thường gặp là nghĩ `reversed()` chỉ đảo tăng/giảm của giá trị không null. Trên thực tế, nó đảo **toàn bộ quan hệ thứ tự**, gồm vị trí của `null`.

~~~java
Comparator<Integer> firstAscending =
        Comparator.nullsFirst(Comparator.naturalOrder());

Comparator<Integer> reversedWhole = firstAscending.reversed();
// reversedWhole: số giảm dần, null chuyển xuống cuối.

Comparator<Integer> firstDescending =
        Comparator.nullsFirst(Comparator.reverseOrder());
// firstDescending: null vẫn đứng đầu, số giảm dần.
~~~

Vì vậy:

| Yêu cầu | Cách tạo comparator |
|---|---|
| Null đầu, số tăng | `nullsFirst(naturalOrder())` |
| Null cuối, số tăng | `nullsLast(naturalOrder())` |
| Null đầu, số giảm | `nullsFirst(reverseOrder())` |
| Null cuối, số giảm | `nullsLast(reverseOrder())` |
| Đảo hẳn một quy tắc đang có | `existingComparator.reversed()` |

Nếu đang có comparator nhiều tầng, gọi `reversed()` cuối cùng sẽ đảo **toàn bộ thứ tự** của nó. Để giữ vị trí null nhưng chỉ đảo một trường, hãy đảo **comparator của trường đó trước**, sau đó đưa vào `nullsFirst`/`nullsLast` tại tầng tương ứng.

## 6. Chương trình Java 17 chạy được

Lưu file **`ComparatorNullsDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class ComparatorNullsDemo {
    record Employee(String name, Integer priority) {}

    public static void main(String[] args) {
        List<Integer> numbers =
                new ArrayList<>(Arrays.asList(3, null, 1, null, 2));

        numbers.sort(Comparator.nullsFirst(
                Comparator.naturalOrder()));
        System.out.println("numbers first: " + numbers);

        numbers.sort(Comparator.nullsLast(
                Comparator.naturalOrder()));
        System.out.println("numbers last: " + numbers);

        List<Employee> employees = new ArrayList<>(
                Arrays.asList(
                        new Employee("Lan", null),
                        new Employee("An", 2),
                        new Employee("Binh", null),
                        new Employee("Cuong", 1),
                        null,
                        new Employee("Dung", 2)));

        Comparator<Employee> employeeOrder =
                Comparator.nullsLast(
                        Comparator.comparing(
                                Employee::priority,
                                Comparator.nullsLast(
                                        Comparator.naturalOrder()))
                                .thenComparing(Employee::name));

        employees.sort(employeeOrder);
        List<String> labels = employees.stream()
                .map(e -> e == null
                        ? "<null-employee>"
                        : e.name() + ":" + e.priority())
                .toList();
        System.out.println("employees: " + labels);

        List<Integer> reverseExample =
                new ArrayList<>(Arrays.asList(null, 1, 3));
        Comparator<Integer> firstAscending =
                Comparator.nullsFirst(Comparator.naturalOrder());

        reverseExample.sort(firstAscending.reversed());
        System.out.println("reversed whole: " + reverseExample);

        reverseExample.sort(Comparator.nullsFirst(
                Comparator.reverseOrder()));
        System.out.println("null first descending: " + reverseExample);

        Comparator<String> onlyNullPolicy =
                Comparator.nullsFirst(null);
        System.out.println("null-only rule, a vs z equal: "
                + (onlyNullPolicy.compare("a", "z") == 0));
        System.out.println("null-only rule, null before a: "
                + (onlyNullPolicy.compare(null, "a") < 0));

        TreeSet<String> names = new TreeSet<>(
                Comparator.nullsFirst(
                        String.CASE_INSENSITIVE_ORDER));
        names.add(null);
        names.add("An");
        names.add("AN");
        System.out.println("tree set size: " + names.size());
        System.out.println("tree set contains null: "
                + names.contains(null));
    }
}
~~~

Biên dịch và chạy bằng **JDK 17+**:

~~~bash
javac ComparatorNullsDemo.java
java ComparatorNullsDemo
~~~

Đầu ra dự kiến:

~~~text
numbers first: [null, null, 1, 2, 3]
numbers last: [1, 2, 3, null, null]
employees: [Cuong:1, An:2, Dung:2, Binh:null, Lan:null, <null-employee>]
reversed whole: [3, 1, null]
null first descending: [null, 3, 1]
null-only rule, a vs z equal: true
null-only rule, null before a: true
tree set size: 2
tree set contains null: true
~~~

**Đọc kết quả:**

1. Hai comparator đầu chỉ thay vị trí của `null`, các số không null vẫn tăng dần.
2. Comparator nhân viên có **hai lớp bảo vệ null**: trường `priority` null xếp sau số, còn phần tử `Employee` null xếp cuối danh sách. `thenComparing(Employee::name)` sắp `An` trước `Dung` (cùng priority 2) và `Binh` trước `Lan` (cùng priority null).
3. `firstAscending.reversed()` chuyển null từ đầu xuống cuối, đồng thời đảo số sang giảm dần. Nếu muốn null vẫn đầu, hãy đặt `reverseOrder()` **bên trong** `nullsFirst`.
4. Khi comparator truyền vào `nullsFirst` là `null`, mọi giá trị **khác null** được coi là ngang hàng (kết quả so sánh `0`), nhưng chúng đều đứng sau null.
5. `TreeSet` được truyền comparator hỗ trợ null nên có thể chứa một null. Hai chuỗi `"An"` và `"AN"` bị coi cùng khóa vì `CASE_INSENSITIVE_ORDER.compare` trả `0`, mặc dù `String.equals` phân biệt chữ hoa/thường.

Chú ý: kết quả `TreeSet` trong ví dụ chỉ in số phần tử và phép `contains`; không nên suy rằng mọi `TreeSet` mặc định đều cho phép null. Điều đó **phụ thuộc comparator đang dùng**.

## 7. Trường hợp comparator đầu vào là null

Java cho phép:

~~~java
Comparator<String> c = Comparator.nullsLast(null);
~~~

Đây là cú pháp hợp lệ. Khi comparator đối số là **`null`**, wrapper chỉ phân biệt **null** với **khác null**; mọi giá trị khác null được xem là ngang hàng vì không có comparator nền để sắp chúng.

Hệ quả:

- `c.compare("a","z") == 0` dù hai chuỗi không bằng nhau theo `String.equals`.
- Một `TreeSet<String>` dùng `c` có thể gộp mọi chuỗi không null thành **một khóa**.
- Các phần tử không null không có quan hệ từ điển; lựa chọn này **không thay thế** `Comparator.naturalOrder()`.

Trong phần lớn chương trình muốn sắp xếp có ý nghĩa, hãy cung cấp một comparator **không null** như `naturalOrder()`, `reverseOrder()` hoặc `comparing(...)`.

## 8. Stream, List.sort và collection có thứ tự

Cùng một comparator có thể được dùng trong nhiều API:

~~~java
List<Integer> sorted = values.stream()
        .sorted(Comparator.nullsLast(Comparator.naturalOrder()))
        .toList();
~~~

Ở đây `values` là một `List<Integer>` đã được khai báo. `Stream.sorted` tạo stream theo thứ tự phù hợp; `toList()` nhận kết quả thành list và không yêu cầu sửa list nguồn. `List.sort(c)` sắp **trực tiếp** trên list có thể thay đổi; `Collections.sort(list, c)` dùng comparator được truyền vào.

Nếu dùng `TreeSet` hoặc `TreeMap`, `compare == 0` có ý nghĩa **trùng khóa**. Một comparator chỉ nhìn một thuộc tính hoặc comparator nền `null` có thể khiến nhiều object khác nhau theo `equals` bị gộp thành một. Việc xử lý `null` an toàn **không bảo đảm** comparator nhất quán với `equals`; cần kiểm tra hợp đồng và mục đích của collection.

## 9. Các lỗi thường gặp

| Lỗi | Vì sao xảy ra | Cách xử lý |
|---|---|---|
| `naturalOrder()` gặp null | Không có chính sách cho null | Bao bằng `nullsFirst`/`nullsLast` |
| Bao comparator ngoài nhưng khóa vẫn null | `nullsLast(comparing(...))` chỉ xử lý **object null** | Bao comparator **của khóa** bằng nullsFirst/Last |
| Bảo vệ khóa nhưng object có thể null | Method reference trích khóa được gọi trên null | Bao toàn bộ comparator sau cùng |
| `comparingInt(Employee::priority)` với `Integer priority` null | Unboxing `null` gây `NullPointerException` | Dùng `comparing` với comparator xử lý khóa null hoặc chọn giá trị mặc định |
| `reversed()` làm null đổi vị trí | Đảo toàn bộ thứ tự | Đảo comparator nền **trước** khi bao null policy |
| `nullsFirst(null)` dùng để sắp chuỗi theo chữ | Mọi giá trị không null ngang nhau | Truyền `Comparator.naturalOrder()` hoặc comparator thực sự |
| `TreeSet` mất các object khác nhau | Comparator trả `0` cho nhiều giá trị | Thêm tie-breaker phân biệt khóa hoặc chọn collection khác |
| Tưởng `nullsLast` lọc hết null | Wrapper chỉ quy định thứ tự | Dùng `filter(Objects::nonNull)` nếu bài toán thật sự cần loại null |
| Tên/khóa phụ cũng null | `thenComparing` không được bảo vệ | Định nghĩa null policy cho từng khóa có thể null |

## 10. Chọn phương án theo yêu cầu

- **Null đầu, giá trị tăng dần:** `Comparator.nullsFirst(Comparator.naturalOrder())`.
- **Null cuối, giá trị tăng dần:** `Comparator.nullsLast(Comparator.naturalOrder())`.
- **Null cuối, giá trị giảm dần:** `Comparator.nullsLast(Comparator.reverseOrder())`.
- **Object không null nhưng khóa nullable:** `Comparator.comparing(Foo::key, Comparator.nullsLast(Comparator.naturalOrder()))`.
- **Cả object và khóa có thể null:** bao comparator khóa như trên, rồi `Comparator.nullsLast(...)` cho object.
- **Nhiều khóa:** dùng `thenComparing`; đặt null policy **ở từng khóa** cần xử lý null.

Khi kiểm thử, cần thử tối thiểu các cặp: `null/null`, `null/value`, `value/null`, hai value khác nhau, hai object không null có khóa null, hai object có cùng khóa nhưng thuộc tính phụ khác nhau, và cả đảo thứ tự với `reversed()`.

## 11. Tự kiểm tra

**Câu 1.** `Comparator.nullsFirst(Comparator.naturalOrder())` so `null` với số 5 cho kết quả gì?  
**Đáp án:** Một số âm; null đứng trước 5. Không phụ thuộc giá trị số âm cụ thể.

**Câu 2.** Tại sao `Comparator.nullsLast(Comparator.comparing(Employee::priority))` vẫn có thể ném exception?  
**Đáp án:** Nó chỉ cho phép **Employee object** null. Một Employee khác null có `priority == null` vẫn gây lỗi khi so khóa theo natural ordering.

**Câu 3.** Muốn sắp priority giảm dần nhưng priority null vẫn ở cuối thì dùng gì?  
**Đáp án:** `Comparator.comparing(Employee::priority, Comparator.nullsLast(Comparator.reverseOrder()))` cho các Employee khác null; bao thêm ngoài nếu Employee có thể null.

**Câu 4.** Nếu có `Comparator<Integer> c = Comparator.nullsFirst(Comparator.naturalOrder())`, dùng `c.reversed()` còn giữ null đầu không?  
**Đáp án:** Không. Nó đảo toàn bộ thứ tự nên null xuống cuối và các số giảm dần.

**Câu 5.** `Comparator.nullsLast(null)` có so được hai String theo chữ cái không?  
**Đáp án:** Không. Nó xem tất cả hai String khác null là ngang hàng, trả `0`.

**Ghi nhớ:** `nullsFirst`/`nullsLast` **bọc một comparator**, đưa null lên đầu hoặc xuống cuối nhưng không tự so nội dung non-null. Với object có thuộc tính nullable, hãy đặt chính sách null **đúng tầng**, và cân nhắc hiệu ứng của `reversed`, `thenComparing` cùng collection đang sử dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparators-007">Chọn quy tắc so sánh chuỗi theo yêu cầu nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn quy tắc so sánh chuỗi theo yêu cầu nghiệp vụ với Comparator trong Java

## 1. Vì sao một chuỗi có nhiều cách sắp xếp đúng?

Một ứng dụng có thể cần sắp xếp tên người theo tiếng Việt, mã sản phẩm theo số thứ tự, nhãn theo chữ cái không phân biệt hoa/thường, hoặc khóa kỹ thuật theo thứ tự byte/Unicode ổn định. Những quy tắc ấy **khác nhau**. Không thể có một `Comparator<String>` đúng cho mọi trường hợp.

Chẳng hạn với bốn chuỗi `"A"`, `"a"`, `"B"`, `"b"`:
- `String.compareTo` theo thứ tự từ điển của các đơn vị mã UTF-16: `A, B, a, b`.
- `String.CASE_INSENSITIVE_ORDER` xem `"A"` với `"a"` bằng nhau về thứ tự; khi sort một `List` ổn định, thứ tự tương đối của hai phần tử này được giữ lại.
- Quy tắc *không phân biệt hoa/thường trước, rồi dùng thứ tự tự nhiên để phân xử* cho `A, a, B, b`, tức có thứ tự xác định ngay cả giữa những biến thể hoa/thường.

Với `"item2"` và `"item10"`, thứ tự từ điển thông thường xếp `"item10"` trước `"item2"`, vì so ký tự `'1'` với `'2'`. Nếu nghiệp vụ muốn mã có hậu tố số tăng dần, ta phải định nghĩa quy tắc khác.

Trong bối cảnh **Java core – Object Contract / Comparator**, bài này hướng dẫn chọn quy tắc dựa vào ý nghĩa nghiệp vụ, xây dựng comparator đúng hợp đồng, kiểm tra hệ quả với `TreeSet`/`TreeMap`, và phân biệt so sánh kỹ thuật với so sánh ngôn ngữ.

## 2. Trước khi viết mã: đặc tả dữ liệu và quy tắc

Hãy làm rõ sáu câu hỏi:

1. **Đang sắp xếp gì?** Mã kỹ thuật, tên người, nhãn giao diện, mã hàng có chữ và số, hay chuỗi đầu vào tự do?
2. **Có locale không?** Danh sách tên tiếng Việt thường cần quy tắc ngôn ngữ tiếng Việt; khóa kỹ thuật lại cần kết quả ổn định không lệ thuộc ngôn ngữ.
3. **Hoa/thường và dấu có ý nghĩa không?** `"A"` so với `"a"`, `"ma"` so với `"má"` có được coi cùng mức ưu tiên không?
4. **Khoảng trắng, dấu câu, định dạng Unicode có ý nghĩa không?** Có chuẩn hóa trước khi so không; phải phân biệt chuẩn hóa hiển thị với thay đổi định danh thật.
5. **Có phần số không?** `"SKU-2"` và `"SKU-10"` cần so như số; `"SKU-02"` và `"SKU-2"` được coi cùng nhóm hay hai mã khác nhau?
6. **`compare(a,b)==0` có nghĩa gì?** Chỉ là cùng thứ hạng để sắp xếp danh sách, hay phải coi là trùng khóa trong `TreeSet`/`TreeMap`?

Ví dụ ma trận quyết định:

| Nhu cầu | Quy tắc khởi đầu | Điểm cần quyết định |
|---|---|---|
| Khóa máy, mã phân biệt hoa/thường | `Comparator.naturalOrder()` | Thứ tự là UTF-16; không phải thứ tự đọc của con người |
| Lọc/xếp nhóm bỏ qua hoa/thường | `String.CASE_INSENSITIVE_ORDER` | So bằng 0 cho một số chuỗi khác nhau theo `String.equals` |
| Xếp chữ không phân biệt hoa/thường nhưng giữ mọi biến thể | Case-insensitive rồi tie-break bằng natural order | Giữ phân biệt `"A"` và `"a"` trong sorted set |
| Tên/nhãn hiển thị tiếng Việt | `Collator` với `Locale.forLanguageTag("vi-VN")` | Phải chọn strength, phân biệt dấu và tie-break |
| Mã có hậu tố là số | Tách, kiểm tra rồi so số; sau đó tie-break | Số 2 và 02 có tương đương không? |
| Giá trị có `null` | `nullsFirst` hoặc `nullsLast` | Thống nhất `null` là dữ liệu hợp lệ hay lỗi |
| Xếp theo nhiều tiêu chí | `comparing`, `thenComparing` | Thứ tự ưu tiên và xử lý trùng là gì? |

Một yêu cầu cụ thể tốt là: “Danh sách tên sản phẩm hiển thị cho người dùng vi-VN; dùng thứ tự ngôn ngữ Việt, giữ dấu phân biệt, không loại mất sản phẩm chỉ vì tên gần giống; trường hợp collator xem bằng nhau thì so chuỗi gốc để kết quả lặp lại nhất quán”.

## 3. Hợp đồng `Comparator<T>` cần giữ

Chính sách so chuỗi không chỉ quyết định ai đứng trước; nó còn quyết định hai chuỗi nào thuộc cùng nhóm khi `compare(a,b)==0`. Với bất kỳ quy tắc nào (natural, bỏ qua hoa/thường, `Collator`, hay so phần số), vẫn phải có đối dấu, bắc cầu và dấu so sánh nhất quán giữa những phần tử được xem ngang hàng.

Ví dụ `String.CASE_INSENSITIVE_ORDER.compare("A","a")==0` nhưng `"A".equals("a")` là `false`. Đây là một comparator hợp lệ để hiển thị, song `TreeSet` có thể gộp hai khóa ấy. Thêm `thenComparing(Comparator.naturalOrder())` nếu cần giữ từng biến thể String khác nhau; **đừng thêm tie-breaker** nếu nghiệp vụ thật sự muốn định danh không phân biệt hoa/thường.

Đối với chuỗi có dấu hoặc mã số, cần xác định normalization, locale, case policy, leading zero và khóa định danh trước khi chọn cách phá hòa. Comparator không nên phụ thuộc random, thời gian, cấu hình `Collator` đang bị sửa hoặc nguồn ngoài thay đổi trong lúc sort. Với các tiêu chí số, dùng factory chuẩn (`comparingInt/Long`) thay cho phép trừ có nguy cơ tràn. Khi so trong `TreeSet/TreeMap`, kiểm tra cả hai chiều của quan hệ `compare==0` và `equals` với dữ liệu đại diện.
## 4. Lựa chọn một: thứ tự kỹ thuật của `String`

`String.compareTo` so các đơn vị mã UTF-16 theo thứ tự từ trái sang phải, khác với việc dùng quy tắc bảng chữ cái tự nhiên của bất kỳ locale cụ thể nào. Với các chuỗi có dấu hoặc surrogate pair, không suy luận rằng so UTF-16 đồng nghĩa với so “ký tự người dùng nhìn thấy” theo ngôn ngữ.

```java
Comparator<String> technical = Comparator.naturalOrder();
List<String> labels = new ArrayList<>(List.of("b", "A", "a", "B"));
labels.sort(technical);
System.out.println(labels); // [A, B, a, b]
```

Đây là đoạn minh họa trong phương thức có import `java.util.*` hoặc các import tương đương. Thứ tự này thích hợp với mã kỹ thuật có schema cố định nếu yêu cầu đúng là thứ tự so sánh String. Nó **không** phải cách chuẩn để hiển thị tên tiếng Việt cho con người.

### Unicode normalization và biểu diễn cùng ký tự

Hai chuỗi trông giống nhau có thể dùng các dãy Unicode khác nhau: một ký tự có dấu dựng sẵn, hoặc ký tự cơ sở cộng dấu kết hợp. `String.equals` và `compareTo` nhìn vào dãy mã nên có thể cho kết quả khác nhau. Nếu miền nghiệp vụ coi các biểu diễn tương đương chuẩn Unicode là cùng dữ liệu, có thể chuẩn hóa về NFC khi nhận dữ liệu:

```java
import java.text.Normalizer;

String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC);
```

Đoạn trích này cần biến `raw` đã khai báo. Không mặc định chuẩn hóa tất cả trường như mật khẩu, token, mã ký hoặc ID bên ngoài: chuẩn hóa có thể làm thay đổi dữ liệu mang ý nghĩa định danh. Hãy thống nhất cả lúc nhập, lưu, tìm kiếm và so sánh.

## 5. Lựa chọn hai: so sánh bỏ qua hoa/thường

`String.CASE_INSENSITIVE_ORDER` và `String.compareToIgnoreCase` thực hiện so sánh không phân biệt hoa/thường theo cơ chế dựa trên ánh xạ chữ hoa/chữ thường của Java, **không phải bộ sắp xếp ngôn ngữ có locale**. Chúng không đồng nghĩa với `Collator`, và không tự xử lý mọi biến thể Unicode, dấu hay cách ghép chữ theo từng ngôn ngữ.

```java
Comparator<String> ignoreCase = String.CASE_INSENSITIVE_ORDER;
Comparator<String> ignoreCaseThenNatural =
        ignoreCase.thenComparing(Comparator.naturalOrder());
```

Với `List.of("b", "A", "a", "B")`:
- Sắp xếp `ignoreCase` bằng `List.sort` (stable) cho `[A, a, b, B]`. Hai giá trị không phân biệt được bằng comparator giữ tương đối thứ tự xuất hiện trong list đầu vào.
- Sắp xếp `ignoreCaseThenNatural` cho `[A, a, B, b]`. Tiêu chí phụ dùng `String.compareTo` tách `A/a` và `B/b`.
- Thêm cả bốn chuỗi vào `TreeSet<>(ignoreCase)` chỉ có **hai phần tử**, vì `compare==0` được coi là trùng.

**Câu hỏi nghiệp vụ trọng yếu:** muốn nhóm `"A"` và `"a"` như cùng tên, hay chỉ muốn chúng nằm gần nhau nhưng vẫn là hai khóa riêng? Câu trả lời quyết định có dùng tie-breaker hay không.

### Không lạm dụng `toLowerCase()`

Có thể thấy code dạng `Comparator.comparing(s -> s.toLowerCase())`. Cách này:
- Phụ thuộc default locale nếu không truyền `Locale`.
- Có thể tạo chuỗi mới nhiều lần trong khi sort.
- Có thể có hành vi khác với `compareToIgnoreCase` vì Unicode case conversion phụ thuộc locale và có thể thay đổi chiều dài.
- Không giải quyết luôn quy tắc dấu, biến thể chuẩn Unicode hoặc tính nhất quán với khóa database.

Nếu thật sự cần một khóa canonical không phụ thuộc locale, hãy chọn và ghi rõ một chính sách như `toLowerCase(Locale.ROOT)`, cân nhắc Unicode normalization, kiểm tra các ngôn ngữ liên quan và xử lý tie-break. `Locale.ROOT` không có nghĩa là “đúng theo mọi ngôn ngữ”; nó phù hợp hơn cho một số biến đổi kỹ thuật không hướng đến quy tắc hiển thị của một quốc gia.

## 6. Lựa chọn ba: so sánh ngôn ngữ bằng `Collator`

Với tên người, tên địa danh hoặc nhãn hiển thị, thường cần **collation** theo locale. Java có `java.text.Collator`:

```java
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

Locale vietnamese = Locale.forLanguageTag("vi-VN");
Collator collator = Collator.getInstance(vietnamese);
collator.setStrength(Collator.TERTIARY);

Comparator<String> vietnameseOrder = collator::compare;
Comparator<String> vietnameseWithTieBreaker =
        vietnameseOrder.thenComparing(Comparator.naturalOrder());
```

Ở đây:
- Chọn locale **tường minh** (`vi-VN`) thay vì mặc định phụ thuộc máy chạy.
- `Collator` đưa ra thứ tự ngôn ngữ cho locale đã chọn, phù hợp hơn `String.compareTo` khi sort tên tiếng Việt.
- `setStrength` quy định mức khác biệt được xét: `PRIMARY` chủ yếu phân biệt chữ cơ sở; `SECONDARY` thêm mức khác biệt phụ như nhiều loại dấu; `TERTIARY` thêm khác biệt ở mức sâu hơn như một số khác biệt về hoa/thường. Cách sắp chính xác phụ thuộc quy tắc và dữ liệu locale do JDK cung cấp.
- `thenComparing(Comparator.naturalOrder())` phá hòa khi collator cho kết quả `0`, nhờ đó các chuỗi khác nhau theo `String.equals` không bị nhập làm một trong `TreeSet`.
- Không khẳng định dấu thanh tiếng Việt luôn thuộc đúng một mức strength trong mọi provider và bản dữ liệu; cần kiểm thử các trường hợp thực tế bằng JDK, locale data và chính sách sản phẩm đang triển khai.

### Chọn mức strength theo yêu cầu

| Strength | Phù hợp khi | Điều cần kiểm tra |
|---|---|---|
| `Collator.PRIMARY` | Muốn nhóm nhiều biến thể nhỏ lại theo chữ cơ sở | Có thể xem những tên khác nhau là đồng hạng |
| `Collator.SECONDARY` | Cần phân biệt thêm mức dấu/biến thể phụ theo collation | Quy tắc phân biệt cụ thể của locale |
| `Collator.TERTIARY` | Muốn phân biệt sâu hơn, có thể gồm hoa/thường | Không mặc nhiên đồng nhất với `String.equals` |
| `Collator.IDENTICAL` | Cần mức phân biệt cao nhất theo collator | Vẫn phải xác định yêu cầu về các dạng chuẩn Unicode, provider và tie-break |

`Collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION)` có thể cần khi muốn xử lý nhất quán những chuỗi khác nhau ở dạng ký tự dựng sẵn và ký tự cơ sở + dấu kết hợp. Chi phí xử lý và kết quả cần được kiểm thử theo yêu cầu thực tế. Đừng thay thế việc xác định chính sách normalization dữ liệu bằng một cờ collator mà không đánh giá.

**Không dùng giả định rằng thứ tự `"Hà"`, `"Hoa"`, `"Hòa"` sẽ giống nhau trên mọi phiên bản JDK và mọi cấu hình collation provider.** Dữ liệu locale có thể khác giữa các bản JDK/CLDR. Nếu cần thứ tự tuyệt đối ổn định giữa hệ thống hoặc lưu vào DB/index, cần định nghĩa phiên bản quy tắc collation được quản lý.

### `Collator` là object có trạng thái cấu hình

Các lời gọi như `setStrength`, `setDecomposition` làm thay đổi cách so sánh. Tránh chia sẻ rồi thay đổi cùng một `Collator` trong nhiều luồng hoặc khi collection đã dùng comparator đó. Nếu cần xử lý đồng thời, dùng instance riêng hoặc bản clone theo worker/thread phù hợp; đừng thay chính sách comparator trong lúc `TreeSet`/`TreeMap` đang có dữ liệu.

## 7. Lựa chọn bốn: mã chứa phần số

Với `item1`, `item2`, `item10`, sort theo thứ tự `String.compareTo` trả `item1, item10, item2` vì so ký tự. Nếu code hợp lệ luôn có tiền tố `item` và một dãy chữ số ASCII, so **giá trị số phần hậu tố** sẽ cho `item1, item2, item10`.

Quy tắc rõ ràng:
1. Xác thực cú pháp mã trước hoặc trong hàm trích số: `item[0-9]+`.
2. Parse phần sau `item` bằng `BigInteger` nếu không muốn giới hạn chiều dài hoặc overflow của `int`/`long`.
3. So giá trị số trước.
4. Quyết định `item02` với `item2`: hai mã có cùng giá trị số nhưng khác biểu diễn; nếu muốn giữ cả hai như hai khóa khác nhau, bổ sung natural-order tie-breaker.
5. Với mã có nhiều đoạn số/chữ không đồng nhất, phải xây dựng parser/tokenizer rõ ràng; không cắt cố định ở ký tự thứ năm cho mọi domain.

```java
static BigInteger itemNumber(String code) {
    if (!code.matches("item[0-9]+")) {
        throw new IllegalArgumentException("Not item + digits: " + code);
    }
    return new BigInteger(code.substring(4));
}

Comparator<String> byNumber = Comparator.comparing(
        StringComparatorDemo::itemNumber)
        .thenComparing(Comparator.naturalOrder());
```

Đoạn trích này thuộc lớp `StringComparatorDemo` của ví dụ hoàn chỉnh phía sau. Với `item02` và `item2`, số đều là 2 nhưng tiêu chí phụ phân biệt chuỗi gốc. **Không** xử lý số bằng `return Integer.parseInt(aSuffix) - Integer.parseInt(bSuffix)` vì phép trừ có thể overflow và các số dài có thể vượt miền.

Một chính sách khác có thể muốn các mã số `02` và `2` là **cùng định danh**. Nếu vậy cần chuẩn hóa khóa ngay khi ghi hoặc so `0` một cách có chủ đích; cần cân nhắc tác động lên `TreeSet`/`TreeMap` và uniqueness của dữ liệu.

## 8. Lựa chọn năm: null, khoảng trắng, nhiều tiêu chí

### Null

`Comparator.naturalOrder()` và nhiều comparator khác không nhận `null`; nếu dữ liệu cho phép `null`, định nghĩa thứ tự:

```java
Comparator<String> nullLast =
        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER
                .thenComparing(Comparator.naturalOrder()));
```

Nếu `null` phải đứng đầu dùng `Comparator.nullsFirst(...)`. Với danh sách có `null`, tránh `List.of(...)` vì factory này không chấp nhận null; dùng `Arrays.asList(...)` hoặc list mutable theo nhu cầu. Nếu null là dữ liệu không hợp lệ, fail fast ở biên input còn tốt hơn âm thầm xếp null cuối.

### Khoảng trắng và tiêu chí phụ

Nếu yêu cầu là “bỏ khoảng trắng đầu/cuối khi xếp nhưng vẫn phân biệt chuỗi gốc”, có thể:

```java
Comparator<String> trimThenNatural =
        Comparator.comparing(String::strip)
                .thenComparing(Comparator.naturalOrder());
```

`String.strip` dựa trên Unicode whitespace theo `Character.isWhitespace` và khác `String.trim`. Một số ký tự không bị xử lý theo cách người dùng mong đợi, nên cần kiểm thử dữ liệu thực. Comparator trên **không sửa chính chuỗi gốc**; chỉ sử dụng bản transform để so, rồi so chuỗi gốc khi bằng nhau.

Nếu cần chính sách “không phân biệt hoa/thường; độ dài tăng; cuối cùng chữ gốc”, viết thứ tự tiêu chí tường minh. Hãy kiểm tra trường hợp hai comparator khác nhau về tính tương đương để tránh gộp khóa ngoài ý muốn.

## 9. Ví dụ Java 17 hoàn chỉnh, output có thể kiểm chứng

Tệp **`StringComparatorDemo.java`** sau đây chạy độc lập bằng JDK 17+. Nó minh họa kết quả xác định cho natural order, case-insensitive, tie-breaker, hậu tố số và null. Phần `Collator` không in danh sách đã sắp vì thứ tự chữ tiếng Việt có thể phụ thuộc phiên bản dữ liệu locale; chương trình vẫn thiết lập và chạy comparator thực tế.

```java
import java.math.BigInteger;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class StringComparatorDemo {
    static List<String> sorted(List<String> source, Comparator<String> order) {
        List<String> copy = new ArrayList<>(source);
        copy.sort(order);
        return copy;
    }

    static BigInteger itemNumber(String code) {
        if (!code.matches("item[0-9]+")) {
            throw new IllegalArgumentException("Not item + digits: " + code);
        }
        return new BigInteger(code.substring(4));
    }

    public static void main(String[] args) {
        List<String> labels = List.of("b", "A", "a", "B");
        System.out.println("natural: "
                + sorted(labels, Comparator.naturalOrder()));
        System.out.println("ignore case: "
                + sorted(labels, String.CASE_INSENSITIVE_ORDER));

        Comparator<String> totalCaseOrder = String.CASE_INSENSITIVE_ORDER
                .thenComparing(Comparator.naturalOrder());
        System.out.println("tie breaker: "
                + sorted(labels, totalCaseOrder));

        TreeSet<String> ignoreCaseSet =
                new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ignoreCaseSet.addAll(labels);
        System.out.println("set ignoring case size: "
                + ignoreCaseSet.size());

        List<String> ids = List.of("item10", "item2", "item1", "item02");
        Comparator<String> byNumber = Comparator.comparing(
                StringComparatorDemo::itemNumber)
                .thenComparing(Comparator.naturalOrder());
        System.out.println("IDs natural: "
                + sorted(ids, Comparator.naturalOrder()));
        System.out.println("IDs numeric: " + sorted(ids, byNumber));

        Comparator<String> nullLast =
                Comparator.nullsLast(totalCaseOrder);
        System.out.println("null last: " + sorted(
                java.util.Arrays.asList(null, "b", "A"), nullLast));

        Collator vi = Collator.getInstance(
                Locale.forLanguageTag("vi-VN"));
        vi.setStrength(Collator.TERTIARY);
        Comparator<String> viOrder = vi::compare;
        List<String> names = sorted(
                List.of("Hòa", "Hoa", "Hà", "Anh"),
                viOrder.thenComparing(Comparator.naturalOrder()));
        System.out.println("vi collator configured: "
                + (vi.getStrength() == Collator.TERTIARY));
        System.out.println("vi names count: " + names.size());
    }
}
```

Chạy tại thư mục lưu tệp:

```bash
javac --release 17 StringComparatorDemo.java
java StringComparatorDemo
```

Output chính xác của các dòng in dưới đây:

```text
natural: [A, B, a, b]
ignore case: [A, a, b, B]
tie breaker: [A, a, B, b]
set ignoring case size: 2
IDs natural: [item02, item1, item10, item2]
IDs numeric: [item1, item02, item2, item10]
null last: [A, b, null]
vi collator configured: true
vi names count: 4
```

Ví dụ này đã được kiểm tra biên dịch/chạy với `javac --release 17`. Giải thích chi tiết:

- `natural`: ký tự đầu `A`/`B` đứng trước `a`/`b` theo quy tắc UTF-16.
- `ignore case`: `List.sort` ổn định, nên trong các nhóm so bằng nhau (`A/a` và `b/B`) nó giữ thứ tự tương đối lúc đầu.
- `tie breaker`: tiêu chí phụ natural order phân biệt hoa/thường, khiến các giá trị khác nhau có thứ tự rõ.
- `TreeSet` bỏ bớt các phần tử khi comparator trả `0` cho những chuỗi khác nhau về `String.equals`.
- `IDs natural`: so chuỗi thuần, nên `item02` đứng trước `item1` và `item10` đứng trước `item2`.
- `IDs numeric`: parse hậu tố số, rồi natural order phá hòa `item02`/`item2`.
- `null last`: comparator xác định vị trí `null`.
- `vi collator`: kiểm tra một instance `Collator` cho locale Việt đã được cấu hình và đã dùng sort 4 tên. Chương trình cố tình chỉ in **số phần tử** để không phụ thuộc thứ tự ngôn ngữ được cung cấp ở từng JDK.

## 10. Tie-breaker khi sắp xếp đối tượng có tên

Khi có dữ liệu đối tượng, hãy so theo trường đã chọn và thêm một khóa bất biến để phân biệt các bản ghi nếu business yêu cầu thứ tự tổng:

```java
record Customer(long id, String displayName) {}

Comparator<Customer> byDisplayNameThenId =
        Comparator.comparing(
                Customer::displayName,
                String.CASE_INSENSITIVE_ORDER)
        .thenComparingLong(Customer::id);
```

Đoạn trên là ví dụ minh họa, không nằm trong file chạy ở mục 9. Nếu hai khách hàng khác nhau có tên giống nhau khi bỏ qua hoa/thường, ID dùng làm tiêu chí phụ. Điều kiện là ID có ý nghĩa định danh duy nhất và ổn định. Nếu cùng ID vẫn có thể xuất hiện nhiều bản ghi khác nhau trong list, cần kiểm tra lại mô hình.

Trường hợp hiển thị tiếng Việt, có thể thay `String.CASE_INSENSITIVE_ORDER` bằng một comparator dựa trên `Collator` được cấu hình. Nhưng phải xác định việc khác dấu/hòa khi collation trả `0` sẽ được phân xử bởi ID hay chuỗi gốc để đáp ứng nghiệp vụ.

### Khi tie-breaker là sai chủ ý

Nếu hệ thống xem hai tên tài khoản `"ADMIN"` và `"admin"` là cùng một định danh không phân biệt hoa/thường, comparator cho kết quả `0` có thể đúng với *quan hệ duy nhất* đó. Thêm `thenComparing(naturalOrder())` sẽ khiến chúng trở thành hai khóa khác nhau trong `TreeMap`. Vì vậy **tie-breaker là một quyết định nghiệp vụ**, không phải quy tắc máy móc.

Đặc biệt cần kiểm tra consistency giữa application và database: nếu database dùng collation khác hoặc unique index bỏ qua hoa/thường/dấu khác Java comparator, ứng dụng có thể cho phép khóa mà cơ sở dữ liệu từ chối, hoặc ngược lại.

## 11. Hiệu năng và tính ổn định trong hệ thống

Một comparator được gọi nhiều lần khi sort, có thể khoảng `O(n log n)` lần đối với thuật toán sort so sánh thường dùng. Vì vậy:

- Không gọi HTTP/database trong `compare`.
- Không parse một mã phức tạp hoặc normalize chuỗi hàng nghìn lần nếu có thể chuẩn bị khóa sort một lần cho từng phần tử. Với dataset lớn, mô hình dữ liệu nên lưu sẵn khóa chuẩn hóa hoặc sử dụng sort key thích hợp và kiểm soát version.
- Không thay đổi `Collator` đang được dùng bởi `TreeSet`/`TreeMap` sau khi collection đã có phần tử.
- Không dùng comparator dựa trên thời gian, random hoặc trạng thái volatile thay đổi trong lúc sort.
- Với nhiều luồng, mỗi luồng nên có cấu hình `Collator` riêng hoặc cơ chế clone/quản lý instance phù hợp; tránh sửa một instance dùng chung.
- Kiểm tra kết quả sort khi nâng JDK, thay đổi locale provider/CLDR, hoặc di chuyển sang database có collation khác. Nếu thứ tự là hợp đồng dữ liệu hoặc ảnh hưởng pagination, phải quản trị rõ phiên bản và tie-breaker ổn định.
- Sắp xếp trong DB cần `ORDER BY` tương ứng và tie-breaker duy nhất để pagination ổn định; một comparator Java chỉ sắp được dữ liệu đã được nạp vào bộ nhớ, không đảm bảo thứ tự trang phía DB.

Nếu mục tiêu chỉ là hiển thị tên, có thể chấp nhận phụ thuộc dữ liệu collation của JDK. Nếu mục tiêu là định danh, chống trùng hoặc chữ ký/canonicalization, hãy dùng quy tắc riêng **cố định và kiểm thử được**.

## 12. Kiểm thử comparator theo nghiệp vụ

Đừng chỉ kiểm thử một danh sách happy path. Hãy tạo tập test gồm:

| Loại trường hợp | Ví dụ | Cần xác minh |
|---|---|---|
| Hoa/thường | `A`, `a`, `B`, `b` | Có gộp hay phân biệt, tie-break ra sao |
| Dấu tiếng Việt | `ma`, `má`, `mà`, `MÁ` | Thứ tự phù hợp rule, locale và strength |
| Khác dạng Unicode | Ký tự dựng sẵn và ký tự gốc + dấu | Chính sách NFC/NFD và collation nhất quán |
| Số trong mã | `item2`, `item10`, `item02` | Parse số, leading zero, tie-break |
| Khoảng trắng | `" An"`, `"An"`, `"An "` | Có strip/trim hay giữ nguyên |
| Null | `null`, `""`, `"a"` | Xếp đầu/cuối hoặc từ chối dữ liệu |
| Chuỗi rỗng | `""` | Chấp nhận hay validation fail |
| Kiểu hỗn hợp | Chữ, số, emoji, ký tự ngoài BMP | Không giả định UTF-16 tương đương collation |
| Nhiều object cùng tên | Hai khách ID khác nhau | Có mất phần tử trong `TreeSet` không |

Ngoài thứ tự kỳ vọng, kiểm thử **hợp đồng toán học** của comparator:
1. Với mỗi `a,b`: dấu của `compare(a,b)` đối nghịch dấu của `compare(b,a)`.
2. Với `a,b,c`: nếu `a<=b` và `b<=c` thì `a<=c`.
3. Khi `compare(a,b)==0`, so `a` và `b` với từng `c` khác phải cho dấu tương đương.
4. Khi comparator làm khóa `TreeSet`/`TreeMap`, kiểm tra các cặp khác nhau theo `equals` có bị gộp ngoài mong muốn không.
5. Kiểm tra sắp xếp lặp lại ra cùng kết quả dưới cùng bộ dữ liệu, cùng quy tắc và cấu hình locale.

Có thể tạo một unit test có comparator chuẩn, dùng `Collections.sort` hoặc `List.sort`, rồi assert danh sách chính xác. Với `Collator`, test trên JDK/provider/locale data của môi trường hỗ trợ, đồng thời ghi rõ phiên bản nếu thứ tự phải tái lập.

## 13. Những sai lầm thường gặp

**1. Dùng `String.compareTo` cho mọi ngôn ngữ.** Đây là thứ tự kỹ thuật UTF-16, không giải quyết đầy đủ collation theo tiếng Việt.

**2. Dùng `compareToIgnoreCase` rồi giả định mọi cách viết đều tương đương.** Nó không phải full Unicode case folding hoặc collation, và không tự bỏ dấu.

**3. Dùng `Collator.getInstance()` không truyền locale.** Kết quả có thể phụ thuộc môi trường JVM; cần chọn locale theo chức năng.

**4. Cho comparator trả `0` với mọi tên “gần giống” rồi đưa vào `TreeSet`.** Nhiều phần tử khác nhau có thể bị xem như một. Nếu mục tiêu chỉ là sort, cần tie-breaker.

**5. Thêm tie-breaker vào quy tắc unique không phân biệt hoa/thường.** Có thể vô tình cho phép hai khóa mà nghiệp vụ muốn coi là trùng.

**6. So số bằng cách so string hoặc phép trừ.** `item10` đứng trước `item2` theo từ điển; phép trừ số có thể overflow.

**7. Chuẩn hóa dữ liệu không kiểm soát.** `strip`/`toLowerCase`/NFC có thể hợp lý cho một nhóm trường nhưng làm biến đổi token hoặc khóa bên ngoài.

**8. Comparator phụ thuộc trạng thái mutable.** Thay đổi thông tin của khóa đang trong `TreeMap`/`TreeSet` có thể phá tính tổ chức của cây, tương tự vấn đề hash collection khi key đổi hash.

**9. Test một bản JDK rồi khẳng định thứ tự locale bất biến.** Với tên hiển thị, nên xác nhận cả dữ liệu locale và môi trường triển khai.

## 14. Bài tập thực hành có tiêu chí rõ ràng

### Bài 1 — Sắp tên giao diện theo tiếng Việt

Tạo list `["Hoa", "Hòa", "Hà", "Anh"]`. So bằng `String.compareTo` và một `Collator` locale `vi-VN` ở các mức strength. Ghi nhận kết quả trên môi trường thực tế; giải thích vì sao so sánh UTF-16 khác ngôn ngữ. Đánh giá xem cần giữ hoa/thường, dấu và tie-break chuỗi gốc hay không.

### Bài 2 — Từ danh sách sang tập hợp

Dùng `["Admin", "admin", "ADMIN"]` trong `ArrayList` và `TreeSet` với `String.CASE_INSENSITIVE_ORDER`. So số lượng phần tử và giải thích tại sao. Thử `thenComparing(Comparator.naturalOrder())`. Quyết định cách đúng cho danh sách hiển thị và cho khóa tài khoản không phân biệt hoa/thường.

### Bài 3 — Sắp mã sản phẩm có số

Nhập `item1`, `item02`, `item2`, `item10`. Viết comparator so BigInteger hậu tố rồi tie-break theo biểu diễn gốc. Bổ sung validation từ chối `item-2` và `itemX`. Thử số dài hơn giới hạn `long`.

### Bài 4 — Tên có khoảng trắng

Thiết kế comparator sắp `" An"`, `"An"` và `"An "` theo chuỗi sau `strip`, sau đó theo chuỗi gốc. Nêu lý do có hoặc không chuẩn hóa dữ liệu ngay lúc nhập.

### Bài 5 — Ba tiêu chí cho đối tượng

Với record `Customer(id, displayName, priority)`, viết comparator: priority tăng dần, tên theo quy tắc đã chọn, ID tăng dần để phá hòa. Kiểm tra không mất bản ghi khi đưa vào `TreeSet` nếu ID là duy nhất và không đổi.

### Bài 6 — Test hợp đồng

Sinh một tập chuỗi gồm ASCII, chữ có dấu, emoji, chuỗi rỗng; kiểm tra tính đối dấu và bắc cầu trên mọi bộ ba trong tập hữu hạn. Khi phát hiện tình huống kết quả `compare==0` cho hai chuỗi khác `equals`, quyết định đó là quy tắc grouping có chủ đích hay lỗi cần sửa.

## 15. Checklist ra quyết định cuối bài

- Đã biết rõ dữ liệu là mã kỹ thuật, tên/ngôn ngữ hay mã có phần số.
- Đã chọn chính sách locale, hoa/thường, dấu, Unicode normalization và khoảng trắng.
- Đã giải thích đúng ý nghĩa `compare(a,b)==0` trong nghiệp vụ.
- Đã phân biệt yêu cầu **sort** với yêu cầu **uniqueness** của `TreeSet`/`TreeMap`.
- Đã có tie-breaker ổn định khi muốn giữ mọi bản ghi khác nhau.
- Đã xử lý hợp lệ `null`, dữ liệu sai cấu trúc và số có leading zero.
- Comparator giữ tính đối dấu, bắc cầu, và kết quả nhất quán trên dữ liệu không đổi.
- Đã kiểm tra locale/provider/JDK khi cần thứ tự ngôn ngữ ổn định.
- Đã kiểm tra sự tương thích với collation ở database nếu so sánh diễn ra cả hai nơi.
- Chạy được `StringComparatorDemo.java`, giải thích được các dòng stdout và đánh đổi từng comparator.

**Kết luận:** Chọn `Comparator<String>` bắt đầu từ **ý nghĩa nghiệp vụ của thứ tự và tính tương đương**, rồi mới chọn API. `String.compareTo` thích hợp cho thứ tự kỹ thuật, `CASE_INSENSITIVE_ORDER` cho nhóm bỏ qua hoa/thường, `Collator` cho quy tắc ngôn ngữ, và comparator tách số cho mã có phần số. Sử dụng tie-breaker có chủ đích để tránh làm mất khóa trong sorted collections; luôn kiểm thử các trường hợp Unicode, locale và ID thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparators-008">Phân biệt Comparator dùng cho hiển thị với quan hệ bằng nhau của đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt Comparator dùng cho hiển thị với quan hệ bằng nhau của đối tượng

## Mục tiêu học tập

Trong Java, **thứ tự hiển thị** và **quan hệ bằng nhau của đối tượng** là hai khái niệm độc lập. `Comparator<T>` định nghĩa thứ tự để sắp danh sách: theo tên, điểm số, thời gian, mức độ ưu tiên hoặc giá tiền. Ngược lại, `equals(Object)` xác định hai đối tượng có được xem là bằng nhau theo hợp đồng của lớp hay không. Một comparator dùng để hiển thị có thể coi hai đối tượng là **tương đương theo thứ tự** dù chúng là hai bản ghi nghiệp vụ khác nhau.

Bài này giúp bạn:

- Phân biệt `Comparator.compare(a, b) == 0`, `a.equals(b)`, `a == b` và `a.hashCode()`.
- Giải thích trường hợp hai đối tượng có cùng khóa sắp xếp nhưng không bằng nhau.
- Hiểu vì sao `List.sort` giữ mọi phần tử, còn `TreeSet`/`TreeMap` dùng `compare == 0` để xác định phần tử hoặc khóa tương đương.
- Dùng `thenComparing` để tạo tie-breaker, nhưng không nhầm nó với hợp đồng `equals`.
- Thiết kế đồng thời quy tắc bằng nhau nghiệp vụ và thứ tự hiển thị, đặc biệt với ID, tên trùng nhau, null, chữ hoa/thường và phân trang.

## 1. Các phép kiểm tra có ý nghĩa khác nhau

| Biểu thức | Ý nghĩa |
| --- | --- |
| `a == b` | Hai tham chiếu cùng chỉ tới **một đối tượng** hay không |
| `a.equals(b)` | Hai đối tượng có **bằng nhau theo hợp đồng lớp** hay không |
| `comparator.compare(a, b) == 0` | Hai đối tượng có **cùng vị trí trong thứ tự** mà comparator định nghĩa hay không |
| `a.hashCode() == b.hashCode()` | Hai đối tượng có cùng mã băm hay không; điều này **không chứng minh bằng nhau** |

`compare(a, b)` trả số **âm**, `0` hoặc số **dương** tương ứng `a` đứng trước, tương đương về thứ tự hoặc đứng sau `b`. Không dựa vào trị tuyệt đối cụ thể: chỉ nên xét dấu kết quả.

Một comparator được gọi là **nhất quán với equals** (*consistent with equals*) khi với các phần tử liên quan:

```text
comparator.compare(a, b) == 0  ⇔  a.equals(b)
```

Dấu tương đương hai chiều có nghĩa:

1. Nếu comparator trả `0` thì `equals` trả `true`.
2. Nếu `equals` trả `true` thì comparator trả `0`.

Một comparator dùng **chỉ để sắp xếp giao diện** có thể không nhất quán với `equals` mà vẫn hợp lệ, miễn đáp ứng hợp đồng thứ tự của `Comparator`. Tuy nhiên, khi đưa comparator ấy vào `TreeSet` hoặc `TreeMap`, sự khác biệt này có thể làm mất bản ghi hoặc thay giá trị khóa.

**Không nhầm `Comparator.equals(Object)`:** Đây là phương thức trên **bản thân đối tượng comparator**, không phải cách kiểm tra hai phần tử dữ liệu. `compare(a,b) == 0` mới là cách comparator thể hiện hai phần tử tương đương về thứ tự.

## 2. Một khóa hiển thị có thể đại diện cho nhiều đối tượng

Xét danh sách thành viên:

| ID | Tên | Điểm |
| ---: | --- | ---: |
| 101 | An | 90 |
| 102 | An | 90 |
| 103 | Bình | 80 |

Giao diện xếp hạng theo điểm giảm dần và tên tăng dần. Hai thành viên ID `101` và `102` có cùng khóa hiển thị: điểm `90` và tên `"An"`. Vì thế comparator chỉ so điểm và tên sẽ trả `0`.

Nhưng đó vẫn có thể là hai người khác nhau. Nếu lớp biểu diễn một thành viên bằng record:

```java
record Member(int id, String name, int score) {}
```

`equals` mặc định của record so **tất cả thành phần**. ID khác nhau làm `equals` trả `false` dù khóa hiển thị trùng nhau.

Sơ đồ:

```text
Thành viên 101 ── điểm 90, tên An ──┐
                                    ├── cùng vị trí theo Comparator
Thành viên 102 ── điểm 90, tên An ──┘

equals của record: khác nhau vì ID khác nhau
```

Đây là tình huống hợp lý trong bảng xếp hạng: **cùng thứ hạng hoặc cùng tiêu chí sắp xếp không đồng nghĩa cùng danh tính**.

## 3. Ví dụ chạy được: List và TreeSet cho kết quả khác nhau

Tạo tệp **`DisplayComparatorDemo.java`** (Java 17+):

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class DisplayComparatorDemo {
    record Member(int id, String name, int score) {}

    public static void main(String[] args) {
        Member an101 = new Member(101, "An", 90);
        Member an102 = new Member(102, "An", 90);
        Member binh103 = new Member(103, "Binh", 80);

        Comparator<Member> forDisplay =
                Comparator.comparingInt(Member::score)
                        .reversed()
                        .thenComparing(Member::name);

        System.out.println("same object: " + (an101 == an102));
        System.out.println("equals: " + an101.equals(an102));
        System.out.println("display compare == 0: "
                + (forDisplay.compare(an101, an102) == 0));

        List<Member> members =
                new ArrayList<>(List.of(an101, an102, binh103));
        members.sort(forDisplay);

        System.out.println("sorted list size: " + members.size());
        System.out.println("sorted IDs: "
                + members.stream().map(Member::id).toList());

        Set<Member> hashSet =
                new HashSet<>(List.of(an101, an102, binh103));

        Set<Member> treeSet = new TreeSet<>(forDisplay);
        treeSet.addAll(List.of(an101, an102, binh103));

        System.out.println("HashSet size: " + hashSet.size());
        System.out.println("TreeSet size: " + treeSet.size());

        Member anotherAn = new Member(999, "An", 90);
        System.out.println("TreeSet contains anotherAn: "
                + treeSet.contains(anotherAn));
        System.out.println("HashSet contains anotherAn: "
                + hashSet.contains(anotherAn));
    }
}
```

Biên dịch và chạy:

```bash
javac DisplayComparatorDemo.java
java DisplayComparatorDemo
```

**Đầu ra chính xác:**

```text
same object: false
equals: false
display compare == 0: true
sorted list size: 3
sorted IDs: [101, 102, 103]
HashSet size: 3
TreeSet size: 2
TreeSet contains anotherAn: true
HashSet contains anotherAn: false
```

**Giải thích:**

- `List.sort(forDisplay)` chỉ thay đổi **thứ tự** phần tử trong danh sách, không loại phần tử có comparator trả `0`. Vì vậy danh sách vẫn có ba thành viên.
- `List.sort` trong Java là sắp xếp **ổn định** (*stable*). Hai phần tử được comparator xem là ngang nhau giữ thứ tự tương đối như trước khi sắp: ở đây `101` trước `102`.
- `HashSet` dùng `equals` và `hashCode` của `Member`. Khác ID nên `an101` và `an102` đều được giữ.
- `TreeSet` được tạo bằng `forDisplay` dùng `compare(a,b) == 0` để xác định phần tử tương đương, vì thế gộp hai thành viên khác ID nhưng cùng tên/điểm.
- `treeSet.contains(new Member(999,"An",90))` trả `true` vì tiêu chí so sánh xem phần tử đó tương đương với một phần tử đã có. `HashSet.contains` trả `false` vì ID khác.

**Điểm rút ra:** Comparator hiển thị phù hợp với việc sắp một `List`; nếu dùng nó để định nghĩa tập hợp có thứ tự, hãy hiểu rõ các phần tử có thể bị coi là trùng.

## 4. Cấu trúc dữ liệu sử dụng quy tắc nào?

| Cấu trúc / thao tác | Quy tắc được dùng | Hệ quả khi `compare == 0` nhưng `equals == false` |
| --- | --- | --- |
| `List.sort(comparator)` | Comparator chỉ quyết định thứ tự | Giữ cả hai phần tử |
| `Collections.sort(list, comparator)` | Comparator chỉ quyết định thứ tự | Giữ cả hai phần tử |
| `stream.sorted(comparator)` | Comparator quyết định thứ tự kết quả | Không tự loại phần tử |
| `HashSet` | `equals` và `hashCode` | Không dùng comparator; hai đối tượng có thể đều tồn tại |
| `HashMap` | `equals` và `hashCode` trên khóa | Không dùng comparator; hai khóa khác nhau theo `equals` có thể cùng tồn tại |
| `TreeSet(comparator)` | `compare == 0` xác định tương đương | Chỉ giữ một đại diện |
| `TreeMap(comparator)` | `compare == 0` xác định khóa tương đương | `put` khóa sau có thể cập nhật giá trị của mục trước |

Một comparator chỉ so một phần của đối tượng rất phổ biến khi dùng cho UI: tên, điểm, giá, thời điểm… Nhưng khi chọn `TreeSet`/`TreeMap`, comparator trở thành một phần của **ngữ nghĩa nhận diện phần tử/khóa** chứ không đơn thuần là công cụ sắp xếp.

Tài liệu Java yêu cầu thứ tự dùng bởi sorted set/map nên **consistent with equals** để các cấu trúc này đáp ứng đúng hợp đồng chung của `Set`/`Map`. Chúng vẫn có hành vi được xác định theo comparator nếu không nhất quán, nhưng dễ gây bất ngờ cho mã khách.

## 5. Ví dụ TreeMap: khóa hiển thị trùng có thể ghi đè giá trị

Giả sử ứng dụng hiển thị nội dung theo tiêu đề không phân biệt chữ hoa/chữ thường. Một comparator phù hợp cho mục tiêu trình bày là:

```java
Comparator<Article> byDisplayTitle =
        Comparator.comparing(
                Article::title,
                String.CASE_INSENSITIVE_ORDER);
```

Đây là đoạn minh họa, giả định kiểu `Article` có accessor `title()`. Comparator coi tiêu đề `"Java"` và `"JAVA"` tương đương, dù hai bài viết có ID khác nhau.

Chạy ví dụ đầy đủ sau.

**Tệp `ComparatorMapDemo.java`:**

```java
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ComparatorMapDemo {
    record Article(int id, String title) {}

    public static void main(String[] args) {
        Article a = new Article(1, "Java");
        Article b = new Article(2, "JAVA");
        Article c = new Article(3, "Spring");

        Comparator<Article> displayOrder =
                Comparator.comparing(
                        Article::title,
                        String.CASE_INSENSITIVE_ORDER);

        Comparator<Article> strictOrder =
                displayOrder
                        .thenComparing(Article::title)
                        .thenComparingInt(Article::id);

        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("display compare zero: "
                + (displayOrder.compare(a, b) == 0));
        System.out.println("strict compare zero: "
                + (strictOrder.compare(a, b) == 0));

        Set<Article> displaySet = new TreeSet<>(displayOrder);
        displaySet.add(a);
        displaySet.add(b);
        displaySet.add(c);

        Set<Article> strictSet = new TreeSet<>(strictOrder);
        strictSet.add(a);
        strictSet.add(b);
        strictSet.add(c);

        System.out.println("display TreeSet size: "
                + displaySet.size());
        System.out.println("strict TreeSet size: "
                + strictSet.size());

        Map<Article, String> displayMap =
                new TreeMap<>(displayOrder);
        displayMap.put(a, "first");
        displayMap.put(b, "second");

        Map<Article, String> hashMap = new HashMap<>();
        hashMap.put(a, "first");
        hashMap.put(b, "second");

        System.out.println("display TreeMap size: "
                + displayMap.size());
        System.out.println("display TreeMap get(a): "
                + displayMap.get(a));
        System.out.println("HashMap size: " + hashMap.size());
    }
}
```

```bash
javac ComparatorMapDemo.java
java ComparatorMapDemo
```

**Đầu ra chính xác:**

```text
a.equals(b): false
display compare zero: true
strict compare zero: false
display TreeSet size: 2
strict TreeSet size: 3
display TreeMap size: 1
display TreeMap get(a): second
HashMap size: 2
```

Vì `displayOrder` chỉ so tiêu đề không phân biệt hoa/thường, `a` và `b` được `TreeSet` coi là tương đương; `TreeMap.put(b,"second")` tìm thấy khóa tương đương và thay giá trị `"first"` bằng `"second"`. `HashMap` vẫn giữ hai khóa vì record có ID và title khác nhau theo `equals`.

`strictOrder` có ba cấp:

1. Tiêu đề không phân biệt hoa/thường.
2. Tiêu đề phân biệt hoa/thường.
3. ID.

Với record `Article(int id, String title)` trong ví dụ và tiêu đề không `null`, `strictOrder.compare(a,b) == 0` chỉ khi **cả hai thành phần ID và title giống nhau**. Điều này nhất quán với `equals` mặc định của record, nên `strictSet` giữ cả ba bài.

**Cảnh báo quan trọng:** Thêm ID ở cuối comparator không tự động bảo đảm nhất quán với `equals` cho mọi lớp. Cần kiểm tra chính xác trường nào quyết định bằng nhau của lớp đó.

## 6. Chiều ngược lại: equals true nhưng Comparator khác 0

Không nhất quán có thể xuất hiện theo **hai hướng**:

- `compare(a,b) == 0` nhưng `a.equals(b) == false`: sorted set/map có thể coi hai bản ghi khác nhau là một khóa.
- `a.equals(b) == true` nhưng `compare(a,b) != 0`: sorted set/map có thể giữ hai đại diện mà `equals` coi là bằng nhau.

Ví dụ: một entity `Product` định nghĩa bằng nhau **chỉ bằng ID**, còn tên là thuộc tính hiển thị. Hai phiên bản của cùng ID có tên khác nhau vẫn bằng nhau theo `equals` nhưng khác nhau khi comparator chỉ so tên.

**Tệp `IdEqualityComparatorDemo.java`:**

```java
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class IdEqualityComparatorDemo {
    static final class Product {
        private final long id;
        private final String label;

        Product(long id, String label) {
            this.id = id;
            this.label = label;
        }

        long id() {
            return id;
        }

        String label() {
            return label;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj
                    || (obj instanceof Product other && id == other.id);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(id);
        }
    }

    public static void main(String[] args) {
        Product oldVersion = new Product(5L, "A");
        Product newVersion = new Product(5L, "Z");

        Comparator<Product> forDisplay =
                Comparator.comparing(Product::label);
        Comparator<Product> byIdentity =
                Comparator.comparingLong(Product::id);

        Set<Product> hashSet = new HashSet<>();
        hashSet.add(oldVersion);
        hashSet.add(newVersion);

        Set<Product> displaySet = new TreeSet<>(forDisplay);
        displaySet.add(oldVersion);
        displaySet.add(newVersion);

        Set<Product> identitySet = new TreeSet<>(byIdentity);
        identitySet.add(oldVersion);
        identitySet.add(newVersion);

        System.out.println("equals by id: "
                + oldVersion.equals(newVersion));
        System.out.println("display comparator nonzero: "
                + (forDisplay.compare(oldVersion, newVersion) != 0));
        System.out.println("identity comparator zero: "
                + (byIdentity.compare(oldVersion, newVersion) == 0));
        System.out.println("HashSet size: " + hashSet.size());
        System.out.println("TreeSet by display size: "
                + displaySet.size());
        System.out.println("TreeSet by id size: "
                + identitySet.size());
    }
}
```

```bash
javac IdEqualityComparatorDemo.java
java IdEqualityComparatorDemo
```

**Đầu ra chính xác:**

```text
equals by id: true
display comparator nonzero: true
identity comparator zero: true
HashSet size: 1
TreeSet by display size: 2
TreeSet by id size: 1
```

Ở đây `forDisplay` vẫn có ích để **sắp danh sách theo tên**. Nhưng nếu dùng nó trong `TreeSet`, tập hợp có thể nhận hai đại diện cùng ID. `byIdentity` mới nhất quán với `equals` của lớp `Product` vì cả hai chỉ quan tâm ID.

Muốn vừa **duy nhất theo ID**, vừa **hiển thị theo tên**, có thể giữ bản ghi trong `Map<Long,Product>` (ID là khóa), lấy các giá trị thành một danh sách rồi sắp danh sách theo tên. Hai trách nhiệm được thực hiện tách biệt, dễ kiểm tra hơn.

## 7. Tie-breaker giúp thứ tự xác định nhưng không thay thế equals

`thenComparing`, `thenComparingInt`, `thenComparingLong` dùng để bổ sung thứ tự khi các tiêu chí trước bằng nhau. Ví dụ với `Member`:

```java
Comparator<Member> ranking =
        Comparator.comparingInt(Member::score)
                .reversed()
                .thenComparing(Member::name)
                .thenComparingInt(Member::id);
```

Đoạn này là minh họa khi `Member` đã được khai báo như ở mục 3. Nó sắp theo điểm giảm dần, sau đó tên tăng dần, cuối cùng ID tăng dần.

Nếu kiểu `Member` là record gồm đúng `id`, `name`, `score` và tên không `null`, comparator trên nhất quán với `equals` của record: `compare == 0` khi và chỉ khi cả ba thành phần bằng nhau.

Tuy nhiên, với lớp entity có `equals` **chỉ xét ID**, comparator so tên trước rồi ID **không nhất thiết** nhất quán: hai entity cùng ID nhưng đổi tên vẫn có thể bị so khác nhau. Hãy luôn xét hợp đồng lớp cụ thể, không áp dụng mẹo “thêm ID cuối cùng” cho mọi trường hợp.

**Khi nào không cần tie-breaker?** Khi UI cố ý hiển thị hai người cùng điểm/tên với cùng thứ hạng hoặc chấp nhận thứ tự gốc. Khi đó giữ comparator hiển thị và dùng `List.sort` là hợp lý.

**Khi nào nên có tie-breaker?** Khi muốn thứ tự tái lập giữa nhiều lần tải dữ liệu, xuất báo cáo hoặc phân trang. Một khóa ổn định như ID có thể đảm bảo thứ tự ngay cả khi nhiều phần tử trùng tên và điểm.

### 7.1. Stable sort khác thứ tự xác định toàn cục

`List.sort` giữ thứ tự tương đối của các phần tử có `compare == 0` trong **danh sách nguồn**. Nhưng nếu danh sách đầu vào lấy từ nguồn có thứ tự không đảm bảo (như tập hợp hash), thứ tự các phần tử ngang nhau cũng không nhất thiết ổn định giữa nhiều lần tải dữ liệu.

Nếu cần phân trang nhất quán, bổ sung tiêu chí phụ đủ xác định thứ tự. Đây là vấn đề **thứ tự**, không phải lý do để thay đổi `equals`.

## 8. Comparator không quyết định hashCode

Hợp đồng `hashCode` gắn với `equals`:

```text
a.equals(b) == true
    ⇒ a.hashCode() == b.hashCode()
```

Comparator chỉ dùng để hiển thị không làm thay đổi phương thức `hashCode`. Hai bản ghi trùng khóa sắp xếp có thể có hash khác nhau nếu chúng không bằng nhau theo `equals`. Không có quy tắc bắt buộc `compare(a,b) == 0` phải kéo theo `hashCode` bằng nhau.

`TreeSet` và `TreeMap` cũng **không sử dụng hashCode** khi quyết định phần tử/khóa tương đương; chúng dựa vào `compare == 0`. Vì thế sửa `hashCode` không thể chữa lỗi TreeSet gộp những ID khác nhau do comparator chỉ xem tên.

Phân biệt rõ:

```text
Sắp xếp giao diện        -> Comparator với List.sort / Stream.sorted
Bằng nhau của đối tượng  -> equals
Tập hợp hoặc map băm     -> equals + hashCode
Tập hợp hoặc map có thứ tự -> Comparator / Comparable
```

## 9. Những vấn đề thường gặp với Comparator dùng cho UI

### 9.1. Sắp chuỗi không phân biệt hoa/thường

`String.CASE_INSENSITIVE_ORDER` giúp sắp tên theo thứ tự không phân biệt chữ hoa/thường. Nhưng `"java"` và `"JAVA"` có thể bị coi tương đương trong `TreeSet<String>` nếu dùng comparator ấy, dù `String.equals` của chúng trả `false`.

Nếu UI phải theo quy tắc ngôn ngữ, cân nhắc `java.text.Collator` với `Locale` phù hợp. Một collator cũng có thể coi các chuỗi khác nhau là tương đương ở một mức so sánh nhất định, nên hãy xác định rõ nó chỉ dùng sắp hiển thị hay còn được phép quyết định khóa.

### 9.2. Giá trị null

Khi khóa sắp xếp có thể `null`, nhiều comparator được tạo bằng `Comparator.comparing` cần một bộ so sánh hỗ trợ null. Ví dụ:

```java
Comparator<Article> byTitle =
        Comparator.comparing(
                Article::title,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
```

Đây là minh họa dùng record `Article` từ mục 5. Tiêu đề `null` được xếp cuối, nhưng nhiều bản ghi có `null` vẫn có thể cùng khóa sắp xếp. Điều đó không sao khi sắp `List`, nhưng cần thận trọng khi dùng làm comparator cho `TreeSet`.

### 9.3. So sánh số

Tránh viết comparator số nguyên bằng phép trừ:

```java
// Có thể overflow với các giá trị int lớn:
(a, b) -> a - b
```

Dùng `Integer.compare(a,b)` hoặc `Comparator.comparingInt(...)` để giữ đúng thứ tự. Với `double`, dùng `Double.compare` hoặc `comparingDouble` và hiểu quy tắc NaN/signed zero khi cần. Comparator không tự biến phép so sánh số thực thành kiểm tra gần đúng.

### 9.4. Trường mutable

Nếu một đối tượng được thêm vào `TreeSet`/`TreeMap` rồi trường quyết định thứ tự bị thay đổi, cấu trúc cây có thể không còn tra cứu đúng theo vị trí khóa ban đầu. Nên tránh sửa trường tham gia comparator khi khóa đang nằm trong cấu trúc có thứ tự. Với danh sách, có thể cập nhật dữ liệu và sắp lại theo yêu cầu giao diện.

## 10. Quy trình chọn giải pháp

1. **Xác định quan hệ bằng nhau từ nghiệp vụ:** ID duy nhất, toàn bộ record component, mã SKU, tổ hợp trường giá trị hay quy tắc rõ ràng khác.
2. **Xác định khóa hiển thị độc lập:** điểm, tên, ngày, giá tiền, trạng thái… và chiều tăng/giảm.
3. **Chọn collection:** `List` để hiển thị đầy đủ; `HashSet`/`HashMap` nếu muốn duy nhất theo `equals/hashCode`; `TreeSet`/`TreeMap` khi thật sự muốn dùng quan hệ tương đương theo comparator.
4. **Xem xét tie-breaker:** thêm khóa ổn định nếu cần thứ tự xác định, đặc biệt cho phân trang.
5. **Kiểm tra hai chiều nhất quán** nếu dùng sorted set/map: trường hợp `compare == 0` nhưng `equals == false`, và `equals == true` nhưng `compare != 0`.
6. **Kiểm tra dữ liệu biên:** khóa `null`, khác hoa/thường, trùng tên, thay đổi trạng thái hoặc nguồn không có thứ tự ổn định.

| Nhu cầu | Cách triển khai phù hợp |
| --- | --- |
| Xếp danh sách thành viên theo điểm/tên, giữ mọi ID | `List.sort(displayComparator)` |
| Sắp kết quả có thứ tự tái lập | Thêm tie-breaker đủ ổn định |
| Duy nhất sản phẩm theo ID | Dùng ID làm khóa map hoặc định nghĩa `equals/hashCode` theo ID |
| Tập hợp duy nhất theo `equals` | `HashSet` |
| Tập hợp có thứ tự và duy nhất theo cùng `equals` | `TreeSet` với comparator consistent-with-equals |
| Cố ý gộp chuỗi theo so sánh không phân biệt hoa/thường | `TreeSet`/`TreeMap` với comparator ấy khi chính sách gộp được chấp nhận |
| Nhóm đối tượng cùng khóa hiển thị mà không mất bản ghi | `Map<GroupKey, List<Item>>` hoặc thao tác grouping tương đương |

## 11. Các lỗi tư duy dễ gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “Comparator trả `0` nghĩa là `equals` trả `true`.” | Chỉ đúng với comparator **consistent with equals**. |
| “Hai phần tử cùng khóa sắp xếp là một đối tượng.” | Chúng có thể là hai ID khác nhau. |
| “`List.sort` loại bản ghi trùng thứ hạng.” | Không, nó chỉ thay đổi thứ tự. |
| “`TreeSet` dùng `equals` để kiểm tra trùng.” | Nó dùng `compare == 0` theo comparator/natural ordering. |
| “Thêm ID ở cuối luôn tạo comparator đúng với equals.” | Còn phụ thuộc trường nào quyết định `equals` của lớp. |
| “Nếu comparator dùng cho hiển thị, TreeMap cũng chỉ sắp thứ tự.” | Với `TreeMap`, comparator còn quyết định khóa tương đương, có thể thay giá trị. |
| “Sửa `hashCode` sẽ sửa lỗi TreeSet gộp nhầm.” | TreeSet không dùng hashCode để xác định phần tử trùng. |
| “Muốn sort theo tên thì phải ghi đè equals theo tên.” | Không, comparator có thể định nghĩa độc lập mà không đổi equality. |

## 12. Tự kiểm tra

**Câu 1.** Hai nhân viên khác ID nhưng cùng điểm và tên. Comparator theo điểm/tên trả `0`. Có được kết luận `equals` bằng `true` không?

**Đáp án:** Không. Comparator chỉ xác nhận cùng khóa thứ tự hiển thị; `equals` tùy hợp đồng lớp.

**Câu 2.** Sắp `List` theo điểm, hai người cùng điểm có bị mất một người không?

**Đáp án:** Không. `List.sort` giữ mọi phần tử, đồng thời stable nên giữ thứ tự tương đối của những phần tử comparator trả `0`.

**Câu 3.** Tại sao `TreeSet` có thể chỉ có một phần tử sau khi thêm hai đối tượng khác nhau theo `equals`?

**Đáp án:** Vì comparator của TreeSet có thể trả `0` cho cả hai, khiến TreeSet coi chúng là một phần tử tương đương.

**Câu 4.** Một `TreeMap` so khóa bài viết theo tiêu đề không phân biệt hoa/thường. Thêm `"Java"` rồi `"JAVA"` sẽ như thế nào?

**Đáp án:** Nếu comparator chỉ so tiêu đề không phân biệt hoa/thường, hai khóa tương đương; `put` thứ hai cập nhật giá trị mục đã có, không tạo mục thứ hai.

**Câu 5.** `Product.equals` chỉ so ID; comparator so tên rồi ID. Nó có nhất quán với equals không?

**Đáp án:** Không nhất thiết. Hai phiên bản có cùng ID nhưng khác tên bằng nhau theo `equals`, trong khi comparator có thể trả khác `0`.

**Câu 6.** Ứng dụng cần duy nhất sản phẩm theo ID và hiển thị theo tên. Nên xử lý ra sao?

**Đáp án:** Quản lý khóa bằng ID trong map hoặc collection phù hợp, rồi tạo danh sách dữ liệu để sắp theo tên. Nếu cần phân trang ổn định, thêm ID vào tiêu chí sắp danh sách.

## Kết luận

**`Comparator` xác định thứ tự, còn `equals` xác định sự bằng nhau của đối tượng.** Comparator dùng để hiển thị có thể trả `0` cho các bản ghi khác nhau theo `equals` mà không gây mất dữ liệu nếu chỉ dùng với `List.sort`. Nhưng `TreeSet`/`TreeMap` lấy `compare == 0` làm quan hệ tương đương của phần tử/khóa, do đó comparator hiển thị có thể làm gộp bản ghi hoặc cập nhật nhầm giá trị. Khi cần một sorted set/map tuân thủ quan hệ bằng nhau của đối tượng, phải kiểm tra cả hai chiều của điều kiện **consistent with equals**. Hãy tách chính sách bình đẳng nghiệp vụ khỏi yêu cầu sắp xếp giao diện.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparators-009">Trạng thái thay đổi trong Comparator làm kết quả sắp xếp không ổn định</a>

<details>
<summary>Xem chi tiết</summary>

# Trạng thái thay đổi trong Comparator làm kết quả sắp xếp không ổn định

## 1. Vấn đề cần hiểu

`Comparator<T>` biểu diễn quy tắc xác định thứ tự giữa hai đối tượng cùng kiểu `T`. Các API như `List.sort`, `Collections.sort`, `Stream.sorted`, `TreeSet`, `TreeMap` và `PriorityQueue` có thể gọi `compare(a, b)` nhiều lần để đưa ra quyết định.

Nếu một comparator **đọc trạng thái có thể thay đổi** (cờ đảo chiều, thời gian hiện tại, cấu hình toàn cục, số lần so sánh, dữ liệu từ dịch vụ bên ngoài hoặc thuộc tính mutable của phần tử), quan hệ thứ tự mà nó trả về có thể đổi giữa các lần gọi.

Ví dụ cùng hai đối tượng `a` và `b`:

```text
Lần 1: compare(a, b) = -1  -> a đứng trước b
Lần 2: compare(a, b) = +1  -> a đứng sau b
```

Nếu dữ liệu của hai đối tượng và tiêu chí so sánh về mặt nghiệp vụ lẽ ra không đổi, hai kết quả trái chiều cho thấy thuật toán đang nhận các quyết định mâu thuẫn. Sắp xếp có thể cho thứ tự không như mong đợi, hoặc một triển khai có thể phát hiện việc vi phạm hợp đồng và ném ngoại lệ. Không được mặc định thuật toán sẽ luôn phát hiện lỗi.

Điểm mấu chốt: **đối với một thao tác sắp xếp, bộ tiêu chí so sánh cần tạo ra một quan hệ thứ tự nhất quán từ đầu tới cuối**. Với collection có thứ tự luôn được duy trì như `TreeSet`/`TreeMap`, tiêu chí phải tiếp tục nhất quán trong thời gian các phần tử/khóa còn nằm trong cấu trúc.

## 2. Hợp đồng `Comparator.compare()` yêu cầu gì?

Với `Comparator<T> comparator`, `compare(x, y)` trả số nguyên âm, `0` hoặc dương để diễn tả `x` đứng trước, tương đương theo comparator, hoặc đứng sau `y`. **Giá trị số cụ thể** như `-1` hoặc `1` không có nghĩa đặc biệt; điều cần xét là dấu.

Javadoc `Comparator` quy định các tính chất logic của quan hệ thứ tự:

1. **Đổi hai đối số phải đảo dấu:** `signum(compare(x, y)) == -signum(compare(y, x))`. Một comparator không được đồng thời khẳng định `x < y` và `y < x`.
2. **Tính bắc cầu:** nếu `compare(x, y) > 0` và `compare(y, z) > 0` thì `compare(x, z) > 0`.
3. **Tính tương đương nhất quán:** nếu `compare(x, y) == 0` thì với mọi `z` hợp lệ, `compare(x, z)` và `compare(y, z)` phải cùng dấu.

Những tính chất trên mô tả **một quan hệ thứ tự nhất quán**. Nếu comparator thay đổi cách đánh giá trong khi thuật toán đang dựa vào nó, quan hệ tổng thể có thể không còn thỏa những giả định đó.

Lưu ý quan trọng: tài liệu nói `Comparator` có thứ tự **consistent with equals** khi `compare(a, b) == 0` tương đương với `a.equals(b)`. Đây là đặc tính rất nên có khi dùng `TreeSet`/`TreeMap` để đáp ứng hợp đồng `Set`/`Map`, nhưng một comparator không nhất quán với `equals` **không tự động** có nghĩa là nó thay đổi theo thời gian. Đó là **hai vấn đề riêng**.

## 3. Ba nguồn trạng thái khiến comparator đổi kết quả

### 3.1. Biến điều khiển bên ngoài

Ví dụ comparator dùng cờ `descending`:

```java
Comparator<Integer> comparator =
        (a, b) -> descending
                ? Integer.compare(b, a)
                : Integer.compare(a, b);
```

Đoạn trên là **mảnh mã minh họa** giả sử đã có biến trạng thái `descending`. Khi cờ đổi từ `false` sang `true`, cùng cặp `1` và `2` chuyển từ “1 trước 2” sang “1 sau 2”.

**Đảo chiều giữa hai lần sắp xếp độc lập có thể là yêu cầu nghiệp vụ hoàn toàn hợp lệ.** Rủi ro xuất hiện khi comparator đọc cờ **trong cùng một lần sắp xếp đang diễn ra**, hoặc khi một collection đã lập cấu trúc theo thứ tự cũ nhưng vẫn dùng chính comparator nay đã đổi hướng.

### 3.2. Biến đếm, random và đồng hồ

Những giá trị như `System.currentTimeMillis()`, `Instant.now()`, `Random.nextBoolean()` hoặc biến đếm tăng mỗi lần gọi đều có thể thay đổi trong khi hai đối tượng không đổi.

Ví dụ lỗi khái niệm:

```java
int[] calls = {0};
Comparator<Integer> bad = (a, b) -> {
    calls[0]++;
    return (calls[0] % 2 == 0 ? -1 : 1)
            * Integer.compare(a, b);
};
```

Ở đây, cùng lời gọi `bad.compare(1, 2)` có thể trả `-1` rồi `+1` chỉ vì số lần gọi khác nhau. Đây là **phương thức không đáng dùng để sắp xếp**. Việc mã trên có thể biên dịch không làm nó thỏa hợp đồng thứ tự.

Không nên biến “ai được so sánh trước”, “đã so bao nhiêu lần”, “đang là phút chẵn hay lẻ” thành tiêu chí thứ tự của những giá trị vốn phải xếp ổn định.

### 3.3. Thuộc tính mutable của chính phần tử

Không cần comparator có biến riêng thì kết quả vẫn có thể thay đổi. Ví dụ:

```java
Comparator<Employee> bySalary =
        Comparator.comparingInt(Employee::getSalary);
```

Đây là **đoạn mã minh họa**, giả sử có lớp `Employee` và phương thức `getSalary()`.

Nếu một thread hoặc tác vụ khác sửa lương của nhân viên trong lúc danh sách đang được sắp xếp, những lần gọi comparator có thể đọc trạng thái trước và sau cập nhật. Ngay cả khi comparator tự nó không ghi gì, dữ liệu đầu vào mutable cũng khiến thứ tự thiếu ổn định.

Với `TreeSet<Employee>` hoặc `TreeMap<Employee, ...>` sắp theo lương, việc sửa lương của phần tử/khóa đang lưu cũng làm cấu trúc đã sắp xếp không còn khớp tiêu chí mới. Bảng/cây không tự tái sắp xếp mỗi khi một field trong đối tượng bị sửa.

## 4. Ví dụ đầy đủ chạy được bằng Java 17

Ví dụ tách rõ hai tình huống:

- **An toàn:** chọn comparator tăng dần hoặc giảm dần cố định cho từng thao tác sắp xếp riêng biệt.
- **Không an toàn nếu dùng trong cùng thao tác:** comparator đọc cờ đang thay đổi, hoặc đảo kết quả theo số lần gọi. Chương trình chỉ gọi trực tiếp `compare` để chứng minh lỗi; **không gọi `sort` với comparator sai** vì kết quả và ngoại lệ có thể phụ thuộc thuật toán, kích thước dữ liệu và thứ tự gọi.

Tạo tệp **`ComparatorStateDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ComparatorStateDemo {
    record Task(String name, int priority) {}

    static String names(List<Task> tasks) {
        return tasks.stream().map(Task::name)
                .collect(Collectors.joining(", "));
    }

    public static void main(String[] args) {
        Comparator<Task> stable = Comparator
                .comparingInt(Task::priority)
                .thenComparing(Task::name);

        Task a = new Task("api", 3);
        Task b = new Task("db", 1);
        Task c = new Task("web", 2);

        List<Task> tasks = List.of(a, b, c);
        List<Task> ascending = new ArrayList<>(tasks);
        ascending.sort(stable);
        System.out.println("ascending: " + names(ascending));

        List<Task> descending = new ArrayList<>(tasks);
        descending.sort(stable.reversed());
        System.out.println("descending: " + names(descending));

        AtomicBoolean reverseFlag = new AtomicBoolean(false);
        Comparator<Task> mutable = (left, right) -> reverseFlag.get()
                ? stable.compare(right, left)
                : stable.compare(left, right);

        System.out.println("pair before: " + mutable.compare(b, c));
        reverseFlag.set(true);
        System.out.println("pair after: " + mutable.compare(b, c));

        int[] calls = {0};
        Comparator<Integer> changingEachCall = (left, right) -> {
            calls[0]++;
            int sign = (calls[0] % 2 == 0) ? -1 : 1;
            return sign * Integer.compare(left, right);
        };
        System.out.println("unstable first: "
                + changingEachCall.compare(1, 2));
        System.out.println("unstable second: "
                + changingEachCall.compare(1, 2));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 ComparatorStateDemo.java
java ComparatorStateDemo
```

**Stdout đã kiểm chứng bằng biên dịch và chạy Java:**

```text
ascending: db, web, api
descending: api, web, db
pair before: -1
pair after: 1
unstable first: -1
unstable second: 1
```

Giải thích:

1. `stable` so sánh `priority` rồi dùng `name` làm tiêu chí phụ khi bằng ưu tiên. Vì hai trường của record `Task` ở đây là `int` và `String`, và các instance này không bị sửa, thứ tự so sánh cố định.
2. `stable.reversed()` tạo comparator đảo ngược thứ tự *một cách cố định*. Hai lần sort độc lập có thể tạo ra kết quả tăng dần và giảm dần đúng yêu cầu.
3. `mutable.compare(b, c)` ban đầu trả `-1`, nhưng sau khi đổi `reverseFlag` lại trả `1`. Kết quả của cùng một phép so sánh đã phụ thuộc trạng thái bên ngoài.
4. `changingEachCall` trả `-1` rồi `1` cho đúng một cặp số, dù hai số không hề đổi. Nếu thuật toán sort dựa vào comparator này, các quyết định phân loại sẽ tự mâu thuẫn.
5. `AtomicBoolean` bảo đảm việc đọc/ghi flag có ngữ nghĩa bộ nhớ phù hợp giữa các thread; nó **không** làm một comparator có tiêu chí đổi chiều trở nên ổn định. **Thread-safe trong việc đọc biến không đồng nghĩa với nhất quán của quan hệ thứ tự.**

Phần an toàn của ví dụ dùng comparator khác nhau cho từng lần sort. Phần cố ý sai chỉ minh họa việc lặp lại so sánh; không coi output từ một lần sort sử dụng comparator thay đổi là quy tắc Java phải bảo đảm.

## 5. Điều gì có thể xảy ra trong các API sắp xếp?

### 5.1. `List.sort` và `Collections.sort`

Hai API sắp xếp dựa trên kết quả của nhiều phép so sánh. Khi comparator đổi tiêu chí giữa chừng, thuật toán có thể nhận thông tin “a trước b” ở một lần, “a sau b” ở lần khác.

Hậu quả có thể gồm:

- Danh sách sau khi sắp xếp **không còn tuân theo comparator ở thời điểm kết thúc**.
- Thứ tự phụ thuộc vào số lượng và trình tự so sánh mà thuật toán tình cờ thực hiện.
- Một số triển khai có thể ném `IllegalArgumentException` với thông báo dạng “Comparison method violates its general contract!”. Đây là hành vi **có thể xảy ra**, không phải ngoại lệ được bảo đảm cho mọi comparator sai.
- Nếu phần tử bị một thread khác thay đổi đồng thời, còn có các vấn đề an toàn truy cập collection và dữ liệu. Không được chỉ sửa comparator rồi mặc định mọi cuộc đua đã được loại bỏ.

Đừng viết kiểm thử kiểu “comparator sai phải luôn ném ngoại lệ”. Một mảng nhỏ có thể sắp xếp xong mà không phát hiện lỗi; điều đó **không chứng minh** comparator đúng hợp đồng.

### 5.2. `Stream.sorted(comparator)`

`Stream.sorted` cũng cần một quan hệ so sánh nhất quán khi thực thi terminal operation. Đặt comparator phụ thuộc vào đồng hồ, random hoặc cấu hình có thể bị sửa trong pipeline là sai về mặt thiết kế, ngay cả khi cú pháp stream trông bất biến.

Với stream song song, comparator có trạng thái mutable không đồng bộ còn có thể gây *data race*; kể cả trạng thái được đồng bộ, các thread đọc **những giá trị hợp lệ nhưng khác nhau** vẫn khiến thứ tự thiếu nhất quán. Đồng bộ chỉ là một phần của giải pháp.

### 5.3. `TreeSet` và `TreeMap`

Các collection này dùng **thứ tự do comparator cung cấp** (hoặc natural ordering) để tổ chức và tra cứu phần tử/khóa. Điểm khác biệt quan trọng với một lần sort: chúng cần tiêu chí thứ tự ổn định **trong suốt thời gian các mục đang được lưu**.

Ví dụ, một `TreeSet` được tạo với comparator tăng dần rồi thêm các phần tử. Nếu sau đó cờ bên ngoài làm chính comparator chuyển sang giảm dần, cấu trúc tìm kiếm đã được xây dựng theo quan hệ trước đó. Những thao tác `contains`, `add`, `remove` hoặc duyệt có thể cho kết quả bất ngờ; collection không tự xây dựng lại toàn bộ cây khi cờ đổi.

Ngoài ra, với `TreeSet`/`TreeMap`, **`compare(a,b) == 0`** quyết định hai phần tử/khóa tương đương theo thứ tự. Nếu comparator sử dụng field mutable, hai khóa từng khác nhau có thể trở nên “bằng nhau” theo tiêu chí mới, hoặc ngược lại. Điều đó ảnh hưởng tính duy nhất và tìm kiếm trong collection.

Ví dụ **mảnh mã minh họa** (không phải chương trình độc lập):

```java
TreeSet<Employee> employees =
        new TreeSet<>(Comparator.comparingInt(Employee::getSalary));

Employee employee = new Employee("An", 100);
employees.add(employee);

// Nếu setter thay đổi chính tiêu chí mà cây dùng để định vị:
employee.setSalary(1000);

// Cây không tự tái cấu trúc chỉ vì một thuộc tính của
// đối tượng đã lưu bị sửa; thao tác tra cứu có thể sai kỳ vọng.
```

Cần các import và khai báo lớp `Employee` nếu muốn chạy độc lập. Cách an toàn là tránh sửa thuộc tính khóa/sắp xếp trong khi phần tử nằm trong cây; nếu bắt buộc, **remove trước khi thay đổi**, sau đó sửa và **add lại**, hoặc tạo cấu trúc mới từ snapshot dữ liệu đã cố định. Không dựa vào việc `remove` bằng đối tượng đã sửa còn tìm thấy entry cũ.

### 5.4. `PriorityQueue`

`PriorityQueue` dùng comparator để quyết định phần tử có độ ưu tiên cao nhất theo quy ước của hàng đợi. Nếu comparator thay đổi tiêu chí sau khi các phần tử đã được thêm, cấu trúc heap hiện có không tự sửa vị trí mọi phần tử theo thứ tự mới. Một phép `peek` hoặc `poll` sau đó có thể không phản ánh ưu tiên vừa thay đổi.

Khi cần đổi toàn bộ chính sách ưu tiên, tạo một priority queue mới sử dụng comparator cố định mới và đưa các phần tử từ snapshot hiện tại vào, thay vì đổi trạng thái bên trong comparator cũ rồi tiếp tục dùng hàng đợi.

## 6. Ba loại “ổn định” dễ bị nhầm lẫn

### 6.1. Comparator nhất quán

Cùng dữ liệu, cùng tiêu chí, các lần so sánh phải biểu diễn cùng quan hệ thứ tự. Đây là trọng tâm của bài. Để comparator ổn định, đầu vào dùng cho phép so sánh và cấu hình của nó phải giữ giá trị phù hợp trong khoảng sử dụng.

### 6.2. Thuật toán sắp xếp ổn định (stable sort)

Một thuật toán sort được gọi là **stable** nếu hai phần tử được comparator xem là bằng nhau (`compare(a,b) == 0`) vẫn giữ **thứ tự tương đối ban đầu** trong kết quả. Theo tài liệu Java, `List.sort` là phép sắp xếp ổn định.

Điều đó **không cứu** một comparator thay đổi. Stable sort chỉ có ý nghĩa khi comparator đã cho một quan hệ thứ tự hợp lệ. Nếu `compare` thay kết quả giữa chừng, câu hỏi “hai phần tử bằng nhau theo comparator” không còn một câu trả lời nhất quán.

### 6.3. Kết quả ổn định qua nhiều lần chạy

Ngay cả comparator hoàn toàn đúng, nhiều phần tử có cùng khóa so sánh có thể tạo ra những kết quả khác khi **thứ tự đầu vào khác**. Một stable sort bảo toàn thứ tự của những phần tử được xem là tương đương, nên sự khác biệt ở thứ tự đầu vào vẫn có thể được giữ lại.

Nếu cần thứ tự kết quả hoàn toàn xác định, bổ sung **tie-breaker** phù hợp, chẳng hạn sắp theo `priority` rồi theo `id`:

```java
Comparator<Task> byPriorityAndId = Comparator
        .comparingInt(Task::priority)
        .thenComparing(Task::id);
```

Đây là **mảnh mã minh họa** giả sử `Task` có accessor `priority()` và `id()`. Comparator này chỉ tạo thứ tự xác định khi giá trị các accessor ổn định và tiêu chí phụ đủ để phân biệt những phần tử cần phân biệt. Nếu hai đối tượng khác nhau vẫn có cùng cả hai tiêu chí, chúng tiếp tục được comparator coi là bằng nhau.

## 7. Thiết kế comparator không phụ thuộc trạng thái thay đổi

### 7.1. Cố định cấu hình tại thời điểm tạo comparator

Thay vì đọc biến cấu hình đang có thể thay đổi ở **mỗi** lần `compare`, hãy chụp giá trị cấu hình trước khi bắt đầu:

```java
boolean descendingSnapshot = currentDescendingSetting;
Comparator<Item> base =
        Comparator.comparingInt(Item::getRank);

Comparator<Item> chosen = descendingSnapshot
        ? base.reversed()
        : base;
```

Đây là **đoạn mã minh họa**, giả sử `currentDescendingSetting`, `Item` và `getRank()` được định nghĩa phù hợp. Biến `descendingSnapshot` cố định hướng sắp xếp cho comparator được chọn. Nếu cần đổi chính sách, tạo comparator mới và sort lại.

Không nên đổi biến global giữa các lần so sánh rồi trông cậy thuật toán sẽ chỉ đọc nó một lần.

### 7.2. Chụp dữ liệu thuộc tính trước khi sắp xếp

Khi tiêu chí phụ thuộc dữ liệu có thể đổi (giá thị trường, điểm động, số lượt xem, trạng thái nghiệp vụ), có thể tạo **snapshot bất biến**:

```java
record RankedItem(String id, int scoreSnapshot) {}

Comparator<RankedItem> byScore =
        Comparator.comparingInt(RankedItem::scoreSnapshot)
                  .thenComparing(RankedItem::id);
```

Đây là **đoạn mã minh họa**. Quy trình phù hợp là đọc điểm số tại một thời điểm xác định, xây dựng các `RankedItem`, rồi sắp xếp snapshot. Khi nguồn dữ liệu thay đổi, tạo snapshot mới thay vì âm thầm đổi quan hệ so sánh của những phần tử đang nằm trong cấu trúc.

Record chỉ làm các trường thành `final`; nếu một record chứa tham chiếu đến một đối tượng mutable, **nội dung bên trong tham chiếu đó vẫn có thể thay đổi**. Chọn component bất biến hoặc sao chép phòng vệ khi cần.

### 7.3. Tách so sánh khỏi side effect

`compare` nên chỉ **đọc dữ liệu liên quan** và trả kết quả. Tránh:

- Tăng biến đếm **ảnh hưởng tiêu chí xếp hạng** mỗi lần so sánh.
- Gọi setter, cập nhật database hoặc ghi vào object ngay trong comparator.
- Gọi API bên ngoài để lấy giá trị mới cho cùng đối tượng ở các lần so sánh.
- Sinh số ngẫu nhiên để giải quyết trường hợp hòa.
- Đọc thời gian hiện tại để đảo thứ tự liên tục.

Nếu nghiệp vụ cần tie-break ngẫu nhiên, **sinh seed hoặc thứ tự phụ trước khi sort** và giữ giá trị đó cố định trong suốt phép sắp xếp. Nếu nghiệp vụ cần so thời điểm, hãy so các timestamp đã được lưu/chụp, thay vì hỏi “bây giờ là mấy giờ?” bên trong comparator mỗi lần.

### 7.4. Tránh tràn số và sai logic

Ngay cả comparator không có mutable state cũng có thể sai. Ví dụ:

```java
Comparator<Integer> wrong = (a, b) -> a - b;
```

Phép trừ có thể tràn `int` và gây đảo dấu không hợp lý với các giá trị biên. Dùng:

```java
Comparator<Integer> right = Integer::compare;
```

Khi sắp theo thuộc tính, ưu tiên `Comparator.comparingInt`, `comparingLong`, `comparing`, `thenComparing`; chúng giúp biểu đạt rõ tiêu chí và tránh nhiều lỗi so sánh thủ công.

Đây là vấn đề độc lập nhưng liên quan: một comparator “cố định trạng thái” mà vi phạm tính bắc cầu hay đổi dấu cũng không được xem là hợp lệ.

## 8. Comparator, đồng thời và quyền sửa dữ liệu

Trong hệ thống đa luồng, việc dùng `AtomicBoolean` hoặc `volatile` để đọc cờ có thể giúp thread này **thấy được** cập nhật từ thread khác. Nhưng nếu giá trị được nhìn thấy thay đổi giữa hai lời gọi, quan hệ thứ tự vẫn bị đảo. Cách giải quyết tốt hơn:

1. Chốt cấu hình trước khi bắt đầu thao tác sort.
2. Tạo snapshot nhất quán của các thuộc tính cần so sánh.
3. Không cho thread khác sửa dữ liệu/snapshot đang dùng trong sort.
4. Nếu cần công bố kết quả sau khi tính xong, công bố danh sách hoặc collection mới bằng chiến lược đồng bộ an toàn thích hợp.
5. Với collection luôn được tổ chức theo thứ tự như `TreeMap`, `TreeSet`, `PriorityQueue`, xây lại cấu trúc khi quan hệ xếp hạng thực sự thay đổi.

Đừng coi `Collections.synchronizedList`, `volatile` trên một field hoặc thread-safe flag là giải pháp tự động cho **một phép so sánh phụ thuộc nhiều dữ liệu**. Bất biến hoặc snapshot nhất quán mới giải quyết tính ổn định của tiêu chí.

## 9. Kiểm tra và review comparator

Có thể đánh giá một comparator bằng các câu hỏi sau:

- Khi gọi `compare(a,b)` nhiều lần với dữ liệu không đổi, kết quả **cùng dấu** không?
- `compare(a,b)` và `compare(b,a)` có dấu đối nhau không?
- Các quan hệ `a > b`, `b > c` có kéo theo `a > c` không?
- Khi `compare(a,b)==0`, dấu so sánh của `a` và `b` với một `c` có giống nhau không?
- Comparator có đọc `now()`, random, môi trường, config mutable, cơ sở dữ liệu, hoặc field mutable của phần tử không?
- Những giá trị đó có được **cố định trong khoảng sử dụng** không?
- Nếu dùng `TreeSet/TreeMap`, thứ tự có nhất quán với `equals` khi ứng dụng cần tuân thủ đầy đủ hợp đồng `Set/Map` không?
- Sau khi phần tử được đưa vào cấu trúc có thứ tự, có code nào sửa tiêu chí định vị của nó không?

Một phép thử lặp ngắn (đặt **bên trong phương thức** của một lớp kiểm thử có comparator và dữ liệu hợp lệ):

```java
int first = Integer.signum(comparator.compare(a, b));
int second = Integer.signum(comparator.compare(a, b));
if (first != second) {
    throw new AssertionError("Comparator khong on dinh");
}
```

Đây là **mảnh kiểm thử minh họa**, không phải một chương trình độc lập. Nó bắt được một số lỗi biến trạng thái, nhưng không chứng minh mọi đầu vào hoặc mọi lịch thực thi đa luồng. Cần kết hợp với kiểm thử các bộ ba phần tử, dữ liệu biên, và review đường đọc dữ liệu của comparator.

Nếu một lần chạy sort có kết quả bất thường hoặc ném `IllegalArgumentException` liên quan hợp đồng so sánh, hãy ưu tiên kiểm tra **logic, dữ liệu và trạng thái comparator** trước khi điều tra thuật toán sort. Tuy vậy, ngoại lệ đó không phải phương pháp chẩn đoán duy nhất và không xuất hiện ở mọi trường hợp.

## 10. Tình huống thực tế

**Bảng xếp hạng:** điểm số cập nhật liên tục trong database. Không gọi API lấy điểm mới ngay trong `compare`; thay bằng chụp điểm tại thời điểm công bố, rồi sắp xếp các snapshot.

**UI có nút đảo chiều:** mỗi lần nhấn nút tạo danh sách sắp xếp mới với `comparator.reversed()`. Đây là hành vi hợp lệ; đảm bảo nút nhấn giữa một thao tác sort không làm comparator đang chạy đổi hướng.

**Hàng đợi ưu tiên công việc:** khi chính sách ưu tiên thay đổi, tạo `PriorityQueue` mới với comparator mới và nạp lại các công việc cần giữ. Đừng đổi biến ưu tiên mà heap cũ đang tham chiếu.

**Tập nhân viên có thứ tự theo lương:** nếu lương thay đổi, đừng sửa trực tiếp trường xếp hạng khi đối tượng vẫn nằm trong `TreeSet`, rồi chờ cây tự di chuyển phần tử. Thiết kế theo khóa bất biến hoặc quản lý thao tác gỡ/thêm bằng giá trị cũ và mới.

**Sắp xếp theo tên ngôn ngữ:** có thể dùng `Collator` để so chuỗi theo locale, nhưng không thay đổi cấu hình hay dùng chung một đối tượng cấu hình có thể sửa trong lúc sort. Khởi tạo bộ so sánh có cấu hình cố định cho thao tác cần làm.

## 11. Tổng kết kiến thức cần nhớ

- Comparator phải cung cấp **quan hệ thứ tự nhất quán** cho dữ liệu mà thuật toán đang xử lý; cùng cặp không nên đổi dấu chỉ vì một biến ngoài đổi hoặc số lần gọi tăng.
- Tránh đọc thời gian hiện tại, random, config mutable, số đếm hay thuộc tính đang bị sửa trong `compare`.
- **Thay comparator giữa hai lần sort độc lập là hợp lệ** nếu mỗi lần sort sử dụng một tiêu chí nhất quán; **đổi tiêu chí trong lúc sort** mới gây rủi ro trực tiếp.
- `TreeSet`, `TreeMap` và `PriorityQueue` lưu cấu trúc tổ chức theo comparator; khi quan hệ thứ tự đổi, thường phải xây lại cấu trúc.
- Stable sorting algorithm, thread-safe flag và “comparator nhất quán” là các khái niệm khác nhau; một cái không tự bảo đảm cái còn lại.
- Dùng comparator cố định, snapshot dữ liệu, tie-breaker rõ ràng và các helper như `Comparator.comparingInt`/`thenComparing` để có kết quả dễ kiểm chứng.
- Java có thể phát hiện vi phạm ở một số tình huống và ném ngoại lệ, nhưng **không bảo đảm luôn phát hiện**; trách nhiệm bảo toàn hợp đồng thuộc về cách thiết kế comparator.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="comparators-010">Comparator.equals() khác với việc compare() trả về 0 cho hai phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# `Comparator.equals()` khác với `compare() == 0` cho hai phần tử

## 1. Hai lời gọi có ý nghĩa khác nhau

Trong Java, `Comparator<T>` biểu diễn **một quy tắc so sánh và sắp xếp các đối tượng kiểu `T`**. Interface này khai báo phương thức cốt lõi `int compare(T a, T b)` và có phương thức `boolean equals(Object obj)` kế thừa từ `Object`; một lớp comparator **có thể** ghi đè `equals` để định nghĩa khi nào hai **comparator** tương đương.

Điểm quan trọng là **đối tượng đang được so sánh khác nhau**:

- `comparator.compare(a, b) == 0`: hỏi **hai phần tử `a` và `b` có ngang nhau theo thứ tự mà comparator định nghĩa hay không**. Kết quả không trực tiếp nói rằng `a.equals(b)` là `true`.
- `comparator.equals(otherComparator)`: hỏi **đối tượng comparator hiện tại có bằng một đối tượng comparator khác hay không**. Đây là một lời gọi `equals(Object)` trên chính comparator, không phải phép kiểm tra bằng nhau của hai phần tử dữ liệu.

Ví dụ sơ lược:

~~~java
Comparator<String> comparator = String.CASE_INSENSITIVE_ORDER;
boolean samePosition = comparator.compare("Java", "JAVA") == 0;
boolean sameValue = "Java".equals("JAVA");
~~~

`samePosition` là `true` vì comparator không phân biệt chữ hoa/thường; `sameValue` là `false` vì `String.equals` có phân biệt chữ hoa/thường. Trong khi đó, `comparator.equals(anotherComparator)` là câu hỏi về **hai chiến lược sắp xếp**.

**Không đánh đồng ba biểu thức**:

| Biểu thức | Chủ thể được so sánh | Ý nghĩa |
| --- | --- | --- |
| `a == b` | Hai tham chiếu phần tử | Có cùng đối tượng hay không |
| `a.equals(b)` | Hai phần tử | Có bằng nhau theo hợp đồng `equals` của phần tử hay không |
| `c.compare(a, b) == 0` | Hai phần tử qua bộ so sánh `c` | Có tương đương trong **thứ tự do `c` định nghĩa** hay không |
| `c.equals(d)` | Hai comparator `c` và `d` | Có được comparator coi là hai bộ so sánh bằng nhau hay không |

## 2. Giá trị âm, bằng 0, dương của `compare(a, b)`

`compare(a,b)` trả số âm nếu a đứng trước b, số dương nếu đứng sau, và `0` nếu hai phần tử thuộc cùng nhóm tương đương theo **quy tắc đang chọn**. Không bắt buộc trả chính xác `-1` hoặc `1`, và cũng không có nghĩa `a.equals(b)` phải là `true` khi kết quả so sánh bằng 0.

~~~text
a ~ b  ⇔  comparator.compare(a, b) == 0
~~~

Ví dụ, một comparator theo độ dài sẽ trả `0` cho `"cat"` và `"dog"`, dù `String.equals` trả `false`. Hợp đồng vẫn yêu cầu đối dấu khi đảo đối số, tính bắc cầu và quan hệ nhất quán với mọi phần tử thứ ba; chi tiết toán học nằm ở bài về hợp đồng dấu và nhóm 0.

Điểm đặc biệt cần nhớ trong **bài này** là `comparatorA.equals(comparatorB)` hoàn toàn không sử dụng `compare(a,b)` để kiểm tra bằng nhau của **hai phần tử**. Nó hỏi hai *đối tượng bộ so sánh* có bằng nhau không; ý nghĩa chính xác được phân tích ở mục tiếp theo.
## 3. Ví dụ chạy được: bằng nhau trong thứ tự khác bằng nhau của `String`

Tạo tệp `ComparatorZeroDemo.java`:

~~~java
import java.util.Comparator;

public class ComparatorZeroDemo {
    public static void main(String[] args) {
        Comparator<String> ignoreCase = String.CASE_INSENSITIVE_ORDER;

        String a = new String("Java");
        String b = new String("JAVA");
        String c = "Spring";

        System.out.println("a == b: " + (a == b));
        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("compare(a,b)==0: " + (ignoreCase.compare(a, b) == 0));
        System.out.println("compare(a,c)<0: " + (ignoreCase.compare(a, c) < 0));
        System.out.println("compare(c,a)>0: " + (ignoreCase.compare(c, a) > 0));
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 ComparatorZeroDemo.java
java ComparatorZeroDemo
~~~

Stdout:

~~~text
a == b: false
a.equals(b): false
compare(a,b)==0: true
compare(a,c)<0: true
compare(c,a)>0: true
~~~

Phân tích:

1. `new String("Java")` và `new String("JAVA")` tạo hai đối tượng String riêng.
2. `String.equals` phân biệt hoa/thường nên trả `false`.
3. `String.CASE_INSENSITIVE_ORDER` bỏ qua khác biệt hoa/thường khi so, nên `compare(a,b)` trả `0`.
4. Với `"Java"` và `"Spring"`, thứ tự không phân biệt hoa/thường vẫn đặt `Java` trước `Spring`.

Kết quả trên chứng minh rằng **tương đương trong comparator không đồng nghĩa bằng nhau theo `equals` của phần tử**.

## 4. `Comparator.equals(Object)` thực sự so sánh điều gì?

`Comparator.equals(Object)` mang hợp đồng của `Object.equals` (phản xạ, đối xứng, bắc cầu, nhất quán và không bằng `null`). Tài liệu `Comparator` còn nêu ý nghĩa đặc biệt nếu comparator **tự định nghĩa** sự bằng nhau: hai comparator được coi bằng nhau phải tạo ra **cùng thứ tự**, tức dấu của kết quả `compare` tương ứng giống nhau đối với mọi cặp phần tử trong miền so sánh.

Có hai trường hợp quan trọng:

**Trường hợp 1: comparator không ghi đè `equals`.** Phương thức kế thừa `Object.equals` thường chỉ trả `true` khi đang so **chính cùng một instance comparator**. Hai instance khác nhau có cùng thuật toán so sánh vẫn có thể `equals` trả `false`. Điều này hợp lệ: không bắt buộc comparator phải so bằng nhau theo “mã thuật toán giống nhau”.

**Trường hợp 2: lớp comparator chủ động ghi đè `equals`.** Lớp có thể cho hai comparator bằng nhau nếu chúng giữ cùng cấu hình và chắc chắn tạo cùng quy tắc thứ tự. Nếu ghi đè `equals` thì phải ghi đè `hashCode` tương ứng theo hợp đồng đối tượng. Kết quả `equals` là `true` phải đủ để bảo đảm chúng có cùng thứ tự; ngược lại, **hai comparator cùng hành vi so sánh chưa bắt buộc `equals` trả `true`**.

Không thể chỉ thử vài cặp dữ liệu rồi tuyên bố hai comparator tương đương với mọi giá trị. Hợp đồng về thứ tự là yêu cầu trên **toàn bộ miền giá trị mà chúng hỗ trợ**.

### Ví dụ chạy được: hai comparator cùng thuật toán nhưng không `equals`

Tạo tệp `ComparatorIdentityDemo.java`:

~~~java
import java.util.Comparator;

public class ComparatorIdentityDemo {
    static final class ByLength implements Comparator<String> {
        @Override
        public int compare(String a, String b) {
            return Integer.compare(a.length(), b.length());
        }
        // Không override equals(Object): kế thừa Object.equals.
    }

    public static void main(String[] args) {
        ByLength first = new ByLength();
        ByLength second = new ByLength();

        System.out.println("same comparator object: " + (first == second));
        System.out.println("comparators equal: " + first.equals(second));
        System.out.println("first compares: " + first.compare("dog", "cat"));
        System.out.println("second compares: " + second.compare("dog", "cat"));
        System.out.println("element objects equal: " + "dog".equals("cat"));
    }
}
~~~

~~~bash
javac -encoding UTF-8 ComparatorIdentityDemo.java
java ComparatorIdentityDemo
~~~

Stdout:

~~~text
same comparator object: false
comparators equal: false
first compares: 0
second compares: 0
element objects equal: false
~~~

Hai `ByLength` là hai object comparator riêng, vì vậy `first.equals(second)` là `false`. Cả hai đều cho `"dog"` và `"cat"` cùng độ dài, nên `compare(...)` trả `0`. Điều đó **không cho phép kết luận** rằng `first.equals(second)` phải là `true`; nó còn cho thấy `compare == 0` không liên quan trực tiếp đến `String.equals`.

## 5. Ví dụ ghi đè `Comparator.equals` theo cấu hình thứ tự

Khi ứng dụng có một loại comparator rõ ràng với tham số cấu hình xác định thứ tự, có thể tự định nghĩa `equals/hashCode` cho **đối tượng comparator**.

Tạo tệp `ConfiguredComparatorDemo.java`:

~~~java
import java.util.Comparator;

public class ConfiguredComparatorDemo {
    static final class ByRemainder implements Comparator<Integer> {
        private final int divisor;

        ByRemainder(int divisor) {
            if (divisor <= 0) {
                throw new IllegalArgumentException("divisor must be positive");
            }
            this.divisor = divisor;
        }

        @Override
        public int compare(Integer a, Integer b) {
            return Integer.compare(
                    Math.floorMod(a, divisor),
                    Math.floorMod(b, divisor)
            );
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ByRemainder other)) return false;
            return divisor == other.divisor;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(divisor);
        }
    }

    public static void main(String[] args) {
        ByRemainder by10a = new ByRemainder(10);
        ByRemainder by10b = new ByRemainder(10);
        ByRemainder by5 = new ByRemainder(5);

        System.out.println("same instance: " + (by10a == by10b));
        System.out.println("same comparator configuration: "
                + by10a.equals(by10b));
        System.out.println("matching comparator hashes: "
                + (by10a.hashCode() == by10b.hashCode()));
        System.out.println("different configuration: " + by10a.equals(by5));
        System.out.println("12 versus 22: " + by10a.compare(12, 22));
        System.out.println("12.equals(22): " + Integer.valueOf(12).equals(22));
    }
}
~~~

~~~bash
javac -encoding UTF-8 ConfiguredComparatorDemo.java
java ConfiguredComparatorDemo
~~~

Stdout:

~~~text
same instance: false
same comparator configuration: true
matching comparator hashes: true
different configuration: false
12 versus 22: 0
12.equals(22): false
~~~

Trong ví dụ này, hai comparator có cùng `divisor` luôn áp dụng cùng công thức nên `equals` có thể trả `true`, và `hashCode` dựa trên cùng trường đó. Tuy nhiên hai số `12` và `22` vẫn là **hai Integer không bằng nhau**, dù kết quả chia lấy dư cho 10 đều bằng 2 và comparator trả 0.

`ByRemainder` cố ý chỉ xét phần dư nên **không nhất quán với `Integer.equals`**. Đây là một thứ tự theo nhóm/phần dư, không phải thứ tự số nguyên tự nhiên. Không áp dụng kiểu comparator này cho `TreeSet<Integer>` nếu muốn tập chứa **mọi số nguyên khác nhau**.

Lưu ý `compare` trong ví dụ yêu cầu các đối số `Integer` không `null` (unboxing vào `Math.floorMod` có thể ném `NullPointerException` nếu nhận null). Nếu cần hỗ trợ null, có thể bao comparator bằng `Comparator.nullsFirst` hoặc `Comparator.nullsLast` với chính sách phù hợp.

## 6. “Consistent with equals” là thuộc tính của quan hệ sắp xếp

Một thứ tự do `Comparator` định nghĩa được gọi là **nhất quán với `equals` của phần tử** khi, với mọi `a`, `b` thuộc miền so sánh:

~~~text
comparator.compare(a, b) == 0  ⇔  a.equals(b)
~~~

Đây là quan hệ giữa **`compare` và `equals` của phần tử**, hoàn toàn khác với `comparator.equals(otherComparator)`.

Ví dụ:

- `Comparator.naturalOrder()` trên `Integer` có quan hệ `compare(a,b)==0` phù hợp với `Integer.equals`.
- `String.CASE_INSENSITIVE_ORDER` **không** nhất quán với `String.equals` vì `"Java"` và `"JAVA"` có thứ tự bằng nhau nhưng `equals` là `false`.
- Comparator `Comparator.comparingInt(String::length)` không nhất quán với `String.equals` vì hai chuỗi khác nhau vẫn có thể cùng độ dài.
- Một comparator chỉ so `Person.id` chỉ nhất quán với `Person.equals` nếu `equals` của Person cũng định nghĩa bằng nhau đúng theo `id` trong toàn bộ miền đối tượng.

Comparator không nhất quán với `equals` không phải lúc nào cũng là lỗi. Nó phù hợp cho **sắp xếp theo nhóm, so không phân biệt hoa/thường, gom các giá trị cùng khóa**. Nhưng khi dùng trong những collection xác định tính duy nhất dựa trên so sánh thứ tự, sự khác biệt có hệ quả lớn.

### Phân biệt quan hệ bằng nhau với việc ưu tiên sắp xếp

Nếu muốn sắp theo độ dài nhưng **không** coi hai chuỗi cùng độ dài là một giá trị duy nhất, có thể thêm tiêu chí phụ:

~~~java
Comparator<String> byLengthThenNatural =
        Comparator.comparingInt(String::length)
                  .thenComparing(Comparator.naturalOrder());
~~~

Đoạn mã này là **mảnh minh họa trong ngữ cảnh đã `import java.util.Comparator;`**, không phải một tệp chương trình độc lập. `thenComparing` chỉ áp dụng so thứ hai khi tiêu chí độ dài trả 0. Với hai chuỗi khác nhau `"dog"` và `"cat"`, tiêu chí phụ phân biệt được chúng. Trên String, cách so tổng hợp này nhất quán với `String.equals`: trả 0 chỉ khi nội dung chuỗi giống nhau (trong miền không null). Đối với loại phần tử khác, phải chọn tiêu chí phụ thực sự phân biệt các đối tượng mà `equals` xem là khác.

## 7. Hệ quả đối với `TreeSet`

`TreeSet` là tập có sắp xếp. Khi được tạo với một comparator, nó dùng kết quả so sánh thứ tự để quyết định vị trí và **tính trùng nhau** của phần tử: nếu `compare(a,b)==0`, tập xem chúng như cùng một phần tử theo cấu trúc cây.

Tạo tệp `TreeSetComparatorDemo.java`:

~~~java
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class TreeSetComparatorDemo {
    public static void main(String[] args) {
        Comparator<String> ignoreCase = String.CASE_INSENSITIVE_ORDER;

        Set<String> tree = new TreeSet<>(ignoreCase);
        System.out.println("tree first add: " + tree.add("Java"));
        System.out.println("tree second add: " + tree.add("JAVA"));
        System.out.println("tree size: " + tree.size());
        System.out.println("tree contains java: " + tree.contains("java"));
        System.out.println("tree first element: " + tree.iterator().next());

        Set<String> hash = new HashSet<>();
        hash.add("Java");
        hash.add("JAVA");
        System.out.println("hash size: " + hash.size());
    }
}
~~~

~~~bash
javac -encoding UTF-8 TreeSetComparatorDemo.java
java TreeSetComparatorDemo
~~~

Stdout:

~~~text
tree first add: true
tree second add: false
tree size: 1
tree contains java: true
tree first element: Java
hash size: 2
~~~

Giải thích:

1. `TreeSet` đưa `"Java"` vào tập.
2. Khi thêm `"JAVA"`, comparator không phân biệt hoa/thường trả 0 so với `"Java"`, nên `TreeSet` coi đây là phần tử đã có và `add` trả `false`.
3. Tập vẫn chứa phần tử đầu tiên `"Java"`; `contains("java")` trả `true` theo comparator.
4. `HashSet` dùng `hashCode` và `equals` của `String`, hai chuỗi khác nội dung về chữ hoa/thường nên cả hai được lưu.

**Điểm thiết kế quan trọng:** Các lớp sorted set hoạt động đúng theo **quy tắc comparator**, nhưng để đáp ứng trọn hợp đồng `Set` về tính bằng nhau giữa phần tử, thứ tự phải **nhất quán với `equals`**. Khi comparator không nhất quán, hành vi của sorted set vẫn được xác định theo cấu trúc cây nhưng khác trực giác về `Set` thông thường và có thể vi phạm hợp đồng chung của `Set` đối với tập dựa trên `equals`.

Dùng `TreeSet` với comparator không nhất quán là hợp lý khi bạn **thật sự muốn gộp những phần tử thuộc cùng lớp thứ tự**. Nếu muốn chỉ sắp xếp theo một trường nhưng vẫn giữ tất cả các đối tượng khác nhau, hãy bổ sung tiêu chí phụ nhất quán.

## 8. Hệ quả tương tự với `TreeMap`

`TreeMap` sử dụng comparator để sắp xếp **khóa** và xác định khi nào hai khóa được coi tương đương. Nếu comparator trả 0, thao tác `put` cho khóa mới có thể **ghi đè giá trị** của entry đã có, dù `key1.equals(key2)` là `false`.

Tạo tệp `TreeMapComparatorDemo.java`:

~~~java
import java.util.Map;
import java.util.TreeMap;

public class TreeMapComparatorDemo {
    public static void main(String[] args) {
        Map<String, String> map =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        System.out.println("first put: " + map.put("Java", "first"));
        System.out.println("second put: " + map.put("JAVA", "second"));
        System.out.println("size: " + map.size());
        System.out.println("lookup java: " + map.get("java"));
        System.out.println("original key kept: " + map.keySet().iterator().next());
        System.out.println("key equals: " + "Java".equals("JAVA"));
    }
}
~~~

~~~bash
javac -encoding UTF-8 TreeMapComparatorDemo.java
java TreeMapComparatorDemo
~~~

Stdout:

~~~text
first put: null
second put: first
size: 1
lookup java: second
original key kept: Java
key equals: false
~~~

Lần `put("JAVA", "second")` tìm thấy một khóa tương đương thứ tự với `"Java"`, nên cập nhật **value** của entry đó thay vì thêm entry mới. Tên khóa lưu ban đầu trong cây vẫn là `"Java"` trong ví dụ. Vì thế:

- `TreeMap` có một entry thay vì hai.
- `get("java")` tìm được giá trị mới `"second"`.
- `String.equals("Java","JAVA")` vẫn là `false`.

Giống `TreeSet`, `TreeMap` hoạt động theo comparator được cung cấp; muốn tuân trọn hợp đồng chung của `Map` dựa trên `equals` của khóa thì comparator cần nhất quán với `equals`.

## 9. Điều gì xảy ra khi dùng `sort`, `min/max` hoặc `binarySearch`?

Ở phép **sắp xếp** (`List.sort`, `Arrays.sort`, `Collections.sort`), comparator chỉ quyết định thứ tự. Hai phần tử được `compare` trả 0 có thể vẫn là hai đối tượng riêng và vẫn có mặt trong danh sách sau khi sort. Sắp xếp không tự gộp các phần tử.

Ví dụ một danh sách chứa `"Java"` và `"JAVA"` sau khi `sort(String.CASE_INSENSITIVE_ORDER)` vẫn có **hai phần tử**. Tính ổn định của phương thức sắp xếp được công bố cho những API cụ thể, ví dụ `List.sort` đảm bảo không đảo thứ tự của các phần tử mà comparator coi là bằng nhau.

Tương tự:

- `Collections.min/max` với comparator chọn cực trị **theo thứ tự của comparator**; một phần tử cực trị không nhất thiết bằng phần tử khác đang cùng lớp thứ tự theo `equals`.
- `Collections.binarySearch(list, key, comparator)` có thể tìm một phần tử mà `compare(found,key)==0`, không cần `found.equals(key)`; danh sách phải được sắp theo comparator phù hợp trước. Nếu có nhiều kết quả tương đương thứ tự, không bảo đảm chọn vị trí cụ thể nào.

### Ví dụ chạy được: danh sách không tự gộp phần tử

Tạo tệp `SortKeepsElementsDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SortKeepsElementsDemo {
    public static void main(String[] args) {
        Comparator<String> byLength =
                Comparator.comparingInt(String::length);

        List<String> words = new ArrayList<>(
                List.of("dog", "a", "cat")
        );
        words.sort(byLength);

        System.out.println("sorted: " + words);
        System.out.println("size: " + words.size());
        System.out.println("dog vs cat: " + byLength.compare("dog", "cat"));
        System.out.println("dog equals cat: " + "dog".equals("cat"));
    }
}
~~~

~~~bash
javac -encoding UTF-8 SortKeepsElementsDemo.java
java SortKeepsElementsDemo
~~~

Stdout:

~~~text
sorted: [a, dog, cat]
size: 3
dog vs cat: 0
dog equals cat: false
~~~

Hai chuỗi cùng độ dài vẫn được giữ. `List.sort` là phép **sắp xếp**, còn `TreeSet` áp dụng quy tắc tương đương thứ tự vào **tính duy nhất** phần tử.

## 10. Cách tạo comparator thích hợp với ý định nghiệp vụ

### Nếu cần gom phần tử theo một khóa

Nếu miền nghiệp vụ coi tên không phân biệt hoa/thường là cùng một khóa, `String.CASE_INSENSITIVE_ORDER` với `TreeMap` hoặc `TreeSet` có thể phù hợp. Hãy mô tả rõ rằng tính duy nhất là theo **khóa chuẩn hóa** hoặc **lớp tương đương do comparator quy định**; đừng nhầm đây là `String.equals`.

Nếu muốn quản lý chính xác cách chuẩn hóa, cân nhắc chuyển dữ liệu sang một kiểu khóa riêng có `equals/hashCode` phản ánh quan hệ đó, ví dụ cấu trúc chứa tên đã chuẩn hóa một cách nhất quán. Với ngôn ngữ tự nhiên và đa ngôn ngữ, so sánh/chuẩn hóa Unicode, locale và quy tắc chữ hoa/thường cần được thiết kế cụ thể; không nên mặc định mọi cách so chuỗi đều có cùng hợp đồng.

### Nếu cần bảo tồn mọi phần tử khác nhau

Comparator nên có **tie-breaker** để phân biệt các phần tử khác nhau theo `equals`. Với String:

~~~java
Comparator<String> byLengthThenText =
        Comparator.comparingInt(String::length)
                  .thenComparing(Comparator.naturalOrder());
~~~

Với lớp tùy biến, nếu `equals` dựa trên `id`, có thể thêm so sánh `id` làm tie-breaker (miễn là `id` có ngữ nghĩa phù hợp). Nhưng **đừng dùng `System.identityHashCode` như một tie-breaker duy nhất** để bảo đảm phân biệt mọi đối tượng: hash có thể trùng nhau. Cũng không thể chỉ thêm tiêu chí phụ tùy ý rồi mặc định comparator đã nhất quán với mọi định nghĩa `equals`.

### Nếu muốn so hai comparator có bằng nhau hay không

Hãy dùng `comparatorA.equals(comparatorB)` khi mục tiêu **thật sự là đối tượng comparator**; kết quả `false` có thể chỉ do chúng là hai instance khác nhau. Trong nhiều ứng dụng, không cần kiểm tra sự bằng nhau của hai comparator: chỉ cần chọn đúng một comparator và truyền dùng lại.

Nếu xây dựng một **lớp comparator có cấu hình** và muốn dùng nó làm khóa cache/configuration, có thể triển khai `equals` và `hashCode` dựa trên các tham số xác định thứ tự. Điều này là lựa chọn thiết kế **độc lập** với quy tắc `compare(a,b)==0` trên dữ liệu.

## 11. Những nhầm lẫn thường gặp

| Nhầm lẫn | Sự thật |
| --- | --- |
| `c.equals(d)` tương đương `c.compare(a,b)==0`. | Hai vế có **đối tượng so sánh khác nhau**: comparator với comparator, phần tử với phần tử. |
| `compare(a,b)==0` thì `a.equals(b)` luôn `true`. | Chỉ đúng khi comparator **nhất quán với equals** trên miền phần tử liên quan. |
| Hai comparator dùng cùng thuật toán thì `c.equals(d)` luôn `true`. | Không nhất thiết: `equals` có thể kế thừa từ `Object` và so danh tính instance. |
| `c.equals(d)==false` nghĩa là tồn tại cặp phần tử mà hai comparator sắp khác nhau. | Sai: equals trả false **không chứng minh** hai comparator tạo thứ tự khác. |
| Comparator phải trả đúng `-1`, `0`, `1`. | Hợp đồng chỉ quy định dấu âm/0/dương. |
| `TreeSet` quyết định phần tử trùng bằng `a.equals(b)`. | Với comparator cung cấp, cây dùng `compare(a,b)==0` để quyết định tương đương thứ tự. |
| `TreeMap.put` hai khóa có `equals` khác nhau luôn tạo hai entry. | Có thể chỉ còn một entry nếu comparator coi chúng tương đương (`compare==0`). |
| `sort` loại bớt phần tử có `compare==0`. | Sắp xếp không gộp phần tử; danh sách giữ nguyên số lượng. |
| Chỉ cần thêm tie-breaker theo một hash là luôn có total order đúng. | Hash có thể trùng; cần tiêu chí phụ phù hợp với ngữ nghĩa đối tượng. |
| `Comparator.equals` phải được ghi đè trong mọi comparator. | Không bắt buộc; kế thừa `Object.equals` là hợp lệ. |

## 12. Checklist kiểm tra thiết kế

Khi viết hoặc sử dụng một `Comparator<T>`:

1. Xác định **quy tắc sắp xếp** của `compare(a,b)` và những trường nào tham gia.
2. Hỏi rõ: hai phần tử được `compare` trả 0 có cần coi như **cùng khóa** không, hay chỉ cùng mức ưu tiên/nhóm?
3. So đối chiếu với `a.equals(b)`: comparator có **consistent with equals** trong miền sử dụng không?
4. Nếu dùng `TreeSet`/`TreeMap`, kiểm tra kỹ trường hợp `compare(a,b)==0` nhưng `a.equals(b)==false` để tránh vô tình gộp phần tử hoặc ghi đè giá trị.
5. Nếu chỉ cần sắp xếp List, đừng cho rằng hai phần tử `compare==0` sẽ bị xóa hay trộn.
6. Với nhiều tiêu chí, dùng `Comparator.comparing(...).thenComparing(...)` và chọn tie-breaker theo ngữ nghĩa mong muốn.
7. Đảm bảo comparator tuân quy tắc về dấu, bắc cầu, và sự nhất quán của các phần tử tương đương; tránh viết `return a - b` cho `int` vì tràn số có thể tạo kết quả sai, dùng `Integer.compare(a,b)`.
8. Nếu comparator có trạng thái hoặc cấu hình thay đổi trong lúc đang dùng bởi một `TreeSet`/`TreeMap`, thứ tự cây có thể không còn hợp lệ; nên giữ cấu hình so sánh **ổn định** suốt vòng đời sử dụng.
9. Chỉ đánh giá `comparatorA.equals(comparatorB)` khi thực sự cần so **hai comparator**. Nếu tự override phương thức này, phải đảm bảo kết quả `true` suy ra cùng thứ tự và giữ hợp đồng với `hashCode()`.

**Ghi nhớ:** `Comparator.equals(Object)` trả lời liệu **hai đối tượng bộ so sánh** có được coi bằng nhau hay không; `Comparator.compare(a,b)==0` trả lời liệu **hai phần tử** có tương đương theo một quy tắc thứ tự hay không. Quan hệ thứ hai chỉ trùng `a.equals(b)` khi comparator nhất quán với equals. Phân biệt ba quan hệ này giúp tránh các lỗi khó phát hiện với `TreeSet`, `TreeMap` và việc định nghĩa tính duy nhất của dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)
