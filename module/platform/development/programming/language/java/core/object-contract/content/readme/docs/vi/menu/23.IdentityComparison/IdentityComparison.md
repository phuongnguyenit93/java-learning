<a id="back-to-top"></a>

# So sánh theo danh tính

## Menu
- [Khi bài toán cần phân biệt từng thể hiện dù giá trị bằng nhau](#identity-comparison-001)
- [IdentityHashMap dùng == cho cả khóa và giá trị thay vì equals()](#identity-comparison-002)
- [Dùng danh tính khi duyệt và theo dõi đồ thị đối tượng](#identity-comparison-003)
- [Phân biệt identity hash code với mã duy nhất](#identity-comparison-004)
- [Không thay HashMap bằng IdentityHashMap nếu nghiệp vụ cần bằng nhau theo giá trị](#identity-comparison-005)
- [IdentityHashMap cố ý khác hợp đồng Map thông thường ở quan hệ so sánh](#identity-comparison-006)
- [Đối tượng value-based và lý do không dựa vào danh tính thể hiện](#identity-comparison-007)

## <a id="identity-comparison-001">Khi bài toán cần phân biệt từng thể hiện dù giá trị bằng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Khi bài toán cần phân biệt từng thể hiện dù giá trị bằng nhau

## 1. Vấn đề cần giải quyết

Trong Java, hai biến tham chiếu có thể trỏ đến **hai đối tượng khác nhau** nhưng các đối tượng ấy mang **cùng giá trị logic**. Có những bài toán chỉ quan tâm nội dung: hai vé có cùng mã được xem là tương đương. Nhưng cũng có bài toán phải phân biệt chính xác **từng thể hiện (instance) được tạo ra trong bộ nhớ**: vé thứ nhất đã được xử lý, còn vé thứ hai chưa, dù cả hai cùng mã.

Câu hỏi phải đặt ra trước khi chọn phép so sánh là:

- **Value equality (bằng nhau về giá trị):** hai đối tượng có được xem là tương đương theo quy tắc nghiệp vụ hay không?
- **Reference identity (cùng danh tính đối tượng):** hai tham chiếu có trỏ đến **chính cùng một đối tượng** hay không?

Đây là hai quan hệ khác nhau. `equals()` có thể được định nghĩa để coi hai đối tượng khác nhau là bằng nhau; điều đó không biến chúng thành một instance.

## 2. Phân biệt `==`, `equals()` và hash code

| Công cụ | Ý nghĩa khi dùng với đối tượng | Khi nào phù hợp |
| --- | --- | --- |
| `a == b` | Hai tham chiếu cùng trỏ đến một đối tượng, hoặc cả hai đều `null`. | Cần nhận diện **chính instance**. |
| `a.equals(b)` | Bằng nhau theo cách lớp cài đặt `equals`; có thể báo lỗi nếu `a == null`. | Cần so sánh **giá trị/ngữ nghĩa nghiệp vụ**. |
| `Objects.equals(a, b)` | So sánh theo `equals`, xử lý `null` an toàn. | Cần value equality khi một trong hai tham chiếu có thể `null`. |
| `a.hashCode()` | Mã băm theo hợp đồng `equals`–`hashCode`; có thể bị ghi đè. | Làm việc với cấu trúc băm theo giá trị. |
| `System.identityHashCode(a)` | Mã băm theo danh tính mặc định, bất kể lớp ghi đè `hashCode`. | Chẩn đoán/thuật toán dựa trên identity, **không** dùng riêng nó để xác nhận cùng đối tượng. |

Với **kiểu nguyên thủy** (`int`, `double`, `boolean`...), `==` so sánh giá trị nguyên thủy; bài học này tập trung vào **tham chiếu đối tượng**. `Object.equals` mặc định tương đương so sánh identity, nhưng nhiều lớp như `String`, các lớp wrapper và `record` dùng value equality. Vì vậy không thể suy ra ý nghĩa `equals` mà bỏ qua hợp đồng của lớp.

Đặc điểm quan trọng: nếu `a == b` thì một lời gọi `a.equals(b)` hợp lệ phải trả về `true` với cách cài đặt `equals` tuân thủ hợp đồng thông thường. Chiều ngược lại **không đúng**: `a.equals(b)` là `true` không có nghĩa `a == b`.

## 3. Ví dụ chạy được: hai instance cùng giá trị

Tạo file **`IdentityComparisonDemo.java`**:

```java
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

record Ticket(String code) {}

public class IdentityComparisonDemo {
    public static void main(String[] args) {
        Ticket first = new Ticket("T-01");
        Ticket second = new Ticket("T-01");
        Ticket alias = first;

        System.out.println("first == second: " + (first == second));
        System.out.println("first.equals(second): " + first.equals(second));
        System.out.println("alias == first: " + (alias == first));

        Map<Ticket, String> byValue = new HashMap<>();
        byValue.put(first, "first");
        byValue.put(second, "second");

        Map<Ticket, String> byIdentity = new IdentityHashMap<>();
        byIdentity.put(first, "first");
        byIdentity.put(second, "second");

        System.out.println("HashMap size: " + byValue.size());
        System.out.println("HashMap get(first): " + byValue.get(first));
        System.out.println("IdentityHashMap size: " + byIdentity.size());
        System.out.println("IdentityHashMap get(first): " + byIdentity.get(first));
        System.out.println("IdentityHashMap get(second): " + byIdentity.get(second));
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac IdentityComparisonDemo.java
java IdentityComparisonDemo
```

Kết quả:

```text
first == second: false
first.equals(second): true
alias == first: true
HashMap size: 1
HashMap get(first): second
IdentityHashMap size: 2
IdentityHashMap get(first): first
IdentityHashMap get(second): second
```

Diễn giải:

1. Mỗi lần `new Ticket(...)` tạo ra một đối tượng riêng, nên `first == second` là `false`.
2. `record Ticket` sinh `equals` dựa trên thành phần `code`; cùng `"T-01"` khiến `first.equals(second)` là `true`.
3. `alias = first` chỉ sao chép **tham chiếu**; nó không tạo thêm đối tượng.
4. `HashMap` dùng `hashCode` và `equals` của khóa, nên khóa `second` thay giá trị gắn với khóa tương đương `first`; còn một entry.
5. `IdentityHashMap` phân biệt `first` và `second` bằng `==`, nên lưu được hai entry độc lập.

**Bài học:** chọn `HashMap` khi “cùng mã vé” nghĩa là một khóa logic; chọn `IdentityHashMap` khi “đúng đối tượng vé này” mới là khóa.

## 4. Những tình huống identity là đúng yêu cầu

### 4.1. Theo dõi mỗi instance trong quá trình xử lý

Giả sử một luồng xử lý nhận hai đối tượng sự kiện có cùng `eventCode`. Hệ thống phải đánh dấu **đối tượng đã duyệt trong lần chạy hiện tại**, chứ không gom mọi đối tượng có cùng mã thành một. Một tập hợp theo identity phù hợp cho việc ghi nhận từng instance:

```java
Set<Event> visited =
        Collections.newSetFromMap(new IdentityHashMap<Event, Boolean>());

visited.add(eventA);
boolean alreadyVisited = visited.contains(eventB);
```

Đoạn trên **chỉ là trích đoạn minh họa**, giả định lớp `Event` và các biến `eventA`, `eventB` đã được khai báo; cần import `java.util.Set`, `java.util.Collections`, `java.util.IdentityHashMap`. Nếu hai biến trỏ đến hai instance khác nhau, `contains(eventB)` là `false` dù `eventA.equals(eventB)` trả về `true`. Nếu `eventB` là alias của `eventA`, kết quả là `true`.

Đặc biệt hữu ích khi duyệt **đồ thị đối tượng có chu trình**, phát hiện đối tượng đã ghé qua và tránh lặp vô hạn. Nếu dùng `HashSet` mà các nút khác instance lại có `equals` giống nhau, thuật toán có thể bỏ sót nút hợp lệ.

### 4.2. Gắn trạng thái tạm thời vào từng đối tượng

Ví dụ trình kiểm tra cấu trúc, bộ tuần tự hóa, bộ ghi dấu vết hoặc debugger muốn gắn trạng thái `VISITING`, `VISITED` cho **từng object**. Hai object được xem là có cùng giá trị vẫn có thể nằm ở vị trí khác nhau trong đồ thị, có tham chiếu con khác nhau, hoặc trải qua trạng thái xử lý khác nhau. Bảng ánh xạ identity giúp trạng thái không bị nhập nhằng.

### 4.3. Quản lý đối tượng làm khóa đồng bộ hóa

Monitor của `synchronized (lock)` gắn với **danh tính đối tượng `lock`**. Hai đối tượng lock khác nhau không phối hợp với nhau chỉ vì `lockA.equals(lockB)` trả về `true`. Các thread muốn bảo vệ cùng một vùng trạng thái phải dùng **cùng instance lock** (thường là một trường `private final Object lock = new Object();` được chia sẻ bởi đúng các thao tác cần đồng bộ).

Không tạo một đối tượng lock mới chỉ có cùng giá trị cho từng lời gọi rồi cho rằng chúng sẽ loại trừ lẫn nhau. Cũng tránh khóa trên string literal hoặc đối tượng công khai có thể bị mã khác dùng chung một cách ngoài ý muốn.

### 4.4. Phân biệt đối tượng sống với cùng một khóa nghiệp vụ

Hai đối tượng mô tả “khách hàng mã 123” có thể là hai bản sao dữ liệu trong bộ nhớ. Nếu muốn so sánh **khách hàng nào**, hãy dùng định danh nghiệp vụ ổn định và chính sách `equals` phù hợp. Nếu muốn hỏi **biến nào đang tham chiếu đến cùng object đang được thao tác**, dùng `==`. Identity trong JVM không phải định danh nghiệp vụ dài hạn: tải lại từ database, deserialize hoặc nhận bản sao qua API thường cho instance mới.

## 5. Quy tắc ra quyết định## 6. Quy tắc ra quyết định

| Câu hỏi/nhu cầu | Chọn | Lý do |
| --- | --- | --- |
| “Hai giá trị `String` có cùng nội dung?” | `equals` hoặc `Objects.equals` | Nội dung mới là tiêu chí. |
| “Đây có đúng object đang được giữ trong bộ nhớ không?” | `==` | Yêu cầu cùng instance. |
| “Hai bản ghi cùng khóa nghiệp vụ có đại diện một thực thể logic?” | So sánh ID/giá trị theo hợp đồng domain | Identity tham chiếu không bền qua lần tải/copy. |
| “Đã duyệt chính nút object này trong đồ thị chưa?” | Tập hợp/map theo identity | Không nhập các nút riêng chỉ vì `equals` giống nhau. |
| “Map cần gom các khóa có giá trị tương đương?” | `HashMap` | Dựa trên `equals`/`hashCode`. |
| “Map phải lưu metadata tách riêng theo từng object?” | `IdentityHashMap` | Dựa trên `==` cho khóa. |
| “Hai thao tác có dùng chung monitor đồng bộ?” | Kiểm tra cùng object lock | Monitor có danh tính theo instance. |

Cách tự hỏi nhanh: **Nếu tạo thêm một đối tượng bằng `new` với nội dung y hệt, yêu cầu có coi nó là một mục riêng không?** Nếu có, rất có thể cần identity trong **phạm vi vòng đời các object đó**. Nếu không, ưu tiên value equality hoặc một ID nghiệp vụ.

## 6. Những cạm bẫy thường gặp

### 7.1. Dùng `==` cho `String` hoặc wrapper

`String` có cơ chế intern; compiler/JVM cũng có các cơ chế tái sử dụng một số đối tượng. Vì vậy đôi khi `==` tình cờ là `true` với hai giá trị bằng nhau. Điều đó **không bảo đảm** hai chuỗi cùng nội dung sẽ cùng instance:

```java
String x = new String("java");
String y = new String("java");

System.out.println(x == y);      // false
System.out.println(x.equals(y)); // true
```

Đoạn này là trích đoạn Java độc lập về mặt câu lệnh (có thể đặt trong `main`). Tránh suy luận về value equality từ hành vi cache của boxed `Integer` hoặc string pool. Với chuỗi nội dung, dùng `equals` hoặc `Objects.equals`.

### 7.2. Tưởng `identityHashCode` là địa chỉ bộ nhớ hay ID duy nhất

`System.identityHashCode(obj)` **không phải địa chỉ bộ nhớ**, không là ID ổn định giữa những lần chạy JVM, và **có thể trùng** ở hai object khác nhau. Vì vậy:

- `System.identityHashCode(a) == System.identityHashCode(b)` **không chứng minh** `a == b`.
- Khi cần so sánh trực tiếp danh tính, dùng `a == b`; khi cần cấu trúc dữ liệu theo danh tính, dùng cấu trúc thực sự hỗ trợ identity.

Lưu ý `System.identityHashCode(null)` trả về `0`; số này cũng không phải một dấu hiệu nhận diện duy nhất.

### 7.3. Mặc định mọi `equals` đều là value equality

Nếu lớp không ghi đè `Object.equals`, mặc định `equals` dùng identity; khi đó `a.equals(b)` và `a == b` cho cùng kết quả nếu `a` không null. Nhưng lớp có thể được thay đổi để ghi đè `equals` sau này. Hãy chủ động chọn toán tử/thư viện phản ánh **ý định thuật toán**, không dựa vào hành vi tình cờ hiện tại của một lớp.

### 7.4. Dùng `IdentityHashMap` làm thay thế phổ quát cho `HashMap`

`IdentityHashMap` là cấu trúc chuyên biệt. Nó dùng **reference equality cho cả khóa và giá trị** trong các phép toán liên quan; hành vi này khác các triển khai `Map` thông thường và có thể tạo kết quả bất ngờ khi đối chiếu map/entry với cấu trúc dùng `equals`. Không có bảo đảm thứ tự duyệt. Nó cũng **không thread-safe**, và các tham chiếu mạnh trong map có thể giữ object sống lâu nếu không xóa entry khi không cần.

`IdentityHashMap` thích hợp cho topology/preservation của object graph và metadata theo instance; đừng dùng chỉ để né việc thiết kế `equals`/`hashCode` đúng cho khóa nghiệp vụ.

### 7.5. Tưởng danh tính JVM tồn tại qua serialization hoặc dịch vụ khác

Một object được copy, deserialize hoặc tái tạo ở JVM khác thường là instance mới. So sánh tham chiếu chỉ có ý nghĩa khi các tham chiếu cùng quan sát object trong một JVM đang chạy. Muốn nối dữ liệu qua lần chạy, database hay mạng, cần **ID ổn định** do ứng dụng quy định.

### 7.6. Nhầm identity với trạng thái hiện tại

Một object có thể đổi các trường mutable mà vẫn là **cùng instance** (`a == alias` vẫn `true`). Ngược lại, hai immutable record có thể bằng nhau suốt đời nhưng là **hai instance**. Đừng suy ra danh tính từ việc giá trị đã đổi hay chưa. Dùng khóa mutable trong `HashMap` còn gây rủi ro nếu các trường tham gia `hashCode` thay đổi sau khi thêm vào; đó là vấn đề hợp đồng băm, cần phân biệt với identity.

## 7. Checklist thiết kế và kiểm thử

1. Viết rõ tiêu chí: “bằng nhau về dữ liệu”, “cùng định danh nghiệp vụ” hay “chính cùng đối tượng trong bộ nhớ”.
2. Với value equality: định nghĩa/kiểm tra `equals` và `hashCode` nhất quán; xử lý `null` bằng `Objects.equals` khi cần.
3. Với reference identity: dùng `==`, `IdentityHashMap`, hoặc identity-backed set; không thay bằng so sánh identity hash code.
4. Thử ít nhất ba trường hợp: **cùng tham chiếu**, **khác instance nhưng `equals` là `true`**, và **khác giá trị**. Nếu có thể `null`, thử thêm cả `null`.
5. Rà soát vòng đời object, số lượng entry giữ trong map, luồng truy cập và nhu cầu đồng bộ; identity trong bộ nhớ không thay ID nghiệp vụ bên ngoài.
6. Khi dùng object làm lock, đảm bảo mọi thao tác cần phối hợp tham chiếu **cùng object lock**.

## 8. Câu hỏi tự kiểm tra

**Tình huống A:** `a` và `b` là hai instance khác nhau của cùng một `record Product("P1")`. `a.equals(b)` trả `true`. Nếu chỉ muốn gửi thông báo một lần cho mỗi mã sản phẩm, nên dùng `HashSet` hay identity set?

**Đáp án:** `HashSet` phù hợp vì yêu cầu gom theo mã/giá trị, giả sử quy tắc `equals` của `Product` đúng với định nghĩa trùng sản phẩm.

**Tình huống B:** Bộ phân tích cây object phải ghi nhận đường đi qua hai nút riêng cùng tên, và tránh quay lại đúng một nút đã thăm vì đồ thị có chu trình. Nên dùng loại visited set nào?

**Đáp án:** Identity set phù hợp vì mỗi nút là một object cụ thể; hai nút có cùng tên vẫn cần duyệt độc lập, trong khi quay lại cùng instance phải được nhận ra.

**Tình huống C:** Hai số `System.identityHashCode` bằng nhau có cho phép kết luận hai tham chiếu trỏ đến một object không?

**Đáp án:** Không. Hash có thể va chạm; để kết luận cùng instance trong JVM, so sánh `==`.

**Ghi nhớ:** “Giá trị như nhau” trả lời **đối tượng được coi là tương đương theo quy tắc nào**; “danh tính như nhau” trả lời **có phải đúng một thể hiện đang tồn tại hay không**. Lựa chọn nằm ở yêu cầu bài toán, không nằm ở việc hai object trông giống nhau đến mức nào.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="identity-comparison-002">IdentityHashMap dùng == cho cả khóa và giá trị thay vì equals()</a>

<details>
<summary>Xem chi tiết</summary>

# IdentityHashMap: so sánh cả khóa và giá trị bằng == thay vì equals()

## 1. Ý tưởng cốt lõi

**java.util.IdentityHashMap<K, V>** là một triển khai Map đặc biệt: nó dùng **danh tính tham chiếu (reference identity)** thay cho **bằng nhau theo giá trị (logical equality)** khi so sánh **khóa lẫn giá trị**.

Hai toán tử/quan hệ dễ nhầm:

- **a == b**, khi a và b là tham chiếu đối tượng, nghĩa là cả hai đang trỏ đến **chính cùng một đối tượng** (hoặc cả hai đều null).
- **a.equals(b)** là phép so sánh logic do lớp của a định nghĩa. Ví dụ String.equals xét nội dung ký tự; hai String có nội dung giống nhau vẫn có thể là hai đối tượng riêng.

IdentityHashMap chọn quan hệ thứ nhất. Vì vậy:

- Hai khóa khác tham chiếu **được coi là hai khóa riêng**, dù key1.equals(key2) trả true.
- Tra cứu bằng một đối tượng mới có nội dung giống khóa đã lưu **không tìm thấy** khóa đó nếu tham chiếu khác.
- Các thao tác liên quan đến kiểm tra **giá trị** cũng dùng danh tính; ví dụ containsValue tìm giá trị cùng tham chiếu, không chấp nhận một đối tượng chỉ bằng nhau theo equals.
- Đây là **lựa chọn thiết kế có chủ đích**, không phải HashMap bị cấu hình nhầm.

Điều kiện quan trọng: toán tử == giữa **hai kiểu nguyên thủy** sẽ so sánh giá trị nguyên thủy; trong IdentityHashMap, khóa và giá trị thuộc tham số kiểu K, V nên các phần tử được xử lý ở dạng **tham chiếu đối tượng**. Với Integer hoặc kiểu bao khác, đừng để autoboxing che khuất ranh giới giữa danh tính tham chiếu và giá trị số.

## 2. So sánh với HashMap thông thường

| Hành vi | HashMap<K, V> | IdentityHashMap<K, V> |
| --- | --- | --- |
| Xác định khóa có trùng | Dựa trên equals, phối hợp hashCode | Dựa trên == (cùng tham chiếu) |
| Tìm khóa bằng đối tượng khác nhưng equals true | Có thể tìm thấy, nếu hashCode nhất quán | Không tìm thấy |
| Kiểm tra giá trị với containsValue | Dùng equals (null được xử lý) | Dùng == (cùng tham chiếu) |
| Hai khóa khác tham chiếu nhưng equals true | Được coi là một khóa | Có thể cùng tồn tại thành hai khóa |
| Mục tiêu thường gặp | Bản đồ theo giá trị hoặc định danh logic | Bản đồ theo **đối tượng cụ thể trong bộ nhớ** |
| Hàm băm phục vụ tìm khóa | Dựa trên key.hashCode | Dựa trên cơ chế băm danh tính |
| Có chấp nhận null | Có một khóa null, có thể có giá trị null | Có một khóa null, có thể có giá trị null |

Một ví dụ:

~~~java
String k1 = new String("ID");
String k2 = new String("ID");

System.out.println(k1.equals(k2)); // true
System.out.println(k1 == k2);      // false
~~~

Dùng new String tạo hai đối tượng riêng; vì vậy String.equals trả true còn toán tử == trả false. Với HashMap, k1 và k2 đại diện một khóa logic theo chuỗi "ID"; với IdentityHashMap, chúng là hai khóa danh tính khác nhau.

**Đừng thay ví dụ bằng hai string literal** như "ID" và "ID" rồi kết luận Java luôn coi chuỗi cùng nội dung là cùng đối tượng. String literal có thể tham chiếu cùng đối tượng thông qua string pool, còn những đối tượng được tạo riêng bằng new vẫn khác tham chiếu. Việc so sánh danh tính không thay thế String.equals khi muốn xác định nội dung.

## 3. IdentityHashMap dùng danh tính cho cả khóa và giá trị

### 3.1. Khóa: get, put, containsKey và remove

Giả sử:

~~~java
String first = new String("account");
String second = new String("account");
IdentityHashMap<String, Integer> identities = new IdentityHashMap<>();
identities.put(first, 10);
identities.put(second, 20);
~~~

Các dòng trên là trích đoạn trong ngữ cảnh đã import java.util.IdentityHashMap.

Vì first != second theo toán tử ==, map có **hai entry** dù first.equals(second) là true.

- identities.get(first) trả 10.
- identities.get(second) trả 20.
- identities.get(new String("account")) trả null nếu chưa lưu chính đối tượng vừa tạo, vì đối tượng mới không cùng tham chiếu với first hoặc second.
- identities.containsKey(new String("account")) trả false.
- identities.remove(new String("account")) không xóa hai entry nói trên.

Lưu ý giá trị null trả về từ get có thể nghĩa là **không có khóa** hoặc **khóa tồn tại nhưng chứa giá trị null**. Nếu cần phân biệt, kiểm tra containsKey bằng đúng tham chiếu khóa.

### 3.2. Giá trị: containsValue và những thao tác kiểm tra cặp khóa–giá trị

Trong IdentityHashMap, phép tìm **giá trị** cũng theo ==:

~~~java
String stored = new String("ACTIVE");
IdentityHashMap<String, String> map = new IdentityHashMap<>();
String key = new String("user-1");
map.put(key, stored);

System.out.println(map.containsValue(stored));               // true
System.out.println(map.containsValue(new String("ACTIVE"))); // false
~~~

Đối tượng String mới có cùng nội dung "ACTIVE" nhưng khác tham chiếu, nên containsValue trả false. Đây là điểm dễ bị bỏ quên: **IdentityHashMap không chỉ dùng == với khóa**.

Một số phép kiểm tra trên tập entry hoặc thao tác Map liên quan đồng thời đến khóa và giá trị cũng chịu ảnh hưởng của quy tắc danh tính. Khi cần hành vi cụ thể của một API như remove(key, value), contains, replace hoặc equals giữa hai map, hãy dựa vào tài liệu của IdentityHashMap thay vì áp dụng trực giác về HashMap.

Quan hệ này không có nghĩa **mỗi giá trị chỉ được dùng một lần**: nhiều khóa có thể cùng trỏ tới một đối tượng value; bản đồ vẫn cho phép các entry như vậy.

### 3.3. null

IdentityHashMap hỗ trợ **một khóa null** và các giá trị null. Vì null == null là true, hai lần put(null, value) cập nhật cùng một khóa null thay vì tạo thêm khóa mới.

~~~java
IdentityHashMap<Object, Object> map = new IdentityHashMap<>();
map.put(null, null);
System.out.println(map.containsKey(null));   // true
System.out.println(map.containsValue(null)); // true
~~~

Không được gọi phương thức instance equals trên một tham chiếu null. Quan hệ danh tính == tự xử lý null mà không cần gọi phương thức trên đối tượng đó.

## 4. Chương trình Java 17+ hoàn chỉnh

Lưu thành **IdentityHashMapDemo.java**:

~~~java
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class IdentityHashMapDemo {
    static final class Node {
        final String label;

        Node(String label) {
            this.label = label;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Node other
                    && label.equals(other.label);
        }

        @Override
        public int hashCode() {
            return label.hashCode();
        }
    }

    public static void main(String[] args) {
        String k1 = new String("code");
        String k2 = new String("code");
        String v1 = new String("ready");
        String v2 = new String("ready");

        IdentityHashMap<String, String> identity =
                new IdentityHashMap<>();
        identity.put(k1, v1);
        identity.put(k2, v2);

        Map<String, String> ordinary = new HashMap<>();
        ordinary.put(k1, v1);
        ordinary.put(k2, v2);

        System.out.println("keys equals: " + k1.equals(k2));
        System.out.println("keys ==: " + (k1 == k2));
        System.out.println("values equals: " + v1.equals(v2));
        System.out.println("values ==: " + (v1 == v2));
        System.out.println("IdentityHashMap size: " + identity.size());
        System.out.println("HashMap size: " + ordinary.size());

        System.out.println("identity get same key: "
                + (identity.get(k1) == v1));
        System.out.println("identity get equal new key: "
                + identity.get(new String("code")));
        System.out.println("identity contains equal new key: "
                + identity.containsKey(new String("code")));
        System.out.println("identity contains exact value: "
                + identity.containsValue(v1));
        System.out.println("identity contains equal new value: "
                + identity.containsValue(new String("ready")));
        System.out.println("HashMap contains equal new value: "
                + ordinary.containsValue(new String("ready")));
        System.out.println("HashMap get equal new key: "
                + (ordinary.get(new String("code")) == v2));

        IdentityHashMap<Object, Object> withNull =
                new IdentityHashMap<>();
        withNull.put(null, null);
        System.out.println("null key present: "
                + withNull.containsKey(null));
        System.out.println("null value present: "
                + withNull.containsValue(null));

        Node n1 = new Node("same");
        Node n2 = new Node("same");
        IdentityHashMap<Node, Boolean> visited =
                new IdentityHashMap<>();
        visited.put(n1, Boolean.TRUE);
        visited.put(n2, Boolean.TRUE);

        Set<Node> ordinaryNodes = new HashSet<>();
        ordinaryNodes.add(n1);
        ordinaryNodes.add(n2);

        Set<Node> identitySet =
                Collections.newSetFromMap(
                        new IdentityHashMap<Node, Boolean>());
        identitySet.add(n1);
        identitySet.add(n2);

        System.out.println("nodes equals: " + n1.equals(n2));
        System.out.println("visited identities: " + visited.size());
        System.out.println("ordinary HashSet nodes: "
                + ordinaryNodes.size());
        System.out.println("identity Set nodes: "
                + identitySet.size());
    }
}
~~~

Chạy trong thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 IdentityHashMapDemo.java
java IdentityHashMapDemo
~~~

Kết quả stdout:

~~~text
keys equals: true
keys ==: false
values equals: true
values ==: false
IdentityHashMap size: 2
HashMap size: 1
identity get same key: true
identity get equal new key: null
identity contains equal new key: false
identity contains exact value: true
identity contains equal new value: false
HashMap contains equal new value: true
HashMap get equal new key: true
null key present: true
null value present: true
nodes equals: true
visited identities: 2
ordinary HashSet nodes: 1
identity Set nodes: 2
~~~

### Vì sao output như vậy?

1. **Hai khóa k1/k2:** equals theo String là true, nhưng == false. IdentityHashMap có hai entry, HashMap chỉ có một entry và lần put thứ hai ghi đè value của khóa logic "code".
2. **Tra cứu bằng khóa mới:** IdentityHashMap không thấy String mới cùng nội dung. HashMap tìm được vì String.equals và String.hashCode xét nội dung.
3. **Giá trị v1/v2:** hai String bằng nhau theo nội dung nhưng khác tham chiếu. containsValue(v1) true, còn containsValue(new String("ready")) false trên IdentityHashMap; HashMap chấp nhận đối tượng giá trị tương đương theo equals.
4. **null:** cùng một khóa null và một giá trị null được hỗ trợ. containsKey/containsValue phân biệt được null có mặt khi đã put.
5. **Hai Node có cùng label:** equals true và hashCode giống nhau theo định nghĩa của Node, nhưng n1 và n2 là hai đối tượng riêng. HashSet gộp thành một phần tử; IdentityHashMap và một Set tạo từ IdentityHashMap giữ cả hai.

Chương trình tạo một lớp Node cố tình có equals/hashCode theo label, qua đó chứng minh phép so sánh danh tính **độc lập** với tiêu chí bằng nhau do lớp Node cài đặt. Không sử dụng thứ tự duyệt của map trong output nên không phụ thuộc thứ tự duyệt entry.

## 5. Cơ chế băm danh tính và System.identityHashCode

IdentityHashMap vẫn là một cấu trúc băm, nhưng không dùng phương thức **key.hashCode()** làm tiêu chí hash logic để tìm khóa. Nó băm dựa trên **danh tính** của đối tượng; System.identityHashCode(object) là API cung cấp mã băm kiểu danh tính ngay cả khi lớp đã ghi đè hashCode.

~~~java
Object value = new String("same");
int identityHash = System.identityHashCode(value);
int logicalHash = value.hashCode();
~~~

Đây là hai khái niệm khác nhau:

- **value.hashCode()** có thể được lớp tùy biến. String.hashCode được tính dựa trên nội dung nên hai String khác tham chiếu cùng nội dung có cùng logical hash.
- **System.identityHashCode(value)** trả mã băm theo cách triển khai mặc định dựa trên danh tính đối tượng, kể cả khi lớp ghi đè hashCode. Đừng diễn giải nó thành địa chỉ vùng nhớ hay một số định danh toàn cục duy nhất.
- **Hai đối tượng khác nhau vẫn có thể trùng identity hash**: int chỉ có số lượng giá trị hữu hạn. Map xử lý đụng độ băm bằng kiểm tra danh tính == của khóa, chứ không kết luận cùng khóa vì mã băm trùng.
- **Mã identity hash không phải khóa lâu dài để lưu vào cơ sở dữ liệu**: không có bảo đảm tính duy nhất hoặc ổn định giữa các lần chạy JVM.

Không cần tự gọi System.identityHashCode khi sử dụng IdentityHashMap; cấu trúc tự triển khai cơ chế danh tính. Việc tính lại hashCode đã ghi đè trong một class cũng không thể biến HashMap thành IdentityHashMap nếu logic equality vẫn dựa trên equals.

## 6. Vì sao IdentityHashMap khác hợp đồng Map thông thường?

Giao diện Map nói chung định nghĩa các thao tác dựa trên khái niệm khóa **bằng nhau theo equals** và hashCode tương thích. IdentityHashMap được tài liệu Java nêu rõ là triển khai có chủ đích **không tuân theo quy ước đối tượng-bằng-nhau thông thường của Map**: thay thế bằng quan hệ ==.

Điều này gây bất ngờ nếu đổi kiểu cài đặt nhưng giữ cùng API:

~~~java
Map<String, Integer> map = new IdentityHashMap<>();
String saved = new String("item");
map.put(saved, 42);

// Đối tượng khóa mới có cùng nội dung không tra cứu được:
Integer result = map.get(new String("item")); // null
~~~

Code vẫn biên dịch vì IdentityHashMap implements Map, nhưng **ý nghĩa equality đã đổi**. Việc gán cho biến kiểu Map không “chuyển” phép so sánh về equals.

Những hệ quả thực tế:

- Khi so sánh bản đồ, tập entry hoặc thực hiện những thao tác liên quan equals/hashCode với các Map khác loại, có thể gặp kết quả khác kỳ vọng do quy tắc equality không đồng nhất.
- Dữ liệu hai bản đồ có vẻ giống nhau khi in ra không bảo đảm bản đồ có cùng quan hệ khóa/giá trị; không dựa vào toString để xác định bằng nhau.
- Sử dụng IdentityHashMap trong API công khai cần mô tả rõ **danh tính đối tượng** là một phần hợp đồng. Nếu hệ thống mong đợi khóa có thể được tái tạo từ ID/string, hãy dùng HashMap hoặc một cấu trúc Map theo giá trị.
- Không “nâng cấp hiệu năng” HashMap thành IdentityHashMap chỉ vì ít gọi equals; thay đổi cấu trúc có thể làm thay đổi hành vi đúng đắn của chương trình.

## 7. Tình huống nên sử dụng

### 7.1. Ghi nhận các đối tượng đã thăm trong đồ thị

Khi duyệt một object graph có vòng (cycle), hai nút Node khác tham chiếu dù equals true có thể vẫn là **hai nút vật lý cần xử lý riêng**. Một IdentityHashMap<Node, Boolean> là lựa chọn hữu ích để đánh dấu từng thể hiện đã thăm, nhất là nếu equals của Node so sánh theo nội dung hoặc có tính toán đắt đỏ.

~~~java
// Khai báo trong phương thức xử lý đồ thị:
IdentityHashMap<Node, Boolean> visited = new IdentityHashMap<>();

if (visited.put(node, Boolean.TRUE) == null) {
    // Lần đầu gặp chính node tham chiếu này.
    // Duyệt các liên kết của node ở đây.
}
~~~

Điều kiện ở đây chỉ đúng khi ứng dụng thống nhất luôn lưu Boolean.TRUE làm giá trị đánh dấu, **không dùng null làm giá trị**. Nếu một node từng được lưu với giá trị null, put trả null cũng không chứng minh đó là lần đầu; dùng containsKey nếu muốn xử lý chắc chắn cả trường hợp có null value.

### 7.2. Ánh xạ metadata theo từng đối tượng cụ thể

Một trình kiểm tra cấu trúc, bộ theo dõi aliasing, công cụ serialization, hoặc bộ ánh xạ đối tượng nguồn sang đối tượng bản sao đôi khi cần gắn metadata với **từng instance cụ thể**, không phải với mọi instance bằng nhau theo equals.

Nếu hai đối tượng khác nhau cùng biểu diễn nội dung nhưng là hai node riêng trong bộ nhớ, IdentityHashMap lưu được hai bản ghi riêng. Điều đó quan trọng khi cần giữ thông tin về object sharing hoặc phát hiện vòng lặp trong cấu trúc tham chiếu.

### 7.3. Tạo Set theo danh tính

Nếu muốn một tập hợp giữ các phần tử khác tham chiếu kể cả khi equals true, có thể tạo:

~~~java
Set<Node> seen = java.util.Collections.newSetFromMap(
        new java.util.IdentityHashMap<Node, Boolean>());
~~~

Map truyền vào newSetFromMap phải **đang trống**. Set sinh ra dùng quy tắc khóa của IdentityHashMap, tức là danh tính ==. Với một yêu cầu chỉ cần Set theo danh tính, cách này biểu đạt chủ ý rõ hơn dùng IdentityHashMap<Node, Boolean> và thao tác trực tiếp trên value.

## 8. Khi nào không nên dùng?

**Không nên dùng IdentityHashMap** khi khóa đại diện cho giá trị, nội dung hoặc ID logic:

- String là username, mã sản phẩm, URL hoặc khóa cấu hình;
- Integer/Long là số hiệu, ID database hoặc mã trạng thái;
- record hoặc value object được thiết kế với equals/hashCode theo các component;
- khóa có thể tạo lại từ request, JSON, database row hay một bản sao tương đương.

Trong các trường hợp này, HashMap thường hợp lý hơn, vì các đối tượng có cùng giá trị được tìm thấy dù khác tham chiếu.

**Ví dụ dễ sai với boxing:**

~~~java
IdentityHashMap<Integer, String> values = new IdentityHashMap<>();
values.put(Integer.valueOf(1000), "found");

// Không được giả định một Integer mới có cùng trị số
// sẽ cùng tham chiếu với khóa lưu ở trên.
String answer = values.get(Integer.valueOf(1000));
~~~

Các đối tượng wrapper có thể được cache hoặc được tạo mới tùy API và khoảng giá trị, vì vậy không dùng phép == của wrapper hay IdentityHashMap để thể hiện bình đẳng số học. Ví dụ trên chỉ minh họa **rủi ro thiết kế**, không tuyên bố mọi JVM luôn cho answer bằng null.

Khi cần kiểm tra giá trị, dùng equals và Map theo giá trị. Khi cần danh tính, hãy tạo hoặc truyền **đúng đối tượng khóa ban đầu**, không tái tạo một đối tượng mới có dữ liệu tương đương.

## 9. Những đặc điểm triển khai và lưu ý vận hành

- **Không duy trì thứ tự:** IdentityHashMap không đảm bảo thứ tự phần tử theo thời điểm chèn hoặc theo giá trị. Đừng dựa vào thứ tự của keySet, entrySet, values hoặc toString.
- **Không đồng bộ sẵn:** giống HashMap, nó không tự cung cấp đồng bộ cho truy cập/ghi đồng thời. Dùng biện pháp đồng bộ hoặc thiết kế an toàn luồng nếu nhiều thread cập nhật map.
- **initial expectedMaxSize:** constructor nhận số phần tử kỳ vọng để điều chỉnh kích thước nội bộ; không phải giới hạn cứng số entry được phép thêm. Giá trị âm không hợp lệ.
- **Bộ nhớ và vòng đời:** map giữ tham chiếu mạnh tới khóa và giá trị đã lưu. Vì vậy, việc dùng nó như bảng metadata có thể giữ đối tượng sống lâu hơn mong muốn nếu không xóa entry; cần quản lý vòng đời hoặc cân nhắc cấu trúc khác khi cần khóa yếu (weak keys).
- **Thay đổi trường của khóa:** khác HashMap dùng hashCode theo giá trị, IdentityHashMap vẫn xác định cùng khóa theo chính tham chiếu khi trạng thái nội bộ của object thay đổi. Tuy nhiên việc thay đổi object vẫn có thể ảnh hưởng logic chương trình, nên không đồng nghĩa có thể thao tác mutable object tùy ý trong mọi ứng dụng.
- **Không đánh đồng identity hash với tính duy nhất:** collision vẫn có thể xảy ra và danh tính đối tượng được quyết định bằng ==, không bằng việc so sánh hai số identityHashCode.

## 10. Các nhầm lẫn và cách kiểm tra

| Nhầm lẫn | Sự thật |
| --- | --- |
| IdentityHashMap chỉ dùng == cho khóa | Dùng danh tính cho cả khóa **và giá trị** |
| Hai khóa equals true thì put lần sau luôn ghi đè | Chỉ ghi đè khi là **chính cùng tham chiếu khóa** |
| Tên class IdentityHashMap nghĩa là map mã hash duy nhất | identity hash có thể va chạm, == mới xác nhận danh tính |
| Có thể lookup bằng String mới cùng nội dung | Chỉ được nếu tham chiếu trùng; dùng HashMap nếu muốn so nội dung |
| containsValue dùng equals như mọi Map | IdentityHashMap kiểm tra == giữa các giá trị |
| Dùng cùng tham số generic Map thì hai triển khai có cùng equality | Generic xác định kiểu, không thay đổi chính sách bằng nhau |
| null không dùng được với map danh tính | null key và null value vẫn được hỗ trợ |
| Bản đồ danh tính luôn nhanh hơn HashMap | Hiệu năng phụ thuộc kích thước, phân bố hash, cách dùng; chọn theo ngữ nghĩa trước |

**Checklist khi review code:**

1. Bài toán quan tâm cùng đối tượng hay cùng giá trị? Nếu là cùng giá trị, thường không chọn IdentityHashMap.
2. Có phải caller tái tạo khóa từ String/ID không? Nếu có, việc get bằng đối tượng mới sẽ không phù hợp.
3. Các thao tác tìm giá trị có cần equals không? Nếu cần, containsValue của IdentityHashMap sẽ khác dự kiến.
4. Có null key/value không? Phân biệt không có entry với entry chứa null.
5. Map có được trao qua interface Map cho code giả định hợp đồng equals phổ thông không? Nếu có, phải làm rõ hoặc đổi cấu trúc.
6. Nếu dùng làm visited set, có cần phân biệt từng node instance dù Node.equals true không?
7. Có vấn đề vòng đời bộ nhớ hoặc thao tác đa luồng không? Quản lý tham chiếu mạnh, xóa entry và đồng bộ thích hợp.

## 11. Ghi nhớ

**IdentityHashMap đặt câu hỏi “có phải đúng đối tượng này không?”**, không phải “hai đối tượng có giá trị giống nhau không?”. Vì thế:

- Khóa được xác định bằng **==**, không phải equals.
- Giá trị cũng được kiểm tra bằng **==**, điển hình trong containsValue.
- Hai đối tượng khác tham chiếu nhưng equals true được giữ riêng dưới dạng khóa.
- Map phù hợp với object graph, metadata theo instance và phát hiện chia sẻ tham chiếu; không phù hợp làm bản đồ theo ID hoặc nội dung.

Chỉ sử dụng khi **danh tính tham chiếu là chính xác điều nghiệp vụ cần**. Nếu muốn lookup theo giá trị logic, hãy ưu tiên HashMap cùng equals/hashCode đúng hợp đồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="identity-comparison-003">Dùng danh tính khi duyệt và theo dõi đồ thị đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng danh tính khi duyệt và theo dõi đồ thị đối tượng

Trong Java, một đồ thị đối tượng gồm các **instance** (đỉnh) và các tham chiếu nối chúng (cạnh). Khi duyệt đồ thị để xuất dữ liệu, phân tích liên kết, sao chép cấu trúc, đánh dấu đã thăm hoặc phát hiện chu kỳ, ta thường cần trả lời: **“Đúng instance này đã xuất hiện trước đó chưa?”** Đây là câu hỏi về **danh tính đối tượng (object identity)**.

Bài học phân biệt danh tính với sự bằng nhau về giá trị, hướng dẫn dùng `IdentityHashMap` để quản lý tập đã thăm và trạng thái theo từng instance, đồng thời chỉ rõ sự khác nhau giữa tham chiếu được dùng chung và chu kỳ.

## 1. Vì sao `equals()` không đủ để duyệt đồ thị đối tượng?

Xem mô hình sau:

```text
          root
          /  \
         v    v
       left  right        Hai instance khác nhau,
         \    /           nhưng có thể equals() == true.
          v  v
         shared           Hai nhánh trỏ đúng cùng instance.
            |
            +---> root    Cạnh tạo chu kỳ.
```

Ba trường hợp phải được xử lý riêng:

1. **Khác instance, bằng nhau về dữ liệu.** `left == right` là `false` nhưng `left.equals(right)` có thể là `true`, ví dụ hai nút cùng trường `value`. Nếu mục tiêu là xử lý từng instance, không được bỏ một nút vì dữ liệu của nó giống nút khác.
2. **Cùng instance theo nhiều đường.** `left.next` và `right.next` cùng trỏ đến `shared`; thuật toán chỉ nên xử lý `shared` một lần dù có hai đường đi đến nó.
3. **Chu kỳ.** Nếu `shared` trỏ ngược `root`, việc duyệt không kiểm soát có thể lặp vô hạn hoặc gây `StackOverflowError`.

Bài toán “xử lý **mỗi instance** một lần” cần danh tính; bài toán “gộp các đối tượng có **giá trị tương đương**” thường cần `equals()` và `hashCode()`. Hai bài toán có tiêu chí khác nhau, nên không thể hoán đổi tập visited một cách tùy ý.

## 2. Công cụ so sánh đúng tiêu chí

| Công cụ | Cơ chế | Dùng khi nào |
| --- | --- | --- |
| `a == b` | Hai biến trỏ đúng cùng instance, hoặc đều `null` | So sánh danh tính |
| `a.equals(b)` | Kiểm tra quan hệ tương đương do kiểu đối tượng định nghĩa | So sánh giá trị/ngữ nghĩa |
| `HashSet` / `HashMap` | Sử dụng `hashCode()` và `equals()` | Gộp các key được coi là bằng nhau |
| `IdentityHashMap` | So key bằng `==` và dùng `System.identityHashCode()` cho cơ chế băm | Theo dõi từng instance |
| `System.identityHashCode(x)` | Trả về mã băm theo danh tính, không phụ thuộc `hashCode()` được override | Hỗ trợ cơ chế băm/chẩn đoán; **không phải ID duy nhất** |

`System.identityHashCode()` **có thể trùng nhau ở hai instance khác nhau**; không dùng kết quả như một khóa định danh độc nhất hay thay thế cho phép so sánh `==`. Cũng không coi số ấy là địa chỉ bộ nhớ ổn định hoặc ID giữ nguyên giữa các lần chạy Java.

`IdentityHashMap` là cấu trúc chuyên dụng: nó chủ ý có ngữ nghĩa khóa khác `Map` thông thường. Không thay `HashMap` bằng `IdentityHashMap` cho các bài toán nghiệp vụ mà key thực sự cần được xem là bằng nhau theo `equals()`.

## 3. Tạo tập visited theo danh tính

Khi chỉ cần biết một instance đã được duyệt hay chưa, tạo `Set` dựa trên một `IdentityHashMap` còn trống:

```java
Set<Node> visited =
        Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
```

Cần import `java.util.Set`, `java.util.Collections` và `java.util.IdentityHashMap`. Map truyền cho `Collections.newSetFromMap(...)` phải trống ngay lúc tạo set.

Sau đó, mẫu duyệt đơn giản là:

```java
if (!visited.add(node)) {
    continue; // Chính instance này đã được ghi nhận trước đó.
}
// Xử lý node, rồi đưa những nút liên kết vào danh sách chờ.
```

`add` trả về `true` đúng lần đầu ghi nhận một instance, ngay cả khi set đã có một instance khác với `equals()` tương đương. Đánh dấu trước khi mở rộng cạnh ngăn xử lý lặp khi gặp chu kỳ. Không cần `contains` rồi `add` nếu một lời gọi `add` đã trả lại thông tin cần dùng.

## 4. Ví dụ Java 17+: hai nút bằng nhau, một tham chiếu dùng chung và một chu kỳ

Chương trình sau cố ý định nghĩa `equals()` chỉ theo `value`: hai nút `left` và `right` khác instance nhưng đều có `value = "same"`. Trường `id` khác nhau giúp quan sát rõ việc duyệt có bỏ sót node nào không.

Lưu dưới tên **`IdentityGraphDemo.java`**:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class IdentityGraphDemo {
    static final class Node {
        final int id;
        final String value;
        final List<Node> next = new ArrayList<>();

        Node(int id, String value) {
            this.id = id;
            this.value = value;
        }

        Node link(Node... targets) {
            Collections.addAll(next, targets);
            return this;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Node that
                    && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }
    }

    static List<Integer> dfs(Node root, Set<Node> visited) {
        List<Integer> order = new ArrayList<>();
        if (root == null) {
            return order;
        }

        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (!visited.add(node)) {
                continue;
            }

            order.add(node.id);
            // Đẩy theo thứ tự ngược để nhánh trái được duyệt trước.
            for (int i = node.next.size() - 1; i >= 0; i--) {
                stack.push(node.next.get(i));
            }
        }
        return order;
    }

    enum Mark { ACTIVE, DONE }

    static boolean hasCycle(
            Node node, IdentityHashMap<Node, Mark> marks) {
        Mark mark = marks.get(node);
        if (mark == Mark.ACTIVE) {
            return true; // Quay lại node trong nhánh DFS hiện tại.
        }
        if (mark == Mark.DONE) {
            return false; // Node đã kết thúc ở nhánh trước.
        }

        marks.put(node, Mark.ACTIVE);
        boolean cycle = false;
        for (Node child : node.next) {
            if (hasCycle(child, marks)) {
                cycle = true;
            }
        }
        marks.put(node, Mark.DONE);
        return cycle;
    }

    public static void main(String[] args) {
        Node root = new Node(0, "root");
        Node left = new Node(1, "same");
        Node right = new Node(2, "same");
        Node shared = new Node(3, "shared");

        root.link(left, right);
        left.link(shared);
        right.link(shared);
        shared.link(root); // Tạo chu kỳ.

        Set<Node> identityVisited =
                Collections.newSetFromMap(
                        new IdentityHashMap<Node, Boolean>());

        System.out.println("left == right: " + (left == right));
        System.out.println(
                "left.equals(right): " + left.equals(right));
        System.out.println(
                "DFS theo identity: " + dfs(root, identityVisited));
        System.out.println(
                "DFS theo equals:   " + dfs(root, new HashSet<>()));
        System.out.println(
                "Co chu ky: " + hasCycle(root, new IdentityHashMap<>()));

        shared.next.clear(); // Xóa cạnh shared -> root.
        System.out.println(
                "Co chu ky sau khi bo canh: "
                        + hasCycle(root, new IdentityHashMap<>()));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac IdentityGraphDemo.java
java IdentityGraphDemo
```

Kết quả xác định:

```text
left == right: false
left.equals(right): true
DFS theo identity: [0, 1, 3, 2]
DFS theo equals:   [0, 1, 3]
Co chu ky: true
Co chu ky sau khi bo canh: false
```

**Diễn giải:**

- `left` và `right` tạo bởi hai lần `new` nên không trùng danh tính. `equals()` trả về `true` vì cả hai có chuỗi `"same"`.
- DFS theo danh tính đi qua `root (0) → left (1) → shared (3) → right (2)`. Khi gặp lại root từ shared, tập visited chặn xử lý lặp; shared cũng không bị xử lý lần thứ hai từ right.
- DFS với `HashSet` theo `equals()` chỉ cho `[0, 1, 3]`, vì right bị gộp với left. Với một bài toán yêu cầu ghé từng instance, đây là lỗi bỏ sót đỉnh.
- Phương thức `hasCycle` đánh dấu node đang trên nhánh DFS là `ACTIVE`, hoàn tất là `DONE`. Cạnh `shared → root` trở lại node `ACTIVE` nên chu kỳ được phát hiện. Xóa cạnh ấy làm kết quả thành `false`, dù shared vẫn có hai tham chiếu đi đến.

`equals()` trong ví dụ được đơn giản hóa có chủ đích để thấy rõ sự khác nhau giữa value và identity; trong hệ thống thực tế, hợp đồng `equals()`/`hashCode()` cần được thiết kế theo ngữ nghĩa của kiểu dữ liệu.

## 5. Gặp lại cùng instance không nhất thiết là chu kỳ

Phải phân biệt **visited** với **nhánh DFS hiện hành**:

| Quan sát | Kết luận đúng |
| --- | --- |
| `visited.add(node)` trả về `false` | Instance này đã gặp. Có thể chỉ là liên kết dùng chung. |
| Đi từ nút hiện tại tới nút `ACTIVE` trong DFS có hướng | Có cạnh quay lại một nút tổ tiên trên nhánh DFS: tồn tại chu kỳ. |
| Đi tới nút `DONE` | Nút đã được xử lý ở nhánh trước; bản thân cạnh này không chứng minh chu kỳ. |

Trong sơ đồ, root→left→shared và root→right→shared cùng hội tụ về shared. Nếu bỏ cạnh shared→root thì đó là đồ thị có hướng **không chu kỳ**, dù DFS vẫn gặp lại một instance. Vì vậy, không dùng quy tắc “đã thăm thì có chu kỳ”.

Set identity đủ cho công việc **duyệt mỗi instance một lần**. Để tìm chu kỳ trong đồ thị có hướng, dùng ba trạng thái ngầm: chưa thấy, `ACTIVE` và `DONE`, cũng phải được gắn **theo identity**. Có thể dùng `IdentityHashMap<Node, Mark>` như phương thức ở ví dụ. Khi có nhiều đỉnh gốc rời nhau, giữ cùng một map trạng thái cho cả lượt kiểm tra để không xử lý trùng.

Đệ quy của `hasCycle` có thể gặp `StackOverflowError` khi đồ thị rất sâu. Trường hợp đó nên triển khai DFS với stack tường minh, mỗi frame ghi node và chỉ số cạnh đang xét, rồi chuyển `ACTIVE → DONE` lúc frame kết thúc. Dùng `visited` đơn thuần không thay được thông tin về stack đang hoạt động.

## 6. Lưu dữ liệu riêng cho từng instance

Một `Set` chỉ lưu có/chưa. Khi cần trạng thái, kết quả phân tích hay đối tượng bản sao gắn với từng node, dùng `IdentityHashMap` trực tiếp:

```java
IdentityHashMap<Node, Integer> depthByInstance =
        new IdentityHashMap<>();

depthByInstance.put(left, 1);
depthByInstance.put(right, 1); // Hai key riêng dù equals() == true.
```

Các tình huống phổ biến:

- **Đánh dấu hai trạng thái DFS**: `IdentityHashMap<Node, Mark>` lưu `ACTIVE`/`DONE`.
- **Sao chép đồ thị có chu kỳ**: lưu `sourceInstance → cloneInstance` ngay sau khi tạo bản sao rỗng, *trước khi* duyệt các cạnh; lần gặp lại source sẽ trả bản sao đã đăng ký. Nhờ thế một đối tượng dùng chung trong nguồn vẫn dùng chung trong bản sao và chu kỳ không khiến thuật toán tạo vô hạn bản sao.
- **Tuần tự hóa đồ thị bảo toàn chia sẻ tham chiếu**: lưu `instance → id` do thuật toán tự cấp, rồi các cạnh có thể tham chiếu ID cũ thay vì mở rộng lại cùng instance.
- **Phân tích/caching trong một lượt duyệt**: gắn kết quả tạm cho từng instance mà không gộp những node bằng nhau về giá trị.

Đối với map, `get(key) == null` không đủ phân biệt “không có key” và “key tồn tại nhưng value bằng `null`”. Dùng `containsKey(key)` khi cần phân biệt; `IdentityHashMap.containsKey` vẫn kiểm tra theo danh tính.

Không dùng `IdentityHashMap` làm nơi ghi lâu dài những trạng thái vốn phải được chia sẻ theo key nghiệp vụ; mỗi lần tạo instance mới biểu diễn cùng một thực thể nghiệp vụ sẽ là một key khác.

## 7. Kỹ thuật DFS/BFS và chi phí

**DFS lặp** có thể dùng `ArrayDeque<Node>` với `push`/`pop`; **BFS** có thể dùng `addLast`/`removeFirst`. Với cả hai, tập visited theo identity quyết định node nào cần xử lý.

Trong ví dụ, node được thêm vào visited khi **lấy khỏi stack**. Nếu nhiều cạnh trỏ cùng node, stack có thể chứa bản tham chiếu ấy nhiều lần, nhưng phần xử lý chính chỉ chạy một lần. Một biến thể khác đánh dấu ngay khi **đưa vào stack/queue** để giảm phần tử chờ trùng; khi đổi thời điểm đánh dấu, phải đảm bảo thứ tự và quy tắc duyệt vẫn đúng.

Với `V` instance có thể tới được và `E` cạnh được đọc, thời gian kỳ vọng là `O(V + E)`; visited giữ `O(V)` tham chiếu. Khi cho phép nhiều mục chờ trùng, stack có thể tốn tới `O(E)` bộ nhớ trong trường hợp xấu. Đồ thị sâu nên dùng vòng lặp với stack tường minh để tránh giới hạn ngăn xếp đệ quy.

**Dữ liệu `null`:** `ArrayDeque` không chấp nhận phần tử `null`. Nếu danh sách liên kết có thể chứa `null`, chỉ đẩy `child` khi `child != null`. Việc map identity chấp nhận key `null` không làm nó trở thành một đỉnh hợp lệ của đồ thị.

**Thay đổi đồng thời:** `IdentityHashMap` không tự bảo đảm an toàn luồng khi nhiều luồng cập nhật. Nếu cấu trúc cạnh cũng bị sửa lúc duyệt, kết quả có thể phản ánh những thời điểm khác nhau. Cần xác định mô hình đồng bộ, snapshot hoặc giới hạn quyền sửa phù hợp; một map riêng cho mỗi lượt duyệt là mặc định dễ kiểm soát.

**Vòng đời bộ nhớ:** `IdentityHashMap` giữ tham chiếu mạnh tới key. Dùng map cục bộ trong một lượt duyệt và giải phóng sau khi hoàn tất để không vô tình giữ cả đồ thị trong bộ nhớ. `WeakHashMap` không phải map weak-identity tương đương: nó vẫn dùng `equals()`/`hashCode()`; thiết kế key yếu theo identity đòi hỏi cơ chế riêng.

## 8. Lỗi dễ gặp

| Sai lầm | Biểu hiện | Cách xử lý |
| --- | --- | --- |
| Dùng `HashSet<Node>` cho visited trong bài toán theo instance | Hai node bằng nhau về giá trị bị gộp | Dùng set dựa trên `IdentityHashMap` |
| Coi mọi lần gặp lại node là chu kỳ | Liên kết hội tụ bị báo nhầm | Theo dõi `ACTIVE` và `DONE` khi tìm chu kỳ |
| Chỉ đánh dấu node sau khi đã duyệt tất cả nhánh | Chu kỳ có thể khiến đệ quy không dừng | Đăng ký instance trước khi mở rộng nhánh |
| Tạo visited mới cho từng nhánh | Không biết nhánh khác đã gặp cùng instance | Dùng chung visited của cả lượt duyệt |
| Dùng lại visited qua các lần duyệt độc lập | Lượt sau bỏ sót instance | Tạo visited mới cho lượt mới, trừ khi chủ đích khác |
| Dùng `identityHashCode` làm định danh duy nhất | Có thể trùng mã băm giữa hai object | So danh tính bằng `==` hoặc key identity |
| Coi ID theo identity là địa chỉ bộ nhớ | Giả định sai về JVM | Không suy ra địa chỉ từ API chuẩn |
| Dùng đệ quy cho đồ thị không giới hạn độ sâu | `StackOverflowError` | Dùng stack frame tường minh |
| Lưu identity map toàn cục không dọn | Giữ instance sống lâu không cần thiết | Giới hạn vòng đời, xóa map khi xong |

## 9. Cách chọn giải pháp cho một bài toán thực tế

1. Xác định **đơn vị cần xử lý**: một instance cụ thể hay một giá trị/thực thể nghiệp vụ? Chỉ chọn identity cho trường hợp thứ nhất.
2. Xác định đồ thị có thể có **chia sẻ, chu kỳ, nút rỗng** hay không. Nếu có, thiết kế đánh dấu từ đầu; không giả định dữ liệu luôn là cây.
3. Nếu chỉ cần “duyệt một lần”, dùng `Set` trên `IdentityHashMap`; nếu cần trạng thái per-instance, dùng `IdentityHashMap<Instance, State>`.
4. Nếu phải **phát hiện chu kỳ có hướng**, theo dõi trạng thái nhánh hiện hành `ACTIVE` và hoàn tất `DONE` bằng identity.
5. Chọn DFS/BFS, thời điểm đánh dấu, cách xử lý `null`, độ sâu và bộ nhớ theo quy mô dữ liệu.
6. Tạo vùng trạng thái riêng cho mỗi lần duyệt, và kết thúc vòng đời map đúng lúc.

**Ghi nhớ:** hai đối tượng `equals()` nhau có thể vẫn là hai đỉnh riêng trong đồ thị Java. `IdentityHashMap` giúp theo dõi chính xác từng instance, còn trạng thái `ACTIVE`/`DONE` cho phép phân biệt một chu kỳ thực sự với tham chiếu được chia sẻ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="identity-comparison-004">Phân biệt identity hash code với mã duy nhất</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt identity hash code với mã duy nhất trong Java

## 1. Vấn đề: cùng một đối tượng hay cùng một mã?

Trong Java, **danh tính đối tượng** (*object identity*) trả lời câu hỏi «hai biến có đang trỏ tới chính cùng một instance trong JVM không?». Trong khi đó, **bằng nhau về giá trị** (*value equality*) trả lời câu hỏi «hai đối tượng có được xem là tương đương theo quy tắc nghiệp vụ hoặc trạng thái không?». **Identity hash code** chỉ là một số băm liên quan tới danh tính đối tượng; nó **không phải mã định danh duy nhất**.

Ví dụ, hai đối tượng `Person` khác nhau có thể cùng tên, cùng mã khách hàng và được `equals()` đánh giá bằng nhau; chúng vẫn là hai instance riêng. Ngược lại, hai biến trỏ đến cùng một instance luôn có cùng danh tính, bất kể dữ liệu của instance ấy thay đổi.

## 2. Phân biệt các công cụ

| Công cụ | Trả lời câu hỏi gì? | Điểm cần nhớ |
| --- | --- | --- |
| `a == b` (với biến tham chiếu) | Có cùng một đối tượng không? | So sánh tham chiếu, là phép kiểm tra identity trực tiếp. |
| `a.equals(b)` | Có bằng nhau theo quy tắc của lớp không? | Có thể được override để so sánh giá trị; `Object.equals()` mặc định dùng identity. |
| `a.hashCode()` | Giá trị băm mà lớp cung cấp là gì? | Có thể override; phục vụ cấu trúc băm, chịu hợp đồng `equals()`/`hashCode()`. |
| `System.identityHashCode(a)` | Mã băm theo cách mặc định của `Object` là gì? | Bỏ qua mọi override của `hashCode()`; trả về `int`, có thể trùng. |
| ID nghiệp vụ hoặc ID lưu trữ | Có thể tham chiếu thực thể nào qua thời gian/hệ thống? | Phải được thiết kế và kiểm soát tính duy nhất, ví dụ khóa chính DB/UUID. |

`System.identityHashCode(x)` trả về giá trị mà cách cài đặt mặc định của `Object.hashCode()` sẽ trả về cho đối tượng đó, **kể cả khi lớp đã override `hashCode()`**. Với `x == null`, phương thức trả về `0`. Còn `Objects.hashCode(x)` (nếu dùng) gọi `x.hashCode()` khi `x` khác `null`, nên không thay thế cho identity hash code.

### Tính chất logic quan trọng

- Nếu `a == b` thì `System.identityHashCode(a) == System.identityHashCode(b)`.
- Nếu hai identity hash code **khác nhau** trong cùng lần thực thi thì hai tham chiếu không thể trỏ tới cùng một đối tượng.
- Nếu hai identity hash code **bằng nhau**, **không được kết luận** `a == b`: các đối tượng khác nhau vẫn có thể va chạm băm (*collision*).
- Nếu `a.equals(b)` là `true`, hợp đồng `hashCode()` buộc `a.hashCode() == b.hashCode()`, nhưng **không bắt buộc** hai identity hash code bằng nhau.
- Nếu `a.hashCode() == b.hashCode()` thì chưa đủ để kết luận `a.equals(b)` hay `a == b`.

Identity hash code có kiểu `int` (32 bit, tức tối đa `2^32` mẫu bit), nên không thể bảo đảm một giá trị riêng cho mọi đối tượng trong mọi tình huống. Đặc tả Java chỉ hướng đến việc các instance khác nhau nhận mã băm khác nhau **trong mức độ khả thi hợp lý**, chứ không cam kết không va chạm. Không được coi con số này là địa chỉ bộ nhớ, số thứ tự tạo đối tượng hoặc ID bền vững: JVM có quyền chọn cách tính, GC có thể di chuyển đối tượng, và giá trị không có cam kết ổn định giữa các lần chạy hay giữa các JVM.

## 3. Ví dụ chạy được với Java 17+

Lớp `Ticket` chủ ý định nghĩa bằng nhau theo `code`, nên hai instance khác nhau vẫn `equals()` bằng nhau. Ta đối chiếu `HashMap` (dựa vào `equals()`/`hashCode()`) với `IdentityHashMap` (dựa vào `==` và identity hash code).

```java
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class IdentityHashCodeDemo {
    static final class Ticket {
        private final String code;

        Ticket(String code) {
            this.code = code;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof Ticket ticket && code.equals(ticket.code));
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }
    }

    public static void main(String[] args) {
        Ticket first = new Ticket("T-01");
        Ticket second = new Ticket("T-01");
        Ticket alias = first;

        System.out.println("first == second: " + (first == second));
        System.out.println("first.equals(second): " + first.equals(second));
        System.out.println("first == alias: " + (first == alias));
        System.out.println("value hashes equal: "
                + (first.hashCode() == second.hashCode()));
        System.out.println("alias identity hashes equal: "
                + (System.identityHashCode(first) == System.identityHashCode(alias)));

        Map<Ticket, String> byValue = new HashMap<>();
        byValue.put(first, "one");
        byValue.put(second, "two");

        Map<Ticket, String> byIdentity = new IdentityHashMap<>();
        byIdentity.put(first, "one");
        byIdentity.put(second, "two");

        System.out.println("HashMap size: " + byValue.size());
        System.out.println("IdentityHashMap size: " + byIdentity.size());
        System.out.println("identityHashCode(null): " + System.identityHashCode(null));
    }
}
```

Lưu tệp `IdentityHashCodeDemo.java`, biên dịch và chạy:

```bash
javac -encoding UTF-8 IdentityHashCodeDemo.java
java IdentityHashCodeDemo
```

Đầu ra xác định:

```text
first == second: false
first.equals(second): true
first == alias: true
value hashes equal: true
alias identity hashes equal: true
HashMap size: 1
IdentityHashMap size: 2
identityHashCode(null): 0
```

**Giải thích:** `first` và `second` là hai đối tượng khác nhau dù cùng `code`; vì vậy `==` trả `false`, còn `equals()` trả `true`. Hai biến `first`/`alias` trỏ tới một đối tượng nên identity hash code của chúng bằng nhau. `HashMap` gộp hai khóa tương đương theo giá trị thành một entry; `IdentityHashMap` giữ cả hai khóa vì khác tham chiếu. **Ví dụ cố ý không in và không giả định** `System.identityHashCode(first)` khác `System.identityHashCode(second)`; giả định đó sẽ sai khi có collision.

## 4. Vì sao hash không chứng minh được tính duy nhất?

Hàm băm ánh xạ rất nhiều đối tượng có thể tồn tại về một tập giá trị số hữu hạn. Việc một mã trông ngẫu nhiên hoặc ít khi trùng không biến nó thành mã duy nhất. `HashMap` và `IdentityHashMap` đều phải xử lý trường hợp các khóa có cùng hash: hash được dùng để tổ chức/tra cứu vị trí, còn phép so sánh khóa mới xác nhận khóa nào là khóa cần tìm. Đặc biệt, `IdentityHashMap` dùng so sánh **tham chiếu** dù hai khóa có thể cùng identity hash code.

Đừng viết điều kiện nhận dạng theo kiểu `System.identityHashCode(a) == System.identityHashCode(b)`; hãy dùng `a == b`. Cũng đừng lưu identity hash code làm khóa chính database, khóa cache dùng qua nhiều lần chạy, ID hiển thị cho người dùng, mã audit log cần truy vết lâu dài hay ID truyền giữa các dịch vụ.

### `ClassName@hex` trong log có phải ID duy nhất?

Không. Cách triển khai mặc định của `Object.toString()` ghép tên lớp, dấu `@` và biểu diễn hex của `hashCode()`. Phần hex ấy là **hash code**, không phải cam kết duy nhất; nếu lớp override `hashCode()` mà không override `toString()`, phần hex còn phản ánh phương thức `hashCode()` đã override. Một chuỗi như `Ticket@1a2b3c` không đủ để xác nhận đối tượng nào trên toàn hệ thống.

## 5. Khi nào dùng gì?

| Nhu cầu | Cách phù hợp |
| --- | --- |
| Kiểm tra hai biến có trỏ đúng cùng instance trong thời điểm hiện tại | `a == b` |
| Kiểm tra hai giá trị/nghiệp vụ tương đương | Cài đặt `equals()` và `hashCode()` nhất quán; dùng `Objects.equals(a, b)` khi cần an toàn với `null`. |
| Gom và tra cứu theo giá trị | `HashMap`, `HashSet` cùng hợp đồng `equals()`/`hashCode()`. |
| Theo dõi node trong đồ thị đối tượng, xử lý alias/cycle theo instance | `IdentityHashMap` hoặc `Collections.newSetFromMap(new IdentityHashMap<>())`. |
| Gắn nhãn băm hỗ trợ chẩn đoán một đối tượng đang sống | `System.identityHashCode(obj)`, thường kèm ngữ cảnh/lần chạy và kiểm tra `==` khi cần. |
| Nhận dạng thực thể qua restart, DB hoặc mạng | ID nghiệp vụ được lưu bền vững; ví dụ khóa chính DB có ràng buộc `UNIQUE`/`PRIMARY KEY`, hoặc UUID được cấp và quản lý. |

`UUID.randomUUID()` tạo UUID ngẫu nhiên với xác suất trùng rất thấp trong thực tế, **không phải bằng chứng toán học rằng không bao giờ trùng**. Nếu nghiệp vụ yêu cầu duy nhất nghiêm ngặt, hãy áp ràng buộc duy nhất ở nơi lưu trữ và xử lý khi có xung đột. Hai Java object khác nhau biểu diễn cùng bản ghi database có thể mang **cùng ID nghiệp vụ** mà vẫn `a != b` theo phép so sánh tham chiếu.

## 6. Sai lầm phổ biến và cách tránh

1. **Nhầm identity hash code với địa chỉ bộ nhớ.** Java không hứa mã băm là địa chỉ, cũng không hứa địa chỉ của đối tượng bất biến do GC.
2. **Dùng trùng hash để kết luận cùng object.** Collision hợp lệ; dùng `==` để kiểm tra identity.
3. **Dùng `hashCode()` để nhận dạng lâu dài.** Override, phiên chạy mới và collision đều khiến mã băm không phù hợp cho ID bền vững.
4. **So sánh đối tượng có cùng dữ liệu bằng `==`.** `==` có thể trả `false` dù `equals()` trả `true`; chọn phép so sánh theo mục tiêu.
5. **Giả định mọi object khác nhau có identity hash khác nhau.** Không có bảo đảm ấy; `IdentityHashMap` vẫn giữ được các khóa riêng nhờ kiểm tra tham chiếu.
6. **Lạm dụng `IdentityHashMap` cho dữ liệu nghiệp vụ.** Loại map này cố ý dùng identity thay cho value equality, vì thế kết quả có thể trái trực giác nếu gọi với các khóa bằng nhau theo `equals()`.

## 7. Tự kiểm tra

- Hai biến cùng tham chiếu mà identity hash code khác nhau trong một lần chạy được không? **Không**.
- Hai instance khác nhau mà identity hash code trùng nhau được không? **Có**, vì mã băm có thể va chạm.
- Hai đối tượng `equals()` bằng nhau mà identity hash code khác nhau được không? **Có**.
- Có nên dùng `System.identityHashCode()` làm mã đơn hàng duy nhất không? **Không**; dùng ID được cấp phát và xác thực tính duy nhất bởi hệ thống lưu trữ.

**Ghi nhớ:** *Identity* được xác nhận bằng cùng tham chiếu (`==`); *identity hash code* là số băm hỗ trợ cơ chế theo danh tính; *unique identifier* là định danh do thiết kế hệ thống quy định và duy trì.

## Tài liệu tham khảo

- [Java SE 17 — `System.identityHashCode(Object)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#identityHashCode(java.lang.Object))
- [Java SE 17 — hợp đồng `Object.hashCode()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#hashCode())
- [Java SE 17 — `IdentityHashMap`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="identity-comparison-005">Không thay HashMap bằng IdentityHashMap nếu nghiệp vụ cần bằng nhau theo giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Không thay HashMap bằng IdentityHashMap nếu nghiệp vụ cần bằng nhau theo giá trị

## 1. Quyết định quan trọng: “cùng khóa” nghĩa là gì?

Khi chọn một cấu trúc Map trong Java, trước hết phải trả lời **hai khóa nào được coi là cùng một khóa**. Với các đối tượng Java, có hai khái niệm khác nhau:

- **Bằng nhau theo giá trị (logical equality)**: hai object có thể là hai instance khác nhau nhưng đại diện cùng giá trị nghiệp vụ. Quy tắc này được diễn đạt bằng **equals(Object)** và hợp đồng đi kèm **hashCode()**.
- **Cùng một đối tượng (reference identity)**: hai tham chiếu trỏ tới **chính cùng một instance**. Java kiểm tra bằng toán tử **==** trên các tham chiếu.

**HashMap** chọn ngữ nghĩa **giá trị**: các khóa khác instance nhưng **equals true** (với hashCode tương thích) phải được nhận diện là cùng khóa. **IdentityHashMap** chọn ngữ nghĩa **danh tính tham chiếu**: chỉ những tham chiếu **==** mới được coi là cùng khóa. Vì vậy, đổi **HashMap** thành **IdentityHashMap** là **thay đổi logic nghiệp vụ**, không đơn thuần là đổi cách tối ưu bộ nhớ, tốc độ hoặc thuật toán băm.

Ví dụ: hai đối tượng **CustomerId("C-17")** được tạo từ hai request khác nhau. Nếu nghiệp vụ quy định cùng mã khách hàng là cùng một khách hàng, HashMap phải cho phép dùng instance thứ hai để tìm bản ghi được chèn bằng instance thứ nhất. IdentityHashMap không bảo đảm và thường trả về không tìm thấy, dù **equals** giữa chúng trả true.

**Quy tắc chọn mặc định:** Khi khóa là mã khách hàng, mã đơn hàng, UUID, String, record, value object, hoặc tổ hợp thuộc tính định danh nghiệp vụ, thông thường hãy dùng **HashMap<K,V>** (hoặc LinkedHashMap nếu cần thứ tự). Chỉ dùng **IdentityHashMap** khi yêu cầu thực sự là phân biệt từng object **theo tham chiếu**.

## 2. Hợp đồng equals/hashCode của HashMap

**HashMap<K,V>** sử dụng **hashCode()** để hỗ trợ xác định vị trí tìm kiếm và **equals(Object)** để kiểm tra sự tương đương của khóa. Điều kiện nền tảng của hợp đồng Java:

1. **Phản xạ:** a.equals(a) phải true.
2. **Đối xứng:** a.equals(b) có cùng kết quả với b.equals(a).
3. **Bắc cầu:** a bằng b, b bằng c thì a bằng c.
4. **Nhất quán:** khi dữ liệu tham gia so sánh không đổi, kết quả equals ổn định.
5. **Với null:** a.equals(null) là false đối với a khác null.
6. **Hash tương thích:** nếu a.equals(b) true thì a.hashCode() **bắt buộc bằng** b.hashCode().

Điều ngược lại của quy tắc hash **không** đúng: hai khóa có cùng hashCode có thể không bằng nhau. HashMap xử lý va chạm băm bằng cách tiếp tục kiểm tra khóa.

Khi một khóa mới được đưa vào **put** và nó tương đương một khóa đã tồn tại, giá trị gắn với khóa đó được **thay thế**; size không tăng. Trong triển khai HashMap thông thường, khóa đại diện đã lưu được giữ lại khi chỉ thay giá trị của cùng logical key. Không nên dựa vào tham chiếu của “khóa đại diện” trừ khi API hoặc yêu cầu thực sự cần.

**HashMap** cho phép một khóa null và các giá trị null. Vì **get(key)** có thể trả null khi không có ánh xạ hoặc khi ánh xạ tới giá trị null, dùng **containsKey(key)** để phân biệt nếu cần. Đây là thuộc tính Map độc lập với việc chọn equals hay reference identity.

## 3. IdentityHashMap thay thế equals bằng ==

**java.util.IdentityHashMap<K,V>** là Map đặc biệt:

- So sánh **khóa** bằng **==**, thay vì gọi equals theo giá trị.
- Băm khóa theo **System.identityHashCode(key)**, thay vì hashCode được override trong lớp khóa.
- Hai object có cùng nội dung và equals true nhưng **khác tham chiếu** vẫn là **hai khóa**.
- Hai biến tham chiếu cùng instance là **một khóa**, kể cả khi object đó override equals một cách đặc biệt.
- Cho phép khóa null và giá trị null; hành vi kiểm tra danh tính với null vẫn được xử lý như khóa riêng của Map.
- Không bảo đảm thứ tự duyệt. Không dùng kết quả lặp qua entrySet để suy ra thứ tự chèn.

IdentityHashMap **cố ý không tuân quy ước equals theo giá trị** thường dùng cho Map và các view liên quan. Đặc biệt, trong các chương trình trộn Map thường với IdentityHashMap, đừng giả định kiểm tra **mapA.equals(mapB)** luôn có cùng ý nghĩa hoặc đối xứng như giữa hai Map thông thường; tài liệu Java cảnh báo về việc này.

**System.identityHashCode(obj)** không phải địa chỉ bộ nhớ được Java bảo đảm và không đảm bảo duy nhất giữa các object. Có thể xảy ra va chạm identity hash; IdentityHashMap vẫn dựa trên **tham chiếu ==** để phân biệt khóa. Không dùng số identity hash như một ID vĩnh viễn hay khóa nghiệp vụ.

## 4. Ví dụ Java 17: cùng giá trị, hai kết quả Map khác nhau

Tạo file **HashVsIdentityMapDemo.java**:

~~~java
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class HashVsIdentityMapDemo {
    record CustomerId(String value) {}

    public static void main(String[] args) {
        CustomerId first = new CustomerId("C-17");
        CustomerId second = new CustomerId("C-17");
        CustomerId lookup = new CustomerId("C-17");

        System.out.println("same reference: " + (first == second));
        System.out.println("same value: " + first.equals(second));
        System.out.println("hash equal: " +
            (first.hashCode() == second.hashCode()));

        Map<CustomerId, String> byValue = new HashMap<>();
        byValue.put(first, "created");
        byValue.put(second, "updated");

        System.out.println("HashMap size: " + byValue.size());
        System.out.println("HashMap lookup: " + byValue.get(lookup));

        Map<CustomerId, String> byIdentity =
            new IdentityHashMap<>();
        byIdentity.put(first, "created");
        byIdentity.put(second, "updated");

        System.out.println("IdentityHashMap size: " +
            byIdentity.size());
        System.out.println("IdentityHashMap first: " +
            byIdentity.get(first));
        System.out.println("IdentityHashMap second: " +
            byIdentity.get(second));
        System.out.println("IdentityHashMap fresh: " +
            byIdentity.get(lookup));
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac HashVsIdentityMapDemo.java
java HashVsIdentityMapDemo
~~~

Kết quả:

~~~text
same reference: false
same value: true
hash equal: true
HashMap size: 1
HashMap lookup: updated
IdentityHashMap size: 2
IdentityHashMap first: created
IdentityHashMap second: updated
IdentityHashMap fresh: null
~~~

**Vì sao HashMap chỉ có một entry?** Record **CustomerId** tự sinh equals/hashCode theo component String. Cả first và second đều chứa "C-17", nên hai khóa bằng nhau. Lần put thứ hai cập nhật giá trị của logical key. Một instance CustomerId thứ ba với cùng mã vẫn tìm thấy **"updated"**.

**Vì sao IdentityHashMap có hai entry?** first và second được tạo bằng hai biểu thức **new CustomerId(...)**, nên khác tham chiếu. IdentityHashMap không sử dụng equals/hashCode do record sinh để quyết định cùng khóa. Một instance mới **lookup** không phải first hay second, nên **get(lookup)** trả null.

Nếu chương trình cần đại diện cho “khách hàng C-17”, kết quả IdentityHashMap là **sai về nghĩa nghiệp vụ**: cùng khách hàng bị chia ra nhiều entry. Không có ngoại lệ hay cảnh báo biên dịch nào nhắc về vấn đề này.

## 5. String và wrapper: các trường hợp đánh lừa khi thử bằng ==

Một sai lầm là kiểm tra IdentityHashMap bằng **string literal** rồi tưởng rằng khóa tương đương theo nội dung:

~~~java
String a = "paid";
String b = "paid";
System.out.println(a == b); // thường true vì string literal được intern
~~~

Đoạn này chỉ minh họa. Java intern các string literal tương ứng; điều này khiến hai biến trong ví dụ trỏ cùng object. Nhưng chuỗi nhận từ file, JSON, database hoặc tạo bằng **new String(...)** có thể là instance khác dù giá trị giống nhau.

Tạo file **StringIdentityDemo.java**:

~~~java
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class StringIdentityDemo {
    public static void main(String[] args) {
        String first = new String("paid");
        String second = new String("paid");

        Map<String, Integer> byValue = new HashMap<>();
        Map<String, Integer> byIdentity = new IdentityHashMap<>();

        byValue.put(first, 1);
        byValue.put(second, 2);
        byIdentity.put(first, 1);
        byIdentity.put(second, 2);

        System.out.println("same object: " + (first == second));
        System.out.println("same text: " + first.equals(second));
        System.out.println("value map size: " + byValue.size());
        System.out.println("identity map size: " + byIdentity.size());
        System.out.println("value lookup: " +
            byValue.get(new String("paid")));
        System.out.println("identity lookup: " +
            byIdentity.get(new String("paid")));
    }
}
~~~

Lệnh chạy:

~~~bash
javac StringIdentityDemo.java
java StringIdentityDemo
~~~

Kết quả:

~~~text
same object: false
same text: true
value map size: 1
identity map size: 2
value lookup: 2
identity lookup: null
~~~

Các kiểu wrapper như **Integer** cũng có thể được cache ở một số giá trị; không suy ra ý nghĩa nghiệp vụ của **==** từ vài kết quả thử nghiệm có cache. Nếu giá trị là số, chuỗi hoặc khóa dữ liệu, phải dựa trên **equals** hoặc các phép so primitive phù hợp, không dựa trên tình cờ cùng tham chiếu.

Không “sửa” cách dùng IdentityHashMap bằng việc gọi **String.intern()** một cách tùy tiện. Interning thay đổi chính sách chia sẻ tham chiếu, không thay thế hợp đồng giá trị; việc ép mọi dữ liệu đi qua interning có thể có chi phí hoặc hậu quả quản lý bộ nhớ và vẫn dễ bị bỏ sót ở nguồn khác.

## 6. Hậu quả trong xử lý dữ liệu thật

Một map nghiệp vụ thường dùng để:

- **Gom dữ liệu theo customerId:** khách hàng có thể xuất hiện nhiều lần trong log hoặc payload khác nhau.
- **Khử trùng theo orderId:** hai bản ghi cùng định danh chỉ được tính là một đơn.
- **Tra trạng thái theo correlationId:** một request đi qua nhiều tầng có thể tạo nhiều String mới nhưng cùng nội dung.
- **Đếm số thực thể logic:** hai bản sao value object đại diện cùng ID phải gộp thành một nhóm.
- **Lưu cache theo khóa tham số:** hai khóa có bộ tham số bằng nhau phải truy cập chung một entry.

Trong các bài toán này, IdentityHashMap có thể **nhân đôi entry**, tính sai số lượng, bỏ lỡ cache, mất khả năng tra cứu bằng khóa tương đương tạo mới, hoặc khiến kết quả phụ thuộc vào **việc hệ thống có tái sử dụng cùng instance hay không**.

Ví dụ: xử lý dữ liệu phân tán thường deserialize hai bản JSON độc lập thành hai object Java độc lập. Chúng có thể **equals true**, nhưng không cùng object. IdentityHashMap sẽ đối xử như hai khách hàng khác nhau nếu dùng object đó làm khóa, mặc dù miền nghiệp vụ coi cùng ID là một khách hàng.

Đây là lý do không nên chọn IdentityHashMap chỉ vì thấy tên có chữ **HashMap** và suy đoán nó “tối ưu hơn”. Nó là **cấu trúc có semantics riêng**, dành cho bài toán cần tham chiếu vật lý.

## 7. Ví dụ: khóa tổ hợp nghiệp vụ từ record

Một lỗi khác là gom theo mã ID nhưng quên namespace. Trong hệ thống đa tenant, cùng mã khách hàng ở hai tenant có thể đại diện hai khách hàng khác nhau. **HashMap** vẫn cần khóa logic được thiết kế đúng.

Tạo file **BusinessKeyDemo.java**:

~~~java
import java.util.HashMap;
import java.util.Map;

public class BusinessKeyDemo {
    record CustomerKey(String tenant, String customerCode) {}

    public static void main(String[] args) {
        CustomerKey first = new CustomerKey("A", "C-17");
        CustomerKey duplicate = new CustomerKey("A", "C-17");
        CustomerKey otherTenant = new CustomerKey("B", "C-17");

        Map<CustomerKey, Integer> counts = new HashMap<>();
        counts.merge(first, 1, Integer::sum);
        counts.merge(duplicate, 1, Integer::sum);
        counts.merge(otherTenant, 1, Integer::sum);

        System.out.println("entries: " + counts.size());
        System.out.println("tenant A: " +
            counts.get(new CustomerKey("A", "C-17")));
        System.out.println("tenant B: " +
            counts.get(new CustomerKey("B", "C-17")));
    }
}
~~~

Chạy:

~~~bash
javac BusinessKeyDemo.java
java BusinessKeyDemo
~~~

Kết quả:

~~~text
entries: 2
tenant A: 2
tenant B: 1
~~~

Record **CustomerKey** tự sinh equals/hashCode theo **cả hai component**. Hai bản ghi cùng tenant và cùng customerCode là một khóa, còn cùng customerCode ở tenant khác thì không. HashMap thể hiện chính xác hợp đồng khóa này.

Nếu dùng IdentityHashMap, hai bản ghi của tenant A lại thành hai khóa vì được tạo riêng. Nếu chỉ dùng String customerCode làm khóa HashMap mà bỏ tenant, các khách hàng khác tenant sẽ bị gộp sai. Do đó **chọn HashMap và thiết kế khóa đúng** là hai việc đều quan trọng.

## 8. Khi nào IdentityHashMap thực sự phù hợp?

IdentityHashMap được thiết kế cho các tình huống **danh tính đối tượng có ý nghĩa riêng biệt so với giá trị equals**.

Ví dụ:

1. **Theo dõi object trong thuật toán xử lý đồ thị object:** hai node có cùng dữ liệu nhưng là hai node vật lý khác nhau; thuật toán cần ghi nhớ từng node đã thăm.
2. **Sao chép sâu hoặc serialize đồ thị với tham chiếu chia sẻ:** cần ánh xạ **object gốc → object sao chép**, trong đó hai object gốc có nội dung bằng nhau vẫn phải tạo hai bản sao riêng khi chúng là hai node khác nhau.
3. **Theo dõi trạng thái phụ cho từng instance:** công cụ chẩn đoán, phân tích heap hoặc trình duyệt cấu trúc object cần phân biệt tham chiếu.
4. **Kiểm tra tính đồng nhất tham chiếu:** lưu một thuộc tính gắn với **chính object cụ thể**, không gắn với bất kỳ object nào có cùng giá trị.

Trong những tình huống đó, nếu dùng HashMap và lớp node override equals theo dữ liệu, **hai node riêng** có thể vô tình bị gộp. IdentityHashMap làm đúng yêu cầu phân biệt instance.

Nếu theo dõi các node bằng `Collections.newSetFromMap(new IdentityHashMap<>())`, một lần thêm mỗi instance giữ hai node riêng dù nhãn bằng nhau. Thuật toán DFS/BFS và nhận biết chu kỳ được triển khai đầy đủ ở bài **Dùng danh tính khi duyệt và theo dõi đồ thị đối tượng**.

**Cẩn trọng:** IdentityHashMap giữ tham chiếu mạnh tới khóa và giá trị như một map thông thường trong khi entry còn tồn tại; không dùng nó như cơ chế weak reference. Nếu cần liên kết metadata với object nhưng muốn cho phép garbage collection, phải xem xét cấu trúc như **WeakHashMap** và hợp đồng riêng của nó. Không đánh đồng hai cấu trúc chỉ vì chúng đều chứa chữ Map.

## 9. Trường hợp đặc biệt: hai object equals sai hợp đồng

Có thể có người đổi HashMap thành IdentityHashMap vì **HashMap “không tìm thấy” khóa**. Nhưng nguyên nhân thường là:

- Lớp override **equals** nhưng quên override **hashCode** tương thích.
- Những field tham gia equals/hashCode thay đổi sau khi dùng object làm khóa.
- Các trường mảng dùng equals/hashCode theo danh tính dù nghiệp vụ yêu cầu so nội dung.
- Khóa chỉ chứa một phần định danh nên gộp nhầm các bản ghi khác nhau.
- Sử dụng **LocalDateTime**, **OffsetDateTime**, **BigDecimal** hoặc loại giá trị khác với hợp đồng equals không khớp yêu cầu nghiệp vụ.

**IdentityHashMap có thể che một số triệu chứng** vì nó bỏ qua equals/hashCode tùy biến, nhưng hậu quả là đổi từ “một khóa cho mỗi giá trị” sang “một khóa cho mỗi instance”. Nếu hệ thống cần equals theo giá trị, cách sửa đúng là **khắc phục hợp đồng của kiểu khóa và làm khóa ổn định**, hoặc rút ra một value key bất biến chuyên dụng.

### Ví dụ khóa mutable: đây không phải lý do đổi IdentityHashMap

Tạo file **MutableKeyDemo.java**:

~~~java
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public class MutableKeyDemo {
    static final class MutableKey {
        String code;

        MutableKey(String code) {
            this.code = code;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof MutableKey other
                && Objects.equals(code, other.code);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(code);
        }
    }

    public static void main(String[] args) {
        MutableKey key = new MutableKey("A");
        Map<MutableKey, String> byValue = new HashMap<>();
        Map<MutableKey, String> byIdentity = new IdentityHashMap<>();

        byValue.put(key, "found");
        byIdentity.put(key, "found");

        key.code = "B";

        System.out.println("HashMap after edit: " + byValue.get(key));
        System.out.println("IdentityHashMap after edit: " +
            byIdentity.get(key));

        System.out.println("IdentityHashMap equivalent new key: " +
            byIdentity.get(new MutableKey("B")));
    }
}
~~~

Chạy:

~~~bash
javac MutableKeyDemo.java
java MutableKeyDemo
~~~

Kết quả:

~~~text
HashMap after edit: null
IdentityHashMap after edit: found
IdentityHashMap equivalent new key: null
~~~

**Giải thích:** HashMap lưu khóa ở vị trí tổ chức theo hash của String **"A"**. Sau khi sửa code thành **"B"**, hashCode đổi, nên một lần get mới theo hash của B không tìm đúng entry cũ trong ví dụ này. IdentityHashMap vẫn tìm được khi dùng **cùng tham chiếu key**, vì identity không đổi; nhưng với object khác có cùng code B, nó vẫn không tìm thấy.

**Cách sửa nghiệp vụ:** dùng **record MutableKeySnapshot(String code)** hoặc một lớp khóa bất biến; khi cần thay đổi code, tạo **khóa mới** và cập nhật Map có chủ đích. Không thay loại Map chỉ để tránh biểu hiện của mutable key.

## 10. Không dùng identityHashCode làm giá trị ID

Hai khái niệm quan trọng:

- **obj == other** chứng minh hai tham chiếu là cùng object trong ngữ cảnh chạy.
- **System.identityHashCode(obj) == System.identityHashCode(other)** **không chứng minh** hai tham chiếu là cùng object, vì identity hash có thể va chạm.

Không ghi identityHashCode vào database như khóa duy nhất hoặc dùng để liên kết đối tượng giữa các tiến trình/lần khởi động JVM. Số đó không mang ý nghĩa định danh nghiệp vụ hay địa chỉ bộ nhớ bền vững.

Nếu ứng dụng cần ID thực thể, dùng ID được quản lý rõ ràng trong model (ví dụ UUID hoặc khóa database). Nếu cần phân biệt tham chiếu trong thuật toán tạm thời, dùng **==** hoặc IdentityHashMap/identity set để duy trì quan hệ tham chiếu trực tiếp.

## 11. HashMap và IdentityHashMap trong API công khai

Chữ ký **Map<K,V>** che đi sự khác biệt về cách so khóa. Hai implementation đều có thể được truyền vào một hàm nhận Map, nhưng hành vi lookup/put phụ thuộc instance map thực tế.

Ví dụ một hàm dùng **containsKey(new CustomerId("C-17"))** có thể đúng với HashMap và sai với IdentityHashMap đã nhận một CustomerId tương đương nhưng khác tham chiếu. Nếu API yêu cầu “logical key equality”, việc chấp nhận IdentityHashMap là **sai tiền điều kiện**. Tài liệu và test cần diễn đạt điều kiện này, hoặc module chủ động tạo HashMap với khóa value object rõ ràng.

Một số nơi cố dùng IdentityHashMap để “tăng tốc” vì nó không gọi equals phức tạp. Điều đó thay đổi kết quả nghiệp vụ, không phải tối ưu an toàn. Chỉ tối ưu sau khi chứng minh dữ liệu thật và có một phép chuyển đổi **bảo toàn semantics**. Với Map theo giá trị, có thể cải thiện hashCode, tính bất biến của khóa, phân bổ dung lượng hoặc thiết kế khóa chuyên biệt trong khi vẫn giữ HashMap.

Không dùng **IdentityHashMap** để suy luận rằng **equals** của khóa không quan trọng với hệ thống: các API khác vẫn dùng equals, ví dụ HashSet, HashMap khác, List.contains và nhiều nghiệp vụ so sánh giá trị.

## 12. Kiểm tra đúng ngữ nghĩa bằng bộ test tối thiểu

Đối với bài toán cần gộp theo giá trị, hãy kiểm tra ít nhất:

1. **Hai instance được tạo độc lập, cùng giá trị:** equals true, hashCode bằng nhau; khi put vào HashMap, size chỉ tăng một lần.
2. **Lookup bằng instance thứ ba:** HashMap trả đúng giá trị dù tham chiếu khác khóa đã put.
3. **Khác giá trị nghiệp vụ:** equals false và được giữ thành hai khóa; hashCode có thể tình cờ trùng nhưng vẫn không bị gộp.
4. **Khóa tổ hợp:** những thành phần như tenantId, loại thực thể, mã và phiên bản được xét theo đúng định nghĩa miền.
5. **Khóa ổn định:** không sửa field ảnh hưởng equals/hashCode khi khóa còn ở trong Map.
6. **Null:** nếu null được phép, kiểm tra containsKey để phân biệt “không có entry” với “entry có value null”.
7. **Mảng:** nếu nghiệp vụ coi nội dung là giá trị, dùng Arrays.equals/hashCode hoặc wrapper đúng hợp đồng.
8. **Các phiên bản dữ liệu:** deserialize cùng bản ghi hai lần, tạo hai object độc lập rồi thử lookup/gom nhóm.
9. **Nếu thực sự dùng identity:** khẳng định bằng test rằng hai object equals nhưng khác tham chiếu **phải** là hai khóa riêng.

Test theo dữ liệu độc lập quan trọng vì String literal, boxing hoặc các cơ chế tái sử dụng instance có thể làm ví dụ quá đơn giản vô tình trùng identity và che lỗi.

## 13. Bảng quyết định cho ứng dụng

| Nhu cầu thực sự | Cấu trúc thường phù hợp | Tiêu chí bằng nhau |
| --- | --- | --- |
| Trạng thái theo **customerId**, **orderId**, UUID, record key | **HashMap** | **equals/hashCode** của khóa |
| Tra cứu theo **String** tạo từ nhiều nguồn | **HashMap<String,V>** | Nội dung String |
| Gom nhóm theo **tenant + id** | **HashMap<BusinessKey,V>** | equals/hashCode của value key tổ hợp |
| Cần thứ tự các nhóm theo lần xuất hiện | **LinkedHashMap** | Vẫn là **equals/hashCode** |
| Cần thứ tự sắp xếp theo Comparator | **TreeMap** | **compare == 0** theo comparator; phải kiểm tra nhất quán với equals |
| Theo dõi **từng node object** kể cả khi equals nhau | **IdentityHashMap** | **==** và identity hash |
| Tập hợp các object **theo danh tính** | **Collections.newSetFromMap(new IdentityHashMap<>())** | **==** |
| Metadata gắn với object, cần quản lý theo tham chiếu yếu | Xem xét **WeakHashMap** hoặc cơ chế reference phù hợp | **WeakHashMap vẫn có semantics riêng và dùng equals**, không phải thay thế IdentityHashMap |

**Ghi nhớ cốt lõi:** **HashMap** và **IdentityHashMap** trả lời hai câu hỏi khác nhau. Nếu hệ thống cần “hai bản ghi có cùng giá trị nghiệp vụ là một khóa”, hãy giữ **HashMap** với khóa có equals/hashCode đúng và ổn định. **IdentityHashMap** chỉ phù hợp khi yêu cầu chính là **cùng tham chiếu object**; thay nó vào Map theo giá trị sẽ làm mất tính đúng đắn của dữ liệu mà không gây lỗi biên dịch.

Tham khảo chính thức: [HashMap (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html), [IdentityHashMap (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html), [Object.equals/hashCode (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="identity-comparison-006">IdentityHashMap cố ý khác hợp đồng Map thông thường ở quan hệ so sánh</a>

<details>
<summary>Xem chi tiết</summary>

# IdentityHashMap: Vì sao cố ý khác hợp đồng Map thông thường ở quan hệ so sánh

Trong Java, `IdentityHashMap<K, V>` triển khai giao diện `Map<K, V>` nhưng **cố ý sử dụng danh tính đối tượng (reference identity, toán tử `==`)** để phân biệt các khóa và so sánh các giá trị, thay cho quan hệ bằng nhau về mặt logic (`equals()`). Đây là ngoại lệ được mô tả công khai trong API, phù hợp với một số bài toán đặc biệt; không phải lỗi của `IdentityHashMap`.

Ý chính cần ghi nhớ: **hai đối tượng có `equals() == true` vẫn có thể là hai khóa khác nhau trong `IdentityHashMap`**. Khi đem map này so sánh với một map thông thường như `HashMap`, thậm chí `Map.equals()` có thể không còn đối xứng.

## 1. Phân biệt bằng nhau về giá trị và bằng nhau về danh tính

Giả sử:

```java
String a = new String("java");
String b = new String("java");

System.out.println(a.equals(b)); // true
System.out.println(a == b);      // false
```

- `a.equals(b)` trả về `true` vì hai chuỗi mang cùng nội dung.
- `a == b` trả về `false` vì `new` tạo ra hai đối tượng riêng biệt.
- Việc `equals()` trả về `true` không có nghĩa là hai biến trỏ tới cùng đối tượng. Cùng tham chiếu thì `==` luôn là `true`, bất kể lớp ghi đè `equals()` ra sao.

Với khóa trong `HashMap`, Java dùng `hashCode()` và `equals()` để tìm khóa tương đương logic. `IdentityHashMap` dùng `==` kết hợp mã băm danh tính (`System.identityHashCode()`). Mã băm danh tính chỉ là công cụ tra cứu: **hai đối tượng khác nhau vẫn có thể trùng identity hash code**, và map phải phân biệt chúng bằng `==`. Mã băm này không phải địa chỉ bộ nhớ hoặc ID duy nhất tuyệt đối.

| Tình huống | `HashMap` thông thường | `IdentityHashMap` |
| --- | --- | --- |
| Hai khóa cùng một tham chiếu | Cùng một khóa | Cùng một khóa |
| Hai khóa khác tham chiếu nhưng `equals()` là `true` | Cùng khóa nếu tuân thủ hợp đồng `hashCode/equals` | Hai khóa riêng |
| `get(key)` / `containsKey(key)` | Dựa trên tương đương logic của key | Dựa trên đúng tham chiếu đã lưu |
| `containsValue(value)` | So sánh theo `equals()` | So sánh bằng `==` |
| Hai entry có key/value bằng nhau về nội dung nhưng khác tham chiếu | Có thể là cùng mapping | Không cùng mapping theo danh tính |

**Lưu ý:** `IdentityHashMap` không băm theo value; việc kiểm tra danh tính value thể hiện ở `containsValue()` và quan hệ so sánh các entry/map. `get()` vẫn chọn mapping dựa trên **danh tính key**.

## 2. “Vi phạm hợp đồng Map” chính xác là gì?

Hợp đồng `Map` thông thường mô tả tính tương đương của khóa dựa trên `equals()` và quan hệ bằng nhau giữa hai map dựa trên các mapping tương đương. `IdentityHashMap` triển khai thao tác `Map` nhưng chủ ý thay tiêu chí tương đương khóa/giá trị bằng **cùng tham chiếu đối tượng**.

Các hệ quả quan trọng:

1. **Tính duy nhất của khóa thay đổi.** `put(k1, v1)` rồi `put(k2, v2)` không thay mapping cũ chỉ vì `k1.equals(k2)` là `true`; hai key phải là chính cùng đối tượng.
2. **`IdentityHashMap.equals(other)` xét các mapping theo danh tính.** Đặc tả diễn đạt bằng `this.entrySet().equals(other.entrySet())`, trong đó các entry của `IdentityHashMap` sử dụng quan hệ tham chiếu.
3. **So sánh với map thông thường có thể bất đối xứng:** tồn tại trường hợp `normalMap.equals(identityMap) == true` nhưng `identityMap.equals(normalMap) == false`. Javadoc còn cảnh báo tính bắc cầu (`transitivity`) của `Object.equals` có thể bị vi phạm khi trộn các kiểu map.
4. **`hashCode()` của map dựa trên entry với ngữ nghĩa identity.** Quy tắc “bằng nhau thì hash code bằng nhau” được đảm bảo khi so sánh giữa các `IdentityHashMap`; khi so sánh chéo với map thường, không thể mặc định quy tắc đó còn đúng.

Điểm mấu chốt: **giữa các instance `IdentityHashMap`, hợp đồng `equals/hashCode` vẫn được bảo đảm**; rủi ro xuất hiện khi code áp dụng giả định của `HashMap`/`Map` thông thường hoặc so sánh chéo các kiểu map.

## 3. Ví dụ Java 17+: Chứng minh khác biệt và tính bất đối xứng

Lưu thành `IdentityMapContractDemo.java`:

```java
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class IdentityMapContractDemo {
    public static void main(String[] args) {
        String k1 = new String("code");
        String k2 = new String("code");
        String v1 = new String("same");
        String v2 = new String("same");

        Map<String, String> normal = new HashMap<>();
        Map<String, String> byIdentity = new IdentityHashMap<>();

        normal.put(k1, v1);
        normal.put(k2, v2);
        byIdentity.put(k1, v1);
        byIdentity.put(k2, v2);

        System.out.println("k1.equals(k2) = " + k1.equals(k2));
        System.out.println("k1 == k2 = " + (k1 == k2));
        System.out.println("HashMap.size = " + normal.size());
        System.out.println("IdentityHashMap.size = " + byIdentity.size());
        System.out.println("IdentityHashMap.containsKey(new key) = "
                + byIdentity.containsKey(new String("code")));
        System.out.println("IdentityHashMap.containsValue(new value) = "
                + byIdentity.containsValue(new String("same")));
        System.out.println("IdentityHashMap.containsValue(v1) = "
                + byIdentity.containsValue(v1));

        // Cùng object khóa, hai object giá trị khác nhau nhưng equals() == true.
        Object sharedKey = new Object();
        Map<Object, String> regularOne = new HashMap<>();
        Map<Object, String> identityOne = new IdentityHashMap<>();
        regularOne.put(sharedKey, new String("same"));
        identityOne.put(sharedKey, new String("same"));

        System.out.println("HashMap.equals(IdentityHashMap) = "
                + regularOne.equals(identityOne));
        System.out.println("IdentityHashMap.equals(HashMap) = "
                + identityOne.equals(regularOne));
    }
}
```

Biên dịch và chạy:

```bash
javac IdentityMapContractDemo.java
java IdentityMapContractDemo
```

Kết quả chuẩn:

```text
k1.equals(k2) = true
k1 == k2 = false
HashMap.size = 1
IdentityHashMap.size = 2
IdentityHashMap.containsKey(new key) = false
IdentityHashMap.containsValue(new value) = false
IdentityHashMap.containsValue(v1) = true
HashMap.equals(IdentityHashMap) = true
IdentityHashMap.equals(HashMap) = false
```

Giải thích:

- Hai chuỗi khóa cùng nội dung nhưng khác đối tượng. `HashMap` giữ một mapping vì khóa thứ hai tương đương khóa đầu; `IdentityHashMap` giữ cả hai vì `k1 != k2`.
- Tạo chuỗi mới `new String("code")` để tra cứu không tìm thấy key của `IdentityHashMap` dù nội dung bằng nhau. Tương tự, `containsValue(new String("same"))` là `false`; dùng đúng object `v1` thì là `true`.
- Ở phần cuối, hai map chứa **chính cùng object `sharedKey`**, nhưng value là hai `String` khác tham chiếu, cùng nội dung. `HashMap.equals` chấp nhận value tương đương theo `equals()` nên trả `true`. `IdentityHashMap.equals` đòi đúng value reference nên trả `false`. Đây là phản ví dụ cụ thể cho tính đối xứng, **không phải** hiện tượng ngẫu nhiên do thứ tự duyệt hay va chạm hash.

**Không dùng `System.identityHashCode(...)` để dự đoán output:** con số hash có thể khác giữa các lần chạy, không biểu thị thứ tự map và không chứng minh hai tham chiếu là cùng object.

## 4. Ảnh hưởng đến API, collection views và chuyển đổi map

### Các thao tác tra cứu

- `put(k, v)` thay thế value chỉ khi đã tồn tại mapping với **chính object `k`**; object khóa mới có cùng nội dung được thêm thành mapping mới.
- `get(k)`, `containsKey(k)`, `remove(k)` tra cứu bằng identity của key. `get(k) == null` có thể nghĩa là không có key **hoặc** key đang ánh xạ tới `null`; dùng `containsKey(k)` để phân biệt.
- `containsValue(v)` dùng identity của value, không dùng `v.equals(...)`.
- `IdentityHashMap` cho phép một key `null` và các value `null`.

### `keySet()`, `entrySet()`, `values()`

- `keySet()` là một **set dựa trên danh tính**: hai key khác object nhưng `equals() == true` vẫn cùng xuất hiện. Không nên mặc định rằng view này so sánh với mọi `HashSet` theo quy ước `Set.equals` thông thường.
- `entrySet()` thể hiện mapping theo identity của **cả key lẫn value**. Đây là nền tảng của `IdentityHashMap.equals/hashCode`.
- `values()` là collection view, được liên kết với map gốc, không phải bản sao. Việc kiểm tra value hiện diện tuân theo identity semantics.
- `IdentityHashMap` không bảo đảm thứ tự duyệt; không viết test phụ thuộc thứ tự `keySet()`/`entrySet()`/`values()`.

### Chuyển qua `HashMap` có thể làm mất mapping

Nếu `identityMap` chứa hai khóa `k1` và `k2` khác object nhưng `k1.equals(k2) == true`:

```java
Map<String, String> copied = new HashMap<>(identityMap);
```

`copied` có thể gộp hai mapping thành một; value còn lại phụ thuộc thứ tự duyệt nguồn nên không được dựa vào nó. Ngược lại, tạo `IdentityHashMap` từ `HashMap` không thể khôi phục các khóa khác object đã bị gộp. Hai lớp cùng triển khai `Map<K,V>` nhưng việc đổi implementation có thể thay **ý nghĩa dữ liệu**.

### Bẫy khi dùng `Map.equals/hashCode` để tích hợp

Một API nhận `Map` bất kỳ thường giả định `equals` đối xứng, bắc cầu và tương thích `hashCode`. Với `IdentityHashMap`, các giả định ấy có thể thất bại khi trộn với map thường. Cần xem xét code so sánh map, dùng map làm key trong map khác, kiểm tra `assertEquals(expectedHashMap, actualIdentityMap)`, hoặc chuyển đổi dữ liệu qua API không nêu rõ tiêu chí equality. **Thứ tự hai đối số so sánh có thể làm thay kết quả.**

## 5. Khi nào nên dùng IdentityHashMap?

Chỉ dùng khi bài toán cần định danh **đúng từng instance**, kể cả các instance tình cờ mang dữ liệu giống nhau:

- **Sao chép/chuyển đổi đồ thị object có chia sẻ hoặc chu trình:** ánh xạ mỗi *node object gốc* sang đúng *node object mới*. Nếu hai node khác nhau nhưng `equals()` giống nhau, nhập chúng làm một sẽ phá cấu trúc đồ thị.
- **Theo dõi object đã thăm trong quá trình duyệt đồ thị:** các node đại diện hai thực thể riêng phải được xử lý riêng, dù cùng thuộc tính.
- **Ánh xạ object gốc sang proxy hoặc metadata chẩn đoán:** giữ một proxy riêng cho từng object cụ thể trong runtime.

Khi chỉ cần tập các đối tượng đã thăm, dùng `Collections.newSetFromMap(new IdentityHashMap<>())` với map ban đầu trống. Bài **Dùng danh tính khi duyệt và theo dõi đồ thị đối tượng** có ví dụ DFS và phân biệt việc gặp lại node với chu kỳ.

## 6. Những nhầm lẫn## 6. Những nhầm lẫn và giới hạn cần tránh

1. **Không nghĩ `IdentityHashMap` là `HashMap` nhanh hơn.** Hai cấu trúc có ngữ nghĩa khác nhau. Chi tiết bảng băm và tối ưu không phải lý do thay thế nếu nghiệp vụ dùng `equals()`.
2. **Không dùng cho khóa nghiệp vụ “bằng giá trị”.** Email, mã sản phẩm, ID hay chuỗi được tạo lại qua request có thể thành nhiều entry khó tra cứu dù cùng nội dung.
3. **Không hiểu `==` là `equals()`.** Với object, `==` so tham chiếu; ghi đè `equals/hashCode` không thay đổi điều này.
4. **Không dùng identity hash code làm ID toàn cục.** `System.identityHashCode(x)` có thể trùng giữa các object khác nhau và không phải định danh bền vững qua tiến trình.
5. **Không giả định thứ tự, thread safety hoặc tự giải phóng key.** `IdentityHashMap` không bảo đảm thứ tự, không đồng bộ hóa mặc định và giữ tham chiếu mạnh tới key/value. Khi nhiều luồng có sửa cấu trúc phải đồng bộ phù hợp; nếu cần weak keys, chọn cấu trúc theo yêu cầu đó.
6. **Không trông chờ iterator fail-fast làm cơ chế đồng bộ.** `ConcurrentModificationException` chỉ được phát hiện ở mức cố gắng tốt nhất, không thay cho synchronization.
7. **Không so sánh bằng `Map.equals` khi hai bên dùng tiêu chí khác nhau.** Xác định rõ cần cùng instance hay cùng dữ liệu; nếu chuyển sang cấu trúc dùng equality theo giá trị, phải đánh giá trước rủi ro gộp key.

## 7. Checklist thực hành

Trước khi chọn `IdentityHashMap`, hãy trả lời:

- Hai key khác tham chiếu nhưng `equals() == true` có **bắt buộc** phải được giữ riêng không?
- Việc so sánh value (`containsValue`, `entrySet`, `Map.equals`) có thật sự cần `==` thay cho `equals()` không?
- Code gọi có dựa vào hợp đồng `Map.equals/hashCode` khi so sánh với `HashMap`/`TreeMap` không?
- Có bước copy map, serialize, cache hoặc expose `Map` ra API khác làm mất ý nghĩa identity không?
- Test bao gồm cả **cùng tham chiếu**, **khác tham chiếu nhưng bằng giá trị**, **`null`**, và **so sánh chéo loại map** chưa?

Nếu tiêu chí là bằng nhau về logic, `HashMap` thường phù hợp. Nếu tiêu chí là **object nào trong bộ nhớ**, `IdentityHashMap` đáp ứng mục đích đó; hãy bảo vệ ranh giới ngữ nghĩa khi kết hợp với collection khác.

## Tài liệu tham khảo

- [Java SE 17 API — IdentityHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html): mô tả identity semantics, ngoại lệ với hợp đồng `Map`, `equals/hashCode`, collection views và ứng dụng trên đồ thị object.
- [Java SE 17 API — Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): hợp đồng tổng quát về key, value, `equals` và `hashCode`.
- [Java SE 17 API — System.identityHashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#identityHashCode(java.lang.Object)): mã băm theo danh tính, độc lập với override `hashCode()`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="identity-comparison-007">Đối tượng value-based và lý do không dựa vào danh tính thể hiện</a>

<details>
<summary>Xem chi tiết</summary>

# Đối tượng value-based và lý do không dựa vào danh tính thể hiện trong Java

Trong Java, hai biến tham chiếu có thể chỉ tới **hai đối tượng khác nhau** nhưng mô tả **cùng một giá trị**. Với các lớp *value-based* (lớp dựa trên giá trị), sự khác biệt về đối tượng nào được cấp phát, tái sử dụng hay chia sẻ không phải là điều chương trình nên dựa vào. Điều quan trọng là **giá trị theo hợp đồng của lớp**.

Bài này nằm trong chủ đề **so sánh theo danh tính** của Java Core: nhận diện khi nào phải dùng so sánh theo giá trị và vì sao những phép kiểm tra dựa vào danh tính có thể làm hỏng logic, khóa đồng bộ, hoặc cấu trúc dữ liệu.

## 1. Phân biệt danh tính tham chiếu và tính bằng nhau về giá trị

Xét hai tham chiếu kiểu đối tượng:

- `a == b`, nếu cả hai toán hạng đều là **tham chiếu**, hỏi liệu chúng có trỏ tới **cùng một đối tượng** ở thời điểm đó không. Đây là so sánh theo danh tính (*reference identity*).
- `a.equals(b)` hỏi liệu chúng có **bằng nhau theo hợp đồng `equals`** của lớp không. Với value-based class, hợp đồng này dựa vào giá trị.
- `Objects.equals(a, b)` thực hiện so sánh theo `equals` có xử lý `null`: cả hai `null` thì `true`, một bên `null` thì `false`.
- `System.identityHashCode(a)` cung cấp mã băm liên quan đến danh tính tham chiếu, **không** thay thế cho `a.hashCode()` khi cần nhận diện một giá trị.

Ví dụ, ngày 23/10/2026 là **một giá trị lịch**. Hai lời gọi `LocalDate.of(2026, 10, 23)` mô tả cùng ngày, dù cách JVM thể hiện và quản lý các đối tượng trả về có thể khác nhau. Do đó, câu hỏi nghiệp vụ là *có cùng ngày không?*; kiểm tra đúng là `equals`.

> Cần phân biệt: trong Java 17, nhiều thể hiện value-based vẫn là các đối tượng tham chiếu bình thường ở cấp JVM. Khái niệm *value-based* không có nghĩa mọi phép `==` đều bị cấm biên dịch hoặc lớp đó chưa từng có danh tính vật lý. Nó có nghĩa **mã ứng dụng không được lấy danh tính đó làm một phần của hợp đồng xử lý giá trị**.

## 2. Thế nào là một value-based class?

Theo tài liệu Java SE 17, lớp value-based có các đặc điểm cốt lõi:

1. Là lớp `final`, không thiết kế để kế thừa tùy ý; các trường thể hiện được khai báo `final`. Trường có thể tham chiếu tới đối tượng mutable, vì vậy phải hiểu tính bất biến theo đúng hợp đồng từng API.
2. `equals`, `hashCode` và `toString` được tính từ trạng thái giá trị thích hợp, không từ danh tính đối tượng.
3. Các phương thức của lớp xem hai thể hiện có `equals` bằng nhau là **có thể thay thế cho nhau** (*freely substitutable*) đối với hành vi được lớp bảo đảm.
4. Bản thân lớp không dùng monitor của thể hiện làm cơ chế đồng bộ.
5. Không cung cấp constructor công khai mang nghĩa “mỗi lần tạo chắc chắn được một danh tính mới”; constructor truy cập được sẽ không có hoặc đã bị đánh dấu deprecated.
6. Factory không cam kết luôn trả về đối tượng mới hay luôn trả về đối tượng cũ: với cùng giá trị, các lần tạo độc lập **có thể** nhận lại cùng tham chiếu hoặc tham chiếu khác.

Ví dụ quen thuộc trong Java 17 gồm `java.time.LocalDate`, `java.time.Instant`, `java.util.Optional` và những lớp bao kiểu nguyên thủy như `java.lang.Integer`. Luôn xem Javadoc của lớp cụ thể để xác nhận nó được tài liệu công bố là *value-based*.

**Hệ quả thiết kế:** API cho phép thay thế một thể hiện bằng thể hiện khác có cùng giá trị mà không làm thay đổi hành vi được bảo đảm. Người dùng API không nên đòi hỏi một danh tính ổn định hoặc duy nhất cho mỗi lời gọi factory.

## 3. Vì sao `==` không thể là tiêu chí về giá trị?

Giả sử:

```java
LocalDate first  = LocalDate.of(2026, 10, 23);
LocalDate second = LocalDate.of(2026, 10, 23);

// Đúng: kiểm tra cùng giá trị ngày.
boolean sameDate = first.equals(second);

// Sai về mặt hợp đồng: dựa vào cách hai tham chiếu được thể hiện.
boolean sameReference = (first == second);
```

Biến `sameDate` phù hợp với bài toán; `sameReference` chỉ là quan sát một chi tiết thể hiện. Không được suy ra rằng với value-based class, `==` *luôn* là `true` hoặc *luôn* là `false` khi hai giá trị bằng nhau.

Các lớp bao kiểu nguyên thủy cũng dễ gây ngộ nhận:

```java
Integer x = Integer.valueOf(1000);
Integer y = Integer.valueOf(1000);
boolean equalValue = x.equals(y); // true
boolean sameObject = (x == y);    // Đừng dùng để suy ra bằng nhau về số.
```

Factory có thể cache hoặc dùng lại thể hiện theo giá trị và triển khai. Quan sát `==` ở một môi trường không phải cam kết cho những môi trường hay phiên bản sau.

**Lưu ý về autoboxing:** `Integer a == Integer b` so sánh tham chiếu vì hai bên là đối tượng; `int a == int b` so sánh giá trị số; `int a == Integer b` thường unbox `b` rồi so sánh số, và có thể ném `NullPointerException` nếu `b` là `null`. Vì vậy phải xem **kiểu thực tế của hai toán hạng**, không chỉ nhìn ký hiệu `==`.

## 4. Ví dụ Java 17 chạy được: so sánh, Set và Map

Lưu đoạn sau vào tệp `ValueBasedDemo.java`:

```java
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ValueBasedDemo {
    public static void main(String[] args) {
        LocalDate first = LocalDate.of(2026, 10, 23);
        LocalDate second = LocalDate.of(2026, 10, 23);

        System.out.println("LocalDate.equals: " + first.equals(second));

        Set<LocalDate> days = new HashSet<>();
        days.add(first);
        days.add(second);
        System.out.println("Distinct day values: " + days.size());

        Map<LocalDate, String> descriptions = new HashMap<>();
        descriptions.put(first, "ngay hoc");
        System.out.println("Lookup with equal key: " + descriptions.get(second));

        Optional<String> one = Optional.of("Java");
        Optional<String> two = Optional.of("Java");
        System.out.println("Optional.equals: " + one.equals(two));

        Integer x = Integer.valueOf(1000);
        Integer y = Integer.valueOf(1000);
        System.out.println("Integer.equals: " + x.equals(y));

        System.out.println("Null-safe equals: " + Objects.equals(null, null));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac ValueBasedDemo.java
java ValueBasedDemo
```

Kết quả chuẩn:

```text
LocalDate.equals: true
Distinct day values: 1
Lookup with equal key: ngay hoc
Optional.equals: true
Integer.equals: true
Null-safe equals: true
```

Giải thích:

- `LocalDate.equals` kiểm tra ngày; cả hai thể hiện mô tả cùng ngày nên bằng nhau.
- `HashSet` dựa trên `equals` và `hashCode`, nên không giữ hai phần tử tương đương về giá trị ngày.
- `HashMap` tìm lại được dữ liệu bằng khóa khác tham chiếu nhưng bằng nhau theo `equals` và có `hashCode` tương ứng.
- Hai `Optional` chứa cùng chuỗi có `equals` bằng nhau; `Optional.empty()` cũng phải được kiểm tra bằng API như `isEmpty()`, không dựa vào `==`.
- `Integer.equals` biểu đạt bình đẳng của giá trị số đóng hộp. Ví dụ cố ý không in `first == second` hay `x == y` vì chúng không có kết luận giá trị hữu ích.

## 5. Những thao tác nhạy với danh tính cần tránh

| Thao tác | Vấn đề khi dùng với value-based object | Hướng thay thế |
| --- | --- | --- |
| `a == b` hoặc `a != b` để so giá trị | Thực chất kiểm tra cùng tham chiếu; factory không hứa cách cấp phát/tái sử dụng | `a.equals(b)`, `Objects.equals(a, b)` hoặc API so sánh theo miền |
| `System.identityHashCode(a)` làm khóa giá trị | Dựa vào danh tính, không theo hợp đồng giá trị | `a.hashCode()` đi cùng `equals()`; hoặc dùng trực tiếp `a` làm khóa `HashMap` |
| `IdentityHashMap<ValueType, ...>` | Map dùng `==` cho khóa nên hai giá trị tương đương không nhất thiết được coi là một khóa | `HashMap` / `ConcurrentHashMap` khi khóa là giá trị |
| `synchronized (value)`, `value.wait()`, `value.notify()` | Monitor thuộc thể hiện cụ thể, dễ bị dùng chung ngoài ý muốn hoặc thay đổi qua các phiên bản Java | Khóa riêng kiểu `Object` hoặc cấu trúc đồng bộ của `java.util.concurrent` |
| Dùng số lượng object / danh tính cấp phát làm dấu hiệu nghiệp vụ | Tối ưu cache, factory hoặc đặc tính runtime có thể thay đổi cách thể hiện | Lưu một ID miền nghiệp vụ rõ ràng nếu cần định danh thực thể |

Một `IdentityHashMap` có thể hoàn toàn phù hợp khi bạn chủ ý theo dõi **danh tính của các đối tượng thông thường** (ví dụ duyệt đồ thị object). Vấn đề ở đây là dùng nó để biểu diễn **bản đồ theo giá trị** của các lớp value-based.

Không nên sử dụng `WeakReference` hoặc hình thức lưu/truy vết dựa trên từng thể hiện cụ thể để tự tạo cam kết danh tính cho một giá trị. Vòng đời tham chiếu yếu và vòng đời của giá trị là hai khái niệm khác nhau; hãy chọn dữ liệu khóa/định danh đúng với mục tiêu.

## 6. Đặc biệt nguy hiểm: dùng value-based object làm khóa `synchronized`

Mã dưới là **phản ví dụ minh họa**, không phải lời khuyên sử dụng:

```java
// Giả sử day là một LocalDate.
synchronized (day) {
    // Tránh đồng bộ theo một value-based object.
}
```

Có hai rủi ro đối nghịch nhưng đều thực tế:

1. Nếu hai bên xử lý cùng giá trị ngày nhưng nhận **tham chiếu khác nhau**, chúng sẽ khóa **hai monitor khác nhau**, nên không bảo vệ cùng tài nguyên.
2. Nếu factory chia sẻ tham chiếu, những phần code **không liên quan** có thể vô tình cùng khóa một monitor, gây tranh chấp hoặc deadlock.

Từ JDK 16, JEP 390 bổ sung cảnh báo của `javac` cho việc đồng bộ trên các lớp value-based do JDK nhận biết. Có thể dùng `-Xlint:synchronization` để yêu cầu loại chẩn đoán này khi rà soát. Cảnh báo là công cụ tìm lỗi; việc một đoạn mã không phát cảnh báo **không chứng minh** rằng cách khóa của nó đúng (ví dụ nếu đối tượng được giữ dưới biến kiểu `Object`). Một số JDK hỗ trợ tùy chọn chẩn đoán runtime `-XX:DiagnoseSyncOnValueBasedClasses`; đây là tùy chọn phụ thuộc HotSpot/JDK, không phải giải pháp thiết kế.

Khi cần monitor để bảo vệ trạng thái riêng, hãy dùng một đối tượng khóa có chủ sở hữu rõ ràng:

```java
// Đoạn minh họa là thành viên trong một lớp quản lý trạng thái.
private final Object lock = new Object();

void updateState() {
    synchronized (lock) {
        // Đọc/ghi trạng thái cần bảo vệ.
    }
}
```

Nếu cần đồng bộ theo một khóa *nghiệp vụ* dùng chung giữa nhiều luồng, chọn cấu trúc dữ liệu đồng bộ có quản lý khóa/tính nguyên tử theo **giá trị khóa**, chẳng hạn `ConcurrentHashMap.compute(...)`. Không thay thế bằng cách `synchronized (LocalDate.of(...))` rồi kỳ vọng toàn bộ luồng sẽ được tuần tự hóa.

## 7. Ví dụ áp dụng: đếm tác vụ theo ngày một cách an toàn

Kịch bản: các lời gọi có thể tạo những `LocalDate` khác tham chiếu nhưng cùng ngày; toàn bộ tác vụ trong ngày phải tăng cùng bộ đếm. Chương trình sau dùng tính bằng nhau của khóa và cấu trúc concurrent:

```java
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DailyCountDemo {
    private static final ConcurrentHashMap<LocalDate, AtomicInteger> COUNTS =
            new ConcurrentHashMap<>();

    public static void main(String[] args) {
        LocalDate first = LocalDate.of(2026, 10, 23);
        LocalDate second = LocalDate.of(2026, 10, 23);

        COUNTS.computeIfAbsent(first, k -> new AtomicInteger()).incrementAndGet();
        COUNTS.computeIfAbsent(second, k -> new AtomicInteger()).incrementAndGet();

        System.out.println("Count: " + COUNTS.get(first).get());
        System.out.println("Distinct date keys: " + COUNTS.size());
    }
}
```

Lưu thành `DailyCountDemo.java` và chạy:

```bash
javac DailyCountDemo.java
java DailyCountDemo
```

Kết quả:

```text
Count: 2
Distinct date keys: 1
```

`computeIfAbsent` chọn/lưu một bộ đếm cho mỗi **giá trị khóa**, còn `AtomicInteger.incrementAndGet()` cập nhật bộ đếm nguyên tử. Đây là hướng phù hợp khi truy cập đồng thời; không có giả định nào về việc hai lời gọi `LocalDate.of` trả về cùng đối tượng. Trong ứng dụng thật, quản lý kích thước/vòng đời map phù hợp với số lượng ngày cần giữ.

## 8. Khi nào so sánh theo danh tính vẫn đúng?

Không có quy tắc rằng Java phải loại bỏ hoàn toàn `==` cho đối tượng. Điều quan trọng là **ý định so sánh**:

- Với **primitive** `int`, `long`, `boolean`..., `==` là so sánh giá trị theo quy tắc của toán tử.
- Với **enum**, `==` là cách so sánh hằng enum thường dùng, vì mỗi hằng là thể hiện xác định do ngôn ngữ quản lý.
- Với **đối tượng thường** có ý nghĩa danh tính (cùng thực thể/một node đồ thị/một instance đang được theo dõi), `==` có thể là lựa chọn chủ đích.
- Với **value-based class**, so sánh quan hệ giá trị bằng `equals` hoặc API miền chuyên biệt; không dùng danh tính như bằng chứng rằng hai giá trị bằng/khác.
- Một `record` do người dùng khai báo có `equals/hashCode` dựa trên components, nhưng **không tự động trở thành lớp value-based của Java Platform** theo nghĩa tài liệu JDK. Đừng đồng nhất “immutable”, “record”, “value object” trong thiết kế miền với nhãn kỹ thuật *value-based class*.

Khi cần phân biệt *hai thực thể nghiệp vụ* tình cờ có cùng thuộc tính (chẳng hạn hai đơn hàng khác nhau cùng tổng tiền), hãy lưu ID thực thể riêng; khi cần so sánh *một giá trị* (ngày, khoảng thời gian, số đóng hộp), dùng hợp đồng giá trị. Không suy ra ID nghiệp vụ từ địa chỉ đối tượng hoặc mã băm danh tính.

## 9. Lỗi thường gặp và cách tự kiểm

**Lỗi 1: “Tôi thử trên máy và `==` đúng.”** Một lần thực thi chỉ chứng minh hai tham chiếu tình cờ trùng nhau tại thời điểm đó. Kết quả có thể bị tác động bởi cache, factory, môi trường hoặc thay đổi triển khai. Viết kiểm thử theo `equals` và hành vi, không theo `assertSame` / `assertNotSame` cho value-based objects.

**Lỗi 2: “Cùng giá trị thì `synchronized(value)` khóa cùng tài nguyên.”** Bằng nhau về giá trị không đảm bảo chung monitor; ngược lại việc chia sẻ đối tượng có thể tạo liên hệ khóa ngoài ý muốn. Chọn khóa riêng hoặc công cụ concurrent.

**Lỗi 3: “Có `hashCode()` thì dùng `identityHashCode()` cũng được.”** `hashCode` của lớp value-based phục vụ giao ước `equals/hashCode`; `identityHashCode` theo danh tính. Không trộn hai hệ quy chiếu này khi dùng tập hợp hoặc map.

**Lỗi 4: “Immutable nào cũng được JDK công bố value-based.”** Tính bất biến là một thuộc tính; hợp đồng value-based còn liên quan factory, danh tính và tính thay thế tự do. Kiểm tra Javadoc trước khi áp dụng kết luận cho một lớp.

**Checklist khi review code:** Nếu logic đang kiểm tra `==`, chọn khóa `IdentityHashMap`, dùng `identityHashCode`, monitor `synchronized` hoặc tham chiếu yếu trên một đối tượng mô tả giá trị, hãy hỏi: *Mình thực sự cần danh tính instance hay cần giá trị?* Nếu cần giá trị, đổi sang `equals/hashCode` hoặc API nghiệp vụ phù hợp; nếu cần danh tính, chọn một thực thể/ID có hợp đồng danh tính rõ ràng.

## 10. Tóm lược và tài liệu chuẩn

**Nguyên tắc nhớ:** Với value-based class, **hai thể hiện có cùng giá trị phải được đối xử tương đương theo hợp đồng của lớp**. Danh tính mà JVM biểu hiện ở một lần chạy là chi tiết không nên phụ thuộc để quyết định so sánh, phân nhóm, khóa hay nhận diện thực thể.

Tài liệu tham khảo chính thức:

- [Java SE 17 — Value-based Classes](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/doc-files/ValueBased.html): định nghĩa và những thao tác nhạy với danh tính cần tránh.
- [Java SE 17 — Optional](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html): nhấn mạnh các thể hiện bằng nhau có thể thay thế và không dùng cho đồng bộ.
- [OpenJDK JEP 390 — Warnings for Value-Based Classes](https://openjdk.org/jeps/390): cảnh báo liên quan đến đồng bộ và định hướng tránh phụ thuộc danh tính.


</details>

- [Quay lại đầu trang](#back-to-top)
