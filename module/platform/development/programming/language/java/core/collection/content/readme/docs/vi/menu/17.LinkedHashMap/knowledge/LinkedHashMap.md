<a id="back-to-top"></a>

# LinkedHashMap

## Menu
- [Kết hợp bảng băm với thứ tự duyệt có xác định](#linked-hash-map-001)
- [Thứ tự chèn và hành vi thay giá trị của khóa đã có](#linked-hash-map-002)
- [Chế độ access-order và các thao tác làm đổi thứ tự](#linked-hash-map-003)
- [Chi phí duyệt, lưu trữ và lựa chọn so với HashMap](#linked-hash-map-004)
- [removeEldestEntry và mô hình bộ nhớ đệm LRU đơn giản](#linked-hash-map-005)
- [Giới hạn an toàn đa luồng của bộ nhớ đệm tự viết](#linked-hash-map-006)
- [Tương tác giữa access-order, iterator và thay đổi cấu trúc](#linked-hash-map-007)
- [Thao tác đầu–cuối và view đảo thứ tự từ Java 21](#linked-hash-map-008)

## <a id="linked-hash-map-001">Kết hợp bảng băm với thứ tự duyệt có xác định</a>

<details>
<summary>Xem chi tiết</summary>

# Kết hợp bảng băm với thứ tự duyệt có xác định

## 1. Vì sao cần LinkedHashMap?

Một màn hình cấu hình có các trường được thêm theo thứ tự: tên hiển thị, email, ngôn ngữ. Chương trình cần hai khả năng:

- Tìm nhanh giá trị theo tên trường.
- Khi xuất danh sách, giữ đúng thứ tự đã đưa các trường vào.

HashMap giải quyết tốt việc ánh xạ khóa tới giá trị, nhưng không bảo đảm thứ tự duyệt. Một List giữ được thứ tự, nhưng nếu chỉ dùng danh sách các cặp thì tìm một khóa thường phải kiểm tra nhiều cặp.

LinkedHashMap kết hợp hai ý tưởng: **dùng bảng băm để định vị ánh xạ theo khóa và dùng một chuỗi liên kết để xác định thứ tự duyệt các ánh xạ**.

Vì vậy, tên LinkedHashMap không có nghĩa mỗi lần get đều phải đi hết một LinkedList. Hai cấu trúc phục vụ hai công việc khác nhau.

Bài học tập trung vào cơ chế kết hợp ấy, thứ tự chèn mặc định và những hệ quả khi đọc, cập nhật, sao chép hoặc kiểm thử map. Chế độ thứ tự truy cập được giới thiệu để tránh hiểu rằng mọi LinkedHashMap luôn giữ nguyên vị trí sau get.

## 2. Hai câu hỏi độc lập: tìm ở đâu và duyệt theo đường nào?

### 2.1. Bảng băm trả lời câu hỏi tìm kiếm

Khi gọi get(key), cơ chế băm dùng hash của khóa để định hướng tới bucket có thể chứa ánh xạ, sau đó kiểm tra khóa phù hợp bằng quy tắc bằng của map.

Điều kiện cần giữ vẫn là hợp đồng equals/hashCode. Hai khóa bằng nhau phải có cùng hash; nhiều khóa khác nhau có thể va chạm mà vẫn là những ánh xạ riêng.

Việc có thêm thứ tự không làm LinkedHashMap bỏ mô hình khóa duy nhất của Map.

### 2.2. Chuỗi liên kết trả lời câu hỏi duyệt

Ngoài thông tin phục vụ bảng băm, các entry còn được nối với nhau theo một thứ tự của toàn map.

Hình khái niệm với ba entry:

```text
Bảng băm: hash(key) → bucket phù hợp → entry cần tìm

Thứ tự duyệt:
đầu → [zeta=3] ⇄ [alpha=1] ⇄ [beta=2] → cuối
```

Một entry được tham gia cả cơ chế định vị theo hash lẫn cơ chế thứ tự. Không cần duy trì thủ công một HashMap và một List chứa dữ liệu trùng nhau trong mã ứng dụng.

Trong mã nguồn OpenJDK 17u, entry thông thường có hai liên kết before/after; map có đầu và cuối của chuỗi. Iterator đi theo liên kết thứ tự, thay vì lấy thứ tự bucket làm thứ tự kết quả. Có thể đối chiếu [LinkedHashMap.java OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/LinkedHashMap.java).

Tên trường và cách tổ chức lớp nội bộ là chi tiết cài đặt. Điều người dùng được dựa vào là hợp đồng thứ tự duyệt của LinkedHashMap.

### 2.3. Không nhầm hai loại liên kết

Một bucket có nhiều khóa va chạm cũng có thể cần liên kết hoặc cấu trúc nội bộ để giữ các ứng viên trong bucket đó.

Chuỗi before/after của LinkedHashMap có vai trò khác: nó nối **tất cả entry của map theo thứ tự duyệt**, kể cả những entry nằm ở các bucket khác nhau.

Vì thế không suy ra “các khóa gần nhau khi duyệt thì có cùng bucket” hoặc “các khóa cùng hash sẽ đứng liền nhau trong kết quả”.

## 3. Mặc định là thứ tự chèn, không phải thứ tự khóa

Các constructor thông thường như new LinkedHashMap<>() tạo map theo **thứ tự chèn (insertion order)**.

Nếu lần lượt thêm zeta, alpha, beta thì thứ tự duyệt là:

```text
zeta → alpha → beta
```

Nó không tự đổi thành alpha, beta, zeta chỉ vì tên khóa có thứ tự chữ cái như vậy.

### 3.1. Khóa mới được thêm vào cuối

Một khóa chưa có trong map tạo thêm một ánh xạ. Trong chế độ chèn mặc định, ánh xạ ấy đứng sau những ánh xạ còn tồn tại trước đó.

Thứ tự ở đây được tạo bởi lịch sử thao tác lên map, không phải một thuộc tính sắp xếp sẵn của dữ liệu.

### 3.2. Ghi lại khóa đang có giữ vị trí

Với thứ tự zeta, alpha, beta, gọi put("alpha", 10) thay giá trị của alpha nhưng không đẩy alpha xuống cuối trong chế độ chèn.

Điều này có ích khi muốn giữ lần xuất hiện đầu tiên của một khóa, đồng thời cập nhật nội dung mới nhất cho khóa ấy.

Không cần cùng một đối tượng khóa theo phép ==. Một đối tượng khóa khác nhưng bằng khóa đã có vẫn là cập nhật ánh xạ của khóa logic ấy, nếu các khóa tuân hợp đồng.

### 3.3. Xóa rồi thêm lại là một lần chèn mới

Nếu remove("zeta") rồi put("zeta", 30), giữa hai thao tác ấy map không còn zeta. Lần put sau thêm một ánh xạ mới vào cuối.

Thứ tự chuyển từ:

```text
zeta → alpha → beta
       xóa zeta
alpha → beta
       thêm zeta
alpha → beta → zeta
```

Đây là khác biệt giữa “cập nhật giá trị của một khóa còn tồn tại” và “xóa ánh xạ rồi tạo lại”.

### 3.4. Đọc trong chế độ chèn không đổi thứ tự

Get, containsKey và việc duyệt các view không được hiểu là một lần chèn lại. Với cấu hình mặc định, đọc alpha không làm alpha rời vị trí cũ.

Phải biết chế độ thứ tự trước khi kết luận về tác dụng của một thao tác đọc; phần sau sẽ đối chiếu chế độ truy cập.

## 4. Ví dụ Java 17: quan sát thứ tự qua các thao tác

Lưu thành **LinkedOrderDemo.java**:

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public class LinkedOrderDemo {
    static String entries(Map<String, Integer> map) {
        StringJoiner result = new StringJoiner("; ");
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            result.add(entry.getKey() + "=" + entry.getValue());
        }
        return result.toString();
    }

    public static void main(String[] args) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("zeta", 3);
        scores.put("alpha", 1);
        scores.put("beta", 2);
        System.out.println("initial keys: " + new ArrayList<>(scores.keySet()));
        System.out.println("get alpha: " + scores.get("alpha"));
        System.out.println("after get: " + new ArrayList<>(scores.keySet()));

        System.out.println("old alpha value: " + scores.put("alpha", 10));
        System.out.println("after update: " + new ArrayList<>(scores.keySet()));
        System.out.println("size after update: " + scores.size());

        scores.remove("zeta");
        scores.put("zeta", 30);
        scores.put("beta", 10);
        System.out.println("after remove and insert: " + new ArrayList<>(scores.keySet()));
        System.out.println("values: " + new ArrayList<>(scores.values()));
        System.out.println("entries: " + entries(scores));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 LinkedOrderDemo.java
java LinkedOrderDemo
```

Stdout:

```text
initial keys: [zeta, alpha, beta]
get alpha: 1
after get: [zeta, alpha, beta]
old alpha value: 1
after update: [zeta, alpha, beta]
size after update: 3
after remove and insert: [alpha, beta, zeta]
values: [10, 10, 30]
entries: alpha=10; beta=10; zeta=30
```

Các chương trình trong bài đã được biên dịch với --release 17 và chạy trên OpenJDK 24.0.2. Cờ này chọn mức ngôn ngữ/API đích và bytecode; JVM chạy chương trình vẫn là phiên bản 24.0.2.

### 4.1. Giải thích kết quả

Ba khóa ban đầu là zeta, alpha, beta nên keySet đi theo thứ tự ấy.

Get alpha trả 1 nhưng không đổi thứ tự. Put alpha bằng 10 trả về giá trị cũ 1; số khóa vẫn là ba và alpha vẫn ở vị trí thứ hai.

Sau khi xóa zeta rồi thêm lại, zeta đứng cuối. Tiếp theo cập nhật beta thành 10 không đổi vị trí beta.

Dòng values là [10, 10, 30]. Nó có hai giá trị 10 vì Map chỉ đòi khóa duy nhất; giá trị có thể trùng. Dòng entries thể hiện chính xác cặp khóa–giá trị ở từng vị trí duyệt.

### 4.2. Vì sao vẫn khai báo biến bằng Map?

Biến scores có kiểu Map<String, Integer>, còn đối tượng thực tế được tạo là LinkedHashMap. Việc dùng giao diện Map không làm mất hành vi của đối tượng thực tế.

Tuy nhiên một phương thức nhận Map bất kỳ không được tự giả định mọi đối số đều có thứ tự chèn. Nếu thứ tự là yêu cầu của giao tiếp, phải mô tả yêu cầu ấy rõ ràng và dùng cấu trúc hoặc bản sao phù hợp tại ranh giới.

Các new ArrayList<>(...) trong ví dụ tạo danh sách để in và so sánh thứ tự ở thời điểm đó. Chúng không thay đổi thứ tự bên trong map.

## 5. Một thứ tự được chia sẻ qua keySet, values và entrySet

LinkedHashMap có ba góc nhìn quen thuộc:

| View | Dữ liệu khi duyệt | Quan hệ với cùng một thứ tự entry |
| --- | --- | --- |
| keySet() | Khóa | Khóa của entry thứ nhất, thứ hai, ... |
| values() | Giá trị | Giá trị của entry thứ nhất, thứ hai, ... |
| entrySet() | Cặp khóa–giá trị | Chính các entry theo thứ tự đó |

Nếu map không bị thay đổi giữa các lượt duyệt, ba góc nhìn tương ứng cùng một chuỗi entry. Không có việc keySet giữ thứ tự chèn còn values tự sắp xếp theo giá trị.

### 5.1. View không phải bản sao

Giữ một biến trỏ tới keySet rồi thêm khóa vào map sẽ làm view phản ánh map đã đổi. Ngược lại, thao tác xóa được hỗ trợ qua view có tác dụng lên map.

Nếu cần giữ danh sách khóa tại một thời điểm, có thể tạo new ArrayList<>(map.keySet()). Đây là bản chụp của dãy tham chiếu khóa ở thời điểm sao chép, không phải bản sao sâu của các đối tượng khóa.

### 5.2. Muốn dùng cả khóa và giá trị, duyệt entrySet

Trong ví dụ, entries duyệt Map.Entry để lấy cả hai thành phần của cùng ánh xạ. Cách này thể hiện trực tiếp công việc đang làm.

Đặc biệt, không cần lấy từng khóa rồi gọi get lần nữa chỉ để tìm giá trị đã nằm trong entry. Trong chế độ thứ tự truy cập, một lệnh get còn có thể tác động tới thứ tự, nên lựa chọn vòng lặp cần có chủ đích.

### 5.3. Xóa khi đang duyệt

Nếu cần xóa ngay trong một lượt iterator, dùng remove của chính iterator sau next khi thao tác đó được phép. Iterator của LinkedHashMap duy trì tính nhất quán của việc duyệt và xóa ánh xạ.

Không vừa duyệt bằng enhanced for vừa gọi map.remove tùy ý rồi dựa vào việc một lần chạy chưa báo lỗi. Thay đổi cấu trúc ngoài đường được phép có thể khiến iterator không còn hợp lệ.

Cơ chế fail-fast hỗ trợ phát hiện lỗi theo khả năng; không phải giao thức đồng bộ hay bằng chứng mọi truy cập sai chắc chắn sẽ bị phát hiện.

## 6. Thứ tự chèn, thứ tự truy cập và thứ tự sắp xếp

“Có thứ tự” là một mô tả chưa đủ. Cần biết **thứ tự được tạo theo quy tắc nào**.

| Cấu trúc hoặc cấu hình | Quy tắc thứ tự chủ yếu |
| --- | --- |
| HashMap | Không có cam kết thứ tự duyệt |
| LinkedHashMap mặc định | Thứ tự chèn của các khóa còn tồn tại |
| LinkedHashMap với accessOrder = true | Thứ tự truy cập, từ ít được truy cập gần đây nhất tới gần đây nhất |
| TreeMap | Thứ tự khóa theo thứ tự tự nhiên hoặc Comparator phù hợp |

### 6.1. LinkedHashMap không cần so sánh các khóa để giữ thứ tự chèn

Khóa không phải triển khai Comparable chỉ để dùng LinkedHashMap. Ta cũng không truyền Comparator vào constructor của lớp này để yêu cầu sắp xếp.

Nếu cần dãy khóa đã sắp xếp hoặc truy vấn theo khoảng, phải chọn một hợp đồng đáp ứng yêu cầu đó. Thứ tự chèn không tự cung cấp các phép truy vấn như floorKey.

Đối chiếu [TreeMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html) khi yêu cầu là thứ tự so sánh khóa.

### 6.2. Chế độ truy cập thay đổi ý nghĩa của lịch sử

Constructor ba tham số có thể bật accessOrder:

```java
Map<String, Integer> byAccess = new LinkedHashMap<>(16, 0.75f, true);
```

Đây là đoạn khai báo minh họa trong ngữ cảnh đã import Map và LinkedHashMap. Số 16 là tham số dung lượng, 0,75 là load factor và true chọn chế độ truy cập.

Nếu đang có a, b, c, truy cập thành công a bằng get có thể đưa a về cuối, tạo b, c, a. Cuối chuỗi khi ấy là entry được truy cập gần đây nhất, không còn đơn giản là khóa được chèn gần đây nhất.

Tài liệu lớp định nghĩa những thao tác nào được tính là truy cập. Ví dụ get và put có vai trò này đối với entry liên quan trong điều kiện tương ứng; containsKey không được tính là truy cập làm đổi thứ tự.

Không suy ra mọi lời gọi phương thức lên map đều làm một entry thành mới nhất. Duyệt view cũng không được tính là truy cập entry theo quy tắc này.

### 6.3. Chế độ truy cập không tự tạo một cache đầy đủ

Cấu hình accessOrder chỉ chọn cách duy trì thứ tự. Nó không tự đặt giới hạn số entry, thời gian hết hạn hoặc chính sách loại bỏ dữ liệu.

Đây là nền tảng hữu ích cho một số cách tổ chức dữ liệu theo mức dùng gần đây. Muốn có hành vi cache hoàn chỉnh còn cần chính sách riêng.

Trong bài này, cần nhận biết chế độ để hiểu đúng hợp đồng duyệt; không phải tự triển khai một hệ thống cache.

## 7. Có cùng ánh xạ chưa chắc có cùng thứ tự

Hai LinkedHashMap có thể chứa cùng những cặp khóa–giá trị nhưng đã được chèn theo trình tự khác nhau.

Map.equals kiểm tra **cùng các ánh xạ**, không kiểm tra dãy thứ tự chèn. Đó là hợp đồng chung được nêu trong [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#equals(java.lang.Object)).

Ví dụ:

```text
Map trái:  b=2, a=1, c=3
Map phải:  c=3, a=1, b=2

Các ánh xạ giống nhau.
Dãy khóa khi duyệt khác nhau.
```

### 7.1. Kiểm thử thứ tự bằng dãy có thứ tự

Nếu mục tiêu là kiểm tra thứ tự khóa, hãy so sánh hai List khóa được tạo từ lượt duyệt, hoặc kiểm tra chuỗi khóa thu được theo iterator.

Chỉ dùng map.equals hoặc keySet.equals là chưa đủ. Set cũng đánh giá thành phần theo phép bằng của tập hợp, không xác nhận thứ tự duyệt của nó.

Nếu cần kiểm tra cả nội dung và thứ tự, có thể kiểm tra ánh xạ rồi kiểm tra dãy khóa. Nếu cần giữ các cặp để so sánh sau khi map còn có thể đổi, nên chụp các cặp thích hợp thay vì giữ entry sống rồi coi đó là dữ liệu bất biến.

### 7.2. Không dùng hashCode của map để xác nhận thứ tự

Hợp đồng hashCode của Map đi cùng equals của ánh xạ. Hai map bằng nhau phải có cùng hashCode dù thứ tự chèn khác.

Một hashCode trùng nhau càng không chứng minh thứ tự giống nhau. Đừng dùng nó thay cho một kiểm tra thứ tự có mục đích rõ ràng.

## 8. Sao chép giữ thứ tự đang quan sát, không khôi phục một lịch sử bị mất

new LinkedHashMap<>(source) tạo một map theo thứ tự chèn với các ánh xạ từ source. Thứ tự được đưa vào bản sao theo lượt duyệt nguồn.

### 8.1. Nếu nguồn có hợp đồng thứ tự

Sao chép từ một LinkedHashMap theo thứ tự chèn giúp giữ thứ tự duyệt của nguồn tại lúc sao chép.

Nếu nguồn là TreeMap, bản sao có thể bắt đầu với thứ tự khóa đang được duyệt theo sắp xếp. Nhưng bản sao mới vẫn là LinkedHashMap theo thứ tự chèn: thêm một khóa mới sau đó đưa nó xuống cuối, không tự chen vào vị trí đã sắp xếp.

Sao chép từ một map theo thứ tự truy cập cũng ghi nhận thứ tự được quan sát lúc đó; constructor sao chép không tự mang chế độ accessOrder sang bản mới.

### 8.2. Nếu nguồn không bảo đảm thứ tự

Sao chép một HashMap thành LinkedHashMap ghi nhận thứ tự mà HashMap đưa ra trong lượt sao chép. Nó không biết các khóa đã được thêm vào HashMap theo trình tự nào.

Kết quả mới có thể được giữ ổn định theo lịch sử thao tác sau đó, nhưng không trở thành một thứ tự chuẩn như chữ cái, thời gian tạo bản ghi hay thứ tự đầu vào ban đầu.

Nếu đầu ra cần thứ tự nhất quán độc lập với thứ tự nguồn, hãy định nghĩa quy tắc đó rồi chủ động sắp xếp hoặc đưa dữ liệu vào theo quy tắc ấy.

### 8.3. Bản sao cấu trúc chưa phải bản sao sâu

Thêm hoặc xóa ánh xạ ở bản gốc không tự thêm hoặc xóa ánh xạ ở bản sao. Tuy nhiên khóa và giá trị là các đối tượng được tham chiếu lại.

Nếu một giá trị là đối tượng có thể sửa, hai map có thể cùng trỏ tới đối tượng giá trị đó. LinkedHashMap không tự sao chép sâu mọi đối tượng chỉ vì constructor nhận một Map.

## 9. Ví dụ Java 17: equals, sao chép và chế độ truy cập

Lưu thành **OrderSemanticsDemo.java**:

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderSemanticsDemo {
    static List<String> keys(Map<String, Integer> map) {
        return new ArrayList<>(map.keySet());
    }

    public static void main(String[] args) {
        Map<String, Integer> left = new LinkedHashMap<>();
        left.put("b", 2);
        left.put("a", 1);
        left.put("c", 3);

        Map<String, Integer> right = new LinkedHashMap<>();
        right.put("c", 3);
        right.put("a", 1);
        right.put("b", 2);
        System.out.println("left order: " + keys(left));
        System.out.println("right order: " + keys(right));
        System.out.println("maps equal: " + left.equals(right));
        System.out.println("key sequences equal: " + keys(left).equals(keys(right)));

        Map<String, Integer> copy = new LinkedHashMap<>(left);
        left.remove("a");
        left.put("d", 4);
        System.out.println("left later: " + keys(left));
        System.out.println("copy order: " + keys(copy));

        Map<String, Integer> byAccess = new LinkedHashMap<>(16, 0.75f, true);
        byAccess.put("a", 1);
        byAccess.put("b", 2);
        byAccess.put("c", 3);
        System.out.println("access initial: " + keys(byAccess));
        System.out.println("access get a: " + byAccess.get("a"));
        System.out.println("access after get: " + keys(byAccess));
        System.out.println("access contains b: " + byAccess.containsKey("b"));
        System.out.println("access after contains: " + keys(byAccess));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 OrderSemanticsDemo.java
java OrderSemanticsDemo
```

Stdout trên môi trường đã nêu:

```text
left order: [b, a, c]
right order: [c, a, b]
maps equal: true
key sequences equal: false
left later: [b, c, d]
copy order: [b, a, c]
access initial: [a, b, c]
access get a: 1
access after get: [b, c, a]
access contains b: true
access after contains: [b, c, a]
```

### 9.1. Hai kết quả bằng khác nhau

Left và right có cùng ánh xạ nên maps equal là true. Hai List khóa khác thứ tự nên key sequences equal là false.

Đây là lý do một bài kiểm thử về thứ tự phải kiểm tra dãy có thứ tự, không chỉ gọi equals trên Map.

### 9.2. Bản sao giữ dãy trước khi nguồn đổi

Copy được tạo khi left có b, a, c. Sau đó left bỏ a và thêm d nên thành b, c, d. Copy vẫn có b, a, c.

Các giá trị Integer trong ví dụ không cần xử lý việc sửa đối tượng dùng chung, nhờ vậy ta tập trung quan sát sự độc lập của các ánh xạ.

### 9.3. Get và containsKey khác nhau trong chế độ truy cập

ByAccess bắt đầu với a, b, c. Get a trả 1 và đưa a về cuối, thành b, c, a.

ContainsKey b trả true nhưng không làm b rời đầu chuỗi. Hàm keys chỉ duyệt keySet để tạo List, nên việc in thứ tự cũng không tự phát sinh truy cập làm đổi thứ tự.

Không áp dụng hành vi của byAccess ngược lại cho map mặc định trong chương trình đầu; hai đối tượng có cấu hình thứ tự khác nhau.

## 10. Chi phí của sự kết hợp này

### 10.1. Tra cứu vẫn dựa vào băm

Với hàm băm phân bố phù hợp, các thao tác cơ bản theo khóa có hiệu năng kỳ vọng hằng số. Liên kết thứ tự không biến get thành việc quét toàn chuỗi từ đầu.

Điều kiện phân bố hash vẫn quan trọng. Có thứ tự duyệt không giải quyết một equals/hashCode sai, khóa thay đổi phần định danh hoặc dữ liệu bị dồn quá mức vào một bucket.

### 10.2. Duyệt toàn map đi theo số entry

Iterator theo chuỗi liên kết nên lượt duyệt toàn bộ collection view có chi phí tỉ lệ với size của map, không phải quét mọi bucket theo cách của HashMap.

Ví dụ, một bảng được dành nhiều bucket nhưng chỉ có 100 ánh xạ thì chuỗi thứ tự có 100 entry để thăm. Phần cấu trúc duyệt không cần lần qua từng bucket rỗng.

Tuy nhiên công việc trong thân vòng lặp vẫn có chi phí riêng. “Tỉ lệ với size” không có nghĩa mọi lượt duyệt đều nhanh hơn một cấu trúc khác trong mọi môi trường.

### 10.3. Bộ nhớ và cập nhật có chi phí thêm

Các liên kết trước/sau cần được lưu và cập nhật. Khi thêm, xóa hoặc đổi thứ tự trong chế độ truy cập, cấu trúc phải duy trì chuỗi nhất quán.

Vì vậy không xem LinkedHashMap như một HashMap có thêm thứ tự miễn phí. Kích thước byte cụ thể phụ thuộc bố trí đối tượng và JVM, không nên tự gán một con số cố định cho mọi máy.

Capacity và load factor vẫn ảnh hưởng tới bảng băm, bộ nhớ và việc tăng bảng. Việc iterator không quét bucket rỗng không khiến dành dung lượng cực lớn trở thành vô hại.

### 10.4. Thay đổi bucket không được phá thứ tự đã cam kết

Cài đặt có thể tăng bảng hoặc đổi cách tổ chức một nhóm va chạm. Những việc đó phục vụ cơ chế băm; chúng không cho phép tùy ý đổi thứ tự chèn đã cam kết với người dùng.

Đây là lợi ích của việc tách hai vai trò: cách tìm entry có thể thay đổi bên trong, còn hợp đồng thứ tự vẫn được duy trì.

Kiểm thử theo public API có thể nạp nhiều khóa, dùng cả những khóa va chạm hợp lệ, rồi xác nhận dãy khóa và kết quả tra cứu đều đúng. Không cần kiểm tra tên lớp node nội bộ để chứng minh hợp đồng ấy.

## 11. “Có xác định” cần có đầu vào và lịch sử tương ứng

LinkedHashMap cho biết thứ tự sẽ được duy trì theo quy tắc đã chọn. Nó không tạo ra một thứ tự chuẩn độc lập với mọi lịch sử.

### 11.1. Cùng thao tác cho cùng mô hình thứ tự

Nếu cùng tạo map mặc định rồi lần lượt thêm a, b, c và cập nhật b, ta có cơ sở dự đoán dãy a, b, c.

Nếu đọc dữ liệu từ một nguồn không có hợp đồng thứ tự và đưa vào theo lượt duyệt của nguồn đó, đầu vào cho lịch sử chèn có thể khác nhau. LinkedHashMap vẫn làm đúng quy tắc, nhưng thứ tự giữa những lần xây dựng không nhất thiết giống nhau.

### 11.2. Ổn định trong bộ nhớ chưa bảo đảm thứ tự tới nơi nhận

Chuyển sang một cấu trúc không giữ thứ tự có thể làm mất yêu cầu này. Một thư viện xuất dữ liệu cũng có hợp đồng riêng về thứ tự đầu ra và cách bên nhận diễn giải nó.

Nếu thứ tự là dữ liệu nghiệp vụ, cần mô hình hóa và kiểm tra nó qua toàn bộ đường truyền. Ví dụ, một danh sách có thứ tự có thể phù hợp hơn nếu bên nhận phải đọc các mục theo vị trí.

Không chỉ nhìn một println đúng trong Java rồi kết luận mọi bước xử lý phía sau đều giữ cùng ý nghĩa thứ tự.

### 11.3. Thứ tự không tạo an toàn nhiều luồng

LinkedHashMap không tự đồng bộ hóa truy cập. Khi nhiều luồng cùng thao tác, phải có cách phối hợp thích hợp với yêu cầu của chương trình.

Ở chế độ truy cập, get có thể thay đổi thứ tự nội bộ. Vì vậy không chia thao tác thành “đọc luôn vô hại” và “ghi mới thay cấu trúc” chỉ dựa vào tên phương thức.

Một thứ tự được cam kết trong điều kiện sử dụng đúng không có nghĩa các thao tác đồng thời không được phối hợp sẽ tự tạo lịch sử hợp lệ.

## 12. Null, khóa có thể sửa và giới hạn của “giữ thứ tự”

LinkedHashMap cho phép khóa null và giá trị null. Null như một khóa vẫn chỉ có một ánh xạ; giá trị null có thể xuất hiện ở nhiều khóa.

Trong chế độ chèn, cập nhật giá trị của khóa null đang tồn tại giữ vị trí của khóa đó như các khóa khác.

Get trả null chưa tự chứng minh không có khóa; có thể khóa tồn tại với giá trị null. Nếu cần phân biệt, kiểm tra bằng phương thức phù hợp.

Với khóa có thể sửa, vẫn cần giữ các thuộc tính tham gia equals/hashCode ổn định khi khóa đang được dùng trong map. Chuỗi liên kết có thể còn chứa tham chiếu tới entry không có nghĩa mọi lượt get bằng khóa ấy đều còn đúng nếu quy tắc băm đã bị phá.

Không dùng việc “tôi vẫn nhìn thấy entry khi duyệt” làm bằng chứng khóa đang được thiết kế đúng. Tra cứu và duyệt là hai đường khác nhau; lỗi khóa có thể biểu hiện khác nhau trên hai đường đó.

## 13. Lỗi thường gặp và cách sửa

| Hiểu nhầm | Điều cần sửa |
| --- | --- |
| LinkedHashMap luôn sắp xếp khóa | Xác định thứ tự chèn hay truy cập; nếu cần so sánh khóa thì chọn quy tắc phù hợp |
| Có danh sách liên kết nên get phải O(n) | Phân biệt đường tìm bằng hash với đường iterator |
| Put khóa cũ mặc định đưa nó xuống cuối | Cập nhật ánh xạ còn tồn tại giữ vị trí trong chế độ chèn |
| Xóa rồi thêm lại giống cập nhật tại chỗ | Sau khi xóa, lần thêm lại tạo vị trí chèn mới |
| Values là tập không trùng | Chỉ khóa duy nhất; các giá trị vẫn có thể lặp |
| Map.equals xác nhận thứ tự | So sánh thêm dãy khóa hoặc dãy entry được chụp phù hợp |
| Copy từ HashMap khôi phục thứ tự đã thêm | Bản sao chỉ nhận thứ tự đang được nguồn duyệt |
| Copy giữ nguyên chế độ truy cập của nguồn | Constructor sao chép tạo map theo thứ tự chèn |
| Mọi phương thức đều là một lần truy cập | Đọc hợp đồng access-order; containsKey và duyệt view không có tác dụng ấy |
| Biến có kiểu Map nên đối tượng mất thứ tự | Hành vi của LinkedHashMap vẫn còn; giao diện Map bất kỳ không tự hứa cùng thứ tự |
| Có thứ tự nên an toàn nhiều luồng | Tính đồng bộ là yêu cầu riêng |
| Chuỗi liên kết sửa được khóa sai hash | Hợp đồng khóa vẫn phải được tuân thủ |

## 14. Bài tập tự kiểm tra có lời giải

### Bài 1: Dự đoán thứ tự mặc định

Tạo map mặc định, lần lượt put c, a, b rồi put a lần nữa với giá trị khác. Dãy khóa cuối là gì?

**Lời giải:** c, a, b. Khóa a đang tồn tại nên cập nhật giá trị giữ vị trí.

### Bài 2: Xóa rồi thêm

Từ dãy c, a, b, xóa a rồi thêm a lại. Dãy mới là gì?

**Lời giải:** c, b, a. Ánh xạ a cũ đã bị xóa; lần thêm mới nằm ở cuối.

### Bài 3: Hai giá trị giống nhau

Map theo thứ tự x, y, z có các giá trị 7, 7, 9. Values khi duyệt là gì? Size là bao nhiêu?

**Lời giải:** 7, 7, 9 và size bằng 3. Giá trị trùng không nhập các khóa khác nhau.

### Bài 4: Bảng băm có va chạm

Hai khóa khác nhau có cùng hash hợp lệ. Có bắt buộc chúng phải đứng cạnh nhau khi duyệt LinkedHashMap không?

**Lời giải:** không. Thứ tự của toàn map đi theo quy tắc chèn hoặc truy cập. Nhiều khóa được chèn xen giữa có thể nằm giữa chúng trong chuỗi thứ tự dù không cùng bucket.

### Bài 5: Kiểm tra equals

Hai map có cùng a=1, b=2 nhưng dãy khóa là a,b và b,a. Map.equals có thể true không?

**Lời giải:** có. Nó kiểm tra ánh xạ. Nếu bài kiểm thử đòi thứ tự a,b, cần kiểm tra một dãy có thứ tự tương ứng.

### Bài 6: Sao chép từ nguồn đã sắp xếp

Sao chép một TreeMap có khóa a,c sang LinkedHashMap rồi thêm b. Có chắc bản sao duyệt a,b,c không?

**Lời giải:** không. Bản sao bắt đầu với a,c rồi chèn b vào cuối, nên a,c,b trong chế độ chèn thông thường.

### Bài 7: Hai kiểu đọc

Map access-order đang có a,b,c. Get a thành công, rồi containsKey b. Dãy cuối là gì?

**Lời giải:** b,c,a. Get a đưa a thành mới được truy cập; containsKey b không đưa b xuống cuối.

### Bài 8: Chọn cấu trúc

Cần tra cứu cấu hình theo tên và xuất theo lần xuất hiện đầu tiên của tên; giá trị sau có thể ghi đè giá trị trước. Cấu trúc mặc định nào phù hợp với hai yêu cầu ấy?

**Lời giải:** LinkedHashMap theo thứ tự chèn phù hợp. Put khóa cũ cập nhật giá trị nhưng giữ vị trí lần chèn còn tồn tại, miễn không xóa khóa rồi tạo lại.

## 15. Tài liệu đối chiếu

- [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html): hợp đồng thứ tự, hai chế độ, view, hiệu năng và đồng bộ.
- [LinkedHashMap.java OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/LinkedHashMap.java): liên kết trước/sau, đầu/cuối, cập nhật liên kết và đường iterator.
- [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): hợp đồng ánh xạ, equals, hashCode và các view.
- [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): đối chiếu việc không bảo đảm thứ tự duyệt.
- [TreeMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html): đối chiếu thứ tự theo phép so sánh khóa.

Điểm cần giữ là hai vai trò cùng tồn tại: bảng băm giúp tìm ánh xạ, chuỗi liên kết giúp duyệt theo quy tắc đã chọn. Khi dùng LinkedHashMap, hãy xác định rõ quy tắc thứ tự và kiểm tra nó như một yêu cầu độc lập với nội dung ánh xạ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linked-hash-map-002">Thứ tự chèn và hành vi thay giá trị của khóa đã có</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự chèn và hành vi thay giá trị của khóa đã có

## 1. LinkedHashMap giữ một trật tự bên cạnh các ánh xạ

Một map lưu ánh xạ từ khóa đến giá trị. `LinkedHashMap` còn duy trì một thứ tự duyệt xác định. Với chế độ mặc định, đó là **thứ tự chèn (insertion order)**.

Giả sử lần lượt thêm các khóa `B`, `A`, `C`:

```text
put("B", 2)
put("A", 1)
put("C", 3)

Thứ tự duyệt khóa: B → A → C
```

Kết quả không tự thành `A → B → C`, vì thứ tự chèn không phải thứ tự sắp xếp theo khóa.

Nếu sau đó gọi `put("A", 100)`, map đổi giá trị của `A` nhưng vẫn duyệt theo:

```text
B → A → C
```

Khóa `A` không tự chuyển xuống cuối chỉ vì được ghi lại. Đây là quy tắc chính của bài: **trong LinkedHashMap theo thứ tự chèn, thay giá trị một khóa đang có giữ nguyên vị trí của khóa đó**.

### Vì sao hành vi này hữu ích?

Một báo cáo có thể muốn giữ thứ tự các trường được thêm ban đầu, nhưng vẫn cho sửa số liệu của từng trường. Một bộ cấu hình có thể giữ thứ tự hiển thị đã thiết lập, trong khi các giá trị được cập nhật nhiều lần.

`LinkedHashMap` theo thứ tự chèn đáp ứng dạng yêu cầu “giữ vị trí đã có, cập nhật nội dung”. Nếu ứng dụng muốn thứ tự theo lần sửa gần nhất, đó là một yêu cầu khác cần thiết kế rõ.

Các ví dụ dưới dùng `LinkedHashMap` thông thường, không ghi đè chính sách tự loại bỏ phần tử. Trừ phần đối chiếu chế độ ở cuối, mọi map đều dùng thứ tự chèn mặc định và API tương thích Java 17.

## 2. Thứ tự chèn chính xác là thứ tự của lần tạo ánh xạ đang tồn tại

Cần phân biệt ba sự kiện:

| Sự kiện | Có tạo ánh xạ mới không? | Tác động lên thứ tự chèn |
|---|---|---|
| `put` với khóa chưa có | Có | Thêm khóa ở cuối |
| `put` với khóa đang có | Không; thay giá trị | Giữ vị trí |
| `remove` rồi `put` lại khóa đó | Có một lần chèn mới sau khi đã xóa | Khóa xuất hiện ở cuối khi chèn lại |

Cụm “thứ tự chèn” không có nghĩa map nhớ mãi vị trí của lần xuất hiện đầu tiên trong toàn bộ lịch sử chương trình. Khi ánh xạ bị xóa, vị trí ấy không được dành chỗ để phục hồi tự động về sau.

Ví dụ:

```text
Ban đầu:           B → A → C
put("A", 100):     B → A → C
remove("A"):       B → C
put("A", 200):     B → C → A
```

`put` vào khóa đang tồn tại và xóa–chèn lại là hai chuỗi thay đổi khác nhau, dù cuối cùng đều có thể để lại cùng giá trị cho khóa đó.

### Vị trí không phải chỉ số để truy cập như List

Một `LinkedHashMap` có thứ tự duyệt, nhưng không vì thế trở thành một danh sách hỗ trợ `get(0)` để lấy phần tử đầu.

`map.get(key)` vẫn là tra theo khóa. Nếu khóa là số nguyên `0`, `get(0)` tìm ánh xạ của khóa `0`, không phải “phần tử ở vị trí số 0”.

Khi cần một danh sách khóa có chỉ số, có thể tạo một `List` riêng từ `keySet`; đó là một cấu trúc khác với map.

## 3. Vì sao thay giá trị không làm đổi vị trí?

Về mô hình, `LinkedHashMap` kết hợp khả năng tìm khóa của map băm với một chuỗi liên kết qua các ánh xạ để xác định thứ tự duyệt.

Có thể hình dung:

```text
Thứ tự:   [B] ⇄ [A] ⇄ [C]
Giá trị:   2     1     3
```

Sau khi thay giá trị của `A`:

```text
Thứ tự:   [B] ⇄ [A] ⇄ [C]
Giá trị:   2    100    3
```

Ánh xạ logic của `A` đã có, nên việc thay nội dung không phải lần tạo một vị trí mới ở cuối.

Khi thêm `D` mới:

```text
Thứ tự:   [B] ⇄ [A] ⇄ [C] ⇄ [D]
```

Khi xóa `A`, nó không còn trong chuỗi thứ tự. Chèn lại sau đó tạo vị trí ở cuối, không phải phục hồi khoảng trống cũ.

Đây là mô hình giải thích, không yêu cầu ứng dụng tự quản lý các liên kết nội bộ. Việc mở rộng bảng băm hoặc phân bố bucket không tự biến thứ tự chèn thành thứ tự theo mã băm hay thứ tự khóa.

### Có thứ tự không thay thế hợp đồng khóa

`LinkedHashMap` vẫn cần một định nghĩa khóa đúng. Hai đối tượng khóa khác nhau nhưng bằng nhau theo `equals` và nhất quán về `hashCode` đại diện cùng một khóa logic.

Vì vậy `put(new String("A"), 100)` không tự tạo một vị trí mới nếu đang có khóa chuỗi `"A"`. Đối tượng mới được cấp phát khác với việc có một khóa logic mới.

Cũng không dùng cách sửa thông tin định danh của đối tượng khóa để “đổi vị trí” hoặc “đổi tên” khóa. Khóa phải giữ hợp đồng ổn định trong thời gian làm khóa; thứ tự có thể dự đoán không chữa được một khóa làm sai cơ chế tìm kiếm.

## 4. Chương trình đầy đủ: ghi lại, đọc, xóa và chèn lại

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class LinkedHashMapInsertionDemo {
    public static void main(String[] args) {
        Map<String, Integer> numbers = new LinkedHashMap<>();
        numbers.put("B", 2);
        numbers.put("A", 1);
        numbers.put("C", 3);
        System.out.println("initialKeys=" + numbers.keySet());
        System.out.println("initialValues=" + numbers.values());

        Integer old = numbers.put(new String("A"), 100);
        System.out.println("replacementOld=" + old);
        System.out.println("afterReplaceKeys=" + numbers.keySet());
        System.out.println("afterReplaceMap=" + numbers);
        System.out.println("sizeAfterReplace=" + numbers.size());

        System.out.println("sameValueOld=" + numbers.put("B", 2));
        System.out.println("afterSameValueKeys=" + numbers.keySet());
        System.out.println("readB=" + numbers.get("B"));
        System.out.println("afterReadKeys=" + numbers.keySet());

        numbers.put("D", 4);
        System.out.println("afterNewKey=" + numbers.keySet());
        System.out.println("removedA=" + numbers.remove("A"));
        System.out.println("afterRemove=" + numbers.keySet());
        numbers.put("A", 200);
        System.out.println("afterReinsert=" + numbers.keySet());
        System.out.println("finalMap=" + numbers);

        Map<String, Integer> nullable = new LinkedHashMap<>();
        nullable.put(null, null);
        nullable.put("X", 1);
        System.out.println("nullKeyOld=" + nullable.put(null, 2));
        System.out.println("nullableKeys=" + nullable.keySet());
        System.out.println("nullableSize=" + nullable.size());
        System.out.println("nullKeyNow=" + nullable.get(null));
    }
}
```

Lưu thành `LinkedHashMapInsertionDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 LinkedHashMapInsertionDemo.java
java LinkedHashMapInsertionDemo
```

Đầu ra chính xác:

```text
initialKeys=[B, A, C]
initialValues=[2, 1, 3]
replacementOld=1
afterReplaceKeys=[B, A, C]
afterReplaceMap={B=2, A=100, C=3}
sizeAfterReplace=3
sameValueOld=2
afterSameValueKeys=[B, A, C]
readB=2
afterReadKeys=[B, A, C]
afterNewKey=[B, A, C, D]
removedA=100
afterRemove=[B, C, D]
afterReinsert=[B, C, D, A]
finalMap={B=2, C=3, D=4, A=200}
nullKeyOld=null
nullableKeys=[null, X]
nullableSize=2
nullKeyNow=2
```

### 4.1. Thứ tự chèn không phải thứ tự chữ cái

`initialKeys=[B, A, C]` đúng với ba lần chèn ban đầu. `initialValues=[2, 1, 3]` là các giá trị theo cùng thứ tự ánh xạ, không được sắp xếp theo độ lớn.

### 4.2. Khóa bằng nhau thay cùng một ánh xạ

Lần ghi `new String("A")` trả giá trị cũ `1`, rồi map có `A=100` tại vị trí cũ:

```text
afterReplaceKeys=[B, A, C]
afterReplaceMap={B=2, A=100, C=3}
sizeAfterReplace=3
```

Không cần dùng đúng tham chiếu chuỗi đã chèn trước đó để giữ cùng khóa logic. Điều này dựa trên hợp đồng khóa của kiểu `String`.

Không nên từ đó suy ra map bắt buộc thay đối tượng khóa lưu bên trong bằng chính tham chiếu mới truyền vào. Điều đang được kiểm chứng là ánh xạ, giá trị và thứ tự của khóa logic.

### 4.3. Ghi cùng giá trị và đọc cũng không đẩy khóa xuống cuối

`put("B", 2)` trả `2` dù giá trị mới bằng giá trị cũ. Nó vẫn không chuyển `B` xuống cuối.

`get("B")` đọc giá trị hiện tại và giữ thứ tự trong chế độ chèn. Không có quy tắc “khóa vừa dùng được đưa xuống cuối” cho chế độ mặc định này.

### 4.4. Khóa mới nối cuối; khóa xóa rồi thêm lại cũng nối cuối

Thêm `D` tạo `[B, A, C, D]`. Xóa `A` để lại `[B, C, D]`. Chèn lại `A` tạo `[B, C, D, A]`.

Giá trị cuối của `A` là `200`, nhưng vị trí cuối đến từ việc ánh xạ đã bị xóa rồi được tạo mới. Không phải mọi lần cập nhật giá trị `200` đều mang cùng tác động lên thứ tự.

### 4.5. Giá trị cũ null không có nghĩa khóa chưa có

Map `nullable` có ánh xạ khóa null với giá trị null trước khi thêm `X`. Lần `put(null, 2)` trả null nhưng vẫn là thay giá trị của ánh xạ đã tồn tại:

```text
nullableKeys=[null, X]
nullableSize=2
nullKeyNow=2
```

Khóa null giữ vị trí trước `X`. Việc nhận kết quả cũ null không đủ để kết luận đây là lần chèn một khóa mới. Nếu cần phân biệt có ánh xạ hay chưa, phải xét sự tồn tại của khóa, không chỉ nhìn `get` hoặc giá trị cũ trả về.

## 5. Bảng hành vi ở chế độ thứ tự chèn

Bảng sau giả sử một `LinkedHashMap` sửa được, thao tác hợp lệ và không có nơi khác thay đổi map:

| Thao tác | Giá trị có thể đổi không? | Thứ tự của khóa đã có |
|---|---|---|
| `put(k, newValue)` khi `k` đang có | Có | Giữ nguyên |
| `put(k, sameValue)` khi `k` đang có | Có thể không đổi về nghĩa dữ liệu | Giữ nguyên |
| `get(k)` | Không thay ánh xạ | Giữ nguyên |
| `replace(k, newValue)` khi thay được | Có | Giữ nguyên |
| `entry.setValue(newValue)` trên entry hợp lệ | Có | Giữ nguyên |
| Thêm một khóa mới | Có ánh xạ mới | Khóa mới ở cuối |
| Xóa một khóa | Mất ánh xạ | Khóa không còn vị trí |
| Chèn lại khóa đã bị xóa | Có ánh xạ mới | Ở cuối tại lần chèn lại |

Những phương thức tính/cập nhật khác cũng cần được đọc theo hiệu ứng thật của chúng. Nếu kết quả làm **xóa** ánh xạ, không thể coi đó là một lần “chỉ thay giá trị” rồi mặc định vị trí sẽ được giữ cho tương lai.

Nếu giá trị là một đối tượng khả biến, sửa nội dung đối tượng đó không tự thay vị trí của khóa trong map. Đó là thay dữ liệu của đối tượng giá trị, khác với thêm hoặc xóa ánh xạ.

## 6. putAll: giữ vị trí khóa cũ, nối các khóa mới theo lượt duyệt nguồn

Với map đích theo thứ tự chèn, có thể hiểu `putAll(source)` theo hai nhóm:

1. Khóa nguồn đã có ở đích: thay giá trị, giữ vị trí của đích.
2. Khóa nguồn chưa có ở đích: tạo ánh xạ mới, nối cuối theo thứ tự mà nguồn cung cấp các ánh xạ.

Ví dụ:

```text
Đích trước:       B → A → C
Nguồn duyệt:      A → E → D

Đích sau putAll:  B → A → C → E → D
```

`A` đã có nên không được chuyển ra cuối trước `E`, `D`. Chỉ hai khóa mới nối vào sau chuỗi cũ.

Điều này có ích khi một cấu hình nền đã có thứ tự hiển thị, rồi một tập cập nhật ghi đè một số giá trị và bổ sung mục mới. Nó không tự sắp xếp toàn bộ đích theo thứ tự của nguồn.

### Nguồn cần có thứ tự được xác định nếu thứ tự mục mới là yêu cầu

Nếu nguồn là một `LinkedHashMap` mặc định và được giữ ổn định, có thể dùng thứ tự nguồn để dự đoán các khóa mới nối vào.

Nếu nguồn là `HashMap`, không được suy rằng các khóa sẽ được lấy theo thứ tự lịch sử các lần `put` của nguồn. Đích chỉ tiếp nhận thứ tự duyệt mà nguồn đang cung cấp, không thể tự khôi phục một lịch sử chưa được giữ.

Cũng không nên dùng một `Map.of` có nhiều mục rồi lấy thứ tự các đối số làm một cam kết thứ tự duyệt. Nếu thứ tự là dữ liệu cần giữ, hãy chọn nguồn và quy trình xây nguồn có hợp đồng phù hợp.

## 7. Chương trình đầy đủ: putAll và bản chép có thứ tự

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LinkedHashMapPutAllCopyDemo {
    public static void main(String[] args) {
        Map<String, Integer> target = new LinkedHashMap<>();
        target.put("B", 2);
        target.put("A", 1);
        target.put("C", 3);

        Map<String, Integer> source = new LinkedHashMap<>();
        source.put("A", 10);
        source.put("E", 5);
        source.put("D", 4);

        target.putAll(source);
        System.out.println("sourceOrder=" + source.keySet());
        System.out.println("targetOrder=" + target.keySet());
        System.out.println("targetValues=" + target.values());
        System.out.println("targetMap=" + target);

        Map<String, Integer> copied = new LinkedHashMap<>(target);
        target.put("A", 100);
        target.put("F", 6);
        System.out.println("sourceMapAfterChange=" + target.keySet());
        System.out.println("copyOrder=" + copied.keySet());
        System.out.println("copyA=" + copied.get("A"));
        System.out.println("sourceA=" + target.get("A"));

        Map<String, Integer> unordered = new HashMap<>();
        unordered.put("orange", 1);
        unordered.put("apple", 2);
        unordered.put("banana", 3);

        List<String> observedOrder = new ArrayList<>(unordered.keySet());
        Map<String, Integer> fromUnordered = new LinkedHashMap<>(unordered);
        System.out.println("copyMatchesObservedSourceOrder="
                + new ArrayList<>(fromUnordered.keySet()).equals(observedOrder));
    }
}
```

Lưu thành `LinkedHashMapPutAllCopyDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 LinkedHashMapPutAllCopyDemo.java
java LinkedHashMapPutAllCopyDemo
```

Đầu ra chính xác:

```text
sourceOrder=[A, E, D]
targetOrder=[B, A, C, E, D]
targetValues=[2, 10, 3, 5, 4]
targetMap={B=2, A=10, C=3, E=5, D=4}
sourceMapAfterChange=[B, A, C, E, D, F]
copyOrder=[B, A, C, E, D]
copyA=10
sourceA=100
copyMatchesObservedSourceOrder=true
```

### 7.1. Thứ tự khóa cũ thuộc đích

Nguồn có thứ tự `[A, E, D]`, còn đích sau chép là `[B, A, C, E, D]`. `A` nhận giá trị `10` ở vị trí cũ. `E` và `D` được thêm ở cuối theo thứ tự nguồn.

Các giá trị `[2, 10, 3, 5, 4]` thuộc các khóa theo chính thứ tự mới ấy.

### 7.2. Constructor chép giữ thứ tự quan sát ở thời điểm chép

`new LinkedHashMap<>(target)` tạo map mới với các ánh xạ theo thứ tự mà nguồn được duyệt lúc chép. Sau đó nguồn thay `A` thành `100` và thêm `F`; bản chép vẫn giữ chuỗi khóa cũ cùng giá trị `A=10`.

Bản chép không phải view sống của nguồn. Đây là chép cấu trúc ánh xạ, không phải sao chép sâu mọi đối tượng khóa hoặc giá trị.

### 7.3. Chép HashMap không khôi phục lịch sử chèn

Phần cuối ghi lại thứ tự đang quan sát từ `HashMap`, sau đó chép ngay sang `LinkedHashMap` trong khi nguồn không bị thay đổi.

`copyMatchesObservedSourceOrder=true` nói bản chép giữ thứ tự đã được nguồn cung cấp ở lượt đó. Chương trình không in hoặc khẳng định một thứ tự chữ cái hay thứ tự chèn cụ thể của `HashMap`.

Tạo đích có thứ tự giúp duy trì một chuỗi từ thời điểm chép trở đi. Nó không bổ sung ngược dữ liệu lịch sử mà nguồn không bảo đảm.

## 8. Thứ tự được quan sát qua các view và forEach

Với một map đang giữ thứ tự `B → A → C`:

| Cách duyệt | Dữ liệu quan sát |
|---|---|
| `keySet()` | Khóa theo thứ tự `B`, `A`, `C` |
| `values()` | Giá trị ứng với `B`, rồi `A`, rồi `C` |
| `entrySet()` | Các cặp ánh xạ theo thứ tự đó |
| `Map.forEach` | Hành động được thực hiện trên các ánh xạ theo thứ tự duyệt đã xác định |

Thay giá trị của `A` làm phần giá trị tương ứng thay đổi khi duyệt `values` hoặc `entrySet`, nhưng không đẩy vị trí của `A` ra nơi khác trong chế độ chèn.

`keySet` có kiểu `Set` không có nghĩa iterator của nó buộc phải không có thứ tự. Một set view có thể duyệt theo thứ tự được map hỗ trợ, trong khi định nghĩa bằng nhau của `Set` vẫn không xét thứ tự. Đây là hai khía cạnh khác nhau.

### View sống khác danh sách đã chép

Giả sử đã lưu một view khóa và một danh sách riêng:

```java
// Minh họa với first là LinkedHashMap theo thứ tự chèn.
Set<String> keysView = first.keySet();
List<String> copiedKeys = new ArrayList<>(keysView);
```

Khi thêm khóa mới vào map rồi bắt đầu một lượt duyệt mới:

- `keysView` phản ánh khóa mới.
- `copiedKeys` không tự có phần tử mới vì nó là danh sách đã chép.

Tương tự, view giá trị nhìn thấy giá trị vừa thay. Không nên gọi một view là “ảnh chụp của thứ tự lúc lấy view”.

Các câu trên không cho phép tùy ý thay đổi cấu trúc trong khi một iterator cũ đang hoạt động. View sống và quyền sửa trong lúc duyệt là hai vấn đề riêng.

### Thay giá trị khi đang duyệt

Trong chế độ chèn, chỉ thay giá trị của khóa đang có không được coi là thay đổi cấu trúc thứ tự như thêm/xóa ánh xạ. Khi đang duyệt `entrySet`, cách trực tiếp là dùng `entry.setValue` trên entry hợp lệ mà hiện thực hỗ trợ.

Không dùng điều này để suy rằng có thể `remove` rồi `put` bên ngoài iterator ngay trong vòng lặp. Chuỗi đó sửa cấu trúc và thay đổi vị trí. Nếu cần xóa phần tử hiện tại khi duyệt, dùng cơ chế xóa của chính iterator khi được hỗ trợ.

## 9. Map.equals không kiểm tra thứ tự chèn

Hai map có thể chứa cùng ánh xạ nhưng duyệt theo hai thứ tự khác nhau:

```text
Map thứ nhất: B → A → C
Map thứ hai: C → A → B

Mỗi khóa ở cả hai map vẫn có cùng giá trị tương ứng.
```

Theo hợp đồng chung, `Map.equals` xét các ánh xạ chứ không thêm thứ tự chèn vào tiêu chí bằng nhau. Mã băm của map cũng không phải một phép mã hóa riêng của thứ tự duyệt.

Vì thế muốn kiểm tra kết quả có đúng thứ tự hay không, hãy kiểm tra một chuỗi có xét thứ tự, chẳng hạn danh sách khóa:

```java
// Minh họa với hai map ổn định trong khi so sánh.
boolean sameMappings = first.equals(second);
boolean sameKeyOrder =
        new ArrayList<>(first.keySet())
                .equals(new ArrayList<>(second.keySet()));
```

| Phép so | Điều được so |
|---|---|
| `first.equals(second)` | Các ánh xạ khóa–giá trị |
| `first.keySet().equals(second.keySet())` | Tập khóa, không xét thứ tự |
| `first.entrySet().equals(second.entrySet())` | Tập cặp ánh xạ, không xét thứ tự |
| So hai `ArrayList` chép từ `keySet` | Chuỗi khóa theo thứ tự |

Nếu yêu cầu là vừa cùng dữ liệu vừa cùng thứ tự, phải xét cả hai khía cạnh. Đừng chỉ so `keySet` như một set rồi cho rằng thứ tự đã được kiểm chứng.

Cũng không mặc định một `Collection` giá trị bất kỳ có phép `equals` giống `List` để so chuỗi. Tạo rõ kiểu danh sách khi mục đích là kiểm tra thứ tự phần tử.

## 10. Chương trình đầy đủ: view, thay giá trị và kiểm tra thứ tự

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LinkedHashMapViewsEqualityDemo {
    public static void main(String[] args) {
        Map<String, Integer> first = new LinkedHashMap<>();
        first.put("B", 2);
        first.put("A", 1);
        first.put("C", 3);

        Set<String> keysView = first.keySet();
        Collection<Integer> valuesView = first.values();
        List<String> copiedKeys = new ArrayList<>(keysView);

        first.put("A", 10);
        System.out.println("keysAfterUpdate=" + keysView);
        System.out.println("valuesAfterUpdate=" + valuesView);

        List<String> lines = new ArrayList<>();
        first.forEach((key, value) -> lines.add(key + "=" + value));
        System.out.println("forEachLines=" + lines);

        for (Map.Entry<String, Integer> entry : first.entrySet()) {
            if (entry.getKey().equals("B")) {
                Integer old = entry.setValue(20);
                System.out.println("entryOld=" + old);
            }
        }
        System.out.println("afterEntryUpdate=" + first);

        Map<String, Integer> reversed = new LinkedHashMap<>();
        reversed.put("C", 3);
        reversed.put("A", 10);
        reversed.put("B", 20);
        System.out.println("sameMappings=" + first.equals(reversed));
        System.out.println("sameMapHash="
                + (first.hashCode() == reversed.hashCode()));
        System.out.println("sameKeyOrder="
                + new ArrayList<>(first.keySet()).equals(
                        new ArrayList<>(reversed.keySet())));

        first.put("D", 4);
        System.out.println("liveKeysAfterAppend=" + keysView);
        System.out.println("liveValuesAfterAppend=" + valuesView);
        System.out.println("copiedKeys=" + copiedKeys);
    }
}
```

Lưu thành `LinkedHashMapViewsEqualityDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 LinkedHashMapViewsEqualityDemo.java
java LinkedHashMapViewsEqualityDemo
```

Đầu ra chính xác:

```text
keysAfterUpdate=[B, A, C]
valuesAfterUpdate=[2, 10, 3]
forEachLines=[B=2, A=10, C=3]
entryOld=2
afterEntryUpdate={B=20, A=10, C=3}
sameMappings=true
sameMapHash=true
sameKeyOrder=false
liveKeysAfterAppend=[B, A, C, D]
liveValuesAfterAppend=[20, 10, 3, 4]
copiedKeys=[B, A, C]
```

### 10.1. Giá trị đổi tại vị trí tương ứng

Sau khi cập nhật `A` thành `10`:

```text
keysAfterUpdate=[B, A, C]
valuesAfterUpdate=[2, 10, 3]
forEachLines=[B=2, A=10, C=3]
```

Thứ tự khóa giữ nguyên, giá trị của ánh xạ thứ hai đổi. Các dòng thu qua `forEach` tuân theo cùng trật tự.

Lượt duyệt entry tiếp theo thay giá trị `B` bằng `entry.setValue(20)`; phương thức trả giá trị cũ `2`. Sau đó map vẫn theo thứ tự `B, A, C`.

### 10.2. Hai map bằng nhau nhưng chuỗi khóa khác nhau

Map `reversed` được chèn theo thứ tự `C, A, B` và có cùng ánh xạ với map `first`. Kết quả:

```text
sameMappings=true
sameMapHash=true
sameKeyOrder=false
```

Ba dòng cùng đúng, không có mâu thuẫn. Chúng đang kiểm tra các khía cạnh khác nhau của dữ liệu.

### 10.3. View phản ánh lần thêm sau

Thêm `D` làm view khóa thành `[B, A, C, D]`, view giá trị thành `[20, 10, 3, 4]`. Danh sách `copiedKeys` vẫn là `[B, A, C]`.

Không có một iterator đang mở bị sửa từ ngoài trong ví dụ; chương trình thay đổi map giữa các lượt quan sát rồi duyệt lại view.

## 11. Chế độ thứ tự truy cập là một lựa chọn khác

`LinkedHashMap` còn có constructor cho phép chọn thứ tự truy cập:

```java
// Minh họa cách tạo; chưa phải chương trình độc lập.
Map<String, Integer> insertion = new LinkedHashMap<>();
Map<String, Integer> access =
        new LinkedHashMap<>(16, 0.75f, true);
```

Tham số thứ ba `accessOrder` có ý nghĩa:

| Giá trị | Chế độ |
|---|---|
| `false` | Thứ tự chèn |
| `true` | Thứ tự truy cập |

Các constructor thông thường không chọn `true` tạo map theo thứ tự chèn. Trong chế độ truy cập, một số lời gọi như `get` và `put` được tính là truy cập vào entry; khóa có thể được chuyển về phía cuối theo quy tắc của chế độ này.

Vì vậy phát biểu “ghi lại khóa không đổi thứ tự” trong bài phải gắn với **chế độ chèn và thao tác put thông thường**. Không áp nguyên xi cho một đối tượng đã được tạo theo `accessOrder=true`.

### Chương trình đầy đủ đối chiếu hai chế độ

```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LinkedHashMapOrderModesDemo {
    public static void main(String[] args) {
        Map<String, Integer> insertion = new LinkedHashMap<>();
        Map<String, Integer> access =
                new LinkedHashMap<>(16, 0.75f, true);

        int value = 1;
        for (String key : List.of("A", "B", "C")) {
            insertion.put(key, value);
            access.put(key, value);
            value++;
        }

        System.out.println("insertionBefore=" + insertion.keySet());
        System.out.println("accessBefore=" + access.keySet());

        insertion.put("B", 20);
        access.put("B", 20);
        System.out.println("insertionAfterPutB=" + insertion.keySet());
        System.out.println("accessAfterPutB=" + access.keySet());

        insertion.get("A");
        access.get("A");
        System.out.println("insertionAfterGetA=" + insertion.keySet());
        System.out.println("accessAfterGetA=" + access.keySet());
    }
}
```

Lưu thành `LinkedHashMapOrderModesDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 LinkedHashMapOrderModesDemo.java
java LinkedHashMapOrderModesDemo
```

Đầu ra chính xác:

```text
insertionBefore=[A, B, C]
accessBefore=[A, B, C]
insertionAfterPutB=[A, B, C]
accessAfterPutB=[A, C, B]
insertionAfterGetA=[A, B, C]
accessAfterGetA=[C, B, A]
```

### Cùng lời gọi, khác chính sách thứ tự

Hai map cùng bắt đầu với `[A, B, C]`.

Sau `put("B", 20)`:

- Map thứ tự chèn vẫn `[A, B, C]`.
- Map thứ tự truy cập thành `[A, C, B]`.

Sau `get("A")`:

- Map thứ tự chèn vẫn `[A, B, C]`.
- Map thứ tự truy cập thành `[C, B, A]`.

Khác biệt không do giá trị `20` lớn hơn hay nhỏ hơn các giá trị khác. Nó đến từ chế độ được chọn khi tạo map.

Đừng tự dùng thứ tự truy cập như thể đó là “thứ tự lần sửa gần nhất” nếu ứng dụng còn đọc dữ liệu: một phép đọc cũng có thể làm thay đổi thứ tự. Hãy xác định rõ sự kiện nào trong nghiệp vụ phải ảnh hưởng vị trí.

### Cẩn thận khi đưa get vào vòng duyệt

Trong chế độ chèn, `get` không chuyển khóa theo thứ tự truy cập. Trong chế độ truy cập, lời gọi đọc có thể thay chính thứ tự đang duyệt.

Nếu đã có entry trong tay, đọc `entry.getValue()` thể hiện ý định trực tiếp hơn so với lại gọi `map.get(entry.getKey())`. Không được suy rằng mọi lời gọi đọc trên mọi map đều không có ảnh hưởng tới trạng thái thứ tự.

## 12. Khi muốn chủ động chuyển một khóa xuống cuối

Với API Java 17 và một map theo thứ tự chèn, xóa rồi chèn lại là một cách tạo thứ tự mới. Nhưng phải biết đó là hai thao tác riêng, không chỉ là đổi giá trị.

Đoạn minh họa dưới giả sử `numbers` là `LinkedHashMap<String, Integer>` theo thứ tự chèn, sửa được, do một luồng sở hữu và không có iterator đang mở:

```java
String key = "A";
if (numbers.containsKey(key)) {
    Integer value = numbers.remove(key);
    numbers.put(key, value);
}
```

Kiểm tra sự tồn tại có mục đích: nếu khóa có giá trị null thì vẫn cần giữ ánh xạ khi chuyển vị trí. Chỉ viết `if (value != null)` sau `remove` có thể làm mất một ánh xạ null. Ngược lại, xóa một khóa vốn không có rồi vô điều kiện `put` kết quả null có thể vô tình tạo thêm ánh xạ.

Cặp xóa–chèn lại có các giới hạn cần hiểu:

- Nó thay đổi cấu trúc map và vị trí duyệt.
- Nó không tự là giao dịch nguyên tử cho nhiều luồng.
- Nếu có ngoại lệ hoặc quy tắc riêng về thay đổi dữ liệu, ứng dụng cần xử lý theo yêu cầu của mình.
- Không thực hiện tùy ý giữa một vòng `for-each` đang duyệt map; thay đổi cấu trúc ngoài iterator có thể vi phạm hợp đồng duyệt.

Các iterator fail-fast có thể phát hiện sai sử dụng bằng `ConcurrentModificationException`, nhưng không được dựa vào việc một lần chạy không có ngoại lệ để kết luận cách sửa trong lúc duyệt là hợp lệ. Việc giữ thứ tự dự đoán được cũng không làm `LinkedHashMap` trở thành cấu trúc tự đồng bộ cho nhiều luồng.

### Ranh giới phiên bản: phương thức đặt vị trí tường minh

Từ Java 21, `LinkedHashMap` có các API như `putFirst` và `putLast` để đặt vị trí tường minh, có thể đổi vị trí của entry đã có. Chúng khác `put` thông thường. Quy tắc của từng phương thức phải được đọc theo API tương ứng.

Các chương trình chạy đầy đủ trong bài dùng API tương thích Java 17; phần nhắc này giúp tránh khái quát thành “không có thao tác nào trên LinkedHashMap có thể đổi vị trí khóa đã tồn tại”.

## 13. Áp dụng vào dữ liệu cập nhật nhiều lần

Một tình huống thường gặp là cần giữ thứ tự xuất hiện của các mã trường nhưng dùng giá trị cuối cùng của từng mã:

```text
Các lần cập nhật:
B=1, A=2, B=3, C=4, A=5

Thứ tự chèn của các khóa còn tồn tại:
B → A → C

Giá trị cuối cùng:
B=3, A=5, C=4
```

Chỉ cần gọi `put` cho từng lần cập nhật vào map theo thứ tự chèn, ta giữ vị trí ban đầu của từng khóa đang tồn tại và thay giá trị khi khóa đó xuất hiện lại.

Điều này **không giữ toàn bộ lịch sử thay đổi**. Các giá trị trước của cùng khóa bị thay; nếu cần nhật ký mọi lần sửa, phải lưu lịch sử ở một cấu trúc phù hợp khác.

Nó cũng không tự tạo thứ tự “sửa gần nhất”: ở ví dụ, `A` được cập nhật cuối nhưng không chuyển thành khóa cuối.

### Từ nơi tạo dữ liệu đến nơi dùng dữ liệu

Khai báo:

```java
Map<String, Integer> data = new LinkedHashMap<>();
```

không làm mất hành vi thứ tự của đối tượng `LinkedHashMap` đang tồn tại. Kiểu biến là `Map`, còn đối tượng thực vẫn giữ chính sách thứ tự đã chọn.

Tuy nhiên, một phương thức chỉ hứa trả `Map` bất kỳ thì người gọi không nên tự giả định mọi hiện thực đều giữ thứ tự chèn. Nếu thứ tự là yêu cầu của giao tiếp giữa các phần, hãy mô tả rõ nó trong hợp đồng.

Nếu chuyển dữ liệu qua một bước trung gian, cũng phải xem bước đó có giữ thứ tự không. Chẳng hạn, `Map.copyOf` không cung cấp cam kết giữ thứ tự chèn chỉ vì nguồn là `LinkedHashMap`. Một bản chép bằng `LinkedHashMap` và một map không cho sửa từ factory có các hợp đồng khác nhau.

Khi xuất các dòng báo cáo hoặc xây giao diện, cần để quá trình tiêu thụ đi theo thứ tự đã giữ. Việc chọn đúng cấu trúc nguồn chưa đủ nếu bước sau tự sắp xếp hoặc đổi sang một cấu trúc không hứa giữ thứ tự.

## 14. Lỗi thường gặp

| Cách hiểu sai | Cách hiểu theo đúng chế độ chèn |
|---|---|
| Khóa được ghi lại sẽ tự xuống cuối | `put` vào khóa đang có giữ vị trí |
| Giá trị lớn hơn sẽ được đẩy về sau | Không tự sắp xếp theo giá trị |
| Chèn B, A, C sẽ duyệt A, B, C | Duyệt B, A, C theo thứ tự chèn |
| Xóa rồi chèn lại trở về vị trí lịch sử cũ | Chèn lại tạo vị trí mới ở cuối |
| Giá trị cũ trả về null nghĩa là khóa mới | Có thể khóa cũ đã mang giá trị null |
| Map.equals đủ kiểm tra thứ tự | Nó xét ánh xạ; dùng thêm phép so chuỗi |
| keySet là Set nên không thể có thứ tự duyệt | View vẫn có iterator theo thứ tự map hỗ trợ |
| Chép HashMap sang LinkedHashMap phục hồi thứ tự các lần put cũ | Chỉ giữ thứ tự nguồn cung cấp lúc chép |
| LinkedHashMap luôn dùng thứ tự chèn trong mọi cấu hình | Có chế độ thứ tự truy cập được chọn riêng |
| Có thứ tự đồng nghĩa có API truy cập theo chỉ số | `get` vẫn nhận khóa |

## 15. Tự kiểm tra bằng chuỗi thao tác

Các câu dưới giả sử map theo thứ tự chèn, không có thao tác định vị tường minh hay chính sách tự loại bỏ entry.

### Câu 1: Thay giá trị ở giữa

Map có thứ tự `A, B, C`. Gọi `put("B", 99)`. Thứ tự và kích thước có đổi không?

**Đáp án:** Thứ tự vẫn `A, B, C`; kích thước không tăng. Giá trị của `B` đổi thành `99`.

### Câu 2: Ghi cùng giá trị

`B` đang có giá trị `99`; gọi lại `put("B", 99)`. Có phải khóa bị đưa xuống cuối vì đây là một lần ghi mới không?

**Đáp án:** Không. Khóa logic đã tồn tại, nên không tạo vị trí mới trong thứ tự chèn.

### Câu 3: Đối tượng khóa mới nhưng equals

Map chứa khóa chuỗi `"B"`. Gọi `put(new String("B"), 7)`. Có chắc thêm một vị trí thứ hai cho B không?

**Đáp án:** Không. Hai chuỗi bằng nhau theo nội dung nên là cùng khóa logic, với hợp đồng băm nhất quán của `String`.

### Câu 4: Xóa rồi chèn

Map theo thứ tự `A, B, C`; xóa `B`, rồi chèn lại `B`. Thứ tự cuối là gì?

**Đáp án:** `A, C, B`. Vị trí cũ không được dành lại sau khi ánh xạ bị xóa.

### Câu 5: Khóa mới

Map theo thứ tự `C, A`. Thêm khóa mới `B`. Có tự thành `A, B, C` không?

**Đáp án:** Không. Kết quả là `C, A, B`. Thứ tự chèn khác thứ tự chữ cái.

### Câu 6: putAll

Đích có `A, B`; nguồn duyệt `B, D, C`. Cả hai là các map phù hợp và ổn định. Thứ tự khóa cuối ở đích là gì?

**Đáp án:** `A, B, D, C`. B giữ vị trí và nhận giá trị mới; D, C là khóa mới nối cuối theo nguồn.

### Câu 7: Ánh xạ có giá trị null

Khóa `A` đang có giá trị null trước `B`. Gọi `put("A", 1)` trả null. A có phải xuống cuối không?

**Đáp án:** Không. A đã có ánh xạ; giá trị cũ null không biến lần thay giá trị thành lần tạo khóa mới.

### Câu 8: So hai map khác thứ tự

Hai map có cùng ánh xạ, nhưng chuỗi khóa lần lượt là `A, B` và `B, A`. `Map.equals` có thể true không?

**Đáp án:** Có. Để kiểm tra thứ tự, so thêm các danh sách khóa theo thứ tự duyệt.

### Câu 9: Tham số accessOrder

Map được tạo bằng `new LinkedHashMap<>(16, 0.75f, true)`. Có dùng nguyên quy tắc “get không đổi thứ tự” của chế độ mặc định cho nó được không?

**Đáp án:** Không. Đây là chế độ thứ tự truy cập; `get` có thể làm thay đổi thứ tự.

### Câu 10: Bản chép từ HashMap

Bản chép `LinkedHashMap` khớp với lượt duyệt hiện tại của nguồn `HashMap`. Có chứng minh nó đã giữ lịch sử chèn ban đầu của nguồn không?

**Đáp án:** Không. Nguồn không hứa lịch sử đó qua thứ tự duyệt; bản chép chỉ giữ thứ tự được cung cấp lúc chép.

## 16. Thực hành: giữ vị trí, cập nhật nội dung

Viết một chương trình dùng `LinkedHashMap<String, Integer>` mặc định, thực hiện lần lượt:

```text
put("name", 1)
put("price", 2)
put("stock", 3)
put("price", 20)
put("category", 4)
remove("name")
put("name", 10)
```

Trước khi chạy, dự đoán:

1. Chuỗi khóa sau mỗi thao tác.
2. Giá trị cũ trả về từ lần thay `price`.
3. Giá trị cũ trả về từ lần xóa `name`.
4. Chuỗi khóa và giá trị cuối.
5. Một phép so cụ thể để kiểm tra đúng thứ tự, ngoài kiểm tra ánh xạ.

**Đáp án mốc cuối:**

```text
Khóa: [price, stock, category, name]
Giá trị: [20, 3, 4, 10]
Giá trị cũ khi thay price: 2
Giá trị cũ khi xóa name: 1
Kích thước cuối: 4
```

Mở rộng bằng một map cập nhật có thứ tự nguồn `stock, note, color`. Gọi `putAll`, dự đoán khóa nào giữ vị trí và khóa nào nối cuối, rồi xác nhận bằng danh sách khóa.

Cuối cùng tạo một map thứ hai có cùng ánh xạ nhưng chèn theo thứ tự khác. Kiểm tra `Map.equals` và phép so danh sách khóa riêng biệt. Giải thích vì sao kiểm tra dữ liệu và kiểm tra thứ tự đều cần thiết nếu cả hai là yêu cầu của đầu ra.

## Tài liệu đối chiếu

- [Java 17 — LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html): thứ tự chèn, ghi lại khóa đã có, constructor chép, các view và chế độ thứ tự truy cập.
- [Java 17 — Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): khóa logic, `put`, `putAll`, `forEach`, `equals` và hợp đồng các factory.
- [Java 21 — LinkedHashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashMap.html): các thao tác đặt vị trí tường minh như `putFirst`, `putLast`, để phân biệt với `put` thông thường.

Bốn chương trình độc lập được biên dịch bằng `javac --release 17 -encoding UTF-8` và chạy kiểm chứng trên JDK 24.0.2. Chúng chỉ dùng API tương thích Java 17; các khối đầu ra là kết quả thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linked-hash-map-003">Chế độ access-order và các thao tác làm đổi thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# Chế độ access-order và các thao tác làm đổi thứ tự

## 1. Khi “đọc dữ liệu” cũng thay đổi lịch sử sử dụng

Giả sử chương trình giữ các mục đã dùng gần đây. Sau khi thêm a, b, c, người dùng đọc lại a. Nếu muốn biết mục nào lâu chưa được dùng, thứ tự chèn a,b,c không còn đủ: a vừa được sử dụng, còn b mới là mục ít được truy cập gần đây nhất.

LinkedHashMap có **chế độ thứ tự truy cập (access-order)** để duy trì thứ tự này.

Trong chế độ ấy, dãy duyệt đi từ entry ít được truy cập gần đây nhất tới entry được truy cập gần đây nhất. Một thao tác được tính là truy cập có thể đưa entry liên quan về cuối dãy.

Điểm cần học không chỉ là “get chuyển khóa xuống cuối”. Phải biết:

- Thao tác nào được tính là truy cập.
- Điều kiện nào khiến thao tác thành công, bỏ qua, chèn mới hoặc xóa ánh xạ.
- Khi nào dữ liệu không đổi nhưng thứ tự vẫn đổi.
- Vì sao truy cập trong lúc duyệt có thể làm iterator mất hiệu lực.

Bài này xét các API có trong Java 17. Phần giải thích đường thực thi dùng nguồn OpenJDK 17u; các chương trình được biên dịch với --release 17 và chạy trên OpenJDK 24.0.2. Cờ biên dịch chọn mức API/bytecode đích, không thay phiên bản JVM chạy chương trình.

## 2. Bật chế độ và đọc đúng hai đầu của dãy

Tạo map bằng constructor ba tham số:

```java
Map<String, Integer> recent = new LinkedHashMap<>(16, 0.75f, true);
```

Đây là đoạn khai báo minh họa trong ngữ cảnh đã import Map và LinkedHashMap.

| Tham số | Vai trò |
| --- | --- |
| 16 | Yêu cầu dung lượng ban đầu |
| 0.75f | Load factor |
| true | Chọn access-order |

Nếu tham số cuối là false, hoặc dùng constructor thông thường như new LinkedHashMap<>(), map theo thứ tự chèn.

### 2.1. Đầu cũ hơn, cuối mới hơn

Với dãy a,b,c:

```text
đầu                                          cuối
ít được truy cập gần đây nhất  →  được truy cập gần đây nhất
a                 → b       → c
```

Get a thành công:

```text
trước: a → b → c
sau:   b → c → a
```

Không cần thêm lại a hay thay giá trị để ghi nhận lần truy cập này.

### 2.2. Thứ tự tương đối, không phải bộ đếm hay đồng hồ

Access-order không lưu số lần truy cập để sắp theo tần suất. Một khóa đọc một lần ngay lúc này có thể đứng sau khóa đã đọc cả nghìn lần trước đó.

Nó cũng không tự tạo thời điểm hết hạn. Việc một entry nằm đầu chỉ cho biết quan hệ trong lịch sử truy cập mà map ghi nhận, không cho biết entry đã cũ bao nhiêu giây.

### 2.3. Có truy cập không đồng nghĩa dãy luôn khác đi

Nếu a đã ở cuối, get a vẫn là một truy cập hợp lệ nhưng không có vị trí nào mới hơn để chuyển tới. Dãy quan sát có thể giữ nguyên.

Vì vậy cần phân biệt hai câu hỏi:

1. Thao tác có thuộc loại truy cập entry không?
2. Thứ tự trước và sau có thực sự khác nhau không?

Trong cài đặt OpenJDK 17u, afterNodeAccess chỉ nối lại liên kết khi entry chưa ở cuối. Không dùng chi tiết này để dựa vào việc iterator “có thể vẫn dùng được” sau một số lần get.

## 3. Mô hình thực hiện: tìm entry rồi cập nhật vị trí

Bảng băm vẫn phục vụ tìm khóa. Access-order không biến get thành việc quét từ đầu danh sách để tìm phần tử.

Khi đường thao tác xác định một entry được truy cập, cơ chế thứ tự có thể:

1. Gỡ entry khỏi vị trí hiện tại.
2. Nối hai hàng xóm cũ với nhau.
3. Đặt entry ấy ở cuối.
4. Giữ đầu và cuối của chuỗi nhất quán.

Nếu entry đã ở cuối thì không cần nối lại dãy.

Trong mã nguồn được đối chiếu, vai trò này nằm ở afterNodeAccess của [LinkedHashMap OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/LinkedHashMap.java). Đây là giải thích cài đặt; mã ứng dụng sử dụng các thao tác công khai, không gọi hàm nội bộ.

Điều quan trọng là vị trí entry có thể đổi ngay cả khi khóa, giá trị và size đều không đổi.

## 4. Get, getOrDefault, put và putIfAbsent

Tài liệu lớp liệt kê các thao tác tạo truy cập và những điều kiện liên quan. Đối chiếu [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html) thay vì suy luận từ tên “đọc” hoặc “ghi”.

### 4.1. Get trúng khóa và get không có khóa

| Trạng thái | Kết quả dữ liệu | Ảnh hưởng thứ tự |
| --- | --- | --- |
| Khóa tồn tại với giá trị không null | Trả giá trị | Entry được truy cập, về cuối nếu chưa ở cuối |
| Khóa tồn tại với giá trị null | Trả null | Entry vẫn tồn tại và được truy cập |
| Khóa không tồn tại | Trả null | Không có entry để đưa về cuối |

Do đó, chỉ nhìn giá trị trả về null không thể kết luận thứ tự có bị tác động hay không.

Không có “entry của khóa thiếu” tự được tạo ra khi gọi get.

### 4.2. GetOrDefault phân biệt không có khóa với giá trị null

Nếu khóa có trong map, getOrDefault trả giá trị đang gắn với nó và ghi nhận truy cập, kể cả giá trị ấy là null.

Nếu khóa không có, phương thức trả giá trị dự phòng; nó không thêm ánh xạ mới và không ghi một entry thiếu vào lịch sử.

Giá trị dự phòng không phải giá trị sẽ tự lưu vào map.

### 4.3. Put khóa cũ có thể đổi thứ tự dù giá trị giữ nguyên

Trong access-order, put vào một khóa đang tồn tại là truy cập entry đó. Dù giá trị mới bằng giá trị cũ, entry vẫn có thể về cuối.

Điều này khác map theo thứ tự chèn: ở chế độ chèn, put khóa cũ giữ vị trí cũ.

Nếu khóa chưa tồn tại, put thêm entry mới ở cuối. Map vừa có dữ liệu mới vừa có vị trí mới.

### 4.4. PutIfAbsent không nhất thiết là “không làm gì”

Khi khóa đã có giá trị không null, putIfAbsent có thể không thay giá trị. Tuy nhiên entry vẫn được tính là truy cập trong cấu trúc access-order.

Ví dụ dãy a,b,c và a=1. PutIfAbsent("a", 99) trả 1, không thay thành 99, nhưng dãy có thể thành b,c,a.

Nếu khóa đang gắn null, phương thức có thể gắn giá trị cung cấp vào đó; entry cũng được truy cập. Nếu khóa chưa có, nó tạo entry mới ở cuối.

Trong cài đặt được đối chiếu, cả trường hợp entry null được putIfAbsent lại bằng null cũng đi qua đường truy cập. Trả về null không phải một dấu hiệu chung cho “không có tác động”.

## 5. Replace có điều kiện, setValue và replaceAll

### 5.1. Replace chỉ tạo truy cập khi việc thay được thực hiện

Với replace(key, newValue):

- Có entry cho key: thực hiện thay giá trị và truy cập entry.
- Không có key: không thay, không tạo entry để truy cập.

Với replace(key, expectedValue, newValue):

- Khóa tồn tại và giá trị cũ khớp điều kiện: thay và truy cập entry.
- Điều kiện không khớp hoặc không có khóa: không thay, không đưa khóa về cuối.

Một phép thay thành chính giá trị đang có vẫn là phép thay được thực hiện nếu điều kiện khớp. Không cần giá trị mới phải khác theo equals thì entry mới được truy cập.

Giá trị trả về của replace(key, newValue) có thể là null khi giá trị cũ là null. Muốn hiểu tác động phải biết có entry hay không, không chỉ nhìn kết quả trả về.

### 5.2. Entry.setValue không phải map.put

Lấy một Map.Entry từ entrySet rồi gọi setValue thay giá trị qua view. Tài liệu LinkedHashMap không tính thao tác trên collection view là truy cập entry làm đổi access-order.

Vì vậy cùng ý định “đổi giá trị” có thể có tác động khác nhau tới lịch sử tùy API được dùng.

Chọn theo ý nghĩa cần có, không cố thay một API bằng API khác chỉ vì chúng có vẻ cho cùng giá trị cuối.

### 5.3. ReplaceAll không tương đương chuỗi replace theo từng khóa

Trong cài đặt LinkedHashMap được đối chiếu, replaceAll đi theo các entry và thay giá trị; nó không tự biến mỗi entry thành một truy cập đưa về cuối.

Điều này còn đòi hỏi hàm truyền vào không có tác dụng phụ làm thay đổi cấu trúc hoặc gọi những thao tác truy cập gây nối lại dãy.

Câu “replaceAll giữ thứ tự” trong ví dụ phía dưới nói về hàm thuần tính giá trị mới từ đối số. Nếu chính hàm gọi map.get, map.put hoặc sửa map theo đường khác, phải xét cả tác dụng phụ ấy.

## 6. Ví dụ Java 17: đọc, ghi có điều kiện và thay qua view

Lưu thành **AccessOrderBasics.java**:

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class AccessOrderBasics {
    static void show(String label, Map<String, Integer> map) {
        System.out.println(label + ": " + new ArrayList<>(map.keySet()));
    }

    public static void main(String[] args) {
        Map<String, Integer> map = new LinkedHashMap<>(16, 0.75f, true);
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);
        map.put("d", 4);
        show("initial", map);

        System.out.println("contains a: " + map.containsKey("a"));
        show("after contains", map);
        System.out.println("get b: " + map.get("b"));
        show("after get b", map);
        System.out.println("get missing: " + map.get("missing"));
        show("after missing", map);
        System.out.println("default a: " + map.getOrDefault("a", -1));
        show("after default a", map);

        System.out.println("old c: " + map.put("c", 30));
        show("after put c", map);
        System.out.println("existing b: " + map.putIfAbsent("b", 200));
        show("after putIfAbsent b", map);
        System.out.println("failed replace d: " + map.replace("d", 999, 40));
        show("after failed replace", map);
        System.out.println("successful replace d: " + map.replace("d", 4, 40));
        show("after successful replace", map);

        Map.Entry<String, Integer> first = map.entrySet().iterator().next();
        first.setValue(11);
        show("after entry setValue", map);
        System.out.println("values after setValue: " + new ArrayList<>(map.values()));
        map.replaceAll((key, value) -> value + 1);
        show("after replaceAll", map);
        System.out.println("values after replaceAll: " + new ArrayList<>(map.values()));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 AccessOrderBasics.java
java AccessOrderBasics
```

Stdout trên môi trường đã nêu:

```text
initial: [a, b, c, d]
contains a: true
after contains: [a, b, c, d]
get b: 2
after get b: [a, c, d, b]
get missing: null
after missing: [a, c, d, b]
default a: 1
after default a: [c, d, b, a]
old c: 3
after put c: [d, b, a, c]
existing b: 2
after putIfAbsent b: [d, a, c, b]
failed replace d: false
after failed replace: [d, a, c, b]
successful replace d: true
after successful replace: [a, c, b, d]
after entry setValue: [a, c, b, d]
values after setValue: [11, 30, 2, 40]
after replaceAll: [a, c, b, d]
values after replaceAll: [12, 31, 3, 41]
```

### 6.1. Vì sao putIfAbsent làm b về cuối?

B vẫn có giá trị 2, không phải 200. Nhưng nó vừa được truy cập bởi putIfAbsent. Cần quan sát cả giá trị lẫn dãy khóa để thấy đầy đủ tác động.

### 6.2. Hai lần replace khác nhau ở điều kiện

Lần thay d với giá trị mong đợi 999 thất bại. D không được đưa về cuối.

Lần thay với giá trị mong đợi 4 thành công, d thành 40 và được đưa về cuối.

### 6.3. Quan sát không làm nhiễu lịch sử

Hàm show tạo ArrayList từ keySet. Duyệt view theo cách này không gọi get cho từng khóa, nên việc in dãy không tự đưa các khóa về cuối.

SetValue thay giá trị entry đầu là a thành 11. ReplaceAll tăng các giá trị nhưng không tự tạo lịch sử truy cập mới. Những dòng values kiểm tra nội dung thay đổi mà không dùng các lệnh get gây tác động thêm.

## 7. Compute và merge: xét đường thực thi, không chỉ tên phương thức

Các phương thức này có thể dùng ánh xạ sẵn có, tính lại giá trị, thêm mới, bỏ qua hoặc xóa. Vì vậy một bảng chỉ ghi “compute luôn đưa khóa xuống cuối” là thiếu điều kiện.

Bảng sau mô tả các nhánh chính, với hàm truyền vào không tự sửa hay truy cập map:

| Thao tác | Nhánh thực thi | Tác động tới vị trí |
| --- | --- | --- |
| computeIfAbsent | Đã có giá trị không null | Dùng entry hiện có và ghi nhận truy cập |
| computeIfAbsent | Tính được giá trị không null cho khóa mới | Thêm entry cuối dãy |
| computeIfAbsent | Điền giá trị không null cho entry đang null | Cập nhật và truy cập entry |
| computeIfPresent | Có giá trị không null, hàm trả không null | Cập nhật và truy cập entry |
| computeIfPresent | Hàm trả null | Xóa ánh xạ, không còn entry ấy để đặt cuối |
| compute | Hàm trả không null, đã có entry | Cập nhật và truy cập entry |
| compute | Hàm trả không null, chưa có entry | Thêm entry cuối dãy |
| compute | Hàm trả null | Không tạo entry mới; nếu có ánh xạ thì xóa |
| merge | Giữ/cập nhật ánh xạ với kết quả không null | Entry hiện có được truy cập hoặc entry mới được thêm cuối |
| merge | Hàm gộp trả null | Xóa ánh xạ |

Nếu một entry đã ở cuối thì nhánh truy cập có thể không tạo khác biệt trong dãy quan sát. Nếu bị xóa, thứ tự tương đối của các entry còn lại được giữ.

### 7.1. Không đổi giá trị vẫn có thể là truy cập

Compute("a", (key, value) -> value) với a đang có giá trị không null vẫn sử dụng và cập nhật ánh xạ theo đường ấy. Giá trị cuối giống trước không có nghĩa entry giữ nguyên vị trí.

Tương tự, computeIfAbsent trên một ánh xạ đã có giá trị không null không gọi hàm tạo giá trị, nhưng vẫn ghi nhận việc dùng entry hiện có.

### 7.2. Trả null có nhiều ý nghĩa

Null từ get có thể là giá trị của một entry đang tồn tại.

Null từ một hàm tính lại có thể yêu cầu không tạo mới hoặc xóa ánh xạ, tùy phương thức.

Không lấy cùng một quy tắc thứ tự áp cho mọi kết quả null. Hãy xác định ánh xạ trước, nhánh phương thức và ánh xạ sau.

### 7.3. Nhánh bỏ qua với entry đang null cần đọc chi tiết

Trong nguồn [HashMap OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/HashMap.java), nơi triển khai các phương thức được LinkedHashMap kế thừa:

- ComputeIfPresent không gọi hàm tính lại nếu giá trị hiện có là null; nhánh bỏ qua ấy không gọi afterNodeAccess.
- ComputeIfAbsent với entry đang null và hàm trả null giữ ánh xạ null; đường trả về này cũng không gọi afterNodeAccess.

Đây là **phân tích nhánh cài đặt cụ thể**, được kiểm tra thêm trong chương trình tiếp theo trên môi trường đã nêu. Không suy từ phần liệt kê khái quát các họ phương thức ở Javadoc thành một cam kết rằng mọi lần gọi tên compute đều phải di chuyển entry, bất kể điều kiện null hoặc nhánh bỏ qua.

Khi ứng dụng dựa vào từng nhánh như vậy để định nghĩa lịch sử sử dụng, cần ghi rõ trường hợp và phiên bản được kiểm chứng. Nếu muốn một quy tắc đơn giản hơn, có thể chủ động không dùng giá trị null trong mô hình ứng dụng; đó là lựa chọn thiết kế riêng, không phải hạn chế bắt buộc của LinkedHashMap.

### 7.4. Hàm tính lại không nên tự làm nhiễu thứ tự

Không gọi lại map.get trên chính map để lấy một giá trị mà hàm đã được nhận qua đối số. Trong access-order, lời gọi đó có thể sửa liên kết, kể cả không thay giá trị.

Các phương thức compute/merge có giới hạn đối với việc sửa map trong hàm tính lại. Một tác dụng phụ làm đổi cấu trúc có thể bị phát hiện và gây lỗi, đồng thời làm lập luận về thứ tự khó đúng hơn.

Nếu hàm thuần ném ngoại lệ, đừng ghi nhận như một lần cập nhật thành công. Nếu hàm đã có tác dụng phụ trước khi ném, không được giả định hệ thống sẽ tự hoàn tác toàn bộ tác dụng ấy.

## 8. Ví dụ Java 17: các nhánh tính lại và null

Mỗi dòng của chương trình bắt đầu với **một map mới**, theo dãy a,b,c. Tham số nullAtA chọn a=1 hoặc a=null.

Nhờ khởi tạo lại từng trường hợp, kết quả không bị ảnh hưởng bởi lịch sử từ dòng thử nghiệm trước.

Lưu thành **AccessOrderRemapping.java**:

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class AccessOrderRemapping {
    static void test(String label, boolean nullAtA,
            Function<Map<String, Integer>, Object> operation) {
        Map<String, Integer> map = new LinkedHashMap<>(16, 0.75f, true);
        map.put("a", nullAtA ? null : 1);
        map.put("b", 2);
        map.put("c", 3);

        Object result = operation.apply(map);
        System.out.println(label + " => result=" + result
                + "; keys=" + new ArrayList<>(map.keySet())
                + "; values=" + new ArrayList<>(map.values()));
    }

    public static void main(String[] args) {
        test("absent hit", false, m -> m.computeIfAbsent("a", key -> 99));
        test("absent new", false, m -> m.computeIfAbsent("d", key -> 9));
        test("present update", false, m -> m.computeIfPresent("a", (key, value) -> value + 10));
        test("present remove", false, m -> m.computeIfPresent("a", (key, value) -> null));
        test("compute unchanged", false, m -> m.compute("a", (key, value) -> value));
        test("compute missing null", false, m -> m.compute("x", (key, value) -> null));
        test("merge update", false, m -> m.merge("a", 10, Integer::sum));
        test("merge remove", false, m -> m.merge("a", 10, (oldValue, added) -> null));
        test("get mapped null", true, m -> m.get("a"));
        test("default mapped null", true, m -> m.getOrDefault("a", -1));
        test("present mapped null", true, m -> m.computeIfPresent("a", (key, value) -> 9));
        test("absent null result", true, m -> m.computeIfAbsent("a", key -> null));
        test("absent fills null", true, m -> m.computeIfAbsent("a", key -> 9));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 AccessOrderRemapping.java
java AccessOrderRemapping
```

Stdout:

```text
absent hit => result=1; keys=[b, c, a]; values=[2, 3, 1]
absent new => result=9; keys=[a, b, c, d]; values=[1, 2, 3, 9]
present update => result=11; keys=[b, c, a]; values=[2, 3, 11]
present remove => result=null; keys=[b, c]; values=[2, 3]
compute unchanged => result=1; keys=[b, c, a]; values=[2, 3, 1]
compute missing null => result=null; keys=[a, b, c]; values=[1, 2, 3]
merge update => result=11; keys=[b, c, a]; values=[2, 3, 11]
merge remove => result=null; keys=[b, c]; values=[2, 3]
get mapped null => result=null; keys=[b, c, a]; values=[2, 3, null]
default mapped null => result=null; keys=[b, c, a]; values=[2, 3, null]
present mapped null => result=null; keys=[a, b, c]; values=[null, 2, 3]
absent null result => result=null; keys=[a, b, c]; values=[null, 2, 3]
absent fills null => result=9; keys=[b, c, a]; values=[2, 3, 9]
```

### 8.1. Những trường hợp dùng entry hiện có

Absent hit dùng a=1 mà không cần gọi hàm tạo 99, nhưng a vẫn được đưa cuối.

Compute unchanged trả lại chính giá trị hiện có, nhưng dãy vẫn thành b,c,a.

Hai ví dụ cho thấy không thể lấy việc “giá trị chưa đổi” làm tiêu chí quyết định thứ tự.

### 8.2. Những trường hợp xóa hoặc không tạo ánh xạ

Present remove và merge remove bỏ a khỏi map, nên kết quả còn b,c.

Compute missing null không tạo x, do đó dãy a,b,c giữ nguyên. Không có một entry x để xếp thành mới nhất.

### 8.3. Nhận diện null qua trạng thái, không qua mỗi kết quả trả về

Get mapped null và default mapped null đều trả null, nhưng a đang tồn tại nên về cuối: b,c,a.

Present mapped null và absent null result cũng trả null, nhưng ở những nhánh bỏ qua/không điền giá trị của cài đặt đã giải thích, dãy vẫn là a,b,c.

Absent fills null tính được 9, cập nhật a và đưa a về cuối.

Các dòng thuộc nhánh null đặc biệt cần được đọc cùng phạm vi cài đặt và môi trường của bài. Chúng không phải lý do dùng một quy tắc tùy đoán như “null luôn giữ thứ tự” hoặc “null luôn làm truy cập”.

## 9. PutAll tạo một chuỗi truy cập theo thứ tự nguồn

PutAll không chỉ thêm những khóa còn thiếu. Với access-order, ánh xạ đã có trong map đích cũng được xử lý như một truy cập tương ứng.

Tài liệu lớp xác định các truy cập được thực hiện theo thứ tự entrySet của map nguồn.

Giả sử đích đang có a,b,c,d và nguồn đưa ra c,a,e:

| Bước | Ánh xạ được xử lý | Thứ tự đích sau bước |
| --- | --- | --- |
| Bắt đầu | Chưa xử lý | a,b,c,d |
| 1 | c | a,b,d,c |
| 2 | a | b,d,c,a |
| 3 | e mới | b,d,c,a,e |

B và d không được xử lý bởi đợt putAll này nên vẫn đứng trước các entry vừa được đưa về cuối, theo thứ tự tương đối của chúng.

### 9.1. Nguồn không có hợp đồng thứ tự làm kết quả khó dự đoán hơn

Nếu nguồn là LinkedHashMap theo thứ tự chèn c,a,e, có cơ sở dùng đúng trình tự ấy để lập bảng.

Nếu nguồn là một Map không bảo đảm thứ tự, không tự giả định putAll sẽ đi theo thứ tự chữ cái hay lịch sử mà dữ liệu từng được tạo. Đích ghi nhận những entry theo lượt nguồn thực sự đưa ra.

Muốn kết quả có quy tắc riêng, phải kiểm soát thứ tự nguồn hoặc chủ động chuẩn bị dãy cập nhật.

### 9.2. Thêm mới và truy cập lại đều có vai trò

Khóa e mới nằm cuối vì vừa được thêm. C và a đã có vẫn đổi vị trí vì đợt putAll truy cập lại chúng.

Không dùng cách hiểu “chỉ những khóa mới mới ảnh hưởng thứ tự” cho một map access-order.

Không dùng chính map đang đổi thứ tự làm nguồn để tự lập luận rằng việc sao chép lên nó chắc chắn vô hại. Lượt duyệt nguồn và các truy cập vào đích cần được xem cùng nhau.

## 10. Các thao tác không tự đưa entry về cuối

Bảng dưới nói về **bản thân thao tác**, không bao gồm tác dụng phụ của mã người dùng tự gọi thêm:

| Thao tác | Có tự ghi nhận truy cập để làm mới vị trí không? |
| --- | --- |
| containsKey | Không |
| containsValue | Không |
| size, isEmpty | Không |
| Duyệt keySet, values, entrySet | Không |
| Lấy khóa/giá trị từ một Map.Entry | Không |
| Entry.setValue qua view | Không |
| ForEach với hàm chỉ dùng đối số để xử lý | Không |
| ReplaceAll với hàm thuần tính giá trị mới | Không tự tạo từng truy cập như replace |
| Replace có điều kiện thất bại | Không |
| Get hoặc getOrDefault không tìm thấy khóa | Không có entry để làm mới |

### 10.1. Không truy cập lại không có nghĩa không sửa map

Remove xóa ánh xạ, clear làm map rỗng. Những thao tác này thay đổi cấu trúc nhưng không phải một lần làm mới vị trí của entry bị xóa.

Xóa qua view cũng có tác dụng thật lên map. Nó không tự biến các entry còn lại thành những entry vừa được truy cập.

Vì vậy cần phân biệt ba loại tác động:

- Cập nhật nội dung khóa–giá trị.
- Thêm/xóa ánh xạ.
- Thay đổi vị trí theo lịch sử truy cập.

Một phương thức có thể liên quan tới hơn một loại, hoặc không liên quan loại nào.

### 10.2. Mã quan sát có thể gián tiếp tạo truy cập

Một hàm ghi log tự viết như “duyệt keySet, gọi map.get(key), in giá trị” có thể làm đổi thứ tự. Nguyên nhân là các lệnh get được gọi thêm, không phải keySet tự tính truy cập.

Tương tự, một hàm được truyền cho forEach có thể tự gọi get hoặc put. Khi đó phải xét các lệnh ấy; không dùng tên forEach để kết luận toàn bộ đoạn mã không có tác động.

Muốn chỉ quan sát ánh xạ trong một lượt duyệt, lấy cả khóa lẫn giá trị từ entrySet và tránh gọi lại các API làm mới vị trí.

### 10.3. Chụp danh sách khóa không đóng băng map

New ArrayList<>(map.keySet()) chụp dãy khóa vào một danh sách. Sau đó get từng khóa vẫn là truy cập trên map.

Duyệt danh sách chụp có thể tránh một số vấn đề do iterator của map bị nối lại dãy trong cùng luồng, nhưng không biến các get thành thao tác trung lập với lịch sử.

Cũng không tạo ảnh chụp nhất quán nhiều luồng chỉ bằng câu lệnh sao chép danh sách khóa mà không có sự phối hợp phù hợp.

## 11. Iterator: size không đổi vẫn có thể mất hiệu lực

Ở access-order, đổi thứ tự là một dạng thay đổi cấu trúc. Vì thế một get thành công có thể ảnh hưởng iterator đang giữ.

Mẫu dễ sai:

```java
for (String key : recent.keySet()) {
    Integer value = recent.get(key);
    System.out.println(value);
}
```

Đây là **đoạn minh họa cần tránh**, không phải chương trình độc lập được đề nghị sử dụng. Nó đang duyệt map rồi truy cập lại chính map qua get.

Nếu get đưa entry chưa ở cuối về cuối, iterator được tạo trước đó có thể phát hiện thay đổi và ném ConcurrentModificationException.

### 11.1. Duyệt entrySet để lấy dữ liệu đã có trong entry

Nếu chỉ cần đọc mọi cặp, dùng:

```java
for (Map.Entry<String, Integer> entry : recent.entrySet()) {
    String key = entry.getKey();
    Integer value = entry.getValue();
    System.out.println(key + "=" + value);
}
```

Đây là mẫu trong ngữ cảnh có recent và các import phù hợp. Lấy value từ entry không tạo thêm một truy cập lên map.

Nếu cần xóa trong lượt duyệt, xem thao tác remove của chính iterator theo hợp đồng của nó. Đừng sửa cấu trúc tùy ý qua map rồi tiếp tục dùng iterator cũ.

### 11.2. Fail-fast là chẩn đoán lỗi, không phải điều kiện nghiệp vụ

Cơ chế phát hiện được thực hiện theo khả năng. Không viết logic kiểu “nếu không có ConcurrentModificationException thì vòng lặp chắc an toàn”.

Ví dụ get entry đang ở cuối có thể không nối lại dãy trong một cài đặt, nhưng điều đó không tạo một cách dùng iterator ổn định cho mọi lịch sử.

ConcurrentModificationException cũng không chỉ liên quan nhiều luồng. Một luồng có thể tự làm iterator mất hiệu lực bằng cách đổi cấu trúc ngoài đường được phép.

## 12. Ví dụ Java 17: putAll, vòng đọc đúng và iterator bị ảnh hưởng

Lưu thành **AccessOrderBulkAndIteration.java**:

```java
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public class AccessOrderBulkAndIteration {
    public static void main(String[] args) {
        Map<String, Integer> map = new LinkedHashMap<>(16, 0.75f, true);
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);
        map.put("d", 4);

        Map<String, Integer> incoming = new LinkedHashMap<>();
        incoming.put("c", 30);
        incoming.put("a", 10);
        incoming.put("e", 5);
        map.putAll(incoming);
        System.out.println("after putAll: " + new ArrayList<>(map.keySet()));

        StringJoiner joined = new StringJoiner("; ");
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            joined.add(entry.getKey() + "=" + entry.getValue());
        }
        System.out.println("safe entries: " + joined);
        System.out.println("after safe loop: " + new ArrayList<>(map.keySet()));

        Iterator<String> iterator = map.keySet().iterator();
        System.out.println("iterator first: " + iterator.next());
        System.out.println("get c during iteration: " + map.get("c"));
        System.out.println("after get c: " + new ArrayList<>(map.keySet()));
        try {
            System.out.println("iterator next: " + iterator.next());
        } catch (ConcurrentModificationException expected) {
            System.out.println("observed iterator: ConcurrentModificationException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 AccessOrderBulkAndIteration.java
java AccessOrderBulkAndIteration
```

Stdout quan sát trong môi trường đã nêu:

```text
after putAll: [b, d, c, a, e]
safe entries: b=2; d=4; c=30; a=10; e=5
after safe loop: [b, d, c, a, e]
iterator first: b
get c during iteration: 30
after get c: [b, d, a, e, c]
observed iterator: ConcurrentModificationException
```

### 12.1. PutAll đúng theo trình tự c,a,e

Kết quả b,d,c,a,e khớp bảng phân tích. Giá trị c thành 30 và a thành 10, e được thêm bằng 5.

Vòng entrySet lấy khóa và giá trị trực tiếp. Dãy sau vòng ấy vẫn là b,d,c,a,e.

### 12.2. Một get làm iterator cũ mất hiệu lực

Iterator đã trả b. Sau đó get c đưa c ra cuối, làm dãy thành b,d,a,e,c. Size vẫn bằng 5 nhưng các liên kết thứ tự đã đổi.

Trên môi trường chạy được ghi rõ, lần next tiếp theo quan sát ConcurrentModificationException. Dòng observed là kết quả của thí nghiệm chẩn đoán; không biến việc ném ngoại lệ trong mọi tình huống sai thành bảo đảm tuyệt đối.

Ví dụ cố ý bắt lỗi để chương trình kết thúc có kiểm soát và giải thích nguyên nhân. Trong mã thực tế, sửa cách duyệt hoặc mô hình cập nhật, không dùng catch để tiếp tục iterator đã mất hiệu lực.

## 13. Hệ quả khi dùng access-order trong ứng dụng

### 13.1. Phải xác định “dùng một mục” nghĩa là gì

Nếu kiểm tra tồn tại bằng containsKey thì mục không được làm mới. Nếu lấy giá trị bằng get thì mục được truy cập.

Nếu putIfAbsent tìm thấy dữ liệu đã có, nó vẫn có thể làm mới vị trí dù không thay nội dung. Nếu replace có điều kiện thất bại thì không.

Những khác biệt này ảnh hưởng cách diễn giải đầu dãy. Chọn API phải phù hợp với ý nghĩa “sử dụng” trong bài toán, không chỉ với kiểu giá trị trả về.

### 13.2. Không tự có giới hạn, hết hạn hoặc tính an toàn đồng thời

Access-order cung cấp lịch sử tương đối của các entry. Nó không tự giới hạn số ánh xạ, tự xóa theo thời gian hoặc tự phối hợp nhiều luồng.

Cơ chế loại entry, nếu cần, là một chính sách bổ sung. Không kết luận cứ bật true là đã có một cache hoàn chỉnh.

### 13.3. Get có thể là thao tác sửa cấu trúc

Nếu một thiết kế khóa đồng bộ phân loại mọi get là đọc thuần túy, thiết kế ấy cần được xem lại khi dùng access-order. Hai luồng cùng get có thể cùng tác động chuỗi thứ tự.

LinkedHashMap không tự đồng bộ. Dùng một cơ chế phối hợp phù hợp cho cả các thao tác đổi thứ tự, các lượt duyệt và những chuỗi thao tác cần tính nhất quán.

Không dùng fail-fast để thay vai trò của cơ chế phối hợp.

### 13.4. Sao chép không tự giữ chế độ

New LinkedHashMap<>(source) tạo một map theo thứ tự chèn, nhận dãy mà nguồn đưa ra lúc sao chép. Nó không tự bật accessOrder=true chỉ vì source đang ở chế độ truy cập.

Nếu cần map mới cũng duy trì truy cập, phải tạo nó bằng cấu hình mong muốn rồi đưa các ánh xạ vào theo trình tự phù hợp.

Việc sao chép ánh xạ không tự là bản sao sâu của khóa và giá trị, cũng không tự tạo một ảnh chụp nhất quán khi nguồn đang bị sửa đồng thời.

## 14. Cách kiểm thử thứ tự có chủ đích

Một kiểm thử tốt mô tả rõ:

1. Chế độ của map.
2. Dữ liệu và dãy trước thao tác.
3. Thao tác cùng điều kiện của nó.
4. Giá trị trả về, ánh xạ còn lại và dãy sau thao tác.
5. Môi trường khi đang kiểm tra một nhánh cài đặt cụ thể.

Để kiểm tra dãy khóa, so sánh List được tạo từ keySet. Chỉ kiểm tra Map.equals hoặc keySet.equals không xác nhận thứ tự.

Hãy dùng dữ liệu có ít nhất vài entry và chọn khóa chưa ở cuối. Nếu mọi phép thử đều truy cập entry cuối, nhiều lỗi về kỳ vọng thứ tự sẽ bị che mất vì dãy không có gì để đổi.

Với các nhánh có điều kiện, thử cả thành công và thất bại. Với null, phân biệt khóa thiếu, khóa đang null, kết quả tính lại null và việc xóa ánh xạ.

Hàm quan sát trong bài chỉ duyệt view. Nếu bài kiểm thử dùng get để kiểm tra giá trị, chính lời kiểm tra ấy có thể đổi trạng thái trước phép kiểm tra kế tiếp. Có thể kiểm tra thứ tự trước, hoặc dùng dãy entry/values phù hợp mà không phát sinh thêm truy cập.

Đối với chương trình có nhiều thao tác liên tiếp, ghi lại lịch sử từng bước hoặc khởi tạo map mới cho mỗi trường hợp như chương trình thứ hai. Đừng để một bước xác minh ẩn trở thành nguyên nhân làm kết quả sau khó giải thích.

## 15. Lỗi phổ biến và cách sửa

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| Access-order là thứ tự số lần truy cập nhiều nhất | Đây là thứ tự tương đối theo lần truy cập gần nhất được ghi nhận |
| Get luôn chỉ đọc cấu trúc | Get trúng entry có thể nối lại dãy |
| Get trả null chắc không đổi gì | Có thể đã truy cập entry mang giá trị null |
| Put cùng giá trị không tác động thứ tự | Entry cũ vẫn có thể được làm mới |
| PutIfAbsent không thêm thì là thao tác trung lập | Trường hợp đã có giá trị vẫn có thể tạo truy cập |
| Replace thất bại cũng đưa khóa về cuối | Chỉ phép thay được thực hiện mới ghi nhận truy cập theo quy tắc replace |
| Mọi lần gọi compute đều có cùng tác động | Xét nhánh dùng, tạo, bỏ qua, xóa và các điều kiện null |
| SetValue và put giống nhau về lịch sử | Thao tác qua entry view không tự tạo truy cập như put |
| PutAll chỉ tác động khóa mới | Nó còn truy cập lại ánh xạ có sẵn theo thứ tự nguồn |
| Size giữ nguyên thì iterator còn hợp lệ | Đổi thứ tự cũng có thể là thay đổi cấu trúc |
| In dữ liệu không thể làm thay đổi map | Hàm in tự gọi get có thể làm mới lịch sử |
| Không có ngoại lệ nghĩa là cách dùng chắc đúng | Fail-fast chỉ hỗ trợ chẩn đoán theo khả năng |
| Constructor sao chép giữ nguyên access-order | Constructor nhận Map tạo chế độ thứ tự chèn |
| Bật access-order là có cache đầy đủ | Cần chính sách dung lượng, hết hạn và phối hợp nếu bài toán yêu cầu |

## 16. Bài tập tự kiểm tra có lời giải

### Bài 1: Truy cập lại một khóa

Map access-order có a,b,c. Get a thành công rồi get c thành công. Dãy cuối là gì?

**Lời giải:** sau get a là b,c,a; sau get c là b,a,c.

### Bài 2: Không tìm thấy khóa

Từ a,b,c, gọi getOrDefault("x", -1). Kết quả và thứ tự là gì?

**Lời giải:** trả -1; không thêm x, dãy vẫn a,b,c.

### Bài 3: Không thay giá trị

Từ a=1,b=2,c=3, gọi putIfAbsent("a", 99). Có giữ nguyên giá trị và thứ tự không?

**Lời giải:** giá trị a giữ 1 và phương thức trả 1, nhưng dãy thành b,c,a nếu a chưa ở cuối.

### Bài 4: Replace cùng giá trị

Từ a=1,b=2,c=3, gọi replace("a", 1, 1). Có cần giá trị mới khác giá trị cũ thì a mới được làm mới không?

**Lời giải:** không. Điều kiện khớp nên phép thay được thực hiện; a có thể được đưa cuối dù vẫn bằng 1.

### Bài 5: Bỏ qua giá trị null

Map có a=null,b=2,c=3. Trong đường cài đặt đã đối chiếu, computeIfPresent("a", (key, value) -> value + 1) có gọi hàm và đưa a về cuối không?

**Lời giải:** không. Nhánh này bỏ qua vì giá trị hiện có là null, không gọi hàm tính lại và không gọi afterNodeAccess. Đây là trường hợp cần đọc theo phạm vi cài đặt của bài.

### Bài 6: Hàm gộp trả null

Từ a=1,b=2,c=3, merge a với một giá trị không null nhưng hàm gộp trả null. A có trở thành entry cuối không?

**Lời giải:** ánh xạ a bị xóa; dãy còn b,c. Không có entry a để giữ ở cuối.

### Bài 7: Nguồn putAll

Đích đang a,b,c,d. Nguồn đưa b rồi a. Giả sử đợt cập nhật không xóa hay thêm khóa nào, dãy cuối là gì?

**Lời giải:** xử lý b tạo a,c,d,b; xử lý a tiếp theo tạo c,d,b,a.

### Bài 8: Chỉ đọc dữ liệu khi duyệt

Cần xuất các cặp đang có mà không làm mới lịch sử. Chọn keySet cộng get hay entrySet cộng getValue?

**Lời giải:** dùng entrySet và lấy khóa/giá trị từ entry. KeySet cộng get có thể đổi thứ tự và ảnh hưởng iterator đang duyệt map.

### Bài 9: Kiểm thử che mất thay đổi

Tất cả phép thử get đều chọn entry cuối. Dãy không đổi có chứng minh get không được tính là truy cập không?

**Lời giải:** không. Entry cuối đã ở vị trí mới nhất. Phải thử khóa ở đầu hoặc giữa để quan sát việc di chuyển.

### Bài 10: Quan sát có tác dụng phụ

Một bài kiểm thử kiểm tra giá trị bằng get("a") trước khi kiểm tra dãy. Có thể chính bước kiểm tra ấy làm kết quả dãy thay đổi không?

**Lời giải:** có. Với access-order, get có tác dụng lên lịch sử. Cần thiết kế bước xác minh không làm nhiễu trạng thái đang muốn kiểm tra.

## 17. Tài liệu đối chiếu

- [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html): chế độ truy cập, danh sách thao tác, putAll, replace, view và giới hạn fail-fast.
- [LinkedHashMap.java OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/LinkedHashMap.java): get, getOrDefault, afterNodeAccess, replaceAll và iterator.
- [HashMap.java OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/HashMap.java): các nhánh putIfAbsent, replace, compute và merge được kế thừa.
- [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): điều kiện giá trị null, hàm tính lại, ánh xạ và các view.

Khi dùng access-order, hãy xem thứ tự như một phần trạng thái mà thao tác có thể thay đổi. Muốn dự đoán đúng, cần xét loại API, điều kiện thực thi và cả những lời gọi quan sát hoặc hàm tính lại được thêm vào xung quanh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linked-hash-map-004">Chi phí duyệt, lưu trữ và lựa chọn so với HashMap</a>

<details>
<summary>Xem chi tiết</summary>

# LinkedHashMap: chi phí duyệt, lưu trữ và lựa chọn so với HashMap

Một map có thể phục vụ hai công việc khác nhau: tìm giá trị theo khóa và lần lượt xử lý toàn bộ các ánh xạ. Khi chọn giữa `HashMap` và `LinkedHashMap`, cần xét cả hai công việc, cùng yêu cầu thứ tự và giới hạn bộ nhớ.

Ví dụ, bộ đếm sản phẩm chỉ dùng để trả lời “mã này xuất hiện bao nhiêu lần?” có thể không cần thứ tự. Báo cáo phải xuất các mã theo lần đầu chúng xuất hiện lại có một yêu cầu khác: thứ tự là một phần của kết quả. `LinkedHashMap` chế độ mặc định diễn đạt được yêu cầu đó trực tiếp.

Sau bài này, bạn cần giải thích được vì sao duyệt `LinkedHashMap` phụ thuộc số ánh xạ, phần lưu trữ nào được thêm vào, và khi nào những đặc điểm đó đáng để lựa chọn thay cho `HashMap`. Không nên biến một dòng Big O thành lời hứa về tốc độ trên mọi dữ liệu.

Bài dùng API Java 17. Những chi tiết từ mã nguồn OpenJDK 17 được đánh dấu là cách triển khai cụ thể, không phải yêu cầu chung cho mọi JVM.

## 1. Mô hình cần nhớ: bảng băm và chuỗi thứ tự

### 1.1. Bảng băm phục vụ tra cứu

`HashMap` dùng cấu trúc bảng băm để tìm một ánh xạ từ khóa. `LinkedHashMap` vẫn có nền tảng ấy; nó không thay việc tra cứu bằng cách đi tuần tự từ phần tử đầu đến cuối.

Vì thế, chất lượng `hashCode`, sự nhất quán với `equals` và tính ổn định của khóa vẫn quan trọng. Đổi sang `LinkedHashMap` không sửa một khóa có quy tắc bằng nhau sai, cũng không làm mọi phân bố băm có thời gian tra cứu như nhau.

### 1.2. Chuỗi liên kết phục vụ thứ tự duyệt

`LinkedHashMap` duy trì thêm chuỗi liên kết hai chiều đi qua các ánh xạ. Có thể nhìn cùng một map theo hai hướng:

~~~text
Đường tìm theo khóa:
khóa -> giá trị băm -> vị trí trong bảng -> ánh xạ phù hợp

Đường duyệt theo thứ tự của LinkedHashMap:
đầu -> ánh xạ A <-> ánh xạ B <-> ánh xạ C -> cuối
~~~

Đây là sơ đồ khái niệm. Các ánh xạ được truy cập qua bảng và qua chuỗi không phải hai bản sao đầy đủ, độc lập của mọi khóa và giá trị. Thông tin bổ sung là thông tin giúp giữ và duy trì quan hệ thứ tự.

Chế độ mặc định giữ thứ tự chèn. Gán lại giá trị của khóa đang tồn tại không đưa khóa đó xuống cuối. Xóa một khóa rồi thêm lại là lần chèn mới nên vị trí của nó trở thành cuối chuỗi.

Nếu tạo bằng constructor có `accessOrder=true`, chuỗi giữ thứ tự truy cập theo những thao tác được API quy định. Một `get` thành công có thể đổi vị trí. Các thao tác trên góc nhìn tập hợp không tự tạo lượt truy cập kiểu này. [Java 17: LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html)

### 1.3. Có thứ tự không có nghĩa được sắp xếp

Chèn `30, 10, 20` vào `LinkedHashMap` mặc định cho thứ tự duyệt `30, 10, 20`. Nó không tự sắp thành `10, 20, 30`.

Trước khi chọn cấu trúc, hãy gọi đúng tên yêu cầu:

| Yêu cầu | Ý nghĩa |
|---|---|
| Theo lần đầu xuất hiện | Thứ tự chèn có thể đáp ứng |
| Theo lần truy cập gần nhất | Có thể cần chế độ thứ tự truy cập |
| Theo khóa tăng dần | Cần thứ tự khóa, chẳng hạn TreeMap hoặc bước sắp xếp riêng |
| Không yêu cầu thứ tự | Có thể bắt đầu với HashMap |

Đặt tên yêu cầu trước giúp tránh trả chi phí để giữ một thứ tự mà ứng dụng không dùng, hoặc chọn một cấu trúc có thứ tự nhưng sai loại thứ tự.

## 2. Hai đại lượng quyết định cách đọc độ phức tạp

Gọi:

- `N` là số ánh xạ hiện có, tức `map.size()`.
- `C` là số vị trí của bảng băm trong cách triển khai đang xét.
- `M` là số lần ứng dụng duyệt toàn bộ map trong giai đoạn phân tích.

`N` và `C` không phải cùng một số. Một map có ít ánh xạ vẫn có thể gắn với bảng lớn, chẳng hạn do yêu cầu sức chứa ban đầu lớn. Kích thước dữ liệu hiện tại không đủ để mô tả mọi chi phí của cấu trúc bảng.

API công khai của `HashMap` không có phương thức trả về sức chứa mảng nội bộ. Tham số constructor là yêu cầu khởi tạo; không nên dùng riêng con số truyền vào để khẳng định đã quan sát đúng số vị trí đang được cấp phát. Cách làm tròn và thời điểm cấp phát thuộc cách triển khai.

Tài liệu Java 17 mô tả chi phí duyệt các góc nhìn tập hợp của `HashMap` tỷ lệ với sức chứa cộng kích thước. Với `LinkedHashMap`, chi phí này tỷ lệ với kích thước, bất kể sức chứa. Đây là cơ sở của hai mô hình:

~~~text
Duyệt toàn bộ HashMap:        O(C + N)
Duyệt toàn bộ LinkedHashMap:  O(N)
~~~

[Java 17: HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html), [Java 17: LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html)

Đó là mô hình tăng trưởng, không phải công thức thời gian tính bằng nano giây. Nó không nói mỗi vị trí bảng trống và mỗi ánh xạ tốn đúng một đơn vị công việc.

## 3. Vì sao LinkedHashMap có chi phí duyệt O(N)?

### 3.1. HashMap chịu ảnh hưởng của bảng trống

Để tìm hết ánh xạ, bộ duyệt `HashMap` phải đi qua cấu trúc tổ chức dữ liệu của bảng. Những vị trí không có dữ liệu ảnh hưởng việc tìm vị trí có ánh xạ tiếp theo. Bởi vậy, mô hình chứa cả `C` và `N`.

Khi bảng được giữ tương xứng với dữ liệu, có thể xem `C = Θ(N)`. Khi ấy `O(C + N)` cũng là `O(N)` theo N. Vì vậy, không có mâu thuẫn giữa câu “HashMap duyệt tuyến tính trong trường hợp sức chứa tương xứng” và công thức đầy đủ `O(C + N)`.

Nếu bảng rất thưa, việc bỏ `C` khỏi phân tích lại có thể bỏ sót yếu tố đáng kể. Hai map cùng `size()` chưa chắc có cùng mức tác động từ việc tìm qua bảng.

### 3.2. LinkedHashMap đi theo chuỗi ánh xạ

Bộ duyệt `LinkedHashMap` có thể đi từ ánh xạ đầu đến ánh xạ cuối qua liên kết. Nó không cần tìm ánh xạ tiếp theo bằng cách quét các vị trí bảng trống. Số ánh xạ trên đường đi là N.

Điều này lý giải vì sao sức chứa quá lớn ít gây bất lợi cho riêng đường duyệt hơn so với `HashMap`. Nhưng bộ duyệt vẫn đọc các đối tượng và lần theo tham chiếu. Bố trí đối tượng, bộ nhớ đệm của CPU, JIT và công việc trong thân vòng lặp vẫn ảnh hưởng thời gian thực tế.

`O(N)` không có nghĩa mỗi map có N ánh xạ luôn mất đúng cùng một thời gian duyệt trên mọi máy hoặc ở mọi thời điểm.

### 3.3. Big O không cho tỷ lệ nhanh chậm

Giả sử một mô hình có `N = 100` và `C = 1.000.000`. `C + N` cho thấy việc quét bảng có thể lớn hơn nhiều so với lượng dữ liệu cần xử lý. Chuỗi của `LinkedHashMap` loại bỏ thành phần quét các vị trí trống đó khỏi đường duyệt.

Tuy nhiên, không thể nói nó nhanh hơn đúng `(1.000.000 + 100) / 100 = 10.001` lần. Hai loại bước không nhất thiết có cùng chi phí; thuật toán và môi trường thực thi còn nhiều khác biệt.

Nếu có M lượt duyệt, phần mô hình là `M × (C + N)` hoặc `M × N`. Công thức giúp nhận ra tải công việc nhạy với duyệt, nhưng chưa tính đầy đủ xây dựng map, cập nhật, cấp phát, thu gom rác và xử lý từng phần tử.

| Bối cảnh | Điều có thể kết luận |
|---|---|
| C cùng bậc với N | Cả hai đường duyệt cùng bậc tuyến tính theo N |
| C lớn hơn N rất nhiều | LinkedHashMap có lợi thế cấu trúc ở việc không quét bảng trống |
| Thân vòng lặp xử lý rất nặng | Chênh lệch duyệt cấu trúc có thể chỉ là phần nhỏ của tổng thời gian |
| Cần con số tốc độ cụ thể | Phải đo trên tải công việc và môi trường đại diện |

Một vòng lặp cộng số nguyên và một vòng lặp gọi dịch vụ mạng cho mỗi ánh xạ không có cùng tỷ trọng chi phí cấu trúc dữ liệu. Cần đặt lựa chọn map trong toàn bộ tác vụ.

## 4. Duy trì thứ tự cũng có công việc phải làm

Các thao tác cơ bản như `get`, `put` và `remove` của `LinkedHashMap` vẫn dựa trên bảng băm. Tài liệu nêu hiệu năng thời gian hằng số khi giá trị băm phân bố tốt, đồng thời mô tả việc duy trì liên kết thường làm các thao tác này tốn thêm công việc so với `HashMap`. Ngoại lệ đáng chú ý là lợi thế ở đường duyệt. [Java 17: LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html)

Cần phân biệt:

- Thời gian hằng số kỳ vọng khi phân bố băm tốt với cam kết cho mọi dữ liệu.
- Chi phí trung bình phân bổ qua nhiều lần chèn với một lần chèn phải mở rộng bảng.
- Cùng bậc Big O với cùng thời gian thực tế.

Thêm vài bước nối hoặc tháo liên kết có thể không đổi bậc độ phức tạp, nhưng vẫn thêm công việc.

| Công việc | HashMap | LinkedHashMap |
|---|---|---|
| Tìm theo khóa | Bảng băm | Vẫn là bảng băm |
| Thêm ánh xạ mới | Cập nhật cấu trúc băm, có thể mở rộng | Thêm việc nối vào chuỗi thứ tự |
| Xóa ánh xạ | Loại khỏi cấu trúc băm | Thêm việc tháo khỏi chuỗi thứ tự |
| Duyệt toàn bộ góc nhìn | O(C + N) | O(N) |
| Thứ tự | Không được cam kết | Theo chế độ đã chọn |
| Trạng thái giữ thứ tự | Không có chuỗi thứ tự như LinkedHashMap | Có thông tin liên kết và trạng thái bổ sung |

Ở chế độ thứ tự truy cập, một `get` thành công có thể chuyển ánh xạ xuống cuối. Nếu ánh xạ đã ở cuối, không cần chuyển vị trí; nếu chưa, cấu trúc liên kết có thể phải thay đổi.

Điều này không đủ để thiết kế một bộ đệm có giới hạn. Chủ đề ở đây là chi phí và lựa chọn: chỉ chọn `accessOrder=true` khi cần đúng ngữ nghĩa đó, và nhớ rằng “đọc theo khóa” có thể gây công việc duy trì thứ tự.

## 5. Chi phí lưu trữ: cùng bậc không có nghĩa cùng số byte

### 5.1. Tách cấu trúc map khỏi dữ liệu được tham chiếu

Một mô hình lưu trữ thô gồm:

~~~text
đối tượng map
+ mảng tham chiếu của bảng băm
+ các đối tượng biểu diễn ánh xạ
+ các đối tượng khóa và giá trị được tham chiếu tới
~~~

Nếu tạm tách kích thước khóa và giá trị, phần cấu trúc của cả hai map có thể mô hình hóa theo `O(C + N)`. `LinkedHashMap` thêm thông tin giữ chuỗi nên có thể tốn hơn về hệ số lưu trữ dù cùng bậc tăng trưởng.

Với hàng triệu ánh xạ nhỏ, phần cấu trúc bổ sung có thể đáng kể. Nếu mỗi giá trị tham chiếu một đồ thị đối tượng rất lớn, tỷ lệ của phần cấu trúc trên tổng dung lượng có thể nhỏ hơn. Phải nói rõ đang tính riêng cấu trúc hay toàn bộ dữ liệu còn được giữ sống.

### 5.2. Có thể thấy gì trong OpenJDK 17?

Trong mã nguồn OpenJDK 17, nút thông thường `LinkedHashMap.Entry` mở rộng `HashMap.Node` và có thêm `before`, `after`. Map duy trì cả đầu và cuối chuỗi. Các trường này giải thích cụ thể việc giữ thứ tự cần thêm thông tin. [OpenJDK 17: LinkedHashMap.java](https://raw.githubusercontent.com/openjdk/jdk/jdk-17%2B35/src/java.base/share/classes/java/util/LinkedHashMap.java)

Không được biến quan sát đó thành một số byte cố định cho mọi ánh xạ trên mọi JVM. Tiêu đề đối tượng, độ rộng tham chiếu, nén tham chiếu, căn chỉnh và dạng nút đều có thể ảnh hưởng.

Ngay trong OpenJDK 17, các nút dạng cây của `HashMap` có quan hệ kế thừa với `LinkedHashMap.Entry`. Vì vậy, câu “mọi nút LinkedHashMap luôn chỉ lớn hơn mọi nút HashMap đúng hai tham chiếu” quá rộng. Có thể mô tả hai tham chiếu bổ sung khi so sánh các nút thông thường của cách triển khai này, rồi đo môi trường cụ thể nếu cần số byte. [OpenJDK 17: HashMap.java](https://raw.githubusercontent.com/openjdk/jdk/jdk-17%2B35/src/java.base/share/classes/java/util/HashMap.java)

### 5.3. Không quét C khi duyệt không có nghĩa không lưu C

`LinkedHashMap` vẫn dùng bảng băm để tra cứu. Vì vậy, yêu cầu sức chứa quá lớn vẫn có thể làm tăng dung lượng mảng và ảnh hưởng hệ thống bộ nhớ, dù đường duyệt không đi qua các vị trí trống đó.

Đây là hai câu hỏi độc lập:

1. Cần đi qua những thành phần nào để duyệt toàn bộ?
2. Cần giữ những thành phần nào trong bộ nhớ?

Câu trả lời của câu thứ nhất không được dùng thay cho câu thứ hai.

Hệ số tải và lượng ánh xạ dự kiến giúp lập kế hoạch sức chứa. Trong mô hình ngưỡng mở rộng, để giữ khoảng N ánh xạ với hệ số tải f, có thể xét mức khoảng `N / f` trước quy tắc làm tròn và cấp phát. Đây không phải công thức API bảo đảm chính xác kích thước mảng.

Đừng cho rằng constructor nhận 100.000 là chắc chắn chứa 100.000 ánh xạ mà không mở rộng. Cũng đừng đặt một con số cực lớn chỉ để tránh suy nghĩ về dữ liệu dự kiến.

### 5.4. Sao chép có thể làm tăng bộ nhớ tạm thời

Tạo `LinkedHashMap` từ map có sẵn tạo cấu trúc map mới chứa các ánh xạ tương ứng. Việc sao chép ánh xạ dùng các tham chiếu khóa và giá trị; nó không tự sao chép sâu toàn bộ đối tượng.

Khi hai map cùng tồn tại, ứng dụng giữ cả hai cấu trúc. Nếu chuyển đổi map lớn chỉ để xuất một lần, hãy tính cả cấp phát và đỉnh dùng bộ nhớ, không chỉ thời gian vòng lặp ở cuối.

Sao chép `HashMap` sang `LinkedHashMap` cũng không khôi phục lịch sử chèn đã không được lưu. Map mới giữ trình tự nó gặp khi duyệt nguồn. Nếu nguồn không có thứ tự nghiệp vụ được cam kết, việc sao chép không làm thứ tự đó tự xuất hiện.

## 6. Lựa chọn từ yêu cầu và tải công việc

### 6.1. Tra cứu là chính, không yêu cầu thứ tự

Một bộ đếm chỉ được hỏi theo từng mã và hầu như không được duyệt toàn bộ có thể bắt đầu với `HashMap`. Ứng dụng không cần duy trì thứ tự chèn trong suốt vòng đời của bảng.

Nếu cuối cùng chỉ cần xuất theo mã tăng dần một lần, có thể sắp xếp kết quả ở bước xuất. Không nhất thiết phải giữ một thứ tự chèn rồi bỏ nó đi khi sắp xếp.

Đây là điểm xuất phát hợp lý theo yêu cầu, không phải lời hứa `HashMap` luôn nhanh nhất.

### 6.2. Cần theo lần đầu xuất hiện

Nếu mỗi mã chỉ xuất một lần và phải theo vị trí gặp lần đầu, `LinkedHashMap` mặc định phù hợp. Đoạn sau là mã minh họa một phần, giả sử `codes` là danh sách mã đã có:

~~~java
Map<String, Integer> counts = new LinkedHashMap<>();
for (String code : codes) {
    counts.merge(code, 1, Integer::sum);
}
for (Map.Entry<String, Integer> entry : counts.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}
~~~

Cập nhật số đếm của khóa tồn tại không làm đổi thứ tự chèn. Với đầu vào `B, A, B, C, A`, các khóa được xuất theo `B, A, C`.

Nếu dữ liệu đầu vào đến từ một nguồn không cam kết thứ tự, phải xác định quy tắc đầu vào trước. Cấu trúc giữ thứ tự chỉ giữ được trình tự mà chương trình thực sự cung cấp.

### 6.3. Có nhiều lần duyệt trên bảng thưa

Đây là tình huống đáng xem xét `LinkedHashMap` vì đường duyệt không quét các vị trí bảng trống. Tuy nhiên, cũng cần xem tại sao bảng thưa: cấu hình khởi tạo quá lớn có thể là vấn đề cần sửa.

Cân nhắc đồng thời số ánh xạ, mức thưa, số lượt duyệt, tỷ lệ cập nhật, phần việc trong thân vòng lặp và giới hạn bộ nhớ. Nếu yêu cầu cho phép, sửa cách tạo map có thể giải quyết gốc lãng phí tốt hơn việc chỉ đổi lớp.

### 6.4. Cần thứ tự khóa hoặc truy cập đồng thời

Thứ tự khóa tăng dần là bài toán khác với thứ tự chèn. `TreeMap` phù hợp với nhiều nhu cầu duy trì thứ tự khóa; sắp xếp danh sách kết quả có thể phù hợp khi chỉ cần một bản xuất cuối cùng. Cần phân biệt duy trì thứ tự suốt vòng đời với tạo kết quả có thứ tự tại một thời điểm.

Cả `HashMap` và `LinkedHashMap` đều không tự cung cấp đồng bộ hóa. Chọn lớp giữ thứ tự không giải quyết truy cập đồng thời.

Đặc biệt, với chế độ thứ tự truy cập, `get` có thể làm thay đổi thứ tự cấu trúc. Lập luận “các luồng chỉ đọc” cần xem lại nếu các lần đọc đó là truy cập như vậy. Quy tắc đồng bộ hóa phải bao phủ cách dùng thực tế, kể cả duyệt. [Java 17: LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html)

Nếu cần hợp đồng đồng thời, hãy chọn thiết kế đáp ứng yêu cầu đó rồi đánh giá lại thứ tự. Không có một lớp map tự động giải quyết tất cả các yêu cầu cùng lúc.

## 7. Duyệt theo dữ liệu cần dùng

### 7.1. Cần khóa và giá trị: ưu tiên entrySet

Hai đoạn minh họa một phần sau tính tổng trên map `scores` không có giá trị null:

~~~java
int total = 0;
for (String key : scores.keySet()) {
    total += scores.get(key);
}
~~~

~~~java
int total = 0;
for (Map.Entry<String, Integer> entry : scores.entrySet()) {
    total += entry.getValue();
}
~~~

Đoạn thứ nhất đã có khóa từ bộ duyệt rồi lại yêu cầu tra cứu theo khóa để lấy giá trị. Đoạn thứ hai lấy trực tiếp từ ánh xạ đang được duyệt.

Không nên gọi đoạn thứ nhất là bậc hai chỉ vì có thêm `get`: trong điều kiện băm tốt, các lượt tra cứu bổ sung vẫn có chi phí kỳ vọng hằng số mỗi lượt. Tuy vậy, chúng là công việc không cần làm khi đã có ánh xạ.

Nếu chỉ cần khóa, chọn `keySet()`. Nếu chỉ cần giá trị, chọn `values()`. Nếu cần cả hai, `entrySet()` thể hiện mục đích rõ ràng.

### 7.2. Mẫu keySet rồi get có thể đổi thứ tự truy cập

Trên `LinkedHashMap` có `accessOrder=true`, `get(key)` có thể chuyển ánh xạ xuống cuối ngay lúc bộ duyệt đang hoạt động. Mẫu vòng lặp đó có thể gặp `ConcurrentModificationException`.

Không xây dựng tính đúng của chương trình bằng cách trông chờ ngoại lệ chắc chắn xuất hiện. Cơ chế phát hiện sửa đổi của bộ duyệt chỉ là nỗ lực tốt nhất, không phải công cụ đồng bộ hóa. [Java 17: LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html)

Duyệt `entrySet` và đọc `entry.getValue()` tránh cả lượt tra cứu thừa và lượt truy cập theo khóa có thể đổi thứ tự. Điều này không cho phép luồng khác tự do sửa map cùng lúc; đó vẫn là vấn đề riêng.

Khi cần quan sát thứ tự hiện tại, cũng đừng gọi `get` liên tiếp chỉ để in các giá trị trên một map thứ tự truy cập. Chính thao tác quan sát có thể làm đổi thứ tự bạn đang quan tâm.

## 8. Chương trình đầy đủ và kết quả đã kiểm chứng

Lưu mã sau vào `LinkedHashMapCostDemo.java`. Chương trình Java 17+ không cần thư viện ngoài.

Nó kiểm tra cùng dữ liệu trong bốn map, ghi nhận các lần gọi `hashCode` trong hai cách duyệt, rồi đối chiếu thứ tự chèn và thứ tự truy cập. Đây là ví dụ chức năng, không phải phép đo thời gian.

~~~java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LinkedHashMapCostDemo {
    private static final class Key {
        private static int hashCalls;
        private final int id;

        private Key(int id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            hashCalls++;
            return id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && id == key.id;
        }
    }

    private static void fill(Map<Key, Integer> map) {
        map.put(new Key(30), 3);
        map.put(new Key(10), 1);
        map.put(new Key(20), 2);
    }

    private static List<Integer> ids(Map<Key, Integer> map) {
        List<Integer> result = new ArrayList<>();
        for (Key key : map.keySet()) {
            result.add(key.id);
        }
        return result;
    }

    private static int entrySum(Map<Key, Integer> map) {
        int sum = 0;
        for (Map.Entry<Key, Integer> entry : map.entrySet()) {
            sum += entry.getValue();
        }
        return sum;
    }

    private static void inspect(String label, Map<Key, Integer> map) {
        fill(map);
        Key.hashCalls = 0;
        int fromEntries = entrySum(map);
        int iteratorHashCalls = Key.hashCalls;

        Key.hashCalls = 0;
        int fromKeys = 0;
        for (Key key : map.keySet()) {
            fromKeys += map.get(key);
        }
        int keyThenGetHashCalls = Key.hashCalls;

        if (map.size() != 3 || fromEntries != 6 || fromKeys != 6) {
            throw new AssertionError("Unexpected mappings");
        }
        System.out.println(label + ": size=" + map.size()
                + ", sum=" + fromEntries
                + ", iteratorHashCalls=" + iteratorHashCalls
                + ", keyThenGetHashCalls=" + keyThenGetHashCalls);
    }

    private static void requireIds(Map<Key, Integer> map, List<Integer> expected) {
        if (!ids(map).equals(expected)) {
            throw new AssertionError("Unexpected LinkedHashMap order");
        }
    }

    public static void main(String[] args) {
        inspect("HashMap(requested=16)", new HashMap<Key, Integer>(16));
        inspect("HashMap(requested=4096)", new HashMap<Key, Integer>(4096));
        inspect("LinkedHashMap(requested=16)",
                new LinkedHashMap<Key, Integer>(16));
        inspect("LinkedHashMap(requested=4096)",
                new LinkedHashMap<Key, Integer>(4096));

        LinkedHashMap<Key, Integer> insertion = new LinkedHashMap<>();
        fill(insertion);
        requireIds(insertion, List.of(30, 10, 20));
        System.out.println("insertion/start=" + ids(insertion));
        insertion.put(new Key(30), 30);
        requireIds(insertion, List.of(30, 10, 20));
        System.out.println("insertion/replace=" + ids(insertion));
        insertion.remove(new Key(10));
        insertion.put(new Key(10), 10);
        requireIds(insertion, List.of(30, 20, 10));
        System.out.println("insertion/reinsert=" + ids(insertion));

        LinkedHashMap<Key, Integer> access =
                new LinkedHashMap<>(16, 0.75f, true);
        fill(access);
        System.out.println("access/start=" + ids(access));
        access.get(new Key(10));
        requireIds(access, List.of(30, 20, 10));
        System.out.println("access/afterGet10=" + ids(access));
        Key.hashCalls = 0;
        int accessSum = entrySum(access);
        int accessIteratorHashCalls = Key.hashCalls;
        requireIds(access, List.of(30, 20, 10));
        if (accessSum != 6) {
            throw new AssertionError("Unexpected access-order sum");
        }
        System.out.println("access/entrySum=" + accessSum
                + ", iteratorHashCalls=" + accessIteratorHashCalls);
        System.out.println("access/afterEntries=" + ids(access));
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 LinkedHashMapCostDemo.java
java LinkedHashMapCostDemo
~~~

Stdout của lần chạy kiểm chứng:

~~~text
HashMap(requested=16): size=3, sum=6, iteratorHashCalls=0, keyThenGetHashCalls=3
HashMap(requested=4096): size=3, sum=6, iteratorHashCalls=0, keyThenGetHashCalls=3
LinkedHashMap(requested=16): size=3, sum=6, iteratorHashCalls=0, keyThenGetHashCalls=3
LinkedHashMap(requested=4096): size=3, sum=6, iteratorHashCalls=0, keyThenGetHashCalls=3
insertion/start=[30, 10, 20]
insertion/replace=[30, 10, 20]
insertion/reinsert=[30, 20, 10]
access/start=[30, 10, 20]
access/afterGet10=[30, 20, 10]
access/entrySum=6, iteratorHashCalls=0
access/afterEntries=[30, 20, 10]
~~~

Mã đã biên dịch với `--release 17` và chạy thành công. Các kiểm tra trong chương trình xác nhận số ánh xạ, tổng và các thứ tự yêu cầu. Lựa chọn `--release 17` xác định mức API và mã đích tương thích; nó không có nghĩa JVM thực thi nhất thiết phải là đúng bản JDK 17.

### 8.1. Vì sao bốn tổng đều bằng sáu?

Hàm `fill` thêm ba cặp `30 -> 3`, `10 -> 1` và `20 -> 2`. Cả bốn map giữ cùng dữ liệu. Tính tổng giúp kiểm tra nội dung mà không giả định thứ tự duyệt `HashMap`.

Trong lần chạy này, duyệt `entrySet` để lấy giá trị không gọi lại `hashCode` của các khóa; bộ đếm bằng không. Duyệt `keySet` rồi gọi `get` cho mỗi khóa tạo ba lần gọi `hashCode`.

Đây là quan sát về các lời gọi vào phương thức khóa trên cách triển khai đã chạy. Hợp đồng `Map` không yêu cầu mọi cách triển khai phải có đúng bộ đếm như ví dụ.

Quan trọng hơn, bộ đếm không quan sát số vị trí bảng đã được quét. Bộ duyệt có thể đi qua cấu trúc nội bộ mà không gọi lại `hashCode`. Vì thế, hai dòng cùng in số không không chứng minh hai map có chi phí duyệt bằng nhau.

Chương trình không đo sức chứa thực, số byte, thời gian hay tỷ lệ tốc độ. Nhãn `requested` chủ động nói đây là tham số yêu cầu, không phải kết quả đọc mảng riêng tư.

### 8.2. Thứ tự chèn thay đổi khi nào?

Ba dòng `insertion` cho thấy:

1. Sau khi chèn ba khóa, thứ tự là `[30, 10, 20]`.
2. Thay giá trị khóa `30` vẫn giữ thứ tự đó.
3. Xóa khóa `10` rồi thêm lại làm thứ tự thành `[30, 20, 10]`.

Map không thêm một vị trí mới cho mỗi lần gọi `put` bất kể kết quả. Nó vẫn chỉ có một ánh xạ cho mỗi khóa bằng nhau. Việc duy trì liên kết phải phù hợp với ánh xạ thực sự được thêm, xóa hoặc cập nhật.

Hàm `requireIds` chỉ kiểm tra thứ tự của `LinkedHashMap`. Không có kiểm tra áp đặt thứ tự tương tự lên `HashMap`.

### 8.3. Đọc theo khóa khác với duyệt ánh xạ

Map `access` dùng chế độ thứ tự truy cập. `get(10)` đưa khóa `10` xuống cuối. Hàm `entrySum` sau đó đọc giá trị từ các ánh xạ mà không gọi `get` của map; thứ tự không đổi.

Hàm `ids` duyệt `keySet` và chỉ đọc mã trong khóa, không gọi `map.get`. Nhờ vậy, thao tác in thứ tự không tự tạo một lượt truy cập theo khóa.

Chương trình không cố ý vừa duyệt vừa gọi `get` trên map thứ tự truy cập. Ví dụ giữ hành vi ổn định để chứng minh rõ hai loại đọc có tác động khác nhau, thay vì phụ thuộc thời điểm phát sinh ngoại lệ.

### 8.4. Vì sao không dùng khóa đếm hashCode để đo tốc độ?

Bản thân thao tác tăng bộ đếm đã thêm công việc vào `hashCode`. Khóa đơn giản trong ví dụ giúp quan sát một cơ chế, nhưng không nhất thiết đại diện cho khóa thực tế của ứng dụng.

Một ví dụ chứng minh được tổng, thứ tự và các lời gọi đã quan sát không tự trở thành phép đo hiệu năng có thể suy rộng. Hãy dùng ví dụ để hiểu, rồi dùng phương pháp đo phù hợp khi cần quyết định từ số liệu.

## 9. Khi quyết định cần số liệu thời gian hoặc bộ nhớ

### 9.1. Viết rõ câu hỏi trước khi đo

“Lớp nào nhanh hơn?” là câu hỏi thiếu điều kiện. Có thể thay bằng:

- Với N ánh xạ và phân bố khóa xác định, thời gian duyệt toàn bộ thay đổi thế nào theo cách chuẩn bị bảng?
- Với tỷ lệ đọc, ghi và duyệt của dịch vụ, đổi map ảnh hưởng tổng tác vụ ra sao?
- Với cùng đối tượng khóa và giá trị, cấu trúc map trên JVM cụ thể giữ bao nhiêu bộ nhớ?

Đo riêng duyệt khác với đo cả xây dựng và duyệt. Đo phần cấu trúc khác với tính toàn bộ đồ thị đối tượng còn được tham chiếu. Phạm vi đo phải đi cùng kết quả.

### 9.2. Thời gian trên JVM có nhiều yếu tố gây nhiễu

Một vòng `System.nanoTime` quanh đoạn rất ngắn có thể bị ảnh hưởng bởi khởi động JVM, biên dịch JIT, loại bỏ tính toán không dùng và tải hệ thống. Nếu vừa tạo map vừa duyệt, phép đo có cả cấp phát và xây dựng.

Khi cần thí nghiệm vi mô, JMH là bộ công cụ OpenJDK để xây dựng và chạy phép đo trên JVM. Công cụ không tự sửa giả thuyết hoặc dữ liệu sai, nhưng hỗ trợ cách tổ chức đo phù hợp môi trường JVM. [OpenJDK: JMH](https://github.com/openjdk/jmh)

Cần ghi rõ phiên bản JVM, dữ liệu, loại khóa, cách băm, lượng ánh xạ, cấu hình map, tỷ lệ thao tác, cách dùng kết quả và phạm vi có tính cấp phát hay không. Đo cả hai phương án với điều kiện tương đương và diễn giải trong đúng phạm vi ấy.

### 9.3. Số byte cần quan sát bố cục thực tế

JOL của OpenJDK cung cấp công cụ phân tích bố cục và dung lượng đối tượng Java. Nếu cần con số byte, hãy gắn kết quả với phiên bản, cấu hình JVM và cách tính đối tượng chia sẻ. [OpenJDK: JOL](https://github.com/openjdk/jol)

Không lấy một hiệu số `Runtime.freeMemory` trước và sau một lần tạo map rồi coi đó là kích thước chính xác. Thu gom rác, vùng nhớ JVM và các đối tượng ngoài phạm vi có thể ảnh hưởng.

Bài này không yêu cầu cài các công cụ đó. Mục đích là phân biệt kiến thức có thể lập luận từ hợp đồng và cấu trúc với kết quả cần đo trên môi trường cụ thể.

## 10. Những kết luận dễ sai

| Kết luận | Cách sửa |
|---|---|
| LinkedHashMap luôn nhanh hơn vì duyệt O(N) | Nó có đường duyệt không quét bảng trống; thao tác khác và hằng số vẫn cần xét |
| HashMap luôn duyệt chậm | Khi C tương xứng N, đường duyệt cũng tuyến tính theo N; cần tải công việc cụ thể |
| LinkedHashMap không cần quan tâm sức chứa | Duyệt không phụ thuộc quét C, nhưng bảng vẫn chiếm bộ nhớ |
| Cùng O(C + N) nên bộ nhớ bằng nhau | Cùng bậc không có nghĩa cùng hệ số hoặc số byte |
| Mọi ánh xạ luôn tốn thêm đúng một số byte cố định | Phải xác định dạng nút, bố cục và cấu hình JVM |
| Giữ thứ tự nghĩa là sắp xếp khóa | Phải phân biệt thứ tự chèn, truy cập và thứ tự khóa |
| Sao chép sang LinkedHashMap khôi phục lịch sử chèn | Chỉ giữ được thứ tự gặp ở nguồn, không phục hồi thông tin đã mất |
| get chỉ đọc nên không thể sửa cấu trúc | Với chế độ truy cập, get có thể đổi thứ tự |
| hashCode không được gọi nghĩa là không quét bảng | Bộ đếm đó không quan sát mọi bước nội bộ |
| Một lần đo quyết định cho mọi dữ liệu | Kết quả gắn với dữ liệu, tác vụ và môi trường đã đo |

## 11. Thực hành và lời giải

### Bài 1. Kích thước không phải sức chứa

Một map có `size() = 200` nhưng được chuẩn bị cho lượng dữ liệu lớn hơn nhiều. Vì sao không thể thay mọi đại lượng trong chi phí duyệt bằng 200?

**Lời giải:** `size()` chỉ cho N, không cho C. Mô hình `HashMap` chứa cả C + N nên biết N chưa đủ mô tả tác động của bảng thưa. Đường duyệt `LinkedHashMap` theo N, nhưng phần lưu trữ của nó vẫn có bảng băm. Không được dùng kết luận về đường duyệt thay cho kết luận về dung lượng.

### Bài 2. Tra cứu theo khóa là chính

Ứng dụng chỉ hỏi tổng số lần xuất hiện của từng mã, ít duyệt toàn bộ và không yêu cầu thứ tự. Điểm xuất phát hợp lý là gì? Khi nào xem lại?

**Lời giải:** Có thể bắt đầu với `HashMap` vì thứ tự không phục vụ yêu cầu. Xem lại nếu đầu ra cần thứ tự chèn, tác vụ chuyển sang nhiều lượt duyệt trên bảng thưa hoặc số liệu đại diện chỉ ra vấn đề cần tối ưu. Nếu có truy cập đồng thời, phải đánh giá hợp đồng đồng thời riêng.

### Bài 3. Thứ tự lần đầu xuất hiện

Đầu vào là `B, A, B, C, A`. Cần đếm và xuất theo lần đầu gặp. Chọn gì và kết quả theo thứ tự nào?

**Lời giải:** `LinkedHashMap` mặc định cho thứ tự `B, A, C` với số đếm `2, 2, 1`. Các lần cập nhật số đếm không đổi vị trí chèn. Không chọn chế độ truy cập nếu muốn giữ lần đầu xuất hiện bất chấp các lượt cập nhật về sau.

### Bài 4. Rút gọn mô hình

Trong một họ dữ liệu có `C = 2N`, hãy rút gọn chi phí duyệt HashMap. Có thể kết luận thời gian bằng LinkedHashMap không?

**Lời giải:** `O(C + N) = O(3N) = O(N)`. Hai đường duyệt cùng bậc tuyến tính dưới giả định ấy. Không thể kết luận thời gian bằng nhau vì Big O bỏ qua hệ số và cách truy cập bộ nhớ.

### Bài 5. Tỷ số mô hình không phải tỷ số tốc độ

Với `N = 100` và `C = 1.000.000`, một người nói LinkedHashMap nhanh hơn đúng 10.001 lần. Hãy sửa câu đó.

**Lời giải:** Có thể nói mô hình HashMap có thành phần quét bảng rất lớn so với số ánh xạ; LinkedHashMap không có phần quét vị trí trống đó khi duyệt. Không thể đổi tỷ số số hạng thành tỷ số thời gian. Cần đo nếu cần một con số tốc độ.

### Bài 6. Sửa vòng lặp có công việc thừa

Bạn cần cả khóa và giá trị trên `LinkedHashMap` thứ tự truy cập, nhưng đang duyệt `keySet` rồi gọi `get`. Nêu hai lý do đổi sang `entrySet`.

**Lời giải:** Có thể lấy trực tiếp cả khóa và giá trị từ ánh xạ, tránh lượt tra cứu thừa. Đồng thời, `get` có thể đổi thứ tự trong khi bộ duyệt hoạt động; `entry.getValue()` không tạo truy cập kiểu đó. Việc đổi cách duyệt không tự giải quyết sửa đổi từ luồng khác.

Mẫu minh họa:

~~~java
for (Map.Entry<String, Integer> entry : scores.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}
~~~

### Bài 7. Duyệt và lưu trữ là hai câu hỏi

Một báo cáo nói: “LinkedHashMap duyệt độc lập sức chứa nên đặt sức chứa một triệu cho vài chục ánh xạ không tốn gì thêm.” Sai ở đâu?

**Lời giải:** Báo cáo trộn chi phí đường duyệt với chi phí lưu trữ. Chuỗi giúp tránh quét bảng trống, nhưng bảng vẫn tồn tại để tra cứu. Sức chứa quá lớn có thể tăng dung lượng cấu trúc và ảnh hưởng hệ thống bộ nhớ.

### Bài 8. Giới hạn của bộ đếm

Chương trình in `iteratorHashCalls=0` cho hai yêu cầu sức chứa. Điều gì được quan sát và điều gì chưa được đo?

**Lời giải:** Đã quan sát số lần gọi phương thức `hashCode` của Key trong đoạn đang đếm và kiểm tra dữ liệu bằng tổng. Chưa đo số vị trí quét, sức chứa thực, thời gian, số byte hoặc thu gom rác. Kết quả bằng không không chứng minh chi phí duyệt bằng nhau.

### Bài 9. Sao chép không khôi phục lịch sử

Bạn chỉ nhận một HashMap, không có lịch sử, rồi tạo LinkedHashMap từ nó. Có thể hứa đầu ra theo thứ tự nhập ban đầu không?

**Lời giải:** Không. Map mới giữ thứ tự gặp khi duyệt nguồn. Nguồn không cam kết thứ tự ấy là lịch sử nhập. Muốn hứa thứ tự nhập, phải giữ thông tin ngay khi nhập hoặc có dữ liệu thứ tự riêng.

### Bài 10. Quyết định dựa vào tải công việc

Dịch vụ giữ map nhỏ, đọc theo khóa rất nhiều, mỗi giờ xuất toàn bộ một lần, không yêu cầu thứ tự. Đồng nghiệp muốn đổi chỉ vì “LinkedHashMap duyệt O(N)”. Cần kiểm tra gì?

**Lời giải:** Xác định phần thời gian đáng kể của dịch vụ, số ánh xạ, mức thưa, tỷ lệ thao tác, việc trong lần xuất và giới hạn bộ nhớ. Một lượt xuất mỗi giờ có thể không phải chỗ cần tối ưu. Nếu có vấn đề thực, đo tác vụ đại diện. Nếu sau này xuất hiện yêu cầu thứ tự, đó là lý do chức năng mới để đánh giá lại.

## 12. Tự kiểm tra lập luận lựa chọn

Trước khi đề xuất thay lớp map, hãy điền được câu:

“Ứng dụng cần ... về thứ tự, chủ yếu thực hiện ... với khoảng ... ánh xạ, duyệt ... lần và có giới hạn bộ nhớ ...; vì vậy chọn ... và sẽ đo ... nếu cần số liệu.”

`LinkedHashMap` đem lại hợp đồng thứ tự và đường duyệt theo số ánh xạ, đồng thời cần thêm thông tin liên kết và công việc duy trì. `HashMap` vẫn phù hợp với nhiều bảng tra cứu không yêu cầu thứ tự. Lựa chọn đúng nối được yêu cầu, mô hình chi phí và bằng chứng trong đúng phạm vi sử dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linked-hash-map-005">removeEldestEntry và mô hình bộ nhớ đệm LRU đơn giản</a>

<details>
<summary>Xem chi tiết</summary>

# removeEldestEntry và mô hình bộ nhớ đệm LRU đơn giản

## 1. Bắt đầu từ một cache có chỗ chứa hữu hạn

Một bộ nhớ đệm (cache) giữ tạm kết quả đã tính hoặc đã tải để lần yêu cầu sau có thể dùng lại. Ví dụ, chương trình đã tải mô tả của mã sản phẩm `A`; nếu người dùng hỏi `A` lần nữa, chương trình có thể trả bản đang giữ thay vì tải lại ngay. Việc tra cứu tìm thấy kết quả gọi là **cache hit**; không tìm thấy gọi là **cache miss**. Cache chỉ hữu ích khi chương trình chấp nhận dùng lại dữ liệu theo chính sách đã chọn.

Nếu cứ thêm kết quả vào một `Map` mà không loại bỏ, số entry có thể tăng theo số khóa đã gặp. Một cache đơn giản cần trả lời hai câu hỏi khác nhau:

1. Được giữ tối đa bao nhiêu entry?
2. Khi đã đủ chỗ mà có khóa mới, entry nào phải rời cache?

**LRU — Least Recently Used** chọn entry ít được sử dụng gần đây nhất. LRU không hỏi entry nào được tạo sớm nhất, giá trị nào nhỏ nhất hay khóa nào được dùng nhiều nhất trong toàn bộ lịch sử. Nó chỉ dựa vào thứ tự các lần truy cập mà cấu trúc đang theo dõi. Entry được truy cập thành công gần đây được đưa về phía mới nhất; entry nằm lâu nhất ở phía cũ nhất là ứng viên bị loại.

Trong bài này, thứ tự được viết từ trái sang phải:

```text
[ít được dùng gần đây nhất, ..., vừa được dùng gần đây nhất]
[LRU, ..., MRU]
```

`MRU` là Most Recently Used. Với giới hạn ba entry, chuỗi thao tác sau diễn tả điều cần xây:

| Thao tác | Thứ tự LRU → MRU sau thao tác | Giải thích |
|---|---|---|
| Thêm A | `[A]` | A là entry duy nhất |
| Thêm B | `[A, B]` | B mới được đưa vào |
| Thêm C | `[A, B, C]` | Đã đủ ba entry |
| Đọc A thành công | `[B, C, A]` | A vừa được dùng, B trở thành cũ nhất |
| Thêm D | `[C, A, D]` | D vào cache, B bị loại vì vượt giới hạn |

Việc B rời cache gọi là **loại bỏ entry (eviction)**. Loại một entry khỏi cache không có nghĩa là xóa sản phẩm trong cơ sở dữ liệu hay xóa dữ liệu gốc. Cache nên giữ dữ liệu có thể tải hoặc tạo lại. Nếu bỏ entry đồng nghĩa mất bản dữ liệu duy nhất, cần một thiết kế lưu trữ khác trước khi áp dụng chính sách loại bỏ.

Mục tiêu ở đây là hiểu và tự viết một cache LRU nhỏ, dùng một luồng, giới hạn theo số entry, dựa trên `LinkedHashMap`. Hai thành phần chính là **thứ tự truy cập** và **quy tắc loại bỏ sau khi thêm entry**. Hiểu từng thành phần riêng giúp tránh tạo ra một map có giới hạn nhưng lại loại sai entry.

## 2. LinkedHashMap cung cấp phần nào của cơ chế LRU?

### 2.1. Tra cứu theo khóa và giữ một thứ tự duyệt

`LinkedHashMap` kết hợp cơ chế tra cứu theo khóa của bảng băm với danh sách liên kết giữ thứ tự các entry. Khi dùng chế độ thứ tự truy cập, thứ tự duyệt của `keySet()`, `values()` và `entrySet()` phản ánh thứ tự từ ít được truy cập gần đây nhất đến mới được truy cập gần đây nhất.

Vì cấu trúc đã giữ thứ tự này, cache không cần tự tìm entry có thời gian truy cập nhỏ nhất bằng cách quét toàn bộ map sau mỗi lần thêm. Chương trình cũng không cần ghi một timestamp cho từng `get`. Thứ tự truy cập ở đây là thứ tự thao tác trên map, không phải lịch sử thời gian thực có ngày giờ.

Đoạn mã sau chỉ minh họa cấu hình, chưa phải một chương trình chạy độc lập:

```java
LinkedHashMap<String, String> entries =
        new LinkedHashMap<>(16, 0.75f, true);
```

Ba đối số có nhiệm vụ khác nhau:

| Đối số | Ý nghĩa trong ví dụ | Có phải giới hạn số entry của cache không? |
|---|---|---|
| `16` | Dung lượng ban đầu yêu cầu cho bảng băm | Không |
| `0.75f` | Hệ số tải của bảng băm | Không |
| `true` | Bật thứ tự truy cập (`accessOrder`) | Không; chỉ chọn cách duy trì thứ tự |

`new LinkedHashMap<>(3)` không tạo ra cache chỉ giữ ba entry. Dung lượng ban đầu có thể liên quan đến việc cấp phát và mở rộng bảng băm; nó không cấm map chứa nhiều hơn số đó. Giới hạn cache phải được thể hiện bằng một chính sách riêng.

### 2.2. Thứ tự chèn và thứ tự truy cập trả lời hai câu hỏi khác nhau

Constructor mặc định của `LinkedHashMap` dùng **thứ tự chèn**. Trong chế độ đó, đọc A sau khi đã chèn A, B, C không chuyển A xuống cuối. Nếu luôn loại entry đứng đầu, chính sách gần với FIFO — First In, First Out: entry vào trước rời trước.

Constructor ba đối số với `accessOrder=true` dùng **thứ tự truy cập**. Khi `get(A)` tìm thấy A, entry A trở thành mới được dùng gần đây nhất. Sau đó loại entry đứng đầu mới phù hợp với LRU.

Vì vậy, một cache LRU có giới hạn cần cả hai phần:

```text
accessOrder=true
    → giữ đúng thứ tự sử dụng

removeEldestEntry trả về size() > maximumEntries
    → loại entry đầu khi vừa thêm vượt giới hạn
```

Chỉ bật `accessOrder=true` thì map vẫn có thể tăng không giới hạn. Chỉ ghi đè `removeEldestEntry` nhưng giữ thứ tự chèn thì có giới hạn số entry, song chưa phải LRU.

### 2.3. Truy cập nào làm thay đổi độ mới?

Đối với các thao tác dùng trong bài, cần nhớ:

| Thao tác trên map theo thứ tự truy cập | Thay đổi số entry? | Ảnh hưởng thứ tự |
|---|---|---|
| `get(key)` tìm thấy khóa | Không | Entry trở thành mới nhất |
| `get(key)` không tìm thấy | Không | Không có entry để chuyển |
| `put(key, value)` với khóa mới | Có trước khi xét loại bỏ | Entry mới đứng cuối; có thể loại entry đầu |
| `put(key, value)` với khóa đã tồn tại | Không | Cập nhật giá trị và đưa entry đó về phía mới nhất |
| `containsKey(key)` | Không | Không tính là truy cập để đổi thứ tự |
| Duyệt `entrySet()` hoặc sao chép `keySet()` | Không | Bản thân việc duyệt không làm mới entry |

Không nên suy ra rằng mọi phương thức có vẻ như “đọc” đều giữ nguyên cấu trúc. `get` có thể đổi liên kết thứ tự dù không đổi `size()`. Ngược lại, `containsKey` xác nhận sự tồn tại nhưng không thay thế ý nghĩa sử dụng của `get` trong chính sách LRU.

Tài liệu còn quy định các thao tác tạo truy cập như `getOrDefault`, `putIfAbsent`, các phương thức `compute` và `merge` khi entry tương ứng tồn tại sau thao tác. Các phương thức `replace` chỉ tạo truy cập khi giá trị thực sự được thay thế. Với `putAll`, từng mapping tạo một truy cập theo thứ tự duyệt của `entrySet()` ở map đầu vào. Khi mở rộng API cache, cần kiểm tra đúng hợp đồng của phương thức dùng thêm; không mặc định rằng chỉ `get` mới tác động đến LRU.

## 3. removeEldestEntry là điểm quyết định loại bỏ

### 3.1. Chữ ký và ý nghĩa kết quả boolean

`LinkedHashMap` khai báo phương thức `protected boolean removeEldestEntry(Map.Entry<K,V> eldest)`. Đây là một phương thức được lớp cung cấp để lớp con ghi đè. Tên phương thức không có nghĩa rằng chính đoạn ghi đè luôn phải tự gọi `remove`.

Đoạn cốt lõi sau minh họa phần ghi đè bên trong một lớp con; nó không phải tệp Java độc lập:

```java
@Override
protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > maximumEntries;
}
```

Đọc đoạn mã theo ba bước:

1. `LinkedHashMap` vừa thêm một entry mới và hỏi chính sách xem có nên bỏ entry cũ nhất không.
2. `eldest` là entry đang đứng đầu thứ tự của map: cũ nhất theo thứ tự chèn hoặc ít được truy cập gần đây nhất theo thứ tự truy cập.
3. Kết quả `true` yêu cầu map loại entry đó; kết quả `false` giữ nó.

Giá trị trả về là quyết định `boolean`, không phải entry bị loại. Cài đặt mặc định trả về `false`, nên một `LinkedHashMap` thông thường không tự trở thành cache có giới hạn.

### 3.2. Thời điểm gọi: sau khi thêm entry mới

Điểm quan trọng nhất là phương thức được gọi sau khi `put` hoặc `putAll` chèn một entry mới vào map. Khi đang kiểm tra điều kiện, `size()` đã tính cả entry vừa được chèn.

Với giới hạn ba:

```text
Trước put(D):              [B, C, A]       size=3
Sau khi D được chèn:       [B, C, A, D]    size=4
removeEldestEntry(B):      4 > 3 → true
Sau khi loại B:           [C, A, D]       size=3
```

Điều kiện đúng là `size() > maximumEntries`. Nếu viết `size() >= maximumEntries`, entry thứ ba đã kích hoạt loại bỏ ngay khi map vừa đạt giới hạn ba; cache chỉ giữ được tối đa hai entry sau các lần thêm thông thường.

`get` không gọi cơ chế loại bỏ này chỉ vì một lần đọc vừa diễn ra. Cập nhật một khóa đã có bằng `put` có thể thay đổi thứ tự truy cập nhưng không thêm entry mới để kích hoạt điểm kiểm tra này. Cần phân biệt **làm mới thứ tự** với **kích hoạt kiểm tra sau khi thêm**.

Với `putAll`, đừng hình dung rằng Java nhận toàn bộ dữ liệu rồi chỉ gọi chính sách một lần sau cùng. Các entry mới được chèn lần lượt; chính sách được xét sau các lần chèn đó. Kết quả giữ lại phụ thuộc cả vào thứ tự truy cập được tạo ra trong quá trình nạp.

### 3.3. Một lần quyết định chỉ yêu cầu loại entry đầu

Một lần `true` yêu cầu loại một entry `eldest`. Đây không phải câu lệnh “lặp cho đến khi mọi giới hạn tùy ý đều được thỏa mãn”. Trong thiết kế đơn giản, giới hạn được cố định từ constructor, map bắt đầu rỗng và mỗi `put` khóa mới chỉ làm số entry tăng một. Vì thế kiểm tra rồi bỏ một entry là đủ để quay về giới hạn.

Nếu đổi một giới hạn đang là 100 xuống 2 trong lúc cache đã chứa 100 entry, không thể chỉ sửa biến rồi mong một lần hook dọn tất cả. Cần một thao tác thu gọn có chủ đích. Các lớp trong bài giữ giới hạn cố định để không đưa thêm trách nhiệm đó vào API.

### 3.4. Không tự xóa rồi đồng thời trả về true

Cách rõ ràng trong bài là để phần ghi đè chỉ tính quyết định. Không gọi `remove(eldest.getKey())` ở đây rồi lại trả về `true`. Tài liệu cho phép phần ghi đè trực tiếp sửa map, nhưng khi làm như vậy phải trả về `false`; nếu vừa sửa map vừa trả về `true`, ảnh hưởng của lần gọi không được quy định. Một cache theo số entry không cần dùng cách phức tạp đó.

Không nên dùng phương thức này để tải dữ liệu, phát sinh nghiệp vụ hoặc thực hiện tác vụ chậm. Nó đang chạy trong đường xử lý thêm entry của map. Giữ quyết định ngắn, dễ kiểm tra sẽ giúp hiểu được khi nào dữ liệu bị loại.

## 4. Chương trình 1: quan sát chính xác thời điểm hook chạy

Lưu chương trình đầy đủ dưới đây vào tệp `LinkedHashMapLruHookDemo.java`. Ví dụ dùng Java 17 trở lên. Biến đếm và các dòng `hook:` chỉ giúp quan sát cơ chế trong bài.

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class LinkedHashMapLruHookDemo {
    static final class LruMap<K, V> extends LinkedHashMap<K, V> {
        private final int maximumEntries;
        private int hookCalls;

        LruMap(int maximumEntries) {
            super(16, 0.75f, true);
            if (maximumEntries < 0) {
                throw new IllegalArgumentException("negative limit");
            }
            this.maximumEntries = maximumEntries;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            hookCalls++;
            boolean evict = size() > maximumEntries;
            System.out.println("hook: size=" + size()
                    + ", eldest=" + eldest.getKey()
                    + ", evict=" + evict);
            return evict;
        }
    }

    public static void main(String[] args) {
        LruMap<String, Integer> cache = new LruMap<>(3);
        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);
        System.out.println("initialLruToMru=" + cache.keySet());

        System.out.println("getA=" + cache.get("A"));
        System.out.println("afterGetA=" + cache.keySet());

        cache.put("D", 4);
        System.out.println("afterPutD=" + cache.keySet());
        System.out.println("hasB=" + cache.containsKey("B"));
        System.out.println("size=" + cache.size());

        int beforeUpdate = cache.hookCalls;
        cache.put("C", 30);
        System.out.println("afterExistingPut=" + cache);
        System.out.println("hookCountUnchanged="
                + (beforeUpdate == cache.hookCalls));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 LinkedHashMapLruHookDemo.java
java LinkedHashMapLruHookDemo
```

Stdout:

```text
hook: size=1, eldest=A, evict=false
hook: size=2, eldest=A, evict=false
hook: size=3, eldest=A, evict=false
initialLruToMru=[A, B, C]
getA=1
afterGetA=[B, C, A]
hook: size=4, eldest=B, evict=true
afterPutD=[C, A, D]
hasB=false
size=3
afterExistingPut={A=1, D=4, C=30}
hookCountUnchanged=true
```

### 4.1. Đọc kết quả theo từng giai đoạn

Ba lần `put` đầu đều thêm khóa mới nên xuất hiện ba dòng `hook:`. Ở lần đầu, entry A vừa được thêm cũng chính là `eldest`, vì map chỉ có một entry. `size=1`, `2`, `3` đều không vượt ba nên không entry nào bị loại.

`get("A")` trả về `1` và chuyển A về cuối: `[A, B, C]` thành `[B, C, A]`. Không xuất hiện dòng `hook:` giữa `getA` và `afterGetA`. Đó là bằng chứng rằng cập nhật thứ tự bởi một lần đọc không đồng nghĩa với gọi `removeEldestEntry`.

Khi thêm D, dòng `hook: size=4, eldest=B, evict=true` cho thấy đủ ba điều: kích thước đã tính D, B đang đứng đầu thứ tự truy cập, và điều kiện vượt giới hạn đã đúng. Sau khi `put` kết thúc, map còn `[C, A, D]`, B không tồn tại, `size()` trở về ba.

Cuối cùng, `put("C", 30)` thay giá trị của khóa C đã có. Số entry không tăng. C trở thành mới nhất nên map được in là `{A=1, D=4, C=30}`; `hookCountUnchanged=true` xác nhận không có lần gọi hook mới trong thao tác cập nhật đó.

### 4.2. Vai trò của từng chi tiết triển khai

`super(16, 0.75f, true)` bật thứ tự truy cập ngay khi tạo map. `maximumEntries` là trường `final`, tách riêng giới hạn nghiệp vụ khỏi dung lượng bảng băm. Kiểm tra số âm giúp lỗi cấu hình được báo tại nơi tạo đối tượng. `@Override` giúp trình biên dịch phát hiện nếu gõ sai tên hoặc chữ ký của phương thức.

`eldest.getKey()` được dùng để in entry sắp được xét. Quyết định theo số entry không cần đọc khóa hay giá trị, vì câu hỏi chỉ là `size()` có vượt giới hạn hay không. Không nên thêm điều kiện theo tên khóa khi đang muốn học một chính sách LRU thuần túy.

Các thao tác in `keySet()` và in toàn bộ map phản ánh thứ tự đang có. Chúng không âm thầm gọi `get` cho từng khóa để làm mới lại toàn bộ thứ tự. Chính vì vậy ví dụ có thể dùng các dòng in đó để quan sát LRU.

## 5. Chương trình 2: cùng giới hạn, FIFO và LRU loại khác nhau

Một lỗi dễ mắc là ghi đè đúng `removeEldestEntry` nhưng quên đối số `true`. Chương trình sau tạo hai map với cùng giới hạn ba; chỉ khác chế độ duy trì thứ tự. Lưu vào `LinkedHashMapLruFifoDemo.java`.

```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LinkedHashMapLruFifoDemo {
    static final class BoundedMap<K, V> extends LinkedHashMap<K, V> {
        private final int maximumEntries;

        BoundedMap(int maximumEntries, boolean accessOrder) {
            super(16, 0.75f, accessOrder);
            if (maximumEntries < 0) {
                throw new IllegalArgumentException("negative limit");
            }
            this.maximumEntries = maximumEntries;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maximumEntries;
        }
    }

    public static void main(String[] args) {
        BoundedMap<String, String> fifo = new BoundedMap<>(3, false);
        BoundedMap<String, String> lru = new BoundedMap<>(3, true);

        for (String key : List.of("A", "B", "C")) {
            fifo.put(key, key);
            lru.put(key, key);
        }

        fifo.get("A");
        lru.get("A");
        System.out.println("fifoBeforeD=" + fifo.keySet());
        System.out.println("lruBeforeD=" + lru.keySet());

        fifo.put("D", "D");
        lru.put("D", "D");
        System.out.println("fifoAfterD=" + fifo.keySet());
        System.out.println("lruAfterD=" + lru.keySet());
        System.out.println("fifoHasA=" + fifo.containsKey("A"));
        System.out.println("lruHasA=" + lru.containsKey("A"));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 LinkedHashMapLruFifoDemo.java
java LinkedHashMapLruFifoDemo
```

Stdout:

```text
fifoBeforeD=[A, B, C]
lruBeforeD=[B, C, A]
fifoAfterD=[B, C, D]
lruAfterD=[C, A, D]
fifoHasA=false
lruHasA=true
```

Sau khi thêm A, B, C rồi đọc A, map `fifo` vẫn là `[A, B, C]`; map `lru` đã thành `[B, C, A]`. Khi D được thêm, cả hai đều thấy kích thước bốn và đều trả về `true`, nhưng `eldest` khác nhau:

| Map | Entry đứng đầu trước khi thêm D | Entry bị loại | Thứ tự còn lại |
|---|---|---|---|
| Thứ tự chèn (`false`) | A | A | `[B, C, D]` |
| Thứ tự truy cập (`true`) | B | B | `[C, A, D]` |

`fifoHasA=false` trong khi `lruHasA=true` là khác biệt thực tế của chính sách. A vừa được dùng nhưng vẫn rời cache FIFO vì tuổi chèn của nó không đổi. Trong cache LRU, lần dùng A đã bảo vệ A khỏi lần loại bỏ kế tiếp; B là entry không được dùng lâu nhất.

Cả hai cấu trúc đều có giới hạn số entry. Do đó, chỉ kiểm tra `size() <= maximumEntries` chưa đủ để xác nhận một cache thực hiện đúng LRU. Cần kiểm tra cả **entry nào bị loại** và **thứ tự sau các lần truy cập**.
## 6. Chương trình 3: bọc map thành một cache nhỏ có hợp đồng rõ ràng

### 6.1. Vì sao không chỉ trả thẳng LinkedHashMap ra ngoài?

Kế thừa `LinkedHashMap` như hai ví dụ trước giúp nhìn rõ cơ chế. Tuy nhiên, nếu đưa toàn bộ đối tượng map cho nơi sử dụng, người gọi nhận cả API `Map`, các view liên kết với map và nhiều đường thay đổi dữ liệu. Lúc đó hợp đồng “cache này hỗ trợ những thao tác nào” khó kiểm soát hơn.

Cách thứ hai là **chứa một map ở bên trong**. Lớp cache chỉ công bố các thao tác cần dùng: `get`, `put`, `containsKey`, `size` và một bản sao thứ tự khóa để quan sát. Map nội bộ là `private`; người gọi không nhận trực tiếp `keySet()` hay `entrySet()` đang liên kết với nó.

Cache trong ví dụ chọn các quy tắc cụ thể:

- Giới hạn không âm, được cố định khi tạo cache.
- Giới hạn bằng 0 hợp lệ: dữ liệu có thể được đưa vào rồi bị loại ngay, nên không giữ lại entry.
- Không nhận khóa hoặc giá trị `null`.
- `get` trả về `null` chỉ khi không có entry tương ứng.
- `containsKey` kiểm tra sự tồn tại nhưng không làm mới vị trí LRU.
- `keysLruToMru()` trả về một danh sách sao chép, từ cũ nhất đến mới nhất.

Đây là chính sách của lớp bọc, không phải giới hạn vốn có của `LinkedHashMap`. Bản thân `LinkedHashMap` cho phép khóa `null` và giá trị `null`. Việc cấm chúng ở đây giúp phân biệt cache miss bằng kết quả `get == null` mà không cần thêm trạng thái chứa giá trị rỗng.

Lưu chương trình đầy đủ vào `SimpleLruCacheDemo.java`:

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SimpleLruCacheDemo {
    static final class SimpleLruCache<K, V> {
        private final LinkedHashMap<K, V> entries;

        SimpleLruCache(int maximumEntries) {
            if (maximumEntries < 0) {
                throw new IllegalArgumentException("negative limit");
            }
            entries = new LinkedHashMap<K, V>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > maximumEntries;
                }
            };
        }

        V get(K key) {
            Objects.requireNonNull(key, "key");
            return entries.get(key);
        }

        void put(K key, V value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            entries.put(key, value);
        }

        boolean containsKey(K key) {
            Objects.requireNonNull(key, "key");
            return entries.containsKey(key);
        }

        int size() {
            return entries.size();
        }

        List<K> keysLruToMru() {
            return new ArrayList<>(entries.keySet());
        }
    }

    private static void expectNullPointer(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            System.out.println(label + "=NullPointerException");
        }
    }

    public static void main(String[] args) {
        SimpleLruCache<String, String> cache = new SimpleLruCache<>(2);
        cache.put("A", "a");
        cache.put("B", "b");
        System.out.println("getA=" + cache.get("A"));
        System.out.println("afterHit=" + cache.keysLruToMru());
        System.out.println("getMissing=" + cache.get("missing"));
        System.out.println("afterMiss=" + cache.keysLruToMru());
        System.out.println("hasB=" + cache.containsKey("B"));
        System.out.println("afterContains=" + cache.keysLruToMru());

        cache.put("C", "c");
        System.out.println("afterPutC=" + cache.keysLruToMru());
        System.out.println("getEvictedB=" + cache.get("B"));
        cache.put("A", "a2");
        System.out.println("afterUpdateA=" + cache.keysLruToMru());
        System.out.println("size=" + cache.size());

        List<String> snapshot = cache.keysLruToMru();
        snapshot.clear();
        System.out.println("afterSnapshotClear=" + cache.keysLruToMru());

        SimpleLruCache<String, String> disabled = new SimpleLruCache<>(0);
        disabled.put("A", "a");
        System.out.println("zeroLimitSize=" + disabled.size());
        System.out.println("zeroLimitGetA=" + disabled.get("A"));

        try {
            new SimpleLruCache<String, String>(-1);
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.out.println("negativeLimit=IllegalArgumentException");
        }

        expectNullPointer("nullKey", () -> cache.put(null, "value"));
        expectNullPointer("nullValue", () -> cache.put("X", null));
        System.out.println("afterRejectedNulls=" + cache.keysLruToMru());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SimpleLruCacheDemo.java
java SimpleLruCacheDemo
```

Stdout:

```text
getA=a
afterHit=[B, A]
getMissing=null
afterMiss=[B, A]
hasB=true
afterContains=[B, A]
afterPutC=[A, C]
getEvictedB=null
afterUpdateA=[C, A]
size=2
afterSnapshotClear=[C, A]
zeroLimitSize=0
zeroLimitGetA=null
negativeLimit=IllegalArgumentException
nullKey=NullPointerException
nullValue=NullPointerException
afterRejectedNulls=[C, A]
```

### 6.2. Hit, miss và kiểm tra sự tồn tại

Sau khi thêm A và B, thứ tự là `[A, B]`. `get("A")` tìm thấy `a`, vì thế thứ tự thành `[B, A]`. `get("missing")` trả về `null` và không tạo entry mới; thứ tự vẫn `[B, A]`.

`containsKey("B")` trả về `true` nhưng `afterContains` vẫn là `[B, A]`. Nếu việc kiểm tra tồn tại được coi như một lần dùng dữ liệu trong ứng dụng, cần chọn thao tác phù hợp với ý nghĩa đó. Không thể gọi `containsKey` rồi suy ra B đã được làm mới trong LRU.

Khi C được thêm vào cache giới hạn hai entry, B rời cache. `get("B")` sau đó trả về `null`, hoàn toàn bình thường dù B từng tồn tại trước đó. Một cache miss không chứng minh dữ liệu gốc đã biến mất; nó chỉ chứng minh cache hiện tại không có entry mà yêu cầu đang cần.

`put("A", "a2")` cập nhật khóa đã có, nên kích thước vẫn hai. Sau thao tác này A là mới nhất và thứ tự là `[C, A]`. Nếu ghi lại cùng một giá trị cho khóa đã có, `put` vẫn là một thao tác truy cập theo hợp đồng của map theo thứ tự truy cập; không nên dùng so sánh giá trị cũ/mới để tự suy ra độ mới không đổi.

### 6.3. Bản sao danh sách khóa bảo vệ điều gì?

`new ArrayList<>(entries.keySet())` duyệt thứ tự hiện tại rồi chứa các tham chiếu khóa trong một danh sách mới. Gọi `snapshot.clear()` chỉ xóa phần tử khỏi danh sách mới; dòng `afterSnapshotClear=[C, A]` cho thấy cache không bị xóa theo.

Điều này khác với trả trực tiếp `entries.keySet()`: view đó liên kết với map, và các thao tác loại bỏ được hỗ trợ trên view sẽ tác động đến map. Không lộ view giúp lớp cache giữ các đường thay đổi trạng thái ở một nơi dễ kiểm tra.

Tuy nhiên, đây chỉ là bản sao của **cấu trúc danh sách**. Nó không sao chép sâu từng đối tượng khóa. Cache cũng đang giữ tham chiếu giá trị, không tự tạo bản sao sâu dữ liệu. Nếu khóa là một đối tượng có thể thay đổi, không được sửa các trường tham gia `equals`/`hashCode` khi khóa đang nằm trong map. Nếu giá trị có thể thay đổi, cần quyết định nơi sử dụng có được phép sửa nó hay không. Chính sách LRU không giải quyết thay các hợp đồng đó.

### 6.4. Giới hạn bằng 0, bằng 1 và số âm

Với giới hạn bằng 0, lần `put("A", "a")` chèn entry đầu tiên, `size()` tạm thời bằng một, rồi hook trả về `1 > 0`. Entry duy nhất đó cũng chính là `eldest` và bị loại. Sau khi `put` trả về, `size()` bằng 0 và `get("A")` là `null`.

Cách xử lý này có thể dùng để cấu hình tắt lưu đệm mà vẫn giữ cùng đường gọi API. Nó là quyết định được nêu rõ trong ví dụ; một API khác có thể chọn từ chối giới hạn 0, nhưng phải mô tả và kiểm tra chính sách của mình nhất quán.

Với giới hạn bằng 1, entry đầu tiên được giữ vì `1 > 1` là `false`. Thêm khóa khác làm kích thước tạm thời bằng hai và entry cũ bị loại. Đây cũng là cách dễ phát hiện lỗi dùng `>=`: với điều kiện sai đó, ngay entry đầu tiên đã bị loại.

Giới hạn âm được từ chối bằng `IllegalArgumentException` ngay tại constructor. Không dùng một số âm làm tín hiệu mơ hồ cho “không giới hạn” trong lớp này, vì như vậy điều kiện loại bỏ sẽ không còn khớp hợp đồng đang học.

### 6.5. Từ chối null trước khi thay đổi map

`put` kiểm tra cả khóa và giá trị bằng `Objects.requireNonNull` trước khi gọi `entries.put`. Vì vậy, khi hai lần thử `put(null, "value")` và `put("X", null)` thất bại, không có entry mới nào được chèn, không entry cũ nào bị loại và thứ tự vẫn `[C, A]`.

Không nên kiểm tra giá trị sau khi đã chèn rồi mới ném lỗi. Trong cache có giới hạn, việc chèn một entry không hợp lệ có thể đã loại một entry hợp lệ khác. Kiểm tra trước thao tác thay đổi giữ cho hành vi báo lỗi dễ hiểu.

Nếu ứng dụng cần lưu kết quả “không tìm thấy” để tránh tải lại liên tục, có thể cần một kiểu kết quả hoặc một giá trị đại diện được thiết kế riêng. Chỉ bỏ kiểm tra `null` trong ví dụ sẽ làm điều kiện `get(key) == null` trở nên nhập nhằng: đó có thể là miss hoặc là hit với giá trị `null`. Bài này giữ chính sách không có giá trị `null` để đường hit/miss rõ ràng.

## 7. Chương trình 4: lấy từ cache, thiếu thì tải

### 7.1. Đường xử lý một yêu cầu

Một lớp cache thường được dùng theo mẫu:

```text
Nhận khóa
    ↓
Tra cache bằng get
    ├─ Có giá trị → trả ngay; entry trở thành mới nhất
    └─ Không có → tải/tính kết quả
                      ↓
                put vào cache
                      ↓
           có thể loại entry LRU nếu đầy
                      ↓
                 trả kết quả
```

Hàm tải dữ liệu, gọi là `loader` trong chương trình, nhận khóa và tạo giá trị. Ở đây hàm chỉ đổi chuỗi thành chữ hoa để kết quả và số lần gọi hoàn toàn xác định. Trong một ứng dụng thật, chi phí tải có thể lớn hơn nhiều; nguyên tắc kiểm tra cache trước vẫn giống nhau.

Lưu chương trình sau vào `ReadThroughLruDemo.java`. Đây là chương trình độc lập với ba ví dụ trước; không cần chép thêm lớp từ tệp khác.

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class ReadThroughLruDemo {
    static final class Cache<K, V> {
        private final LinkedHashMap<K, V> entries;

        Cache(int maximumEntries) {
            if (maximumEntries < 0) {
                throw new IllegalArgumentException("negative limit");
            }
            entries = new LinkedHashMap<K, V>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > maximumEntries;
                }
            };
        }

        V getOrLoad(K key, Function<? super K, ? extends V> loader) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(loader, "loader");
            V cached = entries.get(key);
            if (cached != null) {
                return cached;
            }
            V loaded = Objects.requireNonNull(loader.apply(key), "loaded value");
            entries.put(key, loaded);
            return loaded;
        }

        List<K> keysLruToMru() {
            return new ArrayList<>(entries.keySet());
        }
    }

    private static int loadCount;

    private static String load(String key) {
        loadCount++;
        System.out.println("load=" + key);
        return key.toUpperCase(Locale.ROOT);
    }

    public static void main(String[] args) {
        Cache<String, String> cache = new Cache<>(2);
        for (String key : List.of("a", "b", "a", "c", "b")) {
            String value = cache.getOrLoad(key, ReadThroughLruDemo::load);
            System.out.println("result=" + key + ":" + value
                    + ", order=" + cache.keysLruToMru());
        }
        System.out.println("loadCount=" + loadCount);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ReadThroughLruDemo.java
java ReadThroughLruDemo
```

Stdout:

```text
load=a
result=a:A, order=[a]
load=b
result=b:B, order=[a, b]
result=a:A, order=[b, a]
load=c
result=c:C, order=[a, c]
load=b
result=b:B, order=[c, b]
loadCount=4
```

### 7.2. Năm yêu cầu nhưng chỉ bốn lần tải

Cache giữ tối đa hai entry. Từng yêu cầu được xử lý như sau:

| Yêu cầu | Hit hay miss? | Có gọi hàm tải không? | Thứ tự sau yêu cầu | Entry bị loại |
|---|---|---|---|---|
| `a` | Miss | Có, lần 1 | `[a]` | Không |
| `b` | Miss | Có, lần 2 | `[a, b]` | Không |
| `a` | Hit | Không | `[b, a]` | Không |
| `c` | Miss | Có, lần 3 | `[a, c]` | `b` |
| `b` | Miss | Có, lần 4 | `[c, b]` | `a` |

Yêu cầu thứ ba là bằng chứng cache đang dùng lại kết quả: trước dòng `result=a:A, order=[b, a]` không có dòng `load=a` mới. Yêu cầu này đồng thời làm a thành mới nhất, nên khi c được thêm, b phải rời cache.

Yêu cầu b cuối cùng lại phải tải vì b đã bị loại. LRU giúp ưu tiên dữ liệu được dùng gần đây; nó không bảo đảm một khóa chỉ được tải một lần trong suốt đời chương trình. Số lần tải phụ thuộc chuỗi yêu cầu và giới hạn cache.

`Locale.ROOT` giúp thao tác đổi chữ hoa không phụ thuộc locale mặc định của máy. Phần này chỉ giữ ví dụ có kết quả ổn định; nó không tham gia cơ chế LRU.

### 7.3. Vì sao kiểm tra cached != null là đủ ở đây?

Map nội bộ chỉ được thêm dữ liệu qua `getOrLoad`, và kết quả của `loader.apply(key)` được kiểm tra khác `null` trước `put`. Vì vậy không có entry với giá trị `null`; `entries.get(key)` trả `null` có thể được hiểu là miss.

Khóa và chính đối tượng `loader` cũng được kiểm tra trước. Với hợp đồng này, truyền `loader=null` là lỗi kể cả khi khóa đã có trong cache. Đó là lựa chọn kiểm tra đầu vào của API đang viết, không phải hành vi bắt buộc của mọi cache.

Nếu loader ném ngoại lệ, luồng chạy không đến `entries.put`; kết quả lỗi không tự trở thành một entry. Nếu loader trả về `null`, `requireNonNull` ném `NullPointerException`, và cache cũng không chèn một giá trị `null`. Loader trong ví dụ chỉ tính giá trị, không trực tiếp sửa chính cache.

Giá trị rỗng như chuỗi `""` vẫn là một đối tượng khác `null` và vẫn có thể được lưu. Đừng trộn lẫn “không có entry”, “có một giá trị rỗng theo nghiệp vụ” và “hàm tải bị lỗi” thành cùng một nhánh mà không xác định hợp đồng.

Với giới hạn bằng 0, `getOrLoad` vẫn có thể tải và trả giá trị cho yêu cầu hiện tại. Entry vừa đưa vào sẽ bị loại ngay, nên yêu cầu sau lại miss. “Không giữ kết quả” không có nghĩa rằng phương thức không được phép trả kết quả vừa tính.

## 8. Đọc đúng các giới hạn của mô hình

### 8.1. Giới hạn số entry không phải giới hạn byte

`size() > maximumEntries` đếm số mapping. Một entry giữ một chuỗi nhỏ và một entry giữ một cấu trúc dữ liệu rất lớn đều đóng góp một đơn vị vào `size()`. Do đó cache tối đa 100 entry không tự bảo đảm chỉ chiếm một số MB cố định.

Map, khóa, giá trị và các liên kết thứ tự đều có chi phí bộ nhớ. Giới hạn entry giúp chặn sự tăng số mapping theo số khóa từng gặp; nó không thay thế việc đánh giá kích thước và vòng đời của dữ liệu. Không gọi đây là một cơ chế đo hoặc quản lý chính xác dung lượng heap.

Việc loại entry chỉ bỏ tham chiếu từ cache đến dữ liệu đó. Nếu nơi khác còn giữ tham chiếu, đối tượng vẫn có thể được sử dụng. Ngay cả khi không còn tham chiếu mạnh nào, thời điểm thu gom rác do JVM quyết định; `removeEldestEntry` không phải lệnh yêu cầu JVM lập tức giải phóng một lượng byte xác định.

### 8.2. LRU không phải thời hạn sử dụng

LRU dựa vào độ gần đây của truy cập, còn thời hạn sử dụng thường được gọi là TTL — Time To Live. Một entry không được dùng trong một giờ vẫn có thể nằm trong cache LRU nếu chưa có lần thêm nào gây vượt giới hạn. Ngược lại, entry vừa mới được tải vẫn có thể bị loại nếu nhiều khóa khác tiếp tục được dùng.

`removeEldestEntry` không được bộ định thời gọi theo chu kỳ. Đồng hồ chạy qua một mốc không tự làm phương thức này chạy. Một lần `get` cũng không tự gọi hook để dọn dữ liệu hết hạn. Vì vậy không thể có TTL đúng nghĩa chỉ bằng cách đổi biểu thức điều kiện rồi giả định cache sẽ dọn liên tục.

Bài này chỉ thực hiện chính sách LRU theo số entry. Khi cần thời hạn, trọng số dung lượng hoặc chính sách làm mới dữ liệu, phải thiết kế riêng cách kiểm tra và loại bỏ tương ứng. Không nên gắn thêm tên các tính năng đó vào lớp chỉ vì nó đã có một phương thức loại entry.

### 8.3. Giữ kết quả không tự bảo đảm dữ liệu mới nhất

Nếu dữ liệu gốc thay đổi trong khi bản cũ vẫn còn trong cache, một hit có thể trả bản cũ. LRU chỉ quyết định ưu tiên giữ entry nào; nó không biết nguồn dữ liệu vừa được cập nhật. Việc xóa theo thay đổi nguồn, ghi lại giá trị mới hoặc lựa chọn khoảng thời gian chấp nhận dữ liệu cũ là một quyết định khác.

Cache này cũng không lưu dữ liệu qua lần khởi động chương trình. Tạo đối tượng cache mới bắt đầu với map rỗng. Không nên dùng ví dụ này thay cho cơ chế lưu trữ bền vững hoặc coi nó là nơi giữ bản duy nhất của dữ liệu cần bảo toàn.

### 8.4. get có thể thay đổi cấu trúc thứ tự

`LinkedHashMap` không tự đồng bộ cho truy cập đồng thời. Đặc biệt ở chế độ thứ tự truy cập, `get` có thể đổi liên kết thứ tự. Không thể chia thao tác thành “đọc thì không cần bảo vệ, chỉ put mới cần” rồi cho rằng map đã an toàn khi nhiều luồng cùng dùng.

Nếu cần nhiều luồng, mọi đường truy cập trạng thái liên quan phải được phối hợp theo một thiết kế nhất quán. Chỉ thêm một wrapper đồng bộ cho từng lệnh `get` và `put` riêng lẻ chưa làm cả chuỗi **kiểm tra thiếu → tải → đưa vào** trở thành nguyên tử. Hai luồng có thể cùng thấy miss và cùng tải một khóa.

Các chương trình ở đây chạy trong một luồng, không cung cấp cơ chế gộp các lần tải đồng thời, hủy tác vụ hay xử lý tác vụ đang chờ. Không suy ra các bảo đảm đó chỉ từ việc cache có thứ tự LRU. Khi chuyển sang yêu cầu đồng thời, cần xem xét lại hợp đồng trước khi chọn cách khóa và cách thực hiện công việc tải dữ liệu.

### 8.5. Duyệt map và gọi get trên chính map là một bẫy

Trong một map theo thứ tự truy cập, duyệt `keySet()` bằng iterator rồi gọi `get(key)` trên chính map có thể thay đổi thứ tự khi iterator đang chạy. Điều đó có thể dẫn đến `ConcurrentModificationException`, dù chương trình chỉ có một luồng.

Cơ chế fail-fast của iterator chỉ cố gắng phát hiện việc sửa cấu trúc; không nên dùng việc có hay không có ngoại lệ làm phép thử an toàn đồng thời. Nếu entry đã ở vị trí mới nhất, một số lần gọi cũng có thể không làm thứ tự thay đổi; không được từ một trường hợp chạy được suy ra cả mẫu duyệt là an toàn.

Khi chỉ cần quan sát cặp khóa–giá trị trong một luồng, có thể duyệt `entrySet()` và đọc `entry.getKey()`/`entry.getValue()` để tránh tự tạo truy cập `get` trên map. Khi API cache trả bản sao thứ tự khóa như ví dụ, thao tác `clear` trên bản sao không sửa cache. Tuy nhiên, nếu sau đó dùng từng khóa trong bản sao để gọi `cache.get`, các lời gọi đó vẫn sẽ làm mới entry trong cache thật. Có bản sao giúp tránh view liên kết; nó không biến `get` thành thao tác không ảnh hưởng LRU.

## 9. Kiểm tra một cache LRU như thế nào?

### 9.1. Kiểm tra cả dữ liệu lẫn thứ tự

Một kiểm tra tốt phải quan sát đúng chính sách, không chỉ kiểm tra số lượng. Với giới hạn ba, chuỗi `put(A)`, `put(B)`, `put(C)`, `get(A)`, `put(D)` phải thỏa:

- A vẫn có và D có trong cache.
- B không còn trong cache.
- Thứ tự cuối là `[C, A, D]`.
- Số entry cuối là ba.

Nếu chỉ kiểm tra số entry, một cache FIFO sai chính sách cũng có thể vượt qua. Nếu chỉ so sánh hai map bằng `Map.equals`, phép so sánh kiểm tra các mapping, không xác nhận thứ tự LRU. Tương tự, so sánh hai tập khóa không xác nhận thứ tự. Khi cần kiểm tra thứ tự, hãy chuyển nó thành một `List` rồi so sánh danh sách theo đúng thứ tự mong đợi.

Đừng vô tình làm thay đổi chính trạng thái đang kiểm tra: gọi `get` để kiểm tra sự tồn tại của hàng loạt khóa sẽ làm mới từng entry theo thứ tự gọi. Có thể dùng `containsKey` để kiểm tra có/không mà không làm mới, và dùng bản sao thứ tự để xác nhận LRU. Nếu chủ đích đang kiểm tra hiệu ứng của `get`, cần tính cả hiệu ứng đó vào kết quả mong đợi.

### 9.2. Những trường hợp biên đáng kiểm tra

| Trường hợp | Kết quả mong đợi theo các lớp cache trong bài | Lỗi có thể phát hiện |
|---|---|---|
| Thêm đúng bằng giới hạn | Giữ đủ số entry cho phép | Dùng `>=` thay vì `>` |
| Thêm vượt giới hạn một khóa mới | Loại entry đầu hiện tại | Không ghi đè hook hoặc sai điều kiện |
| Đọc thành công một khóa cũ | Đưa entry đó thành mới nhất | Quên `accessOrder=true` |
| Đọc khóa không có | Trả `null`, không tạo entry | Trộn đọc và tự thêm giá trị mặc định ngoài hợp đồng |
| `containsKey` trên entry cũ | Không đổi thứ tự | Hiểu nhầm mọi kiểm tra đều làm mới |
| `put` khóa đã tồn tại | Cập nhật và làm mới, không tăng số entry | Coi mọi `put` là một lần thêm khóa |
| Giới hạn 0 | Sau `put`, cache rỗng | Không định nghĩa rõ cấu hình tắt lưu đệm |
| Giới hạn âm | Constructor ném `IllegalArgumentException` | Để lỗi cấu hình trôi vào thời gian chạy sau đó |
| Khóa/giá trị `null` trong lớp bọc | Báo lỗi trước khi sửa map | Cho `null` lọt vào làm hit/miss nhập nhằng |
| Xóa danh sách khóa sao chép | Cache không đổi | Trả lộ view liên kết |

Trong phần tải theo yêu cầu, đếm số lần loader thực sự chạy là bằng chứng hữu ích: chuỗi `a, b, a, c, b` ở giới hạn hai phải tải bốn lần, không phải ba lần theo số khóa phân biệt và cũng không phải năm lần theo số yêu cầu.

Điều kiện `size() <= maximumEntries` của thiết kế này được hiểu sau khi các thao tác thêm công khai hoàn tất bình thường. Ngay trong hook, map có thể tạm thời có `maximumEntries + 1` entry. Một kiểm tra ném lỗi chỉ vì thấy kích thước tạm thời đó sẽ phủ nhận chính cơ chế thêm rồi loại đang dùng.

### 9.3. Chi phí của các thao tác

Với phân bố băm phù hợp, các thao tác cơ bản như `get` và `put` có thời gian trung bình hằng số theo mô hình của bảng băm. Việc duy trì thứ tự và loại entry đầu không đòi hỏi quét toàn bộ cache trong chính sách này. Đây là một lý do `LinkedHashMap` phù hợp để minh họa LRU nhỏ.

Tuy nhiên, không phải mọi phương thức của lớp bọc đều có cùng chi phí. `keysLruToMru()` sao chép n khóa vào danh sách mới nên tốn thời gian và bộ nhớ theo n. Các dòng in toàn bộ thứ tự trong ví dụ phục vụ quan sát; chúng cũng duyệt dữ liệu. Loader có chi phí riêng, có thể lớn hơn nhiều so với một lần tra cứu map.

Không nên biến nhận xét “get/put trung bình nhanh” thành cam kết mọi thao tác đều có độ trễ cố định. Chất lượng băm, kích thước dữ liệu, cấp phát, việc mở rộng bảng và công việc tải dữ liệu đều có thể ảnh hưởng chi phí thực tế.

## 10. Lỗi phổ biến và cách sửa đúng trọng tâm

| Cách viết hoặc suy luận sai | Vì sao sai | Cách xử lý |
|---|---|---|
| `new LinkedHashMap<>(3)` là cache ba entry | Số 3 là dung lượng ban đầu yêu cầu | Có giới hạn riêng và quy tắc loại bỏ |
| Bật `accessOrder=true` là đã có cache hữu hạn | Nó chỉ giữ thứ tự truy cập | Ghi đè hook theo giới hạn |
| Hook đúng nhưng dùng constructor mặc định | Entry đầu là cũ nhất theo thứ tự chèn | Bật chế độ truy cập nếu muốn LRU |
| `size() >= maximumEntries` | Entry mới đã nằm trong map khi hook chạy | Dùng `size() > maximumEntries` |
| Mỗi `get` đều gọi hook | Đọc có thể đổi thứ tự nhưng không phải thêm entry mới | Phân biệt cập nhật độ mới và kiểm tra loại bỏ |
| `put` khóa cũ luôn tạo một entry mới | Mapping của khóa đã có được cập nhật | Xem riêng đường thêm và đường cập nhật |
| Tự `remove` rồi trả `true` | Vừa sửa map vừa yêu cầu map loại tiếp có hành vi không được quy định | Trong mẫu này chỉ trả quyết định |
| Bỏ entry LRU là xóa dữ liệu gốc | Cache chỉ giữ bản hoặc kết quả có thể tạo lại | Tách lưu đệm khỏi lưu trữ nguồn |
| Entry không được đọc lâu sẽ tự hết hạn | Không có bộ định thời trong cơ chế LRU này | Đừng gán bảo đảm TTL cho cache |
| Chỉ khóa `put`, để `get` chạy tự do ở nhiều luồng | `get` có thể sửa thứ tự truy cập | Thiết kế phối hợp toàn bộ trạng thái |
| Dùng `null` vừa làm dữ liệu vừa làm dấu hiệu miss | `get == null` không còn phân biệt được hai trường hợp | Chọn rõ chính sách giá trị hoặc kiểu kết quả |
| So sánh tập khóa để xác nhận LRU | Tập không mô tả thứ tự trong phép bằng nhau | So sánh một danh sách theo thứ tự |

## 11. Tự kiểm tra

1. Vì sao `removeEldestEntry` đi cùng thứ tự chèn chưa đủ để tạo LRU?
2. Với giới hạn ba, khi hook được gọi sau lần thêm khóa thứ tư, `size()` là mấy?
3. Vì sao điều kiện `>=` làm cache giới hạn một entry không giữ được entry nào sau `put`?
4. `get` tìm thấy entry và `containsKey` tìm thấy entry có cùng hiệu ứng lên LRU không?
5. Cập nhật giá trị của khóa đã có bằng `put` có làm tăng số entry không? Có thể đổi thứ tự không?
6. Một lần trả `true` có ý nghĩa tự dọn mọi entry thừa đến khi đạt một giới hạn vừa giảm mạnh không?
7. Vì sao lớp bọc kiểm tra khác `null` trước khi gọi `entries.put`?
8. Cache có giới hạn 0 trả kết quả vừa tải cho yêu cầu hiện tại được không?
9. Vì sao chỉ đồng bộ từng `get` và `put` chưa bảo đảm một khóa chỉ có một lần tải đang thực hiện?
10. Với cache giới hạn hai, yêu cầu `x, y, x, z, y` cần bao nhiêu lần tải và kết thúc theo thứ tự nào?

### Đáp án và lập luận

1. Thứ tự chèn không đổi khi A được đọc lại. Loại entry đầu sẽ loại entry vào sớm nhất, kể cả entry vừa được dùng. LRU cần thứ tự truy cập để entry đầu phản ánh entry ít được dùng gần đây nhất.
2. Bốn. Entry mới đã được thêm; hook quyết định có loại entry đầu để quay về ba hay không.
3. Entry đầu tiên tạo `size=1`; `1 >= 1` đúng nên nó bị loại ngay. Điều kiện đúng `1 > 1` là sai nên giữ được entry đó.
4. Không. `get` thành công làm entry thành mới nhất; `containsKey` không làm mới thứ tự truy cập.
5. Không tăng số entry. Trong map theo thứ tự truy cập, `put` khóa đã có vẫn làm entry đó thành mới nhất; không có lần thêm mới để kích hoạt hook theo đường chèn.
6. Không. Một quyết định `true` yêu cầu bỏ entry `eldest`. Thiết kế giới hạn cố định đủ dùng một lần loại sau mỗi lần thêm; thay giới hạn động cần cơ chế thu gọn riêng.
7. Để đầu vào sai bị từ chối trước mọi thay đổi và trước nguy cơ loại một entry hợp lệ. Đồng thời giữ hợp đồng `null` từ `get` nghĩa là miss.
8. Có. Phương thức có thể tải, thử lưu rồi trả kết quả đã tính. Cache không giữ entry sau thao tác, nên yêu cầu sau lại phải tải.
9. Hai luồng có thể lần lượt kiểm tra và cùng thấy miss trước khi một luồng ghi kết quả. Chuỗi nhiều bước cần một hợp đồng phối hợp riêng; khóa từng lệnh không gộp chúng thành một bước nguyên tử.
10. Bốn lần tải: x, y, z, rồi y một lần nữa. Các thứ tự sau mỗi yêu cầu là `[x]`, `[x, y]`, `[y, x]`, `[x, z]`, `[z, y]`. Lần dùng x thứ hai là hit; y đã bị loại khi thêm z.

## 12. Thực hành có kết quả đối chiếu

### Bài 1: xác nhận lỗi ranh giới

Trong một bản sao của chương trình quan sát hook, đổi điều kiện thành `size() >= maximumEntries` rồi chạy với giới hạn ba. Trước khi chạy, dự đoán sau ba lần thêm A, B, C còn những khóa nào. Kết quả phải chỉ còn B, C: A đã bị loại khi kích thước vừa bằng ba. Sau đó khôi phục điều kiện đúng và xác nhận map giữ A, B, C.

Mục đích là hiểu thời điểm đánh giá điều kiện, không phải thêm một nhánh đặc biệt cho lần chèn thứ ba. Cùng một điều kiện `>` phải xử lý được giới hạn 0, 1 và lớn hơn.

### Bài 2: tách quan sát và sử dụng

Với giới hạn hai, thêm A, B rồi gọi `containsKey("A")`, sau đó thêm C. Dự đoán A bị loại và thứ tự còn `[B, C]`. Làm lại từ map rỗng nhưng thay `containsKey("A")` bằng `get("A")`; lần này B bị loại và thứ tự còn `[A, C]`.

Hai lần thử phải khởi tạo cache riêng để có cùng điểm xuất phát. Nếu tiếp tục trên cache đã bị thay đổi từ lần thử trước, kết quả sẽ không còn phản ánh đúng một khác biệt duy nhất giữa hai phương thức.

### Bài 3: theo dõi đường cập nhật khóa cũ

Với giới hạn hai, thêm A=a, B=b rồi `put("A", "a2")`. Xác nhận kích thước vẫn hai, thứ tự `[B, A]`, giá trị của A là `a2`. Sau đó thêm C và xác nhận B bị loại. Nếu dùng `get("A")` để kiểm tra giá trị, ghi nhận rằng lời gọi đó cũng là một lần truy cập; ở thời điểm này A đã mới nhất nên thứ tự quan sát vẫn như cũ.

### Bài 4: chứng minh việc loại không làm mất nguồn

Trong chương trình tải theo yêu cầu, giữ loader có thể tạo lại cùng kết quả cho cùng khóa. Chạy chuỗi `a, b, a, c, b`, đối chiếu b được tải hai lần nhưng đều trả `B`. Dữ liệu rời cache rồi được tạo lại; số lần tải tăng, còn ý nghĩa của kết quả không thay đổi.

Tiêu chí hoàn thành là giải thích được vì sao một yêu cầu hit, một yêu cầu miss và entry nào phải rời cache ở từng bước. Chỉ nhìn thấy chương trình chạy không lỗi chưa chứng minh chính sách LRU đã đúng.

## 13. Tài liệu đối chiếu và môi trường chạy

Các chương trình trong bài dùng cú pháp và API tương thích Java 17. Chúng đã được biên dịch với `javac --release 17 -encoding UTF-8` và chạy trên JDK 24.0.2; stdout phía trên là kết quả đã đối chiếu từ các lần chạy đó. Mỗi tệp chỉ chứa một lớp `public` cùng tên tệp, không phụ thuộc thư viện ngoài.

- [LinkedHashMap — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html): thứ tự chèn/truy cập, các thao tác tạo truy cập, thời điểm và hợp đồng của `removeEldestEntry`, giới hạn đồng bộ và iterator.
- [Map — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): hợp đồng mapping, ý nghĩa của `get`, giá trị `null`, phép bằng nhau và các view.
- [Objects.requireNonNull — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html#requireNonNull(T,java.lang.String)): kiểm tra tham chiếu đầu vào khác `null`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linked-hash-map-006">Giới hạn an toàn đa luồng của bộ nhớ đệm tự viết</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn an toàn đa luồng của bộ nhớ đệm tự viết

## 1. Cache đúng ở một luồng chưa có nghĩa đúng khi dùng chung

Một bộ nhớ đệm (cache) tự viết bằng LinkedHashMap có thể rất ngắn: lấy theo khóa, khi thiếu thì tải dữ liệu, thêm vào map, và loại mục ít được dùng gần đây nhất khi vượt giới hạn.

Ở một luồng, những bước ấy có vẻ nối tiếp tự nhiên. Khi hai luồng cùng dùng cache, chúng có thể xen kẽ ở bất kỳ khoảng trống nào mà thiết kế cho phép.

Một lỗi không nhất thiết biểu hiện thành ngoại lệ. Có thể cấu trúc map vẫn còn dùng được nhưng:

- Cùng một khóa bị tải nhiều lần.
- Giá trị cũ ghi đè dữ liệu vừa được làm mới.
- Giới hạn số entry bị vượt hoặc loại sai mục.
- Vòng duyệt thấy trạng thái không nhất quán.
- Đối tượng giá trị bị sửa đồng thời dù map được bảo vệ.
- Một hàm tải chậm chặn mọi yêu cầu khác.

Mục tiêu của bài là hiểu **phạm vi bảo đảm của từng cách đồng bộ**, xây một ví dụ nhỏ có hợp đồng rõ ràng, rồi nhận diện những yêu cầu mà ví dụ ấy chưa giải quyết.

Không cần cố tạo một lỗi phá hỏng LinkedHashMap bằng cách chạy đua không kiểm soát. Ta có thể chứng minh một vấn đề cụ thể bằng lịch xen kẽ được điều khiển và phân tích vùng bảo vệ.

## 2. “An toàn đa luồng” gồm nhiều câu hỏi khác nhau

| Lớp vấn đề | Câu hỏi cần trả lời |
| --- | --- |
| Tính toàn vẹn cấu trúc | Bảng băm và liên kết thứ tự có bị thao tác đồng thời sai cách không? |
| Khả năng nhìn thấy dữ liệu | Luồng đọc có quan hệ đồng bộ để thấy những ghi cần thiết không? |
| Tính nguyên tử | Một thao tác logic nhiều bước có bị luồng khác chen vào không? |
| Chính sách dung lượng | Thêm, cập nhật thứ tự và loại mục có thống nhất trong cùng quy tắc không? |
| Phối hợp tải | Những yêu cầu đồng thời cho cùng khóa có dùng chung một lần tải không? |
| Vòng đời dữ liệu | Invalidate, lỗi tải và tải đang chạy được hiểu thế nào? |
| Đối tượng giá trị | Ai được sửa giá trị sau khi lấy ra, và bằng cách nào? |
| Khả năng phục vụ | Một yêu cầu chậm có chặn toàn bộ cache không? |

Một cấu trúc Map an toàn ở từng phương thức chưa tự trả lời toàn bộ bảng này.

Khi nói “cache an toàn”, hãy nêu rõ những thao tác nào, những bất biến nào và điều kiện sử dụng nào được bảo đảm. Không dùng một nhãn chung để che nhiều quyết định chưa có.

## 3. LinkedHashMap access-order có thao tác đọc làm thay đổi cấu trúc

LinkedHashMap không tự đồng bộ. Tài liệu [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html) nêu yêu cầu phối hợp khi các luồng dùng chung và có thay đổi cấu trúc.

Trong chế độ access-order, get trúng một entry có thể đưa nó về cuối chuỗi. Vì vậy nhiều luồng chỉ gọi get cũng có thể cùng sửa liên kết thứ tự.

### 3.1. Không chỉ khóa put và remove

Nếu một thiết kế khóa put/remove nhưng để get đi tự do vì cho rằng “get chỉ đọc”, nó chưa bảo vệ đầy đủ map access-order.

Ngay cả một phương thức không tự đổi thứ tự như containsKey cũng không được tùy ý đọc cấu trúc đang bị luồng khác sửa mà không có cơ chế phối hợp phù hợp.

Phải xét trạng thái thực sự được đụng tới, không phân loại an toàn chỉ theo tên phương thức.

### 3.2. Read lock không tự phù hợp với get

Một khóa đọc–ghi cho phép nhiều bên cùng giữ read lock. Điều đó chỉ hợp lý nếu những thao tác trong vùng đọc thật sự không cùng sửa trạng thái cần bảo vệ.

Get của map access-order không thỏa giả định ấy trong mọi trường hợp. Đưa mọi get vào read lock rồi cho chúng chạy song song không tự biến chuỗi thứ tự thành an toàn.

Nếu vẫn muốn dùng mô hình đọc–ghi, phải xác định đúng phần nào cần bảo vệ độc quyền. Đừng thêm một lớp khóa phức tạp chỉ để giữ lại một giả định đọc sai.

### 3.3. Fail-fast không thay cơ chế đồng bộ

ConcurrentModificationException chỉ hỗ trợ phát hiện một số cách dùng sai theo khả năng.

Không thấy ngoại lệ không chứng minh không có tranh chấp dữ liệu. Bắt ngoại lệ rồi thử lại cũng không sửa một cấu trúc được chia sẻ sai quy tắc.

Đồng bộ phải đến từ thiết kế truy cập chung, không đến từ việc hy vọng iterator phát hiện mọi lỗi.

### 3.4. RemoveEldestEntry không tự thêm sự đồng bộ

Override removeEldestEntry chỉ bổ sung quyết định loại mục trong đường thêm của map. Nó không tự tạo khóa bảo vệ cho get, put, các liên kết thứ tự hoặc những view được trả ra.

Một lớp con LinkedHashMap có quy tắc size() > limit vẫn cần cùng một giao thức đa luồng cho mọi đường truy cập. Nếu đường gọi được bao bởi khóa đúng, hook chạy trong ngữ cảnh bảo vệ ấy; bản thân hook không thay trách nhiệm phối hợp bên ngoài.

## 4. Khóa chung bảo vệ điều gì?

Một phương án dễ lập luận là dùng cùng một monitor để bảo vệ toàn bộ trạng thái cache và mọi thao tác lên trạng thái ấy.

Theo [Java Language Specification, chương 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html), mở khóa một monitor có quan hệ happens-before với lần khóa tiếp theo trên chính monitor đó.

Điều này giúp tạo cả sự loại trừ lẫn quan hệ nhìn thấy dữ liệu giữa các đoạn mã cùng tuân quy tắc.

### 4.1. Cùng trạng thái phải dùng cùng khóa

Khóa ở một phương thức nhưng dùng một đối tượng khác ở phương thức còn lại không tạo cùng vùng bảo vệ.

Tạo new Object() riêng mỗi lần gọi rồi synchronized trên đối tượng mới cũng không phối hợp được những lời gọi với nhau.

Một đối tượng khóa private final thuộc cache giúp làm rõ quyền sở hữu. Toàn bộ đường truy cập map, kể cả size, invalidate và chụp danh sách khóa, phải tuân cùng quy tắc.

### 4.2. Final và volatile không thay khóa cho các lần sửa tiếp theo

Final giữ tham chiếu field không bị gán lại và có vai trò trong việc khởi tạo đúng. Nó không biến đối tượng map được tham chiếu thành bất biến.

Đặt một tham chiếu volatile tới map có thể phục vụ việc công bố hoặc thay cả tham chiếu. Nhưng nếu sau đó nhiều luồng sửa trực tiếp cùng đối tượng LinkedHashMap mà không phối hợp, volatile trên tham chiếu không tự bảo vệ các bucket và liên kết bên trong.

Một thiết kế thay toàn bộ ảnh chụp bất biến là mô hình khác, có thể được phân tích riêng. Không nhầm nó với việc giữ một mutable map rồi chỉ thêm từ khóa volatile.

### 4.3. Công bố cache cho luồng khác cũng cần đúng cách

Khởi tạo cache hoàn chỉnh trước khi chia sẻ và không để this thoát khỏi constructor.

Các ví dụ dùng ExecutorService để gửi công việc sau khi tạo cache. Hợp đồng của công cụ phối hợp cung cấp quan hệ bộ nhớ cần thiết; không dùng một biến dùng chung tùy tiện rồi hy vọng luồng khác nhìn thấy đối tượng đầy đủ.

Khóa đúng trong phương thức và cách chia sẻ đối tượng đúng là hai phần bổ sung cho nhau.

## 5. Collections.synchronizedMap: hữu ích nhưng không tự tạo giao dịch cho mọi đoạn mã

Có thể bọc map:

```java
Map<String, String> cache = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true));
```

Đây là đoạn khai báo minh họa với các import phù hợp.

Theo [Collections.synchronizedMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map)), mọi truy cập vào map nền phải đi qua đối tượng được bọc để giữ quy tắc truy cập tuần tự.

Không giữ map nền rồi vừa gọi nó trực tiếp ở nơi này, vừa dùng wrapper ở nơi khác. Hai đường đi đó phá hợp đồng bảo vệ.

### 5.1. Hai lời gọi được khóa riêng vẫn có khoảng trống ở giữa

Đoạn logic “get; nếu thiếu thì tải; put” có ít nhất ba phần. Wrapper không tự mở rộng monitor của một lời gọi get tới tận lời gọi put được viết ở một dòng khác.

Lịch xen kẽ có thể là:

| Bước | Luồng T1 | Luồng T2 |
| --- | --- | --- |
| 1 | Get k: chưa có | |
| 2 | | Get k: chưa có |
| 3 | Tải dữ liệu cho k | |
| 4 | | Cũng tải dữ liệu cho k |
| 5 | Put kết quả | |
| 6 | | Put kết quả lần nữa |

Từng get/put có thể đã được bảo vệ đúng. Vấn đề ở đây là **chuỗi thao tác logic chưa nguyên tử**, nên hai lần tải vẫn xảy ra.

### 5.2. Không đánh đồng mọi API với chuỗi get/put tự viết

Ví dụ này không có nghĩa computeIfAbsent của wrapper được cài như một chuỗi get/put không khóa chung.

Trong [Collections.java OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/Collections.java), SynchronizedMap giữ mutex khi gọi computeIfAbsent của map nền. Như vậy hàm tính cũng có thể chạy trong khóa chung.

Đó là một đường thao tác khác, và vẫn có đánh đổi về việc hàm tính chậm giữ khóa. Phải kiểm tra hợp đồng và cài đặt tương ứng, không suy từ một thí nghiệm get/put sang mọi phương thức của Map.

### 5.3. Duyệt cần bảo vệ toàn lượt

Khi dùng Iterator, Spliterator hoặc Stream trên view của synchronizedMap, tài liệu yêu cầu tự synchronized trên **map được bọc**, bao trùm lượt duyệt.

Khóa trên keySet là một monitor khác; nó không thay việc khóa đúng map.

Có thể chụp dữ liệu cần dùng trong một đoạn bảo vệ ngắn rồi xử lý bản chụp ở ngoài. Nếu chụp các entry, cần tránh giữ entry sống của map rồi tưởng đó là bản sao bất biến; bản sao khóa/giá trị cũng chưa tự sao chép sâu đối tượng bên trong.

Ngay cả khi đã khóa đúng, một vòng duyệt keySet rồi tự gọi get trên map access-order vẫn có thể tự thay liên kết trong cùng luồng. Khóa ngăn luồng khác chen vào, không ngăn chính đoạn mã trong khóa sửa thứ tự. Để chỉ đọc các cặp, dùng entrySet và lấy giá trị từ entry.

## 6. Ví dụ Java 17: map đã đồng bộ nhưng vẫn tải trùng

Chương trình sau cố ý tạo hai cache miss trước khi cho hai yêu cầu tiếp tục tải. CountDownLatch điều khiển lịch này; không dùng sleep để cầu may xuất hiện lỗi.

Cache trong ví dụ được bọc synchronizedMap. Đây là minh họa lỗi phối hợp tải, không phải cố chạy đua trên một LinkedHashMap trần.

Lưu thành **DuplicateLoadDemo.java**:

```java
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DuplicateLoadDemo {
    public static void main(String[] args) throws Exception {
        Map<String, String> cache = Collections.synchronizedMap(
                new LinkedHashMap<>(16, 0.75f, true));
        CountDownLatch bothMissed = new CountDownLatch(2);
        AtomicInteger loads = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<String> request = () -> {
            String value = cache.get("k");
            if (value == null) {
                bothMissed.countDown();
                if (!bothMissed.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("second miss did not arrive");
                }
                loads.incrementAndGet();
                value = "value-k";
                cache.put("k", value);
            }
            return value;
        };

        try {
            Future<String> first = pool.submit(request);
            Future<String> second = pool.submit(request);
            boolean sameResult = first.get(10, TimeUnit.SECONDS)
                    .equals(second.get(10, TimeUnit.SECONDS));
            System.out.println("same result: " + sameResult);
            System.out.println("loader calls: " + loads.get());
            System.out.println("cache size: " + cache.size());
            System.out.println("cached value: " + cache.get("k"));
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 DuplicateLoadDemo.java
java DuplicateLoadDemo
```

Stdout:

```text
same result: true
loader calls: 2
cache size: 1
cached value: value-k
```

Hai chương trình chính trong bài được biên dịch với --release 17 và chạy trên OpenJDK 24.0.2. Cờ biên dịch chọn mức API/bytecode đích; JVM thực thi vẫn là phiên bản 24.0.2.

### 6.1. Vì sao size bằng 1 nhưng tải hai lần?

Hai luồng cùng dùng khóa k, nên cuối cùng chỉ có một ánh xạ. Tuy nhiên cả hai đã thấy thiếu trước khi một bên put, nên cả hai đều chạy phần tải.

Kết quả dữ liệu giống nhau không chứng minh cách phối hợp hiệu quả hay đúng với một hàm tải có tác dụng phụ. Nếu tải là một thao tác tốn tài nguyên, hai lần tải vẫn là hai công việc.

### 6.2. Latch đang chứng minh điều gì?

BothMissed chỉ về 0 khi cả hai yêu cầu đã đi qua nhánh thiếu. Sau đó mới cho chúng tải và put. Vì vậy kết quả tải trùng được tái hiện có chủ đích.

Các thời hạn await/get giúp chương trình thất bại rõ nếu thí nghiệm không tiến triển, thay vì treo vô hạn. Chúng là thời hạn của bộ kiểm tra, không phải chính sách timeout của cache.

Không bê nguyên barrier này vào một vùng khóa chung: nếu một luồng giữ khóa rồi đợi luồng kia vào cùng vùng khóa để đếm latch, có thể tự tạo bế tắc.

## 7. Một cache nhỏ có khóa chung và hợp đồng rõ ràng

Ví dụ tiếp theo dùng một lớp bao ngoài, không trả map nền hoặc view sống cho bên ngoài.

Hợp đồng minh họa:

- Số entry tối đa phải dương.
- Không chấp nhận khóa null hoặc giá trị null.
- Mọi thao tác lên map và thứ tự truy cập dùng chung một monitor private.
- Thêm và loại mục được thực hiện trong cùng vùng bảo vệ.
- GetOrLoad giữ khóa từ kiểm tra thiếu tới khi tải, ghi và loại mục xong.
- Hàm tải phải ngắn, không gọi lại cache và không tạo chuỗi chờ khóa bên ngoài.
- Khóa có định danh ổn định; các hàm hashCode/equals không tự sửa cache.
- Giá trị nên là dữ liệu bất biến hoặc được bảo vệ bằng hợp đồng riêng.

Đây là thiết kế cố ý đơn giản để có thể chỉ ra ranh giới bảo vệ. Nó dành cho khối lượng nhỏ và hàm tải phù hợp với việc chạy trong khóa; không phải một cache tải I/O có khả năng phục vụ cao.

### 7.1. Những bất biến cần giữ

Sau khi một phương thức công khai hoàn thành bình thường:

1. Map có không quá maximumSize entry.
2. Các entry còn lại tuân thứ tự truy cập do những thao tác đã được tuần tự hóa tạo ra.
3. Không có entry với giá trị null.
4. Bên ngoài không giữ iterator hoặc view sống để vượt qua quy tắc khóa.

Trong lúc thêm rồi loại mục, size có thể tạm lớn hơn giới hạn ở bên trong vùng bảo vệ. Những thao tác cùng tuân khóa không quan sát phần chuyển tiếp ấy.

Giới hạn số entry không phải giới hạn byte của toàn ứng dụng. Đối tượng giá trị, yêu cầu đang chờ và tài nguyên tải cũng sử dụng bộ nhớ riêng.

## 8. Ví dụ Java 17: khóa trọn thao tác và loại mục LRU

Lưu thành **LockedLruDemo.java**:

```java
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class LockedLruDemo {
    static final class LockedLruCache<K, V> {
        private final Object lock = new Object();
        private final int maximumSize;
        private final LinkedHashMap<K, V> data =
                new LinkedHashMap<>(16, 0.75f, true);

        LockedLruCache(int maximumSize) {
            if (maximumSize <= 0) {
                throw new IllegalArgumentException("maximumSize must be positive");
            }
            this.maximumSize = maximumSize;
        }

        public V get(K key) {
            Objects.requireNonNull(key, "key");
            synchronized (lock) {
                return data.get(key);
            }
        }

        public void put(K key, V value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            synchronized (lock) {
                data.put(key, value);
                trimToLimit();
            }
        }

        public V getOrLoad(K key, Function<? super K, ? extends V> loader) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(loader, "loader");
            synchronized (lock) {
                V current = data.get(key);
                if (current != null) {
                    return current;
                }
                V loaded = Objects.requireNonNull(loader.apply(key), "loaded value");
                data.put(key, loaded);
                trimToLimit();
                return loaded;
            }
        }

        public void invalidate(K key) {
            Objects.requireNonNull(key, "key");
            synchronized (lock) {
                data.remove(key);
            }
        }

        public int size() {
            synchronized (lock) {
                return data.size();
            }
        }

        public List<K> keysSnapshot() {
            synchronized (lock) {
                return List.copyOf(data.keySet());
            }
        }

        private void trimToLimit() {
            while (data.size() > maximumSize) {
                Iterator<K> iterator = data.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        LockedLruCache<String, String> cache = new LockedLruCache<>(2);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<String> request = () -> {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("start signal missing");
            }
            return cache.getOrLoad("k", key -> {
                loads.incrementAndGet();
                return "value-" + key;
            });
        };

        try {
            Future<String> first = pool.submit(request);
            Future<String> second = pool.submit(request);
            start.countDown();
            boolean sameResult = first.get(10, TimeUnit.SECONDS)
                    .equals(second.get(10, TimeUnit.SECONDS));
            System.out.println("same result: " + sameResult);
            System.out.println("loader calls: " + loads.get());

            cache.put("b", "value-b");
            System.out.println("get k: " + cache.get("k"));
            cache.put("c", "value-c");
            System.out.println("keys after eviction: " + cache.keysSnapshot());
            System.out.println("get evicted b: " + cache.get("b"));
            System.out.println("final size: " + cache.size());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 LockedLruDemo.java
java LockedLruDemo
```

Stdout trên môi trường đã nêu:

```text
same result: true
loader calls: 1
get k: value-k
keys after eviction: [k, c]
get evicted b: null
final size: 2
```

### 8.1. Hai yêu cầu đồng thời cho cùng khóa

Hai tác vụ đều được phép bắt đầu sau tín hiệu start. Bên vào vùng bảo vệ trước thấy thiếu k, gọi hàm tải và lưu giá trị.

Bên còn lại chỉ vào được sau đó, đọc được giá trị vừa có và không gọi hàm tải. Vì vậy loader calls bằng 1 trong kịch bản này.

Không cần giả định tác vụ được submit trước phải thắng. Kết quả không phụ thuộc việc luồng nào lấy khóa trước vì chúng yêu cầu cùng khóa và cùng giá trị.

### 8.2. Kiểm tra chính sách loại mục

Sau phần tải, cache có k. Thêm b tạo dãy k,b. Get k ghi nhận dùng k gần đây, nên dãy thành b,k.

Thêm c vượt giới hạn 2. Iterator lấy khóa đầu là b và xóa qua chính iterator, còn k,c.

Get b trả null; size cuối là 2. Đây là một trường hợp LRU cụ thể, không phải dự đoán thứ tự tùy ý của các luồng chạy song song.

### 8.3. Vì sao không cần trả keySet?

KeysSnapshot tạo List.copyOf trong khi giữ khóa, nên bên gọi nhận một danh sách độc lập về cấu trúc, không sửa được dãy danh sách ấy.

Nó chỉ chụp các tham chiếu khóa. Đối tượng khóa vẫn cần ổn định; danh sách không tự biến một khóa có thể sửa thành bất biến.

Trong ví dụ, khóa và giá trị đều là String, giúp tránh thêm bài toán sửa đối tượng dùng chung.

### 8.4. Lỗi tải và null được xử lý thế nào?

Nếu hàm tải ném ngoại lệ hoặc trả null, phương thức không đi tới bước put. Khối synchronized vẫn tự mở khóa khi thoát do ngoại lệ.

Không có ánh xạ đánh dấu lỗi được giữ lại. Một yêu cầu sau có thể thử tải lại. Đây là chính sách của ví dụ, không phải quy tắc duy nhất đúng cho mọi cache.

Không cất null giúp get trả null có nghĩa rõ ràng là chưa có dữ liệu trong cache. Nếu bài toán cần phân biệt “chưa tải” và “đã biết không tồn tại”, có thể dùng một kiểu kết quả riêng và một chính sách giữ kết quả âm; đó là yêu cầu bổ sung.

### 8.5. Không hứa “mỗi khóa chỉ tải một lần mãi mãi”

Một ánh xạ có thể bị loại vì dung lượng, bị invalidate, hoặc chưa bao giờ được lưu do lỗi tải. Sau đó cùng khóa có thể cần tải lại.

Bảo đảm của ví dụ là không cho hai đường getOrLoad chạy chồng phần kiểm tra–tải–ghi trong cùng cache, dưới các điều kiện đã nêu. Nó không biến hàm tải thành thao tác chỉ xảy ra một lần trong suốt đời ứng dụng.

## 9. Ranh giới quan trọng: hàm tải chạy trong khóa chung

Thiết kế trên dễ lập luận về tính nguyên tử vì mọi thứ liên quan tới trạng thái map nằm trong một vùng loại trừ.

Đánh đổi nằm ở đoạn loader.apply: nó chạy khi monitor vẫn đang bị giữ.

### 9.1. Một khóa chậm có thể chặn những khóa khác

Nếu tải k gọi mạng mất vài giây, các get cho những khóa khác trong cùng cache cũng phải đợi monitor, dù dữ liệu của chúng đã có.

Các yêu cầu cùng khóa dùng chung kết quả trong kịch bản đơn giản, nhưng cả cache cũng bị tuần tự hóa quanh công việc ấy.

Đây là lý do ví dụ yêu cầu hàm tải ngắn và không chặn. Không thay bằng một truy vấn chậm rồi vẫn giữ nguyên kết luận về khả năng phục vụ.

### 9.2. Gọi mã bên ngoài khi giữ khóa có thể tạo chuỗi chờ

Hàm tải có thể lấy một khóa khác hoặc đợi một tác vụ khác. Nếu tác vụ kia lại cần cache đang bị giữ, hai bên có thể chờ nhau.

Monitor Java có tính tái nhập: cùng luồng có thể lấy lại monitor nó đang giữ. Điều đó không tự sửa đệ quy tải cùng khóa, chuỗi phụ thuộc qua cache khác hoặc chờ giữa các luồng.

Vì vậy hợp đồng ví dụ cấm hàm tải gọi lại cache. Đừng xem việc một lời gọi lại tình cờ chạy được là bằng chứng một thiết kế tái nhập bất kỳ đã đúng.

### 9.3. Thứ tự đồng thời được xác lập bởi vùng bảo vệ

Khi nhiều luồng cùng truy cập những khóa khác nhau, thứ tự thực tế phụ thuộc thứ tự chúng vào và thực hiện các thao tác được bảo vệ.

Không nhất thiết đó là thứ tự request đến từ bên ngoài hoặc thứ tự thời gian được ghi ở một hệ thống khác.

Muốn hứa một cách sắp thứ tự mạnh hơn, phải định nghĩa thêm quy tắc. Khóa chung tạo một lịch thực hiện có thể lập luận; nó không đọc được ý định thời gian của mọi người gọi.

### 9.4. Giữ đúng dữ liệu không đồng nghĩa có độ trễ tốt

Một thiết kế có thể an toàn về cấu trúc nhưng không đạt thông lượng hoặc độ trễ cần có.

Đo thời gian chờ khóa, thời gian tải, tỷ lệ trúng cache và số yêu cầu đồng thời. Không chỉ đo get ở một luồng rồi suy rằng bản có khóa sẽ mở rộng tốt khi tải tăng.

Các chương trình trong bài xác nhận những thuộc tính cụ thể; stdout không phải phép đo hiệu năng.

## 10. Đưa việc tải ra ngoài khóa: giảm chặn nhưng phát sinh giao thức mới

Một cải tiến dễ nghĩ tới:

1. Khóa, kiểm tra cache.
2. Mở khóa, tải dữ liệu.
3. Khóa lại, kiểm tra và ghi kết quả.
4. Cập nhật thứ tự và loại mục nếu cần.

Cách này có thể giảm thời gian giữ khóa chung. Nhưng nó không tự giữ nguyên toàn bộ ý nghĩa của getOrLoad trong ví dụ trước.

### 10.1. Hai lần tải vẫn có thể xảy ra

Hai luồng có thể cùng vượt qua bước kiểm tra đầu và cùng tải. Kiểm tra lại khi ghi giúp chọn giá trị đã có thay vì ghi đè mù, nhưng không thu hồi được công tải trùng đã thực hiện.

Nếu hàm tải có tác dụng phụ, phải xét tác dụng ấy riêng. Việc chỉ một kết quả được giữ trong map không có nghĩa chỉ một lần tải đã chạy.

### 10.2. Có thể ghi lại dữ liệu sau invalidate

Lịch minh họa:

```text
T1: thấy k thiếu, bắt đầu tải dữ liệu cũ
T2: invalidate k vì dữ liệu nguồn vừa thay đổi
T1: tải xong, put kết quả cũ vào cache
```

Nếu invalidate chỉ xóa entry hiện có, bước ghi muộn của T1 có thể đưa dữ liệu trở lại.

Cần định nghĩa invalidate muốn bảo đảm điều gì: chỉ xóa trạng thái đang lưu, hay còn ngăn một lượt tải đã bắt đầu trước đó được công bố sau này?

Nếu cần yêu cầu thứ hai, thiết kế có thể phải dùng phiên bản hoặc mã thế hệ của khóa/lượt tải. Khi ghi, kết quả chỉ được nhận nếu còn khớp thế hệ hiện hành. Đây là một giao thức bổ sung, không có sẵn chỉ nhờ putIfAbsent.

### 10.3. Kết quả nào thắng khi dữ liệu nguồn đổi?

Nếu hai lượt tải trả hai phiên bản khác nhau, “bên ghi cuối thắng” không tự đồng nghĩa “dữ liệu mới nhất thắng”.

Cần một tiêu chí về phiên bản dữ liệu hoặc thứ tự chấp nhận kết quả. Đồng hồ hoàn thành của hai tác vụ không đủ để suy ra độ mới của dữ liệu nguồn.

### 10.4. Giảm thời gian khóa không được chia rời bất biến

Phần cập nhật map, lịch sử truy cập và chính sách loại mục vẫn cần phối hợp.

Nếu chuyển sang ConcurrentHashMap và một hàng đợi riêng để ghi lịch sử, ta có hai cấu trúc phải thống nhất. Xóa ở cấu trúc này mà không xử lý trạng thái tương ứng ở cấu trúc kia có thể tạo phần tử lịch sử cũ hoặc loại nhầm.

Một thuật toán LRU gần đúng có thể chấp nhận những khác biệt được định nghĩa rõ. Nhưng không được tự nhận LRU chính xác hoặc giới hạn cứng khi chưa có cơ chế bảo đảm chúng.

## 11. Dùng chung một lượt tải cho cùng khóa không chỉ là thêm Future

Có thể muốn mỗi khóa đang tải có một CompletableFuture để những yêu cầu khác chờ cùng kết quả. Ý tưởng này thường được gọi là phối hợp một lượt tải cho cùng khóa, hoặc single-flight.

Để thành thiết kế đúng, cần trả lời thêm:

| Sự kiện | Câu hỏi của giao thức |
| --- | --- |
| Chưa có dữ liệu, chưa có lượt tải | Ai được quyền đăng ký lượt tải? |
| Đã có lượt tải | Yêu cầu sau dùng chung kết quả bằng cách nào? |
| Tải thành công | Khi nào công bố giá trị và bỏ dấu đang tải? |
| Tải thất bại | Có giữ lỗi không, khi nào cho thử lại? |
| Một bên chờ hết hạn | Chỉ bỏ chờ hay hủy công việc chung? |
| Invalidate trong lúc tải | Kết quả đang chạy còn được phép ghi không? |
| Lượt tải cũ hoàn tất sau lượt mới | Làm sao tránh xóa hoặc ghi nhầm trạng thái mới? |
| Có quá nhiều khóa đang tải | Giới hạn số công việc và tài nguyên ở đâu? |

Một yêu cầu timeout không nhất thiết được quyền hủy lượt tải mà những bên khác còn đang chờ. Hủy một Future cũng không tự bảo đảm mọi I/O bên dưới đã dừng.

Việc dọn trạng thái đang tải phải gắn với đúng lượt tải. Một lượt cũ hoàn tất không nên xóa nhầm đăng ký của lượt mới hơn cho cùng khóa.

Giới hạn số entry đã lưu cũng không tự giới hạn số Future, tác vụ, kết nối hoặc dữ liệu trung gian của những khóa đang tải.

Các câu hỏi này giải thích vì sao một cache tải dữ liệu đồng thời có thể phức tạp hơn nhiều so với một Map cộng vài dòng synchronized. Đây là yêu cầu thiết kế; bài không đưa một mảnh Future chưa có đủ giao thức rồi gọi đó là bản triển khai hoàn chỉnh.

## 12. ConcurrentHashMap giải quyết phần nào?

ConcurrentHashMap cung cấp cấu trúc map dùng chung cùng những thao tác nguyên tử theo hợp đồng của lớp.

Theo [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#computeIfAbsent(K,java.util.function.Function)), computeIfAbsent thực hiện lời gọi một cách nguyên tử; hàm tính cần ngắn, đơn giản và không sửa map trong lúc tính.

### 12.1. Có thể thay thế một chuỗi kiểm tra–ghi riêng lẻ

Với bài toán chỉ cần ánh xạ đồng thời và tính giá trị phù hợp với hợp đồng đó, API nguyên tử giúp tránh tự ghép get rồi put không có sự phối hợp.

Nhưng không hứa hàm được gọi một lần mãi mãi cho mỗi khóa. Nếu không lưu được giá trị, có lỗi hoặc ánh xạ bị xóa, các lần gọi sau có thể tính lại.

Cũng không diễn giải rằng mọi cập nhật cho mọi khóa khác luôn hoàn toàn không bị ảnh hưởng trong khi một hàm tính đang chạy. Tài liệu có nêu khả năng một số cập nhật phải chờ.

### 12.2. Nó không tự có thứ tự LRU hoặc trần dung lượng

ConcurrentHashMap không cung cấp chuỗi access-order như LinkedHashMap và không tự loại entry theo LRU.

Ghép size(), put() và một lần xóa ở các dòng riêng không tạo một phép giữ giới hạn nguyên tử. Dưới thay đổi đồng thời, các quan sát tổng hợp cũng không nên bị hiểu như một giao dịch toàn map.

Nếu bổ sung hàng đợi thứ tự hoặc bộ đếm riêng, phải phân tích giao thức chung. Đổi tên loại Map không tự hoàn thành phần chính sách của cache.

### 12.3. Khi cần nhiều khả năng cache

Nếu cần phối hợp tải, giới hạn theo kích thước/trọng số, hết hạn hoặc API bất đồng bộ với nhiều người dùng, có thể đánh giá một thư viện cache được duy trì và có hợp đồng phù hợp.

Ví dụ, tài liệu chính thức của Caffeine trình bày riêng [cách nạp dữ liệu](https://github.com/ben-manes/caffeine/wiki/Population) và [chính sách loại/hết hạn](https://github.com/ben-manes/caffeine/wiki/Eviction). Những tính năng ấy cần được chọn và cấu hình theo yêu cầu, không suy ra mọi cache đều có cùng bảo đảm.

Ngay cả khi dùng thư viện, đối tượng giá trị, tính đúng của hàm tải và độ mới của nguồn dữ liệu vẫn thuộc hợp đồng của ứng dụng.

## 13. Giá trị trả ra ngoài vẫn có thể bị sửa không an toàn

Khóa của cache bảo vệ cấu trúc và thao tác của cache. Khi get đã trả về một đối tượng, bên gọi có thể giữ và dùng đối tượng ấy sau khi khóa được mở.

Nếu giá trị là ArrayList và hai luồng cùng thêm phần tử vào nó, monitor của cache không tự bao quanh những lần thêm ở bên ngoài.

Một số lựa chọn thiết kế:

- Dùng giá trị bất biến, được tạo hoàn chỉnh trước khi lưu.
- Sao chép dữ liệu phù hợp khi nhận vào hoặc trả ra.
- Dùng một đối tượng giá trị có cơ chế an toàn riêng.
- Định nghĩa quyền sở hữu để một bên chịu trách nhiệm sửa.

Bản sao cấu trúc Map hoặc List không tự sao chép sâu giá trị. Một bản chụp không sửa được cũng có thể chứa tham chiếu tới đối tượng còn sửa được.

Tương tự, invalidate chỉ làm cache bỏ ánh xạ theo hợp đồng của nó. Nó không thu hồi mọi tham chiếu mà người gọi đã nhận trước đó.

## 14. Kiểm thử đa luồng cần chứng minh thuộc tính cụ thể

### 14.1. Dùng cơ chế phối hợp thay vì sleep để đoán lịch

Ví dụ đầu dùng CountDownLatch để buộc hai bên đều thấy thiếu trước khi tải. Đây là một lịch cụ thể mà thuật toán cho phép, đủ để bác bỏ lời khẳng định “chắc chỉ tải một lần”.

Tham khảo hợp đồng [CountDownLatch Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html) và [ExecutorService Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html) khi xây những kiểm tra có chờ và chia sẻ dữ liệu.

Thread.sleep không bảo đảm luồng kia đã đi tới đúng bước, và có thể làm bài kiểm tra vừa chậm vừa khó tái hiện.

### 14.2. Kiểm tra dữ liệu, giới hạn và vòng đời riêng

Với cache có khóa chung, những kiểm tra hữu ích gồm:

- Nhiều thao tác đồng thời không làm sai cặp khóa–giá trị còn tồn tại.
- Mọi quan sát qua API công khai tuân giới hạn số entry đã hứa.
- Truy cập một khóa rồi thêm khóa khác loại đúng mục trong kịch bản tuần tự có kiểm soát.
- Hai yêu cầu cùng khóa chia sẻ kết quả trong kịch bản không có invalidate/loại mục xen giữa.
- Hàm tải lỗi không để lại một trạng thái mắc kẹt.
- Lần thử lại có thể tiến triển theo chính sách đã chọn.
- Bản chụp không phải view sống và không mở đường sửa map nền.
- Invalidate đang tải được kiểm tra theo đúng nghĩa đã công bố, nếu thiết kế có tải ngoài khóa.

Không kiểm tra một thứ tự toàn cục cố định giữa những luồng vốn được phép cạnh tranh khóa theo nhiều lịch khác nhau.

### 14.3. Chạy nhiều lần không thay chứng minh về vùng bảo vệ

Kiểm thử với nhiều luồng có thể phát hiện lỗi, nhưng không quan sát thấy lỗi trong vài nghìn vòng không chứng minh mọi lịch đều đúng.

Cần đọc mã để xác định mọi đường chạm trạng thái, vùng bảo vệ, điểm công bố kết quả và các giả định về mã gọi lại.

Hai lớp bằng chứng bổ sung nhau: một lịch tái hiện được cho lỗi cụ thể, và một lập luận về bất biến cho mọi đường mà lớp cho phép.

## 15. Những giới hạn nên viết vào hợp đồng của cache tự viết

| Yêu cầu | Cần mô tả rõ |
| --- | --- |
| Dung lượng | Đếm entry hay trọng số/byte; giới hạn cứng ở đâu hay được phép lệch tạm |
| LRU | Chính xác theo thao tác đã tuần tự hóa hay gần đúng |
| Null | Được lưu không; phân biệt thiếu và kết quả âm ra sao |
| Tải | Chạy trong khóa hay ngoài; có chia sẻ lượt tải không |
| Lỗi | Giữ lỗi, thử lại ngay hay có trì hoãn; ngoại lệ được chuyển cho ai |
| Invalidate | Chỉ xóa ánh xạ hay còn vô hiệu hóa kết quả đang tải |
| Giá trị | Bất biến, sao chép hay được sửa theo một cơ chế riêng |
| Duyệt | View sống, bản chụp hay cần người gọi giữ khóa |
| Nhiều luồng | Monitor/quy tắc nào bao trùm mọi truy cập |
| Tài nguyên | Giới hạn công việc đang tải, bên chờ và thời gian chờ |

Không nhất thiết một cache học tập phải đáp ứng mọi yêu cầu nâng cao. Nó cần nói đúng những gì mình đáp ứng và giữ các bất biến đó trong mã.

Nếu ứng dụng thật cần những điều vượt khỏi phạm vi ấy, nên đổi thiết kế hoặc dùng thành phần phù hợp trước khi dựa vào các bảo đảm chưa có.

## 16. Lỗi phổ biến và cách sửa

| Lỗi suy luận | Cách sửa |
| --- | --- |
| Chỉ get nên LinkedHashMap access-order không có ghi | Get trúng entry có thể sửa liên kết; phối hợp cả đường này |
| Bọc synchronizedMap là mọi đoạn get–load–put đã nguyên tử | Xác định ranh giới thao tác logic, không chỉ từng phương thức |
| Khóa trên keySet cũng như khóa wrapper | Duyệt theo monitor được tài liệu yêu cầu |
| Đã khóa thì keySet cộng get luôn an toàn với iterator | Cùng luồng vẫn có thể đổi thứ tự; đọc trực tiếp qua entry |
| Volatile map làm mọi nội dung map an toàn | Phân biệt công bố tham chiếu và sửa cấu trúc bên trong |
| Hai tác vụ trả cùng dữ liệu nghĩa là chỉ tải một lần | Đếm và phối hợp lượt tải, không chỉ nhìn giá trị cuối |
| ComputeIfAbsent có nghĩa đúng một lần suốt đời khóa | Xét xóa, lỗi, kết quả không lưu và phạm vi bảo đảm cụ thể |
| Tải trong khóa an toàn nên hiệu năng chắc tốt | Xét thời gian chờ và việc chặn những khóa khác |
| Chuyển tải ra ngoài khóa không đổi ý nghĩa invalidate | Phân tích lượt tải đang chạy và quyền công bố kết quả |
| Dùng ConcurrentHashMap là tự có LRU và giới hạn | Chính sách thứ tự/dung lượng vẫn cần thiết kế |
| Cache an toàn thì giá trị mutable cũng an toàn | Giá trị cần hợp đồng riêng sau khi trả ra |
| Không gặp ConcurrentModificationException chứng minh đúng | Fail-fast không thay vùng bảo vệ và lập luận đa luồng |

## 17. Bài tập tự kiểm tra có lời giải

### Bài 1: Hai luồng chỉ đọc

Map access-order đã có nhiều mục. Hai luồng chỉ gọi get. Có thể bỏ đồng bộ vì không gọi put không?

**Lời giải:** không. Get trúng entry có thể đổi liên kết thứ tự; phải phối hợp những truy cập ấy.

### Bài 2: Hai lần miss

Từng get/put đã được synchronizedMap bảo vệ. Hai luồng cùng tải k rồi cuối cùng size bằng 1. Có mâu thuẫn không?

**Lời giải:** không. Từng phương thức được bảo vệ nhưng cả chuỗi kiểm tra–tải–ghi không tự nguyên tử. Hai lượt tải có thể cùng tạo một khóa cuối.

### Bài 3: Duyệt có khóa

Một luồng synchronized trên wrapper rồi duyệt keySet và get từng khóa. Khóa có ngăn chính vòng ấy đổi thứ tự không?

**Lời giải:** không. Monitor ngăn những luồng tuân khóa khác chen vào, nhưng cùng luồng vẫn gọi get làm đổi dãy. Nếu chỉ cần dữ liệu, duyệt entrySet và lấy value từ entry.

### Bài 4: Loader chậm

Cache có một khóa chung và chạy loader trong khóa. Khi tải a bị chậm, một get b đang có sẵn có phải chờ không?

**Lời giải:** có thể phải chờ cùng monitor. An toàn cấu trúc không tự tạo khả năng phục vụ song song.

### Bài 5: Invalidate khi tải ngoài khóa

T1 bắt đầu tải k, T2 invalidate k, rồi T1 lưu kết quả. Nếu yêu cầu là không cho kết quả cũ quay lại sau invalidate, chỉ remove k có đủ không?

**Lời giải:** không. Cần giao thức cho lượt tải đang chạy, chẳng hạn kiểm tra thế hệ/phiên bản trước khi chấp nhận kết quả.

### Bài 6: Giá trị là danh sách

Cache khóa mọi get/put nhưng trả cùng ArrayList cho nhiều luồng. Những lần add bên ngoài có tự nằm trong khóa cache không?

**Lời giải:** không. Phải dùng giá trị bất biến, sao chép, cấu trúc giá trị an toàn hoặc hợp đồng sở hữu thích hợp.

### Bài 7: Giới hạn số mục và công việc đang chạy

Cache chỉ giữ tối đa 100 entry nhưng cho tải đồng thời hàng nghìn khóa. Có chắc toàn bộ tài nguyên bị giới hạn theo 100 không?

**Lời giải:** không. Công việc, Future, kết nối và dữ liệu trung gian có vòng đời khác với entry đã lưu.

### Bài 8: Chọn bằng chứng kiểm thử

Một bài kiểm tra chạy nhiều luồng một lần và không lỗi. Có đủ để tuyên bố lớp đúng với mọi lịch không?

**Lời giải:** không. Cần kiểm thử có thuộc tính rõ ràng và phân tích mọi đường truy cập, vùng bảo vệ cùng bất biến của lớp.

## 18. Tài liệu đối chiếu

- [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html): access-order, thay đổi cấu trúc và yêu cầu đồng bộ.
- [Collections.synchronizedMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map)): truy cập qua wrapper và bảo vệ lượt duyệt.
- [Collections.java OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/Collections.java): đường khóa các phương thức của SynchronizedMap.
- [JLS 17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): monitor, quan hệ happens-before và tranh chấp dữ liệu.
- [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): thao tác nguyên tử và giới hạn hàm tính lại.
- [Caffeine — Population](https://github.com/ben-manes/caffeine/wiki/Population), [Caffeine — Eviction](https://github.com/ben-manes/caffeine/wiki/Eviction): đối chiếu trách nhiệm nạp dữ liệu và chính sách cache.

Một cache tự viết có thể đúng trong một phạm vi nhỏ được xác định rõ. Điều làm nó an toàn là mọi đường truy cập cùng tuân giao thức, còn điều làm nó phù hợp với ứng dụng là những bảo đảm ấy khớp với yêu cầu về tải, vòng đời dữ liệu và khả năng phục vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linked-hash-map-007">Tương tác giữa access-order, iterator và thay đổi cấu trúc</a>

<details>
<summary>Xem chi tiết</summary>

# LinkedHashMap: tương tác giữa access-order, iterator và thay đổi cấu trúc

Một vòng lặp tưởng như chỉ đọc dữ liệu vẫn có thể làm thay đổi `LinkedHashMap` nếu map được tạo ở chế độ thứ tự truy cập (`access-order`). Nguyên nhân là ứng dụng đang đọc giá trị, nhưng map đồng thời ghi nhận lần truy cập ấy vào thứ tự duyệt.

Hãy hình dung một bộ đệm có ba khóa `A, B, C`, từ ít được truy cập gần đây nhất đến mới nhất. Khi đọc `A`, ứng dụng muốn nhận giá trị của A; cấu trúc giữ thứ tự còn phải đưa A xuống cuối. Nếu một iterator đang đi theo chuỗi cũ, hai hoạt động này có thể xung đột dù số ánh xạ không đổi và chương trình chỉ có một luồng.

Bài này giúp bạn nhận ra thay đổi cấu trúc, đọc đúng ý nghĩa của fail-fast, chọn cách duyệt hoặc cập nhật phù hợp và tránh dùng ngoại lệ như cơ chế bảo đảm tính đúng. Ví dụ dùng Java 17+; những chi tiết về `modCount` được trình bày riêng như cơ chế của OpenJDK 17.

## 1. Một map có nhiều loại trạng thái

Đừng chỉ nhìn map như tập cặp khóa–giá trị. Với `LinkedHashMap`, cần phân biệt ít nhất ba loại trạng thái:

| Trạng thái | Ví dụ thay đổi |
|---|---|
| Thành phần ánh xạ | Thêm D hoặc xóa B |
| Giá trị của ánh xạ tồn tại | Thay giá trị A từ 1 thành 10 |
| Thứ tự duyệt | Chuyển A từ đầu xuống cuối |

`size()` chỉ đếm số ánh xạ. Nó không cho biết thứ tự vừa thay đổi hay không, cũng không cho biết có một khóa bị xóa rồi khóa khác được thêm để giữ nguyên số lượng.

Do đó, suy luận “size trước và sau bằng nhau nên iterator vẫn hợp lệ” thiếu thông tin. Một chương trình theo dõi cấu trúc cần quan tâm lịch sử thao tác trong khoảng iterator còn hoạt động.

### 1.1. Hai chế độ thứ tự

Constructor có tham số boolean cuối cùng chọn cách giữ thứ tự:

~~~java
LinkedHashMap<String, Integer> insertion =
        new LinkedHashMap<>(16, 0.75f, false);

LinkedHashMap<String, Integer> access =
        new LinkedHashMap<>(16, 0.75f, true);
~~~

Đây là hai khai báo minh họa, chưa phải chương trình độc lập.

Ở chế độ thứ tự chèn, vị trí phản ánh lần chèn khóa; cập nhật giá trị một khóa đang tồn tại không đổi vị trí đó. Ở chế độ thứ tự truy cập, những phương thức được API quy định là truy cập có thể đưa ánh xạ về phía mới nhất.

Vì vậy, cùng một lời gọi `put("A", 10)` lên khóa A đã có không nhất thiết có cùng tác động thứ tự trong hai chế độ.

### 1.2. Dòng thời gian của một lần đọc

Với thứ tự ban đầu `[A, B, C]`:

~~~text
T0: tạo iterator trên chuỗi A -> B -> C
T1: gọi get("A") trên map thứ tự truy cập
T2: thứ tự map thành B -> C -> A, size vẫn bằng 3
T3: tiếp tục dùng iterator được tạo tại T0
~~~

Tại T3, iterator không tự biến thành một lượt duyệt mới bắt đầu ở B. Nó đã được tạo trong một trạng thái trước đó. Cần xem việc thay đổi ở T1 là sửa đổi trong lúc duyệt, thay vì coi đó là thao tác đọc hoàn toàn độc lập.

API `LinkedHashMap` xác định thay đổi cấu trúc gồm thêm, xóa ánh xạ và, với chế độ thứ tự truy cập, tác động đến thứ tự duyệt. Các iterator của góc nhìn có cơ chế fail-fast khi phát hiện thay đổi không phù hợp. [Java 17: LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html)

## 2. “Access” là quy tắc API, không phải tên gọi nghiệp vụ

Một phương thức có tên nghe như đọc hoặc cập nhật nhẹ vẫn có thể tạo lượt truy cập theo quy tắc của lớp.

### 2.1. Các trường hợp điển hình

Bảng sau xét map có `accessOrder=true`; “có thể đổi thứ tự” nghĩa là phải tính đến khả năng ánh xạ được chuyển vị trí, không phải mọi lời gọi luôn dịch chuyển một nút:

| Thao tác | Điều cần nghĩ đến khi iterator đang hoạt động |
|---|---|
| get khóa đang có | Có lượt truy cập; không coi là chỉ đọc cấu trúc |
| get khóa không có | Không có ánh xạ tương ứng để chuyển xuống cuối |
| getOrDefault với khóa đang có | Không phải cách né việc cập nhật thứ tự |
| put khóa mới | Thêm ánh xạ, thay đổi cấu trúc |
| put khóa đã có | Có thể đổi thứ tự dù chỉ gán lại giá trị |
| putIfAbsent với ánh xạ đang có giá trị khác null | Có thể ghi nhận truy cập dù không thay giá trị |
| replace thành công | Có thể ghi nhận truy cập |
| replace có điều kiện nhưng điều kiện không khớp | Không có lần thay giá trị thành công theo quy tắc đó |
| containsKey, containsValue | Không tạo lượt truy cập giữ độ mới |
| Đọc qua keySet, values, entrySet | Không tự tạo lượt truy cập kiểu get |

Nhóm phương thức `compute`, `computeIfAbsent`, `computeIfPresent` và `merge` cũng nằm trong quy tắc truy cập của `LinkedHashMap`; cần xét kết quả và điều kiện của từng phương thức. `putAll` xử lý các ánh xạ theo thứ tự duyệt nguồn. Đừng suy ra tác động chỉ từ việc “giá trị cuối cùng có vẻ không đổi”. [Quy tắc access-order trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html)

Bài sử dụng các trường hợp thông thường, có giá trị khác null, khi minh họa nhóm cập nhật. Các phương thức tính toán còn có quy tắc riêng về null, thêm và xóa; không nên gom chúng thành một thao tác “đọc rồi gán” duy nhất.

### 2.2. Đọc null không chứng minh không có truy cập

Nếu một khóa đang ánh xạ tới null, `get` trả về null nhưng vẫn đã tìm được ánh xạ. “Trả về null” và “không có khóa” là hai trạng thái khác nhau.

Ví dụ ở phần chương trình đầy đủ đặt giá trị B thành null qua `Map.Entry.setValue` để giữ thứ tự ban đầu, sau đó gọi `get("B")`. Thứ tự trở thành `[A, C, B]`. Ví dụ này tách được tác động của lần get khỏi tác động của một lần put trước đó.

`containsKey` giúp hỏi về sự tồn tại mà không ghi nhận truy cập như get. Tuy nhiên, ghép `containsKey` và `get` không tạo thành một thao tác nguyên tử khi có luồng khác cùng sửa map.

### 2.3. Thay giá trị bằng nhau vẫn cần nhìn vào phương thức

Không thể quyết định tính hợp lệ của vòng lặp bằng câu “tôi đã ghi lại đúng giá trị cũ”. Trong chế độ truy cập, lời gọi phương thức có thể mang ý nghĩa ghi nhận sử dụng ngay cả khi giá trị tham chiếu hoặc nội dung bằng nhau.

Cách tiếp cận đáng tin hơn là hỏi:

1. Phương thức có thể thêm hoặc xóa ánh xạ không?
2. Phương thức có thể ghi nhận truy cập và đổi thứ tự không?
3. Nếu chỉ muốn sửa giá trị của ánh xạ đang duyệt, có cách thao tác qua entry phù hợp không?

## 3. Thay đổi cấu trúc không đồng nghĩa thay đổi size

### 3.1. Cùng số ánh xạ, khác chuỗi

Ban đầu:

~~~text
A=1, B=2, C=3
thứ tự: A -> B -> C
size: 3
~~~

Sau `get("A")` ở chế độ truy cập:

~~~text
A=1, B=2, C=3
thứ tự: B -> C -> A
size: 3
~~~

Không ánh xạ nào được thêm, xóa hoặc thay giá trị, nhưng đường duyệt đã thay đổi.

Một ví dụ khác là xóa A rồi thêm D. Số lượng cuối cùng vẫn là ba, nhưng đây là hai thay đổi thành phần ánh xạ. Iterator cũ không được hợp lệ hóa chỉ vì size cuối cùng quay về số ban đầu.

### 3.2. Thứ tự cuối cùng giống ban đầu cũng chưa đủ

Giả sử lần lượt truy cập A, B, C trên map có thứ tự ban đầu A, B, C:

~~~text
ban đầu:   [A, B, C]
get(A):    [B, C, A]
get(B):    [C, A, B]
get(C):    [A, B, C]
~~~

Thứ tự cuối cùng trùng ban đầu, nhưng trong khoảng đó chuỗi đã bị sửa nhiều lần. Iterator được tạo trước các lần sửa không trở lại hợp lệ một cách tự động.

Đây là khác biệt giữa so sánh hai ảnh chụp trạng thái và tuân thủ quy tắc thao tác trong một lượt duyệt. Với iterator, lịch sử sửa đổi trong khoảng duyệt có ý nghĩa.

### 3.3. Cập nhật giá trị qua entry là trường hợp khác

Trong lượt duyệt `entrySet` của `LinkedHashMap`, `entry.getValue()` đọc giá trị đang có; `entry.setValue(newValue)` là cách được hỗ trợ để thay giá trị của ánh xạ ấy. Nó không phải một lượt get hoặc put trên map nên không dùng để ghi nhận độ mới của truy cập.

Ví dụ minh họa:

~~~java
for (Map.Entry<String, Integer> entry : cache.entrySet()) {
    entry.setValue(entry.getValue() + 1);
}
~~~

Đoạn này giả sử giá trị không null và không có sửa đổi cạnh tranh. Nó cập nhật các giá trị theo lượt duyệt hiện tại mà không chủ động đưa từng khóa xuống cuối.

Entry lấy từ góc nhìn gắn với map gốc. Không nên giữ nó để dùng tùy ý sau khi lượt duyệt kết thúc hoặc map đã bị sửa. Khi cần dữ liệu độc lập, hãy sao chép khóa và giá trị sang dạng phù hợp. [Java 17: Map.Entry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html)

## 4. Iterator theo dõi điều gì?

### 4.1. Mô hình của OpenJDK 17

Một cách giải thích cơ chế là đặt hai bộ đếm cạnh nhau:

~~~text
map:      modCount
iterator: expectedModCount tại lúc được tạo

nếu map thay đổi cấu trúc:
    modCount có thể được tăng

khi iterator thực hiện bước có kiểm tra:
    nếu hai bộ đếm khác nhau -> phát hiện sửa đổi
~~~

Đây là mô hình diễn giải cơ chế của OpenJDK 17, không phải mã nguồn được chép lại.

Trong phiên bản này, việc chuyển một nút chưa ở cuối xuống cuối tăng `modCount`. Iterator ghi nhận giá trị kỳ vọng; đường `next` kiểm tra sự khác biệt. Xóa qua chính iterator cập nhật lại giá trị kỳ vọng của iterator đó. `hasNext` chỉ kiểm tra còn nút kế tiếp. [OpenJDK 17: LinkedHashMap.java](https://raw.githubusercontent.com/openjdk/jdk/jdk-17%2B35/src/java.base/share/classes/java/util/LinkedHashMap.java)

Từ đó có thể hiểu hai hiện tượng thường gây nhầm:

- `get` có thể hoàn tất bình thường; ngoại lệ chỉ xuất hiện ở bước tiếp theo của iterator.
- `hasNext()` trả về true không chứng minh iterator vẫn dùng được sau một thay đổi cấu trúc.

Không dùng thông tin về thời điểm kiểm tra để tìm cách “lách” sửa đổi. Cơ chế giải thích lỗi; hợp đồng sử dụng mới là cơ sở viết mã.

### 4.2. Get ở cuối chuỗi có thể không làm đổi liên kết

OpenJDK 17 chỉ thực hiện việc chuyển vị trí khi nút được truy cập chưa ở cuối. Truy cập khóa cuối hoặc khóa không tồn tại vì vậy có thể không làm iterator báo lỗi trong một thí nghiệm.

Điều đó không chứng minh mẫu “vừa duyệt vừa get cùng map” là hợp lệ cho mọi dữ liệu. Khi lượng dữ liệu hoặc thứ tự thay đổi, một khóa khác có thể chưa ở cuối. Một ví dụ một phần tử cũng không bao phủ trường hợp nhiều phần tử.

Lựa chọn lập trình phải chịu được dữ liệu hợp lệ nói chung. Nếu chỉ cần giá trị của entry đang duyệt, đọc từ entry là cách rõ ràng và không phụ thuộc vị trí tình cờ.

### 4.3. Iterator không phải bản sao

`keySet()`, `values()` và `entrySet()` là những góc nhìn (view) gắn với map, không phải các bản dữ liệu tách biệt. Việc tạo biến `Set<String> keys = cache.keySet()` không tạo một ảnh chụp tại thời điểm đó.

Tương tự, iterator không đóng băng chuỗi ánh xạ để ứng dụng tùy ý sửa map gốc. Nếu muốn duyệt một cấu trúc độc lập, phải tạo cấu trúc độc lập một cách rõ ràng và hiểu giới hạn sao chép của nó.

## 5. Fail-fast có ý nghĩa gì và không bảo đảm điều gì?

### 5.1. ConcurrentModificationException không đòi hỏi nhiều luồng

Tên ngoại lệ dễ khiến người học tìm một luồng thứ hai dù lỗi do chính vòng lặp:

~~~java
for (String key : cache.keySet()) {
    Integer value = cache.get(key);
    System.out.println(key + "=" + value);
}
~~~

Đây là mã minh họa có vấn đề khi `cache` giữ thứ tự truy cập. Vòng for rút gọn dùng iterator ẩn. Thân vòng lặp gọi get trên cùng map và có thể đổi chuỗi mà iterator đang đi.

Chương trình có thể chạy hoàn toàn trên một luồng. “Sửa trong lúc duyệt” ở đây là hai hoạt động chồng lên cùng khoảng đời iterator, không nhất thiết là hai bộ xử lý cùng thực thi.

### 5.2. Ngoại lệ là tín hiệu phát hiện lỗi

Fail-fast là cơ chế phát hiện một số sửa đổi không phù hợp. Nó không phải khóa, giao dịch, ảnh chụp hay hợp đồng map đồng thời.

Không viết thuật toán dạng:

~~~java
try {
    // Duyệt trong khi vẫn sửa map theo cách không phù hợp.
} catch (ConcurrentModificationException exception) {
    // Coi như mọi dữ liệu đã được xử lý an toàn.
}
~~~

Đoạn trên chỉ minh họa lối xử lý sai; không phải giải pháp.

Một phần công việc có thể đã xảy ra trước ngoại lệ. Bắt lỗi rồi chạy lại có thể lặp tác dụng phụ, gửi dữ liệu hai lần hoặc xử lý thiếu phần tử nếu thuật toán không có thiết kế riêng cho việc thử lại.

Không có ngoại lệ cũng không chứng minh đúng. Cần kiểm tra các thao tác thực tế và hợp đồng dữ liệu, không dùng sự im lặng của cơ chế phát hiện làm bằng chứng an toàn. [Java 17: chính sách fail-fast của LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html)

### 5.3. Không tiếp tục “sửa chữa” iterator cũ

Nếu đã thay đổi cấu trúc map theo cách làm lượt duyệt mất hợp lệ, không nên gọi `iterator.remove` với hy vọng nó tự đồng bộ mọi thứ. Cách xóa hợp lệ qua iterator đòi hỏi chính lượt duyệt vẫn tuân thủ quy tắc.

Khi thuật toán cần một giai đoạn sửa đổi riêng, hãy kết thúc lượt duyệt cũ, áp dụng thay đổi rồi tạo lượt duyệt mới nếu cần. Việc tạo iterator mới phải nằm ở điểm thuật toán có trạng thái rõ ràng, không phải một cách thử lại mù để né lỗi.

## 6. Chọn cách duyệt theo mục đích

### 6.1. Chỉ đọc cặp khóa–giá trị

Dùng entry đang được duyệt:

~~~java
for (Map.Entry<String, Integer> entry : cache.entrySet()) {
    System.out.println(entry.getKey() + "=" + entry.getValue());
}
~~~

Cách này tránh lượt tra cứu theo khóa và tránh việc get ghi nhận truy cập trên map. Nó không cần giữ một giả định về khóa nào đang ở cuối.

Nếu chỉ cần khóa, duyệt `keySet`. Nếu chỉ cần giá trị, duyệt `values`. Góc nhìn đúng giúp tránh làm thêm việc không cần thiết.

### 6.2. Sửa giá trị nhưng giữ nguyên thứ tự

Có thể dùng `entry.setValue` trong lượt duyệt entrySet như ở phần trước. Cần quyết định rõ đó có đúng nghiệp vụ không.

Ví dụ, tác vụ điều chỉnh giá của mọi mặt hàng có thể không được xem là một lần người dùng sử dụng mặt hàng. Giữ nguyên thứ tự truy cập là hợp lý. Ngược lại, nếu nghiệp vụ yêu cầu việc cập nhật đó phải làm mới độ gần đây, không dùng setValue chỉ để tránh ngoại lệ rồi vô tình thay đổi ngữ nghĩa. Hãy tách thao tác thành giai đoạn phù hợp.

### 6.3. Xóa trong chính lượt duyệt

Khi cần loại bỏ một số ánh xạ, dùng iterator tường minh và xóa qua chính iterator đó:

~~~java
Iterator<Map.Entry<String, Integer>> iterator =
        cache.entrySet().iterator();

while (iterator.hasNext()) {
    Map.Entry<String, Integer> entry = iterator.next();
    if (entry.getValue() < 0) {
        iterator.remove();
    }
}
~~~

Đoạn minh họa giả sử giá trị khác null và không có thao tác khác sửa map. Không gọi `cache.remove(entry.getKey())` thay cho `iterator.remove()` trong cùng lượt duyệt.

`remove` của iterator chỉ được gọi sau `next` và tối đa một lần cho mỗi lần next. Gọi trước next hoặc gọi hai lần liên tiếp là lỗi trạng thái, khác với lỗi phát hiện thay đổi cấu trúc. [Java 17: Iterator.remove](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html)

### 6.4. Ngoại lệ cho chính iterator không lan sang iterator khác

Giả sử có hai iterator cùng map. Iterator thứ nhất xóa bằng chính `remove` của nó. Lượt duyệt thứ nhất được phép tiếp tục theo quy tắc ấy; iterator thứ hai không tự được cập nhật theo.

Vì vậy, câu “tôi đã dùng iterator.remove nên mọi iterator đều an toàn” sai. Quyền sửa được gắn với chính lượt duyệt đang thực hiện thao tác, không phải giấy phép chung cho mọi lượt duyệt khác.

## 7. Nếu thật sự cần get hoặc put từng khóa thì sao?

### 7.1. Duyệt bản sao danh sách khóa

Nếu cần các lời gọi get làm mới thứ tự, có thể duyệt một danh sách khóa độc lập:

~~~java
List<String> keys = new ArrayList<>(cache.keySet());
for (String key : keys) {
    Integer value = cache.get(key);
    process(key, value);
}
~~~

Đoạn này là mẫu minh họa; `process` đại diện cho hàm xử lý do ứng dụng định nghĩa. Ví dụ xét một luồng và không có thay đổi khác trong lúc tạo danh sách.

Iterator của vòng for thuộc danh sách, không thuộc map đang đổi thứ tự. Đây là lý do mẫu tránh xung đột cấu trúc giữa chính vòng lặp đó và get trên map.

Nhưng cần hiểu kết quả:

- Danh sách là ảnh chụp các tham chiếu khóa, không phải ảnh chụp mọi giá trị.
- Get đọc giá trị của map tại lúc gọi.
- Get vẫn chủ động làm mới thứ tự map.
- Nếu có thay đổi khác, khóa trong danh sách có thể không còn trong map.
- Tạo danh sách từ map đang bị sửa cạnh tranh vẫn cần chính sách đồng bộ hóa.

Bản sao giải quyết một ranh giới cụ thể, không tự giải quyết mọi vấn đề đồng thời hoặc tính nhất quán.

### 7.2. Nếu muốn báo cáo mà không làm mới thứ tự

Đọc qua entrySet có thể đã đủ khi lượt duyệt được bảo vệ phù hợp. Nếu muốn xử lý báo cáo lâu ở ngoài phần được bảo vệ, có thể sao chép các ánh xạ vào một `LinkedHashMap` mới rồi duyệt bản sao.

Constructor từ map tạo một map thứ tự chèn chứa các ánh xạ theo thứ tự gặp ở nguồn. Bản sao không tự trở thành một access-order map chỉ vì nguồn dùng chế độ đó. Trong tình huống này, nó giúp giữ thứ tự hiện có để đọc độc lập.

Cần sao chép khi nguồn ở trạng thái ổn định; nếu có nhiều luồng, thực hiện bước sao chép trong chính sách đồng bộ hóa của ứng dụng. Khóa và giá trị vẫn là các tham chiếu được dùng lại; đối tượng có thể thay đổi bên trong không tự được sao chép sâu.

### 7.3. Sao chép danh sách entry chưa chắc tách dữ liệu

`new ArrayList<>(cache.entrySet())` tạo một danh sách mới, nhưng các phần tử có thể vẫn là entry gắn với map. Đừng coi đó là bản ghi độc lập chỉ vì đối tượng List mới được tạo.

Khi cần các cặp độc lập, có thể tạo từng `AbstractMap.SimpleImmutableEntry` từ khóa và giá trị. Bản ghi cặp độc lập vẫn không sao chép sâu đối tượng khóa hoặc giá trị.

Điều cần chọn là mức độ tách biệt thực sự cần: tách cấu trúc duyệt, tách cặp ánh xạ hay tách toàn bộ dữ liệu có thể thay đổi. Ba yêu cầu này không giống nhau.

## 8. Hàm gọi lại, forEach và stream không tự loại bỏ vấn đề

Đổi cú pháp vòng lặp không thay đổi bản chất nếu phần xử lý vẫn gọi ngược vào map:

~~~java
cache.forEach((key, value) -> {
    cache.get(key);
});
~~~

Đây là mẫu minh họa có vấn đề trên access-order map. `forEach` đã cung cấp value; gọi get không cần thiết và có thể thay đổi thứ tự trong chính quá trình duyệt.

Một lời gọi gián tiếp cũng có tác động tương tự. Ví dụ, hàm tạo báo cáo nhận key rồi tự gọi dịch vụ tra cứu cùng map. Thân vòng lặp nhìn ngắn và “chỉ ghi log”, nhưng chuỗi lời gọi có thể vẫn chứa get.

Với stream, các hàm xử lý phải tuân thủ yêu cầu không can thiệp nguồn theo hợp đồng của pipeline. Chuyển từ for sang `stream().forEach(...)` không cho phép tùy ý thay đổi cấu trúc nguồn. Đặc biệt, `parallelStream` không biến LinkedHashMap thành map đồng thời. [Java 17: yêu cầu không can thiệp của stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html)

Các hàm gọi lại (callback) của `compute` và `merge` cũng không nên tự sửa lại cùng map trong quá trình tính toán. Trên access-order map, cả lời gọi tưởng như chỉ tra cứu cũng cần được xét vì nó có thể sửa thứ tự. Hãy tính từ tham số callback đã nhận hoặc dữ liệu bên ngoài được tổ chức phù hợp. [Java 17: Map.compute và Map.merge](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)

## 9. Đồng bộ hóa phải bao phủ cả thứ tự truy cập

### 9.1. Không coi get là thao tác giữ nguyên cấu trúc

`LinkedHashMap` không tự đồng bộ hóa. Nếu thiết kế có nhiều luồng, cần tính cả những lời gọi get có thể đổi thứ tự vào nhóm thao tác cần bảo vệ.

Ví dụ, một thiết kế dùng khóa đọc–ghi rồi cho phép nhiều get trên access-order map cùng giữ “khóa đọc” có thể dựa trên giả định sai: các lời gọi này không nhất thiết chỉ đọc cấu trúc. Cần thiết kế quy tắc khóa theo trạng thái thực sự bị sửa.

### 9.2. SynchronizedMap không khóa trọn vòng lặp tự động

Với map bọc bằng `Collections.synchronizedMap`, tài liệu yêu cầu đồng bộ hóa trên chính map trả về khi duyệt các góc nhìn. Mọi truy cập cần đi qua cùng cơ chế bảo vệ, tránh giữ tham chiếu map gốc rồi truy cập vòng ngoài. [Java 17: Collections.synchronizedMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map))

Ví dụ minh họa, giả sử các import và việc nạp dữ liệu được thực hiện trong chương trình:

~~~java
Map<String, Integer> shared =
        Collections.synchronizedMap(
                new LinkedHashMap<String, Integer>(16, 0.75f, true));

synchronized (shared) {
    for (Map.Entry<String, Integer> entry : shared.entrySet()) {
        System.out.println(entry.getKey() + "=" + entry.getValue());
    }
}
~~~

Khóa là `shared`, không phải đối tượng `shared.entrySet()`. Đoạn duyệt lấy giá trị trực tiếp từ entry.

### 9.3. Có khóa vẫn có thể tự làm iterator mất hợp lệ

Khóa giúp ngăn những truy cập cạnh tranh tuân thủ cùng khóa. Nó không biến việc đổi thứ tự trong chính thân vòng lặp thành thao tác hợp lệ với iterator cũ.

Nếu ở trong khối synchronized mà vẫn gọi `shared.get(key)` khi đang duyệt chính access-order map đó, cùng luồng vẫn có thể làm đổi chuỗi. Do đó phải xử lý hai vấn đề riêng:

1. Cách các luồng truy cập dữ liệu.
2. Cách thuật toán của một lượt duyệt sửa chính dữ liệu đang duyệt.

Một biện pháp giải quyết vấn đề thứ nhất không tự chứng minh vấn đề thứ hai đã được giải quyết.

## 10. Chương trình Java đầy đủ

Lưu toàn bộ mã sau vào `AccessOrderIteratorDemo.java`. Chương trình dùng các map độc lập cho từng tình huống để kết quả của phần trước không làm nhiễu phần sau.

Các phép kiểm tra khẳng định thứ tự, tổng và dữ liệu của những đường dùng hợp lệ. Hàm `observedCme` chỉ ghi nhận ngoại lệ quan sát được trong các tình huống cố ý làm iterator cũ không còn phù hợp. Nó không phải công cụ quyết định tính đúng cho ứng dụng.

~~~java
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AccessOrderIteratorDemo {
    private static LinkedHashMap<String, Integer> sample(boolean accessOrder) {
        LinkedHashMap<String, Integer> map =
                new LinkedHashMap<>(16, 0.75f, accessOrder);
        map.put("A", 1);
        map.put("B", 2);
        map.put("C", 3);
        return map;
    }

    private static List<String> keys(Map<String, Integer> map) {
        return new ArrayList<>(map.keySet());
    }

    private static void requireOrder(Map<String, Integer> map,
                                     List<String> expected) {
        if (!keys(map).equals(expected)) {
            throw new AssertionError("Unexpected order");
        }
    }

    // Observation only: application correctness must not depend on CME.
    private static boolean observedCme(Iterator<?> iterator) {
        try {
            iterator.next();
            return false;
        } catch (ConcurrentModificationException exception) {
            return true;
        }
    }

    public static void main(String[] args) {
        LinkedHashMap<String, Integer> access = sample(true);
        Iterator<String> old = access.keySet().iterator();
        access.get("A");
        requireOrder(access, List.of("B", "C", "A"));
        System.out.println("access/size=" + access.size());
        System.out.println("access/order=" + keys(access));
        System.out.println("access/oldIteratorCME=" + observedCme(old));

        LinkedHashMap<String, Integer> insertion = sample(false);
        Iterator<String> insertionIterator = insertion.keySet().iterator();
        insertion.put("A", 10);
        requireOrder(insertion, List.of("A", "B", "C"));
        System.out.println("insertion/afterPut=" + insertion);
        System.out.println("insertion/next=" + insertionIterator.next());

        LinkedHashMap<String, Integer> views = sample(true);
        Iterator<String> viewIterator = views.keySet().iterator();
        boolean containsA = views.containsKey("A");
        int sum = 0;
        for (Map.Entry<String, Integer> entry : views.entrySet()) {
            sum += entry.getValue();
        }
        requireOrder(views, List.of("A", "B", "C"));
        if (!containsA || sum != 6) {
            throw new AssertionError("Unexpected view result");
        }
        System.out.println("views/containsA=" + containsA + ", sum=" + sum);
        System.out.println("views/order=" + keys(views));
        System.out.println("views/next=" + viewIterator.next());

        LinkedHashMap<String, Integer> updated = sample(true);
        for (Map.Entry<String, Integer> entry : updated.entrySet()) {
            entry.setValue(entry.getValue() * 10);
        }
        requireOrder(updated, List.of("A", "B", "C"));
        System.out.println("setValue/map=" + updated);

        LinkedHashMap<String, Integer> removed = sample(true);
        List<String> visited = new ArrayList<>();
        Iterator<Map.Entry<String, Integer>> removalIterator =
                removed.entrySet().iterator();
        while (removalIterator.hasNext()) {
            Map.Entry<String, Integer> entry = removalIterator.next();
            visited.add(entry.getKey());
            if (entry.getValue() == 2) {
                removalIterator.remove();
            }
        }
        requireOrder(removed, List.of("A", "C"));
        System.out.println("remove/visited=" + visited);
        System.out.println("remove/map=" + removed);

        LinkedHashMap<String, Integer> two = sample(true);
        Iterator<String> first = two.keySet().iterator();
        Iterator<String> second = two.keySet().iterator();
        first.next();
        first.remove();
        requireOrder(two, List.of("B", "C"));
        System.out.println("twoIterators/otherCME=" + observedCme(second));

        LinkedHashMap<String, Integer> snapshotMap = sample(true);
        List<String> snapshot = keys(snapshotMap);
        List<List<String>> orders = new ArrayList<>();
        for (String key : snapshot) {
            snapshotMap.get(key);
            orders.add(keys(snapshotMap));
        }
        requireOrder(snapshotMap, List.of("A", "B", "C"));
        System.out.println("snapshot/keys=" + snapshot);
        System.out.println("snapshot/orders=" + orders);

        LinkedHashMap<String, Integer> sameSize = sample(true);
        Iterator<String> beforeReplacement = sameSize.keySet().iterator();
        sameSize.remove("A");
        sameSize.put("D", 4);
        requireOrder(sameSize, List.of("B", "C", "D"));
        System.out.println("sameSize/size=" + sameSize.size()
                + ", order=" + keys(sameSize));
        System.out.println("sameSize/oldIteratorCME="
                + observedCme(beforeReplacement));

        LinkedHashMap<String, Integer> nullable = sample(true);
        for (Map.Entry<String, Integer> entry : nullable.entrySet()) {
            if (entry.getKey().equals("B")) {
                entry.setValue(null);
            }
        }
        requireOrder(nullable, List.of("A", "B", "C"));
        Integer value = nullable.get("B");
        requireOrder(nullable, List.of("A", "C", "B"));
        System.out.println("nullAccess/value=" + value
                + ", order=" + keys(nullable));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 AccessOrderIteratorDemo.java
java AccessOrderIteratorDemo
~~~

Stdout của lần chạy kiểm chứng:

~~~text
access/size=3
access/order=[B, C, A]
access/oldIteratorCME=true
insertion/afterPut={A=10, B=2, C=3}
insertion/next=A
views/containsA=true, sum=6
views/order=[A, B, C]
views/next=A
setValue/map={A=10, B=20, C=30}
remove/visited=[A, B, C]
remove/map={A=1, C=3}
twoIterators/otherCME=true
snapshot/keys=[A, B, C]
snapshot/orders=[[B, C, A], [C, A, B], [A, B, C]]
sameSize/size=3, order=[B, C, D]
sameSize/oldIteratorCME=true
nullAccess/value=null, order=[A, C, B]
~~~

Mã đã được biên dịch với `--release 17` và chạy thành công. Tùy chọn release xác định mức API và mã đích tương thích; nó không khẳng định JVM thực thi là đúng bản JDK 17.

Các dòng `CME=true` là kết quả quan sát trên môi trường đã chạy, phù hợp cơ chế OpenJDK được giải thích. Không biến kết quả đó thành quy tắc “mọi sửa đổi sai luôn ném ngoại lệ đúng tại vị trí này”.

## 11. Đọc kết quả theo câu hỏi mà từng phần trả lời

### 11.1. Access: size không đổi nhưng đường duyệt đổi

Ba dòng đầu cho thấy size vẫn bằng ba, thứ tự đã thành B, C, A và lần dùng iterator cũ ghi nhận CME.

Không có luồng phụ nào trong chương trình. Không có thay đổi giá trị. Tác nhân là lần get trên chế độ thứ tự truy cập.

### 11.2. Insertion: thay giá trị một khóa tồn tại

Phần insertion tạo iterator trước khi `put("A", 10)`. Map ở chế độ thứ tự chèn, nên cập nhật giá trị không đổi thành phần ánh xạ hoặc thứ tự chèn. Iterator tiếp tục trả về A trong tình huống đó.

Không suy ra put luôn an toàn khi duyệt: thêm một khóa mới là trường hợp khác, và access-order cũng có quy tắc khác.

### 11.3. Views và setValue: đọc hoặc sửa đúng chỗ

`containsKey("A")` cùng lượt duyệt entrySet tính tổng không chuyển A xuống cuối. Iterator được tạo trước đó vẫn có thể lấy A.

Ở phần setValue, chương trình sửa giá trị từng entry thành mười lần giá trị cũ. Thứ tự vẫn là A, B, C. Nó không dùng put để cập nhật từng khóa và không dùng get để đọc giá trị.

### 11.4. Remove: đúng iterator và iterator khác

Lượt xóa đi qua A, B, C; chỉ B bị loại qua `removalIterator.remove()`. Kết quả còn A và C.

Trong tình huống có hai iterator, iterator thứ nhất xóa hợp lệ qua remove của nó. Iterator thứ hai ghi nhận CME khi được dùng tiếp. Điều đó minh họa giới hạn của ngoại lệ dành cho chính iterator.

### 11.5. Snapshot: thứ tự cuối có thể quay về ban đầu

Danh sách khóa độc lập giữ A, B, C. Vòng lặp đi trên danh sách nên có thể get từng khóa ở map mà không sửa chính cấu trúc đang được iterator danh sách duyệt.

Các ảnh chụp sau từng get lần lượt là B, C, A; C, A, B; A, B, C. Thứ tự cuối giống ban đầu nhưng có lịch sử thay đổi. Không được dùng sự trùng khớp cuối cùng để hợp lệ hóa một iterator map cũ.

### 11.6. SameSize và nullAccess

Xóa A rồi thêm D giữ size bằng ba, nhưng iterator cũ vẫn bị ảnh hưởng. Bộ đếm kích thước không thay thế việc theo dõi thay đổi.

Với nullAccess, giá trị B được đặt thành null qua entry trước khi get. Thứ tự trước get được kiểm tra là A, B, C. Sau get, kết quả trả về null và thứ tự thành A, C, B: một giá trị null không làm lượt truy cập mất tác động lên thứ tự.

## 12. Cách tìm lỗi trong mã thực tế

Khi gặp lỗi quanh iterator, hãy ghi lại một dòng thời gian thay vì chỉ nhìn dòng ném ngoại lệ:

| Mốc | Thông tin cần xác định |
|---|---|
| Tạo map | Chế độ thứ tự chèn hay truy cập? |
| Tạo iterator | Tạo trước hay sau thao tác nghi ngờ? |
| Thân vòng lặp | Có get, put, remove hoặc hàm gọi gián tiếp vào cùng map không? |
| Góc nhìn | Đây là view đang sống hay cấu trúc sao chép độc lập? |
| Phần cập nhật | Qua chính iterator, entry hay map? |
| Đồng thời | Mọi truy cập có tuân thủ cùng chính sách khóa không? |
| Xử lý lỗi | Đã có tác dụng phụ nào trước khi ngoại lệ xuất hiện? |

Nếu lỗi nằm ở `iterator.next()`, tác nhân có thể nằm ở lời gọi trước đó. Đọc cả chuỗi hàm trợ giúp, callback và hàm theo dõi trạng thái.

Không chốt nguyên nhân chỉ bằng việc đổi dữ liệu làm lỗi tạm biến mất. Một khóa tình cờ ở cuối hoặc một map chỉ có một phần tử có thể che khuất việc gọi get làm đổi thứ tự trong trường hợp khác.

## 13. Thực hành và lời giải

### Bài 1. Đọc dòng thời gian

Map access-order có thứ tự A, B, C. Tạo iterator rồi gọi get(B). Thứ tự mới là gì? Vì sao size không chứng minh iterator cũ vẫn hợp lệ?

**Lời giải:** Thứ tự thành A, C, B. Số ánh xạ vẫn là ba nhưng chuỗi đã đổi, thuộc trạng thái cấu trúc cần xét. Iterator cũ được tạo trước thay đổi; size chỉ đếm số ánh xạ.

### Bài 2. Có cần luồng thứ hai không?

Một chương trình một luồng duyệt keySet và gọi get từng khóa trên cùng map. Vì sao vẫn có thể có CME?

**Lời giải:** For rút gọn dùng iterator. Get ở chế độ truy cập có thể đổi chuỗi mà iterator đang dùng. Xung đột giữa duyệt và sửa có thể do cùng luồng tạo ra; tên ngoại lệ không đòi hỏi có luồng thứ hai.

### Bài 3. Sửa cách đọc

Bạn chỉ cần in cặp khóa–giá trị nhưng đang duyệt keySet rồi get. Hãy sửa và nêu hai lợi ích.

**Lời giải:** Duyệt entrySet, dùng entry.getKey và entry.getValue. Tránh lượt tra cứu dư và tránh get làm mới thứ tự trên map. Điều đó không tự giải quyết sửa đổi từ luồng khác.

### Bài 4. Cập nhật có đúng nghiệp vụ không?

Một tác vụ tăng giá trị mọi entry nhưng không được làm thay đổi độ mới truy cập. Dùng entry.setValue có phù hợp không? Nếu việc cập nhật phải làm mới độ gần đây thì sao?

**Lời giải:** SetValue trong lượt duyệt entrySet phù hợp với yêu cầu giữ thứ tự, khi lượt duyệt được bảo vệ đúng. Nếu cập nhật phải làm mới độ gần đây, cần tổ chức giai đoạn riêng hoặc duyệt khóa độc lập rồi thực hiện thao tác mang ngữ nghĩa truy cập; không bỏ tác động nghiệp vụ chỉ để né ngoại lệ.

### Bài 5. Xóa sai chỗ

Một vòng while dùng iterator nhưng xóa bằng cache.remove(key). Vì sao đổi sang iterator.remove có ý nghĩa? Quy tắc gọi remove là gì?

**Lời giải:** Xóa qua chính iterator cho phép lượt duyệt đó duy trì trạng thái kỳ vọng phù hợp. Remove được gọi sau next, tối đa một lần cho mỗi next. Nó không cho phép thêm sửa đổi khác tùy ý và không làm iterator thứ hai tự hợp lệ.

### Bài 6. HasNext trả về true

Sau một lần get làm đổi thứ tự, hasNext vẫn trả về true trong một thí nghiệm OpenJDK. Có nên tiếp tục vì “iterator đã xác nhận” không?

**Lời giải:** Không. Trong cơ chế đã giải thích, hasNext chỉ thấy còn nút kế tiếp; bước next mới kiểm tra bộ đếm thay đổi. Quan trọng hơn, thuật toán phải tuân thủ quy tắc duyệt, không dựa vào thời điểm phát hiện của một phương thức.

### Bài 7. Không có ngoại lệ

Một bài thử chỉ có một khóa, vừa duyệt vừa get mà không có ngoại lệ. Có đủ để giữ mẫu đó trong hệ thống nhiều khóa không?

**Lời giải:** Không. Khóa duy nhất đã ở cuối; cách triển khai có thể không phải chuyển liên kết. Dữ liệu khác có thể gây thay đổi. Sự im lặng của fail-fast không phải bằng chứng mẫu dùng hợp lệ nói chung.

### Bài 8. Ảnh chụp khóa

Đổi sang new ArrayList của keySet giúp tránh điều gì? Nó có tạo ảnh chụp mọi giá trị và bảo đảm đồng thời không?

**Lời giải:** Iterator của vòng lặp đi trên danh sách độc lập nên get trên map không sửa chính cấu trúc danh sách ấy. Giá trị vẫn được đọc ở map tại thời điểm get; khóa hoặc giá trị có thể đã đổi nếu có hoạt động khác. Bước sao chép và truy cập vẫn cần chính sách đồng thời phù hợp.

### Bài 9. Khóa đầy đủ nhưng vẫn sai

Ứng dụng giữ synchronized trên map bọc suốt vòng lặp, nhưng trong thân lại get trên chính access-order map. Vì sao khóa chưa đủ?

**Lời giải:** Khóa ngăn hoạt động cạnh tranh tuân thủ cùng khóa. Nó không ngăn chính luồng đang giữ khóa đổi thứ tự map và làm lượt duyệt của mình mất hợp lệ. Cần sửa cả thuật toán của lượt duyệt, chẳng hạn đọc value từ entry.

### Bài 10. Trạng thái cuối giống ban đầu

Tạo iterator trên A, B, C, rồi get A, get B, get C. Thứ tự cuối lại là A, B, C. Có thể dùng iterator cũ như chưa có gì xảy ra không?

**Lời giải:** Không. Có nhiều lần chuyển thứ tự trong khoảng ấy. Hợp đồng lượt duyệt xét sửa đổi trong quá trình, không chỉ so sánh hai trạng thái đầu và cuối.

## 14. Câu hỏi cần trả lời trước khi viết vòng lặp

Hãy xác định rõ dữ liệu đang duyệt là view hay bản sao, chế độ thứ tự nào đang dùng và thao tác trong thân có thể đổi những loại trạng thái nào. Nếu chỉ đọc cặp dữ liệu, dùng entry. Nếu sửa giá trị mà giữ thứ tự, cân nhắc setValue trong lượt duyệt. Nếu xóa, dùng đúng iterator của lượt duyệt. Nếu cần làm mới truy cập từng khóa, tách cấu trúc duyệt hoặc chia giai đoạn có chủ đích.

Tính đúng đến từ cách tổ chức thao tác và chính sách đồng thời phù hợp. Fail-fast giúp phát hiện lỗi; nó không thay thế những quyết định ấy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linked-hash-map-008">Thao tác đầu–cuối và view đảo thứ tự từ Java 21</a>

<details>
<summary>Xem chi tiết</summary>

# Thao tác đầu–cuối và view đảo thứ tự của LinkedHashMap từ Java 21

## 1. Bài toán: map đã có thứ tự, làm việc với hai đầu như thế nào?

`LinkedHashMap` giữ các cặp khóa–giá trị theo một thứ tự duyệt xác định. Với một danh sách công việc theo mã, ta có thể muốn xem công việc đầu tiên, lấy công việc cuối ra khỏi danh sách, đưa một mã đang có lên đầu hoặc hiển thị cùng dữ liệu theo chiều ngược lại.

Những yêu cầu đó khác với tra cứu `get(key)`. `get` tìm theo khóa, còn thao tác đầu–cuối tìm theo **vị trí trong thứ tự duyệt**. Khóa đầu tiên không nhất thiết nhỏ nhất, tên đầu tiên không nhất thiết đứng trước theo bảng chữ cái và giá trị cuối không nhất thiết lớn nhất.

Từ Java 21, `LinkedHashMap` triển khai `SequencedMap`. Nhờ đó, chương trình có API thống nhất để xem, thêm, đổi vị trí hoặc lấy ra mapping ở hai đầu và lấy một khung nhìn đảo thứ tự. Đây là API thư viện chuẩn, không cần bật `--enable-preview`. Các ví dụ trong bài cần JDK 21 trở lên; chúng không biên dịch với `--release 17`. Có thể đối chiếu thời điểm bổ sung tại [LinkedHashMap — Java 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashMap.html).

Ba khái niệm sẽ được dùng xuyên suốt:

- **Thứ tự duyệt (encounter order):** thứ tự nhìn thấy các entry khi duyệt map.
- **Đầu và cuối:** entry đứng đầu hoặc cuối trong thứ tự của đối tượng đang được gọi.
- **Khung nhìn đảo (`reversed` view):** một cách nhìn cùng map theo chiều ngược lại, có liên kết với dữ liệu gốc.

Hãy hình dung một map có thứ tự khóa A, B, C:

```text
map:          đầu → A → B → C ← cuối
map.reversed: đầu → C → B → A ← cuối
```

Chỉ việc tạo view đảo không di chuyển A, B hay C trong map gốc. Nó thay đổi chiều quan sát. Khi gọi một thao tác có ý nghĩa đầu–cuối trên view, ý nghĩa đó được tính theo chiều của view.

## 2. Nhận diện đúng các API mới

### 2.1. Các thao tác trên cả mapping

Mỗi mapping, còn gọi là một entry, gồm cả khóa và giá trị. Với map có kiểu khóa `K`, giá trị `V`, các phương thức chính là:

| Phương thức | Kiểu kết quả | Có thay đổi map không? | Ý nghĩa |
|---|---|---|---|
| `firstEntry()` | `Map.Entry<K,V>` hoặc `null` | Không loại/thêm entry | Xem mapping đầu |
| `lastEntry()` | `Map.Entry<K,V>` hoặc `null` | Không loại/thêm entry | Xem mapping cuối |
| `pollFirstEntry()` | `Map.Entry<K,V>` hoặc `null` | Có nếu map không rỗng | Lấy và loại mapping đầu |
| `pollLastEntry()` | `Map.Entry<K,V>` hoặc `null` | Có nếu map không rỗng | Lấy và loại mapping cuối |
| `putFirst(k, v)` | Giá trị cũ kiểu `V`, có thể `null` | Có thể thêm/cập nhật/đổi vị trí | Đặt mapping ở đầu |
| `putLast(k, v)` | Giá trị cũ kiểu `V`, có thể `null` | Có thể thêm/cập nhật/đổi vị trí | Đặt mapping ở cuối |
| `reversed()` | `SequencedMap<K,V>` | Tạo view, không tự đảo dữ liệu gốc | Nhìn map theo thứ tự ngược |

`LinkedHashMap` thông thường hỗ trợ các thao tác thay đổi trên đây. Khi viết mã nhận một `SequencedMap` bất kỳ, cần nhớ một số thao tác thay đổi là tùy chọn: một cách triển khai có thể không hỗ trợ việc đặt tùy ý mapping vào đầu/cuối. Tên interface không xóa bỏ các ràng buộc của đối tượng thực tế.

### 2.2. Các view có thứ tự

Ba phương thức bổ sung cho phép làm việc với riêng khóa, riêng giá trị hoặc toàn entry bằng kiểu có thao tác đầu–cuối:

| Phương thức | Kiểu trả về | Một phần tử của view là gì? |
|---|---|---|
| `sequencedKeySet()` | `SequencedSet<K>` | Một khóa |
| `sequencedValues()` | `SequencedCollection<V>` | Một giá trị |
| `sequencedEntrySet()` | `SequencedSet<Map.Entry<K,V>>` | Một entry |

`SequencedSet` kết hợp tính chất không trùng phần tử của `Set` với một thứ tự duyệt xác định. `SequencedCollection` chỉ mô tả tập hợp có thứ tự, không tự đặt điều kiện duy nhất. Vì nhiều khóa khác nhau có thể cùng giá trị, view giá trị của map là collection, không phải set.

Các view cũ `keySet()`, `values()`, `entrySet()` vẫn duyệt theo thứ tự của `LinkedHashMap`. Việc có API mới không làm chúng mất thứ tự. Điểm khác là kiểu trả về của các phương thức mới thể hiện được khả năng thao tác đầu–cuối trong mã nguồn.

### 2.3. Kiểu khai báo quyết định phương thức có thể gọi

Đoạn sau là mã minh họa đặt bên trong một phương thức, không phải một tệp Java độc lập:

```java
LinkedHashMap<String, Integer> linked = new LinkedHashMap<>();
Map<String, Integer> general = linked;
SequencedMap<String, Integer> ordered = linked;

ordered.firstEntry();
linked.putFirst("A", 1);
// general.firstEntry(); // Không biên dịch: Map không khai báo phương thức này.
```

`general` và `ordered` có thể trỏ tới cùng đối tượng. Tuy nhiên, trình biên dịch xét phương thức theo kiểu khai báo của biến. Nếu một API cần dùng thứ tự và hai đầu, nhận hoặc trả `SequencedMap` sẽ thể hiện yêu cầu rõ hơn so với chỉ dùng `Map`.

Không nên ép kiểu một `Map` bất kỳ thành `SequencedMap` để làm cho mã biên dịch. Chẳng hạn, một đối tượng `HashMap` không tự trở thành map có thứ tự khi bị ép kiểu. Cần biết đúng loại đối tượng hoặc thiết kế chữ ký API phù hợp.

## 3. Xem hai đầu và lấy hai đầu ra là hai hành động khác nhau

### 3.1. firstEntry và lastEntry chỉ quan sát

`firstEntry()` trả entry đầu hiện tại; `lastEntry()` trả entry cuối hiện tại. Chúng không lấy entry ra khỏi map. Trong chế độ thứ tự truy cập, hai phép quan sát này cũng không đóng vai trò một lần `get` để đưa entry thành mới được dùng.

Với thứ tự A, B, C, gọi `firstEntry()` rồi `lastEntry()` vẫn để thứ tự A, B, C và vẫn có ba entry. Khi chỉ cần xem mục sắp xử lý mà chưa muốn xóa, đây là nhóm thao tác phù hợp.

### 3.2. pollFirstEntry và pollLastEntry vừa lấy vừa loại

`pollFirstEntry()` trả entry đầu rồi loại mapping tương ứng. `pollLastEntry()` làm tương tự ở cuối. Nếu chỉ in kết quả của một lời gọi `poll`, map đã thay đổi dù không có thêm câu lệnh `remove`.

Ví dụ, với thứ tự A, B, C:

```text
firstEntry()     → trả A, map vẫn [A, B, C]
pollFirstEntry() → trả A, map còn [B, C]
pollLastEntry()  → trả C, map còn [B]
```

Đây là các thao tác theo mapping: không chỉ bỏ một giá trị ra khỏi một danh sách rời rạc. Sau khi poll A, tra cứu khóa A trên map sẽ không còn mapping đó.

### 3.3. Map rỗng trả null cho cả bốn phương thức

Cả `firstEntry`, `lastEntry`, `pollFirstEntry`, `pollLastEntry` đều trả `null` khi map rỗng. Không được gọi ngay `map.firstEntry().getKey()` nếu chưa biết có entry. Có thể lưu kết quả vào biến, kiểm tra khác `null`, rồi mới đọc khóa và giá trị.

Việc map cho phép giá trị `null` không làm kết quả entry trở nên nhập nhằng theo cách của `get`. Một map chứa mapping `A → null` có `firstEntry()` khác `null`; chỉ `entry.getValue()` là `null`. Tương tự, nếu khóa cũng là `null`, entry vẫn tồn tại.

## 4. putFirst và putLast: vừa cập nhật vừa định vị

### 4.1. Khóa chưa có: thêm vào đầu hoặc cuối

`putFirst(k, v)` thêm mapping mới vào đầu thứ tự. `putLast(k, v)` thêm mapping mới vào cuối. Nếu không có mapping cũ cho khóa, giá trị trả về là `null`.

Không có chỉ số số nguyên nào được truyền vào. Đây không phải `List.add(index, element)` và cũng không có thao tác chèn trực tiếp vào vị trí thứ 5. Khi gọi `get(0)` trên một map có khóa số nguyên, số 0 vẫn là khóa cần tìm, không phải chỉ số của entry đầu.

### 4.2. Khóa đã có: giữ một mapping, đổi giá trị và chuyển vị trí

Nếu khóa đã tồn tại, `putFirst` hoặc `putLast` cập nhật giá trị của mapping đó và chuyển nó đến đầu/cuối nếu cần. Chúng không tạo một bản sao của khóa ở vị trí mới. Map vẫn tuân theo quy tắc một khóa tương ứng nhiều nhất một mapping.

Giá trị trả về là **giá trị cũ**, không phải entry mới, không phải vị trí và cũng không phải cờ cho biết có di chuyển hay không. Nếu giá trị cũ là `null`, kết quả `null` không đủ để phân biệt với trường hợp khóa trước đó chưa có. Khi cần biết sự tồn tại trước thao tác, hãy kiểm tra theo hợp đồng phù hợp trước khi cập nhật.

### 4.3. Khác với put thông thường trong chế độ thứ tự chèn

Ở một `LinkedHashMap` mặc định, `put` khóa đã có chỉ thay giá trị, giữ vị trí của khóa. `putFirst` và `putLast` thể hiện yêu cầu vị trí rõ ràng nên vẫn có thể thay đổi vị trí đó.

| Trạng thái ban đầu `[A, B, C]` | Thứ tự sau thao tác |
|---|---|
| `put("B", newValue)` trên map theo thứ tự chèn | `[A, B, C]` |
| `putFirst("B", newValue)` | `[B, A, C]` |
| `putLast("B", newValue)` | `[A, C, B]` |

Bảng trên xét ba lần thử độc lập, cùng điểm xuất phát. Không đọc nó thành ba thao tác nối tiếp nhau. Chế độ thứ tự truy cập sẽ được xét riêng vì `put` thông thường trong chế độ đó còn có ý nghĩa làm mới truy cập.

## 5. Chương trình 1: thêm, chuyển vị trí, xem và poll

Lưu chương trình đầy đủ vào `LinkedHashMapEndsDemo.java`. Map được tạo bằng constructor mặc định nên dùng thứ tự chèn. Ví dụ dùng tên khóa để dễ quan sát vị trí; không có bước sắp xếp theo tên.

```java
import java.util.LinkedHashMap;

public class LinkedHashMapEndsDemo {
    public static void main(String[] args) {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        map.put("beta", 2);
        map.put("delta", 4);

        System.out.println("putFirstNew=" + map.putFirst("alpha", 1));
        System.out.println("putLastNew=" + map.putLast("omega", 9));
        System.out.println("afterNew=" + map);

        System.out.println("putFirstExisting=" + map.putFirst("delta", 40));
        System.out.println("afterMoveFirst=" + map);
        System.out.println("putLastExisting=" + map.putLast("alpha", 10));
        System.out.println("afterMoveLast=" + map);

        map.put("beta", 20);
        System.out.println("afterPlainPut=" + map);
        System.out.println("first=" + map.firstEntry());
        System.out.println("last=" + map.lastEntry());
        System.out.println("beforePollSize=" + map.size());

        System.out.println("pollFirst=" + map.pollFirstEntry());
        System.out.println("pollLast=" + map.pollLastEntry());
        System.out.println("remaining=" + map);
        System.out.println("size=" + map.size());
    }
}
```

Biên dịch và chạy bằng JDK 21 trở lên:

```shell
javac --release 21 -encoding UTF-8 LinkedHashMapEndsDemo.java
java LinkedHashMapEndsDemo
```

Stdout:

```text
putFirstNew=null
putLastNew=null
afterNew={alpha=1, beta=2, delta=4, omega=9}
putFirstExisting=4
afterMoveFirst={delta=40, alpha=1, beta=2, omega=9}
putLastExisting=1
afterMoveLast={delta=40, beta=2, omega=9, alpha=10}
afterPlainPut={delta=40, beta=20, omega=9, alpha=10}
first=delta=40
last=alpha=10
beforePollSize=4
pollFirst=delta=40
pollLast=alpha=10
remaining={beta=20, omega=9}
size=2
```

### 5.1. Theo dõi thứ tự thay vì chỉ nhìn giá trị

Ban đầu có beta, delta. Thêm alpha ở đầu và omega ở cuối tạo thứ tự `[alpha, beta, delta, omega]`. Hai lần thêm khóa mới trả về `null`.

`putFirst("delta", 40)` trả giá trị cũ `4`, thay thành `40` và kéo delta lên đầu. Sau đó `putLast("alpha", 10)` trả `1`, thay thành `10` và đưa alpha xuống cuối. Số entry vẫn bốn vì đây là hai khóa đã có.

Lần `put("beta", 20)` chỉ cập nhật beta tại chỗ. Vì map này theo thứ tự chèn, kết quả vẫn có thứ tự `[delta, beta, omega, alpha]`.

`first=delta=40` và `last=alpha=10` là cách in một entry dưới dạng `khóa=giá trị`; các dòng đó không có nghĩa phương thức trả về một chuỗi có hai dấu bằng. Kiểu kết quả thật là `Map.Entry<String, Integer>`.

### 5.2. Poll để lấy và loại

Hai lời gọi xem đầu/cuối không đổi `beforePollSize=4`. Sau `pollFirstEntry`, delta bị loại. Sau `pollLastEntry`, alpha bị loại. Map còn beta và omega, có kích thước hai.

Quan sát này cũng giải thích vì sao không được dùng `poll` chỉ để xem “công việc tiếp theo là gì” nếu vẫn muốn giữ công việc đó. Chọn `firstEntry` hoặc `lastEntry` khi ý định chỉ là đọc.

## 6. Chương trình 2: entry trả về là snapshot, không phải cửa cập nhật map

### 6.1. Bản chụp của mapping tại thời điểm gọi

Bốn phương thức trả entry trực tiếp trên `SequencedMap` — `firstEntry`, `lastEntry`, `pollFirstEntry`, `pollLastEntry` — trả một **bản chụp (snapshot)** của mapping tại thời điểm gọi. Chúng không hỗ trợ `setValue` để thay giá trị trong map. Hợp đồng này được mô tả tại [SequencedMap — Java 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/SequencedMap.html).

Không nên suy ra rằng hễ một giá trị có kiểu `Map.Entry` thì đều sửa được map qua `setValue`. Cách lấy entry quyết định hợp đồng kết nối với map. Đặc biệt, entry snapshot khác với entry đang được cung cấp trong một lượt duyệt `entrySet()`.

Chương trình sau lưu một snapshot, cập nhật map, thử `setValue` trên snapshot và kiểm tra trường hợp khóa/giá trị `null`. Lưu vào `LinkedHashMapEntrySnapshotDemo.java`.

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class LinkedHashMapEntrySnapshotDemo {
    public static void main(String[] args) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        System.out.println("emptyFirst=" + map.firstEntry());
        System.out.println("emptyLast=" + map.lastEntry());
        System.out.println("emptyPollFirst=" + map.pollFirstEntry());
        System.out.println("emptyPollLast=" + map.pollLastEntry());

        map.put(null, null);
        Map.Entry<String, String> snapshot = map.firstEntry();
        System.out.println("snapshot=" + snapshot);
        System.out.println("entryExists=" + (snapshot != null));
        System.out.println("keyIsNull=" + (snapshot.getKey() == null));
        System.out.println("valueIsNull=" + (snapshot.getValue() == null));

        map.put(null, "updated");
        System.out.println("currentFirst=" + map.firstEntry());
        System.out.println("oldSnapshot=" + snapshot);
        try {
            snapshot.setValue("changed");
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println("setValue=UnsupportedOperationException");
        }

        Map.Entry<String, String> removed = map.pollFirstEntry();
        System.out.println("removed=" + removed);
        System.out.println("emptyAfterPoll=" + map.isEmpty());
        System.out.println("removedValue=" + removed.getValue());
    }
}
```

Biên dịch và chạy bằng JDK 21 trở lên:

```shell
javac --release 21 -encoding UTF-8 LinkedHashMapEntrySnapshotDemo.java
java LinkedHashMapEntrySnapshotDemo
```

Stdout:

```text
emptyFirst=null
emptyLast=null
emptyPollFirst=null
emptyPollLast=null
snapshot=null=null
entryExists=true
keyIsNull=true
valueIsNull=true
currentFirst=null=updated
oldSnapshot=null=null
setValue=UnsupportedOperationException
removed=null=updated
emptyAfterPoll=true
removedValue=updated
```

### 6.2. Đừng nhầm entry null với khóa hoặc giá trị null

Bốn dòng đầu đều in `null` vì map đang rỗng. Sau `map.put(null, null)`, `snapshot` là một entry có thật, được in là `null=null`. Vì thế:

- `snapshot != null` là `true`.
- `snapshot.getKey() == null` là `true`.
- `snapshot.getValue() == null` cũng là `true`.

Đây là ba phép kiểm tra khác nhau. Với API trả toàn entry, một mapping có giá trị `null` vẫn phân biệt được với việc không có mapping ở đầu map.

Sau khi cập nhật khóa `null` thành giá trị `updated`, entry hiện tại là `null=updated`, còn snapshot cũ vẫn là `null=null`. Nó không phải một biến tự đồng bộ với mọi lần thay giá trị của map.

### 6.3. setValue và tính độc lập của bản chụp

Lời gọi `snapshot.setValue("changed")` ném `UnsupportedOperationException`. Muốn cập nhật mapping cần gọi API của map theo ý nghĩa mong muốn: giữ vị trí theo chế độ của `put`, đặt lên đầu bằng `putFirst`, hoặc đặt xuống cuối bằng `putLast`.

Sau `pollFirstEntry`, map rỗng nhưng đối tượng `removed` vẫn cho đọc được `updated`. Đó là kết quả mô tả mapping vừa bị lấy ra, không phải một entry đang còn thuộc map.

Snapshot không đồng nghĩa với sao chép sâu mọi đối tượng. Nếu khóa hoặc giá trị là đối tượng có thể thay đổi, bản chụp vẫn có thể chứa tham chiếu đến chính đối tượng đó. Ví dụ ở đây dùng `String`, nên thao tác `map.put` thay tham chiếu giá trị bằng chuỗi khác; snapshot vẫn giữ tham chiếu cũ. Không dùng kết luận này để khẳng định một giá trị có thể thay đổi đã được nhân bản.

### 6.4. Không đánh đồng mọi cách lấy entry

Ba trường hợp cần tách biệt:

| Cách lấy entry | Điều cần biết |
|---|---|
| `map.firstEntry()` hoặc ba phương thức entry đầu–cuối còn lại | Snapshot, không hỗ trợ sửa map qua `setValue` |
| Entry trong khi duyệt `map.entrySet()` hoặc `sequencedEntrySet()` | Có kết nối với mapping theo hợp đồng duyệt; `LinkedHashMap` cho phép cập nhật giá trị qua `setValue` trong phạm vi phù hợp |
| `map.sequencedEntrySet().getFirst()` | Không lấy bảo đảm snapshot hay khả năng `setValue` của `firstEntry()` áp sang cách này; `SequencedMap` không quy định chung khả năng kết nối của entry lấy theo cách đó |

Trong bài, ví dụ view chỉ đọc và in entry lấy bằng `getFirst`; không dựa vào giả định rằng `setValue` của entry đó phải hoạt động giống một loại entry khác.

## 7. reversed(): đảo chiều nhìn, cùng một dữ liệu

### 7.1. Đầu của view là cuối của map gốc

Đặt tên `reverse = map.reversed()` chỉ tạo thêm một cách thao tác trên dữ liệu đang có. Với `LinkedHashMap`, các thay đổi được phép qua view được truyền vào map gốc; thay đổi map gốc cũng xuất hiện trên view. Đây là đặc tính view liên kết, không phải hành vi của một bản sao.

Mối quan hệ giữa các thao tác có vị trí là:

| Gọi trên `reverse` | Vị trí tương ứng trong `map` gốc |
|---|---|
| `reverse.firstEntry()` | Xem cuối map gốc |
| `reverse.lastEntry()` | Xem đầu map gốc |
| `reverse.pollFirstEntry()` | Lấy và loại cuối map gốc |
| `reverse.pollLastEntry()` | Lấy và loại đầu map gốc |
| `reverse.putFirst(k, v)` | Đặt mapping ở cuối map gốc |
| `reverse.putLast(k, v)` | Đặt mapping ở đầu map gốc |

Nếu khóa đã tồn tại thì phép đặt vẫn cập nhật giá trị và đổi vị trí tương ứng, không tạo một entry thứ hai. Từ “đầu” và “cuối” luôn phải gắn với chiều của đối tượng nhận lời gọi.

### 7.2. Tạo view không đảo lại cấu trúc gốc

Giả sử map gốc là `[A, B, C]`. Sau khi lấy view đảo, map gốc vẫn duyệt `[A, B, C]`; view duyệt `[C, B, A]`. Cả hai nhìn thấy cùng ba mapping. Không có giai đoạn map gốc bị thay thành `[C, B, A]` chỉ vì đã gọi `reversed()`.

Điều này phù hợp khi muốn hiển thị cùng một tập dữ liệu theo hai chiều hoặc lấy từ đầu/cuối tương ứng mà không xây một map chứa bản sao. Nhưng trả view cho nơi khác cũng có nghĩa là trao một đường thao tác liên quan đến map gốc. `reverse.clear()` trên một view sửa được sẽ xóa dữ liệu của map gốc.

### 7.3. put thông thường trên view không đồng nghĩa putLast của view

Đây là một điểm dễ suy luận sai: vì đang nhìn theo chiều đảo, người đọc có thể nghĩ mọi `reverse.put(k, v)` với khóa mới sẽ thêm vào cuối view. Với view của `LinkedHashMap`, `put` thông thường được thực hiện theo hành vi của map gốc. Trong map theo thứ tự chèn, khóa mới được thêm cuối **map gốc**, nên xuất hiện đầu **view đảo**.

Muốn chỉ rõ vị trí trong view, hãy dùng `reverse.putFirst` hoặc `reverse.putLast`. Không tự gán ý nghĩa “thêm cuối view” cho `put` chỉ vì một loại collection khác có cách thêm phần tử như vậy. Mã nguồn [LinkedHashMap trong OpenJDK 21](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.base/share/classes/java/util/LinkedHashMap.java) cho thấy view chuyển tiếp `put` đến map gốc, còn `putFirst` và `putLast` đổi đầu tương ứng.

Ở chế độ thứ tự chèn, cập nhật khóa đã có qua `reverse.put` vẫn không tự di chuyển khóa đó. Ở chế độ thứ tự truy cập, thao tác này còn tạo truy cập theo chính sách của map gốc. Phần sau sẽ xét trường hợp thứ tự truy cập bằng một chương trình riêng.

## 8. Chương trình 3: sửa hai chiều và tạo bản sao độc lập

Lưu chương trình đầy đủ vào `LinkedHashMapReversedDemo.java`. `reverse` là view; `copy` là một map mới được tạo từ thứ tự đang quan sát qua view.

```java
import java.util.LinkedHashMap;
import java.util.SequencedMap;

public class LinkedHashMapReversedDemo {
    public static void main(String[] args) {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        map.put("A", 1);
        map.put("B", 2);
        map.put("C", 3);
        SequencedMap<String, Integer> reverse = map.reversed();
        LinkedHashMap<String, Integer> copy = new LinkedHashMap<>(reverse);

        System.out.println("original=" + map);
        System.out.println("reverse=" + reverse);
        System.out.println("reverseFirst=" + reverse.firstEntry());
        System.out.println("reverseLast=" + reverse.lastEntry());

        reverse.putFirst("X", 24);
        System.out.println("afterReversePutFirst=" + map);
        reverse.putLast("Y", 25);
        System.out.println("afterReversePutLast=" + map);
        reverse.put("Z", 26);
        System.out.println("afterReversePlainPut=" + map);
        System.out.println("reverseNow=" + reverse);

        reverse.put("B", 20);
        reverse.remove("A");
        map.putLast("W", 23);
        System.out.println("sharedOriginal=" + map);
        System.out.println("sharedReverse=" + reverse);

        System.out.println("reversePollFirst=" + reverse.pollFirstEntry());
        System.out.println("reversePollLast=" + reverse.pollLastEntry());
        System.out.println("remainingOriginal=" + map);
        System.out.println("remainingReverse=" + reverse);
        System.out.println("twiceReversedOrder=" + reverse.reversed().keySet());
        System.out.println("equalMappings=" + map.equals(reverse));
        System.out.println("independentCopy=" + copy);
    }
}
```

Biên dịch và chạy bằng JDK 21 trở lên:

```shell
javac --release 21 -encoding UTF-8 LinkedHashMapReversedDemo.java
java LinkedHashMapReversedDemo
```

Stdout:

```text
original={A=1, B=2, C=3}
reverse={C=3, B=2, A=1}
reverseFirst=C=3
reverseLast=A=1
afterReversePutFirst={A=1, B=2, C=3, X=24}
afterReversePutLast={Y=25, A=1, B=2, C=3, X=24}
afterReversePlainPut={Y=25, A=1, B=2, C=3, X=24, Z=26}
reverseNow={Z=26, X=24, C=3, B=2, A=1, Y=25}
sharedOriginal={Y=25, B=20, C=3, X=24, Z=26, W=23}
sharedReverse={W=23, Z=26, X=24, C=3, B=20, Y=25}
reversePollFirst=W=23
reversePollLast=Y=25
remainingOriginal={B=20, C=3, X=24, Z=26}
remainingReverse={Z=26, X=24, C=3, B=20}
twiceReversedOrder=[B, C, X, Z]
equalMappings=true
independentCopy={C=3, B=2, A=1}
```

### 8.1. Ba đối tượng, hai kiểu quan hệ dữ liệu

Ngay sau khi tạo:

```text
map:     {A=1, B=2, C=3}
reverse: {C=3, B=2, A=1}   ← nhìn cùng dữ liệu theo chiều ngược
copy:    {C=3, B=2, A=1}   ← map mới, lấy thứ tự đó lúc tạo
```

`reverse` và `copy` có thể được in giống nhau ở thời điểm đầu, nhưng hành vi sau đó khác nhau. Cách tạo mới quyết định tính độc lập, không phải hình thức dòng in.

`new LinkedHashMap<>(reverse)` duyệt các mapping của view đảo và đưa chúng vào một map theo thứ tự chèn mới. Các thay đổi cấu trúc hay thay tham chiếu giá trị trong map gốc sau đó không tự cập nhật map mới. Đây vẫn là bản sao nông: các đối tượng khóa/giá trị có thể được dùng chung nếu chúng có thể thay đổi.

### 8.2. Đặt đầu và cuối qua view

Ba thao tác đầu cho các kết quả đáng chú ý:

| Thao tác | Thứ tự map gốc sau thao tác | Điều đã xảy ra |
|---|---|---|
| `reverse.putFirst("X", 24)` | `[A, B, C, X]` | X đứng đầu view, tức cuối gốc |
| `reverse.putLast("Y", 25)` | `[Y, A, B, C, X]` | Y đứng cuối view, tức đầu gốc |
| `reverse.put("Z", 26)` | `[Y, A, B, C, X, Z]` | `put` thông thường thêm cuối gốc |

Vì vậy `reverseNow` in thứ tự `[Z, X, C, B, A, Y]`. Không cần đảo thủ công một danh sách khóa sau mỗi thay đổi; view lấy thứ tự ngược từ cùng dữ liệu đang được cập nhật.

### 8.3. Cập nhật giá trị và loại entry đều truyền qua

`reverse.put("B", 20)` đổi mapping B của map gốc thành B=20. `reverse.remove("A")` loại mapping A của map gốc. `map.putLast("W", 23)` thêm W vào cuối gốc và lập tức làm W đứng đầu view.

`reverse.pollFirstEntry()` sau đó lấy W ra, vì W đang đầu view. `reverse.pollLastEntry()` lấy Y ra, vì Y đang cuối view. Map gốc còn `[B, C, X, Z]`, view còn `[Z, X, C, B]`.

Đây là cùng một tập mapping được quan sát hai chiều, không phải hai map cần đồng bộ bằng mã riêng. Nếu chương trình có nhiều biến giữ các view, phải nhớ rằng một lần loại mapping ở bất cứ đường nào cũng tác động đến các đường nhìn còn lại.

### 8.4. Đảo hai lần và phép bằng nhau

`reverse.reversed()` cho thứ tự trở lại như map gốc. Ví dụ kiểm tra thứ tự `[B, C, X, Z]`, không dùng so sánh danh tính đối tượng bằng `==` để định nghĩa ý nghĩa đảo hai lần cho mọi cách triển khai `SequencedMap`.

`map.equals(reverse)` in `true` dù thứ tự duyệt ngược nhau. Hợp đồng bằng nhau của map kiểm tra các mapping, không kiểm tra thứ tự duyệt. Nếu cần kiểm tra thứ tự, hãy lấy một danh sách khóa theo thứ tự hiện tại để so sánh; không dùng `Map.equals` như bằng chứng hai chiều duyệt giống nhau.

`independentCopy={C=3, B=2, A=1}` vẫn giữ dữ liệu và thứ tự tại thời điểm bản sao được tạo. B ở bản sao vẫn là 2, A vẫn có, còn X/Y/Z/W chưa từng được đưa vào bản sao. Điều này phân biệt rất rõ **view theo dõi hiện tại** với **bản sao tại một thời điểm**.

## 9. Các sequenced view của khóa, giá trị và entry

### 9.1. Đầu–cuối của từng view cùng bám theo thứ tự map

Với map `{A=7, B=7, C=9}`:

| View | Thứ tự phần tử | Phần tử đầu | Phần tử cuối |
|---|---|---|---|
| `sequencedKeySet()` | `[A, B, C]` | A | C |
| `sequencedValues()` | `[7, 7, 9]` | 7 ứng với A | 9 ứng với C |
| `sequencedEntrySet()` | `[A=7, B=7, C=9]` | A=7 | C=9 |

Mỗi view hỗ trợ `getFirst`, `getLast` để xem phần tử theo thứ tự của view. Chúng không phải tên khác của `Map.firstEntry`: kiểu phần tử trả về phụ thuộc view. `keys.getFirst()` trả khóa, `values.getFirst()` trả giá trị, còn `entries.getFirst()` trả entry.

Gọi `reversed()` trên một view đảo thứ tự riêng cách nhìn đó. Chẳng hạn, `map.sequencedKeySet().reversed()` cho thứ tự khóa giống `map.reversed().sequencedKeySet()`. Cả hai vẫn liên quan đến cùng map; không nên coi một đường gọi dài hơn là đã tạo được bản sao độc lập.

### 9.2. Xóa từ view là xóa mapping trong map

Các view này của `LinkedHashMap` hỗ trợ loại phần tử. `keys.removeFirst()` loại khóa đầu và mapping của nó; `values.removeLast()` loại giá trị cuối cùng với mapping đang giữ giá trị đó; `entries.removeFirst()` loại entry đầu.

Giá trị có thể trùng nhau nên vị trí rất quan trọng. Với A=7, B=7, C=9, `values.removeFirst()` trả 7 và loại mapping A=7, không loại tất cả mapping có giá trị 7. B=7 vẫn có. Sau đó các view khóa và entry cũng tự phản ánh việc A đã rời map.

### 9.3. Không hỗ trợ thêm qua view không có nghĩa map chỉ đọc

Map sửa được không đồng nghĩa mọi thao tác thêm của mọi collection view đều được hỗ trợ. Trên các sequenced view của `LinkedHashMap`, việc thêm phần tử bằng `addFirst`, `addLast` không được hỗ trợ và ném `UnsupportedOperationException`.

Với view khóa hoặc giá trị, thêm một phần đơn lẻ còn không mô tả đầy đủ cặp khóa–giá trị cần tạo. Ngay cả view entry đã có cả hai thành phần, API view này cũng không cung cấp thao tác thêm đó. Muốn thêm hoặc định vị mapping, dùng `putFirst`/`putLast` trên map.

Vì vậy, không đọc tên `SequencedSet` hoặc `SequencedCollection` như lời hứa rằng tất cả phương thức thay đổi của interface đều thực hiện được trên mọi view. Khả năng đọc, xóa và thêm cần được kiểm tra riêng.

### 9.4. View rỗng ném ngoại lệ ở get/remove hai đầu

Đối với các collection view sửa được trong bài:

| Tình huống rỗng | Kết quả |
|---|---|
| `map.firstEntry()` hoặc `map.lastEntry()` | `null` |
| `map.pollFirstEntry()` hoặc `map.pollLastEntry()` | `null` |
| `keys.getFirst()` hoặc `keys.getLast()` | `NoSuchElementException` |
| `keys.removeFirst()` hoặc `keys.removeLast()` | `NoSuchElementException` |
| `values.getFirst/getLast/removeFirst/removeLast` | Cùng quy tắc ngoại lệ khi rỗng |

Quy tắc của collection được mô tả tại [SequencedCollection — Java 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/SequencedCollection.html). Đừng dùng `keys.getFirst() == null` làm cách kiểm tra view rỗng: nếu view rỗng, lời gọi đã ném ngoại lệ; nếu khóa đầu hợp lệ là `null`, kết quả có thể là `null` dù map không rỗng.

## 10. Chương trình 4: khả năng thay đổi và lỗi rỗng của view

Lưu vào `LinkedHashMapSequencedViewsDemo.java`. Các ngoại lệ trong ví dụ được bắt có chủ đích và in tên để đối chiếu hợp đồng, không phải bỏ qua một lỗi bất ngờ.

```java
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SequencedCollection;
import java.util.SequencedSet;

public class LinkedHashMapSequencedViewsDemo {
    private static void unsupported(String label, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    private static void missing(String label, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
            System.out.println(label + "=NoSuchElementException");
        }
    }

    public static void main(String[] args) {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        map.put("A", 7);
        map.put("B", 7);
        map.put("C", 9);

        SequencedSet<String> keys = map.sequencedKeySet();
        SequencedCollection<Integer> values = map.sequencedValues();
        SequencedSet<Map.Entry<String, Integer>> entries =
                map.sequencedEntrySet();

        System.out.println("keyEnds=" + keys.getFirst() + "," + keys.getLast());
        System.out.println("valueEnds=" + values.getFirst() + "," + values.getLast());
        System.out.println("firstEntryFromView=" + entries.getFirst());
        System.out.println("reversedKeys=" + keys.reversed());
        System.out.println("reversedValues=" + values.reversed());

        unsupported("keyAddFirst", () -> keys.addFirst("X"));
        unsupported("valueAddLast", () -> values.addLast(99));
        unsupported("entryAddFirst",
                () -> entries.addFirst(Map.entry("X", 99)));

        System.out.println("removedFirstValue=" + values.removeFirst());
        System.out.println("afterValueRemoval=" + map);
        System.out.println("removedReverseFirstKey="
                + keys.reversed().removeFirst());
        System.out.println("afterReverseKeyRemoval=" + map);
        System.out.println("removedLastEntry=" + entries.removeLast());
        System.out.println("empty=" + map.isEmpty());

        missing("emptyKeyGetFirst", () -> keys.getFirst());
        missing("emptyKeyRemoveLast", () -> keys.removeLast());
        missing("emptyValueGetLast", () -> values.getLast());
        System.out.println("emptyMapFirst=" + map.firstEntry());

        map.put("D", 4);
        System.out.println("sameKeyViewAfterPut=" + keys);
        System.out.println("sameValueViewAfterPut=" + values);
    }
}
```

Biên dịch và chạy bằng JDK 21 trở lên:

```shell
javac --release 21 -encoding UTF-8 LinkedHashMapSequencedViewsDemo.java
java LinkedHashMapSequencedViewsDemo
```

Stdout:

```text
keyEnds=A,C
valueEnds=7,9
firstEntryFromView=A=7
reversedKeys=[C, B, A]
reversedValues=[9, 7, 7]
keyAddFirst=UnsupportedOperationException
valueAddLast=UnsupportedOperationException
entryAddFirst=UnsupportedOperationException
removedFirstValue=7
afterValueRemoval={B=7, C=9}
removedReverseFirstKey=C
afterReverseKeyRemoval={B=7}
removedLastEntry=B=7
empty=true
emptyKeyGetFirst=NoSuchElementException
emptyKeyRemoveLast=NoSuchElementException
emptyValueGetLast=NoSuchElementException
emptyMapFirst=null
sameKeyViewAfterPut=[D]
sameValueViewAfterPut=[4]
```

### 10.1. Xóa theo đúng vị trí của view

Ba lần xóa chạy nối tiếp:

1. `values.removeFirst()` trả 7 và loại A, nên map còn `{B=7, C=9}`.
2. `keys.reversed().removeFirst()` trả C vì C đang đầu view khóa đảo, nên map còn `{B=7}`.
3. `entries.removeLast()` trả B=7 và loại mapping cuối cùng, nên map rỗng.

Một giá trị trùng nhau vẫn gắn với những mapping riêng. Việc xóa giá trị đầu không có nghĩa xóa toàn bộ dữ liệu cùng giá trị. Việc xóa đầu view đảo không có nghĩa xóa đầu map gốc.

### 10.2. View vẫn sống sau khi map từng rỗng

Sau ba lần xóa, các biến `keys`, `values`, `entries` không biến thành những collection vĩnh viễn rỗng. Chúng vẫn là view của map. Khi thêm D=4 vào map gốc, chính các biến view cũ in `[D]` và `[4]`.

Đây là lý do không nên lưu một view rồi gọi nó là “dữ liệu tại thời điểm tạo”. Nếu cần một danh sách khóa độc lập cho một phép tính sau đó, hãy tạo một collection mới từ view theo đúng chiều mong muốn. Nếu cần cả mapping và thứ tự riêng, dùng map mới như `copy` trong ví dụ trước.

Các lệnh thêm bị từ chối không thay đổi map. Các lệnh đọc/xóa trên view rỗng báo `NoSuchElementException`; trong khi `emptyMapFirst=null` xác nhận API trả entry của map có quy tắc rỗng khác.

## 11. Kết hợp với chế độ thứ tự truy cập

### 11.1. Chiều của view và chính sách truy cập là hai vấn đề riêng

`LinkedHashMap` mặc định dùng thứ tự chèn. Constructor ba đối số với `true` dùng thứ tự truy cập:

```java
// Minh họa cấu hình bên trong một phương thức.
LinkedHashMap<String, Integer> map =
        new LinkedHashMap<>(16, 0.75f, true);
```

Trong map gốc theo thứ tự truy cập, một `get` tìm thấy khóa đưa entry đó về cuối. View đảo nhìn cùng sự thay đổi theo chiều ngược, nên entry được truy cập xuất hiện ở **đầu view đảo**.

Không được suy luận rằng gọi `reverse.get(key)` sẽ đổi chính sách để đưa entry về cuối view. Lời gọi vẫn tạo truy cập trên map gốc. Độ mới của entry thuộc cùng một map, không có một lịch sử truy cập độc lập chỉ dành cho view.

Ngược lại, các thao tác đặt vị trí rõ ràng như `putFirst`/`putLast` thực hiện vị trí được yêu cầu; chúng không đồng thời tạo thêm một lần truy cập để kéo entry về cuối map gốc. Nếu `putFirst` luôn lập tức bị một lần làm mới truy cập kéo xuống cuối, nó đã không thể thực hiện ý nghĩa đặt đầu của mình.

`firstEntry` và `lastEntry` chỉ xem hai đầu, không làm mới vị trí. Các phép quan sát qua collection view cũng không tự biến thành truy cập `get`. Việc đọc tên phương thức cụ thể quan trọng hơn việc gọi chung mọi thao tác này là “đọc dữ liệu”.

### 11.2. Chương trình 5: đối chiếu từng lần đổi vị trí

Lưu vào `LinkedHashMapAccessOrderEndsDemo.java`. Chương trình in song song thứ tự gốc và đảo sau mỗi bước để tránh nhầm phía đầu/cuối.

```java
import java.util.LinkedHashMap;
import java.util.SequencedMap;

public class LinkedHashMapAccessOrderEndsDemo {
    private static void show(String label,
            LinkedHashMap<String, Integer> map,
            SequencedMap<String, Integer> reverse) {
        System.out.println(label + ": original=" + map.keySet()
                + ", reverse=" + reverse.keySet());
    }

    public static void main(String[] args) {
        LinkedHashMap<String, Integer> map =
                new LinkedHashMap<>(16, 0.75f, true);
        map.put("A", 1);
        map.put("B", 2);
        map.put("C", 3);
        SequencedMap<String, Integer> reverse = map.reversed();

        System.out.println("first=" + map.firstEntry());
        System.out.println("last=" + map.lastEntry());
        show("afterInspect", map, reverse);

        System.out.println("reverseGetA=" + reverse.get("A"));
        show("afterReverseGet", map, reverse);
        System.out.println("reverseContainsB=" + reverse.containsKey("B"));
        show("afterContains", map, reverse);

        map.putFirst("C", 30);
        show("afterExplicitFirst", map, reverse);
        System.out.println("getC=" + map.get("C"));
        show("afterGetC", map, reverse);

        reverse.putLast("A", 10);
        show("afterReverseExplicitLast", map, reverse);
        System.out.println("reverseGetB=" + reverse.get("B"));
        show("afterReverseGetB", map, reverse);
    }
}
```

Biên dịch và chạy bằng JDK 21 trở lên:

```shell
javac --release 21 -encoding UTF-8 LinkedHashMapAccessOrderEndsDemo.java
java LinkedHashMapAccessOrderEndsDemo
```

Stdout:

```text
first=A=1
last=C=3
afterInspect: original=[A, B, C], reverse=[C, B, A]
reverseGetA=1
afterReverseGet: original=[B, C, A], reverse=[A, C, B]
reverseContainsB=true
afterContains: original=[B, C, A], reverse=[A, C, B]
afterExplicitFirst: original=[C, B, A], reverse=[A, B, C]
getC=30
afterGetC: original=[B, A, C], reverse=[C, A, B]
afterReverseExplicitLast: original=[A, B, C], reverse=[C, B, A]
reverseGetB=2
afterReverseGetB: original=[A, C, B], reverse=[B, C, A]
```

### 11.3. Giải thích kết quả

Hai phép xem ban đầu trả A=1 và C=3 nhưng `afterInspect` vẫn là `[A, B, C]`. Chúng không làm mới A hay C.

`reverse.get("A")` tìm thấy A. Map gốc thành `[B, C, A]`; view thành `[A, C, B]`. A mới được truy cập nằm cuối gốc và đầu đảo. `reverse.containsKey("B")` trả `true` nhưng không đổi thứ tự, nên B vẫn đầu gốc.

`map.putFirst("C", 30)` đặt C lên đầu gốc và cập nhật giá trị thành 30. Kết quả là `[C, B, A]`, không phải một kết quả có C ở cuối do bị làm mới truy cập ngầm. Ngay sau đó, `map.get("C")` mới là một lần truy cập thực sự và chuyển C xuống cuối, tạo `[B, A, C]`.

`reverse.putLast("A", 10)` đặt A ở cuối view, tương ứng đầu map gốc. Gốc thành `[A, B, C]`, đảo thành `[C, B, A]`. Lần `reverse.get("B")` cuối cùng lại tạo truy cập, đưa B xuống cuối gốc `[A, C, B]` và lên đầu view `[B, C, A]`.

Bảng sau ghi lại sự khác nhau ở cùng một map theo thứ tự truy cập:

| Loại thao tác | Có chủ đích làm mới truy cập không? | Cách hiểu vị trí |
|---|---|---|
| `get` tìm thấy khóa, kể cả qua view đảo | Có | Cuối gốc, đầu view đảo |
| `containsKey` | Không | Không tự đổi vị trí |
| `firstEntry`/`lastEntry` | Không | Chỉ quan sát hai đầu hiện tại |
| `putFirst`/`putLast` | Đặt vị trí rõ ràng | Theo đầu/cuối của đối tượng nhận lời gọi |
| Duyệt các view để đọc phần tử | Không tự tạo truy cập | Theo chiều của view đang duyệt |

### 11.4. Hệ quả khi map đang được dùng làm LRU

Trong một map theo thứ tự truy cập chỉ chịu các thao tác truy cập thông thường, đầu gốc thể hiện entry ít được dùng gần đây nhất. Nhưng `putFirst` cho phép chương trình chủ động chuyển một entry lên đầu. Sau thao tác đặt vị trí đó, không nên còn diễn giải thứ tự như một lịch sử chỉ được quyết định bởi các lần sử dụng.

Nếu một lớp cache muốn giữ chính sách LRU tự động nhất quán, nó cần quyết định có cho bên ngoài gọi các thao tác định vị hay không. Việc có API mới không có nghĩa nên để mọi người gọi tùy ý mà vẫn tuyên bố cache chỉ dựa trên lịch sử truy cập.

Ở chiều đảo, `pollFirstEntry()` lấy entry cuối gốc. Trong thứ tự truy cập thông thường, đó là phía mới nhất, không phải entry cũ nhất. Nếu muốn lấy entry cũ nhất từ view đảo, vị trí tương ứng là cuối view. Trước khi viết logic loại bỏ dựa vào đầu/cuối, phải xác định rõ đang cầm map gốc hay view đảo và liệu thứ tự đã được định vị thủ công hay chưa.

## 12. Chọn thao tác theo ý định sử dụng

### 12.1. Khi cần xem, lấy ra, định vị hoặc sao chép

| Ý định | Cách thể hiện phù hợp trên LinkedHashMap |
|---|---|
| Xem mapping đầu nhưng vẫn giữ nó | `firstEntry()` |
| Lấy mapping cuối khỏi map | `pollLastEntry()` |
| Đưa một khóa lên đầu, đồng thời đặt giá trị mong muốn | `putFirst(key, value)` |
| Đưa một khóa xuống cuối | `putLast(key, value)` |
| Duyệt cùng dữ liệu theo chiều ngược | `reversed()` |
| Lấy khóa đầu và chấp nhận lỗi khi không có phần tử | `sequencedKeySet().getFirst()` |
| Xóa mapping cuối bằng view khóa | `sequencedKeySet().removeLast()` |
| Giữ một map riêng theo thứ tự đảo tại thời điểm tạo | `new LinkedHashMap<>(map.reversed())` |

Đây là lựa chọn theo ngữ nghĩa, không phải các cách viết hoàn toàn thay thế cho nhau. Một view không phải một bản sao; poll không phải một phép xem; snapshot entry không phải một cửa sửa trực tiếp mapping.

### 12.2. Lấy dần các mapping theo một chiều

Nếu cần lấy hết các mapping theo thứ tự hiện tại để xử lý trong một luồng, có thể dùng poll thay vì tự lấy iterator rồi xóa bên ngoài iterator. Đoạn dưới minh họa bên trong một phương thức đã có `map`; nó không phải chương trình độc lập:

```java
Map.Entry<String, Integer> next;
while ((next = map.pollFirstEntry()) != null) {
    System.out.println(next.getKey() + ":" + next.getValue());
}
```

Vòng lặp này **làm rỗng map** nếu chạy hết bình thường. Nó kiểm tra đối tượng entry có tồn tại, không dùng giá trị của entry để quyết định dừng, nên một mapping có giá trị `null` không tự làm vòng lặp kết thúc sớm.

Nếu chỉ muốn duyệt mà giữ dữ liệu, hãy dùng `forEach` hoặc duyệt `entrySet()` theo chiều mong muốn. Nếu muốn lấy từ chiều ngược và loại dần, có thể dùng `pollLastEntry()` trên gốc hoặc `pollFirstEntry()` trên view đảo; cả hai đều làm thay đổi cùng map.

Việc poll trước rồi mới xử lý cũng đặt ra một quyết định ở cấp ứng dụng: nếu xử lý thất bại, mapping đã rời map. Không suy ra đây là một hàng đợi công việc có giao dịch, cơ chế thử lại hoặc lưu bền chỉ vì có API lấy ở hai đầu.

### 12.3. Giữ dữ liệu riêng cần tạo cấu trúc mới

`reversed()` tự nó không tạo quyền chỉ đọc và cũng không cách ly thay đổi. Nếu trả view cho mã gọi bên ngoài, hãy xem đó như một đường liên quan đến map gốc. Khi yêu cầu là một ảnh dữ liệu độc lập về cấu trúc, tạo collection/map mới theo thứ tự cần giữ.

Bản sao mới vẫn có thể sửa được. Tính độc lập về cấu trúc khác với tính bất biến của đối tượng, và sao chép nông khác với sao chép sâu. Trong ví dụ dùng `String`/`Integer`, các giá trị không thể bị sửa tại chỗ; với đối tượng nghiệp vụ có thể thay đổi, hợp đồng chia sẻ tham chiếu cần được xác định riêng.

Không dùng `Map.copyOf` chỉ dựa vào mong muốn “giữ chính xác chiều đảo”: thứ tự duyệt của map do phương thức này tạo không được quy định, theo [Map — Java 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html#unmodifiable). Việc cần thứ tự cụ thể phải được thể hiện bằng một kiểu/cách tạo có hợp đồng thứ tự phù hợp. Trong phạm vi bài này, constructor `LinkedHashMap` từ view đảo đã cho thấy rõ thứ tự được giữ khi tạo bản sao.

## 13. Các điểm cần kiểm tra khi đưa vào mã thực tế

### 13.1. Duyệt và đổi vị trí

Di chuyển entry bằng `putFirst`/`putLast`, thêm hoặc xóa entry có thể làm thay đổi cấu trúc thứ tự mà iterator đang dựa vào. Đổi qua view đảo vẫn là đổi cùng map; không có một iterator độc lập được bảo vệ chỉ vì nó được tạo từ chiều khác.

Đặc biệt ở chế độ thứ tự truy cập, gọi `get` trong khi đang duyệt chính map cũng có thể đổi thứ tự. Muốn chỉ đọc khóa và giá trị trong một lượt duyệt, lấy từ entry đang duyệt thay vì lấy khóa rồi lại gọi `get` để tra lại.

Iterator fail-fast có thể báo `ConcurrentModificationException` khi phát hiện thay đổi không phù hợp, kể cả trong một luồng. Cơ chế đó hoạt động theo nỗ lực phát hiện lỗi, không phải bảo đảm để chương trình dựa vào làm cơ chế phối hợp. Đừng viết logic đúng/sai dựa trên giả định mọi thay đổi đều chắc chắn gây ngoại lệ.

Nếu cần thay đổi nhiều vị trí theo một danh sách quyết định, có thể thu thập khóa vào một collection riêng trước rồi thực hiện các thay đổi sau lượt duyệt. Cần xác định trước rằng những thay đổi đó có chủ đích làm đổi thứ tự; có bản sao danh sách khóa chỉ tách cấu trúc duyệt khỏi map, không vô hiệu hóa hiệu ứng của các lệnh gọi sau đó.

### 13.2. Nhiều luồng không được bảo vệ bởi view

`LinkedHashMap` không tự đồng bộ. View đảo cũng không biến nó thành map an toàn cho truy cập đồng thời. Map gốc, view đảo và các collection view cùng nhìn vào trạng thái liên quan, nên khi cần phối hợp nhiều luồng phải xét mọi đường truy cập đó.

`firstEntry()` rồi `pollFirstEntry()` là hai lời gọi riêng. Nếu trạng thái có thể thay đổi xen giữa, không được giả định entry đã xem chắc chắn chính là entry sẽ lấy ra. Muốn một bước “lấy ra” trong mô hình một luồng của bài, dùng trực tiếp poll và xử lý kết quả của chính lời gọi đó; yêu cầu nguyên tử giữa nhiều luồng cần thiết kế khác.

Ở map theo thứ tự truy cập, một lần `get` có thể đổi liên kết thứ tự, nên không thể coi mọi lệnh đọc là không thay đổi trạng thái. Các ví dụ được chạy tuần tự trong một luồng và không đưa ra bảo đảm đồng thời.

### 13.3. Phiên bản biên dịch và phiên bản chạy

Để dùng các API trong bài, cần thư viện Java 21 trở lên ở lúc biên dịch và môi trường chạy tương ứng. Cài JDK mới nhưng cấu hình dự án vẫn `--release 17` thì API `SequencedMap` và các thao tác mới của `LinkedHashMap` không nằm trong tập API được phép dùng.

Ngược lại, không thể biên dịch ví dụ cho Java 21 rồi chạy lớp đã tạo trên một JVM Java 17. Cần kiểm tra cả cấu hình JDK của IDE, trình biên dịch và môi trường mà lệnh `java` thực sự sử dụng. Đây là thay đổi API theo phiên bản, không phải lỗi cú pháp có thể sửa bằng cách thêm import.

Các chương trình dùng constructor `LinkedHashMap` thông thường, không dùng tính năng ngôn ngữ preview hay thư viện ngoài. `--release 21` trong lệnh biên dịch nêu rõ mức API/bytecode cần cho bài.

## 14. Lỗi thường gặp

| Nhầm lẫn | Hệ quả | Cách sửa |
|---|---|---|
| Đầu/cuối là khóa nhỏ nhất/lớn nhất | Đọc sai entry khi map không sắp theo khóa | Theo dõi đúng thứ tự duyệt |
| Gọi poll chỉ để xem | Mapping biến mất ngoài ý định | Dùng `firstEntry`/`lastEntry` để quan sát |
| Gọi `.getKey()` ngay trên `firstEntry()` | Có thể `NullPointerException` khi map rỗng | Kiểm tra entry khác `null` |
| `putFirst` khóa cũ tạo bản sao khóa | Dự đoán sai kích thước | Hiểu là cập nhật và định vị cùng mapping |
| `put` khóa cũ luôn chuyển cuối | Sai trên map theo thứ tự chèn | Phân biệt chế độ và thao tác định vị |
| `firstEntry().setValue(...)` cập nhật map | `UnsupportedOperationException` | Cập nhật qua API map phù hợp |
| `reversed()` đảo map gốc tại chỗ | Cho rằng thứ tự của mọi biến đều bị đảo | Tách chiều nhìn khỏi dữ liệu gốc |
| `reversed()` tạo một map độc lập | Sửa view làm đổi map gốc | Tạo cấu trúc mới khi cần độc lập |
| `reverse.put(...)` thêm cuối view | Sai vị trí với LinkedHashMap | Dùng thao tác vị trí rõ ràng khi đó là yêu cầu |
| Xóa đầu view đảo tức xóa đầu gốc | Loại nhầm entry | Đầu đảo tương ứng cuối gốc |
| View sửa được thì `addFirst` cũng chạy được | `UnsupportedOperationException` | Thêm cả mapping qua map |
| `getFirst` của view rỗng trả `null` | Bỏ sót `NoSuchElementException` | Theo đúng hợp đồng rỗng của từng API |
| Map bằng view đảo thì thứ tự giống nhau | Kiểm tra thứ tự không phát hiện lỗi | So sánh danh sách theo thứ tự mong muốn |

## 15. Tự kiểm tra và thực hành

### 15.1. Câu hỏi

1. Với thứ tự gốc `[A, B, C]`, `map.reversed().firstEntry()` nhìn thấy entry nào?
2. `firstEntry` và `pollFirstEntry` khác nhau ở tác động nào?
3. Vì sao entry `A=null` khác với kết quả entry `null`?
4. `putFirst("B", value)` trên map đã có A, B, C làm kích thước tăng không?
5. Sau `reverse.putLast("D", 4)` với D là khóa mới, D nằm đầu hay cuối map gốc?
6. Sửa giá trị bằng `setValue` trên entry lấy từ `firstEntry` có được không?
7. `map.sequencedKeySet().removeLast()` có chỉ sửa một tập khóa rời khỏi map không?
8. Vì sao `sequencedValues()` không trả `SequencedSet`?
9. Một `reverse.get("A")` thành công trên map theo thứ tự truy cập làm A nằm phía nào của gốc và của view?
10. Vì sao `map.equals(map.reversed())` có thể đúng dù chiều duyệt khác nhau?

### 15.2. Đáp án ngắn

1. C; đầu view đảo chính là cuối gốc.
2. `firstEntry` chỉ xem; `pollFirstEntry` lấy entry và loại mapping. Cả hai trả `null` nếu map rỗng.
3. `A=null` là một entry có thật chứa giá trị `null`; entry trả về `null` nghĩa là không có entry ở đầu vì map rỗng.
4. Không. Nó cập nhật và chuyển mapping B lên đầu, thứ tự thành `[B, A, C]`.
5. Đầu map gốc, vì cuối view tương ứng đầu gốc.
6. Không; snapshot đầu–cuối trong bài không hỗ trợ `setValue`. Hãy dùng thao tác cập nhật của map.
7. Không. View liên kết nên mapping của khóa cuối bị loại khỏi map gốc.
8. Vì nhiều khóa khác nhau được phép cùng ánh xạ đến một giá trị; view giá trị có thể chứa phần tử trùng.
9. Cuối gốc và đầu view đảo. Lời gọi tạo truy cập trên cùng map gốc.
10. Phép bằng nhau của map kiểm tra tập mapping, không kiểm tra thứ tự duyệt.

### 15.3. Bài thực hành 1: chuyển vị trí không tạo trùng khóa

Tạo map theo thứ tự chèn A=1, B=2, C=3. Chạy nối tiếp `putFirst("B", 20)`, `putLast("A", 10)`. Trước khi chạy, dự đoán giá trị cũ của mỗi lời gọi, thứ tự cuối và kích thước.

Kết quả đối chiếu: hai giá trị trả về lần lượt là 2 và 1; map cuối là `{B=20, C=3, A=10}`, kích thước ba. Nếu có bốn hoặc năm entry trong dự đoán, đã nhầm map với danh sách có thể giữ nhiều bản của cùng khóa.

### 15.4. Bài thực hành 2: xác định đúng chiều trước khi xóa

Từ map gốc A=1, B=2, C=3, tạo `reverse`. Chạy `reverse.putFirst("D", 4)` rồi `reverse.pollLastEntry()`. Ghi lại đồng thời thứ tự gốc và đảo sau mỗi bước.

Kết quả đối chiếu: sau đặt D, gốc `[A, B, C, D]`, đảo `[D, C, B, A]`. Poll cuối view lấy A=1, gốc còn `[B, C, D]`, đảo còn `[D, C, B]`. Nếu kết quả lấy D thì đã dùng nhầm chiều đầu/cuối.

### 15.5. Bài thực hành 3: chứng minh view và bản sao khác nhau

Tạo view đảo và một `new LinkedHashMap<>(view)` từ cùng map ba entry. Sau đó đổi giá trị của B và thêm một khóa mới qua map gốc. In cả gốc, view và bản sao.

Tiêu chí đối chiếu: view nhìn thấy giá trị B mới và khóa mới theo chiều đảo hiện tại; bản sao giữ các mapping và thứ tự tại lúc tạo. Sau đó xóa một mapping qua view và xác nhận nó biến mất khỏi gốc nhưng không tự biến mất khỏi bản sao.

### 15.6. Bài thực hành 4: không trộn hợp đồng rỗng

Trên một map rỗng, đối chiếu `firstEntry()` với `sequencedKeySet().getFirst()`. Tiếp theo thêm mapping có khóa `null`, giá trị `null` rồi gọi lại.

Kết quả đối chiếu: lúc rỗng, API map trả entry `null`, còn API view ném `NoSuchElementException`. Khi đã có mapping, `firstEntry()` là entry khác `null`, còn `getFirst()` của view khóa trả khóa `null` hợp lệ. Không thể kiểm tra rỗng của mọi API bằng cùng một phép so sánh kết quả với `null`.

## 16. Môi trường và tài liệu đối chiếu

Năm chương trình độc lập trong bài đã được biên dịch bằng `javac --release 21 -encoding UTF-8` và chạy trên JDK 24.0.2; stdout đã được đối chiếu từ các lần chạy đó. `--release 21` kiểm tra khả năng dùng API Java 21 lúc biên dịch; thông tin môi trường chạy nêu rõ JDK thực sự đã dùng. Mỗi tệp có một lớp `public` trùng tên tệp, không phụ thuộc ví dụ ở tệp khác.

- [LinkedHashMap — Java 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashMap.html): chế độ thứ tự, phương thức định vị và quan hệ giữa map với view đảo.
- [SequencedMap — Java 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/SequencedMap.html): nhóm thao tác mapping hai đầu, snapshot entry và hợp đồng thứ tự.
- [SequencedCollection — Java 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/SequencedCollection.html): `getFirst/getLast`, `removeFirst/removeLast` và trường hợp rỗng.
- [SequencedSet — Java 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/SequencedSet.html): tập hợp có thứ tự duyệt và view đảo.
- [LinkedHashMap — mã nguồn OpenJDK 21](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.base/share/classes/java/util/LinkedHashMap.java): đối chiếu cách view chuyển tiếp các thao tác và đổi đầu/cuối.


</details>

- [Quay lại đầu trang](#back-to-top)
