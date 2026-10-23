<a id="back-to-top"></a>

# String trong collection

## Menu
- [String làm khóa HashMap hoặc phần tử HashSet](#string-collections-001)
- [Hợp đồng equals–hashCode và va chạm băm](#string-collections-002)
- [So sánh theo nội dung thay vì dựa vào intern](#string-collections-003)
- [Sắp xếp List&lt;String&gt; theo tự nhiên hoặc Comparator](#string-collections-004)
- [Comparator không phân biệt hoa–thường và quan hệ với equals](#string-collections-005)
- [Chuẩn hóa khóa khác với bảo toàn văn bản hiển thị](#string-collections-006)
- [Phân biệt String&#91;&#93; với List&lt;String&gt; khi chuyển đổi dữ liệu](#string-collections-007)

## <a id="string-collections-001">String làm khóa HashMap hoặc phần tử HashSet</a>

<details>
<summary>Xem chi tiết</summary>

# String làm khóa HashMap hoặc phần tử HashSet trong Java

## 1. Mục tiêu và bối cảnh

`String` là một trong những kiểu thường được dùng nhất làm **khóa** của `HashMap<String, V>` và làm **phần tử** của `HashSet<String>`: mã sản phẩm, tên cấu hình, nhãn trạng thái, token, tên đăng nhập, từ vựng hoặc chuỗi dùng để loại trùng. Hai collection này dựa vào cặp phương thức **`hashCode()` và `equals(Object)`** để nhận diện khóa/phần tử tương đương.

Ví dụ:

~~~java
Map<String, Integer> points = new HashMap<>();
points.put("java", 10);
points.put("spring", 20);
System.out.println(points.get("java")); // 10

Set<String> tags = new HashSet<>();
tags.add("backend");
tags.add("backend");
System.out.println(tags.size()); // 1
~~~

Trong các ví dụ, cần có import `java.util.Map`, `java.util.HashMap`, `java.util.Set` và `java.util.HashSet` nếu viết thành lớp Java độc lập. Các đoạn riêng lẻ chỉ nhằm minh họa; chương trình hoàn chỉnh có thể biên dịch bằng Java 17 ở phần 8.

**Điểm cốt lõi:** `HashMap` và `HashSet` quan tâm **nội dung logic được `equals` xem là bằng nhau**, không phải hai biến có tham chiếu đến cùng một đối tượng String hay không.

## 2. Vì sao `String` dùng tốt làm khóa và phần tử tập hợp?

`String` có **nội dung bất biến**, `equals` so sánh nội dung phân biệt hoa/thường, và `hashCode` nhất quán với `equals`. Vì thế, cùng nội dung được coi là cùng khóa/phần tử dù tạo bởi hai instance khác nhau:

~~~java
String first = new String("Java");
String other = new String("Java");
System.out.println(first == other);      // false
System.out.println(first.equals(other)); // true
System.out.println(first.hashCode() == other.hashCode()); // true
~~~

`HashMap` dùng hash để tìm vùng ứng viên rồi kiểm tra tính tương đương khóa; `HashSet` dùng nguyên tắc tương tự để loại phần tử lặp. **Cùng hash chưa chắc cùng giá trị**: `"FB"` và `"Ea"` có hash bằng nhau nhưng nội dung khác. Collection vẫn giữ chúng như hai khóa khác nhau. Công thức hash, va chạm và các điều kiện chi tiết của hợp đồng `equals–hashCode` được phân tích ở bài kế tiếp.

Điểm mạnh của `String` là key đã chèn không đổi nội dung. Nếu sau khi `put` bạn gán lại biến `key = key + "-admin"`, map vẫn giữ khóa cũ: phép gán đổi tham chiếu của biến, **không sửa String đã lưu**.

~~~java
String key = "user";
Map<String, Integer> map = new HashMap<>();
map.put(key, 7);
key = key + "-admin";
System.out.println(map.get("user")); // 7
System.out.println(map.get(key));    // null
~~~

Các đoạn là minh họa bên trong phương thức; chương trình độc lập có đầy đủ imports ở phần 8.

## 3. HashMap sử dụng String key như thế nào?

### 3.1. `put`, `get` và `containsKey`

Mô hình khái niệm: `HashMap` dùng hash code để xác định vị trí ứng viên, rồi sử dụng `equals` để phân biệt các khóa khác nhau nằm cùng vùng lưu trữ. Chi tiết bucket, resize và cấu trúc xử lý va chạm thuộc phần triển khai JDK; API bảo đảm hành vi theo hợp đồng Map.

~~~java
Map<String, Integer> scores = new HashMap<>();
String first = new String("Java");
String sameContent = new String("Java");

scores.put(first, 10);
System.out.println(scores.get(sameContent)); // 10

scores.put(sameContent, 20);
System.out.println(scores.size());          // 1
System.out.println(scores.get("Java"));     // 20
~~~

Lệnh `put` thứ hai **cập nhật giá trị của khóa tương đương** thay vì tạo entry mới. Java map không cho có hai khóa phân biệt theo `equals` cùng tồn tại. Trong khi đó `"Java"` và `"java"` mặc định là hai khóa khác nhau.

Các phương thức thường dùng:

| Thao tác | Ý nghĩa |
| --- | --- |
| `put(key, value)` | Thêm ánh xạ nếu chưa có khóa tương đương, hoặc thay giá trị gắn với khóa đó; trả về giá trị cũ hoặc `null` theo hợp đồng. |
| `get(key)` | Lấy giá trị của khóa tương đương; trả `null` nếu không có mapping, hoặc nếu mapping chứa giá trị `null`. |
| `containsKey(key)` | Kiểm tra tồn tại khóa, giúp phân biệt “không có khóa” với “khóa có giá trị null”. |
| `putIfAbsent(key, value)` | Thêm giá trị khi khóa chưa có mapping hoặc đang gắn với `null`; không phải lệnh cộng dồn. |
| `merge` / `computeIfAbsent` | Hỗ trợ cập nhật/tạo giá trị theo quy tắc, tiện cho đếm tần suất hay gom nhóm. |

Ví dụ đếm tần suất:

~~~java
Map<String, Integer> counts = new HashMap<>();
for (String token : new String[] {"java", "sql", "java"}) {
    counts.merge(token, 1, Integer::sum);
}
System.out.println(counts.get("java")); // 2
System.out.println(counts.get("sql"));  // 1
~~~

Việc dùng `String` làm key không có nghĩa map tự bỏ khoảng trắng, đổi chữ hoa hay chuẩn hóa Unicode: map lưu và tra cứu theo chuỗi **đúng như dữ liệu được đưa vào**.

### 3.2. Collision không phải key trùng

~~~java
Map<String, String> collisionMap = new HashMap<>();
collisionMap.put("FB", "first");
collisionMap.put("Ea", "second");

System.out.println(collisionMap.size());      // 2
System.out.println(collisionMap.get("FB"));   // first
System.out.println(collisionMap.get("Ea"));   // second
~~~

Hai chuỗi có hash code như nhau nhưng `equals` khác nhau, nên map chứa **hai** entry. Collision có thể làm lookup chậm hơn vì cần kiểm tra thêm, nhưng collision **không được gây mất dữ liệu** nếu các key tuân thủ `equals/hashCode`.

### 3.3. Null và giá trị null

`HashMap` cho phép **một** khóa `null` và cho phép value `null`. Đây là hành vi riêng của `HashMap`, không áp dụng mặc định cho mọi `Map`:

~~~java
Map<String, Integer> map = new HashMap<>();
map.put(null, 42);
map.put("pending", null);
System.out.println(map.get(null));             // 42
System.out.println(map.get("pending"));        // null
System.out.println(map.containsKey("pending"));// true
~~~

Trong hệ thống thật, nếu `null` không có nghĩa nghiệp vụ rõ ràng, có thể cấm nó tại ranh giới đầu vào để dữ liệu đơn giản hơn. `Map.of(...)` và một số implementation khác không chấp nhận khóa/value `null`.

## 4. HashSet sử dụng String element như thế nào?

### 4.1. Loại phần tử trùng theo equals/hashCode

`HashSet` là tập hợp không cho phép các phần tử bằng nhau theo `equals`. Nó được triển khai dựa trên `HashMap`, dùng phần tử làm khóa nội bộ và một giá trị giả làm value:

~~~java
Set<String> skills = new HashSet<>();

System.out.println(skills.add(new String("Java"))); // true
System.out.println(skills.add(new String("Java"))); // false
System.out.println(skills.add("java"));             // true
System.out.println(skills.size());                  // 2
System.out.println(skills.contains("Java"));        // true
~~~

`add` trả `true` khi tập hợp thay đổi và `false` nếu đã có phần tử tương đương. Hai đối tượng `String` khác tham chiếu nhưng cùng nội dung chỉ chiếm một phần tử; khác hoa/thường thì là hai phần tử mặc định.

Một tình huống đơn giản là lấy các nhãn duy nhất:

~~~java
List<String> input = List.of("red", "blue", "red", "green");
Set<String> unique = new HashSet<>(input);
System.out.println(unique.size()); // 3
~~~

**Không được dựa vào thứ tự** khi duyệt `HashSet`. Nếu yêu cầu giữ thứ tự xuất hiện đầu tiên, dùng `LinkedHashSet`; nếu cần thứ tự sắp xếp theo bộ so sánh, cân nhắc `TreeSet` với quy tắc so sánh phù hợp.

### 4.2. Một phần tử null, và khác biệt API

`HashSet` cho phép phần tử `null` (tối đa một lần), giống cách `HashMap` cho phép một khóa `null`:

~~~java
Set<String> set = new HashSet<>();
System.out.println(set.add(null)); // true
System.out.println(set.add(null)); // false
System.out.println(set.size());    // 1
~~~

Không suy rộng quy tắc này sang tất cả `Set`: ví dụ `Set.of(...)` không chấp nhận `null`.

## 5. Key nhìn giống nhau vẫn có thể là hai giá trị

`String.equals` so sánh chính xác nội dung UTF-16: khác kiểu chữ, khoảng trắng, dấu kết hợp hoặc ký tự vô hình đều có thể thành **khóa khác**. Ví dụ:

~~~java
Map<String, Integer> prices = new HashMap<>();
prices.put("Café", 120);
System.out.println(prices.get("café"));       // null: khác hoa/thường
System.out.println(prices.get("Cafe\u0301")); // null: Unicode khác biểu diễn
~~~

Không có thao tác chuẩn hóa ngầm khi `put`/`get`. Nếu định danh của ứng dụng thực sự **không phân biệt** một khác biệt nào đó, hãy tạo khóa canonical bằng **cùng một quy tắc** ở cả đường ghi và đường đọc. Ví dụ, mã kỹ thuật có thể dùng `strip().toLowerCase(Locale.ROOT)` nếu chính sách cho phép; muốn thống nhất biểu diễn Unicode chính tắc có thể cần `Normalizer.Form.NFC`. Riêng token, chữ ký hoặc mã phân biệt chữ hoa/thường **không được sửa tùy ý**. Bài “Chuẩn hóa khóa khác với bảo toàn văn bản hiển thị” trình bày thiết kế lưu riêng nhãn và khóa, xử lý va chạm sau chuẩn hóa.

Khi kết quả bất ngờ, hãy kiểm tra trực tiếp `equals` giữa khóa đã lưu và khóa tìm kiếm, kiểm tra khoảng trắng/ký tự vô hình, và dùng `containsKey` nếu value có thể là `null`. `intern` không giải quyết sự khác nhau của nội dung.

## 6. Vì sao String an toàn hơn khóa có thể thay đổi?

`HashMap`/`HashSet` yêu cầu giá trị tham gia `equals` và `hashCode` của một phần tử **không đổi trong thời gian nó còn nằm trong collection**. Nếu dùng một đối tượng tùy biến làm khóa rồi sửa trạng thái được dùng để tính hash, một lần `get` hoặc `remove` có thể không còn tìm được entry trước đó; tương tự `HashSet.contains` có thể thất bại. Vấn đề đến từ vi phạm hợp đồng của key, không phải một lỗi ngẫu nhiên của map.

`String` loại bỏ lớp rủi ro đó do nội dung bất biến. Nhưng biến tham chiếu vẫn có thể đổi sang string mới, điều này không ảnh hưởng đối tượng cũ đã lưu.

### 6.1. StringBuilder và các đối tượng mutable

`StringBuilder` có thể sửa nội dung bằng `append`/`replace` và không ghi đè `equals/hashCode` để so sánh theo nội dung như `String`. Vì vậy dùng một đối tượng `StringBuilder` làm khóa map thường không thể hiện đúng yêu cầu so sánh giá trị. Hãy tạo `String` snapshot nếu cần key theo nội dung:

~~~java
StringBuilder builder = new StringBuilder("part");
String safeKey = builder.toString();

Map<String, Integer> map = new HashMap<>();
map.put(safeKey, 5);
builder.append("-updated");

System.out.println(map.get("part")); // 5
~~~

Dữ liệu trong builder thay đổi nhưng `safeKey` vẫn có giá trị `"part"`. Nếu key phải bao gồm nhiều trường, ưu tiên một kiểu **immutable value object** có `equals/hashCode` nhất quán (ví dụ `record` chứa các thành phần cũng bất biến) hơn là nối chuỗi tùy tiện gây mơ hồ dấu phân cách.

### 6.2. Cùng một String cũng có thể bị “sai” vì chuẩn hóa đầu vào

Tính bất biến chỉ đảm bảo khóa **không biến đổi sau khi đưa vào collection**. Nó không bảo đảm tất cả dữ liệu cần được coi là một khóa đều đã cùng biểu diễn. Ví dụ nếu lúc `put` dùng `input.strip()` nhưng lúc `get` dùng input nguyên văn, ứng dụng vẫn truy vấn hụt. Hãy tập trung chính sách vào một hàm như `canonicalKey(raw)` và dùng nó ở cả hai đường.

## 7. Hiệu năng, thứ tự, luồng và lựa chọn collection

- **Độ phức tạp trung bình:** với hash phân tán tốt và kích thước hợp lý, `HashMap.get`/`put` và `HashSet.contains`/`add` thường đạt khoảng **O(1) trung bình**. Nếu collision cao hoặc resize, chi phí có thể tăng; tuyệt đối không bảo đảm O(1) cho mọi thao tác/mọi đầu vào.
- **Chi phí tính hash String:** tính `hashCode` ban đầu phụ thuộc chiều dài chuỗi; một số JDK ghi nhớ hash của `String` sau lần tính. Khi so sánh khóa trùng hash, `equals` có thể cần so sánh từng code unit đến khi gặp khác biệt.
- **Memory và intern:** không cần `intern()` để làm key. `HashMap` và `HashSet` đã xử lý cùng nội dung bằng `equals/hashCode`; dùng `intern` hàng loạt có thể tạo áp lực quản lý bộ nhớ và không giải quyết canonicalization nghiệp vụ.
- **Thứ tự duyệt:** `HashMap` và `HashSet` **không cam kết** thứ tự chèn hoặc thứ tự sắp xếp. Dùng `LinkedHashMap`/`LinkedHashSet` khi muốn giữ thứ tự chèn; dùng `TreeMap`/`TreeSet` khi thực sự cần sắp xếp (lưu ý comparator chi phối logic nhận diện khóa của cấu trúc sorted).
- **Đồng thời:** `String` bất biến **không làm cho `HashMap` hoặc `HashSet` trở thành thread-safe**. Nhiều luồng sửa collection cần đồng bộ hoặc một cấu trúc phù hợp như `ConcurrentHashMap` / `ConcurrentHashMap.newKeySet()`; các cấu trúc concurrent này có quy tắc riêng, ví dụ không cho key/value `null`.
- **Chọn dạng khóa:** nếu miền khóa là enum hữu hạn, `EnumMap`/`EnumSet` thường phù hợp hơn string key. Nếu miền có quy tắc chuẩn hóa hoặc nhiều thành phần, tạo kiểu khóa rõ ràng sẽ giảm lỗi hơn là trải khắp code các lệnh `trim` và `toLowerCase`.

## 8. Ví dụ Java 17+ độc lập, kết quả không phụ thuộc thứ tự duyệt

Lưu đoạn sau trong file **`StringHashCollectionsDemo.java`**:

~~~java
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StringHashCollectionsDemo {
    public static void main(String[] args) {
        String a = new String("Java");
        String b = new String("Java");

        System.out.println("sameReference=" + (a == b));
        System.out.println("sameContent=" + a.equals(b));
        System.out.println("sameHash=" + (a.hashCode() == b.hashCode()));

        Map<String, Integer> scores = new HashMap<>();
        scores.put(a, 10);
        scores.put(b, 20);
        scores.put("java", 30);
        System.out.println("mapSize=" + scores.size());
        System.out.println("JavaValue=" + scores.get("Java"));
        System.out.println("lowercaseValue=" + scores.get("java"));

        Set<String> unique = new HashSet<>();
        System.out.println("addFirst=" + unique.add(a));
        System.out.println("addEqual=" + unique.add(b));
        System.out.println("addDifferentCase=" + unique.add("java"));
        System.out.println("setSize=" + unique.size());

        System.out.println("hashCollision="
                + ("FB".hashCode() == "Ea".hashCode()));
        Map<String, String> collisions = new HashMap<>();
        collisions.put("FB", "first");
        collisions.put("Ea", "second");
        System.out.println("collisionMapSize=" + collisions.size());
        System.out.println("collisionValues="
                + collisions.get("FB") + "/" + collisions.get("Ea"));

        String original = "\u00E9";
        String decomposed = "e\u0301";
        System.out.println("unicodeEquals=" + original.equals(decomposed));
        String normalizedKey = Normalizer.normalize(
                original, Normalizer.Form.NFC);
        String normalizedLookup = Normalizer.normalize(
                decomposed, Normalizer.Form.NFC);
        Map<String, String> normalizedMap = new HashMap<>();
        normalizedMap.put(normalizedKey, "matched");
        System.out.println("unicodeLookup="
                + normalizedMap.get(normalizedLookup));

        StringBuilder builder = new StringBuilder("part");
        String snapshot = builder.toString();
        Map<String, Integer> stable = new HashMap<>();
        stable.put(snapshot, 5);
        builder.append("-updated");
        System.out.println("snapshotLookup=" + stable.get("part"));

        Map<String, Integer> nullable = new HashMap<>();
        nullable.put("pending", null);
        System.out.println("nullValue=" + (nullable.get("pending") == null));
        System.out.println("containsNullValueKey="
                + nullable.containsKey("pending"));
    }
}
~~~

Biên dịch và chạy từ thư mục chứa file:

~~~bash
javac --release 17 StringHashCollectionsDemo.java
java StringHashCollectionsDemo
~~~

**Stdout chính xác:**

~~~text
sameReference=false
sameContent=true
sameHash=true
mapSize=2
JavaValue=20
lowercaseValue=30
addFirst=true
addEqual=false
addDifferentCase=true
setSize=2
hashCollision=true
collisionMapSize=2
collisionValues=first/second
unicodeEquals=false
unicodeLookup=matched
snapshotLookup=5
nullValue=true
containsNullValueKey=true
~~~

Đoạn chương trình **không in trực tiếp toàn bộ `HashMap` hoặc `HashSet`**, vì thứ tự phần tử/entry khi duyệt không phải hợp đồng được đảm bảo. Các giá trị truy vấn theo key, kích thước, boolean của `add` và so sánh `equals` cho output có thể dự đoán chính xác.

## 9. Quy trình thiết kế String key đáng tin cậy

1. **Định nghĩa thế nào là cùng một khóa** theo nghiệp vụ: có phân biệt hoa/thường không, có bỏ khoảng trắng hay không, có chấp nhận Unicode tương đương chuẩn tắc không?
2. **Chọn canonicalization** (nếu cần) và áp dụng nhất quán **trước `put`/`add` và trước `get`/`contains`/`remove`**.
3. **Dùng `String` hoặc một kiểu khóa bất biến**, không dựa vào object identity hay trạng thái có thể thay đổi.
4. **Chọn collection theo nhu cầu**: `HashMap` cho key → value; `HashSet` cho membership/uniqueness; biến thể có thứ tự hoặc concurrent khi thực sự cần.
5. **Kiểm thử các cặp đầu vào đặc biệt**: cùng nội dung khác đối tượng, khác hoa/thường, space đầu/cuối, ký tự zero-width, precomposed/decomposed Unicode, hash collision và `null` khi hợp lệ.

Một API nên thể hiện chính sách rõ ràng. Ví dụ nếu định danh kỹ thuật cho phép loại whitespace đầu/cuối và không phân biệt hoa/thường:

~~~java
import java.util.Locale;

static String canonicalKey(String raw) {
    if (raw == null) {
        throw new IllegalArgumentException("key must not be null");
    }
    return raw.strip().toLowerCase(Locale.ROOT);
}
~~~

Đây là **minh họa chính sách cụ thể**, không phải quy tắc chuẩn cho mọi loại khóa. Không dùng cho giá trị mà dấu cách/chữ hoa được định nghĩa là có ý nghĩa. Nếu áp dụng chuẩn hóa Unicode, thêm `Normalizer.normalize(...)` vào chính sách sau khi quyết định thứ tự xử lý, rồi xác minh bằng dữ liệu đầu vào thực tế.

## 10. Câu hỏi tự kiểm tra

1. Vì sao hai biến tham chiếu tới **hai đối tượng** `new String("x")` vẫn chỉ tạo **một** entry trong `HashMap`? → Vì `equals` bằng nhau và `hashCode` tương ứng bằng nhau.
2. Hai chuỗi có cùng `hashCode` có bắt buộc là một key không? → **Không**. Va chạm vẫn có thể chứa hai khóa khác nhau, ví dụ `"FB"` và `"Ea"`.
3. `HashSet.add(s)` trả `false` nghĩa là gì? → Đã tồn tại phần tử tương đương theo `equals` (trong điều kiện bộ hợp đồng hash/equals của phần tử hợp lệ).
4. `String` bất biến có đảm bảo `HashMap` an toàn khi nhiều luồng đồng thời sửa không? → **Không**; đó là đặc tính của collection và cách đồng bộ.
5. Vì sao nhập `"é"` đôi khi truy vấn không khớp? → Có thể khác dạng biểu diễn Unicode; cần định nghĩa và áp dụng chuẩn hóa nhất quán nếu nghiệp vụ muốn coi chúng như nhau.
6. Vì sao `map.get(key) == null` chưa chắc nghĩa là khóa không tồn tại? → Một `HashMap` có thể gán value `null` cho key; kiểm tra `containsKey` nếu cần phân biệt.

**Ghi nhớ:** `String` thích hợp làm key/element của cấu trúc hash vì **bất biến** và triển khai `equals/hashCode` theo **nội dung chuỗi**. `HashMap` quản lý ánh xạ, `HashSet` quản lý tính duy nhất; cả hai tuân theo quy tắc so sánh chuỗi đúng như giá trị được cung cấp, không tự chuẩn hóa dữ liệu.

## Tài liệu tham khảo

- [Java SE 17 — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): `equals`, `hashCode`, `isEmpty`, `strip`.
- [Java SE 17 — Object](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): hợp đồng `equals` và `hashCode`.
- [Java SE 17 — HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): key tương đương, null, hiệu năng, thứ tự và vấn đề đồng thời.
- [Java SE 17 — HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html): membership, `add`, hash và thứ tự.
- [Java SE 17 — Normalizer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html): NFC/NFD và tương đương chuẩn tắc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-collections-002">Hợp đồng equals–hashCode và va chạm băm</a>

<details>
<summary>Xem chi tiết</summary>

# String trong collection: hợp đồng equals–hashCode và va chạm băm

Khi dùng **String** làm phần tử của **HashSet** hoặc làm khóa của **HashMap**, Java cần biết hai chuỗi có được xem là **cùng một giá trị** hay không. Hai phương thức **equals(Object)** và **hashCode()** phối hợp để trả lời câu hỏi đó. Hiểu đúng hợp đồng của chúng giúp tránh mất khóa, đếm sai phần tử và hiểu nhầm hiện tượng **va chạm băm (hash collision)**.

Một nguyên tắc cần nhớ ngay từ đầu: **cùng hashCode không có nghĩa là hai String bằng nhau; nhưng hai String bằng nhau theo equals bắt buộc có cùng hashCode.**

## 1. String.equals so sánh nội dung, không so sánh địa chỉ

String ghi đè phương thức **equals(Object)** để so sánh **nội dung ký tự**. Hai đối tượng String khác nhau vẫn được xem là bằng nhau nếu chứa cùng dãy đơn vị mã UTF-16 theo đúng thứ tự.

~~~java
String a = new String("java");
String b = "java";

System.out.println(a == b);      // false
System.out.println(a.equals(b));  // true
~~~

Giải thích:

- **a == b** kiểm tra hai tham chiếu có trỏ đến **cùng một đối tượng** hay không; đối tượng được tạo tường minh bằng **new String(...)** khác đối tượng chuỗi literal.
- **a.equals(b)** so sánh nội dung, nên trả về **true**.
- So sánh nội dung của String có **phân biệt chữ hoa/chữ thường**: **"Java".equals("java")** là **false**.
- **"java".equals(null)** là **false**. Nếu tham chiếu có thể null, có thể dùng **Objects.equals(a, b)** để kiểm tra hai giá trị null an toàn.

Cùng nội dung không nhất thiết cùng tham chiếu. Cơ chế *string pool* hoặc **intern()** không làm thay đổi quy tắc so sánh nội dung của equals; không dùng **==** để quyết định hai chuỗi là cùng khóa trong ứng dụng.

## 2. Hợp đồng equals–hashCode là gì?

Mọi lớp Java có thể dùng như khóa trong bộ sưu tập dựa trên băm cần tuân thủ hai nhóm quy tắc.

### 2.1. Những quy tắc cơ bản của equals

Đối với đối tượng khác null và khi trạng thái liên quan không thay đổi:

1. **Phản xạ:** **x.equals(x)** là true.
2. **Đối xứng:** **x.equals(y)** và **y.equals(x)** phải trả về cùng kết quả.
3. **Bắc cầu:** nếu x bằng y và y bằng z, x cũng phải bằng z.
4. **Nhất quán:** khi dữ liệu dùng để so sánh không đổi, gọi lại equals phải cho cùng kết quả.
5. **Với null:** **x.equals(null)** phải là false.

String đã triển khai các quy tắc này. Không cần viết lại equals cho String.

### 2.2. Những quy tắc của hashCode

Hợp đồng của **hashCode()** gồm:

- Trong cùng một lần thực thi ứng dụng, nếu dữ liệu tham gia vào equals không đổi, các lần gọi **hashCode()** trên cùng đối tượng phải trả lại cùng số nguyên.
- **Nếu x.equals(y) là true thì x.hashCode() và y.hashCode() phải bằng nhau.**
- **Nếu x.equals(y) là false, hashCode của chúng được phép bằng nhau.** Hai giá trị khác nhau **không bắt buộc** tạo hai mã băm khác nhau.

Có thể biểu diễn chính xác chiều suy luận:

~~~text
x.equals(y) == true   ⇒   x.hashCode() == y.hashCode()

x.hashCode() == y.hashCode()   ⇏   x.equals(y) == true
~~~

Vế thứ hai là lý do một collection dựa trên băm phải dùng **equals** để phân biệt những đối tượng có cùng mã băm. Nếu chỉ kiểm tra số hash, dữ liệu hợp lệ sẽ bị coi nhầm là trùng lặp.

## 3. String.hashCode hoạt động ra sao?

**String.hashCode()** tạo một giá trị kiểu **int** dựa trên nội dung String. Công thức về mặt ngữ nghĩa có dạng:

~~~text
h = 0
Lặp qua từng đơn vị mã UTF-16 c của chuỗi:
    h = 31 * h + c
Kết quả là h theo số nguyên int 32-bit của Java.
~~~

Với chuỗi rỗng, hashCode bằng **0**. Với String có nội dung bằng nhau, tính toán trên cùng dãy đơn vị mã UTF-16 luôn cho cùng hashCode. Số **31** là hệ số trong thuật toán hashCode do Java định nghĩa cho String.

Ví dụ:

~~~text
"Aa" = 31 × mã('A') + mã('a')
     = 31 × 65 + 97
     = 2112

"BB" = 31 × mã('B') + mã('B')
     = 31 × 66 + 66
     = 2112
~~~

Do giá trị hashCode chỉ là một số **int 32-bit** trong khi số lượng chuỗi có thể tạo ra là vô hạn, chắc chắn tồn tại những chuỗi khác nhau cho cùng mã băm. hashCode cũng có thể âm do tràn số nguyên 32-bit; đây không phải lỗi.

**hashCode không phải định danh duy nhất, không phải dấu vân tay an toàn và không phải hàm băm mật mã.** Không dùng nó để xác nhận dữ liệu không bị thay đổi, tạo ID duy nhất hoặc kiểm chứng tính toàn vẹn cần bảo mật.

## 4. Từ hash code đến phép tìm kiếm thực tế

`HashSet<String>` và `HashMap<String, V>` sử dụng **cả mã băm lẫn phép so sánh tương đương**. Mã băm giúp tìm nhóm ứng viên; `equals` xác nhận khóa chính xác. Do bảng có số bucket hữu hạn, ngay cả **hai hashCode khác nhau** cũng có thể rơi vào cùng bucket; đó là vấn đề triển khai được xử lý bên trong collection. Khi cùng hash mà khác `equals`, cả hai giá trị vẫn phải được phân biệt.

~~~java
Map<String, Integer> map = new HashMap<>();
map.put("Aa", 10);
map.put("BB", 20); // "Aa" và "BB" có cùng hashCode
System.out.println(map.size());      // 2
System.out.println(map.get("Aa"));   // 10
System.out.println(map.get("BB"));   // 20

Set<String> set = new HashSet<>();
System.out.println(set.add("Aa"));   // true
System.out.println(set.add("BB"));   // true
System.out.println(set.add(new String("Aa"))); // false
System.out.println(set.size());      // 2
~~~

`map.put(new String("Aa"), 30)` (nếu gọi tiếp) cập nhật entry `"Aa"` vì `equals` đúng, nhưng không đổi entry `"BB"`. Không phải gọi `hashCode` hoặc tự duyệt bucket trong ứng dụng; `Map`/`Set` đã quy định ngữ nghĩa này. Ví dụ hoàn chỉnh ở phần 6 kiểm tra cả trường hợp trùng nội dung và collision.

## 5. Va chạm băm thực tế: "Aa" và "BB"

Hai String **"Aa"** và **"BB"** có nội dung khác nhau nhưng **hashCode bằng nhau**. Đây là ví dụ va chạm băm ngắn, ổn định và dễ tự kiểm tra:

~~~java
String first = "Aa";
String second = "BB";

System.out.println(first.hashCode()); // 2112
System.out.println(second.hashCode()); // 2112
System.out.println(first.equals(second)); // false
~~~

**Va chạm không làm mất dữ liệu.** HashSet vẫn lưu được cả "Aa" lẫn "BB" vì equals trả về false. HashMap vẫn phân biệt và lấy đúng giá trị cho hai khóa.

Điều này không mâu thuẫn với hợp đồng equals–hashCode. Hợp đồng yêu cầu **bằng nhau thì cùng hashCode**, chứ không hề yêu cầu **cùng hashCode thì bằng nhau**.

Trong Java hiện đại, các bucket có nhiều phần tử va chạm được quản lý bằng cơ chế nội bộ thích hợp; HashMap có thể chuyển những bucket đủ đông sang cấu trúc cây khi thỏa các điều kiện triển khai. Đừng lập trình dựa vào thứ tự duyệt bucket hay giả định tất cả thao tác trong mọi tình huống đều có thời gian hằng số.

## 6. Chương trình Java 17+ hoàn chỉnh

Tạo file **StringHashContractDemo.java**:

~~~java
import java.util.HashMap;
import java.util.HashSet;

public class StringHashContractDemo {
    public static void main(String[] args) {
        String a = new String("java");
        String b = "java";

        System.out.println("same reference: " + (a == b));
        System.out.println("equal content: " + a.equals(b));
        System.out.println("same hash: "
                + (a.hashCode() == b.hashCode()));

        HashSet<String> languages = new HashSet<>();
        System.out.println("add first: " + languages.add(a));
        System.out.println("add equal: " + languages.add(b));
        System.out.println("size: " + languages.size());
        System.out.println("contains: " + languages.contains("java"));

        String first = "Aa";
        String second = "BB";

        System.out.println("collision hashes: "
                + first.hashCode() + " / " + second.hashCode());
        System.out.println("collision equals: "
                + first.equals(second));

        HashMap<String, Integer> counts = new HashMap<>();
        counts.put(first, 1);
        counts.put(second, 2);
        counts.put(new String("Aa"), 3);

        System.out.println("map size: " + counts.size());
        System.out.println("Aa: " + counts.get("Aa"));
        System.out.println("BB: " + counts.get("BB"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac StringHashContractDemo.java
java StringHashContractDemo
~~~

Stdout:

~~~text
same reference: false
equal content: true
same hash: true
add first: true
add equal: false
size: 1
contains: true
collision hashes: 2112 / 2112
collision equals: false
map size: 2
Aa: 3
BB: 2
~~~

Cách đọc kết quả:

- Hai đối tượng **a**, **b** khác tham chiếu, nhưng có nội dung và hashCode giống nhau.
- **HashSet** chỉ nhận phần tử đầu tiên vì lần thêm thứ hai bằng theo equals.
- Hai khóa **"Aa"** và **"BB"** có cùng hashCode nhưng không bằng nhau, nên HashMap vẫn có **hai** entry.
- Lần **put(new String("Aa"), 3)** cập nhật giá trị của khóa tương đương với **"Aa"**, nhưng không tác động đến **"BB"**.
- Ví dụ chỉ lấy dữ liệu bằng khóa tường minh; không dựa vào thứ tự duyệt **HashMap** hay **HashSet** vì thứ tự đó không được bảo đảm.

Đây là minh họa trực tiếp cho việc **hashCode quyết định hướng tra cứu còn equals quyết định sự tương đương của khóa** trong bộ sưu tập dựa trên băm.

## 7. Điều kiện ổn định của khóa sau khi chèn

Khóa nằm trong cấu trúc hash phải giữ nguyên các thông tin quyết định `equals/hashCode` khi còn được lưu. Với kiểu tự định nghĩa có trường mutable, việc sửa trường đó sau `put` có thể làm `get` không tìm thấy khóa dù entry vẫn tồn tại.

`String` tránh được rủi ro này vì nội dung bất biến. Gán `key = key.toUpperCase()` chỉ đổi biến tham chiếu sang một `String` khác; giá trị cũ trong map vẫn nguyên. Nếu cần khóa gồm nhiều thành phần, cân nhắc một value object bất biến có hợp đồng `equals/hashCode` đúng thay vì dùng khóa mutable.

## 8. Phân biệt HashSet, HashMap với các collection khác

Không phải collection nào cũng dùng hashCode để nhận biết trùng lặp:

| Collection | Cách xác định phần tử/khóa | Ảnh hưởng của String.hashCode |
| --- | --- | --- |
| **HashSet<String>** | Hash rồi so sánh equals khi cần | Dùng để tổ chức tra cứu |
| **HashMap<String, V>** | Hash và equals cho khóa | Dùng để tổ chức tra cứu |
| **ArrayList<String>** | **contains**, **indexOf** dựa trên equals | Không dùng hashCode cho các thao tác này |
| **TreeSet<String>** | **compareTo** hoặc Comparator để xác định thứ tự và trùng lặp | Không dùng hashCode để xác định phần tử trùng |
| **TreeMap<String, V>** | **compareTo** hoặc Comparator cho khóa | Không dùng hashCode để xác định khóa trùng |

Với String theo thứ tự tự nhiên, **compareTo** trả về 0 khi nội dung String bằng nhau theo equals. Tuy nhiên nếu truyền Comparator tùy chỉnh, ví dụ **String.CASE_INSENSITIVE_ORDER**, TreeSet có thể coi **"Java"** và **"java"** là cùng một phần tử dù **String.equals** của chúng trả về false. Đây là quy tắc do Comparator chọn, không phải lỗi của hashCode.

Nếu chỉ cần danh sách có thể chứa giá trị lặp, dùng List. Nếu cần tập giá trị duy nhất theo **String.equals**, dùng HashSet. Nếu cần bảng ánh xạ từ String sang giá trị, dùng HashMap. Nếu cần tập/ánh xạ có thứ tự theo Comparator, cân nhắc TreeSet/TreeMap.

## 9. Sự bằng nhau của khóa còn phụ thuộc chính sách dữ liệu

`String.equals` phân biệt hoa/thường, khoảng trắng và cách mã hóa Unicode. Vì vậy `"Java"` và `"java"` là hai khóa theo `HashSet`, mặc dù một `TreeSet` dùng `String.CASE_INSENSITIVE_ORDER` có thể coi chúng là cùng khóa do quy tắc comparator.

~~~java
Set<String> raw = new HashSet<>();
raw.add("Java");
raw.add("java");
System.out.println(raw.size()); // 2
~~~

Khi nghiệp vụ muốn hai biến thể thành một định danh, chuẩn hóa **trước khi lưu và tìm** theo chính sách rõ ràng. `toLowerCase(Locale.ROOT)` là một lựa chọn cho mã kỹ thuật, nhưng chưa bao trùm mọi quy tắc ngôn ngữ/Unicode. Chuẩn hóa dữ liệu không phải một thành phần của thuật toán hash và không chữa va chạm băm. Bài riêng về canonical key xử lý lựa chọn đó cùng việc bảo toàn nhãn hiển thị.

## 10. Những hiểu lầm thường gặp

### "Hash bằng nhau nghĩa là dữ liệu bằng nhau"

**Sai.** "Aa" và "BB" có hashCode 2112 nhưng equals false. Hash collision là điều được cho phép và phải được xử lý. Không viết logic kiểu:

~~~java
// SAI về ngữ nghĩa bằng nhau:
boolean same = a.hashCode() == b.hashCode();
~~~

Khi cần so nội dung, dùng **a.equals(b)** (nếu a chắc chắn không null) hoặc **Objects.equals(a, b)**.

### "Hai String khác đối tượng là hai khóa khác nhau"

**Sai đối với HashMap<String, ...> và HashSet<String>.** Chúng dựa vào equals, không dựa vào **==**. Một String mới chứa đúng nội dung cũ có thể truy vấn hoặc cập nhật entry đã tồn tại.

### "HashSet bỏ mất khóa khi có collision"

**Sai.** HashSet dùng equals để phân biệt những phần tử thực sự khác nhau. Collision có thể gây thêm chi phí tra cứu, nhưng không có nghĩa collection gộp hai String không bằng nhau.

### "String hashCode có thể dùng làm ID duy nhất"

**Sai.** Đầu ra 32-bit không thể đại diện duy nhất cho mọi chuỗi. HashCode không phải ID nghiệp vụ hay checksum bảo mật. Nếu cần ID, sử dụng cơ chế sinh ID hoặc định danh từ dữ liệu phù hợp yêu cầu.

### "Tôi có thể dùng == vì các literal nằm trong string pool"

**Không an toàn.** String lấy từ file, request, phép ghép, hoặc được tạo bằng **new** có thể mang tham chiếu khác dù nội dung bằng nhau. **intern()** là một cơ chế riêng, không nên dùng để thay thế việc so sánh nội dung.

### "HashMap/HashSet sẽ duyệt theo thứ tự thêm vào"

**Không có bảo đảm.** Cơ chế hash và collision không xác lập thứ tự duyệt ổn định. Khi cần giữ thứ tự chèn, cân nhắc **LinkedHashMap/LinkedHashSet**; khi cần sắp xếp theo thứ tự, cân nhắc **TreeMap/TreeSet** với thứ tự phù hợp.

### "Tất cả trường hợp tìm kiếm đều O(1)"

**Không có bảo đảm tuyệt đối.** Với phân bố hash phù hợp và kích thước bảng hợp lý, HashMap/HashSet có hiệu năng tra cứu trung bình rất tốt. Nếu có nhiều khóa rơi vào cùng bucket, thao tác phải xét nhiều ứng viên; Java có các tối ưu nội bộ cho trường hợp dày đặc, nhưng hiệu năng còn tùy dữ liệu và cấu trúc thực tế.

## 11. Cách tự kiểm tra khi gặp lỗi String trong collection

Khi **contains**, **get** hoặc **put** cho kết quả bất ngờ, thử kiểm tra theo trình tự:

1. In giá trị String theo cách thể hiện rõ whitespace, chữ hoa/thường và ký tự ẩn. **"java"** và **"java "** không bằng nhau.
2. Kiểm tra trực tiếp **equals** giữa hai giá trị và so sánh **hashCode** để minh họa. Nếu equals true mà hashCode khác ở hai đối tượng cùng lớp, lớp tự định nghĩa có thể vi phạm hợp đồng; riêng String chuẩn không xảy ra điều này.
3. Xác định collection đang dùng hợp đồng **equals/hashCode** hay **Comparator**. HashMap/HashSet khác TreeMap/TreeSet.
4. Với khóa kỹ thuật, xác định có chuẩn hóa đồng nhất ở lúc ghi và lúc tra cứu không.
5. Tránh kiểm tra lỗi bằng thứ tự duyệt HashMap vì đó không phải thứ tự được cam kết.
6. Kiểm tra giá trị null riêng nếu API cần phân biệt **không có khóa** với **khóa có giá trị null**. Với Map, **containsKey** có thể cho thông tin khác việc chỉ xem kết quả **get**.

Ví dụ nhỏ về sự khác biệt giữa **không có khóa** và **giá trị null**:

~~~java
Map<String, String> map = new HashMap<>();
map.put("present", null);

// map.get("present") == null và map.get("missing") == null
// nhưng map.containsKey("present") == true,
// còn map.containsKey("missing") == false.
~~~

Đây là vấn đề về ngữ nghĩa Map, không phải hash collision, nhưng thường bị nhầm khi debug việc tra cứu bằng String.

## 12. Tự đánh giá kiến thức

Cho ba chuỗi:

~~~java
String a = "Aa";
String b = "BB";
String c = new String("Aa");
~~~

Hãy dự đoán kết quả:

| Biểu thức | Kết quả | Lý do |
| --- | --- | --- |
| **a.hashCode() == b.hashCode()** | true | Cùng mã băm 2112 |
| **a.equals(b)** | false | Nội dung khác nhau |
| **a == c** | false | c là đối tượng tạo mới |
| **a.equals(c)** | true | Cùng nội dung "Aa" |
| **a.hashCode() == c.hashCode()** | true | equals true kéo theo hashCode bằng nhau |

Nếu thêm **a**, **b**, **c** vào một **HashSet<String>**, kích thước cuối là **2**: a và c được coi là cùng giá trị, còn b khác giá trị dù trùng hashCode. Nếu dùng làm khóa HashMap, **put(c, value)** sẽ cập nhật ánh xạ đang ứng với a; b vẫn là khóa riêng.

Hãy ghi nhớ đúng chiều suy luận: **equals bằng nhau ⇒ hashCode bằng nhau; hashCode bằng nhau ⇏ equals bằng nhau**. String đã bảo đảm hợp đồng, còn HashSet/HashMap đã được thiết kế để xử lý va chạm mà vẫn phân biệt các chuỗi thực sự khác nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-collections-003">So sánh theo nội dung thay vì dựa vào intern</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh `String` theo nội dung thay vì dựa vào `intern()` trong collection Java

## 1. Vấn đề thực tế

Hãy tưởng tượng ứng dụng nhận tên khóa `"java"` từ hai nguồn: một nơi viết trực tiếp trong source code, nơi khác đọc từ dữ liệu hoặc tạo chuỗi mới. Hai giá trị có nội dung giống hệt nhau, nhưng không nhất thiết là cùng **đối tượng `String`**.

~~~java
String a = "java";
String b = new String("java");

System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
~~~

Cả hai đều biểu thị bốn chữ cái `j`, `a`, `v`, `a`. Biểu thức `a == b` cho kết quả `false` vì hai biến tham chiếu đến **hai đối tượng khác nhau**. Biểu thức `a.equals(b)` cho kết quả `true` vì `String.equals` so sánh **nội dung chuỗi**.

Trong `Set<String>`, `Map<String, ...>` hay các phép loại bỏ giá trị trùng lặp, quy tắc nghiệp vụ thường là: **cùng nội dung thì được coi là cùng khóa hoặc cùng phần tử**. Vì thế cần dựa vào `equals()` và `hashCode()`, thay vì cố làm cho mọi chuỗi đều trỏ tới một đối tượng được `intern()`.

Một chương trình có thể "tình cờ chạy đúng" với `==` khi các giá trị đều là string literal, rồi thất bại khi dữ liệu đến từ file, mạng, cơ sở dữ liệu hoặc phép nối chuỗi ở runtime.

## 2. Phân biệt `==`, `equals()` và `intern()`

| Biểu thức / API | Điều nó kiểm tra hoặc thực hiện | Thích hợp khi |
| --- | --- | --- |
| `a == b` | Hai tham chiếu có trỏ tới cùng đối tượng hay không | Chủ ý kiểm tra **định danh đối tượng** |
| `a.equals(b)` | Hai `String` có cùng dãy UTF-16 code unit hay không | Kiểm tra **nội dung** chính xác, phân biệt chữ hoa/thường |
| `Objects.equals(a, b)` | So sánh nội dung an toàn khi một hoặc cả hai biến có thể `null` | So sánh giá trị nullable |
| `a.equalsIgnoreCase(b)` | So sánh không phân biệt hoa/thường theo quy tắc của Java | Khi nghiệp vụ xác định rõ cách so sánh này phù hợp |
| `a.intern()` | Trả về tham chiếu đại diện chuẩn (canonical) trong string pool cho nội dung của `a` | Trường hợp tối ưu hoặc kỹ thuật riêng có chủ đích |
| `a.hashCode()` | Tạo giá trị băm ổn định theo nội dung `String` | Collections dựa trên hash, như `HashSet` và `HashMap` |
| `a.compareTo(b)` | So sánh thứ tự từ điển theo các code unit UTF-16 | Sắp xếp theo **natural order** của `String` |

Điểm cần nhớ: `String.intern()` **không phải** phép kiểm tra hai chuỗi có bằng nhau hay không. Nó trả về một tham chiếu đại diện chuẩn, cho phép những chuỗi có nội dung bằng nhau chia sẻ một đối tượng sau khi intern. Nếu mục đích là **so sánh giá trị**, dùng `equals()` trực tiếp vừa rõ nghĩa vừa không phụ thuộc vào bước chuẩn hóa tham chiếu.

### `intern()` hoạt động như thế nào?

JVM duy trì một **string pool** cho các chuỗi được intern. String literal như `"java"` được intern. Khi gọi `someString.intern()`, kết quả là đối tượng đại diện chuẩn đang có cùng nội dung trong pool, hoặc một đối tượng trở thành đại diện cho nội dung đó nếu trước đó chưa tồn tại.

~~~java
String literal = "java";
String created = new String("java");

System.out.println(literal == created);          // false
System.out.println(literal == created.intern()); // true
System.out.println(created == created.intern()); // false
~~~

`created.intern()` **trả về một tham chiếu**; nó không thay nội dung và không biến chính đối tượng `created` thành string literal. Việc dùng `intern()` ở đây chỉ giải thích cơ chế, không phải giải pháp cần dùng cho so sánh trong collection.

Trường hợp `new String("java")` được tạo riêng là minh họa rõ ràng; với chuỗi được tối ưu hóa hoặc hằng biểu thức tại thời điểm biên dịch, không được suy luận máy móc rằng mọi biểu thức ghép chuỗi đều tạo đối tượng mới.

## 3. Kiểm chứng `intern()` không cần thiết cho hash collection

Hai instance mới chứa cùng chữ vẫn là **một** phần tử `HashSet` và **một** khóa `HashMap`. Không cần chuẩn hóa tham chiếu trước bằng `intern`:

~~~java
String a = new String("id-42");
String b = new String("id-42");

Set<String> set = new HashSet<>();
set.add(a);
set.add(b);
System.out.println(set.size()); // 1

Map<String, Integer> map = new HashMap<>();
map.put(a, 10);
map.put(b, 20);
System.out.println(map.size()); // 1
System.out.println(map.get("id-42")); // 20
~~~

`intern` chỉ trả về tham chiếu chuẩn trong string pool; nếu sau `intern` ta so sánh bằng `==` thì đang kiểm tra identity của **những tham chiếu đã intern**, chứ không thay đổi hợp đồng của `HashMap` hay `HashSet`. Hai nội dung khác nhau vẫn khác nhau; hai nội dung bằng nhau vẫn bằng nhau kể cả trước khi intern. Nếu cần bản đồ dùng **identity** thật sự, phải lựa chọn cấu trúc đặc biệt như `IdentityHashMap`, vốn có ngữ nghĩa khác `HashMap` (phần 6).

**Hash collision là vấn đề riêng**: `"Aa".hashCode() == "BB".hashCode()` nhưng `"Aa".equals("BB")` là `false`. Không so hai nội dung chỉ bằng hash; chi tiết hợp đồng được trình bày trong bài về va chạm băm.

## 5. Ví dụ Java 17+ hoàn chỉnh, có thể chạy

Lưu chương trình dưới tên `StringCollectionEqualityDemo.java`:

~~~java
import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class StringCollectionEqualityDemo {
    public static void main(String[] args) {
        String pooled = "java";
        String separate = new String("java");
        String rebuilt = new StringBuilder()
                .append("ja")
                .append("va")
                .toString();

        System.out.println("referenceEquals=" + (pooled == separate));
        System.out.println("valueEquals=" + pooled.equals(separate));
        System.out.println("internEquals=" +
                (pooled == separate.intern()));
        System.out.println("sameHash=" +
                (pooled.hashCode() == separate.hashCode()));

        Set<String> tags = new HashSet<>();
        tags.add(pooled);
        tags.add(separate);
        tags.add(rebuilt);

        System.out.println("hashSetSize=" + tags.size());
        System.out.println("hashSetContains=" +
                tags.contains(new String("java")));

        Map<String, Integer> scores = new HashMap<>();
        scores.put(new String("id-42"), 10);
        scores.put("id-42", 20);

        System.out.println("hashMapSize=" + scores.size());
        System.out.println("hashMapValue=" +
                scores.get(new String("id-42")));

        // Trường hợp chủ ý dùng identity thay vì content.
        Set<String> identities = Collections.newSetFromMap(
                new IdentityHashMap<String, Boolean>()
        );
        identities.add(new String("key"));
        identities.add(new String("key"));
        System.out.println("identitySetSize=" + identities.size());

        // Sorted Set có thể dựa trên Comparator.
        Set<String> sorted = new TreeSet<>(
                String.CASE_INSENSITIVE_ORDER
        );
        sorted.add("Lan");
        sorted.add("lan");
        System.out.println("treeSetSize=" + sorted.size());
        System.out.println("treeSetContains=" +
                sorted.contains("LAN"));

        System.out.println("equalsIgnoreCase=" +
                "Java".equalsIgnoreCase("JAVA"));
        System.out.println("nullSafe=" +
                Objects.equals(null, null));

        String composed = "\u00E9";
        String decomposed = "e\u0301";
        System.out.println("differentFormsEquals=" +
                composed.equals(decomposed));
        System.out.println("normalizedEquals=" +
                composed.equals(Normalizer.normalize(
                        decomposed, Normalizer.Form.NFC)));

        System.out.println("collisionHashes=" +
                ("Aa".hashCode() == "BB".hashCode()));
        System.out.println("collisionEquals=" +
                "Aa".equals("BB"));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên (lưu tệp bằng UTF-8):

~~~bash
javac -encoding UTF-8 StringCollectionEqualityDemo.java
java StringCollectionEqualityDemo
~~~

`stdout`:

~~~text
referenceEquals=false
valueEquals=true
internEquals=true
sameHash=true
hashSetSize=1
hashSetContains=true
hashMapSize=1
hashMapValue=20
identitySetSize=2
treeSetSize=1
treeSetContains=true
equalsIgnoreCase=true
nullSafe=true
differentFormsEquals=false
normalizedEquals=true
collisionHashes=true
collisionEquals=false
~~~

Giải thích kết quả quan trọng:

1. Ba chuỗi `pooled`, `separate`, `rebuilt` có cùng nội dung `java`; `==` chỉ phản ánh định danh, còn `equals` phản ánh giá trị.
2. `HashSet` chỉ lưu một phần tử; `HashMap` chỉ lưu một entry và giá trị cuối là `20`, dù các đối tượng khóa được tạo riêng.
3. `IdentityHashMap` cố ý dùng **`==`** để phân biệt các khóa. Hai đối tượng `new String("key")` khác nhau làm số phần tử trong set được xây từ identity map là **2**. Đây là hành vi theo thiết kế của collection đặc biệt này.
4. `TreeSet` dùng `String.CASE_INSENSITIVE_ORDER` nên coi `"Lan"` và `"lan"` tương đương theo comparator, dù `"Lan".equals("lan")` là `false`.
5. Hai cách biểu diễn chữ `é` không `equals` trước khi chuẩn hóa; chuẩn hóa `NFC` đưa chúng về cùng dạng.
6. Hai chuỗi `Aa` và `BB` cho cùng hash code, nhưng nội dung không giống nhau, minh họa lý do vẫn phải xét `equals`.

## 6. Ngoại lệ quan trọng: không phải collection nào cũng dùng `String.equals()`

### 6.1. `IdentityHashMap` dùng định danh đối tượng

`IdentityHashMap` được thiết kế để coi hai khóa là bằng nhau khi chúng cùng tham chiếu đến **một đối tượng**. Đó là cấu trúc phù hợp cho một số bài toán theo dõi đối tượng hoặc đồ thị định danh, nhưng **không phù hợp** nếu nghiệp vụ cần nhóm các khóa `String` theo nội dung.

Ví dụ:

~~~java
IdentityHashMap<String, Integer> map = new IdentityHashMap<>();
map.put(new String("user"), 1);
map.put(new String("user"), 2);

System.out.println(map.size()); // 2
~~~

Dùng `intern()` để làm cho `IdentityHashMap` hoạt động giống `HashMap` là cách thiết kế sai hướng: ta đã chọn một collection có **quan hệ bằng nhau khác**, sau đó cố can thiệp tham chiếu để ép nó cho kết quả mong muốn. Hãy chọn `HashMap` nếu cần content equality.

### 6.2. `TreeSet` và `TreeMap` dùng thứ tự so sánh

Các collection có sắp xếp dùng `Comparator.compare(a, b) == 0` hoặc `Comparable.compareTo` trả về `0` để quyết định hai khóa có tương đương trong tập thứ tự hay không.

Với thứ tự tự nhiên của `String`, `a.compareTo(b) == 0` có quan hệ nhất quán với `a.equals(b)`. Tuy nhiên, khi truyền một comparator không phân biệt chữ hoa/chữ thường:

~~~java
Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
names.add("Admin");
names.add("admin");

System.out.println(names.size()); // 1
System.out.println("Admin".equals("admin")); // false
~~~

Ở đây, `TreeSet` gom hai chuỗi theo **comparator**, khác với `HashSet` phân biệt chúng theo `equals/hashCode`. `String.CASE_INSENSITIVE_ORDER` không nhất quán với `String.equals` đối với những chuỗi khác nhau chỉ ở chữ hoa/thường; vì vậy cần cân nhắc hợp đồng `Set` khi chọn comparator và không giả định hai loại tập có cùng quy tắc trùng lặp.

Nếu hệ thống yêu cầu so sánh khóa không phân biệt chữ hoa/thường, nên xác định chiến lược nhất quán toàn hệ thống (ví dụ chuẩn hóa khóa trước khi đưa vào hash collection) và hiểu các quy tắc Unicode/locale liên quan.

## 7. `intern` không thay cho chuẩn hóa nội dung

`intern` **không** đổi chữ hoa/thường, bỏ khoảng trắng hay ghép Unicode về NFC. Do đó, hai giá trị `"Java"` và `"java"`, hoặc `"\u00E9"` với `"e\u0301"`, vẫn có thể là hai giá trị khác nhau dù mỗi bên đã `intern`. String pool gom tham chiếu khi **nội dung đã bằng nhau**, không biến nhiều cách viết thành một định danh nghiệp vụ.

Nếu sản phẩm muốn các cách viết được coi là một key, phải định nghĩa một hàm canonicalization phù hợp và dùng nó ở cả thao tác lưu/tìm; việc bảo toàn nhãn gốc cho hiển thị là trách nhiệm khác. `equalsIgnoreCase` cũng không làm `HashMap` tự đổi hợp đồng khóa. Bài chuyên về chuẩn hóa khóa trình bày chính sách, Unicode normalization và xử lý các biến thể va chạm.

## 8. `null` và chọn chiều gọi `equals()`

Khi giá trị có thể `null`, lời gọi như sau không an toàn:

~~~java
String input = null;
// input.equals("ok"); // Ném NullPointerException.
~~~

Các cách so sánh an toàn:

~~~java
import java.util.Objects;

String input = null;
System.out.println("ok".equals(input));       // false
System.out.println(Objects.equals(input, "ok")); // false
System.out.println(Objects.equals(null, null));  // true
~~~

`Objects.equals(a, b)` kiểm tra đồng nhất tham chiếu hoặc `equals` theo cách null-safe: hai giá trị `null` được coi là bằng nhau; một giá trị `null` còn giá trị kia không `null` là không bằng nhau.

`HashMap` và `HashSet` của Java cho phép `null` theo quy tắc riêng của chúng, nhưng điều đó không có nghĩa rằng ta có thể gọi phương thức `equals()` trên tham chiếu `null`. Đây là hai vấn đề khác nhau.

## 9. Các sai lầm thường gặp

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| `if (input == "admin")` để xét nội dung | Dữ liệu tạo ở runtime có thể bị nhận diện sai | `"admin".equals(input)` |
| Dùng `intern()` trước mọi thao tác `HashMap` | Thêm xử lý pool không cần cho content equality | Tin vào `String.equals/hashCode` và chọn collection đúng |
| So sánh chỉ bằng `hashCode()` | Hash collision gây kết luận bằng nhau sai | Dùng `equals()` |
| Dùng `IdentityHashMap` cho khóa chuỗi nghiệp vụ | Hai đối tượng cùng nội dung có thể thành hai khóa | Dùng `HashMap` |
| Nghĩ `HashSet` và `TreeSet` luôn xử lý trùng giống nhau | `TreeSet` có thể dùng comparator quy định quan hệ khác | Chọn và kiểm tra comparator rõ ràng |
| Nghĩ `equals` tự trim, không phân biệt hoa/thường, chuẩn hóa Unicode | Những khác biệt này được giữ nguyên | Chuẩn hóa theo yêu cầu nghiệp vụ trước khi lưu/tra |
| Gọi `nullable.equals(other)` | Có thể ném `NullPointerException` | `Objects.equals(nullable, other)` hoặc gọi `equals` trên literal |
| Dựa vào kết quả `==` của hai string literal để suy ra mọi chuỗi cùng nội dung là cùng đối tượng | Code chỉ đúng ngẫu nhiên do pooling | Dùng `equals` ở mọi nguồn dữ liệu |

### Checklist khi dùng `String` làm khóa collection

- Mục tiêu là **content equality** hay **reference identity**?
- Nếu là content equality, collection đã dùng `equals/hashCode` đúng chưa?
- Quy tắc phân biệt chữ hoa/thường, khoảng trắng, Unicode normalization đã được định nghĩa chưa?
- Input từ nhiều nguồn có được chuẩn hóa **cùng cách** trước khi `put`, `add`, `get`, `contains` không?
- Nếu dùng `TreeMap`/`TreeSet`, comparator có quan hệ bằng nhau mong muốn không?
- Các chuỗi nullable có được xử lý an toàn không?
- Test có dùng `new String(...)` hoặc chuỗi tạo lúc runtime để tránh phụ thuộc vô tình vào intern pool không?

**Kết luận:** Với `String` trong `HashSet`, `HashMap` và đa số tình huống so sánh giá trị, **hãy so sánh bằng `equals()`** và dựa vào `hashCode()` được cài đặt sẵn. `intern()` quản lý đối tượng đại diện trong string pool, không phải điều kiện để biết hai chuỗi có cùng nội dung. Dùng `==` chỉ khi thực sự cần **định danh tham chiếu**, và kiểm tra quy tắc comparator nếu collection có thứ tự.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-collections-004">Sắp xếp List&lt;String&gt; theo tự nhiên hoặc Comparator</a>

<details>
<summary>Xem chi tiết</summary>

# Sắp xếp List<String> theo thứ tự tự nhiên hoặc Comparator trong Java

## Mục tiêu bài học

Khi có danh sách chuỗi `List<String>`, bạn thường muốn hiển thị từ A đến Z, từ Z đến A, không phân biệt chữ hoa/chữ thường, theo độ dài hoặc theo quy tắc ngôn ngữ. Java cung cấp thứ tự **tự nhiên** của `String` và cơ chế `Comparator` để định nghĩa thứ tự tùy chỉnh.

Sau bài này, bạn có thể:

- Dùng `List.sort(null)`, `Collections.sort(list)` hoặc `list.sort(Comparator.naturalOrder())` để sắp xếp tăng dần theo thứ tự tự nhiên.
- Giải thích cách `String.compareTo` so sánh thứ tự từ điển theo đơn vị UTF-16 và vì sao kết quả không đồng nghĩa với thứ tự bảng chữ cái tiếng Việt.
- Viết `Comparator` cho thứ tự giảm dần, độ dài, nhiều tiêu chí và so sánh không phân biệt hoa thường.
- Phân biệt sắp xếp **trực tiếp trên list** với tạo **list mới** qua Stream.
- Xử lý phần tử `null`, list không hỗ trợ chỉnh sửa, yêu cầu sắp xếp ổn định và lưu ý khi dùng `Collator`.

## 1. Sắp xếp tự nhiên là gì?

`String` cài đặt `Comparable<String>`. Vì thế, mỗi chuỗi có thể so sánh với chuỗi khác qua:

~~~java
int result = first.compareTo(second);
~~~

Theo quy ước của `Comparable`:

- `result < 0`: `first` đứng trước `second`.
- `result == 0`: hai chuỗi có thứ tự bằng nhau theo phép so sánh đó.
- `result > 0`: `first` đứng sau `second`.

`String.compareTo` xét các đơn vị mã **UTF-16** theo thứ tự từ trái sang phải, sử dụng giá trị số của các `char` khác nhau đầu tiên; nếu mọi code unit đã xét bằng nhau nhưng một chuỗi ngắn hơn, chuỗi ngắn hơn đứng trước. Vì vậy, chữ hoa Latin `A` có mã nhỏ hơn chữ thường `a`: thứ tự tự nhiên **phân biệt chữ hoa/chữ thường**. Với ký tự có dấu hoặc các ký tự ngoài ASCII, kết quả là trật tự theo UTF-16, không mặc định là thứ tự từ điển của ngôn ngữ tự nhiên.

Ví dụ:

~~~java
System.out.println("Apple".compareTo("apple") < 0); // true
System.out.println("app".compareTo("apple") < 0);   // true
System.out.println("cat".compareTo("car") > 0);     // true
~~~

**Ghi nhớ:** “natural order” của `String` là thứ tự được định nghĩa bởi `String.compareTo`. Nó không tự loại dấu, không bỏ qua chữ hoa/chữ thường và không đọc quy tắc sắp xếp tiếng Việt hoặc tiếng Nhật.

## 2. Sắp xếp trực tiếp List<String>

Ba cách phổ biến dưới đây có cùng ý nghĩa sắp xếp tăng dần theo thứ tự tự nhiên khi list không có `null`:

~~~java
Collections.sort(list);
list.sort(null);
list.sort(Comparator.naturalOrder());
~~~

- `Collections.sort(list)` là API tiện ích quen thuộc; về bản chất ủy quyền cho việc sắp xếp list.
- `list.sort(null)` dùng thứ tự tự nhiên của phần tử.
- `list.sort(Comparator.naturalOrder())` biểu đạt rõ rằng comparator là thứ tự tự nhiên.

Cả ba cách **thay đổi thứ tự phần tử của chính list**. Chúng không tạo `List` mới và yêu cầu list có hỗ trợ thao tác `set`/sắp xếp vị trí. Không phải mọi triển khai `List` đều cho phép điều này.

### Ví dụ Java 17+ chạy độc lập

Lưu vào `NaturalSortDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NaturalSortDemo {
    public static void main(String[] args) {
        List<String> words = new ArrayList<>(
                List.of("banana", "Apple", "apple", "Banana", "Zebra", "áo"));

        words.sort(Comparator.naturalOrder());
        System.out.println(words);

        words.sort(Comparator.reverseOrder());
        System.out.println(words);
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 NaturalSortDemo.java
java NaturalSortDemo
~~~

Kết quả:

~~~text
[Apple, Banana, Zebra, apple, banana, áo]
[áo, banana, apple, Zebra, Banana, Apple]
~~~

Lý do: `A`, `B`, `Z` là các code unit Latin in hoa đứng trước `a`, `b`, còn chữ `á` (`U+00E1`) đứng sau `b` trong phép so sánh tự nhiên ở ví dụ này. `Comparator.reverseOrder()` đảo thứ tự tự nhiên. Với dữ liệu mang ý nghĩa ngôn ngữ, hãy xem phần `Collator` bên dưới.

**Lưu ý:** `List.of("a", "b")` trả về list không chỉnh sửa được. Vì thế, ví dụ trên phải bọc trong `new ArrayList<>(...)` trước khi gọi `sort`. Nếu sort trực tiếp `List.of(...)`, thao tác có thể ném `UnsupportedOperationException`.

## 3. Comparator hoạt động như thế nào?

`Comparator<String>` mô tả quy tắc so sánh hai chuỗi:

~~~java
Comparator<String> byLength = (left, right) ->
        Integer.compare(left.length(), right.length());
~~~

Giá trị trả về là âm, không hoặc dương với ý nghĩa tương tự `compareTo`. Trong Java hiện đại, bạn thường không cần viết lambda so sánh độ dài bằng tay:

~~~java
Comparator<String> byLength = Comparator.comparingInt(String::length);
~~~

`Comparator.comparingInt` đọc khóa kiểu `int` từ mỗi phần tử; ở đây khóa là `length()`. Comparator có thể được truyền cho `List.sort`, `Stream.sorted`, `Collections.sort` hoặc các API khác có nhận thứ tự so sánh.

**Không nên viết** `left.length() - right.length()` làm kết quả so sánh khi khóa có thể là số nguyên lớn tùy ý, vì phép trừ có thể tràn số. Dùng `Integer.compare` hoặc `comparingInt` giúp tránh lỗi này. Với `String.length()` thực tế không âm, `comparingInt` vẫn rõ ý định hơn.

### Sắp xếp theo độ dài, rồi theo chữ cái

Nếu chỉ dùng độ dài, những chuỗi bằng độ dài sẽ được coi là ngang nhau. `thenComparing` thêm tiêu chí phụ để kết quả dễ dự đoán:

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LengthSortDemo {
    public static void main(String[] args) {
        List<String> words = new ArrayList<>(
                List.of("pear", "fig", "apple", "kiwi", "plum", "fig"));

        Comparator<String> lengthThenNatural =
                Comparator.comparingInt(String::length)
                        .thenComparing(Comparator.naturalOrder());

        words.sort(lengthThenNatural);
        System.out.println(words);

        words.sort(Comparator.comparingInt(String::length)
                .reversed()
                .thenComparing(Comparator.naturalOrder()));
        System.out.println(words);
    }
}
~~~

Lưu `LengthSortDemo.java` rồi chạy:

~~~bash
javac -encoding UTF-8 LengthSortDemo.java
java LengthSortDemo
~~~

Kết quả:

~~~text
[fig, fig, kiwi, pear, plum, apple]
[apple, kiwi, pear, plum, fig, fig]
~~~

Giải thích:

1. Lần sort đầu: độ dài tăng dần, nếu bằng nhau thì so sánh tự nhiên. `fig` (3 ký tự) trước các từ 4 ký tự; `apple` (5 ký tự) cuối.
2. Lần sort sau: độ dài giảm dần; khi độ dài bằng nhau thì vẫn so sánh tự nhiên tăng dần, bởi `reversed()` được đặt **trước** `thenComparing(...)`.

Thứ tự gọi rất quan trọng:

~~~java
Comparator<String> a = Comparator
        .comparingInt(String::length)
        .reversed()
        .thenComparing(Comparator.naturalOrder());

// Khác với comparator dưới đây:
Comparator<String> b = Comparator
        .comparingInt(String::length)
        .thenComparing(Comparator.naturalOrder())
        .reversed();
~~~

Ở `a`, chỉ tiêu chí độ dài bị đảo rồi thêm tiêu chí phụ tăng dần; ở `b`, **toàn bộ comparator ghép** bị đảo, bao gồm cả tiêu chí phụ. Chọn cách viết dựa vào yêu cầu nghiệp vụ.

## 4. Sắp xếp List không phân biệt chữ hoa/thường

`String.CASE_INSENSITIVE_ORDER` dùng quy tắc so sánh không phân biệt hoa/thường của `String`:

~~~java
List<String> words = new ArrayList<>(
        List.of("apple", "Banana", "banana", "Apple", "pear"));
words.sort(String.CASE_INSENSITIVE_ORDER);
System.out.println(words); // [apple, Apple, Banana, banana, pear]
~~~

`List.sort` **ổn định**: khi comparator trả `0` cho `"apple"` và `"Apple"`, thứ tự giữa chúng vẫn giữ như lúc đầu. List không loại phần tử dù comparator coi chúng ngang nhau.

Khi cần thứ tự xác định giữa những biến thể cùng kiểu chữ, thêm tiêu chí phụ:

~~~java
words.sort(String.CASE_INSENSITIVE_ORDER
        .thenComparing(Comparator.naturalOrder()));
System.out.println(words); // [Apple, apple, Banana, banana, pear]
~~~

`CASE_INSENSITIVE_ORDER` không phải bộ sắp xếp ngôn ngữ học cho mọi locale. Và nếu dùng comparator này trong `TreeMap` hoặc `TreeSet`, kết quả `compare == 0` có thể **gộp các khóa khác nhau theo `equals`**. Bài tiếp theo về mối quan hệ Comparator/equals phân tích chính xác hành vi đó.

## 5. Sắp xếp String theo ngôn ngữ: Collator

Với giao diện hướng tới người đọc ở một ngôn ngữ cụ thể, thứ tự tự nhiên của `String` hoặc `CASE_INSENSITIVE_ORDER` có thể không phù hợp. Chẳng hạn khi sắp xếp danh sách từ, tên người hoặc nhãn hiển thị tiếng Việt, thứ tự chữ có dấu và quy tắc so sánh có thể khác với thứ tự mã UTF-16.

`java.text.Collator` là bộ so sánh ký tự phụ thuộc `Locale`:

~~~java
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class LocaleSortDemo {
    public static void main(String[] args) {
        Locale locale = Locale.forLanguageTag("vi-VN");
        Collator collator = Collator.getInstance(locale);

        List<String> words = new ArrayList<>(
                List.of("áo", "An", "ăn", "anh", "Ánh", "ao"));
        words.sort(collator);

        System.out.println(words);
    }
}
~~~

Lưu `LocaleSortDemo.java`; chạy `javac -encoding UTF-8 LocaleSortDemo.java` và `java LocaleSortDemo`. Mảng in ra được sắp xếp theo **quy tắc collation mà JDK/provider đang cung cấp cho `vi-VN`**. Thứ tự cụ thể có thể khác giữa phiên bản JDK hoặc dữ liệu locale, nên không cố ghi cứng một stdout có dấu cho ví dụ này. Hãy chạy và xác minh trên runtime mục tiêu của ứng dụng.

### Độ mạnh so sánh và tính nhất quán

`Collator` có các mức `PRIMARY`, `SECONDARY`, `TERTIARY` để phân biệt những đặc điểm ký tự khác nhau (chẳng hạn cơ sở chữ cái, dấu phụ, cách viết hoa), tùy dữ liệu collation của locale. Ví dụ:

~~~java
collator.setStrength(Collator.PRIMARY);
words.sort(collator);
~~~

Ở một số locale/cặp chuỗi, mức `PRIMARY` có thể coi nhiều biến thể là ngang nhau. Nên xác định chính sách sản phẩm về dấu và chữ hoa thay vì nghĩ `PRIMARY` luôn tương đương với “bỏ tất cả dấu”. Với nội dung quốc tế hóa, cần kiểm thử các cặp từ thực tế.

`Collator` có trạng thái cấu hình và không nên chỉnh cùng một đối tượng đang được dùng đồng thời mà không đồng bộ. Nếu muốn bảo đảm thứ tự hoàn toàn xác định khi hai chuỗi bằng nhau theo collation, bạn có thể bổ sung tiêu chí phụ tự nhiên:

~~~java
Comparator<String> localeOrder = (left, right) -> collator.compare(left, right);
words.sort(localeOrder.thenComparing(Comparator.naturalOrder()));
~~~

Lưu ý: tiêu chí phụ làm rõ cách xử lý trường hợp comparator collation trả về `0`; nó không thay thế bản thân quy tắc ngôn ngữ. Khi kết quả được lưu cố định, truyền qua nhiều hệ thống hoặc dùng làm khóa index, còn cần quản lý phiên bản/dữ liệu collation để tránh thay đổi thứ tự giữa các môi trường.

## 6. Null trong danh sách và đối tượng không chỉnh sửa được

`Comparator.naturalOrder()` và `String.compareTo` không tự hỗ trợ `null`. Khi list có phần tử `null`, sort theo tự nhiên có thể ném `NullPointerException`. Nếu quy tắc dữ liệu cho phép giá trị thiếu, hãy chọn nơi đặt nó:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class NullSortDemo {
    public static void main(String[] args) {
        List<String> words = new ArrayList<>(
                Arrays.asList("pear", null, "apple"));

        words.sort(Comparator.nullsLast(Comparator.naturalOrder()));
        System.out.println(words);

        words.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        System.out.println(words);
    }
}
~~~

Lưu `NullSortDemo.java`, biên dịch và chạy:

~~~bash
javac -encoding UTF-8 NullSortDemo.java
java NullSortDemo
~~~

Kết quả:

~~~text
[apple, pear, null]
[null, apple, pear]
~~~

`Comparator.nullsLast(inner)` đặt `null` sau mọi chuỗi khác; `nullsFirst(inner)` đặt lên đầu. Comparator bên trong vẫn quyết định thứ tự của các chuỗi khác `null`.

Ngoài ra, nên phân biệt:

| Khởi tạo hoặc nguồn | Có thể sort trực tiếp? | Ghi chú |
| --- | --- | --- |
| `new ArrayList<>(...)` | Có | Có thể thay đổi thứ tự phần tử |
| `Arrays.asList(...)` | Thường có | Kích thước cố định nhưng hỗ trợ thay phần tử theo vị trí qua `set` |
| `List.of(...)` | Không | List không chỉnh sửa được; gọi `sort` ném `UnsupportedOperationException` |
| `stream.toList()` (Java 16+) | Không | List trả về không chỉnh sửa được |
| `Collections.unmodifiableList(...)` | Không | View chặn thao tác thay đổi |

Khi chưa biết list nguồn có thể chỉnh sửa hay không, **tạo bản sao** trước:

~~~java
List<String> sorted = new ArrayList<>(source);
sorted.sort(Comparator.naturalOrder());
~~~

Cách này còn giữ nguyên thứ tự của `source`, giúp tránh gây tác dụng phụ lên dữ liệu ở nơi khác. Nếu nguồn có thể chứa `null`, hãy dùng một comparator có `nullsFirst`/`nullsLast` như trên.

## 7. Tạo danh sách đã sắp xếp mà không làm đổi list gốc

`List.sort` thay đổi trực tiếp dữ liệu nguồn. Nếu chỉ muốn tính một kết quả hiển thị mới, hãy tạo bản sao hoặc dùng Stream:

~~~java
import java.util.Comparator;
import java.util.List;

public class StreamSortedDemo {
    public static void main(String[] args) {
        List<String> original = List.of("pear", "apple", "banana");

        List<String> sorted = original.stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        System.out.println(original);
        System.out.println(sorted);
    }
}
~~~

Lưu `StreamSortedDemo.java` và chạy `javac -encoding UTF-8 StreamSortedDemo.java` rồi `java StreamSortedDemo`:

~~~text
[pear, apple, banana]
[apple, banana, pear]
~~~

`Stream.sorted` là phép trung gian, trả về một stream mới có thứ tự đã mô tả. `toList()` (Java 16+) là thao tác kết thúc tạo list kết quả và list ấy **không chỉnh sửa được**. `original` vẫn giữ nguyên.

So sánh nhanh:

- `list.sort(comparator)`: sắp xếp trực tiếp một list có thể chỉnh sửa; phương thức trả về `void`.
- `Collections.sort(list, comparator)`: cũng thay đổi list trực tiếp.
- `list.stream().sorted(comparator).toList()`: tạo list kết quả, không thay đổi nguồn, kết quả không chỉnh sửa được.
- `new ArrayList<>(list)` rồi `sort`: tạo list độc lập **và** có thể tiếp tục chỉnh sửa sau khi sort.

Hãy chọn dựa trên yêu cầu ownership/mutability của danh sách, không chỉ vì cú pháp nào ngắn.

## 8. Comparator đúng nghĩa cần tuân thủ các quy tắc nào?

Comparator nên có thứ tự nhất quán:

1. **Phản đối xứng về dấu:** dấu của `compare(a, b)` phải đối ngược dấu của `compare(b, a)`.
2. **Bắc cầu:** nếu `a` đứng trước `b` và `b` đứng trước `c` thì `a` phải đứng trước `c`.
3. **Nhất quán ở trường hợp bằng nhau:** nếu `compare(a, b) == 0`, việc so sánh `a` và `b` với một giá trị `c` phải cho quan hệ tương thích.
4. Tránh comparator có trạng thái thay đổi hoặc phụ thuộc thời điểm/ngẫu nhiên trong lúc sort.

Comparator không tuân thủ contract có thể tạo thứ tự khó đoán, và một số thuật toán sort có thể ném `IllegalArgumentException` khi phát hiện quan hệ so sánh vi phạm contract.

**Phân biệt `equals` với `compare == 0`:** `String.CASE_INSENSITIVE_ORDER.compare("Apple", "apple") == 0`, nhưng `"Apple".equals("apple")` là `false`. Đối với `List.sort`, hai phần tử vẫn được giữ lại; danh sách không tự xóa bản sao theo comparator. Nhưng nếu đem comparator đó vào `TreeSet` hoặc `TreeMap`, kết quả `compare == 0` có thể làm hai key được coi là cùng một key xét theo tập/bản đồ có thứ tự. Khi thiết kế comparator cho các collection đó, hãy cân nhắc độ nhất quán với `equals` và chính sách khóa nghiệp vụ.

### Tính ổn định và hiệu năng

`List.sort` yêu cầu sắp xếp **ổn định**: khi comparator xem hai phần tử bằng nhau, thứ tự tương đối ban đầu được giữ. Chi phí so sánh và số lần so sánh phụ thuộc độ dài danh sách, phân bố dữ liệu và các thao tác của comparator. Sắp xếp nói chung thường được phân tích theo mức `O(n log n)` phép so sánh cho trường hợp điển hình, nhưng việc so sánh chuỗi có thể còn phải duyệt nhiều ký tự để tìm điểm khác nhau.

Với dữ liệu lớn, tránh làm phép biến đổi tốn kém (ví dụ chuẩn hóa nhiều lần hoặc đọc dữ liệu ngoài) trong mỗi lời gọi comparator. Có thể tính khóa trước và sắp xếp theo khóa đã chuẩn bị nếu chi phí đó đáng kể. Không nên tối ưu sớm khi danh sách nhỏ và comparator đã dễ hiểu.

## 9. Các lỗi thường gặp

**Lỗi 1 — Tưởng tự nhiên tức là A–Z của tiếng Việt.** `compareTo` dựa trên UTF-16, không có ý thức về bộ chữ cái/ngôn ngữ. Dùng `Collator` với `Locale` thích hợp cho danh sách hiển thị theo quy tắc ngôn ngữ, rồi kiểm thử trên JDK thực tế.

**Lỗi 2 — Gọi sort trên List.of.** `List.of` không cho sửa nội dung. Tạo `new ArrayList<>(...)` hoặc dùng `stream().sorted(...).toList()`.

**Lỗi 3 — Có null nhưng dùng naturalOrder.** Nếu dữ liệu cho phép `null`, phải quyết định đặt null đầu/cuối bằng `Comparator.nullsFirst` hoặc `nullsLast`, hoặc loại bỏ trước khi sort nếu nghiệp vụ cho phép.

**Lỗi 4 — Nhầm thứ tự tự nhiên và không phân biệt hoa thường.** `Comparator.naturalOrder()` phân biệt `"Apple"` và `"apple"`; `String.CASE_INSENSITIVE_ORDER` thì có thể coi chúng bằng nhau. Khi cần sắp xếp có thứ tự phụ xác định, dùng `thenComparing(Comparator.naturalOrder())`.

**Lỗi 5 — Đặt reversed() sai vị trí.** `comparingInt(...).reversed().thenComparing(...)` đảo tiêu chí chính, còn `comparingInt(...).thenComparing(...).reversed()` đảo toàn bộ thứ tự đã ghép.

**Lỗi 6 — Dùng toLowerCase() không nêu rõ locale để tạo sort key.** Quy tắc đổi chữ hoa/thường có thể thay đổi theo locale; biến đổi có thể làm tăng số ký tự, và khóa đã đổi chữ không nhất thiết phản ánh collation đúng. Nếu mục tiêu là so sánh không phân biệt hoa thường đơn giản, dùng `CASE_INSENSITIVE_ORDER`; nếu theo ngôn ngữ, dùng `Collator` với locale cụ thể.

**Lỗi 7 — Dùng phép trừ để viết Comparator số.** Thay `a.length() - b.length()` bằng `Integer.compare(a.length(), b.length())` hoặc `Comparator.comparingInt(String::length)`.

**Lỗi 8 — Comparator không ổn định về mặt logic.** Tránh comparator trả giá trị khác nhau cho cùng cặp phần tử trong một lần sort; không dùng biến ngẫu nhiên hoặc dữ liệu có thể thay đổi bất chợt làm khóa so sánh.

**Lỗi 9 — Muốn giữ nguồn nhưng gọi List.sort.** `sort` thay đổi list đang có. Dùng bản sao hoặc `stream().sorted(...).toList()` khi cần kết quả độc lập.

**Lỗi 10 — Nhầm comparator với kiểm tra bằng nhau của Collection.** `List.sort` chỉ đổi thứ tự, không xóa phần tử trùng và không thay đổi `String.equals`. `TreeSet` và `TreeMap` thì dùng comparator để nhận diện khóa trùng theo trật tự so sánh.

## 10. Bài tập thực hành

Cho danh sách:

~~~java
List<String> names = new ArrayList<>(
        List.of("bob", "Amy", "ann", "BOB", "Joe"));
~~~

Hãy dự đoán kết quả sau ba phép sort độc lập, mỗi lần bắt đầu từ dữ liệu ban đầu (tạo list mới cho mỗi phép):

1. `names.sort(Comparator.naturalOrder())`.
2. `names.sort(String.CASE_INSENSITIVE_ORDER)`.
3. `names.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))`.

Chương trình tự kiểm tra:

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SortPractice {
    public static void main(String[] args) {
        List<String> original = List.of("bob", "Amy", "ann", "BOB", "Joe");

        List<String> natural = new ArrayList<>(original);
        natural.sort(Comparator.naturalOrder());
        System.out.println("Natural: " + natural);

        List<String> insensitive = new ArrayList<>(original);
        insensitive.sort(String.CASE_INSENSITIVE_ORDER);
        System.out.println("Ignore case: " + insensitive);

        List<String> byLength = new ArrayList<>(original);
        byLength.sort(Comparator.comparingInt(String::length)
                .thenComparing(Comparator.naturalOrder()));
        System.out.println("Length: " + byLength);
    }
}
~~~

Lưu thành `SortPractice.java` và chạy:

~~~bash
javac -encoding UTF-8 SortPractice.java
java SortPractice
~~~

Kết quả:

~~~text
Natural: [Amy, BOB, Joe, ann, bob]
Ignore case: [Amy, ann, bob, BOB, Joe]
Length: [Amy, BOB, Joe, ann, bob]
~~~

Ở ví dụ thứ ba, mọi tên đều dài ba code unit UTF-16, nên tiêu chí độ dài không phân biệt được phần tử nào; tiêu chí phụ tự nhiên quyết định kết quả, giống lần sort đầu.

**Thử mở rộng:** thêm `"Bình"`, `"Binh"` và `"Ánh"` rồi so sánh kết quả với `Comparator.naturalOrder()`, `String.CASE_INSENSITIVE_ORDER` và `Collator.getInstance(Locale.forLanguageTag("vi-VN"))`. Không suy diễn thứ tự tiếng Việt từ UTF-16; hãy xem kết quả trên runtime bạn dùng.

## Checklist khi áp dụng

- Danh sách có được phép chỉnh sửa không? Nếu không, tạo bản sao hoặc dùng `Stream.sorted`.
- Muốn thứ tự theo mã UTF-16, không phân biệt hoa/thường, theo độ dài hay theo ngôn ngữ? Chọn comparator phù hợp.
- Có `null` không? Quy định xử lý `nullsFirst`/`nullsLast` hoặc loại bỏ có chủ đích.
- Có nhiều tiêu chí không? Dùng `comparingInt`, `thenComparing` và đặt `reversed()` đúng chỗ.
- Có phần tử bằng nhau theo comparator không? Xác định có cần tiêu chí phụ, đồng thời tận dụng tính ổn định của `List.sort`.
- Kết quả có ảnh hưởng tới `TreeSet`/`TreeMap` hoặc phải nhất quán trên nhiều JDK/locale không? Kiểm tra semantics của comparator, `equals` và collation trước khi dùng làm hợp đồng dữ liệu.

## Tài liệu tham khảo chính thức

- [Java SE 17 — List.sort](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator))
- [Java SE 17 — Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)
- [Java SE 17 — String.compareTo và CASE_INSENSITIVE_ORDER](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [Java SE 17 — Collections.sort](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html)
- [Java SE 17 — Collator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Collator.html)
- [Java SE 17 — Stream.sorted](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#sorted(java.util.Comparator))


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-collections-005">Comparator không phân biệt hoa–thường và quan hệ với equals</a>

<details>
<summary>Xem chi tiết</summary>

# Comparator không phân biệt hoa–thường và quan hệ với equals khi lưu String trong Collection

## 1. Vấn đề và mục tiêu

Khi đưa các chuỗi Java vào collection, ta thường muốn sắp xếp **không phân biệt chữ hoa và chữ thường**. Java cung cấp sẵn `String.CASE_INSENSITIVE_ORDER`, một `Comparator<String>` cho mục đích đó. Nhưng một bẫy quan trọng xuất hiện khi dùng comparator này làm **quy tắc xác định khóa trùng nhau** trong `TreeSet` và `TreeMap`:

> **`compare(a, b) == 0` không đồng nghĩa với `a.equals(b)`.**

Ví dụ với `"Java"` và `"java"`:

- `"Java".equals("java")` là `false` vì `String.equals` so sánh nội dung có phân biệt hoa/thường.
- `"Java".equalsIgnoreCase("java")` là `true`.
- `String.CASE_INSENSITIVE_ORDER.compare("Java", "java")` là `0`: hai chuỗi tương đương **theo comparator**.
- `"Java".compareToIgnoreCase("java")` cũng là `0`.

Nếu chỉ sắp xếp để hiển thị, hành vi trên thường đúng mong muốn. Nếu dùng nó để quyết định **phần tử nào được phép tồn tại** trong tập hợp hoặc **khóa nào được coi là cùng một khóa**, hậu quả có thể là mất một biến thể chữ hoa/thường.

Bài học này làm rõ quan hệ giữa `Comparator`, `String.equals`, `equalsIgnoreCase` và cách các collection sử dụng chúng.

## 2. Các phép so sánh String cần phân biệt

| API | Kết quả/ý nghĩa | Có phân biệt hoa–thường? |
| --- | --- | --- |
| `a.equals(b)` | Hai chuỗi có nội dung giống hệt nhau theo `String.equals` | Có |
| `a.equalsIgnoreCase(b)` | Kiểm tra tương đương bỏ qua hoa–thường theo quy tắc của `String` | Không |
| `a.compareTo(b)` | So sánh thứ tự tự nhiên; trả số âm, 0 hoặc số dương | Có |
| `a.compareToIgnoreCase(b)` | So sánh thứ tự bỏ qua hoa–thường | Không |
| `String.CASE_INSENSITIVE_ORDER.compare(a,b)` | `Comparator<String>` tương ứng so sánh không phân biệt hoa–thường | Không |

`Comparator.compare(a,b)` trả giá trị **âm** nếu `a` đứng trước `b`, **0** nếu tương đương theo comparator, và **dương** nếu `a` đứng sau `b`. Khi kiểm thử, đừng phụ thuộc độ lớn cụ thể của số âm/dương; chỉ phụ thuộc vào **dấu của kết quả**.

Với `String.CASE_INSENSITIVE_ORDER`, kết quả tương thích với `compareToIgnoreCase`. Hai chuỗi khác nhau về chữ hoa/thường có thể so sánh bằng 0 dù `equals` trả về `false`. Đây gọi là comparator **không nhất quán với equals** (*inconsistent with equals*).

Điều quan trọng: `equalsIgnoreCase` là một phép thử riêng dành cho `String`; nó **không thay đổi** cách `String.equals` hay `hashCode` hoạt động. Chỉ vì truyền comparator cho một collection không có nghĩa là các loại collection khác cũng tự dùng quy tắc tương đương ấy.

## 3. Vì sao tính nhất quán với equals quan trọng?

Theo quy ước của Java, comparator được gọi là **nhất quán với equals** nếu, đối với mọi đối tượng thích hợp `a` và `b`:

~~~text
compare(a, b) == 0   khi và chỉ khi   a.equals(b) == true
~~~

Với hai chuỗi khác kiểu chữ `"Java"` và `"java"`, comparator bỏ qua hoa–thường vi phạm điều kiện này:

~~~text
String.equals:                  false
String.CASE_INSENSITIVE_ORDER:      0
~~~

Một comparator không nhất quán với equals **vẫn có thể sắp xếp hợp lệ** và được `TreeSet`/`TreeMap` chấp nhận. Nhưng `SortedSet`/`SortedMap` nói chung cần comparator nhất quán với equals để bảo đảm trọn vẹn kỳ vọng của hợp đồng `Set`/`Map`. Nếu không, tập hợp hoặc ánh xạ có thể cư xử khác `HashSet`/`HashMap` khi người dùng cho rằng mọi collection đều xác định trùng lặp giống `equals`.

**Điểm chính cần nhớ:** thuật toán của `TreeSet` và `TreeMap` dựa trên **`compare(...) == 0` để xác định khóa tương đương**. Còn `HashSet` và `HashMap` sử dụng `equals` kết hợp `hashCode` của khóa. Hai nhóm collection có thể chứa số phần tử khác nhau với cùng đầu vào.

## 4. TreeSet và HashSet khác nhau thế nào?

Đoạn sau là **mảnh mã minh họa** có thể đặt trong `main` sau khi import `java.util.*`:

~~~java
Set<String> hash = new HashSet<>();
hash.add("Java");
hash.add("java");
hash.add("JAVA");
// hash.size() == 3

Set<String> tree = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
tree.add("Java");
tree.add("java");
tree.add("JAVA");
// tree.size() == 1
// tree.contains("JAVA") == true
~~~

Với `HashSet`, ba chuỗi có nội dung khác nhau theo `equals` và được coi là ba phần tử khác nhau; `HashSet` không sắp xếp các phần tử.

Với `TreeSet`, ba chuỗi so sánh bằng 0 theo comparator không phân biệt hoa–thường, nên chỉ một phần tử đại diện được lưu. Thông thường phần tử được thêm thành công trước tiên là phần tử được giữ: nếu thêm `"Java"` đầu tiên, việc thêm `"java"` hay `"JAVA"` không thay nội dung chuỗi đang lưu. Phép `contains("JAVA")` trả về `true` vì nó sử dụng quy tắc tương đương của cây; điều này **không chứng minh** chuỗi `"JAVA"` thực sự được lưu như một đối tượng riêng.

Nếu yêu cầu nghiệp vụ là “danh sách từ khóa không phân biệt hoa thường, mỗi từ chỉ tồn tại một lần”, `TreeSet<>(String.CASE_INSENSITIVE_ORDER)` có thể đúng mong muốn. Nếu cần lưu **cả hai biến thể** `"Java"` và `"java"`, comparator đó không phù hợp để làm duy nhất khóa.

## 5. TreeMap và HashMap: khóa tương đương có thể ghi đè giá trị

Tương tự với `TreeMap`:

~~~java
TreeMap<String, Integer> map =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

map.put("Java", 1);
map.put("java", 2);

// map.size() == 1
// map.get("JAVA") == 2
// map.firstKey().equals("Java") == true
~~~

Lần `put` thứ hai không sinh một khóa độc lập vì comparator nói rằng `"Java"` và `"java"` tương đương. Giá trị gắn với khóa đã có được **thay từ 1 thành 2**. Trong trường hợp này, đối tượng/chuỗi khóa được giữ trong cây vẫn là `"Java"`, tức chính khóa của lần thêm đầu. Phép tra cứu bằng `"JAVA"` vẫn lấy được giá trị vì `TreeMap` cũng dùng comparator để tìm kiếm.

Ngược lại, với `HashMap<String,Integer>`, các khóa `"Java"` và `"java"` khác nhau theo `equals`; có thể có hai entry và tra cứu `get("JAVA")` sẽ trả `null` nếu không có entry trùng đúng `"JAVA"`.

**Hệ quả thiết kế:** trước khi sử dụng `TreeMap`/`TreeSet`, hãy xác định rõ “trùng khóa” được định nghĩa bằng **`String.equals`** hay **so sánh không phân biệt hoa–thường**. Đây là lựa chọn về **quy tắc định danh**, không chỉ về thứ tự hiển thị.

## 6. Code Java 17+ đầy đủ để kiểm chứng

Tạo tệp **CaseInsensitiveComparatorDemo.java**:

~~~java
import java.util.*;

public class CaseInsensitiveComparatorDemo {
    private static int passed;

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        passed++;
    }

    public static void main(String[] args) {
        Comparator<String> insensitive = String.CASE_INSENSITIVE_ORDER;
        String first = "Java";
        String second = "java";

        check(insensitive.compare(first, second) == 0, "compare");
        check(first.compareToIgnoreCase(second) == 0, "compareToIgnoreCase");
        check(first.equalsIgnoreCase(second), "equalsIgnoreCase");
        check(!first.equals(second), "equals distinguishes case");

        Set<String> hash = new HashSet<>();
        hash.add("Java");
        hash.add("java");
        hash.add("JAVA");
        check(hash.size() == 3, "HashSet uses equals/hashCode");

        NavigableSet<String> folded = new TreeSet<>(insensitive);
        folded.add("Java");
        folded.add("java");
        folded.add("JAVA");
        check(folded.size() == 1, "TreeSet comparator equality");
        check(folded.contains("JAVA"), "TreeSet contains by comparator");

        NavigableMap<String, Integer> treeMap = new TreeMap<>(insensitive);
        treeMap.put("Java", 1);
        treeMap.put("java", 2);
        check(treeMap.size() == 1, "TreeMap key equivalence");
        check(treeMap.get("JAVA") == 2, "TreeMap lookup");
        check(treeMap.firstKey().equals("Java"), "original key retained");

        Comparator<String> insensitiveThenExact =
                String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
        TreeSet<String> distinct = new TreeSet<>(insensitiveThenExact);
        distinct.add("Java");
        distinct.add("java");
        distinct.add("JAVA");
        check(distinct.size() == 3, "tie breaker keeps distinct keys");
        check(distinct.toString().equals("[JAVA, Java, java]"),
                "tie breaker ordering");
        check(insensitiveThenExact.compare(first, second) != 0,
                "not comparator-equal if !equals");

        List<String> orderedList = new ArrayList<>(List.of("b", "A", "a", "B"));
        orderedList.sort(insensitive);
        check(orderedList.equals(List.of("A", "a", "b", "B")),
                "List.sort stable with ties");

        check(insensitive.compare("I", "\u0131") == 0,
                "Unicode case matching differs from locale conventions");

        System.out.println("equals=" + first.equals(second));
        System.out.println("ignoreCaseCompare=" + insensitive.compare(first, second));
        System.out.println("HashSet.size=" + hash.size());
        System.out.println("TreeSet.size=" + folded.size());
        System.out.println("TreeMap.size=" + treeMap.size());
        System.out.println("TreeMap.get(JAVA)=" + treeMap.get("JAVA"));
        System.out.println("TieBreakerSet=" + distinct);
        System.out.println("SortedList=" + orderedList);
        System.out.println("PASS: " + passed + " checks");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CaseInsensitiveComparatorDemo.java
java CaseInsensitiveComparatorDemo
~~~

Đầu ra chính xác:

~~~text
equals=false
ignoreCaseCompare=0
HashSet.size=3
TreeSet.size=1
TreeMap.size=1
TreeMap.get(JAVA)=2
TieBreakerSet=[JAVA, Java, java]
SortedList=[A, a, b, B]
PASS: 15 checks
~~~

Chương trình kiểm tra 15 giả định bằng `check`; nếu kết quả khác mong đợi, nó ném `AssertionError` và không in dòng cuối `PASS`. Việc kiểm tra `equals`, `compareToIgnoreCase` và comparator cạnh nhau giúp thấy rõ các phép so sánh thực sự khác nhau ở đâu.

`List.sort` ở ví dụ còn kiểm tra **tính ổn định (stable sort)**: chuỗi `"A"` và `"a"` so sánh bằng 0 với comparator nên giữ thứ tự tương đối như trong danh sách đầu. Tương tự, `"b"` và `"B"` giữ thứ tự tương đối. `List.sort` dùng comparator để sắp xếp **nhưng không loại phần tử trùng theo comparator**.

## 7. Cách sắp xếp không phân biệt hoa–thường mà vẫn giữ tất cả biến thể

Nếu cần ưu tiên nhóm các chuỗi theo chữ hoa/thường nhưng vẫn cho phép từng chuỗi **khác nhau theo `equals`** tồn tại trong `TreeSet`, thêm một tiêu chí so sánh phụ (*tie-breaker*):

~~~java
Comparator<String> byCaseInsensitiveThenExact =
        String.CASE_INSENSITIVE_ORDER
              .thenComparing(Comparator.naturalOrder());

TreeSet<String> set = new TreeSet<>(byCaseInsensitiveThenExact);
set.add("Java");
set.add("java");
set.add("JAVA");
// 3 phần tử theo thứ tự: [JAVA, Java, java]
~~~

Quy tắc hoạt động:

1. So sánh **không phân biệt hoa–thường** trước để nhóm các chuỗi cùng cách viết khi bỏ qua hoa/thường.
2. Khi kết quả bước một bằng 0, so sánh tiếp theo **thứ tự tự nhiên của String** (có phân biệt hoa–thường).
3. Cuối cùng, kết quả bằng 0 **chỉ khi hai String có cùng nội dung theo equals**. Vì vậy comparator kết hợp này nhất quán với `String.equals`.

Nhờ tiêu chí thứ hai, `"JAVA"`, `"Java"`, `"java"` đều được giữ trong `TreeSet` dù nằm gần nhau trong thứ tự sắp xếp. Đây là lựa chọn phù hợp nếu **sắp xếp bỏ qua hoa/thường** là nhu cầu hiển thị nhưng **duy nhất khóa theo equals** vẫn phải được duy trì.

Nếu cần bảo toàn **thứ tự chèn**, hãy dùng collection có tính chất tương ứng (ví dụ `LinkedHashSet`); không nên chọn `TreeSet` chỉ vì nó có thể nhận comparator.

## 8. Lựa chọn khác: sắp xếp List không làm biến mất chuỗi

Với `List`, có thể sắp xếp không phân biệt hoa/thường mà **giữ tất cả phần tử**, kể cả khi comparator trả 0:

~~~java
List<String> names = new ArrayList<>(
        List.of("b", "A", "a", "B"));
names.sort(String.CASE_INSENSITIVE_ORDER);
// [A, a, b, B]
~~~

`List.sort` thực hiện sắp xếp ổn định, nên các phần tử bằng nhau theo comparator giữ thứ tự tương đối; không có bước “deduplicate”. Vì thế khi yêu cầu là **chỉ sắp xếp**, một `List` có thể là cấu trúc phù hợp hơn `TreeSet` hoặc `TreeMap`.

Khi cần vừa **bỏ trùng** vừa sắp xếp, hãy định nghĩa hai yêu cầu riêng: bỏ trùng theo `equals` hay theo cách bỏ qua hoa/thường? Sắp xếp bằng comparator nào? Có thể cần một cấu trúc lưu khóa và một danh sách hiển thị riêng.

## 9. Unicode, ngôn ngữ và giới hạn của “không phân biệt hoa–thường”

`String.CASE_INSENSITIVE_ORDER` là quy tắc so sánh của `String` và **không sử dụng locale của người dùng** để xác định thứ tự ngôn ngữ tự nhiên. Ví dụ với chữ `I` ASCII và chữ `ı` (Latin small dotless i, U+0131), Java có thể coi chúng là tương đương khi bỏ qua hoa/thường:

~~~java
boolean same =
        String.CASE_INSENSITIVE_ORDER.compare("I", "\u0131") == 0;
// same == true
~~~

Điều này **không có nghĩa** comparator tạo ra thứ tự từ điển đúng cho mọi ngôn ngữ. Với danh sách tên người, tiêu đề hoặc từ vựng cần sắp xếp theo quy tắc ngôn ngữ cụ thể, hãy xem xét `java.text.Collator` được tạo cho một `Locale` phù hợp, và kiểm thử các quy tắc chữ cái/dấu của ngôn ngữ mục tiêu. `Collator` cũng có thể coi nhiều chuỗi khác nhau theo `equals` là tương đương; khi dùng trong `TreeSet`/`TreeMap` vẫn cần quyết định rõ ý nghĩa của `compare == 0`.

Không nên mặc định thay comparator bằng cách đưa toàn bộ chuỗi qua `toLowerCase()` hoặc `toUpperCase()` mà không chọn locale và không kiểm tra Unicode. Các phép chuyển đổi chữ hoa/thường có thể phụ thuộc ngôn ngữ, có thể làm thay đổi độ dài chuỗi và không nhất thiết tạo đúng cùng quan hệ tương đương với `equalsIgnoreCase`. Nếu mục tiêu chỉ là thứ tự bỏ qua hoa/thường theo Java, dùng comparator sẵn có sẽ trực tiếp và rõ ràng hơn.

## 10. Những lỗi thường gặp

1. **Nghĩ `compare(...) == 0` luôn tương đương với `equals`:** sai với `String.CASE_INSENSITIVE_ORDER`. Kiểm thử cả hai phép so sánh.
2. **Chuyển từ `HashSet` sang `TreeSet` nhưng không xét quy tắc trùng:** có thể làm biến mất các biến thể khác nhau chỉ về chữ hoa/thường.
3. **Cho rằng `TreeMap.put` với khóa khác kiểu chữ luôn thêm entry:** nó có thể ghi đè giá trị của entry cũ.
4. **Nhìn thấy `TreeSet.contains("JAVA") == true` rồi nghĩ phần tử `"JAVA"` đã được thêm:** cây tra cứu theo comparator; nó có thể chỉ đang chứa `"Java"`.
5. **Thêm tie-breaker khi nghiệp vụ thật sự muốn bỏ trùng không phân biệt hoa/thường:** tie-breaker sẽ khiến `"Java"` và `"java"` cùng tồn tại, trái với mong muốn loại trùng.
6. **Cho rằng comparator không phân biệt hoa/thường đồng nghĩa với thứ tự đúng cho mọi locale:** cần kiểm thử Unicode và cân nhắc `Collator` khi yêu cầu về ngôn ngữ quan trọng.
7. **Dùng `hashCode` làm tie-breaker cho khóa String:** giá trị băm có thể va chạm, không bảo đảm `compare == 0` đúng khi và chỉ khi `equals`; dùng `String.compareTo`/`naturalOrder` làm tiêu chí phụ.

## 11. Bài tập kiểm tra hiểu biết

**Bài 1.** `new TreeSet<>(String.CASE_INSENSITIVE_ORDER)` lần lượt nhận `"A"`, `"a"`, `"B"`. Tập có bao nhiêu phần tử?

Đáp án: **2**. `"A"` và `"a"` tương đương theo comparator nên chỉ có một đại diện; `"B"` khác với chúng.

**Bài 2.** `new HashSet<String>()` lần lượt nhận `"A"` và `"a"`. Kích thước là bao nhiêu?

Đáp án: **2** vì `String.equals` phân biệt hoa/thường.

**Bài 3.** Tại sao comparator `String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())` giữ được cả `"A"` và `"a"` trong `TreeSet`?

Đáp án: khi so sánh không phân biệt hoa/thường hòa nhau, tiêu chí thứ hai so sánh có phân biệt hoa/thường và cho kết quả khác 0. Hai chuỗi không bị coi là cùng một khóa.

**Bài 4.** Khi nào nên dùng comparator không phân biệt hoa/thường **không có tie-breaker**?

Đáp án: khi **chính nghiệp vụ quy định** mọi biến thể hoa/thường của một chuỗi là **cùng một khóa**. Khi chỉ cần thứ tự hiển thị, nên sắp xếp `List` hoặc thêm tie-breaker tùy cấu trúc dữ liệu.

## 12. Ghi nhớ

- `String.equals` và `String.hashCode` xác định tương đương **phân biệt hoa/thường** cho `HashSet` và `HashMap`.
- `String.CASE_INSENSITIVE_ORDER` và `compareToIgnoreCase` tạo quan hệ tương đương **không phân biệt hoa/thường**; `compare == 0` có thể xuất hiện với hai chuỗi `equals == false`.
- `TreeSet`/`TreeMap` dùng comparator để quyết định khóa trùng, nên lựa chọn comparator ảnh hưởng trực tiếp đến **dữ liệu được giữ**.
- Nếu cần **sắp xếp** không phân biệt hoa/thường và **giữ mọi String khác nhau theo equals**, dùng comparator chính bỏ qua hoa/thường rồi `thenComparing(Comparator.naturalOrder())`.
- Nếu cần thứ tự theo ngôn ngữ, đánh giá `Collator` theo locale và tiếp tục xem xét tính nhất quán với `equals`.

Hãy quyết định rõ **khái niệm “hai String là cùng một khóa”** trước khi chọn cấu trúc collection và comparator. Đây là yếu tố quyết định tính đúng đắn của chương trình, không đơn thuần là cách sắp xếp chữ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-collections-006">Chuẩn hóa khóa khác với bảo toàn văn bản hiển thị</a>

<details>
<summary>Xem chi tiết</summary>

# Chuẩn hóa khóa khác với bảo toàn văn bản hiển thị trong Java Collections

## 1. Vấn đề: cùng cách tìm kiếm, khác cách trình bày

Giả sử ứng dụng lưu tên một sản phẩm, thẻ nội dung hoặc một mục từ điển trong `Map<String, ...>`. Người dùng nhập `"  Café  "`, sau đó tìm bằng `"café"`, `"CAFÉ"` hay chuỗi Unicode `"Cafe\u0301"` (chữ `e` rồi dấu sắc kết hợp). Ứng dụng có thể muốn tất cả lần tra cứu này trỏ đến **cùng mục**, nhưng khi hiển thị lại muốn giữ nguyên tên người dùng đã đặt, gồm chữ hoa, dấu, khoảng trắng hoặc cách viết thương hiệu.

Đó là hai yêu cầu khác nhau:

- **Khóa tra cứu đã chuẩn hóa (normalized/canonical key)**: biểu diễn được định nghĩa thống nhất để so sánh, tìm kiếm, nhóm hoặc phát hiện trùng lặp.
- **Văn bản hiển thị (display text)**: chuỗi gốc hoặc phiên bản đã được người dùng duyệt để hiển thị lại đúng ý nghĩa và hình thức mong muốn.

**Không nên thay văn bản hiển thị bằng khóa đã chuẩn hóa.** Phép chuẩn hóa có thể làm mất thông tin không khôi phục được: chẳng hạn chữ hoa/thường, dấu cách đầu/cuối, hoặc một số phân biệt ký tự nếu chính sách chuẩn hóa quá mạnh.

Ngữ cảnh của bài là **dùng `String` làm khóa trong các collection Java**, đặc biệt `HashMap`, `HashSet` và `TreeMap`. Tùy bài toán, không phải dữ liệu nào cũng nên được chuẩn hóa: tên hiển thị, tên tài khoản, mã sản phẩm, mã định danh và mật khẩu có chính sách khác nhau.

## 2. `String.equals()` và khóa trong `HashMap`

`String.equals()` so sánh nội dung các chuỗi theo chính xác các đơn vị mã UTF-16. `HashMap<String, V>` dựa vào `hashCode()` và `equals()` của khóa khi thêm hoặc tra cứu phần tử; bản thân `HashMap` **không tự bỏ khoảng trắng, chuyển chữ thường hoặc chuẩn hóa Unicode**.

```java
import java.util.HashMap;
import java.util.Map;

Map<String, Integer> prices = new HashMap<>();
prices.put("Café", 100);

System.out.println(prices.get("Café"));   // 100
System.out.println(prices.get("café"));   // null
System.out.println(prices.get("Cafe\u0301")); // null
```

`"Café"` ở ví dụ thứ nhất chứa ký tự `é` dựng sẵn; `"Cafe\u0301"` chứa `e` và dấu sắc kết hợp. Chúng có thể trông giống nhau khi render nhưng không nhất thiết `equals()` nhau. Chuỗi chữ thường `"café"` cũng khác `"Café"`.

Tương tự, `HashSet<String>` có thể giữ nhiều chuỗi nhìn giống nhau vì từng chuỗi vẫn là phần tử khác nhau theo `equals()`. Muốn các biến thể được xem là cùng khóa thì phải **định nghĩa và áp dụng một quy tắc chuẩn hóa** trước khi thao tác với collection.

## 3. Các tầng chuẩn hóa: chọn theo nhu cầu

### 3.1. Khoảng trắng ở đầu/cuối

`String.strip()` (Java 11+) bỏ các ký tự đầu/cuối mà `Character.isWhitespace` xem là khoảng trắng. `trim()` dùng tiêu chí cũ hơn (`char <= U+0020`), nên hai phương thức không tương đương với mọi ký tự Unicode.

```java
String raw = "  Java  ";
String keyPart = raw.strip();

System.out.println(raw.length());     // 8
System.out.println(keyPart.length()); // 4
```

Không phải mọi loại dấu cách Unicode đều được `strip()` loại bỏ; ví dụ non-breaking space (`U+00A0`) cần chính sách riêng nếu dữ liệu của bạn xem nó là khoảng trắng biên. Không nên tự động xóa **mọi** dấu cách trong chuỗi vì `"New York"` và `"NewYork"` có thể mang ý nghĩa khác nhau.

### 3.2. Chữ hoa/thường

Với khóa tìm kiếm cần hành vi nhất quán, `toLowerCase(Locale.ROOT)` thường phù hợp hơn `toLowerCase()`, vì phiên bản không truyền `Locale` dùng locale mặc định của quá trình chạy và có thể thay đổi kết quả giữa các môi trường.

```java
import java.util.Locale;

String keyPart = "JAVA".toLowerCase(Locale.ROOT);
System.out.println(keyPart); // java
```

`Locale.ROOT` giúp có một quy tắc chung, nhưng **chuyển chữ thường chưa phải Unicode case folding đầy đủ**. Đừng mặc định rằng mọi cặp chuỗi mà người dùng coi là tương đương ở các ngôn ngữ đều trở thành cùng khóa. Chẳng hạn xử lý các chữ cái liên quan đến `I/i` trong tiếng Thổ Nhĩ Kỳ có quy tắc ngôn ngữ riêng. Nếu ứng dụng cần phân loại từ vựng theo ngôn ngữ, phải xác định và kiểm thử chính sách theo ngôn ngữ đó.

### 3.3. Unicode normalization: NFC và NFKC

`java.text.Normalizer` cung cấp các dạng chuẩn hóa Unicode, đặc biệt:

| Dạng | Ý nghĩa tiêu biểu | Khi nào cân nhắc |
| --- | --- | --- |
| `Normalizer.Form.NFC` | Chuẩn hóa **tương đương chính tắc** và ưu tiên dạng ký tự kết hợp sẵn | Phù hợp khi muốn thống nhất các cách mã hóa Unicode tương đương mà vẫn giữ nhiều phân biệt có ý nghĩa |
| `Normalizer.Form.NFD` | Tách thành dạng phân rã chính tắc | Có ích cho một số bước xử lý ký tự có dấu; không phải thao tác mặc định để làm khóa |
| `Normalizer.Form.NFKC` | Chuẩn hóa **tương đương tương thích** rồi kết hợp | Chỉ dùng khi nghiệp vụ chủ ý coi một số dạng trình bày hoặc ký tự tương thích là cùng nhau |
| `Normalizer.Form.NFKD` | Phân rã tương thích | Dùng cho mục đích chuyên biệt; có thể làm mất các phân biệt về cách biểu diễn |

NFC thống nhất hai cách biểu diễn chữ `é`:

```java
import java.text.Normalizer;

String composed = "\u00E9";   // é: một code point
String decomposed = "e\u0301"; // e và dấu sắc kết hợp

System.out.println(composed.equals(decomposed)); // false
System.out.println(
        Normalizer.normalize(composed, Normalizer.Form.NFC)
                .equals(Normalizer.normalize(decomposed, Normalizer.Form.NFC))
); // true
```

**NFC không xóa dấu.** Theo chính sách NFC, `"cafe"` và `"café"` vẫn khác nhau. NFKC có thể gộp thêm một số biểu diễn tương thích (chẳng hạn ký tự toàn chiều rộng với ký tự ASCII tương ứng), nên không được coi là lựa chọn luôn an toàn cho tên riêng, mã nhận diện hoặc nội dung người dùng.

Việc bỏ dấu hoàn toàn là **một chính sách khác**, có thể tạo va chạm không mong muốn giữa các từ hoặc tên khác nghĩa. Không trộn chính sách đó vào NFC nếu không có yêu cầu rõ ràng.

## 4. Một hàm chuẩn hóa khóa có chủ đích

Ví dụ sau chọn chính sách cụ thể cho một mục danh mục được tìm kiếm không phân biệt chữ hoa/thường và chấp nhận Unicode tương đương chính tắc:

1. Bỏ khoảng trắng đầu/cuối theo `strip()`.
2. Chuyển sang chữ thường theo `Locale.ROOT`.
3. Chuẩn hóa NFC **sau** bước chuyển chữ thường, bảo đảm dạng kết quả cuối cùng được chuẩn hóa chính tắc.

```java
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

static String keyOf(String input) {
    Objects.requireNonNull(input, "input");
    String stripped = input.strip();
    String lower = stripped.toLowerCase(Locale.ROOT);
    return Normalizer.normalize(lower, Normalizer.Form.NFC);
}
```

Đây là **đoạn phương thức minh họa**, đặt bên trong một lớp Java. Nó không phải quy tắc bắt buộc cho tất cả hệ thống. Mỗi hệ thống cần quyết định riêng liệu có được bỏ khoảng trắng, bỏ phân biệt chữ hoa/thường, xử lý tương đương Unicode hoặc chấp nhận khóa rỗng hay không.

Một vài tính chất quan trọng:

- `keyOf("  Café ")` và `keyOf("cafe\u0301")` cho cùng khóa theo chính sách trên.
- `keyOf("Cafe")` và `keyOf("Café")` **khác nhau** vì NFC không xóa dấu.
- Hàm không chỉnh sửa `input` vì `String` bất biến; nó trả về một chuỗi đại diện dùng trong cấu trúc dữ liệu.
- Giá trị `null` không là khóa hợp lệ ở ví dụ: `Objects.requireNonNull` từ chối rõ ràng thay vì tạo khóa đặc biệt ngoài ý muốn.
- Sau `strip()`, chuỗi có thể trở thành `""`. Nếu khóa rỗng không hợp lệ cho nghiệp vụ, hãy kiểm tra và trả lỗi trước khi lưu.

Cần **áp dụng đúng cùng hàm** cho cả thao tác ghi, tìm kiếm, cập nhật và xóa. Nếu chỉ chuẩn hóa khi `put` nhưng không chuẩn hóa khi `get`, việc tra cứu vẫn thất bại.

## 5. Thiết kế dữ liệu: lưu khóa và văn bản hiển thị riêng

Một mô hình đơn giản sử dụng `Map<String, Item>`:

```java
record Item(String displayName) {}

Map<String, Item> items = new HashMap<>();

String submittedName = "  Cafe\u0301  ";
String lookupKey = keyOf(submittedName);

items.put(lookupKey, new Item(submittedName));
Item found = items.get(keyOf("CAF\u00C9"));
```

Trong ví dụ này:

- **Khóa trong `Map`** là kết quả `keyOf(submittedName)`; nó dùng cho so khớp.
- **Giá trị `Item.displayName`** chứa đúng `submittedName` ban đầu, kể cả hai dấu cách mỗi bên và dạng Unicode phân rã.
- Khi hiển thị, lấy `found.displayName()`, không dùng chuỗi khóa từ `Map` làm nhãn.
- Nếu muốn loại bỏ dấu cách *trên giao diện*, hãy xác định yêu cầu đó riêng cho dữ liệu hiển thị; không suy diễn từ phép chuẩn hóa khóa.

Trong một ứng dụng lớn hơn, có thể dùng `record Item(String id, String displayName, ...)` với **ID ổn định** riêng. Chuỗi chuẩn hóa phù hợp làm khóa chỉ mục tìm kiếm hoặc nhóm; không nhất thiết nên là ID vĩnh viễn của đối tượng.

### Khi có nhiều đầu vào cùng khóa

Ví dụ `"  Café  "` và `"cafe\u0301"` có cùng khóa đã chuẩn hóa. Nếu dùng:

```java
items.put(keyOf(submittedName), new Item(submittedName));
```

cho nhiều lượt thêm, lần thêm sau có thể **ghi đè giá trị** ở cùng khóa. `HashMap.put` trả về giá trị cũ (nếu có), nhưng nếu bỏ qua giá trị trả về thì rất dễ mất nhãn hiển thị cũ.

Một số chiến lược rõ ràng hơn:

- **Khóa phải duy nhất:** dùng `putIfAbsent` hoặc kiểm tra va chạm trong tầng lưu trữ và báo cho người dùng khi tên đã tồn tại.
- **Nhiều mục có thể cùng khóa tìm kiếm:** dùng `Map<String, List<Item>>` hoặc chỉ mục đảo để một khóa trả về nhiều đối tượng.
- **Mục có ID riêng:** lưu bản ghi theo ID, còn khóa đã chuẩn hóa chỉ để tìm danh sách ID; tên hiển thị không phải nguồn nhận diện duy nhất.

Đối với hệ thống nhiều luồng hoặc có cơ sở dữ liệu, phép kiểm tra trước rồi ghi sau **không tự bảo đảm tính nguyên tử**. `HashMap` không thread-safe cho cập nhật đồng thời. `putIfAbsent` trên `HashMap` hữu ích trong đoạn mã một luồng nhưng không thay thế giao dịch/unique constraint của cơ sở dữ liệu hoặc cấu trúc đồng thời phù hợp.

## 6. Ví dụ Java 17+ có thể biên dịch và chạy

Tạo tệp `KeyNormalizationDemo.java`:

```java
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class KeyNormalizationDemo {

    record Item(String displayName) {}

    static String keyOf(String input) {
        Objects.requireNonNull(input, "input");
        return Normalizer.normalize(
                input.strip().toLowerCase(Locale.ROOT),
                Normalizer.Form.NFC
        );
    }

    public static void main(String[] args) {
        // Cafe + dấu sắc kết hợp; có hai khoảng trắng mỗi bên.
        String original = "  Cafe\u0301  ";
        String query = "CAF\u00C9";

        Map<String, Item> items = new HashMap<>();
        items.put(keyOf(original), new Item(original));

        System.out.println(
                "Same key: " + keyOf(original).equals(keyOf(query))
        );
        System.out.println(
                "Found: " + (items.get(keyOf(query)) != null)
        );
        System.out.println(
                "Display preserved: "
                        + items.get(keyOf(query)).displayName().equals(original)
        );

        Item existing = items.putIfAbsent(
                keyOf(query), new Item(query)
        );
        System.out.println("Collision: " + (existing != null));
        System.out.println("Size: " + items.size());
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac -encoding UTF-8 KeyNormalizationDemo.java
java KeyNormalizationDemo
```

Kết quả chính xác:

```text
Same key: true
Found: true
Display preserved: true
Collision: true
Size: 1
```

**Vì sao?** `original` có khoảng trắng và dạng Unicode phân rã; `query` viết hoa và dùng `É` dựng sẵn. Sau `strip()`, `toLowerCase(Locale.ROOT)` và `Normalizer.Form.NFC`, hai khóa bằng nhau. `Map.get` tìm được giá trị từ khóa chuẩn hóa; trong khi `displayName` vẫn bằng đúng chuỗi gốc, không bị đổi chữ, cắt khoảng trắng hoặc chuyển dạng Unicode. `putIfAbsent` phát hiện đã có một mục ở khóa đó và không thay giá trị, nên kích thước `Map` vẫn là `1`.

Ở đây `record` được khai báo lồng trong lớp; cú pháp hoạt động trong Java 17+.

## 7. `HashSet`, `TreeMap` và comparator

### `HashSet<String>`

Với tập hợp cần coi các biến thể là cùng một phần tử, lưu **khóa chuẩn hóa**:

```java
Set<String> keys = new HashSet<>();
keys.add(keyOf("  Café "));
keys.add(keyOf("cafe\u0301"));

System.out.println(keys.size()); // 1
```

Nếu cần hiển thị các nhãn do người dùng nhập, đừng lấy trực tiếp các phần tử của tập `keys` làm nội dung hiển thị. Hãy lưu chúng ở cấu trúc khác hoặc gắn vào đối tượng dữ liệu.

### `TreeMap<String, V>` và `TreeSet<String>`

Các collection dạng cây dùng `compareTo` hoặc `Comparator` để xác định **thứ tự và khi nào khóa được coi là bằng nhau** (khi `compare(a,b) == 0`). Điều này khác với cơ chế `equals/hashCode` của `HashMap/HashSet`.

Ví dụ:

```java
Map<String, Integer> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
sorted.put("Java", 1);
sorted.put("JAVA", 2);

System.out.println(sorted.size()); // 1
System.out.println(sorted.get("java")); // 2
```

Comparator trên so sánh không phân biệt hoa/thường theo quy tắc của Java, nhưng **không thay thế một chính sách chuẩn hóa đầy đủ**: nó không tự loại bỏ khoảng trắng biên, và không bảo đảm tương đương Unicode chính tắc giống NFC. Đừng cho rằng một `TreeMap` với comparator bất kỳ có cùng quy tắc đồng nhất khóa như `HashMap` lưu các khóa đã chuẩn hóa.

Nếu muốn dùng cùng một quy tắc giữa các collection, thường dễ hiểu nhất là **lưu và so sánh chính các khóa được tạo bởi `keyOf`**. Khi thiết kế comparator tùy chỉnh, phải bảo đảm tính nhất quán, phản xạ và bắc cầu của quan hệ sắp xếp; comparator coi hai khóa là bằng nhau có thể làm collection chỉ giữ một trong hai mục.

## 8. Khi không nên chuẩn hóa khóa theo cách này

Định nghĩa `keyOf` ở trên phù hợp với ví dụ **tìm kiếm tên trong danh mục**; nó không phải bộ quy tắc dùng cho mọi `String`.

| Dữ liệu | Hướng xử lý cần cân nhắc |
| --- | --- |
| Nhãn hiển thị, tên thương hiệu, tên tác phẩm | Bảo toàn nội dung đã chấp nhận; nếu cần tìm kiếm, lưu thêm khóa chỉ mục |
| Mã định danh phân biệt hoa/thường, token bí mật, chữ ký, dữ liệu cần kiểm chứng từng byte | Không tự bỏ dấu cách, hạ chữ, chuẩn hóa Unicode; thực hiện đúng đặc tả của hệ thống |
| Tên đăng nhập mà sản phẩm quy định không phân biệt chữ hoa/thường | Xác định chính sách từ lúc tạo tài khoản; áp dụng ở đăng ký, đăng nhập, thay đổi tên và kiểm tra trùng |
| Tìm kiếm tiếng Việt không phân biệt dấu | Cần đặc tả riêng cho bỏ dấu, chữ `đ`, chuẩn hóa khoảng trắng và xử lý va chạm; NFC tự nó không bỏ dấu |
| Tìm kiếm theo ngôn ngữ tự nhiên | Cân nhắc `Collator` với locale phù hợp, full-text search hoặc chỉ mục chuyên dụng; không mặc định lowercase luôn đúng |
| Dữ liệu Unicode có ký tự dễ gây nhầm hình dạng | NFC không giải quyết toàn bộ vấn đề ký tự trông giống nhau (confusables); cần thêm kiểm tra phù hợp nếu đây là rủi ro nghiệp vụ |

**Cẩn thận với chuẩn hóa mất dữ liệu.** `strip()` bỏ khoảng trắng ở hai đầu; `toLowerCase(Locale.ROOT)` bỏ phân biệt kiểu chữ; `NFKC` và bỏ dấu có thể gộp thêm các đầu vào khác nhau. Nếu hai mục phân biệt có ý nghĩa nghiệp vụ rơi vào cùng khóa thì phải xử lý va chạm có chủ đích, không tùy tiện ghi đè hay tự gộp bản ghi.

Đặc biệt, xác định chính sách **trước khi có dữ liệu thật**. Nếu sau này sửa hàm chuẩn hóa, khóa cũ trong bộ nhớ, tệp, cache hoặc cơ sở dữ liệu có thể không khớp khóa mới. Hãy thiết kế việc tính lại chỉ mục và giải quyết va chạm khi cần migration; với hệ thống lưu trữ lâu dài có thể ghi phiên bản chính sách để hỗ trợ chuyển đổi an toàn.

## 9. Những lỗi thường gặp

1. **Chỉ chuẩn hóa lúc lưu.** `map.put(keyOf(name), item)` nhưng `map.get(userInput)` khiến các biến thể không tìm thấy. Phải dùng `map.get(keyOf(userInput))`.
2. **Dùng khóa làm văn bản hiển thị.** Một nhãn do người dùng đặt như `"  Cafe\u0301  "` bị đổi thành `"café"` nếu luôn hiển thị khóa. Lưu nhãn gốc ở giá trị hoặc trường riêng.
3. **Cho rằng `equalsIgnoreCase` hoặc `CASE_INSENSITIVE_ORDER` là Unicode normalization.** Chúng giải quyết một phần khác của bài toán; vẫn cần xác định dạng Unicode, khoảng trắng và locale.
4. **Tự động dùng `toLowerCase()`.** Locale mặc định có thể thay đổi theo máy/chạy ứng dụng. Với chính sách khóa độc lập locale, truyền `Locale.ROOT`.
5. **Xem `NFKC`, bỏ dấu và NFC là như nhau.** Chúng có phạm vi biến đổi khác nhau; chuẩn hóa rộng hơn có thể làm hai tên riêng hoặc mã khác nhau trở thành cùng khóa.
6. **Ghi đè im lặng khi hai nhãn cùng khóa.** Nếu tính duy nhất không được đảm bảo, hãy giữ danh sách mục hoặc báo rõ va chạm.
7. **Dùng tên hiển thị chuẩn hóa làm ID vĩnh viễn.** Tên có thể đổi và chính sách chuẩn hóa có thể đổi. Với bản ghi dài hạn, thường cần một ID độc lập ổn định.
8. **Thay đổi chính sách mà không di chuyển dữ liệu.** Key ở cache/database tính bằng quy tắc cũ có thể gây tìm thiếu hoặc tạo trùng sau nâng cấp.

## 10. Tự kiểm tra

**Câu 1.** Với hàm `keyOf` ở trên, `keyOf("Cafe")` có bằng `keyOf("Café")` không?

**Đáp án:** Không. NFC thống nhất dạng ký tự tương đương chính tắc, nhưng không xóa dấu sắc của `é`.

**Câu 2.** Vì sao lưu `Map<String, Item>` thường tốt hơn chỉ lưu `Set<String>` các khóa chuẩn hóa khi ứng dụng có yêu cầu hiển thị nguyên văn?

**Đáp án:** `Map` giữ khóa để tra cứu và một giá trị mang nhãn hiển thị riêng. Chỉ lưu khóa đã chuẩn hóa sẽ không đủ thông tin để khôi phục chữ hoa, khoảng trắng hoặc những khác biệt đã bị bỏ.

**Câu 3.** Hai tên `"Café"` và `"cafe\u0301"` trùng sau chuẩn hóa. Có nên dùng `put` để mục sau ghi đè mục trước?

**Đáp án:** Chỉ khi nghiệp vụ chủ ý cho phép thay thế. Nếu cần duy nhất, báo trùng; nếu cần nhiều mục, lưu nhiều giá trị theo một khóa; nếu cần ID, gắn từng bản ghi với ID ổn định.

**Câu 4.** Có nên chuẩn hóa mật khẩu bằng `strip().toLowerCase(Locale.ROOT)` theo bài này không?

**Đáp án:** Không áp dụng tự động. Mật khẩu/token cần giữ đúng quy ước đầu vào của cơ chế xác thực; việc biến đổi thêm có thể thay đổi ý nghĩa bí mật và hành vi xác minh.

## Ghi nhớ

**Khóa chuẩn hóa dùng để xác định “khi nào các chuỗi được coi là cùng nhau”; văn bản hiển thị dùng để giữ “người dùng muốn nhìn thấy chuỗi như thế nào”.** Với `String` trong collection, hãy định nghĩa một hàm `keyOf` phù hợp nghiệp vụ, áp dụng nhất quán trên mọi đường đọc/ghi, xử lý va chạm rõ ràng và lưu riêng nhãn hiển thị cần bảo toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-collections-007">Phân biệt String&#91;&#93; với List&lt;String&gt; khi chuyển đổi dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt String[] và List<String> khi chuyển đổi dữ liệu trong Java

## 1. Vì sao cần phân biệt?

Trong Java, **String[]** (mảng String) và **List<String>** (danh sách String) đều lưu một chuỗi các phần tử có thứ tự và truy cập bằng chỉ số bắt đầu từ 0. Tuy nhiên, **chúng không có cùng cơ chế lưu trữ, kích thước hay quyền thay đổi nội dung**.

Khi chuyển đổi dữ liệu, điều quan trọng không chỉ là “đọc được các String” mà còn là:

- Danh sách có **dùng chung mảng ban đầu** hay tạo cấu trúc độc lập?
- Có thể **thêm/xóa phần tử** hay chỉ thay thế phần tử hiện có?
- Có thể **thay đổi phần tử** qua danh sách hay không?
- Dữ liệu có được **sao chép tại thời điểm chuyển đổi** hay tiếp tục phản ánh thay đổi bên nguồn?
- Có cho phép **null** không? Kiểu trả về thực sự của phương thức chuyển đổi là gì?

Hiểu những điều này giúp tránh lỗi **UnsupportedOperationException**, **ClassCastException**, **ArrayStoreException** và lỗi dữ liệu thay đổi ngoài ý muốn.

Bài sử dụng **Java 17+**, chủ yếu các API chuẩn trong **java.util.Arrays**, **java.util.ArrayList**, **java.util.List** và **java.util.stream**.

## 2. So sánh cấu trúc cơ bản

| Tiêu chí | **String[]** | **List<String>** |
| --- | --- | --- |
| Kiểu | Mảng Java với phần tử kiểu String | Interface collection có phần tử kiểu String |
| Độ dài | Cố định khi tạo mảng; dùng **length** | Dùng **size()**; khả năng tăng/giảm phụ thuộc implementation |
| Truy cập | **arr[i]** | **list.get(i)** |
| Gán phần tử | **arr[i] = value** | **list.set(i, value)** nếu implementation hỗ trợ |
| Thêm/xóa | Không thể thay đổi số phần tử của chính mảng | Có thể được hỗ trợ, ví dụ **ArrayList** |
| Có chứa null? | Có | Tùy danh sách: **ArrayList** cho phép, **List.of** không cho phép |
| In nội dung | **Arrays.toString(arr)** | Thường dùng **list.toString()** |
| Duyệt | **for**, **Arrays.stream(arr)** | **for**, **list.stream()** |
| Vấn đề thường gặp | Kích thước cố định, hiệp biến mảng | Quyền sửa phụ thuộc cách tạo List |

**Không phải mọi List đều có thể thêm/xóa.** **List** là interface; bản thân kiểu **List<String>** không bảo đảm được gọi **add()**, **remove()** hoặc **set()** thành công. Quyền sửa phụ thuộc đối tượng cụ thể:

- **ArrayList**: có thể thay đổi kích thước và thay phần tử.
- **Arrays.asList(array)**: thay phần tử được, nhưng **không** thêm/xóa làm đổi kích thước.
- **List.of(...)** và **List.copyOf(...)**: danh sách **unmodifiable**, không cho sửa cấu trúc hoặc gán phần tử bằng các phương thức thay đổi List.

Các danh sách này vẫn cùng có kiểu tham chiếu **List<String>**, nên cần biết cách chúng được tạo.

## 3. Chuyển String[] thành List<String> bằng Arrays.asList()

Đây là cách thông dụng khi muốn dùng các thao tác theo giao diện **List** trên dữ liệu mảng sẵn có:

~~~java
import java.util.Arrays;
import java.util.List;

String[] names = {"An", "Binh", "Chi"};
List<String> list = Arrays.asList(names);
~~~

**Arrays.asList(names)** trả về danh sách kích thước cố định, **được hỗ trợ bởi chính mảng names** (*backed by the array*).

Điều đó dẫn tới ba quy tắc quan trọng.

### 3.1. Có thể thay phần tử, và việc thay đổi phản ánh hai chiều

~~~java
String[] names = {"An", "Binh"};
List<String> list = Arrays.asList(names);

list.set(0, "Chi");
System.out.println(Arrays.toString(names)); // [Chi, Binh]

names[1] = "Dung";
System.out.println(list);                    // [Chi, Dung]
~~~

Thay phần tử qua **list.set(...)** làm thay giá trị tại vị trí tương ứng của mảng. Thay phần tử qua **names[index]** cũng được phản ánh khi đọc từ List.

**String bất biến** không làm điều này biến mất: ta không sửa nội dung đối tượng String; ta đổi **tham chiếu đến phần tử String** tại một vị trí.

### 3.2. Không thể thêm hoặc xóa để đổi kích thước

~~~java
String[] names = {"An", "Binh"};
List<String> list = Arrays.asList(names);

// list.add("Chi");       // UnsupportedOperationException nếu thực thi
// list.remove(0);        // UnsupportedOperationException nếu thực thi
list.set(1, "Chi");       // Hợp lệ: thay giá trị ở chỉ số sẵn có
~~~

Lý do là mảng ban đầu có độ dài cố định; List do **Arrays.asList** trả về không hỗ trợ thao tác làm thay đổi số lượng phần tử.

Một số tên phương thức như **sort()** hoặc **replaceAll()** có thể thực hiện vì chúng cập nhật phần tử trong kích thước hiện có; ngược lại, **clear()** hoặc **add()** sẽ thất bại. Không suy ra rằng chỉ vì có interface List thì mọi thao tác của List đều được hỗ trợ.

### 3.3. Đây là một dạng view, không phải bản sao tách biệt

**Arrays.asList** không sao chép mảng để tạo dữ liệu độc lập. Nếu code khác còn giữ biến **names** thì nó có thể ảnh hưởng đến danh sách.

Nếu chỉ cần truyền nhanh một mảng cho API nhận **List<String>**, cách này tiện. Nếu muốn danh sách có thể tăng/giảm hoặc muốn tách việc thay đổi phần tử khỏi mảng, hãy tạo **ArrayList** mới.

## 4. Chuyển String[] thành ArrayList có thể thay đổi kích thước

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

String[] names = {"An", "Binh"};

List<String> editable = new ArrayList<>(Arrays.asList(names));
editable.add("Chi");
editable.remove("An");
editable.set(0, "Dung");

System.out.println(editable);              // [Dung, Chi]
System.out.println(Arrays.toString(names)); // [An, Binh]
~~~

**new ArrayList<>(...)** tạo cấu trúc danh sách độc lập với mảng ban đầu:

- Có thể gọi **add**, **remove**, **clear**, **set** theo khả năng của ArrayList.
- Gán phần tử hoặc thay đổi kích thước trong danh sách không đổi các vị trí của mảng nguồn.
- Gán phần tử mới vào mảng nguồn không đổi các vị trí trong danh sách đã tạo.

Đây là **bản sao nông (shallow copy)**: phần tử được sao chép dưới dạng tham chiếu. Với **String**, mỗi String là immutable nên chia sẻ tham chiếu String không gây thay đổi nội dung String. Với danh sách các đối tượng **mutable** khác, việc sao chép danh sách **không** tự động sao chép sâu từng đối tượng.

**Khi cần List có thể sửa, đây thường là lựa chọn rõ ràng nhất.** Có thể viết biến kiểu interface **List<String>** để không ràng buộc code gọi vào implementation cụ thể.

## 5. Tạo List không thể sửa bằng List.of()

Từ Java 9, **List.of(...)** tạo danh sách **unmodifiable**:

~~~java
import java.util.Arrays;
import java.util.List;

String[] names = {"An", "Binh"};
List<String> immutableList = List.of(names);

names[0] = "Chi";

System.out.println(immutableList);         // [An, Binh]
System.out.println(Arrays.toString(names)); // [Chi, Binh]
~~~

Trong trường hợp **String[]** này, **List.of(names)** nhận các phần tử của mảng làm phần tử List, và danh sách tạo ra **không liên kết với mảng nguồn như Arrays.asList**. Thay phần tử của mảng sau khi tạo danh sách không làm thay phần tử đã lưu trong List.

Quy tắc:

- Không thể **add**, **remove**, **set** vào List do **List.of** trả về; phương thức thay đổi danh sách ném **UnsupportedOperationException**.
- **List.of** **không chấp nhận phần tử null**; nếu mảng có phần tử null, lúc tạo danh sách sẽ ném **NullPointerException**.
- Danh sách **unmodifiable** khác với đảm bảo sao chép sâu mọi đối tượng phần tử. Ví dụ với **List<SomeMutableObject>**, bản thân đối tượng vẫn có thể bị sửa từ nơi khác nếu nơi đó có tham chiếu.

Một ví dụ dễ gặp lỗi:

~~~java
String[] values = {"A", null};

// List<String> x = List.of(values); // NullPointerException
List<String> y = Arrays.asList(values); // Hợp lệ: cho phép phần tử null
~~~

Nếu bài toán yêu cầu **null** là giá trị hợp lệ, đừng dùng **List.of** hoặc **List.copyOf** trước khi xác định chính sách xử lý null.

### 5.1. List.copyOf khác gì?

Từ Java 10, **List.copyOf(collection)** tạo một danh sách unmodifiable chứa các phần tử tại thời điểm thực hiện, với quy tắc **không chấp nhận null**. Có thể dùng khi nguồn đã là một **Collection**:

~~~java
List<String> editable = new ArrayList<>(List.of("A", "B"));
List<String> snapshot = List.copyOf(editable);

editable.set(0, "C");

System.out.println(snapshot); // [A, B]
~~~

**List.copyOf** có thể tái sử dụng một List đã unmodifiable phù hợp thay vì lúc nào cũng tạo đối tượng mới. Quy tắc cần quan tâm ở đây là **không để thay đổi cấu trúc/giá trị các vị trí qua nguồn làm thay đổi danh sách trả về**, không phải giả định luôn cấp phát một bản copy mới.

Với mảng nguồn, **List.of(array)** diễn đạt nhu cầu tạo List không thể sửa rất ngắn gọn. Với collection nguồn, **List.copyOf(collection)** diễn đạt rõ mục đích tạo snapshot.

## 6. Ví dụ Java 17+ độc lập: so sánh ba cách chuyển đổi

Tạo tệp **StringArrayListDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StringArrayListDemo {
    public static void main(String[] args) {
        String[] source = {"An", "Binh"};

        // View kích thước cố định, liên kết hai chiều với source.
        List<String> view = Arrays.asList(source);
        view.set(0, "Chi");
        System.out.println("array after view.set: "
                + Arrays.toString(source));

        source[1] = "Dung";
        System.out.println("view after array update: " + view);

        // Bản sao cấu trúc độc lập, có thể thêm/xóa.
        List<String> dynamic =
                new ArrayList<>(Arrays.asList(source));
        dynamic.add("Em");
        dynamic.set(0, "Giang");
        System.out.println("array after copy changes: "
                + Arrays.toString(source));
        System.out.println("dynamic: " + dynamic);

        // List unmodifiable, không cùng dùng mảng source.
        List<String> immutable = List.of(source);
        source[0] = "Hoa";
        System.out.println("List.of snapshot: " + immutable);

        // Chuyển List về mảng String[] mới.
        String[] back = dynamic.toArray(String[]::new);
        back[1] = "Khac";
        System.out.println("converted array: "
                + Arrays.toString(back));
        System.out.println("list after array edit: " + dynamic);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac StringArrayListDemo.java
java StringArrayListDemo
~~~

Kết quả chuẩn:

~~~text
array after view.set: [Chi, Binh]
view after array update: [Chi, Dung]
array after copy changes: [Chi, Dung]
dynamic: [Giang, Dung, Em]
List.of snapshot: [Chi, Dung]
converted array: [Giang, Khac, Em]
list after array edit: [Giang, Dung, Em]
~~~

Diễn giải:

1. **view.set(0, "Chi")** thay vị trí 0 của chính mảng **source**.
2. **source[1] = "Dung"** phản ánh ở **view**, vì view vẫn dùng mảng đó.
3. **new ArrayList<>(...)** giữ các phần tử theo giá trị tại thời điểm tạo, nhưng có các vị trí độc lập. Sửa **dynamic** không thay **source**.
4. **List.of(source)** tạo List không thể sửa từ các phần tử hiện tại; sau khi **source[0] = "Hoa"**, **immutable** vẫn chứa **Chi**.
5. **dynamic.toArray(String[]::new)** tạo **String[]** mới. Sửa mảng **back** không làm **dynamic** đổi theo.

Đó là lý do hai lời gọi đều có dạng “chuyển mảng thành List” nhưng mang ngữ nghĩa sở hữu dữ liệu khác nhau.

## 7. Chuyển List<String> về String[]

Nếu phương thức nhận **String[]** nhưng dữ liệu đang ở **List<String>**, hãy dùng **toArray** có kiểu.

### 7.1. Cách hiện đại: toArray(String[]::new)

~~~java
List<String> names = List.of("An", "Binh", "Chi");

String[] array = names.toArray(String[]::new);
System.out.println(Arrays.toString(array)); // [An, Binh, Chi]
~~~

Biến thể **Collection.toArray(IntFunction<T[]>)** xuất hiện từ Java 11. **String[]::new** là một **array constructor reference**: nó cung cấp cách tạo mảng String đúng kiểu mà API yêu cầu.

Kết quả là một **mảng mới có kiểu String[]**, giữ thứ tự lặp của danh sách. Với List, thứ tự lặp trùng thứ tự các chỉ số.

### 7.2. Cách phổ biến lâu đời: toArray(new String[0])

~~~java
List<String> names = List.of("An", "Binh");
String[] array = names.toArray(new String[0]);
~~~

Đây cũng là cách đúng và tương thích rộng rãi. Mảng đầu vào được dùng để chỉ rõ kiểu phần tử; khi mảng quá nhỏ, API cấp phát một mảng mới có kiểu runtime tương ứng. Với **new String[0]**, kết quả là **String[]** mới có độ dài bằng số phần tử.

Có thể viết:

~~~java
String[] array = names.toArray(new String[names.size()]);
~~~

Nếu mảng truyền vào đủ dài, API có thể **dùng chính mảng đó** để chứa kết quả; khi mảng dài hơn số phần tử, phần tử ngay sau kết quả được đặt thành **null**. Đây là điểm cần biết khi dùng lại một mảng có sẵn:

~~~java
List<String> names = List.of("A", "B");
String[] buffer = new String[4];

String[] result = names.toArray(buffer);
System.out.println(result == buffer);           // true
System.out.println(Arrays.toString(buffer));   // [A, B, null, null]
~~~

**Chọn một trong hai cách dễ đọc**: **list.toArray(String[]::new)** cho Java 11+, hoặc **list.toArray(new String[0])** khi muốn cách quen thuộc và tương thích với Java cũ hơn.

### 7.3. Tránh toArray() không tham số nếu cần String[]

**list.toArray()** trả về **Object[]**, không phải **String[]**:

~~~java
List<String> names = List.of("A", "B");
Object[] generic = names.toArray();      // Đúng: Object[]
String[] typed = names.toArray(String[]::new); // Đúng: String[]
~~~

Đoạn sau sẽ thất bại ở runtime:

~~~java
// String[] wrong = (String[]) names.toArray();
// ClassCastException: Object[] không thể ép thành String[]
~~~

Việc mọi phần tử đều là **String** **không biến kiểu runtime của Object[] thành String[]**. Phải yêu cầu mảng có kiểu thành phần đúng ngay từ lúc chuyển đổi.

## 8. So sánh tính độc lập của dữ liệu sau chuyển đổi

Đây là bảng chọn API theo mục đích phổ biến:

| Từ nguồn | Cách chuyển đổi | Có sửa kích thước List được không? | Liên kết thay phần tử với mảng nguồn? |
| --- | --- | --- | --- |
| **String[]** | **Arrays.asList(arr)** | Không | Có, hai chiều |
| **String[]** | **new ArrayList<>(Arrays.asList(arr))** | Có | Không |
| **String[]** | **List.of(arr)** | Không; unmodifiable | Không |
| **List<String>** | **list.toArray(String[]::new)** | Không áp dụng: kết quả là mảng | Không: mảng mới |
| **List<String>** | **list.toArray(new String[0])** | Không áp dụng: kết quả là mảng | Không: mảng mới |

Bảng nói về **thay phần tử hoặc kích thước của cấu trúc chứa**; không khẳng định có deep copy từng đối tượng. Với String immutable, mối quan hệ này thường đủ để quyết định cách chuyển đổi.

### 8.1. Unmodifiable view không đồng nghĩa snapshot

Có thêm cách bọc một List bằng **Collections.unmodifiableList(list)**:

~~~java
String[] source = {"A", "B"};
List<String> view = Arrays.asList(source);
List<String> readOnlyView = java.util.Collections.unmodifiableList(view);

source[0] = "C";
System.out.println(readOnlyView); // [C, B]
~~~

Người giữ **readOnlyView** không được gọi **set()** để thay phần tử qua wrapper, nhưng List bọc bên dưới vẫn là view trên mảng **source**. Khi source đổi, nội dung đọc từ wrapper đổi theo.

Vì vậy, **read-only interface** và **snapshot độc lập** là hai khái niệm khác nhau:

- Cần chỉ cấm phía nhận sửa qua reference? Wrapper unmodifiable có thể đủ, nhưng dữ liệu gốc vẫn có thể thay đổi.
- Cần giữ tập phần tử ở thời điểm chuyển đổi? Dùng **List.of(array)** hoặc **List.copyOf(collection)** khi chính sách null cho phép.

## 9. Null, chuỗi rỗng và mảng/danh sách rỗng

**String rỗng ""** là một đối tượng String hợp lệ và **khác null**. Đừng loại bỏ nó chỉ vì một API không cho phần tử null.

~~~java
String[] source = {"A", null, ""};

List<String> fixed = Arrays.asList(source);
List<String> dynamic = new ArrayList<>(fixed);

System.out.println(fixed.size());        // 3
System.out.println(dynamic.get(1) == null); // true
System.out.println(dynamic.get(2).isEmpty()); // true

// List<String> immutable = List.of(source);
// Ném NullPointerException vì có phần tử null.
~~~

Khi **chính mảng nguồn bằng null**, các thao tác chuyển đổi nhận mảng không phải là “tạo danh sách rỗng”:

~~~java
String[] source = null;

// Arrays.asList(source) hoặc List.of(source) sẽ gặp lỗi null;
// cần quyết định null nghĩa là gì theo hợp đồng API.
List<String> result =
        source == null ? List.of() : new ArrayList<>(Arrays.asList(source));
~~~

Đây chỉ là **một chính sách minh họa**: chuyển null thành danh sách rỗng. Trong API khác, null có thể là dữ liệu thiếu không hợp lệ và nên được báo lỗi; không mặc định nuốt mất tín hiệu nghiệp vụ.

Với dữ liệu rỗng thực sự:

~~~java
String[] emptyArray = new String[0];
List<String> emptyList = List.of();

System.out.println(Arrays.asList(emptyArray).isEmpty()); // true
System.out.println(emptyList.toArray(String[]::new).length); // 0
~~~

Mảng độ dài 0 và List không phần tử đều là cấu trúc hợp lệ. Nhưng một List rỗng unmodifiable vẫn không cho phép **add()**.

## 10. Hiệp biến mảng và bất biến generics: vấn đề type safety

Sự khác nhau còn nằm ở hệ thống kiểu Java.

### 10.1. Mảng hiệp biến (covariant)

Java cho phép gán **String[]** vào biến **Object[]**, vì String là lớp con của Object. Nhưng mảng giữ kiểu thành phần thực tế ở runtime:

~~~java
Object[] values = new String[]{"A", "B"};
values[0] = "C"; // Hợp lệ
// values[1] = 42; // ArrayStoreException ở runtime
~~~

Biến được khai báo là **Object[]**, nhưng đối tượng thực là **String[]**. Gán Integer vào mảng đó bị từ chối ở runtime.

### 10.2. Generic List bất biến (invariant)

Java không cho phép coi **List<String>** là **List<Object>**:

~~~java
List<String> names = new ArrayList<>();
// List<Object> objects = names; // Lỗi biên dịch
~~~

Điều này ngăn việc thêm đối tượng không phải String vào danh sách qua một tham chiếu **List<Object>**. Nếu chỉ cần **đọc** các phần tử như Object, có thể sử dụng wildcard:

~~~java
List<String> names = List.of("An", "Binh");
List<? extends Object> readable = names;

Object first = readable.get(0);
// readable.add(123); // Không hợp lệ với wildcard extends
~~~

Các quy tắc này giải thích tại sao chuyển đổi dữ liệu nên **giữ kiểu String[] hoặc List<String>**, tránh ép kiểu không an toàn sang mảng/generic khác.

### 10.3. Tránh dùng raw type

Không nên viết:

~~~java
// List raw = Arrays.asList(new String[]{"A", "B"});
~~~

Raw type bỏ bớt kiểm tra kiểu ở thời điểm biên dịch, dễ dẫn đến lỗi khi đưa dữ liệu qua API khác. Với Java hiện đại, khai báo rõ **List<String>**.

## 11. Dùng Stream khi chuyển đổi cần xử lý phần tử

Nếu chỉ chuyển cấu trúc giữ nguyên các phần tử, **Arrays.asList** hoặc **toArray** thường đơn giản hơn. Nếu cần **lọc, ánh xạ, sắp xếp** trong quá trình chuyển, Stream phù hợp hơn.

### 11.1. Từ String[] thành List đã xử lý

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

String[] data = {" An ", "", " Binh ", " Chi "};

List<String> clean = Arrays.stream(data)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(ArrayList::new));

System.out.println(clean); // [An, Binh, Chi]
clean.add("Dung");         // Được: kết quả là ArrayList
~~~

**Collectors.toCollection(ArrayList::new)** làm rõ mong muốn có danh sách có thể thay đổi. Nếu dùng **Stream.toList()** (Java 16+), kết quả là List **unmodifiable**, không nên gọi **add()** hoặc **set()**.

### 11.2. Từ List thành mảng sau khi xử lý

~~~java
List<String> names = List.of("An", "Binh", "Chi");

String[] shortNames = names.stream()
        .filter(s -> s.length() <= 3)
        .toArray(String[]::new);

System.out.println(Arrays.toString(shortNames)); // [An, Chi]
~~~

**Stream.toArray(String[]::new)** tạo mảng đúng kiểu, tương tự ý tưởng dùng **Collection.toArray(String[]::new)**. Nhưng Stream phù hợp khi có thao tác trung gian; không cần mở Stream chỉ để chép List sang mảng.

### 11.3. Đừng suy ra mọi collector trả ArrayList

Các cách như **Collectors.toList()** không hứa hẹn implementation hoặc quyền sửa cụ thể trong hợp đồng API. Nếu ứng dụng cần chắc chắn có thể thêm/xóa, hãy yêu cầu bằng **Collectors.toCollection(ArrayList::new)** hoặc chuyển kết quả thành **new ArrayList<>(...)**.

## 12. Chuyển đổi ở ranh giới API và bảo vệ dữ liệu

Giả sử một phương thức nhận mảng và lưu danh sách cho sử dụng lâu dài:

~~~java
import java.util.Arrays;
import java.util.List;

public class NameRepository {
    private final List<String> names;

    public NameRepository(String[] input) {
        this.names = List.copyOf(Arrays.asList(input));
    }

    public List<String> getNames() {
        return names;
    }
}
~~~

Ý nghĩa:

- **List.copyOf** tạo danh sách unmodifiable, không phụ thuộc việc gán lại phần tử trong mảng **input** sau đó.
- Code bên ngoài không thể thay phần tử qua danh sách trả về.
- Constructor **không chấp nhận null array hoặc null element**; đây phải là lựa chọn phù hợp với hợp đồng của lớp.
- Đây là trường hợp **String** bất biến; nếu phần tử là đối tượng mutable, muốn bất biến sâu cần xem xét sao chép chính đối tượng phần tử.

Nếu API cần nhận dữ liệu có thể chứa null hoặc cần cập nhật danh sách nội bộ, có thể sử dụng **new ArrayList<>(Arrays.asList(input))**, kết hợp quy định quyền sửa/đồng bộ thích hợp.

Khi trả **String[]** cho code khác, dùng **clone()** nếu không muốn bên nhận sửa cùng mảng nội bộ:

~~~java
private final String[] stored = {"A", "B"};

public String[] getNames() {
    return stored.clone();
}
~~~

**clone()** của mảng tạo một mảng mới cùng kiểu và cùng các tham chiếu phần tử; với String immutable, cách này thường đủ để tránh code gọi thay phần tử của mảng lưu trữ.

**Không mặc định rằng chuyển đổi tự phòng thủ dữ liệu**: cụ thể, **Arrays.asList(source)** vẫn cho code gọi sửa các phần tử của mảng source qua **set()**.

## 13. Các lỗi thường gặp và cách khắc phục

| Tình huống | Nguyên nhân | Cách đúng |
| --- | --- | --- |
| **Arrays.asList(arr).add("X")** ném lỗi | List được hỗ trợ bởi mảng, kích thước cố định | **new ArrayList<>(Arrays.asList(arr))** |
| **List.of(arr).set(0, "X")** ném lỗi | List unmodifiable | Chọn **new ArrayList<>(...)** khi cần sửa |
| Sửa mảng làm List tự thay đổi | **Arrays.asList** dùng chung mảng | Dùng bản sao độc lập |
| **(String[]) list.toArray()** ném lỗi | **toArray()** không tham số trả **Object[]** | **toArray(String[]::new)** hoặc **toArray(new String[0])** |
| **List.of(arr)** ném NullPointerException | Mảng chứa phần tử null | Xử lý null hoặc chọn List cho phép null |
| In mảng ra mã nhận dạng khó đọc | **array.toString()** không hiển thị nội dung | Dùng **Arrays.toString(array)** |
| Nhầm **List<String>** luôn có thể add | Interface không bảo đảm implementation mutable | Biết nguồn gốc List; dùng ArrayList nếu cần |
| Bọc **unmodifiableList** nhưng dữ liệu vẫn đổi | Wrapper chỉ chặn thay đổi qua nó; nguồn dưới vẫn có thể đổi | Tạo snapshot độc lập |
| Ép mảng hoặc dùng raw generic không an toàn | Kiểu runtime và quy tắc generic khác nhau | Giữ **String[]**, **List<String>** đúng kiểu |
| Sao chép List rồi tưởng sao chép sâu tất cả đối tượng | Các phần tử được copy theo tham chiếu | Cân nhắc deep copy nếu phần tử mutable |

## 14. Bài tập tự kiểm tra

### Bài 1: Dự đoán đầu ra

~~~java
String[] data = {"A", "B"};
List<String> a = Arrays.asList(data);
List<String> b = new ArrayList<>(a);
List<String> c = List.of(data);

a.set(0, "X");
b.add("C");
data[1] = "Y";

System.out.println(Arrays.toString(data));
System.out.println(a);
System.out.println(b);
System.out.println(c);
~~~

Kết quả:

~~~text
[X, Y]
[X, Y]
[A, B, C]
[A, B]
~~~

Giải thích: **a** dùng chung mảng **data**; **b** tạo cấu trúc ArrayList riêng khi dữ liệu là **[A, B]**; **c** chốt các phần tử thành List unmodifiable tại thời điểm tạo. Việc thay đổi mảng sau đó không cập nhật **b** hoặc **c**.

### Bài 2: Chọn đúng API

Bạn có mảng **String[] tags** và cần:

1. Chỉ đọc các phần tử qua API nhận **List<String>**, có thể chấp nhận các thay đổi từ mảng nguồn → **Arrays.asList(tags)**.
2. Thêm/xóa các nhãn trong List mà không làm thay mảng gốc → **new ArrayList<>(Arrays.asList(tags))**.
3. Lưu một danh sách không thể sửa, không phản ánh các thay đổi vị trí mảng sau khi chuyển, và chắc chắn không có null → **List.of(tags)**.
4. Trả một mảng String độc lập từ một List → **list.toArray(String[]::new)**.
5. Lọc bỏ phần tử rỗng trước khi tạo mảng → Stream với **filter(...)** rồi **toArray(String[]::new)**.

Hãy tự thay các giá trị bằng **null**, chuỗi rỗng hoặc danh sách không phần tử để kiểm tra cách từng API xử lý.

## 15. Checklist trước khi chuyển đổi trong chương trình thật

- [ ] API đầu ra thực sự cần **String[]** hay **List<String>**?
- [ ] Nếu cần List, muốn **view chung dữ liệu**, **bản sao sửa được** hay **snapshot unmodifiable**?
- [ ] Phần tử null có hợp lệ không, hay cần loại bỏ/từ chối trước khi chuyển đổi?
- [ ] Code gọi có cần **add/remove**, **set** hay chỉ đọc?
- [ ] Có ai khác còn giữ mảng/List nguồn và có thể thay đổi nó sau chuyển đổi không?
- [ ] Khi chuyển về mảng, đã gọi **toArray(String[]::new)** hoặc **toArray(new String[0])** chưa?
- [ ] Nếu phần tử là đối tượng mutable thay vì String, đã phân biệt sao chép cấu trúc với deep copy chưa?
- [ ] Đã thử các trường hợp rỗng, một phần tử, nhiều phần tử và thay đổi dữ liệu sau chuyển đổi chưa?

**Ghi nhớ:** **String[]** có độ dài cố định; **List<String>** là một interface với quyền sửa tùy implementation. **Arrays.asList** tạo view kích thước cố định gắn với mảng; **new ArrayList<>(Arrays.asList(...))** tạo cấu trúc độc lập có thể sửa; **List.of** tạo List không thể sửa, không phản ánh việc thay phần tử của mảng nguồn; **toArray(String[]::new)** chuyển về mảng đúng kiểu. Chọn API theo **quyền sửa và quan hệ sở hữu dữ liệu**, không chỉ theo cú pháp ngắn nhất.

### Tài liệu tham khảo chính thức

- Java SE 17, **java.util.Arrays** (asList, toString, stream): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html
- Java SE 17, **java.util.List** (of, copyOf, toArray): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html
- Java SE 17, **java.util.Collection** (toArray): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html
- Java SE 17, **java.util.stream.Stream** (toList, toArray): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html


</details>

- [Quay lại đầu trang](#back-to-top)
