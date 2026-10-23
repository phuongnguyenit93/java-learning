<a id="back-to-top"></a>

# Stream và Spliterator

## Menu
- [Collection lưu dữ liệu còn Stream mô tả quá trình xử lý](#streams-spliterators-001)
- [stream, parallelStream và giới hạn không can thiệp nguồn](#streams-spliterators-002)
- [Spliterator: tryAdvance, forEachRemaining và trySplit](#streams-spliterators-003)
- [estimateSize, getExactSizeIfKnown và các đặc tính nguồn](#streams-spliterators-004)
- [ORDERED, SORTED, DISTINCT, SIZED, IMMUTABLE và CONCURRENT](#streams-spliterators-005)
- [Collectors.toList, toSet và toCollection](#streams-spliterators-006)
- [Collectors.toMap: khóa trùng, hàm gộp và loại Map đích](#streams-spliterators-007)
- [Stream.toList, collect và giới hạn bảo đảm về khả năng sửa](#streams-spliterators-008)

## <a id="streams-spliterators-001">Collection lưu dữ liệu còn Stream mô tả quá trình xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Collection lưu dữ liệu còn Stream mô tả quá trình xử lý

## 1. Hai vai trò cần phân biệt

Giả sử ứng dụng đang giữ bốn tên người:

~~~text
[Lan, An, Mai, Bo]
~~~

Ta cần chọn những tên có ít nhất ba ký tự, rồi viết hoa chúng. Có hai việc khác nhau:

- **Giữ dữ liệu:** hiện có những tên nào, theo thứ tự nào, ở vị trí nào.
- **Xử lý dữ liệu:** kiểm tra độ dài, bỏ qua tên không đạt, đổi tên còn lại thành dạng viết hoa và tạo kết quả.

Một `List<String>` là một dạng `Collection` dùng để biểu diễn nhóm phần tử được giữ. Một `Stream<String>` mô tả một chuỗi xử lý trên nguồn phần tử. Stream có thể đọc từ collection đó, nhưng không phải là một danh sách khác vừa được sao chép ra.

Tài liệu Java phân biệt Stream với Collection ở việc Stream không phải cấu trúc lưu các phần tử, có thể xử lý lười, có thể có nguồn không bị chặn trước về số phần tử và chỉ được dùng một lần. [Java SE 17 — java.util.stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html).

Mô hình khởi đầu:

~~~text
Collection giữ phần tử
        |
        | tạo stream từ nguồn
        v
nguồn -> các bước xử lý -> thao tác kết thúc -> kết quả
~~~

Kết quả cuối có thể là một danh sách mới, một số tổng, một câu trả lời đúng/sai, một phần tử tìm được hoặc tác dụng phụ được yêu cầu. Không phải cứ dùng Stream là phải tạo thêm Collection.

Sau bài này, người học cần đọc được một pipeline theo ba phần, biết khi nào xử lý phần tử bắt đầu, phân biệt nguồn với kết quả, và hiểu vì sao không nên lưu một Stream để duyệt đi duyệt lại như một danh sách.

## 2. Collection là mô hình dữ liệu, Stream là mô hình tính toán

### 2.1. Collection trả lời những câu hỏi về nhóm phần tử

`Collection<E>` là giao diện gốc của hệ phân cấp collection. Các dạng như `List`, `Set` và `Queue` cung cấp những hợp đồng cụ thể hơn. Các phương thức như `size`, `contains` hoặc `iterator` làm việc với nhóm phần tử đang được biểu diễn. Quyền thêm/xóa tùy từng cách cài đặt; không phải mọi Collection đều sửa được. [Java SE 17 — Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html).

Ví dụ:

~~~java
List<String> names = new ArrayList<>(List.of("Lan", "An", "Mai", "Bo"));
~~~

`names` giữ bốn tham chiếu chuỗi. Có thể đọc `names.get(0)`, gọi `names.size()`, hoặc tạo nhiều bộ duyệt qua các lần sử dụng.

Nói “Collection lưu dữ liệu” là mô hình phân biệt trách nhiệm, không có nghĩa mỗi đối tượng Collection luôn sở hữu một vùng dữ liệu độc lập. Một khung nhìn như `map.values()` hoặc `list.subList(...)` có thể dùng chung cấu trúc nền. Điều cốt lõi là nó biểu diễn một nhóm phần tử để truy cập, trong khi Stream biểu diễn đường xử lý các phần tử được nguồn cung cấp.

### 2.2. Stream trả lời câu hỏi cần làm gì với các phần tử

~~~java
Stream<String> selected = names.stream()
        .filter(name -> name.length() >= 3)
        .map(name -> name.toUpperCase(Locale.ROOT));
~~~

Đây là đoạn minh họa bên trong một phương thức, cần import `Stream`, `Locale` và các lớp collection liên quan.

`selected` không phải một `List` chứa sẵn `LAN` và `MAI`. Nó mô tả:

1. Lấy phần tử từ `names`.
2. Chỉ đưa tiếp những tên dài ít nhất ba ký tự.
3. Chuyển mỗi tên được đưa tiếp thành chữ hoa.

Chưa có thao tác kết thúc để yêu cầu kết quả. Sau khi bổ sung:

~~~java
List<String> result = selected.toList();
~~~

mới có danh sách kết quả `[LAN, MAI]` cho dữ liệu đã nêu. `result` là đối tượng dữ liệu mới để giữ kết quả; `selected` là pipeline đã được dùng.

### 2.3. Bảng so sánh theo đúng trách nhiệm

| Câu hỏi | Collection | Stream |
| --- | --- | --- |
| Có biểu diễn nhóm dữ liệu để giữ và truy cập không? | Có, theo hợp đồng của loại collection | Lấy phần tử từ nguồn qua một pipeline |
| Có nhất thiết có chỉ số không? | Chỉ một số loại, như List | Không có thao tác `get(index)` của List |
| Có thể yêu cầu xử lý lại cùng nguồn không? | Có thể tạo lượt duyệt mới nếu nguồn còn dùng được | Cần tạo Stream mới; không dùng lại pipeline cũ |
| Có luôn biết sẵn số phần tử không? | Có phương thức size trong hợp đồng Collection | Có thể cần tính, có thể không có giới hạn hữu hạn |
| Lọc có xóa khỏi nguồn không? | Một phép sửa như removeIf có thể xóa nếu được hỗ trợ | filter chọn phần tử cho đường xử lý |
| Kết quả có bắt buộc là một collection không? | Bản thân nó biểu diễn nhóm phần tử | Không; có thể là số, Optional, boolean hoặc kết quả khác |

Không có kết luận rằng Stream “tốt hơn” Collection. Hai vai trò bổ sung nhau: giữ dữ liệu bằng cấu trúc phù hợp, rồi dùng một cách duyệt hoặc tính toán phù hợp để tạo kết quả.

## 3. Một pipeline gồm nguồn, thao tác trung gian và thao tác kết thúc

Xét biểu thức đầy đủ:

~~~java
List<String> result = names.stream()
        .filter(name -> name.length() >= 3)
        .map(name -> name.toUpperCase(Locale.ROOT))
        .toList();
~~~

| Phần | Vai trò trong ví dụ |
| --- | --- |
| `names.stream()` | Tạo Stream tuần tự từ nguồn collection |
| `filter(...)` | Thao tác trung gian chọn những phần tử đi tiếp |
| `map(...)` | Thao tác trung gian tính một giá trị đầu ra cho mỗi phần tử đi tiếp |
| `toList()` | Thao tác kết thúc thu kết quả thành danh sách |

Một pipeline có thể không có thao tác trung gian nào, chẳng hạn `names.stream().count()`. Cũng có thể có nhiều bước lọc, ánh xạ hoặc sắp xếp trước khi kết thúc.

Theo hợp đồng `Stream`, việc tính trên nguồn bắt đầu khi thao tác kết thúc được khởi động; các hàm hành vi của pipeline phải tuân thủ yêu cầu không can thiệp vào nguồn và thường phải không phụ thuộc trạng thái thay đổi bên ngoài. [Java SE 17 — Stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html).

### Thao tác trung gian không phải một Collection trung gian

Cách nhìn dễ gây nhầm là:

~~~text
lọc xong tạo một List mới
rồi viết hoa toàn bộ List đó
rồi lại tạo một List mới khác
~~~

Đó không phải điều mà mỗi lời gọi `filter` hoặc `map` bắt buộc làm. Chúng xây dựng các giai đoạn trong cùng pipeline. Nhiều giai đoạn có thể được phối hợp khi xử lý phần tử mà không tạo một danh sách đầy đủ sau mỗi giai đoạn.

Tuy nhiên, không được từ đó kết luận mọi pipeline đều không cần bộ nhớ tạm. Một thao tác như `sorted` có thể phải giữ dữ liệu để xác định thứ tự trước khi đưa kết quả đi tiếp. “Stream không phải kho lưu phần tử” và “cách thực hiện thuật toán có thể dùng bộ đệm” là hai nhận định tương thích.

## 4. Tính lười: chưa yêu cầu kết quả thì chưa chạy bước xử lý phần tử

Để quan sát, có thể ghi dấu các lời gọi trong một ví dụ tuần tự:

~~~java
List<String> trace = new ArrayList<>();

Stream<String> plan = names.stream()
        .filter(name -> {
            trace.add("filter:" + name);
            return name.length() >= 3;
        })
        .map(name -> {
            trace.add("map:" + name);
            return name.toUpperCase(Locale.ROOT);
        });
~~~

Ngay sau khi tạo pipeline, `trace` vẫn rỗng. Các lambda chưa được gọi trên phần tử chỉ vì ta đã viết `filter` và `map`.

Khi gọi `plan.toList()`, ví dụ minh chứng cho đường xử lý:

~~~text
Lan -> filter đạt -> map thành LAN -> đưa vào kết quả
An  -> filter không đạt
Mai -> filter đạt -> map thành MAI -> đưa vào kết quả
Bo  -> filter không đạt
~~~

Không có `map:An` hoặc `map:Bo` vì hai phần tử đã bị chặn ở bước lọc. Đường xử lý theo phần tử giúp hiểu việc kết hợp giai đoạn, thay vì tưởng rằng toàn bộ phép lọc luôn phải tạo một tập dữ liệu mới trước khi bắt đầu ánh xạ.

Các dấu ghi trong `trace` chỉ phục vụ quan sát pipeline tuần tự cụ thể ở bài. Chúng không phải mẫu thiết kế để cập nhật trạng thái nghiệp vụ trong lambda; cũng không được đem nguyên ví dụ chứa `ArrayList` ghi dấu sang chạy song song rồi mặc nhiên coi là an toàn.

### Tính lười không có nghĩa là chưa xảy ra bất kỳ việc gì

Các đối tượng pipeline vẫn có thể đã được tạo, tham số vẫn được đánh giá và kiểm tra. Một nguồn I/O có thể đã mở tài nguyên ngay khi tạo Stream, dù chưa đọc hết phần tử.

Ví dụ `Files.lines(path)` trả về Stream gắn với một tệp đang mở. Việc các dòng được đọc lười không cho phép bỏ qua nghĩa vụ đóng tệp. Bởi vậy, phát biểu đúng là các thao tác trung gian không tự thực hiện toàn bộ phép xử lý phần tử, không phải “chưa gọi terminal thì mọi thứ hoàn toàn chưa xảy ra”.

### Lỗi trong hàm xử lý có thể xuất hiện muộn

Đoạn minh họa:

~~~java
Stream<Integer> parsed = List.of("1", "bad").stream()
        .map(Integer::parseInt);
~~~

Tạo pipeline không có nghĩa chuỗi `"bad"` đã được phân tích. Một thao tác kết thúc thật sự cần các giá trị số, chẳng hạn `parsed.mapToInt(Integer::intValue).sum()`, sẽ gặp lỗi khi xử lý dữ liệu không hợp lệ.

Không nên chỉ bắt ngoại lệ quanh chỗ tạo Stream rồi cho rằng đã bao hết lỗi chuyển đổi. Vùng xử lý lỗi phải bao quát nơi pipeline được thực hiện.

## 5. Thao tác kết thúc quyết định kết quả cần tính

Từ cùng một nguồn `names`, có thể đặt những câu hỏi khác nhau:

~~~java
long total = names.stream().count();

long longNameCount = names.stream()
        .filter(name -> name.length() >= 3)
        .count();

int totalLength = names.stream()
        .mapToInt(String::length)
        .sum();
~~~

Với `[Lan, An, Mai, Bo]`, ba kết quả lần lượt là `4`, `2` và `10`. Không cần tạo một danh sách độ dài chỉ để tính tổng. `mapToInt` tạo một `IntStream`, phù hợp phép tính trên các giá trị nguyên thủy.

Một số thao tác kết thúc có thể dừng trước khi xử lý hết nguồn. Ví dụ:

~~~java
String first = names.stream()
        .filter(name -> name.startsWith("M"))
        .findFirst()
        .orElse("none");
~~~

Trong ví dụ tuần tự có thứ tự, khi tìm tới `Mai` thì đã có câu trả lời. Không cần tiếp tục kiểm tra `Bo`. `findFirst` biểu diễn yêu cầu lấy phần tử đầu tiên theo thứ tự gặp của nguồn có thứ tự; `Optional` mô tả khả năng không tìm thấy kết quả.

Vì thế, “có terminal” không đồng nghĩa “mọi phần tử phải qua mọi lambda”. Thuật toán chỉ cần tính đủ thông tin cho kết quả mà hợp đồng yêu cầu.

### count có thể không cần duyệt từng phần tử

Nếu số lượng đã biết và các giai đoạn không thay đổi số phần tử, cách cài đặt có thể tính `count` mà không thực hiện một số bước không ảnh hưởng kết quả. Tài liệu nêu rõ khả năng tối ưu này. [Java SE 17 — Stream.count](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#count()).

Đoạn minh họa không nên dùng cho công việc bắt buộc:

~~~java
long count = names.stream()
        .peek(System.out::println)
        .count();
~~~

Không được đặt một yêu cầu quan trọng như ghi nhận giao dịch, xác thực bắt buộc hoặc cập nhật cơ sở dữ liệu trong `peek` rồi dựa vào `count` để bảo đảm nó chạy. Các dòng in có thể không xuất hiện nếu cách cài đặt suy ra số lượng mà không cần duyệt.

Nếu mục đích chính là thực hiện hành động trên từng phần tử, dùng một thao tác được thiết kế cho việc đó, hoặc một vòng lặp rõ ràng. Tối ưu của Stream là một phần lý do cần tách biến đổi dữ liệu khỏi các tác dụng phụ không liên quan đến kết quả.

## 6. Một Stream là một lượt xử lý, không phải công thức dùng lại tự do

Sai lầm phổ biến:

~~~java
Stream<String> selected = names.stream()
        .filter(name -> name.length() >= 3);

long count = selected.count();
List<String> list = selected.toList();
~~~

Lời gọi `count` đã dùng pipeline. Không được dùng lại `selected` cho `toList`. Trong chương trình minh chứng, việc dùng lại bị phát hiện và gây `IllegalStateException`.

Không nên dùng ngoại lệ làm cơ chế kiểm tra xem Stream còn dùng được hay không. Hợp đồng cấm việc tái sử dụng; một cách cài đặt không nhất thiết phát hiện được mọi cách dùng lại. Khi đã chuyển pipeline cho một thao tác kết thúc, hãy coi nó đã được tiêu thụ.

Cũng không thể tách hai nhánh từ cùng một Stream như tách từ một Collection:

~~~java
Stream<String> base = names.stream();
Stream<String> a = base.filter(name -> name.startsWith("L"));
// Tiếp tục tạo nhánh khác từ base không phải cách tái sử dụng hợp lệ.
~~~

Các bước trong pipeline liên kết với nhau. Biến `base` không trở thành một kho dữ liệu chung cho nhiều nhánh truy vấn độc lập.

### Cách 1: tạo Stream mới từ nguồn

~~~java
long count = names.stream()
        .filter(name -> name.length() >= 3)
        .count();

List<String> list = names.stream()
        .filter(name -> name.length() >= 3)
        .toList();
~~~

Hai lượt đọc lại nguồn. Nếu nguồn thay đổi giữa hai lượt hoặc phép xử lý đắt, hai kết quả hoặc chi phí có thể khác điều người viết mong muốn.

### Cách 2: giữ kết quả đã tính

~~~java
List<String> list = names.stream()
        .filter(name -> name.length() >= 3)
        .toList();

int count = list.size();
~~~

Bây giờ dữ liệu cần giữ nằm trong `list`. Khi cần dùng lại nhiều lần, một Collection kết quả thường diễn tả yêu cầu rõ hơn việc cố giữ một Stream đã tiêu thụ.

### Cách 3: giữ hàm tạo pipeline mới

~~~java
Supplier<Stream<String>> selectedNames =
        () -> names.stream().filter(name -> name.length() >= 3);

long count = selectedNames.get().count();
List<String> list = selectedNames.get().toList();
~~~

Ở đây mỗi lời gọi `get` tạo một Stream mới vì thân lambda gọi lại `names.stream()`. Điều đó do cách viết lambda này, không phải bảo đảm chung của mọi `Supplier`. Hợp đồng `Supplier` không bắt buộc luôn trả đối tượng mới. [Java SE 17 — Supplier](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/Supplier.html).

Một supplier chỉ trả lại cùng một Stream đã tạo sẵn vẫn có lỗi tái sử dụng. Giữ được “công thức” nghĩa là giữ một hàm thực sự dựng lượt xử lý mới, không phải đổi tên biến chứa pipeline cũ.

## 7. Stream không phải bản chụp tự động của Collection

Trong ví dụ với `ArrayList`:

~~~java
List<String> values = new ArrayList<>(List.of("A"));
Stream<String> delayed = values.stream().filter(value -> true);

values.add("B");
List<String> result = delayed.toList();
~~~

Kết quả là `[A, B]`. Tạo Stream trước khi thêm `B` không tự sao chép mảng dữ liệu của `ArrayList` để giữ phiên bản `[A]`.

Các nguồn JDK được thiết kế phù hợp có thể phản ánh thay đổi trước khi thao tác kết thúc bắt đầu; đây là điều được mô tả trong phần không can thiệp vào nguồn của thư viện Stream. Không suy ra cùng hành vi cho mọi nguồn tùy biến hoặc cho việc sửa nguồn trong lúc đang xử lý. [Java SE 17 — Non-interference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html#NonInterference).

Sau khi đã thu thành `result`, nếu thêm `C` vào `values`, các vị trí của `result` vẫn là `[A, B]`. Một Stream mới đọc lại `values` có thể cho `[A, B, C]`.

Phân biệt ba thời điểm:

| Thời điểm | Đối tượng đang có |
| --- | --- |
| Sau khi tạo nguồn | Collection chứa dữ liệu |
| Sau khi dựng pipeline, chưa chạy terminal | Mô tả xử lý gắn với nguồn; không tự có bản sao kết quả |
| Sau khi thu thành danh sách | Collection kết quả giữ các phần tử đã thu trong lần xử lý ấy |

Nếu cần cố định dữ liệu trước khi xây dựng một lượt tính toán, hãy chọn cách sao chép phù hợp. Một bản sao cấu trúc chỉ tách các vị trí của collection; phần tử là đối tượng sửa được vẫn có thể dùng chung. Nếu có truy cập đồng thời, cả thời điểm sao chép và mức nhất quán của nguồn đều cần được thiết kế riêng.

## 8. filter và map không tự sửa nguồn

Với nguồn:

~~~text
[Lan, An, Mai, Bo]
~~~

pipeline lọc tên dài rồi viết hoa có thể trả:

~~~text
[LAN, MAI]
~~~

Nguồn không trở thành `[LAN, MAI]` chỉ vì đã dùng `filter` và `map`. `filter` quyết định phần tử nào đi tiếp; `map` tính giá trị đầu ra. Chúng không có nghĩa “xóa phần tử nguồn” hoặc “gán phần tử mới vào nguồn”.

Đối chiếu:

| Yêu cầu | Cách diễn tả |
| --- | --- |
| Tạo kết quả gồm các phần tử đạt điều kiện | Stream `filter` rồi thu kết quả |
| Xóa phần tử không đạt khỏi một Collection sửa được | Phép sửa như `removeIf` với điều kiện tương ứng |
| Tính giá trị mới cho kết quả | Stream `map` |
| Thay các vị trí của một List sửa được | Phép sửa như `replaceAll`, theo hợp đồng của List |

Lambda vẫn là mã Java và có thể tự viết ra tác dụng phụ. Nếu trong `map` gọi setter của đối tượng nguồn, đối tượng đó có thể bị sửa. Khi ấy chính mã bên trong lambda làm thay đổi, không phải một bảo đảm rằng “Stream sẽ tự biến nguồn thành đầu ra”.

Không nên viết:

~~~java
// Minh họa một cách làm sai với nguồn ArrayList đang được duyệt.
names.stream().forEach(name -> names.remove(name));
~~~

Đây là can thiệp vào nguồn trong lúc xử lý, kể cả khi Stream đang chạy tuần tự. Có thể gây ngoại lệ hoặc kết quả không phù hợp hợp đồng; không được dựa vào việc một lần chạy chưa ném lỗi để coi là hợp lệ.

Nếu cần tạo một danh sách mới, hãy thu kết quả. Nếu cần xóa tại chỗ, dùng thao tác sửa có hợp đồng phù hợp hoặc bộ duyệt theo đúng quy tắc của cấu trúc nguồn. Việc chọn đúng mô hình giúp tránh trộn lẫn giữ dữ liệu và mô tả biến đổi.

## 9. Giữ kết quả cũng cần chọn quyền sửa và mức sao chép

`Stream.toList()` trả về danh sách không cho sửa qua các thao tác thay đổi danh sách. Nó không có nghĩa là một `ArrayList` sửa được. [Java SE 17 — Stream.toList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList()).

Nếu cần kết quả thêm/xóa được:

~~~java
List<String> editable = names.stream()
        .filter(name -> name.length() >= 3)
        .collect(Collectors.toCollection(ArrayList::new));
~~~

`Collectors.toCollection` cho chọn cấu trúc đích rõ ràng. [Java SE 17 — Collectors.toCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#toCollection(java.util.function.Supplier)).

Sau đó `editable.add("NEW")` chỉ thêm vào kết quả, không thêm tên vào `names`. Cấu trúc kết quả và cấu trúc nguồn là hai đối tượng có những vị trí riêng.

Nhưng nếu phần tử là đối tượng có trạng thái sửa được:

~~~java
StringBuilder item = new StringBuilder("seed");
List<StringBuilder> source = List.of(item);
List<StringBuilder> result = source.stream().toList();

result.get(0).append("!");
~~~

`source.get(0)` cũng biểu diễn `seed!`. Cả hai nơi giữ tham chiếu tới cùng `StringBuilder`. Danh sách kết quả không cho thêm/xóa vẫn có thể chứa đối tượng có trạng thái sửa được.

Do đó, “không sửa nguồn bằng phép lọc/ánh xạ thông thường” không đồng nghĩa “đầu ra là bản sao sâu không liên quan gì đến đầu vào”. Muốn biến thành dữ liệu độc lập ở cấp đối tượng, phải thực hiện phép sao chép hoặc tạo đối tượng đầu ra phù hợp.

## 10. Stream có thể có nguồn không phải Collection

Stream còn có thể xuất phát từ mảng, bộ sinh giá trị, khoảng số hoặc tài nguyên I/O. Không cần tạo sẵn một danh sách mới để làm nguồn cho mọi bài toán.

Ví dụ:

~~~java
List<Integer> firstFour = Stream.iterate(1, n -> n + 1)
        .limit(4)
        .toList();
~~~

Kết quả là `[1, 2, 3, 4]`. Nguồn `iterate` không cần giữ trước một danh sách chứa tất cả các số sẽ được sinh. Pipeline chỉ lấy đủ số phần tử mà yêu cầu kết quả cần.

Từ đây xuất hiện sự khác biệt quan trọng: Collection có kích thước hữu hạn trong mô hình của nó, nhưng một nguồn Stream có thể tiếp tục sinh phần tử mà không có điểm dừng được xác định trước.

Không phải cứ đặt `limit` ở đâu đó là mọi pipeline đều chắc chắn kết thúc. Một bước lọc trước đó có thể không bao giờ đưa ra phần tử để `limit` đếm. Một bước sắp xếp trên nguồn vô hạn cũng không thể hoàn tất việc nhận toàn bộ đầu vào để quyết định thứ tự đầy đủ.

Vì vậy, khi nguồn có thể không hữu hạn, hãy suy nghĩ theo dòng dữ liệu: bước nào cần đợi bao nhiêu đầu vào, bước nào tạo được đầu ra, và điều kiện dừng có thực sự đạt được không. Đây là hệ quả của mô hình xử lý, không phải vấn đề một danh sách lớn hay nhỏ.

## 11. Không suy ra hiệu năng chỉ từ chữ Stream

Một pipeline lọc–ánh xạ–tính tổng có thể kết hợp các bước và tránh tạo các danh sách trung gian đầy đủ. Nhưng Stream vẫn có chi phí quản lý pipeline, lời gọi hàm và những yêu cầu riêng của từng phép xử lý.

`sorted` có thể cần bộ đệm. `distinct` có thể cần giữ thông tin những giá trị đã gặp. Thu kết quả bằng `toList` vẫn phải giữ dữ liệu kết quả. Nếu đầu ra có hàng triệu phần tử thì đổi cách viết vòng lặp sang Stream không làm dữ liệu đầu ra tự biến mất khỏi bộ nhớ.

Cũng không có bảo đảm rằng dùng `parallelStream` luôn nhanh hơn. Chi phí chia việc, nguồn dữ liệu, thứ tự, phép gộp và công việc trên mỗi phần tử đều ảnh hưởng kết quả. Đặc biệt, viết lambda sửa một danh sách dùng chung từ nhiều luồng không được làm an toàn chỉ bằng cách gọi `parallel`.

Với bài toán đơn giản, một vòng lặp có thể dễ hiểu hơn. Với một chuỗi lọc và tính kết quả rõ ràng, Stream có thể diễn tả ý định tốt. Chọn cách viết dựa trên hợp đồng, độ rõ và phép đo phù hợp nếu hiệu năng là yêu cầu thật sự.

### Spliterator nằm ở ranh giới cung cấp phần tử

Trong nhóm kiến thức này, có thể hiểu `Spliterator` là một cơ chế để duyệt nguồn và, khi phù hợp, chia nguồn thành các phần. `Collection.stream()` dùng cơ chế của nguồn để xây dựng Stream tuần tự. [Java SE 17 — Collection.stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#stream()).

Nó không biến Stream thành kho lưu phần tử. Nguồn vẫn cung cấp dữ liệu; pipeline vẫn mô tả các bước xử lý. Việc một nguồn có thể chia ra không đồng nghĩa mọi pipeline phải chạy song song hoặc mọi thao tác có thể bỏ qua yêu cầu thứ tự.

`BaseStream.iterator()` và `spliterator()` là các terminal đặc biệt trao quyền duyệt thủ công cho bên gọi. Trong các trường hợp ấy, việc lấy từng phần tử tiếp tục qua bộ duyệt được trả về, thay vì terminal phải xử lý xong mọi dữ liệu trước khi trả về. Bài dùng các terminal thông thường như `toList`, `count`, `sum` và `findFirst` để quan sát mô hình thực hiện.

## 12. Tiêu thụ Stream và đóng tài nguyên là hai việc

Thao tác kết thúc dùng pipeline để tính kết quả. Đóng Stream gọi các hàm xử lý đóng và giải phóng tài nguyên theo nguồn. Hai việc này không được coi là đồng nghĩa.

`BaseStream` triển khai `AutoCloseable`; các hàm được đăng ký bằng `onClose` chạy khi Stream được đóng. [Java SE 17 — BaseStream.close](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/BaseStream.html#close()).

Trong phần minh chứng, `count` hoàn tất nhưng hàm `onClose` chưa chạy cho đến khi thoát `try-with-resources`. Đây là cách quan sát sự khác nhau mà không cần mở một tệp thật.

Stream tạo từ Collection trong bộ nhớ thường không mang tài nguyên I/O cần đóng. Với nguồn như `Files.lines`, phải tuân thủ hợp đồng tài nguyên của phương thức:

~~~java
// Minh họa bên trong một phương thức có xử lý IOException.
// path là Path do ứng dụng cung cấp.
try (Stream<String> lines = Files.lines(path)) {
    long nonBlank = lines.filter(line -> !line.isBlank()).count();
    System.out.println(nonBlank);
}
~~~

Cần import `java.nio.file.Files`, `java.util.stream.Stream` và xử lý ngoại lệ I/O phù hợp. Đây là đoạn minh họa phụ thuộc tệp đầu vào, không có stdout cố định như chương trình độc lập phía dưới.

`Files.lines` đọc các dòng lười và giữ tham chiếu tới tệp đang mở; đóng Stream sẽ đóng tệp. Tài liệu yêu cầu dùng `try-with-resources` hoặc cơ chế tương đương để đóng kịp thời. [Java SE 17 — Files.lines](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#lines(java.nio.file.Path)).

Không trả một Stream của tệp ra khỏi khối đã đóng rồi yêu cầu nơi khác đọc nó. Nếu API trả Stream gắn tài nguyên cho bên gọi, quyền sở hữu và trách nhiệm đóng phải được công bố rõ ràng.

## 13. Chương trình minh chứng đầy đủ

Lưu mã sau thành **CollectionAndStreamDemo.java**. Chương trình dùng API tương thích Java 17, không cần thư viện ngoài và không đọc/ghi tệp.

Các danh sách `trace`, `checked` và `lifecycle` chỉ ghi dấu cho các ví dụ quan sát tuần tự. Kết quả không phải bảo đảm về thứ tự tác dụng phụ của một pipeline song song hoặc về số lần gọi lambda trong mọi phép tối ưu.

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectionAndStreamDemo {
    private static void rejects(
            String label,
            Class<? extends RuntimeException> expected,
            Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " did not fail");
        } catch (RuntimeException ex) {
            if (!expected.isInstance(ex)) {
                throw ex;
            }
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        List<String> names = new ArrayList<>(List.of("Lan", "An", "Mai", "Bo"));
        List<String> trace = new ArrayList<>();

        Stream<String> plan = names.stream()
                .filter(name -> {
                    trace.add("filter:" + name);
                    return name.length() >= 3;
                })
                .map(name -> {
                    trace.add("map:" + name);
                    return name.toUpperCase(Locale.ROOT);
                });

        System.out.println("before-terminal=" + trace);
        List<String> result = plan.toList();
        System.out.println("result=" + result);
        System.out.println("after-terminal=" + trace);
        System.out.println("source=" + names);
        rejects("reuse-plan", IllegalStateException.class, () -> plan.count());

        System.out.println("fresh-count=" + names.stream().count());
        System.out.println("long-name-count="
                + names.stream().filter(name -> name.length() >= 3).count());
        System.out.println("total-length=" + names.stream().mapToInt(String::length).sum());

        Supplier<Stream<String>> longNames =
                () -> names.stream().filter(name -> name.length() >= 3);
        System.out.println("factory-count=" + longNames.get().count());
        System.out.println("factory-list=" + longNames.get().toList());

        List<String> changing = new ArrayList<>(List.of("A"));
        Stream<String> delayed = changing.stream().filter(value -> true);
        changing.add("B");
        List<String> captured = delayed.toList();
        System.out.println("before-start-change=" + captured);
        changing.add("C");
        System.out.println("materialized-result=" + captured);
        System.out.println("new-query=" + changing.stream().toList());

        List<String> checked = new ArrayList<>();
        String first = names.stream()
                .filter(name -> {
                    checked.add(name);
                    return name.startsWith("M");
                })
                .findFirst()
                .orElse("none");
        System.out.println("first-match=" + first);
        System.out.println("checked=" + checked);

        List<Integer> generated = Stream.iterate(1, n -> n + 1)
                .limit(4)
                .toList();
        System.out.println("generated=" + generated);

        rejects("result-add", UnsupportedOperationException.class, () -> result.add("NEW"));
        List<String> editable = names.stream()
                .filter(name -> name.length() >= 3)
                .collect(Collectors.toCollection(ArrayList::new));
        editable.add("NEW");
        System.out.println("editable=" + editable);
        System.out.println("source-still=" + names);

        StringBuilder item = new StringBuilder("seed");
        List<StringBuilder> objects = List.of(item);
        List<StringBuilder> objectResult = objects.stream().toList();
        objectResult.get(0).append("!");
        System.out.println("shared-object=" + objects.get(0));

        List<String> lifecycle = new ArrayList<>();
        try (Stream<String> closable = Stream.of("A")
                .onClose(() -> lifecycle.add("closed"))) {
            System.out.println("resource-count=" + closable.count());
            System.out.println("after-terminal-close=" + lifecycle);
        }
        System.out.println("after-close=" + lifecycle);
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 CollectionAndStreamDemo.java
java CollectionAndStreamDemo
~~~

Stdout chính xác:

~~~text
before-terminal=[]
result=[LAN, MAI]
after-terminal=[filter:Lan, map:Lan, filter:An, filter:Mai, map:Mai, filter:Bo]
source=[Lan, An, Mai, Bo]
reuse-plan -> IllegalStateException
fresh-count=4
long-name-count=2
total-length=10
factory-count=2
factory-list=[Lan, Mai]
before-start-change=[A, B]
materialized-result=[A, B]
new-query=[A, B, C]
first-match=Mai
checked=[Lan, An, Mai]
generated=[1, 2, 3, 4]
result-add -> UnsupportedOperationException
editable=[Lan, Mai, NEW]
source-still=[Lan, An, Mai, Bo]
shared-object=seed!
resource-count=1
after-terminal-close=[]
after-close=[closed]
~~~

Mã đã biên dịch với `--release 17` và chạy trên JDK 24.0.2. Cờ biên dịch xác minh mức ngôn ngữ/API Java 17; chương trình không được tuyên bố là đã chạy trên JVM 17.

Đối chiếu theo nhóm:

- `before-terminal` rỗng, còn `after-terminal` ghi các bước đã chạy khi lấy kết quả.
- `result` có hai chuỗi viết hoa, trong khi `source` vẫn giữ bốn tên ban đầu.
- `reuse-plan` phát hiện việc dùng lại pipeline đã tiêu thụ.
- Các dòng đếm, tính tổng và factory dùng Stream mới cho từng lượt.
- `before-start-change` nhìn thấy thay đổi ở `ArrayList` trước khi terminal bắt đầu; `materialized-result` sau đó đã là dữ liệu kết quả riêng.
- `first-match` và `checked` cho thấy lượt tìm kiếm tuần tự không cần kiểm tra `Bo` sau khi tìm thấy `Mai`.
- `generated` có nguồn sinh giá trị, không cần một Collection đã chứa sẵn các số.
- `result-add` và `editable` phân biệt quyền sửa các dạng danh sách đầu ra.
- `shared-object` minh họa cùng một đối tượng phần tử có thể còn được nguồn và kết quả tham chiếu.
- Hai dòng cuối phân biệt hoàn tất terminal với việc đóng Stream.

## 14. Thực hành có đáp án

### Bài 1 — Pipeline đã có dữ liệu kết quả chưa?

Sau `Stream<String> s = names.stream().filter(name -> name.length() >= 3)`, có thể coi `s` là danh sách đã lọc để gọi `get(0)` không?

**Đáp án:** Không. `s` là một Stream mô tả đường xử lý, không có phương thức `get` của List và chưa tạo danh sách kết quả. Nếu cần dữ liệu giữ lại để truy cập theo chỉ số, dùng một terminal thích hợp như `toList` rồi thao tác trên List đó.

### Bài 2 — Đếm rồi lấy danh sách từ cùng Stream

Có thể gọi `s.count()` rồi `s.toList()` trên cùng `s` không? Nêu hai cách sửa theo nhu cầu.

**Đáp án:** Không được tái sử dụng Stream sau terminal. Có thể tạo Stream mới từ nguồn cho mỗi lượt, hoặc tính danh sách một lần rồi lấy `size` của danh sách nếu cả hai kết quả cần dựa trên cùng dữ liệu đã thu.

### Bài 3 — Lọc có xóa nguồn không?

Nguồn là `[Lan, An, Mai, Bo]`. Pipeline lọc tên dài ít nhất ba ký tự rồi viết hoa trả `[LAN, MAI]`. Nguồn có còn `An` và `Bo` không?

**Đáp án:** Có, nguồn vẫn giữ bốn tên nếu lambda không tự sửa nguồn hoặc đối tượng nguồn. `filter` chỉ chọn các phần tử đi tiếp trong pipeline. Muốn xóa tại chỗ phải chọn một thao tác sửa có hợp đồng phù hợp.

### Bài 4 — Stream có đóng băng dữ liệu lúc tạo không?

Tạo Stream từ `ArrayList` đang chứa `A`, thêm `B` trước terminal, rồi thu danh sách. Vì sao kết quả ví dụ là `[A, B]`?

**Đáp án:** Stream từ nguồn này không tự chép dữ liệu thành bản chụp ở lúc gọi `stream`. Thay đổi xảy ra trước khi lượt xử lý bắt đầu được nguồn JDK phù hợp phản ánh. Không dùng điều này để biện minh việc sửa nguồn trong lúc terminal đang chạy hoặc để suy ra hành vi của mọi nguồn tùy biến.

### Bài 5 — Vì sao không đặt công việc bắt buộc trong peek?

Một pipeline gọi `peek` để ghi nhận mọi phần tử rồi kết thúc bằng `count`. Có được dùng số đếm đúng để chứng minh mọi lần ghi nhận đã chạy không?

**Đáp án:** Không. Cách cài đặt có thể suy ra số lượng mà không duyệt các giai đoạn không ảnh hưởng kết quả. Dùng cơ chế thực hiện hành động tường minh, chẳng hạn vòng lặp hoặc terminal phù hợp, thay vì dựa vào một tác dụng phụ có thể bị bỏ qua khi tối ưu.

### Bài 6 — Supplier nào thật sự tạo lượt xử lý mới?

`Supplier<Stream<String>> factory = () -> names.stream()` khác gì một supplier chỉ trả lại biến `savedStream` đã tạo trước đó?

**Đáp án:** Lambda thứ nhất gọi lại `stream` để tạo lượt mới mỗi lần `get`. Lambda thứ hai có thể trả cùng một Stream đã bị tiêu thụ. Bản thân kiểu Supplier không bảo đảm đối tượng mới; phải xem cách nó tạo kết quả.

### Bài 7 — Kết quả không cho sửa có làm đối tượng bên trong bất biến không?

Một `List<StringBuilder>` được đưa qua `stream().toList()`. Có thể sửa `StringBuilder` lấy từ kết quả không? Nguồn có thấy thay đổi không?

**Đáp án:** Danh sách đầu ra không cho thêm/xóa hay thay vị trí bằng mutator của List, nhưng đối tượng `StringBuilder` vẫn sửa được. Nếu nguồn và đầu ra dùng chung tham chiếu, nguồn cũng nhìn thấy trạng thái mới. Cần phép sao chép đối tượng hoặc kiểu bất biến nếu yêu cầu tách trạng thái.

### Bài 8 — Terminal xong có chắc đã đóng tệp không?

Sau `Files.lines(path).count()`, có được bỏ trách nhiệm đóng Stream vì `count` đã là terminal không?

**Đáp án:** Không. Tiêu thụ pipeline và đóng tài nguyên là hai việc. Stream của `Files.lines` phải được đóng theo hợp đồng, thường bằng `try-with-resources`, kể cả khi terminal đã hoàn thành hoặc gặp lỗi.

## 15. Cách đọc một đoạn mã dùng Collection và Stream

Trước hết tìm nơi dữ liệu được giữ và ai có quyền thay đổi nó. Sau đó đọc Stream như một lượt xử lý gồm nguồn, các giai đoạn trung gian và terminal. Xác định kết quả cần được giữ lại hay chỉ cần một giá trị tổng hợp.

Khi cần dùng lại dữ liệu, giữ Collection hoặc tạo lại Stream từ một nguồn còn dùng được. Khi cần dùng lại một công thức xử lý, giữ hàm dựng pipeline mới. Khi nguồn có tài nguyên, xác định trách nhiệm đóng; khi có đối tượng sửa được, phân biệt bản sao cấu trúc và bản sao trạng thái.

Mô hình chính của bài là: **Collection biểu diễn nhóm dữ liệu; Stream biểu diễn một lượt tính toán trên nguồn; terminal tạo kết quả mà ứng dụng cần.** Hiểu được ba vai trò này giúp tránh coi Stream là một danh sách tạm, tránh chạy lại pipeline đã dùng và lựa chọn đúng nơi lưu kết quả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="streams-spliterators-002">stream, parallelStream và giới hạn không can thiệp nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# stream, parallelStream và giới hạn không can thiệp nguồn

## 1. Stream là một phép xử lý trên nguồn dữ liệu

Giả sử một danh sách chứa các số từ 1 đến 10. Ta muốn lấy số chẵn, bình phương từng số rồi tạo danh sách kết quả. Có thể mô tả công việc bằng một chuỗi thao tác: nguồn → lọc → biến đổi → thu thập.

`Stream` là mô hình biểu diễn chuỗi xử lý đó. Nó không phải một danh sách chứa sẵn bản sao của dữ liệu, cũng không phải một luồng thực thi riêng theo nghĩa `Thread`. Trong tiếng Việt, cần phân biệt:

- **Luồng phần tử (stream):** dòng dữ liệu đi qua các bước xử lý.
- **Luồng thực thi (thread):** một đơn vị thực thi mã trong chương trình.

Một stream có thể được xử lý tuần tự hoặc song song. Khi xử lý song song, thư viện có thể phân chia công việc cho các luồng thực thi, nhưng điều đó không thay đổi trách nhiệm của người viết mã: nguồn dữ liệu và các hàm xử lý phải đáp ứng hợp đồng của stream.

Một pipeline, tức chuỗi xử lý stream, có ba thành phần:

| Thành phần | Vai trò | Ví dụ |
| --- | --- | --- |
| Nguồn dữ liệu | Cung cấp các phần tử | Một `List<Integer>` |
| Thao tác trung gian | Mô tả lọc, biến đổi hoặc tổ chức các phần tử | `filter`, `map`, `sorted` |
| Thao tác kết thúc | Yêu cầu tạo kết quả hoặc thực hiện hành động cuối | `toList`, `sum`, `count`, `forEach` |

Ví dụ minh họa, đặt trong một phương thức đã có biến `numbers` và các `import` cần thiết:

```java
List<Integer> squares = numbers.stream()
        .filter(n -> n % 2 == 0)
        .map(n -> n * n)
        .toList();
```

`filter` không xóa số lẻ khỏi `numbers`. Nó chọn những phần tử được đi tiếp trong pipeline. `map` không thay từng ô của danh sách nguồn bằng bình phương; nó tạo giá trị đầu ra cho bước tiếp theo.

Đó là lý do stream phù hợp với việc mô tả **kết quả cần tạo**. Nếu muốn trực tiếp xóa phần tử khỏi một collection, cần chọn thao tác sửa collection phù hợp thay vì sửa nguồn từ bên trong pipeline đang duyệt nó.

Bài này tập trung vào ba câu hỏi: khi nào nguồn được đọc, chế độ tuần tự hoặc song song ảnh hưởng điều gì, và giới hạn nào phải giữ để quá trình đọc không bị can thiệp.

## 2. Tạo pipeline chưa có nghĩa là đã duyệt xong nguồn

Các thao tác trung gian được đánh giá trì hoãn: gọi `filter` hoặc `map` thường mới xây dựng mô tả xử lý. Việc tính kết quả được yêu cầu khi gọi thao tác kết thúc.

Vì vậy, không nên hình dung `source.stream()` là “chụp toàn bộ source ngay lập tức”. Thời điểm nguồn được ràng buộc với quá trình duyệt còn phụ thuộc loại nguồn và `Spliterator` của nó.

Với `ArrayList` trong chương trình dưới đây, thêm phần tử sau khi tạo stream nhưng trước khi bắt đầu thao tác kết thúc sẽ được phản ánh trong kết quả. Đây không phải việc sửa nguồn trong lúc pipeline đang chạy.

Lưu vào `StreamLifecycleDemo.java`.

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class StreamLifecycleDemo {
    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("a", "bb", "ccc"));
        Stream<String> pipeline = source.stream()
                .filter(value -> value.length() >= 2)
                .map(value -> value.toUpperCase(Locale.ROOT));

        System.out.println("sizeWhenPipelineCreated=" + source.size());
        source.add("dddd");
        System.out.println("sizeBeforeTerminal=" + source.size());

        List<String> result = pipeline.toList();
        System.out.println("result=" + result);
        System.out.println("sourceAfterTerminal=" + source);

        try {
            pipeline.count();
        } catch (IllegalStateException e) {
            System.out.println("reuse=IllegalStateException");
        }

        source.add("eeeee");
        System.out.println("resultAfterLaterAdd=" + result);
        System.out.println("newStreamCount=" + source.stream().count());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 StreamLifecycleDemo.java
java StreamLifecycleDemo
```

Stdout:

```text
sizeWhenPipelineCreated=3
sizeBeforeTerminal=4
result=[BB, CCC, DDDD]
sourceAfterTerminal=[a, bb, ccc, dddd]
reuse=IllegalStateException
resultAfterLaterAdd=[BB, CCC, DDDD]
newStreamCount=5
```

Chương trình có ba mốc thời gian cần tách biệt:

```text
Tạo ArrayList
    ↓
Tạo stream và khai báo filter/map
    ↓
Thêm dddd vào nguồn trước thao tác kết thúc
    ↓
Gọi toList: thực hiện pipeline và nhận kết quả
    ↓
Thêm eeeee vào nguồn sau khi pipeline đã xong
```

Khi tạo pipeline, nguồn có ba phần tử. Trước `toList`, nguồn có thêm `dddd`, nên kết quả chứa `DDDD`. Hai bước lọc và chuyển chữ hoa chỉ ảnh hưởng kết quả: danh sách nguồn vẫn là `[a, bb, ccc, dddd]`.

Sau đó, thêm `eeeee` không tự cập nhật danh sách kết quả đã tạo. Muốn xử lý trạng thái mới của nguồn, chương trình gọi `source.stream()` lần nữa.

### Một stream chỉ dùng cho một lần xử lý

Sau thao tác kết thúc, pipeline đã được tiêu thụ. Không dùng lại nó để thực hiện phép tổng hợp thứ hai, cũng không tách một đối tượng stream thành hai nhánh sử dụng độc lập.

Trong ví dụ, gọi `pipeline.count()` sau `pipeline.toList()` ném `IllegalStateException`. Quy tắc là không tái sử dụng stream; không xây chương trình dựa trên giả định rằng mọi cách tái sử dụng sai đều chắc chắn được phát hiện. [Javadoc Stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html)

Giữ nguồn và tạo stream mới khi cần duyệt lại là hai việc khác với giữ một stream đã chạy. Stream từ collection thông thường cũng không đại diện cho một tiến trình luôn theo dõi mọi lần cập nhật tiếp theo của collection.

### Kết quả trong ví dụ có yêu cầu phiên bản nào?

`Stream.toList()` có từ Java 16, nên dùng được với Java 17 trong bài. Nó trả về danh sách không cho sửa cấu trúc. Không mặc định rằng có thể gọi `result.add(...)` trên kết quả đó.

Nếu cần một danh sách cho phép thêm hoặc xóa, có thể yêu cầu rõ một collection kết quả thích hợp; phần thu thập kết quả ở dưới dùng `Collectors.toCollection(ArrayList::new)`.

## 3. stream và parallelStream khác nhau ở chế độ thực thi

`Collection.stream()` tạo stream tuần tự. `parallelStream()` cho phép yêu cầu xử lý song song; cách triển khai mặc định tạo stream ở chế độ song song, dù hợp đồng giao diện `Collection` cho phép một cách triển khai trả về stream tuần tự. Có thể kiểm tra chế độ thực tế bằng `isParallel()`. [Javadoc Collection.stream và parallelStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html)

Với các collection JDK được dùng trong bài:

| Cách gọi | Ý nghĩa |
| --- | --- |
| `source.stream()` | Bắt đầu với chế độ tuần tự |
| `source.parallelStream()` | Bắt đầu với chế độ song song |
| `stream.parallel()` | Đặt chế độ xử lý song song cho pipeline |
| `stream.sequential()` | Đặt chế độ xử lý tuần tự cho pipeline |
| `stream.isParallel()` | Hỏi chế độ hiện tại, không phải đếm số luồng thực thi |

Không đọc một chuỗi có `parallel()` ở giữa rồi kết luận “các bước trước chạy tuần tự, các bước sau chạy song song”. Chế độ được đặt gần nhất áp dụng cho việc thực thi toàn pipeline khi thao tác kết thúc yêu cầu xử lý.

### Thí nghiệm về chế độ và thứ tự kết quả

Lưu vào `StreamExecutionModeDemo.java`.

```java
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.IntStream;

public class StreamExecutionModeDemo {
    public static void main(String[] args) {
        List<Integer> source = IntStream.rangeClosed(1, 10).boxed().toList();

        System.out.println("streamParallel=" + source.stream().isParallel());
        System.out.println("parallelStreamParallel="
                + source.parallelStream().isParallel());
        System.out.println("afterSequential="
                + source.parallelStream().sequential().isParallel());
        System.out.println("afterParallel="
                + source.stream().parallel().isParallel());

        List<Integer> sequential = source.stream()
                .filter(n -> n % 2 == 0)
                .map(n -> n * n)
                .toList();
        List<Integer> parallel = source.parallelStream()
                .filter(n -> n % 2 == 0)
                .map(n -> n * n)
                .toList();

        System.out.println("sequential=" + sequential);
        System.out.println("parallel=" + parallel);
        System.out.println("sameResult=" + sequential.equals(parallel));

        StringJoiner ordered = new StringJoiner(",");
        source.parallelStream()
                .filter(n -> n % 2 == 0)
                .forEachOrdered(n -> ordered.add(n.toString()));
        System.out.println("orderedActions=" + ordered);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 StreamExecutionModeDemo.java
java StreamExecutionModeDemo
```

Stdout:

```text
streamParallel=false
parallelStreamParallel=true
afterSequential=false
afterParallel=true
sequential=[4, 16, 36, 64, 100]
parallel=[4, 16, 36, 64, 100]
sameResult=true
orderedActions=2,4,6,8,10
```

Hai danh sách `sequential` và `parallel` bằng nhau vì cùng thực hiện phép lọc, biến đổi độc lập trên nguồn có thứ tự. Trong pipeline này, `toList()` giữ thứ tự gặp phần tử của stream, nên kết quả vẫn là `[4, 16, 36, 64, 100]`.

Không thể từ kết quả có thứ tự suy ra rằng các phép bình phương đã chạy lần lượt trên cùng một luồng thực thi. **Thứ tự của kết quả** và **thời điểm thực hiện mỗi phép tính** là hai vấn đề khác nhau.

Dòng `orderedActions` dùng `forEachOrdered`. Khi stream có thứ tự gặp, thao tác này thực hiện hành động theo thứ tự đó. Các hành động được thực hiện lần lượt và có quan hệ hoàn tất trước giữa hành động trước với hành động sau; vì vậy `StringJoiner` trong ví dụ không bị nhiều hành động sửa đồng thời. Điều này không cam kết tất cả hành động chạy trên cùng một luồng thực thi. [Javadoc forEachOrdered](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#forEachOrdered(java.util.function.Consumer))

Nếu đổi thành `forEach` trên parallel stream, không còn bảo đảm thứ tự gặp cho các hành động, cũng không thể dùng chung `StringJoiner` như thể chỉ có một bên sửa. Muốn thu thập dữ liệu, nên dùng thao tác thu thập thay vì tự sửa một đối tượng kết quả bên ngoài.

### Spliterator giúp phân chia nguồn, không làm nguồn tự an toàn

`Spliterator` có nhiệm vụ hỗ trợ duyệt và phân chia phần tử. Khi xử lý song song, thư viện có thể chia nguồn thành các phần, xử lý rồi kết hợp kết quả. Khả năng chia cân bằng và chi phí chia ảnh hưởng lợi ích của cách thực thi này.

Các đặc tính như `ORDERED`, `SIZED`, `IMMUTABLE` hoặc `CONCURRENT` mô tả những bảo đảm của quá trình duyệt. Chúng không phải bằng chứng rằng mọi đối tượng phần tử hoặc mọi hàm do người dùng truyền vào đều an toàn khi chạy đồng thời. [Javadoc Spliterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Spliterator.html)

Do đó, `parallelStream()` không biến `ArrayList` thành collection cho phép nhiều bên tùy ý thêm hoặc xóa trong lúc duyệt. Nó cũng không cấp một luồng thực thi riêng cho mỗi phần tử, không cam kết một số luồng cố định và không tự bảo vệ trạng thái dùng chung của các hàm xử lý.

## 4. Không can thiệp nguồn là điều kiện của cả hai chế độ

Với nguồn thông thường không hỗ trợ cập nhật đồng thời, các hàm được truyền vào pipeline phải không sửa nguồn đang được xử lý. Điều này áp dụng cả khi dùng `stream()`, không chỉ `parallelStream()`.

Trong các thao tác kết thúc thông thường của bài, khoảng thời gian cần bảo vệ bắt đầu khi gọi thao tác kết thúc và kết thúc khi thao tác đó hoàn tất. Các cách lấy `iterator()` hoặc `spliterator()` để tự điều khiển việc duyệt là trường hợp riêng: trách nhiệm bảo vệ phải kéo dài theo việc duyệt thực tế. [Quy tắc non-interference của Stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html#NonInterference)

### “Nguồn” là đối tượng thực, không chỉ là tên biến

Xét đoạn mã sai sau. Đây là đoạn minh họa lỗi trong một phương thức, không phải chương trình độc lập và không có stdout được bảo đảm:

```java
List<Integer> source = new ArrayList<>(List.of(1, 2, 3, 4));

source.stream().forEach(n -> {
    if (n == 2) {
        source.add(99);
    }
});
```

Khi `forEach` đang duyệt `source`, hành động lại thêm phần tử vào chính `source`. Dùng `stream()` tuần tự không làm việc đó hợp lệ. Việc đọc và sửa có thể xung đột về quy tắc duyệt ngay trên một luồng thực thi.

Cũng không tránh được vấn đề bằng cách sửa qua một tên khác:

```java
// Đoạn minh họa sai; source là ArrayList đang được stream duyệt.
List<Integer> alias = source;
source.stream().forEach(n -> alias.remove(n));
```

`alias` vẫn trỏ đến cùng đối tượng. Tương tự, một phương thức được gọi trong `map` hoặc `filter` có thể sửa nguồn một cách gián tiếp; không phải chỉ những lời gọi `source.add` nhìn thấy ngay trong lambda mới đáng kiểm tra.

### Không chỉ add và remove mới cần xem xét

Thêm, xóa hoặc xóa toàn bộ nguồn là những trường hợp rõ nhất. Thay phần tử bằng `set`, sắp xếp lại collection hoặc sửa một khung nhìn dùng chung cấu trúc cũng có thể làm dữ liệu được đọc thay đổi trong khi pipeline đang thực hiện.

Riêng với `ArrayList`, `set` không được phân loại là thay đổi cấu trúc theo cách theo dõi của lớp. Điều đó không biến việc thay phần tử trong lúc stream đang đọc thành một hợp đồng xử lý đáng tin. **Không làm bộ đếm thay đổi cấu trúc tăng** và **không can thiệp nguồn** không phải cùng một điều kiện.

Nguồn cũng có thể bị sửa bởi một luồng thực thi khác mà không có bất kỳ câu lệnh sửa nào trong lambda. Để chạy pipeline đúng, cần kiểm soát mọi đường sửa nguồn trong khoảng thời gian xử lý.

### ConcurrentModificationException không phải cơ chế đồng bộ

Một nguồn có cơ chế phát hiện thay đổi có thể ném `ConcurrentModificationException`. Tuy nhiên, cơ chế fail-fast chỉ hỗ trợ phát hiện lỗi trên cơ sở cố gắng phát hiện, không bảo đảm bắt được mọi can thiệp. Không có ngoại lệ cũng chưa chứng minh rằng phép tính đúng. [Javadoc ArrayList về fail-fast](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

Với đoạn mã vi phạm hợp đồng, có thể gặp ngoại lệ, kết quả không đúng hoặc hành vi không còn theo giả định của chương trình. Không viết kiểm thử bắt buộc mọi cách sửa nguồn sai đều phải ném đúng một ngoại lệ tại đúng một phần tử.

Ngoại lệ cũng không hoàn tác những thay đổi đã xảy ra trước đó. Thử sửa nguồn rồi bắt `ConcurrentModificationException` để chạy lại không phải cách an toàn để triển khai một phép xử lý dữ liệu.

### forEachOrdered không cấp quyền sửa nguồn

`forEachOrdered` điều phối thứ tự hành động cuối. Nó không thay đổi hợp đồng của nguồn. Nếu hành động thêm hoặc xóa chính danh sách đang được duyệt, vấn đề can thiệp nguồn vẫn tồn tại.

Cần tách rõ hai yêu cầu: thứ tự hành động và tính hợp lệ của việc đọc nguồn. Giải quyết yêu cầu thứ nhất không tự giải quyết yêu cầu thứ hai.

## 5. Tạo kết quả mới hoặc tách giai đoạn sửa nguồn

Có ba cách diễn đạt thường phù hợp hơn việc vừa stream vừa sửa nguồn:

1. Dùng `filter` và `map` để tạo kết quả mới.
2. Nếu muốn sửa trực tiếp collection, dùng thao tác được thiết kế cho mục đích đó, chẳng hạn `removeIf`, ngoài pipeline đang duyệt.
3. Nếu cần xử lý một trạng thái đã chọn của dữ liệu, tạo bản sao nguồn tại thời điểm được bảo vệ rồi xử lý bản sao.

Lưu vào `NonInterferingTransformDemo.java`.

```java
import java.util.ArrayList;
import java.util.List;

public class NonInterferingTransformDemo {
    public static void main(String[] args) {
        List<Integer> source = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));

        List<Integer> transformed = source.parallelStream()
                .filter(n -> n % 2 == 0)
                .map(n -> n * 10)
                .toList();
        System.out.println("transformed=" + transformed);
        System.out.println("sourceUnchanged=" + source);

        List<Integer> snapshot = new ArrayList<>(source);
        source.removeIf(n -> n % 2 == 0);

        List<Integer> fromSnapshot = snapshot.parallelStream()
                .filter(n -> n % 2 == 0)
                .map(n -> n * 10)
                .toList();
        System.out.println("sourceAfterRemoveIf=" + source);
        System.out.println("snapshot=" + snapshot);
        System.out.println("fromSnapshot=" + fromSnapshot);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 NonInterferingTransformDemo.java
java NonInterferingTransformDemo
```

Stdout:

```text
transformed=[20, 40, 60]
sourceUnchanged=[1, 2, 3, 4, 5, 6]
sourceAfterRemoveIf=[1, 3, 5]
snapshot=[1, 2, 3, 4, 5, 6]
fromSnapshot=[20, 40, 60]
```

Lần xử lý đầu lấy số chẵn rồi nhân 10. Nguồn vẫn có đủ sáu số vì pipeline chỉ tạo `transformed`.

Sau khi lần xử lý đó kết thúc, chương trình sao chép cấu trúc nguồn sang `snapshot`, rồi gọi `removeIf` trên nguồn. Khi stream của `snapshot` chạy, nó đọc danh sách riêng, nên vẫn tính từ sáu số ban đầu dù nguồn hiện tại chỉ còn `[1, 3, 5]`.

`removeIf` trong ví dụ là một thao tác riêng. Không đặt nó vào hàm xử lý của chính stream đang đọc collection đó. Biểu thức kiểm tra điều kiện cũng chỉ đọc giá trị, không tiếp tục sửa collection bằng một đường khác.

### Bản sao phải được tạo khi việc đọc nguồn hợp lệ

`new ArrayList<>(source)` không phải nút “làm an toàn đồng thời” cho mọi nguồn. Để sao chép đúng, bản thân quá trình đọc `source` lúc sao chép cũng phải được bảo vệ khỏi những cập nhật không hợp lệ.

Trong chương trình trên, chỉ có một luồng thực thi sở hữu và sửa danh sách, nên việc sao chép được thực hiện ở thời điểm ổn định. Trong ứng dụng có nhiều bên ghi, cần một cơ chế phối hợp thích hợp cho giai đoạn lấy dữ liệu; sau đó có thể xử lý bản sao độc lập về cấu trúc.

Một lớp bao không cho sửa như `Collections.unmodifiableList(source)` cũng chưa phải bản sao. Nếu bên khác vẫn giữ `source` và sửa nó, lớp bao vẫn dùng dữ liệu nền đó.

### Bản sao cấu trúc không sao chép sâu phần tử

Danh sách chứa các `Integer` trong ví dụ dùng phần tử bất biến. Với đối tượng có trạng thái thay đổi được, `new ArrayList<>(source)` vẫn giữ cùng các tham chiếu phần tử.

Ví dụ, sao chép một `List<Order>` không tự tạo các đối tượng `Order` mới. Nếu bên khác đổi trường giá trị trong những `Order` đó, pipeline trên bản sao vẫn cần được đánh giá về tính nhất quán dữ liệu.

Sửa trạng thái phần tử khác với thêm hoặc xóa khỏi collection. Không thể chỉ nhìn thấy một lời gọi setter rồi kết luận mọi trường hợp đều sai; cần xem đối tượng có được dùng chung, có xuất hiện nhiều lần, có bị đọc hoặc ghi đồng thời và có làm kết quả phụ thuộc thời điểm thực thi hay không. Khi mục tiêu là biến đổi dữ liệu, tạo đối tượng đầu ra mới thường làm trách nhiệm rõ hơn việc sửa đối tượng đang dùng chung.

## 6. Không sửa nguồn vẫn chưa đủ nếu có trạng thái dùng chung

Một pipeline có thể không động vào collection nguồn nhưng vẫn sai khi các hàm của nó cùng sửa một đối tượng bên ngoài.

Đoạn dưới minh họa lỗi, không phải chương trình mẫu để chạy và không có kết quả đầu ra đáng tin:

```java
List<Integer> output = new ArrayList<>();

numbers.parallelStream()
        .map(n -> n * 2)
        .forEach(output::add);
```

`output` không phải nguồn `numbers`, nên lỗi ở đây không phải thêm vào chính nguồn. Lỗi là nhiều hành động có thể gọi `ArrayList.add` đồng thời trên cùng một danh sách không được bảo vệ. Có thể mất phần tử, sai trạng thái hoặc gặp ngoại lệ.

Dùng danh sách có đồng bộ có thể bảo vệ từng thao tác thêm, nhưng không tự diễn đạt đúng thứ tự kết quả cần có và tạo ra điểm tranh chấp chung. Với mục tiêu thu thập kết quả, nên để thao tác thu thập của stream quản lý các vùng tích lũy.

### Thí nghiệm dùng collect và phép tổng hợp

Lưu vào `ParallelReductionDemo.java`.

```java
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ParallelReductionDemo {
    public static void main(String[] args) {
        List<Integer> source = IntStream.rangeClosed(1, 12).boxed().toList();

        List<Integer> doubled = source.parallelStream()
                .map(n -> n * 2)
                .collect(Collectors.toCollection(ArrayList::new));
        int sequentialSum = source.stream().mapToInt(Integer::intValue).sum();
        int parallelSum = source.parallelStream().mapToInt(Integer::intValue).sum();
        long evenCount = source.parallelStream().filter(n -> n % 2 == 0).count();

        System.out.println("doubled=" + doubled);
        System.out.println("sequentialSum=" + sequentialSum);
        System.out.println("parallelSum=" + parallelSum);
        System.out.println("evenCount=" + evenCount);
        System.out.println("source=" + source);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ParallelReductionDemo.java
java ParallelReductionDemo
```

Stdout:

```text
doubled=[2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24]
sequentialSum=78
parallelSum=78
evenCount=6
source=[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
```

`Collectors.toCollection(ArrayList::new)` cung cấp cách tạo các collection tích lũy do thư viện quản lý. Với cơ chế thu thập này, các phần kết quả được tích lũy riêng rồi kết hợp phù hợp; không phải mọi luồng thực thi cùng gọi `add` vào một `ArrayList` bên ngoài.

Vì thế có thể dùng `ArrayList` làm vùng tích lũy trong cách thu thập này dù `ArrayList` không tự an toàn cho mọi lời gọi đồng thời. Đây là bảo đảm từ giao thức thu thập, không phải một thay đổi về tính chất của lớp `ArrayList`. [Javadoc Collectors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html)

`sum` và `count` cũng cho phép thư viện tổ chức việc tích lũy kết quả thay vì người dùng tự tăng biến chung. Trong ví dụ, tổng từ 1 đến 12 bằng 78, có sáu số chẵn và nguồn không bị sửa.

### Kết hợp kết quả phải đúng về mặt toán học

Nếu dùng `reduce` hoặc tự xây cách thu thập, cần tuân thủ hợp đồng kết hợp. Khi chia dữ liệu thành các nhóm, kết quả không được thay đổi chỉ vì thư viện chọn cách nhóm khác.

Ví dụ với phép trừ:

```text
(10 - 3) - 2 = 5
10 - (3 - 2) = 9
```

Phép trừ không có tính kết hợp. Đổi một phép giảm dùng phép trừ sang song song rồi mong nó luôn giống cách trừ từ trái sang phải là đặt sai yêu cầu lên thư viện.

Trong ví dụ dùng số nguyên nhỏ của bài, tổng không bị tràn và không gặp vấn đề làm tròn. Với phép cộng số thực, đổi cách nhóm có thể tạo khác biệt làm tròn; không áp dụng máy móc kỳ vọng “kết quả giống từng bit” cho mọi phép tổng hợp.

### Trạng thái an toàn khi cập nhật vẫn có thể sai ý nghĩa

Giả sử dùng một bộ đếm nguyên tử trong `filter` và giữ ba phần tử có ba lần tăng đầu tiên. Bộ đếm có thể không mất cập nhật, nhưng ở chế độ song song, ba phần tử được xử lý trước không nhất thiết là ba phần tử đầu trong thứ tự nguồn.

Nếu yêu cầu là “ba phần tử đầu”, hãy diễn đạt bằng `limit(3)` trên stream có thứ tự phù hợp. Một cấu trúc an toàn đồng thời chỉ giải quyết cách truy cập trạng thái, không tự biến một quy tắc phụ thuộc lịch chạy thành đúng yêu cầu.

### Không dùng peek làm bước bắt buộc cho nghiệp vụ

Đoạn sau chỉ minh họa việc quan sát, không hứa chắc có ba dòng được in:

```java
// Đoạn minh họa; numbers là List<Integer> đã có.
long count = numbers.stream()
        .peek(System.out::println)
        .count();
```

Nếu số lượng phần tử đã biết và các bước giữa không làm thay đổi số lượng, thư viện có thể tính `count` mà không duyệt từng phần tử. Khi đó `peek` có thể không được gọi. [Javadoc Stream.count](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#count())

Ngoài ra, thao tác kết thúc ngắn mạch có thể không cần đọc hết nguồn. Đừng đặt một cập nhật bắt buộc phải xảy ra cho mọi phần tử vào `map`, `filter` hoặc `peek` rồi giả định nó luôn chạy đủ lần. Hãy thể hiện kết quả cần tính bằng giá trị trả về, phép tổng hợp hoặc một giai đoạn hành động rõ ràng.

## 7. Loại nguồn quyết định chính sách khi dữ liệu thay đổi

Không thể chỉ nhìn tên phương thức `stream()` rồi kết luận mọi loại collection đều lấy cùng một trạng thái dữ liệu.

Ba trường hợp cần phân biệt:

| Loại nguồn | Cách nhìn quá trình duyệt | Khi dữ liệu thay đổi |
| --- | --- | --- |
| `ArrayList` thông thường | Có cơ chế ràng buộc muộn và phát hiện can thiệp | Thay trước khi xử lý có thể được phản ánh; không sửa trong lúc xử lý |
| `CopyOnWriteArrayList` | Duyệt ảnh chụp của mảng tại lúc tạo bộ duyệt | Những lần thêm hoặc xóa sau đó không đi vào ảnh chụp đang duyệt |
| Tập khóa của `ConcurrentHashMap` | Bộ duyệt hỗ trợ cập nhật đồng thời, nhất quán yếu | Không hứa cho một ảnh chụp nguyên tử của toàn bộ map |

Lưu vào `SourceTraversalPoliciesDemo.java`.

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

public class SourceTraversalPoliciesDemo {
    public static void main(String[] args) {
        List<String> regular = new ArrayList<>(List.of("A", "B"));
        Stream<String> regularStream = regular.stream();
        regular.add("C");
        System.out.println("regularResult=" + regularStream.toList());
        System.out.println("regularConcurrent=" + regular.spliterator()
                .hasCharacteristics(Spliterator.CONCURRENT));

        CopyOnWriteArrayList<String> copyOnWrite =
                new CopyOnWriteArrayList<>(List.of("A", "B"));
        Stream<String> snapshotStream = copyOnWrite.stream();
        copyOnWrite.add("C");
        System.out.println("copyOnWriteResult=" + snapshotStream.toList());
        System.out.println("copyOnWriteCurrent=" + copyOnWrite);
        System.out.println("copyOnWriteImmutableTraversal="
                + copyOnWrite.spliterator()
                .hasCharacteristics(Spliterator.IMMUTABLE));
        System.out.println("copyOnWriteConcurrentTraversal="
                + copyOnWrite.spliterator()
                .hasCharacteristics(Spliterator.CONCURRENT));

        ConcurrentHashMap<String, Integer> concurrent = new ConcurrentHashMap<>();
        concurrent.put("B", 2);
        concurrent.put("A", 1);
        System.out.println("keySetConcurrentTraversal="
                + concurrent.keySet().spliterator()
                .hasCharacteristics(Spliterator.CONCURRENT));
        System.out.println("stableKeysForDisplay="
                + concurrent.keySet().stream().sorted().toList());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SourceTraversalPoliciesDemo.java
java SourceTraversalPoliciesDemo
```

Stdout:

```text
regularResult=[A, B, C]
regularConcurrent=false
copyOnWriteResult=[A, B]
copyOnWriteCurrent=[A, B, C]
copyOnWriteImmutableTraversal=true
copyOnWriteConcurrentTraversal=false
keySetConcurrentTraversal=true
stableKeysForDisplay=[A, B]
```

### Với ArrayList: không chụp ngay tại lời gọi stream

`regularStream` được tạo khi danh sách có `A` và `B`. Thêm `C` trước thao tác kết thúc khiến kết quả gồm cả `C`. Đặc tính `CONCURRENT` là `false`; đây không phải nguồn được thiết kế để nhận các cập nhật đồng thời tùy ý trong lúc stream đang đọc.

Sự ràng buộc muộn thu hẹp khoảng thời gian có thể xảy ra can thiệp. Nó không xóa yêu cầu bảo vệ trong thời gian thực thi pipeline.

### Với CopyOnWriteArrayList: ảnh chụp của quá trình duyệt

`CopyOnWriteArrayList` cho phép sửa chính danh sách, nhưng spliterator của nó duyệt một ảnh chụp mảng đã có khi spliterator được tạo. Trong chương trình, stream cũ cho `[A, B]`, còn trạng thái hiện tại của danh sách đã là `[A, B, C]`.

Đặc tính `IMMUTABLE` ở đây mô tả nguồn phần tử của bộ duyệt đó, không có nghĩa đối tượng `CopyOnWriteArrayList` không thể gọi `add`. Nó cũng không có nghĩa các đối tượng phần tử đã trở thành bất biến. [Javadoc CopyOnWriteArrayList.spliterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html#spliterator())

Không chọn lớp này chỉ để che một pipeline đang sửa nguồn sai cách. Mỗi lần ghi có chi phí sao chép; cần dùng khi chính sách ảnh chụp và đặc điểm đọc nhiều hơn ghi phù hợp với yêu cầu.

### Với ConcurrentHashMap: hỗ trợ cập nhật không đồng nghĩa chụp nguyên tử

`keySetConcurrentTraversal=true` cho thấy bộ duyệt tập khóa báo đặc tính `CONCURRENT`. Đây là ví dụ về nguồn được thiết kế để duyệt trong khi có cập nhật theo hợp đồng của nó.

Tuy nhiên, một lần duyệt có thể quan sát trạng thái ở các thời điểm khác nhau. Không xem kết quả tổng hợp trong lúc map đang thay đổi là ảnh chụp nhất quán của toàn bộ map tại một thời điểm duy nhất. [Javadoc ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

Chương trình không tạo cập nhật cạnh tranh thực tế cho map, nên dòng `stableKeysForDisplay` chỉ in hai khóa đã thêm và sắp xếp chúng để kết quả hiển thị ổn định. Dòng đó không phải thí nghiệm chứng minh mọi cập nhật đồng thời đều được quan sát.

Ngoại lệ về nguồn concurrent cũng không làm các hàm xử lý tự hết trạng thái dùng chung. Nếu một lambda sửa một `ArrayList` kết quả bên ngoài hoặc đọc một biến đang thay đổi không có phối hợp, các vấn đề đó vẫn phải được giải quyết riêng.

Nếu nghiệp vụ cần xử lý đúng toàn bộ dữ liệu tại cùng một thời điểm, phải thiết kế cách lấy trạng thái nhất quán. Đổi collection sang concurrent chỉ để không còn `ConcurrentModificationException` chưa đủ để chứng minh yêu cầu đó.

## 8. Chỉ chọn song song sau khi phép xử lý đã đúng

Chế độ song song là lựa chọn thực thi, không phải bước sửa tính đúng đắn. Trước hết cần xác định:

- Hàm lọc và biến đổi có thể tính độc lập cho từng phần tử hay không.
- Có nguồn nào bị sửa trong thời gian pipeline chạy hay không.
- Việc kết hợp kết quả có tuân thủ hợp đồng không.
- Thứ tự nào cần giữ ở đầu ra hoặc ở các hành động.
- Có trạng thái dùng chung bên ngoài pipeline hay không.

Sau đó mới cân nhắc chi phí. Phân chia công việc, lập lịch và kết hợp kết quả đều có chi phí. Nguồn nhỏ hoặc thao tác quá nhẹ có thể không thu được lợi ích; nguồn khó chia cân bằng cũng có thể làm một số phần hoàn thành muộn hơn nhiều phần khác. Không suy ra `parallelStream()` luôn nhanh hơn `stream()`. [Oracle Java Tutorial: Parallelism](https://docs.oracle.com/javase/tutorial/collections/streams/parallelism.html)

Một nguồn `ArrayList` ổn định có thể được đọc bởi parallel stream dù bản thân collection không cung cấp mọi thao tác an toàn đồng thời. Điều kiện ở đây là không có bên sửa nguồn trong thời gian xử lý và các hàm tham gia đáp ứng hợp đồng. Không cần biến mọi nguồn chỉ đọc thành collection concurrent chỉ vì chọn chế độ song song.

Nếu kết quả đúng nhưng cần kết luận về tốc độ, đo với dữ liệu và môi trường thực tế bằng cách đo thích hợp cho JVM. Một lần in tên luồng thực thi hoặc một phép đo thời gian rất ngắn chưa đủ để chứng minh lợi ích.

Các chương trình trong bài dùng đầu vào nhỏ để kiểm chứng hành vi và kết quả. Chúng không phải bài đo hiệu năng.

### Khi nào vòng lặp là cách diễn đạt phù hợp?

Nếu công việc phải sửa nhiều cấu trúc có liên hệ với nhau theo thứ tự rõ ràng, một vòng lặp tuần tự có thể diễn đạt trách nhiệm tốt hơn một pipeline chứa nhiều tác động phụ.

Tuy vậy, đổi stream sang vòng lặp `for-each` cũng không tự cho phép thêm hoặc xóa trực tiếp collection đang được iterator của vòng lặp duyệt. Muốn xóa trong khi duyệt, chọn API và quy tắc duyệt phù hợp, chẳng hạn `removeIf` hoặc thao tác xóa của iterator trong trường hợp được hỗ trợ.

Điều cần giữ là hợp đồng của nguồn và thuật toán, không phải hình thức viết lambda hay vòng lặp.

## 9. Những hiểu nhầm dễ làm phép xử lý sai

| Hiểu nhầm | Điều cần kiểm tra hoặc sửa |
| --- | --- |
| `stream()` là bản sao dữ liệu | Xác định loại nguồn và thời điểm ràng buộc; tạo bản sao riêng nếu cần |
| Chỉ parallel stream mới cấm sửa nguồn | Quy tắc không can thiệp áp dụng cả pipeline tuần tự |
| Không gặp `ConcurrentModificationException` nghĩa là an toàn | Cơ chế phát hiện lỗi không bảo đảm bắt mọi can thiệp |
| Thay bằng `set` thì chắc chắn hợp lệ vì không đổi size | Dữ liệu nguồn vẫn bị thay trong thời gian duyệt |
| Đổi sang tên biến khác thì không còn sửa nguồn | Kiểm tra đối tượng thực và mọi đường tham chiếu dùng chung |
| `forEachOrdered` làm mọi thao tác hợp lệ | Nó xử lý thứ tự hành động, không cấp quyền sửa nguồn |
| Parallel stream làm nguồn tự an toàn đồng thời | Nguồn và các hàm vẫn phải đáp ứng hợp đồng riêng |
| Có danh sách kết quả bên ngoài là có thể gọi `add` từ mọi lambda | Dùng thao tác thu thập; tránh tích lũy không được bảo vệ |
| Bộ đếm nguyên tử làm mọi thuật toán có trạng thái đúng thứ tự | Phân biệt an toàn cập nhật với ý nghĩa lựa chọn phần tử |
| `peek` luôn chạy một lần cho mỗi phần tử | Có thể tối ưu bỏ bước hoặc kết thúc sớm |
| Collection concurrent luôn cho ảnh chụp cố định | Đọc chính sách của bộ duyệt; nhất quán yếu khác ảnh chụp |
| Tạo `new ArrayList<>(source)` bảo vệ cả mọi đối tượng phần tử | Bản sao cấu trúc vẫn dùng chung tham chiếu phần tử |
| Thêm `parallel()` ở giữa chỉ làm đoạn sau song song | Chế độ được đặt gần nhất áp dụng cho toàn pipeline khi chạy |

Một cách tự kiểm tra hữu ích là tách ba câu hỏi:

1. **Nguồn có ổn định hoặc có chính sách cập nhật phù hợp không?**
2. **Hàm xử lý có phụ thuộc trạng thái đang thay đổi hoặc gây tranh chấp không?**
3. **Thao tác kết thúc và cách kết hợp có bảo đảm đúng kết quả cần có không?**

Trả lời “có dùng collection an toàn đồng thời” mới xử lý được một phần của câu hỏi đầu, không thay thế hai câu hỏi còn lại.

## 10. Thực hành dự đoán và sửa cách xử lý

### Bài 1: thay đổi trước và sau thao tác kết thúc

Một `ArrayList` ban đầu chứa `[1, 2]`. Tạo stream có `filter(n -> n > 1)`, thêm `3` vào danh sách rồi gọi `toList()`. Sau khi có kết quả, thêm `4` vào danh sách. Kết quả đã tạo chứa những phần tử nào?

**Đối chiếu:**

Kết quả là `[2, 3]`. Lần thêm `3` xảy ra trước thao tác kết thúc và được stream từ `ArrayList` trong tình huống này quan sát. Lần thêm `4` xảy ra sau đó không tự cập nhật danh sách kết quả. Muốn xử lý cả `4`, tạo stream mới từ nguồn.

### Bài 2: thay đổi trong lúc duyệt tuần tự

Một pipeline dùng `source.stream().forEach(...)`; bên trong hành động, gọi `source.remove(...)`. Có thể kết luận an toàn vì không dùng `parallelStream()` không?

**Đối chiếu:**

Không. Pipeline đang đọc và sửa cùng nguồn thông thường trong một lần duyệt. Vấn đề vẫn có thể xảy ra trên một luồng thực thi. Nếu yêu cầu là loại phần tử theo điều kiện, dùng `removeIf` riêng hoặc tạo danh sách mới bằng `filter`, tùy việc có cần sửa nguồn hay không.

Không đặt một stdout hay một ngoại lệ cố định làm hợp đồng cho đoạn mã vi phạm.

### Bài 3: thu thập từ parallel stream

Đoạn mã có `List<Integer> result = new ArrayList<>();`, sau đó dùng parallel stream với `forEach(result::add)`. Nguồn hoàn toàn không bị sửa. Đoạn mã đã đủ điều kiện đúng chưa?

**Đối chiếu:**

Chưa. Các hành động có thể cập nhật cùng `result` đồng thời. Với mục đích tạo danh sách, dùng `toList()` nếu không cần sửa cấu trúc kết quả, hoặc `collect(Collectors.toCollection(ArrayList::new))` nếu cần một `ArrayList` cho phép sửa.

Sự ổn định của nguồn và cách tích lũy kết quả là hai điều kiện riêng.

### Bài 4: thứ tự kết quả và thứ tự hành động

Một `List` có thứ tự được lọc và biến đổi bằng parallel stream. Khi kết thúc bằng `toList()`, kết quả có thể giữ thứ tự gặp. Điều đó có chứng minh các lời gọi hàm `map` đã xảy ra tuần tự không? Thay thao tác kết thúc bằng `forEach` có bảo đảm thứ tự hành động giống danh sách không?

**Đối chiếu:**

Không cho cả hai câu. Thư viện có thể xử lý các phần độc lập rồi ghép kết quả đúng thứ tự. `forEach` trên parallel stream không cam kết tôn trọng thứ tự gặp. Nếu cần thứ tự hành động trên một stream có thứ tự, xem xét `forEachOrdered`; vẫn phải giữ các giới hạn về nguồn và trạng thái dùng chung.

### Bài 5: collection an toàn đồng thời và ảnh chụp

Tạo stream từ `CopyOnWriteArrayList` có `[A, B]`, sau đó thêm `C` trước khi gọi `toList()`. So sánh với tình huống tương tự trên `ArrayList`. Vì sao chương trình `SourceTraversalPoliciesDemo` cho hai kết quả khác nhau?

**Đối chiếu:**

Stream từ `CopyOnWriteArrayList` trong ví dụ duyệt ảnh chụp tại lúc bộ duyệt được tạo, nên cho `[A, B]`. Stream từ `ArrayList` dùng cơ chế ràng buộc muộn trong tình huống đó, nên cho `[A, B, C]`. Cùng lời gọi `stream()` không thay thế hợp đồng riêng của loại nguồn.

### Bài 6: sửa một yêu cầu có trạng thái

Yêu cầu là “lấy ba phần tử đầu tiên thỏa điều kiện theo thứ tự nguồn”. Một giải pháp dùng bộ đếm nguyên tử trong `filter` để giữ ba lần xử lý đầu trên parallel stream. Vì sao cách đó không diễn đạt đúng yêu cầu?

**Đối chiếu:**

Thứ tự các luồng thực thi đến bộ đếm không phải thứ tự gặp phần tử trong nguồn. Bộ đếm có thể cập nhật an toàn nhưng chọn sai ba phần tử theo yêu cầu. Diễn đạt bằng cách lọc điều kiện rồi `limit(3)` trên stream có thứ tự phù hợp.

### Bài 7: thiết kế một phép biến đổi không can thiệp nguồn

Cho danh sách số `[1, 2, 3, 4, 5, 6]`. Viết chương trình tạo danh sách bình phương các số lẻ, rồi kiểm chứng:

- Nguồn vẫn là `[1, 2, 3, 4, 5, 6]`.
- Kết quả là `[1, 9, 25]`.
- Bản tuần tự và bản song song có cùng kết quả có thứ tự.
- Không dùng danh sách bên ngoài để tích lũy bằng `forEach`.
- Không dựa vào tên luồng thực thi, số lần gọi một bước quan sát hoặc ngoại lệ fail-fast để quyết định đúng sai.

Có thể phát triển từ `StreamExecutionModeDemo`: đổi điều kiện thành số lẻ, giữ phép bình phương và tạo kết quả bằng `toList()`. Sau đó giải thích tại sao phép xử lý không sửa nguồn, không có trạng thái dùng chung cần cập nhật và vẫn giữ đúng thứ tự kết quả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="streams-spliterators-003">Spliterator: tryAdvance, forEachRemaining và trySplit</a>

<details>
<summary>Xem chi tiết</summary>

# Spliterator: tryAdvance, forEachRemaining và trySplit

## 1. Một bộ duyệt có thể chia phần công việc còn lại

Khi đọc một danh sách, ta có thể muốn lấy từng phần tử một, xử lý nhanh tất cả phần còn lại, hoặc giao một phần chưa xử lý cho một bộ duyệt khác. `Spliterator<T>` cung cấp ba thao tác tương ứng:

| Phương thức | Yêu cầu chính | Giá trị trả về |
| --- | --- | --- |
| `tryAdvance(action)` | Thực hiện hành động trên một phần tử còn lại, nếu có | `boolean` |
| `forEachRemaining(action)` | Thực hiện hành động trên các phần tử còn lại của bộ duyệt này | `void` |
| `trySplit()` | Thử tách một phần chưa duyệt sang một Spliterator khác | Spliterator mới hoặc `null` |

Spliterator là đối tượng dùng để **duyệt và phân chia các phần tử của nguồn**. Nguồn có thể là Collection, mảng hoặc một dạng nguồn khác; bản thân Spliterator không phải danh sách chứa bản sao của mọi dữ liệu. [Java SE 17 — Spliterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Spliterator.html).

Hình dung một chồng hồ sơ có thứ tự:

~~~text
[A, B, C, D, E, F]
~~~

Một Spliterator đang quản lý việc đọc chồng hồ sơ này. Đọc `A` làm phần chưa đọc còn `B..F`. Sau đó có thể tách một phần của `B..F` cho bộ duyệt khác. Hồ sơ `A` đã xử lý không tự xuất hiện lại trong phần tách.

Trong bài, “còn lại” luôn có nghĩa là phần mà **chính Spliterator đang xét còn chịu trách nhiệm duyệt**, không phải toàn bộ Collection nguồn. Sau khi chia, hai Spliterator có trách nhiệm trên hai phần khác nhau.

Mục tiêu học là theo dõi chính xác trạng thái ấy: phần nào đã đi qua hành động, phần nào được chuyển sang bộ duyệt mới, phần nào vẫn thuộc bộ duyệt cũ, và thời điểm nào thật sự hết phần tử.

## 2. So với Iterator: thay cách yêu cầu phần tử và bổ sung khả năng chia

Một `Iterator` thường được dùng theo cặp `hasNext` và `next`:

~~~java
while (iterator.hasNext()) {
    String value = iterator.next();
    consume(value);
}
~~~

Đây là đoạn minh họa có `iterator` và `consume` do ứng dụng cung cấp. `hasNext` hỏi còn phần tử hay không; `next` trả về phần tử và tiến trạng thái. [Java SE 17 — Iterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html).

Với Spliterator, `tryAdvance` kết hợp việc thử lấy phần tử với việc chuyển nó cho một `Consumer`:

~~~java
boolean moved = spliterator.tryAdvance(value -> consume(value));
~~~

Nếu có phần tử, hành động được gọi với phần tử ấy và phương thức trả `true`. Nếu không có, không có phần tử nào được chuyển cho hành động và phương thức trả `false`.

Spliterator còn có `trySplit` để phân chia phần chưa duyệt. Tuy nhiên, khả năng chia không có nghĩa phải dùng nhiều luồng. Cả ba phương thức đều có thể được dùng trong một chương trình tuần tự để học hoặc để xây dựng thuật toán riêng.

Spliterator cũng không có phương thức `remove` như thao tác tùy chọn của Iterator. Không nên tìm cách dùng `tryAdvance` để thay thế một cơ chế xóa hợp lệ của cấu trúc nguồn.

## 3. tryAdvance: một lời gọi thành công xử lý một phần tử

Chữ ký:

~~~java
boolean tryAdvance(Consumer<? super T> action)
~~~

Với nguồn có thứ tự `A, B, C`, các lời gọi bình thường lần lượt có ý nghĩa:

| Lời gọi | Phần tử chuyển cho action | Kết quả |
| --- | --- | --- |
| Lần 1 | `A` | `true` |
| Lần 2 | `B` | `true` |
| Lần 3 | `C` | `true` |
| Lần 4 | Không có | `false` |

Lần xử lý phần tử cuối vẫn trả `true`. Nó không trả lời câu hỏi “sau lần này còn phần tử tiếp theo không”; nó trả lời câu hỏi “lần gọi này có phần tử để thực hiện hành động không”.

Ví dụ minh họa trong một phương thức:

~~~java
List<String> data = new ArrayList<>(List.of("A", "B", "C"));
Spliterator<String> cursor = data.spliterator();
List<String> visited = new ArrayList<>();

boolean first = cursor.tryAdvance(visited::add);
~~~

Sau lời gọi, `first` bằng `true` và `visited` là `[A]`. Nguồn `data` vẫn là `[A, B, C]`. Trạng thái duyệt đã tiến, chứ phần tử `A` không bị xóa khỏi danh sách.

Các đoạn ngắn trong bài cần import thích hợp; phần chương trình đầy đủ phía sau có toàn bộ import và lệnh chạy.

### 3.1. Boolean không phải kết quả kiểm tra nghiệp vụ

`action` có kiểu `Consumer` và không trả về một quyết định lọc. Nếu hành động tự kiểm tra điều kiện rồi không làm gì thêm, `tryAdvance` vẫn có thể trả `true` vì nó đã lấy một phần tử.

Ví dụ minh họa:

~~~java
boolean moved = cursor.tryAdvance(value -> {
    if (value.startsWith("Z")) {
        System.out.println(value);
    }
});
~~~

Không có dòng nào được in không chứng minh `moved` là `false`. Phần tử đã được lấy có thể chỉ không đạt điều kiện `startsWith`.

Cũng vậy, `visited::add` có thể được thích nghi làm Consumer dù `List.add` vốn có kết quả boolean. Giá trị ấy không trở thành giá trị trả về của `tryAdvance`.

### 3.2. Lấy từng phần tử khi cần kiểm soát nhịp xử lý

`tryAdvance` hữu ích khi bên gọi cần dừng giữa chừng, xử lý một lượng có giới hạn hoặc phối hợp với một điều kiện bên ngoài. Không cần rút hết nguồn trong một lời gọi.

Nếu mục đích duy nhất là xử lý hết phần còn lại, dùng `forEachRemaining` thường diễn tả rõ hơn một vòng lặp gọi `tryAdvance` có thân rỗng.

Sau khi một Spliterator đã hết phần việc, `tryAdvance` với hành động hợp lệ trả `false` và không gọi hành động. Điều này chỉ nói về bộ duyệt đó; một phần đã tách sang Spliterator khác có thể vẫn chưa được đọc.

## 4. forEachRemaining: xử lý phần chưa duyệt, không bắt đầu lại nguồn

Chữ ký:

~~~java
void forEachRemaining(Consumer<? super T> action)
~~~

Nếu đã dùng `tryAdvance` để xử lý `A` và `B` trong nguồn `A..F`, lời gọi `forEachRemaining` tiếp theo chỉ xử lý `C, D, E, F`.

Ví dụ:

~~~java
Spliterator<String> cursor = data.spliterator();
List<String> visited = new ArrayList<>();

cursor.tryAdvance(visited::add);
cursor.tryAdvance(visited::add);
cursor.forEachRemaining(visited::add);
~~~

Với `data` là `[A, B, C, D, E, F]`, `visited` sau cùng cũng là `[A, B, C, D, E, F]`, không phải `[A, B, A, B, C, D, E, F]`.

Phương thức trả `void`, nên không có kết quả boolean báo đã xử lý được bao nhiêu phần tử. Nếu cần kết quả tính toán, hành động hoặc thuật toán bao quanh phải thu kết quả một cách phù hợp.

Theo hợp đồng, `forEachRemaining` thực hiện hành động tuần tự trong luồng hiện tại cho tới khi hết phần tử hoặc hành động ném ngoại lệ. Nếu có đặc tính `ORDERED`, các hành động theo thứ tự gặp của phần nguồn ấy. Cách cài đặt mặc định gọi lặp `tryAdvance`, nhưng một cách cài đặt cụ thể có thể tối ưu vòng duyệt. [Java SE 17 — Spliterator.forEachRemaining](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Spliterator.html#forEachRemaining(java.util.function.Consumer)).

Hai hệ quả thực hành:

- Gọi lại `forEachRemaining` sau khi đã xử lý hết không làm dữ liệu được duyệt lại.
- `forEachRemaining` trên một phần được chia không tự xử lý phần đang thuộc bộ duyệt khác.

Tên phương thức có chữ “all remaining” theo nghĩa mọi phần tử còn lại của **đối tượng nhận lời gọi**, không phải mọi phần tử từng có trong nguồn.

## 5. trySplit: chuyển một phần trách nhiệm duyệt sang bộ duyệt mới

Chữ ký:

~~~java
Spliterator<T> trySplit()
~~~

Nếu chia được, phương thức trả về Spliterator quản lý một phần các phần tử chưa duyệt. Sau lời gọi, phần ấy không còn thuộc phạm vi của Spliterator cũ. Nếu chưa thể hoặc không muốn chia tiếp, nó trả `null`.

Điều cần hình dung là **phân chia**, không phải nhân đôi:

~~~text
trước:
cursor -> [A, B, C, D, E, F]

sau một lần chia trong ví dụ:
prefix -> [A, B, C]
cursor -> [D, E, F]
~~~

Hai phần phải không trùng nhau ở cấp phần tử được nguồn phân chia. Đây không phải yêu cầu rằng các **giá trị** phải khác nhau: nguồn `[X, X, Y]` có hai vị trí chứa `X` và cả hai đều cần được xử lý đúng một lần.

`trySplit` không nhận một Consumer nên không tự thực hiện hành động nghiệp vụ mà ta sẽ truyền cho `tryAdvance` hoặc `forEachRemaining`. Tuy nhiên, không được suy ra nó luôn là thao tác miễn phí, không cấp phát hoặc không đọc gì từ cấu trúc nền. Một cách cài đặt có thể cần chuẩn bị hoặc gom một lô phần tử để tạo phần chia.

### 5.1. Với nguồn ORDERED, phần trả về là tiền tố

Nếu Spliterator báo `ORDERED`, phần trả về phải là một tiền tố thực sự của phần chưa duyệt. Spliterator cũ giữ phần sau. Đây là ràng buộc cho phép thuật toán ghép kết quả đúng thứ tự. [Java SE 17 — Spliterator.trySplit](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Spliterator.html#trySplit()).

Với nguồn có thứ tự `A..F` và một phép chia ba–ba, muốn tái tạo thứ tự nguồn thì duyệt `prefix` trước, rồi `cursor`. Nếu duyệt phần sau trước, mỗi bộ duyệt vẫn có thể tuân thủ thứ tự bên trong phần của nó, nhưng danh sách ghép tổng thể sẽ thành `[D, E, F, A, B, C]`.

Nếu nguồn không có `ORDERED`, không áp đặt lên nó ý nghĩa tiền tố theo một thứ tự nghiệp vụ chưa được bảo đảm. Khả năng chia thành các phần không tự tạo ra thứ tự sắp xếp.

### 5.2. Tỷ lệ chia không bắt buộc luôn là hai nửa

Chia cân bằng thường có lợi cho xử lý song song, nhưng hợp đồng không yêu cầu mọi nguồn chia đúng `50/50`. Cấu trúc cây, nguồn dựa trên bộ duyệt hoặc nguồn khó biết kích thước có thể có chiến lược khác.

Kết quả chính xác `[B, C]` và `[D, E, F]` trong chương trình của bài là quan sát của nguồn `ArrayList` cụ thể sau khi đã đọc `A`. Không lấy những con số đó làm đáp án cố định cho mọi loại Collection.

## 6. Kết hợp cả ba phương thức trên cùng một ví dụ

Nguồn:

~~~text
[A, B, C, D, E, F]
~~~

Đầu tiên:

~~~java
Spliterator<String> cursor = data.spliterator();
List<String> first = new ArrayList<>();
cursor.tryAdvance(first::add);
~~~

Trạng thái lúc này:

~~~text
đã xử lý:   [A]
cursor:     [B, C, D, E, F]
~~~

Tiếp theo:

~~~java
Spliterator<String> prefix = cursor.trySplit();
~~~

Trong ca `ArrayList` được chạy:

~~~text
đã xử lý:   [A]
prefix:     [B, C]
cursor:     [D, E, F]
~~~

Cuối cùng:

~~~java
prefix.forEachRemaining(prefixValues::add);
cursor.forEachRemaining(remainingValues::add);
~~~

Hai danh sách đầu ra là `[B, C]` và `[D, E, F]`. Ghép phần đã xử lý, phần tiền tố và phần còn lại cho `A..F` đúng một lần.

Ba điểm cần kiểm tra:

1. `A` không bị đưa vào phép chia vì nó đã đi qua hành động.
2. Duyệt `cursor` không tự kéo theo `prefix`.
3. Danh sách nguồn không bị xóa hoặc cắt thành hai danh sách mới chỉ vì quyền duyệt đã được chia.

Trong mã tổng quát, phải kiểm tra `prefix != null` trước khi dùng. Chương trình đầy đủ dùng nguồn biết được để minh họa một lần chia thành công và có kiểm tra bảo vệ giả định của ví dụ.

## 7. trySplit trả null không có nghĩa là hết dữ liệu

Một bộ duyệt trên mảng một phần tử có thể không chia thêm được:

~~~java
Spliterator<String> one = Arrays.spliterator(new String[] {"solo"});
Spliterator<String> part = one.trySplit();
~~~

Trong ví dụ này, `part` là `null`. Nhưng `one.tryAdvance(...)` vẫn xử lý được `solo` và trả `true`.

Cần tách hai câu hỏi:

| Câu hỏi | Cách kiểm tra phù hợp |
| --- | --- |
| Có tách thêm được một phần để giao đi không? | Gọi `trySplit` và xem kết quả có null không |
| Lần gọi này có phần tử để xử lý không? | Gọi `tryAdvance` với hành động hợp lệ |
| Muốn xử lý hết phần việc hiện tại | Gọi `forEachRemaining` |

Không nên viết thuật toán chỉ xử lý khi `trySplit` khác `null` rồi bỏ luôn nhánh `null`. Nhánh đó thường chính là phần việc cần được duyệt trực tiếp.

`trySplit` có thể từ chối do nguồn rỗng, ràng buộc cấu trúc, hiệu quả thực hiện hoặc việc đã bắt đầu duyệt khiến cách cài đặt không chia tiếp được. Vì vậy, cả nguồn còn nhiều phần tử cũng không bảo đảm mọi Spliterator luôn chia được.

Với nguồn hữu hạn, việc cố chia lặp đi lặp lại phải đi tới trường hợp không chia tiếp được theo hợp đồng. Tuy nhiên, một thuật toán thực tế vẫn nên có ngưỡng dừng phù hợp, thay vì chia tới đơn vị nhỏ nhất chỉ vì có thể.

## 8. Duyệt đệ quy theo thứ tự: chia được thì chia, không chia được thì xử lý

Đoạn helper sau dùng cho nguồn có thứ tự phù hợp:

~~~java
static <T> void drainInOrder(Spliterator<T> source, List<T> output) {
    Spliterator<T> prefix = source.trySplit();
    if (prefix == null) {
        source.forEachRemaining(output::add);
    } else {
        drainInOrder(prefix, output);
        drainInOrder(source, output);
    }
}
~~~

Đây là phương thức đặt trong một lớp, cần import `Spliterator` và `List`. Chương trình đầy đủ dùng helper với các `ArrayList` nhỏ.

Luồng suy luận:

1. Thử tách phần đầu.
2. Nếu không có phần tách, xử lý trực tiếp phần việc hiện tại.
3. Nếu có, xử lý phần tiền tố trước.
4. Sau đó xử lý phần còn lại của đối tượng cũ.

Việc gọi helper như trên vẫn hoàn toàn tuần tự. Không có dòng mã nào khởi tạo luồng hoặc gửi tác vụ sang bộ thực thi.

Helper phục vụ minh họa nguyên tắc bảo toàn thứ tự. Một hệ thống xử lý dữ liệu lớn cần cân nhắc độ sâu đệ quy, số phần chia, chi phí cấp phát và ngưỡng kích thước. Không nên coi ví dụ chia tới khi `null` là chính sách hiệu năng tối ưu cho mọi nguồn.

Với nguồn không có thứ tự được xác định, tên `drainInOrder` chỉ có thể mang nghĩa thứ tự do thuật toán chọn, không thể cam kết khôi phục một thứ tự ban đầu mà nguồn không hứa cung cấp.

## 9. estimateSize là thông tin hỗ trợ, không thay thế phép thử còn phần tử

`estimateSize()` báo một ước lượng về số phần tử mà bộ duyệt còn có thể đưa ra. Khi không biết, quá đắt để tính hoặc nguồn không hữu hạn, giá trị có thể là `Long.MAX_VALUE`.

`SIZED` và `SUBSIZED` đặt thêm những ràng buộc về kích thước, nhưng có điều kiện liên quan tới việc đã duyệt hoặc chia hay chưa. Không được giả định mọi Spliterator luôn trả số đếm chính xác sau mọi thao tác.

`ArrayList.spliterator()` báo `ORDERED`, `SIZED` và `SUBSIZED`, đồng thời có chính sách liên kết muộn và phát hiện sửa đổi theo khả năng tốt nhất. [Java SE 17 — ArrayList.spliterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html#spliterator()).

Trong ca thử của bài, sau khi xử lý `A` rồi chia:

~~~text
prefix.estimateSize() = 2
cursor.estimateSize() = 3
~~~

Hai giá trị phù hợp với `[B, C]` và `[D, E, F]` của cách cài đặt được chạy. Đó là thông tin chẩn đoán hữu ích, không phải lý do để thay tất cả vòng duyệt bằng một vòng lặp theo ước lượng.

Tránh mẫu suy luận “estimate lớn hơn không thì chắc chắn còn, estimate bằng không thì chắc chắn hết” khi viết mã nhận một Spliterator bất kỳ mà chưa kiểm tra hợp đồng đặc tính và trạng thái. `tryAdvance` mới trực tiếp thử thực hiện một bước; `forEachRemaining` trực tiếp yêu cầu rút phần còn lại.

Bài chỉ dùng các đặc tính cần thiết để hiểu ba thao tác. Các bit đặc tính là cam kết để thuật toán tận dụng đúng, không phải nhãn tùy ý gắn cho đẹp.

## 10. Chia công việc không tự tạo an toàn đa luồng

Một luồng có thể gọi `trySplit` rồi chuyển Spliterator trả về cho luồng khác. Cách này tạo cơ sở để các phần nguồn được xử lý riêng.

Nhưng không được để hai luồng cùng gọi `tryAdvance` hoặc `trySplit` trên **cùng một đối tượng Spliterator** rồi mặc nhiên coi là an toàn. Hợp đồng Spliterator không yêu cầu đối tượng phải an toàn cho kiểu truy cập đồng thời ấy.

Mô hình trách nhiệm phù hợp là:

~~~text
bộ duyệt ban đầu
      |
      | chia trước khi giao
      +--> phần 1 --> một bên xử lý
      |
      +--> phần 2 --> một bên xử lý khác
~~~

Ngoài quyền sở hữu bộ duyệt, hành động xử lý cũng cần phù hợp. Hai phần nguồn khác nhau cùng gọi `sharedArrayList.add` từ hai luồng vẫn có thể gây lỗi vì nơi thu kết quả bị dùng chung không an toàn.

Nếu cần giữ thứ tự toàn cục, còn phải thiết kế cách ghép kết quả theo thứ tự phần chia. Việc từng phần có `ORDERED` không làm các thời điểm hoàn tất ở nhiều luồng tự sắp hàng đúng thứ tự.

Chương trình của bài cố ý duyệt tất cả trong một luồng, thu vào các danh sách riêng hoặc theo thứ tự đệ quy. Các kết quả được kiểm tra không phải bằng chứng cho một thuật toán đa luồng.

## 11. Hành động null, ngoại lệ và việc sửa nguồn

### 11.1. action null là tham số không hợp lệ

Cả `tryAdvance` lẫn `forEachRemaining` yêu cầu một hành động hợp lệ. Truyền `null` gây `NullPointerException`; không dùng nó như phép hỏi “còn dữ liệu không” mà không muốn xử lý.

Có thể dùng một hành động không làm gì nếu thật sự muốn tiêu thụ một bước mà bỏ giá trị:

~~~java
boolean skipped = cursor.tryAdvance(value -> { });
~~~

Nếu trả `true`, phần tử ấy đã đi qua trạng thái duyệt và không còn nằm trong phần chưa duyệt. Đây là hành động tiêu thụ, không phải thao tác nhìn trước không thay đổi trạng thái.

### 11.2. Ngoại lệ của action được truyền ra ngoài

Nếu hành động ném lỗi ở phần tử `B`, lời gọi đang duyệt dừng và ngoại lệ được truyền cho bên gọi. Tác dụng phụ đã thực hiện trên `A` hoặc trên `B` trước khi ném không tự được hoàn tác.

Hợp đồng còn nêu rằng hành vi tiếp theo của Spliterator không được xác định khi action ném ngoại lệ. Vì thế, không được viết một thuật toán tổng quát dựa vào giả định “bắt lỗi rồi gọi tiếp, chắc chắn sẽ bắt đầu ở phần tử ngay sau B”.

Trong chương trình minh chứng, bộ duyệt đã gặp lỗi được bỏ, không tiếp tục truy vấn hoặc dùng lại nó. Danh sách `seen-before-failure` chỉ cho biết tác dụng phụ quan sát được trước lỗi trong ví dụ ấy.

### 11.3. Nguồn bị sửa trong lúc duyệt không được xử lý bằng phỏng đoán

Với nguồn sửa được, phải biết chính sách liên kết nguồn và sửa đổi đồng thời của Spliterator. `ArrayList` có Spliterator liên kết muộn và fail-fast, nhưng phát hiện sửa đổi chỉ theo khả năng tốt nhất.

Các thay đổi sau khi bộ duyệt đã liên kết với nguồn có thể làm việc duyệt không còn hợp lệ. Việc ghi log bằng `estimateSize` hoặc gọi `trySplit` cũng có thể liên quan đến thời điểm liên kết; không chỉ `tryAdvance` mới đáng chú ý.

Đừng dựa vào `ConcurrentModificationException` để chứng minh không có truy cập sai. `forEachRemaining` có thể tối ưu kiểm tra sửa đổi sau một lô thay vì sau từng phần tử. Giữ nguồn ổn định trong quá trình minh họa và chọn chính sách phối hợp phù hợp nếu ứng dụng cần cập nhật đồng thời.

## 12. Biến thể nguyên thủy và cầu nối sang Stream

### 12.1. Spliterator.OfInt dùng hành động nhận int

Với mảng số nguyên:

~~~java
Spliterator.OfInt numbers = Arrays.spliterator(new int[] {2, 4, 6});
int[] total = {0};

numbers.tryAdvance((int value) -> total[0] += value);
numbers.forEachRemaining((int value) -> total[0] += value);
~~~

Lời gọi đầu tính thêm `2`, lời gọi sau tính thêm `4` và `6`, tổng cuối bằng `12`. Lambda ghi rõ kiểu `int` để chọn dạng hành động nguyên thủy.

Các biến thể `OfInt`, `OfLong` và `OfDouble` có overload dành cho kiểu nguyên thủy. Dùng hành động nguyên thủy phù hợp tránh việc cơ chế duyệt phải đóng gói từng giá trị thành đối tượng chỉ để chuyển cho một Consumer tổng quát.

`total` ở đây là một ô lưu tổng dùng trong ví dụ tuần tự. Không mang nguyên cách cập nhật mảng dùng chung này sang nhiều luồng rồi coi là phép cộng nguyên tử.

### 12.2. Stream có thể tiếp nhận phần việc chưa duyệt

`StreamSupport.stream(spliterator, false)` tạo một Stream tuần tự từ Spliterator đã cho. Nó không yêu cầu quay lại đầu nguồn. [Java SE 17 — StreamSupport.stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/StreamSupport.html#stream(java.util.Spliterator,boolean)).

Nếu đã dùng `tryAdvance` bỏ qua `A`, Stream nhận bộ duyệt ấy chỉ còn nhìn thấy `B, C, D, E, F`. Tạo một lớp Stream bao ngoài không làm trạng thái duyệt được khởi động lại.

Từ lúc giao bộ duyệt cho pipeline, nên để pipeline quản lý lượt duyệt đó. Không xen các lời gọi thủ công vào cùng Spliterator khi Stream đang sử dụng nó. Nếu cần một lượt mới từ đầu, tạo bộ duyệt mới từ nguồn theo hợp đồng của nguồn.

## 13. Chương trình minh chứng đầy đủ

Lưu mã sau thành **SpliteratorOperationsDemo.java**. Chương trình dùng Java 17+, thư viện chuẩn và không tạo luồng phụ.

`rejects` xác nhận loại ngoại lệ dự kiến. Ca lỗi action chỉ quan sát danh sách đã ghi dấu rồi bỏ bộ duyệt lỗi. Helper đệ quy chỉ dùng cho những nguồn nhỏ, có thứ tự trong ví dụ.

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

public class SpliteratorOperationsDemo {
    private static void rejects(
            String label,
            Class<? extends RuntimeException> expected,
            Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " did not fail");
        } catch (RuntimeException ex) {
            if (!expected.isInstance(ex)) {
                throw ex;
            }
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }

    private static <T> void drainInOrder(Spliterator<T> source, List<T> output) {
        Spliterator<T> prefix = source.trySplit();
        if (prefix == null) {
            source.forEachRemaining(output::add);
        } else {
            drainInOrder(prefix, output);
            drainInOrder(source, output);
        }
    }

    public static void main(String[] args) {
        List<String> data = new ArrayList<>(List.of("A", "B", "C", "D", "E", "F"));

        Spliterator<String> cursor = data.spliterator();
        List<String> first = new ArrayList<>();
        System.out.println("advance=" + cursor.tryAdvance(first::add));
        System.out.println("first=" + first);
        Spliterator<String> prefix = cursor.trySplit();
        if (prefix == null) {
            throw new AssertionError("Expected ArrayList split");
        }
        System.out.println("split-size=" + prefix.estimateSize());
        System.out.println("remaining-size=" + cursor.estimateSize());
        List<String> prefixValues = new ArrayList<>();
        List<String> remainingValues = new ArrayList<>();
        prefix.forEachRemaining(prefixValues::add);
        cursor.forEachRemaining(remainingValues::add);
        System.out.println("prefix=" + prefixValues);
        System.out.println("remaining=" + remainingValues);
        System.out.println("source=" + data);
        System.out.println("advance-after-end=" + cursor.tryAdvance(first::add));
        System.out.println("first-still=" + first);

        Spliterator<String> mixed = data.spliterator();
        List<String> visited = new ArrayList<>();
        mixed.tryAdvance(visited::add);
        mixed.tryAdvance(visited::add);
        System.out.println("before-bulk=" + visited);
        mixed.forEachRemaining(visited::add);
        System.out.println("after-bulk=" + visited);
        mixed.forEachRemaining(value -> visited.add("unexpected"));
        System.out.println("after-empty-bulk=" + visited);

        Spliterator<String> singleton = Arrays.spliterator(new String[] {"solo"});
        System.out.println("singleton-split-null=" + (singleton.trySplit() == null));
        List<String> soloValues = new ArrayList<>();
        System.out.println("singleton-advance=" + singleton.tryAdvance(soloValues::add));
        System.out.println("singleton-values=" + soloValues);

        List<String> rebuilt = new ArrayList<>();
        drainInOrder(data.spliterator(), rebuilt);
        System.out.println("recursive=" + rebuilt);
        List<String> duplicateValues = new ArrayList<>();
        drainInOrder(new ArrayList<>(List.of("X", "X", "Y")).spliterator(), duplicateValues);
        System.out.println("duplicates=" + duplicateValues);

        Spliterator<String> empty = List.<String>of().spliterator();
        System.out.println("empty-split-null=" + (empty.trySplit() == null));
        System.out.println("empty-advance=" + empty.tryAdvance(value -> {
            throw new AssertionError("No value expected");
        }));

        rejects("null-advance-action", NullPointerException.class,
                () -> data.spliterator().tryAdvance((Consumer<String>) null));
        rejects("null-bulk-action", NullPointerException.class,
                () -> data.spliterator().forEachRemaining((Consumer<String>) null));

        Spliterator<String> failing = data.spliterator();
        List<String> beforeFailure = new ArrayList<>();
        rejects("action-failure", IllegalArgumentException.class,
                () -> failing.forEachRemaining(value -> {
                    beforeFailure.add(value);
                    if (value.equals("B")) {
                        throw new IllegalArgumentException("stop");
                    }
                }));
        System.out.println("seen-before-failure=" + beforeFailure);

        Spliterator.OfInt primitive = Arrays.spliterator(new int[] {2, 4, 6});
        int[] total = {0};
        primitive.tryAdvance((int value) -> total[0] += value);
        System.out.println("primitive-first-total=" + total[0]);
        primitive.forEachRemaining((int value) -> total[0] += value);
        System.out.println("primitive-full-total=" + total[0]);

        Spliterator<String> forStream = data.spliterator();
        forStream.tryAdvance(value -> { });
        List<String> streamRemainder = StreamSupport.stream(forStream, false).toList();
        System.out.println("stream-remainder=" + streamRemainder);
    }
}
~~~

Biên dịch và chạy tại thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 SpliteratorOperationsDemo.java
java SpliteratorOperationsDemo
~~~

Stdout chính xác:

~~~text
advance=true
first=[A]
split-size=2
remaining-size=3
prefix=[B, C]
remaining=[D, E, F]
source=[A, B, C, D, E, F]
advance-after-end=false
first-still=[A]
before-bulk=[A, B]
after-bulk=[A, B, C, D, E, F]
after-empty-bulk=[A, B, C, D, E, F]
singleton-split-null=true
singleton-advance=true
singleton-values=[solo]
recursive=[A, B, C, D, E, F]
duplicates=[X, X, Y]
empty-split-null=true
empty-advance=false
null-advance-action -> NullPointerException
null-bulk-action -> NullPointerException
action-failure -> IllegalArgumentException
seen-before-failure=[A, B]
primitive-first-total=2
primitive-full-total=12
stream-remainder=[B, C, D, E, F]
~~~

Mã đã biên dịch với `--release 17` và chạy trên JDK 24.0.2. Cờ biên dịch kiểm tra mức ngôn ngữ/API Java 17; kết quả chạy được xác nhận trên JVM đã nêu, không phải một lần chạy JVM 17.

Đọc kết quả:

- `advance` và `first` cho biết một bước đã xử lý `A`.
- `split-size`, `prefix` và `remaining` thể hiện lần chia phần `B..F` của ArrayList được dùng.
- `source` không đổi vì duyệt không xóa hay cắt Collection nguồn.
- `advance-after-end` trả false mà `first-still` không có phần tử mới.
- Nhóm `before-bulk` và `after-bulk` cho thấy forEachRemaining tiếp tục trạng thái đã có.
- `singleton-split-null` cùng `singleton-advance` chứng minh không chia được chưa có nghĩa hết phần tử.
- `recursive` giữ thứ tự, còn `duplicates` giữ cả hai vị trí có giá trị `X`.
- Nhóm `empty` không gọi action trên một phần tử không tồn tại.
- Các ca `null` kiểm tra tham số action; `action-failure` kiểm tra ngoại lệ từ chính hành động.
- Nhóm `primitive` minh họa overload nhận int.
- `stream-remainder` xác nhận Stream chỉ nhận phần chưa duyệt của Spliterator.

## 14. Thực hành có đáp án

### Bài 1 — Ý nghĩa true ở phần tử cuối

Một Spliterator còn đúng một phần tử. Lời gọi `tryAdvance(action)` thành công trả gì? Lời gọi tiếp theo với action hợp lệ trả gì?

**Đáp án:** Lần xử lý phần tử cuối trả `true` vì có một phần tử được chuyển cho action. Lần sau trả `false` vì khi bắt đầu lời gọi đã không còn phần tử. Boolean không phải lời hứa rằng sau lần xử lý còn một phần tử tiếp nữa.

### Bài 2 — forEachRemaining bắt đầu ở đâu?

Nguồn `A, B, C, D` đã được `tryAdvance` xử lý hai lần. Gọi `forEachRemaining` sẽ xử lý phần nào? Có xóa dữ liệu nguồn không?

**Đáp án:** Xử lý `C` và `D`, theo thứ tự nếu nguồn có ORDERED. `A` và `B` không xuất hiện lại. Bản thân việc tiến trạng thái duyệt không xóa các phần tử khỏi Collection nguồn.

### Bài 3 — null từ trySplit

Một bộ duyệt trả `null` khi gọi `trySplit`. Có được kết luận tất cả phần tử đã được xử lý không?

**Đáp án:** Không. `null` chỉ cho biết lời gọi không tách được phần mới. Có thể nguồn còn một phần tử, không hỗ trợ chia hoặc từ chối vì lý do hiệu quả. Tiếp tục xử lý phần hiện tại bằng thao tác duyệt phù hợp.

### Bài 4 — Tránh mất phần việc sau chia

Với nguồn ORDERED còn `A, B, C, D`, phép chia trả một prefix `A, B` và bộ duyệt cũ giữ `C, D`. Nếu chỉ duyệt bộ cũ thì thiếu gì? Muốn ghép đúng thứ tự phải làm sao?

**Đáp án:** Thiếu phần `A, B` đang thuộc prefix. Duyệt và thu kết quả cả hai phần; khi cần thứ tự nguồn, ghép kết quả prefix trước phần cũ. Bộ cũ không còn tự bao gồm phần đã giao đi.

### Bài 5 — Hai giá trị bằng nhau có phải trùng công việc không?

Nguồn là `[X, X, Y]`. Sau chia rồi duyệt, kết quả vẫn chứa hai `X`. Có phải trySplit đã nhân đôi dữ liệu không?

**Đáp án:** Không. Hai vị trí khác nhau có thể chứa giá trị bằng nhau. Phân chia phải bảo toàn các phần tử nguồn theo phạm vi, không tự loại các giá trị trùng. Cần đối chiếu số lần xuất hiện ban đầu thay vì suy ra từ `equals` của các giá trị.

### Bài 6 — Action ném lỗi rồi gọi tiếp

`forEachRemaining` đã chạy action trên `A`, đến `B` thì action ném lỗi. Có thể viết mã tổng quát bắt lỗi rồi tiếp tục bộ duyệt với giả định sẽ bắt đầu ở `C` không?

**Đáp án:** Không. Hành vi tiếp theo của Spliterator sau ngoại lệ từ action không được xác định theo hợp đồng chung. Các tác dụng phụ trước lỗi cũng không tự quay lui. Thiết kế một chiến lược xử lý lỗi riêng hoặc tạo lại lượt xử lý phù hợp thay vì giả định vị trí tiếp tục.

### Bài 7 — Chia rồi dùng nhiều luồng

Sau `trySplit`, có thể cho hai luồng cùng thao tác trên một trong hai đối tượng Spliterator để “tăng tốc thêm” không? Dùng chung một ArrayList đầu ra có tự an toàn không?

**Đáp án:** Không có bảo đảm an toàn khi nhiều luồng cùng thao tác trên cùng Spliterator. Cần giao quyền xử lý từng bộ duyệt cho một bên tại một thời điểm. Nơi thu kết quả cũng phải có thiết kế phù hợp; phân chia nguồn không làm ArrayList dùng chung trở nên an toàn.

### Bài 8 — Bọc Stream có làm quay lại đầu không?

Một Spliterator của `[A, B, C]` đã xử lý `A`. Sau đó tạo `StreamSupport.stream(s, false)` và thu danh sách. Stream có lấy lại `A` không?

**Đáp án:** Không, nó nhận trạng thái phần chưa duyệt của `s` và chỉ có `B, C` trong ví dụ này. Muốn đọc lại từ đầu phải có bộ duyệt mới từ nguồn còn phù hợp, không phải chỉ tạo lớp Stream bao ngoài bộ duyệt đã tiến.

## 15. Cách theo dõi đúng ba thao tác

Trước mỗi lời gọi, xác định phần việc đang thuộc Spliterator nào. `tryAdvance` chuyển một phần tử qua hành động; `forEachRemaining` rút phần việc còn lại của bộ duyệt đó; `trySplit` chuyển một phần trách nhiệm chưa duyệt sang bộ duyệt khác hoặc báo chưa chia được.

Khi cần thứ tự, xem nguồn có ORDERED và giữ quy tắc tiền tố trước phần sau. Khi cần nhiều luồng, tách quyền sở hữu bộ duyệt và thiết kế cả nơi nhận kết quả. Khi gặp lỗi action hoặc nguồn bị sửa, quay lại hợp đồng nguồn thay vì đoán vị trí con trỏ.

Giữ mô hình phần đã xử lý, phần được giao đi và phần còn lại sẽ giúp dùng Spliterator đúng mà không nhầm phép chia với sao chép dữ liệu, không bỏ nhánh trySplit trả null và không vô tình đọc lại hoặc bỏ sót công việc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="streams-spliterators-004">estimateSize, getExactSizeIfKnown và các đặc tính nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# estimateSize, getExactSizeIfKnown và các đặc tính nguồn

## 1. Bộ duyệt cần biết nhiều hơn “phần tử tiếp theo là gì?”

Một `Spliterator` vừa hỗ trợ duyệt phần tử, vừa hỗ trợ chia phần dữ liệu cho những công việc nhỏ hơn. Để quyết định chia thế nào, chuẩn bị vùng chứa ra sao hoặc có thể bỏ qua một bước xử lý nào, thư viện cần thông tin về nguồn.

Có hai nhóm thông tin chính:

- **Kích thước:** còn khoảng bao nhiêu phần tử; có bảo đảm biết chính xác hay không.
- **Đặc tính:** có thứ tự gặp, có phần tử trùng, có sắp xếp, có thể chứa `null`, và nguồn có chính sách thay đổi như thế nào.

Những thông tin mô tả này thường được gọi là siêu dữ liệu (metadata). Chúng là hợp đồng do bộ duyệt báo cáo. Chúng không phải kết quả tự động quét toàn bộ nguồn để kiểm chứng mọi thuộc tính.

Ví dụ, một `ArrayList` có bốn phần tử có thể cung cấp số lượng trước khi duyệt. Một iterator đến từ một quá trình tạo dữ liệu có thể chưa biết sẽ trả bao nhiêu phần tử. Cả hai đều có thể được biểu diễn bằng `Spliterator`, nhưng thư viện không được áp dụng cùng giả định về kích thước.

Cần phân biệt ba câu hỏi:

| Câu hỏi | API thường liên quan |
| --- | --- |
| Có thể ước lượng số phần tử sẽ gặp không? | `estimateSize()` |
| Bộ duyệt có báo một kích thước chính xác theo hợp đồng không? | `getExactSizeIfKnown()`, `SIZED` |
| Những bảo đảm nào đi kèm việc duyệt hoặc chia nguồn? | `characteristics()`, `hasCharacteristics(...)` |

“Bốn phần tử trong collection gốc”, “bốn phần tử còn thuộc phần việc của bộ duyệt này” và “bốn phần tử sẽ còn lại sau một bộ lọc” cũng là những điều khác nhau. Bài này đi từ kích thước của một bộ duyệt đến các đặc tính nguồn và cách chúng thay đổi khi xử lý.

Các chương trình độc lập dùng Java 17. Lưu đúng tên tệp theo `public class`; các lệnh biên dịch và stdout được ghi ngay sau từng chương trình.

## 2. estimateSize là ước lượng, không phải lệnh đếm

`estimateSize()` trả về một giá trị `long` ước lượng số phần tử mà việc duyệt phần còn lại sẽ gặp. Khi nguồn vô hạn, chưa biết kích thước hoặc tính số lượng quá tốn kém, phương thức có thể trả `Long.MAX_VALUE`.

Vì vậy:

- Một số hữu hạn trả về chưa tự chứng minh số đó chính xác.
- `Long.MAX_VALUE` không chứng minh nguồn chắc chắn vô hạn.
- Giá trị trả về không phải dung lượng mảng đã cấp phát cho collection.
- Nó mô tả phần việc của bộ duyệt đang hỏi, không phải lúc nào cũng là tổng kích thước nguồn ban đầu.

`estimateSize()` không phải một thao tác kết thúc stream đếm bằng cách duyệt qua mọi phần tử. Nó được thiết kế để cung cấp thông tin có thể dùng cho việc tổ chức xử lý, kể cả khi thông tin chỉ gần đúng. [Javadoc Spliterator.estimateSize](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Spliterator.html#estimateSize())

Một ước lượng vẫn có giá trị sử dụng. Chẳng hạn, biết một phần việc có khả năng lớn hơn phần khác giúp quyết định có tiếp tục chia hay không. Tuy nhiên, không lấy một ước lượng chưa được bảo đảm làm số lượng để kết luận rằng tất cả dữ liệu đã được xử lý.

### SIZED đặt điều kiện chính xác lên ước lượng

Đặc tính `SIZED` cho biết kích thước được báo có bảo đảm chính xác trong điều kiện của hợp đồng. Điểm thời gian rất quan trọng:

1. Nếu bộ duyệt có `SIZED` và chưa bị duyệt một phần hoặc chia, ước lượng phải là số phần tử chính xác cho một lần duyệt đầy đủ hợp lệ.
2. Nếu bộ duyệt có `SUBSIZED` và chưa bị duyệt một phần, bảo đảm chính xác còn áp dụng sau khi chia.
3. Ngoài những điều kiện đó, không chỉ nhìn cờ rồi suy ra mọi cách triển khai đều luôn cập nhật đúng số phần tử còn lại sau từng lần `tryAdvance`.

Các bảo đảm còn giả định nguồn không bị can thiệp trái với chính sách của nó. Không dùng một số lượng đã lấy trước đó để chứng minh nguồn không thể thay đổi.

Một số lớp cập nhật kích thước còn lại rất chính xác sau mỗi bước. Chương trình về `ArrayList` bên dưới cho thấy hành vi đó. Đây là điều tốt của cách triển khai cụ thể, không phải lý do bỏ qua điều kiện trạng thái của giao diện.

## 3. getExactSizeIfKnown không tự duyệt để tìm đáp án

Cách triển khai mặc định của `getExactSizeIfKnown()` có logic tương đương đoạn minh họa sau:

```java
// Mã minh họa logic mặc định, không phải chương trình độc lập.
return hasCharacteristics(Spliterator.SIZED) ? estimateSize() : -1L;
```

Nó hỏi bộ duyệt có báo `SIZED` hay không. Nếu có, trả về `estimateSize()`; nếu không, trả về `-1`. Phương thức không biến một nguồn chưa biết số lượng thành nguồn đã được đếm. [Javadoc getExactSizeIfKnown](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Spliterator.html#getExactSizeIfKnown())

Tên phương thức cần được đọc cùng hợp đồng về trạng thái bộ duyệt:

- Trước khi duyệt hoặc chia, `SIZED` là cơ sở để dùng số lượng chính xác.
- Khi nguồn có bảo đảm mạnh hơn như `SUBSIZED`, có thể giữ thông tin chính xác qua các lần chia theo điều kiện tương ứng.
- Sau khi đã duyệt một phần, cách triển khai mặc định vẫn chỉ kiểm tra `SIZED` rồi chuyển tiếp sang `estimateSize()`; nó không bổ sung một cơ chế kiểm đếm độc lập.

Do đó, muốn viết thuật toán tổng quát cho mọi `Spliterator`, không dùng tên “exact” để suy rộng rằng số trả về sau mọi trạng thái luôn là một bộ đếm còn lại được cập nhật.

### -1 và 0 mang ý nghĩa khác nhau

`-1` là tín hiệu không báo một kích thước chính xác. Nó không phải số lượng âm của dữ liệu và không đồng nghĩa nguồn rỗng.

`0` là một số lượng. Với một bộ duyệt mới có kích thước được bảo đảm chính xác và nguồn hợp lệ, `0` cho biết không có phần tử để duyệt.

Nếu thực sự cần số phần tử của một nguồn chưa biết kích thước, phải chọn một phép đếm có duyệt. Việc đó tiêu thụ bộ duyệt hoặc stream tương ứng, có thể tốn chi phí và không thể hoàn tất với nguồn vô hạn. Đọc metadata và duyệt để đếm là hai công việc khác nhau.

## 4. Thí nghiệm: kích thước của ArrayList khi duyệt và chia

Lưu vào `SpliteratorSizesDemo.java`.

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;

public class SpliteratorSizesDemo {
    public static void main(String[] args) {
        List<Integer> source = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        Spliterator<Integer> remaining = source.spliterator();

        System.out.println("initialEstimate=" + remaining.estimateSize());
        System.out.println("initialExact=" + remaining.getExactSizeIfKnown());
        System.out.println("sized=" + remaining.hasCharacteristics(Spliterator.SIZED));
        System.out.println("subsized="
                + remaining.hasCharacteristics(Spliterator.SUBSIZED));

        List<Integer> first = new ArrayList<>();
        remaining.tryAdvance(first::add);
        System.out.println("first=" + first);
        long beforeSplit = remaining.estimateSize();
        System.out.println("estimateAfterOne=" + beforeSplit);
        System.out.println("exactAfterOne=" + remaining.getExactSizeIfKnown());

        Spliterator<Integer> prefix = remaining.trySplit();
        if (prefix == null) {
            throw new IllegalStateException("Expected a split for this ArrayList");
        }
        System.out.println("prefixExact=" + prefix.getExactSizeIfKnown());
        System.out.println("remainingExact=" + remaining.getExactSizeIfKnown());
        System.out.println("splitEstimatesAddUp="
                + (beforeSplit == prefix.estimateSize() + remaining.estimateSize()));

        List<Integer> left = new ArrayList<>();
        List<Integer> right = new ArrayList<>();
        prefix.forEachRemaining(left::add);
        remaining.forEachRemaining(right::add);
        System.out.println("prefixValues=" + left);
        System.out.println("remainingValues=" + right);
        System.out.println("remainingEstimateAfterDrain=" + remaining.estimateSize());
        System.out.println("source=" + source);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SpliteratorSizesDemo.java
java SpliteratorSizesDemo
```

Stdout:

```text
initialEstimate=6
initialExact=6
sized=true
subsized=true
first=[1]
estimateAfterOne=5
exactAfterOne=5
prefixExact=2
remainingExact=3
splitEstimatesAddUp=true
prefixValues=[2, 3]
remainingValues=[4, 5, 6]
remainingEstimateAfterDrain=0
source=[1, 2, 3, 4, 5, 6]
```

Đầu tiên, bộ duyệt bao phủ sáu phần tử nên cả ước lượng lẫn kích thước chính xác đều là 6. Nó báo `SIZED` và `SUBSIZED`.

Sau `tryAdvance`, phần tử `1` đã được xử lý. Với bộ duyệt của `ArrayList` trong chương trình, số lượng còn lại được cập nhật thành 5.

Lần `trySplit` tiếp theo trả về một bộ duyệt cho tiền tố `[2, 3]`; đối tượng `remaining` giữ phần `[4, 5, 6]`. Có thể hình dung:

```text
Ban đầu:            [1, 2, 3, 4, 5, 6]  kích thước 6
Đã duyệt:           [1]
Còn trước khi chia: [2, 3, 4, 5, 6]     kích thước 5
Sau khi chia:
  prefix            [2, 3]              kích thước 2
  remaining         [4, 5, 6]           kích thước 3
```

Tổng hai ước lượng sau chia bằng ước lượng trước chia: `2 + 3 = 5`. Đây là quan hệ mà `SUBSIZED` yêu cầu khi chia thành công.

Các bộ duyệt xử lý những phần việc tách nhau; `trySplit` không nhân bản mọi phần tử để cả hai đối tượng cùng duyệt toàn bộ nguồn. Sau khi phần việc của `remaining` được duyệt hết, ước lượng của nó trong ví dụ là 0. Danh sách nguồn vẫn giữ nguyên sáu số.

### SIZED và SUBSIZED không thay thế cho nhau

`SIZED` nói về thông tin kích thước của bộ duyệt hiện tại ở điều kiện đã nêu. `SUBSIZED` bổ sung bảo đảm rằng các bộ duyệt con được tạo trực tiếp hoặc gián tiếp bởi `trySplit` cũng có `SIZED` và `SUBSIZED`.

Một cấu trúc có thể biết tổng số phần tử nhưng không lưu sẵn số phần tử chính xác của mỗi nhánh nhỏ. Khi đó, báo `SIZED` cho bộ duyệt gốc chưa đủ để báo `SUBSIZED`.

Các quan hệ cần nhớ là:

```text
SUBSIZED ⇒ SIZED
SIZED ⇏ SUBSIZED
```

Tỷ lệ chia `2/3` trong chương trình là kết quả quan sát với `ArrayList` và trạng thái cụ thể. Không đặt yêu cầu mọi nguồn phải luôn chia đúng nửa. Một nguồn có thể không chia được hoặc quyết định không chia vì chi phí; `trySplit()` trả `null` không tự chứng minh rằng nguồn rỗng.

## 5. Thí nghiệm: nguồn hữu hạn nhưng không biết kích thước

Lưu vào `UnknownSizeSpliteratorDemo.java`.

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;

public class UnknownSizeSpliteratorDemo {
    public static void main(String[] args) {
        Spliterator<String> unknown = Spliterators.spliteratorUnknownSize(
                List.of("red", "blue", "green").iterator(),
                Spliterator.ORDERED);

        System.out.println("estimateIsMax="
                + (unknown.estimateSize() == Long.MAX_VALUE));
        System.out.println("exact=" + unknown.getExactSizeIfKnown());
        System.out.println("sized=" + unknown.hasCharacteristics(Spliterator.SIZED));

        List<String> visited = new ArrayList<>();
        unknown.tryAdvance(visited::add);
        System.out.println("first=" + visited);
        System.out.println("estimateAfterOneIsMax="
                + (unknown.estimateSize() == Long.MAX_VALUE));
        System.out.println("exactAfterOne=" + unknown.getExactSizeIfKnown());

        unknown.forEachRemaining(visited::add);
        System.out.println("allVisited=" + visited);
        System.out.println("actualVisitedCount=" + visited.size());
        System.out.println("exactAfterDrain=" + unknown.getExactSizeIfKnown());
        System.out.println("hasAnother=" + unknown.tryAdvance(visited::add));

        Spliterator<String> empty = Spliterators.emptySpliterator();
        System.out.println("knownEmptyEstimate=" + empty.estimateSize());
        System.out.println("knownEmptyExact=" + empty.getExactSizeIfKnown());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 UnknownSizeSpliteratorDemo.java
java UnknownSizeSpliteratorDemo
```

Stdout:

```text
estimateIsMax=true
exact=-1
sized=false
first=[red]
estimateAfterOneIsMax=true
exactAfterOne=-1
allVisited=[red, blue, green]
actualVisitedCount=3
exactAfterDrain=-1
hasAnother=false
knownEmptyEstimate=0
knownEmptyExact=0
```

Nguồn chỉ có ba chuỗi. Tuy nhiên, chương trình cố ý bọc iterator bằng `Spliterators.spliteratorUnknownSize`, nên bộ duyệt không báo `SIZED`. Phương thức hỗ trợ này cũng không báo `SIZED` hoặc `SUBSIZED` ngay cả nếu người gọi truyền các cờ đó vào tham số đặc tính. [Javadoc spliteratorUnknownSize](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Spliterators.html#spliteratorUnknownSize(java.util.Iterator,int))

Đọc các dòng kết quả:

- `estimateIsMax=true`: dùng `Long.MAX_VALUE` để biểu thị không có một ước lượng hữu hạn hữu ích lúc này.
- `exact=-1`: không báo kích thước chính xác.
- `first=[red]`: bộ duyệt vẫn duyệt bình thường dù chưa biết kích thước.
- Sau một phần tử, ước lượng trong cách triển khai này vẫn là `Long.MAX_VALUE`.
- `actualVisitedCount=3`: việc duyệt thật đã xử lý ba phần tử.
- `exactAfterDrain=-1`: không tự xuất hiện cờ `SIZED` chỉ vì đã đi đến cuối.
- `hasAnother=false`: `tryAdvance` mới trực tiếp xác nhận không còn phần tử cho lần gọi đó.
- Bộ duyệt rỗng được tạo bằng `emptySpliterator` báo ước lượng và kích thước chính xác là 0.

Điều này cho thấy **không biết kích thước** và **không có phần tử** là hai trạng thái khác nhau.

### Không dùng metadata thay cho điều kiện duyệt

Không viết vòng lặp tổng quát kiểu “cứ duyệt khi `getExactSizeIfKnown() > 0`”. Với nguồn chưa biết kích thước, giá trị `-1` sẽ khiến bỏ qua cả nguồn đang có dữ liệu.

Cũng không dùng “`estimateSize() == Long.MAX_VALUE` thì dừng vì nguồn vô hạn”. Chương trình vừa kiểm chứng một nguồn hữu hạn có đúng giá trị ước lượng đó.

Khi mục tiêu là duyệt từng phần tử, dùng kết quả của `tryAdvance` hoặc thao tác duyệt phù hợp. Khi mục tiêu là quyết định chiến lược, đọc metadata cùng các bảo đảm của nó.

### Đếm nguồn sẽ tiêu thụ nguồn

Biến `actualVisitedCount` trong ví dụ được suy ra từ danh sách các giá trị đã thu thập sau khi duyệt. Đây là một phép kiểm chứng có chi phí duyệt và lưu kết quả.

Nếu chỉ cần đếm, có thể dùng một phép tổng hợp đếm thay cho lưu tất cả. Nhưng vẫn không thể dùng lại bộ duyệt đã bị tiêu thụ để đi qua nguồn lần nữa. Muốn vừa có kết quả vừa có số lượng, thiết kế phép xử lý để giữ thông tin cần thiết hoặc tạo một bộ duyệt mới từ nguồn có thể duyệt lại.

## 6. Đặc tính nào liên quan trực tiếp đến kích thước?

`characteristics()` là mặt nạ bit công bố các bảo đảm của bộ duyệt. Có thể gọi `hasCharacteristics(mask)` để hỏi xem bộ duyệt có đủ **tất cả** bit trong mặt nạ hay không. Khi đang quyết định về kích thước, hai bit cần xem trước là:

| Bit | Điều được bảo đảm |
| --- | --- |
| `SIZED` | Kích thước ước lượng chính xác khi đáp ứng điều kiện về trạng thái duyệt và nguồn không bị can thiệp sai hợp đồng. |
| `SUBSIZED` | Những bộ duyệt tạo ra trực tiếp hoặc gián tiếp qua `trySplit` tiếp tục có bảo đảm `SIZED` và `SUBSIZED`. |

Quan hệ là `SUBSIZED ⇒ SIZED`, còn `SIZED` không suy ra `SUBSIZED`. Vì vậy, một bộ duyệt biết số lượng toàn nguồn vẫn có thể không biết chính xác kích thước của từng phần được chia. `getExactSizeIfKnown()` mặc định dựa vào `SIZED` và `estimateSize()`; nó không tự đếm lại phần còn lại.

Những bit khác trả lời câu hỏi khác: `ORDERED` nói về thứ tự gặp, `SORTED` về quy tắc sắp xếp, `DISTINCT` về phần tử không trùng theo equals, `NONNULL` về việc không có phần tử null, `IMMUTABLE` về nguồn ổn định khi duyệt và `CONCURRENT` về chính sách cập nhật nguồn. Không có một bit không có nghĩa thuộc tính đối nghịch chắc chắn đúng. Các điều kiện và quan hệ của từng bit được phân tích riêng trong bài *ORDERED, SORTED, DISTINCT, SIZED, IMMUTABLE và CONCURRENT*.

## 7. Đối chiếu nguồn để không suy kích thước từ tên lớp

Trên JDK 17, bộ duyệt `ArrayList` thông thường có `SIZED` và `SUBSIZED`, nên ví dụ ở mục 4 kiểm chứng được kích thước của hai phần sau chia. Bộ duyệt `TreeSet` báo `SIZED` nhưng không báo `SUBSIZED`: biết tổng số phần tử chưa đủ để yêu cầu mọi nhánh con có số lượng chính xác. Một nguồn bọc iterator bằng `spliteratorUnknownSize` như mục 5 không báo `SIZED` dù dữ liệu thực tế hữu hạn.

`CopyOnWriteArrayList` có cơ chế duyệt ảnh chụp và báo `IMMUTABLE`, trong khi khung nhìn khóa `ConcurrentHashMap` báo `CONCURRENT` và không cam kết kích thước chính xác như một ảnh chụp nguyên tử khi nguồn có thể được cập nhật. Đây là sự khác biệt trong **chính sách nguồn**, không phải chứng minh bộ duyệt hoặc hàm xử lý tự an toàn khi nhiều luồng cùng sửa trạng thái ngoài pipeline.

Khi viết thuật toán, hãy kiểm tra bit của **đối tượng Spliterator đang sử dụng**, thay vì lấy một bảng cờ của lớp khác để suy diễn. Phần tiếp theo xét vì sao chính các thao tác của Stream có thể làm thay đổi metadata đó.

## 8. Kích thước nguồn không phải kích thước của mọi pipeline

Một danh sách có bốn phần tử không có nghĩa mọi stream bắt đầu từ nó đều biết trước số phần tử đầu ra.

- `map` thông thường biến mỗi phần tử đầu vào thành một phần tử đầu ra, nên có thể giữ thông tin số lượng.
- `filter` có thể loại bao nhiêu phần tử tùy giá trị và điều kiện; trước khi đánh giá, chưa chắc biết số lượng đầu ra.
- `limit` có thể làm thông tin số lượng thay đổi theo một giới hạn đã biết.
- Các bước khác còn có thể làm thay đổi những đặc tính như `DISTINCT` hoặc `SORTED`.

Cần hỏi metadata của đúng bộ duyệt đang xử lý, không lấy metadata nguồn ban đầu rồi áp nguyên cho đầu ra.

Lưu vào `StreamSizeMetadataDemo.java`.

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;

public class StreamSizeMetadataDemo {
    private static void describe(String name, Spliterator<?> spliterator) {
        System.out.println(name + ":estimate=" + spliterator.estimateSize()
                + ",exact=" + spliterator.getExactSizeIfKnown()
                + ",sized=" + spliterator.hasCharacteristics(Spliterator.SIZED));
    }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>(
                List.of("A", "BB", "CCC", "DDDD"));

        describe("source", source.spliterator());
        describe("mapped", source.stream().map(String::length).spliterator());

        Spliterator<String> filtered = source.stream()
                .filter(value -> value.length() >= 3)
                .spliterator();
        describe("filtered", filtered);
        List<String> selected = new ArrayList<>();
        filtered.forEachRemaining(selected::add);
        System.out.println("filteredActual=" + selected);
        System.out.println("filteredActualCount=" + selected.size());

        describe("limited", source.stream().limit(2).spliterator());
        System.out.println("source=" + source);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 StreamSizeMetadataDemo.java
java StreamSizeMetadataDemo
```

Stdout:

```text
source:estimate=4,exact=4,sized=true
mapped:estimate=4,exact=4,sized=true
filtered:estimate=4,exact=-1,sized=false
filteredActual=[CCC, DDDD]
filteredActualCount=2
limited:estimate=2,exact=2,sized=true
source=[A, BB, CCC, DDDD]
```

Đây là kết quả kiểm chứng trên JDK 17:

| Trường hợp | Ước lượng | Kích thước chính xác báo được | Quan sát |
| --- | --- | --- | --- |
| Nguồn `ArrayList` | 4 | 4 | Biết số lượng trước khi duyệt |
| Sau `map(String::length)` | 4 | 4 | Không thay đổi số lượng |
| Sau `filter` giữ chuỗi dài từ 3 ký tự | 4 | -1 | Chưa biết chính xác số chuỗi qua lọc |
| Sau `limit(2)` trên nguồn đã biết kích thước | 2 | 2 | Cách triển khai tính được giới hạn số lượng |

Với `filtered`, ước lượng 4 không phải số phần tử kết quả thực. Khi duyệt, chỉ có `CCC` và `DDDD`, nên số thực là 2.

Không suy rộng rằng mọi ước lượng chưa có `SIZED` đều luôn là một cận trên chặt hoặc luôn bằng số phần tử nguồn. Hợp đồng chung cho phép ước lượng không chính xác; bảng chỉ mô tả các bộ duyệt cụ thể trong chương trình.

Cũng không dùng ví dụ này để khẳng định mọi nguồn hoặc mọi cách triển khai stream đều phải báo bộ cờ giống nhau sau mỗi thao tác. Phía sử dụng đọc những bảo đảm thực tế được báo, còn kết quả phép xử lý vẫn phải tuân theo hợp đồng của thao tác stream.

### Lấy spliterator từ stream là chuyển quyền điều khiển việc duyệt

`Stream.spliterator()` là một thao tác kết thúc đặc biệt cho phép người gọi tự điều khiển việc duyệt. Sau khi đã lấy bộ duyệt, không tiếp tục dùng chính đối tượng stream đó cho một thao tác kết thúc khác. [Javadoc BaseStream.spliterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/BaseStream.html#spliterator())

Chương trình tạo một stream mới cho mỗi trường hợp `mapped`, `filtered` và `limited`. Riêng `filtered` được duyệt qua bộ duyệt đã lấy, không quay lại gọi `toList()` trên một stream đã sử dụng.

Việc gọi `estimateSize` hoặc `getExactSizeIfKnown` trên bộ duyệt không đồng nghĩa đã xử lý từng phần tử. Tuy nhiên, lấy bộ duyệt từ stream đã xác lập cách sử dụng stream đó; không nhầm hai vòng đời này.

## 9. Hỏi kích thước có thể làm nguồn ràng buộc sớm

Một bộ duyệt ràng buộc muộn chưa nhất thiết chốt phạm vi nguồn ngay lúc được tạo. Theo hợp đồng của loại bộ duyệt này, lần duyệt đầu, lần chia đầu hoặc lần hỏi kích thước ước lượng đầu có thể là thời điểm ràng buộc với nguồn.

Vì vậy, thao tác “chỉ xem thử kích thước” không nhất thiết hoàn toàn vô nghĩa đối với vòng đời của bộ duyệt.

Đoạn dưới minh họa mốc cần chú ý, không phải chương trình mẫu với kết quả duyệt sau sửa được bảo đảm:

```java
List<String> source = new ArrayList<>(List.of("A", "B"));
Spliterator<String> spliterator = source.spliterator();

long initialEstimate = spliterator.estimateSize();
// Lần hỏi kích thước đã ràng buộc bộ duyệt ArrayList với nguồn.

source.add("C");
// Không tiếp tục xem đây là cùng tình huống với việc thêm trước ràng buộc.
// Duyệt tiếp trên bộ duyệt đã ràng buộc có thể phát hiện can thiệp.
```

`getExactSizeIfKnown` mặc định gọi `estimateSize` khi có `SIZED`, nên cũng có thể dẫn tới việc ràng buộc đó.

Không dùng một lần ghi nhật ký thông tin mô tả rồi giả định bộ duyệt vẫn còn ở trạng thái hoàn toàn chưa đụng đến nguồn. Nếu cần xử lý dữ liệu sau khi đã sửa một collection thông thường, tạo bộ duyệt mới ở thời điểm hợp lệ hoặc thiết kế một ảnh chụp riêng.

### Có kích thước chính xác không có nghĩa nguồn không đổi

`SIZED` mô tả số lượng trong những điều kiện hợp lệ, không cung cấp khóa hay ngăn một bên khác sửa nguồn. `ArrayList` vẫn cần tuân thủ chính sách không can thiệp trong lúc duyệt.

Bộ duyệt fail-fast có thể ném `ConcurrentModificationException` khi phát hiện thay đổi, nhưng việc phát hiện không bảo đảm bắt mọi trường hợp. Không có ngoại lệ chưa chứng minh dữ liệu không bị can thiệp.

Tương tự, `CONCURRENT` không giải quyết mọi yêu cầu về tính nhất quán. Nếu nguồn đang thay đổi, cần đọc chính sách của nguồn để biết kết quả duyệt có thể quan sát các cập nhật như thế nào, thay vì đòi một kích thước cố định suốt quá trình.

## 10. Dùng metadata để lựa chọn, không bịa thêm bảo đảm

### Chuẩn bị vùng chứa

Nếu có một kích thước chính xác trong trạng thái được bảo đảm, thông tin đó có thể giúp chuẩn bị vùng chứa phù hợp. Nếu chỉ có ước lượng hoặc giá trị không biết, vùng chứa cần tăng trưởng linh hoạt.

Trước khi dùng một giá trị `long` để tạo vùng chứa có kích thước kiểu `int`, cần kiểm tra phạm vi. Không ép `Long.MAX_VALUE` hoặc `-1` thành một kích thước mảng rồi xem đó là số phần tử thực.

Trong mã ứng dụng thông thường, thao tác thu thập của stream thường đã xử lý những quyết định này. Không cần tự dựng một thuật toán cấp phát chỉ để thay một lời gọi thu thập rõ ràng.

### Chia công việc

`estimateSize` giúp đánh giá quy mô phần việc; `SUBSIZED` cho thêm bảo đảm về kích thước các phần sau chia. Tuy nhiên, biết kích thước không nói rằng mỗi phần tử có cùng chi phí xử lý.

Hai phần có cùng số phần tử vẫn có thể mất thời gian rất khác nhau. Ngược lại, một nguồn có ước lượng chưa chính xác vẫn có thể chia và xử lý hiệu quả. Các cờ là thông tin đầu vào cho chiến lược, không phải kết quả đo hiệu năng.

### Tối ưu thao tác stream

Biết số lượng và đặc tính có thể giúp thư viện chọn cách xử lý tốt hơn. Ví dụ, khi số lượng đã biết và những bước trung gian không làm thay đổi số lượng, một phép `count` có thể không cần duyệt từng phần tử. Điều này cũng là lý do không đặt hành động bắt buộc vào một bước quan sát rồi giả định nó luôn được gọi. [Javadoc Stream.count](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#count())

Không tự gắn `SIZED`, `SORTED` hoặc `DISTINCT` cho một bộ duyệt chỉ để mong thư viện chạy nhanh hơn. Nếu thông tin báo sai, phía sử dụng có thể bỏ qua những công việc mà thực tế vẫn cần làm.

### Những lỗi cần nhận ra

| Cách hiểu sai | Điều cần sửa |
| --- | --- |
| `estimateSize` luôn là số phần tử thật | Kiểm tra `SIZED`, `SUBSIZED`, trạng thái duyệt và chính sách nguồn |
| `Long.MAX_VALUE` chứng minh nguồn vô hạn | Nó còn có thể biểu thị chưa biết hoặc tính kích thước quá tốn kém |
| `getExactSizeIfKnown` tự đếm toàn bộ | Cách mặc định chỉ kiểm tra cờ rồi trả metadata hoặc `-1` |
| `-1` nghĩa là hết dữ liệu | Dùng thao tác duyệt để xác định còn phần tử; `-1` là không biết kích thước |
| Có `SIZED` thì mọi con sau chia cũng có | Cần phân biệt với bảo đảm `SUBSIZED` |
| Bộ duyệt đã duyệt một phần vẫn luôn báo chính xác phần còn lại | Đọc bảo đảm theo trạng thái và cách triển khai; không suy rộng từ một ví dụ |
| `ORDERED` đồng nghĩa `SORTED` | Thứ tự chỉ số hoặc thứ tự chèn không phải thứ tự sắp xếp |
| Không có `NONNULL` nghĩa là chắc có null | Thiếu cờ chỉ có nghĩa không cung cấp cam kết đó |
| `IMMUTABLE` làm mọi đối tượng phần tử bất biến | Nó mô tả nguồn phần tử của lần duyệt, không phải bất biến sâu |
| `CONCURRENT` cho phép nhiều bên dùng cùng bộ duyệt tùy ý | Tách chính sách cập nhật nguồn khỏi quyền truy cập đối tượng bộ duyệt |
| `hasCharacteristics` với mặt nạ A và B chỉ cần có một cờ | Phương thức yêu cầu có tất cả cờ trong mặt nạ |
| Hỏi kích thước không ảnh hưởng trạng thái nguồn ràng buộc | Trên nguồn ràng buộc muộn, việc hỏi có thể kích hoạt ràng buộc |

## 11. Thực hành đọc đúng những bảo đảm

### Bài 1: một số hữu hạn chưa có SIZED

Một bộ duyệt báo `estimateSize() == 100` nhưng không có `SIZED`. Có thể dùng 100 làm tổng số phần tử chắc chắn sẽ duyệt không? Cách mặc định của `getExactSizeIfKnown()` trả gì?

**Đối chiếu:**

Không thể khẳng định số thật là 100 chỉ từ ước lượng. Cách mặc định trả `-1` vì không có `SIZED`. Số hữu hạn và kích thước được bảo đảm chính xác là hai điều kiện khác nhau.

### Bài 2: nguồn có ba phần tử nhưng ước lượng cực lớn

`spliteratorUnknownSize` bọc iterator của một danh sách có ba phần tử. Nó báo `Long.MAX_VALUE` và `-1` ở hai phương thức kích thước. Dữ liệu có bị mất hoặc nguồn có bắt buộc vô hạn không?

**Đối chiếu:**

Không cho cả hai câu. Lớp bao không cung cấp kích thước chính xác nhưng vẫn duyệt được ba phần tử. Có thể kiểm chứng bằng `tryAdvance` hoặc `forEachRemaining`; việc đó thực sự tiêu thụ các phần tử.

### Bài 3: biết tổng nhưng chưa biết từng phần

Một bộ duyệt gốc có `SIZED` nhưng không có `SUBSIZED`. Sau `trySplit`, có được mặc định mọi bộ duyệt con đều có kích thước chính xác không?

**Đối chiếu:**

Không. Phải xem đặc tính và hợp đồng của từng bộ duyệt con. Bảo đảm `SIZED` của gốc không tự tạo bảo đảm đệ quy về kích thước các phần chia.

### Bài 4: dữ liệu đang không null

Một `ArrayList<String>` đang chứa `[A, B]`, nhưng bộ duyệt không báo `NONNULL`. Có mâu thuẫn không?

**Đối chiếu:**

Không. Hai giá trị hiện tại đều khác `null`, nhưng lớp không cung cấp cam kết `NONNULL` qua bộ duyệt đó. Thiếu cờ không đồng nghĩa khẳng định ngược lại.

### Bài 5: sắp xếp và thứ tự tự nhiên

Một bộ duyệt có `SORTED`, và `getComparator()` trả `null`. Nó có còn phải báo `ORDERED` không? Giá trị `null` có nghĩa nguồn không được sắp xếp không?

**Đối chiếu:**

Nó vẫn phải có `ORDERED`. Giá trị `null` ở trường hợp có `SORTED` biểu thị dùng thứ tự tự nhiên. Nếu không có `SORTED`, lời gọi `getComparator` không phải trả `null` để báo “không biết” mà ném `IllegalStateException`.

### Bài 6: cờ có trước và sau phép lọc

Nguồn có bốn chuỗi, nhưng chỉ hai chuỗi thỏa điều kiện. Bộ duyệt của pipeline sau `filter` báo ước lượng 4, kích thước chính xác `-1`. Cần dùng số nào làm số kết quả thật?

**Đối chiếu:**

Chưa có số kết quả thật từ metadata đó. Phải thực hiện phép xử lý để biết có hai chuỗi qua lọc. Không gán số 4 cho kích thước kết quả chỉ vì đó là kích thước nguồn hoặc ước lượng đang được báo.

### Bài 7: đọc mặt nạ nhiều cờ

Một bộ duyệt có `ORDERED` và `SIZED`, nhưng không có `SORTED`. Dự đoán:

```java
// Đoạn câu hỏi, s là bộ duyệt có các cờ vừa nêu.
boolean first = s.hasCharacteristics(Spliterator.ORDERED | Spliterator.SIZED);
boolean second = s.hasCharacteristics(Spliterator.ORDERED | Spliterator.SORTED);
```

**Đối chiếu:**

Biểu thức đầu trả `true`; biểu thức sau trả `false`. Phải có đủ mọi cờ được yêu cầu.

### Bài 8: kiểm tra sau một phần đã duyệt

Trong `SpliteratorSizesDemo`, sau khi duyệt phần tử đầu, cả hai phương thức đều cho 5. Có thể biến kết quả này thành quy tắc bắt buộc cho mọi `Spliterator` có `SIZED` sau từng `tryAdvance` không?

**Đối chiếu:**

Không. Chương trình kiểm chứng cách triển khai của `ArrayList` ở trạng thái cụ thể. Hợp đồng chung của ước lượng phân biệt trạng thái chưa duyệt, đã chia và đã duyệt một phần. Cách mặc định của `getExactSizeIfKnown` không tự bổ sung phép đếm còn lại.

Khi thiết kế một phép xử lý mới, hãy ghi rõ đang hỏi bộ duyệt nào, đã tiêu thụ hoặc chia phần nào, nguồn có thể thay đổi theo chính sách nào và cờ nào thật sự được báo. Những thông tin đó mới cho phép dùng metadata đúng ý nghĩa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="streams-spliterators-005">ORDERED, SORTED, DISTINCT, SIZED, IMMUTABLE và CONCURRENT</a>

<details>
<summary>Xem chi tiết</summary>

# ORDERED, SORTED, DISTINCT, SIZED, IMMUTABLE và CONCURRENT

## 1. Đặc tính là cam kết cho người dùng bộ duyệt

Một Spliterator có thể đưa phần tử ra từng bước, duyệt phần còn lại và chia nguồn thành các phần. Để thuật toán biết có thể dựa vào điều gì, nó còn công bố những **đặc tính** qua `characteristics()`.

Sáu đặc tính trong bài trả lời những nhóm câu hỏi khác nhau:

| Đặc tính | Câu hỏi được trả lời |
| --- | --- |
| `ORDERED` | Các phần tử có một thứ tự gặp được xác định không? |
| `SORTED` | Thứ tự gặp có tuân theo một quy tắc sắp xếp không? |
| `DISTINCT` | Các phần tử gặp được có khác nhau theo quan hệ equals không? |
| `SIZED` | Có cam kết biết chính xác số phần tử trong điều kiện quy định không? |
| `IMMUTABLE` | Nguồn mà bộ duyệt bao quát có tránh được việc thêm, xóa, thay phần tử trong lúc duyệt không? |
| `CONCURRENT` | Nguồn có chính sách cho phép thay đổi đồng thời an toàn trong lúc duyệt không? |

Các bit này là thông tin về nguồn, cấu trúc và phần tử mà thuật toán có thể tận dụng. Chúng không phải lệnh yêu cầu Spliterator tự sắp xếp, tự loại trùng hoặc tự khóa dữ liệu. [Java SE 17 — Spliterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Spliterator.html).

Ví dụ một danh sách `[3, 1, 3]` có thứ tự chỉ số rõ ràng nhưng chưa tăng dần và có phần tử trùng. Một TreeSet có thể vừa có thứ tự, vừa được sắp xếp, vừa không trùng. Một khung nhìn khóa của ConcurrentHashMap có thể không có thứ tự gặp được cam kết, nhưng lại hỗ trợ nguồn thay đổi đồng thời.

Muốn dùng đúng, phải đọc từng đặc tính theo phạm vi cam kết của nó, thay vì coi mọi tính chất thuận lợi là cùng một loại “an toàn”.

## 2. Đọc bit đúng cách bằng hasCharacteristics

`characteristics()` trả về một `int` chứa nhiều bit được kết hợp bằng phép OR. Khi chỉ cần kiểm tra, dùng phương thức tiện ích:

~~~java
boolean ordered = source.hasCharacteristics(Spliterator.ORDERED);
~~~

Đây là đoạn minh họa với `source` là một `Spliterator<?>`. `ordered` bằng `true` khi bộ duyệt công bố đặc tính đó.

Để yêu cầu đồng thời nhiều đặc tính:

~~~java
boolean both = source.hasCharacteristics(
        Spliterator.ORDERED | Spliterator.SORTED);
~~~

`both` có nghĩa là **có cả ORDERED lẫn SORTED**, không phải có ít nhất một trong hai.

Nếu tự dùng phép toán bit, cách kiểm tra đầy đủ là:

~~~java
int required = Spliterator.ORDERED | Spliterator.SORTED;
boolean all = (source.characteristics() & required) == required;
~~~

So sánh `(flags & required) != 0` chỉ xác nhận có ít nhất một bit đang bật. Nhầm hai cách kiểm tra có thể khiến thuật toán đi vào nhánh cần dữ liệu đã sắp xếp dù nguồn chỉ có thứ tự chỉ số.

Không so sánh `source.characteristics() == Spliterator.ORDERED` để hỏi có ORDERED không. Một Spliterator có thể đồng thời báo `ORDERED`, `SIZED` và `SUBSIZED` nên giá trị toàn bộ mặt nạ khác riêng hằng `ORDERED`.

### Không có bit không chứng minh tính chất đối nghịch

Một ArrayList đang chứa `[1, 2, 3]` có thể không công bố `SORTED`. Dữ liệu cụ thể nhìn tăng dần không có nghĩa cách cài đặt Spliterator đã công bố quy tắc sắp xếp ấy.

Tương tự, một danh sách hiện có ba số khác nhau chưa chắc báo `DISTINCT`. Thuật toán phải phân biệt:

- Quan sát được tính chất ở một bộ dữ liệu cụ thể.
- Có đặc tính được bộ duyệt công bố để dựa vào như một hợp đồng.

Thông tin đặc tính có thể mang tính bảo thủ. Không tự suy ra bit chỉ bằng một lần nhìn kết quả in, và không tự gắn bit để mong Stream chạy nhanh hơn.

## 3. ORDERED: thứ tự gặp, không phải thứ tự tăng dần

`ORDERED` nghĩa là nguồn xác định một **thứ tự gặp** cho các phần tử. Với List, đó là thứ tự chỉ số tăng dần.

Ví dụ:

~~~text
List:       [3, 1, 3]
thứ tự gặp: 3 rồi 1 rồi 3
~~~

Thứ tự này rõ ràng nhưng không phải thứ tự số tăng dần. Đây là ví dụ điển hình của `ORDERED` mà không cần `SORTED`.

Với Spliterator có ORDERED:

- `tryAdvance` lấy phần tử kế tiếp theo thứ tự gặp.
- `forEachRemaining` gọi hành động theo thứ tự gặp của phần nguồn đang bao quát.
- `trySplit` khi chia thành công phải trả về một tiền tố của phần chưa duyệt.

Nhờ quy tắc tiền tố, thuật toán có thể xử lý phần đầu và phần sau riêng rồi ghép đúng thứ tự. Tuy nhiên, bản thân việc chia không làm thời điểm hoàn tất giữa nhiều luồng tự xếp theo thứ tự nguồn.

### Các nguồn khác nhau có các cam kết khác nhau

`LinkedHashSet` vừa giữ các phần tử không trùng vừa có thứ tự chèn. Nếu chèn `3`, `1` rồi `3` lần nữa, kết quả duyệt là `[3, 1]`: có thứ tự nhưng không tự sắp tăng dần. [Java SE 17 — LinkedHashSet.spliterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html#spliterator()).

`HashSet` không cam kết thứ tự duyệt. Một lần chạy có thể in ra những con số trông tăng dần, nhưng điều đó không phải hợp đồng để dựa vào khi hiển thị, kiểm thử hoặc phân trang. [Java SE 17 — HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html).

Không có ORDERED cũng không có nghĩa mỗi lần duyệt bắt buộc phải khác lần trước hoặc dữ liệu bị xáo ngẫu nhiên. Nó chỉ nghĩa là không có một thứ tự gặp được cam kết theo đặc tính này.

## 4. SORTED: thứ tự tuân theo một phép so sánh

`SORTED` mạnh hơn ORDERED: không chỉ có thứ tự gặp, mà thứ tự ấy còn tuân theo một quy tắc sắp xếp.

Quan hệ bắt buộc:

~~~text
SORTED  =>  ORDERED
~~~

Một bộ duyệt báo SORTED phải đồng thời báo ORDERED. Chiều ngược lại không đúng.

### 4.1. getComparator phân biệt thứ tự tự nhiên và comparator riêng

Nếu nguồn được sắp theo thứ tự tự nhiên của các phần tử Comparable:

~~~java
Comparator<? super Integer> cmp = source.getComparator();
~~~

`cmp` có thể là `null` để biểu thị thứ tự tự nhiên. `null` trong trường hợp này không phải lỗi thiếu quy tắc sắp xếp.

Nếu nguồn dùng comparator riêng, `getComparator` trả về comparator tương ứng. Nếu bộ duyệt không báo SORTED, gọi `getComparator` sẽ gây `IllegalStateException` theo hợp đồng.

Để sử dụng an toàn:

~~~java
if (source.hasCharacteristics(Spliterator.SORTED)) {
    Comparator<? super Integer> cmp = source.getComparator();
    // cmp == null nghĩa là thứ tự tự nhiên.
}
~~~

Đây là đoạn minh họa cần các import và kiểu nguồn phù hợp, không phải một chương trình độc lập.

### 4.2. Sắp xếp không đồng nghĩa luôn tăng dần tự nhiên

Một TreeSet tạo bằng `Comparator.reverseOrder()` có thể duyệt `[3, 1]` và vẫn báo SORTED. Nó được sắp xếp theo comparator đảo chiều.

`TreeSet` công bố thứ tự dựa trên thứ tự tự nhiên hoặc comparator tại lúc tạo. Comparator cần nhất quán với equals để tuân thủ đầy đủ hợp đồng Set. [Java SE 17 — TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html).

Nếu mục tiêu là tăng dần tự nhiên, không được thấy SORTED rồi bỏ qua yêu cầu so sánh. Trong chương trình minh chứng, Stream từ TreeSet đảo chiều vẫn được gọi `sorted()` để cho `[1, 3]` theo thứ tự tự nhiên.

### 4.3. SORTED và DISTINCT là hai cam kết riêng

Một dãy đã sắp xếp vẫn có thể chứa các giá trị bằng nhau, như `[1, 1, 3]`. SORTED không tự loại trùng.

Ngược lại, một HashSet có thể không trùng nhưng không có thứ tự sắp xếp. Do đó, không suy ra DISTINCT từ SORTED và cũng không suy ra SORTED từ DISTINCT.

## 5. DISTINCT: không trùng theo equals

`DISTINCT` công bố rằng các phần tử được gặp khác nhau theo quan hệ `equals`. Nó không chỉ nói rằng chúng là những đối tượng có địa chỉ khác nhau.

Hai đối tượng `String` được tạo riêng nhưng có cùng nội dung vẫn có thể bằng nhau theo equals. Một nguồn không trùng theo danh tính tham chiếu chưa đủ để tự công bố DISTINCT theo nghĩa chung này.

Một Set dùng đúng hợp đồng là ví dụ quen thuộc. Với dữ liệu đầu vào `[3, 1, 3]`, HashSet hoặc LinkedHashSet giữ hai giá trị `3` và `1`. Đặc tính DISTINCT cho thuật toán biết không cần xem hai lần `3` như hai phần tử nguồn riêng nữa.

### DISTINCT không có nghĩa NONNULL

Một HashSet có thể chứa một phần tử `null` mà vẫn không có phần tử trùng. Cho phép một `null` và bảo đảm không trùng là hai điều kiện không mâu thuẫn.

`NONNULL` là đặc tính riêng nói rằng những phần tử được gặp không bao giờ null. Khi một thuật toán cần bỏ kiểm tra null, nó phải có bảo đảm phù hợp; DISTINCT không cung cấp bảo đảm đó.

### Phép ánh xạ có thể phá tính không trùng

Nguồn `[1, 3]` là các giá trị khác nhau. Nhưng phép biến đổi `n -> 0` cho ra `[0, 0]`. Nếu cứ giữ DISTINCT sau một phép ánh xạ tùy ý thì thuật toán có thể bỏ một bước loại trùng dù kết quả đã có trùng.

Đây là lý do phải xét đặc tính của **nguồn hoặc pipeline đang dùng**, không chỉ nhìn lớp collection ban đầu. Một phép biến đổi có thể bảo toàn số phần tử nhưng làm mất một cam kết khác.

Với các đối tượng có equals phụ thuộc trạng thái sửa được, việc sửa đối tượng trong lúc duyệt còn có thể phá những giả định của nguồn. Đặc tính không phải giấy phép thay đổi dữ liệu theo cách làm quan hệ bằng nhau biến đổi không kiểm soát.

## 6. SIZED: biết chính xác trong điều kiện của hợp đồng

`SIZED` nói rằng trước khi duyệt hoặc chia, giá trị `estimateSize()` là số lượng hữu hạn chính xác sẽ được gặp khi duyệt thành công, nếu không có sửa đổi nguồn làm mất điều kiện đó.

Không được rút gọn thành “estimateSize lúc nào cũng là số phần tử còn lại chính xác”. Trạng thái đã duyệt một phần, đã chia và các đặc tính của phần con đều cần được xét.

Ví dụ với ArrayList ba phần tử, trước khi duyệt:

~~~java
Spliterator<Integer> source = numbers.spliterator();
long size = source.getExactSizeIfKnown();
~~~

Trong chương trình minh chứng, `size` là `3`. Nếu không có SIZED, `getExactSizeIfKnown` trả `-1` thay vì biến một ước lượng thành số đếm chắc chắn.

### 6.1. SIZED không có nghĩa nguồn không thay đổi

Một ArrayList sửa được vẫn có Spliterator báo SIZED. Điều đó không làm nó bất biến hoặc an toàn khi nhiều luồng cùng thay đổi.

Cần tách:

- Có thể biết kích thước đúng trong điều kiện ổn định.
- Có bảo đảm nguồn giữ nguyên trong toàn bộ quá trình hay không.

Hai câu hỏi thuộc hai đặc tính hoặc chính sách khác nhau.

### 6.2. SUBSIZED là bảo đảm cho các phần sau khi chia

`SUBSIZED` liên quan trực tiếp tới SIZED: những Spliterator được tạo bởi các lần chia sẽ có các bảo đảm kích thước cần thiết, bao gồm tiếp tục báo SIZED và SUBSIZED theo hợp đồng.

Một nguồn biết tổng số phần tử chưa chắc biết chính xác kích thước từng nhánh được chia. Vì vậy:

~~~text
SUBSIZED => SIZED
SIZED không tự suy ra SUBSIZED
~~~

Spliterator của ArrayList báo cả ORDERED, SIZED và SUBSIZED. [Java SE 17 — ArrayList.spliterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html#spliterator()).

Trong ví dụ chưa duyệt phần tử nào, tách một phần rồi cộng hai `estimateSize` vẫn được tổng `3`. Đây là ca phù hợp để minh họa việc phân chia kích thước đã biết. Không dùng phép thử trên một loại nguồn để mặc định mọi cây, bảng băm hoặc nguồn I/O đều có cùng bảo đảm.

### 6.3. Không có SIZED không đồng nghĩa vô hạn

Nguồn có thể hữu hạn nhưng khó biết trước kích thước, hoặc có thay đổi đồng thời làm số đếm cố định không phù hợp. `estimateSize` vẫn có thể cho một ước lượng hữu ích.

`Long.MAX_VALUE` có thể biểu thị không biết, quá đắt để tính hoặc nguồn không hữu hạn. Chỉ nhìn con số đó không đủ để kết luận nguồn chắc chắn vô hạn.

Đặc tính giúp người dùng biết có được coi một con số là cam kết hay chỉ là thông tin ước lượng.

## 7. IMMUTABLE: nguồn ổn định đối với bộ duyệt, không phải sao chép sâu

Trong nghĩa của đặc tính này, nguồn mà Spliterator bao quát không thể bị thêm, xóa hoặc thay thế phần tử theo cách gây thay đổi trong lúc duyệt. Đó là cam kết về **nguồn phần tử**, không phải khẳng định mọi đối tượng phần tử đều có trạng thái bất biến.

### 7.1. CopyOnWriteArrayList cho thấy vì sao phải nói rõ phạm vi nguồn

`CopyOnWriteArrayList` là collection có thể được sửa. Tuy nhiên, Spliterator của nó giữ một ảnh chụp của trạng thái danh sách tại lúc tạo và báo IMMUTABLE, ORDERED, SIZED, SUBSIZED. [Java SE 17 — CopyOnWriteArrayList.spliterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html#spliterator()).

Ví dụ:

~~~java
CopyOnWriteArrayList<String> values =
        new CopyOnWriteArrayList<>(List.of("A", "B"));

Spliterator<String> snapshot = values.spliterator();
values.set(0, "X");
values.add("C");
~~~

Khi duyệt `snapshot`, ta vẫn nhận `[A, B]`. Danh sách hiện tại là `[X, B, C]`. Collection không bị khóa vĩnh viễn hoặc biến thành đối tượng cấm sửa; bộ duyệt giữ một nguồn ảnh chụp không bị các lần cập nhật ấy thay thế.

Đây là điểm dễ nhầm nếu chỉ đọc tên lớp hoặc chỉ nhìn chữ IMMUTABLE rồi nói “collection này không thể gọi add”.

### 7.2. Đối tượng phần tử vẫn có thể sửa được

Nếu ảnh chụp chứa một `StringBuilder`, tham chiếu trong ảnh chụp không tự được nhân bản:

~~~java
StringBuilder item = new StringBuilder("seed");
CopyOnWriteArrayList<StringBuilder> values = new CopyOnWriteArrayList<>();
values.add(item);

Spliterator<StringBuilder> snapshot = values.spliterator();
item.append("!");
~~~

Nguồn ảnh chụp vẫn giữ cùng tham chiếu, nên duyệt sẽ thấy đối tượng biểu diễn `seed!`. Không có phần tử mới được thêm hoặc tham chiếu phần tử bị thay trong ảnh chụp; chính trạng thái bên trong đối tượng đã đổi.

Muốn dữ liệu bất biến ở cấp nội dung nghiệp vụ, cần kiểu phần tử bất biến hoặc quy tắc sao chép đối tượng riêng. IMMUTABLE không tự cung cấp bản sao sâu.

### 7.3. Khung nhìn chỉ đọc không tự thành nguồn IMMUTABLE

`Collections.unmodifiableList(backing)` cấm sửa qua khung nhìn ấy, nhưng bên giữ `backing` vẫn có thể sửa nguồn. Với khung nhìn trên ArrayList trong chương trình, Spliterator không báo IMMUTABLE, và thêm `B` qua backing được nhìn thấy khi đọc khung nhìn.

Không được gộp ba khái niệm:

| Khái niệm | Điều đang nói tới |
| --- | --- |
| Không cho sửa qua một tham chiếu khung nhìn | Quyền thao tác qua đường truy cập đó |
| IMMUTABLE của Spliterator | Nguồn mà bộ duyệt bao quát không bị thay phần tử trong quá trình liên quan |
| Đối tượng phần tử bất biến | Trạng thái bên trong đối tượng không thay đổi |

Một danh sách kích thước cố định nhưng cho phép `set` cũng không đủ để tự suy ra IMMUTABLE. Chỉ không tăng/giảm số vị trí chưa giải quyết việc thay tham chiếu tại những vị trí ấy.

## 8. CONCURRENT: nguồn chấp nhận cập nhật đồng thời theo chính sách của nó

`CONCURRENT` nói rằng nguồn cho phép những thay đổi đồng thời được hỗ trợ diễn ra an toàn mà không cần bên ngoài tự khóa toàn bộ nguồn cho lượt duyệt. Spliterator phải đi cùng một chính sách mô tả ảnh hưởng của các thay đổi ấy.

Ví dụ quan trọng là các khung nhìn của ConcurrentHashMap:

| Khung nhìn | Những đặc tính được tài liệu nêu |
| --- | --- |
| Khóa | CONCURRENT, DISTINCT, NONNULL |
| Giá trị | CONCURRENT, NONNULL |
| Entry | CONCURRENT, DISTINCT, NONNULL |

Khóa không trùng, nhưng nhiều khóa có thể cùng giá trị nên khung nhìn giá trị không tự có DISTINCT. Các bộ duyệt của những khung nhìn này có tính nhất quán yếu. [Java SE 17 — ConcurrentHashMap views](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#keySet()).

### 8.1. Không phải ảnh chụp tại một thời điểm duy nhất

Một lượt duyệt trên nguồn đồng thời có thể nhìn thấy trạng thái qua thời gian duyệt theo chính sách của cấu trúc. Nó không nhất thiết đóng băng mọi khóa và giá trị tại cùng một thời điểm.

Do đó, không dùng CONCURRENT để cam kết một báo cáo tổng hợp giữa nhiều khóa là ảnh chụp nguyên tử. Nếu nghiệp vụ đòi hỏi tính nhất quán toàn cục, cần thiết kế cơ chế riêng.

### 8.2. Không có nghĩa nhiều luồng cùng dùng một đối tượng Spliterator

Đặc tính này thuộc **nguồn dữ liệu**, không làm đối tượng bộ duyệt tự trở thành an toàn cho nhiều luồng cùng gọi `tryAdvance` hoặc `trySplit`.

Mô hình xử lý song song thường giao mỗi phần chia cho một bên xử lý tại một thời điểm. Nơi nhận kết quả và các hành động cũng phải có thiết kế phù hợp; dùng một ArrayList đầu ra chung từ nhiều luồng vẫn không được làm an toàn chỉ nhờ nguồn báo CONCURRENT.

### 8.3. Không bổ sung các quyền sửa mà API khác không hỗ trợ

Một khung nhìn khóa có nguồn đồng thời không vì vậy mà hỗ trợ mọi phép `add`. Quyền thêm/xóa qua một API cụ thể và khả năng nguồn chịu thay đổi đồng thời là hai hợp đồng khác nhau.

Đặc tính giúp quản lý sự can thiệp lên nguồn trong lúc duyệt, không thay thế toàn bộ tài liệu của Collection hoặc Map đang cung cấp khung nhìn.

## 9. Các quan hệ và tổ hợp cần đọc đúng

Các quan hệ quan trọng:

~~~text
SORTED  => ORDERED
SUBSIZED => SIZED

ORDERED không suy ra SORTED
SORTED không suy ra DISTINCT
DISTINCT không suy ra NONNULL
SIZED không suy ra IMMUTABLE
CONCURRENT không suy ra đối tượng Spliterator an toàn đa luồng
IMMUTABLE không suy ra phần tử bất biến sâu
~~~

Ở Spliterator cấp gốc, CONCURRENT không nên được ghép đồng thời với SIZED vì kích thước có thể đổi khi nguồn được cập nhật. Nó cũng không nên được ghép với IMMUTABLE như hai lời hứa trái ngược về cùng nguồn. Những phần con có thể có bảo đảm riêng nếu phạm vi đã được cố định; không suy rộng tổ hợp của cấp gốc thành một quy tắc đơn giản cho mọi phần con.

Nếu bộ duyệt không báo IMMUTABLE hoặc CONCURRENT, cần đọc chính sách nguồn về thời điểm liên kết dữ liệu và phát hiện sửa đổi. “Fail-fast” thường chỉ là phát hiện theo khả năng tốt nhất, không phải cơ chế đồng bộ và không phải bằng chứng rằng chưa ném ngoại lệ thì không có lỗi truy cập.

### Không có một “bit tốt nhất” cho mọi việc

Một nguồn IMMUTABLE giữ ổn định tập phần tử của lượt duyệt nhưng có thể phải trả chi phí tạo ảnh chụp. Một nguồn CONCURRENT cho phép tiếp tục cập nhật nhưng không tự cung cấp ảnh chụp toàn cục. Nguồn ORDERED hữu ích khi kết quả cần đúng thứ tự nhưng có thể làm một số xử lý song song phải giữ thêm ràng buộc.

Chọn đặc tính để hiểu đúng hợp đồng nguồn, không dùng chúng như một thang điểm chất lượng chung của collection.

## 10. Đặc tính của pipeline có thể khác đặc tính của nguồn ban đầu

Khi Stream tiếp nhận Spliterator, các phép xử lý trung gian có thể giữ hoặc làm mất những cam kết.

### filter có thể làm mất việc biết trước kích thước

Một ArrayList ba phần tử biết kích thước đầu vào. Sau `filter(n -> n > 0)`, với dữ liệu cụ thể `[3, 1, 3]` thì cả ba đều đạt. Tuy nhiên, một phép lọc tổng quát cần kiểm tra phần tử mới biết bao nhiêu phần tử đi tiếp.

Trong chương trình, Spliterator của pipeline lọc chỉ báo ORDERED, không tiếp tục báo SIZED và SUBSIZED của ArrayList nguồn. Đó không phải lỗi đếm thiếu; pipeline không hứa biết số đầu ra trước khi thực hiện điều kiện.

### map có thể làm mất SORTED và DISTINCT

TreeSet nguồn giữ các số khác nhau và có thứ tự sắp xếp. Sau `map(n -> 0)`, nhiều phần tử trở thành cùng giá trị. Một phép ánh xạ tùy ý cũng có thể đảo lộn quan hệ so sánh.

Trong ca thử, Spliterator của pipeline ánh xạ vẫn báo ORDERED và SIZED nhưng không báo SORTED hoặc DISTINCT. Thứ tự gặp được giữ và mỗi đầu vào sinh một đầu ra, còn những bảo đảm về giá trị đã mất.

Những quan sát này giúp phân biệt hợp đồng đầu vào và hợp đồng đầu ra. Khi viết thuật toán tiếp theo, xem phần dữ liệu hoặc bộ duyệt đang thật sự được chuyển tới nó, không chỉ tra đặc tính của collection ở đầu chương trình.

## 11. Vì sao khai báo sai có thể gây kết quả sai?

Các thuật toán thư viện có thể tin vào đặc tính đã công bố. Nếu một nguồn nói sai rằng không có phần tử trùng, bước loại trùng có thể được xem là không cần thiết. Nếu nguồn nói sai kích thước, thuật toán cấp phát hoặc ghép kết quả có thể dựa trên một giả định không đúng.

Các bit vì thế là **nghĩa vụ của người cài đặt Spliterator**, không phải gợi ý mà thư viện buộc phải tự duyệt kiểm tra lại từ đầu.

Nếu tạo nguồn tùy biến:

- Chỉ báo ORDERED khi có thứ tự gặp thực sự và cách chia giữ đúng ràng buộc tiền tố.
- Chỉ báo SORTED khi thứ tự và `getComparator` cùng tuân thủ hợp đồng.
- Chỉ báo DISTINCT khi bảo đảm theo equals cho phần tử được gặp.
- Chỉ báo SIZED, SUBSIZED khi phép ước lượng và mọi phần chia liên quan đáp ứng điều kiện.
- Chỉ báo IMMUTABLE hoặc CONCURRENT theo đúng mô hình nguồn, không dựa vào việc “chưa thấy lỗi”.

`StreamSupport` là cầu nối cho nguồn cấp thấp; dạng nhận supplier yêu cầu tập đặc tính khai báo khớp với bộ duyệt do supplier tạo. Thư viện không thể tự sửa mọi cam kết sai của nguồn. [Java SE 17 — StreamSupport](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/StreamSupport.html).

Khi chỉ dùng collection chuẩn, thường không cần tự xây dựng Spliterator. Nhưng hiểu nghĩa của các bit vẫn cần thiết để giải thích vì sao một pipeline giữ thứ tự, mất số lượng biết trước hoặc có chính sách duyệt khác khi nguồn thay đổi.

## 12. Chương trình minh chứng đầy đủ

Lưu mã sau thành **SpliteratorCharacteristicsDemo.java**. Chương trình tương thích Java 17 và dùng thư viện chuẩn.

Hàm `flags` chỉ in tên những bit được báo theo một thứ tự cố định để dễ đọc; thứ tự tên trong chuỗi không phải thứ tự phần tử nguồn. Hàm `drain` tiêu thụ bộ duyệt, nên mỗi ví dụ cần một Spliterator phù hợp cho lượt duyệt của nó.

Chương trình không in thứ tự phần tử của HashSet hoặc ConcurrentHashMap để tránh biến một thứ tự không được cam kết thành stdout giả định. Những ví dụ về đa luồng chỉ kiểm tra đặc tính đã được tài liệu công bố, không dùng một lần chạy tuần tự để chứng minh an toàn đồng thời.

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Spliterator;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class SpliteratorCharacteristicsDemo {
    private static String flags(Spliterator<?> source) {
        int[] bits = {
            Spliterator.ORDERED, Spliterator.SORTED, Spliterator.DISTINCT,
            Spliterator.SIZED, Spliterator.IMMUTABLE, Spliterator.CONCURRENT,
            Spliterator.SUBSIZED, Spliterator.NONNULL
        };
        String[] names = {
            "ORDERED", "SORTED", "DISTINCT", "SIZED",
            "IMMUTABLE", "CONCURRENT", "SUBSIZED", "NONNULL"
        };
        List<String> result = new ArrayList<>();
        for (int i = 0; i < bits.length; i++) {
            if (source.hasCharacteristics(bits[i])) {
                result.add(names[i]);
            }
        }
        return result.toString();
    }

    private static <T> List<T> drain(Spliterator<T> source) {
        List<T> result = new ArrayList<>();
        source.forEachRemaining(result::add);
        return result;
    }

    public static void main(String[] args) {
        List<Integer> numbers = new ArrayList<>(List.of(3, 1, 3));
        Spliterator<Integer> listSource = numbers.spliterator();
        System.out.println("array-list=" + flags(listSource));
        System.out.println("array-values=" + drain(listSource));

        System.out.println("hash-set="
                + flags(new HashSet<>(numbers).spliterator()));
        LinkedHashSet<Integer> linked = new LinkedHashSet<>(numbers);
        System.out.println("linked-set=" + flags(linked.spliterator()));
        System.out.println("linked-values=" + drain(linked.spliterator()));

        TreeSet<Integer> natural = new TreeSet<>(numbers);
        Spliterator<Integer> naturalSource = natural.spliterator();
        System.out.println("tree-set=" + flags(naturalSource));
        System.out.println("natural-comparator-null=" + (naturalSource.getComparator() == null));
        System.out.println("tree-values=" + drain(naturalSource));

        TreeSet<Integer> reverse = new TreeSet<>(Comparator.reverseOrder());
        reverse.addAll(numbers);
        Spliterator<Integer> reverseSource = reverse.spliterator();
        System.out.println("reverse-comparator-1-3=" + reverseSource.getComparator().compare(1, 3));
        System.out.println("reverse-values=" + drain(reverseSource));
        System.out.println("natural-sort-from-reverse=" + reverse.stream().sorted().toList());

        Spliterator<Integer> sortedLooking =
                new ArrayList<>(List.of(1, 2, 3)).spliterator();
        System.out.println("sorted-looking-sorted="
                + sortedLooking.hasCharacteristics(Spliterator.SORTED));
        System.out.println("sorted-looking-distinct="
                + sortedLooking.hasCharacteristics(Spliterator.DISTINCT));
        System.out.println("ordered-and-sorted="
                + sortedLooking.hasCharacteristics(Spliterator.ORDERED | Spliterator.SORTED));
        try {
            sortedLooking.getComparator();
            throw new AssertionError("Expected missing SORTED");
        } catch (IllegalStateException expected) {
            System.out.println("unsorted-getComparator -> IllegalStateException");
        }

        Spliterator<Integer> sized = numbers.spliterator();
        System.out.println("list-exact-before=" + sized.getExactSizeIfKnown());
        Spliterator<Integer> split = sized.trySplit();
        if (split == null) {
            throw new AssertionError("Expected ArrayList split");
        }
        System.out.println("split-sized="
                + split.hasCharacteristics(Spliterator.SIZED | Spliterator.SUBSIZED));
        System.out.println("split-total=" + (split.estimateSize() + sized.estimateSize()));

        CopyOnWriteArrayList<String> copyOnWrite =
                new CopyOnWriteArrayList<>(List.of("A", "B"));
        Spliterator<String> snapshot = copyOnWrite.spliterator();
        System.out.println("copy-on-write=" + flags(snapshot));
        copyOnWrite.set(0, "X");
        copyOnWrite.add("C");
        System.out.println("snapshot-values=" + drain(snapshot));
        System.out.println("current-list=" + copyOnWrite);

        StringBuilder item = new StringBuilder("seed");
        CopyOnWriteArrayList<StringBuilder> objects = new CopyOnWriteArrayList<>();
        objects.add(item);
        Spliterator<StringBuilder> objectSnapshot = objects.spliterator();
        item.append("!");
        System.out.println("immutable-flag="
                + objectSnapshot.hasCharacteristics(Spliterator.IMMUTABLE));
        System.out.println("mutable-element=" + drain(objectSnapshot));

        List<String> backing = new ArrayList<>(List.of("A"));
        List<String> readOnly = Collections.unmodifiableList(backing);
        System.out.println("read-only-immutable="
                + readOnly.spliterator().hasCharacteristics(Spliterator.IMMUTABLE));
        backing.add("B");
        System.out.println("read-only-values=" + readOnly);

        ConcurrentHashMap<String, Integer> concurrent = new ConcurrentHashMap<>();
        concurrent.put("A", 1);
        concurrent.put("B", 1);
        Spliterator<String> concurrentKeys = concurrent.keySet().spliterator();
        System.out.println("concurrent-keys=" + flags(concurrentKeys));
        System.out.println("concurrent-values=" + flags(concurrent.values().spliterator()));
        System.out.println("concurrent-exact=" + concurrentKeys.getExactSizeIfKnown());

        System.out.println("filtered-list="
                + flags(numbers.stream().filter(n -> n > 0).spliterator()));
        System.out.println("mapped-tree="
                + flags(natural.stream().map(n -> 0).spliterator()));
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 SpliteratorCharacteristicsDemo.java
java SpliteratorCharacteristicsDemo
~~~

Stdout chính xác:

~~~text
array-list=[ORDERED, SIZED, SUBSIZED]
array-values=[3, 1, 3]
hash-set=[DISTINCT, SIZED]
linked-set=[ORDERED, DISTINCT, SIZED, SUBSIZED]
linked-values=[3, 1]
tree-set=[ORDERED, SORTED, DISTINCT, SIZED]
natural-comparator-null=true
tree-values=[1, 3]
reverse-comparator-1-3=1
reverse-values=[3, 1]
natural-sort-from-reverse=[1, 3]
sorted-looking-sorted=false
sorted-looking-distinct=false
ordered-and-sorted=false
unsorted-getComparator -> IllegalStateException
list-exact-before=3
split-sized=true
split-total=3
copy-on-write=[ORDERED, SIZED, IMMUTABLE, SUBSIZED]
snapshot-values=[A, B]
current-list=[X, B, C]
immutable-flag=true
mutable-element=[seed!]
read-only-immutable=false
read-only-values=[A, B]
concurrent-keys=[DISTINCT, CONCURRENT, NONNULL]
concurrent-values=[CONCURRENT, NONNULL]
concurrent-exact=-1
filtered-list=[ORDERED]
mapped-tree=[ORDERED, SIZED]
~~~

Mã đã biên dịch bằng `--release 17` và chạy trên JDK 24.0.2. Đây là mức API/ngôn ngữ khi biên dịch và phiên bản JVM đã chạy; không phải tuyên bố đã chạy bằng JVM 17.

Đối chiếu các nhóm:

- `array-list` và `array-values` cho thấy ORDERED đi cùng dữ liệu chưa tăng dần và còn trùng.
- `hash-set` và `linked-set` tách tính không trùng khỏi thứ tự gặp.
- `tree-set`, `natural-comparator-null` và `reverse-values` phân biệt hai quy tắc sắp xếp.
- `sorted-looking` cho thấy dữ liệu đang có tính chất chưa đủ để khẳng định bit được báo.
- `ordered-and-sorted` kiểm tra đồng thời hai đặc tính.
- Nhóm `list-exact`, `split-sized` và `split-total` minh họa kích thước với nguồn ArrayList chưa duyệt.
- Nhóm `copy-on-write` giữ ảnh chụp cũ dù collection hiện tại thay đổi.
- `mutable-element` cho thấy ảnh chụp cấu trúc không làm đối tượng phần tử bất biến sâu.
- `read-only` phân biệt khung nhìn không cho sửa với nguồn ổn định.
- Hai dòng `concurrent` về khung nhìn phân biệt khóa không trùng với các giá trị có thể trùng.
- Hai dòng cuối minh họa sự thay đổi cam kết sau filter và map.

## 13. Thực hành có đáp án

### Bài 1 — ORDERED có bắt buộc tăng dần không?

ArrayList chứa `[9, 2, 9]` có Spliterator báo ORDERED. Điều đó có sai vì dãy không tăng dần không?

**Đáp án:** Không. ORDERED nói về thứ tự gặp theo chỉ số của List. Nó không hứa tăng dần hoặc không trùng. Muốn biết có thứ tự so sánh được công bố hay không phải kiểm tra SORTED và quy tắc tương ứng.

### Bài 2 — getComparator trả null

Một TreeSet số nguyên dùng thứ tự tự nhiên. Spliterator của nó báo SORTED nhưng `getComparator()` trả null. Có phải thiếu comparator gây lỗi không?

**Đáp án:** Không. Với nguồn SORTED, null biểu thị thứ tự tự nhiên. Nếu không báo SORTED thì gọi getComparator gây IllegalStateException; cần phân biệt hai trường hợp thay vì coi mọi null đều là lỗi.

### Bài 3 — Một bit hay cả hai bit?

`s.hasCharacteristics(ORDERED | SORTED)` có trả true khi `s` chỉ báo ORDERED không? Nếu viết `(flags & mask) != 0` thì đang kiểm tra điều gì?

**Đáp án:** hasCharacteristics yêu cầu mọi bit trong mask nên trả false. Biểu thức khác chỉ hỏi có ít nhất một bit giao nhau, không đủ để khẳng định nguồn đã được sắp xếp.

### Bài 4 — DISTINCT và null

Một HashSet chứa `null` và `"A"` có thể báo DISTINCT không? Có thể dựa vào đó để bỏ kiểm tra null ở hành động xử lý không?

**Đáp án:** Có thể báo DISTINCT vì không có hai phần tử bằng nhau. Nhưng DISTINCT không bảo đảm NONNULL; hành động vẫn phải xử lý null nếu không có hợp đồng khác loại trừ nó.

### Bài 5 — Có SIZED thì mọi phần chia đều chính xác?

Một Spliterator báo SIZED. Có được tự suy ra nó báo SUBSIZED và mọi phần con sau trySplit giữ kích thước chính xác như ArrayList không?

**Đáp án:** Không. SIZED có điều kiện về thời điểm và nguồn ổn định; biết kích thước toàn bộ không tự biết chính xác mọi phần chia. SUBSIZED mới bổ sung các cam kết liên quan tới các phần con theo hợp đồng.

### Bài 6 — Collection sửa được nhưng Spliterator IMMUTABLE

Vì sao CopyOnWriteArrayList vẫn gọi add được, trong khi Spliterator đã tạo từ nó báo IMMUTABLE?

**Đáp án:** Bộ duyệt giữ một ảnh chụp nguồn ổn định tại lúc tạo. Các cập nhật tạo trạng thái mới của collection, không thay các vị trí trong ảnh chụp mà bộ duyệt đang giữ. Điều này không biến mọi đối tượng phần tử thành bất biến sâu.

### Bài 7 — CONCURRENT có cho dùng chung một bộ duyệt không?

Nguồn báo CONCURRENT. Hai luồng có thể cùng gọi tryAdvance trên cùng một đối tượng Spliterator và cùng thêm vào một ArrayList đầu ra mà không cần thiết kế gì thêm không?

**Đáp án:** Không. CONCURRENT là cam kết về khả năng nguồn chịu cập nhật đồng thời, không phải bảo đảm an toàn cho đối tượng bộ duyệt hoặc nơi thu kết quả. Cần phân chia quyền xử lý và phối hợp kết quả phù hợp.

### Bài 8 — Giữ nguyên bit sau map

TreeSet nguồn báo SORTED và DISTINCT. Sau `map(n -> 0)`, nếu một bộ duyệt tùy biến cứ giữ nguyên hai bit đó thì có vấn đề gì?

**Đáp án:** Kết quả có thể chứa nhiều số 0 nên DISTINCT không còn đúng; một phép map tùy ý cũng không bảo toàn quy tắc sắp xếp. Khai báo sai có thể khiến thuật toán thư viện áp dụng tối ưu không hợp lệ. Phải công bố đặc tính của đầu ra thực sự.

## 14. Cách sử dụng các đặc tính khi đọc và viết mã

Trước tiên xác định bộ duyệt đang bao quát nguồn nào, ở thời điểm nào. Sau đó dùng hasCharacteristics để hỏi đúng cam kết mà thuật toán cần: thứ tự, quy tắc sắp xếp, tính không trùng, số lượng hay chính sách thay đổi nguồn.

Không suy ra một bit từ tên collection hoặc từ dữ liệu tình cờ đang có. Khi đi qua phép lọc, ánh xạ hoặc chia nguồn, đánh giá lại phạm vi cam kết. Khi có nhiều luồng, tách tính an toàn của nguồn khỏi quyền sở hữu bộ duyệt và trạng thái dùng chung trong hành động.

Hiểu sáu đặc tính theo những trục riêng giúp giải thích đúng hành vi của Stream và Spliterator: có thứ tự chưa chắc đã sắp xếp; biết kích thước chưa chắc bất biến; ảnh chụp cấu trúc chưa chắc sao chép sâu; nguồn đồng thời chưa chắc là ảnh chụp nguyên tử.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="streams-spliterators-006">Collectors.toList, toSet và toCollection</a>

<details>
<summary>Xem chi tiết</summary>

# Collectors.toList, toSet và toCollection

## 1. Ba bộ thu thập giải quyết vấn đề gì?

Một luồng `Stream` mô tả cách đọc và xử lý các phần tử. Sau khi lọc hoặc biến đổi, ứng dụng thường cần giữ kết quả để tiếp tục sử dụng: một dãy đầy đủ, một tập giá trị không trùng hoặc một loại collection có khả năng cụ thể.

Ba phương thức `Collectors.toList()`, `Collectors.toSet()` và `Collectors.toCollection(...)` tạo **bộ thu thập** (collector) cho công việc đó. Chúng chưa tạo ra danh sách dữ liệu ngay tại thời điểm được gọi; phương thức kết thúc `Stream.collect(...)` sử dụng bộ thu thập để tích lũy kết quả.

Cần phân biệt ba vai trò:

| Thành phần | Vai trò |
| --- | --- |
| `Stream<T>` | Luồng các phần tử và chuỗi thao tác xử lý |
| `Collector<T,A,R>` | Mô tả cách tích lũy phần tử để tạo kết quả |
| `Collectors` | Lớp tiện ích cung cấp các bộ thu thập thông dụng |

Trong `Collector<T,A,R>`:

- `T` là kiểu phần tử được nhận.
- `A` là kiểu của vùng tích lũy có thể thay đổi trong quá trình thu thập.
- `R` là kiểu kết quả được trả về.
- Dấu `?` trong các chữ ký tiện ích che giấu kiểu tích lũy nội bộ mà bên gọi không cần phụ thuộc vào.

Một luồng công việc điển hình:

```text
Nguồn dữ liệu
  -> Stream
  -> filter / map / các bước xử lý cần thiết
  -> collect(bộ thu thập)
  -> collection kết quả
```

Ví dụ, các lần xuất hiện lặp trong một dãy đơn hàng có thể đều phải được giữ, còn dãy nhãn dùng để lọc giao diện có thể chỉ cần mỗi nhãn một lần. Chọn bộ thu thập là chọn ý nghĩa của kết quả, không chỉ chọn cách viết ngắn hơn.

[API `Collector`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collector.html) mô tả quá trình tích lũy; [`Stream.collect`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#collect(java.util.stream.Collector)) thực hiện phép thu thập đó.

### 1.1. Vì sao gọi toList riêng chưa có một List?

Đoạn sau minh họa các vai trò, không phải chương trình chạy độc lập:

```java
Collector<String, ?, List<String>> plan = Collectors.toList();
List<String> result = Stream.of("sun", "rain").collect(plan);
```

`plan` là kế hoạch thu thập. `result` mới là danh sách chứa dữ liệu sau khi thao tác kết thúc chạy.

Hai cách viết dưới đây không biên dịch; chúng là các ví dụ lỗi cần phân biệt:

```java
List<String> wrong = Collectors.toList();

Stream.of("x").collect(
        Collectors.toCollection(new ArrayList<String>()));
```

Dòng đầu nhầm bộ thu thập với kết quả. Dòng sau truyền một đối tượng collection, trong khi `toCollection` cần một hàm tạo có kiểu `Supplier`. Cách đúng thường dùng tham chiếu hàm tạo như `ArrayList::new` hoặc lambda tạo đối tượng mới.

## 2. Đọc hợp đồng của ba phương thức

Các chữ ký API rút gọn:

```java
static <T> Collector<T, ?, List<T>> toList();
static <T> Collector<T, ?, Set<T>> toSet();
static <T, C extends Collection<T>>
        Collector<T, ?, C> toCollection(Supplier<C> collectionFactory);
```

| Phương thức | Kết quả | Phần tử trùng | Thứ tự | Quyền kiểm soát lớp đích |
| --- | --- | --- | --- | --- |
| `Collectors.toList()` | `List<T>` | Giữ các lần xuất hiện | Giữ thứ tự gặp của luồng nếu có | Không hứa lớp triển khai cụ thể |
| `Collectors.toSet()` | `Set<T>` | Gộp theo hợp đồng set | Không cam kết giữ thứ tự gặp | Không hứa lớp triển khai cụ thể |
| `Collectors.toCollection(factory)` | `C` do hàm tạo chọn | Theo collection được tạo | Tích lũy theo thứ tự gặp; cách duyệt còn phụ thuộc loại đích | Chọn tường minh qua hàm tạo |

Hai bộ thu thập tổng quát `toList` và `toSet` không cam kết lớp triển khai, khả năng sửa, khả năng tuần tự hóa hoặc tính an toàn khi nhiều luồng dùng kết quả.

Do đó, không nên ép kết quả `toList` thành `ArrayList`, cũng không nên coi `toSet` là lời hứa sẽ trả `HashSet`. Một cách triển khai có thể sử dụng những lớp đó mà không biến tên lớp đang quan sát được thành hợp đồng công khai.

Nếu cần chắc chắn một `ArrayList` có thể thêm/xóa phần tử, yêu cầu nó qua `toCollection(ArrayList::new)`. Nếu cần kết quả chỉ đọc, phải chọn một API hoặc bước hoàn tất có cam kết tương ứng; không suy ra cả quyền sửa lẫn việc cấm sửa chỉ từ tên `toList`.

[Đặc tả `Collectors`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html) nêu rõ giới hạn của hai bộ thu thập tổng quát và vai trò của `toCollection`.

### 2.1. Thứ tự gặp không phải thứ tự các luồng con chạy xong

**Thứ tự gặp** (encounter order) là thứ tự mà nguồn/chuỗi thao tác cung cấp cho các phần tử. List thường cung cấp thứ tự dãy; một nguồn không có cam kết thứ tự không tự trở thành có thứ tự chỉ vì kết quả được đưa vào list.

Một phép thu thập bảo toàn thứ tự gặp có thể chạy song song mà vẫn trả kết quả theo thứ tự ấy. Điều đó không yêu cầu từng luồng xử lý phải kết thúc theo thứ tự từ phần tử đầu tới cuối.

`toSet` là bộ thu thập có đặc tính `UNORDERED`: nó không cam kết giữ thứ tự gặp. Điều này không có nghĩa nó phải xáo ngẫu nhiên phần tử hoặc phải tạo một thứ tự khác ở mỗi lần chạy. Một đầu ra tình cờ có vẻ đã sắp xếp không bổ sung cam kết thứ tự cho API.

### 2.2. sorted rồi toSet không bảo đảm duyệt theo thứ tự đã sắp xếp

Nếu ứng dụng sắp xếp luồng rồi kết thúc bằng `toSet`, hợp đồng của đích vẫn không hứa thứ tự duyệt tương ứng.

Tùy yêu cầu, có thể chọn:

- `sorted(...).collect(Collectors.toList())` để giữ một dãy đã sắp xếp, gồm cả những lần xuất hiện lặp.
- `collect(Collectors.toCollection(TreeSet::new))` để có một tập hợp duy trì thứ tự tự nhiên và gộp các phần tử theo quy tắc so sánh.
- `collect(Collectors.toCollection(LinkedHashSet::new))` để gộp trùng nhưng giữ thứ tự chèn theo nguồn có thứ tự.

Đây là ba ý định khác nhau. Dùng `TreeSet` chỉ để “làm list có thứ tự” có thể làm mất các lần xuất hiện mà nghiệp vụ còn cần.

## 3. Chương trình 1: Dãy đầy đủ, tập hợp và đích tường minh

Chương trình này cố ý không in một thứ tự cụ thể cho kết quả `toSet`. Nó kiểm tra nội dung tập hợp bằng `Set.equals` và kiểm tra số phần tử, vì đó mới là những điều cần quan sát.

Tệp `BasicCollectionCollectorsDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BasicCollectionCollectorsDemo {
    public static void main(String[] args) {
        List<String> drinks =
                new ArrayList<>(List.of("tea", "coffee", "tea", "juice"));

        List<String> all = drinks.stream().collect(Collectors.toList());
        Set<String> unique = drinks.stream().collect(Collectors.toSet());
        ArrayList<String> editable = drinks.stream()
                .collect(Collectors.toCollection(ArrayList::new));
        LinkedHashSet<String> firstSeen = drinks.stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        TreeSet<String> sortedUnique = drinks.stream()
                .collect(Collectors.toCollection(TreeSet::new));

        System.out.println("all=" + all);
        System.out.println("uniqueSize=" + unique.size());
        System.out.println("uniqueValues=" +
                unique.equals(Set.of("tea", "coffee", "juice")));
        System.out.println("firstSeen=" + firstSeen);
        System.out.println("sortedUnique=" + sortedUnique);

        editable.add("water");
        drinks.add("cocoa");
        System.out.println("editable=" + editable);
        System.out.println("sourceNow=" + drinks);
        System.out.println("allStill=" + all);

        System.out.println("emptyList=" +
                Stream.<String>empty().collect(Collectors.toList()).isEmpty());
        System.out.println("emptySet=" +
                Stream.<String>empty().collect(Collectors.toSet()).isEmpty());

        List<String> nullable = Stream.of("milk", null, "milk")
                .collect(Collectors.toList());
        Set<String> nullableSet = Stream.of("milk", null, "milk")
                .collect(Collectors.toSet());
        System.out.println("nullableList=" + nullable);
        System.out.println("nullableSetSize=" + nullableSet.size());
        System.out.println("nullableSetContainsNull=" + nullableSet.contains(null));
    }
}
```

Biên dịch và chạy tại thư mục chứa tệp bằng JDK hỗ trợ Java 17 trở lên:

```shell
javac --release 17 -encoding UTF-8 BasicCollectionCollectorsDemo.java
java BasicCollectionCollectorsDemo
```

Đầu ra:

```text
all=[tea, coffee, tea, juice]
uniqueSize=3
uniqueValues=true
firstSeen=[tea, coffee, juice]
sortedUnique=[coffee, juice, tea]
editable=[tea, coffee, tea, juice, water]
sourceNow=[tea, coffee, tea, juice, cocoa]
allStill=[tea, coffee, tea, juice]
emptyList=true
emptySet=true
nullableList=[milk, null, milk]
nullableSetSize=2
nullableSetContainsNull=true
```

### 3.1. Các kết quả khác nhau vì hợp đồng khác nhau

`all` giữ hai lần `"tea"`. `uniqueSize=3` cho thấy tập hợp đã gộp trùng; `uniqueValues=true` xác nhận đủ ba giá trị cần có mà không phụ thuộc thứ tự duyệt.

`firstSeen` giữ thứ tự xuất hiện đầu tiên của mỗi giá trị trong luồng tuần tự này nhờ `LinkedHashSet`. `sortedUnique` lại duyệt theo thứ tự tự nhiên của `TreeSet`.

`editable` có lớp đích được yêu cầu tường minh là `ArrayList`, nên lời gọi `add("water")` thuộc hợp đồng của lớp đó. Chương trình không dùng một lời gọi sửa trên kết quả `Collectors.toList()` để biến hành vi của một bản JDK thành điều kiện bắt buộc của API.

Khi thêm `"cocoa"` vào nguồn sau thu thập, `all` vẫn giữ dãy đã tích lũy. Kết quả không phải khung nhìn sống của list nguồn. Các đối tượng phần tử bên trong vẫn có thể được chia sẻ; chương trình thứ ba sẽ kiểm tra riêng điều đó.

### 3.2. Luồng rỗng và phần tử null

Luồng rỗng vẫn được thu thập thành kết quả rỗng; không cần tự rẽ nhánh trả `null` để biểu diễn “không có phần tử”.

Hai kết quả cuối có `null` chứng minh hành vi của cách triển khai đang chạy ví dụ. Trong mã nguồn OpenJDK 17, `toList` dùng `ArrayList` và `toSet` dùng `HashSet` để tích lũy. Đây là thông tin về cách triển khai, không phải lời hứa rằng kết quả luôn có chính các lớp ấy.

Nếu chính sách `null` là phần bắt buộc của hợp đồng ứng dụng, hãy xác thực rõ ràng hoặc chọn một loại đích có chính sách đã biết qua `toCollection`. Không dùng một bộ thu thập tổng quát như thể nó tự thực hiện quy tắc nghiệp vụ “loại mọi phần tử null”.

[Mã nguồn `Collectors` của OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/stream/Collectors.java) hữu ích để giải thích các quan sát; mã ứng dụng vẫn nên dựa trên hợp đồng công khai hoặc đích đã chọn tường minh.

## 4. toCollection: cung cấp một hàm tạo đúng hợp đồng

### 4.1. Hàm tạo phải tạo collection mới và rỗng

`Supplier<C>` là giao diện hàm không nhận đối số và trả về một `C` khi gọi `get()`. Riêng hợp đồng `toCollection` yêu cầu hàm được cung cấp trả về **một collection mới, rỗng** để tích lũy dữ liệu.

Điểm phân biệt này quan trọng: giao diện [`Supplier`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/Supplier.html) nói chung không bắt buộc mọi lời gọi phải tạo một đối tượng khác. Yêu cầu mới/rỗng ở đây đến từ ngữ cảnh [`Collectors.toCollection`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#toCollection(java.util.function.Supplier)), không phải từ tên `Supplier` đứng riêng.

Có thể diễn đạt hai cách tạo tương đương:

```java
// Doan minh hoa ham tao, khong phai chuong trinh doc lap.
Collectors.toCollection(ArrayList::new);
Collectors.toCollection(() -> new ArrayList<String>());
```

Vì cần tích lũy phần tử và ghép các kết quả trung gian, collection được tạo phải hỗ trợ các thao tác cần thiết. Trả về một collection không cho thêm phần tử không đáp ứng vai trò đó, dù đối tượng ấy triển khai giao diện `Collection`.

Không nên trả về một biến collection dùng chung, kể cả khi nó đang rỗng ở lần gọi đầu tiên. Lần thu thập khác có thể nhận lại chính dữ liệu đã tích lũy; khi chạy song song, các vùng đáng lẽ độc lập còn có thể cùng trỏ tới một đối tượng.

### 4.2. Hàm tạo có thể mang cấu hình của loại đích

Khi cần một bộ so sánh, lambda có thể cung cấp cấu hình cho từng collection mới:

```java
// Doan minh hoa: order la Comparator<String> da duoc xac dinh.
Collectors.toCollection(() -> new TreeSet<String>(order));
```

Các collection riêng có thể dùng cùng một bộ so sánh ổn định. Điều cần tránh là dùng chung chính bộ chứa kết quả hoặc để quy tắc so sánh thay đổi trong lúc thu thập.

Loại đích còn quyết định ý nghĩa của thao tác thêm:

- `ArrayList` giữ các lần xuất hiện và thứ tự thêm.
- `LinkedHashSet` gộp trùng theo `equals/hashCode`, giữ thứ tự chèn.
- `TreeSet` xác định trùng theo kết quả so sánh bằng `0`, đồng thời duy trì thứ tự so sánh.
- Một loại khác có thể có chính sách `null` hoặc giới hạn riêng.

Vì vậy, `toCollection` tích lũy theo thứ tự gặp không có nghĩa iterator của mọi loại đích sẽ duyệt theo thứ tự ấy. Ví dụ, `TreeSet` thể hiện thứ tự của bộ so sánh khi duyệt. Nếu chọn một tập hợp không cam kết thứ tự duyệt, hàm thu thập không biến nó thành tập hợp có thứ tự.

[`LinkedHashSet`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html) và [`TreeSet`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html) có hợp đồng rất khác nhau. Với `TreeSet`, bộ so sánh cần nhất quán với `equals` để tuân thủ hợp đồng chung của `Set`; so sánh chỉ theo một thuộc tính không đủ phân biệt bản ghi có thể làm gộp dữ liệu ngoài ý muốn.

### 4.3. Có thể dùng lại collector, nhưng không dùng chung vùng tích lũy

Một collector được cấu hình đúng có thể được dùng cho các luồng độc lập. Mỗi lần thu thập sẽ nhận collection riêng từ hàm tạo.

Đây là hai mức khác nhau:

- Dùng lại mô tả cách thu thập.
- Dùng lại cùng một collection đang chứa kết quả của lần trước.

Mức đầu hữu ích để đóng gói chính sách. Mức sau vi phạm yêu cầu của hàm tạo trong `toCollection`.

Cũng cần phân biệt collector với `Stream`: một đối tượng stream chỉ được sử dụng cho một lần duyệt/chuỗi thao tác kết thúc theo hợp đồng. Muốn thu cùng nguồn thành list rồi thành set, hãy tạo những stream riêng từ nguồn, như chương trình thứ nhất đã làm.

### 4.4. Chương trình 2: Hàm tạo đúng, hàm tạo sai và cấu hình TreeSet

Phần `invalidPlan` cố ý minh họa cách dùng sai bằng dữ liệu cục bộ và các lần thu thập tuần tự. Nó không phải mẫu để dùng trong ứng dụng.

Tệp `CollectorFactoryContractDemo.java`:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectorFactoryContractDemo {
    public static void main(String[] args) {
        Collector<String, ?, LinkedHashSet<String>> plan =
                Collectors.toCollection(LinkedHashSet::new);
        LinkedHashSet<String> first = Stream.of("blue", "red", "blue").collect(plan);
        LinkedHashSet<String> second = Stream.of("green").collect(plan);
        System.out.println("first=" + first);
        System.out.println("second=" + second);
        System.out.println("sameResult=" + (first == second));

        Comparator<String> order =
                Comparator.nullsFirst(Comparator.reverseOrder());
        TreeSet<String> custom = Stream.of("tea", null, "juice", "coffee", "tea")
                .collect(Collectors.toCollection(() -> new TreeSet<>(order)));
        System.out.println("customOrder=" + custom);

        try {
            Stream.of("one", null)
                    .collect(Collectors.toCollection(TreeSet::new));
            throw new AssertionError("Natural-order tree accepted null");
        } catch (NullPointerException expected) {
            System.out.println("naturalNull=NullPointerException");
        }

        try {
            Stream.of("one")
                    .collect(Collectors.toCollection(() -> List.<String>of()));
            throw new AssertionError("Read-only accumulator accepted data");
        } catch (UnsupportedOperationException expected) {
            System.out.println("readOnlyFactory=UnsupportedOperationException");
        }

        ArrayList<String> shared = new ArrayList<>();
        Collector<String, ?, ArrayList<String>> invalidPlan =
                Collectors.toCollection(() -> shared);
        ArrayList<String> badFirst = Stream.of("first").collect(invalidPlan);
        ArrayList<String> badSecond = Stream.of("second").collect(invalidPlan);
        ArrayList<String> badEmpty = Stream.<String>empty().collect(invalidPlan);
        System.out.println("badSameResult=" + (badFirst == badSecond));
        System.out.println("badFirstNow=" + badFirst);
        System.out.println("badEmpty=" + badEmpty);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 CollectorFactoryContractDemo.java
java CollectorFactoryContractDemo
```

Đầu ra:

```text
first=[blue, red]
second=[green]
sameResult=false
customOrder=[null, tea, juice, coffee]
naturalNull=NullPointerException
readOnlyFactory=UnsupportedOperationException
badSameResult=true
badFirstNow=[first, second]
badEmpty=[first, second]
```

`sameResult=false` xác nhận collector đúng tạo kết quả riêng cho hai lần thu thập. Kết quả đầu không bị bổ sung phần tử của lần sau.

`customOrder` là set dùng bộ so sánh xử lý `null` trước, sau đó đảo thứ tự tự nhiên. Trong khi đó, cây dùng thứ tự tự nhiên không chấp nhận phần tử `null`. Chính sách này đến từ loại đích và bộ so sánh, không phải vì `toCollection` tự lọc dữ liệu.

`readOnlyFactory` thất bại khi cần thêm phần tử vào list được tạo bằng `List.of`. Một đầu vào rỗng có thể không làm lộ vấn đề thêm phần tử, nên thử thành công với dữ liệu rỗng chưa chứng minh hàm tạo phù hợp cho mọi đầu vào.

Ba dòng cuối cho thấy lỗi dùng chung collection rất rõ: kết quả cũ thay đổi sau lần thu thập mới, và thu một stream rỗng lại trả collection đang có dữ liệu. Không cần chờ một lỗi song song ngẫu nhiên để nhận ra hàm tạo đã sai hợp đồng.

## 5. Thu thập song song và bộ chứa kết quả

### 5.1. Vì sao có thể dùng ArrayList trong một phép collect song song?

Một bộ thu thập có thể mô tả bốn phần phối hợp:

| Thành phần | Vai trò |
| --- | --- |
| Hàm cấp bộ chứa, `supplier` | Tạo vùng tích lũy ban đầu |
| Hàm tích lũy, `accumulator` | Đưa một phần tử vào vùng tích lũy |
| Hàm ghép, `combiner` | Kết hợp các kết quả trung gian |
| Bước hoàn tất, `finisher` | Chuyển vùng tích lũy thành kết quả cuối nếu cần |

Với cách thu thập không dùng chung một bộ chứa giữa các luồng tích lũy, thư viện có thể chia dữ liệu thành các phần, tạo vùng riêng cho từng phần, rồi ghép sau khi tích lũy hoàn tất. Bản thân mỗi `ArrayList` riêng không cần chịu các lời gọi thêm đồng thời từ nhiều luồng.

[Hợp đồng `Collector`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collector.html) quy định việc cô lập vùng tích lũy đối với các collector không đồng thời. Đây là nền tảng để dùng phép thu thập song song đúng cách.

Điều đó không trao quyền chia sẻ cùng một `ArrayList` ra ngoài rồi để nhiều luồng tự sửa nó. Cũng không biến list cuối cùng thành một cấu trúc an toàn cho mọi truy cập đồng thời sau khi trả về.

### 5.2. Parallel không đồng nghĩa với CONCURRENT

Đặc tính `Collector.Characteristics.CONCURRENT` có nghĩa hàm tích lũy có thể được gọi đồng thời trên **cùng một bộ chứa kết quả**. Đây là cam kết khác với khả năng thư viện thực hiện nhiều phần tích lũy độc lập song song.

Một collection đích có cơ chế an toàn nhiều luồng cũng không tự thay thế được việc đọc các đặc tính của collector. Trong cách triển khai OpenJDK 17, `toCollection` không tự trở thành collector có `CONCURRENT` chỉ vì hàm tạo được cung cấp trả về một hàng đợi an toàn nhiều luồng.

`UNORDERED` lại là một đặc tính khác, nói về việc không cam kết giữ thứ tự gặp. Không được suy ra `UNORDERED` có nghĩa cho phép nhiều luồng tùy ý sửa cùng một bộ chứa.

[`Collector.Characteristics`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collector.Characteristics.html) định nghĩa các thuộc tính này. Khi phân tích một cách triển khai cụ thể, có thể quan sát `characteristics()`; không suy từ tên lớp đích thành một đặc tính mà collector chưa công bố.

### 5.3. Giữ thứ tự đầu vào không yêu cầu thứ tự thực thi cố định

Một luồng có thứ tự có thể xử lý các phần ở thời điểm khác nhau rồi ghép kết quả đúng thứ tự gặp. Vì vậy, `parallel().collect(Collectors.toList())` không mặc nhiên đảo lộn thứ tự list.

Nguồn và các thao tác trước đó vẫn phải cung cấp cam kết thứ tự cần giữ. Một nguồn không có thứ tự, hoặc một bước chủ ý bỏ cam kết thứ tự, không được bộ thu thập tự khôi phục lịch sử.

Không thay phép thu thập bằng một tác dụng phụ vào collection bên ngoài như đoạn cần tránh sau:

```java
// Mau can tranh: shared khong phai vung tich luy rieng cua collector.
List<String> shared = new ArrayList<>();
source.parallelStream().forEach(shared::add);
```

Đoạn trên có nguy cơ tranh chấp khi nhiều luồng thêm vào cùng list; nó cũng không cung cấp cơ chế ghép theo thứ tự của một phép thu thập. Chạy thành công một lần không chứng minh cách viết đó đúng. Sử dụng `collect` với bộ thu thập phù hợp để thư viện quản lý các vùng tích lũy.

### 5.4. Chương trình 3: Kết quả song song, quyền sửa và tham chiếu phần tử

Tệp `ParallelCollectorDemo.java`:

```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ParallelCollectorDemo {
    public static void main(String[] args) {
        List<Integer> sequential = IntStream.range(0, 5000)
                .map(value -> value % 13)
                .boxed()
                .collect(Collectors.toList());
        List<Integer> parallel = IntStream.range(0, 5000)
                .parallel()
                .map(value -> value % 13)
                .boxed()
                .collect(Collectors.toList());
        System.out.println("sameOrderedList=" + sequential.equals(parallel));
        System.out.println("parallelSize=" + parallel.size());

        Set<Integer> unique = IntStream.range(0, 5000)
                .parallel()
                .map(value -> value % 13)
                .boxed()
                .collect(Collectors.toSet());
        LinkedHashSet<Integer> orderedUnique = IntStream.range(0, 5000)
                .parallel()
                .map(value -> value % 13)
                .boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        System.out.println("parallelSetSize=" + unique.size());
        System.out.println("parallelFirstSeen=" + orderedUnique);

        Collector<Integer, ?, ConcurrentLinkedQueue<Integer>> queueCollector =
                Collectors.toCollection(ConcurrentLinkedQueue::new);
        System.out.println("queueCollectorConcurrent=" +
                queueCollector.characteristics()
                        .contains(Collector.Characteristics.CONCURRENT));

        List<Integer> directList = IntStream.range(0, 3).boxed().toList();
        System.out.println("streamToList=" + directList);
        try {
            directList.add(99);
            throw new AssertionError("Stream.toList result was editable");
        } catch (UnsupportedOperationException expected) {
            System.out.println("streamToListAdd=UnsupportedOperationException");
        }

        List<Integer> editable = IntStream.range(0, 3).boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        editable.add(99);
        System.out.println("explicitEditable=" + editable);

        StringBuilder message = new StringBuilder("status");
        List<StringBuilder> collected = Stream.of(message)
                .collect(Collectors.toList());
        message.append("-updated");
        System.out.println("sameElement=" + (collected.get(0) == message));
        System.out.println("elementAfter=" + collected);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ParallelCollectorDemo.java
java ParallelCollectorDemo
```

Đầu ra:

```text
sameOrderedList=true
parallelSize=5000
parallelSetSize=13
parallelFirstSeen=[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
queueCollectorConcurrent=false
streamToList=[0, 1, 2]
streamToListAdd=UnsupportedOperationException
explicitEditable=[0, 1, 2, 99]
sameElement=true
elementAfter=[status-updated]
```

`sameOrderedList=true` kiểm tra cả thứ tự và số lần xuất hiện của 5.000 phần tử. `parallelSetSize=13` kiểm tra kết quả gộp các giá trị từ `0` tới `12`. `parallelFirstSeen` còn giữ thứ tự gặp đầu tiên qua một đích `LinkedHashSet`.

`queueCollectorConcurrent=false` là quan sát đặc tính của collector được tạo trong ví dụ. Điểm cần hiểu là collection đích và cơ chế tích lũy của collector có các cam kết riêng; chọn một hàng đợi an toàn nhiều luồng không làm mọi kiểu phép thu thập trở thành tích lũy đồng thời vào cùng một vùng.

Phần `streamToListAdd` sẽ được đối chiếu với `Collectors.toList` ở phần tiếp theo. Phần `sameElement` và `elementAfter` cho thấy một giới hạn khác: tạo collection kết quả mới không tự sao chép sâu những đối tượng phần tử.

## 6. Quyền sửa là một hợp đồng riêng của kết quả

Trong ví dụ Collector ở mục 3–5, `Collectors.toList()` tạo một `List` nhưng **không cam kết khả năng sửa hoặc lớp triển khai cụ thể**. `Collectors.toCollection(ArrayList::new)` chủ động chọn `ArrayList` có thể thêm, xóa và thay phần tử. Còn `Stream.toList()` (Java 16+) là một terminal trực tiếp, có cam kết kết quả **không cho sửa**. Ba cách viết này không tương đương chỉ vì đều tạo `List`.

| Nhu cầu | Lựa chọn |
| --- | --- |
| Thu các phần tử thành List nhưng không cần lớp cụ thể | `collect(Collectors.toList())` |
| Thu thành List có quyền thêm hoặc sửa được xác định rõ | `collect(Collectors.toCollection(ArrayList::new))` |
| Chỉ cần danh sách kết quả không cho sửa, vẫn có thể chứa null | `stream.toList()` |
| Cần kết quả không cho sửa và từ chối null | `collect(Collectors.toUnmodifiableList())` |

Cũng có thể tích lũy vào một `ArrayList` mới rồi dùng `collectingAndThen` với `Collections.unmodifiableList` để trả một lớp bao chỉ đọc. Không được dùng chính lớp bao chỉ đọc làm vùng tích lũy của `toCollection`, vì hàm tích lũy vẫn cần thêm phần tử. Trường hợp collector chạy song song, thư viện có thể tạo nhiều vùng chứa rồi ghép; kết quả cuối không tự trở thành an toàn cho mọi thao tác sửa đồng thời về sau.

Riêng các đối tượng phần tử vẫn có thể dùng chung tham chiếu với nguồn. Ví dụ `StringBuilder` trong chương trình 3 thay đổi trạng thái mà không cần thay vị trí của List. Khi cần ảnh chụp độc lập ở cấp dữ liệu, phải tạo phần tử mới phù hợp. Bài *Stream.toList, collect và giới hạn bảo đảm về khả năng sửa* phân tích chi tiết phép `set`, `sort`, chính sách null, chuyển đổi kiểu và các giới hạn này.

## 7. Chọn và sử dụng bộ thu thập theo yêu cầu cụ thể

### 7.1. Bảng lựa chọn nhanh

| Yêu cầu | Lựa chọn phù hợp |
| --- | --- |
| Giữ mọi lần xuất hiện, cần một `List`, chưa yêu cầu lớp cụ thể | `Collectors.toList()` |
| Chỉ cần tập giá trị không trùng, không yêu cầu thứ tự hay lớp cụ thể | `Collectors.toSet()` |
| Chắc chắn cần list cho phép sửa theo hợp đồng `ArrayList` | `Collectors.toCollection(ArrayList::new)` |
| Gộp trùng và giữ thứ tự gặp của một nguồn có thứ tự | `Collectors.toCollection(LinkedHashSet::new)` |
| Cần một tập hợp duy trì bộ so sánh riêng | `toCollection` với hàm tạo một `TreeSet` mới có bộ so sánh ấy |
| Cần kết quả chỉ đọc | Chọn API/bước hoàn tất có cam kết chỉ đọc và đúng chính sách `null` |
| Cần giữ dữ liệu lặp rồi sắp xếp | Giữ kiểu dãy và dùng quy tắc sắp xếp; không tự thay bằng set |

Một yêu cầu như “không trùng” cần gắn với quy tắc nhận diện. Với set băm thông thường, cần `equals/hashCode` đúng hợp đồng. Với cây, phép so sánh bằng `0` quyết định việc gộp. Không thể đổi loại set rồi giả định mọi kiểu phần tử vẫn có cùng ý nghĩa trùng.

Nếu các đối tượng bằng nhau theo `equals` nhưng còn có trạng thái khác, `toSet` không cung cấp một chính sách nghiệp vụ “luôn giữ đúng bản ghi đầu tiên” để lựa chọn trạng thái đại diện. Hãy xác định chính sách đó rõ ràng và dùng một quy trình có cam kết phù hợp.

### 7.2. Phân biệt lọc dữ liệu với cách chứa dữ liệu

Chọn `ArrayList` hoặc `TreeSet` không thay thế việc xác thực đầu vào. Các câu hỏi cần được định nghĩa riêng:

- `null` là dữ liệu hợp lệ, cần bị loại bỏ hay phải làm cả thao tác thất bại?
- Các lần xuất hiện có ý nghĩa riêng hay được phép gộp?
- Cần giữ thứ tự nhận vào, thứ tự sắp xếp hay không cần thứ tự?
- Kiểu đích có thể chứa và so sánh được mọi giá trị nhận vào không?

Nếu nghiệp vụ chọn bỏ phần tử `null`, hãy làm bước lọc tường minh. Nếu nghiệp vụ chọn từ chối dữ liệu thiếu, đó là một quy tắc khác. Không gọi chung hai hành vi này là “làm sạch” rồi để loại collection tình cờ quyết định.

Tương tự, `toSet` không đếm tần suất. Sau khi các lần xuất hiện bằng nhau bị gộp, không thể chỉ từ set mà suy ra mỗi giá trị đã xuất hiện bao nhiêu lần.

### 7.3. Cẩn thận với stream và trạng thái bên ngoài

Hàm tạo, hàm biến đổi và các quy tắc được sử dụng trong quá trình thu thập cần đáp ứng hợp đồng tương ứng. Không sửa nguồn trong lúc stream đang duyệt nó, cũng không dùng một collection bên ngoài làm nơi chứa chung thay cho vùng tích lũy được thư viện quản lý.

Một nguồn là `ArrayList` thông thường có thể được đọc bởi một stream song song khi được dùng đúng điều kiện, không bị sửa trong lúc duyệt. Không cần biến mọi nguồn thành cấu trúc đồng thời chỉ vì xuất hiện `parallel()`. Điều phải kiểm soát là cách nguồn được truy cập và cách vùng tích lũy được quản lý.

Sau khi thu thập, nếu kết quả được trao cho nhiều luồng cùng sửa, cần một chính sách truy cập phù hợp cho kết quả ấy. Thành công của phép `collect` song song không chứng minh list trả về tự có mọi đặc tính an toàn đồng thời cho phần còn lại của ứng dụng.

[Hợp đồng `Stream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html) yêu cầu các hàm xử lý không can thiệp vào nguồn và giải thích việc sử dụng một stream theo một lần duyệt.

### 7.4. Chi phí vật chất hóa kết quả

Các bộ thu thập này tạo collection chứa kết quả, không tạo một khung nhìn lười tiếp tục đọc nguồn về sau. Với một list giữ `n` phần tử, cần chỗ cho các tham chiếu tương ứng. Với set, số giá trị được giữ còn phụ thuộc số phần tử phân biệt và chính sách của đích.

Một luồng kết quả vô hạn không thể được thu đầy đủ vào một collection hữu hạn. Cần thiết kế giới hạn hoặc cơ chế tiêu thụ phù hợp thay vì dùng `collect` như một thao tác tự dừng.

Chạy song song còn có chi phí tạo các vùng riêng và ghép kết quả. `parallel()` không tự bảo đảm nhanh hơn. Cấp trước một dung lượng rất lớn trong hàm tạo của `toCollection` cũng có thể gây lãng phí nếu hàm ấy được gọi cho nhiều phần dữ liệu.

Không dựa vào giả định “hàm tạo chỉ được gọi đúng một lần” để tính dung lượng, ghi nhận nghiệp vụ hoặc chia sẻ một bộ đệm. Khi hiệu năng quan trọng, cần đo trên dữ liệu và chuỗi thao tác thực sự, đồng thời giữ đúng hợp đồng của hàm tạo.

### 7.5. Kiểm tra kết quả bằng đúng tính chất được cam kết

Khi kiểm tra một phép thu thập:

- Với list có thứ tự, kiểm tra cả dãy và số lần xuất hiện.
- Với `toSet`, kiểm tra tập giá trị; không cố định một thứ tự mà API không hứa.
- Với đích tường minh, kiểm tra những khả năng mà kiểu đích cần cung cấp.
- Kiểm tra đầu vào rỗng, dữ liệu lặp và chính sách `null` theo yêu cầu.
- Nếu dùng lại collector, xác nhận kết quả giữa các lần thu thập không bị dùng chung ngoài ý muốn.
- Nếu dùng song song, so sánh kết quả theo đúng hợp đồng, không theo thứ tự các tác vụ hoàn tất.

Không dùng một phép ép kiểu, tên lớp nội bộ hoặc một lần sửa thành công trên một JDK để thay cho cam kết mà API đã công bố.

## 8. Thực hành và giải thích

### Bài 1: Một list có phần tử lặp cần giữ nguyên

Cho một nguồn có thứ tự `[tea, coffee, tea, juice]`. Cần lấy ra một list giữ đủ bốn vị trí và còn phải thêm phần tử về sau.

**Điểm cần giải thích:** giữ kiểu dãy đáp ứng yêu cầu số lần xuất hiện; chọn `toCollection(ArrayList::new)` diễn đạt thêm quyền sửa. `toSet` làm mất một lần `tea`; `Collectors.toList` không đủ để cam kết một lớp có quyền sửa cụ thể.

### Bài 2: Nhãn không trùng, giữ thứ tự gặp

Cho luồng có thứ tự, cần gộp các nhãn bằng nhau theo `equals` và giữ thứ tự xuất hiện đầu tiên.

**Điểm cần giải thích:** `toCollection(LinkedHashSet::new)` cung cấp một đích có hợp đồng phù hợp. `toSet` không cam kết thứ tự. Nếu nguồn đã không còn thứ tự cần thiết, chọn lớp đích có thứ tự cũng không khôi phục được lịch sử đã mất.

### Bài 3: Hàm tạo trả về một biến collection dùng chung

Một hàm tạo trả về biến `shared`. Để “sửa”, người viết gọi `clear()` trên nó trước mỗi lần trả về.

**Điểm cần giải thích:** xóa dữ liệu không tạo ra đối tượng riêng. Các lần thu thập vẫn có thể giữ cùng một collection; khi có nhiều vùng tích lũy, một lần xóa còn có thể phá dữ liệu vùng khác. Cần cấp collection mới, rỗng theo đúng hợp đồng thay vì tái sử dụng đối tượng dùng chung.

### Bài 4: Dùng collection chỉ đọc làm vùng tích lũy

Một đầu vào rỗng chạy qua `toCollection(() -> List.of())` có vẻ không gặp lỗi. Sau đó cùng mã thất bại với đầu vào có phần tử.

**Điểm cần giải thích:** trường hợp rỗng chưa thực hiện đường thêm dữ liệu. Collection được cung cấp không hỗ trợ vai trò tích lũy cần thiết. Nếu kết quả cuối cần chỉ đọc, dùng collection cho phép tích lũy rồi áp dụng một bước hoàn tất phù hợp.

### Bài 5: Chuyển từ collect(toList()) sang Stream.toList()

Mã phía sau đang thêm phần tử vào kết quả. Sau một lần rút gọn cú pháp, thao tác thêm gây `UnsupportedOperationException`.

**Điểm cần giải thích:** hai API có cam kết khác nhau; `Stream.toList` không cho sửa. Nhu cầu sửa nên được nêu rõ bằng loại đích thích hợp, không dựa vào việc phiên bản hiện tại của `Collectors.toList` từng cho sửa.

### Bài 6: Thu thập song song bằng một list bên ngoài

Người viết dùng `parallelStream().forEach(shared::add)` với `shared` là một `ArrayList`, rồi thấy một lần chạy cho đủ số phần tử.

**Điểm cần giải thích:** chưa có cơ chế cô lập và ghép vùng tích lũy của collector; một lần thành công không chứng minh an toàn. Dùng `collect` đúng cách cho phép thư viện quản lý các kết quả riêng, rồi xem xét chính sách truy cập list sau khi phép thu thập kết thúc.

Khi đọc một lời gọi thu thập, hãy xác định được **kiểu kết quả, cách xử lý phần tử trùng, cam kết thứ tự, quyền sửa và nơi mỗi vùng tích lũy được tạo**. Những thông tin ấy giải thích được vì sao ba bộ thu thập gần giống nhau về cú pháp lại phục vụ những nhu cầu khác nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="streams-spliterators-007">Collectors.toMap: khóa trùng, hàm gộp và loại Map đích</a>

<details>
<summary>Xem chi tiết</summary>

# Collectors.toMap: khóa trùng, hàm gộp và loại Map đích

## 1. Tạo Map là một phép quyết định cách gom dữ liệu

Giả sử một Stream đưa ra các dòng bán hàng:

~~~text
mã B, số lượng 2
mã A, số lượng 3
mã B, số lượng 5
mã C, số lượng 4
mã A, số lượng 1
~~~

Ta muốn tra cứu theo mã. Nhưng một Map chỉ giữ tối đa một giá trị cho mỗi khóa, trong khi mã `B` và `A` xuất hiện nhiều lần. Vì vậy, chuyển các dòng thành Map không chỉ là đổi cách chứa dữ liệu. Phải quyết định những dòng cùng mã được xử lý thế nào.

Một số kết quả đều có thể đúng, tùy yêu cầu:

| Quy tắc | Giá trị của B | Giá trị của A |
| --- | --- | --- |
| Giữ dòng đầu tiên | `2` | `3` |
| Giữ dòng sau cùng | `5` | `1` |
| Cộng số lượng | `7` | `4` |
| Chọn số lượng lớn nhất | `5` | `3` |
| Giữ tất cả số lượng thành danh sách | `[2, 5]` | `[3, 1]` |
| Cấm mã xuất hiện lặp | Báo lỗi | Báo lỗi |

`Collectors.toMap` cung cấp cách tạo collector để gom các phần tử vào Map. Hai hàm ánh xạ xác định khóa và giá trị; các overload khác bổ sung hàm gộp khi trùng khóa và factory tạo Map đích. [Java SE 17 — Collectors.toMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#toMap(java.util.function.Function,java.util.function.Function)).

Bài này tập trung vào ba quyết định: **khóa được tạo ra là gì, các giá trị cùng khóa được gộp ra sao, và Map nào sẽ giữ kết quả**. Hiểu ba phần đó giúp tránh vừa mất dữ liệu vừa tưởng phép collect đã thành công đúng ý.

## 2. Đọc ba overload theo mức quyền điều khiển

Dạng viết rút gọn:

~~~java
toMap(keyMapper, valueMapper)

toMap(keyMapper, valueMapper, mergeFunction)

toMap(keyMapper, valueMapper, mergeFunction, mapFactory)
~~~

Đây là minh họa các overload, không phải những lời gọi độc lập đã có đủ biến trong phạm vi.

| Tham số | Nhiệm vụ |
| --- | --- |
| `keyMapper` | Biến một phần tử đầu vào thành khóa `K` |
| `valueMapper` | Biến phần tử đó thành giá trị `U` |
| `mergeFunction` | Nhận hai giá trị `U` cùng khóa và quyết định giá trị `U` kết quả |
| `mapFactory` | Tạo một Map mới, rỗng, phù hợp để collector chèn/gộp dữ liệu |

Với mô hình Java 17:

~~~java
record Sale(String code, int quantity) { }
~~~

có thể dùng `Sale::code` để tạo khóa và `Sale::quantity` để tạo giá trị. `quantity` là `int` nhưng được đóng gói thành `Integer` khi làm giá trị của `Map<String, Integer>`.

Phân biệt thao tác kết thúc và collector:

~~~java
Map<String, Integer> total = rows.stream().collect(
        Collectors.toMap(
                Sale::code,
                Sale::quantity,
                Integer::sum,
                LinkedHashMap::new));
~~~

`Collectors.toMap(...)` mô tả cách gom. `collect(...)` mới là thao tác kết thúc thực hiện lượt xử lý Stream. Đoạn trên giả định `rows` là danh sách các `Sale`, có các import thích hợp; chương trình đầy đủ phía sau cung cấp toàn bộ phần cần chạy.

## 3. Dạng hai tham số: cấm khóa trùng

~~~java
Map<String, Integer> byCode = rows.stream()
        .collect(Collectors.toMap(Sale::code, Sale::quantity));
~~~

Dạng này phù hợp khi mỗi khóa sau ánh xạ phải xuất hiện duy nhất. Nếu có hai đầu vào tạo ra cùng khóa, collector ném `IllegalStateException`. Nó không mặc định giữ đầu, giữ cuối hoặc cộng.

### 3.1. Giá trị bằng nhau vẫn là trùng khóa

Hai dòng `A=1` và `A=1` vẫn là hai đầu vào cùng khóa. Không có quy tắc mặc định “nếu giá trị bằng nhau thì chấp nhận”.

Nếu ứng dụng thật sự muốn chấp nhận hai bản ghi tương đương nhưng báo lỗi khi nội dung khác, cần viết một hàm gộp diễn tả chính sách đó. Không thể suy ra chính sách từ việc dùng overload hai tham số.

### 3.2. Trùng xét trên khóa sau keyMapper

Các chuỗi nguồn có thể khác nhau:

~~~text
"Java"
" java "
"JAVA"
~~~

Nhưng `trim().toLowerCase(Locale.ROOT)` tạo cùng khóa `java`. Trùng xuất hiện ở **kết quả ánh xạ khóa**, không nhất thiết ở đối tượng đầu vào trước đó.

Chính sách chuẩn hóa phải được áp dụng nhất quán. Nếu khóa biểu diễn một mã không phân biệt hoa thường, có thể chuẩn hóa mã theo quy tắc đã chọn rồi xử lý trùng trên dạng chuẩn. Không tùy tiện bỏ khoảng trắng hoặc đổi chữ hoa/thường nếu dữ liệu nghiệp vụ coi những khác biệt đó là có ý nghĩa.

### 3.3. distinct trên dòng chưa chắc làm khóa duy nhất

Với `record Sale(String code, int quantity)`, `Sale("B", 2)` và `Sale("B", 5)` là hai dòng khác nhau. `rows.stream().distinct()` không loại một trong hai chỉ vì cùng mã.

Sau đó `toMap(Sale::code, Sale::quantity)` vẫn gặp trùng khóa `B`. Phải phân biệt quan hệ bằng nhau của toàn bộ đầu vào với quan hệ bằng nhau của khóa dùng cho Map.

Tương tự, hai khóa có cùng `hashCode` chưa đủ để kết luận là cùng khóa theo equals. Xung đột giá trị băm và trùng khóa sau ánh xạ là hai chuyện khác nhau.

## 4. Hàm gộp quyết định dữ liệu nào còn lại

Dạng ba hoặc bốn tham số nhận một `BinaryOperator<U>`. Hai toán hạng và kết quả đều thuộc kiểu giá trị `U`, không phải tự động là hai phần tử đầu vào ban đầu. [Java SE 17 — BinaryOperator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/BinaryOperator.html).

Trong lượt tuần tự, khi gặp một khóa đã có giá trị, có thể hình dung:

~~~text
giá trị đang tích lũy + giá trị từ dòng mới -> giá trị thay thế
~~~

Nếu đang cộng nhiều dòng, “giá trị đang tích lũy” có thể đã là tổng của nhiều dòng trước, không còn là số lượng của một dòng đơn lẻ.

### 4.1. Giữ đầu hoặc giữ cuối là hai chính sách khác nhau

~~~java
(oldValue, newValue) -> oldValue
~~~

Giữ giá trị đã có. Với nguồn có thứ tự xử lý tuần tự, `B` giữ `2`, `A` giữ `3`.

~~~java
(oldValue, newValue) -> newValue
~~~

Giữ giá trị đến sau. Với nguồn ấy, `B` thành `5`, `A` thành `1`.

“Sau” ở đây là sau trong thứ tự xử lý được yêu cầu, không tự có nghĩa mới nhất theo thời gian nghiệp vụ. Nếu cần bản ghi có timestamp mới nhất, hãy so timestamp bằng một quy tắc tường minh; đừng dùng vị trí sau của một nguồn không có thứ tự được cam kết để suy ra độ mới.

Cũng không có bảo đảm rằng “giữ cũ” làm `valueMapper` của dòng mới được bỏ qua. Mỗi dòng vẫn phải được ánh xạ để tạo giá trị trước khi giải quyết xung đột; một phép ánh xạ sai hoặc tạo null vẫn có thể làm collect thất bại.

### 4.2. Cộng hoặc chọn lớn nhất

Các hàm phổ biến với giá trị `Integer`:

~~~java
Integer::sum
Integer::max
~~~

Với dữ liệu ban đầu:

~~~text
cộng:         B=7, A=4, C=4
chọn lớn:     B=5, A=3, C=4
~~~

Chọn quy tắc phải theo nghĩa dữ liệu. Cộng phù hợp số lượng của nhiều giao dịch; không nhất thiết phù hợp một mã trạng thái hoặc một số đo chỉ nên giữ phiên bản cuối.

Nếu tổng có thể vượt miền `int`, cần chọn mô hình số thích hợp và quy tắc kiểm tra giới hạn. Chọn `Integer::sum` không tự cung cấp kiểm tra tràn theo nhu cầu nghiệp vụ.

### 4.3. Giữ cả đối tượng bằng Function.identity

Nếu cần Map trỏ tới dòng bán hàng được chọn:

~~~java
Map<String, Sale> best = rows.stream().collect(
        Collectors.toMap(
                Sale::code,
                Function.identity(),
                BinaryOperator.maxBy(Comparator.comparingInt(Sale::quantity)),
                LinkedHashMap::new));
~~~

Lúc này kiểu giá trị là `Sale` nên hàm gộp nhận hai `Sale`. `Function.identity()` giữ chính đối tượng đầu vào làm giá trị; nó không sao chép đối tượng.

Nếu hai dòng bằng nhau theo tiêu chí số lượng nhưng khác các trường khác, cần thêm quy tắc phá hòa phù hợp. Không nên để việc chọn một trong hai chỉ là hệ quả vô tình của thứ tự duyệt.

## 5. Muốn giữ mọi dòng thì đừng âm thầm bỏ chúng

Nếu cần giữ `B -> [2, 5]`, `A -> [3, 1]` và `C -> [4]`, bài toán là phân nhóm nhiều giá trị cho mỗi khóa:

~~~java
Map<String, List<Integer>> all = rows.stream().collect(
        Collectors.groupingBy(
                Sale::code,
                LinkedHashMap::new,
                Collectors.mapping(Sale::quantity, Collectors.toList())));
~~~

Đây là lựa chọn tự nhiên hơn việc thêm một hàm “giữ đầu” chỉ để dập lỗi trùng khóa.

Có thể dùng `toMap` với giá trị là danh sách, nhưng khi đó phải tự quyết định cách tạo danh sách mới và cách gộp chúng, tránh sửa một danh sách bất biến hoặc dùng chung danh sách sai phạm vi. Với yêu cầu giữ tất cả, `groupingBy` diễn tả ý định rõ ràng hơn.

Điểm cần giữ là: **hàm gộp có thể làm mất thông tin có chủ đích**. Nếu hai giao dịch được gộp thành một tổng, không thể từ tổng đó lấy lại chính xác từng giao dịch ban đầu. Nếu chỉ giữ dòng đầu, những dòng sau không còn trong Map kết quả.

Trước khi chọn merger, hãy trả lời Map sẽ phục vụ tra cứu một đại diện, tính toán tổng hợp hay lưu toàn bộ nhóm dữ liệu. Đó là quyết định mô hình, không chỉ là một lambda ngắn cho mã biên dịch được.

## 6. Hàm gộp và xử lý song song: cần tính kết hợp

Collector có thể tích lũy từng phần dữ liệu vào các Map riêng rồi gộp các kết quả từng phần. Vì vậy, hàm gộp không chỉ được dùng như “giá trị cũ của một dòng và giá trị của dòng kế tiếp”; nó có thể nhận những giá trị đã được tổng hợp.

Hợp đồng Collector yêu cầu các phép tích lũy và kết hợp đáp ứng điều kiện để việc chia rồi ghép cho kết quả tương đương. [Java SE 17 — Collector](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collector.html).

Một điều kiện quan trọng cho quy tắc gộp là tính kết hợp:

~~~text
merge(merge(a, b), c) tương đương merge(a, merge(b, c))
~~~

Phép trừ không thỏa:

~~~text
(10 - 3) - 2 = 5
10 - (3 - 2) = 9
~~~

Nếu dùng `(a, b) -> a - b` rồi kỳ vọng luôn như một vòng lặp trừ từ trái sang phải, việc chuyển sang pipeline song song có thể làm lộ sai giả định.

Tính kết hợp không đồng nghĩa tính giao hoán. Quy tắc giữ đầu hoặc giữ cuối có tính chất theo thứ tự riêng; nó không cho phép đảo tùy ý các giá trị hoặc bỏ yêu cầu thứ tự. Nếu đầu vào không có thứ tự được cam kết hoặc dùng một phép gom unordered, “đầu/cuối” phải được đánh giá lại.

Các hàm ánh xạ và hàm gộp cũng không nên sửa nguồn Stream hoặc phụ thuộc vào một bộ đếm ngoài để quyết định kết quả. Trong pipeline song song, trạng thái dùng chung có thể tạo tranh chấp; ngay cả khi chạy tuần tự, nó làm quy tắc gộp khó hiểu và khó kiểm tra.

Với yêu cầu nghiệp vụ đặc biệt vốn là một chuỗi cập nhật có trạng thái không kết hợp được, một vòng lặp tuần tự tường minh có thể là cách diễn tả đúng hơn cố ép vào một phép gom tổng quát.

## 7. Hàm gộp trả null có nghĩa là xóa ánh xạ

Các overload có merger dùng ngữ nghĩa gộp theo `Map.merge`. Nếu hàm gộp trả `null`, ánh xạ hiện có bị xóa; không phải khóa được giữ với một giá trị null. [Java SE 17 — Map.merge](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#merge(K,V,java.util.function.BiFunction)).

Ví dụ cố ý chạy tuần tự với merger `(a, b) -> null`:

~~~text
A=10  -> {A=10}
B=1   -> {A=10, B=1}
A=20  -> gộp A trả null, xóa A -> {B=1}
A=30  -> A chưa có, thêm lại  -> {B=1, A=30}
~~~

Kết quả không phải `{A=null, B=1}`. Khóa `A` còn có thể xuất hiện trở lại nếu một dòng sau cùng khóa được xử lý sau khi ánh xạ đã bị xóa.

Đây là một hành vi cần biết để đọc API, không phải chính sách mặc định nên dùng để tránh lỗi trùng. Quy tắc xóa khi va chạm còn có thể không phù hợp các điều kiện kết hợp khi chạy song song. Ca thử trong bài chỉ minh họa chuỗi thao tác tuần tự đã nêu.

Nếu ý định là báo lỗi khi trùng, hãy ném lỗi hoặc dùng overload cấm trùng. Nếu ý định là bỏ một số dòng, cân nhắc lọc với điều kiện rõ ràng trước khi gom. Đừng trả null từ merger chỉ vì chưa quyết định phải giữ giá trị nào.

## 8. Map đích quyết định thứ tự và một phần ngữ nghĩa khóa

### 8.1. Không có factory thì không hứa một lớp Map cụ thể

Hai overload đầu trả `Map` theo hợp đồng, không bảo đảm loại triển khai, quyền sửa, tính tuần tự hóa hay tính an toàn đa luồng cụ thể. Không dựa vào một lần thấy `HashMap` trong trình gỡ lỗi để ép kiểu hoặc suy ra thứ tự đầu ra.

Nếu chỉ cần đọc và tra cứu mà không cần một hợp đồng đặc biệt, có thể giữ kết quả ở kiểu `Map<K, V>`. Nếu thứ tự, comparator hoặc khả năng sửa là một phần của yêu cầu, chỉ định đích phù hợp bằng overload bốn tham số.

### 8.2. LinkedHashMap: giữ thứ tự chèn của khóa

~~~java
Map<String, Integer> total = rows.stream().collect(
        Collectors.toMap(
                Sale::code,
                Sale::quantity,
                Integer::sum,
                LinkedHashMap::new));
~~~

Trong ví dụ với List có thứ tự và pipeline tuần tự, khóa xuất hiện lần đầu theo `B, A, C`. Các lần gộp cập nhật `B` hoặc `A` không đưa chúng xuống cuối trong LinkedHashMap chế độ thứ tự chèn mặc định.

Kết quả được in là `{B=7, A=4, C=4}`. Đây là **thứ tự khóa được chèn**, không phải thứ tự tăng dần và cũng không phải thứ tự của lần cập nhật cuối. [Java SE 17 — LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

Chọn LinkedHashMap không tự làm một nguồn unordered trở thành có thứ tự nghiệp vụ ổn định. Nếu nguồn không cam kết phần tử nào đến trước, cần xác định hoặc sắp xếp đầu vào trước theo yêu cầu.

### 8.3. TreeMap: sắp xếp theo khóa

~~~java
Map<String, Integer> total = rows.stream().collect(
        Collectors.toMap(
                Sale::code,
                Sale::quantity,
                Integer::sum,
                TreeMap::new));
~~~

Với khóa chuỗi thông thường, kết quả theo thứ tự tự nhiên là `{A=4, B=7, C=4}`. Nếu có comparator riêng, thứ tự khóa và việc nhận biết khóa tương đương theo comparator phụ thuộc comparator ấy. [Java SE 17 — TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html).

TreeMap không sắp xếp theo giá trị. Nếu muốn tổng số lượng giảm dần, cần một bước sắp xếp các cặp theo giá trị rồi xây dựng biểu diễn kết quả phù hợp; đổi factory sang TreeMap chưa giải quyết yêu cầu đó.

### 8.4. Factory phải tạo Map mới, rỗng và dùng được cho quá trình gom

Không viết một supplier luôn trả lại cùng một Map bên ngoài đã chứa dữ liệu rồi mong collector tự phân biệt dữ liệu cũ/mới. Đặc biệt khi chia việc song song, các vùng tích lũy phải có phạm vi sở hữu phù hợp.

`LinkedHashMap::new` và `TreeMap::new` diễn tả rõ việc tạo mới. Với loại khóa enum, có thể chọn một factory tạo `EnumMap` theo đúng lớp enum nếu đó là hợp đồng đích cần có.

Một Map không hỗ trợ chèn/gộp không thể làm nơi tích lũy bình thường chỉ vì nó rỗng lúc ban đầu. Khả năng tạo đối tượng đích và khả năng thực hiện các phép tích lũy cần cùng phù hợp.

## 9. Đổi Map đích có thể đổi cách nhận biết cùng khóa

Với Map thông thường tuân thủ equals, `"a"` và `"A"` là hai khóa khác nhau. Nhưng TreeMap dùng comparator `String.CASE_INSENSITIVE_ORDER` coi chúng bằng nhau về so sánh.

Ca minh họa:

~~~java
Map<String, Integer> counts = letters.stream().collect(
        Collectors.toMap(
                Function.identity(),
                text -> 1,
                Integer::sum,
                () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));
~~~

Với `letters = ["a", "A", "b"]`, chương trình cho `{a=2, b=1}`. Cùng dữ liệu vào LinkedHashMap cho ba khóa `a`, `A` và `b` riêng.

Ví dụ này cố ý cho thấy tác động của comparator. Comparator không phân biệt hoa thường không nhất quán với equals của String; TreeMap vẫn có hành vi so sánh được định nghĩa, nhưng không tuân thủ đầy đủ hợp đồng Map theo equals trong trường hợp đó.

Nếu nghiệp vụ coi hai dạng mã là một, thường nên chuẩn hóa khóa thành một biểu diễn chung và dùng quy tắc khóa nhất quán, thay vì vô tình đưa một comparator làm thay đổi quan hệ khóa vào factory.

Không chỉ kiểm tra “các đối tượng khóa có khác nhau không”. Hãy kiểm tra **Map đích dùng tiêu chí gì để coi hai khóa là cùng một vị trí tra cứu**. Đồng thời tránh sửa đối tượng khóa sau khi đưa vào Map theo cách làm thay đổi equals, hashCode hoặc thứ tự so sánh cần cho cấu trúc.

## 10. null: phân biệt giá trị ánh xạ, khóa và kết quả merger

### 10.1. valueMapper trả null không được cứu bởi Map đích cho phép null

Trong các collector được minh chứng, nếu `valueMapper` trả null, collect ném `NullPointerException`. Điều này xảy ra cả ở dạng hai tham số và ở dạng dùng LinkedHashMap cho phép lưu null bằng các phép thao tác khác.

Dạng có merger gắn với `Map.merge`, vốn nhận giá trị đầu vào khác null. Dạng không merger trong OpenJDK 17 cũng kiểm tra giá trị ánh xạ khác null khi tích lũy. [OpenJDK 17 — Collectors.java](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/stream/Collectors.java).

Do đó, không sửa lỗi bằng cách chỉ đổi `HashMap` thành `LinkedHashMap`. Phải chọn chính sách cho giá trị thiếu: báo lỗi tại biên dữ liệu, lọc dòng, dùng một đối tượng biểu diễn vắng mặt hoặc một mô hình thu thập khác nếu thật sự cần ánh xạ null.

Nếu báo lỗi, có thể kiểm tra tường minh với `Objects.requireNonNull` kèm thông tin phù hợp, thay vì chờ một thông báo ít ngữ cảnh ở sâu trong phép collect. [Java SE 17 — Objects.requireNonNull](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html#requireNonNull(T)).

### 10.2. Khóa null còn phụ thuộc Map và quy tắc đích

Ví dụ dùng factory tường minh `LinkedHashMap::new` có thể lưu một khóa null với giá trị khác null. TreeMap dùng thứ tự tự nhiên không chấp nhận khóa null trong ca thử.

Không biến hành vi của một Map cụ thể thành lời hứa rằng mọi overload hoặc mọi factory đều chấp nhận khóa null. Nếu khóa thiếu là dữ liệu không hợp lệ của ứng dụng, hãy xác thực trước; chọn một Map tình cờ chấp nhận null không làm mã nghiệp vụ trở nên đúng.

### 10.3. Merger trả null là một hành vi khác

Tóm tắt ba trường hợp:

| Tình huống | Ý nghĩa |
| --- | --- |
| `valueMapper` trả null trong các ca collector đã nêu | Không có giá trị đầu vào hợp lệ để tích lũy; gây lỗi |
| Khóa ánh xạ là null | Cần xem hợp đồng Map đích và chính sách ứng dụng |
| `mergeFunction` trả null | Xóa ánh xạ theo Map.merge |

Đừng dùng một câu chung “toMap không xử lý null” rồi bỏ qua sự khác nhau. Những vị trí null này xuất hiện ở những giai đoạn và mang ý nghĩa khác nhau.

## 11. Loại Map kết quả không quyết định collector có concurrent hay không

`toMap` là collector không concurrent. Nó vẫn có thể dùng trong một pipeline song song: thư viện tạo các vùng tích lũy riêng, sau đó gộp Map từng phần theo hợp đồng. Việc ghép khóa từ nhiều Map có thể tốn chi phí.

Chỉ định một loại Map có hỗ trợ truy cập đồng thời trong factory không tự biến collector `toMap` thành collector có đặc tính CONCURRENT. Nếu cần mô hình gom đồng thời phù hợp, xem `toConcurrentMap` và hợp đồng unordered của nó.

Đừng dùng `(a, b) -> b` với một phép gom unordered rồi gọi giá trị được giữ là “dòng cuối trong tệp” khi không có cơ chế bảo toàn thứ tự đó. Tính an toàn truy cập và thứ tự nghiệp vụ là hai yêu cầu độc lập.

Ví dụ song song trong chương trình dùng phép cộng số lượng, so sánh Map kết quả theo equals với kết quả tuần tự. Nó không in thứ tự của Map mặc định hoặc đo tốc độ để suy ra một cách thực hiện luôn tốt hơn.

Ngoài Map tích lũy, chính nguồn và các đối tượng được giữ làm giá trị cũng phải phù hợp với cách truy cập. `Function.identity` không tự biến các đối tượng giá trị thành an toàn đa luồng hoặc tạo bản sao sâu.

## 12. Lỗi không phải một giao dịch quay lui toàn bộ công việc

Nếu gặp trùng khóa bị cấm, lỗi ánh xạ hoặc ngoại lệ trong merger, collect thất bại và không có Map kết quả thành công để trả về cho lời gọi ấy.

Nhưng mọi tác dụng phụ bên ngoài mà lambda đã làm trước đó không tự được hoàn tác. Ví dụ ghi một bản tin ở `valueMapper` rồi gặp khóa trùng sau đó không khiến bản tin đã ghi biến mất.

Vì vậy, các hàm nên tập trung vào biến đổi và gộp dữ liệu. Nếu cần kiểm tra dữ liệu trước một hành động bên ngoài không thể dễ hoàn tác, hãy thu và xác thực kết quả trước, sau đó mới thực hiện hành động theo một giai đoạn phù hợp.

Không đưa một Map bên ngoài dùng chung vào supplier để quan sát “kết quả dở dang” như một cơ chế lỗi mặc định. Supplier của collector phải giữ đúng hợp đồng vùng tích lũy; cần thiết kế báo lỗi có cấu trúc riêng nếu ứng dụng muốn báo tất cả mã trùng hoặc tất cả dòng sai.

## 13. Chương trình minh chứng đầy đủ

Lưu mã sau thành **ToMapPoliciesDemo.java**. Chương trình dùng record và thư viện chuẩn, tương thích Java 17.

Các Map có stdout phụ thuộc thứ tự được tạo bằng LinkedHashMap hoặc TreeMap. Ở ca dạng hai tham số, chương trình tạo một TreeMap riêng chỉ để in dữ liệu theo thứ tự; bước hiển thị đó không phải tuyên bố collector mặc định trả TreeMap.

Hàm `rejects` chỉ chấp nhận đúng họ ngoại lệ dự kiến. Ca merger trả null được chạy tuần tự có chủ đích để quan sát xóa rồi thêm lại ánh xạ. Comparator không phân biệt hoa thường là ví dụ về thay đổi tiêu chí khóa, không phải khuyến nghị bỏ qua yêu cầu nhất quán với equals.

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ToMapPoliciesDemo {
    record Sale(String code, int quantity) { }
    record Label(String code, String text) { }

    private static void rejects(
            String label,
            Class<? extends RuntimeException> expected,
            Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " did not fail");
        } catch (RuntimeException ex) {
            if (!expected.isInstance(ex)) {
                throw ex;
            }
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        List<Sale> rows = List.of(
                new Sale("B", 2), new Sale("A", 3),
                new Sale("B", 5), new Sale("C", 4), new Sale("A", 1));

        Map<String, Integer> unique = rows.subList(0, 2).stream()
                .collect(Collectors.toMap(Sale::code, Sale::quantity));
        System.out.println("unique-sorted-for-display=" + new TreeMap<>(unique));
        rejects("duplicate-key", IllegalStateException.class,
                () -> rows.stream().collect(Collectors.toMap(Sale::code, Sale::quantity)));
        rejects("equal-values-still-duplicate", IllegalStateException.class,
                () -> List.of(new Sale("A", 1), new Sale("A", 1)).stream()
                        .collect(Collectors.toMap(Sale::code, Sale::quantity)));
        rejects("distinct-rows-not-distinct-keys", IllegalStateException.class,
                () -> rows.stream().distinct()
                        .collect(Collectors.toMap(Sale::code, Sale::quantity)));

        Map<String, Integer> first = rows.stream().collect(Collectors.toMap(
                Sale::code, Sale::quantity, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
        Map<String, Integer> last = rows.stream().collect(Collectors.toMap(
                Sale::code, Sale::quantity, (oldValue, newValue) -> newValue, LinkedHashMap::new));
        Map<String, Integer> total = rows.stream().collect(Collectors.toMap(
                Sale::code, Sale::quantity, Integer::sum, LinkedHashMap::new));
        Map<String, Integer> largest = rows.stream().collect(Collectors.toMap(
                Sale::code, Sale::quantity, Integer::max, LinkedHashMap::new));
        System.out.println("first=" + first);
        System.out.println("last=" + last);
        System.out.println("total=" + total);
        System.out.println("largest=" + largest);
        System.out.println("insertion-order=" + new ArrayList<>(total.keySet()));

        Map<String, Integer> sorted = rows.stream().collect(Collectors.toMap(
                Sale::code, Sale::quantity, Integer::sum, TreeMap::new));
        System.out.println("tree-order=" + sorted);

        Map<String, Sale> bestRows = rows.stream().collect(Collectors.toMap(
                Sale::code, Function.identity(),
                BinaryOperator.maxBy(Comparator.comparingInt(Sale::quantity)),
                LinkedHashMap::new));
        System.out.println("best-B=" + bestRows.get("B").quantity());

        Map<String, List<Integer>> all = rows.stream().collect(Collectors.groupingBy(
                Sale::code, LinkedHashMap::new,
                Collectors.mapping(Sale::quantity, Collectors.toList())));
        System.out.println("all-values=" + all);

        List<String> spellings = List.of("Java", " java ", "JAVA");
        Map<String, Integer> normalized = spellings.stream().collect(Collectors.toMap(
                text -> text.trim().toLowerCase(Locale.ROOT),
                text -> 1, Integer::sum, LinkedHashMap::new));
        System.out.println("normalized=" + normalized);

        List<String> letters = List.of("a", "A", "b");
        Map<String, Integer> ordinary = letters.stream().collect(Collectors.toMap(
                Function.identity(), text -> 1, Integer::sum, LinkedHashMap::new));
        Map<String, Integer> ignoreCase = letters.stream().collect(Collectors.toMap(
                Function.identity(), text -> 1, Integer::sum,
                () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));
        System.out.println("ordinary-keys=" + ordinary);
        System.out.println("comparator-keys=" + ignoreCase);

        List<Label> nullable = List.of(new Label("A", "ok"), new Label("B", null));
        rejects("null-value-two-arguments", NullPointerException.class,
                () -> nullable.stream().collect(Collectors.toMap(Label::code, Label::text)));
        rejects("null-value-linked-map", NullPointerException.class,
                () -> nullable.stream().collect(Collectors.toMap(
                        Label::code, Label::text, (a, b) -> b, LinkedHashMap::new)));

        List<Label> nullKey = List.of(new Label(null, "unknown"));
        Map<String, String> explicitNullKey = nullKey.stream().collect(Collectors.toMap(
                Label::code, Label::text, (a, b) -> b, LinkedHashMap::new));
        System.out.println("explicit-null-key=" + explicitNullKey);
        rejects("null-key-natural-tree", NullPointerException.class,
                () -> nullKey.stream().collect(Collectors.toMap(
                        Label::code, Label::text, (a, b) -> b, TreeMap::new)));

        List<Sale> cancellation = List.of(
                new Sale("A", 10), new Sale("B", 1),
                new Sale("A", 20), new Sale("A", 30));
        Map<String, Integer> removedOnMerge = cancellation.stream().collect(Collectors.toMap(
                Sale::code, Sale::quantity, (a, b) -> null, LinkedHashMap::new));
        System.out.println("null-merge-result=" + removedOnMerge);

        int left = (10 - 3) - 2;
        int right = 10 - (3 - 2);
        System.out.println("subtraction-grouping=" + left + " / " + right);
        Map<String, Integer> parallelTotal = rows.parallelStream().collect(Collectors.toMap(
                Sale::code, Sale::quantity, Integer::sum));
        System.out.println("parallel-equal=" + parallelTotal.equals(total));
        System.out.println("input-size=" + rows.size());
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 ToMapPoliciesDemo.java
java ToMapPoliciesDemo
~~~

Stdout chính xác:

~~~text
unique-sorted-for-display={A=3, B=2}
duplicate-key -> IllegalStateException
equal-values-still-duplicate -> IllegalStateException
distinct-rows-not-distinct-keys -> IllegalStateException
first={B=2, A=3, C=4}
last={B=5, A=1, C=4}
total={B=7, A=4, C=4}
largest={B=5, A=3, C=4}
insertion-order=[B, A, C]
tree-order={A=4, B=7, C=4}
best-B=5
all-values={B=[2, 5], A=[3, 1], C=[4]}
normalized={java=3}
ordinary-keys={a=1, A=1, b=1}
comparator-keys={a=2, b=1}
null-value-two-arguments -> NullPointerException
null-value-linked-map -> NullPointerException
explicit-null-key={null=unknown}
null-key-natural-tree -> NullPointerException
null-merge-result={B=1, A=30}
subtraction-grouping=5 / 9
parallel-equal=true
input-size=5
~~~

Mã đã biên dịch với `--release 17` và chạy trên JDK 24.0.2. Cờ biên dịch xác minh mức ngôn ngữ/API Java 17; đây không phải một lần chạy bằng JVM 17.

Các nhóm kết quả cần đối chiếu:

- Ba ca trùng khóa phân biệt khóa trùng, giá trị bằng nhau và distinct trên toàn dòng.
- `first`, `last`, `total`, `largest` dùng bốn chính sách khác nhau cho cùng dữ liệu.
- `insertion-order` và `tree-order` tách thứ tự chèn khỏi thứ tự khóa.
- `best-B` giữ cả một dòng Sale được chọn, thay vì chỉ giữ số lượng.
- `all-values` bảo toàn mọi số lượng bằng phân nhóm.
- `normalized` cho thấy các chuỗi khác nhau có thể tạo cùng khóa sau chuẩn hóa.
- Hai dòng `ordinary-keys` và `comparator-keys` cho thấy Map đích có thể thay đổi cách xử lý khóa tương đương theo comparator.
- Các ca null phân biệt giá trị đầu vào bị từ chối với khả năng lưu khóa null của Map đã chọn.
- `null-merge-result` thể hiện xóa ánh xạ rồi thêm lại, không lưu giá trị null.
- `subtraction-grouping` giải thích tại sao phải xét tính kết hợp.
- `parallel-equal` kiểm tra nội dung của kết quả cộng gộp; `input-size` xác nhận danh sách nguồn vẫn có năm dòng.

## 14. Thực hành có đáp án

### Bài 1 — Cùng khóa, cùng giá trị

Hai đầu vào tạo `A=1` và `A=1`. Dạng hai tham số của toMap có tự chấp nhận vì hai giá trị bằng nhau không?

**Đáp án:** Không. Nó cấm khóa trùng và ném IllegalStateException. Nếu muốn chấp nhận các giá trị tương đương theo một quy tắc, phải diễn tả bằng merger phù hợp thay vì dựa vào mặc định.

### Bài 2 — distinct có giải quyết mã trùng không?

Hai record `Sale("B", 2)` và `Sale("B", 5)` được đưa qua distinct rồi toMap theo mã. Vì sao vẫn lỗi?

**Đáp án:** Hai record khác nhau theo toàn bộ các thành phần nên distinct không gộp chúng. keyMapper lại tạo cùng khóa B. Tính duy nhất của đầu vào và tính duy nhất của khóa sau ánh xạ là hai điều khác nhau.

### Bài 3 — Chọn đúng chính sách gộp

Cùng mã có các số lượng `2` và `5`. Muốn tổng giao dịch thì dùng giữ cuối hay cộng? Muốn giữ một dòng có số lượng lớn nhất thì valueMapper nên trả số hay cả dòng?

**Đáp án:** Tổng là `7` nên dùng phép cộng. Nếu cần các trường khác của dòng thắng, valueMapper giữ cả dòng bằng Function.identity và merger chọn đối tượng theo comparator. Chỉ giữ số `5` sẽ không giữ những trường còn lại.

### Bài 4 — LinkedHashMap có sắp theo khóa không?

Nguồn tuần tự gặp khóa B, A, B, C và gộp theo tổng. LinkedHashMap mặc định giữ thứ tự nào? Muốn khóa tăng dần dùng gì?

**Đáp án:** Thứ tự chèn lần đầu là B, A, C; cập nhật B không đưa nó xuống cuối. Muốn thứ tự khóa tự nhiên có thể chọn TreeMap. Không Map nào trong hai cách này tự sắp theo tổng giá trị.

### Bài 5 — Hàm gộp trả null

Nguồn tuần tự có A=10, A=20, A=30 và merger luôn trả null khi được gọi. Kết quả của A cuối cùng có phải null không?

**Đáp án:** Không. Dòng thứ hai làm merger trả null và xóa A. Dòng thứ ba gặp khóa chưa có nên thêm A=30 mà không phải gộp với một giá trị đang tồn tại. Hành vi này không phải cách lưu ánh xạ A tới null.

### Bài 6 — Đổi đích sang LinkedHashMap có nhận value null không?

Một valueMapper trả null. Có thể sửa lỗi chỉ bằng cách dùng LinkedHashMap::new vì LinkedHashMap cho phép null không?

**Đáp án:** Không trong các overload được minh chứng. Giá trị null vẫn không đáp ứng quá trình tích lũy của collector, đặc biệt ngữ nghĩa Map.merge yêu cầu giá trị đưa vào khác null. Cần chính sách dữ liệu thiếu rõ ràng thay vì chỉ đổi lớp Map.

### Bài 7 — Phép trừ và parallelStream

Merger là `(a, b) -> a - b`. Vì sao không thể mặc định kết quả luôn như trừ liên tiếp từ trái sang phải khi chuyển sang song song?

**Đáp án:** Phép trừ không kết hợp: `(10-3)-2` khác `10-(3-2)`. Các Map từng phần và bước gộp có thể nhóm giá trị theo cách khác. Nếu yêu cầu thật sự là cập nhật trạng thái lần lượt, chọn một mô hình tuần tự tường minh.

### Bài 8 — Đổi comparator làm các khóa gộp lại

Nguồn có chuỗi a và A, hai khóa khác nhau theo equals. Khi đích là TreeMap dùng comparator không phân biệt hoa thường, vì sao chúng có thể gộp vào cùng vị trí?

**Đáp án:** TreeMap nhận biết tương đương khóa qua comparator; comparator đó trả 0 cho hai chuỗi. Nó không nhất quán với equals của String nên cần hiểu rõ giới hạn hợp đồng Map. Nếu nghiệp vụ coi hai dạng là một mã, chuẩn hóa khóa theo quy tắc rõ ràng thường giúp mô hình nhất quán hơn.

## 15. Những quyết định cần công bố trong mã

Trước một lời gọi toMap, hãy xác định miền khóa sau chuẩn hóa, chính sách trùng, ý nghĩa giá trị và loại Map đích. Nếu khóa lặp là lỗi dữ liệu, để lỗi được bộc lộ; nếu là dữ liệu hợp lệ, chọn gộp hoặc phân nhóm phù hợp.

Sau đó xem thứ tự đầu vào, tính kết hợp của merger, null và quyền sở hữu đối tượng kết quả. Không dùng một lambda giữ đầu/cuối như cách chữa lỗi chung cho mọi dữ liệu, và không trông chờ factory tự giải quyết những yêu cầu nó không biểu diễn.

Đọc đủ keyMapper, valueMapper, mergeFunction và mapFactory giúp thấy chính xác Map kết quả sẽ giữ thông tin gì, bỏ thông tin gì và tuân theo quy tắc nào khi một khóa xuất hiện nhiều lần.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="streams-spliterators-008">Stream.toList, collect và giới hạn bảo đảm về khả năng sửa</a>

<details>
<summary>Xem chi tiết</summary>

# Stream.toList, collect và giới hạn bảo đảm về khả năng sửa

## 1. Cùng kiểu List nhưng quyền sửa có thể khác nhau

Một pipeline stream có thể tạo ra `List` để phía sau đọc, bổ sung dữ liệu, sắp xếp hoặc trả về từ một API. Chọn thao tác kết thúc không chỉ là chọn cách viết ngắn hơn; đó còn là chọn hợp đồng của đối tượng kết quả.

Ví dụ, hai đoạn minh họa sau cùng có biến kiểu `List<String>`:

```java
// Đoạn minh họa; source là List<String> đã có.
List<String> first = source.stream().toList();
List<String> second = source.stream().collect(Collectors.toList());
```

Từ kiểu `List<String>`, chưa thể kết luận cả hai đều cho gọi `add`, `remove` hoặc `set`. Các thao tác sửa trong giao diện `List` là những khả năng có thể không được hỗ trợ bởi một cách triển khai cụ thể.

Cần tách ba câu hỏi:

1. **Danh sách cho sửa qua API của nó không?** Có thể thêm, xóa, thay phần tử hoặc sắp xếp tại chỗ không?
2. **Danh sách có dùng trực tiếp cấu trúc của nguồn không?** Nếu nguồn thay đổi sau khi thu thập xong, kết quả có cập nhật theo không?
3. **Các đối tượng phần tử có thể thay đổi không?** Danh sách không cho thay phần tử chưa chắc đã ngăn được việc sửa một trường bên trong phần tử.

Bài này tập trung vào các bảo đảm đó khi dùng `Stream.toList()`, `collect` và những collector tạo danh sách. Các chương trình dùng Java 17, được lưu theo đúng tên `public class`, có lệnh biên dịch và stdout để tự kiểm chứng.

## 2. Đọc hợp đồng trước khi chọn cách thu thập

| Cách tạo kết quả | Bảo đảm về quyền sửa | Kiểu triển khai | Thứ tự |
| --- | --- | --- | --- |
| `stream.toList()` | Không cho sửa danh sách; các lời gọi phương thức sửa ném `UnsupportedOperationException` | Không được bảo đảm là một lớp cụ thể | Giữ thứ tự gặp nếu stream có thứ tự |
| `stream.collect(Collectors.toList())` | Không có bảo đảm về khả năng sửa | Không có bảo đảm là `ArrayList` | Thu thập theo thứ tự gặp |
| `stream.collect(Collectors.toCollection(ArrayList::new))` | Cho sửa theo hợp đồng của `ArrayList` | Chủ động yêu cầu `ArrayList` | Thu thập theo thứ tự gặp |
| `stream.collect(Collectors.toUnmodifiableList())` | Không cho sửa danh sách | Không chọn một lớp cụ thể để phụ thuộc | Thu thập theo thứ tự gặp |
| `collect` kết hợp `collectingAndThen` | Do bước thu thập và bước hoàn tất quyết định | Do cách tạo và cách hoàn tất quyết định | Phụ thuộc collector được dùng |

### Stream.toList có cam kết không cho sửa

`Stream.toList()` trả về danh sách không cho sửa. Điều này bao gồm cả thay phần tử bằng `set`, không chỉ những thao tác làm thay đổi kích thước như `add` hoặc `remove`. Phương thức có từ Java 16. [Javadoc Stream.toList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList())

Nếu cần đổi thứ tự, có thể sắp xếp trong pipeline bằng `sorted` rồi thu thập. Nếu cần tiếp tục sửa sau khi đã thu thập, chọn rõ một kết quả cho phép sửa.

### Collectors.toList không cam kết cho sửa

`Collectors.toList()` không bảo đảm kiểu triển khai, khả năng sửa, khả năng tuần tự hóa hoặc tính an toàn đồng thời của danh sách trả về. Vì vậy, “đang chạy được `add` ở phiên bản JDK này” chưa phải hợp đồng cho mã ứng dụng. [Javadoc Collectors.toList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#toList())

Không thay điều chưa được bảo đảm bằng khẳng định ngược lại. Collector này cũng không cam kết kết quả luôn không cho sửa. Kết luận đúng là: nếu thuật toán cần một trong hai chính sách, hãy yêu cầu rõ chính sách đó.

### Muốn sửa thì yêu cầu một vùng chứa phù hợp

`Collectors.toCollection(ArrayList::new)` thể hiện trực tiếp rằng kết quả cần là `ArrayList`. Lớp này hỗ trợ thêm, xóa, thay phần tử và sắp xếp. Không phải đoán lớp nội bộ mà một collector khác hiện đang dùng.

Cũng có thể tạo bản sao cho phép sửa từ một danh sách đã có:

```java
// Đoạn minh họa; result là List<String> đã thu thập.
List<String> editable = new ArrayList<>(result);
```

Bản sao này độc lập về cấu trúc với `result`, nhưng vẫn có thể dùng chung các đối tượng phần tử. Phần 5 sẽ kiểm chứng giới hạn đó.

## 3. Thí nghiệm: cùng dữ liệu, khác quyền sửa

Lưu vào `StreamListGuaranteesDemo.java`.

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class StreamListGuaranteesDemo {
    private static void expectBlocked(String name, Runnable action) {
        try {
            action.run();
            throw new IllegalStateException("Expected rejection: " + name);
        } catch (UnsupportedOperationException e) {
            System.out.println(name + "=UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        List<String> source = List.of("B", "A", "B");
        List<String> direct = source.stream().toList();
        List<String> unspecified = source.stream().collect(Collectors.toList());
        ArrayList<String> mutable = source.stream()
                .collect(Collectors.toCollection(ArrayList::new));
        List<String> unmodifiable = source.stream()
                .collect(Collectors.toUnmodifiableList());

        System.out.println("direct=" + direct);
        System.out.println("collector=" + unspecified);
        System.out.println("explicitArrayList=" + mutable);
        System.out.println("unmodifiableCollector=" + unmodifiable);
        expectBlocked("directAdd", () -> direct.add("C"));
        expectBlocked("directSet", () -> direct.set(0, "X"));
        expectBlocked("directRemove", () -> direct.remove("B"));
        expectBlocked("directSort", () -> direct.sort(Comparator.naturalOrder()));
        expectBlocked("unmodifiableAdd", () -> unmodifiable.add("C"));

        mutable.sort(Comparator.naturalOrder());
        mutable.set(0, "A*");
        mutable.remove("B");
        mutable.add("C");
        System.out.println("mutableAfterEdits=" + mutable);
        System.out.println("source=" + source);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 StreamListGuaranteesDemo.java
java StreamListGuaranteesDemo
```

Stdout:

```text
direct=[B, A, B]
collector=[B, A, B]
explicitArrayList=[B, A, B]
unmodifiableCollector=[B, A, B]
directAdd=UnsupportedOperationException
directSet=UnsupportedOperationException
directRemove=UnsupportedOperationException
directSort=UnsupportedOperationException
unmodifiableAdd=UnsupportedOperationException
mutableAfterEdits=[A*, B, C]
source=[B, A, B]
```

Bốn kết quả ban đầu đều chứa `[B, A, B]`. Thứ tự và phần tử trùng được giữ lại; thu thập thành danh sách không tự loại trùng.

Các dòng lỗi được in có chủ đích:

- `directAdd` kiểm tra thêm phần tử.
- `directSet` kiểm tra thay phần tử tại chỉ số, dù thao tác đó không làm đổi kích thước.
- `directRemove` kiểm tra xóa.
- `directSort` kiểm tra sắp xếp tại chỗ.
- `unmodifiableAdd` kiểm tra danh sách từ `Collectors.toUnmodifiableList()`.

Tất cả lời gọi sửa tương ứng bị chặn. Chương trình bắt đúng `UnsupportedOperationException` và in nhãn cố định, nên stdout không phụ thuộc nội dung thông điệp ngoại lệ của từng bản JDK.

Biến `unspecified` được tạo bằng `Collectors.toList()` chỉ được đọc và in. Ví dụ không lấy việc thử sửa thành công trên một cách triển khai để tuyên bố có bảo đảm cho sửa.

Ngược lại, `mutable` được tạo với `ArrayList::new`, nên chương trình chủ động dựa vào hợp đồng của `ArrayList`: sắp xếp, thay giá trị đầu, xóa một `B`, thêm `C`, rồi nhận `[A*, B, C]`. Nguồn vẫn là `[B, A, B]`.

### final và List không quyết định quyền sửa ở thời gian chạy

Từ khóa `final` trên một biến tham chiếu chỉ ngăn gán biến sang đối tượng khác. Nó không làm một `ArrayList` tự trở thành danh sách không cho sửa.

Ngược lại, bỏ `final` khỏi biến chứa kết quả `toList()` không làm kết quả đó hỗ trợ `add`. Quyền sửa thuộc về hợp đồng của đối tượng danh sách, không phải khả năng gán lại biến.

### Sắp xếp trước hoặc sau phải chọn rõ

Nếu cần một kết quả đã sắp xếp và sau đó chỉ đọc, có thể dùng đoạn minh họa:

```java
// source là List<String> không có phần tử null.
List<String> sortedResult = source.stream().sorted().toList();
```

Nếu muốn tiếp tục chỉnh sửa danh sách đã thu thập, dùng một `ArrayList` kết quả rồi gọi `sort`. Không thu thập bằng `toList()` rồi mặc định lệnh `sort` sẽ chạy được.

## 4. Quyền sửa và chính sách null là hai lựa chọn riêng

Hai danh sách đều không cho sửa không có nghĩa chúng chấp nhận cùng loại dữ liệu đầu vào.

`Stream.toList()` có thể chứa phần tử `null`. Trong khi đó, `Collectors.toUnmodifiableList()` từ chối `null` và ném `NullPointerException` nếu gặp nó. Vì vậy, hai cách viết này không thể thay thế nhau chỉ dựa trên từ “không cho sửa”. [Javadoc Collectors.toUnmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#toUnmodifiableList())

| Yêu cầu | Cách diễn đạt phù hợp |
| --- | --- |
| Danh sách không cho sửa, vẫn giữ phần tử null | `stream.toList()` |
| Danh sách không cho sửa và từ chối null | `collect(Collectors.toUnmodifiableList())` |
| ArrayList cho sửa, có thể chứa null | `collect(Collectors.toCollection(ArrayList::new))` |
| Bao một kết quả ArrayList riêng thành danh sách không cho sửa, vẫn giữ null | `collectingAndThen(toCollection(ArrayList::new), Collections::unmodifiableList)` |

Cách cuối dài hơn, nhưng cho thấy một collector có thể tích lũy trong vùng chứa cho phép sửa rồi trả một đối tượng không cho sửa ở bước hoàn tất.

Lưu vào `StreamListNullPolicyDemo.java`.

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class StreamListNullPolicyDemo {
    public static void main(String[] args) {
        List<String> source = Arrays.asList("A", null, "B");
        List<String> direct = source.stream().toList();
        ArrayList<String> mutable = source.stream()
                .collect(Collectors.toCollection(ArrayList::new));
        List<String> wrapped = source.stream().collect(Collectors.collectingAndThen(
                Collectors.toCollection(ArrayList::new),
                Collections::unmodifiableList));

        System.out.println("direct=" + direct);
        System.out.println("mutable=" + mutable);
        System.out.println("wrapped=" + wrapped);

        try {
            source.stream().collect(Collectors.toUnmodifiableList());
        } catch (NullPointerException e) {
            System.out.println("unmodifiableCollector=NullPointerException");
        }
        try {
            List.copyOf(direct);
        } catch (NullPointerException e) {
            System.out.println("copyOf=NullPointerException");
        }
        try {
            wrapped.set(0, "X");
        } catch (UnsupportedOperationException e) {
            System.out.println("wrappedSet=UnsupportedOperationException");
        }

        List<String> withoutNull = source.stream()
                .filter(value -> value != null)
                .collect(Collectors.toUnmodifiableList());
        System.out.println("withoutNull=" + withoutNull);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 StreamListNullPolicyDemo.java
java StreamListNullPolicyDemo
```

Stdout:

```text
direct=[A, null, B]
mutable=[A, null, B]
wrapped=[A, null, B]
unmodifiableCollector=NullPointerException
copyOf=NullPointerException
wrappedSet=UnsupportedOperationException
withoutNull=[A, B]
```

Nguồn được tạo bằng `Arrays.asList` để chứa được `null`. Nếu dùng `List.of("A", null, "B")`, lỗi đã xảy ra ở bước tạo nguồn, chưa tới phép thu thập cần kiểm chứng.

Kết quả cho thấy:

1. `direct`, `mutable` và `wrapped` đều giữ phần tử `null`.
2. `toUnmodifiableList` từ chối đầu vào đó.
3. `List.copyOf(direct)` cũng từ chối `null`, dù `direct` vốn đã không cho sửa.
4. Lớp bao `wrapped` chặn `set`, nhưng việc không cho thay phần tử không đồng nghĩa phải loại bỏ phần tử `null` đang có.
5. Pipeline `withoutNull` chủ động lọc bỏ `null` trước khi dùng collector từ chối `null`.

Bước lọc cuối là một quyết định về dữ liệu, không phải thủ thuật luôn đúng để né lỗi. Nếu `null` là đầu vào không hợp lệ cần báo cho phía gọi, tự loại bỏ nó có thể che mất lỗi. Chỉ lọc khi yêu cầu cho phép bỏ những phần tử đó.

Cũng cần phân biệt phần tử `null` bên trong nguồn với chính biến nguồn hoặc stream là `null`. Chính sách chứa phần tử không làm một lời gọi phương thức trên tham chiếu `null` trở nên hợp lệ.

## 5. Không cho sửa danh sách không có nghĩa phần tử bất biến

Khi thu thập một stream của các đối tượng, danh sách kết quả thường chứa các tham chiếu phần tử đã đi qua pipeline. Nếu không có bước tạo đối tượng mới, việc thu thập không tự sao chép sâu từng đối tượng.

Lưu vào `ListResultAndElementsDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListResultAndElementsDemo {
    static final class Task {
        private String label;

        Task(String label) {
            this.label = label;
        }
    }

    record TaskView(String label) {}

    public static void main(String[] args) {
        Task first = new Task("draft");
        List<Task> source = new ArrayList<>(List.of(first));
        List<Task> liveView = Collections.unmodifiableList(source);
        List<Task> result = source.stream().toList();
        List<TaskView> projected = source.stream()
                .map(task -> new TaskView(task.label))
                .toList();

        source.add(new Task("later"));
        System.out.println("sourceSizeAfterAdd=" + source.size());
        System.out.println("liveViewSizeAfterAdd=" + liveView.size());
        System.out.println("resultSizeAfterAdd=" + result.size());
        System.out.println("sameElementReference=" + (result.get(0) == first));

        first.label = "done";
        System.out.println("resultElementLabel=" + result.get(0).label);
        System.out.println("projectedLabel=" + projected.get(0).label());

        source.clear();
        System.out.println("liveViewSizeAfterClear=" + liveView.size());
        System.out.println("resultSizeAfterClear=" + result.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ListResultAndElementsDemo.java
java ListResultAndElementsDemo
```

Stdout:

```text
sourceSizeAfterAdd=2
liveViewSizeAfterAdd=2
resultSizeAfterAdd=1
sameElementReference=true
resultElementLabel=done
projectedLabel=draft
liveViewSizeAfterClear=0
resultSizeAfterClear=1
```

Chương trình tạo ba cách nhìn từ dữ liệu nguồn:

| Biến | Được tạo như thế nào | Tính chất trong ví dụ |
| --- | --- | --- |
| `liveView` | `Collections.unmodifiableList(source)` | Không cho sửa qua lớp bao, nhưng đọc cấu trúc nguồn trực tiếp |
| `result` | `source.stream().toList()` | Danh sách kết quả đã thu thập, chứa tham chiếu `Task` |
| `projected` | `map` sang `TaskView`, rồi `toList` | Chứa các đối tượng biểu diễn dữ liệu được tạo trong bước biến đổi |

Sau `source.add(...)`, nguồn và `liveView` cùng có hai phần tử, nhưng `result` vẫn có một. Sau `source.clear()`, `liveView` rỗng, còn `result` vẫn có phần tử đã thu thập.

`Collections.unmodifiableList` là một khung nhìn chỉ đọc của danh sách nền. Nó không tự sao chép cấu trúc nguồn; thay đổi danh sách nền qua một tham chiếu khác vẫn được phản ánh khi đọc khung nhìn. [Javadoc Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List))

### Cấu trúc đã độc lập, đối tượng vẫn dùng chung

Dòng `sameElementReference=true` cho thấy `result.get(0)` chính là đối tượng `first`. Sửa trường `first.label` thành `done` làm giá trị đọc qua `result` cũng là `done`.

Không có lời gọi `result.set(...)` nào trong thay đổi đó. Danh sách không bị thay phần tử; chính đối tượng đang được phần tử tham chiếu đến đã đổi trạng thái.

Do vậy, câu “kết quả không cho sửa” cần được hiểu đúng phạm vi: chặn các thao tác sửa danh sách không đồng nghĩa chặn mọi thay đổi có thể nhìn thấy thông qua những phần tử của nó.

### Muốn một ảnh chụp dữ liệu phải chọn cách biểu diễn phần tử

`projected` chứa các `TaskView` được tạo khi nhãn còn là `draft`. Trường của `TaskView` trong ví dụ chỉ là một `String` bất biến, nên việc sửa `Task.label` sau đó không đổi `projectedLabel`.

Đây là một cách tách mô hình dữ liệu đầu ra khỏi đối tượng nguồn. Không suy ra mọi `record` đều bất biến sâu: nếu một record chứa trường tham chiếu đến danh sách hoặc đối tượng có thể sửa, vẫn phải thiết kế cách bảo vệ phần dữ liệu đó.

Tương tự, `new ArrayList<>(result)` tạo danh sách cho sửa nhưng không tự tạo các `Task` mới. Tạo bản sao cấu trúc và tạo bản sao trạng thái phần tử là hai công việc riêng.

### Không dùng “unmodifiable” để suy ra mọi bảo đảm khác

Một danh sách không cho sửa cũng không tự tạo chính sách phối hợp truy cập cho các đối tượng phần tử dùng chung. Khi dữ liệu được chia sẻ giữa các luồng thực thi, vẫn phải xem cách công bố dữ liệu và cách đọc hoặc sửa các đối tượng đó.

Bản thân `Stream.toList()` cũng không cam kết một lớp triển khai hay khả năng tuần tự hóa cụ thể. Không ép kiểu kết quả sang lớp nội bộ được quan sát ở một lần chạy để lấy thêm chức năng.

Kết quả có thể thuộc dạng đối tượng dựa trên giá trị; nên so sánh nội dung bằng `equals` khi đó là điều cần kiểm tra. Không xây giao thức dựa trên danh tính tham chiếu của hai kết quả `toList()`, và không dùng chính kết quả làm đối tượng khóa đồng bộ.

## 6. collect có thể tích lũy trong đối tượng sửa được nhưng trả kết quả không cho sửa

Tên “mutable reduction” mô tả cách tích lũy: các phần tử được đưa vào một vùng chứa bằng cách cập nhật vùng chứa đó. Nó không buộc đối tượng kết quả cuối cùng của mọi collector phải hỗ trợ sửa.

Có hai dạng sử dụng thường gặp:

- `collect(collector)`: collector đóng gói cách tạo vùng tích lũy, nhận phần tử, kết hợp và hoàn tất kết quả.
- `collect(supplier, accumulator, combiner)`: truyền trực tiếp cách tạo vùng chứa, đưa một phần tử vào và ghép hai phần kết quả.

Với `collectingAndThen`, sau bước thu thập còn có một hàm hoàn tất. Hàm đó có thể trả lớp bao không cho sửa. Vì vậy, nhìn thấy từ `collect` trong mã chưa đủ để kết luận kết quả có thể gọi `add`.

### Thí nghiệm thu thập song song

Lưu vào `ParallelListCollectionDemo.java`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ParallelListCollectionDemo {
    public static void main(String[] args) {
        List<Integer> source = List.of(1, 2, 3, 4, 5, 6);
        List<Integer> direct = source.parallelStream()
                .map(n -> n * 2)
                .toList();
        ArrayList<Integer> mutable = source.parallelStream()
                .map(n -> n * 2)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        List<Integer> wrapped = source.parallelStream()
                .map(n -> n * 2)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(ArrayList::new),
                        Collections::unmodifiableList));

        System.out.println("direct=" + direct);
        System.out.println("mutable=" + mutable);
        System.out.println("wrapped=" + wrapped);
        System.out.println("equalBeforeEdits="
                + (direct.equals(mutable) && direct.equals(wrapped)));

        mutable.add(14);
        System.out.println("mutableAfterAdd=" + mutable);
        System.out.println("directAfterOtherEdit=" + direct);
        try {
            wrapped.add(14);
        } catch (UnsupportedOperationException e) {
            System.out.println("wrappedAdd=UnsupportedOperationException");
        }
        System.out.println("source=" + source);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ParallelListCollectionDemo.java
java ParallelListCollectionDemo
```

Stdout:

```text
direct=[2, 4, 6, 8, 10, 12]
mutable=[2, 4, 6, 8, 10, 12]
wrapped=[2, 4, 6, 8, 10, 12]
equalBeforeEdits=true
mutableAfterAdd=[2, 4, 6, 8, 10, 12, 14]
directAfterOtherEdit=[2, 4, 6, 8, 10, 12]
wrappedAdd=UnsupportedOperationException
source=[1, 2, 3, 4, 5, 6]
```

Ba kết quả ban đầu cùng chứa `[2, 4, 6, 8, 10, 12]` theo thứ tự gặp của nguồn. Chế độ song song không tự làm danh sách đầu ra mất thứ tự hoặc trở thành danh sách cho sửa.

Ở cách thu thập ba tham số:

```text
ArrayList::new     tạo vùng chứa mới
ArrayList::add     đưa một phần tử vào vùng chứa
ArrayList::addAll  ghép phần thứ hai vào phần thứ nhất
```

Thư viện có thể tạo nhiều vùng chứa trung gian và kết hợp chúng, thay vì cho mọi bên cùng sửa một `ArrayList` bên ngoài. Hàm tạo phải trả một vùng chứa mới mỗi lần được gọi; không trả lại cùng một danh sách dùng chung từ supplier. [Javadoc Stream.collect](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#collect(java.util.function.Supplier,java.util.function.BiConsumer,java.util.function.BiConsumer))

Trong `wrapped`, collector tích lũy vào các `ArrayList` rồi bước hoàn tất trả một lớp bao `Collections.unmodifiableList`. Vùng chứa nền này được tạo riêng cho kết quả; chương trình không để lộ một tham chiếu khác để sửa nó. Vì vậy, phía nhận `wrapped` không thể thêm phần tử qua API của danh sách.

`mutable.add(14)` chạy được vì cách thu thập đã chọn rõ `ArrayList`. Sửa `mutable` không sửa `direct` hay nguồn. Gọi `wrapped.add(14)` bị chặn.

### Đừng thay collect bằng tích lũy bên ngoài

Một cách viết gọi `forEach(output::add)` với `output` là `ArrayList` bên ngoài không có cùng giao thức tích lũy và kết hợp của collector. Với parallel stream, những hành động có thể cùng cập nhật một đối tượng không được bảo vệ.

Nếu mục tiêu là tạo danh sách, dùng thao tác thu thập diễn đạt đúng chính sách đầu ra. Không tự thêm trạng thái dùng chung chỉ để có được một danh sách cho sửa.

Ngay cả khi collector tổ chức quá trình tích lũy song song an toàn, đối tượng `ArrayList` trả về sau đó vẫn có hợp đồng thông thường của `ArrayList`. Nó không tự trở thành một collection an toàn cho mọi truy cập đồng thời trong phần còn lại của ứng dụng.

## 7. Thay cách thu thập còn có thể ảnh hưởng suy luận kiểu

Khi đổi từ `collect(Collectors.toList())` sang `toList()`, ngoài quyền sửa cần kiểm tra kiểu dữ liệu mà đoạn mã đang yêu cầu.

`String` là một `CharSequence`, nhưng Java không coi `List<String>` là kiểu con của `List<CharSequence>`. Nếu coi hai kiểu danh sách này như nhau, một phía có thể thêm một loại `CharSequence` khác vào danh sách mà phía còn lại tin rằng chỉ chứa `String`. [Java Language Specification về kiểu con](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.10.2)

Lời gọi `collect` tổng quát có thể suy luận kiểu collector từ ngữ cảnh kiểu đích. Trong khi đó, `toList()` trả `List<T>` theo kiểu phần tử của stream đang có.

Lưu chương trình hợp lệ sau vào `ListCollectionTypeDemo.java`.

```java
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListCollectionTypeDemo {
    public static void main(String[] args) {
        List<CharSequence> collected =
                Stream.of("A", "B").collect(Collectors.toList());
        List<CharSequence> widened =
                Stream.<CharSequence>of("A", "B").toList();
        List<? extends CharSequence> readable =
                Stream.of("A", "B").toList();

        System.out.println("collected=" + collected);
        System.out.println("widened=" + widened);
        System.out.println("readable=" + readable);
        System.out.println("firstLength=" + readable.get(0).length());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ListCollectionTypeDemo.java
java ListCollectionTypeDemo
```

Stdout:

```text
collected=[A, B]
widened=[A, B]
readable=[A, B]
firstLength=1
```

Có ba lựa chọn khác nhau trong ví dụ:

- `collected` dùng suy luận kiểu ở lời gọi `collect`.
- `widened` yêu cầu ngay từ bước tạo stream rằng phần tử có kiểu `CharSequence`.
- `readable` dùng kiểu có ký tự đại diện để đọc những phần tử thuộc một kiểu con của `CharSequence`.

Chúng có thể in cùng nội dung, nhưng lý do các phép gán hợp lệ thuộc về hệ thống kiểu, không thuộc về quyền sửa ở thời gian chạy.

### Một thay thế máy móc có thể không biên dịch

Lưu riêng ví dụ **cố ý sai kiểu** dưới đây vào `IncompatibleToListAssignment.java`. Đây là thí nghiệm lỗi biên dịch, không phải chương trình được kỳ vọng chạy:

```java
import java.util.List;
import java.util.stream.Stream;

public class IncompatibleToListAssignment {
    public static void main(String[] args) {
        List<CharSequence> result = Stream.of("A", "B").toList();
    }
}
```

Kiểm chứng lỗi bằng:

```shell
javac --release 17 -encoding UTF-8 IncompatibleToListAssignment.java
```

Lệnh biên dịch thất bại. Phần thông báo chỉ ra nguyên nhân là:

```text
List<String> cannot be converted to List<CharSequence>
```

Đây là trích đoạn nguyên nhân từ thông báo trình biên dịch, không phải stdout của chương trình. Không có bước chạy `java` cho tệp cố ý sai này.

Cách sửa phù hợp trong tình huống đó là xác định kiểu stream cần có, chẳng hạn `Stream.<CharSequence>of(...)`, hoặc chọn kiểu biến nhận phù hợp với mục đích sử dụng. Không sửa bằng ép kiểu không được kiểm chứng hoặc chuyển sang kiểu thô để che lỗi.

### Ký tự đại diện không phải cơ chế làm danh sách chỉ đọc

`List<? extends CharSequence>` giới hạn những phép đưa giá trị vào ở mức kiểm tra kiểu; nó không phải lời hứa rằng mọi thao tác sửa danh sách đều bị cấm. Một số thao tác như xóa vẫn có thể được gọi về mặt kiểu.

Trong chương trình, `readable` không cho sửa ở thời gian chạy vì đối tượng thực được tạo bằng `toList()`, không phải vì chỉ riêng dấu `? extends` tạo ra tính chất đó.

Nếu một API cần bảo đảm kết quả không cho sửa, phải tạo và mô tả đúng kết quả theo hợp đồng. Không dùng một kiểu có ký tự đại diện để thay thế lời hứa đó.

## 8. Chọn cách tạo kết quả theo công việc tiếp theo

### Cần đọc một danh sách kết quả

Nếu chỉ cần duyệt kết quả hoặc trả danh sách không cho sửa qua API, `toList()` thường diễn đạt trực tiếp ý định. Nó giữ thứ tự gặp nếu có và vẫn giữ các lần xuất hiện trùng.

Tuy nhiên, cần xem dữ liệu có chứa đối tượng thay đổi được không. Nếu muốn một kết quả dữ liệu ổn định, chọn cách tạo phần tử đầu ra phù hợp thay vì chỉ chặn thao tác trên danh sách.

### Cần tiếp tục thêm, xóa, thay hoặc sắp xếp

Yêu cầu rõ `ArrayList` bằng `toCollection(ArrayList::new)`, hoặc tạo `new ArrayList<>(result)` khi đã có một danh sách kết quả.

Cách chọn này làm hợp đồng rõ với cả người viết và người đọc mã. Không cần phụ thuộc vào lớp triển khai ngầm của `Collectors.toList()`, và không cần thử sửa rồi bắt ngoại lệ để đoán chính sách ở thời gian chạy.

Nếu dùng phép tạo bản sao, hãy đặt tên phản ánh vai trò của bản sao trong công việc tiếp theo. Việc sao chép phải có mục đích; không cần sao chép thêm chỉ để đạt một lớp nội bộ mà chương trình không thực sự cần.

### Cần kết quả không cho sửa và từ chối null

`Collectors.toUnmodifiableList()` diễn đạt cả hai điều kiện. Nếu đầu vào có thể chứa `null`, quyết định báo lỗi, thay bằng giá trị hợp lệ hay loại bỏ phải đến từ hợp đồng nghiệp vụ.

Không đổi sang `toList()` chỉ để hết `NullPointerException` nếu việc từ chối `null` vốn là một điều kiện đúng của API.

### Cần tùy chỉnh bước hoàn tất

`collectingAndThen` cho phép thu thập rồi chuyển đổi kết quả một lần ở cuối, ví dụ bao danh sách nền riêng thành lớp không cho sửa. Quyền sửa cuối cùng phụ thuộc cả hai bước, không chỉ bộ tích lũy trung gian. [Javadoc collectingAndThen](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#collectingAndThen(java.util.stream.Collector,java.util.function.Function))

Nếu dùng lớp bao, đừng để lộ tham chiếu sửa được của danh sách nền rồi gọi đó là một ảnh chụp không thể thay đổi. Hơn nữa, đối tượng phần tử vẫn cần một chính sách riêng.

### Cần thứ tự khác với thứ tự gặp

Các cách tạo `List` không tự sắp xếp hoặc tự loại trùng. Nếu muốn sắp xếp, dùng một bước sắp xếp; nếu muốn loại trùng, mô tả rõ quy tắc loại trùng.

Nếu nguồn không có thứ tự gặp được bảo đảm, tạo `List` không tự tạo thêm một thứ tự nghiệp vụ ổn định cho các giá trị đó. Muốn kết quả có thứ tự xác định, cần chỉ định tiêu chí phù hợp.

Thứ tự đầu ra và quyền sửa là hai quyết định độc lập. Một danh sách đã sắp xếp có thể cho sửa hoặc không cho sửa tùy cách thu thập.

### Kiểm tra phiên bản khi chuyển mã

| API | Có từ Java |
| --- | --- |
| `Stream`, `Collectors.toList()` và `Collectors.toCollection(...)` | 8 |
| `Collectors.toUnmodifiableList()` | 10 |
| `Stream.toList()` | 16 |

Các lệnh trong bài dùng `--release 17`, nên đều nằm trong phạm vi hỗ trợ của Java 17. Nếu dự án phải biên dịch với Java 8 hoặc 11, không thể chỉ thay sang `Stream.toList()` rồi mong vẫn giữ được mức tương thích đó.

Một lần chuyển đổi mã cần kiểm tra cả nơi sử dụng kết quả: thao tác sửa, chính sách `null`, kiểu đích và phiên bản Java. So sánh hai dòng tạo danh sách mà không đọc các thao tác theo sau có thể bỏ sót thay đổi hành vi.

## 9. Những kết luận cần tránh

| Kết luận sai | Cách hiểu đúng |
| --- | --- |
| Kết quả có kiểu `List` thì phải gọi được `add` | Giao diện không bảo đảm mọi cách triển khai hỗ trợ sửa |
| `collect(Collectors.toList())` luôn trả ArrayList cho sửa | Kiểu triển khai và khả năng sửa không được API bảo đảm |
| `toList()` chỉ cấm thay đổi kích thước | Các thao tác sửa như `set` và `sort` cũng bị chặn |
| Mọi cách thu thập bằng collect đều cho sửa | Bước hoàn tất có thể trả kết quả không cho sửa |
| Hai kết quả không cho sửa có cùng chính sách null | `toList` và `toUnmodifiableList` khác nhau ở điểm này |
| Danh sách không cho sửa là bản sao sâu | Các đối tượng phần tử có thể vẫn dùng chung |
| Lớp bao unmodifiable không đổi khi nguồn đổi | Khung nhìn đọc danh sách nền và có thể phản ánh thay đổi từ đường khác |
| Tạo ArrayList từ kết quả sẽ sao chép từng đối tượng | Nó sao chép cấu trúc chứa các tham chiếu, không tự sao chép sâu |
| `final List` làm danh sách bất biến | `final` điều khiển phép gán lại biến tham chiếu |
| Parallel stream trả kết quả cho sửa hoặc không có thứ tự | Các điều kiện đó vẫn do thao tác và collector cụ thể quyết định |
| Collector dùng ArrayList trung gian thì kết quả luôn sửa được | Hàm hoàn tất có thể bao hoặc chuyển đổi kết quả |
| Đổi collect sang toList chỉ là rút gọn cú pháp | Có thể đổi quyền sửa, tính tương thích kiểu hoặc yêu cầu phiên bản |
| `List<? extends T>` tự bảo đảm không cho sửa | Ký tự đại diện giải quyết quan hệ kiểu, không tạo lớp bao bảo vệ |
| Có thể đồng bộ trên danh tính kết quả toList | API không cho phép phụ thuộc vào danh tính của kết quả theo cách đó |

Đối với một API trả danh sách, lời mô tả hữu ích cần nêu kết quả có cho sửa không, có chấp nhận `null` không, thứ tự nào được giữ và dữ liệu phần tử được bảo vệ tới mức nào. Chỉ ghi kiểu `List<T>` chưa truyền đạt đủ các điều kiện này.

## 10. Thực hành kiểm chứng hợp đồng

### Bài 1: sửa sau khi thu thập

Một phương thức tạo kết quả bằng `source.stream().toList()`, rồi gọi `result.add(extra)`. Thay sang `collect(Collectors.toList())` có đủ để bảo đảm thuật toán sẽ luôn sửa được kết quả không?

**Đối chiếu:**

Không. `Collectors.toList()` không có cam kết cho sửa. Nếu thuật toán cần thêm phần tử, yêu cầu rõ `ArrayList` bằng `toCollection(ArrayList::new)`, hoặc tạo một bản sao `ArrayList` trước giai đoạn sửa.

### Bài 2: thay phần tử không đổi size

Kết quả của `toList()` có ba phần tử. Gọi `set(0, value)` có hợp lệ vì kích thước vẫn bằng ba không?

**Đối chiếu:**

Không. Thay phần tử vẫn là thao tác sửa danh sách và bị chặn bằng `UnsupportedOperationException`. “Không cho sửa” mạnh hơn “không cho đổi kích thước”.

### Bài 3: đầu vào có null

Pipeline đang dùng `toList()` và dữ liệu có một phần tử `null`. Đổi sang `collect(Collectors.toUnmodifiableList())` có giữ nguyên hành vi chỉ vì cả hai không cho sửa không?

**Đối chiếu:**

Không. Collector thứ hai từ chối `null`. Cần quyết định có giữ phần tử `null`, báo lỗi hay biến đổi dữ liệu theo hợp đồng.

### Bài 4: nguồn và phần tử cùng thay đổi

Thu thập một `List<Task>` bằng `toList()`. Sau khi hoàn tất, thêm một `Task` mới vào nguồn, rồi đổi nhãn của một `Task` đã nằm trong kết quả. Hai thay đổi có tác động giống nhau không?

**Đối chiếu:**

Không. Thêm vào cấu trúc nguồn không tự thêm phần tử vào danh sách kết quả đã thu thập. Nhưng sửa trạng thái đối tượng `Task` đang dùng chung có thể được nhìn thấy qua phần tử trong kết quả.

Muốn một ảnh chụp dữ liệu độc lập, cần tạo phần tử đầu ra phù hợp, như `TaskView` chứa nhãn `String` trong chương trình.

### Bài 5: vùng tích lũy và kết quả cuối

Một collector tích lũy vào `ArrayList`, sau đó bước hoàn tất gọi `Collections.unmodifiableList`. Phía gọi có được thêm vào danh sách trả về vì biết bên trong là ArrayList không?

**Đối chiếu:**

Không. API của kết quả cuối bị lớp bao chặn các thao tác sửa. Biết vùng chứa trung gian cho sửa không thay đổi hợp đồng của đối tượng đã trả về.

Nếu một tham chiếu khác tới danh sách nền bị để lộ, cần đánh giá đường sửa đó riêng; lớp bao không tự cắt quan hệ với dữ liệu nền.

### Bài 6: đổi cách viết và lỗi kiểu

Một phép gán vào `List<CharSequence>` biên dịch với `Stream.of("A", "B").collect(Collectors.toList())`. Đổi đuôi sang `toList()` thì lỗi. Có phải vì kết quả không cho sửa nên Java từ chối phép gán không?

**Đối chiếu:**

Không. Đây là vấn đề suy luận kiểu và quan hệ giữa `List<String>` với `List<CharSequence>`. Có thể yêu cầu kiểu stream là `CharSequence` ngay từ đầu hoặc chọn kiểu biến nhận phù hợp. Quyền sửa là một hợp đồng khác.

### Bài 7: chọn hai phiên bản kết quả

Từ danh sách số `[3, 1, 3, 2]`, hãy tạo:

1. Một danh sách sắp tăng dần, giữ phần tử trùng và không cho sửa.
2. Một `ArrayList` sắp tăng dần, giữ phần tử trùng và cho phép thêm `4` sau khi thu thập.

**Đối chiếu về cách làm:**

Phiên bản đầu có thể dùng `sorted().toList()`, cho `[1, 2, 3, 3]`. Phiên bản sau dùng `sorted().collect(Collectors.toCollection(ArrayList::new))`, rồi thêm `4` để có `[1, 2, 3, 3, 4]`.

Khi kiểm chứng, đọc lại nguồn để xác nhận không bị sắp xếp tại chỗ. Đồng thời giải thích vì sao việc giữ thứ tự kết quả và việc cho phép sửa là hai quyết định được lựa chọn riêng.


</details>

- [Quay lại đầu trang](#back-to-top)
