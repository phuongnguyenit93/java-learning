<a id="back-to-top"></a>

# TreeMap

## Menu
- [SortedMap, NavigableMap và thứ tự khóa](#tree-map-001)
- [Cây tìm kiếm cân bằng và chi phí thao tác](#tree-map-002)
- [Comparator, compareTo và khóa được xem là trùng](#tree-map-003)
- [firstKey, lastKey, firstEntry và lastEntry](#tree-map-004)
- [lowerEntry, floorEntry, ceilingEntry và higherEntry](#tree-map-005)
- [headMap, tailMap, subMap và lựa chọn biên](#tree-map-006)
- [descendingMap, navigableKeySet và view đảo thứ tự](#tree-map-007)
- [Chính sách null, tính khả biến của khóa và tính nhất quán phép so sánh](#tree-map-008)

## <a id="tree-map-001">SortedMap, NavigableMap và thứ tự khóa</a>

<details>
<summary>Xem chi tiết</summary>

# TreeMap, SortedMap, NavigableMap và thứ tự khóa

Một map thông thường trả lời câu hỏi “khóa này ánh xạ tới giá trị nào?”. Nhiều bài toán còn cần biết khóa đầu tiên, khóa gần một mốc nhất hoặc tất cả khóa trong một khoảng. Ví dụ, bảng cấu hình có hiệu lực từ các mốc thời gian cần tìm bản cấu hình mới nhất không muộn hơn thời điểm đang xét.

`TreeMap` giải quyết nhóm bài toán này bằng cách giữ các khóa theo một quy tắc so sánh. `SortedMap` và `NavigableMap` là hai giao diện mô tả những khả năng liên quan đến thứ tự; chúng không phải hai chế độ khác nhau mà phải bật trên TreeMap.

Bài này dùng Java 17. Mục tiêu là hiểu quan hệ giữa ba tên gọi, đọc đúng ý nghĩa của thứ tự khóa, chọn truy vấn lân cận và khoảng khóa, đồng thời phân biệt góc nhìn dùng chung dữ liệu với bản sao độc lập.

## 1. Từ Map đến NavigableMap

Quan hệ giao diện trong Java 17 có thể đọc theo chuỗi:

~~~text
Map<K, V>
    ↑
SortedMap<K, V>
    ↑
NavigableMap<K, V>
    ↑ được triển khai bởi
TreeMap<K, V>
~~~

Mũi tên biểu diễn việc kiểu bên dưới có các khả năng của kiểu bên trên. `TreeMap` là một lớp cụ thể, còn ba tên đầu là giao diện.

| Kiểu | Điều nó bổ sung hoặc biểu diễn |
|---|---|
| Map | Các ánh xạ khóa–giá trị |
| SortedMap | Thứ tự toàn phần trên khóa, khóa đầu/cuối và các khoảng |
| NavigableMap | Tìm khóa lân cận, điều khiển biên khoảng, chiều đảo và lấy/xóa hai đầu |
| TreeMap | Một cách triển khai NavigableMap dựa trên cây đỏ–đen |

`TreeMap` có bảo đảm chi phí `O(log n)` cho `containsKey`, `get`, `put` và `remove`. Bài này không đi vào thuật toán cân bằng cây; điều cần nối với API là dữ liệu đã có quan hệ thứ tự để phục vụ các truy vấn theo mốc và theo khoảng. [Java 17: TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)

### 1.1. SortedMap chưa phải NavigableMap về mặt kiểu biến

Một đối tượng TreeMap có thể được tham chiếu qua nhiều kiểu:

~~~java
TreeMap<Integer, String> tree = new TreeMap<>();
Map<Integer, String> general = tree;
SortedMap<Integer, String> sorted = tree;
NavigableMap<Integer, String> navigable = tree;
~~~

Đây là các khai báo minh họa, chưa phải chương trình độc lập.

Bốn biến cùng trỏ đến một đối tượng. Duyệt qua `general.entrySet()` vẫn theo thứ tự do TreeMap giữ. Đổi kiểu tham chiếu không biến đối tượng thành HashMap và không xóa thứ tự của nó.

Nhưng các phương thức gọi được ở lúc biên dịch phụ thuộc kiểu khai báo:

- `general` có các phương thức của Map.
- `sorted` có thêm `firstKey`, `lastKey` và các khoảng của SortedMap.
- `navigable` có thêm `floorEntry`, `ceilingKey`, `descendingMap` và các phương thức điều hướng khác.

Nếu một hàm cần khả năng điều hướng, nên biểu diễn yêu cầu bằng tham số `NavigableMap` phù hợp. Không nên nhận Map rồi ép kiểu mù với giả định mọi nơi đều truyền TreeMap.

### 1.2. Vì sao cần hơn một giao diện?

Một hàm chỉ đếm số ánh xạ không cần yêu cầu lớp cây cụ thể. Một hàm cần khóa lân cận lại cần hợp đồng mạnh hơn Map. Các giao diện giúp nói rõ điều hàm cần từ dữ liệu.

Ví dụ, chức năng tìm phiên bản cấu hình tại một thời điểm có thể cần `floorEntry`. Khai báo nhận NavigableMap làm khả năng đó hiện rõ trong chữ ký, thay vì giấu một giả định TreeMap trong phần thân.

## 2. TreeMap sắp xếp khóa theo quy tắc nào?

### 2.1. Thứ tự tự nhiên

Nếu không truyền comparator, TreeMap dùng thứ tự tự nhiên (`natural ordering`) của khóa qua `Comparable.compareTo`. Các khóa phải so sánh được với nhau.

Với `Integer`, thứ tự thông thường là thứ tự số. Chèn `40, 10, 30, 20` vẫn cho thứ tự duyệt `10, 20, 30, 40`.

Với `String`, quy tắc do `String.compareTo` định nghĩa. Không nên mặc nhiên coi đó là quy tắc sắp tên tiếng Việt theo nhu cầu ngôn ngữ của ứng dụng. Quy tắc so sánh phải được chọn theo dữ liệu và nghiệp vụ. [Java 17: Comparable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html)

### 2.2. Comparator riêng

Có thể đưa một đối tượng so sánh khi tạo map:

~~~java
NavigableMap<Integer, String> decreasing =
        new TreeMap<>(Comparator.reverseOrder());
~~~

Khi ấy thứ tự của map là thứ tự đảo. “Khóa nhỏ nhất”, “trước” và “sau” trong các phương thức điều hướng đều phải hiểu theo comparator này, không tự động theo cách đọc số tăng dần.

`comparator()` trả về comparator đang dùng, hoặc null nếu dùng thứ tự tự nhiên. Kết quả null ở đây không có nghĩa map không có thứ tự.

`Comparator` phải tạo quan hệ so sánh nhất quán, chẳng hạn tính bắc cầu. Khi so số nguyên, không nên viết `a - b` để lấy dấu vì tràn số có thể làm sai quan hệ. Dùng `Integer.compare(a, b)` hoặc các hàm tạo comparator thích hợp. [Java 17: Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)

### 2.3. Sắp xếp theo khóa, không theo giá trị

Giả sử map có các cặp:

~~~text
40 -> forty
10 -> ten
30 -> thirty
20 -> twenty
~~~

Duyệt entrySet theo khóa tăng dần cho `10=ten, 20=twenty, 30=thirty, 40=forty`. Duyệt values cho `ten, twenty, thirty, forty` vì các giá trị đi theo ánh xạ của khóa tương ứng. Điều đó không nói các chuỗi giá trị đã được sắp chữ cái.

Thứ tự cũng không phải thứ tự chèn. Nếu cần lần đầu xuất hiện, đó là yêu cầu khác. TreeMap giữ quy tắc so sánh, không lưu lịch sử chèn để dùng làm thứ tự duyệt.

`SortedMap` xác định thứ tự khóa được phản ánh khi duyệt các góc nhìn `keySet`, `entrySet` và `values`. [Java 17: SortedMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SortedMap.html)

## 3. So sánh bằng 0 ảnh hưởng cả định danh khóa

Comparator không chỉ trang trí cách in dữ liệu. TreeMap dùng phép so sánh để xác định các khóa trong cấu trúc của nó.

### 3.1. Hai chuỗi khác nhau có thể chiếm một vị trí

Với comparator không phân biệt hoa thường, `"java"` và `"JAVA"` được so sánh bằng 0. Thêm lần lượt hai khóa ấy có thể tạo một ánh xạ với giá trị sau thay giá trị trước, thay vì hai ánh xạ.

Trong chương trình phía dưới:

~~~java
TreeMap<String, Integer> folded =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
folded.put("java", 1);
folded.put("JAVA", 2);
~~~

Kết quả có một ánh xạ và tra cứu `"JaVa"` nhận 2. Ví dụ chỉ khẳng định số ánh xạ và giá trị tra được; không dựa vào việc đại diện khóa nào được giữ để giải thích định danh nghiệp vụ.

Điều này thường bị hiểu nhầm thành “mất dữ liệu do cây”. Thực ra, quy tắc so sánh đã nói hai khóa thuộc cùng vị trí so sánh.

### 3.2. Nhất quán với equals

Để tuân thủ hợp đồng Map dựa trên equals, thứ tự của sorted map cần nhất quán với equals: so sánh bằng 0 tương ứng với bằng nhau theo equals.

Comparator không phân biệt hoa thường với String không có tính nhất quán đó, vì `"java".equals("JAVA")` là false. Ví dụ cố ý bộc lộ sự khác biệt, không phải khuyến nghị dùng comparator ấy cho mọi danh mục định danh. [Java 17: quan hệ giữa so sánh và equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)

Nếu nghiệp vụ thực sự không phân biệt hoa thường, có thể thiết kế biểu diễn khóa được chuẩn hóa phù hợp. Nếu cần giữ hai bản ghi có cùng điểm hoặc cùng tên hiển thị, phải có tiêu chí phân biệt thêm thay vì vô tình gộp chúng.

Ví dụ minh họa một phần với khóa bản ghi:

~~~java
record RankKey(int score, long id) {}

Comparator<RankKey> byScoreThenId =
        Comparator.comparingInt(RankKey::score)
                  .thenComparingLong(RankKey::id);

NavigableMap<RankKey, String> ranking =
        new TreeMap<>(byScoreThenId);
~~~

Hai khóa khác id nhưng cùng score vẫn so sánh khác 0 nhờ tiêu chí phụ. Với bản ghi này, equals cũng xét hai thành phần đó.

### 3.3. Giữ ổn định các thành phần được so sánh

Nếu khóa đang nằm trong cây mà trường dùng để so sánh bị thay đổi, quan hệ thứ tự của đối tượng đó với các khóa khác có thể không còn phù hợp vị trí đã được tổ chức. Đừng giả định map sẽ tự phát hiện và đặt lại khóa.

Khóa bất biến giúp việc tổ chức dữ liệu rõ ràng hơn. Khi cần thay thông tin ảnh hưởng thứ tự, hãy thiết kế thao tác loại khóa cũ và thêm khóa mới theo trạng thái được kiểm soát, thay vì sửa trực tiếp đối tượng đang đóng vai trò khóa.

## 4. SortedMap: hai đầu và ba loại khoảng

### 4.1. Khóa đầu và cuối

`firstKey()` trả về khóa đầu theo thứ tự map, `lastKey()` trả về khóa cuối. Với map tự nhiên có `10, 20, 30, 40`, kết quả là 10 và 40.

Trên map giảm dần của cùng các số, đầu là 40 và cuối là 10. “Đầu” là đầu thứ tự của map, không nhất thiết là số nhỏ nhất theo cách đọc số tăng dần.

Nếu map trống, hai phương thức này ném `NoSuchElementException`. Khi muốn kết quả có thể vắng mà không dùng ngoại lệ, NavigableMap có các phương thức trả entry ở hai đầu.

### 4.2. Khoảng mặc định của SortedMap

Theo thứ tự hiện có, các phương thức là:

| Phương thức | Điều kiện khóa k |
|---|---|
| subMap(a, b) | a ≤ k < b |
| headMap(b) | k < b |
| tailMap(a) | k ≥ a |

Dấu ≤ và < trong bảng là quan hệ theo comparator hoặc thứ tự tự nhiên đang dùng.

Với khóa `10, 20, 30, 40` theo thứ tự số tăng:

~~~text
subMap(20, 40) -> 20, 30
headMap(30)    -> 10, 20
tailMap(30)    -> 30, 40
~~~

Khoảng subMap mặc định bao gồm đầu dưới và loại đầu trên. Cách viết này thường được gọi là khoảng nửa mở `[a, b)`.

Biên không bắt buộc là khóa đang tồn tại. Với cùng dữ liệu, `subMap(15, 35)` chọn 20 và 30. Biên dùng để so sánh phạm vi, không phải lời yêu cầu phải tìm thấy hai ánh xạ biên.

### 4.3. Chọn khoảng theo nghiệp vụ

Khoảng nửa mở giúp ghép các vùng liền nhau mà không lặp biên: `[a, b)` rồi `[b, c)`. Ví dụ, báo cáo một ngày có thể dùng thời điểm bắt đầu ngày hiện tại và đầu ngày tiếp theo thay cho việc tự tính “thời điểm cuối ngày” với một độ chính xác giả định.

Đây là lý do thiết kế phạm vi trước khi gọi hàm. Khi nghiệp vụ yêu cầu bao gồm cả hai mốc, cần dạng có tham số boolean của NavigableMap, không được tự bỏ sót đầu trên vì nhớ nhầm mặc định.

## 5. NavigableMap: tìm khóa lân cận

### 5.1. Bốn quan hệ cần phân biệt

Với mốc x và thứ tự map:

| Nhóm | Khóa được chọn |
|---|---|
| lowerKey(x), lowerEntry(x) | Khóa lớn nhất nhưng nhỏ hơn x |
| floorKey(x), floorEntry(x) | Khóa lớn nhất nhưng không lớn hơn x |
| ceilingKey(x), ceilingEntry(x) | Khóa nhỏ nhất nhưng không nhỏ hơn x |
| higherKey(x), higherEntry(x) | Khóa nhỏ nhất nhưng lớn hơn x |

Có thể nhớ bằng việc tách hai câu hỏi: tìm phía trước hay phía sau, và có chấp nhận bằng đúng mốc hay không.

Với khóa `10, 20, 30, 40` theo thứ tự số:

| Mốc | lower | floor | ceiling | higher |
|---|---:|---:|---:|---:|
| 20 | 10 | 20 | 20 | 30 |
| 25 | 20 | 20 | 30 | 30 |
| 5 | Không có | Không có | 10 | 10 |
| 45 | 40 | 40 | Không có | Không có |

Các phương thức trả null khi không có kết quả phù hợp. Bản `Key` trả khóa; bản `Entry` trả cả khóa và giá trị. [Java 17: NavigableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableMap.html)

### 5.2. Ví dụ cấu hình có hiệu lực từ một mốc

Giả sử khóa là thời điểm bắt đầu hiệu lực:

~~~text
10 -> cấu hình A
20 -> cấu hình B
30 -> cấu hình C
~~~

Muốn biết cấu hình tại thời điểm 25, dùng floorEntry(25), nhận ánh xạ ở 20. Dùng lowerEntry(25) cũng cho 20 trong ví dụ này, nhưng tại đúng thời điểm 20, lowerEntry sẽ trả bản ở 10, khác yêu cầu.

Đó là lý do phải kiểm thử cả trường hợp bằng biên, không chỉ chọn một mốc nằm giữa hai khóa. Khi dữ liệu có thể vắng ở đầu trục thời gian, cũng cần định nghĩa trường hợp floorEntry trả null: không có cấu hình hợp lệ hay phải dùng cấu hình mặc định?

### 5.3. Lân cận theo thứ tự không phải gần nhất theo khoảng cách

Lower, floor, ceiling và higher nói về quan hệ thứ tự. Chúng không tự trả “giá trị có khoảng cách số học nhỏ nhất”.

Ví dụ, với các khóa 10 và 100, tìm khóa gần số 90 nhất cần cân nhắc khoảng cách sau khi lấy ứng viên thích hợp. Chọn floorKey(90) một mình chỉ cho khóa phía dưới là 10, không phải lời giải “gần nhất” theo khoảng cách.

Với thời điểm, địa chỉ hoặc phiên bản, quan hệ nghiệp vụ có thể không phải khoảng cách số. Hãy gọi đúng tên điều ứng dụng cần.

### 5.4. Khi giá trị hoặc khóa có thể null

Một entry kết quả khác null có thể chứa giá trị null. Vì vậy, đừng kiểm tra `entry.getValue() == null` để kết luận không tìm thấy.

Nếu comparator cho phép khóa null, phương thức trả khóa có thể gặp sự nhập nhằng giữa “khóa kết quả là null” và “không có kết quả”. Dạng trả entry giúp phân biệt qua chính đối tượng entry.

Phải xét quy tắc null của cách triển khai; không suy từ một giao diện sang việc mọi map đều cho phép null.

## 6. Hai đầu: xem và lấy ra là hai thao tác khác nhau

NavigableMap có các phương thức:

| Phương thức | Tác động |
|---|---|
| firstEntry() | Xem ánh xạ đầu, không xóa |
| lastEntry() | Xem ánh xạ cuối, không xóa |
| pollFirstEntry() | Lấy và xóa ánh xạ đầu |
| pollLastEntry() | Lấy và xóa ánh xạ cuối |

Map trống cho kết quả null ở các phương thức trên. Tên `poll` là lời nhắc rằng thao tác làm thay đổi map khi có kết quả.

Ví dụ, dùng pollFirstEntry để “xem khóa nhỏ nhất” trong phần ghi log sẽ vô tình lấy nó khỏi dữ liệu. Nếu chỉ muốn quan sát, chọn firstEntry hoặc firstKey theo cách xử lý map trống mong muốn.

### 6.1. Entry từ truy vấn điều hướng của TreeMap

Các entry do những phương thức điều hướng của TreeMap như firstEntry, floorEntry hoặc pollFirstEntry trả về là ảnh chụp ánh xạ tại thời điểm tạo. Chúng không hỗ trợ setValue để ghi ngược lại map. Muốn sửa ánh xạ đang tồn tại, thực hiện thao tác map thích hợp.

Không mở rộng câu này thành “mọi Map.Entry trong Java đều bất biến”. Entry lấy khi duyệt entrySet có hợp đồng riêng; một cặp trả về từ truy vấn điều hướng và một entry của góc nhìn duyệt không nên bị coi là cùng một loại quan hệ với map.

Ảnh chụp cặp cũng không có nghĩa sao chép sâu đối tượng giá trị. Ví dụ dùng String để việc so sánh giá trị cũ và mới rõ ràng.

## 7. Biên bao gồm và loại trừ trong NavigableMap

Các dạng bổ sung cho phép chọn từng biên:

~~~java
NavigableMap<Integer, String> middle =
        map.subMap(20, true, 40, false);

NavigableMap<Integer, String> upTo30 =
        map.headMap(30, true);

NavigableMap<Integer, String> after30 =
        map.tailMap(30, false);
~~~

Đây là đoạn minh họa với biến map đã có, không phải chương trình độc lập.

Các kết quả trên dữ liệu 10, 20, 30, 40:

~~~text
subMap(20, true, 40, false) -> 20, 30
headMap(30, true)          -> 10, 20, 30
tailMap(30, false)         -> 40
~~~

`true` nghĩa là bao gồm biên đó nếu có khóa so sánh bằng biên; `false` nghĩa là loại trừ.

Nếu hai biên bằng nhau, các cờ vẫn quan trọng. Khoảng bao gồm cả hai đầu có thể chứa khóa bằng mốc nếu khóa đó tồn tại; khoảng loại ít nhất một đầu không chứa khóa ấy. Trường hợp mặc định `subMap(a, a)` là khoảng rỗng.

Nếu biên bắt đầu đứng sau biên kết thúc theo thứ tự của map, yêu cầu khoảng không hợp lệ. Đừng hiểu “bắt đầu nhỏ hơn kết thúc” luôn là phép so số tăng dần; phần thứ tự đảo sẽ minh họa điều này.

## 8. Các khoảng là góc nhìn dùng chung dữ liệu

### 8.1. Không tự tạo bản sao

`subMap`, `headMap` và `tailMap` trên TreeMap trả góc nhìn (`view`) vào dữ liệu. Thay đổi hợp lệ qua view được phản ánh vào map gốc; thay đổi map gốc trong phạm vi cũng được nhìn thấy qua view.

Ví dụ:

~~~text
map gốc: 10, 20, 30, 40
view [20,40): 20, 30

thêm 35 qua view:
map gốc: 10, 20, 30, 35, 40
view:    20, 30, 35

xóa 20 qua view:
map gốc: 10, 30, 35, 40
view:    30, 35
~~~

Có thể đọc view như “cửa sổ giới hạn khóa” vào cùng dữ liệu, không phải bảng dữ liệu mới đã được tách.

### 8.2. Ghi ngoài phạm vi bị từ chối

Với view `[20,40)`, put khóa 40 hoặc 45 qua view ném `IllegalArgumentException`. Ngay cả khi 40 đã tồn tại trong map gốc, nó vẫn ngoài phạm vi view vì đầu trên bị loại.

Giới hạn view không cấm map gốc có các khóa ngoài khoảng; nó giới hạn những khóa view đại diện và có thể chèn qua view. Một phép get khóa ngoài phạm vi không tự biến thành phép ghi và không nên được diễn giải như yêu cầu mở rộng view.

Các view lồng nhau tiếp tục bị ràng buộc bởi phạm vi đang có. Không dùng một view để tự mở rộng ra ngoài cửa sổ của view cha. [Java 17: hợp đồng các vùng khóa của TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)

### 8.3. Clear view khác clear toàn bộ map

`view.clear()` loại các ánh xạ nằm trong vùng của view khỏi dữ liệu gốc. Nó không chỉ làm rỗng một bản hiển thị riêng, và cũng không nhất thiết xóa những khóa ngoài phạm vi.

Điều này có ích cho việc xóa một khoảng, nhưng cần xem đó là thao tác sửa dữ liệu gốc. Đặt tên biến phản ánh phạm vi giúp giảm nhầm lẫn, chẳng hạn `expiredByTime` thay vì một tên chung chung như `temp`.

### 8.4. Khi cần bản độc lập

Có thể tạo TreeMap mới từ SortedMap hoặc NavigableMap đang có:

~~~java
NavigableMap<Integer, String> window =
        source.subMap(20, true, 40, false);

TreeMap<Integer, String> copy = new TreeMap<>(window);
~~~

Bản copy có cấu trúc độc lập; sửa view về sau không tự thêm hoặc xóa ánh xạ trong copy. Các tham chiếu khóa và giá trị vẫn được dùng lại, nên không tự có sao chép sâu.

Nếu nguồn đang bị sửa cạnh tranh, bước sao chép vẫn cần chính sách đồng thời phù hợp. Tạo đối tượng mới không tự hợp thức hóa việc đọc nguồn trong trạng thái không được bảo vệ.

## 9. Thứ tự đảo thay đổi cả nghĩa của điều hướng

### 9.1. DescendingMap là view

`descendingMap()` trả góc nhìn với thứ tự đảo so với map hiện tại. Nó dùng cùng dữ liệu, nên xóa một khóa qua map đảo cũng xóa ánh xạ trong map gốc.

Với gốc có thứ tự 10, 20, 30, 40, map đảo duyệt 40, 30, 20, 10.

Các phương thức vẫn làm việc theo thứ tự của chính view:

~~~text
trên thứ tự tăng:
lowerKey(20)  -> 10
higherKey(20) -> 30

trên thứ tự đảo:
lowerKey(20)  -> 30
higherKey(20) -> 10
~~~

Khóa 30 đứng trước 20 trong thứ tự đảo, còn 10 đứng sau. Đây không phải lỗi của lower hoặc higher; quan hệ đang dùng đã đổi.

### 9.2. Biên khoảng cũng theo thứ tự đảo

Trên view đảo, `subMap(40, true, 20, false)` chọn 40 và 30. Cặp biên trông “từ số lớn xuống số nhỏ” nhưng đúng chiều của comparator.

Ngược lại, lấy thói quen truyền 20 rồi 40 từ map tăng áp vào view đảo có thể tạo khoảng không hợp lệ. Hãy đọc dãy thứ tự thực tế trước khi diễn giải fromKey và toKey.

`descendingKeySet` và `navigableKeySet` cung cấp góc nhìn khóa tương ứng. Chúng không phải nơi có thể tự thêm một khóa mới mà không có giá trị map; việc loại khóa được hỗ trợ sẽ tác động đến ánh xạ gốc theo hợp đồng góc nhìn.

## 10. Chọn constructor cũng là chọn thứ tự

Hai constructor sao chép có điểm khác biệt đáng học:

| Đối số được chọn theo kiểu ở lúc biên dịch | TreeMap mới dùng |
|---|---|
| Map | Thứ tự tự nhiên của khóa |
| SortedMap | Thứ tự của SortedMap nguồn |

Ví dụ, một TreeMap nguồn đang giảm dần. Nếu truyền nguồn qua biến kiểu SortedMap, constructor giữ quy tắc giảm dần. Nếu truyền qua biến kiểu Map, constructor nhận Map dùng thứ tự tự nhiên.

Không có việc “runtime luôn nhìn ra đối tượng nguồn là TreeMap rồi chọn lại dạng nạp chồng (overload)”. Việc chọn dạng nạp chồng dựa vào kiểu biểu thức ở lúc biên dịch.

Khi muốn kiểm soát rõ ràng hơn, có thể tạo map với comparator mong muốn rồi putAll các ánh xạ. Thứ tự sẽ do comparator của đích quyết định; putAll không tự thay nó bằng comparator nguồn.

Chi tiết này giải thích vì sao hai bản sao cùng ánh xạ có thể duyệt khác thứ tự. Nó cũng nhắc rằng kiểu giao diện không chỉ ảnh hưởng danh sách phương thức gọi được mà còn có thể ảnh hưởng việc chọn constructor. [Java 17: các constructor của TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)

## 11. Null, kiểu khóa và những giới hạn cần biết

TreeMap dùng thứ tự tự nhiên không chấp nhận khóa null. Comparator riêng có thể cho phép null nếu chính comparator định nghĩa cách so sánh nó, chẳng hạn `Comparator.nullsFirst(...)`.

Chương trình dùng comparator cho phép null và in thứ tự `[null, A, B]`. Điều đó không có nghĩa mọi TreeMap đều chấp nhận null.

Giá trị null là câu hỏi khác với khóa null. Một ánh xạ có giá trị null vẫn có thể tồn tại; cần dùng containsKey hoặc entry phù hợp khi phải phân biệt với vắng ánh xạ.

Các khóa phải so sánh được với nhau bằng quy tắc của map. Generic giúp kiểm tra kiểu ở lúc biên dịch nhưng không tự bảo đảm mọi quy tắc so sánh đều đúng. Không dùng raw type để trộn tùy ý những loại khóa không thể so sánh.

TreeMap cũng không tự đồng bộ hóa. Iterator có cơ chế phát hiện sửa đổi theo nỗ lực tốt nhất; cơ chế đó không thay thế chính sách truy cập đồng thời. Nội dung về thứ tự ở đây giả sử các thao tác được tổ chức trong trạng thái hợp lệ của map.

## 12. Chương trình Java đầy đủ

Lưu mã sau vào `SortedNavigationDemo.java`. Mỗi nhóm tình huống dùng dữ liệu phù hợp để tránh một phép poll hoặc xóa ở phần trước làm sai cách hiểu phần sau.

Chương trình kiểm chứng thứ tự khóa, khác biệt giữa lower và floor, view so với copy, entry điều hướng, thứ tự đảo, comparator so sánh bằng 0 và hai overload constructor.

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;

public class SortedNavigationDemo {
    private static TreeMap<Integer, String> sample() {
        TreeMap<Integer, String> map = new TreeMap<>();
        map.put(40, "forty");
        map.put(10, "ten");
        map.put(30, "thirty");
        map.put(20, "twenty");
        return map;
    }

    private static <K, V> List<K> keys(Map<K, V> map) {
        return new ArrayList<>(map.keySet());
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Unexpected result");
        }
    }

    public static void main(String[] args) {
        TreeMap<Integer, String> tree = sample();
        Map<Integer, String> asMap = tree;
        SortedMap<Integer, String> sorted = tree;
        NavigableMap<Integer, String> navigable = tree;

        require(keys(asMap).equals(List.of(10, 20, 30, 40)));
        System.out.println("order/keys=" + keys(asMap));
        System.out.println("order/values=" + new ArrayList<>(tree.values()));
        System.out.println("sorted/first=" + sorted.firstKey()
                + ", last=" + sorted.lastKey());
        System.out.println("sorted/[20,40)=" + sorted.subMap(20, 40));
        System.out.println("near/25: lower=" + navigable.lowerKey(25)
                + ", floor=" + navigable.floorKey(25)
                + ", ceiling=" + navigable.ceilingKey(25)
                + ", higher=" + navigable.higherKey(25));
        System.out.println("near/20: lower=" + navigable.lowerKey(20)
                + ", floor=" + navigable.floorKey(20)
                + ", ceiling=" + navigable.ceilingKey(20)
                + ", higher=" + navigable.higherKey(20));

        NavigableMap<Integer, String> range =
                tree.subMap(20, true, 40, false);
        TreeMap<Integer, String> independent = new TreeMap<>(range);
        range.put(35, "thirty-five");
        range.remove(20);
        require(keys(tree).equals(List.of(10, 30, 35, 40)));
        require(keys(independent).equals(List.of(20, 30)));
        boolean outsideRejected = false;
        try {
            range.put(40, "not-allowed");
        } catch (IllegalArgumentException exception) {
            outsideRejected = true;
        }
        require(outsideRejected);
        System.out.println("view/range=" + range);
        System.out.println("view/parent=" + tree);
        System.out.println("view/copy=" + independent);
        System.out.println("view/outsideRejected=" + outsideRejected);

        TreeMap<Integer, String> snapshotMap = sample();
        Map.Entry<Integer, String> first = snapshotMap.firstEntry();
        snapshotMap.put(10, "TEN");
        require(first.getValue().equals("ten"));
        boolean snapshotImmutable = false;
        try {
            first.setValue("changed");
        } catch (UnsupportedOperationException exception) {
            snapshotImmutable = true;
        }
        require(snapshotImmutable);
        System.out.println("entry/snapshot=" + first
                + ", current=" + snapshotMap.get(10)
                + ", setValueRejected=" + snapshotImmutable);

        TreeMap<Integer, String> drained = sample();
        Map.Entry<Integer, String> smallest = drained.pollFirstEntry();
        Map.Entry<Integer, String> largest = drained.pollLastEntry();
        require(keys(drained).equals(List.of(20, 30)));
        System.out.println("poll/first=" + smallest + ", last=" + largest);
        System.out.println("poll/remaining=" + drained);

        TreeMap<Integer, String> empty = new TreeMap<>();
        boolean firstKeyThrows = false;
        try {
            empty.firstKey();
        } catch (NoSuchElementException exception) {
            firstKeyThrows = true;
        }
        require(firstKeyThrows && empty.firstEntry() == null);
        System.out.println("empty/firstEntry=" + empty.firstEntry()
                + ", firstKeyThrows=" + firstKeyThrows);

        TreeMap<Integer, String> ascending = sample();
        NavigableMap<Integer, String> descending = ascending.descendingMap();
        require(keys(descending).equals(List.of(40, 30, 20, 10)));
        System.out.println("descending/keys=" + keys(descending));
        System.out.println("descending/at20: lower=" + descending.lowerKey(20)
                + ", higher=" + descending.higherKey(20));
        System.out.println("descending/[40,20)="
                + descending.subMap(40, true, 20, false));
        descending.remove(20);
        require(!ascending.containsKey(20));
        System.out.println("descending/parentAfterRemove=" + keys(ascending));

        TreeMap<String, Integer> folded =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        folded.put("java", 1);
        folded.put("JAVA", 2);
        require(folded.size() == 1 && folded.get("JaVa") == 2);
        System.out.println("compareZero/size=" + folded.size()
                + ", value=" + folded.get("JaVa"));

        TreeMap<Integer, String> reverseSource =
                new TreeMap<>(Comparator.reverseOrder());
        reverseSource.putAll(sample());
        SortedMap<Integer, String> sortedSource = reverseSource;
        Map<Integer, String> mapSource = reverseSource;
        TreeMap<Integer, String> copySorted = new TreeMap<>(sortedSource);
        TreeMap<Integer, String> copyMap = new TreeMap<>(mapSource);
        require(keys(copySorted).equals(List.of(40, 30, 20, 10)));
        require(keys(copyMap).equals(List.of(10, 20, 30, 40)));
        System.out.println("constructor/SortedMap=" + keys(copySorted));
        System.out.println("constructor/Map=" + keys(copyMap));

        NavigableMap<String, Integer> nullable =
                new TreeMap<>(Comparator.nullsFirst(
                        Comparator.<String>naturalOrder()));
        nullable.put("B", 2);
        nullable.put(null, 0);
        nullable.put("A", 1);
        require(keys(nullable).toString().equals("[null, A, B]"));
        System.out.println("nullKeys/order=" + keys(nullable));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 SortedNavigationDemo.java
java SortedNavigationDemo
~~~

Stdout của lần chạy kiểm chứng:

~~~text
order/keys=[10, 20, 30, 40]
order/values=[ten, twenty, thirty, forty]
sorted/first=10, last=40
sorted/[20,40)={20=twenty, 30=thirty}
near/25: lower=20, floor=20, ceiling=30, higher=30
near/20: lower=10, floor=20, ceiling=20, higher=30
view/range={30=thirty, 35=thirty-five}
view/parent={10=ten, 30=thirty, 35=thirty-five, 40=forty}
view/copy={20=twenty, 30=thirty}
view/outsideRejected=true
entry/snapshot=10=ten, current=TEN, setValueRejected=true
poll/first=10=ten, last=40=forty
poll/remaining={20=twenty, 30=thirty}
empty/firstEntry=null, firstKeyThrows=true
descending/keys=[40, 30, 20, 10]
descending/at20: lower=30, higher=10
descending/[40,20)={40=forty, 30=thirty}
descending/parentAfterRemove=[10, 30, 40]
compareZero/size=1, value=2
constructor/SortedMap=[40, 30, 20, 10]
constructor/Map=[10, 20, 30, 40]
nullKeys/order=[null, A, B]
~~~

Mã đã được biên dịch với `--release 17` và chạy thành công. Tùy chọn release xác định mức API và mã đích tương thích; không khẳng định JVM thực thi là đúng bản JDK 17.

### 12.1. Các dòng order và sorted

Dữ liệu được chèn theo 40, 10, 30, 20 nhưng khóa được in theo 10, 20, 30, 40. Các giá trị đi theo các ánh xạ ấy, không theo một phép sắp giá trị riêng.

Ba biến giao diện cùng tham chiếu tree. Kết quả cho thấy việc nhìn đối tượng qua Map không làm mất thứ tự duyệt của TreeMap, còn kiểu SortedMap cho phép gọi firstKey và subMap.

### 12.2. Hai mốc near được chọn có chủ đích

Mốc 25 nằm giữa hai khóa nên lower và floor cùng cho 20, ceiling và higher cùng cho 30. Nếu chỉ thử mốc này, dễ tưởng các cặp phương thức là đồng nghĩa.

Mốc 20 đúng bằng khóa có sẵn làm khác biệt lộ rõ: lower là 10, floor là 20, ceiling là 20 và higher là 30. Một bài kiểm tra biên phải có trường hợp bằng đúng mốc.

### 12.3. View thay đổi, copy giữ dữ liệu đã sao chép

Copy được tạo trước khi view thêm 35 và xóa 20. Vì vậy copy vẫn chứa 20 và 30, trong khi map gốc phản ánh hai thao tác của view.

Lần put khóa 40 bị từ chối vì 40 là đầu trên bị loại. Việc khóa đó tồn tại trong map gốc không cho view quyền sửa nó.

### 12.4. Entry chụp cặp và poll xóa dữ liệu

FirstEntry giữ cặp 10=ten sau khi map đã được cập nhật thành 10=TEN. Lần setValue trên entry đó bị từ chối. Ví dụ dùng String để tránh nhập nhằng với việc sửa bên trong một đối tượng giá trị có thể thay đổi.

PollFirstEntry và pollLastEntry trả 10=ten và 40=forty, đồng thời map còn 20 và 30. Trên map trống, firstEntry trả null, trong khi firstKey ném NoSuchElementException.

### 12.5. Đảo thứ tự và chọn overload

Map đảo duyệt 40, 30, 20, 10; lower và higher ở mốc 20 được hiểu theo chiều này. Xóa 20 qua view đảo làm map gốc không còn 20.

Hai dòng constructor dùng cùng dữ liệu nguồn giảm dần nhưng khác kiểu biểu thức đối số. Một bản giữ giảm dần; bản qua Map dùng thứ tự tự nhiên. Đây là kết quả lựa chọn overload, không phải dữ liệu bị xáo ngẫu nhiên.

## 13. Lỗi thường gặp và cách nhận diện

| Nhận định dễ sai | Cách kiểm tra đúng |
|---|---|
| TreeMap sắp xếp theo giá trị | Xác định comparator áp lên khóa nào |
| TreeMap giữ thứ tự chèn | Thử chèn ngược thứ tự rồi đọc keySet |
| comparator() bằng null nghĩa là không sắp | Null biểu diễn thứ tự tự nhiên |
| lower và floor giống nhau | Thử mốc bằng đúng một khóa |
| ceiling là phần tử gần nhất theo khoảng cách | Nó là ứng viên phía không nhỏ hơn theo thứ tự |
| subMap mặc định bao gồm cả hai biên | Dạng hai đối số là đầu dưới gồm, đầu trên loại |
| subMap là bản sao để sửa thử | Nó là view; sửa hợp lệ đi vào map gốc |
| Có thể put khóa ngoài vùng vì gốc có khóa đó | Phạm vi view vẫn giới hạn quyền chèn qua view |
| descending chỉ đổi cách in | Nó đảo cả quan hệ điều hướng và biên khoảng |
| firstEntry và pollFirstEntry đều chỉ đọc | Poll loại ánh xạ khỏi map |
| Mọi entry đều setValue được | Entry từ truy vấn điều hướng có hợp đồng riêng |
| compare bằng 0 chỉ ảnh hưởng hiển thị | Nó ảnh hưởng việc TreeMap coi các khóa cùng vị trí |
| Bản copy luôn giữ comparator nguồn | Phải xét constructor được chọn theo kiểu đối số |

## 14. Thực hành và lời giải

### Bài 1. Phân biệt kiểu giao diện và đối tượng

Biến Map tham chiếu một TreeMap có khóa 30, 10, 20. Duyệt keySet có còn theo thứ tự không? Vì sao không gọi trực tiếp floorKey qua biến Map?

**Lời giải:** Đối tượng vẫn là TreeMap nên giữ thứ tự 10, 20, 30 theo quy tắc đang dùng. Map không khai báo floorKey nên kiểu biến không cho phép gọi phương thức ấy. Nếu hàm cần điều hướng, hãy biểu diễn yêu cầu bằng NavigableMap phù hợp.

### Bài 2. Kiểm thử đúng biên

Với khóa 10, 20, 30, hãy tính lowerKey(20), floorKey(20), ceilingKey(20), higherKey(20). Tại sao chỉ thử mốc 25 chưa đủ?

**Lời giải:** Kết quả là 10, 20, 20, 30. Mốc 25 cho lower bằng floor và ceiling bằng higher, nên không bộc lộ sự khác biệt về việc chấp nhận bằng mốc.

### Bài 3. Chọn cấu hình có hiệu lực

Các mốc bắt đầu là 100, 200, 300. Tại thời điểm 200 cần cấu hình ở mốc nào và dùng phương thức nào? Nếu truy vấn 50 thì sao?

**Lời giải:** Dùng floorEntry(200), nhận ánh xạ ở 200. LowerEntry(200) trả mốc 100 nên sai yêu cầu. Tại 50 không có mốc không muộn hơn, kết quả là null; ứng dụng phải định nghĩa chính sách không có cấu hình hoặc dùng mặc định.

### Bài 4. Khoảng và dữ liệu gốc

Map có 10, 20, 30, 40. Tạo subMap(20, 40), rồi gọi clear trên view. Những khóa nào còn trong gốc? Put(40, value) qua view có được không?

**Lời giải:** View đại diện 20 và 30; clear loại hai ánh xạ đó khỏi gốc, còn 10 và 40. Khóa 40 ngoài view do đầu trên bị loại, nên put qua view bị từ chối bằng IllegalArgumentException.

### Bài 5. Thứ tự đảo

Map gốc tăng dần có 10, 20, 30, 40. Trên descendingMap, lowerKey(20) và higherKey(20) là gì? SubMap(40, true, 20, false) chứa gì?

**Lời giải:** Lower là 30 và higher là 10 theo thứ tự đảo. Khoảng bắt đầu ở 40 bao gồm, kết thúc ở 20 loại, nên chứa 40 và 30.

### Bài 6. Comparator vô tình gộp khóa

Khóa là cặp score và id, nhưng comparator chỉ xét score. Hai bản ghi cùng score khác id có chắc được giữ thành hai ánh xạ không? Sửa như thế nào nếu cần giữ cả hai?

**Lời giải:** Không. Comparator cho kết quả 0 khi score bằng nhau nên TreeMap coi chúng cùng vị trí khóa. Có thể thêm id làm tiêu chí phụ, đồng thời thiết kế quan hệ equals tương thích và giữ khóa ổn định.

### Bài 7. Xem và lấy ra

Tác vụ ghi log cần in ánh xạ đầu mà không sửa map, map có thể trống. Chọn firstEntry hay pollFirstEntry? Có thể setValue trên kết quả điều hướng để cập nhật gốc không?

**Lời giải:** Chọn firstEntry và xử lý null nếu trống. PollFirstEntry sẽ loại ánh xạ nếu có. Entry từ truy vấn điều hướng TreeMap không hỗ trợ setValue ghi ngược; muốn sửa phải dùng thao tác map phù hợp.

### Bài 8. Sao chép và comparator

Nguồn là TreeMap giảm dần. Một biến SortedMap và một biến Map cùng trỏ nguồn. Hai lần new TreeMap với hai biến ấy có luôn cùng thứ tự không?

**Lời giải:** Không. Constructor nhận SortedMap giữ thứ tự nguồn; constructor nhận Map dùng thứ tự tự nhiên. Overload được chọn theo kiểu biểu thức ở lúc biên dịch. Muốn rõ ý định có thể tạo đích với comparator cụ thể rồi sao chép ánh xạ.

## 15. Cách đọc một truy vấn trước khi chạy

Trước khi viết floor, subMap hoặc descendingMap, hãy trả lời bốn câu: thứ tự nào đang dùng, có nhận bằng mốc hay không, kết quả là view hay cặp/bản sao, và thao tác có sửa dữ liệu không.

Khi bốn câu này rõ ràng, chuỗi Map → SortedMap → NavigableMap trở thành các mức khả năng dễ dùng: ánh xạ, thứ tự, rồi điều hướng theo thứ tự. TreeMap cung cấp một cách triển khai để nối các khả năng ấy với bài toán thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tree-map-002">Cây tìm kiếm cân bằng và chi phí thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# Cây tìm kiếm cân bằng và chi phí thao tác

## 1. TreeMap giải quyết bài toán gì?

Một chương trình giữ các mốc thời gian cùng dữ liệu của từng mốc. Nó cần tìm đúng một mốc, nhưng cũng cần biết mốc gần nhất phía trước, mốc tiếp theo và những mốc nằm trong một khoảng.

Nếu chỉ cần ánh xạ theo khóa, bảng băm là một lựa chọn quen thuộc. Khi cần **thứ tự khóa và khả năng điều hướng theo thứ tự ấy**, một cấu trúc tìm kiếm có thứ tự trở nên hữu ích.

TreeMap tổ chức ánh xạ bằng một cây tìm kiếm cân bằng. Trong Java, lớp này dựa trên cây đỏ–đen và thực hiện NavigableMap.

Hai ý tưởng cần gắn với nhau:

- **Tìm kiếm nhị phân theo thứ tự khóa** giúp loại bỏ một phần ứng viên sau mỗi lần so sánh.
- **Giữ chiều cao cây có giới hạn** ngăn đường tìm kiếm kéo dài thành một chuỗi tuyến tính.

Tài liệu [TreeMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html) bảo đảm chi phí log(n) cho containsKey, get, put và remove. Để dùng phát biểu ấy đúng, cần hiểu n là số ánh xạ, phép so sánh có chi phí riêng và không phải mọi phương thức của lớp đều có cùng độ phức tạp.

## 2. Cây tìm kiếm nhị phân: mỗi nút giúp chọn một hướng

Một nút chứa khóa, giá trị và các liên kết cấu trúc. Trong mô hình thứ tự tăng thông thường:

- Khóa ở cây con trái nhỏ hơn khóa nút hiện tại.
- Khóa ở cây con phải lớn hơn khóa nút hiện tại.
- So sánh bằng 0 xác định cùng vị trí khóa theo thứ tự của TreeMap.

“Nhỏ hơn” và “lớn hơn” ở đây là theo compareTo hoặc Comparator được chọn, không nhất thiết là thứ tự số học tăng dần.

Hình minh họa:

```text
             40
           /    \
         20      60
        /  \    /  \
       10  30  50  70
```

Để tìm 50 theo thứ tự số tự nhiên:

1. So với 40: lớn hơn, đi phải.
2. So với 60: nhỏ hơn, đi trái.
3. So với 50: bằng, tìm được ánh xạ.

Không cần kiểm tra mọi khóa 10,20,30,40,50 theo một lượt quét danh sách.

Hình là mô hình cây tìm kiếm để giải thích đường đi, **không phải cam kết rằng TreeMap sau một dãy put cụ thể phải có đúng gốc, màu và hình dạng như hình**.

### 2.1. Cây có thứ tự không cần sắp xếp lại toàn bộ sau mỗi get

Cấu trúc đã được tổ chức theo quan hệ so sánh. Get đi theo các liên kết để tìm vị trí, không tạo một danh sách mới rồi sort mỗi lần.

Tương tự, duyệt theo thứ tự có thể đi trái–nút–phải trên cấu trúc đã có. Thứ tự là đặc tính của cách tổ chức cây.

### 2.2. Thứ tự chèn và thứ tự duyệt là hai chuyện khác nhau

Thêm 40,20,60,10 không có nghĩa TreeMap duyệt theo đúng trình tự thêm ấy.

Duyệt đi theo thứ tự khóa của map. Nếu Comparator đảo chiều, chiều “trái/phải” theo phép so sánh cũng phục vụ thứ tự đảo ấy.

Không cần biết màu nút hoặc gốc hiện tại để dùng hợp đồng thứ tự của lớp.

## 3. Vì sao một cây tìm kiếm thường chưa đủ?

Nếu chỉ thêm nút theo quy tắc tìm kiếm mà không cân bằng, một dãy khóa tăng có thể tạo chuỗi:

```text
1
 \
  2
   \
    3
     \
      4
       \
        5
```

Tìm khóa cuối phải đi gần hết chuỗi. Dù mỗi nút vẫn tuân quy tắc tìm kiếm, chiều cao đã cùng bậc với n, nên một lượt tìm có thể là O(n).

Vấn đề không phải dãy khóa sai. Cấu trúc cần có cách giữ hình dạng phù hợp bất kể dữ liệu đến theo thứ tự nào.

### 3.1. Chiều cao quyết định độ dài một đường tìm

Gọi h là chiều cao theo một quy ước thống nhất. Một lượt tìm từ gốc chỉ đi qua số nút tỉ lệ với h + 1.

Nếu h = O(log n), đường ấy ngắn theo cấp logarit. Nếu h = O(n), việc có hai liên kết trái/phải trên mỗi nút không tự giúp được nhiều.

Đây là mối liên hệ cần giữ: **cân bằng → chiều cao có giới hạn → đường tìm có giới hạn**.

### 3.2. Cân bằng không có nghĩa mọi tầng phải đầy

Một cây có thể chưa có đủ 2, 4, 8, ... nút ở từng tầng mà vẫn có chiều cao O(log n).

TreeMap không phải cây nhị phân hoàn hảo, không bắt buộc hai cây con của mọi nút có đúng cùng số phần tử, và không cần dùng một mảng đầy đủ để biểu diễn hình dạng.

Các điều kiện cân bằng được thiết kế để đủ mạnh cho giới hạn chiều cao, đồng thời vẫn có thể duy trì khi thêm hoặc xóa.

## 4. Cây đỏ–đen giữ chiều cao bằng các bất biến

Ở mô hình chuẩn:

1. Mỗi nút mang màu đỏ hoặc đen.
2. Gốc đen; lá NIL quy ước là đen.
3. Nút đỏ không có con đỏ.
4. Các đường từ một nút tới lá NIL có cùng số nút đen.

Với n nút dữ liệu, các điều kiện này dẫn tới h ≤ 2 log₂(n + 1), theo quy ước chiều cao chuẩn. Đếm nút thay vì cạnh có thể lệch một hằng số.

Lập luận cốt lõi: không có hai nút đỏ liên tiếp, nên số tầng chỉ tăng tối đa theo một hệ số hằng so với chiều cao đen; cây con với chiều cao đen b cần ít nhất 2^b − 1 nút. Suy ra chiều cao cùng bậc logarit.

Có thể đối chiếu phần chứng minh trong [bài giảng cây của Cornell CS2110](https://www.cs.cornell.edu/courses/cs2110/2018su/lectures/Lecture9-Trees.pdf). Điều cần dùng trong bài là giới hạn chiều cao, không phải học màu như một thuộc tính của dữ liệu nghiệp vụ.

### 4.1. Màu không tham gia so sánh khóa

Màu phục vụ duy trì cấu trúc. Nó không làm một khóa “ưu tiên hơn”, không thay Comparator và không phải thứ tự mới của map.

Hai cây có hình dạng hoặc màu khác nhau vẫn có thể biểu diễn cùng tập ánh xạ và cùng thứ tự khóa.

### 4.2. Java không yêu cầu người gọi tự quản lý màu

Các thao tác put/remove của TreeMap duy trì cấu trúc bên trong. Người dùng không cần cài các trường màu vào lớp khóa hoặc truyền thông tin cân bằng khi gọi API.

Nguồn [TreeMap.java OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/TreeMap.java) có các đường xoay và sửa cân bằng sau thêm/xóa. Chúng giúp giải thích cơ chế; mã ứng dụng không dựa vào tên hàm private để sử dụng map.

## 5. Xoay và đổi màu: đổi hình dạng nhưng giữ thứ tự

Một phép xoay trái khái niệm:

```text
       x                         y
      / \                       / \
     A   y       xoay trái     x   C
        / \       ---->      / \
       B   C                A   B
```

A, B, C là các cây con. Trước và sau phép xoay, thứ tự duyệt vẫn là:

```text
A → x → B → y → C
```

Các liên kết được tổ chức lại cục bộ, trong khi quan hệ thứ tự giữa các vùng được giữ. Đổi màu đi kèm giúp khôi phục các bất biến cần thiết.

### 5.1. Một phép xoay cục bộ không phải sắp xếp toàn bộ map

Phép xoay cập nhật một số liên kết quanh các nút liên quan. Không cần lấy tất cả khóa ra rồi sort lại.

Cả quy trình sửa sau thao tác có thể đi qua các tổ tiên để xử lý những vi phạm còn lại. Giới hạn chiều cao giúp phần công việc đó vẫn có giới hạn thích hợp.

### 5.2. Không cần cây luôn có hình dạng duy nhất

Các lịch thêm/xóa khác nhau có thể dẫn tới những bố trí hợp lệ khác nhau.

Hợp đồng công khai không yêu cầu một khóa cụ thể luôn là gốc hoặc một lần put nhất định luôn thực hiện đúng số phép xoay nào đó.

Kiểm thử tính đúng nên kiểm tra ánh xạ, thứ tự và hành vi API. Nếu đo chi tiết cài đặt, phải ghi rõ phiên bản và mục tiêu của phép quan sát.

## 6. Get, put và remove đi qua cây như thế nào?

### 6.1. Get và containsKey

Bắt đầu từ gốc, so sánh khóa cần tìm với khóa hiện tại:

- Kết quả âm: đi tới cây con trái.
- Kết quả dương: đi tới cây con phải.
- Kết quả 0: đã tìm thấy vị trí khóa theo thứ tự.
- Gặp liên kết rỗng: không có khóa cần tìm.

Một đường như vậy có độ dài O(log n) khi cây được duy trì cân bằng.

ContainsKey không cần duyệt mọi giá trị. Nó kiểm tra sự tồn tại theo cùng cấu trúc chỉ mục khóa.

### 6.2. Put khóa đã có

Nếu phép so sánh xác định một khóa tương đương đã có, put thay giá trị gắn với ánh xạ đó. Size không tăng.

Việc tìm được vị trí vẫn có chi phí theo đường cây; “chỉ thay một giá trị” không làm cả lời gọi trở thành O(1) nếu trước đó vẫn phải tìm khóa.

Thứ tự của TreeMap phải nhất quán với equals nếu muốn tuân đầy đủ hợp đồng Map chung. Không dùng một Comparator thiếu tiêu chí khiến hai khóa nghiệp vụ khác nhau bị xem bằng 0 rồi mong cây tự tách chúng.

### 6.3. Put khóa mới

Lời gọi tìm vị trí thích hợp, nối thêm nút, rồi thực hiện phần duy trì cân bằng nếu cần.

Tổng công việc có đường tìm và phần điều chỉnh cấu trúc. Đây là lý do O(log n) vẫn phù hợp khi thêm khóa, kể cả khi đầu vào đã tăng hoặc giảm có thứ tự.

Không phải chỉ thêm lá rồi để cây lệch vô hạn.

### 6.4. Remove có cả tìm và sửa cấu trúc

Trước hết cần tìm nút theo khóa. Sau khi xác định được nút, xử lý xóa tùy số con và tiếp tục sửa các bất biến nếu cần.

Với nút có hai con, một hướng xử lý là dùng phần tử kế tiếp theo thứ tự để đưa bài toán về trường hợp xóa đơn giản hơn. Nguồn OpenJDK 17u có đường xử lý này.

Không suy ra remove chắc O(1) vì “đã biết khóa”. Biết giá trị khóa chưa có nghĩa đang giữ trực tiếp vị trí nút để bỏ qua tìm kiếm, và phần sửa sau xóa cũng có chi phí.

## 7. Bảng chi phí: phân biệt hợp đồng với phân tích cài đặt

Bảng dưới dùng n là số ánh xạ và giả định phép so sánh/so bằng có chi phí hằng số, dữ liệu và thứ tự được dùng đúng hợp đồng.

| Thao tác | Chi phí theo mô hình thông thường | Cơ sở |
| --- | --- | --- |
| containsKey, get, put, remove | O(log n) | Bảo đảm được nêu trong tài liệu TreeMap |
| firstKey, lastKey | O(log n) ở trường hợp xấu | Trong OpenJDK, đi tới biên trái/phải của cây |
| floor, ceiling, lower, higher theo khóa | O(log n) theo đường cây | Tìm vị trí lân cận trong cây cân bằng |
| pollFirstEntry, pollLastEntry | O(log n) | Tìm biên rồi xóa/sửa cây |
| Duyệt toàn bộ entry bằng iterator | O(n) | Đi theo thứ tự các nút, không tìm lại từ gốc cho mỗi entry |
| containsValue | O(n) ở trường hợp xấu | Cây được chỉ mục theo khóa, không phải giá trị |
| size của TreeMap gốc | O(1) trong cài đặt đối chiếu | Đọc số lượng được lưu |
| Duyệt k kết quả trong một khoảng khóa | O(log n + k) theo đường iterator | Định vị phần bắt đầu rồi thăm kết quả |

Với n rất nhỏ hoặc bằng 0, có thể viết dạng log(n + 1) để tránh đọc log(0). Ký hiệu ở bảng mô tả cách tăng trưởng, không phải số nano giây của một lời gọi.

Các dòng phân tích cài đặt không nên bị biến thành một cam kết chung cho mọi implementation của NavigableMap hoặc SortedMap. Khi dùng lớp khác, phải đọc hợp đồng của lớp ấy.

### 7.1. Gốc không nhất thiết là nhỏ nhất hoặc lớn nhất

Cân bằng giúp gốc có vai trò phân chia vùng, không có nghĩa gốc giữ khóa đầu hay khóa cuối.

Trong cài đặt được đối chiếu, tìm khóa nhỏ nhất có thể phải đi hết một đường trái, và tìm khóa lớn nhất đi một đường phải. Không mặc định hai thao tác ấy O(1) chỉ vì map “đã có thứ tự”.

### 7.2. Một view nhỏ không tự có mọi thao tác O(1)

SubMap là một view theo khoảng, không phải một cây mới độc lập chứa sẵn riêng từng kết quả.

Ví dụ, size của view giới hạn trong nguồn OpenJDK 17u có thể cần duyệt để đếm, và có cơ chế nhớ kết quả theo trạng thái sửa đổi. Không suy từ size của TreeMap gốc sang một cam kết rằng mọi lần gọi size trên mọi view đều O(1).

Cũng không cộng O(log n) cho từng phần tử nếu đang dùng một iterator liên tiếp của view. Phần bắt đầu và phần duyệt kết quả có vai trò khác nhau.

## 8. Ví dụ Java 17: chèn tăng dần và đếm so sánh khi tìm

Chương trình dùng Comparator đúng theo thứ tự số, có thêm bộ đếm số lần compare được gọi. Bộ đếm không tham gia quyết định thứ tự.

Map được nạp bằng các khóa tăng dần. Nếu đây là cây tìm kiếm không cân bằng đơn giản, đầu vào như vậy dễ tạo chuỗi dài. TreeMap duy trì cơ chế cân bằng của nó.

Lưu thành **TreeMapSearchCost.java**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

public class TreeMapSearchCost {
    static final class CountingOrder implements Comparator<Integer> {
        int calls;

        @Override
        public int compare(Integer left, Integer right) {
            calls++;
            return Integer.compare(left, right);
        }

        void reset() {
            calls = 0;
        }
    }

    public static void main(String[] args) {
        System.out.println("ascending inserts; observed comparison counts");
        for (int n : new int[] {31, 127, 1023, 8191}) {
            CountingOrder order = new CountingOrder();
            TreeMap<Integer, Integer> map = new TreeMap<>(order);
            for (int key = 0; key < n; key++) {
                map.put(key, key * 10);
            }
            order.reset();
            Integer value = map.get(n - 1);
            System.out.println("n=" + n + "; value=" + value
                    + "; get comparisons=" + order.calls);
        }

        Map<Integer, String> sample = new TreeMap<>();
        for (int key : new int[] {40, 20, 60, 10, 30, 50, 70}) {
            sample.put(key, "v" + key);
        }
        System.out.println("get 30: " + sample.get(30));
        System.out.println("put old 30: " + sample.put(30, "updated"));
        System.out.println("remove 40: " + sample.remove(40));
        System.out.println("keys after removal: " + new ArrayList<>(sample.keySet()));
        System.out.println("value after update: " + sample.get(30));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TreeMapSearchCost.java
java TreeMapSearchCost
```

Stdout quan sát:

```text
ascending inserts; observed comparison counts
n=31; value=300; get comparisons=8
n=127; value=1260; get comparisons=12
n=1023; value=10220; get comparisons=18
n=8191; value=81900; get comparisons=24
get 30: v30
put old 30: v30
remove 40: v40
keys after removal: [10, 20, 30, 50, 60, 70]
value after update: updated
```

Các chương trình trong bài được biên dịch với --release 17 và chạy trên OpenJDK 24.0.2. Cờ --release chọn mức API/ngôn ngữ và bytecode đích; nó không biến JVM chạy thành Java 17.

### 8.1. Đọc bộ đếm đúng phạm vi

Khi n tăng từ 31 lên 8.191, số phép so sánh trong lượt tìm khóa cuối được quan sát tăng từ 8 lên 24, không thành một lượt đi qua 8.191 khóa.

Những con số chính xác này thuộc môi trường và lịch thao tác đã nêu. Một cài đặt hoặc hình dạng cây hợp lệ khác có thể có số khác mà vẫn đáp ứng O(log n).

Chương trình không dùng reflection để đo chiều cao hoặc xem màu. Nó quan sát các lời gọi Comparator được truyền vào công khai.

### 8.2. Đây chưa phải phép đo toàn bộ thời gian

Counter không đếm cấp phát, xoay, đổi màu, đi theo liên kết, quản lý bộ nhớ hoặc thời gian CPU cho các phần khác.

Đếm so sánh giúp làm rõ đường tìm; nó không biến toàn bộ chi phí put/remove thành đúng số lần compare. Lý do những thao tác ấy có giới hạn còn đến từ thuật toán duy trì cân bằng.

Không lấy một bộ đếm bằng 24 rồi diễn giải thành thời gian bằng 24 đơn vị tùy ý.

### 8.3. Nội dung vẫn phải đúng sau cập nhật và xóa

Phần map nhỏ kiểm tra get, put khóa cũ và remove, rồi duyệt các khóa còn lại.

Put 30 trả về v30 và thay giá trị thành updated. Remove 40 bỏ ánh xạ ấy. Dãy cuối vẫn tăng theo khóa: 10,20,30,50,60,70.

Không cần biết sau các thao tác ấy nút nào đang là gốc để xác nhận kết quả công khai.

## 9. Duyệt toàn cây không phải n lần tìm kiếm từ gốc

Một iterator có thể tìm nút đầu rồi tiếp tục qua phần tử kế tiếp theo thứ tự.

Trong OpenJDK 17u, các nút có liên kết con và cha; đường successor giúp đi tới phần tử kế tiếp mà không gọi một phép get mới cho mỗi khóa.

### 9.1. Một bước next có thể leo nhiều liên kết

Nếu nút có cây con phải, phần tử kế tiếp có thể là nút ngoài cùng trái của cây con ấy.

Nếu không, đường đi có thể phải leo lên tổ tiên thích hợp. Một bước riêng lẻ không nhất thiết chỉ thực hiện đúng một phép gán liên kết.

Nhưng xét cả lượt duyệt đầy đủ, tổng công đi qua các liên kết được phân bổ theo số nút. Ta không trả lại toàn bộ giá tìm từ gốc cho mỗi entry.

Vì thế có thể phân biệt:

- Chi phí trường hợp xấu của một bước đi kế tiếp.
- Chi phí trung bình phân bổ của các bước trong cả lượt duyệt.
- Chi phí tổng của việc duyệt toàn bộ n entry.

### 9.2. Duyệt entrySet thường thể hiện trực tiếp công việc hơn

Nếu cần cả khóa và giá trị, lấy chúng từ Map.Entry đang được duyệt.

Một vòng keySet rồi get từng khóa thực hiện thêm những lượt tìm kiếm theo khóa. Tính tổng có thể có phần O(n log n), dù chính iterator đang đi qua các khóa theo thứ tự.

Tương tự, tự gọi higherKey từ đầu cho từng bước là một dãy truy vấn điều hướng riêng. Không tự đồng nhất nó với đường iterator có trạng thái kế tiếp.

### 9.3. Xử lý kết quả cũng là công việc thật

Muốn xuất n ánh xạ thì ít nhất cần thăm n kết quả. Cây cân bằng không thể biến việc đọc mọi giá trị thành O(log n).

Nếu thân vòng lặp xử lý dữ liệu có kích thước khác nhau, hãy cộng cả chi phí xử lý ấy. O(n) của phần duyệt cấu trúc không phải bảo đảm mọi công việc trong vòng lặp đều hằng số.

## 10. ContainsValue không hưởng cùng chỉ mục với get

TreeMap tổ chức cây theo khóa. Khi chỉ cho một giá trị cần tìm, không có cơ sở dùng quan hệ khóa để quyết định bỏ cây con nào.

Trong cài đặt được đối chiếu, containsValue đi theo các entry để so giá trị và có thể dừng khi tìm thấy.

Nếu giá trị không có, có thể phải kiểm tra đủ n ánh xạ. Nếu giá trị xuất hiện gần đầu lượt duyệt, có thể dừng sớm hơn. O(n) ở đây là giới hạn trường hợp xấu.

Nếu một ứng dụng cần tra theo cả khóa lẫn giá trị rất thường xuyên, phải xem xét mô hình chỉ mục riêng phù hợp với quan hệ một–nhiều hoặc nhiều–nhiều của dữ liệu. Không giả định TreeMap đã tự có một cây thứ hai cho mọi giá trị.

Giá trị cũng có thể trùng ở nhiều khóa. Một thứ tự khóa không tạo quy tắc loại trùng giá trị.

## 11. Ví dụ Java 17: duyệt, tìm giá trị và một khoảng nhỏ

Chương trình tiếp theo tách hai loại bộ đếm:

- CountingOrder đếm so sánh khóa.
- Value.equalityChecks đếm phép equals trên giá trị được tìm.

Các bộ đếm chỉ phục vụ thí nghiệm một luồng, không được dùng để thay đổi kết quả so sánh hoặc so bằng.

Lưu thành **TreeMapTraversalCost.java**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class TreeMapTraversalCost {
    static final class CountingOrder implements Comparator<Integer> {
        int calls;

        @Override
        public int compare(Integer left, Integer right) {
            calls++;
            return Integer.compare(left, right);
        }
    }

    record Value(int number) {
        static int equalityChecks;

        @Override
        public boolean equals(Object other) {
            equalityChecks++;
            return other instanceof Value value && number == value.number;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(number);
        }
    }

    public static void main(String[] args) {
        CountingOrder order = new CountingOrder();
        TreeMap<Integer, Value> map = new TreeMap<>(order);
        for (int key = 0; key < 1000; key++) {
            map.put(key, new Value(key));
        }

        order.calls = 0;
        long sum = 0;
        int visited = 0;
        for (Map.Entry<Integer, Value> entry : map.entrySet()) {
            sum += entry.getValue().number();
            visited++;
        }
        System.out.println("full visited: " + visited);
        System.out.println("full sum: " + sum);
        System.out.println("observed full key comparisons: " + order.calls);

        Value.equalityChecks = 0;
        boolean found = map.containsValue(new Value(-1));
        System.out.println("missing value found: " + found);
        System.out.println("observed value equality checks: " + Value.equalityChecks);

        NavigableMap<Integer, Value> window = map.subMap(450, true, 455, false);
        long rangeSum = 0;
        int rangeVisited = 0;
        for (Map.Entry<Integer, Value> entry : window.entrySet()) {
            rangeSum += entry.getValue().number();
            rangeVisited++;
        }
        System.out.println("range keys: " + new ArrayList<>(window.keySet()));
        System.out.println("range visited: " + rangeVisited);
        System.out.println("range sum: " + rangeSum);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TreeMapTraversalCost.java
java TreeMapTraversalCost
```

Stdout trên môi trường đã nêu:

```text
full visited: 1000
full sum: 499500
observed full key comparisons: 0
missing value found: false
observed value equality checks: 1000
range keys: [450, 451, 452, 453, 454]
range visited: 5
range sum: 2260
```

### 11.1. Không gọi Comparator không có nghĩa không làm việc

Vòng entrySet đã thăm 1.000 entry và cộng được 499.500. Bộ đếm so sánh khóa bằng 0 trên môi trường này vì iterator đi theo cấu trúc thứ tự sẵn có.

Nó vẫn phải di chuyển qua các nút, lấy giá trị và cộng tổng. Không suy ra O(1) hoặc “không tốn thời gian” từ việc một loại hàm không được gọi.

### 11.2. Giá trị không có cần kiểm tra đủ dữ liệu

Map chứa các Value từ 0 tới 999. Tìm Value(-1) không thấy và bộ đếm equals là 1.000.

Kết quả làm rõ rằng chỉ mục đang theo khóa, không phải theo thành phần number của Value.

Một số phép so bằng có thể tốn hơn hằng số, nên chi phí thực còn phụ thuộc cách giá trị được biểu diễn.

### 11.3. Khoảng khóa chỉ đưa ra kết quả thuộc khoảng

SubMap dùng cận dưới 450 được lấy và cận trên 455 không được lấy, nên khóa là 450 tới 454, có 5 kết quả và tổng bằng 2.260.

View không tự sao chép toàn bộ 1.000 ánh xạ. Khi duyệt khoảng, mô hình công việc có phần định vị đầu và phần đi qua những entry thuộc kết quả.

Chương trình không thay đổi map trong khi duyệt. Không cần mở thêm một bài toán iterator bị sửa cấu trúc để quan sát chi phí ở đây.

### 11.4. Giới hạn của phép quan sát

Các số bộ đếm thuộc đường thực thi đã chạy, không phải yêu cầu rằng mọi phiên bản TreeMap phải gọi equals hoặc compare đúng cùng số lần.

Hợp đồng dữ liệu và giới hạn độ phức tạp là phần cần giữ. Thí nghiệm hỗ trợ giải thích cơ chế; nó không thay cho việc đọc hợp đồng và phân tích thuật toán.

## 12. Chi phí Comparator là một phần của thời gian thật

Khi viết O(log n), ta thường xem một phép so sánh khóa là hằng số. Giả định ấy cần được nói rõ.

Nếu một lần so sánh tốn S, phần chi phí so sánh có thể được mô tả bằng O(S log n) trong mô hình S là một chặn thích hợp cho các phép so sánh liên quan.

### 12.1. Chuỗi dài không giống số nguyên nhỏ

So sánh hai chuỗi có thể cần xét một đoạn ký tự chung trước khi tìm được khác biệt. Nếu độ dài liên quan là L, phần so sánh có thể phụ thuộc L.

Một cây có ít tầng không làm chi phí xử lý từng khóa dài tự biến mất.

Nếu Comparator phân tích lại văn bản hoặc tính một đại lượng đắt ở mỗi lượt, phần công việc ấy cũng được lặp theo số lần so sánh. Có thể cân nhắc biểu diễn khóa hoặc chuẩn bị dữ liệu trước, miễn giữ nguyên quy tắc thứ tự và hợp đồng khóa.

### 12.2. Comparator phải tạo một quan hệ thứ tự đúng

Một Comparator phải tuân các yêu cầu về dấu, tính bắc cầu và các trường hợp được xem bằng nhau theo hợp đồng của nó. Đối chiếu [Comparator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html).

Dùng Integer.compare thay cho phép trừ trực tiếp giúp tránh một lỗi tràn số phổ biến khi so số nguyên.

Không dùng số ngẫu nhiên, thời gian hiện tại hoặc trạng thái thay đổi tùy lần gọi để quyết định cùng một cặp khóa lúc lớn lúc nhỏ.

Cây cân bằng chỉ bảo vệ hình dạng theo thuật toán. Nó không sửa được một quan hệ thứ tự tự mâu thuẫn.

### 12.3. Không sửa thuộc tính dùng để sắp xếp khi khóa đang nằm trong cây

Nếu một đối tượng khóa đổi trường mà Comparator sử dụng, vị trí cũ có thể không còn phù hợp với thứ tự mới. Sau đó tìm kiếm hoặc điều hướng có thể cho kết quả không như mong đợi.

Việc cây vẫn còn nhiều nút và chiều cao vẫn thấp không chứng minh khóa đang được sử dụng đúng.

Giữ thông tin định danh/thứ tự ổn định; nếu cần thay đổi khóa logic, xử lý theo một quy trình bỏ ánh xạ cũ và thêm ánh xạ mới phù hợp với hợp đồng ứng dụng.

## 13. Nạp nhiều phần tử và sao chép: không áp một công thức cho mọi đường

### 13.1. N lần put riêng lẻ thường cho O(n log n)

Nạp n khóa mới vào một cây rỗng bằng các lời gọi put riêng lẻ có tổng chi phí O(n log n) theo mô hình so sánh hằng số.

Đầu vào đã sắp xếp không làm cây đỏ–đen biến thành chuỗi, nhưng cũng không tự biến một vòng n lần put thành một API dựng cây tuyến tính.

Từng lần vẫn có đường tìm vị trí cùng công duy trì cấu trúc tương ứng.

### 13.2. Nguồn đã sắp xếp có thể cho một đường dựng riêng

Constructor TreeMap nhận SortedMap được tài liệu mô tả là chạy tuyến tính và giữ thứ tự của nguồn.

Cài đặt OpenJDK 17u còn có đường buildFromSorted để dựng cấu trúc từ dữ liệu đã có thứ tự; putAll vào một map rỗng có những điều kiện tối ưu riêng liên quan loại nguồn và Comparator.

Vì vậy không phát biểu “mọi cách sao chép n entry vào TreeMap đều chắc mất n log n”. Phải biết constructor hoặc đường thao tác nào đang được dùng.

Nếu đối số chỉ là một Map bất kỳ, không được tự cho rằng nguồn đã có thứ tự đúng với Comparator của đích. Những overload và điều kiện khác nhau có ý nghĩa thật.

### 13.3. Kiểm tra lợi ích tổng thể

Nếu phải sort dữ liệu chưa có thứ tự chỉ để dùng một đường dựng tuyến tính, phần sort ấy cũng có chi phí. Không bỏ nó khỏi tổng rồi kết luận toàn bộ quy trình đã thành O(n).

Một tối ưu có lợi khi đầu vào đã phù hợp hoặc khi công chuẩn bị được dùng cho mục đích khác; cần tính toàn bộ đường dữ liệu.

## 14. Những điều cây cân bằng không tự cung cấp

### 14.1. Không có truy cập phần tử thứ k theo chỉ số chỉ vì cây cân bằng

TreeMap có thứ tự khóa, nhưng không có API get(index) như List để lấy mục thứ k theo vị trí.

Một cây thống kê thứ tự có thể lưu thêm số nút của từng cây con để phục vụ một loại truy vấn khác. Không tự gán khả năng đó cho TreeMap chỉ vì cũng dùng cây cân bằng.

Nếu tự duyệt tới vị trí k, công việc phải xét số bước duyệt ấy. Chỉ định vị bằng khóa và định vị bằng thứ hạng là hai yêu cầu khác nhau.

### 14.2. Không có chi phí bộ nhớ bằng một mảng khóa–giá trị

Nút cây cần giữ liên kết và thông tin phục vụ cân bằng bên cạnh khóa, giá trị. Bố trí đối tượng, các tham chiếu và tính cục bộ bộ nhớ có thể ảnh hưởng hiệu năng thực tế.

Không biến O(log n) thành kết luận rằng TreeMap luôn nhanh hoặc tiết kiệm bộ nhớ hơn mọi cấu trúc khác.

TreeMap cũng không có tham số initialCapacity kiểu bảng băm để người gọi chọn số bucket. Cơ chế tăng cấu trúc bằng nút và duy trì cân bằng khác với tăng một bảng bucket.

### 14.3. Không tự an toàn đa luồng

Cân bằng là một thuộc tính thuật toán của cấu trúc, không phải cơ chế phối hợp các luồng.

TreeMap không tự đồng bộ. Khi dùng chung với các thao tác sửa cấu trúc, cần một thiết kế phối hợp phù hợp; fail-fast không thay thế trách nhiệm ấy.

Không lấy việc các get/put có giới hạn logarit làm bằng chứng chúng tự nguyên tử hoặc an toàn trong mọi chuỗi thao tác đồng thời.

## 15. Chọn TreeMap theo loại truy vấn

| Yêu cầu chính | Điều cần cân nhắc |
| --- | --- |
| Tìm theo khóa và cần giữ thứ tự khóa | TreeMap cung cấp cả ánh xạ và thứ tự |
| Cần floor/ceiling, phần tử trước/sau hoặc khoảng khóa | Cây có thứ tự phục vụ trực tiếp những truy vấn này |
| Chỉ cần tìm theo khóa, không cần thứ tự | So sánh với cấu trúc băm theo điều kiện dữ liệu và hiệu năng |
| Cần thứ tự chèn | Thứ tự khóa của TreeMap không biểu diễn yêu cầu ấy |
| Cần lấy mục thứ k theo chỉ số | Cần xem mô hình dữ liệu/API khác phù hợp |
| Dữ liệu hầu như bất biến, chủ yếu duyệt hoặc tìm | Có thể đánh giá cả cách lưu liên tục đã sắp xếp theo toàn bộ quy trình |

Không chọn chỉ bằng một ký hiệu O. Cần xét loại truy vấn, chi phí khóa, tần suất thêm/xóa, bộ nhớ và khối lượng thực tế.

Nếu đo hiệu năng, tách công dựng dữ liệu khỏi công truy vấn khi câu hỏi cần tách. Dùng kết quả truy vấn thật, làm nóng JVM và tránh lấy thời gian in console làm phép đo thao tác cấu trúc.

## 16. Lỗi thường gặp và cách sửa

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| Có hai nhánh nên tìm luôn log n | Cần chiều cao được giữ có giới hạn |
| Chèn tăng dần làm mọi TreeMap lệch thành chuỗi | Cây đỏ–đen duy trì cân bằng khi cập nhật |
| Cân bằng nghĩa hai bên có đúng cùng số nút | Chỉ cần các bất biến đủ để chặn chiều cao |
| Gốc luôn là khóa nhỏ nhất | Biên trái/phải và gốc là các vai trò khác nhau |
| Đã biết khóa nên remove O(1) | Vẫn cần tìm vị trí và duy trì cấu trúc |
| Mọi phương thức TreeMap đều log n | Duyệt toàn bộ và containsValue có chi phí khác |
| Iterator next giống một get từ gốc | Iterator có trạng thái để đi tiếp qua cấu trúc |
| Không gọi Comparator khi duyệt nghĩa duyệt miễn phí | Vẫn phải thăm và xử lý các entry |
| Comparator đắt không ảnh hưởng kết luận thời gian | Chi phí so sánh là một phần của tổng |
| Cây tự sửa được khóa thay trường sắp xếp | Khóa vẫn phải ổn định theo hợp đồng |
| N lần put lên dữ liệu đã sắp xếp luôn là O(n) | Phân biệt vòng put với đường dựng chuyên biệt |
| Số compare trong một lần chạy là hằng số API | Đó là quan sát môi trường, không phải cam kết số lần chính xác |
| Có thứ tự là có truy cập theo chỉ số | Thứ tự khóa không tự tạo truy vấn thứ hạng |
| Cân bằng đồng nghĩa an toàn nhiều luồng | Đồng bộ là một trách nhiệm khác |

## 17. Bài tập tự kiểm tra có lời giải

### Bài 1: Một cây tìm kiếm bị lệch

Một cây tìm kiếm không cân bằng nhận các khóa 1 tới n theo thứ tự tăng và chỉ nối khóa mới ở phía phải. Tìm n có thể tốn bao nhiêu?

**Lời giải:** O(n), vì phải đi qua một chuỗi có chiều dài cùng bậc n. Quy tắc tìm kiếm không tự bảo đảm chiều cao logarit.

### Bài 2: Cây đỏ–đen đã có thứ tự đầu vào

TreeMap nhận n khóa tăng dần bằng các lần put. Có được kết luận lượt get cuối chắc là O(n) chỉ vì đầu vào tăng dần không?

**Lời giải:** không. TreeMap duy trì cơ chế cân bằng; hợp đồng của nó bảo đảm chi phí log(n) cho các thao tác cơ bản được nêu.

### Bài 3: Thăm mọi ánh xạ

Cần tính tổng giá trị của n entry. Cây cân bằng có làm tổng công duyệt chỉ còn O(log n) không?

**Lời giải:** không. Phải thăm n kết quả, nên phần duyệt toàn bộ là O(n), chưa kể chi phí xử lý từng giá trị.

### Bài 4: Tìm theo giá trị không có

TreeMap có n khóa và không chứa giá trị v. ContainsValue(v) có dùng cùng đường chỉ mục khóa như get(key) không?

**Lời giải:** không. Không có khóa để chọn hướng theo quan hệ sắp xếp. Trường hợp xấu cần kiểm tra n giá trị.

### Bài 5: Comparator xử lý chuỗi dài

Nếu một phép so sánh khóa tốn tới O(L), chỉ viết “get tốn O(log n) thời gian bất kể độ dài chuỗi” có đủ chính xác không?

**Lời giải:** chưa đủ. Theo mô hình đó, phần công so sánh có thể là O(L log n). Cần nêu giả định về chi phí từng phép so sánh.

### Bài 6: Mỗi khóa lại tìm giá trị

Vòng lặp duyệt keySet rồi get từng khóa có nhất thiết giữ tổng chi phí bằng đường entrySet thuần không?

**Lời giải:** không. Nó thêm n lượt tìm theo khóa, có thể tạo phần O(n log n). Nếu cần cả hai thành phần, dùng Map.Entry đang duyệt.

### Bài 7: Một khoảng có k kết quả

Với iterator của view khoảng, mô hình O(log n + k) đang phân biệt hai loại công nào?

**Lời giải:** định vị biên bắt đầu trên cây và thăm các entry thuộc khoảng. Nó không cần một get mới cho mỗi kết quả.

### Bài 8: Dựng từ SortedMap

Có được nói mọi constructor sao chép TreeMap đều n log n mà không xem loại nguồn không?

**Lời giải:** không. Constructor nhận SortedMap được tài liệu mô tả chạy tuyến tính. Cần phân biệt đường xây dựng và cả chi phí chuẩn bị nguồn.

### Bài 9: Hình dạng cây trong kiểm thử

Một bản JDK khác có cùng ánh xạ, cùng thứ tự và đáp ứng giới hạn thao tác, nhưng gốc/màu khác hình minh họa. Có đủ để kết luận sai không?

**Lời giải:** không. Hình minh họa không phải hợp đồng về bố trí nội bộ; phải kiểm tra các bảo đảm công khai.

## 18. Tài liệu đối chiếu

- [TreeMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html): cây đỏ–đen, bảo đảm thao tác cơ bản, containsValue và các constructor.
- [TreeMap.java OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/TreeMap.java): tìm kiếm, xoay, sửa sau thêm/xóa, iterator và dựng từ nguồn có thứ tự.
- [NavigableMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableMap.html): truy vấn lân cận và view theo khoảng.
- [Comparator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html): hợp đồng phép so sánh khóa.
- [Cornell CS2110 — Trees](https://www.cs.cornell.edu/courses/cs2110/2018su/lectures/Lecture9-Trees.pdf): các bất biến và giới hạn chiều cao cây đỏ–đen.

Cân bằng giữ đường tìm theo khóa ngắn, còn tổng chi phí phụ thuộc thao tác đang làm và công việc tại mỗi bước. Hiểu cả cấu trúc lẫn phạm vi của bảng độ phức tạp giúp dùng TreeMap đúng cho tìm kiếm, cập nhật, duyệt và truy vấn theo thứ tự.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tree-map-003">Comparator, compareTo và khóa được xem là trùng</a>

<details>
<summary>Xem chi tiết</summary>

# TreeMap: Comparator, compareTo và khóa được xem là trùng

## 1. Một quy tắc so sánh quyết định cả thứ tự lẫn danh tính khóa

`TreeMap` lưu các cặp khóa–giá trị và duyệt khóa theo thứ tự đã chọn. Ví dụ, ta có thể muốn tra dữ liệu của một phiên bản phần mềm và đồng thời duyệt các phiên bản từ thấp đến cao. Một map có thứ tự như vậy cần biết khóa nào đứng trước khóa nào.

Điểm dễ bỏ sót là: trong `TreeMap`, quy tắc so sánh còn quyết định **hai khóa có được xem là cùng một khóa hay không**. Nó không chỉ được dùng lúc in hoặc sắp xếp kết quả cuối cùng.

Giả sử một comparator chỉ so sánh độ dài chuỗi. Hai chuỗi `"cat"` và `"dog"` có nội dung khác nhau, nhưng comparator trả về 0 vì cùng dài ba ký tự. Đưa lần lượt chúng vào cùng `TreeMap` sẽ tác động đến cùng một mapping theo cách nhìn của map. Nếu muốn giữ cả hai như hai khóa riêng, quy tắc so sánh cần phân biệt chúng.

Bài học sẽ đi theo ba câu hỏi:

1. `TreeMap` lấy quy tắc so sánh từ `Comparable.compareTo` hay từ `Comparator.compare`?
2. Kết quả âm, 0, dương có ý nghĩa gì khi tìm và cập nhật khóa?
3. Làm thế nào thiết kế quy tắc so sánh để không vô tình gộp các khóa cần giữ riêng?

Ở đây, “khóa trùng” có nghĩa **trùng theo thứ tự của chính map**, không phải cùng tham chiếu đối tượng. Việc nó có đồng thời trùng theo `equals` hay không là một yêu cầu thiết kế cần kiểm tra.

## 2. Hai nguồn cung cấp thứ tự

### 2.1. Comparable và thứ tự tự nhiên của kiểu

Một kiểu triển khai `Comparable<T>` cung cấp phương thức `compareTo(T other)`. Đây là thứ tự tự nhiên mà kiểu đó công bố. Ví dụ, một kiểu `Version` có thể quy định so số phiên bản lớn trước, rồi số phiên bản nhỏ nếu phần lớn bằng nhau.

Trong ví dụ `Version` phía dưới, `compareTo` so phần phiên bản lớn trước, rồi so phần nhỏ. Khả năng so sánh này được khai báo trực tiếp trên kiểu khóa.

Khi tạo `new TreeMap<K, V>()`, map dùng thứ tự tự nhiên của khóa. Các khóa phải có khả năng so sánh với nhau theo thứ tự đó. Generics không tự kiểm tra tất cả điều kiện này ngay ở nơi khai báo `TreeMap`: một lớp không triển khai `Comparable` vẫn có thể xuất hiện trong khai báo kiểu, rồi gây lỗi khi map phải so sánh khóa.

### 2.2. Comparator là quy tắc được truyền từ bên ngoài

`Comparator<T>` cung cấp `compare(T left, T right)`. Nó phù hợp khi muốn chọn thứ tự riêng cho một map, khi kiểu khóa không thể sửa, hoặc khi cùng một kiểu cần nhiều cách sắp thứ tự.

Đoạn minh họa bên trong một phương thức:

```java
Comparator<String> byLength = Comparator.comparingInt(String::length);
TreeMap<String, Integer> map = new TreeMap<>(byLength);
```

Map này dùng `byLength` để so khóa. Nó không tự gọi thêm `String.compareTo` khi độ dài bằng nhau. Nếu muốn có tiêu chí phụ theo nội dung chuỗi, phải ghi rõ tiêu chí đó trong comparator.

Một comparator có thể sử dụng `compareTo` ở bên trong, chẳng hạn `Comparator.naturalOrder()`. Nhưng đó là lựa chọn trong cài đặt comparator; không có hai vòng độc lập kiểu “comparator cho thứ tự, equals cho trùng khóa” ở `TreeMap`.

### 2.3. Constructor chọn một quy tắc có hiệu lực

| Cách tạo map | Nguồn thứ tự | `map.comparator()` |
|---|---|---|
| `new TreeMap<>()` | Thứ tự tự nhiên của khóa | `null` |
| `new TreeMap<>(comparator)` với comparator khác `null` | Comparator được cung cấp | Comparator đó |
| Constructor nhận comparator `null` | Thứ tự tự nhiên | `null` |

`comparator() == null` không có nghĩa map không được sắp thứ tự. Nó cho biết map dùng thứ tự tự nhiên. Nếu đã có comparator khác `null`, kiểu khóa không bắt buộc tự triển khai `Comparable`, miễn comparator so sánh hợp lệ mọi khóa được đưa vào.

Ví dụ, một record có hai trường không tự sinh `compareTo` chỉ vì nó là record. Record có thể tự sinh `equals` theo các thành phần, nhưng khả năng so thứ tự vẫn phải được khai báo hoặc được cung cấp qua comparator.

## 3. Đọc kết quả so sánh theo dấu

Cả `compareTo` lẫn `compare` trả một số nguyên:

| Kết quả | Ý nghĩa trong thứ tự được chọn |
|---|---|
| `< 0` | Khóa bên trái đứng trước khóa bên phải |
| `== 0` | Hai khóa tương đương theo thứ tự |
| `> 0` | Khóa bên trái đứng sau khóa bên phải |

API chỉ yêu cầu **dấu** của kết quả. Không được kiểm tra chung mọi comparator bằng `result == -1` hoặc `result == 1`. Một cài đặt hợp lệ có thể trả một số âm hoặc dương khác. Cách kiểm tra đúng cho quan hệ trước–sau là so với 0.

“Đứng trước” cũng không luôn đồng nghĩa “giá trị số nhỏ hơn”. Với comparator đảo thứ tự số, số 10 có thể đứng trước số 2. Ý nghĩa đầu/cuối và trước/sau của map đều theo quy tắc đang dùng.

### 3.1. Cách tìm kiếm được hình dung qua cây

Không cần tự cài cây để hiểu ảnh hưởng của comparator. Có thể hình dung ở mỗi khóa đang xét, map lấy kết quả so sánh để quyết định:

```text
So khóa cần tìm với khóa đang xét
    ├─ âm   → tiếp tục theo phía khóa đứng trước
    ├─ dương → tiếp tục theo phía khóa đứng sau
    └─ bằng 0 → đã gặp khóa được xem là tương đương
```

`TreeMap` thực tế dùng cây đỏ–đen để duy trì cấu trúc cân bằng. Chi tiết xoay cây không làm thay đổi quy tắc nhận diện khóa: kết quả so sánh bằng 0 là trường hợp gặp cùng khóa theo thứ tự. Đây không phải việc sắp lại một danh sách sau khi đã lưu tất cả đối tượng riêng biệt.

### 3.2. Kết quả bằng 0 chi phối các thao tác theo khóa

Giả sử `compare(a, b) == 0` theo quy tắc của map:

| Thao tác | Điều cần dự đoán |
|---|---|
| `put(a, v1)` rồi `put(b, v2)` | Lần sau cập nhật mapping tương đương; không tăng số mapping chỉ vì b là đối tượng khác |
| `get(b)` khi đã có a | Có thể tìm mapping của a theo so sánh bằng 0 |
| `containsKey(b)` khi đã có a | Nhận diện cùng nhóm khóa tương đương |
| `remove(b)` khi đã có a | Có thể loại mapping mà a đã tạo |

Các từ “tương đương” trong bảng không nói đến giá trị `V`. TreeMap sắp và nhận diện **khóa K**. Hai giá trị bằng nhau không làm hai khóa khác nhau bị gộp; hai giá trị khác nhau cũng không cứu được hai khóa mà comparator đã xem là tương đương.

### 3.3. Không nhầm với hash collision

Khóa có cùng mã băm trong `HashMap` chưa có nghĩa là cùng khóa; cấu trúc băm còn xét phép bằng nhau theo hợp đồng của nó. `TreeMap` dùng phép so sánh thứ tự cho việc nhận diện khóa, không dùng `hashCode` để quyết định các khóa đi về đâu.

Vì vậy, sửa `hashCode` không giải quyết việc comparator trả 0 cho hai khóa cần giữ riêng. Ngược lại, cài `equals` rất đầy đủ nhưng comparator bỏ sót một thành phần quan trọng vẫn có thể làm `TreeMap` cập nhật nhầm nhóm khóa theo ý định ứng dụng.

## 4. Chương trình 1: compareTo xác định thứ tự tự nhiên và khóa trùng

Lưu chương trình đầy đủ vào `TreeMapComparableDemo.java`. Ví dụ tương thích Java 17 trở lên. `Version` là record có hai thành phần số; `equals` do record cung cấp xét cả hai thành phần, và `compareTo` cũng xét cả hai.

```java
import java.util.Comparator;
import java.util.TreeMap;

public class TreeMapComparableDemo {
    record Version(int major, int minor) implements Comparable<Version> {
        @Override
        public int compareTo(Version other) {
            int result = Integer.compare(major, other.major);
            return result != 0 ? result : Integer.compare(minor, other.minor);
        }

        @Override
        public String toString() {
            return major + "." + minor;
        }
    }

    public static void main(String[] args) {
        Version first = new Version(1, 2);
        Version later = new Version(1, 10);
        Version same = new Version(1, 2);
        System.out.println("sameReference=" + (first == same));
        System.out.println("sameEquals=" + first.equals(same));
        System.out.println("sameCompare=" + first.compareTo(same));
        System.out.println("firstBeforeLater=" + (first.compareTo(later) < 0));

        TreeMap<Version, String> natural = new TreeMap<>();
        natural.put(later, "later");
        natural.put(first, "initial");
        natural.put(new Version(2, 0), "major-two");
        System.out.println("oldValue=" + natural.put(same, "updated"));
        System.out.println("natural=" + natural);
        System.out.println("naturalSize=" + natural.size());
        System.out.println("lookup=" + natural.get(new Version(1, 2)));
        System.out.println("usesNaturalOrder=" + (natural.comparator() == null));

        TreeMap<Version, String> reverse =
                new TreeMap<>(Comparator.reverseOrder());
        reverse.putAll(natural);
        System.out.println("reverseKeys=" + reverse.keySet());
        System.out.println("reverseDuplicateCompare="
                + reverse.comparator().compare(first, same));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TreeMapComparableDemo.java
java TreeMapComparableDemo
```

Stdout:

```text
sameReference=false
sameEquals=true
sameCompare=0
firstBeforeLater=true
oldValue=initial
natural={1.2=updated, 1.10=later, 2.0=major-two}
naturalSize=3
lookup=updated
usesNaturalOrder=true
reverseKeys=[2.0, 1.10, 1.2]
reverseDuplicateCompare=0
```

### 4.1. Khác đối tượng vẫn có thể là cùng khóa

`first` và `same` được tạo bằng hai lời gọi constructor nên `sameReference=false`. Tuy nhiên, chúng cùng biểu diễn 1.2, `equals` trả `true` và `compareTo` trả 0. Lần `put(same, "updated")` cập nhật mapping mà `first` đã tạo, trả về giá trị cũ `initial`.

Map có ba nhóm khóa: 1.2, 1.10 và 2.0. Số lần gọi `put` là bốn nhưng số mapping là ba. `get(new Version(1, 2))` tìm thấy `updated` mà không cần dùng đúng đối tượng đã truyền vào lần chèn đầu.

Đó là hành vi mong muốn khi khóa biểu diễn một giá trị: không cần giữ lại cùng một tham chiếu để có thể tìm dữ liệu.

### 4.2. So sánh từng thành phần, không so chuỗi hiển thị

`compareTo` so `major` trước. Chỉ khi `major` bằng nhau mới so `minor`. Do đó 1.2 đứng trước 1.10 vì 2 nhỏ hơn 10. Chương trình không chuyển khóa thành chuỗi rồi so `"1.2"` với `"1.10"` theo thứ tự ký tự.

Phương thức `toString` chỉ làm dòng in dễ đọc; nó không được `TreeMap` gọi làm quy tắc so sánh khóa. Cài đặt dùng `Integer.compare`, tránh lấy hiệu hai số có thể tràn miền giá trị.

### 4.3. Đảo thứ tự không tách khóa đã tương đương

Map thứ hai nhận `Comparator.reverseOrder()` và duyệt `[2.0, 1.10, 1.2]`. Nó vẫn coi hai phiên bản 1.2 là tương đương, nên `reverseDuplicateCompare=0`.

Đảo chiều thay quan hệ trước/sau; nó không biến các cặp bằng nhau theo so sánh thành khác nhau. Nếu comparator ban đầu làm hai khóa cần giữ riêng bị gộp, thêm `.reversed()` không sửa được lỗi đó.

## 5. Comparator trả 0 nhưng equals trả false

### 5.1. Cần phân biệt hai quan hệ bằng nhau

Với hai khóa hợp lệ a và b, quy tắc thứ tự được gọi là **nhất quán với equals** khi:

```text
compare(a, b) == 0  ⇔  a.equals(b)
```

Dấu hai chiều rất quan trọng. Không chỉ cần `equals=true` thì so sánh bằng 0; cũng cần so sánh bằng 0 thì `equals=true`. Nếu chỉ thỏa một chiều, khái niệm cùng khóa theo map có thể khác với khái niệm cùng đối tượng giá trị ở những API khác.

Tài liệu [TreeMap — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html) giải thích rằng map dùng so sánh cho mọi khóa. Một thứ tự không nhất quán với `equals` vẫn có thể cho hành vi tìm/cập nhật theo comparator được xác định, nhưng map không còn tuân thủ đầy đủ hợp đồng chung của `Map` dựa trên `equals`.

Không nên hiểu điều này thành “TreeMap luôn kiểm tra equals sau khi comparator trả 0” hoặc “TreeMap sẽ tự ném ngoại lệ khi hai quan hệ khác nhau”. Nó tiếp tục làm việc theo so sánh của mình.

### 5.2. Ví dụ không phân biệt hoa thường

`String.CASE_INSENSITIVE_ORDER` xem `"Java"`, `"JAVA"`, `"java"` là tương đương về thứ tự, trong khi `String.equals` phân biệt nội dung chữ hoa/chữ thường. Đây là ví dụ dễ quan sát việc comparator quyết định khóa trùng.

Lưu chương trình đầy đủ vào `TreeMapCaseInsensitiveDemo.java`:

```java
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class TreeMapCaseInsensitiveDemo {
    public static void main(String[] args) {
        String first = new String("Java");
        String second = new String("JAVA");
        Comparator<String> order = String.CASE_INSENSITIVE_ORDER;
        System.out.println("stringEquals=" + first.equals(second));
        System.out.println("comparison=" + order.compare(first, second));

        TreeMap<String, String> map = new TreeMap<>(order);
        map.put(first, "initial");
        System.out.println("oldValue=" + map.put(second, "updated"));
        System.out.println("size=" + map.size());
        System.out.println("storedKeys=" + map.keySet());
        System.out.println("getLowercase=" + map.get("java"));
        System.out.println("containsMixedCase=" + map.containsKey("jAvA"));
        System.out.println("removed=" + map.remove("jAvA"));
        System.out.println("sizeAfterRemove=" + map.size());

        Map<String, Integer> tree = new TreeMap<>(order);
        Map<String, Integer> hash = new HashMap<>();
        tree.put("Java", 1);
        hash.put("JAVA", 1);
        System.out.println("treeEqualsHash=" + tree.equals(hash));
        System.out.println("hashEqualsTree=" + hash.equals(tree));

        TreeMap<String, String> nullFriendly =
                new TreeMap<>(Comparator.nullsFirst(order));
        nullFriendly.put(null, "null-key");
        nullFriendly.put("A", null);
        System.out.println("nullFriendlyKeys=" + nullFriendly.keySet());
        System.out.println("nullKeyValue=" + nullFriendly.get(null));
        System.out.println("getLowercaseNullValue=" + nullFriendly.get("a"));
        System.out.println("containsLowercaseA=" + nullFriendly.containsKey("a"));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TreeMapCaseInsensitiveDemo.java
java TreeMapCaseInsensitiveDemo
```

Stdout:

```text
stringEquals=false
comparison=0
oldValue=initial
size=1
storedKeys=[Java]
getLowercase=updated
containsMixedCase=true
removed=updated
sizeAfterRemove=0
treeEqualsHash=false
hashEqualsTree=true
nullFriendlyKeys=[null, A]
nullKeyValue=null-key
getLowercaseNullValue=null
containsLowercaseA=true
```

### 5.3. Thay giá trị, tìm và xóa bằng một biến thể khác

`stringEquals=false` nhưng `comparison=0`. Sau hai lần `put`, `size=1`; giá trị cũ `initial` được trả ra và mapping nhận giá trị mới `updated`.

Trong lần chạy này, `storedKeys=[Java]` cho thấy dạng khóa được giữ để duyệt vẫn là `Java`, dù giá trị đã được cập nhật bằng lời gọi dùng `JAVA`. Không nên dùng việc cập nhật một khóa tương đương làm cơ chế chuẩn hóa cách viết khóa đang hiển thị, hay dựa vào danh tính đối tượng khóa để quyết định logic nghiệp vụ. Nếu cần một dạng khóa chuẩn, hãy tạo và dùng dạng đó rõ ràng trước khi lưu.

`get("java")`, `containsKey("jAvA")` và `remove("jAvA")` đều nhận diện cùng mapping. Sau remove, map rỗng. Phạm vi của quan hệ tương đương không chỉ nằm ở lệnh chèn thứ hai; nó đi theo các thao tác tra và loại khóa.

### 5.4. Hệ quả khi so sánh với map khác

Phần `treeEqualsHash=false` nhưng `hashEqualsTree=true` cho thấy một biểu hiện cụ thể của việc vi phạm hợp đồng chung. Hai map giữ các khóa `Java` và `JAVA`; khi các hướng so sánh tra cứu theo các quy tắc khóa khác nhau, kết quả phép bằng nhau có thể mất tính đối xứng.

Ở chiều `tree.equals(hash)`, khóa Java của tree được tra trong hash và không trùng JAVA theo `String.equals`. Ở chiều `hash.equals(tree)`, khóa JAVA lại được tra trong tree và khớp Java theo comparator. Đây không phải một bảo đảm nên khai thác trong chương trình. Ví dụ cố ý cho thấy vì sao lời cảnh báo về tính nhất quán với `equals` có ảnh hưởng thực tế, thay vì chỉ là một yêu cầu hình thức. Với map dùng chung qua các API tổng quát, nên thiết kế kiểu khóa và quy tắc so sánh đồng thuận về thế nào là cùng khóa.

Nếu nghiệp vụ thật sự coi mọi biến thể hoa thường là cùng mã, một lựa chọn là xác định dạng chuẩn ở ranh giới nhập dữ liệu hoặc tạo một kiểu khóa có `equals`, `hashCode`, thứ tự cùng dựa trên dạng chuẩn đó. Quy tắc chuẩn hóa phải được chọn theo miền dữ liệu; comparator không tự sửa `String.equals` và cũng không thay thế một chính sách chuẩn hóa được mô tả rõ.

### 5.5. null của khóa và null của giá trị là hai điều khác nhau

Cuối chương trình, `Comparator.nullsFirst(order)` bổ sung quy tắc cho khóa `null`: `null` đứng trước khóa khác `null`, còn hai khóa khác `null` được so bằng comparator đã chọn. Nhờ vậy map có thể giữ khóa `null` trong ví dụ này.

`new TreeMap<>()` dùng thứ tự tự nhiên không cho phép khóa `null`. Một comparator khác không xử lý `null` cũng có thể từ chối nó. Vì vậy không có quy tắc chung rằng mọi `TreeMap` đều nhận hoặc mọi `TreeMap` đều từ chối khóa `null`; điều đó phụ thuộc quy tắc thứ tự.

Giá trị `null` không tham gia so thứ tự khóa. Mapping `A → null` vẫn tồn tại; `get("a")` trả `null` và `containsKey("a")` là `true` theo comparator không phân biệt hoa thường. Kết quả `get == null` không tự chứng minh rằng khóa không có.

## 6. Chương trình 3: tiêu chí phụ quyết định có giữ riêng hai khóa không

### 6.1. Chỉ so độ ưu tiên sẽ gộp các mã cùng mức

Giả sử khóa `Ticket` có hai thành phần: `priority` là độ ưu tiên và `code` là mã. Nếu comparator chỉ nhìn `priority`, hai ticket 1:A và 1:B sẽ được xem là cùng khóa dù record `equals` thấy chúng khác nhau.

Đây không phải lỗi của `Comparator.comparingInt`. Nó đã thực hiện đúng tiêu chí được yêu cầu. Vấn đề là tiêu chí đó chưa thể hiện đủ quan hệ phân biệt khóa mà ứng dụng mong muốn.

Khi muốn thứ tự ưu tiên trước, mã sau, có thể nối tiêu chí bằng `thenComparing`. Tiêu chí sau chỉ được dùng khi tiêu chí trước trả 0; nó không thay đổi kết quả trước/sau đã phân biệt được ở tiêu chí trước.

Lưu chương trình đầy đủ vào `TreeMapComparatorChainDemo.java`:

```java
import java.util.Comparator;
import java.util.Objects;
import java.util.TreeMap;

public class TreeMapComparatorChainDemo {
    record Ticket(int priority, String code) {
        Ticket {
            Objects.requireNonNull(code, "code");
        }

        @Override
        public String toString() {
            return priority + ":" + code;
        }
    }

    public static void main(String[] args) {
        Ticket a = new Ticket(1, "A");
        Ticket b = new Ticket(1, "B");
        Comparator<Ticket> byPriority =
                Comparator.comparingInt(Ticket::priority);

        System.out.println("aEqualsB=" + a.equals(b));
        System.out.println("priorityCompare=" + byPriority.compare(a, b));
        TreeMap<Ticket, String> collapsed = new TreeMap<>(byPriority);
        collapsed.put(a, "alpha");
        System.out.println("collapsedOld=" + collapsed.put(b, "beta"));
        System.out.println("collapsed=" + collapsed);
        System.out.println("collapsedSize=" + collapsed.size());
        System.out.println("lookupA=" + collapsed.get(a));
        System.out.println("reverseStillEqual="
                + (byPriority.reversed().compare(a, b) == 0));

        Comparator<Ticket> fullOrder =
                byPriority.thenComparing(Ticket::code);
        TreeMap<Ticket, String> complete = new TreeMap<>(fullOrder);
        complete.put(new Ticket(2, "C"), "gamma");
        complete.put(b, "beta");
        complete.put(a, "alpha");
        System.out.println("complete=" + complete);
        System.out.println("completeSize=" + complete.size());
        System.out.println("lookupB=" + complete.get(new Ticket(1, "B")));
        System.out.println("sameKeyOld="
                + complete.put(new Ticket(1, "B"), "beta-2"));
        System.out.println("sizeAfterSameKey=" + complete.size());
        System.out.println("completeAfterUpdate=" + complete);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TreeMapComparatorChainDemo.java
java TreeMapComparatorChainDemo
```

Stdout:

```text
aEqualsB=false
priorityCompare=0
collapsedOld=alpha
collapsed={1:A=beta}
collapsedSize=1
lookupA=beta
reverseStillEqual=true
complete={1:A=alpha, 1:B=beta, 2:C=gamma}
completeSize=3
lookupB=beta
sameKeyOld=beta
sizeAfterSameKey=3
completeAfterUpdate={1:A=alpha, 1:B=beta-2, 2:C=gamma}
```

### 6.2. Đọc kết quả của map chỉ có một tiêu chí

`aEqualsB=false` nhưng `priorityCompare=0`. Lần thêm b trả giá trị cũ `alpha`, map chỉ có một mapping và `lookupA=beta`. Nghĩa là ứng dụng không còn hai chỗ độc lập để lưu dữ liệu alpha và beta theo hai mã.

Dòng `collapsed={1:A=beta}` là kết quả đã quan sát: entry được hiển thị bằng khóa 1:A nhưng giá trị đã được cập nhật bởi lần put dùng 1:B. Nếu chỉ nhìn cột khóa để suy ra giá trị luôn được tạo từ đúng đối tượng khóa ban đầu, có thể hiểu sai dữ liệu.

`reverseStillEqual=true` tiếp tục cho thấy đổi chiều không làm hai khóa ưu tiên bằng nhau trở thành khác nhau.

### 6.3. thenComparing làm rõ điều kiện trả 0

Comparator đầy đủ có logic:

```text
So priority
    ├─ khác nhau → dùng kết quả đó
    └─ bằng nhau → so code
                     ├─ khác nhau → phân biệt hai khóa
                     └─ bằng nhau → trả 0
```

Với record trong bài, `equals` xét đúng hai thành phần `priority` và `code`; `code` được kiểm tra khác `null` khi tạo. Comparator cũng chỉ trả 0 khi cả hai thành phần bằng nhau. Vì vậy thứ tự này nhất quán với `equals` trên miền khóa đó.

Map giữ đủ ba mapping `{1:A=alpha, 1:B=beta, 2:C=gamma}`. Thêm một đối tượng mới cũng biểu diễn 1:B vẫn cập nhật mapping 1:B, trả `beta`, giữ kích thước ba và không đụng đến 1:A.

### 6.4. Chọn tiêu chí phụ theo danh tính khóa, không theo nhu cầu làm khác bất kỳ

Không phải cứ thêm một tiêu chí bất kỳ là tốt. Nếu nối `System.identityHashCode` hoặc một số ngẫu nhiên chỉ để tránh 0, hai đối tượng biểu diễn cùng một khóa nghiệp vụ có thể bị tách ra. Khi đó tạo lại đối tượng khóa để tra cứu có thể không còn tìm đúng nhóm tương đương mong muốn.

Mã định danh cần ổn định và phù hợp với khái niệm cùng khóa. Trong ví dụ này, cặp `priority, code` là toàn bộ giá trị của khóa. Nếu trong mô hình khác chỉ `code` mới là danh tính, còn priority thay đổi độc lập, có thể cần dùng code làm khóa tra cứu và lưu priority ở phần dữ liệu, hoặc thiết kế một chỉ mục thứ tự riêng.

Điều cần quyết định là **những khác biệt nào phải làm hai khóa được lưu riêng**. Không nên lấy một thuộc tính chỉ phục vụ hiển thị rồi mặc định nó đủ làm danh tính cho `TreeMap`.

### 6.5. thenComparing không phải cách gom nhóm nhiều giá trị

Nếu yêu cầu thật sự là một độ ưu tiên chứa nhiều ticket, kiểu dữ liệu có thể cần là `TreeMap<Integer, List<Ticket>>`, với danh sách các ticket nằm ở giá trị. Khi đó hai ticket cùng mức không bị đặt trực tiếp làm hai khóa cạnh tranh.

Cách đó có ý nghĩa khác với `TreeMap<Ticket, Value>` có comparator đầy đủ. Map theo mức ưu tiên trả một nhóm; map theo khóa ghép trả một mapping riêng cho từng cặp thành phần. Chọn theo thao tác ứng dụng cần, thay vì cố sửa comparator để một cấu trúc đồng thời mang hai nghĩa.

## 7. Chương trình 4: BigDecimal và sự khác nhau giữa giá trị số với biểu diễn

### 7.1. Một ví dụ từ thư viện chuẩn

`BigDecimal` là trường hợp quan trọng vì không cần tự viết comparator sai vẫn có thể gặp so sánh bằng 0 nhưng `equals=false`. Với `new BigDecimal("2.0")` và `new BigDecimal("2.00")`: giá trị số bằng nhau, nhưng scale khác nhau.

`compareTo` so theo giá trị số, còn `equals` xét cả giá trị và scale. Chi tiết này được nêu tại [BigDecimal — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html). Không nên diễn giải nó là lỗi làm tròn trong ví dụ: hai chuỗi được tạo trực tiếp thành số thập phân, không đi qua `double`.

Lưu chương trình đầy đủ vào `TreeMapBigDecimalKeyDemo.java`:

```java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.TreeMap;

public class TreeMapBigDecimalKeyDemo {
    public static void main(String[] args) {
        BigDecimal first = new BigDecimal("2.0");
        BigDecimal second = new BigDecimal("2.00");
        System.out.println("equals=" + first.equals(second));
        System.out.println("compareTo=" + first.compareTo(second));
        System.out.println("scales=" + first.scale() + "," + second.scale());

        TreeMap<BigDecimal, String> numeric = new TreeMap<>();
        numeric.put(first, "first");
        System.out.println("numericOld=" + numeric.put(second, "second"));
        System.out.println("numeric=" + numeric);
        System.out.println("numericSize=" + numeric.size());
        System.out.println("numericLookup="
                + numeric.get(new BigDecimal("2.000")));

        Comparator<BigDecimal> numericThenScale =
                Comparator.<BigDecimal>naturalOrder()
                        .thenComparingInt(BigDecimal::scale);
        TreeMap<BigDecimal, String> representation =
                new TreeMap<>(numericThenScale);
        representation.put(first, "first");
        representation.put(second, "second");
        System.out.println("representation=" + representation);
        System.out.println("representationSize=" + representation.size());
        System.out.println("representationLookup="
                + representation.get(new BigDecimal("2.000")));
        System.out.println("normalizedEquals="
                + first.stripTrailingZeros().equals(second.stripTrailingZeros()));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TreeMapBigDecimalKeyDemo.java
java TreeMapBigDecimalKeyDemo
```

Stdout:

```text
equals=false
compareTo=0
scales=1,2
numericOld=first
numeric={2.0=second}
numericSize=1
numericLookup=second
representation={2.0=first, 2.00=second}
representationSize=2
representationLookup=null
normalizedEquals=true
```

### 7.2. Map theo thứ tự tự nhiên gộp cùng giá trị số

Map `numeric` không nhận comparator riêng nên dùng `BigDecimal.compareTo`. `compareTo=0` làm hai khóa rơi vào cùng một nhóm tương đương. Lần put thứ hai trả `first`, map còn một mapping với giá trị `second`.

`numericLookup=second` cho thấy một khóa thứ ba `2.000` cũng tìm cùng mapping. Khóa dùng lúc tra không cần có cùng scale nếu quy tắc thứ tự chỉ xét giá trị số.

Đây có thể phù hợp với một phần yêu cầu “các cách viết cùng giá trị số là cùng nhóm”, nhưng thứ tự tự nhiên của `BigDecimal` không nhất quán với `BigDecimal.equals`. Khi dùng như một `Map` tổng quát hoặc phối hợp với các cấu trúc dựa trên `equals`, phải nhận thức rõ sự khác nhau đó.

### 7.3. Map xét thêm scale giữ riêng các biểu diễn

Comparator `numericThenScale` so giá trị số trước, rồi so scale khi giá trị số bằng nhau. Vì thế 2.0 và 2.00 được giữ riêng, map có hai mapping. 2.000 mang scale thứ ba nên `representationLookup=null`, vì chưa có khóa đó.

Với `BigDecimal`, bằng nhau về giá trị số và cùng scale cũng tương ứng với điều kiện `equals`; comparator này tạo ra quan hệ 0 phù hợp với `equals`. Nhưng nó cũng **đổi ý nghĩa tra cứu**: truy vấn một giá trị số với scale khác không còn mặc nhiên trúng entry đang có.

Không nên thêm scale chỉ để thấy `size=2` rồi coi đó là lựa chọn luôn đúng. Nếu scale là một phần dữ liệu cần giữ riêng, cách này có lý do. Nếu ứng dụng coi mọi biểu diễn cùng số là một khóa, cần mô hình hóa quan hệ đó nhất quán.

### 7.4. Chuẩn hóa là một lựa chọn về miền dữ liệu

`stripTrailingZeros()` trong ví dụ cho hai giá trị có dạng chuẩn bằng nhau nên `normalizedEquals=true`. Nếu chọn chuẩn hóa làm chính sách khóa, phải áp dụng cùng quy tắc ở lúc thêm, tra và xóa; chỉ chuẩn hóa lúc put mà tra bằng kiểu khóa chưa chuẩn sẽ tạo một hợp đồng khó hiểu.

Không tự đồng nhất chuẩn hóa khóa với định dạng hiển thị hoặc quy tắc làm tròn tiền tệ. `stripTrailingZeros` có thể làm scale âm với một số giá trị; nó không có nghĩa mọi kết quả luôn có scale 0 hoặc luôn được in theo cùng một dạng thập phân. Chọn biểu diễn chuẩn theo yêu cầu dữ liệu và kiểm tra các trường hợp biên của nó.

Điều quan trọng của ví dụ là nhận ra **compareTo và equals có thể chủ ý khác nhau trong một kiểu chuẩn**. Việc các khóa thuộc một lớp thư viện không miễn cho người dùng `TreeMap` kiểm tra quan hệ trùng khóa.

## 8. Comparator phải là một quan hệ thứ tự hợp lệ

### 8.1. Ba ràng buộc cần hiểu

Có thể ký hiệu `c(a, b)` là kết quả so hai khóa a và b. Bên cạnh việc so sánh được mọi khóa trong miền cho phép, comparator cần các quan hệ nhất quán:

1. **Đảo dấu khi đổi chỗ:** dấu của `c(a,b)` phải đối nghịch dấu của `c(b,a)`. Nếu a đứng trước b, b phải đứng sau a. Với hai khóa tương đương, hai chiều cùng bằng 0.
2. **Bắc cầu:** nếu a đứng trước b và b đứng trước c, a cũng phải đứng trước c. Tương tự cho chiều đứng sau.
3. **Nhất quán khi bằng 0:** nếu a và b được xem là tương đương, khi so cả hai với một khóa z, chúng phải cho cùng dấu tương quan với z.

Quy tắc thứ ba giúp “cùng nhóm khóa” có ý nghĩa trong cả cây, chứ không chỉ đúng ở một cặp. Không thể cho a bằng b nhưng a đứng trước z còn b đứng sau z và vẫn mong cấu trúc tìm kiếm có một thứ tự hợp lệ.

Đối với các khóa được chấp nhận, `c(a,a)` phải bằng 0. Comparator không được trả 1 chỉ vì hai tham chiếu khác nhau hoặc vì muốn ép một khóa mới luôn được thêm. Những cách né kết quả 0 như vậy phá vỡ thứ tự thay vì tạo ra một map nhiều giá trị hợp lệ.

Ràng buộc thứ tự và tính nhất quán với `equals` là hai lớp khác nhau. Comparator chỉ theo độ dài chuỗi có thể là một quan hệ thứ tự hợp lệ trên các nhóm độ dài, nhưng không nhất quán với `String.equals`. Comparator dùng phép trừ bị tràn có thể hỏng ngay cả các ràng buộc thứ tự cơ bản.

### 8.2. Vì sao phép trừ số nguyên nguy hiểm?

Đoạn `(a, b) -> a - b` có vẻ ngắn gọn để so số tăng dần. Tuy nhiên, phép trừ `int` có thể tràn miền giá trị, làm dấu của kết quả không còn phản ánh quan hệ giữa hai toán hạng.

Điều này không chỉ làm một lần in bị sai. `TreeMap` dựa vào dấu để chọn đường tìm kiếm. Nếu dấu không nhất quán, cấu trúc không có quan hệ hợp lệ để duy trì và tra cứu. Không nên thử chèn một vài số nhỏ rồi kết luận comparator đã đúng cho toàn miền `int`.

Chương trình tiếp theo kiểm tra trực tiếp các tính chất của phép so sánh. Nó cố ý không đưa comparator sai vào map để tránh biến một hành vi của cấu trúc với thứ tự hỏng thành kết quả mà người học dựa vào.

### 8.3. Chương trình 5: quan sát lỗi tràn số ở comparator

Lưu vào `ComparatorContractDemo.java`:

```java
import java.util.Comparator;

public class ComparatorContractDemo {
    public static void main(String[] args) {
        Comparator<Integer> bad = (left, right) -> left - right;
        Comparator<Integer> good = Integer::compare;
        int minimum = Integer.MIN_VALUE;
        int maximum = Integer.MAX_VALUE;

        System.out.println("badMinVsZero=" + bad.compare(minimum, 0));
        System.out.println("badZeroVsMin=" + bad.compare(0, minimum));
        System.out.println("badOppositeSigns="
                + (Integer.signum(bad.compare(minimum, 0))
                == -Integer.signum(bad.compare(0, minimum))));
        System.out.println("goodOppositeSigns="
                + (Integer.signum(good.compare(minimum, 0))
                == -Integer.signum(good.compare(0, minimum))));

        boolean badPremise = bad.compare(minimum, 0) < 0
                && bad.compare(0, maximum) < 0;
        boolean badConclusion = bad.compare(minimum, maximum) < 0;
        boolean goodPremise = good.compare(minimum, 0) < 0
                && good.compare(0, maximum) < 0;
        boolean goodConclusion = good.compare(minimum, maximum) < 0;
        System.out.println("badTransitivityPremise=" + badPremise);
        System.out.println("badTransitivityConclusion=" + badConclusion);
        System.out.println("goodTransitivityPremise=" + goodPremise);
        System.out.println("goodTransitivityConclusion=" + goodConclusion);
        System.out.println("negatingMinStillNegative=" + (-minimum < 0));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ComparatorContractDemo.java
java ComparatorContractDemo
```

Stdout:

```text
badMinVsZero=-2147483648
badZeroVsMin=-2147483648
badOppositeSigns=false
goodOppositeSigns=true
badTransitivityPremise=true
badTransitivityConclusion=false
goodTransitivityPremise=true
goodTransitivityConclusion=true
negatingMinStillNegative=true
```

### 8.4. Đọc lỗi qua dấu và tính bắc cầu

`minimum - 0` và `0 - minimum` đều cho `-2147483648` sau phép tính `int`. Cả hai đều âm, nên comparator sai đang nói đồng thời minimum đứng trước 0 và 0 đứng trước minimum. `badOppositeSigns=false` chỉ ra lỗi đó.

Comparator sai còn cho minimum đứng trước 0 và 0 đứng trước maximum, nhưng lại không cho minimum đứng trước maximum. Vì vậy `badTransitivityPremise=true` trong khi `badTransitivityConclusion=false`.

`Integer.compare` xử lý so sánh mà không lấy hiệu dễ tràn nên cả hai kiểm tra tương ứng đúng. Với trường `long`, dùng `Long.compare` hoặc `Comparator.comparingLong`; không lấy hiệu `long` rồi ép về `int`.

`negatingMinStillNegative=true` cho thấy tự đổi dấu một kết quả so sánh bất kỳ cũng có bẫy tại `Integer.MIN_VALUE`. Để đảo một comparator đúng, dùng `reversed()`; để đảo thứ tự tự nhiên, dùng `Comparator.reverseOrder()`. Đừng sửa một comparator vốn sai bằng cách chỉ đổi dấu kết quả.

Các ví dụ số biên cung cấp phản ví dụ đủ để bác bỏ comparator sai. Ngược lại, một số mẫu kiểm tra hữu hạn chạy qua không chứng minh một comparator bất kỳ đúng cho mọi dữ liệu; cài đặt vẫn cần được xây từ các phép so sánh có hợp đồng phù hợp.

## 9. Giữ quy tắc so sánh ổn định trong suốt thời gian lưu khóa

### 9.1. Không sửa trường tham gia so sánh khi khóa đang nằm trong cây

Giả sử một đối tượng khóa đang được đặt trong cây dựa trên điểm ưu tiên bằng 1. Nếu sửa điểm đó thành 100 qua một tham chiếu khác, `TreeMap` không tự biết để tháo khóa ra và chèn lại theo vị trí mới. Cấu trúc hiện tại đã được tổ chức theo các kết quả so sánh trước đó.

Hệ quả có thể là thứ tự duyệt không còn phù hợp với dữ liệu đang thấy hoặc việc tra bằng một khóa tương đương không đi đến vị trí mong đợi. Không dựa vào một kết quả lỗi cụ thể vì nó còn phụ thuộc cấu trúc và chuỗi thao tác; vấn đề gốc là đã làm mất tính ổn định của khóa so sánh.

Cách thiết kế đơn giản là dùng khóa bất biến, chẳng hạn record chỉ gồm các thành phần bất biến như `int` và `String` trong ví dụ. Nếu thật sự cần thay thông tin dùng làm khóa, hãy loại mapping bằng khóa còn nguyên trạng, rồi thêm lại bằng khóa mới theo một quy trình đã xác định. Sửa khóa trước rồi mới cố dùng khóa đó để remove không bảo đảm còn tìm được mapping cũ.

Record không tự làm mọi đối tượng bên trong bất biến sâu. Một record chứa danh sách có thể thay đổi vẫn có thể gặp vấn đề nếu comparator đọc nội dung danh sách đó. Cần kiểm tra dữ liệu mà phép so sánh thực sự phụ thuộc.

### 9.2. Comparator cũng không được đổi ý giữa chừng

Comparator có thể hỏng tính ổn định ngay cả khi bản thân khóa không đổi. Ví dụ, nó đọc một cờ bên ngoài để lúc thì tăng dần, lúc thì giảm dần; hoặc nó phụ thuộc số ngẫu nhiên, thời gian hiện tại, hay một bảng giá trị có thể thay đổi.

Khi các kết quả so sánh thay đổi sau khi cây đã được xây, map không tự sắp lại tất cả entry. Muốn một chính sách thứ tự mới, hãy xây một cấu trúc mới với quy tắc ổn định đã chọn và chuyển dữ liệu một cách có chủ đích; không chỉ thay một biến được comparator cũ đọc.

Đặc biệt, sắp khóa theo giá trị lấy từ một map khác bằng một comparator phụ thuộc trạng thái thay đổi sẽ trộn tra cứu với thứ tự không ổn định. Nếu yêu cầu là xem kết quả theo giá trị tại một thời điểm, thường nên tạo dữ liệu hoặc chỉ mục riêng cho mục đích đó thay vì âm thầm làm danh tính khóa của `TreeMap` phụ thuộc giá trị đang cập nhật.

### 9.3. Giá trị V không phải khóa so sánh

Với `TreeMap<K,V>` bình thường, cập nhật giá trị của một khóa không làm map chuyển sang sắp theo giá trị mới. Comparator nhận các khóa kiểu K, không nhận hai entry để tùy ý xét V.

Một giá trị có thể trùng với giá trị của entry khác mà cả hai mapping vẫn tồn tại, miễn các khóa không tương đương theo comparator. Ngược lại, dùng hai giá trị rất khác nhau không làm một cặp khóa so sánh bằng 0 trở thành hai chỗ lưu.

## 10. Miền khóa hợp lệ, null và lỗi kiểu

### 10.1. Mọi khóa phải so sánh được với nhau

Trong map dùng thứ tự tự nhiên, việc hai lớp đều triển khai `Comparable` chưa đủ để bảo đảm các đối tượng của hai lớp đó so được với nhau. Ví dụ, `Integer` và `Long` không tự có một thứ tự tự nhiên chung chỉ vì cả hai là số.

Không nên bỏ generics hoặc khai báo một kiểu rất rộng rồi đưa vào nhiều loại khóa mà không có quy tắc so sánh chung. Map có thể ném `ClassCastException` khi cần so các khóa không tương thích. Lệnh `get` hoặc `containsKey` có tham số kiểu `Object` cũng không có nghĩa mọi đối tượng truyền vào đều có thể được so với khóa đang có.

Nếu dùng comparator ngoài, nó phải xử lý hợp lệ mọi cặp khóa thuộc miền mà API cho phép. Không có yêu cầu bắt buộc mọi khóa phải cùng một lớp cụ thể nếu comparator thật sự xây được một thứ tự chung, nhưng việc đó cần được thiết kế rõ ràng; ép kiểu tùy tiện không tạo ra thứ tự hợp lệ.

### 10.2. Hai chỗ có thể xuất hiện null trong cấu hình có ý nghĩa khác nhau

`new TreeMap<>(comparator)` với đối tượng comparator bằng `null` nghĩa là dùng thứ tự tự nhiên. Nhưng `Comparator.nullsFirst(null)` có ý nghĩa khác: nó coi mọi khóa khác `null` là bằng nhau, còn `null` đứng trước chúng.

Vì vậy, truyền `null` vào `nullsFirst` không phải cách yêu cầu tự động dùng `compareTo` cho các khóa còn lại. Nếu muốn chuỗi dùng thứ tự tự nhiên và nhận khóa `null`, cấu hình minh họa bên trong một phương thức là:

```java
Comparator<String> order = Comparator.nullsFirst(Comparator.naturalOrder());
TreeMap<String, Integer> map = new TreeMap<>(order);
```

Điểm này quan trọng trực tiếp với khóa trùng: một comparator xem mọi khóa khác `null` là tương đương sẽ chỉ có một nhóm khóa khác `null` để lưu mapping, dù người dùng đưa vào nhiều chuỗi khác nhau.

### 10.3. Nói rõ chính sách thay vì dựa vào ngoại lệ ngẫu nhiên

Nếu API không chấp nhận khóa `null`, hãy mô tả và kiểm tra quy tắc đó ở ranh giới thích hợp. Nếu nhận, chọn comparator có cách xử lý rõ ràng cho `null` và cho các khóa khác `null`. Không tạo comparator chỉ xử lý được một vài cặp rồi mong map tự bổ sung phần còn lại.

Khi `map.comparator()` khác `null`, comparator đó vẫn có thể chỉ ủy quyền cho thứ tự tự nhiên, chẳng hạn một map được tạo bằng `Comparator.naturalOrder()`. Vì thế getter cho biết cách cấu hình được giữ, không phải một phép suy luận rằng thứ tự tự nhiên và comparator ngoài luôn mô tả hai thứ tự khác nhau về kết quả.

## 11. Kiểm tra comparator và hành vi khóa trùng

### 11.1. Kiểm tra quan hệ, không chỉ một danh sách đã sắp

Một lần duyệt vài khóa có vẻ đúng thứ tự chưa chứng minh comparator tốt. Nên kiểm tra những nhóm dữ liệu làm lộ chính sách:

- Hai đối tượng khác tham chiếu nhưng cùng giá trị khóa: phải được nhận diện theo ý định nghiệp vụ.
- Hai khóa giống tiêu chí chính nhưng khác tiêu chí phụ: phải giữ riêng nếu đó là yêu cầu.
- Hai khóa khác nhau theo `equals` nhưng comparator đang trả 0: cần xác nhận đây có phải điều chủ ý, và hiểu giới hạn hợp đồng `Map` nếu giữ thiết kế đó.
- Số biên như `Integer.MIN_VALUE`, 0, `Integer.MAX_VALUE` để phát hiện lỗi dấu và tràn số.
- Các biến thể biểu diễn như `BigDecimal` khác scale hoặc chuỗi khác hoa thường nếu miền dữ liệu cho phép.
- Khóa `null` nếu API hỗ trợ, cùng trường hợp giá trị `null` để không nhầm kết quả get.

Ở mức hợp đồng, có thể kiểm tra các cặp và bộ ba mẫu: đổi chỗ phải đảo dấu; hai quan hệ trước liên tiếp phải kéo theo quan hệ trước bắc cầu; khóa có kết quả 0 phải có cùng dấu tương quan với các khóa thứ ba. Sau đó kiểm tra thêm sự tương ứng với `equals` nếu map cần tuân thủ hợp đồng `Map` tổng quát.

Tài liệu [Comparator — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html) và [Comparable — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html) là nguồn đối chiếu các điều kiện này. Không thay việc hiểu hợp đồng bằng cách quan sát riêng một chuỗi sắp xếp chạy không lỗi.

### 11.2. Kiểm tra thao tác map từ cùng một cặp khóa

Với cặp a, b cần được xem là trùng, hãy xác nhận:

1. Thêm a rồi thêm b không tăng số mapping ở lần sau.
2. Lần put b trả giá trị cũ và giá trị mới được tra thấy bằng cả hai khóa.
3. `containsKey` với một đối tượng mới thuộc cùng nhóm tương đương nhận ra mapping.
4. Remove bằng một khóa tương đương loại đúng mapping đó.

Với cặp cần giữ riêng, kiểm tra chiều ngược: cả hai mapping cùng tồn tại, tra từng khóa trả đúng giá trị riêng, remove một khóa không loại khóa còn lại. Chỉ kiểm tra `size()` chưa đủ nếu comparator hoặc logic tra cứu vẫn không đúng như mong muốn.

`put` trả `null` có thể là thêm mới hoặc thay giá trị cũ vốn đã `null`. Nếu phép kiểm tra cần phân biệt, hãy quan sát sự tồn tại hoặc kích thước trong ngữ cảnh đã được xác định, thay vì coi kết quả `null` là bằng chứng duy nhất.

### 11.3. Chi phí so sánh cũng là một phần chi phí tra cứu

`TreeMap` có chi phí logarit theo số mapping cho các thao tác cơ bản như `get`, `put`, `containsKey`, `remove`. Tuy nhiên, mỗi bước còn phải chạy phép so sánh. Comparator xử lý chuỗi dài, tính toán nặng hoặc tải dữ liệu từ bên ngoài sẽ làm chi phí thực tế tăng.

Giữ comparator ngắn, ổn định và không có tác dụng phụ giúp cả tính đúng lẫn khả năng dự đoán chi phí. Không làm thay đổi chính map trong lúc comparator đang được gọi để tìm vị trí; phép so sánh nên trả lời quan hệ giữa hai khóa thay vì thực hiện công việc cập nhật khác.

## 12. Lỗi thường gặp

| Nhầm lẫn | Hậu quả | Hướng xử lý |
|---|---|---|
| Comparator chỉ dùng để trình bày thứ tự | Không dự đoán được mapping bị cập nhật | Xét cả ý nghĩa `compare == 0` |
| `equals=false` thì TreeMap luôn giữ hai entry | Sai khi so sánh bằng 0 | Kiểm tra quy tắc đang có hiệu lực |
| Có comparator ngoài thì map vẫn gọi equals để phân biệt thêm | Hai khóa vẫn bị gộp ngoài ý muốn | Đưa tiêu chí phân biệt vào comparator hoặc đổi mô hình khóa |
| Chỉ so điểm/độ dài/độ ưu tiên | Các đối tượng khác cùng tiêu chí bị gộp | Bổ sung tiêu chí nhận diện cần thiết |
| Thêm số ngẫu nhiên để tránh trả 0 | Thứ tự không ổn định, không tra lại được như mong muốn | Dùng thành phần khóa ổn định |
| So số bằng phép trừ | Có thể đảo dấu, mất bắc cầu khi tràn số | Dùng hàm compare hoặc comparing theo kiểu số |
| Kiểm tra kết quả đúng bằng -1 hoặc 1 | Sai với comparator trả độ lớn khác | Kiểm tra `< 0`, `== 0`, `> 0` |
| Đảo comparator sẽ tách khóa đang trùng | Kết quả 0 vẫn là 0 | Sửa tiêu chí thay vì chỉ đổi chiều |
| BigDecimal cùng giá trị số luôn equals | Bỏ sót scale | Chọn rõ khóa theo số hay theo biểu diễn |
| Sửa trường khóa trong khi đang lưu | Vị trí cây và dữ liệu khóa không còn tương ứng | Dùng khóa ổn định, thay khóa theo quy trình có chủ đích |
| Cờ bên ngoài đổi thì TreeMap tự sắp lại | Comparator đổi nhưng cây không được xây lại | Dùng quy tắc ổn định cho mỗi cấu trúc |
| `comparator()==null` nghĩa là không có thứ tự | Hiểu sai thứ tự tự nhiên | Đọc đúng ý nghĩa getter |

## 13. Tự kiểm tra

### Câu hỏi

1. Hai khóa khác tham chiếu có bắt buộc tạo hai mapping trong `TreeMap` không?
2. Với comparator chỉ theo độ dài, thêm `cat → 1` rồi `dog → 2` tạo mấy mapping?
3. `TreeMap` có tự gọi `equals` để tách cat và dog sau khi comparator trả 0 không?
4. Vì sao `compare(a,b) == 1` không phải cách kiểm tra chung cho mọi comparator?
5. Comparator được truyền vào có bắt buộc gọi thêm `compareTo` khi trả 0 không?
6. `thenComparing` dùng tiêu chí sau khi nào?
7. `reversed()` có thay đổi nhóm khóa đang so sánh bằng 0 không?
8. `new BigDecimal("2.0")` và `new BigDecimal("2.00")` có đồng thời bằng nhau theo equals và compareTo không?
9. Một comparator đúng về tính bắc cầu đã chắc chắn nhất quán với equals chưa?
10. Vì sao sửa `hashCode` không giải quyết việc `TreeMap` đang gộp hai khóa?

### Đáp án

1. Không. Quan hệ tương đương theo thứ tự mới quyết định; hai đối tượng mới biểu diễn cùng khóa vẫn có thể cập nhật cùng mapping.
2. Một mapping có giá trị mới 2 vì hai độ dài đều ba.
3. Không. Muốn giữ riêng theo nội dung phải bổ sung tiêu chí hoặc chọn mô hình phù hợp.
4. API chỉ quy định số dương, không bắt buộc đúng 1. Dùng `> 0`.
5. Không. Quy tắc comparator là quy tắc có hiệu lực; tiêu chí bổ sung phải được thể hiện rõ.
6. Khi các tiêu chí trước coi hai khóa tương đương, tức cho kết quả 0.
7. Không; đảo thứ tự chỉ đổi quan hệ trước/sau, không tách nhóm tương đương.
8. compareTo trả 0 nhưng equals trả false vì scale khác.
9. Chưa. Comparator theo độ dài có thể là thứ tự hợp lệ nhưng gộp các chuỗi không equals.
10. TreeMap không dùng mã băm để nhận diện khóa trùng; cần xem phép so sánh và kết quả 0.

## 14. Thực hành với kết quả đối chiếu

### Bài 1: từ một tiêu chí sang hai tiêu chí

Tạo `TreeMap<String, Integer>` dùng `Comparator.comparingInt(String::length)`. Chèn `cat → 1`, `dog → 2`, `ox → 3`. Dự đoán số mapping, giá trị tra bằng `pig` và thứ tự các nhóm độ dài trước khi chạy.

Kết quả đối chiếu: có hai mapping, tương ứng độ dài hai và ba. `get("pig")` tìm nhóm độ dài ba và trả 2 dù chuỗi pig chưa từng được chèn. Đây là hệ quả trực tiếp của khóa tương đương theo độ dài.

Làm lại từ map rỗng với comparator độ dài nối thêm thứ tự tự nhiên của chuỗi. Khi đó cat và dog được giữ riêng, kích thước là ba, thứ tự khóa là `[ox, cat, dog]`, còn `get("pig")` trả `null` vì không có khóa pig. Hai lần thử phải dùng hai map mới để cùng có điểm xuất phát rõ ràng.

### Bài 2: kiểm tra một khóa ghép

Dùng record Ticket của chương trình 3. Thêm lần lượt 1:A, 1:B, rồi một đối tượng mới cũng là 1:A, với ba giá trị khác nhau. Với comparator đầy đủ, dự đoán kích thước cuối và mapping nào bị cập nhật.

Kết quả đối chiếu: có hai mapping; lần cuối chỉ cập nhật 1:A. Nếu map còn một mapping, comparator đang bỏ qua code. Nếu có ba mapping, quan hệ khóa không còn đúng với cặp thành phần đang biểu diễn.

### Bài 3: chọn rõ chính sách BigDecimal

Dùng hai map trong chương trình 4 rồi tra bằng 2.000. Giải thích vì sao map thứ nhất trả giá trị đã có, còn map thứ hai trả `null`. Sau đó tự quyết định liệu miền dữ liệu của một ví dụ ứng dụng cần phân biệt scale hay không.

Tiêu chí hoàn thành không phải luôn làm cho cả hai map có cùng `size()`. Cần giải thích được thế nào là cùng khóa, và bảo đảm mọi đường thêm/tra/xóa cùng tuân theo quyết định đó.

### Bài 4: tìm phản ví dụ thay vì chỉ dùng số nhỏ

Với comparator phép trừ, thử các cặp 1 và 2 trước, rồi các giá trị biên ở chương trình 5. Nêu rõ tính chất nào bị vi phạm. Sau đó thay bằng `Integer.compare` và đối chiếu lại các kiểm tra dấu, bắc cầu.

Không cần xây một TreeMap dùng comparator sai để làm bài này. Lỗi đã hiện diện trong quan hệ so sánh; sửa quan hệ đó là điều kiện trước khi dùng nó làm nền tảng cấu trúc tìm kiếm.

## 15. Môi trường chạy và tài liệu

Năm chương trình trong bài độc lập, dùng cú pháp và API tương thích Java 17. Chúng đã được biên dịch bằng `javac --release 17 -encoding UTF-8` và chạy trên JDK 24.0.2; stdout được đối chiếu từ các lần chạy đó. Mỗi lớp `public` được lưu trong tệp trùng tên, không cần thư viện ngoài.

- [TreeMap — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html): nguồn thứ tự, ý nghĩa khóa so sánh bằng nhau và yêu cầu nhất quán với equals.
- [Comparable — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html): thứ tự tự nhiên và hợp đồng của compareTo.
- [Comparator — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html): hợp đồng so sánh, nối tiêu chí, đảo chiều và xử lý null.
- [BigDecimal — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): so sánh giá trị số, equals theo giá trị và scale, chuẩn hóa số 0 tận cùng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tree-map-004">firstKey, lastKey, firstEntry và lastEntry</a>

<details>
<summary>Xem chi tiết</summary>

# TreeMap: firstKey, lastKey, firstEntry và lastEntry

Khi dữ liệu đã được tổ chức theo khóa, ứng dụng thường cần xem hai đầu của thứ tự: mốc thời gian sớm nhất và muộn nhất, mã nhỏ nhất và lớn nhất, hoặc bản ghi đứng đầu và cuối theo comparator.

`TreeMap` có bốn phương thức phục vụ việc quan sát đó. `firstKey` và `lastKey` trả riêng khóa; `firstEntry` và `lastEntry` trả một cặp khóa–giá trị. Điểm dễ nhầm nằm ở map rỗng, ý nghĩa của “đầu/cuối” khi thứ tự đảo và quan hệ giữa entry kết quả với dữ liệu gốc.

Bài dùng Java 17. Mục tiêu là chọn đúng phương thức cho mục đích đọc, xử lý kết quả vắng rõ ràng và hiểu giới hạn của ảnh chụp ánh xạ.

## 1. Bốn phương thức trả gì?

| Phương thức | Kết quả trên map không rỗng | Khi map rỗng | Xóa ánh xạ không? |
|---|---|---|---|
| firstKey() | Khóa đầu theo thứ tự của map | Ném NoSuchElementException | Không |
| lastKey() | Khóa cuối theo thứ tự của map | Ném NoSuchElementException | Không |
| firstEntry() | Cặp khóa–giá trị ở đầu | Trả null | Không |
| lastEntry() | Cặp khóa–giá trị ở cuối | Trả null | Không |

`firstKey` và `lastKey` thuộc hợp đồng SortedMap; các phương thức trả entry ở hai đầu được NavigableMap bổ sung. TreeMap triển khai NavigableMap nên cung cấp cả bốn phương thức. [Java 17: SortedMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SortedMap.html), [Java 17: NavigableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableMap.html)

Không phương thức nào trong bốn tên trên có ý nghĩa “lấy ra khỏi map”. Nếu mục đích là lấy và xóa, đó là nhóm `pollFirstEntry` hoặc `pollLastEntry`. Đừng dùng poll chỉ để xem dữ liệu rồi vô tình loại ánh xạ.

### 1.1. Tình huống đơn giản

Giả sử TreeMap dùng thứ tự tự nhiên của Integer và chứa:

~~~text
10 -> ten
20 -> twenty
30 -> thirty
~~~

Khi ấy:

~~~text
firstKey()   -> 10
lastKey()    -> 30
firstEntry() -> cặp 10=ten
lastEntry()  -> cặp 30=thirty
~~~

Các dòng `10=ten` và `30=thirty` là cách biểu diễn cặp trong ví dụ, không phải chuỗi khóa cần phân tích bằng cách tách dấu bằng. Trong mã, hãy lấy khóa bằng `entry.getKey()` và giá trị bằng `entry.getValue()`.

### 1.2. Khi chỉ có một ánh xạ

Khóa đầu và khóa cuối là cùng khóa. Hai entry biểu diễn cùng cặp dữ liệu.

Không cần suy ra hai lần gọi trả về cùng một đối tượng entry trong bộ nhớ. Điều ứng dụng quan tâm là khóa và giá trị theo hợp đồng, không phải danh tính đối tượng được tạo để mang kết quả.

Nếu cần so nội dung cặp, dùng phép so sánh nội dung phù hợp; không dùng `==` để yêu cầu hai kết quả entry phải cùng đối tượng.

## 2. “Đầu” và “cuối” theo thứ tự của map

### 2.1. Không phải lần chèn đầu và lần chèn cuối

TreeMap sắp xếp theo thứ tự tự nhiên của khóa hoặc comparator đã chọn. Chèn lần lượt `30, 10, 20` không làm firstKey trở thành 30. Với thứ tự số tăng, đầu là 10 và cuối là 30.

Thứ tự chèn là một yêu cầu khác. Nếu nghiệp vụ hỏi “bản ghi nào được thêm đầu tiên?”, không nên dùng TreeMap rồi diễn giải firstEntry như lịch sử chèn.

Tương tự, firstEntry không tìm giá trị nhỏ nhất. Nó trả ánh xạ có khóa đầu theo thứ tự. Nếu giá trị là độ ưu tiên, giá tiền hoặc thời gian nằm bên trong đối tượng, comparator trên khóa không tự chuyển sang xét các giá trị ấy.

### 2.2. Comparator đảo làm đảo hai đầu

Với comparator giảm dần và các khóa 10, 20, 30:

~~~text
thứ tự map: 30 -> 20 -> 10
firstKey: 30
lastKey:  10
~~~

Cụm “khóa thấp nhất” trong hợp đồng thứ tự phải được đọc theo comparator của map. Nó không luôn có nghĩa số nhỏ nhất theo phép so số tăng dần quen thuộc.

Đây là lý do nên đặt tên kết quả theo nghiệp vụ, chẳng hạn `earliestByTime` chỉ khi comparator thực sự đi theo thời gian từ sớm đến muộn. Nếu comparator ưu tiên điểm cao trước, firstEntry là đầu quy tắc xếp hạng ấy.

### 2.3. Comparator nhiều tiêu chí

Giả sử khóa có điểm và mã định danh. Nếu comparator xét điểm giảm dần rồi mã tăng dần, phần tử đầu là điểm cao nhất; khi bằng điểm, mã nhỏ hơn đứng trước.

Muốn hiểu firstEntry trong tình huống này, phải đọc toàn bộ comparator. Đừng chỉ nhìn kiểu khóa hoặc tên phương thức.

Comparator cũng phải phân biệt các khóa theo ý định của dữ liệu. Nếu hai khóa so sánh bằng 0, TreeMap coi chúng thuộc cùng vị trí khóa; không có hai ánh xạ riêng để first và last lựa chọn giữa chúng. Giữ các thành phần dùng so sánh ổn định là điều kiện để thứ tự cây tiếp tục phản ánh đúng dữ liệu. [Java 17: TreeMap và quy tắc thứ tự khóa](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)

## 3. Chọn trả khóa hay trả cả entry?

### 3.1. Chỉ cần khóa

Nếu đang có SortedMap không rỗng và chỉ cần mốc đầu hoặc mốc cuối, firstKey/lastKey diễn đạt trực tiếp mục đích.

Đoạn minh họa một phần:

~~~java
SortedMap<Integer, String> labels = new TreeMap<>();
labels.put(20, "middle");
labels.put(10, "start");

Integer earliestKey = labels.firstKey();
~~~

Ví dụ biết chắc labels có dữ liệu do chính các dòng trước đó vừa tạo trong cùng luồng. Đây khác với giả định một map dùng chung “chắc vẫn còn dữ liệu” sau một phép kiểm tra tách rời.

Không cần sao chép toàn bộ keySet vào List chỉ để lấy vị trí 0 hoặc vị trí cuối. Các phương thức đã mô tả đúng thao tác trên sorted map.

### 3.2. Cần cả khóa và giá trị

Dùng firstEntry/lastEntry để nhận cặp:

~~~java
NavigableMap<Integer, String> orderedLabels = new TreeMap<>();
orderedLabels.put(10, "start");
Map.Entry<Integer, String> first = orderedLabels.firstEntry();
if (first != null) {
    System.out.println(first.getKey() + ": " + first.getValue());
}
~~~

Đoạn minh họa khai báo `orderedLabels` bằng NavigableMap để có phương thức firstEntry. Nếu biến chỉ được khai báo SortedMap trong Java 17 thì không gọi trực tiếp firstEntry qua kiểu đó.

Khai báo kiểu biến là phần của thiết kế. Hàm cần phương thức entry ở hai đầu nên nhận NavigableMap phù hợp, thay vì nhận Map rồi ép kiểu mù.

### 3.3. Tránh tách lấy khóa rồi tra giá trị nếu cần một cặp kết quả

Mẫu sau có hai thao tác riêng:

~~~java
Integer key = map.firstKey();
String value = map.get(key);
~~~

Trong một map ổn định trên một luồng, nó có thể cho kết quả mong muốn. Nhưng nếu cần cả khóa lẫn giá trị, firstEntry thể hiện mục đích rõ hơn và không cần lượt tra cứu riêng bằng khóa.

Nếu có sửa đổi khác xen giữa hai lời gọi, key và value có thể không còn phản ánh cùng trạng thái dữ liệu. Một dòng thời gian khái niệm:

~~~text
T1: lấy firstKey, nhận 10
T2: một thao tác khác xóa khóa 10
T3: get(10), không còn ánh xạ tương ứng
~~~

Dùng một phương thức trả cặp loại bỏ nhu cầu tự ghép cặp bằng hai lần đọc đó. Nó không tự biến TreeMap thành cấu trúc an toàn cho nhiều luồng; phần truy cập đồng thời vẫn cần chính sách riêng.

## 4. Map rỗng: ngoại lệ và null là hai hợp đồng khác nhau

### 4.1. FirstKey và lastKey không dùng null làm dấu báo rỗng

Trên map rỗng, hai phương thức trả khóa ném `NoSuchElementException`. Không viết kiểm tra theo mẫu:

~~~java
Integer first = map.firstKey();
if (first == null) {
    // Không phải cách kiểm tra map rỗng bằng firstKey.
}
~~~

Đây là đoạn minh họa một giả định sai. Nếu map rỗng, chương trình không đi tới phép so sánh vì ngoại lệ đã được ném.

Nếu một nghiệp vụ yêu cầu map không được rỗng, ngoại lệ có thể giúp lộ việc điều kiện đó bị vi phạm. Nếu trạng thái rỗng là bình thường, cần thiết kế nhánh xử lý rỗng thay vì coi đó là lỗi bất ngờ.

### 4.2. FirstEntry và lastEntry cho phép rỗng là một kết quả bình thường

Mẫu đọc một đầu và xử lý vắng:

~~~java
Map.Entry<Integer, String> first = map.firstEntry();
if (first == null) {
    System.out.println("No mappings");
} else {
    System.out.println(first.getKey() + "=" + first.getValue());
}
~~~

Không gọi getKey hoặc getValue trước khi đã xử lý khả năng entry bằng null.

Nếu tầng ứng dụng dùng Optional để biểu diễn kết quả vắng, có thể bọc chính entry. Không tự bọc riêng giá trị rồi đánh đồng “giá trị null” với “không có ánh xạ”.

### 4.3. IsEmpty rồi firstKey không phải một thao tác nguyên tử

Trên một luồng, với dữ liệu không bị sửa xen giữa, kiểm tra isEmpty rồi firstKey có thể dùng được. Nhưng hai lời gọi vẫn là hai bước.

Nếu map được chia sẻ và luồng khác có quyền xóa, nó có thể trở thành rỗng sau lần kiểm tra. Không dùng isEmpty như biện pháp thay thế đồng bộ hóa.

Tương tự, gọi firstEntry rồi lastEntry không tự tạo một ảnh chụp nhất quán của cả hai đầu. Nếu cần cả hai ở cùng một trạng thái được bảo vệ, phải tổ chức các lần đọc trong cùng chính sách bảo vệ dữ liệu.

## 5. Entry kết quả của TreeMap là ảnh chụp cặp ánh xạ

### 5.1. Không bám theo thay đổi giá trị về sau

Với TreeMap:

~~~text
lúc đầu:          10 -> ten
captured = firstEntry()

cập nhật map:    10 -> TEN
captured vẫn:    10 -> ten
firstEntry mới:  10 -> TEN
~~~

Entry do firstEntry/lastEntry của TreeMap trả về biểu diễn ánh xạ tại thời điểm kết quả được tạo. Thay một giá trị trong map không biến entry cũ thành cặp mang giá trị mới.

Nếu khóa bị xóa khỏi map, cặp đã chụp vẫn là dữ liệu kết quả cũ; nó không tự biến thành null. Nếu thêm khóa đứng trước, entry đã chụp cũng không tự trở thành đầu mới.

Do đó, đặt tên biến `capturedFirst` hoặc `firstAtReadTime` có thể diễn đạt ý nghĩa tốt hơn tên khiến người đọc tưởng đó là liên kết sống tới đầu hiện tại.

### 5.2. Không dùng setValue để ghi ngược

Các entry điều hướng này của TreeMap không hỗ trợ `setValue`. Gọi nó ném `UnsupportedOperationException`.

Nếu muốn sửa ánh xạ hiện tại, dùng thao tác map phù hợp, chẳng hạn put với khóa đã chọn, đồng thời xem xét việc trạng thái có thể đã thay đổi trước lúc sửa.

Không khái quát thành “mọi Map.Entry đều bất biến”. Entry thu được từ iterator của entrySet có hợp đồng riêng. Chủ đề ở đây là entry được trả trực tiếp bởi firstEntry/lastEntry của TreeMap.

### 5.3. Ảnh chụp cặp không phải sao chép sâu

Giả sử giá trị là một ArrayList:

~~~text
map:   1 -> đối tượng List L
entry: 1 -> cùng đối tượng List L
~~~

Nếu sửa nội dung của L, cả hai tham chiếu đều nhìn thấy nội dung đã sửa. Entry không cho gán lại trường value bằng setValue, nhưng điều đó không làm đối tượng List trở thành bất biến.

Nếu thay ánh xạ trong map để trỏ tới một List mới, entry cũ vẫn trỏ List cũ. Cần tách hai hành động:

- Thay giá trị được tham chiếu bởi ánh xạ.
- Thay nội dung bên trong chính đối tượng giá trị.

Khái niệm cặp không sửa được của `SimpleImmutableEntry` cũng phân biệt việc không hỗ trợ thay value với việc các đối tượng được tham chiếu có tự bất biến hay không. Không cần dựa vào tên lớp thực tế của entry TreeMap để sử dụng hợp đồng này. [Java 17: AbstractMap.SimpleImmutableEntry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.SimpleImmutableEntry.html)

Nếu cần dữ liệu báo cáo tách hoàn toàn khỏi thay đổi sau đó, phải chọn cách sao chép hoặc kiểu dữ liệu bất biến phù hợp với đối tượng giá trị, không chỉ giữ entry.

## 6. Null ở ba vị trí mang ba ý nghĩa

| Vị trí có null | Ý nghĩa cần xét |
|---|---|
| Biến entry bằng null | Với firstEntry/lastEntry của TreeMap, không có ánh xạ vì map hoặc view đang rỗng |
| entry.getValue() bằng null | Có thể là một ánh xạ đang tồn tại với giá trị null |
| Khóa kết quả bằng null | Có thể là khóa null hợp lệ nếu comparator cho phép |

### 6.1. Giá trị null không làm entry biến mất

Một TreeMap chứa `1 -> null` vẫn có firstEntry khác null. Đọc entry.getValue nhận null không chứng minh map rỗng.

Mẫu kiểm tra đúng là xét đối tượng entry trước, rồi xử lý giá trị theo chính sách dữ liệu.

### 6.2. Khóa null có thể hợp lệ với comparator riêng

TreeMap dùng thứ tự tự nhiên không cho phép khóa null. Một comparator chủ động xử lý null, như `Comparator.nullsFirst(...)`, có thể cho phép nó.

Khi map ấy chứa khóa null, firstKey có thể trả null trong khi map không rỗng. Điều này không mâu thuẫn với hợp đồng rỗng: map rỗng vẫn ném ngoại lệ; kết quả null trong trường hợp này chính là một khóa hợp lệ.

Vì vậy, không dùng `firstKey() == null` như phép kiểm tra map rỗng. Nếu tự động mở hộp một khóa Integer có thể null sang int, cũng phải tính đến việc mở hộp null gây lỗi; đó khác với lỗi không có phần tử.

### 6.3. Không phân tích chuỗi in để đoán trạng thái

Dòng `null=unknown` có thể là cách in một entry có khóa null, không phải một entry vắng. Dùng entry và các phương thức truy cập của nó để xác định trạng thái, không dùng tiền tố chuỗi log.

Chương trình đầy đủ kiểm tra cả null ở giá trị và null ở khóa để hai trường hợp này không bị gộp với entry bằng null.

## 7. Hai đầu của view có phạm vi riêng

### 7.1. View khoảng chỉ xét khóa trong vùng

Nếu map gốc có `10, 20, 30` và view là `[15, 30)`, view chỉ chứa 20. FirstKey và lastKey của view đều là 20, dù hai đầu của gốc là 10 và 30.

“Đầu” và “cuối” luôn xét trên đối tượng map hoặc view mà lời gọi được thực hiện. Không lấy hai đầu của gốc rồi tự cho đó cũng là hai đầu của mọi subMap.

### 7.2. View rỗng dù gốc không rỗng

View `[11, 19]` trên dữ liệu trên không có ánh xạ nào. FirstEntry/lastEntry của view trả null, còn firstKey/lastKey của view ném NoSuchElementException.

Điều này quan trọng khi xử lý một khoảng thời gian không có sự kiện: map tổng thể có thể rất lớn, nhưng khoảng đang hỏi vẫn rỗng. Kiểm tra size của gốc không thay thế xử lý kết quả rỗng của view.

View là góc nhìn dùng chung dữ liệu. Khi dữ liệu gốc trong phạm vi thay đổi, các lần đọc hai đầu về sau phản ánh phạm vi hiện tại. Entry đã chụp từ lần đọc trước vẫn là kết quả cũ.

### 7.3. View đảo đổi chỗ hai đầu

Trên descendingMap của cùng dữ liệu tăng dần:

~~~text
gốc:       10, 20, 30
view đảo:  30, 20, 10

firstEntry của view đảo: 30=thirty
lastEntry của view đảo:  10=ten
~~~

Đó là hai đầu theo comparator đảo của view. Không có yêu cầu phải sao chép rồi sắp lại để quan sát chiều ngược.

## 8. Dùng hai đầu trong một quyết định nghiệp vụ

### 8.1. Chọn phương thức từ kết quả cần dùng

| Nhu cầu | Lựa chọn phù hợp |
|---|---|
| Chỉ cần khóa đầu, dữ liệu bắt buộc không rỗng | firstKey |
| Chỉ cần khóa cuối, dữ liệu bắt buộc không rỗng | lastKey |
| Cần cặp đầu, rỗng là kết quả bình thường | firstEntry và xử lý null |
| Cần cặp cuối, rỗng là kết quả bình thường | lastEntry và xử lý null |
| Cần lấy đồng thời xóa | Cân nhắc phương thức poll tương ứng |
| Cần phần tử gần một mốc | Đó là nhóm floor/ceiling/lower/higher, không phải hai đầu toàn map |

Không dùng lastEntry để hỏi “bản ghi mới nhất” nếu khóa và comparator không thực sự diễn đạt độ mới. Ví dụ, map sắp theo mã sản phẩm không biến mã lớn nhất thành sản phẩm được cập nhật gần nhất.

### 8.2. Kiểm soát ranh giới giữa đọc và hành động

Một entry có thể đủ để in báo cáo ở trạng thái đã đọc. Nhưng nếu hành động tiếp theo là sửa hoặc xóa chính ánh xạ ấy, cần đánh giá trạng thái tại thời điểm hành động.

Trong hệ thống nhiều luồng, đọc firstEntry rồi sửa theo khóa không tự trở thành thao tác nguyên tử. Entry ảnh chụp mang dữ liệu đã đọc, không cấp quyền giữ độc quyền ánh xạ.

TreeMap không tự đồng bộ hóa. Khi dùng một wrapper đồng bộ, mọi truy cập phải tuân thủ cùng cơ chế. Nếu cần đọc cả hai đầu dưới một trạng thái được bảo vệ, có thể giữ khóa trong suốt hai lần đọc.

Đoạn minh họa một phần, với các import phù hợp:

~~~java
NavigableMap<Integer, String> shared =
        Collections.synchronizedNavigableMap(new TreeMap<>());

Map.Entry<Integer, String> first;
Map.Entry<Integer, String> last;
synchronized (shared) {
    first = shared.firstEntry();
    last = shared.lastEntry();
}
~~~

Điều kiện là các truy cập khác cũng tuân thủ wrapper và cùng quy tắc bảo vệ, không đi vòng qua một tham chiếu gốc không được khóa. Việc đọc xong không giữ cho map bất biến mãi về sau. Nếu giá trị có thể thay đổi bên trong, cũng cần chính sách riêng cho các đối tượng đó. [Java 17: Collections.synchronizedNavigableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedNavigableMap(java.util.NavigableMap))

## 9. Chương trình Java đầy đủ

Lưu mã sau vào `TreeMapEndpointsDemo.java`. Chương trình dùng các map riêng để tách từng câu hỏi: thứ tự tự nhiên, rỗng, một ánh xạ, comparator đảo, ảnh chụp cặp, giá trị có thể thay đổi, null và view.

Các phép kiểm tra khẳng định những điều mà phần giải thích dựa vào. Không có phép đo thời gian hoặc giả định về danh tính đối tượng entry.

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.function.Supplier;

public class TreeMapEndpointsDemo {
    private static TreeMap<Integer, String> sample() {
        TreeMap<Integer, String> map = new TreeMap<>();
        map.put(30, "thirty");
        map.put(10, "ten");
        map.put(20, "twenty");
        return map;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Unexpected result");
        }
    }

    private static String emptyKeyResult(Supplier<?> operation) {
        try {
            operation.get();
            return "returned";
        } catch (NoSuchElementException exception) {
            return exception.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        TreeMap<Integer, String> natural = sample();
        require(natural.firstKey() == 10 && natural.lastKey() == 30);
        System.out.println("natural/firstKey=" + natural.firstKey()
                + ", lastKey=" + natural.lastKey());
        System.out.println("natural/firstEntry=" + natural.firstEntry()
                + ", lastEntry=" + natural.lastEntry());
        System.out.println("natural/afterReads=" + natural);

        TreeMap<Integer, String> empty = new TreeMap<>();
        System.out.println("empty/firstKey=" + emptyKeyResult(empty::firstKey));
        System.out.println("empty/lastKey=" + emptyKeyResult(empty::lastKey));
        System.out.println("empty/firstEntry=" + empty.firstEntry()
                + ", lastEntry=" + empty.lastEntry());

        TreeMap<Integer, String> single = new TreeMap<>();
        single.put(7, "only");
        require(single.firstKey().equals(single.lastKey()));
        System.out.println("single/firstEntry=" + single.firstEntry()
                + ", lastEntry=" + single.lastEntry());

        TreeMap<Integer, String> reversed =
                new TreeMap<>(Comparator.reverseOrder());
        reversed.putAll(sample());
        require(reversed.firstKey() == 30 && reversed.lastKey() == 10);
        System.out.println("reverse/firstKey=" + reversed.firstKey()
                + ", lastKey=" + reversed.lastKey());
        System.out.println("reverse/firstEntry=" + reversed.firstEntry()
                + ", lastEntry=" + reversed.lastEntry());

        TreeMap<Integer, String> changing = sample();
        Map.Entry<Integer, String> captured = changing.firstEntry();
        changing.put(10, "TEN");
        require(captured.getValue().equals("ten"));
        System.out.println("snapshot/captured=" + captured
                + ", current=" + changing.firstEntry());
        changing.remove(10);
        require(captured.getKey() == 10 && changing.firstKey() == 20);
        System.out.println("snapshot/afterRemove=" + captured
                + ", newFirst=" + changing.firstEntry());
        boolean rejected = false;
        try {
            captured.setValue("forbidden");
        } catch (UnsupportedOperationException exception) {
            rejected = true;
        }
        require(rejected);
        System.out.println("snapshot/setValueRejected=" + rejected);

        TreeMap<Integer, List<String>> mutable = new TreeMap<>();
        mutable.put(1, new ArrayList<>(List.of("draft")));
        Map.Entry<Integer, List<String>> shallow = mutable.firstEntry();
        mutable.get(1).add("reviewed");
        require(shallow.getValue().equals(List.of("draft", "reviewed")));
        System.out.println("shallow/afterObjectMutation=" + shallow.getValue());
        mutable.put(1, new ArrayList<>(List.of("replacement")));
        System.out.println("shallow/oldValue=" + shallow.getValue()
                + ", currentValue=" + mutable.firstEntry().getValue());

        TreeMap<Integer, String> nullValue = new TreeMap<>();
        nullValue.put(1, null);
        Map.Entry<Integer, String> present = nullValue.firstEntry();
        require(present != null && present.getValue() == null);
        System.out.println("nullValue/entryPresent=" + (present != null)
                + ", key=" + present.getKey() + ", value=" + present.getValue());

        TreeMap<String, String> nullKey =
                new TreeMap<>(Comparator.nullsFirst(
                        Comparator.<String>naturalOrder()));
        nullKey.put("A", "alpha");
        nullKey.put(null, "unknown");
        require(!nullKey.isEmpty() && nullKey.firstKey() == null);
        System.out.println("nullKey/empty=" + nullKey.isEmpty()
                + ", firstKey=" + nullKey.firstKey());
        System.out.println("nullKey/firstEntry=" + nullKey.firstEntry());

        TreeMap<Integer, String> parent = sample();
        NavigableMap<Integer, String> window =
                parent.subMap(15, true, 30, false);
        require(window.firstKey() == 20 && window.lastKey() == 20);
        System.out.println("view/firstKey=" + window.firstKey()
                + ", lastKey=" + window.lastKey());
        NavigableMap<Integer, String> emptyWindow =
                parent.subMap(11, true, 19, true);
        require(parent.size() == 3 && emptyWindow.isEmpty());
        System.out.println("view/parentSize=" + parent.size()
                + ", emptyWindowFirst=" + emptyWindow.firstEntry());
        System.out.println("view/emptyWindowFirstKey="
                + emptyKeyResult(emptyWindow::firstKey));

        NavigableMap<Integer, String> backward = parent.descendingMap();
        require(backward.firstKey().equals(parent.lastKey()));
        System.out.println("descending/firstEntry=" + backward.firstEntry()
                + ", lastEntry=" + backward.lastEntry());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 TreeMapEndpointsDemo.java
java TreeMapEndpointsDemo
~~~

Stdout của lần chạy kiểm chứng:

~~~text
natural/firstKey=10, lastKey=30
natural/firstEntry=10=ten, lastEntry=30=thirty
natural/afterReads={10=ten, 20=twenty, 30=thirty}
empty/firstKey=NoSuchElementException
empty/lastKey=NoSuchElementException
empty/firstEntry=null, lastEntry=null
single/firstEntry=7=only, lastEntry=7=only
reverse/firstKey=30, lastKey=10
reverse/firstEntry=30=thirty, lastEntry=10=ten
snapshot/captured=10=ten, current=10=TEN
snapshot/afterRemove=10=ten, newFirst=20=twenty
snapshot/setValueRejected=true
shallow/afterObjectMutation=[draft, reviewed]
shallow/oldValue=[draft, reviewed], currentValue=[replacement]
nullValue/entryPresent=true, key=1, value=null
nullKey/empty=false, firstKey=null
nullKey/firstEntry=null=unknown
view/firstKey=20, lastKey=20
view/parentSize=3, emptyWindowFirst=null
view/emptyWindowFirstKey=NoSuchElementException
descending/firstEntry=30=thirty, lastEntry=10=ten
~~~

Mã đã biên dịch với `--release 17` và chạy thành công. Tùy chọn release xác định mức API và mã đích tương thích; nó không khẳng định JVM thực thi là đúng bản JDK 17.

## 10. Giải thích các nhóm kết quả

### 10.1. Natural, empty và single

Ba dòng natural cho thấy các lần đọc hai đầu không xóa dữ liệu. Map vẫn chứa 10, 20, 30 sau các lời gọi.

Các dòng empty đối chiếu hai cách báo rỗng: phương thức trả khóa ném NoSuchElementException, phương thức trả entry cho null.

Ở single, cả đầu và cuối biểu diễn `7=only`. Chương trình không so sánh hai entry bằng `==` vì không cần cam kết chúng là cùng đối tượng.

### 10.2. Reverse và descending

Map tạo bằng comparator giảm dần có firstKey bằng 30 và lastKey bằng 10. View descending của map tăng dần cũng có hai đầu tương ứng.

Cùng chữ first nhưng kết quả phụ thuộc quy tắc thứ tự. Đây là kết quả cần kiểm tra khi một báo cáo “đầu danh sách” có vẻ ngược mong đợi.

### 10.3. Snapshot

Cặp captured vẫn là `10=ten` sau khi map thành `10=TEN`. Sau khi xóa 10, cặp cũ vẫn đọc được như dữ liệu kết quả đã chụp, trong khi đầu hiện tại của map là `20=twenty`.

SetValue bị từ chối. Tình huống này minh họa entry điều hướng không phải một tay cầm dùng để sửa trực tiếp vị trí đầu hiện tại của cây.

### 10.4. Shallow

Sau khi thêm phần tử `reviewed` vào cùng List, entry cũ thấy `[draft, reviewed]`. Sau khi map được gán một List mới, entry cũ vẫn giữ List cũ, còn entry mới cho `[replacement]`.

Như vậy, ảnh chụp cặp tách việc thay tham chiếu ánh xạ, nhưng không tự sao chép nội dung List.

### 10.5. Null và view

Entry của map có giá trị null vẫn tồn tại. Map cho phép khóa null không rỗng dù firstKey trả null. Các phép kiểm tra dùng đối tượng entry và isEmpty rõ ràng, không suy từ cách in.

View có một khóa cho cùng khóa ở hai đầu. View rỗng trả firstEntry null dù gốc có ba ánh xạ. Lỗi rỗng thuộc phạm vi đang được hỏi.

## 11. Các lỗi dễ gặp

| Lỗi | Điều cần sửa trong cách nghĩ |
|---|---|
| First là phần tử được chèn đầu tiên | First theo thứ tự khóa hiện tại |
| Last là giá trị lớn nhất | Last chọn theo khóa và comparator |
| FirstKey trả null khi map rỗng | Map rỗng ném NoSuchElementException |
| Entry.getValue bằng null nghĩa là không có entry | Kiểm tra chính entry trước |
| FirstKey bằng null nghĩa là map rỗng | Comparator có thể cho phép khóa null |
| Entry trả về luôn cập nhật theo map | Entry điều hướng của TreeMap là ảnh chụp cặp |
| SetValue trên firstEntry sẽ sửa đầu cây | Thao tác không được hỗ trợ |
| Ảnh chụp entry là sao chép sâu | Khóa và giá trị có thể vẫn là các tham chiếu chung |
| Map gốc không rỗng nên mọi view đều có hai đầu | Mỗi view có phạm vi riêng và có thể rỗng |
| Hai lần đọc hai đầu là một lần chụp toàn map | Cần chính sách bảo vệ nếu có sửa đổi cạnh tranh |
| Bốn phương thức đọc sẽ lấy phần tử ra | Chúng không xóa ánh xạ |
| Chỉ cần đổi kiểu biến thành Map để bỏ thứ tự | Thứ tự của đối tượng TreeMap không đổi theo kiểu tham chiếu |

## 12. Thực hành và lời giải

### Bài 1. Nhận diện đúng hai đầu

TreeMap tự nhiên nhận các khóa 80, 10, 50 theo thứ tự chèn đó. FirstKey và lastKey là gì? Nếu dùng comparator giảm dần thì sao?

**Lời giải:** Tự nhiên cho 10 và 80. Giảm dần cho 80 và 10. Thứ tự chèn không quyết định hai đầu; comparator hoặc thứ tự tự nhiên quyết định.

### Bài 2. Chọn hợp đồng rỗng

Một màn hình có thể chưa có bản ghi, cần hiển thị cặp cuối nếu có. Chọn lastKey rồi get hay lastEntry? Cần xử lý điều gì trước khi đọc giá trị?

**Lời giải:** LastEntry diễn đạt trực tiếp nhu cầu lấy cặp và cho null khi rỗng. Phải kiểm tra entry khác null trước khi gọi getKey/getValue. Giá trị bên trong entry cũng có thể null theo chính sách dữ liệu.

### Bài 3. Cặp đã chụp

Map có 1=old và 2=other. Lấy firstEntry rồi put(1, "new"), sau đó xóa 1. Entry đã lấy có tự biến thành 2=other không?

**Lời giải:** Không. Entry điều hướng là ảnh chụp cặp 1=old. Lần đọc firstEntry mới sau khi xóa cho cặp 2=other. Kết quả cũ không tự theo dõi đầu hiện tại của map.

### Bài 4. Không sửa được entry nhưng List vẫn đổi

Giá trị của firstEntry là một ArrayList. SetValue không được hỗ trợ, nhưng thêm phần tử qua entry.getValue vẫn có thể làm thay đổi List. Có mâu thuẫn không?

**Lời giải:** Không. Không hỗ trợ thay trường value của cặp không làm đối tượng List bất biến. Entry giữ tham chiếu đến List; thao tác trên chính List vẫn phụ thuộc hợp đồng List và chính sách dữ liệu.

### Bài 5. View rỗng

Gốc chứa 10, 20, 30. View là subMap(11, true, 19, true). FirstEntry của view và firstKey của view có hành vi gì?

**Lời giải:** View không có ánh xạ. FirstEntry trả null; firstKey ném NoSuchElementException. Gốc không rỗng không thay đổi hợp đồng rỗng của view.

### Bài 6. Khóa null

Một TreeMap dùng comparator nullsFirst và có khóa null cùng khóa A. FirstKey trả null. Có thể dùng kết quả đó để thông báo map rỗng không?

**Lời giải:** Không. Đó là khóa null hợp lệ trong thứ tự đã chọn. Có thể đọc firstEntry rồi kiểm tra đối tượng entry hoặc kiểm tra isEmpty trong bối cảnh truy cập hợp lệ. Map rỗng gọi firstKey sẽ ném ngoại lệ.

### Bài 7. Hai bước đọc và thay đổi xen giữa

Mã lấy firstKey rồi get giá trị bằng khóa đó trên map được chia sẻ. Giữa hai bước, ánh xạ bị xóa. Vì sao một lần get trả null không thể được hiểu đơn giản là “map ban đầu không có dữ liệu”?

**Lời giải:** Hai bước đã quan sát các thời điểm khác nhau. Khóa tồn tại tại lần đọc đầu nhưng có thể không còn ở lần sau; ngoài ra giá trị null cũng có thể là dữ liệu hợp lệ. Nếu cần cặp, dùng phương thức trả entry phù hợp; nếu cần tính nhất quán đồng thời hoặc hành động tiếp theo, thiết kế chính sách bảo vệ trọn phạm vi cần thiết.

## 13. Trước khi chọn một trong bốn phương thức

Hãy xác định thứ tự đang dùng, cần khóa hay cả cặp, trạng thái rỗng có bình thường hay không và kết quả sẽ được dùng như dữ liệu đã đọc hay căn cứ cho một thao tác sửa tiếp theo.

Bốn phương thức đều quan sát hai đầu. Khác biệt về kiểu kết quả và cách báo rỗng giúp ứng dụng biểu diễn đúng nhu cầu; hiểu ảnh chụp cặp giúp tránh nhầm một kết quả đọc với liên kết sống đến dữ liệu hiện tại.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tree-map-005">lowerEntry, floorEntry, ceilingEntry và higherEntry</a>

<details>
<summary>Xem chi tiết</summary>

# lowerEntry, floorEntry, ceilingEntry và higherEntry

## 1. Tìm một ánh xạ lân cận thay vì chỉ tìm đúng khóa

Giả sử chương trình giữ các phiên bản cấu hình theo mốc bắt đầu hiệu lực. Khi hỏi tại mốc 25, map có thể chỉ chứa các mốc 10, 20 và 30.

Get(25) chỉ trả lời liệu có đúng khóa 25 hay không. Nó không trả lời phiên bản gần nhất đã bắt đầu trước hoặc tại 25.

TreeMap thực hiện NavigableMap, cung cấp bốn phép tìm entry lân cận theo thứ tự khóa:

- LowerEntry: phía trước, không lấy khóa bằng mốc hỏi.
- FloorEntry: phía trước, có thể lấy khóa bằng mốc hỏi.
- CeilingEntry: phía sau, có thể lấy khóa bằng mốc hỏi.
- HigherEntry: phía sau, không lấy khóa bằng mốc hỏi.

“Phía trước/phía sau” ở đây phải hiểu theo **thứ tự của map**. Với thứ tự số tự nhiên, chúng tương ứng phía nhỏ hơn/lớn hơn. Khi đảo Comparator, không được tiếp tục áp trực giác số tăng như cũ.

Bài này giải thích dấu biên, trường hợp không có kết quả, ý nghĩa Map.Entry trả về và cách chọn phương thức theo yêu cầu thực tế.

## 2. Bảng định nghĩa cần nắm chắc

Gọi q là khóa truy vấn, k là khóa của một ánh xạ đang có.

| Phương thức | Điều kiện của khóa kết quả | Chọn khóa nào trong các ứng viên? |
| --- | --- | --- |
| lowerEntry(q) | k < q | Khóa lớn nhất thỏa điều kiện |
| floorEntry(q) | k ≤ q | Khóa lớn nhất thỏa điều kiện |
| ceilingEntry(q) | k ≥ q | Khóa nhỏ nhất thỏa điều kiện |
| higherEntry(q) | k > q | Khóa nhỏ nhất thỏa điều kiện |

Tất cả quan hệ trong bảng dùng thứ tự của map, qua compareTo hoặc Comparator.

Nếu không có ứng viên, kết quả là null. Nếu có, kết quả là Map.Entry<K,V>, chứa cả khóa và giá trị của ánh xạ được chọn.

Đối chiếu định nghĩa công khai ở [NavigableMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableMap.html).

### 2.1. Hai cặp khác nhau ở dấu bằng

Lower và floor cùng tìm về phía nhỏ hơn theo thứ tự, nhưng floor cho phép bằng q.

Ceiling và higher cùng tìm về phía lớn hơn theo thứ tự, nhưng ceiling cho phép bằng q.

Khi q không tồn tại trong map, lower và floor thường cho cùng ứng viên; ceiling và higher cũng vậy. Khi q có một vị trí khóa tương đương theo Comparator, dấu bằng tạo khác biệt rõ.

### 2.2. “Lớn nhất” hoặc “nhỏ nhất” giúp chọn sát biên

Không phải chỉ lấy một khóa bất kỳ nhỏ hơn q. Lower/floor lấy khóa sát q nhất từ phía được phép.

Tương tự, ceiling/higher không lấy một khóa lớn hơn tùy ý; chúng chọn ứng viên đầu tiên phù hợp theo chiều thứ tự.

Đó là lý do các phép này hữu ích cho mốc hiệu lực, vị trí tiếp theo hoặc ranh giới của một khoảng.

### 2.3. Khóa truy vấn không cần tồn tại

Q là mốc dùng để so sánh. Nó không cần được put vào map trước.

Không thêm q tạm vào map rồi xóa chỉ để tìm lân cận. Những API này đã được thiết kế để định vị theo một khóa truy vấn độc lập.

## 3. Đọc bốn phép bằng một bộ dữ liệu nhỏ

Map theo thứ tự số tự nhiên có:

```text
10=ten       20=twenty       30=thirty
   ────────────────┬────────────────
              các khóa đã có
```

Bảng dưới chỉ ghi **khóa** của entry kết quả; dấu “không có” nghĩa cả entry là null:

| q | lowerEntry | floorEntry | ceilingEntry | higherEntry |
| --- | --- | --- | --- | --- |
| 5 | Không có | Không có | 10 | 10 |
| 10 | Không có | 10 | 10 | 20 |
| 15 | 10 | 10 | 20 | 20 |
| 20 | 10 | 20 | 20 | 30 |
| 25 | 20 | 20 | 30 | 30 |
| 30 | 20 | 30 | 30 | Không có |
| 35 | 30 | 30 | Không có | Không có |

### 3.1. Truy vấn bằng một khóa đang có

Với q = 20:

- Lower bỏ qua 20 vì yêu cầu nhỏ hơn nghiêm ngặt, nên chọn 10.
- Floor chấp nhận 20, nên chọn 20.
- Ceiling cũng chấp nhận 20, nên chọn 20.
- Higher bỏ qua 20, nên chọn 30.

Floor và ceiling có thể trả cùng một ánh xạ. Không có yêu cầu hai kết quả này phải khác nhau.

### 3.2. Truy vấn nằm giữa hai khóa

Với q = 25, không có khóa bằng 25:

- Lower và floor đều chọn 20.
- Ceiling và higher đều chọn 30.

Đây là trường hợp nhiều người học thấy các cặp “giống nhau”, rồi bỏ qua dấu bằng. Phải kiểm tra thêm trường hợp q đúng bằng khóa để hiểu đầy đủ.

### 3.3. Truy vấn vượt biên và map rỗng

Q nhỏ hơn mọi khóa thì không có ứng viên cho lower/floor. Q lớn hơn mọi khóa thì không có ứng viên cho ceiling/higher.

Map rỗng không có ứng viên cho cả bốn phép, với khóa truy vấn hợp lệ theo hợp đồng.

Không tự thay kết quả thiếu bằng khóa nhỏ nhất/lớn nhất ở phía ngược lại. Nếu ứng dụng cần một quy tắc dự phòng như vậy, phải định nghĩa rõ ngoài phép truy vấn.

## 4. Ví dụ Java 17: bảy vị trí truy vấn và toàn bộ kết quả

Lưu thành **NeighborEntriesDemo.java**:

```java
import java.util.ArrayList;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class NeighborEntriesDemo {
    static String format(Map.Entry<Integer, String> entry) {
        return entry == null ? "none" : entry.getKey() + "=" + entry.getValue();
    }

    static void show(NavigableMap<Integer, String> map, int query) {
        System.out.println(query + " | " + format(map.lowerEntry(query))
                + " | " + format(map.floorEntry(query))
                + " | " + format(map.ceilingEntry(query))
                + " | " + format(map.higherEntry(query)));
    }

    public static void main(String[] args) {
        NavigableMap<Integer, String> map = new TreeMap<>();
        map.put(30, "thirty");
        map.put(10, "ten");
        map.put(20, "twenty");

        System.out.println("q | lower | floor | ceiling | higher");
        for (int query : new int[] {5, 10, 15, 20, 25, 30, 35}) {
            show(map, query);
        }
        System.out.println("keys after queries: " + new ArrayList<>(map.keySet()));
        System.out.println("size after queries: " + map.size());

        NavigableMap<Integer, String> empty = new TreeMap<>();
        System.out.println("empty floor: " + format(empty.floorEntry(20)));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 NeighborEntriesDemo.java
java NeighborEntriesDemo
```

Stdout:

```text
q | lower | floor | ceiling | higher
5 | none | none | 10=ten | 10=ten
10 | none | 10=ten | 10=ten | 20=twenty
15 | 10=ten | 10=ten | 20=twenty | 20=twenty
20 | 10=ten | 20=twenty | 20=twenty | 30=thirty
25 | 20=twenty | 20=twenty | 30=thirty | 30=thirty
30 | 20=twenty | 30=thirty | 30=thirty | none
35 | 30=thirty | 30=thirty | none | none
keys after queries: [10, 20, 30]
size after queries: 3
empty floor: none
```

Các chương trình trong bài được biên dịch với --release 17 và chạy trên OpenJDK 24.0.2. Cờ --release chọn mức ngôn ngữ/API và bytecode đích; JVM chạy chương trình vẫn là bản 24.0.2.

### 4.1. Thứ tự thêm không quyết định kết quả lân cận

Map được thêm theo 30,10,20 nhưng có thứ tự khóa 10,20,30. Các phép điều hướng dựa vào thứ tự ấy.

Không chọn “entry vừa được put gần đây nhất” hoặc “entry nằm gần dòng thêm q nhất trong mã nguồn”.

### 4.2. Tìm không làm mất entry

Sau các truy vấn, map vẫn có ba khóa 10,20,30 và size bằng 3.

Bốn phép này định vị ánh xạ, không lấy ra rồi xóa. Không nhầm chúng với các phép poll có tác dụng loại phần tử.

### 4.3. Chỉ xử lý entry sau khi kiểm tra tồn tại

Hàm format kiểm tra entry == null trước khi gọi getKey/getValue.

Đó là hai tầng riêng: **có ánh xạ kết quả hay không**, rồi **khóa/giá trị của ánh xạ ấy là gì**.

Không gọi map.floorEntry(q).getValue() nếu chưa có cơ sở rằng một ứng viên chắc chắn tồn tại.

## 5. Entry trả về chứa gì và có thể sửa được không?

### 5.1. Entry có giá trị null vẫn là một kết quả

TreeMap cho phép giá trị null. Nếu một khóa phù hợp đang gắn với null, floorEntry hoặc phương thức khác vẫn có thể trả một entry khác null.

Ví dụ, map chứa 20=null. FloorEntry(20) có kết quả với key 20 và value null.

Không kiểm tra chỉ getValue() == null để kết luận không tìm thấy khóa. Kết luận “không có ứng viên” dựa vào việc **entry trả về là null**.

### 5.2. Các entry điều hướng của TreeMap là bản chụp ánh xạ

Với bốn phương thức đang xét của TreeMap, entry trả về ghi nhận cặp khóa–giá trị tại thời điểm được tạo và không hỗ trợ setValue.

Đổi giá trị trong map bằng put sau đó không tự cập nhật trường giá trị đã được ghi vào bản chụp entry trước đó.

Trong nguồn [TreeMap.java OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/TreeMap.java), các phép này đi qua exportEntry để tạo một SimpleImmutableEntry từ kết quả tìm.

Không suy tính chất này thành một quy tắc chung cho mọi Map.Entry lấy từ mọi nguồn. Entry có thể xuất hiện từ iterator, view hoặc một lớp triển khai khác với hợp đồng khác. Cần biết API đã tạo ra nó.

### 5.3. Sửa map qua API của map

Nếu muốn thay ánh xạ được chọn, trước hết lấy entry, kiểm tra tồn tại rồi dùng khóa của nó cho thao tác cập nhật phù hợp trên map.

Ví dụ minh họa trong ngữ cảnh có map và q:

```java
Map.Entry<Integer, String> selected = map.floorEntry(q);
if (selected != null) {
    map.put(selected.getKey(), "updated");
}
```

Đây là đoạn trong một chương trình đã có biến và import thích hợp, không phải một tệp chạy độc lập.

Tìm và cập nhật là hai bước. Khi map được dùng chung giữa các luồng, một chuỗi như vậy cần cơ chế phối hợp theo yêu cầu ứng dụng; bản chụp entry không tự biến hai bước thành một giao dịch.

### 5.4. Bản chụp không phải bản sao sâu

Nếu value là một đối tượng có thể sửa, bản chụp entry và map vẫn có thể cùng tham chiếu đối tượng đó.

Không thay được value bằng setValue không có nghĩa những trường bên trong đối tượng value đã trở thành bất biến.

Các ví dụ dùng String để tập trung vào ý nghĩa ánh xạ và tránh lẫn thêm tác động sửa đối tượng dùng chung.

## 6. Thứ tự Comparator quyết định mọi dấu quan hệ

### 6.1. Thứ tự số tự nhiên

Với khóa 10,20,30 theo natural order, lowerEntry(25) chọn 20 và higherEntry(25) chọn 30.

Ở cấu hình này, “nhỏ hơn” trùng với số học quen thuộc.

### 6.2. Thứ tự đảo chiều

Nếu map có thứ tự 30,20,10, thì 30 đứng trước 25 theo thứ tự đảo, còn 20 đứng sau 25.

Với cùng dữ liệu và q = 25:

| Phép | Theo thứ tự tự nhiên | Theo thứ tự đảo |
| --- | --- | --- |
| lowerEntry | 20 | 30 |
| floorEntry | 20 | 30 |
| ceilingEntry | 30 | 20 |
| higherEntry | 30 | 20 |

Các định nghĩa không đổi. Điều đổi là quan hệ so sánh mà các định nghĩa dùng.

Đừng cố nhớ “lower luôn là số bé hơn” rồi áp cho một Comparator bất kỳ.

### 6.3. DescendingMap đảo nghĩa điều hướng

DescendingMap là view đảo thứ tự của map gốc. Những quan hệ điều hướng được hiểu theo thứ tự của view ấy.

Về quan hệ khóa:

- Lower của view đảo tương ứng higher của map gốc.
- Floor của view đảo tương ứng ceiling của map gốc.
- Ceiling của view đảo tương ứng floor của map gốc.
- Higher của view đảo tương ứng lower của map gốc.

View vẫn gắn với cùng dữ liệu nền. Việc nhìn qua view không tự tạo một bản sao riêng của toàn map.

### 6.4. “Bằng” là bằng theo thứ tự

Floor/ceiling xét khả năng so sánh bằng 0 theo thứ tự map, không đòi q và khóa lưu trong cây phải là cùng một đối tượng theo ==.

Comparator cần có quan hệ thứ tự đúng và nên nhất quán với equals để TreeMap tuân đầy đủ hợp đồng Map. Không thay Comparator hoặc sửa thuộc tính dùng để sắp xếp khóa khi chúng đang được dùng như thể cây sẽ tự tổ chức lại.

## 7. Ví dụ Java 17: null, bản chụp và view đảo chiều

Lưu thành **NeighborEntrySemantics.java**:

```java
import java.util.ArrayList;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class NeighborEntrySemantics {
    static String format(Map.Entry<Integer, String> entry) {
        return entry == null ? "none" : entry.getKey() + "=" + entry.getValue();
    }

    public static void main(String[] args) {
        NavigableMap<Integer, String> map = new TreeMap<>();
        map.put(10, "ten");
        map.put(20, null);
        map.put(30, "thirty");

        Map.Entry<Integer, String> saved = map.floorEntry(20);
        System.out.println("entry exists: " + (saved != null));
        System.out.println("snapshot before put: " + format(saved));
        map.put(20, "updated");
        System.out.println("current value: " + map.get(20));
        System.out.println("snapshot after put: " + format(saved));
        try {
            saved.setValue("changed through entry");
        } catch (UnsupportedOperationException expected) {
            System.out.println("setValue: UnsupportedOperationException");
        }

        NavigableMap<Integer, String> descending = map.descendingMap();
        System.out.println("descending keys: " + new ArrayList<>(descending.keySet()));
        System.out.println("descending lower 25: " + format(descending.lowerEntry(25)));
        System.out.println("descending floor 25: " + format(descending.floorEntry(25)));
        System.out.println("descending ceiling 25: " + format(descending.ceilingEntry(25)));
        System.out.println("descending higher 25: " + format(descending.higherEntry(25)));
        System.out.println("original keys: " + new ArrayList<>(map.keySet()));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 NeighborEntrySemantics.java
java NeighborEntrySemantics
```

Stdout trên môi trường đã nêu:

```text
entry exists: true
snapshot before put: 20=null
current value: updated
snapshot after put: 20=null
setValue: UnsupportedOperationException
descending keys: [30, 20, 10]
descending lower 25: 30=thirty
descending floor 25: 30=thirty
descending ceiling 25: 20=updated
descending higher 25: 20=updated
original keys: [10, 20, 30]
```

### 7.1. Có entry dù value bằng null

Saved khác null vì khóa 20 là ứng viên hợp lệ cho floorEntry(20). Value của nó là null, nhưng không phải “không có kết quả”.

Sau put(20, "updated"), get trên map cho giá trị mới. Saved vẫn ghi 20=null vì đó là bản chụp cặp ánh xạ đã nhận.

### 7.2. SetValue bị từ chối theo hợp đồng entry này

Thử saved.setValue gây UnsupportedOperationException. Ví dụ bắt ngoại lệ để minh họa khả năng của đối tượng trả về.

Trong cách dùng thông thường, cập nhật qua map như phần trước thay vì dùng ngoại lệ làm đường xử lý chính.

### 7.3. Dữ liệu gốc và chiều nhìn khác nhau

View descending duyệt 30,20,10. Tại q=25, lower/floor của view chọn 30 và ceiling/higher chọn 20.

Map gốc vẫn có dãy khóa 10,20,30. Không có thao tác sắp xếp lại map gốc theo lịch sử thêm; chỉ có một góc nhìn thứ tự khác trên cùng dữ liệu.

## 8. Chọn phương thức từ yêu cầu nghiệp vụ

Hãy viết rõ điều kiện trước khi nhớ tên API.

| Điều kiện cần tìm, theo thứ tự map | Phương thức phù hợp |
| --- | --- |
| Mốc cuối cùng trước q, không lấy q | lowerEntry(q) |
| Mốc cuối cùng không vượt q | floorEntry(q) |
| Mốc đầu tiên không nhỏ hơn q | ceilingEntry(q) |
| Mốc đầu tiên sau q, không lấy q | higherEntry(q) |

### 8.1. Cấu hình bắt đầu có hiệu lực từ một mốc

Giả sử các phiên bản bắt đầu ở 10,20,30. Mỗi phiên bản được coi là có hiệu lực từ mốc bắt đầu cho tới trước phiên bản kế tiếp.

Tại 25, phiên bản có mốc bắt đầu lớn nhất không vượt 25 là 20: dùng floorEntry.

Tại đúng 20, phiên bản 20 phải được chọn. Dùng lowerEntry sẽ lùi về 10 và tạo lỗi ngay tại thời điểm chuyển phiên bản.

Nếu truy vấn trước mốc 10, floorEntry không có kết quả. Ứng dụng cần quyết định đó là thiếu cấu hình, dùng cấu hình mặc định hay một tình huống khác; API không tự đoán chính sách ấy.

### 8.2. Tìm công việc ở mốc hiện tại hoặc sau đó

Nếu một công việc có thể bắt đầu ngay tại q, dùng ceilingEntry(q).

Nếu quy tắc yêu cầu mốc phải sau q nghiêm ngặt, dùng higherEntry(q).

Hai yêu cầu khác nhau chỉ ở dấu bằng, nhưng có thể tạo khác biệt về việc một mục được xử lý ngay hay bị bỏ qua.

### 8.3. Tìm phiên bản ngay trước hoặc ngay sau một khóa

Khi q đang có trong map và cần hàng xóm khác nó, lower/higher là hai phép phù hợp.

Nếu dùng floor/ceiling, kết quả có thể là chính entry q, không phải một hàng xóm khác.

Không sửa q thành q - 1 hoặc q + 1 để giả lập dấu nghiêm ngặt. Cách đó phụ thuộc kiểu dữ liệu, có thể tràn số, và không áp dụng chung cho mọi Comparator hoặc kiểu khóa.

## 9. “Gần nhất theo phía” khác “gần nhất theo khoảng cách”

Bốn phép điều hướng chọn theo quan hệ thứ tự và hướng biên. Chúng không tự tính khoảng cách số học.

Với các khóa 10 và 20:

- Tại q = 12, floor chọn 10, ceiling chọn 20; 10 gần hơn về khoảng cách số.
- Tại q = 18, floor vẫn chọn 10, ceiling vẫn chọn 20; lần này 20 gần hơn.

Muốn chọn khóa gần nhất theo khoảng cách tuyệt đối trên số tự nhiên, có thể lấy hai ứng viên floor và ceiling rồi so khoảng cách, xử lý trường hợp chỉ có một bên và định nghĩa cách phá hòa.

Một Comparator tổng quát chỉ cho thứ tự; nó không tự cung cấp một hàm khoảng cách có ý nghĩa nghiệp vụ.

Nếu tính chênh lệch giữa các khóa int ở biên lớn, phải tính trong kiểu đủ rộng trước phép trừ. Đừng để một phép abs trên hiệu int đã tràn quyết định sai ứng viên.

Các quy tắc khoảng cách này là bước chọn bổ sung của ứng dụng, không phải ý nghĩa ẩn của floorEntry hoặc ceilingEntry.

## 10. Entry, Key, get và poll phục vụ các nhu cầu khác nhau

### 10.1. Chỉ cần khóa thì dùng phương thức Key tương ứng

LowerKey, floorKey, ceilingKey và higherKey áp cùng quan hệ nhưng chỉ trả khóa.

Nếu cần cả khóa lẫn giá trị, dùng phương thức Entry để có trực tiếp cặp ánh xạ phù hợp, thay vì tìm khóa rồi get lại một lần.

Trong một chương trình có sửa đổi đồng thời, hai lời gọi riêng không tự là một ảnh chụp thống nhất. TreeMap cũng không tự an toàn đa luồng.

### 10.2. Get chỉ tìm khóa tương đương, không tìm lân cận

Get(q) không tự chọn floor khi không có q.

Ví dụ map có 10,20,30, get(25) không có ánh xạ tương đương. FloorEntry(25) có thể trả 20=twenty.

Không trộn chính sách “lấy đúng khóa” và “lấy mốc áp dụng gần nhất” bằng việc xử lý null tùy tiện.

### 10.3. Điều hướng không xóa, poll có thể xóa

Bốn phép đang xét chỉ định vị và trả kết quả. Chúng không làm size giảm.

PollFirstEntry/pollLastEntry lấy và xóa entry biên. Đó là API khác với một ý nghĩa thay đổi dữ liệu rõ ràng.

Muốn xóa một lân cận vừa tìm, cần một thao tác xóa riêng và xem tính nguyên tử nếu trạng thái được dùng chung; không gọi lowerEntry rồi mặc định mục đã rời map.

## 11. Null của khóa và tính hợp lệ của truy vấn

### 11.1. Khóa truy vấn phải so sánh được

Với natural order, không dùng null như một mốc hợp lệ. Với Comparator riêng, khả năng nhận null phụ thuộc Comparator và hợp đồng của map.

Các API điều hướng có thể ném ngoại lệ khi khóa không thể so sánh phù hợp. Dùng kiểu generic và quy tắc khóa rõ ràng giúp tránh các lời gọi không có ý nghĩa.

Map rỗng chỉ có nghĩa không có ứng viên; không nên coi trạng thái rỗng là lý do bỏ qua mọi ràng buộc hợp lệ của kiểu khóa/truy vấn.

### 11.2. Một khóa null hợp lệ khác với không có entry

Nếu chủ động dùng Comparator.nullsFirst để cho phép khóa null, một entry kết quả có thể có getKey() bằng null.

Ví dụ map theo nullsFirst có null="null-key" và 10="ten". LowerEntry(10) có thể là entry của khóa null. Entry ấy vẫn khác null.

Trong trường hợp này lowerKey(10) trả null không đủ để phân biệt khóa null hợp lệ với không có ứng viên. Dùng kết quả Entry khi cần phân biệt hai trường hợp.

Đây là lý do phải tách rõ ba khái niệm:

- Không có entry kết quả.
- Có entry với khóa null, nếu được phép.
- Có entry với giá trị null.

Nếu ứng dụng không cần khóa null, có thể chọn chính sách không cho phép để mô hình đơn giản hơn. Không dùng null như một “khóa nhỏ vô hạn” mà chưa có Comparator hỗ trợ.

## 12. Cơ chế tìm trên cây và chi phí sử dụng

TreeMap dựa trên cây tìm kiếm cân bằng. Một phép tìm lân cận không cần quét lần lượt mọi entry.

Hình dung floorEntry(q):

1. Nếu khóa hiện tại lớn hơn q, ứng viên cần tìm phải nằm phía nhỏ hơn.
2. Nếu khóa hiện tại không vượt q, nó là một ứng viên; phần có thể tốt hơn nằm phía lớn hơn nhưng vẫn không vượt q.
3. Nếu gặp bằng q thì đã có ứng viên tốt nhất cho dấu ≤.
4. Khi không thể đi tiếp, trả ứng viên phù hợp hoặc không có kết quả.

Lower giống hướng tìm về phía nhỏ hơn nhưng không chấp nhận bằng q. Ceiling/higher có mô hình đối xứng về phía lớn hơn.

Nguồn OpenJDK có thể thực hiện việc tìm ứng viên qua các đường con và cha cụ thể. Phần giải thích trên là cách lập luận về điều kiện kết quả, không phải yêu cầu mã nguồn phải giữ đúng một biến ứng viên theo một mẫu duy nhất.

### 12.1. Một truy vấn có chi phí theo chiều cao

Với TreeMap và mô hình so sánh khóa có chi phí hằng số, các đường điều hướng này có chi phí O(log n) theo cây cân bằng được phân tích.

Nếu so sánh khóa đắt, phần chi phí ấy cũng phải được tính. Không xem tên phương thức ngắn là một bảo đảm thời gian hằng số.

### 12.2. Dùng để định vị, không thay iterator cho mọi lượt quét

Một chuỗi gọi higherEntry lặp lại có thể hữu ích khi chương trình cần từng truy vấn lân cận độc lập.

Nhưng nếu mục tiêu là duyệt liên tiếp k entry, mỗi lời gọi tìm mới có thể cộng thêm O(log n), tạo tổng O(k log n). Một iterator hoặc view khoảng có đường đi liên tiếp phù hợp hơn cho lượt quét.

Đặc biệt, lấy floorEntry hoặc ceilingEntry với chính khóa kết quả làm mốc tiếp theo mà không thay đổi gì có thể trả lại cùng entry mãi. Khi cần một khóa khác tiếp theo, điều kiện nghiêm ngặt của higher/lower mới phản ánh việc tiến bước.

Việc chọn iterator hay truy vấn lân cận còn phụ thuộc mục tiêu và hợp đồng sửa đổi dữ liệu, không chỉ tránh vòng lặp không tiến triển.

## 13. Cách tự kiểm tra đủ các biên

Một bộ dữ liệu ba khóa cách nhau đủ rộng giúp kiểm tra các nhóm sau:

| Nhóm truy vấn | Điều cần xác nhận |
| --- | --- |
| Nhỏ hơn mọi khóa | Lower/floor thiếu, ceiling/higher chọn khóa đầu theo thứ tự |
| Bằng khóa đầu | Dấu nghiêm ngặt và dấu bao gồm khác nhau |
| Nằm giữa hai khóa | Cặp lower/floor và cặp ceiling/higher có thể cùng kết quả |
| Bằng một khóa giữa | Floor/ceiling có thể trả chính khóa ấy |
| Bằng khóa cuối | Một phía nghiêm ngặt có thể không còn kết quả |
| Lớn hơn mọi khóa | Lower/floor chọn khóa cuối, ceiling/higher thiếu |
| Map rỗng | Không có entry cho truy vấn hợp lệ |
| Map một khóa | Kiểm tra rõ dấu bằng ở cả hai phía |

Thử thêm Comparator đảo chiều để chắc rằng bài kiểm tra dùng quan hệ của map, không chỉ một điều kiện số học cố định.

Với Entry, kiểm tra cả sự tồn tại, khóa và giá trị. Một kết quả có đúng khóa nhưng đọc sai ý nghĩa null vẫn là lỗi ở mã gọi.

Để kiểm tra tính không thay đổi dữ liệu, có thể giữ các ánh xạ dự kiến và xác nhận size/khóa/giá trị sau loạt truy vấn.

Với bản chụp entry, kiểm tra thay map bằng put không làm cặp đã nhận tự đổi và không dùng setValue làm cách cập nhật cho entry điều hướng của TreeMap.

## 14. Lỗi phổ biến và cách sửa

| Hiểu nhầm | Cách sửa |
| --- | --- |
| Lower và floor giống nhau vì một lần thử đều trả 20 | Thử thêm q đúng bằng một khóa |
| Ceiling luôn là phần tử sau q | Ceiling cho phép bằng q; higher mới bỏ dấu bằng |
| Query phải được thêm trước vào map | Dùng trực tiếp khóa truy vấn để định vị |
| Không có kết quả thì API tự lấy phía đối diện | Xử lý null theo chính sách rõ ràng |
| Entry có value null nghĩa không tìm thấy | Kiểm tra entry == null trước, tách giá trị của ánh xạ |
| Gọi setValue trên entry điều hướng sẽ sửa map | Các entry này của TreeMap không hỗ trợ setValue; dùng API map |
| Lower luôn là số nhỏ hơn | Đọc theo Comparator, kể cả view đảo |
| Floor/ceiling tự chọn khoảng cách nhỏ nhất | Chúng chọn theo phía; khoảng cách là bước riêng |
| Có kết quả lân cận nghĩa entry đã bị xóa | Bốn phương thức chỉ tìm, không poll |
| Lấy Key rồi get luôn giống một ảnh chụp Entry | Hai lời gọi có thể tách biệt về trạng thái |
| Q - 1 thay được lower cho mọi loại khóa | Không phụ thuộc số học; dùng dấu quan hệ đúng |
| Lặp ceiling với khóa vừa tìm sẽ luôn tiến | Nó có thể trả lại cùng khóa; chọn higher hoặc iterator khi phù hợp |

## 15. Bài tập tự kiểm tra có lời giải

### Bài 1: Dấu bằng

Map tự nhiên có 4,8,12. Với q=8, khóa của bốn kết quả lower, floor, ceiling, higher là gì?

**Lời giải:** 4,8,8,12.

### Bài 2: Nằm giữa hai khóa

Cũng map ấy, q=10. Bốn khóa kết quả là gì?

**Lời giải:** 8,8,12,12. Không có khóa bằng 10 nên dấu bằng không tạo khác biệt trong từng cặp.

### Bài 3: Ngoài biên

Với q=2, có lowerEntry hoặc floorEntry không? Ceiling/higher chọn gì?

**Lời giải:** lower/floor không có; ceiling/higher chọn khóa 4.

### Bài 4: Chọn cấu hình có hiệu lực

Phiên bản bắt đầu từ các mốc 100,200,300. Tại đúng 200, nếu phiên bản mới có hiệu lực ngay từ mốc ấy, dùng lower hay floor?

**Lời giải:** floorEntry(200), để chọn phiên bản bắt đầu ở 200. Lower sẽ lùi về 100.

### Bài 5: Có null trong giá trị

Map có 8=null. FloorEntry(8) trả entry khác null với value null. Có được xử lý như không có ứng viên không?

**Lời giải:** không. Ánh xạ khóa 8 đang tồn tại; chỉ giá trị của nó là null.

### Bài 6: Thứ tự đảo

Map có các số 4,8,12 nhưng Comparator đảo chiều. LowerEntry(10) chọn số nào?

**Lời giải:** 12, vì 12 nằm trước 10 theo thứ tự đảo. Không áp điều kiện “số nhỏ hơn 10” vào quan hệ của map đảo.

### Bài 7: Gần nhất về khoảng cách

Map có 10 và 20, q=18. Floor trả 10 và ceiling trả 20. Phương thức nào tự quyết định 20 là gần nhất theo khoảng cách tuyệt đối?

**Lời giải:** không phương thức nào trong bốn phép tự làm bước chọn đó. Ứng dụng so hai khoảng cách và xử lý biên/phá hòa nếu cần.

### Bài 8: Bản chụp entry

Saved = floorEntry(8) ghi 8="old". Sau put(8, "new"), saved có bắt buộc tự đổi thành "new" không?

**Lời giải:** không. Entry điều hướng của TreeMap là bản chụp ánh xạ, không phải một handle cập nhật sống cho map.

### Bài 9: Tiến tới khóa tiếp theo

Tìm được khóa 8 và muốn một khóa lớn hơn khác 8. CeilingEntry(8) có bảo đảm tiến tới khóa khác không?

**Lời giải:** không. Nó có thể trả lại 8; higherEntry(8) mới yêu cầu lớn hơn nghiêm ngặt theo thứ tự map.

## 16. Tài liệu đối chiếu

- [NavigableMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableMap.html): bốn quan hệ lân cận, phương thức Key và ý nghĩa view đảo.
- [TreeMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html): hợp đồng cụ thể của lớp, quy tắc khóa và entry trả về.
- [TreeMap.java OpenJDK 17u](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/TreeMap.java): các đường tìm ứng viên và exportEntry.
- [Map.Entry Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html): cặp khóa–giá trị và khả năng tùy chọn của thao tác setValue.

Chọn đúng một trong bốn phép bắt đầu từ điều kiện cần tìm: phía nào, có lấy trường hợp bằng hay không, và theo thứ tự nào. Sau đó xử lý sự tồn tại của entry riêng với nội dung khóa–giá trị mà entry mang theo.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tree-map-006">headMap, tailMap, subMap và lựa chọn biên</a>

<details>
<summary>Xem chi tiết</summary>

# TreeMap: headMap, tailMap, subMap và cách lựa chọn biên

Một TreeMap có thể chứa nhiều khóa, nhưng một tác vụ thường chỉ cần một vùng: các mốc trước hạn, các bản ghi từ một mốc trở đi hoặc các sự kiện nằm giữa hai thời điểm.

`headMap`, `tailMap` và `subMap` biểu diễn những vùng ấy theo thứ tự khóa. Điều cần chọn không chỉ là hai con số. Phải xác định chiều thứ tự, mỗi biên có được bao gồm hay không và kết quả có còn dùng chung dữ liệu với map gốc hay không.

Bài dùng Java 17. Sau bài này, bạn cần đọc được một khoảng thành điều kiện so sánh, viết đúng lời gọi API, kiểm tra các trường hợp bằng biên và nhận ra tác động của thao tác qua view.

## 1. Mô hình: một cửa sổ khóa trên cùng dữ liệu

Giả sử TreeMap dùng thứ tự số tăng và có các khóa:

~~~text
10, 20, 30, 40, 50
~~~

Ba kiểu cửa sổ là:

- `headMap`: phần đứng trước một mốc kết thúc.
- `tailMap`: phần bắt đầu từ hoặc sau một mốc.
- `subMap`: phần nằm giữa hai mốc.

Kết quả trên TreeMap là góc nhìn (`view`) có phạm vi khóa, không phải một bản sao đã lọc xong. Bạn có thể giữ view để đọc vùng dữ liệu đó; khi gốc đổi trong phạm vi, view phản ánh thay đổi.

Tạo một view không tự thêm, xóa hay sửa ánh xạ. Những thao tác cập nhật hợp lệ thực hiện qua view về sau mới tác động đến dữ liệu gốc. [Java 17: các phương thức vùng của TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)

### 1.1. Điều kiện xét trên khóa

View theo khoảng khóa không tự lọc theo nội dung giá trị. Nếu khóa là mã sản phẩm còn giá trị có trường ngày cập nhật, headMap theo mã không biến thành truy vấn trước một ngày.

Muốn các khoảng phục vụ thời gian, khóa phải có quy tắc thứ tự thể hiện thời gian phù hợp. Thiết kế khóa và câu hỏi nghiệp vụ phải khớp nhau trước khi chọn phương thức.

### 1.2. View không có nghĩa chỉ đọc

Từ “góc nhìn” chỉ nói về quan hệ với dữ liệu gốc, không có nghĩa mọi thao tác sửa bị cấm. TreeMap cung cấp những thao tác sửa được hỗ trợ trong vùng khóa.

Ví dụ, `view.remove(key)` có thể xóa ánh xạ tương ứng khỏi gốc; `view.clear()` có thể xóa cả vùng. Vì vậy, đừng dùng một view làm nơi “sửa thử” với giả định gốc không bị ảnh hưởng.

## 2. Ba mặc định của SortedMap

Các phương thức không có boolean dùng những biên cố định:

| Lời gọi | Điều kiện khóa k theo thứ tự map | Ký hiệu vùng |
|---|---|---|
| headMap(b) | k < b | Phần trước b, không gồm b |
| tailMap(a) | k ≥ a | Phần từ a, có gồm a |
| subMap(a, b) | a ≤ k < b | [a, b) |

Với khóa 10, 20, 30, 40, 50:

~~~text
headMap(30)   -> 10, 20
tailMap(30)   -> 30, 40, 50
subMap(20,40) -> 20, 30
~~~

Mặc định headMap loại mốc kết thúc; tailMap gồm mốc bắt đầu; subMap gồm đầu dưới và loại đầu trên. Không nên nhớ chung “các hàm này đều bỏ biên” hoặc “đều gồm biên”. [Java 17: SortedMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SortedMap.html)

### 2.1. Ý nghĩa của khoảng nửa mở

`[a, b)` nghĩa là bắt đầu có a, kết thúc trước b. Hai vùng liền nhau `[a, b)` và `[b, c)` không lặp khóa bằng b.

Điều đó hữu ích khi chia dữ liệu thành các phiên hoặc các ngày. Mốc nối thuộc vùng thứ hai, không xuất hiện hai lần chỉ vì nó bằng đúng ranh giới.

Đây là quy tắc lựa chọn biên, không phải yêu cầu dữ liệu phải có a hoặc b. Nếu không có khóa ở biên, các khóa nằm giữa vẫn được chọn.

### 2.2. Kiểu trả về ở Java 17

Các dạng cũ không có boolean được khai báo trả SortedMap. Các dạng có boolean trong NavigableMap trả NavigableMap.

Đoạn minh họa một phần với biến `map` có kiểu NavigableMap:

~~~java
SortedMap<Integer, String> legacy = map.subMap(20, 40);

NavigableMap<Integer, String> explicit =
        map.subMap(20, true, 40, false);
~~~

Nếu cần tiếp tục dùng floorKey, firstEntry hoặc các dạng điều hướng khác trên kết quả, dạng có boolean cũng thể hiện được kiểu NavigableMap trực tiếp. Không ép kiểu tùy tiện chỉ để bỏ qua khác biệt khai báo.

## 3. Chọn rõ từng biên bằng boolean

NavigableMap bổ sung:

~~~text
headMap(toKey, inclusive)
tailMap(fromKey, inclusive)
subMap(fromKey, fromInclusive, toKey, toInclusive)
~~~

Đây là sơ đồ chữ ký để đọc tham số, không phải đoạn mã chạy độc lập.

`true` nghĩa là chấp nhận khóa so sánh bằng biên tương ứng; `false` nghĩa là loại khóa so sánh bằng biên đó. Với subMap, hai boolean áp dụng riêng cho hai đầu. [Java 17: NavigableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableMap.html)

### 3.1. Một biên: trước hoặc sau mốc

Trên dãy số tăng:

| Yêu cầu | Lời gọi với mốc 30 | Khóa kết quả |
|---|---|---|
| Trước 30 | headMap(30, false) | 10, 20 |
| Không sau 30 | headMap(30, true) | 10, 20, 30 |
| Từ 30 trở đi | tailMap(30, true) | 30, 40, 50 |
| Sau 30 | tailMap(30, false) | 40, 50 |

Những từ như “đến”, “từ”, “trước” trong yêu cầu tự nhiên có thể được người dùng hiểu khác nhau. Trong mã, hãy chuyển chúng thành điều kiện có hoặc không có dấu bằng trước.

### 3.2. Hai biên: bốn vùng thường gặp

Với a = 20 và b = 40:

| Vùng | Lời gọi | Khóa kết quả |
|---|---|---|
| [20, 40] | subMap(20, true, 40, true) | 20, 30, 40 |
| [20, 40) | subMap(20, true, 40, false) | 20, 30 |
| (20, 40] | subMap(20, false, 40, true) | 30, 40 |
| (20, 40) | subMap(20, false, 40, false) | 30 |

Cặp boolean không nói “sắp tăng hay giảm”. Nó chỉ lựa chọn tính bao gồm của hai biên trong thứ tự mà map đã có.

Nếu lời gọi nhiều tham số khó đọc trong một đoạn mã quan trọng, có thể đặt tên biến diễn đạt ý định:

~~~java
boolean includeStart = true;
boolean includeEnd = false;

NavigableMap<Integer, String> selected =
        map.subMap(start, includeStart, end, includeEnd);
~~~

Đoạn trên là minh họa với map, start và end đã được xác định. Tên biến phải khớp nghiệp vụ, không chỉ thay true/false bằng những tên chung chung khó hiểu hơn.

### 3.3. Viết điều kiện so sánh khi còn nghi ngờ

Gọi c là quy tắc so sánh của map. Có thể đọc khoảng [a, b) thành:

~~~text
c(k, a) >= 0
và
c(k, b) < 0
~~~

Khoảng (a, b] chỉ thay hai dấu ở biên:

~~~text
c(k, a) > 0
và
c(k, b) <= 0
~~~

Đây là công cụ lập luận, không bắt buộc phải viết một vòng lọc thủ công thay cho API. Nó giúp xác định chính xác khóa bằng đầu hoặc cuối có được giữ hay không.

## 4. Biên không cần tồn tại, nhưng phải hợp lệ

### 4.1. Biên là mốc so sánh

Với khóa 10, 20, 30, 40, 50, `subMap(15, true, 45, false)` chọn 20, 30, 40. Không có 15 hay 45 trong map vẫn không làm lời gọi mất ý nghĩa.

Cũng không cần tự tìm khóa gần nhất trước khi gọi subMap. Các mốc đã định nghĩa được vùng so sánh mà ứng dụng muốn.

### 4.2. Hai biên bằng nhau

Trên map gốc không bị giới hạn phạm vi, với các biên và kiểu khóa hợp lệ:

- `subMap(a, true, a, true)` có thể chứa khóa so sánh bằng a nếu tồn tại.
- Nếu ít nhất một đầu bị loại, không có khóa nào thỏa đồng thời hai điều kiện khi hai mốc so sánh bằng nhau.
- `subMap(a, a)` mặc định là khoảng rỗng.

Ví dụ:

~~~text
subMap(20, true, 20, true)  -> 20
subMap(20, true, 20, false) -> rỗng
subMap(25, true, 25, true)  -> rỗng nếu map không có khóa 25
~~~

“Có thể chứa khóa ở mốc” không có nghĩa API tự tạo một khóa mới khi chưa có. Nó chỉ cho phép vị trí đó thuộc phạm vi.

### 4.3. Biên đảo chiều là lỗi yêu cầu

Nếu fromKey đứng sau toKey theo comparator, subMap ném IllegalArgumentException. Không nên tự coi đó là vùng rỗng rồi nuốt lỗi, trừ khi tầng ứng dụng đã có chính sách chuẩn hóa yêu cầu rõ ràng.

“Vùng hợp lệ nhưng hiện không có dữ liệu” khác với “hai biên không hợp lệ”. Hai trạng thái thường cần phản hồi khác nhau ở tầng nghiệp vụ.

### 4.4. Null không có nghĩa vô hạn

Không truyền null để ngụ ý “không có biên”. TreeMap tự nhiên không cho phép khóa hoặc biên null; có thể gặp NullPointerException. Comparator riêng chỉ cho phép null khi chính nó định nghĩa cách so sánh null.

Ngay cả khi null hợp lệ, nó là một mốc theo comparator, không tự có nghĩa âm vô hạn hay dương vô hạn cho mọi map. Muốn chỉ giới hạn một đầu, headMap hoặc tailMap đã biểu diễn đúng nhu cầu.

Khóa và biên còn phải so sánh được theo quy tắc của map. Một lỗi kiểu hoặc comparator không phù hợp khác với một khoảng rỗng hợp lệ.

## 5. Bao gồm biên theo compare, không theo equals

Giả sử comparator coi chữ hoa và chữ thường tương đương. Khóa `"b"` được so sánh bằng 0 với mốc `"B"`.

Khi ấy:

~~~text
headMap("B", false) -> loại khóa "b"
headMap("B", true)  -> có thể gồm khóa "b"
~~~

Inclusive xét kết quả so sánh bằng 0, không đòi hỏi đối tượng biên phải là đúng cùng đối tượng khóa hoặc bằng nhau theo một phép so khác.

Ví dụ comparator không phân biệt hoa thường trong chương trình chỉ dùng để minh họa cách xét biên. Khi thiết kế khóa thực, cần cân nhắc sự nhất quán giữa comparator và equals để map tuân thủ hợp đồng dữ liệu mong muốn. [Java 17: Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)

Các thành phần khóa và trạng thái dùng trong comparator phải ổn định trong quá trình dùng map. Một quy tắc tự thay đổi theo biến bên ngoài không tạo một cửa sổ khóa đáng tin chỉ bằng cách gọi lại headMap.

## 6. View sống và tác động lên map gốc

### 6.1. Thay đổi hai chiều

Bắt đầu với gốc 10, 20, 30, 40, 50 và view [20, 40):

~~~text
gốc:  10, 20, 30, 40, 50
view: 20, 30

gốc thêm 25:
view thấy 20, 25, 30

view thêm 35:
gốc có 10, 20, 25, 30, 35, 40, 50

view xóa 20:
gốc còn 10, 25, 30, 35, 40, 50
~~~

Không cần lấy lại view để thấy những ánh xạ mới nằm trong phạm vi. Nhưng một List khóa đã sao chép trước đó là cấu trúc khác và không tự được thêm phần tử như view.

### 6.2. Đọc ngoài vùng và chèn ngoài vùng khác nhau

Trên view [20, 40), khóa 40 ngoài phạm vi dù map gốc đang chứa 40.

Với dữ liệu không có giá trị null trong ví dụ:

- Get 40 qua view không tìm thấy ánh xạ trong view.
- Remove 40 qua view không loại ánh xạ ngoài vùng ở gốc.
- Put 40 qua view bị từ chối bằng IllegalArgumentException.

Không lấy quyền của map gốc để suy ra view cũng có quyền ghi mọi khóa. View chỉ đại diện phần dữ liệu thuộc phạm vi của nó.

### 6.3. Clear view là xóa vùng

Sau khi view chứa 25, 30, 35, gọi clear trên view sẽ xóa ba ánh xạ ấy khỏi gốc, để lại 10, 40, 50.

Đây là một cách diễn đạt thao tác xóa theo khoảng rất trực tiếp. Cần hiểu đây là thay đổi dữ liệu, không phải chỉ dọn một đối tượng tạm giữ kết quả lọc.

Không vừa giữ iterator cũ trên cấu trúc chung vừa tùy ý thêm, xóa qua map hoặc view khác. Các đối tượng nhìn khác tên vẫn có thể cùng dựa trên một dữ liệu gốc.

### 6.4. Bản sao khi cần dữ liệu độc lập

Đoạn minh họa:

~~~java
NavigableMap<Integer, String> window =
        source.subMap(20, true, 40, false);

TreeMap<Integer, String> independent = new TreeMap<>(window);
~~~

Constructor nhận SortedMap phù hợp với kiểu NavigableMap nguồn nên bản TreeMap mới giữ thứ tự của nguồn. Cấu trúc ánh xạ độc lập với window về các thao tác thêm hoặc xóa sau đó.

Đây vẫn là sao chép ánh xạ, không tự sao chép sâu đối tượng khóa và giá trị. Nếu giá trị là đối tượng có thể thay đổi, cần chọn mức độ sao chép theo yêu cầu.

Nếu nguồn có sửa đổi cạnh tranh, tạo bản sao cũng cần chính sách đọc nguồn phù hợp. Một cấu trúc kết quả độc lập không tự giải quyết thời điểm đang thu thập dữ liệu từ nguồn.

## 7. View lồng nhau: không thể mở rộng quyền biên

### 7.1. Giới hạn của view cha vẫn còn

Giả sử outer là [20, 40). Gọi một phương thức vùng trên outer không được tự mở rộng nó để lấy thêm 10 hoặc bao gồm 40.

Ví dụ:

~~~text
outer.headMap(40, false) -> giữ phần trước 40, vẫn loại 40: hợp lệ
outer.headMap(40, true)  -> muốn gồm 40: bị từ chối
outer.tailMap(10, false) -> đặt biên thấp ngoài phạm vi outer: bị từ chối
~~~

Không nên tưởng một yêu cầu vượt biên luôn được API tự cắt về giao với phạm vi cha. Các hợp đồng vùng quy định việc tạo view với biên không hợp lệ có thể bị từ chối.

### 7.2. Biên bằng đầu bị loại không phải lúc nào cũng bị cấm

Một điểm cần đọc kỹ là cờ bao gồm. Trên outer [20, 40), `tailMap(40, false)` tạo vùng rỗng hợp lệ trong TreeMap: nó vẫn loại mốc 40 và không lấy bất kỳ khóa nào ngoài quyền của outer.

Trong khi đó, yêu cầu gồm 40 sẽ đòi một mốc cha đã loại. Vì vậy, chỉ hỏi “40 có xuất hiện trong outer không?” chưa đủ để quyết định mọi lời gọi lồng có biên 40.

Cách lập luận là xét cả giá trị mốc và quyền bao gồm mốc. Chương trình đầy đủ kiểm chứng cặp trường hợp giữ biên trên bị loại và yêu cầu bao gồm nó.

### 7.3. Rỗng không làm mất các ràng buộc tạo vùng

Một view có thể rỗng vì dữ liệu chưa có khóa trong phạm vi. Nó vẫn mang giới hạn biên; không được dùng việc rỗng để suy ra có thể chèn hoặc tạo view con tùy ý.

Tách hai khái niệm:

- Miền khóa mà view cho phép đại diện.
- Những ánh xạ đang thực sự tồn tại trong miền ấy.

Khi kiểm tra lỗi biên, phải xét miền cho phép, không chỉ nhìn danh sách khóa đang in ra.

## 8. Thứ tự đảo: head không còn đồng nghĩa số nhỏ hơn

### 8.1. Đọc chiều comparator trước

Với view đảo của các khóa 10, 20, 30, 40, 50:

~~~text
50, 40, 30, 20, 10
~~~

Head là phần đứng trước mốc theo thứ tự này. Tail là phần từ hoặc sau mốc theo thứ tự này.

| Lời gọi trên view đảo | Khóa kết quả theo thứ tự view |
|---|---|
| headMap(30, true) | 50, 40, 30 |
| tailMap(30, false) | 20, 10 |
| subMap(40, true, 20, false) | 40, 30 |

Không có lỗi đảo dấu ngẫu nhiên. Quy tắc so sánh của view đã đảo nên quan hệ trước/sau thay đổi tương ứng.

### 8.2. From và to phải đúng chiều map

Trên view đảo, 40 đứng trước 20 nên cặp from = 40, to = 20 hợp lệ. Truyền from = 20, to = 40 theo thói quen số tăng làm from đứng sau to trong comparator của view và bị từ chối.

Đừng tự sắp hai mốc bằng Math.min và Math.max khi hàm được thiết kế nhận một sorted map bất kỳ. Cách đó ngầm áp thứ tự số tăng, có thể trái comparator nguồn.

View đảo vẫn dùng chung dữ liệu. Tác động thêm, xóa hợp lệ qua vùng của view đảo vẫn đi vào gốc, không có lớp dữ liệu độc lập chỉ vì chiều duyệt khác.

## 9. Chọn biên từ nghiệp vụ, không tạo “mốc giả”

### 9.1. Dùng inclusive thay vì cộng một đơn vị

Muốn bao gồm b, nên dùng cờ bao gồm b thay vì luôn đổi thành “nhỏ hơn b + 1”.

Cách cộng một đơn vị có nhiều giả định: khóa phải có phép cộng, miền phải rời rạc theo đúng đơn vị đó và phép cộng không được tràn. Với Integer.MAX_VALUE, cộng thêm một là sai trong kiểu int.

Dạng `subMap(max, true, max, true)` biểu diễn khoảng một mốc ở giá trị tối đa mà không cần tạo một số kế tiếp. Với chuỗi hoặc khóa nghiệp vụ phức tạp, khái niệm “b + 1” thậm chí có thể không tồn tại.

### 9.2. Khoảng một ngày

Nếu khóa là LocalDateTime của thời gian địa phương trong ví dụ, muốn lấy các sự kiện thuộc ngày d:

~~~text
start = đầu ngày d
end   = đầu ngày d + 1 ngày theo lịch
chọn [start, end)
~~~

Sự kiện đúng tại start được giữ. Sự kiện đúng tại end thuộc ngày tiếp theo và bị loại. Không cần đoán một “giây cuối ngày” rồi bỏ sót phần độ chính xác nhỏ hơn giây.

Mã minh họa một phần:

~~~java
LocalDateTime start = day.atStartOfDay();
LocalDateTime end = day.plusDays(1).atStartOfDay();

NavigableMap<LocalDateTime, String> daily =
        events.subMap(start, true, end, false);
~~~

Ví dụ đang dùng LocalDate và LocalDateTime trong phạm vi ngày hợp lệ; một hệ thống lưu mốc theo kiểu thời gian khác cần xác định biên theo mô hình thời gian của nó. [Java 17: LocalDate.plusDays và atStartOfDay](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/LocalDate.html)

### 9.3. Ma trận kiểm tra biên

Trước khi dùng trong tác vụ thật, chọn các dữ liệu thử:

| Vị trí khóa | Câu hỏi |
|---|---|
| Ngay trước from | Bị loại chưa? |
| Bằng from | Đúng theo fromInclusive chưa? |
| Nằm giữa | Được giữ chưa? |
| Bằng to | Đúng theo toInclusive chưa? |
| Ngay sau to | Bị loại chưa? |

Bổ sung trường hợp không có khóa ở biên, hai biên bằng nhau và comparator đảo nếu hàm cho phép thứ tự tùy chọn. Các trường hợp bằng mốc là nơi khác biệt giữa hai boolean thật sự xuất hiện.

## 10. Chương trình Java đầy đủ

Lưu toàn bộ mã sau vào `TreeMapRangeBoundsDemo.java`. Chương trình không cần thư viện ngoài.

Nó dùng các nhóm dữ liệu riêng để việc clear hoặc xóa ở một nhóm không ảnh hưởng các nhóm còn lại. Các phép kiểm tra xác nhận view sống, bản sao độc lập, lỗi biên và dữ liệu trong khoảng ngày.

~~~java
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public class TreeMapRangeBoundsDemo {
    private static TreeMap<Integer, String> sample() {
        TreeMap<Integer, String> map = new TreeMap<>();
        for (int key : new int[] {10, 20, 30, 40, 50}) {
            map.put(key, "v" + key);
        }
        return map;
    }

    private static <K, V> List<K> keys(java.util.Map<K, V> map) {
        return new ArrayList<>(map.keySet());
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Unexpected range result");
        }
    }

    private static boolean rejected(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    public static void main(String[] args) {
        TreeMap<Integer, String> map = sample();
        System.out.println("default/head30=" + keys(map.headMap(30)));
        System.out.println("default/tail30=" + keys(map.tailMap(30)));
        System.out.println("default/sub20to40=" + keys(map.subMap(20, 40)));
        System.out.println("flags/head30Inclusive="
                + keys(map.headMap(30, true)));
        System.out.println("flags/tail30Exclusive="
                + keys(map.tailMap(30, false)));
        System.out.println("flags/open20to40="
                + keys(map.subMap(20, false, 40, false)));
        System.out.println("flags/closed20to40="
                + keys(map.subMap(20, true, 40, true)));
        System.out.println("bounds/notPresent="
                + keys(map.subMap(15, true, 45, false)));
        System.out.println("equal/closed="
                + keys(map.subMap(20, true, 20, true)));
        System.out.println("equal/halfOpen="
                + keys(map.subMap(20, true, 20, false)));
        System.out.println("equal/missing="
                + keys(map.subMap(25, true, 25, true)));

        TreeMap<Integer, String> parent = sample();
        NavigableMap<Integer, String> window =
                parent.subMap(20, true, 40, false);
        TreeMap<Integer, String> independent = new TreeMap<>(window);
        parent.put(25, "v25");
        window.put(35, "v35");
        window.remove(20);
        require(keys(window).equals(List.of(25, 30, 35)));
        require(keys(parent).equals(List.of(10, 25, 30, 35, 40, 50)));
        require(keys(independent).equals(List.of(20, 30)));
        System.out.println("live/window=" + keys(window));
        System.out.println("live/parent=" + keys(parent));
        System.out.println("live/copy=" + keys(independent));
        boolean outside = rejected(() -> window.put(40, "not-allowed"));
        require(outside && parent.get(40).equals("v40"));
        System.out.println("live/outsidePutRejected=" + outside);
        System.out.println("live/outsideGet=" + window.get(40)
                + ", outsideRemove=" + window.remove(40));
        window.clear();
        require(keys(parent).equals(List.of(10, 40, 50)));
        System.out.println("live/parentAfterClear=" + keys(parent));

        NavigableMap<Integer, String> outer =
                sample().subMap(20, true, 40, false);
        System.out.println("nested/sameExcludedUpper="
                + keys(outer.headMap(40, false)));
        boolean includeUpper = rejected(() -> outer.headMap(40, true));
        boolean expandLower = rejected(() -> outer.tailMap(10, false));
        require(includeUpper && expandLower);
        System.out.println("nested/includeUpperRejected=" + includeUpper);
        System.out.println("nested/emptyAtUpper="
                + keys(outer.tailMap(40, false)));
        System.out.println("nested/expandLowerRejected=" + expandLower);

        NavigableMap<Integer, String> descending = sample().descendingMap();
        System.out.println("descending/head30Inclusive="
                + keys(descending.headMap(30, true)));
        System.out.println("descending/tail30Exclusive="
                + keys(descending.tailMap(30, false)));
        System.out.println("descending/sub40to20="
                + keys(descending.subMap(40, true, 20, false)));
        boolean wrongDirection =
                rejected(() -> descending.subMap(20, true, 40, false));
        require(wrongDirection);
        System.out.println("descending/wrongDirectionRejected="
                + wrongDirection);

        TreeMap<String, Integer> words =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        words.put("a", 1);
        words.put("b", 2);
        words.put("c", 3);
        System.out.println("comparison/headBExclusive="
                + keys(words.headMap("B", false)));
        System.out.println("comparison/headBInclusive="
                + keys(words.headMap("B", true)));

        TreeMap<Integer, String> maximum = new TreeMap<>();
        maximum.put(Integer.MAX_VALUE, "maximum");
        System.out.println("max/closed="
                + keys(maximum.subMap(Integer.MAX_VALUE, true,
                        Integer.MAX_VALUE, true)));

        LocalDate day = LocalDate.of(2026, 10, 20);
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime nextStart = day.plusDays(1).atStartOfDay();
        TreeMap<LocalDateTime, String> events = new TreeMap<>();
        events.put(start.minusNanos(1), "previous");
        events.put(start, "start");
        events.put(start.plusHours(8), "morning");
        events.put(nextStart, "next");
        NavigableMap<LocalDateTime, String> today =
                events.subMap(start, true, nextStart, false);
        require(new ArrayList<>(today.values())
                .equals(List.of("start", "morning")));
        System.out.println("day/values=" + new ArrayList<>(today.values()));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 TreeMapRangeBoundsDemo.java
java TreeMapRangeBoundsDemo
~~~

Stdout của lần chạy kiểm chứng:

~~~text
default/head30=[10, 20]
default/tail30=[30, 40, 50]
default/sub20to40=[20, 30]
flags/head30Inclusive=[10, 20, 30]
flags/tail30Exclusive=[40, 50]
flags/open20to40=[30]
flags/closed20to40=[20, 30, 40]
bounds/notPresent=[20, 30, 40]
equal/closed=[20]
equal/halfOpen=[]
equal/missing=[]
live/window=[25, 30, 35]
live/parent=[10, 25, 30, 35, 40, 50]
live/copy=[20, 30]
live/outsidePutRejected=true
live/outsideGet=null, outsideRemove=null
live/parentAfterClear=[10, 40, 50]
nested/sameExcludedUpper=[20, 30]
nested/includeUpperRejected=true
nested/emptyAtUpper=[]
nested/expandLowerRejected=true
descending/head30Inclusive=[50, 40, 30]
descending/tail30Exclusive=[20, 10]
descending/sub40to20=[40, 30]
descending/wrongDirectionRejected=true
comparison/headBExclusive=[a]
comparison/headBInclusive=[a, b]
max/closed=[2147483647]
day/values=[start, morning]
~~~

Mã đã được biên dịch với `--release 17` và chạy thành công. Tùy chọn release xác định mức API và mã đích tương thích; nó không khẳng định JVM thực thi là đúng bản JDK 17.

## 11. Giải thích kết quả quan trọng

### 11.1. Default, flags và equal

Ba dòng default đối chiếu ba mặc định khác nhau. Nhóm flags cho thấy boolean thay việc có lấy đúng mốc hay không, không thay thứ tự map.

Nhóm equal phân biệt vùng một mốc có dữ liệu, vùng loại một đầu nên rỗng và vùng một mốc hợp lệ nhưng chưa có ánh xạ ở mốc ấy. Rỗng không luôn là lỗi.

### 11.2. Live

Bản copy được tạo lúc window chứa 20 và 30. Gốc thêm 25, view thêm 35 rồi xóa 20; window và gốc cùng phản ánh trạng thái mới, copy giữ các ánh xạ đã sao chép.

Put 40 qua window bị từ chối. Get hoặc remove 40 qua window không đi xuyên giới hạn để lấy hoặc xóa ánh xạ 40 ở gốc. Clear window sau đó chỉ loại vùng hiện tại, để gốc còn 10, 40, 50.

### 11.3. Nested

HeadMap ở biên trên 40 với inclusive false hợp lệ, nhưng đổi thành true bị từ chối. TailMap tại 40 với false cho vùng rỗng; tailMap từ 10 bị từ chối vì nằm ngoài giới hạn cha.

Các kết quả này nhắc rằng miền khóa cho phép và tập ánh xạ đang có là hai đối tượng suy nghĩ khác nhau.

### 11.4. Descending và comparison

Nhóm descending đọc head, tail và from/to theo chiều 50 xuống 10. Trường hợp truyền biên sai chiều bị từ chối bằng IllegalArgumentException.

Nhóm comparison dùng mốc B trên khóa b để cho thấy inclusive phụ thuộc phép so sánh. Chương trình không dựa vào sự bằng nhau theo equals giữa hai chuỗi để lựa chọn biên.

### 11.5. Max và day

Khoảng đóng tại Integer.MAX_VALUE không cần cộng một đơn vị. Khoảng ngày giữ sự kiện đúng đầu ngày và lúc sáng, loại sự kiện ngay trước đầu ngày cùng sự kiện đúng đầu ngày kế tiếp.

Những ví dụ này cho thấy cờ biên giải quyết trực tiếp yêu cầu thay vì cần mẹo tạo một khóa kế tiếp giả định.

## 12. Lỗi thường gặp

| Lỗi | Cách sửa |
|---|---|
| HeadMap mặc định có gồm mốc | Dạng không boolean loại mốc kết thúc |
| TailMap mặc định loại mốc | Dạng không boolean gồm mốc bắt đầu |
| SubMap hai đối số gồm cả hai đầu | Nó là [from, to) |
| Biên phải có trong map | Biên là mốc so sánh |
| Hai biên bằng nhau luôn là lỗi | Có thể là vùng hợp lệ một mốc hoặc rỗng |
| Biên sai chiều cứ trả vùng rỗng | Đó có thể là IllegalArgumentException |
| View là bản sao để sửa thử | Thao tác sửa hợp lệ tác động vào gốc |
| Clear view chỉ dọn đối tượng tạm | Nó xóa các ánh xạ trong vùng khỏi gốc |
| Gốc có khóa thì view ghi được khóa đó | View còn bị ràng buộc bởi phạm vi |
| View con tự cắt mọi biên về vùng cha | Yêu cầu vượt giới hạn có thể bị từ chối |
| Head luôn là các số nhỏ hơn | Phải đọc theo comparator, kể cả thứ tự đảo |
| Bao gồm b phải đổi thành b + 1 | Dùng inclusive; không giả định có khóa kế tiếp |
| Null biểu diễn không giới hạn | Null tuân thủ quy tắc so sánh, không là ký hiệu vô hạn |
| Sao chép view làm đối tượng giá trị bất biến | Sao chép ánh xạ không tự sao chép sâu |

## 13. Thực hành và lời giải

### Bài 1. Ba mặc định

Map có 5, 10, 15, 20, 25. Hãy cho khóa trong headMap(15), tailMap(15) và subMap(10, 20).

**Lời giải:** Head là 5, 10. Tail là 15, 20, 25. Sub là 10, 15 vì đầu dưới được gồm còn đầu trên bị loại.

### Bài 2. Hai đầu đều có dấu bằng

Cần chọn các khóa từ 10 đến 20, bao gồm cả hai mốc. Viết lời gọi và giải thích vì sao không cần dùng 21.

**Lời giải:** SubMap(10, true, 20, true). Cờ cuối biểu diễn trực tiếp việc gồm 20. Dùng 21 giả định một miền số rời rạc và có thể gây lỗi ở giới hạn kiểu hoặc không áp dụng được cho loại khóa khác.

### Bài 3. Mốc vắng và mốc bằng nhau

Với khóa 10, 20, 30, 40, kết quả subMap(15, true, 35, false) là gì? SubMap(20, true, 20, true) và subMap(20, false, 20, true) khác nhau thế nào?

**Lời giải:** Khoảng thứ nhất chứa 20, 30 dù không có hai mốc 15 và 35. Khoảng đóng một mốc chứa 20; khoảng loại đầu dưới với cùng hai mốc là rỗng.

### Bài 4. View ghi vào đâu?

Gốc có 10, 20, 30, 40, 50; view là [20, 40). Thêm 35 qua view, rồi clear view. Gốc còn khóa nào? Có thể put 40 qua view trước khi clear không?

**Lời giải:** Clear loại 20, 30 và 35 khỏi gốc, để lại 10, 40, 50. Put 40 qua view bị từ chối do đầu trên loại 40, dù gốc có sẵn khóa ấy.

### Bài 5. View lồng nhau

Outer là [20, 40). Phân biệt outer.headMap(40, false), outer.headMap(40, true) và outer.tailMap(10, false).

**Lời giải:** Trường hợp đầu giữ giới hạn loại 40 nên hợp lệ. Trường hợp thứ hai đòi gồm biên mà cha đã loại nên bị từ chối. Trường hợp thứ ba đặt mốc ngoài giới hạn cha; không được mặc nhiên cắt về khoảng của outer.

### Bài 6. Thứ tự đảo

View đảo có 50, 40, 30, 20, 10. HeadMap(30, true), tailMap(30, false) và subMap(40, true, 20, false) chứa gì?

**Lời giải:** Lần lượt là 50, 40, 30; 20, 10; và 40, 30. Các quan hệ trước/sau theo comparator đảo, không theo phép so số tăng dần.

### Bài 7. Chọn một ngày

Các sự kiện nằm ở ngay trước đầu ngày, đúng đầu ngày, giữa ngày và đúng đầu ngày kế tiếp. Cần vùng ngày hiện tại. Dùng khoảng nào và những sự kiện nào được giữ?

**Lời giải:** Dùng [đầu ngày, đầu ngày kế tiếp). Giữ đúng đầu ngày và giữa ngày; loại sự kiện trước đầu ngày cùng sự kiện tại đầu ngày kế tiếp. Không cần tự tính giây cuối cùng của ngày.

### Bài 8. Một vùng rỗng vẫn có giới hạn

View [100, 200) hiện không có ánh xạ. Có thể put 50 qua view vì “view chưa có dữ liệu” không? Bản copy của view có còn bị giới hạn [100, 200) giống view không?

**Lời giải:** Không thể put 50 qua view; miền cho phép không phụ thuộc việc đang rỗng. Một TreeMap mới sao chép các ánh xạ từ view có cấu trúc map riêng, không tiếp tục là cửa sổ giới hạn của gốc chỉ vì được tạo từ view. Comparator được giữ qua constructor SortedMap, nhưng quyền phạm vi của view không biến thành giới hạn chèn của một TreeMap mới.

## 14. Tự đọc lại lời gọi vùng

Trước khi chạy, hãy diễn giải lời gọi thành một câu đầy đủ: “theo thứ tự này, lấy khóa sau hoặc bằng mốc nào, trước hay bằng mốc nào, và kết quả là view của dữ liệu nào”.

Nếu câu ấy rõ, các cờ boolean bớt trở thành điều phải học thuộc. HeadMap, tailMap và subMap là cách biểu diễn miền khóa; chọn đúng biên và hiểu dữ liệu dùng chung là hai phần cùng cần để dùng chúng đúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tree-map-007">descendingMap, navigableKeySet và view đảo thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# TreeMap: descendingMap, navigableKeySet và view đảo thứ tự

## 1. Một tập mapping có thể được nhìn theo nhiều chiều

Giả sử chương trình giữ các phiên bản tài liệu theo số thứ tự 10, 20, 30. Ta muốn tra theo số, xem từ cũ đến mới ở một màn hình và xem từ mới đến cũ ở màn hình khác. Không nhất thiết phải tạo hai map rồi tự đồng bộ mỗi khi dữ liệu thay đổi.

`TreeMap` đã giữ khóa theo một thứ tự xác định. **Khung nhìn (view)** cho phép thao tác với cùng dữ liệu qua một hướng hoặc một phạm vi khác. Hai API chính trong bài là:

- `descendingMap()`: view của cả mapping theo thứ tự đảo.
- `navigableKeySet()`: view của các khóa, có các phép tìm khóa lân cận và làm việc với hai đầu.

Hai API liên quan giúp nối các cách nhìn là `descendingKeySet()` trên map và `descendingSet()` trên `NavigableSet`. Chúng không tạo một kho dữ liệu độc lập chỉ vì kết quả có kiểu map hoặc set.

Với map dùng thứ tự tự nhiên của số nguyên:

```text
Dữ liệu gốc:                   10=A, 20=B, 30=C
map duyệt khóa:                10 → 20 → 30
map.descendingMap duyệt khóa:  30 → 20 → 10
map.navigableKeySet:           10 → 20 → 30
map.descendingKeySet:          30 → 20 → 10
```

Điều cần hiểu không chỉ là đảo chiều dòng in. Khi thao tác qua view đảo, **đầu/cuối, trước/sau, khóa lân cận và cận phạm vi đều phải được đọc theo thứ tự của view**. Khi xóa qua view sửa được, mapping trong map gốc cũng bị xóa.

Các ví dụ trong bài dùng Java 17 trở lên, chỉ dựa trên các API điều hướng đã có của `TreeMap` và `NavigableMap`.

## 2. Nhận diện đúng kiểu kết quả

| Lời gọi | Kiểu kết quả | Dữ liệu nhìn thấy | Thứ tự |
|---|---|---|---|
| `map.descendingMap()` | `NavigableMap<K,V>` | Cả khóa và giá trị | Đảo so với map nhận lời gọi |
| `map.navigableKeySet()` | `NavigableSet<K>` | Chỉ khóa | Cùng thứ tự với map |
| `map.descendingKeySet()` | `NavigableSet<K>` | Chỉ khóa | Đảo so với map |
| `keys.descendingSet()` | `NavigableSet<K>` | Các phần tử của set/view khóa | Đảo so với set nhận lời gọi |

`NavigableMap` mở rộng khả năng của map có thứ tự: ngoài tra chính xác theo khóa, nó còn tìm khóa hoặc entry ngay trước/sau một mốc. `NavigableSet` làm các phép tương tự trên phần tử đơn lẻ, nên ở view khóa, kết quả là khóa chứ không phải entry.

`keySet()` của `TreeMap` vẫn được duyệt có thứ tự. Khác biệt hữu ích của `navigableKeySet()` là kiểu trả về công bố các phương thức như `lower`, `floor`, `ceiling`, `higher`, `pollFirst` và `descendingSet`.

Đoạn sau chỉ minh họa bên trong một phương thức, không phải tệp Java độc lập:

```java
TreeMap<Integer, String> map = new TreeMap<>();
NavigableMap<Integer, String> down = map.descendingMap();
NavigableSet<Integer> keys = map.navigableKeySet();
NavigableSet<Integer> downKeys = map.descendingKeySet();
```

Không khai báo `TreeMap<Integer,String> down = map.descendingMap()`: API trả về `NavigableMap`, không hứa rằng view là một đối tượng `TreeMap` độc lập. Dùng đúng interface vừa đủ giúp thể hiện khả năng thật của kết quả.

## 3. “Giảm dần” là đảo thứ tự đang có

### 3.1. Không cố định là số lớn đến số nhỏ

`TreeMap` có thể dùng thứ tự tự nhiên hoặc một comparator riêng. `descendingMap()` đảo **thứ tự của map hiện tại**, không áp đặt một quy tắc chung cho mọi kiểu khóa.

Nếu map gốc theo thứ tự số tăng dần, view đảo đi từ lớn xuống nhỏ. Nhưng nếu map gốc đã được tạo với comparator số giảm dần, view đảo của nó lại đi từ nhỏ lên lớn. Với khóa phức hợp, thứ tự đảo cũng dựa trên comparator của khóa đó.

Do đó, trong bài các phát biểu “khóa lớn nhất” và “khóa nhỏ nhất theo giá trị số” chỉ được áp dụng khi đã nêu rõ map gốc dùng thứ tự tự nhiên của số nguyên. Trong trường hợp tổng quát, hãy nói đầu/cuối hoặc trước/sau theo comparator.

Tài liệu [NavigableMap — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableMap.html) mô tả view đảo có thứ tự ngược với map gốc. Đảo hai lần cho một view có ý nghĩa tương đương map ban đầu; không cần dùng danh tính đối tượng `==` để định nghĩa tính chất này.

### 3.2. Đảo chiều không thay mapping hoặc tạo nhóm khóa mới

View vẫn chứa cùng các cặp khóa–giá trị. `get(key)` không trả một giá trị khác chỉ vì đang dùng chiều đảo. Các nhóm khóa tương đương theo so sánh cũng không bị tách thành khóa mới: đảo thứ tự giữ nguyên quan hệ so sánh bằng 0.

`down.put(key, value)` thêm hoặc cập nhật mapping trong map gốc. Vị trí duyệt sau đó được quyết định bởi thứ tự khóa, không phải vị trí thực hiện lời gọi hay việc map được gọi là “down”. Không thể thêm một khóa số 25 vào đầu tùy ý của một TreeMap tự nhiên vốn có 10, 20, 30; khóa đó nằm giữa 20 và 30 trong chiều gốc.

`down.values()` và `down.entrySet()` duyệt theo thứ tự khóa của view đảo. Đó không phải việc sắp giá trị V theo một comparator mới.

## 4. View đảo được liên kết hai chiều với map gốc

### 4.1. Thay đổi ở một đường nhìn thấy ở đường còn lại

Với `TreeMap` sửa được thông thường, các thao tác trên `descendingMap()` tác động đến cùng dữ liệu:

| Thao tác | Ảnh hưởng đến map gốc |
|---|---|
| `down.put(k, v)` | Thêm hoặc cập nhật mapping k |
| `down.remove(k)` | Loại mapping k nếu có |
| `down.clear()` khi down là view đảo toàn map | Xóa toàn bộ mapping của map gốc |
| `down.pollFirstEntry()` | Loại entry đầu view, tức cuối gốc |
| `down.pollLastEntry()` | Loại entry cuối view, tức đầu gốc |

Ngược lại, thêm hoặc loại mapping qua map gốc lập tức được phản ánh khi quan sát view. Không có thao tác “làm mới view” riêng phải gọi sau mỗi put.

### 4.2. Xem hai đầu và lấy ra là hai việc khác nhau

`firstKey()`/`lastKey()` chỉ xem khóa đầu/cuối, không loại mapping. `firstEntry()`/`lastEntry()` xem cả mapping ở hai đầu. Các phương thức có `poll` vừa lấy kết quả vừa loại mapping.

Trong map gốc có khóa `[10, 20, 30]`, view đảo là `[30, 20, 10]`. `down.firstKey()` là 30; `down.pollFirstEntry()` lấy entry 30 và khiến map gốc không còn khóa 30. Nếu chỉ muốn hiển thị mục đầu tiên, dùng phép xem để tránh xóa ngoài ý định.

## 5. Chương trình 1: thao tác qua hai chiều

Lưu chương trình đầy đủ vào `TreeMapDescendingViewDemo.java`. Map gốc dùng thứ tự tự nhiên của `Integer`.

```java
import java.util.NavigableMap;
import java.util.TreeMap;

public class TreeMapDescendingViewDemo {
    public static void main(String[] args) {
        TreeMap<Integer, String> map = new TreeMap<>();
        map.put(10, "A");
        map.put(20, "B");
        map.put(30, "C");
        NavigableMap<Integer, String> down = map.descendingMap();
        System.out.println("original=" + map);
        System.out.println("descending=" + down);
        System.out.println("descendingEnds=" + down.firstKey() + "," + down.lastKey());
        down.put(25, "X");
        down.remove(10);
        map.put(5, "D");
        System.out.println("sharedOriginal=" + map);
        System.out.println("sharedDescending=" + down);
        System.out.println("lookup20=" + down.get(20));
        System.out.println("descendingPollFirst=" + down.pollFirstEntry());
        System.out.println("descendingPollLast=" + down.pollLastEntry());
        System.out.println("remainingOriginal=" + map);
        System.out.println("remainingDescending=" + down);
        System.out.println("twiceDescending=" + down.descendingMap().navigableKeySet());
        System.out.println("equalMappings=" + map.equals(down));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TreeMapDescendingViewDemo.java
java TreeMapDescendingViewDemo
```

Stdout:

```text
original={10=A, 20=B, 30=C}
descending={30=C, 20=B, 10=A}
descendingEnds=30,10
sharedOriginal={5=D, 20=B, 25=X, 30=C}
sharedDescending={30=C, 25=X, 20=B, 5=D}
lookup20=B
descendingPollFirst=30=C
descendingPollLast=5=D
remainingOriginal={20=B, 25=X}
remainingDescending={25=X, 20=B}
twiceDescending=[20, 25]
equalMappings=true
```

### 5.1. Thêm theo vị trí sắp thứ tự, không theo thời điểm chèn

Ban đầu gốc là `{10=A, 20=B, 30=C}`, view là `{30=C, 20=B, 10=A}`. Gọi `down.put(25, "X")` thêm 25 vào vị trí phù hợp trong cùng cây. `down.remove(10)` loại 10 khỏi gốc. Gọi `map.put(5, "D")` thêm 5 qua gốc và view cũng nhìn thấy nó.

Vì vậy gốc trở thành `[5, 20, 25, 30]`, view trở thành `[30, 25, 20, 5]`. `lookup20=B` xác nhận tra theo khóa không bị đổi ý nghĩa giá trị.

### 5.2. Poll trên view đảo lấy đầu nào của gốc?

`descendingPollFirst=30=C` loại khóa lớn nhất theo thứ tự tự nhiên đang xét. Sau đó `descendingPollLast=5=D` loại khóa nhỏ nhất. Map gốc còn `[20, 25]`, view còn `[25, 20]`.

`twiceDescending=[20, 25]` quay về cùng chiều gốc. `equalMappings=true` không có nghĩa hai chiều duyệt giống nhau: phép bằng nhau của map xét các mapping, không xét thứ tự duyệt. Khi cần kiểm tra thứ tự, hãy quan sát một danh sách khóa theo thứ tự, không chỉ dùng `Map.equals`.

## 6. Các phép tìm lân cận đổi nghĩa theo chiều

### 6.1. Ý nghĩa của lower, floor, ceiling, higher

Các phép điều hướng dùng thứ tự của **đối tượng nhận lời gọi**:

| Trên map | Trên view khóa | Ý nghĩa theo thứ tự của đối tượng |
|---|---|---|
| `lowerKey(x)` | `lower(x)` | Khóa gần nhất ở trước x, không nhận khóa tương đương x |
| `floorKey(x)` | `floor(x)` | Khóa gần nhất ở trước hoặc tương đương x |
| `ceilingKey(x)` | `ceiling(x)` | Khóa gần nhất ở sau hoặc tương đương x |
| `higherKey(x)` | `higher(x)` | Khóa gần nhất ở sau x, không nhận khóa tương đương x |

“Gần nhất” ở đây là gần theo thứ tự, không phải tối thiểu hóa một khoảng cách số học bất kỳ. Với khóa không phải số, không cần có khái niệm trừ hai khóa để tính khoảng cách.

Với gốc tự nhiên có `[10, 20, 30, 40]`, mốc 25 cho:

| Phép tìm | Gốc tăng dần | View giảm dần |
|---|---|---|
| lower | 20 | 30 |
| floor | 20 | 30 |
| ceiling | 30 | 20 |
| higher | 30 | 20 |

Trong chiều giảm, 30 đứng trước 25, còn 20 đứng sau 25. Vì thế `down.lowerKey(25)` trả 30 dù 30 lớn hơn 25 về giá trị số.

### 6.2. Cặp phép tương ứng giữa gốc và đảo

Với `down = map.descendingMap()`, có thể đối chiếu:

```text
down.lowerKey(x)   ↔ map.higherKey(x)
down.floorKey(x)   ↔ map.ceilingKey(x)
down.ceilingKey(x) ↔ map.floorKey(x)
down.higherKey(x)  ↔ map.lowerKey(x)
```

Các phép trả entry tương ứng cũng đổi chiều theo cách đó. `floor` và `ceiling` vẫn bao gồm trường hợp khóa tương đương x; `lower` và `higher` vẫn loại trường hợp bằng. Không đổi cờ “có nhận bằng” chỉ vì đổi hướng.

## 7. Chương trình 2: điều hướng và cận phạm vi trong chiều đảo

Lưu vào `TreeMapDescendingNavigationDemo.java`. Các truy vấn gồm mốc có thật, mốc nằm giữa và mốc nằm ngoài tập khóa.

```java
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public class TreeMapDescendingNavigationDemo {
    private static void show(String label, NavigableMap<Integer, String> map, int key) {
        System.out.println(label + "(" + key + "): lower=" + map.lowerKey(key)
                + ", floor=" + map.floorKey(key)
                + ", ceiling=" + map.ceilingKey(key)
                + ", higher=" + map.higherKey(key));
    }

    public static void main(String[] args) {
        TreeMap<Integer, String> map = new TreeMap<>();
        for (int key : List.of(10, 20, 30, 40)) {
            map.put(key, "v" + key);
        }
        NavigableMap<Integer, String> down = map.descendingMap();
        for (int query : List.of(25, 20, 5, 45)) {
            show("original", map, query);
            show("descending", down, query);
        }
        System.out.println("descendingRange="
                + down.subMap(40, true, 20, false).navigableKeySet());
        System.out.println("descendingHead=" + down.headMap(30, false).navigableKeySet());
        System.out.println("descendingTail=" + down.tailMap(30, true).navigableKeySet());
        try {
            down.subMap(10, true, 40, true);
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.out.println("wrongBoundOrder=IllegalArgumentException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TreeMapDescendingNavigationDemo.java
java TreeMapDescendingNavigationDemo
```

Stdout:

```text
original(25): lower=20, floor=20, ceiling=30, higher=30
descending(25): lower=30, floor=30, ceiling=20, higher=20
original(20): lower=10, floor=20, ceiling=20, higher=30
descending(20): lower=30, floor=20, ceiling=20, higher=10
original(5): lower=null, floor=null, ceiling=10, higher=10
descending(5): lower=10, floor=10, ceiling=null, higher=null
original(45): lower=40, floor=40, ceiling=null, higher=null
descending(45): lower=null, floor=null, ceiling=40, higher=40
descendingRange=[40, 30]
descendingHead=[40]
descendingTail=[30, 20, 10]
wrongBoundOrder=IllegalArgumentException
```

### 7.1. Có khóa bằng mốc và không có kết quả

Tại mốc 20, cả floor và ceiling đều có thể trả 20. Lower/higher phải tìm một khóa khác: ở gốc là 10/30, còn ở chiều đảo là 30/10.

Mốc 5 và 45 cho thấy `null` khi không có ứng viên theo hướng cần tìm. Ví dụ ở chiều giảm, 5 nằm sau mọi khóa đang có, nên không có khóa ceiling/higher của 5; lower/floor là 10. Kết quả không có ứng viên không làm map thay đổi.

### 7.2. from và to cũng theo comparator của view

`down.subMap(40, true, 20, false)` lấy từ 40 đến trước 20 theo chiều giảm, nên có `[40, 30]`. Cờ `true` nhận 40; cờ `false` loại 20.

`down.headMap(30, false)` lấy phần đứng trước 30 trong chiều giảm, tức `[40]`. `down.tailMap(30, true)` lấy từ 30 về sau, tức `[30, 20, 10]`.

`down.subMap(10, true, 40, true)` sai thứ tự cận vì 10 đứng sau 40 trong comparator của view. Nó ném `IllegalArgumentException`. Không dùng quy tắc “số nhỏ viết trước” để đặt cận cho mọi view.

### 7.3. Khi chuyển một phạm vi sang chiều đảo

Nếu muốn cùng tập khóa thuộc `(20, 40]` của gốc tự nhiên, ta có thể nhìn bằng gốc `subMap(20, false, 40, true)` hoặc view đảo `subMap(40, true, 20, false)`. Hai cách chứa cùng mapping nhưng duyệt ngược nhau.

Khi đổi cận đầu/cuối, cần mang theo đúng cờ bao gồm của từng **giá trị cận**: cận 40 vẫn được nhận, cận 20 vẫn bị loại. Chỉ đảo hai số mà giữ cờ theo vị trí cũ có thể tạo ra một tập khóa khác.

## 8. navigableKeySet: view khóa có khả năng điều hướng

### 8.1. Ba đường nhìn khóa theo chiều đảo

Với map có khóa 10, 20, 30, các lời gọi sau cho cùng thứ tự khóa đảo:

```java
// Minh họa bên trong một phương thức đã có biến map.
map.descendingKeySet();
map.navigableKeySet().descendingSet();
map.descendingMap().navigableKeySet();
```

Chúng đều nhìn khóa theo chiều 30, 20, 10 và đều liên quan đến cùng map. Không nên dùng việc chúng có phải cùng đối tượng `==` hay không để quyết định xem thay đổi có truyền qua map gốc hay không. Hợp đồng view mới là điều cần dựa vào.

`downKeys.first()` nhìn phần tử đầu theo comparator của downKeys, nên trong ví dụ số tự nhiên đó là 30. Các phương thức `lower/floor/ceiling/higher` của set cũng dùng chiều của chính set, giống quy tắc đã thấy trên map.

### 8.2. Xóa khóa từ view là xóa cả mapping

View khóa không chỉ là một tập số độc lập. Trên view khóa của `TreeMap`, các thao tác loại phần tử được hỗ trợ:

| Thao tác trên view khóa | Ảnh hưởng |
|---|---|
| `remove(k)` | Loại mapping của k nếu có trong view |
| `pollFirst()` | Lấy khóa đầu và loại mapping tương ứng |
| `pollLast()` | Lấy khóa cuối và loại mapping tương ứng |
| `iterator.remove()` sau `next()` hợp lệ | Loại mapping của khóa iterator vừa trả |
| `clear()` | Loại toàn bộ mapping mà view khóa đang nhìn thấy |

Nếu view khóa đến từ toàn map, clear làm rỗng map. Nếu nó đến từ một map phạm vi, clear chỉ loại các mapping đang thuộc phạm vi đó. Tính chất liên kết vẫn có hiệu lực qua nhiều lớp view.

Tài liệu [TreeMap — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html) nêu rõ view khóa liên kết với map và hỗ trợ loại phần tử. Truyền một view khóa cho nơi khác có thể trao cho nơi đó khả năng xóa mapping, dù kiểu dữ liệu họ nhận chỉ là một set.

### 8.3. Không thể thêm khóa qua add

`keys.add(k)` và `keys.addAll(...)` không được hỗ trợ trên view khóa của map; chúng ném `UnsupportedOperationException`. Một khóa riêng lẻ không cung cấp giá trị V cho mapping mới. Muốn thêm mapping, hãy dùng `put` trên map với cả khóa và giá trị.

Vì vậy, “map sửa được” không đồng nghĩa “mọi phương thức sửa của mọi view đều chạy được”. View khóa cho phép loại mapping nhưng không cho thêm mapping bằng `add`. `NavigableSet` chỉ là kiểu API; khả năng thao tác cụ thể còn phụ thuộc cách triển khai và nguồn tạo view.

### 8.4. Phân biệt rỗng với không có ứng viên

| Lời gọi trên kiểu đang xét | Khi rỗng hoặc không có kết quả |
|---|---|
| `keys.first()` / `keys.last()` | Ném `NoSuchElementException` nếu set rỗng |
| `keys.pollFirst()` / `keys.pollLast()` | Trả `null` nếu set rỗng |
| `keys.lower/floor/ceiling/higher(x)` | Trả `null` nếu không có ứng viên |
| `map.firstKey()` / `map.lastKey()` | Ném `NoSuchElementException` nếu map rỗng |
| `map.firstEntry()` / `map.lastEntry()` | Trả entry `null` nếu map rỗng |
| `map.pollFirstEntry()` / `map.pollLastEntry()` | Trả entry `null` nếu map rỗng |

Đừng áp dụng quy tắc rỗng của một phương thức sang một phương thức khác chỉ vì chúng cùng nói đến đầu/cuối. Hợp đồng `NavigableSet` được mô tả tại [NavigableSet — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableSet.html).

## 9. Chương trình 3: view khóa, iterator và trường hợp null

Lưu chương trình đầy đủ vào `TreeMapNavigableKeysDemo.java`. Các ngoại lệ được bắt có chủ đích để in ra hành vi cần quan sát.

```java
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.TreeMap;

public class TreeMapNavigableKeysDemo {
    public static void main(String[] args) {
        TreeMap<Integer, String> map = new TreeMap<>();
        map.put(10, "A");
        map.put(20, "B");
        map.put(30, "C");
        NavigableSet<Integer> keys = map.navigableKeySet();
        NavigableSet<Integer> downKeys = map.descendingKeySet();
        System.out.println("keys=" + keys);
        System.out.println("descendingKeys=" + downKeys);
        System.out.println("descendingSet=" + keys.descendingSet());
        System.out.println("keysFromDescendingMap="
                + map.descendingMap().navigableKeySet());
        System.out.println("descendingEnds=" + downKeys.first() + "," + downKeys.last());
        System.out.println("lower25=" + keys.lower(25) + "," + downKeys.lower(25));
        try {
            keys.add(15);
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println("addKey=UnsupportedOperationException");
        }
        System.out.println("descendingPollFirst=" + downKeys.pollFirst());
        System.out.println("remove10=" + keys.remove(10));
        System.out.println("afterKeyRemovals=" + map);
        map.put(25, "X");
        System.out.println("sameKeyViews=" + keys + "," + downKeys);
        Iterator<Integer> iterator = downKeys.iterator();
        System.out.println("iteratorNext=" + iterator.next());
        iterator.remove();
        System.out.println("afterIteratorRemove=" + map);
        keys.clear();
        System.out.println("emptyMap=" + map.isEmpty());
        System.out.println("emptyPoll=" + keys.pollFirst());
        try {
            keys.first();
            throw new AssertionError("Expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
            System.out.println("emptyFirst=NoSuchElementException");
        }
        TreeMap<Integer, String> nullable =
                new TreeMap<>(Comparator.nullsFirst(Comparator.naturalOrder()));
        nullable.put(null, "nil");
        nullable.put(10, "ten");
        System.out.println("nullableFirst=" + nullable.navigableKeySet().first());
        System.out.println("nullablePolledKey=" + nullable.navigableKeySet().pollFirst());
        System.out.println("nullableSizeAfterPoll=" + nullable.size());
        nullable.put(null, "nil-again");
        Map.Entry<Integer, String> entry = nullable.descendingMap().pollLastEntry();
        System.out.println("nullablePolledEntry=" + entry);
        System.out.println("nullableEntryExists=" + (entry != null));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TreeMapNavigableKeysDemo.java
java TreeMapNavigableKeysDemo
```

Stdout:

```text
keys=[10, 20, 30]
descendingKeys=[30, 20, 10]
descendingSet=[30, 20, 10]
keysFromDescendingMap=[30, 20, 10]
descendingEnds=30,10
lower25=20,30
addKey=UnsupportedOperationException
descendingPollFirst=30
remove10=true
afterKeyRemovals={20=B}
sameKeyViews=[20, 25],[25, 20]
iteratorNext=25
afterIteratorRemove={20=B}
emptyMap=true
emptyPoll=null
emptyFirst=NoSuchElementException
nullableFirst=null
nullablePolledKey=null
nullableSizeAfterPoll=1
nullablePolledEntry=null=nil-again
nullableEntryExists=true
```

### 9.1. Các đường đảo cùng nhìn thấy một thứ tự

Ba dòng `descendingKeys`, `descendingSet`, `keysFromDescendingMap` đều là `[30, 20, 10]`. Trong khi đó `keys` là `[10, 20, 30]`. `lower25=20,30` ghi kết quả trên view cùng chiều và view đảo: hướng điều hướng thay đổi theo chiều của view.

Lệnh `keys.add(15)` bị từ chối. Sau đó `downKeys.pollFirst()` lấy 30 ra và loại mapping 30=C. `keys.remove(10)` loại mapping 10=A. Map gốc chỉ còn 20=B.

### 9.2. Một view đã tạo vẫn phản ánh thay đổi mới

Khi thêm 25=X qua map gốc, chính các biến view cũ nhìn thấy `[20, 25]` và `[25, 20]`. Chúng không giữ riêng ba khóa tại thời điểm khởi tạo.

Iterator được tạo từ `downKeys` trả 25 trước. Gọi `iterator.remove()` đúng sau lần `next()` đó loại 25 khỏi map; gốc còn 20=B. Đây là đường xóa được iterator hỗ trợ, không phải một lệnh `map.remove` bên ngoài iterator đang hoạt động.

`keys.clear()` sau đó làm map rỗng. `pollFirst()` trả `null`, còn `first()` ném `NoSuchElementException`, đúng hai hợp đồng khác nhau.

### 9.3. Khóa null làm kết quả chỉ trả khóa có thể nhập nhằng

Map `nullable` dùng comparator cho phép khóa `null` và đặt nó đầu thứ tự gốc. Khi map đang có null và 10, `first()` trả khóa `null` hợp lệ. `pollFirst()` cũng trả `null`, nhưng lần này nó **đã loại một mapping**, được chứng minh bằng `nullableSizeAfterPoll=1`.

Do đó không thể luôn suy ra “poll trả null nghĩa là trước đó rỗng” nếu chính cấu trúc cho phép phần tử null. Quy tắc thông thường chỉ không nhập nhằng khi miền khóa không có null.

Ví dụ cuối chuyển sang `pollLastEntry()` trên view đảo. Entry có khóa null nằm cuối chiều đảo, nên được lấy ra. Dòng `nullablePolledEntry=null=nil-again` mô tả một đối tượng entry có thật chứa khóa null, và `nullableEntryExists=true` xác nhận đối tượng kết quả không phải null.

Khi cần phân biệt “không có mapping” với “có mapping mang khóa null”, API trả cả entry thường giúp thể hiện rõ hơn API chỉ trả khóa. Nó không yêu cầu giá trị bên trong entry phải khác null.

## 10. View phạm vi vẫn dùng chung dữ liệu

### 10.1. Phạm vi là điều kiện cận, không phải danh sách khóa được chụp lại

`down.subMap(40, true, 20, false)` không chụp riêng các khóa đang có lúc gọi. Nó xác định một cửa nhìn chứa các khóa trong phạm vi đã chọn, theo comparator đảo. Khi map gốc được thêm khóa mới nằm trong phạm vi, khóa đó xuất hiện trong view.

Ngược lại, khóa nằm ngoài phạm vi không xuất hiện dù vẫn tồn tại trong map gốc. Lệnh ghi một khóa ngoài phạm vi qua view sẽ bị từ chối bằng `IllegalArgumentException`; việc map gốc có khả năng chứa khóa đó không nới cận của view.

Loại entry đang đúng cận không làm cận thay đổi. Sau khi mapping 40 bị xóa, view vẫn có cận 40 và vẫn có thể nhìn thấy khóa 35 được thêm về sau. Không hiểu cận phạm vi như “khóa lớn nhất đang còn trong view”.

### 10.2. clear qua phạm vi chỉ xóa phần đang nhìn thấy

Giả sử gốc có 10, 20, 25, 30, 35, còn view phạm vi nhìn 35, 30, 25. `range.clear()` loại đúng các mapping 25, 30, 35 khỏi gốc. Các khóa 10 và 20 còn lại vì nằm ngoài phạm vi, trong đó 20 bị loại bởi cờ cận không bao gồm.

Việc xóa qua phạm vi và qua view đảo không cần chạy thêm một vòng xóa trên map gốc. Nhưng cũng vì vậy, không được coi range như một bản sao có thể dọn bỏ tự do mà không ảnh hưởng chủ sở hữu map.

## 11. Chương trình 4: bản sao, kiểu constructor và phạm vi sống

Lưu vào `TreeMapViewCopyRangeDemo.java`. Ví dụ tạo cả view liên kết lẫn các cấu trúc sao chép để quan sát sự khác nhau sau khi thay đổi.

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class TreeMapViewCopyRangeDemo {
    public static void main(String[] args) {
        TreeMap<Integer, String> map = new TreeMap<>();
        map.put(10, "A");
        map.put(20, "B");
        map.put(30, "C");
        map.put(40, "D");
        NavigableMap<Integer, String> down = map.descendingMap();
        TreeMap<Integer, String> descendingCopy = new TreeMap<>(down);
        Map<Integer, String> generalMap = down;
        TreeMap<Integer, String> naturalCopy = new TreeMap<>(generalMap);
        List<Integer> keySnapshot = new ArrayList<>(down.navigableKeySet());
        System.out.println("descendingCopyKeys=" + descendingCopy.keySet());
        System.out.println("naturalCopyKeys=" + naturalCopy.keySet());
        System.out.println("keySnapshot=" + keySnapshot);
        NavigableMap<Integer, String> range = down.subMap(40, true, 20, false);
        System.out.println("initialRange=" + range);
        range.put(30, "C2");
        range.remove(40);
        map.put(35, "new");
        range.put(25, "X");
        System.out.println("sharedRange=" + range);
        System.out.println("sharedOriginal=" + map);
        try {
            range.put(10, "outside");
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.out.println("outsideRangePut=IllegalArgumentException");
        }
        System.out.println("unchanged10=" + map.get(10));
        range.clear();
        System.out.println("afterRangeClear=" + map);
        System.out.println("sameDescendingView=" + down);
        System.out.println("independentDescendingCopy=" + descendingCopy);
        System.out.println("independentNaturalCopy=" + naturalCopy);
        System.out.println("independentKeySnapshot=" + keySnapshot);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TreeMapViewCopyRangeDemo.java
java TreeMapViewCopyRangeDemo
```

Stdout:

```text
descendingCopyKeys=[40, 30, 20, 10]
naturalCopyKeys=[10, 20, 30, 40]
keySnapshot=[40, 30, 20, 10]
initialRange={40=D, 30=C}
sharedRange={35=new, 30=C2, 25=X}
sharedOriginal={10=A, 20=B, 25=X, 30=C2, 35=new}
outsideRangePut=IllegalArgumentException
unchanged10=A
afterRangeClear={10=A, 20=B}
sameDescendingView={20=B, 10=A}
independentDescendingCopy={40=D, 30=C, 20=B, 10=A}
independentNaturalCopy={10=A, 20=B, 30=C, 40=D}
independentKeySnapshot=[40, 30, 20, 10]
```

### 11.1. Cùng đối tượng đầu vào, hai constructor có hợp đồng thứ tự khác

`down` được khai báo là `NavigableMap`, vốn cũng là `SortedMap`. Vì vậy `new TreeMap<>(down)` chọn constructor nhận `SortedMap` và giữ cùng quy tắc thứ tự của down. `descendingCopyKeys` là `[40, 30, 20, 10]`.

`generalMap` trỏ đến chính view đó nhưng được khai báo là `Map`. Lời gọi `new TreeMap<>(generalMap)` chọn constructor nhận `Map`, tạo TreeMap theo **thứ tự tự nhiên**. Kết quả `naturalCopyKeys` là `[10, 20, 30, 40]`.

Lựa chọn overload của constructor diễn ra theo kiểu mà trình biên dịch nhìn thấy tại lời gọi, không tự đổi sang overload SortedMap chỉ vì đối tượng thực tế lúc chạy có khả năng đó. Đây là lý do một thay đổi nhỏ về kiểu biến hoặc chữ ký hàm có thể làm cách sao chép thứ tự thay đổi.

Các hợp đồng constructor này được mô tả trong [TreeMap — Constructor Details](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html). Với khóa chỉ so được bằng comparator riêng mà không có thứ tự tự nhiên, chọn nhầm constructor nhận Map còn có thể gây lỗi, không chỉ đổi chiều.

### 11.2. Danh sách khóa là một bản chụp cấu trúc riêng

`new ArrayList<>(down.navigableKeySet())` tạo danh sách mới theo thứ tự duyệt của view tại thời điểm tạo: `[40, 30, 20, 10]`. Thêm hoặc xóa khóa trong map sau đó không tự cập nhật danh sách này.

Danh sách chỉ giữ khóa, không giữ các mapping. Hai bản sao TreeMap giữ cả mapping, nhưng được sắp theo comparator riêng đã chọn khi tạo. Vì vậy, “chụp dữ liệu theo chiều đảo” có thể là một danh sách khóa hoặc một map mới; cần chọn đúng kiểu kết quả mà công việc tiếp theo cần.

### 11.3. Ghi qua range và ghi qua gốc đều được phản ánh

`range` ban đầu có `{40=D, 30=C}`. Sau các thao tác:

- `range.put(30, "C2")` cập nhật 30 trong map gốc.
- `range.remove(40)` loại 40 trong map gốc.
- `map.put(35, "new")` làm 35 xuất hiện trong range vì nằm giữa các cận.
- `range.put(25, "X")` thêm 25 vào gốc và vào range.

Range lúc này là `{35=new, 30=C2, 25=X}`. Cận vẫn là từ 40 có nhận đến 20 không nhận. Lời gọi `range.put(10, "outside")` bị từ chối, và `unchanged10=A` xác nhận mapping 10 của gốc không bị thay giá trị.

Sau `range.clear()`, gốc còn `{10=A, 20=B}`, view đảo còn `{20=B, 10=A}`. Hai bản sao TreeMap và danh sách khóa vẫn giữ dữ liệu lúc được tạo. Chúng độc lập về cấu trúc với gốc, trong khi down và range tiếp tục theo dõi dữ liệu hiện tại.

### 11.4. Sao chép nông không phải sao chép sâu

Các constructor copy và ArrayList ở đây tạo cấu trúc chứa mới, nhưng không nhân bản sâu từng đối tượng khóa hoặc giá trị. Ví dụ dùng `Integer` và `String`, nên không có thao tác sửa đối tượng giá trị tại chỗ làm kết quả khó quan sát.

Nếu giá trị là một đối tượng có thể thay đổi, map gốc và bản sao có thể cùng giữ tham chiếu đến nó. Sửa đối tượng đó qua một tham chiếu vẫn có thể được quan sát ở cả hai. Tương tự, phải giữ ổn định các thành phần khóa mà comparator dùng; sao chép một map không làm khóa có thể thay đổi tự trở nên an toàn.

Bản sao cũng không mặc nhiên chỉ đọc. Tính độc lập về cấu trúc, quyền sửa cấu trúc và việc chia sẻ tham chiếu đối tượng là ba câu hỏi riêng.

## 12. Duyệt an toàn và chọn đúng quyền thay đổi

### 12.1. Duyệt ngược không bắt buộc sao chép

Nếu chỉ cần đi qua các khóa theo chiều ngược, có thể dùng `descendingKeySet()` trong vòng lặp hoặc lấy `map.navigableKeySet().descendingIterator()`. `descendingIterator()` có hiệu ứng duyệt theo chiều của `descendingSet().iterator()`.

Nếu cần cả khóa lẫn giá trị, duyệt `map.descendingMap().entrySet()` sẽ đọc được cả hai từ entry. Không nhất thiết tạo danh sách khóa đảo rồi tra lại từng khóa chỉ để lấy giá trị, trừ khi bản sao danh sách phục vụ một mục đích riêng.

Đoạn sau minh họa một lượt duyệt đọc, đặt trong phương thức đã có `map`, không phải chương trình độc lập:

```java
for (Map.Entry<Integer, String> entry : map.descendingMap().entrySet()) {
    System.out.println(entry.getKey() + ":" + entry.getValue());
}
```

Lượt duyệt này không tự loại mapping. Nếu muốn lấy dần và loại các mapping theo chiều đảo trong một luồng, có thể gọi `pollFirstEntry()` trên view và kiểm tra đối tượng entry trả về. Cần xác định rõ việc lấy ra trước khi xử lý có phù hợp với trường hợp xử lý thất bại hay không; bản thân TreeMap không cung cấp giao dịch cho công việc phía sau.

### 12.2. Thay đổi qua một view cũng ảnh hưởng iterator ở view khác

Map gốc, view đảo và các view khóa không có ba bản dữ liệu tách biệt. Nếu đang duyệt một view rồi thêm/xóa mapping qua một đường khác, iterator có thể bị ảnh hưởng bởi thay đổi cấu trúc của cùng map.

`TreeMap` có iterator fail-fast, có thể báo `ConcurrentModificationException` khi phát hiện kiểu thay đổi đó. Đây là cơ chế phát hiện lỗi theo nỗ lực, không phải một bảo đảm phối hợp truy cập và cũng không phải cơ chế chỉ dành cho nhiều luồng. Một luồng tự sửa map ngoài iterator của nó vẫn có thể gặp vấn đề.

Khi cần xóa khóa đang duyệt, dùng `iterator.remove()` theo đúng vòng đời `next`/`remove`, như chương trình 3. Không viết một vòng for-each trên downKeys rồi tự gọi `map.remove(key)` và cho rằng đổi biến nhận lời gọi đã tách việc xóa khỏi cấu trúc đang duyệt.

Nếu cần tạo một kế hoạch thay đổi rồi mới áp dụng, có thể thu thập các khóa vào một danh sách riêng trước. Điều đó tách cấu trúc duyệt danh sách khỏi cấu trúc map; nó không tự làm cả quy trình trở thành nguyên tử khi có luồng khác cùng thay đổi dữ liệu.

### 12.3. View không tạo quyền chỉ đọc hoặc khả năng đồng thời

`descendingMap()` tự nó không biến map thành chỉ đọc. `navigableKeySet()` cũng không phải một bản công bố khóa vô hại nếu bên nhận được phép gọi `remove` hoặc `clear`. Khi thiết kế API trả dữ liệu, cần chọn rõ có muốn cho phép bên nhận thay đổi nguồn qua kết quả hay không.

Một lớp bọc chỉ đọc thích hợp có thể ngăn thay đổi qua chính lớp bọc, nhưng khái niệm chỉ đọc vẫn khác với bản chụp độc lập: thay đổi từ chủ sở hữu map gốc có thể tiếp tục được nhìn thấy qua các view. Khi cần dữ liệu tách biệt về cấu trúc, phải tạo cấu trúc mới như ví dụ copy.

`TreeMap` không tự đồng bộ cho truy cập đồng thời; các view của nó cũng không thêm sự đồng bộ. Một chuỗi `firstKey()` rồi `remove(key)` gồm hai thao tác riêng, không được suy ra rằng dữ liệu không thể thay đổi xen giữa khi nhiều luồng cùng dùng. Mọi đường truy cập qua gốc và qua view phải tuân theo cùng một thiết kế phối hợp nếu có yêu cầu đồng thời.

## 13. Kiểm tra theo quan hệ giữa các view

### 13.1. Những điều nên đối chiếu

Một phép kiểm tra chỉ nhìn chuỗi in giảm dần sẽ bỏ sót phần quan trọng của view. Nên kiểm tra cả dữ liệu liên kết và ngữ nghĩa theo chiều:

1. Lấy view, sau đó thêm qua gốc: view có nhìn thấy mapping mới ở đúng vị trí không?
2. Xóa qua view khóa: mapping trong gốc có bị loại không?
3. Poll đầu view đảo: có loại cuối gốc không?
4. Với một mốc ở giữa, lower/floor/ceiling/higher có đổi cặp đúng theo chiều không?
5. Với mốc trùng khóa, floor/ceiling có giữ tính bao gồm còn lower/higher vẫn loại bằng không?
6. View phạm vi có nhận đúng cận và từ chối ghi ngoài cận không?
7. Bản sao có giữ đúng comparator mong muốn và độc lập với thay đổi cấu trúc của gốc không?

Kiểm tra thứ tự bằng một danh sách khóa theo thứ tự duyệt, chẳng hạn `new ArrayList<>(view.navigableKeySet())`. Phép bằng nhau của map hoặc set không kiểm tra thứ tự, nên hai view đi ngược chiều vẫn có thể bằng nhau về mapping hoặc tập khóa.

### 13.2. Trường hợp biên có giá trị học tập

- Map rỗng: tách rõ phương thức trả null và phương thức ném ngoại lệ.
- Map một mapping: đầu và cuối cùng là một khóa; poll một đầu làm đầu kia cũng biến mất.
- Map đã dùng comparator giảm dần: descendingMap phải đảo về chiều tăng của số trong trường hợp đó.
- Cận phạm vi không trùng khóa đang có: cận vẫn là mốc so sánh, không cần được chèn vào map.
- Mapping đúng cận bị xóa: phạm vi vẫn giữ cận đã định, không thu hẹp theo tập khóa hiện tại.
- Khóa null nếu comparator cho phép: phân biệt entry null với entry có khóa null.

Nếu from và to tương đương theo comparator, một phạm vi nhận cả hai đầu có thể chứa nhóm khóa tại mốc đó; nếu loại một đầu thì không chứa mốc. Khi làm việc với comparator riêng, cần xét quan hệ so sánh thay vì chỉ dựa vào hình thức biểu diễn khóa.

### 13.3. Chi phí của view và bản sao khác nhau

Việc lấy view không đòi hỏi sao chép tất cả mapping thành một map dữ liệu độc lập. Các thao tác qua view vẫn làm việc trên cấu trúc được liên kết, với chiều và cận của view.

Tuy nhiên, đừng suy ra mọi thao tác trên view đều có chi phí hằng số. Duyệt k phần tử và tạo danh sách k khóa phải xử lý k phần tử; các phép tìm trên TreeMap vẫn dựa vào cấu trúc cây và phép so sánh khóa. Các view phạm vi cũng có công việc kiểm tra cận và thao tác trên phần dữ liệu được nhìn thấy.

Chọn view khi cần nhìn dữ liệu hiện tại theo chiều hoặc phạm vi khác. Chọn bản sao khi cần cấu trúc độc lập và chấp nhận chi phí tạo dữ liệu chứa mới. Đây là quyết định về quan hệ dữ liệu trước khi là quyết định tối ưu tốc độ.

## 14. Lỗi thường gặp

| Nhầm lẫn | Hệ quả | Cách xử lý |
|---|---|---|
| descendingMap tạo một TreeMap riêng | Sửa view làm thay đổi gốc ngoài ý định | Đọc đúng quan hệ view liên kết |
| Tạo view đảo làm map gốc đổi chiều duyệt | Dự đoán sai kết quả từ biến gốc | Giữ riêng chiều của từng đối tượng nhận lời gọi |
| descending luôn là số lớn đến số nhỏ | Sai khi gốc đã có comparator đảo | Đảo thứ tự đang có, không mặc định giá trị số |
| lower luôn trả số nhỏ hơn | Sai trên view giảm dần | Đọc theo comparator của view |
| Viết cận subMap số nhỏ trước ở mọi chiều | IllegalArgumentException hoặc sai phạm vi | Đặt from/to theo thứ tự view |
| Đổi cận nhưng quên đổi vị trí cờ bao gồm | Nhận nhầm một đầu, bỏ nhầm đầu khác | Gắn cờ với giá trị cận tương ứng |
| navigableKeySet là tập khóa độc lập | Remove/clear làm mất mapping nguồn | Tạo collection mới nếu cần bản sao |
| Map sửa được nên keys.add cũng được | UnsupportedOperationException | Thêm mapping bằng put cả khóa và giá trị |
| Poll chỉ xem đầu/cuối | Dữ liệu bị loại | Dùng first/last để xem |
| First của set rỗng trả null | Bỏ sót NoSuchElementException | Theo hợp đồng riêng của phương thức |
| Poll khóa trả null luôn nghĩa là rỗng | Sai khi có khóa null hợp lệ | Dùng entry hoặc hợp đồng khóa không null khi cần phân biệt |
| Mọi constructor TreeMap copy đều giữ comparator | Bản sao đổi thứ tự hoặc không so được khóa | Phân biệt overload Map và SortedMap |
| Map.equals hoặc Set.equals chứng minh thứ tự giống nhau | Kiểm tra không phát hiện đảo chiều | So sánh một danh sách theo thứ tự |

## 15. Tự kiểm tra

### Câu hỏi

1. Gốc tự nhiên có `[10, 20, 30]`; sau khi tạo down, gốc có tự chuyển thành `[30, 20, 10]` không?
2. Xóa khóa 20 qua `down.navigableKeySet()` có ảnh hưởng map gốc không?
3. `down.pollFirstEntry()` lấy đầu hay cuối gốc?
4. Với gốc `[10, 20, 30, 40]`, `down.lowerKey(25)` và `down.ceilingKey(25)` là gì?
5. Vì sao `down.subMap(10, true, 40, true)` không hợp lệ khi down dùng thứ tự số giảm dần?
6. `map.descendingKeySet()` và `map.navigableKeySet().descendingSet()` có cần là cùng đối tượng để cùng nhìn dữ liệu không?
7. Có thể thêm khóa vào map bằng `navigableKeySet().add(key)` không?
8. Tạo `ArrayList` từ view khóa có tiếp tục nhìn thấy các khóa thêm về sau không?
9. Khi đầu vào được khai báo NavigableMap, constructor TreeMap copy nào được chọn? Nếu khai báo Map thì sao?
10. Một poll khóa trả null có đủ để suy ra trước đó không có mapping trong mọi TreeMap không?

### Đáp án

1. Không; gốc giữ chiều cũ, down nhìn ngược.
2. Có; view khóa liên kết nên mapping 20 bị loại khỏi gốc.
3. Cuối gốc, vì đó là đầu view đảo.
4. Lần lượt là 30 và 20, theo chiều của down.
5. 10 đứng sau 40 trong comparator giảm dần; cận from/to bị đảo so với thứ tự yêu cầu.
6. Không; quan hệ được bảo đảm bởi hợp đồng view, không dựa vào danh tính đối tượng.
7. Không; view khóa không hỗ trợ add/addAll, cần put mapping trên map.
8. Không; đó là danh sách mới chứa các khóa tại lúc tạo, dù bản thân các đối tượng khóa có thể vẫn được chia sẻ tham chiếu.
9. NavigableMap cũng là SortedMap nên chọn overload SortedMap và giữ thứ tự; kiểu Map chọn overload Map, dùng thứ tự tự nhiên.
10. Không; nếu comparator cho phép khóa null, poll có thể vừa loại một mapping mang khóa null.

## 16. Thực hành với kết quả đối chiếu

### Bài 1: xóa qua view đảo

Tạo gốc tự nhiên có 1=A, 2=B, 3=C và lấy down. Thêm 4=D qua gốc, rồi gọi `down.navigableKeySet().pollFirst()`. Trước khi chạy, dự đoán khóa được lấy và thứ tự cả hai chiều.

Kết quả đối chiếu: poll trả 4 và loại 4=D; gốc còn `[1, 2, 3]`, down còn `[3, 2, 1]`. Nếu dự đoán 1 thì đã đọc đầu view như đầu gốc.

### Bài 2: cận có bao gồm theo chiều đảo

Với gốc có 10, 20, 30, 40, lấy `down.subMap(40, false, 20, true)`. Liệt kê khóa đang nhìn và viết một phạm vi trên gốc chứa cùng mapping.

Kết quả đối chiếu: down nhìn `[30, 20]`; gốc tương ứng là `subMap(20, true, 40, false)` với thứ tự `[20, 30]`. Cờ true vẫn đi cùng cận 20 và cờ false vẫn đi cùng cận 40.

### Bài 3: view không chụp riêng tập khóa ban đầu

Tạo range đảo từ 40 có nhận đến 20 không nhận khi gốc chỉ có 10, 20, 30, 40. Xóa mapping 40, rồi thêm 35 qua gốc. Giải thích vì sao range nhìn thấy 35 dù khóa đó chưa tồn tại lúc tạo range.

Kết quả đối chiếu: range nhìn `[35, 30]`. Phạm vi được xác định bởi cận, không bởi bản sao danh sách các khóa lúc khởi tạo.

### Bài 4: kiểm tra bản sao và kiểu khai báo

Tạo down, một TreeMap copy từ biến NavigableMap và một TreeMap copy từ biến Map trỏ cùng down. Sau đó sửa gốc. Đối chiếu thứ tự ban đầu của hai bản sao và việc chúng có tự cập nhật hay không.

Kết quả đối chiếu: bản sao từ NavigableMap giữ chiều của down; bản sao từ Map dùng thứ tự tự nhiên. Cả hai không tự cập nhật cấu trúc theo gốc. Muốn xác nhận thứ tự, so danh sách khóa, không chỉ so Map.equals.

## 17. Môi trường chạy và tài liệu

Bốn chương trình trong bài độc lập, dùng cú pháp và API tương thích Java 17. Chúng đã được biên dịch bằng `javac --release 17 -encoding UTF-8` và chạy trên JDK 24.0.2; stdout đã được đối chiếu từ các lần chạy đó. Mỗi lớp public được lưu trong tệp trùng tên, không cần thư viện ngoài.

- [TreeMap — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html): view đảo, view khóa, constructor sao chép và phạm vi liên kết.
- [NavigableMap — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableMap.html): quan hệ điều hướng, descendingMap và cận phạm vi.
- [NavigableSet — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableSet.html): tìm lân cận, hai đầu, view đảo và iterator.
- [SortedMap — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SortedMap.html): thứ tự của map và các collection view tương ứng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tree-map-008">Chính sách null, tính khả biến của khóa và tính nhất quán phép so sánh</a>

<details>
<summary>Xem chi tiết</summary>

# Chính sách null, tính khả biến của khóa và tính nhất quán phép so sánh

## 1. Ba điều quyết định TreeMap có biểu diễn đúng khóa hay không

TreeMap cần một quan hệ thứ tự để đặt khóa vào cây và tìm lại khóa bằng những lần so sánh.

Vì vậy, chỉ tạo được một TreeMap và gọi put không có nghĩa thiết kế khóa đã hoàn chỉnh. Cần trả lời ba câu hỏi:

1. Khóa null và giá trị null có được phép không?
2. Những thông tin dùng để nhận diện, sắp xếp khóa có giữ ổn định khi khóa đang nằm trong map không?
3. Quan hệ “so sánh bằng 0” có trùng với quan hệ equals của khóa không?

Ba vấn đề có liên hệ, nhưng không được gộp thành một quy tắc mơ hồ như “khóa phải tốt”.

Một Comparator có thể sắp xếp có quy luật nhưng gộp các khóa khác equals. Một lớp khóa có equals/hashCode đúng vẫn có thể bị dùng sai nếu trường sắp xếp bị sửa sau put. Một map cho phép value null không có nghĩa nó cũng cho phép key null.

Bài này tách từng điều kiện, đưa ví dụ chạy được và chỉ rõ đâu là hợp đồng công khai, đâu là kết quả quan sát khi cố ý sử dụng khóa sai cách.

## 2. Khóa, giá trị và thứ tự có vai trò khác nhau

| Thành phần | Vai trò trong TreeMap |
| --- | --- |
| Khóa | Xác định vị trí ánh xạ thông qua quan hệ thứ tự |
| Giá trị | Dữ liệu gắn với khóa; không quyết định đường tìm mặc định |
| `compareTo`/Comparator | Quyết định đi trái, đi phải hoặc xem đã có khóa tương đương |
| `equals` | Quy tắc bằng của đối tượng và nền tảng của hợp đồng Map chung |
| Trạng thái dùng để sắp xếp | Phải tiếp tục phù hợp với vị trí khóa đã được tổ chức trong cây |

TreeMap không dùng hash của khóa để chọn bucket như HashMap. Điều đó không cho phép bỏ qua hợp đồng equals/hashCode của một kiểu khóa dùng chung trong Java; chỉ là đường tìm của TreeMap dựa vào so sánh.

Một khóa có thể được dùng ở các cấu trúc khác, và các thao tác như equals/hashCode của bản thân map cũng có hợp đồng riêng. Thiết kế kiểu dữ liệu không nên đúng cho một lời gọi rồi sai khi chuyển sang ngữ cảnh khác.

## 3. Chính sách null: phân biệt khóa với giá trị

### 3.1. Natural order không nhận khóa null

`new TreeMap<>()` dùng thứ tự tự nhiên của khóa. Các khóa phải có khả năng so sánh phù hợp với nhau; null không phải một khóa hợp lệ trong cấu hình natural order.

`put`(null, value) bị từ chối bằng NullPointerException. Không coi null là “nhỏ nhất mặc định” chỉ vì null có thể xuất hiện trong một số loại Map khác.

Tài liệu [TreeMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html) nêu điều kiện khóa và các ngoại lệ liên quan tới natural order hoặc Comparator không nhận null.

### 3.2. Comparator riêng có thể cho phép khóa null

Nếu bài toán chủ động cần khóa null, có thể chọn một quan hệ thứ tự nhận null:

```java
Comparator<String> order =
        Comparator.nullsFirst(Comparator.naturalOrder());
Map<String, Integer> map = new TreeMap<>(order);
```

Đây là đoạn khai báo minh họa trong ngữ cảnh có các import phù hợp.

`nullsFirst` xem null đứng trước các khóa khác; nullsLast xem null đứng sau. Các khóa không null được so bằng Comparator bên trong.

Vẫn chỉ có một vị trí khóa null, vì so null với null được xem là bằng 0. `put` lại khóa null cập nhật ánh xạ của vị trí ấy, không tạo vô hạn entry null.

### 3.3. Value null được phép độc lập với chính sách key

Một TreeMap dùng natural order vẫn có thể chứa "beta" → null. Value không cần được so để tìm vị trí khóa "beta".

Do đó, bảng chính sách có thể là:

| Cấu hình | Key null | Value null |
| --- | --- | --- |
| TreeMap dùng natural order | Không cho phép | Cho phép |
| Comparator riêng không xử lý null | Phụ thuộc Comparator, thường bị từ chối khi so | Cho phép |
| Comparator nhận null hợp lệ | Có thể cho phép | Cho phép |

Không suy chính sách key từ chính sách value hoặc ngược lại.

### 3.4. `get` trả null có hai khả năng

`get`("beta") trả null có thể vì:

- Không có khóa "beta".
- Có khóa "beta", nhưng giá trị của nó là null.

Dùng containsKey khi cần phân biệt sự tồn tại. `getOrDefault` chỉ dùng giá trị dự phòng khi khóa không có; nó không thay một ánh xạ đang có value null bằng giá trị dự phòng.

Tương tự, put/remove trả null không luôn đủ để suy ra trước thao tác có ánh xạ hay không.

Nếu kiểm tra tồn tại rồi thực hiện một bước khác trên dữ liệu dùng chung, cần xét tính nguyên tử của cả chuỗi; TreeMap không tự đồng bộ.

### 3.5. Null của đối tượng khóa và null của một trường là hai tầng

Một Comparator so Person theo name có thể cần chính sách cho name null. Điều đó khác với việc cả đối tượng Person là null.

Bọc Comparator của name bằng nullsLast chỉ xử lý giá trị name null khi đã có một đối tượng để lấy trường. Muốn cho phép cả đối tượng khóa null, phải xử lý ở tầng ngoài thích hợp.

Cần xác định đúng nơi null xuất hiện, không bọc tùy ý rồi mong mọi trường hợp đều được giải quyết.

### 3.6. Đừng nhầm hai loại tham số Comparator null

Truyền Comparator null cho constructor TreeMap nghĩa là dùng natural order.

Nhưng truyền null làm Comparator bên trong nullsFirst/nullsLast có ý nghĩa khác: theo hợp đồng các hàm ấy, mọi giá trị không null được xem bằng nhau.

Vì vậy Comparator.nullsFirst(null) không tự có nghĩa “null trước, những giá trị khác theo thứ tự tự nhiên”. Nếu dùng nó cho khóa "a" và "b", hai khóa không null có thể cùng vị trí so sánh.

Hãy truyền rõ Comparator.naturalOrder() hoặc một Comparator đúng cho các giá trị không null. Đối chiếu [Comparator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html#nullsFirst(java.util.Comparator)).

## 4. Ví dụ Java 17: natural order, nullsFirst và nullsLast

Lưu thành **TreeMapNullPolicy.java**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.TreeMap;

public class TreeMapNullPolicy {
    public static void main(String[] args) {
        NavigableMap<String, String> natural = new TreeMap<>();
        try {
            natural.put(null, "not accepted");
        } catch (NullPointerException expected) {
            System.out.println("natural null key: NullPointerException");
        }

        natural.put("beta", null);
        System.out.println("get beta: " + natural.get("beta"));
        System.out.println("contains beta: " + natural.containsKey("beta"));
        System.out.println("get missing: " + natural.get("missing"));
        System.out.println("contains missing: " + natural.containsKey("missing"));
        System.out.println("default beta: " + natural.getOrDefault("beta", "fallback"));
        System.out.println("default missing: " + natural.getOrDefault("missing", "fallback"));

        NavigableMap<String, String> first = new TreeMap<>(
                Comparator.nullsFirst(Comparator.naturalOrder()));
        first.put("b", "B");
        first.put(null, "NULL");
        first.put("a", "A");
        first.put(null, "UPDATED");
        System.out.println("nullsFirst keys: " + new ArrayList<>(first.keySet()));
        System.out.println("null-key value: " + first.get(null));
        System.out.println("nullsFirst size: " + first.size());

        NavigableMap<String, String> last = new TreeMap<>(
                Comparator.nullsLast(Comparator.naturalOrder()));
        last.putAll(first);
        System.out.println("nullsLast keys: " + new ArrayList<>(last.keySet()));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TreeMapNullPolicy.java
java TreeMapNullPolicy
```

Stdout:

```text
natural null key: NullPointerException
get beta: null
contains beta: true
get missing: null
contains missing: false
default beta: null
default missing: fallback
nullsFirst keys: [null, a, b]
null-key value: UPDATED
nullsFirst size: 3
nullsLast keys: [a, b, null]
```

Các chương trình trong bài được biên dịch với --release 17 và chạy trên OpenJDK 24.0.2. Cờ này chọn mức ngôn ngữ/API và bytecode đích, không thay phiên bản JVM thực thi.

### 4.1. Cùng trả null nhưng trạng thái khác nhau

Beta có ánh xạ với value null, còn missing không có ánh xạ. `containsKey` cho hai kết quả khác nhau.

`getOrDefault` beta vẫn trả null; getOrDefault missing trả fallback.

### 4.2. Thứ tự null là một lựa chọn được công bố

Map first cho dãy null,a,b; map last cho a,b,null. Việc bọc Comparator xác định vị trí null theo quy tắc rõ ràng.

`put` khóa null lần thứ hai thay "NULL" bằng "UPDATED", nên size vẫn là 3.

### 4.3. Một cách chạy thành công không mở rộng hợp đồng

Không dùng một lời gọi tình cờ không lỗi trong một trạng thái đặc biệt để kết luận mọi truy vấn null đều hợp lệ.

Nếu mô hình khóa không cho null, hãy giữ điều kiện đó ở ranh giới dữ liệu. Nếu cho phép, kiểm tra cả thêm, tìm và điều hướng theo Comparator đã chọn.

## 5. Hợp đồng thứ tự và tính nhất quán với equals là hai yêu cầu

### 5.1. Comparator cần tạo thứ tự có quy luật

Một quan hệ dùng cho TreeMap cần tuân hợp đồng Comparator, trong đó có:

- Đảo hai đối số phải đảo dấu kết quả so sánh.
- Quan hệ lớn hơn phải có tính bắc cầu.
- Hai đối tượng so bằng 0 phải có cùng quan hệ dấu khi so với một đối tượng thứ ba.

Kết quả compare không bắt buộc chỉ là -1, 0 hoặc 1; dấu âm, 0 và dấu dương mới là điều quan trọng.

Một phép trừ int trực tiếp để so có thể tràn số. Dùng Integer.compare hoặc Comparator.comparingInt giúp tránh lỗi ấy trong trường hợp số nguyên thông thường.

Một Comparator trả kết quả ngẫu nhiên hoặc đổi chiều tùy lần gọi không tạo cơ sở ổn định cho cây tìm kiếm.

### 5.2. “Nhất quán với equals” có nghĩa chính xác

Với mọi cặp khóa trong miền được hỗ trợ, điều kiện mong muốn là:

```text
compare(a, b) == 0  ⇔  a.equals(b)
```

Với miền cho phép null, cần hiểu quan hệ bằng của cả null theo chính sách đã chọn; có thể diễn đạt phép kiểm tra bằng Objects.equals cho miền ấy.

Đây là điều kiện **hai chiều**:

| Trường hợp lệch | Điều có thể xảy ra với TreeMap |
| --- | --- |
| `equals` false nhưng compare bằng 0 | Những khóa khác nhau theo equals bị xem cùng một vị trí |
| `equals` true nhưng compare khác 0 | Những đối tượng cùng khóa theo equals có thể bị tách theo thứ tự |

Chỉ kiểm tra một chiều không đủ.

### 5.3. Một Comparator không nhất quán với equals vẫn có thể có thứ tự hợp lệ

Comparator chỉ nhìn priority của một đối tượng có thể so sánh đúng các mức priority và tuân tính bắc cầu. Nhưng nếu equals của đối tượng xét cả priority lẫn id, hai id khác nhau cùng priority sẽ so bằng 0 dù equals false.

Đây không nhất thiết là một Comparator ngẫu nhiên hay sai tính bắc cầu. Vấn đề là nó định nghĩa lớp tương đương khác với equals của khóa.

TreeMap có hành vi theo quan hệ so sánh ấy. Tài liệu lớp nêu rằng khi thứ tự không nhất quán với equals, cách hành xử của sorted map vẫn được xác định theo thứ tự, nhưng không tuân đầy đủ hợp đồng Map chung.

Không mô tả hiện tượng gộp khóa như cây “làm mất dữ liệu ngẫu nhiên” khi chính Comparator đã nói hai khóa là cùng vị trí.

## 6. Compare bằng 0 ảnh hưởng cả put, get và remove

Khi tìm trong TreeMap, kết quả compare bằng 0 nghĩa đã tới vị trí khóa được xem tương đương.

Hệ quả:

1. `put` một khóa so bằng 0 với khóa đã có có thể thay giá trị của ánh xạ ấy, không tăng size.
2. `get` bằng một đối tượng khác equals nhưng so bằng 0 vẫn có thể tìm cùng ánh xạ.
3. Remove theo một đối tượng so bằng 0 có thể xóa ánh xạ thuộc vị trí tương đương ấy.
4. Điều hướng theo khóa cũng dùng quan hệ so sánh, không tự chuyển sang equals ở bước cuối.

Vì vậy Comparator không chỉ là một tùy chọn trình bày thứ tự khi in. Nó tham gia vào cách TreeMap nhận diện vị trí khóa.

Nếu yêu cầu chỉ là “sắp xếp các mục để hiển thị”, cần cân nhắc việc giữ một Map theo định danh ổn định rồi tạo một dãy đã sắp xếp để hiển thị. Không nhất thiết phải dùng toàn bộ đối tượng hiển thị làm khóa TreeMap.

## 7. Ví dụ Java 17: priority trùng và BigDecimal

Ví dụ đầu dùng record Item(priority, id). `equals` của record xét cả hai thành phần; id được yêu cầu khác null.

Comparator chỉ theo priority sẽ gộp hai Item có cùng priority. Comparator đầy đủ theo priority rồi id phân biệt chúng trong miền dữ liệu này.

Ví dụ tiếp theo dùng BigDecimal để thấy một kiểu thư viện có natural order không nhất quán với equals.

Lưu thành **ComparisonIdentityDemo.java**:

```java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class ComparisonIdentityDemo {
    record Item(int priority, String id) {
        Item {
            Objects.requireNonNull(id, "id");
        }
    }

    public static void main(String[] args) {
        Item a = new Item(1, "A");
        Item b = new Item(1, "B");
        Comparator<Item> byPriority = Comparator.comparingInt(Item::priority);
        Map<Item, String> grouped = new TreeMap<>(byPriority);
        grouped.put(a, "first");
        System.out.println("items equal: " + a.equals(b));
        System.out.println("priority comparison: " + byPriority.compare(a, b));
        System.out.println("old value on second put: " + grouped.put(b, "second"));
        System.out.println("priority-only size: " + grouped.size());
        System.out.println("lookup A: " + grouped.get(a));
        System.out.println("lookup B: " + grouped.get(b));

        Comparator<Item> complete = byPriority.thenComparing(Item::id);
        Map<Item, String> separate = new TreeMap<>(complete);
        separate.put(a, "first");
        separate.put(b, "second");
        System.out.println("complete comparison: " + complete.compare(a, b));
        System.out.println("complete size: " + separate.size());

        BigDecimal x = new BigDecimal("1.0");
        BigDecimal y = new BigDecimal("1.00");
        System.out.println("decimal equals: " + x.equals(y));
        System.out.println("decimal compareTo: " + x.compareTo(y));
        Map<BigDecimal, String> tree = new TreeMap<>();
        Map<BigDecimal, String> hash = new HashMap<>();
        tree.put(x, "one");
        tree.put(y, "two");
        hash.put(x, "one");
        hash.put(y, "two");
        System.out.println("decimal TreeMap size: " + tree.size());
        System.out.println("decimal HashMap size: " + hash.size());

        Comparator<BigDecimal> valueThenScale = Comparator
                .<BigDecimal>naturalOrder()
                .thenComparingInt(BigDecimal::scale);
        Map<BigDecimal, String> scaleAware = new TreeMap<>(valueThenScale);
        scaleAware.put(x, "one");
        scaleAware.put(y, "two");
        System.out.println("value-and-scale size: " + scaleAware.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ComparisonIdentityDemo.java
java ComparisonIdentityDemo
```

Stdout:

```text
items equal: false
priority comparison: 0
old value on second put: first
priority-only size: 1
lookup A: second
lookup B: second
complete comparison: -1
complete size: 2
decimal equals: false
decimal compareTo: 0
decimal TreeMap size: 1
decimal HashMap size: 2
value-and-scale size: 2
```

### 7.1. Priority-only đang giữ một vị trí cho mỗi priority

A và B khác equals, nhưng cùng priority nên compare bằng 0. `put` B trả về giá trị cũ first và thay nó bằng second.

`size` bằng 1 và cả get A lẫn get B cùng tìm thấy second. Điều này theo đúng quan hệ so sánh đã cung cấp.

Nếu yêu cầu là giữ hai đối tượng riêng, Comparator priority-only không diễn đạt yêu cầu đó.

### 7.2. Comparator đầy đủ phù hợp với record này

`thenComparing` id chỉ chạy khi priority bằng nhau. Hai Item chỉ so bằng 0 khi cả priority và id bằng nhau, phù hợp với equals của record trong ví dụ.

Vì vậy hai khóa A và B được giữ riêng, size bằng 2.

Không suy ra thêm một tie-breaker id bất kỳ luôn làm mọi kiểu khóa nhất quán; còn phải biết equals của kiểu khóa ấy xét điều gì.

### 7.3. BigDecimal phân biệt giá trị số với scale

BigDecimal("1.0") và BigDecimal("1.00") có compareTo bằng 0 nhưng equals false: equals còn xét scale.

Vì vậy TreeMap theo natural order có một vị trí so sánh, còn HashMap dùng equals/hashCode giữ hai khóa theo quy tắc của kiểu này.

Đây là đặc tính được mô tả trong [BigDecimal Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html), không phải lỗi băm hoặc lỗi cân bằng cây.

Nếu mô hình cần phân biệt theo cả giá trị số và scale, Comparator theo giá trị rồi scale là một lựa chọn có ý nghĩa như phần scaleAware trong chương trình.

Nếu mô hình chỉ quan tâm giá trị số, có thể thiết kế kiểu khóa hoặc bước chuẩn hóa nhất quán với cách nhận diện đó. Chuẩn hóa là một quyết định dữ liệu: không áp tùy tiện nếu scale là thông tin cần giữ.

## 8. Tie-breaker chỉ đúng khi đi cùng mô hình định danh

Giả sử equals của một kiểu thực thể chỉ xét id, nhưng Comparator xét priority rồi id.

Hai đối tượng cùng id nhưng có priority khác nhau vẫn equals true, trong khi Comparator khác 0. Thêm id ở cuối chưa giải quyết chiều lệch này.

Ngược lại, nếu equals xét nhiều thành phần nhưng Comparator bỏ một thành phần có khả năng phân biệt khóa, vẫn có thể gộp các khóa khác equals.

Cần lựa chọn mô hình rõ ràng:

| Yêu cầu | Hướng biểu diễn cần cân nhắc |
| --- | --- |
| Một ánh xạ theo id ổn định | Dùng khóa định danh phù hợp |
| Nhiều mục cùng priority | Giữ danh sách/nhóm theo priority hoặc dùng khóa tổng hợp thật sự phân biệt |
| Sắp xếp để hiển thị | Sắp xếp một dãy mục thay vì thay định danh của map |
| Vừa tra theo id vừa có chỉ mục thứ tự | Thiết kế hai chỉ mục với quy trình cập nhật nhất quán |
| Một khóa tổng hợp là định danh thực | `equals` và Comparator phải cùng thể hiện các thành phần định danh ấy |

Không đưa identityHashCode hoặc một số ngẫu nhiên vào làm tie-breaker chỉ để “không còn compare bằng 0”. Cách đó không tự khớp equals, có thể có va chạm hoặc làm mất ý nghĩa của định danh.

Mục tiêu không phải ép mọi cặp đối tượng có thứ tự khác nhau; mục tiêu là quan hệ thứ tự và quan hệ cùng khóa phù hợp với nhau.

## 9. Vì sao sửa khóa sau khi chèn có thể làm mất đường tìm

### 9.1. Cây lưu vị trí, không liên tục tính lại vị trí

Khi chèn một khóa, TreeMap dùng kết quả so sánh ở thời điểm đó để tổ chức cây. Map giữ tham chiếu tới đối tượng khóa; nó không tự chụp một bản sao bất biến của mọi trường.

Nếu sửa trường mà Comparator đang dùng, kết quả so sánh hiện tại có thể khác kết quả đã dùng để đặt khóa vào cây. Đối tượng vẫn còn được tham chiếu trong một nút, nhưng đường tìm từ gốc theo thứ tự mới có thể đi về phía khác.

Việc duyệt thấy đối tượng không chứng minh get bằng chính tham chiếu ấy sẽ tìm được. Duyệt đi qua các liên kết đang có; tìm theo khóa chọn đường bằng kết quả so sánh hiện tại.

Đây là lý do vấn đề không được giải quyết bằng câu “tôi vẫn dùng đúng object cũ”.

### 9.2. Phân biệt hai loại ràng buộc ổn định

Có hai điều cần giữ:

- Không thay đổi những thông tin ảnh hưởng quan hệ bằng của một khóa trong khi nó đang làm khóa.
- Với TreeMap, quan hệ thứ tự dùng để tổ chức và tìm khóa cũng phải tiếp tục ổn định, phù hợp với cây đã tạo.

Hợp đồng [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html) nói hành vi không được xác định khi một đối tượng khóa bị thay đổi theo cách ảnh hưởng phép so sánh equals trong lúc nó nằm trong map.

Riêng cơ chế tìm kiếm có thứ tự còn giải thích vì sao sửa một trường Comparator đọc là nguy hiểm, kể cả khi một thiết kế equals khác không đọc trường ấy. Comparator không thể đổi cách chia trái/phải rồi mong các vị trí cũ tự thích nghi.

Không đồng nhất vấn đề này với va chạm hash: TreeMap không dùng hash để chọn đường tìm. Sửa mỗi hashCode mà không đổi equals hay thứ tự không phải cùng cơ chế hỏng đường tìm vừa mô tả, mặc dù thiết kế kiểu dữ liệu vẫn phải tuân hợp đồng Java của nó.

### 9.3. Final và record chưa luôn có nghĩa bất biến sâu

Một biến tham chiếu final chỉ ngăn gán biến ấy sang đối tượng khác. Nó không tự ngăn thay đổi dữ liệu bên trong đối tượng được tham chiếu.

Record có các thành phần final, nhưng nếu một thành phần là danh sách hay đối tượng khả biến, nội dung bên trong vẫn có thể thay đổi. Nếu Comparator hoặc equals đọc nội dung đó, khóa vẫn có thể thay đổi về mặt quan hệ.

Trong ví dụ an toàn ở phần sau, StableKey chỉ chứa int và String. Không có dữ liệu khả biến bên trong hai thành phần này, nên các thông tin dùng cho equals và Comparator được giữ ổn định.

Không suy từ một ví dụ record an toàn sang mọi record bất kỳ.

### 9.4. Không chỉ đối tượng khóa mới có thể thay đổi thứ tự

Một Comparator có thể đọc trạng thái bên ngoài: bảng ưu tiên có thể sửa, cấu hình đổi chiều hoặc quy tắc phân loại đang thay đổi.

Nếu sửa trạng thái đó làm cùng một cặp khóa cho kết quả so sánh khác trước, cây hiện có cũng không được tự sắp xếp lại. Ngay cả khóa hoàn toàn bất biến cũng không đủ cứu một Comparator đổi quy tắc giữa vòng đời map.

Khi cần một thứ tự mới, hãy tạo một map mới với quy tắc ổn định mới và xây lại chỉ mục từ dữ liệu đáng tin cậy. Không chỉ sửa một cờ trong Comparator của map đang sử dụng.

## 10. Ví dụ Java 17: khóa khả biến và cách thay khóa ổn định

Chương trình có hai thí nghiệm độc lập:

1. Cố ý sửa rank của khóa đang nằm trong một map để quan sát lỗi thiết kế.
2. Dùng một map khác với khóa bất biến, bỏ khóa cũ rồi thêm khóa mới.

Trong thí nghiệm thứ nhất, rank nằm trong cả equals và Comparator. Việc sửa rank khi khóa đang ở trong map rơi vào trường hợp mà hợp đồng Map không xác định hành vi.

**Các dòng bắt đầu bằng observed là kết quả quan sát trên OpenJDK 24.0.2 trong đúng lần chạy này. Chúng không phải kết quả được Java bảo đảm cho mọi cây, mọi thứ tự chèn hoặc mọi phiên bản JVM.**

Lưu thành **MutableTreeKeyDemo.java**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class MutableTreeKeyDemo {
    static final class MutableKey {
        private int rank;
        private final String id;

        MutableKey(int rank, String id) {
            this.rank = rank;
            this.id = Objects.requireNonNull(id, "id");
        }

        int rank() {
            return rank;
        }

        String id() {
            return id;
        }

        void setRank(int rank) {
            this.rank = rank;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MutableKey key
                    && rank == key.rank && id.equals(key.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rank, id);
        }

        @Override
        public String toString() {
            return id + "@" + rank;
        }
    }

    record StableKey(int rank, String id) {
        StableKey {
            Objects.requireNonNull(id, "id");
        }

        @Override
        public String toString() {
            return id + "@" + rank;
        }
    }

    public static void main(String[] args) {
        Comparator<MutableKey> mutableOrder = Comparator
                .comparingInt(MutableKey::rank)
                .thenComparing(MutableKey::id);
        Map<MutableKey, String> broken = new TreeMap<>(mutableOrder);
        MutableKey a = new MutableKey(1, "A");
        broken.put(a, "value-A");
        broken.put(new MutableKey(2, "B"), "value-B");
        broken.put(new MutableKey(3, "C"), "value-C");
        System.out.println("before mutation: " + new ArrayList<>(broken.keySet()));

        a.setRank(4);
        System.out.println("observed keys after mutation: " + new ArrayList<>(broken.keySet()));
        System.out.println("observed get mutated key: " + broken.get(a));
        System.out.println("observed contains mutated key: " + broken.containsKey(a));

        Comparator<StableKey> stableOrder = Comparator
                .comparingInt(StableKey::rank)
                .thenComparing(StableKey::id);
        Map<StableKey, String> safe = new TreeMap<>(stableOrder);
        StableKey oldKey = new StableKey(1, "A");
        safe.put(oldKey, "value-A");
        safe.put(new StableKey(2, "B"), "value-B");
        safe.put(new StableKey(3, "C"), "value-C");
        String value = safe.remove(oldKey);
        StableKey newKey = new StableKey(4, "A");
        safe.put(newKey, value);
        System.out.println("safe keys: " + new ArrayList<>(safe.keySet()));
        System.out.println("safe lookup: " + safe.get(newKey));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MutableTreeKeyDemo.java
java MutableTreeKeyDemo
```

Stdout trên môi trường nêu trên:

```text
before mutation: [A@1, B@2, C@3]
observed keys after mutation: [A@4, B@2, C@3]
observed get mutated key: null
observed contains mutated key: false
safe keys: [B@2, C@3, A@4]
safe lookup: value-A
```

### 10.1. Dãy quan sát không tự đổi thành thứ tự mới

Trước khi sửa, các rank được duyệt theo 1, 2, 3.

Sau khi sửa A thành rank 4, lần chạy này vẫn duyệt theo các vị trí đã có, tạo dãy A@4, B@2, C@3. Đó không còn là thứ tự tăng dần theo Comparator hiện tại.

Map không nhận thông báo setRank để tự tháo nút rồi chèn lại.

### 10.2. Cùng một tham chiếu vẫn có thể không tìm thấy

Trong lần chạy này, get(a) trả null và containsKey(a) trả false sau khi rank đổi.

Giá trị của A ban đầu là chuỗi khác null, nên kết quả get null ở thí nghiệm này không phải một ánh xạ hợp lệ đang chứa value null như ví dụ phần 4.

Tuy nhiên, không chuyển quan sát này thành quy tắc “sửa khóa thì get chắc chắn trả null”. Một hình dạng cây khác có thể tình cờ vẫn tìm thấy khóa, và việc tình cờ đó không làm cách dùng trở nên đúng.

Cũng không viết kiểm thử yêu cầu mọi phiên bản JVM phải luôn cho đúng ba dòng observed này. Đây là minh họa cho điều kiện sử dụng bị vi phạm, không phải hợp đồng kết quả cần đóng đinh.

### 10.3. Map an toàn thay đối tượng khóa thay vì sửa nó tại chỗ

Phần safe dùng một cây riêng với StableKey. Khóa oldKey được remove khi vẫn còn rank 1, nên đường tìm còn phù hợp với vị trí cũ.

Sau đó chương trình tạo một StableKey mới có rank 4 rồi put lại giá trị. Cây có cơ hội tổ chức ánh xạ mới theo đúng thứ tự hiện tại.

Do ví dụ biết chắc khóa cũ tồn tại và value là chuỗi khác null, biến value nhận lại được "value-A". Ứng dụng cho phép value null cần xử lý thông tin tồn tại riêng, không suy từ remove trả null rằng khóa cũ vắng mặt.

## 11. Quy trình cập nhật và phục hồi chỉ mục

### 11.1. Chọn dữ liệu ổn định làm khóa

Nếu map tra cứu đơn hàng theo mã đơn, mã ổn định thường là khóa tự nhiên hơn toàn bộ đối tượng đơn hàng đang thay đổi.

Giá, trạng thái xử lý hoặc mức ưu tiên có thể nằm ở value. Sửa value không làm đổi vị trí khóa nếu Comparator chỉ đọc khóa và không tham chiếu ngược sang những dữ liệu vừa sửa.

Nếu bài toán thật sự cần chỉ mục theo ưu tiên rồi mã, dùng một ảnh chụp khóa ổn định phù hợp với các thành phần đó. Đối tượng nghiệp vụ có thể thay đổi ở nơi khác; khóa đang nằm trong chỉ mục giữ nguyên giá trị cho tới khi được tháo ra.

Đây là lựa chọn mô hình dữ liệu, không phải yêu cầu mọi đối tượng trong chương trình đều bất biến.

### 11.2. Khi cần đổi khóa, tháo ánh xạ khi khóa cũ còn hợp lệ

Một quy trình có kiểm soát cần xác định:

1. Ánh xạ cũ có tồn tại không, kể cả khi value được phép null?
2. Khóa mới có hợp lệ về null, kiểu và Comparator không?
3. Khóa mới có trùng một vị trí đang có theo Comparator không?
4. Nếu trùng, nghiệp vụ muốn từ chối, hợp nhất hay thay thế giá trị?
5. Khi đã chốt chính sách, tháo khóa cũ khi nó còn hợp lệ và chèn khóa mới ổn định.

Không nên sửa khóa cũ trước rồi mới gọi remove bằng khóa đã sửa. Lúc ấy chính thao tác remove có thể không tìm đúng đường nữa.

Ví dụ phần 10 là trường hợp đơn giản: không có va chạm khóa mới, khóa cũ chắc chắn tồn tại, dữ liệu được dùng trong một luồng. Với dữ liệu dùng chung, các bước kiểm tra và cập nhật còn cần được bảo vệ thành một thao tác nghiệp vụ nhất quán.

Chỉ bọc từng lời gọi riêng lẻ trong một cơ chế đồng bộ không tự làm cả chuỗi kiểm tra–xóa–thêm trở thành nguyên tử.

### 11.3. Đừng dùng remove trả null làm toàn bộ quyết định

Nếu map có thể chứa value null, cả hai tình huống sau đều có thể làm remove trả null:

- Không có ánh xạ cần xóa.
- Đã có và đã xóa một ánh xạ có value null.

Ứng dụng cần giữ thông tin tồn tại bằng cách thích hợp trong cùng phạm vi kiểm soát cập nhật. Khi dùng containsKey rồi remove, phải bảo đảm dữ liệu không bị một thao tác khác thay đổi ở giữa nếu tính đúng phụ thuộc hai bước đó.

Vấn đề này độc lập với việc khóa có bất biến hay không: khóa ổn định giúp tìm đúng vị trí, nhưng không thay ý nghĩa của kết quả null.

### 11.4. Nếu khóa đã bị sửa, ưu tiên nguồn dữ liệu chuẩn

Cách sửa thiết kế đáng tin là lấy dữ liệu chuẩn, tạo khóa ổn định mới, rồi xây một chỉ mục mới dưới Comparator đã chốt.

Cần kiểm tra va chạm theo quan hệ so sánh trong quá trình xây lại. Nếu mô hình cũ đã gộp nhiều khóa khác nhau thành một vị trí, các giá trị từng bị thay thế không tự xuất hiện trở lại chỉ nhờ tạo map mới. Muốn khôi phục chúng phải có nguồn dữ liệu khác còn giữ thông tin.

Không mặc định một constructor sao chép sẽ “sửa cây”. Trong mã nguồn OpenJDK 17, constructor nhận SortedMap và một số đường tối ưu putAll có thể xây từ trình tự được coi là đã sắp xếp. Chúng không phải một giao thức phục hồi dữ liệu bị sử dụng sai. Xem [TreeMap.java của OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/TreeMap.java).

Nếu cần xây một chỉ mục theo thứ tự mới, hãy chủ động tạo map rỗng với Comparator đúng và đưa từng cặp khóa ổn định–giá trị từ nguồn chuẩn vào đó. Vẫn phải giải quyết khóa trùng theo yêu cầu nghiệp vụ; việc gọi put không tự quyết định thay bạn.

Không hứa một chiến thuật phục hồi tổng quát cho mọi trạng thái có được sau khi đã vi phạm điều kiện sử dụng.

## 12. Ghép ba chính sách thành một thiết kế rõ ràng

### 12.1. Viết chính sách trước khi chọn Comparator

Một ví dụ yêu cầu có thể là:

- Khóa là cặp priority và id.
- Cả đối tượng khóa và id đều không được null.
- Priority và id không đổi khi khóa nằm trong map.
- Hai khóa bằng nhau khi cả hai thành phần bằng nhau.
- Thứ tự là priority tăng dần rồi id tăng dần.
- Giá trị được phép null, nên sự tồn tại được kiểm tra tách với get.
- Khi đổi priority, ứng dụng tạo khóa mới và cập nhật chỉ mục theo quy trình.

Những quyết định này giải thích vì sao Comparator theo priority rồi id phù hợp. Chỉ nhìn một dòng comparingInt mà không biết định danh, null và vòng đời khóa thì chưa đủ đánh giá thiết kế.

Một bài toán khác có thể chọn chính sách khác. Điều cần giữ là các quyết định không mâu thuẫn với nhau.

### 12.2. Kiểu generic rộng không bảo đảm mọi khóa so được với nhau

TreeMap<Object, V> dùng natural order không tự biến mọi Object thành một miền có thứ tự chung. Một số nguyên và một chuỗi không có nghĩa tự nhiên là luôn so sánh được với nhau.

Tương tự, Comparator chấp nhận một kiểu khai báo rộng có thể vẫn đọc một trường chỉ có ở một số đối tượng hoặc từ chối một phần miền giá trị.

Hãy xác định miền khóa thực tế, kiểm tra mọi cặp cần hỗ trợ và xử lý dữ liệu không hợp lệ ở ranh giới nhập. Không dựa vào việc một khóa đầu tiên đã put thành công để suy ra mọi khóa tương lai đều hợp lệ.

### 12.3. Null không phải công cụ chữa khóa bị gộp

Bọc Comparator bằng nullsFirst/nullsLast chỉ bổ sung chính sách cho null. Nó không tự làm phần so sánh các khóa không null nhất quán với equals.

Nếu Comparator bên trong chỉ đọc priority, hai đối tượng khác id cùng priority vẫn có thể so bằng 0 sau khi được bọc.

Ngược lại, bổ sung tie-breaker đúng cho định danh không tự giải quyết null của một trường bên trong. Mỗi tầng phải có chính sách phù hợp.

### 12.4. Đồng bộ không thay thế tính đúng của khóa

Một TreeMap được bảo vệ truy cập bởi khóa đồng bộ vẫn có thể sai thiết kế nếu một luồng sửa trường sắp xếp của khóa khi nó đang nằm trong cây.

Đồng bộ trả lời ai được truy cập và vào thời điểm nào. Nó không tự thay Comparator, giữ ổn định dữ liệu khóa hoặc xây lại các nút sau thay đổi.

Tương tự, chuyển sang một cấu trúc có thứ tự hỗ trợ truy cập đồng thời không tự loại bỏ yêu cầu về quan hệ so sánh và vòng đời khóa.

## 13. Kiểm tra hợp đồng bằng các tình huống có ý nghĩa

### 13.1. Kiểm tra Comparator độc lập trước

Với các khóa thuộc miền hỗ trợ, cần kiểm tra:

| Điều cần kiểm tra | Dấu hiệu mong đợi |
| --- | --- |
| So một khóa với chính nó | Kết quả bằng 0 |
| Đảo a và b | Dấu kết quả đảo chiều |
| a lớn hơn b, b lớn hơn c | a lớn hơn c |
| a và b so bằng 0 | So a với c và b với c phải cho cùng dấu |
| So sánh và equals | Compare bằng 0 khi và chỉ khi Objects.equals đúng trong miền đã chọn |
| Cùng cặp khóa, trạng thái hợp lệ không đổi | Kết quả không đổi qua các lần gọi |

Dùng cả các giá trị biên số nguyên, như MIN_VALUE và MAX_VALUE, để phát hiện cách so bằng phép trừ dễ tràn.

Dùng nhiều đối tượng mới có cùng dữ liệu để tránh kiểm tra duy nhất bằng cùng một tham chiếu. Một Comparator chỉ đúng cho đúng object đã có chưa đủ cho map theo giá trị.

Không lấy vài trường hợp điển hình làm chứng minh toán học cho toàn bộ miền vô hạn. Chúng là kiểm thử phát hiện lỗi; lập luận theo định nghĩa các thành phần vẫn cần thiết.

### 13.2. Kiểm tra null ở đúng tầng

Nếu cấm null, kiểm tra đầu vào bị từ chối theo chính sách đã công bố. Nếu cho phép, kiểm tra null với null, null với khóa thường và các khóa thường với nhau.

Nếu đối tượng khóa có trường có thể null, thêm riêng các trường hợp trường null. Không dùng kết quả xử lý cả đối tượng null để thay cho kiểm tra trường null.

Với nullsFirst/nullsLast, kiểm tra cả hành vi Comparator bên trong. Một lớp bọc hợp lệ không thể sửa phép so sánh sai của phần bên trong.

### 13.3. Kiểm tra thao tác map theo mô hình định danh

Một bộ kiểm tra có ý nghĩa nên bao gồm:

- Hai khóa khác nhau thật sự phải được giữ riêng theo định danh đã chọn.
- Một đối tượng mới bằng khóa cũ phải tìm lại đúng ánh xạ.
- `put` khóa tương đương cập nhật đúng giá trị và không tăng số ánh xạ.
- Remove khóa tương đương tác động đúng ánh xạ.
- Duyệt theo thứ tự đã công bố khi mọi điều kiện sử dụng đều hợp lệ.
- Giá trị null và khóa vắng mặt được phân biệt khi nghiệp vụ cần.
- Thay khóa bằng quy trình an toàn không làm mất dữ liệu hoặc ghi đè ngoài chính sách.

Nếu Comparator cố ý không nhất quán với equals, cần nhận diện rõ quyết định đó và hệ quả với hợp đồng Map. Không viết kiểm thử giả định TreeMap dùng equals để tách các khóa trong trường hợp ấy.

### 13.4. Kiểm tra khóa ổn định và giới hạn của thí nghiệm sai

Xem xét mọi trường Comparator, compareTo, equals và hashCode đọc, cùng mọi dữ liệu khả biến có thể được truy cập gián tiếp từ chúng.

Kiểm thử quy trình đúng bằng khóa mới có cùng dữ liệu, không chỉ kiểm tra get bằng tham chiếu ban đầu. Với chỉ mục có nhiều thành phần, đổi một thành phần phải đi qua quy trình cập nhật đã thiết kế.

Phần cố ý sửa khóa trong bài chỉ giúp nhìn thấy một biểu hiện của việc vi phạm điều kiện. Không dùng các giá trị observed làm đặc tả cho ứng dụng.

## 14. Các lỗi phổ biến và cách nhận diện

| Nhận định hoặc cách làm | Vấn đề | Hướng xử lý |
| --- | --- | --- |
| TreeMap cho value null nên key cũng null được | Hai chính sách độc lập | Xem natural order hoặc Comparator cụ thể |
| `get` null nghĩa là không có khóa | Có thể là value null | Kiểm tra sự tồn tại khi cần |
| `nullsFirst`(null) dùng natural order cho phần còn lại | Các khóa không null bị coi là bằng nhau | Truyền Comparator bên trong rõ ràng |
| Comparator chỉ dùng để in có thứ tự | Nó quyết định cả vị trí khóa | Kiểm tra put/get/remove và định danh |
| Hai object khác nhau thì chắc có hai ánh xạ | Compare bằng 0 có thể gộp chúng | Đối chiếu quan hệ so sánh với equals |
| Comparator bắc cầu thì luôn tuân hợp đồng Map | Vẫn có thể lệch equals | Kiểm tra cả hai chiều compare bằng 0 và equals |
| Thêm id ở cuối luôn giải quyết được | `equals` có thể chỉ xét id hoặc xét thành phần khác | Thiết kế từ định danh thật |
| Biến final làm đối tượng khóa bất biến | Dữ liệu bên trong còn có thể sửa | Kiểm tra toàn bộ dữ liệu được quan hệ khóa đọc |
| Sửa khóa xong rồi remove để sửa lại | Remove cũng có thể không tìm đúng đường | Tháo khi khóa cũ còn hợp lệ |
| Duyệt còn thấy khóa thì get chắc thấy | Duyệt và tìm chọn đường khác nhau | Giữ ổn định quan hệ khóa |
| Đổi chiều Comparator thì cây tự đổi chiều | Cây không tự tổ chức lại | Xây chỉ mục mới theo quy tắc mới |
| Chạy một lần vẫn đúng nên cách sửa khóa an toàn | Đã vượt điều kiện hợp đồng | Kiểm tra thiết kế, không dựa vào may mắn |
| Sao chép SortedMap chắc chắn sửa dữ liệu sai | Có đường xây dựng tin thứ tự đầu vào | Dùng nguồn chuẩn và khóa ổn định |
| Đồng bộ truy cập chữa được khóa khả biến | Đồng bộ không tái lập thứ tự đã bị thay đổi | Thiết kế khóa đúng và bảo vệ quy trình cập nhật |

## 15. Bài tập tự kiểm tra

### Bài 1. Value null khác khóa vắng mặt thế nào?

Một TreeMap natural order có "x" → null. `get`("x") và get("y") đều trả null. Cần dùng gì để phân biệt, và getOrDefault("x", "fallback") trả gì?

**Gợi ý trả lời:** containsKey phân biệt sự tồn tại. `getOrDefault` với "x" vẫn trả null vì ánh xạ đã có; với "y" mới dùng fallback.

### Bài 2. Cho null đứng đầu đã đủ chưa?

Một Comparator ngoài dùng nullsFirst, nhưng Comparator bên trong chỉ so priority. Hai khóa không null khác id cùng priority có được giữ riêng không?

**Gợi ý trả lời:** Không chỉ nhờ lớp bọc null. Hai khóa vẫn so bằng 0 theo phần bên trong, nên TreeMap xem cùng một vị trí.

### Bài 3. Comparator có thứ tự hợp lệ nhưng lệch equals

Record Key(int level, String code) có equals mặc định. Comparator chỉ so level. Hai khóa có cùng level nhưng code khác nhau nói lên lỗi gì?

**Gợi ý trả lời:** Có thể vẫn là một thứ tự hợp lệ theo level, nhưng không nhất quán với equals. TreeMap gộp các khóa theo quan hệ so sánh, nên không đáp ứng đầy đủ hợp đồng Map chung theo equals.

### Bài 4. Tie-breaker có đủ không?

Một lớp có equals chỉ xét id. Comparator xét priority rồi id. Hai đối tượng cùng id, khác priority có thỏa điều kiện nhất quán không?

**Gợi ý trả lời:** Không. `equals` true nhưng compare khác 0. Cần thay mô hình khóa hoặc cách tạo chỉ mục theo đúng định danh nghiệp vụ.

### Bài 5. Hai BigDecimal có cùng giá trị số

Nếu dùng new BigDecimal("2.0") và new BigDecimal("2.00") làm khóa TreeMap natural order, tại sao có thể chỉ còn một ánh xạ dù equals false?

**Gợi ý trả lời:** `compareTo` so giá trị số nên bằng 0; equals còn xét scale. TreeMap dùng quan hệ so sánh để xác định cùng vị trí khóa.

### Bài 6. Cùng một tham chiếu có bảo đảm tìm lại?

Sau khi put một đối tượng khóa, sửa trường Comparator đọc rồi get bằng chính đối tượng ấy. Có thể kết luận chắc chắn tìm thấy hoặc chắc chắn không tìm thấy không?

**Gợi ý trả lời:** Không. Vị trí đã xây có thể không còn phù hợp với đường tìm mới; nếu thay đổi còn ảnh hưởng equals thì Map không xác định hành vi trong trường hợp đó. Một kết quả quan sát riêng không tạo bảo đảm.

### Bài 7. Record có luôn an toàn không?

Một record giữ tham chiếu tới danh sách khả biến; Comparator đọc nội dung danh sách. Việc các thành phần record là final đã đủ giữ thứ tự không?

**Gợi ý trả lời:** Chưa đủ. Tham chiếu không đổi nhưng nội dung được đọc có thể đổi. Cần giữ ổn định dữ liệu thực sự quyết định quan hệ khóa.

### Bài 8. Khi nào tháo khóa cũ?

Muốn đổi rank của một ánh xạ đang có, nên sửa rank rồi remove hay remove khi khóa cũ còn hợp lệ rồi tạo khóa mới?

**Gợi ý trả lời:** Tháo ánh xạ khi khóa cũ còn hợp lệ, rồi dùng khóa mới ổn định, đồng thời xử lý tồn tại, value null, va chạm khóa mới và tính nhất quán của cả quy trình khi cần.

### Bài 9. Comparator đọc một cờ đảo chiều

Mọi khóa đều bất biến nhưng Comparator đọc cờ cấu hình có thể đổi từ tăng sang giảm. Đổi cờ có tự đảo lại cây không?

**Gợi ý trả lời:** Không. Thứ tự giữa cùng các khóa đã thay đổi trong khi cấu trúc cũ vẫn còn. Tạo một chỉ mục mới theo quy tắc mới ổn định.

### Bài 10. Có nên đóng đinh kết quả khóa bị sửa?

Một thí nghiệm sửa khóa sau put cho get null trên một JVM. Kiểm thử ứng dụng có nên yêu cầu mọi JVM luôn trả null để phát hiện “lỗi TreeMap” không?

**Gợi ý trả lời:** Không. Thí nghiệm đã dùng khóa ngoài điều kiện hợp đồng. Kiểm thử phải yêu cầu quy trình hợp lệ giữ đúng ánh xạ; biểu hiện sau khi vi phạm không phải đặc tả cần bảo vệ.

## 16. Tài liệu đối chiếu

- [TreeMap — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html): quan hệ thứ tự, hợp đồng Map, chính sách null và các thao tác khóa.
- [Comparator — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html): các luật so sánh, tính nhất quán với equals, nullsFirst/nullsLast và thenComparing.
- [Map — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): ý nghĩa khóa bằng nhau, value null và điều kiện sử dụng khóa khả biến.
- [BigDecimal — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): quan hệ giữa compareTo, equals và scale.
- [TreeMap.java — OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/TreeMap.java): cơ chế tìm theo Comparator và các đường xây dựng từ dữ liệu được coi là có thứ tự. Chi tiết triển khai cần gắn với phiên bản; hợp đồng API mới là cơ sở cho cách dùng hợp lệ.


</details>

- [Quay lại đầu trang](#back-to-top)
