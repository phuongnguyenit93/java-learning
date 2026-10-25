<a id="back-to-top"></a>

# Map

## Menu
- [Khóa duy nhất, giá trị có thể trùng và ý nghĩa ánh xạ](#map-001)
- [put, putAll, get, remove và giá trị cũ trả về](#map-002)
- [containsKey, containsValue và phân biệt vắng khóa với giá trị null](#map-003)
- [getOrDefault và trường hợp khóa ánh xạ tới null](#map-004)
- [keySet, values, entrySet và Map.Entry](#map-005)
- [Duyệt bằng entrySet, forEach hoặc các view](#map-006)
- [putIfAbsent, replace và remove có điều kiện](#map-007)
- [computeIfAbsent, computeIfPresent, compute và kết quả null](#map-008)
- [merge, replaceAll và các mẫu cập nhật dữ liệu](#map-009)
- [equals, hashCode và hợp đồng khóa của Map](#map-010)

## <a id="map-001">Khóa duy nhất, giá trị có thể trùng và ý nghĩa ánh xạ</a>

<details>
<summary>Xem chi tiết</summary>

# Khóa duy nhất, giá trị có thể trùng và ý nghĩa ánh xạ

## 1. Map trả lời câu hỏi “từ khóa này tìm được gì?”

Giả sử ứng dụng quản lý nhóm làm việc của nhân viên:

```text
emp-1 → Backend
emp-2 → Backend
emp-3 → Data
```

Khi có mã `emp-2`, ứng dụng muốn tìm ngay nhóm tương ứng. Mã nhân viên đóng vai trò **khóa (key)**; tên nhóm là **giá trị (value)**. Mỗi dòng là một **ánh xạ (mapping)** từ một khóa tới một giá trị.

Trong Java, `Map<K, V>` mô tả cấu trúc đó:

```java
Map<String, String> teamByEmployee;
```

Đây là khai báo minh họa, cần import `java.util.Map`. Kiểu thứ nhất là kiểu khóa, kiểu thứ hai là kiểu giá trị. Hai kiểu có thể giống nhau mà vai trò vẫn khác nhau.

Ba nguyên tắc đầu tiên:

- Trong một map, một khóa xác định tối đa một ánh xạ hiện tại.
- Nhiều khóa khác nhau có thể cùng ánh xạ tới các giá trị bằng nhau.
- Tra cứu theo khóa không tự biến thành tra cứu ngược từ giá trị về một khóa duy nhất.

Bài này giải thích ba nguyên tắc ấy, cách chúng thể hiện qua các thao tác cơ bản, và cách chọn mô hình khi một khóa cần chứa một nhóm dữ liệu.

## 2. Khóa duy nhất không có nghĩa mọi dữ liệu đều duy nhất

### 2.1. Mỗi khóa chỉ giữ một giá trị hiện tại

Với `emp-1 → Backend`, nếu ghi tiếp `emp-1 → Platform` vào một map có hỗ trợ cập nhật, kết quả hiện tại là:

```text
emp-1 → Platform
```

Map không đồng thời giữ hai ánh xạ riêng `emp-1 → Backend` và `emp-1 → Platform`. Nếu cần lịch sử nhóm cũ và mới, phải thiết kế dữ liệu lịch sử riêng; một map đơn giản không tự lưu lịch sử thay thế.

“Tối đa một” còn bao gồm trường hợp khóa chưa tồn tại. Không phải mọi giá trị có thể có của kiểu K đều bắt buộc phải có trong map.

### 2.2. Hai khóa có thể cùng một giá trị

Hai ánh xạ:

```text
emp-1 → Backend
emp-2 → Backend
```

hoàn toàn hợp lệ. Không có yêu cầu một tên nhóm chỉ thuộc về một nhân viên.

Ở đây có hai khóa, hai ánh xạ và hai lần xuất hiện của giá trị Backend. Nếu chỉ đếm các tên nhóm khác nhau thì có một tên nhóm, nhưng đó là một phép thống kê khác.

Đừng suy ra `size()` bằng số giá trị phân biệt. `Map.size()` đếm số ánh xạ khóa–giá trị.

### 2.3. Hướng ánh xạ quan trọng

Cấu trúc:

```java
Map<String, String> teamByEmployee;
```

diễn đạt “mỗi nhân viên có một nhóm hiện tại”. Nếu đổi thành “mỗi nhóm có nhiều nhân viên”, kiểu đơn `Map<String, String>` với tên nhóm làm khóa thường không đủ.

Việc đảo mũi tên là thay đổi mô hình, không chỉ đổi tên biến:

```text
Nhân viên → Nhóm hiện tại
Nhóm      → Danh sách nhân viên
```

Các giá trị có thể trùng làm quan hệ ban đầu không nhất thiết một–một. Vì vậy không có một khóa ngược duy nhất chỉ từ một tên nhóm.

Hợp đồng nền tảng của giao diện và ba cách nhìn dữ liệu được mô tả trong [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

## 3. Khi nào hai khóa được coi là cùng khóa?

### 3.1. Với HashMap và LinkedHashMap, không chỉ xét tham chiếu

Hai đối tượng String khác nhau có thể chứa cùng nội dung:

```java
String first = "emp-1";
String second = new String("emp-1");
```

Trong ngữ cảnh này:

```text
first == second       là false
first.equals(second)  là true
```

Với `HashMap` hoặc `LinkedHashMap`, hai khóa String đó biểu thị cùng một khóa theo phép bằng. Gọi `put` bằng `second` cập nhật ánh xạ có khóa bằng `first`; nó không tạo thêm một nhân viên chỉ vì đối tượng String được tạo bằng `new`.

Mã ví dụ bên dưới dùng `new String` chỉ để làm rõ khác biệt giữa tham chiếu và giá trị, không phải cách cần dùng thông thường để tạo mã nhân viên.

### 3.2. Trùng hashCode không đủ để là cùng khóa

Map dựa trên bảng băm dùng `hashCode` để hỗ trợ tìm kiếm, nhưng không coi mọi khóa cùng mã băm là bằng nhau.

Ví dụ hai chuỗi `"Aa"` và `"BB"` có thể có cùng mã băm mà không bằng nhau. Một HashMap vẫn có thể giữ ánh xạ cho cả hai.

Quy tắc của khóa tùy chỉnh phải nhất quán: nếu hai khóa bằng nhau theo `equals`, chúng phải có cùng `hashCode`. Chiều ngược lại không bắt buộc.

Không dùng một phép băm làm “mã định danh chắc chắn duy nhất” chỉ vì nó giúp tìm nhanh. Mã băm có thể va chạm; khóa còn cần quy tắc bằng nhau đúng với miền dữ liệu.

Đặc tính về phép bằng, null và thứ tự của lớp được đối chiếu ở [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

### 3.3. Map có thứ tự còn cần đọc quy tắc của lớp

`TreeMap` xác định sự tương đương trong thứ tự khóa qua `compareTo` hoặc comparator. Hai khóa được so thành 0 sẽ được xử lý như cùng một khóa trong map đó.

Ví dụ một comparator bỏ qua hoa/thường có thể coi `"Code"` và `"code"` cùng vị trí khóa, dù String.equals của hai chuỗi là false. Khi đó ghi khóa sau có thể thay giá trị ở khóa đã có.

Để tuân theo hợp đồng Map thông thường dựa trên phép bằng, quy tắc thứ tự của TreeMap cần nhất quán với `equals`. Xem [TreeMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html).

Vì thế, câu “khóa duy nhất” phải đi cùng câu “theo quy tắc nhận diện khóa nào của cấu trúc đang dùng?”. Không dùng một thử nghiệm trên một lớp để suy ra mọi lớp Map có cùng chính sách đặc thù.

### 3.4. Tính duy nhất chỉ nằm trong phạm vi map đang xét

Hai map độc lập có thể cùng chứa khóa `emp-1`, mỗi map giữ một giá trị khác nhau. Map không tạo ra ràng buộc duy nhất trên toàn chương trình hay trên một cơ sở dữ liệu.

Nếu mã nhân viên chỉ duy nhất trong từng tổ chức, một map tổng hợp nhiều tổ chức có thể cần khóa ghép như `(organizationId, employeeId)`. Dùng riêng employeeId có thể vô tình ghi đè dữ liệu của tổ chức khác.

Kiểu record với các thành phần định danh bất biến thường giúp biểu đạt một khóa ghép rõ ràng. Khóa cần phản ánh đúng miền mà nó phải phân biệt, không chỉ đủ ngắn để tiện gõ.

## 4. put: thêm khóa mới hoặc thay giá trị của khóa đã có

### 4.1. Hai trường hợp làm size thay đổi khác nhau

Trên một map có thể sửa:

| Trước lệnh | Lệnh | Sau lệnh | Biến đổi size |
| --- | --- | --- | --- |
| Không có `emp-1` | `put("emp-1", "Backend")` | Có `emp-1 → Backend` | Tăng 1 |
| Có `emp-1 → Backend` | `put("emp-1", "Platform")` | Có `emp-1 → Platform` | Không đổi |
| Có `emp-1 → Platform` | `put("emp-2", "Platform")` | Có thêm `emp-2 → Platform` | Tăng 1 |

Giá trị bằng nhau ở dòng cuối không ngăn thêm một khóa mới. Ngược lại, giá trị mới khác hoàn toàn ở dòng giữa vẫn không tạo thêm khóa thứ hai.

### 4.2. Kết quả put là giá trị cũ

`put(key, value)` trả giá trị trước đó gắn với khóa, hoặc null khi không có giá trị cũ. Với map cho phép giá trị null, kết quả null còn có thể đến từ một ánh xạ cũ có giá trị null.

Ví dụ:

```java
// Đoạn lệnh minh họa; map có thể sửa và ban đầu không chứa emp-1.
String old1 = map.put("emp-1", "Backend");  // null
String old2 = map.put("emp-1", "Platform"); // "Backend"
```

Đừng dùng riêng `old == null` để chứng minh chắc chắn trước đó không có khóa, nếu lớp map cho phép giá trị null.

Nếu nghiệp vụ cần phân biệt “tạo mới” với “cập nhật” trong ví dụ một luồng, có thể kiểm tra sự tồn tại của khóa trước khi ghi. Một chuỗi kiểm tra rồi ghi không tự trở thành thao tác nguyên tử cho nhiều luồng.

### 4.3. Ghi lại cùng một giá trị không thêm lịch sử

Gọi lại `put("emp-1", "Backend")` khi khóa đã ánh xạ tới Backend vẫn giữ một ánh xạ cho emp-1. Nó không ghi lại “nhân viên đã được gán Backend hai lần”.

Map biểu thị trạng thái liên kết hiện tại. Nếu yêu cầu là ghi các sự kiện gán nhóm, cần một danh sách sự kiện hoặc một cấu trúc phù hợp để lưu nhiều lần xảy ra.

## 5. get, containsKey và containsValue trả lời ba câu khác nhau

| Phương thức | Câu hỏi |
| --- | --- |
| `get(key)` | Giá trị đang gắn với khóa này là gì? |
| `containsKey(key)` | Map có ánh xạ cho khóa này không? |
| `containsValue(value)` | Có ít nhất một khóa đang ánh xạ tới giá trị bằng mục tiêu không? |

Với `emp-1 → Backend` và `emp-2 → Backend`:

- `get("emp-1")` trả Backend.
- `containsKey("Backend")` không có nghĩa “tìm nhóm Backend”; Backend đang ở phía giá trị.
- `containsValue("Backend")` có thể trả true nhưng không trả về emp-1, emp-2 hoặc số lượng nhân viên.

Tên biến như `teamByEmployee` giúp đọc hướng ánh xạ. Một biến tên `data` làm người đọc dễ nhầm “đang dùng mã hay tên nhóm làm khóa?”.

### Null từ get có hai khả năng

Với map cho phép giá trị null:

```text
Trường hợp A: không có khóa emp-4
Trường hợp B: có khóa emp-4, giá trị đang là null
```

Cả hai có thể cho `get("emp-4") == null`. `containsKey("emp-4")` phân biệt chúng.

| Trạng thái | get | containsKey | Có được size đếm không? |
| --- | --- | --- | --- |
| Không có khóa | `null` | `false` | Không |
| Có khóa ánh xạ tới null | `null` | `true` | Có |
| Có khóa ánh xạ tới giá trị khác null | Giá trị đó | `true` | Có |

Không tự động mở hộp một kết quả có thể null vào kiểu nguyên thủy, chẳng hạn `int count = map.get(key)` với Map có giá trị Integer, nếu chưa quyết định cách xử lý thiếu dữ liệu.

Null ở đây là một khả năng của lớp cài đặt, không phải yêu cầu mọi Map đều hỗ trợ.

## 6. Chương trình chạy: khóa duy nhất và giá trị trùng

Lưu thành **MapKeyValueDemo.java**:

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class MapKeyValueDemo {
    public static void main(String[] args) {
        Map<String, String> teamByEmployee = new LinkedHashMap<>();
        teamByEmployee.put("emp-1", "Backend");
        teamByEmployee.put("emp-2", "Backend");
        teamByEmployee.put("emp-3", "Data");

        System.out.println("initial map: " + teamByEmployee);
        System.out.println("mapping count: " + teamByEmployee.size());
        System.out.println("keys: " + teamByEmployee.keySet());
        System.out.println("values: " + teamByEmployee.values());
        System.out.println("contains key Backend: " + teamByEmployee.containsKey("Backend"));
        System.out.println("contains value Backend: " + teamByEmployee.containsValue("Backend"));
        System.out.println("get emp-1: " + teamByEmployee.get("emp-1"));

        System.out.println("old team: " + teamByEmployee.put("emp-1", "Platform"));
        System.out.println("size after replacement: " + teamByEmployee.size());

        String anotherKey = new String("emp-1");
        System.out.println("same key reference: " + (anotherKey == "emp-1"));
        System.out.println("equal key value: " + anotherKey.equals("emp-1"));
        System.out.println("old team using equal key: "
                + teamByEmployee.put(anotherKey, "Security"));
        System.out.println("map after equal key: " + teamByEmployee);

        teamByEmployee.put("emp-4", null);
        System.out.println("get emp-4: " + teamByEmployee.get("emp-4"));
        System.out.println("contains emp-4: " + teamByEmployee.containsKey("emp-4"));
        System.out.println("get missing: " + teamByEmployee.get("missing"));
        System.out.println("contains missing: " + teamByEmployee.containsKey("missing"));

        System.out.println("removed emp-2 value: " + teamByEmployee.remove("emp-2"));
        System.out.println("contains Backend after removal: "
                + teamByEmployee.containsValue("Backend"));
        System.out.println("final map: " + teamByEmployee);
        System.out.println("final size: " + teamByEmployee.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapKeyValueDemo.java
java MapKeyValueDemo
```

Stdout chính xác:

```text
initial map: {emp-1=Backend, emp-2=Backend, emp-3=Data}
mapping count: 3
keys: [emp-1, emp-2, emp-3]
values: [Backend, Backend, Data]
contains key Backend: false
contains value Backend: true
get emp-1: Backend
old team: Backend
size after replacement: 3
same key reference: false
equal key value: true
old team using equal key: Platform
map after equal key: {emp-1=Security, emp-2=Backend, emp-3=Data}
get emp-4: null
contains emp-4: true
get missing: null
contains missing: false
removed emp-2 value: Backend
contains Backend after removal: false
final map: {emp-1=Security, emp-3=Data, emp-4=null}
final size: 3
```

Chương trình dùng LinkedHashMap mặc định để thứ tự in theo thứ tự thêm khóa được xác định. Thay giá trị của khóa đã tồn tại không làm khóa ấy tự chuyển xuống cuối trong cấu hình này. Đối chiếu [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

Không dùng thứ tự in này làm bảo đảm của HashMap hoặc của mọi Map. Tính duy nhất của khóa và chính sách thứ tự là hai thuộc tính riêng.

Các điểm cần giải thích:

- Ba ánh xạ ban đầu chỉ có hai tên nhóm khác nhau.
- Giá trị Backend xuất hiện hai lần trong `values()`.
- Thay nhóm của emp-1 không đổi size.
- String khóa mới tạo vẫn cập nhật emp-1 vì bằng nội dung.
- emp-4 có ánh xạ null, còn missing không có ánh xạ.
- Xóa emp-2 không xóa nhân viên nào khác. Backend hết xuất hiện vì emp-1 đã đổi sang Security trước đó.

Các chương trình trong bài được biên dịch bằng `--release 17` và chạy trên OpenJDK 24.0.2. Cờ đó kiểm tra ngôn ngữ/API mục tiêu Java 17 và tạo bytecode tương ứng; JVM dùng để chạy vẫn là bản đã nêu.

## 7. Ba cách nhìn cùng một tập ánh xạ

Map không kế thừa giao diện `Collection`. Nó cung cấp các góc nhìn collection để đọc khóa, giá trị hoặc cả cặp:

| Góc nhìn | Kiểu trả về | Điều cần nhớ |
| --- | --- | --- |
| `keySet()` | `Set<K>` | Khóa không lặp theo quy tắc của map |
| `values()` | `Collection<V>` | Giá trị có thể xuất hiện nhiều lần |
| `entrySet()` | `Set<Map.Entry<K,V>>` | Mỗi entry nối một khóa với giá trị tương ứng |

Với ví dụ:

```text
map:      {emp-1=Backend, emp-2=Backend, emp-3=Data}
keys:     [emp-1, emp-2, emp-3]
values:   [Backend, Backend, Data]
entries:  emp-1=Backend; emp-2=Backend; emp-3=Data
```

Hai entry có cùng giá trị Backend vẫn khác vì khóa khác. `entrySet()` là tập các cặp ánh xạ, không có nghĩa tập các giá trị phải duy nhất.

### Các góc nhìn không tự là bản sao độc lập

Trên các map hỗ trợ cập nhật, những góc nhìn này gắn với dữ liệu của map. Thay đổi map có thể được phản ánh qua góc nhìn; một số thao tác xóa được hỗ trợ trên góc nhìn cũng xóa ánh xạ tương ứng.

Vì thế, gọi `values().remove("Backend")` không chỉ xóa một chuỗi ra khỏi một danh sách tạm. Nếu được hỗ trợ, nó có thể xóa một ánh xạ đang mang giá trị đó. Khi có nhiều giá trị bằng nhau, không dùng thao tác này để chọn một nhân viên cụ thể; xóa theo khóa thể hiện mục tiêu rõ hơn.

Cũng không dùng `keySet().add(key)` như cách thêm một ánh xạ: chỉ có khóa chưa đủ xác định giá trị đi kèm. Nếu cần cấu trúc tách rời để xử lý riêng, hãy tạo bản sao phù hợp và hiểu rằng sao chép cấu trúc không tự sao chép sâu đối tượng phần tử.

## 8. Tra cứu ngược có thể trả nhiều khóa

Nếu câu hỏi là “ai thuộc nhóm Backend?”, không có một kết quả đơn duy nhất được bảo đảm. Có thể không có ai, một người hoặc nhiều người.

Phương thức minh họa sau đặt trong một lớp, cần import Map, List, ArrayList và Objects:

```java
static List<String> employeesInTeam(
        Map<String, String> teamByEmployee, String targetTeam) {
    List<String> result = new ArrayList<>();
    for (Map.Entry<String, String> entry : teamByEmployee.entrySet()) {
        if (Objects.equals(entry.getValue(), targetTeam)) {
            result.add(entry.getKey());
        }
    }
    return result;
}
```

Hàm trả mọi khóa có giá trị phù hợp. `Objects.equals` xử lý cả trường hợp giá trị hoặc mục tiêu null trong một map cho phép null.

Thứ tự kết quả đi theo thứ tự duyệt entry của map nguồn. Nếu nguồn là HashMap, không cam kết một thứ tự tên nhân viên cố định chỉ từ phương thức này. Nếu đầu ra cần sắp xếp theo mã, phải thêm một quyết định sắp xếp rõ ràng.

Đối với map dựa trên bảng băm thông dụng, tra cứu theo khóa được tối ưu hơn việc quét theo một giá trị bất kỳ. Không suy ra `containsValue` hoặc hàm tìm ngược có cùng đặc tính tìm kiếm với `get(key)`.

Nếu tra cứu nhóm → nhân viên xảy ra thường xuyên, có thể cần một chỉ mục ngược như Map nhóm → danh sách nhân viên. Khi đó ứng dụng phải cập nhật các chỉ mục nhất quán khi nhân viên đổi nhóm. Map không tự duy trì mối liên hệ giữa hai cấu trúc riêng.

## 9. Một khóa cần nhiều dữ liệu: ánh xạ tới một nhóm

### 9.1. Hai lần put không biến Map thành đa ánh xạ

Nếu viết:

```java
// Đoạn lệnh minh họa:
Map<String, String> courseByStudent = new LinkedHashMap<>();
courseByStudent.put("S1", "Java");
courseByStudent.put("S1", "SQL");
```

thì S1 ánh xạ tới SQL. Java không còn là giá trị hiện tại của S1 trong map đó. Đổi tên biến thành `coursesByStudent` không thay đổi hành vi của kiểu dữ liệu.

### 9.2. Một giá trị có thể là một collection

Muốn một sinh viên có nhiều môn, có thể dùng:

```java
Map<String, List<String>> coursesByStudent;
```

S1 vẫn có **một giá trị**: một đối tượng List. Danh sách bên trong giữ nhiều môn.

```text
S1 → [Java, SQL]
S2 → [Java]
```

Có hai ánh xạ, dù tổng số phần tử trong các danh sách là ba. Đây không phải vi phạm khóa duy nhất.

Cách cập nhật đúng là tìm danh sách của khóa rồi thêm phần tử vào danh sách đó, hoặc tạo một danh sách mới theo chính sách ứng dụng. Gọi put để thay toàn bộ danh sách bằng một danh sách mới vẫn là thay giá trị của một khóa.

Nếu muốn một sinh viên không lặp môn, có thể cân nhắc giá trị dạng Set; nếu cần giữ lần đăng ký lặp và thứ tự, List có ý nghĩa khác. Map không tự chọn quy tắc bên trong collection giá trị.

### 9.3. Giá trị trùng và dùng chung đối tượng là hai chuyện

Hai khóa có thể giữ hai danh sách riêng nhưng bằng nội dung. Hai khóa cũng có thể giữ đúng cùng một đối tượng danh sách.

Trong trường hợp dùng chung, sửa danh sách qua một tham chiếu làm nội dung được nhìn thấy từ cả hai khóa thay đổi. Map lưu tham chiếu, không tự sao chép payload cho mỗi khóa.

Nếu nghiệp vụ yêu cầu danh sách độc lập, phải tạo đối tượng riêng cho từng giá trị hoặc dùng chính sách dữ liệu bất biến phù hợp. Không đổ lỗi cho khóa trùng khi vấn đề thực tế là nhiều khóa đang cùng trỏ đến một payload thay đổi được.

## 10. Chương trình chạy: một giá trị, danh sách giá trị và cùng tham chiếu

Lưu thành **MappingGroupsDemo.java**:

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MappingGroupsDemo {
    private static void addCourse(
            Map<String, List<String>> coursesByStudent,
            String student, String course) {
        List<String> courses = coursesByStudent.get(student);
        if (courses == null) {
            courses = new ArrayList<>();
            coursesByStudent.put(student, courses);
        }
        courses.add(course);
    }

    public static void main(String[] args) {
        Map<String, String> oneCourse = new LinkedHashMap<>();
        oneCourse.put("S1", "Java");
        System.out.println("old course when replacing: " + oneCourse.put("S1", "SQL"));
        System.out.println("single-value map: " + oneCourse);
        System.out.println("single-value mapping count: " + oneCourse.size());

        Map<String, List<String>> coursesByStudent = new LinkedHashMap<>();
        addCourse(coursesByStudent, "S1", "Java");
        addCourse(coursesByStudent, "S1", "SQL");
        addCourse(coursesByStudent, "S2", "Java");
        System.out.println("grouped map: " + coursesByStudent);
        System.out.println("grouped mapping count: " + coursesByStudent.size());
        int courseCount = 0;
        for (List<String> courses : coursesByStudent.values()) {
            courseCount += courses.size();
        }
        System.out.println("total course entries: " + courseCount);

        List<String> shared = new ArrayList<>(List.of("Java"));
        Map<String, List<String>> sharedValues = new LinkedHashMap<>();
        sharedValues.put("S1", shared);
        sharedValues.put("S2", shared);
        shared.add("SQL");
        System.out.println("shared value after change: " + sharedValues);
        System.out.println("same value reference: "
                + (sharedValues.get("S1") == sharedValues.get("S2")));

        Map<String, String> fixed = Map.of("S1", "Java", "S2", "Java");
        System.out.println("Map.of duplicate values size: " + fixed.size());
        try {
            Map.of("S1", "Java", "S1", "SQL");
        } catch (IllegalArgumentException ex) {
            System.out.println("Map.of duplicate keys: " + ex.getClass().getSimpleName());
        }
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 MappingGroupsDemo.java
java MappingGroupsDemo
```

Stdout chính xác:

```text
old course when replacing: Java
single-value map: {S1=SQL}
single-value mapping count: 1
grouped map: {S1=[Java, SQL], S2=[Java]}
grouped mapping count: 2
total course entries: 3
shared value after change: {S1=[Java, SQL], S2=[Java, SQL]}
same value reference: true
Map.of duplicate values size: 2
Map.of duplicate keys: IllegalArgumentException
```

Hàm `addCourse` trong chương trình tạo danh sách khi chưa có, rồi thêm môn vào danh sách. Các giá trị danh sách do hàm quản lý đều khác null. Nó là ví dụ một luồng; chuỗi kiểm tra/tạo/thêm không được trình bày như một thao tác nhóm nguyên tử cho nhiều luồng.

Phần dùng `shared` tạo hai ánh xạ tới cùng một danh sách, nên sau khi thêm SQL cả S1 và S2 đều nhìn thấy Java, SQL. Đây là chia sẻ payload có chủ đích để minh họa, khác với cấu trúc nhóm độc lập ở phần trước.

Phần `Map.of` cho thấy cần phân biệt hai tình huống:

- Hai khóa khác nhau cùng giá trị Java là hợp lệ.
- Truyền hai khóa S1 bằng nhau vào một lần tạo Map.of bị từ chối bằng IllegalArgumentException.

Map.of tạo map không cho sửa, nên không có quy tắc “ghi khóa sau sẽ thay giá trị khóa trước” trong lời gọi factory này. Hành vi put trên một map có thể sửa và kiểm tra trùng khi tạo map bất biến là hai hợp đồng khác nhau.

## 11. Null và khả năng sửa phụ thuộc lớp map

### 11.1. Không có một chính sách null cho mọi Map

HashMap và LinkedHashMap cho phép khóa null và giá trị null. Một khóa null vẫn là một khóa: ghi lại null sẽ thay giá trị gắn với khóa ấy, không tạo ra nhiều khóa null độc lập.

Nhiều khóa khác nhau có thể cùng giữ giá trị null, giống như cùng giữ Backend. Mỗi ánh xạ vẫn được size đếm.

Map.of không cho khóa null hoặc giá trị null. Không dùng một ví dụ HashMap để suy ra một factory hoặc lớp map khác phải chấp nhận null.

Nếu nghiệp vụ không muốn có trạng thái “khóa tồn tại nhưng chưa có giá trị”, có thể cấm null ở tầng tiếp nhận dữ liệu. Đó là một chính sách thiết kế giúp đơn giản hóa tra cứu, không phải điều mọi Map tự áp đặt.

### 11.2. Khóa duy nhất không đồng nghĩa map luôn cập nhật được

Một map không cho sửa vẫn có khóa duy nhất và có thể có giá trị trùng. Cố gọi put/remove trên một cấu trúc không hỗ trợ sửa có thể báo `UnsupportedOperationException`.

Khi giải thích “put thay giá trị của khóa cũ”, điều kiện ngữ cảnh là đang dùng một map hỗ trợ cập nhật. Đừng gọi API có cùng tên rồi bỏ qua khả năng sửa của đối tượng thực tế.

Tương tự, null, thứ tự duyệt và khả năng truy cập đồng thời là các đặc tính cần đọc từ lớp cụ thể. Giao diện K → V không tự quyết định tất cả các chính sách đó.

## 12. Chọn khóa có ý nghĩa và giữ quy tắc nhận diện ổn định

### 12.1. Giá trị trùng có thể hoàn toàn đúng mô hình

Tên người, phòng ban, trạng thái hay mức điểm có thể trùng giữa nhiều bản ghi. Không nên ép chúng thành duy nhất chỉ để “vừa map”.

Ví dụ dùng tên người làm khóa có thể làm hai người cùng tên ghi đè nhau. Nếu nghiệp vụ cần phân biệt họ, chọn mã định danh phù hợp thay vì kết luận Map không chứa được hai người.

Ngược lại, nếu chính sách thực sự chỉ cho một cấu hình hiện tại trên mỗi mã cấu hình, việc put cùng khóa thay giá trị có thể đúng ý.

Hãy viết một câu trước khi chọn kiểu:

```text
Trong phạm vi ..., mỗi khóa ... xác định tối đa một ...
```

Nếu câu đó không đúng với dữ liệu thật, cần thay khóa, phạm vi hoặc cấu trúc giá trị.

### 12.2. Không đổi các trường nhận diện khi khóa đang nằm trong map

Với khóa tùy chỉnh, thay một trường tham gia `equals` hoặc `hashCode` sau khi đã đưa vào map có thể làm map không còn tìm khóa theo cách người viết mong đợi. Hợp đồng Map không xác định hành vi khi khóa bị sửa theo cách ảnh hưởng phép bằng trong lúc nó đang được dùng làm khóa.

Ưu tiên các khóa có thành phần định danh ổn định. Một record chỉ giúp nếu những thành phần tham gia định danh bên trong cũng giữ được ý nghĩa ổn định; record chứa một đối tượng thay đổi được không tự tạo sự bất biến sâu.

Nếu cần đổi định danh, thực hiện một quy trình chuyển ánh xạ rõ ràng với khóa cũ và khóa mới theo yêu cầu ứng dụng. Đừng dựa vào việc sửa một trường rồi mong cấu trúc tự tái lập mọi chỉ mục.

### 12.3. Map không tự kiểm tra quy tắc nghiệp vụ về giá trị

Nhiều khóa cùng giá trị là hợp lệ ở mức cấu trúc. Nếu nghiệp vụ yêu cầu địa chỉ email chỉ thuộc một người, `Map<Id, Email>` không tự cấm hai Id cùng Email.

Có thể cần kiểm tra bổ sung hoặc một mô hình chỉ mục theo email. Cũng cần quyết định quy tắc chuẩn hóa email hoặc mã trước khi áp dụng tính duy nhất; Map không hiểu ý nghĩa miền dữ liệu chỉ từ tên kiểu String.

Đừng đánh đồng “cấu trúc cho phép” với “nghiệp vụ chấp nhận”. Bài học về khóa và giá trị giúp biết ràng buộc nào đã có, ràng buộc nào còn phải thiết kế.

## 13. Lỗi phổ biến và cách chẩn đoán

| Hiện tượng | Suy luận sai hoặc nguyên nhân | Hướng sửa |
| --- | --- | --- |
| Ghi lần hai mất giá trị cũ | Mong Map giữ nhiều giá trị độc lập cho một khóa | Dùng collection làm giá trị hoặc lưu lịch sử riêng |
| size nhỏ hơn số lần put | Đã cập nhật các khóa tương đương | Đếm khóa mới thay vì đếm lời gọi |
| Hai nhân viên cùng nhóm bị coi là lỗi | Nhầm duy nhất của khóa sang giá trị | Cho phép giá trị trùng theo mô hình |
| get tên nhóm không ra nhân viên | Tra ngược phía khóa | Duyệt các entry hoặc có chỉ mục ngược |
| get trả null nên kết luận không có khóa | Bỏ qua giá trị null được lưu | Dùng containsKey khi cần phân biệt |
| Tạo String mới nhưng vẫn ghi đè | Nhầm cùng khóa với cùng tham chiếu | Đọc quy tắc bằng nhau của khóa |
| Hai khóa cùng hash bị tưởng là một | Nhầm mã băm với định danh duy nhất | Kiểm tra equals và hợp đồng hashCode |
| Hai danh sách cùng thay đổi | Hai khóa dùng chung payload | Tạo danh sách riêng khi cần độc lập |
| put trên Map.of bị lỗi | Bỏ qua map không cho sửa | Chọn đúng cấu trúc theo khả năng cập nhật |
| Khóa tự chỉnh sửa rồi không tra được | Quy tắc nhận diện đã đổi sau khi thêm | Dùng khóa ổn định và cập nhật ánh xạ rõ ràng |

## 14. Bài tập tự kiểm tra có lời giải

### Bài 1: Đếm ánh xạ

Một map rỗng có thể sửa lần lượt nhận `A → 10`, `B → 10`, `A → 20`. Size và các ánh xạ cuối là gì?

**Lời giải:** size là 2. A ánh xạ tới 20, B ánh xạ tới 10. Hai lời gọi đầu tạo hai khóa; lời gọi cuối thay giá trị của A.

### Bài 2: Khóa mới hay đối tượng mới?

HashMap chứa khóa String `"id-1"`. Ghi tiếp bằng `new String("id-1")` có bắt buộc tăng size không?

**Lời giải:** không. Hai String bằng nội dung nên biểu thị cùng khóa của HashMap. Đối tượng khóa được tạo mới không có nghĩa định danh logic mới.

### Bài 3: Từ giá trị về khóa

Có A → Backend và B → Backend. `containsValue("Backend")` có trả A không? Có thể xây map ngược Backend → một String mà giữ đủ hai nhân viên không?

**Lời giải:** containsValue trả boolean, không trả A. Map ngược với một String làm giá trị chỉ giữ một giá trị hiện tại cho Backend, nên cần dạng nhóm như Backend → danh sách nhân viên nếu muốn giữ cả A và B.

### Bài 4: Có khóa nhưng giá trị null

HashMap chứa A → null. `get("A")`, `containsKey("A")` và size lần lượt là gì?

**Lời giải:** null, true và 1. Giá trị null không xóa sự tồn tại của ánh xạ. Một khóa B chưa có cũng cho get bằng null nhưng containsKey là false.

### Bài 5: Một danh sách là bao nhiêu giá trị của Map?

Map chứa S1 → [Java, SQL] và S2 → [Java]. Map có vi phạm một khóa một giá trị không? Size là bao nhiêu?

**Lời giải:** không vi phạm. Mỗi khóa ánh xạ tới một đối tượng danh sách. Size là 2; tổng số phần tử môn học trong các danh sách là 3 và được đếm bằng phép khác.

### Bài 6: Hai khóa cùng trỏ một danh sách

A và B cùng trỏ tới một ArrayList ban đầu có Java. Thêm SQL vào danh sách qua A. B có thấy SQL không?

**Lời giải:** có, vì cả hai ánh xạ dùng cùng đối tượng giá trị. Tính duy nhất của khóa không tự sao chép payload. Nếu cần độc lập, phải tạo danh sách riêng.

### Bài 7: Map.of và giá trị trùng

`Map.of("A", 1, "B", 1)` có hợp lệ không? `Map.of("A", 1, "A", 2)` thì sao?

**Lời giải:** trường hợp đầu hợp lệ, có hai ánh xạ cùng giá trị 1. Trường hợp sau bị từ chối do khóa trùng. Đây là kiểm tra lúc tạo bằng factory, khác put thay giá trị trên map có thể sửa.

### Bài 8: Phạm vi định danh

Hai tổ chức cùng có nhân viên mã 001. Một map toàn hệ thống chỉ dùng mã 001 làm khóa gây mất một bản ghi sau khi put. Có phải Map làm sai không?

**Lời giải:** cấu trúc đang thực hiện đúng quy tắc một khóa một giá trị hiện tại. Mô hình khóa thiếu phạm vi tổ chức. Có thể cần khóa ghép tổ chức + mã nhân viên hoặc map riêng theo từng tổ chức.

## 15. Tài liệu đối chiếu

- [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): hợp đồng khóa–giá trị và các góc nhìn dữ liệu.
- [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): chính sách của map dựa trên bảng băm được nhắc trong bài.
- [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html): thứ tự xác định cho ví dụ in kết quả.
- [TreeMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html): quy tắc thứ tự và tính tương đương của khóa.

Khi thiết kế một Map, hãy xác định rõ khóa phân biệt điều gì, giá trị biểu thị điều gì, và một khóa có cần một đối tượng đơn hay một nhóm dữ liệu. Khóa duy nhất giữ cho phép tra cứu có nghĩa xác định; giá trị trùng cho phép nhiều khóa cùng chia sẻ một nội dung hợp lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="map-002">put, putAll, get, remove và giá trị cũ trả về</a>

<details>
<summary>Xem chi tiết</summary>

# put, putAll, get, remove và giá trị cũ trả về

## 1. Nhìn Map qua một ánh xạ đang tồn tại

Một `Map<K, V>` lưu các ánh xạ từ khóa kiểu `K` đến giá trị kiểu `V`. Ví dụ, khóa `"pen"` có thể xác định số lượng bút hiện tại là `10`:

```text
"pen"      → 10
"notebook" → 7
```

Điểm quan trọng là **mỗi khóa có nhiều nhất một giá trị hiện tại**. Gọi `put("pen", 15)` làm ánh xạ của `"pen"` chuyển từ `10` sang `15`; thao tác này không tạo thêm một bản ghi `"pen"` thứ hai. Nếu cần một khóa gắn với nhiều phần tử, kiểu giá trị phải thể hiện điều đó, chẳng hạn `Map<String, List<String>>`. Bản thân `put` vẫn chỉ gắn khóa với một đối tượng giá trị.

`Map` thuộc hệ thống thư viện collection của Java nhưng không kế thừa `Collection<E>`. Thao tác của nó xoay quanh khóa và giá trị, không phải vị trí chỉ số như `List`. Không có thao tác “thêm vào vị trí số 2” trong bốn phương thức của bài này.

Bài học tập trung vào bốn câu hỏi:

- Muốn gắn hoặc thay giá trị của một khóa thì dùng gì?
- Muốn lấy giá trị hiện tại thì dùng gì?
- Muốn bỏ một ánh xạ thì dùng gì?
- Kết quả phương thức trả về phản ánh trạng thái **trước** hay **sau** thao tác?

Câu hỏi cuối đặc biệt dễ gây lỗi. Với `put` và `remove` một tham số, giá trị trả về thường giúp nhận biết giá trị vừa bị thay thế hoặc lấy ra khỏi map. Nó không phải một cờ thông báo thành công.

### Vì sao cần giá trị cũ?

Giả sử màn hình sửa tồn kho gửi số lượng mới `15`. Sau khi gọi `put("pen", 15)`, chương trình có thể cần ghi nhận rằng số lượng trước đó là `10`. Đọc `get("pen")` sau thao tác chỉ nhận được `15`; thông tin `10` đã bị thay thế. Giá trị trả về của `put` cung cấp chính thông tin cũ đó.

Tương tự, `remove("pen")` vừa xóa ánh xạ vừa trả lại giá trị từng được gắn với khóa. Nếu chỉ cần giá trị bị lấy ra, thường không cần gọi `get` riêng rồi mới `remove`. Tuy nhiên, một kết quả `null` có thể không đủ để xác định trước đó khóa có tồn tại hay không; cần hiểu chính sách `null` của loại map đang dùng.

## 2. Bảng hợp đồng của bốn phương thức

Với biến `Map<K, V> map`, các chữ ký liên quan là:

| Phương thức | Tác động nếu thao tác được hỗ trợ | Kết quả trả về |
|---|---|---|
| `V put(K key, V value)` | Gắn `key` với `value`; thay giá trị nếu khóa đã có ánh xạ | Giá trị **trước khi gọi** của khóa, hoặc `null` |
| `void putAll(Map<? extends K, ? extends V> source)` | Chép các ánh xạ từ `source` vào map đích; nguồn thắng khi trùng khóa | Không trả về giá trị |
| `V get(Object key)` | Tra cứu giá trị hiện tại của khóa | Giá trị hiện tại, hoặc `null` nếu không có ánh xạ |
| `V remove(Object key)` | Xóa ánh xạ của khóa nếu có | Giá trị **trước khi xóa** của khóa, hoặc `null` |

Ở `put` và `remove`, `null` có hai khả năng nếu map cho phép giá trị `null`: trước đó không có ánh xạ, hoặc trước đó có ánh xạ với giá trị `null`. Phần 5 sẽ tách hai trạng thái này.

`get` và `remove` nhận `Object` ở vị trí khóa trong chữ ký API. Điều đó không biến map thành kho lưu khóa tùy ý: kiểu khóa khi ghi vẫn được ràng buộc bởi `K`, và tra cứu với đối tượng không thích hợp không phải một kỹ thuật nên dựa vào. Một số hiện thực có thể từ chối khóa không tương thích bằng ngoại lệ.

Bảng trên mô tả ý nghĩa ánh xạ. Các thao tác thay đổi map là thao tác tùy chọn của giao diện `Map`; một map không cho sửa có thể ném `UnsupportedOperationException`. Khả năng chấp nhận khóa hoặc giá trị `null` cũng phụ thuộc hiện thực. Chưa thể kết luận một đối tượng sửa được hay nhận `null` chỉ vì kiểu khai báo của nó là `Map`.

### Đọc một dòng put theo hai trục thời gian

Xét câu lệnh minh họa sau, với giả thiết map đang có `"pen" → 10`:

```java
Integer oldValue = stock.put("pen", 15);
```

Có hai thông tin khác nhau:

```text
Trước lời gọi:  stock["pen"] = 10
Đối số mới:                  15
Giá trị trả về:              10
Sau lời gọi:   stock["pen"] = 15
```

`oldValue` bằng `10`, còn `stock.get("pen")` sau đó bằng `15`. Tên biến nên phản ánh điều này: `oldValue`, `previousStock` hoặc `removedValue` dễ hiểu hơn `result` khi cần phân biệt hai thời điểm.

## 3. Thí nghiệm đầy đủ: thêm, thay, đọc và xóa

Chương trình độc lập dưới đây dùng `LinkedHashMap` ở chế độ mặc định để thứ tự hiển thị map có thể dự đoán được theo thứ tự chèn. Trong chế độ này, thay giá trị của một khóa đang có không đưa khóa đó xuống cuối. Việc chọn hiện thực này chỉ giúp quan sát kết quả; ý nghĩa giá trị trả về của bốn phương thức đến từ hợp đồng `Map`.

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class MapBasicReturnDemo {
    public static void main(String[] args) {
        Map<String, Integer> stock = new LinkedHashMap<>();

        Integer firstOld = stock.put("pen", 10);
        System.out.println("firstOld=" + firstOld);
        System.out.println("penNow=" + stock.get("pen"));
        System.out.println("size=" + stock.size());

        Integer replacedOld = stock.put("pen", 15);
        System.out.println("replacedOld=" + replacedOld);
        System.out.println("penNow=" + stock.get("pen"));
        System.out.println("size=" + stock.size());

        Integer notebookOld = stock.put("notebook", 7);
        System.out.println("notebookOld=" + notebookOld);
        System.out.println("stock=" + stock);

        System.out.println("missing=" + stock.get("missing"));
        System.out.println("sizeAfterMissingGet=" + stock.size());

        Integer removedOld = stock.remove("pen");
        System.out.println("removedOld=" + removedOld);
        System.out.println("stockAfterRemove=" + stock);
        System.out.println("removedAgain=" + stock.remove("pen"));

        Integer sameValueOld = stock.put("notebook", 7);
        System.out.println("sameValueOld=" + sameValueOld);
        System.out.println("finalSize=" + stock.size());
    }
}
```

Lưu mã vào `MapBasicReturnDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapBasicReturnDemo.java
java MapBasicReturnDemo
```

Kết quả chuẩn:

```text
firstOld=null
penNow=10
size=1
replacedOld=10
penNow=15
size=1
notebookOld=null
stock={pen=15, notebook=7}
missing=null
sizeAfterMissingGet=2
removedOld=15
stockAfterRemove={notebook=7}
removedAgain=null
sameValueOld=7
finalSize=1
```

### Giải thích từng chuyển trạng thái

1. `put("pen", 10)` gặp khóa chưa có: tạo ánh xạ mới, trả `null`, kích thước thành `1`.
2. `put("pen", 15)` gặp khóa đã có: thay `10` bằng `15`, trả `10`, kích thước vẫn là `1`.
3. `put("notebook", 7)` thêm một khóa khác: trả `null`, kích thước thành `2`.
4. `get("missing")` trả `null` vì không có ánh xạ. Nó không tự tạo khóa hay thêm giá trị mặc định; kích thước vẫn là `2`.
5. `remove("pen")` trả `15` rồi map chỉ còn `"notebook" → 7`.
6. `remove("pen")` lần nữa không tìm thấy ánh xạ nên trả `null`.
7. `put("notebook", 7)` trả `7`, dù giá trị mới và giá trị cũ bằng nhau.

Dòng cuối rất đáng chú ý: **giá trị trả về là dữ liệu cũ, không phải câu trả lời “dữ liệu có đổi hay không”**. Một phép ghi cùng giá trị vẫn trả giá trị cũ. Ngược lại, phép thêm mới có tác dụng thật nhưng có thể trả `null`.

Nếu đổi sang `HashMap`, các giá trị tra cứu trong ví dụ vẫn tuân theo hợp đồng, nhưng không nên lấy thứ tự in của toàn bộ map làm điều kiện đúng/sai. `HashMap` không bảo đảm thứ tự duyệt.

### Kích thước thay đổi theo sự có mặt của khóa

| Trạng thái trước lời gọi | Lời gọi thành công | Thay đổi kích thước |
|---|---|---|
| Chưa có khóa `k` | `put(k, value)` | Tăng 1 |
| Đã có khóa `k` | `put(k, value)` | Không đổi |
| Có ánh xạ của `k` | `remove(k)` | Giảm 1 |
| Không có ánh xạ của `k` | `remove(k)` | Không đổi |
| Bất kỳ | `get(k)` | Không tạo hoặc xóa ánh xạ |

Quy tắc kích thước vẫn đúng khi giá trị của một ánh xạ là `null`, miễn hiện thực cho phép giá trị đó. Kích thước đếm số ánh xạ, không đếm số giá trị khác `null`.

Không nên mở rộng hàng cuối thành phát biểu “`get` không bao giờ có tác động nào trong mọi map”. Chẳng hạn, `LinkedHashMap` có chế độ riêng sắp xếp theo truy cập, trong đó `get` có thể làm thay đổi thứ tự truy cập. Điều được dùng ở đây là `get` không tự tạo hay xóa ánh xạ.

## 4. Đọc đúng kết quả của put và remove

### 4.1. Giá trị cũ không phải mã thành công

Với `Map<String, Boolean>`, `put` có thể trả `false` vì giá trị cũ thật sự là `Boolean.FALSE`. Điều đó không có nghĩa phép ghi thất bại. Với `Map<String, Integer>`, kết quả `0` cũng chỉ là số nguyên cũ.

Đoạn sau là **mã minh họa**, giả sử `flags` là một `Map<String, Boolean>` sửa được:

```java
flags.put("enabled", false);
Boolean previous = flags.put("enabled", true);
// previous là Boolean.FALSE; flags.get("enabled") là Boolean.TRUE.
```

Tương tự, `remove` trả `false` từ một `Map<String, Boolean>` là có thể đã xóa một ánh xạ mang giá trị `false`. Hãy đọc kiểu dữ liệu và chữ ký phương thức trước khi diễn giải một kết quả như cờ thành công.

Khi phương thức hoàn tất bình thường, hiệu ứng được hiểu theo hợp đồng của nó. Một số tình huống không hợp lệ được báo bằng ngoại lệ; đừng tự quy ước `null`, `0` hay `false` thành “thất bại” nếu API không định nghĩa như vậy.

### 4.2. Khóa bằng nhau có thể là cùng một khóa logic

Trong các ví dụ dùng `HashMap` hoặc `LinkedHashMap`, việc xác định khóa tương ứng dựa trên hợp đồng `equals` và `hashCode`, không yêu cầu hai tham chiếu phải là cùng một đối tượng.

Đoạn minh họa:

```java
Map<String, Integer> counts = new HashMap<>();
counts.put("pen", 10);
Integer old = counts.put(new String("pen"), 15);
// old = 10, counts.size() = 1, counts.get("pen") = 15.
```

`new String("pen")` tạo đối tượng chuỗi khác nhưng vẫn bằng `"pen"` theo `equals`. Vì vậy phép `put` thứ hai thay giá trị của khóa logic đang có. Mục đích ví dụ là phân biệt “đối tượng khóa mới được cấp phát” với “khóa logic chưa có trong map”; trong mã thông thường không cần tạo chuỗi bằng `new String` như vậy.

Bài này dùng khóa `String` bất biến để tập trung vào thao tác. Với khóa tự định nghĩa, tính đúng đắn của `equals`, `hashCode` và việc không làm biến đổi thông tin xác định khóa khi khóa đang nằm trong map là tiền đề riêng cần giữ. Không nên chẩn đoán mọi ca `get` không thấy dữ liệu là lỗi ở giá trị trả về của `put`.

### 4.3. Giá trị trả về là tham chiếu cũ khi V là kiểu tham chiếu

Nếu `V` là một đối tượng khả biến, `put` hoặc `remove` không tự tạo ảnh chụp sâu của đối tượng đó. Chúng trả tham chiếu giá trị cũ. Thay ánh xạ trong map cũng không hủy đối tượng cũ đang được nơi khác tham chiếu.

Ví dụ, nếu giá trị cũ là một `ArrayList<String>`, biến nhận từ `put` vẫn có thể trỏ đến danh sách đó sau khi map đã gắn khóa với một danh sách mới. Danh sách cũ không còn là giá trị của khóa ấy trong map, nhưng vẫn là một đối tượng tồn tại bình thường nếu còn tham chiếu tới nó.

Vì vậy “lưu giá trị cũ” và “lưu bản chụp không thay đổi của dữ liệu cũ” là hai nhu cầu khác nhau. Nhu cầu thứ hai có thể cần sao chép dữ liệu theo cấu trúc và quy tắc của ứng dụng.

## 5. Null: phân biệt không có khóa và có khóa mang giá trị null

`HashMap` và `LinkedHashMap` cho phép giá trị `null`. Với những map này, có ít nhất ba trạng thái cần phân biệt:

| Trạng thái của khóa `k` | `get(k)` | `containsKey(k)` |
|---|---|---|
| Không có ánh xạ | `null` | `false` |
| Có ánh xạ `k → null` | `null` | `true` |
| Có ánh xạ `k → "ready"` | `"ready"` | `true` |

Do đó câu hỏi “giá trị đọc được có khác `null` không?” khác câu hỏi “map có chứa khóa này không?”.

### 5.1. put(k, null) không phải remove(k)

Nếu map cho phép giá trị `null`:

- `put(k, null)` giữ hoặc tạo ánh xạ của `k`; sau đó `containsKey(k)` là `true`.
- `remove(k)` bỏ ánh xạ; sau đó `containsKey(k)` là `false`.

Một ứng dụng có thể chủ động cấm giá trị `null` để giảm số trạng thái phải xử lý, nhưng đó là chính sách của ứng dụng. Không được suy từ việc `get(k)` trả `null` rằng thao tác xóa đã xảy ra.

### 5.2. getOrDefault không thay mọi null bằng giá trị mặc định

`getOrDefault(k, fallback)` dùng `fallback` khi **không có ánh xạ** của `k`. Nếu có ánh xạ với giá trị `null`, kết quả vẫn có thể là `null`.

Phương thức này cũng không chèn `fallback` vào map. Nếu cần một thao tác tính và lưu giá trị khi thiếu, đó là một nhu cầu khác với đọc giá trị mặc định; không nên mong `get` hay `getOrDefault` âm thầm thực hiện nó.

## 6. Thí nghiệm đầy đủ: hai ý nghĩa của kết quả null

Chương trình độc lập dưới đây dùng `HashMap<String, String>`. Nó chỉ in các giá trị tra cứu và kích thước, nên kết quả không phụ thuộc thứ tự duyệt của `HashMap`.

```java
import java.util.HashMap;
import java.util.Map;

public class MapNullAmbiguityDemo {
    public static void main(String[] args) {
        Map<String, String> states = new HashMap<>();

        System.out.println("firstPutOld=" + states.put("draft", null));
        System.out.println("draftValue=" + states.get("draft"));
        System.out.println("missingValue=" + states.get("missing"));
        System.out.println("hasDraft=" + states.containsKey("draft"));
        System.out.println("hasMissing=" + states.containsKey("missing"));
        System.out.println("draftOrDefault="
                + states.getOrDefault("draft", "fallback"));
        System.out.println("missingOrDefault="
                + states.getOrDefault("missing", "fallback"));

        boolean existedBeforePut = states.containsKey("draft");
        String oldOnReplace = states.put("draft", "ready");
        System.out.println("existedBeforePut=" + existedBeforePut);
        System.out.println("oldOnReplace=" + oldOnReplace);
        System.out.println("current=" + states.get("draft"));

        System.out.println("oldBeforeNull=" + states.put("draft", null));
        System.out.println("sizeWithNull=" + states.size());

        boolean existedBeforeRemove = states.containsKey("draft");
        String removedOld = states.remove("draft");
        System.out.println("existedBeforeRemove=" + existedBeforeRemove);
        System.out.println("removedOld=" + removedOld);
        System.out.println("hasDraftAfterRemove=" + states.containsKey("draft"));
        System.out.println("sizeAfterRemove=" + states.size());
        System.out.println("removeMissing=" + states.remove("draft"));
    }
}
```

Lưu mã vào `MapNullAmbiguityDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapNullAmbiguityDemo.java
java MapNullAmbiguityDemo
```

Kết quả chuẩn:

```text
firstPutOld=null
draftValue=null
missingValue=null
hasDraft=true
hasMissing=false
draftOrDefault=null
missingOrDefault=fallback
existedBeforePut=true
oldOnReplace=null
current=ready
oldBeforeNull=ready
sizeWithNull=1
existedBeforeRemove=true
removedOld=null
hasDraftAfterRemove=false
sizeAfterRemove=0
removeMissing=null
```

### Vì sao oldOnReplace vẫn là null?

Ngay trước `put("draft", "ready")`, map đã có ánh xạ `"draft" → null`. Vì vậy:

- `existedBeforePut` là `true`: khóa có tồn tại.
- `oldOnReplace` là `null`: đó là giá trị cũ thật sự.
- `current` là `"ready"`: đó là giá trị mới.

Nếu chỉ nhìn `oldOnReplace == null` rồi kết luận “đây là lần chèn khóa mới”, chương trình sẽ kết luận sai.

### Vì sao removedOld là null dù đã xóa?

Trước khi xóa, chương trình đã gọi `put("draft", null)`. Ánh xạ vẫn tồn tại, có kích thước `1`. Lời gọi `remove("draft")` lấy ra giá trị cũ `null` và loại bỏ ánh xạ. Kết quả:

- `existedBeforeRemove = true`.
- `removedOld = null`.
- `hasDraftAfterRemove = false`.
- `sizeAfterRemove = 0`.

Lần gọi `remove("draft")` tiếp theo cũng trả `null`, nhưng vì không còn ánh xạ. Hai lần trả cùng giá trị không có nghĩa chúng tạo ra cùng thay đổi.

### Thời điểm kiểm tra sự tồn tại rất quan trọng

Nếu muốn biết khóa có tồn tại **trước khi ghi hoặc xóa**, hãy thu thông tin đó ở trạng thái trước thao tác, như ví dụ dùng `existedBeforePut` và `existedBeforeRemove`.

Kiểm tra `containsKey(k)` sau `put(k, value)` không giúp khôi phục việc trước đó có khóa hay chưa: sau một phép `put` thành công, cả hai trường hợp đều có khóa. Tương tự, sau `remove(k)`, cả trường hợp xóa được lẫn trường hợp vốn không có khóa đều có thể cho `containsKey(k) == false`.

Các ví dụ chạy trong một luồng. Chuỗi “`containsKey` rồi `put`” hoặc “`containsKey` rồi `remove`” là nhiều lời gọi riêng; giữa chúng, một luồng khác có thể thay đổi map nếu không có cơ chế phối hợp thích hợp. Không được xem cặp lời gọi này như một thao tác nguyên tử chỉ vì các dòng mã nằm cạnh nhau. `HashMap` cũng không tự cung cấp đồng bộ cho truy cập thay đổi từ nhiều luồng.

## 7. putAll: chép ánh xạ và xử lý khóa trùng

`target.putAll(source)` đưa các ánh xạ của `source` vào `target`. Có thể hiểu tác động trên từng khóa theo nguyên tắc:

```text
Khóa xuất hiện trong source:
    target nhận giá trị từ source.

Khóa chỉ có trong target:
    target giữ nguyên ánh xạ đó.
```

Ví dụ:

| Khóa | Trong target trước thao tác | Trong source | Trong target sau thao tác |
|---|---|---|---|
| `"pen"` | `10` | `20` | `20` |
| `"notebook"` | `5` | Không có | `5` |
| `"marker"` | Không có | `3` | `3` |

Các hệ quả cần giữ rõ:

- `putAll` không xóa sạch đích rồi mới chép nguồn.
- Khi trùng khóa, giá trị nguồn ghi đè giá trị ở đích.
- Nó không cộng số lượng: `10` và `20` không tự biến thành `30`.
- Nó không hợp nhất sâu các trường của hai đối tượng giá trị.
- Nó không trả một map chứa các giá trị cũ; kiểu trả về là `void`.
- Chỉ những ánh xạ được chép tại thời điểm thao tác mới liên quan; đây không phải việc thiết lập một liên kết đồng bộ tự động giữa hai map.

Nếu nguồn có giá trị `null` và đích cho phép giá trị đó, `null` cũng là giá trị được ghi vào đích khi chép. Nó không có nghĩa “bỏ qua khóa này” hay “xóa khóa khỏi đích”. Nếu đích cấm `null`, cần theo chính sách của hiện thực thay vì suy từ việc nguồn chấp nhận `null`.

### Vì sao tham số có ? extends K và ? extends V?

Chữ ký `putAll(Map<? extends K, ? extends V> source)` cho phép chép từ map có kiểu khóa và kiểu giá trị hẹp hơn nhưng vẫn gán được cho kiểu của đích.

Đoạn minh họa sau cần các import `java.util.Map` và `java.util.LinkedHashMap`:

```java
Map<String, Integer> source = Map.of("pen", 20);
Map<CharSequence, Number> target = new LinkedHashMap<>();
target.putAll(source);
```

`String` là một kiểu con của `CharSequence`, còn `Integer` là một kiểu con của `Number`. Vì `putAll` đọc các khóa và giá trị từ nguồn rồi ghi vào đích, quan hệ kiểu này là phù hợp. Không cần ép kiểu thô hoặc bỏ kiểm tra generic.

Nguồn trong đoạn này được tạo bởi `Map.of` và không cho sửa, nhưng vẫn có thể được **đọc** để chép vào một đích sửa được. Khả năng sửa của nguồn và của đích là hai vấn đề khác nhau: lời gọi tác động lên `target`, không sửa `source`.

## 8. Thí nghiệm đầy đủ: bản sao ánh xạ và đối tượng dùng chung

Chương trình độc lập sau chứng minh hai điều dễ bị lẫn:

1. Thay ánh xạ trong map nguồn sau `putAll` không tự thay ánh xạ tương ứng trong map đích.
2. Nếu cả hai ánh xạ trỏ tới cùng một đối tượng khả biến, thay đổi đối tượng đó có thể được nhìn thấy qua cả hai map.

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MapPutAllSemanticsDemo {
    public static void main(String[] args) {
        Map<String, Integer> target = new LinkedHashMap<>();
        target.put("pen", 10);
        target.put("notebook", 5);

        Map<String, Integer> source = new LinkedHashMap<>();
        source.put("pen", 20);
        source.put("marker", 3);

        Map<String, Integer> before = new LinkedHashMap<>(target);
        target.putAll(source);

        System.out.println("before=" + before);
        System.out.println("target=" + target);
        System.out.println("source=" + source);

        source.put("pen", 99);
        System.out.println("sourcePenAfterChange=" + source.get("pen"));
        System.out.println("targetPenAfterSourceChange=" + target.get("pen"));

        List<String> shared = new ArrayList<>();
        shared.add("A");

        Map<String, List<String>> listSource = new LinkedHashMap<>();
        listSource.put("item", shared);
        Map<String, List<String>> listTarget = new LinkedHashMap<>();
        listTarget.putAll(listSource);

        listSource.get("item").add("B");
        System.out.println("targetAfterSharedListChange="
                + listTarget.get("item"));

        listSource.put("item", new ArrayList<>(List.of("X")));
        System.out.println("sourceAfterRebind=" + listSource.get("item"));
        System.out.println("targetAfterSourceRebind=" + listTarget.get("item"));
        System.out.println("stillSharesOriginal="
                + (listTarget.get("item") == shared));
    }
}
```

Lưu mã vào `MapPutAllSemanticsDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapPutAllSemanticsDemo.java
java MapPutAllSemanticsDemo
```

Kết quả chuẩn:

```text
before={pen=10, notebook=5}
target={pen=20, notebook=5, marker=3}
source={pen=20, marker=3}
sourcePenAfterChange=99
targetPenAfterSourceChange=20
targetAfterSharedListChange=[A, B]
sourceAfterRebind=[X]
targetAfterSourceRebind=[A, B]
stillSharesOriginal=true
```

### 8.1. Vì sao target giữ pen bằng 20?

Ngay sau `target.putAll(source)`, cả hai map có ánh xạ `"pen" → 20`. Sau đó `source.put("pen", 99)` thay riêng ánh xạ trong `source`.

`target` không phải một khung nhìn sống của `source`. Nó vẫn có ánh xạ riêng `"pen" → 20`; vì thế `targetPenAfterSourceChange=20`.

Đối tượng `Integer` là bất biến. Việc “đổi số lượng” trong ví dụ là thay giá trị tham chiếu của ánh xạ, không phải sửa nội dung của một đối tượng số nguyên đã có.

### 8.2. Vì sao thêm B lại nhìn thấy qua listTarget?

Ở phần sau, `listSource` và `listTarget` là hai map khác nhau, nhưng giá trị của khóa `"item"` trong cả hai đều trỏ tới cùng danh sách `shared`:

```text
listSource["item"] ─┐
                   ├──→ shared: [A]
listTarget["item"] ─┘
```

Lời gọi `listSource.get("item").add("B")` sửa **đối tượng danh sách** đang được dùng chung. Nó không thay ánh xạ của khóa `"item"`. Vì vậy đọc danh sách qua `listTarget` cũng thấy `[A, B]`.

Tiếp theo, `listSource.put("item", new ArrayList<>(List.of("X")))` thay riêng ánh xạ ở nguồn:

```text
listSource["item"] ───→ danh sách mới: [X]
listTarget["item"] ───→ shared: [A, B]
```

Map đích vẫn trỏ tới danh sách cũ. Dòng `stillSharesOriginal=true` xác nhận điều đó bằng phép so sánh tham chiếu `==`.

Ba tầng cần phân biệt là:

| Tầng | Câu hỏi cần hỏi |
|---|---|
| Đối tượng map | Hai biến có trỏ tới cùng một map không? |
| Ánh xạ | Một khóa trong từng map đang trỏ tới giá trị nào? |
| Đối tượng giá trị | Các ánh xạ có trỏ tới cùng một đối tượng khả biến không? |

`putAll` chép các ánh xạ; nó không tự sao chép sâu đồ thị đối tượng của khóa và giá trị. Nếu ứng dụng cần hai map có dữ liệu hoàn toàn độc lập, phải xác định cách sao chép phù hợp cho từng loại dữ liệu. Chỉ đổi cú pháp từ `putAll` sang hàm dựng chép map cũng không tự giải quyết mọi tham chiếu dùng chung bên trong.

### 8.3. before là bản chụp ở mức nào?

Trong phần đầu, `new LinkedHashMap<>(target)` tạo một map khác để lưu các ánh xạ trước `putAll`. Do các khóa là `String` và giá trị là `Integer`, đều bất biến trong ví dụ, kết quả `before` hữu ích để quan sát số liệu cũ.

Nếu giá trị là danh sách khả biến, một bản chép map như vậy vẫn có thể dùng chung danh sách. Nó không tự bảo đảm rằng nội dung sâu của dữ liệu cũ sẽ không đổi. Khi cần nhật ký hoặc khả năng hoàn tác chính xác, hãy thiết kế việc lưu trạng thái theo dữ liệu thật thay vì mặc định rằng mọi bản chép map đều là bản sao sâu.

## 9. Khi cần thu giá trị cũ của nhiều khóa

`putAll` trả `void`. Câu lệnh sau **không hợp lệ về kiểu**:

```java
// Minh họa một câu lệnh không biên dịch:
Map<String, Integer> oldValues = target.putAll(source);
```

Vế phải không tạo một giá trị để gán. Nếu cần biết dữ liệu cũ khi ghi hàng loạt, có thể chọn một trong hai cách tùy yêu cầu:

- Chụp những ánh xạ cần thiết trước khi gọi `putAll`.
- Tự duyệt nguồn và gọi `put` cho từng khóa, đồng thời thu kết quả và thông tin tồn tại trước đó.

Ví dụ sau là **đoạn minh họa đặt trong một phương thức**, với `target` và `source` là hai `Map<String, Integer>` khác nhau, nguồn ổn định, đích sửa được, và việc truy cập do một luồng sở hữu:

```java
Map<String, Integer> oldValues = new LinkedHashMap<>();
Map<String, Boolean> existedBefore = new LinkedHashMap<>();

for (Map.Entry<String, Integer> entry : source.entrySet()) {
    String key = entry.getKey();
    boolean existed = target.containsKey(key);
    Integer old = target.put(key, entry.getValue());

    existedBefore.put(key, existed);
    oldValues.put(key, old);
}
```

Đoạn mã dùng `LinkedHashMap` nên cần import tương ứng nếu đưa vào chương trình. Hai map ghi nhận hai loại thông tin:

- `oldValues` ghi giá trị cũ mà `put` trả về.
- `existedBefore` phân biệt khóa chưa có với khóa đã có giá trị `null`.

Nếu ứng dụng cấm mọi giá trị `null`, dữ liệu ghi nhận có thể được đơn giản hóa theo chính sách đó. Nếu ứng dụng cho phép `null`, chỉ giữ `oldValues` có thể chưa đủ để phục hồi trạng thái cũ: “không có ánh xạ” và “có ánh xạ với giá trị `null`” cần cách phục hồi khác nhau.

Đây là ví dụ thu thông tin trong quá trình cập nhật, chưa phải cơ chế giao dịch hay hoàn tác an toàn tổng quát. Nếu có ngoại lệ giữa vòng lặp, một phần dữ liệu có thể đã được ghi. Nếu nhiều luồng cùng sửa, từng lời gọi riêng không tự tạo thành một thao tác nguyên tử cho toàn bộ vòng lặp. Nếu bản thân đối tượng giá trị khả biến, thông tin cũ thu được vẫn có thể là các tham chiếu dùng chung.

### putAll không hứa mọi thay đổi cùng thành công hoặc cùng thất bại

Hợp đồng `Map.putAll` không cung cấp bảo đảm giao dịch cho toàn bộ tập ánh xạ. Chẳng hạn, đích có thể từ chối một khóa hoặc giá trị nào đó. Không nên suy rằng khi ngoại lệ xuất hiện, tất cả ánh xạ trước lời gọi chắc chắn còn nguyên.

Mức thay đổi khi thất bại phụ thuộc hiện thực và quá trình thực hiện. Không cần chứng minh bằng một thứ tự duyệt giả định để áp dụng nguyên tắc này: điều quan trọng là **không có bảo đảm chung để dựa vào**.

Nếu nghiệp vụ yêu cầu xác nhận toàn bộ dữ liệu rồi mới công bố trạng thái mới, cần thiết kế bước kiểm tra, vùng dữ liệu tạm và cách thay trạng thái phù hợp. Nếu map được chia sẻ giữa nhiều luồng, còn cần cơ chế phối hợp tương ứng. Một lệnh `putAll` riêng lẻ không thay cho các yêu cầu đó.

Không nên sửa cấu trúc map nguồn trong lúc đang chép mà không hiểu bảo đảm riêng của hiện thực. Các ví dụ trong bài đều giữ nguồn ổn định trong suốt thao tác.

## 10. Bẫy unboxing: phép ghi đã xong nhưng phép gán vẫn lỗi

`Map<String, Integer>` có kiểu giá trị là `Integer`, không phải `int`. Khi khóa chưa có, `put` có thể trả `null`. Câu lệnh dưới đây chứa thêm bước chuyển `Integer` thành `int`:

```java
// Minh họa một câu lệnh có thể gây NullPointerException:
int old = counters.put("hits", 1);
```

Trình tự liên quan là:

1. Gọi `put("hits", 1)`.
2. Map tạo ánh xạ `"hits" → 1` và trả `null` vì chưa có giá trị cũ.
3. Phép gán cần mở hộp (unboxing) kết quả `Integer` sang `int`.
4. Mở hộp `null` gây `NullPointerException`.

Vì thế ngoại lệ trong câu lệnh này **không chứng minh phép ghi chưa xảy ra**. Nếu bắt ngoại lệ rồi ghi lại một cách mù quáng, chương trình có thể đang xử lý một trạng thái khác với mình nghĩ.

Cách tiếp nhận kết quả đúng khi có thể nhận `null` là dùng kiểu bao:

```java
// Minh họa với counters đã được khởi tạo:
Integer old = counters.put("hits", 1);
if (old != null) {
    int previousCount = old;
    System.out.println(previousCount);
}
```

Nếu còn cần phân biệt “không có khóa” với “có giá trị `null`”, bổ sung thông tin sự tồn tại như phần 6; chỉ đổi từ `int` sang `Integer` giải quyết lỗi mở hộp chứ chưa giải quyết mọi mơ hồ về trạng thái.

Tình huống tương tự xảy ra với `int removed = map.remove("absent")` khi kết quả trả về là `null`. Cũng có thể xảy ra với `boolean previous = flags.put(...)` nếu kết quả `Boolean` là `null`.

## 11. Map không cho sửa và overload remove trả boolean

Cùng tên `remove` nhưng khác danh sách tham số có thể có kiểu trả về và ý nghĩa khác nhau:

| Lời gọi | Kiểu trả về | Ý nghĩa |
|---|---|---|
| `remove(key)` | `V` | Xóa theo khóa và trả giá trị cũ |
| `remove(key, expectedValue)` | `boolean` | Chỉ xóa nếu khóa đang được gắn với giá trị phù hợp; trả việc có xóa được theo điều kiện đó hay không |

Nếu `remove("k", 99)` trả `false`, không nên diễn giải thành “giá trị cũ là false”. Đây là overload trả cờ điều kiện. Ngược lại, `remove("k")` từ một `Map<String, Boolean>` có thể trả giá trị cũ `Boolean.FALSE`. Số lượng đối số và kiểu trả về quyết định cách đọc.

Không được từ sự tồn tại của overload điều kiện suy rằng mọi hiện thực `Map` đều có bảo đảm nguyên tử cho truy cập đồng thời. Đó là hợp đồng cần kiểm tra ở hiện thực hoặc giao diện chuyên biệt được sử dụng.

Một vấn đề khác là khả năng sửa map. Các map tạo bởi `Map.of` không cho phép thêm, xóa hoặc thay ánh xạ. Chúng vẫn là `Map` và vẫn đọc được bằng `get`, nhưng những phép thay đổi không được hỗ trợ.

### Thí nghiệm đầy đủ

Chương trình độc lập sau đồng thời kiểm chứng lỗi mở hộp, map không cho sửa và hai lời gọi `remove` có điều kiện:

```java
import java.util.HashMap;
import java.util.Map;

public class MapReturnPitfallsDemo {
    private static void expectUnsupported(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        Map<String, Integer> counters = new HashMap<>();

        try {
            int old = counters.put("hits", 1);
            System.out.println("unexpectedOld=" + old);
        } catch (NullPointerException expected) {
            System.out.println("firstPutAssignment=NullPointerException");
        }
        System.out.println("hitsExistsAfterException="
                + counters.containsKey("hits"));
        System.out.println("hitsValueAfterException=" + counters.get("hits"));

        try {
            int removed = counters.remove("absent");
            System.out.println("unexpectedRemoved=" + removed);
        } catch (NullPointerException expected) {
            System.out.println("missingRemoveAssignment=NullPointerException");
        }

        Map<String, Integer> readOnly = Map.of("fixed", 1);
        expectUnsupported("readOnlyPut", () -> readOnly.put("fixed", 2));
        expectUnsupported("readOnlyRemove", () -> readOnly.remove("fixed"));
        expectUnsupported("readOnlyPutAll",
                () -> readOnly.putAll(Map.of("extra", 3)));

        Map<String, Integer> values = new HashMap<>();
        values.put("k", 10);
        System.out.println("removeWrongValue=" + values.remove("k", 99));
        System.out.println("valueAfterWrongRemove=" + values.get("k"));
        System.out.println("removeMatchingValue=" + values.remove("k", 10));
        System.out.println("hasKAfterMatchingRemove=" + values.containsKey("k"));
    }
}
```

Lưu mã vào `MapReturnPitfallsDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapReturnPitfallsDemo.java
java MapReturnPitfallsDemo
```

Kết quả chuẩn:

```text
firstPutAssignment=NullPointerException
hitsExistsAfterException=true
hitsValueAfterException=1
missingRemoveAssignment=NullPointerException
readOnlyPut=UnsupportedOperationException
readOnlyRemove=UnsupportedOperationException
readOnlyPutAll=UnsupportedOperationException
removeWrongValue=false
valueAfterWrongRemove=10
removeMatchingValue=true
hasKAfterMatchingRemove=false
```

### Đọc kết quả để tìm đúng nguyên nhân

- `firstPutAssignment=NullPointerException` đi cùng `hitsExistsAfterException=true` và giá trị `1`: lỗi nằm ở bước mở hộp kết quả cũ `null` sau phép ghi.
- `missingRemoveAssignment=NullPointerException`: không có giá trị cũ để mở hộp thành `int`.
- Ba dòng `readOnly...` cho thấy việc sửa các ánh xạ của `Map.of` bị từ chối bằng `UnsupportedOperationException`.
- `removeWrongValue=false` vì giá trị đang có là `10`, không phải `99`; ánh xạ vẫn còn.
- `removeMatchingValue=true` vì điều kiện khớp; sau đó khóa `"k"` không còn trong map.

Hàm `expectUnsupported` không nuốt lỗi im lặng: nó ném `AssertionError` nếu thao tác được mong là không hỗ trợ lại hoàn tất bình thường. Các ví dụ vì vậy thể hiện rõ điều kiện mà chúng kiểm chứng.

Không nên dùng một phép thay đổi không có hiệu ứng, chẳng hạn `putAll` với nguồn rỗng, làm phép thử tổng quát xem map có sửa được không. Hợp đồng cho phép khác biệt ở việc thao tác không được hỗ trợ có ném ngoại lệ khi không tạo thay đổi hay không. Hãy biết cách map được tạo và hợp đồng của hiện thực, thay vì dò khả năng sửa bằng một phép thử mơ hồ.

## 12. Chọn cách xử lý theo thông tin thật sự cần

| Nhu cầu | Cách tiếp cận | Điểm phải kiểm tra |
|---|---|---|
| Đọc giá trị hiện tại | `get(key)` | `null` có thể biểu thị nhiều trạng thái |
| Đọc với giá trị dự phòng khi không có khóa | `getOrDefault(key, fallback)` | Khóa có giá trị `null` không tự nhận dự phòng |
| Xác định có ánh xạ hay không | `containsKey(key)` | Nếu kết hợp với thao tác khác, xét quyền sở hữu và truy cập đồng thời |
| Ghi một khóa và lấy giá trị cũ | Nhận kết quả của `put` vào kiểu `V` phù hợp | Không mở hộp `null`; không nhầm giá trị cũ với giá trị mới |
| Xóa theo khóa và lấy giá trị cũ | `remove(key)` | Giá trị cũ `null` không chứng minh là không có gì bị xóa |
| Xóa chỉ khi giá trị đang khớp | `remove(key, expectedValue)` | Đây là overload trả `boolean`; xem bảo đảm của hiện thực |
| Chép các ánh xạ và chấp nhận nguồn ghi đè | `putAll(source)` | Kiểu trả về `void`; không sao chép sâu; không bảo đảm giao dịch |
| Giữ lịch sử trước nhiều lần ghi | Chụp trạng thái hoặc thu kết quả từng `put` | Ghi nhận sự tồn tại nếu cho phép `null`; xét đối tượng khả biến |

Hãy chọn theo yêu cầu nghiệp vụ trước. Ví dụ, cập nhật tồn kho bằng **giá trị mới đã được tính** phù hợp với `put`; cộng thêm một lượng vào giá trị hiện tại là một bài toán khác. Việc gọi `put` nhiều lần không tự tạo phép cộng hay bảo vệ phép đọc–tính–ghi trước thay đổi đồng thời.

Tương tự, cấu hình “đưa các giá trị mặc định vào những khóa còn thiếu” khác với “để nguồn ghi đè mọi khóa trùng”. Nếu cần giữ giá trị hiện có, không chọn `putAll` rồi hy vọng nó bỏ qua các khóa trùng.

## 13. Lỗi thường gặp và cách đọc lại hợp đồng

### Nhầm put trả giá trị mới

Dấu hiệu: ghi `15` nhưng biến kết quả chứa `10`, rồi cho rằng map không cập nhật.

Cách kiểm tra: xem riêng kết quả trả về và `get` sau thao tác. `10` là dữ liệu cũ; `get` trả `15` là trạng thái mới.

### Nhầm null là thao tác thất bại

Dấu hiệu: lần thêm đầu trả `null`, chương trình báo lỗi hoặc thử lại.

Cách kiểm tra: xem liệu khóa trước đó chưa có, hoặc có giá trị `null`. Một phép ghi thành công có thể trả `null` hoàn toàn đúng hợp đồng.

### Dùng get để kiểm tra tồn tại trong map cho phép null

Dấu hiệu: `get(k) == null` được coi như khóa chắc chắn không có.

Cách kiểm tra: nếu chính sách cho phép giá trị `null`, dùng `containsKey` khi cần phân biệt. Nếu chính sách ứng dụng cấm `null`, cần bảo đảm chính sách đó trên mọi đường ghi chứ không chỉ giả định ở một chỗ đọc.

### Nhầm putAll là giữ dữ liệu cũ ở khóa trùng

Dấu hiệu: sau khi chép cấu hình, giá trị đang có bị thay ngoài mong muốn.

Cách kiểm tra: `source` thắng ở khóa trùng. Muốn giữ giá trị đích phải chọn và viết rõ một quy tắc khác; tên `putAll` không hàm ý “chỉ thêm nếu thiếu”.

### Nhầm chép map là sao chép sâu

Dấu hiệu: hai map khác nhau nhưng sửa danh sách qua một map lại thấy thay đổi qua map kia.

Cách kiểm tra: kiểm tra tham chiếu đối tượng giá trị. Hai ánh xạ độc lập vẫn có thể trỏ tới cùng danh sách.

### Bắt ngoại lệ rồi giả định chưa có tác động

Dấu hiệu: gặp `NullPointerException` ở câu lệnh gán kết quả `put` vào `int`, hoặc gặp ngoại lệ khi cập nhật hàng loạt, rồi tiếp tục dựa trên trạng thái cũ.

Cách kiểm tra: tách bước gọi phương thức và bước xử lý kết quả; kiểm tra trạng thái thực tế theo hợp đồng. Đối với yêu cầu giao dịch, cần thiết kế bảo đảm phù hợp từ trước.

### Quên map có thể không cho sửa

Dấu hiệu: biến khai báo `Map`, `get` chạy được nhưng `put` ném `UnsupportedOperationException`.

Cách kiểm tra: truy nguyên cách tạo đối tượng, chẳng hạn `Map.of`. Kiểu giao diện cung cấp chữ ký phương thức; nó không buộc mọi đối tượng phải hỗ trợ tất cả thao tác thay đổi.

## 14. Tự kiểm tra bằng các chuỗi thao tác nhỏ

Các câu dưới đây giả sử chỉ có một luồng sử dụng map. Mỗi câu là một tình huống độc lập.

### Câu 1: Giá trị cũ và kích thước

Ban đầu `Map<String, Integer> map = new HashMap<>();`. Thực hiện:

```java
Integer a = map.put("x", 4);
Integer b = map.put("x", 9);
Integer c = map.put("x", 9);
```

`a`, `b`, `c`, `map.get("x")` và `map.size()` lần lượt là gì?

**Đáp án:** `null`, `4`, `9`, `9`, `1`. Lần thứ ba ghi cùng số `9` nhưng vẫn trả giá trị cũ `9`.

### Câu 2: Có null rồi xóa

Một `HashMap<String, String>` đang rỗng. Sau `put("x", null)`, gọi `remove("x")` và nhận `null`. Có thể kết luận phép xóa không có tác dụng không?

**Đáp án:** Không. Trước phép xóa đã có ánh xạ `"x" → null`, nên xóa làm kích thước từ `1` thành `0`. Giá trị cũ bị lấy ra là `null`.

### Câu 3: Đọc mặc định

Map có `"x" → null` và không có `"y"`. Kết quả `getOrDefault("x", "N/A")` và `getOrDefault("y", "N/A")` là gì? Sau hai lần đọc có thêm khóa `"y"` không?

**Đáp án:** Lần lượt là `null` và `"N/A"`. Không thêm khóa `"y"`; đọc dự phòng không phải phép ghi.

### Câu 4: putAll trên khóa trùng

Đích có `"a" → 1`, `"b" → 2`; nguồn có `"b" → 8`, `"c" → 3`. Sau `target.putAll(source)`, đích có những ánh xạ nào? Phương thức trả map nào?

**Đáp án:** Đích có `"a" → 1`, `"b" → 8`, `"c" → 3`. Phương thức không trả map nào vì kiểu trả về là `void`. Câu trả lời này nói về tập ánh xạ, không khẳng định thứ tự duyệt của một `Map` bất kỳ.

### Câu 5: Giá trị false

Một `Map<String, Boolean>` có `"enabled" → false`. Gọi `Boolean old = map.put("enabled", true);`. Nếu `old` bằng `false`, phép ghi có thất bại không?

**Đáp án:** Không. `false` là giá trị cũ. Giá trị hiện tại của khóa là `true` nếu lời gọi hoàn tất bình thường. Đây không phải overload trả cờ thành công.

### Câu 6: Ngoại lệ sau phép ghi

Một `HashMap<String, Integer>` rỗng thực hiện `int old = map.put("x", 1);` và phát sinh `NullPointerException`. Sau khi bắt ngoại lệ, `map.containsKey("x")` là gì?

**Đáp án:** `true`, giá trị đang có là `1`. `put` đã hoàn tất rồi kết quả `null` mới bị mở hộp thành `int`, gây ngoại lệ. Sửa kiểu biến nhận thành `Integer` và xử lý trạng thái cần thiết.

### Câu 7: Hai map dùng chung danh sách

Nguồn có `"x"` trỏ tới danh sách `["A"]`. Đích chép bằng `putAll`. Sau đó sửa danh sách cũ bằng `add("B")`, rồi thay riêng ánh xạ nguồn bằng một danh sách mới `["X"]`. Đích đang nhìn thấy danh sách nào?

**Đáp án:** Đích vẫn trỏ tới danh sách cũ `["A", "B"]`. Việc sửa đối tượng dùng chung khác với việc thay ánh xạ trong một map.

### Câu 8: Hai overload remove

Map sửa được đang có `"k" → 10`. Gọi `remove("k", 99)`, rồi `remove("k")`. Hai kết quả có kiểu và giá trị gì?

**Đáp án:** Lời gọi đầu trả `boolean false`, giữ ánh xạ vì giá trị không khớp. Lời gọi sau trả `Integer 10` và xóa ánh xạ. Không được đọc hai kết quả theo cùng một ý nghĩa.

### Câu 9: Nguồn không cho sửa

`source = Map.of("x", 1)`, còn `target = new HashMap<>()`. `target.putAll(source)` có bị từ chối chỉ vì nguồn không cho sửa không?

**Đáp án:** Không. Thao tác đọc nguồn và sửa đích; nguồn này hợp lệ để đọc. Ngược lại, gọi `source.put("x", 2)` tác động lên map không cho sửa và bị từ chối.

### Câu 10: Giữ dấu vết trước một lần xóa

Map cho phép giá trị `null`. Chỉ lưu `V removed = map.remove(k)` có đủ phân biệt “khóa không có” và “khóa có với giá trị `null`” trước phép xóa không?

**Đáp án:** Không. Trong tình huống một luồng sở hữu dữ liệu, có thể ghi thêm `containsKey(k)` trước khi xóa. Nếu có truy cập đồng thời, cần cách phối hợp phù hợp; hai lời gọi không tự tạo một thao tác nguyên tử.

## 15. Thực hành: ghi nhận một lần thay đổi tồn kho

Viết một chương trình dùng `Map<String, Integer>` với khóa mã hàng:

1. Tạo map sửa được và gắn `"pen" → 10`.
2. Ghi số lượng mới `15`, lưu giá trị cũ bằng kiểu `Integer`.
3. In cả giá trị cũ và giá trị hiện tại.
4. Ghi `"notebook" → 7` rồi xóa `"pen"`, in giá trị bị lấy ra.
5. Chép nguồn có `"notebook" → 9`, `"marker" → 3` vào đích.
6. Kiểm tra các ánh xạ cuối bằng `get`, `containsKey` và `size`, không dựa vào thứ tự in của `HashMap`.

Các điều kiện đúng cần đạt:

```text
Giá trị cũ khi thay pen: 10
Giá trị pen ngay sau thay: 15
Giá trị bị lấy ra khi xóa pen: 15
Cuối cùng không còn pen.
Cuối cùng notebook = 9.
Cuối cùng marker = 3.
Kích thước cuối = 2.
```

Sau đó mở rộng theo hai chính sách riêng:

- **Cấm giá trị null:** kiểm tra đầu vào trước khi ghi; giải thích vì sao giá trị trả về `null` khi đó có thể được dùng để nhận biết trước đó chưa có ánh xạ, trong phạm vi chính sách đã được bảo đảm.
- **Cho phép giá trị null:** thêm tình huống một khóa có giá trị `null`; ghi thêm thông tin sự tồn tại trước phép ghi hoặc xóa, rồi phân biệt với khóa chưa có.

Không nên trộn hai chính sách trong cùng suy luận. Một kết luận đúng khi mọi đường ghi cấm `null` có thể sai ngay khi có đường ghi cho phép `null`.

## 16. Những câu hỏi cần tự trả lời trước khi dùng

Trước một thao tác trên map, hãy xác định rõ:

1. Đang cần đọc giá trị hiện tại, thay ánh xạ, xóa ánh xạ hay chép nhiều ánh xạ?
2. Nếu có kết quả trả về, đó là giá trị hiện tại, giá trị cũ hay cờ điều kiện?
3. Map và chính sách ứng dụng có cho phép `null` không?
4. Biến nhận có giữ được `null` hay đang buộc mở hộp thành kiểu nguyên thủy?
5. Map đích có hỗ trợ sửa không?
6. Nếu chép dữ liệu, có chấp nhận nguồn ghi đè khóa trùng và dùng chung đối tượng khả biến không?
7. Nếu phải giữ trạng thái trước đó hoặc phối hợp nhiều luồng, bảo đảm cần thiết đến từ đâu?

Trả lời được các câu hỏi này giúp đọc một dòng `put`, `putAll`, `get` hoặc `remove` theo đúng trạng thái dữ liệu, thay vì suy đoán từ tên biến hay một giá trị `null` đơn lẻ.

## Tài liệu đối chiếu

- [Java 17 — Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): hợp đồng `put`, `putAll`, `get`, `remove`, `getOrDefault`, thao tác tùy chọn và các map không cho sửa.
- [Java 17 — HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): khả năng nhận khóa/giá trị `null`, thứ tự duyệt không được bảo đảm và yêu cầu phối hợp khi nhiều luồng cùng truy cập thay đổi.
- [Java 17 — LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html): thứ tự chèn mặc định, thay giá trị khóa đang có và chế độ thứ tự truy cập.
- [Java Language Specification 17 — Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.8): mở hộp tham chiếu `null` gây `NullPointerException`.

Các chương trình trong bài dùng API tương thích Java 17 trở lên. Mã đã được kiểm chứng bằng `javac --release 17 -encoding UTF-8` và chạy với JDK 24.0.2; các khối kết quả là đầu ra thực tế của bốn chương trình độc lập.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="map-003">containsKey, containsValue và phân biệt vắng khóa với giá trị null</a>

<details>
<summary>Xem chi tiết</summary>

# containsKey, containsValue và phân biệt vắng khóa với giá trị null

## 1. Cần hỏi về khóa hay hỏi về giá trị?

`Map<K, V>` lưu các ánh xạ từ khóa sang giá trị. Khóa cho biết bạn đang tìm thông tin của mục nào; giá trị là dữ liệu được gắn với mục đó. Hai câu hỏi sau có thể cho kết quả khác nhau:

- “Có mục mang khóa `nickname` hay không?”
- “Có mục nào đang chứa giá trị `nickname` hay không?”

Trong một hồ sơ, khóa `nickname` có thể tồn tại nhưng chưa có biệt danh, nên giá trị được lưu là `null`. Việc hồ sơ đã khai báo mục này khác với việc chưa hề có mục đó. Nếu chương trình chỉ nhìn giá trị do `get` trả về, hai tình huống rất dễ bị gộp làm một.

Bài học dùng `HashMap` làm ví dụ chính vì nó cho phép cả khóa `null` và giá trị `null`. Điều đó không phải quy định chung cho mọi lớp triển khai `Map`. Khi dùng một loại bản đồ khác, phải kiểm tra hợp đồng của chính loại đó. [Tài liệu HashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

Sau bài này, bạn cần chọn đúng phép kiểm tra, đọc được ba trạng thái của một khóa và không đánh đồng “không có ánh xạ” với “ánh xạ có giá trị `null`”. Đây là nền tảng khi xử lý cấu hình, dữ liệu nhập một phần, bộ nhớ đệm và kết quả tra cứu có thể rỗng.

## 2. Ba thao tác trả lời ba câu hỏi khác nhau

| Thao tác | Câu hỏi chính | Kết quả |
| --- | --- | --- |
| `containsKey(key)` | Có ánh xạ cho khóa này không? | `boolean` |
| `containsValue(value)` | Có ít nhất một khóa ánh xạ tới giá trị này không? | `boolean` |
| `get(key)` | Giá trị của khóa này là gì? | Một giá trị kiểu V, có thể là `null` |

`containsKey` không yêu cầu giá trị của khóa phải khác `null`. `containsValue` không cho biết khóa nào có giá trị đó và cũng không cho biết có bao nhiêu khóa cùng chứa nó. `get` lấy dữ liệu theo khóa nhưng một mình giá trị trả về có thể chưa đủ để kết luận khóa tồn tại hay vắng mặt. [Hợp đồng các phương thức Map, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

Ví dụ:

~~~java
Map<String, String> profile = new HashMap<>();
profile.put("name", "Lan");
profile.put("nickname", null);
profile.put("empty", "");
~~~

Các phép kiểm tra, trong khi bản đồ không thay đổi:

~~~text
profile.containsKey("nickname")    -> true
profile.containsValue("nickname")  -> false
profile.containsValue("Lan")       -> true
profile.containsKey("Lan")         -> false
profile.get("name")                -> "Lan"
~~~

`"Lan"` là giá trị của khóa `"name"`, không phải một khóa được tạo tự động. Tương tự, tên một khóa không tự xuất hiện trong tập giá trị chỉ vì nó có mặt ở phía khóa.

Việc nhiều khóa cùng ánh xạ tới một giá trị là hợp lệ. Chẳng hạn, hai tài khoản có thể cùng chọn một ngôn ngữ. `containsValue("vi")` chỉ cần tìm thấy một ánh xạ phù hợp là đủ để trả `true`; nó không phải phép kiểm tra giá trị duy nhất.

## 3. Mô hình ba trạng thái của một khóa

Với bản đồ cho phép giá trị `null`, hãy tách thông tin “có ánh xạ không” khỏi “giá trị là gì”:

| Trạng thái của khóa k | `containsKey(k)` | `get(k)` | Cách hiểu |
| --- | --- | --- | --- |
| Vắng khóa | `false` | `null` | Không có ánh xạ cho k |
| Có khóa, giá trị `null` | `true` | `null` | Có ánh xạ nhưng dữ liệu đang là `null` |
| Có khóa, giá trị khác `null` | `true` | Giá trị đã lưu | Có ánh xạ và có đối tượng giá trị |

Hai hàng đầu có cùng kết quả `get(k)`. Do đó:

~~~java
if (profile.get("nickname") == null) {
    // Chưa đủ thông tin để nói rằng khóa nickname không tồn tại.
}
~~~

Đây là đoạn minh họa điều kiện chưa đầy đủ, không phải cách phân loại ba trạng thái.

Nếu cần diễn đạt trực tiếp:

~~~java
if (!profile.containsKey("nickname")) {
    System.out.println("Vang khoa");
} else if (profile.get("nickname") == null) {
    System.out.println("Co khoa, gia tri null");
} else {
    System.out.println("Co khoa, gia tri khac null");
}
~~~

Đoạn minh họa giả sử không có bên khác sửa bản đồ trong lúc kiểm tra. Ở chương trình đầy đủ, ta sẽ gói kết quả thành một kiểu giữ cả cờ tồn tại và giá trị để phía gọi không phải suy đoán.

### Những giá trị rỗng không phải null

Các giá trị sau khác nhau:

~~~text
null       Không trỏ tới một đối tượng String.
""         Có một chuỗi, độ dài bằng 0.
"null"     Có một chuỗi gồm bốn ký tự n, u, l, l.
" "        Có một chuỗi gồm một dấu cách.
~~~

Nếu `profile.put("empty", "")` thì khóa `empty` tồn tại và giá trị khác `null`. Nếu nghiệp vụ muốn coi chuỗi rỗng hoặc toàn khoảng trắng là “chưa nhập”, đó là một quy tắc bổ sung như `isEmpty()` hoặc `isBlank()`; nó không thay đổi kết quả của `containsKey`.

Không dùng `String.valueOf(profile.get(k))` để xác định trạng thái. Cách chuyển đó có thể biến `null` thành chuỗi hiển thị `"null"`, khiến bạn khó phân biệt với giá trị chuỗi `"null"` thật. Chương trình phía sau dùng ký hiệu `<null>` riêng và đặt giá trị chuỗi trong ngoặc vuông khi in để minh chứng khác biệt.

## 4. containsValue(null) không xác định trạng thái của một khóa cụ thể

Giả sử bản đồ có:

~~~text
name     -> "Lan"
nickname -> null
~~~

Khi đó:

~~~text
containsValue(null) -> true
get("missing")     -> null
containsKey("missing") -> false
~~~

Nếu viết:

~~~java
boolean exists =
        profile.get("missing") != null || profile.containsValue(null);
~~~

thì `exists` bằng `true` dù khóa `missing` vắng mặt. Phần `containsValue(null)` chỉ cho biết có một giá trị `null` ở đâu đó, trong trường hợp này là ở khóa `nickname`. Nó không gắn thông tin đó với khóa đang hỏi.

Muốn kiểm tra tồn tại của một khóa, gọi `containsKey` cho chính khóa đó. Muốn biết một khóa đang ánh xạ tới `null`, kết hợp:

~~~java
boolean mappedToNull =
        profile.containsKey("nickname")
        && profile.get("nickname") == null;
~~~

Dấu `&&` giúp chỉ đọc giá trị sau khi xác nhận tồn tại. Tuy nhiên, hai lời gọi vẫn là hai thao tác riêng; biểu thức không tự có tính nguyên tử trong chương trình nhiều luồng.

Chiều suy luận cũng cần rõ: nếu bản đồ không thay đổi và một khóa thực sự ánh xạ tới `null`, `containsValue(null)` của `HashMap` sẽ là `true`. Nhưng từ `containsValue(null) == true` không thể suy ra một khóa bất kỳ đang ánh xạ tới `null`. Đây là khác biệt giữa một khẳng định trên toàn bản đồ và một khẳng định về một khóa cụ thể.

## 5. getOrDefault chỉ thay thế trường hợp vắng khóa

`getOrDefault(k, fallback)` chỉ dùng `fallback` khi **không có ánh xạ** cho khóa `k`. Nếu khóa có mặt nhưng ánh xạ tới `null`, kết quả vẫn là `null`:

~~~java
Map<String, String> profile = new HashMap<>();
profile.put("nickname", null);
System.out.println(profile.getOrDefault("nickname", "Guest")); // null
System.out.println(profile.getOrDefault("missing", "Guest"));  // Guest
~~~

Vì thế, khi câu hỏi là **khóa có tồn tại không**, vẫn phải dùng `containsKey(k)`. Giá trị mặc định cũng không phải bằng chứng rằng khóa đã hoặc chưa tồn tại: giá trị đang lưu có thể tình cờ bằng chính `fallback`.

Bài tiếp theo về `getOrDefault` phân tích sâu các tình huống dễ nhầm: mở hộp `null`, biểu thức mặc định được tính trước lời gọi, mặc định là collection và chính sách thay cả `null` nếu ứng dụng thực sự muốn.
## 6. Optional không tự giữ được ba trạng thái

`Optional.ofNullable(value)` tạo một `Optional` rỗng khi đối số là `null`, và có giá trị khi đối số khác `null`. Nó không biết `null` đến từ khóa vắng hay từ ánh xạ `null`. [Tài liệu Optional.ofNullable, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html#ofNullable(T)).

Do đó:

~~~java
Optional<String> first =
        Optional.ofNullable(profile.get("missing"));
Optional<String> second =
        Optional.ofNullable(profile.get("nickname"));
~~~

cả `first` và `second` đều rỗng. Đây là kết quả đúng theo hợp đồng `Optional` nhưng không đáp ứng yêu cầu giữ ba trạng thái của `Map`.

Có thể dùng `Optional.ofNullable(map.get(k)).orElse("Guest")` nếu nghiệp vụ muốn gộp vắng khóa và giá trị `null`. Đừng giới thiệu biểu thức này như một cách thay thế hoàn toàn cho `containsKey`.

Một kiểu kết quả đơn giản hơn để giữ đầy đủ thông tin:

~~~java
record Lookup<V>(boolean present, V value) {
}
~~~

Khi đó:

~~~text
Lookup(false, null)  -> vắng khóa
Lookup(true, null)   -> có khóa, giá trị null
Lookup(true, "Lan")  -> có khóa, giá trị "Lan"
~~~

Trạng thái `Lookup(false, "Lan")` mâu thuẫn với ý nghĩa đã chọn. Chương trình đầy đủ thêm kiểm tra trong constructor để ngăn tạo trạng thái này.

Hàm đọc có thể tối ưu trường hợp giá trị khác `null`:

~~~java
V value = map.get(key);
boolean present = value != null || map.containsKey(key);
~~~

Nếu `value` khác `null`, đã có đủ thông tin rằng khóa tồn tại trong lần đọc đó, nên phép `||` không gọi `containsKey`. Nếu `value` là `null`, phải hỏi thêm về tồn tại. Cách đọc này có tiền điều kiện bản đồ không bị thay đổi giữa các thao tác; kiểu `record` chỉ làm kết quả rõ ràng, không tự tạo ảnh chụp nguyên tử của một bản đồ đang được sửa.

## 7. Chương trình Java 17+ hoàn chỉnh

Chương trình sau minh chứng cả phép kiểm tra theo khóa và theo giá trị, hai loại `null`, kết quả mặc định, mở hộp, các thao tác dễ hiểu nhầm theo tên “Absent”, và việc lưu kết quả tra cứu không tìm thấy vào bộ nhớ đệm.

`lookup` và `loadCached` dành cho bản đồ được truy cập tuần tự trong ví dụ. Chúng không phải tiện ích an toàn luồng dùng chung cho mọi `Map`. Hàm tải của bộ nhớ đệm được giả định không sửa lại chính bản đồ bộ nhớ đệm trong lúc đang tải.

Lưu thành `MapPresenceDemo.java`:

~~~java
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class MapPresenceDemo {
    record Lookup<V>(boolean present, V value) {
        Lookup {
            if (!present && value != null) {
                throw new IllegalArgumentException("Absent lookup cannot hold a value");
            }
        }

        String state() {
            return !present ? "ABSENT"
                    : value == null ? "PRESENT_NULL" : "PRESENT_VALUE";
        }
    }

    static <K, V> Lookup<V> lookup(Map<K, V> map, K key) {
        Objects.requireNonNull(map, "map");
        V value = map.get(key);
        boolean present = value != null || map.containsKey(key);
        return new Lookup<>(present, value);
    }

    static String display(String value) {
        return value == null ? "<null>" : "[" + value + "]";
    }

    static void printLookup(Map<String, String> map, String key) {
        Lookup<String> result = lookup(map, key);
        System.out.println("key=" + key
                + " present=" + result.present()
                + " value=" + display(result.value())
                + " state=" + result.state());
    }

    static <K, V> V loadCached(
            Map<K, V> cache, K key, Function<? super K, ? extends V> loader) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        V value = loader.apply(key);
        cache.put(key, value);
        return value;
    }

    static void expectNullPointer(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected NullPointerException: " + label);
        } catch (NullPointerException exception) {
            System.out.println(label + "=NullPointerException");
        }
    }

    public static void main(String[] args) {
        Map<String, String> map = new HashMap<>();
        map.put("name", "Lan");
        map.put("nickname", null);
        map.put("empty", "");
        map.put("literal", "null");
        map.put("fallback", "DEFAULT");

        for (String key : List.of("missing", "nickname", "name", "empty", "literal")) {
            printLookup(map, key);
        }
        System.out.println("contains equal nickname="
                + map.containsKey(new String("nickname")));
        System.out.println("contains value Lan="
                + map.containsValue(new String("Lan")));
        System.out.println("contains key Lan=" + map.containsKey("Lan"));
        System.out.println("contains value nickname=" + map.containsValue("nickname"));
        System.out.println("contains value null=" + map.containsValue(null));
        System.out.println("missing get is null=" + (map.get("missing") == null));
        System.out.println("missing key exists=" + map.containsKey("missing"));
        System.out.println("default missing="
                + display(map.getOrDefault("missing", "DEFAULT")));
        System.out.println("default nickname="
                + display(map.getOrDefault("nickname", "DEFAULT")));
        System.out.println("default stored fallback="
                + display(map.getOrDefault("fallback", "DEFAULT")));
        System.out.println("optional missing present="
                + Optional.ofNullable(map.get("missing")).isPresent());
        System.out.println("optional nickname present="
                + Optional.ofNullable(map.get("nickname")).isPresent());

        map.put(null, "root");
        System.out.println("null key exists=" + map.containsKey(null));
        System.out.println("null key value=" + display(map.get(null)));

        Map<String, Integer> numbers = new HashMap<>();
        numbers.put("limit", null);
        System.out.println("integer default missing="
                + numbers.getOrDefault("missing", 10));
        expectNullPointer("unbox null mapping", () -> {
            int value = numbers.getOrDefault("limit", 10);
            System.out.println("unreachable value=" + value);
        });

        Map<String, String> changes = new HashMap<>(map);
        String previous = changes.putIfAbsent("nickname", "Guest");
        System.out.println("putIfAbsent previous=" + display(previous));
        System.out.println("putIfAbsent value=" + display(changes.get("nickname")));

        changes.put("nickname", null);
        int[] computations = {0};
        String computed = changes.computeIfAbsent("nickname", key -> {
            computations[0]++;
            return "Generated";
        });
        System.out.println("compute null mapping=" + display(computed));
        System.out.println("computation count=" + computations[0]);

        changes.put("nickname", null);
        changes.computeIfAbsent("nickname", key -> null);
        System.out.println("null computation keeps null key="
                + changes.containsKey("nickname"));
        changes.computeIfAbsent("new", key -> null);
        System.out.println("null computation creates new key="
                + changes.containsKey("new"));

        String removed = changes.remove("nickname");
        System.out.println("remove null mapping returned=" + display(removed));
        System.out.println("key exists after remove="
                + changes.containsKey("nickname"));
        changes.put("nickname", null);
        System.out.println("conditional remove absent="
                + changes.remove("missing", null));
        System.out.println("conditional remove null="
                + changes.remove("nickname", null));

        Map<String, String> cache = new HashMap<>();
        int[] loads = {0};
        Function<String, String> loader = key -> {
            loads[0]++;
            return null;
        };
        System.out.println("cached first=" + display(loadCached(cache, "user-7", loader)));
        System.out.println("cached second=" + display(loadCached(cache, "user-7", loader)));
        System.out.println("cached miss key exists=" + cache.containsKey("user-7"));
        System.out.println("loader calls=" + loads[0]);

        Map<String, String> concurrent = new ConcurrentHashMap<>();
        System.out.println("concurrent missing=" + display(concurrent.get("missing")));
        expectNullPointer("concurrent put null value",
                () -> concurrent.put("x", null));
        expectNullPointer("concurrent containsKey null",
                () -> concurrent.containsKey(null));
        expectNullPointer("concurrent containsValue null",
                () -> concurrent.containsValue(null));
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 MapPresenceDemo.java
java MapPresenceDemo
~~~

Dùng JDK 17 hoặc mới hơn hỗ trợ phiên bản đích 17. Chương trình sử dụng cú pháp và API tương thích Java 17; kết quả không phụ thuộc thứ tự duyệt của `HashMap` vì từng khóa được kiểm tra theo một danh sách cố định.

Stdout chính xác:

~~~text
key=missing present=false value=<null> state=ABSENT
key=nickname present=true value=<null> state=PRESENT_NULL
key=name present=true value=[Lan] state=PRESENT_VALUE
key=empty present=true value=[] state=PRESENT_VALUE
key=literal present=true value=[null] state=PRESENT_VALUE
contains equal nickname=true
contains value Lan=true
contains key Lan=false
contains value nickname=false
contains value null=true
missing get is null=true
missing key exists=false
default missing=[DEFAULT]
default nickname=<null>
default stored fallback=[DEFAULT]
optional missing present=false
optional nickname present=false
null key exists=true
null key value=[root]
integer default missing=10
unbox null mapping=NullPointerException
putIfAbsent previous=<null>
putIfAbsent value=[Guest]
compute null mapping=[Generated]
computation count=1
null computation keeps null key=true
null computation creates new key=false
remove null mapping returned=<null>
key exists after remove=false
conditional remove absent=false
conditional remove null=true
cached first=<null>
cached second=<null>
cached miss key exists=true
loader calls=1
concurrent missing=<null>
concurrent put null value=NullPointerException
concurrent containsKey null=NullPointerException
concurrent containsValue null=NullPointerException
~~~

### Đọc các nhóm kết quả

Năm dòng đầu là phép phân loại. `missing` và `nickname` đều in `value=<null>` nhưng cờ `present` khác nhau. `empty` in `value=[]` vì chuỗi rỗng vẫn là một giá trị chuỗi. `literal` in `value=[null]` vì đó là bốn ký tự thật.

Hai phép tạo `new String(...)` minh chứng rằng với các khóa và giá trị chuỗi trong `HashMap`, nội dung bằng nhau là đủ; không cần cùng tham chiếu đối tượng. Các dòng `contains key Lan` và `contains value nickname` nhắc rằng khóa và giá trị không hoán đổi vai trò.

Nhóm `default` cho thấy mặc định chỉ dùng cho khóa vắng. Nhóm `optional` cho thấy việc chuyển riêng giá trị trả về của `get` sang `Optional` đã gộp mất hai trạng thái.

`null key exists=true` là một câu chuyện khác với giá trị `null`: khóa `null` trong ví dụ đang ánh xạ tới chuỗi `"root"`. Nhóm mở hộp cho thấy một `getOrDefault` hợp lệ vẫn có thể trả `null` dẫn đến ngoại lệ khi gán vào kiểu nguyên thủy.

Các dòng `putIfAbsent`, `compute` và `remove` sẽ được giải thích theo chính sách trạng thái ở phần sau. Hai dòng `cached` đều trả `null` nhưng `loader calls=1` cho biết lần tra cứu không tìm thấy đã được nhớ lại. Các dòng `concurrent` minh chứng loại bản đồ khác có chính sách `null` khác.


## 8. “Absent” trong tên phương thức không luôn đồng nghĩa containsKey bằng false

`containsKey(k)` chỉ kiểm tra **có ánh xạ hay không**. Nhưng một số phương thức dùng chữ “Absent” lại coi cả ánh xạ tới `null` là trường hợp cần khởi tạo giá trị:

~~~java
Map<String, String> settings = new HashMap<>();
settings.put("theme", null);
System.out.println(settings.containsKey("theme")); // true
settings.putIfAbsent("theme", "light");
System.out.println(settings.get("theme"));         // light
~~~

`computeIfAbsent` cũng có thể gọi hàm để tạo giá trị khi khóa đang ánh xạ tới `null`; nếu hàm trả `null`, phương thức không đặt giá trị mới. Vì vậy, hãy xác định rõ chính sách “thiếu khóa” hay “chưa có giá trị khác null” trước khi chọn API.

Các bài `putIfAbsent, replace và remove có điều kiện` và `computeIfAbsent, computeIfPresent, compute` giải thích riêng thời điểm gọi hàm, giá trị trả về và việc thêm/thay/xóa. Tại đây, điểm cần nhớ là **tên phương thức không thay cho phép kiểm tra hiện diện chính xác bằng `containsKey`**.
## 9. Khóa null và giá trị null là hai trục độc lập

Một ánh xạ có hai vị trí: khóa và giá trị. Đừng thấy `null` ở một vị trí rồi suy luận sang vị trí còn lại.

Với `HashMap`:

~~~java
Map<String, String> map = new HashMap<>();
map.put(null, "root");
~~~

`containsKey(null)` là `true` nhưng `containsValue(null)` là `false` nếu không có ánh xạ `null` nào khác. Giá trị của khóa `null` ở đây là chuỗi `"root"`.

Nếu tiếp tục:

~~~java
map.put(null, null);
~~~

khóa `null` vẫn có mặt, chỉ giá trị được thay. Khi đó `get(null)` trả `null` và `containsKey(null)` vẫn là `true`.

Một `HashMap` không tạo nhiều khóa `null` khác nhau. Ghi tiếp cùng khóa `null` sẽ cập nhật ánh xạ của khóa đó. Trong khi đó, nhiều khóa khác nhau có thể cùng mang giá trị `null`.

Trong thiết kế nghiệp vụ, việc một lớp cho phép `null` không bắt buộc bạn phải cho phép `null` trong dữ liệu ứng dụng. Có thể quy định mọi khóa phải là mã hợp lệ và mọi giá trị phải là một đối tượng mô tả rõ trạng thái. Khi ấy, hãy kiểm tra quy định đó ở mọi đường ghi, thay vì chỉ dựa vào kiểu khai báo `Map<String, V>`.

## 10. Không áp chính sách null của HashMap cho mọi Map

`Map` là giao diện; khả năng nhận khóa hoặc giá trị `null` phụ thuộc triển khai. Một phép hỏi bằng `null` không được hỗ trợ có thể ném ngoại lệ, thay vì luôn trả `false`.

| Loại bản đồ trong Java | Có thể lưu khóa null? | Có thể lưu giá trị null? | Hệ quả liên quan |
| --- | --- | --- | --- |
| `HashMap` | Có | Có | `get(k) == null` có thể là hai trạng thái |
| `ConcurrentHashMap` | Không | Không | `get(k) == null` biểu thị không có ánh xạ trong lần đọc đó, với khóa hợp lệ |
| Bản đồ tạo bởi `Map.of` hoặc `Map.copyOf` | Không | Không | Không dùng chúng để tạo bản đồ có ánh xạ null |

Các nhà máy `Map.of` và `Map.copyOf` còn tạo bản đồ không cho cập nhật; đó là một thuộc tính khác với việc hỗ trợ `null`. Một bản đồ có thể không cho sửa nhưng vẫn bắt nguồn từ một cấu trúc cho phép giá trị `null`, nên không dùng khái niệm “không sửa được” như đồng nghĩa của “không có null”.

Trong `ConcurrentHashMap`, khóa và giá trị `null` bị cấm. Các phép `containsKey(null)` và `containsValue(null)` được minh chứng trong chương trình đều ném `NullPointerException`. [Tài liệu ConcurrentHashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

Với kiểu này, một lần `get(key)` trả `null` có thể được dùng làm dấu hiệu không có giá trị được lưu cho khóa trong lần đọc đó. Điều này không có nghĩa khóa sẽ tiếp tục vắng mặt sau khi một luồng khác cập nhật, và không cho phép dùng khóa `null` để hỏi.

Không chọn `ConcurrentHashMap` chỉ để che một thiết kế trạng thái chưa rõ. Nếu ứng dụng muốn ghi nhớ “đã tra cứu nhưng không tìm thấy”, cần một giá trị khác `null` để diễn đạt trạng thái đó, chẳng hạn một đối tượng kết quả hoặc `Optional.empty()` được lưu như giá trị bản đồ.

Nếu chỉ biết tham số có kiểu `Map<K, V>` mà không biết triển khai hoặc hợp đồng đầu vào, không nên thử `containsValue(null)` rồi giả định lời gọi luôn an toàn. Hãy thiết kế chính sách giá trị được nhận ngay trong API hoặc đọc hợp đồng của đối tượng bản đồ được truyền vào.

## 11. Ứng dụng: cấu hình nhiều tầng và bộ nhớ đệm

### Cấu hình nhiều tầng

Giả sử một lớp cấu hình cục bộ có ba ý nghĩa:

~~~text
Vắng khóa       -> kế thừa cấu hình chung.
Có khóa -> null -> người dùng đã chọn bỏ giá trị kế thừa.
Có khóa -> x    -> dùng giá trị x.
~~~

Nếu viết `Optional.ofNullable(local.get(key)).orElse(globalValue)`, cả vắng khóa và `null` cục bộ đều dùng cấu hình chung. Như vậy thao tác đã làm mất lựa chọn “bỏ giá trị kế thừa”.

Với truy cập tuần tự và các biến đã có sẵn, chính sách đúng có thể là:

~~~java
String resolved = local.containsKey(key)
        ? local.get(key)
        : globalValue;
~~~

Kết quả `null` lúc này có thể là đáp án đúng vì người dùng đã chọn như thế. Không mặc định coi mọi `null` là lỗi cần thay bằng một giá trị khác.

Ngược lại, nếu quy định nghiệp vụ là `null` tương đương chưa thiết lập, cách đọc `get` rồi thay mặc định khi `null` lại phù hợp. Cùng một API có thể được dùng đúng hoặc sai tùy cách bạn mô hình hóa trạng thái.

### Bộ nhớ đệm cho lần tra cứu không tìm thấy

Trong một bộ nhớ đệm, có thể cần phân biệt:

- Vắng khóa: chưa thực hiện lần tra cứu, hoặc kết quả cũ đã được loại khỏi bộ nhớ đệm.
- Có khóa ánh xạ tới `null`: đã tra cứu và không có kết quả.
- Có khóa ánh xạ tới đối tượng: đã tra cứu và có kết quả.

Nếu dùng `cache.get(key) == null` để quyết định tải, trạng thái thứ hai sẽ bị xem như trạng thái thứ nhất, khiến cùng một dữ liệu không tồn tại bị tải lại liên tục.

Hàm `loadCached` trong chương trình dùng `containsKey` trước. Lần đầu tải trả `null` rồi ghi ánh xạ đó vào `HashMap`. Lần thứ hai thấy khóa đã tồn tại nên trả giá trị đã ghi, không gọi bộ tải nữa. Bởi vậy stdout có hai kết quả `<null>` nhưng chỉ một lần tải.

Đó là một chính sách bộ nhớ đệm đơn giản trong một luồng, không phải cơ chế hết hạn hay làm mới hoàn chỉnh. Khi dữ liệu thật xuất hiện sau này, kết quả “không tìm thấy” cũ cần được vô hiệu hóa hoặc hết hạn theo quy định ứng dụng. Ngoại lệ của bộ tải cũng không tự đồng nghĩa với “không tìm thấy”: hàm đã cho không ghi kết quả nếu bộ tải ném ngoại lệ.

Một cách biểu đạt tránh giá trị bản đồ `null`:

~~~java
Map<String, Optional<String>> cache = new HashMap<>();
~~~

Với quy định không bao giờ ghi một `Optional` bằng `null`:

~~~text
Không có ánh xạ                  -> chưa tra cứu.
Có ánh xạ -> Optional.empty()    -> đã tra cứu, không có dữ liệu.
Có ánh xạ -> Optional.of(value)  -> đã tra cứu, có dữ liệu.
~~~

Lưu `Optional.empty()` vẫn là lưu một đối tượng khác `null`. Điều này khác với `Optional.ofNullable(cache.get(key))` trên một bản đồ giá trị chuỗi: ở đây `Optional` là một phần của mô hình dữ liệu trong bản đồ, không phải thao tác cố khôi phục thông tin đã mất sau `get`.

Nếu dùng cách này với `ConcurrentHashMap`, giá trị `Optional.empty()` là hợp lệ vì bản thân nó khác `null`. Việc nạp một lần, xử lý lỗi, hết hạn và cập nhật đồng thời vẫn phải có thiết kế phù hợp; chỉ đổi kiểu giá trị không hoàn thành toàn bộ bài toán.

## 12. Equality, chi phí tra cứu và lựa chọn phép hỏi

### So theo nội dung và hợp đồng khóa

Với các ví dụ `HashMap<String, String>`, `new String("nickname")` có thể dùng để hỏi khóa `"nickname"` đã lưu vì `String.equals` so nội dung. Tương tự, `containsValue(new String("Lan"))` tìm được giá trị chuỗi `"Lan"`.

Không cần so đối tượng khóa bằng `==` để quyết định rằng `containsKey` phải trả `true`. Đối với một khóa tự định nghĩa dùng trong `HashMap`, cần có `equals` và `hashCode` nhất quán; hai đối tượng bằng nhau phải có cùng mã băm. Một trường hợp “nhìn dữ liệu giống nhau nhưng không tìm thấy” có thể bắt nguồn từ hợp đồng khóa bị viết sai, không phải từ giá trị `null`.

Tránh thay đổi những trường tham gia `equals` hoặc `hashCode` của một đối tượng trong lúc nó đang được dùng làm khóa `HashMap`. Việc tra cứu theo khóa bị thay đổi không còn có cơ sở hoạt động như bạn mong muốn. Các ví dụ dùng `String` bất biến để không trộn lỗi thiết kế khóa với bài toán trạng thái cần học.

Những lớp bản đồ chuyên dụng có thể quy định cách nhận diện khóa khác. Vì thế, các kết luận thực hành bằng chuỗi trong bài được gắn với `HashMap`, không phải lời hứa rằng mọi đối tượng mang kiểu `Map` đều có cùng cơ chế tìm kiếm nội bộ.

### containsValue thường đắt hơn containsKey

Với `HashMap` và mã băm phân tán tốt, tra theo khóa có chi phí kỳ vọng hằng số. `containsValue` không sử dụng cùng một chỉ mục băm theo giá trị, nên để kết luận không có giá trị cần tìm, nó có thể phải đi qua nhiều phần tử.

Trong mã nguồn OpenJDK 17, `containsKey` kiểm tra kết quả tìm nút theo khóa; `containsValue` đi qua bảng các vùng băm và các nút trong đó. Ở trường hợp không tìm thấy trên bản đồ khác rỗng, chi phí duyệt có thể phụ thuộc cả dung lượng bảng C và số ánh xạ N, theo O(C + N), không chỉ số ánh xạ. [Mã nguồn HashMap, OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk/jdk-17%2B35/src/java.base/share/classes/java/util/HashMap.java).

Điều này giải thích vì sao không thay `containsKey(k)` bằng một phép duyệt giá trị chỉ vì dữ liệu ví dụ ít. Với nhiều truy vấn theo giá trị, hãy xem lại mô hình tra cứu. Có thể cần thêm một chỉ mục giá trị sang các khóa liên quan, nhưng khi nhiều khóa có cùng giá trị, chỉ mục đảo không thể mặc định chỉ giữ một khóa duy nhất.

Không gán một bảng độ phức tạp chung cho mọi `Map`. Loại bản đồ, cách so khóa, cấu trúc dữ liệu và chi phí `equals`/`hashCode` ảnh hưởng kết quả. Riêng phép `containsValue` cũng có thể dừng sớm khi tìm thấy một giá trị phù hợp; “có thể phải duyệt” không có nghĩa mọi lời gọi đều duyệt hết.

### Chọn phép hỏi đúng nhu cầu

| Nhu cầu | Cách biểu đạt phù hợp |
| --- | --- |
| Chỉ cần biết khóa tồn tại | `containsKey(key)` |
| Chỉ cần biết có giá trị v ở đâu đó | `containsValue(v)`, nếu chính sách dữ liệu cho phép truy vấn v |
| Cần lấy giá trị, chấp nhận gộp vắng và null | `get(key)` |
| Cần mặc định chỉ khi vắng khóa | `getOrDefault(key, defaultValue)` |
| Cần phân biệt cả ba trạng thái | Giá trị cộng cờ tồn tại, như `Lookup` |
| Cần mặc định cho cả vắng và null | Đọc bằng `get` rồi kiểm tra null, hoặc một cách gộp trạng thái được công bố rõ |
| Cần kiểm tra một cặp khóa–giá trị cụ thể | Đọc theo chính khóa đó và so giá trị; xử lý riêng giá trị null và tính đồng thời |

Với nhu cầu cuối, `Objects.equals(map.get(k), expected)` chưa đủ nếu `expected` là `null`: khóa vắng cũng có `get` bằng `null`. Khi bản đồ không thay đổi, cần thêm điều kiện tồn tại để phân biệt cặp `k -> null` với việc không có k.

## 13. Hai phép đọc không tự tạo một ảnh chụp nguyên tử

Hàm `lookup` minh họa mô hình kết quả đúng trong truy cập tuần tự. Nếu có luồng khác sửa bản đồ giữa `get` và `containsKey`, kết quả kết hợp có thể không tương ứng với một trạng thái từng tồn tại.

Một lịch cập nhật minh họa:

~~~text
Ban đầu: không có khóa k.
Luồng A: get(k) nhận null.
Luồng B: put(k, "Lan").
Luồng A: containsKey(k) nhận true.
Luồng A: ghép Lookup(true, null).
~~~

Nếu bản đồ chưa bao giờ chứa `k -> null`, kết quả vừa ghép mô tả một trạng thái không có thật. Ngay cả khi hai thao tác riêng được thực hiện trên một lớp bản đồ an toàn luồng, việc tự ghép hai quan sát vẫn không tự biến thành một thao tác nguyên tử.

Với `HashMap`, không được để nhiều luồng đọc/ghi đồng thời mà thiếu cơ chế phối hợp phù hợp. Nếu thiết kế cho phép giá trị `null` và cần đọc đủ trạng thái trong một vùng nhất quán, mọi bên đọc và ghi liên quan phải tuân theo cùng cơ chế khóa hoặc làm việc trên một ảnh chụp không thay đổi.

Ví dụ về ý tưởng bảo vệ bằng khóa, với các biến đã có sẵn:

~~~java
Lookup<String> result;
synchronized (lock) {
    result = lookup(sharedMap, key);
}
~~~

Điều kiện cần là mọi thao tác ghi ảnh hưởng tới `sharedMap` cũng dùng chính `lock` đó. Chỉ bọc bên đọc, hoặc mỗi bên dùng một đối tượng khóa khác nhau, không tạo ra bảo đảm mong muốn.

Nếu dữ liệu được lưu theo hợp đồng cấm giá trị `null`, một lần `get` có thể đủ để phân biệt vắng và hiện diện. `ConcurrentHashMap` áp dụng chính sách này và hỗ trợ thao tác đồng thời, nhưng vẫn cần đọc hợp đồng của thao tác cập nhật mà bạn sử dụng. Đừng coi một chuỗi tự viết `containsKey -> get -> put` là nguyên tử chỉ vì từng phương thức thuộc một bản đồ an toàn luồng.

Trong ví dụ bộ nhớ đệm, việc hai luồng cùng thấy vắng khóa rồi cùng tải là một vấn đề khác cần xử lý. `containsKey` giải quyết sự phân biệt trạng thái, không tự giải quyết việc tải trùng.

## 14. Lỗi thường gặp

| Cách viết hoặc suy luận | Vì sao chưa đúng | Hướng xử lý |
| --- | --- | --- |
| `get(k) == null` nghĩa là không có khóa | Có thể đã lưu null | Hỏi thêm `containsKey(k)` nếu cần phân biệt |
| `containsValue(null)` cho biết k có null | Chỉ là thông tin trên toàn bản đồ | Kiểm tra chính khóa k |
| `containsKey(k)` nghĩa là `get(k)` khác null | Tồn tại không quyết định giá trị | Tách hai thông tin |
| `getOrDefault` luôn loại null khỏi kết quả | Null đã lưu được trả nguyên | Chọn chính sách mặc định đúng nghiệp vụ |
| So kết quả với giá trị mặc định để biết khóa vắng | Giá trị thật có thể bằng mặc định | Giữ cờ tồn tại riêng |
| Bọc `get` bằng `Optional` sẽ giữ ba trạng thái | Hai nguồn null cùng trở thành rỗng | Dùng kiểu kết quả hoặc mô hình dữ liệu rõ ràng |
| `putIfAbsent` không bao giờ thay khóa đã tồn tại | Giá trị null vẫn có thể bị thay | Xem chính sách của phương thức |
| `remove(k) != null` là cờ đã xóa | Xóa ánh xạ null vẫn trả null | Dùng thao tác có kết quả boolean phù hợp hoặc kiểm tra dưới cùng cơ chế đồng bộ |
| Mọi Map đều nhận truy vấn null và trả false nếu không có | Một số lớp ném ngoại lệ | Kiểm tra chính sách triển khai |
| Đổi sang bản đồ an toàn luồng làm chuỗi kiểm tra trở nên nguyên tử | Nhiều lời gọi vẫn là nhiều quan sát | Thiết kế thao tác nguyên tử hoặc vùng bảo vệ phù hợp |
| Dùng `containsValue` thường xuyên để tra ngược một khóa | Không cho biết khóa, có thể phải duyệt | Thiết kế hướng tra cứu theo nhu cầu |
| Coi chuỗi rỗng, chuỗi "null" và null là một | Chúng có trạng thái đối tượng khác nhau | Chuẩn hóa theo quy tắc nghiệp vụ được nêu rõ |

Không sửa mọi lỗi bằng một lệnh “thay null thành mặc định”. Trước hết phải xác định `null` có phải một trạng thái hợp lệ và có ý nghĩa riêng hay không. Quyết định đó chi phối cách tra cứu, cập nhật, lưu bộ nhớ đệm và trả kết quả cho nơi gọi.

## 15. Bài tập và lời giải

### Bài 1 — Đọc ba trạng thái

Cho `Map<String, Integer> ages = new HashMap<>()`, thêm `ages.put("An", null)` và `ages.put("Binh", 20)`. Hãy điền kết quả của `get` và `containsKey` cho An, Binh, Chi.

**Lời giải:**

| Khóa | get | containsKey | Trạng thái |
| --- | --- | --- | --- |
| An | null | true | Có khóa, giá trị null |
| Binh | 20 | true | Có khóa, giá trị khác null |
| Chi | null | false | Vắng khóa |

Chỉ nhìn cột `get` không phân biệt được An và Chi.

### Bài 2 — Chọn phép kiểm tra theo đúng phía

Với `Map<String, String> roleByUser` có `"u1" -> "ADMIN"`, muốn hỏi có tài khoản u1 hay chưa thì dùng gì? Muốn hỏi đã có ai giữ vai trò ADMIN thì dùng gì?

**Lời giải:** lần lượt là `containsKey("u1")` và `containsValue("ADMIN")`. Phép thứ hai không trả danh sách tài khoản và không cho biết có bao nhiêu tài khoản ADMIN.

### Bài 3 — containsValue(null) có cứu được get không?

Bản đồ có `"a" -> null`. Khóa b chưa có. Biểu thức `map.get("b") != null || map.containsValue(null)` trả gì và có đúng để kiểm tra khóa b không?

**Lời giải:** trả `true` nhưng không đúng. Giá trị `null` thuộc khóa a; khóa b vẫn vắng. Dùng `containsKey("b")`, kết quả `false`.

### Bài 4 — Mặc định cho khóa đã ánh xạ null

Bản đồ có `"timeout" -> null`. Kết quả `map.getOrDefault("timeout", 30)` là gì? Nếu gán thẳng cho `int` thì sao?

**Lời giải:** với `Map<String, Integer>` cho phép null, kết quả là `Integer null`. Gán cho `int` gây `NullPointerException` khi mở hộp. Nếu muốn dùng 30 cho cả vắng khóa và null, đọc vào `Integer` rồi kiểm tra null rõ ràng.

### Bài 5 — Chuỗi rỗng và chuỗi "null"

Bản đồ có `"x" -> ""` và `"y" -> "null"`. Cả hai có phải ánh xạ tới `null` không? `containsKey` trả gì?

**Lời giải:** không. Cả hai đều ánh xạ tới đối tượng `String` khác null, nên `containsKey("x")` và `containsKey("y")` đều là `true`. Muốn coi chúng là dữ liệu chưa nhập cần một quy tắc nghiệp vụ riêng, không sửa nghĩa của Map.

### Bài 6 — Optional bị mất thông tin

Một hàm trả `Optional.ofNullable(map.get(k))`. Nơi gọi có thể biết k đã tồn tại với null hay chưa từng tồn tại không?

**Lời giải:** không; cả hai thành `Optional.empty()`. Nếu cần giữ thông tin đó, trả `Lookup<V>` có cờ tồn tại, hoặc dùng một mô hình dữ liệu trong bản đồ có các trạng thái được định nghĩa rõ.

### Bài 7 — Chính sách không ghi đè null

Khóa `"color"` đã tồn tại với giá trị null và null mang ý nghĩa “không dùng màu”. Có nên gọi `putIfAbsent("color", "blue")` để chỉ bổ sung khi chưa có khóa không?

**Lời giải:** không. Lời gọi có thể thay null bằng blue. Trong truy cập tuần tự, kiểm tra `!containsKey("color")` rồi mới thêm thể hiện chính sách cần giữ. Nếu có cập nhật đồng thời, cần cơ chế bảo vệ cho toàn bộ quyết định và thao tác ghi.

### Bài 8 — Kết quả null của computeIfAbsent

Trên `HashMap`, khóa mới `"new"` chưa tồn tại. `computeIfAbsent("new", k -> null)` có tạo ánh xạ null để lần sau không gọi hàm tính nữa không?

**Lời giải:** không. Không có giá trị khác null được tạo nên không thêm ánh xạ mới. Lần sau vẫn có thể gọi hàm tính. Nếu cần nhớ kết quả không tìm thấy, dùng một giá trị biểu diễn riêng như `Optional.empty()` được lưu thật sự, hoặc một chính sách chứa null có kiểm tra `containsKey` phù hợp.

### Bài 9 — Giá trị cũ khi xóa

Bản đồ có `"a" -> null`. `remove("a")` trả null. Có thể kết luận chưa xóa gì không? Sau đó `containsKey("a")` là gì?

**Lời giải:** không thể kết luận từ giá trị trả về. Ánh xạ null đã bị xóa; trong ví dụ tuần tự, `containsKey("a")` sau đó là `false`. Nếu muốn chỉ xóa một ánh xạ null và nhận cờ thành công, `HashMap.remove("a", null)` trả `true` khi ánh xạ phù hợp còn tồn tại.

### Bài 10 — Hai loại null

Một `HashMap<String, String>` chỉ có `null -> "root"`. Điền `containsKey(null)`, `get(null)` và `containsValue(null)`.

**Lời giải:** lần lượt `true`, `"root"` và `false`. Khóa null không bắt buộc đi kèm giá trị null. Không áp các kết quả này cho `ConcurrentHashMap` vì nó không nhận khóa null.

### Bài 11 — Kiểm tra tính đồng thời

Luồng A đọc `get(k)` thấy null. Luồng B thêm `k -> "Lan"`. Luồng A đọc `containsKey(k)` thấy true và ghép thành “có khóa, giá trị null”. Sai ở đâu?

**Lời giải:** hai kết quả thuộc hai thời điểm khác nhau. Việc ghép chúng không tạo một ảnh chụp nguyên tử. Cần một hợp đồng truy cập nhất quán, chẳng hạn vùng khóa được mọi bên liên quan tuân thủ, hoặc mô hình cấm null với thao tác đọc phù hợp trên cấu trúc đồng thời.

### Bài 12 — Thiết kế một trường cấu hình

Yêu cầu: vắng khóa thì kế thừa; khóa có null thì tắt giá trị; khóa có chuỗi thì dùng chuỗi đó. Viết biểu thức chọn giá trị khi bản đồ không thay đổi. Vì sao `getOrDefault` phù hợp hơn cách `Optional.ofNullable(get).orElse` trong yêu cầu này?

**Lời giải:** có thể dùng `local.getOrDefault(key, inheritedValue)` hoặc `local.containsKey(key) ? local.get(key) : inheritedValue`. Chúng giữ nguyên null đã lưu. Cách bọc riêng `get` bằng `Optional.ofNullable` rồi `orElse` sẽ thay cả null cục bộ bằng giá trị kế thừa, làm mất lựa chọn tắt.

## 16. Tự kiểm tra cách đọc Map

Khi viết một điều kiện, hãy gọi tên thông tin đang cần: tồn tại của khóa, sự xuất hiện của giá trị, hay dữ liệu gắn với một khóa. Ba câu hỏi này dẫn tới ba thao tác khác nhau.

Nếu giá trị null được phép lưu, hãy thử điều kiện trên ít nhất ba trạng thái: vắng khóa, có khóa với null, có khóa với giá trị thật. Thử thêm chuỗi rỗng hoặc giá trị mặc định trùng dữ liệu thật nếu nghiệp vụ có chúng. Khi có nhiều luồng cập nhật, kiểm tra riêng liệu các quan sát có được đọc trong cùng một trạng thái nhất quán hay không.

`containsKey` giữ thông tin về ánh xạ; `get` đưa ra giá trị; `containsValue` hỏi trên phía giá trị của toàn bản đồ. Dùng chúng theo đúng câu hỏi và quyết định ý nghĩa của null ngay trong mô hình dữ liệu sẽ tránh được phần lớn lỗi nhầm vắng khóa với giá trị null.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="map-004">getOrDefault và trường hợp khóa ánh xạ tới null</a>

<details>
<summary>Xem chi tiết</summary>

# getOrDefault và trường hợp khóa ánh xạ tới null

## 1. Mặc định cho “không có khóa”, không phải cho mọi kết quả null

Giả sử một map giữ giới hạn sử dụng của từng tài khoản:

```text
known → 7
unset → null
zero  → 0
```

Tài khoản missing chưa có trong map. Ứng dụng muốn dùng 99 khi chưa có cấu hình:

```java
Integer limit = limits.getOrDefault(account, 99);
```

Đây là đoạn lệnh minh họa trong một phương thức, với `limits` có kiểu `Map<String, Integer>` và `account` là String.

Điểm quyết định là **khóa có tồn tại hay không**:

- known có khóa, nên trả 7.
- unset có khóa, nên trả giá trị đã lưu là null.
- zero có khóa, nên trả 0.
- missing không có khóa, nên trả 99.

Một lỗi phổ biến là đọc tên phương thức thành “nếu get trả null thì trả mặc định”. Cách hiểu đó bỏ mất khác biệt giữa **không có ánh xạ** và **có ánh xạ tới null**.

Bài này giải thích khác biệt đó, tác động đối với kiểu số và collection, thời điểm tạo giá trị mặc định, và cách viết đúng khi nghiệp vụ muốn coi null là thiếu.

## 2. Chữ ký và ba trạng thái cần phân biệt

### 2.1. Giá trị trả về có kiểu V

Chữ ký trong Map:

```java
default V getOrDefault(Object key, V defaultValue)
```

Đây là chữ ký API rút gọn, không phải khai báo một chương trình độc lập. `key` là khóa cần tra; `defaultValue` là giá trị được trả khi không có ánh xạ cho khóa.

Với `Map<String, Integer>`, kết quả là Integer. Đối số `0` hoặc `99` được đóng hộp thành Integer trong ngữ cảnh này. Tên “default” không làm kết quả luôn khác null hoặc tự đổi sang kiểu nguyên thủy.

Phương thức có từ Java 8 nên có sẵn trong Java 17+.

### 2.2. Bảng quyết định

Giả sử map cho phép lưu giá trị null và giá trị mặc định là 99:

| Trạng thái của khóa | get(key) | containsKey(key) | getOrDefault(key, 99) |
| --- | --- | --- | --- |
| Không có khóa | `null` | `false` | `99` |
| Có khóa, giá trị null | `null` | `true` | `null` |
| Có khóa, giá trị 7 | `7` | `true` | `7` |
| Có khóa, giá trị 0 | `0` | `true` | `0` |

Hai dòng đầu có cùng kết quả `get` nhưng khác kết quả `getOrDefault`.

Nếu mặc định cũng là null, cả trường hợp không có khóa và trường hợp ánh xạ tới null đều trả null. Bản thân một giá trị trả về như vậy không mang đủ thông tin để phân biệt hai trạng thái.

Hợp đồng này được mô tả trong [Map.getOrDefault Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#getOrDefault(java.lang.Object,V)).

### 2.3. 0, false và chuỗi rỗng vẫn là giá trị

Không có quy tắc coi 0, false, chuỗi rỗng hoặc danh sách rỗng là “thiếu” trong getOrDefault. Nếu một khóa đã ánh xạ tới những giá trị đó, chúng được trả như giá trị đang có.

Nếu nghiệp vụ muốn “chuỗi rỗng cũng được xem là chưa cấu hình”, đó là một bước kiểm tra riêng sau khi đọc. Không gán thêm ý nghĩa này cho API.

## 3. Cơ chế tư duy: phải giữ thông tin có khóa

Một mô hình tương đương về kết quả trong điều kiện map không bị nơi khác thay đổi:

```java
// Phương thức minh họa đặt trong một lớp, cần import Map.
static <K, V> V readAbsentOnly(Map<K, V> map, K key, V fallback) {
    V value = map.get(key);
    if (value != null || map.containsKey(key)) {
        return value;
    }
    return fallback;
}
```

Nếu đọc được giá trị khác null, đã có kết quả. Nếu đọc được null, cần biết map có giữ khóa không:

```text
get(key)
   ├─ khác null → trả giá trị đó
   └─ null
       ├─ có khóa → trả null đang được lưu
       └─ không có khóa → trả mặc định
```

Đây là cách giải thích ý nghĩa, không phải lời khẳng định mọi lớp Map phải gọi get rồi containsKey đúng số lần như đoạn mã. Lớp cài đặt có thể tối ưu tra cứu trực tiếp theo cấu trúc của nó.

Không thay mô hình trên bằng:

```java
// Chính sách khác: gộp không có khóa và có giá trị null.
V value = map.get(key);
return value != null ? value : fallback;
```

Đoạn này là phần thân phương thức minh họa với các biến đã có. Nó có thể đúng với nghiệp vụ muốn thay cả null, nhưng không có cùng ý nghĩa getOrDefault.

## 4. Phương thức không lưu giá trị mặc định vào map

Với một map chưa có khóa `"missing"`:

```java
// Đoạn lệnh minh họa:
Integer first = limits.getOrDefault("missing", 99);
Integer second = limits.getOrDefault("missing", 88);
```

Kết quả có thể lần lượt là 99 và 88 vì mỗi lần gọi có một mặc định riêng. Không có bước tự lưu 99 để lần sau đọc lại.

Sau các lời gọi đó:

```text
limits.containsKey("missing") vẫn là false
số ánh xạ không tăng
```

Tương tự, getOrDefault không tự thay ánh xạ `unset → null` thành `unset → 99`.

Hãy phân biệt hai mục tiêu:

| Mục tiêu | Tác động cần có |
| --- | --- |
| Đọc với một giá trị dự phòng khi thiếu khóa | Trả giá trị, không lưu dự phòng |
| Khởi tạo hoặc cập nhật dữ liệu trong map | Cần thao tác ghi phù hợp và chính sách riêng |

Các API khởi tạo như `computeIfAbsent` có hợp đồng khác, gồm khả năng ghi và cách xử lý ánh xạ null. Không đổi sang chúng chỉ vì muốn tránh viết điều kiện mà chưa quyết định có được phép thay đổi map hay không.

Cũng không diễn đạt quá rộng rằng lời gọi “không thể gây bất cứ thay đổi nào”. Biểu thức đối số do người viết cung cấp có thể có tác dụng phụ. Với LinkedHashMap cấu hình theo thứ tự truy cập, getOrDefault còn có thể cập nhật thứ tự truy cập; xem [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html). Điểm ở đây là phương thức không tự chèn hoặc thay giá trị ánh xạ bằng mặc định.

## 5. Chương trình chạy: đủ ba trạng thái và lỗi mở hộp

Lưu thành **GetOrDefaultStatesDemo.java**:

```java
import java.util.HashMap;
import java.util.Map;

public class GetOrDefaultStatesDemo {
    public static void main(String[] args) {
        Map<String, Integer> limits = new HashMap<>();
        limits.put("known", 7);
        limits.put("unset", null);
        limits.put("zero", 0);

        System.out.println("get known: " + limits.get("known"));
        System.out.println("get unset: " + limits.get("unset"));
        System.out.println("get missing: " + limits.get("missing"));
        System.out.println("default known: " + limits.getOrDefault("known", 99));
        System.out.println("default unset: " + limits.getOrDefault("unset", 99));
        System.out.println("default zero: " + limits.getOrDefault("zero", 99));
        System.out.println("default missing: " + limits.getOrDefault("missing", 99));
        System.out.println("contains unset: " + limits.containsKey("unset"));
        System.out.println("contains missing: " + limits.containsKey("missing"));

        System.out.println("size after reads: " + limits.size());
        System.out.println("another missing default: "
                + limits.getOrDefault("missing", 88));
        System.out.println("missing with null default: "
                + limits.getOrDefault("missing", null));

        int missingLimit = limits.getOrDefault("missing", 0);
        System.out.println("missing primitive limit: " + missingLimit);
        try {
            int unsetLimit = limits.getOrDefault("unset", 0);
            System.out.println("unset primitive limit: " + unsetLimit);
        } catch (NullPointerException ex) {
            System.out.println("unset primitive limit: " + ex.getClass().getSimpleName());
        }

        System.out.println("still contains unset: " + limits.containsKey("unset"));
        System.out.println("still missing: " + !limits.containsKey("missing"));
        System.out.println("final size: " + limits.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 GetOrDefaultStatesDemo.java
java GetOrDefaultStatesDemo
```

Stdout chính xác:

```text
get known: 7
get unset: null
get missing: null
default known: 7
default unset: null
default zero: 0
default missing: 99
contains unset: true
contains missing: false
size after reads: 3
another missing default: 88
missing with null default: null
missing primitive limit: 0
unset primitive limit: NullPointerException
still contains unset: true
still missing: true
final size: 3
```

Những kết quả cần giải thích:

- get không phân biệt unset với missing chỉ qua kết quả null.
- getOrDefault trả null cho unset nhưng 99 cho missing.
- 0 của zero được giữ nguyên.
- Đọc nhiều lần không tạo khóa missing.
- Mặc định 0 không ngăn lỗi khi giá trị thực đang là null.
- Ánh xạ unset vẫn tồn tại sau khi ngoại lệ mở hộp đã được bắt.

Các chương trình trong bài được biên dịch với `--release 17` và chạy trên OpenJDK 24.0.2. Cờ đó kiểm tra ngôn ngữ/API mục tiêu Java 17 và tạo bytecode tương ứng; JVM chạy ví dụ vẫn là bản đã nêu.

### Vì sao int vẫn có thể nhận NullPointerException?

Trong lệnh:

```java
int value = limits.getOrDefault("unset", 0);
```

phương thức trả Integer null vì khóa unset đang tồn tại với giá trị null. Sau đó Java cố mở hộp Integer thành int. Chính bước mở hộp gây NullPointerException.

Không có quy tắc nào nói “đã truyền mặc định số nguyên thì kết quả chắc chắn khác null”. Trước hết phải chọn chính sách đối với null đã được lưu.

## 6. Nếu muốn thay cả null, hãy viết rõ một chính sách khác

### 6.1. Coi không có khóa và giá trị null đều là thiếu

Một phương thức minh họa:

```java
static <K, V> V readMissingOrNull(Map<K, V> map, K key, V fallback) {
    V value = map.get(key);
    return value != null ? value : fallback;
}
```

Với fallback 99:

| Trạng thái | getOrDefault | readMissingOrNull |
| --- | --- | --- |
| Không có khóa | `99` | `99` |
| Có khóa ánh xạ tới null | `null` | `99` |
| Có khóa ánh xạ tới 7 | `7` | `7` |

Tên phương thức riêng giúp người đọc thấy đây là quyết định của ứng dụng. Nó chỉ trả giá trị theo chính sách mới, không tự ghi thay null trong map.

Nếu fallback vẫn là null, kết quả vẫn có thể null. Muốn một kết quả khác null cần dữ liệu hoặc điều kiện kiểm tra phù hợp, không chỉ một tên phương thức nghe có vẻ an toàn.

### 6.2. Null có thể mang ý nghĩa thật

Trong một cấu hình, có thể quy ước:

```text
không có khóa → kế thừa cấu hình chung
có khóa null → chủ động xóa/không đặt giá trị ở phạm vi này
có giá trị   → dùng giá trị cụ thể
```

Khi đó thay cả null bằng mặc định làm mất ý nghĩa phân biệt của miền dữ liệu. Ngược lại, nếu null chỉ là dữ liệu chưa hoàn thiện và ứng dụng chủ trương coi như thiếu, chính sách thay cả null có thể hợp lý.

Đừng chọn cách xử lý chỉ để ngoại lệ biến mất. Trước hết xác định null có hợp lệ, có chủ đích và khác việc không có khóa hay không.

### 6.3. Không đoán bằng một giá trị đặc biệt

Nếu dùng -1, chuỗi rỗng hoặc một đối tượng mẫu để đánh dấu “không có khóa”, phải chắc rằng giá trị ấy không thể là dữ liệu hợp lệ. Nếu không, ứng dụng lại tạo một sự nhập nhằng khác.

Khi cần biết sự tồn tại, dùng thông tin tồn tại của khóa hoặc một kiểu kết quả mô tả rõ trạng thái. GetOrDefault phù hợp khi chỉ cần một giá trị dự phòng cho trường hợp không có ánh xạ.

## 7. Giá trị mặc định được tính trước khi phương thức chạy

### 7.1. getOrDefault nhận một giá trị, không nhận một lời hứa tính sau

Xét:

```java
String value = settings.getOrDefault("theme", buildDefault());
```

Dù theme đang có giá trị dark, `buildDefault()` vẫn phải được tính để tạo đối số trước khi gọi getOrDefault. Sau đó phương thức có thể trả dark và bỏ qua giá trị đối số đã tính.

Thứ tự tư duy:

```text
tính đối số key
    ↓
tính biểu thức defaultValue
    ↓
gọi getOrDefault với các đối số đã có
    ↓
chọn giá trị đang lưu hoặc giá trị mặc định
```

Quy tắc tính đối số của Java được mô tả trong [JLS 17, mục 15.7.4](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.7.4).

Nếu buildDefault tốn thời gian, ghi log, tăng bộ đếm hoặc ném ngoại lệ, những việc ấy vẫn có thể xảy ra dù map cấu hình đã có giá trị cần đọc. Không đặt một tác vụ có tác dụng phụ vào đối số với suy nghĩ “chỉ chạy khi thiếu”.

### 7.2. Một ngoại lệ ở biểu thức mặc định xảy ra trước việc tra cứu

Nếu biểu thức mặc định ném ngoại lệ, lời gọi phương thức chưa được thực hiện bình thường với một giá trị mặc định hoàn chỉnh. Khóa có sẵn không làm Java bỏ qua lỗi ở đối số.

Không dùng getOrDefault làm cơ chế tránh gọi một dịch vụ dự phòng đắt tiền. Việc kiểm tra cần diễn ra trước khi gọi dịch vụ ấy.

### 7.3. Tạo khi cần và vẫn giữ null đã lưu

Một helper có thể nhận Supplier:

```java
static <K, V> V readAbsentOnlyLazy(
        Map<K, V> map, K key, Supplier<? extends V> fallback) {
    Objects.requireNonNull(fallback);
    V value = map.get(key);
    return value != null || map.containsKey(key) ? value : fallback.get();
}
```

Đây là phương thức minh họa đặt trong một lớp; cần import Map, Objects và `java.util.function.Supplier`.

Lời gọi:

```java
String value = readAbsentOnlyLazy(
        settings, "theme", () -> buildDefault());
```

tạo một đối tượng hành vi để gọi sau. Phần thân lambda `() -> buildDefault()` chỉ được thực hiện khi helper gọi `fallback.get()`.

Helper vẫn giữ null nếu khóa tồn tại. Supplier không tự thay đổi chính sách thiếu dữ liệu; điều kiện trong helper mới quyết định lúc nào dùng nó.

Ví dụ chỉ đọc này giả định map không bị nơi khác sửa giữa các bước. Hai lần tra cứu không được giới thiệu như một giao dịch nguyên tử hoặc một bảo đảm cho nhiều luồng.

## 8. Chương trình chạy: mặc định tính sẵn, tính khi cần và payload thay đổi được

Lưu thành **DefaultEvaluationDemo.java**:

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class DefaultEvaluationDemo {
    private static int builds;

    private static String buildDefault() {
        builds++;
        System.out.println("build-default #" + builds);
        return "fallback-" + builds;
    }

    private static <K, V> V readAbsentOnlyLazy(
            Map<K, V> map, K key, Supplier<? extends V> fallback) {
        Objects.requireNonNull(fallback);
        V value = map.get(key);
        return value != null || map.containsKey(key) ? value : fallback.get();
    }

    private static <K, V> V readMissingOrNull(Map<K, V> map, K key, V fallback) {
        V value = map.get(key);
        return value != null ? value : fallback;
    }

    public static void main(String[] args) {
        Map<String, String> settings = new HashMap<>();
        settings.put("theme", "dark");
        settings.put("note", null);

        System.out.println("eager present: "
                + settings.getOrDefault("theme", buildDefault()));
        System.out.println("eager mapped null: "
                + settings.getOrDefault("note", buildDefault()));
        System.out.println("eager missing: "
                + settings.getOrDefault("language", buildDefault()));
        System.out.println("builds after getOrDefault: " + builds);

        System.out.println("lazy present: "
                + readAbsentOnlyLazy(settings, "theme", () -> buildDefault()));
        System.out.println("lazy mapped null: "
                + readAbsentOnlyLazy(settings, "note", () -> buildDefault()));
        System.out.println("lazy missing: "
                + readAbsentOnlyLazy(settings, "language", () -> buildDefault()));
        System.out.println("builds after lazy reads: " + builds);
        System.out.println("null-as-missing policy: "
                + readMissingOrNull(settings, "note", "untitled"));
        System.out.println("settings size: " + settings.size());
        System.out.println("language stored: " + settings.containsKey("language"));

        Map<String, List<String>> groups = new HashMap<>();
        List<String> fallback = groups.getOrDefault("guest", new ArrayList<>());
        fallback.add("blue");
        System.out.println("modified fallback: " + fallback);
        System.out.println("guest stored: " + groups.containsKey("guest"));

        groups.put("member", new ArrayList<>(List.of("green")));
        List<String> existing = groups.getOrDefault("member", new ArrayList<>());
        existing.add("gold");
        System.out.println("existing stored list: " + groups.get("member"));
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 DefaultEvaluationDemo.java
java DefaultEvaluationDemo
```

Stdout chính xác:

```text
build-default #1
eager present: dark
build-default #2
eager mapped null: null
build-default #3
eager missing: fallback-3
builds after getOrDefault: 3
lazy present: dark
lazy mapped null: null
build-default #4
lazy missing: fallback-4
builds after lazy reads: 4
null-as-missing policy: untitled
settings size: 2
language stored: false
modified fallback: [blue]
guest stored: false
existing stored list: [green, gold]
```

Ở ba lời gọi getOrDefault đầu:

- theme có dark nhưng vẫn có lần dựng mặc định số 1.
- note có null nhưng vẫn có lần dựng số 2; kết quả vẫn null.
- language thiếu nên lần dựng số 3 được dùng làm kết quả.

Ở helper nhận Supplier:

- theme trả dark mà không dựng thêm.
- note trả null mà không dựng thêm.
- language thiếu mới dựng mặc định số 4.

Sau cả hai nhóm, settings vẫn có hai ánh xạ và không có language. Việc tạo khi cần không đồng nghĩa với việc lưu kết quả vào map.

Hàm readMissingOrNull dùng cho note trả untitled, vì hàm ấy chủ trương gộp null với thiếu khóa. Nó là một chính sách khác được gọi có chủ đích.

## 9. Giá trị mặc định là collection: hai lỗi dễ bị che khuất

### 9.1. Thêm vào mặc định không tự tạo ánh xạ

Một cách viết dễ gây hiểu nhầm:

```java
// Đoạn lệnh minh họa, groups chưa có khóa guest:
groups.getOrDefault("guest", new ArrayList<>()).add("blue");
```

Danh sách mới nhận blue, nhưng chưa có lệnh nào gắn danh sách đó với guest trong map. Nếu không giữ tham chiếu hoặc ghi nó vào nơi khác, lần đọc sau guest vẫn thiếu.

Chương trình giữ danh sách mặc định trong biến để quan sát được:

```text
modified fallback: [blue]
guest stored: false
```

Nếu mục tiêu là tạo nhóm khi thiếu rồi thêm thành viên, phải chọn một thao tác khởi tạo/cập nhật có chủ đích. GetOrDefault chỉ giải quyết giá trị trả về của lần đọc.

### 9.2. Khóa đã có danh sách thì trả chính tham chiếu đó

Trong chương trình, member có một ArrayList đang giữ green. GetOrDefault trả chính danh sách đang lưu; thêm gold qua tham chiếu nhận được làm nội dung trong map thành `[green, gold]`.

Phương thức không sao chép sâu giá trị. Vì thế có thể vừa đúng rằng getOrDefault không chèn một ánh xạ mới, vừa đúng rằng mã gọi có thể sửa payload đã được trả ra.

Nếu giá trị là danh sách không cho sửa, lời gọi add có thể báo UnsupportedOperationException. Nếu giá trị đang là null, lời gọi add báo NullPointerException; danh sách mặc định mới tạo không thay thế được null của khóa đã có.

### 9.3. Một mặc định dùng chung không tạo các nhóm độc lập

Nếu dùng cùng một ArrayList làm mặc định cho hai khóa đang thiếu, cả hai lời gọi có thể trả cùng đối tượng ấy, nhưng không tự tạo khóa nào trong map.

Nếu nghiệp vụ muốn mỗi nhóm một danh sách riêng, cần quy trình tạo dữ liệu phù hợp. Không dùng một mặc định dùng chung rồi suy ra mỗi khóa đã sở hữu một bản sao.

## 10. Null phụ thuộc loại map, còn mặc định vẫn là đối số trả về

HashMap cho phép giá trị null, nên cả ba trạng thái trong bảng đầu có thể xảy ra. Xem [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

ConcurrentHashMap không cho lưu khóa hoặc giá trị null. Với một khóa hợp lệ, trường hợp “có khóa ánh xạ tới null” không thuộc trạng thái dữ liệu hợp lệ của lớp đó; xem [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

Không từ đó suy ra defaultValue lúc đọc phải được lưu hoặc phải có mọi ràng buộc của giá trị được chèn. Chẳng hạn một mặc định null có thể được dùng cho khóa hợp lệ đang thiếu mà không chèn null vào ConcurrentHashMap.

Tương tự, một map không cho sửa như Map.of vẫn có thể được đọc với giá trị dự phòng khi thiếu một khóa hợp lệ. GetOrDefault không cần quyền ghi chỉ để trả mặc định.

Khóa không hợp lệ theo lớp map vẫn có thể gây lỗi. Phương thức không phải một lớp bọc bắt mọi NullPointerException hay ClassCastException rồi đổi thành defaultValue. Khi viết mã dùng được với nhiều loại map, đọc chính sách khóa và null của kiểu thực tế.

## 11. Dùng trong một phép cập nhật: phải nhìn cả biểu thức

Một ví dụ đếm trong map không lưu giá trị null:

```java
// Đoạn lệnh minh họa một luồng:
int next = counts.getOrDefault(word, 0) + 1;
counts.put(word, next);
```

Nếu word chưa có, getOrDefault trả 0. Phép cộng cho 1. Lệnh put sau đó mới lưu 1 vào map.

Nếu word đang ánh xạ tới null, phép cộng cần mở hộp null và có thể lỗi trước lệnh put. Có mặc định 0 không tự làm một bộ đếm có null trở thành hợp lệ.

Ngoài ra, đọc–cộng–ghi gồm nhiều bước. Hai luồng cùng chạy không tự được một phép tăng nguyên tử chỉ vì bước đọc dùng getOrDefault. Cần một thiết kế cập nhật phù hợp khi có truy cập đồng thời.

Khi xem một dòng mã dài, tách ba tác động:

```text
tra cứu với chính sách thiếu khóa
    → tính kết quả mới
    → ghi nếu có thao tác ghi rõ ràng
```

Điều này tránh gán tác động của put, phép mở hộp hoặc phương thức tạo mặc định vào nhầm getOrDefault.

## 12. Lỗi phổ biến và cách sửa

| Hiện tượng | Nguyên nhân | Cách sửa suy luận |
| --- | --- | --- |
| Truyền mặc định 99 nhưng nhận null | Khóa đã tồn tại với giá trị null | Chọn rõ có cần thay null hay giữ nó |
| Gán sang int rồi lỗi | Integer trả về null bị mở hộp | Xử lý chính sách null trước bước mở hộp |
| Đọc missing nhưng map vẫn không có khóa | GetOrDefault không lưu mặc định | Thêm thao tác khởi tạo nếu nghiệp vụ cần ghi |
| Dịch vụ tạo mặc định vẫn chạy khi có giá trị | Đối số được tính trước lời gọi | Kiểm tra trước hoặc dùng helper tạo khi cần |
| Mặc định ném lỗi dù khóa tồn tại | Ngoại lệ xảy ra khi tính đối số | Không đặt phép tính cần né vào đối số tính sẵn |
| Thêm vào danh sách mặc định rồi dữ liệu biến mất | Danh sách chưa được gắn với map | Dùng quy trình tạo/ghi nhóm rõ ràng |
| Thêm vào danh sách được trả làm map đổi nội dung | Nhận cùng tham chiếu payload | Sao chép khi cần dữ liệu tách rời |
| Dùng default null nhưng vẫn không biết có khóa | Kết quả null vẫn nhập nhằng | Xem containsKey hoặc trả trạng thái rõ ràng |
| Coi 0/false/rỗng là thiếu | Gán quy tắc nghiệp vụ vào API | Kiểm tra điều kiện ấy riêng |
| Đếm song song vẫn mất cập nhật | Đọc–cộng–ghi không nguyên tử | Chọn cơ chế cập nhật đồng thời phù hợp |

## 13. Bài tập tự kiểm tra có lời giải

### Bài 1: Ba lần đọc

Map chứa A → 5 và B → null; C chưa có. Gọi getOrDefault với mặc định 10 cho từng khóa trả gì? Size sau đó là bao nhiêu?

**Lời giải:** A trả 5, B trả null, C trả 10. Size vẫn là 2; C không được thêm và B không bị thay.

### Bài 2: Mặc định cũng là null

Vẫn map trên, gọi getOrDefault(B, null) và getOrDefault(C, null). Hai kết quả có phân biệt được B với C không?

**Lời giải:** không, cả hai là null. ContainsKey(B) là true, containsKey(C) là false trong trạng thái đang xét.

### Bài 3: Mở hộp

Với Map<String,Integer> chứa B → null, lệnh `int value = map.getOrDefault("B", 0)` có bảo đảm value bằng 0 không?

**Lời giải:** không. Phương thức trả null của B; mở hộp sang int gây NullPointerException. Cần quyết định chính sách đối với null đã lưu.

### Bài 4: Khi nào hàm dựng chạy?

A đã có giá trị. Biểu thức `map.getOrDefault("A", createDefault())` có chạy createDefault không?

**Lời giải:** có, vì Java phải tính đối số trước lời gọi. Việc A có giá trị chỉ ảnh hưởng giá trị nào được phương thức trả, không làm đối số trở thành tính khi cần.

### Bài 5: Giữ null nhưng dựng khi thiếu

Muốn tránh tạo mặc định nếu A có giá trị, đồng thời giữ null khi A tồn tại với null. Chỉ kiểm tra `map.get("A") == null` có đủ không?

**Lời giải:** không. Kết quả null cũng có thể là một ánh xạ hợp lệ đang tồn tại. Cần xem sự tồn tại của khóa như helper readAbsentOnlyLazy, trong điều kiện sử dụng một luồng hoặc một trạng thái đọc được kiểm soát.

### Bài 6: Danh sách mặc định

Groups thiếu khóa G. Lấy `getOrDefault("G", new ArrayList<>())`, thêm X vào kết quả rồi bỏ biến. Map có G không?

**Lời giải:** không. Phép thêm sửa danh sách được tạo, không tạo ánh xạ G → danh sách. Cần thao tác ghi hoặc khởi tạo phù hợp nếu muốn lưu.

### Bài 7: Null như một quyết định cấu hình

Trong ứng dụng, không có khóa nghĩa là kế thừa; có khóa null nghĩa là chủ động không đặt giá trị. Có nên thay getOrDefault bằng “nếu get null thì dùng mặc định” không?

**Lời giải:** không nếu muốn giữ hai ý nghĩa ấy. Cách thay sẽ gộp trạng thái chủ động null với trạng thái thiếu khóa và làm sai chính sách cấu hình.

### Bài 8: Hai mặc định liên tiếp

Map rỗng. Gọi getOrDefault("A", 1) rồi getOrDefault("A", 2). Kết quả có phải 1 rồi 1 vì mặc định đã được ghi nhớ không?

**Lời giải:** không. Kết quả là 1 rồi 2, map vẫn rỗng. Giá trị dự phòng thuộc từng lần gọi, không được tự ghi nhớ.

## 14. Tài liệu đối chiếu

- [Map.getOrDefault Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#getOrDefault(java.lang.Object,V)): ý nghĩa không có ánh xạ.
- [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): trường hợp map cho phép null.
- [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): chính sách không lưu null.
- [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html): tác động của truy cập trong cấu hình thứ tự truy cập.
- [JLS 17, tính đối số từ trái sang phải](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.7.4): thời điểm tính biểu thức mặc định.

Trước khi dùng getOrDefault, hãy xác định rõ “thiếu khóa” có khác “có giá trị null” hay không, giá trị dự phòng có tốn công tạo không, và kết quả có cần được lưu lại không. Ba quyết định đó giúp chọn đúng giữa một phép đọc, một chính sách thay null và một thao tác khởi tạo dữ liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="map-005">keySet, values, entrySet và Map.Entry</a>

<details>
<summary>Xem chi tiết</summary>

# keySet, values, entrySet và Map.Entry

## 1. Một Map, ba cách nhìn cùng các ánh xạ

`Map<K, V>` lưu quan hệ từ khóa sang giá trị. Khi cần xử lý dữ liệu bằng các thao tác của Collection, Java cung cấp ba góc nhìn:

- `keySet()`: nhìn các khóa.
- `values()`: nhìn các giá trị.
- `entrySet()`: nhìn từng cặp khóa–giá trị.

“Góc nhìn” (view) là khái niệm quan trọng: các đối tượng trả về liên kết với bản đồ gốc, thay vì tự sao chép toàn bộ dữ liệu thành một bộ độc lập. Nếu bản đồ thay đổi, góc nhìn phản ánh dữ liệu hiện tại. Một số thao tác qua góc nhìn cũng thay đổi trực tiếp bản đồ. [Hợp đồng các góc nhìn Map, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

Ví dụ về số lượng hàng của các mặt hàng:

~~~text
Map:
a -> 10
b -> 20
c -> 20
z -> null

keySet:  [a, b, c, z]
values:  [10, 20, 20, null]
entrySet:[a=10, b=20, c=20, z=null]
~~~

Mỗi ánh xạ đóng góp một khóa, một lần xuất hiện giá trị và một entry. Vì thế cả ba góc nhìn có số phần tử bằng `map.size()`, dù một số giá trị trùng nhau.

Bài học dùng `LinkedHashMap` mặc định trong chương trình để thứ tự in dễ đối chiếu. Đó là lựa chọn cho ví dụ; các kiểu trả về `Set` và `Collection` không tự bảo đảm thứ tự chèn cho mọi bản đồ.

Sau bài này, bạn cần chọn đúng góc nhìn cho công việc, dự đoán tác động của phép xóa hoặc cập nhật, và phân biệt một góc nhìn, một bản sao chứa tham chiếu, một entry tách rời và một bản sao sâu.

## 2. Kiểu trả về nói gì về dữ liệu?

Các chữ ký chính:

~~~java
Set<K> keySet();
Collection<V> values();
Set<Map.Entry<K, V>> entrySet();
~~~

| Góc nhìn | Một phần tử biểu diễn | Có thể trùng theo ý nghĩa thông thường? | Dùng khi |
| --- | --- | --- | --- |
| `Set<K>` từ `keySet` | Một khóa | Không trùng khóa theo hợp đồng bản đồ | Chỉ cần khóa |
| `Collection<V>` từ `values` | Giá trị của một ánh xạ | Có, nhiều khóa có thể cùng giá trị | Chỉ cần giá trị |
| `Set<Map.Entry<K,V>>` từ `entrySet` | Một cặp khóa–giá trị | Không có hai ánh xạ cho cùng một khóa | Cần cả khóa lẫn giá trị |

`values()` không trả `Set<V>` vì bản đồ cho phép `b -> 20` và `c -> 20` cùng tồn tại. Biến tập giá trị thành một `Set` sẽ loại trùng và làm mất thông tin về số ánh xạ.

Ví dụ:

~~~java
Collection<Integer> values = map.values();
~~~

không đồng nghĩa với:

~~~java
Set<Integer> distinctValues = new HashSet<>(map.values());
~~~

Dòng đầu tạo một biến giữ góc nhìn. Dòng sau tạo một cấu trúc độc lập chứa các giá trị phân biệt tại thời điểm sao chép; nếu có hai ánh xạ cùng giá trị 20, tập mới chỉ chứa một phần tử bằng 20.

Tương tự, `Set<Map.Entry<K,V>>` không phải `Map<K,V>`. Nó là một tập các đối tượng biểu diễn cặp. Muốn đọc phía khóa và phía giá trị của một phần tử, dùng `getKey()` và `getValue()`.

## 3. Chọn cách duyệt theo thông tin thực sự cần

### Chỉ cần khóa

~~~java
for (String key : map.keySet()) {
    System.out.println(key);
}
~~~

Đây là cách tự nhiên khi cần liệt kê tên cấu hình, mã sản phẩm hoặc tài khoản có trong bản đồ.

### Chỉ cần giá trị

~~~java
for (Integer value : map.values()) {
    if (value != null) {
        System.out.println(value);
    }
}
~~~

Mỗi ánh xạ được xét một lần nên giá trị trùng có thể xuất hiện nhiều lần. Trong ví dụ mở đầu, số 20 được duyệt hai lần.

### Cần cả khóa và giá trị

~~~java
for (Map.Entry<String, Integer> entry : map.entrySet()) {
    String key = entry.getKey();
    Integer value = entry.getValue();
    System.out.println(key + " -> " + value);
}
~~~

`entrySet` cho cả hai dữ liệu từ cùng một ánh xạ. Cách duyệt khóa rồi gọi lại `map.get(key)` có thể dùng được trong tình huống đơn giản, nhưng thêm các lượt tra cứu khi bạn vốn đã có thể đọc entry.

Không mặc định đánh giá hai cách có cùng chi phí trên mọi bản đồ. Hơn nữa, trên một `LinkedHashMap` được cấu hình theo thứ tự truy cập, một số phép `get` còn ảnh hưởng thứ tự; chương trình trong bài dùng constructor mặc định theo thứ tự chèn để tránh trộn hai cơ chế.

Với nhu cầu đọc cặp mà không cần đối tượng Entry, có thể dùng:

~~~java
map.forEach((key, value) ->
        System.out.println(key + " -> " + value));
~~~

Dù dùng cách nào, việc thêm hoặc xóa ánh xạ trong lúc duyệt vẫn phải tuân theo quy tắc của cách duyệt đó. Cú pháp lambda không tự làm cập nhật trở nên an toàn.

## 4. Góc nhìn giữ liên kết với bản đồ gốc

Giữ một biến góc nhìn rồi sửa bản đồ:

~~~java
Set<String> keys = map.keySet();
map.put("d", 40);
~~~

`keys` thấy khóa d. Không cần gọi lại `keySet()` để nhìn thấy ánh xạ mới.

Tương tự:

~~~java
Collection<Integer> values = map.values();
map.put("a", 11);
~~~

góc nhìn `values` phản ánh giá trị mới của a. Nó không phải danh sách số lượng đã được chụp ở thời điểm khai báo biến.

Sơ đồ quan hệ:

~~~text
              +--> keySet: nhìn các khóa
Map gốc ------+--> values: nhìn từng giá trị
              +--> entrySet: nhìn các cặp

Ba góc nhìn cùng dựa trên trạng thái Map gốc.
Một phép xóa hợp lệ qua góc nhìn xóa ánh xạ trong Map.
Một phép thay đổi Map được phản ánh trong các góc nhìn.
~~~

Khi gọi `map.clear()`, các góc nhìn đang giữ trở nên rỗng. Nếu sau đó thêm một ánh xạ mới, chính các biến góc nhìn cũ lại nhìn thấy ánh xạ mới. “Rỗng hiện tại” không đồng nghĩa “một bộ rỗng đã tách khỏi nguồn”.

Không kiểm tra `map.keySet() == map.keySet()` rồi dùng kết quả để suy luận về tính liên kết. Việc các lần gọi có trả cùng một đối tượng hay không là chi tiết triển khai; điều cần dựa vào là hợp đồng góc nhìn.

## 5. Xóa qua các góc nhìn: tác động thật lên ánh xạ

Các ví dụ trong phần này áp dụng cho bản đồ sửa được như `LinkedHashMap` trong chương trình. Những bản đồ chỉ đọc có thể từ chối thao tác.

### keySet.remove xóa theo khóa

~~~java
boolean changed = map.keySet().remove("c");
~~~

Nếu khóa c tồn tại, ánh xạ của nó bị xóa và `changed` là `true`. Giá trị của ánh xạ bị xóa theo, vì một Map không giữ một giá trị “mồ côi” không thuộc khóa nào.

Phép này vẫn có thể xóa thành công khi giá trị đang là `null`. Cờ `boolean` nói về việc khóa được loại, không cần dựa vào giá trị cũ.

### values.remove xóa một lần xuất hiện

Giả sử có hai ánh xạ với giá trị 40. Lời gọi:

~~~java
boolean changed = map.values().remove(40);
~~~

xóa một ánh xạ có giá trị bằng 40, nếu có. Nó không thay giá trị 40 thành một khoảng trống và không mặc định xóa mọi ánh xạ có giá trị 40.

Sau khi xóa một lần, số lần xuất hiện 40 giảm từ hai xuống một và kích thước bản đồ giảm một. Nếu việc chọn chính xác khóa nào là quan trọng, đừng dựa vào `values.remove`; hãy xóa theo khóa hoặc theo cặp khóa–giá trị.

Thứ tự duyệt của loại Map cụ thể có thể làm bạn quan sát được một khóa thường bị xóa trước. Đó không phải một hợp đồng “luôn chọn khóa này” dùng được cho mọi Map.

### entrySet.remove xóa theo cả cặp

~~~java
boolean changed = map.entrySet().remove(Map.entry("b", 20));
~~~

Lời gọi chỉ xóa ánh xạ b nếu nó đang có giá trị bằng 20. Nếu b có giá trị 999 thì cặp `b=20` không khớp.

So sánh:

~~~text
Map có b -> 20.

entrySet.remove(entry b=999) -> false, ánh xạ b còn.
entrySet.remove(entry b=20)  -> true, ánh xạ b bị xóa.
~~~

Một entry dùng để dò không cần được lấy từ chính bản đồ đó; chỉ cần là một đối tượng `Map.Entry` biểu diễn cặp cần so. Với cặp có `null`, hãy chọn cách tạo entry hỗ trợ `null`, như `new AbstractMap.SimpleImmutableEntry<>(key, value)`. `Map.entry` không nhận khóa hoặc giá trị `null`.

### clear, removeIf, removeAll và retainAll

`clear()` qua bất kỳ góc nhìn nào của bản đồ sửa được đều loại toàn bộ ánh xạ. Đó không phải thao tác chỉ xóa “đối tượng hiển thị” của góc nhìn.

Các phép xóa hàng loạt cũng cần đọc theo phía được chọn:

~~~java
map.keySet().removeIf(key -> key.startsWith("temp-"));
map.values().removeIf(value -> value == null);
map.entrySet().removeIf(entry ->
        entry.getValue() != null && entry.getValue() < 0);
~~~

Ba đoạn là những ví dụ độc lập về lọc: theo khóa, theo giá trị và theo cặp. `values().removeIf` có thể xóa mọi ánh xạ thỏa điều kiện, khác với một lần `values().remove(value)` chỉ xóa một lần xuất hiện.

`retainAll` giữ lại các phần tử thuộc bộ tiêu chí; mọi ánh xạ có phần tử tương ứng không được giữ sẽ bị loại. Hãy chọn góc nhìn dựa vào ý nghĩa muốn lọc và tránh sửa chính Map từ bên trong điều kiện lọc.

## 6. Vì sao không thêm qua add và addAll?

Ba góc nhìn tiêu chuẩn của Map không hỗ trợ `add` hoặc `addAll`. Để thêm hay cập nhật ánh xạ, dùng thao tác của Map như `put`.

~~~java
map.keySet().add("new");
map.values().add(99);
map.entrySet().add(Map.entry("new", 99));
~~~

Với các góc nhìn trong ví dụ, ba lời gọi trên đều ném `UnsupportedOperationException`.

Về trực giác, thêm riêng một khóa chưa cho biết giá trị; thêm riêng một giá trị chưa cho biết khóa. Riêng một Entry tuy có đủ cặp, hợp đồng `entrySet` vẫn không định nghĩa `add` như một lối thay thế cho `put`. Đừng suy luận khả năng thêm chỉ từ tên kiểu `Set`.

Điểm quan trọng là “liên kết với Map” không đồng nghĩa “có thể thực hiện mọi phép cập nhật theo hai chiều”. Góc nhìn cho phép đọc, và trên bản đồ phù hợp cho phép các đường xóa; việc thêm ánh xạ vẫn đi qua API của Map.

## 7. Map.Entry biểu diễn một cặp như thế nào?

Các phương thức cốt lõi:

~~~java
K getKey();
V getValue();
V setValue(V value);
~~~

`getKey` đọc khóa, `getValue` đọc giá trị. `setValue` là thao tác tùy chọn: nếu được hỗ trợ với entry liên kết đang được dùng đúng phạm vi, nó thay giá trị của ánh xạ và trả về giá trị cũ.

~~~java
for (Map.Entry<String, Integer> entry : map.entrySet()) {
    Integer old = entry.getValue();
    if (old != null) {
        Integer previous = entry.setValue(old + 1);
    }
}
~~~

Biến `previous` giữ giá trị trước cập nhật. Khóa được giữ nguyên. Map.Entry không có phương thức `setKey`; thay khóa là thay đổi định danh ánh xạ, cần một thao tác trên bản đồ với chính sách xử lý khóa đích đã tồn tại.

Không giả định mọi đối tượng có kiểu `Map.Entry` đều ghi được vào Map. Entry từ góc nhìn chỉ đọc, entry tạo bởi `Map.entry` hoặc `SimpleImmutableEntry` có thể không cho `setValue`. Ngược lại, một `SimpleEntry` tự tạo có thể cho thay giá trị của chính nó nhưng không liên kết với một Map nào.

### Phạm vi sống của entry liên kết

Đối tượng entry lấy trong lúc duyệt `entrySet` có liên kết được bảo đảm trong phạm vi duyệt đó. Java 17 không cho một bảo đảm chung rằng có thể giữ nó rồi tiếp tục dùng tùy ý ngoài lần duyệt, hoặc sau khi bản đồ bị thay đổi theo cách không được phép. Đặc biệt, không giữ một entry đã bị xóa rồi gọi `setValue` để mong nó thêm lại ánh xạ. [Hợp đồng Map.Entry, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html).

Vì vậy, hãy đọc khóa/giá trị và cập nhật bằng `setValue` ngay khi đang duyệt. Nếu cần mang cặp đi nơi khác hoặc giữ lâu hơn, tạo một entry tách rời lúc nó còn hợp lệ.

### So sánh bằng nhau xét cả hai phía

Theo hợp đồng Entry thông thường, hai entry bằng nhau khi khóa bằng nhau và giá trị bằng nhau, với so sánh xử lý được `null`. Vì thế `b=20` khác `b=999`, và cũng khác `c=20`.

Điều này giải thích tại sao `entrySet.contains` hoặc `entrySet.remove` phải xét cả cặp. Một mình giá trị bằng nhau không làm hai ánh xạ trở thành cùng một entry.

Đừng dùng `==` để so một entry dò mới tạo với entry trong bản đồ nếu điều muốn kiểm tra là cùng cặp dữ liệu. Hai đối tượng khác nhau vẫn có thể biểu diễn cùng cặp.

## 8. Chương trình Java 17+ hoàn chỉnh

Lưu thành `MapViewsDemo.java`. Chương trình dùng `LinkedHashMap` mặc định theo thứ tự chèn, nên stdout có thứ tự ổn định để đối chiếu. Khả năng giữ thứ tự chèn đến từ loại Map này, không đến từ riêng kiểu `Set` hay `Collection` của góc nhìn. [Tài liệu LinkedHashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

Phần có entry liên kết chỉ đọc và cập nhật entry trong lúc lần duyệt còn đang hoạt động; sau khi thay đổi cấu trúc nguồn, chương trình không dùng lại những entry liên kết đã giữ. Các entry tách rời mới được dùng về sau.

~~~java
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MapViewsDemo {
    static void expectUnsupported(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnsupportedOperationException: " + label);
        } catch (UnsupportedOperationException exception) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    static int countValue(Collection<Integer> values, Integer target) {
        int count = 0;
        for (Integer value : values) {
            if (Objects.equals(value, target)) {
                count++;
            }
        }
        return count;
    }

    public static void main(String[] args) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("a", 10);
        map.put("b", 20);
        map.put("c", 20);
        map.put("z", null);

        Set<String> keys = map.keySet();
        Collection<Integer> values = map.values();
        Set<Map.Entry<String, Integer>> entries = map.entrySet();

        System.out.println("initial keys=" + keys);
        System.out.println("initial values=" + values);
        System.out.println("initial entries=" + entries);
        System.out.println("view sizes=" + keys.size() + ","
                + values.size() + "," + entries.size());

        map.put("d", 40);
        System.out.println("keys after put=" + keys);
        map.put("a", 11);
        System.out.println("values after replace=" + values);

        System.out.println("remove c through keys=" + keys.remove("c"));
        System.out.println("map after key removal=" + map);
        System.out.println("remove mismatched pair="
                + entries.remove(Map.entry("b", 999)));
        System.out.println("remove matching pair="
                + entries.remove(Map.entry("b", 20)));
        System.out.println("contains z-null entry="
                + entries.contains(new AbstractMap.SimpleImmutableEntry<>("z", null)));
        System.out.println("remove null through values=" + values.remove(null));
        System.out.println("map after pair and null removal=" + map);

        expectUnsupported("keys add", () -> keys.add("new"));
        expectUnsupported("values add", () -> values.add(99));
        expectUnsupported("entries add", () -> entries.add(Map.entry("new", 99)));

        map.put("e", 40);
        System.out.println("count 40 before=" + countValue(values, 40));
        System.out.println("remove one 40=" + values.remove(40));
        System.out.println("count 40 after=" + countValue(values, 40));
        System.out.println("size after one value removal=" + map.size());

        Iterator<Map.Entry<String, Integer>> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            Integer value = entry.getValue();
            if (value != null && value < 20) {
                iterator.remove();
            }
        }
        System.out.println("keys after iterator removal=" + keys);
        for (Map.Entry<String, Integer> entry : entries) {
            entry.setValue(entry.getValue() + 1);
        }
        System.out.println("map after setValue=" + map);

        Map<String, Integer> source = new LinkedHashMap<>();
        source.put("p", 1);
        source.put("q", 2);
        Set<String> sourceKeys = source.keySet();
        List<String> keyCopy = new ArrayList<>(sourceKeys);
        List<Map.Entry<String, Integer>> entryReferences = new ArrayList<>();
        List<Map.Entry<String, Integer>> detachedEntries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            detachedEntries.add(new AbstractMap.SimpleImmutableEntry<>(entry));
        }

        Iterator<Map.Entry<String, Integer>> sourceIterator =
                source.entrySet().iterator();
        Map.Entry<String, Integer> first = sourceIterator.next();
        entryReferences.add(first);
        Integer old = first.setValue(100);
        System.out.println("setValue old=" + old);
        System.out.println("source p=" + source.get("p"));
        System.out.println("entry reference value=" + entryReferences.get(0).getValue());
        System.out.println("detached entry value=" + detachedEntries.get(0).getValue());

        source.put("r", 3);
        System.out.println("live keys contain r=" + sourceKeys.contains("r"));
        System.out.println("copied keys contain r=" + keyCopy.contains("r"));
        source.values().clear();
        System.out.println("source size after view clear=" + source.size());
        System.out.println("live key size after clear=" + sourceKeys.size());
        System.out.println("copied keys after clear=" + keyCopy);
        System.out.println("detached entries after clear=" + detachedEntries);
        expectUnsupported("detached setValue",
                () -> detachedEntries.get(0).setValue(9));

        Map<String, Integer> base = new LinkedHashMap<>();
        base.put("u", 7);
        Map<String, Integer> readOnly = Collections.unmodifiableMap(base);
        Set<String> readOnlyKeys = readOnly.keySet();
        base.put("v", 8);
        System.out.println("read-only live keys=" + readOnlyKeys);
        expectUnsupported("read-only key removal",
                () -> readOnlyKeys.remove("u"));
        expectUnsupported("read-only entry setValue",
                () -> readOnly.entrySet().iterator().next().setValue(99));

        Map<String, Integer> fixedMappings = Map.copyOf(base);
        base.put("w", 9);
        System.out.println("read-only sees w=" + readOnly.containsKey("w"));
        System.out.println("copyOf sees w=" + fixedMappings.containsKey("w"));

        keys.clear();
        System.out.println("map size after keys clear=" + map.size());
        System.out.println("all original views empty="
                + (keys.isEmpty() && values.isEmpty() && entries.isEmpty()));
        map.put("f", 50);
        System.out.println("old keys after new mapping=" + keys);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn hỗ trợ phiên bản đích:

~~~shell
javac --release 17 -encoding UTF-8 MapViewsDemo.java
java MapViewsDemo
~~~

Stdout chính xác:

~~~text
initial keys=[a, b, c, z]
initial values=[10, 20, 20, null]
initial entries=[a=10, b=20, c=20, z=null]
view sizes=4,4,4
keys after put=[a, b, c, z, d]
values after replace=[11, 20, 20, null, 40]
remove c through keys=true
map after key removal={a=11, b=20, z=null, d=40}
remove mismatched pair=false
remove matching pair=true
contains z-null entry=true
remove null through values=true
map after pair and null removal={a=11, d=40}
keys add=UnsupportedOperationException
values add=UnsupportedOperationException
entries add=UnsupportedOperationException
count 40 before=2
remove one 40=true
count 40 after=1
size after one value removal=2
keys after iterator removal=[e]
map after setValue={e=41}
setValue old=1
source p=100
entry reference value=100
detached entry value=1
live keys contain r=true
copied keys contain r=false
source size after view clear=0
live key size after clear=0
copied keys after clear=[p, q]
detached entries after clear=[p=1, q=2]
detached setValue=UnsupportedOperationException
read-only live keys=[u, v]
read-only key removal=UnsupportedOperationException
read-only entry setValue=UnsupportedOperationException
read-only sees w=true
copyOf sees w=false
map size after keys clear=0
all original views empty=true
old keys after new mapping=[f]
~~~

### Đối chiếu những thay đổi chính

Ban đầu ba góc nhìn cùng có bốn phần tử, dù giá trị 20 xuất hiện hai lần. Thêm d qua Map làm biến `keys` cũ nhìn thấy d; thay a làm biến `values` cũ nhìn thấy 11.

Xóa c qua khóa loại ánh xạ c. Dò cặp b=999 thất bại nhưng b=20 xóa thành công. Xóa null qua tập giá trị loại ánh xạ z. Các lời gọi `add` được chặn đúng theo hợp đồng góc nhìn.

Khi d và e cùng có giá trị 40, một lần `values.remove(40)` chỉ làm số lần xuất hiện giảm từ hai xuống một. Ví dụ dùng LinkedHashMap nên d được gặp trước, và phần tử còn lại là e. Không chuyển quan sát đó thành lời hứa chọn khóa theo tên trên mọi loại Map.

Xóa a qua chính iterator rồi tăng giá trị bằng `setValue` làm bản đồ còn `{e=41}`. Phần nguồn p, q đối chiếu tham chiếu entry với entry tách rời. Phần chỉ đọc cho thấy “không sửa được qua đường này” không đồng nghĩa “dữ liệu nguồn không bao giờ đổi”. Cuối cùng, xóa qua `keys.clear` làm mọi góc nhìn rỗng, rồi thêm f làm biến `keys` cũ lại nhìn thấy f.


## 9. Thay đổi dữ liệu trong lúc duyệt đúng cách

### Xóa bằng chính iterator đang duyệt

Khi cần loại ánh xạ theo điều kiện kết hợp khóa và giá trị:

~~~java
Iterator<Map.Entry<String, Integer>> iterator =
        map.entrySet().iterator();

while (iterator.hasNext()) {
    Map.Entry<String, Integer> entry = iterator.next();
    Integer value = entry.getValue();
    if (value != null && value < 20) {
        iterator.remove();
    }
}
~~~

`iterator.remove()` xóa phần tử vừa được trả bởi `next()`. Không gọi trước `next` và không gọi hai lần cho cùng một lần `next`. Sau khi xóa, không tiếp tục dùng entry vừa xóa như một ánh xạ còn tồn tại.

Đây là đường xóa được phối hợp với trạng thái của iterator. Trong khi đó, đoạn sau không phải một cách xóa an toàn chung cho các góc nhìn của `HashMap` hoặc `LinkedHashMap`:

~~~java
for (String key : map.keySet()) {
    if (key.startsWith("temp-")) {
        map.remove(key);
    }
}
~~~

Đây là mã minh họa lỗi. Nó xóa trực tiếp qua Map trong lúc một iterator ngầm của vòng `for-each` đang hoạt động. Các iterator phát hiện thay đổi cấu trúc có thể ném `ConcurrentModificationException`, và hợp đồng duyệt không cho bạn dựa vào kết quả của việc sửa ngoài đường được hỗ trợ.

Không viết mã có chủ ý làm sai rồi trông chờ ngoại lệ để quyết định thuật toán. Cơ chế phát hiện thay đổi chỉ là hỗ trợ bắt lỗi, không phải một bảo đảm đồng bộ và không phải lúc nào mọi lỗi cũng được phát hiện.

Muốn lọc gọn trong tình huống thích hợp:

~~~java
map.entrySet().removeIf(entry ->
        entry.getKey().startsWith("temp-"));
~~~

Điều kiện nên chỉ đọc thông tin cần quyết định. Đừng gọi `map.put` hoặc `map.remove` từ trong điều kiện để tạo thêm một đường sửa cấu trúc.

### Đổi giá trị bằng entry đang duyệt

Trên bản đồ hỗ trợ cập nhật, `entry.setValue` trong lúc duyệt entry-set là đường cập nhật giá trị được hợp đồng cho phép. Nó giữ nguyên số ánh xạ và khóa của ánh xạ đang xét.

Không từ đó suy ra rằng mọi lời gọi `map.put` bên trong mọi vòng duyệt đều an toàn. Loại Map, chế độ thứ tự truy cập và thao tác cụ thể ảnh hưởng quy tắc duyệt. Cách đơn giản là dùng đúng API được thiết kế cho công việc: iterator để xóa phần tử hiện tại, entry để thay giá trị hiện tại, hoặc một giai đoạn cập nhật riêng sau khi đã hoàn thành lượt đọc.

### Góc nhìn không tạo ảnh chụp cho nhiều luồng

Gọi `keySet` hay `entrySet` không khóa nguồn và không đóng băng dữ liệu. Nếu Map được chia sẻ giữa các luồng, phải áp dụng cơ chế truy cập phù hợp với loại Map đó.

Tương tự, một vòng sao chép không tự trở thành ảnh chụp nguyên tử của một nguồn đang được sửa. Muốn bản chụp nhất quán, phải lấy dữ liệu dưới cơ chế phối hợp được mọi bên liên quan tuân thủ hoặc dùng một nguồn đã ổn định. Những quy tắc này độc lập với việc biến kết quả có kiểu `List` hay `Set`.

## 10. Góc nhìn, bản sao collection và entry tách rời

Đây là nơi nhiều lỗi thiết kế xảy ra vì tên biến có chữ “copy” nhưng dữ liệu bên trong vẫn có liên kết hoặc dùng chung đối tượng.

### Bản sao khóa hoặc giá trị

~~~java
List<String> keyCopy = new ArrayList<>(map.keySet());
List<Integer> valueCopy = new ArrayList<>(map.values());
~~~

Hai danh sách có cấu trúc riêng. Map thêm hoặc xóa ánh xạ sau đó không tự thêm hoặc xóa phần tử trong danh sách đã sao chép. `valueCopy` vẫn giữ các lần xuất hiện trùng nhau.

Nếu cần tập khóa:

~~~java
Set<String> keyCopy = new HashSet<>(map.keySet());
~~~

Tập mới giữ khóa tại thời điểm sao chép nhưng không hứa thứ tự chèn. Nếu cần giữ thứ tự nguồn khi chụp, có thể chọn `ArrayList` hoặc `LinkedHashSet` phù hợp với yêu cầu.

Việc sao chép này vẫn sao chép tham chiếu tới đối tượng khóa hoặc giá trị. Nếu giá trị là một `ArrayList` có thể sửa, thay đổi chính danh sách giá trị có thể nhìn thấy từ cả hai nơi. Một danh sách kết quả mới không tự sao chép sâu từng đối tượng giá trị.

### Sao chép collection entry chưa tách từng entry

Đoạn sau tạo một danh sách riêng, nhưng không yêu cầu tạo mới từng đối tượng entry:

~~~java
List<Map.Entry<String, Integer>> entries =
        new ArrayList<>(map.entrySet());
~~~

Vì vậy, không coi nó là một bản chụp các cặp ổn định để cất lâu. Nó thu gom các tham chiếu mà nguồn cung cấp; các entry có thể gắn với Map, và việc dùng entry liên kết ngoài phạm vi duyệt không được hợp đồng chung bảo đảm.

Trong chương trình, danh sách `entryReferences` giữ cùng đối tượng `first` đang được dùng trong một lần duyệt còn hoạt động. `first.setValue(100)` làm `entryReferences.get(0).getValue()` cùng thấy 100, bởi hai biến đang trỏ tới cùng một entry. Chương trình không dùng danh sách đó sau khi sửa cấu trúc nguồn.

Ý nghĩa của ví dụ là sao chép tham chiếu không biến đối tượng được trỏ tới thành một đối tượng khác. Không suy ra một cam kết rằng mọi loại Map đều cho phép giữ entry liên kết và quan sát cập nhật tùy ý về sau.

### Tạo mới từng entry để tách khỏi Map

Với dữ liệu có thể có khóa hoặc giá trị null, một cách chụp các cặp:

~~~java
List<Map.Entry<String, Integer>> snapshot = new ArrayList<>();
for (Map.Entry<String, Integer> entry : map.entrySet()) {
    snapshot.add(new AbstractMap.SimpleImmutableEntry<>(entry));
}
~~~

`SimpleImmutableEntry` mới giữ cặp tham chiếu khóa và giá trị đã đọc, không thuộc góc nhìn entry-set của Map. Nó không cho `setValue` trên chính đối tượng entry này. [Tài liệu SimpleImmutableEntry, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.SimpleImmutableEntry.html).

Trong chương trình, entry tách rời p=1 không đổi thành p=100 khi entry đang duyệt được cập nhật. Sau khi Map nguồn bị xóa hết, các entry tách rời vẫn biểu diễn p=1, q=2.

Nếu đã bảo đảm cả hai phía khác null, Java 17 còn có `Map.Entry.copyOf(entry)` để tạo một entry không liên kết với Map, không cho đổi giá trị. `Map.entry(key, value)` tạo cặp trực tiếp cũng có yêu cầu không nhận null.

Nếu muốn một entry độc lập nhưng cho phép đổi giá trị nội bộ, `AbstractMap.SimpleEntry` là một lựa chọn. Gọi `setValue` trên entry tự tạo đó chỉ sửa entry đó, không cập nhật một Map chưa từng liên kết với nó.

### “Không cho setValue” vẫn chưa là sao chép sâu

Ví dụ một entry tách rời có giá trị là `List<String>`. Entry không cho thay tham chiếu giá trị bằng `setValue` nhưng đối tượng danh sách được giữ vẫn có thể cho `add` hoặc `remove`.

Muốn kết quả tách rời tới mức nào, cần quyết định từng lớp:

| Cấu trúc tạo ra | Thêm/xóa ánh xạ sau đó có được phản ánh? | Khóa/giá trị có thể vẫn dùng chung đối tượng? |
| --- | --- | --- |
| Góc nhìn của Map | Có | Có |
| `new ArrayList<>(map.values())` | Không tự phản ánh cấu trúc Map | Có |
| Danh sách các `SimpleImmutableEntry` mới | Không tự phản ánh các cặp mới hoặc giá trị bị thay tham chiếu trong Map | Có |
| Bản sao sâu theo mô hình nghiệp vụ | Theo hợp đồng bản sao được thiết kế | Có thể tách, tùy cách sao chép |

Trong ví dụ chạy được, khóa là `String` và giá trị là `Integer` nên việc dùng chung các đối tượng bất biến không gây thay đổi nội dung bất ngờ. Với dữ liệu có thể sửa, cần thêm bước chuyển sang đối tượng bất biến hoặc sao chép phù hợp; không có một thao tác “copy” chung tự làm hết điều đó.

## 11. Không sửa được và không đổi theo nguồn là hai thuộc tính khác nhau

### Bọc một Map bằng unmodifiableMap

~~~java
Map<String, Integer> readOnly =
        Collections.unmodifiableMap(base);
~~~

`readOnly` là một góc nhìn không cho sửa qua API của nó. Những thay đổi ở `base` vẫn có thể được nhìn thấy qua `readOnly`. Việc sửa qua các góc nhìn collection của lớp bọc cũng bị từ chối. [Tài liệu Collections.unmodifiableMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableMap(java.util.Map)).

Trong chương trình:

~~~text
base ban đầu có u=7.
Tạo readOnly và readOnlyKeys.
base thêm v=8.
readOnlyKeys nhìn thấy [u, v].
~~~

Thử `readOnlyKeys.remove("u")` bị chặn. Thử lấy entry qua `readOnly.entrySet()` rồi `setValue(99)` cũng bị chặn. Không thể lách quyền chỉ đọc bằng cách đi vòng qua entry.

Đừng nhầm với chỉ bọc một collection:

~~~java
Set<Map.Entry<String, Integer>> onlySetProtected =
        Collections.unmodifiableSet(map.entrySet());
~~~

Lớp bọc tập ngăn thêm/xóa qua chính tập đó, nhưng nó không tự biến từng entry bên trong thành entry không cho `setValue`. Nếu Map bên dưới hỗ trợ entry cập nhật và entry còn ở phạm vi sử dụng hợp lệ, việc trả collection như vậy có thể vẫn làm lộ đường thay đổi giá trị. Khi muốn một giao diện Map chỉ đọc, dùng lớp bọc Map phù hợp; khi muốn một kết quả tách rời, tạo dữ liệu tách rời.

Tương tự, bọc `map.values()` thành collection không cho sửa không làm các đối tượng giá trị bên trong trở nên bất biến.

### Sao chép ánh xạ bằng Map.copyOf

`Map.copyOf(base)` cho một Map không cho cập nhật và không tiếp tục phản ánh việc thêm/xóa/thay ánh xạ ở nguồn. Nó không nhận khóa hay giá trị null. Thứ tự duyệt của kết quả không phải một bảo đảm giữ thứ tự chèn của `LinkedHashMap`.

Trong chương trình, sau khi chụp `fixedMappings` rồi thêm w vào base, góc nhìn `readOnly` thấy w nhưng `fixedMappings` không thấy w. Chương trình chỉ in cờ tồn tại, tránh giả định thứ tự duyệt của Map tạo bởi `copyOf`.

Một lần nữa, các đối tượng khóa và giá trị có thể được dùng chung. Nếu giá trị có thể sửa, Map kết quả không cho `put` vẫn không đồng nghĩa nội dung sâu của giá trị đã bị đóng băng.

### Trả kết quả ra khỏi một lớp

Giả sử một lớp quản lý dữ liệu có phương thức:

~~~java
Set<String> names() {
    return internalMap.keySet();
}
~~~

Nếu bản đồ nội bộ sửa được, phía gọi có thể xóa dữ liệu bên trong bằng `names().clear()` hoặc `names().remove(...)`. Chữ ký trả về `Set` không ngăn việc đó.

Hãy quyết định hợp đồng cần cung cấp:

- Góc nhìn sống và cho sửa: trả góc nhìn nếu đó là chủ đích được công bố.
- Góc nhìn sống chỉ đọc: bọc đúng mức để chặn các đường sửa cần chặn.
- Bản chụp độc lập về cấu trúc: sao chép sang collection hoặc Map phù hợp.
- Kết quả không thay đổi sâu: cần cả mô hình khóa/giá trị phù hợp, không chỉ một lớp bọc ngoài.

Quyết định này ảnh hưởng quyền sở hữu dữ liệu, không chỉ cách viết vòng lặp.

## 12. Thứ tự, sắp xếp entry và chi phí cần hiểu đúng

### Góc nhìn tuân theo loại Map

`HashMap` không hứa thứ tự chèn. `LinkedHashMap` mặc định dùng trong bài duyệt theo thứ tự chèn. `TreeMap` có thứ tự theo khóa do nó quy định. Việc gán một `keySet` vào biến `Set` không tạo thêm một thứ tự mới.

Không ghép hai danh sách chụp từ `keySet` và `values` ở hai thời điểm khác nhau rồi coi phần tử cùng chỉ số chắc chắn là một cặp. Nguồn có thể đã thay đổi giữa hai lượt đọc, và hai bản sao không tự mang thông tin liên kết. Nếu cần cặp, duyệt `entrySet` một lượt và chụp từng cặp khi nguồn ổn định.

### Sắp kết quả biểu diễn, không giả định sắp lại Map gốc

Muốn một danh sách cặp sắp theo giá trị rồi khóa, có thể tạo các entry tách rời rồi sắp danh sách đó. Ví dụ minh họa với `String` làm khóa, `Integer` làm giá trị và có thể có giá trị null:

~~~java
List<Map.Entry<String, Integer>> snapshot = new ArrayList<>();
for (Map.Entry<String, Integer> entry : map.entrySet()) {
    snapshot.add(new AbstractMap.SimpleImmutableEntry<>(entry));
}

Comparator<Integer> valueOrder =
        Comparator.nullsLast(Comparator.naturalOrder());

snapshot.sort(
        Map.Entry.<String, Integer>comparingByValue(valueOrder)
                .thenComparing(Map.Entry.comparingByKey()));
~~~

Đây là đoạn xử lý đặt trong một ngữ cảnh đã có `map` và các import cần thiết. Nó giả sử khóa khác null để tiêu chí so theo khóa tự nhiên hợp lệ. `null` ở phía giá trị được đưa xuống cuối nhờ comparator đã chọn.

`Map.Entry.comparingByKey` và `comparingByValue` tạo comparator cho cặp. Sắp danh sách `snapshot` không đổi loại Map và không biến Map gốc thành bản đồ sắp theo giá trị. Nếu muốn giữ kết quả theo một thứ tự cụ thể, hãy giữ danh sách đó hoặc xây một cấu trúc kết quả có hợp đồng rõ ràng.

### Chi phí lấy góc nhìn khác chi phí duyệt hoặc sao chép

Không coi lời gọi `keySet` là thao tác sao chép N khóa chỉ vì kiểu trả về là collection. Các góc nhìn thông dụng liên kết với nguồn và không phải một bộ dữ liệu đầy đủ mới. Tuy nhiên, khi duyệt hoặc sao chép, chương trình vẫn phải xét các phần tử nguồn theo chi phí của loại Map.

Một bản sao chứa N phần tử cần bộ nhớ theo N cho phần chứa tham chiếu, ngoài chi phí sao chép sâu nếu có. Sắp danh sách các cặp còn tạo công việc sắp xếp theo số cặp và chi phí comparator. Không trộn chi phí “lấy một góc nhìn” với chi phí “chụp toàn bộ rồi sắp”.

Xóa theo khóa có thể tận dụng cấu trúc tìm khóa của Map; xóa theo giá trị thường cần tìm một ánh xạ phù hợp. Vì thế, chọn đúng góc nhìn cũng giúp tránh thêm lượt tìm kiếm khi đã có khóa hoặc entry.

## 13. Những lỗi cần tự nhận ra

| Lỗi | Hệ quả | Cách sửa |
| --- | --- | --- |
| Coi `keySet` là một bản sao | Map đổi làm kết quả giữ trước đó đổi theo | Tạo bản sao nếu cần chụp |
| Coi `values` là Set | Mất hoặc hiểu sai các lần xuất hiện trùng | Giữ ngữ nghĩa Collection |
| Gọi `clear` trên góc nhìn để “dọn biến tạm” | Xóa dữ liệu trong Map | Chỉ clear một collection độc lập khi đó là mục tiêu |
| Thêm bằng `keySet.add` hoặc `entrySet.add` | Ngoại lệ thao tác không được hỗ trợ | Thêm ánh xạ bằng Map |
| Xóa theo giá trị rồi mặc định một khóa cụ thể bị xóa | Xóa sai đối tượng theo yêu cầu | Xóa theo khóa hoặc cặp |
| Sửa trực tiếp Map trong vòng for-each | Phá quy tắc iterator, có thể gặp ngoại lệ | Iterator.remove hoặc thao tác lọc phù hợp |
| Giữ entry liên kết lâu rồi cập nhật sau khi nguồn đã đổi | Ngoài phạm vi bảo đảm của Entry | Đọc/cập nhật khi duyệt hoặc chụp entry mới |
| Sao chép collection entry rồi gọi nó là bản chụp ổn định | Các entry bên trong chưa được tách | Tạo từng entry tách rời |
| Mọi Map.Entry đều setValue được | Ngoại lệ hoặc hiểu sai tác động | Kiểm tra loại entry và hợp đồng |
| Chỉ bọc Set<Entry> rồi coi dữ liệu đã chỉ đọc | Entry bên trong có thể còn đường sửa | Bọc Map đúng mức hoặc tạo dữ liệu tách rời |
| Coi unmodifiableMap là bản sao cố định | Nguồn đổi vẫn thấy thay đổi | Chọn bản sao nếu cần độc lập ánh xạ |
| Coi bản sao nông là bản sao sâu | Đối tượng giá trị vẫn dùng chung | Sao chép hoặc bất biến hóa giá trị theo mô hình |

## 14. Bài tập và lời giải

### Bài 1 — Kiểu và số phần tử

Map có `a -> 1`, `b -> 1`, `c -> 2`. Ba góc nhìn có kiểu gì và mỗi góc nhìn có bao nhiêu phần tử?

**Lời giải:** `keySet` là `Set<String>`, `values` là `Collection<Integer>`, `entrySet` là `Set<Map.Entry<String,Integer>>`. Mỗi góc nhìn có ba phần tử. Giá trị 1 xuất hiện hai lần trong `values` nên không được tự đếm thành hai giá trị phân biệt.

### Bài 2 — Góc nhìn cũ có thấy ánh xạ mới không?

Tạo `Set<String> keys = map.keySet()` khi Map đang rỗng. Sau đó `map.put("x", 10)`, `map.clear()`, rồi `map.put("y", 20)`. Biến keys đang thấy gì?

**Lời giải:** thấy khóa y và không thấy x. Nó liên kết với Map hiện tại, không cố định ở trạng thái rỗng lúc được tạo và cũng không tự ngừng liên kết sau clear.

### Bài 3 — Xóa một giá trị trùng

Map có ba khóa cùng ánh xạ tới 7. Sau một lời gọi `map.values().remove(7)` thành công, còn bao nhiêu ánh xạ có giá trị 7? Có biết chắc tên khóa đã bị xóa chỉ từ kiểu Map không?

**Lời giải:** còn hai ánh xạ có giá trị 7. Không có căn cứ chọn một tên khóa cụ thể chỉ từ kiểu Map. Nếu cần xóa chính xác một khóa, dùng phép theo khóa hoặc cặp.

### Bài 4 — So cả cặp

Map có `a -> 1`. `entrySet.remove(Map.entry("a", 2))` có xóa a không? Còn `entrySet.remove(Map.entry("a", 1))`?

**Lời giải:** lời gọi thứ nhất trả false và không xóa; lời gọi thứ hai trả true và xóa ánh xạ nếu nguồn chưa đổi. Entry xét cả khóa lẫn giá trị, không chỉ sự tồn tại của khóa a.

### Bài 5 — Vì sao không dùng add?

Một entry có đủ khóa và giá trị. Vì sao vẫn không dùng `map.entrySet().add(entry)` như một cách thêm ánh xạ?

**Lời giải:** hợp đồng entrySet không hỗ trợ add/addAll. Việc dữ liệu đủ không tạo thêm một thao tác ngoài hợp đồng. Dùng `map.put(entry.getKey(), entry.getValue())` nếu đó là chính sách thêm/thay thế mong muốn.

### Bài 6 — Xóa khi duyệt

Cần xóa các ánh xạ có giá trị null. Sửa đoạn `for-each` đang gọi `map.remove(entry.getKey())`.

**Lời giải:** dùng `Iterator<Map.Entry<K,V>>`; sau `next`, nếu `entry.getValue() == null` thì gọi `iterator.remove()`. Hoặc, trên bản đồ sửa được hỗ trợ thao tác, dùng `map.entrySet().removeIf(e -> e.getValue() == null)`. Không sử dụng lại entry đã xóa để sửa ánh xạ.

### Bài 7 — Tác động của setValue

Trong vòng duyệt entry-set của LinkedHashMap sửa được, entry a=10 nhận `setValue(15)`. Phương thức trả gì, khóa có đổi không, Map có thêm ánh xạ không?

**Lời giải:** trả 10, khóa vẫn là a, ánh xạ a đổi giá trị thành 15 và số ánh xạ giữ nguyên. Không có setKey tương ứng để đổi khóa của cùng đối tượng entry.

### Bài 8 — Bản sao entry và phạm vi dùng

`new ArrayList<>(map.entrySet())` có đủ để trả các cặp làm bản chụp lâu dài không? Sửa như thế nào?

**Lời giải:** chưa đủ; danh sách chỉ sao chép các tham chiếu entry mà nguồn cung cấp. Trong lúc duyệt nguồn hợp lệ và ổn định, tạo `new AbstractMap.SimpleImmutableEntry<>(entry)` cho từng cặp. Với dữ liệu không có null có thể dùng `Map.Entry.copyOf`. Nếu giá trị có thể sửa, còn phải quyết định có cần sao chép sâu hay không.

### Bài 9 — Chỉ đọc nhưng còn sống theo nguồn

Có `readOnly = Collections.unmodifiableMap(base)`. Sau đó base thêm khóa mới. readOnly có thấy không? Có thể cập nhật qua entrySet của readOnly không?

**Lời giải:** có thấy ánh xạ mới vì đây là góc nhìn chỉ đọc của nguồn. Không được sửa qua entry của lớp bọc Map này; setValue bị từ chối. Đừng nhầm lớp bọc Map với việc chỉ bọc riêng một Set chứa các entry còn sửa được.

### Bài 10 — Thiết kế phương thức trả về

Một lớp trả thẳng `internalMap.values()` trong phương thức `quantities()`. Phía gọi muốn xóa mọi giá trị null bằng `removeIf`. Có thể ảnh hưởng dữ liệu nội bộ không? Nên đổi hợp đồng thế nào nếu chỉ muốn trả báo cáo?

**Lời giải:** có; trên nguồn sửa được, phép lọc qua góc nhìn có thể xóa ánh xạ nội bộ. Nếu mục tiêu là báo cáo độc lập về cấu trúc, trả một bản sao giá trị; nếu cần các cặp, tạo các cặp tách rời. Nếu báo cáo còn phải không cho sửa hoặc không đổi sâu, áp dụng thêm giới hạn tương ứng với dữ liệu giá trị.

## 15. Kiểm tra quyết định trước khi dùng

Khi chọn một collection từ Map, hãy xác định rõ bạn cần khóa, giá trị hay cặp. Sau đó quyết định đối tượng đang giữ là góc nhìn sống hay bản chụp, phía gọi có quyền thay đổi dữ liệu hay không, và dữ liệu có cần được giữ độc lập sau lần duyệt hay không.

Trước một phép xóa, nói rõ phía nào đang được so sánh: khóa, một lần xuất hiện giá trị hay cả cặp. Trước một phép cập nhật Entry, kiểm tra nó có hỗ trợ setValue và còn trong phạm vi dùng hợp lệ. Trước khi trả “bản sao”, kiểm tra cả cấu trúc ngoài lẫn các tham chiếu đối tượng bên trong.

Ba góc nhìn không phải ba bản đồ riêng. Chúng là ba cách tiếp cận cùng các ánh xạ; hiểu quan hệ đó giúp dùng API Collection thuận tiện mà vẫn kiểm soát đúng dữ liệu được thay đổi.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="map-006">Duyệt bằng entrySet, forEach hoặc các view</a>

<details>
<summary>Xem chi tiết</summary>

# Duyệt bằng entrySet, forEach hoặc các view

## 1. Duyệt Map là đi qua những gì?

Một `Map<K, V>` tổ chức dữ liệu thành các ánh xạ: mỗi khóa xác định nhiều nhất một giá trị hiện tại. Ví dụ, map tồn kho có thể chứa:

```text
"pen"      → 3
"notebook" → 2
"marker"   → 3
```

Tùy công việc, ta cần nhìn cùng dữ liệu theo ba cách:

- Chỉ cần mã hàng: duyệt các khóa.
- Chỉ cần tổng số lượng: duyệt các giá trị.
- Cần in “mã hàng = số lượng” hoặc lọc theo cả hai: duyệt từng cặp khóa–giá trị.

Java cung cấp ba **khung nhìn (view)** tương ứng qua `keySet()`, `values()` và `entrySet()`. Ngoài ra, `Map.forEach` nhận một hành động xử lý trực tiếp từng khóa cùng giá trị.

Khung nhìn không phải một bản sao được chụp tại thời điểm gọi. Nó được nối với map gốc. Điều này giúp xử lý dữ liệu ngay trên cấu trúc có sẵn, nhưng cũng có nghĩa một phép xóa qua view có thể xóa dữ liệu trong map. Hiểu liên hệ này quan trọng không kém việc viết được vòng lặp.

### Mục tiêu sau bài học

Bạn cần chọn được cách duyệt theo thông tin mình thực sự cần, đọc đúng kiểu phần tử trong từng view và giải thích được:

1. Vì sao `entrySet` thuận tiện khi cần cả khóa lẫn giá trị?
2. `map.forEach((key, value) -> ...)` khác gì `map.entrySet().forEach(entry -> ...)`?
3. Vì sao `values()` trả `Collection<V>`, không phải `Set<V>`?
4. Khi nào có thể thay giá trị hoặc xóa trong lúc duyệt?
5. Vì sao giữ một view khác với giữ một bản sao, và giữ một `Map.Entry` khác với chụp một cặp dữ liệu độc lập?

## 2. Bản đồ các cách duyệt

| Cách lấy dữ liệu | Kiểu nhận được | Mỗi phần tử biểu diễn gì? | Khi nên chọn |
|---|---|---|---|
| `map.entrySet()` | `Set<Map.Entry<K, V>>` | Một ánh xạ gồm khóa và giá trị | Cần cả hai, hoặc cần thao tác trên từng ánh xạ |
| `map.keySet()` | `Set<K>` | Một khóa | Chỉ cần khóa |
| `map.values()` | `Collection<V>` | Một giá trị ứng với một ánh xạ | Chỉ cần giá trị; chấp nhận giá trị lặp |
| `map.forEach(action)` | Phương thức trả `void` | Hành động nhận riêng `K` và `V` | Hành động ngắn gọn, không cần `break` hay iterator trực tiếp |

Các kiểu view kế thừa `Collection`, vì vậy có thể dùng vòng lặp `for-each`, iterator hoặc `forEach` của `Iterable` trên chúng.

Bản thân `Map` không kế thừa `Iterable`. Do đó đoạn sau là **cú pháp minh họa không biên dịch**, dù nhìn giống vòng lặp trên danh sách:

```java
// Không hợp lệ: stock là một Map, không phải Iterable.
for (Object item : stock) {
    System.out.println(item);
}
```

Muốn dùng vòng lặp nâng cao, hãy chọn một view, chẳng hạn `stock.entrySet()`. Việc `Map` có phương thức tên `forEach` không làm `Map` trở thành `Iterable`: đó là phương thức được chính giao diện `Map` cung cấp.

### Một nguồn dữ liệu, ba loại phần tử

Với map ví dụ ban đầu:

```text
keySet():    "pen", "notebook", "marker"
values():     3,     2,          3
entrySet():  ("pen",3), ("notebook",2), ("marker",3)
```

Đây là minh họa theo thứ tự chèn của một `LinkedHashMap` mặc định. Không suy rằng mọi `Map` đều có thứ tự đó.

Hai khóa khác nhau có thể có cùng giá trị `3`. Vì thế:

- `keySet` là một `Set`: không có khóa trùng trong map.
- `entrySet` là một `Set` các ánh xạ.
- `values` cần giữ cả hai lần xuất hiện của `3`, nên được khai báo là `Collection`, không hứa loại bỏ phần tử trùng.

Trong ví dụ này, kích thước của mỗi view bằng kích thước map, tức `3`. Số lượng **giá trị phân biệt** chỉ là `2`, một đại lượng khác.

## 3. entrySet: giữ khóa và giá trị trong cùng một phần tử

Một phần tử `Map.Entry<K, V>` biểu diễn một cặp khóa–giá trị. Hai phương thức đọc cơ bản là:

| Phương thức | Kết quả |
|---|---|
| `entry.getKey()` | Khóa kiểu `K` |
| `entry.getValue()` | Giá trị kiểu `V` |
| `entry.setValue(newValue)` | Thay giá trị nếu được hỗ trợ; trả giá trị cũ |

Vòng lặp minh họa, đặt trong một phương thức đã có `Map<String, Integer> stock`:

```java
for (Map.Entry<String, Integer> entry : stock.entrySet()) {
    String product = entry.getKey();
    Integer count = entry.getValue();
    System.out.println(product + "=" + count);
}
```

Phần tử đang duyệt là một entry, không phải riêng khóa và cũng không phải một map con. `getValue()` ở đây đọc giá trị thuộc chính ánh xạ đang xét.

Nếu dùng `var`, đoạn minh họa tương đương có thể viết:

```java
for (var entry : stock.entrySet()) {
    System.out.println(entry.getKey() + "=" + entry.getValue());
}
```

`var` chỉ giúp suy luận kiểu biến cục bộ. Nó không thay đổi kiểu thực sự của phần tử hoặc quy tắc duyệt.

### Vì sao thường không duyệt keySet rồi get khi cần cả hai?

Đoạn minh họa sau có thể cho kết quả đúng với một map ổn định phù hợp:

```java
for (String key : stock.keySet()) {
    Integer value = stock.get(key);
    System.out.println(key + "=" + value);
}
```

Nhưng nếu cần cả khóa và giá trị của mọi ánh xạ, `entrySet` thể hiện ý định trực tiếp hơn và tránh thêm một lần tra cứu theo khóa cho từng phần tử.

Không nên biến nhận xét này thành một tuyên bố tốc độ tuyệt đối cho mọi map hoặc mọi chương trình. Chi phí phụ thuộc hiện thực, kích thước, thao tác bên trong vòng lặp và môi trường chạy. Lợi ích rõ nhất ở mức mã nguồn là đang có sẵn cả cặp dữ liệu thì không cần tìm lại giá trị bằng khóa.

Ngoài chi phí, còn có vấn đề hành vi: với `LinkedHashMap` được tạo ở chế độ thứ tự truy cập, `get` có thể làm thay đổi thứ tự truy cập. Đưa `get` vào vòng duyệt khóa khi đó có thể ảnh hưởng chính cấu trúc thứ tự đang duyệt. `entrySet` giúp tránh lời gọi truy cập bổ sung không cần thiết.

Nếu chỉ cần khóa, `keySet` vẫn là lựa chọn đúng. Không cần chọn `entrySet` rồi bỏ hết giá trị chỉ vì coi nó là cách duyệt “tốt nhất” trong mọi tình huống.

## 4. map.forEach: một hành động nhận hai đối số

Chữ ký chính của phương thức là:

```java
// Chữ ký API, không phải một chương trình độc lập.
void forEach(BiConsumer<? super K, ? super V> action);
```

`BiConsumer` là một giao diện hàm cho hành động nhận hai đầu vào và không trả kết quả. Với `Map<String, Integer>`, lambda thường có hai tham số:

```java
// Minh họa với stock đã được khởi tạo.
stock.forEach((key, value) -> {
    System.out.println(key + "=" + value);
});
```

Ở đây `key` là khóa và `value` là giá trị, không phải hai phần tử kế tiếp nhau của một tập hợp.

Cần phân biệt với `forEach` gọi trên view:

```java
// Nhận một Map.Entry cho mỗi lần gọi.
stock.entrySet().forEach(entry -> {
    System.out.println(entry.getKey() + "=" + entry.getValue());
});

// Nhận một khóa cho mỗi lần gọi.
stock.keySet().forEach(key -> {
    System.out.println(key);
});

// Nhận một giá trị cho mỗi lần gọi.
stock.values().forEach(value -> {
    System.out.println(value);
});
```

Nhóm ví dụ trên là các đoạn minh họa cần một biến `stock` đã có. Sự khác biệt nằm ở kiểu phần tử nơi phương thức được gọi:

| Biểu thức | Số tham số lambda thông thường | Dữ liệu của mỗi lần gọi |
|---|---:|---|
| `stock.forEach((k, v) -> ...)` | 2 | Khóa và giá trị |
| `stock.entrySet().forEach(e -> ...)` | 1 | Một entry |
| `stock.keySet().forEach(k -> ...)` | 1 | Một khóa |
| `stock.values().forEach(v -> ...)` | 1 | Một giá trị |

Viết lambda hai tham số cho `entrySet().forEach` là nhầm chữ ký: view đang cung cấp một đối tượng entry, không tự tách nó thành hai tham số.

`forEach` phù hợp với hành động như in dữ liệu, gửi từng cặp sang một hàm khác hoặc ghi nhận vào một cấu trúc kết quả khác do chương trình kiểm soát. Nếu công việc cần tìm phần tử đầu tiên rồi dừng, vòng lặp thường dễ biểu đạt hơn. Phần 10 sẽ kiểm chứng khác biệt giữa `return` trong lambda và `break` trong vòng lặp.

## 5. Chương trình đầy đủ: quan sát tất cả cách đọc

Chương trình độc lập sau dùng `LinkedHashMap` mặc định. Nhờ thứ tự chèn, các dòng đầu ra có thứ tự xác định, thuận tiện để kiểm chứng.

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class MapTraversalFormsDemo {
    public static void main(String[] args) {
        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("pen", 3);
        stock.put("notebook", 2);
        stock.put("marker", 3);

        System.out.println("entrySet:");
        for (Map.Entry<String, Integer> entry : stock.entrySet()) {
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }

        System.out.println("map.forEach:");
        stock.forEach((key, value) ->
                System.out.println(key + "=" + value));

        System.out.println("entrySet.forEach:");
        stock.entrySet().forEach(entry ->
                System.out.println(entry.getKey() + "=" + entry.getValue()));

        System.out.println("keys:");
        for (String key : stock.keySet()) {
            System.out.println(key);
        }

        System.out.println("values:");
        int total = 0;
        for (Integer count : stock.values()) {
            System.out.println(count);
            total += count;
        }
        System.out.println("total=" + total);
        System.out.println("map=" + stock);
    }
}
```

Lưu thành `MapTraversalFormsDemo.java`, biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapTraversalFormsDemo.java
java MapTraversalFormsDemo
```

Đầu ra chính xác:

```text
entrySet:
pen=3
notebook=2
marker=3
map.forEach:
pen=3
notebook=2
marker=3
entrySet.forEach:
pen=3
notebook=2
marker=3
keys:
pen
notebook
marker
values:
3
2
3
total=8
map={pen=3, notebook=2, marker=3}
```

### Điều cần thấy từ kết quả

Ba nhóm `entrySet`, `map.forEach` và `entrySet.forEach` đều đi qua các ánh xạ giống nhau trong ví dụ. Sự khác nhau nằm ở dạng mã và dữ liệu đầu vào của hành động:

- Vòng lặp `entrySet` đặt quyền điều khiển từng bước ở thân vòng lặp.
- `map.forEach` truyền khóa và giá trị thành hai tham số.
- `entrySet.forEach` truyền một entry làm một tham số.

Nhóm `keys` chỉ in mã hàng. Nhóm `values` in cả hai lần xuất hiện của `3`; phép cộng cuối nhận `3 + 2 + 3 = 8`. Nếu tự đưa các giá trị sang một `Set` trước khi cộng, hai số `3` bị gộp lại và đó sẽ là một phép tính khác.

Chương trình giả thiết số lượng đều khác `null`. Biểu thức `total += count` mở hộp `Integer`; nếu dữ liệu cho phép `null`, phải quyết định cách xử lý trước khi cộng, chẳng hạn bỏ qua, báo dữ liệu không hợp lệ hoặc dùng một quy tắc nghiệp vụ rõ ràng.

Không có bước đọc nào trong chương trình tự xóa hay thêm ánh xạ, nên dòng `map=` cuối vẫn biểu diễn ba mã hàng ban đầu.

## 6. View là một đường nhìn vào map gốc

Khi lưu:

```java
// Minh họa với stock đã được khởi tạo.
Set<String> keys = stock.keySet();
Collection<Integer> values = stock.values();
Set<Map.Entry<String, Integer>> entries = stock.entrySet();
```

ta không có ba kho dữ liệu được sao chép độc lập. Các biến này giữ ba view được map hỗ trợ phía sau.

Hai chiều liên hệ quan trọng là:

```text
Thêm/xóa/thay ánh xạ trong map
        ↓
Nội dung quan sát qua view phản ánh map

Xóa phần tử qua một view có hỗ trợ xóa
        ↓
Ánh xạ tương ứng trong map bị xóa
```

Điều này nói về liên hệ dữ liệu, **không bảo đảm đồng bộ giữa các luồng**, và cũng không cho phép giữ một iterator cũ rồi tùy ý sửa map bên ngoài iterator.

Ví dụ, lưu `keys = stock.keySet()`, hoàn tất một thao tác khác rồi thêm khóa mới vào `stock`; khi duyệt `keys` bằng một lượt duyệt mới, sẽ quan sát được khóa mới đó. Không cần gọi lại `keySet()` để “làm mới bản sao”, vì từ đầu nó không phải bản sao.

### Các thao tác xóa có ý nghĩa gì?

| Thao tác trên view sửa được | Tác động lên map |
|---|---|
| `keySet().remove(k)` | Xóa ánh xạ có khóa `k` |
| `values().remove(v)` | Xóa một ánh xạ mang giá trị phù hợp nếu có |
| `entrySet().remove(entry)` | Xóa ánh xạ phù hợp với cả khóa và giá trị của entry |
| `clear()` trên một trong ba view | Xóa các ánh xạ trong map |
| `iterator.remove()` của view | Xóa ánh xạ tương ứng phần tử vừa được iterator trả ra |

Các thao tác thay đổi là tùy chọn và phụ thuộc map/view có hỗ trợ hay không. Với map không cho sửa, thao tác xóa qua view cũng không phải lối đi vòng để sửa map.

`values().remove(v)` chỉ xóa **một** lần xuất hiện phù hợp theo hợp đồng `Collection.remove`. Nếu nhiều khóa cùng mang giá trị đó, không nên mặc định mọi khóa ấy đều bị xóa, cũng không dựa vào một khóa cụ thể sẽ bị chọn nếu hiện thực không cam kết điều này.

Đối với `entrySet().remove`, chỉ đưa đúng khóa nhưng sai giá trị chưa đủ để xóa. Có thể tạo một entry độc lập để biểu diễn cặp cần so sánh, như chương trình tiếp theo.

### Vì sao không thể add qua view?

Ba view của `Map` không hỗ trợ `add` hoặc `addAll`. Với khóa hoặc giá trị riêng lẻ, còn thiếu nửa kia của ánh xạ. Ngay cả `entrySet` có đủ một cặp thì hợp đồng của view vẫn không hỗ trợ thêm bằng `add`.

Muốn thêm một ánh xạ, hãy dùng API của map như `put`. Đừng suy rằng `Set` hoặc `Collection` có tên phương thức `add` thì mọi đối tượng của các kiểu đó đều hỗ trợ thao tác này.

## 7. View sống, bản chép và xóa qua view

Một view đã lấy trước khi thêm ánh xạ mới vẫn phản ánh bản đồ sau khi cập nhật. Danh sách đã sao chép thì giữ cấu trúc tại thời điểm tạo. Đoạn minh họa đặt trong một phương thức đã import các kiểu cần dùng; `LinkedHashMap` giữ thứ tự chèn để dễ đối chiếu:

~~~java
Map<String, Integer> stock = new LinkedHashMap<>();
stock.put("pen", 3);
Set<String> liveKeys = stock.keySet();
List<String> copiedKeys = new ArrayList<>(liveKeys);
stock.put("marker", 3);
System.out.println(liveKeys);   // [pen, marker]
System.out.println(copiedKeys); // [pen]
~~~

Nếu `liveKeys.remove("pen")` được gọi ngoài vòng duyệt, ánh xạ `pen` cũng mất khỏi `stock`. Tương tự, `stock.values().remove(3)` xóa **một lần xuất hiện** của giá trị `3`, không xóa tất cả các khóa cùng mang `3`. Ở đây `values()` có kiểu `Collection<Integer>`, nên `remove(3)` là xóa theo giá trị (`remove(Object)`), không phải xóa một vị trí trong danh sách.

Đó là tác động của view; còn **việc xóa ngay trong một lượt duyệt** cần dùng iterator hoặc `removeIf` đúng cách. Phần 8–9 tập trung kiểm chứng hai cơ chế này. Bài `keySet, values, entrySet và Map.Entry` đã giải thích đầy đủ các thao tác xóa qua từng view.
## 8. Thay giá trị và xóa trong lúc duyệt

Cần phân biệt ba hành động khác nhau:

1. Thay giá trị của ánh xạ hiện tại.
2. Xóa ánh xạ hiện tại.
3. Thêm/xóa ánh xạ khác bằng một lời gọi ngoài cơ chế duyệt.

Không phải mọi cách làm đều có cùng hợp đồng.

### 8.1. Thay giá trị qua entry.setValue

Với entry được trả ra từ iterator của `entrySet`, có thể dùng `setValue` trong lúc duyệt nếu hiện thực hỗ trợ. Thay đổi đó được phản ánh vào map gốc.

Đoạn minh họa với `Map<String, Integer> stock` sửa được và giá trị khác `null`:

```java
for (Map.Entry<String, Integer> entry : stock.entrySet()) {
    Integer old = entry.setValue(entry.getValue() + 1);
    System.out.println("old=" + old);
}
```

`setValue` trả giá trị cũ. Nó không có chức năng đổi khóa. `Map.Entry` không cung cấp `setKey`; nếu cần thay khóa, phải thiết kế việc bỏ ánh xạ cũ và tạo ánh xạ mới bằng cơ chế thích hợp, thay vì biến đổi chính đối tượng khóa trong map.

Không được mặc định `setValue` luôn hoạt động. Entry từ map không cho sửa hoặc một loại entry không hỗ trợ thay giá trị có thể ném `UnsupportedOperationException`.

### 8.2. Xóa bằng chính iterator đang duyệt

Vòng lặp nâng cao giấu iterator phía sau, nên khi cần xóa phần tử hiện tại, dùng iterator tường minh thường dễ kiểm soát:

```java
// Minh họa với stock sửa được, không có giá trị null.
Iterator<Map.Entry<String, Integer>> iterator =
        stock.entrySet().iterator();

while (iterator.hasNext()) {
    Map.Entry<String, Integer> entry = iterator.next();
    if (entry.getValue() == 0) {
        iterator.remove();
    }
}
```

Quy tắc quan trọng:

- `remove()` xóa phần tử vừa được `next()` trả ra.
- Không gọi `remove()` trước lần `next()` đầu tiên.
- Không gọi `remove()` hai lần liên tiếp cho cùng một lần `next()`.
- Iterator có thể không hỗ trợ xóa; khi đó có thể ném `UnsupportedOperationException`.
- Nếu cần ghi nhận khóa/giá trị của phần tử bị xóa, đọc hoặc chép thông tin cần thiết **trước khi xóa**.

Sau khi ánh xạ đã bị loại bỏ, không tiếp tục dựa vào hành vi của entry cũ như thể nó còn là một ánh xạ hợp lệ trong map. Phần 13 sẽ nói rõ phạm vi sử dụng entry.

### 8.3. Xóa bằng removeIf của view

Nếu chỉ cần lọc bỏ các ánh xạ theo điều kiện, có thể gọi:

```java
// Minh họa với stock sửa được và các giá trị khác null.
boolean changed = stock.entrySet()
        .removeIf(entry -> entry.getValue() <= 0);
```

Lời gọi trả `true` nếu có phần tử bị xóa, không trả số lượng bị xóa. Với `entrySet`, điều kiện có thể dùng cả khóa lẫn giá trị; với `keySet` hoặc `values`, chỉ có dữ liệu tương ứng.

Để biểu diễn “xóa mọi giá trị bằng 3, kể cả khi map có thể chứa null”, một đoạn minh họa là:

```java
stock.values().removeIf(value -> java.util.Objects.equals(value, 3));
```

`removeIf` cần khả năng xóa được hỗ trợ. Bản thân điều kiện kiểm tra không nên lại tự thêm hoặc xóa ánh xạ trong chính map đang được lọc. Hãy để thao tác lọc quản lý việc xóa.

## 9. Chương trình đầy đủ: sửa giá trị rồi xóa có kiểm soát

```java
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class MapTraversalEditDemo {
    public static void main(String[] args) {
        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("pen", 3);
        stock.put("notebook", 0);
        stock.put("marker", 2);
        stock.put("expired", 0);

        for (Map.Entry<String, Integer> entry : stock.entrySet()) {
            if (entry.getValue() > 0) {
                Integer old = entry.setValue(entry.getValue() + 1);
                System.out.println("updated=" + entry.getKey()
                        + ", old=" + old + ", now=" + entry.getValue());
            }
        }

        Iterator<Map.Entry<String, Integer>> iterator =
                stock.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            if (entry.getValue() == 0) {
                String removedKey = entry.getKey();
                iterator.remove();
                System.out.println("removed=" + removedKey);
            }
        }
        System.out.println("afterIterator=" + stock);

        stock.put("discarded", -1);
        boolean changed = stock.entrySet().removeIf(
                entry -> entry.getValue() <= 0);
        System.out.println("removeIfChanged=" + changed);
        System.out.println("finalMap=" + stock);
    }
}
```

Lưu thành `MapTraversalEditDemo.java`, biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapTraversalEditDemo.java
java MapTraversalEditDemo
```

Đầu ra chính xác:

```text
updated=pen, old=3, now=4
updated=marker, old=2, now=3
removed=notebook
removed=expired
afterIterator={pen=4, marker=3}
removeIfChanged=true
finalMap={pen=4, marker=3}
```

### Giải thích luồng thực hiện

Lượt `entrySet` đầu tiên chỉ thay giá trị dương: `"pen"` từ `3` lên `4`, `"marker"` từ `2` lên `3`. Các dòng `old=` là dữ liệu cũ mà `setValue` trả về.

Lượt iterator tiếp theo xóa hai ánh xạ mang giá trị `0`. Khóa được lưu vào `removedKey` trước khi `iterator.remove()` được gọi, nên việc in thông tin không cần dùng entry đã bị xóa.

Sau khi lượt iterator kết thúc, chương trình mới thêm `"discarded" → -1`. Tiếp đó, một lần gọi `entrySet().removeIf` độc lập xóa ánh xạ không dương. Cấu trúc cuối còn `"pen" → 4` và `"marker" → 3`.

Không có thao tác thêm hoặc xóa ngoài iterator trong lúc iterator đó đang duyệt. Đây là điều quan trọng về cách tổ chức các bước; không nên rút gọn chúng thành một vòng lặp rồi chèn các lời gọi sửa cấu trúc tùy ý.

## 10. Điều khiển luồng trong forEach khác vòng lặp

### 10.1. return không phải break

Trong lambda truyền cho `Map.forEach`, `return;` kết thúc **lần gọi lambda hiện tại**. Nó không yêu cầu `forEach` bỏ các ánh xạ còn lại và không tự trả khỏi phương thức bên ngoài.

`break` và `continue` của vòng lặp không thể được dùng để nhảy từ bên trong lambda sang một vòng lặp ở ngoài lambda. Nếu cần dừng ngay khi gặp một ánh xạ, hãy dùng vòng lặp tường minh và `break`, hoặc viết một phương thức tìm kiếm phù hợp.

`Map.forEach` trả `void`; nó không phải phép biến đổi sinh một map mới. Muốn biến đổi giá trị, cân nhắc thao tác chuyên dụng như `replaceAll` hoặc `entry.setValue` khi phù hợp với hợp đồng hiện thực. Muốn tạo kết quả khác, hãy tạo một cấu trúc đích riêng và viết rõ cách thu dữ liệu.

### 10.2. Gán lại tham số lambda không thay ánh xạ

Đoạn minh họa:

```java
// Minh họa với scores đã có các giá trị Integer khác null.
scores.forEach((key, value) -> {
    value = value + 10;
});
```

Chỉ biến tham số cục bộ `value` được gán lại. Không có lời gọi nào ghi giá trị mới vào map, nên các ánh xạ không tự cập nhật.

Cần phân biệt việc gán lại tham số với sửa một đối tượng khả biến được tham chiếu. Nếu `value` là một danh sách, `value.add(...)` có thể sửa chính danh sách đang được map tham chiếu. Nó không phải việc thay ánh xạ của khóa sang một đối tượng giá trị khác.

### 10.3. Ngoại lệ có thể dừng quá trình nhưng không hoàn tác tác động trước đó

Nếu hành động ném ngoại lệ, `forEach` truyền ngoại lệ đó ra bên gọi và không tiếp tục xử lý như thể không có lỗi. Những tác động hành động đã thực hiện trước đó, chẳng hạn ghi thêm vào một danh sách kết quả, không được `forEach` tự hoàn tác.

Không nên dùng ngoại lệ như cách `break` thông thường chỉ để tìm phần tử đầu tiên. Ngoại lệ thích hợp để biểu diễn lỗi thật; vòng lặp có điều khiển dừng trực tiếp thường rõ hơn cho việc tìm kiếm.

### Chương trình đầy đủ

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MapForEachControlDemo {
    public static void main(String[] args) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("A", 1);
        scores.put("B", 2);
        scores.put("C", 3);

        System.out.println("lambdaReturn:");
        scores.forEach((key, value) -> {
            if (value == 2) {
                return;
            }
            System.out.println("processed=" + key);
        });
        System.out.println("afterForEach");

        System.out.println("loopBreak:");
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() == 2) {
                System.out.println("stoppedAt=" + entry.getKey());
                break;
            }
            System.out.println("visited=" + entry.getKey());
        }

        System.out.println("localParameterAssignment:");
        scores.forEach((key, value) -> {
            value = value + 10;
            System.out.println(key + " local=" + value);
        });
        System.out.println("mapAfterLocalAssignment=" + scores);

        List<String> visited = new ArrayList<>();
        try {
            scores.forEach((key, value) -> {
                visited.add(key);
                if (value == 2) {
                    throw new IllegalStateException("stop");
                }
            });
        } catch (IllegalStateException expected) {
            System.out.println("callbackFailure=" + expected.getMessage());
        }
        System.out.println("visitedBeforeFailure=" + visited);
        System.out.println("mapAfterFailure=" + scores);
    }
}
```

Lưu thành `MapForEachControlDemo.java`, biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapForEachControlDemo.java
java MapForEachControlDemo
```

Đầu ra chính xác:

```text
lambdaReturn:
processed=A
processed=C
afterForEach
loopBreak:
visited=A
stoppedAt=B
localParameterAssignment:
A local=11
B local=12
C local=13
mapAfterLocalAssignment={A=1, B=2, C=3}
callbackFailure=stop
visitedBeforeFailure=[A, B]
mapAfterFailure={A=1, B=2, C=3}
```

### Đọc kết quả theo từng cơ chế

- Nhóm `lambdaReturn` bỏ xử lý khóa `B` nhưng vẫn xử lý `C`. Điều này chứng minh `return` chỉ kết thúc lần gọi lambda với `B`.
- Nhóm `loopBreak` dừng ở `B`, nên không đi tới `C`.
- Nhóm `localParameterAssignment` in các số `11`, `12`, `13` trong biến cục bộ, nhưng map vẫn chứa `1`, `2`, `3`.
- Khi hành động ném `IllegalStateException` ở `B`, danh sách `visited` đã chứa `A` và `B`, còn `C` chưa được xử lý.

Danh sách `visited` là một đối tượng kết quả riêng, khác với map đang duyệt. Lambda có thể sửa nội dung của danh sách mà không gán lại biến `visited`.

Ngược lại, biến cục bộ được lambda bắt giữ phải là `final` hoặc có giá trị gán ổn định (effectively final). Vì thế không thể tự do tăng một biến tổng cục bộ bên ngoài lambda:

```java
// Minh họa không biên dịch: total bị thay đổi trong lambda.
int total = 0;
scores.forEach((key, value) -> {
    total += value;
});
```

Nếu chỉ cần tính tổng tuần tự, vòng lặp trên `values()` như chương trình đầu vừa rõ vừa tránh phải tạo một hộp khả biến chỉ để vượt ràng buộc cú pháp. Việc chọn lambda không tự làm một thao tác nhanh hơn hoặc an toàn hơn khi có nhiều luồng.

## 11. Không sửa cấu trúc tùy ý trong khi đang duyệt

Đoạn sau là **mẫu sai để phân tích, không phải cách xóa được khuyến nghị**:

```java
// stock là một map thông thường với iterator fail-fast.
for (Map.Entry<String, Integer> entry : stock.entrySet()) {
    if (entry.getValue() == 0) {
        stock.remove(entry.getKey());
    }
}
```

Vòng lặp đang dùng một iterator, nhưng thao tác xóa lại đi qua `stock.remove`. Với các hiện thực như `HashMap` hoặc `LinkedHashMap`, thay đổi cấu trúc theo cách đó trong lúc iterator hoạt động có thể bị phát hiện và dẫn tới `ConcurrentModificationException`.

Những điểm cần hiểu đúng:

- Một luồng duy nhất cũng có thể gây lỗi này; tên ngoại lệ không có nghĩa chắc chắn có hai luồng.
- Không được dựa vào việc một lần chạy ngắn không ném ngoại lệ để kết luận cách làm hợp lệ.
- Cơ chế phát hiện lỗi fail-fast là cố gắng phát hiện sai sử dụng, không phải một bảo đảm mọi thay đổi sai đều chắc chắn bị phát hiện.
- Bắt ngoại lệ rồi tiếp tục dùng lại iterator không sửa được thiết kế sai.
- Chuyển đoạn mã sang `map.forEach` hoặc `entrySet().forEach` không tự khiến việc thêm/xóa tùy ý trở nên hợp lệ.

Đối với xóa phần tử hiện tại, dùng `iterator.remove` nếu hỗ trợ. Đối với lọc hàng loạt, dùng `removeIf` trên view thích hợp nếu hỗ trợ. Đối với một tập thay đổi phức tạp, có thể thu ý định vào một cấu trúc riêng rồi áp dụng sau khi lượt duyệt đã kết thúc.

Đoạn minh họa thu các khóa cần xóa, giả sử `stock` không bị nơi khác sửa trong suốt quá trình:

```java
List<String> toRemove = new ArrayList<>();

for (Map.Entry<String, Integer> entry : stock.entrySet()) {
    if (entry.getValue() != null && entry.getValue() == 0) {
        toRemove.add(entry.getKey());
    }
}

for (String key : toRemove) {
    stock.remove(key);
}
```

Đây là hai lượt riêng và cần bộ nhớ cho danh sách khóa. Nó không tự trở thành giao dịch hoặc giải pháp truy cập đồng thời; mục đích ở đây là không sửa cấu trúc map bằng lời gọi ngoài iterator trong lúc lượt duyệt map đang mở.

### Thay giá trị không đồng nghĩa mọi cách thay đều có thể dùng chung

Với `HashMap`, chỉ thay giá trị của một khóa đang có không được coi là thay đổi cấu trúc. Tuy nhiên, không nên suy rộng thành quy tắc cho mọi hiện thực hoặc cho tuổi thọ mọi entry.

Cơ chế trực tiếp được hợp đồng `entrySet` cho phép trong lúc duyệt là `setValue` trên entry do iterator trả ra, khi được hỗ trợ. Dùng cơ chế đó diễn đạt ý định rõ hơn so với gọi tùy ý các phương thức khác trên map trong thân vòng lặp.

Với một `LinkedHashMap` theo thứ tự truy cập, một phép `get` cũng có thể ảnh hưởng thứ tự duyệt. Vì vậy khi chẩn đoán lỗi duyệt, không chỉ tìm `put` hoặc `remove`; còn cần hiểu loại map và chế độ tạo nó.

### Nếu nhiều luồng cùng truy cập

View và `forEach` không tự cung cấp một bản chụp nhất quán hay khóa bảo vệ toàn bộ quá trình. Một vòng duyệt `HashMap` thông thường không trở nên an toàn chỉ vì đọc qua `entrySet` thay vì `keySet`.

Nếu dữ liệu được chia sẻ, cần chọn cách phối hợp hoặc hiện thực có hợp đồng truy cập đồng thời phù hợp. Một số hiện thực chuyên dụng có quy tắc iterator khác với fail-fast; không áp cùng một kết luận cho mọi lớp chỉ vì chúng cùng khai báo `Map`. Các ví dụ trong bài dùng một luồng sở hữu dữ liệu để tách vấn đề duyệt khỏi bài toán đồng bộ.

## 12. Thứ tự và chi phí thuộc hợp đồng của hiện thực

### 12.1. Đổi cú pháp không tạo thứ tự mới

`entrySet`, `keySet`, `values` và `forEach` không tự sắp xếp dữ liệu theo bảng chữ cái hay theo giá trị.

- `HashMap` không bảo đảm thứ tự duyệt.
- `LinkedHashMap` ở chế độ mặc định duyệt theo thứ tự chèn.
- `LinkedHashMap` ở chế độ thứ tự truy cập có quy tắc khác.
- Map tạo bởi `Map.of` không cam kết một thứ tự duyệt để dùng làm kỳ vọng cố định.

Với `Map.forEach`, nếu hiện thực không có chỉ định khác, hành động theo thứ tự duyệt entry set khi thứ tự đó được xác định. Vì vậy chọn lambda thay cho vòng lặp không biến `HashMap` thành map có thứ tự chèn.

Nếu đầu ra báo cáo bắt buộc có một thứ tự, hãy chọn cấu trúc có hợp đồng thứ tự phù hợp hoặc tạo và sắp xếp dữ liệu đầu ra một cách rõ ràng. Đừng lấy thứ tự đã quan sát được ở một lần chạy `HashMap` làm yêu cầu ổn định của chương trình.

Không nên chép riêng khóa và giá trị ở hai thời điểm rồi ghép chúng bằng vị trí để phục hồi các cặp. Dữ liệu có thể đã thay đổi giữa hai lần chép. Khi mối liên hệ khóa–giá trị là điều cần giữ, hãy xử lý hoặc chụp từng cặp qua `entrySet`.

### 12.2. Duyệt hết map không phải O(1)

Lấy một view không có nghĩa toàn bộ dữ liệu đã được xử lý. Duyệt `n` ánh xạ và thực hiện một hành động cho mỗi ánh xạ đòi hỏi chi phí theo số phần tử cùng công việc của hành động.

Đối với các hiện thực thường dùng:

| Hiện thực và thao tác | Chi phí duyệt các view |
|---|---|
| `HashMap` | Tỉ lệ với `capacity + size` |
| `LinkedHashMap` | Tỉ lệ với `size`, không phụ thuộc dung lượng bảng băm theo cùng cách |

`capacity` ở đây là dung lượng bảng băm, còn `size` là số ánh xạ. Một `HashMap` có dung lượng lớn nhưng ít phần tử vẫn có thể tốn công quét các bucket. Không nên gọi mọi vòng duyệt `Map` là có cùng chi phí chỉ dựa vào tên giao diện.

Các độ lớn trên chưa tính chi phí của việc in, truy cập mạng, xử lý chuỗi hoặc công việc riêng trong thân vòng lặp. Nếu mỗi ánh xạ gây một tác vụ chậm, khác biệt giữa cú pháp vòng lặp và lambda có thể không phải vấn đề chính cần tối ưu.

Dùng `entrySet` khi cần cả cặp tránh yêu cầu tra cứu lại từng giá trị bằng khóa. Còn việc lựa chọn cấu trúc dữ liệu nên dựa thêm vào yêu cầu thứ tự, cập nhật, truy cập và dữ liệu thực tế.

## 13. Giữ view, chép collection và giữ entry là ba việc khác nhau

Trước khi giữ dữ liệu sau một lượt duyệt, xác định rõ thứ cần giữ là **view sống**, **bản sao cấu trúc**, hay **cặp khóa–giá trị tách rời**:

- `stock.keySet()` phản ánh những thay đổi về ánh xạ trong nguồn; một `new ArrayList<>(stock.keySet())` có cấu trúc riêng nhưng có thể vẫn dùng chung đối tượng khóa.
- `new ArrayList<>(stock.entrySet())` chỉ sao chép các tham chiếu entry. Không dựa vào những entry còn gắn với map như một bản chụp bền vững sau khi map thay đổi.
- Muốn giữ cặp độc lập, tạo `new AbstractMap.SimpleImmutableEntry<>(entry)` **trong lúc entry còn hợp lệ**. Entry mới không theo việc thay ánh xạ trong nguồn, nhưng đối tượng khóa và giá trị bên trong có thể vẫn được chia sẻ.
- `Collections.unmodifiableMap(stock)` chặn thay ánh xạ qua lớp bọc nhưng vẫn là góc nhìn sống; không tự tạo bản chụp độc lập hoặc biến giá trị có thể sửa thành bất biến.

Với bài toán duyệt và xuất báo cáo, việc chọn bản chụp còn phụ thuộc yêu cầu nhất quán khi có nhiều luồng thay đổi dữ liệu. Chương trình ở phần 14 kiểm chứng điểm khó hơn: **bản sao chứa danh sách giá trị vẫn chia sẻ danh sách cũ**, ngay cả khi map nguồn đã gán lại ánh xạ.
## 14. Chương trình đầy đủ: bản chép, entry độc lập và lớp bọc

```java
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MapViewCopiesDemo {
    private static void expectUnsupported(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        Map<String, List<String>> source = new LinkedHashMap<>();
        source.put("item", new ArrayList<>(List.of("A")));

        Map<String, List<String>> readOnly =
                Collections.unmodifiableMap(source);
        List<String> copiedKeys = new ArrayList<>(source.keySet());
        Map<String, List<String>> copiedMap = new LinkedHashMap<>(source);

        List<Map.Entry<String, List<String>>> detachedEntries =
                new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            detachedEntries.add(new AbstractMap.SimpleImmutableEntry<>(entry));
        }

        source.put("extra", new ArrayList<>(List.of("X")));
        source.get("item").add("B");

        System.out.println("readOnlyKeys=" + readOnly.keySet());
        System.out.println("copiedKeys=" + copiedKeys);
        System.out.println("copiedMapKeys=" + copiedMap.keySet());
        System.out.println("copiedMapItem=" + copiedMap.get("item"));
        System.out.println("detachedEntryKey="
                + detachedEntries.get(0).getKey());
        System.out.println("detachedEntryValue="
                + detachedEntries.get(0).getValue());

        source.put("item", new ArrayList<>(List.of("Z")));
        System.out.println("readOnlyItemAfterRebind=" + readOnly.get("item"));
        System.out.println("copiedMapItemAfterRebind=" + copiedMap.get("item"));
        System.out.println("detachedEntryValueAfterRebind="
                + detachedEntries.get(0).getValue());

        expectUnsupported("keySetAdd", () -> source.keySet().add("newKey"));
        expectUnsupported("valuesAdd",
                () -> source.values().add(new ArrayList<>()));
        expectUnsupported("entrySetAdd", () -> source.entrySet().add(
                new AbstractMap.SimpleImmutableEntry<>(
                        "newKey", new ArrayList<>())));
        expectUnsupported("readOnlyKeyRemove",
                () -> readOnly.keySet().remove("item"));
        expectUnsupported("readOnlyEntrySetValue",
                () -> readOnly.entrySet().iterator().next()
                        .setValue(new ArrayList<>()));
    }
}
```

Lưu thành `MapViewCopiesDemo.java`, biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapViewCopiesDemo.java
java MapViewCopiesDemo
```

Đầu ra chính xác:

```text
readOnlyKeys=[item, extra]
copiedKeys=[item]
copiedMapKeys=[item]
copiedMapItem=[A, B]
detachedEntryKey=item
detachedEntryValue=[A, B]
readOnlyItemAfterRebind=[Z]
copiedMapItemAfterRebind=[A, B]
detachedEntryValueAfterRebind=[A, B]
keySetAdd=UnsupportedOperationException
valuesAdd=UnsupportedOperationException
entrySetAdd=UnsupportedOperationException
readOnlyKeyRemove=UnsupportedOperationException
readOnlyEntrySetValue=UnsupportedOperationException
```

### 14.1. Thêm khóa mới chỉ xuất hiện ở lớp bọc sống

`readOnlyKeys=[item, extra]` vì `readOnly` đọc map `source` phía sau. `copiedKeys` và `copiedMapKeys` chỉ có `item` vì chúng đã chép cấu trúc trước khi khóa `extra` được thêm.

Tên `readOnly` trong chương trình nghĩa là không sửa ánh xạ qua tham chiếu đó; nó không hứa toàn bộ dữ liệu đã được đóng băng.

### 14.2. Danh sách cũ vẫn dùng chung sau khi chép

Lúc đầu, `source`, `copiedMap` và entry độc lập đều giữ tham chiếu tới cùng danh sách chứa `A`. Sau khi thêm `B` vào danh sách, cả `copiedMapItem` lẫn `detachedEntryValue` đều thấy `[A, B]`.

Sau đó, `source.put("item", ...)` gắn nguồn với danh sách mới `[Z]`:

```text
source["item"] và readOnly["item"] → danh sách mới [Z]

copiedMap["item"] ─┐
                  ├→ danh sách cũ [A, B]
detached entry ────┘
```

Entry độc lập không tự theo ánh xạ mới của nguồn. Nhưng nội dung danh sách cũ có thể tiếp tục thay đổi nếu còn nơi nào sửa nó, vì đây vẫn chỉ là việc chép tham chiếu.

### 14.3. Những thao tác không được hỗ trợ bị từ chối

Ba thao tác `add` trên các view của map nguồn sửa được đều bị từ chối. Đây là hạn chế của hợp đồng view, không phải bằng chứng rằng map nguồn hoàn toàn không sửa được.

Hai thao tác cuối tác động qua lớp bọc không cho sửa: xóa khóa qua `readOnly.keySet` và đổi giá trị của entry lấy từ `readOnly.entrySet`. Chúng ném `UnsupportedOperationException`, cho thấy view không phải đường đi vòng để thay ánh xạ của lớp bọc.

Hàm `expectUnsupported` ném `AssertionError` nếu thao tác được kiểm tra lại chạy bình thường. Nhờ đó các dòng đầu ra biểu diễn điều kiện thực sự đã được kiểm chứng, không chỉ là thông báo in sẵn.

## 15. Chọn công cụ theo công việc

| Công việc | Cách thường phù hợp | Lý do |
|---|---|---|
| In từng khóa và giá trị | `entrySet` hoặc `Map.forEach` | Có cả cặp dữ liệu |
| Chỉ kiểm tra tên khóa | `keySet` | Không cần lấy giá trị |
| Cộng các số lượng | Vòng lặp trên `values` | Giữ cả giá trị lặp và cập nhật biến tổng trực tiếp |
| Tìm một ánh xạ rồi dừng | Vòng lặp trên `entrySet` | Có `break` hoặc trả kết quả từ phương thức bao ngoài |
| Thay giá trị của entry hiện tại | `entry.setValue` khi được hỗ trợ | Thao tác gắn với entry đang duyệt |
| Xóa từng ánh xạ trong một quy trình có nhiều bước | Iterator tường minh và `iterator.remove` | Kiểm soát phần tử hiện tại cùng quá trình xóa |
| Lọc bỏ các ánh xạ theo điều kiện | `entrySet().removeIf` khi được hỗ trợ | Điều kiện dùng được cả khóa và giá trị |
| Đưa dữ liệu cho nơi khác xem trực tiếp, hạn chế sửa ánh xạ | Lớp bọc không cho sửa phù hợp | Vẫn phải hiểu nguồn sống và giá trị khả biến |
| Lưu dữ liệu để dùng sau | Tạo bản chép cấu trúc và các cặp dữ liệu độc lập theo nhu cầu | Không giữ nhầm view hoặc entry sống như bản chụp |

Không có một cú pháp duyệt duy nhất tối ưu cho mọi công việc. Viết đúng thường bắt đầu bằng việc xác định dữ liệu cần dùng, thời gian cần giữ dữ liệu và các thay đổi được phép xảy ra trong quá trình đó.

## 16. Tự kiểm tra: dự đoán trước khi chạy

### Câu 1: Giá trị trùng

Một map có `"a" → 5`, `"b" → 5`, `"c" → 8`. `values().size()` bằng bao nhiêu? Duyệt `values()` có nhất thiết chỉ thấy hai số không?

**Đáp án:** Kích thước bằng `3`. Có ba giá trị ứng với ba ánh xạ, trong đó số `5` xuất hiện hai lần. Nếu muốn các giá trị phân biệt, phải thực hiện một phép tạo tập hợp riêng và chấp nhận việc mất thông tin số lần xuất hiện.

### Câu 2: View được lấy trước

Lấy `Set<String> keys = map.keySet();`, sau đó thêm một khóa mới vào map và bắt đầu một lượt duyệt mới trên `keys`. View có phản ánh khóa mới không?

**Đáp án:** Có. View được hỗ trợ bởi map. Điều này không có nghĩa một iterator đang chạy được phép chịu mọi thay đổi bên ngoài, cũng không có nghĩa có bảo đảm đồng bộ giữa các luồng.

### Câu 3: Xóa khóa qua view

Với map sửa được, `keys.remove("a")` trả `true`. Ánh xạ của `"a"` trong map còn không?

**Đáp án:** Không còn. Thao tác xóa qua view loại bỏ ánh xạ tương ứng trong map gốc.

### Câu 4: Xóa một giá trị lặp

Có hai khóa cùng mang giá trị `5`. Gọi một lần `map.values().remove(5)`. Có chắc cả hai ánh xạ đều bị xóa không?

**Đáp án:** Không. `Collection.remove` xóa một lần xuất hiện phù hợp nếu có. Muốn lọc hết các ánh xạ có giá trị đó, phải dùng một điều kiện xóa hàng loạt rõ ràng trên một cấu trúc hỗ trợ.

### Câu 5: Đúng khóa nhưng sai giá trị

Map có `"a" → 5`. Thử xóa qua `entrySet().remove(new AbstractMap.SimpleImmutableEntry<>("a", 9))`. Ánh xạ có bị xóa không?

**Đáp án:** Không trong ngữ nghĩa ánh xạ của các map thông thường đang xét. Entry yêu cầu cả khóa và giá trị phù hợp; cặp `"a" → 9` không phải cặp đang có.

### Câu 6: return trong lambda

Một `LinkedHashMap` mặc định có các khóa theo thứ tự `A`, `B`, `C`. Lambda bỏ qua `B` bằng `return;`. `C` còn được xử lý không?

**Đáp án:** Có, nếu không có lỗi hoặc điều kiện khác dừng thao tác. `return` chỉ kết thúc lần gọi lambda với `B`. Muốn dừng toàn bộ ở `B`, dùng cơ chế có khả năng dừng như vòng lặp với `break`.

### Câu 7: Gán lại value

Với `Map<String, Integer>`, lambda gọi `value = value + 1;`. Map có tự tăng từng giá trị không?

**Đáp án:** Không. Tham số cục bộ đổi tham chiếu, còn ánh xạ không được ghi lại. Nếu giá trị là đối tượng khả biến và gọi phương thức sửa đối tượng, đó lại là một hành động khác.

### Câu 8: Xóa hai lần qua iterator

Iterator có hỗ trợ xóa. Sau một lần `next()`, gọi `remove()` hai lần liên tiếp mà không gọi `next()` thêm. Điều gì sai?

**Đáp án:** `remove` chỉ được gọi một lần cho mỗi lần `next`. Lần thứ hai vi phạm trạng thái iterator và dẫn tới `IllegalStateException`.

### Câu 9: Không thấy ConcurrentModificationException

Một đoạn mã xóa bằng `map.remove` ngay trong vòng `for-each` trên `entrySet` tình cờ chạy xong mà không có ngoại lệ. Điều đó chứng minh cách xóa đúng không?

**Đáp án:** Không. Fail-fast không phải bảo đảm mọi sai sử dụng đều bị phát hiện. Cách xóa phải theo hợp đồng, không theo việc một lần chạy cụ thể có báo lỗi hay không.

### Câu 10: Danh sách entry đã được chép

`new ArrayList<>(map.entrySet())` có tự tạo bản chụp độc lập cho từng cặp khóa–giá trị không?

**Đáp án:** Không. Nó tạo một danh sách chứa các tham chiếu entry. Muốn giữ dữ liệu độc lập để dùng sau, cần chủ động tạo các cặp dữ liệu riêng khi entry còn hợp lệ; nếu giá trị khả biến, còn phải quyết định có sao chép sâu hay không.

### Câu 11: Lớp bọc không cho sửa

`readOnly = Collections.unmodifiableMap(source)`. Sau đó sửa map qua `source`. Các phép đọc qua `readOnly` có thể thấy thay đổi không?

**Đáp án:** Có. Đây là một lớp bọc đọc qua nguồn, không phải bản sao tách biệt. Hạn chế sửa qua `readOnly` cũng không tự đóng băng những đối tượng giá trị khả biến.

### Câu 12: Chi phí duyệt

Một `HashMap` có dung lượng bảng rất lớn nhưng chỉ vài ánh xạ. Có thể chỉ từ `size` nhỏ kết luận mọi chi phí duyệt cũng nhỏ tương ứng không?

**Đáp án:** Không. Duyệt các view của `HashMap` có chi phí theo cả dung lượng và số ánh xạ. Không nên bỏ qua cấu trúc hiện thực khi phân tích.

## 17. Thực hành: báo cáo và lọc tồn kho

Tạo một chương trình riêng với `LinkedHashMap<String, Integer>`:

```text
pen      → 3
notebook → 0
marker   → 3
eraser   → 1
```

Thực hiện theo thứ tự:

1. In từng mã hàng và số lượng bằng `entrySet`.
2. In lại bằng `Map.forEach`, xác nhận các ánh xạ giống nhau.
3. Cộng tổng bằng `values`, giữ cả hai số `3`.
4. Tạo một danh sách riêng các khóa hiện có.
5. Xóa những ánh xạ có số lượng bằng `0` bằng iterator hoặc `removeIf`, theo hợp đồng hỗ trợ.
6. Tăng mỗi số lượng còn lại thêm `1` bằng `entry.setValue`.
7. Giải thích vì sao danh sách khóa đã chép ở bước 4 vẫn chứa `"notebook"`, còn view khóa sống thì không.

Các điều kiện cần đạt:

```text
Tổng số lượng ban đầu = 7.
Sau lọc, không có khóa notebook trong map.
Sau tăng, pen = 4, marker = 4, eraser = 2.
Kích thước map cuối = 3.
Danh sách khóa đã chép trước lọc vẫn có notebook.
```

Mở rộng thêm một khóa có giá trị `null`, rồi lựa chọn và ghi rõ chính sách:

- Bỏ qua giá trị `null` khi cộng, hoặc báo dữ liệu không hợp lệ.
- Khi lọc, quyết định `null` có bị xóa hay giữ lại.
- Không vô tình mở hộp `null` bằng phép `<=`, `==` với số nguyên hoặc phép cộng.

Khi kiểm tra kết quả, dùng tập ánh xạ và các điều kiện dữ liệu làm tiêu chí. Chỉ kiểm tra thứ tự in khi hiện thực hoặc bước sắp xếp đã có hợp đồng thứ tự rõ ràng.

## Tài liệu đối chiếu

- [Java 17 — Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): ba collection view, `forEach`, thao tác tùy chọn và các map tạo bằng factory.
- [Java 17 — Map.Entry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html): cặp khóa–giá trị, `setValue` và phạm vi liên hệ với map gốc.
- [Java 17 — Iterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html): `next`, `remove` và trạng thái sử dụng iterator.
- [Java 17 — Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html): xóa một lần xuất hiện và `removeIf`.
- [Java 17 — HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): thứ tự không được bảo đảm, chi phí duyệt và giới hạn của fail-fast.
- [Java 17 — LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html): thứ tự chèn, thứ tự truy cập và chi phí duyệt.
- [Java 17 — Collections.unmodifiableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableMap(java.util.Map)): lớp bọc không cho sửa và các view đọc qua nguồn.
- [Java 17 — AbstractMap.SimpleImmutableEntry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.SimpleImmutableEntry.html): cặp dữ liệu riêng không hỗ trợ thay giá trị.

Năm chương trình độc lập trong bài dùng API tương thích Java 17 trở lên, đã được biên dịch bằng `javac --release 17 -encoding UTF-8` và chạy với JDK 24.0.2. Các khối đầu ra là kết quả thực tế của những chương trình đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="map-007">putIfAbsent, replace và remove có điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

# putIfAbsent, replace và remove có điều kiện

## 1. Ghi dữ liệu chỉ khi điều kiện vẫn đúng

Một map giữ trạng thái hiện tại của công việc. Chương trình muốn:

- Đặt giá trị khởi đầu nếu chưa có giá trị dùng được.
- Thay giá trị nếu khóa đã tồn tại.
- Thay giá trị nếu nó vẫn đúng như bản đã đọc.
- Xóa ánh xạ nếu giá trị hiện tại vẫn thuộc về lần xử lý mong đợi.

Gọi `put` hoặc `remove(key)` trực tiếp chưa diễn đạt đủ những điều kiện này. Các phương thức trong bài đưa điều kiện vào chính thao tác cập nhật.

Cần tách hai câu hỏi:

1. **Điều kiện logic là gì?** Chưa có khóa, đang có khóa, hay giá trị hiện tại bằng giá trị mong đợi?
2. **Kiểm tra và thay đổi có nguyên tử không?** Điều này phụ thuộc hợp đồng của loại map, không suy ra chỉ từ tên phương thức.

Bài dùng HashMap để học các trạng thái có null, sau đó dùng ConcurrentHashMap để giải thích cập nhật theo giá trị mong đợi. Ví dụ Java 17+ chạy độc lập, không cần thư viện ngoài.

## 2. Bốn chữ ký và hai kiểu kết quả

Các chữ ký API rút gọn:

```java
V putIfAbsent(K key, V value);
V replace(K key, V value);
boolean replace(K key, V oldValue, V newValue);
boolean remove(Object key, Object value);
```

Đây là các chữ ký để đối chiếu, không phải khai báo một chương trình độc lập.

| Phương thức | Điều kiện áp dụng | Kết quả |
| --- | --- | --- |
| `putIfAbsent(k, v)` | Chưa có khóa hoặc đang ánh xạ tới null | Giá trị cũ; có thể null |
| `replace(k, v)` | Khóa tồn tại | Giá trị cũ; có thể null |
| `replace(k, expected, next)` | Khóa tồn tại và giá trị hiện tại bằng expected | Boolean |
| `remove(k, expected)` | Khóa tồn tại và giá trị hiện tại bằng expected | Boolean |

Điểm dễ lẫn nhất là hai dòng đầu đều trả V, không trả boolean. Hai dòng sau trả việc điều kiện có được đáp ứng để thực hiện thao tác hay không.

Hợp đồng nền tảng nằm trong [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html). Các bảng null trong phần tiếp theo xét một map có thể sửa và có hỗ trợ null, như HashMap.

## 3. putIfAbsent: giữ giá trị đang có nếu nó khác null

### 3.1. “Absent” ở đây gồm cả ánh xạ null

Với ứng viên mới là `"NEW"`:

| Trạng thái trước | Giá trị trả về | Trạng thái sau |
| --- | --- | --- |
| Không có khóa k | `null` | `k → "NEW"` |
| Có `k → null` | `null` | `k → "NEW"` |
| Có `k → "OLD"` | `"OLD"` | Vẫn `k → "OLD"` |

Vì vậy, putIfAbsent không tương đương với câu “chỉ thêm nếu containsKey là false” trên một map cho phép null.

Nếu giá trị đã có khác null, ứng viên mới không thay thế nó. Nếu giá trị đã có là null, ứng viên có thể lấp chỗ đó.

### 3.2. Kết quả là giá trị trước thao tác, không phải luôn giá trị hiện tại

Đoạn lệnh minh họa:

```java
// map là Map<String, String> có thể sửa và ban đầu thiếu task.
String previous = map.putIfAbsent("task", "NEW");
```

Sau lần thêm thành công, previous là null, còn `map.get("task")` là NEW. Không đọc previous thành “giá trị hiện tại sau khi khởi tạo”.

Mẫu thường gặp trong ngữ cảnh không lưu null là dùng giá trị cũ nếu có, hoặc dùng ứng viên vừa được chấp nhận. Nhưng nếu map cho phép null, một kết quả null không phân biệt được trước đó thiếu khóa hay đã ánh xạ null.

### 3.3. Ứng viên null còn có ý nghĩa riêng

HashMap cho phép:

```java
// Đoạn lệnh minh họa:
map.putIfAbsent("task", null);
```

Nếu task chưa có, có thể xuất hiện ánh xạ task → null. Nếu task đã có giá trị khác null, giá trị đó được giữ. Nếu task đã là null, trạng thái logic có thể vẫn là null.

Do đó putIfAbsent trả null không tự chứng minh đã tạo một giá trị hữu ích hoặc đã làm nội dung logic thay đổi.

Chính sách null của HashMap có thể đối chiếu ở [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

## 4. replace hai đối số: chỉ thay khi khóa tồn tại

Với `replace(k, "NEW")`:

| Trạng thái trước | Giá trị trả về | Trạng thái sau |
| --- | --- | --- |
| Không có khóa k | `null` | Vẫn không có k |
| Có `k → null` | `null` | `k → "NEW"` |
| Có `k → "OLD"` | `"OLD"` | `k → "NEW"` |

Dòng thứ hai là khác biệt quan trọng so với giả định “replace chỉ thay một giá trị khác null”. Điều kiện ở đây là sự tồn tại của khóa.

So với put:

```text
put(k, v)      có thể tạo khóa mới hoặc thay khóa cũ
replace(k, v)  chỉ thay ánh xạ của khóa đã có
```

Vì cả trường hợp không có khóa và trường hợp thay một giá trị null đều có thể trả null, không dùng riêng kết quả null của replace hai đối số để kết luận “không thay được”.

Nếu muốn xác nhận đã thay từ một giá trị cụ thể, biến thể ba đối số cung cấp một điều kiện và kết quả boolean rõ hơn.

## 5. replace ba đối số: thay nếu giá trị hiện tại vẫn khớp

### 5.1. Điều kiện gồm cả có khóa và bằng giá trị

```java
boolean changed = map.replace("task", "NEW", "RUNNING");
```

Đây là đoạn lệnh minh họa trong một phương thức. Nó không có nghĩa “tìm ở đâu đó giá trị NEW rồi thay”. Nó nhắm đúng khóa task.

| Trạng thái trước | `replace(k, "NEW", "RUNNING")` | Trạng thái sau |
| --- | --- | --- |
| Không có k | `false` | Không có k |
| Có `k → "NEW"` | `true` | `k → "RUNNING"` |
| Có `k → "DONE"` | `false` | Vẫn `k → "DONE"` |
| Có `k → null` | `false` | Vẫn `k → null` |

Giá trị expected là một điều kiện bảo vệ dựa trên bản chương trình mong đợi. Nếu một nơi khác đã đổi NEW thành DONE, thao tác với expected NEW không nên ghi đè DONE.

### 5.2. So bằng equals, không bắt buộc cùng tham chiếu

Trong các map thông dụng của bài, điều kiện dùng phép bằng giá trị theo hợp đồng Map. Hai record khác tham chiếu nhưng bằng các thành phần vẫn có thể khớp.

Nếu lớp giá trị chưa có equals theo ý nghĩa nghiệp vụ, một đối tượng được tạo mới với các trường trông giống nhau có thể không khớp. Ngược lại, nếu equals bỏ qua một trường mà nghiệp vụ cần kiểm tra, thao tác có thể chấp nhận một trạng thái rộng hơn mong muốn.

Đừng giả định “có điều kiện” đồng nghĩa với so từng trường bất kỳ mà người gọi đang nghĩ đến. Điều kiện thực tế phụ thuộc định nghĩa bằng nhau của giá trị.

### 5.3. Boolean true không luôn có nghĩa dữ liệu khác trước

Nếu ánh xạ hiện tại là `k → "A"`, lệnh:

```java
map.replace(k, "A", "A");
```

có thể trả true vì điều kiện khớp và phép thay được chấp nhận. Giá trị logic trước và sau vẫn bằng A.

Vì vậy, cách diễn đạt chính xác là “thao tác thay theo điều kiện đã thành công”, không phải luôn “nội dung đã khác trước” hoặc “size đã đổi”. Replace không thêm một khóa thứ hai.

## 6. remove hai đối số: chỉ xóa đúng ánh xạ đang mong đợi

So hai biến thể:

```java
V old = map.remove(key);                    // xóa theo khóa, trả giá trị cũ
boolean removed = map.remove(key, expected); // xóa nếu giá trị còn khớp
```

Đây là các dòng minh họa trong ngữ cảnh kiểu K, V và biến đã được định nghĩa.

Với `remove(k, "A")`:

| Trạng thái trước | Kết quả | Trạng thái sau |
| --- | --- | --- |
| Không có k | `false` | Không có k |
| Có `k → "A"` | `true` | Không còn k |
| Có `k → "B"` | `false` | Giữ B |
| Có `k → null` | `false` | Giữ ánh xạ null |

Ví dụ một bộ nhớ đệm có thể muốn xóa dữ liệu chỉ khi nó vẫn là phiên bản đã quan sát. Remove theo khóa không kiểm tra phiên bản; remove với expected thể hiện điều kiện ấy.

### Expected null không làm khóa vắng mặt thành một kết quả khớp

Với HashMap hỗ trợ null:

```text
không có k     → remove(k, null) là false
có k → null    → remove(k, null) là true và xóa k
```

Lỗi tự viết lại phổ biến là:

```java
// Cách kiểm tra thiếu điều kiện tồn tại:
if (Objects.equals(map.get(key), expected)) {
    map.remove(key);
}
```

Nếu expected là null và key không có, Objects.equals vẫn có thể true. Đoạn mã đã gộp hai trạng thái khác nhau. Ngoài ra, kiểm tra rồi xóa là nhiều bước, chưa có bảo đảm đồng thời chỉ từ hình thức đó.

Dùng phương thức có hợp đồng phù hợp rõ hơn việc tự dựng một kiểm tra thiếu điều kiện.

## 7. Chương trình chạy: điều kiện, null và giá trị trả về

Lưu thành **ConditionalMapMethodsDemo.java**:

```java
import java.util.HashMap;
import java.util.Map;

public class ConditionalMapMethodsDemo {
    public static void main(String[] args) {
        Map<String, String> map = new HashMap<>();
        map.put("fixed", "A");
        map.put("null-slot", null);

        System.out.println("putIfAbsent new old: " + map.putIfAbsent("new", "N"));
        System.out.println("new current: " + map.get("new"));
        System.out.println("putIfAbsent fixed old: " + map.putIfAbsent("fixed", "B"));
        System.out.println("fixed after putIfAbsent: " + map.get("fixed"));
        System.out.println("putIfAbsent null-slot old: "
                + map.putIfAbsent("null-slot", "filled"));
        System.out.println("null-slot current: " + map.get("null-slot"));
        System.out.println("putIfAbsent null candidate old: "
                + map.putIfAbsent("still-null", null));
        System.out.println("still-null exists: " + map.containsKey("still-null"));

        System.out.println("replace missing old: " + map.replace("missing", "M"));
        System.out.println("missing exists: " + map.containsKey("missing"));
        map.put("null-replace", null);
        System.out.println("replace mapped-null old: " + map.replace("null-replace", "R"));
        System.out.println("null-replace current: " + map.get("null-replace"));
        System.out.println("replace fixed old: " + map.replace("fixed", "B"));

        System.out.println("replace stale A: " + map.replace("fixed", "A", "C"));
        System.out.println("replace expected B: " + map.replace("fixed", "B", "C"));
        System.out.println("replace same C: " + map.replace("fixed", "C", "C"));
        map.put("null-expected", null);
        System.out.println("replace expected null: "
                + map.replace("null-expected", null, "V"));
        System.out.println("replace absent expected null: "
                + map.replace("absent", null, "V"));

        map.put("null-remove", null);
        System.out.println("remove absent expected null: " + map.remove("absent", null));
        System.out.println("remove mapped null: " + map.remove("null-remove", null));
        System.out.println("null-remove exists: " + map.containsKey("null-remove"));
        System.out.println("remove fixed wrong value: " + map.remove("fixed", "X"));
        System.out.println("fixed still current: " + map.get("fixed"));
        System.out.println("remove fixed expected C: " + map.remove("fixed", "C"));
        System.out.println("fixed exists at end: " + map.containsKey("fixed"));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ConditionalMapMethodsDemo.java
java ConditionalMapMethodsDemo
```

Stdout chính xác:

```text
putIfAbsent new old: null
new current: N
putIfAbsent fixed old: A
fixed after putIfAbsent: A
putIfAbsent null-slot old: null
null-slot current: filled
putIfAbsent null candidate old: null
still-null exists: true
replace missing old: null
missing exists: false
replace mapped-null old: null
null-replace current: R
replace fixed old: A
replace stale A: false
replace expected B: true
replace same C: true
replace expected null: true
replace absent expected null: false
remove absent expected null: false
remove mapped null: true
null-remove exists: false
remove fixed wrong value: false
fixed still current: C
remove fixed expected C: true
fixed exists at end: false
```

Các trạng thái được thiết lập rõ để đọc kết quả từng trường hợp:

- fixed ban đầu có A nên putIfAbsent không thay bằng B.
- null-slot được lấp bằng filled dù khóa đã tồn tại.
- missing không được replace hai đối số tạo ra.
- null-replace có khóa nên replace thay được null bằng R, dù kết quả cũ trả về vẫn null.
- fixed sau đó là B; expected A bị từ chối, expected B được chấp nhận để chuyển thành C.
- Thay C bằng C vẫn có kết quả true.
- Expected null chỉ khớp ánh xạ null đang tồn tại, không khớp một khóa vắng mặt.
- Remove với giá trị sai giữ dữ liệu; remove với giá trị C đúng mới xóa fixed.

Các chương trình trong bài được biên dịch bằng `--release 17` và chạy trên OpenJDK 24.0.2. Cờ đó kiểm tra ngôn ngữ/API mục tiêu Java 17 và tạo bytecode tương ứng; JVM chạy ví dụ vẫn là bản đã nêu.

## 8. Có điều kiện không tự đồng nghĩa nguyên tử trên mọi Map

### 8.1. Điều kiện logic và đồng bộ là hai hợp đồng

Một cách viết:

```java
// Minh họa nhiều bước riêng:
if (!map.containsKey(key)) {
    map.put(key, candidate);
}
```

có khoảng giữa lúc kiểm tra và lúc ghi. Nếu có một nơi khác ghi trong khoảng đó, quyết định trước có thể đã cũ.

Gọi một phương thức có tên putIfAbsent diễn đạt ý định tốt hơn, nhưng không thể kết luận mọi đối tượng kiểu Map đều cung cấp thao tác nguyên tử. Các cài đặt mặc định của Map không bảo đảm đồng bộ hoặc tính nguyên tử cho những phương thức này.

HashMap không tự trở thành cấu trúc an toàn cho nhiều luồng chỉ vì đang gọi một default method. Các ví dụ HashMap trong bài chạy một luồng.

### 8.2. ConcurrentMap có hợp đồng mạnh hơn

ConcurrentMap quy định các thao tác như putIfAbsent, replace và remove theo giá trị mong đợi có hành vi kiểm tra–cập nhật nguyên tử tương ứng. ConcurrentHashMap là một lớp cài đặt phù hợp để dùng những hợp đồng đó.

Ví dụ, với `ConcurrentHashMap`, replace ba đối số không để một cập nhật khác chen vào giữa phép kiểm tra khớp và phép thay của chính thao tác đó theo cách làm sai điều kiện.

Đối chiếu [ConcurrentMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html) và [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

Kiểu biến có thể là Map nhưng đối tượng thực tế vẫn là ConcurrentHashMap. Hãy dựa vào hợp đồng của loại cài đặt đang sử dụng, đồng thời chọn kiểu khai báo đủ rõ khi mã cần năng lực đồng thời.

### 8.3. Một thao tác nguyên tử không làm cả quy trình thành giao dịch

Hai lần replace ở hai khóa không tự trở thành một thay đổi nguyên tử trên cả hai. Việc đổi trạng thái trong map rồi gửi một yêu cầu mạng cũng không tự thuộc cùng một giao dịch.

Kết quả true chỉ xác nhận thao tác cụ thể đã được chấp nhận theo điều kiện của nó. Ngay sau đó, một thao tác hợp lệ khác có thể tiếp tục thay đổi dữ liệu.

Nếu cần ràng buộc nhiều khóa hoặc nhiều hệ thống, phải thiết kế cơ chế phù hợp ở mức quy trình. Không mở rộng bảo đảm của một thao tác map thành bảo đảm toàn cục.

## 9. Chương trình chạy: tránh cập nhật từ bản đọc đã cũ

Lưu thành **ExpectedValueUpdatesDemo.java**:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ExpectedValueUpdatesDemo {
    record TaskState(String phase, long version) {}

    public static void main(String[] args) {
        ConcurrentMap<String, TaskState> states = new ConcurrentHashMap<>();
        states.putIfAbsent("job-7", new TaskState("NEW", 1));

        TaskState seenByA = states.get("job-7");
        TaskState seenByB = new TaskState("NEW", 1);
        System.out.println("same expected reference: " + (seenByA == seenByB));
        System.out.println("equal expected state: " + seenByA.equals(seenByB));

        TaskState running = new TaskState("RUNNING", 2);
        System.out.println("A starts job: "
                + states.replace("job-7", seenByA, running));
        System.out.println("B cancels stale state: "
                + states.replace("job-7", seenByB, new TaskState("CANCELLED", 2)));
        System.out.println("B removes stale state: " + states.remove("job-7", seenByB));
        System.out.println("state after stale attempts: " + states.get("job-7"));

        TaskState newAgain = new TaskState("NEW", 3);
        System.out.println("return to NEW with new version: "
                + states.replace("job-7", running, newAgain));
        System.out.println("old NEW version updates: "
                + states.replace("job-7", seenByA, new TaskState("FAILED", 4)));
        System.out.println("current versioned state: " + states.get("job-7"));
        System.out.println("remove current version: " + states.remove("job-7", newAgain));
        System.out.println("job remains: " + states.containsKey("job-7"));

        ConcurrentMap<String, String> plain = new ConcurrentHashMap<>();
        plain.put("job", "READY");
        String oldRead = plain.get("job");
        plain.replace("job", "READY", "BUSY");
        plain.replace("job", "BUSY", "READY");
        System.out.println("old plain value accepted again: "
                + plain.replace("job", oldRead, "CANCELLED"));
        System.out.println("plain final state: " + plain.get("job"));
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 ExpectedValueUpdatesDemo.java
java ExpectedValueUpdatesDemo
```

Stdout chính xác:

```text
same expected reference: false
equal expected state: true
A starts job: true
B cancels stale state: false
B removes stale state: false
state after stale attempts: TaskState[phase=RUNNING, version=2]
return to NEW with new version: true
old NEW version updates: false
current versioned state: TaskState[phase=NEW, version=3]
remove current version: true
job remains: false
old plain value accepted again: true
plain final state: CANCELLED
```

Chương trình chạy tuần tự để các bước có kết quả xác định. Nó mô phỏng hai nơi đã giữ bản mong đợi của cùng một trạng thái; không trình bày thứ tự in này như lịch thực thi của hai luồng đang chạy thật.

seenByA và seenByB khác tham chiếu nhưng bằng nhau theo record. A chuyển NEW phiên bản 1 thành RUNNING phiên bản 2. Sau đó, B không thể hủy hoặc xóa dựa trên trạng thái NEW phiên bản 1 đã cũ.

Khi trạng thái trở lại NEW nhưng có phiên bản 3, bản NEW phiên bản 1 vẫn không khớp. Trường version tham gia equals của record nên giúp phân biệt hai lần trạng thái có cùng tên.

Ví dụ chuỗi plain ở cuối không có version. Giá trị đi từ READY sang BUSY rồi lại READY. Một bản đọc cũ READY lại khớp với giá trị hiện tại, nên phép đổi sang CANCELLED thành công. Thao tác chỉ kiểm tra giá trị hiện tại, không tự lưu lịch sử đã đi qua BUSY.

## 10. Phép bằng hiện tại không tự phát hiện mọi thay đổi đã xảy ra

### 10.1. Trở lại cùng giá trị vẫn có thể khớp

Tình huống giá trị đi từ A sang B rồi trở về A thường được mô tả bằng tên **ABA**. Nó cho thấy một phép so sánh giá trị hiện tại không đủ để biết “chưa từng có ai thay đổi kể từ lần đọc”.

Không phải mọi chương trình đều cần phát hiện lịch sử trung gian. Nếu yêu cầu chỉ là “hiện tại còn READY thì được hủy”, chấp nhận READY sau một vòng chuyển trạng thái có thể đúng.

Nếu yêu cầu là “chỉ được cập nhật đúng bản tôi đã đọc”, cần có một định danh phiên bản phù hợp trong điều kiện.

### 10.2. Phiên bản phải được quản lý bởi ứng dụng

Một trường version không tự tăng chỉ vì được đặt trong record hoặc ConcurrentHashMap. Mã ứng dụng phải cấp phiên bản mới theo chính sách của nó.

Cũng không nên tái sử dụng phiên bản trong khoảng thời gian mà các bản đọc cũ vẫn có thể được dùng làm điều kiện. Nếu xóa rồi tạo lại cùng khóa và đặt lại cùng phiên bản, bản cũ có thể lại trông bằng bản mới.

Ví dụ dùng các phiên bản 1, 2, 3 để minh họa; nó không phải một hệ thống cấp phiên bản hoàn chỉnh cho mọi vòng đời dữ liệu.

### 10.3. Giá trị thay đổi được có thể làm bản mong đợi không còn là bản chụp

Nếu map giữ một đối tượng có thể sửa và biến expected trỏ tới chính đối tượng đó, sửa đối tượng tại chỗ cũng làm nội dung mà expected nhìn thấy thay đổi. So sánh sau đó không còn giống so với một bản dữ liệu bất biến đã đọc trước.

Ưu tiên giá trị bất biến hoặc một bản chụp có ý nghĩa cho kiểu kiểm tra phiên bản. Record trong ví dụ chứa String và long để giữ trạng thái so sánh ổn định.

Ngay cả trong ConcurrentHashMap, tính nguyên tử của cập nhật ánh xạ không tự khóa mọi trường bên trong một đối tượng payload mà nhiều nơi cùng sửa.

## 11. Đối số mới vẫn được tạo trước lời gọi

Ví dụ:

```java
Value old = map.putIfAbsent(key, createValue());
```

là đoạn minh họa với kiểu và biến đã được định nghĩa. Java tính createValue trước khi phương thức nhận đối số. Một giá trị cũ khác null có thể được giữ, nhưng đối tượng ứng viên mới đã được tạo rồi.

Điều tương tự áp dụng cho biểu thức tạo newValue của replace. Điều kiện có thể thất bại sau khi ứng viên đã được tính.

Nếu việc tạo ứng viên tốn công và cần tạo khi thiếu, hãy chọn một API tính toán phù hợp như computeIfAbsent sau khi đọc rõ hợp đồng của nó. Đây là hợp đồng khác: cách xử lý null, quyền ghi và hành vi đồng thời phải được xét riêng.

Không đặt việc gửi thông báo hoặc tạo tài nguyên bên ngoài vào một biểu thức ứng viên rồi cho rằng điều kiện false sẽ làm tác dụng phụ ấy chưa từng xảy ra.

## 12. Null và khả năng sửa vẫn phụ thuộc loại map

Các bảng null và chương trình đầu dùng HashMap. Chúng không có nghĩa mọi Map phải chấp nhận khóa null, giá trị mới null hoặc mọi đối số expected null theo cùng một cách.

ConcurrentHashMap không lưu khóa null hoặc giá trị null. Với khóa hợp lệ, các lời gọi `putIfAbsent(k, null)`, `replace(k, null)`, `replace(k, null, v)` và `replace(k, v, null)` báo NullPointerException. Không bê nguyên bảng HashMap sang lớp này.

Trong khi đó, `remove(k, null)` với khóa hợp lệ trả false vì không có ánh xạ null nào để khớp. Cần đọc đúng phương thức và phân biệt đối số mong đợi với giá trị sẽ được ghi; không suy ra một chính sách tham số đồng nhất chỉ từ việc lớp không lưu null.

Map không cho sửa cũng có thể báo UnsupportedOperationException khi gọi các thao tác cập nhật. Không dùng một điều kiện có vẻ chắc false làm cách bảo đảm an toàn khi gọi phương thức ghi trên đối tượng chỉ đọc.

Một bước chuẩn bị tốt trước khi chọn API là xác định: có thể sửa không, có null không, kiểu bằng nhau của giá trị là gì, và có yêu cầu nguyên tử nào.

## 13. Chọn phương thức theo mục đích

| Mục đích | Phương thức phù hợp | Điều phải đọc từ kết quả |
| --- | --- | --- |
| Chỉ điền khi chưa có giá trị khác null | putIfAbsent | Giá trị cũ, không phải cờ boolean |
| Thay giá trị của một khóa đã tồn tại | replace hai đối số | Giá trị cũ; null có thể nhập nhằng |
| Thay đúng giá trị hiện tại đang mong đợi | replace ba đối số | true/false của điều kiện |
| Xóa chỉ khi còn đúng giá trị mong đợi | remove hai đối số | true khi đã xóa ánh xạ khớp |
| Ghi bất kể khóa đã tồn tại hay chưa | put | Có thể tạo hoặc thay ánh xạ |
| Xóa bất kể giá trị hiện tại là gì | remove một đối số | Trả giá trị cũ, có thể null |

Đừng chọn phương thức chỉ vì nó ngắn hơn một dòng if. Chọn dựa trên điều kiện nghiệp vụ cần giữ, rồi kiểm tra loại map có đáp ứng yêu cầu đồng thời không.

Đối với một map cho phép null, nếu nghiệp vụ cần “chỉ thêm khi hoàn toàn không có khóa, kể cả phải giữ null đã có”, putIfAbsent không diễn đạt đúng điều kiện đó. Trong ví dụ một luồng có thể kiểm tra containsKey và put; khi có nhiều luồng cần một thiết kế đồng bộ phù hợp với điều kiện cụ thể.

## 14. Lỗi phổ biến và cách kiểm tra

| Lỗi | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| putIfAbsent thay mất null đã lưu | Tưởng absent chỉ là không có khóa | Nhớ điều kiện gồm ánh xạ null |
| replace hai đối số không tạo dữ liệu | Khóa chưa tồn tại | Chọn put nếu được tạo mới |
| replace trả null nên kết luận thất bại | Trước đó có thể có giá trị null | Đọc đúng kiểu trả về và trạng thái |
| Gán putIfAbsent vào boolean | Nhầm phương thức trả V | Giữ kiểu giá trị cũ hoặc chọn biến thể boolean khác |
| replace true nhưng giá trị nhìn giống trước | old và new bằng nhau | True là điều kiện thay đã được chấp nhận |
| remove(k,null) bị hiểu là xóa cả khóa không có | Bỏ qua điều kiện tồn tại | Phân biệt thiếu khóa với ánh xạ null |
| Một giá trị mới cùng dữ liệu không khớp expected | Equals chưa theo mô hình mong muốn | Kiểm tra phép bằng của lớp giá trị |
| Gọi trên HashMap rồi mong tránh đua ghi | Suy nguyên tử chỉ từ tên method | Chọn hợp đồng ConcurrentMap hoặc đồng bộ thích hợp |
| Bản đọc cũ khớp sau khi giá trị quay lại | Chỉ so giá trị hiện tại | Thêm phiên bản nếu cần phân biệt lịch sử |
| Tạo ứng viên tốn công dù điều kiện thất bại | Đối số được tính trước | Chọn cơ chế tạo khi cần nếu đúng yêu cầu |

## 15. Bài tập tự kiểm tra có lời giải

### Bài 1: Hai loại “chưa có”

HashMap có A → null và chưa có B. Gọi putIfAbsent(A, 1) rồi putIfAbsent(B, 2). Kết quả và dữ liệu sau đó là gì?

**Lời giải:** cả hai trả null. Sau đó A → 1 và B → 2. Một kết quả null không phân biệt được A vốn đã có khóa với B vốn chưa có.

### Bài 2: replace hai đối số

HashMap có A → null, chưa có B. Gọi replace(A, 1) và replace(B, 2). Cả hai trả null; chúng có cùng tác động không?

**Lời giải:** không. A được thay thành 1 vì khóa tồn tại. B vẫn không có. Kiểu trả về là giá trị cũ nên hai trường hợp có thể cùng null.

### Bài 3: Điều kiện null

HashMap có A → null, chưa có B. remove(A, null) và remove(B, null) trả gì?

**Lời giải:** lần đầu true và xóa A; lần sau false. Cần cả tồn tại khóa và giá trị khớp.

### Bài 4: Thay bằng chính giá trị cũ

Map có A → 5. replace(A, 5, 5) trả true có mâu thuẫn với việc size và giá trị không đổi không?

**Lời giải:** không. Điều kiện đã khớp và thao tác được chấp nhận. True không có nghĩa giá trị logic bắt buộc phải khác trước.

### Bài 5: Bản đọc đã cũ

A đọc trạng thái NEW, rồi B chuyển nó thành DONE. A gọi replace(key, NEW, RUNNING). Kết quả mong đợi trên một thao tác điều kiện hợp lệ là gì?

**Lời giải:** false, giữ DONE. Nếu dùng put trực tiếp, A có thể ghi đè mà không kiểm tra trạng thái B đã cập nhật.

### Bài 6: Map hay ConcurrentMap?

Đổi một chuỗi containsKey/put thành putIfAbsent trên HashMap có đủ để cho nhiều luồng cùng cập nhật an toàn không?

**Lời giải:** không. Map/HashMap không tự cung cấp bảo đảm nguyên tử chỉ vì gọi phương thức có điều kiện. Phải chọn loại map hoặc cơ chế đồng bộ có hợp đồng phù hợp.

### Bài 7: Giá trị quay lại

Một giá trị đi READY → BUSY → READY. Bản đọc cũ READY có thể khớp replace với expected READY không?

**Lời giải:** có. Phép kiểm tra so giá trị hiện tại, không tự phát hiện lịch sử. Nếu cần phân biệt đúng phiên bản, đưa phiên bản có ý nghĩa vào giá trị so sánh và quản lý nó đúng.

### Bài 8: Ứng viên được tạo khi nào?

Khóa đã có giá trị khác null. putIfAbsent(key, build()) có bảo đảm không chạy build không?

**Lời giải:** không. Build được tính trước lời gọi. Phương thức có thể giữ giá trị cũ sau khi ứng viên đã được tạo.

## 16. Tài liệu đối chiếu

- [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): điều kiện và giá trị trả về của từng biến thể.
- [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): chính sách null trong ví dụ trạng thái.
- [ConcurrentMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html): hợp đồng cập nhật nguyên tử.
- [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): lớp cài đặt dùng trong ví dụ giá trị mong đợi.

Trước một cập nhật, hãy xác định điều kiện trên khóa, điều kiện trên giá trị và bảo đảm đồng thời cần có. Sau cập nhật, đọc kết quả theo đúng kiểu trả về; giá trị cũ, cờ điều kiện và trạng thái hiện tại là những thông tin khác nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="map-008">computeIfAbsent, computeIfPresent, compute và kết quả null</a>

<details>
<summary>Xem chi tiết</summary>

# computeIfAbsent, computeIfPresent, compute và kết quả null

## 1. Từ lấy giá trị sang quyết định ánh xạ tiếp theo

Nhiều thao tác trên `Map` không chỉ đọc một giá trị mà phải quyết định sẽ lưu gì tiếp theo: khởi tạo danh sách cho một nhóm, tăng bộ đếm, giảm tồn kho hoặc xóa một trạng thái đã hết hiệu lực.

Nếu tự viết từng bước `get`, kiểm tra, tính toán rồi `put` hoặc `remove`, ý định cập nhật dễ bị phân tán. Họ phương thức `compute` cho phép đưa quy tắc tính giá trị mới vào một hàm mà Map gọi tại thời điểm phù hợp.

Ba tên phương thức rất giống nhau nhưng khác ở hai câu hỏi:

1. Khi nào hàm tính được gọi?
2. Nếu hàm tính trả `null`, bản đồ sẽ giữ, thêm hay xóa ánh xạ?

Câu hỏi thứ hai đặc biệt quan trọng. Trong các phương thức này, `null` trả từ hàm tính thường là một chỉ dẫn điều khiển cập nhật; nó không đơn thuần có nghĩa “hãy lưu giá trị null”.

Bài học dùng `HashMap` trong truy cập tuần tự để làm rõ các trạng thái có thể có, sau đó phân biệt bảo đảm đồng thời của từng triển khai. Không suy ra rằng một lời gọi có tên `compute` trên bất kỳ `Map` nào cũng tự an toàn luồng.

## 2. Nền tảng: có khóa chưa chắc có giá trị khác null

Với một `HashMap<String, Integer>`, khóa k có thể ở ba trạng thái:

~~~text
A: chưa có khóa k.
N: đã có k, giá trị được lưu là null.
V: đã có k, giá trị được lưu là một Integer khác null.
~~~

Ví dụ giá trị khác null là 5:

| Trạng thái trước | containsKey(k) | get(k) |
| --- | --- | --- |
| A — vắng khóa | false | null |
| N — có khóa với null | true | null |
| V — có khóa với 5 | true | 5 |

`HashMap` cho phép trạng thái N. Một lớp khác như `ConcurrentHashMap` không cho lưu giá trị null, nên nó không có trạng thái lưu trữ đó. [Tài liệu HashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

Tên “Absent” và “Present” trong hai phương thức không chỉ kiểm tra `containsKey`:

- `computeIfAbsent` coi cả A và N là cần tính giá trị.
- `computeIfPresent` chỉ gọi hàm khi đang ở V, tức đã có giá trị khác null.
- `compute` cho quy tắc tính chạy ở cả A, N và V khi lời gọi hợp lệ.

Do đó, đừng dịch máy móc `computeIfPresent` thành “cứ tồn tại khóa là gọi hàm”. Một ánh xạ đang giữ null có khóa thật nhưng không thỏa điều kiện “giá trị hiện diện và khác null” của phương thức này.

## 3. Chữ ký và dữ liệu mà hàm tính nhận được

Các chữ ký rút gọn theo ý nghĩa kiểu dữ liệu:

~~~java
V computeIfAbsent(
        K key,
        Function<? super K, ? extends V> mappingFunction);

V computeIfPresent(
        K key,
        BiFunction<? super K, ? super V, ? extends V> remappingFunction);

V compute(
        K key,
        BiFunction<? super K, ? super V, ? extends V> remappingFunction);
~~~

Đây là chữ ký phương thức để đọc hợp đồng, không phải một tệp Java độc lập.

`Function` nhận một đối số: khóa. Nó đủ cho công việc tạo giá trị từ khóa khi chưa có giá trị khác null.

`BiFunction` nhận hai đối số: khóa và giá trị cũ. Với `computeIfPresent`, hàm chỉ được gọi khi giá trị cũ khác null. Với `compute`, giá trị cũ có thể là null vì khóa chưa có hoặc vì khóa đang ánh xạ tới null.

Cả ba phương thức trả về giá trị sau xử lý theo hợp đồng của chúng, chứ không đồng loạt trả giá trị cũ như `put`. Nếu kết quả trả là null, muốn biết còn ánh xạ hay không phải xét phương thức và trạng thái, không chỉ nhìn riêng giá trị trả về.

Hàm tính được truyền vào phải khác null. Khả năng nhận khóa null, hỗ trợ cập nhật và các hạn chế khác còn tùy loại Map. Không dùng một hàm tính null để biểu diễn “không làm gì”; hãy chọn phương thức phù hợp hoặc trả kết quả có ý nghĩa từ một hàm hợp lệ.

## 4. computeIfAbsent: tạo khi chưa có giá trị khác null

Ví dụ nhóm các mục theo tên nhóm:

~~~java
groups.computeIfAbsent("A", key -> new ArrayList<>()).add("first");
~~~

Nếu A chưa có danh sách, hàm tạo một `ArrayList` rồi lưu vào Map. Nếu A đã có danh sách khác null, hàm không được gọi; phương thức trả danh sách hiện có, và `add` thêm mục vào danh sách đó.

Với dữ liệu tuần tự, hãy đọc quy tắc thành ba nhánh:

| Trạng thái trước của k | Có gọi hàm không? | Hàm tính nhận gì? |
| --- | --- | --- |
| Chưa có k | Có | Khóa k |
| Có k với null | Có | Khóa k |
| Có k với giá trị x khác null | Không | Không có lần gọi |

Khi hàm được gọi:

- Trả một giá trị y khác null: y được lưu làm giá trị của k và được trả cho nơi gọi.
- Trả null khi k vốn vắng: không tạo ánh xạ mới.
- Trả null khi k vốn có giá trị null: ánh xạ null cũ vẫn còn trong ví dụ HashMap; phương thức không tự xóa nó.

Khi k đã có giá trị x khác null, kết quả là x. Một giá trị “dự kiến trả” trong hàm không có tác dụng vì hàm không được thực thi.

Những quy tắc về điều kiện gọi và kết quả null là một phần của hợp đồng `Map.computeIfAbsent`. [Tài liệu Map, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#computeIfAbsent(K,java.util.function.Function)).

### Tạo lười và lưu kết quả không tìm thấy

Hàm tính chỉ chạy khi điều kiện yêu cầu, nên có thể tránh tạo một đối tượng mới nếu đã có giá trị. Tuy nhiên, không suy ra rằng nó chạy “đúng một lần suốt đời của khóa”.

Nếu lần đầu trả null mà chưa tạo ánh xạ, lần sau khóa vẫn cần tính. Nếu lần tính ném ngoại lệ hoặc ánh xạ bị xóa về sau, một lời gọi sau cũng có thể lại gọi hàm.

Với bộ tải có thể trả null, đoạn nối tiếp sau có nguy cơ lỗi:

~~~java
map.computeIfAbsent(key, k -> loadList(k)).add(item);
~~~

Đây là đoạn minh họa có các biến và hàm đã được định nghĩa ở ngữ cảnh ngoài. Nếu `loadList` trả null, kết quả của `computeIfAbsent` cũng có thể là null; gọi `add` sẽ ném `NullPointerException`.

Nếu mục tiêu là luôn có một collection để thêm, hàm tạo collection nên luôn trả một đối tượng phù hợp khác null. Nếu mục tiêu là biểu diễn “đã tải nhưng không có kết quả”, cần một giá trị mô hình hóa trạng thái đó, không mong computeIfAbsent tự lưu một null mới để ghi nhớ.

## 5. computeIfPresent: chỉ cập nhật giá trị đang khác null

Ví dụ giảm một bộ đếm đã có:

~~~java
Integer updated = counts.computeIfPresent(
        "single", (key, old) -> old == 1 ? null : old - 1);
~~~

Đoạn minh họa giả sử các bộ đếm đang dương. Khi giá trị là 1, quy tắc nghiệp vụ trong ví dụ chọn xóa ánh xạ thay vì giữ bộ đếm 0. Đó là quyết định của hàm tính, không phải hành vi tự động của Map đối với mọi số 0.

| Trạng thái trước của k | Có gọi hàm không? | Giá trị cũ truyền vào |
| --- | --- | --- |
| Chưa có k | Không | Không có lần gọi |
| Có k với null | Không | Không có lần gọi |
| Có k với x khác null | Có | x |

Nếu được gọi:

- Trả y khác null: thay giá trị bằng y và trả y.
- Trả null: xóa ánh xạ k và trả null.

Nếu không được gọi, trạng thái không bị thay đổi bởi phương thức. Vì vậy, với một khóa vốn ánh xạ tới null, `computeIfPresent` trả null nhưng vẫn có thể còn khóa. Nó không xóa các khóa có giá trị null chỉ vì tên phương thức chứa “Present”.

Hàm tính của `computeIfPresent` nhận giá trị cũ khác null, nên việc mở hộp một `Integer old` trong hàm này không bị lỗi do old là null trong một lời gọi đúng hợp đồng. Kết quả của toàn phương thức lại vẫn có thể null khi không đủ điều kiện hoặc khi hàm quyết định xóa; gán kết quả thẳng vào `int` vẫn có thể gây lỗi.

Hãy phân biệt null ở đầu vào của hàm tính, null do hàm trả và null do toàn phương thức trả. Chúng xuất hiện ở những vị trí khác nhau của quy trình.

## 6. compute: quyết định chung cho cả tạo, thay và xóa

Ví dụ tăng tần suất từ hoặc tạo bộ đếm lần đầu:

~~~java
counts.compute(word, (key, old) ->
        old == null ? 1 : Math.addExact(old, 1));
~~~

Với khóa chưa có hoặc đang có null, old là null nên quy tắc chọn 1. Với một số đếm đã có, quy tắc tạo số đếm tăng một. `Math.addExact` giúp phép tăng không âm thầm tràn miền int; nếu tràn, nó ném ngoại lệ thay vì lưu một số sai.

Quy tắc trạng thái của compute:

| Trạng thái trước | Đối số old | Hàm trả y khác null | Hàm trả null |
| --- | --- | --- | --- |
| Vắng khóa | null | Tạo k -> y | Tiếp tục vắng |
| Có k -> null | null | Thay bằng k -> y | Xóa k |
| Có k -> x khác null | x | Thay bằng k -> y | Xóa k |

Khác biệt quan trọng nhất là trường hợp có khóa ánh xạ tới null: `compute(k, (key, old) -> null)` xóa khóa đó. Nó không giống `computeIfAbsent` trả null rồi giữ nguyên ánh xạ null cũ.

`compute` nhận old bằng null cho cả vắng khóa và ánh xạ null. Chỉ từ tham số old, hàm không phân biệt được hai trạng thái đó. Nếu nghiệp vụ cần chúng có ý nghĩa khác nhau, hãy mô hình hóa trạng thái bằng kiểu giá trị rõ ràng hoặc thiết kế thao tác đọc/cập nhật có bảo đảm phù hợp. Đừng mặc định một old null luôn chứng minh chưa tồn tại khóa.

Với một lời gọi hợp lệ trên bản đồ cho phép cập nhật, hàm của compute được dùng để quyết định kết quả cả khi chưa có giá trị. Điều đó không phải lời hứa chung về số lần hàm có thể được gọi ở mọi triển khai đồng thời; phần sau sẽ tách rõ hợp đồng đó.

## 7. Bảng đối chiếu tập trung vào null

Bảng dưới áp dụng cho HashMap tuần tự, hàm hợp lệ, không có thay đổi phụ lên Map trong lúc tính. x là giá trị cũ khác null.

| Phương thức | Vắng khóa | Có khóa -> null | Có khóa -> x |
| --- | --- | --- | --- |
| `computeIfAbsent` | Gọi hàm | Gọi hàm | Không gọi, trả x |
| `computeIfPresent` | Không gọi | Không gọi | Gọi hàm |
| `compute` | Gọi hàm với old null | Gọi hàm với old null | Gọi hàm với old x |

Nếu hàm được cấu hình để trả null:

| Phương thức | Ban đầu vắng | Ban đầu có null | Ban đầu có x khác null |
| --- | --- | --- | --- |
| `computeIfAbsent` | Vẫn vắng | Giữ ánh xạ null cũ | Không gọi hàm, giữ x |
| `computeIfPresent` | Vẫn vắng | Không gọi hàm, giữ ánh xạ null | Xóa ánh xạ |
| `compute` | Vẫn vắng | Xóa ánh xạ | Xóa ánh xạ |

Không nói “cả ba đều xóa nếu trả null” mà bỏ qua điều kiện gọi. Với computeIfPresent ở trạng thái có null, không có lần gọi nào để trả null từ hàm; toàn phương thức trả null vì không có giá trị khác null để xử lý.

Cũng không nói “trả null nghĩa là không có khóa sau lời gọi” cho mọi phương thức. Các ô giữ ánh xạ null cũ là phản ví dụ trực tiếp.

Muốn chủ động lưu một ánh xạ null mới trong HashMap, `put(key, null)` là một thao tác khác. Trả null từ hàm compute không phải cách tổng quát để yêu cầu lưu null.

## 8. Chương trình Java 17+ kiểm chứng 18 trường hợp

Chương trình tạo mới một HashMap cho mỗi tổ hợp, tránh để trường hợp trước làm thay đổi đầu vào trường hợp sau:

~~~text
3 phương thức
x 3 trạng thái ban đầu
x 2 kết quả được cấu hình cho hàm: 9 hoặc null
= 18 trường hợp.
~~~

Các cột stdout:

- `configured`: giá trị hàm sẽ trả nếu được gọi; không khẳng định hàm đã chạy.
- `calls`: số lần hàm thực sự được gọi trong trường hợp tuần tự này.
- `result`: giá trị do phương thức Map trả về.
- `present` và `value`: trạng thái Map sau lời gọi, đọc bằng containsKey và get.

Những ví dụ tiếp theo minh chứng tạo nhóm, đếm, xóa, lần tải trả null, ngoại lệ và tác dụng phụ trên một đối tượng giá trị. Phần ConcurrentHashMap được chạy tuần tự để kiểm tra ngữ nghĩa null, không phải một bài thử tranh chấp nhiều luồng.

Lưu tệp `MapComputeDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapComputeDemo {
    enum State { ABSENT, NULL_VALUE, VALUE }
    enum Operation { IF_ABSENT, IF_PRESENT, COMPUTE }

    static void runCase(Operation operation, State state, Integer replacement) {
        Map<String, Integer> map = new HashMap<>();
        if (state == State.NULL_VALUE) {
            map.put("k", null);
        } else if (state == State.VALUE) {
            map.put("k", 5);
        }
        int[] calls = {0};
        Integer result = switch (operation) {
            case IF_ABSENT -> map.computeIfAbsent("k", key -> {
                calls[0]++;
                return replacement;
            });
            case IF_PRESENT -> map.computeIfPresent("k", (key, old) -> {
                calls[0]++;
                return replacement;
            });
            case COMPUTE -> map.compute("k", (key, old) -> {
                calls[0]++;
                return replacement;
            });
        };
        System.out.println(operation + " " + state
                + " configured=" + replacement
                + " calls=" + calls[0]
                + " result=" + result
                + " present=" + map.containsKey("k")
                + " value=" + map.get("k"));
    }

    public static void main(String[] args) {
        for (Operation operation : Operation.values()) {
            for (State state : State.values()) {
                runCase(operation, state, 9);
                runCase(operation, state, null);
            }
        }

        Map<String, List<String>> groups = new HashMap<>();
        int[] creations = {0};
        groups.computeIfAbsent("A", key -> {
            creations[0]++;
            return new ArrayList<>();
        }).add("first");
        groups.computeIfAbsent("A", key -> {
            creations[0]++;
            return new ArrayList<>();
        }).add("second");
        System.out.println("group A=" + groups.get("A"));
        System.out.println("group creations=" + creations[0]);

        Map<String, Integer> counts = new HashMap<>();
        for (String word : List.of("java", "map", "java")) {
            counts.compute(word, (key, old) ->
                    old == null ? 1 : Math.addExact(old, 1));
        }
        System.out.println("counts java=" + counts.get("java")
                + " map=" + counts.get("map"));
        counts.put("single", 1);
        Integer afterDecrement = counts.computeIfPresent(
                "single", (key, old) -> old == 1 ? null : old - 1);
        System.out.println("decrement result=" + afterDecrement
                + " present=" + counts.containsKey("single"));

        Map<String, String> retry = new HashMap<>();
        int[] loads = {0};
        for (int i = 0; i < 2; i++) {
            retry.computeIfAbsent("missing", key -> {
                loads[0]++;
                return null;
            });
        }
        System.out.println("null loads=" + loads[0]
                + " present=" + retry.containsKey("missing"));

        Map<String, Integer> safeMapping = new HashMap<>();
        safeMapping.put("k", 5);
        try {
            safeMapping.compute("k", (key, old) -> {
                throw new IllegalStateException("failure");
            });
        } catch (IllegalStateException exception) {
            System.out.println("failed compute=" + exception.getMessage());
        }
        System.out.println("mapping after failure=" + safeMapping.get("k"));

        Map<String, List<String>> mutable = new HashMap<>();
        mutable.put("k", new ArrayList<>(List.of("old")));
        try {
            mutable.compute("k", (key, old) -> {
                old.add("changed");
                throw new IllegalStateException("failure after mutation");
            });
        } catch (IllegalStateException exception) {
            System.out.println("mutable compute=" + exception.getMessage());
        }
        System.out.println("value object after failure=" + mutable.get("k"));

        Map<String, Integer> concurrent = new ConcurrentHashMap<>();
        Integer noValue = concurrent.computeIfAbsent("k", key -> null);
        System.out.println("concurrent null result=" + noValue
                + " present=" + concurrent.containsKey("k"));
        concurrent.compute("k", (key, old) -> old == null ? 1 : old + 1);
        concurrent.compute("k", (key, old) -> old == null ? 1 : old + 1);
        System.out.println("concurrent sequential count=" + concurrent.get("k"));
        concurrent.computeIfPresent("k", (key, old) -> null);
        System.out.println("concurrent after removal=" + concurrent.containsKey("k"));
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 MapComputeDemo.java
java MapComputeDemo
~~~

Dùng JDK 17 hoặc mới hơn hỗ trợ phiên bản đích 17. Chương trình chỉ dùng cú pháp và API tương thích Java 17. Nó in các khóa chỉ định thay vì dựa vào thứ tự duyệt của HashMap.

Stdout chính xác:

~~~text
IF_ABSENT ABSENT configured=9 calls=1 result=9 present=true value=9
IF_ABSENT ABSENT configured=null calls=1 result=null present=false value=null
IF_ABSENT NULL_VALUE configured=9 calls=1 result=9 present=true value=9
IF_ABSENT NULL_VALUE configured=null calls=1 result=null present=true value=null
IF_ABSENT VALUE configured=9 calls=0 result=5 present=true value=5
IF_ABSENT VALUE configured=null calls=0 result=5 present=true value=5
IF_PRESENT ABSENT configured=9 calls=0 result=null present=false value=null
IF_PRESENT ABSENT configured=null calls=0 result=null present=false value=null
IF_PRESENT NULL_VALUE configured=9 calls=0 result=null present=true value=null
IF_PRESENT NULL_VALUE configured=null calls=0 result=null present=true value=null
IF_PRESENT VALUE configured=9 calls=1 result=9 present=true value=9
IF_PRESENT VALUE configured=null calls=1 result=null present=false value=null
COMPUTE ABSENT configured=9 calls=1 result=9 present=true value=9
COMPUTE ABSENT configured=null calls=1 result=null present=false value=null
COMPUTE NULL_VALUE configured=9 calls=1 result=9 present=true value=9
COMPUTE NULL_VALUE configured=null calls=1 result=null present=false value=null
COMPUTE VALUE configured=9 calls=1 result=9 present=true value=9
COMPUTE VALUE configured=null calls=1 result=null present=false value=null
group A=[first, second]
group creations=1
counts java=2 map=1
decrement result=null present=false
null loads=2 present=false
failed compute=failure
mapping after failure=5
mutable compute=failure after mutation
value object after failure=[old, changed]
concurrent null result=null present=false
concurrent sequential count=2
concurrent after removal=false
~~~

### Đọc một số dòng quan trọng

`IF_ABSENT NULL_VALUE configured=null calls=1 ... present=true` chứng minh hàm đã chạy, trả null, nhưng ánh xạ null cũ chưa bị xóa.

`IF_PRESENT NULL_VALUE ... calls=0` cho biết “có khóa” chưa đủ điều kiện gọi hàm. Trong khi đó, `COMPUTE NULL_VALUE configured=null ... present=false` thể hiện compute loại luôn ánh xạ null.

`group creations=1` cho thấy danh sách nhóm A chỉ cần được tạo một lần trong chuỗi thao tác tuần tự này. `null loads=2` thể hiện một kết quả null không tự tạo ánh xạ mới để ngăn lần tính sau.

Hai nhóm ngoại lệ giúp phân biệt ánh xạ chưa được thay với việc mọi tác dụng phụ đều được hoàn tác. Nhóm ConcurrentHashMap cho thấy lớp cấm lưu null vẫn chấp nhận một hàm trả null theo ý nghĩa không thêm hoặc xóa, chứ không ném ngoại lệ chỉ vì kết quả hàm là null.


## 9. Ứng dụng đúng theo chính sách cập nhật

### Khởi tạo collection cho từng khóa

Mẫu quen thuộc:

~~~java
groups.computeIfAbsent(groupId, key -> new ArrayList<>()).add(item);
~~~

Mỗi khóa cần một collection riêng. Không tạo sẵn một danh sách dùng chung bên ngoài rồi trả cùng danh sách đó cho mọi khóa, trừ khi việc dùng chung là một chủ đích rất rõ. Nếu A và B cùng trỏ tới một danh sách, thêm vào A cũng có thể làm dữ liệu đọc qua B thay đổi.

Hàm tạo phải phù hợp với thao tác tiếp theo. Trả `List.of()` tạo một danh sách không cho thêm phần tử; gọi `add` sau đó sẽ thất bại. Trong chương trình, `new ArrayList<>()` được chọn vì chính mục tiêu là bổ sung các phần tử vào nhóm.

Một giá trị null đã có ở khóa nhóm sẽ được thay bằng danh sách mới khi hàm tạo trả khác null. Nếu null cũ mang ý nghĩa nghiệp vụ riêng, hãy xem lại liệu computeIfAbsent có đúng chính sách mong muốn hay không.

### Chỉ sửa đối tượng đã có giá trị

`computeIfPresent` phù hợp khi không muốn tự tạo dữ liệu mới chỉ vì một yêu cầu cập nhật đến. Ví dụ giảm số lượt còn lại của một mục đã tồn tại, hoặc chuẩn hóa một chuỗi đang có.

Tuy nhiên, một giá trị null đã lưu sẽ bị bỏ qua. Nếu muốn dọn các ánh xạ null, computeIfPresent không phải công cụ sẽ gọi hàm cho chúng. Có thể cần một phép xóa hoặc một lượt lọc theo đúng yêu cầu.

### Thống nhất tạo và tăng bộ đếm

`compute` diễn đạt rõ quy tắc “chưa có giá trị thì bắt đầu từ 1, có rồi thì tăng”. Ví dụ `counts` trong chương trình không cần một nhánh `containsKey` tách riêng.

Nếu 0 vẫn là một giá trị hợp lệ cần giữ, hãy trả 0. Nếu muốn hết số lượng thì xóa ánh xạ, hãy trả null đúng ở nhánh đó. Không có quy tắc chung nào trong Map tự biến số 0, chuỗi rỗng hay collection rỗng thành việc xóa.

Ví dụ điều chỉnh điểm với quy tắc không giữ kết quả bằng 0, trong ngữ cảnh đã có `scores`, `key` và `delta`:

~~~java
Integer result = scores.compute(key, (k, old) -> {
    int next = Math.addExact(old == null ? 0 : old, delta);
    return next == 0 ? null : next;
});
~~~

Đây là đoạn minh họa một chính sách cụ thể. Nếu old null từng mang ý nghĩa khác với vắng khóa, việc coi cả hai là 0 đã gộp chúng có chủ đích và cần được nghiệp vụ chấp nhận.

### Khi cần nhớ một kết quả không tìm thấy

Một bộ nhớ đệm có bộ tải trả null không tự ghi nhận lần tra cứu thất bại thông qua computeIfAbsent. Hai lời gọi có thể cùng cần tải vì không có giá trị mới được lưu.

Có thể lưu một đối tượng kết quả khác null, chẳng hạn `Optional.empty()` biểu thị đã tải nhưng không có dữ liệu. Khi đó kiểu giá trị bản đồ là `Optional<V>`, không phải V có thể null. Hàm tải cần chuyển kết quả sang một đối tượng Optional khác null.

Đừng gộp lỗi tải với không tìm thấy nếu chúng có chính sách khác nhau. Ngoại lệ của bộ tải, một dữ liệu không tồn tại và một dữ liệu chưa được tải là ba thông tin có thể cần được theo dõi riêng.

## 10. Ngoại lệ và tác dụng phụ: không có cơ chế hoàn tác tổng quát

Khi hàm tính ném một ngoại lệ không được xử lý trong hàm, ngoại lệ được truyền ra nơi gọi. Trong lời gọi tuân thủ hợp đồng, phương thức không cài đặt giá trị mới tính dở thành ánh xạ mới.

Ví dụ trong chương trình:

~~~text
Trước: k -> 5.
Hàm compute ném IllegalStateException.
Sau khi bắt ngoại lệ: k vẫn có giá trị 5.
~~~

Nhưng “ánh xạ chưa bị thay” không có nghĩa mọi thứ hàm đã làm đều được hoàn tác.

Ở ví dụ thứ hai, giá trị cũ là một `ArrayList`. Hàm gọi `old.add("changed")` rồi mới ném ngoại lệ. Bản đồ vẫn trỏ tới chính danh sách cũ đó, và danh sách đã bị thêm phần tử:

~~~text
Trước: [old]
Sau khi hàm ném ngoại lệ: [old, changed]
~~~

Phương thức compute không có một hệ thống giao dịch để quay ngược nội dung mọi đối tượng, khôi phục biến ngoài, xóa bản ghi nhật ký hoặc hủy một yêu cầu đã gửi ra hệ thống khác.

Vì vậy, nên giữ hàm tính tập trung vào việc quyết định một giá trị. Nếu muốn tránh sửa đối tượng cũ trước khi tính xong, tạo một đối tượng mới từ dữ liệu cũ rồi chỉ trả đối tượng mới khi quy tắc hoàn tất. Điều đó giúp kiểm soát tác dụng phụ trong mô hình tuần tự; mức an toàn khi có dữ liệu dùng chung vẫn cần được thiết kế riêng.

Cũng cần phân biệt hai kiểu thất bại:

- Hàm trả null: một kết quả bình thường có ngữ nghĩa không thêm hoặc xóa theo phương thức.
- Hàm ném ngoại lệ: tính toán không hoàn tất bình thường và ngoại lệ được truyền ra.

Không bắt mọi ngoại lệ rồi trả null như một thói quen. Với compute hoặc computeIfPresent đang có giá trị, hành vi đó có thể biến lỗi tính toán thành lệnh xóa dữ liệu.

## 11. Không sửa lại chính Map từ trong hàm tính

Một lỗi dễ mắc là vừa trả giá trị mới vừa tự ghi thêm vào Map:

~~~java
map.compute("a", (key, old) -> {
    map.put("b", 100);
    return 10;
});
~~~

Đây là mã minh họa một cách dùng không hợp lệ theo quy tắc hàm tính. Thay vì coi nó như một thao tác cập nhật hai khóa, hãy tách thiết kế cập nhật theo nhu cầu thực sự.

Hàm tính không nên sửa chính Map đang được compute, kể cả cố cập nhật một khóa khác trong đó. Với HashMap, phát hiện một số sửa đổi từ hàm có thể dẫn tới `ConcurrentModificationException` theo cơ chế cố gắng phát hiện. Không coi ngoại lệ này là một cơ chế đảm bảo mọi sửa đổi sai đều được phát hiện hoặc được hoàn tác. [Hợp đồng các phương thức compute của HashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

Gọi lồng compute trên chính Map cũng không phải một lối thoát chung. Với triển khai đồng thời, một số kiểu cập nhật đệ quy bị cấm hoặc có thể được phát hiện bằng ngoại lệ. Đừng dùng ngoại lệ do đệ quy để điều khiển thuật toán.

Nếu cần thay đổi hai khóa phải nhất quán với nhau, một compute cho một khóa không tự cung cấp giao dịch trên cả hai khóa. Hãy chọn cơ chế phối hợp hoặc mô hình dữ liệu phù hợp với tính nhất quán cần đạt.

Việc sửa chính đối tượng giá trị cũ, như danh sách trong phần ngoại lệ, là một tác dụng phụ khác với việc gọi Map.put. Nó vẫn cần được xem xét vì có thể làm thay đổi dữ liệu dùng chung và ảnh hưởng khả năng xử lý thất bại.

## 12. Map, HashMap, ConcurrentMap và ConcurrentHashMap có bảo đảm khác nhau

### Không suy ra tính nguyên tử từ tên phương thức

Các phương thức mặc định của `Map` không đưa ra bảo đảm đồng bộ chung cho mọi đối tượng triển khai. `HashMap` cũng không phải cấu trúc để các luồng tự do cùng cập nhật mà không có cơ chế phối hợp.

Thay một đoạn `get -> tính -> put` bằng một lời gọi `HashMap.compute` làm ý định gọn hơn nhưng không biến HashMap thành bản đồ an toàn luồng. Nếu nhiều luồng chia sẻ dữ liệu, mọi bên liên quan phải tuân theo cơ chế truy cập phù hợp, hoặc dùng một cấu trúc đồng thời có đúng hợp đồng.

### ConcurrentHashMap có hợp đồng tính nguyên tử cụ thể

`ConcurrentHashMap` triển khai các phương thức compute với bảo đảm nguyên tử cho thao tác được mô tả. Hàm tính cần ngắn gọn; trong lúc tính, một số cập nhật khác có thể phải chờ. Không đưa những công việc dài hoặc phụ thuộc lẫn nhau vào hàm rồi giả định toàn hệ thống vẫn tiến hành độc lập. [Tài liệu ConcurrentHashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

Đối với một lời gọi hợp lệ trên ConcurrentHashMap, hợp đồng của nó quy định số lần gọi hàm theo điều kiện của từng phương thức. Điều này khác với lời khẳng định “hàm chỉ chạy một lần trong toàn bộ ứng dụng”: ánh xạ có thể bị xóa, một lần tính có thể không tạo giá trị, và những lời gọi về sau vẫn có thể cần tính lại.

ConcurrentHashMap không lưu khóa hoặc giá trị null, nhưng chấp nhận **kết quả hàm tính bằng null** với ý nghĩa điều khiển:

| Lời gọi trên trạng thái phù hợp | Kết quả hàm null có nghĩa |
| --- | --- |
| `computeIfAbsent` cho khóa đang vắng | Không thiết lập ánh xạ mới |
| `computeIfPresent` cho khóa đang có | Loại ánh xạ |
| `compute` | Loại ánh xạ nếu có, hoặc giữ vắng nếu chưa có |

Không nhầm “cấm put một giá trị null” với “cấm hàm compute trả null”. Chương trình đã kiểm chứng điểm này trên ConcurrentHashMap theo thứ tự tuần tự.

### Không mở rộng lời hứa của một lớp sang mọi ConcurrentMap

Một số thuật toán mặc định của `ConcurrentMap` có thể thử lại khi có cạnh tranh cập nhật; hàm tính lại có thể được gọi nhiều lần trong một lời gọi. Vì vậy, mã nhận một `ConcurrentMap` nói chung cần dựa vào hợp đồng của triển khai được hỗ trợ, không lấy lời hứa của riêng ConcurrentHashMap làm mặc định cho tất cả. [Tài liệu ConcurrentMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html).

Đây là một lý do nữa để hạn chế tác dụng phụ trong hàm tính. Gửi một sự kiện ra ngoài hay tăng một biến ngoài mỗi khi hàm được gọi có thể không tương ứng một–một với số cập nhật ánh xạ thành công.

Bộ đếm `calls` trong chương trình là dụng cụ quan sát các trường hợp tuần tự đã chọn. Không coi số lần quan sát đó là bằng chứng kiểm thử mọi hành vi cạnh tranh của một triển khai đồng thời.

### An toàn của Map không tự bảo vệ giá trị bên trong

Mẫu:

~~~java
groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
~~~

không tự làm `ArrayList` bên trong trở thành một collection an toàn luồng ngay cả khi groups là ConcurrentHashMap. Thao tác `add` diễn ra trên đối tượng danh sách được trả về; một lời gọi computeIfAbsent không tạo ra lớp bảo vệ cho mọi thao tác tiếp theo trên danh sách đó.

Cần chọn giá trị có cơ chế đồng thời phù hợp hoặc thiết kế cập nhật bằng những giá trị bất biến, cùng quy tắc truy cập nhất quán. Phân biệt an toàn của ánh xạ với an toàn của đối tượng mà ánh xạ trỏ tới.

## 13. Chọn phương thức và nhận ra các lỗi điển hình

| Ý định | Lựa chọn thường phù hợp | Điều phải xác minh |
| --- | --- | --- |
| Tạo giá trị khi chưa có giá trị khác null | computeIfAbsent | Null cũ có được phép xem như chưa khởi tạo không? |
| Chỉ sửa khi đã có giá trị khác null | computeIfPresent | Có chấp nhận bỏ qua khóa đang ánh xạ null không? |
| Cùng một quy tắc cho tạo, thay và xóa | compute | Hàm đã xử lý old null chưa? |
| Chỉ muốn đọc, không muốn sửa Map | get hoặc getOrDefault theo yêu cầu | Không dùng compute chỉ để trả mặc định rồi vô tình tạo ánh xạ |
| Muốn lưu null mới vào HashMap | put với chính sách rõ ràng | Không dùng null trả từ compute như yêu cầu lưu null |
| Muốn thao tác đồng thời nguyên tử | Triển khai có hợp đồng phù hợp | Không suy ra từ kiểu Map hoặc tên compute |

Các lỗi hay gặp:

| Lỗi | Kết quả dễ xảy ra | Cách sửa |
| --- | --- | --- |
| IfPresent đồng nghĩa containsKey | Hàm không chạy với giá trị null như mong đợi | Đọc điều kiện “đã có giá trị khác null” |
| Mọi kết quả null đều xóa | Hiểu sai ánh xạ null được giữ bởi IfAbsent hoặc bị bỏ qua bởi IfPresent | Đối chiếu cả trạng thái và điều kiện gọi |
| compute trả null là lưu null | Ánh xạ bị loại | Tách rõ lệnh xóa với lưu null trực tiếp |
| IfAbsent là bộ nhớ đệm cho null | Bộ tải bị gọi lại | Lưu một trạng thái kết quả khác null nếu muốn ghi nhớ |
| Nối phương thức lên kết quả có thể null | NullPointerException | Quy định hàm tạo phải khác null hoặc kiểm tra kết quả |
| Gán kết quả vào kiểu nguyên thủy | Mở hộp null thất bại | Giữ kiểu bao và xử lý nhánh null |
| Bắt lỗi rồi trả null để “bỏ qua” | Có thể xóa giá trị đang có | Chọn chính sách thất bại riêng, đừng vô tình phát lệnh xóa |
| Sửa Map trong hàm tính | Vi phạm hợp đồng, có thể có ngoại lệ | Tách thao tác hoặc đổi mô hình cập nhật |
| Hàm ném lỗi nên mọi thay đổi được hoàn tác | Tác dụng phụ trên đối tượng hoặc hệ thống ngoài vẫn còn | Giảm tác dụng phụ, tính trên giá trị mới khi phù hợp |
| ConcurrentHashMap bảo vệ mọi collection giá trị | Mất cập nhật hoặc lỗi trong đối tượng dùng chung | Thiết kế an toàn của giá trị riêng |
| Mọi ConcurrentMap gọi hàm cùng số lần | Tác dụng phụ bị lặp khi có thử lại | Dựa vào hợp đồng triển khai, giữ hàm tính phù hợp |

Với kiểu Map không cho cập nhật, các phương thức compute có thể bị từ chối bằng `UnsupportedOperationException`. Đừng dùng chữ “If” trong tên để suy ra rằng gọi một phương thức cập nhật trên bản đồ chỉ đọc sẽ luôn được phép khi bạn đoán không có thay đổi.

## 14. Bài tập và lời giải

### Bài 1 — Phân biệt điều kiện gọi

HashMap có `a -> null`, `b -> 5`, chưa có c. Với một hàm tính hợp lệ, computeIfAbsent sẽ xét gọi hàm cho khóa nào? computeIfPresent thì sao?

**Lời giải:** computeIfAbsent gọi cho a và c, không gọi cho b. ComputeIfPresent gọi cho b, không gọi cho a hoặc c. Compute xét hàm ở cả ba trường hợp khi lời gọi hợp lệ.

### Bài 2 — Ba cách trả null trên một ánh xạ null

Bắt đầu riêng từng lần từ `a -> null`. So sánh trạng thái sau `computeIfAbsent(a, k -> null)`, `computeIfPresent(a, (k,v) -> null)` và `compute(a, (k,v) -> null)`.

**Lời giải:** hai cách đầu giữ ánh xạ a -> null, nhưng vì hai lý do khác nhau: IfAbsent có gọi hàm và không ghi giá trị mới; IfPresent không gọi hàm. Compute gọi hàm rồi loại ánh xạ a. Cần containsKey để phân biệt kết quả trạng thái khi giá trị trả đều null.

### Bài 3 — Hàm không chạy nhưng được cấu hình trả null

Map có `a -> 5`. Hàm truyền cho computeIfAbsent được viết để trả null. Kết quả phương thức và giá trị lưu sau đó là gì?

**Lời giải:** hàm không chạy vì đã có giá trị 5 khác null. Phương thức trả 5 và Map giữ 5. Không có kết quả null thực sự từ hàm để xử lý trong lời gọi này.

### Bài 4 — Giữ số 0 hay xóa?

Bộ đếm a đang bằng 1. Muốn giảm xuống 0 nhưng vẫn giữ khóa. Có nên trả null ở nhánh old bằng 1 không?

**Lời giải:** không. Trả 0 để lưu 0; trả null trong computeIfPresent sẽ xóa ánh xạ. Lựa chọn xóa bộ đếm 0 trong ví dụ chương trình là một chính sách nghiệp vụ, không phải quy định bắt buộc của Map.

### Bài 5 — Bộ tải trả null hai lần

Khóa chưa tồn tại. Gọi computeIfAbsent hai lần liên tiếp với một bộ tải luôn trả null. Có bao nhiêu lần bộ tải chạy trong HashMap tuần tự? Có ánh xạ mới không?

**Lời giải:** hai lần; không có ánh xạ mới. Lần đầu không tạo một giá trị để lời gọi sau tái sử dụng. Nếu cần ghi nhớ không tìm thấy, dùng một giá trị biểu diễn trạng thái khác null.

### Bài 6 — Giá trị cũ của compute

Hàm compute nhận old bằng null. Có kết luận chắc chắn chưa có khóa trước lời gọi không?

**Lời giải:** không nếu Map cho phép lưu giá trị null. Nó có thể là vắng khóa hoặc một ánh xạ null. Chỉ tham số old không mang đủ thông tin phân biệt hai trạng thái đó.

### Bài 7 — Ngoại lệ có hoàn tác danh sách không?

Map giữ `k -> ArrayList["one"]`. Hàm compute thêm "two" vào old rồi ném ngoại lệ. Có thể mặc định danh sách trong Map vẫn chỉ có "one" không?

**Lời giải:** không. Ánh xạ có thể vẫn giữ nguyên tham chiếu danh sách, nhưng đối tượng đó đã bị sửa. Không có cơ chế hoàn tác tự động cho nội dung đối tượng. Tính trên một bản sao mới giúp tránh sửa đối tượng cũ trước khi thành công trong mô hình phù hợp.

### Bài 8 — Tại sao không sửa khóa khác trong hàm?

Hàm compute cho a gọi `map.put("b", 10)` rồi trả giá trị cho a. Có thể coi đây là một cập nhật hai khóa nguyên tử không?

**Lời giải:** không. Hàm tính không nên sửa chính Map đang xử lý; có thể vi phạm hợp đồng và phát sinh ngoại lệ. Một compute cho một khóa không cung cấp giao dịch hai khóa. Cần thiết kế cơ chế nhất quán riêng.

### Bài 9 — Null trên ConcurrentHashMap

ConcurrentHashMap cấm lưu giá trị null. `compute("a", (k,v) -> null)` có nhất thiết ném ngoại lệ vì hàm trả null không?

**Lời giải:** không. Null trả từ hàm có ngữ nghĩa không thêm hoặc loại ánh xạ, không phải lưu giá trị null. Khóa truyền vào vẫn phải hợp lệ và hàm vẫn phải tuân thủ các ràng buộc khác.

### Bài 10 — Danh sách nhóm trong nhiều luồng

groups là ConcurrentHashMap, giá trị là ArrayList. Mọi luồng dùng computeIfAbsent rồi add lên danh sách trả về. Chỉ như thế đã đủ bảo vệ danh sách chưa?

**Lời giải:** chưa. Map bảo vệ thao tác ánh xạ theo hợp đồng của nó; ArrayList không tự trở thành an toàn luồng. Cần một giá trị có cơ chế truy cập thích hợp hoặc thiết kế cập nhật khác bảo vệ toàn bộ dữ liệu liên quan.

### Bài 11 — Không chuyển lời hứa giữa các triển khai

Một phương thức thư viện nhận ConcurrentMap và hàm tính có tác dụng phụ. Vì thường dùng ConcurrentHashMap, có thể giả định mọi triển khai được truyền vào đều gọi hàm một lần không?

**Lời giải:** không. Một số thuật toán mặc định có thể thử lại và gọi hàm nhiều lần khi cạnh tranh cập nhật. Thư viện phải nêu rõ các hợp đồng triển khai được hỗ trợ hoặc thiết kế hàm không phụ thuộc một giả định số lần gọi không được bảo đảm.

### Bài 12 — Đọc bảng kiểm chứng

Giải thích dòng `IF_PRESENT NULL_VALUE configured=9 calls=0 result=null present=true value=null`. Có phải phương thức đã cố lưu 9 rồi thất bại không?

**Lời giải:** không. Configured chỉ là kết quả hàm sẽ trả nếu được gọi. Giá trị cũ là null nên điều kiện IfPresent không thỏa; calls bằng 0, không có phép tính 9 nào được thực hiện, ánh xạ null cũ được giữ nguyên và phương thức trả null.

## 15. Cách tự kiểm tra một quy tắc compute

Trước khi viết lambda, xác định các trạng thái đầu vào mà nó phải xử lý và điều kiện nào mới cho phép gọi nó. Sau đó gán ý nghĩa rõ ràng cho hai loại kết quả: một giá trị khác null và null.

Kiểm tra quy tắc trên một khóa vắng, một khóa có null nếu triển khai cho phép, và một khóa có giá trị thật. Quan sát riêng số lần hàm chạy, giá trị phương thức trả và sự tồn tại của ánh xạ sau lời gọi. Nếu có tác dụng phụ hoặc dữ liệu nhiều luồng, kiểm tra thêm hợp đồng thất bại và hợp đồng đồng thời của đúng lớp Map đang dùng.

Nhớ ba vai trò: computeIfAbsent tạo khi chưa có giá trị khác null; computeIfPresent sửa giá trị đã khác null; compute áp dụng quy tắc chung. Null trả từ hàm là tín hiệu điều khiển việc giữ vắng hoặc xóa theo từng phương thức, và chỉ có ý nghĩa khi hàm thực sự đã được gọi.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="map-009">merge, replaceAll và các mẫu cập nhật dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# merge, replaceAll và các mẫu cập nhật dữ liệu

## 1. Hai bài toán cập nhật khác nhau

Một map giữ số lần xuất hiện của từng từ. Mỗi từ mới đọc cần được cộng vào số đã có; nếu chưa có thì bắt đầu từ 1. Một map khác giữ nhãn hiển thị; ứng dụng cần chuẩn hóa mọi nhãn đang tồn tại.

Hai nhu cầu đó dẫn đến hai công cụ:

- `merge`: kết hợp một giá trị đầu vào với giá trị hiện có của **một khóa**.
- `replaceAll`: tính lại giá trị cho **các ánh xạ đang có**, sử dụng từng khóa và giá trị hiện tại.

Cả hai nhận hàm tính giá trị mới, nhưng khác nhau về tham số hàm, phạm vi tác động và ý nghĩa của kết quả null.

Bài này đi từ bảng trạng thái đến ví dụ chạy được, rồi giải thích các mẫu đếm, gộp, chuẩn hóa, xử lý lỗi và giới hạn đồng thời. Các ví dụ dùng API Java 17+ và không cần thư viện ngoài.

## 2. So sánh nhanh trước khi viết lambda

| Thuộc tính | merge | replaceAll |
| --- | --- | --- |
| Phạm vi | Một khóa được chỉ định | Các ánh xạ đang có |
| Đầu vào ngoài map | Một giá trị đưa vào, phải khác null | Không có giá trị đưa vào cho một khóa riêng |
| Tham số của hàm | Giá trị cũ, giá trị đưa vào | Khóa, giá trị hiện tại |
| Khi khóa thiếu | Gắn trực tiếp giá trị đưa vào | Không tạo khóa mới |
| Khi giá trị cũ null | Gắn trực tiếp giá trị đưa vào | Hàm có thể nhận null nếu map cho phép |
| Hàm trả null | Xóa ánh xạ đang được gộp | Thử gán null làm giá trị thay thế |
| Kết quả của phương thức | Giá trị mới hoặc null nếu không còn ánh xạ | void |

Hai chữ ký rút gọn:

```java
V merge(K key, V value,
        BiFunction<? super V, ? super V, ? extends V> remappingFunction);

void replaceAll(
        BiFunction<? super K, ? super V, ? extends V> function);
```

Đây là chữ ký API để đối chiếu, không phải mã chương trình độc lập. `BiFunction` là hàm nhận hai đầu vào và trả một kết quả.

Hợp đồng chi tiết có trong [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

## 3. merge: ba nhánh bắt buộc phải hiểu

### 3.1. Thiếu khóa hoặc đang ánh xạ null

Nếu chưa có khóa k, `merge(k, incoming, f)` gắn trực tiếp incoming với k. Hàm f không được dùng để tạo giá trị đầu tiên.

Nếu k đang có giá trị null trong một map cho phép null, hành vi cũng là gắn trực tiếp incoming. Giá trị null cũ không được truyền vào f như một giá trị cũ bình thường.

Với incoming bằng 3:

| Trước lệnh | Có gọi hàm gộp không? | Giá trị sau |
| --- | --- | --- |
| Không có k | Không | `k → 3` |
| Có `k → null` | Không | `k → 3` |
| Có `k → 2` | Có, với `(2, 3)` | Theo kết quả hàm |

Vì vậy, một điều kiện chỉ viết bên trong lambda không tự áp dụng cho nhánh khởi tạo.

### 3.2. Có giá trị cũ khác null

Trong trường hợp đó, hàm nhận:

```text
đối số thứ nhất: giá trị cũ
đối số thứ hai:  giá trị incoming của lần gọi
```

Ví dụ:

```java
// Đoạn lệnh minh họa, counts là Map<String, Integer> có thể sửa.
counts.merge("java", 1, (oldCount, increment) -> oldCount + increment);
```

Hai đối số lambda đều là giá trị số, không phải `(key, value)`. Đặt tên biến `(k, v)` không đổi hợp đồng này; nó chỉ làm người đọc dễ hiểu sai.

Nếu muốn phép gộp phụ thuộc khóa, có thể sử dụng khóa đã biết từ phạm vi bên ngoài một cách rõ ràng, hoặc chọn một API tính lại theo khóa có hợp đồng phù hợp. Không giả định merge tự truyền khóa vào hàm gộp.

### 3.3. Kết quả null là yêu cầu xóa

Nếu f được gọi và trả một giá trị khác null, map lưu giá trị đó. Nếu f trả null, ánh xạ bị xóa.

```text
giá trị cũ khác null
    ↓
gọi f(oldValue, incoming)
    ├─ kết quả khác null → lưu kết quả
    └─ kết quả null      → xóa ánh xạ
```

Merge trả giá trị mới, hoặc null khi không còn giá trị ánh xạ sau thao tác. Nó không trả giá trị cũ như put.

Ví dụ, nếu old là 5 và incoming là -5, hàm có thể tính 0 rồi chủ đích trả null để bỏ khóa có tổng bằng 0. Nhưng nếu hàm trả số 0 thật thì map giữ ánh xạ tới 0; Java không tự coi 0 là lệnh xóa.

## 4. Các ràng buộc null của merge

### 4.1. Giá trị incoming không được null

Ngay cả khi HashMap cho phép lưu null nói chung, đối số value của merge vẫn phải khác null:

```java
// Ví dụ không hợp lệ:
totals.merge("item", null, Integer::sum);
```

Đoạn này báo NullPointerException. Không dùng null ở vị trí incoming để diễn đạt “hãy xóa khóa”. Việc xóa qua merge nằm ở kết quả của hàm gộp khi hàm thực sự được gọi.

Hàm remappingFunction cũng không được null, kể cả một lần gọi có vẻ sẽ rơi vào nhánh chưa có khóa và không cần thực thi hàm.

### 4.2. Một hàm luôn trả null không xóa mọi trường hợp

Xét:

```java
// Đoạn lệnh minh họa:
totals.merge("item", 5, (oldValue, incoming) -> null);
```

Nếu item đang có giá trị khác null, hàm chạy và xóa item.

Nếu item chưa có hoặc đang là null, hàm không chạy; merge lưu 5. Đây là lý do không dùng mẫu này thay cho remove khi ý định là xóa bất kể trạng thái.

### 4.3. Khóa null là một chính sách khác

Merge không tự cho mọi loại map nhận khóa null. HashMap có thể nhận khóa null; ConcurrentHashMap không nhận. Chính sách đối với khóa không giống yêu cầu incoming phải khác null.

Đối chiếu [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html) khi áp dụng các ví dụ có ánh xạ null.

## 5. Chương trình chạy: quan sát từng nhánh merge

Lưu thành **MergeStateDemo.java**:

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class MergeStateDemo {
    private static int sumCalls;

    private static Integer add(Integer oldValue, Integer incoming) {
        sumCalls++;
        return Math.addExact(oldValue, incoming);
    }

    public static void main(String[] args) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        totals.put("existing", 2);
        totals.put("null-slot", null);

        System.out.println("existing result: " + totals.merge("existing", 3, MergeStateDemo::add));
        System.out.println("missing result: " + totals.merge("missing", 4, MergeStateDemo::add));
        System.out.println("null-slot result: " + totals.merge("null-slot", 7, MergeStateDemo::add));
        System.out.println("sum callback calls: " + sumCalls);
        System.out.println("after initial merges: " + totals);

        Integer removed = totals.merge("existing", -5, (oldValue, incoming) -> {
            int next = Math.addExact(oldValue, incoming);
            return next == 0 ? null : next;
        });
        System.out.println("zero-result merge return: " + removed);
        System.out.println("existing remains: " + totals.containsKey("existing"));

        System.out.println("missing with null-returning callback: "
                + totals.merge("fresh", 5, (oldValue, incoming) -> null));
        System.out.println("fresh current: " + totals.get("fresh"));

        try {
            totals.merge("bad-value", null, Integer::sum);
        } catch (NullPointerException ex) {
            System.out.println("null incoming value: " + ex.getClass().getSimpleName());
        }
        try {
            totals.merge("bad-function", 1, null);
        } catch (NullPointerException ex) {
            System.out.println("null remapping function: " + ex.getClass().getSimpleName());
        }

        totals.put("fail", 9);
        try {
            totals.merge("fail", 1, (oldValue, incoming) -> {
                throw new IllegalStateException("cannot combine");
            });
        } catch (IllegalStateException ex) {
            System.out.println("callback failure: " + ex.getClass().getSimpleName());
        }
        System.out.println("fail value after exception: " + totals.get("fail"));
        System.out.println("final mappings: " + totals);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MergeStateDemo.java
java MergeStateDemo
```

Stdout chính xác:

```text
existing result: 5
missing result: 4
null-slot result: 7
sum callback calls: 1
after initial merges: {existing=5, null-slot=7, missing=4}
zero-result merge return: null
existing remains: false
missing with null-returning callback: 5
fresh current: 5
null incoming value: NullPointerException
null remapping function: NullPointerException
callback failure: IllegalStateException
fail value after exception: 9
final mappings: {null-slot=7, missing=4, fresh=5, fail=9}
```

Hàm add chỉ được gọi một lần trong ba lệnh đầu, vì chỉ existing có giá trị cũ khác null. Missing và null-slot nhận trực tiếp giá trị đưa vào.

Lệnh gộp existing với -5 chủ đích trả null khi tổng bằng 0; khóa existing bị xóa. Ngược lại, fresh chưa có nên hàm luôn trả null không được gọi; fresh được tạo với 5.

Trường hợp fail có giá trị 9. Hàm gộp ném ngoại lệ trước khi trả giá trị mới, nên ánh xạ ấy vẫn là 9. Điều này không có nghĩa mọi tác dụng phụ do người viết đặt trong hàm đều được hoàn tác; ví dụ sử dụng hàm chỉ tính kết quả hoặc ném lỗi.

Chương trình dùng LinkedHashMap mặc định để thứ tự in ánh xạ dễ đối chiếu. Không xem thứ tự in này là cam kết của mọi loại Map. Xem [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

Các chương trình trong bài được biên dịch với `--release 17` và chạy trên OpenJDK 24.0.2. Cờ đó kiểm tra ngôn ngữ/API mục tiêu Java 17 và sinh bytecode tương ứng; JVM dùng để chạy vẫn là bản đã nêu.

## 6. replaceAll: tính lại giá trị cho từng khóa đang có

### 6.1. Hàm nhận khóa và giá trị

Ví dụ:

```java
// Đoạn lệnh minh họa, scores là Map<String, Integer> có thể sửa.
scores.replaceAll((name, score) -> score + name.length());
```

Khác với merge, đối số thứ nhất ở đây là khóa. Hàm được dùng để tạo giá trị thay thế cho ánh xạ tương ứng. Đối số function phải khác null, kể cả khi map rỗng. Map cũng phải hỗ trợ thay giá trị; map không cho sửa có thể báo UnsupportedOperationException.

Phương thức trả void. Nó không trả một map mới, không trả số phần tử đã đổi và không yêu cầu gán lại:

```java
// Cách dùng đúng trong ngữ cảnh:
scores.replaceAll((name, score) -> score * 2);

// Ví dụ sai, không biên dịch:
// Map<String, Integer> updated = scores.replaceAll((name, score) -> score * 2);
```

### 6.2. Không tạo khóa mới

Nếu map đang có A, B, C, replaceAll xử lý giá trị gắn với các khóa ấy. Nó không tự thêm một khóa D chưa tồn tại.

Trong một lần chạy bình thường không có sửa cấu trúc từ bên ngoài và không có lỗi, bộ khóa được giữ; giá trị từng ánh xạ được tính lại. Số lượng ánh xạ không tăng chỉ vì một giá trị mới khác giá trị cũ.

Đừng nhầm tên replaceAll với “thay mọi phần tử bằng một cặp khóa–giá trị mới”. Hàm trả V, không trả một Entry có khóa mới.

### 6.3. Giá trị cũ null vẫn cần được xử lý

Nếu map cho phép null, hàm có thể được gọi với oldValue null. Đoạn:

```java
labels.replaceAll((key, value) -> value.strip());
```

có thể lỗi nếu một value là null. Chính sách chuẩn hóa cần được viết rõ:

```java
labels.replaceAll((key, value) ->
        value == null ? "unknown" : value.strip());
```

Đây là các lệnh minh họa cho `Map<String, String>` có thể sửa. Chuỗi rỗng sau strip vẫn là một giá trị; không tự bị xóa.

## 7. Null từ replaceAll không có cùng nghĩa với merge

Với một HashMap cho phép null:

```java
// Đoạn lệnh minh họa:
labels.replaceAll((key, value) -> null);
```

cố gán null cho giá trị của mỗi ánh xạ. Các khóa vẫn tồn tại sau một lượt thành công.

| Tình huống | merge | replaceAll |
| --- | --- | --- |
| Hàm được gọi và trả null | Xóa ánh xạ đang gộp | Gán null nếu map hỗ trợ |
| Muốn xóa mọi entry | Không phải công cụ áp dụng toàn map | Trả null không thay cho clear |
| Loại map không cho lưu null | Null từ hàm gộp vẫn có thể yêu cầu xóa | Kết quả null có thể gây lỗi khi thay giá trị |

ConcurrentHashMap không lưu giá trị null. Với nó, replaceAll tạo kết quả null không phải một cách xóa entry; có thể phát sinh NullPointerException.

Nếu muốn lọc bỏ các entry theo điều kiện, hãy chọn thao tác xóa/lọc phù hợp. Không tái sử dụng ý nghĩa null của một API cho API khác chỉ vì cả hai nhận lambda.

## 8. Chương trình chạy: đếm, biến đổi hàng loạt và lỗi giữa chừng

Lưu thành **BulkValueUpdatesDemo.java**:

```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BulkValueUpdatesDemo {
    public static void main(String[] args) {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (String word : List.of("java", "map", "java", "api", "map", "java")) {
            frequencies.merge(word, 1, Math::addExact);
        }
        System.out.println("frequencies: " + frequencies);
        frequencies.replaceAll((word, count) -> count * word.length());
        System.out.println("weighted values: " + frequencies);

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("A", " raw ");
        labels.put("B", null);
        labels.put("C", "");
        labels.replaceAll((key, value) -> value == null ? "unknown" : value.strip());
        System.out.println("normalized labels: " + labels);
        labels.replaceAll((key, value) -> key.equals("B") ? null : value);
        System.out.println("B after null result: " + labels.get("B"));
        System.out.println("B key remains: " + labels.containsKey("B"));
        System.out.println("label count: " + labels.size());

        Map<Integer, Integer> partial = new LinkedHashMap<>();
        partial.put(1, 10);
        partial.put(2, 20);
        partial.put(3, 30);
        try {
            partial.replaceAll((key, value) -> {
                if (key == 2) {
                    throw new IllegalArgumentException("stop at key 2");
                }
                return value + 1;
            });
        } catch (IllegalArgumentException ex) {
            System.out.println("replaceAll failure: " + ex.getClass().getSimpleName());
        }
        System.out.println("observed partial update: " + partial);
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 BulkValueUpdatesDemo.java
java BulkValueUpdatesDemo
```

Stdout trên môi trường kiểm tra:

```text
frequencies: {java=3, map=2, api=1}
weighted values: {java=12, map=6, api=3}
normalized labels: {A=raw, B=unknown, C=}
B after null result: null
B key remains: true
label count: 3
replaceAll failure: IllegalArgumentException
observed partial update: {1=11, 2=20, 3=30}
```

Vòng merge đếm java ba lần, map hai lần và api một lần. Math.addExact được dùng để phép cộng có thể báo tràn số thay vì âm thầm quay vòng. Dữ liệu mẫu nhỏ nên không xảy ra lỗi.

ReplaceAll tiếp theo có cả khóa và giá trị: số đếm được nhân với độ dài từ. Nó biến đổi map hiện tại, không tạo map kết quả riêng.

Phần labels cho thấy giá trị null cũ được đổi thành unknown; một lần replaceAll sau trả null cho B nhưng B vẫn tồn tại và size vẫn là 3.

Dòng observed partial update minh họa trạng thái quan sát sau lỗi: khóa 1 đã đổi thành 11, khóa 2 gây lỗi và khóa 3 chưa được xử lý. Không dùng dãy khóa cụ thể đó làm một hợp đồng thứ tự cho mọi Map hoặc mọi cài đặt. Điều cần giữ là **replaceAll không cam kết hoàn tác những thay đổi đã xong khi một lần tính sau ném lỗi**.

## 9. Các mẫu cập nhật dữ liệu thường dùng

### 9.1. Đếm tần suất

Với map ban đầu rỗng:

```java
// Đoạn lệnh minh họa, counts là Map<String, Integer>.
for (String word : words) {
    counts.merge(word, 1, Math::addExact);
}
```

Từ xuất hiện lần đầu nhận 1. Những lần sau kết hợp oldCount với 1.

Nếu map đang có word → null, merge cũng đưa nó về 1 mà không gọi hàm cộng. Điều này khác một biểu thức tự mở hộp null rồi cộng.

Cần quyết định kích thước kiểu số. Integer có giới hạn; Long cũng có giới hạn. Dùng Math.addExact giúp phát hiện tràn, nhưng cách xử lý lỗi hoặc chọn kiểu lớn hơn vẫn thuộc thiết kế ứng dụng.

### 9.2. Gộp hai bảng tổng theo cùng khóa

Một phương thức tiện ích minh họa, đặt trong một lớp và cần import Map:

```java
static void addTotals(
        Map<String, Integer> target,
        Map<String, Integer> incoming) {
    for (Map.Entry<String, Integer> entry : incoming.entrySet()) {
        target.merge(entry.getKey(), entry.getValue(), Math::addExact);
    }
}
```

Phương thức giả định các giá trị incoming khác null. Mỗi entry cập nhật một khóa trong target; có thể thêm khóa mới hoặc cộng vào khóa cũ.

Nó không cam kết cả lô là một giao dịch. Nếu một entry sau lỗi, các entry trước có thể đã cập nhật. Cũng không dùng vòng này để sửa một cấu trúc nguồn đồng thời theo cách không được map hỗ trợ; cách đơn giản cho ví dụ là nguồn và đích được kiểm soát rõ.

### 9.3. Nối nội dung theo khóa

```java
// Đoạn lệnh minh họa, messages là Map<String, String>.
messages.merge("topic", "first", (oldText, newText) -> oldText + ", " + newText);
messages.merge("topic", "second", (oldText, newText) -> oldText + ", " + newText);
```

Nếu topic ban đầu thiếu, kết quả sau hai lệnh là `first, second`. Lần đầu không gọi lambda nên không có dấu phẩy ở đầu.

Ghép chuỗi nhiều lần có thể tạo nhiều đối tượng trung gian. Nếu dữ liệu lớn hoặc cần lưu từng mục riêng để xử lý tiếp, một collection làm giá trị có thể phù hợp hơn. Merge không làm mọi kiểu tích lũy có cùng chi phí.

### 9.4. Loại bỏ khóa khi tổng bằng 0

Một hàm gộp có thể tính tổng rồi trả null khi tổng bằng 0, như chương trình đầu.

Nhưng không đặt toàn bộ kiểm tra hợp lệ của đầu vào trong hàm gộp. Với khóa thiếu, incoming được dùng trực tiếp:

```java
// Minh họa bẫy logic, balances chưa có new-key:
balances.merge("new-key", -1,
        (oldValue, delta) -> oldValue + delta <= 0 ? null : oldValue + delta);
```

Hàm không chạy cho new-key, nên map có thể giữ -1. Nếu miền dữ liệu cấm giá trị âm, phải kiểm tra đầu vào hoặc chọn một quy trình cập nhật xử lý cả nhánh khởi tạo, không suy rằng lambda của merge luôn được gọi.

### 9.5. Chuẩn hóa hoặc áp dụng quy tắc cho toàn bộ giá trị

ReplaceAll phù hợp khi mọi ánh xạ hiện có đều cần một phép tính cùng dạng: bỏ khoảng trắng, đổi nhãn, chuẩn hóa null hoặc tính một giá trị từ khóa và dữ liệu cũ.

Nếu chỉ cần sửa một khóa, dùng một thao tác hướng đến khóa đó thường dễ đọc hơn. Nếu cần đổi cả khóa, phải thiết kế việc tạo map mới và xử lý xung đột khóa mới; replaceAll chỉ biến đổi V.

## 10. Lỗi không tự biến thành một lần cập nhật “tất cả hoặc không gì”

### 10.1. Một merge và một lô replaceAll có ranh giới khác

Nếu hàm gộp của merge ném ngoại lệ, ngoại lệ được đưa ra cho mã gọi và ánh xạ đang tính không nhận kết quả mới. Điều đó giả định hàm không tự sửa map hoặc sửa payload đang được chia sẻ.

ReplaceAll có thể đã cập nhật nhiều entry trước khi gặp lỗi. Ngoại lệ không tự khôi phục các giá trị cũ.

Một vòng addTotals gồm nhiều merge cũng là nhiều thao tác. Khóa sau lỗi không tự hoàn tác các khóa đã gộp trước.

### 10.2. Tính trên bản mới nếu cần giữ nguyên nguồn khi việc tính thất bại

Một phương thức minh họa:

```java
static <K, V> Map<K, V> transformedCopy(
        Map<K, V> source,
        BiFunction<? super K, ? super V, ? extends V> transform) {
    Map<K, V> result = new LinkedHashMap<>();
    for (Map.Entry<K, V> entry : source.entrySet()) {
        result.put(entry.getKey(),
                transform.apply(entry.getKey(), entry.getValue()));
    }
    return result;
}
```

Đặt trong một lớp, cần import Map, LinkedHashMap và BiFunction. Phương thức chỉ trả bản kết quả sau khi tính xong. Nếu transform chỉ tính dữ liệu và không tự sửa nguồn/payload, một lỗi giữa chừng không làm cấu trúc source bị cập nhật bởi phương thức.

Đây là một cách tách bước tính khỏi bước áp dụng, không phải một giao dịch nguyên tử cho mọi luồng. Việc công bố hoặc thay bản dữ liệu dùng chung cần chính sách phù hợp của ứng dụng.

Bản sao cấu trúc cũng không tự sao chép sâu mọi đối tượng khóa/giá trị. Nếu transform sửa trực tiếp một đối tượng cũ đang được nguồn giữ, nguồn vẫn có thể quan sát thay đổi đó.

## 11. Giữ hàm tính ngắn, rõ và không tự sửa map

Trong hàm merge hoặc replaceAll, hãy tính giá trị trả về thay vì gọi thêm put/remove/clear trên chính map đang xử lý.

Ví dụ cần tránh:

```java
// Ví dụ sai về thiết kế hàm gộp:
totals.merge("A", 1, (oldValue, incoming) -> {
    totals.remove("B");
    return oldValue + incoming;
});
```

Việc sửa nguồn trong lúc map đang tính/cập nhật có thể bị phát hiện bằng ngoại lệ hoặc gây hành vi không được hợp đồng cho phép. Không dùng một lần không có ngoại lệ để chứng minh cách viết ấy hợp lệ.

Không khẳng định mọi sửa sai đều bị phát hiện ở cùng vị trí. Cơ chế phát hiện khác nhau giữa các lớp và có thể chỉ cố gắng phát hiện theo khả năng.

Các tác vụ như gửi mạng, ghi thông báo hay sửa dữ liệu ngoài map còn tạo tác dụng phụ khó hoàn tác và khó lặp lại an toàn. Giữ hàm tính kết quả rõ ràng giúp việc xử lý lỗi và truy cập đồng thời dễ đánh giá hơn.

Bộ đếm sumCalls ở chương trình đầu chỉ phục vụ quan sát trên ví dụ một luồng. Không dùng cách tăng một biến ngoài trong lambda làm bộ đếm đồng thời hoặc giả định callback luôn chạy đúng một lần trên mọi loại Map.

## 12. Đồng thời: nguyên tử cho một khóa khác với cập nhật cả map

### 12.1. Không suy nguyên tử từ default method của Map

Các phương thức mặc định của Map không tự có bảo đảm đồng bộ hoặc tính nguyên tử. HashMap không trở thành an toàn cho nhiều luồng chỉ vì gọi merge thay cho get rồi put.

Khi có nhiều luồng cập nhật, phải chọn lớp cài đặt hoặc cơ chế đồng bộ có hợp đồng phù hợp.

### 12.2. ConcurrentHashMap.merge có hợp đồng nguyên tử

ConcurrentHashMap thực hiện một lời gọi merge nguyên tử theo hợp đồng của nó. Điều này phù hợp với việc cộng một giá trị vào cùng khóa khi nhiều luồng cùng cập nhật.

Hàm tính nên ngắn và đơn giản vì các cập nhật khác có thể phải chờ trong lúc tính. Không dùng nó để cập nhật các ánh xạ khác trên chính map.

Null từ hàm gộp có thể xóa một ánh xạ của ConcurrentHashMap dù lớp không lưu giá trị null. Đây là dấu hiệu điều khiển thao tác merge, không phải lưu một payload null.

Xem [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#merge(K,V,java.util.function.BiFunction)).

### 12.3. replaceAll không phải một lần thay cả bản đồ nguyên tử

Không suy rằng mọi giá trị thay đổi đồng thời tại một thời điểm chỉ vì tên có chữ All. Với map đồng thời, một luồng khác có thể quan sát hoặc cập nhật dữ liệu trong quá trình xử lý theo hợp đồng của cấu trúc.

Cài đặt mặc định replaceAll của ConcurrentMap có thể thử lại khi có cạnh tranh, bao gồm gọi hàm nhiều lần cho một khóa. Do đó không đặt một tác dụng phụ chỉ được phép xảy ra đúng một lần vào hàm với giả định không có lần gọi lại.

Đối chiếu [ConcurrentMap.replaceAll Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html#replaceAll(java.util.function.BiFunction)).

Một lời gọi merge nguyên tử cho một khóa, một lượt replaceAll và một lô nghiệp vụ nhiều khóa là ba phạm vi khác nhau. Thiết kế đúng phải chọn phạm vi cần bảo đảm trước.

## 13. Lỗi phổ biến và cách chọn lại thao tác

| Hiện tượng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Lambda merge không chạy cho khóa mới | Nhánh thiếu/null dùng incoming trực tiếp | Xử lý kiểm tra khởi tạo ở đúng nơi |
| Đưa null vào merge bị lỗi trên HashMap | Incoming của merge bắt buộc khác null | Phân biệt null đầu vào với null kết quả hàm |
| Trả null trong replaceAll nhưng khóa vẫn còn | Null là giá trị thay thế | Dùng thao tác xóa/lọc nếu cần bỏ entry |
| Tưởng đối số đầu của merge là khóa | Nhầm với replaceAll/compute | Đặt tên oldValue và incoming |
| Gán kết quả replaceAll vào Map | ReplaceAll trả void | Dùng trực tiếp hoặc tự tạo map kết quả |
| Một giá trị null cũ làm hàm chuẩn hóa lỗi | ReplaceAll có thể truyền null vào hàm | Viết chính sách null rõ ràng |
| Sau lỗi dữ liệu đã đổi một phần | Không có rollback toàn lô | Tách bước tính hoặc thiết kế giao dịch phù hợp |
| Hàm gộp tự sửa thêm khóa khác | Sửa map trong lúc đang tính | Trả kết quả, không sửa nguồn trong callback |
| Đếm song song sai trên HashMap | Không có bảo đảm đồng thời | Chọn ConcurrentHashMap hoặc đồng bộ phù hợp |
| Thông báo gửi lặp trong replaceAll đồng thời | Hàm có thể được gọi lại | Tách tác dụng phụ khỏi phép tính |
| Giá trị âm lọt qua dù lambda kiểm tra | Khóa mới bỏ qua lambda merge | Kiểm tra incoming hoặc dùng quy trình xử lý đủ nhánh |

## 14. Bài tập tự kiểm tra có lời giải

### Bài 1: Có gọi hàm không?

Map có A → 2, B → null và chưa có C. Merge từng khóa với incoming 3 và hàm cộng. Kết quả là gì, hàm cộng chạy mấy lần?

**Lời giải:** A → 5, B → 3, C → 3. Hàm chỉ chạy cho A, tổng cộng một lần trong điều kiện một luồng của bài.

### Bài 2: Hàm luôn trả null

Map có A → 2 và chưa có B. Gọi merge(A, 5, hàm trả null) rồi merge(B, 5, cùng hàm). Sau đó còn gì?

**Lời giải:** A bị xóa vì hàm được gọi và trả null. B được tạo với 5 vì nhánh thiếu không gọi hàm.

### Bài 3: Null của replaceAll

HashMap có A → 1 và B → 2. ReplaceAll trả null cho mọi entry. Size có thành 0 không?

**Lời giải:** không. Nếu lượt xử lý thành công, vẫn có hai khóa, mỗi khóa ánh xạ tới null. Muốn xóa mọi ánh xạ cần một thao tác khác như clear theo đúng mục đích.

### Bài 4: Tham số lambda

Trong merge, hai tham số là gì? Trong replaceAll, hai tham số là gì?

**Lời giải:** merge nhận giá trị cũ và giá trị incoming; replaceAll nhận khóa và giá trị hiện tại. Tên biến lambda không thay đổi hợp đồng.

### Bài 5: Lỗi ở entry thứ hai

Một lượt replaceAll đã thay entry đầu rồi hàm ném lỗi ở entry tiếp theo. Có được giả định entry đầu tự trở về giá trị cũ không?

**Lời giải:** không. Phương thức không hứa rollback toàn lô. Cần thiết kế tính trước hoặc giao dịch riêng nếu nghiệp vụ đòi không có cập nhật một phần.

### Bài 6: Kiểm tra âm chỉ trong hàm gộp

Map rỗng. Merge khóa K với incoming -1; lambda sẽ trả null nếu tổng không dương. Có chắc K không xuất hiện không?

**Lời giải:** không. Lambda không chạy khi K thiếu, nên incoming -1 được gắn trực tiếp. Phải xử lý điều kiện hợp lệ cho cả nhánh khởi tạo.

### Bài 7: Một khóa và cả map

ConcurrentHashMap.merge có thao tác nguyên tử. Có suy ra replaceAll trên map đó là một giao dịch nguyên tử của mọi khóa không?

**Lời giải:** không. Bảo đảm cho một thao tác gộp ở một khóa không tự mở rộng thành thay cả map tại một thời điểm.

### Bài 8: Giá trị trả về

A đang có 2. Merge(A, 3, hàm cộng) trả 2 hay 5? ReplaceAll trả map mới hay số lượng thay đổi?

**Lời giải:** merge trả 5 là giá trị sau gộp. ReplaceAll trả void, không trả map mới hoặc số lượng entry.

## 15. Tài liệu đối chiếu

- [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): hợp đồng merge và replaceAll.
- [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): chính sách null của lớp thông dụng trong ví dụ.
- [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html): thứ tự xác định cho các ví dụ in.
- [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): cập nhật đồng thời theo một khóa.
- [ConcurrentMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html): cơ chế thay giá trị hàng loạt và khả năng thử lại.

Chọn merge khi cần đưa thêm dữ liệu vào một khóa; chọn replaceAll khi cần biến đổi giá trị của các ánh xạ hiện có. Luôn xác định riêng ba điều: khi nào hàm được gọi, null trong kết quả có nghĩa gì, và một lỗi hoặc cạnh tranh có thể để lại phạm vi thay đổi nào.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="map-010">equals, hashCode và hợp đồng khóa của Map</a>

<details>
<summary>Xem chi tiết</summary>

# equals, hashCode và hợp đồng khóa của Map

## 1. Map cần biết khi nào hai khóa là một

Giả sử chương trình lưu tồn kho theo mã hàng và kho:

```text
(mã hàng = "PEN", kho = 1) → 10
```

Lần đầu dữ liệu đến từ màn hình nhập hàng. Lần sau, một yêu cầu khác tạo một đối tượng khóa mới cũng mang mã `"PEN"` và kho `1`. Hai đối tượng khác nhau trong bộ nhớ có nên chỉ tới cùng ánh xạ không?

Nếu ý nghĩa nghiệp vụ là “cùng mã hàng, cùng kho”, câu trả lời là có. Đây là **sự bằng nhau theo giá trị hay định danh logic**, khác với việc hai biến trỏ tới đúng cùng một đối tượng.

Với các map thông thường như `HashMap` và `LinkedHashMap`, lớp khóa phải mô tả sự bằng nhau đó bằng `equals`, đồng thời cung cấp `hashCode` nhất quán để map băm tìm được các khóa tương đương.

Nếu thiết kế sai, chương trình có thể gặp các biểu hiện khó hiểu:

- Vừa `put` xong nhưng `get` bằng một khóa tưởng là tương đương lại không thấy.
- Hai khóa `equals` với nhau nhưng map quan sát được hai ánh xạ.
- Sửa một trường của khóa rồi việc tra cứu không còn tìm thấy dữ liệu cũ.
- Đổi từ map băm sang map có thứ tự và số lượng ánh xạ thay đổi vì quy tắc so khóa khác.

Bài học đi từ ý nghĩa của khóa đến hợp đồng Java, rồi kiểm chứng thiết kế đúng và các phản ví dụ. Mục tiêu không phải học thuộc một công thức băm, mà là giữ sự tương ứng ổn định giữa **khóa nghiệp vụ → quan hệ bằng nhau → cơ chế tìm kiếm của map**.

## 2. Phân biệt ==, equals và hashCode

| Cơ chế | Câu hỏi chính |
|---|---|
| `a == b` với kiểu tham chiếu | Hai biến có trỏ tới cùng một đối tượng không? |
| `a.equals(b)` | Hai đối tượng có được lớp của chúng xem là bằng nhau không? |
| `a.hashCode()` | Mã số nguyên phục vụ cơ chế băm của đối tượng là gì? |

Mặc định, `Object.equals` dùng sự đồng nhất tham chiếu: hai đối tượng riêng biệt không bằng nhau chỉ vì các trường trông giống nhau. Một lớp muốn có ngữ nghĩa bằng nhau theo dữ liệu phải định nghĩa hoặc được cung cấp cách triển khai tương ứng.

`String` đã có `equals` và `hashCode` theo nội dung. Vì thế hai chuỗi khác đối tượng nhưng cùng nội dung có thể dùng để tìm cùng một khóa trong `HashMap`.

Đoạn minh họa, đặt trong một phương thức:

```java
String first = new String("PEN");
String second = new String("PEN");

System.out.println(first == second);      // false
System.out.println(first.equals(second)); // true
```

`new String` ở đây chỉ để tạo hai đối tượng khác nhau nhằm quan sát. Mã ứng dụng thông thường không cần tạo chuỗi theo cách này.

Không nên dùng kết quả của `==` với chuỗi literal hoặc số nguyên được đóng hộp để suy ra quy tắc so khóa: cơ chế dùng chung đối tượng có thể khiến một vài trường hợp tình cờ trỏ cùng tham chiếu. Quan hệ logic của khóa cần được xác định độc lập với các tối ưu lưu trữ đó.

### hashCode không phải định danh duy nhất

`hashCode` trả một `int`. Số lượng giá trị `int` hữu hạn, trong khi số đối tượng hay trạng thái có thể phân biệt có thể rất lớn. Do đó hai đối tượng khác nhau hoàn toàn có thể có cùng mã băm.

Đừng dùng riêng `hashCode` như mã khách hàng, khóa duy nhất trong cơ sở dữ liệu, chữ ký bảo mật hoặc bằng chứng hai đối tượng bằng nhau. Nó là một thành phần của cơ chế tìm kiếm, không thay thế phép so sánh logic.

## 3. Hợp đồng equals: một quan hệ tương đương

Với các tham chiếu khác `null`, `equals` phải có những tính chất sau:

| Tính chất | Điều kiện |
|---|---|
| Phản xạ | `x.equals(x)` là `true` |
| Đối xứng | `x.equals(y)` và `y.equals(x)` có cùng kết quả |
| Bắc cầu | Nếu `x.equals(y)` và `y.equals(z)` đều đúng thì `x.equals(z)` cũng đúng |
| Nhất quán | Gọi lại cho cùng dữ liệu liên quan phải có cùng kết quả |
| So với null | Với `x` khác `null`, `x.equals(null)` là `false` |

Tính nhất quán có điều kiện: thông tin dùng để so sánh không bị thay đổi. Nó không nói rằng mọi đối tượng khả biến phải mãi mãi bằng một đối tượng khác sau khi dữ liệu đã đổi. Chính vì khả năng thay đổi này, việc dùng đối tượng khả biến làm khóa phải được kiểm soát.

### Vì sao các tính chất này quan trọng với map?

Nếu quan hệ không đối xứng, kết quả “hai khóa có cùng một ánh xạ không” có thể phụ thuộc hướng so sánh. Nếu không bắc cầu, ba khóa có thể tạo một nhóm “bằng nhau” tự mâu thuẫn. Một cấu trúc dữ liệu không thể biến một định nghĩa khóa mâu thuẫn thành định nghĩa nghiệp vụ nhất quán.

Hãy bắt đầu bằng câu hỏi nghiệp vụ: **những trường nào xác định cùng một khóa?** Sau đó triển khai một quan hệ tương đương đúng cho câu trả lời ấy.

Ví dụ, nếu khóa là cặp mã hàng–kho, hai khóa cùng mã hàng nhưng khác kho phải khác nhau. Nếu chỉ so mã hàng, các kho bị gộp ngoài mong muốn. Nếu đưa thêm tên hiển thị vào sự bằng nhau, đổi tên có thể khiến một mặt hàng bị xem là khóa mới dù nghiệp vụ không muốn vậy.

### Đúng chữ ký mới là ghi đè

Phương thức cần ghi đè là:

```java
// Chữ ký API.
public boolean equals(Object other)
```

Nếu vô tình viết `equals(StockKey other)`, ta tạo một overload chứ không ghi đè `Object.equals(Object)`. Một lời gọi trực tiếp với kiểu cụ thể có thể tạo cảm giác đang hoạt động, trong khi lời gọi qua giao diện mà collection sử dụng không đi theo cách mong muốn.

Dùng `@Override` để trình biên dịch kiểm tra đúng chữ ký. Tương tự, `hashCode` phải là `public int hashCode()`, không phải một phương thức nhận thêm tham số.

## 4. Hợp đồng hashCode: cùng equals thì cùng hash

Ba yêu cầu chính là:

1. Gọi `hashCode` nhiều lần trên cùng đối tượng trong một lần chạy phải cho cùng số, nếu thông tin được dùng trong `equals` không đổi.
2. Nếu `a.equals(b)` là `true`, thì `a.hashCode() == b.hashCode()` phải là `true`.
3. Nếu `a.equals(b)` là `false`, hai mã băm có thể khác nhau hoặc giống nhau.

Chiều suy luận quan trọng:

```text
a.equals(b) == true
        ⇒ a.hashCode() == b.hashCode()

a.hashCode() == b.hashCode()
        ⇏ a.equals(b) == true
```

Hai đối tượng không bằng nhau nhưng cùng mã băm tạo ra **va chạm băm (hash collision)**. Va chạm được các map băm xử lý bằng cơ chế phân biệt tiếp; nó không tự làm hai khóa thành một.

Nếu hai đối tượng bằng nhau mà mã băm khác nhau, đó là vi phạm hợp đồng của lớp khóa. Đây không phải dạng va chạm hợp lệ.

### Mã băm phải dùng thông tin phù hợp với equals

Giả sử `equals` chỉ dùng `id`, còn `hashCode` dùng thêm tên hiển thị có thể khác nhau giữa hai đối tượng cùng `id`. Hai đối tượng bằng nhau có thể nhận mã băm khác nhau, vi phạm hợp đồng.

Một cách thiết kế dễ kiểm tra là dùng cùng các thành phần định danh trong cả hai phương thức. Không bắt buộc mọi trường phải góp vào mã băm theo một cách duy nhất: một hàm băm hằng cũng giữ được yêu cầu “bằng nhau thì cùng băm”, nhưng thường phân bố kém. Đó là sự khác nhau giữa **đúng hợp đồng** và **có chất lượng phân bố tốt**.

Không đưa thời gian hiện tại, số ngẫu nhiên, bộ đếm lời gọi hoặc trạng thái ngoại vi vào `equals`/`hashCode` nếu chúng làm kết quả thay đổi dù định danh khóa không đổi.

### Không dựa vào một con số băm cố định ngoài hợp đồng

Hợp đồng `Object.hashCode` không bắt buộc giữ cùng con số qua các lần chạy ứng dụng. Một số lớp có công thức được tài liệu hóa riêng, nhưng điều đó không biến mọi `hashCode` thành định danh bền vững.

Với `record`, mã băm được suy ra từ các thành phần, nhưng không nên đoán công thức số cụ thể hoặc lưu con số ấy như định danh của bản ghi. Kiểm tra quan hệ cần thiết giữa các khóa bằng nhau thay vì ép mọi hiện thực phải tạo cùng một số theo một công thức do mình đoán.

## 5. HashMap phối hợp hashCode với equals như thế nào?

Ở mức mô hình, có thể hình dung quá trình tra cứu:

```text
Khóa dùng để tra
      ↓
Lấy mã băm và định vị vùng tìm kiếm
      ↓
Xét các ứng viên phù hợp
      ↓
Phân biệt khóa bằng quan hệ bằng nhau
      ↓
Trả giá trị của ánh xạ tìm được
```

Đây là mô hình giải thích, không phải mã nguồn đầy đủ hay công thức chỉ số bucket của mọi phiên bản Java. Hiện thực có thể xử lý thêm mã băm, lưu thông tin băm hoặc tổ chức ứng viên theo những cấu trúc khác nhau.

Điều quan trọng là map băm **không nhất thiết gọi equals với mọi khóa**. Nó có quyền bỏ qua các ứng viên dựa trên thông tin băm. Vì thế câu “hai khóa này `equals` mà sao `get` không thấy?” phải được kiểm tra cùng với mã băm.

Từ hợp đồng hợp lệ:

- Khóa tương đương có cùng mã băm nên có thể được tìm theo cùng cơ chế định vị.
- Khóa khác nhau có thể va chạm; `equals` phân biệt chúng để giữ hai ánh xạ.
- Nếu khóa đổi thông tin xác định sự bằng nhau hoặc băm khi đang được lưu, cấu trúc đã tổ chức trước đó có thể không còn phù hợp.

`LinkedHashMap` bổ sung quản lý thứ tự, nhưng vẫn dựa trên cơ chế map băm cho khóa. Chuyển từ `HashMap` sang `LinkedHashMap` không chữa được một lớp khóa có `equals`/`hashCode` sai.

## 6. Thiết kế khóa đúng: định danh bất biến và hai phương thức nhất quán

Chương trình độc lập dưới đây có hai loại khóa:

- `StockKey`: lớp khóa tự triển khai `equals` và `hashCode` theo cả `sku` và `warehouse`.
- `CollidingKey`: khóa record cố ý trả mã băm hằng để chứng minh va chạm không đồng nghĩa khóa bằng nhau.

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MapKeyContractDemo {
    static final class StockKey {
        private final String sku;
        private final int warehouse;

        StockKey(String sku, int warehouse) {
            this.sku = Objects.requireNonNull(sku, "sku");
            this.warehouse = warehouse;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StockKey key)) {
                return false;
            }
            return warehouse == key.warehouse && sku.equals(key.sku);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sku, warehouse);
        }
    }

    record CollidingKey(int id) {
        @Override
        public int hashCode() {
            return 0;
        }
    }

    public static void main(String[] args) {
        StockKey first = new StockKey("PEN", 1);
        StockKey same = new StockKey("PEN", 1);
        StockKey otherWarehouse = new StockKey("PEN", 2);

        System.out.println("sameReference=" + (first == same));
        System.out.println("equalKeys=" + first.equals(same));
        System.out.println("equalHashes="
                + (first.hashCode() == same.hashCode()));
        System.out.println("otherWarehouseEqual="
                + first.equals(otherWarehouse));

        Map<StockKey, Integer> stock = new HashMap<>();
        System.out.println("firstOld=" + stock.put(first, 10));
        System.out.println("replacementOld=" + stock.put(same, 15));
        System.out.println("sizeAfterEqualKey=" + stock.size());
        System.out.println("lookupWithNewKey="
                + stock.get(new StockKey("PEN", 1)));
        stock.put(otherWarehouse, 7);
        System.out.println("sizeAfterDifferentKey=" + stock.size());

        CollidingKey left = new CollidingKey(1);
        CollidingKey right = new CollidingKey(2);
        Map<CollidingKey, String> collisionMap = new HashMap<>();
        collisionMap.put(left, "left");
        collisionMap.put(right, "right");
        System.out.println("collisionEquals=" + left.equals(right));
        System.out.println("collisionSameHash="
                + (left.hashCode() == right.hashCode()));
        System.out.println("collisionSize=" + collisionMap.size());
        System.out.println("lookupLeft="
                + collisionMap.get(new CollidingKey(1)));
        System.out.println("lookupRight="
                + collisionMap.get(new CollidingKey(2)));
    }
}
```

Lưu vào `MapKeyContractDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapKeyContractDemo.java
java MapKeyContractDemo
```

Kết quả đã kiểm chứng:

```text
sameReference=false
equalKeys=true
equalHashes=true
otherWarehouseEqual=false
firstOld=null
replacementOld=10
sizeAfterEqualKey=1
lookupWithNewKey=15
sizeAfterDifferentKey=2
collisionEquals=false
collisionSameHash=true
collisionSize=2
lookupLeft=left
lookupRight=right
```

### 6.1. Vì sao first và same là cùng khóa logic?

`first == same` là `false`: chúng là hai đối tượng. Nhưng `equals` so cả mã hàng và kho, nên chúng bằng nhau và phải có cùng mã băm.

Khi gọi `stock.put(same, 15)`, map thay giá trị của ánh xạ logic đang có, trả giá trị cũ `10` và giữ kích thước `1`. Một đối tượng khóa mới `new StockKey("PEN", 1)` cũng tra được giá trị `15`.

Điều này cho phép tạo khóa tra cứu từ một yêu cầu mới mà không cần giữ đúng đối tượng khóa đã đưa vào map trước đó.

Không nên suy từ việc thay giá trị thành một cam kết chung rằng đối tượng khóa được lưu bên trong bắt buộc đổi sang tham chiếu vừa truyền vào. Điều được hợp đồng quan tâm ở đây là ánh xạ của khóa logic và giá trị tương ứng.

### 6.2. Các lựa chọn trong StockKey có mục đích gì?

- Lớp `final` giữ phạm vi quan hệ bằng nhau trong một loại khóa cụ thể, tránh việc lớp con bổ sung định danh không nhất quán.
- Hai trường `final` không thể được gán lại sau khi dựng đối tượng.
- `String` là bất biến; `int` là giá trị nguyên thủy. Không có đối tượng con khả biến làm thay đổi định danh.
- `Objects.requireNonNull` thể hiện chính sách mã hàng không được `null`.
- `equals` nhận `Object`, có `@Override`, kiểm tra cùng loại và so các trường định danh.
- `Objects.hash(sku, warehouse)` tính mã băm nhất quán theo hai thành phần đó.

`Objects.hash` là một tiện ích, không phải yêu cầu bắt buộc cho mọi khóa. Dùng nó cũng không tự sửa một lựa chọn trường sai: nếu đưa thêm trường không phù hợp hoặc bỏ sót ngữ nghĩa của mảng, vẫn phải xem lại thiết kế.

### 6.3. Vì sao hai CollidingKey vẫn tồn tại riêng?

`CollidingKey(1)` và `CollidingKey(2)` có cùng mã băm `0` nhưng không bằng nhau vì `id` khác. Map giữ hai ánh xạ và tra đúng từng giá trị.

Hàm băm hằng trong chương trình là công cụ thí nghiệm. Nó không vi phạm yêu cầu cùng khóa thì cùng băm, nhưng có thể làm nhiều khóa dồn vào cùng vùng xử lý và giảm hiệu quả. Không cần thiết kế một mã băm duy nhất cho mọi khóa; cần tính đúng trước và phân bố thích hợp sau.

## 7. Phản ví dụ: equals đúng ý tưởng nhưng hashCode bất nhất

Chương trình sau **cố ý định nghĩa một lớp khóa sai**. `BadKey.equals` chỉ so `id`, nhưng `hashCode` trả trường `wrongHash` được truyền riêng. Hai khóa cùng `id` vì vậy có thể nhận mã băm khác nhau.

Các dòng quan sát map dưới đây là kết quả thực nghiệm trên JDK 24.0.2. Vì lớp khóa đã vi phạm hợp đồng, không được dùng kết quả ấy như một quy tắc đúng mà mọi map hoặc mọi hiện thực bắt buộc phải giữ.

```java
import java.util.HashMap;
import java.util.Map;

public class MapBrokenHashDemo {
    static final class BadKey {
        private final int id;
        private final int wrongHash;

        BadKey(int id, int wrongHash) {
            this.id = id;
            this.wrongHash = wrongHash;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BadKey key && id == key.id;
        }

        @Override
        public int hashCode() {
            return wrongHash;
        }
    }

    public static void main(String[] args) {
        BadKey first = new BadKey(7, 7);
        BadKey second = new BadKey(7, 8);

        System.out.println("equalKeys=" + first.equals(second));
        System.out.println("equalHashes="
                + (first.hashCode() == second.hashCode()));

        Map<BadKey, String> map = new HashMap<>();
        map.put(first, "first");
        map.put(second, "second");
        System.out.println("observedSize=" + map.size());
        System.out.println("probeHash7=" + map.get(new BadKey(7, 7)));
        System.out.println("probeHash8=" + map.get(new BadKey(7, 8)));
    }
}
```

Lưu vào `MapBrokenHashDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapBrokenHashDemo.java
java MapBrokenHashDemo
```

Kết quả đã kiểm chứng:

```text
equalKeys=true
equalHashes=false
observedSize=2
probeHash7=first
probeHash8=second
```

### Đọc kết quả để xác định lỗi ở đâu

Hai dòng đầu đủ để chứng minh vi phạm:

```text
equalKeys=true
equalHashes=false
```

`observedSize=2` cùng hai kết quả tra cứu khác nhau cho thấy hậu quả có thể rất khó hiểu: hai đối tượng mà lớp khóa tự nhận là bằng nhau vẫn dẫn tới các đường tìm kiếm khác nhau trong map băm đang chạy.

Đây không phải lỗi khiến `HashMap` phải “quét hết để sửa hộ” khóa. Hợp đồng cho phép hiện thực tận dụng thông tin băm để tránh so sánh không cần thiết. Lớp khóa có trách nhiệm cung cấp thông tin nhất quán.

Một lỗi thực tế tương tự là ghi đè `equals` nhưng để nguyên `Object.hashCode`. Nếu hai đối tượng được xem là bằng nhau theo dữ liệu nhưng nhận mã băm theo cơ chế mặc định không phù hợp, map băm có thể không hoạt động như nghiệp vụ mong muốn. Không nên dựa vào việc một bộ dữ liệu tình cờ có cùng mã băm để cho rằng lớp đã đúng.

### Cách sửa theo nguyên nhân

1. Xác định chính xác thành phần định danh, chẳng hạn `id`.
2. Cho `equals` so thành phần ấy theo một quan hệ tương đương.
3. Cho `hashCode` suy ra mã băm nhất quán từ định danh.
4. Giữ định danh ổn định trong suốt thời gian khóa được dùng trong map.
5. Kiểm tra bằng hai hoặc nhiều đối tượng khác nhau nhưng cùng định danh, thay vì chỉ thử lại đúng một tham chiếu.

Không sửa bằng cách ép mọi lời gọi tra cứu phải dùng đúng đối tượng ban đầu nếu nghiệp vụ cần sự bằng nhau theo giá trị. Cách đó chỉ che việc thiết kế khóa chưa đáp ứng nhu cầu.

## 8. Khóa khả biến: đừng thay định danh khi đang nằm trong map

Một map không sao chép sâu khóa khi `put`. Nếu đưa một đối tượng khả biến vào làm khóa, chương trình vẫn có thể giữ một tham chiếu khác tới đối tượng đó và sửa nó.

Nguy hiểm xuất hiện khi thông tin bị sửa là thông tin tham gia vào `equals`, `hashCode` hoặc quy tắc so thứ tự của map đang dùng:

```text
Lúc đưa vào map:
    khóa có định danh 20
    map tổ chức ánh xạ theo thông tin đó

Sau khi sửa trực tiếp:
    cùng đối tượng khóa có định danh 21
    map không tự thực hiện một thao tác đổi khóa hợp lệ
```

`Map` không bảo đảm hành vi nếu đối tượng khóa bị thay đổi theo cách ảnh hưởng các so sánh `equals` trong khi đang làm khóa. Với map băm, thay mã băm cũng có thể khiến đường tìm kiếm mới không còn trùng thông tin đã dùng khi lưu.

Điều này không có nghĩa mọi trường của một đối tượng khóa đều bắt buộc bất biến trong mọi thiết kế. Điều cần giữ ổn định là thông tin xác định khóa theo hợp đồng của cấu trúc. Tuy nhiên, tách một kiểu khóa nhỏ bất biến khỏi dữ liệu mô tả có thể đổi thường dễ kiểm soát hơn dùng một đối tượng lớn có nhiều người sửa.

### Chương trình đầy đủ: quan sát lỗi và cách dùng khóa mới

Phần `unsafe` dưới đây **cố ý vi phạm điều kiện sử dụng khóa**. Các dòng có tiền tố `observed` là kết quả quan sát trên JDK 24.0.2, không phải lời hứa về hành vi khi đã vi phạm hợp đồng.

Phần `safe` dùng khóa record bất biến. Khi cần chuyển định danh, nó bỏ ánh xạ cũ và tạo ánh xạ với một khóa mới, thay vì sửa đối tượng đang được map sử dụng.

```java
import java.util.HashMap;
import java.util.Map;

public class MapMutableKeyDemo {
    static final class MutableKey {
        private int id;

        MutableKey(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MutableKey key && id == key.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    record StableKey(int id) {
    }

    public static void main(String[] args) {
        MutableKey key = new MutableKey(20);
        Map<MutableKey, String> unsafe = new HashMap<>();
        unsafe.put(key, "payload");
        System.out.println("beforeMutation=" + unsafe.get(key));
        System.out.println("hashBefore=" + key.hashCode());

        key.id = 21;
        System.out.println("hashAfter=" + key.hashCode());
        System.out.println("observedSize=" + unsafe.size());
        System.out.println("observedSameReferenceLookup=" + unsafe.get(key));
        System.out.println("observedOldIdLookup="
                + unsafe.get(new MutableKey(20)));

        boolean referenceStillSeen = false;
        for (Map.Entry<MutableKey, String> entry : unsafe.entrySet()) {
            if (entry.getKey() == key) {
                referenceStillSeen = true;
            }
        }
        System.out.println("observedReferenceInEntries=" + referenceStillSeen);

        Map<StableKey, String> safe = new HashMap<>();
        StableKey oldKey = new StableKey(20);
        StableKey newKey = new StableKey(21);
        safe.put(oldKey, "payload");

        if (safe.containsKey(newKey)) {
            throw new IllegalStateException("Target key already exists");
        }
        String moved = safe.remove(oldKey);
        safe.put(newKey, moved);

        System.out.println("safeHasOldKey=" + safe.containsKey(oldKey));
        System.out.println("safeNewKeyValue=" + safe.get(new StableKey(21)));
        System.out.println("safeSize=" + safe.size());
    }
}
```

Lưu vào `MapMutableKeyDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapMutableKeyDemo.java
java MapMutableKeyDemo
```

Kết quả đã kiểm chứng:

```text
beforeMutation=payload
hashBefore=20
hashAfter=21
observedSize=1
observedSameReferenceLookup=null
observedOldIdLookup=null
observedReferenceInEntries=true
safeHasOldKey=false
safeNewKeyValue=payload
safeSize=1
```

### 8.1. Vì sao size vẫn là 1 nhưng get lại trả null?

Trong lần chạy đã kiểm chứng, đối tượng khóa vẫn quan sát được khi duyệt entry, nhưng tra bằng cùng tham chiếu sau khi đổi `id` lại không tìm được. Việc giữ đúng tham chiếu không giúp map bỏ qua toàn bộ bước định vị băm.

Không nên giải thích kết quả `null` này thành “map chắc chắn đã xóa dữ liệu”. Các dòng quan sát cho thấy điều khác: dữ liệu có thể vẫn nằm trong cấu trúc nhưng không còn được tìm theo cách đang gọi.

Cũng không nên viết logic dựa vào việc mọi khóa sau khi sửa chắc chắn sẽ không tìm được. Tùy cách thay đổi, mã băm, tổ chức hiện thực và dữ liệu, một vài trường hợp có thể tình cờ vẫn tra được. Khi điều kiện hợp đồng đã mất, một lần tra được không khôi phục tính đúng đắn của thiết kế.

### 8.2. Đổi khóa phải là một thay đổi được kiểm soát

Phần `safe` giữ `StableKey(20)` nguyên vẹn, lấy giá trị ra khỏi ánh xạ cũ và đưa vào ánh xạ mới với `StableKey(21)`. Khóa cũ không bị biến thành khóa khác trong khi đang được lưu.

Chương trình kiểm tra khóa đích đã có hay chưa vì “đổi khóa” có thể va vào một ánh xạ đang tồn tại. Ứng dụng phải quyết định rõ là từ chối, ghi đè hay hợp nhất theo nghiệp vụ; không để một `put` vô tình quyết định thay.

Ví dụ chỉ có một luồng sở hữu map, các giá trị đều đã biết và hợp lệ. Cặp `remove` rồi `put` không tự là một giao dịch nguyên tử. Nếu có nhiều luồng hoặc có yêu cầu hoàn tác khi lỗi, phải thiết kế cơ chế phối hợp tương ứng.

### 8.3. Đừng trông chờ sửa chữa tự động

Các cách sau không phải cách chữa hợp đồng khóa:

- Đổi sang `LinkedHashMap`: cơ chế khóa băm vẫn cần nhất quán.
- Đổi sang một map đồng thời: bảo đảm truy cập đồng thời không làm khóa khả biến trở thành khóa ổn định.
- Tăng dung lượng hoặc thêm phần tử để mong map “sắp lại cho đúng”: không có bảo đảm phục hồi định danh nghiệp vụ theo cách đó.
- Chỉ lưu mã băm cũ trong khóa rồi cho `equals` thay đổi tự do: có thể tiếp tục tạo một quan hệ định danh không phù hợp.
- Bọc map thành không cho sửa: lớp bọc không tự đóng băng đối tượng khóa bên trong.

Nếu dữ liệu đã bị đưa vào với các khóa không hợp lệ, cần đánh giá lại dữ liệu và chính sách xử lý các định danh trùng trước khi dựng một cấu trúc đúng. Không nên âm thầm chép sang map khác rồi mặc định mọi thông tin đã được phục hồi.

## 9. Record giúp viết khóa, nhưng không tự làm mọi dữ liệu bất biến sâu

Với một khóa gồm các thành phần ổn định như `String` và `int`, `record` thường là cách biểu diễn gọn:

```java
// Minh họa một kiểu khóa theo cả mã hàng và kho.
record StockKey(String sku, int warehouse) {
}
```

Nếu không tự ghi đè, record sinh `equals` và `hashCode` dựa trên các thành phần. Sự bằng nhau yêu cầu cùng lớp record và các thành phần tương ứng bằng nhau theo quy tắc record.

Tuy vậy, trường thành phần giữ một tham chiếu `final` không có nghĩa đối tượng được tham chiếu không thể bị sửa. Một thành phần `List<String>` có thể trỏ tới `ArrayList` mà nơi khác vẫn còn quyền sửa. Một thành phần mảng cũng có quy tắc bằng nhau riêng cần hiểu.

### Chương trình đầy đủ: danh sách được bảo vệ và mảng so theo tham chiếu

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapRecordKeyDemo {
    record MutableTags(List<String> tags) {
    }

    record StableTags(List<String> tags) {
        StableTags {
            tags = List.copyOf(tags);
        }
    }

    record ArrayKey(int[] codes) {
    }

    public static void main(String[] args) {
        List<String> mutableInput = new ArrayList<>(List.of("java"));
        MutableTags unstable = new MutableTags(mutableInput);
        mutableInput.add("map");
        System.out.println("unstableTags=" + unstable.tags());
        System.out.println("unstableEqualsOneTag="
                + unstable.equals(new MutableTags(List.of("java"))));

        List<String> input = new ArrayList<>(List.of("java"));
        StableTags stable = new StableTags(input);
        Map<StableTags, String> map = new HashMap<>();
        map.put(stable, "saved");
        int initialHash = stable.hashCode();

        input.add("map");
        System.out.println("stableTags=" + stable.tags());
        System.out.println("stableHashUnchanged="
                + (initialHash == stable.hashCode()));
        System.out.println("stableLookup="
                + map.get(new StableTags(List.of("java"))));

        try {
            stable.tags().add("blocked");
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println("stableListMutation=UnsupportedOperationException");
        }

        ArrayKey first = new ArrayKey(new int[] {1, 2});
        ArrayKey second = new ArrayKey(new int[] {1, 2});
        System.out.println("sameArrayContents="
                + Arrays.equals(first.codes(), second.codes()));
        System.out.println("arrayRecordsEqual=" + first.equals(second));

        Map<ArrayKey, String> arrayMap = new HashMap<>();
        arrayMap.put(first, "first");
        arrayMap.put(second, "second");
        System.out.println("arrayMapSize=" + arrayMap.size());
    }
}
```

Lưu vào `MapRecordKeyDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapRecordKeyDemo.java
java MapRecordKeyDemo
```

Kết quả đã kiểm chứng:

```text
unstableTags=[java, map]
unstableEqualsOneTag=false
stableTags=[java]
stableHashUnchanged=true
stableLookup=saved
stableListMutation=UnsupportedOperationException
sameArrayContents=true
arrayRecordsEqual=false
arrayMapSize=2
```

### 9.1. Vì sao MutableTags vẫn thay đổi?

`MutableTags` giữ thẳng danh sách đầu vào. Sau `mutableInput.add("map")`, lời gọi accessor nhìn thấy `[java, map]`. Quan hệ bằng nhau của record cũng có thể thay đổi vì nội dung danh sách tham gia so sánh.

Trong chương trình, đối tượng này được dùng để quan sát dữ liệu và sự bằng nhau, chưa đưa vào map. Nếu dùng nó làm khóa rồi tiếp tục sửa danh sách như vậy, ta có nguy cơ vi phạm điều kiện ổn định của khóa.

Điểm sai không nằm ở khả năng record sinh `equals`/`hashCode` theo thành phần. Điểm cần thiết kế là thành phần có đúng ngữ nghĩa và đủ ổn định để làm định danh hay không.

### 9.2. StableTags bảo vệ điều gì?

Hàm dựng của `StableTags` dùng `List.copyOf(tags)` để giữ một danh sách không cho sửa tách khỏi các thay đổi cấu trúc của danh sách đầu vào. Với các phần tử là `String` bất biến, định danh của ví dụ được giữ ổn định.

Sau khi sửa `input` bên ngoài:

- `stable.tags()` vẫn là `[java]`.
- Mã băm của cùng khóa không thay đổi.
- Khóa mới `StableTags(List.of("java"))` tra được giá trị `"saved"`.
- Thử thêm trực tiếp qua accessor bị từ chối.

Cách này áp dụng một chính sách rõ ràng: danh sách và các phần tử của nó không được `null` theo điều kiện của `List.copyOf`. Nếu ứng dụng có yêu cầu khác, phải chọn cách kiểm tra và sao chép phù hợp.

Nếu phần tử danh sách là đối tượng khả biến, chỉ `List.copyOf` chưa tự sao chép sâu các phần tử. Khi ấy cần xem thêm trạng thái nào của từng phần tử tham gia `equals`/`hashCode`.

### 9.3. Vì sao ArrayKey có hai khóa dù các số giống nhau?

Mảng Java không tự định nghĩa `equals` theo từng phần tử. Hai mảng `new int[] {1, 2}` khác đối tượng không bằng nhau chỉ vì cùng nội dung.

Record dùng quy tắc bằng nhau của thành phần tham chiếu; nó không tự chuyển mọi mảng thành so sánh sâu. Vì vậy:

```text
sameArrayContents=true
arrayRecordsEqual=false
arrayMapSize=2
```

Điều này không tự là vi phạm hợp đồng Java. Hai khóa đang được phân biệt theo tham chiếu mảng, và mã băm phải phù hợp với cách bằng nhau ấy. Nhưng nếu nghiệp vụ muốn “cùng các mã số thì cùng khóa”, thiết kế đó chưa biểu đạt đúng nhu cầu.

Nếu tự triển khai khóa so mảng theo nội dung, phải phối hợp `Arrays.equals` với cách băm nội dung phù hợp như `Arrays.hashCode`, đồng thời sao chép bảo vệ đầu vào và không để bên ngoài sửa mảng xác định khóa. Chỉ sửa `equals` mà quên `hashCode`, hoặc sao chép lúc dựng nhưng trả thẳng mảng nội bộ cho nơi khác sửa, vẫn có thể làm khóa sai.

Đối với cấu trúc lồng nhau, cần xác định mức so sánh nông hay sâu theo nghiệp vụ. Không mặc định một tiện ích “deep” bất kỳ sẽ tự chọn đúng định danh cho dữ liệu.

## 10. Hợp đồng khóa còn phụ thuộc loại map được chọn

Phần lớn bài dùng `HashMap` và quan hệ bằng nhau theo dữ liệu. Không được kết luận mọi lớp triển khai `Map` đều sử dụng `hashCode` để tìm khóa.

| Loại map | Cơ chế nhận diện khóa cần hiểu |
|---|---|
| `HashMap`, `LinkedHashMap` | Cơ chế băm kết hợp quan hệ bằng nhau của khóa |
| `TreeMap` | Kết quả so sánh của thứ tự tự nhiên hoặc comparator |
| `IdentityHashMap` | Sự đồng nhất tham chiếu `==` thay cho sự bằng nhau theo dữ liệu |

### 10.1. TreeMap dùng compareTo hoặc Comparator

Với `TreeMap`, hai khóa có kết quả so sánh bằng `0` được xem là cùng vị trí khóa trong cấu trúc cây. `hashCode` không phải cơ chế định vị khóa của nó.

Để thực hiện đúng hợp đồng chung của `Map`, quan hệ thứ tự cần nhất quán với `equals`: với các khóa hợp lệ, kết quả so sánh bằng `0` phải tương ứng với sự bằng nhau theo `equals`.

Nếu comparator chỉ so tên còn `equals` so cả tên và mã định danh, hai đối tượng khác mã nhưng cùng tên có thể bị map cây gộp. Thêm một trường vào công thức băm không chữa được chuyện này; phải xem lại quy tắc so thứ tự.

Một trường hợp đáng quan sát là `BigDecimal`:

- `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là `false`, vì `equals` xét cả giá trị và scale.
- `compareTo` giữa hai giá trị đó trả `0`, vì thứ tự số học coi chúng bằng giá trị số.

Do đó map băm và `TreeMap` dùng thứ tự tự nhiên có thể giữ số lượng khóa khác nhau. `BigDecimal` không bị lỗi `equals`/`hashCode` trong trường hợp này. Đây là sự khác nhau giữa ngữ nghĩa bằng nhau và ngữ nghĩa thứ tự cần được người thiết kế chọn rõ.

`TreeMap` vẫn có hành vi được định nghĩa theo thứ tự của nó khi thứ tự không nhất quán với `equals`, nhưng khi đó không đáp ứng hợp đồng chung của `Map`. Không nên dùng trường hợp đó để phát biểu rằng hợp đồng chung không còn ý nghĩa.

### 10.2. IdentityHashMap là lựa chọn chuyên biệt

`IdentityHashMap` cố ý dùng `==` khi so khóa và giá trị. Hai đối tượng chuỗi khác nhau nhưng cùng nội dung có thể được giữ như hai khóa riêng.

Đây là hiện thực dành cho những nhu cầu cần nhận diện **đúng từng đối tượng**, chẳng hạn theo dõi các nút riêng trong đồ thị đối tượng. Nó không phải cách thay thế mặc định cho `HashMap` dùng định danh nghiệp vụ.

Tài liệu của lớp nêu rõ việc chủ ý khác hợp đồng bằng nhau chung của `Map`. Vì vậy không truyền nó vào một thuật toán vốn dựa trên ngữ nghĩa bằng nhau thông thường mà không xem lại giả thiết.

### 10.3. Null là chính sách của hiện thực, không phải một phương thức trên null

`HashMap` cho phép một khóa `null`; map xử lý trường hợp này riêng. Nó không cần chương trình gọi `null.hashCode()` hoặc `null.equals(...)`.

Điều này khác với hợp đồng `x.equals(null) == false` khi `x` khác `null`. Đừng nhầm việc “map cho phép khóa null” với việc có thể gọi phương thức trên một tham chiếu null.

Một số map cấm khóa hoặc giá trị `null`; map có thứ tự còn phụ thuộc khả năng comparator xử lý khóa. Chỉ từ kiểu khai báo `Map` không thể suy ra mọi chính sách chấp nhận dữ liệu.

## 11. Phân biệt equals/hashCode của khóa, entry và cả map

Có ba tầng với ba câu hỏi:

| Tầng | Câu hỏi của equals |
|---|---|
| Đối tượng khóa | Hai đối tượng có cùng định danh logic theo kiểu khóa không? |
| `Map.Entry` | Hai cặp có cùng khóa và cùng giá trị không? |
| `Map` | Hai map có cùng tập ánh xạ không? |

Nhầm các tầng này dễ dẫn tới một cách kiểm tra thiếu dữ liệu.

### 11.1. Map.Entry xét cả khóa lẫn giá trị

Với các entry tuân thủ hợp đồng thông thường, có thể diễn đạt sự bằng nhau bằng:

```java
// Công thức minh họa, không phải chương trình độc lập.
Objects.equals(firstEntry.getKey(), secondEntry.getKey())
        && Objects.equals(firstEntry.getValue(), secondEntry.getValue())
```

Mã băm của entry được xác định bằng phép XOR mã băm của khóa và giá trị, dùng `0` cho thành phần `null`:

```java
// Công thức hợp đồng Map.Entry.
Objects.hashCode(entry.getKey()) ^ Objects.hashCode(entry.getValue())
```

Ký hiệu `^` ở đây là XOR bit của Java, không phải phép lũy thừa. `Objects.hashCode(null)` cho `0`, giúp công thức xử lý thành phần null mà không gọi phương thức trên null.

Khóa bằng nhau nhưng giá trị khác nhau tạo hai entry khác nhau. Hai entry có cùng mã băm cũng chưa đủ để kết luận entry bằng nhau.

### 11.2. Map.equals xét ánh xạ, không xét thứ tự chèn

Theo hợp đồng chung, hai map bằng nhau nếu chúng biểu diễn cùng các ánh xạ. Cách mô tả chính thức dựa trên sự bằng nhau của `entrySet`.

Vì vậy hai `LinkedHashMap` có cùng khóa và giá trị nhưng được chèn theo thứ tự ngược nhau vẫn có thể bằng nhau. Thứ tự quan sát qua iterator không phải một thành phần thêm vào định nghĩa `Map.equals`.

Các hiện thực thông thường khác nhau cũng có thể bằng nhau nếu cùng ánh xạ. Điều này giả định các khóa, giá trị và hiện thực liên quan tuân thủ hợp đồng bằng nhau chung; các trường hợp chuyên biệt như `IdentityHashMap` hoặc thứ tự không nhất quán cần được xét theo hợp đồng riêng đã nêu.

Một map có `"a" → null` không bằng map rỗng. Nó cũng không bằng map chỉ có `"b" → null`. Chỉ so kết quả `get` mà bỏ qua sự tồn tại và định danh khóa có thể bỏ mất khác biệt này.

### 11.3. Map.hashCode là tổng mã băm của các entry

Mã băm của map theo hợp đồng chung là tổng mã băm của từng entry. Phép cộng không phụ thuộc thứ tự duyệt, phù hợp với việc `Map.equals` không coi thứ tự chèn là khác biệt.

Kết quả giữ yêu cầu:

```text
Hai map bằng nhau ⇒ cùng mã băm.
Cùng mã băm      ⇏ hai map bằng nhau.
```

Tổng được tính theo kiểu `int` của Java. Không nên thay nó bằng một công thức chuỗi phụ thuộc thứ tự rồi vẫn tuyên bố đó là `Map.hashCode` theo hợp đồng.

### Vì sao một map khả biến thường không phù hợp làm khóa của map khác?

Mã băm của map có thể thay đổi khi các ánh xạ hoặc dữ liệu tham gia băm thay đổi. Nếu dùng map ấy làm khóa trong một `HashMap` ngoài rồi sửa nó, ta gặp lại chính vấn đề khóa không ổn định.

Tương tự, sửa giá trị khả biến bên trong một map có thể không làm thay đổi khóa tra cứu của map đó, nhưng có thể ảnh hưởng `Map.equals` hoặc `Map.hashCode` của cả map. Phải biết đang nói về tầng nào trước khi kết luận một thay đổi có an toàn cho vai trò khóa hay không.

## 12. Chương trình đầy đủ: bằng nhau ở từng tầng và các chính sách khóa

Chương trình sau ghép ba quan sát có liên hệ:

1. Hai map cùng ánh xạ dù thứ tự chèn khác nhau.
2. `HashMap` và `TreeMap` nhận diện hai khóa `BigDecimal` khác nhau theo cơ chế riêng.
3. `IdentityHashMap` phân biệt hai đối tượng chuỗi mà `HashMap` coi là cùng khóa logic.

```java
import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class MapEqualityPoliciesDemo {
    public static void main(String[] args) {
        Map<String, Integer> first = new LinkedHashMap<>();
        first.put("a", 1);
        first.put("b", 2);

        Map<String, Integer> reversed = new LinkedHashMap<>();
        reversed.put("b", 2);
        reversed.put("a", 1);

        System.out.println("firstKeys=" + first.keySet());
        System.out.println("reversedKeys=" + reversed.keySet());
        System.out.println("mapsEqual=" + first.equals(reversed));
        System.out.println("mapHashesEqual="
                + (first.hashCode() == reversed.hashCode()));

        int entryHashSum = 0;
        for (Map.Entry<String, Integer> entry : first.entrySet()) {
            entryHashSum += entry.hashCode();
        }
        System.out.println("mapHashMatchesEntrySum="
                + (first.hashCode() == entryHashSum));

        Map.Entry<String, Integer> pair =
                new AbstractMap.SimpleImmutableEntry<>("a", 1);
        System.out.println("entryEquals="
                + pair.equals(Map.entry("a", 1)));
        System.out.println("entryHashMatchesXor="
                + (pair.hashCode()
                == (Objects.hashCode(pair.getKey())
                ^ Objects.hashCode(pair.getValue()))));

        BigDecimal scaleOne = new BigDecimal("1.0");
        BigDecimal scaleTwo = new BigDecimal("1.00");
        Map<BigDecimal, String> hashMap = new HashMap<>();
        Map<BigDecimal, String> treeMap = new TreeMap<>();
        hashMap.put(scaleOne, "first");
        hashMap.put(scaleTwo, "second");
        treeMap.put(scaleOne, "first");
        treeMap.put(scaleTwo, "second");

        System.out.println("decimalEquals=" + scaleOne.equals(scaleTwo));
        System.out.println("decimalCompare=" + scaleOne.compareTo(scaleTwo));
        System.out.println("decimalHashMapSize=" + hashMap.size());
        System.out.println("decimalTreeMapSize=" + treeMap.size());
        System.out.println("treeLookup=" + treeMap.get(scaleOne));

        String left = new String("id");
        String right = new String("id");
        Map<String, Integer> normal = new HashMap<>();
        Map<String, Integer> identity = new IdentityHashMap<>();
        normal.put(left, 1);
        normal.put(right, 2);
        identity.put(left, 1);
        identity.put(right, 2);

        System.out.println("stringEquals=" + left.equals(right));
        System.out.println("stringSameReference=" + (left == right));
        System.out.println("normalSize=" + normal.size());
        System.out.println("identitySize=" + identity.size());
    }
}
```

Lưu vào `MapEqualityPoliciesDemo.java`, rồi biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapEqualityPoliciesDemo.java
java MapEqualityPoliciesDemo
```

Kết quả đã kiểm chứng:

```text
firstKeys=[a, b]
reversedKeys=[b, a]
mapsEqual=true
mapHashesEqual=true
mapHashMatchesEntrySum=true
entryEquals=true
entryHashMatchesXor=true
decimalEquals=false
decimalCompare=0
decimalHashMapSize=2
decimalTreeMapSize=1
treeLookup=second
stringEquals=true
stringSameReference=false
normalSize=1
identitySize=2
```

### 12.1. Nhóm map và entry

`firstKeys=[a, b]` khác thứ tự `reversedKeys=[b, a]`, nhưng `mapsEqual=true` và `mapHashesEqual=true`. Dòng `mapHashMatchesEntrySum=true` kiểm chứng tổng mã băm entry.

Một entry được dựng bằng `SimpleImmutableEntry` bằng entry có cùng cặp dữ liệu từ `Map.entry`. Dòng `entryHashMatchesXor=true` kiểm chứng công thức hợp đồng, không phụ thuộc việc hai entry là cùng một lớp cụ thể.

### 12.2. Nhóm BigDecimal

`decimalEquals=false`, nhưng `decimalCompare=0`. Vì vậy:

- `HashMap` giữ hai khóa theo `equals`, kích thước là `2`.
- `TreeMap` dùng thứ tự tự nhiên coi kết quả so sánh bằng `0` là cùng khóa, kích thước là `1`.
- Giá trị sau cùng ở vị trí khóa trong `TreeMap` là `"second"`.

Nếu nghiệp vụ muốn phân biệt scale, phải chọn quy tắc và cấu trúc phù hợp. Nếu muốn chuẩn hóa để bỏ một khác biệt biểu diễn, việc chuẩn hóa phải được quyết định theo nghiệp vụ và áp dụng nhất quán cho cả ghi lẫn tra cứu. Không tự xóa khác biệt dữ liệu chỉ để làm các map cho cùng kích thước.

### 12.3. Nhóm đồng nhất tham chiếu

Hai chuỗi `left` và `right` có cùng nội dung nhưng khác đối tượng:

```text
stringEquals=true
stringSameReference=false
normalSize=1
identitySize=2
```

Không có mâu thuẫn: hai map đang áp dụng hai chính sách nhận diện khóa khác nhau. Chọn đúng chính sách là một phần của thiết kế, không chỉ là lựa chọn tên lớp.

## 13. Những quyết định thiết kế cần làm trước khi viết công thức băm

### Chọn đúng thành phần định danh

Với dữ liệu nhiều tổ chức, cùng một mã tài khoản trong hai tổ chức có thể là hai khóa khác nhau; khóa cần thể hiện cả tổ chức nếu đó là quy tắc nghiệp vụ. Với tồn kho, cùng mã hàng ở hai kho cũng vậy.

Ngược lại, thông tin mô tả thường xuyên thay đổi như tên hiển thị hoặc ghi chú có thể nên nằm trong giá trị, thay vì vô tình làm khóa đổi định danh.

Không phải mọi thuộc tính của một đối tượng dữ liệu đều cần tham gia `equals`. Nhưng những thuộc tính được chọn phải mô tả đúng sự bằng nhau của **kiểu đó**. Nếu cùng một đối tượng cần được so theo nhiều tiêu chí khác nhau, có thể tách các kiểu khóa hay phép so sánh chuyên dụng, thay vì để `equals` thay đổi theo ngữ cảnh bên ngoài.

### Kế thừa phải giữ tính đối xứng và bắc cầu

Một lớp cha coi hai đối tượng bằng nhau theo một mã, còn lớp con đòi thêm một thuộc tính, có thể làm phát sinh tình trạng cha bằng con nhưng con không bằng cha.

Không có một quy tắc “luôn dùng instanceof” hoặc “luôn dùng getClass” giải quyết mọi mô hình. Cần xác định các lớp có thuộc cùng một miền bằng nhau hay không và giữ hợp đồng cho mọi cặp liên quan.

Với kiểu khóa giá trị nhỏ, thiết kế `final`, `record` hoặc dùng thành phần thường giảm khó khăn này. Nếu cho phép mở rộng kiểu khóa, phải kiểm tra quan hệ giữa lớp cha và lớp con một cách chủ động.

### Chuẩn hóa phải thuộc quy tắc dữ liệu

Nếu mã khóa được xem là không phân biệt hoa/thường, khoảng trắng hoặc một biểu diễn đặc biệt, hãy quyết định cách biểu diễn chuẩn trước khi lưu và tra cứu. Không nên chỉ chuẩn hóa trong `hashCode` còn `equals` giữ một quy tắc khác.

Cũng không nên tự động cắt khoảng trắng hay đổi chữ của mọi mã khi nghiệp vụ chưa cho phép: sự khác biệt tưởng như hình thức có thể là phần có ý nghĩa của định danh.

### So sánh gần đúng thường không phải equals hợp lệ

Quy tắc “hai số cách nhau dưới một ngưỡng thì bằng nhau” có thể không bắc cầu. Ví dụ, với ngưỡng `1`, số `0` gần `0.75`, `0.75` gần `1.5`, nhưng `0` không gần `1.5`.

Đó có thể là một phép kiểm tra gần nhau hữu ích, nhưng không nên đưa thẳng vào `equals` của khóa map như một quan hệ tương đương. Nếu cần nhóm dữ liệu theo khoảng, phải thiết kế một định danh nhóm rõ ràng và kiểm tra ngữ nghĩa của việc nhóm.

## 14. Kiểm chứng một lớp khóa

Một lần thử chỉ bằng đúng đối tượng vừa đưa vào map là quá yếu. Nên kiểm tra ít nhất các nhóm tình huống sau:

| Nhóm | Điều cần kiểm tra |
|---|---|
| Hai đối tượng khác nhau cùng định danh | `equals` hai chiều đúng, mã băm bằng nhau |
| Ba đối tượng cùng định danh | Tính bắc cầu |
| Khác một thành phần định danh | Không bằng nhau theo yêu cầu nghiệp vụ |
| Null và kiểu không liên quan | Không gây diễn giải sai quan hệ bằng nhau |
| Va chạm băm hợp lệ | Hai khóa khác nhau vẫn giữ hai ánh xạ và tra đúng |
| Ghi bằng một khóa, đọc bằng khóa tương đương mới | Tra đúng ánh xạ |
| Ghi lại bằng khóa tương đương | Thay giá trị; không tăng số khóa logic |
| Sao chép bảo vệ dữ liệu đầu vào | Sửa đầu vào không đổi định danh đã lưu |
| Lớp cha/lớp con nếu có | Đối xứng và bắc cầu vẫn giữ |
| Comparator nếu dùng map có thứ tự | Kết quả so bằng 0 phù hợp với ngữ nghĩa đã chọn |

Kiểm tra tính đúng đắn trước khi đo hiệu năng. Một hàm băm phân bố tốt không cứu được `equals` sai; một `equals` đúng cũng chưa đủ cho map băm nếu mã băm bất nhất.

Nếu cần đo hiệu năng, dùng dữ liệu và phương pháp đo phù hợp. Không lấy vài dòng `println` hoặc một lần đo rất ngắn làm bằng chứng rằng một công thức băm tốt hơn mọi lựa chọn khác.

### Khi get bất ngờ trả null, kiểm tra theo thứ tự nào?

1. Có chắc đang dùng đúng map và đúng kiểu khóa, chưa xóa hoặc thay dữ liệu ở nơi khác không?
2. Hai khóa định xem là một có thực sự `equals` hai chiều không?
3. Chúng có cùng mã băm khi đang được dùng không?
4. Có trường hoặc đối tượng con nào tham gia định danh đã thay đổi không?
5. Có viết nhầm overload `equals(MyKey)` thay vì ghi đè `equals(Object)` không?
6. Map có dùng comparator hoặc đồng nhất tham chiếu thay cho cơ chế mình đang nghĩ không?
7. Nếu map cho phép giá trị `null`, đã phân biệt ánh xạ có giá trị null với khóa không tồn tại bằng `containsKey` khi cần chưa?

Chuỗi kiểm tra này giúp tách lỗi định danh khỏi lỗi thao tác hoặc giả định sai về loại map. Nó không biến việc đọc nhiều phương thức liên tiếp thành một thao tác nguyên tử; nếu dữ liệu được chia sẻ nhiều luồng, còn phải xét thời điểm và cơ chế phối hợp.

## 15. Tự kiểm tra và giải thích

### Câu 1: Hash bằng nhau có phải khóa bằng nhau?

Hai khóa có `hashCode() == 0` nhưng `equals` là `false`. Có vi phạm hợp đồng không? Map băm có phải chỉ giữ một khóa không?

**Đáp án:** Không vi phạm chỉ vì điều đó. Đây là va chạm băm hợp lệ; map có thể giữ cả hai ánh xạ và phải phân biệt theo sự bằng nhau khi các điều kiện hợp đồng khác được đáp ứng.

### Câu 2: Equals bằng nhau nhưng hash khác nhau

Hai đối tượng cùng mã nghiệp vụ, `equals` trả `true`, nhưng mã băm khác nhau. Có thể yêu cầu `HashMap` quét mọi phần tử để vẫn tìm đúng không?

**Đáp án:** Không. Lớp khóa vi phạm yêu cầu bằng nhau thì cùng mã băm. Map có thể tận dụng băm để bỏ qua các ứng viên; phải sửa lớp khóa.

### Câu 3: Chỉ có equals, không hashCode

Lớp tự ghi đè `equals` theo `id`, giữ nguyên `Object.hashCode`. Dùng hai đối tượng riêng cùng `id` trong map băm có bảo đảm đúng ý nghiệp vụ không?

**Đáp án:** Không có cơ sở để bảo đảm hợp đồng băm đã được giữ. Thông thường phải ghi đè cả `hashCode` nhất quán với `equals`; kiểm tra trên hai đối tượng riêng, không chỉ cùng tham chiếu.

### Câu 4: Sửa khóa nhưng dùng đúng tham chiếu cũ

Đối tượng khóa đổi một trường tham gia băm sau khi được lưu. Có chắc `get(key)` tìm được vì `key` vẫn là đúng đối tượng cũ không?

**Đáp án:** Không. Tra cứu vẫn dựa vào cơ chế định vị. Quan trọng hơn, điều kiện ổn định của khóa đã bị vi phạm; hành vi mong muốn không còn được bảo đảm chung.

### Câu 5: Record có list

`record Key(List<String> parts)` có tự bảo vệ khỏi việc nơi gọi sửa danh sách đầu vào không?

**Đáp án:** Không. Record giữ thành phần tham chiếu; nếu cần định danh ổn định theo nội dung, phải kiểm soát hoặc sao chép bảo vệ danh sách và xét cả các phần tử bên trong.

### Câu 6: Record có mảng

Hai `record Key(int[] parts)` được dựng bằng hai mảng khác nhau cùng nội dung. Chúng tự động bằng nhau theo từng số không?

**Đáp án:** Không. Mảng dùng `equals` theo tham chiếu; record không tự chuyển nó thành so sánh nội dung. Nếu muốn ngữ nghĩa nội dung, cần thiết kế sự bằng nhau, mã băm và bảo vệ dữ liệu phù hợp.

### Câu 7: Đổi thứ tự chèn

Hai map tuân thủ hợp đồng chung chứa cùng ánh xạ nhưng được chèn theo thứ tự khác nhau. Điều đó có khiến `Map.equals` bắt buộc trả `false` không?

**Đáp án:** Không. `Map.equals` xét tập ánh xạ, không thêm thứ tự chèn vào định nghĩa bằng nhau.

### Câu 8: Chỉ so khóa khi so entry

Hai entry cùng khóa nhưng giá trị khác nhau. Chúng có bằng nhau chỉ vì là “cùng khóa” không?

**Đáp án:** Không. `Map.Entry.equals` xét cả khóa và giá trị. Đây là một tầng khác với sự bằng nhau của riêng khóa.

### Câu 9: TreeMap có cần hashCode để định vị?

Hai khóa so bằng `0` theo comparator nhưng không `equals`. Thêm một công thức băm khác có làm `TreeMap` giữ chúng thành hai vị trí không?

**Đáp án:** Không. `TreeMap` nhận diện vị trí khóa theo phép so thứ tự. Phải xem lại comparator và yêu cầu nhất quán với `equals`, không sửa sai chỗ bằng mã băm.

### Câu 10: IdentityHashMap và chuỗi

Hai chuỗi cùng nội dung nhưng khác đối tượng được đưa vào `IdentityHashMap`. Vì sao có thể giữ hai ánh xạ?

**Đáp án:** Lớp dùng sự đồng nhất tham chiếu `==`, không gộp hai đối tượng chỉ vì `String.equals` trả `true`. Đây là lựa chọn chuyên biệt có khác biệt được tài liệu hóa với hợp đồng chung.

### Câu 11: Giá trị của map đổi có làm khóa tra cứu đổi không?

`Map<StockKey, List<String>>` có khóa ổn định. Sửa danh sách giá trị có tự đổi `StockKey.hashCode` không?

**Đáp án:** Không nếu `StockKey` được thiết kế độc lập với danh sách ấy. Tuy nhiên, nội dung và mã băm của cả map có thể bị ảnh hưởng vì `Map.hashCode` xét cả entry. Nếu dùng chính map làm khóa ở nơi khác, đó lại là một rủi ro khác.

### Câu 12: Mã băm khác qua lần chạy

Có nên lưu `hashCode` của một khóa tự định nghĩa rồi dùng con số ấy làm định danh bền vững qua lần khởi động sau không?

**Đáp án:** Không nên. Hợp đồng chung không cung cấp bảo đảm đó, và va chạm vẫn có thể xảy ra. Hãy lưu định danh dữ liệu thật theo một định dạng phù hợp.

## 16. Thực hành: khóa tồn kho theo mã hàng và kho

Thiết kế kiểu khóa bất biến gồm `sku` và `warehouse`, rồi viết một chương trình kiểm chứng:

1. Tạo hai đối tượng khác nhau cùng `sku="PEN"`, `warehouse=1`.
2. Xác nhận chúng bằng nhau hai chiều, cùng mã băm, nhưng không cùng tham chiếu.
3. Ghi số lượng `10` bằng khóa đầu, rồi ghi `15` bằng khóa thứ hai.
4. Xác nhận giá trị cũ ở bước ghi lại là `10`, kích thước vẫn bằng `1`.
5. Tra bằng một khóa mới tương đương và nhận `15`.
6. Thêm khóa cùng `sku` nhưng `warehouse=2`, xác nhận kích thước thành `2`.
7. Xác nhận không có cách sửa dữ liệu xác định khóa qua các tham chiếu mà lớp công khai.
8. Nếu dùng một thành phần collection, bổ sung kiểm tra sửa đầu vào không ảnh hưởng khóa đã tạo.

Sau đó tự viết một phản ví dụ chỉ để học: cho `equals` bỏ qua `warehouse` nhưng `hashCode` dùng nó. Trước khi thử map, hãy tìm ngay cặp khóa chứng minh “bằng nhau nhưng khác mã băm”. Việc nhận diện vi phạm ở cấp lớp giúp tránh nhầm rằng lỗi chỉ xuất hiện sau khi chọn một map cụ thể.

Khi hoàn thiện, quay lại phiên bản khóa đúng; không để phản ví dụ sai trở thành mã khóa của ứng dụng. Mọi kỳ vọng về tìm kiếm phải dựa trên hợp đồng đã được giữ, thay vì dựa vào một lần chạy tình cờ cho kết quả thuận lợi.

## Tài liệu đối chiếu

- [Java 17 — Object.equals và Object.hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): quan hệ tương đương và các yêu cầu của mã băm.
- [Java 17 — Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): khóa khả biến, tối ưu so sánh, `Map.equals` và `Map.hashCode`.
- [Java 17 — HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): hiện thực map băm và chính sách khóa/giá trị null.
- [Java 17 — Record](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html): sự bằng nhau theo thành phần và việc sao chép bảo vệ.
- [Java 17 — Map.Entry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html): bằng nhau của cặp và công thức mã băm entry.
- [Java 17 — TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html): so thứ tự và yêu cầu nhất quán với `equals`.
- [Java 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): sự khác nhau giữa `equals` và thứ tự số học.
- [Java 17 — IdentityHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html): đồng nhất tham chiếu và sự khác biệt có chủ ý với hợp đồng chung.

Năm chương trình trong bài dùng cú pháp và API tương thích Java 17 trở lên, đã được biên dịch bằng `javac --release 17 -encoding UTF-8` và chạy với JDK 24.0.2. Các phản ví dụ vi phạm hợp đồng được ghi rõ là thí nghiệm quan sát, không phải hành vi được bảo đảm để ứng dụng dựa vào.


</details>

- [Quay lại đầu trang](#back-to-top)
