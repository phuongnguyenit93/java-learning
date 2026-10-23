<a id="back-to-top"></a>

# An toàn đa luồng

## Menu
- [Tranh chấp khi nhiều luồng đọc và ghi collection](#thread-safety-001)
- [Phân biệt an toàn từng thao tác với an toàn cả chuỗi thao tác](#thread-safety-002)
- [Collections.synchronizedList, synchronizedSet và synchronizedMap](#thread-safety-003)
- [Khóa đúng đối tượng khi duyệt các wrapper đồng bộ](#thread-safety-004)
- [Collection đồng thời, wrapper có khóa và bản sao bất biến](#thread-safety-005)
- [Iterator nhất quán yếu và iterator dùng ảnh chụp](#thread-safety-006)
- [Vì sao fail-fast không phải cơ chế đồng bộ hoặc kiểm tra đúng đắn](#thread-safety-007)
- [Tính nhìn thấy dữ liệu và giới hạn của việc công bố collection chỉ đọc](#thread-safety-008)

## <a id="thread-safety-001">Tranh chấp khi nhiều luồng đọc và ghi collection</a>

<details>
<summary>Xem chi tiết</summary>

# Tranh chấp khi nhiều luồng đọc và ghi collection

## 1. Vấn đề bắt đầu từ trạng thái được dùng chung

Một collection giữ nhiều thông tin liên quan: các phần tử, số lượng, vị trí hoặc liên kết giữa phần tử, và những trạng thái nội bộ phục vụ tìm kiếm hoặc duyệt. Khi nhiều luồng cùng truy cập, một thao tác ở mức mã nguồn có thể cần nhiều lần đọc–ghi bên trong.

Ví dụ, ý định “tăng bộ đếm của mã A thêm một” thường bị viết thành:

~~~java
int oldValue = counts.get("A");
counts.put("A", oldValue + 1);
~~~

Hai dòng này không tự trở thành một hành động không thể chen ngang. Một luồng khác có thể đọc cùng giá trị cũ rồi cũng ghi kết quả tăng một, khiến một lần cập nhật bị mất.

Với collection không được thiết kế cho truy cập đồng thời, còn có vấn đề ở cấp cấu trúc và khả năng nhìn thấy dữ liệu. Với collection an toàn đa luồng, vẫn có thể sai ở **chuỗi thao tác nghiệp vụ** nếu từng bước riêng được bảo vệ nhưng cả chuỗi không được bảo vệ.

Bài học cần phân biệt bốn câu hỏi:

1. Các phương thức riêng có được gọi đồng thời an toàn không?
2. Nhiều bước có cần được xem như một thao tác nguyên tử không?
3. Luồng đọc có được nhìn thấy các cập nhật theo một quan hệ đồng bộ hợp lệ không?
4. Lượt duyệt cần thấy dữ liệu sống, ảnh chụp hay một trạng thái nhất quán toàn bộ?

Không có một câu trả lời “dùng List” hoặc “đổi thành Map concurrent” giải quyết đồng thời tất cả các câu hỏi ấy.

## 2. Hai tầng tranh chấp: dữ liệu bên trong và logic bên ngoài

### 2.1. Tranh chấp trên dữ liệu chia sẻ không được đồng bộ đúng

Theo mô hình bộ nhớ Java, các truy cập xung đột lên biến chia sẻ, có ít nhất một lần ghi và không được sắp thứ tự phù hợp bởi happens-before, tạo thành data race. Quan hệ happens-before mô tả những bảo đảm về thứ tự và khả năng nhìn thấy giữa các hành động. [Java Language Specification 17 — Happens-before Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5).

Trong một collection thông thường, việc đọc các trường nội bộ trong khi luồng khác sửa chúng mà không có giao thức phù hợp không phải một cách sử dụng hợp lệ chỉ vì mỗi phần tử là một tham chiếu Java.

Kết quả không nên được dự đoán bằng cách tưởng tượng rằng mọi câu lệnh của mọi luồng chỉ xen kẽ như những khối hoàn chỉnh. Mã tối ưu và mô hình bộ nhớ còn ảnh hưởng tới điều một luồng được phép nhìn thấy.

### 2.2. Các phương thức an toàn vẫn có thể kết hợp thành một thuật toán sai

Một ConcurrentHashMap bảo vệ các thao tác của nó. Tuy nhiên:

~~~java
int oldValue = map.get("item");
map.put("item", oldValue + 1);
~~~

vẫn có khe thời gian giữa đọc và ghi. Mỗi phương thức có thể đúng theo hợp đồng riêng, nhưng cả yêu cầu “tăng một” không được biểu diễn bằng một phép cập nhật nguyên tử.

Đó là lý do cần phân biệt **an toàn của cấu trúc dữ liệu** và **đúng đắn của bất biến nghiệp vụ**. Một thuật toán có thể không làm hỏng nội bộ collection mà vẫn tạo kết quả sai.

Chương trình ở phần minh chứng chủ động dùng những collection có phương thức an toàn để tái lập các tranh chấp ở tầng chuỗi thao tác. Nó không cần gây hỏng một ArrayList không được bảo vệ rồi trông chờ một kết quả ngẫu nhiên.

## 3. ArrayList và HashMap không tự bảo vệ các lần đọc–ghi đồng thời

`ArrayList` và `HashMap` thông thường không có cơ chế đồng bộ nội bộ cho việc dùng tùy ý từ nhiều luồng. Tài liệu yêu cầu đồng bộ bên ngoài trong trường hợp nhiều luồng truy cập và có sửa đổi cấu trúc. [Java SE 17 — ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html), [Java SE 17 — HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

Ví dụ sau chỉ minh họa một cách sử dụng sai, không phải chương trình để dựa vào stdout cố định:

~~~java
// shared là cùng một ArrayList được nhiều luồng truy cập không có bảo vệ.
// Luồng ghi:
shared.add("new");

// Luồng đọc:
for (String value : shared) {
    consume(value);
}
~~~

Có thể gặp lỗi khi duyệt hoặc dữ liệu quan sát không đáp ứng yêu cầu. Không được biến một biểu hiện cụ thể thành kết luận rằng mọi lần chạy sai đều phải ném cùng ngoại lệ.

### Đọc một vị trí cũng không tự an toàn chỉ vì không duyệt Iterator

`get`, `size` hoặc `contains` đều đọc trạng thái. Trong lúc nguồn thay đổi mà không có hợp đồng phù hợp, chuyển từ vòng lặp foreach sang vòng lặp chỉ số không tự chữa tranh chấp.

Ngay cả việc không tăng/giảm kích thước chưa đủ để khẳng định mọi cập nhật đều an toàn. Một `set` thay tham chiếu phần tử hoặc một `put` thay giá trị vẫn là việc ghi dữ liệu chia sẻ; cần bảo đảm nhìn thấy và phối hợp phù hợp với ứng dụng.

### Chỉ đọc có thể đơn giản hơn nếu không còn ghi

Nhiều luồng có thể cùng đọc một cấu trúc đã được xây dựng xong, được công bố an toàn và không còn bị sửa. Điều kiện “không còn sửa” phải bao gồm mọi đường tham chiếu có khả năng thay đổi phần liên quan.

Nếu phần tử bên trong là đối tượng sửa được, collection không đổi cấu trúc vẫn chưa giải quyết việc nhiều luồng đọc–ghi trạng thái của phần tử. Collection và đối tượng phần tử là hai tầng cần xem riêng.

## 4. Mất cập nhật: hai lần tăng nhưng chỉ còn một

Giả sử bộ đếm đang là `0`. Hai luồng thực hiện chuỗi get–put:

| Thời điểm | Luồng thứ nhất | Luồng thứ hai | Giá trị trong Map |
| --- | --- | --- | --- |
| 1 | Đọc `0` | | `0` |
| 2 | | Đọc `0` | `0` |
| 3 | Ghi `1` | | `1` |
| 4 | | Ghi `1` | `1` |

Không lần ghi nào tăng từ `1` lên `2` vì cả hai luồng đã tính từ cùng giá trị cũ. Đây là mất cập nhật.

Đổi Map thành ConcurrentHashMap làm get và put có hợp đồng truy cập đồng thời, nhưng không gộp chúng thành một phép đọc–sửa–ghi duy nhất.

Với bộ đếm theo khóa, có thể dùng:

~~~java
counts.merge("item", 1, Integer::sum);
~~~

`ConcurrentHashMap.merge` thực hiện lần cập nhật theo khóa một cách nguyên tử theo hợp đồng của lớp. Nếu chưa có giá trị thì đặt `1`; nếu đã có thì gộp với `1`. [Java SE 17 — ConcurrentHashMap.merge](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#merge(K,V,java.util.function.BiFunction)).

Không suy ra cùng tính nguyên tử từ việc gọi `merge` trên một Map bất kỳ chỉ vì giao diện Map có phương thức ấy. Phải xem hợp đồng của cách cài đặt đang dùng.

Hàm gộp nên ngắn, đơn giản và không sửa lại các ánh xạ khác theo cách vi phạm hợp đồng. Một phép nguyên tử theo một khóa cũng không tự tạo giao dịch cho nhiều khóa, chẳng hạn chuyển số dư giữa hai tài khoản.

## 5. Kiểm tra rồi hành động: contains–add và size–get

### 5.1. contains rồi add có thể thêm trùng

Xét yêu cầu “chỉ thêm X nếu chưa có”:

~~~java
if (!list.contains("X")) {
    list.add("X");
}
~~~

Nếu hai luồng cùng kiểm tra trước khi luồng nào thêm, cả hai đều thấy chưa có `X`, rồi cả hai đều thêm. Kết quả có thể là `[X, X]`.

Điều này vẫn xảy ra với `Collections.synchronizedList` nếu chỉ dựa vào khóa riêng trong từng phương thức. Khóa của `contains` được nhả trước khi gọi `add`; luồng khác có thể đi qua giữa hai bước.

Nếu dùng danh sách bọc đồng bộ và cần bất biến này, bảo vệ cả đoạn:

~~~java
synchronized (list) {
    if (!list.contains("X")) {
        list.add("X");
    }
}
~~~

Tất cả các nơi liên quan phải tuân thủ giao thức truy cập trên cùng đối tượng bọc. Một luồng khác sửa trực tiếp danh sách nền hoặc dùng một khóa khác có thể phá điều kiện bảo vệ.

Với yêu cầu tập hợp duy nhất, một Set phù hợp truy cập đồng thời có thể diễn tả mô hình tốt hơn. Với CopyOnWriteArrayList, phương thức `addIfAbsent` cung cấp đúng thao tác cần thiết; không nên tự tách thành contains và add nếu cần tính nguyên tử tương ứng.

### 5.2. size rồi get có thể dùng một chỉ số đã hết hiệu lực

~~~java
int lastIndex = list.size() - 1;
String value = list.get(lastIndex);
~~~

Giữa hai dòng, luồng khác có thể xóa phần tử hoặc clear danh sách. `lastIndex` được tính từ trạng thái trước, còn `get` đọc trạng thái sau.

Trong chương trình, danh sách bọc đồng bộ ban đầu có một phần tử. Luồng đọc lấy chỉ số `0`, luồng ghi xóa hết, rồi luồng đọc gọi get và gặp IndexOutOfBoundsException. Các phương thức riêng vẫn được bảo vệ; điều kiện sử dụng giữa chúng đã thay đổi.

Cách xử lý tùy ý nghĩa yêu cầu: khóa cả chuỗi nếu phải đọc một phần tử theo trạng thái đã kiểm tra, dùng một snapshot, hoặc chọn API nguyên tử phù hợp. Với hàng đợi concurrent, chẳng hạn, thường nên dùng phép lấy phần tử có hợp đồng rõ như poll thay vì tách isEmpty rồi remove.

## 6. Một khóa chỉ có tác dụng khi mọi bên dùng cùng giao thức

Một khóa chung có hai vai trò quan trọng:

- Ngăn các đoạn được bảo vệ chạy chen vào nhau theo cách phá bất biến.
- Thiết lập quan hệ đồng bộ để cập nhật trước lần nhả khóa được nhìn thấy bởi lần lấy cùng khóa tiếp theo.

Khóa phần ghi nhưng để phần đọc đi ngoài giao thức không tự cung cấp toàn bộ bảo đảm cần có. Khóa các đối tượng khác nhau cũng không bảo vệ cùng một vùng trạng thái.

Ví dụ lỗi thiết kế:

~~~text
luồng 1 khóa lockA rồi sửa shared
luồng 2 khóa lockB rồi đọc shared
~~~

Hai luồng không tranh cùng khóa nên các khối vẫn có thể chạy đồng thời. Việc cả hai đều có từ khóa synchronized trong mã chưa đủ.

Một lớp quản lý collection có thể dùng khóa riêng và chỉ cho truy cập qua các phương thức của lớp. Điều quan trọng là không để lộ tham chiếu nền cho mã khác sửa bên ngoài giao thức ấy.

### Không giữ khóa lâu hơn yêu cầu

Đoạn cần tính nguyên tử nên đủ rộng để giữ bất biến nhưng tránh kéo theo công việc không cần thiết như I/O chậm, callback không kiểm soát hoặc chờ một luồng khác đang cần cùng khóa.

Không đặt một điểm chờ “cả hai luồng phải tới” bên trong khóa mà chỉ một luồng được vào. Khi luồng đầu giữ khóa và chờ, luồng thứ hai không vào được để tới điểm chờ: chương trình tự tạo tình huống không thể tiến triển.

Ví dụ minh chứng đặt barrier **trước** khối khóa trong ca sửa đúng. Nhờ vậy, việc phối hợp khởi đầu không làm hai luồng chờ nhau trong vùng loại trừ.

## 7. Duyệt là một chuỗi truy cập, không phải một lần gọi đơn

Một vòng foreach tạo và dùng Iterator qua nhiều bước. Nếu nguồn được bọc bởi synchronizedList, không được coi việc gọi `iterator()` là đã giữ khóa cho toàn bộ vòng lặp.

Cách dùng đúng theo hợp đồng lớp bọc:

~~~java
synchronized (list) {
    for (String value : list) {
        consume(value);
    }
}
~~~

Đây là đoạn minh họa cần `list` là danh sách bọc tương ứng và `consume` tuân thủ thiết kế của ứng dụng.

Tài liệu của synchronizedList yêu cầu đồng bộ trên chính danh sách trả về khi duyệt qua Iterator, Spliterator hoặc Stream. Mọi truy cập tới nguồn nền phải đi qua lớp bọc để bảo đảm giao thức. [Java SE 17 — Collections.synchronizedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedList(java.util.List)).

Với synchronizedMap, khi duyệt keySet, values hoặc entrySet phải khóa Map bọc theo hợp đồng của nó, không tự đổi sang khóa một khung nhìn vừa lấy ra.

### Stream không tự khóa nguồn cho cả lượt xử lý

`list.stream().filter(...).toList()` vẫn phải duyệt nguồn. Đổi cú pháp foreach sang Stream không biến một nguồn sửa được dùng chung thành ảnh chụp hoặc nguồn an toàn.

Nếu cần giữ khóa suốt một lượt Stream tuần tự, vùng khóa phải bao trùm thời gian thực hiện terminal theo chính sách lớp bọc. Với xử lý chậm hoặc cần chạy song song, thường nên tạo snapshot dưới khóa rồi xử lý snapshot bên ngoài thay vì giữ khóa qua toàn bộ công việc.

Cũng tránh để pipeline song song chạy các tác vụ lại cố lấy một khóa mà luồng gọi đang giữ trong khi chờ chúng hoàn thành. Chọn chiến lược nguồn và quyền sở hữu rõ ràng trước khi thêm parallelStream.

## 8. ConcurrentModificationException giúp phát hiện lỗi, không thay cơ chế bảo vệ

Iterator fail-fast của những collection như ArrayList có thể ném `ConcurrentModificationException` khi phát hiện sửa đổi không phù hợp.

Nhưng không được suy ra:

~~~text
không ném ngoại lệ => mọi truy cập đều an toàn
~~~

Việc phát hiện chỉ theo khả năng tốt nhất. Một lần chạy không gặp ngoại lệ có thể do lịch chạy chưa bộc lộ lỗi hoặc dạng ghi không được phép không bị cơ chế đó phát hiện theo cách đang tưởng.

Ngoại lệ này cũng không chứng minh chắc chắn có hai luồng. Ngay trong một luồng, sửa trực tiếp collection bằng cách không phù hợp trong khi Iterator đang duyệt cũng có thể làm Iterator phát hiện thay đổi.

Bắt ngoại lệ rồi lặp lại vô hạn không tạo được một thuật toán đúng. Phải quyết định dữ liệu cần được đọc theo ảnh chụp, theo trạng thái sống có chính sách đồng thời, hay dưới khóa giữ một trạng thái nhất quán.

## 9. Ba mô hình đọc dữ liệu khi có cập nhật

| Mô hình | Điều người đọc cần hiểu | Đổi lại |
| --- | --- | --- |
| Đọc dưới khóa chung | Trạng thái liên quan được giữ ổn định trong vùng khóa nếu mọi bên tuân thủ | Có thể chặn các luồng khác |
| Đọc snapshot | Người đọc dùng một tập phần tử đã cố định cho lượt xử lý | Phải trả chi phí tạo/giữ bản sao và chấp nhận dữ liệu có thể cũ |
| Duyệt nguồn concurrent | Cập nhật có thể tiếp tục theo hợp đồng cấu trúc | Không mặc nhiên có ảnh chụp nguyên tử toàn bộ |

### 9.1. Tạo snapshot cũng phải đọc nguồn hợp lệ

Với một synchronizedList:

~~~java
List<String> snapshot;
synchronized (source) {
    snapshot = List.copyOf(source);
}
~~~

Việc chép được làm trong vùng bảo vệ tương ứng. Sau đó có thể xử lý snapshot ngoài khóa nếu những bảo đảm về phần tử và công bố kết quả phù hợp.

`List.copyOf` trả danh sách không cho sửa và không phản ánh các thay đổi vị trí tiếp theo của nguồn; nó không chấp nhận phần tử null. Nếu dữ liệu hợp lệ có null, phải chọn cách sao chép và công bố khác thích hợp. [Java SE 17 — List.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)).

Không lấy một ArrayList đang bị ghi không đồng bộ rồi gọi copyOf để “làm nó an toàn”. Trước khi có bản sao, thao tác sao chép vẫn phải đọc nguồn đang có tranh chấp.

### 9.2. CopyOnWriteArrayList giữ snapshot cho Iterator

Iterator của CopyOnWriteArrayList dùng trạng thái mảng tại lúc tạo. Các cập nhật sau không làm Iterator ấy nhìn thấy phần tử mới. [Java SE 17 — CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html).

Trong ca thử, Iterator được tạo từ `[A, B]`, sau đó collection thêm `C`. Iterator vẫn thu `[A, B]`, còn collection hiện tại là `[A, B, C]`.

Cách này có ích khi đọc nhiều hơn ghi rất nhiều, nhưng mỗi cập nhật có chi phí sao chép cấu trúc. Không chọn nó làm đáp án mặc định cho một tập dữ liệu lớn bị ghi liên tục.

### 9.3. ConcurrentHashMap không đóng băng mọi khóa trong một lượt duyệt

Các lượt duyệt của ConcurrentHashMap có tính nhất quán yếu, phù hợp với cập nhật đồng thời theo hợp đồng của lớp. Chúng không tự tạo ảnh chụp toàn cục tại một thời điểm duy nhất. [Java SE 17 — ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

Một phép tăng nguyên tử theo khóa có thể đúng, nhưng tính tổng nhiều khóa trong lúc các luồng khác cập nhật vẫn cần xem yêu cầu nhất quán của báo cáo. Không gộp bảo đảm nguyên tử của một phương thức thành bảo đảm giao dịch cho toàn bộ Map.

## 10. Công bố dữ liệu, final, volatile và đối tượng phần tử

### 10.1. Công bố an toàn là điều kiện để bên đọc dùng trạng thái đã tạo

Xây dựng một collection rồi đưa cho luồng khác cần một đường công bố phù hợp, chẳng hạn quan hệ qua Thread.start, khóa chung, tham chiếu volatile hoặc các cấu trúc truyền dữ liệu có hợp đồng bộ nhớ.

Các tiện ích concurrent mô tả những quan hệ happens-before cho việc chuyển công việc hoặc dữ liệu giữa các luồng. [Java SE 17 — Memory consistency properties](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html#MemoryVisibility).

Chương trình minh chứng khởi tạo dữ liệu trước khi start các luồng, dùng barrier/latch để phối hợp và chờ các luồng kết thúc trước khi in kết quả. Nó không dùng sleep để đoán rằng “chắc luồng kia đã ghi xong”.

### 10.2. final không làm collection trở thành bất biến

~~~java
final List<String> names = new ArrayList<>();
names.add("A");
~~~

Đây là đoạn minh họa hợp lệ. `final` ngăn gán lại biến `names`, không cấm gọi add, clear hoặc sửa trạng thái phần tử.

Đừng coi việc trường giữ collection là final như một sự thay thế cho hợp đồng sửa dữ liệu. Cần xét trạng thái nào còn có thể thay đổi sau khi đối tượng được xây dựng và ai được phép thay đổi nó.

### 10.3. volatile trên tham chiếu không khóa nội dung collection

Một trường `volatile List<String>` có thể dùng để công bố việc thay cả tham chiếu tới một snapshot mới. Nhưng nó không biến các phép add/get trên cùng ArrayList thành các thao tác được tuần tự hóa.

Mô hình phù hợp là chuẩn bị một phiên bản dữ liệu không tiếp tục sửa rồi công bố tham chiếu mới. Nếu vẫn cho nhiều bên sửa cùng collection được tham chiếu, cần một cơ chế riêng cho những lần sửa đó.

### 10.4. Map an toàn không tự bảo vệ ArrayList được giữ làm giá trị

Ví dụ minh họa cần thận trọng:

~~~java
map.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
~~~

Nếu `map` là ConcurrentHashMap, việc thiết lập ánh xạ có hợp đồng nguyên tử của Map. Nhưng đối tượng ArrayList được trả về vẫn có thể bị nhiều luồng cùng gọi add. Map không khóa mọi hành động tương lai lên giá trị.

Có thể chọn cấu trúc giá trị phù hợp, một khóa riêng theo thiết kế, hoặc thay thế giá trị bằng các đối tượng dữ liệu mới trong phép cập nhật nguyên tử. Phải xác định rõ quyền sở hữu và trạng thái bên trong, không chỉ nhìn lớp của Map ngoài cùng.

## 11. Tái lập lỗi bằng lịch phối hợp, không dựa vào may rủi

Một chương trình đa luồng chạy sai không nhất thiết sai trong mọi lần chạy. Chạy một vòng lặp lớn rồi chờ “thỉnh thoảng ra số khác” có thể khó học và khó kiểm tra.

Phần minh chứng dùng hai cơ chế:

- `CyclicBarrier` buộc cả hai luồng đã đọc/kiểm tra xong rồi mới cho chúng ghi.
- `CountDownLatch` buộc thao tác xóa xảy ra sau khi đã đọc kích thước nhưng trước khi đọc phần tử.

Các công cụ này tạo ra một lịch xen kẽ hợp lệ làm lộ khe giữa những phương thức riêng. Chúng không biến phép get–put thành nguyên tử; chúng chỉ làm cho lịch gây lỗi được chủ động tái lập. [Java SE 17 — CyclicBarrier](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CyclicBarrier.html), [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html).

Mỗi ca kiểm tra đều đợi hai luồng kết thúc và đưa lỗi trong luồng phụ về luồng gọi. Nếu không làm vậy, luồng chính có thể in số liệu khi tác vụ chưa xong hoặc chỉ thấy kết quả có vẻ đúng trong khi một luồng đã dừng do ngoại lệ.

Các giới hạn chờ trong helper dùng để phát hiện một ví dụ không tiến triển, không để xác định thứ tự nghiệp vụ. Thứ tự được tạo bởi barrier và latch, không bởi tốc độ CPU hoặc một khoảng sleep ước chừng.

## 12. Chương trình minh chứng đầy đủ

Lưu mã sau thành **CollectionRaceDemo.java**. Chương trình dùng API Java 17 và thư viện chuẩn.

Ba ca lỗi logic được tạo trên collection có phương thức riêng an toàn: mất cập nhật get–put, thêm trùng contains–add và chỉ số hết hiệu lực size–get. Chương trình không cố tạo data race nội bộ trên ArrayList thường rồi khẳng định một stdout ngẫu nhiên.

Ca sửa đúng bảo vệ cả check–add bằng một khóa và dùng ConcurrentHashMap.merge cho việc tăng theo khóa. Ca addIfAbsent dùng đúng thao tác của CopyOnWriteArrayList. Những phần snapshot và đối tượng phần tử giúp kiểm tra phạm vi bảo đảm còn lại.

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CollectionRaceDemo {
    @FunctionalInterface
    interface CheckedTask {
        void run() throws Exception;
    }

    private static Thread taskThread(CheckedTask task, AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                task.run();
            } catch (Throwable problem) {
                failure.compareAndSet(null, problem);
            }
        });
    }

    private static void runPair(CheckedTask first, CheckedTask second) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread a = taskThread(first, failure);
        Thread b = taskThread(second, failure);
        a.start();
        b.start();
        a.join(8000);
        b.join(8000);
        if (a.isAlive() || b.isAlive()) {
            a.interrupt();
            b.interrupt();
            throw new AssertionError("Tasks did not finish");
        }
        if (failure.get() != null) {
            throw new AssertionError("Task failed", failure.get());
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out at latch");
        }
    }

    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
        counts.put("item", 0);
        CyclicBarrier bothRead = new CyclicBarrier(2);
        CheckedTask splitIncrement = () -> {
            int oldValue = counts.get("item");
            bothRead.await(5, TimeUnit.SECONDS);
            counts.put("item", oldValue + 1);
        };
        runPair(splitIncrement, splitIncrement);
        System.out.println("lost-update=" + counts.get("item"));

        List<String> separate = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        CheckedTask checkThenAdd = () -> {
            boolean absent = !separate.contains("X");
            bothChecked.await(5, TimeUnit.SECONDS);
            if (absent) {
                separate.add("X");
            }
        };
        runPair(checkThenAdd, checkThenAdd);
        System.out.println("separate-check-add=" + separate);

        List<String> locked = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier ready = new CyclicBarrier(2);
        CheckedTask lockedAdd = () -> {
            ready.await(5, TimeUnit.SECONDS);
            synchronized (locked) {
                if (!locked.contains("X")) {
                    locked.add("X");
                }
            }
        };
        runPair(lockedAdd, lockedAdd);
        System.out.println("locked-check-add=" + locked);

        ConcurrentHashMap<String, Integer> atomicCounts = new ConcurrentHashMap<>();
        CheckedTask atomicIncrement = () -> {
            for (int i = 0; i < 1000; i++) {
                atomicCounts.merge("item", 1, Integer::sum);
            }
        };
        runPair(atomicIncrement, atomicIncrement);
        System.out.println("atomic-merge=" + atomicCounts.get("item"));

        List<String> shrinking = Collections.synchronizedList(
                new ArrayList<>(List.of("A")));
        CountDownLatch sizeRead = new CountDownLatch(1);
        CountDownLatch cleared = new CountDownLatch(1);
        AtomicReference<String> readOutcome = new AtomicReference<>();
        runPair(() -> {
            int index = shrinking.size() - 1;
            sizeRead.countDown();
            await(cleared);
            try {
                readOutcome.set(shrinking.get(index));
            } catch (IndexOutOfBoundsException expected) {
                readOutcome.set("IndexOutOfBoundsException");
            }
        }, () -> {
            await(sizeRead);
            shrinking.clear();
            cleared.countDown();
        });
        System.out.println("size-then-get=" + readOutcome.get());

        List<String> source = Collections.synchronizedList(
                new ArrayList<>(List.of("A", "B")));
        List<String> snapshot;
        synchronized (source) {
            snapshot = List.copyOf(source);
        }
        source.add("C");
        System.out.println("snapshot=" + snapshot);
        System.out.println("source-after-snapshot=" + source);

        CopyOnWriteArrayList<String> copyOnWrite =
                new CopyOnWriteArrayList<>(List.of("A", "B"));
        Iterator<String> iterator = copyOnWrite.iterator();
        copyOnWrite.add("C");
        List<String> seen = new ArrayList<>();
        iterator.forEachRemaining(seen::add);
        System.out.println("copy-on-write-iterator=" + seen);
        System.out.println("copy-on-write-current=" + copyOnWrite);

        CopyOnWriteArrayList<String> unique = new CopyOnWriteArrayList<>();
        CheckedTask addIfAbsent = () -> unique.addIfAbsent("X");
        runPair(addIfAbsent, addIfAbsent);
        System.out.println("add-if-absent=" + unique);

        StringBuilder sharedItem = new StringBuilder("seed");
        List<StringBuilder> objectSnapshot = List.copyOf(List.of(sharedItem));
        sharedItem.append("!");
        System.out.println("shallow-snapshot-element=" + objectSnapshot.get(0));
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 CollectionRaceDemo.java
java CollectionRaceDemo
~~~

Stdout chính xác:

~~~text
lost-update=1
separate-check-add=[X, X]
locked-check-add=[X]
atomic-merge=2000
size-then-get=IndexOutOfBoundsException
snapshot=[A, B]
source-after-snapshot=[A, B, C]
copy-on-write-iterator=[A, B]
copy-on-write-current=[A, B, C]
add-if-absent=[X]
shallow-snapshot-element=seed!
~~~

Mã đã biên dịch bằng `--release 17` và chạy trên JDK 24.0.2. Cờ biên dịch kiểm tra mức ngôn ngữ/API Java 17; lần chạy được xác nhận trên JVM đã nêu.

Giải thích kết quả:

| Dòng kết quả | Điều đã được kiểm tra |
| --- | --- |
| `lost-update=1` | Hai luồng cùng lấy 0, cùng tính 1 rồi ghi; một lần tăng bị mất |
| `separate-check-add=[X, X]` | contains và add an toàn riêng nhưng khe giữa chúng cho phép hai lần thêm |
| `locked-check-add=[X]` | Cả chuỗi kiểm tra–thêm được bảo vệ bằng cùng một khóa |
| `atomic-merge=2000` | Hai luồng, mỗi luồng 1000 lần cập nhật nguyên tử theo khóa |
| `size-then-get=IndexOutOfBoundsException` | Chỉ số được tính trước khi danh sách bị xóa không còn hợp lệ lúc get |
| `snapshot=[A, B]` | Bản sao vị trí giữ dữ liệu của lượt sao chép |
| `source-after-snapshot=[A, B, C]` | Nguồn có thể tiếp tục thay đổi sau đó |
| Nhóm `copy-on-write` | Iterator giữ ảnh chụp, khác trạng thái collection hiện tại |
| `add-if-absent=[X]` | Hai luồng dùng đúng thao tác không thêm trùng của CopyOnWriteArrayList |
| `shallow-snapshot-element=seed!` | Sao chép cấu trúc không nhân bản StringBuilder bên trong |

Các kết quả là minh chứng cho những lịch đã được điều phối và các API được dùng. Chúng không chứng minh mọi thuật toán dùng các lớp này đều an toàn, cũng không phải một phép đo hiệu năng.

## 13. Chọn cách xử lý theo bất biến cần giữ

| Nhu cầu | Hướng thiết kế |
| --- | --- |
| Dữ liệu xây xong rồi chỉ đọc | Công bố an toàn và ngăn mọi cập nhật không hợp lệ |
| Nhiều thao tác phải thấy cùng trạng thái | Một giao thức khóa bao trùm toàn bộ bất biến hoặc một API nguyên tử phù hợp |
| Cập nhật độc lập theo khóa | ConcurrentHashMap với merge/compute/putIfAbsent theo đúng hợp đồng |
| Duyệt nhiều, cập nhật ít và chấp nhận snapshot | Cân nhắc CopyOnWriteArrayList hoặc phiên bản dữ liệu không sửa |
| Xử lý lâu nhưng không muốn giữ khóa lâu | Sao chép nguồn hợp lệ dưới khóa rồi xử lý ngoài khóa |
| Chuyển phần tử giữa bên sản xuất và bên tiêu thụ | Cấu trúc hàng đợi có hợp đồng concurrent/blocking phù hợp |

Việc chọn cấu trúc nên xuất phát từ điều dữ liệu phải luôn đúng, không chỉ từ số lượng luồng. Một collection concurrent có thể hỗ trợ hàng nghìn lời gọi riêng an toàn nhưng không giữ được một quy tắc liên quan tới nhiều collection nếu quy tắc đó chưa được biểu diễn.

Với khóa ngoài, cần ghi rõ khóa nào bảo vệ trạng thái nào. Với snapshot, cần ghi rõ thời điểm chụp và mức sao chép. Với cấu trúc concurrent, cần đọc phạm vi nguyên tử và chính sách duyệt. Những thông tin ấy giúp người sửa mã sau này không vô tình dùng một đường truy cập phá giao thức.

## 14. Thực hành có đáp án

### Bài 1 — ConcurrentHashMap nhưng vẫn mất lần tăng

Hai luồng đều chạy `map.put(k, map.get(k) + 1)`. Vì sao Map không hỏng mà bộ đếm vẫn có thể sai?

**Đáp án:** Get và put có hợp đồng riêng nhưng cả chuỗi không nguyên tử. Hai luồng có thể cùng đọc giá trị cũ rồi cùng ghi một kết quả. Dùng phép cập nhật nguyên tử theo khóa như merge nếu phù hợp yêu cầu.

### Bài 2 — synchronizedList đã đủ cho contains–add chưa?

Một danh sách được bọc bằng synchronizedList. Hai luồng chạy kiểm tra chưa có X rồi thêm X. Vì sao vẫn có thể chứa hai X?

**Đáp án:** Khóa của contains không bao trùm tới add. Hai luồng có thể cùng kiểm tra trước khi bên nào thêm. Bảo vệ cả đoạn bằng cùng khóa hoặc chọn cấu trúc/API cung cấp đúng thao tác duy nhất cần có.

### Bài 3 — Có từ synchronized ở cả hai phía

Luồng ghi khóa lockA, luồng đọc khóa lockB nhưng cùng truy cập một danh sách. Có đủ bảo vệ không?

**Đáp án:** Không. Hai khóa độc lập không loại trừ nhau và không thiết lập giao thức chung cho dữ liệu ấy. Mọi truy cập liên quan phải tuân thủ cùng cơ chế bảo vệ được thiết kế.

### Bài 4 — Không có ConcurrentModificationException

Một lần chạy đọc–ghi ArrayList không ném ngoại lệ. Có thể kết luận rằng vòng duyệt chỉ số hoặc lịch hiện tại làm nó an toàn không?

**Đáp án:** Không. Fail-fast chỉ phát hiện theo khả năng tốt nhất và không thay thế đồng bộ. Không gặp lỗi trong một lần chạy không chứng minh tuân thủ hợp đồng bộ nhớ hoặc bất biến nghiệp vụ.

### Bài 5 — Chép để an toàn

Có thể gọi List.copyOf trên một ArrayList đang bị sửa không đồng bộ rồi coi mọi rủi ro đã được giải quyết không?

**Đáp án:** Không. Quá trình sao chép vẫn phải đọc nguồn hợp lệ. Cần giữ nguồn ổn định bằng giao thức phù hợp trong lúc tạo snapshot, rồi công bố kết quả đúng cách. Bản sao cấu trúc vẫn có thể dùng chung đối tượng phần tử.

### Bài 6 — volatile List có khóa add không?

Một trường volatile trỏ tới ArrayList. Hai luồng cùng add vào ArrayList đó có tự được tuần tự hóa không?

**Đáp án:** Không. Volatile trên tham chiếu không biến nội dung collection thành vùng được khóa. Nó có thể phù hợp để công bố một phiên bản dữ liệu mới không tiếp tục sửa; các cập nhật trên cùng đối tượng vẫn cần cơ chế riêng.

### Bài 7 — Giá trị bên trong Map concurrent

Vì sao `concurrentMap.computeIfAbsent(k, x -> new ArrayList<>()).add(v)` chưa bảo đảm nhiều luồng cùng thêm vào danh sách giá trị an toàn?

**Đáp án:** Map bảo vệ việc tạo/đặt ánh xạ theo hợp đồng, nhưng ArrayList được giữ làm giá trị vẫn là đối tượng riêng không tự an toàn. Phải thiết kế cấu trúc giá trị hoặc giao thức cập nhật phù hợp.

### Bài 8 — Đọc snapshot hay dữ liệu mới nhất?

Iterator của CopyOnWriteArrayList được tạo trước khi thêm C. Vì sao nó vẫn trả A, B? Nếu báo cáo phải phản ánh một trạng thái nhất quán mới hơn, có thể chỉ khẳng định “collection an toàn đa luồng” là đủ không?

**Đáp án:** Iterator giữ ảnh chụp tại lúc tạo, nên không có C trong lượt ấy. An toàn đa luồng không tự xác định yêu cầu độ mới và nhất quán của báo cáo. Phải chọn thời điểm đọc, phạm vi khóa hoặc phiên bản snapshot phù hợp yêu cầu.

## 15. Cách nhận diện tranh chấp trong mã thực tế

Tìm nơi trạng thái được chia sẻ, rồi đánh dấu tất cả các đoạn đọc–kiểm tra–ghi hoặc các lượt duyệt phụ thuộc nhiều lần đọc. Với mỗi đoạn, hỏi điều gì có thể thay đổi giữa các bước và cơ chế nào giữ bất biến.

Tiếp theo kiểm tra quyền truy cập: có đường nào sửa trực tiếp collection nền, dùng khóa khác, giữ đối tượng giá trị để sửa bên ngoài, hoặc công bố một tham chiếu trước khi trạng thái sẵn sàng không. Đừng chỉ kiểm tra một phương thức đang được sửa rồi bỏ qua các bên dùng chung dữ liệu.

Mô hình quan trọng của bài là: **collection an toàn bảo vệ đúng phần hợp đồng của nó; thuật toán vẫn phải bảo vệ cả chuỗi thao tác mà nghiệp vụ cần xem là một hành động.** Kết hợp quyền sở hữu rõ ràng, công bố đúng, thao tác nguyên tử và chính sách duyệt phù hợp mới tạo thành một thiết kế đọc–ghi nhiều luồng đáng tin.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-002">Phân biệt an toàn từng thao tác với an toàn cả chuỗi thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt an toàn từng thao tác với an toàn cả chuỗi thao tác

## 1. Vấn đề cần hiểu trước khi chọn collection

Giả sử một bộ đếm lượt truy cập được lưu trong map. Mỗi yêu cầu đọc số hiện tại, cộng một, rồi ghi trở lại. Bạn thay `HashMap` bằng `ConcurrentHashMap`, nhưng tổng cuối cùng vẫn thấp hơn số yêu cầu đã xử lý. Điều này có thể xảy ra dù từng lần `get` và `put` đều được thực hiện đúng theo hợp đồng của map.

Điểm mấu chốt là **ranh giới thao tác mà collection bảo vệ có thể nhỏ hơn ranh giới công việc mà chương trình cần bảo vệ**. “Tăng bộ đếm một lần” là một công việc; “đọc một giá trị” và “ghi một giá trị” là hai lần gọi riêng. Luồng khác có thể chạy vào khoảng giữa chúng.

Bài học này tập trung vào việc xác định và thu hẹp khoảng trống đó trong Java Collections. Sau khi học, bạn cần làm được bốn việc:

- Chỉ ra điều kiện phải luôn đúng khi nhiều luồng cùng làm việc.
- Viết một lịch xen kẽ khiến chuỗi thao tác hiện tại sai.
- Chọn phương thức gộp sẵn hoặc khóa chung bao phủ đúng công việc.
- Kiểm tra cả đường đọc, đường ghi và đối tượng phần tử, thay vì chỉ nhìn tên lớp collection.

Kiến thức nền cần có là `Map`, `List`, `Queue`, tham chiếu đối tượng, lambda và cách đọc một khối `synchronized`. Các chương trình dùng cú pháp/API tương thích Java 17 trở lên. Không cần biết cách JVM triển khai bảng băm hay mã máy để theo dõi lập luận.

### 1.1. Các khái niệm liên quan

| Khái niệm | Câu hỏi nó trả lời | Ví dụ trong bài |
| --- | --- | --- |
| An toàn đa luồng (thread safety) | Đối tượng có giữ đúng hợp đồng khi nhiều luồng sử dụng theo cách được quy định không? | Hai luồng cùng gọi `ConcurrentHashMap.put` |
| Tính nguyên tử (atomicity) | Một công việc có được quan sát như một bước không bị chen ngang trong phạm vi đã cam kết không? | Gộp “đọc số cũ rồi cộng một” bằng `merge` |
| Tính nhìn thấy (visibility) | Luồng đọc có quan hệ đồng bộ cần thiết để thấy dữ liệu đã được công bố không? | Đọc kết quả sau khi tác vụ hoàn tất qua `Future.get` |
| Điều kiện bất biến (invariant) | Điều gì phải đúng ở mọi trạng thái mà bên sử dụng được phép quan sát? | Tổng số chỗ của hai ngăn luôn bằng 20 |
| Thao tác ghép (compound action) | Công việc nào gồm nhiều bước phụ thuộc lẫn nhau? | Kiểm tra chưa có rồi thêm, hoặc đọc hai khóa rồi tính tổng |
| Giao thức đồng bộ | Các bên phải tuân thủ quy tắc phối hợp nào? | Mọi lần di chuyển và đọc tổng đều giữ cùng một khóa |

Tính nguyên tử luôn cần một phạm vi cụ thể: một lần cập nhật, một khóa, một collection, hay cả nhiều đối tượng. Không nên dùng từ “atomic” mà bỏ qua phạm vi đó.

Một collection an toàn đa luồng không tự nhận biết quy tắc nghiệp vụ của chương trình. Nó không biết hai khóa `left` và `right` phải giữ tổng bằng 20, cũng không biết một tên chỉ được đăng ký một lần nếu bạn tự ghép `contains` với `add`.

## 2. Vì sao nhiều thao tác an toàn vẫn tạo thành chuỗi sai?

### 2.1. Đọc–sửa–ghi có thể làm mất cập nhật

Đoạn sau là **mã minh họa cố ý sai khi nhiều luồng cùng tăng**, không phải một chương trình độc lập:

```java
int before = counts.get("hits");
counts.put("hits", before + 1);
```

Giả sử `counts` là `ConcurrentHashMap<String, Integer>` và đã chứa `"hits" -> 0`. Hai luồng có thể thực hiện:

| Bước | Luồng A | Luồng B | Giá trị trong map |
| --- | --- | --- | --- |
| 1 | Đọc được 0 | Chưa đọc | 0 |
| 2 | Giữ số 0 trong biến cục bộ | Đọc được 0 | 0 |
| 3 | Tính 0 + 1, ghi 1 | Giữ số 0 trong biến cục bộ | 1 |
| 4 | Đã xong | Tính 0 + 1, ghi 1 | 1 |

Hai lần tăng đã hoàn thành nhưng chỉ còn kết quả 1. Map không làm sai một lần `put` nào: cả hai luồng đều yêu cầu ghi 1. Sai sót nằm trong cách chương trình tạo ra hai giá trị mới từ cùng một giá trị cũ.

Đây là **mất cập nhật (lost update)**. Việc viết gọn thành `counts.put("hits", counts.get("hits") + 1)` chỉ giảm số dòng, không gộp hai lần gọi thành một thao tác nguyên tử. Ý tưởng đọc–tính–ghi cũng giải thích vì sao `x++` không tự trở thành nguyên tử. [Oracle: Thread Interference](https://docs.oracle.com/javase/tutorial/essential/concurrency/interfere.html)

### 2.2. Kiểm tra–rồi–làm phụ thuộc vào một quan sát đã cũ

Một ví dụ khác là **kiểm tra rồi hành động (check-then-act)**. Đoạn minh họa dưới đây cũng cố ý để lộ khoảng tranh chấp:

```java
if (!registrations.containsKey("seat-7")) {
    registrations.put("seat-7", "Lan");
}
```

Luồng A thấy chỗ trống. Trước khi A ghi, luồng B đăng ký chỗ đó cho `"Minh"`. Sau đó A ghi `"Lan"` và thay thế đăng ký của B. Nếu quy tắc là “chỉ người đầu tiên được đăng ký”, cách cài đặt này sai.

Không có hai khóa `"seat-7"` trong map; map vẫn giữ quy tắc mỗi khóa có tối đa một ánh xạ. **Không trùng khóa của map** và **không ghi đè người đã đăng ký** là hai điều kiện khác nhau. Một phép `put` luôn có thể thay giá trị tại khóa đã tồn tại.

Một giá trị trả về từ `isEmpty`, `containsKey`, `size` hay `get` là một quan sát. Muốn dùng nó để quyết định một thay đổi tiếp theo, cần chứng minh điều kiện đó vẫn được bảo vệ cho tới lúc thay đổi xảy ra.

### 2.3. Race condition không đồng nghĩa với data race

Trong bài, **lỗi phụ thuộc lịch chạy (race condition)** nghĩa là tính đúng đắn phụ thuộc vào cách các bước của nhiều luồng xen kẽ. Hai luồng dùng hoàn toàn những API an toàn của `ConcurrentHashMap` vẫn có thể tạo ra loại lỗi này.

**Tranh chấp dữ liệu theo mô hình bộ nhớ Java (data race)** có nghĩa hẹp hơn: hai truy cập xung đột không được sắp thứ tự bởi quan hệ happens-before. Không nên suy luận “không có data race trên cấu trúc map” thành “quy trình đăng ký chắc chắn đúng”.

Theo JLS, mở khóa một monitor tạo quan hệ happens-before với lần khóa tiếp theo trên chính monitor đó. Quy tắc này phục vụ việc phối hợp thứ tự và nhìn thấy dữ liệu; chương trình vẫn phải đặt đủ các bước cần bảo vệ trong phạm vi khóa. [JLS 17.4.5 — Happens-before Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5)

### 2.4. `final` và `volatile` không tự bảo vệ chuỗi thao tác

`final Map<...>` ngăn gán lại biến tham chiếu sau khi khởi tạo theo quy tắc của `final`; nó không biến map thành bất biến. Một biến tham chiếu `volatile` giúp phối hợp việc công bố/thay thế tham chiếu, nhưng không khiến `get` rồi `put`, hoặc `contains` rồi `add`, thành một thao tác duy nhất.

Tương tự, một bộ đếm khai báo `volatile int` vẫn cần cơ chế phù hợp nếu nhiều luồng cùng tăng bằng `++`. Khả năng nhìn thấy của từng truy cập không thay thế việc gộp toàn bộ bước đọc–sửa–ghi. [Oracle: Atomic Access](https://docs.oracle.com/javase/tutorial/essential/concurrency/atomic.html)

Vì vậy, khi xem xét một lỗi, hãy hỏi riêng hai câu: “dữ liệu được công bố/đọc bằng cơ chế nào?” và “những bước phụ thuộc nhau được gộp bằng cơ chế nào?”. Trả lời câu thứ nhất chưa đủ để trả lời câu thứ hai.

## 3. Thí nghiệm: cùng một lỗi với concurrent map và synchronized map

Chương trình đầy đủ sau ép hai luồng **đều đọc số 0 trước khi bất kỳ luồng nào ghi**. `CyclicBarrier` là điểm hẹn của hai tác vụ. Không dùng `Thread.sleep` và không trông chờ chạy nhiều lần để tình cờ thấy lỗi.

Chương trình thử bốn trường hợp:

1. `ConcurrentHashMap` với `get`/`put` tách rời.
2. `Collections.synchronizedMap` với `get`/`put` tách rời.
3. `ConcurrentHashMap.merge` để gộp tăng theo khóa.
4. Khối `synchronized` bao trọn đọc–sửa–ghi trên synchronized map.

Lưu thành **`AtomicStepDemo.java`**:

```java
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AtomicStepDemo {
    private static final int REPETITIONS = 2_000;

    public static void main(String[] args) throws Exception {
        Map<String, Integer> concurrent = new ConcurrentHashMap<>();
        Map<String, Integer> wrapped =
                Collections.synchronizedMap(new HashMap<>());

        System.out.println("concurrent split = " + forceLostUpdate(concurrent));
        System.out.println("wrapped split = " + forceLostUpdate(wrapped));

        ConcurrentHashMap<String, Integer> merged = new ConcurrentHashMap<>();
        runTwice(() -> {
            for (int i = 0; i < REPETITIONS; i++) {
                merged.merge("hits", 1, Integer::sum);
            }
        });
        require(merged.get("hits") == 2 * REPETITIONS, "merge lost an update");
        System.out.println("concurrent merge = " + merged.get("hits"));

        Map<String, Integer> guarded =
                Collections.synchronizedMap(new HashMap<>());
        guarded.put("hits", 0);
        runTwice(() -> {
            for (int i = 0; i < REPETITIONS; i++) {
                synchronized (guarded) {
                    int before = guarded.get("hits");
                    guarded.put("hits", before + 1);
                }
            }
        });
        require(guarded.get("hits") == 2 * REPETITIONS, "guard lost an update");
        System.out.println("wrapped whole block = " + guarded.get("hits"));
    }

    private static int forceLostUpdate(Map<String, Integer> map)
            throws Exception {
        map.put("hits", 0);
        CyclicBarrier bothRead = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                int before = map.get("hits");
                bothRead.await(5, TimeUnit.SECONDS);
                map.put("hits", before + 1);
                return null;
            });
            Future<?> second = pool.submit(() -> {
                int before = map.get("hits");
                bothRead.await(5, TimeUnit.SECONDS);
                map.put("hits", before + 1);
                return null;
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            int result = map.get("hits");
            require(result == 1, "the forced schedule did not occur");
            return result;
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }
        }
    }

    private static void runTwice(Runnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(action);
            Future<?> second = pool.submit(action);
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all AtomicStepDemo.java
java AtomicStepDemo
```

Stdout khi chương trình hoàn thành bình thường:

```text
concurrent split = 1
wrapped split = 1
concurrent merge = 4000
wrapped whole block = 4000
```

Hai dòng đầu luôn là 1 trong lịch mà chương trình ép được: mỗi luồng giữ một biến cục bộ `before = 0`, rồi cả hai ghi `1`. Thứ tự hai lần ghi không làm thay đổi kết quả này.

Hai dòng cuối là 4.000 vì có hai tác vụ, mỗi tác vụ tăng 2.000 lần. Ở trường hợp `merge`, thay đổi được gửi cho map dưới dạng một phép gộp. Ở trường hợp khóa ngoài, luồng thứ hai không thể chen vào giữa lần đọc và lần ghi của luồng đang giữ monitor.

`Collections.synchronizedMap` không tự giữ monitor từ lúc trả về `get` cho đến một lần `put` sau đó. Hai lần gọi của người dùng tạo hai phạm vi bảo vệ riêng. Chỉ khi khối bên ngoài bao cả chuỗi, khoảng trống giữa chúng mới được che phủ.

Điểm hẹn chỉ đặt trong trường hợp cố ý sai. **Không chuyển barrier vào bên trong khối giữ cùng một khóa**: tác vụ đầu có thể giữ khóa rồi chờ tác vụ thứ hai, trong khi tác vụ thứ hai đang chờ chính khóa đó. Khi đó thiết kế thử nghiệm tạo bế tắc thay vì chứng minh tính đúng đắn.

Theo hợp đồng, `CyclicBarrier` cho phép một nhóm luồng chờ nhau tại một điểm. `Future.get` nhận kết quả hoặc lỗi của tác vụ; các hành động của tác vụ xảy ra-trước các hành động sau lần `get` tương ứng. [CyclicBarrier Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CyclicBarrier.html), [Future Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)

Các thời hạn trong mã là để bài thử báo lỗi thay vì chờ vô hạn nếu môi trường không chạy được tác vụ. Chúng không phải điều kiện tạo ra lịch xen kẽ. Phần `finally` dừng nhóm luồng; ngoại lệ chờ/biên dịch/chạy không bị bỏ qua để in một kết quả có vẻ thành công.

## 4. Cách sửa thứ nhất: dùng thao tác gộp phù hợp

### 4.1. Chọn API diễn đạt đúng điều kiện cần bảo vệ

Với `ConcurrentMap`, một số thao tác có điều kiện được thiết kế để thực hiện nguyên tử. Đừng dựng lại chúng bằng nhiều lần `get`, `containsKey`, `put` hoặc `remove`:

| Ý định | Chuỗi tự ghép có khoảng trống | API cần xem xét |
| --- | --- | --- |
| Chỉ đăng ký khi chưa có khóa | `containsKey` rồi `put` | `putIfAbsent(key, value)` |
| Chỉ xóa nếu giá trị vẫn là giá trị đã biết | `get` rồi so sánh rồi `remove(key)` | `remove(key, expectedValue)` |
| Chỉ thay nếu giá trị chưa đổi | `get` rồi so sánh rồi `put` | `replace(key, expectedValue, newValue)` |

Giá trị trả về cho biết kết quả của thao tác có điều kiện. Đoạn sau là **mã minh họa**, giả sử `registrations` là `ConcurrentHashMap<String, String>` dùng chung:

```java
String previous = registrations.putIfAbsent("seat-7", "Lan");
if (previous == null) {
    System.out.println("Dang ky thanh cong");
} else {
    System.out.println("Cho da duoc dang ky");
}
```

Trong ví dụ này, `null` biểu thị không có giá trị cũ, vì loại map đang dùng không chứa giá trị null. Một khóa có thể được xóa rồi đăng ký lại trong tương lai nếu ứng dụng cho phép; thành công của lần gọi này không phải lời bảo đảm “chỗ đó thuộc về Lan mãi mãi”.

Các biến thể có điều kiện trên được quy định trong hợp đồng `ConcurrentMap`. Dùng kết quả của chính thao tác đó để quyết định nhánh tiếp theo. [ConcurrentMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html)

### 4.2. Cập nhật theo giá trị hiện tại bằng `compute` hoặc `merge`

Với `ConcurrentHashMap`, có thể diễn đạt tăng bộ đếm theo khóa bằng một trong hai đoạn minh họa sau:

```java
counts.merge("hits", 1, Integer::sum);

counts.compute("hits", (key, oldValue) ->
        oldValue == null ? 1 : oldValue + 1);
```

Đây là **hai cách thay thế**, không phải hai dòng cần chạy cho một lượt truy cập; chạy cả hai sẽ tăng hai lần. Trong đoạn này, `counts` là `ConcurrentHashMap<String, Integer>`.

`merge` dùng giá trị 1 khi chưa có ánh xạ; khi đã có, nó kết hợp số cũ với 1. `compute` nhận cả trường hợp chưa có và đã có. `computeIfAbsent` thích hợp để tạo ánh xạ còn thiếu, không phải để tăng mọi lượt truy cập.

Trên `ConcurrentHashMap`, các lần gọi `compute`/`computeIfAbsent`/`merge` có cam kết nguyên tử cho phép cập nhật ánh xạ đang xét. Hàm trả về null có thể làm không tạo hoặc xóa ánh xạ tùy phương thức. Hàm tính cần ngắn, không sửa map đang tính; một số cập nhật khác có thể phải chờ. [ConcurrentHashMap — compute và merge](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#compute(K,java.util.function.BiFunction))

Cách sửa này vẫn đòi hỏi mọi đường tăng bộ đếm dùng quy tắc tương thích. Nếu một đường khác còn chạy `get` rồi `put` với số cũ, nó vẫn có thể ghi đè kết quả của một lần `merge`. Đổi một chỗ gọi không tự sửa những chỗ gọi khác.

### 4.3. Kiểu khai báo `Map` không tự mang bảo đảm của mọi lớp triển khai

Cần đọc hợp đồng lớp thực tế:

```java
Map<String, Integer> a = new HashMap<>();
Map<String, Integer> b = new ConcurrentHashMap<>();
```

Đây là **hai khai báo minh họa**, cần import tương ứng. Cả hai đều gọi được `merge`, nhưng tên phương thức giống nhau không chứng minh cùng mức bảo đảm đồng bộ. Nhiều phương thức mặc định của `Map`, bao gồm các phương thức tính/cập nhật, không cam kết đồng bộ hay nguyên tử; lớp triển khai phải quy định hành vi của nó. [Map Java 17 — các phương thức mặc định](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#merge(K,V,java.util.function.BiFunction))

Việc khai báo `b` bằng `Map` không làm mất cách cài đặt của `ConcurrentHashMap`: Java vẫn chọn phương thức ghi đè của đối tượng thực tế. Tuy nhiên, nếu hợp đồng của một thành phần chỉ nói “nhận một Map bất kỳ”, bên sử dụng không có cơ sở mặc định rằng tất cả đối tượng được truyền vào đều an toàn khi chia sẻ giữa các luồng.

Khi thiết kế API, hãy nêu rõ cần `ConcurrentMap`, một đối tượng cụ thể với cam kết đã biết, hay một map chỉ được truy cập dưới khóa riêng. Kiểu và tài liệu hợp đồng nên làm rõ yêu cầu này cho người gọi.

### 4.4. Tính nguyên tử của cập nhật không đồng nghĩa tác dụng phụ xảy ra đúng một lần

Một hàm tính nên chỉ xây dựng/biến đổi kết quả cục bộ rồi trả về. Không nên suy từ “ánh xạ được cập nhật nguyên tử” sang “gửi thông báo, trừ tiền bên ngoài, hoặc gọi dịch vụ từ xa đúng một lần”.

Các phương thức mặc định `compute`/`merge` của `ConcurrentMap` có thể thử lại và gọi hàm tính nhiều lần khi cạnh tranh. Riêng `ConcurrentHashMap.computeIfAbsent` gọi hàm một lần trong một lần gọi phương thức nếu khóa vắng, và không gọi hàm nếu khóa đã có giá trị; đây không phải cam kết chỉ chạy hàm một lần trong suốt đời khóa. [ConcurrentMap — merge](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html#merge(K,V,java.util.function.BiFunction)), [ConcurrentHashMap — computeIfAbsent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#computeIfAbsent(K,java.util.function.Function))

Ví dụ, hàm tạo có thể trả về null, ném ngoại lệ, hoặc ánh xạ được xóa để lần gọi sau tạo lại. Nếu bạn làm một tác dụng phụ trước khi hàm thất bại, một thay đổi đối với map không tự hoàn tác tác dụng phụ đó.

Cũng không gọi `compute` trên map khác trong khi tùy tiện giữ khóa hay chờ tác vụ khác. Một tác vụ A giữ tài nguyên X và chờ Y, trong khi tác vụ B giữ Y và chờ X, có thể tạo vòng chờ. Đưa một công việc dài vào hàm tính còn khiến phần cần phối hợp bị giữ lâu hơn. Việc tải dữ liệu chậm cần một thiết kế quản lý vòng đời tác vụ riêng nếu yêu cầu ứng dụng đòi hỏi, không thể giải quyết chỉ bằng đổi tên phương thức map.

## 5. Cách sửa thứ hai: giữ một khóa cho toàn bộ chuỗi

### 5.1. Với synchronized wrapper, khóa đúng đối tượng trả về

Giả sử danh sách được tạo và công bố an toàn bằng:

```java
List<String> names =
        Collections.synchronizedList(new ArrayList<>());
```

Muốn thực hiện quy tắc “chưa có thì thêm”, đoạn minh họa dưới đây giữ monitor của chính `names` cho cả hai bước:

```java
synchronized (names) {
    if (!names.contains("Lan")) {
        names.add("Lan");
    }
}
```

Điều kiện là tất cả bên sử dụng phải truy cập danh sách qua wrapper đó và tuân thủ cùng giao thức cho các công việc ghép. Một luồng gọi `add` trực tiếp vẫn có thể thêm trùng sau khi khối này kết thúc; khóa không biến `List` thành `Set` và không cấm một thao tác mà chương trình vẫn cho phép.

Tương tự, nếu dữ liệu gốc bị giữ ở nơi khác rồi sửa trực tiếp, wrapper không thể bắt đường truy cập đó phải chờ monitor của nó.

Đối với các synchronized wrapper, tài liệu yêu cầu mọi truy cập cấu trúc nền đi qua đối tượng trả về. Khi duyệt bằng iterator/spliterator/stream, người dùng phải tự giữ khóa phù hợp. Với view của synchronized map, khóa map trả về, không khóa riêng `keySet`/`entrySet`. [Collections — synchronized wrappers](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map))

### 5.2. Khóa từng đoạn nhỏ vẫn để hở phần quyết định

Đoạn **minh họa cố ý sai về phạm vi khóa** sau vẫn có khoảng trống:

```java
int before;
synchronized (counts) {
    before = counts.get("hits");
}

synchronized (counts) {
    counts.put("hits", before + 1);
}
```

Giữa hai khối, một luồng khác có thể cập nhật giá trị. Biến cục bộ `before` không tự đổi theo map. Cần bao cả quyết định và cập nhật trong cùng lần giữ khóa.

Khóa cũng cần đúng **danh tính đối tượng**, không chỉ có cùng tên hay cùng nội dung:

- Hai đối tượng `new Object()` khác nhau là hai khóa khác nhau.
- Khóa map nền không đồng nghĩa khóa synchronized wrapper.
- Tạo một wrapper mới quanh cùng map ở mỗi lần gọi không tạo ra một khóa chung.
- Khóa `this` của hai đối tượng dịch vụ khác nhau không tạo loại trừ lẫn nhau giữa chúng.

Khả năng tái nhập của monitor cho phép cùng luồng đã giữ monitor gọi tiếp phương thức cũng dùng monitor đó. Nó không khiến hai monitor khác nhau trở thành một khóa chung. [JLS 17.1 — Synchronization](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.1)

### 5.3. Khóa ngoài quanh concurrent collection chỉ có tác dụng khi các bên hợp tác

Bạn có thể xây dựng một quy tắc dùng `synchronized (guard)` quanh một `ConcurrentHashMap`. Nhưng các luồng khác chỉ gọi `map.put` mà không lấy `guard` sẽ không tự chờ khóa ngoài đó.

Đặc biệt, `synchronized (concurrentMap)` không phải lệnh yêu cầu lớp đó dừng mọi cập nhật nội bộ. `ConcurrentHashMap` không cung cấp khóa toàn bảng chặn mọi truy cập theo cách này. [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

Nếu đã có một khóa chung bao toàn bộ thao tác và toàn bộ đường truy cập, một `HashMap` riêng tư có thể đáp ứng thiết kế đó. Khi ấy tính an toàn nằm ở lớp bao và giao thức khóa. Cần ngăn map thoát ra ngoài để người gọi không vô tình tạo một đường đi vòng.

Khi nhiều đối tượng cần khóa riêng, thứ tự lấy khóa phải nhất quán để tránh vòng chờ. Với một bài toán nhỏ, khóa chung thường dễ lập luận hơn; chỉ tách khóa khi bạn đã xác định rõ điều kiện bất biến và cách mọi đường truy cập phối hợp.

## 6. Một khóa nguyên tử không bảo vệ được điều kiện liên quan nhiều khóa

### 6.1. Hai lần `compute` vẫn là hai lần cập nhật

Giả sử có hai ngăn lưu số chỗ `left` và `right`. Ban đầu mỗi ngăn có 10 chỗ; công việc “di chuyển một chỗ từ trái sang phải” cần giữ điều kiện:

```text
left + right = 20
```

Bạn có thể viết hai phép `compute` an toàn theo từng khóa, nhưng vẫn tạo ra một trạng thái trung gian:

```java
slots.compute("left", (key, value) -> value - 1);
slots.compute("right", (key, value) -> value + 1);
```

Đoạn này là **mã minh họa cố ý chưa bảo vệ bất biến nhiều khóa**, với `slots` là `ConcurrentHashMap<String, Integer>` đã có hai khóa.

Sau dòng thứ nhất, trạng thái là `left = 9, right = 10`. Sau dòng thứ hai, trạng thái là `left = 9, right = 11`. Cả hai phép tính từng khóa đều đúng, nhưng một người đọc giữa hai dòng có thể thấy tổng 19.

Đọc tổng cũng là một chuỗi: `get("left")` rồi `get("right")`. Ngay cả khi hai lần cập nhật được đặt gần nhau và không cố ý dừng giữa chừng, một phép đọc ghép không được phối hợp vẫn có thể trộn quan sát từ hai giai đoạn.

Điều ứng dụng cần bảo vệ là **phép di chuyển và phép chụp hai số liên quan**, không chỉ các lần ghi từng số. Nguyên tử trên một khóa không tự mở rộng sang khóa thứ hai.

### 6.2. Thí nghiệm quan sát trạng thái trung gian và lớp bao có khóa chung

Chương trình sau có hai nửa:

- Nửa đầu dùng concurrent map và hai latch để người đọc chắc chắn chạy giữa hai cập nhật.
- Nửa sau đặt map riêng tư trong `SlotStore`. Cả `move` lẫn `snapshot` giữ cùng `guard`, và chỉ trả ra một record gồm hai số nguyên.

Lưu thành **`CollectionInvariantDemo.java`**:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CollectionInvariantDemo {
    private static final int CHECKS = 1_000;

    public static void main(String[] args) throws Exception {
        showIntermediateState();
        showGuardedState();
    }

    private static void showIntermediateState() throws Exception {
        ConcurrentHashMap<String, Integer> slots = new ConcurrentHashMap<>();
        slots.put("left", 10);
        slots.put("right", 10);
        CountDownLatch firstWrite = new CountDownLatch(1);
        CountDownLatch readFinished = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> writer = pool.submit(() -> {
                slots.compute("left", (key, value) -> value - 1);
                firstWrite.countDown();
                await(readFinished);
                slots.compute("right", (key, value) -> value + 1);
                return null;
            });
            Future<Integer> reader = pool.submit(() -> {
                await(firstWrite);
                try {
                    return slots.get("left") + slots.get("right");
                } finally {
                    readFinished.countDown();
                }
            });
            int observed = reader.get(10, TimeUnit.SECONDS);
            writer.get(10, TimeUnit.SECONDS);
            require(observed == 19, "intermediate state was not observed");
            System.out.println("separate compute, observed total = " + observed);
            System.out.println("separate compute, final total = "
                    + (slots.get("left") + slots.get("right")));
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }
        }
    }

    private static void showGuardedState() throws Exception {
        SlotStore slots = new SlotStore();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> writer = pool.submit(() -> {
                await(start);
                for (int i = 0; i < CHECKS; i++) {
                    slots.move("left", "right");
                    slots.move("right", "left");
                }
                return null;
            });
            Future<Integer> reader = pool.submit(() -> {
                await(start);
                for (int i = 0; i < CHECKS; i++) {
                    Snapshot snapshot = slots.snapshot();
                    require(snapshot.total() == 20, "broken invariant");
                }
                return CHECKS;
            });
            start.countDown();
            int checks = reader.get(10, TimeUnit.SECONDS);
            writer.get(10, TimeUnit.SECONDS);
            Snapshot last = slots.snapshot();
            require(last.left() == 10 && last.right() == 10, "wrong final state");
            System.out.println("guarded snapshots checked = " + checks);
            System.out.println("guarded final = " + last);
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }
        }
    }

    private static final class SlotStore {
        private final Object guard = new Object();
        private final Map<String, Integer> values = new HashMap<>();

        private SlotStore() {
            values.put("left", 10);
            values.put("right", 10);
        }

        private void move(String from, String to) {
            if (from.equals(to)) {
                throw new IllegalArgumentException("distinct slots required");
            }
            synchronized (guard) {
                Integer source = values.get(from);
                Integer target = values.get(to);
                if (source == null || target == null || source <= 0) {
                    throw new IllegalStateException("invalid move");
                }
                values.put(from, source - 1);
                values.put(to, target + 1);
            }
        }

        private Snapshot snapshot() {
            synchronized (guard) {
                return new Snapshot(values.get("left"), values.get("right"));
            }
        }
    }

    private record Snapshot(int left, int right) {
        private int total() {
            return left + right;
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("latch timed out");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all CollectionInvariantDemo.java
java CollectionInvariantDemo
```

Stdout khi chạy thành công:

```text
separate compute, observed total = 19
separate compute, final total = 20
guarded snapshots checked = 1000
guarded final = Snapshot[left=10, right=10]
```

Ở nửa đầu, `firstWrite` mở sau khi giảm ngăn trái. Luồng ghi chờ `readFinished`, nên không tăng ngăn phải trước khi luồng đọc lấy tổng. Kết quả 19 được tạo bởi quan hệ phối hợp rõ ràng, không dựa vào tốc độ máy.

Ở nửa sau, tác vụ ghi di chuyển sang phải rồi chuyển lại, lặp 1.000 lần. Tác vụ đọc kiểm tra 1.000 bản chụp. Mỗi bản chụp có thể thấy `10/10` hoặc `9/11`, nhưng tổng luôn là 20. Kết quả cuối `10/10` được đọc sau khi tác vụ ghi hoàn thành.

Đường đọc cũng lấy `guard` nên không được vào giữa hai lần `put` của một phép di chuyển. Nếu `snapshot` bỏ khóa, lập luận này mất cơ sở dù `move` vẫn còn `synchronized`.

Record `Snapshot` giữ hai giá trị `int`, không trả map nội bộ hay một `Map.Entry` sống. Sau khi thoát khóa, người đọc chỉ tính toán trên bản chụp của chính mình; tác vụ ghi sau đó không sửa được hai thành phần của record này.

`CountDownLatch` cung cấp cổng chờ một lần và quan hệ công bố dữ liệu qua `countDown`/`await` theo hợp đồng của nó. Trong thí nghiệm, latch dùng để điều khiển thứ tự, còn `guard` là cơ chế bảo vệ bất biến của lớp bao. [CountDownLatch Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)

### 6.3. Điều gì thực sự chứng minh bản sửa?

Thí nghiệm là minh chứng chạy được, còn lý do đúng nằm trong cấu trúc lớp:

1. `values` là riêng tư và không được trả ra.
2. Mọi phương thức sau khởi tạo có đọc/sửa trạng thái đều giữ cùng `guard`.
3. Kiểm tra nguồn còn chỗ và cả hai lần ghi đều nằm trong một khối.
4. Người đọc lấy hai số dưới cùng khóa rồi trả ra dữ liệu độc lập.
5. Hai tác vụ chỉ nhận đối tượng sau khi nó đã được khởi tạo, qua cơ chế gửi tác vụ của executor.

Từ đó có thể xét từng lần di chuyển như một chuyển đổi giữa hai trạng thái hợp lệ đối với các bên tuân thủ giao thức. Một người đọc chỉ thấy trước hoặc sau lần di chuyển.

Trong ví dụ, các tên ngăn được xác định trước, số lượng nhỏ và phép tính không thể tràn `int`. Nếu mở rộng thành API tổng quát, phải xác định thêm miền giá trị, kiểm tra tràn số, quy tắc thêm/xóa ngăn và cách xử lý lỗi trước khi thay đổi trạng thái. Giữ khóa không tự cung cấp hoàn tác nếu bạn sửa được một nửa rồi ném ngoại lệ.

### 6.4. Đôi khi nên đổi cách biểu diễn trạng thái

Nếu nhiều dữ liệu luôn phải thay đổi cùng nhau, có thể gom chúng thành **một giá trị bất biến** rồi thay cả giá trị đó theo một thao tác có bảo đảm phù hợp. Ví dụ, lưu một record chứa cả hai số dưới một khóa, hoặc dùng `AtomicReference` tới trạng thái bất biến.

`AtomicReference` hỗ trợ cập nhật nguyên tử một tham chiếu. Điều đó có thể phù hợp khi ranh giới nghiệp vụ trùng với toàn bộ giá trị mà tham chiếu trỏ tới. [AtomicReference Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html)

Đây là một lựa chọn thiết kế, không phải lời khuyên đưa mọi dữ liệu của chương trình vào một đối tượng khổng lồ. Cần cân nhắc kích thước bản sao, tần suất cập nhật và mức cạnh tranh. Nếu record vẫn chứa một danh sách có thể sửa được từ bên ngoài, riêng việc record không có setter chưa làm toàn bộ đồ thị đối tượng bất biến.

Với bài toán trong chương trình, một khóa chung và dữ liệu riêng tư đã đủ đơn giản để chứng minh. Chưa có lý do phải triển khai thêm giao thức nhiều khóa hoặc thuật toán thử lại phức tạp.

## 7. Map an toàn không tự làm đối tượng giá trị an toàn

### 7.1. `computeIfAbsent` chỉ gộp phần tạo/lấy ánh xạ

Đoạn minh họa sau rất dễ bị hiểu quá mức:

```java
ConcurrentHashMap<String, List<String>> groups = new ConcurrentHashMap<>();
groups.computeIfAbsent("team-a", key -> new ArrayList<>()).add("Lan");
```

Đây là **đoạn minh họa có vấn đề khi nhiều luồng cùng sửa danh sách**, cần import tương ứng. Có hai công việc nối nhau:

1. Tạo hoặc lấy tham chiếu danh sách trong map.
2. Gọi `add` lên danh sách được trả về.

Phần `add` xảy ra trên `ArrayList`, sau khi phương thức của map đã trả về. Những luồng cùng nhận được danh sách đó không tự có một giao thức bảo vệ cấu trúc danh sách. `ArrayList` yêu cầu đồng bộ bên ngoài khi được nhiều luồng truy cập và có ít nhất một luồng sửa cấu trúc. [ArrayList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

Hãy vẽ ranh giới đối tượng để nhận ra điều này:

```text
ConcurrentHashMap
    "team-a" -> tham chieu -> ArrayList dung chung

Bao ve anh xa khong tu bao ve moi thay doi trong ArrayList.
```

Đổi map bên ngoài không sửa hợp đồng của đối tượng bên trong. Ngược lại, bọc danh sách bên trong cũng chỉ giải quyết những thao tác mà wrapper của danh sách bảo vệ; một chuỗi `size`/`get`/`remove` khác vẫn cần xem lại.

### 7.2. Một cách giải quyết: thay giá trị bất biến theo khóa

Nếu yêu cầu là “thêm một thành viên và mọi người đọc nhận được danh sách không sửa được của một phiên bản”, đoạn minh họa dưới đây gom việc xây dựng phiên bản mới vào `compute`:

```java
ConcurrentHashMap<String, List<String>> groups = new ConcurrentHashMap<>();

groups.compute("team-a", (key, oldMembers) -> {
    List<String> next = oldMembers == null
            ? new ArrayList<>()
            : new ArrayList<>(oldMembers);
    next.add("Lan");
    return List.copyOf(next);
});
```

Đây là **đoạn mã minh họa không độc lập**. Giả định quan trọng là mọi giá trị đã có trong map cũng tuân thủ chính sách không cho sửa, thành viên là `String` không null, và mọi đường thêm đều dùng giao thức tương thích.

`next` là danh sách mới chỉ dùng cục bộ trong lần tính. Không sửa `oldMembers`, nên một luồng đã giữ phiên bản cũ vẫn có thể đọc phiên bản đó theo hợp đồng đã định. Người đọc lấy được một tham chiếu danh sách là lấy một phiên bản, không phải giữ quyền ngăn phiên bản mới thay nó trong map. `List.copyOf` tạo danh sách không cho sửa và không nhận phần tử null. [List.copyOf Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection))

Chi phí là phải sao chép danh sách cho mỗi lần thay đổi. Nếu phần tử là đối tượng có trạng thái thay đổi, sao chép danh sách chỉ sao chép tham chiếu; cần quy tắc riêng cho trạng thái phần tử.

Một lựa chọn khác là giá trị cũng có cơ chế đồng bộ riêng, chẳng hạn collection concurrent phù hợp. Tuy vậy, khi yêu cầu liên quan cả ánh xạ và vòng đời giá trị, vẫn phải xem xét phép xóa/thay giá trị trong map.

### 7.3. Giá trị `AtomicInteger` cũng có ranh giới vòng đời

Với bộ đếm, có thể dùng đoạn minh họa:

```java
ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
AtomicInteger counter =
        counters.computeIfAbsent("hits", key -> new AtomicInteger());
counter.incrementAndGet();
```

`AtomicInteger.incrementAndGet` gộp việc tăng trên chính đối tượng bộ đếm. [AtomicInteger Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html#incrementAndGet())

Nhưng một luồng khác có thể xóa ánh xạ `"hits"` sau khi luồng đầu lấy `counter` và trước khi nó tăng. Khi đó số vẫn tăng đúng trên đối tượng đã nhận, nhưng đối tượng ấy có thể không còn là giá trị hiện tại trong map.

Vì thế, cách này có thể phù hợp nếu ánh xạ bộ đếm được giữ ổn định và yêu cầu cho phép đếm theo vòng đời đó. Nếu quy tắc là “mỗi lần tăng phải cập nhật chính giá trị đang đăng ký tại khóa, phối hợp với cả xóa/reset”, cần xác định lại giao thức; chỉ ghép hai đối tượng an toàn không chứng minh toàn bộ công việc.

`LongAdder` hay một collection concurrent bên trong không tự xóa vấn đề vòng đời này. Điều cần làm trước là định nghĩa ý nghĩa của xóa, reset, thay phiên bản và những thao tác đang thực hiện.

## 8. Duyệt an toàn và đọc bản chụp nhất quán là hai yêu cầu khác nhau

### 8.1. Duyệt synchronized wrapper phải giữ khóa cho cả lượt duyệt

Đoạn minh họa sau cho thấy cách lấy bản sao của synchronized map:

```java
Map<String, Integer> shared =
        Collections.synchronizedMap(new HashMap<>());

Map<String, Integer> snapshot;
synchronized (shared) {
    snapshot = new HashMap<>(shared);
}
```

Đây là **đoạn mã minh họa**, với mọi truy cập map nền đều qua cùng wrapper. Việc sao chép diễn ra trong khối khóa, nên các thao tác sửa qua wrapper ở luồng khác phải chờ. Sau đó có thể duyệt `snapshot` ngoài khóa nếu bản sao chỉ do luồng hiện tại dùng hoặc được công bố bằng giao thức phù hợp.

Muốn duyệt trực tiếp view, giữ khóa map cho **cả** quá trình tạo và sử dụng iterator:

```java
synchronized (shared) {
    for (Map.Entry<String, Integer> entry : shared.entrySet()) {
        System.out.println(entry.getKey() + "=" + entry.getValue());
    }
}
```

Đoạn này chỉ minh họa phạm vi khóa; không hứa thứ tự in của `HashMap`. Với view từ synchronized sorted/navigable wrapper, cần tuân thủ khóa của wrapper gốc theo tài liệu. Không đổi khóa sang đối tượng view chỉ vì vòng lặp đang duyệt view đó. [Collections Java 17 — quy tắc duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map))

Giữ khóa trong lúc in, gọi hàm bên ngoài hoặc làm việc chậm có thể kéo dài thời gian chờ của bên khác. Khi yêu cầu cho phép, lấy bản chụp cần thiết dưới khóa rồi xử lý bản chụp ở ngoài thường làm phạm vi giữ khóa dễ kiểm soát hơn.

Bản sao của map vẫn chỉ độc lập về các ánh xạ và cấu trúc chứa. Trong ví dụ này `String` và `Integer` không đổi trạng thái, nên không có phần tử có thể sửa bên ngoài làm thay đổi số đã chụp. Với giá trị khác, phải xét độ sâu của bản chụp.

### 8.2. Concurrent traversal không mặc định là ảnh chụp toàn bộ tại một thời điểm

Iterator của nhiều collection concurrent được quy định là nhất quán yếu (weakly consistent): có thể duyệt khi cấu trúc đang thay đổi mà không mang ngữ nghĩa ảnh chụp bất biến toàn bộ. [java.util.concurrent — Concurrent Collections](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html)

Vì vậy, việc `new HashMap<>(concurrentMap)` tạo một map mới không tự chứng minh dữ liệu nguồn là ảnh chụp toàn cục tại một thời điểm. Bản đích có thể giữ nguyên kết quả sao chép sau đó, nhưng tính ổn định của bản đích và tính nhất quán của thời điểm lấy dữ liệu nguồn là hai câu hỏi khác nhau.

Cũng không dùng `size() == 0` hoặc `isEmpty()` như một quyền giữ chỗ để thao tác sau. Trong `ConcurrentHashMap`, các phép quan sát tổng hợp khi có cập nhật đồng thời chỉ phản ánh trạng thái tạm thời; `putAll`/`clear` không cung cấp một giao dịch toàn map cho người đọc đồng thời. [ConcurrentHashMap Java 17 — trạng thái tổng hợp](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

Nếu ứng dụng cần đối chiếu toàn bộ dữ liệu cùng một phiên bản, hãy định nghĩa cơ chế riêng: cùng khóa cho cả ghi và chụp, trạng thái bất biến theo phiên bản, hoặc một thiết kế lưu trữ có ranh giới giao dịch thích hợp.

### 8.3. Iterator dạng snapshot cũng không gộp các lời gọi khác nhau

`CopyOnWriteArrayList` có iterator nhìn phiên bản mảng tại lúc iterator được tạo. Nhưng `contains("Lan")` rồi `add("Lan")` là hai lời gọi khác nhau; một iterator có ảnh chụp không biến chuỗi đó thành thêm có điều kiện.

Lớp này có `addIfAbsent` để diễn đạt phép thêm nếu chưa có. Ảnh chụp cấu trúc cũng không sao chép sâu trạng thái của các đối tượng phần tử. [CopyOnWriteArrayList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html#addIfAbsent(E))

Tương tự, gọi `get(0)` sau một lần `isEmpty()` trên danh sách có thể là hai quan sát khác thời điểm; sự an toàn của từng phương thức không giữ chỉ số còn hợp lệ đến lần gọi tiếp theo.

Không có `ConcurrentModificationException` cũng không chứng minh quy trình đúng. Ngược lại, không nên dùng ngoại lệ đó làm cơ chế đồng bộ hay làm bằng chứng rằng mọi lỗi tranh chấp chắc chắn sẽ được phát hiện. Iterator của từng loại collection có hợp đồng riêng; cơ chế fail-fast là để phát hiện lỗi theo khả năng tốt nhất, không phải một giao thức giữ bất biến. [HashMap Java 17 — fail-fast iterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)

## 9. Queue: lấy kết quả của thao tác nhận việc, không giữ một quan sát cũ

### 9.1. `isEmpty` không giữ lại phần tử cho người vừa kiểm tra

Đoạn minh họa sau cố ý để lộ lỗi khi hai bên cùng lấy việc từ queue:

```java
if (!jobs.isEmpty()) {
    String job = jobs.remove();
}
```

Với `ConcurrentLinkedQueue`, luồng A có thể thấy queue còn việc; luồng B lấy mất phần tử cuối; sau đó A gọi `remove` trên queue trống. `remove` ném `NoSuchElementException` trong trường hợp trống, còn `poll` trả về null. [Queue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html)

Một lần lấy không chờ nên dựa vào **kết quả của chính `poll`**. Đoạn minh họa dưới đây chưa gồm phần xử lý công việc:

```java
String job = jobs.poll();
if (job != null) {
    System.out.println("Da nhan: " + job);
} else {
    System.out.println("Hien chua co viec");
}
```

Giả sử `jobs` là `ConcurrentLinkedQueue<String>`. Loại queue này không cho phần tử null, nên null biểu thị lần lấy không nhận được phần tử. `size` của nó còn cần duyệt và không đáng dùng làm phép giữ chỗ khi có thay đổi đồng thời. [ConcurrentLinkedQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedQueue.html)

Khi hai luồng cùng `poll` một phần tử duy nhất, một bên lấy được việc, bên còn lại nhận null. Đây có thể là kết quả hoàn toàn đúng của một giao thức lấy việc không chờ. Không cần một câu hỏi `isEmpty` trước đó.

Tuy nhiên, null chỉ cho biết **lần lấy đó** không có phần tử. Nó không chứng minh nhà sản xuất đã hoàn tất hay hệ thống sẽ không có việc mới. Dừng một nhóm tác vụ tiêu thụ cần một quy tắc kết thúc riêng.

### 9.2. Nếu cần chờ, dùng thao tác chờ đúng nghĩa

Với `BlockingQueue`, chọn `take()` khi cần chờ tới khi lấy được phần tử; chọn `poll(timeout, unit)` khi có giới hạn chờ. Khi thêm, `offer` trả kết quả thành công/thất bại; `put` có thể chờ chỗ trống. [BlockingQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)

Quan sát `remainingCapacity() > 0` rồi `add` không giữ chỗ cho luồng hiện tại. Một bên khác có thể thêm trước. Hãy quyết định theo kết quả của `offer` hoặc theo giao thức chờ `put` phù hợp.

Nếu API trả về `InterruptedException`, cần truyền ngoại lệ lên tầng có trách nhiệm hoặc khôi phục cờ ngắt khi chuyển cách báo lỗi theo thiết kế. Không bắt rồi bỏ qua để tiếp tục như đã nhận được việc.

Cũng không giữ một khóa bên ngoài rồi gọi `take` vô thời hạn nếu nhà sản xuất phải lấy chính khóa đó để đưa việc vào. Thiết kế ấy có thể giữ khóa trong khi chờ một điều kiện mà chính nó ngăn bên khác tạo ra.

Nhận một phần tử an toàn không đồng nghĩa xử lý bên ngoài queue thành công đúng một lần. Nếu luồng lấy việc rồi lỗi trước khi lưu kết quả, cơ chế khôi phục/ghi nhận hoàn thành thuộc giao thức xử lý công việc, không phải bảo đảm của `poll`.

## 10. Thí nghiệm: queue và danh sách copy-on-write

Chương trình đầy đủ dưới đây kiểm chứng hai kiểu “kiểm tra rồi làm”:

- Queue: kiểm tra còn phần tử, để bên khác lấy hết, rồi `remove`.
- Danh sách: hai bên cùng xác nhận chưa có tên, rồi cả hai `add`.

Các trường hợp đúng dùng `poll` và `addIfAbsent`. Tác vụ nào thắng có thể thay đổi theo lịch chạy; stdout chỉ ghi các kết quả tổng hợp không phụ thuộc danh tính bên thắng.

Lưu thành **`CollectionProtocolDemo.java`**:

```java
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CollectionProtocolDemo {
    public static void main(String[] args) throws Exception {
        showQueueRace();
        showSinglePoll();
        showListRace();
        showAddIfAbsent();
    }

    private static void showQueueRace() throws Exception {
        ConcurrentLinkedQueue<String> jobs = new ConcurrentLinkedQueue<>();
        jobs.add("job-1");
        CountDownLatch checked = new CountDownLatch(1);
        CountDownLatch removed = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> {
                boolean wasNonEmpty = !jobs.isEmpty();
                checked.countDown();
                await(removed);
                if (wasNonEmpty) {
                    try {
                        return jobs.remove();
                    } catch (NoSuchElementException ex) {
                        return "NoSuchElementException";
                    }
                }
                return "unexpected empty observation";
            });
            Future<String> second = pool.submit(() -> {
                await(checked);
                try {
                    return jobs.poll();
                } finally {
                    removed.countDown();
                }
            });
            String removedJob = second.get(10, TimeUnit.SECONDS);
            String firstResult = first.get(10, TimeUnit.SECONDS);
            require("job-1".equals(removedJob), "wrong removed job");
            require("NoSuchElementException".equals(firstResult), "race not shown");
            System.out.println("check then remove = " + firstResult);
        } finally {
            stop(pool);
        }
    }

    private static void showSinglePoll() throws Exception {
        ConcurrentLinkedQueue<String> jobs = new ConcurrentLinkedQueue<>();
        jobs.add("job-1");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> jobs.poll());
            Future<String> second = pool.submit(() -> jobs.poll());
            String a = first.get(10, TimeUnit.SECONDS);
            String b = second.get(10, TimeUnit.SECONDS);
            int claimed = (a == null ? 0 : 1) + (b == null ? 0 : 1);
            int empty = (a == null ? 1 : 0) + (b == null ? 1 : 0);
            require(claimed == 1 && empty == 1, "wrong poll outcome");
            require("job-1".equals(a) || "job-1".equals(b), "wrong job");
            System.out.println("poll: claimed = " + claimed + ", empty = " + empty);
        } finally {
            stop(pool);
        }
    }

    private static void showListRace() throws Exception {
        CopyOnWriteArrayList<String> names = new CopyOnWriteArrayList<>();
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                boolean absent = !names.contains("Lan");
                bothChecked.await(5, TimeUnit.SECONDS);
                if (absent) {
                    names.add("Lan");
                }
                return null;
            });
            Future<?> second = pool.submit(() -> {
                boolean absent = !names.contains("Lan");
                bothChecked.await(5, TimeUnit.SECONDS);
                if (absent) {
                    names.add("Lan");
                }
                return null;
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            require(names.size() == 2, "duplicate schedule not shown");
            System.out.println("contains then add = " + names);
        } finally {
            stop(pool);
        }
    }

    private static void showAddIfAbsent() throws Exception {
        CopyOnWriteArrayList<String> names = new CopyOnWriteArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> names.addIfAbsent("Lan"));
            Future<Boolean> second = pool.submit(() -> names.addIfAbsent("Lan"));
            boolean a = first.get(10, TimeUnit.SECONDS);
            boolean b = second.get(10, TimeUnit.SECONDS);
            require(a != b && names.size() == 1, "wrong conditional add");
            System.out.println("addIfAbsent = " + names);
        } finally {
            stop(pool);
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("latch timed out");
        }
    }

    private static void stop(ExecutorService pool) throws InterruptedException {
        pool.shutdownNow();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("pool did not terminate");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all CollectionProtocolDemo.java
java CollectionProtocolDemo
```

Stdout khi chạy thành công:

```text
check then remove = NoSuchElementException
poll: claimed = 1, empty = 1
contains then add = [Lan, Lan]
addIfAbsent = [Lan]
```

Ở trường hợp queue sai, latch `checked` cho phép bên thứ hai lấy việc chỉ sau khi bên thứ nhất quan sát queue còn phần tử. Latch `removed` chỉ cho bên thứ nhất tiếp tục khi việc đã bị lấy. Vì vậy, ngoại lệ phản ánh đúng khoảng hở giữa hai lần gọi.

Khối `catch` ở đây chỉ chuyển ngoại lệ thành kết quả để quan sát trong bài thử. Nó không phải cách sửa giao thức lấy việc. Bản sửa là quyết định bằng kết quả của một lần `poll`.

Ở trường hợp danh sách sai, barrier bảo đảm cả hai đều có `absent = true` trước khi thêm. Hai lần `add` hợp lệ tạo ra `[Lan, Lan]`. Danh sách không bị hỏng; quy tắc không thêm trùng do chương trình tự đặt mới là thứ bị vi phạm.

Ở bản dùng `addIfAbsent`, hai giá trị boolean trả về phải khác nhau và danh sách có đúng một tên. Điều này không hứa tên không bao giờ bị xóa/thêm lại sau đó. Nếu một đường khác vẫn gọi `add("Lan")` trực tiếp, quy tắc không trùng có thể lại bị phá.

Nhìn hai ví dụ cạnh nhau sẽ thấy cùng một lỗi thiết kế dưới hai hình thức: **đọc một điều kiện, nhả quyền phối hợp, rồi hành động như thể điều kiện còn nguyên**. Thao tác có điều kiện gộp sẵn có giá trị vì nó đặt kiểm tra và thay đổi vào cùng ranh giới bảo vệ.

## 11. Quy trình phân tích một chuỗi thao tác trong mã thực tế

### 11.1. Viết yêu cầu thành điều kiện có thể kiểm tra

Trước khi chọn `ConcurrentHashMap`, `synchronized` hay `CopyOnWriteArrayList`, hãy viết yêu cầu cụ thể. Ví dụ:

- Mỗi lần tăng hoàn tất đóng góp đúng một đơn vị, nếu không có thao tác reset/xóa.
- Một đăng ký không ghi đè đăng ký đang tồn tại.
- Một phép di chuyển không cho người đọc quan sát tổng khác 20.
- Mỗi lần lấy việc nhận tối đa một phần tử hoặc nhận kết quả không có việc.
- Một danh sách thành viên không chứa hai phần tử bằng nhau theo quy tắc đã chọn.

Không phải yêu cầu nào cũng cần một ảnh chụp toàn hệ thống. Ngược lại, nếu yêu cầu cần nhiều dữ liệu cùng một phiên bản, một phép đọc an toàn từng phần sẽ chưa đáp ứng.

### 11.2. Liệt kê mọi bước và điểm có thể xen kẽ

Chuyển một dòng viết gọn thành các hành động rõ ràng. Với `put(key, get(key) + 1)`, tách thành đọc, tính, ghi. Với `if (size() < limit) add(x)`, tách thành đo kích thước, so sánh, thêm.

Sau đó thử đặt một thao tác của luồng khác vào mỗi khoảng giữa. Nếu tạo được lịch hợp lệ khiến điều kiện sai, bạn đã có một phản ví dụ. Không cần chứng minh lỗi “thường xuyên xảy ra” mới sửa.

Nếu không tìm được lịch sai, vẫn phải đọc hợp đồng: có thể còn vấn đề công bố dữ liệu, phần tử có thể sửa, đường đi vòng hoặc ngoại lệ giữa chừng mà cách tách bước ban đầu chưa tính đến.

### 11.3. Chọn ranh giới bảo vệ nhỏ nhất đủ đáp ứng yêu cầu

| Phạm vi của điều kiện | Cơ chế có thể phù hợp | Điều cần kiểm chứng thêm |
| --- | --- | --- |
| Một cập nhật theo khóa | `putIfAbsent`, `replace`, `remove` có điều kiện, hoặc `compute`/`merge` trên lớp phù hợp | Cam kết lớp thực tế, giá trị trả về, quy tắc xóa/thay |
| Một chuỗi trên synchronized wrapper | Khóa chính wrapper cho toàn chuỗi | Không có đường sửa map/list nền bên ngoài |
| Nhiều khóa hoặc nhiều collection | Lớp bao riêng tư với khóa chung, hoặc thiết kế trạng thái gộp | Mọi người đọc và ghi cùng giao thức |
| Trạng thái trong một phần tử | Phần tử bất biến hoặc cơ chế đồng bộ riêng | Không lộ tham chiếu cho phép sửa ngoài giao thức |
| Lấy một việc hiện có | `poll` trên concurrent queue thích hợp | Xử lý null và vòng đời nhà sản xuất |
| Chờ việc/chỗ trống | Thao tác chờ của `BlockingQueue` | Ngắt, thời hạn, kết thúc và khóa đang giữ |
| Đọc toàn bộ dữ liệu cùng phiên bản | Chụp dưới khóa chung hoặc công bố trạng thái bất biến | Độ sâu bản sao và mọi đường cập nhật |

Đây là cách chọn theo yêu cầu. Không có cơ chế trong bảng tự bao phủ tất cả các hàng khác.

### 11.4. Đóng gói giao thức để người gọi khó dùng sai

Một dịch vụ chỉ cung cấp `increase`, `registerIfAbsent`, `move` và `snapshot` thường dễ kiểm soát hơn việc trả map sống rồi yêu cầu mọi nơi nhớ cùng một quy tắc khóa.

Nếu một đối tượng bị chia sẻ rộng, hãy rà soát cả:

- Chỗ tạo và công bố đối tượng cho các luồng.
- Phương thức đọc, ghi, xóa, reset và các thao tác hàng loạt.
- View, iterator và tham chiếu phần tử thoát ra ngoài.
- Hàm callback có gọi mã khác hoặc thực hiện tác dụng phụ.
- Đường đọc báo cáo dùng nhiều khóa để suy luận một điều kiện.
- Hành vi khi công việc bị ngắt, hết thời gian hoặc ném ngoại lệ.

Khóa riêng tư giúp giới hạn người phối hợp; trả về bản chụp cần thiết giúp giới hạn khả năng sửa ngoài ý muốn. Nếu buộc phải công khai collection, tài liệu của API cần mô tả đủ ranh giới và trách nhiệm của người gọi.

### 11.5. Kiểm thử phải đi cùng lập luận

Ba chương trình của bài ép các lịch sai cụ thể, rồi kiểm tra kết quả của cách sửa. Chúng phù hợp để học vì hành vi sai không phụ thuộc may rủi và lỗi tác vụ được chuyển về luồng chính.

Một lần chạy được 4.000 không tự chứng minh mọi cách cài đặt bộ đếm đều đúng. Một bài thử chạy hàng triệu lần không thấy lỗi cũng chưa loại bỏ được lịch chưa xuất hiện. Bằng chứng chính vẫn là hợp đồng API, phạm vi khóa, tính riêng tư của trạng thái và việc mọi đường truy cập tuân thủ cùng giao thức.

Không dùng `sleep`, tốc độ in log hay việc “máy của tôi chỉ có một lõi” để chứng minh không có tranh chấp. Đồng thời, không đặt thao tác chờ của bài thử vào trong vùng khóa theo cách làm thay đổi bản chất giao thức cần kiểm chứng.

## 12. Luyện tập và tự kiểm tra

### Bài 1 — Giới hạn số phần tử

Một `CopyOnWriteArrayList<String>` được dùng chung. Hai luồng chạy đoạn mã minh họa sau, với giới hạn 10:

```java
if (names.size() < 10) {
    names.add(candidate);
}
```

**Yêu cầu:** Khi danh sách đang có 9 phần tử và hai ứng viên khác nhau, hãy viết lịch làm kích thước thành 11. `addIfAbsent` có sửa đúng quy tắc giới hạn không?

**Đối chiếu:** Hai luồng đều có thể quan sát kích thước 9 rồi lần lượt thêm. `addIfAbsent` chỉ gộp kiểm tra phần tử bằng nhau và thêm, không kiểm tra giới hạn 10. Cần một cơ chế chung bao cả phép kiểm tra sức chứa và thêm, được mọi đường cập nhật tuân thủ; hoặc đổi sang cấu trúc/giao thức có giới hạn phù hợp với yêu cầu.

### Bài 2 — Khóa đúng bên ghi nhưng sai bên đọc

`move` dùng một khóa chung để sửa hai khóa. `total` chạy `map.get("left") + map.get("right")` mà không lấy khóa. Map là `ConcurrentHashMap`.

**Yêu cầu:** Phần nào của điều kiện “luôn đọc tổng bằng 20” còn thiếu?

**Đối chiếu:** Người đọc không tham gia giao thức bảo vệ bất biến. Nó vẫn có thể quan sát giữa hai lần ghi hoặc kết hợp dữ liệu từ hai thời điểm. Cần lấy cùng khóa cho toàn lần đọc, trả bản chụp được tạo dưới khóa, hoặc biểu diễn hai số thành một trạng thái gộp có cơ chế thay thế phù hợp.

### Bài 3 — Ánh xạ và đối tượng bên trong

Một dịch vụ tạo danh sách bằng `computeIfAbsent`, rồi trả `List<String>` đó cho người gọi. Dịch vụ tự dùng `compute` cho mọi lần sửa về sau.

**Yêu cầu:** Điều gì xảy ra nếu người gọi giữ và sửa danh sách đã nhận?

**Đối chiếu:** Một đường sửa có thể đi ngoài giao thức của dịch vụ. `compute` ở các đường khác không khóa mọi hành động trên danh sách đã thoát ra. Cần thiết kế giá trị bất biến/không cho sửa, bản sao có độ sâu phù hợp, hoặc API không trả tham chiếu sửa được. Cũng cần xem bản không cho sửa có còn phản ánh một cấu trúc nền đang thay đổi hay không.

### Bài 4 — Một dòng tăng atomic nhưng reset đồng thời

Map lưu `AtomicInteger`. Luồng A lấy bộ đếm, luồng B xóa khóa rồi tạo bộ đếm mới, luồng A tăng đối tượng cũ.

**Yêu cầu:** Lần tăng có bị mất trên chính đối tượng cũ không? Nó có nhất thiết xuất hiện khi đọc bộ đếm hiện tại trong map không?

**Đối chiếu:** Phép tăng trên đối tượng cũ vẫn có thể hoàn toàn đúng và nguyên tử. Nhưng map có thể đã trỏ sang đối tượng mới, nên người đọc theo khóa không thấy lần tăng ấy. Cần định nghĩa reset/xóa có ý nghĩa gì đối với thao tác đang chạy và chọn ranh giới phối hợp bao được ý nghĩa đó.

### Bài 5 — Queue tạm thời trống

Một tác vụ gọi `poll()`, nhận null rồi đóng toàn bộ hệ thống tiêu thụ; nhà sản xuất vẫn đang hoạt động.

**Yêu cầu:** Cách lấy đã an toàn, vậy vì sao quy trình vẫn có thể sai?

**Đối chiếu:** An toàn của một lần lấy không định nghĩa điều kiện kết thúc luồng công việc. Null phản ánh lần lấy hiện tại. Hệ thống cần tín hiệu hoàn tất, hủy hoặc một giao thức kết thúc khác, kết hợp với việc các nhà sản xuất thực sự đã dừng.

## 13. Tài liệu để kiểm tra hợp đồng khi áp dụng

Các ví dụ và lịch xen kẽ trong bài là minh họa tự xây dựng. Khi đổi lớp collection hoặc phiên bản Java, hãy đối chiếu đúng hợp đồng của lớp/phương thức đang dùng:

- [Collections Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html): synchronized wrapper và khóa khi duyệt.
- [ConcurrentMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html): thao tác có điều kiện và hành vi mặc định của hàm tính.
- [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): phạm vi nguyên tử của cập nhật theo khóa và giới hạn quan sát toàn map.
- [Queue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html), [BlockingQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): nhận/thêm bằng kết quả thao tác và biến thể chờ.
- [CopyOnWriteArrayList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html): iterator dạng snapshot và `addIfAbsent`.
- [JLS chương 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): monitor, thứ tự và mô hình bộ nhớ Java.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-003">Collections.synchronizedList, synchronizedSet và synchronizedMap</a>

<details>
<summary>Xem chi tiết</summary>

# Collections.synchronizedList, synchronizedSet và synchronizedMap

## 1. Lớp bao đồng bộ giải quyết việc truy cập collection như thế nào?

Khi nhiều luồng thực thi cùng sử dụng một collection thông thường, việc một luồng thêm hoặc xóa trong lúc luồng khác truy cập có thể làm chương trình không còn đáp ứng hợp đồng của cấu trúc đó. Một cách tổ chức là đặt các thao tác truy cập sau cùng một cơ chế khóa.

Ba phương thức của `Collections` tạo lớp bao cho mục đích này:

| Phương thức | Nhận cấu trúc nền | Trả về |
| --- | --- | --- |
| `synchronizedList(list)` | Một `List<E>` | `List<E>` có các thao tác truy cập trực tiếp được đồng bộ |
| `synchronizedSet(set)` | Một `Set<E>` | `Set<E>` có các thao tác truy cập trực tiếp được đồng bộ |
| `synchronizedMap(map)` | Một `Map<K,V>` | `Map<K,V>` có các thao tác truy cập trực tiếp được đồng bộ |

Lớp bao vẫn dùng cấu trúc nền đã truyền vào. Nó không chuyển mọi phần tử sang một collection độc lập và không làm các đối tượng phần tử tự trở thành an toàn đồng thời.

Mô hình cần hiểu là:

```text
Các bên sử dụng
      ↓
Một lớp bao đồng bộ được chia sẻ
      ↓
Cùng cơ chế khóa cho các thao tác phù hợp
      ↓
Collection nền
```

Những thao tác trực tiếp như `list.add`, `set.add` hoặc `map.put` đi qua lớp bao. Nhưng một thuật toán gồm nhiều lời gọi, hoặc một quá trình duyệt qua iterator, không tự trở thành một khối thao tác nguyên vẹn chỉ vì collection đã được bao.

Vì thế có ba cấp cần phân biệt:

1. **Một thao tác truy cập trực tiếp được đồng bộ.**
2. **Một chuỗi thao tác nghiệp vụ cần cùng nằm trong một vùng khóa.**
3. **Một lần duyệt cần được bảo vệ suốt thời gian sử dụng bộ duyệt.**

Bài này lần lượt kiểm chứng ba cấp đó cho List, Set và Map. Các chương trình dùng Java 17, có tên tệp, lệnh biên dịch và stdout để chạy lại độc lập.

## 2. Tạo một lớp bao và thống nhất đường truy cập

Cách khởi tạo minh họa, đặt trong phương thức có các `import` tương ứng:

```java
List<String> names = Collections.synchronizedList(new ArrayList<>());
Set<String> codes = Collections.synchronizedSet(new HashSet<>());
Map<String, Integer> counts = Collections.synchronizedMap(new HashMap<>());
```

Cấu trúc nền được tạo trực tiếp bên trong lời gọi để giảm việc giữ lại tham chiếu khác có thể bỏ qua lớp bao.

Hợp đồng của các phương thức nhấn mạnh rằng mọi truy cập tới cấu trúc nền phải đi qua lớp bao được trả về. Nếu vẫn sửa một `ArrayList` nền qua tham chiếu cũ trong khi bên khác dùng lớp bao, không còn cùng một chính sách bảo vệ. [Javadoc synchronizedList, synchronizedSet và synchronizedMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html)

### Lớp bao không thay đổi mọi tính chất của cấu trúc nền

Ví dụ:

- `synchronizedList(new ArrayList<>())` vẫn có ý nghĩa danh sách, giữ phần tử trùng và thứ tự chỉ số.
- `synchronizedSet(new HashSet<>())` vẫn có quy tắc phần tử duy nhất của tập băm và không được bảo đảm thứ tự duyệt.
- `synchronizedMap(new HashMap<>())` vẫn dùng quy tắc khóa của map băm.
- Khả năng chứa `null` hoặc hỗ trợ một thao tác sửa cụ thể còn phụ thuộc cấu trúc nền.
- Bao một danh sách vốn không cho sửa không tự làm `add` trở thành thao tác được hỗ trợ.

“Được đồng bộ” và “cho phép sửa” là hai tính chất khác nhau.

### Đừng tạo hai lớp bao độc lập trên cùng một cấu trúc nền

Đoạn dưới minh họa một thiết kế không đáp ứng yêu cầu dùng chung khóa; không phải chương trình mẫu để sử dụng:

```java
List<String> raw = new ArrayList<>();
List<String> first = Collections.synchronizedList(raw);
List<String> second = Collections.synchronizedList(raw);
```

`first` và `second` là hai lớp bao độc lập. Các thao tác đi qua hai đối tượng đó không được bảo vệ bởi cùng monitor chỉ vì cùng tham chiếu tới `raw`.

Tương tự, khóa trên `raw` không thay thế cho khóa mà các thao tác qua lớp bao đang sử dụng. Cần một đường truy cập và một chính sách khóa nhất quán, thay vì mỗi bên tự bao hoặc tự chọn đối tượng khóa.

Nếu muốn tiếp nhận dữ liệu ban đầu rồi sở hữu cấu trúc riêng, có thể tạo bản sao nền trước khi bao. Bản sao đó vẫn phải được tạo tại thời điểm việc đọc dữ liệu đầu vào hợp lệ; lớp bao không sửa được một quá trình sao chép vốn đang bị can thiệp.

## 3. Thí nghiệm các thao tác trực tiếp trên ba lớp bao

Lưu vào `SynchronizedWrappersDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SynchronizedWrappersDemo {
    public static void main(String[] args) throws InterruptedException {
        List<Integer> list = Collections.synchronizedList(new ArrayList<>());
        Set<Integer> set = Collections.synchronizedSet(new HashSet<>());
        Map<Integer, Integer> map = Collections.synchronizedMap(new HashMap<>());

        Runnable write = () -> {
            for (int i = 0; i < 200; i++) {
                list.add(i);
                set.add(i);
                map.put(i, i * 2);
            }
        };
        Thread first = new Thread(write);
        Thread second = new Thread(write);
        first.start();
        second.start();
        first.join();
        second.join();

        int listSum;
        synchronized (list) {
            listSum = list.stream().mapToInt(Integer::intValue).sum();
        }
        TreeSet<Integer> sortedSet;
        synchronized (set) {
            sortedSet = new TreeSet<>(set);
        }

        System.out.println("listSize=" + list.size());
        System.out.println("listSum=" + listSum);
        System.out.println("setSize=" + set.size());
        System.out.println("setFirstForDisplay=" + sortedSet.first());
        System.out.println("setLastForDisplay=" + sortedSet.last());
        System.out.println("mapSize=" + map.size());
        System.out.println("mapValue199=" + map.get(199));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SynchronizedWrappersDemo.java
java SynchronizedWrappersDemo
```

Stdout:

```text
listSize=400
listSum=39800
setSize=200
setFirstForDisplay=0
setLastForDisplay=199
mapSize=200
mapValue199=398
```

Hai luồng thực thi đều đưa các số từ 0 đến 199 vào ba cấu trúc:

- List nhận hai lần mỗi số, nên có 400 phần tử; tổng là `2 × (0 + ... + 199) = 39800`.
- Set chỉ giữ một lần mỗi số, nên có 200 phần tử.
- Map có 200 khóa; hai bên ghi cùng giá trị `i * 2` cho mỗi khóa nên khóa 199 có giá trị 398.

Chương trình không dự đoán thứ tự hai luồng xen kẽ khi thêm vào List. Nó cũng không dự đoán thứ tự duyệt của `HashSet`. Hai giá trị đầu và cuối được in từ một `TreeSet` bản sao tạo riêng để hiển thị có thứ tự.

Việc tạo bản sao tập được đặt trong `synchronized (set)`, vì quá trình đọc các phần tử để sao chép là một lần duyệt. Phép tính tổng stream cũng nằm trọn trong `synchronized (list)`.

Các lời gọi `join()` chờ hai luồng ghi kết thúc trước khi in kết quả tổng hợp. Nhờ vậy stdout không mô tả một trạng thái nửa chừng khi bên ghi vẫn còn chạy.

### Ba cấu trúc không tự trở thành một giao dịch chung

Mỗi lớp bao có cơ chế khóa của nó. Ba câu lệnh thêm vào List, Set và Map trong thân vòng lặp không phải một giao dịch bao trùm cả ba cấu trúc.

Ví dụ, khi các luồng còn đang chạy, một bên quan sát có thể thấy thao tác với List đã xong nhưng thao tác với Map tương ứng chưa xảy ra. Nếu nghiệp vụ yêu cầu một bất biến liên hệ nhiều cấu trúc, cần thiết kế một vùng bảo vệ ở lớp sở hữu dữ liệu và thống nhất mọi đường truy cập liên quan.

Không suy từ kết quả đúng sau `join` rằng mọi trạng thái trung gian cũng đáp ứng bất biến liên cấu trúc.

## 4. Một chuỗi kiểm tra rồi hành động cần cùng vùng khóa

Xét yêu cầu: chỉ thêm mã `X` vào danh sách nếu chưa có.

Đoạn minh họa sau chưa đủ, dù `names` là một synchronized list:

```java
if (!names.contains("X")) {
    names.add("X");
}
```

`contains` và `add` là hai thao tác riêng. Sau khi kiểm tra xong và trước khi thêm, một bên khác có thể thực hiện cùng kiểm tra rồi thêm.

Một lịch xen kẽ hợp lệ là:

```text
Luồng A: contains(X) → false
Luồng B: contains(X) → false
Luồng A: add(X)
Luồng B: add(X)
Kết quả: [X, X]
```

Không có yêu cầu hai luồng phải đồng thời đi vào chính phương thức `add`. Từng thao tác được bảo vệ nhưng toàn bộ quyết định vẫn sai mục tiêu “chỉ có một X”.

### Thí nghiệm tạo lịch xen kẽ có kiểm soát

Lưu vào `SynchronizedCheckThenActDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class SynchronizedCheckThenActDemo {
    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        List<String> broken = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch bothChecked = new CountDownLatch(2);
        Runnable splitOperation = () -> {
            boolean absent = !broken.contains("X");
            bothChecked.countDown();
            await(bothChecked);
            if (absent) {
                broken.add("X");
            }
        };
        Thread first = new Thread(splitOperation);
        Thread second = new Thread(splitOperation);
        first.start();
        second.start();
        first.join();
        second.join();
        System.out.println("separateCalls=" + broken);
        System.out.println("separateSize=" + broken.size());

        List<String> guarded = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        Runnable combinedOperation = () -> {
            await(start);
            synchronized (guarded) {
                if (!guarded.contains("X")) {
                    guarded.add("X");
                }
            }
        };
        Thread third = new Thread(combinedOperation);
        Thread fourth = new Thread(combinedOperation);
        third.start();
        fourth.start();
        start.countDown();
        third.join();
        fourth.join();
        System.out.println("oneCriticalSection=" + guarded);
        System.out.println("guardedSize=" + guarded.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SynchronizedCheckThenActDemo.java
java SynchronizedCheckThenActDemo
```

Stdout:

```text
separateCalls=[X, X]
separateSize=2
oneCriticalSection=[X]
guardedSize=1
```

Phần `separateCalls` dùng `CountDownLatch(2)` để buộc cả hai luồng hoàn tất kiểm tra trước khi một trong hai được đi tiếp tới `add`. Cả hai đều đã thấy chưa có `X`, nên kết quả có hai `X`.

Đây là cách kiểm chứng một lịch xen kẽ có thể xảy ra. Không cần dựa vào `Thread.sleep` hoặc hy vọng chạy nhiều lần rồi tình cờ bắt được lỗi. Latch chỉ sắp xếp mốc của thí nghiệm; nó không tự gộp hai thao tác thành một thao tác nguyên tử. [Javadoc CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)

Phần `oneCriticalSection` đặt cả kiểm tra và thêm trong `synchronized (guarded)`. Khi một luồng đang ở vùng này, các thao tác đi qua cùng lớp bao từ bên khác phải phối hợp qua cùng khóa. Sau khi luồng đầu thêm `X`, luồng sau kiểm tra sẽ thấy nó đã có.

Không đặt một hàng rào chờ hai luồng cùng vào bên trong vùng khóa loại trừ nhau. Trong chương trình, tín hiệu bắt đầu của phần đúng được chờ trước khi lấy khóa.

### Vì sao khóa trên chính lớp bao?

Với lớp bao gốc được trả về từ các hàm trong bài, chính đối tượng lớp bao là monitor dùng để phối hợp với các thao tác trực tiếp. Khóa trên một `new Object()` riêng sẽ không tự chặn bên khác đang chỉ gọi `guarded.add`.

Một luồng đang giữ monitor có thể đi vào lại các thao tác cần cùng monitor đó; cơ chế này gọi là khóa có thể vào lại. Vì thế việc gọi `contains` và `add` bên trong `synchronized (guarded)` là mô hình phù hợp. [Java Language Specification về đồng bộ hóa](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.1)

Nếu yêu cầu dữ liệu thực sự là một tập giá trị duy nhất, `Set.add` vốn đã gộp kiểm tra thành viên và thêm vào một thao tác tập. Không cần dùng List chỉ để tự dựng lại mọi quy tắc của Set. Nhưng với một bất biến gồm nhiều điều kiện hoặc nhiều thao tác, vẫn phải xác định đúng phạm vi vùng khóa.

## 5. synchronizedMap không tự gộp get rồi put thành tăng bộ đếm

Một lỗi tương tự xảy ra với phép tăng giá trị:

```java
// Đoạn minh họa chưa đủ bảo vệ; counts là synchronized map.
int previous = counts.get("hits");
counts.put("hits", previous + 1);
```

Mỗi `get` hoặc `put` riêng được đồng bộ. Tuy nhiên, hai bên có thể cùng đọc 0, cùng tính 1 và lần lượt ghi 1. Kết quả tăng hai lần nhưng giá trị cuối chỉ là 1.

Muốn biểu diễn một phép tăng theo hai lời gọi này, cần khóa cả bước đọc lẫn bước ghi trên cùng lớp bao.

Lưu vào `SynchronizedMapCounterDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SynchronizedMapCounterDemo {
    public static void main(String[] args) throws InterruptedException {
        Map<String, Integer> counts =
                Collections.synchronizedMap(new HashMap<>());
        counts.put("hits", 0);

        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    synchronized (counts) {
                        int previous = counts.get("hits");
                        counts.put("hits", previous + 1);
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        Map<String, Integer> snapshot;
        synchronized (counts) {
            snapshot = new TreeMap<>(counts);
        }
        System.out.println("expected=4000");
        System.out.println("actual=" + counts.get("hits"));
        System.out.println("snapshot=" + snapshot);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SynchronizedMapCounterDemo.java
java SynchronizedMapCounterDemo
```

Stdout:

```text
expected=4000
actual=4000
snapshot={hits=4000}
```

Bốn luồng thực thi, mỗi luồng tăng 1000 lần. Mỗi lần tăng đặt `get` và `put` trong cùng `synchronized (counts)`, nên kết quả là 4000.

Việc đọc map để tạo `TreeMap` bản sao cũng được bảo vệ bằng khóa `counts`. Sau đó bản sao được dùng để hiển thị mà không tiếp tục giữ khóa của map dùng chung.

Ví dụ không nói rằng mọi thuật toán với Map đều phải viết tay bằng `get` rồi `put`. Một API gộp thao tác có thể diễn đạt mục tiêu tốt hơn trong tình huống phù hợp. Điều cần kiểm tra là hợp đồng của đúng phương thức và đúng loại Map, không suy ra chuỗi lời gọi riêng tự nguyên tử chỉ từ tên “synchronized”.

### Đọc nhiều lần cũng có thể là một thao tác ghép

Không chỉ cập nhật mới cần xem xét. Với List, đoạn kiểm tra không rỗng rồi lấy phần tử đầu có thể bị một bên khác xóa dữ liệu giữa hai lời gọi:

```java
// Đoạn minh họa phạm vi bảo vệ; list là synchronized list.
synchronized (list) {
    if (!list.isEmpty()) {
        String first = list.get(0);
        // Dùng first theo phạm vi cần bảo vệ.
    }
}
```

Nếu chỉ cần lấy một tham chiếu phần tử, sau khi đã lấy có thể xử lý tiếp ngoài khóa khi hợp đồng cho phép. Nếu cần đọc nhiều trạng thái liên quan của phần tử, phải xem cả chính sách bảo vệ trạng thái đó.

Giữ một biến tham chiếu collection là `final` cũng không tự gộp các lời gọi. `final` và tính nguyên tử của một thuật toán là hai vấn đề khác nhau.

## 6. Duyệt cần khóa suốt lần duyệt, không chỉ lúc lấy iterator

Lớp bao không giữ khóa thay cho người dùng trong toàn bộ thời gian sử dụng một `Iterator`, `Spliterator` hoặc `Stream`.

Với List hoặc Set gốc, mô hình duyệt là:

```java
// Đoạn minh họa; names là synchronized List<String>.
synchronized (names) {
    Iterator<String> iterator = names.iterator();
    while (iterator.hasNext()) {
        String value = iterator.next();
        // Xử lý ngắn, không can thiệp cấu trúc đang duyệt.
    }
}
```

Khởi tạo iterator bên trong vùng khóa rồi tiếp tục giữ khóa tới khi duyệt xong. Vòng lặp `for (String value : names)` cũng dùng cơ chế iterator; cú pháp ngắn hơn không tự có khóa bảo vệ.

Đối với Stream, cần bảo vệ cả quá trình thực thi thao tác kết thúc:

```java
// Đoạn minh họa; values là synchronized List<Integer>.
int total;
synchronized (values) {
    total = values.stream().mapToInt(Integer::intValue).sum();
}
```

Chỉ khóa lúc gọi `values.stream()` rồi thực hiện `sum()` sau khi đã ra khỏi khối khóa không bảo vệ được giai đoạn đọc phần tử.

### Khung nhìn của Map dùng khóa của Map gốc

Với `synchronizedMap`, các khung nhìn `keySet()`, `values()` và `entrySet()` vẫn gắn với Map. Khi duyệt chúng, khóa trên Map lớp bao được trả về, không khóa trên chính đối tượng khung nhìn. [Javadoc synchronizedMap về duyệt khung nhìn](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map))

Ví dụ `Set<K> keys = map.keySet()` không làm `keys` trở thành một tập bản sao. Thêm hoặc xóa ánh xạ trong Map có thể làm nội dung nhìn thấy qua `keys` thay đổi.

Lưu vào `SynchronizedMapViewsDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;

public class SynchronizedMapViewsDemo {
    public static void main(String[] args) {
        Map<String, Integer> map =
                Collections.synchronizedMap(new LinkedHashMap<>());
        map.put("A", 1);
        map.put("B", 2);
        map.put("C", 3);
        Set<String> keys = map.keySet();

        StringJoiner entries = new StringJoiner(",");
        int sum = 0;
        boolean holdsMapLock;
        Map<String, Integer> afterRemoval;
        synchronized (map) {
            holdsMapLock = Thread.holdsLock(map);
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                entries.add(entry.getKey() + "=" + entry.getValue());
                sum += entry.getValue();
            }
            Iterator<String> iterator = keys.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().equals("B")) {
                    iterator.remove();
                }
            }
            afterRemoval = new TreeMap<>(map);
        }

        map.put("D", 4);
        List<String> keySnapshot;
        synchronized (map) {
            keySnapshot = new ArrayList<>(keys);
        }
        System.out.println("holdsMapLock=" + holdsMapLock);
        System.out.println("entriesBeforeRemoval=" + entries);
        System.out.println("sumBeforeRemoval=" + sum);
        System.out.println("afterRemoval=" + afterRemoval);
        System.out.println("keysAfterLaterPut=" + keySnapshot);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SynchronizedMapViewsDemo.java
java SynchronizedMapViewsDemo
```

Stdout:

```text
holdsMapLock=true
entriesBeforeRemoval=A=1,B=2,C=3
sumBeforeRemoval=6
afterRemoval={A=1, C=3}
keysAfterLaterPut=[A, C, D]
```

Chương trình dùng `LinkedHashMap` nền để thứ tự chèn `A`, `B`, `C` được xác định cho phần hiển thị.

Trong `synchronized (map)`:

1. Duyệt `entrySet` và tính tổng 6.
2. Tạo iterator từ `keys`.
3. Xóa khóa `B` bằng chính `iterator.remove()`.
4. Tạo Map bản sao sau khi xóa.

Dòng `holdsMapLock=true` xác nhận monitor của Map đang được giữ ở vùng duyệt. Khóa dùng là `map`, không phải `keys` hoặc `map.entrySet()`.

Sau đó `map.put("D", 4)` thêm một ánh xạ. Khi đọc lại `keys` dưới khóa Map, khung nhìn có `[A, C, D]`. Nó phản ánh nguồn hiện tại chứ không giữ ảnh chụp từ lần lấy `keySet` ban đầu.

### Vì sao khóa trên khung nhìn không đủ?

Đối tượng được viết trong `synchronized (x)` xác định monitor được giữ. Khóa trên `keys` và khóa trên `map` là hai việc khác nhau.

Các thao tác cập nhật thông thường qua lớp bao Map phối hợp với monitor của Map. Một vùng `synchronized (keys)` không tự ngăn chúng chạy chỉ vì `keys` là khung nhìn của Map.

Nguyên tắc thực hành là giữ lại tham chiếu đến lớp bao gốc để dùng đúng monitor khi cần duyệt. Không chuyền một khung nhìn sang nơi khác rồi bỏ mất thông tin về chính sách khóa của nguồn.

### Khóa không hợp pháp hóa mọi thay đổi trong khi duyệt

Khóa ngăn bên khác truy cập qua cùng chính sách, nhưng không tự cho phép một iterator thông thường chịu mọi thay đổi cấu trúc từ chính luồng đang giữ khóa.

Chẳng hạn, vừa duyệt bằng iterator vừa gọi `map.remove` qua đường khác vẫn có thể vi phạm hợp đồng của iterator. Khi cần loại bỏ phần tử trong quá trình duyệt và iterator hỗ trợ thao tác đó, dùng `iterator.remove()` như chương trình.

Không dựa vào việc có hay không có `ConcurrentModificationException` để kết luận thuật toán đúng. Cơ chế phát hiện thay đổi là hỗ trợ tìm lỗi, không thay thế chính sách khóa và hợp đồng duyệt.

Cũng không giữ một `Map.Entry` lấy từ khung nhìn rồi xem nó như bản ghi bất biến có thể tùy ý sử dụng ngoài mọi khóa. Entry có thể gắn với cấu trúc nguồn; nếu cần dữ liệu để xử lý sau, sao chép khóa và giá trị theo chính sách phù hợp.

## 7. Chụp dữ liệu dưới khóa rồi xử lý bên ngoài

Một lần duyệt dài làm các bên cần cùng khóa phải chờ lâu hơn. Nếu công việc tiếp theo không cần nhìn thấy những cập nhật mới nhất, có thể tách thành hai giai đoạn:

1. Giữ khóa để lấy một trạng thái dữ liệu hợp lệ.
2. Nhả khóa rồi xử lý bản sao hoặc dữ liệu đầu ra đã tạo.

Với List, bản sao cấu trúc có thể là `new ArrayList<>(list)` trong khối `synchronized (list)`. Với Map, có thể sao chép ánh xạ dưới `synchronized (map)`. Nếu giá trị là đối tượng thay đổi được, vẫn cần xem mức độ độc lập của bản sao.

Lưu vào `SynchronizedSnapshotDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SynchronizedSnapshotDemo {
    static final class Document {
        private String title;

        Document(String title) {
            this.title = title;
        }
    }

    record DocumentView(String title) {}

    public static void main(String[] args) throws InterruptedException {
        Document first = new Document("draft");
        List<Document> documents = Collections.synchronizedList(
                new ArrayList<>(List.of(first)));

        List<Document> structuralSnapshot;
        List<DocumentView> projectedSnapshot;
        synchronized (documents) {
            structuralSnapshot = new ArrayList<>(documents);
            projectedSnapshot = documents.stream()
                    .map(document -> new DocumentView(document.title))
                    .toList();
        }

        Thread writer = new Thread(() -> {
            synchronized (documents) {
                first.title = "updated";
                documents.add(new Document("later"));
            }
        });
        writer.start();
        writer.join();

        System.out.println("sourceSize=" + documents.size());
        System.out.println("structuralSnapshotSize=" + structuralSnapshot.size());
        System.out.println("sharedDocumentTitle=" + structuralSnapshot.get(0).title);
        System.out.println("projectedTitle=" + projectedSnapshot.get(0).title());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SynchronizedSnapshotDemo.java
java SynchronizedSnapshotDemo
```

Stdout:

```text
sourceSize=2
structuralSnapshotSize=1
sharedDocumentTitle=updated
projectedTitle=draft
```

Chương trình chụp hai loại dữ liệu trước khi bắt đầu luồng ghi:

- `structuralSnapshot` sao chép cấu trúc danh sách nhưng vẫn tham chiếu tới cùng đối tượng `Document`.
- `projectedSnapshot` tạo các `DocumentView` có trường `String` chứa tiêu đề lúc chụp.

Sau khi luồng ghi đổi tiêu đề và thêm phần tử:

- Nguồn có hai phần tử.
- Bản sao cấu trúc vẫn có một phần tử.
- Tiêu đề của đối tượng dùng chung đọc qua bản sao đã là `updated`.
- Tiêu đề trong dữ liệu biểu diễn đầu ra vẫn là `draft`.

### Bảo vệ collection và bảo vệ trạng thái phần tử khác nhau

Lớp bao đồng bộ bảo vệ những thao tác truy cập qua nó. Một lời gọi sửa trường của đối tượng đã lấy ra không tự đi lại qua cơ chế khóa của collection.

Nếu chọn dùng khóa collection để bảo vệ cả trường tiêu đề, mọi bên đọc hoặc ghi trường đó khi có cạnh tranh cũng phải tuân thủ cùng chính sách. Chỉ một bên dùng `synchronized (documents)` còn bên khác tùy ý sửa trực tiếp thì không đủ.

Trong chương trình, luồng chính gọi `writer.join()` trước khi đọc tiêu đề đã sửa. Nó chờ bên ghi kết thúc; ví dụ không đưa ra một phép đọc không được phối hợp trong lúc trường đang bị ghi. Quan hệ hoàn tất giữa các luồng giúp kết quả quan sát có ý nghĩa; không thay `join` bằng một thời gian ngủ ước lượng. [Java Language Specification về quan hệ happens-before](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5)

Các trường của `DocumentView` trong ví dụ dùng `String` bất biến nên dữ liệu tiêu đề đã tách khỏi trường sửa được của `Document`. Nếu đối tượng đầu ra chứa tham chiếu đến cấu trúc thay đổi được khác, vẫn cần thiết kế cách sao chép hoặc bảo vệ phần đó.

### Tránh giữ khóa cho công việc không cần nguồn dùng chung

Việc gọi I/O, chờ tác vụ khác hoặc thực hiện tính toán dài trong vùng khóa có thể giữ các bên khác chờ theo. Nếu có thể xử lý một bản chụp, hãy chỉ giữ khóa cho giai đoạn lấy dữ liệu và duy trì bất biến cần thiết.

Khi xử lý song song, các luồng thực thi khác không tự sở hữu monitor mà luồng gọi đang giữ. Không giữ khóa nguồn rồi cho tác vụ con cần lại chính khóa đó trong khi luồng gọi chờ kết quả. Một bản chụp dữ liệu phù hợp thường giúp tách việc đọc nguồn dùng chung khỏi giai đoạn tính toán.

## 8. Phạm vi bảo đảm và chi phí của lớp bao đồng bộ

### Đồng bộ còn liên quan đến việc nhìn thấy dữ liệu

Sử dụng cùng monitor không chỉ ngăn hai vùng được bảo vệ chạy cùng lúc. Việc nhả monitor có quan hệ xảy ra trước được bảo đảm với lần lấy cùng monitor phía sau, qua đó cung cấp quy tắc nhìn thấy các ghi trước đó.

Tuy nhiên, quy tắc này cần một chính sách nhất quán. Một bên ghi ngoài khóa và một bên đọc trong khóa không tự tạo thành cặp phối hợp đúng chỉ vì bên đọc có từ khóa `synchronized`.

Các chương trình tạo dữ liệu trước khi bắt đầu luồng, dùng lớp bao hoặc vùng khóa cho phần truy cập cạnh tranh, và chờ `join` trước khi đánh giá kết quả cuối. Đây là những mốc phối hợp có ý nghĩa trong hợp đồng Java, không phải thời gian chờ phỏng đoán.

### Một monitor chung có thể trở thành điểm tranh chấp

Các thao tác trực tiếp trên một lớp bao gốc phối hợp qua cùng monitor, nên những bên truy cập phải chờ nhau theo phạm vi khóa đó. Cách tổ chức này dễ hiểu và cho phép gộp một bất biến gồm nhiều thao tác bằng khóa bên ngoài.

Đổi lại, nhiều bên cùng truy cập một cấu trúc có thể tranh chấp cùng khóa, kể cả khi đang xử lý những phần tử hoặc khóa nghiệp vụ khác nhau. Lớp bao không phải cam kết làm chương trình chạy song song nhanh hơn và không hứa lịch cấp khóa công bằng theo thứ tự yêu cầu.

Không rút ngắn vùng khóa tới mức bỏ mất một phần bất biến. Cũng không kéo dài nó tới những xử lý không còn cần nguồn dùng chung. Phạm vi đúng phải được xác định theo yêu cầu dữ liệu.

### Không thay bằng concurrent collection rồi giữ nguyên mọi giả định về khóa

`ConcurrentHashMap` được thiết kế cho việc đọc và cập nhật đồng thời theo chính sách riêng; các thao tác của nó không dùng monitor của đối tượng theo cùng hợp đồng lớp bao `synchronizedMap`. Vì vậy, một khối `synchronized (map)` không tự chặn mọi cập nhật thông thường của một `ConcurrentHashMap`. [Javadoc ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

Nếu cần các thao tác độc lập theo khóa có khả năng mở rộng tốt hơn, có thể xem xét API concurrent thích hợp. Nếu cần một trạng thái chung nhất quán gồm nhiều bước, phải thiết kế lại bất biến theo đúng loại cấu trúc và thao tác được chọn.

Tương tự, `CopyOnWriteArrayList` cung cấp cách duyệt ảnh chụp, nhưng các lần sửa có chi phí tạo mảng mới. Nó phù hợp với những tình huống có đặc điểm đọc và ghi tương ứng, không phải tên thay thế cho mọi synchronized list. [Javadoc CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)

Mục tiêu là chọn một mô hình truy cập rõ ràng: lớp bao với monitor chung, nguồn duyệt ảnh chụp hoặc cấu trúc concurrent với các thao tác phù hợp. Không trộn các giả định về chúng chỉ vì đều được gọi là an toàn đa luồng.

## 9. Thiết kế cách sử dụng và nhận ra lỗi phổ biến

Một lớp sở hữu dữ liệu nên giữ riêng tham chiếu lớp bao và cung cấp những thao tác có ý nghĩa. Nếu đưa collection ra để mọi bên tự sửa, rất khó giữ các bất biến ghép của lớp sở hữu.

Ví dụ, thay vì để nơi gọi tùy ý thực hiện `contains` rồi `add`, lớp sở hữu có thể cung cấp phương thức “thêm nếu chưa có” với vùng khóa đúng. Thay vì trả khung nhìn Map mà không nói cách khóa, có thể trả bản chụp dữ liệu khi đó là điều bên nhận cần.

Phạm vi bảo vệ nên thể hiện được trong mô tả và mã:

| Tình huống | Cách tổ chức |
| --- | --- |
| Một lời gọi `add`, `contains`, `get`, `put` trực tiếp | Gọi qua lớp bao được chia sẻ |
| Kiểm tra rồi cập nhật cùng cấu trúc | Giữ monitor của lớp bao cho toàn bộ quyết định |
| Lấy nhiều giá trị cần nhất quán với nhau | Đọc trong một vùng khóa đúng phạm vi |
| Duyệt List hoặc Set bằng iterator | Giữ khóa lớp bao gốc từ lúc tạo iterator tới khi duyệt xong |
| Duyệt khung nhìn của synchronized Map | Giữ khóa Map lớp bao gốc |
| Thực thi stream đọc dữ liệu dùng chung | Giữ khóa cho toàn bộ giai đoạn duyệt, hoặc xử lý bản chụp |
| Cần tính toán dài không đòi trạng thái mới nhất | Chụp dữ liệu hợp lệ dưới khóa rồi tính bên ngoài |
| Phần tử là đối tượng có thể sửa | Xác định thêm chính sách cho trạng thái đối tượng |
| Bất biến liên quan nhiều cấu trúc | Thiết kế vùng sở hữu và chính sách khóa bao trùm; không coi mỗi lớp bao riêng là giao dịch chung |

Những lỗi thường gặp:

| Cách viết hoặc cách hiểu | Vấn đề |
| --- | --- |
| Giữ `raw` và cập nhật nó ngoài lớp bao | Bỏ qua cơ chế bảo vệ đã chọn |
| Tạo hai lớp bao độc lập trên cùng `raw` | Hai monitor không tự phối hợp |
| Khóa trên collection nền thay vì lớp bao gốc | Không cùng monitor với những thao tác thông thường qua lớp bao |
| Cho rằng hai lời gọi riêng tự thành nguyên tử | Có thể có thao tác khác xen giữa |
| Khóa lúc tạo iterator rồi nhả trước vòng lặp | Phần duyệt thật không còn được bảo vệ |
| Dùng vòng `for-each` và quên nó dùng iterator | Cú pháp không tự thêm vùng khóa |
| Duyệt `map.keySet()` dưới `synchronized (keys)` | Phải khóa trên Map lớp bao gốc |
| Tạo stream dưới khóa rồi thực thi sau khi nhả | Không bảo vệ thời gian pipeline đọc nguồn |
| Giữ khóa rồi sửa cấu trúc qua đường không phù hợp với iterator | Khóa không thay thế hợp đồng của iterator |
| Xem không có ngoại lệ là bằng chứng an toàn | Lỗi phối hợp không phải lúc nào cũng được phát hiện bằng ngoại lệ |
| Trả một đối tượng phần tử rồi coi mọi setter của nó được bảo vệ | Lời gọi setter không tự lấy khóa collection |
| Chuyển sang ConcurrentHashMap nhưng vẫn dựa vào khóa monitor của wrapper | Hai loại cấu trúc có hợp đồng phối hợp khác nhau |
| Đọc nhiều collection sau từng lời gọi rồi xem là một trạng thái chung | Những lớp bao riêng không tạo giao dịch liên cấu trúc |
| Dùng `sleep` để đợi bên ghi thay vì phối hợp hoàn tất | Thời gian trôi qua không thay thế tín hiệu hoặc `join` |

Khi sửa một lỗi đa luồng, câu hỏi có ích là: trạng thái nào cần được bảo vệ, mọi bên đang dùng monitor nào và phạm vi thao tác nào phải được coi là một đơn vị? Chỉ thêm một lớp bao mà chưa trả lời ba câu hỏi đó thường mới giải quyết được một phần vấn đề.

## 10. Thực hành kiểm chứng đúng phạm vi khóa

### Bài 1: vì sao vẫn có hai X?

Một synchronized list được dùng bằng hai lời gọi `contains` rồi `add`. Mỗi phương thức đều đi qua lớp bao, nhưng kết quả vẫn có hai phần tử `X`. Có phải lớp bao đã hỏng tính an toàn bên trong không?

**Đối chiếu:**

Không. Lỗi là thuật toán gồm hai lời gọi chưa được gộp vào một vùng khóa. Hai luồng có thể cùng quan sát chưa có `X` rồi lần lượt thêm. `SynchronizedCheckThenActDemo` dùng latch để kiểm chứng đúng lịch này.

Đặt toàn bộ kiểm tra và thêm trong `synchronized (list)` nếu đó là bất biến cần giữ.

### Bài 2: Set và hành động đi kèm

Trong một synchronized set chỉ có các bên thêm, không có bên xóa, muốn biết chính lời gọi này có thêm một mã mới hay không. Có cần tự gọi `contains` rồi `add` không?

**Đối chiếu:**

Không cần. Dùng giá trị boolean trả về của `set.add(code)`: `true` cho biết lời gọi đã thêm phần tử chưa có, `false` cho biết phần tử đã có theo quy tắc của Set.

Nếu còn nhiều cập nhật khác phải diễn ra cùng việc thêm như một bất biến, cần thiết kế phạm vi bảo vệ chung cho chúng. Giá trị trả về của một lời gọi không tự gộp mọi hành động về sau thành một giao dịch.

### Bài 3: tăng bộ đếm trong Map

Hai luồng cùng đọc `hits=0` từ synchronized Map rồi mỗi luồng ghi giá trị đọc cộng 1. Giá trị cuối có thể là 1 không? Việc một lần `put` chờ lần `put` kia hoàn tất có giải quyết được không?

**Đối chiếu:**

Giá trị cuối có thể là 1. Cả hai đã tính từ cùng giá trị cũ trước khi ghi. Xếp lần lượt hai phép ghi không làm xuất hiện lại lần tăng đã mất.

Với cách dùng `get` rồi `put`, giữ monitor của Map lớp bao cho cả lần đọc, tính và ghi như chương trình bộ đếm.

### Bài 4: chọn đối tượng khóa khi duyệt Map

Có `Map<String,Integer> map = Collections.synchronizedMap(...)` và `Set<String> keys = map.keySet()`. Khi dùng iterator từ `keys`, nên khóa trên `keys` hay `map`?

**Đối chiếu:**

Khóa trên `map`, tức lớp bao gốc. Tạo iterator và giữ khóa tới khi duyệt xong. Khóa trên khung nhìn không cùng monitor với những thao tác thông thường của lớp bao Map.

### Bài 5: khối khóa đã kết thúc trước vòng lặp

Chương trình tạo iterator trong `synchronized (list)`, gán iterator ra biến ngoài rồi duyệt sau khi đã rời khối. Cách đó đã đáp ứng yêu cầu chưa?

**Đối chiếu:**

Chưa. Việc cần bảo vệ là toàn bộ quá trình duyệt, không chỉ bước tạo bộ duyệt. Giữ khóa trong vòng lặp, hoặc tạo bản chụp dữ liệu dưới khóa rồi duyệt bản chụp theo chính sách của nó.

### Bài 6: lớp bao thứ hai có cứu lớp bao thứ nhất không?

Một bên dùng `first = synchronizedList(raw)`, bên khác tự tạo `second = synchronizedList(raw)`. Cả hai đều không dùng trực tiếp `raw`. Điều kiện “đều có synchronized” đã đủ chưa?

**Đối chiếu:**

Chưa. Hai lớp bao độc lập không tự có cùng monitor. Cần chia sẻ cùng một lớp bao hoặc thiết kế một chính sách truy cập chung thật sự, không để mỗi bên tự chọn khóa riêng quanh cùng dữ liệu.

### Bài 7: bản sao đã chụp dưới khóa có đóng băng phần tử không?

Một `List<Document>` được sao chép bằng `new ArrayList<>(documents)` dưới đúng khóa. Sau đó một bên sửa tiêu đề của một `Document` dùng chung. Bản sao có bắt buộc giữ tiêu đề cũ không?

**Đối chiếu:**

Không. Nó độc lập về cấu trúc nhưng vẫn chứa tham chiếu tới đối tượng đó. Nếu cần giữ giá trị tiêu đề ở thời điểm chụp, tạo dữ liệu đầu ra phù hợp dưới chính sách bảo vệ, như `DocumentView` trong ví dụ.

Khi kiểm chứng việc đọc sau ghi ở những luồng khác nhau, dùng mốc phối hợp đúng; không suy ra tính nhìn thấy dữ liệu từ một thời gian ngủ.

### Bài 8: thay loại Map và giữ nguyên synchronized

Đổi một biến từ synchronized Map sang `ConcurrentHashMap`, nhưng vẫn dùng `synchronized (map)` để mong chặn mọi `put` từ các bên khác. Có thể giữ nguyên giả định đó không?

**Đối chiếu:**

Không. Quy tắc monitor trong bài gắn với lớp bao đồng bộ. Các thao tác thông thường của `ConcurrentHashMap` không phối hợp theo cùng giả định đó. Cần chọn thao tác và thiết kế bất biến theo hợp đồng của loại Map mới.

Một lời giải đúng phải giải thích được vì sao mọi bên truy cập trạng thái liên quan đều được cùng một chính sách bảo vệ, thay vì chỉ chỉ ra rằng trong mã có từ khóa `synchronized`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-004">Khóa đúng đối tượng khi duyệt các wrapper đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Khóa đúng đối tượng khi duyệt các wrapper đồng bộ

## 1. Một wrapper đồng bộ không giữ khóa thay cho cả vòng lặp

Các phương thức như `Collections.synchronizedList`, `synchronizedSet` và `synchronizedMap` tạo lớp bọc có cơ chế đồng bộ cho các thao tác được hỗ trợ. Nhưng một lượt duyệt qua Iterator, Spliterator hoặc Stream gồm nhiều bước và có thể kéo dài hơn một lời gọi phương thức.

Vì vậy, câu hỏi cần trả lời là:

> Toàn bộ lượt duyệt phải giữ monitor của đối tượng nào để phối hợp với những nơi đang sửa dữ liệu?

Đáp án không luôn là “đối tượng đang được for-each duyệt”. Khi duyệt `map.keySet()`, phải khóa Map bọc. Khi duyệt một vùng con như `headSet` hoặc `subMap` từ wrapper gốc, vẫn phải dùng khóa gốc được hợp đồng chỉ định.

Tài liệu các wrapper đồng bộ yêu cầu khóa thủ công đúng đối tượng khi duyệt và truy cập dữ liệu nền qua lớp bọc. [Java SE 17 — Collections synchronized wrappers](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedCollection(java.util.Collection)).

Mô hình của bài:

~~~text
nhiều đường nhìn dữ liệu
        |
        v
một nguồn nền và một giao thức khóa chung
        |
        v
giữ đúng khóa trong toàn bộ thời gian duyệt
~~~

Bài sẽ đi từ List/Set đơn giản tới Map và các vùng con, rồi chỉ ra các trường hợp khóa nhầm: khóa Iterator, khóa khung nhìn, khóa dữ liệu nền, tạo hai wrapper độc lập hoặc khóa một lớp bọc chỉ đọc bên ngoài.

## 2. Monitor thuộc về đối tượng, không thuộc về tên biến hay equals

`synchronized (object)` lấy monitor gắn với chính đối tượng mà biểu thức đó tham chiếu. Hai đối tượng khác nhau có monitor khác nhau, dù:

- Chúng có cùng lớp triển khai.
- Chúng biểu diễn nội dung bằng nhau theo equals.
- Chúng dùng chung một collection nền.
- Một đối tượng là khung nhìn được tạo từ đối tượng kia.

Java cho phép cùng một luồng lấy lại monitor nó đang giữ; đây là khả năng tái nhập. Điều này giúp mã giữ khóa wrapper gọi những phương thức wrapper dùng cùng monitor mà không tự chặn chính nó. [Java Language Specification 17 — Synchronization](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.1).

Nhưng khóa một đối tượng con không có nghĩa tự động khóa đối tượng cha:

~~~text
synchronized(view)
    chỉ giữ monitor của view

synchronized(root)
    chỉ giữ monitor của root
~~~

Một phương thức của `view` có thể chủ động dùng khóa gốc bên trong cách cài đặt. Điều đó không làm monitor của chính `view` trở thành cùng monitor với gốc, và không khiến một khối synchronized(view) giữ khóa gốc xuyên suốt vòng duyệt.

Đây là khác biệt giữa **đối tượng được gọi phương thức** và **đối tượng làm khóa cho giao thức truy cập**.

## 3. Bảng chọn khóa cho những trường hợp thường gặp

Giả định các wrapper gốc được tạo bằng các factory công khai tương ứng của `Collections`:

| Đang duyệt | Đối tượng cần khóa để phối hợp với wrapper |
| --- | --- |
| Một synchronizedCollection gốc | Collection bọc được factory trả về |
| Một synchronizedList hoặc synchronizedSet gốc | List/Set bọc gốc |
| subList lấy từ synchronizedList | List bọc gốc chia sẻ khóa, không chỉ subList |
| keySet, values, entrySet của synchronizedMap | Map bọc gốc |
| subSet, headSet, tailSet của synchronizedSortedSet | SortedSet bọc gốc |
| Vùng con hoặc khung nhìn của synchronizedSortedMap | SortedMap bọc gốc |
| Khung nhìn điều hướng của wrapper navigable tương ứng | Wrapper gốc theo hợp đồng của nó |

“Gốc” ở đây là đối tượng sở hữu giao thức khóa cho nhóm khung nhìn đang dùng, không phải lúc nào cũng là collection thô được đưa vào factory.

Ví dụ:

~~~java
List<String> list = Collections.synchronizedList(new ArrayList<>());
~~~

Khóa công khai cần dùng cho lượt duyệt là `list`. Không cần và không nên tìm một ArrayList bên trong để khóa thay.

Nếu API chỉ đưa cho ta một khung nhìn mà không cung cấp thông tin về giao thức khóa, không thể tự suy ra khóa gốc bằng cách nhìn kiểu giao diện `Collection`. Thiết kế API cần giữ quyền duyệt ở nơi biết gốc, hoặc công bố snapshot hay một thao tác quản lý việc duyệt.

## 4. List và Set: tạo bộ duyệt và dùng nó trong cùng vùng khóa

Ví dụ với List:

~~~java
List<String> list = Collections.synchronizedList(
        new ArrayList<>(List.of("A", "B", "C")));

synchronized (list) {
    Iterator<String> iterator = list.iterator();
    while (iterator.hasNext()) {
        consume(iterator.next());
    }
}
~~~

Đây là đoạn minh họa bên trong một phương thức, với `consume` do ứng dụng định nghĩa. Phần chương trình đầy đủ phía sau không phụ thuộc hàm còn thiếu và có lệnh chạy cụ thể.

Hai điểm đều quan trọng:

1. Tạo Iterator bên trong vùng khóa.
2. Giữ khóa cho tới khi hoàn tất việc dùng Iterator.

Tài liệu synchronizedList chỉ rõ yêu cầu này cho Iterator, Spliterator và Stream. [Java SE 17 — Collections.synchronizedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedList(java.util.List)).

### 4.1. Chỉ khóa lúc tạo Iterator là chưa đủ

Mẫu sau sai giao thức:

~~~java
Iterator<String> iterator;
synchronized (list) {
    iterator = list.iterator();
}

// Sai nếu nguồn có thể bị các bên khác sửa trong lúc duyệt.
while (iterator.hasNext()) {
    consume(iterator.next());
}
~~~

Ra khỏi khối, các luồng khác có thể lấy cùng khóa để sửa nguồn. Iterator không tự giữ monitor của List trong suốt thời gian tồn tại của nó.

### 4.2. Khóa từng bước riêng không giữ được một lượt duyệt nhất quán

Nếu mỗi lần hasNext/next được đặt trong một khối khóa tách rời, vẫn có khoảng trống cho nguồn đổi giữa các bước hoặc giữa các phần tử. Với Iterator thường của collection nền, điều đó không đáp ứng mẫu bảo vệ toàn bộ lượt duyệt mà wrapper yêu cầu.

Hãy xác định ranh giới công việc: nếu nhiệm vụ cần một lượt nhìn ổn định, khóa cả lượt hoặc tạo một snapshot phù hợp.

### 4.3. foreach vẫn là duyệt

~~~java
synchronized (list) {
    for (String value : list) {
        consume(value);
    }
}
~~~

Cú pháp gọn hơn không thay đổi đối tượng khóa. Nếu `for` dùng một khung nhìn con, phải xem khóa của khung nhìn ấy thay vì máy móc khóa chính biến đang đứng sau dấu hai chấm.

Set bọc đồng bộ gốc được xử lý tương tự, chỉ khác hợp đồng phần tử và thứ tự của Set nền. Khóa đúng không làm HashSet có thứ tự hoặc tự sắp xếp dữ liệu.

## 5. Map: khóa Map bọc, không khóa keySet, values hoặc entrySet

Xét:

~~~java
Map<String, Integer> map =
        Collections.synchronizedMap(new LinkedHashMap<>());

Set<String> keys = map.keySet();
~~~

Khung nhìn `keys` không phải một bản sao độc lập. Nó dùng chung Map nền. Khi duyệt, khóa đúng là `map`:

~~~java
synchronized (map) {
    for (String key : keys) {
        consume(key);
    }
}
~~~

Không thay thành `synchronized (keys)` rồi cho rằng các lời gọi `map.put` từ luồng khác sẽ bị chặn bởi khóa đó. Chúng sử dụng giao thức khóa của Map, không phải monitor riêng của đối tượng keySet.

Tài liệu synchronizedMap yêu cầu khóa trên Map trả về khi duyệt mọi khung nhìn collection của nó, gồm keySet, values và entrySet. [Java SE 17 — Collections.synchronizedMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map)).

### 5.1. Có thể lấy khung nhìn trước, nhưng bộ duyệt phải được dùng dưới khóa đúng

Lấy `map.keySet()` trước khối khóa như ví dụ là cách sử dụng được tài liệu minh họa. Khi bắt đầu Iterator hoặc lượt Stream trên khung nhìn, dùng vùng khóa bảo vệ trọn quá trình tương ứng.

Không đưa một Iterator đang sống ra khỏi khối rồi trả cho bên gọi sử dụng như thể nó đã được “khóa sẵn”.

### 5.2. Entry là phần tử có hợp đồng riêng

Khi duyệt entrySet dưới khóa, có thể đọc khóa và giá trị để tạo dữ liệu đầu ra. Nếu muốn giữ các cặp để xử lý lâu hơn sau khi nhả khóa, nên tạo các entry hoặc đối tượng dữ liệu độc lập trong lúc đang duyệt.

Chỉ chép `new ArrayList<>(map.entrySet())` không tự nhân bản từng entry thành cặp độc lập. Khóa bảo vệ lượt chép không thay đổi hợp đồng thời gian sống và quan hệ của các entry lấy từ khung nhìn.

Chương trình minh chứng tạo chuỗi biểu diễn khóa–giá trị bên trong vùng khóa rồi mới in các chuỗi ấy bên ngoài. Nó không giữ các entry sống từ nguồn để tiếp tục dùng sau khi nguồn có thể đổi.

### 5.3. Khóa khung nhìn có thể tạo ấn tượng sai

Một phương thức đơn lẻ của keySet có thể vẫn đồng bộ đúng bên trong vì khung nhìn chia sẻ khóa gốc với Map. Điều đó không hợp thức hóa việc giữ `synchronized(keys)` quanh Iterator.

Trong OpenJDK 17, các khung nhìn keySet, values và entrySet của wrapper được tạo với cùng mutex của wrapper Map. Đây là cơ chế giải thích vì sao các phương thức dùng chung giao thức, trong khi monitor của chính đối tượng khung nhìn vẫn là một đối tượng khác. [OpenJDK 17 — Collections.java](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/Collections.java).

## 6. Các vùng con tiếp tục dùng khóa gốc

### 6.1. subList của synchronizedList

~~~java
List<String> list = Collections.synchronizedList(
        new ArrayList<>(List.of("A", "B", "C")));
List<String> tail = list.subList(1, 3);

synchronized (list) {
    for (String value : tail) {
        consume(value);
    }
}
~~~

`tail` là một đối tượng khung nhìn khác, nhưng các thao tác của wrapper con chia sẻ khóa với List gốc. Do đó, giữ monitor của `tail` không thay thế giữ monitor của `list` cho lượt duyệt.

Mã nguồn OpenJDK cho thấy subList của synchronizedList truyền tiếp mutex hiện có cho wrapper con; nó không tạo một giao thức độc lập mới cho cùng vùng dữ liệu.

Ngoài vấn đề khóa, subList còn có hợp đồng về thay đổi cấu trúc của List nền. Nếu cấu trúc cha bị đổi bên ngoài khung nhìn theo cách làm subList không còn hợp lệ, chỉ lấy đúng khóa sau đó không làm khung nhìn tự hợp lệ trở lại. Khóa giải quyết truy cập chen ngang, không thay thế quy tắc hợp lệ của cấu trúc.

### 6.2. subSet, headSet và tailSet

Với SortedSet:

~~~java
SortedSet<Integer> sorted = Collections.synchronizedSortedSet(
        new TreeSet<>(List.of(1, 2, 3, 4)));
SortedSet<Integer> head = sorted.headSet(4);

synchronized (sorted) {
    for (Integer value : head) {
        consume(value);
    }
}
~~~

`headSet(4)` ở đây lấy những phần tử nhỏ hơn `4`. Đối tượng khóa là `sorted`, không phải `head`. Tài liệu SortedSet bọc đồng bộ nêu rõ phải khóa tập gốc cả khi duyệt các vùng con. [Java SE 17 — Collections.synchronizedSortedSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedSortedSet(java.util.SortedSet)).

Với NavigableSet, những khung nhìn điều hướng và vùng có tham số bao gồm/không bao gồm cũng phải tuân thủ khóa gốc theo hợp đồng wrapper tương ứng. Đừng suy ra rằng thêm tham số ranh giới tạo một khóa mới độc lập.

### 6.3. subMap, headMap, tailMap và các khung nhìn tiếp theo

Với SortedMap:

~~~java
SortedMap<String, Integer> root =
        Collections.synchronizedSortedMap(new TreeMap<>());
SortedMap<String, Integer> range = root.subMap("A", "C");

synchronized (root) {
    for (String key : range.keySet()) {
        consume(key);
    }
}
~~~

Cần khóa `root`, không khóa `range` và cũng không khóa `range.keySet()`.

Khung nhìn có thể đi qua nhiều tầng nhưng giao thức không được suy ra chỉ từ tầng ngoài cùng. Tài liệu synchronizedSortedMap và synchronizedNavigableMap chỉ định Map gốc cho việc duyệt các khung nhìn vùng con ấy. [Java SE 17 — Collections.synchronizedSortedMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedSortedMap(java.util.SortedMap)).

Một cách nhớ chính xác là theo dõi **ai sở hữu khóa chung** ngay từ lúc tạo wrapper, rồi giữ thông tin ấy cùng với những khung nhìn được tạo ra.

## 7. Spliterator và Stream phải sống trong phạm vi bảo vệ phù hợp

### 7.1. Spliterator không giữ khóa của wrapper suốt đời

~~~java
synchronized (list) {
    Spliterator<String> source = list.spliterator();
    source.forEachRemaining(value -> consume(value));
}
~~~

Đây là một lượt duyệt tuần tự dưới khóa List. Không lấy Spliterator ra ngoài khối rồi cho rằng lớp bọc đã biến nó thành một ảnh chụp hoặc bộ duyệt tự khóa toàn bộ.

Sau `trySplit`, các phần chia vẫn đọc nguồn nền. Phân chia không tự tạo nhiều snapshot độc lập với những lần ghi vào nguồn.

### 7.2. Stream lười nên phải chú ý nơi terminal chạy

Mẫu sai về phạm vi:

~~~java
Stream<String> plan;
synchronized (list) {
    plan = list.stream().filter(value -> !value.isBlank());
}

// Lượt duyệt thực sự nằm ngoài khóa.
List<String> result = plan.toList();
~~~

Tạo pipeline trong vùng khóa không có nghĩa việc đọc phần tử đã hoàn tất. Với nguồn có thể được các bên khác sửa, cần bảo vệ cả thời gian thực hiện terminal theo hợp đồng.

Mẫu tuần tự phù hợp:

~~~java
List<String> result;
synchronized (list) {
    result = list.stream()
            .filter(value -> !value.isBlank())
            .toList();
}
~~~

Đoạn này giả định phần tử khác null và kết quả theo hợp đồng của toList phù hợp nhu cầu.

### 7.3. Không dùng khóa ngoài như lời giải tự động cho parallelStream

Một pipeline song song có thể đưa hành động sang các luồng khác. Nếu luồng gọi giữ khóa gốc và đợi các tác vụ hoàn thành, còn tác vụ lại cố lấy chính khóa đó, chương trình có thể không tiến triển.

Ngay cả khi callback không lấy khóa, vẫn cần hiểu chính sách nguồn, phạm vi bảo vệ và chi phí giữ khóa. Khi muốn xử lý song song hoặc làm việc lâu, cách dễ kiểm soát hơn thường là tạo snapshot hợp lệ dưới khóa rồi xử lý snapshot sau khi nhả.

Đây không phải kết luận mọi parallelStream đều bị cấm. Nó là yêu cầu thiết kế quyền sở hữu và thời gian giữ khóa thay vì bọc synchronized rồi bỏ qua hành vi của các tác vụ.

## 8. Những đối tượng khóa thường bị chọn nhầm

### 8.1. Khóa collection nền thay cho wrapper

~~~java
List<String> backing = new ArrayList<>();
List<String> safe = Collections.synchronizedList(backing);
~~~

Các phương thức của wrapper dùng giao thức của `safe`. Chỉ giữ `synchronized(backing)` không buộc một luồng gọi `safe.add` phải chờ khóa ấy.

Cách tránh lỗi là tạo wrapper và giữ nó như điểm truy cập duy nhất. Đừng để nhiều nơi còn sửa trực tiếp backing bên ngoài giao thức. Nếu một thiết kế riêng dùng khóa khác, mọi nơi phải được thiết kế đồng nhất; không trộn nó vô tình với mutex của wrapper chuẩn.

### 8.2. Khóa Iterator

Iterator là một đối tượng duyệt, không phải đối tượng khóa mà các phương thức của wrapper đang dùng. `synchronized(iterator)` không làm những người gọi `list.add` phối hợp với vòng lặp ấy.

Tương tự, khóa Stream hoặc Spliterator chỉ khóa đối tượng đó; nó không tự khóa nguồn.

### 8.3. Hai wrapper riêng trên cùng backing

~~~java
List<String> first = Collections.synchronizedList(backing);
List<String> second = Collections.synchronizedList(backing);
~~~

Hai lời gọi factory độc lập không tự nhận ra cùng backing rồi thống nhất một monitor. Một bên khóa `first` không ngăn bên khác đi qua `second` theo monitor khác.

Nếu hai bên cùng sửa backing theo hai giao thức độc lập, việc cả hai đều có tên “synchronizedList” không cứu được thiết kế.

Trong chương trình, ca này chỉ thử khả năng lấy hai monitor khác nhau; không thực hiện các lượt ghi xung đột lên backing. Nó phục vụ minh chứng danh tính khóa, không phải mẫu dùng hai wrapper để bảo vệ cùng nguồn.

### 8.4. Khóa lớp bọc chỉ đọc bên ngoài

~~~java
List<String> readOnly = Collections.unmodifiableList(safe);
~~~

`readOnly` cấm sửa qua giao diện đó, nhưng monitor của `readOnly` không tự là monitor của `safe`. Giữ synchronized(readOnly) không làm những nơi đang dùng safe tuân theo khóa ấy.

Nếu API chỉ cung cấp readOnly cho người gọi, nên cân nhắc cung cấp snapshot hoặc phương thức đọc được quản lý từ bên sở hữu. Không yêu cầu người gọi đoán xem bên trong có bao nhiêu lớp bọc và khóa nào mới đúng.

## 9. Một API đọc nên giữ quyền khóa ở nơi biết nguồn

Nếu một hàm chỉ nhận `Collection<T> view` rồi viết `synchronized(view)`, nó chưa có đủ căn cứ để bảo đảm đúng cho mọi khung nhìn đồng bộ có thể được truyền vào.

Hai hướng thiết kế rõ hơn:

| Hướng | Trách nhiệm |
| --- | --- |
| Bên sở hữu dữ liệu thực hiện lượt đọc | Nó biết wrapper gốc và giữ khóa đúng |
| Bên sở hữu trả snapshot độc lập | Nó tạo snapshot trong giao thức hợp lệ rồi để bên gọi đọc ngoài khóa |

Có thể truyền thêm một đối tượng khóa đã được thỏa thuận trong một API nội bộ, nhưng điều đó phải là hợp đồng rõ ràng. Truyền một Object bất kỳ không tự tạo quan hệ với các phương thức đang sửa nguồn.

Cũng tránh trả một Iterator sống từ trong một phương thức synchronized:

~~~java
// Minh họa thiết kế không giữ khóa cho thời gian bên gọi sử dụng Iterator.
Iterator<String> iteratorForCaller() {
    synchronized (list) {
        return list.iterator();
    }
}
~~~

Monitor đã nhả khi phương thức trả về. Nếu cần cung cấp dữ liệu để bên gọi duyệt độc lập, trả một bản sao phù hợp thường dễ biểu đạt đúng hơn.

Mục tiêu không phải che giấu khóa đến mức người dùng không biết dữ liệu có nhất quán hay không. Mục tiêu là đặt việc thực hiện giao thức ở nơi có đủ thông tin và giữ phạm vi trách nhiệm rõ ràng.

## 10. Tạo snapshot dưới khóa rồi làm việc ngoài khóa

Với danh sách tên khác null:

~~~java
List<String> snapshot;
synchronized (list) {
    snapshot = List.copyOf(list);
}

process(snapshot);
~~~

`process` là hàm do ứng dụng cung cấp; đoạn này minh họa cách tách thời gian giữ khóa khỏi xử lý có thể lâu. Snapshot không phản ánh việc thêm/xóa vị trí sau đó ở List nguồn.

Nếu nguồn có null hợp lệ, chọn một phép sao chép chấp nhận null và quyền sửa kết quả phù hợp thay vì mặc định dùng List.copyOf. Bản sao cấu trúc cũng không tự nhân bản những đối tượng phần tử sửa được.

Với Map, nếu cần snapshot cặp để dùng sau, tạo các cặp độc lập ngay trong vùng khóa. Nếu dùng SimpleImmutableEntry, tham chiếu khóa/giá trị được giữ trong cặp mới; trạng thái bên trong đối tượng khóa/giá trị vẫn cần chính sách riêng.

Một snapshot chỉ có ý nghĩa khi quá trình tạo nó đã đọc nguồn hợp lệ. Tạo bản sao ngoài khóa từ một wrapper đang có các bên sửa không giải quyết được yêu cầu duyệt ổn định chỉ bằng tên “copy”.

Bên đọc còn cần biết dữ liệu snapshot có thể cũ. Nếu nghiệp vụ phải kiểm tra điều kiện rồi cập nhật dựa trên trạng thái hiện tại, không được thay phần giao dịch ấy bằng snapshot mà bỏ bước xác nhận/cập nhật nguyên tử cần có.

## 11. Khóa đúng chưa thay thế hợp đồng Iterator và chính sách callback

Giữ khóa ngăn các bên tuân thủ cùng khóa chen vào, nhưng chính luồng đang duyệt vẫn có thể làm một thao tác không hợp lệ.

Ví dụ, gọi add trực tiếp lên nguồn trong khi Iterator đang chạy có thể phá hợp đồng của Iterator, dù cùng luồng đang giữ khóa và có thể tái nhập monitor. Tái nhập không biến một sửa đổi cấu trúc tùy ý thành thao tác Iterator hợp lệ.

Nếu cần xóa trong một lượt duyệt được hỗ trợ, dùng đúng thao tác Iterator hoặc một phương thức sửa phù hợp với cấu trúc và yêu cầu. Đừng dùng một lần không gặp ConcurrentModificationException để coi mọi thay đổi đều được chấp nhận.

Callback trong vùng khóa cũng cần được kiểm soát. Một callback làm I/O chậm, giữ một khóa khác hoặc chờ công việc đang cần khóa gốc có thể làm tăng thời gian chặn hoặc tạo deadlock.

Nếu cần nhiều khóa cho nhiều nguồn, có một thứ tự lấy khóa nhất quán trong thiết kế. Không khóa List trước Map ở nơi này rồi khóa Map trước List ở nơi khác mà không xem khả năng chờ vòng.

Đối với `list.forEach(action)` trực tiếp, cách cài đặt wrapper có thể đồng bộ toàn phương thức; đó không phải cùng lời gọi với `list.iterator().forEachRemaining(action)`. Không lấy việc một phương thức tiện ích có khóa nội bộ để suy ra mọi bộ duyệt được lấy ra đều giữ khóa suốt thời gian dùng.

## 12. Kiểm tra monitor bằng Thread.holdsLock

`Thread.holdsLock(object)` cho biết **luồng hiện tại** có giữ monitor của đối tượng đó hay không. Nó không lấy khóa, không chờ khóa và không kiểm tra xem một luồng khác có giữ khóa hay không. [Java SE 17 — Thread.holdsLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#holdsLock(java.lang.Object)).

Trong ví dụ:

~~~java
synchronized (list) {
    System.out.println(Thread.holdsLock(list)); // true
    System.out.println(Thread.holdsLock(tail)); // false nếu tail là đối tượng khác
}
~~~

Hai kết quả không mâu thuẫn với việc các phương thức tail dùng chung mutex gốc. Lần gọi holdsLock(tail) chỉ hỏi monitor của chính tail.

Phương thức này hữu ích để kiểm tra tiền điều kiện của helper nội bộ hoặc để học danh tính khóa. Nó không tự chứng minh toàn bộ hệ thống an toàn; vẫn phải kiểm tra mọi nơi sửa dữ liệu và thời gian sống của bộ duyệt.

Chương trình còn dùng một luồng thăm dò và latch: trong khi luồng gọi đang giữ monitor của khung nhìn, luồng thăm dò thử lấy monitor gốc. Nếu lấy được và báo qua latch trước khi luồng gọi nhả khung nhìn, điều đó chứng minh hai monitor không loại trừ nhau.

Latch chỉ phối hợp phép thử; nó không tham gia một thuật toán duyệt nguồn sai. [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html).

## 13. Chương trình minh chứng đầy đủ

Lưu mã sau thành **WrapperTraversalLockDemo.java**. Chương trình dùng API tương thích Java 17, không cần thư viện ngoài.

Các lượt duyệt thực sự đều giữ đúng khóa gốc. Phép thử khóa sai chỉ thăm dò monitor và không duyệt/ghi dữ liệu xung đột qua một khung nhìn không được bảo vệ. Không có yêu cầu tái lập một lỗi ngẫu nhiên từ data race.

Sau khi thêm D vào List gốc, chương trình không tiếp tục dùng subList cũ. Các entry từ Map được chuyển thành chuỗi ngay trong khóa, nên dữ liệu được in sau đó không giữ entry sống của khung nhìn.

~~~java
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class WrapperTraversalLockDemo {
    private static boolean rootLockAvailableWhileViewHeld(Object root, Object view)
            throws InterruptedException {
        CountDownLatch enteredRoot = new CountDownLatch(1);
        Thread probe = new Thread(() -> {
            synchronized (root) {
                enteredRoot.countDown();
            }
        });
        boolean entered;
        synchronized (view) {
            probe.start();
            entered = enteredRoot.await(5, TimeUnit.SECONDS);
        }
        probe.join(5000);
        if (probe.isAlive()) {
            probe.interrupt();
            throw new AssertionError("Probe did not finish");
        }
        return entered;
    }

    public static void main(String[] args) throws Exception {
        List<String> list = Collections.synchronizedList(
                new ArrayList<>(List.of("A", "B", "C")));
        List<String> visited = new ArrayList<>();
        synchronized (list) {
            Iterator<String> iterator = list.iterator();
            while (iterator.hasNext()) {
                visited.add(iterator.next());
            }
            System.out.println("list-root-held=" + Thread.holdsLock(list));
        }
        System.out.println("list-values=" + visited);

        List<String> tail = list.subList(1, 3);
        List<String> tailValues = new ArrayList<>();
        synchronized (list) {
            for (String value : tail) {
                tailValues.add(value);
            }
            System.out.println("sublist-root-held=" + Thread.holdsLock(list));
            System.out.println("sublist-view-held=" + Thread.holdsLock(tail));
        }
        System.out.println("sublist-values=" + tailValues);
        synchronized (tail) {
            System.out.println("view-only-root-held=" + Thread.holdsLock(list));
        }
        System.out.println("root-available-under-sublist-lock="
                + rootLockAvailableWhileViewHeld(list, tail));

        String joined;
        synchronized (list) {
            joined = list.stream().collect(Collectors.joining("|"));
        }
        System.out.println("stream-under-root=" + joined);

        Map<String, Integer> map = Collections.synchronizedMap(new LinkedHashMap<>());
        map.put("B", 2);
        map.put("A", 1);
        Set<String> keys = map.keySet();
        Collection<Integer> values = map.values();
        Set<Map.Entry<String, Integer>> entries = map.entrySet();
        List<String> keyValues = new ArrayList<>();
        List<Integer> mappedValues = new ArrayList<>();
        List<String> entryValues = new ArrayList<>();
        synchronized (map) {
            for (String key : keys) {
                keyValues.add(key);
            }
            for (Integer value : values) {
                mappedValues.add(value);
            }
            for (Map.Entry<String, Integer> entry : entries) {
                entryValues.add(entry.getKey() + "=" + entry.getValue());
            }
            System.out.println("map-root-held=" + Thread.holdsLock(map));
            System.out.println("map-key-view-held=" + Thread.holdsLock(keys));
        }
        System.out.println("map-keys=" + keyValues);
        System.out.println("map-values=" + mappedValues);
        System.out.println("map-entries=" + entryValues);
        System.out.println("map-root-available-under-key-lock="
                + rootLockAvailableWhileViewHeld(map, keys));

        SortedSet<Integer> sorted = Collections.synchronizedSortedSet(
                new TreeSet<>(List.of(1, 2, 3, 4)));
        SortedSet<Integer> head = sorted.headSet(4);
        List<Integer> headValues = new ArrayList<>();
        synchronized (sorted) {
            for (Integer value : head) {
                headValues.add(value);
            }
            System.out.println("set-root-held=" + Thread.holdsLock(sorted));
            System.out.println("set-range-held=" + Thread.holdsLock(head));
        }
        System.out.println("set-range-values=" + headValues);

        SortedMap<String, Integer> sortedMap =
                Collections.synchronizedSortedMap(new TreeMap<>());
        sortedMap.put("A", 1);
        sortedMap.put("B", 2);
        sortedMap.put("C", 3);
        SortedMap<String, Integer> range = sortedMap.subMap("A", "C");
        List<String> rangeKeys = new ArrayList<>();
        synchronized (sortedMap) {
            for (String key : range.keySet()) {
                rangeKeys.add(key);
            }
            System.out.println("sorted-map-root-held=" + Thread.holdsLock(sortedMap));
            System.out.println("sorted-map-range-held=" + Thread.holdsLock(range));
        }
        System.out.println("sorted-map-range-keys=" + rangeKeys);

        List<String> snapshot;
        synchronized (list) {
            snapshot = List.copyOf(list);
        }
        list.add("D");
        System.out.println("snapshot=" + snapshot);
        System.out.println("current-list=" + list);

        List<String> sharedBacking = new ArrayList<>(List.of("X"));
        List<String> firstWrapper = Collections.synchronizedList(sharedBacking);
        List<String> secondWrapper = Collections.synchronizedList(sharedBacking);
        System.out.println("independent-wrapper-locks="
                + rootLockAvailableWhileViewHeld(firstWrapper, secondWrapper));

        List<String> readOnly = Collections.unmodifiableList(list);
        System.out.println("outer-read-only-lock-is-different="
                + rootLockAvailableWhileViewHeld(list, readOnly));
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 WrapperTraversalLockDemo.java
java WrapperTraversalLockDemo
~~~

Stdout chính xác:

~~~text
list-root-held=true
list-values=[A, B, C]
sublist-root-held=true
sublist-view-held=false
sublist-values=[B, C]
view-only-root-held=false
root-available-under-sublist-lock=true
stream-under-root=A|B|C
map-root-held=true
map-key-view-held=false
map-keys=[B, A]
map-values=[2, 1]
map-entries=[B=2, A=1]
map-root-available-under-key-lock=true
set-root-held=true
set-range-held=false
set-range-values=[1, 2, 3]
sorted-map-root-held=true
sorted-map-range-held=false
sorted-map-range-keys=[A, B]
snapshot=[A, B, C]
current-list=[A, B, C, D]
independent-wrapper-locks=true
outer-read-only-lock-is-different=true
~~~

Mã đã biên dịch bằng `--release 17` và chạy trên JDK 24.0.2. Cờ biên dịch xác minh mức ngôn ngữ/API Java 17; JVM chạy được nêu rõ, không phải một lần chạy bằng JVM 17.

Cách đọc kết quả:

- `list-root-held` xác nhận vùng duyệt List giữ monitor gốc.
- `sublist-root-held` và `sublist-view-held` phân biệt gốc với đối tượng subList.
- `view-only-root-held` cho thấy chỉ khóa subList không làm luồng hiện tại giữ khóa gốc.
- Các dòng `root-available` cho thấy một luồng khác có thể lấy gốc khi ta chỉ giữ monitor của khung nhìn.
- Các dữ liệu `map-keys`, `map-values`, `map-entries` được thu trong một vùng khóa Map.
- Các vùng SortedSet và SortedMap được duyệt dưới khóa wrapper gốc, không dưới monitor riêng của vùng.
- `snapshot` không đổi sau khi nguồn thêm D.
- Hai dòng cuối kiểm tra monitor khác nhau của hai wrapper độc lập và một lớp bọc chỉ đọc bên ngoài.

Thứ tự in do luồng main thực hiện. Luồng thăm dò chỉ báo qua latch rồi kết thúc; chương trình không dùng sleep để đoán lịch chạy.

## 14. Thực hành có đáp án

### Bài 1 — List gốc và Iterator

Một helper khóa synchronizedList, tạo Iterator rồi nhả khóa và trả Iterator ra ngoài. Bên gọi có được coi Iterator ấy đã được bảo vệ cho cả lượt duyệt không?

**Đáp án:** Không. Monitor đã được nhả khi helper trả về. Cần giữ đúng khóa suốt thời gian sử dụng bộ duyệt hoặc trả một snapshot để bên gọi duyệt độc lập.

### Bài 2 — Khóa keySet

Map được tạo bằng synchronizedMap. Vì sao synchronized(map.keySet()) không thay thế synchronized(map) cho một vòng lặp trên khóa?

**Đáp án:** Các thao tác của wrapper phối hợp qua mutex gốc của Map. Monitor của đối tượng keySet là đối tượng khác. Khóa keySet không làm những bên gọi map.put phải đợi cùng monitor đó.

### Bài 3 — subList có tên synchronized trong cách cài đặt

subList của synchronizedList vẫn là một wrapper đồng bộ. Vì sao khóa chính subList quanh Iterator chưa đúng?

**Đáp án:** Wrapper con chia sẻ mutex của gốc cho các phương thức, không dùng monitor riêng của nó làm khóa độc lập cho cả lượt duyệt. Phải theo dõi và giữ wrapper gốc sở hữu giao thức khóa chung.

### Bài 4 — Vùng con của SortedMap

Có root là synchronizedSortedMap, range là root.subMap(...) và keys là range.keySet(). Khi duyệt keys, khóa đối tượng nào?

**Đáp án:** Khóa root. Không chọn range hoặc keys chỉ vì chúng gần vòng lặp hơn. Các tầng khung nhìn tiếp tục dùng giao thức gốc theo hợp đồng wrapper.

### Bài 5 — Tạo Stream trong khóa

Pipeline được tạo dưới synchronized(list) nhưng toList chạy sau khi ra ngoài khối. Phần tử có được bảo vệ cho lượt duyệt không?

**Đáp án:** Không chỉ nhờ đoạn tạo pipeline. Stream lười nên terminal là nơi lượt đọc thực sự diễn ra. Giữ khóa phù hợp trong thời gian terminal tuần tự hoặc tạo snapshot hợp lệ rồi xử lý ngoài khóa.

### Bài 6 — Hai wrapper cùng nền

Hai module tự gọi synchronizedList trên cùng một ArrayList rồi mỗi nơi khóa wrapper của mình. Có đủ phối hợp không?

**Đáp án:** Không. Hai wrapper độc lập có monitor độc lập. Cần một điểm truy cập và giao thức khóa chung; không tạo nhiều lớp bọc riêng rồi giả định chúng tự thống nhất khóa.

### Bài 7 — holdsLock có lấy khóa không?

`Thread.holdsLock(root)` trả false. Có nghĩa root đang do một luồng khác giữ không? Lời gọi có bảo vệ mã tiếp theo không?

**Đáp án:** Không cho biết điều đó. Nó chỉ báo luồng hiện tại không giữ monitor root và không thực hiện lấy khóa. Muốn dùng giao thức phải lấy khóa bằng cơ chế phù hợp; holdsLock chỉ là phép quan sát hoặc kiểm tra tiền điều kiện.

### Bài 8 — Giữ khóa nhưng callback tự thêm phần tử

Một vòng Iterator đã giữ đúng khóa wrapper. Callback gọi add trực tiếp lên nguồn trong cùng luồng. Vì sao vẫn có thể không hợp lệ?

**Đáp án:** Khóa ngăn các bên khác chen vào nhưng không thay hợp đồng Iterator. Sửa cấu trúc trực tiếp trong lượt duyệt có thể làm Iterator mất hiệu lực. Tái nhập monitor không làm một thao tác sửa tùy ý trở thành thao tác duyệt hợp lệ.

## 15. Quy tắc làm việc với wrapper đồng bộ

Giữ lại thông tin về đối tượng gốc ngay từ lúc tạo wrapper, để mọi khung nhìn và thao tác đọc–ghi phối hợp theo cùng giao thức. Khi duyệt, khóa đúng gốc trước khi tạo bộ duyệt và giữ nó cho đến khi hoàn tất phạm vi cần bảo vệ.

Nếu không muốn giữ khóa qua xử lý lâu, tạo dữ liệu độc lập trong vùng bảo vệ rồi làm việc ngoài khóa. Đừng để Iterator, Spliterator, Stream chưa được thực hiện hoặc entry sống làm rò rỉ một lượt duyệt ra khỏi phạm vi mà nó cần được bảo vệ.

Quy tắc cốt lõi là: **khóa đối tượng sở hữu giao thức đồng bộ, không chọn khóa theo tên biến hoặc theo khung nhìn đang được duyệt.** Với List/Set gốc đó là wrapper gốc; với các khung nhìn Map và vùng con dùng chung mutex, vẫn theo khóa gốc được hợp đồng chỉ định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-005">Collection đồng thời, wrapper có khóa và bản sao bất biến</a>

<details>
<summary>Xem chi tiết</summary>

# Collection đồng thời, wrapper có khóa và bản sao bất biến

## 1. Ba mô hình khác nhau cho dữ liệu được chia sẻ

Khi nhiều luồng thực thi cùng cần một tập dữ liệu, câu hỏi đầu tiên không chỉ là “lớp nào an toàn đa luồng?”. Cần xác định dữ liệu có còn thay đổi không, ai được thay đổi và người đọc cần thấy một phiên bản nhất quán tới mức nào.

Có ba cách tổ chức thường gặp:

1. **Collection đồng thời:** cấu trúc và các thao tác được thiết kế cho truy cập đồng thời theo hợp đồng riêng, chẳng hạn `ConcurrentHashMap`.
2. **Lớp bao có khóa:** các thao tác phù hợp trên một collection nền được phối hợp qua cơ chế khóa, chẳng hạn `Collections.synchronizedMap`.
3. **Bản sao không cho sửa cấu trúc:** tạo một tập dữ liệu đầu ra không được thêm, xóa hoặc thay phần tử qua API; khi cần cập nhật, tạo và công bố một phiên bản khác.

Ba mô hình không phải ba cấp “tốt, khá, kém”. Chúng xử lý những yêu cầu khác nhau.

| Câu hỏi | Collection đồng thời | Wrapper có khóa | Bản sao không cho sửa |
| --- | --- | --- | --- |
| Dữ liệu có thể cập nhật tại chỗ không? | Có, theo API của lớp | Có nếu cấu trúc nền hỗ trợ | Không qua API của bản sao |
| Ai tổ chức phối hợp truy cập? | Cấu trúc và thao tác concurrent cụ thể | Lớp bao và các vùng khóa do ứng dụng thiết kế | Cơ chế tạo, công bố và thay phiên bản |
| Mọi chuỗi nhiều lời gọi có tự nguyên tử không? | Không | Không; có thể cần giữ cùng khóa | Việc thay tham chiếu hoặc đọc nhiều phiên bản vẫn cần thiết kế |
| Người đọc luôn có ảnh chụp toàn bộ tại một thời điểm không? | Không thể suy chung | Có thể lấy trạng thái dưới khóa đúng phạm vi | Bản sao ổn định chưa chứng minh quá trình lấy nguồn là ảnh chụp nguyên tử |
| Đối tượng phần tử tự bất biến không? | Không | Không | Không, nếu chỉ sao chép cấu trúc |

Trong bài, “bản sao bất biến” được hiểu trước hết ở mức cấu trúc collection: không cho thêm, xóa hoặc thay phần tử. Muốn bất biến sâu còn phải bảo đảm trạng thái của các đối tượng phần tử và những đối tượng mà chúng tham chiếu tới.

Một danh sách chỉ chứa `String` và một danh sách chứa các đối tượng nghiệp vụ có setter không có cùng mức độ ổn định, dù cả hai đều được tạo bằng `List.copyOf`.

## 2. Không cho sửa, có khóa và có bản sao là ba tính chất riêng

Các cách viết sau biểu đạt ba ý khác nhau:

```java
// Đoạn minh họa; source là List<String> hợp lệ.
List<String> locked = Collections.synchronizedList(source);
List<String> readOnlyView = Collections.unmodifiableList(source);
List<String> snapshot = List.copyOf(source);
```

- `locked` là lớp bao đồng bộ nhưng vẫn dùng dữ liệu nền `source`.
- `readOnlyView` chặn sửa qua chính khung nhìn đó, nhưng vẫn nhìn dữ liệu nền.
- `snapshot` có nội dung cấu trúc không tự cập nhật theo những thay đổi về sau ở nguồn.

Nếu nguồn đang được nhiều bên sửa, bản thân bước tạo bản sao cũng phải đọc nguồn theo một chính sách hợp lệ. Cú pháp `copyOf` không tự sửa mọi vấn đề phối hợp ở đầu vào.

### Thí nghiệm wrapper, khung nhìn và bản sao

Lưu vào `LockedViewAndCopyDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LockedViewAndCopyDemo {
    public static void main(String[] args) {
        List<String> locked = Collections.synchronizedList(
                new ArrayList<>(List.of("A", "B")));
        List<String> readOnlyView = Collections.unmodifiableList(locked);
        List<String> snapshot;
        synchronized (locked) {
            snapshot = List.copyOf(locked);
        }

        locked.add("C");
        synchronized (locked) {
            System.out.println("locked=" + locked);
            System.out.println("readOnlyView=" + readOnlyView);
        }
        System.out.println("snapshot=" + snapshot);

        try {
            readOnlyView.add("D");
        } catch (UnsupportedOperationException e) {
            System.out.println("viewAdd=UnsupportedOperationException");
        }
        try {
            snapshot.add("D");
        } catch (UnsupportedOperationException e) {
            System.out.println("copyAdd=UnsupportedOperationException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 LockedViewAndCopyDemo.java
java LockedViewAndCopyDemo
```

Stdout:

```text
locked=[A, B, C]
readOnlyView=[A, B, C]
snapshot=[A, B]
viewAdd=UnsupportedOperationException
copyAdd=UnsupportedOperationException
```

Sau khi tạo ba đối tượng liên quan, chương trình thêm `C` thông qua `locked`:

- `locked` có `[A, B, C]`.
- `readOnlyView` cũng có `[A, B, C]` vì nó đọc dữ liệu nền qua lớp bao.
- `snapshot` vẫn là `[A, B]`.

Cả `readOnlyView.add` và `snapshot.add` đều bị chặn, nhưng nguyên nhân “không cho sửa qua API” không làm hai đối tượng có cùng quan hệ với nguồn.

`Collections.unmodifiableList` tạo khung nhìn, còn `List.copyOf` có cam kết rằng các thay đổi cấu trúc về sau ở nguồn không được phản ánh vào kết quả. [Javadoc Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)), [Javadoc List.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection))

Trong ví dụ, việc tạo bản sao và phần đọc khung nhìn được đặt dưới đúng khóa của `locked`. Không thêm một lớp `unmodifiable` rồi giả định đã có một monitor mới thay thế monitor gốc.

### Không cần phụ thuộc danh tính của bản sao

Một factory có thể tái sử dụng một đối tượng đầu vào phù hợp thay vì luôn cấp một đối tượng mới. Điều cần dựa vào là hợp đồng nội dung và quyền sửa, không phải kết luận rằng hai tham chiếu chắc chắn khác nhau.

Nếu cần công bố hoặc cập nhật dữ liệu giữa các luồng, dùng một cơ chế phối hợp rõ ràng. Không chọn chính đối tượng collection dựa trên giá trị làm khóa chỉ vì nghĩ nó luôn là một đối tượng mới riêng biệt.

## 3. Wrapper có khóa phù hợp với một chính sách sở hữu chung

Với `Collections.synchronizedList`, `synchronizedSet` hoặc `synchronizedMap`, mọi đường truy cập tới cấu trúc nền phải tuân thủ chính sách của lớp bao. Giữ lại tham chiếu thô rồi sửa nó ở nơi khác làm mất sự thống nhất đó.

Các thao tác trực tiếp như `add`, `get` hoặc `put` được phối hợp, nhưng một quyết định gồm nhiều lời gọi cần có phạm vi bảo vệ riêng.

Đoạn minh họa kiểm tra rồi thêm dưới cùng monitor:

```java
// names là một synchronized List<String> được chia sẻ.
synchronized (names) {
    if (!names.contains("new")) {
        names.add("new");
    }
}
```

Nếu bỏ khối ngoài, hai luồng vẫn có thể cùng thấy chưa có phần tử trước khi lần lượt thêm. Từng phương thức được đồng bộ không có nghĩa toàn bộ thuật toán tự là một thao tác nguyên tử.

### Duyệt là một phạm vi cần được bảo vệ

Với iterator, spliterator hoặc stream trên lớp bao, phải tuân thủ quy tắc khóa thủ công cho thời gian duyệt. Với các khung nhìn của synchronized Map, khóa là Map lớp bao gốc, không phải đối tượng `keySet` hay `entrySet`. [Javadoc synchronizedMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map))

Điều đó cho phép ứng dụng lấy một trạng thái có kiểm soát trong lúc mọi bên liên quan cùng dùng đúng chính sách khóa. Đổi lại, những bên cần cùng khóa có thể phải chờ trong thời gian vùng bảo vệ đang chạy.

Một wrapper không tự tạo giao dịch cho nhiều collection khác nhau. Nếu một bất biến liên quan một List và một Map, hai monitor độc lập không đủ để đảm bảo người đọc luôn thấy một trạng thái chung của cả hai.

### Đừng chuyển quy tắc monitor sang mọi collection concurrent

Một khối `synchronized (someMap)` chỉ hữu ích khi những bên cần phối hợp thực sự tuân thủ monitor đó. Các thao tác thông thường của `ConcurrentHashMap` không tự dùng monitor của đối tượng theo hợp đồng của `synchronizedMap`.

Vì thế, đổi loại Map mà giữ nguyên giả định “khóa bên ngoài sẽ chặn mọi thao tác” có thể làm sai thiết kế. Chọn loại cấu trúc và cách bảo vệ một bất biến là một quyết định kết hợp.

## 4. Collection đồng thời cung cấp những thao tác có hợp đồng riêng

`ConcurrentHashMap` cho phép truy cập và cập nhật đồng thời theo hợp đồng của nó. Các phép đọc thường có thể chồng lấp với cập nhật; không nên hình dung toàn bộ Map bị giữ bởi một monitor chung giống lớp bao đồng bộ.

Tuy nhiên, “collection đồng thời” không có nghĩa mọi thao tác đều không chờ, mọi chuỗi lời gọi đều nguyên tử hoặc mọi lần duyệt đều là ảnh chụp toàn bộ.

Đoạn minh họa sau vẫn chưa gộp một phép tăng:

```java
// counts là ConcurrentHashMap<String, Integer>.
int previous = counts.getOrDefault("count", 0);
counts.put("count", previous + 1);
```

Hai bên có thể đọc cùng giá trị cũ rồi ghi cùng giá trị mới. Cấu trúc bên trong Map vẫn hợp lệ nhưng mục tiêu đếm đủ số lần cập nhật có thể không đạt.

Với yêu cầu cập nhật theo một khóa, chọn thao tác nguyên tử phù hợp, chẳng hạn `merge`, `compute` hoặc `putIfAbsent` theo đúng ngữ nghĩa cần có.

### Thí nghiệm cập nhật nguyên tử theo khóa

Lưu vào `ConcurrentMapOperationsDemo.java`.

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class ConcurrentMapOperationsDemo {
    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                await(start);
                for (int n = 0; n < 500; n++) {
                    counts.merge("requests", 1, Integer::sum);
                }
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        Integer first = counts.putIfAbsent("limit", 42);
        Integer second = counts.putIfAbsent("limit", 99);
        System.out.println("requests=" + counts.get("requests"));
        System.out.println("firstPrevious=" + first);
        System.out.println("secondPrevious=" + second);
        System.out.println("limit=" + counts.get("limit"));

        try {
            counts.put("invalid", null);
        } catch (NullPointerException e) {
            System.out.println("nullValue=NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ConcurrentMapOperationsDemo.java
java ConcurrentMapOperationsDemo
```

Stdout:

```text
requests=2000
firstPrevious=null
secondPrevious=42
limit=42
nullValue=NullPointerException
```

Bốn luồng thực thi đều chờ tín hiệu bắt đầu rồi mỗi luồng tăng `requests` 500 lần. `merge("requests", 1, Integer::sum)` diễn đạt việc thêm giá trị ban đầu nếu chưa có, hoặc cộng vào giá trị hiện tại như một thao tác nguyên tử theo hợp đồng phương thức. Sau khi `join` các luồng, tổng là 2000. [Javadoc ConcurrentHashMap.merge](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#merge(K,V,java.util.function.BiFunction))

Hàm kết hợp `Integer::sum` chỉ tính giá trị mới, không có I/O hay cập nhật một ánh xạ khác. Hàm tính lại trong các thao tác này cần ngắn và tuân thủ giới hạn của phương thức; không coi nó là nơi tùy ý thực hiện một quy trình nghiệp vụ dài.

Hai lần `putIfAbsent` cho khóa `limit` cho thấy:

- Lần đầu chưa có khóa, nên thêm 42 và trả `null` để báo chưa có giá trị trước.
- Lần sau thấy 42 đã có, nên không thay thành 99 và trả 42.
- Giá trị cuối của `limit` vẫn là 42.

`ConcurrentHashMap` không chấp nhận khóa hoặc giá trị `null`. Vì thế `firstPrevious=null` là giá trị báo chưa có ánh xạ trước đó, không phải bằng chứng Map đang lưu một giá trị `null`.

### Nguyên tử theo khóa không phải giao dịch nhiều khóa

Một phép `merge` hoặc `putIfAbsent` đúng không tự tạo giao dịch bao trùm hai khóa, hai Map hoặc một Map cùng một hệ thống bên ngoài.

Ví dụ, nếu cần cập nhật hai số liệu mà tổng của chúng phải giữ nguyên, hai lời gọi cập nhật riêng vẫn có thể để người đọc thấy trạng thái xen giữa. Khi đó phải thiết kế bất biến và cách công bố dữ liệu phù hợp, thay vì chỉ đổi loại Map.

Tương tự, lấy một đối tượng giá trị ra từ Map rồi sửa trường của nó không tự trở thành thao tác nguyên tử của Map. Tính an toàn của cấu trúc chứa và tính an toàn của trạng thái phần tử vẫn là hai tầng riêng.

## 5. Cách duyệt cho thấy sự khác nhau rõ nhất giữa các mô hình

Có ít nhất ba kiểu hành vi cần phân biệt:

| Kiểu duyệt | Cách tổ chức | Người đọc cần hiểu |
| --- | --- | --- |
| Duyệt dưới khóa của wrapper | Giữ đúng monitor suốt lần duyệt | Những bên cập nhật theo cùng khóa phải chờ |
| Duyệt ảnh chụp của CopyOnWriteArrayList | Iterator giữ mảng đã có khi nó được tạo | Không tự nhìn những lần thêm hoặc xóa sau đó |
| Duyệt nhất quán yếu của ConcurrentHashMap | Duyệt theo chính sách concurrent của lớp | Không có cam kết ảnh chụp nguyên tử toàn bộ Map |

Không gom hai loại sau thành một khái niệm “cứ concurrent là duyệt snapshot”. Chính sách của từng lớp có ý nghĩa khác nhau.

### Thí nghiệm wrapper khóa người ghi và iterator ảnh chụp

Lưu vào `TraversalModelsDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

public class TraversalModelsDemo {
    public static void main(String[] args) throws InterruptedException {
        List<Integer> locked = Collections.synchronizedList(
                new ArrayList<>(List.of(1, 2, 3)));
        CountDownLatch approachingAdd = new CountDownLatch(1);
        CountDownLatch addFinished = new CountDownLatch(1);
        List<Integer> lockedRead = new ArrayList<>();
        Thread writer;
        boolean finishedInsideLock;

        synchronized (locked) {
            writer = new Thread(() -> {
                approachingAdd.countDown();
                locked.add(4);
                addFinished.countDown();
            });
            writer.start();
            approachingAdd.await();
            Iterator<Integer> iterator = locked.iterator();
            while (iterator.hasNext()) {
                lockedRead.add(iterator.next());
            }
            finishedInsideLock = addFinished.getCount() == 0;
        }
        writer.join();
        System.out.println("lockedRead=" + lockedRead);
        System.out.println("addFinishedInsideLock=" + finishedInsideLock);
        System.out.println("lockedAfterWriter=" + locked);

        CopyOnWriteArrayList<Integer> copyOnWrite =
                new CopyOnWriteArrayList<>(List.of(1, 2, 3));
        Iterator<Integer> oldIterator = copyOnWrite.iterator();
        Thread anotherWriter = new Thread(() -> copyOnWrite.add(4));
        anotherWriter.start();
        anotherWriter.join();
        List<Integer> oldRead = new ArrayList<>();
        oldIterator.forEachRemaining(oldRead::add);
        System.out.println("snapshotIteratorRead=" + oldRead);
        System.out.println("copyOnWriteAfterWriter=" + copyOnWrite);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TraversalModelsDemo.java
java TraversalModelsDemo
```

Stdout:

```text
lockedRead=[1, 2, 3]
addFinishedInsideLock=false
lockedAfterWriter=[1, 2, 3, 4]
snapshotIteratorRead=[1, 2, 3]
copyOnWriteAfterWriter=[1, 2, 3, 4]
```

Ở phần synchronized List, luồng chính giữ monitor của `locked`. Bên ghi báo tín hiệu trước khi gọi `locked.add(4)`. Khi luồng chính còn giữ khóa, phép thêm đó chưa thể hoàn tất, nên `addFinishedInsideLock=false`.

Tín hiệu ở đây được phát trước khi bên ghi cần khóa. Vì vậy luồng chính có thể chờ mốc tín hiệu trong vùng khóa mà không yêu cầu bên ghi phải vào vùng đang bị khóa mới phát được tín hiệu.

Dữ liệu duyệt dưới khóa là `[1, 2, 3]`. Sau khi nhả khóa và chờ bên ghi kết thúc, nguồn có `[1, 2, 3, 4]`. Thí nghiệm không đoán trạng thái lập lịch hoặc dùng thời gian ngủ để suy ra rằng bên ghi đã chạy xong.

Ở phần `CopyOnWriteArrayList`, iterator được tạo trước khi thêm 4. Bên ghi hoàn tất trước khi iterator cũ được duyệt, nhưng iterator vẫn trả `[1, 2, 3]`. Trạng thái hiện tại của collection đã là `[1, 2, 3, 4]`.

Đó là chính sách ảnh chụp của iterator: đọc mảng đã chọn từ lúc tạo iterator, không cập nhật theo mảng mới của collection. Các lần ghi có chi phí tạo bản sao mảng; chính sách này cần được cân nhắc theo tần suất đọc và ghi. [Javadoc CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)

### ConcurrentHashMap không cung cấp cùng ảnh chụp đó

Iterator và spliterator của `ConcurrentHashMap` có chính sách nhất quán yếu. Chúng hỗ trợ việc duyệt khi có cập nhật, nhưng không cam kết toàn bộ kết quả tương ứng với một trạng thái duy nhất tại một thời điểm. Những phép tổng hợp như `size` trong lúc cập nhật cũng không phải hàng rào để quyết định một bất biến chung. [Javadoc ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

Không ném `ConcurrentModificationException` chỉ là một phần của hợp đồng đó, không đồng nghĩa:

- Nhìn thấy mọi cập nhật vừa xảy ra.
- Giữ nguyên mọi giá trị lúc iterator được tạo.
- Có cùng kết quả với một lần đọc toàn bộ dưới khóa.
- Có thể thực hiện một giao dịch nhiều khóa dựa trên kết quả duyệt.

Nếu nghiệp vụ chỉ cần duyệt dữ liệu đang thay đổi để giám sát hoặc xử lý theo chính sách cho phép, kiểu nhất quán này có thể phù hợp. Nếu cần một phiên bản thống nhất, phải thiết kế cách lấy hoặc công bố phiên bản đó.

## 6. Bản sao ổn định chưa chắc được tạo từ một thời điểm nguyên tử

`List.copyOf`, `Set.copyOf` và `Map.copyOf` tạo kết quả không cho sửa cấu trúc theo hợp đồng tương ứng. Khi nguồn được sửa về sau, cấu trúc kết quả không tự chạy theo nguồn.

Nhưng có hai giai đoạn độc lập:

```text
Đọc trạng thái từ nguồn  →  Tạo cấu trúc kết quả không cho sửa
        ↑                                ↑
  Có nhất quán không?             Có ổn định về sau không?
```

Đầu ra ổn định chỉ trả lời vế sau. Nếu đầu vào đang được đọc theo chính sách nhất quán yếu, không có một phép khóa toàn cục tự xuất hiện chỉ vì kết quả cuối được tạo bằng `copyOf`.

Ví dụ minh họa một lần đọc có thể bị xen cập nhật:

```text
Trạng thái đầu: A=1, B=1

Lần đọc bản sao: đọc A=1
Bên ghi:        đổi A thành 2
Bên ghi:        đổi B thành 2
Lần đọc bản sao: đọc B=2

Giá trị đã đọc: A=1, B=2
```

Trong lịch này, các trạng thái nguồn đi qua là `(1,1)`, `(2,1)`, `(2,2)`. Bộ giá trị đã đọc `(1,2)` không phải một trong những trạng thái toàn bộ đó.

Đây là minh họa vấn đề đọc nhiều giá trị khi chúng thay đổi, không phải stdout cố định của một chương trình được giả định luôn có đúng lịch xen kẽ này. Một cấu trúc duyệt nhất quán yếu không tạo cam kết ngăn mọi trường hợp như vậy.

### Khi cần ảnh chụp đúng phạm vi, phải bảo vệ bước lấy dữ liệu

Với wrapper có khóa, có thể giữ đúng monitor trong khi sao chép, miễn mọi bên ghi dữ liệu liên quan đều tuân thủ cùng chính sách.

Với một hệ thống công bố các phiên bản không cho sửa, người đọc có thể lấy một tham chiếu phiên bản rồi đọc toàn bộ dữ liệu từ đúng phiên bản đó.

Với một collection concurrent đang được cập nhật độc lập theo nhiều khóa, nếu yêu cầu đòi trạng thái toàn cục, cần một thiết kế riêng. `Map.copyOf(concurrentMap)` không phải thao tác giao dịch chụp toàn bộ Map tại một thời điểm.

Ngay cả khi tạo bản sao từ `ArrayList` thông thường, vẫn phải bảo đảm giai đoạn đọc nguồn không bị cập nhật trái với hợp đồng. Kết quả không cho sửa không sửa được một bước lấy dữ liệu không hợp lệ.

## 7. Công bố các phiên bản không cho sửa bằng một tham chiếu được phối hợp

Khi dữ liệu chủ yếu được đọc, một mô hình khác là không cập nhật tại chỗ phiên bản đã đưa cho người đọc. Bên ghi tạo phiên bản tiếp theo, rồi thay tham chiếu dùng để công bố phiên bản hiện tại.

Người đọc lấy tham chiếu một lần và tiếp tục dùng chính phiên bản đó:

```text
Tham chiếu hiện tại ──> phiên bản mới
Người đọc cũ       ──> phiên bản cũ vẫn giữ nguyên
```

Việc không cho sửa cấu trúc giúp đọc một phiên bản ổn định. Cơ chế công bố tham chiếu giúp các luồng phối hợp về việc nhìn thấy và thay phiên bản. Hai trách nhiệm này không thay thế cho nhau.

Có thể tổ chức công bố bằng một khóa, trường `volatile` trong thiết kế thích hợp hoặc `AtomicReference`. Nếu nhiều bên ghi cần tính phiên bản mới từ phiên bản hiện tại, chỉ gọi `get` rồi `set` riêng không tự gộp toàn bộ cập nhật.

### Thí nghiệm thay phiên bản nguyên tử

Lưu vào `PublishedImmutableVersionsDemo.java`.

```java
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class PublishedImmutableVersionsDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicReference<Map<String, Integer>> current =
                new AtomicReference<>(Map.of("count", 0));
        Map<String, Integer> oldVersion = current.get();

        Runnable update = () -> {
            for (int i = 0; i < 200; i++) {
                current.updateAndGet(previous -> {
                    Map<String, Integer> next = new HashMap<>(previous);
                    next.put("count", previous.get("count") + 1);
                    return Map.copyOf(next);
                });
            }
        };
        Thread first = new Thread(update);
        Thread second = new Thread(update);
        first.start();
        second.start();
        first.join();
        second.join();

        Map<String, Integer> latest = current.get();
        System.out.println("oldVersionCount=" + oldVersion.get("count"));
        System.out.println("latestVersionCount=" + latest.get("count"));
        try {
            latest.put("count", -1);
        } catch (UnsupportedOperationException e) {
            System.out.println("latestPut=UnsupportedOperationException");
        }
        System.out.println("oldVersionStill=" + oldVersion.get("count"));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 PublishedImmutableVersionsDemo.java
java PublishedImmutableVersionsDemo
```

Stdout:

```text
oldVersionCount=0
latestVersionCount=400
latestPut=UnsupportedOperationException
oldVersionStill=0
```

Mỗi luồng tạo 200 lần cập nhật. `updateAndGet` gộp việc lấy phiên bản hiện tại, tính ứng viên và cập nhật tham chiếu theo hợp đồng nguyên tử của phương thức, nên sau hai luồng giá trị là 400. [Javadoc AtomicReference.updateAndGet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html#updateAndGet(java.util.function.UnaryOperator))

Mỗi ứng viên được tạo bằng một `HashMap` cục bộ, sửa bản cục bộ đó rồi chuyển thành `Map.copyOf`. Không có thao tác sửa tại chỗ một Map đã được công bố.

`oldVersion` vẫn giữ `count=0`, trong khi `latest` có `count=400`. Gọi `put` lên `latest` bị chặn. Khóa và giá trị trong ví dụ đều là `String` hoặc `Integer` bất biến, nên không có một giá trị đối tượng thay đổi được ẩn bên trong phiên bản.

### Hàm tính phiên bản có thể được gọi lại

Khi các cập nhật cạnh tranh, `updateAndGet` có thể áp dụng lại hàm tính. Vì vậy, hàm đó cần không có tác động phụ bên ngoài.

Ví dụ tạo một Map cục bộ là phù hợp: nếu ứng viên không được dùng, không có dữ liệu ngoài nào đã bị cập nhật. Không đặt hành động gửi yêu cầu bên ngoài, ghi nhận một sự kiện bắt buộc chỉ một lần hoặc tăng một biến chung trong hàm rồi giả định hàm luôn được gọi đúng một lần cho mỗi cập nhật thành công.

Điều này khác với một lời gọi phương thức nghiệp vụ thông thường mà người đọc mã có thể tưởng chỉ chạy một lượt.

### Lấy cùng một phiên bản cho các lần đọc liên quan

Nếu một thao tác cần đọc hai trường thuộc cùng trạng thái cấu hình, nên lấy tham chiếu hiện tại một lần rồi đọc cả hai từ phiên bản đã lấy.

Gọi `current.get()` riêng trước mỗi trường có thể nhận các phiên bản khác nhau nếu bên ghi vừa công bố một phiên bản mới. Mỗi lần lấy tham chiếu đúng riêng lẻ không có nghĩa nhiều lần lấy liên tiếp tự thuộc một giao dịch đọc chung.

Ví dụ có chủ đích giữ `oldVersion` và `latest` thành hai tham chiếu riêng để làm rõ ranh giới phiên bản. Không dùng `Thread.sleep` để suy luận bên ghi đã xong; các lần `join` bảo vệ thời điểm đánh giá kết quả cuối.

### Chi phí không biến mất, nó chuyển sang lúc cập nhật

Tạo bản sao toàn bộ mỗi lần cập nhật tốn công sao chép và tạo đối tượng mới. Mô hình này có thể phù hợp khi người đọc cần phiên bản ổn định và số lần cập nhật tương đối ít, nhưng không phải cách mặc định tối ưu cho một Map lớn được sửa liên tục.

Ví dụ có một ánh xạ để kiểm chứng tính đúng đắn, không phải phép đo hiệu năng. Khi dữ liệu lớn, cần cân nhắc kích thước phiên bản, tần suất cập nhật, khả năng giữ phiên bản cũ và cách thu hồi những phiên bản không còn được dùng.

## 8. Bản sao không cho sửa vẫn có giới hạn ở phần tử

Một bản sao `List.copyOf` không tự tạo đối tượng mới cho từng phần tử. `Map.copyOf` cũng không tự sao chép sâu mọi khóa và giá trị.

Lưu vào `ShallowImmutableCopyDemo.java`.

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShallowImmutableCopyDemo {
    static final class MutableTag {
        private String text;

        MutableTag(String text) {
            this.text = text;
        }
    }

    public static void main(String[] args) {
        MutableTag tag = new MutableTag("before");
        List<MutableTag> source = new ArrayList<>(List.of(tag));
        List<MutableTag> copiedList = List.copyOf(source);
        Map<String, MutableTag> copiedMap = Map.copyOf(Map.of("tag", tag));

        source.add(new MutableTag("later"));
        tag.text = "after";
        System.out.println("sourceSize=" + source.size());
        System.out.println("copiedListSize=" + copiedList.size());
        System.out.println("copiedListText=" + copiedList.get(0).text);
        System.out.println("copiedMapText=" + copiedMap.get("tag").text);

        try {
            copiedList.add(new MutableTag("extra"));
        } catch (UnsupportedOperationException e) {
            System.out.println("copiedListAdd=UnsupportedOperationException");
        }
        try {
            List.copyOf(Arrays.asList("A", null));
        } catch (NullPointerException e) {
            System.out.println("nullListElement=NullPointerException");
        }

        Map<String, String> withNull = new HashMap<>();
        withNull.put("key", null);
        try {
            Map.copyOf(withNull);
        } catch (NullPointerException e) {
            System.out.println("nullMapValue=NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ShallowImmutableCopyDemo.java
java ShallowImmutableCopyDemo
```

Stdout:

```text
sourceSize=2
copiedListSize=1
copiedListText=after
copiedMapText=after
copiedListAdd=UnsupportedOperationException
nullListElement=NullPointerException
nullMapValue=NullPointerException
```

Sau khi thêm một phần tử vào nguồn:

- Nguồn có hai phần tử.
- `copiedList` vẫn có một phần tử.

Đây là sự độc lập về cấu trúc.

Nhưng sửa `tag.text` thành `after` làm cả `copiedListText` lẫn `copiedMapText` đều là `after`. Chúng vẫn tham chiếu cùng một `MutableTag`. Thí nghiệm chạy những thay đổi này trên một luồng để quan sát quan hệ tham chiếu; nó không biến một thao tác sửa trường dùng chung thành an toàn đa luồng.

Do đó, muốn nhiều người đọc một phiên bản dữ liệu bất biến sâu, phải xem cả mô hình phần tử:

- Dùng các giá trị bất biến phù hợp.
- Tạo dữ liệu đầu ra riêng nếu cần cắt quan hệ với đối tượng nguồn.
- Sao chép hoặc bảo vệ những cấu trúc lồng nhau có thể thay đổi.
- Không để một setter hoặc một tham chiếu có thể sửa xuyên qua ranh giới dữ liệu đã công bố.

Không có một lớp bao collection nào tự suy ra cách sao chép sâu đúng cho mọi đối tượng nghiệp vụ.

### Chính sách null và thứ tự cũng cần được kiểm tra

Các factory `List.copyOf`, `Set.copyOf` và `Map.copyOf` từ chối phần tử hoặc khóa, giá trị `null` theo hợp đồng tương ứng. Chương trình kiểm chứng trường hợp phần tử null của List và giá trị null của Map. [Javadoc List](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html), [Javadoc Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html), [Javadoc Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)

Đổi mô hình dữ liệu có thể làm đổi những chính sách khác:

| Lựa chọn | Null | Thứ tự cần chú ý |
| --- | --- | --- |
| Wrapper trên một collection nền | Theo khả năng của nền | Theo hợp đồng của nền |
| `ConcurrentHashMap` | Không nhận khóa hoặc giá trị null | Không cam kết thứ tự duyệt theo chèn |
| `CopyOnWriteArrayList` | Cho phép phần tử null | Có thứ tự danh sách; iterator đọc ảnh chụp |
| `List.copyOf` | Từ chối phần tử null | Giữ thứ tự duyệt của nguồn |
| `Set.copyOf` | Từ chối phần tử null | Không cam kết giữ thứ tự duyệt của nguồn |
| `Map.copyOf` | Từ chối khóa hoặc giá trị null | Không cam kết giữ thứ tự duyệt của nguồn |

Ví dụ, đổi một Map nền cho phép null thành `ConcurrentHashMap` hoặc một bản `Map.copyOf` không chỉ đổi cách phối hợp truy cập. Cần kiểm tra dữ liệu hợp lệ và hợp đồng API.

Tương tự, khi cần một ảnh chụp Map duyệt theo thứ tự chèn, không mặc định `Map.copyOf` giữ thứ tự đó. Có thể tạo một cấu trúc nền riêng có thứ tự rồi bao không cho sửa, đồng thời không để lộ đường sửa nền và vẫn bảo vệ bước lấy dữ liệu.

## 9. Chọn mô hình theo bất biến và cách cập nhật

| Tình huống | Mô hình có thể phù hợp | Điều phải xác định |
| --- | --- | --- |
| Nhiều cập nhật độc lập theo khóa | Collection concurrent và thao tác nguyên tử phù hợp | Phạm vi nguyên tử theo khóa; trạng thái giá trị |
| Một nhóm thao tác cần cùng một trạng thái dùng chung | Wrapper có khóa hoặc lớp sở hữu với chính sách khóa rõ | Monitor và toàn bộ đường đọc, ghi cần phối hợp |
| Dữ liệu cấu hình đọc nhiều, thay theo phiên bản | Phiên bản không cho sửa được công bố đúng cách | Bất biến phần tử, cách thay tham chiếu và cách đọc một phiên bản |
| Danh sách cần duyệt thường xuyên, ít cập nhật | Collection có chính sách ảnh chụp như copy-on-write nếu chi phí phù hợp | Chi phí ghi và việc người đọc không thấy cập nhật mới |
| Tác vụ tính toán dài chấp nhận dữ liệu tại một mốc | Chụp dữ liệu hợp lệ rồi xử lý bản chụp | Tính nhất quán khi chụp và mức độ sao chép |
| Chuyển dữ liệu đầu ra cho bên chỉ được đọc | Bản sao hoặc mô hình dữ liệu đầu ra không cho sửa | Có dùng chung đối tượng phần tử hay không |

Những lựa chọn này cần được kiểm tra trên yêu cầu thật. Không phải mọi Map đều có một bất biến toàn cục, và không phải mọi lần đọc đều cần thấy dữ liệu mới nhất. Yêu cầu càng rõ, phạm vi phối hợp càng dễ thiết kế.

### Các kết luận dễ gây lỗi

| Kết luận sai | Điều cần phân biệt |
| --- | --- |
| Có từ concurrent là mọi thuật toán nhiều bước đều đúng | Thao tác nguyên tử cụ thể khác chuỗi lời gọi bất kỳ |
| Có wrapper là chỉ cần không sửa trực tiếp dữ liệu thô | Còn phải bảo vệ thao tác ghép và quá trình duyệt |
| Bọc unmodifiable là đã có snapshot | Khung nhìn vẫn có thể chạy theo cấu trúc nền |
| copyOf tự khóa toàn bộ nguồn khi đọc | Bước lấy dữ liệu cần chính sách nhất quán riêng |
| Kết quả copyOf ổn định nên chắc là một trạng thái nguồn tại một thời điểm | Ổn định về sau khác tính nguyên tử khi lấy nguồn |
| CopyOnWriteArrayList và ConcurrentHashMap có cùng chính sách duyệt | Một loại iterator ảnh chụp khác với duyệt nhất quán yếu |
| Thread-safe của Map bảo vệ mọi setter của giá trị | Giá trị có trạng thái dùng chung cần chính sách riêng |
| Danh sách không cho sửa nghĩa là mọi phần tử bất biến sâu | Bản sao cấu trúc có thể giữ cùng tham chiếu phần tử |
| Gọi AtomicReference.get rồi set riêng là một phép cập nhật ghép nguyên tử | Cần thao tác nguyên tử thích hợp hoặc một chính sách bên ngoài |
| Hàm updateAndGet luôn được gọi đúng một lần | Có thể được áp dụng lại khi cạnh tranh |
| Đọc current.get nhiều lần tự cho cùng phiên bản | Nếu cần nhất quán theo phiên bản, lấy tham chiếu một lần |
| synchronized trên ConcurrentHashMap chặn mọi thao tác của nó | Không cùng hợp đồng monitor với synchronizedMap |
| Đổi mô hình chỉ ảnh hưởng tốc độ | Null, thứ tự, chính sách duyệt và quyền sửa cũng có thể khác |

Một thiết kế có thể kết hợp nhiều mô hình ở các ranh giới khác nhau. Chẳng hạn, bộ phận ghi dùng Map đồng thời cho những cập nhật độc lập; một quy trình khác tạo dữ liệu tổng hợp theo chính sách đã xác định rồi công bố kết quả không cho sửa. Tuy nhiên, phần kết hợp phải có hợp đồng rõ, không tự xuất hiện chỉ vì từng thành phần riêng được gọi là an toàn đa luồng.

## 10. Thực hành xác định đúng bảo đảm cần có

### Bài 1: khung nhìn hay bản sao?

Từ một synchronized List có `[A, B]`, tạo một `Collections.unmodifiableList` và một `List.copyOf` theo đúng chính sách khóa. Sau đó thêm `C` vào nguồn qua lớp bao. Hai kết quả đọc có giống nhau không?

**Đối chiếu:**

Không. Khung nhìn đọc `[A, B, C]`, bản sao giữ `[A, B]`. Cả hai chặn thao tác thêm qua API của kết quả, nhưng có quan hệ khác nhau với nguồn.

### Bài 2: cộng bằng hai lời gọi trên ConcurrentHashMap

Một đoạn mã dùng `get` rồi `put` để tăng bộ đếm. Có thể bỏ qua việc phối hợp thao tác ghép chỉ vì Map là concurrent không?

**Đối chiếu:**

Không. Hai bên có thể tính từ cùng giá trị cũ. Với yêu cầu đếm theo một khóa, có thể dùng `merge` như chương trình, hoặc chọn một thao tác khác có hợp đồng phù hợp.

Điều này chưa giải quyết mọi bất biến liên quan nhiều khóa hoặc hệ thống bên ngoài.

### Bài 3: cần thấy phiên bản cũ hay bản cập nhật?

Iterator của `CopyOnWriteArrayList` được tạo khi có `[1, 2, 3]`. Một bên đã thêm 4 và hoàn tất trước khi iterator cũ được duyệt. Iterator cũ có bắt buộc thấy 4 không?

**Đối chiếu:**

Không. Nó đọc ảnh chụp lúc được tạo, nên trong tình huống đã kiểm chứng vẫn thấy `[1, 2, 3]`. Muốn đọc trạng thái mới, lấy một lần duyệt mới theo hợp đồng lớp.

### Bài 4: tạo Map.copyOf từ Map đang cập nhật

Một Map concurrent chứa nhiều giá trị liên hệ với nhau. Sau `Map.copyOf`, kết quả không cho sửa. Có thể kết luận ngay rằng mọi giá trị trong đó cùng thuộc một thời điểm toàn cục không?

**Đối chiếu:**

Không. Cần xem cách đọc nguồn. Một đầu ra ổn định có thể được tạo từ những giá trị quan sát ở các thời điểm khác nhau nếu nguồn không cung cấp ảnh chụp nguyên tử.

Nếu cần một phiên bản thống nhất, bảo vệ quá trình lấy dữ liệu hoặc dùng cơ chế công bố phiên bản đáp ứng yêu cầu.

### Bài 5: một Map không cho sửa nhưng có MutableTag

Map được tạo bằng `Map.copyOf`, nhưng giá trị là một `MutableTag` đã được dùng ở nơi khác. Khi nơi khác sửa trường của tag, giá trị đọc qua Map có bắt buộc giữ trạng thái cũ không?

**Đối chiếu:**

Không. Map chặn thay ánh xạ qua API, không sao chép sâu đối tượng tag. Cần dữ liệu phần tử bất biến, bản sao phù hợp hoặc một chính sách phối hợp riêng.

### Bài 6: công bố phiên bản bằng AtomicReference

Một yêu cầu cần đọc hai thông số thuộc cùng phiên bản. Cách nào rõ hơn: gọi `current.get()` trước mỗi thông số, hay lấy một biến phiên bản rồi đọc cả hai từ biến đó?

**Đối chiếu:**

Lấy một tham chiếu phiên bản rồi đọc cả hai. Nếu có bên ghi thay tham chiếu giữa hai lần `get`, hai lần đọc riêng có thể thuộc hai phiên bản khác nhau.

AtomicReference giúp phối hợp tham chiếu, nhưng ứng dụng vẫn phải xác định phạm vi của một lần đọc nhất quán.

### Bài 7: tác động phụ trong updateAndGet

Hàm tính phiên bản mới vừa tạo Map vừa ghi nhận một sự kiện bên ngoài. Có được giả định sự kiện đó xảy ra đúng một lần cho mỗi lần cập nhật thành công không?

**Đối chiếu:**

Không. Hàm tính có thể được gọi lại khi cạnh tranh. Giữ hàm tính không có tác động phụ bên ngoài và thiết kế việc ghi nhận sự kiện theo một cơ chế phù hợp riêng.

### Bài 8: nêu đủ điều kiện cho một lựa chọn

Chọn một trong ba yêu cầu:

1. Bộ đếm nhiều khóa được cập nhật độc lập.
2. Một cặp dữ liệu phải được đọc và thay đổi như cùng một trạng thái.
3. Bộ cấu hình được thay theo phiên bản, người đọc cần giữ một phiên bản trong suốt tác vụ.

Với mỗi lựa chọn, hãy nêu cấu trúc hoặc mô hình phối hợp, phạm vi nguyên tử, chính sách duyệt và mức bảo vệ phần tử.

Một lời giải đạt yêu cầu không chỉ nêu tên lớp. Nó cần giải thích người đọc được thấy trạng thái nào, bên ghi được sửa ở đâu và vì sao những cập nhật liên quan không phá bất biến đã chọn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-006">Iterator nhất quán yếu và iterator dùng ảnh chụp</a>

<details>
<summary>Xem chi tiết</summary>

# Iterator nhất quán yếu và iterator dùng ảnh chụp

## 1. Vì sao collection an toàn đa luồng vẫn có nhiều cách duyệt?

Giả sử chương trình đang duyệt danh sách người nhận thông báo thì một bên đăng ký thêm người mới. Lượt thông báo hiện tại nên gửi cho danh sách đã xác định lúc bắt đầu, hay có thể nhận thêm người vừa đăng ký? Nếu một người hủy đăng ký giữa lượt, lời gọi đang chờ tới người đó có bị hủy ngay không?

Một tình huống khác là duyệt một bảng trạng thái có rất nhiều cập nhật. Chương trình có thể chấp nhận một báo cáo gần với thời điểm hiện tại để theo dõi, nhưng bản đối soát cuối kỳ lại cần dữ liệu cùng một phiên bản.

Các tình huống đó cần những **cam kết về kết quả duyệt** khác nhau. Chọn collection an toàn đa luồng mới chỉ trả lời được một phần câu hỏi. Bạn còn phải biết iterator quan sát phiên bản nào, có phản ánh thay đổi trong lúc duyệt không, và có được sửa collection thông qua iterator không.

Hai cơ chế chính trong bài là:

- **Iterator nhất quán yếu (weakly consistent iterator):** cho phép duyệt cùng lúc với các thao tác khác theo hợp đồng của lớp, nhưng không cố định toàn bộ lượt duyệt vào một phiên bản duy nhất.
- **Iterator dùng ảnh chụp (snapshot iterator):** duyệt trạng thái cấu trúc đã được chốt tại lúc iterator được tạo. Thay đổi cấu trúc về sau không đổi tập tham chiếu mà iterator đó đang duyệt.

“Ảnh chụp” ở đây cần hiểu đúng độ sâu. Một mảng tham chiếu cố định không tự làm trạng thái của mọi đối tượng phần tử bất biến.

Sau bài này, bạn cần phân biệt được ba câu hỏi: **có thể duyệt an toàn không**, **đang nhìn phiên bản nào**, và **kết quả có đủ nhất quán cho yêu cầu ứng dụng không**. Chúng liên quan nhưng không thay thế nhau.

Các chương trình dùng Java 17 trở lên. Kiến thức nền gồm `Iterator`, `List`, `Map`, tham chiếu đối tượng và vòng lặp `for-each`. Phần thí nghiệm hai luồng giải thích các điểm chờ ngay trong bài.

## 2. Mô hình tinh thần: collection đang sống và một lượt duyệt

### 2.1. Collection, view và iterator có vòng đời khác nhau

Một collection là đối tượng chứa dữ liệu. Một **khung nhìn (view)** là một cách truy cập dữ liệu liên quan, chẳng hạn `map.keySet()`. Một iterator là đối tượng giữ tiến trình của **một lượt duyệt**.

Đoạn minh họa sau tạo hai lượt duyệt khác nhau trên cùng một danh sách:

```java
Iterator<String> first = names.iterator();
names.add("Lan");
Iterator<String> second = names.iterator();
```

Đây là đoạn mã minh họa, giả sử `names` là `CopyOnWriteArrayList<String>`. `first` và `second` được tạo ở hai thời điểm khác nhau. Chúng không nhất thiết duyệt cùng một phiên bản. `first` không được “làm mới” chỉ vì chương trình gọi `iterator()` để tạo `second`.

Vòng lặp nâng cao `for (String name : names)` lấy một iterator khi bắt đầu vòng lặp. Nó không lấy lại một iterator mới cho mỗi phần tử. Vì vậy, thời điểm bắt đầu vòng lặp có ý nghĩa với iterator dạng ảnh chụp.

Với `ConcurrentHashMap`, `keySet` là view liên kết với map, còn iterator của view là nhất quán yếu. Gọi view không đồng nghĩa chụp một bản sao độc lập của tất cả khóa. [ConcurrentHashMap — keySet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#keySet())

### 2.2. Ba trục cần xem riêng

| Trục xem xét | Ví dụ câu hỏi | Không được suy luận tự động |
| --- | --- | --- |
| Chính sách quan sát thay đổi | Thêm sau khi tạo iterator có xuất hiện trong lượt này không? | Không ném ngoại lệ nghĩa là luôn thấy dữ liệu mới |
| Thứ tự duyệt | Theo chỉ số, thứ tự khóa, FIFO hay không có thứ tự cam kết? | Nhất quán yếu nghĩa là thứ tự hoàn toàn tùy ý |
| Quyền sửa qua iterator | `remove`, `set`, `add` có được hỗ trợ không? | Duyệt an toàn nghĩa là iterator chỉ đọc |

Một map có khóa được sắp xếp vẫn có thể dùng iterator nhất quán yếu. Một danh sách có thứ tự theo chỉ số vẫn có thể dùng iterator ảnh chụp. Hai đặc tính “thứ tự” và “phiên bản quan sát” không phải một.

### 2.3. Đối chiếu nhanh với iterator fail-fast

Iterator của các cấu trúc thông thường như `HashMap` có cơ chế **phát hiện sửa đổi sớm (fail-fast)** khi phát hiện thay đổi cấu trúc ngoài giao thức mà nó cho phép. Cơ chế này chỉ cố gắng phát hiện lỗi; không được dùng việc có hay không có `ConcurrentModificationException` để chứng minh an toàn đa luồng. [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)

Ngoại lệ này có thể xuất hiện ngay cả khi chỉ một luồng vừa duyệt vừa sửa sai cách; chữ “Concurrent” trong tên ngoại lệ không có nghĩa phải có ít nhất hai luồng.

Ngược lại, iterator nhất quán yếu và iterator ảnh chụp được thiết kế với chính sách khác để xử lý thay đổi liên quan. Việc chúng không ném `ConcurrentModificationException` vì thay đổi cấu trúc nguồn không có nghĩa mọi kiểu sử dụng chương trình đều đúng.

Tên “fail-safe iterator” hay xuất hiện trong các bài viết không chính thức, nhưng không nên dùng nó như một hợp đồng chung để gom mọi iterator không fail-fast vào cùng một loại. Hãy dùng tên chính sách và cam kết mà tài liệu lớp thực tế đưa ra.

## 3. Iterator ảnh chụp: chốt cấu trúc tại lúc tạo

### 3.1. Cơ chế của `CopyOnWriteArrayList`

`CopyOnWriteArrayList` dùng cơ chế sao chép khi ghi (copy-on-write). Iterator giữ tham chiếu tới phiên bản mảng ở lúc nó được tạo. Cập nhật danh sách tạo/đưa vào sử dụng một phiên bản phù hợp cho danh sách hiện tại; iterator cũ tiếp tục nhìn phiên bản cũ.

Iterator của lớp này không phản ánh thêm/xóa/thay phần tử của danh sách sau thời điểm tạo. Nó không hỗ trợ `remove`; `ListIterator` cũng không hỗ trợ `set` và `add`. Các thao tác đó ném `UnsupportedOperationException`. [CopyOnWriteArrayList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)

Có thể hình dung bằng hai phiên bản:

```text
t0: list -> [An, Binh, Chi]
             ^
             iterator cu giu phien ban nay

t1: list -> [An, Binh-moi, Dung]

iterator cu:  An, Binh, Chi
iterator moi: An, Binh-moi, Dung
```

Không có bước “đồng bộ lại” để iterator cũ nhảy sang mảng mới. Điều đó làm lượt duyệt có một phạm vi cấu trúc ổn định, nhưng kết quả có thể cũ so với danh sách hiện tại.

### 3.2. Chương trình đầy đủ: phiên bản cũ và tham chiếu cũ

Lưu thành **`SnapshotIteratorDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class SnapshotIteratorDemo {
    public static void main(String[] args) {
        CopyOnWriteArrayList<String> names =
                new CopyOnWriteArrayList<>(List.of("An", "Binh", "Chi"));
        Iterator<String> old = names.iterator();

        names.set(1, "Binh-moi");
        names.remove("Chi");
        names.add("Dung");

        List<String> oldNames = drain(old);
        require(oldNames.equals(List.of("An", "Binh", "Chi")), "old snapshot");
        System.out.println("old iterator = " + oldNames);
        System.out.println("new iterator = " + drain(names.iterator()));
        System.out.println("current list = " + names);
        System.out.println("iterator.remove = " + unsupported(old::remove));

        ListIterator<String> editableCursor = names.listIterator();
        editableCursor.next();
        System.out.println("listIterator.set = "
                + unsupported(() -> editableCursor.set("X")));
        System.out.println("listIterator.add = "
                + unsupported(() -> editableCursor.add("Y")));

        Item apple = new Item("apple", 1);
        Item banana = new Item("banana", 2);
        CopyOnWriteArrayList<Item> items =
                new CopyOnWriteArrayList<>(List.of(apple, banana));
        Iterator<Item> itemSnapshot = items.iterator();

        items.set(0, new Item("new-apple", 7));
        items.remove(banana);
        apple.quantity = 99;

        List<Item> oldItems = drain(itemSnapshot);
        require(oldItems.get(0) == apple, "element reference was copied deeply");
        require(oldItems.get(1) == banana, "removed reference was lost");
        require(oldItems.get(0).quantity == 99, "field change was not visible");
        System.out.println("old item references = " + oldItems);
        System.out.println("current item references = " + items);
        System.out.println("same original apple = " + (oldItems.get(0) == apple));
    }

    private static <T> List<T> drain(Iterator<T> iterator) {
        List<T> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static String unsupported(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Item {
        private final String name;
        private int quantity;

        private Item(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        @Override
        public String toString() {
            return name + ":" + quantity;
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all SnapshotIteratorDemo.java
java SnapshotIteratorDemo
```

Stdout khi chạy thành công:

```text
old iterator = [An, Binh, Chi]
new iterator = [An, Binh-moi, Dung]
current list = [An, Binh-moi, Dung]
iterator.remove = UnsupportedOperationException
listIterator.set = UnsupportedOperationException
listIterator.add = UnsupportedOperationException
old item references = [apple:99, banana:2]
current item references = [new-apple:7]
same original apple = true
```

Chương trình có hai thí nghiệm riêng.

**Thí nghiệm tên người:** iterator được tạo trước khi sửa danh sách. Sau đó chương trình thay `Binh`, xóa `Chi`, thêm `Dung`. Iterator cũ vẫn đi qua ba tham chiếu của phiên bản cũ. Iterator mới và đối tượng danh sách hiện tại nhìn phiên bản mới.

Các lệnh `remove`, `set`, `add` được thử qua iterator chỉ để minh chứng thao tác không được hỗ trợ. Chúng không thay đổi danh sách. Muốn cập nhật danh sách, gọi phương thức trên `CopyOnWriteArrayList`, rồi hiểu rằng lượt duyệt hiện tại vẫn theo ảnh chụp của nó.

**Thí nghiệm hàng hóa:** iterator giữ tham chiếu tới `apple` và `banana` ban đầu. Danh sách hiện tại thay `apple` bằng một đối tượng mới rồi xóa `banana`, nhưng hai tham chiếu cũ vẫn nằm trong ảnh chụp. Khi sửa `apple.quantity = 99`, iterator cũ đọc chính đối tượng đó nên in `apple:99`.

Biểu thức `oldItems.get(0) == apple` kiểm tra danh tính đối tượng, chứng minh ảnh chụp không tạo một bản sao sâu cho `apple`.

### 3.3. Ảnh chụp cấu trúc không phải ảnh chụp sâu

Hãy tách ba điều sau:

| Điều được giữ | Ví dụ | Có phải snapshot iterator tự cung cấp không? |
| --- | --- | --- |
| Tập tham chiếu và thứ tự của phiên bản danh sách | Vẫn chứa tham chiếu `banana` đã bị xóa khỏi danh sách hiện tại | Có, với iterator của `CopyOnWriteArrayList` |
| Trạng thái bên trong mỗi phần tử | `apple.quantity` luôn giữ số 1 | Không |
| Quan hệ nhất quán giữa nhiều đối tượng ngoài danh sách | Tổng hàng hóa và cấu hình thuộc cùng một phiên bản nghiệp vụ | Không |

Nếu phần tử là `String` bất biến, việc giữ tham chiếu làm ví dụ dễ quan sát: bản thân chuỗi không đổi nội dung. Với đối tượng có trường thay đổi, câu chuyện khác hẳn.

Chương trình trên chỉ dùng **một luồng** khi sửa và đọc `quantity`. Nó minh chứng sự chia sẻ tham chiếu, không minh chứng rằng các lần đọc/ghi trường thường giữa nhiều luồng đã được đồng bộ đúng.

Nếu nhiều luồng sửa trạng thái phần tử, cần giao thức riêng: đối tượng bất biến, `volatile`/kiểu atomic cho trạng thái phù hợp, hoặc khóa bảo vệ đủ phạm vi. Đặt đối tượng vào một collection concurrent không tự đồng bộ mọi thay đổi tương lai trên đối tượng đó.

Các concurrent collection có bảo đảm công bố dữ liệu theo hợp đồng bộ nhớ của chúng; điều này không thay thế giao thức cho các lần sửa xảy ra sau đó. [java.util.concurrent — Memory Consistency Properties](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html#MemoryVisibility)

### 3.4. Chi phí và vòng đời của ảnh chụp

Đọc nhiều, cập nhật ít là hoàn cảnh thường phù hợp với copy-on-write. Một thay đổi thực sự đối với danh sách có thể cần sao chép mảng; nếu ghi liên tục hoặc danh sách lớn, chi phí cấp phát và sao chép cần được đo với khối lượng thực tế.

Tạo iterator không đồng nghĩa sao chép sâu từng phần tử. Từ việc iterator giữ phiên bản mảng cũ, có thể suy ra rằng một iterator sống lâu còn giữ các tham chiếu của phiên bản đó. Xóa phần tử khỏi danh sách hiện tại không buộc iterator cũ nhả tham chiếu ngay.

Khi không còn tham chiếu có thể truy cập tới phiên bản cũ, bộ thu gom rác mới có thể thu hồi nó theo cơ chế của JVM. Không nên giữ iterator lâu trong bộ nhớ đệm hay một tác vụ chờ kéo dài nếu không cần.

`CopyOnWriteArraySet` cũng dùng iterator dựa trên ảnh chụp mảng và không hỗ trợ xóa qua iterator. Nó phù hợp với tập thường nhỏ, số lần đọc vượt nhiều so với cập nhật; không phải giải pháp mặc định cho mọi tập dữ liệu concurrent. [CopyOnWriteArraySet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArraySet.html)

## 4. Iterator nhất quán yếu: cho phép cập nhật mà không chốt một phiên bản toàn cục

### 4.1. Những điều có thể dựa vào

Tài liệu `java.util.concurrent` mô tả nhiều iterator concurrent là nhất quán yếu: có thể tiếp tục cùng với các thao tác khác, không dùng `ConcurrentModificationException` để báo sửa đổi nguồn, và có thể nhưng không bắt buộc phản ánh thay đổi sau khi iterator được tạo. Cần đọc thêm cam kết của lớp cụ thể. [java.util.concurrent — Concurrent Collections](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html)

Ở `ConcurrentHashMap`, kết quả iterator phản ánh trạng thái map ở các thời điểm tại hoặc sau lúc iterator được tạo; không có cam kết rằng toàn bộ lượt duyệt là một ảnh chụp duy nhất. Mỗi iterator được thiết kế cho một luồng sử dụng tại một thời điểm. [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

Một cách đọc hữu ích là: **được phép đi qua cấu trúc đang hoạt động, nhưng không được tự nâng mức bảo đảm thành báo cáo cùng một phiên bản**.

Điều này không có nghĩa iterator được phép trả dữ liệu tùy tiện, bỏ qua mọi quy tắc thứ tự hay trả các phần tử chưa từng thuộc nguồn. Hợp đồng của từng lớp vẫn ràng buộc kết quả. Những phần tử ổn định không bị thêm/xóa/thay trong lượt duyệt là điểm tốt để thiết kế kiểm tra; những phần tử bị thay đổi cần xem đúng điều gì API hứa.

### 4.2. Một kết quả duyệt có thể ghép nhiều giai đoạn

Giả sử một lượt duyệt đã đọc dòng A. Trước khi nó đọc dòng B, một tác vụ khác cập nhật dữ liệu. Phần sau của lượt duyệt có thể chịu ảnh hưởng từ giai đoạn sau, trong khi phần đã trả về không tự được tính lại.

Vì vậy, tổng cộng dồn từ iterator không nhất thiết là tổng của toàn bộ hệ thống tại một thời điểm. Ngay cả khi từng giá trị `Integer` riêng lẻ bất biến, **thời điểm lấy các ánh xạ** vẫn có thể khác nhau.

Tương tự, khi khóa `session-7` bị xóa hoặc thay giá trị, việc iterator từng thấy nó không chứng minh phiên đó còn đang đăng ký khi bạn xử lý xong. Kết quả duyệt là quan sát để quyết định công việc tiếp theo, không phải quyền giữ tài nguyên.

“Nhất quán yếu” cũng không tự có nghĩa “iterator đã đi hết rồi sẽ tiếp tục cập nhật cho tới khi thấy mọi thay đổi mới”. Một iterator là một lượt duyệt có tiến trình; nó không phải kênh theo dõi sự kiện hay đăng ký nhận mọi thay đổi.

### 4.3. Thứ tự vẫn phụ thuộc loại collection

| Collection/view | Chính sách iterator | Điểm về thứ tự cần nhớ |
| --- | --- | --- |
| `ConcurrentHashMap.keySet/values/entrySet` | Nhất quán yếu | Không dựa vào thứ tự khóa của bảng băm |
| `ConcurrentLinkedQueue` | Nhất quán yếu | Queue có thứ tự FIFO theo hợp đồng của nó |
| `ConcurrentSkipListMap` và các view | Nhất quán yếu | Khóa vẫn theo thứ tự tự nhiên hoặc comparator |
| `CopyOnWriteArrayList` | Ảnh chụp | Theo thứ tự chỉ số của phiên bản được chụp |

`ConcurrentLinkedQueue` quy định iterator có thể hoạt động cùng các thao tác khác; các cam kết về phần tử/thứ tự phải đọc theo tài liệu queue. `ConcurrentSkipListMap` quy định iterator nhất quán yếu dù bản thân map vẫn sắp xếp khóa. [ConcurrentLinkedQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedQueue.html), [ConcurrentSkipListMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListMap.html)

Bảng này chỉ nêu những lớp cụ thể. Đừng suy rằng mọi lớp có tiền tố `Concurrent` đều dùng một thuật toán iterator, hoặc mọi cấu trúc trong `java.util.concurrent` đều là ảnh chụp.

## 5. Thí nghiệm hai luồng với iterator nhất quán yếu

### 5.1. Thiết kế bài thử theo điều được cam kết

Một bài thử sai thường làm như sau: tạo iterator, thêm một khóa mới rồi khẳng định iterator chắc chắn thấy khóa đó. Cách khẳng định ấy tự áp đặt một cam kết mạnh hơn hợp đồng nhất quán yếu.

Chương trình sau chia khóa thành hai nhóm:

- `base-a`, `base-b` được thêm trước khi tạo iterator và không bị sửa/xóa trong toàn bộ lượt duyệt.
- `changing-0`, `changing-1`, `changing-2` được thay đổi theo hai giai đoạn sau khi tạo iterator.

Bài thử chỉ kiểm tra chắc chắn các khóa ổn định được tìm thấy, không có khóa lạ hay khóa lặp trong lịch cập nhật này. Nó **không khẳng định một tập cố định các khóa changing phải xuất hiện**.

Chỉ một tác vụ sở hữu iterator. Tác vụ kia chỉ sửa map. Các `CountDownLatch` xác định thứ tự các giai đoạn; không dùng `Thread.sleep` để đoán lúc luồng kia đã chạy.

### 5.2. Chương trình đầy đủ

Lưu thành **`WeakIteratorDemo.java`**:

```java
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class WeakIteratorDemo {
    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        map.put("base-a", 1);
        map.put("base-b", 2);
        map.put("changing-0", 0);

        CountDownLatch created = new CountDownLatch(1);
        CountDownLatch phaseOneDone = new CountDownLatch(1);
        CountDownLatch firstVisited = new CountDownLatch(1);
        CountDownLatch phaseTwoDone = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Set<String>> traversal = pool.submit(() -> {
                Iterator<String> iterator = map.keySet().iterator();
                created.countDown();
                await(phaseOneDone);

                Set<String> seen = new HashSet<>();
                require(iterator.hasNext(), "stable entries must be available");
                require(seen.add(iterator.next()), "repeated first key");
                firstVisited.countDown();
                await(phaseTwoDone);

                while (iterator.hasNext()) {
                    require(seen.add(iterator.next()), "repeated key");
                }
                return seen;
            });

            Future<?> updates = pool.submit(() -> {
                await(created);
                map.remove("changing-0");
                map.put("changing-1", 1);
                phaseOneDone.countDown();

                await(firstVisited);
                map.remove("changing-1");
                map.put("changing-2", 2);
                phaseTwoDone.countDown();
                return null;
            });

            Set<String> seen = traversal.get(10, TimeUnit.SECONDS);
            updates.get(10, TimeUnit.SECONDS);
            Set<String> stable = Set.of("base-a", "base-b");
            Set<String> universe = Set.of(
                    "base-a", "base-b",
                    "changing-0", "changing-1", "changing-2");

            require(seen.containsAll(stable), "stable entry was missed");
            require(universe.containsAll(seen), "unknown key");
            System.out.println("stable keys found = " + seen.containsAll(stable));
            System.out.println("observed keys are known = "
                    + universe.containsAll(seen));
            System.out.println("repeated keys = false");
            System.out.println("changing-key subset = not asserted");
            System.out.println("final keys = " + new TreeSet<>(map.keySet()));
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("latch timed out");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all WeakIteratorDemo.java
java WeakIteratorDemo
```

Stdout khi chạy thành công:

```text
stable keys found = true
observed keys are known = true
repeated keys = false
changing-key subset = not asserted
final keys = [base-a, base-b, changing-2]
```

Giải thích thứ tự phối hợp:

1. Tác vụ duyệt tạo iterator rồi mở cổng `created`.
2. Tác vụ cập nhật thay `changing-0` bằng `changing-1`, sau đó mở `phaseOneDone`.
3. Tác vụ duyệt lấy một phần tử, mở `firstVisited`, rồi chờ.
4. Tác vụ cập nhật thay `changing-1` bằng `changing-2`, mở `phaseTwoDone`.
5. Tác vụ duyệt đi hết phần còn lại. Luồng chính lấy kết quả và kiểm tra các điều kiện đã chọn.

Trong lịch này, một phần tử được trả về trước giai đoạn cập nhật thứ hai, phần còn lại sau giai đoạn đó. Tập `seen` của tác vụ duyệt là dữ liệu riêng của nó, không bị tác vụ cập nhật sửa.

Dòng `changing-key subset = not asserted` **không phải một tập kết quả quan sát được**. Nó thông báo rõ rằng bài thử không áp đặt kết quả cố định cho nhóm đang thay đổi. Bạn có thể tự in `new TreeSet<>(seen)` để khảo sát, nhưng kết quả của phần đó không phải stdout cố định mà hợp đồng chung bắt buộc.

Dòng `final keys` được đọc sau khi cả hai tác vụ hoàn tất. Khi ấy không còn bên sửa map, nên in các khóa cuối bằng `TreeSet` cho thứ tự rõ ràng. Đây là trạng thái cuối, không phải tuyên bố rằng iterator cũ đã duyệt đúng tập cuối đó.

Các giới hạn chờ giúp chương trình báo lỗi thay vì treo nếu một tác vụ không thể tiến triển. Mọi lỗi kiểm tra trong tác vụ được chuyển về luồng chính qua `Future.get`. Bài thử không bắt lỗi rồi tiếp tục in kết quả như đã thành công.

### 5.3. Điều không được suy ra từ một lần chạy

Nếu máy của bạn in ra đủ tất cả khóa mới, điều đó không biến “có thể phản ánh thay đổi” thành “bắt buộc phản ánh mọi thay đổi”. Nếu lần chạy khác chỉ thấy một phần thay đổi, cũng không có nghĩa iterator bị hỏng.

Không nên khẳng định một chuỗi khóa cụ thể dựa vào cách phân bố băm hoặc thứ tự các ô nội bộ mà bạn quan sát trên một bản JDK. Đó có thể chỉ là hệ quả của cách triển khai/lịch chạy hiện tại. Thứ tự không được cam kết của `ConcurrentHashMap` không phải thứ tự cần học thuộc.

Bài thử đồng bộ hóa lịch chỉ ở các điểm cần thiết để đặt thay đổi vào giữa lượt duyệt. Không khóa cả map để loại bỏ hoàn toàn cập nhật, vì như vậy bài thử sẽ không còn khảo sát tình huống cần phân biệt.

## 6. So sánh trực tiếp hai chính sách

| Câu hỏi | Iterator nhất quán yếu | Iterator ảnh chụp của copy-on-write |
| --- | --- | --- |
| Duyệt cùng lúc với cập nhật nguồn? | Có theo hợp đồng lớp | Có, lượt cũ giữ phiên bản đã chốt |
| Chốt toàn bộ lượt vào phiên bản lúc tạo? | Không được mặc định như vậy | Có đối với cấu trúc/tham chiếu của phiên bản được chụp |
| Thay đổi sau lúc tạo có thể hiện ra? | Có thể; không phải mọi thay đổi đều bắt buộc hiện ra | Không làm đổi cấu trúc mà iterator cũ đang duyệt |
| Ném `ConcurrentModificationException` vì những thay đổi nguồn đã được chính sách cho phép? | Không | Không |
| Có sao chép sâu mọi phần tử không? | Không có cam kết đó | Không |
| Có được sửa qua iterator không? | Phụ thuộc lớp và thao tác cụ thể | `remove` không hỗ trợ; list iterator cũng không hỗ trợ `set/add` |
| Dữ liệu trả về còn là hiện trạng khi xử lý xong không? | Không có bảo đảm chung | Không; phiên bản có thể đã cũ |
| Có làm công việc nhiều bước thành một giao dịch không? | Không | Không |

Hai chính sách giải quyết những nhu cầu khác nhau. Một lượt duyệt listener có thể muốn tập người nhận cố định cho một đợt. Một bảng theo dõi trạng thái có thể ưu tiên tiếp tục hoạt động trong lúc cập nhật thay vì dừng tất cả bên ghi để lấy một bản chụp toàn cục.

Nhưng một phép kiểm kê cần chứng minh các số thuộc cùng một phiên bản sẽ không thể chỉ nói “iterator không ném ngoại lệ”. Yêu cầu nhất quán của ứng dụng quyết định bạn có chấp nhận kết quả đó được không.

### 6.1. Không nhầm ảnh chụp một entry với ảnh chụp cả iterator

Tài liệu `ConcurrentSkipListMap` mô tả các `Map.Entry` trả về là ảnh chụp ánh xạ tại lúc chúng được tạo, và không hỗ trợ `Entry.setValue`. Đồng thời, iterator của map vẫn là nhất quán yếu. [ConcurrentSkipListMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListMap.html)

Hai điều này không mâu thuẫn: entry A có thể được tạo ở thời điểm t1, entry B ở thời điểm t2. Mỗi cặp khóa–giá trị được giữ lại không tự chứng minh cả tập các cặp thuộc cùng thời điểm.

Từ “snapshot” luôn cần hỏi tiếp: ảnh chụp **cái gì**, ở **thời điểm nào**, và **độ sâu nào**? Với entry chứa một tham chiếu tới đối tượng có thể sửa, trạng thái bên trong đối tượng đó vẫn là câu hỏi riêng.

### 6.2. Sửa qua iterator là một phần hợp đồng riêng

Interface `Iterator` cho phép `remove` là thao tác tùy chọn. Mặc định có thể không hỗ trợ; quy tắc sửa đổi khi đang duyệt còn tùy chính sách lớp triển khai. [Iterator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#remove())

`ConcurrentHashMap` hỗ trợ xóa qua iterator của view, còn iterator copy-on-write đã thử ở trên không hỗ trợ. Do đó, không thể dùng nhãn “không fail-fast” để đoán quyền sửa.

Nếu yêu cầu là **chỉ xóa khi giá trị hiện tại vẫn bằng giá trị đã quan sát**, hãy diễn đạt điều kiện đó bằng thao tác có điều kiện phù hợp. Đoạn minh họa không độc lập sau giả sử `states` là `ConcurrentHashMap<String, String>`:

```java
for (Map.Entry<String, String> entry : states.entrySet()) {
    if ("STALE".equals(entry.getValue())) {
        states.remove(entry.getKey(), entry.getValue());
    }
}
```

`ConcurrentMap.remove(key, expectedValue)` thực hiện kiểm tra giá trị và xóa theo một thao tác nguyên tử. Nó kiểm tra theo quy tắc bằng nhau của giá trị, không tự mang một số phiên bản. Nếu giá trị thay đổi rồi trở lại bằng giá trị cũ, một yêu cầu theo phiên bản cần thêm dữ liệu phiên bản trong thiết kế. [ConcurrentMap — remove có điều kiện](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html#remove(java.lang.Object,java.lang.Object))

Cũng không suy từ việc một thao tác xóa được phép sang bảo đảm “toàn bộ lượt dọn dẹp là một giao dịch”. Các cập nhật khác vẫn có thể diễn ra giữa những lần xử lý entry khác nhau.

## 7. Ứng dụng: một lượt gửi thông báo có tập listener cố định

### 7.1. Quy tắc mong muốn

Giả sử một lượt phát sự kiện lấy danh sách listener ở lúc bắt đầu. Trong khi đang gọi listener A, A hủy đăng ký B và thêm C.

Với chính sách ảnh chụp, một lựa chọn hợp lý là:

- Lượt hiện tại tiếp tục gọi A rồi B vì đó là danh sách đã chốt.
- Lượt tiếp theo gọi A rồi C.
- Không cần giữ khóa collection trong suốt thời gian chạy mã listener chỉ để bảo vệ iterator.

Điều này mô tả **quy tắc của ví dụ**, không phải yêu cầu phù hợp với mọi hệ thống. Nếu hủy đăng ký phải ngăn ngay mọi lời gọi chưa bắt đầu, ảnh chụp đơn thuần chưa đáp ứng.

### 7.2. Chương trình đầy đủ

Lưu thành **`ListenerSnapshotDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ListenerSnapshotDemo {
    public static void main(String[] args) {
        CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
        List<String> calls = new ArrayList<>();

        Runnable listenerB = () -> calls.add("B");
        Runnable listenerC = () -> calls.add("C");
        Runnable listenerA = () -> {
            calls.add("A");
            listeners.remove(listenerB);
            listeners.addIfAbsent(listenerC);
        };

        listeners.add(listenerA);
        listeners.add(listenerB);

        dispatch(listeners);
        require(calls.equals(List.of("A", "B")), "first round");
        System.out.println("round 1 = " + calls);
        calls.clear();

        dispatch(listeners);
        require(calls.equals(List.of("A", "C")), "second round");
        System.out.println("round 2 = " + calls);
        System.out.println("registered listeners = " + listeners.size());
    }

    private static void dispatch(CopyOnWriteArrayList<Runnable> listeners) {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all ListenerSnapshotDemo.java
java ListenerSnapshotDemo
```

Stdout khi chạy thành công:

```text
round 1 = [A, B]
round 2 = [A, C]
registered listeners = 2
```

Vòng `for-each` tạo iterator trước khi gọi A. Khi A sửa danh sách, iterator của lượt đầu vẫn chứa B. C được thêm vào danh sách hiện tại để lượt sau nhìn thấy.

`addIfAbsent(listenerC)` giúp ví dụ không đăng ký lại cùng đối tượng C mỗi lần A chạy. Đây là thao tác riêng phục vụ quản lý danh sách; chính sách ảnh chụp không tự ngăn thêm trùng bằng các đường `add` khác.

Ví dụ chủ động dùng **một luồng phát sự kiện**. `calls` là `ArrayList` chỉ do luồng này đọc/ghi. Nếu cho nhiều luồng gọi `dispatch` cùng lúc, cần xét riêng tính an toàn của listener, dữ liệu nhật ký và trạng thái mà listener truy cập.

### 7.3. Các yêu cầu bổ sung không được snapshot tự giải quyết

Ảnh chụp danh sách không làm mã hàm được gọi (callback) an toàn đa luồng. Hai lượt phát đồng thời vẫn có thể gọi cùng một listener ở hai luồng.

Nó cũng không bảo đảm mọi listener đều được gọi thành công. Trong chương trình trên, nếu một `listener.run()` ném ngoại lệ mà không có nơi xử lý phù hợp, vòng lặp kết thúc tại đó. Tập tham chiếu ổn định không đồng nghĩa xử lý mọi phần tử sẽ hoàn tất.

Nếu quy tắc hủy đăng ký mạnh hơn, có thể cần trạng thái đăng ký, kiểm tra quyền gọi hoặc cơ chế phối hợp tác vụ đang chạy. Cần chỉ rõ thời điểm “hủy có hiệu lực” và cách xử lý lời gọi đã bắt đầu. Đổi collection không tự định nghĩa các điều đó.

Tương tự, listener bị xóa khỏi danh sách hiện tại vẫn có thể được ảnh chụp của lượt đang chạy giữ tham chiếu. Việc tháo đăng ký và việc không còn bất kỳ tham chiếu nào là hai sự kiện khác nhau.

## 8. Khi cần bản chụp nhất quán thật sự

### 8.1. Sao chép sau một lượt duyệt yếu không làm nguồn trở thành một phiên bản

Đoạn minh họa sau tạo một map mới:

```java
Map<String, Integer> copy = new HashMap<>(concurrentMap);
```

Giả sử `concurrentMap` là `ConcurrentHashMap<String, Integer>` đang được cập nhật. Bản `copy` có cấu trúc riêng sau khi tạo, nhưng dữ liệu đi vào nó vẫn được lấy trong lúc nguồn có thể thay đổi.

Vì vậy, cần tách hai thuộc tính:

- **Độc lập sau khi sao chép:** thay một ánh xạ của bản mới không tự thay ánh xạ nguồn.
- **Nhất quán lúc thu thập:** mọi ánh xạ đã thu có thuộc cùng một phiên bản nguồn không?

Thuộc tính thứ nhất không chứng minh thuộc tính thứ hai. Sắp xếp bản sao bằng `TreeMap` chỉ đổi cách trình bày thứ tự, không sửa lịch sử thu thập dữ liệu. Bọc bản sao bằng một view không cho sửa cũng không nâng mức nhất quán của nguồn.

Nếu bạn dừng/phối hợp mọi bên ghi rồi lấy bản sao, lập luận thay đổi: lúc đó nguồn không chuyển phiên bản trong khi thu thập. Điều cần chứng minh nằm ở cơ chế dừng/phối hợp, không ở tên constructor sao chép.

### 8.2. Lấy bản sao dưới một khóa được mọi đường truy cập tôn trọng

Ví dụ minh họa không độc lập:

```java
Map<String, Integer> shared =
        Collections.synchronizedMap(new HashMap<>());

Map<String, Integer> snapshot;
synchronized (shared) {
    snapshot = new HashMap<>(shared);
}
```

Mọi truy cập map nền phải đi qua cùng đối tượng bọc, và các công việc nhiều bước cần giữ đúng monitor cho toàn bộ công việc đó. Việc sao chép nằm trong khối khóa nên các bên sửa qua đối tượng bọc ở luồng khác phải chờ. Với các khóa `String` và giá trị `Integer` bất biến trong ví dụ, bản sao không chia sẻ các trường phần tử có thể sửa.

Tài liệu synchronized map yêu cầu giữ khóa map trả về trong suốt lượt duyệt các collection view; không khóa riêng `keySet` thay cho map. [Collections — synchronizedMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map))

Nếu nguồn là `ConcurrentHashMap`, tùy tiện `synchronized (concurrentMap)` quanh lúc chụp không khiến các luồng chỉ gọi `put` phải chờ monitor ngoài đó. Khóa chỉ phối hợp được những đường truy cập thực sự tuân thủ giao thức khóa. Một lớp bao riêng tư có thể giữ map không thoát ra ngoài và cung cấp các phương thức cập nhật/chụp dùng cùng khóa.

Khi cần ảnh chụp sâu, phải sao chép trạng thái phần tử trong lúc trạng thái đó cũng được bảo vệ. Sao chép từng đối tượng ở các thời điểm rời rạc không tự tạo tính nhất quán cho quan hệ giữa chúng.

### 8.3. Công bố trạng thái bất biến theo phiên bản

Một hướng khác là xây dựng trọn một trạng thái bất biến rồi công bố một tham chiếu tới phiên bản mới qua cơ chế đồng bộ phù hợp. Người đọc giữ một phiên bản trong suốt công việc.

Cách đó chỉ thuyết phục nếu dữ liệu liên quan thật sự được gom trong phiên bản và không có tham chiếu sửa được đi vòng. Nếu hai danh sách được công bố độc lập, mỗi danh sách có snapshot iterator vẫn chưa chứng minh chúng thuộc cùng một phiên bản nghiệp vụ.

Không có lựa chọn nào bắt buộc cho mọi ứng dụng. Hãy bắt đầu từ yêu cầu: theo dõi xấp xỉ trong lúc hoạt động, gửi cho tập đã chốt, hay đối chiếu dữ liệu cùng thời điểm. Sau đó chọn cơ chế có mức bảo đảm đủ cho yêu cầu ấy.

## 9. Không suy rộng hợp đồng iterator sang mọi cách truy cập

### 9.1. Vòng lặp chỉ số không phải iterator ảnh chụp

Đoạn minh họa dưới đây không tạo snapshot iterator, dù `names` là `CopyOnWriteArrayList<String>`:

```java
for (int i = 0; i < names.size(); i++) {
    System.out.println(names.get(i));
}
```

Mỗi `size` và `get` là một lần gọi riêng trên danh sách hiện tại. Giữa lúc kiểm tra `i < names.size()` và `get(i)`, một luồng khác có thể xóa phần tử, làm chỉ số không còn hợp lệ. Ngay cả khi không lỗi chỉ số, hai lần `get` có thể quan sát hai phiên bản khác nhau.

Vòng `for-each` hoặc một iterator đã tạo có chính sách khác vì nó giữ tiến trình trên nguồn mà hợp đồng iterator quy định. Đừng thay vòng lặp ảnh chụp bằng vòng chỉ số rồi cho rằng cùng mức bảo đảm.

### 9.2. View không cho sửa không có nghĩa là ảnh chụp

Bọc một danh sách sống bằng `Collections.unmodifiableList` chỉ chặn một số đường sửa thông qua view đó. Nó không tự tách dữ liệu khỏi danh sách nền, không tạo khóa cho bên ghi và không nâng iterator nền thành snapshot iterator. [Collections — unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List))

Tương tự, một `subList` là view với hợp đồng riêng. Đối với `CopyOnWriteArrayList.subList`, tài liệu nêu ngữ nghĩa view trở nên không xác định nếu danh sách nền bị sửa qua đường khác với view đó. Không được suy “list gốc có snapshot iterator” thành “mọi thao tác với mọi view đều giữ phiên bản cũ vô điều kiện”. [CopyOnWriteArrayList — subList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html#subList(int,int))

Hãy luôn xác định đối tượng thật sự đang dùng: list gốc, view, iterator đã tạo, hay bản sao độc lập.

### 9.3. `Spliterator` và stream cần đọc đúng điểm gắn với nguồn

Stream thường lấy dữ liệu qua `Spliterator`. Hai đặc tính hay gặp là:

- `CONCURRENT`: nguồn cho phép thay đổi đồng thời theo chính sách của nó.
- `IMMUTABLE`: nguồn mà spliterator đang duyệt không thể bị sửa cấu trúc trong lượt đó.

`CONCURRENT` không có nghĩa mọi luồng được tự do cùng gọi một instance spliterator, và không hứa bản chụp toàn cục. `IMMUTABLE` nói về nguồn/phiên bản mà spliterator duyệt, không tự chứng minh trạng thái bên trong từng đối tượng phần tử là bất biến. [Spliterator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Spliterator.html)

Ví dụ, spliterator của `CopyOnWriteArrayList` báo `IMMUTABLE` và giữ ảnh chụp tại lúc spliterator được tạo, dù đối tượng list vẫn cho phép thêm/xóa về sau. Spliterator của các view `ConcurrentHashMap` báo `CONCURRENT`. Đây là hai cách quản lý thay đổi nguồn khác nhau.

Không mặc định mọi `stream()` chốt dữ liệu ngay tại lúc gọi phương thức, hoặc mọi stream đều chờ tới terminal operation mới chọn nguồn. Điểm gắn với nguồn phụ thuộc nguồn và cách tạo spliterator/stream; có nguồn gắn muộn, có nguồn giữ ảnh chụp sớm.

Hàm trong chuỗi xử lý (pipeline) vẫn phải tuân thủ yêu cầu không can thiệp không hợp lệ vào nguồn và quản lý trạng thái đúng cách. Cho phép nguồn concurrent được cập nhật không làm mọi tác dụng phụ trong lambda an toàn. [java.util.stream — Non-interference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html#NonInterference)

Nếu mục tiêu bài toán chỉ cần một lượt duyệt tuần tự, nắm đúng `Iterator` trước. Không cần đổi sang parallel stream để “sửa” một yêu cầu nhất quán mà nguồn chưa đáp ứng.

### 9.4. Không chia sẻ một con trỏ duyệt chỉ vì collection an toàn

Một đối tượng iterator có trạng thái tiến trình, chẳng hạn vị trí hiện tại và phần tử cuối đã trả về. Khả năng cho phép **một luồng duyệt trong khi luồng khác sửa collection** không đồng nghĩa **hai luồng cùng dùng một iterator** là an toàn.

Cách thông thường là mỗi tác vụ có iterator riêng, hoặc chuyển việc phân chia phần tử cho một cơ chế được thiết kế cho việc đó. Nếu cần dùng chung con trỏ, phải có hợp đồng/giao thức đồng bộ riêng; không lấy tính an toàn của collection làm bằng chứng thay thế.

Điều tương tự áp dụng cho dữ liệu thu thập: một `ArrayList` cục bộ chỉ do một tác vụ ghi như trong thí nghiệm không thể được biến thành bộ chứa kết quả dùng chung mà không xem xét lại cơ chế phối hợp.

## 10. Chọn cơ chế theo yêu cầu quan sát

| Yêu cầu | Lựa chọn có thể phù hợp | Giới hạn cần chấp nhận hoặc giải quyết |
| --- | --- | --- |
| Duyệt bảng trạng thái trong khi cập nhật liên tục, chấp nhận quan sát các giai đoạn khác nhau | Iterator nhất quán yếu trên collection phù hợp | Không dùng kết quả như một ảnh chụp đối soát toàn cục |
| Chốt tập người nhận cho một lượt, số lần đăng ký thay đổi ít | Snapshot iterator của copy-on-write | Hủy đăng ký không tự rút phần tử khỏi ảnh chụp đang chạy |
| Duyệt nhiều, dữ liệu ít thay đổi, cần thứ tự danh sách | `CopyOnWriteArrayList` | Chi phí sao chép khi cập nhật và giữ phiên bản cũ |
| Tập nhỏ, đọc nhiều, không cần phần tử trùng | `CopyOnWriteArraySet` | Đánh giá chi phí tìm kiếm/cập nhật với kích thước thực tế |
| Báo cáo cần mọi số cùng phiên bản | Chụp dưới khóa chung hoặc công bố trạng thái bất biến | Mọi đường ghi và trạng thái phần tử phải cùng giao thức |
| Chỉ muốn cấm người gọi sửa qua một view | View không cho sửa | Không có snapshot/đồng bộ mới chỉ vì view không có quyền sửa |

Khi lựa chọn, hãy xác định mức cũ mà ứng dụng chấp nhận. Một ảnh chụp ổn định nhưng quá cũ có thể không phù hợp với yêu cầu điều khiển hiện thời. Một lượt duyệt mới hơn từng phần nhưng trộn phiên bản có thể không phù hợp với phép đối chiếu.

Hiệu năng cũng cần đúng dạng tải: số phần tử, tỷ lệ đọc/ghi, số lượt duyệt giữ đồng thời, thời gian xử lý mỗi phần tử và chi phí trạng thái phần tử. Không suy từ một vòng lặp nhỏ trong ví dụ thành kết luận một collection luôn nhanh nhất.

## 11. Luyện tập: xác định chính xác phạm vi bảo đảm

### Bài 1 — Xóa người nhận giữa lượt

Danh sách copy-on-write có A, B, C. Iterator được tạo; sau khi trả A, bên khác xóa B và thêm D. Các phần tử là chuỗi bất biến.

**Câu hỏi:** Iterator cũ bắt buộc phản ánh thao tác xóa/thêm mới không? Tạo một iterator mới sau cập nhật có cùng ý nghĩa với tiếp tục iterator cũ không?

**Đối chiếu:** Iterator cũ tiếp tục phiên bản A, B, C đã chốt; sau khi A đã trả về, phần còn lại là B, C. Iterator mới lấy phiên bản sau cập nhật. Hai lượt có thời điểm chụp khác nhau.

### Bài 2 — Ảnh chụp có phần tử thay đổi

Một snapshot iterator giữ tham chiếu `Order` có trường `status`. Sau khi tạo iterator, một luồng khác thay `status`.

**Câu hỏi:** Có thể khẳng định iterator luôn đọc trạng thái cũ không? Có thể khẳng định mọi lần đọc trường đã an toàn không?

**Đối chiếu:** Không thể khẳng định cả hai chỉ từ snapshot iterator. Ảnh chụp giữ tham chiếu, không chụp sâu trường. Cần xem tính bất biến và cơ chế đồng bộ của `Order`; nếu có quan hệ bất biến nhiều trường thì một trường volatile riêng lẻ chưa tự giải quyết toàn bộ.

### Bài 3 — Tổng từ concurrent map

Một luồng duyệt các giá trị trong `ConcurrentHashMap` để cộng tổng. Luồng khác đang di chuyển một đơn vị giữa hai khóa bằng hai cập nhật.

**Câu hỏi:** Không có `ConcurrentModificationException` có đủ chứng minh tổng thuộc cùng một phiên bản không?

**Đối chiếu:** Không. Iterator nhất quán yếu hỗ trợ lượt duyệt trong bối cảnh cập nhật, không biến hai lần sửa hay cả lượt cộng thành một giao dịch. Cần một giao thức giữ bất biến và cách đọc/chụp tương ứng nếu tổng phải có nghĩa tại một thời điểm.

### Bài 4 — Entry có ảnh chụp, iterator vẫn yếu

Một `ConcurrentSkipListMap` trả entry thứ nhất rồi entry thứ hai. Tài liệu gọi entry là snapshot của ánh xạ.

**Câu hỏi:** Có thể lấy đó làm bằng chứng hai entry thuộc cùng một phiên bản toàn map không?

**Đối chiếu:** Không. Mỗi entry có thời điểm được tạo riêng; ảnh chụp một ánh xạ không phải ảnh chụp toàn bộ lượt duyệt. Thứ tự khóa được sắp xếp cũng không gộp thời điểm quan sát.

### Bài 5 — Bài thử ép một kết quả không có trong hợp đồng

Một bài thử tạo iterator của concurrent map, thêm khóa `late`, rồi yêu cầu mọi lần chạy phải thấy `late`.

**Câu hỏi:** Bài thử đang xác minh điều gì sai?

**Đối chiếu:** Nó đã biến một thay đổi có thể được phản ánh thành một thay đổi bắt buộc được phản ánh. Nên kiểm tra những cam kết thật sự cần cho thuật toán hoặc đổi cơ chế nếu yêu cầu ứng dụng bắt buộc bao gồm khóa đó.

### Bài 6 — Cờ `IMMUTABLE`

Một spliterator của `CopyOnWriteArrayList` có cờ `IMMUTABLE`, nhưng chương trình vẫn gọi `list.add` thành công.

**Câu hỏi:** Hai điều đó có mâu thuẫn không?

**Đối chiếu:** Không. Cờ mô tả nguồn/phiên bản spliterator đang duyệt; đối tượng list hiện tại có thể chuyển sang phiên bản khác. Cờ đó cũng không đóng băng trường của mọi đối tượng phần tử.

## 12. Tài liệu để đối chiếu khi đổi collection

Các chương trình và tình huống trong bài được xây dựng để phân biệt cam kết về phiên bản quan sát. Khi áp dụng cho lớp khác, đọc chính sách iterator/spliterator và quyền sửa của đúng lớp đó:

- [java.util.concurrent Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html): nhất quán yếu và công bố dữ liệu.
- [CopyOnWriteArrayList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html), [CopyOnWriteArraySet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArraySet.html): snapshot iterator và giới hạn cập nhật.
- [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html), [ConcurrentLinkedQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedQueue.html), [ConcurrentSkipListMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListMap.html): cam kết của từng cấu trúc cụ thể.
- [Iterator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html), [Spliterator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Spliterator.html): con trỏ duyệt, thao tác tùy chọn và đặc tính nguồn.
- [Collections Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html): view và yêu cầu khóa đối tượng bọc đồng bộ.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-007">Vì sao fail-fast không phải cơ chế đồng bộ hoặc kiểm tra đúng đắn</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao fail-fast không phải cơ chế đồng bộ hoặc kiểm tra đúng đắn

Một vòng lặp chạy hết mà không có `ConcurrentModificationException` chỉ cho biết lần chạy đó đã không ném ngoại lệ ấy. Nó chưa trả lời được ba câu hỏi quan trọng: các luồng có truy cập dữ liệu theo một quy tắc an toàn không, vòng lặp đã quan sát tập dữ liệu nào, và kết quả có thỏa yêu cầu của chương trình không.

Trong Java Collections Framework, **fail-fast** là cách một số iterator phát hiện việc sửa đổi không phù hợp với lượt duyệt đang diễn ra và dừng bằng ngoại lệ. Cơ chế này giúp tìm lỗi sử dụng collection. Để xây dựng chương trình đa luồng đúng, vẫn phải chọn cơ chế đồng bộ, quy tắc quan sát dữ liệu và thao tác nguyên tử phù hợp.

Bài học đi từ một iterator trong một luồng đến trường hợp nhiều luồng cùng dùng collection. Các thuật ngữ cần phân biệt là:

- **Lượt duyệt:** quá trình sử dụng một iterator hoặc spliterator cụ thể để đọc các phần tử.
- **Sửa đổi cấu trúc:** thay đổi ảnh hưởng đến tổ chức collection và tính hợp lệ của lượt duyệt; tiêu chí cụ thể phụ thuộc lớp triển khai.
- **Đồng bộ:** thiết lập quyền truy cập và quan hệ nhìn thấy dữ liệu giữa các luồng.
- **Tính nguyên tử:** một thao tác cần được quan sát như một đơn vị, không bị thao tác cạnh tranh chen vào giữa các bước quan trọng.
- **Bất biến nghiệp vụ:** điều kiện mà chương trình phải giữ, chẳng hạn mỗi mã đăng ký chỉ xuất hiện một lần.
- **Chẩn đoán:** phát hiện dấu hiệu sai để hỗ trợ sửa chương trình; một dấu hiệu chẩn đoán không tự thực thi các điều kiện trên.

## 1. Hợp đồng thực sự của fail-fast

`ConcurrentModificationException` có thể xuất hiện khi một đối tượng phát hiện sửa đổi không được phép trong tình huống hiện tại. Chữ “Concurrent” trong tên ngoại lệ không chứng minh rằng có hai luồng: một luồng tự sửa collection trực tiếp trong khi giữ iterator cũ cũng có thể gây lỗi.

Javadoc nêu rõ việc phát hiện chỉ là **best effort**, nghĩa là cố gắng phát hiện trong khả năng của triển khai, không có bảo đảm phát hiện mọi trường hợp sửa đổi đồng thời thiếu đồng bộ. Không được lấy ngoại lệ này làm điều kiện bảo đảm chương trình đúng. [Java 17: ConcurrentModificationException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ConcurrentModificationException.html)

Ví dụ sau là một đoạn minh họa trong phương thức, với các kiểu từ `java.util`:

~~~java
List<String> names = new ArrayList<>(List.of("An", "Binh"));
Iterator<String> cursor = names.iterator();

names.add("Chi");
String next = cursor.next();
~~~

Iterator được tạo trước `add`. Lần `next` sau đó dùng một lượt duyệt đã bị thay đổi cấu trúc của `ArrayList` làm mất hiệu lực. Với triển khai được kiểm chứng trong chương trình bên dưới, `next` ném `ConcurrentModificationException`.

Cùng thao tác thêm ấy, nếu thực hiện trước khi tạo iterator thì iterator mới bắt đầu trên trạng thái mới. Vấn đề cần phân tích là quan hệ giữa sửa đổi và **tuổi thọ iterator**, không phải riêng tên phương thức `add`.

Hai suy luận thường gặp đều không hợp lệ:

~~~text
Có ConcurrentModificationException
    => chắc chắn hai luồng truy cập collection cùng lúc.

Không có ConcurrentModificationException
    => collection đã được dùng an toàn và kết quả đã đúng.
~~~

Suy luận thứ nhất sai vì lỗi có thể do một luồng. Suy luận thứ hai sai vì bộ phát hiện không kiểm tra mọi loại lỗi, mọi thao tác hay mọi yêu cầu nghiệp vụ.

## 2. Vì sao cơ chế phát hiện hữu ích nhưng không đủ

Giả sử danh sách lệnh cần xử lý là `[A, B, C]`. Sau khi xử lý `A`, một đoạn mã thêm `D` vào danh sách. Iterator cũ đang giữ vị trí, giả định về cấu trúc và các thông tin nội bộ của lượt duyệt trước đó.

Nếu tiếp tục như chưa có thay đổi, chương trình phải tự trả lời những câu hỏi khó: có đọc `D` không, chỉ số còn tương ứng với phần tử nào, phần tử đã bị xóa có cần xử lý nữa không? Một iterator fail-fast không cố tạo ra chính sách duyệt động cho mọi tình huống này. Khi phát hiện trạng thái không khớp, nó báo lỗi để người viết chương trình thiết kế lại cách sử dụng.

Giá trị của cơ chế nằm ở chỗ lỗi xuất hiện gần thao tác đang dùng iterator. Tuy nhiên:

- Nó không ngăn việc thêm `D` đã xảy ra.
- Nó không thu hồi công việc xử lý `A`.
- Nó không quyết định `D` thuộc lượt xử lý hiện tại hay lượt sau.
- Nó không biết yêu cầu của ứng dụng là xử lý một lần, ít nhất một lần, hay chỉ xử lý một bản chụp đầu vào.
- Nó không thay người viết chương trình chọn khóa hoặc loại collection phù hợp.

Đó là những quyết định thiết kế có trước việc bắt ngoại lệ.

## 3. Mô hình bộ đếm: hiểu để đọc lỗi, không dùng làm giao thức đồng bộ

Nhiều lớp kế thừa `AbstractList` dùng trường `modCount` để hỗ trợ phát hiện thay đổi cấu trúc. `AbstractList` cho phép lớp con sử dụng hoặc không sử dụng cơ chế này; nó không phải yêu cầu rằng mọi collection phải dùng cùng một bộ đếm. [Java 17: AbstractList.modCount](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html#modCount)

Có thể hình dung một lượt duyệt bằng mô hình sau. Đây là mô hình giải thích, không phải mã để thay thế iterator của JDK:

~~~text
Collection có dấu thay đổi: revision = 8
Iterator ghi nhớ:          expected = 8

Có sửa đổi được theo dõi:
    revision = 9

Đến một điểm kiểm tra của iterator:
    nếu revision khác expected thì báo lỗi
~~~

Số được ghi nhớ không phải bản sao toàn bộ dữ liệu. Việc hai giá trị bằng nhau cũng không thực hiện một phép kiểm chứng ngữ nghĩa trên từng phần tử.

Trong `ArrayList` của OpenJDK 17, iterator giữ một giá trị mong đợi và kiểm tra ở những đường đi nhất định. `hasNext` dùng vị trí hiện tại và kích thước, trong khi `next` có đường kiểm tra thay đổi. Vì vậy không thể suy từ tên “fail-fast” rằng mọi lời gọi đều kiểm tra ngay lập tức. Đây là chi tiết triển khai để lý giải quan sát, không phải hợp đồng áp dụng cho tất cả iterator. [Mã nguồn OpenJDK 17: ArrayList.Itr](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/ArrayList.java)

### 3.1. Bộ đếm không phải khóa

So sánh hai số không buộc luồng ghi phải chờ. Một luồng khác vẫn có thể thay đổi dữ liệu ở khoảng giữa lần kiểm tra và lần sử dụng dữ liệu. Nếu các truy cập vốn thiếu đồng bộ, chính việc đọc bộ đếm cũng không tự tạo một quan hệ công bố dữ liệu đầy đủ.

Ngược lại, khóa chung xác định đoạn mã nào được vào vùng bảo vệ và khi nào. Theo mô hình bộ nhớ Java, mở khóa một monitor xảy ra trước về quan hệ nhìn thấy dữ liệu đối với lần khóa tiếp theo trên cùng monitor. `volatile` và các công cụ trong `java.util.concurrent` cũng có hợp đồng riêng. So sánh một trường số thông thường không có những tác dụng này. [JLS 17, mục 17.1 và 17.4.5](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html)

Ngay cả ý tưởng “đổi bộ đếm thành `volatile`” cũng chưa giải quyết bài toán: nhìn thấy một số mới không làm cho thao tác kiểm tra rồi đọc nhiều phần tử trở thành một đơn vị nguyên tử. Cần thiết kế giao thức truy cập, không chỉ làm dấu hiệu thay đổi dễ nhìn thấy hơn.

### 3.2. Nội dung quay lại như cũ không phục hồi iterator cũ

Bắt đầu với `[A, B]`, tạo iterator, thêm `TEMP` rồi xóa `TEMP`. Danh sách cuối lại là `[A, B]`. Điều này không có nghĩa iterator đã hợp lệ trở lại.

Tình huống ấy phân biệt **bằng nhau về nội dung ở hai thời điểm** với **không có sửa đổi trong khoảng thời gian đó**. Cơ chế theo dõi thay đổi không phải phép so sánh danh sách cuối cùng với một bản sao ban đầu. Chương trình đầy đủ sẽ cho thấy iterator cũ vẫn báo lỗi.

Không nên đọc hoặc sửa bộ đếm nội bộ để “hợp thức hóa” iterator. Ứng dụng phải tuân thủ API và chính sách sửa đổi của collection đang dùng.

### 3.3. “Không đổi kích thước” không phải quy tắc tổng quát

Với `ArrayList`, việc thay tham chiếu ở một vị trí bằng `set` không được xem là sửa đổi cấu trúc theo mô tả lớp. Trong khi đó những thao tác quản lý cấu trúc khác có thể ảnh hưởng đến lượt duyệt mà không thể được đánh giá chỉ bằng `size` trước và sau. [Java 17: ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

Do đó, cách kiểm tra bằng `oldSize == list.size()` không phải bản thay thế cho hợp đồng iterator. Cũng không suy rộng quy tắc `set` của `ArrayList` cho mọi collection, mọi view hay mọi thuật toán.

## 4. Ba tầng phải được đánh giá độc lập

| Tầng cần đánh giá | Câu hỏi cụ thể | Fail-fast trả lời được gì? |
|---|---|---|
| An toàn truy cập | Có các truy cập đọc/ghi xung đột thiếu đồng bộ không? | Không kiểm chứng toàn bộ giao thức giữa các luồng. |
| Hợp đồng duyệt | Iterator này cho phép những sửa đổi nào? | Có thể phát hiện một số vi phạm theo chính sách của lớp. |
| Đúng yêu cầu | Có bỏ sót, xử lý trùng, sai tổng, sai điều kiện duy nhất không? | Không biết bất biến hay ý nghĩa nghiệp vụ. |

Một chương trình có thể đạt tầng đầu nhưng vi phạm tầng thứ hai. Chẳng hạn tất cả thao tác nằm trong cùng một khối khóa, nhưng chính luồng giữ khóa gọi `list.add` rồi tiếp tục dùng iterator cũ.

Một chương trình khác có thể dùng các phương thức riêng lẻ an toàn giữa nhiều luồng nhưng vi phạm tầng thứ ba: hai luồng đều kiểm tra “chưa có” rồi cùng thêm một mã. Nếu không có lượt duyệt nào trong đoạn này thì không có lý do để chờ một bộ phát hiện của iterator can thiệp.

Để chứng minh tính đúng, cần nói rõ **điều gì phải luôn đúng**, **thao tác nào duy trì điều đó**, và **các truy cập cạnh tranh được phối hợp ra sao**. Việc không thấy ngoại lệ không thay thế ba phần ấy.

## 5. Một chương trình kiểm chứng các ranh giới

Tệp `FailFastBoundaryDemo.java` dưới đây là chương trình chạy độc lập, dùng API có trong Java 17. Chương trình đã được biên dịch bằng `javac --release 17` và chạy trên OpenJDK Zulu 24.0.2; đây không phải tuyên bố đã chạy trên một JVM 17.

Các phần cố ý làm sai hợp đồng iterator được giới hạn trong những trường hợp có thứ tự rõ ràng. Chương trình không tạo một cuộc đua đọc/ghi tự do trên `ArrayList` rồi khẳng định stdout của cuộc đua phải cố định.

Phần nhiều luồng dùng `CyclicBarrier` để buộc cả hai luồng kiểm tra xong trước khi thêm. Những lần truy cập danh sách ở đó đi qua `Collections.synchronizedList`. Bởi vậy kết quả trùng có thể giải thích bằng lịch thực thi của các lời gọi hợp lệ riêng lẻ, không cần dựa vào hỏng cấu trúc nội bộ.

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class FailFastBoundaryDemo {
    @FunctionalInterface
    interface Action {
        void run() throws Exception;
    }

    static String outcome(Runnable action) {
        try {
            action.run();
            return "completed";
        } catch (ConcurrentModificationException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    static void runPair(Action action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable task = () -> {
            try {
                action.run();
            } catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            }
        };
        Thread first = new Thread(task, "first");
        Thread second = new Thread(task, "second");
        first.setDaemon(true);
        second.setDaemon(true);
        first.start();
        second.start();
        first.join(5000);
        second.join(5000);
        if (first.isAlive() || second.isAlive()) {
            first.interrupt();
            second.interrupt();
            throw new AssertionError("Pair did not finish");
        }
        if (failure.get() != null) {
            throw new AssertionError("Task failed", failure.get());
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> direct = new ArrayList<>(List.of("A", "B"));
        Iterator<String> directIt = direct.iterator();
        direct.add("C");
        System.out.println("direct = " + outcome(() -> directIt.next()));

        List<String> removed = new ArrayList<>(List.of("A", "B", "C"));
        Iterator<String> removeIt = removed.iterator();
        while (removeIt.hasNext()) {
            if (removeIt.next().equals("B")) {
                removeIt.remove();
            }
        }
        System.out.println("iterator-remove = " + removed);

        List<String> guarded =
                Collections.synchronizedList(new ArrayList<>(List.of("P", "Q")));
        synchronized (guarded) {
            Iterator<String> guardedIt = guarded.iterator();
            guarded.add("R");
            System.out.println("guarded-direct = "
                    + outcome(() -> guardedIt.next()));
        }

        List<String> restored = new ArrayList<>(List.of("A", "B"));
        Iterator<String> restoredIt = restored.iterator();
        restored.add("TEMP");
        restored.remove("TEMP");
        System.out.println("restored-content = " + restored);
        System.out.println("restored-iterator = "
                + outcome(() -> restoredIt.next()));

        List<String> replaced = new ArrayList<>(List.of("old", "tail"));
        Iterator<String> replaceIt = replaced.iterator();
        replaced.set(0, "new");
        System.out.println("set-next = " + replaceIt.next());

        StringBuilder box = new StringBuilder("safe");
        List<StringBuilder> boxes = new ArrayList<>(List.of(box));
        Iterator<StringBuilder> boxIt = boxes.iterator();
        box.append("?");
        System.out.println("element-next = " + boxIt.next());

        List<String> shortened = new ArrayList<>(List.of("A", "B"));
        Iterator<String> shortIt = shortened.iterator();
        System.out.println("shortcut-first = " + shortIt.next());
        shortened.remove("B");
        System.out.println("shortcut-hasNext = " + shortIt.hasNext());

        List<String> ordered = new ArrayList<>(List.of("L", "R"));
        Iterator<String> orderedIt = ordered.iterator();
        AtomicReference<Throwable> writerFailure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                ordered.add("S");
            } catch (Throwable ex) {
                writerFailure.set(ex);
            }
        }, "ordered-writer");
        writer.setDaemon(true);
        writer.start();
        writer.join(5000);
        if (writer.isAlive()) {
            writer.interrupt();
            throw new AssertionError("Writer did not finish");
        }
        if (writerFailure.get() != null) {
            throw new AssertionError("Writer failed", writerFailure.get());
        }
        System.out.println("after-join = " + outcome(() -> orderedIt.next()));

        List<Integer> duplicate =
                Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier checked = new CyclicBarrier(2);
        runPair(() -> {
            boolean absent = !duplicate.contains(42);
            checked.await(5, TimeUnit.SECONDS);
            if (absent) {
                duplicate.add(42);
            }
        });
        System.out.println("separate-calls = " + duplicate);
        System.out.println("unique-invariant = " + (duplicate.size() == 1));

        List<Integer> unique =
                Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier ready = new CyclicBarrier(2);
        runPair(() -> {
            ready.await(5, TimeUnit.SECONDS);
            synchronized (unique) {
                if (!unique.contains(42)) {
                    unique.add(42);
                }
            }
        });
        System.out.println("whole-operation = " + unique);
        System.out.println("unique-invariant-fixed = " + (unique.size() == 1));

        List<String> jobs = new ArrayList<>(List.of("A", "B", "C"));
        List<String> delivered = new ArrayList<>();
        boolean interrupted = false;
        Iterator<String> jobIt = jobs.iterator();
        try {
            while (jobIt.hasNext()) {
                String job = jobIt.next();
                delivered.add(job);
                if (job.equals("A")) {
                    jobs.add("D");
                }
            }
        } catch (ConcurrentModificationException ex) {
            interrupted = true;
        }
        System.out.println("retry-caught = " + interrupted);
        System.out.println("effects-before-retry = " + delivered);
        for (String job : jobs) {
            delivered.add(job);
        }
        System.out.println("effects-after-retry = " + delivered);

        CopyOnWriteArrayList<String> snapshots =
                new CopyOnWriteArrayList<>(List.of("U", "V"));
        Iterator<String> snapshotIt = snapshots.iterator();
        snapshots.add("W");
        List<String> snapshotRead = new ArrayList<>();
        snapshotIt.forEachRemaining(snapshotRead::add);
        System.out.println("snapshot-iterator = " + snapshotRead);
        System.out.println("snapshot-current = " + snapshots);
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 FailFastBoundaryDemo.java
java FailFastBoundaryDemo
~~~

Stdout của lần kiểm chứng:

~~~text
direct = ConcurrentModificationException
iterator-remove = [A, C]
guarded-direct = ConcurrentModificationException
restored-content = [A, B]
restored-iterator = ConcurrentModificationException
set-next = new
element-next = safe?
shortcut-first = A
shortcut-hasNext = false
after-join = ConcurrentModificationException
separate-calls = [42, 42]
unique-invariant = false
whole-operation = [42]
unique-invariant-fixed = true
retry-caught = true
effects-before-retry = [A]
effects-after-retry = [A, A, B, C, D]
snapshot-iterator = [U, V]
snapshot-current = [U, V, W]
~~~

Riêng dòng `shortcut-hasNext = false` là quan sát trên triển khai đã chạy và phù hợp với cách viết `ArrayList.Itr` trong OpenJDK 17 đã đối chiếu. Một triển khai hoặc phiên bản khác có thể kiểm tra ở điểm khác. Không viết ứng dụng dựa vào việc trường hợp vi phạm hợp đồng này sẽ im lặng.

Các thời hạn năm giây giúp chương trình kiểm chứng báo lỗi nếu phối hợp bị kẹt; chúng không dùng để quyết định luồng nào thắng. `Thread.sleep` không tham gia thiết lập lịch thực thi. Kết quả chỉ được in sau khi các luồng tương ứng đã kết thúc; lỗi trong luồng phụ được chuyển về luồng chính thay vì bị bỏ qua.

## 6. Đọc từng nhóm kết quả

### 6.1. Một luồng cũng đủ gây ngoại lệ

`direct` tạo iterator rồi thêm một phần tử bằng chính danh sách. Lời gọi `next` sau đó báo `ConcurrentModificationException`. Không có luồng phụ trong tình huống này.

Ngược lại, `iterator-remove` xóa `B` qua chính iterator đang duyệt và còn `[A, C]`. Đây là đường sửa đổi mà iterator của `ArrayList` hỗ trợ. Với giao diện `Iterator` nói chung, `remove` là thao tác tùy chọn, có điều kiện gọi sau `next` và không được gọi hai lần cho cùng phần tử vừa trả về. Không được giả định mọi iterator đều hỗ trợ xóa. [Java 17: Iterator.remove](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#remove())

Thay `removeIt.remove()` bằng `removed.remove("B")` là thay đổi đường sửa collection. Dù ý định vẫn là xóa `B`, hợp đồng đối với iterator hiện tại đã khác.

Nếu có hai iterator trên cùng `ArrayList`, việc sửa hợp lệ qua iterator thứ nhất cũng không tự cập nhật trạng thái mong đợi của iterator thứ hai. Quyền sửa thuộc lượt duyệt đang thực hiện thao tác, không phải quyền miễn kiểm tra cho mọi lượt duyệt đang tồn tại.

### 6.2. Khóa đúng vẫn không cho phép tùy ý sửa trong lúc duyệt

Trong `guarded-direct`, một luồng giữ monitor của danh sách bọc đồng bộ, tạo iterator và thêm `R`. Thao tác thêm vẫn thực hiện được bởi monitor Java có tính tái nhập: chính luồng đang giữ khóa có thể vào lại đoạn mã dùng cùng khóa.

Khóa giữ các luồng tuân thủ cùng giao thức ở ngoài vùng bảo vệ. Nó không biến `guarded.add("R")` thành `guardedIt.add`, cũng không làm iterator được tạo trước đó quên lịch sử thay đổi.

Đây là tình huống hay xuất hiện qua hàm gọi lại: vòng lặp đang giữ khóa gọi một hàm xử lý phần tử; hàm ấy lại thêm hoặc xóa collection. Khi xem stack trace, cần lần theo cả các hàm được gọi gián tiếp, không chỉ nhìn thân vòng lặp.

Chọn một trong các hướng có ý nghĩa với bài toán:

- Xóa bằng iterator nếu thao tác đó được hỗ trợ và phù hợp.
- Thu thập các thay đổi dự định rồi áp dụng sau khi lượt duyệt kết thúc.
- Duyệt một bản chụp đã lấy an toàn, chấp nhận bản chụp phản ánh thời điểm lấy dữ liệu.
- Chọn cấu trúc có chính sách duyệt cho phép kiểu cập nhật cần dùng.

Mỗi hướng thay đổi cách xử lý dữ liệu, vì vậy cần kiểm tra lại yêu cầu của ứng dụng.

### 6.3. Có thay đổi nhưng không nhất thiết có tín hiệu fail-fast

`set-next = new` xuất hiện vì việc `set` thay tham chiếu trong danh sách này không làm mất hiệu lực iterator theo cơ chế đang xét. Iterator trả về giá trị mới ở vị trí chưa đọc. Điều này không biến việc nhiều luồng đồng thời `set` và `get` trên `ArrayList` thành an toàn.

`element-next = safe?` cho thấy một giới hạn khác: collection chứa tham chiếu đến `StringBuilder`. Sửa nội dung của chính đối tượng ấy không phải thao tác thêm hoặc xóa phần tử trong collection. Iterator không phải bộ theo dõi mọi trường của mọi đối tượng mà nó có thể trả về.

Ví dụ sửa `StringBuilder` chỉ có một luồng. Khi chuyển sang nhiều luồng, tính an toàn của phần tử phải được thiết kế riêng; collection an toàn không tự làm mọi đối tượng nằm trong nó an toàn.

`shortcut-hasNext = false` minh họa điểm kiểm tra có thể không được đi tới. Sau khi lấy `A`, chương trình xóa `B` trực tiếp; vị trí iterator lúc đó trùng kích thước mới. Đường `hasNext` được quan sát trả `false`, nên một vòng lặp kiểu thông thường có thể kết thúc mà không gọi `next` thêm lần nào.

Điều cần học từ quan sát này là **không được dùng sự im lặng làm giấy chứng nhận**, chứ không phải học một thủ thuật sửa danh sách để né ngoại lệ.

### 6.4. Có hai luồng nhưng không cần có truy cập chồng lấn

Trong `after-join`, iterator được tạo trước khi khởi động luồng ghi. Luồng ghi thêm `S` rồi kết thúc; luồng chính đợi bằng `join` và chỉ sau đó gọi `next`.

Không có lượt đọc cấu trúc danh sách của luồng chính diễn ra đồng thời với thao tác thêm trong ví dụ này. Dù vậy, iterator cũ vẫn được sử dụng sau một sửa đổi không đi qua nó và báo lỗi.

Quan hệ sau khi luồng kết thúc qua `join` là cơ sở công bố kết quả của luồng ghi cho luồng chính. Ngoại lệ ở đây không chứng minh `join` thiếu đồng bộ; nó phản ánh tuổi thọ iterator vượt qua sửa đổi không phù hợp. [JLS 17.4.5: Thread.start và Thread.join](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5)

Đây cũng là lý do không nên dịch máy móc tên ngoại lệ thành kết luận “đã có hai câu lệnh chạy đúng cùng một thời điểm”.

### 6.5. Không có ngoại lệ nhưng bất biến vẫn sai

`separate-calls` có lịch thực thi bị điều khiển như sau:

~~~text
Luồng thứ nhất: contains(42) -> false
Luồng thứ hai:  contains(42) -> false
Cả hai qua hàng rào
Luồng thứ nhất: add(42)
Luồng thứ hai:  add(42)
Kết quả: [42, 42]
~~~

Thứ tự hai lần thêm có thể đổi, nhưng cả hai đều thêm cùng giá trị, nên kết quả vẫn là hai số `42`. Mỗi lời gọi qua wrapper được bảo vệ riêng. Điều không được bảo vệ là **toàn bộ quyết định kiểm tra rồi thêm**.

Ở đây không tạo iterator, không xảy ra `ConcurrentModificationException` và `unique-invariant = false`. Thất bại được chứng minh bằng điều kiện dữ liệu mong muốn, không bằng việc chờ ngoại lệ.

Phần `whole-operation` đặt cả `contains` và `add` vào một khối `synchronized` chung. Luồng vào sau kiểm tra lại trong vùng bảo vệ và thấy phần tử đã có. Do đó chỉ còn `[42]`. Hàng rào được đặt **trước** khối khóa; nếu đặt chờ cả hai luồng bên trong cùng khóa thì một luồng có thể chờ luồng còn lại trong khi đang ngăn nó vào.

### 6.6. Ngoại lệ không hoàn tác tác dụng đã phát sinh

Trong phần `jobs`, phần tử `A` đã được thêm vào `delivered` trước khi danh sách nguồn được thêm `D` và iterator phát hiện lỗi. Vì vậy sau khi bắt ngoại lệ:

~~~text
effects-before-retry = [A]
~~~

Khi chương trình cố ý duyệt lại từ đầu, `A` được thêm lần nữa:

~~~text
effects-after-retry = [A, A, B, C, D]
~~~

`delivered` là mô hình cho một tác dụng có thể quan sát: gửi thông báo, ghi file, cập nhật hệ thống khác hoặc cộng số liệu. Ngoại lệ của iterator không tự đảo các tác dụng đó. Khối `synchronized` cũng không cung cấp cơ chế hoàn tác.

Không thể biến cách “bắt lỗi rồi chạy lại” thành đúng chỉ bằng giới hạn số lần thử. Phải định nghĩa đầu vào của một lần xử lý, cách nhận diện việc đã làm, tính lặp an toàn của thao tác và cơ chế giao dịch nếu bài toán có yêu cầu ấy.

### 6.7. Iterator không fail-fast có thể là lựa chọn hợp lệ

`CopyOnWriteArrayList` cung cấp iterator theo bản chụp mảng tại lúc tạo. Thay đổi danh sách sau đó không cập nhật mảng mà iterator ấy dùng; các thao tác thay đổi qua iterator không được hỗ trợ. [Java 17: CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)

Vì vậy chương trình in:

~~~text
snapshot-iterator = [U, V]
snapshot-current = [U, V, W]
~~~

Không có ngoại lệ ở đây phù hợp với một chính sách duyệt đã được định nghĩa. Tuy nhiên nếu yêu cầu của ứng dụng là “lượt duyệt phải xử lý cả `W` vừa được thêm” thì bản chụp cũ không đáp ứng yêu cầu ấy. Chọn collection an toàn vẫn phải kiểm tra ngữ nghĩa quan sát dữ liệu.

## 7. Chọn cơ chế đúng theo yêu cầu

### 7.1. Dữ liệu chỉ thuộc một luồng

Cách dễ suy luận nhất trong nhiều tình huống là giới hạn quyền sở hữu: danh sách tạm chỉ do một luồng xây dựng và duyệt. Khi cần chuyển dữ liệu sang luồng khác, bàn giao kết quả bằng cơ chế công bố phù hợp và quy định rõ bên nào còn được sửa.

Giới hạn quyền sở hữu không loại bỏ lỗi tự sửa collection khi đang dùng iterator. Với thao tác xóa chọn lọc trong một luồng, có thể dùng iterator có hỗ trợ `remove` hoặc một API lọc phù hợp.

Đoạn minh họa sau nằm trong phương thức và cần `java.util.ArrayList` cùng `java.util.List`:

~~~java
List<Integer> values = new ArrayList<>(List.of(1, 2, 3, 4));
values.removeIf(value -> value % 2 == 0);
// Sau lời gọi: values chứa [1, 3].
~~~

Đây là lời gọi cập nhật do collection thực hiện theo hợp đồng API, không phải cho phép hàm điều kiện tự thêm hoặc xóa cùng nguồn. Với collection thông thường, cũng không được suy rằng `removeIf` tự tạo cơ chế phối hợp với các luồng khác.

### 7.2. Cần một lượt duyệt ổn định trên collection có thể bị sửa

Nếu dùng wrapper `Collections.synchronizedList`, việc duyệt bằng iterator, spliterator hoặc stream cần nằm trong khóa của chính wrapper theo hợp đồng API. Các bên truy cập phải tuân thủ cùng đường đồng bộ. [Java 17: Collections.synchronizedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedList(java.util.List))

Ví dụ minh họa trong phương thức, với các kiểu từ `java.util`:

~~~java
List<Integer> shared =
        Collections.synchronizedList(new ArrayList<>(List.of(10, 20, 30)));

int total = 0;
synchronized (shared) {
    for (int value : shared) {
        total += value;
    }
}
// total là 60 đối với dữ liệu khởi tạo trên.
~~~

Khối khóa phải bao trùm việc tạo và sử dụng iterator, không chỉ lời gọi `iterator()`. Một bên khác giữ tham chiếu đến `ArrayList` gốc và sửa trực tiếp sẽ phá giao thức dù vòng lặp đã khóa wrapper.

Với view của map hoặc danh sách con, phải xác định mutex gốc mà API yêu cầu. Khóa một đối tượng bất kỳ có cùng nội dung không tạo phối hợp với bên ghi.

Ngay cả khi giữ khóa đúng, tránh để hàm gọi lại tự sửa cấu trúc qua đường không phù hợp với iterator hiện tại. Đồng thời xem xét chi phí giữ khóa, thứ tự lấy nhiều khóa và công việc có thể chặn trong thân xử lý.

### 7.3. Cần giải phóng khóa trước khi xử lý lâu

Có thể lấy một bản sao dưới khóa, rồi xử lý bản sao bên ngoài:

~~~java
List<Integer> snapshot;
synchronized (shared) {
    snapshot = new ArrayList<>(shared);
}

for (int value : snapshot) {
    // Xử lý dữ liệu của bản sao này.
}
~~~

Đoạn trên tiếp tục dùng `shared` đã khai báo ở ví dụ trước. Bản sao là một danh sách khác; việc thêm hoặc xóa phần tử ở nguồn sau lúc sao chép không đổi thành viên trong bản sao.

Đây là quyết định về thời điểm quan sát. Nó không cam kết rằng dữ liệu vẫn còn hiện hành lúc gửi kết quả ra ngoài. Nếu phần tử là đối tượng có thể sửa, hai danh sách vẫn có thể chứa cùng tham chiếu; sao chép danh sách không tự chụp toàn bộ đồ thị đối tượng.

Quan trọng hơn, thao tác lấy bản sao cũng phải an toàn. Viết `new ArrayList<>(unsafeSource)` trong lúc luồng khác sửa `unsafeSource` mà không phối hợp không phải cách tự sinh ra một bản chụp nhất quán.

### 7.4. Cần duyệt đồng thời với cập nhật

Có thể chọn collection đồng thời có chính sách duyệt thích hợp. Ví dụ iterator của `ConcurrentHashMap` không ném `ConcurrentModificationException` do cập nhật map và có tính nhất quán yếu; nó không hứa một bản chụp nguyên khối của toàn map. Iterator vẫn được thiết kế cho một luồng sử dụng tại một thời điểm. [Java 17: ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

Điều này có thể phù hợp cho thống kê giám sát chấp nhận dữ liệu biến động. Nó có thể không phù hợp cho việc xuất một báo cáo bắt buộc mọi dòng thuộc cùng một phiên bản dữ liệu. Muốn điều kiện mạnh hơn phải có thiết kế bổ sung hoặc chọn mô hình khác.

`CopyOnWriteArrayList` thích hợp khi việc duyệt nhiều hơn sửa và chi phí sao chép khi cập nhật chấp nhận được. Bản chụp chỉ ổn định đối với thành viên/tham chiếu trong mảng của iterator; tính biến đổi bên trong phần tử vẫn là câu hỏi riêng.

Không đánh giá chất lượng hai loại iterator bằng việc loại nào ném nhiều ngoại lệ hơn. Hãy đối chiếu chính sách duyệt với nhu cầu của người sử dụng dữ liệu.

### 7.5. Cần thao tác nguyên tử theo khóa dữ liệu

Với map đồng thời, sử dụng API có hợp đồng thích hợp cho một khóa như `putIfAbsent`, `replace` có điều kiện hoặc phương thức cập nhật được lớp cụ thể bảo đảm. Việc viết hai lời gọi `get` rồi `put` vẫn có khoảng giữa cho thao tác khác chen vào. [Java 17: ConcurrentMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html)

Ví dụ minh họa độc lập trong phương thức:

~~~java
java.util.concurrent.ConcurrentMap<String, String> registrations =
        new java.util.concurrent.ConcurrentHashMap<>();

String previous = registrations.putIfAbsent("id-7", "Lan");
boolean created = previous == null;
// Trong ví dụ khởi tạo rỗng này: created là true.
~~~

Nếu quy tắc yêu cầu cập nhật nhiều khóa, hai collection hoặc cả collection và một hệ thống ngoài, một thao tác nguyên tử trên một khóa chưa bảo vệ toàn bộ quy tắc đó.

Tương tự, nếu dữ liệu là tập hợp duy nhất, một `Set` có hợp đồng đồng thời thích hợp có thể diễn đạt yêu cầu trực tiếp hơn danh sách cộng một phép kiểm tra riêng. Việc chọn cấu trúc phải đi cùng yêu cầu và các thao tác được hỗ trợ.

## 8. Những cách “sửa” chỉ che triệu chứng

### 8.1. Bắt rồi bỏ qua ngoại lệ

~~~java
try {
    // Duyệt và xử lý.
} catch (java.util.ConcurrentModificationException ex) {
    // Bỏ qua.
}
~~~

Đây là đoạn minh họa cách xử lý sai, không phải chương trình hoàn chỉnh. Nó chuyển một lỗi có thể quan sát thành một lượt xử lý bị dừng nhưng trông như thành công. Nếu hàm trả kết quả một phần, bên nhận có thể không biết đã bị bỏ sót dữ liệu.

Có thể bắt ngoại lệ tại ranh giới thích hợp để ghi chẩn đoán hoặc đánh dấu lần xử lý thất bại. Điều đó khác với coi lỗi đã được chữa và tiếp tục trả kết quả như bình thường. Tài nguyên vẫn phải được đóng theo cấu trúc quản lý tài nguyên tương ứng.

### 8.2. Đổi sang vòng lặp chỉ số để hết lỗi

~~~java
for (int index = 0; index < list.size(); index++) {
    consume(list.get(index));
}
~~~

Đoạn minh họa trên giả sử `list` và `consume` đã có. Nó không tạo iterator fail-fast, nhưng các lần `size` và `get` vẫn là những bước khác nhau. Nếu phần tử bị xóa, chỉ số có thể bị dịch, dữ liệu có thể bị bỏ qua hoặc đọc không phù hợp; nếu nhiều luồng cùng truy cập thiếu phối hợp thì vấn đề đồng bộ vẫn còn.

Đổi loại vòng lặp chỉ có giá trị khi thuật toán mới thực sự có hợp đồng sửa đổi và bảo vệ phù hợp. Việc ít thấy ngoại lệ hơn tự nó không phải cải thiện tính đúng.

### 8.3. Cho luồng ngủ để “tránh đụng nhau”

Thêm độ trễ chỉ thay đổi lịch thực thi trong vài lần chạy. Nó không chứng minh rằng luồng ghi đã hoàn tất hoặc các ghi đã được công bố bằng quan hệ cần thiết.

Khi cần chờ một sự kiện, dùng công cụ phối hợp có hợp đồng rõ như `join`, latch hoặc future phù hợp với bài toán. Trong kiểm thử, dùng hàng rào/latch để điều khiển điểm giao nhau; không biến thời gian chờ ngẫu nhiên thành lập luận bảo đảm.

### 8.4. Đặt `volatile` lên biến tham chiếu collection

Một tham chiếu `volatile List<?>` có thể tham gia công bố đối tượng hoặc thay một bản dữ liệu theo giao thức phù hợp. Nó không làm cho các thao tác thêm/xóa bên trong cùng một `ArrayList` trở nên nguyên tử, cũng không tự bảo vệ phần tử có thể sửa.

Nếu chọn mô hình thay toàn bộ bản dữ liệu, phải quy định bản đã công bố có tiếp tục bị sửa không và người đọc dùng một bản nào trong suốt thao tác. Không trộn ý tưởng thay bản dữ liệu với việc nhiều luồng sửa tự do cùng một bản.

### 8.5. Đổi tên cơ chế thành “fail-safe”

Tài liệu Java mô tả iterator theo hợp đồng cụ thể như fail-fast, bản chụp hoặc nhất quán yếu. “Fail-safe” thường được dùng không chính thức trong trao đổi, nhưng một nhãn đó không đủ để suy ra chính sách quan sát, quyền xóa hay tính nguyên tử. Tài liệu package mô tả rõ nhóm iterator nhất quán yếu và các đặc điểm của collection đồng thời. [Java 17: java.util.concurrent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html)

Khi đọc một hướng dẫn dùng từ này, hãy chuyển nó thành câu hỏi cụ thể: iterator lấy dữ liệu lúc nào, cập nhật nào có thể xuất hiện, có hỗ trợ `remove` không, và có thể chia sẻ cùng iterator cho nhiều luồng không?

## 9. Stream và spliterator vẫn cần hợp đồng nguồn dữ liệu

Thay vòng `for-each` bằng `stream().forEach(...)` không tự thêm khóa quanh nguồn. Stream yêu cầu các hàm hành vi không can thiệp không phù hợp vào nguồn trong lúc thực thi; đối với nguồn đồng thời, chính sách nguồn có thể cho phép cập nhật nhưng vẫn phải hiểu kết quả mong đợi. [Java 17: quy tắc non-interference của Stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html#NonInterference)

Vì xử lý stream có thể trì hoãn đến thao tác kết thúc, cần phân biệt thời điểm dựng pipeline và thời điểm nguồn được duyệt. Nếu dùng wrapper đồng bộ và dựa vào khóa ngoài, việc chỉ khóa lúc tạo stream rồi thực thi thao tác kết thúc bên ngoài khóa không bảo vệ lượt duyệt.

Spliterator cũng có chính sách ràng buộc nguồn và phát hiện can thiệp riêng. Với thao tác duyệt hàng loạt, việc phát hiện có thể được dồn đến một điểm kiểm tra sau khi đã gọi hàm xử lý nhiều phần tử. Do đó, ngoại lệ không chứng minh rằng chưa có tác dụng nào phát sinh. [Java 17: Spliterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Spliterator.html)

`parallelStream` thêm khả năng có nhiều tác vụ cùng xử lý. Nó không sửa một nguồn thiếu an toàn và không tự làm một danh sách kết quả bên ngoài trở thành nơi ghi đồng thời hợp lệ. Khi cần kết quả tổng hợp, dùng phép giảm hoặc bộ thu có hợp đồng phù hợp, đồng thời bảo đảm nguồn được dùng đúng.

## 10. Điều tra lỗi và kiểm chứng điều kiện đúng

Khi gặp `ConcurrentModificationException`, đi từ đối tượng đang được duyệt đến mọi đường sửa nó:

1. Xác định lớp triển khai thực, wrapper và view, thay vì chỉ nhìn kiểu khai báo `List` hay `Map`.
2. Xác định iterator được tạo ở đâu, có bị lưu lại qua nhiều lời gọi hay qua một lần cập nhật không.
3. Tìm việc sửa trực tiếp, sửa qua alias, qua collection gốc của view và qua hàm gọi lại.
4. Phân biệt sửa do cùng luồng với sửa do luồng khác; tên ngoại lệ không cho sẵn câu trả lời.
5. Kiểm tra giao thức khóa, công bố dữ liệu và tuổi thọ iterator như các vấn đề riêng.
6. Ghi rõ kết quả nghiệp vụ cần giữ trước khi thay cách duyệt hoặc thay collection.

Thông tin hữu ích cho chẩn đoán gồm dấu thời gian, luồng, danh tính collection, thao tác cập nhật và ranh giới lượt xử lý. Chú ý rằng việc tự ghi log nội dung một collection đang bị sửa thiếu phối hợp cũng có thể trở thành một lượt duyệt không an toàn.

Trong kiểm thử, hãy kiểm tra trực tiếp các điều kiện có ý nghĩa:

- Đủ đúng tập mã công việc thuộc đầu vào đã chọn.
- Không có mã bị xử lý hai lần khi yêu cầu mỗi mã một lần.
- Tổng số liệu hoặc số lượng đăng ký cuối cùng đúng.
- Các cập nhật thất bại được báo rõ và không bị diễn giải thành thành công một phần.
- Kết quả của bản chụp và kết quả theo dữ liệu hiện hành được phân biệt.

Chạy một phép thử nhiều lần có thể tìm thêm lịch gây lỗi nhưng không chứng minh không còn lịch khác. Một phép thử luôn mong đợi `ConcurrentModificationException` từ cuộc đua thiếu đồng bộ có thể tự mang giả định sai. Cần kết hợp hợp đồng API, lập luận về thứ tự truy cập và phép thử có lịch được điều khiển.

## 11. Bài tập tự kiểm tra

### Bài 1 — Ngoại lệ trong một luồng

Một phương thức tạo iterator trên `ArrayList`, gọi một hàm trợ giúp để thêm phần tử vào chính danh sách ấy rồi tiếp tục `next`. Không có luồng phụ. Có thể loại trừ `ConcurrentModificationException` không?

**Đáp án:** Không. Cần xét hàm trợ giúp đã sửa cấu trúc ngoài đường iterator như thế nào. Số luồng không quyết định tính hợp lệ của iterator.

### Bài 2 — Đã khóa nhưng vẫn lỗi

Tất cả mã nằm trong `synchronized (list)` trên wrapper đồng bộ. Trong vòng lặp, hàm xử lý phần tử gọi `list.remove`. Người viết kết luận JVM đã không tôn trọng khóa vì vẫn thấy ngoại lệ. Kết luận này sai ở đâu?

**Đáp án:** Khóa chỉ phối hợp các bên dùng cùng monitor; nó không cấm luồng giữ khóa tái nhập và tự sửa danh sách. Phải chọn đường cập nhật phù hợp với iterator hoặc tách pha cập nhật khỏi pha duyệt.

### Bài 3 — Nội dung được khôi phục

Tạo iterator trên `[1, 2]`, thêm `3` rồi xóa `3`, sau đó tiếp tục iterator cũ. Có thể chứng minh an toàn bằng cách so sánh danh sách cuối với ban đầu không?

**Đáp án:** Không. So sánh hai trạng thái không nói rằng đã không có sửa đổi ở giữa. Với ví dụ `ArrayList` đã kiểm chứng, iterator cũ báo thay đổi dù nội dung cuối giống ban đầu.

### Bài 4 — Không lỗi nhưng có mã trùng

Hai luồng dùng `synchronizedList`, mỗi luồng gọi `contains` rồi `add` nếu chưa có. Hệ thống chạy không có ngoại lệ nhưng xuất hiện mã trùng. Nên thêm khối `catch` hay thay ranh giới thao tác?

**Đáp án:** Thay ranh giới thao tác hoặc cấu trúc dữ liệu. Phải bảo vệ toàn bộ quyết định kiểm tra rồi thêm bằng giao thức chung, hoặc dùng thao tác nguyên tử diễn đạt trực tiếp yêu cầu. Bắt ngoại lệ không ngăn hai lần quyết định cùng dựa trên trạng thái “chưa có”.

### Bài 5 — Bản chụp có đúng yêu cầu không?

Một iterator của `CopyOnWriteArrayList` được tạo trước khi đăng ký người nghe mới. Lượt thông báo hiện tại không gọi người nghe mới và không có ngoại lệ. Đó có phải lỗi collection không?

**Đáp án:** Không thể kết luận như vậy. Đó là hành vi bản chụp của iterator. Cần định nghĩa người nghe thêm trong lúc thông báo sẽ tham gia lượt hiện tại hay lượt tiếp theo; nếu yêu cầu khác thì chọn hoặc thiết kế cơ chế tương ứng.

### Bài 6 — Thử lại sau tác dụng bên ngoài

Một vòng lặp đã gửi hai thông báo trước khi iterator báo lỗi. Người viết bắt ngoại lệ rồi chạy lại toàn bộ vòng lặp. Có bảo đảm mỗi thông báo chỉ gửi một lần không?

**Đáp án:** Không. Ngoại lệ không hoàn tác hai lần gửi trước đó. Phải thiết kế tính lặp an toàn, dấu nhận diện tác vụ hoặc giao dịch phù hợp; chỉ thay iterator hay giới hạn số lần thử chưa đáp ứng yêu cầu.

### Bài 7 — Phần tử thay đổi mà danh sách không đổi

Danh sách chứa các đối tượng số dư. Không thêm hoặc xóa phần tử, nhưng một luồng sửa trường số dư và luồng khác đọc chúng. Không thấy ngoại lệ có chứng minh an toàn không?

**Đáp án:** Không. Cần xét hợp đồng đồng bộ của các đối tượng số dư và các trường được đọc/ghi. Theo dõi cấu trúc collection không phải theo dõi mọi trạng thái nằm bên trong phần tử.

### Bài 8 — Kiểm thử âm tính

Một bài kiểm thử tạo hai luồng đọc/ghi `ArrayList` tự do và chạy một triệu lần không thấy `ConcurrentModificationException`. Có thể dùng kết quả đó để bỏ khóa trong mã sản phẩm không?

**Đáp án:** Không. Phép thử chưa tạo ra bảo đảm đồng bộ hoặc chứng minh mọi lịch thực thi. Hãy xác định các truy cập xung đột, hợp đồng dữ liệu và bất biến cần giữ; sau đó chọn giao thức đúng và kiểm tra kết quả bằng lịch phối hợp có chủ đích.

Khi đánh giá một thay đổi trên collection, câu hỏi quyết định là: **cơ chế nào bảo vệ thao tác và ngữ nghĩa quan sát mà bài toán cần?** Fail-fast hỗ trợ phát hiện sai cách dùng; thiết kế quyền sở hữu, đồng bộ và bất biến mới là cơ sở để lập luận tính đúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-008">Tính nhìn thấy dữ liệu và giới hạn của việc công bố collection chỉ đọc</a>

<details>
<summary>Xem chi tiết</summary>

# Tính nhìn thấy dữ liệu và giới hạn của việc công bố collection chỉ đọc

## 1. Chỉ đọc trả lời câu hỏi về quyền sửa, chưa trả lời câu hỏi về nhìn thấy

Giả sử một luồng thực thi tạo danh sách, điền dữ liệu rồi đưa tham chiếu cho luồng khác. Phía nhận chỉ cần đọc danh sách đó. Cụm từ “collection chỉ đọc” nghe có vẻ đủ an toàn, nhưng thực tế có nhiều trách nhiệm khác nhau.

| Trách nhiệm | Câu hỏi cần trả lời |
| --- | --- |
| Quyền sửa qua API | Bên nhận có gọi được `add`, `remove`, `set` hoặc `put` không? |
| Quan hệ với nguồn | Có một cấu trúc độc lập hay chỉ là khung nhìn của nguồn còn sửa được? |
| Công bố dữ liệu | Luồng nhận lấy được tham chiếu và trạng thái khởi tạo bằng cơ chế nào? |
| Thay đổi về sau | Còn ai sửa cấu trúc nền hoặc đối tượng phần tử không? |
| Nhất quán theo phiên bản | Những lần đọc liên quan có cùng thuộc một phiên bản không? |

`Collections.unmodifiableList(source)` chặn những thao tác sửa qua khung nhìn. Nó không tự biến mọi lần gán tham chiếu đến khung nhìn thành một thao tác công bố giữa các luồng.

`List.copyOf(source)` tạo kết quả không chạy theo những thay đổi cấu trúc về sau của nguồn. Nó cũng không tự thiết kế cách thay tham chiếu hiện tại khi nhiều phiên bản cần được chia sẻ.

**Tính nhìn thấy dữ liệu (visibility)** nói về những giá trị mà một luồng được bảo đảm quan sát từ những lần ghi của luồng khác theo mô hình bộ nhớ Java. Nó khác với:

- Quyền sửa một collection.
- Tính nguyên tử của một phép tăng hoặc một chuỗi nhiều bước.
- Việc nhiều lần đọc cùng thuộc một trạng thái nghiệp vụ.

Ví dụ, hai lần lấy tham chiếu nguyên tử đều có thể đúng nhưng nhìn hai phiên bản khác nhau. Một danh sách không cho sửa cũng có thể chứa một đối tượng phần tử đang thay đổi.

Bài này dùng các chương trình Java 17 độc lập để kiểm chứng từng ranh giới. Các kết quả có phối hợp rõ ràng; đoạn mã thiếu phối hợp chỉ được dùng để phân tích giới hạn bảo đảm, không được gán một stdout tưởng tượng.

## 2. Công bố cần dựa vào bảo đảm của mô hình bộ nhớ

Công bố một collection là làm cho tham chiếu và trạng thái cần đọc của nó trở nên sử dụng được bởi một luồng khác.

Để giải thích điều đó, Java định nghĩa quan hệ **xảy ra trước có bảo đảm (happens-before)**. Nếu một lần ghi nằm trước một lần đọc theo quan hệ phù hợp, chương trình có cơ sở về thứ tự và tính nhìn thấy, thay vì dựa vào suy đoán về bộ nhớ đệm hoặc tốc độ CPU.

Một mô hình công bố điển hình là:

```text
Luồng công bố                         Luồng đọc

Tạo cấu trúc
Ghi các phần tử
Hoàn tất trạng thái cần công bố
       ↓
Mốc công bố có phối hợp ─────────────> Mốc nhận có phối hợp
                                              ↓
                                      Đọc trạng thái đã công bố
```

Các quy tắc thường được dùng gồm ghi và đọc một trường `volatile`, nhả và lấy cùng monitor, `Thread.start`, hoàn tất một luồng rồi `join`, hoặc những API đồng thời có hợp đồng bộ nhớ tương ứng. [Java Language Specification về happens-before](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5)

Không cần mô tả mọi lần nhìn thấy dữ liệu bằng một câu “đã đẩy xuống RAM”. Bảo đảm nằm ở mô hình bộ nhớ và hợp đồng API; JVM có thể thực thi bằng những cách khác nhau miễn giữ đúng hành vi được phép.

### Tham chiếu không bị xé không đồng nghĩa có đủ bảo đảm công bố

Việc đọc hoặc ghi một tham chiếu đối tượng là nguyên tử theo nghĩa không tạo một nửa tham chiếu từ lần ghi này ghép với nửa tham chiếu từ lần ghi khác. Nhưng riêng tính chất đó không tạo quan hệ công bố cần thiết cho một giao thức chia sẻ dữ liệu.

Đoạn dưới chỉ minh họa một thiết kế thiếu giao thức; không phải chương trình có kết quả được bảo đảm:

```java
// Trường dùng chung thông thường, không volatile.
static List<String> shared;

// Một luồng thực hiện:
shared = Collections.unmodifiableList(new ArrayList<>(List.of("ready")));

// Luồng khác tự đọc shared mà không có mốc phối hợp.
```

Chỉ thấy tên `unmodifiableList` chưa đủ để giải thích luồng kia khi nào quan sát được tham chiếu mới. Nếu có bảo đảm khởi tạo đặc biệt từ trường `final` hoặc đối tượng bất biến được xây dựng đúng, phải phân tích đúng phạm vi của nó; phần 6 sẽ làm rõ điều này.

Điều không được làm là viết một vòng kiểm tra trên trường thông thường, chạy thấy kết thúc trên một máy rồi suy ra giao thức đó luôn hợp lệ.

### Đúng chiều và đúng thời điểm mới có ích

Một ghi `volatile` chỉ giúp công bố những hành động nằm trước nó theo quan hệ tương ứng. Nó không tự đưa mọi lần sửa phát sinh về sau vào cùng một lần công bố.

Tương tự, nhả khóa A không tự phối hợp với người đọc lấy khóa B. Có từ khóa `synchronized` ở cả hai nơi chưa đủ nếu hai bên không dùng cùng chính sách.

## 3. Thí nghiệm công bố danh sách qua volatile

Lưu vào `VolatileReadOnlyPublicationDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VolatileReadOnlyPublicationDemo {
    private static volatile List<Integer> published;

    public static void main(String[] args) throws InterruptedException {
        int[] observed = new int[2];
        Thread reader = new Thread(() -> {
            List<Integer> local;
            while ((local = published) == null) {
                Thread.onSpinWait();
            }
            observed[0] = local.size();
            observed[1] = local.stream().mapToInt(Integer::intValue).sum();
        });
        Thread publisher = new Thread(() -> {
            List<Integer> building = new ArrayList<>();
            building.add(10);
            building.add(20);
            building.add(30);
            published = Collections.unmodifiableList(building);
        });

        reader.start();
        publisher.start();
        publisher.join();
        reader.join();
        System.out.println("seenSize=" + observed[0]);
        System.out.println("seenSum=" + observed[1]);
        try {
            published.add(40);
        } catch (UnsupportedOperationException e) {
            System.out.println("publishedAdd=UnsupportedOperationException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 VolatileReadOnlyPublicationDemo.java
java VolatileReadOnlyPublicationDemo
```

Stdout:

```text
seenSize=3
seenSum=60
publishedAdd=UnsupportedOperationException
```

Luồng công bố tạo `building`, thêm đủ 10, 20, 30 rồi mới gán khung nhìn vào trường `published` có `volatile`.

Luồng đọc chờ đến khi đọc được tham chiếu khác `null`, giữ nó trong biến cục bộ `local`, rồi đọc kích thước và tính tổng. Những ghi khởi tạo dữ liệu xảy ra trước ghi `volatile`; việc đọc trường `volatile` tạo mốc phối hợp cho người đọc.

Bảo đảm về nhìn thấy dữ liệu đến từ việc công bố đúng thời điểm qua trường đó. Bảo đảm chặn `add` đến từ lớp bao `unmodifiableList`. Đó là hai phần hợp đồng khác nhau.

Trong chương trình, danh sách nền không được sửa sau công bố và không có đường sửa được đưa cho người đọc. Các phần tử `Integer` cũng bất biến. Vì thế người đọc có thể xử lý dữ liệu sau khi lấy tham chiếu mà không có một bên tiếp tục thay cấu trúc nền.

### volatile trên tham chiếu không làm từng phần tử thành volatile

Khai báo `volatile List<T>` áp dụng cho trường chứa tham chiếu. Nó không tự gắn chính sách `volatile` cho các ô của danh sách, bộ đếm kích thước hay mọi trường bên trong từng `T`.

Nếu một bên vẫn tiếp tục gọi `building.add` hoặc sửa đối tượng phần tử sau câu gán `published`, cần một chính sách khác cho những thay đổi đó. Một lần công bố đúng không biến mọi cập nhật tương lai thành an toàn.

### onSpinWait không phải thành phần tạo bảo đảm nhìn thấy

`Thread.onSpinWait()` chỉ báo cho runtime rằng đoạn mã đang chờ bằng vòng lặp; nó có thể giúp cách thực thi vòng chờ. Chương trình vẫn đúng về cơ chế công bố nếu bỏ lời gọi đó, vì biến được kiểm tra là `volatile`. [Javadoc Thread.onSpinWait](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#onSpinWait())

Không thêm `onSpinWait` vào vòng đọc một trường thông thường rồi coi như đã sửa xong giao thức. Trong mã ứng dụng, có thể dùng latch, future hoặc hàng đợi khi chúng diễn đạt việc chờ và chuyển dữ liệu rõ hơn một vòng chờ chủ động.

### join bảo vệ phần đánh giá kết quả cuối

Mảng `observed` chỉ được luồng đọc ghi, và luồng chính chỉ đọc nó sau khi `reader.join()` hoàn tất. Các lời gọi `join` làm thời điểm in kết quả có ý nghĩa.

Điều đó không thay thế quan hệ giữa luồng công bố và luồng đọc trong lúc pipeline dữ liệu đang được sử dụng: ở ví dụ này, quan hệ công bố chính vẫn là trường `published` có `volatile`.

## 4. Những thay đổi sau công bố cần một quan hệ phối hợp mới

Giả sử đã gán một khung nhìn chỉ đọc vào trường `volatile`, rồi sau đó tiếp tục sửa danh sách nền. Người đọc không được dựa vào lần công bố cũ để suy ra mọi sửa đổi mới đều đã được phối hợp.

Có hai vấn đề riêng:

1. Khung nhìn vẫn dùng chung dữ liệu nền.
2. Những ghi về sau cần có quy tắc nhìn thấy và tính nhất quán riêng.

### Thí nghiệm cho thay đổi về sau một mốc phối hợp rõ ràng

Lưu vào `ChangesAfterPublicationDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ChangesAfterPublicationDemo {
    private static volatile List<String> published;

    public static void main(String[] args) throws InterruptedException {
        List<String> backing = new ArrayList<>(List.of("before"));
        published = Collections.unmodifiableList(backing);
        List<String> copied = List.copyOf(backing);
        CountDownLatch referenceCaptured = new CountDownLatch(1);
        CountDownLatch changeFinished = new CountDownLatch(1);
        String[] observed = new String[4];

        Thread reader = new Thread(() -> {
            List<String> view = published;
            referenceCaptured.countDown();
            try {
                changeFinished.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            observed[0] = Integer.toString(view.size());
            observed[1] = view.get(0);
            observed[2] = Integer.toString(copied.size());
            observed[3] = copied.get(0);
        });
        reader.start();

        referenceCaptured.await();
        backing.set(0, "after");
        backing.add("later");
        changeFinished.countDown();
        reader.join();

        System.out.println("viewSize=" + observed[0]);
        System.out.println("viewFirst=" + observed[1]);
        System.out.println("copySize=" + observed[2]);
        System.out.println("copyFirst=" + observed[3]);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ChangesAfterPublicationDemo.java
java ChangesAfterPublicationDemo
```

Stdout:

```text
viewSize=2
viewFirst=after
copySize=1
copyFirst=before
```

Luồng đọc lấy tham chiếu `view` rồi báo mốc `referenceCaptured`. Luồng chính chờ mốc đó, sửa danh sách nền, sau đó mới phát tín hiệu `changeFinished`. Luồng đọc chỉ tiếp tục đọc cấu trúc sau khi `await` thành công.

Vì vậy, kết quả `viewSize=2` và `viewFirst=after` có nguyên nhân rõ ràng:

- Khung nhìn không phải bản sao nên vẫn đọc nguồn nền.
- Latch cung cấp quan hệ phối hợp cho những ghi xảy ra sau lần gán `published`.

Các hành động trước `countDown` có quan hệ happens-before với các hành động sau một lần `await` tương ứng thành công, theo hợp đồng của latch. [Javadoc CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)

Chương trình không chứng minh rằng một khung nhìn chỉ đọc tự bảo đảm nhìn thấy cập nhật tương lai. Ngược lại, nó cố ý đưa vào một cơ chế phối hợp riêng để phép đọc có hợp đồng đúng.

`copied` được tạo bằng `List.copyOf` khi nguồn còn có `before`. Nó giữ cấu trúc `[before]` dù nguồn đã có `[after, later]`. Ở ví dụ này phần tử là `String` bất biến, nên không có thay đổi trạng thái phần tử dùng chung.

### Latch trong ví dụ không chỉ là dụng cụ trì hoãn

Khi thiết kế một thí nghiệm đa luồng, một latch có thể vừa điều phối thứ tự vừa tạo quan hệ bộ nhớ. Không thêm latch rồi dùng kết quả để kết luận rằng phiên bản không có latch cũng có cùng bảo đảm.

Tương tự, nếu dùng `join` để chờ bên ghi xong trước khi đọc, chính `join` đã cung cấp một quan hệ hoàn tất. Một kết quả đúng sau `join` không chứng minh một phép đọc cạnh tranh, không có phối hợp, cũng luôn đúng.

Trong ví dụ này, dữ liệu ban đầu còn được tạo trước `reader.start()`; điều đó cũng có ý nghĩa về công bố ban đầu. Phần cần kiểm chứng là những ghi được thực hiện sau khi người đọc đã lấy khung nhìn, và chúng được phối hợp bằng `changeFinished`.

### volatile không phải khóa cho nguồn nền

Đọc một trường `volatile` rồi duyệt một `ArrayList` đang bị bên khác sửa vẫn có thể vi phạm hợp đồng duyệt của nguồn. Tham chiếu được công bố đúng và nguồn được bảo vệ trong quá trình sử dụng là hai điều kiện riêng.

Nếu muốn tiếp tục sửa cùng cấu trúc, có thể dùng chính sách khóa hoặc cấu trúc concurrent thích hợp. Nếu muốn một phiên bản ổn định để đọc, tạo bản sao đúng cách và không sửa phiên bản đã công bố.

## 5. Nhìn thấy đúng từng lần vẫn có thể trộn hai phiên bản

Một trường `volatile` hoặc một `AtomicReference` giúp phối hợp việc truy cập tham chiếu. Nó không tự gộp nhiều lần lấy tham chiếu thành một lần đọc giao dịch.

Giả sử phiên bản 1 có nhãn `old`, phiên bản 2 có nhãn `new`. Nếu người đọc lấy số phiên bản từ lần đọc thứ nhất và nhãn từ lần đọc tham chiếu thứ hai, có thể ghép “phiên bản 1, nhãn new”.

Đây không nhất thiết là lỗi nhìn thấy bộ nhớ. Mỗi lần đọc có thể quan sát đúng một phiên bản đã được công bố, nhưng thuật toán đọc đã chọn hai mốc khác nhau.

Lưu vào `CapturedVersionReadDemo.java`.

```java
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class CapturedVersionReadDemo {
    record Settings(int version, List<String> labels) {
        Settings {
            labels = List.copyOf(labels);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicReference<Settings> current =
                new AtomicReference<>(new Settings(1, List.of("old")));
        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch replacementReady = new CountDownLatch(1);
        String[] observed = new String[4];

        Thread reader = new Thread(() -> {
            Settings captured = current.get();
            observed[0] = Integer.toString(captured.version());
            firstRead.countDown();
            try {
                replacementReady.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            observed[1] = current.get().labels().get(0);
            observed[2] = Integer.toString(captured.version());
            observed[3] = captured.labels().get(0);
        });
        reader.start();

        firstRead.await();
        current.set(new Settings(2, List.of("new")));
        replacementReady.countDown();
        reader.join();

        System.out.println("separateReadVersion=" + observed[0]);
        System.out.println("separateReadLabel=" + observed[1]);
        System.out.println("capturedVersion=" + observed[2]);
        System.out.println("capturedLabel=" + observed[3]);
        System.out.println("latestVersion=" + current.get().version());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 CapturedVersionReadDemo.java
java CapturedVersionReadDemo
```

Stdout:

```text
separateReadVersion=1
separateReadLabel=new
capturedVersion=1
capturedLabel=old
latestVersion=2
```

Latch buộc bên đọc lấy `captured` trước khi luồng chính thay tham chiếu. Sau khi thay xong, người đọc được cho đi tiếp.

Hai dòng đầu ghép giá trị từ hai mốc:

```text
separateReadVersion=1
separateReadLabel=new
```

Hai dòng sau đọc từ cùng `captured`:

```text
capturedVersion=1
capturedLabel=old
```

Phiên bản hiện tại đã là 2, nhưng biến cục bộ vẫn giữ tham chiếu phiên bản 1. `Settings` có trường record cố định và constructor sao chép danh sách bằng `List.copyOf`; các nhãn là `String`. Không có thao tác sửa tại chỗ phiên bản cũ.

`AtomicReference` cung cấp cách truy cập và cập nhật một biến tham chiếu theo các thao tác atomic. Trong ví dụ, nó là nơi công bố phiên bản hiện tại; latch còn được dùng để tạo lịch quan sát xác định cho thí nghiệm. [Javadoc java.util.concurrent.atomic](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/package-summary.html)

### Quy tắc lấy một phiên bản cho một tác vụ

Nếu yêu cầu cần nhiều lần đọc nhất quán theo phiên bản:

1. Lấy tham chiếu phiên bản hiện tại một lần.
2. Giữ trong một biến cục bộ.
3. Đọc những dữ liệu liên quan từ đúng đối tượng phiên bản đó.
4. Không tự trộn thêm dữ liệu từ một lần lấy phiên bản mới nếu hợp đồng không cho phép.

Điều này yêu cầu bản thân phiên bản có mức ổn định phù hợp. Nếu nó chứa phần tử đang thay đổi được, chỉ giữ cùng một tham chiếu chưa đủ để có ảnh chụp sâu.

Nhiều bên ghi cũng là một yêu cầu khác. `get` rồi `set` riêng để tính phiên bản mới không tự tạo một phép cập nhật ghép nguyên tử. Cần một thao tác cập nhật phù hợp hoặc một chính sách bảo vệ cho toàn bộ bước tính và công bố.

## 6. final có bảo đảm khởi tạo riêng, nhưng không đóng băng mọi dữ liệu

Không nên rút vấn đề công bố thành câu “bất cứ thứ gì không dùng volatile đều sai”. Java còn có bảo đảm về trường `final`, khởi tạo lớp và các cơ chế phối hợp khác.

### Biến cục bộ final và trường final khác vai trò

Một biến cục bộ `final List<T>` không được gán lại sau khi khởi tạo. Tính chất đó không tự tạo một kênh công bố tới luồng khác và không làm danh sách trở thành bất biến sâu.

Trường instance `final` có thêm ngữ nghĩa khởi tạo của mô hình bộ nhớ Java. Khi đối tượng được xây dựng đúng, không để tham chiếu `this` thoát ra cho luồng khác trước khi constructor hoàn tất, các giá trị trường `final` có bảo đảm khởi tạo đặc biệt. Bảo đảm còn liên quan tới trạng thái khởi tạo của những đối tượng được tham chiếu qua các trường đó. Đây là một bảo đảm đặc biệt cho trạng thái khởi tạo khi luồng khác đã quan sát được đối tượng được xây dựng hoàn tất, kể cả trong trường hợp việc truyền tham chiếu không dùng đồng bộ thông thường; nó không tạo cơ chế buộc một vòng chờ quan sát mọi lần thay tham chiếu. [Java Language Specification về final fields](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.5)

Đoạn minh họa thiết kế lớp, không phải chương trình độc lập:

```java
final class Catalog {
    private final List<String> names;

    Catalog(List<String> source) {
        this.names = List.copyOf(source);
    }

    List<String> names() {
        return names;
    }
}
```

Ở đây cần giả định bước đọc `source` khi sao chép là hợp lệ. Đối tượng không để lộ chính nó trong constructor, danh sách không cho sửa cấu trúc và phần tử là `String` bất biến.

Không được vì có bảo đảm `final` mà kết luận một trường tham chiếu thông thường dùng để thay `Catalog` hiện tại sẽ tự có giao thức cập nhật và thông báo. Bảo đảm khởi tạo không buộc một vòng chờ trên trường thông thường quan sát được mọi lần thay tham chiếu.

Nó cũng không bảo vệ các sửa đổi xảy ra về sau trên một collection nền còn thay đổi được hoặc trên những trường không được bảo vệ của phần tử.

### static final qua khởi tạo lớp là một cách chia sẻ hợp lệ

Ví dụ khai báo trường trong một lớp:

```java
private static final List<String> DEFAULT_NAMES = List.of("A", "B");
```

Khởi tạo lớp có cơ chế phối hợp của JVM. Khi dùng đúng cách, kiểu dữ liệu tĩnh không thay đổi này không cần một trường `volatile` riêng chỉ để người dùng lớp đọc danh sách mặc định đã khởi tạo. [Java Language Specification về khởi tạo lớp](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2)

Tuy nhiên, nếu thay phần tử bằng một đối tượng có setter, `static final` vẫn không tự biến mọi lời gọi setter thành an toàn đa luồng. Nó cố định tham chiếu trường và tham gia hợp đồng khởi tạo, không áp một khóa lên toàn bộ đồ thị đối tượng.

### Tạo trước Thread.start cũng có ý nghĩa

Những hành động trước khi gọi `start` có quan hệ happens-before với hành động trong luồng được bắt đầu. Vì vậy, một snapshot được chuẩn bị xong, đưa vào tác vụ rồi bắt đầu luồng đọc có thể có một đường công bố hợp lệ mà không dùng thêm trường `volatile`.

Điều kiện còn lại vẫn là kiểm soát thay đổi sau đó. Nếu một bên tiếp tục sửa đối tượng được dùng chung, bảo đảm của thời điểm bắt đầu luồng không tự bao phủ những sửa đổi tương lai.

## 7. Collection chỉ đọc vẫn có thể chứa phần tử thay đổi được

Một collection không cho thêm hoặc xóa phần tử chưa phải một đồ thị dữ liệu bất biến sâu. Những tham chiếu đang có bên trong vẫn có thể dẫn tới trạng thái thay đổi được.

Lưu vào `ReadOnlyListMutableCounterDemo.java`.

```java
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ReadOnlyListMutableCounterDemo {
    public static void main(String[] args) throws InterruptedException {
        List<AtomicInteger> counters = List.of(new AtomicInteger());
        Runnable increment = () -> {
            for (int i = 0; i < 1000; i++) {
                counters.get(0).incrementAndGet();
            }
        };
        Thread first = new Thread(increment);
        Thread second = new Thread(increment);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("listSize=" + counters.size());
        System.out.println("counterValue=" + counters.get(0).get());
        try {
            counters.add(new AtomicInteger());
        } catch (UnsupportedOperationException e) {
            System.out.println("listAdd=UnsupportedOperationException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ReadOnlyListMutableCounterDemo.java
java ReadOnlyListMutableCounterDemo
```

Stdout:

```text
listSize=1
counterValue=2000
listAdd=UnsupportedOperationException
```

Danh sách có đúng một phần tử và không cho gọi `add`. Nhưng phần tử đó là một `AtomicInteger`, được thiết kế để cập nhật.

Hai luồng mỗi bên tăng 1000 lần, nên giá trị cuối là 2000. Tính đúng của phép tăng và các quy tắc truy cập giá trị đến từ `AtomicInteger`, không đến từ việc danh sách bên ngoài không cho sửa. [Javadoc AtomicInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html)

Dữ liệu được chuẩn bị trước khi bắt đầu hai luồng, và luồng chính đợi cả hai hoàn tất bằng `join` trước khi in kết quả. Không có phép đọc mảng hoặc phần tử đang thiếu mốc phối hợp trong phần đánh giá cuối.

Nếu thay `AtomicInteger` bằng một đối tượng có trường `int` thông thường rồi tùy ý thực hiện `value++` từ hai luồng, danh sách chỉ đọc không tự bảo vệ phép đọc, cộng và ghi đó. Tình huống còn có vấn đề tính nguyên tử, không chỉ nhìn thấy dữ liệu.

### Khóa hoặc giá trị trong Map cũng có giới hạn tương tự

Một Map không cho thay ánh xạ có thể vẫn chứa giá trị có setter. Một Set không cho thêm phần tử có thể vẫn chứa đối tượng có trạng thái thay đổi được.

Nếu sửa trạng thái ảnh hưởng tới `equals`, `hashCode` hoặc thứ tự so sánh của khóa hay phần tử tập, còn có thể vi phạm giả định của cấu trúc dữ liệu. Vì vậy, thiết kế phần tử ổn định có giá trị cả về tính đúng của collection lẫn việc chia sẻ giữa các luồng.

Khi hợp đồng API nói “chỉ đọc”, cần nêu rõ nó cấm sửa cấu trúc hay cung cấp một giá trị bất biến sâu. Hai lời hứa không tương đương.

## 8. Bàn giao qua hàng đợi có thể cung cấp đường công bố rõ ràng

Không phải mọi giao thức cần một trường `volatile` và vòng chờ tự viết. Một `BlockingQueue` có hợp đồng về bộ nhớ khi một luồng đặt phần tử vào và luồng khác lấy phần tử đó.

Những hành động trước khi đặt đối tượng vào hàng đợi có quan hệ happens-before với các hành động sau khi luồng khác truy cập hoặc lấy đúng phần tử đó. [Javadoc BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)

Lưu vào `ReadOnlyQueueHandoffDemo.java`.

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ReadOnlyQueueHandoffDemo {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<List<String>> channel = new ArrayBlockingQueue<>(1);
        String[] received = new String[1];
        int[] sizes = new int[2];

        Thread consumer = new Thread(() -> {
            try {
                List<String> message = channel.take();
                received[0] = message.toString();
                sizes[0] = message.size();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
        Thread producer = new Thread(() -> {
            try {
                List<String> building =
                        new ArrayList<>(List.of("alpha", "beta"));
                List<String> message = List.copyOf(building);
                channel.put(message);
                building.add("later");
                sizes[1] = building.size();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });

        consumer.start();
        producer.start();
        producer.join();
        consumer.join();
        System.out.println("received=" + received[0]);
        System.out.println("receivedSize=" + sizes[0]);
        System.out.println("builderSizeAfterSend=" + sizes[1]);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ReadOnlyQueueHandoffDemo.java
java ReadOnlyQueueHandoffDemo
```

Stdout:

```text
received=[alpha, beta]
receivedSize=2
builderSizeAfterSend=3
```

Bên tạo dữ liệu có một `building` chỉ do chính nó sở hữu:

1. Tạo đủ `alpha` và `beta`.
2. Tạo `message = List.copyOf(building)`.
3. Đặt `message` vào hàng đợi.
4. Tiếp tục sửa `building` bằng cách thêm `later`.

Bên nhận lấy `message` qua `take`. Nó đọc đúng danh sách `[alpha, beta]` với kích thước 2. Phần xây dựng riêng của bên gửi đã có kích thước 3 nhưng không làm cấu trúc `message` chạy theo.

Có hai bảo đảm kết hợp:

- Hàng đợi cung cấp quan hệ công bố dữ liệu trước bước gửi.
- Bản sao không cho sửa và các phần tử `String` bất biến giữ dữ liệu thông điệp ổn định sau đó.

Các mảng dùng để ghi nhận kết quả được các luồng con cập nhật, rồi chỉ được luồng chính đọc sau `join`. Chúng là nơi ghi nhận kết quả của ví dụ, không phải một kênh đọc cạnh tranh chưa được bảo vệ.

### Gửi khung nhìn còn thay đổi được là một hợp đồng khác

Nếu thay `List.copyOf(building)` bằng một khung nhìn `Collections.unmodifiableList(building)`, rồi bên gửi tiếp tục sửa `building` sau khi gửi, khung nhìn vẫn dùng cùng cấu trúc nền.

Hợp đồng hàng đợi chỉ công bố những hành động ở trước bước đưa phần tử vào theo quan hệ tương ứng. Nó không tự cấm bên gửi sửa nền về sau và không khóa thay cho bên nhận trong suốt thời gian đọc.

Một lựa chọn khác là chuyển quyền sở hữu cấu trúc cho bên nhận và cam kết bên gửi không đụng vào nó nữa. Lựa chọn đó cần được thiết kế và tuân thủ rõ ràng; chỉ bọc một lớp chỉ đọc chưa tạo ra việc chuyển quyền sở hữu.

## 9. Thiết kế đường đi của dữ liệu thay vì chỉ chọn nhãn chỉ đọc

Có thể đọc một thiết kế chia sẻ collection theo chuỗi sau:

```text
Xây dựng dữ liệu hợp lệ
        ↓
Chọn mức bảo vệ cấu trúc và phần tử
        ↓
Hoàn tất phiên bản cần đưa cho người đọc
        ↓
Công bố bằng một cơ chế có hợp đồng bộ nhớ
        ↓
Người đọc lấy đúng phạm vi phiên bản
        ↓
Mọi thay đổi về sau có chính sách riêng
```

Các câu hỏi cần giải thích được:

| Giai đoạn | Điều phải rõ |
| --- | --- |
| Xây dựng | Có bên nào đọc hoặc sửa cùng nguồn trong lúc tạo bản sao không? |
| Giới hạn quyền sửa | Kết quả là khung nhìn, bản sao cấu trúc hay dữ liệu bất biến sâu? |
| Công bố | Quan hệ nào cho phép luồng nhận đọc trạng thái đã chuẩn bị? |
| Đọc | Một tác vụ lấy một phiên bản hay có thể ghép nhiều phiên bản? |
| Cập nhật về sau | Sửa tại chỗ dưới khóa, dùng phần tử atomic, hay tạo phiên bản mới? |
| Kết thúc tác vụ | Có dùng tín hiệu hoàn tất đúng nghĩa khi cần quan sát kết quả không? |

Nếu chọn khóa, cả bên ghi lẫn bên đọc trạng thái liên quan phải dùng cùng monitor hoặc cùng giao thức khóa. Khóa trên một lớp bao chỉ đọc mới tạo không tự phối hợp với bên đang khóa trên một đối tượng nền khác.

Nếu chọn `volatile` hoặc một tham chiếu atomic, xác định những ghi nào nằm trước bước công bố và còn thao tác sửa nào xảy ra sau đó.

Nếu chọn phiên bản không cho sửa, xác định cả cấu trúc lồng nhau và trạng thái phần tử; một lần sao chép danh sách không tự tạo bản sao sâu cho mọi đối tượng.

### Không dùng thời gian trôi qua để thay cho nhìn thấy dữ liệu

`Thread.sleep` và `Thread.yield` không có ngữ nghĩa đồng bộ hóa để buộc các lần ghi được công bố hay các lần đọc phải nạp lại một trường thông thường. [Java Language Specification về sleep và yield](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.3)

Thêm log có thể làm thay đổi lịch chạy hoặc tương tác khóa trong chương trình. Việc lỗi biến mất khi thêm `println` không tạo thành bằng chứng rằng giao thức dữ liệu đã đúng.

Với đoạn mã có tranh chấp dữ liệu, không hứa một kết quả sai cố định như “luôn đọc 0”, “luôn treo” hoặc “chạy lại là chắc thấy lỗi”. Giới hạn là không có bảo đảm cần thiết; một lần quan sát thuận lợi không mở rộng hợp đồng Java.

### Những kết luận thường bị nhầm

| Kết luận | Điều cần sửa |
| --- | --- |
| Không gọi được add thì dữ liệu đã được công bố an toàn | Quyền sửa qua API khác cơ chế công bố giữa các luồng |
| Tham chiếu được gán nguyên tử thì mọi trạng thái đã nhìn thấy đúng | Tính nguyên tử của tham chiếu khác các quan hệ khởi tạo và công bố |
| volatile List làm mọi phần tử tự volatile | Chỉ trường tham chiếu có chính sách đó |
| Ghi volatile một lần bảo vệ mọi sửa đổi tương lai của nền | Những ghi sau đó cần một quan hệ phối hợp riêng |
| unmodifiableList cắt quan hệ với nguồn | Nó là khung nhìn, không phải bản sao độc lập |
| copyOf làm bất biến sâu mọi phần tử | Nó không tự sao chép sâu đồ thị đối tượng |
| Có final là không cần phân tích constructor hoặc cách chia sẻ | Bảo đảm final có điều kiện xây dựng và phạm vi riêng |
| Mọi dữ liệu chỉ đọc đều bắt buộc có trường volatile | Khởi tạo lớp, ngữ nghĩa khởi tạo final trong phạm vi của nó, start, khóa hoặc API bàn giao cũng có những bảo đảm phù hợp |
| AtomicReference.get nhiều lần tự cho một phiên bản | Mỗi lần lấy có thể quan sát một phiên bản hợp lệ khác |
| Dùng latch trong kiểm thử chỉ làm chậm các bước | Latch còn tạo quan hệ bộ nhớ có thể thay đổi điều được kiểm chứng |
| Đã đọc đúng sau join thì mọi đọc cạnh tranh trước đó cũng đúng | join cung cấp một mốc hoàn tất; không thể bỏ nó khỏi lập luận |
| Queue bảo vệ mọi sửa đổi sau khi gửi đối tượng | Hợp đồng bàn giao không tự bảo vệ cập nhật tương lai của đối tượng dùng chung |
| Vòng chờ có sleep hoặc onSpinWait tự an toàn | Phải có biến hoặc giao thức có ngữ nghĩa phối hợp đúng |
| Chạy nhiều lần không lỗi thì không có tranh chấp dữ liệu | Cần kiểm tra quan hệ bộ nhớ, không chỉ đếm lần chạy thành công |

Một hợp đồng trả collection chỉ đọc nên nói rõ dữ liệu có thể thay đổi từ phía khác không, phần tử có thể thay đổi không và người dùng nhận một ảnh chụp hay khung nhìn. Với dữ liệu dùng giữa các luồng, còn cần mô tả đường công bố hoặc giao thức sử dụng phù hợp.

## 10. Thực hành tìm đúng quan hệ bảo đảm

### Bài 1: chỉ bọc unmodifiable rồi gán vào trường thông thường

Một luồng tạo danh sách, bọc `unmodifiableList` rồi gán vào trường dùng chung không có `volatile`. Luồng kia lặp đọc trường đó mà không có mốc phối hợp. Có thể giải thích vòng chờ luôn quan sát được tham chiếu mới chỉ bằng việc danh sách không cho sửa không?

**Đối chiếu:**

Không. Lớp bao chỉ đọc không phải một giao thức thông báo tham chiếu mới. Cần xác định bảo đảm công bố và tiến trình quan sát phù hợp, chẳng hạn trường `volatile`, một khóa dùng chung hoặc API bàn giao dữ liệu.

Nếu có bảo đảm khởi tạo đặc biệt của đối tượng bất biến được xây dựng đúng, phải phân biệt nó với việc một vòng chờ trên trường thông thường chắc chắn nhìn thấy cập nhật.

### Bài 2: sửa nền sau lần ghi volatile

Danh sách nền được điền xong, công bố qua một trường `volatile`, rồi tiếp tục bị sửa bằng `set`. Người đọc chỉ đọc trường tham chiếu đó một lần. Lần ghi `volatile` trước có tự bảo vệ lần `set` sau không?

**Đối chiếu:**

Không. Nó không tự đưa những ghi về sau vào cùng lần công bố. Cần một chính sách mới cho các cập nhật đó, hoặc không sửa phiên bản đã công bố.

`ChangesAfterPublicationDemo` có kết quả xác định vì dùng thêm latch cho các ghi sau công bố.

### Bài 3: hai lần đọc đều an toàn nhưng khác phiên bản

Phiên bản 1 có nhãn `old`, phiên bản 2 có nhãn `new`. Người đọc lấy số phiên bản rồi lấy nhãn qua hai lần đọc tham chiếu hiện tại riêng biệt. Vì sao có thể ghép “1, new” mà không cần giả định bộ nhớ đọc sai?

**Đối chiếu:**

Một lần đọc có thể lấy phiên bản 1, lần sau lấy phiên bản 2. Đó là hai quan sát hợp lệ ở hai mốc. Nếu cần một trạng thái nhất quán, lấy tham chiếu một lần và đọc các giá trị liên quan từ cùng phiên bản ổn định.

### Bài 4: final tham chiếu tới một List

Một lớp có trường `final List<MutableItem>` được gán trong constructor. Sau khi constructor hoàn tất, bên khác sửa một trường của `MutableItem`. Chỉ từ từ khóa `final` có thể kết luận thao tác đó được bảo vệ không?

**Đối chiếu:**

Không. Trường `final` có bảo đảm khởi tạo riêng khi xây dựng đúng, nhưng không khóa mọi thay đổi về sau của đối tượng được tham chiếu. Trạng thái `MutableItem` cần mức bất biến hoặc phối hợp riêng.

### Bài 5: danh sách mặc định static final

Một lớp khai báo `private static final List<String> DEFAULTS = List.of("A", "B");` và không có phần tử thay đổi được. Có cần thêm `volatile` chỉ vì dữ liệu được đọc từ nhiều luồng không?

**Đối chiếu:**

Không cần thêm như vậy chỉ để đọc dữ liệu mặc định đã được khởi tạo lớp đúng cách. Khởi tạo lớp đã có hợp đồng phối hợp, và cấu trúc cùng các giá trị `String` không thay đổi trong thiết kế này.

Không suy rộng đáp án sang một biến tham chiếu khác thường xuyên bị gán lại hoặc sang những phần tử có trạng thái thay đổi được.

### Bài 6: chỉ đọc bên ngoài, AtomicInteger bên trong

Một `List.of(new AtomicInteger())` chặn thêm phần tử. Hai luồng vẫn tăng được bộ đếm bên trong. Vì sao điều đó không mâu thuẫn?

**Đối chiếu:**

Danh sách không bị thay cấu trúc; trạng thái đối tượng phần tử được cập nhật. `AtomicInteger` cung cấp hợp đồng cho phép tăng và đọc giá trị; tính chất chỉ đọc của danh sách không làm việc đó và cũng không cấm nó.

### Bài 7: hàng đợi và dữ liệu nền còn bị sửa

Bên gửi đặt `Collections.unmodifiableList(building)` vào một `BlockingQueue`, rồi tiếp tục sửa `building`. Bên nhận có được coi thông điệp là một snapshot không đổi chỉ vì đi qua hàng đợi không?

**Đối chiếu:**

Không. Hàng đợi có bảo đảm công bố cho các hành động trước khi gửi, nhưng khung nhìn vẫn có nền dùng chung. Cần bản sao phù hợp, chuyển quyền sở hữu hoặc một chính sách cho những sửa đổi tiếp theo.

### Bài 8: xác định điều mà một kiểm chứng thật sự chứng minh

Một ví dụ chỉ đọc trường thông thường sau khi đã `join` bên ghi, và kết quả luôn đúng. Có được bỏ `join` rồi dùng chính quan sát đó để nói bản không có phối hợp cũng an toàn không?

**Đối chiếu:**

Không. `join` là một phần của lập luận về hoàn tất và nhìn thấy dữ liệu. Thay đổi mốc phối hợp làm thay đổi những bảo đảm đang dùng.

Một lời giải tốt cần chỉ ra từng mốc: dữ liệu được tạo ở đâu, tham chiếu được chuyển bằng cách nào, những ghi nào nằm trước mốc công bố, người đọc giữ phiên bản nào và những thay đổi về sau được bảo vệ ra sao.


</details>

- [Quay lại đầu trang](#back-to-top)
