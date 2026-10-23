<a id="back-to-top"></a>

# Collection chỉ đọc

## Menu
- [Phân biệt không sửa được, kích thước cố định và bất biến sâu](#unmodifiable-collections-001)
- [Collections.unmodifiableList, unmodifiableSet và unmodifiableMap](#unmodifiable-collections-002)
- [Vì sao unmodifiable view vẫn thấy thay đổi từ nguồn](#unmodifiable-collections-003)
- [List.of, Set.of, Map.of và Map.ofEntries](#unmodifiable-collections-004)
- [List.copyOf, Set.copyOf và Map.copyOf](#unmodifiable-collections-005)
- [Quy tắc null, phần tử trùng và khóa trùng của các factory](#unmodifiable-collections-006)
- [emptyList, singleton, nCopies và các trường hợp dùng](#unmodifiable-collections-007)
- [Bảo vệ dữ liệu khi nhận tham số hoặc trả về collection từ API](#unmodifiable-collections-008)

## <a id="unmodifiable-collections-001">Phân biệt không sửa được, kích thước cố định và bất biến sâu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt collection không sửa được, kích thước cố định và bất biến sâu

Một danh sách có thể từ chối `add` nhưng vẫn cho `set`. Một danh sách từ chối cả `add`, `remove` và `set` vẫn có thể hiện thêm phần tử sau khi nguồn được sửa. Ngay cả khi danh sách đã tách khỏi nguồn, đối tượng lấy qua `get` vẫn có thể thay đổi.

Ba tình huống ấy nằm ở ba tầng khác nhau: quyền sửa vật chứa, liên kết với dữ liệu bên ngoài và trạng thái của phần tử. Hiểu đúng từng tầng giúp thiết kế kết quả trả về, cấu hình và bản chụp dữ liệu có hợp đồng rõ ràng.

Bài này dùng Java 17 làm mức API tối thiểu. Cần biết `List`, `ArrayList`, tham chiếu đối tượng và ngoại lệ trước khi làm các thí nghiệm. `Map` được dùng ở một ví dụ ngắn để cho thấy cùng cách phân tích áp dụng cho cả khóa và giá trị.

## 1. Bắt đầu từ điều người dùng dữ liệu được phép làm

Giả sử một dịch vụ trả về danh sách mặt hàng. Yêu cầu “chỉ đọc” còn thiếu thông tin. Người nhận có được đổi thứ tự không? Có được thay mặt hàng ở vị trí 0 không? Nếu dịch vụ thêm một mặt hàng sau đó thì danh sách đã trả có nhìn thấy không? Người nhận có được đổi số lượng của chính mặt hàng không?

Hãy xét bốn câu hỏi riêng:

1. **Đổi số lượng phần tử:** có thêm, xóa hoặc làm rỗng vật chứa được không?
2. **Đổi phần tử hoặc vị trí:** có thay `list[i]` bằng tham chiếu khác, sắp xếp lại hoặc thay ánh xạ của map được không?
3. **Nhìn thấy nguồn thay đổi:** kết quả là góc nhìn (view) hay đã giữ riêng thành phần của mình?
4. **Đổi đối tượng bên trong:** phần tử, khóa, giá trị và những đối tượng lồng bên dưới có phương thức sửa trạng thái không?

Đừng gộp tất cả thành một câu “có mutable không?”. Cùng một `List<E>` có thể trả lời khác nhau ở cả bốn câu. Kiểu khai báo `List<E>` chưa diễn đạt các bảo đảm này; người dùng phải biết hợp đồng của cách tạo hoặc phương thức trả kết quả.

Cũng cần tách **quan sát thấy thay đổi** khỏi **tự gọi phương thức sửa**. Khi nguồn của một view được cập nhật, người giữ view có thể quan sát thấy dữ liệu mới dù họ không có quyền sửa qua view.

## 2. Ba khái niệm và một bảng đối chiếu

### Kích thước cố định

Danh sách kích thước cố định (fixed-size list) không cho tăng hoặc giảm số ô chứa phần tử. Điều này chưa quyết định có được thay tham chiếu tại một ô hay không.

`Arrays.asList(array)` là ví dụ điển hình: danh sách dựa trên chính mảng được truyền vào, hỗ trợ thay phần tử và phản ánh thay đổi qua lại với mảng. `add` hoặc `remove` làm đổi kích thước sẽ bị từ chối. Vì thế “fixed-size” mô tả kích thước, không mô tả đầy đủ quyền sửa. [API Arrays.asList, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

### Không sửa được qua API của collection

Collection không sửa được (unmodifiable collection) từ chối các phương thức sửa vật chứa theo hợp đồng của nó. Với list, những thao tác ấy bao gồm thêm, xóa và thay phần tử. Sửa nội dung của đối tượng do `get` trả về là một lời gọi khác, nằm ngoài việc sửa vật chứa.

Định nghĩa của `Collection` cũng phân biệt collection không sửa được và view không sửa được. View có thể nhìn thấy thay đổi ở nguồn; phần tử có thể có trạng thái riêng thay đổi được. Vì vậy chỉ riêng nhãn “unmodifiable” chưa chứng minh bất biến sâu. [Định nghĩa trong Collection, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#unmodcoll).

### Bất biến sâu

Trong bài này, **bất biến sâu** (deep immutability) nghĩa là sau khi giá trị được xây dựng xong, trạng thái dữ liệu có thể quan sát của nó và của các đối tượng cấu thành giá trị ấy không thể bị sửa qua các tham chiếu được chia sẻ.

Với một `List<List<String>>`, bảo vệ list ngoài là chưa đủ: các list trong cũng phải có trạng thái ổn định. Với `List<Order>`, còn phải xét `Order` chứa gì. Nếu `Order` có danh sách chi tiết, mỗi chi tiết có thể lại chứa đối tượng khác. Phạm vi chứng minh đi theo mô hình dữ liệu, không dừng ở một lần gọi `copyOf`.

Không nhất thiết phải nhân bản mọi đối tượng mới có được bất biến sâu. Có thể chia sẻ an toàn những giá trị vốn đã bất biến, chẳng hạn chuỗi. Điều cần loại bỏ là khả năng sửa trạng thái chung làm thay đổi giá trị đang bảo vệ.

| Cách tạo điển hình | Thêm/xóa qua list | Thay phần tử bằng `set` | Thay đổi cấu trúc nguồn có hiện vào kết quả? | Tự bảo đảm phần tử bất biến sâu? |
| --- | --- | --- | --- | --- |
| `new ArrayList<>(source)` | Có | Có | Không liên kết cấu trúc nguồn | Không |
| `Arrays.asList(array)` | Không đổi kích thước | Có | Có, cùng các ô của mảng | Không |
| `Collections.unmodifiableList(source)` | Không | Không | Có nếu nguồn thay đổi | Không |
| `List.copyOf(source)` | Không | Không | Không phản ánh thay đổi thành phần sau đó ở nguồn | Không |
| `List.of(a, b)` | Không | Không | Không có vật chứa nguồn để theo dõi | Không |
| List ngoài không sửa được, phần tử là giá trị bất biến đã được bảo vệ đầy đủ | Không | Không | Không có đường sửa dữ liệu đã công bố | Có, theo hợp đồng của mô hình ấy |

“Không liên kết cấu trúc nguồn” không có nghĩa mọi đối tượng bên trong đều khác nhau. Ví dụ `new ArrayList<>(source)` vẫn có thể giữ cùng tham chiếu tới từng `Order`. Bảng đang phân biệt vật chứa và dữ liệu bên trong, không khẳng định có sao chép sâu.

Các hàng cũng có thể đồng thời mang nhiều tính chất. Một list không sửa được dĩ nhiên không cho người nhận đổi kích thước qua nó; điều đó không làm `Arrays.asList` trở thành không sửa được, vì list của `Arrays.asList` vẫn hỗ trợ `set`.

## 3. Vì sao kích thước cố định vẫn có thể sửa được?

Mảng có số phần tử cố định nhưng từng ô mảng có thể được gán giá trị khác. Khi một list cung cấp cách truy cập những ô ấy, thay phần tử không đòi tạo thêm hoặc xóa bớt ô.

Đoạn sau là **mã minh họa trong một phương thức**, chưa phải tệp chương trình độc lập:

~~~java
String[] names = {"Lan", "Minh"};
List<String> fixed = Arrays.asList(names);

fixed.set(0, "An");   // Thay tham chiếu tại ô 0.
names[1] = "Binh";    // Sửa cùng vùng lưu phần tử qua mảng.
~~~

`fixed.size()` vẫn là 2. Sau hai phép gán, cả mảng và list biểu diễn hai tên `An`, `Binh`. Không có thao tác thêm phần tử.

Sắp xếp cũng có thể chỉ thay thứ tự các giá trị trong những ô sẵn có. Một danh sách không cần tăng giảm kích thước để được sắp xếp. Chương trình ở phần minh chứng sẽ sắp xếp list do `Arrays.asList` tạo ra và in lại chính mảng.

Điều này giúp giải thích một lỗi thường gặp: nhận `Arrays.asList` rồi gọi `add` vì biến có kiểu `List`. Giao diện có khai báo phương thức không đồng nghĩa mọi đối tượng thực thi đều hỗ trợ thao tác đó. Một số thao tác sửa trong Collections Framework là tùy chọn.

Nếu cần một danh sách có thể thêm/xóa độc lập với mảng, hãy tạo vật chứa có hợp đồng phù hợp:

~~~java
List<String> editable = new ArrayList<>(Arrays.asList(names));
editable.add("Chi");
~~~

Đây vẫn chỉ là sao chép ở tầng danh sách. Với `String`, việc dùng chung phần tử không gây đường sửa nội dung chuỗi; với đối tượng nghiệp vụ sửa được, cần phân tích tiếp.

Không suy diễn rằng tất cả danh sách có cùng kích thước đều có cùng quyền sửa. `List.of("Lan", "Minh")` cũng có hai phần tử nhưng từ chối cả `set`. Số lượng hiện tại, quyền thay phần tử và quyền tăng giảm kích thước là những thông tin khác nhau.

Một chi tiết khi nhận đầu vào từ mảng: `Arrays.asList` nhận các phần tử kiểu tham chiếu. Nếu truyền một `int[]` trực tiếp, kết quả thường là list chứa **một đối tượng mảng `int[]`**, không phải list nhiều `Integer`. Điều đó càng cho thấy phải xác định đúng đối tượng nào đang là “phần tử” trước khi nói đến sao chép và bất biến.

## 4. View chỉ đọc và bản chụp thành phần có mục đích khác nhau

### View không sửa được: người sở hữu nguồn vẫn cập nhật

`Collections.unmodifiableList(source)` trả về view đọc qua danh sách nguồn. Người nhận không được sửa qua list hay iterator của view. Chủ sở hữu nguồn vẫn có thể sửa nguồn và view nhìn thấy thay đổi ấy. [API Collections.unmodifiableList, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)).

Ví dụ có ý nghĩa trong một thành phần quản lý lịch tác vụ: bên ngoài được đọc lịch hiện hành, còn thành phần quản lý cập nhật nguồn bằng những phương thức đã kiểm soát. Hợp đồng có thể là “đọc trạng thái hiện tại”, không phải “giữ nguyên lịch tại lúc gọi”.

Mã minh họa:

~~~java
List<String> source = new ArrayList<>(List.of("queued"));
List<String> live = Collections.unmodifiableList(source);

source.add("running");
// live bây giờ biểu diễn [queued, running].
~~~

Nếu tài liệu của phương thức chỉ ghi “trả về list không sửa được”, người nhận chưa thể kết luận việc lưu list ấy trong một báo cáo sẽ giữ nguyên lịch sử. Cần ghi rõ đây là view sống theo nguồn.

Việc bọc cũng không chuyển quyền sở hữu. Nếu hàm tạo lưu trực tiếp list do bên ngoài truyền rồi bọc lại, bên ngoài vẫn giữ một đường sửa nguồn. Từ chối `result.add(...)` chưa loại bỏ đường sửa `input.add(...)`.

### Bản chụp thành phần: không theo nguồn ở tầng ngoài

`List.copyOf(source)` tạo kết quả không sửa được, giữ thứ tự duyệt đầu vào và không phản ánh những thay đổi thành phần sau đó ở collection nguồn. Nó từ chối phần tử `null`. API cho phép tối ưu tái sử dụng một list thích hợp; không dùng `==` hoặc kỳ vọng “luôn cấp phát đối tượng mới” để kiểm tra tính độc lập. [API List.copyOf, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)).

Ở đây “bản chụp thành phần” nói về những tham chiếu có mặt, số lượng và thứ tự của list. Không có lời hứa rằng toàn bộ trạng thái mỗi đối tượng được nhân bản.

~~~java
List<String> source = new ArrayList<>(List.of("queued"));
List<String> saved = List.copyOf(source);

source.add("running");
// saved vẫn biểu diễn [queued].
~~~

Với các phần tử `String`, cách này thường đủ để làm một giá trị danh sách ổn định. Với các phần tử `MutableTask`, thêm một nhiệm vụ mới vào nguồn không làm list đã lưu dài thêm, nhưng sửa một nhiệm vụ vốn được cả hai list cùng tham chiếu vẫn có thể hiện ở cả hai.

Có thể tạo kết quả tương tự về độc lập tầng ngoài bằng cách bọc một bản sao riêng:

~~~java
List<String> saved =
        Collections.unmodifiableList(new ArrayList<>(source));
~~~

Cần bảo đảm không để lộ bản `ArrayList` bên trong. Cách này cũng cần cân nhắc hợp đồng `null`; nó không giống hoàn toàn `List.copyOf`. Lựa chọn phải xuất phát từ yêu cầu API, không phải thay một dòng cho “hiện đại hơn”.

### Một view có thể ổn định nếu nguồn không còn đường sửa

View chỉ đọc không tự bảo đảm bất biến, nhưng cũng không đồng nghĩa nó chắc chắn thay đổi. Nếu nguồn chỉ được giữ kín bên trong và không bao giờ sửa sau khi công bố, view có thể có trạng thái ổn định.

Điều ấy là kết quả của **quản lý quyền sở hữu**, không phải khả năng tự đóng băng của lớp bọc. Khi người duy trì sau này thêm đường sửa nguồn, lời hứa ổn định có thể bị phá vỡ dù dòng `unmodifiableList` vẫn còn nguyên.

Để một API dễ duy trì, hãy chọn rõ một trong hai ý định: cung cấp trạng thái hiện tại có thể cập nhật, hoặc công bố một giá trị đã chốt. Tên phương thức và tài liệu nên thể hiện ý định đó.

## 5. Không sửa vật chứa khác với không sửa phần tử

Xét một mặt hàng có trường `quantity` và danh sách nhãn `tags`:

~~~text
list ngoài
  └── tham chiếu tới mặt hàng
        ├── quantity
        └── tham chiếu tới list nhãn
              └── các String
~~~

Nếu list ngoài không sửa được, người nhận không được bỏ mặt hàng ra khỏi list hoặc thay mặt hàng bằng một đối tượng khác. Nhưng thao tác `item.setQuantity(7)` hướng tới đối tượng mặt hàng. Nó không gọi `list.set` và không cần quyền sửa list ngoài.

Tương tự, `item.getTags().add("sale")` sửa list nhãn. Có ít nhất hai lớp phải phân tích trước khi chạm tới các chuỗi.

Đoạn minh họa này nhấn mạnh điểm nối giữa các lớp:

~~~java
List<StringBuilder> messages =
        List.of(new StringBuilder("draft"));

messages.get(0).append("-edited");
~~~

List vẫn có một phần tử, cùng tham chiếu cũ, cùng vị trí cũ. Đối tượng `StringBuilder` đổi nội dung. Nhận xét “list không sửa được” vẫn đúng; nhận xét “toàn bộ giá trị chắc chắn không thay đổi” thì sai.

Không thể khắc phục bằng cách chỉ bọc list ngoài thêm một lần. Lớp bọc thứ hai không biến `StringBuilder` thành chuỗi bất biến. Phải chuyển phần tử sang kiểu biểu diễn phù hợp, hoặc sao chép và bảo vệ nó theo hợp đồng rõ ràng.

Một phép biến đổi có chủ đích là lấy nội dung của từng `StringBuilder` thành `String` tại thời điểm chốt:

~~~java
List<String> savedMessages = messages.stream()
        .map(StringBuilder::toString)
        .toList();
~~~

Đây là một phép chuyển mô hình dữ liệu, không phải khả năng sao chép sâu tự động của `toList`. Hàm `map` quyết định phần nào được chụp; `toList` chỉ gom kết quả thành list không sửa được.

Trong Java 17, `Stream.toList()` đã có sẵn từ Java 16. Kết quả của nó không sửa được nhưng không có bảo đảm cụ thể về lớp triển khai. [API Stream.toList, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList()).

Khi phần tử chứa tài nguyên sống, khóa, luồng xử lý hoặc đối tượng có quan hệ vòng, “sao chép toàn bộ đồ thị” thường không còn là một yêu cầu hiển nhiên. Hãy xác định một giá trị nghiệp vụ cần lưu, chẳng hạn mã mặt hàng, số lượng và nhãn, rồi xây dựng biểu diễn ổn định cho chính giá trị ấy.

## 6. Bảo vệ dữ liệu lồng nhau theo mô hình cụ thể

### Trường hợp hai tầng list chứa chuỗi

Giả sử đầu vào có kiểu `List<List<String>>`. `List.copyOf(input)` chỉ giữ riêng các tham chiếu list con. Mỗi list con vẫn là một đối tượng độc lập có thể được sửa bởi người đang giữ nó.

Nếu hợp đồng yêu cầu đóng băng các nhóm chuỗi, có thể chụp từng list con trước, rồi chụp list ngoài:

~~~java
List<List<String>> groups = input.stream()
        .map(group -> List.copyOf(group))
        .toList();
~~~

Đoạn này là mã minh họa với biến `input` đã tồn tại. Nó giả định nguồn không thay đổi trong quá trình đọc và các list con/phần tử đáp ứng chính sách không `null` của `List.copyOf`.

Vì tầng lá ở đây là `String`, không có đường sửa nội dung chuỗi thông thường. Mỗi list con kết quả không sửa được, list ngoài kết quả không sửa được, và các list nguồn không còn điều khiển thành phần của chúng. Với mô hình hai tầng này, lập luận bất biến đi đến được các lá.

Nếu thay `String` bằng `MutableItem`, cùng đoạn mã không còn đủ. Nó sẽ chốt các tầng list nhưng vẫn chia sẻ mặt hàng. Đừng đặt tên hàm chung chung là `deepCopy` rồi dùng cho mọi kiểu phần tử khi nó chỉ xử lý hai lớp list.

### Trường hợp phần tử là dữ liệu nghiệp vụ

Thay vì cố biến đối tượng có hành vi cập nhật thành bất biến, có thể định nghĩa kiểu dành riêng cho dữ liệu đã chốt:

~~~java
record ItemSnapshot(
        String code,
        int quantity,
        List<String> tags
) {
    ItemSnapshot {
        Objects.requireNonNull(code);
        tags = List.copyOf(tags);
    }
}
~~~

Mã trên là khai báo kiểu minh họa; cần `import java.util.List` và `import java.util.Objects`. Chương trình hoàn chỉnh bên dưới chứa khai báo này và cách dùng.

Lập luận cho mô hình ấy gồm các bước:

- `quantity` là giá trị nguyên, không phải tham chiếu tới một ô số lượng có thể sửa bên ngoài.
- `code` là `String` và được yêu cầu không `null`.
- `tags` được giữ dưới dạng list không sửa được, không theo thành phần của list đầu vào.
- Từng nhãn là `String`. Không còn một đối tượng nhãn sửa được bị chia sẻ.
- Không thêm phương thức nào làm lộ một nguồn sửa được ẩn phía sau các trường này.

Hàm tạo chính là ranh giới nhận dữ liệu. Nếu chỉ có accessor chỉ đọc nhưng hàm tạo vẫn lưu nguyên list đầu vào, người tạo đối tượng vẫn có thể sửa qua list cũ.

Tài liệu về thiết kế đối tượng bất biến nhấn mạnh việc kiểm soát các tham chiếu tới dữ liệu sửa được ở cả đầu vào lẫn đầu ra. Sao chép phòng vệ phải phục vụ mô hình dữ liệu cụ thể. [Hướng dẫn thiết kế đối tượng bất biến của Oracle](https://docs.oracle.com/javase/tutorial/essential/concurrency/imstrat.html).

Không cần trả một bản sao mới của `tags` sau mỗi lần gọi accessor trong mô hình này: bản thân list đã được chốt và lá là chuỗi. Với một trường `byte[]` thì tình huống khác: mảng luôn còn các ô gán được, nên cần sao chép khi nhận vào và khi trả ra, hoặc dùng một kiểu biểu diễn có hợp đồng thích hợp.

### Sao chép sâu và bất biến sâu không đồng nghĩa

Nếu sao chép mặt hàng thành đối tượng mới nhưng đối tượng mới vẫn có setter công khai, người nhận vẫn sửa được bản sao. Đó có thể là bản sao độc lập có thể chỉnh sửa, rất hữu ích cho một biểu mẫu, nhưng chưa phải giá trị bất biến sâu.

Ngược lại, một cấu trúc bất biến sâu có thể chia sẻ rất nhiều phần đã bất biến. Một list chốt các `ItemSnapshot` không nhất thiết phải tạo lại mọi chuỗi. Bảo đảm quan trọng là thay đổi dữ liệu không thể lan qua một phần sửa được đang chia sẻ.

Vì vậy hãy nói chính xác mình cần gì: “bản sao để người nhận sửa riêng” hay “giá trị không được thay đổi sau khi công bố”. Hai mục tiêu dẫn tới cách thiết kế khác nhau.

## 7. `final` và `record` bảo vệ đến đâu?

### `final` khóa biến tham chiếu

~~~java
final List<String> labels = new ArrayList<>();
labels.add("new");
~~~

Đoạn này hợp lệ. `final` ngăn gán `labels` sang một list khác sau khi đã khởi tạo; nó không tước quyền `add` của đối tượng `ArrayList` đang được tham chiếu.

Điều tương tự áp dụng cho `final String[]`: biến luôn giữ cùng mảng nhưng từng ô vẫn có thể được gán lại. Đây là quy tắc ngôn ngữ về biến, không phải bộ đóng băng đối tượng. [JLS 17, mục 4.12.4 về biến final](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4).

Đừng dùng việc thấy nhiều trường `private final` như bằng chứng duy nhất của bất biến sâu. Còn phải xét những đối tượng các trường ấy trỏ tới và các đường truy cập vào chúng.

### `record` không tự sao chép các thành phần

~~~java
record Tags(List<String> values) {
}
~~~

Một `record` như trên nhận và giữ giá trị tham chiếu của thành phần. Nếu bên ngoài giữ list đầu vào, họ vẫn có thể sửa list ấy. Accessor mặc định cũng trả giá trị của thành phần, nên người nhận có thể gọi `values().add(...)` nếu list thực tế hỗ trợ.

API `Record` mô tả record là vật mang dữ liệu bất biến nông; hàm tạo và accessor có thể cần được khai báo để kiểm tra đầu vào hoặc sao chép phòng vệ. [API Record, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html).

Mô hình `ItemSnapshot` đã xử lý vấn đề bằng hàm tạo gọn (compact constructor). Việc viết `tags = List.copyOf(tags)` thay tham số sẽ được dùng để khởi tạo trường. Tuy vậy, bảo đảm sâu đến từ cả kiểu lá `String` và việc chốt list; chỉ riêng cú pháp `record` không làm điều đó.

Một record chứa list phần tử sửa được có thể đổi kết quả `equals` hoặc `hashCode` theo trạng thái các thành phần. Đừng dùng một giá trị như thế làm khóa rồi sửa dữ liệu tham gia so sánh. Tên gọi “record” không loại bỏ nghĩa vụ kiểm soát trạng thái.

## 8. Minh chứng chạy được: kiểm tra từng đường thay đổi

Lưu toàn bộ mã dưới đây vào **`CollectionProtectionDemo.java`**. Chương trình dùng API và cú pháp tương thích Java 17, không cần thư viện ngoài.

`MutableItem` cố ý giữ danh sách nhãn đầu vào để tạo tình huống có dữ liệu chia sẻ. Đây là mô hình dùng cho thí nghiệm. `ItemSnapshot` là biểu diễn đã được bảo vệ của cùng những thông tin được chọn.

Hàm `rejected` chỉ coi một thao tác là bị chặn khi nhận đúng `UnsupportedOperationException`. Nếu thao tác bất ngờ thành công, chương trình dừng bằng `AssertionError`. Tương tự, `rejectsNull` kiểm tra đúng `NullPointerException`. Chương trình không dựa vào thông báo ngoại lệ, tên lớp triển khai nội bộ hay thứ tự duyệt map.

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectionProtectionDemo {
    static final class MutableItem {
        private final String code;
        private int quantity;
        private final List<String> tags;

        MutableItem(String code, int quantity, List<String> tags) {
            this.code = Objects.requireNonNull(code);
            this.quantity = quantity;
            this.tags = Objects.requireNonNull(tags);
        }

        ItemSnapshot snapshot() {
            return new ItemSnapshot(code, quantity, tags);
        }

        @Override
        public String toString() {
            return code + ":" + quantity + ":" + tags;
        }
    }

    record ItemSnapshot(String code, int quantity, List<String> tags) {
        ItemSnapshot {
            Objects.requireNonNull(code);
            tags = List.copyOf(tags);
        }
    }

    record PlainTags(List<String> values) {
    }

    record SafeTags(List<String> values) {
        SafeTags {
            values = List.copyOf(values);
        }
    }

    private static void rejected(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " unexpectedly succeeded");
        } catch (UnsupportedOperationException ex) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    private static void rejectsNull(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " unexpectedly succeeded");
        } catch (NullPointerException ex) {
            System.out.println(label + "=NullPointerException");
        }
    }

    public static void main(String[] args) {
        String[] words = {"pear", "apple"};
        List<String> fixed = Arrays.asList(words);
        fixed.set(0, "plum");
        System.out.println("array.afterSet=" + Arrays.toString(words));
        words[1] = "apricot";
        System.out.println("fixed.afterArrayEdit=" + fixed);
        Collections.sort(fixed);
        System.out.println("array.afterSort=" + Arrays.toString(words));
        rejected("fixed.add", () -> fixed.add("orange"));
        rejected("fixed.remove", () -> fixed.remove(0));

        List<String> source = new ArrayList<>(List.of("A", "B"));
        List<String> live = Collections.unmodifiableList(source);
        List<String> detached = List.copyOf(source);
        source.set(0, "A2");
        source.add("C");
        System.out.println("live=" + live);
        System.out.println("detached=" + detached);
        rejected("live.set", () -> live.set(0, "X"));
        Iterator<String> iterator = live.iterator();
        iterator.next();
        rejected("live.iterator.remove", iterator::remove);

        List<String> itemTags = new ArrayList<>(List.of("school"));
        MutableItem item = new MutableItem("pen", 2, itemTags);
        List<MutableItem> outer = List.of(item);
        List<ItemSnapshot> snapshots = outer.stream()
                .map(MutableItem::snapshot)
                .toList();
        item.quantity = 7;
        itemTags.add("sale");
        System.out.println("outer.afterItemEdit=" + outer);
        System.out.println("snapshot=" + snapshots);
        rejected("outer.set", () -> outer.set(0, item));
        rejected("snapshot.tags.add",
                () -> snapshots.get(0).tags().add("extra"));

        List<String> inner = new ArrayList<>(List.of("x"));
        List<List<String>> nested = new ArrayList<>();
        nested.add(inner);
        List<List<String>> shallow = List.copyOf(nested);
        List<List<String>> deepForStrings = nested.stream()
                .map(xs -> List.copyOf(xs))
                .toList();
        inner.add("y");
        nested.add(List.of("new"));
        System.out.println("nested.source=" + nested);
        System.out.println("nested.shallow=" + shallow);
        System.out.println("nested.deep=" + deepForStrings);
        rejected("nested.deep.inner.add",
                () -> deepForStrings.get(0).add("z"));

        List<String> incoming = new ArrayList<>(List.of("base"));
        PlainTags plain = new PlainTags(incoming);
        SafeTags safe = new SafeTags(incoming);
        incoming.add("later");
        System.out.println("record.plain=" + plain.values());
        System.out.println("record.safe=" + safe.values());
        rejected("record.safe.accessor.add", () -> safe.values().add("x"));

        final List<String> finalReference = new ArrayList<>();
        finalReference.add("still mutable");
        System.out.println("final.reference=" + finalReference);

        Map<String, List<String>> map =
                Map.of("items", new ArrayList<>(List.of("one")));
        map.get("items").add("two");
        System.out.println("map.value=" + map.get("items"));
        rejected("map.put", () -> map.put("items", List.of("other")));

        List<String> withNull = Arrays.asList("ok", null);
        System.out.println("fixed.null=" + withNull);
        rejectsNull("copyOf.null", () -> List.copyOf(withNull));
        rejectsNull("of.null", () -> List.of("ok", (String) null));

        List<String> streamList = Stream.of("s", "t").toList();
        rejected("stream.set", () -> streamList.set(0, "S"));
        ArrayList<String> editable = Stream.of("s", "t")
                .collect(Collectors.toCollection(ArrayList::new));
        editable.add("u");
        System.out.println("stream.editable=" + editable);
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 CollectionProtectionDemo.java
java CollectionProtectionDemo
~~~

Dùng JDK 17 trở lên để biên dịch. Tùy chọn `--release 17` giới hạn ngôn ngữ và API ở Java 17; nó không biến JDK đang chạy thành JDK 17. Ví dụ đã được biên dịch với tùy chọn trên và chạy trên OpenJDK 24.0.2, thoát thành công với stdout chính xác sau:

~~~text
array.afterSet=[plum, apple]
fixed.afterArrayEdit=[plum, apricot]
array.afterSort=[apricot, plum]
fixed.add=UnsupportedOperationException
fixed.remove=UnsupportedOperationException
live=[A2, B, C]
detached=[A, B]
live.set=UnsupportedOperationException
live.iterator.remove=UnsupportedOperationException
outer.afterItemEdit=[pen:7:[school, sale]]
snapshot=[ItemSnapshot[code=pen, quantity=2, tags=[school]]]
outer.set=UnsupportedOperationException
snapshot.tags.add=UnsupportedOperationException
nested.source=[[x, y], [new]]
nested.shallow=[[x, y]]
nested.deep=[[x]]
nested.deep.inner.add=UnsupportedOperationException
record.plain=[base, later]
record.safe=[base]
record.safe.accessor.add=UnsupportedOperationException
final.reference=[still mutable]
map.value=[one, two]
map.put=UnsupportedOperationException
fixed.null=[ok, null]
copyOf.null=NullPointerException
of.null=NullPointerException
stream.set=UnsupportedOperationException
stream.editable=[s, t, u]
~~~

### Đọc kết quả theo quan hệ nguyên nhân

**Ba dòng đầu:** `fixed.set` đổi mảng, gán vào mảng đổi list, và sắp xếp list đổi thứ tự trong mảng. Kích thước luôn là 2. Hai dòng ngoại lệ kế tiếp xác nhận những thao tác thêm/xóa thực sự bị từ chối.

**`live` và `detached`:** hai kết quả cùng được tạo trước khi nguồn đổi. `live` nhìn thấy cả `A2` và `C`; `detached` vẫn giữ `A` và `B`. Không phải `live` được quyền sửa, mà chủ sở hữu nguồn đã sửa qua một tham chiếu khác. Iterator của view cũng không mở một lối xóa vòng qua lớp bọc.

**`outer.afterItemEdit` và `snapshot`:** list ngoài `outer` không cho `set` nhưng vẫn phản ánh mặt hàng có số lượng 7 và nhãn `sale`. `snapshots` đã lấy số lượng 2 và chốt nhãn từ trước. Như vậy hai kết quả khác nhau ở cách biểu diễn phần tử, không chỉ ở quyền sửa list ngoài.

**Ba dòng `nested`:** nguồn có thêm nhóm mới và list con đầu tiên có thêm `y`. Bản sao nông không nhận nhóm mới nhưng vẫn thấy `y` vì chia sẻ list con. Bản đã chụp từng nhóm giữ `[[x]]`. Thao tác thêm vào list con kết quả bị chặn.

**Hai record:** `PlainTags` theo list đầu vào nên nhìn thấy `later`. `SafeTags` đã chốt list trong hàm tạo nên giữ `[base]`; accessor cũng không cung cấp một list thêm được. Đây là kiểm tra cả đường đầu vào và đường đầu ra của mô hình chứa chuỗi.

**`final.reference`:** một biến `final` vẫn có thể trỏ tới list cho phép thêm. Không có phép gán lại biến trong chương trình.

**Hai dòng `map`:** ánh xạ `"items" → list` không cho thay, nhưng list đang làm giá trị vẫn thêm được `"two"`. Quyền sửa map và quyền sửa đối tượng giá trị là hai quyền khác nhau.

**Các dòng cuối:** `Arrays.asList` có thể chứa `null`, còn `List.copyOf` và `List.of` từ chối `null` trong tình huống thử. Kết quả `Stream.toList` không cho thay phần tử; khi thực sự cần một `ArrayList` chỉnh sửa, chương trình yêu cầu rõ bằng `Collectors.toCollection(ArrayList::new)`.

Không có dòng nào cố sửa nguồn trong khi đang duyệt nó. Kết quả kiểm chứng là hành vi trong một luồng với thứ tự thao tác rõ ràng; không dùng nó để suy ra bảo đảm chụp nguyên tử khi nhiều luồng cùng cập nhật.

## 9. Áp dụng cho `Set`, `Map` và kết quả stream

### Set: không có `set(index)` nhưng phần tử vẫn có trạng thái

Set không có khái niệm vị trí như list. Vì thế sự phân biệt “thay phần tử tại một vị trí” không áp nguyên xi, nhưng việc bảo vệ vật chứa và trạng thái phần tử vẫn tách biệt.

`Set.of` và `Set.copyOf` tạo set không sửa được. Một phần tử sửa được vẫn có thể làm trạng thái quan sát của set thay đổi; đặc biệt phải giữ ổn định những dữ liệu tham gia `equals` và `hashCode` khi phần tử đang ở trong set. [API Set, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html).

Không nên thử rồi ghi nhớ một kết quả cụ thể của `contains` sau khi đã làm biến đổi dữ liệu so sánh của phần tử. Cách đúng là tránh trạng thái ấy: dùng phần tử có định danh ổn định hoặc một giá trị đã chốt.

### Map: bảo vệ ánh xạ không đóng băng khóa hay giá trị

Map không phải kiểu con của `Collection`, nhưng cùng cách suy luận vẫn hữu ích. Không sửa map nghĩa là không thêm/xóa/thay ánh xạ qua API map. Khóa và giá trị có thể chứa dữ liệu riêng.

`Map.of` và `Map.copyOf` tạo map không sửa được, từ chối khóa/giá trị `null`. Chúng không hứa đóng băng đối tượng khóa hay giá trị, cũng không hứa giữ thứ tự duyệt của một `LinkedHashMap` đầu vào. [API Map, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#unmodifiable).

Với `Map<String, List<String>>` dùng làm cấu hình đã chốt, cần bảo vệ mỗi list giá trị rồi mới chốt map ngoài. Nếu cần thứ tự cụ thể khi trình bày, hãy chọn hợp đồng giữ thứ tự phù hợp và kiểm soát nguồn riêng; đừng lấy một kết quả tình cờ của `Map.copyOf` làm bảo đảm.

Nếu khóa là một đối tượng sửa được, bảo vệ map ngoài cũng không ngăn ai đó sửa chính khóa. Đây là một lý do mạnh để dùng khóa mang giá trị ổn định.

### Stream: thao tác gom không quyết định mọi tầng

Đừng đồng nhất ba biểu thức sau:

~~~java
List<String> a = streamA.toList();
List<String> b = streamB.collect(Collectors.toList());
ArrayList<String> c =
        streamC.collect(Collectors.toCollection(ArrayList::new));
~~~

Đây là đoạn minh họa với ba stream độc lập đã tồn tại; một stream đã tiêu thụ không được dùng lại.

- `toList()` của `Stream` có hợp đồng kết quả không sửa được.
- `Collectors.toList()` không bảo đảm tính sửa được hay lớp triển khai của list kết quả.
- `toCollection(ArrayList::new)` yêu cầu rõ kiểu vật chứa có thể chỉnh sửa mà chương trình mong muốn.

Hợp đồng của `Collectors.toList` không cho phép suy ra “luôn là `ArrayList`” từ một lần chạy. [API Collectors, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#toList()).

Cả ba cách đều chưa tự chuyển `MutableItem` thành `ItemSnapshot`. Phép biến đổi phần tử phải được viết rõ trong `map` hoặc một bước xây dựng dữ liệu tương đương. Cũng không nên suy ra chính sách `null` của mọi cách gom từ chính sách của `List.of`; mỗi API có hợp đồng riêng.

## 10. Chọn bảo đảm theo nhu cầu của API

### Người nhận cần tự chỉnh sửa một bản riêng

Trả một bản sao vật chứa sửa được có thể là lựa chọn đúng. Ví dụ giao diện chỉnh sửa lấy danh sách mã mặt hàng rồi thêm/bớt trong biểu mẫu:

~~~java
List<String> editableCodes = new ArrayList<>(storedCodes);
~~~

Với mã là chuỗi, việc chia sẻ phần tử không tạo đường sửa nội dung mã. Nếu danh sách chứa đối tượng có setter, phải quyết định biểu mẫu có được sửa trực tiếp chúng không. Một bản sao list không tự tạo “phiên chỉnh sửa riêng” cho toàn bộ mô hình.

### Người nhận cần đọc trạng thái hiện tại

View không sửa được có thể phù hợp khi người sở hữu quản lý cập nhật. Tài liệu nên nói rõ kết quả có thể phản ánh cập nhật sau đó, và các quy tắc dùng đồng thời nếu có.

Tên như `currentTasksView` giúp nêu ý định nhưng không thay thế hợp đồng. Người gọi vẫn cần biết vòng đời của nguồn và quyền sửa các phần tử.

### Người nhận cần giá trị đã chốt cho báo cáo hoặc cấu hình

Khi các phần tử đã bất biến, chốt vật chứa thường đủ. Khi phần tử sửa được, hãy ánh xạ sang kiểu dữ liệu chốt như `ItemSnapshot`. Với mô hình đã lồng nhiều tầng, hãy viết quy tắc bảo vệ từng tầng có thể sửa.

Đừng hứa “snapshot” mơ hồ. Có thể chỉ là bản chụp số lượng và thứ tự các tham chiếu; có thể là bản chụp đầy đủ các trường cần cho báo cáo. Nêu rõ những trường nào được chốt sẽ giúp người gọi hiểu và kiểm thử đúng.

### Cần giữ số ô nhưng cho thay giá trị

Một list kích thước cố định phù hợp với thao tác trên các vị trí sẵn có hoặc làm cầu nối với mảng. Không cần chuyển sang một collection không sửa được nếu yêu cầu thật sự vẫn cho thay phần tử.

Điều đáng kiểm tra là thuật toán phía sau đòi quyền gì. Một thuật toán chỉ thay/sắp xếp vị trí có thể làm việc với list kích thước cố định; thuật toán thêm/xóa cần hợp đồng khác.

### Các chi phí phải gắn với việc thật sự làm

Bọc một view không có cùng mục tiêu với duyệt và sao chép dữ liệu. Chốt các nhóm hoặc chuyển mỗi phần tử sang snapshot có chi phí đọc và tạo dữ liệu theo số phần tử, số tầng và lượng thông tin được giữ. Không thể so hiệu năng chỉ bằng số ký tự của lời gọi API.

Chọn đúng bảo đảm trước, rồi đo nếu chi phí có ý nghĩa. Sao chép sâu tùy ý có thể đắt và còn sai về ý nghĩa nghiệp vụ; chốt đúng dữ liệu cần dùng thường dễ giải thích và kiểm chứng hơn.

## 11. Những kết luận cần tránh khi đọc mã “chỉ đọc”

### “Có `UnsupportedOperationException` nghĩa là bất biến”

Ngoại lệ từ `add` chỉ chứng minh thao tác `add` đó bị từ chối trong tình huống đã thử. Nó không chứng minh `set` bị chặn, nguồn không thay đổi hay phần tử bất biến.

Không nên thăm dò quyền sửa bằng cách gọi các thao tác không tạo tác dụng rồi đoán hợp đồng, chẳng hạn xóa một phần tử vốn không có. Một số phương thức tùy chọn có thể không ném ngoại lệ khi không có gì cần thay đổi. Hãy đọc hợp đồng API và kiểm tra các thao tác thực sự liên quan.

### “Cùng kích thước nghĩa là không thay đổi”

Một list có thể giữ nguyên `size()` nhưng đổi toàn bộ tham chiếu và thứ tự. Một map có thể giữ nguyên số khóa nhưng thay các giá trị. Một list có thể giữ nguyên cả tham chiếu mà trạng thái phần tử bên trong vẫn đổi.

Kiểm thử cần quan sát đúng điều API hứa bảo vệ, chẳng hạn nội dung bản báo cáo sau khi nguồn được cập nhật, chứ không chỉ so kích thước.

### “Sao chép xong là bất biến”

`new ArrayList<>(source)` thường được dùng chính vì người nhận muốn tiếp tục sửa bản sao. Tách nguồn và cấm sửa là hai quyết định độc lập.

Nếu cần cấm sửa kết quả, phải chọn vật chứa có hợp đồng tương ứng; nếu cần cấm mọi thay đổi giá trị quan sát, còn phải xử lý phần tử.

### “Bọc list ở getter là bảo vệ xong đối tượng”

Có thể vẫn tồn tại đường sửa từ tham số hàm tạo, một getter khác, một phương thức cập nhật, hoặc một phần tử chứa list lồng bên trong. Kiểm tra cả đầu vào và mọi đường trả dữ liệu.

Với dữ liệu như mảng, sao chép đầu vào mà trả lại chính mảng nội bộ vẫn để lộ đường sửa. Ngược lại, sao chép đầu ra mà giữ nguyên mảng đầu vào vẫn cho người tạo sửa ngầm bên trong.

### “Không sửa được thì an toàn đồng thời”

Một view không sửa được trên nguồn đang được cập nhật vẫn là dữ liệu sống. Một list ngoài không sửa được chứa đối tượng đang bị nhiều luồng sửa vẫn có vấn đề đồng thời ở những đối tượng đó.

Giá trị bất biến sâu giúp chia sẻ dữ liệu dễ hơn vì không cần phối hợp các lần ghi vào chính giá trị ấy sau xây dựng. Tuy vậy vẫn phải công bố tham chiếu đúng và xử lý đúng việc thay toàn bộ phiên bản, các tài nguyên bên ngoài hoặc nhiều thao tác cần thống nhất. [Khái niệm đối tượng bất biến trong tài liệu đồng thời của Oracle](https://docs.oracle.com/javase/tutorial/essential/concurrency/immutable.html).

Gọi `List.copyOf` hoặc lặp để tạo snapshot không tự biến quá trình đọc nhiều đối tượng đang bị sửa thành một giao dịch nguyên tử. Cần một giao thức nhất quán với phía cập nhật, chẳng hạn cùng cơ chế khóa, nguồn có phiên bản hoặc dữ liệu vốn đã chốt.

### “Bất biến sâu nghĩa là không cần nói rõ phạm vi”

Một kết quả có thể chốt thông tin hiển thị nhưng vẫn chỉ giữ mã tham chiếu tới một dữ liệu bên ngoài sẽ đổi. Điều đó không sai nếu hợp đồng chỉ chốt mã và thông tin đã chọn. Nó chỉ sai khi được quảng cáo là đóng băng mọi trạng thái của hệ thống.

Hãy chứng minh trên đồ thị dữ liệu thực sự tạo thành giá trị trả về. Không đưa tài nguyên không thuộc giá trị, như cơ sở dữ liệu sống, vào lời hứa bất biến của một list.

## 12. Thực hành có đáp án

Hãy tự trả lời trước khi đọc phần giải thích. Với mỗi bài, chỉ ra **đường tham chiếu nào** cho phép quan sát hoặc tạo thay đổi, thay vì chỉ gắn nhãn “mutable/immutable”.

### Bài 1 — Không thêm được nhưng vẫn thay được

Đoạn này chạy trong một phương thức, có đủ import:

~~~java
String[] data = {"a", "b"};
List<String> values = Arrays.asList(data);
values.set(1, "B");
~~~

**Câu hỏi:** `data[1]` là gì? `values.add("c")` có được hỗ trợ không? Cần thay cách tạo thế nào nếu người nhận phải thêm phần tử?

**Đáp án:** `data[1]` là `"B"`, vì list dùng các ô của cùng mảng. Thêm làm đổi kích thước bị từ chối bằng `UnsupportedOperationException`. Có thể dùng `new ArrayList<>(Arrays.asList(data))` để có list thêm được và tách cấu trúc khỏi mảng. Vì các phần tử là chuỗi nên không cần nhân bản chuỗi để tránh sửa nội dung.

### Bài 2 — View đổi dù người nhận không sửa

~~~java
List<String> original = new ArrayList<>(List.of("a"));
List<String> result = Collections.unmodifiableList(original);
original.add("b");
~~~

**Câu hỏi:** `result` có bao nhiêu phần tử? Lời hứa nào phù hợp với kết quả này?

**Đáp án:** Có 2 phần tử. `result` đọc qua `original`; cập nhật qua `original` được quan sát qua view. Lời hứa phù hợp là “không sửa qua kết quả, nhìn thấy trạng thái nguồn”. Nếu muốn giữ các chuỗi tại lúc gọi, dùng một bản chụp như `List.copyOf(original)` **tại thời điểm cần chốt**, không phải sau khi đã thêm `b` nếu mục tiêu là dữ liệu trước đó.

### Bài 3 — Bản sao nông giữ nguyên tham chiếu phần tử

~~~java
StringBuilder text = new StringBuilder("a");
List<StringBuilder> source = new ArrayList<>(List.of(text));
List<StringBuilder> saved = List.copyOf(source);
source.clear();
text.append("b");
~~~

**Câu hỏi:** `saved.size()` bằng bao nhiêu? `saved.get(0).toString()` là gì? Làm thế nào để giữ nội dung văn bản ban đầu?

**Đáp án:** Kích thước là 1; văn bản là `"ab"`. `clear` nguồn không đổi thành phần của `saved`, nhưng `text` là đối tượng cùng được chia sẻ. Cần chuyển sang `String` ở thời điểm chốt, chẳng hạn `source.stream().map(StringBuilder::toString).toList()` **trước `clear` và trước `append`**. Sau khi nguồn đã bị làm rỗng, không thể khôi phục nội dung cũ chỉ từ nguồn ấy bằng thao tác sao chép.

### Bài 4 — Đóng băng tầng nào?

~~~java
List<String> row = new ArrayList<>(List.of("x"));
List<List<String>> table = List.of(row);
row.add("y");
~~~

**Câu hỏi:** Giá trị quan sát của `table` là gì? Thay đổi ít nhất về cách xây dựng để giữ nhóm chuỗi ban đầu?

**Đáp án:** `table` biểu diễn `[[x, y]]`. List ngoài không cho thêm/xóa nhưng chia sẻ `row`. Tại thời điểm cần chốt, tạo `List.of(List.copyOf(row))` hoặc, với nhiều nhóm, ánh xạ từng nhóm qua `List.copyOf` rồi gom thành list ngoài không sửa được. Nếu tạo sau khi thêm `y`, bản chốt hợp lệ sẽ chứa `y`; sao chép không quay ngược thời gian.

### Bài 5 — Record nào bảo vệ danh sách nhãn?

So sánh hai khai báo:

~~~java
record DraftLabels(List<String> labels) {
}

record SavedLabels(List<String> labels) {
    SavedLabels {
        labels = List.copyOf(labels);
    }
}
~~~

**Câu hỏi:** Khi đối tượng được tạo từ một `ArrayList<String>`, kiểu nào không theo thay đổi thành phần của list đầu vào? Vì sao kết luận chỉ gắn với kiểu lá `String` ở đây?

**Đáp án:** `SavedLabels` chốt list trong hàm tạo. `DraftLabels` chỉ giữ tham chiếu đầu vào. Với `SavedLabels`, accessor trả list không sửa được và các chuỗi không cho đổi nội dung, nên mô hình này có giá trị ổn định. Nếu đổi thành `List<MutableLabel>`, `copyOf` chưa bảo vệ trạng thái từng nhãn; cần biểu diễn nhãn bất biến hoặc chuyển từng nhãn sang dữ liệu chốt.

### Bài 6 — Sao chép sâu vẫn có thể sửa

Một hàm tạo `new ArrayList<MutableItem>()` và tạo một `MutableItem` mới cho mỗi phần tử. Mỗi mặt hàng mới có list nhãn riêng, nhưng vẫn có setter số lượng và phương thức thêm nhãn.

**Câu hỏi:** Kết quả có độc lập với nguồn không? Có bất biến sâu không? Nếu bọc list ngoài bằng `unmodifiableList` thì sao?

**Đáp án:** Nếu mọi phần sửa được cần tách đã thật sự được sao chép, người nhận có thể có bản độc lập để chỉnh sửa. Nó chưa bất biến sâu vì các đối tượng mới vẫn sửa được. Bọc list ngoài chỉ chặn sửa vật chứa; setter và thêm nhãn trên mặt hàng vẫn còn. Cần chuyển sang một mô hình giá trị không có đường sửa được công bố, hoặc kiểm soát đầy đủ mọi tham chiếu tới phần bên trong.

### Bài 7 — Map không sửa được chứa list sửa được

~~~java
Map<String, List<String>> options =
        Map.of("modes", new ArrayList<>(List.of("fast")));
options.get("modes").add("safe");
~~~

**Câu hỏi:** `options.put("modes", List.of("other"))` và lời gọi `add` trên có cùng đối tượng đích không? Cần làm gì để chốt hai chế độ tại thời điểm tạo?

**Đáp án:** `put` hướng tới map và bị từ chối. `add` hướng tới list giá trị và được hỗ trợ trong ví dụ. Để chốt dữ liệu chuỗi, xây dựng giá trị bằng `List.copyOf(inputModes)` rồi đưa giá trị ấy vào map không sửa được, hoặc dùng `List.of` nếu biết sẵn từng chuỗi. Nếu một danh sách khác vẫn dùng chung đối tượng phần tử sửa được, phải xử lý tầng đó tiếp.

### Bài 8 — Viết hợp đồng trước khi chọn API

Ba phương thức cần trả về:

1. Danh sách mã để người nhận tự thêm/xóa mà không đổi danh sách mã của dịch vụ.
2. Danh sách tác vụ chỉ cho đọc nhưng phải nhìn thấy dịch vụ cập nhật sau đó.
3. Báo cáo mặt hàng giữ nguyên mã, số lượng và nhãn tại lúc chốt.

**Câu hỏi:** Chọn cách tạo phù hợp và nêu một giới hạn của từng cách.

**Đáp án:**

1. Có thể dùng `new ArrayList<>(codes)`. Nó tách vật chứa và cho chỉnh sửa; nếu `codes` có phần tử sửa được thay vì chuỗi thì vẫn cần xét quyền sửa phần tử.
2. Có thể dùng `Collections.unmodifiableList(tasks)`. Nó theo nguồn; phải ghi rõ quyền sửa tác vụ bên trong và quy tắc dùng đồng thời. Không hứa giữ nguyên lịch sử.
3. Chuyển từng mặt hàng thành `ItemSnapshot` với các trường cần báo cáo được bảo vệ, rồi gom thành list không sửa được. Cần bảo đảm việc đọc nguồn để chốt là nhất quán theo yêu cầu nghiệp vụ; các lời gọi sao chép tự chúng không tạo một thời điểm chụp nguyên tử cho toàn hệ thống.

Khi đánh giá một kết quả “chỉ đọc”, hãy lần theo từng đường thay đổi: từ biến đến vật chứa, từ vật chứa đến phần tử, rồi đến dữ liệu lồng bên trong. Một bảo đảm chỉ có ý nghĩa khi chỉ rõ nó chặn đường nào và còn cho phép quan sát thay đổi qua đường nào.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unmodifiable-collections-002">Collections.unmodifiableList, unmodifiableSet và unmodifiableMap</a>

<details>
<summary>Xem chi tiết</summary>

# Collections.unmodifiableList, unmodifiableSet và unmodifiableMap

## 1. Cung cấp quyền đọc qua một đường tham chiếu

Một lớp đang giữ danh sách, tập hợp hoặc map sửa được, nhưng muốn người nhận chỉ đọc qua kết quả được trả về. Ba phương thức trong bài tạo các view không cho sửa qua giao diện của chúng:

| Phương thức | Đối số | Kết quả |
|---|---|---|
| Collections.unmodifiableList(...) | List | List không cho sửa qua wrapper |
| Collections.unmodifiableSet(...) | Set | Set không cho sửa qua wrapper |
| Collections.unmodifiableMap(...) | Map | Map không cho sửa qua wrapper |

Wrapper là lớp bọc: nó cung cấp một đường truy cập khác lên cùng dữ liệu nguồn. Các thao tác đọc dựa trên nguồn; các thao tác sửa qua wrapper bị từ chối bằng UnsupportedOperationException.

Điểm cần nắm ngay từ đầu là **không cho sửa qua wrapper không đồng nghĩa dữ liệu nguồn không bao giờ đổi**. Nếu nơi khác còn giữ nguồn sửa được, nơi đó vẫn có thể thay đổi nguồn. View sẽ đọc dữ liệu nguồn theo hợp đồng của nó.

Một mô hình đơn giản:

```text
Chủ sở hữu ──→ collection nguồn sửa được
                         ↑
Người nhận ──→ wrapper đọc dữ liệu nguồn
              add/remove/... bị từ chối qua đường này
```

Các ví dụ đầy đủ trong bài dùng Java 17 trở lên. List được minh họa bằng ArrayList; Set và Map dùng LinkedHashSet/LinkedHashMap để thứ tự stdout được xác định rõ.

### 1.1. Vì sao dùng wrapper?

Wrapper hữu ích khi muốn cho người khác quan sát dữ liệu hiện tại nhưng giữ các thao tác cập nhật trong lớp chủ sở hữu. Nó cũng giúp diễn đạt rằng một kết quả không được sửa trực tiếp.

Ví dụ, một registry cho phép đăng ký bằng phương thức có kiểm tra điều kiện. Trả trực tiếp List nội bộ sẽ cho người gọi add mà bỏ qua điều kiện đó. Trả một view không cho sửa sẽ buộc các cập nhật collection qua API mà registry công bố.

Tuy vậy, vẫn phải xét các tham chiếu khác và trạng thái phần tử. Một List không cho add chưa tự ngăn người nhận sửa một đối tượng mutable được get từ List.

## 2. Hợp đồng chung cần phân biệt

### 2.1. Đọc từ nguồn, không sao chép toàn bộ dữ liệu

Ba phương thức unmodifiable trong bài tạo view được hỗ trợ bởi đối số. Chúng không có hợp đồng thu mọi phần tử vào một collection chứa riêng như new ArrayList<>(source).

Nếu nguồn thêm phần tử, xóa phần tử hoặc đổi một ánh xạ hợp lệ, việc đọc wrapper phản ánh nguồn theo hợp đồng. Không gọi wrapper là một snapshot cố định tại thời điểm bọc.

Không dựa vào danh tính đối tượng wrapper. API có thể trả lại một đối số đã không cho sửa phù hợp; điều cần dùng là hợp đồng, không phải kỳ vọng mỗi lần bọc chắc chắn tạo đối tượng khác theo ==.

Nguồn đối chiếu: [Collections — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html), các mục unmodifiableList, unmodifiableSet và unmodifiableMap.

### 2.2. Phương thức sửa vẫn xuất hiện ở mức kiểu

List vẫn khai báo add, set, remove; Map vẫn khai báo put và compute. Wrapper không tạo một giao diện Java mới chỉ có các phương thức đọc.

Vì vậy, mã gọi view.add có thể biên dịch rồi ném UnsupportedOperationException lúc chạy. Đổi kiểu tham chiếu hay thêm final trước biến không mở lại quyền sửa của wrapper.

final chỉ ngăn gán lại biến tham chiếu theo quy tắc ngôn ngữ. Nó không biến collection nguồn thành một đối tượng không đổi và không thay vai trò của unmodifiable wrapper.

### 2.3. Các đường sửa gián tiếp cũng cần được chặn

Chỉ chặn view.add là chưa đủ cho hợp đồng không cho sửa. Iterator có remove, List có ListIterator và subList, Map có keySet, values, entrySet cùng các Entry.

Các wrapper chuẩn trong bài bảo vệ cả những đường sửa qua các view hoặc iterator mà chúng trả về. Không xem iterator.remove hay entry.setValue như lối sửa hợp lệ thay thế một lệnh add/put bị cấm.

Các ví dụ tiếp theo kiểm tra những đường đi cụ thể bằng các lời gọi thực tế, rồi in nguồn để đối chiếu.

### 2.4. Hợp đồng nguồn vẫn có ý nghĩa

Wrapper không tự sắp xếp dữ liệu, không lọc null và không tạo tính duy nhất khác cho nguồn.

Một wrapper trên LinkedHashSet phản ánh thứ tự duyệt của LinkedHashSet. Bọc một HashSet không tự tạo cam kết thứ tự thêm. Tương tự, unmodifiableMap không biến HashMap thành map sắp theo khóa.

Đối số collection/map null bị từ chối bằng NullPointerException. Điều đó khác với phần tử, khóa hoặc giá trị null đã được một nguồn hợp lệ cho phép.

## 3. unmodifiableList: vẫn đọc theo chỉ số, không sửa qua List

unmodifiableList giữ giao diện List: có thể dùng get, size, contains, duyệt và lấy một khoảng con. Quyền đọc theo chỉ số không đồng nghĩa được set vào chỉ số đó.

Các thao tác sửa cần bị xét gồm add, remove, set, clear, thay hàng loạt phần tử và các đường sửa từ iterator/ListIterator. subList của wrapper cũng không mở quyền xóa một khoảng khỏi nguồn.

### 3.1. Chương trình 1: nguồn đổi, wrapper đọc thấy

Lưu vào `UnmodifiableListDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class UnmodifiableListDemo {
    static void expectUnsupported(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("A", "B"));
        List<String> view = Collections.unmodifiableList(source);
        source.add("C");
        source.set(0, "A2");
        System.out.println("viewAfterSourceEdits=" + view);
        System.out.println("first=" + view.get(0));
        expectUnsupported("add", () -> view.add("D"));
        expectUnsupported("set", () -> view.set(0, "changed"));
        Iterator<String> cursor = view.iterator();
        System.out.println("iteratorNext=" + cursor.next());
        expectUnsupported("iteratorRemove", () -> cursor.remove());
        expectUnsupported("subListClear", () -> view.subList(1, 3).clear());
        expectUnsupported("removeIf", () -> view.removeIf(value -> value.equals("B")));
        System.out.println("sourceAfterRejectedEdits=" + source);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 UnmodifiableListDemo.java
java UnmodifiableListDemo
```

Stdout:

```text
viewAfterSourceEdits=[A2, B, C]
first=A2
add=UnsupportedOperationException
set=UnsupportedOperationException
iteratorNext=A2
iteratorRemove=UnsupportedOperationException
subListClear=UnsupportedOperationException
removeIf=UnsupportedOperationException
sourceAfterRejectedEdits=[A2, B, C]
```

### 3.2. Hai đường truy cập có quyền khác nhau

source.add(C) và source.set(0,A2) được gọi qua ArrayList nguồn, nên thành công. viewAfterSourceEdits là `[A2, B, C]`; get(0) trả A2.

view.add và view.set ném UnsupportedOperationException vì được gọi qua wrapper. Cùng dữ liệu nguồn nhưng đường thao tác khác có quyền sửa khác.

### 3.3. Iterator và khoảng con không vượt qua giới hạn

cursor.next vẫn đọc A2, nhưng cursor.remove bị từ chối. Iterator được lấy từ wrapper không trao lại quyền xóa của ArrayList nguồn.

view.subList(1,3).clear cũng bị từ chối. Việc tạo một view con từ wrapper không làm khoảng con đó sửa được.

removeIf là một thao tác sửa theo điều kiện và cũng bị từ chối trong ví dụ. Nguồn sau những lời gọi bị chặn vẫn là A2,B,C.

Helper expectUnsupported chỉ phục vụ kiểm chứng: nếu lời gọi không ném ngoại lệ, nó báo AssertionError. Mã nghiệp vụ nên chọn đúng quyền thao tác từ đầu, không dùng ngoại lệ để dò rồi bỏ qua yêu cầu sửa.

## 4. unmodifiableSet: giữ hợp đồng tập hợp và chặn sửa

Set không có chỉ số như List. Wrapper vẫn cung cấp size, contains, phép duyệt và tính chất tập hợp theo nguồn. Nó không cho add, remove hoặc clear qua wrapper.

Nếu nguồn dùng đối tượng mutable làm phần tử, wrapper không tự đóng băng trạng thái của đối tượng đó. Đặc biệt, không được dựa vào wrapper để bảo vệ hợp đồng equals/hashCode của một phần tử đang ở trong tập băm.

Hợp đồng Set được mô tả tại [Set — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html).

### 4.1. Chương trình 2: nguồn và chính sách null

Lưu vào `UnmodifiableSetDemo.java`:

```java
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class UnmodifiableSetDemo {
    static void expectUnsupported(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        Set<String> source = new LinkedHashSet<>(List.of("red", "blue"));
        Set<String> view = Collections.unmodifiableSet(source);
        System.out.println("containsRed=" + view.contains("red"));
        source.add("green");
        System.out.println("viewAfterSourceAdd=" + view);
        expectUnsupported("setAdd", () -> view.add("yellow"));
        expectUnsupported("setRemove", () -> view.remove("red"));
        expectUnsupported("setClear", () -> view.clear());
        Iterator<String> cursor = view.iterator();
        System.out.println("setIteratorNext=" + cursor.next());
        expectUnsupported("setIteratorRemove", () -> cursor.remove());
        source.add(null);
        System.out.println("viewAfterSourceNull=" + view);
        System.out.println("viewContainsNull=" + view.contains(null));
        System.out.println("sourceSize=" + source.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 UnmodifiableSetDemo.java
java UnmodifiableSetDemo
```

Stdout:

```text
containsRed=true
viewAfterSourceAdd=[red, blue, green]
setAdd=UnsupportedOperationException
setRemove=UnsupportedOperationException
setClear=UnsupportedOperationException
setIteratorNext=red
setIteratorRemove=UnsupportedOperationException
viewAfterSourceNull=[red, blue, green, null]
viewContainsNull=true
sourceSize=4
```

### 4.2. Thứ tự trong output đến từ nguồn LinkedHashSet

source thêm red,blue, sau đó green. viewAfterSourceAdd là `[red, blue, green]`. Wrapper không sắp xếp theo chữ cái; nó đọc thứ tự của nguồn.

setAdd, setRemove, setClear và setIteratorRemove đều bị từ chối. contains và iterator.next vẫn đọc được dữ liệu.

### 4.3. Wrapper không biến một nguồn có null thành nguồn cấm null

LinkedHashSet nguồn cho null, nên source.add(null) thành công. Wrapper sau đó nhìn thấy null và contains(null) trả true; sourceSize là 4.

Không áp quy tắc “mọi collection không cho sửa đều cấm null” cho ba wrapper này. Các factory như Set.of hoặc Set.copyOf có hợp đồng null riêng; chúng không đồng nghĩa với việc bọc một Set đã có bằng unmodifiableSet.

Cũng không suy ra mọi nguồn đều chấp nhận truy vấn null. Quyền và hạn chế của nguồn thực tế vẫn cần được đọc theo API của nó.


## 5. unmodifiableMap phải bảo vệ cả ba collection view

Map có thể bị sửa không chỉ qua put/remove. keySet.remove có thể xóa ánh xạ theo khóa; values.remove có thể xóa một ánh xạ mang giá trị tương ứng; entrySet và Entry.setValue cũng có các thao tác sửa.

unmodifiableMap chặn sửa qua chính Map và qua những collection view mà nó cung cấp. Điều này giúp người nhận không lấy một view con rồi sửa map nguồn qua đường đó.

| Đường truy cập | Ví dụ thao tác đọc | Ví dụ thao tác sửa bị chặn |
|---|---|---|
| Map | get, containsKey, size | put, remove, clear, compute |
| keySet() | contains, iterator.next | remove, clear, iterator.remove |
| values() | contains, duyệt giá trị | remove, clear |
| entrySet() | duyệt cặp khóa–giá trị | remove, clear |
| Entry từ entrySet của wrapper | getKey, getValue | setValue |

Không đánh đồng việc chặn setValue với việc mọi đối tượng giá trị trở nên bất biến. getValue có thể trả một List mutable; thay nội dung List đó khác với yêu cầu thay ánh xạ trong Map.

Hợp đồng Map cùng các view được mô tả tại [Map — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html). Hợp đồng wrapper cụ thể nằm ở [Collections.unmodifiableMap — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html).

### 5.1. Phương thức có callback vẫn là thao tác sửa

compute, merge, replaceAll và các phương thức cập nhật tương tự không trở thành quyền đọc chỉ vì người gọi truyền một hàm.

Nếu ý định là tính một kết quả khác từ dữ liệu, hãy tạo kết quả riêng. Nếu ý định là cập nhật map, phải đi qua API có quyền sửa phù hợp. Không cố dùng một phương thức cập nhật khác để lách một put đã bị từ chối.

### 5.2. Không dựa vào một phép sửa “chắc không làm gì”

Các giao diện collection/map có những quy định riêng về thao tác tùy chọn và lời gọi không gây tác dụng thực tế. Các thuật toán như sort còn có thể có cách thực hiện khác nhau.

Vì vậy, không dùng remove một khóa không tồn tại, putAll rỗng hay sort một danh sách đã có thứ tự như phép kiểm tra đọc vô hại. Viết mã theo ý định đọc hoặc sửa; không dùng kết quả của một trường hợp không đổi dữ liệu để kết luận quyền sửa nói chung.

Ví dụ tiếp theo dùng các lời gọi sửa rõ ràng trên dữ liệu có sẵn để kiểm tra hợp đồng wrapper.

## 6. Chương trình 3: chặn sửa qua Map, view và Entry

Lưu vào `UnmodifiableMapDemo.java`. LinkedHashMap được dùng để thứ tự các ánh xạ khi in được xác định.

```java
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class UnmodifiableMapDemo {
    static void expectUnsupported(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        Map<String, Integer> source = new LinkedHashMap<>();
        source.put("a", 1);
        source.put("b", 2);
        Map<String, Integer> view = Collections.unmodifiableMap(source);
        source.put("b", 20);
        source.put("c", 3);
        System.out.println("viewAfterSourcePut=" + view);
        System.out.println("getB=" + view.get("b"));
        expectUnsupported("mapPut", () -> view.put("d", 4));
        expectUnsupported("mapRemove", () -> view.remove("a"));
        expectUnsupported("mapCompute", () -> view.compute("a", (key, value) -> value + 1));
        expectUnsupported("keySetRemove", () -> view.keySet().remove("a"));
        expectUnsupported("valuesRemove", () -> view.values().remove(20));
        expectUnsupported("entrySetClear", () -> view.entrySet().clear());
        Map.Entry<String, Integer> entry = view.entrySet().iterator().next();
        System.out.println("entryBeforeSet=" + entry);
        expectUnsupported("entrySetValue", () -> entry.setValue(99));
        System.out.println("sourceAfterRejectedMapEdits=" + source);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 UnmodifiableMapDemo.java
java UnmodifiableMapDemo
```

Stdout:

```text
viewAfterSourcePut={a=1, b=20, c=3}
getB=20
mapPut=UnsupportedOperationException
mapRemove=UnsupportedOperationException
mapCompute=UnsupportedOperationException
keySetRemove=UnsupportedOperationException
valuesRemove=UnsupportedOperationException
entrySetClear=UnsupportedOperationException
entryBeforeSet=a=1
entrySetValue=UnsupportedOperationException
sourceAfterRejectedMapEdits={a=1, b=20, c=3}
```

### 6.1. Cập nhật nguồn được đọc qua wrapper

Sau source.put(b,20) và source.put(c,3), wrapper đọc được `{a=1, b=20, c=3}`. get(b) trả 20.

Việc thay giá trị b ở nguồn không bị wrapper ngăn cản. Wrapper chỉ kiểm soát các thao tác gọi qua chính nó và những view bảo vệ mà nó trả về.

### 6.2. Các cách cập nhật khác nhau đều bị từ chối

mapPut, mapRemove và mapCompute đều ném UnsupportedOperationException. Không có việc dùng compute để mở lại quyền sửa một map bị bọc.

keySetRemove, valuesRemove và entrySetClear cũng bị từ chối. Đó là những đường có thể sửa một Map thông thường, nên không thể chỉ bảo vệ các phương thức trực tiếp trên Map rồi bỏ qua chúng.

### 6.3. Entry đọc được nhưng không setValue được

Entry đầu trong ví dụ là a=1. entry.setValue(99) bị chặn; sourceAfterRejectedMapEdits vẫn là `{a=1, b=20, c=3}`.

Entry này được lấy từ entrySet của wrapper và dùng ngay trong phép minh họa. Không tự coi mọi Entry là một snapshot có vòng đời vô hạn: hợp đồng của Entry còn phụ thuộc đường lấy nó. Điểm cần rút ra ở đây là wrapper không trao quyền sửa ánh xạ bằng setValue.

## 7. Chỉ đọc ở lớp ngoài chưa phải chỉ đọc mọi trạng thái

### 7.1. Một phần tử mutable vẫn có thể được sửa

List<StringBuilder> được bọc vẫn trả StringBuilder khi gọi get. Append trên StringBuilder không gọi add/remove/set của List.

Wrapper không có khả năng tự thay đổi toàn bộ API của mọi kiểu phần tử E. Muốn kiểm soát việc sửa trạng thái đó, cần mô hình phần tử bất biến, bản sao phần tử hoặc một cách cung cấp dữ liệu phù hợp khác.

Tương tự, Map không cho put vẫn có thể mang giá trị là List sửa được. Gọi map.get(key).add(x) sửa List giá trị, không thay ánh xạ của Map.

### 7.2. Sao chép trước khi bọc chỉ tách lớp được sao chép

Collections.unmodifiableList(new ArrayList<>(source)) tạo một cấu trúc List chứa riêng rồi bọc nó. Thêm/xóa trên source về sau không làm cấu trúc kết quả tự thay đổi.

Nhưng ArrayList mới vẫn chứa các tham chiếu phần tử đã có. Nếu phần tử là StringBuilder, cả nguồn lẫn kết quả có thể cùng nhìn thấy một lần append.

Đừng gọi cấu trúc này là deep copy tổng quát. Cần xét riêng các lớp dữ liệu có trạng thái có thể thay đổi.

### 7.3. Collection lồng nhau cần quyết định từng lớp

Với Map<String,List<String>>, muốn tách khỏi nguồn và chặn sửa qua cả Map lẫn List con, có thể:

1. Tạo Map chứa mới.
2. Tạo List mới cho mỗi giá trị.
3. Bọc từng List con.
4. Bọc Map ngoài.
5. Quản lý quyền sửa các cấu trúc chứa mới, không đưa tham chiếu mutable của chúng ra ngoài.

String trong ví dụ là bất biến nên không cần tạo thêm một String chỉ để chặn sửa nội dung nó. Nếu đổi thành List<MutablePermission>, sao chép và bọc List vẫn chưa tự bảo vệ MutablePermission.

Đây là một chính sách theo mô hình cụ thể. Không có quy tắc tự động rằng cứ thêm một wrapper ngoài cùng là mọi đường dữ liệu bên trong đều đã chỉ đọc.

## 8. Chương trình 4: tách cấu trúc chứa và bọc các lớp cần bảo vệ

Lưu vào `UnmodifiableLayersDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UnmodifiableLayersDemo {
    static void expectUnsupported(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        List<StringBuilder> source = new ArrayList<>(List.of(new StringBuilder("draft")));
        List<StringBuilder> detachedOuter =
                Collections.unmodifiableList(new ArrayList<>(source));
        source.add(new StringBuilder("extra"));
        detachedOuter.get(0).append("!");
        System.out.println("sourceSize=" + source.size());
        System.out.println("detachedSize=" + detachedOuter.size());
        System.out.println("sharedMutableElement=" + source.get(0));
        System.out.println("sameElement=" + (source.get(0) == detachedOuter.get(0)));

        Map<String, List<String>> roles = new LinkedHashMap<>();
        roles.put("admin", new ArrayList<>(List.of("read")));
        Map<String, List<String>> outerView = Collections.unmodifiableMap(roles);
        outerView.get("admin").add("write");
        System.out.println("sourceRolesAfterInnerEdit=" + roles.get("admin"));

        Map<String, List<String>> copied = new LinkedHashMap<>();
        roles.forEach((key, values) -> copied.put(key,
                Collections.unmodifiableList(new ArrayList<>(values))));
        Map<String, List<String>> readOnlyCopy = Collections.unmodifiableMap(copied);
        roles.get("admin").add("owner");
        System.out.println("sourceRolesAfterLaterEdit=" + roles.get("admin"));
        System.out.println("copiedRoles=" + readOnlyCopy.get("admin"));
        expectUnsupported("nestedListAdd", () -> readOnlyCopy.get("admin").add("blocked"));
        expectUnsupported("outerMapPut", () -> readOnlyCopy.put("guest", List.of("read")));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 UnmodifiableLayersDemo.java
java UnmodifiableLayersDemo
```

Stdout:

```text
sourceSize=2
detachedSize=1
sharedMutableElement=draft!
sameElement=true
sourceRolesAfterInnerEdit=[read, write]
sourceRolesAfterLaterEdit=[read, write, owner]
copiedRoles=[read, write]
nestedListAdd=UnsupportedOperationException
outerMapPut=UnsupportedOperationException
```

### 8.1. List ngoài đã tách, StringBuilder vẫn chung

detachedOuter được tạo từ ArrayList mới. Sau source.add(extra), sourceSize là 2 còn detachedSize vẫn 1.

Tuy nhiên, append qua detachedOuter.get(0) làm source.get(0) in draft!. sameElement là true. Cấu trúc chứa riêng không tự nhân bản đối tượng phần tử.

### 8.2. Map ngoài không cho put nhưng List giá trị còn sửa được

outerView bọc roles. outerView.get(admin).add(write) thành công vì giá trị được trả về là ArrayList chưa được bọc.

sourceRolesAfterInnerEdit là read,write. Không có put nào gọi trên outerView trong thao tác này; hợp đồng chặn sửa Map không bị vượt qua, mà một đối tượng khác được sửa.

### 8.3. Bản sao hai lớp không theo thay đổi của nguồn cũ

copied tạo các List giá trị mới từ nội dung hiện tại, rồi từng List được bọc. readOnlyCopy bọc Map chứa những List đó.

Sau roles.get(admin).add(owner), nguồn cũ có read,write,owner nhưng copiedRoles vẫn chỉ read,write. List giá trị của kết quả không còn là List giá trị nguồn.

nestedListAdd bị chặn bởi wrapper List con; outerMapPut bị chặn bởi wrapper Map ngoài. Cần bảo vệ đúng lớp mà người nhận có thể tiếp cận.

### 8.4. Quyền sở hữu cấu trúc mới vẫn quan trọng

copied vẫn là một LinkedHashMap sửa được trong phạm vi chương trình. Nếu mã chủ sở hữu tiếp tục sửa copied, readOnlyCopy sẽ đọc thấy thay đổi đó.

Ví dụ không giữ một tham chiếu sửa được đến các ArrayList con được tạo trong biểu thức trước khi bọc. Trong API thực tế, hãy kiểm soát những tham chiếu này thay vì chỉ dựa vào tên biến readOnly.

Bốn ví dụ không dùng nhiều luồng; việc tách và bọc dữ liệu không tự cung cấp hợp đồng truy cập đồng thời.


## 9. Chọn view đang theo nguồn hay kết quả tách khỏi nguồn

### 9.1. Khi cần quan sát dữ liệu hiện tại

Dùng unmodifiableList(source), unmodifiableSet(source) hoặc unmodifiableMap(source) khi người nhận được phép thấy những cập nhật của nguồn nhưng không được tự sửa qua kết quả được cung cấp.

Chủ sở hữu vẫn cần giữ quyền cập nhật trong những phương thức phù hợp. Ví dụ, thêm một tên qua phương thức register có thể kiểm tra trùng hoặc điều kiện nghiệp vụ trước khi sửa nguồn.

Nếu đưa cả source sửa được ra ngoài ở một chỗ khác, wrapper ở getter không thu hồi quyền sửa mà tham chiếu source kia đã có. Cần xem toàn bộ đường cung cấp dữ liệu.

### 9.2. Khi cần một kết quả chứa độc lập

Có thể sao chép cấu trúc chứa trước rồi bọc. Các đoạn dưới đây là minh họa đặt trong phương thức đã có listSource kiểu List<String>, setSource kiểu Set<String>, mapSource kiểu Map<String,Integer> và các import java.util tương ứng; chúng không phải tệp Java độc lập.

```java
List<String> listResult =
        Collections.unmodifiableList(new ArrayList<>(listSource));

Set<String> setResult =
        Collections.unmodifiableSet(new LinkedHashSet<>(setSource));

Map<String, Integer> mapResult =
        Collections.unmodifiableMap(new LinkedHashMap<>(mapSource));
```

ArrayList mới tách các vị trí List khỏi nguồn. LinkedHashSet và LinkedHashMap mới thu dữ liệu theo thứ tự duyệt của nguồn tại lúc sao chép. Chúng không tự suy ra một thứ tự thêm đã bị nguồn bỏ qua, và cũng không tự sắp theo khóa hay giá trị.

Những thay đổi tiếp theo của collection nguồn cũ không làm cấu trúc chứa mới tự cập nhật. Tuy nhiên, các phần tử, khóa và giá trị được sao chép theo tham chiếu; nếu chúng mutable thì còn phải xét lớp dữ liệu đó.

### 9.3. So với các factory copyOf

List.copyOf, Set.copyOf và Map.copyOf cũng cung cấp kết quả không cho sửa với hợp đồng nội dung không theo các thay đổi collection nguồn sau đó. Chúng không phải các view sống giống ba wrapper bọc trực tiếp nguồn trong bài.

Nhưng không thay thế máy móc:

| Lựa chọn | Quan hệ với cấu trúc nguồn về sau | Null và thứ tự cần xét |
|---|---|---|
| Bọc trực tiếp bằng unmodifiable | Đọc theo nguồn đang được bọc | Phụ thuộc nguồn; không tự sắp xếp hoặc lọc null |
| Sao chép vào cấu trúc đã chọn rồi bọc | Cấu trúc mới tách khỏi nguồn cũ | Theo kiểu chứa được chọn; vẫn là sao chép nông phần tử |
| List.copyOf | Nội dung List kết quả không theo sửa cấu trúc nguồn về sau | Cấm phần tử null, giữ thứ tự của collection đầu vào |
| Set.copyOf | Tập kết quả không theo sửa cấu trúc nguồn về sau | Cấm null, không hứa thứ tự như LinkedHashSet |
| Map.copyOf | Các ánh xạ kết quả không theo sửa cấu trúc nguồn về sau | Cấm khóa/giá trị null, không hứa thứ tự như LinkedHashMap |

Các factory này vẫn có thể chứa đối tượng mutable được dùng chung. Chúng cũng có thể tận dụng một kết quả không cho sửa phù hợp, nên không hứa luôn cấp một đối tượng mới theo danh tính.

Nguồn đối chiếu: [List — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html), [Set — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html), [Map — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

### 9.4. Giữ đúng giao diện mà API cần

Ba phương thức trong bài trả List, Set và Map. Nếu API còn cần hợp đồng SortedSet, NavigableSet, SortedMap hoặc NavigableMap, hãy chọn wrapper chuyên biệt tương ứng thay vì bọc thành Set/Map rồi ép kiểu để gọi các phương thức chuyên biệt.

Không ép một wrapper về ArrayList, LinkedHashSet hay LinkedHashMap để sửa. Wrapper không có hợp đồng là chính lớp cụ thể đó. Nếu người nhận cần một bản sửa được, tạo một bản chứa riêng theo yêu cầu thay vì cố mở quyền sửa của view.

### 9.5. Wrapper theo đối tượng nguồn, không theo tên biến

Từ quan hệ bọc đối tượng có thể suy ra: gán biến source sang một collection khác không làm wrapper cũ tự chuyển sang đối tượng mới.

Thay nội dung collection đang được bọc và thay một biến để trỏ đến collection khác là hai hành động khác nhau. Khi một lớp thay toàn bộ trường dữ liệu bằng collection mới, cần thiết kế rõ những view đã trả trước đó sẽ còn nhìn đối tượng cũ hay người nhận phải lấy kết quả mới.

## 10. Vòng đời, quyền sở hữu và truy cập đồng thời

### 10.1. Chỉ đọc không tự bảo đảm an toàn cho nhiều luồng

Wrapper không tự thêm đồng bộ cho ArrayList, LinkedHashSet hoặc LinkedHashMap nguồn. Nếu một nơi đọc wrapper còn nơi khác sửa nguồn đồng thời, vẫn phải tuân theo hợp đồng truy cập của nguồn và thiết kế phối hợp phù hợp.

Không coi UnsupportedOperationException là công cụ điều phối luồng. Nó từ chối một đường sửa không được hỗ trợ; nó không thay thế khóa, không tạo ảnh chụp nguyên tử và không làm nhiều lần đọc thành một giao dịch.

Nếu cần dữ liệu nhất quán tại một thời điểm, quá trình tạo bản sao cũng phải đọc nguồn trong điều kiện thích hợp. Bọc một nguồn đang thay đổi không giải quyết điều kiện đó.

### 10.2. Iterator không phải snapshot được tạo tự động

Iterator lấy từ wrapper đọc theo cơ chế của nguồn. Nếu nguồn có iterator fail-fast, sửa cấu trúc nguồn trong lúc đang duyệt có thể làm phép duyệt bị phát hiện lỗi.

Chặn iterator.remove chỉ giới hạn quyền xóa qua iterator đó; nó không đóng băng nguồn trong suốt vòng lặp. Không giữ iterator rồi sửa nguồn qua tham chiếu khác và suy ra đọc sẽ an toàn vì iterator đến từ wrapper.

Các chương trình đầy đủ trong bài sửa nguồn trước khi tạo iterator cần dùng, và không dựa vào cạnh tranh giữa các luồng.

### 10.3. Không cho sửa collection không bảo vệ khóa mutable

Nếu một đối tượng mutable được dùng làm phần tử Set hoặc khóa Map, thay trạng thái ảnh hưởng equals/hashCode có thể làm vi phạm hợp đồng cấu trúc chứa. Wrapper không tự khóa các trường hay phương thức sửa của đối tượng đó.

Một thiết kế dữ liệu chỉ đọc đủ cho nhu cầu thực tế thường cần cả quyền sở hữu nguồn rõ ràng và kiểu phần tử/khóa phù hợp. Không đo mức bảo vệ chỉ bằng việc một lệnh add bị từ chối.

### 10.4. Chi phí và thời gian sống cần được cân nhắc

Bọc trực tiếp không có ý nghĩa sao chép toàn bộ n phần tử sang cấu trúc chứa khác. Điều này phù hợp khi muốn một view tiếp tục quan sát dữ liệu đang được chủ sở hữu quản lý.

Sao chép trước khi bọc cần thu các tham chiếu hoặc ánh xạ vào cấu trúc chứa mới. Đổi lại, cấu trúc kết quả không còn bám vào các vị trí hay ánh xạ của collection nguồn cũ.

Không chọn chỉ theo tên “nhẹ” hay “an toàn”. Hãy xác định người nhận có được nhìn thấy cập nhật sau đó không, có giữ kết quả lâu không, và các đối tượng phần tử còn chia sẻ trạng thái nào.

## 11. Những lỗi thường gặp

| Cách hiểu hoặc cách làm | Vấn đề | Cách xử lý |
|---|---|---|
| Bọc xong thì source không sửa được nữa | Tham chiếu nguồn vẫn có quyền của nguồn | Quản lý các đường cung cấp source |
| Wrapper là snapshot | View đọc theo nguồn | Sao chép trước nếu cần tách cấu trúc chứa |
| Không add được nhưng set chắc được | set cũng là sửa List | Đọc hợp đồng quyền sửa, không chỉ kích thước |
| Dùng iterator.remove để thay view.remove | Iterator của wrapper không mở quyền xóa | Dùng API cập nhật có quyền hoặc sửa một bản sao |
| Xóa qua subList của wrapper | View con vẫn không cho sửa | Không coi view con là đường vượt quyền |
| Sửa map qua keySet/values/entrySet | Những đường đó cũng được bảo vệ | Cập nhật qua chủ sở hữu hoặc bản map khác |
| Dùng Entry.setValue vì Entry đọc được | Thay giá trị ánh xạ vẫn là thao tác sửa | Không dùng Entry của wrapper để cập nhật map |
| Map không cho put thì List giá trị cũng không add được | Hai đối tượng có quyền riêng | Bọc hoặc sao chép các lớp cần bảo vệ |
| List không cho sửa làm StringBuilder bên trong bất biến | Wrapper không đổi API của phần tử | Chọn phần tử bất biến hoặc bản sao phù hợp |
| Mọi collection chỉ đọc đều cấm null | Wrapper và factory có hợp đồng khác nhau | Xét nguồn và cách tạo kết quả |
| Wrapper thêm thứ tự ổn định cho HashSet/HashMap | Nó không đổi chính sách thứ tự của nguồn | Chọn cấu trúc có thứ tự khi đó là yêu cầu |
| final collection thay thế unmodifiable | final chỉ hạn chế gán lại biến | Tách quyền gán biến với quyền sửa đối tượng |
| Unmodifiable đồng nghĩa thread-safe | Không có bảo đảm đồng bộ tự động | Thiết kế truy cập theo hợp đồng nguồn |
| Ép wrapper về lớp nguồn để sửa | Lớp thực tế không được hứa là lớp nguồn | Tạo bản sửa được riêng khi cần |
| Gán source sang collection khác thì view cũ theo sang | Wrapper gắn với đối tượng đã được bọc | Quản lý vòng đời của view và trường nguồn |
| Chỉ kiểm tra size sau thao tác để đo bất biến | Phần tử hoặc giá trị có thể đổi mà size giữ nguyên | Kiểm tra cả trạng thái và quan hệ tham chiếu |

Không dùng catch chung Exception rồi bỏ qua để che một thao tác sửa bị từ chối. UnsupportedOperationException thường cho thấy mã gọi đang dùng một kết quả vượt quyền sửa mà hợp đồng cung cấp.

## 12. Tự kiểm tra và thực hành

### 12.1. Câu hỏi với đáp án

1. **unmodifiableList(source) có sao chép phần tử vào List mới độc lập không?** Không theo hợp đồng wrapper; nó cung cấp view đọc dữ liệu nguồn.
2. **source.add sau khi bọc có bị wrapper ngăn không?** Không. Lời gọi qua nguồn vẫn theo quyền của nguồn.
3. **Vì sao view.add biên dịch được nhưng lỗi lúc chạy?** Kiểu List có add; đối tượng wrapper không hỗ trợ thao tác sửa đó.
4. **Iterator từ wrapper có remove được nếu nguồn ArrayList sửa được không?** Không. Quyền xóa qua iterator của wrapper cũng bị chặn.
5. **Map.Entry từ entrySet của unmodifiableMap có setValue để sửa nguồn không?** Không qua đường được wrapper cung cấp.
6. **get(key).add trên một Map<String,List<String>> không cho put có thể thành công không?** Có, nếu List giá trị vẫn sửa được. Nó sửa đối tượng List, không gọi put trên Map.
7. **Bọc một LinkedHashSet có null có tự loại null không?** Không. Wrapper không tự lọc dữ liệu nguồn.
8. **Bọc một HashMap có tạo thứ tự khóa tăng dần không?** Không. Thứ tự vẫn theo hợp đồng nguồn.
9. **Sao chép List rồi bọc có làm StringBuilder phần tử được nhân bản không?** Không. Cấu trúc chứa riêng vẫn có thể giữ cùng tham chiếu phần tử.
10. **Unmodifiable có tự làm một phép duyệt an toàn khi luồng khác sửa nguồn không?** Không. Phải xét cơ chế truy cập của nguồn và cách phối hợp riêng.

### 12.2. Bài tập: so sánh view và bản sao chứa

Tạo ArrayList<String> chứa one,two. Tạo view bọc trực tiếp và một kết quả bọc ArrayList sao chép từ nguồn.

Thêm three qua nguồn. Kỳ vọng view nhìn one,two,three; kết quả chứa riêng vẫn one,two. Thử add qua cả hai kết quả và xác minh thao tác bị từ chối.

Thay kiểu phần tử thành StringBuilder rồi append qua kết quả chứa riêng. Giải thích vì sao kết quả có thể vẫn ảnh hưởng phần tử nguồn dù số vị trí đã độc lập.

### 12.3. Bài tập: bảo vệ đủ các đường Map

Tạo LinkedHashMap<String,Integer> có hai ánh xạ và bọc unmodifiableMap. Kiểm tra riêng put, remove, keySet.remove, values.remove và Entry.setValue từ entrySet.

Sau mỗi phép thử bị từ chối, đọc map nguồn để xác nhận ánh xạ không đổi. Các phép đọc get, containsKey và duyệt entry vẫn phải hoạt động theo dữ liệu nguồn.

Không thử những thao tác sai chỉ số hay một Entry ngoài vòng đời hợp lệ rồi quy mọi ngoại lệ cho wrapper.

### 12.4. Bài tập: thiết kế API trả Map của các List

Một lớp giữ Map<String,List<String>> chứa các nhóm quyền. Người nhận không được thêm/xóa nhóm, không được thêm/xóa quyền trong nhóm, và không được quan sát các sửa đổi về cấu trúc nguồn sau lúc nhận.

Thiết kế một kết quả có Map chứa riêng và các List giá trị riêng, bọc đúng hai lớp. Giữ rõ quyền sở hữu các cấu trúc mutable mới, rồi sửa nguồn cũ để kiểm tra kết quả không theo thay đổi đó.

Nếu thay String bằng một đối tượng Permission mutable, hãy nêu thêm trạng thái nào vẫn chưa được bảo vệ bởi chỉ sao chép và bọc các collection.

## 13. Nguồn đối chiếu và phạm vi kiểm chứng

- [Collections — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html): hợp đồng ba wrapper unmodifiable, thao tác đọc dựa trên nguồn và thao tác sửa bị từ chối.
- [List — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html): giao diện List, thao tác sửa, List.copyOf và giới hạn với phần tử mutable.
- [Set — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html): hợp đồng tập hợp, phần tử mutable, null và Set.copyOf.
- [Map — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): collection view, thao tác cập nhật, khóa mutable và Map.copyOf.

Bốn chương trình đầy đủ đã biên dịch với `javac --release 17 -encoding UTF-8` và chạy trên Corretto JDK 17.0.15, cho đúng stdout được ghi. Ví dụ dùng LinkedHashSet/LinkedHashMap để cố định thứ tự duyệt cần in. Đoạn tạo ba kết quả ở phần lựa chọn view/bản sao là mã minh họa cần đặt trong ngữ cảnh có các biến nguồn và import phù hợp; nó không được trình bày như một tệp Java độc lập.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unmodifiable-collections-003">Vì sao unmodifiable view vẫn thấy thay đổi từ nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao unmodifiable view vẫn thấy thay đổi từ nguồn

## 1. Một lối đọc bị chặn ghi, không phải dữ liệu được đóng băng

Giả sử một lớp giữ danh sách người đang tham gia phòng học. Lớp đó muốn cho nơi khác xem danh sách, nhưng mọi thao tác tham gia/rời phòng phải đi qua các phương thức nghiệp vụ của nó.

Collections.unmodifiableList(source) đáp ứng nhu cầu này: nơi nhận không được sửa danh sách qua kết quả trả về, trong khi chủ sở hữu source vẫn tiếp tục thêm hoặc xóa người. Khi đọc lại view sau các cập nhật tuần tự hợp lệ ở nguồn, nơi nhận thấy trạng thái mới.

Hai điều đó không mâu thuẫn. **unmodifiable mô tả thao tác không được phép qua một giao diện; view mô tả dữ liệu được đọc từ nguồn nào.**

Hãy phân biệt ba yêu cầu:

| Yêu cầu | Câu hỏi tương ứng |
|---|---|
| Chặn ghi qua một tham chiếu | Nơi giữ tham chiếu này có gọi add, remove, set, put… được không? |
| Tách khỏi thay đổi cấu trúc ở nguồn | Nguồn thêm/xóa/thay vị trí thì dữ liệu trả về có đổi theo không? |
| Giữ nguyên trạng thái của mọi phần tử | Nếu đối tượng bên trong thay thuộc tính, nơi đọc có thấy không? |

Một unmodifiable view chỉ trực tiếp giải quyết yêu cầu đầu tiên. Hai yêu cầu còn lại phụ thuộc có sao chép dữ liệu hay không và các đối tượng bên trong có thay đổi được hay không.

Bài này dùng các lớp bọc Collections.unmodifiableList, unmodifiableSet và unmodifiableMap làm mô hình chính. Các ví dụ đầy đủ chạy trên Java 17 trở lên. Mọi cập nhật ở ví dụ được thực hiện tuần tự trong một luồng; phần làm việc giữa nhiều luồng được phân tích riêng.

## 2. Cơ chế: lớp bọc đọc qua collection được bọc

### 2.1. Quan hệ với nguồn vẫn còn

Một view không cần quản lý một bộ phần tử độc lập. Nó giữ quan hệ với cấu trúc được bọc và chuyển các thao tác đọc thích hợp tới cấu trúc đó.

Về mặt ý tưởng, khi gọi view.size(), câu trả lời đến từ kích thước hiện tại của nguồn. Khi gọi view.get(i), giá trị đến từ phần tử hiện tại ở vị trí i của nguồn. Các thao tác sửa qua view bị chặn.

Đây là mô tả cơ chế, không phải mã nguồn triển khai phải giống hệt như vậy ở mọi JDK. Điều cần dựa vào là hợp đồng API: các truy vấn trên unmodifiableList và unmodifiableMap đọc xuyên qua cấu trúc được cung cấp. [Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)) và [unmodifiableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableMap(java.util.Map)) nêu trực tiếp hành vi này.

Vì nguồn còn tồn tại và còn được sửa qua tham chiếu khác, lần đọc view sau đó thấy nguồn đã đổi. Không có tác vụ đồng bộ nội dung, gửi sự kiện hay sao chép lại âm thầm để đạt kết quả ấy; lớp bọc vốn đang đọc cùng dữ liệu.

### 2.2. Có hai đường truy cập với quyền khác nhau

Có thể hình dung:

- Chủ sở hữu giữ source, được sửa theo API của source.
- Nơi nhận giữ view, được đọc nhưng các thao tác cập nhật qua view bị từ chối.
- Cả hai đường truy cập cuối cùng vẫn liên quan đến cùng một cấu trúc dữ liệu.

Bọc source không biến bản thân source thành một collection không sửa được. Nó cũng không thu hồi các tham chiếu mutable đã được trao ra trước đó.

Nếu một đoạn code khác đã có source hoặc một view có thể ghi của source, đoạn code đó vẫn có thể thay đổi dữ liệu. Khi đánh giá quyền sở hữu, phải theo các tham chiếu thực tế, không chỉ nhìn tên biến readOnly.

### 2.3. Không phải mọi lớp bọc đều cần là đối tượng mới

Không dùng view != source làm định nghĩa của unmodifiable view. Một số lời gọi có thể tái sử dụng đối tượng đã đáp ứng hợp đồng. Chi tiết số lượng lớp bọc hoặc tên lớp nội bộ không quyết định việc có liên kết với nguồn.

Thử nghiệm đúng là quan sát quyền sửa và kết quả đọc sau khi nguồn thay đổi. So sánh tham chiếu, xem tên lớp runtime hay ép kiểu tới một lớp nội bộ không thay thế hợp đồng API.

Tài liệu [Collection — View Collections và Unmodifiable Collections](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html) là điểm tham chiếu để phân biệt nơi chứa dữ liệu với lớp cung cấp góc nhìn.

## 3. Chương trình với List: nguồn đổi, view đổi; bản sao giữ cấu trúc cũ

Chương trình tạo ba kết quả từ cùng một nguồn:

- view bọc trực tiếp source.
- copiedThenWrapped bọc một ArrayList đã sao chép.
- copiedByFactory được tạo bằng List.copyOf(source).

Nguồn chứa String bất biến để phần đầu chỉ tập trung vào thay đổi ở cấu trúc list.

Tệp **LiveListViewDemo.java**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class LiveListViewDemo {
    private static void expectUOE(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + ": no exception");
        } catch (UnsupportedOperationException expected) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("A", "B"));
        List<String> view = Collections.unmodifiableList(source);
        List<String> copiedThenWrapped =
                Collections.unmodifiableList(new ArrayList<>(source));
        List<String> copiedByFactory = List.copyOf(source);

        source.set(0, "A2");
        source.add("C");
        System.out.println("view=" + view);
        System.out.println("copiedThenWrapped=" + copiedThenWrapped);
        System.out.println("copiedByFactory=" + copiedByFactory);
        expectUOE("viewAdd", () -> view.add("D"));
        expectUOE("viewSet", () -> view.set(0, "X"));
        Iterator<String> iterator = view.iterator();
        iterator.next();
        expectUOE("iteratorRemove", iterator::remove);

        List<String> current = source;
        List<String> fromCurrent = Collections.unmodifiableList(current);
        current = new ArrayList<>(List.of("X"));
        current.add("Y");
        System.out.println("reboundVariable=" + current);
        System.out.println("oldObjectView=" + fromCurrent);

        source.remove("B");
        System.out.println("afterSourceRemove=" + view);
        source.clear();
        System.out.println("afterSourceClear=" + view);
        source.add("NEW");
        System.out.println("afterSourceRefill=" + view);

        String[] array = {"red", "blue"};
        List<String> arrayView =
                Collections.unmodifiableList(Arrays.asList(array));
        array[0] = "green";
        System.out.println("arrayBacked=" + arrayView);

        CopyOnWriteArrayList<String> concurrentSource =
                new CopyOnWriteArrayList<>(List.of("old"));
        List<String> concurrentView =
                Collections.unmodifiableList(concurrentSource);
        Iterator<String> oldIterator = concurrentView.iterator();
        concurrentSource.add("new");
        List<String> oldIteration = new ArrayList<>();
        oldIterator.forEachRemaining(oldIteration::add);
        System.out.println("currentView=" + concurrentView);
        System.out.println("oldIterator=" + oldIteration);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 LiveListViewDemo.java
java LiveListViewDemo
```

Stdout chính xác:

```text
view=[A2, B, C]
copiedThenWrapped=[A, B]
copiedByFactory=[A, B]
viewAdd=UnsupportedOperationException
viewSet=UnsupportedOperationException
iteratorRemove=UnsupportedOperationException
reboundVariable=[X, Y]
oldObjectView=[A2, B, C]
afterSourceRemove=[A2, C]
afterSourceClear=[]
afterSourceRefill=[NEW]
arrayBacked=[green, blue]
currentView=[old, new]
oldIterator=[old]
```

### 3.1. set cũng là thay đổi mà view đọc được

source.set(0,"A2") thay phần tử ở một vị trí nhưng không tăng kích thước. source.add("C") thêm một vị trí mới. view thấy cả hai kết quả và hiện [A2,B,C].

Vì vậy không giới hạn ý nghĩa “thay đổi từ nguồn” vào riêng thao tác thêm/xóa. Thay phần tử, đổi thứ tự hợp lệ hoặc thay giá trị ánh xạ cũng có thể làm kết quả đọc của view khác đi.

copiedThenWrapped vẫn là [A,B] vì nó đang bọc ArrayList khác, được tạo trước khi source đổi. copiedByFactory cũng giữ các phần tử của lần sao chép. Điểm tách liên kết là bước **sao chép cấu trúc**, không phải tên unmodifiable.

### 3.2. Các đường sửa của chính view bị chặn

view.add("D"), view.set(0,"X") và iterator.remove() đều gây UnsupportedOperationException. Với List, việc không cho sửa bao gồm cả thay một phần tử; đây không chỉ là danh sách kích thước cố định.

Iterator được lấy qua unmodifiable view không cung cấp một lối vòng để xóa nguồn. Tương tự, ListIterator của lớp bọc không cho sửa bằng add/set/remove theo giao diện ấy.

Nhưng không vì các thao tác này thất bại mà suy ra source.add(...) cũng phải thất bại. Chính source là đường cập nhật mà chủ sở hữu còn giữ.

### 3.3. Gán biến khác không đổi đối tượng mà view đang bọc

current ban đầu trỏ source. fromCurrent được tạo từ đối tượng mà current đang trỏ tới ở thời điểm gọi unmodifiableList.

Sau đó current được gán sang ArrayList mới [X,Y]. Việc gán này chỉ đổi giá trị của biến current. Nó không điều khiển fromCurrent chuyển sang bọc đối tượng mới. fromCurrent vẫn đọc list cũ, nên hiện [A2,B,C].

Đây là một chi tiết hữu ích khi đọc code:

- Sửa đối tượng nguồn đã bọc → view đọc thấy thay đổi hợp lệ của đối tượng đó.
- Gán một biến sang đối tượng khác → view cũ không tự chuyển mục tiêu.

Nếu cần một API luôn lấy collection hiện tại từ một trường có thể thay thế, đó là một thiết kế truy vấn khác. Đừng kỳ vọng một lớp bọc đã trả về sẽ tự theo biến của ứng dụng.

### 3.4. clear không ngắt liên kết

Sau source.clear(), view trở thành rỗng. Sau source.add("NEW"), chính view đó lại hiện NEW. Nó chưa trở thành một collection rỗng độc lập và cũng chưa bị “đóng” sau lần clear.

Một view rỗng chỉ đang nhìn thấy nguồn rỗng tại thời điểm đọc. Tính rỗng không nói gì về việc nguồn còn có thể thay đổi về sau.

### 3.5. Nguồn có thể nằm sau một view khác

arrayView bọc Arrays.asList(array), mà list này dựa trên mảng array. Khi array[0] được thay thành green, lớp bọc ngoài đọc xuyên qua list trung gian và thấy green.

Chuỗi ở đây là: unmodifiable view → list dựa trên mảng → mảng. Thêm một lớp bọc không cắt liên kết ở các lớp bên trong.

[Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)) trả danh sách kích thước cố định dựa trên mảng. Nó cho phép thay phần tử qua set khi chưa bị lớp bọc chặn, khác với một list không sửa được.

Phần CopyOnWriteArrayList ở cuối chương trình sẽ được giải thích ở mục iterator: view hiện tại và một iterator đã tạo trước đó không bắt buộc thể hiện cùng phiên bản dữ liệu.

## 4. Quy tắc tương tự với Set và Map

### 4.1. UnmodifiableSet vẫn dựa trên set nguồn

Collections.unmodifiableSet(sourceSet) chặn các cập nhật qua set trả về. Nếu chủ sở hữu thêm/xóa phần tử qua sourceSet theo cách hợp lệ, kết quả đọc từ view phản ánh set nguồn hiện tại.

Lớp bọc không tự tạo thứ tự mới. Nếu nguồn có thứ tự duyệt được bảo đảm, view đọc theo hợp đồng đó; nếu nguồn không cam kết thứ tự, việc bọc không làm thứ tự trở nên ổn định.

Với SortedSet hoặc NavigableSet, chọn lớp bọc tương ứng nếu API cần giữ các khả năng đọc về thứ tự/điều hướng. Các view con được trả về vẫn phải giữ việc chặn ghi theo hợp đồng lớp bọc. Nhưng phần tử mutable của set vẫn là vấn đề riêng, nhất là khi thay thuộc tính ảnh hưởng equals/hashCode hoặc Comparator.

### 4.2. UnmodifiableMap chặn cả đường ghi qua các collection view

Map có nhiều đường thay đổi ánh xạ: put/remove trực tiếp, xóa qua keySet/values, hoặc cập nhật entry khi entry hỗ trợ setValue.

Collections.unmodifiableMap phải chặn các đường ghi từ chính kết quả được trả ra. Nó không thể chỉ chặn put rồi để keySet().remove(...) xóa nguồn như một lối vòng.

Trong khi đó, chủ sở hữu source map vẫn có thể cập nhật ánh xạ. Các tập khóa và collection giá trị lấy từ view cũng tiếp tục là góc nhìn vào dữ liệu đang được quản lý.

## 5. Chương trình Map: các view đọc theo nguồn và bảo vệ đường cập nhật

Ví dụ dùng LinkedHashMap để thứ tự in theo thứ tự chèn được xác định. Các khóa String và giá trị Integer đều bất biến, tránh trộn lẫn với việc sửa thuộc tính đối tượng bên trong.

Tệp **LiveMapViewDemo.java**:

```java
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class LiveMapViewDemo {
    private static void expectUOE(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + ": no exception");
        } catch (UnsupportedOperationException expected) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        Map<String, Integer> source = new LinkedHashMap<>();
        source.put("A", 10);
        source.put("B", 20);
        Map<String, Integer> view = Collections.unmodifiableMap(source);
        Set<String> keys = view.keySet();
        Collection<Integer> values = view.values();

        System.out.println("initial=" + view);
        source.put("C", 30);
        source.put("A", 11);
        source.remove("B");
        System.out.println("updated=" + view);
        System.out.println("capturedKeys=" + keys);
        System.out.println("capturedValues=" + values);

        Map.Entry<String, Integer> writableEntry =
                source.entrySet().iterator().next();
        writableEntry.setValue(12);
        System.out.println("afterSourceEntryUpdate=" + view);

        Map.Entry<String, Integer> readOnlyEntry =
                view.entrySet().iterator().next();
        expectUOE("mapPut", () -> view.put("D", 40));
        expectUOE("keyRemove", () -> keys.remove("A"));
        expectUOE("valueRemove", () -> values.remove(12));
        expectUOE("entrySetValue", () -> readOnlyEntry.setValue(99));
        System.out.println("afterBlockedWrites=" + source);

        source.clear();
        System.out.println("emptyView=" + view);
        System.out.println("emptyKeys=" + keys);
        source.put("N", 7);
        System.out.println("refilledView=" + view);
        System.out.println("refilledValues=" + values);
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 LiveMapViewDemo.java
java LiveMapViewDemo
```

Stdout chính xác:

```text
initial={A=10, B=20}
updated={A=11, C=30}
capturedKeys=[A, C]
capturedValues=[11, 30]
afterSourceEntryUpdate={A=12, C=30}
mapPut=UnsupportedOperationException
keyRemove=UnsupportedOperationException
valueRemove=UnsupportedOperationException
entrySetValue=UnsupportedOperationException
afterBlockedWrites={A=12, C=30}
emptyView={}
emptyKeys=[]
refilledView={N=7}
refilledValues=[7]
```

### 5.1. Thêm, thay giá trị và xóa đều đi qua nguồn

Khi nguồn thêm C, đổi giá trị A và xóa B, view hiện {A=11,C=30}. keys và values đã lấy từ view trước đó cũng hiện các khóa/giá trị đang có, chứ không giữ ảnh chụp lúc chúng được lấy.

writableEntry lấy từ source.entrySet() trong ví dụ LinkedHashMap này có thể cập nhật A thành 12. Đó vẫn là một đường ghi hợp lệ của nguồn, nên view đọc thấy A=12.

Không tổng quát khả năng setValue của entry nguồn thành tính chất của mọi loại Map.Entry trong Java. Một số entry từ API khác là ảnh chụp không cho cập nhật; bài này chỉ dùng đường được map cụ thể hỗ trợ.

### 5.2. Entry được trả từ lớp bọc không mở lại quyền ghi

readOnlyEntry lấy qua view.entrySet() không cho setValue(99). keys.remove("A") và values.remove(12) cũng bị chặn, giống view.put("D",40).

Cần phân biệt hai thao tác:

- entry.setValue(newValue) là thay giá trị của ánh xạ; lớp bọc map chặn đường ghi này.
- entry.getValue().someMutator() là gọi một phương thức trên đối tượng giá trị; nếu giá trị mutable, lớp bọc map không tự biến đối tượng ấy thành bất biến.

Chương trình dùng Integer nên không có phương thức sửa giá trị bên trong. Với List hoặc một đối tượng nghiệp vụ làm giá trị, phải phân tích thêm tầng đối tượng đó.

### 5.3. Những view đã lấy vẫn theo dữ liệu mới

Sau source.clear(), cả view và keys rỗng. Sau source.put("N",7), view hiện N=7 và values hiện 7.

Không cần lấy lại view.values() mới để nhìn thấy ánh xạ mới qua collection values cũ. Tuy nhiên, một iterator đã đi dở trên collection đó có quy tắc riêng; không dùng ví dụ này để suy rằng mọi iterator cũ đều tự theo dữ liệu mới.

## 6. Phần tử có thể đổi ngay cả qua view chỉ đọc

Lớp bọc điều khiển thao tác của collection. Nó không chặn mọi phương thức của mọi đối tượng mà collection trả về.

Nếu view.get(0) trả một Reading có trường value thay đổi được, câu lệnh view.get(0).value = 15 không gọi list.set(). Nó sửa chính Reading. Vì vậy không có lý do để lớp bọc list ném UnsupportedOperationException cho thao tác đó.

Tương tự, một list ngoài không cho sửa có thể chứa các list trong vẫn sửa được. Không có cơ chế tự bọc đệ quy mọi lớp dữ liệu bên trong.

### 6.1. Chương trình phân biệt ba tầng

Chương trình dưới đây so sánh:

- view của danh sách nguồn.
- shallowSnapshot là bản sao các tham chiếu phần tử bằng List.copyOf.
- detachedValues là danh sách các giá trị mới, mỗi giá trị là record chỉ chứa int.

Nửa sau dùng danh sách lồng nhau chứa String để làm rõ việc chỉ sao chép lớp ngoài.

Tệp **ReadOnlyLayersDemo.java**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReadOnlyLayersDemo {
    static final class Reading {
        int value;

        Reading(int value) {
            this.value = value;
        }
    }

    record ReadingSnapshot(int value) {}

    private static List<Integer> values(List<Reading> readings) {
        return readings.stream().map(r -> r.value).toList();
    }

    public static void main(String[] args) {
        List<Reading> source = new ArrayList<>();
        source.add(new Reading(10));
        source.add(new Reading(20));
        List<Reading> view = Collections.unmodifiableList(source);
        List<Reading> shallowSnapshot = List.copyOf(source);

        view.get(0).value = 15;
        System.out.println("sourceAfterElementEdit=" + values(source));
        System.out.println("snapshotAfterElementEdit=" + values(shallowSnapshot));

        source.set(0, new Reading(99));
        System.out.println("viewAfterReplacement=" + values(view));
        System.out.println("snapshotAfterReplacement=" + values(shallowSnapshot));

        List<ReadingSnapshot> detachedValues = source.stream()
                .map(r -> new ReadingSnapshot(r.value))
                .toList();
        source.get(0).value = 100;
        System.out.println("viewAfterLaterEdit=" + values(view));
        System.out.println("detachedValues=" +
                detachedValues.stream().map(ReadingSnapshot::value).toList());

        List<String> inner = new ArrayList<>(List.of("x"));
        List<List<String>> nestedSource = new ArrayList<>();
        nestedSource.add(inner);
        List<List<String>> outerView =
                Collections.unmodifiableList(nestedSource);
        List<List<String>> outerSnapshot = List.copyOf(nestedSource);

        outerView.get(0).add("y");
        List<List<String>> copiedBothLevels = nestedSource.stream()
                .map(List::copyOf)
                .toList();
        inner.add("z");
        System.out.println("nestedView=" + outerView);
        System.out.println("outerSnapshot=" + outerSnapshot);
        System.out.println("copiedBothLevels=" + copiedBothLevels);

        source.clear();
        System.out.println("viewAfterClear=" + values(view));
        System.out.println("shallowSnapshotAfterClear=" + values(shallowSnapshot));
        System.out.println("detachedAfterClear=" +
                detachedValues.stream().map(ReadingSnapshot::value).toList());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ReadOnlyLayersDemo.java
java ReadOnlyLayersDemo
```

Stdout chính xác:

```text
sourceAfterElementEdit=[15, 20]
snapshotAfterElementEdit=[15, 20]
viewAfterReplacement=[99, 20]
snapshotAfterReplacement=[15, 20]
viewAfterLaterEdit=[100, 20]
detachedValues=[99, 20]
nestedView=[[x, y, z]]
outerSnapshot=[[x, y, z]]
copiedBothLevels=[[x, y]]
viewAfterClear=[]
shallowSnapshotAfterClear=[15, 20]
detachedAfterClear=[99, 20]
```

### 6.2. Một bản sao nông vẫn có thể hiện thay đổi thuộc tính

Lúc đầu, view, shallowSnapshot và source đều giữ cùng Reading đầu tiên. Sửa value của Reading ấy thành 15 làm cả nguồn và shallowSnapshot đọc ra 15.

Sau đó source.set(0,new Reading(99)) thay tham chiếu ở nguồn. view đi theo vị trí của nguồn nên thấy 99. shallowSnapshot vẫn giữ tham chiếu tới Reading trước đó, nên thấy 15.

Hai dòng kết quả này chỉ ra hai nguyên nhân khác nhau làm dữ liệu nhìn thấy thay đổi:

1. Cấu trúc nguồn thay đổi phần tử/ánh xạ → live view đọc thấy.
2. Đối tượng phần tử dùng chung bị sửa → mọi nơi còn giữ đối tượng ấy có thể thấy trạng thái mới, kể cả bản sao nông.

Không kết luận rằng List.copyOf “vẫn là view sống của source” chỉ vì một đối tượng mutable trong bản sao đổi trạng thái.

### 6.3. Chuyển sang giá trị bất biến khi cần ảnh chụp trạng thái

detachedValues tạo ReadingSnapshot mới từ giá trị int đang đọc. ReadingSnapshot chỉ chứa int, nên trạng thái cần lưu không còn liên kết mutable với Reading nguồn.

Nguồn đổi value từ 99 thành 100 rồi bị clear, detachedValues vẫn lưu [99,20]. Đây là kết quả của phép ánh xạ giá trị rõ ràng cộng với cấu trúc kết quả không cho sửa; không phải tác dụng riêng của lớp bọc unmodifiable.

Không phải mọi record đều bất biến sâu. Nếu một record chứa List mutable và lưu nguyên tham chiếu đó, trạng thái bên trong vẫn có thể đổi. Ví dụ này chọn thành phần int để tránh sự nhập nhằng ấy.

### 6.4. Danh sách lồng nhau cần xét từng tầng

outerView.get(0).add("y") thành công vì danh sách trong chưa bị bọc và vẫn mutable. outerSnapshot tạo bằng List.copyOf(nestedSource) cũng giữ chính inner đó.

copiedBothLevels sao chép từng list trong bằng List.copyOf rồi thu các kết quả vào list ngoài không cho sửa. Khi inner gốc nhận z, outerView và outerSnapshot đều hiện x,y,z, còn copiedBothLevels giữ x,y.

Vì các phần tử cuối cùng là String bất biến, hai tầng sao chép ở mô hình cụ thể này đủ tách trạng thái cần lưu. Không gọi cách viết ấy là một thuật toán sao chép sâu cho mọi đồ thị tùy ý.

Stream.toList() được dùng trong ví dụ để tạo list kết quả không cho sửa; nó là một phép thu kết quả, không phải unmodifiable view tự theo danh sách nguồn. Hợp đồng ở [Stream.toList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList()) cần được đọc riêng với Collections.unmodifiableList.

## 7. Muốn nguồn đổi mà kết quả không đổi thì phải cắt liên kết phù hợp

### 7.1. Sao chép cấu trúc trước khi bọc

Với list, biểu thức Collections.unmodifiableList(new ArrayList<>(source)) có hai bước khác nhau:

1. new ArrayList<>(source) tạo list chứa các tham chiếu phần tử lúc sao chép.
2. unmodifiableList(...) chặn thao tác ghi qua kết quả trả về.

Các thay đổi cấu trúc về sau của source không làm list riêng đó đổi. Tuy nhiên, nếu vẫn có ai giữ tham chiếu mutable tới list riêng, họ có thể sửa nó và lớp bọc sẽ đọc thấy. Chính sách “ảnh chụp không ai thay đổi cấu trúc được” cần cả việc không rò rỉ đường ghi vào bản sao.

Khi tạo list riêng ngay trong biểu thức rồi chỉ trả lớp bọc, ứng dụng có thể giữ riêng quyền sở hữu cấu trúc ấy. Còn các phần tử vẫn là vấn đề sao chép nông đã thấy ở ReadOnlyLayersDemo.

### 7.2. List.copyOf là lựa chọn rõ cho ảnh chụp phần tử không null

List.copyOf(source) trả list không cho sửa và các thay đổi sau đó của collection đầu vào không phản ánh vào kết quả về mặt thêm/xóa/thay tham chiếu phần tử. Nó vẫn không tự nhân bản các đối tượng phần tử.

Phương thức không nhận phần tử null. Vì vậy, thay Collections.unmodifiableList(source) bằng List.copyOf(source) có thể thay đổi cả:

- Quan hệ theo dõi nguồn.
- Thời điểm sao chép dữ liệu.
- Chính sách null.
- Chi phí tạo kết quả.

Không dùng thay thế ấy như một “tối ưu không đổi hành vi” nếu API trước đó có chủ đích trả view sống hoặc hỗ trợ null.

Nếu hợp đồng cần giữ null ở các phần tử, một cách sao chép cấu trúc có thể là hàm minh họa sau. Đây là một phương thức cần đặt trong một lớp Java có import Collection, List, ArrayList và Collections; nó không phải chương trình main độc lập:

```java
static <T> List<T> snapshotAllowingNull(Collection<? extends T> source) {
    return Collections.unmodifiableList(new ArrayList<>(source));
}
```

Hàm chỉ cho phép null ở phần tử theo khả năng của ArrayList; tham chiếu source bằng null vẫn không hợp lệ. Nó không sao chép sâu phần tử.

List.copyOf có thể tái sử dụng một list đầu vào đã phù hợp trong một số trường hợp. Đó không phải lý do để kiểm tra bằng == hoặc suy rằng nó được phép trả một view còn theo cấu trúc mutable ở ngoài. Dựa vào hợp đồng kết quả, không dựa vào số lần cấp phát.

Xem [List.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)) để đối chiếu bảo đảm tách khỏi cập nhật collection đầu vào và điều kiện null.

### 7.3. Map và Set cũng cần chọn cách sao chép đúng yêu cầu

Map.copyOf(sourceMap) tạo kết quả không cho sửa và không theo các cập nhật ánh xạ sau đó của map đầu vào; khóa và giá trị vẫn có thể dùng chung đối tượng. Nó không chấp nhận khóa/giá trị null và không phải lựa chọn mặc định để giữ một thứ tự duyệt tùy chỉnh của nguồn.

Nếu cần thứ tự chèn cụ thể của một LinkedHashMap tại thời điểm sao chép, có thể sao chép sang LinkedHashMap riêng rồi bọc unmodifiableMap. Nếu cần thứ tự Comparator và khả năng điều hướng của cây, cần chọn cấu trúc sao chép và lớp bọc giữ các khả năng đó.

Với Set, tách phần tử của cấu trúc nguồn không làm phần tử mutable trở thành khóa an toàn hoặc bất biến. Thay thuộc tính ảnh hưởng equals/hashCode/Comparator khi phần tử đang nằm trong set vẫn là vấn đề của mô hình dữ liệu.

Phân biệt ba mức để chọn:

| Kết quả | Theo thay đổi cấu trúc của source? | Có thể cùng phần tử mutable? |
|---|---|---|
| Collections.unmodifiableList(source) | Có, theo hợp đồng nguồn/view | Có |
| Collections.unmodifiableList(new ArrayList<>(source)) | Không theo source ban đầu | Có |
| List.copyOf(source) | Không theo collection đầu vào về cấu trúc | Có |
| Danh sách các giá trị bất biến được dựng riêng | Không theo cấu trúc nguồn | Chỉ còn dùng chung những gì mô hình giá trị cho phép |

## 8. Thiết kế API: công khai view hay công khai ảnh chụp?

Nếu một đối tượng nghiệp vụ muốn sở hữu danh sách nhưng cho nơi khác theo dõi trạng thái mới, trả view sống có thể là hành vi đúng. Nếu nơi gọi cần một kết quả cố định cho báo cáo, kiểm tra hoặc một bước xử lý, trả snapshot thường phù hợp hơn.

Nên thể hiện khác biệt trong tên hoặc tài liệu phương thức. Chỉ ghi “trả danh sách chỉ đọc” chưa đủ nói rõ nguồn thay đổi thì kết quả sẽ thế nào.

Lớp minh họa sau không có main, có thể lưu trong **TagCatalog.java**. Các phần tử String bất biến giúp tập trung vào quyền sở hữu cấu trúc:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class TagCatalog {
    private final List<String> tags;
    private final List<String> readOnlyTags;

    TagCatalog(Collection<String> initialTags) {
        tags = new ArrayList<>(List.copyOf(initialTags));
        readOnlyTags = Collections.unmodifiableList(tags);
    }

    void addTag(String tag) {
        tags.add(Objects.requireNonNull(tag));
    }

    List<String> tagsView() {
        return readOnlyTags;
    }

    List<String> tagsSnapshot() {
        return List.copyOf(tags);
    }
}
```

Trong constructor, dữ liệu đầu vào được sao chép và kiểm tra không có null qua List.copyOf, rồi đặt vào ArrayList riêng để TagCatalog còn có thể thêm tag. Việc nơi gọi sửa collection initialTags sau đó không sửa trực tiếp cấu trúc nội bộ.

tagsView() luôn trả một view của danh sách nội bộ. Một view đã nhận trước đó sẽ thấy tag được thêm hợp lệ qua addTag(). tagsSnapshot() trả một list giữ các phần tử của thời điểm gọi; thêm tag về sau không cập nhật snapshot đó.

Ở đây final giữ tham chiếu tags và readOnlyTags ổn định sau constructor. Nó không khiến nội dung tags bất biến: addTag vẫn làm tags đổi. Chặn ghi ở bên nhận đến từ lớp bọc, còn quyền cập nhật của TagCatalog là chủ đích thiết kế.

Lớp minh họa chưa thiết kế chính sách cho nhiều luồng. Nếu cần dùng đồng thời, phải bổ sung một hợp đồng đồng bộ hoặc xuất bản snapshot phù hợp; không có bảo đảm ấy chỉ vì trường là private final hoặc kết quả là unmodifiable.

### 8.1. Bọc đầu vào trực tiếp vẫn để người gửi giữ đường ghi

Một lỗi thiết kế phổ biến là lưu Collections.unmodifiableList(input) vào trường, rồi cho rằng cấu trúc từ đây chỉ do lớp mình kiểm soát. Người gửi vẫn có input mutable và có thể sửa nó.

Nếu mục tiêu là ngăn người gửi thay đổi cấu trúc nội bộ qua dữ liệu đã truyền vào, cần sao chép phòng vệ khi nhận đầu vào. Nếu mục tiêu là cố ý theo dõi input đang thay đổi, bọc trực tiếp có thể đúng, nhưng phải ghi rõ và chấp nhận quan hệ đó.

Không có một lựa chọn duy nhất đúng cho mọi getter hoặc constructor. Lựa chọn phụ thuộc hợp đồng sở hữu dữ liệu.

### 8.2. Bọc nhiều lần không làm dữ liệu độc lập hơn

Nếu lớp bọc mới vẫn đọc qua lớp bọc cũ, rồi lớp bọc cũ đọc nguồn mutable, nguồn cuối cùng vẫn là nơi lưu dữ liệu.

Bởi vậy, unmodifiableList(unmodifiableList(source)) không có ý nghĩa “đóng băng hai lần”. Một JDK còn có thể tái sử dụng lớp bọc đã phù hợp. Muốn cắt liên kết cần bước sao chép thật sự, không phải thêm số lớp bọc.

### 8.3. Nếu nguồn đã không còn thay đổi thì view có thể trông cố định

Collections.unmodifiableList(List.of("a","b")) không có nguồn mutable bên dưới để người giữ list ấy thay đổi cấu trúc qua API thông thường. Trong trường hợp đó dữ liệu không biến động về cấu trúc.

Điều này không đổi định nghĩa chung của unmodifiable view. Nó chỉ có nghĩa nguồn của trường hợp cụ thể cũng không cho sửa. Nếu phần tử là đối tượng mutable, trạng thái phần tử vẫn cần xét riêng.

## 9. View sống không đồng nghĩa iterator luôn sống

### 9.1. View và iterator là hai đối tượng với hợp đồng khác nhau

Một collection view có thể đọc trạng thái nguồn hiện tại mỗi lần truy vấn. Iterator được tạo ra để duyệt có thể giữ vị trí, trạng thái kiểm tra sửa đổi hoặc một ảnh chụp riêng.

Vì thế không suy luận rằng iterator đã tạo từ view phải nhìn thấy mọi phần tử vừa thêm vào nguồn. Lớp bọc unmodifiable chặn các phép sửa qua iterator; nó không thay iterator nguồn thành một cơ chế duyệt phiên bản mới hoàn toàn.

### 9.2. Ví dụ CopyOnWriteArrayList trong chương trình đầu

concurrentView bọc CopyOnWriteArrayList ban đầu chứa old. oldIterator được tạo trước khi nguồn thêm new.

Sau khi thêm, đọc concurrentView hiện [old,new]. Nhưng duyệt oldIterator chỉ có old. Không có luồng thứ hai trong ví dụ này: khác biệt đến từ hợp đồng iterator giữ ảnh chụp của CopyOnWriteArrayList.

[CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html) quy định iterator theo trạng thái mảng tại lúc tạo. Do đó “view tiếp tục theo nguồn” và “iterator đã lấy giữ phiên bản cũ” có thể cùng đúng.

### 9.3. Với ArrayList, iterator đang duyệt có thể bị vô hiệu

ArrayList có iterator phát hiện sửa đổi cấu trúc theo cơ chế fail-fast. Nếu chủ sở hữu sửa nguồn trong lúc nơi khác đang duyệt iterator của view, lớp bọc không làm việc sửa đó biến mất và không mặc định bảo vệ iterator.

ConcurrentModificationException có thể báo lỗi kiểu này, kể cả các thay đổi tuần tự do cùng một luồng gây ra qua tham chiếu khác. Cơ chế phát hiện là cố gắng bắt lỗi; không dùng ngoại lệ ấy làm công cụ đồng bộ hoặc điều kiện đúng của chương trình.

Không kết luận “chỉ đọc nên không thể có ConcurrentModificationException”. Bên đọc không ghi qua view nhưng bên khác vẫn có thể ghi vào nguồn.

## 10. Làm việc giữa nhiều luồng: chỉ đọc không tự bảo đảm thấy dữ liệu mới

Các chương trình trong bài cho thay đổi và đọc diễn ra tuần tự, nên dễ quan sát quan hệ nguồn/view. Khi có nhiều luồng, cần thêm một tầng hợp đồng.

Bọc một ArrayList trong unmodifiableList không tạo khóa, không tạo quan hệ công bố dữ liệu mới giữa các luồng và không biến ArrayList thành một collection đồng thời.

Không thể từ chữ “live” hứa rằng một luồng khác sẽ thấy mọi cập nhật ngay lập tức hoặc thấy một ảnh chụp nhất quán của nhiều thao tác.

Một thiết kế đồng thời cần xác định:

- Ai có quyền ghi và các bên đọc/ghi phối hợp bằng cơ chế nào.
- Một lần đọc cần một phần tử, nhiều thao tác nhất quán hay cả phiên bản cố định.
- Phần tử mutable được quản lý đồng thời ra sao.
- Khi tạo snapshot, quá trình đọc nguồn có được bảo vệ để không chụp trạng thái hỗn hợp hay không.

Collections.synchronizedList là một cơ chế khác với unmodifiableList. Nếu chọn lớp bọc synchronized, việc truy cập và duyệt phải tuân theo khóa của thiết kế đó; việc chồng thêm một lớp unmodifiable không cho phép tùy tiện khóa một đối tượng khác và coi đã đồng bộ với bên ghi.

Một collection đồng thời như CopyOnWriteArrayList cũng có hợp đồng riêng về thao tác và iterator, không mặc định phù hợp mọi tải ghi hoặc mọi phần tử mutable.

Các nguyên tắc truy cập của [Collections.synchronizedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedList(java.util.List)) và đặc tả của [ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html) giúp phân biệt trách nhiệm lớp bọc với trách nhiệm phối hợp giữa luồng.

## 11. View hợp thành vẫn chịu giới hạn của nguồn

### 11.1. Không chữa được điều kiện vô hiệu của subList

Nếu bọc unmodifiableList(source.subList(...)), lớp ngoài chặn ghi qua kết quả nhưng list bên trong vẫn là subList.

Hợp đồng List.subList quy định ngữ nghĩa của nó trở nên không xác định khi list nguồn bị sửa cấu trúc theo cách không đi qua subList. Vì vậy không hứa rằng mọi kiểu sửa cấu trúc của source đều làm lớp bọc subList trở thành một cửa sổ hợp lệ tự điều chỉnh.

Một lỗi cần tránh là lấy kết quả đúng của unmodifiableList(source) với toàn list rồi áp nguyên vào mọi chuỗi view con. Phải đọc hợp đồng của từng lớp trong chuỗi.

### 11.2. Phạm vi chỉ cho nhìn dữ liệu trong khoảng

Một unmodifiableNavigableSet bọc view phạm vi của TreeSet vẫn chỉ nhìn các phần tử thuộc khoảng. Nguồn thêm phần tử ngoài khoảng thì view không hiện phần tử đó; đây là điều kiện chọn dữ liệu của view trong, không phải bằng chứng lớp bọc đã tạo snapshot.

Tương tự, một unmodifiableMap bọc một subMap chỉ đọc các ánh xạ trong phạm vi khóa của subMap. Nguồn đổi ngoài phạm vi có thể không làm kết quả quan sát từ cửa sổ ấy đổi.

Tính sống phải được hiểu là đọc theo nguồn và điều kiện view đang có, không phải mọi cập nhật ở bất cứ đâu trong cấu trúc gốc đều xuất hiện.

### 11.3. Không giữ một entry qua mọi biến động rồi mặc định nó luôn hợp lệ

Map.Entry lấy từ iterator có những điều kiện sử dụng theo hợp đồng của Map và kiểu entry cụ thể. Lớp bọc không biến entry đã lấy thành một bộ theo dõi khóa có tuổi thọ vô hạn.

Khi cần đọc giá trị hiện tại của một khóa qua map view sau cập nhật, dùng thao tác truy vấn map phù hợp. Khi cần lưu ảnh chụp entry, tạo một biểu diễn dữ liệu riêng và xác định mức độ sao chép của khóa/giá trị.

Chương trình LiveMapViewDemo chỉ dùng entry ở những điểm cập nhật/đọc được kiểm soát; nó không dựa vào entry cũ sau khi nguồn bị clear rồi nạp lại.

## 12. Chi phí, vòng đời và cách kiểm tra hợp đồng

Lớp bọc trực tiếp không có ý nghĩa sao chép toàn bộ phần tử nguồn sang một cấu trúc độc lập. Đây là lợi ích khi muốn cho đọc trạng thái đang thay đổi mà không tạo snapshot sau mỗi lần yêu cầu.

Nhưng không có nghĩa mọi thao tác đọc đều rẻ như nhau. Duyệt n phần tử vẫn phải đọc n phần tử; tìm kiếm hoặc truy cập theo vị trí phụ thuộc cấu trúc nguồn và hợp đồng của nó. Lớp bọc không tự thay LinkedList thành ArrayList hoặc tạo chỉ mục mới.

Giữ một view còn giữ quan hệ với nguồn. Nếu view hoặc chuỗi view sống lâu hơn nhu cầu ban đầu, nó có thể kéo dài vòng đời của cấu trúc nguồn. Khi chỉ cần một phần dữ liệu lịch sử nhỏ và muốn tách vòng đời, sao chép phù hợp có thể tốt hơn việc giữ view.

Để kiểm tra đúng tính chất của một API chỉ đọc, không chỉ thử add rồi dừng. Một bộ kiểm tra hành vi nên có:

1. Thử một thao tác ghi thực sự qua kết quả và xác nhận bị từ chối theo hợp đồng.
2. Sửa nguồn qua đường được phép, rồi đọc lại kết quả để biết đó là live view hay snapshot.
3. Thử thay tham chiếu ở một vị trí nguồn, tách khỏi việc chỉ sửa thuộc tính của phần tử.
4. Nếu phần tử mutable, thử sửa thuộc tính để kiểm tra mức độc lập thực tế.
5. Với cấu trúc lồng nhau, kiểm tra cả tầng trong.
6. Nếu có iterator hoặc view con, kiểm tra theo hợp đồng riêng của chúng.
7. Nếu cần snapshot nhất quán giữa các luồng, kiểm chứng cơ chế phối hợp lúc tạo snapshot.

Không dùng một thao tác không làm thay đổi gì, chẳng hạn một lời gọi với dữ liệu rỗng, để phân loại mọi collection trong Java. Một số phép cập nhật không có hiệu lực có quy tắc ngoại lệ phụ thuộc hợp đồng cụ thể. Hãy chọn một thao tác ghi thực sự khi mục tiêu là kiểm chứng quyền ghi bị chặn.

## 13. Bài tập và lời giải

### Bài 1 — Bọc trực tiếp hay bọc sau sao chép?

Nguồn là ArrayList [1,2]. Tạo a = unmodifiableList(source) và b = unmodifiableList(new ArrayList<>(source)). Sau source.add(3), a và b hiện gì?

**Lời giải:** a hiện [1,2,3], b hiện [1,2]. a còn đọc cấu trúc source; b đọc một ArrayList khác đã có trước lần thêm. Cả hai đều không cho thêm phần tử qua chính tham chiếu kết quả.

### Bài 2 — Gán lại biến

x đang trỏ ArrayList [A]. v = unmodifiableList(x). Sau x = new ArrayList<>(List.of("B")), v hiện A hay B?

**Lời giải:** A. Lớp bọc đang giữ quan hệ với đối tượng được truyền vào lúc tạo, không theo tên biến x. Nếu đối tượng cũ bị sửa hợp lệ qua một tham chiếu khác, v mới đọc thấy các sửa đổi đó.

### Bài 3 — final có ngăn nguồn thay đổi không?

Một trường final List<String> source được khởi tạo bằng ArrayList. Một trường final khác bọc source bằng unmodifiableList. Lệnh source.add("new") trong lớp có bị chặn vì final không?

**Lời giải:** không. final chặn gán lại trường source sau khởi tạo theo quy tắc thông thường, không cấm thay đổi nội dung ArrayList. View đọc thấy phần tử mới; add qua view vẫn bị chặn.

### Bài 4 — Bản sao nông có phần tử mutable

source chứa một Account mutable có balance = 10. a là unmodifiable view, b là List.copyOf(source). Nếu source.get(0).balance = 20 thì a và b đọc balance bằng bao nhiêu? Sau khi source.set(0,new Account(30)) thì sao?

**Lời giải:** sau sửa thuộc tính, cả hai đọc 20 vì còn cùng Account. Sau thay tham chiếu, a đọc Account mới có 30, b giữ Account cũ có 20. Kết quả thứ nhất không chứng minh b là view của cấu trúc source.

### Bài 5 — Map.Entry có chặn hết trạng thái bên trong không?

m là unmodifiableMap của Map<String,List<String>>. So sánh khả năng m.entrySet().iterator().next().setValue(newList) và m.get("k").add("x"), giả sử giá trị hiện có là một ArrayList mutable.

**Lời giải:** setValue là thao tác thay ánh xạ qua entry của lớp bọc và bị chặn. add trên ArrayList giá trị vẫn có thể thành công, vì nó sửa đối tượng được trả ra chứ không gọi một phép ghi của map. Muốn chặn hoặc tách trạng thái đó phải thiết kế thêm ở tầng giá trị.

### Bài 6 — View rỗng

Sau khi tạo view, chủ sở hữu gọi source.clear(). Có thể xem view như một list rỗng cố định để cache lâu dài không?

**Lời giải:** không thể chỉ từ clear mà kết luận vậy. Nguồn có thể thêm dữ liệu về sau và cùng view đọc thấy lại. Nếu cần giữ kết quả rỗng của một thời điểm, phải lấy kết quả với hợp đồng snapshot phù hợp.

### Bài 7 — Iterator của CopyOnWriteArrayList

Tạo unmodifiable view của CopyOnWriteArrayList [a]. Lấy iterator i, rồi nguồn thêm b. Một lần đọc view và một lần duyệt i có bắt buộc cùng hiện a,b không?

**Lời giải:** không. View hiện tại có [a,b], còn i giữ ảnh chụp lúc tạo và duyệt [a]. Chặn ghi qua iterator và quy tắc iterator nhìn phiên bản nào là hai vấn đề riêng.

### Bài 8 — Đổi getter sang List.copyOf

Một getter trước đây trả live view của danh sách có thể chứa null. Có thể thay toàn bộ bằng List.copyOf mà không thông báo thay đổi hợp đồng không?

**Lời giải:** không. Kết quả sẽ không theo các cập nhật cấu trúc về sau như view trước đó, và dữ liệu có null có thể khiến lời gọi thất bại. Cần xác định lại hợp đồng, xử lý null và kiểm tra nơi gọi phụ thuộc tính sống.

### Bài 9 — Chọn hợp đồng cho báo cáo

Một báo cáo cần danh sách giao dịch đúng tại thời điểm bắt đầu tạo báo cáo. Dữ liệu nguồn vẫn tiếp tục được cập nhật. Chỉ trả unmodifiableList(source) đã đáp ứng chưa?

**Lời giải:** chưa. Nó chặn báo cáo sửa danh sách qua kết quả nhưng không tách các cập nhật nguồn. Cần tạo snapshot nhất quán trong điều kiện phối hợp đúng với bên ghi. Nếu giao dịch mutable và báo cáo cần trạng thái cố định của chúng, còn phải tạo giá trị/ảnh chụp thích hợp cho từng giao dịch.

## 14. Những điểm cần nhận ra khi đọc code thực tế

| Điều đang thấy trong code | Kết luận được phép rút ra | Điều còn phải kiểm tra |
|---|---|---|
| Collections.unmodifiableList(source) | Ghi qua lớp bọc bị chặn; đọc theo nguồn | Ai còn đường ghi vào nguồn? |
| Nguồn được sửa sau khi tạo view | View toàn nguồn có thể đọc trạng thái mới hợp lệ | Quy tắc iterator, luồng và view con |
| new ArrayList<>(source) rồi bọc | Cấu trúc riêng với source ban đầu | Bản sao có rò rỉ đường ghi; phần tử có dùng chung không? |
| List.copyOf(source) | Kết quả không theo thay đổi cấu trúc của collection đầu vào | Null, phần tử mutable và thời điểm sao chép |
| Trường nguồn là final | Tham chiếu trường có ràng buộc gán lại | Nội dung đối tượng vẫn có thể đổi |
| Các tầng ngoài đều unmodifiable | Chặn ghi qua các tầng đã bọc | Tầng trong chưa bọc và các đối tượng giá trị |
| Dữ liệu in giống lúc tạo view | Chưa quan sát thấy thay đổi | Nguồn có thực sự bị sửa, thay đổi có nằm trong phạm vi không? |
| Một iterator chỉ thấy dữ liệu cũ | Có thể là quy tắc iterator | Không kết luận cả view là snapshot chỉ từ iterator đó |
| Kết quả chỉ được đọc bởi nơi nhận | Nơi nhận không ghi qua tham chiếu ấy | Bên khác có ghi và có đồng bộ đúng không? |

Câu hỏi hữu ích nhất khi gặp một unmodifiable view là: **lớp bọc đang đọc từ đối tượng nào, và ai vẫn có thể sửa đối tượng đó hoặc các phần tử được chứa?** Từ câu trả lời ấy mới quyết định cần view sống, bản sao cấu trúc hay ảnh chụp giá trị.

## 15. Tài liệu đối chiếu

- [Collections — unmodifiableList, unmodifiableSet và unmodifiableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html).
- [Collection — View Collections và Unmodifiable Collections](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html).
- [List — copyOf và hợp đồng subList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html).
- [Map — collection views và copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).
- [CopyOnWriteArrayList — iterator giữ ảnh chụp](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unmodifiable-collections-004">List.of, Set.of, Map.of và Map.ofEntries</a>

<details>
<summary>Xem chi tiết</summary>

# List.of, Set.of, Map.of và Map.ofEntries

Khi danh sách trạng thái, tập quyền hoặc bảng tra cứu đã được xác định ngay lúc xây dựng, viết một loạt `add` và `put` dễ làm ý định bị chìm trong thao tác khởi tạo. Các phương thức `List.of`, `Set.of`, `Map.of` và `Map.ofEntries` cho phép ghi trực tiếp dữ liệu cần có và trả về vật chứa không sửa được.

Điều quan trọng không chỉ là cú pháp ngắn. Mỗi phương thức diễn đạt một hợp đồng về thứ tự, phần tử trùng, `null` và quyền sửa. Chọn sai hợp đồng có thể làm chương trình ném ngoại lệ ngay khi tạo dữ liệu, hoặc làm một kiểm thử phụ thuộc vào thứ tự duyệt không được bảo đảm.

Các phương thức này có từ **Java 9**. Toàn bộ chương trình trong bài dùng mức Java 17, không cần thư viện ngoài. Người học cần biết ý nghĩa cơ bản của `List`, `Set`, `Map`, kiểu tham số và ngoại lệ. [Các bổ sung của JDK 9](https://docs.oracle.com/javase/9/whatsnew/).

## 1. Chọn ý nghĩa dữ liệu trước khi chọn lời gọi

Xét một công cụ quản lý tác vụ:

- Các bước `"receive"`, `"validate"`, `"store"` có thứ tự. Lặp một bước có thể có ý nghĩa. Đây là dữ liệu dạng list.
- Các quyền `"READ"`, `"WRITE"` là thành viên của một tập. Thứ tự khai báo không nhất thiết là thứ tự hiển thị. Đây là dữ liệu dạng set.
- Mã trạng thái `"NEW"` tương ứng một nhãn hiển thị. Mỗi mã phải ánh xạ tới một giá trị. Đây là dữ liệu dạng map.

Đoạn sau là mã minh họa trong một phương thức có đủ import:

~~~java
List<String> pipeline = List.of("receive", "validate", "store");
Set<String> permissions = Set.of("READ", "WRITE");
Map<String, String> labels = Map.of(
        "NEW", "Mới",
        "DONE", "Hoàn tất"
);
~~~

Đừng dùng set chỉ để làm các phần tử “không sửa được” nếu thứ tự và lặp lại còn quan trọng. List cũng có thể không sửa được. Ngược lại, đừng dùng list thay cho tập quyền rồi để những phần tử trùng gây hiểu nhầm về dữ liệu.

Map không phải là một list chứa các cặp theo thứ tự tham số. Cú pháp `"NEW", "Mới", "DONE", "Hoàn tất"` mô tả các ánh xạ, không tạo cam kết rằng `"NEW"` sẽ được duyệt trước `"DONE"`.

Các phương thức là **phương thức tạo tĩnh** (static factory methods) trên giao diện. Chúng tạo và trả một đối tượng thỏa hợp đồng của giao diện; chương trình không cần biết lớp triển khai bên trong. Hãy lưu kết quả bằng kiểu `List<E>`, `Set<E>` hoặc `Map<K,V>` phù hợp.

## 2. Bảng hợp đồng cần nhớ

| Cách tạo | Dữ liệu đầu vào | Trùng lặp | `null` khi tạo | Thứ tự kết quả |
| --- | --- | --- | --- | --- |
| `List.of(e1, e2, ...)` | Các phần tử | Cho phép phần tử bằng nhau | Không cho phần tử `null` | Giữ thứ tự các đối số |
| `Set.of(e1, e2, ...)` | Các phần tử | Từ chối phần tử trùng bằng `IllegalArgumentException` | Không cho phần tử `null` | Không có cam kết về thứ tự duyệt |
| `Map.of(k1, v1, k2, v2, ...)` | Các cặp khóa–giá trị xen kẽ | Từ chối khóa trùng; giá trị có thể trùng | Không cho khóa hoặc giá trị `null` | Không có cam kết về thứ tự duyệt |
| `Map.ofEntries(entry1, entry2, ...)` | Các `Map.Entry` | Từ chối khóa trùng; giá trị có thể trùng | Không cho entry, khóa hoặc giá trị `null` | Không có cam kết về thứ tự duyệt |

Kết quả của cả bốn cách tạo đều **không sửa được qua API vật chứa**: không thêm, xóa hoặc thay thế phần tử/ánh xạ. Riêng các đối tượng phần tử, khóa và giá trị có thể vẫn sửa được nếu kiểu của chúng cho phép.

Cần đọc hai cột “trùng” và “thứ tự” cùng nhau. Set không có phần tử trùng không đồng nghĩa set đã được sắp xếp. Map không có khóa trùng không đồng nghĩa khóa đầu tiên trong lời gọi là khóa đầu tiên khi duyệt.

Tài liệu Java 17 mô tả riêng các bảo đảm của [list không sửa được](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#unmodifiable), [set không sửa được](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#unmodifiable) và [map không sửa được](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#unmodifiable).

## 3. `List.of`: một dãy có thứ tự, cho phép lặp lại

### Cú pháp từ rỗng đến nhiều phần tử

~~~java
List<String> empty = List.of();
List<String> single = List.of("ready");
List<String> repeated = List.of("start", "retry", "retry", "finish");
~~~

Ở list cuối, hai lần `"retry"` là hai vị trí trong dãy. Không có bước loại trùng. `get(0)` là `"start"` và thứ tự dãy theo đúng thứ tự đối số.

Java cung cấp các overload với số đối số cố định từ 0 đến 10 và một dạng nhận số đối số thay đổi (varargs). Vì vậy **`List.of` không bị giới hạn ở 10 phần tử**:

~~~java
List<Integer> months = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
~~~

Không cần biết overload nào được chọn để dùng đúng hợp đồng list. Cũng không nên suy ra một ngưỡng hiệu năng hoặc cấu trúc lưu trữ ổn định từ số lượng overload.

### Không hỗ trợ cả thêm, xóa lẫn thay phần tử

Với list do `List.of` tạo, `add` và `remove` bị từ chối; `set` cũng bị từ chối. Đây không phải list “chỉ cố định kích thước nhưng còn cho thay ô”.

Đoạn sau là **ví dụ thao tác sẽ ném ngoại lệ**, không phải cách cập nhật list:

~~~java
List<String> steps = List.of("draft", "review");
steps.set(0, "editing"); // UnsupportedOperationException
~~~

Một thuật toán sắp xếp tại chỗ hay `replaceAll` cũng là thao tác sửa. Nếu cần các thao tác ấy, tạo bản làm việc có hợp đồng sửa được, hoặc xây dựng kết quả mới bằng một phép biến đổi.

### Kiểu phần tử của kết quả rỗng cần được đặt trong ngữ cảnh

~~~java
List<String> declared = List.of();
var inferred = List.of();
List<String> explicit = List.<String>of();
~~~

`declared` lấy kiểu phần tử từ kiểu đích. Trong ví dụ `var`, không có kiểu đích cụ thể như `List<String>`, nên không nên giả định trình biên dịch tự biết đó là list chuỗi chỉ vì dự định của người viết.

Có thể ghi kiểu rõ khi một API hoặc biểu thức phức tạp cần nó. Kiểu đích giải quyết vấn đề kiểu dữ liệu; nó không đổi quyền sửa kết quả.

## 4. `Set.of`: khai báo một tập thành viên hợp lệ

### Lỗi trùng là lỗi dữ liệu đầu vào

~~~java
Set<String> states = Set.of("NEW", "RUNNING", "DONE");
~~~

Cách khai báo này phù hợp khi mỗi thành viên chỉ được nêu một lần. Nếu danh sách đối số có hai giá trị bằng nhau, lời gọi ném `IllegalArgumentException` thay vì lặng lẽ giữ lại một.

~~~java
Set<String> invalid = Set.of("NEW", new String("NEW"));
// IllegalArgumentException: hai chuỗi bằng nhau theo equals.
~~~

Ví dụ cố ý sai ở trên giúp phân biệt `Set.of` với một quy trình thu thập dữ liệu rồi loại trùng. Nếu đầu vào người dùng thực sự có thể lặp và yêu cầu là gộp lặp, hãy dùng quy trình thích hợp cho yêu cầu đó; đừng chọn một phương thức từ chối trùng rồi coi ngoại lệ là sự cố bất ngờ.

### Trùng được xác định theo bình đẳng giá trị

Hai đối tượng khác nhau về tham chiếu vẫn có thể là phần tử trùng. Ngược lại, cùng `hashCode` chưa đủ làm hai phần tử trùng nếu chúng không bằng nhau theo `equals`.

Chương trình hoàn chỉnh sẽ dùng `"Aa"` và `"BB"`: hai chuỗi này có cùng mã băm nhưng không bằng nhau, nên một set gồm hai chuỗi vẫn hợp lệ. Kết quả không được suy ra chỉ bằng so sánh mã băm.

Nếu dùng một lớp tự viết làm phần tử, hợp đồng `equals`/`hashCode` của lớp phải đúng. Đừng thay đổi trạng thái tham gia so sánh khi đối tượng đang là phần tử set; tính không sửa được của set ngoài không ngăn sửa chính đối tượng ấy.

### Không phải tập sắp xếp hay tập giữ thứ tự chèn

`Set.of("z", "a", "m")` không cam kết duyệt theo `z, a, m`, cũng không cam kết `a, m, z`. Trong một lần chạy có thể tình cờ thấy một trong các thứ tự ấy.

Nếu đầu ra cho người dùng cần sắp xếp, thực hiện sắp xếp rõ ràng. Ví dụ trong chương trình dùng `new TreeSet<>(colors)` chỉ để **tạo biểu diễn in có thứ tự** cho dữ liệu chuỗi. Điều đó không thay hợp đồng của set gốc.

Nếu thứ tự chính là ý nghĩa của dữ liệu, cân nhắc dùng list hoặc một cấu trúc giữ thứ tự có hợp đồng rõ. Không thể suy ra thứ tự nghiệp vụ bằng cách đọc lần duyệt đầu tiên của `Set.of`.

Giống `List.of`, `Set.of` có dạng varargs nên cũng hỗ trợ hơn 10 phần tử, miễn các phần tử không trùng và không `null`.

## 5. `Map.of`: đọc theo từng cặp khóa–giá trị

### Cú pháp xen kẽ phải được nhóm cho dễ đọc

~~~java
Map<String, Integer> retries = Map.of(
        "network", 3,
        "database", 2,
        "validation", 0
);
~~~

Mỗi khóa đứng ngay trước giá trị của nó. Kết quả có ba ánh xạ. `get("network")` trả 3.

Hãy xuống dòng theo từng cặp. Với lời gọi dài trên một dòng, việc ghép nhầm khóa–giá trị vẫn có thể biên dịch nếu các tham số có kiểu tương thích nhưng ý nghĩa dữ liệu đã sai.

Giới hạn của `Map.of` là **tối đa 10 cặp**, không phải 10 đối số đơn lẻ. Nó có các overload cho 0 đến 10 ánh xạ; không có dạng varargs nhận một dãy tùy ý `k1, v1, k2, v2, ...`. Nhu cầu 11 cặp cần `Map.ofEntries` hoặc một cách xây dựng khác.

### Giá trị trùng được; khóa trùng không được

~~~java
Map<String, Integer> aliases = Map.of("one", 1, "single", 1);
~~~

Map có hai khóa khác nhau cùng trỏ tới giá trị bằng 1. Đây là dữ liệu hợp lệ.

Nhưng đoạn cố ý sai sau ném `IllegalArgumentException`:

~~~java
Map<String, Integer> invalid = Map.of("x", 1, "x", 2);
~~~

Nó không thực hiện quy tắc “ghi sau thắng” như hai lời gọi `put` thông thường trên nhiều map sửa được. Ngay cả `Map.of("x", 1, "x", 1)` vẫn có khóa trùng và bị từ chối, dù hai giá trị giống nhau.

Cách tạo này hữu ích để phát hiện lỗi bảng tra cứu khai báo: một mã bị gõ lặp sẽ không âm thầm đè ánh xạ trước.

### Không có khóa là chuyện khác với khóa có giá trị `null`

Map này không cho giá trị `null`. `get` với một khóa hợp lệ nhưng không có mặt vẫn trả `null` theo hợp đồng thông thường của map.

Do đó `map.get("missing") == null` không chứng minh map đã lưu một giá trị `null`. Trong chương trình minh chứng, đó là dấu hiệu không có ánh xạ cho chuỗi `"missing"`.

Cũng không suy từ việc cấm lưu khóa `null` thành lời hứa rằng mọi phép hỏi bằng `null` sẽ trả `false` hoặc `null`. Các thao tác truy vấn phần tử/khóa không hợp lệ có quy tắc riêng và có thể ném ngoại lệ. Nếu đầu vào tra cứu có thể `null`, xử lý điều kiện ấy rõ trước khi tra.

## 6. `Map.ofEntries` và vai trò của `Map.entry`

### Mỗi cặp trở thành một đối số có cấu trúc

~~~java
Map<String, Integer> thresholds = Map.ofEntries(
        Map.entry("low", 10),
        Map.entry("medium", 20),
        Map.entry("high", 30)
);
~~~

`Map.entry(k, v)` tạo một đối tượng `Map.Entry<K,V>` chứa cặp khóa–giá trị. Tạo entry chưa tạo map và cũng chưa thêm cặp vào một map đang tồn tại.

`Map.ofEntries` nhận các entry, lấy khóa và giá trị của chúng để tạo map không sửa được. Nó dùng varargs nên hỗ trợ số entry lớn hơn 10; giới hạn thực tế vẫn là tài nguyên và giới hạn nền tảng thông thường, không có lời hứa “vô hạn”.

Có thể dùng import tĩnh cho các bảng lớn:

~~~java
import static java.util.Map.entry;
~~~

Khi đã có import này, viết `entry("low", 10)` trong `Map.ofEntries`. Đây là lựa chọn cách viết; `Map.entry` đầy đủ thường dễ nhận diện hơn với người mới.

### Entry đầu vào không là nơi lưu ánh xạ sau đó

`Map.ofEntries` lấy khóa và giá trị, không lưu chính các entry đầu vào. Vì vậy thay giá trị của một entry sửa được sau khi tạo không phải cách cập nhật map kết quả. [API Map.ofEntries, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#ofEntries(java.util.Map.Entry...)).

Ví dụ minh họa:

~~~java
AbstractMap.SimpleEntry<String, Integer> sourceEntry =
        new AbstractMap.SimpleEntry<>("limit", 3);

Map<String, Integer> result = Map.ofEntries(sourceEntry);
sourceEntry.setValue(9);
~~~

Sau đó `sourceEntry.getValue()` là 9 nhưng `result.get("limit")` vẫn là 3. Đó là thay một tham chiếu giá trị trong entry nguồn; map đã lấy cặp tham chiếu trước đó.

Nếu giá trị là một list sửa được, map và entry nguồn vẫn có thể cùng giữ tham chiếu tới list ấy. Việc không giữ chính entry không có nghĩa nhân bản sâu đối tượng khóa và giá trị.

### `Map.entry` không cho `setValue`

Entry do `Map.entry` tạo không sửa được, từ chối khóa hoặc giá trị `null` và không tự gắn với map nào. `entry.setValue(...)` ném `UnsupportedOperationException`.

Cần phân biệt hợp đồng của một entry cụ thể với bản thân giao diện `Map.Entry`. Giao diện cũng được dùng cho nhiều entry có nguồn gốc và quyền sửa khác nhau; không thể nhìn kiểu `Map.Entry` rồi kết luận tất cả đều hỗ trợ hay đều từ chối `setValue`. [Giao diện Map.Entry, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html).

Một chi tiết khi cần tuần tự hóa: entry do `Map.entry` tạo **không hỗ trợ tuần tự hóa**. Điều đó không ngăn map do `Map.ofEntries` tạo hỗ trợ tuần tự hóa khi các khóa và giá trị đáp ứng hợp đồng tương ứng, vì map không giữ các entry đầu vào. Đừng suy khả năng của đối tượng trung gian sang đối tượng kết quả.

## 7. `null` và trùng lặp: xác định lỗi ở đúng tầng

### Dùng kiểu rõ trong ví dụ `null`

~~~java
List.of("ok", (String) null);
Set.of("ok", (String) null);
Map.of("x", (Integer) null);
Map.entry("x", (Integer) null);
~~~

Tất cả các lời gọi trên là ví dụ cố ý sai và ném `NullPointerException`. Ép kiểu trong ví dụ giúp thể hiện rõ vị trí dữ liệu `null` đang được kiểm tra.

Với varargs còn có sự khác nhau giữa “một phần tử `null`” và “cả mảng tham số là `null`”:

~~~java
List.of((String) null);    // Một phần tử null.
List.of((String[]) null);  // Mảng varargs null.
~~~

Cả hai đều không hợp lệ. Không dùng một lời gọi trần như `List.of(null)` để giải thích số phần tử nếu còn chưa xác định overload và kiểu mà trình biên dịch chọn.

### `Map.ofEntries` có nhiều vị trí có thể `null`

Đầu vào không hợp lệ có thể là:

- Chính mảng entry là `null`.
- Một phần tử entry trong mảng là `null`.
- Một entry không `null` nhưng chứa khóa hoặc giá trị `null`.

`Map.entry` đã từ chối `null` ngay khi tạo cặp. Để minh chứng việc `Map.ofEntries` cũng kiểm tra nội dung entry đến từ nơi khác, chương trình dùng `SimpleEntry` có khóa `null` và xác nhận lỗi tại bước tạo map.

Trong biểu thức `Map.ofEntries(Map.entry("x", null))`, lỗi đã có thể phát sinh trong `Map.entry` trước khi lời gọi ngoài được thực hiện. Khi đọc stack trace, phân biệt đúng nơi xây dựng dữ liệu sẽ giúp chẩn đoán dễ hơn.

Nếu dữ liệu có đồng thời nhiều lỗi, chẳng hạn vừa trùng vừa có `null`, không nên viết kiểm thử bắt buộc một thứ tự phát hiện lỗi mà hợp đồng không quy định. Thử từng ràng buộc với dữ liệu chỉ vi phạm ràng buộc đang kiểm tra.

### Đây không phải bước làm sạch dữ liệu tự động

Các factory này thích hợp để khẳng định dữ liệu đã có hình dạng hợp lệ. Chúng không tự thay `null` bằng chuỗi rỗng, không tự gộp khóa lặp và không tự chọn giá trị “đúng” khi có xung đột.

Nếu đầu vào đến từ biểu mẫu hay tệp cấu hình, hãy kiểm tra và báo lỗi với ngữ cảnh nghiệp vụ trước khi tạo cấu trúc cuối. `NullPointerException` hoặc `IllegalArgumentException` từ factory không thay thế một thông báo dễ hiểu như “mã trạng thái DONE được khai báo hai lần”.

## 8. Mảng, varargs và phần tử thật sự của collection

### Truyền mảng tham chiếu để lấy nhiều phần tử

~~~java
String[] codes = {"a", "b"};
List<String> list = List.of(codes);
Set<String> set = Set.of(codes);
~~~

Trong cách gọi này, mảng cung cấp các phần tử cho factory. List có hai chuỗi theo thứ tự mảng. Set cũng chứa hai chuỗi, với điều kiện không trùng. Nếu mảng có `null` hoặc phần tử trùng khi tạo set, các ràng buộc vẫn áp dụng.

Kết quả không là view ghi xuyên tới các ô của mảng đầu vào. Đổi `codes[0]` sau đó không đổi phần tử của list/set đã tạo. Chương trình minh chứng sẽ kiểm tra điểm này trực tiếp.

Đây là một khác biệt quan trọng với `Arrays.asList(codes)`: list của `Arrays.asList` dựa trên mảng và còn hỗ trợ thay phần tử. [API Arrays.asList, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

### Muốn một phần tử là chính mảng thì phải nói rõ

~~~java
List<String[]> oneArray = List.<String[]>of(codes);
~~~

Ở đây list chỉ có **một phần tử** và phần tử ấy là đối tượng mảng `codes`. Đổi `codes[0]` sẽ được quan sát qua `oneArray.get(0)[0]` vì mảng là phần tử được chia sẻ.

Không có mâu thuẫn với ví dụ trước: hai lời gọi đã chọn hai mô hình dữ liệu khác nhau. Một lời gọi lấy các chuỗi làm phần tử; lời gọi còn lại lấy mảng làm phần tử.

Cách truyền mảng và ghi tham số kiểu rõ được nêu trong API `List.of(E...)` và `Set.of(E...)`. Java giải quyết overload theo kiểu biểu thức và các pha xác định phương thức áp dụng; không phải dựa vào ý muốn ngầm của người viết. [Quy tắc gọi phương thức trong JLS 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12.2).

### Mảng kiểu nguyên thủy không tự thành nhiều wrapper

~~~java
List<int[]> numbers = List.of(new int[]{3, 4});
~~~

List có một phần tử `int[]`, không phải hai `Integer`. Một đối tượng `int[]` không phải `Integer[]` và factory không tự đi vào mảng để boxing từng số.

Để khai báo dãy các số nguyên dạng wrapper, dùng `List.of(3, 4)`. Để chuyển dữ liệu từ mảng nguyên thủy đã tồn tại, chọn một bước chuyển đổi rõ ràng; không đoán kiểu phần tử từ dấu ngoặc của mảng.

Cũng cần lưu ý khi truyền một collection sẵn có:

~~~java
List<String> existing = new ArrayList<>(List.of("a", "b"));
List<List<String>> oneList = List.of(existing);
~~~

`oneList` có một phần tử là list `existing`; `of` không phải thao tác “bung” mọi collection. Khi mục tiêu là tạo bản không sửa được từ các phần tử của một collection đã có, `copyOf` là họ API khác cần xem xét. Hãy xác định đúng kiểu phần tử trước khi chọn.

## 9. Chương trình hoàn chỉnh kiểm tra hợp đồng

Lưu mã vào **`CollectionFactoriesDemo.java`**. Chương trình không in trực tiếp set/map có thứ tự duyệt không được bảo đảm. Các dòng in có thứ tự dùng `TreeSet` hoặc `TreeMap` cho khóa và phần tử chuỗi; các phép kiểm tra khác dùng `size`, `get`, `contains` hoặc `equals`.

Hàm `rejects` xác nhận đúng loại ngoại lệ mong đợi. Nếu một thao tác bất ngờ thành công hoặc ném sai loại, chương trình dừng bằng `AssertionError`. Nó không dựa vào nội dung thông báo ngoại lệ vì thông báo không phải điều cần kiểm chứng ở đây.

~~~java
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class CollectionFactoriesDemo {
    private static void rejects(
            String label,
            Class<? extends RuntimeException> expected,
            Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " unexpectedly succeeded");
        } catch (RuntimeException ex) {
            if (!expected.isInstance(ex)) {
                throw new AssertionError(label + " wrong exception", ex);
            }
            System.out.println(label + "=" + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        System.out.println("empty.sizes="
                + List.of().size() + "/"
                + Set.of().size() + "/"
                + Map.of().size() + "/"
                + Map.ofEntries().size());

        List<String> levels = List.of("low", "high", "low");
        System.out.println("list=" + levels);
        System.out.println("list.index1=" + levels.get(1));
        rejects("list.add", UnsupportedOperationException.class,
                () -> levels.add("medium"));
        rejects("list.set", UnsupportedOperationException.class,
                () -> levels.set(0, "medium"));

        Set<String> colors = Set.of("red", "blue", "green");
        System.out.println("set.sorted=" + new TreeSet<>(colors));
        System.out.println("set.containsGreen=" + colors.contains("green"));
        rejects("set.add", UnsupportedOperationException.class,
                () -> colors.add("black"));
        System.out.println("set.hashCollision.size="
                + Set.of("Aa", "BB").size());

        Map<String, Integer> limits = Map.of("small", 2, "large", 4);
        System.out.println("map.sorted=" + new TreeMap<>(limits));
        System.out.println("map.small=" + limits.get("small"));
        System.out.println("map.missing=" + limits.get("missing"));
        System.out.println("map.sameValues.size="
                + Map.of("one", 1, "single", 1).size());
        rejects("map.put", UnsupportedOperationException.class,
                () -> limits.put("small", 9));
        rejects("map.keySet.remove", UnsupportedOperationException.class,
                () -> limits.keySet().remove("small"));
        Map.Entry<String, Integer> fromMap =
                limits.entrySet().iterator().next();
        rejects("map.entry.setValue", UnsupportedOperationException.class,
                () -> fromMap.setValue(9));

        Map<String, Integer> many = Map.ofEntries(
                Map.entry("k01", 1),
                Map.entry("k02", 2),
                Map.entry("k03", 3),
                Map.entry("k04", 4),
                Map.entry("k05", 5),
                Map.entry("k06", 6),
                Map.entry("k07", 7),
                Map.entry("k08", 8),
                Map.entry("k09", 9),
                Map.entry("k10", 10),
                Map.entry("k11", 11));
        System.out.println("ofEntries.size=" + many.size());
        System.out.println("ofEntries.k11=" + many.get("k11"));

        Map.Entry<String, Integer> pair = Map.entry("limit", 3);
        rejects("entry.setValue", UnsupportedOperationException.class,
                () -> pair.setValue(9));
        AbstractMap.SimpleEntry<String, Integer> seed =
                new AbstractMap.SimpleEntry<>("limit", 3);
        Map<String, Integer> captured = Map.ofEntries(seed);
        seed.setValue(9);
        System.out.println("seed.value=" + seed.getValue());
        System.out.println("ofEntries.captured=" + captured.get("limit"));

        rejects("list.null", NullPointerException.class,
                () -> List.of("ok", (String) null));
        rejects("set.null", NullPointerException.class,
                () -> Set.of("ok", (String) null));
        rejects("map.nullKey", NullPointerException.class,
                () -> Map.of((String) null, 1));
        rejects("map.nullValue", NullPointerException.class,
                () -> Map.of("x", (Integer) null));
        rejects("entry.nullValue", NullPointerException.class,
                () -> Map.entry("x", (Integer) null));
        rejects("ofEntries.nullEntry", NullPointerException.class,
                () -> Map.<String, Integer>ofEntries(
                        (Map.Entry<String, Integer>) null));
        rejects("ofEntries.nullKey", NullPointerException.class,
                () -> Map.ofEntries(
                        new AbstractMap.SimpleEntry<String, Integer>(null, 1)));
        rejects("set.duplicate", IllegalArgumentException.class,
                () -> Set.of("red", new String("red")));
        rejects("map.duplicateKey", IllegalArgumentException.class,
                () -> Map.of("x", 1, new String("x"), 1));
        rejects("ofEntries.duplicateKey", IllegalArgumentException.class,
                () -> Map.ofEntries(Map.entry("x", 1), Map.entry("x", 2)));

        String[] codes = {"a", "b"};
        List<String> expanded = List.of(codes);
        Set<String> expandedSet = Set.of(codes);
        List<String[]> oneArray = List.<String[]>of(codes);
        codes[0] = "changed";
        System.out.println("array.expandedList=" + expanded);
        System.out.println("array.expandedSet.sorted="
                + new TreeSet<>(expandedSet));
        System.out.println("array.singleElement.size=" + oneArray.size());
        System.out.println("array.singleElement.value="
                + Arrays.toString(oneArray.get(0)));
        List<int[]> primitiveArray = List.of(new int[]{3, 4});
        System.out.println("primitive.size=" + primitiveArray.size());
        System.out.println("primitive.first.length="
                + primitiveArray.get(0).length);

        System.out.println("list.eleven.size="
                + List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11).size());
        System.out.println("set.eleven.size="
                + Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11).size());

        List<String> steps = new ArrayList<>(List.of("draft"));
        Map<String, List<String>> nested = Map.of("steps", steps);
        steps.add("published");
        System.out.println("nested.value=" + nested.get("steps"));
        rejects("nested.map.put", UnsupportedOperationException.class,
                () -> nested.put("steps", List.of("other")));

        List<String> editable = new ArrayList<>(levels);
        editable.add("medium");
        System.out.println("editable=" + editable);
        System.out.println("original=" + levels);
        System.out.println("list.valueEquality="
                + levels.equals(List.of("low", "high", "low")));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 CollectionFactoriesDemo.java
java CollectionFactoriesDemo
~~~

Cần JDK 17 trở lên để biên dịch với lệnh này. `--release 17` giới hạn cú pháp và API ở mức 17. Mã đã được biên dịch theo lệnh ấy và chạy trên OpenJDK 24.0.2, thoát thành công với stdout chính xác sau:

~~~text
empty.sizes=0/0/0/0
list=[low, high, low]
list.index1=high
list.add=UnsupportedOperationException
list.set=UnsupportedOperationException
set.sorted=[blue, green, red]
set.containsGreen=true
set.add=UnsupportedOperationException
set.hashCollision.size=2
map.sorted={large=4, small=2}
map.small=2
map.missing=null
map.sameValues.size=2
map.put=UnsupportedOperationException
map.keySet.remove=UnsupportedOperationException
map.entry.setValue=UnsupportedOperationException
ofEntries.size=11
ofEntries.k11=11
entry.setValue=UnsupportedOperationException
seed.value=9
ofEntries.captured=3
list.null=NullPointerException
set.null=NullPointerException
map.nullKey=NullPointerException
map.nullValue=NullPointerException
entry.nullValue=NullPointerException
ofEntries.nullEntry=NullPointerException
ofEntries.nullKey=NullPointerException
set.duplicate=IllegalArgumentException
map.duplicateKey=IllegalArgumentException
ofEntries.duplicateKey=IllegalArgumentException
array.expandedList=[a, b]
array.expandedSet.sorted=[a, b]
array.singleElement.size=1
array.singleElement.value=[changed, b]
primitive.size=1
primitive.first.length=2
list.eleven.size=11
set.eleven.size=11
nested.value=[draft, published]
nested.map.put=UnsupportedOperationException
editable=[low, high, low, medium]
original=[low, high, low]
list.valueEquality=true
~~~

### Những dòng nào chứng minh điều gì?

**`empty.sizes`** xác nhận cả bốn factory dùng được cho dữ liệu rỗng. Không cần tạo rồi làm rỗng một vật chứa sửa được để có kết quả rỗng.

**Nhóm `list`** xác nhận list giữ thứ tự và phần tử lặp, có truy cập theo chỉ số nhưng không cho thêm hoặc thay phần tử.

**Nhóm `set`** dùng biểu diễn sắp xếp riêng để stdout ổn định. `set.hashCollision.size=2` cho thấy đụng mã băm không tự biến hai chuỗi khác nhau thành phần tử trùng.

**Nhóm `map`** xác nhận ánh xạ, giá trị lặp hợp lệ và việc khóa vắng trả `null`. Cả `put`, xóa qua `keySet` và `setValue` trên entry của map đều không mở một đường sửa kết quả.

**Nhóm `ofEntries`** tạo 11 ánh xạ. Giá trị cuối được truy vấn bằng khóa nên không phụ thuộc khóa ấy nằm ở đâu trong lần duyệt.

**`seed.value` và `ofEntries.captured`** tách việc sửa entry nguồn khỏi map đã tạo. Dữ liệu ở đây là `String` và `Integer`; không có đối tượng giá trị sửa được lồng bên trong làm kết quả bị nhập nhằng.

**Các dòng ngoại lệ** kiểm tra từng vi phạm riêng. Hai chuỗi khác tham chiếu nhưng bằng nhau vẫn tạo lỗi trùng. Khóa trùng bị từ chối cả khi giá trị giống nhau và khi giá trị khác nhau.

**Nhóm `array`** xác nhận hai cách hiểu khác nhau của mảng: lấy các chuỗi thành nhiều phần tử, hoặc giữ chính mảng làm một phần tử. Chỉ trường hợp sau tiếp tục quan sát phép gán vào mảng sau tạo.

**Hai dòng `eleven`** ngăn nhầm giới hạn của `Map.of` sang `List.of` hoặc `Set.of`. Cả hai collection đều tạo được 11 phần tử.

**Nhóm `nested`** xác nhận map không sửa được vẫn có thể chứa list sửa được. Sửa list nguồn thể hiện qua cùng đối tượng giá trị; `put` trên map vẫn bị chặn.

**Các dòng cuối** cho thấy khi cần chỉnh sửa, tạo `ArrayList` mới sẽ cho một bản làm việc riêng ở tầng list. `equals` kiểm tra bình đẳng giá trị của hai list; chương trình không dự đoán hai factory có trả cùng đối tượng hay không.

## 10. “Không sửa được” có những giới hạn nào?

### Không làm bất biến sâu các đối tượng bên trong

~~~java
List<StringBuilder> notes =
        List.of(new StringBuilder("draft"));

notes.get(0).append("-edited");
~~~

Đây là mã minh họa hợp lệ. List vẫn giữ cùng một phần tử ở cùng vị trí, còn phần tử `StringBuilder` đổi trạng thái.

Với map, `map.get(key).add(...)` có thể sửa một list giá trị dù `map.put(...)` bị chặn. Với set hay khóa map, sửa dữ liệu tham gia `equals`/`hashCode` còn có thể phá giả định tra cứu.

Nếu mục tiêu là dữ liệu cấu hình ổn định, dùng kiểu lá bất biến hoặc chuyển dữ liệu sửa được sang biểu diễn đã chốt trước khi đưa vào factory. Các phương thức tạo không tự duyệt mọi tầng để sao chép đối tượng.

Tài liệu về collection không sửa được phân biệt quyền sửa vật chứa với trạng thái các phần tử và với view trên nguồn. [Định nghĩa trong Collection, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#unmodcoll).

### Không bảo đảm một lớp triển khai hoặc một định danh đối tượng

Không ép kiểu kết quả `List.of` sang `ArrayList`, không kiểm tra tên lớp nội bộ và không dùng nó làm điều kiện nghiệp vụ.

Các kết quả này có ngữ nghĩa dựa trên giá trị (value-based). Hãy dùng `equals` để so nội dung theo hợp đồng của list/set/map. Không dựa vào `==`, `System.identityHashCode` hay việc factory “luôn trả đối tượng mới”.

Cũng không dùng kết quả factory làm đối tượng khóa đồng bộ. Nếu cần một khóa, tạo đối tượng khóa riêng có quyền sở hữu rõ ràng. Bảo đảm của dữ liệu không phải một cơ chế cấp phát khóa cho chương trình.

### Không biến khởi tạo dữ liệu thành một giao dịch đồng thời

Nếu các đối số là tham chiếu tới đối tượng đang được luồng khác sửa, factory vẫn chỉ nhận những tham chiếu ấy. Nếu dữ liệu được thu thập qua nhiều lần đọc, những lần đọc đó không tự trở thành một bản chụp nguyên tử chỉ vì bước cuối gọi `ofEntries`.

Dùng dữ liệu đã ổn định hoặc một giao thức đọc phù hợp với nguồn. Phần trọng tâm của các factory là tạo cấu trúc với hợp đồng rõ, không cung cấp cơ chế điều phối các luồng.

### Không làm dữ liệu trở thành hằng số biên dịch

Một trường `static final List<String>` có thể giữ một list do factory tạo, nhưng đối tượng list vẫn được khởi tạo khi chạy. Đây không phải một “collection literal” mới trong cú pháp ngôn ngữ và cũng không phải hằng số chuỗi/số được trình biên dịch xử lý như constant expression.

`final` chỉ ngăn gán lại biến sau khởi tạo. Quyền sửa vật chứa đến từ factory; khả năng bất biến sâu còn phụ thuộc dữ liệu bên trong.

## 11. Các lựa chọn thực tế và lỗi chuyển đổi thường gặp

### Tạo dữ liệu khai báo đã biết

Các factory phù hợp cho dữ liệu như danh sách chế độ, tập mã được hỗ trợ, bảng ánh xạ hằng và dữ liệu đầu vào cố định của thí nghiệm. Chúng thể hiện ngay ý định không sửa vật chứa và phát hiện một số sai sót ngay lúc xây dựng. [Hướng dẫn sử dụng factory của Oracle](https://docs.oracle.com/en/java/javase/17/core/creating-immutable-lists-sets-and-maps.html).

Ví dụ một tập mã hợp lệ nên từ chối việc khai báo nhầm hai lần cùng mã; một danh sách bước xử lý có thể cần giữ phần tử lặp. Hợp đồng dữ liệu quyết định nên dùng set hay list.

### Cần cập nhật sau đó

~~~java
List<String> work = new ArrayList<>(List.of("draft", "review"));
work.add("publish");
~~~

Bản `ArrayList` hỗ trợ chỉnh sửa. Với map, có thể xây dựng một map sửa được từ dữ liệu gốc khi yêu cầu cho phép cập nhật.

Đừng thay các đoạn đang dùng `ArrayList`/`HashMap` bằng `of` chỉ vì cú pháp ngắn hơn mà bỏ qua các lần cập nhật phía sau. Các lần gọi `add`, `sort`, `removeIf`, `put`, `compute` hoặc cập nhật qua view đều cần được đánh giá theo hợp đồng mới.

Nếu muốn dữ liệu thay đổi theo phiên bản mà không sửa bản cũ, hãy tạo giá trị mới và thay tham chiếu sở hữu theo cách phù hợp. Không bắt `UnsupportedOperationException` rồi âm thầm coi thao tác cập nhật đã thành công.

### Cần thứ tự cho set hoặc map

Nếu dữ liệu phải hiển thị theo thứ tự khai báo, đừng khởi đầu bằng `Map.of` rồi hi vọng `new LinkedHashMap<>(thatMap)` khôi phục thứ tự các đối số. Bản sao chỉ có thể giữ thứ tự mà nó quan sát từ map đầu vào, không biết thứ tự các đối số trong mã nguồn.

Cách giữ thứ tự chèn phải được xây dựng từ đầu bằng các lần chèn có thứ tự vào một `LinkedHashMap`, rồi áp dụng quyền đọc phù hợp nếu cần. [Hợp đồng thứ tự của LinkedHashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

Nếu yêu cầu là thứ tự khóa, sắp xếp theo khóa một cách rõ ràng. Nếu yêu cầu là một chuỗi bước, dùng list. Ba yêu cầu “có cùng ánh xạ”, “giữ thứ tự chèn” và “sắp xếp theo khóa” không thay thế nhau.

### Cần chuyển dữ liệu đã có

`List.of(existingList)` giữ list ấy làm một phần tử. `Set.of(existingSet)` cũng không tự chuyển thành tập các thành viên của set đầu vào. Họ `copyOf` dành cho một nhu cầu khác: lấy dữ liệu từ collection/map đã tồn tại để tạo kết quả không sửa được.

Đừng suy ra mọi ràng buộc của `copyOf` từ `of`. Chẳng hạn, một API chuyển đổi tập hợp có thể có cách xử lý phần tử trùng khác với một factory khai báo set từ nhiều đối số. Khi chuyển đổi dữ liệu, đọc đúng hợp đồng phương thức được gọi.

### Cần hiểu lỗi ngay khi chương trình khởi tạo

Một bảng `static final` có khóa trùng hoặc `null` có thể làm việc khởi tạo lớp thất bại trước khi phần xử lý nghiệp vụ chạy. Khi đó cần đọc nguyên nhân bên trong lỗi khởi tạo để tìm đúng factory và cặp dữ liệu lỗi.

Đừng chỉ nhìn một lỗi bên ngoài rồi kết luận JVM hoặc thư viện bị hỏng. Một bảng tra cứu được kiểm tra ở bước tạo chính là nơi nên phát hiện dữ liệu khai báo không hợp lệ.

## 12. Thực hành có đáp án

### Bài 1 — Giữ một lần lặp hay báo lỗi?

Xét hai lời gọi:

~~~java
List<String> attempts = List.of("read", "retry", "retry");
Set<String> actions = Set.of("read", "retry", "retry");
~~~

**Câu hỏi:** Lời gọi nào thành công? Nếu mục tiêu là mô tả lần lượt những gì đã xảy ra, chọn cấu trúc nào?

**Đáp án:** List thành công và có 3 phần tử theo thứ tự. Set ném `IllegalArgumentException` vì `"retry"` bị khai báo trùng. Nhật ký diễn biến cần giữ thứ tự và số lần xuất hiện nên phù hợp với list; dùng set sẽ làm mất ý nghĩa ngay cả khi một quy trình khác cho phép tự loại trùng.

### Bài 2 — Có cùng giá trị không có nghĩa là trùng khóa

~~~java
Map<String, Integer> a = Map.of("small", 1, "tiny", 1);
Map<String, Integer> b = Map.of("small", 1, "small", 1);
~~~

**Câu hỏi:** Map nào hợp lệ? Có phải map thứ hai chọn một trong hai cặp không?

**Đáp án:** `a` hợp lệ với 2 ánh xạ. `b` không được tạo mà ném `IllegalArgumentException`. `Map.of` kiểm tra trùng khóa; nó không dùng quy tắc “giá trị giống nhau thì bỏ qua cặp lặp” và không chọn một cặp làm kết quả.

### Bài 3 — Có 11 ánh xạ thì viết thế nào?

Một bảng cần ánh xạ từ `"A01"` đến `"A11"` sang số tương ứng.

**Câu hỏi:** Có thể gọi `Map.of` với 11 cặp không? Dùng factory nào trong phạm vi bài để khai báo đủ?

**Đáp án:** `Map.of` không có overload nhận 11 cặp; đây là vấn đề khớp phương thức khi biên dịch, không phải lỗi kích thước lúc chạy. Dùng `Map.ofEntries` và 11 lời gọi `Map.entry`. Không chia thành hai map rồi tưởng rằng chúng tự được nối. `List.of` và `Set.of` không có cùng giới hạn 10 vì có dạng varargs phần tử.

### Bài 4 — Mảng được đọc thành phần tử hay giữ nguyên?

~~~java
String[] values = {"x", "y"};
List<String> a = List.of(values);
List<String[]> b = List.<String[]>of(values);
values[0] = "X";
~~~

**Câu hỏi:** Kích thước và dữ liệu nhìn thấy của mỗi list là gì?

**Đáp án:** `a` có kích thước 2, vẫn chứa `"x"` và `"y"`. `b` có kích thước 1; phần tử ấy là cùng mảng, nên `b.get(0)[0]` là `"X"`. Đừng in thẳng mảng rồi dự đoán chuỗi mặc định; dùng `Arrays.toString` nếu cần hiển thị các ô.

### Bài 5 — Mảng số nguyên có hai số

~~~java
var values = List.of(new int[]{8, 9});
~~~

**Câu hỏi:** Có phải đây là `List<Integer>` kích thước 2 không? Nếu muốn hai phần tử số nguyên wrapper thì viết thế nào?

**Đáp án:** Không. Đó là list có một phần tử `int[]`. Dùng `List.of(8, 9)` để có hai phần tử `Integer`. Đưa một mảng nguyên thủy làm đối số không yêu cầu Java tự bung các ô rồi boxing từng ô.

### Bài 6 — Sửa entry nguồn có cập nhật map không?

~~~java
AbstractMap.SimpleEntry<String, Integer> pair =
        new AbstractMap.SimpleEntry<>("count", 5);

Map<String, Integer> counts = Map.ofEntries(pair);
pair.setValue(6);
~~~

**Câu hỏi:** `counts.get("count")` bằng bao nhiêu? Nếu `pair` được tạo bằng `Map.entry` thì `setValue` làm gì?

**Đáp án:** Map vẫn trả 5, vì nó lấy khóa/giá trị từ entry lúc tạo và không giữ entry đó làm chỗ lưu ánh xạ. Nếu dùng `Map.entry`, `setValue` bị từ chối bằng `UnsupportedOperationException`. Trong cả hai trường hợp, không dùng entry đầu vào làm giao diện cập nhật map kết quả.

### Bài 7 — Có thể viết một stdout cố định thế này không?

Một kiểm thử in trực tiếp `Set.of("c", "a", "b")` rồi khẳng định chuỗi phải là `[c, a, b]`. Kiểm thử khác in trực tiếp `Map.of("z", 1, "a", 2)` rồi khẳng định khóa `z` phải đứng đầu.

**Câu hỏi:** Hai kiểm thử dựa vào hợp đồng nào bị thiếu? Sửa cách kiểm tra ra sao?

**Đáp án:** Các factory set/map không bảo đảm thứ tự duyệt ấy. Kiểm tra tập thành viên hoặc ánh xạ bằng `equals`/`contains`/`get` và kích thước. Nếu thực sự cần chuỗi có thứ tự, sắp xếp rõ trước khi in; dữ liệu chuỗi có thể dùng bản `TreeSet`/`TreeMap` như chương trình trong bài. Nếu thứ tự chèn là yêu cầu sản phẩm, chọn cấu trúc giữ thứ tự ngay từ lúc xây dựng.

### Bài 8 — Vì sao map “chỉ đọc” vẫn đổi dữ liệu nhìn thấy?

~~~java
List<String> modes = new ArrayList<>(List.of("fast"));
Map<String, List<String>> config = Map.of("modes", modes);
modes.add("safe");
~~~

**Câu hỏi:** `config.get("modes")` chứa gì? Lời gọi `config.put("modes", List.of("other"))` có được hỗ trợ không? Nếu cần giá trị cấu hình ổn định thì thay điểm nào?

**Đáp án:** Giá trị list biểu diễn `[fast, safe]` vì list ấy được chia sẻ. `put` trên map bị từ chối. Nếu mô hình chỉ chứa các chuỗi, chốt list giá trị bằng `List.copyOf(modes)` **tại thời điểm cần lưu** rồi đưa vào map không sửa được; nếu đã biết sẵn các chế độ, có thể dùng `Map.of("modes", List.of("fast"))`. Khi phần tử còn chứa đối tượng sửa được, phải bảo vệ cả tầng đó, không chỉ bọc map ngoài.

Một lời gọi factory đúng cần thống nhất ba điều: hình dạng dữ liệu là dãy, tập hay ánh xạ; các đối số thỏa chính sách về `null` và trùng; người dùng kết quả hiểu rõ quyền sửa cùng bảo đảm về thứ tự. Cú pháp ngắn chỉ hữu ích khi ba điều ấy đã rõ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unmodifiable-collections-005">List.copyOf, Set.copyOf và Map.copyOf</a>

<details>
<summary>Xem chi tiết</summary>

# List.copyOf, Set.copyOf và Map.copyOf

## 1. Bắt đầu từ nhu cầu chốt dữ liệu để đọc

Một phương thức nhận danh sách cấu hình từ bên ngoài và lưu lại để dùng về sau. Nếu lưu thẳng tham chiếu đầu vào, bên gọi vẫn có thể thêm, xóa hoặc thay phần tử bằng biến họ đang giữ. Nếu chỉ đưa một lớp bọc chỉ đọc ra ngoài, việc sửa qua lớp bọc bị chặn, nhưng dữ liệu bên dưới vẫn có thể thay đổi qua nguồn. Khi yêu cầu là “giữ tập dữ liệu đã nhận, không đi theo các lần sửa cấu trúc sau đó của nguồn”, cần một ranh giới mạnh hơn.

Ba phương thức tạo tĩnh (static factory) `List.copyOf`, `Set.copyOf` và `Map.copyOf` phục vụ ranh giới này. Chúng nhận một collection hoặc map đã có và trả về kết quả không cho phép sửa cấu trúc qua API của kết quả. Nguồn vẫn có thể sửa được nếu chính kiểu nguồn hỗ trợ; việc nguồn thêm/xóa/thay tham chiếu phần tử hoặc thêm/xóa/thay ánh xạ sau khi sao chép không làm thay đổi phần chứa của kết quả.

Có thể gọi kết quả là **ảnh chụp cấu trúc**: chốt những tham chiếu phần tử, hoặc những liên kết khóa–giá trị, được lấy từ đầu vào. Cách gọi này giúp tách hai câu hỏi:

- Collection kết quả chứa những phần tử nào, theo thứ tự nào nếu có?
- Các đối tượng mà collection đang tham chiếu có còn tự thay đổi được hay không?

`copyOf` giải quyết câu hỏi thứ nhất theo hợp đồng của List, Set hoặc Map. Nó không tự sao chép toàn bộ trạng thái bên trong từng đối tượng để giải quyết câu hỏi thứ hai.

Ví dụ, danh sách chứa `String` thường dễ dùng làm dữ liệu ổn định vì `String` là bất biến. Danh sách chứa `StringBuilder` vẫn có thể hiển thị văn bản khác khi một builder được sửa. Cả hai danh sách đều có thể không hỗ trợ `add`, `remove` và `set`; điểm khác biệt nằm ở đối tượng phần tử.

Bài này đi theo thứ tự: phân biệt ba hình dạng dữ liệu; hiểu quy tắc null, trùng lặp và thứ tự; kiểm chứng từng factory; sau đó xác định mức bảo vệ cần có khi dùng trong hàm khởi tạo hoặc kết quả trả về. Người học cần biết collection có thể chứa tham chiếu đến đối tượng và nắm các thao tác cơ bản của `ArrayList`, `Set`, `Map`.

## 2. Ba chữ ký, ba ý nghĩa sao chép

Các phương thức `copyOf` này có từ Java 10. Toàn bộ chương trình trong bài dùng Java 17, được lưu thành từng tệp độc lập theo tên lớp `public`. Các lệnh `javac --release 17` và `java` cần chạy bằng JDK 17 hoặc mới hơn phù hợp.

| Lời gọi | Đầu vào | Kiểu kết quả | Điều được giữ |
| --- | --- | --- | --- |
| `List.copyOf(collection)` | `Collection<? extends E>` | `List<E>` | Phần tử theo thứ tự duyệt đầu vào, gồm cả phần tử lặp |
| `Set.copyOf(collection)` | `Collection<? extends E>` | `Set<E>` | Mỗi giá trị phân biệt theo ngữ nghĩa tập hợp, không có bảo đảm thứ tự duyệt |
| `Map.copyOf(map)` | `Map<? extends K, ? extends V>` | `Map<K, V>` | Các ánh xạ khóa–giá trị, không có bảo đảm thứ tự duyệt |

`List.copyOf` không chỉ nhận một List: một Set cũng là Collection. Tuy vậy, kết quả List chỉ chốt thứ tự mà nguồn đã cung cấp lúc duyệt. Nó không tự biến một nguồn không có thứ tự được bảo đảm thành quy tắc sắp xếp có ý nghĩa cho nghiệp vụ.

`Set.copyOf` cũng nhận được List. Đây là điểm hữu ích khi cần chuyển từ dữ liệu có thể lặp sang một tập hợp chỉ đọc. Việc loại trùng là một lựa chọn mô hình dữ liệu: số lần xuất hiện không còn được biểu diễn trong kết quả. Nếu tần suất quan trọng, không dùng Set để thay cho danh sách đầu vào.

`Map.copyOf` nhận Map vì nó cần quan hệ giữa khóa và giá trị. Một danh sách các giá trị riêng lẻ không đủ thông tin để suy ra khóa; một danh sách các cặp có thể lặp khóa còn cần quy tắc xử lý xung đột trước khi tạo Map.

Phân biệt `of` và `copyOf`: `List.of("A", "B")` tạo dữ liệu từ các đối số phần tử; `List.copyOf(existing)` lấy phần tử từ một Collection đã có. Nếu `existing` là một List, `List.of(existing)` tạo danh sách có một phần tử là chính List đó, không trải các phần tử bên trong. Không thay hai lời gọi cho nhau chỉ vì chúng cùng trả về List chỉ đọc.

Tài liệu API gốc mô tả chữ ký và phiên bản của từng phương thức: [List.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)), [Set.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#copyOf(java.util.Collection)), [Map.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#copyOf(java.util.Map)).

## 3. List.copyOf: giữ thứ tự, giữ phần tử lặp, chặn thay thế

Đối với danh sách, vị trí là một phần của dữ liệu. Từ nguồn có thứ tự `[A, B, A]`, kết quả phải biểu diễn ba vị trí đó theo đúng thứ tự nguồn đã duyệt. Hai lần xuất hiện của `A` không bị gộp; `size()` vẫn bằng ba.

Sau khi có kết quả, ba loại sửa đều bị chặn:

- Thêm phần tử làm tăng số vị trí.
- Xóa phần tử làm giảm số vị trí.
- Thay phần tử ở một vị trí bằng `set`, dù kích thước không đổi.

Điểm cuối phân biệt danh sách không sửa được với danh sách chỉ cố định kích thước. Một List không hỗ trợ thêm/xóa chưa chắc đã cấm `set`. Đừng dùng việc `add` ném ngoại lệ làm bằng chứng rằng mọi List khác cũng có hợp đồng giống `List.copyOf`.

Giả sử `source` là `ArrayList` và đã được chép sang `snapshot`. Lời gọi `source.set(0, "Z")` thay tham chiếu mà nguồn lưu ở vị trí đầu; nó không thay tham chiếu đang được kết quả lưu ở vị trí tương ứng. Tương tự, `source.add("C")` không thêm phần tử vào kết quả. Đây là sự độc lập của cấu trúc chứa.

Nếu phần tử đầu là một đối tượng có thể sửa, lời gọi một phương thức sửa trên đối tượng đó là chuyện khác. `source.get(0).append(...)` không thay vị trí hay tham chiếu của nguồn; nó sửa chính đối tượng đang được cả hai bên giữ. Phần 9 và 10 sẽ kiểm chứng ranh giới này.

Đầu vào List không được null, và không được có phần tử null. List rỗng hợp lệ: một danh sách không chứa phần tử khác với việc không có đối tượng List nào.

## 4. Thí nghiệm List: ảnh chụp và lớp bọc chỉ đọc

Lưu chương trình thành `ListCopyOfDemo.java`. Chương trình tạo một nguồn, một lớp bọc chỉ đọc của nguồn và hai ảnh chụp: một từ nguồn, một từ chính lớp bọc. Việc lấy ảnh chụp từ lớp bọc vẫn phải cho kết quả độc lập với thay đổi cấu trúc về sau của dữ liệu bên dưới.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListCopyOfDemo {
    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("A", "B", "A"));
        List<String> view = Collections.unmodifiableList(source);
        List<String> snapshot = List.copyOf(source);
        List<String> snapshotFromView = List.copyOf(view);

        source.set(0, "Z");
        source.add("C");

        System.out.println("source=" + source);
        System.out.println("view=" + view);
        System.out.println("snapshot=" + snapshot);
        System.out.println("snapshotFromView=" + snapshotFromView);
        System.out.println("emptySize=" + List.copyOf(List.of()).size());

        try {
            snapshot.set(0, "X");
        } catch (UnsupportedOperationException e) {
            System.out.println("set=UnsupportedOperationException");
        }

        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        try {
            List.copyOf(withNull);
        } catch (NullPointerException e) {
            System.out.println("nullElement=NullPointerException");
        }

        try {
            List.<String>copyOf(null);
        } catch (NullPointerException e) {
            System.out.println("nullSource=NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ListCopyOfDemo.java
java ListCopyOfDemo
```

Stdout:

```text
source=[Z, B, A, C]
view=[Z, B, A, C]
snapshot=[A, B, A]
snapshotFromView=[A, B, A]
emptySize=0
set=UnsupportedOperationException
nullElement=NullPointerException
nullSource=NullPointerException
```

Đọc kết quả theo ba thời điểm. Ban đầu, cả nguồn và các kết quả đều nhìn thấy `A, B, A`. Hai lời gọi sửa được thực hiện trên `source` sau khi cả hai ảnh chụp đã tạo xong. Khi in, nguồn và `view` cùng hiện `Z, B, A, C`, còn hai ảnh chụp vẫn hiện `A, B, A`.

Dòng `snapshotFromView` tránh một hiểu nhầm thường gặp: “đầu vào đã chỉ đọc thì cứ trả lại nó”. Một lớp bọc đang đọc xuyên qua nguồn có thể tiếp tục thay đổi phần nội dung quan sát được. Kết quả của `copyOf` phải đáp ứng hợp đồng chốt cấu trúc, dù đầu vào được truyền qua biến có giao diện chỉ đọc.

Lời gọi `snapshot.set(0, "X")` bị chặn ngay cả khi không làm thay đổi kích thước. Hai ca null được chuẩn bị riêng để xác định đúng nơi lỗi xảy ra: `ArrayList` trong ví dụ cho phép thêm null; ngoại lệ được quan sát ở `List.copyOf(withNull)`. Nếu dùng một factory vốn đã cấm null để chuẩn bị dữ liệu, chương trình có thể lỗi trước khi thực sự đi đến `copyOf`.

Phần bắt ngoại lệ chỉ giúp thí nghiệm chạy tiếp để quan sát nhiều ca. Trong thiết kế API thật, nên công bố điều kiện đầu vào và xử lý dữ liệu không hợp lệ ở ranh giới thích hợp; không dùng ngoại lệ như cách suy đoán ngẫu nhiên khả năng của mọi List.

## 5. Set.copyOf: loại trùng là thay đổi ý nghĩa dữ liệu

Một Set trả lời câu hỏi “giá trị này có xuất hiện không?”, không lưu số lần xuất hiện như List. Nếu nguồn có `beta, alpha, beta`, tập hợp kết quả có hai giá trị phân biệt. Đây là hành vi bình thường của `Set.copyOf`, không phải lỗi dữ liệu trùng.

Cần phân biệt rõ với `Set.of`. Truyền hai phần tử bằng nhau vào `Set.of` gây `IllegalArgumentException`, còn `Set.copyOf` nhận Collection có phần tử trùng và giữ lại một đại diện trong nhóm các phần tử bằng nhau. Không suy từ tên “factory chỉ đọc” rằng mọi factory xử lý đầu vào trùng theo cùng một cách.

Nếu hai đối tượng được `equals` xem là bằng nhau nhưng chứa thêm thông tin khác, không dựa vào việc đại diện giữ lại là đối tượng đầu tiên hay cuối cùng. Ví dụ, hai đối tượng mã hàng cùng mã định danh nhưng khác phần mô tả có thể được xem là bằng nhau theo quy tắc lớp. Nếu cần chọn mô tả mới nhất, phải xử lý quy tắc đó trước; `Set.copyOf` không là công cụ chọn bản ghi thắng theo thời gian.

Kết quả không cam kết thứ tự chèn hay thứ tự sắp xếp của nguồn. Một nguồn `LinkedHashSet` có thứ tự riêng hoặc `TreeSet` có bộ so sánh riêng không biến kết quả `Set.copyOf` thành loại tập hợp chuyên biệt tương ứng.

Trong thí nghiệm tiếp theo, việc in thông qua `new TreeSet<>(snapshot)` chỉ tạo một cách trình bày theo thứ tự chữ để stdout dễ kiểm tra. Nó không chứng minh `snapshot` tự có thứ tự đó. Khi so sánh tập hợp trong kiểm tra chương trình, hãy kiểm tra phần tử và quan hệ bằng nhau; chỉ kiểm tra thứ tự khi API đã cam kết thứ tự.

## 6. Thí nghiệm Set: loại trùng và quy tắc đầu vào

Lưu thành `SetCopyOfDemo.java`. Nguồn được cố ý chọn là List để số lần lặp còn quan sát được trước khi chuyển sang Set.

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class SetCopyOfDemo {
    public static void main(String[] args) {
        List<String> source =
                new ArrayList<>(List.of("beta", "alpha", "beta"));
        Set<String> snapshot = Set.copyOf(source);

        System.out.println("sourceSize=" + source.size());
        System.out.println("snapshotSize=" + snapshot.size());
        System.out.println("snapshotForDisplay=" + new TreeSet<>(snapshot));

        source.clear();
        source.add("gamma");
        System.out.println("sourceNow=" + source);
        System.out.println("snapshotAfterChange=" + new TreeSet<>(snapshot));

        try {
            snapshot.add("delta");
        } catch (UnsupportedOperationException e) {
            System.out.println("add=UnsupportedOperationException");
        }

        try {
            Set.of("beta", "beta");
        } catch (IllegalArgumentException e) {
            System.out.println("ofDuplicate=IllegalArgumentException");
        }

        List<String> withNull = new ArrayList<>();
        withNull.add("alpha");
        withNull.add(null);
        try {
            Set.copyOf(withNull);
        } catch (NullPointerException e) {
            System.out.println("nullElement=NullPointerException");
        }

        try {
            Set.<String>copyOf(null);
        } catch (NullPointerException e) {
            System.out.println("nullSource=NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SetCopyOfDemo.java
java SetCopyOfDemo
```

Stdout:

```text
sourceSize=3
snapshotSize=2
snapshotForDisplay=[alpha, beta]
sourceNow=[gamma]
snapshotAfterChange=[alpha, beta]
add=UnsupportedOperationException
ofDuplicate=IllegalArgumentException
nullElement=NullPointerException
nullSource=NullPointerException
```

`sourceSize=3` và `snapshotSize=2` là bằng chứng về việc đổi hình dạng dữ liệu. Sau `source.clear()` và `source.add("gamma")`, kết quả vẫn chứa `alpha` và `beta`; việc nguồn đổi không làm tập kết quả đi theo.

Lời gọi `snapshot.add("delta")` cố thêm một giá trị mới nên minh họa trực tiếp việc chặn sửa. Ca `Set.of("beta", "beta")` được tách riêng và in tên khác để tránh gán ngoại lệ của `of` cho `copyOf`.

Với dữ liệu null, Set không có chính sách “bỏ qua phần tử không hợp lệ”. Cả null trong Collection và null thay cho chính Collection đều gây lỗi. Nếu yêu cầu nghiệp vụ là loại bỏ null, đó phải là bước chuẩn hóa có chủ đích trước khi gọi factory, và cần xác định liệu loại bỏ có làm mất thông tin quan trọng hay không.

Phần in đã dùng cách sắp xếp riêng. Không bỏ `TreeSet` rồi tiếp tục đòi stdout thô của Set phải đúng `[alpha, beta]` trên mọi lần chạy hoặc mọi JDK.

## 7. Map.copyOf: chốt liên kết khóa–giá trị

Đối với Map, phải bảo vệ cả tập khóa lẫn giá trị được gắn với từng khóa. Nếu nguồn ban đầu chứa `alpha -> 1` và `beta -> 2`, rồi nguồn đổi `beta` sang `20`, kết quả đã tạo vẫn gắn `beta` với `2`. Một khóa mới được thêm vào nguồn cũng không tự xuất hiện trong kết quả.

Không thể sửa Map kết quả bằng `put`, `remove`, `replace` hoặc các phương thức thay đổi ánh xạ khác. Các đường đi gián tiếp qua `keySet`, `values`, `entrySet` cũng không là lối vòng để thay đổi Map. Trong thí nghiệm, việc xóa khóa qua `keySet` và gọi `setValue` trên một Entry đều bị chặn.

Cần đọc chính xác “không sửa giá trị qua Map”. Nếu một giá trị là List, việc thay List được gắn với khóa là thay ánh xạ của Map. Gọi `add` trên List đã lấy ra lại là sửa đối tượng giá trị. Hai thao tác này thuộc hai đối tượng khác nhau; Map chỉ đọc không tự biến List bên trong thành List chỉ đọc.

Map kết quả không nhận khóa null hoặc giá trị null. Quy tắc của nguồn có thể rộng hơn: một `HashMap` dùng làm nguồn vẫn có thể cho phép các dữ liệu ấy, nhưng không vì thế mà việc chuyển sang `Map.copyOf` hợp lệ.

`Map.copyOf` cũng không mang theo thứ tự duyệt của `LinkedHashMap` hay bộ so sánh của `TreeMap` như một cam kết của kết quả. Nếu báo cáo cần thứ tự khóa, hãy xây dựng rõ một bước trình bày theo thứ tự. Nếu chính API cần các thao tác của `SortedMap` hoặc `NavigableMap`, kiểu `Map` trả về từ factory không thay thế được hợp đồng đó.

Với nguồn Map thông thường có quy tắc khóa nhất quán với `equals`, mỗi khóa đã có tối đa một ánh xạ. Khi đưa dữ liệu thô có nhiều bản ghi cùng khóa vào Map, quyết định giữ bản nào xảy ra ở bước xây dựng Map nguồn. `Map.copyOf` không nhận một hàm gộp và không tự suy ra chính sách “mới nhất thắng”.

## 8. Thí nghiệm Map: nguồn thay đổi, kết quả giữ ánh xạ cũ

Lưu thành `MapCopyOfDemo.java`. `TreeMap` trong các dòng in chỉ được dùng để định thứ tự hiển thị; nó không là kiểu thực tế được yêu cầu từ `Map.copyOf`.

```java
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class MapCopyOfDemo {
    public static void main(String[] args) {
        Map<String, Integer> source = new LinkedHashMap<>();
        source.put("beta", 2);
        source.put("alpha", 1);
        Map<String, Integer> snapshot = Map.copyOf(source);

        source.put("beta", 20);
        source.put("gamma", 3);
        System.out.println("sourceForDisplay=" + new TreeMap<>(source));
        System.out.println("snapshotForDisplay=" + new TreeMap<>(snapshot));
        System.out.println("oldBeta=" + snapshot.get("beta"));

        try {
            snapshot.put("delta", 4);
        } catch (UnsupportedOperationException e) {
            System.out.println("put=UnsupportedOperationException");
        }

        try {
            snapshot.keySet().remove("alpha");
        } catch (UnsupportedOperationException e) {
            System.out.println("keySetRemove=UnsupportedOperationException");
        }

        try {
            snapshot.entrySet().iterator().next().setValue(99);
        } catch (UnsupportedOperationException e) {
            System.out.println("entrySetValue=UnsupportedOperationException");
        }

        Map<String, Integer> nullKey = new HashMap<>();
        nullKey.put(null, 1);
        try {
            Map.copyOf(nullKey);
        } catch (NullPointerException e) {
            System.out.println("nullKey=NullPointerException");
        }

        Map<String, Integer> nullValue = new HashMap<>();
        nullValue.put("alpha", null);
        try {
            Map.copyOf(nullValue);
        } catch (NullPointerException e) {
            System.out.println("nullValue=NullPointerException");
        }

        try {
            Map.<String, Integer>copyOf(null);
        } catch (NullPointerException e) {
            System.out.println("nullSource=NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapCopyOfDemo.java
java MapCopyOfDemo
```

Stdout:

```text
sourceForDisplay={alpha=1, beta=20, gamma=3}
snapshotForDisplay={alpha=1, beta=2}
oldBeta=2
put=UnsupportedOperationException
keySetRemove=UnsupportedOperationException
entrySetValue=UnsupportedOperationException
nullKey=NullPointerException
nullValue=NullPointerException
nullSource=NullPointerException
```

Sau khi nguồn thay giá trị của `beta` và thêm `gamma`, bản trình bày nguồn hiện `beta=20` cùng khóa mới. Bản trình bày kết quả vẫn hiện `beta=2` và không có `gamma`. Dòng `oldBeta=2` kiểm tra trực tiếp ánh xạ nên không phụ thuộc thứ tự duyệt.

Ba ca sửa đi qua ba đường khác nhau:

| Đường gọi | Đối tượng nhận thao tác | Điều bị yêu cầu thay đổi |
| --- | --- | --- |
| `snapshot.put(...)` | Map kết quả | Thêm một ánh xạ |
| `snapshot.keySet().remove(...)` | View tập khóa | Xóa ánh xạ mang khóa tương ứng |
| `snapshot.entrySet().iterator().next().setValue(...)` | Entry lấy từ kết quả | Thay giá trị trong một ánh xạ |

Cả ba đều bị từ chối. Ca Entry không cần dự đoán khóa nào xuất hiện đầu tiên: tất cả Entry được lấy từ kết quả này đều không cho phép thay giá trị qua `setValue`.

Hai nguồn có null được tạo riêng bằng `HashMap`, mỗi nguồn chỉ kiểm tra một loại vi phạm. Bằng cách đó, `nullKey` và `nullValue` trong stdout chỉ ra đúng điều kiện đang được kiểm tra. Ca null cho cả Map là điều kiện thứ ba, độc lập với khóa và giá trị.

Đọc một Map chỉ đọc vẫn có thể trả ra đối tượng có thể sửa. Trong ví dụ này các khóa là `String`, giá trị là `Integer`, nên không có phép sửa trạng thái bên trong chúng như với một List giá trị. Cần xem kiểu dữ liệu thực tế trước khi kết luận về mức độ bất biến.

## 9. Giới hạn sao chép nông và quyền sở hữu dữ liệu

“Sao chép nông” nghĩa là tạo ranh giới ở phần chứa nhưng vẫn giữ những tham chiếu đối tượng đã lấy từ nguồn. Ba factory không biết cách tạo một bản sao độc lập cho mọi lớp do ứng dụng định nghĩa. Không có một quy tắc chung đúng cho mọi đồ thị đối tượng, kết nối tài nguyên hoặc mối liên hệ giữa các đối tượng.

Có thể hình dung một List có hai tầng:

1. Tầng danh sách: các vị trí và tham chiếu được lưu ở từng vị trí.
2. Tầng phần tử: trạng thái bên trong các đối tượng mà các tham chiếu trỏ đến.

`List.copyOf` bảo vệ tầng danh sách trong kết quả. Để bảo vệ tầng phần tử, cần đối tượng bất biến hoặc một chính sách chuyển đổi/sao chép phù hợp. Nếu phần tử lại là collection, phải quyết định bảo vệ thêm tầng đó.

Map có thêm sự phân biệt giữa khóa và giá trị. `Map.copyOf` chốt các cặp tham chiếu khóa–giá trị. Nó không gọi một thao tác “sao chép sâu” lên từng khóa hay giá trị. Một `Map<String, List<String>>` có thể không cho phép đổi List gắn với khóa nhưng vẫn cho phép sửa List đang được gắn, nếu List ấy vốn là `ArrayList`.

Với Set và khóa Map, đối tượng có thể sửa còn liên quan đến ngữ nghĩa nhận diện. Nếu thay đổi trạng thái tham gia `equals` hoặc `hashCode` sau khi đưa vào tập hợp hoặc làm khóa, có thể vi phạm điều kiện sử dụng của cấu trúc. Không dùng việc “collection ngoài chỉ đọc” để suy ra rằng sửa các trường ấy là an toàn. Chọn khóa và phần tử có ngữ nghĩa bằng nhau ổn định trong suốt thời gian sử dụng.

Không phải lúc nào cũng cần sao chép sâu. Một cấu hình gồm `Map<String, List<String>>` có thể được bảo vệ đủ bằng cách chụp từng List bên trong rồi chụp Map bên ngoài, vì String không có trạng thái sửa được. Với `Map<String, List<Customer>>`, cách đó vẫn giữ cùng các Customer. Phải xác định chính xác tầng dữ liệu nào cần độc lập.

“Quyền sở hữu” trong ngữ cảnh này là quy ước ai được giữ và sửa dữ liệu nào. `copyOf` giúp một đối tượng tiếp nhận collection đầu vào mà không tiếp tục trao quyền sửa danh sách hay ánh xạ nội bộ cho bên truyền vào. Nó không tự thu hồi mọi tham chiếu đến các đối tượng phần tử mà bên ngoài đã giữ.

## 10. Thí nghiệm quyền sở hữu: phần tử chung, collection lồng và hàm khởi tạo

Lưu thành `CopyOfOwnershipDemo.java`. Chương trình gồm ba ca độc lập:

- List chứa builder cho thấy tham chiếu phần tử vẫn dùng chung.
- Map chứa List cho thấy khác biệt giữa chụp Map ngoài và chụp thêm các List trong.
- `Catalog` tiếp nhận List tên để giữ cấu hình riêng, không đi theo việc bên gọi thêm tên vào đầu vào.

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CopyOfOwnershipDemo {
    static final class Catalog {
        private final List<String> names;

        Catalog(Collection<String> input) {
            names = List.copyOf(input);
        }

        List<String> names() {
            return names;
        }
    }

    public static void main(String[] args) {
        List<StringBuilder> source =
                new ArrayList<>(List.of(new StringBuilder("draft")));
        List<StringBuilder> shallow = List.copyOf(source);
        source.get(0).append("-edited");
        System.out.println("sameElement=" + (source.get(0) == shallow.get(0)));
        System.out.println("shallowText=" + shallow.get(0));

        List<String> permissions = new ArrayList<>(List.of("read"));
        Map<String, List<String>> sourceMap = new HashMap<>();
        sourceMap.put("admin", permissions);
        Map<String, List<String>> outerOnly = Map.copyOf(sourceMap);

        Map<String, List<String>> staged = new HashMap<>();
        sourceMap.forEach((key, value) -> staged.put(key, List.copyOf(value)));
        Map<String, List<String>> nestedSnapshot = Map.copyOf(staged);

        permissions.add("write");
        System.out.println("outerOnly=" + outerOnly.get("admin"));
        System.out.println("nestedSnapshot=" + nestedSnapshot.get("admin"));
        sourceMap.put("admin", new ArrayList<>(List.of("replacement")));
        System.out.println("sourceMapping=" + sourceMap.get("admin"));
        System.out.println("oldMapping=" + outerOnly.get("admin"));

        List<String> input = new ArrayList<>(List.of("Java"));
        Catalog catalog = new Catalog(input);
        input.add("SQL");
        System.out.println("catalogNames=" + catalog.names());
        try {
            catalog.names().add("HTTP");
        } catch (UnsupportedOperationException e) {
            System.out.println("catalogAdd=UnsupportedOperationException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 CopyOfOwnershipDemo.java
java CopyOfOwnershipDemo
```

Stdout:

```text
sameElement=true
shallowText=draft-edited
outerOnly=[read, write]
nestedSnapshot=[read]
sourceMapping=[replacement]
oldMapping=[read, write]
catalogNames=[Java]
catalogAdd=UnsupportedOperationException
```

Dòng `sameElement=true` so sánh hai tham chiếu phần tử, không so sánh định danh của hai collection. `shallowText=draft-edited` xuất hiện vì cả nguồn lẫn kết quả giữ cùng builder. Không có `add` hay `set` nào được thực hiện trên `shallow`.

Ở ca Map lồng, hai kết quả được tạo khi danh sách quyền mới chỉ chứa `read`. Sau đó `permissions.add("write")` sửa List cũ:

- `outerOnly` vẫn giữ tham chiếu đến List cũ ấy, nên đọc được `[read, write]`.
- `nestedSnapshot` giữ List chỉ đọc đã chụp trước đó, nên đọc được `[read]`.

Tiếp theo, nguồn thay toàn bộ List được gắn với khóa `admin` bằng một List khác chứa `replacement`. `outerOnly` không theo ánh xạ mới; nó tiếp tục giữ List cũ `[read, write]`. Hai dòng `sourceMapping` và `oldMapping` cho thấy đồng thời hai tính chất: ánh xạ đã được chốt, còn đối tượng giá trị cũ thì vẫn có thể từng bị sửa.

Biến `staged` là một Map tạm để thu các List đã chụp. Sau khi hoàn tất bước chuẩn bị, `Map.copyOf(staged)` chốt lớp ngoài. Đây là quy trình hai tầng cụ thể cho kiểu dữ liệu trong ví dụ, không phải thuật toán sao chép sâu dùng được cho mọi loại đối tượng.

Với `Catalog`, trường `names` là `final`, nhưng chính `List.copyOf(input)` mới tách danh sách nội bộ khỏi các thay đổi danh sách đầu vào. Nếu chỉ gán `names = input`, từ khóa `final` vẫn không ngăn `input.add("SQL")` sửa nội dung danh sách mà đối tượng đang giữ.

Hàm `names()` có thể trả lại trường `names` trong thiết kế này vì collection đã chốt và các phần tử String là bất biến. Không cần tạo một ảnh chụp mới ở mỗi lần đọc chỉ để chống thao tác thêm/xóa từ bên gọi. Nếu sau này thay kiểu phần tử bằng đối tượng có thể sửa, phải đánh giá lại hợp đồng trả về; giữ nguyên tên phương thức không giữ nguyên mức độ bảo vệ.

## 11. Bằng nhau về dữ liệu không đồng nghĩa với cùng đối tượng

Tên `copyOf` không hứa rằng mỗi lời gọi đều cấp phát một collection mới có định danh khác. Khi đầu vào đã đáp ứng điều kiện thích hợp, triển khai có thể tận dụng một kết quả chỉ đọc đã có. Tối ưu này không làm thay đổi hợp đồng quan sát được về dữ liệu.

Vì vậy, không thiết kế mã dựa trên hai suy luận sau:

- “Đã gọi copy thì `result != input` chắc chắn đúng.”
- “Đầu vào chỉ đọc thì `result == input` chắc chắn đúng.”

Khi muốn kiểm tra dữ liệu, dùng ngữ nghĩa phù hợp: List xét phần tử và thứ tự, Set xét tập phần tử, Map xét các ánh xạ. Khi muốn kiểm tra việc tách khỏi nguồn, sửa nguồn theo hợp đồng cho phép rồi quan sát kết quả. Hai chương trình đầu đã làm điều đó mà không cần biết tên lớp triển khai của kết quả.

Các kết quả factory là đối tượng dựa trên giá trị: không chọn chúng làm đối tượng khóa đồng bộ hóa hay dựa vào định danh để điều khiển nghiệp vụ. Dùng một đối tượng khóa riêng khi thiết kế thực sự cần đồng bộ hóa, với cơ chế quản lý rõ ràng.

Điểm này cũng giải thích tại sao không ép kiểu kết quả thành `ArrayList`, `HashSet` hoặc `HashMap`. Factory cung cấp giao diện được công bố, không cung cấp lời hứa rằng tên implementation tương ứng với tên cấu trúc nguồn. Sửa mã bằng cách bỏ ép kiểu và lập trình theo hợp đồng cần dùng; nếu cần một bản sao có thể sửa, chủ động tạo kiểu có thể sửa.

Không suy từ việc có thể tái sử dụng sang kết luận rằng `copyOf` luôn rẻ hoặc luôn không cấp phát. Với nguồn có thể sửa, việc tạo dữ liệu độc lập có thể cần duyệt nguồn và giữ các tham chiếu riêng. Dùng phép đo với dữ liệu thực tế khi chi phí sao chép ảnh hưởng đến đường chạy quan trọng; không đặt giả định định danh vào thuật toán.

## 12. Chọn công cụ theo quyền sửa và mức phụ thuộc nguồn

Trước khi chọn phương thức, mô tả hợp đồng bằng một câu cụ thể: “người nhận được sửa không?”, “nguồn sửa thì kết quả có đi theo không?”, “đối tượng phần tử có được dùng chung không?”. Một từ “bất biến” đứng riêng thường chưa đủ để trả lời cả ba.

| Nhu cầu | Hướng lựa chọn | Điều còn phải xem |
| --- | --- | --- |
| Bên nhận được sửa một List riêng | `new ArrayList<>(source)` | Phần tử vẫn có thể dùng chung |
| Bên nhận chỉ được đọc và cần thấy thay đổi từ List nguồn | `Collections.unmodifiableList(source)` | Nguồn bên dưới vẫn có thể thay đổi |
| Bên nhận chỉ đọc một danh sách đã chốt | `List.copyOf(source)` | Không nhận null; không sao chép sâu |
| Bên nhận chỉ cần các giá trị phân biệt đã chốt | `Set.copyOf(source)` | Mất số lần lặp; không cam kết thứ tự |
| Bên nhận chỉ đọc các ánh xạ đã chốt | `Map.copyOf(source)` | Không nhận khóa/giá trị null; giá trị bên trong có thể sửa |
| Bên nhận cần thứ tự hoặc giao diện chuyên biệt | Chọn cấu trúc và lớp bọc phù hợp với hợp đồng đó | Phải nêu rõ việc có tiếp tục phụ thuộc nguồn hay không |

Bảng dùng List để minh họa khác biệt giữa constructor sao chép, lớp bọc và factory. Với Set và Map, cũng cần phân biệt khả năng sửa và quan hệ với nguồn; không lựa chọn chỉ dựa trên việc tên phương thức có chữ `copy` hay `unmodifiable`.

Lớp bọc có thể rất phù hợp nếu mục tiêu là cung cấp một cửa sổ đọc vào dữ liệu đang được chủ sở hữu quản lý. `copyOf` phù hợp khi muốn phát hành một tập dữ liệu đã chốt cho người nhận. Đây là hai hợp đồng khác nhau mà một thiết kế có thể cần ở các vị trí khác nhau.

Nếu cần một List kết quả có thể sửa tiếp, không thử sửa `List.copyOf` rồi bắt ngoại lệ để chuyển hướng. Tạo thẳng bản sao có thể sửa. Nếu cần sắp xếp rồi phát hành chỉ đọc, thực hiện bước sắp xếp trên dữ liệu có thể sửa trước khi chụp kết quả; không gọi thao tác sắp xếp trên List đã chốt và kỳ vọng factory sẽ tự chuyển chế độ.

Nếu cần bảo toàn thứ tự chèn của tập hợp hoặc map như một phần API, không dùng Set/Map `copyOf` để hứa thứ tự đó. Có thể chuẩn bị một cấu trúc riêng có thứ tự rồi bọc chỉ đọc, với điều kiện không còn đường sửa nguồn làm thay đổi dữ liệu đã công bố. Cách chuẩn bị phải phản ánh đúng hợp đồng của ứng dụng, không chỉ đúng hình thức tên lớp.

Đối chiếu mô tả việc truy vấn đọc xuyên qua nguồn của lớp bọc trong [Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)) để thấy rõ vì sao một view chỉ đọc vẫn khác một tập dữ liệu đã chốt.

## 13. Null, dữ liệu rỗng và trách nhiệm kiểm tra

Ba factory đều nhận dữ liệu rỗng hợp lệ. List rỗng, Set rỗng và Map rỗng vẫn là đối tượng có hợp đồng rõ ràng. Việc có cho phép “không có dữ liệu” trong nghiệp vụ không nhất thiết phải biểu diễn bằng null.

| Trường hợp | List.copyOf | Set.copyOf | Map.copyOf |
| --- | --- | --- | --- |
| Chính đối tượng đầu vào là null | `NullPointerException` | `NullPointerException` | `NullPointerException` |
| Đầu vào rỗng hợp lệ | Chấp nhận | Chấp nhận | Chấp nhận |
| Có phần tử null | `NullPointerException` | `NullPointerException` | Xem riêng khóa và giá trị |
| Có khóa null | Không áp dụng | Không áp dụng | `NullPointerException` |
| Có giá trị null | Không áp dụng | Không áp dụng | `NullPointerException` |

Quy tắc null của factory là một điều kiện kỹ thuật. Quy tắc nghiệp vụ có thể mạnh hơn: chuỗi không được rỗng, mã phải tồn tại, danh sách không được vượt giới hạn, hoặc một người dùng không được có quyền trùng. `copyOf` không thay thế những kiểm tra ấy.

Ví dụ, `Set.copyOf` có thể loại trùng thành công, nhưng điều đó không có nghĩa dữ liệu trùng luôn được chấp nhận trong nghiệp vụ. Nếu yêu cầu là báo lỗi khi nhập lặp quyền, chuyển sang Set mà không kiểm tra có thể che giấu lỗi đầu vào. Nếu yêu cầu là tập quyền duy nhất, loại trùng lại có thể đúng. Mô tả mục tiêu trước rồi mới dùng cơ chế.

Ngược lại, không nên âm thầm chuyển mọi null đầu vào thành một collection rỗng nếu hai trạng thái mang ý nghĩa khác nhau. “Không có mục nào” và “bên gọi quên cung cấp dữ liệu” có thể cần cách xử lý khác. Đặt quy tắc này ở API tiếp nhận để người gọi hiểu trách nhiệm của mình.

Khi factory ném ngoại lệ trong ví dụ, việc thực thi thoát khỏi lời gọi đó; không có một kết quả “sao chép một phần” được trả cho biến nhận. Những chương trình trên chỉ bắt loại ngoại lệ dự kiến để quan sát từng trường hợp, không biến null thành dữ liệu hợp lệ.

## 14. Dùng copyOf ở ranh giới đối tượng và kết quả trả về

Một quy trình thường dễ đọc là: tiếp nhận dữ liệu; chuẩn hóa hoặc kiểm tra theo yêu cầu; dựng dữ liệu trung gian nếu cần; chốt collection; công bố kết quả. Đặt bước chốt ở nơi quyền sở hữu thay đổi giúp người đọc biết bên nào còn được quyền sửa phần chứa.

Trong hàm khởi tạo, `Catalog` ở phần 10 là một ví dụ nhỏ. Đối tượng không muốn cấu hình thay đổi chỉ vì bên gọi tiếp tục sửa List họ truyền vào. Việc chụp đầu vào ngay tại thời điểm tiếp nhận tạo một ranh giới dễ kiểm chứng. Trường `final` sau đó diễn tả rằng chính tham chiếu trường không được gán lại.

Ở phương thức trả về, có hai tình huống:

- Đối tượng đã giữ sẵn một collection chốt và mức bảo vệ phần tử phù hợp. Có thể trả kết quả đã có, như `Catalog.names()`.
- Đối tượng đang quản lý dữ liệu thay đổi, còn người gọi cần một kết quả riêng cho lần yêu cầu hiện tại. Chụp dữ liệu tại ranh giới trả về, với chính sách phần tử phù hợp.

Không dùng một tên phương thức như `getItems` để mặc định rằng cả hai tình huống có cùng hành vi. Tài liệu hợp đồng nên nói rõ người nhận có thấy các thay đổi về sau của chủ sở hữu hay không.

Với map lồng, hãy kiểm tra cả đầu vào và đường truy cập đầu ra. Chụp Map ngoài nhưng trả List giá trị có thể sửa vẫn cho người nhận quyền sửa List đó. Nếu mục tiêu là cấu hình chỉ đọc hai tầng, cần chụp từng List, sau đó chụp Map; nếu phần tử List là đối tượng có thể sửa, tiếp tục đánh giá tầng ấy.

Ảnh chụp cấu trúc không tự cung cấp một giao thức đồng bộ hóa với luồng đang ghi nguồn. Chỉ riêng lời gọi `copyOf` không thiết lập khóa chung với những đường sửa khác trong ứng dụng. Nếu nhiều luồng có thể sửa nguồn lúc lấy dữ liệu, cần một quy tắc phối hợp phù hợp với loại nguồn và yêu cầu nhất quán. Không suy từ “kết quả không sửa được” sang “quá trình đọc đầu vào luôn là ảnh chụp nguyên tử”.

Tương tự, không xem việc gọi factory nhiều lần là cách quản lý phiên bản nghiệp vụ. Nếu mỗi bản cấu hình cần số phiên bản, thời điểm có hiệu lực hoặc quan hệ với dữ liệu khác, mô hình hóa các thông tin đó riêng. `copyOf` chỉ là một bước xây dựng phần dữ liệu collection trong mô hình ấy.

## 15. Chẩn đoán những hiểu nhầm thường gặp

| Quan sát hoặc giả định | Cách đọc đúng và hướng sửa |
| --- | --- |
| Gọi `set` trên kết quả List và nhận ngoại lệ | Kết quả chặn thay phần tử, không chỉ chặn đổi kích thước; tạo bản sao có thể sửa nếu đó là yêu cầu |
| Đổi nguồn mà view chỉ đọc vẫn đổi theo | View có thể đọc xuyên nguồn; chọn ảnh chụp nếu cần chốt dữ liệu |
| Sửa builder rồi văn bản trong List copy cũng đổi | Phần tử dùng chung; chọn phần tử bất biến hoặc chuyển đổi/sao chép phần tử |
| List ba phần tử thành Set hai phần tử | Giá trị trùng đã được gộp; dùng List nếu phải giữ số lần xuất hiện |
| Kỳ vọng Set.copyOf ném lỗi vì phần tử trùng | Quy tắc này khác Set.of; kiểm tra tính duy nhất riêng nếu nghiệp vụ yêu cầu lỗi |
| Thứ tự Set hoặc Map kết quả khác nguồn | Factory không hứa bảo toàn thứ tự đó; chọn một hợp đồng có thứ tự rõ ràng |
| Ép kết quả thành implementation nguồn | Factory chỉ cam kết giao diện và hành vi công bố; bỏ phụ thuộc tên lớp |
| Dùng == để chứng minh đã chép | Định danh không chứng minh độc lập dữ liệu; kiểm tra hành vi với thay đổi nguồn |
| Map ngoài chỉ đọc nhưng List giá trị vẫn sửa được | Cần bảo vệ tầng List nếu không muốn trao quyền sửa nó |
| Đặt trường final nhưng dữ liệu vẫn thay đổi | Final chặn gán lại trường; cần quản lý đối tượng được tham chiếu |

Khi thấy lỗi, xác định chính đối tượng nhận thao tác sửa. `map.put(key, list)` tác động lên Map; `map.get(key).add(value)` tác động lên List; `map.get(key).get(0).setName(...)` tác động lên một đối tượng phần tử sâu hơn nữa. Cách phân tầng này giúp tìm đúng nơi cần thay đổi thiết kế, thay vì thêm một lớp bọc ngoài rồi hy vọng mọi trạng thái bên trong cũng được bảo vệ.

Không rút ra hợp đồng từ một lần in. Một thứ tự duyệt tình cờ, một phép so sánh định danh hoặc tên lớp thấy trong debugger có thể là đặc điểm triển khai. Các ca kiểm chứng nên tập trung vào yêu cầu đã công bố: phần tử/ánh xạ, quyền sửa, quan hệ với nguồn và mức chia sẻ đối tượng.

## 16. Thực hành có tiêu chí quan sát

### Bài 1: Chốt kết quả tìm kiếm

Một phương thức đang dựng List mã sản phẩm, có thể có cùng một mã xuất hiện hai lần. Người nhận phải nhìn thấy đúng thứ tự kết quả tìm kiếm và không được thêm/xóa/thay phần tử.

Hãy chọn factory, nêu chính sách null và viết kiểm tra bằng cách sửa List nguồn sau khi tạo kết quả. Tiêu chí đạt: thứ tự và hai lần xuất hiện được giữ; thay List nguồn không đổi kết quả; thao tác sửa trên kết quả bị chặn. Hướng lựa chọn là `List.copyOf`, vì số vị trí và thứ tự còn thuộc yêu cầu.

### Bài 2: Tập quyền duy nhất

Đầu vào là List quyền dạng String, trong đó có quyền lặp do nhiều nhóm cấp cùng một quyền. Kết quả chỉ cần trả lời có quyền hay không; không có yêu cầu thứ tự.

Hãy xác định có nên báo lỗi khi lặp hay chấp nhận gộp. Nếu nghiệp vụ cho phép gộp, `Set.copyOf` biểu diễn đúng tập quyền duy nhất. Kiểm tra nội dung bằng quan hệ tập hợp, không viết kiểm tra đòi một chuỗi in theo thứ tự cụ thể.

Nếu yêu cầu đổi thành “gặp quyền lặp phải báo lỗi”, chỉ gọi `Set.copyOf` chưa đủ. Cần bổ sung kiểm tra dữ liệu đầu vào; không đổi sang `Set.of(list)` và tưởng rằng List sẽ được tự trải thành các quyền.

### Bài 3: Bảng cấu hình có giá trị lồng

Nguồn là `Map<String, List<String>>`, khóa là tên tính năng, giá trị là các tên môi trường. Người nhận không được thay ánh xạ hoặc sửa các List môi trường. Mọi tên là String không null.

Hãy mô tả quy trình hai tầng và kiểm chứng hai đường sửa khác nhau: đổi List được gắn trong Map nguồn; thêm phần tử vào List cũ. Tiêu chí đạt: cả hai đường sửa nguồn không làm thay đổi dữ liệu đã công bố. Quy trình ở phần 10 cung cấp một cách làm cho đúng hình dạng dữ liệu này.

### Bài 4: Kết quả có yêu cầu thứ tự khóa

Một báo cáo phải duyệt các mục theo thứ tự chèn đã xác định. Có người đề xuất chuyển `LinkedHashMap` thành `Map.copyOf` rồi coi thứ tự vẫn được giữ vì nguồn có thứ tự.

Hãy chỉ ra phần cam kết còn thiếu và chọn cách biểu diễn đáp ứng thứ tự. Tiêu chí đạt là nói rõ thứ tự nằm trong hợp đồng của cấu trúc nào và việc bên ngoài có còn giữ đường sửa cấu trúc đó hay không. Chỉ kiểm tra một lần in đúng không đủ để chứng minh thiết kế.

### Bài 5: Đổi kiểu phần tử trong Catalog

Thay danh sách tên String bằng danh sách đối tượng có phương thức sửa tên. Hàm khởi tạo vẫn dùng `List.copyOf`.

Dự đoán điều gì được bảo vệ và điều gì còn được chia sẻ. Tiêu chí đạt: phân biệt việc thay một vị trí trong List với việc sửa tên trên đối tượng ở vị trí đó; đề xuất đối tượng bất biến hoặc cách sao chép phù hợp nếu cần ổn định tên.

## 17. Tự kiểm tra trước khi áp dụng

1. **Ba factory có luôn tạo đối tượng collection mới không?** Không. Hợp đồng dữ liệu không phụ thuộc việc cấp phát mới hay tận dụng kết quả thích hợp đã có. Không kiểm tra đúng/sai bằng định danh.
2. **List.copyOf có loại phần tử trùng không?** Không. Nó giữ các phần tử theo thứ tự duyệt nguồn. Set.copyOf mới biểu diễn các giá trị phân biệt.
3. **Set.copyOf có chọn đại diện đầu tiên của các phần tử bằng nhau không?** Không có cam kết ấy. Nếu cần quy tắc chọn bản ghi, thực hiện quy tắc rõ ràng trước khi tạo tập kết quả.
4. **Map.copyOf có giữ thứ tự của LinkedHashMap không?** Không có bảo đảm thứ tự duyệt tương ứng. Đặt yêu cầu thứ tự vào hợp đồng và chọn công cụ thích hợp.
5. **Nguồn chứa null nhưng tự cho phép null thì sao?** Quy tắc nguồn không thay đổi quy tắc factory. Xử lý null theo hợp đồng tiếp nhận trước khi chụp nếu cần.
6. **Map.copyOf có làm List giá trị thành chỉ đọc không?** Không. Nó chốt các ánh xạ; cần xử lý List ở tầng trong riêng.
7. **Lớp bọc chỉ đọc và ảnh chụp khác nhau ở đâu?** Lớp bọc có thể tiếp tục đọc qua nguồn; ảnh chụp copyOf không đi theo việc nguồn thêm/xóa/thay phần tử hoặc ánh xạ sau khi sao chép.
8. **Khi nào có thể trả trực tiếp collection đã chốt từ một trường?** Khi quyền sửa và mức chia sẻ phần tử đáp ứng hợp đồng của đối tượng. Với String bất biến trong Catalog, điều kiện này rõ ràng hơn với phần tử có thể sửa.
9. **Vì sao stdout của Set và Map trong bài phải qua cấu trúc sắp xếp?** Để trình bày ổn định khi kiểm chứng dữ liệu; không gán cho kết quả factory một thứ tự mà API không hứa.
10. **Kiểm tra nào chứng minh độc lập với nguồn tốt hơn so sánh ==?** Tạo kết quả, sửa nguồn theo cách được phép, rồi kiểm tra phần tử hoặc ánh xạ trong kết quả vẫn như yêu cầu. Kiểm tra thêm riêng việc dùng chung đối tượng phần tử khi mức bất biến có liên quan.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unmodifiable-collections-006">Quy tắc null, phần tử trùng và khóa trùng của các factory</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc null, phần tử trùng và khóa trùng của các factory

## 1. Tạo collection chỉ đọc cũng là chọn chính sách dữ liệu

Một danh sách mã quyền có thể cần giữ hai lần xuất hiện của cùng mã. Một tập mã quyền chỉ cần mỗi mã một lần. Một bảng cấu hình lại cần mỗi khóa có đúng một giá trị. Cả ba kết quả đều có thể không cho sửa, nhưng quy tắc dữ liệu đầu vào không giống nhau.

Các phương thức tạo tĩnh (factory) như List.of, Set.of và Map.of vừa tạo kết quả vừa áp dụng hợp đồng của từng loại:

- List giữ thứ tự và có thể giữ nhiều phần tử bằng nhau.
- Set chỉ giữ một đại diện cho mỗi phần tử theo quan hệ bằng nhau; cách tạo có thể từ chối hoặc gộp đầu vào trùng.
- Map yêu cầu khóa duy nhất; nhiều khóa khác nhau vẫn được phép có cùng giá trị.
- Chính sách không nhận null là một điều kiện riêng, không tự suy ra chỉ từ chữ “chỉ đọc”.

Điểm dễ nhầm nhất: **Set.of từ chối phần tử trùng, còn Set.copyOf chấp nhận collection có phần tử trùng và giữ một đại diện.** Tên cùng có Set và cùng trả kết quả không cho sửa chưa đủ để suy ra cùng quy tắc đầu vào.

Bài này tập trung vào các factory của List, Set, Map, các bản copyOf, Map.entry/Map.ofEntries và các collector tạo kết quả không cho sửa. Phần cuối đối chiếu một số factory cũ để tránh áp quy tắc null của họ of cho mọi API Java.

Ba chương trình đầy đủ dùng Java 17 trở lên và thư viện chuẩn. Những biểu thức gây lỗi được đặt trong hàm kiểm tra nên chương trình vẫn chạy hết. Kết quả của set/map chỉ được in khi thứ tự đã được làm rõ hoặc đã chuyển sang cấu trúc có thứ tự để trình bày.

## 2. Bảng quy tắc cần phân biệt ngay

### 2.1. Các factory chính

| Phương thức | null ở phần tử/khóa/giá trị được đưa trực tiếp vào | Đầu vào trùng | Kết quả khi hợp lệ |
|---|---|---|---|
| List.of(...) | Không nhận phần tử null | Cho phép phần tử trùng | List giữ các vị trí theo thứ tự đối số |
| List.copyOf(collection) | Collection và các phần tử đều không null | Giữ phần tử trùng | List theo thứ tự duyệt đầu vào |
| Set.of(...) | Không nhận phần tử null | Từ chối phần tử trùng bằng IllegalArgumentException | Set không có phần tử trùng |
| Set.copyOf(collection) | Collection và các phần tử đều không null | Gộp phần tử trùng, giữ một đại diện không được cam kết là đầu hay cuối | Set không có phần tử trùng |
| Map.of(k,v,...) | Không nhận khóa hoặc giá trị null | Từ chối khóa trùng bằng IllegalArgumentException; giá trị trùng được phép | Map với các khóa duy nhất |
| Map.ofEntries(entries...) | Không nhận entry, khóa hoặc giá trị null | Từ chối khóa trùng bằng IllegalArgumentException | Map lấy khóa/giá trị từ các entry |
| Map.copyOf(map) | Map, các khóa và các giá trị đều không null | Nhận các ánh xạ hiện tại của map đầu vào; không phát hiện lịch sử ghi đè đã mất trước đó | Map không theo các cập nhật ánh xạ về sau của map nguồn |
| Map.entry(k,v) | Không nhận khóa hoặc giá trị null | Chỉ tạo một entry, chưa có vấn đề trùng giữa nhiều entry | Entry không cho setValue |

Với các trường hợp null bị cấm ở bảng, lời gọi không hợp lệ gây NullPointerException. Đây là lỗi ở bước tạo kết quả, khác UnsupportedOperationException khi cố sửa kết quả đã được tạo thành công.

Map.copyOf nhận một Map chứ không nhận một dãy cặp khóa/giá trị. Một map thông thường đã áp dụng quy tắc khóa duy nhất trước khi copyOf được gọi; cần nhớ khác biệt này khi kiểm tra dữ liệu trùng.

Các kiểu collection cụ thể vẫn có thể có chính sách khác. Ví dụ HashMap mutable cho phép một khóa null và giá trị null; điều đó không làm Map.copyOf nhận được các null ấy.

### 2.2. Quy mô đối số và phiên bản API

List.of và Set.of có các dạng thuận tiện cho số phần tử nhỏ, cùng dạng varargs để nhận nhiều phần tử hơn. Map.of có các dạng từ không ánh xạ đến mười ánh xạ; với nhiều cặp hơn thường dùng Map.ofEntries.

Các phương thức of, Map.ofEntries và Map.entry có từ Java 9. Các bản copyOf có từ Java 10. Mọi API này đều có trên Java 17 dùng trong bài.

List.of(), Set.of(), Map.of() và Map.ofEntries() có thể tạo kết quả rỗng. Rỗng không có nghĩa một factory bắt đầu chấp nhận một phần tử null; “không có phần tử” và “có một phần tử null” là hai dữ liệu khác nhau.

## 3. null: phân biệt đối tượng đầu vào, phần tử và dữ liệu bên trong

### 3.1. null không được tự bỏ qua

List.of("a",null) không tạo list chỉ còn a. Set.of("a",null) không bỏ null để tiếp tục. Map.of("a",null) không bỏ ánh xạ hoặc thay null bằng chuỗi rỗng.

Nếu nghiệp vụ muốn lọc, thay giá trị thiếu hoặc từ chối với thông báo riêng, phải làm việc đó tường minh. Factory không chọn chính sách nghiệp vụ thay ứng dụng.

Cũng cần phân biệt null với dữ liệu khác:

- Chuỗi "null" là một String có bốn ký tự, không phải tham chiếu null.
- Chuỗi rỗng "" vẫn là một String không null.
- Một collection rỗng là một đối tượng collection, không phải null.
- Một đối tượng có một trường bên trong bằng null vẫn có thể là phần tử không null.

### 3.2. copyOf kiểm tra cả nguồn và nội dung cần lấy

List.copyOf(null) không có collection để sao chép nên gây NullPointerException. List.copyOf một ArrayList không null nhưng có phần tử null cũng không hợp lệ.

Set.copyOf có cùng điều kiện không nhận nguồn null và không có phần tử null. Map.copyOf yêu cầu map nguồn không null và không chứa khóa/giá trị null.

Không nhìn kiểu ArrayList hoặc HashMap của nguồn để đoán rằng nội dung chắc chắn phù hợp: chính những lớp nguồn đó cho phép một số null mà factory đích từ chối.

### 3.3. Với varargs, hãy viết ví dụ null không nhập nhằng

Trong Java, một lời gọi chỉ viết List.of(null) có thể liên quan tới việc chọn overload và cách hiểu tham số varargs, đồng thời có thể tạo cảnh báo của compiler. Khi muốn diễn đạt chính xác trường hợp đang thử, nên ghi rõ:

- List.of((String) null): một phần tử String bằng null.
- List.of((String[]) null): mảng varargs bằng null.

Cả hai trường hợp này đều thất bại, nhưng ý nghĩa đầu vào khác nhau. Dùng cast trong ví dụ để giải thích hợp đồng, không phải để lách điều kiện null.

### 3.4. Factory không kiểm tra đệ quy mọi trường của phần tử

List chứa một mảng String[] không null có thể được tạo hợp lệ dù một ô trong mảng là null, miễn mảng đó thật sự là **một phần tử** của list.

Factory chỉ áp điều kiện lên phần tử mà nó nhận ở cấp đó. Nó không tự đi vào mọi mảng, list hoặc đối tượng nghiệp vụ để xác nhận không có null ở bất cứ đâu.

Phần varargs ở cuối chương trình thứ hai sẽ kiểm chứng điều này với List.<String[]>of(...). Nếu API nghiệp vụ cấm cả null trong đối tượng con, phải có kiểm tra mô hình tương ứng.

## 4. Chương trình kiểm tra các factory nhận đối số trực tiếp

DirectFactoryRulesDemo tách từng dữ liệu không hợp lệ thành một lời gọi riêng. Hàm expect chỉ in tên loại ngoại lệ đã xác nhận đúng, không phụ thuộc thông báo hoặc stack trace của một bản JDK cụ thể.

Tệp **DirectFactoryRulesDemo.java**:

```java
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DirectFactoryRulesDemo {
    private static void expect(String label,
            Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + ": no exception");
        } catch (RuntimeException failure) {
            if (!type.isInstance(failure)) {
                throw failure;
            }
            System.out.println(label + "=" + failure.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        System.out.println("listDuplicates=" + List.of("x", "x"));
        System.out.println("emptySizes=" +
                List.of().size() + "," + Set.of().size() + "," + Map.of().size());

        expect("listNullElement", NullPointerException.class,
                () -> List.of((String) null));
        expect("listNullArray", NullPointerException.class,
                () -> List.of((String[]) null));
        expect("setNullElement", NullPointerException.class,
                () -> Set.of((String) null));
        expect("setDuplicate", IllegalArgumentException.class,
                () -> Set.of("x", "x"));

        Map<String, Integer> repeatedValues = Map.of("a", 7, "b", 7);
        System.out.println("repeatedMapValueSize=" + repeatedValues.size());
        expect("mapNullKey", NullPointerException.class,
                () -> Map.of((String) null, 1));
        expect("mapNullValue", NullPointerException.class,
                () -> Map.of("a", (Integer) null));
        expect("mapDuplicateKey", IllegalArgumentException.class,
                () -> Map.of("a", 1, "a", 1));

        expect("entryNullValue", NullPointerException.class,
                () -> Map.entry("a", (Integer) null));
        expect("ofEntriesNullEntry", NullPointerException.class,
                () -> Map.ofEntries((Map.Entry<String, Integer>) null));
        expect("ofEntriesNullKey", NullPointerException.class,
                () -> Map.ofEntries(
                        new AbstractMap.SimpleEntry<String, Integer>(null, 1)));
        expect("ofEntriesNullValue", NullPointerException.class,
                () -> Map.ofEntries(
                        new AbstractMap.SimpleEntry<String, Integer>("a", null)));
        expect("ofEntriesDuplicateKey", IllegalArgumentException.class,
                () -> Map.ofEntries(Map.entry("a", 1), Map.entry("a", 2)));

        Map.Entry<String, Integer> entry =
                new AbstractMap.SimpleEntry<>("a", 1);
        Map<String, Integer> extracted = Map.ofEntries(entry);
        entry.setValue(9);
        System.out.println("extractedValue=" + extracted.get("a"));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 DirectFactoryRulesDemo.java
java DirectFactoryRulesDemo
```

Stdout chính xác:

```text
listDuplicates=[x, x]
emptySizes=0,0,0
listNullElement=NullPointerException
listNullArray=NullPointerException
setNullElement=NullPointerException
setDuplicate=IllegalArgumentException
repeatedMapValueSize=2
mapNullKey=NullPointerException
mapNullValue=NullPointerException
mapDuplicateKey=IllegalArgumentException
entryNullValue=NullPointerException
ofEntriesNullEntry=NullPointerException
ofEntriesNullKey=NullPointerException
ofEntriesNullValue=NullPointerException
ofEntriesDuplicateKey=IllegalArgumentException
extractedValue=1
```

### 4.1. List giữ trùng; Set xem trùng là lỗi ở họ of

List.of("x","x") tạo hai vị trí cùng giá trị x. Set.of("x","x") bị từ chối. Không dùng Set.of như một phép “loại trùng tiện lợi” cho các đối số không biết trước.

Từ chối trùng phù hợp khi một khai báo lặp lại được xem là lỗi cần phát hiện. Nếu muốn thu các giá trị riêng biệt từ một nguồn có thể lặp, Set.copyOf hoặc một phép thu set với hợp đồng tương ứng sẽ thể hiện mục đích khác.

### 4.2. Map chỉ yêu cầu khóa duy nhất

Map.of("a",7,"b",7) hợp lệ vì có hai khóa khác nhau. Giá trị 7 được dùng cho cả hai ánh xạ không tạo ra xung đột.

Map.of("a",1,"a",1) vẫn bị từ chối dù giá trị cũng giống nhau. Factory không tự chọn “nếu cùng giá trị thì bỏ qua khóa lặp” và cũng không áp quy tắc “cặp sau thắng” như thao tác put lặp trên một map mutable.

Hợp đồng của [Map.of và Map.ofEntries](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#ofEntries(java.util.Map.Entry...)) phân biệt rõ khóa trùng với giá trị trùng.

### 4.3. Map.entry có thể báo lỗi trước Map.ofEntries

Map.entry("a",null) tự thất bại ngay khi tạo entry. Nếu nó nằm trong đối số của Map.ofEntries, chương trình còn chưa có entry hợp lệ để truyền tiếp.

Map.ofEntries cũng phải kiểm tra những entry đến từ nguồn khác. AbstractMap.SimpleEntry trong ví dụ có thể mang null, nên hai phép thử khóa null và giá trị null kiểm tra chính bước trích dữ liệu của ofEntries.

Một tham chiếu entry bằng null cũng bị từ chối. Vì vậy cần kiểm tra ba tầng riêng: mảng/dãy entry, mỗi đối tượng entry, và khóa/giá trị bên trong entry.

### 4.4. Map.ofEntries lấy dữ liệu, không giữ đối tượng entry đầu vào

Ở cuối chương trình, entry mutable ban đầu chứa a→1. Sau khi extracted được tạo, entry.setValue(9) không đổi extracted.get("a"), vẫn là 1.

Điều này minh họa hợp đồng trích khóa/giá trị ra khỏi entry. Nó không sao chép sâu đối tượng khóa hoặc giá trị; nếu giá trị là một đối tượng mutable dùng chung, trạng thái bên trong đối tượng ấy vẫn cần xét riêng.

## 5. “Trùng” dựa vào quan hệ bằng nhau, không dựa vào hình thức hiển thị

### 5.1. Khác tham chiếu vẫn có thể là phần tử hoặc khóa trùng

Hai String được tạo bằng hai đối tượng khác nhau nhưng cùng nội dung vẫn equals nhau. Set.of không coi chúng là hai phần tử riêng chỉ vì == trả false.

Với lớp nghiệp vụ, quy tắc bằng nhau do equals() định nghĩa, cùng hợp đồng hashCode() tương ứng. Nếu hai đối tượng có cùng mã nghiệp vụ được equals xem là bằng nhau, chúng có thể là phần tử trùng hoặc khóa trùng ở các factory này, dù những trường mô tả khác nhau.

Ngược lại, hai đối tượng in ra cùng một chuỗi chưa chắc equals nhau. Không dựa vào toString() để kiểm tra trùng.

### 5.2. Cùng hashCode không có nghĩa là trùng

Hash code hỗ trợ tổ chức tra cứu, không thay cho quan hệ equals. Hai phần tử khác nhau có thể va chạm hash mà vẫn cùng tồn tại.

Ví dụ "FB" và "Ea" có cùng hashCode của String nhưng không bằng nhau theo equals. Set.of("FB","Ea") vì thế có hai phần tử, không bị lỗi trùng.

Nếu lớp tự viết vi phạm hợp đồng equals/hashCode, không dùng một kết quả ngẫu nhiên của factory để suy ra Java đang đổi định nghĩa bằng nhau. Cần sửa hợp đồng lớp trước.

### 5.3. Không tự chuẩn hóa chuỗi hoặc dùng Comparator thay equals

"admin" và "ADMIN" là hai String khác nhau theo equals. Factory không tự chuyển chữ thường, trim khoảng trắng hoặc áp một Comparator không phân biệt hoa/thường.

Nếu nghiệp vụ xem chúng là một mã, phải chuẩn hóa hoặc ánh xạ khóa theo chính sách đã định trước bước tạo collection. Việc đó có thể làm phát sinh xung đột mới mà dữ liệu thô chưa có, nên cần chọn từ chối, gộp hay báo lỗi rõ ràng.

## 6. copyOf: khác biệt quan trọng ở cách xử lý dữ liệu trùng

List.copyOf giữ các phần tử trùng và thứ tự duyệt đầu vào. Set.copyOf nhận một Collection, vì vậy đầu vào hoàn toàn có thể là List có phần tử lặp. Nó tạo set với một đại diện cho mỗi nhóm bằng nhau.

Theo hợp đồng riêng của [Set.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#copyOf(java.util.Collection)), khi có nhiều đối tượng bằng nhau, đại diện được giữ không được cam kết là đối tượng xuất hiện đầu tiên hoặc cuối cùng.

### 6.1. Chương trình sao chép, equality và mảng

Label trong chương trình chỉ dùng id cho equals/hashCode. Hai Label có cùng id nhưng khác text vì vậy là các đối tượng khác tham chiếu mà vẫn bằng nhau theo quy tắc của lớp.

Tệp **CopyFactoryRulesDemo.java**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class CopyFactoryRulesDemo {
    static final class Label {
        final int id;
        final String text;

        Label(int id, String text) {
            this.id = id;
            this.text = text;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Label label && id == label.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    private static void expect(String label,
            Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + ": no exception");
        } catch (RuntimeException failure) {
            if (!type.isInstance(failure)) {
                throw failure;
            }
            System.out.println(label + "=" + failure.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("b", "a", "b"));
        List<String> listCopy = List.copyOf(source);
        Set<String> setCopy = Set.copyOf(source);
        System.out.println("copiedList=" + listCopy);
        System.out.println("copiedSetSorted=" + new TreeSet<>(setCopy));

        expect("copyListNull", NullPointerException.class,
                () -> List.copyOf(Arrays.asList("x", null)));
        expect("copySetNull", NullPointerException.class,
                () -> Set.copyOf(Arrays.asList("x", null)));

        Label first = new Label(1, "first");
        Label second = new Label(1, "second");
        System.out.println("differentObjects=" + (first != second));
        System.out.println("equalElements=" + first.equals(second));
        expect("ofEqualElements", IllegalArgumentException.class,
                () -> Set.of(first, second));
        Set<Label> labels = Set.copyOf(List.of(first, second));
        Label chosen = labels.iterator().next();
        System.out.println("copyDedupSize=" + labels.size());
        System.out.println("representativeIsInput=" +
                (chosen == first || chosen == second));

        System.out.println("sameHash=" + ("FB".hashCode() == "Ea".hashCode()));
        System.out.println("collisionSetSize=" + Set.of("FB", "Ea").size());

        String[] row1 = {"same"};
        String[] row2 = {"same"};
        System.out.println("differentArraySetSize=" + Set.of(row1, row2).size());
        expect("sameArrayRepeated", IllegalArgumentException.class,
                () -> Set.of(row1, row1));
        List<int[]> primitiveArrayElement = List.of(new int[]{1, 2});
        System.out.println("primitiveArrayListSize=" + primitiveArrayElement.size());
        List<String[]> nested = List.<String[]>of(new String[]{"x", null});
        System.out.println("nestedListSize=" + nested.size());
        System.out.println("nestedNullAllowed=" + (nested.get(0)[1] == null));

        Map<String, Integer> staged = new LinkedHashMap<>();
        staged.put("k", 1);
        staged.put("k", 2);
        Map<String, Integer> mapCopy = Map.copyOf(staged);
        System.out.println("stagedSize=" + staged.size());
        System.out.println("copiedMapValue=" + mapCopy.get("k"));

        Map<String, Integer> nullValue = new LinkedHashMap<>();
        nullValue.put("k", null);
        expect("copyMapNullValue", NullPointerException.class,
                () -> Map.copyOf(nullValue));
        Map<String, Integer> nullKey = new LinkedHashMap<>();
        nullKey.put(null, 1);
        expect("copyMapNullKey", NullPointerException.class,
                () -> Map.copyOf(nullKey));
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 CopyFactoryRulesDemo.java
java CopyFactoryRulesDemo
```

Stdout chính xác:

```text
copiedList=[b, a, b]
copiedSetSorted=[a, b]
copyListNull=NullPointerException
copySetNull=NullPointerException
differentObjects=true
equalElements=true
ofEqualElements=IllegalArgumentException
copyDedupSize=1
representativeIsInput=true
sameHash=true
collisionSetSize=2
differentArraySetSize=2
sameArrayRepeated=IllegalArgumentException
primitiveArrayListSize=1
nestedListSize=1
nestedNullAllowed=true
stagedSize=1
copiedMapValue=2
copyMapNullValue=NullPointerException
copyMapNullKey=NullPointerException
```

### 6.2. Đại diện được giữ không phải quy tắc chọn dữ liệu nghiệp vụ

Set.of(first,second) bị lỗi vì hai Label bằng nhau. Set.copyOf(List.of(first,second)) thành công với kích thước 1.

Chương trình chỉ kiểm tra đại diện là một trong hai đối tượng đầu vào. Nó không in nhãn text nào được giữ rồi tuyên bố đó là quy tắc “giữ bản đầu”.

Nếu hai bản có cùng id nhưng khác thông tin mà nghiệp vụ cần xử lý, việc loại trùng tùy ý có thể mất dữ liệu quan trọng. Cần tự kiểm tra xung đột hoặc chọn phép gộp tường minh, không dùng Set.copyOf làm bộ hòa giải dữ liệu.

### 6.3. Map.copyOf không thấy lịch sử khóa đã bị ghi đè

staged.put("k",1) rồi staged.put("k",2) làm map nguồn chỉ còn một ánh xạ k→2. Map.copyOf(staged) nhận đúng trạng thái hiện tại ấy, nên không có hai ánh xạ k→1 và k→2 để kiểm tra với nhau.

Nếu yêu cầu là phát hiện khóa lặp trong một tệp cấu hình hoặc một dãy bản ghi, phải kiểm tra trong lúc đọc dãy đó, trước khi ghi đè làm mất thông tin. Không dựng HashMap bằng put rồi trông chờ copyOf phát hiện các lần xuất hiện cũ.

Với map có quy tắc khóa đặc biệt như IdentityHashMap, còn phải xem chính sách chuyển đổi quan hệ khóa. Map.copyOf không phải lời hứa giữ nguyên ngữ nghĩa so sánh theo danh tính của mọi loại map đầu vào.

### 6.4. Thứ tự in đã được chuẩn hóa có chủ đích

copiedSetSorted được in bằng new TreeSet<>(setCopy). Vì vậy [a,b] trong stdout là thứ tự của TreeSet dùng để trình bày, không phải cam kết rằng Set.copyOf giữ thứ tự chèn hoặc tự sắp xếp.

Set.of/Set.copyOf và Map.of/Map.ofEntries/Map.copyOf không phải API chọn thứ tự chèn. Muốn một thứ tự cho báo cáo hoặc kiểm thử, phải chọn cấu trúc/hợp đồng phù hợp hoặc so sánh dưới dạng tập/ánh xạ.

## 7. Mảng và varargs: phải xác định cái gì là một phần tử

### 7.1. Mảng tham chiếu có thể được dùng làm dãy đối số

Với String[] words, lời gọi List.of(words) thường lấy các String trong mảng làm phần tử thông qua cách gọi varargs. Nếu trong words có null, các phần tử được đưa vào list có null và lời gọi bị từ chối.

Muốn list có **một phần tử là chính mảng String[]**, có thể viết List.<String[]>of(words). Trong chương trình, mảng ấy không null nên phần tử cấp ngoài hợp lệ; một ô bên trong bằng null không bị kiểm tra đệ quy.

Đây là khác biệt về cách truyền dữ liệu và kiểu phần tử, không phải một ngoại lệ bí mật cho null.

### 7.2. Mảng nguyên thủy không tự biến thành list các số bọc

List.of(new int[]{1,2}) tạo list có một phần tử kiểu int[]. Nó không tự tạo List<Integer> gồm hai phần tử.

Vì vậy kiểm tra list.size() cho kết quả 1. Nếu muốn một list Integer từ các số trong mảng nguyên thủy, phải dùng một phép chuyển đổi phù hợp, chẳng hạn luồng số rồi boxed; không đọc List.of như thao tác trải mảng nguyên thủy tự động.

### 7.3. Mảng thông thường không so sánh nội dung bằng equals

row1 và row2 là hai String[] có cùng nội dung. Chúng là hai đối tượng mảng khác nhau và equals của mảng thông thường không so nội dung từng ô, nên Set.of(row1,row2) có kích thước 2.

Set.of(row1,row1) lại lặp đúng cùng một đối tượng và bị từ chối. Muốn quan hệ bằng nhau theo nội dung mảng, cần một kiểu dữ liệu/khóa định nghĩa điều đó phù hợp; gọi Arrays.equals ở một chỗ riêng không làm factory tự dùng phép so sánh ấy.

Quy tắc equals của [Object](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object)) cùng các phép so sánh nội dung của [Arrays](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html) là hai cơ chế cần phân biệt.

## 8. Collector tạo kết quả chỉ đọc có quy tắc riêng

Một pipeline Stream có thể tạo dữ liệu không cho sửa bằng Collectors.toUnmodifiableList, toUnmodifiableSet và toUnmodifiableMap. Chúng cùng liên quan mục tiêu tạo kết quả chỉ đọc nhưng không có mọi quy tắc ngoại lệ giống lời gọi of trực tiếp.

### 8.1. Bảng đối chiếu collector

| Collector | null | Trùng | Thứ tự |
|---|---|---|---|
| toUnmodifiableList() | Từ chối phần tử null | Giữ phần tử trùng | Giữ thứ tự gặp phần tử nếu stream có thứ tự |
| toUnmodifiableSet() | Từ chối phần tử null | Gộp các phần tử bằng nhau, giữ đại diện tùy hợp đồng | Không cam kết thứ tự chèn |
| toUnmodifiableMap(keyMapper,valueMapper) | Từ chối khóa/giá trị null do các hàm ánh xạ trả về | Khóa trùng gây IllegalStateException | Không cam kết thứ tự chèn |
| toUnmodifiableMap(keyMapper,valueMapper,mergeFunction) | Vẫn không cho các hàm ánh xạ tạo khóa/giá trị null | Dùng mergeFunction xử lý các giá trị cùng khóa | Không cam kết thứ tự chèn |

Các hàm ánh xạ và hàm gộp được truyền vào cũng phải không null. Khi thiết kế phép gộp, phải đọc hợp đồng của nó, không dùng việc trả null như một cách tùy tiện giữ một ánh xạ có giá trị null.

Khác biệt nổi bật là **IllegalStateException của collector bản không có hàm gộp**, so với **IllegalArgumentException của Map.of/Map.ofEntries khi nhận khóa trùng**.

### 8.2. Chương trình thu Stream và đối chiếu factory cũ

Ví dụ ghi rõ từng nguồn Stream riêng, không dùng lại một stream đã kết thúc. Các map được in qua TreeMap khi cần stdout ổn định.

Tệp **CollectorFactoryRulesDemo.java**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectorFactoryRulesDemo {
    private static void expect(String label,
            Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + ": no exception");
        } catch (RuntimeException failure) {
            if (!type.isInstance(failure)) {
                throw failure;
            }
            System.out.println(label + "=" + failure.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        System.out.println("collectedList=" + Stream.of("x", "x")
                .collect(Collectors.toUnmodifiableList()));
        System.out.println("collectedSetSize=" + Stream.of("x", "x")
                .collect(Collectors.toUnmodifiableSet()).size());

        expect("collectorListNull", NullPointerException.class,
                () -> Stream.of("x", null)
                        .collect(Collectors.toUnmodifiableList()));
        expect("collectorSetNull", NullPointerException.class,
                () -> Stream.of("x", null)
                        .collect(Collectors.toUnmodifiableSet()));
        expect("collectorDuplicateKey", IllegalStateException.class,
                () -> Stream.of("x", "x").collect(
                        Collectors.toUnmodifiableMap(Function.identity(), x -> 1)));
        expect("collectorNullKey", NullPointerException.class,
                () -> Stream.of("x").collect(
                        Collectors.toUnmodifiableMap(x -> (String) null, x -> 1)));
        expect("collectorNullValue", NullPointerException.class,
                () -> Stream.of("x").collect(
                        Collectors.toUnmodifiableMap(
                                Function.identity(), x -> (Integer) null)));

        Map<String, Integer> counts = Stream.of("red", "blue", "red").collect(
                Collectors.toUnmodifiableMap(
                        Function.identity(), x -> 1, Integer::sum));
        System.out.println("mergedCountsSorted=" + new TreeMap<>(counts));
        System.out.println("streamToListNull=" + Stream.of("x", null).toList());

        System.out.println("legacySingletonList=" +
                Collections.singletonList(null));
        System.out.println("legacySingletonSetHasNull=" +
                Collections.singleton(null).contains(null));
        Map<String, Integer> legacyMap =
                Collections.singletonMap(null, null);
        System.out.println("legacySingletonMap=" +
                legacyMap.size() + "," + legacyMap.containsKey(null));
        System.out.println("legacyNCopies=" + Collections.nCopies(2, null));
        List<String> nullableSource =
                new ArrayList<>(Arrays.asList("x", null));
        System.out.println("wrappedNullable=" +
                Collections.unmodifiableList(nullableSource));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 CollectorFactoryRulesDemo.java
java CollectorFactoryRulesDemo
```

Stdout chính xác:

```text
collectedList=[x, x]
collectedSetSize=1
collectorListNull=NullPointerException
collectorSetNull=NullPointerException
collectorDuplicateKey=IllegalStateException
collectorNullKey=NullPointerException
collectorNullValue=NullPointerException
mergedCountsSorted={blue=1, red=2}
streamToListNull=[x, null]
legacySingletonList=[null]
legacySingletonSetHasNull=true
legacySingletonMap=1,true
legacyNCopies=[null, null]
wrappedNullable=[x, null]
```

### 8.3. Gộp khóa là một quyết định rõ ràng

Stream.of("x","x") với toUnmodifiableMap(identity,x→1) có hai phần tử ánh xạ tới cùng khóa x. Nó vẫn lỗi dù hai giá trị đều là 1; bản collector này không tự chấp nhận “trùng nhưng cùng giá trị”.

Trong phép đếm màu, mergeFunction là Integer::sum. Hai lần gặp red góp vào cùng khóa và được cộng thành 2. Đây là chính sách đếm có ý nghĩa rõ ràng, khác với âm thầm giữ một bản bất kỳ.

Tùy nghiệp vụ, có thể cần giữ một bản theo tiêu chí xác định, cộng dồn, thu các bản thành danh sách hoặc từ chối xung đột. Hàm gộp phải biểu đạt đúng ý định đó. Với pipeline có thể chạy song song, còn phải đáp ứng các điều kiện của phép thu và phép gộp; không dùng một hàm phụ thuộc thứ tự thực thi tùy ý.

Hợp đồng ở [Collectors.toUnmodifiableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#toUnmodifiableMap(java.util.function.Function,java.util.function.Function)) và các collector cùng họ là cơ sở để phân biệt kiểu ngoại lệ và chính sách trùng.

### 8.4. Stream.toList khác toUnmodifiableList về null

Stream.toList() có từ Java 16, trả list không cho sửa và trong ví dụ vẫn giữ null. Collectors.toUnmodifiableList() lại từ chối null.

Hai cách viết cùng có kết quả không cho sửa không có nghĩa đổi qua lại luôn giữ nguyên chính sách dữ liệu. Phải đọc hợp đồng phương thức đang dùng; đặc tả [Stream.toList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList()) quy định cách thu list và không áp điều kiện từ chối null của toUnmodifiableList.

Collectors.toList() cũng là một phương thức khác nữa: không dùng nó làm cam kết chung về kiểu triển khai hoặc tính sửa được của kết quả. Nếu cần một ArrayList mutable một cách tường minh, chọn phép thu tạo cấu trúc đó theo hợp đồng.

## 9. Không áp quy tắc họ of cho mọi factory hoặc lớp bọc

### 9.1. Một số factory cũ cho phép null

Chương trình thứ ba cho thấy:

| API | Đầu vào trong ví dụ | Kết quả |
|---|---|---|
| Collections.singletonList(null) | Một phần tử null | List có một null |
| Collections.singleton(null) | Một phần tử null | Set có một null |
| Collections.singletonMap(null,null) | Khóa và giá trị null | Một ánh xạ có khóa null |
| Collections.nCopies(2,null) | Lặp cùng giá trị null hai lần | List có hai phần tử null |
| Collections.unmodifiableList(nullableSource) | Nguồn đã có null | View đọc lại phần tử null của nguồn |

Các factory/lớp bọc này không có cùng điều kiện đầu vào với List.of, Set.of và Map.of. Vì vậy “collection chỉ đọc không bao giờ có null” là một phát biểu sai nếu áp cho toàn bộ Java Collections.

Collections.nCopies cũng không nhân bản đối tượng phần tử. Nếu truyền một đối tượng mutable, các vị trí lặp cùng tham chiếu; điều này khác với tạo n đối tượng mutable riêng.

Hợp đồng của các phương thức thuộc [Collections](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html) phải được đọc theo từng API, không thay bằng suy luận từ tên chung “factory”.

### 9.2. Lớp bọc không phải bộ lọc dữ liệu

Collections.unmodifiableList(source) chủ yếu chặn sửa qua giao diện được trả ra, không chủ động kiểm tra và loại null đã có trong source. Với Set hoặc Map, lớp bọc cũng dựa vào dữ liệu nguồn và chính sách vốn có của nguồn.

Nếu source là một ArrayList có các phần tử lặp, bọc unmodifiableList vẫn giữ các phần tử lặp. Nếu map nguồn đã ghi đè khóa trong lúc put, bọc nó không khôi phục lịch sử các bản ghi đã bị thay.

Bởi vậy, thay lớp bọc bằng copyOf có thể đổi chính sách null, quan hệ theo nguồn và thời điểm phát hiện lỗi. Không coi đó là một thay thế chỉ liên quan cách viết.

## 10. Chọn chính sách trước khi chọn phương thức

Hãy bắt đầu từ yêu cầu dữ liệu, thay vì chọn một factory bất kỳ rồi sửa lỗi cho đến khi chạy được:

1. Dữ liệu có được phép thiếu không, và thiếu được biểu diễn bằng null, không có phần tử/khóa, hay một giá trị cụ thể?
2. Phần tử trùng là có ý nghĩa, cần gộp, hay là lỗi?
3. Với map, khóa được chuẩn hóa theo quy tắc nào trước khi kiểm tra trùng?
4. Nếu hai bản có cùng khóa/mã nhưng khác nội dung, chính sách xung đột là gì?
5. Kết quả có cần thứ tự duyệt cụ thể không?
6. Cần ảnh chụp tách khỏi nguồn hay một view tiếp tục theo nguồn?

### 10.1. Không âm thầm xóa null nếu đó là lỗi dữ liệu

Lọc Objects::nonNull trước List.copyOf có thể là đúng nếu hợp đồng chủ ý bỏ các mục thiếu. Nó cũng có thể che mất một bản ghi sai mà người dùng cần được báo.

Tương tự, Set.copyOf có thể phù hợp với danh sách tag mà việc lặp lại không quan trọng, nhưng không phù hợp để kiểm tra rằng tệp khai báo không có mục lặp. Hai trường hợp có cùng đầu vào hình thức nhưng yêu cầu nghiệp vụ khác nhau.

Nếu cần lỗi với vị trí, tên trường hoặc mã bản ghi, kiểm tra và báo ở biên nhận dữ liệu. Thông báo lỗi mặc định của factory không phải một định dạng chẩn đoán nghiệp vụ được cam kết.

### 10.2. Ví dụ nhận Collection nhưng từ chối trùng có chủ đích

Nếu yêu cầu là tạo set không cho sửa, đồng thời đầu vào phải không null và không có phần tử trùng, có thể viết một phương thức riêng như sau.

Đây là phương thức đặt trong một lớp có import Collection, Set, LinkedHashSet và Objects; không phải chương trình main độc lập. Phần tử phải có equals/hashCode đúng và không thay đổi những thuộc tính liên quan trong lúc xử lý:

```java
static <T> Set<T> requireDistinct(Collection<? extends T> values) {
    Objects.requireNonNull(values, "values");
    Set<T> unique = new LinkedHashSet<>();
    int index = 0;
    for (T value : values) {
        Objects.requireNonNull(value, "null element at index " + index);
        if (!unique.add(value)) {
            throw new IllegalArgumentException("duplicate at index " + index);
        }
        index++;
    }
    return Set.copyOf(unique);
}
```

Phương thức kiểm tra từng giá trị trước khi tạo kết quả. Khi LinkedHashSet.add trả false, đã gặp phần tử bằng nhau trước đó nên báo lỗi thay vì gộp âm thầm.

LinkedHashSet ở đây là cấu trúc tạm để kiểm tra. Kết quả cuối dùng Set.copyOf nên **không cam kết thứ tự chèn**. Nếu hợp đồng yêu cầu giữ thứ tự, phải chọn cách trả cấu trúc/lớp bọc phù hợp thay vì gán thêm bảo đảm không có trong phương thức này.

Thông báo dùng chỉ số bắt đầu từ 0 để minh họa chẩn đoán. Ứng dụng thực tế có thể dùng số dòng hoặc tên trường, miễn hợp đồng rõ ràng. Đây không phải kiểm tra đệ quy các đối tượng con và cũng không làm phần tử mutable trở nên bất biến.

### 10.3. Với map, kiểm tra trước khi dữ liệu mất đi

Nếu đang xử lý một dãy entry và muốn từ chối khóa lặp, hãy kiểm tra ở bước thu dãy thành map. Map.ofEntries phù hợp khi đã có toàn bộ entry và muốn quy tắc từ chối khóa trùng của factory.

Với Stream, có thể dùng toUnmodifiableMap bản không có hàm gộp nếu kiểu lỗi đó phù hợp, hoặc tự cung cấp cơ chế báo xung đột theo nghiệp vụ. Nếu muốn gộp, dùng bản có hàm gộp với chính sách được xác định.

Một HashMap đã bị put ghi đè không còn đủ thông tin để biết trước đó có hai bản hay không. Việc gọi Map.copyOf sau cùng chỉ cố định trạng thái hiện có; không phải một bước kiểm toán toàn bộ dữ liệu thô.

## 11. Những điều các kiểm tra này không bảo đảm

### 11.1. Không kiểm tra mọi thuộc tính nghiệp vụ

List.of nhận "" vì chuỗi đó không null. Set.of có thể nhận "admin" và "ADMIN" vì chúng không equals. Map.of nhận một số âm làm giá trị nếu kiểu cho phép; nó không biết số đó có hợp lệ với hạn mức nghiệp vụ không.

Hợp lệ theo factory chỉ nói đã thỏa các điều kiện của factory. Nó không chứng minh mọi giá trị có ý nghĩa với ứng dụng.

### 11.2. Không tự sao chép sâu hoặc bảo vệ khóa mutable

Các factory lưu phần tử/khóa/giá trị theo hợp đồng của chúng, không tự nhân bản sâu mọi trạng thái bên trong. Một phần tử có thể đổi thuộc tính dù collection không cho thêm/xóa/thay vị trí.

Nếu phần tử của set hoặc khóa của map bị đổi các thuộc tính ảnh hưởng equals/hashCode khi đang được chứa, hành vi tra cứu có thể không còn đúng theo hợp đồng. Không cho sửa cấu trúc không tự cấm các setter của đối tượng khóa.

Chính sách phát hiện trùng vì thế dựa vào một quan hệ bằng nhau đúng và ổn định. Chọn khóa bất biến thường giúp giữ điều kiện đó.

### 11.3. Không tự làm nguồn đọc đồng thời trở nên nhất quán

copyOf tạo kết quả không theo các thay đổi cấu trúc về sau của nguồn, nhưng việc đọc nguồn để tạo kết quả vẫn cần hợp đồng phối hợp nếu nguồn bị nhiều luồng cập nhật.

Một dữ liệu đang thay đổi mà thiếu đồng bộ không được sửa bằng cách chỉ thêm copyOf ở cuối. Bài này dùng dữ liệu tuần tự để tập trung kiểm chứng chính sách null và trùng.

### 11.4. Không chọn thứ tự duyệt hoặc danh tính đối tượng theo nhu cầu

Không dùng thứ tự in của một Set.of trong một lần chạy làm bằng chứng về thứ tự chèn. Không dùng == trên các collection do factory trả về để kiểm tra “đã copy đúng”: factory có thể tái sử dụng đối tượng phù hợp theo hợp đồng.

Nếu cần kết quả thứ tự xác định cho hiển thị, chọn hoặc chuyển sang một mô hình có thứ tự được nêu rõ. Nếu cần so sánh tập phần tử, dùng quan hệ của Set; nếu cần so sánh dãy, dùng quan hệ của List với thứ tự tương ứng.

## 12. Phân biệt các lỗi và tránh dựa vào chi tiết triển khai

| Tình huống | Loại kết quả cần nhớ |
|---|---|
| null ở đầu vào bị List.of/Set.of/Map.of cấm | NullPointerException |
| Phần tử trùng ở Set.of | IllegalArgumentException |
| Khóa trùng ở Map.of/Map.ofEntries | IllegalArgumentException |
| Phần tử trùng ở List.of/List.copyOf | Hợp lệ, vẫn giữ các lần xuất hiện |
| Phần tử trùng ở Set.copyOf/toUnmodifiableSet | Hợp lệ, giữ một đại diện |
| Khóa trùng ở toUnmodifiableMap bản không có hàm gộp | IllegalStateException |
| Cố add/remove/put trên kết quả factory không cho sửa | UnsupportedOperationException |
| Giá trị lặp ở hai khóa Map khác nhau | Hợp lệ nếu các dữ liệu còn lại hợp lệ |

### 12.1. Không cam kết thứ tự phát hiện nhiều lỗi cùng lúc

Nếu một đầu vào vừa có null vừa có phần tử/khóa trùng, không viết kiểm thử chung cho mọi factory rồi yêu cầu một lỗi cụ thể luôn được phát hiện trước. Hợp đồng có thể không quy định thứ tự kiểm tra đó.

Các chương trình trong bài cô lập từng lỗi để kết luận rõ. Khi kiểm tra dữ liệu ứng dụng và cần thứ tự báo lỗi nhất quán, hãy tự thiết kế thứ tự kiểm tra ở biên dữ liệu.

### 12.2. Không phụ thuộc chuỗi thông báo hoặc tên lớp nội bộ

Thông báo của IllegalArgumentException hay IllegalStateException có thể chứa chi tiết hữu ích để gỡ lỗi, nhưng không nên được dùng như một giao thức phân tích dữ liệu ổn định giữa các phiên bản.

Tương tự, tên lớp nội bộ của list/set/map do JDK tạo không phải API để ứng dụng lựa chọn hành vi. Kiểm tra theo hợp đồng phương thức và loại ngoại lệ được quy định.

### 12.3. Không dùng contains(null) làm phép dò hợp lệ tổng quát

Việc collection không chứa null không tự bảo đảm contains(null) luôn trả false. Các API collection có quy tắc về đối số không được chấp nhận; một phép truy vấn như vậy có thể bị từ chối tùy hợp đồng.

Để kiểm tra collection đầu vào trước khi gọi factory, hãy kiểm tra từng phần tử theo chính sách của mình. Sau khi factory đã tạo kết quả hợp lệ với hợp đồng không null, không cần thử một truy vấn null để “xác nhận lại”.

## 13. Bài tập và lời giải

### Bài 1 — Cùng dữ liệu, khác factory

So sánh List.of("a","a"), Set.of("a","a") và Set.copyOf(List.of("a","a")).

**Lời giải:** List có hai phần tử a; Set.of gây IllegalArgumentException; Set.copyOf tạo set kích thước 1. “Không cho sửa” không quyết định chính sách đối với đầu vào trùng.

### Bài 2 — Giá trị Map lặp

Map.of("left",10,"right",10) có hợp lệ không? Map.of("left",10,"left",10) thì sao?

**Lời giải:** trường hợp đầu hợp lệ vì khóa khác nhau. Trường hợp sau gây IllegalArgumentException vì khóa lặp, dù giá trị giống nhau.

### Bài 3 — Null ở cấp nào?

So sánh List.of((String)null), List.of((String[])null) và List.<String[]>of(new String[]{null}).

**Lời giải:** hai trường hợp đầu gây NullPointerException; một trường hợp là phần tử null, trường hợp còn lại là mảng varargs null. Trường hợp thứ ba tạo list một phần tử là mảng không null, bên trong mảng có null. Factory không kiểm tra đệ quy mọi ô của phần tử mảng.

### Bài 4 — Entry chưa tạo xong

Trong Map.ofEntries(Map.entry("k",null)), lời gọi nào có thể báo lỗi trước?

**Lời giải:** Map.entry("k",null) gây NullPointerException khi được đánh giá để tạo đối số. Chưa có entry hợp lệ để truyền vào Map.ofEntries. Một entry lấy từ nguồn khác có khóa/giá trị null cũng sẽ bị ofEntries từ chối khi nó trích dữ liệu.

### Bài 5 — Hai đối tượng bằng nhau, khác mô tả

Hai Label khác tham chiếu có cùng id và equals/hashCode dựa trên id, nhưng khác text. Có thể dùng Set.copyOf rồi chắc chắn lấy được text của Label đầu tiên không?

**Lời giải:** không. Kết quả chỉ giữ một đại diện trong nhóm bằng nhau và không cam kết quy tắc “bản đầu thắng”. Nếu mô tả khác nhau cần được xử lý, phải định nghĩa phát hiện xung đột hoặc tiêu chí chọn bản.

### Bài 6 — Hash code giống nhau

Hai String khác nội dung có cùng hashCode. Có bắt buộc Set.of từ chối chúng không?

**Lời giải:** không. Trùng được xác định theo quan hệ bằng nhau, không phải chỉ theo hashCode. "FB" và "Ea" ở ví dụ cùng hash nhưng đều tồn tại trong set.

### Bài 7 — Lỗi phát hiện quá muộn

Đọc hai dòng cấu hình k=1, k=2 bằng hai lần put vào HashMap, rồi gọi Map.copyOf. Có nhận được lỗi khóa trùng từ hai dòng đầu vào không?

**Lời giải:** không thể dựa vào bước copyOf để phát hiện lịch sử ấy. Sau put lần hai, map chỉ giữ k=2. Phải kiểm tra khóa lặp lúc chuyển các dòng cấu hình thành map, trước khi thông tin bị ghi đè.

### Bài 8 — Collector có cùng giá trị

Stream có "x","x", dùng toUnmodifiableMap(identity,x→1) không có hàm gộp. Hai giá trị bằng nhau có làm collector chấp nhận không?

**Lời giải:** không. Khóa trùng gây IllegalStateException. Nếu muốn đếm số lần xuất hiện, có thể dùng hàm gộp cộng số đếm. Đó là một chính sách được viết rõ, không phải hành vi mặc định.

### Bài 9 — Thay Stream.toList bằng collector

Một stream có một phần tử null. Có thể đổi stream.toList() sang collect(toUnmodifiableList()) rồi mặc định chính sách dữ liệu không đổi không?

**Lời giải:** không. toUnmodifiableList từ chối null; ví dụ Stream.toList vẫn giữ null. Cả hai cho kết quả không sửa được nhưng điều kiện đầu vào khác nhau.

### Bài 10 — Chuyển từ singleton sang of

Code cũ dùng Collections.singletonMap(null,null). Có thể thay trực tiếp bằng Map.of(null,null) khi muốn viết ngắn hơn không?

**Lời giải:** không giữ nguyên hành vi. singletonMap của ví dụ cho phép ánh xạ đó; Map.of từ chối null. Trước khi thay cần xác định dữ liệu thiếu phải được biểu diễn và kiểm tra như thế nào.

### Bài 11 — Cùng nội dung mảng

a và b là hai int[] khác đối tượng, cùng chứa 1,2. Set.of(a,b) có bắt buộc xem chúng là trùng không?

**Lời giải:** không. Mảng thông thường dùng quan hệ bằng nhau theo danh tính đối tượng, không tự so nội dung. Lặp đúng cùng tham chiếu a hai lần lại là trùng. Nếu cần khóa/tập theo nội dung, hãy dùng một biểu diễn có equals/hashCode theo nội dung phù hợp.

### Bài 12 — Chuẩn hóa trước tạo Map

Hai mã "A" và "a" được coi là một mã sau bước chuyển sang chữ thường. Nếu tạo Map trực tiếp từ dữ liệu thô không báo trùng, đã chứng minh dữ liệu hợp lệ theo nghiệp vụ chưa?

**Lời giải:** chưa. Quan hệ khóa sau chuẩn hóa khác với equals trên dữ liệu thô. Cần chuẩn hóa theo chính sách xác định, rồi kiểm tra xung đột trên khóa đã chuẩn hóa trước khi cố định kết quả.

## 14. Cách tự kiểm chứng khi dùng factory trong dự án

Một bộ kiểm tra nhỏ nên tách các tình huống sau thành ca độc lập:

1. Đầu vào hợp lệ rỗng, một phần tử và nhiều phần tử.
2. Tham chiếu nguồn null đối với copyOf.
3. Phần tử null; riêng Map thử cả khóa null và giá trị null.
4. Phần tử/khóa bằng nhau nhưng khác tham chiếu.
5. Phần tử cùng hash nhưng không equals.
6. Giá trị Map lặp với các khóa khác nhau.
7. Chính sách trùng: từ chối, giữ mọi lần xuất hiện hay gộp.
8. Các trường hợp varargs/mảng nếu API của mình nhận mảng.
9. Không so thứ tự Set/Map trừ khi đã chọn hợp đồng thứ tự riêng.
10. Không dựa vào thứ tự phát hiện của một đầu vào cố ý có nhiều lỗi khác nhau.

Nếu dữ liệu có các đối tượng mutable, thêm kiểm tra về biến đổi thuộc tính hoặc chuyển sang giá trị bất biến theo nhu cầu. Nếu lấy từ nhiều luồng, kiểm tra cả thời điểm và tính nhất quán của việc tạo kết quả.

Các chương trình trong bài in tên ngoại lệ hoặc giá trị xác định để phân biệt hợp đồng, không dựa vào tên lớp nội bộ, thông báo lỗi hay thứ tự ngẫu nhiên của set/map.

## 15. Tài liệu đối chiếu

- [List — các factory of và copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html).
- [Set.copyOf — gộp phần tử trùng và chọn đại diện](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#copyOf(java.util.Collection)).
- [Map — of, ofEntries, entry và copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).
- [Collectors — các phép thu không cho sửa](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html).
- [Hướng dẫn tạo list, set và map không cho sửa](https://docs.oracle.com/en/java/javase/17/core/creating-immutable-lists-sets-and-maps.html).
- [Collections — singleton, nCopies và các lớp bọc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unmodifiable-collections-007">emptyList, singleton, nCopies và các trường hợp dùng</a>

<details>
<summary>Xem chi tiết</summary>

# emptyList, singleton, nCopies và các trường hợp dùng

Không phải kết quả collection nào cũng cần một danh sách được xây bằng nhiều lần `add`. Có những phương thức luôn trả về không có phần tử, có đúng một phần tử, hoặc một giá trị lặp lại ở nhiều vị trí. Lớp tiện ích `java.util.Collections` cung cấp các cách tạo diễn đạt trực tiếp những hình dạng dữ liệu ấy.

Ba tên cần nhận diện là `emptyList`, `singleton` và `nCopies`. Chúng không có cùng kiểu kết quả: `emptyList` trả list rỗng; `singleton` trả **set** một phần tử; `nCopies` trả list có `n` vị trí cùng giữ phần tử đã truyền. Họ `singletonList` và `singletonMap` bổ sung trường hợp một phần tử list và một ánh xạ map.

Phần quan trọng nhất của bài là hiểu quyền sửa và sự chia sẻ tham chiếu. `nCopies(3, object)` không gọi hàm tạo ba lần và không nhân bản sâu đối tượng. Một kết quả không cho thêm, xóa hay thay ô vẫn có thể chứa đối tượng sửa được.

Các ví dụ dùng Java 17, không cần thư viện ngoài. Cần biết `List`, `Set`, `Map` và tham chiếu đối tượng trước khi chạy chương trình.

## 1. Chọn cách tạo theo hình dạng kết quả

Hãy bắt đầu bằng ý nghĩa dữ liệu cần trả hoặc truyền:

| Nhu cầu | Cách tạo phù hợp | Kiểu kết quả | Số phần tử/ánh xạ |
| --- | --- | --- | --- |
| Không có phần tử trong một dãy | `Collections.emptyList()` | `List<T>` | 0 |
| Không có thành viên trong một tập | `Collections.emptySet()` | `Set<T>` | 0 |
| Không có ánh xạ | `Collections.emptyMap()` | `Map<K,V>` | 0 |
| Một thành viên của tập | `Collections.singleton(value)` | `Set<T>` | 1 |
| Một phần tử của dãy | `Collections.singletonList(value)` | `List<T>` | 1 |
| Một cặp khóa–giá trị | `Collections.singletonMap(key, value)` | `Map<K,V>` | 1 |
| Cùng một phần tử lặp ở `n` vị trí | `Collections.nCopies(n, value)` | `List<T>` | `n` nếu `n >= 0` |

Đây là các phương thức tạo tĩnh. Người dùng kết quả làm việc qua kiểu giao diện, không cần phụ thuộc lớp triển khai bên trong.

Tài liệu gọi các kết quả này là collection “immutable”. Khi áp dụng, cần hiểu đúng phạm vi: cấu trúc phần tử/ánh xạ của kết quả không cho sửa; đối tượng được giữ bên trong không tự bị đóng băng hay sao chép sâu. Xem các khai báo và hợp đồng trong [API Collections, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html).

Không chọn theo độ ngắn tên hàm. Nếu API cần list có chỉ số 0, `singleton` không thay thế `singletonList` vì kiểu trả về khác. Nếu mục tiêu là có `n` đối tượng mới độc lập, `nCopies` không đáp ứng dù tên có chữ “copies”.

## 2. `emptyList`: một list tồn tại nhưng không có phần tử

### Rỗng khác với `null`

~~~java
List<String> result = Collections.emptyList();
~~~

Đây là mã minh họa trong một phương thức có đủ import. `result` là một đối tượng list hợp lệ, có `size() == 0` và `isEmpty() == true`. Có thể duyệt bằng vòng lặp; vòng lặp sẽ không thực hiện thân lần nào.

`null` lại có nghĩa không có đối tượng list để gọi phương thức. Một API hứa “trả về tất cả nhãn tìm được, có thể không có nhãn” thường diễn đạt hợp lý bằng một list rỗng. Người gọi có thể dùng cùng một đoạn duyệt cho cả trường hợp có và không có kết quả.

Không dùng list rỗng để che lỗi. Nếu truy vấn thất bại, chưa tải dữ liệu hoặc người gọi không có quyền truy cập, việc biến mọi trường hợp thành “không có phần tử” có thể làm mất thông tin quan trọng. Hợp đồng của API phải phân biệt trạng thái nghiệp vụ cần thiết.

### Rỗng không có nghĩa là danh sách để tiếp tục thêm

Đoạn cố ý sai:

~~~java
List<String> names = Collections.emptyList();
names.add("Lan"); // UnsupportedOperationException
~~~

Nếu cần tích lũy dần các tên, tạo `new ArrayList<>()`. Nếu cần trả một kết quả không có phần tử và không cho người nhận thêm vào chính kết quả ấy, `emptyList` diễn đạt rõ ý định.

Cũng không có phần tử ở vị trí 0. `emptyList.get(0)` không trả `null`; nó ném `IndexOutOfBoundsException`. “Không có phần tử” khác với “có một phần tử mà giá trị của nó là `null`”.

### Có kiểu dù chưa có phần tử

~~~java
List<String> a = Collections.emptyList();
List<String> b = Collections.<String>emptyList();
~~~

Cách đầu dùng kiểu đích để suy luận `T`. Cách sau ghi rõ tham số kiểu khi ngữ cảnh cần làm rõ. Không phải đưa một phần tử giả vào list để giúp trình biên dịch hiểu kiểu.

Nên dùng phương thức có generic thay vì trường raw type `Collections.EMPTY_LIST` trong mã mới. Trường raw có thể dẫn tới cảnh báo chuyển đổi không được kiểm tra; `emptyList()` cho phép giữ thông tin kiểu trong mã nguồn.

Không kiểm tra list rỗng bằng `result == Collections.emptyList()`. Cách triển khai có thể tái sử dụng đối tượng; sự tồn tại của tái sử dụng không biến định danh đối tượng thành điều kiện nghiệp vụ. Dùng `isEmpty()` khi cần biết có phần tử không.

### Hai họ hàng thường dùng

~~~java
Set<String> noPermissions = Collections.emptySet();
Map<String, Integer> noOverrides = Collections.emptyMap();
~~~

Ý nghĩa “rỗng” giống nhau ở số lượng nhưng thao tác và hợp đồng bình đẳng vẫn theo từng giao diện. Một list rỗng và một set rỗng không phải hai kiểu có thể thay nhau chỉ vì đều có `size() == 0`.

## 3. `singleton`: một phần tử của set, không phải mẫu thiết kế Singleton

### Tên hàm nói về số thành viên

~~~java
Set<String> requested = Collections.singleton("report");
~~~

Set có một thành viên `"report"`. Thường có thể truyền nó vào một API nhận nhiều mã khi lần gọi hiện tại chỉ cần một mã.

Từ “singleton” ở đây không khẳng định chỉ có một đối tượng trên toàn ứng dụng, không quy định một hàm `getInstance` và không biến phần tử thành biến toàn cục. Nó nói rằng tập kết quả có đúng một phần tử.

Nếu một phương thức nhận `Set<String>`, cách tạo này giúp diễn đạt yêu cầu một thành viên mà không cần tạo `HashSet` rồi `add`. Nếu phương thức nhận `List<String>`, cần chọn `singletonList` thay vì cố ép kiểu.

### `singletonList` có vị trí 0

~~~java
List<String> oneStep = Collections.singletonList("validate");
~~~

List có kích thước 1 và `oneStep.get(0)` là `"validate"`. Nó không cho thay bằng `set(0, other)`, không cho thêm phần tử thứ hai hoặc xóa phần tử duy nhất.

Đừng nhầm với một list kích thước cố định còn hỗ trợ thay phần tử. Từ “một phần tử” chưa tự quyết định quyền sửa; quyền sửa ở đây đến từ hợp đồng của factory.

### `singletonMap` có một ánh xạ

~~~java
Map<String, String> override =
        Collections.singletonMap("mode", "fast");
~~~

Map có đúng một khóa `"mode"` ánh xạ tới `"fast"`. Nó không cho `put` để thay giá trị hay thêm khóa khác.

Hữu ích khi truyền đúng một tùy chọn cho API nhận map, tạo dữ liệu kiểm thử có một ánh xạ, hoặc công bố một bảng tra cứu chỉ có một mục. Khi cần cập nhật tùy chọn sau đó, hãy tạo map sửa được phù hợp thay vì dựa vào kiểu giao diện `Map` rồi giả định `put` sẽ thành công.

### Phần tử vẫn có thể là đối tượng sửa được

Nếu `singletonList` giữ một `StringBuilder`, gọi `get(0).append(...)` sửa đối tượng ấy. Nếu `singletonMap` giữ một list làm giá trị, list đó có thể tiếp tục nhận phần tử qua tham chiếu khác.

Cấu trúc một phần tử không thay đổi nhưng trạng thái quan sát bên trong có thể đổi. Đối với phần tử set hoặc khóa map, cần đặc biệt giữ ổn định dữ liệu tham gia `equals`/`hashCode` trong thời gian đối tượng đang được lưu. Đây là yêu cầu của [Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html) và [Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html), không được loại bỏ bởi số phần tử bằng một.

## 4. `nCopies`: lặp một phần tử ở nhiều vị trí

### Số lượng và giá trị lặp

~~~java
List<String> states = Collections.nCopies(4, "pending");
~~~

Kết quả có kích thước 4. Mỗi vị trí hợp lệ từ 0 đến 3 trả cùng phần tử đã truyền. Khi in, nó biểu diễn `[pending, pending, pending, pending]`.

Quy tắc với số lượng:

- `n > 0`: có `n` phần tử.
- `n == 0`: list rỗng.
- `n < 0`: ném `IllegalArgumentException`.

Chỉ số hợp lệ vẫn theo `List`: `0 <= index < size()`. `nCopies(0, value)` không có vị trí 0 dù đã truyền một giá trị vào lời gọi.

Kết quả không hỗ trợ thay phần tử bằng `set`. Nếu cần từng vị trí nhận trạng thái khác nhau về sau, hãy tạo vật chứa làm việc từ dữ liệu ban đầu, như phần tiếp theo.

### “Copies” không có nghĩa gọi hàm tạo nhiều lần

Xét:

~~~java
List<StringBuilder> buffers =
        Collections.nCopies(3, new StringBuilder("start"));
~~~

Biểu thức `new StringBuilder("start")` được đánh giá một lần để tạo đối số. `nCopies` nhận một tham chiếu ấy rồi biểu diễn nó ở ba vị trí.

Có thể mô tả quan hệ như sau:

~~~text
vị trí 0 ─┐
vị trí 1 ─┼──> cùng một StringBuilder("start")
vị trí 2 ─┘
~~~

Không có ba `StringBuilder` khác nhau. Sửa qua `buffers.get(0)` sẽ được quan sát khi đọc ở vị trí 1 và 2 vì cả ba cùng trỏ tới một đối tượng.

Đối với chuỗi `String` hoặc một giá trị đã bất biến, dùng chung tham chiếu thường là điều mong muốn. Đối với bộ đệm, list con, đối tượng phiên làm việc hay ô có setter, điều đó dễ tạo lỗi dùng chung ngoài ý muốn.

### Biểu diễn gọn không xóa chi phí của các bước sau

API mô tả dữ liệu biểu diễn của `nCopies` là nhỏ và chỉ giữ một tham chiếu tới phần tử. Vì thế không nên tưởng rằng nó luôn cấp phát một mảng gồm `n` tham chiếu ngay khi gọi. [API nCopies, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#nCopies(int,T)).

Tuy nhiên, nếu sau đó thực sự duyệt từng vị trí, chuyển thành mảng hoặc tạo `ArrayList` chứa toàn bộ `n` vị trí, các bước ấy vẫn phải xử lý dữ liệu theo công việc mà chúng làm. Một biểu diễn lặp gọn không bảo đảm mọi thao tác sau đều rẻ hoặc không cấp phát.

Chương trình minh chứng tạo một list lặp có một triệu vị trí nhưng chỉ in `size` và đọc vị trí cuối. Nó không in cả một triệu phần tử và không tạo bản sao lớn để chứng minh khái niệm này.

## 5. Tạo các ô ban đầu cho một list sửa được

Một ứng dụng có thể cần bốn ô trạng thái ban đầu là `"pending"`, sau đó đổi từng ô và thêm ô mới.

~~~java
List<String> slots =
        new ArrayList<>(Collections.nCopies(4, "pending"));

slots.set(2, "done");
slots.add("extra");
~~~

`nCopies` tạo dữ liệu ban đầu; hàm tạo `ArrayList(Collection)` tạo vật chứa mới có các phần tử ấy. Vật chứa mới hỗ trợ `set` và `add`.

Không có sự thay đổi quyền sửa của list nguồn. List nguồn do `nCopies` tạo vẫn không sửa được; đối tượng `ArrayList` là một vật chứa khác.

Hàm tạo sao chép phần tử theo thứ tự duyệt của collection nguồn. Với phần tử sửa được, các tham chiếu vẫn có thể được chia sẻ; tạo list mới không tự tạo lại từng phần tử. [Các hàm tạo của ArrayList, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

### Cách này khác với chỉ đặt capacity

~~~java
List<String> reserved = new ArrayList<>(4);
~~~

Đối số 4 trong hàm tạo này là sức chứa ban đầu (capacity), không phải số phần tử hiện có. `reserved.size()` vẫn là 0. Do đó `reserved.set(2, "done")` không có ô thứ 2 để thay.

So sánh:

| Biểu thức | `size()` lúc đầu | Thay bằng `set(0, ...)` | Có thể thêm? |
| --- | --- | --- | --- |
| `new ArrayList<String>(4)` | 0 | Chưa có vị trí 0 | Có |
| `Collections.nCopies(4, "pending")` | 4 | Không hỗ trợ sửa | Không |
| `new ArrayList<>(Collections.nCopies(4, "pending"))` | 4 | Có | Có |

Một cách khác là tạo list rỗng rồi `add` đủ số phần tử. Lựa chọn phụ thuộc việc thật sự cần “các ô đã tồn tại” hay chỉ cần dự trữ bộ nhớ để bổ sung dần.

Đừng dùng `nCopies` chỉ để làm tăng capacity khi không cần các phần tử ban đầu; nó thay đổi cả `size` của bản sao khi dùng để khởi tạo `ArrayList`.

## 6. Đệm dữ liệu và tương tác với các thuật toán list

### Thêm nhiều lần cùng giá trị bằng `addAll`

~~~java
List<String> columns = new ArrayList<>(List.of("Ada", "Lin"));
columns.addAll(Collections.nCopies(2, "-"));
~~~

List đích trở thành `[Ada, Lin, -, -]`. Dữ liệu lặp chỉ làm nguồn cho `addAll`; list đích phải hỗ trợ thêm.

Nếu muốn đệm tới độ dài đích, tính **số ô thiếu**, không truyền nhầm tổng độ dài cần có:

~~~java
int targetSize = 6;
int missing = targetSize - columns.size();
if (missing > 0) {
    columns.addAll(Collections.nCopies(missing, "-"));
}
~~~

Đoạn này chỉ thêm khi thiếu; nó không cắt bớt nếu list đã dài hơn đích. Nếu yêu cầu là độ dài chính xác, trường hợp dài hơn phải được xử lý theo quy tắc nghiệp vụ riêng.

Đối với đầu vào kích thước từ bên ngoài, kiểm tra giới hạn có ý nghĩa cho ứng dụng trước khi tạo nhiều phần tử. Không chuyển mọi số âm thành 0 nếu số âm thật sự là lỗi cấu hình cần báo.

### `Collections.copy` cần số ô sẵn có và quyền thay ô

`Collections.copy(dest, src)` thay các phần tử đích theo chỉ số, không tự `add` để nới list đích. `dest.size()` phải ít nhất bằng `src.size()` và đích phải hỗ trợ thao tác thay phần tử.

Vì thế hai lỗi độc lập có thể xuất hiện:

1. `new ArrayList<>(source.size())` có capacity nhưng `size` bằng 0: chưa có đủ ô, nên không làm đích hợp lệ cho phép copy nguồn không rỗng.
2. `Collections.nCopies(source.size(), initial)` có đủ ô nhưng không cho thay, nên cũng chưa là đích hợp lệ.

Cách chuẩn bị phù hợp cho thí nghiệm này:

~~~java
List<String> source = List.of("A", "B", "C");
List<String> destination =
        new ArrayList<>(Collections.nCopies(source.size(), "?"));

Collections.copy(destination, source);
~~~

Kết quả đích là `[A, B, C]`. Nếu chỉ muốn một bản sao mới ngay từ đầu, `new ArrayList<>(source)` đơn giản hơn. Ví dụ dùng `copy` ở đây để hiểu yêu cầu của thuật toán thay vị trí và vai trò của những ô khởi tạo.

`Collections.copy` cũng không phải thao tác nhân bản sâu các phần tử; nó chuyển các giá trị phần tử sang các vị trí đích theo hợp đồng list.

### `nCopies` và `fill` giải quyết hai việc khác nhau

`nCopies` tạo một list biểu diễn sự lặp. `Collections.fill(existing, value)` thay phần tử ở các vị trí hiện có của một list đã tồn tại. `fill` không làm `size` từ 0 thành `n`.

Nếu list đang rỗng, việc fill không tự tạo ô. Nếu list không hỗ trợ thay phần tử, việc fill để thực sự thay dữ liệu không phải một cách phá quyền sửa. Chọn cách tạo hoặc thuật toán theo thao tác cần thực hiện, không chỉ theo ý tưởng chung “điền giá trị”.

## 7. Bẫy list lồng nhau: nhiều vị trí cùng một hàng

Một ý tưởng dễ xuất hiện khi tạo bảng ba hàng:

~~~java
List<List<String>> rows = new ArrayList<>(
        Collections.nCopies(3, new ArrayList<String>()));
~~~

List ngoài là `ArrayList` nên có thể sửa. Nhưng ba phần tử bên trong đều là cùng **một** `ArrayList<String>`. Lớp bọc sao chép ở ngoài không thay quan hệ tham chiếu này.

~~~java
rows.get(0).add("X");
~~~

Sau lời gọi, in `rows` cho ra `[[X], [X], [X]]`. Không phải vòng lặp trong `add` đã sửa ba hàng. Chỉ một hàng thực sự tồn tại và nó được hiển thị qua ba vị trí.

Để có ba hàng độc lập, tạo một đối tượng hàng trong mỗi lượt:

~~~java
List<List<String>> rows = new ArrayList<>();
for (int i = 0; i < 3; i++) {
    rows.add(new ArrayList<>());
}
~~~

Bây giờ thêm `"X"` vào hàng đầu chỉ làm kết quả `[[X], [], []]`.

Điều quyết định là **vị trí đặt lời gọi tạo đối tượng mới**. Nó phải được thực hiện riêng cho mỗi phần tử. Không đủ để chỉ bao list ngoài bằng `new ArrayList<>(...)` hoặc gọi lại một phương thức vẫn trả cùng đối tượng con.

Mẫu sai tương tự còn xuất hiện khi tạo nhiều bộ đệm bằng một `StringBuilder`, nhiều giỏ hàng bằng một đối tượng giỏ, hoặc nhiều cấu hình chỉnh sửa bằng cùng một bản cấu hình có setter. Trước khi dùng `nCopies`, hãy quyết định việc dùng chung phần tử là chủ đích hay lỗi.

Nếu các hàng chỉ là dữ liệu đã chốt và không sửa được, chia sẻ có thể hoàn toàn phù hợp. “Cùng tham chiếu” không tự là lỗi; lỗi nằm ở việc cần độc lập nhưng lại cho phép cập nhật chung.

## 8. `null`, kiểu dữ liệu và khác biệt với `of`

### Các factory nhỏ này có thể chứa `null`

Các trường hợp sau đều tạo được:

~~~java
Set<String> oneNull = Collections.singleton(null);
List<String> nullItem = Collections.singletonList(null);
Map<String, String> nullPair = Collections.singletonMap(null, null);
List<String> nullSlots = Collections.nCopies(3, null);
~~~

`nullItem` có kích thước 1, không phải 0. `nullSlots` có kích thước 3. `nullPair` có một ánh xạ với khóa `null` và giá trị `null`.

Với map, `get(null) == null` tự nó chưa phân biệt khóa vắng và giá trị `null`; dùng `containsKey` nếu cần xác định có ánh xạ. Chương trình sẽ in cả hai thông tin.

Khả năng chứa `null` không có nghĩa nên dùng `null` trong mọi mô hình. Chỉ dùng khi hợp đồng phần tử và các bước xử lý phía sau hiểu rõ ý nghĩa của nó.

### Đổi sang factory mới có thể đổi hành vi

Các cách viết sau không có cùng chính sách `null`:

| Cách tạo | Với một phần tử `null` |
| --- | --- |
| `Collections.singletonList(null)` | Tạo list kích thước 1 |
| `List.of((String) null)` | `NullPointerException` |
| `Collections.singleton(null)` | Tạo set kích thước 1 |
| `Set.of((String) null)` | `NullPointerException` |
| `Collections.singletonMap(null, null)` | Tạo một ánh xạ |
| `Map.of((String) null, (String) null)` | `NullPointerException` |

`List.of`, `Set.of` và `Map.of` từ chối `null` theo các hợp đồng riêng của chúng. Vì thế chuyển mã cũ từ các factory nhỏ sang `of` cần kiểm tra dữ liệu đầu vào; không coi đó là thay cú pháp không ảnh hưởng hành vi. [Hợp đồng List.of](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#unmodifiable), [Set.of](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#unmodifiable), [Map.of](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#unmodifiable).

Tương tự, `List.copyOf(nullItem)` bị từ chối vì nguồn có phần tử `null`. Một list hợp lệ với factory này chưa chắc hợp lệ khi chuyển qua factory khác.

### Generic có thể cần được ghi rõ

Với nhiều chỗ dùng `null` hoặc kết quả chưa có phần tử, kiểu đích giúp trình biên dịch hiểu kiểu mong muốn:

~~~java
List<String> slots = Collections.nCopies(3, null);
List<String> explicit = Collections.<String>nCopies(3, null);
~~~

Cả hai mô tả list chuỗi theo kiểu khai báo. Không cần ép kiểu raw để làm việc đó.

Cũng cần xác định phần tử đang là gì. `Collections.singletonList(array)` luôn tạo list có một phần tử là mảng đã truyền; nó không có dạng varargs để tự lấy mỗi ô mảng làm một phần tử. Dùng `Arrays.toString` khi muốn in nội dung mảng đó, không dự đoán từ chuỗi hiển thị mặc định của một đối tượng mảng.

## 9. Chương trình minh chứng chạy được

Lưu toàn bộ mã vào **`SmallCollectionsDemo.java`**. Hàm `rejects` chỉ chấp nhận đúng loại ngoại lệ được mong đợi. Nếu thao tác bất ngờ thành công hoặc ném một loại khác, chương trình dừng bằng `AssertionError`.

Các phép kiểm tra quyền sửa đều nhằm những thay đổi thực sự: thêm một phần tử vào list rỗng, thay phần tử đang tồn tại hoặc thay ánh xạ. Chương trình không dùng những thao tác không làm gì như `clear` trên list đã rỗng để suy ra hợp đồng.

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SmallCollectionsDemo {
    private static void rejects(
            String label,
            Class<? extends RuntimeException> expected,
            Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " unexpectedly succeeded");
        } catch (RuntimeException ex) {
            if (!expected.isInstance(ex)) {
                throw new AssertionError(label + " wrong exception", ex);
            }
            System.out.println(label + "=" + ex.getClass().getSimpleName());
        }
    }

    private static List<String> labelsFor(boolean enabled) {
        if (!enabled) {
            return Collections.emptyList();
        }
        return Collections.singletonList("active");
    }

    public static void main(String[] args) {
        List<String> empty = Collections.emptyList();
        System.out.println("empty.isEmpty=" + empty.isEmpty());
        System.out.println("empty.family.sizes="
                + empty.size() + "/"
                + Collections.emptySet().size() + "/"
                + Collections.emptyMap().size());
        rejects("empty.add", UnsupportedOperationException.class,
                () -> empty.add("x"));
        rejects("empty.get", IndexOutOfBoundsException.class,
                () -> empty.get(0));

        Set<String> oneSet = Collections.singleton("solo");
        List<String> oneList = Collections.singletonList("solo");
        Map<String, String> oneMap =
                Collections.singletonMap("mode", "fast");
        System.out.println("singleton.set=" + oneSet);
        System.out.println("singleton.list=" + oneList);
        System.out.println("singleton.map=" + oneMap);
        rejects("singleton.add", UnsupportedOperationException.class,
                () -> oneSet.add("other"));
        rejects("singletonList.set", UnsupportedOperationException.class,
                () -> oneList.set(0, "other"));
        rejects("singletonMap.put", UnsupportedOperationException.class,
                () -> oneMap.put("mode", "safe"));

        Set<String> nullSet = Collections.singleton(null);
        List<String> nullList = Collections.singletonList(null);
        Map<String, String> nullMap =
                Collections.singletonMap(null, null);
        System.out.println("null.set.contains=" + nullSet.contains(null));
        System.out.println("null.list=" + nullList);
        System.out.println("null.map.containsKey=" + nullMap.containsKey(null));
        System.out.println("null.map.value=" + nullMap.get(null));

        List<String> repeated = Collections.nCopies(3, "idle");
        System.out.println("repeat=" + repeated);
        System.out.println("repeat.size=" + repeated.size());
        rejects("repeat.set", UnsupportedOperationException.class,
                () -> repeated.set(1, "done"));
        System.out.println("repeat.zero.size="
                + Collections.nCopies(0, "unused").size());
        rejects("repeat.negative", IllegalArgumentException.class,
                () -> Collections.nCopies(-1, "idle"));
        System.out.println("repeat.null="
                + Collections.<String>nCopies(2, null));

        StringBuilder shared = new StringBuilder("start");
        List<StringBuilder> aliases = Collections.nCopies(3, shared);
        System.out.println("repeat.shared="
                + (aliases.get(0) == aliases.get(2)));
        aliases.get(0).append("!");
        System.out.println("repeat.afterEdit=" + aliases);

        List<String> slots =
                new ArrayList<>(Collections.nCopies(4, "pending"));
        slots.set(2, "done");
        slots.add("extra");
        System.out.println("slots=" + slots);

        List<String> padded = new ArrayList<>(List.of("Ada", "Lin"));
        padded.addAll(Collections.nCopies(2, "-"));
        System.out.println("padding=" + padded);

        List<String> source = List.of("A", "B", "C");
        List<String> capacityOnly = new ArrayList<>(3);
        System.out.println("capacity.size=" + capacityOnly.size());
        rejects("capacity.copy", IndexOutOfBoundsException.class,
                () -> Collections.copy(capacityOnly, source));
        List<String> destination =
                new ArrayList<>(Collections.nCopies(3, "?"));
        Collections.copy(destination, source);
        System.out.println("copy.destination=" + destination);
        rejects("copy.readonlyDestination", UnsupportedOperationException.class,
                () -> Collections.copy(
                        Collections.nCopies(3, "?"), source));

        List<List<String>> sharedRows = new ArrayList<>(
                Collections.nCopies(3, new ArrayList<String>()));
        sharedRows.get(0).add("X");
        System.out.println("grid.shared=" + sharedRows);

        List<List<String>> independentRows = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            independentRows.add(new ArrayList<>());
        }
        independentRows.get(0).add("X");
        System.out.println("grid.independent=" + independentRows);

        List<String> compact = Collections.nCopies(1_000_000, "same");
        System.out.println("compact.size=" + compact.size());
        System.out.println("compact.last=" + compact.get(999_999));

        System.out.println("return.none=" + labelsFor(false));
        System.out.println("return.one=" + labelsFor(true));
        rejects("List.of.null", NullPointerException.class,
                () -> List.of((String) null));
        rejects("List.copyOf.null", NullPointerException.class,
                () -> List.copyOf(nullList));
    }
}
~~~

Biên dịch và chạy tại thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 SmallCollectionsDemo.java
java SmallCollectionsDemo
~~~

Cần JDK 17 trở lên cho lệnh biên dịch này. `--release 17` giới hạn cú pháp và API ở Java 17; mã đã được biên dịch với tùy chọn ấy và chạy trên OpenJDK 24.0.2, thoát thành công. Stdout chính xác:

~~~text
empty.isEmpty=true
empty.family.sizes=0/0/0
empty.add=UnsupportedOperationException
empty.get=IndexOutOfBoundsException
singleton.set=[solo]
singleton.list=[solo]
singleton.map={mode=fast}
singleton.add=UnsupportedOperationException
singletonList.set=UnsupportedOperationException
singletonMap.put=UnsupportedOperationException
null.set.contains=true
null.list=[null]
null.map.containsKey=true
null.map.value=null
repeat=[idle, idle, idle]
repeat.size=3
repeat.set=UnsupportedOperationException
repeat.zero.size=0
repeat.negative=IllegalArgumentException
repeat.null=[null, null]
repeat.shared=true
repeat.afterEdit=[start!, start!, start!]
slots=[pending, pending, done, pending, extra]
padding=[Ada, Lin, -, -]
capacity.size=0
capacity.copy=IndexOutOfBoundsException
copy.destination=[A, B, C]
copy.readonlyDestination=UnsupportedOperationException
grid.shared=[[X], [X], [X]]
grid.independent=[[X], [], []]
compact.size=1000000
compact.last=same
return.none=[]
return.one=[active]
List.of.null=NullPointerException
List.copyOf.null=NullPointerException
~~~

### Đối chiếu kết quả với cơ chế

**`empty.isEmpty` và `empty.family.sizes`:** có các vật chứa hợp lệ, đều có số phần tử/ánh xạ bằng 0. `empty.add` bị chặn; `empty.get` cho thấy rỗng không có vị trí 0.

**Ba dòng `singleton`:** tên `singleton` không tự hàm ý list. Set, list và map được tạo bằng đúng ba hàm khác nhau. Các thao tác sửa thực sự đều bị từ chối.

**Nhóm `null`:** một set chứa `null` vẫn có thể tìm thành viên `null`; list chứa một phần tử `null` vẫn có một vị trí. Map có khóa `null` trả giá trị `null` nhưng `containsKey` xác nhận ánh xạ tồn tại.

**Nhóm `repeat`:** số lượng bằng 3, 0 và âm được thử riêng. Danh sách lặp cho phép `null`, nhưng không cho `set`. Phép so `==` giữa hai phần tử trong thí nghiệm này kiểm tra đúng quan hệ cùng tham chiếu mà `nCopies` tạo ra, không dùng để dự đoán định danh của chính các list factory.

**`repeat.afterEdit`:** cùng một `StringBuilder` đã đổi thành `"start!"`, nên ba vị trí đều biểu diễn văn bản mới. Không có lời gọi nào thay ô trong list.

**`slots` và `padding`:** dữ liệu lặp được dùng để khởi tạo hoặc nối vào list đích sửa được. Các giá trị chuỗi được chia sẻ an toàn vì không có thao tác đổi nội dung chuỗi.

**`capacity` và `copy`:** chỉ cấp capacity chưa tạo phần tử. Bản `ArrayList` có các ô khởi tạo nhận được dữ liệu nguồn; list `nCopies` dùng trực tiếp làm đích thì không hỗ trợ thay ô.

**Hai dòng `grid`:** sao chép list ngoài không nhân bản các list con. Chỉ vòng lặp tạo `new ArrayList<>()` cho từng lượt mới cho các hàng độc lập trong ví dụ này.

**`compact`:** list lặp biểu diễn một triệu vị trí, nhưng thí nghiệm chỉ đọc hai thông tin nhỏ. Không suy ra từ đây rằng chuyển cả list ấy sang mảng hoặc xuất hết thành văn bản cũng có cùng chi phí.

**`return.none` và `return.one`:** một API có kết quả 0 hoặc 1 phần tử có thể trả collection hợp lệ ở cả hai nhánh. Hai dòng ngoại lệ cuối cho thấy một list có `null` không chuyển trực tiếp sang chính sách không `null` của `List.of`/`List.copyOf` được.

## 10. Các trường hợp dùng thực tế

### Trả không có kết quả nhưng giữ hợp đồng collection

Một bộ phân tích có thể tìm được nhiều cảnh báo hoặc không có cảnh báo. Nếu API hứa trả về một list và không cho sửa kết quả, nhánh không có cảnh báo có thể trả `Collections.emptyList()`.

Cần giữ hợp đồng giữa các nhánh. Nếu nhánh rỗng không cho thêm nhưng nhánh có dữ liệu lại trả trực tiếp `ArrayList` nội bộ, người gọi có thể vô tình phụ thuộc quyền sửa thay đổi theo dữ liệu.

Không nhất thiết ép mọi API trả collection. Nếu nghiệp vụ là “có hoặc không một đối tượng”, một kiểu biểu diễn sự vắng mặt có thể rõ hơn. `emptyList` và `singletonList` hợp lý khi API thực sự được thiết kế để xử lý dãy kết quả, kể cả dãy 0 hoặc 1 phần tử.

### Truyền đúng một giá trị cho API nhận nhiều giá trị

Một API tìm kiếm theo tập mã có thể nhận `Collections.singleton(code)`. Một API xử lý dãy bước có thể nhận `Collections.singletonList(step)`. Một API nhận tùy chọn có thể nhận `Collections.singletonMap(key, value)`.

Điều kiện là API nhận phải tôn trọng quyền đọc, không thử thêm/xóa vào đầu vào. Một tham số có kiểu `Collection` hay `List` không tự cam kết nó sửa được.

Nếu phương thức cần tự sửa dữ liệu để tính toán, nó nên tạo bản làm việc phù hợp hoặc ghi rõ yêu cầu đầu vào sửa được. Hợp đồng rõ giúp người gọi không phải đoán loại list nội bộ.

### Dữ liệu lặp cho khởi tạo và trình bày

`nCopies` phù hợp để tạo các ô ban đầu có cùng giá trị bất biến, thêm dấu đệm vào một dãy, hoặc cung cấp dữ liệu lặp cho thuật toán chỉ đọc.

Nếu các ô sẽ nhận giá trị khác nhau, dùng bản list sửa được từ dữ liệu lặp. Nếu mỗi ô sẽ chứa một đối tượng riêng có trạng thái, tạo từng đối tượng độc lập thay vì dùng `nCopies` như một bộ phát sinh đối tượng.

### Dữ liệu kiểm thử có ý nghĩa

Các hình dạng rỗng, một phần tử và nhiều lần lặp giúp thử biên của thuật toán. Nhưng hãy chọn giá trị lặp theo mục tiêu:

- Muốn thử xử lý nhiều vị trí có cùng giá trị thì `nCopies` có thể phù hợp.
- Muốn thử nhiều đối tượng bằng nhau về dữ liệu nhưng khác tham chiếu thì phải tạo nhiều đối tượng.
- Muốn thử trạng thái từng phần tử thay đổi độc lập thì càng không dùng một đối tượng lặp chung.

Một kiểm thử dùng sai quan hệ tham chiếu có thể che lỗi hoặc tạo ra hiện tượng không đại diện cho dữ liệu thực.

## 11. Các lỗi thường gặp và cách suy luận lại

### “Singleton nghĩa là đối tượng duy nhất toàn hệ thống”

Ở đây nó nghĩa là một phần tử trong collection kết quả. Số phần tử không phải số lần cấp phát, không phải mẫu thiết kế và không phải quyền sở hữu toàn cục.

### “Chỉ có một phần tử thì `set` phải được”

Quyền `set` đến từ hợp đồng list cụ thể. `singletonList` và `nCopies` không phải list chỉ chặn tăng giảm kích thước nhưng vẫn cho thay phần tử.

Nếu cần thay, dùng một bản làm việc sửa được. Đừng thử ép kiểu sang lớp khác để vượt hợp đồng.

### “Rỗng là một ô `null`”

`emptyList()` có `size() == 0`. `singletonList(null)` có `size() == 1`. `nCopies(3, null)` có `size() == 3`. Ba hình dạng khác nhau và tác động tới mọi vòng lặp, phép truy cập chỉ số và thuật toán phía sau.

### “Tạo `ArrayList` ở ngoài sẽ làm mọi phần tử độc lập”

Hàm tạo list sao chép các tham chiếu phần tử. Trong trường hợp hàng lồng nhau, nó vẫn giữ nhiều tham chiếu tới cùng hàng. Muốn độc lập phải thiết kế đúng bước tạo/sao chép phần tử.

### “Không ném lỗi với thao tác không làm gì nghĩa là sửa được”

Một số thao tác tùy chọn hoặc thuật toán sửa có thể không ném ngoại lệ khi lời gọi thực tế không tạo thay đổi. Đừng lấy `clear` trên list rỗng, loại bỏ phần tử vắng hoặc sắp xếp dữ liệu không cần đổi làm cách nhận diện quyền sửa.

Tài liệu của `Collections` nêu việc các thuật toán sửa có thể ném hoặc không ném khi lời gọi không có tác dụng. Hợp đồng API và các thao tác thực sự cần cho bài toán mới là căn cứ.

### “Không sửa được thì mọi dữ liệu chia sẻ đều an toàn đồng thời”

Một `singletonList` chứa đối tượng đang bị sửa hoặc `nCopies` lặp một đối tượng có trạng thái vẫn chia sẻ chính trạng thái ấy. Việc list không cho thay ô không điều phối các lần ghi vào phần tử.

Nếu cần chia sẻ dữ liệu ổn định, chọn phần tử bất biến hoặc áp dụng một cơ chế phối hợp phù hợp cho dữ liệu sửa được. Cách phân biệt vật chứa và phần tử được giải thích trong [Collection, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#unmodcoll).

### “Có số lượng `n` thì mọi thao tác đều giống một `ArrayList` kích thước `n`”

Hai cấu trúc có thể bằng nhau về phần tử khi so `equals` nhưng khác quyền sửa và cách biểu diễn. Đừng viết mã phụ thuộc tên lớp nội bộ hoặc kỳ vọng mọi thao tác có cùng chi phí.

Một list lặp gọn cũng có thể dẫn tới lượng công việc lớn nếu bạn xuất toàn bộ nội dung hoặc vật chất hóa mọi vị trí. Chọn thuật toán theo thao tác thực tế.

## 12. Thực hành có đáp án

### Bài 1 — Ba dạng không có dữ liệu giống nhau không?

So sánh:

~~~java
List<String> a = Collections.emptyList();
List<String> b = Collections.singletonList(null);
List<String> c = Collections.nCopies(3, null);
~~~

**Câu hỏi:** Kích thước từng list là bao nhiêu? List nào có thể gọi `get(0)` mà không lỗi chỉ số?

**Đáp án:** Kích thước lần lượt là 0, 1 và 3. `a.get(0)` ném `IndexOutOfBoundsException`; `b.get(0)` và `c.get(0)` đều trả `null`. Có một phần tử `null` vẫn là có một vị trí trong dãy, khác với dãy rỗng.

### Bài 2 — Cần một list cho API có chỉ số

Một hàm nhận `List<String>` và đọc phần tử ở vị trí 0 sau khi kiểm tra kích thước.

**Câu hỏi:** Có thể truyền trực tiếp `Collections.singleton("x")` không? Cách tạo nào trong bài đúng kiểu?

**Đáp án:** Không, vì `singleton` trả `Set<String>`. Dùng `Collections.singletonList("x")`. Không ép một set thành list chỉ vì nó có đúng một phần tử; kiểu và hợp đồng truy cập khác nhau.

### Bài 3 — Hai vị trí có hai bộ đệm không?

~~~java
List<StringBuilder> items =
        Collections.nCopies(2, new StringBuilder("a"));
items.get(1).append("b");
~~~

**Câu hỏi:** Khi đọc văn bản ở vị trí 0 và 1, thấy gì? List có bị thay ô không?

**Đáp án:** Cả hai đều biểu diễn `"ab"` vì giữ cùng một `StringBuilder`. Không có ô nào được thay; đối tượng được chia sẻ đổi trạng thái. Nếu cần hai bộ đệm độc lập, gọi `new StringBuilder("a")` riêng cho mỗi phần tử khi xây list.

### Bài 4 — Capacity chưa phải kích thước

~~~java
List<String> a = new ArrayList<>(5);
List<String> b =
        new ArrayList<>(Collections.nCopies(5, "pending"));
~~~

**Câu hỏi:** `a.set(4, "done")` và `b.set(4, "done")` có cùng kết quả không?

**Đáp án:** Không. `a` vẫn rỗng nên không có chỉ số 4 và ném `IndexOutOfBoundsException`. `b` có 5 phần tử thật và hỗ trợ thay nên phép `set` thành công. Nếu chỉ cần thêm dần, `a` hợp lý; nếu cần các vị trí đã tồn tại, phải tạo các phần tử như `b` hoặc thêm đủ bằng vòng lặp.

### Bài 5 — Sao chép list ngoài có sửa lỗi chung hàng không?

~~~java
List<List<Integer>> grid = new ArrayList<>(
        Collections.nCopies(2, new ArrayList<Integer>()));

grid.get(0).add(7);
~~~

**Câu hỏi:** Kết quả biểu diễn của `grid` là gì? Viết cách tạo hai hàng độc lập.

**Đáp án:** Kết quả là `[[7], [7]]`. List ngoài mới không nhân bản hàng. Có thể tạo như sau trong một phương thức:

~~~java
List<List<Integer>> grid = new ArrayList<>();
for (int i = 0; i < 2; i++) {
    grid.add(new ArrayList<>());
}
~~~

Với cách tạo này, thêm 7 vào hàng đầu cho kết quả `[[7], []]`.

### Bài 6 — Đệm tới độ dài đích

Một list đã có 3 phần tử; muốn thêm `"-"` để có 8 phần tử.

**Câu hỏi:** Truyền 8 hay 5 vào `nCopies` trong `addAll`? Nếu list đang có 10 phần tử thì sao?

**Đáp án:** Truyền số còn thiếu là 5. Truyền 8 sẽ làm list dài thành 11. Nếu đã có 10, đoạn mã “chỉ đệm khi thiếu” không thêm gì; muốn cắt còn 8 cần một yêu cầu và thao tác riêng. Không để số âm đi vào `nCopies` rồi coi ngoại lệ là cách điều khiển nhánh bình thường.

### Bài 7 — Đích cho `Collections.copy` cần gì?

Nguồn có ba chuỗi. Có hai ứng viên đích:

~~~java
List<String> a = new ArrayList<>(3);
List<String> b = Collections.nCopies(3, "?");
~~~

**Câu hỏi:** Tại sao cả hai chưa phù hợp? Cách tạo đích phù hợp là gì?

**Đáp án:** `a` có size 0 nên chưa có ô nhận dữ liệu; `b` có size 3 nhưng không cho thay ô. Có thể dùng `new ArrayList<>(Collections.nCopies(3, "?"))` để vừa đủ ô vừa sửa được. Nếu chỉ muốn tạo ngay một bản sao nguồn, `new ArrayList<>(source)` đơn giản hơn việc chuẩn bị ô rồi gọi `Collections.copy`.

### Bài 8 — Đổi sang `List.of` có luôn giữ hành vi không?

Một hàm có hợp đồng chấp nhận một phần tử `null` và đang trả `Collections.singletonList(value)`. Một thay đổi định dùng `List.of(value)` để thay thế.

**Câu hỏi:** Cần kiểm tra điều gì trước khi thay? Có thể dùng việc cả hai đều không sửa được làm bằng chứng tương đương không?

**Đáp án:** Phải kiểm tra chính sách `null`. Với `value == null`, cách cũ trả list kích thước 1 còn cách mới ném `NullPointerException`. Cùng quyền sửa vật chứa không chứng minh mọi hành vi giống nhau. Nếu muốn đổi hợp đồng để cấm `null`, phải xử lý như một thay đổi API có chủ đích và cập nhật phía gọi tương ứng.

Khi dùng các factory nhỏ này, hãy xác định rõ số phần tử cần có, kiểu collection cần trả và việc phần tử được chia sẻ hay phải độc lập. Ba thông tin ấy giúp phân biệt một kết quả gọn, đúng mục đích với một list nhìn có vẻ hợp lý nhưng sai quyền sửa hoặc sai quan hệ tham chiếu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unmodifiable-collections-008">Bảo vệ dữ liệu khi nhận tham số hoặc trả về collection từ API</a>

<details>
<summary>Xem chi tiết</summary>

# Bảo vệ dữ liệu khi nhận tham số hoặc trả về collection từ API

## 1. Collection đi qua API mang theo cả quyền sửa

Một lớp có danh sách thành viên và yêu cầu danh sách luôn có ít nhất một người. Hàm khởi tạo kiểm tra đầu vào không rỗng rồi lưu List nhận được. Vài dòng sau, bên gọi xóa List bằng biến họ vẫn giữ. Danh sách bên trong lớp cũng rỗng, dù không có phương thức xóa thành viên nào của lớp được gọi.

Vấn đề nằm ở tham chiếu dùng chung. Trong Java, truyền một biến tham chiếu vào phương thức tạo ra một bản sao của **giá trị tham chiếu**, không tự tạo bản sao đối tượng mà nó trỏ đến. Hai biến có thể cùng dẫn đến một List. Tương tự, trả về tham chiếu đến List nội bộ có thể cho người nhận một đường sửa trạng thái của đối tượng.

API trong bài là bề mặt lớp và phương thức mà các thành phần Java dùng để làm việc với nhau. Khi một collection đi qua bề mặt ấy, hợp đồng cần trả lời:

- Phương thức chỉ đọc dữ liệu trong lúc chạy hay giữ nó để dùng về sau?
- Bên nhận được sửa collection hay chỉ đọc?
- Thay đổi ở một bên có xuất hiện ở bên kia không?
- Phần tử bên trong là dữ liệu bất biến hay đối tượng còn có thể sửa?

**Bảo vệ dữ liệu** ở đây là duy trì những quy tắc mà chủ sở hữu đã cam kết, đồng thời cấp đúng quyền truy cập cho bên sử dụng. Một collection chỉ đọc giải quyết quyền sửa phần chứa; một bản sao giải quyết một mức phụ thuộc tham chiếu; đối tượng phần tử có thể cần một quyết định riêng.

Bài học bắt đầu bằng một trường hợp lộ tham chiếu, xây dựng cách tiếp nhận an toàn, rồi so sánh ba chính sách trả về. Sau đó mới xử lý đối tượng phần tử và collection lồng nhau. Mục tiêu là chọn hợp đồng phù hợp và kiểm chứng nó, thay vì thêm lớp bọc vào mọi chỗ mà không biết đang bảo vệ điều gì.

Kiến thức cần có là tham chiếu đối tượng, trường `private final`, thao tác List/Set/Map và ngoại lệ cơ bản. Tất cả chương trình dưới đây độc lập, chạy bằng Java 17; lưu mỗi chương trình theo đúng tên tệp được ghi trước mã.

## 2. Ba khái niệm giúp đọc đúng ranh giới dữ liệu

### 2.1. Nhiều đường dẫn đến cùng một đối tượng

Tình trạng nhiều tham chiếu cùng trỏ đến một đối tượng thường gọi là dùng chung tham chiếu (aliasing). Nếu đối tượng đó cho phép sửa, một đường dẫn có thể sửa dữ liệu mà đường khác đang quan sát.

Biến tham số là biến riêng của phương thức, nhưng đối tượng nhận được chưa chắc là đối tượng riêng. Gán lại biến tham số không gán lại biến bên gọi; gọi `clear` trên đối tượng List qua biến tham số vẫn tác động lên List dùng chung.

Một trường `private` ngăn mã bên ngoài truy cập trực tiếp trường bằng tên. Nó không thu hồi những tham chiếu đến đối tượng mà bên ngoài đã có. Một trường `final` không cho gán lại tham chiếu trường sau khi khởi tạo theo quy tắc ngôn ngữ. Nó không cấm các phương thức sửa trên đối tượng được tham chiếu.

### 2.2. Quy tắc luôn đúng của đối tượng

Điều kiện như “danh sách thành viên không rỗng” là một bất biến của đối tượng (invariant): điều lớp muốn giữ đúng ở các thời điểm quan sát hợp lệ. Nếu getter cho phép người nhận gọi `clear` lên List thật, người nhận có thể phá điều kiện đó mà không đi qua logic của lớp.

Vì thế, bảo vệ collection liên quan trực tiếp đến tính đóng gói. Đặt trường là private chỉ là bước kiểm soát đường truy cập; thiết kế tham số, kết quả và phần tử mới quyết định có còn đường sửa trạng thái ngoài ý muốn hay không.

### 2.3. Ba mức độc lập

| Mức cần xét | Câu hỏi kiểm tra | Ví dụ |
| --- | --- | --- |
| Phần chứa | Bên ngoài còn thêm/xóa/thay phần tử hoặc ánh xạ nội bộ được không? | Getter trả thẳng ArrayList nội bộ |
| Quan hệ với nguồn | Nguồn thay đổi thì kết quả đã nhận có đổi theo không? | View chỉ đọc so với ảnh chụp |
| Trạng thái phần tử | Đối tượng được lấy ra có còn sửa trạng thái chung được không? | List chỉ đọc chứa đối tượng có setter |

Một giải pháp có thể đáp ứng mức này mà chưa đáp ứng mức khác. Nói rõ mức cần bảo vệ giúp tránh cả thiếu bảo vệ lẫn sao chép không cần thiết.

## 3. Thí nghiệm lộ tham chiếu ở cả đầu vào và đầu ra

Chương trình này cố ý giữ một thiết kế sai với yêu cầu “không rỗng”. Lưu thành `UnsafeReferenceDemo.java` để quan sát nguyên nhân, không dùng lớp Roster này làm mẫu thiết kế.

```java
import java.util.ArrayList;
import java.util.List;

public class UnsafeReferenceDemo {
    static final class Roster {
        private final List<String> members;

        Roster(List<String> input) {
            if (input.isEmpty()) {
                throw new IllegalArgumentException("At least one member");
            }
            members = input;
        }

        List<String> members() {
            return members;
        }
    }

    public static void main(String[] args) {
        List<String> input = new ArrayList<>(List.of("An", "Binh"));
        Roster roster = new Roster(input);
        System.out.println("initial=" + roster.members());

        input.clear();
        System.out.println("afterInputClear=" + roster.members());

        roster.members().add("Injected");
        System.out.println("inputAfterGetterAdd=" + input);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 UnsafeReferenceDemo.java
java UnsafeReferenceDemo
```

Stdout:

```text
initial=[An, Binh]
afterInputClear=[]
inputAfterGetterAdd=[Injected]
```

Lúc khởi tạo, đầu vào hợp lệ và getter hiện hai thành viên. Sau `input.clear()`, lớp không được hỏi ý kiến nhưng dữ liệu nội bộ đã thành rỗng. Điều kiện được kiểm tra ở hàm khởi tạo không còn đúng vì lớp tiếp tục phụ thuộc vào List thuộc quyền sửa của bên gọi.

Sau đó `roster.members().add("Injected")` đi theo chiều ngược lại. Getter trả tham chiếu đến cùng List, nên thao tác thêm qua kết quả cũng xuất hiện ở biến `input`. Cả hai chiều cùng có nguyên nhân là không thiết lập ranh giới quyền sở hữu.

Từ khóa `final` trong chương trình không hề bị vi phạm. Trường `members` vẫn trỏ đến đúng List ban đầu; chỉ nội dung List thay đổi. Đây là lý do cần kiểm tra hành vi thật thay vì nhìn khai báo trường rồi kết luận đối tượng bất biến.

Chỉ sửa getter thành một lớp bọc chỉ đọc vẫn chưa xử lý đường `input.clear()`. Chỉ sao chép đầu vào thành một ArrayList riêng nhưng tiếp tục trả thẳng ArrayList đó cũng chưa xử lý đường sửa qua getter. Phải xem cả hai ranh giới.

## 4. Tiếp nhận tham số: giữ dữ liệu mình đã kiểm tra

Nếu lớp cần giữ một danh sách độc lập để dùng lâu dài, một quy trình rõ ràng là:

1. Tạo dữ liệu riêng ở mức cần bảo vệ.
2. Kiểm tra các điều kiện nghiệp vụ trên chính dữ liệu sẽ giữ.
3. Chỉ gán trường khi quá trình chuẩn bị và kiểm tra đã thành công.
4. Cung cấp đầu ra không mở lại đường sửa ngoài hợp đồng.

Với List tên String, `List.copyOf(input)` phù hợp cho bước đầu vì kết quả không hỗ trợ sửa phần chứa và không đi theo việc đầu vào thêm/xóa/thay tham chiếu phần tử về sau. String là bất biến nên việc dùng chung các String không mở thêm quyền sửa tên.

Kiểm tra trên bản đã chụp giúp tránh mô hình “kiểm tra đối tượng bên ngoài, rồi giữ một dữ liệu có thể khác dữ liệu đã kiểm tra”. Tuy nhiên, việc chụp không tự khóa nguồn đang được một luồng khác sửa. Hợp đồng tiếp nhận vẫn cần quy tắc phối hợp nếu việc sửa đồng thời có thể xảy ra.

Đây là nguyên tắc thiết kế ranh giới, không phải yêu cầu luôn sao chép mọi tham số. Một phương thức chỉ tính toán trong thời gian gọi có thể không cần giữ bản sao khi hợp đồng cho phép đọc trực tiếp. Một thao tác được thiết kế để sửa List do bên gọi đưa vào cũng có thể hợp lệ, miễn hành vi ấy được công bố. Cần sao chép khi mức phụ thuộc và quyền sửa của dữ liệu đầu vào không đáp ứng yêu cầu của đối tượng nhận.

Hướng dẫn của Oracle phân biệt rõ việc sao chép dữ liệu có thể sửa ở đầu vào và đầu ra, đồng thời nhấn mạnh sử dụng bản đã bảo vệ cho logic kiểm tra: [Secure Coding Guidelines, mục 6 về khả năng thay đổi dữ liệu](https://www.oracle.com/java/technologies/javase/seccodeguide.html#6).

## 5. Thí nghiệm nhận List tên và công bố kết quả chỉ đọc

Lưu thành `DefensiveRosterDemo.java`. Lớp này công bố chính sách: danh sách không null, không chứa null, có ít nhất một tên, tên không toàn khoảng trắng; thứ tự và số lần xuất hiện được giữ như đầu vào.

```java
import java.util.ArrayList;
import java.util.List;

public class DefensiveRosterDemo {
    static final class Roster {
        private final List<String> members;

        Roster(List<String> input) {
            List<String> captured = List.copyOf(input);
            if (captured.isEmpty()) {
                throw new IllegalArgumentException("At least one member");
            }
            if (captured.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("Blank member");
            }
            members = captured;
        }

        List<String> members() {
            return members;
        }
    }

    public static void main(String[] args) {
        List<String> input = new ArrayList<>(List.of("An", "Binh"));
        Roster roster = new Roster(input);
        input.clear();
        System.out.println("afterInputClear=" + roster.members());

        try {
            roster.members().clear();
        } catch (UnsupportedOperationException e) {
            System.out.println("getterClear=UnsupportedOperationException");
        }
        System.out.println("afterGetterAttempt=" + roster.members());

        try {
            new Roster(List.of());
        } catch (IllegalArgumentException e) {
            System.out.println("emptyInput=IllegalArgumentException");
        }

        try {
            new Roster(List.of(" "));
        } catch (IllegalArgumentException e) {
            System.out.println("blankInput=IllegalArgumentException");
        }

        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        try {
            new Roster(withNull);
        } catch (NullPointerException e) {
            System.out.println("nullMember=NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 DefensiveRosterDemo.java
java DefensiveRosterDemo
```

Stdout:

```text
afterInputClear=[An, Binh]
getterClear=UnsupportedOperationException
afterGetterAttempt=[An, Binh]
emptyInput=IllegalArgumentException
blankInput=IllegalArgumentException
nullMember=NullPointerException
```

Hàm khởi tạo giữ `captured`, không giữ `input`. Khi bên gọi xóa nguồn sau khi khởi tạo, danh sách thành viên vẫn như lúc tiếp nhận. Khi người nhận gọi `clear` qua getter, kết quả không cho phép thao tác đó.

Có thể trả trực tiếp trường `members` trong thiết kế này vì trường đã chứa List chốt và phần tử là String. Không cần tạo thêm ArrayList ở mỗi lần đọc để bảo vệ cùng một quyền sửa. Cách này không dựa vào việc phương thức tạo có tạo đối tượng mới trong mọi trường hợp; nó dựa vào hợp đồng dữ liệu được trả về.

Các ca không hợp lệ được tách theo trách nhiệm:

- Không có thành viên và tên toàn khoảng trắng vi phạm điều kiện do lớp đặt ra.
- Phần tử null vi phạm điều kiện của phương thức tạo được dùng khi tiếp nhận.
- List nguồn rỗng vẫn là dữ liệu kỹ thuật hợp lệ đối với phương thức tạo, nhưng không hợp lệ đối với Roster này.

Việc chọn loại ngoại lệ và thông báo nên nhất quán với API thực tế. Ví dụ không âm thầm biến danh sách rỗng thành danh sách mặc định, không lọc bỏ tên lỗi và không thay đổi tên bằng cách cắt khoảng trắng. Nếu cần chuẩn hóa, phải xác định và công bố đó là bước riêng trong hợp đồng.

Đọc hợp đồng chụp và quy tắc null tại [List.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)). Phương thức này có từ Java 10; các chương trình trong bài dùng JDK 17.

## 6. Trả collection: chọn đúng một hợp đồng có thể giải thích

Khi trả về collection, có ít nhất ba hợp đồng hữu ích. Không phải hợp đồng nào cũng phù hợp với mọi đối tượng.

| Hợp đồng trả về | Bên nhận sửa phần chứa được không? | Chủ sở hữu thay nguồn thì kết quả cũ thế nào? | Cách làm cho List String |
| --- | --- | --- | --- |
| View chỉ đọc đang theo nguồn | Không qua view | Có thể thấy thay đổi của nguồn | `Collections.unmodifiableList(internal)` |
| Ảnh chụp chỉ đọc | Không | Không đi theo các lần sửa phần chứa của nguồn sau khi chụp | `List.copyOf(internal)` |
| Bản sao riêng có thể chỉnh sửa | Có, trên bản riêng | Không đi theo thay đổi phần chứa của nguồn | `new ArrayList<>(internal)` |

Một view chỉ đọc có thể phù hợp cho màn hình cần quan sát dữ liệu đang được chủ sở hữu quản lý. Một ảnh chụp phù hợp cho kết quả cần ổn định trong một quá trình xử lý. Một bản sao có thể sửa phù hợp khi người nhận cần lọc, sắp xếp hoặc bổ sung vào dữ liệu riêng mà không cập nhật chủ sở hữu.

Từ “chỉ đọc” cần chỉ rõ nó nói về quyền sửa qua đối tượng trả về. Nó không tự hứa rằng mọi lần đọc đều cho cùng dữ liệu. View có thể cấm `add` nhưng vẫn hiện thêm mục mới do chủ sở hữu thêm qua một phương thức hợp lệ.

Nếu chủ sở hữu cần giữ quy tắc nghiệp vụ khi dữ liệu thay đổi, nên cho phép thay đổi qua các phương thức có nghĩa như `addTask`, `removeMember` hoặc `replacePermissions`. Các phương thức ấy có thể kiểm tra điều kiện trước khi thay đổi. Trả thẳng một List nội bộ rồi yêu cầu bên gọi “đừng sửa sai” bỏ qua cơ hội giữ quy tắc đó ở chính đối tượng có trách nhiệm.

Lưu thành `ReturnPolicyDemo.java`. Chương trình dùng String để tách câu chuyện quyền sửa phần chứa khỏi câu chuyện trạng thái phần tử.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ReturnPolicyDemo {
    static final class TaskBoard {
        private final List<String> tasks = new ArrayList<>();

        void addTask(String task) {
            Objects.requireNonNull(task, "task");
            if (task.isBlank()) {
                throw new IllegalArgumentException("Blank task");
            }
            tasks.add(task);
        }

        List<String> readView() {
            return Collections.unmodifiableList(tasks);
        }

        List<String> readSnapshot() {
            return List.copyOf(tasks);
        }

        List<String> editableCopy() {
            return new ArrayList<>(tasks);
        }
    }

    public static void main(String[] args) {
        TaskBoard board = new TaskBoard();
        board.addTask("learn");
        List<String> view = board.readView();
        List<String> snapshot = board.readSnapshot();
        List<String> editable = board.editableCopy();

        board.addTask("practice");
        editable.add("local-only");
        System.out.println("view=" + view);
        System.out.println("snapshot=" + snapshot);
        System.out.println("editable=" + editable);
        System.out.println("board=" + board.readSnapshot());

        try {
            view.add("blocked");
        } catch (UnsupportedOperationException e) {
            System.out.println("viewAdd=UnsupportedOperationException");
        }

        try {
            snapshot.add("blocked");
        } catch (UnsupportedOperationException e) {
            System.out.println("snapshotAdd=UnsupportedOperationException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ReturnPolicyDemo.java
java ReturnPolicyDemo
```

Stdout:

```text
view=[learn, practice]
snapshot=[learn]
editable=[learn, local-only]
board=[learn, practice]
viewAdd=UnsupportedOperationException
snapshotAdd=UnsupportedOperationException
```

Sau lần gọi `addTask("practice")`, `view` nhìn thấy hai mục vì nó theo List nội bộ. `snapshot` vẫn có một mục. `editable` cũng không nhận mục mới của chủ sở hữu nhưng cho phép thêm `local-only` vào bản riêng.

Dòng `board` xác nhận việc sửa bản riêng không cập nhật đối tượng TaskBoard. Hai ca ngoại lệ lại xác nhận view và ảnh chụp đều chặn thao tác thêm qua kết quả, dù quan hệ của chúng với nguồn khác nhau.

Các tên `readView`, `readSnapshot` và `editableCopy` trong ví dụ cố ý thể hiện chính sách. API thật có thể chọn cách đặt tên khác, nhưng tài liệu không nên để người nhận phải thử sửa hoặc quan sát debugger mới biết quyền của mình.

Hành vi đọc xuyên qua nguồn của lớp bọc được mô tả tại [Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)). Sự phân biệt collection chỉ đọc, view và dữ liệu phần tử được trình bày trong [hợp đồng Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html).

## 7. Kiểu giao diện giúp tách bản triển khai, không tự tạo quyền chỉ đọc

Nhận tham số dưới kiểu `List<String>` thay vì bắt buộc `ArrayList<String>` giúp API diễn đạt nhu cầu danh sách mà không ràng buộc một bản triển khai. Tuy vậy, List vẫn có các phương thức sửa; chữ ký kiểu không tự cam kết khả năng sửa thực tế hay việc chuyển quyền sở hữu.

Chọn giao diện theo dữ liệu cần biểu diễn:

- Cần vị trí, thứ tự và số lần lặp: List.
- Cần tập giá trị phân biệt: Set.
- Cần liên kết khóa–giá trị: Map.
- Chỉ cần đọc một nhóm phần tử mà không đòi vị trí: có thể nhận Collection hoặc Iterable nếu phù hợp với cách sử dụng.

Không đổi List thành Set chỉ vì muốn bảo vệ dữ liệu. Loại trùng có thể làm mất thông tin, chẳng hạn số lần một sản phẩm xuất hiện trong dữ liệu đầu vào. Tương tự, không chọn một phương thức tạo không bảo đảm thứ tự rồi vẫn hứa thứ tự của một cấu trúc nguồn chuyên biệt.

Kiểu wildcard như `List<? extends Item>` có thể giới hạn việc thêm một Item cụ thể ở thời điểm biên dịch. Nó không là kiểu List chỉ đọc: những thao tác như `clear` vẫn thuộc API, và các Item lấy ra vẫn có thể có phương thức sửa. Giới hạn kiểu và quyền sửa đối tượng là hai khía cạnh khác nhau.

Trả về Iterable thay cho List có thể thu hẹp bề mặt phương thức mà người nhận thấy. Nhưng nếu đối tượng thực tế vẫn là collection nội bộ có thể sửa, việc đổi kiểu khai báo không tạo bản sao hay thu hồi tham chiếu. Không dùng việc “người gọi không nhìn thấy add trong chữ ký này” làm bằng chứng cho mức độ bảo vệ dữ liệu.

Với một API chỉ cần trả số lượng, giá trị tính toán hoặc một thông tin nhỏ, có thể trả trực tiếp kết quả đó thay vì công bố cả collection nội bộ. Thiết kế theo nhu cầu sử dụng thường giảm số đường truy cập cần quản lý.

## 8. Bảo vệ List ngoài chưa bảo vệ đối tượng phần tử

Giả sử báo cáo chứa các dòng hàng. List không được sửa, nhưng mỗi dòng vẫn là đối tượng có `setQuantity`. Người nhận có thể không xóa được dòng nhưng vẫn đổi số lượng trên dòng đã lấy ra. Nếu dòng đó còn được đối tượng khác dùng, thay đổi sẽ xuất hiện ở cả hai nơi.

Có ba hướng lựa chọn tùy hợp đồng:

1. Dùng đối tượng phần tử bất biến để có thể chia sẻ an toàn về trạng thái.
2. Sao chép phần tử thành đối tượng riêng, nếu người nhận được sửa bản riêng.
3. Chuyển dữ liệu sang một dạng giá trị chỉ chứa các thông tin cần công bố.

Hướng thứ ba thường phù hợp cho kết quả đọc. Nó còn giúp tránh công bố cả phương thức sửa hoặc dữ liệu không cần thiết của đối tượng nghiệp vụ nội bộ.

Lưu thành `ElementBoundaryDemo.java`. `Line` là đối tượng có thể sửa; `LineSnapshot` chỉ giữ mã String và số lượng int. `ShallowReport` cố ý mới bảo vệ List ngoài; `Report` tạo dữ liệu giá trị riêng trước khi chốt danh sách.

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ElementBoundaryDemo {
    static final class Line {
        private final String sku;
        private int quantity;

        Line(String sku, int quantity) {
            this.sku = Objects.requireNonNull(sku, "sku");
            setQuantity(quantity);
        }

        String sku() {
            return sku;
        }

        int quantity() {
            return quantity;
        }

        void setQuantity(int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Positive quantity required");
            }
            this.quantity = quantity;
        }
    }

    record LineSnapshot(String sku, int quantity) {
        LineSnapshot {
            Objects.requireNonNull(sku, "sku");
            if (quantity <= 0) {
                throw new IllegalArgumentException("Positive quantity required");
            }
        }
    }

    static final class ShallowReport {
        private final List<Line> lines;

        ShallowReport(List<Line> input) {
            lines = List.copyOf(input);
        }

        List<Line> lines() {
            return lines;
        }
    }

    static final class Report {
        private final List<LineSnapshot> lines;

        Report(List<Line> input) {
            List<LineSnapshot> captured = new ArrayList<>();
            for (Line line : input) {
                Objects.requireNonNull(line, "line");
                captured.add(new LineSnapshot(line.sku(), line.quantity()));
            }
            lines = List.copyOf(captured);
        }

        List<LineSnapshot> lines() {
            return lines;
        }
    }

    public static void main(String[] args) {
        Line original = new Line("P1", 2);
        List<Line> input = new ArrayList<>(List.of(original));
        ShallowReport shallow = new ShallowReport(input);
        Report report = new Report(input);

        original.setQuantity(7);
        System.out.println("shallowQuantity=" + shallow.lines().get(0).quantity());
        System.out.println("reportQuantity=" + report.lines().get(0).quantity());

        shallow.lines().get(0).setQuantity(9);
        System.out.println("originalQuantity=" + original.quantity());
        System.out.println("reportAfterEdit=" + report.lines().get(0).quantity());

        input.clear();
        System.out.println("reportSize=" + report.lines().size());
        try {
            report.lines().clear();
        } catch (UnsupportedOperationException e) {
            System.out.println("reportClear=UnsupportedOperationException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ElementBoundaryDemo.java
java ElementBoundaryDemo
```

Stdout:

```text
shallowQuantity=7
reportQuantity=2
originalQuantity=9
reportAfterEdit=2
reportSize=1
reportClear=UnsupportedOperationException
```

Sau khi sửa `original` từ số lượng 2 thành 7, báo cáo sao chép nông đọc được 7. Báo cáo đã chuyển sang `LineSnapshot` vẫn giữ 2. Khi sửa qua dòng lấy từ `shallow.lines()` thành 9, đối tượng `original` cũng đổi theo. Một lần nữa, không có thao tác sửa List ngoài nào được thực hiện.

`Report` lấy các giá trị cần thiết rồi dựng đối tượng `LineSnapshot` riêng. Việc xóa List đầu vào sau đó không xóa dòng trong báo cáo; việc cố xóa qua List đầu ra bị chặn. Hai chiều được bảo vệ ở cả phần chứa và mức trạng thái mà báo cáo cần giữ.

Record trong ví dụ là cú pháp gọn cho một nhóm giá trị. Các thành phần của nó được lưu bằng trường final và có phương thức đọc như `sku()`, `quantity()`. Trong trường hợp cụ thể này, String bất biến và int là giá trị nguyên thủy, nên không có đối tượng con có thể sửa để bị lộ ra.

Không suy rộng rằng mọi record đều tự bất biến sâu. Nếu một thành phần record là List có thể sửa, record vẫn cần xử lý ranh giới List đầu vào/đầu ra. Trường final không thay thế chính sách đối với đối tượng được tham chiếu. Có thể đối chiếu quy tắc trường và phương thức truy cập record trong [JLS 17, Record Classes](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.10).

Cách tạo bản giá trị ở đây phù hợp với Line đang có. Nó không tự sao chép một đồ thị đối tượng bất kỳ, không giữ mọi quan hệ định danh giữa các phần tử và không đảm bảo đọc nguyên tử nhiều trường đang bị luồng khác thay đổi. Những yêu cầu đó phải được thiết kế riêng nếu mô hình có chúng.

## 9. Collection lồng nhau cần xét từng tầng có quyền sửa

Một cấu hình `Map<String, Set<String>>` có ít nhất hai tầng collection:

- Map quyết định vai trò nào được gắn với Set nào.
- Set quyết định những quyền nào thuộc vai trò đó.

Chỉ chụp hoặc bọc Map ngoài chưa chặn việc thêm quyền vào một Set đã được giữ chung. Chỉ bọc Set rồi giữ nguồn Map bên ngoài cũng chưa chặn việc thay Set gắn với một vai trò. Mục tiêu bảo vệ hai tầng cần xử lý cả hai đường.

Lưu thành `NestedPolicyDemo.java`. Hợp đồng của ví dụ là chốt bảng vai trò và các tập quyền tại thời điểm tiếp nhận, không cho người nhận sửa qua đầu ra; vai trò không có trong bảng trả tập rỗng. Khóa và quyền là String, nên không có tầng đối tượng giá trị có thể sửa sâu hơn.

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class NestedPolicyDemo {
    static final class AccessPolicy {
        private final Map<String, Set<String>> roles;

        AccessPolicy(Map<String, Set<String>> input) {
            Objects.requireNonNull(input, "input");
            Map<String, Set<String>> captured = new HashMap<>();
            input.forEach((role, permissions) -> {
                Objects.requireNonNull(role, "role");
                if (role.isBlank()) {
                    throw new IllegalArgumentException("Blank role");
                }
                Set<String> values = Set.copyOf(permissions);
                if (values.stream().anyMatch(String::isBlank)) {
                    throw new IllegalArgumentException("Blank permission");
                }
                captured.put(role, values);
            });
            roles = Map.copyOf(captured);
        }

        Set<String> permissionsFor(String role) {
            Objects.requireNonNull(role, "role");
            return roles.getOrDefault(role, Set.of());
        }

        Map<String, Set<String>> snapshot() {
            return roles;
        }
    }

    public static void main(String[] args) {
        Set<String> permissions = new HashSet<>(Set.of("read", "write"));
        Map<String, Set<String>> input = new HashMap<>();
        input.put("editor", permissions);
        AccessPolicy policy = new AccessPolicy(input);

        permissions.add("delete");
        input.clear();
        System.out.println("editorForDisplay="
                + new TreeSet<>(policy.permissionsFor("editor")));
        System.out.println("missingEmpty="
                + policy.permissionsFor("missing").isEmpty());

        try {
            policy.permissionsFor("editor").clear();
        } catch (UnsupportedOperationException e) {
            System.out.println("permissionClear=UnsupportedOperationException");
        }

        try {
            policy.snapshot().put("admin", Set.of("all"));
        } catch (UnsupportedOperationException e) {
            System.out.println("mapPut=UnsupportedOperationException");
        }

        System.out.println("editorAfterAttempts="
                + new TreeSet<>(policy.permissionsFor("editor")));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 NestedPolicyDemo.java
java NestedPolicyDemo
```

Stdout:

```text
editorForDisplay=[read, write]
missingEmpty=true
permissionClear=UnsupportedOperationException
mapPut=UnsupportedOperationException
editorAfterAttempts=[read, write]
```

Trong hàm khởi tạo, mỗi Set quyền được chụp trước khi đưa vào Map tạm. Khi toàn bộ đầu vào đã được chuẩn bị và kiểm tra, Map tạm được chụp thành trường `roles`. Trường chỉ nhận kết quả đã hoàn tất; không công bố Map đang được điền dở.

Sau khi tạo chính sách, chương trình thực hiện hai thay đổi đầu vào khác loại: thêm `delete` vào Set cũ và xóa Map nguồn. Kết quả vẫn có hai quyền `read`, `write` cho `editor`. Các lần cố xóa tập quyền hoặc thêm ánh xạ qua kết quả đều bị chặn.

Việc in dùng `TreeSet` để có thứ tự hiển thị ổn định, không phải để ngầm hứa rằng Set chốt sẽ duyệt theo thứ tự chữ. Khi kết quả có yêu cầu thứ tự thực sự, phải chọn và công bố hợp đồng có thứ tự ngay trong thiết kế.

Map tạm và Set chốt tạo được mức bảo vệ đủ cho hình dạng dữ liệu của ví dụ. Nếu giá trị đổi thành `Set<Permission>` với đối tượng Permission có thể sửa, cần tiếp tục đánh giá quyền sửa ở tầng đó. Không áp dụng một công thức “bọc hai lần” mà bỏ qua kiểu phần tử thực tế.

Chi tiết phương thức tạo dùng trong ví dụ: [Set.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#copyOf(java.util.Collection)) và [Map.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#copyOf(java.util.Map)). Cả hai từ chối null theo hợp đồng tương ứng và không cung cấp cơ chế sao chép sâu đối tượng.

## 10. Ghi hợp đồng để người gọi không phải đoán

Một API trả `List<T>` vẫn còn nhiều điều cần nói ngoài kiểu T. Hợp đồng có thể trình bày bằng Javadoc hoặc tài liệu phương thức, nhưng nên dùng những phát biểu kiểm chứng được.

| Khía cạnh | Câu hỏi cần trả lời | Ví dụ diễn đạt rõ |
| --- | --- | --- |
| Thời gian giữ đầu vào | Chỉ đọc trong lúc gọi hay giữ sau khi gọi? | Lớp lưu ảnh chụp các tên đã nhận |
| Quyền sửa nguồn | Bên gọi còn được dùng và sửa nguồn sau khi gọi không? | Sửa phần chứa của nguồn sau khi hoàn tất không đổi danh sách đã lưu |
| Quyền sửa kết quả | Kết quả có cho thêm/xóa/thay phần tử không? | Kết quả không hỗ trợ thao tác sửa List |
| Quan hệ với trạng thái hiện tại | Kết quả cũ có nhận thay đổi mới từ chủ sở hữu không? | Kết quả là ảnh chụp của lần lấy dữ liệu |
| Phần tử | Chia sẻ đối tượng hay tạo dữ liệu giá trị riêng? | Kết quả chứa các LineSnapshot độc lập với Line đầu vào |
| Dữ liệu không hợp lệ | Null, rỗng, phần tử lỗi được xử lý thế nào? | Null bị từ chối, danh sách rỗng gây lỗi điều kiện nghiệp vụ |
| Thứ tự và trùng lặp | Có giữ vị trí và số lần xuất hiện không? | List giữ thứ tự đầu vào và cho phép tên lặp |
| Truy cập đồng thời | Bên gọi được sửa nguồn trong khi phương thức đọc không? | Việc sửa đồng thời phải tuân theo cơ chế phối hợp đã nêu |

Đối với `Roster` ở phần 5, mô tả đầy đủ có thể là: “Tiếp nhận một List tên không null, không chứa null, không rỗng và không có tên toàn khoảng trắng. Lớp giữ thứ tự cùng các lần xuất hiện trong List tại lúc tiếp nhận. Thay đổi phần chứa của List đầu vào sau khi hàm khởi tạo hoàn tất không làm thay đổi danh sách đã lưu. Kết quả đọc không cho phép sửa List.”

Đối với `TaskBoard.readView()`, cần thêm ý rằng người nhận có thể thấy dữ liệu do TaskBoard cập nhật về sau. Nếu thay bằng lời hứa “kết quả cố định”, bản triển khai dùng lớp bọc đang theo nguồn sẽ không đáp ứng hợp đồng dù mọi lần gọi `add` qua view đều bị chặn.

Không cần công bố tên lớp triển khai nếu đó không phải phần có ý nghĩa của API. Lời hứa “luôn trả ArrayList” vừa không mô tả đầy đủ quyền sửa vừa làm người dùng phụ thuộc vào một lựa chọn có thể không cần thiết. Công bố khả năng, thứ tự, mức độc lập và chính sách lỗi thường hữu ích hơn.

Hợp đồng cũng giúp xem xét thay đổi phiên bản. Đổi một getter từ bản sao có thể sửa sang kết quả chỉ đọc có thể làm mã bên gọi bắt đầu nhận ngoại lệ. Đổi từ ảnh chụp sang view có thể làm dữ liệu cũ đổi theo trạng thái mới. Không coi những thay đổi ấy chỉ là tối ưu nội bộ nếu hành vi đã được công bố khác đi.

## 11. Giữ đường thay đổi hợp lệ ở phía chủ sở hữu

Khi lớp quản lý dữ liệu thay đổi, không nhất thiết biến toàn bộ đối tượng thành bất biến. Có thể giữ collection nội bộ có thể sửa, nhưng chỉ mở những thao tác đáp ứng quy tắc của lớp.

Ví dụ, thay vì trả List thật để bên gọi tự thêm thành viên, lớp có thể cung cấp `addMember(name)`. Thay vì để bên gọi xóa tùy ý, lớp có thể kiểm tra trước xem việc xóa có làm danh sách rỗng hay không. Đầu ra đọc khi ấy được chọn là view hoặc ảnh chụp tùy yêu cầu.

Đối với một thao tác nhận cả collection để thay thế trạng thái, nên chuẩn bị dữ liệu mới ở biến cục bộ trước. Hoàn tất việc kiểm tra và tạo mức bảo vệ cần thiết rồi mới thay trạng thái đang giữ. Nếu dữ liệu không hợp lệ ở mục cuối, tránh để đối tượng đã cập nhật một phần các mục trước đó mà không có hợp đồng cho phép kết quả dở dang.

Phải xét cả những đường ra ngoài getter:

- Trả `keySet`, `values` hoặc `entrySet` của Map nội bộ có thể công bố view liên quan đến Map.
- Trả một phần List bằng `subList` có thể tiếp tục liên hệ với List nguồn.
- Gửi collection hoặc đối tượng phần tử cho một hàm xử lý do bên ngoài cung cấp cũng trao một đường giữ tham chiếu.
- Một bộ duyệt có khả năng xóa có thể là đường sửa ngay cả khi API không trả trực tiếp List.

Giải pháp phụ thuộc hợp đồng của từng đường. Nếu đường đó chỉ nên cung cấp dữ liệu đọc, đưa ra biểu diễn đã được bảo vệ ở mức cần thiết. Nếu bên nhận được thay đổi, hướng thay đổi về thao tác mà chủ sở hữu kiểm soát hoặc một bản riêng được phép sửa.

Ví dụ, gọi một hàm xử lý ngoài bằng List chỉ đọc chứa Line có setter vẫn cho hàm ấy giữ và sửa Line. Không giới hạn việc kiểm tra ở từ khóa `return`; cần theo dõi mọi nơi tham chiếu nội bộ được chuyển sang thành phần khác.

Không chuyển trực tiếp collection có thể sửa thuộc một chủ sở hữu khác vào trường của mình rồi mặc định bên kia sẽ không sửa nữa. Nếu có thỏa thuận chuyển quyền sở hữu, thỏa thuận phải rõ và có khả năng duy trì. Trong mã Java thông thường, cơ chế tham chiếu không tự vô hiệu hóa biến mà phía chuyển vẫn đang giữ.

## 12. Giới hạn của sao chép và chi phí cần cân nhắc

Sao chép bảo vệ mức độc lập đã chọn, không thay thế mọi cơ chế quản lý trạng thái.

**Về truy cập đồng thời:** List nguồn có thể sửa không mặc nhiên an toàn để đọc trong khi một luồng khác ghi. Bản sao tạo xong có thể không còn theo nguồn, nhưng quá trình lấy dữ liệu vẫn phải tuân theo hợp đồng đồng bộ hóa của nguồn. Không dựa vào việc đôi lúc thấy hoặc không thấy `ConcurrentModificationException` để kết luận quá trình đọc đúng. Hợp đồng Collection dành trách nhiệm chính sách đồng bộ hóa cho từng bản triển khai.

**Về nhiều trường liên quan:** Chuyển một đối tượng có thể sửa sang dữ liệu giá trị có thể đọc nhiều trường ở các thời điểm khác nhau. Nếu yêu cầu các trường phải phản ánh cùng một trạng thái nghiệp vụ, chủ sở hữu cần cung cấp cách lấy trạng thái nhất quán hoặc phối hợp việc sửa. Vòng lặp sao chép phần tử tự nó không thiết lập yêu cầu này.

**Về mức sâu:** Sao chép một tầng chưa đảm bảo độc lập toàn bộ đồ thị. Với quan hệ dùng chung, vòng tham chiếu hoặc tài nguyên có vòng đời riêng, cần một chính sách phù hợp. Không gọi đệ quy “copy mọi thứ” khi chưa xác định phần nào là giá trị, phần nào là tài nguyên và phần nào cần giữ quan hệ định danh.

**Về chi phí:** Tạo bản sao có thể cần duyệt dữ liệu, cấp phát và giữ thêm tham chiếu. Nếu trạng thái đã là dữ liệu chốt phù hợp, có thể trả lại kết quả đó thay vì dựng một bản sao ở mỗi getter. Nếu kết quả cần thay đổi theo thời gian, cân nhắc tạo ảnh chụp tại những thời điểm có ý nghĩa và tái sử dụng trong phạm vi hợp đồng cho phép.

Không loại bỏ bước bảo vệ chỉ vì thấy cấp phát trong công cụ đo hiệu năng mà chưa hiểu yêu cầu quyền sở hữu. Cũng không sao chép nhiều lần qua mọi lớp trung gian khi các lớp đã chia sẻ một giá trị bất biến phù hợp. Tối ưu dựa trên đường dùng dữ liệu và phép đo, đồng thời giữ nguyên những hành vi mà API hứa.

Các ví dụ trong bài chạy một luồng để quan sát rõ từng đường thay đổi. Chúng chứng minh quan hệ dữ liệu ở những thao tác đã nêu, không là một thiết kế đồng bộ hóa hoàn chỉnh.

## 13. Kiểm chứng bằng hành vi thay vì định danh

Một bài kiểm tra tốt cố sửa qua từng đường mà hợp đồng nói đã được chặn hoặc đã tách. Không chỉ kiểm tra nội dung ngay sau hàm khởi tạo, vì lúc ấy cả thiết kế lộ tham chiếu cũng có thể cho kết quả đúng.

| Đường kiểm tra | Cách gây thay đổi | Điều cần quan sát theo hợp đồng |
| --- | --- | --- |
| Đầu vào sau khi tiếp nhận | Thêm/xóa/thay tham chiếu phần tử trong nguồn | Dữ liệu riêng đã chốt không đổi |
| Kết quả chỉ đọc | Gọi thao tác sửa có tác dụng thực sự | Bị từ chối; dữ liệu chủ sở hữu vẫn đúng |
| Bản sao chỉnh sửa được | Sửa bản trả về | Thay đổi chỉ thuộc bản riêng |
| View đang theo nguồn | Cập nhật qua phương thức của chủ sở hữu | View thấy dữ liệu mới theo hợp đồng |
| Phần tử có thể sửa | Gọi setter qua tham chiếu nguồn hoặc kết quả | Mức dùng chung/độc lập đúng như công bố |
| Collection lồng | Sửa từng tầng bằng tham chiếu còn giữ | Các tầng đã hứa bảo vệ không đổi |
| Dữ liệu không hợp lệ | Null, rỗng hoặc mục lỗi | Lỗi rõ ràng; không công bố trạng thái dở dang |

Không dùng `result != input` làm bằng chứng duy nhất cho sự độc lập. Hai collection khác đối tượng vẫn có thể dùng chung phần tử có thể sửa; hai kết quả phương thức tạo cũng không nhất thiết cần khác định danh để đáp ứng đúng hợp đồng.

Khi kiểm tra List, thứ tự và số lần xuất hiện thường thuộc dữ liệu cần so sánh. Với Set hoặc Map không hứa thứ tự, so sánh phần tử hay ánh xạ; đừng buộc thứ tự chuỗi in. Thí nghiệm NestedPolicyDemo cố ý sắp xếp riêng lúc in để stdout ổn định.

Khi kiểm tra quyền sửa, chọn thao tác rõ ràng có yêu cầu thay đổi, như thêm một phần tử mới hoặc xóa một mục đang tồn tại. Không suy ra quy tắc cho mọi collection từ những thao tác không có tác dụng như thêm một collection rỗng, vì hợp đồng chung có chỗ cho khác biệt về ngoại lệ trong các trường hợp này.

Cuối cùng, kiểm tra trạng thái sau lần sửa bị từ chối hoặc sau đầu vào không hợp lệ. Câu hỏi quan trọng không chỉ là “có ngoại lệ không?” mà còn là “đối tượng có còn giữ đúng các quy tắc đã cam kết không?”.

## 14. Thực hành thiết kế và tự giải thích

### Bài 1: Danh sách người nhận thông báo

Một lớp nhận `List<String>` người nhận, giữ để dùng nhiều lần và yêu cầu không rỗng. Người gọi vẫn muốn dùng List nguồn cho công việc khác.

Hãy nêu chính sách tiếp nhận và đầu ra. Sau khi tạo đối tượng, xóa List nguồn và thử xóa qua getter. Tiêu chí đạt: đối tượng vẫn giữ danh sách hợp lệ; người gọi hiểu rằng sửa nguồn không cập nhật lớp, và đầu ra có quyền sửa rõ ràng. Có thể dùng mô hình tiếp nhận của DefensiveRosterDemo nếu phần tử vẫn là String.

### Bài 2: Báo cáo cho phép xử lý riêng

Phương thức trả kết quả để người nhận tự sắp xếp và lọc, nhưng không được cập nhật dữ liệu nội bộ của dịch vụ.

Hãy chọn bản sao có thể sửa, xác định phần tử có được dùng chung không và kiểm tra việc xóa một mục khỏi kết quả không xóa mục nội bộ. Nếu phần tử có setter và người nhận cũng được sửa chúng trên bản riêng, sao chép phần chứa thôi chưa đủ; cần quyết định cách tạo dữ liệu phần tử riêng.

### Bài 3: Giao diện quan sát trạng thái mới

Một thành phần muốn quan sát các mục mới được chủ sở hữu thêm vào nhưng không được tự thêm/xóa.

Hãy thiết kế một view chỉ đọc và tài liệu nói rõ quan hệ với nguồn. Tiêu chí đạt: cập nhật qua phương thức của chủ sở hữu xuất hiện ở view; sửa qua view bị chặn; nếu có nhiều luồng, cơ chế phối hợp không bị bỏ qua. Không mô tả kết quả này là ảnh chụp cố định.

### Bài 4: Map chứa danh sách địa chỉ

Nguồn là `Map<String, List<Address>>`; Address là đối tượng có thể sửa. Kết quả phải là báo cáo không đổi khi nguồn hoặc một Address bên ngoài thay đổi.

Hãy vẽ bằng lời ba tầng cần bảo vệ: ánh xạ Map, các List và trạng thái Address. Tiêu chí đạt: đề xuất dữ liệu địa chỉ bất biến hoặc dữ liệu giá trị riêng, chốt các List tương ứng rồi chốt Map. Không xem một lần `Map.copyOf` là đủ cho cả ba tầng.

### Bài 5: Phương thức thay toàn bộ cấu hình

Một đối tượng đang có cấu hình hợp lệ. Phương thức thay thế nhận một collection mới nhưng mục cuối vi phạm điều kiện.

Hãy mô tả thứ tự chuẩn bị, kiểm tra và cập nhật để cấu hình cũ không bị thay đổi một phần khi có lỗi. Nêu nơi hoàn tất bảo vệ dữ liệu mới trước khi công bố. Nếu thao tác có yêu cầu đồng thời, tách yêu cầu cập nhật nhất quán ấy khỏi việc chỉ tạo collection chỉ đọc.

### Các câu hỏi tự kiểm tra

- **Vì sao private final chưa đủ?** Private kiểm soát truy cập trường, final kiểm soát gán lại trường; cả hai không tự cấm sửa đối tượng còn được tham chiếu từ nơi khác.
- **Sao chép đầu vào rồi trả thẳng một ArrayList nội bộ có giải quyết đủ hai chiều không?** Chưa. Người nhận vẫn có thể sửa trạng thái qua đầu ra.
- **Bọc List đầu vào chỉ đọc rồi giữ lớp bọc có chốt dữ liệu không?** Chưa. Nếu nguồn bên dưới còn được sửa, lớp bọc có thể tiếp tục thấy thay đổi.
- **Khi nào trả lại collection đã giữ là phù hợp?** Khi collection và mức chia sẻ phần tử đã đáp ứng hợp đồng đầu ra, như Roster chứa List chốt các String.
- **Đổi kiểu trả về thành Iterable có tự tạo bản sao không?** Không. Kiểu khai báo và quan hệ tham chiếu là hai vấn đề khác nhau.
- **Điều gì quan trọng hơn một công thức bọc hay sao chép cố định?** Xác định ai được sửa tầng dữ liệu nào, ở thời điểm nào, rồi kiểm chứng đúng những đường thay đổi ấy.



</details>

- [Quay lại đầu trang](#back-to-top)
