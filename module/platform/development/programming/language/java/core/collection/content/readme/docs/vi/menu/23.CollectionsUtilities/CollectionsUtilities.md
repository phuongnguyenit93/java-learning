<a id="back-to-top"></a>

# Tiện ích Collections

## Menu
- [sort, reverse và shuffle](#collections-utilities-001)
- [binarySearch và điều kiện danh sách đã được sắp xếp](#collections-utilities-002)
- [min, max, frequency và disjoint](#collections-utilities-003)
- [swap, rotate và replaceAll](#collections-utilities-004)
- [fill và nCopies: thay phần tử so với tạo danh sách](#collections-utilities-005)
- [copy: kích thước đích và khác biệt với tạo bản sao mới](#collections-utilities-006)
- [addAll và chuyển các đối số thành phần tử collection](#collections-utilities-007)
- [checkedCollection, checkedList, checkedSet và checkedMap](#collections-utilities-008)
- [asLifoQueue, newSetFromMap và điều kiện của cấu trúc nền](#collections-utilities-009)

## <a id="collections-utilities-001">sort, reverse và shuffle</a>

<details>
<summary>Xem chi tiết</summary>

# sort, reverse và shuffle: ba cách thay đổi thứ tự của List

Một danh sách lưu các phần tử theo vị trí, nhưng “thay đổi thứ tự” có thể phục vụ những mục đích rất khác. Sắp xếp điểm từ thấp đến cao cần một tiêu chí so sánh. Xem lại lịch sử từ cuối lên đầu chỉ cần đảo thứ tự đang có. Trộn thứ tự câu hỏi cần một phép hoán vị ngẫu nhiên.

`Collections.sort`, `Collections.reverse` và `Collections.shuffle` tương ứng với ba mục đích đó. Chúng đều làm việc trên **list được truyền vào**, trả về `void` và không tự tạo bản kết quả riêng. Vì vậy bài học gồm hai câu hỏi song song: thứ tự nào được tạo ra và ai sẽ quan sát thấy sự thay đổi.

Các ví dụ chạy được dùng Java 17. Người học cần biết `List`, `ArrayList`, tham chiếu và ý nghĩa cơ bản của `Comparator`. Bài sẽ giải thích thêm tính ổn định của sort và vai trò của seed khi shuffle.

## 1. Phân biệt mục tiêu trước khi gọi hàm

| Thao tác | Quy tắc tạo thứ tự | Có so sánh giá trị? | Một mục đích điển hình |
| --- | --- | --- | --- |
| `Collections.sort(list)` | Tăng theo thứ tự tự nhiên | Có | Xếp số từ nhỏ đến lớn |
| `Collections.sort(list, comparator)` | Theo comparator | Có | Xếp điểm giảm dần rồi mã tăng dần |
| `Collections.reverse(list)` | Vị trí `i` chuyển tới `n - 1 - i` | Không | Đảo dãy sự kiện hiện có |
| `Collections.shuffle(list)` | Trộn vị trí bằng nguồn ngẫu nhiên mặc định | Không | Trộn dãy cho một lượt thực hành |
| `Collections.shuffle(list, random)` | Trộn vị trí bằng nguồn ngẫu nhiên được chỉ định | Không | Tái hiện một thí nghiệm với seed đã biết |

Cả ba nhóm thao tác giữ nguyên số phần tử và số lần xuất hiện của từng phần tử. Chúng đổi vị trí, không loại trùng, không tạo bản sao sâu và không tự sửa các trường nghiệp vụ bên trong phần tử.

Ví dụ dãy `[4, 1, 3, 1, 2]`:

- Sort tăng tạo `[1, 1, 2, 3, 4]`.
- Reverse dãy ban đầu tạo `[2, 1, 3, 1, 4]`.
- Shuffle tạo một hoán vị, vẫn có hai số 1 và một lần mỗi số 2, 3, 4.

Reverse không tự hiểu “giảm dần”. Shuffle cũng không đồng nghĩa “thứ tự chắc chắn khác lần trước”. Chọn thao tác theo hậu điều kiện thật sự cần có.

## 2. Đổi ngay list đầu vào và giữ quyền sở hữu rõ ràng

### Ba hàm đều trả `void`

Đây là mã minh họa trong một phương thức:

~~~java
List<Integer> values = new ArrayList<>(List.of(3, 1, 2));
Collections.sort(values);
~~~

Sau lời gọi, `values` biểu diễn `[1, 2, 3]`. Không có list mới được trả từ `sort` để gán cho một biến khác. `reverse` và `shuffle` cũng có cách dùng như vậy.

Nếu hai biến cùng trỏ tới list này, cả hai đều nhìn thấy thứ tự mới:

~~~java
List<Integer> alias = values;
Collections.reverse(values);
// alias cũng nhìn thấy [3, 2, 1].
~~~

Đây là tác dụng của cùng một đối tượng list, không phải việc thuật toán tự tìm và sửa mọi biến trong chương trình.

### Tạo bản làm việc khi cần giữ thứ tự gốc

~~~java
List<Integer> original = List.of(3, 1, 2);
List<Integer> working = new ArrayList<>(original);
Collections.sort(working);
~~~

`original` vẫn giữ thứ tự cũ, còn `working` có thứ tự đã sắp xếp. Hàm tạo list sao chép các tham chiếu phần tử; nó không tự nhân bản từng đối tượng.

Với phần tử là đối tượng sửa được, bản làm việc riêng bảo vệ **thứ tự list nguồn**, không tự bảo vệ mọi trạng thái phần tử. Nếu comparator hoặc mã khác sửa chính đối tượng ấy, các list chia sẻ phần tử có thể cùng nhìn thấy thay đổi.

Cần làm rõ hợp đồng phương thức: “sắp xếp list được truyền vào” hay “trả một list đã sắp xếp và giữ nguyên đầu vào”. Không để người gọi phải đoán từ tên một biến cục bộ.

### List cần hỗ trợ thay vị trí, không nhất thiết cần đổi kích thước

Những thao tác này không cần thêm hoặc xóa phần tử để hoàn thành nhiệm vụ, nhưng cần quyền thay các giá trị ở vị trí của list hoặc qua iterator.

Một list do `Arrays.asList(array)` tạo có kích thước cố định nhưng hỗ trợ thay phần tử nên có thể sort, reverse và shuffle. Vì nó dựa trên mảng, thứ tự trong chính mảng cũng thay đổi. [Hợp đồng Arrays.asList, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

List do `List.of` hoặc `List.copyOf` tạo không cho sửa; `Collections.unmodifiableList` cũng không cấp quyền sửa qua view. Nếu cần xử lý thứ tự, tạo bản làm việc sửa được phù hợp. [Các list không sửa được trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#unmodifiable).

Không dùng một thao tác không có tác dụng trên list rỗng hay một phần tử để đoán quyền sửa. Một thuật toán có thể không phải gọi bước thay vị trí trong trường hợp ấy; kết quả “không ném lỗi” chưa chứng minh list hỗ trợ xử lý tổng quát.

## 3. `sort`: tạo thứ tự bằng quy tắc so sánh

### Thứ tự tự nhiên

`Collections.sort(list)` dùng thứ tự tự nhiên của các phần tử. Các phần tử phải so sánh được với nhau theo hợp đồng `Comparable` phù hợp.

~~~java
List<Integer> scores = new ArrayList<>(List.of(8, 3, 8, 5));
Collections.sort(scores);
~~~

Kết quả là `[3, 5, 8, 8]`. Hai số 8 vẫn còn; sort không là phép loại trùng.

Dùng generic đúng kiểu giúp tránh đưa các loại không so sánh được vào cùng list. Nếu lách kiểu qua raw type rồi trộn các loại không tương thích, không nên chờ sort tự tìm một quy tắc hợp lý; quá trình so sánh có thể ném `ClassCastException`.

### Comparator mô tả quy tắc cho một lần sắp xếp

Với một đối tượng kết quả thi, có thể muốn sắp xếp theo điểm thay vì thứ tự tự nhiên của chính lớp:

~~~java
Comparator<Attempt> byPoints =
        Comparator.comparingInt(Attempt::points);

Collections.sort(attempts, byPoints);
~~~

Đây là đoạn minh họa với kiểu `Attempt` và biến `attempts` đã tồn tại; chương trình đầy đủ bên dưới khai báo cả hai.

`compare(a, b)` trả số âm khi `a` đứng trước `b`, 0 khi hai phần tử bằng nhau theo tiêu chí sắp xếp, số dương khi `a` đứng sau `b`. Số trả về không phải vị trí đích và cũng không phải một giá trị boolean.

Không dùng phép trừ số nguyên như `a.points() - b.points()` làm cách so sánh tổng quát; phép trừ có thể tràn. `Integer.compare` hoặc `Comparator.comparingInt` biểu đạt việc so sánh mà không dùng phép trừ rủi ro ấy.

### Ghép và đảo tiêu chí đúng tầng

~~~java
Comparator<Attempt> order =
        Comparator.comparingInt(Attempt::points)
                .reversed()
                .thenComparing(Attempt::id);
~~~

Quy tắc trên là điểm giảm dần; khi điểm bằng nhau, mã tăng dần.

Nếu đặt `reversed()` sau toàn bộ `thenComparing`, cả hai tiêu chí đều bị đảo. Điều đó có thể đúng hoặc sai tùy yêu cầu, nhưng hai cách viết không tương đương.

Comparator phải nhất quán: chẳng hạn, nếu `a` đứng sau `b` và `b` đứng sau `c`, quan hệ phải có tính bắc cầu để `a` cũng đứng sau `c`. Không trả kết quả ngẫu nhiên, không thay tiêu chí giữa chừng và không sửa khóa so sánh trong khi sort.

Một comparator vi phạm hợp đồng có thể dẫn đến kết quả không đúng hoặc `IllegalArgumentException` nếu vi phạm được phát hiện. Không dựa vào việc “chạy một lần chưa ném lỗi” để coi comparator là hợp lệ. [Hợp đồng Comparator, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html).

### `List.sort` và `Collections.sort`

Với comparator, có thể viết `list.sort(comparator)`. `Collections.sort` của Java 17 chuyển việc thực hiện sang `List.sort` theo tài liệu triển khai.

Hai cách gọi đều nhằm sắp xếp list hiện có. Việc chọn cú pháp không tạo một bản sao đầu vào và không thay yêu cầu về tính sửa được.

Không biến thuật toán cụ thể thành hợp đồng bất biến của mọi list tùy chỉnh. Bảo đảm quan trọng khi sử dụng là kết quả theo quy tắc so sánh và tính ổn định; các lớp có thể có cách thực hiện riêng đáp ứng hợp đồng. [API List.sort, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator)).

## 4. Tính ổn định của sort và bẫy sort rồi reverse

### “Bằng nhau” trong sort là comparator trả 0

Giả sử dữ liệu đầu vào theo đúng thứ tự:

~~~text
B:2, D:1, A:2, C:1
~~~

Ký hiệu là `mã:điểm`. Khi chỉ so theo điểm, B và A được xem là bằng nhau theo tiêu chí; D và C cũng như vậy, dù các đối tượng không bằng nhau về mọi trường.

Sort **ổn định** (stable sort) giữ thứ tự tương đối ban đầu của các phần tử mà comparator xem là bằng nhau.

Sort tăng theo điểm cho:

~~~text
D:1, C:1, B:2, A:2
~~~

D vẫn đứng trước C; B vẫn đứng trước A. `Collections.sort` bảo đảm tính ổn định. [API Collections.sort, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#sort(java.util.List,java.util.Comparator)).

### Đảo toàn dãy cũng đảo thứ tự trong từng nhóm bằng điểm

Reverse kết quả tăng ở trên cho:

~~~text
A:2, B:2, C:1, D:1
~~~

Điểm đã giảm dần, nhưng B/A và D/C đều đổi thứ tự. Nếu yêu cầu là “điểm giảm dần, người có cùng điểm giữ thứ tự tiếp nhận”, sort tăng rồi reverse không đáp ứng.

Cách đúng cho yêu cầu ấy là sort trực tiếp với comparator giảm dần. Tính ổn định của sort giữ thứ tự gốc trong các nhóm bằng điểm:

~~~text
B:2, A:2, D:1, C:1
~~~

Nếu yêu cầu thật sự là “điểm giảm, khi bằng điểm thì mã tăng”, hãy ghi cả tiêu chí mã vào comparator. Khi ấy việc giữ thứ tự tiếp nhận không còn là tiêu chí cho những phần tử khác mã.

### Ổn định không tự tạo một thứ tự đầu vào xác định

Nếu list được xây từ một nguồn không bảo đảm thứ tự duyệt, giữ thứ tự ban đầu trong các nhóm bằng nhau chỉ giữ thứ tự đã quan sát ở lần xây đó. Nó không tự tạo một quy tắc phụ ổn định giữa các lần chạy.

Khi báo cáo cần kết quả xác định, bổ sung tiêu chí phá hòa có ý nghĩa như mã duy nhất. Khi thứ tự tiếp nhận có ý nghĩa, phải lưu giữ chính thứ tự ấy từ đầu.

## 5. `reverse`: ánh xạ vị trí, không so sánh nội dung

Với `n` phần tử, reverse đưa phần tử cũ ở chỉ số `i` tới chỉ số `n - 1 - i`. Có thể hiểu bằng việc đổi chỗ hai đầu rồi tiến dần vào giữa.

~~~text
trước: [a, b, c, d, e]
sau:   [e, d, c, b, a]
~~~

Phần tử giữa của dãy lẻ ở nguyên vị trí. Một dãy rỗng hay chỉ một phần tử vẫn có cùng thứ tự sau đảo. Nếu không có tác động khác ở giữa, reverse hai lần khôi phục thứ tự ban đầu.

Reverse không cần phần tử triển khai `Comparable` và không cần comparator. Nếu list cho phép phần tử `null`, thao tác chỉ đổi vị trí của `null` như các phần tử khác, không so sánh nó.

### Reverse không thay thế sort giảm dần

Với `[4, 1, 3, 1, 2]`, reverse cho `[2, 1, 3, 1, 4]`. Kết quả không tăng cũng không giảm.

Chỉ khi dãy đầu vào đã được sắp tăng theo một tiêu chí thì đảo nó mới cho thứ tự giảm theo tiêu chí đó. Ngay cả khi ấy vẫn phải xét yêu cầu thứ tự tương đối trong nhóm bằng nhau như phần trước.

### Phân biệt các tên gần giống

- `Collections.reverse(list)` sửa thứ tự các vị trí ngay trong list.
- `Collections.reverseOrder()` tạo comparator ngược thứ tự tự nhiên.
- `Collections.reverseOrder(comparator)` hoặc `comparator.reversed()` tạo quy tắc so sánh ngược.

Tạo comparator không làm list nào tự đổi thứ tự. Phải đưa quy tắc ấy cho một thao tác sort hoặc một cấu trúc có sử dụng comparator.

Nếu đang đọc mã phiên bản Java mới có một API view đảo chiều, không đánh đồng nó với `Collections.reverse`. Trọng tâm ở đây là phương thức `void` sửa list đã truyền; chương trình dùng API Java 17.

## 6. `shuffle`: hoán vị các vị trí bằng nguồn ngẫu nhiên

### Cơ chế chọn dần từ cuối

Một cách hiểu thuật toán là đi từ vị trí cuối về đầu. Ở mỗi lượt, chọn ngẫu nhiên một vị trí trong đoạn chưa chốt, bao gồm chính vị trí hiện tại, rồi đổi chỗ.

Mã sau chỉ minh họa cơ chế cho list có quyền sửa; nó không phải chương trình độc lập và không nhằm thay thế lời gọi thư viện:

~~~java
for (int remaining = values.size(); remaining > 1; remaining--) {
    int selected = random.nextInt(remaining);
    Collections.swap(values, remaining - 1, selected);
}
~~~

Ở lượt đầu của dãy dài 4, chọn trong 4 vị trí để chốt cuối dãy; lượt sau chọn trong 3 vị trí còn lại; tiếp tục với 2. Đây là ý tưởng trộn Fisher–Yates.

Việc chọn đúng miền `[0, remaining)` rất quan trọng. Một vòng lặp “mỗi vị trí đổi với một vị trí bất kỳ trong toàn dãy” không tự có cùng phân bố hoán vị.

Với nguồn lựa chọn công bằng phù hợp, thuật toán tạo các hoán vị vị trí với xác suất bằng nhau. Overload không truyền nguồn ngẫu nhiên được tài liệu mô tả là có xác suất xấp xỉ bằng nhau. Không dùng vài lần in thử để chứng minh một phân bố xác suất.

### Có thể giữ nguyên một phần hoặc toàn bộ thứ tự

Một vị trí có thể được chọn để đổi với chính nó. Hoán vị đồng nhất, nghĩa là dãy cuối trùng dãy đầu, vẫn là một kết quả hợp lệ.

Vì vậy các khẳng định sau đều sai:

- “Shuffle xong mọi phần tử phải đổi vị trí.”
- “Lần thứ hai bắt buộc khác lần thứ nhất.”
- “Cùng seed nhưng đổi đầu vào vẫn phải cho cùng dãy giá trị.”
- “Có hai phần tử giống nhau thì thuật toán sẽ loại bớt một.”

Với phần tử lặp, nhiều hoán vị vị trí có thể cho cùng dãy giá trị nhìn thấy. Nếu mọi phần tử đều bằng nhau, bạn không thể đòi quan sát một thứ tự giá trị khác dù các vị trí đã tham gia trộn.

### Không sort bằng một comparator ngẫu nhiên

Một comparator ngẫu nhiên không mô tả một quan hệ thứ tự nhất quán. Cùng một cặp có thể được so khác nhau ở các lần gọi; tính bắc cầu có thể bị phá.

Muốn trộn thì dùng shuffle. Muốn xếp theo một giá trị ưu tiên ngẫu nhiên cố định, cần xây dựng rõ các giá trị ấy và xử lý hòa theo yêu cầu; không để kết quả so sánh thay đổi mỗi lần comparator được gọi.

## 7. Seed giúp tái hiện điều gì?

~~~java
List<String> first = new ArrayList<>(original);
List<String> second = new ArrayList<>(original);

Collections.shuffle(first, new Random(17));
Collections.shuffle(second, new Random(17));
~~~

Hai nguồn `Random` được khởi tạo cùng seed; đầu vào hai list có cùng thứ tự. Trong cùng cách thực hiện, hai phép trộn tiêu thụ cùng chuỗi lựa chọn và cho cùng kết quả.

`Random` có hợp đồng rằng cùng seed và cùng chuỗi lời gọi phương thức tạo ra cùng chuỗi số. [API Random, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html).

Các điều kiện phải cùng được giữ:

1. Dữ liệu đầu vào và thứ tự ban đầu giống nhau.
2. Nguồn ngẫu nhiên cùng loại, cùng trạng thái khởi đầu.
3. Chuỗi tiêu thụ số ngẫu nhiên giống nhau.
4. Cách trộn sử dụng chuỗi ấy giống nhau.

Nếu dùng một `Random` rồi gọi shuffle hai lần liên tiếp, lần hai bắt đầu từ trạng thái đã tiến đi. Nó không tương đương tạo `new Random(seed)` lại trước mỗi lần. Dùng chung nguồn cho nhiều tác vụ cũng làm lịch sử tiêu thụ ảnh hưởng kết quả.

Seed hữu ích để ghi lại một tình huống kiểm thử và tái hiện lỗi. Nó không có nghĩa kết quả trộn là một định dạng dữ liệu lâu dài được cố định qua mọi thay đổi của thuật toán thư viện. Khi cần khôi phục chính xác một thứ tự đã công bố, lưu chính thứ tự hoặc các định danh tương ứng là yêu cầu khác với chỉ lưu seed.

`java.util.Random` tạo số giả ngẫu nhiên và không phù hợp cho yêu cầu mật mã. Bài này dùng nó cho thí nghiệm tái hiện và kiểm thử, không suy rộng thành bảo đảm cho mọi cơ chế bốc chọn có yêu cầu riêng.

## 8. `null` và các quy tắc đảo giảm dần

### Đặt chính sách `null` vào comparator

Một list có thể cho chứa `null` nhưng thứ tự tự nhiên không cung cấp vị trí hợp lệ cho `null` khi cần so sánh nó với số. Nếu dữ liệu có `null`, dùng comparator có chính sách rõ:

~~~java
Comparator<Integer> ascendingNullLast =
        Comparator.nullsLast(Comparator.naturalOrder());

Collections.sort(values, ascendingNullLast);
~~~

Với dữ liệu `[3, null, 1]`, kết quả là `[1, 3, null]`.

Không tạo nguồn thử bằng `List.of(3, null, 1)` vì factory ấy đã từ chối `null` trước khi đến bước sort. Chương trình dùng `Arrays.asList` rồi sao chép sang `ArrayList` để kiểm tra đúng bước sắp xếp.

Truyền comparator `null` cho `Collections.sort(list, null)` có nghĩa dùng thứ tự tự nhiên; nó không có nghĩa tự biết cách xếp các phần tử `null`.

### Đảo toàn quy tắc khác với đảo phần giá trị không `null`

Nếu reverse toàn list `[1, 3, null]`, kết quả là `[null, 3, 1]`. Muốn số giảm dần nhưng `null` vẫn cuối, hãy dùng:

~~~java
Comparator<Integer> descendingNullLast =
        Comparator.nullsLast(Comparator.reverseOrder());
~~~

Khi ấy kết quả là `[3, 1, null]`.

Tương tự, gọi `reversed()` trên toàn comparator đã có `nullsLast` đảo cả quy tắc đặt `null`. Phải chọn đúng tầng đảo theo yêu cầu.

## 9. Chỉ xử lý một đoạn bằng `subList`

Một list có thể có phần đầu/cuối cần giữ nguyên, còn đoạn ở giữa cần xếp:

~~~java
List<String> items =
        new ArrayList<>(List.of("left", "c", "a", "b", "right"));

Collections.sort(items.subList(1, 4));
~~~

`subList(1, 4)` gồm chỉ số 1, 2, 3; cận cuối 4 không được lấy. Nó là view trên đoạn của list nguồn, nên sắp xếp view thay các vị trí tương ứng trong nguồn.

Kết quả nguồn là `[left, a, b, c, right]`. Có thể reverse hoặc shuffle chính view ấy nếu các quyền sửa được hỗ trợ.

Đừng tưởng `subList` là một bản sao để thử nghiệm riêng. Nếu cần tách khỏi nguồn, tạo `new ArrayList<>(source.subList(from, to))` trước khi xử lý.

Trong khi sử dụng view, không tự thêm/xóa cấu trúc nguồn qua một đường khác rồi tiếp tục dựa vào view cũ. Quyền sở hữu và vòng đời của view vẫn cần được kiểm soát; thuật toán đổi thứ tự không loại bỏ hợp đồng ấy. [API List.subList, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#subList(int,int)).

## 10. Chương trình hoàn chỉnh và stdout chính xác

Lưu mã sau vào **`ListOrderingDemo.java`**. Kiểu `Attempt` là record có hai trường; chương trình chỉ dùng số và chuỗi ổn định làm dữ liệu so sánh.

Stdout không chứa một dãy trộn ngẫu nhiên mặc định cần đoán trước. Thay vào đó, phần shuffle kiểm tra số lần xuất hiện của từng giá trị, tính bằng nhau khi dùng hai nguồn cùng seed, và việc nguồn ban đầu còn nguyên.

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class ListOrderingDemo {
    record Attempt(String id, int points) {
        @Override
        public String toString() {
            return id + ":" + points;
        }
    }

    private static Map<String, Integer> counts(List<String> values) {
        Map<String, Integer> result = new TreeMap<>();
        for (String value : values) {
            result.merge(value, 1, Integer::sum);
        }
        return result;
    }

    private static void rejects(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " unexpectedly succeeded");
        } catch (UnsupportedOperationException ex) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        List<Integer> source = List.of(4, 1, 3, 1, 2);
        List<Integer> reversed = new ArrayList<>(source);
        Collections.reverse(reversed);
        List<Integer> ascending = new ArrayList<>(source);
        Collections.sort(ascending);
        List<Integer> descending = new ArrayList<>(source);
        Collections.sort(descending, Comparator.reverseOrder());
        System.out.println("numbers.source=" + source);
        System.out.println("numbers.reversed=" + reversed);
        System.out.println("numbers.asc=" + ascending);
        System.out.println("numbers.desc=" + descending);

        List<Attempt> attempts = List.of(
                new Attempt("B", 2),
                new Attempt("D", 1),
                new Attempt("A", 2),
                new Attempt("C", 1));
        Comparator<Attempt> byPoints =
                Comparator.comparingInt(Attempt::points);
        List<Attempt> stableAscending = new ArrayList<>(attempts);
        Collections.sort(stableAscending, byPoints);
        List<Attempt> reverseAfterSort = new ArrayList<>(stableAscending);
        Collections.reverse(reverseAfterSort);
        List<Attempt> stableDescending = new ArrayList<>(attempts);
        Collections.sort(stableDescending, byPoints.reversed());
        List<Attempt> descendingThenId = new ArrayList<>(attempts);
        Collections.sort(descendingThenId,
                byPoints.reversed().thenComparing(Attempt::id));
        System.out.println("attempts.original=" + attempts);
        System.out.println("attempts.asc=" + stableAscending);
        System.out.println("attempts.ascThenReverse=" + reverseAfterSort);
        System.out.println("attempts.descStable=" + stableDescending);
        System.out.println("attempts.descThenId=" + descendingThenId);

        List<Integer> nullable =
                new ArrayList<>(Arrays.asList(3, null, 1));
        Collections.sort(nullable,
                Comparator.nullsLast(Comparator.naturalOrder()));
        System.out.println("nulls.ascLast=" + nullable);
        Collections.reverse(nullable);
        System.out.println("nulls.reverseWhole=" + nullable);
        List<Integer> descendingNullLast =
                new ArrayList<>(Arrays.asList(3, null, 1));
        Collections.sort(descendingNullLast,
                Comparator.nullsLast(Comparator.reverseOrder()));
        System.out.println("nulls.descLast=" + descendingNullLast);

        Integer[] backing = {3, 1, 2};
        List<Integer> fixed = Arrays.asList(backing);
        Collections.sort(fixed);
        System.out.println("fixed.afterSort=" + Arrays.toString(backing));
        Collections.reverse(fixed);
        System.out.println("fixed.afterReverse=" + Arrays.toString(backing));
        Collections.shuffle(fixed, new Random(17));
        List<Integer> fixedCheck = new ArrayList<>(fixed);
        Collections.sort(fixedCheck);
        System.out.println("fixed.shufflePreserves="
                + fixedCheck.equals(List.of(1, 2, 3)));

        List<String> window =
                new ArrayList<>(List.of("left", "c", "a", "b", "right"));
        Collections.sort(window.subList(1, 4));
        System.out.println("window.afterSort=" + window);
        Collections.reverse(window.subList(1, 4));
        System.out.println("window.afterReverse=" + window);

        List<String> original = List.of("A", "B", "A", "C", "D");
        List<String> first = new ArrayList<>(original);
        List<String> second = new ArrayList<>(original);
        Collections.shuffle(first, new Random(17));
        Collections.shuffle(second, new Random(17));
        System.out.println("shuffle.sameSeed=" + first.equals(second));
        if (!counts(first).equals(counts(original))) {
            throw new AssertionError("shuffle changed multiplicities");
        }
        System.out.println("shuffle.counts=" + counts(first));
        System.out.println("shuffle.source=" + original);
        List<String> defaultRandom = new ArrayList<>(original);
        Collections.shuffle(defaultRandom);
        System.out.println("shuffle.defaultPreserves="
                + counts(defaultRandom).equals(counts(original)));

        List<Integer> linked =
                new LinkedList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8));
        Collections.shuffle(linked, new Random(17));
        Collections.sort(linked);
        System.out.println("linked.afterShuffleThenSort=" + linked);

        List<Integer> twice = new ArrayList<>(source);
        Collections.reverse(twice);
        Collections.reverse(twice);
        System.out.println("reverse.twice=" + twice.equals(source));

        List<Integer> readonly = List.of(3, 1, 2);
        rejects("sort.readonly", () -> Collections.sort(readonly));
        rejects("reverse.readonly", () -> Collections.reverse(readonly));
        rejects("shuffle.readonly",
                () -> Collections.shuffle(readonly, new Random(17)));

        List<String> empty = new ArrayList<>();
        Collections.sort(empty);
        Collections.reverse(empty);
        Collections.shuffle(empty, new Random(17));
        System.out.println("empty.size=" + empty.size());
        List<String> single = new ArrayList<>(List.of("only"));
        Collections.sort(single);
        Collections.reverse(single);
        Collections.shuffle(single, new Random(17));
        System.out.println("single.value=" + single);
    }
}
~~~

Biên dịch và chạy tại thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 ListOrderingDemo.java
java ListOrderingDemo
~~~

Cần JDK 17 trở lên cho lệnh biên dịch này. Mã đã được biên dịch với `--release 17` và chạy trên OpenJDK 24.0.2, thoát thành công với stdout chính xác sau:

~~~text
numbers.source=[4, 1, 3, 1, 2]
numbers.reversed=[2, 1, 3, 1, 4]
numbers.asc=[1, 1, 2, 3, 4]
numbers.desc=[4, 3, 2, 1, 1]
attempts.original=[B:2, D:1, A:2, C:1]
attempts.asc=[D:1, C:1, B:2, A:2]
attempts.ascThenReverse=[A:2, B:2, C:1, D:1]
attempts.descStable=[B:2, A:2, D:1, C:1]
attempts.descThenId=[A:2, B:2, C:1, D:1]
nulls.ascLast=[1, 3, null]
nulls.reverseWhole=[null, 3, 1]
nulls.descLast=[3, 1, null]
fixed.afterSort=[1, 2, 3]
fixed.afterReverse=[3, 2, 1]
fixed.shufflePreserves=true
window.afterSort=[left, a, b, c, right]
window.afterReverse=[left, c, b, a, right]
shuffle.sameSeed=true
shuffle.counts={A=2, B=1, C=1, D=1}
shuffle.source=[A, B, A, C, D]
shuffle.defaultPreserves=true
linked.afterShuffleThenSort=[1, 2, 3, 4, 5, 6, 7, 8]
reverse.twice=true
sort.readonly=UnsupportedOperationException
reverse.readonly=UnsupportedOperationException
shuffle.readonly=UnsupportedOperationException
empty.size=0
single.value=[only]
~~~

### Giải thích các nhóm kết quả

**`numbers`:** reverse được áp dụng lên bản sao của dãy chưa sort nên kết quả không giảm dần. Hai bản sort tăng và giảm dùng tiêu chí phù hợp. Nguồn có hai số 1 và các bản đều giữ số lần xuất hiện.

**`attempts`:** sort tăng giữ D trước C, B trước A. Reverse kết quả ấy đảo các nhóm hòa. Sort giảm trực tiếp từ đầu vào giữ B trước A, D trước C. Khi bổ sung tiêu chí mã tăng, thứ tự hòa được xác định bởi mã thay vì thứ tự gốc.

**`nulls`:** reverse toàn dãy đưa `null` từ cuối lên đầu; comparator giảm dành riêng cho phần không `null` giữ `null` cuối như yêu cầu.

**`fixed`:** list có kích thước cố định vẫn cho thay vị trí. Hai dòng đầu in lại mảng nguồn để chứng minh tác động qua view. Phần shuffle kiểm tra lại tập phần tử có số lần xuất hiện, không khẳng định một hoán vị cụ thể.

**`window`:** sort và reverse đoạn giữa tác động vào cùng list nguồn, còn hai phần tử biên giữ nguyên.

**`shuffle`:** hai phép trộn cùng điều kiện cho kết quả bằng nhau. Bảng đếm có hai A và một B, C, D. Phép trộn mặc định cũng giữ số lần xuất hiện dù dãy giá trị ngẫu nhiên không được in. Nguồn `original` không đổi vì các phép trộn dùng bản sao.

**`linked`:** shuffle cũng làm việc với `LinkedList` sửa được. Sort sau đó phục hồi thứ tự tăng của các giá trị; dòng này kiểm tra nội dung, không là một phép đo hiệu năng.

**`reverse.twice`:** hai lần đảo liên tiếp phục hồi dãy ban đầu.

**Các dòng ngoại lệ:** list do `List.of` tạo không hỗ trợ thay vị trí. Chương trình kiểm tra riêng từng thuật toán trên dữ liệu có nhiều phần tử, không dùng trường hợp không cần thao tác để đoán quyền sửa.

**`empty` và `single`:** các list làm việc sửa được ở hai biên 0 và 1 phần tử vẫn hợp lệ. Không cần viết nhánh đặc biệt chỉ để tránh một thao tác đổi thứ tự trên chúng.

## 11. Cơ chế, chi phí và giới hạn cần biết

### Sort phụ thuộc cả list lẫn comparator

Với các list tiêu chuẩn thường dùng, sắp xếp so sánh thường có chi phí cỡ `O(n log n)`; cách triển khai thích nghi có thể tận dụng dữ liệu đã có trật tự. Không lấy công thức ấy làm lời hứa cho mọi lớp list tùy chỉnh hoặc comparator có công việc tốn kém.

Nếu mỗi lần so sánh lại đọc tệp, gọi dịch vụ hoặc tính một giá trị đắt, chi phí comparator có thể chi phối tổng thời gian. Nên xác định khóa so sánh ổn định trước khi sort nếu mô hình cho phép.

Tài liệu `List.sort` mô tả việc dùng mảng trung gian ở cách thực hiện mặc định để tránh sắp xếp dựa trên truy cập chỉ số chậm của list liên kết. Nó cũng mô tả thuật toán thích nghi, ổn định. Đây là thông tin về cách thực hiện, không phải yêu cầu mọi list phải giữ một lớp thuật toán cụ thể.

### Reverse và shuffle có công việc tuyến tính trong triển khai tiêu chuẩn

Reverse cần đổi các cặp vị trí đối xứng. Shuffle cần chốt dần các vị trí. `Collections` mô tả hai thao tác với thời gian tuyến tính.

Với list lớn không có `RandomAccess`, cách thực hiện shuffle có thể đưa dữ liệu sang mảng, trộn rồi ghi trở lại, tránh truy cập chỉ số lặp dẫn tới chi phí bậc hai. Vì thế “sửa list được truyền vào” không có nghĩa “không bao giờ cấp phát bộ nhớ tạm”. [Cơ chế shuffle trong Collections, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#shuffle(java.util.List,java.util.Random)).

Đối với `ArrayList`, truy cập vị trí có chi phí hằng theo hợp đồng của lớp. Đối với `LinkedList`, truy cập bằng chỉ số phải đi qua các nút. Không tự viết một vòng `get(i)`/`set(i)` rồi mặc định nó có cùng chi phí trên cả hai. [ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html), [LinkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html).

### Không có bảo đảm rollback nếu thao tác thất bại

Một comparator có thể ném lỗi giữa quá trình so sánh; một list tùy chỉnh cũng có thể từ chối một bước sửa. Đừng dựa vào giả định rằng có ngoại lệ thì đầu vào chắc chắn giữ nguyên như trước.

Nếu cần bảo vệ list gốc khỏi quá trình sắp xếp có thể thất bại, xử lý một bản làm việc rồi chỉ công bố kết quả khi thành công. Comparator vẫn phải không tạo tác dụng phụ lên các phần tử chia sẻ.

### Cần phối hợp nếu list được dùng đồng thời

Đổi thứ tự là một quá trình gồm nhiều bước. Không cho luồng khác tùy ý đọc/ghi cùng list giữa quá trình rồi coi kết quả là một ảnh chụp nhất quán.

Một lớp bọc đồng bộ từng phương thức không tự biến mọi chuỗi thao tác nhiều bước thành một giao dịch theo yêu cầu nghiệp vụ. Khi cần, áp dụng một giao thức sở hữu/khóa cho toàn quá trình hoặc tạo bản làm việc có quyền sở hữu riêng rồi công bố kết quả phù hợp.

Không dùng `ConcurrentModificationException` làm cơ chế bảo vệ dữ liệu. Phát hiện sửa ngoài dự kiến không thay thế sự phối hợp đúng.

## 12. Kiểm thử đúng thứ cần bảo đảm

### Với sort

Kiểm tra kết quả theo comparator và kiểm tra các phần tử không bị thiếu/thừa. Nếu tính ổn định quan trọng, tạo ít nhất hai phần tử phân biệt được nhưng comparator trả 0 để quan sát thứ tự tương đối.

Nếu có nhiều tiêu chí, chọn dữ liệu có hòa ở tiêu chí đầu và khác ở tiêu chí sau. Nếu mọi điểm đều khác nhau, kiểm thử sẽ không phát hiện việc đảo sai tiêu chí phụ.

### Với reverse

Kiểm tra ánh xạ vị trí hoặc một dãy kỳ vọng cụ thể. Thử cả số phần tử chẵn và lẻ khi tự viết thuật toán. Tính chất reverse hai lần về ban đầu hữu ích nhưng chưa đủ để chứng minh một thuật toán bất kỳ là reverse: một hàm không làm gì cũng thỏa tính chất đó.

Chương trình trong bài có cả kết quả reverse cụ thể và tính chất hai lần đảo để tránh dựa vào một bằng chứng quá yếu.

### Với shuffle

Kiểm tra kích thước và số lần xuất hiện của từng phần tử. Chỉ chuyển list thành set rồi so không đủ: `[A, A, B]` và `[A, B, B]` có cùng tập thành viên nhưng số lần xuất hiện khác.

Dùng seed có kiểm soát khi cần tái hiện một tình huống. Không kiểm tra “thứ tự phải khác trước” vì hoán vị ban đầu hợp lệ. Không chứng minh phân bố công bằng bằng một lần hoặc vài lần chạy.

Một kiểm thử thuộc tính như “giữ các phần tử” cũng chưa đủ tự chứng minh thuật toán đã trộn đúng phân bố: một hàm không làm gì vẫn giữ phần tử. Tính đúng của cơ chế lựa chọn phải dựa vào thuật toán và hợp đồng nguồn ngẫu nhiên, còn thí nghiệm hỗ trợ phát hiện lỗi triển khai cụ thể.

## 13. Thực hành có đáp án

### Bài 1 — Reverse có làm giảm dần không?

Đầu vào `[2, 5, 1, 4]` được đưa thẳng cho `Collections.reverse`.

**Câu hỏi:** Kết quả là gì? Muốn giảm dần theo số thì dùng gì?

**Đáp án:** Kết quả là `[4, 1, 5, 2]`, không phải thứ tự giảm. Dùng `Collections.sort(list, Comparator.reverseOrder())` trên list sửa được để có `[5, 4, 2, 1]`. Reverse chỉ phụ thuộc thứ tự vị trí hiện có.

### Bài 2 — Giữ thứ tự tiếp nhận khi bằng điểm

Dữ liệu theo thứ tự `P:2, Q:1, R:2, S:1`. Yêu cầu điểm giảm dần và cùng điểm giữ thứ tự tiếp nhận.

**Câu hỏi:** Sort tăng rồi reverse cho kết quả nào? Sort giảm trực tiếp cho kết quả nào?

**Đáp án:** Sort tăng ổn định cho `Q:1, S:1, P:2, R:2`; reverse tiếp cho `R:2, P:2, S:1, Q:1`, không giữ thứ tự hòa ban đầu. Sort trực tiếp bằng comparator điểm giảm cho `P:2, R:2, Q:1, S:1` và đáp ứng yêu cầu.

### Bài 3 — Đặt `reversed` ở đâu?

Yêu cầu điểm giảm dần nhưng tên tăng dần khi bằng điểm.

**Câu hỏi:** Hai comparator sau có cùng ý nghĩa không?

~~~java
Comparator<Attempt> a =
        Comparator.comparingInt(Attempt::points)
                .reversed()
                .thenComparing(Attempt::id);

Comparator<Attempt> b =
        Comparator.comparingInt(Attempt::points)
                .thenComparing(Attempt::id)
                .reversed();
~~~

**Đáp án:** Không. `a` đảo tiêu chí điểm rồi thêm mã tăng. `b` đảo cả quy tắc đã ghép, nên khi bằng điểm thì mã giảm. Đây là thay đổi yêu cầu sắp xếp, không chỉ khác cách viết.

### Bài 4 — Cố định kích thước nhưng có thể đổi thứ tự

~~~java
Integer[] array = {2, 3, 1};
List<Integer> values = Arrays.asList(array);
Collections.sort(values);
Collections.reverse(values);
~~~

**Câu hỏi:** Mảng cuối là gì? Vì sao không cần `add`?

**Đáp án:** Mảng cuối là `[3, 2, 1]`. Các thao tác chỉ thay giá trị tại những ô sẵn có; list hỗ trợ thay phần tử và ghi xuyên tới mảng. Kích thước vẫn là 3. Điều đó không áp dụng cho một list không sửa được chỉ vì nó cũng có kích thước 3.

### Bài 5 — Cùng seed là chưa đủ

Hai list có cùng tập các chuỗi nhưng thứ tự đầu vào khác nhau. Mỗi list được shuffle bằng một `new Random(42)` riêng.

**Câu hỏi:** Có được khẳng định hai kết quả phải có cùng dãy giá trị không? Nếu cần tái hiện, lưu điều gì?

**Đáp án:** Không. Cùng chuỗi lựa chọn vị trí áp dụng lên hai thứ tự đầu vào khác nhau không nhất thiết cho cùng dãy giá trị. Để tái hiện thí nghiệm, giữ cả dữ liệu và thứ tự đầu vào, seed, loại nguồn và cách tiêu thụ nguồn. Nếu cần bảo tồn một thứ tự đã công bố lâu dài, lưu chính thứ tự hoặc các định danh của nó.

### Bài 6 — Shuffle có bắt buộc làm dãy đổi không?

Đầu vào là `[A, A, A]`. Kiểm thử yêu cầu kết quả phải khác đầu vào và mỗi phần tử phải chuyển vị trí.

**Câu hỏi:** Yêu cầu ấy có đúng hợp đồng không?

**Đáp án:** Không. Mọi hoán vị vị trí đều cho cùng dãy giá trị `[A, A, A]`. Ngay cả khi các phần tử phân biệt được, vẫn có thể có vị trí giữ nguyên và có thể gặp hoán vị đồng nhất. Kiểm tra giữ số lần xuất hiện và dùng thuật toán/nguồn ngẫu nhiên có hợp đồng phù hợp, không đòi một kết quả khác bắt buộc.

### Bài 7 — `null` cuối nhưng giá trị giảm

Có list sửa được chứa `[null, 2, 1]`. Yêu cầu kết quả `[2, 1, null]`.

**Câu hỏi:** Sort tăng với `nullsLast` rồi reverse có đúng không? Viết comparator phù hợp.

**Đáp án:** Không; sort tăng cho `[1, 2, null]` rồi reverse đưa `null` lên đầu. Dùng `Comparator.nullsLast(Comparator.reverseOrder())` để đảo thứ tự các số và giữ chính sách `null` cuối.

### Bài 8 — View hay bản riêng?

~~~java
List<String> source =
        new ArrayList<>(List.of("L", "b", "a", "R"));
List<String> middle = source.subList(1, 3);
Collections.sort(middle);
~~~

**Câu hỏi:** `source` trở thành gì? Nếu cần giữ nguyên nguồn thì tạo `middle` thế nào?

**Đáp án:** Nguồn thành `[L, a, b, R]` vì `middle` là view của hai vị trí 1 và 2. Cần tạo `new ArrayList<>(source.subList(1, 3))` trước khi sort để có list riêng về thứ tự. Bản sao vẫn chia sẻ các phần tử nếu chúng là đối tượng sửa được; trong ví dụ các phần tử là chuỗi.

Khi xử lý thứ tự, hãy xác định tiêu chí hoặc phép hoán vị cần có, quyền sửa list và nguồn dữ liệu phải giữ nguyên. Sort dùng quan hệ so sánh, reverse dùng vị trí hiện tại, shuffle dùng các lựa chọn ngẫu nhiên; những khác biệt ấy quyết định cả cách viết lẫn cách kiểm chứng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collections-utilities-002">binarySearch và điều kiện danh sách đã được sắp xếp</a>

<details>
<summary>Xem chi tiết</summary>

# binarySearch và điều kiện danh sách đã được sắp xếp

## 1. Vì sao tìm kiếm nhị phân cần thứ tự?

Giả sử danh sách các mốc đã có thứ tự 3, 7, 11, 15, 19, 23, 27. Muốn tìm 19, ta có thể so với phần tử ở giữa là 15. Vì 19 đứng sau 15 theo thứ tự đang dùng, toàn bộ phần phía trước và chính 15 đều không thể là đáp án. Chỉ cần tiếp tục trong nửa còn lại.

Đó là ý tưởng của **tìm kiếm nhị phân (binary search)**: mỗi lần so sánh loại bỏ một phần lớn vùng còn có thể chứa khóa tìm.

Việc loại bỏ ấy chỉ có căn cứ khi danh sách đã được sắp theo đúng phép so sánh. Nếu 19 có thể nằm tùy ý ở phía trước 15, loại bỏ nửa trước có thể bỏ luôn đáp án.

Một cách chạy minh họa trên danh sách trên:

| Lần | Vùng chỉ số còn xét | Phần tử giữa | So với 19 | Quyết định |
|---|---|---|---|---|
| 1 | 0 đến 6 | 15 tại 3 | 15 đứng trước 19 | Chỉ còn vùng 4 đến 6 |
| 2 | 4 đến 6 | 23 tại 5 | 23 đứng sau 19 | Chỉ còn vùng 4 đến 4 |
| 3 | 4 đến 4 | 19 tại 4 | Bằng nhau theo phép so sánh | Tìm thấy tại 4 |

Bảng giải thích một cách chia vùng hợp lệ, không yêu cầu mọi bản triển khai thư viện phải chọn đúng các bước giữa như vậy.

Collections.binarySearch là tiện ích tìm trên List. Nó **không tự sắp xếp danh sách**, không tìm tuyến tính để cứu đầu vào sai, và không chèn phần tử giúp nơi gọi.

Bài học đi từ điều kiện thứ tự, cách đọc kết quả, comparator, phần tử trùng đến việc duy trì danh sách khi sử dụng thật. Ba chương trình đầy đủ chạy bằng Java 17 trở lên và chỉ dùng thư viện chuẩn.

## 2. “Đã sắp xếp” là theo phép so sánh nào?

### 2.1. Hai dạng gọi chính

| Dạng gọi | Thứ tự danh sách phải có |
|---|---|
| Collections.binarySearch(list,key) | Thứ tự tự nhiên của các phần tử, tăng dần theo Comparable |
| Collections.binarySearch(list,key,order) | Tăng dần theo quan hệ do order định nghĩa |
| Collections.binarySearch(list,key,null) | Dùng thứ tự tự nhiên như trường hợp không truyền comparator |

“Thứ tự tự nhiên” là thứ tự do kiểu phần tử định nghĩa, chẳng hạn Integer theo giá trị số, String theo thứ tự so sánh chuỗi của String. Một dãy trông tăng theo cách người dùng đọc chưa chắc tăng theo compareTo của kiểu dữ liệu.

Ví dụ các String "1","10","2" có thể ở đúng thứ tự tự nhiên dù không giống thứ tự số 1,2,10. Nếu nghiệp vụ muốn so như số, phải chuyển thành kiểu số hoặc cung cấp phép so sánh thích hợp rồi sắp theo nó.

List không tự mang một Comparator cho binarySearch lấy lại như TreeSet/TreeMap. Mỗi lần gọi dạng có Comparator phải truyền quy tắc đúng với dữ liệu đang tìm.

### 2.2. Không giảm, không phải tăng nghiêm ngặt

Với comparator c hợp lệ và ổn định, danh sách đã sắp khi mọi cặp kề nhau thỏa:

c.compare(list.get(i), list.get(i + 1)) <= 0.

Phần tử trùng hoặc bằng nhau theo comparator được phép. Dãy 1,2,2,2,4 vẫn đã sắp. Không cần loại bỏ phần tử trùng trước khi dùng binarySearch.

Danh sách rỗng và một phần tử không có cặp kề vi phạm thứ tự. Điều kiện so sánh với khóa tìm vẫn phải được sử dụng đúng; không dùng một trường hợp rỗng để suy rằng mọi kiểu khóa đều tương thích ở các trường hợp khác.

### 2.3. Giảm dần theo số vẫn là tăng theo comparator đảo

Comparator.reverseOrder() cho số nguyên đặt 50 trước 40, 40 trước 30. Danh sách 50,40,30,20,10 vì thế đã sắp tăng theo quan hệ của comparator đó.

Tìm trên danh sách này bằng dạng mặc định dùng thứ tự tự nhiên là sai điều kiện. Cần truyền Comparator giảm dần tương ứng.

Cách viết dễ kiểm soát là giữ comparator trong một biến và dùng nó cho cả sort lẫn binarySearch. Không bắt buộc là cùng một đối tượng Comparator theo ==; điều cần đúng là danh sách vẫn có thứ tự theo quan hệ dùng để tìm.

Một thứ tự chi tiết hơn đôi khi vẫn tương thích với tìm theo tiêu chí thô hơn. Chẳng hạn sắp theo điểm rồi tên vẫn giữ thứ tự theo điểm. Nhưng sắp chỉ theo điểm không tự bảo đảm các phần tử cùng điểm đã tăng theo tên để tìm bằng comparator điểm rồi tên. Cần kiểm tra quan hệ thực tế, không dựa vào hai tên comparator có vẻ gần nhau.

Đặc tả của [Collections.binarySearch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#binarySearch(java.util.List,T,java.util.Comparator)) yêu cầu danh sách được sắp trước theo phép so sánh dùng cho lời gọi.

### 2.4. Vi phạm điều kiện không bảo đảm nhận một ngoại lệ

Nếu danh sách chưa được sắp đúng, hợp đồng không bảo đảm kết quả tìm kiếm. Có lần tìm thấy, có lần trả số âm dù phần tử có thật, hoặc cho vị trí chèn không có ý nghĩa đúng với toàn danh sách.

Không viết code bắt một ngoại lệ “danh sách chưa sắp” rồi mới sửa. binarySearch không có trách nhiệm duyệt toàn bộ danh sách để phát hiện điều kiện đó trước mỗi lần tìm.

## 3. Kết quả trả về có hai nhánh, không chỉ có chỉ số hoặc -1

Gọi r = Collections.binarySearch(list,key).

### 3.1. r >= 0: tìm thấy một phần tử so sánh bằng khóa

r là chỉ số bắt đầu từ 0 trong chính list được truyền vào. Có thể lấy list.get(r) khi danh sách chưa thay đổi làm vô hiệu ý nghĩa vị trí.

Chỉ số 0 là tìm thấy ở phần tử đầu, không phải thất bại. Vì vậy điều kiện đúng để kiểm tra có kết quả là r >= 0, không phải r > 0.

“Tìm thấy” được hiểu theo compareTo/Comparator bằng 0. Nó không mặc định nghĩa là tìm thấy cùng một tham chiếu hoặc một đối tượng equals khóa; ví dụ đối tượng có comparator theo một trường sẽ minh họa sự khác nhau này.

### 3.2. r < 0: mã hóa vị trí chèn

Khi không tìm thấy, r không chỉ là một mã thất bại chung. Nó có dạng:

r = -insertionPoint - 1.

insertionPoint là vị trí mà khóa có thể được chèn vào để giữ thứ tự: vị trí phần tử đầu tiên đứng sau khóa theo phép so sánh, hoặc list.size() nếu khóa đứng sau toàn bộ phần tử.

Vì đang ở nhánh **không có phần tử so sánh bằng khóa**, vị trí đầu tiên không nhỏ hơn khóa và vị trí đầu tiên lớn hơn khóa trùng nhau.

Giải mã bằng:

insertionPoint = -(r + 1).

Vị trí chèn thuộc [0,size], trong khi chỉ số phần tử hợp lệ thuộc [0,size). Giá trị size hợp lệ cho add(size,key) của một list có hỗ trợ thêm, nhưng không hợp lệ cho get(size).

### 3.3. Ví dụ giải mã

Nguồn có thứ tự [-10,0,10,20,30]:

| Khóa | Kết quả r | Ý nghĩa |
|---|---|---|
| -10 | 0 | Tìm thấy phần tử đầu |
| 10 | 2 | Tìm thấy tại chỉ số 2 |
| 15 | -4 | Không có; chèn tại 3 trước 20 |
| -20 | -1 | Không có; chèn tại 0 |
| 40 | -6 | Không có; chèn tại 5, tức cuối list |

Với list rỗng, vị trí chèn là 0 nên kết quả là -1. -1 không có nghĩa duy nhất là “không tìm thấy ở bất cứ đâu” mà còn mã hóa chính vị trí 0.

Không dùng Math.abs(r) để lấy vị trí chèn. Với r = -4, Math.abs cho 4 trong khi vị trí đúng là 3. Cách -(r + 1) cũng tránh phải xử lý một bước lấy đối của số âm nhỏ nhất trước khi trừ 1.

## 4. Chương trình đọc kết quả, chèn khi chưa có và xử lý phần tử trùng

Tệp **BinarySearchResultDemo.java**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BinarySearchResultDemo {
    private static void report(List<Integer> values, int key) {
        int result = Collections.binarySearch(values, key);
        if (result >= 0) {
            System.out.println("key=" + key + ", found=" + result +
                    ", value=" + values.get(result));
        } else {
            int insertionPoint = -(result + 1);
            System.out.println("key=" + key + ", result=" + result +
                    ", insertion=" + insertionPoint);
        }
    }

    public static void main(String[] args) {
        List<Integer> values =
                new ArrayList<>(List.of(-10, 0, 10, 20, 30));
        report(values, -10);
        report(values, 10);
        report(values, 15);
        report(values, -20);
        report(values, 40);
        report(List.of(), 42);

        int result = Collections.binarySearch(values, 15);
        if (result < 0) {
            values.add(-(result + 1), 15);
        }
        System.out.println("afterInsert=" + values);
        System.out.println("readOnlySearch=" +
                Collections.binarySearch(List.of(2, 4, 6, 8), 6));

        List<Integer> duplicates = List.of(-2, 0, 0, 0, 4);
        int duplicateResult = Collections.binarySearch(duplicates, 0);
        System.out.println("duplicateFound=" + (duplicateResult >= 0));
        System.out.println("duplicateMatched=" + duplicates.get(duplicateResult));
        System.out.println("duplicateIndexInRange=" +
                (duplicateResult >= 1 && duplicateResult <= 3));
        report(duplicates, 1);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 BinarySearchResultDemo.java
java BinarySearchResultDemo
```

Stdout chính xác:

```text
key=-10, found=0, value=-10
key=10, found=2, value=10
key=15, result=-4, insertion=3
key=-20, result=-1, insertion=0
key=40, result=-6, insertion=5
key=42, result=-1, insertion=0
afterInsert=[-10, 0, 10, 15, 20, 30]
readOnlySearch=2
duplicateFound=true
duplicateMatched=0
duplicateIndexInRange=true
key=1, result=-5, insertion=4
```

### 4.1. Tìm kiếm không cần list sửa được

readOnlySearch tìm trên List.of(2,4,6,8) và trả 2. binarySearch chỉ đọc; nó không cần add/set hay tự gọi sort.

Ngược lại, đoạn chèn 15 cần list hỗ trợ add(index,value). Chỉ số chèn đúng không làm một list không sửa được bỗng hỗ trợ thao tác thêm.

Nếu phải sắp một list không cho sửa mà vẫn muốn giữ dữ liệu đầu vào, có thể tạo ArrayList riêng rồi sắp bản riêng. Không gọi sort lên kết quả chỉ đọc và cho rằng binarySearch sẽ làm phần còn lại.

### 4.2. Số âm phải được xử lý trước khi truy cập

report() chỉ gọi get(result) ở nhánh result >= 0. Ở nhánh âm, nó giải mã vị trí chèn và in thông tin.

Nếu dùng số âm thẳng làm chỉ số, đó là lỗi của nơi gọi, thường dẫn tới IndexOutOfBoundsException. binarySearch đã trả thông tin hợp lệ về việc không có khóa.

### 4.3. Khi trùng, không cam kết phần tử đầu hoặc cuối

duplicates có các phần tử bằng 0 ở chỉ số 1,2,3. Chương trình kiểm tra kết quả nằm trong khoảng đó và phần tử tại kết quả bằng 0, nhưng không in một chỉ số cụ thể rồi coi đó là hợp đồng.

Collections.binarySearch không cam kết trả vị trí đầu tiên hoặc cuối cùng khi có nhiều phần tử so sánh bằng khóa. Sort ổn định cũng không thay đổi hợp đồng này: giữ thứ tự của các phần tử bằng nhau khi sắp không có nghĩa thuật toán tìm phải chọn phần tử đầu trong nhóm.

Nếu chỉ cần biết có ít nhất một phần tử bằng khóa, kết quả bất kỳ trong nhóm là đủ. Nếu cần đếm, lấy toàn bộ nhóm hoặc chèn trước/sau nhóm, cần tìm biên riêng.

## 5. Comparator quyết định nghĩa của tìm thấy và vị trí chèn

Chương trình sau có các Score gồm tên và điểm. Comparator đầu tiên chỉ nhìn points, nên hai Score khác tên có thể so sánh bằng 0 dù record.equals() trả false.

Nó cũng dùng một danh sách số giảm dần và một comparator cho phép null để kiểm tra điều kiện thứ tự ở các trường hợp khác nhau.

Tệp **BinarySearchComparatorDemo.java**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BinarySearchComparatorDemo {
    record Score(String name, int points) {}

    public static void main(String[] args) {
        Comparator<Score> byPoints = Comparator.comparingInt(Score::points);
        List<Score> scores = new ArrayList<>(List.of(
                new Score("A", 30), new Score("B", 10),
                new Score("C", 20), new Score("D", 20)));
        scores.sort(byPoints);
        System.out.println("sortedPoints=" +
                scores.stream().map(Score::points).toList());

        Score probe = new Score("probe", 20);
        int result = Collections.binarySearch(scores, probe, byPoints);
        Score matched = scores.get(result);
        System.out.println("matchedPoints=" + matched.points());
        System.out.println("compareZero=" + (byPoints.compare(matched, probe) == 0));
        System.out.println("equalsProbe=" + matched.equals(probe));
        System.out.println("indexOfProbe=" + scores.indexOf(probe));

        int missing = Collections.binarySearch(
                scores, new Score("probe", 25), byPoints);
        System.out.println("missing25=" + missing);
        System.out.println("insert25At=" + (-(missing + 1)));

        Comparator<Score> byPointsThenName =
                byPoints.thenComparing(Score::name);
        scores.sort(byPointsThenName);
        int exact = Collections.binarySearch(
                scores, new Score("D", 20), byPointsThenName);
        System.out.println("exactName=" + scores.get(exact).name());

        Comparator<Integer> descending = Comparator.reverseOrder();
        List<Integer> descendingValues =
                new ArrayList<>(List.of(50, 40, 30, 20, 10));
        int reverseMissing =
                Collections.binarySearch(descendingValues, 35, descending);
        int reverseInsertion = -(reverseMissing + 1);
        System.out.println("descendingMissing35=" + reverseMissing);
        System.out.println("descendingInsertion=" + reverseInsertion);
        descendingValues.add(reverseInsertion, 35);
        System.out.println("descendingAfterInsert=" + descendingValues);

        Comparator<Integer> nullsFirst =
                Comparator.nullsFirst(Comparator.naturalOrder());
        List<Integer> nullable = Arrays.asList(null, 10, 20);
        System.out.println("nullKeyIndex=" +
                Collections.binarySearch(nullable, null, nullsFirst));
        System.out.println("nullableMissing15=" +
                Collections.binarySearch(nullable, 15, nullsFirst));
        System.out.println("nullComparatorUsesNatural=" +
                Collections.binarySearch(List.of(1, 2, 3), 2, null));
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 BinarySearchComparatorDemo.java
java BinarySearchComparatorDemo
```

Stdout chính xác:

```text
sortedPoints=[10, 20, 20, 30]
matchedPoints=20
compareZero=true
equalsProbe=false
indexOfProbe=-1
missing25=-4
insert25At=3
exactName=D
descendingMissing35=-3
descendingInsertion=2
descendingAfterInsert=[50, 40, 35, 30, 20, 10]
nullKeyIndex=0
nullableMissing15=-3
nullComparatorUsesNatural=1
```

### 5.1. Tìm theo điểm khác tìm đúng toàn bộ record

probe có tên probe và điểm 20. Sau khi sắp theo points, binarySearch trả một Score có điểm 20. Tên có thể là C hoặc D; chương trình không dựa vào lựa chọn nào.

byPoints.compare(matched,probe) bằng 0 nhưng matched.equals(probe) bằng false, vì record.equals xét các thành phần của record. scores.indexOf(probe) trả -1 do dùng quan hệ equals.

Đây là hai câu hỏi khác nhau:

- binarySearch với byPoints: có phần tử nào thuộc nhóm điểm 20 không?
- indexOf với probe: có phần tử equals đúng probe theo hợp đồng lớp không?

Comparator chỉ theo points vẫn là một phép so sánh hợp lệ cho nhu cầu tìm nhóm điểm. Không bắt buộc mọi comparator dùng tìm trong List phải phân biệt mọi trường của record.

Muốn tìm theo điểm rồi tên, chương trình sắp lại bằng byPoints.thenComparing(Score::name) và tìm với chính quan hệ đó. Kết quả exactName là D.

### 5.2. Vị trí chèn cũng dùng chiều của comparator

Trong danh sách 50,40,30,20,10 theo reverseOrder, 35 được chèn tại 2, giữa 40 và 30. Mã trả về là -3.

Không giải mã xong rồi áp một quy tắc vị trí theo số tăng dần. Vị trí chèn đã là vị trí trong dãy giảm dần được truyền vào.

### 5.3. Comparator null và khóa null là hai chuyện khác nhau

Truyền null ở tham số comparator có nghĩa dùng thứ tự tự nhiên; nó không phải một comparator cho phép phần tử hoặc khóa null.

Comparator.nullsFirst(naturalOrder()) mới biểu đạt chính sách đặt null trước các số. Danh sách nullable trong ví dụ đã đúng thứ tự của chính comparator đó, nên tìm null tại 0 hợp lệ và tìm 15 trả vị trí chèn 2.

Không mặc định natural ordering biết so sánh null. Một trường hợp đặc biệt chưa cần so sánh, chẳng hạn list rỗng, cũng không chứng minh null được hỗ trợ chung cho mọi dữ liệu.

### 5.4. Phép so sánh phải đúng và ổn định

Không viết comparator cho số nguyên bằng a - b nếu miền giá trị có thể làm phép trừ tràn số. Dùng Integer.compare hoặc các factory comparingInt như ví dụ giúp biểu đạt phép so sánh đúng hơn.

Comparator cần thỏa hợp đồng thứ tự, gồm tính nhất quán và bắc cầu. Nó cũng không nên thay đổi ý nghĩa giữa sort và search vì một cờ cấu hình hoặc trạng thái bên ngoài bị đổi.

Tài liệu [Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html) giải thích quan hệ so sánh và việc compare bằng 0 có thể khác equals. [Comparable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html) quy định nền tảng cho trường hợp thứ tự tự nhiên.

## 6. Muốn phần tử đầu, cuối hoặc toàn bộ nhóm trùng thì tìm biên

### 6.1. Hai biên cần phân biệt

Trên list đã sắp theo comparator c:

- lowerBound(key): vị trí đầu tiên có phần tử không đứng trước key, tức c.compare(element,key) >= 0; nếu không có thì trả size.
- upperBound(key): vị trí đầu tiên có phần tử đứng sau key, tức c.compare(element,key) > 0; nếu không có thì trả size.

Các phần tử so sánh bằng key nằm trong khoảng chỉ số [lowerBound,upperBound). Số phần tử trong nhóm là upperBound - lowerBound.

Nếu hai biên bằng nhau, không có phần tử so sánh bằng khóa. Vị trí đó chính là vị trí chèn khi không tìm thấy.

Nếu nhóm không rỗng:

- Vị trí đầu nhóm là lowerBound.
- Vị trí cuối nhóm là upperBound - 1.
- Chèn ở lowerBound đặt phần tử mới trước nhóm.
- Chèn ở upperBound đặt phần tử mới sau nhóm.

Đây là các hàm tự viết của ví dụ, không phải tên phương thức có sẵn trong Collections. Chúng vẫn yêu cầu list đã được sắp và comparator đúng.

### 6.2. Chương trình biên, kiểm tra thứ tự và chỉ số của subList

Tệp **BinarySearchBoundsDemo.java**:

```java
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class BinarySearchBoundsDemo {
    public static <T> int lowerBound(
            List<? extends T> values, T key, Comparator<? super T> order) {
        Objects.requireNonNull(values);
        Objects.requireNonNull(order);
        int low = 0;
        int high = values.size();
        while (low < high) {
            int mid = low + (high - low) / 2;
            if (order.compare(values.get(mid), key) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    public static <T> int upperBound(
            List<? extends T> values, T key, Comparator<? super T> order) {
        Objects.requireNonNull(values);
        Objects.requireNonNull(order);
        int low = 0;
        int high = values.size();
        while (low < high) {
            int mid = low + (high - low) / 2;
            if (order.compare(values.get(mid), key) <= 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    public static <T> boolean isSorted(
            List<? extends T> values, Comparator<? super T> order) {
        Objects.requireNonNull(values);
        Objects.requireNonNull(order);
        Iterator<? extends T> iterator = values.iterator();
        if (!iterator.hasNext()) {
            return true;
        }
        T previous = iterator.next();
        while (iterator.hasNext()) {
            T current = iterator.next();
            if (order.compare(previous, current) > 0) {
                return false;
            }
            previous = current;
        }
        return true;
    }

    public static void main(String[] args) {
        Comparator<Integer> ascending = Comparator.naturalOrder();
        List<Integer> values = List.of(1, 2, 2, 2, 4);
        int lower = lowerBound(values, 2, ascending);
        int upper = upperBound(values, 2, ascending);
        System.out.println("bounds2=" + lower + "," + upper);
        System.out.println("count2=" + (upper - lower));
        System.out.println("equalRange=" + values.subList(lower, upper));
        System.out.println("bounds3=" +
                lowerBound(values, 3, ascending) + "," +
                upperBound(values, 3, ascending));
        System.out.println("beforeAll=" + lowerBound(values, 0, ascending));
        System.out.println("afterAll=" + upperBound(values, 9, ascending));
        System.out.println("emptyBounds=" +
                lowerBound(List.<Integer>of(), 2, ascending) + "," +
                upperBound(List.<Integer>of(), 2, ascending));
        System.out.println("sorted=" + isSorted(values, ascending));
        System.out.println("unsorted=" +
                isSorted(List.of(1, 3, 2), ascending));

        List<Integer> descending = List.of(5, 4, 4, 2);
        Comparator<Integer> reverse = Comparator.reverseOrder();
        System.out.println("descendingBounds4=" +
                lowerBound(descending, 4, reverse) + "," +
                upperBound(descending, 4, reverse));

        List<Integer> whole = List.of(0, 10, 20, 30, 40);
        int from = 1;
        List<Integer> part = whole.subList(from, 4);
        int localFound = Collections.binarySearch(part, 20);
        int localMissing = Collections.binarySearch(part, 25);
        System.out.println("localFound=" + localFound);
        System.out.println("wholeFound=" + (from + localFound));
        System.out.println("localMissing=" + localMissing);
        System.out.println("wholeInsertion=" + (from - (localMissing + 1)));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 BinarySearchBoundsDemo.java
java BinarySearchBoundsDemo
```

Stdout chính xác:

```text
bounds2=1,4
count2=3
equalRange=[2, 2, 2]
bounds3=4,4
beforeAll=0
afterAll=5
emptyBounds=0,0
sorted=true
unsorted=false
descendingBounds4=1,3
localFound=1
wholeFound=2
localMissing=-3
wholeInsertion=3
```

### 6.3. Tại sao thay một dấu so sánh làm đổi biên?

Trong lowerBound, khi phần tử giữa đứng trước key, biên cần tìm phải nằm bên phải nên low = mid + 1. Khi phần tử giữa bằng hoặc đứng sau key, chưa được bỏ qua vị trí giữa, nên high = mid.

Trong upperBound, trường hợp bằng key cũng phải đi sang phải. Vì vậy điều kiện ở nhánh cập nhật low dùng <= 0 thay vì < 0.

Vùng tìm được biểu diễn bằng [low,high), cho phép kết quả là size mà không truy cập get(size). Khi low == high, vòng lặp kết thúc tại đúng biên.

Cách tính mid = low + (high - low) / 2 tránh cộng trực tiếp hai chỉ số lớn trước khi chia. Mỗi lần lặp đều làm vùng còn lại nhỏ hơn.

Trên [1,2,2,2,4], biên của 2 là 1 và 4, nên nhóm có ba phần tử. Biên của 3 đều là 4, xác nhận không có 3 và có thể chèn tại 4.

### 6.4. Những hàm minh họa không có mọi chính sách của thư viện

Hai hàm biên trong chương trình yêu cầu Comparator không null một cách tường minh. Muốn thứ tự tự nhiên, truyền Comparator.naturalOrder(). Đây là hợp đồng của hàm tự viết, khác dạng Collections.binarySearch(...,null) dùng thứ tự tự nhiên.

Cả hai dùng get(mid), nên phù hợp nhất với list truy cập ngẫu nhiên nhanh. Chúng có thể tính đúng trên list truy cập tuần tự hợp lệ, nhưng không tự có tối ưu duyệt iterator của Collections.binarySearch cho list lớn kiểu đó.

isSorted dùng iterator để kiểm tra các cặp kề nhau. Khi comparator là một quan hệ thứ tự đúng và ổn định, điều kiện không giảm giữa các cặp kề xác nhận danh sách đã sắp theo quan hệ đó. Hàm không kiểm chứng toàn bộ hợp đồng bắc cầu hay tính đúng của một Comparator tùy ý.

## 7. Tìm trên subList: kết quả thuộc hệ chỉ số của đối tượng được truyền vào

Trong chương trình, whole là [0,10,20,30,40]. part = whole.subList(1,4) chứa [10,20,30].

Tìm 20 trên part trả chỉ số 1. Chỉ số tương ứng trong whole là 1 + 1 = 2. binarySearch không tự trả chỉ số của list cha.

Khi không có 25 trong part:

- Kết quả localMissing là -3.
- Vị trí chèn trong part là -(-3 + 1) = 2.
- Vị trí chèn tương ứng trong whole là from + 2 = 3.

Phải **giải mã số âm trước**, rồi mới chuyển hệ tọa độ. Không cộng offset thẳng vào một kết quả âm đã mã hóa rồi coi đó là chỉ số hoặc kết quả đã mã hóa của list cha.

Nếu có kết quả r >= 0, chuyển chỉ số bằng from + r. Nếu r < 0, chuyển vị trí chèn bằng from - (r + 1).

Quy tắc này áp dụng khi quan hệ giữa subList và list cha vẫn hợp lệ và chưa có thay đổi làm vô hiệu view/chỉ số. List.subList có điều kiện riêng về sửa cấu trúc ở nguồn; lớp bọc chỉ đọc hoặc binarySearch không loại bỏ các điều kiện ấy.

Các phần tử của một đoạn liên tiếp trong list đã sắp vẫn giữ thứ tự theo cùng comparator. Tuy nhiên, điều đó không cho phép dùng một comparator khác hoặc bỏ qua việc list cha thay đổi.

## 8. Giữ điều kiện sắp xếp trong suốt vòng đời dữ liệu

### 8.1. Một lần sort trong quá khứ chưa đủ

Sau sort, các thao tác sau có thể làm danh sách không còn thứ tự phù hợp:

- Thêm một phần tử vào vị trí tùy ý không đúng thứ tự.
- Dùng set thay phần tử bằng một giá trị phá quan hệ với phần tử kề.
- Đảo hoặc trộn thứ tự danh sách rồi vẫn tìm bằng comparator cũ.
- Đổi thuộc tính được comparator đọc trên một đối tượng phần tử.
- Đổi quy tắc của comparator hoặc trạng thái bên ngoài mà nó dựa vào.

Không phải mọi sửa đổi đều phá thứ tự. Xóa phần tử khỏi một list đã sắp vẫn giữ thứ tự của các phần tử còn lại. Chèn đúng vị trí tính được hoặc thay bằng giá trị vẫn nằm giữa hai hàng xóm có thể giữ điều kiện.

Điều cần theo dõi là quan hệ thứ tự hiện tại, không chỉ một cờ “đã từng gọi sort”.

### 8.2. Chèn khi chưa có và chèn thêm vào nhóm đã có

Ở nhánh r < 0, vị trí chèn giải mã được bảo toàn thứ tự khi chèn đúng khóa vừa tìm vào một list chưa thay đổi.

Ở nhánh r >= 0, nếu mục đích là bảo đảm duy nhất theo comparator thì có thể không chèn. Nếu muốn giữ nhiều phần tử bằng nhau, phải xác định vị trí trong nhóm có quan trọng không.

Chèn tại một vị trí khớp có thể vẫn giữ thứ tự theo comparator, nhưng không bảo đảm đặt sau toàn bộ các phần tử cùng khóa. Muốn trước hoặc sau nhóm, dùng biên tương ứng.

Nếu đối tượng có thêm tiêu chí phụ, cần thống nhất comparator để quyết định điều kiện thứ tự đầy đủ. Không tìm vị trí chỉ theo điểm rồi mặc định danh sách vẫn đúng thứ tự điểm-và-tên sau khi chèn tên bất kỳ.

### 8.3. Trường của phần tử mutable cũng là một đường làm hỏng thứ tự

Giữ list không cho add/set không làm các phần tử bên trong bất biến. Nếu một Score dạng mutable được tăng điểm trực tiếp, vị trí vật lý cũ có thể không còn phù hợp với điểm mới.

Khi đó binarySearch vẫn nhìn thứ tự vật lý đang có, không tự sắp lại vì đối tượng đổi thuộc tính. Có thể cần cập nhật qua một lớp quản lý, đưa phần tử ra rồi chèn lại đúng vị trí, sắp lại khi kết thúc lô cập nhật hoặc dùng dữ liệu bất biến theo yêu cầu.

Một bản sao cấu trúc bằng new ArrayList<>(source) cũng có thể vẫn giữ cùng các đối tượng mutable, nên không tự giải quyết vấn đề này.

### 8.4. Kết quả chỉ số không phải định danh ổn định của đối tượng

Sau khi chèn/xóa phần tử trước vị trí vừa tìm, cùng một đối tượng có thể chuyển sang chỉ số khác. Lưu r rồi dùng lại sau nhiều cập nhật có thể lấy nhầm phần tử.

Nếu cần giữ một thực thể, hãy giữ thông tin nhận diện hoặc tham chiếu phù hợp với vòng đời dữ liệu; nếu cần một vị trí cho thao tác hiện tại, phải kiểm soát việc danh sách không đổi giữa tìm và sử dụng.

Trong môi trường nhiều luồng, chuỗi tìm → giải mã → chèn hoặc tìm → get có thể cần thực hiện trong cùng cơ chế đồng bộ/quyền sở hữu. binarySearch không tự khóa toàn bộ quy trình ấy.

## 9. Chi phí: ít phép so sánh chưa chắc ít bước truy cập

### 9.1. Trên list truy cập ngẫu nhiên

Nếu get(index) có chi phí gần như hằng số và phép so sánh có chi phí được kiểm soát, tìm kiếm nhị phân cần O(log n) lần thu hẹp vùng.

ArrayList là ví dụ thường gặp cho truy cập vị trí nhanh. Khi comparator so sánh những đối tượng lớn hoặc phải tính toán nhiều, chi phí mỗi phép so sánh vẫn cần tính thêm. O(log n) không có nghĩa mọi phép so sánh đều miễn phí.

### 9.2. Trên LinkedList hoặc list truy cập tuần tự lớn

LinkedList phải đi qua các liên kết để đến một chỉ số. Nếu tự viết thuật toán dùng get(mid) lặp lại mà bỏ qua kiểu list, tổng chi phí có thể xấu hơn điều người viết suy từ số lần so sánh.

Đặc tả Collections.binarySearch mô tả việc dùng cách tìm dựa trên iterator cho list lớn không triển khai RandomAccess: có O(log n) phép so sánh phần tử nhưng O(n) bước đi qua liên kết. Không được gọi toàn bộ thời gian trên LinkedList là O(log n) chỉ vì thuật toán mang tên binary search.

[RandomAccess](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/RandomAccess.html) là interface đánh dấu để các thuật toán chung phân biệt khả năng truy cập theo vị trí. [LinkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html) nêu cách duyệt để tới chỉ số; các bảo đảm thao tác của [ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html) khác với mô hình đó.

Không hard-code ngưỡng kích thước chuyển thuật toán của một bản JDK vào logic ứng dụng. Ngưỡng và cách triển khai cụ thể không phải điều kiện nghiệp vụ.

### 9.3. Tìm vị trí nhanh không làm chèn ArrayList nhanh như vậy

Ngay cả khi tìm vị trí chèn trên ArrayList có số bước logarithm, chèn giữa list vẫn có thể cần dịch nhiều phần tử, với chi phí tuyến tính theo số phần tử phải dời.

Nếu liên tục thêm dữ liệu và cần thứ tự duy trì sau mỗi lần, hãy đánh giá cả tìm, chèn, xóa và nhu cầu giữ phần tử trùng. Một cấu trúc có thứ tự khác có thể phù hợp hơn, nhưng không thay List bằng Set/Map mà bỏ qua khác biệt về vị trí và quan hệ bằng nhau.

### 9.4. Có nên sort trước mọi lần tìm?

Nếu dữ liệu chưa được sắp và chỉ cần một lần tìm theo equals, tìm tuyến tính có thể đơn giản và phù hợp hơn việc sort toàn bộ. Sắp xếp còn làm đổi thứ tự đầu vào nếu thực hiện trực tiếp.

Với nhiều truy vấn trên dữ liệu ít thay đổi, sắp một lần rồi tìm nhiều lần có thể có lợi. Trong phân tích sắp xếp bằng so sánh thông thường, chi phí chuẩn bị cỡ O(n log n) có thể được phân bổ cho nhiều lần tìm O(log n) trên cấu trúc truy cập ngẫu nhiên.

Nếu cần giữ thứ tự nguồn ban đầu, tạo bản riêng để sắp hoặc giữ thêm chỉ số gốc trong mô hình. Kết quả binarySearch trên bản đã sắp là chỉ số của bản đó, không phải chỉ số cũ của đầu vào chưa sắp.

## 10. Phát hiện sai điều kiện và xử lý đầu vào

### 10.1. Kiểm tra thứ tự khi nào?

Hàm isSorted trong BinarySearchBoundsDemo kiểm tra bằng cách đi qua từng cặp kề, có chi phí O(n). Nó hữu ích ở biên nhận dữ liệu, lúc gỡ lỗi hoặc khi viết kiểm thử.

Nếu kiểm tra toàn bộ list trước mọi lần binarySearch, tổng chi phí mỗi lần không còn chỉ O(log n). Trong một lớp quản lý dữ liệu, thường nên bảo đảm thứ tự bằng thiết kế của các đường cập nhật, hoặc kiểm tra khi dữ liệu đi vào/thay đổi theo lô.

Không dùng isSorted như bằng chứng rằng một Comparator vi phạm hợp đồng bỗng trở nên đúng. Hàm kiểm tra thứ tự của dữ liệu theo comparator được cung cấp, trong giả định comparator là quan hệ thích hợp.

### 10.2. Lỗi kiểu và khả năng so sánh

Dạng tìm tự nhiên cần các phần tử và khóa so sánh được với nhau theo Comparable. Generics giúp phát hiện nhiều lỗi lúc biên dịch; nếu bỏ qua bằng raw type hoặc ép kiểu không đúng, vẫn có thể gặp ClassCastException lúc chạy.

Dạng có Comparator không bắt buộc lớp phần tử tự triển khai Comparable, như record Score trong ví dụ. Tuy vậy, comparator phải xử lý được các phần tử thực tế và khóa tìm.

Truyền list null không có dữ liệu để tìm và không hợp lệ. Khóa null/phần tử null cần một chính sách so sánh hỗ trợ chúng; việc list triển khai nào đó cho phép chứa null không đủ để kết luận phép tìm theo thứ tự tự nhiên hợp lệ.

### 10.3. Dữ liệu sai thứ tự không nên có “stdout chuẩn” cho kết quả tìm

Một ví dụ có chủ đích dùng danh sách chưa sắp rồi in một chỉ số từ binarySearch chỉ cho thấy hành vi quan sát được của lần chạy ấy. Nó không tạo hợp đồng về kết quả trên đầu vào vi phạm điều kiện.

Trong bài này, isSorted([1,3,2]) trả false để phát hiện vấn đề. Không dùng kết quả tìm trên dãy ấy làm bài tập yêu cầu nhớ một con số cố định.

## 11. Quy trình áp dụng trong một chức năng thực tế

Với một danh sách dữ liệu cần tìm nhiều lần, có thể tổ chức như sau:

1. Xác định chính xác khóa tìm và quan hệ so sánh, gồm tiêu chí phụ nếu cần.
2. Kiểm tra dữ liệu về kiểu, null và các bất biến nghiệp vụ.
3. Tạo hoặc chọn list phù hợp, rồi sắp theo quan hệ đó.
4. Thực hiện các lần binarySearch bằng cùng quan hệ tương thích.
5. Phân nhánh kết quả: chỉ số khớp hoặc vị trí chèn đã giải mã.
6. Nếu cần toàn bộ nhóm bằng nhau, dùng phép tìm biên với hợp đồng rõ.
7. Chỉ cho cập nhật qua các đường giữ được thứ tự hoặc sắp lại khi cần.
8. Kiểm soát việc dữ liệu không đổi giữa tìm và thao tác phụ thuộc chỉ số.

Nếu chỉ cần tìm có/không mà không cần vị trí hoặc thứ tự, một cấu trúc tra cứu theo khóa có thể biểu đạt tốt hơn. Nhưng phải xét quan hệ so sánh: tìm theo Comparator bằng 0 có thể không giống tra cứu theo equals/hashCode.

Nếu truy vấn là “tìm theo điểm”, kết quả so sánh bằng 0 chỉ xác nhận điểm khớp, không chứng minh mọi trường khác trùng khớp. Nếu truy vấn là “tìm đúng thực thể có mã cụ thể”, hãy đưa điều kiện đó vào mô hình chỉ mục hoặc bước kiểm tra phù hợp.

Không dùng binarySearch như một cách tìm phần tử bất kỳ trên mọi List. Nó có giá trị khi ứng dụng thực sự giữ được quan hệ thứ tự cần thiết.

## 12. Những lỗi phổ biến và cách đọc đúng

| Cách dùng sai | Vấn đề | Cách sửa |
|---|---|---|
| Tìm trên list chưa sắp | Việc loại nửa vùng không có căn cứ | Sắp trước hoặc chọn cách tìm khác |
| Sắp theo một quan hệ, tìm bằng quan hệ không tương thích | Điều kiện đã sắp của phép tìm bị vi phạm | Dùng comparator đúng với thứ tự hiện tại |
| Kiểm tra r > 0 | Bỏ sót kết quả tại chỉ số 0 | Kiểm tra r >= 0 |
| Chỉ so r == -1 để xác định không có | Các số âm khác cũng là không có | Phân nhánh r < 0 |
| Dùng Math.abs(r) làm vị trí chèn | Sai công thức và có vấn đề biên số nguyên | Dùng -(r + 1) khi r < 0 |
| Dùng vị trí chèn size cho get | size là biên sau phần tử cuối | Chỉ dùng cho thao tác có hợp đồng nhận biên đó |
| Mặc định kết quả là phần tử đầu nhóm trùng | API không cam kết lựa chọn ấy | Tìm lowerBound/upperBound khi cần |
| Mặc định compare bằng 0 là equals | Có thể chỉ khớp theo một thuộc tính | Hiểu đúng comparator và điều kiện nghiệp vụ |
| Cộng offset vào kết quả âm của subList | Cộng vào dữ liệu đang mã hóa | Giải mã rồi chuyển chỉ số |
| Gọi sort hoặc add trên list chỉ đọc | Tìm đọc được nhưng sửa không được | Chuẩn bị list mutable riêng khi cần |
| Giữ chỉ số qua nhiều cập nhật | Chỉ số có thể trỏ phần tử khác | Dùng trong phạm vi dữ liệu ổn định |
| Gọi mọi binarySearch là O(log n) thời gian | Bỏ qua truy cập LinkedList và chi phí so sánh | Phân tích cả số so sánh lẫn truy cập |

## 13. Bài tập và lời giải

### Bài 1 — Chỉ số 0

List đã sắp [2,5,9]. Tìm 2 trả 0. Điều kiện if (r > 0) có đúng để kiểm tra tìm thấy không?

**Lời giải:** không. 0 là chỉ số hợp lệ của phần tử đầu. Dùng r >= 0; mọi r âm mới thuộc nhánh không tìm thấy.

### Bài 2 — Giải mã vị trí chèn

List [2,5,9], tìm 7. Kết quả là bao nhiêu, và chèn vào đâu?

**Lời giải:** vị trí chèn là 2, trước 9, nên kết quả là -3. Giải mã bằng -(-3 + 1) = 2. Không dùng giá trị tuyệt đối vì nó cho 3.

### Bài 3 — Biên đầu, cuối và list rỗng

Với [2,5,9], tìm 1 và 12 trả gì? Với list rỗng, tìm một số bất kỳ trả gì?

**Lời giải:** tìm 1 có vị trí chèn 0 nên trả -1. Tìm 12 có vị trí chèn 3 nên trả -4. List rỗng có vị trí chèn 0 nên trả -1.

### Bài 4 — Dãy giảm dần

List [9,7,4] được sắp theo reverseOrder. Tìm 8 bằng comparator đó cho vị trí chèn nào và kết quả nào?

**Lời giải:** chèn tại 1, giữa 9 và 7; kết quả là -2. Tìm bằng thứ tự tự nhiên trên list này vi phạm điều kiện, không được dựa vào một con số trả về cụ thể.

### Bài 5 — Phần tử trùng

List [1,3,3,3,8], tìm 3. Có được khẳng định kết quả là 1 không? lowerBound và upperBound của 3 là gì?

**Lời giải:** không được khẳng định binarySearch chọn 1; bất kỳ chỉ số hợp lệ trong nhóm 1,2,3 đều đáp ứng hợp đồng. Hai biên là 1 và 4; số phần tử bằng khóa là 3.

### Bài 6 — Compare bằng 0 nhưng equals khác

Danh sách Score được sắp theo points. Khóa tìm có tên probe, points = 20. Nếu tìm được Score tên C, points = 20, kết quả có sai vì tên khác không?

**Lời giải:** không sai đối với comparator chỉ theo points. Đó là khớp theo nhóm điểm. Nếu yêu cầu tên cũng phải khớp, cần một quan hệ tìm phù hợp hoặc kiểm tra thêm trong nhóm.

### Bài 7 — Sort ổn định và kết quả đầu tiên

Một phép sort ổn định giữ thứ tự các Score cùng điểm. Từ đó có thể suy binarySearch sẽ trả Score đầu tiên trong nhóm không?

**Lời giải:** không. Tính ổn định là hợp đồng của phép sắp, còn lựa chọn một kết quả trong nhóm là hợp đồng khác. Muốn phần tử đầu phải tìm biên đầu.

### Bài 8 — subList và mã âm

whole là [0,10,20,30,40,50], part = whole.subList(2,5) là [20,30,40]. Tìm 35 trên part trả gì và vị trí chèn tương ứng trong whole là gì?

**Lời giải:** trong part, vị trí chèn là 2 nên trả -3. Chuyển sang whole sau khi giải mã: 2 + 2 = 4. Không cộng offset 2 thẳng vào -3 rồi coi là chỉ số kết quả.

### Bài 9 — Thay thuộc tính của phần tử

List các Product được sắp theo price. Một Product ở giữa bị đổi price thành giá lớn nhất mà vị trí không đổi. Chỉ vì list không có add/remove từ lần sort, binarySearch còn chắc chắn hợp lệ không?

**Lời giải:** không. Thay thuộc tính tham gia so sánh có thể phá thứ tự dù cấu trúc list không đổi kích thước. Cần quản lý cập nhật để duy trì hoặc khôi phục thứ tự.

### Bài 10 — LinkedList và chi phí

Một LinkedList rất lớn đã sắp. Có đúng Collections.binarySearch luôn tìm trong O(log n) thời gian chỉ vì chỉ so sánh O(log n) phần tử không?

**Lời giải:** không. Truy cập/di chuyển trong cấu trúc cũng có chi phí. Hợp đồng thư viện mô tả O(n) bước đi qua liên kết ở cách tìm dựa trên iterator cho list tuần tự lớn, dù số so sánh là O(log n).

### Bài 11 — Comparator chi tiết hơn

List được sắp theo điểm rồi tên. Tìm chỉ theo điểm có nhất thiết sai điều kiện không? Còn list chỉ được sắp theo điểm mà tìm bằng điểm rồi tên thì sao?

**Lời giải:** thứ tự điểm-rồi-tên vẫn giữ không giảm theo điểm, nên tìm theo điểm có thể hợp lệ. Chiều ngược không tự bảo đảm thứ tự tên trong từng nhóm điểm; phải sắp hoặc xác minh theo quan hệ chi tiết dùng để tìm.

### Bài 12 — Nguồn bất biến về cấu trúc

Một List không cho sửa chứa các đối tượng mutable và được giữ qua unmodifiable view. Nó đã sắp theo một trường mutable. Chặn add/set qua view có đủ giữ điều kiện tìm kiếm không?

**Lời giải:** không. Chủ sở hữu nguồn hoặc người giữ phần tử còn có thể sửa trạng thái làm thay đổi quan hệ so sánh. Cần mô hình phần tử và quyền cập nhật phù hợp, không chỉ lớp bọc cấu trúc.

## 14. Tài liệu đối chiếu

- [Collections.binarySearch — điều kiện thứ tự, mã vị trí chèn và độ phức tạp](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#binarySearch(java.util.List,T,java.util.Comparator)).
- [List.sort — comparator và tính ổn định của phép sắp](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator)).
- [Comparator — quan hệ thứ tự và sự khác nhau với equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html).
- [Comparable — thứ tự tự nhiên](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html).
- [RandomAccess — khả năng truy cập theo chỉ số](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/RandomAccess.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collections-utilities-003">min, max, frequency và disjoint</a>

<details>
<summary>Xem chi tiết</summary>

# min, max, frequency và disjoint

Nhiều câu hỏi về collection chỉ cần một kết quả nhỏ: phần tử thấp nhất, phần tử cao nhất, số lần một giá trị xuất hiện hoặc hai nhóm có chồng lấp hay không. Không cần sắp xếp cả danh sách hay tự tạo một collection giao nếu chỉ muốn trả lời những câu hỏi ấy.

`Collections.min` và `Collections.max` chọn một phần tử theo thứ tự. `Collections.frequency` đếm phần tử bằng một giá trị theo `equals`. `Collections.disjoint` trả lời liệu hai collection có **không có phần tử chung** hay không.

Điểm dễ sai là xem mọi phương thức đều dùng cùng một nghĩa của “bằng nhau”. Comparator, `equals` và cách xác định thành viên của một collection có thể khác nhau. Bài này tách rõ từng hợp đồng, rồi kiểm chứng bằng Java 17 chạy được.

## 1. Chọn phương thức từ câu hỏi cần trả lời

| Câu hỏi | Phương thức | Kết quả | Có thay thứ tự hoặc sửa collection không? |
| --- | --- | --- | --- |
| Phần tử nhỏ nhất theo một thứ tự là gì? | `min(collection)` hoặc `min(collection, comparator)` | Một phần tử `T` | Không cần sửa |
| Phần tử lớn nhất theo một thứ tự là gì? | `max(collection)` hoặc `max(collection, comparator)` | Một phần tử `T` | Không cần sửa |
| Có bao nhiêu phần tử bằng giá trị đang hỏi? | `frequency(collection, value)` | `int` | Không cần sửa |
| Hai collection có hoàn toàn không chung phần tử không? | `disjoint(left, right)` | `boolean` | Không cần sửa |

Các phương thức nhận `Collection`, không chỉ `List`. List, set và nhiều collection khác có thể là đầu vào hợp lệ. Chúng không đòi collection hỗ trợ `add`, `remove` hay `set`.

Map không là kiểu con của `Collection`. Khi làm việc với map, chọn đúng góc nhìn như `keySet()`, `values()` hoặc `entrySet()` để câu hỏi có ý nghĩa.

Một kết quả nhỏ không có nghĩa công việc luôn hằng số. Để biết phần tử lớn nhất trong một collection chưa có cấu trúc hỗ trợ riêng, thường phải duyệt các phần tử. Phải phân biệt lượng dữ liệu trả về với lượng dữ liệu cần đọc.

## 2. `min` và `max` theo thứ tự tự nhiên

### Không cần sắp xếp đầu vào

Đoạn sau là mã minh họa trong một phương thức có đủ import:

~~~java
List<Integer> values = List.of(9, 2, 7, 2);

int lowest = Collections.min(values);
int highest = Collections.max(values);
~~~

`lowest` là 2 và `highest` là 9. List vẫn có thứ tự `[9, 2, 7, 2]`; hai số 2 vẫn có mặt. Không có bước sort, loại trùng hay thay phần tử trong list.

`min` và `max` trả **phần tử**, không trả chỉ số của nó. Với collection không có truy cập chỉ số, câu hỏi về chỉ số vốn không có ý nghĩa.

### Thứ tự tự nhiên đến từ `Comparable`

Overload không có comparator yêu cầu các phần tử có thứ tự tự nhiên và so sánh được với nhau. Với `Integer`, đó là thứ tự số.

Nếu một lớp nghiệp vụ không triển khai `Comparable`, không nên mong `min` tự đoán rằng phải dùng giá, tuổi hay tên. Hãy cung cấp comparator phù hợp.

Nếu lách generic bằng raw type rồi trộn những kiểu không so sánh được, lời gọi có thể ném `ClassCastException` khi cần so sánh. Generic đúng giúp diễn đạt và kiểm tra ý định ở thời điểm biên dịch. [Thứ tự tự nhiên và Comparable, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html).

### Cơ chế duyệt và giữ một ứng viên

Có thể hiểu cách tìm min như sau:

1. Lấy một phần tử làm ứng viên ban đầu.
2. Đọc lần lượt các phần tử còn lại.
3. Khi gặp một phần tử nhỏ hơn theo thứ tự đang dùng, thay ứng viên.
4. Sau khi duyệt xong, ứng viên là một phần tử cực tiểu.

Max làm tương tự nhưng giữ ứng viên lớn hơn. Cách này không cần đảo thứ tự các phần tử và không cần tạo một list đã sắp xếp.

Đó cũng là lý do list không sửa được vẫn dùng được với các truy vấn này. Không có quyền sửa vật chứa không đồng nghĩa không được tính toán trên nội dung.

## 3. Comparator quyết định nghĩa của “nhỏ nhất” và “lớn nhất”

### Chọn một trường nghiệp vụ

Giả sử mỗi đề xuất có mã và một con số chi phí:

~~~java
record Offer(String code, int cost) {
}

Comparator<Offer> byCost =
        Comparator.comparingInt(Offer::cost);
~~~

Khi đó:

~~~java
Offer cheapest = Collections.min(offers, byCost);
Offer mostExpensive = Collections.max(offers, byCost);
~~~

Đây là các mảnh mã minh họa với biến `offers` đã tồn tại; chương trình đầy đủ ở dưới khai báo toàn bộ dữ liệu.

Kết quả là một `Offer`, không phải riêng giá trị `cost`. Muốn lấy con số, đọc `cheapest.cost()` sau khi đã chọn phần tử.

Comparator nên mô tả một quan hệ nhất quán và ổn định trong suốt lần duyệt. Không so bằng phép trừ số nguyên có thể tràn; dùng `Integer.compare` hoặc `comparingInt`. Không đổi trường khóa so sánh giữa chừng. [Hợp đồng Comparator, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html).

### Comparator đảo chiều cũng đảo ý nghĩa min/max

Với các số `[9, 2, 7, 2]`:

~~~java
int firstInDescendingOrder =
        Collections.min(values, Comparator.reverseOrder());
~~~

Kết quả là 9. Nó là phần tử “nhỏ nhất” **theo comparator giảm dần**, dù là số lớn nhất theo thứ tự tự nhiên.

Tương tự, `max(values, Comparator.reverseOrder())` trả 2. Đừng đọc tên `min` tách khỏi quy tắc so sánh đã truyền.

Nếu mục tiêu là chọn số lớn nhất tự nhiên, `max(values)` dễ đọc hơn việc dùng `min` với comparator đảo. Comparator đảo hữu ích khi nó là một phần của một quy tắc nghiệp vụ, nhưng tên biến vẫn phải thể hiện đúng ý nghĩa.

### Comparator của một set không tự được dùng

Một `TreeSet` có thể duyệt theo comparator riêng, chẳng hạn giảm dần. Điều đó không đổi hợp đồng của `Collections.min(collection)` thành “lấy phần tử đầu theo comparator của set”.

Với set giảm dần gồm 2, 9, 4:

- `tree.first()` là 9 theo thứ tự mà set duy trì.
- `Collections.min(tree)` là 2 theo thứ tự tự nhiên.
- `Collections.min(tree, tree.comparator())` là 9 theo comparator giảm dần được truyền rõ.

Phân biệt “thứ tự cấu trúc duy trì” và “thứ tự truy vấn chọn dùng”. Nếu muốn dùng quy tắc của set, phải truyền nó hoặc dùng thao tác chuyên biệt có đúng hợp đồng. [Cách TreeSet dùng comparator, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html).

### Nhiều phần tử cùng cực trị

Giả sử A và B đều có `cost` bằng 50. Nếu comparator chỉ so chi phí, cả hai đều là ứng viên hợp lệ cho min.

Hợp đồng chọn phần tử cực trị không nên được diễn giải thành một lời hứa chọn mã cụ thể trong nhóm hòa. Nếu yêu cầu “chi phí nhỏ nhất; khi bằng nhau thì mã nhỏ nhất”, viết tiêu chí phụ:

~~~java
Comparator<Offer> byCostThenCode =
        Comparator.comparingInt(Offer::cost)
                .thenComparing(Offer::code);

Offer selected = Collections.min(offers, byCostThenCode);
~~~

Không dựa vào một lần chạy hiện tại để gán ý nghĩa nghiệp vụ cho thứ tự hòa chưa được quy định. Nếu chỉ quan tâm chi phí min, kiểm thử giá trị chi phí sẽ chính xác hơn việc ép một mã phải thắng khi comparator chưa phân biệt các mã.

## 4. Collection rỗng, `null` và giá trị trả về

### Không có min/max khi không có phần tử

`Collections.min` và `Collections.max` ném `NoSuchElementException` trên collection rỗng. Chúng không trả 0, không tạo một đối tượng mặc định và không dùng `null` như dấu hiệu chung cho “không có phần tử”. [Hợp đồng min/max trong Collections, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#min(java.util.Collection)).

Cách xử lý phụ thuộc API của ứng dụng:

- Nếu không có dữ liệu là trạng thái hợp lệ, kiểm tra và biểu diễn sự vắng mặt rõ ràng.
- Nếu nghiệp vụ bắt buộc có dữ liệu, báo lỗi theo ngữ cảnh.
- Chỉ dùng một giá trị mặc định khi nó thật sự có ý nghĩa, không phải để làm biến mất ngoại lệ.

Ví dụ, số 0 không luôn là min phù hợp của tập dữ liệu vắng: nó có thể bị hiểu nhầm là một quan sát có giá trị 0.

Kiểm tra `isEmpty` rồi đọc dữ liệu cần cùng một trạng thái nguồn ổn định. Nếu bên khác có thể sửa collection giữa hai bước, phép kiểm tra trước không tự tạo một giao dịch.

### `null` chỉ được xếp nếu quy tắc cho phép

Một collection cho chứa `null` chưa có nghĩa thứ tự tự nhiên xử lý `null` khi so sánh. Với dữ liệu có `null`, truyền comparator có chính sách rõ:

~~~java
Comparator<String> nullLast =
        Comparator.nullsLast(Comparator.naturalOrder());

String min = Collections.min(values, nullLast);
String max = Collections.max(values, nullLast);
~~~

Nếu `values` là `["x", null, "x", null]`, min là `"x"` còn max là `null`. `null` đang được xem là lớn hơn các chuỗi không `null`, nên nó có thể là **phần tử kết quả hợp lệ**.

Với `nullsFirst`, hướng ưu tiên `null` đảo lại. Truyền comparator `null` cho overload comparator có nghĩa quay về thứ tự tự nhiên, không có nghĩa bỏ qua phần tử `null`.

Không dùng một collection đặc biệt ít phần tử để suy ra việc so sánh `null` luôn an toàn: có trường hợp thuật toán chưa phải so sánh cặp nào. Chính sách của comparator mới là căn cứ khi cần so sánh.

Nếu mô hình cho phép một min/max thực sự là `null`, đừng gộp kết quả ấy với “collection rỗng”. Một wrapper không biểu diễn được giá trị `null` hiện diện có thể làm mất phân biệt này; thiết kế kết quả phải theo mô hình cụ thể.

### Kết quả không là bản sao phòng vệ

Khi phần tử là đối tượng sửa được, min/max trả một phần tử đã có trong collection, không tự nhân bản nó. Nếu người nhận sửa đối tượng được chọn, collection vẫn giữ tham chiếu tới đối tượng ấy.

Các truy vấn không sửa collection trong quá trình chọn không đồng nghĩa đối tượng kết quả tự có quyền đọc riêng. Cần áp dụng chính sách quyền sở hữu hoặc biểu diễn dữ liệu đã chốt nếu API không muốn lộ đối tượng sống.

## 5. `frequency`: đếm theo `equals`, không theo comparator

### Câu hỏi là “có bao nhiêu phần tử bằng giá trị này?”

~~~java
List<String> labels = Arrays.asList("x", null, "x", null);

int xCount = Collections.frequency(labels, new String("x"));
int nullCount = Collections.frequency(labels, null);
~~~

Cả hai kết quả đều là 2. Đối tượng chuỗi mới vẫn bằng các chuỗi `"x"` theo `equals`; không cần cùng tham chiếu.

Định nghĩa hình thức là đếm các phần tử `e` thỏa `Objects.equals(value, e)`. Khi giá trị đang hỏi là `null`, các phần tử `null` được đếm. Khi không có phần tử phù hợp, kết quả là 0. [API frequency, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#frequency(java.util.Collection,java.lang.Object)), [Objects.equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html#equals(java.lang.Object,java.lang.Object)).

Collection rỗng cũng trả 0. Ngược lại, truyền chính collection là `null` không phải một collection rỗng và sẽ gây `NullPointerException`.

### Không có overload comparator

Với `Offer("A", 50)` và `Offer("B", 50)`, comparator theo cost trả 0, nhưng hai record không bằng nhau theo mọi thành phần.

Nếu list có hai đề xuất ấy:

- Đếm một `new Offer("A", 50)` có thể cho 1.
- Đếm một `new Offer("X", 50)` cho 0 dù chi phí cũng là 50.
- Muốn đếm mọi đề xuất có cost 50, cần điều kiện kiểm tra trường hoặc một bước ánh xạ phù hợp.

Một vòng lặp thể hiện rõ ý định:

~~~java
int matchingCost = 0;
for (Offer offer : offers) {
    if (offer.cost() == 50) {
        matchingCost++;
    }
}
~~~

Không cố truyền comparator cho `frequency`; API không có dạng ấy. Phải chọn giữa “bằng nhau theo mô hình đối tượng” và “khớp một điều kiện nghiệp vụ”.

### Không dùng cách tra cứu của set để thay định nghĩa tần suất

Một `TreeSet<String>` không phân biệt hoa thường có thể giữ `"red"` và trả `true` khi hỏi `contains("RED")`. Tuy vậy, `frequency(set, "RED")` đếm theo `String.equals`, nên không đếm phần tử `"red"` ấy.

Không có mâu thuẫn: hai lời gọi đang dùng hai quy tắc khác nhau. Trong trường hợp một set tuân thủ ngữ nghĩa `equals` thông thường, tần suất của một giá trị là 0 hoặc 1; với các cấu trúc dùng quy tắc khác, hãy trở về đúng hợp đồng của từng thao tác.

`frequency` cũng không đếm chuỗi con. Một list chứa `"Java"` hai lần không làm tần suất của `"Ja"` bằng 2. Nó đếm các phần tử nguyên vẹn bằng đối tượng mục tiêu.

## 6. `disjoint`: `true` nghĩa là không có phần tử chung

### Đọc dấu phủ định đúng chiều

~~~java
List<String> requested = List.of("READ", "WRITE", "READ");
Set<String> blocked = Set.of("DELETE", "WRITE");

boolean noOverlap = Collections.disjoint(requested, blocked);
~~~

Kết quả là `false` vì có phần tử chung `"WRITE"`. Nếu muốn biến diễn đạt “có chồng lấp”, dùng phủ định rõ:

~~~java
boolean overlaps = !Collections.disjoint(requested, blocked);
~~~

Tên biến là một phần quan trọng giúp đọc đúng logic. Đừng đặt tên `hasCommon` rồi gán thẳng kết quả `disjoint` nếu điều đó làm đảo ý nghĩa.

Phần tử lặp không đổi câu trả lời tồn tại: chỉ cần một giá trị chung là đủ để `disjoint` trả `false`. Phương thức không trả số lần chung và không tạo collection giao.

### Rỗng và cùng một đối tượng collection

Với collection hợp lệ và các phần tử truy vấn tương thích:

- Một bên rỗng thì không có phần tử chung, nên kết quả là `true`.
- Cả hai bên rỗng vẫn là `true`.
- Truyền cùng một collection không rỗng ở cả hai phía cho `false`.
- Truyền cùng collection rỗng ở cả hai phía cho `true`.

Một collection không rỗng dĩ nhiên có phần tử chung với chính nó. Không cần tạo bản sao chỉ để gọi phép kiểm tra này.

### Có thể dừng khi tìm được phần tử chung

Ý tưởng truy vấn là đọc phần tử từ một collection và kiểm tra thành viên ở collection kia. Khi tìm được một phần tử chung, đã đủ trả `false`; không cần liệt kê toàn bộ giao.

Không nên dựa vào thứ tự gọi `contains` hoặc mặc định phía trái luôn là phía được duyệt. Cách triển khai được quyền chọn một trong hai phía hoặc một cách tính tương đương.

Điều này cũng ảnh hưởng thiết kế các collection có hạn chế loại phần tử hoặc quy tắc so khớp đặc biệt, như phần tiếp theo.

## 7. Điều kiện tương thích của `disjoint`

### Các phần tử cần là đầu vào truy vấn hợp lệ cho cả hai phía

Một collection có `null` và collection kia không cho `null` có thể gây `NullPointerException` tùy thao tác tra cứu được thực hiện. Kiểu phần tử không hợp lệ với collection kia cũng có thể dẫn đến `ClassCastException`.

Vì vậy, không suy rằng “hai generic khác nhau không thể chung phần tử nên luôn trả `true`”. Lời gọi có thể đi qua cách tra cứu có giới hạn kiểu cụ thể.

Tương tự, `disjoint` không phải bộ lọc tự bỏ `null`. Nếu cần dùng `null` như một phần tử chung, hai đầu vào phải hỗ trợ việc truy vấn ấy. Chương trình dùng list cho `null` và `HashSet` cho `null` để thí nghiệm có điều kiện hợp lệ.

`Collection` cho phép những khác biệt có quy định về phần tử không hợp lệ và ngoại lệ truy vấn. [Hợp đồng Collection, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html).

### Hai quy tắc bình đẳng khác nhau có thể làm kết quả không được xác định

Tài liệu `disjoint` nêu rõ: nếu một collection dùng phép xác định bằng nhau phi chuẩn, cả hai phải dùng cùng phép xác định ấy, nếu không kết quả của phương thức không được xác định.

Ví dụ cần chú ý:

- `SortedSet` dùng một thứ tự không nhất quán với `equals`, chẳng hạn quy tắc so chuỗi không phân biệt hoa thường.
- Tập khóa từ `IdentityHashMap` dùng định danh tham chiếu thay vì `equals`.

Không viết một kết quả `true`/`false` được cho là bắt buộc khi trộn những ngữ nghĩa không tương thích như vậy. Đổi vị trí hai đối số hoặc thêm một phần tử phụ không phải cách sửa đúng vấn đề.

Có hai hướng hợp lệ: chuẩn hóa dữ liệu sang một quy tắc nghiệp vụ thống nhất, hoặc bảo đảm cả hai cấu trúc thật sự dùng cùng quan hệ tương đương. “Cùng kiểu phần tử là String” chưa chứng minh hai quan hệ so khớp giống nhau.

Chương trình sẽ thử hai `TreeSet` cùng dùng `String.CASE_INSENSITIVE_ORDER`, một bên chứa `"red"` và một bên chứa `"RED"`. Chúng có cùng quy tắc không phân biệt hoa thường, nên `disjoint` cho `false`. Chương trình **không** trộn một set ấy với một set dựa trên `String.equals` rồi đoán kết quả.

Đây là giới hạn hợp đồng được công bố, không phải một tối ưu riêng chỉ cần nhớ ở một phiên bản Java. [API disjoint, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#disjoint(java.util.Collection,java.util.Collection)).

### Muốn danh sách phần tử chung là một câu hỏi khác

Nếu cần biết chính xác những mã nào chồng lấp để hiển thị lỗi, một boolean chưa đủ. Hãy thực hiện phép tìm giao phù hợp với quy tắc bình đẳng và quyền sửa dữ liệu.

Không gọi `retainAll` trực tiếp trên nguồn người khác chỉ để tính giao nếu không được phép sửa nguồn. Nếu dùng cách tính trên bản sao, vẫn phải giữ đúng ngữ nghĩa phần tử và xem việc lặp có cần giữ hay không.

## 8. Áp dụng cho map qua đúng góc nhìn

Giả sử:

~~~java
Map<String, Integer> quantities =
        Map.of("small", 4, "large", 9, "medium", 4);
~~~

Map không tự là đầu vào của các phương thức nhận `Collection`. Các góc nhìn trả lời những câu hỏi khác nhau:

| Góc nhìn | Ví dụ câu hỏi |
| --- | --- |
| `quantities.keySet()` | Các mã có chồng lấp với một nhóm mã khác không? |
| `quantities.values()` | Số lượng lớn nhất là bao nhiêu? Có bao nhiêu ánh xạ có giá trị 4? |
| `quantities.entrySet()` | Ánh xạ nào có giá trị lớn nhất theo comparator của entry? |

`Collections.max(quantities.values())` trả 9. `frequency(quantities.values(), 4)` trả 2, vì hai ánh xạ có cùng giá trị vẫn đóng góp hai phần tử trong góc nhìn giá trị.

Nếu cần trả cả khóa chiến thắng, chỉ lấy max của `values` rồi quét tìm khóa có thể chưa diễn đạt quy tắc hòa. Có thể chọn entry bằng comparator theo giá trị và thêm tiêu chí khóa nếu cần.

Các góc nhìn của map có thể gắn với nguồn. Chọn xong một entry cũng cần xét hợp đồng vòng đời của entry nếu định giữ nó lâu; không suy rằng một truy vấn đọc đã tự tạo bản snapshot độc lập. [Các collection view của Map, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

## 9. Chương trình hoàn chỉnh với stdout chính xác

Lưu mã vào **`CollectionQueriesDemo.java`**. Dữ liệu số và đề xuất được giữ trong list không sửa được để chứng minh các truy vấn không cần quyền cập nhật vật chứa.

Với hai đề xuất cùng chi phí thấp nhất, chương trình chỉ khẳng định giá trị chi phí ở phép chọn chưa có tiêu chí phụ. Phép chọn mã cụ thể chỉ xuất hiện sau khi bổ sung tiêu chí mã vào comparator.

~~~java
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

public class CollectionQueriesDemo {
    record Offer(String code, int cost) {
    }

    private static void noElement(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " unexpectedly succeeded");
        } catch (NoSuchElementException ex) {
            System.out.println(label + "=NoSuchElementException");
        }
    }

    public static void main(String[] args) {
        List<Integer> numbers = List.of(9, 2, 7, 2);
        System.out.println("numbers.min=" + Collections.min(numbers));
        System.out.println("numbers.max=" + Collections.max(numbers));
        System.out.println("numbers.minReverse="
                + Collections.min(numbers, Comparator.reverseOrder()));
        System.out.println("numbers.maxReverse="
                + Collections.max(numbers, Comparator.reverseOrder()));
        System.out.println("numbers.after=" + numbers);

        List<Offer> offers = List.of(
                new Offer("B", 50),
                new Offer("A", 50),
                new Offer("C", 80));
        Comparator<Offer> byCost = Comparator.comparingInt(Offer::cost);
        Offer cheapest = Collections.min(offers, byCost);
        System.out.println("offer.minCost=" + cheapest.cost());
        System.out.println("offer.maxCode="
                + Collections.max(offers, byCost).code());
        System.out.println("offer.minWithTieBreak="
                + Collections.min(
                        offers, byCost.thenComparing(Offer::code)).code());
        System.out.println("frequency.offerA="
                + Collections.frequency(offers, new Offer("A", 50)));
        System.out.println("frequency.offerX="
                + Collections.frequency(offers, new Offer("X", 50)));
        int cost50 = 0;
        for (Offer offer : offers) {
            if (offer.cost() == 50) {
                cost50++;
            }
        }
        System.out.println("predicate.cost50=" + cost50);

        List<String> nullable = Arrays.asList("x", null, "x", null);
        System.out.println("frequency.null="
                + Collections.frequency(nullable, null));
        System.out.println("frequency.newString="
                + Collections.frequency(nullable, new String("x")));
        System.out.println("frequency.missing="
                + Collections.frequency(nullable, "missing"));
        Comparator<String> nullLast =
                Comparator.nullsLast(Comparator.naturalOrder());
        System.out.println("nulls.minLast="
                + Collections.min(nullable, nullLast));
        System.out.println("nulls.maxLast="
                + Collections.max(nullable, nullLast));

        TreeSet<Integer> reverseSet =
                new TreeSet<>(Comparator.reverseOrder());
        reverseSet.addAll(List.of(2, 9, 4));
        System.out.println("tree.first=" + reverseSet.first());
        System.out.println("tree.minNatural="
                + Collections.min(reverseSet));
        System.out.println("tree.minOwnComparator="
                + Collections.min(reverseSet, reverseSet.comparator()));

        TreeSet<String> insensitive =
                new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        insensitive.add("red");
        TreeSet<String> sameRule =
                new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sameRule.add("RED");
        System.out.println("case.containsRED="
                + insensitive.contains("RED"));
        System.out.println("case.frequencyRED="
                + Collections.frequency(insensitive, "RED"));
        System.out.println("case.disjointSameRule="
                + Collections.disjoint(insensitive, sameRule));

        List<String> requested = List.of("READ", "WRITE", "READ");
        Set<String> blocked = Set.of("DELETE", "WRITE");
        System.out.println("disjoint.common="
                + Collections.disjoint(requested, blocked));
        System.out.println("disjoint.separate="
                + Collections.disjoint(requested, Set.of("ARCHIVE")));
        System.out.println("disjoint.empty="
                + Collections.disjoint(
                        Collections.<String>emptyList(), requested));
        System.out.println("disjoint.selfNonempty="
                + Collections.disjoint(requested, requested));
        List<String> empty = Collections.emptyList();
        System.out.println("disjoint.selfEmpty="
                + Collections.disjoint(empty, empty));
        Set<String> containsNull = new HashSet<>();
        containsNull.add(null);
        System.out.println("disjoint.commonNull="
                + Collections.disjoint(nullable, containsNull));

        Map<String, Integer> quantities =
                Map.of("small", 4, "large", 9, "medium", 4);
        System.out.println("map.maxValue="
                + Collections.max(quantities.values()));
        System.out.println("map.frequency4="
                + Collections.frequency(quantities.values(), 4));
        System.out.println("map.keysDisjoint="
                + Collections.disjoint(
                        quantities.keySet(), Set.of("unknown")));

        noElement("empty.min",
                () -> Collections.min(Collections.<Integer>emptyList()));
        noElement("empty.max",
                () -> Collections.max(Collections.<Integer>emptyList()));
        System.out.println("empty.frequency="
                + Collections.frequency(empty, "x"));
        List<Integer> single = List.of(5);
        System.out.println("single.minMax="
                + Collections.min(single) + "/" + Collections.max(single));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 CollectionQueriesDemo.java
java CollectionQueriesDemo
~~~

Cần JDK 17 trở lên cho lệnh biên dịch này. Mã đã được biên dịch với `--release 17` và chạy trên OpenJDK 24.0.2, thoát thành công. Stdout chính xác:

~~~text
numbers.min=2
numbers.max=9
numbers.minReverse=9
numbers.maxReverse=2
numbers.after=[9, 2, 7, 2]
offer.minCost=50
offer.maxCode=C
offer.minWithTieBreak=A
frequency.offerA=1
frequency.offerX=0
predicate.cost50=2
frequency.null=2
frequency.newString=2
frequency.missing=0
nulls.minLast=x
nulls.maxLast=null
tree.first=9
tree.minNatural=2
tree.minOwnComparator=9
case.containsRED=true
case.frequencyRED=0
case.disjointSameRule=false
disjoint.common=false
disjoint.separate=true
disjoint.empty=true
disjoint.selfNonempty=false
disjoint.selfEmpty=true
disjoint.commonNull=false
map.maxValue=9
map.frequency4=2
map.keysDisjoint=true
empty.min=NoSuchElementException
empty.max=NoSuchElementException
empty.frequency=0
single.minMax=5/5
~~~

### Giải thích kết quả theo từng quan hệ so khớp

**`numbers`:** min tự nhiên là 2, max tự nhiên là 9. Comparator đảo khiến min là 9 và max là 2. List vẫn có thứ tự gốc; không cần sort để chọn cực trị.

**`offer`:** chi phí thấp nhất là 50; mã của đề xuất cao nhất là C vì chi phí 80 là duy nhất. Khi thêm tiêu chí mã để phá hòa ở chi phí thấp nhất, A được chọn.

**`frequency.offerA`, `frequency.offerX` và `predicate.cost50`:** record A/50 xuất hiện một lần; record X/50 không xuất hiện. Tuy vậy có hai đề xuất cùng chi phí 50. Một điều kiện trên cost không giống phép `equals` của toàn record.

**`frequency.null` và `frequency.newString`:** target `null` được xử lý có nghĩa; một chuỗi mới khác tham chiếu vẫn bằng `"x"` và được đếm hai lần.

**`nulls`:** comparator xếp `null` cuối làm max trả `null` trên collection không rỗng. Đây không phải trường hợp không có kết quả do dữ liệu rỗng.

**`tree`:** phần tử đầu theo thứ tự giảm của set là 9; min tự nhiên vẫn là 2; min được truyền comparator riêng của set là 9.

**`case`:** `contains("RED")` dùng quy tắc set và trả `true`, còn `frequency` dùng `String.equals` nên trả 0. Hai set trong phép `disjoint` dùng cùng quy tắc không phân biệt hoa thường, vì vậy chúng có chung thành viên theo quy tắc ấy.

**`disjoint`:** có WRITE chung cho kết quả `false`; nhóm ARCHIVE riêng cho `true`. Các biên rỗng/cùng đối tượng được thử riêng. Hai collection hỗ trợ `null` nhận ra `null` là một phần tử chung.

**`map`:** max trên giá trị là 9 và có hai giá trị 4. Kiểm tra mã `unknown` trên tập khóa không tìm thấy phần tử chung.

**`empty` và `single`:** min/max không có phần tử để trả nên ném đúng `NoSuchElementException`. Tần suất trên list rỗng bằng 0. Với một phần tử số 5, cả min và max đều là 5.

Hàm `noElement` khiến chương trình thất bại nếu một truy vấn rỗng bất ngờ không ném ngoại lệ. Chương trình không dựa vào nội dung thông báo ngoại lệ hoặc thứ tự duyệt của hash set/map để tạo stdout.

## 10. Chi phí và lựa chọn cho truy vấn lặp lại

### Min/max duyệt thay vì sort

Min hoặc max trên collection thông thường cần một lượt duyệt và giữ một ứng viên. Khi phép so sánh có chi phí hằng và việc duyệt bình thường tuyến tính, công việc là `O(n)` với lượng trạng thái phụ nhỏ.

Sort chỉ để lấy một cực trị thường làm nhiều việc hơn và có thể còn thay thứ tự đầu vào nếu sort trực tiếp. Chỉ sort khi bạn thật sự cần cả thứ tự đã sắp xếp hoặc một bước sau sử dụng nó.

Gọi min rồi max là hai lượt duyệt; tổng vẫn là `O(n)` theo bậc tăng trưởng, không thành `O(n²)`. Nếu cần đồng thời nhiều thống kê trên nguồn đắt tiền, cân nhắc một lượt tổng hợp phù hợp thay vì lặp lại mọi truy vấn.

Với cấu trúc đã có thứ tự và tiêu chí truy vấn trùng thứ tự nó duy trì, các thao tác chuyên biệt như đầu/cuối có thể phù hợp hơn. Phải giữ đúng ý nghĩa comparator; không mặc định `first` của set giảm là số nhỏ nhất tự nhiên.

### Frequency đơn giản cho vài câu hỏi, bảng đếm cho nhiều câu hỏi

Mỗi lần gọi `frequency` phải xét các phần tử theo quy tắc bình đẳng của nó. Nếu hỏi `q` giá trị trên list có `n` phần tử bằng cách gọi riêng từng lần, có thể phải thực hiện công việc cỡ `q × n` phép xét.

Nếu cần thống kê mọi giá trị hoặc tra tần suất nhiều lần, có thể xây dựng bảng đếm một lần:

~~~java
Map<String, Integer> counts = new HashMap<>();
for (String value : values) {
    counts.merge(value, 1, Integer::sum);
}
~~~

Đây là mã minh họa với `List<String> values` và đủ import. Cần giữ hợp đồng `equals`/`hashCode` đúng và biết chính sách `null` của map dùng làm chỉ mục.

`HashMap` hỗ trợ khóa `null` và có chi phí get/put hằng theo điều kiện phân tán mã băm phù hợp. Đó không phải bảo đảm cho mọi cách cài `Map` hoặc cho một hàm `equals` tự viết rất tốn kém. [HashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

Bảng đếm cũng cần được cập nhật hoặc dựng lại nếu nguồn thay đổi. Tối ưu truy vấn không được đánh đổi lấy một kết quả đã cũ mà người gọi tưởng còn hiện hành.

### Disjoint phụ thuộc chi phí kiểm tra thành viên

Nếu kiểm tra thành viên ở một list phải quét tuyến tính, so hai list không có phần tử chung có thể cần công việc cỡ tích hai kích thước. Nếu một phía là `HashSet` có tra cứu hiệu quả, phép kiểm tra thường rẻ hơn.

Dựng một hash set riêng có thể có lợi cho nhiều truy vấn, nhưng nó tốn thêm bộ nhớ và phải giữ cùng quan hệ bình đẳng với bài toán. Đừng chuyển một tập theo định danh hay comparator đặc biệt sang `HashSet` rồi nghĩ rằng chỉ có hiệu năng thay đổi.

`HashSet` công bố điều kiện chi phí cho thao tác thành viên dựa trên phân tán mã băm, đồng thời không bảo đảm thứ tự duyệt. [HashSet, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html).

Không có một công thức `O(n + m)` vô điều kiện cho mọi cặp `Collection` tùy chỉnh. Phải xét cách duyệt, phép `contains`, `equals` và dữ liệu đầu vào thực tế.

## 11. Lỗi thường gặp khi ghép các truy vấn

### Dùng min/max như thể trả một con số mặc định trên rỗng

Collection rỗng không có phần tử cực trị. Giá trị mặc định phải là quyết định của API ứng dụng; min/max không tự tạo quyết định ấy.

### Chọn phần tử theo comparator rồi đếm bằng `frequency` để đếm mọi phần tử hòa

Một min theo cost có thể là A/50. `frequency(offers, selected)` đếm những record bằng A/50, không đếm mọi record có cost 50. Muốn số ứng viên hòa, kiểm tra `comparator.compare(element, selected) == 0` với comparator hợp lệ hoặc kiểm tra đúng trường cần thiết.

### Hiểu `disjoint` là “có giao”

Tên hàm mang ý nghĩa không chung phần tử. `true` là không chồng lấp; `false` là có ít nhất một phần tử chung. Viết tên biến hoặc phủ định rõ để tránh đảo nhánh.

### Đếm trong set rồi dùng kết quả để suy số lần trong nguồn list

Chuyển list thành set có thể làm mất số lần lặp theo quan hệ của set. `frequency(set, x)` không khôi phục được số lần x trong list ban đầu. Muốn tần suất, đếm trên nguồn còn giữ số lần xuất hiện hoặc dùng bảng đếm được xây từ nguồn ấy.

### Tưởng truy vấn chỉ đọc là ảnh chụp nguyên tử

Min/max/frequency/disjoint không chủ động sửa đầu vào, nhưng nhiều lần đọc không tự diễn ra ở cùng một trạng thái nếu nguồn bị cập nhật đồng thời.

Một kết quả “không có mã trùng” ở thời điểm kiểm tra không tự bảo đảm ngay sau đó việc thêm mã sẽ không xung đột với một cập nhật khác. Nếu việc kiểm tra và hành động phải thống nhất, cần một giao thức phù hợp với nơi sở hữu dữ liệu.

Không lấy việc không thấy `ConcurrentModificationException` làm bằng chứng nguồn không đổi. Hợp đồng đồng thời của collection và cách công bố dữ liệu vẫn cần được tôn trọng.

### Tưởng phương thức chọn phần tử là bản sao sâu

Kết quả min/max có thể là cùng đối tượng mà nguồn đang chứa. Nếu cần trả dữ liệu chỉ đọc ổn định ra ngoài, chuyển sang biểu diễn đã chốt hoặc bảo vệ đối tượng theo hợp đồng của ứng dụng.

## 12. Thực hành có đáp án

### Bài 1 — Duyệt hay sắp xếp?

Một list không sửa được chứa `[6, 1, 4, 1]`. Cần biết min và max nhưng giữ nguyên thứ tự list.

**Câu hỏi:** Có cần tạo `ArrayList` rồi sort không? Hai kết quả là gì?

**Đáp án:** Không cần. `Collections.min` trả 1 và `Collections.max` trả 6 mà không sửa thứ tự. Hai số 1 vẫn tồn tại. Chỉ tạo bản sắp xếp nếu cần dùng cả thứ tự ấy cho mục đích khác.

### Bài 2 — Min theo thứ tự giảm

Với `[3, 8, 5]`, gọi `Collections.min(values, Comparator.reverseOrder())`.

**Câu hỏi:** Kết quả là 3 hay 8? Vì sao tên min không tự quyết định thứ tự tự nhiên?

**Đáp án:** Kết quả là 8. Trong thứ tự giảm do comparator quy định, 8 đứng trước 5 và 3, nên là cực tiểu theo chính thứ tự ấy. Muốn min tự nhiên, dùng overload không comparator hoặc comparator tự nhiên.

### Bài 3 — Hai đề xuất cùng chi phí

Có `Offer("B", 20)` và `Offer("A", 20)`. Comparator hiện tại chỉ so cost.

**Câu hỏi:** Nếu kiểm thử bắt buộc A được chọn thì đã đủ hợp đồng chưa? Làm gì nếu yêu cầu thật sự là cost thấp nhất rồi mã nhỏ nhất?

**Đáp án:** Chưa đủ: comparator hiện tại coi cả hai hòa và yêu cầu mã A không được diễn đạt. Dùng `Comparator.comparingInt(Offer::cost).thenComparing(Offer::code)` rồi lấy min. Nếu chỉ quan tâm chi phí, kiểm tra cost bằng 20 thay vì ép một mã.

### Bài 4 — Đếm theo đối tượng hay theo trường?

List có A/20, B/20 và A/20, dùng record `Offer` như chương trình.

**Câu hỏi:** Tần suất của `new Offer("A", 20)` là bao nhiêu? Của `new Offer("X", 20)` là bao nhiêu? Có bao nhiêu phần tử cost 20?

**Đáp án:** Lần lượt là 2, 0 và 3. Hai câu đầu dùng `equals` của toàn record; câu cuối dùng điều kiện trên trường cost. Comparator chỉ so cost không làm record X/20 bằng record A/20.

### Bài 5 — `null` là kết quả hay không có dữ liệu?

List không rỗng chứa `["a", null, "b"]`. Dùng `Comparator.nullsLast(Comparator.naturalOrder())` để lấy max.

**Câu hỏi:** Max là gì? Có được coi đó là bằng chứng list rỗng không?

**Đáp án:** Max là `null` vì comparator đặt `null` lớn hơn mọi chuỗi không `null`. Collection không rỗng. Trên collection rỗng, min/max ném `NoSuchElementException`; không được gộp hai trường hợp nếu mô hình cho phép `null`.

### Bài 6 — Đọc boolean của `disjoint`

A là `["x", "x", "y"]`. B là `["z", "y"]`. C là collection rỗng.

**Câu hỏi:** `disjoint(A, B)`, `disjoint(A, C)` và `disjoint(A, A)` trả gì với các collection dùng `equals` thông thường và phần tử tương thích?

**Đáp án:** Lần lượt là `false`, `true` và `false`. Có y chung giữa A/B; collection rỗng không chung phần tử với A; A không rỗng nên chung phần tử với chính nó. Số lần x lặp không đổi câu trả lời tồn tại này.

### Bài 7 — Set không phân biệt hoa thường

Một `TreeSet` dùng `String.CASE_INSENSITIVE_ORDER` giữ `"red"`. `contains("RED")` trả `true`.

**Câu hỏi:** Có được suy `frequency(set, "RED") == 1` không? Có nên trộn set ấy với một hash set dùng `String.equals` trong `disjoint` rồi bắt buộc một kết quả boolean cụ thể không?

**Đáp án:** Không cho cả hai suy luận. Frequency dùng `Objects.equals` nên không đếm `"red"` là `"RED"`. Disjoint yêu cầu các phép bình đẳng phi chuẩn phải tương thích ở cả hai collection; trộn các quan hệ ấy làm kết quả không được xác định theo hợp đồng. Chuẩn hóa dữ liệu hoặc dùng cùng một quy tắc thực sự ở cả hai phía.

### Bài 8 — Tần suất nhiều lần và dữ liệu còn thay đổi

Một nguồn có n phần tử, cần trả tần suất cho q mã. Mỗi mã đang gọi `Collections.frequency` riêng.

**Câu hỏi:** Công việc có thể tăng theo đại lượng nào? Có thể cải thiện thế nào và cần giữ điều kiện gì?

**Đáp án:** Có thể phải xét cỡ `q × n` phần tử. Xây bảng đếm từ một lượt duyệt rồi tra theo mã có thể phù hợp cho nhiều truy vấn. Phải giữ đúng `equals`/`hashCode`, chính sách `null`, giới hạn bộ nhớ và sự nhất quán khi nguồn thay đổi. Một bảng đếm không được cập nhật là dữ liệu cũ; nó không còn tự đại diện cho trạng thái nguồn hiện tại.

Khi chọn các tiện ích này, hãy xác định quan hệ đang dùng trước: thứ tự để chọn cực trị, `equals` để đếm hay ngữ nghĩa thành viên tương thích để xét chồng lấp. Sau đó xử lý rõ rỗng, `null` và vòng đời của nguồn; kết quả nhỏ mới phản ánh đúng câu hỏi nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collections-utilities-004">swap, rotate và replaceAll</a>

<details>
<summary>Xem chi tiết</summary>

# swap, rotate và replaceAll

## 1. Ba cách thay đổi một List đang có

Một danh sách tác vụ có thể cần đổi chỗ hai mục, đưa nhóm cuối lên đầu hoặc thay tất cả nhãn cũ bằng nhãn mới. Cả ba nhu cầu đều giữ nguyên số vị trí trong danh sách, nhưng tác động lên dữ liệu theo những quy tắc khác nhau.

Lớp tiện ích `java.util.Collections` có các phương thức tĩnh phục vụ những thao tác này:

| Phương thức | Câu hỏi nó giải quyết | Đối tượng được thay đổi | Giá trị trả về |
| --- | --- | --- | --- |
| `swap(list, i, j)` | Hai vị trí nào cần đổi phần tử cho nhau? | Chính List truyền vào | `void` |
| `rotate(list, distance)` | Cả dãy cần dịch vòng bao nhiêu vị trí? | Chính List truyền vào | `void` |
| `replaceAll(list, oldVal, newVal)` | Mọi lần xuất hiện của giá trị nào cần được thay? | Chính List truyền vào | `boolean` cho biết có phần tử khớp |

Đây là các thao tác **tại chỗ**: gọi phương thức rồi đọc lại List ấy để xem kết quả. Chúng không trả một List mới đã được biến đổi. Nếu hai biến đang giữ cùng một List, việc sửa qua một biến cũng được quan sát qua biến còn lại.

Có thể hình dung List như dãy ô đánh số từ 0. `swap` đổi những gì hai ô đang giữ; `rotate` sắp lại các tham chiếu theo vòng; `replaceAll` tìm các ô có phần tử khớp rồi gán giá trị thay thế. Các thao tác này không tự sao chép sâu đối tượng phần tử.

Khác biệt quan trọng là cách xác định mục tiêu: `swap` dùng chỉ số, `rotate` dùng khoảng dịch, còn `replaceAll` dùng điều kiện bằng nhau của giá trị. Vì vậy, đầu vào đúng cho thao tác này chưa chắc đúng hoặc có ý nghĩa với thao tác kia.

Người học cần biết List có thứ tự, chỉ số bắt đầu từ 0, `get` đọc phần tử và `set` thay phần tử tại một vị trí. Tất cả chương trình dưới đây độc lập, dùng Java 17. Lưu mỗi chương trình đúng tên tệp; các lệnh biên dịch và stdout được ghi ngay sau mã.

## 2. Không đổi kích thước vẫn cần quyền sửa

Một List có thể cho thay phần tử nhưng không cho tăng hoặc giảm số phần tử. Đây là điều kiện đủ cho nhiều thao tác trong bài, vì chúng chủ yếu cần khả năng thay phần tử qua `set` hoặc cơ chế tương ứng của bộ duyệt List.

Ví dụ, `Arrays.asList(array)` tạo một List cố định kích thước, liên hệ trực tiếp với mảng. Không thể dùng nó để thêm một vị trí mới, nhưng có thể thay phần tử ở vị trí đã tồn tại. Các tiện ích thay tham chiếu phần tử có thể hoạt động trên List đó và thay đổi được quan sát ở mảng.

Ngược lại, List từ `List.of`, `List.copyOf` hoặc một lớp bọc chỉ đọc không cho phép thay phần tử. Việc `swap` và `rotate` giữ nguyên `size()` không làm chúng trở thành thao tác chỉ đọc. Lời gọi cần sửa vẫn có thể bị từ chối bằng `UnsupportedOperationException`.

Nếu cần kết quả riêng từ nguồn chỉ đọc, chuẩn bị một List có thể sửa, chẳng hạn `new ArrayList<>(source)`, rồi áp dụng tiện ích lên bản riêng. Quyết định này còn thay đổi quan hệ với nguồn: người dùng giữ nguồn cũ không thấy các thao tác trên phần chứa của bản sao.

Không dùng kiểu `List<?>` trong chữ ký `swap` hay `rotate` để suy ra tham số chỉ đọc. Hai phương thức có thể chuyển những tham chiếu đã nằm trong chính List sang các vị trí khác mà không cần nhận một phần tử mới từ bên ngoài. Khả năng sửa thực tế phụ thuộc đối tượng List.

## 3. swap: đổi đúng hai vị trí

Với `Collections.swap(list, i, j)`, cả `i` lẫn `j` phải nằm trong khoảng từ 0 đến `list.size() - 1`. Chỉ số bằng `size()` không hợp lệ cho một vị trí phần tử hiện có.

Nếu hai chỉ số khác nhau, phần tử cũ ở `i` sang `j` và phần tử cũ ở `j` sang `i`. Những vị trí khác giữ nguyên. Nếu hai chỉ số giống nhau và lời gọi hoàn tất bình thường, nội dung không đổi. Không dùng ca không có thay đổi ấy để đoán khả năng sửa của mọi loại List.

Lưu thành `SwapPositionsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SwapPositionsDemo {
    public static void main(String[] args) {
        List<String> values = new ArrayList<>(List.of("A", "B", "C", "D"));
        List<String> alias = values;
        Collections.swap(values, 0, 3);
        System.out.println("afterSwap=" + values);
        System.out.println("alias=" + alias);

        Collections.swap(values, 1, 1);
        System.out.println("sameIndex=" + values);
        System.out.println("size=" + values.size());

        try {
            Collections.swap(values, 0, values.size());
        } catch (IndexOutOfBoundsException e) {
            System.out.println("badIndex=IndexOutOfBoundsException");
        }

        try {
            Collections.swap(values, -1, 0);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("negativeIndex=IndexOutOfBoundsException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SwapPositionsDemo.java
java SwapPositionsDemo
```

Stdout:

```text
afterSwap=[D, B, C, A]
alias=[D, B, C, A]
sameIndex=[D, B, C, A]
size=4
badIndex=IndexOutOfBoundsException
negativeIndex=IndexOutOfBoundsException
```

Dòng đầu cho thấy hai đầu của dãy đã đổi chỗ. Dòng `alias` có cùng nội dung vì đó là một biến khác giữ chính List đang được sửa; không có bản sao mới được tạo bởi `swap`.

Lời gọi dùng cùng chỉ số 1 không đổi dữ liệu. Hai ca lỗi sau đó kiểm tra riêng cận trên và chỉ số âm. Việc List có bốn phần tử không có nghĩa chỉ số 4 hợp lệ: các vị trí của nó là 0, 1, 2, 3.

`swap` không tìm theo giá trị. Nếu List chứa hai phần tử bằng nhau ở hai vị trí khác nhau, phương thức vẫn được chỉ định bằng chỉ số. Nó cũng không sắp xếp toàn bộ List và không cần một Comparator để xác định phần tử nào “lớn hơn”.

Một cách tự mô phỏng là giữ tạm phần tử ở vị trí đầu, gán phần tử thứ hai vào vị trí đầu rồi gán phần tử tạm vào vị trí thứ hai. Điều cần hiểu là sự bảo toàn hai phần tử, không phải một chuỗi gán cụ thể mà mọi bản triển khai phải dùng.

Hợp đồng chỉ số và kết quả được mô tả tại [Collections.swap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#swap(java.util.List,int,int)).

## 4. rotate: dịch vòng và chiều của khoảng dịch

Xét dãy `[A, B, C, D, E]`. Dịch vòng nghĩa là phần tử vượt qua một đầu sẽ quay lại đầu còn lại. Không có vị trí mới được thêm và không có phần tử nào bị loại bỏ.

- Khoảng dịch dương đưa các phần tử sang phải.
- Khoảng dịch âm đưa các phần tử sang trái.
- Khoảng dịch bằng 0 giữ nguyên dãy khi thao tác hoàn tất.
- Khoảng dịch có thể lớn hơn kích thước; các vòng đầy đủ không làm thay đổi kết quả cuối.

Ví dụ, dịch phải một vị trí đưa E lên đầu, tạo `[E, A, B, C, D]`. Dịch trái hai vị trí đưa A và B xuống cuối theo đúng thứ tự của chúng, tạo `[C, D, E, A, B]`.

Với List không rỗng có n phần tử, có thể dùng số học modulo để dự đoán. Chuẩn hóa khoảng dịch thành r trong đoạn từ 0 đến n - 1 bằng `Math.floorMod(distance, n)`. Sau đó, theo nghĩa toán học:

`vị trí mới của phần tử cũ ở i = (i + r) mod n`

Đây là công thức vị trí, không phải yêu cầu tự viết lại thuật toán thư viện. Nếu triển khai phép cộng trong Java với chỉ số rất lớn, phải tính đến tràn số int; không xem biểu thức toán học là luôn an toàn để chép nguyên xi vào mọi phép tính int.

Với n bằng 5 và r bằng 2:

| Phần tử ban đầu | Chỉ số cũ | Chỉ số mới |
| --- | --- | --- |
| A | 0 | 2 |
| B | 1 | 3 |
| C | 2 | 4 |
| D | 3 | 0 |
| E | 4 | 1 |

Đọc theo chỉ số mới cho kết quả `[D, E, A, B, C]`. Cũng có thể tách dãy thành hai khối: ba phần tử đầu và hai phần tử cuối; đưa khối cuối lên đầu, giữ thứ tự bên trong từng khối.

Với khoảng dịch âm, không dùng trị tuyệt đối để quyết định kết quả. `floorMod` đưa số âm về phần dư không âm phù hợp với vòng. Trường hợp List rỗng phải được xét riêng khi tự tính modulo, vì không được chia lấy dư cho 0.

## 5. Thí nghiệm rotate với số âm, số lớn và List rỗng

Lưu thành `RotateDistanceDemo.java`. Mỗi khoảng dịch được áp dụng lên một ArrayList mới từ cùng dữ liệu gốc, nên các dòng không phải các lần dịch cộng dồn.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RotateDistanceDemo {
    public static void main(String[] args) {
        List<String> base = List.of("A", "B", "C", "D", "E");
        int[] distances = {1, -2, 7, Integer.MIN_VALUE, 0};
        for (int distance : distances) {
            List<String> values = new ArrayList<>(base);
            Collections.rotate(values, distance);
            System.out.println("distance=" + distance + " -> " + values);
        }

        int normalized = Math.floorMod(Integer.MIN_VALUE, base.size());
        System.out.println("minValueNormalized=" + normalized);

        List<String> roundTrip = new ArrayList<>(base);
        Collections.rotate(roundTrip, 2);
        Collections.rotate(roundTrip, -2);
        System.out.println("roundTrip=" + roundTrip);

        List<String> empty = new ArrayList<>();
        Collections.rotate(empty, 8);
        System.out.println("empty=" + empty);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 RotateDistanceDemo.java
java RotateDistanceDemo
```

Stdout:

```text
distance=1 -> [E, A, B, C, D]
distance=-2 -> [C, D, E, A, B]
distance=7 -> [D, E, A, B, C]
distance=-2147483648 -> [D, E, A, B, C]
distance=0 -> [A, B, C, D, E]
minValueNormalized=2
roundTrip=[A, B, C, D, E]
empty=[]
```

Khoảng dịch 7 tương đương dịch phải 2 đối với năm phần tử. Khoảng dịch `Integer.MIN_VALUE` cũng chuẩn hóa thành 2 trong trường hợp này; chương trình cho cùng kết quả tương ứng. Không cần giới hạn khoảng dịch vào một đoạn nhỏ trước khi truyền cho `Collections.rotate`.

Ca `roundTrip` dịch phải hai rồi dịch trái hai trên cùng List, nên quay lại dãy ban đầu. Dịch vòng không đảo thứ tự toàn bộ như thao tác reverse; những phần tử đi qua biên tiếp tục nối vòng theo thứ tự của dãy.

Ca List rỗng được gọi trực tiếp với `Collections.rotate` và vẫn rỗng. Trong mã tự dự đoán, không gọi `Math.floorMod(distance, empty.size())`; đây là phép tính riêng không hợp lệ do mẫu số bằng 0, không phải điều mà chương trình trên đã làm.

Nếu tự tính khoảng dịch ngược bằng `-distance`, cần chú ý `Integer.MIN_VALUE`: đổi dấu giá trị ấy trong kiểu int không tạo ra số dương đối xứng biểu diễn được. Khi cần thao tác nghịch đảo cho List không rỗng, có thể dựa trên khoảng dịch đã chuẩn hóa phù hợp thay vì phủ định tùy ý một int đầu vào. Ví dụ đang kiểm chứng cặp 2 và -2, không khẳng định mọi phép đổi dấu int đều an toàn.

Xem quy tắc vị trí và miền khoảng dịch trong [Collections.rotate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#rotate(java.util.List,int)).

## 6. Collections.replaceAll: thay mọi lần xuất hiện khớp giá trị

Chữ ký của tiện ích là `Collections.replaceAll(List<T> list, T oldVal, T newVal)`. Phương thức xét các phần tử trong List và thay những phần tử khớp `oldVal` bằng `newVal`.

Điều kiện khớp được hiểu như sau:

- Nếu `oldVal` là null, phần tử null là phần tử khớp.
- Nếu `oldVal` khác null, dùng `oldVal.equals(element)`.

Do đó, hai đối tượng khác tham chiếu nhưng bằng nhau theo `equals` vẫn có thể khớp. Không dùng `==` để dự đoán việc thay giá trị thông thường. So sánh ở đây cũng không dựa trên Comparator hay thứ tự sắp xếp.

Phương thức thay **tất cả** vị trí khớp, không dừng ở vị trí đầu tiên. Thứ tự các vị trí được giữ, kích thước được giữ, nhưng số lần xuất hiện của các giá trị có thể thay đổi vì giá trị cũ được thay bằng giá trị mới.

Giá trị boolean nói rằng List có ít nhất một phần tử khớp điều kiện hay không. Nó không là số phần tử đã thay, cũng không là bằng chứng rằng nội dung quan sát theo `equals` đã khác trước. Nếu thay `"green"` bằng `"green"` trong List có green, kết quả boolean vẫn là true.

`newVal` phải phù hợp với kiểu và các ràng buộc phần tử của List. Với một List cho phép null, có thể dùng null làm giá trị cần tìm hoặc giá trị thay thế. Không suy từ khả năng đó rằng mọi List đều nhận null hoặc đều cho phép `set`.

Việc thay được thực hiện ở tham chiếu phần tử của List. Nó không gọi một setter nghiệp vụ trên đối tượng cũ và không tự tạo một bản sao mới của `newVal` cho từng vị trí. Nếu `newVal` là một đối tượng có thể sửa, những vị trí được thay có thể cùng giữ đối tượng đó; cần hiểu mức chia sẻ khi dùng kiểu phần tử như vậy.

## 7. Thí nghiệm replaceAll và phân biệt phương thức cùng tên trên List

Lưu thành `ReplaceMatchingValuesDemo.java`. Một chuỗi red được tạo bằng `new String` để nhấn mạnh rằng khớp dựa trên nội dung bằng nhau, không yêu cầu cùng một tham chiếu String. Null được chuẩn bị bằng một List cho phép null.

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ReplaceMatchingValuesDemo {
    public static void main(String[] args) {
        List<String> values = new ArrayList<>(
                Arrays.asList("red", null, new String("red"), "blue"));

        boolean found = Collections.replaceAll(values, "red", "green");
        System.out.println("foundRed=" + found);
        System.out.println("afterRed=" + values);

        boolean foundNull = Collections.replaceAll(values, null, "unknown");
        System.out.println("foundNull=" + foundNull);
        System.out.println("afterNull=" + values);

        boolean absent = Collections.replaceAll(values, "black", "white");
        System.out.println("absent=" + absent);

        boolean sameValue = Collections.replaceAll(values, "green", "green");
        System.out.println("sameValue=" + sameValue);
        System.out.println("afterSameValue=" + values);

        values.replaceAll(value -> value.toUpperCase(Locale.ROOT));
        System.out.println("afterOperator=" + values);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ReplaceMatchingValuesDemo.java
java ReplaceMatchingValuesDemo
```

Stdout:

```text
foundRed=true
afterRed=[green, null, green, blue]
foundNull=true
afterNull=[green, unknown, green, blue]
absent=false
sameValue=true
afterSameValue=[green, unknown, green, blue]
afterOperator=[GREEN, UNKNOWN, GREEN, BLUE]
```

Sau lần thay đầu tiên, cả hai vị trí red đều thành green, còn null và blue được giữ. Lần thay thứ hai chỉ nhắm các phần tử null nên tạo nhãn unknown tại đúng vị trí đó.

Không có black trong List, vì vậy biến `absent` là false. Khi thay green bằng green, biến `sameValue` là true, trong khi nội dung hiển thị vẫn như trước lần gọi. Hai dòng này giúp đọc đúng ý nghĩa của kết quả boolean.

Dòng cuối gọi `values.replaceAll(...)`, tức phương thức của chính List. Nó nhận một hàm một đối số rồi dùng kết quả hàm để thay từng phần tử. Ở thời điểm này null đã được thay thành unknown, nên phép chuyển thành chữ hoa áp dụng được cho mọi phần tử String.

| Tiêu chí | `Collections.replaceAll(list, oldVal, newVal)` | `list.replaceAll(operator)` |
| --- | --- | --- |
| Nơi khai báo | Lớp tiện ích Collections | Giao diện List |
| Đầu vào chính | Một giá trị cần tìm và một giá trị thay | Hàm biến đổi từng phần tử |
| Phần tử được xử lý | Các phần tử khớp giá trị cũ | Từng phần tử theo kết quả hàm |
| Giá trị trả về | `boolean` cho biết có khớp | `void` |
| Ví dụ nhu cầu | Đổi mọi nhãn cũ sang một nhãn mới | Chuẩn hóa hoặc tính giá trị mới theo từng phần tử |

`Locale.ROOT` trong chương trình làm phép đổi chữ hoa không phụ thuộc ngôn ngữ mặc định của máy. Phần này minh họa cách phân biệt hai API cùng tên; mục tiêu chính của bài vẫn là tiện ích Collections thay theo giá trị.

Nếu chỉ cần thay đúng một vị trí đã biết, `list.set(index, value)` diễn đạt mục tiêu khác với việc thay tất cả giá trị bằng nhau. Nếu cần một điều kiện tùy ý và mỗi phần tử tạo kết quả riêng, phương thức nhận hàm của List có thể phù hợp hơn. Chọn theo quy tắc biến đổi cần thực hiện.

Hợp đồng tương ứng: [Collections.replaceAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#replaceAll(java.util.List,T,T)) và [List.replaceAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#replaceAll(java.util.function.UnaryOperator)).

## 8. Thí nghiệm khả năng sửa: cố định kích thước và chỉ đọc

Lưu thành `ListMutationSupportDemo.java`. Chương trình thử ba tiện ích trên List từ mảng, rồi trên một lớp bọc chỉ đọc. Các ca bị chặn đều thực sự yêu cầu thay đổi dữ liệu, không dựa vào tình huống không có tác dụng.

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ListMutationSupportDemo {
    public static void main(String[] args) {
        String[] array = {"A", "B", "C", "A"};
        List<String> fixed = Arrays.asList(array);
        Collections.swap(fixed, 0, 1);
        Collections.rotate(fixed, 1);
        boolean found = Collections.replaceAll(fixed, "A", "X");
        System.out.println("fixed=" + fixed);
        System.out.println("array=" + Arrays.toString(array));
        System.out.println("found=" + found);
        try {
            fixed.add("new");
        } catch (UnsupportedOperationException e) {
            System.out.println("fixedAdd=UnsupportedOperationException");
        }

        List<String> readOnly = Collections.unmodifiableList(
                new ArrayList<>(List.of("A", "B", "C")));
        try {
            Collections.swap(readOnly, 0, 1);
        } catch (UnsupportedOperationException e) {
            System.out.println("readOnlySwap=UnsupportedOperationException");
        }
        try {
            Collections.rotate(readOnly, 1);
        } catch (UnsupportedOperationException e) {
            System.out.println("readOnlyRotate=UnsupportedOperationException");
        }
        try {
            Collections.replaceAll(readOnly, "A", "Z");
        } catch (UnsupportedOperationException e) {
            System.out.println("readOnlyReplace=UnsupportedOperationException");
        }
        System.out.println("readOnly=" + readOnly);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ListMutationSupportDemo.java
java ListMutationSupportDemo
```

Stdout:

```text
fixed=[X, B, X, C]
array=[X, B, X, C]
found=true
fixedAdd=UnsupportedOperationException
readOnlySwap=UnsupportedOperationException
readOnlyRotate=UnsupportedOperationException
readOnlyReplace=UnsupportedOperationException
readOnly=[A, B, C]
```

Theo dõi List từ mảng qua từng bước:

| Bước | Nội dung |
| --- | --- |
| Ban đầu | `[A, B, C, A]` |
| Đổi vị trí 0 và 1 | `[B, A, C, A]` |
| Dịch phải một vị trí | `[A, B, A, C]` |
| Thay mọi A bằng X | `[X, B, X, C]` |

Dòng in mảng có cùng nội dung cuối vì List cố định kích thước đang dựa trên mảng đó. Các thao tác `set` được phép, còn `add` cần tăng kích thước nên bị từ chối.

Đối với `readOnly`, cả ba yêu cầu sửa đều bị chặn. Giữ nguyên số phần tử không đủ để được phép thao tác: quyền thay tham chiếu ở một vị trí cũng là quyền sửa dữ liệu.

Bảng so sánh đúng cần tách hai khả năng:

| Loại List trong ví dụ | Thay phần tử ở vị trí có sẵn | Tăng/giảm số phần tử |
| --- | --- | --- |
| ArrayList thông thường | Có | Có |
| List từ Arrays.asList | Có | Không |
| Lớp bọc Collections.unmodifiableList | Không qua lớp bọc | Không qua lớp bọc |

Không lập trình bằng cách gọi thử một tiện ích rồi bắt ngoại lệ để đoán loại List. Nếu phương thức của mình có trách nhiệm tạo kết quả đã biến đổi, chuẩn bị List phù hợp ngay từ đầu. Nếu phương thức yêu cầu sửa chính List của bên gọi, nêu rõ điều kiện hỗ trợ thay phần tử.

Các thuật toán sửa dữ liệu của Collections có quy tắc cho phép khác biệt về việc ném ngoại lệ trong một số lời gọi không có tác dụng. Vì vậy, những ca như không tìm thấy giá trị, khoảng dịch không đổi dãy hoặc hai chỉ số trùng nhau không nên được dùng làm phép thử phổ quát về khả năng sửa.

Quan hệ giữa mảng và List cùng khả năng giữ cố định kích thước được mô tả ở [Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

## 9. Áp dụng lên subList: phạm vi nhỏ, tác động lên List cha

Nếu chỉ muốn biến đổi một đoạn, có thể lấy một khung nhìn (view) bằng `subList(fromIndex, toIndex)` rồi truyền view đó cho tiện ích. Cận đầu được tính vào, cận cuối không được tính vào. Chỉ số khi gọi `swap` trên view là chỉ số cục bộ của đoạn, không phải chỉ số trực tiếp của List cha.

Giả sử List cha là `[head, A, B, C, D, tail]`. View `subList(1, 5)` gồm bốn phần tử `[A, B, C, D]`. Vị trí 0 của view tương ứng với vị trí 1 của cha; vị trí 3 tương ứng với vị trí 4.

View không tự tạo bản sao. Khi sửa phần tử qua view, List cha nhận thay đổi ở đoạn tương ứng. Những phần tử ngoài đoạn không là mục tiêu của tiện ích.

Lưu thành `SubListUtilitiesDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SubListUtilitiesDemo {
    public static void main(String[] args) {
        List<String> parent = new ArrayList<>(
                List.of("head", "A", "B", "C", "D", "tail"));
        List<String> window = parent.subList(1, 5);

        Collections.swap(window, 0, 3);
        System.out.println("parentAfterSwap=" + parent);

        Collections.rotate(window, -1);
        System.out.println("parentAfterRotate=" + parent);

        boolean found = Collections.replaceAll(window, "A", "X");
        System.out.println("found=" + found);
        System.out.println("window=" + window);
        System.out.println("parentAfterReplace=" + parent);

        List<String> move = new ArrayList<>(List.of("P", "Q", "R", "S", "T"));
        Collections.rotate(move.subList(1, 4), -1);
        System.out.println("moveOne=" + move);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SubListUtilitiesDemo.java
java SubListUtilitiesDemo
```

Stdout:

```text
parentAfterSwap=[head, D, B, C, A, tail]
parentAfterRotate=[head, B, C, A, D, tail]
found=true
window=[B, C, X, D]
parentAfterReplace=[head, B, C, X, D, tail]
moveOne=[P, R, S, Q, T]
```

Hai phần tử head và tail không đổi qua cả ba thao tác trên window. Các dòng in List cha cho thấy đoạn giữa đã đổi chỗ, dịch vòng rồi thay giá trị. Dòng `window` cho thấy kết quả cục bộ của chính đoạn đó.

Ca `moveOne` dùng dịch vòng để di chuyển một phần tử mà giữ thứ tự các phần tử còn lại trong đoạn. Đoạn từ chỉ số 1 đến trước 4 là `[Q, R, S]`. Dịch trái một vị trí tạo `[R, S, Q]`; khi đặt trong List cha, Q được chuyển từ chỉ số 1 đến chỉ số 3.

Tổng quát, khi cần chuyển phần tử ở j sang k với j nhỏ hơn hoặc bằng k, có thể dịch trái một vị trí trên đoạn từ j đến trước k + 1. Đó là cách xử lý vị trí trong một đoạn, không yêu cầu xóa phần tử rồi thêm một vị trí mới.

Điều này đặc biệt hữu ích khi List hỗ trợ thay phần tử nhưng không hỗ trợ đổi kích thước. Tuy nhiên, phải tính đúng cận cuối: dùng k thay cho k + 1 sẽ bỏ chính vị trí đích ra khỏi đoạn.

Trong khi đang sử dụng view, không tùy ý thêm/xóa cấu trúc ở List cha qua một đường khác rồi tiếp tục coi view là hợp lệ. Hợp đồng của `subList` có điều kiện về thay đổi cấu trúc bên ngoài view. Ví dụ chỉ thay phần tử, không xen thêm các thao tác làm thay kích thước nguồn.

Nếu muốn biến đổi một đoạn độc lập mà không sửa cha, tạo một List riêng từ đoạn trước khi gọi tiện ích. Quyền sửa, phạm vi và quan hệ với nguồn là ba quyết định cần xem cùng nhau.

## 10. Cơ chế và chi phí phụ thuộc kiểu List

Hiểu cách List truy cập phần tử giúp chọn thao tác đúng. Một phương thức nhận giao diện List không có nghĩa mọi List có cùng chi phí cho `get(index)` và `set(index, value)`.

Với [ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html), các thao tác get/set có thời gian hằng số. Đổi hai vị trí chỉ cần một số ít thao tác truy cập và thay phần tử, nên swap có chi phí hằng số theo kích thước trong trường hợp thông thường này.

Với [LinkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html), truy cập bằng chỉ số phải đi từ đầu hoặc cuối đến vị trí thích hợp. Đổi hai vị trí ở giữa một List lớn có thể tốn thời gian theo chiều dài đường duyệt. Không áp dụng kết luận về ArrayList cho mọi List chỉ vì chữ ký tiện ích giống nhau.

Đối với rotate, cần phân biệt kết quả và một cách triển khai kết quả:

- Kết quả được xác định bằng khoảng dịch vòng và vị trí phần tử.
- Bản triển khai JDK có thể chuyển phần tử theo các chu trình vị trí, hoặc xử lý các đoạn bằng những lần đảo thứ tự, tùy kích thước và khả năng truy cập của List.

Mô tả thuật toán trong Javadoc của Collections là thông tin về bản triển khai, không buộc mọi phiên bản phải giữ cùng một chuỗi lệnh nội bộ. Điều quan trọng khi sử dụng là kết quả đúng và List có quyền thay phần tử. Không tự dịch từng bước một lặp lại hàng triệu lần chỉ vì distance rất lớn; số vòng đầy đủ đã không làm thay đổi kết quả mong muốn.

ReplaceAll theo giá trị cần xét các phần tử để tìm mọi lần khớp. Với các List thông dụng và phép equals có chi phí hằng số, chi phí duyệt thường tuyến tính theo số phần tử. Nếu equals là phép so sánh nội dung lớn, phải tính thêm chi phí so sánh ấy.

Một view subList có thể giảm phạm vi cần xét xuống đoạn liên quan, nhưng chi phí vẫn phụ thuộc cách List nền thực hiện truy cập. Không cho rằng tạo view đã sao chép dữ liệu sang một cấu trúc có chi phí khác.

Các thao tác trong bài cũng không công bố một giao dịch cập nhật nguyên tử với các luồng khác. Khi List dùng chung và có truy cập đồng thời, cần cơ chế phối hợp phù hợp cho toàn bộ thao tác nghiệp vụ. Giữ nguyên size không đồng nghĩa với việc các thay đổi phần tử có thể được thực hiện tùy ý đồng thời.

## 11. Đọc ngoại lệ và giữ đúng phạm vi tác động

| Tình huống | Cách đọc và xử lý |
| --- | --- |
| swap nhận chỉ số âm hoặc bằng size | Chỉ số không nằm trong phạm vi phần tử hiện có; kiểm tra cả hai cận |
| rotate nhận số âm hoặc lớn hơn size | Khoảng dịch đó hợp lệ; kiểm tra lại chiều và phép chuẩn hóa nếu dự đoán sai |
| Tự tính modulo với size bằng 0 | Tách trường hợp rỗng trước phép tính; không gán lỗi số học ấy cho rotate |
| Tiện ích sửa một List chỉ đọc | Cần List hỗ trợ thay phần tử hoặc tạo bản riêng có thể sửa |
| List cố định kích thước vẫn cho swap/rotate | Điều này phù hợp khi List hỗ trợ set; không đồng nhất với quyền add/remove |
| replaceAll trả true nhưng nội dung in không đổi | Có phần tử khớp; boolean không là phép đo mức thay đổi nội dung |
| replaceAll không khớp đối tượng theo dự đoán | Xem lại equals của oldVal và kiểu phần tử; không dùng định danh hay Comparator để thay quy tắc khớp |
| Dùng chỉ số cha khi swap trên subList | Chuyển sang chỉ số cục bộ của view |
| Biến đổi một đoạn rồi cha cũng đổi | Đoạn là view; tạo List riêng nếu cần kết quả độc lập |

Nếu chính đối tượng List truyền vào là null, các tiện ích ném `NullPointerException` theo hợp đồng Collections. Phân biệt trường hợp này với việc một List hợp lệ có chứa phần tử null: ví dụ replaceAll đã xử lý được phần tử null vì List ấy cho phép.

Các phép sửa thư viện không tự kiểm tra mọi quy tắc nghiệp vụ của ứng dụng. Một List có thể cho thay phần tử nhưng kiểu kiểm tra dữ liệu hoặc mã equals của phần tử có thể phát sinh lỗi trong quá trình chạy. Không suy rằng luôn có cơ chế hoàn tác toàn bộ những gì đã thực hiện trước lỗi.

Nếu cần bảo toàn dữ liệu nguồn khi việc biến đổi thất bại, lựa chọn thực hiện trên dữ liệu riêng rồi kiểm tra/công bố kết quả theo quy trình phù hợp. Chỉ thêm try/catch quanh một thao tác tại chỗ không tự làm nó thành cập nhật “tất cả hoặc không có gì”.

Khi kiểm chứng, in hoặc so sánh cả nội dung và kích thước trước–sau. Với swap và rotate, kiểm tra thêm rằng các lần xuất hiện của phần tử vẫn được bảo toàn. Với replaceAll, xác định rõ các vị trí khớp; số lần xuất hiện của giá trị mới có thể tăng dù tổng số vị trí không đổi.

## 12. Thực hành từ dự đoán đến kiểm chứng

### Bài 1: Đổi chỗ hai mục được chọn

Nguồn là `[draft, review, publish, archive]`. Hãy đổi vị trí 1 và 3, dự đoán kết quả trước khi chạy, rồi kiểm tra kích thước.

Kết quả mong đợi là `[draft, archive, publish, review]`, kích thước vẫn bằng bốn. Thử thêm ca chỉ số bằng size để xác định khác biệt giữa “vị trí có thể chèn trong một số API” và “vị trí phần tử hiện có để swap”.

### Bài 2: Chọn phần tử bắt đầu của một vòng

Nguồn là `[Mon, Tue, Wed, Thu, Fri]`. Cần Wed đứng đầu và thứ tự vòng tiếp tục như ban đầu.

Hãy chọn khoảng dịch trước khi gọi rotate. Dịch trái hai vị trí, tức distance bằng -2, tạo `[Wed, Thu, Fri, Mon, Tue]`. Sau đó chọn một khoảng dịch dương tương đương và kiểm chứng hai kết quả bằng nhau. Với năm phần tử, distance bằng 3 là một lựa chọn như vậy.

### Bài 3: Thay trạng thái nhưng đọc đúng boolean

Nguồn là `[open, closed, open]`. Thay mọi open bằng pending, rồi thay pending bằng pending.

Lần đầu cần tạo `[pending, closed, pending]` và trả true. Lần sau vẫn trả true vì có phần tử khớp, dù chuỗi hiển thị không khác. Thêm ca tìm một trạng thái không có để kiểm tra nhánh false.

### Bài 4: Chỉ xoay đoạn giữa

Nguồn là `[L, A, B, C, R]`. Cần dịch phải một vị trí trong đoạn A, B, C, giữ L và R ở nguyên vị trí.

Hãy xác định view với cận đầu 1 và cận cuối 4. Kết quả cần là `[L, C, A, B, R]`. Kiểm tra rằng chỉ số cục bộ và cận cuối không tính vào được dùng đúng.

### Bài 5: Biến đổi List từ mảng

Tạo một mảng String rồi lấy List qua Arrays.asList. Áp dụng từng tiện ích, in cả List và mảng sau mỗi bước. Cuối cùng thử thêm một phần tử mới.

Tiêu chí đạt: các phép thay phần tử xuất hiện ở cả hai cách nhìn dữ liệu; thao tác tăng kích thước bị từ chối. Giải thích bằng hai quyền khác nhau: quyền set và quyền add/remove.

### Bài 6: Giữ nguồn chỉ đọc và tạo kết quả riêng

Đầu vào là List được tạo bằng List.of. Cần trả một danh sách đã rotate mà đầu vào vẫn như cũ.

Hãy tạo ArrayList riêng trước khi xoay. Tiêu chí đạt: nguồn giữ nội dung ban đầu, kết quả riêng đúng chiều xoay, và không phụ thuộc vào việc bắt ngoại lệ rồi mới sửa thiết kế.

## 13. Các câu hỏi kiểm tra mức hiểu

1. **Swap và rotate có trả một List mới không?** Không. Chúng trả void và tác động lên List được truyền vào.
2. **Vì sao List cố định kích thước vẫn có thể dùng được?** Vì các tiện ích không cần thêm/xóa vị trí, nhưng cần khả năng thay phần tử ở vị trí đã có.
3. **Distance dương trong rotate đi theo hướng nào?** Sang phải, với phần tử vượt cuối quay lên đầu.
4. **Khoảng dịch lớn hơn size có là lỗi không?** Không. Có thể dự đoán bằng khoảng dịch tương đương theo modulo khi List không rỗng.
5. **Collections.replaceAll dùng gì để khớp?** Dùng null khớp null hoặc oldVal.equals(element), không phải so sánh tham chiếu thông thường.
6. **Boolean true có chứng minh dữ liệu khác trước không?** Không. Nó cho biết đã có phần tử khớp điều kiện.
7. **List.replaceAll nhận gì khác?** Nó nhận một hàm biến đổi từng phần tử và trả void.
8. **Sửa subList có sửa List cha không?** Có thể tác động lên đoạn tương ứng vì subList là view, không phải bản sao.
9. **Giữ nguyên size có đồng nghĩa thao tác chỉ đọc không?** Không. Đổi hoặc thay tham chiếu phần tử vẫn là sửa List.
10. **Một dự đoán tốt cần xét những gì?** Phạm vi vị trí, chiều hoặc điều kiện khớp, quyền set của List và việc dữ liệu đang là nguồn thật, view hay bản riêng.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collections-utilities-005">fill và nCopies: thay phần tử so với tạo danh sách</a>

<details>
<summary>Xem chi tiết</summary>

# fill và nCopies: thay phần tử so với tạo danh sách

## 1. Hai nhu cầu giống hình thức nhưng khác thao tác

Một danh sách đã có năm trạng thái và cần đặt tất cả về “chờ xử lý”. Một danh sách khác chưa tồn tại và cần có ngay năm vị trí ban đầu mang cùng giá trị. Kết quả nhìn bên ngoài đều có thể là năm chữ giống nhau, nhưng cách làm khác nhau:

- Collections.fill(list,value) **thay các phần tử ở những vị trí đã tồn tại trong list**.
- Collections.nCopies(n,value) **trả về một List có kích thước n, biểu diễn n lần lặp của cùng value**.

fill là thao tác cập nhật list được truyền vào; nCopies là phương thức tạo kết quả để nơi gọi sử dụng. Không dùng hai tên như những cách viết thay thế tùy ý.

Một khác biệt nữa cần biết ngay: nCopies không tạo n đối tượng phần tử mới. Nếu value là tham chiếu tới đối tượng có thể thay đổi, mọi vị trí lặp cùng tham chiếu đó. fill cũng đặt cùng một tham chiếu vào các vị trí được thay.

| Câu hỏi | fill(list,value) | nCopies(n,value) |
|---|---|---|
| Đầu vào chính | Một list đã có | Số vị trí cần có và phần tử lặp |
| Tác động | Thay phần tử của list đầu vào | Trả một list kết quả |
| Giá trị trả về | void | List<T> |
| Kích thước sau thao tác | Giữ nguyên size của list | Bằng n nếu n hợp lệ |
| Cần list đầu vào cho phép thay phần tử? | Có, khi thực sự phải thay | Không nhận list đầu vào |
| Kết quả có sửa cấu trúc/vị trí được không? | Phụ thuộc list được truyền vào | List kết quả không cho sửa |
| Có tạo một đối tượng value mới cho từng vị trí? | Không | Không |

Bài học dùng Java 17 trở lên, chỉ dùng thư viện chuẩn. Ba chương trình đầy đủ kiểm tra thay phần tử, tạo list lặp và vấn đề tham chiếu dùng chung. Những đoạn giải thích về null, list rỗng và lớp bọc đều được xét theo hợp đồng của list thực tế.

## 2. fill: thay toàn bộ phần tử hiện có

### 2.1. Thao tác theo size, không theo capacity

Collections.fill có thể được hiểu về tác dụng như: với mỗi chỉ số từ 0 đến size - 1, đặt phần tử tại chỉ số đó thành value. Đây là mô tả ý nghĩa, không bắt buộc thư viện dùng đúng một vòng lặp set(index,...) trên mọi loại List.

Sau khi hoàn tất trên một list thông thường hợp lệ:

- size không đổi.
- Thứ tự các vị trí vẫn là các vị trí cũ.
- Phần tử tại mỗi vị trí được thay bằng value.
- Không có phần tử mới được thêm để tăng kích thước.

new ArrayList<>(5) tạo ArrayList rỗng với dung lượng ban đầu phù hợp để chứa thêm dữ liệu về sau. Nó không tạo sẵn năm phần tử null.

Vì size vẫn bằng 0, fill lên list này không tạo ra năm vị trí. Dung lượng (capacity) là khả năng lưu trữ nội bộ; kích thước (size) là số phần tử hiện có theo API.

Tài liệu [ArrayList(int)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html#%3Cinit%3E(int)) xác định constructor này tạo list rỗng. [Collections.fill](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#fill(java.util.List,T)) quy định thao tác thay phần tử của list đã có.

### 2.2. Cần hỗ trợ set, không nhất thiết hỗ trợ add/remove

Một list có kích thước cố định vẫn có thể cho phép thay phần tử. Arrays.asList(array) là ví dụ: không thêm/xóa để đổi kích thước được, nhưng có thể set phần tử và cập nhật mảng được bọc.

Bởi vậy fill có thể thành công trên Arrays.asList. Ngược lại, list không cho sửa như List.of hoặc lớp bọc Collections.unmodifiableList chặn việc thay phần tử qua giao diện list, nên fill khi phải thay phần tử sẽ bị từ chối.

Phân biệt hai thuộc tính:

- **Không đổi kích thước được:** chưa đủ kết luận set không được.
- **Không thay phần tử qua list được:** không đáp ứng yêu cầu cập nhật của fill.

fill trả void. Không gán kết quả của lời gọi vào một biến List với ý định nhận “danh sách sau khi điền”; chính list đầu vào là nơi đã thay đổi.

### 2.3. Giá trị null phụ thuộc chính sách của list đích

fill không có quy tắc cấm null chung giống List.of. Nếu list đích cho phép null và hỗ trợ thay phần tử, có thể dùng fill(list,null).

Nếu list đích hoặc lớp bọc có ràng buộc khác, thao tác vẫn phải tuân theo ràng buộc đó. Một kiểu List không bảo đảm mọi triển khai đều nhận mọi giá trị.

Truyền list bằng null là trường hợp khác: không có list để cập nhật và không hợp lệ. Không nhầm “phần tử thay thế có thể là null” với “list đích có thể là null”.

## 3. Chương trình fill: kích thước, mảng và một đoạn list

Tệp **FillExistingListDemo.java**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FillExistingListDemo {
    private static void expectUOE(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + ": no exception");
        } catch (UnsupportedOperationException expected) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        List<String> values = new ArrayList<>(List.of("A", "B", "C"));
        Collections.fill(values, "x");
        System.out.println("filled=" + values);
        System.out.println("size=" + values.size());
        Collections.fill(values, null);
        System.out.println("filledNull=" + values);

        List<String> capacityOnly = new ArrayList<>(5);
        Collections.fill(capacityOnly, "ready");
        System.out.println("capacityOnlySize=" + capacityOnly.size());
        System.out.println("capacityOnlyValues=" + capacityOnly);

        String[] array = {"red", "green", "blue"};
        List<String> fixedSize = Arrays.asList(array);
        Collections.fill(fixedSize, "gray");
        System.out.println("fixedSize=" + fixedSize);
        System.out.println("backingArray=" + Arrays.toString(array));

        List<String> parent =
                new ArrayList<>(List.of("a", "b", "c", "d"));
        Collections.fill(parent.subList(1, 3), "m");
        System.out.println("parentAfterRangeFill=" + parent);

        expectUOE("fillReadOnly",
                () -> Collections.fill(List.of("a", "b"), "x"));
        List<String> wrapped = Collections.unmodifiableList(
                new ArrayList<>(List.of("a", "b")));
        expectUOE("fillWrapped",
                () -> Collections.fill(wrapped, "x"));

        int[] numbers = {1, 2, 3};
        Arrays.fill(numbers, 7);
        System.out.println("primitiveArray=" + Arrays.toString(numbers));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 FillExistingListDemo.java
java FillExistingListDemo
```

Stdout chính xác:

```text
filled=[x, x, x]
size=3
filledNull=[null, null, null]
capacityOnlySize=0
capacityOnlyValues=[]
fixedSize=[gray, gray, gray]
backingArray=[gray, gray, gray]
parentAfterRangeFill=[a, m, m, d]
fillReadOnly=UnsupportedOperationException
fillWrapped=UnsupportedOperationException
primitiveArray=[7, 7, 7]
```

### 3.1. Điền giá trị không tăng kích thước

values ban đầu có ba phần tử. Sau fill bằng x, nó vẫn có ba phần tử. Sau fill bằng null, nó vẫn có ba vị trí nhưng mỗi vị trí giữ null.

capacityOnly được khởi tạo bằng new ArrayList<>(5), nên vẫn rỗng trước và sau fill. Không có lỗi ở ví dụ list rỗng cho phép sửa này, nhưng cũng không có “năm giá trị ready” được tạo ra.

Nếu muốn tạo sẵn năm vị trí, phải thêm năm phần tử bằng cách thích hợp hoặc tạo dữ liệu khởi tạo có kích thước năm. nCopies là một lựa chọn khi các vị trí ban đầu dùng cùng giá trị.

### 3.2. Fill trên list dựa vào mảng cập nhật mảng nguồn

fixedSize là view list của array. fill thay ba phần tử của fixedSize thành gray, nên array cũng hiện gray ở cả ba ô.

Nó không tạo ra một mảng mới và không làm list trở thành cấu trúc độc lập. Nếu cần một list riêng, phải sao chép sang cấu trúc riêng trước.

[Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)) mô tả quan hệ dựa trên mảng này. Tính kích thước cố định không ngăn phép thay giá trị trong các vị trí đã có.

### 3.3. Fill một phạm vi bằng subList

parent.subList(1,3) là view của các vị trí chỉ số 1 và 2, không gồm chỉ số 3. fill trên view đó làm parent từ [a,b,c,d] thành [a,m,m,d].

Đây là dùng một view để giới hạn những vị trí được thay, không phải fill tự có tham số from/to. Các điều kiện về tính hợp lệ của subList vẫn phải được giữ: không sửa cấu trúc list cha ngoài quy tắc của view trong lúc sử dụng nó.

### 3.4. Với mảng nguyên thủy, dùng API của mảng

Chương trình dùng Arrays.fill(numbers,7) cho int[]. Collections.fill nhận List, không nhận trực tiếp int[].

Cũng không coi Arrays.asList(intArray) là một List<Integer> chứa từng số. Mảng nguyên thủy có thể trở thành một phần tử kiểu int[] trong list do cách truyền kiểu; nó không tự được chuyển thành các Integer riêng để fill như danh sách số.

Arrays.fill là công cụ phù hợp để gán giá trị vào các ô của mảng nguyên thủy. Thao tác ấy vẫn không tăng độ dài mảng.

## 4. nCopies: tạo một list lặp, không sửa list có sẵn

### 4.1. Kích thước là n, giá trị tại mọi vị trí là phần tử đã truyền

Collections.nCopies(n,value) trả List<T> biểu diễn n lần xuất hiện của value.

- n > 0: list có n vị trí.
- n == 0: list rỗng.
- n < 0: IllegalArgumentException.
- value có thể là null.
- Kết quả không cho thay phần tử hoặc sửa cấu trúc theo giao diện list.

Đây là list có phần tử lặp hợp lệ, không phải Set và không loại trùng. Từ copies trong tên không phải lời hứa gọi clone hoặc constructor của value nhiều lần.

Nếu cần thay các vị trí về sau, hãy tạo một list cho phép sửa từ dữ liệu lặp đó, chẳng hạn new ArrayList<>(Collections.nCopies(n,value)).

### 4.2. Biểu diễn lặp gọn hơn việc dựng n phần tử riêng

Tài liệu [Collections.nCopies](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#nCopies(int,T)) mô tả một biểu diễn nhỏ giữ một tham chiếu tới đối tượng được lặp.

Điều này cho phép biểu diễn nhiều vị trí có cùng phần tử mà không phải tạo n đối tượng phần tử. Cần phân biệt kích thước logic của List với số đối tượng dữ liệu thật sự được tạo cho các phần tử.

Đọc get(i) ở các chỉ số hợp lệ đều nhận cùng giá trị/tham chiếu value. Duyệt hết list hoặc sao chép nó sang một ArrayList vẫn phải xử lý n vị trí logic.

### 4.3. Không cho sửa list không đồng nghĩa phần tử bất biến

Nếu value là String, việc dùng chung tham chiếu thường không gây lo ngại về sửa nội dung String vì String bất biến.

Nếu value là Box có thể thay đổi, nơi gọi vẫn có thể lấy Box qua get rồi sửa thuộc tính của Box. Điều đó không phải gọi set trên List và không tự bị List chặn.

Phần này sẽ được kiểm chứng bằng so sánh tham chiếu và số lần gọi constructor trong chương trình thứ ba.

## 5. Chương trình nCopies: list chỉ đọc và bản cho phép sửa có sẵn vị trí

Tệp **NCopiesListDemo.java**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NCopiesListDemo {
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
        List<String> repeated = Collections.nCopies(4, "x");
        System.out.println("repeated=" + repeated);
        System.out.println("size=" + repeated.size());
        System.out.println("zero=" + Collections.nCopies(0, "x"));
        System.out.println("nullable=" + Collections.nCopies(2, null));
        expect("negativeCount", IllegalArgumentException.class,
                () -> Collections.nCopies(-1, "x"));
        expect("setRepeated", UnsupportedOperationException.class,
                () -> repeated.set(0, "y"));
        expect("addRepeated", UnsupportedOperationException.class,
                () -> repeated.add("y"));
        expect("fillRepeated", UnsupportedOperationException.class,
                () -> Collections.fill(repeated, "y"));

        List<String> editable = new ArrayList<>(repeated);
        editable.set(0, "first");
        editable.add("tail");
        System.out.println("originalRepeated=" + repeated);
        System.out.println("editable=" + editable);

        List<String> slots =
                new ArrayList<>(Collections.nCopies(3, null));
        slots.set(1, "ready");
        System.out.println("initializedSlots=" + slots);

        List<String> appended = new ArrayList<>(List.of("prefix"));
        appended.addAll(Collections.nCopies(2, "v"));
        System.out.println("appended=" + appended);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 NCopiesListDemo.java
java NCopiesListDemo
```

Stdout chính xác:

```text
repeated=[x, x, x, x]
size=4
zero=[]
nullable=[null, null]
negativeCount=IllegalArgumentException
setRepeated=UnsupportedOperationException
addRepeated=UnsupportedOperationException
fillRepeated=UnsupportedOperationException
originalRepeated=[x, x, x, x]
editable=[first, x, x, x, tail]
initializedSlots=[null, ready, null]
appended=[prefix, v, v]
```

### 5.1. nCopies có kích thước thật theo API List

repeated có size bằng 4 và đọc ra bốn lần x. Các phép set, add hoặc fill nhằm thay phần tử trong chính list này bị từ chối.

Không dùng nCopies làm “ArrayList đã chuẩn bị” rồi gọi set trực tiếp. Nó đã có vị trí logic nhưng kết quả không cung cấp quyền cập nhật ấy.

Với n == 0, không có phần tử. Không dùng hành vi của các thao tác không có hiệu lực trên một list rỗng để kết luận mọi list cho sửa: ngoại lệ của một số thao tác không có tác dụng còn phụ thuộc hợp đồng cụ thể.

### 5.2. new ArrayList<>(nCopies(...)) tách cấu trúc, không tự clone phần tử

editable là ArrayList mới có bốn phần tử x lấy từ repeated. Nó hỗ trợ set và add, nên trở thành [first,x,x,x,tail]. repeated vẫn giữ bốn vị trí x như trước.

Bản cho phép sửa có cấu trúc riêng, nhưng các đối tượng phần tử ban đầu vẫn là những tham chiếu được sao chép. Với String bất biến ở ví dụ này, sự dùng chung không làm nội dung x đổi. Với đối tượng có thể thay đổi, phải xét tiếp ở chương trình sau.

### 5.3. Chuẩn bị các ô trống có thể gán về sau

slots được tạo bằng new ArrayList<>(Collections.nCopies(3,null)), nên ngay từ đầu đã có size bằng 3. Vì vậy slots.set(1,"ready") hợp lệ và chỉ thay vị trí giữa.

Cách này khác new ArrayList<>(3), vốn chỉ chuẩn bị dung lượng và chưa có vị trí 1 để set.

null ở đây là giá trị được chọn để biểu diễn ô chưa có dữ liệu. Nếu ứng dụng không dùng null cho trạng thái đó, có thể chọn một giá trị mặc định khác hoặc một mô hình trạng thái rõ ràng hơn.

### 5.4. Dùng nCopies làm nguồn cho addAll

appended ban đầu có prefix. addAll(nCopies(2,"v")) thêm hai phần tử v, làm size tăng từ 1 lên 3.

Chính addAll tăng kích thước list đích; nCopies chỉ cung cấp collection nguồn có hai lần lặp. Không nhầm trường hợp này với fill, vốn thay các phần tử hiện có và không thêm vị trí.

## 6. Tham chiếu dùng chung: cả fill và nCopies đều không clone phần tử

### 6.1. Một biểu thức new không tự được đánh giá lại cho từng vị trí

Trong Collections.nCopies(3,new Box(1)), đối số new Box(1) được đánh giá để tạo **một** Box trước khi gọi nCopies. nCopies nhận tham chiếu tới Box ấy rồi biểu diễn nó ở ba vị trí.

Tương tự, Collections.fill(list,new Box(5)) nhận một Box mới được tạo trước lời gọi và đặt cùng tham chiếu đó vào mọi vị trí được thay. Nó không gọi constructor Box riêng cho mỗi phần tử.

Để quan sát, chương trình sau có bộ đếm số lần gọi constructor. Nó còn phân biệt sửa trạng thái của Box, thay tham chiếu trong list và tạo các Box độc lập.

Tệp **SharedRepeatedElementDemo.java**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SharedRepeatedElementDemo {
    static final class Box {
        static int constructions;
        int value;

        Box(int value) {
            constructions++;
            this.value = value;
        }
    }

    private static List<Integer> values(List<Box> boxes) {
        return boxes.stream().map(box -> box.value).toList();
    }

    public static void main(String[] args) {
        Box.constructions = 0;
        List<Box> repeated = Collections.nCopies(3, new Box(1));
        System.out.println("nCopiesConstructions=" + Box.constructions);
        System.out.println("sameElement=" + (repeated.get(0) == repeated.get(1)));
        repeated.get(0).value = 9;
        System.out.println("repeatedAfterMutation=" + values(repeated));

        List<Box> editableCopy = new ArrayList<>(repeated);
        editableCopy.get(1).value = 10;
        System.out.println("originalAfterCopyMutation=" + values(repeated));
        editableCopy.set(0, new Box(7));
        System.out.println("copyAfterReplacement=" + values(editableCopy));
        System.out.println("originalAfterReplacement=" + values(repeated));

        List<Box> filled = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            filled.add(new Box(-1));
        }
        Box oldFirst = filled.get(0);
        Box.constructions = 0;
        Collections.fill(filled, new Box(5));
        System.out.println("fillConstructions=" + Box.constructions);
        System.out.println("fillSharesElement=" + (filled.get(0) == filled.get(1)));
        filled.get(2).value = 8;
        System.out.println("filledAfterMutation=" + values(filled));
        System.out.println("oldObjectStill=" + oldFirst.value);

        List<Box> independent = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            independent.add(new Box(0));
        }
        independent.get(0).value = 7;
        System.out.println("independent=" + values(independent));
        System.out.println("differentElements=" +
                (independent.get(0) != independent.get(1)));

        List<List<Integer>> sharedRows = new ArrayList<>(
                Collections.nCopies(2,
                        new ArrayList<>(Collections.nCopies(3, 0))));
        sharedRows.get(0).set(1, 9);
        System.out.println("sharedRows=" + sharedRows);
        System.out.println("sameRow=" + (sharedRows.get(0) == sharedRows.get(1)));

        List<List<Integer>> separateRows = new ArrayList<>();
        for (int row = 0; row < 2; row++) {
            separateRows.add(new ArrayList<>(Collections.nCopies(3, 0)));
        }
        separateRows.get(0).set(1, 9);
        System.out.println("separateRows=" + separateRows);
        System.out.println("differentRows=" +
                (separateRows.get(0) != separateRows.get(1)));

        Box.constructions = 0;
        List<Box> empty = Collections.nCopies(0, new Box(99));
        System.out.println("zeroCountSize=" + empty.size());
        System.out.println("zeroCountArgumentConstructions=" + Box.constructions);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SharedRepeatedElementDemo.java
java SharedRepeatedElementDemo
```

Stdout chính xác:

```text
nCopiesConstructions=1
sameElement=true
repeatedAfterMutation=[9, 9, 9]
originalAfterCopyMutation=[10, 10, 10]
copyAfterReplacement=[7, 10, 10]
originalAfterReplacement=[10, 10, 10]
fillConstructions=1
fillSharesElement=true
filledAfterMutation=[8, 8, 8]
oldObjectStill=-1
independent=[7, 0, 0]
differentElements=true
sharedRows=[[0, 9, 0], [0, 9, 0]]
sameRow=true
separateRows=[[0, 9, 0], [0, 0, 0]]
differentRows=true
zeroCountSize=0
zeroCountArgumentConstructions=1
```

### 6.2. Ba vị trí, một Box

nCopiesConstructions bằng 1 và sameElement bằng true. Sửa repeated.get(0).value thành 9 khiến cả ba vị trí đọc ra 9 vì cả ba cùng trả chính Box đó.

Không có ba đối tượng tự đồng bộ giá trị với nhau. Chỉ có một đối tượng được ba vị trí logic tham chiếu tới.

List repeated vẫn không cho set/add theo giao diện của nó. Việc giá trị in ra đổi không chứng minh danh sách đã cho sửa cấu trúc; thao tác đã sửa đối tượng Box được chứa bên trong.

### 6.3. Sao chép sang ArrayList không tự tách Box

editableCopy có cấu trúc ArrayList riêng nhưng lúc tạo vẫn chứa ba tham chiếu tới Box của repeated.

editableCopy.get(1).value = 10 sửa Box dùng chung, làm repeated cũng đọc ra [10,10,10]. Sau đó editableCopy.set(0,new Box(7)) chỉ thay tham chiếu ở vị trí 0 của bản ArrayList.

Bản sao lúc này hiện [7,10,10], còn repeated vẫn hiện [10,10,10]. Đây là khác biệt giữa:

- Sửa đối tượng mà một tham chiếu đang trỏ tới.
- Thay tham chiếu được lưu ở một vị trí của list.

Cấu trúc riêng không đồng nghĩa mọi phần tử cũng riêng.

### 6.4. Fill thay tham chiếu, không sửa đối tượng cũ

filled ban đầu chứa ba Box riêng có value bằng -1. oldFirst vẫn giữ Box đầu tiên ấy.

Sau fill(filled,new Box(5)), list giữ cùng một Box mới ở cả ba vị trí. Sửa value qua vị trí thứ ba thành 8 làm cả list đọc ra [8,8,8]. Nhưng oldFirst.value vẫn là -1.

Vậy fill không gọi một phép “reset” lên từng Box cũ. Nó thay phần tử của list bằng tham chiếu mới, còn đối tượng cũ vẫn giữ trạng thái nếu có nơi khác tiếp tục tham chiếu đến.

Nếu nhiệm vụ thật sự là cập nhật trạng thái của từng đối tượng đang có, cần dùng phương thức cập nhật của các đối tượng đó theo chính sách riêng. fill không thay thế ý nghĩa ấy.

### 6.5. n bằng 0 vẫn đánh giá biểu thức đối số

Collections.nCopies(0,new Box(99)) trả list rỗng, nhưng new Box(99) vẫn được đánh giá một lần trước lời gọi. Bộ đếm vì vậy là 1 dù zeroCountSize bằng 0.

Đây là quy tắc đánh giá đối số của lời gọi Java, không phải nCopies chủ động tạo một phần tử rồi đặt nó vào list rỗng. Không dùng cách truyền một đối tượng có chi phí hoặc tác dụng phụ để suy rằng “n bằng 0 nên biểu thức tạo đối tượng sẽ không chạy”.

Với fill trên list rỗng cũng vậy: một biểu thức đối số như new Box(...) được đánh giá trước khi phương thức thực hiện thao tác, dù không có vị trí để thay.

## 7. Khi cần phần tử hoặc hàng độc lập, tạo ở đúng cấp

### 7.1. Đối tượng mới phải được tạo trong mỗi lần lặp cần tách

Ở phần independent, new Box(0) nằm bên trong vòng lặp, nên có một Box riêng cho mỗi vị trí. Sửa Box đầu thành 7 cho kết quả [7,0,0].

Đây là khác biệt cốt lõi so với nCopies(3,new Box(0)): một bên gọi constructor ở mỗi lần lặp, một bên đánh giá biểu thức đối số một lần.

Nếu việc tạo phần tử được đóng gói trong một hàm hoặc Supplier, nó vẫn phải thực sự trả đối tượng riêng theo hợp đồng của ứng dụng. Chỉ gọi một hàm nhiều lần không bảo đảm độc lập nếu hàm ấy luôn trả một đối tượng dùng chung.

### 7.2. Bẫy ma trận có nhiều hàng cùng tham chiếu

sharedRows được tạo từ nCopies(2,một ArrayList hàng). Bọc kết quả bằng new ArrayList chỉ tạo cấu trúc ngoài riêng; hai vị trí hàng vẫn trỏ tới một ArrayList trong.

Vì vậy sharedRows.get(0).set(1,9) làm cả hai hàng nhìn thấy 9 ở cột 1. sameRow bằng true là bằng chứng trực tiếp về cùng tham chiếu hàng.

separateRows đặt new ArrayList<>(nCopies(3,0)) bên trong vòng lặp từng hàng, nên mỗi hàng có cấu trúc riêng. Cập nhật hàng đầu không đổi hàng thứ hai.

Ở ví dụ đúng, giá trị ô là Integer bất biến. Nếu các ô là đối tượng có thể thay đổi, tạo hàng riêng mới chỉ giải quyết cấp hàng; còn phải quyết định mỗi ô có cần đối tượng riêng không.

Không có một lớp new ArrayList ở ngoài cùng làm mọi tầng dữ liệu tự độc lập.

### 7.3. Dùng chung không luôn là lỗi

Lặp một giá trị bất biến, một ký hiệu trạng thái chung hoặc một đối tượng được thiết kế dùng chung có thể đúng nhu cầu.

Lỗi xuất hiện khi nơi gọi mong mỗi vị trí có trạng thái riêng có thể cập nhật nhưng lại tạo nhiều tham chiếu tới cùng một đối tượng. Vì vậy, trước khi chọn nCopies hoặc fill, cần hỏi “mỗi vị trí cần cùng giá trị hay cần cùng một đối tượng?” và “có vị trí nào sẽ sửa trạng thái riêng về sau không?”.

## 8. Chọn thao tác theo mục đích

| Mục đích | Cách thể hiện |
|---|---|
| Thay mọi phần tử của list hiện có bằng cùng giá trị | Collections.fill(list,value) |
| Tạo list chỉ đọc có n lần cùng phần tử | Collections.nCopies(n,value) |
| Tạo list cho phép sửa có sẵn n vị trí ban đầu | new ArrayList<>(Collections.nCopies(n,value)) |
| Thêm n lần một giá trị vào cuối list hiện có | list.addAll(Collections.nCopies(n,value)) |
| Tạo n đối tượng có thể thay đổi độc lập | Tạo từng đối tượng trong vòng lặp hoặc bằng cơ chế sinh phần tử đúng hợp đồng |
| Thay mỗi phần tử theo một hàm phụ thuộc phần tử cũ | List.replaceAll(operator), nếu list hỗ trợ |
| Bỏ toàn bộ phần tử để size về 0 | clear(), nếu list hỗ trợ |
| Gán giá trị vào các ô của mảng | Arrays.fill(...) |

### 8.1. fill không phải clear rồi add

clear làm list rỗng, size bằng 0. fill giữ size và thay phần tử ở các vị trí cũ.

Nếu clear rồi gọi fill trên ArrayList vừa rỗng, không có vị trí nào để điền lại. Muốn tăng size trở lại phải thêm phần tử bằng thao tác phù hợp.

### 8.2. fill khác replaceAll về cách tính giá trị

fill nhận một đối tượng đã được đánh giá, rồi dùng cùng đối tượng ấy cho các vị trí được thay.

replaceAll nhận một hàm được áp dụng cho từng phần tử theo hợp đồng của List. Nó phù hợp khi giá trị mới phụ thuộc vào phần tử cũ hoặc khi cần gọi một phép tạo mới theo từng phần tử hiện có.

Tuy vậy, replaceAll cũng không tạo vị trí cho một list rỗng. Nó cũng không tự bảo đảm các kết quả của hàm là những đối tượng khác nhau: hàm có thể trả cùng tham chiếu nhiều lần.

Đặc tả [List.replaceAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#replaceAll(java.util.function.UnaryOperator)) tách rõ phép biến đổi từng phần tử này với việc gán cùng một phần tử của fill.

### 8.3. nCopies không trải hoặc sao chép nội dung collection được truyền làm phần tử

Nếu value chính là một List, nCopies(n,value) tạo list ngoài lặp tham chiếu tới list trong đó. Nó không tự lấy các phần tử của value rồi tạo n bản sâu.

Tương tự, List.of(Collections.nCopies(3,"x")) là một list có một phần tử là list bên trong, không phải cách tạo trực tiếp ba String x ở tầng ngoài.

Khi đọc code có nhiều tầng generic, hãy xác định kiểu phần tử của mỗi tầng trước khi suy luận kích thước và mức độ độc lập.

## 9. Chi phí, quyền sửa và các giới hạn thực tế

### 9.1. Fill đi qua các vị trí cần thay

Tài liệu fill mô tả thời gian tuyến tính theo số phần tử. Với các list thông thường có thao tác cập nhật phù hợp, nó phải xử lý các vị trí hiện có, không chỉ đổi một biến mô tả cho toàn bộ dữ liệu.

Không suy mọi List có cùng chi phí thực tế cho set. Những kiểu có cơ chế cập nhật đặc biệt, chẳng hạn tạo bản sao nội bộ khi ghi, có thể làm chi phí khác đáng kể. Hiểu dữ liệu đích trước khi dùng fill trong một đường xử lý thường xuyên.

### 9.2. Biểu diễn nCopies gọn không làm mọi thao tác phía sau rẻ

nCopies biểu diễn một phần tử lặp và số lần lặp một cách gọn. Nhưng:

- Duyệt hết hoặc in ra n vị trí vẫn phải xử lý n vị trí.
- new ArrayList<>(nCopies(n,value)) vật chất hóa một cấu trúc ArrayList chứa n tham chiếu.
- addAll vào list đích làm list đích chứa thêm các phần tử tương ứng.
- Đối tượng value có chi phí tạo riêng, diễn ra theo cách ứng dụng đánh giá đối số.

Không dùng tính gọn của list lặp để kết luận một bản ArrayList được sao chép từ nó cũng chỉ cần bộ nhớ không đổi theo n.

### 9.3. Quyền sửa của nguồn/view vẫn có ý nghĩa

fill trên view có thể thay dữ liệu nguồn, như Arrays.asList và subList trong chương trình đầu. Nếu một phương thức chỉ được quyền đọc, không nên nhận một view có thể ghi rồi xem fill là thao tác “chỉnh bản tạm”.

Ngược lại, bọc một nguồn bằng unmodifiableList không làm chủ sở hữu nguồn mất quyền sửa. fill qua lớp bọc bị chặn; fill trực tiếp qua tham chiếu nguồn cho phép sửa có thể vẫn cập nhật dữ liệu mà bên đọc quan sát.

### 9.4. Không tự đóng tài nguyên, hủy đối tượng hoặc khôi phục khi lỗi

Thay một tham chiếu khỏi list không gọi close trên tài nguyên cũ, không xóa mọi trạng thái của đối tượng cũ và không bảo đảm nó lập tức được thu gom. oldFirst trong chương trình vẫn giữ và đọc được Box cũ.

fill cũng không phải một giao dịch với cam kết hoàn tác mọi vị trí nếu một thao tác cập nhật của list đích bị lỗi. Với một list có ràng buộc hoặc triển khai riêng, cần hiểu chính sách cập nhật của nó và không tự coi nguồn như chưa bị động tới sau lỗi.

Khi nhiều luồng cùng đọc/ghi, một chuỗi thay nhiều vị trí không tự trở thành một lần công bố nguyên tử của toàn bộ danh sách. Cần cơ chế phối hợp hoặc mô hình dữ liệu phù hợp nếu bên đọc phải thấy một phiên bản nhất quán.

## 10. Những lỗi thường gặp

| Cách hiểu sai | Kết quả dễ gặp | Cách hiểu đúng |
|---|---|---|
| ArrayList<>(n) đã có n phần tử | Fill không tạo dữ liệu, set ở vị trí tưởng có bị lỗi | Constructor này tạo list rỗng có dung lượng ban đầu |
| Fill tăng size đến số mong muốn | List vẫn giữ size cũ | Fill thay vị trí hiện có |
| List kích thước cố định không thể fill | Bỏ qua trường hợp Arrays.asList hỗ trợ set | Thay phần tử khác với thêm/xóa |
| nCopies trả một ArrayList cho phép sửa | set/add/fill bị UnsupportedOperationException | Tạo ArrayList riêng nếu cần sửa cấu trúc |
| Copies nghĩa là n bản clone của phần tử | Sửa một đối tượng làm mọi vị trí hiện cùng giá trị | Các vị trí lặp cùng tham chiếu |
| New ArrayList ở ngoài làm mọi phần tử độc lập | Bản sao vẫn sửa đối tượng của nguồn | Sao chép cấu trúc là sao chép các tham chiếu |
| Fill “reset” từng đối tượng cũ | Người giữ đối tượng cũ không thấy trạng thái bị reset | Fill thay tham chiếu trong list |
| Tạo ma trận bằng nCopies một hàng | Cập nhật một hàng làm hàng khác đổi theo | Tạo từng hàng riêng nếu cần độc lập |
| n bằng 0 nên biểu thức tạo đối số không chạy | Constructor/tác dụng phụ vẫn xảy ra | Đối số được đánh giá trước lời gọi |
| Fill null làm các đối tượng cũ biến mất ngay | Nơi khác vẫn giữ chúng | Chỉ thay các tham chiếu ở những vị trí của list |

Với null và số lần lặp, cần nhớ riêng: nCopies nhận phần tử null nhưng từ chối n âm. fill phụ thuộc khả năng thay phần tử và chính sách giá trị của list đích.

Không lấy một thao tác không có hiệu lực trên list rỗng làm phép thử tổng quát xem list có cho sửa hay không. Các ví dụ gây UnsupportedOperationException trong bài đều dùng list không rỗng và thao tác thật sự nhằm thay/thêm phần tử.

## 11. Bài tập và lời giải

### Bài 1 — Dung lượng chưa phải phần tử

Tạo List<String> a = new ArrayList<>(4), rồi gọi fill(a,"x"). a có bao nhiêu phần tử?

**Lời giải:** 0. Dung lượng ban đầu 4 không tạo phần tử. Fill không tăng size nên không có vị trí nào được thêm.

### Bài 2 — Tạo các vị trí để set

Muốn một ArrayList có sẵn bốn vị trí null rồi gán chỉ số 2 thành ready, cách new ArrayList<>(4) đã đủ chưa?

**Lời giải:** chưa. Có thể tạo new ArrayList<>(Collections.nCopies(4,null)), rồi set(2,"ready"). Kết quả có size 4. List từ nCopies trực tiếp có size đúng nhưng không cho set, nên cần ArrayList riêng nếu muốn cập nhật.

### Bài 3 — List kích thước cố định

String[] a = {"a","b"}; List<String> v = Arrays.asList(a). Fill v bằng x có bắt buộc lỗi vì không thêm được phần tử không? Mảng a thế nào?

**Lời giải:** không. View này hỗ trợ thay phần tử dù kích thước cố định. Fill thành công và mảng a cũng có x ở hai ô.

### Bài 4 — Tạo n bản hay một bản?

Collections.nCopies(5,new Box(0)) gọi constructor Box bao nhiêu lần do biểu thức new đó? Nếu sửa Box lấy tại vị trí 2, các vị trí khác nhìn thấy gì?

**Lời giải:** biểu thức new tạo một Box. Mọi vị trí đều trả cùng tham chiếu, nên các vị trí khác cũng đọc trạng thái mới của Box sau khi sửa.

### Bài 5 — Fill khác sửa đối tượng cũ

Một list giữ ba Box khác nhau. Có biến outside giữ Box đầu tiên. Sau fill(list,new Box(10)), outside có tự trỏ đến Box mới hoặc bị đổi value thành 10 không?

**Lời giải:** không. Fill thay tham chiếu ở các vị trí list. outside vẫn trỏ Box cũ, với trạng thái cũ nếu không có thao tác khác thay đổi nó.

### Bài 6 — Sao chép list lặp

b = new ArrayList<>(Collections.nCopies(3,sharedBox)). b có cấu trúc sửa được. Điều đó có làm ba Box độc lập không?

**Lời giải:** không. b có ba vị trí riêng của cấu trúc ArrayList, nhưng cả ba chứa cùng tham chiếu sharedBox. Muốn Box độc lập phải tạo đối tượng riêng cho từng vị trí theo nhu cầu.

### Bài 7 — Ma trận hai hàng

Tạo một hàng cho phép sửa r, rồi new ArrayList<>(Collections.nCopies(2,r)). Nếu gọi get(0).set(1,7), hàng thứ hai có thể cũng hiện 7 không?

**Lời giải:** có, vì hai vị trí hàng cùng trỏ tới r. Tạo ArrayList ngoài không sao chép sâu hàng. Để hai hàng độc lập, tạo từng hàng mới bên trong vòng lặp.

### Bài 8 — Biên số lần lặp

nCopies(0,"x"), nCopies(-1,"x") và nCopies(2,null) có kết quả gì?

**Lời giải:** lần lượt là list rỗng, IllegalArgumentException, và list hai phần tử null không cho sửa cấu trúc/vị trí.

### Bài 9 — N bằng 0 và biểu thức đối số

nCopies(0,createExpensiveObject()) có bảo đảm createExpensiveObject không được gọi không?

**Lời giải:** không. Java đánh giá đối số trước lời gọi nên hàm vẫn được gọi một lần. nCopies chỉ nhận kết quả của lời gọi ấy và số lần lặp bằng 0.

### Bài 10 — Chọn thao tác phù hợp

Đã có list ba số [1,2,3]. Cần thay thành [0,0,0] nhưng giữ size; một nhu cầu khác là thêm ba số 0 vào cuối để thành [1,2,3,0,0,0]. Hai trường hợp dùng gì?

**Lời giải:** trường hợp đầu dùng fill trên list có hỗ trợ thay phần tử. Trường hợp sau dùng addAll(Collections.nCopies(3,0)) trên list có hỗ trợ thêm. nCopies cung cấp dữ liệu lặp, còn addAll mới làm list đích lớn lên.

### Bài 11 — Một list ngoài hay ba phần tử?

List.of(Collections.nCopies(3,"x")) có size ở tầng ngoài bằng bao nhiêu?

**Lời giải:** 1. Phần tử duy nhất là list bên trong có ba x. Không có phép tự trải list lồng nhau ở lời gọi này.

### Bài 12 — Giá trị theo phần tử cũ

Đã có list các số và cần mỗi số mới bằng số cũ nhân hai. Fill bằng một giá trị có đáp ứng không?

**Lời giải:** không biểu đạt đúng phép biến đổi từng giá trị. Có thể dùng replaceAll với hàm tương ứng nếu list hỗ trợ thay phần tử, hoặc tạo một kết quả mới theo hợp đồng mong muốn. Fill dùng cùng một đối tượng đối số cho các vị trí, không tự tính theo từng phần tử cũ.

## 12. Tài liệu đối chiếu

- [Collections.fill — thay phần tử của list](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#fill(java.util.List,T)).
- [Collections.nCopies — danh sách lặp một đối tượng](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#nCopies(int,T)).
- [ArrayList — kích thước, dung lượng và constructor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).
- [Arrays.asList và Arrays.fill](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html).
- [List — set, replaceAll và subList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collections-utilities-006">copy: kích thước đích và khác biệt với tạo bản sao mới</a>

<details>
<summary>Xem chi tiết</summary>

# Collections.copy: kích thước đích và khác biệt với tạo bản sao mới

Tên `copy` dễ khiến người đọc nghĩ rằng lời gọi sẽ tạo một list mới chứa dữ liệu nguồn. `Collections.copy` làm một việc cụ thể hơn: nó **ghi các phần tử nguồn vào những vị trí đã có của list đích**, bắt đầu từ vị trí 0.

Điểm cần nhớ là `destination.size() >= source.size()`, không phải capacity của đích đủ lớn. Đích cũng phải hỗ trợ thay phần tử. Khi đích dài hơn nguồn, phần đuôi đích được giữ nguyên; phương thức không thu nhỏ hay nối thêm list.

Bài này giải thích hợp đồng ấy bằng Java 17 và đối chiếu nó với `new ArrayList<>(source)`, `addAll` và `List.copyOf`. Các ví dụ cũng tách việc sao chép vật chứa khỏi việc nhân bản đối tượng phần tử.

## 1. Đọc chữ ký theo đúng hướng dữ liệu

Chữ ký của phương thức:

~~~java
public static <T> void copy(
        List<? super T> dest,
        List<? extends T> src
)
~~~

Đây là phần khai báo API, không phải một phương thức cần tự cài lại. **Đích đứng trước, nguồn đứng sau**:

~~~java
Collections.copy(destination, source);
~~~

Phương thức trả `void`. Không có list kết quả mới để gán từ lời gọi. Đối tượng list đích đã tồn tại từ trước và chính nó bị thay dữ liệu.

Nguồn cần đọc được các phần tử; nó có thể là list không sửa được. Đích cần đủ số vị trí đang có và cần cho thay phần tử ở các vị trí được ghi. Hai yêu cầu “đủ vị trí” và “được quyền thay” độc lập nhau.

Về kiểu dữ liệu, nguồn sản xuất phần tử nên có `? extends T`; đích tiêu thụ phần tử nên có `? super T`. Nhờ vậy có thể đưa dữ liệu `Integer` vào một `List<Number>` hợp lệ mà không ép kiểu raw.

~~~java
List<Integer> source = List.of(7, 8);
List<Number> destination =
        new ArrayList<>(List.<Number>of(0.5, 1.5, 2.5));

Collections.copy(destination, source);
~~~

Đoạn trên là mã minh họa trong một phương thức có đủ import. Đích thành `[7, 8, 2.5]`. Kiểu rộng hơn của đích nhận được các số nguyên; vị trí còn lại vẫn giữ số thực cũ.

## 2. Hậu điều kiện: ghi đè đoạn đầu và giữ phần còn lại

Giả sử hai list độc lập ban đầu có nội dung:

~~~text
source:      [A, B]
destination: [old0, old1, tail2, tail3]
~~~

Sau `Collections.copy(destination, source)`:

~~~text
source:      [A, B]
destination: [A, B, tail2, tail3]
~~~

Với nguồn có `n` phần tử và đích có `m` phần tử, khi phép copy hợp lệ hoàn thành trên hai vùng không ảnh hưởng lẫn nhau:

- Các vị trí `0` đến `n - 1` của đích nhận phần tử tương ứng từ nguồn.
- Các vị trí `n` đến `m - 1` của đích giữ nguyên.
- Kích thước đích vẫn là `m`.
- Không có bước `add` để nới đích hoặc `remove` để làm ngắn đích.

Đó là hợp đồng của [Collections.copy, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#copy(java.util.List,java.util.List)).

### Copy nguồn ngắn hơn không xóa dữ liệu cũ ở đuôi

Nếu tiếp tục copy `[Z]` vào đích đang là `[A, B, tail2, tail3]`, đích thành:

~~~text
[Z, B, tail2, tail3]
~~~

B, tail2 và tail3 vẫn còn. Nếu yêu cầu của ứng dụng là “đích có đúng nội dung và đúng số phần tử như nguồn”, cần xử lý việc thay toàn bộ dữ liệu bằng một chiến lược khác. `copy` riêng nó chỉ thay đoạn đầu.

Điều này quan trọng khi tái sử dụng một list làm vùng kết quả: dữ liệu cũ ở đuôi không tự biến thành dữ liệu không còn hiệu lực. API của ứng dụng phải nói rõ độ dài dữ liệu hợp lệ hoặc quản lý kích thước list đúng yêu cầu.

### Giữ đối tượng đích đồng nghĩa các tham chiếu khác thấy thay đổi

~~~java
List<String> alias = destination;
Collections.copy(destination, source);
~~~

`alias` và `destination` vẫn trỏ tới cùng một list. Người giữ `alias` nhìn thấy dữ liệu ở các vị trí được thay.

Không giống việc tạo một list mới rồi gán lại riêng biến cục bộ, cách copy vào đối tượng hiện có giữ nguyên định danh của vật chứa. Điều đó hữu ích khi một thành phần cố ý sở hữu các ô dữ liệu sẵn có, nhưng cũng có thể gây tác động ngoài ý muốn nếu đích đang được chia sẻ.

## 3. Size là số phần tử thật; capacity chỉ là sức chứa

### Lỗi thường gặp với hàm tạo nhận số nguyên

~~~java
List<String> source = List.of("A", "B");
List<String> destination = new ArrayList<>(source.size());

Collections.copy(destination, source);
~~~

Đây là ví dụ cố ý sai: đích có capacity ban đầu cho 2 phần tử nhưng `size()` vẫn bằng 0. Chưa có vị trí 0 hay 1 để thay, nên copy nguồn không rỗng ném `IndexOutOfBoundsException`.

`new ArrayList<>(n)` tạo list rỗng với sức chứa ban đầu, không tạo `n` phần tử `null`. `ensureCapacity(n)` cũng không bổ sung phần tử vào list. [Hàm tạo ArrayList(int), Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html#%3Cinit%3E(int)).

Chỉ số hợp lệ được xác định bởi `size`, không phải lượng bộ nhớ phía trong đã được dự trữ. Sau khi `clear` một list, `size` về 0 dù cách quản lý bộ nhớ bên trong có thể vẫn còn vùng đã cấp phát; list ấy cũng chưa có các ô để `set` hoặc copy vào.

### Cần các phần tử ban đầu nếu muốn copy vào list mới

Một cách tạo đủ vị trí là:

~~~java
List<String> destination =
        new ArrayList<>(Collections.nCopies(source.size(), "?"));

Collections.copy(destination, source);
~~~

Lúc này hàm tạo `ArrayList(Collection)` nhận một collection có đúng số phần tử, nên list mới có các vị trí tương ứng. Nó cho thay phần tử, vì thế đáp ứng cả hai yêu cầu.

Có thể dùng một vòng lặp `add` để tạo đủ các ô ban đầu. Không bắt buộc dùng `nCopies`; điều quyết định là list đích thật sự đã có số phần tử cần thay.

Tuy nhiên, nếu mục tiêu duy nhất là một list mới giống nguồn, tạo ô rồi copy thường vòng vèo hơn cần thiết:

~~~java
List<String> result = new ArrayList<>(source);
~~~

Cách này vừa tạo vật chứa mới vừa đưa các phần tử nguồn vào ngay từ hàm tạo. Nó không cần một đối tượng đích đang có để ghi đè.

### Ba trạng thái có vẻ gần nhau nhưng khác điều kiện

| Cách chuẩn bị đích | Size với `n > 0` | Có thay phần tử được không? | Đủ làm đích copy n phần tử? |
| --- | --- | --- | --- |
| `new ArrayList<T>(n)` | 0 | Có thể thay khi vị trí đã tồn tại, nhưng hiện chưa có | Không |
| `Collections.nCopies(n, value)` | n | Không hỗ trợ thay | Không |
| `new ArrayList<>(Collections.nCopies(n, value))` | n | Có | Có |
| `new ArrayList<>(source)` | Size của nguồn | Có | Có, nhưng thường đã đạt mục tiêu tạo bản sao mới |

Không lấy việc list hỗ trợ `add` để thay điều kiện size. Một `ArrayList` rỗng có thể thêm nhưng không cho `set(0, value)` vì vị trí 0 chưa tồn tại.

## 4. Đích đủ dài chưa chắc cho thay phần tử

### Quyền `set` khác với quyền tăng giảm kích thước

`copy` cần thay giá trị ở các ô. Nó không đòi đích phải là một `ArrayList` và không bắt buộc list đổi kích thước được.

Ví dụ:

~~~java
String[] array = {"x", "y", "keep"};
List<String> destination = Arrays.asList(array);

Collections.copy(destination, List.of("A", "B"));
~~~

List từ `Arrays.asList` có kích thước cố định nhưng cho thay phần tử, nên copy thành công. Chính mảng thành `[A, B, keep]` vì list là view dựa trên mảng. [API Arrays.asList, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

Đây là tình huống mà “không `add` được” không ngăn việc dùng làm đích copy.

### List không sửa được không cấp quyền ghi đè

Một list do `List.of`, `List.copyOf` hoặc `Collections.nCopies` tạo có thể có đủ số phần tử nhưng không hỗ trợ thay. Copy dữ liệu thực sự vào nó sẽ ném `UnsupportedOperationException`.

~~~java
List<String> destination = List.of("?", "?");
Collections.copy(destination, List.of("A", "B"));
~~~

Đoạn trên cố ý minh họa lỗi quyền sửa. Việc `destination` có kiểu `List<String>` không đồng nghĩa mọi phương thức sửa khai báo trên giao diện đều được hỗ trợ.

`List.set` là thao tác tùy chọn. Ngoài quyền sửa, list cụ thể còn có thể áp ràng buộc về loại phần tử hoặc `null`. [Hợp đồng List.set, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#set(int,E)).

### Nguồn không cần quyền sửa

Nguồn chỉ được đọc để lấy phần tử. `List.of("A", "B")` làm nguồn hợp lệ dù nó không cho `set`.

Đừng đổi nhầm hai tham số rồi cho rằng lỗi phát sinh ở nguồn. Đích là tham số thứ nhất, và quyền thay phần tử của nó mới là điều cần cho quá trình ghi.

Cũng không dùng source rỗng làm cách kiểm tra tổng quát quyền sửa đích. Khi không có phần tử nào để thay, có thể không có thao tác ghi nào được thực hiện; một lần không ném lỗi không chứng minh đích dùng được cho nguồn không rỗng.

## 5. So sánh copy vào đối tượng cũ với tạo kết quả mới

| Thao tác | Có vật chứa kết quả mới? | Tác động lên đích hiện có | Size sau thao tác | Quyền sửa kết quả |
| --- | --- | --- | --- | --- |
| `Collections.copy(dest, src)` | Không tự tạo | Ghi đè đoạn đầu | Giữ size cũ của dest | Theo loại dest đã chọn |
| `new ArrayList<>(src)` | Có, một ArrayList mới | Không cần dest cũ | Bằng số phần tử nguồn | Sửa được |
| `dest.addAll(src)` | Không tự tạo | Chèn/nối phần tử vào dest | Tăng theo số phần tử thêm | Dest phải hỗ trợ thêm |
| `List.copyOf(src)` | Trả list có hợp đồng không sửa được; có thể tái sử dụng kết quả thích hợp | Không ghi đè dest cũ | Bằng số phần tử nguồn | Không sửa được; từ chối phần tử null |

### Khi cần giữ nguyên đối tượng đích và các ô phía sau

`Collections.copy` phù hợp khi đã có vùng list đủ lớn và chủ đích là thay một đoạn đầu. Ví dụ một bộ hiển thị đang sở hữu những ô vị trí cố định, cần cập nhật các vị trí đầu và giữ phần khác theo quy tắc riêng.

Phải biết ai đang giữ tham chiếu tới đích. Copy làm họ nhìn thấy thay đổi. Nếu mục tiêu là trả một giá trị riêng cho người gọi, việc giữ một đích dùng chung có thể đi ngược yêu cầu.

### Khi cần bản làm việc chỉnh sửa độc lập về cấu trúc

`new ArrayList<>(source)` là lựa chọn trực tiếp. Các lần thêm/xóa/thay tham chiếu trên list mới không điều khiển các ô của list nguồn.

Nó vẫn giữ các tham chiếu tới phần tử. Độc lập về cấu trúc list chưa là độc lập về mọi đối tượng bên trong. [Hàm tạo ArrayList(Collection), Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html#%3Cinit%3E(java.util.Collection)).

### Khi cần nối dữ liệu

`addAll` giữ phần tử cũ và thêm phần tử nguồn vào cuối nếu dùng dạng không chỉ số. Đích `[old, tail]` thêm `[A, B]` thành `[old, tail, A, B]`.

Điều này khác copy, vì copy cùng nguồn lên đích ấy thành `[A, B]` và không làm size tăng. Không thay thế một lời gọi bằng lời gọi kia chỉ vì cả hai đều đưa dữ liệu từ nguồn sang đích.

### Khi cần một list không sửa được từ nguồn

`List.copyOf` trả kết quả không theo những thay đổi thành phần sau đó của collection nguồn, nhưng không nhân bản sâu các đối tượng phần tử. Nó từ chối `null` trong nguồn và không bảo đảm mỗi lần gọi tạo một định danh list mới.

Tên `copyOf` vì thế cũng không cho phép suy “luôn cấp phát một object mới hoàn toàn”. Dùng hợp đồng nội dung và quyền sửa, không dùng `==` để xác minh một bản dữ liệu thích hợp. [API List.copyOf, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)).

## 6. Sao chép phần tử ở đây là sao chép giá trị tham chiếu

Giả sử nguồn chứa một đối tượng `Box` có số lượng sửa được:

~~~text
source[0] ───────> Box("stock", 1)
~~~

Sau copy vào đích, quan hệ là:

~~~text
source[0] ───────┐
                ├──> cùng một Box("stock", 1)
destination[0] ─┘
~~~

Không có bước gọi hàm tạo `Box` mới. Nếu đổi `count` của đối tượng đó thành 2, cả nguồn và đích đều đọc thấy `stock:2`.

Ngược lại, `destination.set(0, new Box("local", 9))` thay tham chiếu riêng ở ô đích. Ô nguồn vẫn trỏ tới đối tượng `stock`. Hai loại thao tác phải được phân biệt:

1. **Sửa đối tượng đang được chia sẻ:** có thể hiện qua cả hai list.
2. **Thay tham chiếu tại một vị trí của list đích:** không tự thay tham chiếu tương ứng của nguồn độc lập.

Tạo `new ArrayList<>(source)` cũng sao chép phần tử theo cách này. `List.copyOf(source)` không đóng băng các đối tượng bên trong chỉ vì list ngoài không sửa được.

Nếu yêu cầu là người nhận được chỉnh sửa hoàn toàn riêng, phải quyết định cách sao chép từng phần sửa được của mô hình. Nếu yêu cầu là dữ liệu không thay đổi sau công bố, cần một biểu diễn được bảo vệ đầy đủ. Hai mục tiêu ấy không tự được giải quyết bởi tên “copy”.

Tài liệu `Collection` phân biệt rõ tính không sửa được của vật chứa với trạng thái phần tử. [Collection, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#unmodcoll).

## 7. Copy vào một đoạn và xử lý hai vùng chồng lấp

### Dùng `subList` làm đích có độ lệch

`Collections.copy` không có tham số chỉ số bắt đầu. Có thể truyền view của đoạn cần ghi:

~~~java
List<String> destination =
        new ArrayList<>(List.of("L", "x", "y", "R"));
List<String> source = List.of("A", "B");

Collections.copy(destination.subList(1, 3), source);
~~~

View có hai phần tử tương ứng vị trí 1 và 2 của nguồn đích. Kết quả list gốc là `[L, A, B, R]`. Vị trí 0 của view không phải vị trí 0 của list gốc.

Cận cuối của `subList(from, to)` không được lấy. Đoạn `[1, 3)` có độ dài 2. Vẫn phải bảo đảm view có đủ size cho nguồn và hỗ trợ thay phần tử.

`subList` không tự tạo một bản sao. Các lần ghi vào view tác động lên list nền. Không thêm/xóa cấu trúc list nền qua đường khác rồi tiếp tục dùng view như thể vòng đời của nó không đổi. [API List.subList, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#subList(int,int)).

### Nguồn và đích có thể cùng chia sẻ list nền

Nếu lấy hai đoạn của cùng list, một lần ghi đích có thể thay dữ liệu mà nguồn còn định đọc ở bước sau. Không nên suy rằng `Collections.copy` luôn chụp toàn bộ nguồn trước khi ghi hoặc tự xử lý mọi tình huống chồng lấp như một API sao chép mảng có hợp đồng riêng.

Nếu ý định là chuyển dữ liệu **ban đầu** của một đoạn sang đoạn chồng lấp, tạo bản chụp nguồn tách biệt trước:

~~~java
List<String> values =
        new ArrayList<>(List.of("A", "B", "C", "D", "E"));

List<String> snapshot =
        new ArrayList<>(values.subList(0, 3));

Collections.copy(values.subList(1, 4), snapshot);
~~~

Nguồn đã chốt ở tầng list là `[A, B, C]`. Ghi vào các vị trí 1, 2, 3 cho kết quả `[A, A, B, C, E]`.

Ví dụ không dự đoán kết quả của một cách gọi chồng lấp không có bản chụp. Nó thiết kế nguồn và đích để dữ liệu cần đọc không còn bị chính quá trình ghi làm đổi.

Nếu phần tử là đối tượng sửa được và bạn cần cả trạng thái của chúng tại một thời điểm, một bản sao list vẫn chưa đủ để chụp sâu. Trong ví dụ các phần tử là chuỗi nên vấn đề đang xét là các vị trí của list.

## 8. `null`, lỗi và giới hạn của một lần copy

### Chính list không được là `null`

Truyền nguồn hoặc đích là `null` gây `NullPointerException`. Một nguồn `null` không có cùng nghĩa với list nguồn rỗng.

Nguồn rỗng có size 0 nên không có phần tử nào cần chuyển. Đích sửa được chứa `[keep]` vẫn giữ `[keep]` trong thí nghiệm. Đừng dùng một nguồn rỗng để thay thao tác làm rỗng đích.

### Phần tử `null` phụ thuộc hợp đồng đích

Với nguồn `[null, N]` và đích `ArrayList` có ba ô `[?, ?, tail]`, copy cho `[null, N, tail]`. `ArrayList` cho phép `null`.

Một list cụ thể có thể từ chối `null` hoặc một số loại giá trị. Đủ size và có phương thức `set` vẫn chưa bảo đảm mọi dữ liệu bất kỳ là hợp lệ với đích.

Trong khi đó, `List.copyOf(source)` từ chối nguồn có phần tử `null`. Đây là khác biệt hành vi phải xét khi đổi cách tạo dữ liệu.

### Không coi lỗi là một lời hứa rollback

Các lỗi điển hình gồm đích quá ngắn và không hỗ trợ thay phần tử. Với list tùy chỉnh hoặc dữ liệu không hợp lệ với đích, còn có thể có lỗi trong khi đọc/ghi các phần tử.

Hợp đồng không cung cấp một giao dịch với bảo đảm tự phục hồi toàn bộ dữ liệu cũ nếu mọi bước không hoàn thành. Không dựa vào một thí nghiệm lỗi kích thước để suy ra mọi lỗi ở mọi loại list đều xảy ra trước lần ghi đầu tiên.

Nếu cần kiểm tra/biến đổi dữ liệu có thể thất bại, chuẩn bị và kiểm tra trên dữ liệu riêng trước khi công bố thay đổi theo giao thức sở hữu của ứng dụng. Điều đó vẫn khác với bảo đảm cập nhật nguyên tử cho một list đang được chia sẻ.

### Không có ảnh chụp đồng thời tự động

Nếu nguồn thay đổi trong khi copy, hoặc luồng khác đọc đích giữa các bước ghi, kết quả có thể không là một trạng thái thống nhất theo yêu cầu nghiệp vụ.

Cần giữ nguồn ổn định và kiểm soát quyền ghi/đọc đích trong toàn khoảng thao tác nếu yêu cầu có tính nhất quán. Đồng bộ từng lời gọi nhỏ không tự gộp cả quá trình copy thành một giao dịch ứng dụng.

## 9. Chương trình hoàn chỉnh và bằng chứng chạy

Lưu mã vào **`ListCopyDemo.java`**. Hàm `rejects` kiểm tra đúng loại ngoại lệ mong đợi; nếu thao tác thành công ngoài dự kiến hoặc ném sai loại, chương trình dừng bằng `AssertionError`.

Các nguồn `String` đơn giản dùng để kiểm tra size và đoạn đuôi. Đối tượng `Box` được dùng riêng để nhìn thấy sự chia sẻ phần tử; không có cách tạo list nào trong nhóm thí nghiệm tự nhân bản `Box`.

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ListCopyDemo {
    static final class Box {
        private final String name;
        private int count;

        Box(String name, int count) {
            this.name = name;
            this.count = count;
        }

        @Override
        public String toString() {
            return name + ":" + count;
        }
    }

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
        List<String> source = List.of("A", "B");
        List<String> destination = new ArrayList<>(
                List.of("old0", "old1", "tail2", "tail3"));
        List<String> alias = destination;
        Collections.copy(destination, source);
        System.out.println("prefix.destination=" + destination);
        System.out.println("prefix.size=" + destination.size());
        System.out.println("prefix.sameObject=" + (alias == destination));
        Collections.copy(destination, List.of("Z"));
        System.out.println("prefix.afterShortSource=" + alias);
        System.out.println("source.after=" + source);

        List<String> capacityOnly = new ArrayList<>(source.size());
        System.out.println("capacity.size=" + capacityOnly.size());
        rejects("capacity.copy", IndexOutOfBoundsException.class,
                () -> Collections.copy(capacityOnly, source));
        List<String> tooSmall = new ArrayList<>(List.of("old"));
        rejects("tooSmall.copy", IndexOutOfBoundsException.class,
                () -> Collections.copy(tooSmall, source));

        List<String> readyTarget =
                new ArrayList<>(Collections.nCopies(source.size(), "?"));
        Collections.copy(readyTarget, source);
        System.out.println("readyTarget=" + readyTarget);
        rejects("readonly.copy", UnsupportedOperationException.class,
                () -> Collections.copy(List.of("?", "?"), source));
        rejects("nCopies.copy", UnsupportedOperationException.class,
                () -> Collections.copy(Collections.nCopies(2, "?"), source));

        String[] backing = {"x", "y", "keep"};
        Collections.copy(Arrays.asList(backing), source);
        System.out.println("fixed.array=" + Arrays.toString(backing));

        List<String> fresh = new ArrayList<>(source);
        fresh.add("C");
        System.out.println("fresh.editable=" + fresh);
        List<String> appended = new ArrayList<>(List.of("old", "tail"));
        appended.addAll(source);
        System.out.println("append.result=" + appended);

        List<Number> numbers =
                new ArrayList<>(List.<Number>of(0.5, 1.5, 2.5));
        Collections.copy(numbers, List.of(7, 8));
        System.out.println("numbers.destination=" + numbers);

        List<Box> boxSource =
                new ArrayList<>(List.of(new Box("stock", 1)));
        List<Box> boxDestination =
                new ArrayList<>(Collections.<Box>nCopies(1, null));
        Collections.copy(boxDestination, boxSource);
        List<Box> freshBoxes = new ArrayList<>(boxSource);
        List<Box> readonlyBoxes = List.copyOf(boxSource);
        System.out.println("boxes.sameElement="
                + (boxSource.get(0) == boxDestination.get(0)));
        boxSource.get(0).count = 2;
        System.out.println("boxes.source=" + boxSource);
        System.out.println("boxes.destination=" + boxDestination);
        System.out.println("boxes.fresh=" + freshBoxes);
        System.out.println("boxes.readonly=" + readonlyBoxes);
        boxDestination.set(0, new Box("local", 9));
        System.out.println("boxes.afterDestinationReplace=" + boxDestination);
        System.out.println("boxes.sourceAfterReplace=" + boxSource);

        List<String> withNull = Arrays.asList(null, "N");
        List<String> nullableDestination =
                new ArrayList<>(List.of("?", "?", "tail"));
        Collections.copy(nullableDestination, withNull);
        System.out.println("nullable.destination=" + nullableDestination);
        rejects("nullable.copyOf", NullPointerException.class,
                () -> List.copyOf(withNull));

        List<String> window =
                new ArrayList<>(List.of("L", "x", "y", "R"));
        Collections.copy(window.subList(1, 3), source);
        System.out.println("window.destination=" + window);

        List<String> overlapping =
                new ArrayList<>(List.of("A", "B", "C", "D", "E"));
        List<String> snapshot =
                new ArrayList<>(overlapping.subList(0, 3));
        Collections.copy(overlapping.subList(1, 4), snapshot);
        System.out.println("overlap.withSnapshot=" + overlapping);

        List<String> linked =
                new LinkedList<>(List.of("x", "y", "tail"));
        Collections.copy(linked, source);
        System.out.println("linked.destination=" + linked);

        List<String> keep = new ArrayList<>(List.of("keep"));
        Collections.copy(keep, Collections.<String>emptyList());
        System.out.println("emptySource.destination=" + keep);
        rejects("null.destination", NullPointerException.class,
                () -> Collections.copy((List<String>) null, source));
        rejects("null.source", NullPointerException.class,
                () -> Collections.copy(keep, (List<String>) null));
    }
}
~~~

Biên dịch và chạy tại thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 ListCopyDemo.java
java ListCopyDemo
~~~

Cần JDK 17 trở lên cho lệnh biên dịch này. Mã đã được biên dịch với `--release 17` và chạy trên OpenJDK 24.0.2, thoát thành công. Stdout chính xác:

~~~text
prefix.destination=[A, B, tail2, tail3]
prefix.size=4
prefix.sameObject=true
prefix.afterShortSource=[Z, B, tail2, tail3]
source.after=[A, B]
capacity.size=0
capacity.copy=IndexOutOfBoundsException
tooSmall.copy=IndexOutOfBoundsException
readyTarget=[A, B]
readonly.copy=UnsupportedOperationException
nCopies.copy=UnsupportedOperationException
fixed.array=[A, B, keep]
fresh.editable=[A, B, C]
append.result=[old, tail, A, B]
numbers.destination=[7, 8, 2.5]
boxes.sameElement=true
boxes.source=[stock:2]
boxes.destination=[stock:2]
boxes.fresh=[stock:2]
boxes.readonly=[stock:2]
boxes.afterDestinationReplace=[local:9]
boxes.sourceAfterReplace=[stock:2]
nullable.destination=[null, N, tail]
nullable.copyOf=NullPointerException
window.destination=[L, A, B, R]
overlap.withSnapshot=[A, A, B, C, E]
linked.destination=[A, B, tail]
emptySource.destination=[keep]
null.destination=NullPointerException
null.source=NullPointerException
~~~

### Đọc các nhóm kết quả

**`prefix`:** copy hai phần tử thay đúng hai vị trí đầu trong đích bốn phần tử. Size vẫn 4 và biến alias vẫn trỏ tới cùng đích. Copy nguồn chỉ có Z tiếp theo không xóa B hay các ô đuôi.

**`source.after`:** nguồn độc lập có thứ tự và nội dung ban đầu. Chương trình không tráo hai tham số.

**`capacity` và `tooSmall`:** capacity bằng độ dài nguồn nhưng size 0 vẫn không đủ. Đích có một phần tử cho nguồn hai phần tử cũng quá ngắn. Cả hai kiểm tra kích thước tách biệt khỏi lỗi quyền sửa.

**`readyTarget`:** bản `ArrayList` tạo từ các ô ban đầu vừa đủ số vị trí vừa hỗ trợ thay. `readonly.copy` và `nCopies.copy` có đủ size nhưng từ chối ghi.

**`fixed.array`:** đích kích thước cố định vẫn nhận dữ liệu vì cho thay phần tử. Dòng in mảng xác nhận tác động lên đối tượng nền của view.

**`fresh` và `append`:** một thao tác tạo bản làm việc mới, thao tác còn lại nối thêm nguồn vào đích cũ. Không thao tác nào có cùng hậu điều kiện với ghi đè đoạn đầu của `copy`.

**`numbers`:** chữ ký generic cho phép nguồn số nguyên vào đích kiểu `Number`. Phần đuôi `2.5` vẫn còn.

**`boxes`:** nguồn và đích giữ cùng phần tử, được xác nhận bằng so sánh tham chiếu phần tử trong thí nghiệm. Sửa count xuất hiện qua cả bản tạo bằng constructor lẫn list không sửa được. Thay ô đích bằng Box mới chỉ đổi ô ở đích riêng.

**`nullable`:** phần tử `null` được đưa vào `ArrayList`, còn factory `List.copyOf` từ chối nguồn có `null`. Quyền sửa và chính sách phần tử là các hợp đồng khác nhau.

**`window` và `overlap`:** view đích thay đúng đoạn của list gốc. Trường hợp chồng lấp dùng bản chụp nguồn tách biệt nên ghi đúng dữ liệu ban đầu đã chọn.

**`linked`:** đích không bắt buộc là `ArrayList`; `LinkedList` có đủ vị trí và hỗ trợ thay cũng dùng được. Dòng này kiểm tra nội dung, không phải phép đo thời gian.

**`emptySource` và các dòng `null`:** nguồn rỗng không làm rỗng đích; thiếu chính đối tượng nguồn/đích bị báo bằng ngoại lệ.

## 10. Áp dụng và chi phí

### Chọn copy khi đích tồn tại là một phần của yêu cầu

Nếu đã có vùng vị trí cần cập nhật và phải giữ nguyên đối tượng đích, `Collections.copy` có ý nghĩa trực tiếp. Phần còn lại của đích giữ nguyên là một bảo đảm cần được chủ ý sử dụng.

Nếu bạn vừa tạo một đích mới bằng các ô giả rồi copy chỉ để trả một list mới giống nguồn, `new ArrayList<>(source)` thường thể hiện ý định dễ hơn. Không cần tối ưu bằng một chuỗi chuẩn bị phức tạp khi chưa có yêu cầu giữ đích.

### Dữ liệu ngắn hơn lần trước cần một quy tắc xử lý đuôi

Giả sử đích được dùng cho một đợt ba kết quả rồi đợt sau chỉ có một kết quả. Copy đợt hai sẽ không tự xóa hai ô cũ.

Có thể thiết kế một vùng có độ dài cố định và quản lý riêng số ô có dữ liệu hợp lệ. Cũng có thể cần thay toàn bộ danh sách và size để phản ánh đúng đợt kết quả mới. Hai mô hình khác nhau; đừng để một đoạn đuôi vô tình quyết định hành vi sản phẩm.

Nếu muốn xóa và thêm lại trên cùng list, phải biết đích có hỗ trợ các thao tác cấu trúc ấy không và ai quan sát các trạng thái trung gian. Đó không là một biến thể tự động của `Collections.copy`.

### Công việc tăng theo số phần tử nguồn

`Collections.copy` được tài liệu mô tả chạy tuyến tính. Với các list chuẩn và phép truy cập/thay thông thường, việc chuyển n phần tử phải xử lý n giá trị tham chiếu.

Không cần clone các đối tượng phần tử để hoàn thành hợp đồng này. Nếu bạn bổ sung một bước chuyển đổi hoặc sao chép sâu theo mô hình, chi phí ấy là phần việc khác.

Các lớp list có cách truy cập khác nhau. `ArrayList` có truy cập theo vị trí hằng thời gian; `LinkedList` phải đi qua nút cho truy cập bằng chỉ số. Không tự thay tiện ích bằng một vòng `get(i)`/`set(i)` rồi giả định mọi kiểu list đều có chi phí như nhau. [ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html), [LinkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html).

Với list tùy chỉnh có cách đọc/ghi tốn kém, cần xét chi phí của các thao tác nó cung cấp. Tên API không làm biến mất chi phí của đích hoặc nguồn.

## 11. Cách kiểm tra một lời gọi copy

Trước hết, viết trạng thái mong muốn của đích sau thao tác. Nếu mong muốn là “nối dữ liệu”, “tạo list mới” hoặc “thu nhỏ đích theo nguồn”, hãy nhận ra rằng đó không phải hậu điều kiện riêng của `Collections.copy`.

Sau đó kiểm tra các thông tin thực sự quyết định:

1. Hai tham số có đúng hướng đích–nguồn không?
2. Đích có ít nhất `source.size()` phần tử thật chưa?
3. Đích có cho thay phần tử và nhận các giá trị nguồn không?
4. Phần đuôi giữ nguyên có đúng với yêu cầu không?
5. Nguồn và đích có cùng nền hoặc bị bên khác sửa trong lúc thao tác không?
6. Chia sẻ đối tượng phần tử có đúng quyền sở hữu cần thiết không?

Trong kiểm thử, đừng chỉ so đoạn đầu. Cần thử đích dài hơn nguồn để phát hiện hiểu sai về đuôi và size; thử capacity-only để phát hiện nhầm sức chứa; thử fixed-size sửa được và list không sửa được để tách quyền thay khỏi khả năng tăng giảm.

Nếu dữ liệu chứa đối tượng sửa được, kiểm tra riêng việc sửa phần tử và việc thay ô. Hai thao tác thể hiện hai quan hệ khác nhau và thường bị gộp dưới từ “copy”.

## 12. Thực hành có đáp án

### Bài 1 — Nguồn hai, đích bốn

Đích là `[p, q, r, s]`, nguồn là `[A, B]`. Cả hai là list độc lập, đích hỗ trợ thay.

**Câu hỏi:** Sau copy, đích và size là gì? Nguồn có bị tiêu thụ hay bị làm rỗng không?

**Đáp án:** Đích là `[A, B, r, s]`, size vẫn 4. Nguồn vẫn có A và B; copy không lấy phần tử ra khỏi nguồn. Hai vị trí cuối giữ nguyên vì nằm ngoài đoạn được ghi.

### Bài 2 — Đã cấp capacity nhưng vẫn lỗi

~~~java
List<Integer> source = List.of(10, 20, 30);
List<Integer> destination = new ArrayList<>(3);
Collections.copy(destination, source);
~~~

**Câu hỏi:** Điều kiện nào chưa đạt? `ensureCapacity(100)` có sửa được điều đó không?

**Đáp án:** Đích có size 0, chưa có ba phần tử thật. Capacity và `ensureCapacity` không tăng size, nên không giải quyết điều kiện. Nếu cần copy vào các ô, tạo đủ phần tử trước. Nếu chỉ muốn list mới, dùng `new ArrayList<>(source)`.

### Bài 3 — Đủ ô nhưng không được ghi

Đích được tạo bằng `Collections.nCopies(3, 0)` và nguồn là `List.of(1, 2, 3)`.

**Câu hỏi:** Có phải đủ size là copy thành công không? Cách tạo nào cho một đích ba ô sửa được?

**Đáp án:** Không. List `nCopies` không hỗ trợ thay nên copy dữ liệu thực sự bị từ chối bằng `UnsupportedOperationException`. Có thể dùng `new ArrayList<>(Collections.nCopies(3, 0))` để vừa có ô vừa sửa được. Vẫn nên dùng constructor từ nguồn nếu mục tiêu chỉ là bản mới giống nguồn.

### Bài 4 — List dựa trên mảng

~~~java
String[] array = {"x", "y", "z"};
Collections.copy(Arrays.asList(array), List.of("A", "B"));
~~~

**Câu hỏi:** Mảng trở thành gì? Vì sao không cần list có khả năng thêm?

**Đáp án:** Mảng là `[A, B, z]`. View cho thay các ô sẵn có và ghi vào cùng mảng; không có bước làm tăng kích thước. Phần tử z giữ nguyên vì nguồn chỉ có hai phần tử.

### Bài 5 — Copy hay addAll?

Đích ban đầu `[old, tail]`, nguồn `[A, B]`.

**Câu hỏi:** So sánh kết quả `Collections.copy(dest, src)` với `dest.addAll(src)` khi thực hiện riêng từ cùng trạng thái ban đầu.

**Đáp án:** Copy cho `[A, B]` với size 2. AddAll cho `[old, tail, A, B]` với size 4. Một bên thay vị trí đã có; bên kia thêm vị trí mới. Không thể đổi qua lại mà không xét mục tiêu nghiệp vụ.

### Bài 6 — Chia sẻ phần tử

Nguồn có một Box với count 1. Copy vào list đích riêng rồi đổi `source.get(0).count` thành 5.

**Câu hỏi:** Đích thấy count nào? Nếu tiếp tục `dest.set(0, new Box("other", 9))` thì Box ở nguồn có tự thành other/9 không?

**Đáp án:** Đích ban đầu thấy count 5 vì giữ cùng Box. Thay ô đích bằng Box mới không thay tham chiếu ở ô nguồn độc lập; nguồn vẫn giữ Box cũ count 5. Muốn phần tử độc lập cần một chiến lược tạo/sao chép Box, không chỉ sao chép list.

### Bài 7 — Ghi đoạn giữa và chồng lấp

List là `[A, B, C, D, E]`. Cần đưa dữ liệu **ban đầu** ở các vị trí 0–2 sang 1–3.

**Câu hỏi:** Cách làm nào tránh nguồn đọc bị việc ghi đích làm đổi? Kết quả mong muốn là gì?

**Đáp án:** Tạo `new ArrayList<>(values.subList(0, 3))` trước, rồi copy từ bản ấy vào `values.subList(1, 4)`. Kết quả là `[A, A, B, C, E]`. Không giả định hai view chồng lấp tự có ngữ nghĩa chụp nguồn ban đầu.

### Bài 8 — Ba API đều có chữ “copy”

Nguồn list có phần tử `null` và một đối tượng sửa được khác. Xét `Collections.copy(dest, src)`, `new ArrayList<>(src)` và `List.copyOf(src)`.

**Câu hỏi:** API nào cần đích tồn tại? API nào từ chối `null`? Có API nào trong ba cách tự nhân bản sâu đối tượng sửa được không?

**Đáp án:** `Collections.copy` cần đích đủ size và cho thay; việc nhận `null` còn theo hợp đồng đích. Constructor `ArrayList` tạo list sửa được mới và có thể giữ `null`. `List.copyOf` từ chối nguồn có phần tử `null`. Không cách nào tự nhân bản sâu đối tượng sửa được; việc đó cần quy tắc riêng của mô hình.

Khi đọc `Collections.copy`, hãy nghĩ đến việc thay một đoạn ô đang tồn tại. Size, quyền `set`, phần đuôi và sự chia sẻ phần tử mới là những thông tin quyết định; chữ “copy” không tự mang theo lời hứa cấp phát list mới, đổi kích thước hay sao chép sâu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collections-utilities-007">addAll và chuyển các đối số thành phần tử collection</a>

<details>
<summary>Xem chi tiết</summary>

# addAll và chuyển các đối số thành phần tử collection

## 1. Từ nhiều đối số đến một collection đang có

Giả sử một danh sách đã có phần tử ban đầu và cần bổ sung ba tên. Có thể gọi `add` ba lần, hoặc dùng `Collections.addAll(destination, first, second, third)` để diễn đạt cùng nhu cầu thêm nhiều phần tử.

Phương thức này nhận **collection đích đã tồn tại**. Nó không tự tạo List mới, không xóa dữ liệu đích cũ và không trả về collection đã được điền. Những đối số sau đích là các phần tử được đề nghị thêm. Kết quả trả về là boolean cho biết collection đích có thay đổi do lời gọi hay không.

Cần tách hai vai trò:

- **Cách cung cấp dữ liệu:** truyền từng đối số hoặc một mảng phù hợp với tham số varargs.
- **Cách lưu dữ liệu:** List, Set hoặc loại Collection cụ thể quyết định quyền thêm, phần tử trùng, thứ tự và ràng buộc giá trị.

Vì vậy, cùng một dãy đối số có thể làm List tăng kích thước theo số lần xuất hiện nhưng chỉ bổ sung một số giá trị mới vào Set. `Collections.addAll` không biến mọi collection thành List và không ghi đè quy tắc của đích.

Từ “chuyển các đối số thành phần tử” cũng cần đọc đúng mức: phương thức thêm các giá trị hoặc tham chiếu đối số vào đích. Nó không tự trải đệ quy một cấu trúc lồng nhau và không tự sao chép sâu đối tượng phần tử.

Bài học đi từ chữ ký, phép thêm cơ bản và giá trị boolean đến varargs với mảng, null, quyền sửa và các lỗi thường gặp. Người học cần biết cách tạo ArrayList/Set, phương thức add và mảng Java. Mọi chương trình dưới đây là tệp độc lập, biên dịch và chạy bằng Java 17 theo lệnh ghi kèm.

## 2. Đọc chữ ký Collections.addAll

Chữ ký khái quát là:

`static <T> boolean addAll(Collection<? super T> destination, T... elements)`

| Thành phần | Ý nghĩa |
| --- | --- |
| `static` | Gọi qua lớp tiện ích Collections |
| `<T>` | Kiểu phần tử mà lời gọi đang thêm |
| `Collection<? super T>` | Đích có thể nhận T hoặc kiểu cha phù hợp của T |
| `T... elements` | Tham số số lượng biến đổi, còn gọi là varargs |
| `boolean` | Có thay đổi collection do lời gọi hay không |

Phần `? super T` giúp một đích có kiểu phần tử rộng hơn tiếp nhận các đối số có kiểu hẹp hơn. Ví dụ, `List<Number>` có thể nhận Integer, Long và Double vì chúng đều là Number. Không cần biến đích thành kiểu thô để thêm các giá trị phù hợp đó.

Kiểu rộng không có nghĩa mọi giá trị đều hợp lệ. `List<Integer>` không trở thành nơi chứa String chỉ vì phương thức là generic. Kiểm tra kiểu ở lúc biên dịch vẫn giúp ngăn việc thêm không tương thích; một collection cụ thể còn có thể có ràng buộc ở thời điểm chạy.

Varargs cho phép gọi với không phần tử, một phần tử hoặc nhiều phần tử. Khi truyền từng đối số, Java có cơ chế đóng gói chúng thành mảng cho tham số cuối. Khi đã có một mảng phù hợp, có thể truyền chính mảng ấy làm tham số. Điều quyết định các phần tử được thêm là **kiểu của lời gọi và hình dạng mảng**, không phải mong muốn tự động trải mọi dữ liệu.

Phương thức có từ Java 5. Trong bài dùng JDK 17 để các ví dụ có môi trường thống nhất. Hợp đồng gốc: [Collections.addAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#addAll(java.util.Collection,T...)).

## 3. Thí nghiệm thêm các đối số vào List

Lưu thành `AddAllBasicsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AddAllBasicsDemo {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>(List.of("seed"));
        boolean changed = Collections.addAll(names, "An", "Binh", "An");
        System.out.println("changed=" + changed);
        System.out.println("names=" + names);
        System.out.println("size=" + names.size());

        boolean emptyArguments = Collections.addAll(names);
        System.out.println("emptyArguments=" + emptyArguments);

        List<Number> numbers = new ArrayList<>();
        Collections.addAll(numbers, 1, 2L, 3.5);
        System.out.println("numbers=" + numbers);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 AddAllBasicsDemo.java
java AddAllBasicsDemo
```

Stdout:

```text
changed=true
names=[seed, An, Binh, An]
size=4
emptyArguments=false
numbers=[1, 2, 3.5]
```

List đã chứa seed trước lời gọi. Ba đối số được bổ sung vào ArrayList theo thứ tự đã truyền, gồm hai lần xuất hiện của An. Collection cũ không bị thay bằng một đối tượng mới và phần tử seed không bị xóa.

Biến `changed` là true vì List đã có thêm phần tử. Nó không mang giá trị bốn hay ba; số phần tử cần đọc bằng `size()` hoặc tính riêng theo mục tiêu của chương trình.

Ca không truyền thêm phần tử trả false trên List có thể sửa trong thí nghiệm: không có phần tử nào được bổ sung nên đích không thay đổi. Điều này khác với truyền một đối số null, vốn có thể là một phần tử thực nếu đích cho phép.

Các số `1`, `2L` và `3.5` được đưa vào `List<Number>`. Java có thể đóng gói các giá trị nguyên thủy đơn lẻ thành kiểu bao tương ứng khi lời gọi yêu cầu. Chuỗi in không có nghĩa mọi phần tử đã bị ép thành cùng kiểu số thực; đích Number có thể chứa nhiều kiểu số con.

Nếu cần dùng ngay dữ liệu đã thêm, đọc chính biến `names` hoặc `numbers`. Gán kết quả của Collections.addAll vào một biến List là sai ý nghĩa kiểu trả về.

## 4. Boolean và phần tử trùng phụ thuộc collection đích

Đối với List cho phép thêm, việc thêm lại một giá trị thường tạo thêm một lần xuất hiện. Đối với Set, một phần tử đã có có thể không làm thay đổi tập hợp. Vì vậy, true không có nghĩa “mọi đối số đều trở thành phần tử mới”, còn false không nhất thiết là lỗi.

Lưu thành `AddAllDestinationsDemo.java`:

```java
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class AddAllDestinationsDemo {
    public static void main(String[] args) {
        Set<String> ordered = new LinkedHashSet<>(List.of("A"));
        boolean first = Collections.addAll(ordered, "A", "B", "B");
        boolean repeated = Collections.addAll(ordered, "A", "B");
        System.out.println("first=" + first);
        System.out.println("repeated=" + repeated);
        System.out.println("ordered=" + ordered);
        System.out.println("size=" + ordered.size());

        Set<String> sorted = new TreeSet<>();
        Collections.addAll(sorted, "C", "A", "B");
        System.out.println("sorted=" + sorted);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 AddAllDestinationsDemo.java
java AddAllDestinationsDemo
```

Stdout:

```text
first=true
repeated=false
ordered=[A, B]
size=2
sorted=[A, B, C]
```

LinkedHashSet ban đầu đã có A. Lần thêm đầu nhận A, B, B: chỉ B tạo giá trị mới, nhưng cả lời gọi vẫn trả true vì tập hợp có thay đổi. Lần thêm tiếp chỉ nhận A và B vốn đã có nên trả false.

Kích thước Set bằng hai, không bằng tổng số đối số từng được truyền. Nếu cần lưu số lần một giá trị xuất hiện, Set không biểu diễn đủ dữ liệu ấy.

Thứ tự in của `ordered` đến từ hợp đồng LinkedHashSet giữ thứ tự chèn. Việc đề nghị thêm lại phần tử đã có không chuyển nó xuống cuối trong kiểu này. Kết quả `sorted` lại đến từ TreeSet sắp xếp theo thứ tự tự nhiên của String, dù lời gọi truyền C, A, B.

Không lấy thứ tự đối số làm lời hứa về thứ tự duyệt của mọi Collection. Chọn đích theo nhu cầu dữ liệu: List giữ chuỗi các lần xuất hiện; Set quản lý giá trị phân biệt; các loại Set cụ thể có hoặc không có cam kết thứ tự riêng.

Tài liệu đích dùng trong ví dụ: [LinkedHashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html) và [TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html). Với phần tử tự định nghĩa, cần giữ quy tắc bằng nhau hoặc so sánh ổn định theo hợp đồng của cấu trúc đã chọn.

## 5. Phân biệt Collections.addAll với collection.addAll

Hai cách viết cùng tên nhận hình dạng đầu vào khác nhau:

| Cách gọi | Đối tượng nhận dữ liệu | Dữ liệu được đưa vào | Giá trị trả về |
| --- | --- | --- | --- |
| `Collections.addAll(target, a, b, c)` | Tham số target | Các đối số phần tử hoặc mảng phù hợp | Boolean có thay đổi |
| `target.addAll(source)` | Chính target | Một Collection nguồn | Boolean có thay đổi |

Nếu dữ liệu đã là một Collection, phương thức của đích diễn đạt trực tiếp việc thêm các phần tử của nguồn. Nếu dữ liệu là vài giá trị rời hoặc một mảng tham chiếu phù hợp, tiện ích Collections thường giúp lời gọi ngắn gọn.

Đừng truyền một List làm đối số phần tử cho Collections.addAll rồi mặc định phương thức sẽ mở List đó ra. Nếu kiểu đích cho phép, List ấy có thể là **một phần tử lồng**. Tiện ích không coi mọi đối tượng triển khai Iterable là dãy cần tự trải.

Tương tự, Map không trực tiếp là Collection để dùng làm đích của chữ ký này. Cần đọc đúng kiểu dữ liệu và thao tác ánh xạ mà API đang yêu cầu, thay vì dựa trên từ “collection” theo nghĩa nói chung.

`Collection.addAll` không làm nguồn trở thành dữ liệu độc lập: các phần tử là đối tượng vẫn có thể được dùng chung tham chiếu. Cả hai cách thêm đều không là thao tác sao chép sâu chỉ vì có chữ All.

Nếu cân nhắc hiệu năng, không mặc định rằng cách viết nào luôn nhanh hơn trên mọi đích. Một số collection có cách thêm hàng loạt riêng; số phần tử và chi phí thêm/so sánh cũng ảnh hưởng. Chọn cách gọi đúng với hình dạng đầu vào trước, rồi đo khi thực sự có đường chạy cần tối ưu.

Xem chữ ký nhận Collection nguồn trong [Collection.addAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#addAll(java.util.Collection)).

## 6. Mảng truyền vào varargs: trải một tầng theo kiểu phù hợp

Một mảng String phù hợp với tham số `T...` khi T là String. Khi truyền mảng ấy, tiện ích lấy các phần tử String của mảng để thêm vào collection đích. Nó không thêm chính mảng như một phần tử String.

Nhưng nếu T là `String[]`, thì một phần tử là cả mảng String. Collection đích khi ấy có dạng `List<String[]>`; tham số varargs tương ứng ở một tầng mảng cao hơn. Phải đọc kiểu phần tử trước khi dự đoán số phần tử được thêm.

Lưu thành `ArrayVarargsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ArrayVarargsDemo {
    public static void main(String[] args) {
        String[] names = {"An", "Binh"};
        List<String> flat = new ArrayList<>();
        Collections.addAll(flat, names);

        List<String[]> groups = new ArrayList<>();
        Collections.<String[]>addAll(groups, names);
        System.out.println("flat=" + flat);
        System.out.println("groupCount=" + groups.size());
        System.out.println("sameArray=" + (groups.get(0) == names));
        System.out.println("group=" + Arrays.toString(groups.get(0)));

        names[0] = "Changed";
        System.out.println("flatAfterArrayEdit=" + flat);
        System.out.println("groupAfterArrayEdit=" + Arrays.toString(groups.get(0)));

        int[] raw = {1, 2, 3};
        List<int[]> batches = new ArrayList<>();
        Collections.addAll(batches, raw);
        System.out.println("batchCount=" + batches.size());
        System.out.println("batch=" + Arrays.toString(batches.get(0)));

        List<Integer> boxed = new ArrayList<>();
        for (int value : raw) {
            boxed.add(value);
        }
        System.out.println("boxed=" + boxed);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ArrayVarargsDemo.java
java ArrayVarargsDemo
```

Stdout:

```text
flat=[An, Binh]
groupCount=1
sameArray=true
group=[An, Binh]
flatAfterArrayEdit=[An, Binh]
groupAfterArrayEdit=[Changed, Binh]
batchCount=1
batch=[1, 2, 3]
boxed=[1, 2, 3]
```

Có ba hình dạng dữ liệu khác nhau trong chương trình:

| Đích | Đối số được thêm | Số phần tử mới trong ví dụ |
| --- | --- | --- |
| `List<String>` | Các String trong mảng names | Hai |
| `List<String[]>` | Cả mảng names như một phần tử | Một |
| `List<int[]>` | Cả mảng raw như một phần tử | Một |

Lời gọi `Collections.<String[]>addAll(groups, names)` ghi rõ kiểu T là `String[]`. Đó là cách biểu đạt chủ đích “collection chứa các mảng”. Nó không là thao tác chuyển một nhóm String thành một String duy nhất.

Dòng `sameArray=true` cho thấy phần tử trong groups là chính mảng names. Khi thay phần tử đầu của mảng, nội dung quan sát qua groups thay đổi. Còn flat vẫn giữ An và Binh vì nó đã nhận các tham chiếu String từ các ô mảng tại lúc thêm; thay tham chiếu trong ô mảng về sau không thay vị trí List đã có.

Sự độc lập của flat với việc gán lại ô mảng không chứng minh sao chép sâu phần tử. Nếu mảng chứa các đối tượng có thể sửa, List nhận các đối tượng ấy vẫn có thể quan sát thay đổi trạng thái của chúng.

### Vì sao int[] không tự thành nhiều Integer?

Một mảng `int[]` là một đối tượng mảng, nhưng nó không là `Integer[]`. Cơ chế đóng gói các giá trị int đơn lẻ không tự chuyển cả `int[]` thành mảng các Integer.

Trong chương trình, phần tử của batches có kiểu `int[]`, nên việc thêm một mảng raw làm kích thước batches bằng một. Để có `List<Integer>` chứa từng số, vòng lặp cuối lấy từng int và thêm giá trị ấy; tại từng lần thêm, Java mới áp dụng việc đóng gói phù hợp.

Không cố giải quyết bằng một phép ép kiểu mảng không hợp lệ. Quyết định muốn lưu **từng số** hay **từng mảng số** trước, rồi chọn kiểu collection và quá trình chuyển đổi tương ứng.

Quy tắc gọi phương thức có số lượng đối số biến đổi và việc chuẩn bị mảng tham số được mô tả trong [JLS 17, đánh giá đối số của lời gọi phương thức](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12.4.2).

## 7. Không có đối số, một phần tử null và mảng null

Ba tình huống này khác nhau:

- Không có phần tử để thêm: phần varargs biểu diễn một dãy rỗng.
- Có một đối số phần tử null: dãy có một vị trí, vị trí ấy chứa null.
- Mảng varargs là null: không có đối tượng mảng hợp lệ để lấy dãy phần tử.

Chính sách null còn phụ thuộc collection đích. ArrayList cho phép phần tử null; một số collection khác từ chối. Varargs không tự biến null thành chuỗi rỗng, không bỏ qua null và không thay quy tắc của đích.

Lưu thành `NullVarargsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NullVarargsDemo {
    public static void main(String[] args) {
        List<String> values = new ArrayList<>();
        boolean changed = Collections.addAll(values, (String) null);
        System.out.println("oneNullChanged=" + changed);
        System.out.println("values=" + values);
        System.out.println("size=" + values.size());

        try {
            Collections.addAll(values, (String[]) null);
        } catch (NullPointerException e) {
            System.out.println("nullArray=NullPointerException");
        }

        try {
            Collections.<String>addAll(null, "A");
        } catch (NullPointerException e) {
            System.out.println("nullDestination=NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 NullVarargsDemo.java
java NullVarargsDemo
```

Stdout:

```text
oneNullChanged=true
values=[null]
size=1
nullArray=NullPointerException
nullDestination=NullPointerException
```

Ép kiểu `(String) null` diễn đạt một phần tử String có giá trị null. Với ArrayList trong ví dụ, phần tử đó được thêm, kích thước bằng một và boolean là true.

Ép kiểu `(String[]) null` lại diễn đạt chính mảng phần tử là null. Lời gọi phát sinh ngoại lệ; đây không phải cách yêu cầu thêm một phần tử null.

Ca cuối kiểm tra đích null với một phần tử thực sự cần thêm. Một collection đích hợp lệ phải tồn tại trước khi dùng tiện ích. Không dùng những đường thực thi không có tác dụng để suy đoán rằng null là một đích được API hỗ trợ.

Tránh viết một đối số null không ghi rõ kiểu khi mục tiêu là phân biệt hai ý nghĩa trên. Trong ngữ cảnh varargs, null trần có thể được hiểu theo kiểu mảng phù hợp và dễ dẫn đến lời gọi khác với ý định “thêm một phần tử null”. Việc ghi kiểu rõ trong thí nghiệm giúp quan sát chính xác tình huống cần kiểm tra.

## 8. Đích phải hỗ trợ thêm và đáp ứng ràng buộc phần tử

Collections.addAll cần khả năng **thêm** vào collection đích. Một List có thể cho thay phần tử nhưng không cho tăng kích thước; điều đó không đáp ứng yêu cầu bổ sung phần tử mới.

List từ Arrays.asList có kích thước cố định theo mảng nền. List từ List.of không cho sửa phần chứa. Trong hai trường hợp, lời gọi cố thêm phần tử mới đều bị chặn, nhưng nguyên nhân khả năng của chúng không hoàn toàn giống nhau.

Ngoài quyền thêm, đích còn có thể từ chối null hoặc một giá trị không đáp ứng điều kiện của nó. Một lỗi trong lúc thêm hàng loạt không mang theo lời hứa rằng mọi phần tử trước đó sẽ được hoàn tác.

Lưu thành `AddAllLimitsDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class AddAllLimitsDemo {
    public static void main(String[] args) {
        List<String> fixed = Arrays.asList("base");
        try {
            Collections.addAll(fixed, "new");
        } catch (UnsupportedOperationException e) {
            System.out.println("fixed=UnsupportedOperationException");
        }

        List<String> readOnly = List.of("base");
        try {
            Collections.addAll(readOnly, "new");
        } catch (UnsupportedOperationException e) {
            System.out.println("readOnly=UnsupportedOperationException");
        }

        Deque<String> queue = new ArrayDeque<>();
        queue.add("seed");
        try {
            Collections.addAll(queue, "first", null, "last");
        } catch (NullPointerException e) {
            System.out.println("nullElement=NullPointerException");
        }
        System.out.println("queueAfterFailure=" + queue);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 AddAllLimitsDemo.java
java AddAllLimitsDemo
```

Stdout:

```text
fixed=UnsupportedOperationException
readOnly=UnsupportedOperationException
nullElement=NullPointerException
queueAfterFailure=[seed, first]
```

Hai ca đầu chủ động thêm một phần tử mới nên chứng minh trực tiếp việc đích không hỗ trợ thao tác cần dùng. Không nên thử lời gọi với không đối số rồi xem kết quả đó là bằng chứng rằng đích có thể thêm phần tử.

Ở ca ArrayDeque, dữ liệu ban đầu là seed. Trong lần chạy JDK 17 này, first đã được thêm trước khi việc gặp null gây ngoại lệ; last không được thêm. Collection sau lỗi còn `[seed, first]`.

Điều rút ra là không có cam kết hoàn tác toàn bộ cho lời gọi tiện ích. Không viết nghiệp vụ dựa vào giả định “có ngoại lệ thì đích chắc chắn như cũ”, cũng không phụ thuộc một tiền tố cập nhật cụ thể để điều khiển chương trình.

Nếu yêu cầu là tiếp nhận trọn một nhóm giá trị hoặc từ chối cả nhóm, cần một quy trình phù hợp: kiểm tra và chuẩn bị dữ liệu trước khi công bố, chọn đích đáp ứng mọi ràng buộc, và xử lý cập nhật theo hợp đồng của ứng dụng. Tạo một collection tạm chỉ giải quyết được phần chuẩn bị; nó không tự làm thao tác lên collection dùng chung thành một giao dịch.

ArrayDeque từ chối null theo [hợp đồng ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html). Khả năng cố định kích thước và liên hệ với mảng được mô tả tại [Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

## 9. Thêm vào đích hay tạo một collection từ đối số?

Khi bắt đầu với vài đối số, có nhiều cách diễn đạt nhưng chúng không có cùng vai trò.

| Nhu cầu | Công cụ thường phù hợp | Điều cần nhớ |
| --- | --- | --- |
| Bổ sung vào collection đang có | `Collections.addAll(target, ...)` | Thay đổi target; trả boolean |
| Bổ sung từ Collection nguồn | `target.addAll(source)` | Nguồn phải là Collection phù hợp |
| Lấy List theo các giá trị hoặc mảng tham chiếu | `Arrays.asList(...)` | Kích thước cố định; có quan hệ với mảng |
| Tạo List chỉ đọc từ các phần tử | `List.of(...)` | Không cho sửa phần chứa; không nhận null |
| Tạo ArrayList riêng từ một Collection | Constructor nhận Collection | Phần chứa có thể sửa; phần tử vẫn có thể dùng chung |

Nếu mục tiêu là tạo một List có thể tiếp tục thêm/xóa, có thể tạo ArrayList rồi dùng Collections.addAll để đưa những giá trị ban đầu vào. Đừng nhầm boolean của tiện ích với kết quả của một phương thức tạo List.

Nếu dùng Arrays.asList để chuẩn bị List, cần nhớ đó không phải một ArrayList độc lập có thể tăng kích thước. Việc tên lớp tiện ích gần nhau không làm hai hợp đồng giống nhau.

Nếu chọn List.of làm kết quả chỉ đọc, chính sách null cũng thay đổi so với một ArrayList nhận phần tử null. Chọn công cụ theo yêu cầu dữ liệu, không chỉ theo số dòng mã.

Với mảng nguyên thủy, vấn đề “mảng là một phần tử hay từng giá trị là phần tử” vẫn cần xét ở các API generic nhận varargs. Đổi từ Collections.addAll sang một phương thức khác có tên tạo List không tự chuyển `int[]` thành nhiều Integer.

Các đặc điểm của List chỉ đọc do factory cung cấp được mô tả trong [List: Unmodifiable Lists](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#unmodifiable).

## 10. Đọc lời gọi theo đích, kiểu phần tử và kết quả

Khi gặp một lời gọi Collections.addAll, có thể phân tích theo ba bước.

**Bước một: xác định đích.** Đích đã tồn tại chưa, có hỗ trợ add không, có chấp nhận null không và dùng quy tắc nào để nhận diện phần tử trùng? Chính đối tượng đích quyết định cách phần tử trở thành dữ liệu trong collection.

**Bước hai: xác định một phần tử có kiểu gì.** Trong `List<String>`, một phần tử là một chuỗi. Trong `List<String[]>`, một phần tử là cả một mảng chuỗi. Với một List chứa các List, truyền một List có thể là thêm một phần tử lồng; không có cơ chế tự trải mọi tầng.

**Bước ba: đọc boolean theo thay đổi của đích.** True nghĩa là có thay đổi, không cho biết đã thêm được bao nhiêu phần tử mới. Một vài đối số đã có trong Set không ngăn các đối số mới khác được thêm. Khi lời gọi hoàn tất bình thường, phương thức đã xử lý nhóm phần tử theo hợp đồng, không dùng lần thay đổi đầu tiên để bỏ qua phần còn lại.

Đối với dữ liệu lỗi, phải xét riêng việc lời gọi chưa hoàn tất. Ngoại lệ không mang một kết quả boolean hợp lệ về cho biến nhận, nhưng điều đó cũng không hứa rằng đích chưa bị thay đổi.

Cuối cùng, nhận biết quan hệ tham chiếu. Thêm một đối tượng có thể sửa vào collection không chuyển riêng toàn bộ trạng thái của đối tượng đó cho collection. Bên ngoài có thể vẫn giữ tham chiếu. Chương trình mảng lồng đã cho thấy cùng một mảng được quan sát qua cả biến nguồn và phần tử đích.

Nếu collection được dùng chung giữa nhiều luồng, tiện ích này không thay thế chính sách phối hợp truy cập của đích. Cần phân biệt việc một lệnh add riêng lẻ được một loại collection hỗ trợ với yêu cầu cập nhật cả nhóm như một bước nghiệp vụ thống nhất.

## 11. Lỗi phổ biến và cách xác định nguyên nhân

| Hiểu nhầm hoặc lỗi | Cách đọc đúng |
| --- | --- |
| Gán kết quả Collections.addAll cho một biến List | Kết quả là boolean; đọc collection đã truyền để lấy dữ liệu |
| Tưởng lời gọi xóa dữ liệu cũ rồi điền lại | Tiện ích bổ sung theo quyền add của đích |
| Mong số phần tử Set tăng đúng bằng số đối số | Đối số trùng có thể không tạo giá trị mới |
| Cho rằng false luôn báo thêm thất bại | Đích có thể không đổi vì mọi giá trị đã có hoặc không có phần tử để thêm |
| Truyền một List rồi mong tự trải thành nhiều phần tử | Dùng phương thức nhận Collection nguồn nếu mục tiêu là lấy các phần tử của List đó |
| Truyền int[] rồi mong nhiều Integer | Mảng nguyên thủy không tự chuyển thành mảng kiểu bao; cần chuyển từng giá trị |
| Dùng null mà không phân biệt phần tử và mảng | Ghi rõ kiểu và ý định; chính sách null còn phụ thuộc đích |
| Thêm vào Arrays.asList vì nó cho phép set | Quyền thay phần tử không đồng nghĩa quyền tăng kích thước |
| Nghĩ gặp ngoại lệ thì đích chắc chắn như cũ | Không có cam kết hoàn tác chung; cần quy trình tiếp nhận phù hợp |
| Nghĩ thêm đối tượng vào collection là sao chép sâu | Đích có thể giữ chính tham chiếu đối tượng đầu vào |

Khi kiểm tra một ca khó, đừng chỉ in collection. In thêm `size()`, kiểu phần tử đã khai báo, boolean trả về và nội dung mảng bằng `Arrays.toString` khi cần. Với collection chứa mảng, chuỗi in mặc định của mảng không diễn đạt các giá trị bên trong một cách hữu ích cho việc dự đoán.

Không thay đích bằng kiểu Object hay kiểu thô chỉ để làm một lời gọi sai ý nghĩa biên dịch được. Một lời gọi thêm cả mảng hoặc cả List như một phần tử có thể hợp lệ về kiểu nhưng vẫn không đúng yêu cầu cần từng phần tử nhỏ hơn. Kiểu dữ liệu và số phần tử mong muốn phải nhất quán.

## 12. Thực hành với kết quả dự đoán được

### Bài 1: Thêm ba đối số vào List có dữ liệu

Bắt đầu từ `[base]`, thêm A, B, A bằng tiện ích. Dự đoán nội dung, kích thước và boolean trước khi chạy.

Kết quả cần có `[base, A, B, A]`, kích thước bốn và true. Gọi tiếp với không phần tử trên đích ArrayList hợp lệ để kiểm tra trường hợp không thay đổi.

### Bài 2: Cùng đối số nhưng đích là Set

Bắt đầu từ LinkedHashSet chứa A. Thêm A, B, A rồi lặp lại cùng nhóm đối số.

Lần đầu có giá trị mới B nên trả true, lần sau không có giá trị mới nên trả false. Kích thước cuối vẫn bằng hai. Hãy giải thích vì sao số đối số không là số phần tử mới được bảo đảm.

### Bài 3: Chọn từng chuỗi hay cả mảng

Có một `String[]` gồm ba tên. Cần hai kết quả khác nhau: một `List<String>` có ba phần tử và một `List<String[]>` có một phần tử.

Hãy ghi kiểu rõ cho cả hai đích, dự đoán size rồi kiểm chứng. Thay một ô của mảng sau khi thêm để quan sát sự khác nhau giữa đã lấy các tham chiếu String và đang giữ chính mảng.

### Bài 4: Từ mảng nguyên thủy đến List số

Có `int[]` gồm ba số. Một yêu cầu cần lưu từng mảng số như một lô; yêu cầu khác cần `List<Integer>` chứa từng số.

Hãy chọn `List<int[]>` cho yêu cầu đầu và một bước lấy từng giá trị cho yêu cầu sau. Tiêu chí đạt: giải thích được vì sao lời gọi với mảng nguyên thủy không tự cung cấp ba đối số Integer.

### Bài 5: Null ở đúng tầng dữ liệu

Dùng Array`List<String>` làm đích. Kiểm tra một phần tử String null và một mảng String null bằng hai kiểu ép rõ ràng.

Trước khi chạy, dự đoán ca nào có một phần tử, ca nào không có mảng hợp lệ để duyệt. Sau đó đổi đích sang một cấu trúc từ chối null để nhận biết chính sách đích cũng là một phần của kết quả.

### Bài 6: Không giả định cập nhật toàn bộ

Dùng ArrayDeque và một nhóm có phần tử hợp lệ, null rồi một phần tử hợp lệ khác. Quan sát dữ liệu sau lỗi, rồi mô tả một quy trình tiếp nhận cần kiểm tra đầu vào trước khi công bố.

Tiêu chí đạt là nhận ra lời gọi tiện ích không hứa hoàn tác. Không dùng kết quả cập nhật một phần cụ thể như một quy tắc nghiệp vụ cần được giữ ở mọi đích hoặc mọi phiên bản.

### Tự kiểm tra

- **Collections.addAll có tạo đích không?** Không. Collection đích phải được cung cấp.
- **Giá trị trả về có là số phần tử thêm được không?** Không. Nó cho biết có thay đổi hay không khi lời gọi hoàn tất bình thường.
- **Varargs có tự trải mọi collection lồng không?** Không. Phải đọc kiểu phần tử và tham số mảng của lời gọi.
- **Một `int[]` có tự thành nhiều Integer không?** Không. Lấy từng giá trị nếu mục tiêu là `List<Integer>`.
- **Thêm một null và truyền mảng null có giống nhau không?** Không. Một bên là phần tử của dãy, bên kia là chính đối tượng mảng không tồn tại.
- **Điều gì quyết định thứ tự và phần tử trùng?** Hợp đồng của collection đích, không chỉ thứ tự viết các đối số.
- **Đã thêm tham chiếu thì phần tử có độc lập trạng thái với nguồn không?** Không mặc nhiên. Cần chính sách sao chép hoặc kiểu giá trị phù hợp nếu yêu cầu độc lập trạng thái.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collections-utilities-008">checkedCollection, checkedList, checkedSet và checkedMap</a>

<details>
<summary>Xem chi tiết</summary>

# checkedCollection, checkedList, checkedSet và checkedMap

## 1. Kiểm tra kiểu ở đúng nơi dữ liệu đi vào

Một biến khai báo là `List<String>` cho người đọc biết danh sách chứa chuỗi. Trong chương trình sử dụng generics đúng cách, trình biên dịch giúp duy trì điều đó: lời gọi thêm một `Integer` vào danh sách này không biên dịch được. Vấn đề xuất hiện khi danh sách đi qua mã cũ sử dụng kiểu thô (raw type), phép ép kiểu không được kiểm tra (unchecked cast) hoặc một thư viện không tuân thủ khai báo kiểu.

Giả sử một thư viện nhận danh sách tên người dùng dưới dạng `List` rồi thêm số `42`. `ArrayList` không tự ghi nhớ rằng tham số kiểu ban đầu là `String`. Lần ghi sai có thể hoàn tất; lỗi chỉ lộ ra khi một đoạn mã khác đọc phần tử và dùng nó như chuỗi. Dấu vết ngăn xếp khi ấy chỉ tới nơi đọc, khiến việc tìm nơi đã ghi sai khó hơn.

Các hàm `Collections.checkedCollection`, `checkedList`, `checkedSet` và `checkedMap` tạo một **khung nhìn kiểm tra kiểu lúc chạy** (dynamically typesafe view). Khi có dữ liệu không tương thích được đưa vào qua khung nhìn, thao tác bị chặn bằng `ClassCastException`. Mục đích thực tế là kéo lỗi về gần điểm ghi sai, nơi còn biết thành phần nào đã cung cấp dữ liệu.

Trong bài này:

- **Cấu trúc gốc** (backing collection/map) thực sự giữ các phần tử hoặc ánh xạ.
- **Lớp bọc** (wrapper) là đối tượng do hàm `checked...` trả về; nó chuyển tiếp thao tác tới cấu trúc gốc sau bước kiểm tra cần thiết.
- **Đối tượng mô tả kiểu lúc chạy** (type token) là đối tượng `Class` như `String.class` hoặc `Integer.class`.
- **Nhiễm kiểu** (heap pollution) là tình trạng một tham chiếu có kiểu tham số hóa không còn phù hợp với đối tượng/dữ liệu thực tế mà nó đại diện.

Luồng ghi cần hình dung:

```text
Mã gọi
  -> khung nhìn checked, giữ type token
  -> kiểm tra phần tử / khóa / giá trị sắp ghi
  -> thao tác trên cấu trúc gốc

Ghi thẳng vào cấu trúc gốc
  -> không đi qua bước kiểm tra của khung nhìn
```

Khung nhìn vẫn chia sẻ dữ liệu với cấu trúc gốc. Nó không tạo bản sao, không tự khóa truy cập và không biến các đối tượng phần tử thành bất biến. Vì vậy, bài toán ở đây là bảo vệ một ranh giới ghi dữ liệu trước sai lệch kiểu, gắn với cách quản lý các tham chiếu đang cùng trỏ vào cấu trúc đó. [API `checkedCollection`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#checkedCollection(java.util.Collection,java.lang.Class)) mô tả trực tiếp trường hợp tích hợp thư viện và truy tìm lỗi kiểu.

## 2. Chọn đúng hàm theo hợp đồng cần giữ

Các chữ ký dưới đây được rút gọn để đọc; đây là khai báo API, không phải chương trình chạy độc lập:

```java
static <E> Collection<E> checkedCollection(Collection<E> c, Class<E> type);
static <E> List<E> checkedList(List<E> list, Class<E> type);
static <E> Set<E> checkedSet(Set<E> s, Class<E> type);
static <K, V> Map<K, V> checkedMap(
        Map<K, V> map, Class<K> keyType, Class<V> valueType);
```

| Hàm | Kết quả nhìn từ mã gọi | Dữ liệu cần kiểm tra khi ghi | Khi nên chọn |
| --- | --- | --- | --- |
| `checkedCollection` | `Collection<E>` | Phần tử | Chỉ cần giao diện collection tổng quát |
| `checkedList` | `List<E>` | Phần tử thêm hoặc thay thế | Cần chỉ số, `set`, `ListIterator`, `subList` |
| `checkedSet` | `Set<E>` | Phần tử được đưa vào tập hợp | Cần giữ ý nghĩa tập hợp và hợp đồng bằng nhau của `Set` |
| `checkedMap` | `Map<K,V>` | Khóa và giá trị; cả giá trị thay thế | Cần giữ ánh xạ khóa–giá trị |

Hàm được chọn quyết định giao diện của lớp bọc. Bọc một `ArrayList` bằng `checkedCollection` không tạo ra một `List` chỉ vì cấu trúc gốc là list. Mã gọi không nên ép kết quả này về `List` để lấy `get` hoặc `set`. Nếu hợp đồng cần list, hãy tạo bằng `checkedList` ngay từ đầu.

Tương tự, gán kết quả của `checkedList` vào biến `Collection<String>` chỉ thu hẹp những phương thức có thể gọi qua biến ấy; đối tượng vẫn là một khung nhìn `List`. Hai tình huống “chọn hàm tạo lớp bọc tổng quát” và “khai báo biến bằng siêu giao diện” có tác động khác nhau.

### 2.1. Khác biệt về equals và hashCode

`Collection` không quy định một phép so sánh nội dung chung có thể áp dụng đồng thời cho list và set. `List` so sánh theo dãy phần tử; `Set` có hợp đồng của tập hợp. Do đó, `checkedCollection` dùng cách so sánh và mã băm theo `Object`, không chuyển tiếp `equals` và `hashCode` sang cấu trúc gốc.

Ví dụ, hai khung nhìn tổng quát chứa cùng dãy số không vì thế mà bằng nhau. Ngược lại, khung nhìn được tạo bằng `checkedList` vẫn phải đáp ứng hợp đồng bằng nhau của `List`, và `checkedSet` phải đáp ứng hợp đồng của `Set`. Chọn hàm bọc theo giao diện thực sự cần cung cấp; đừng dùng `checkedCollection` như một bản thay thế hoàn toàn tương đương của cả ba loại.

Đây cũng là lý do không nên suy luận một lớp bọc “chuyển tiếp tất cả mọi phương thức y hệt” chỉ từ việc `toString` của nó trông giống dữ liệu gốc.

## 3. Generics và Class phối hợp như thế nào?

Generics phát hiện lỗi dựa trên kiểu mà trình biên dịch nhìn thấy. Đối tượng mô tả kiểu cung cấp một phép kiểm tra bổ sung khi chương trình đang chạy. Hai lớp kiểm tra phục vụ những thời điểm khác nhau.

Đoạn minh họa sau cố ý có một dòng không biên dịch; không dùng nó như một tệp chạy độc lập:

```java
List<String> names =
        Collections.checkedList(new ArrayList<>(), String.class);

names.add("Mai");   // Hop le.
names.add(42);      // Loi bien dich: 42 khong phai String.
```

Trong mã thô, lời gọi thêm số có thể vượt qua kiểm tra tĩnh với cảnh báo. Lớp bọc vẫn giữ `String.class` nên kiểm tra lúc chạy tiếp tục có hiệu lực. Ép biến lớp bọc về kiểu thô không làm biến mất đối tượng lớp bọc.

### 3.1. “Đúng kiểu” là tương thích gán, không phải cùng một lớp cụ thể

Một `Collection<Number>` được bọc với `Number.class` có thể nhận `Integer`, `Double` và các kiểu con phù hợp khác. Một collection được bọc với `CharSequence.class` có thể nhận `String` hoặc `StringBuilder`.

Điều kiện không phải `value.getClass() == type`. Mô hình phù hợp hơn với giá trị khác `null` là phép thử `type.isInstance(value)`. [`Class.isInstance`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#isInstance(java.lang.Object)) mô tả việc chấp nhận kiểu con và kiểu triển khai giao diện.

Điều này giải thích hai kết quả khác nhau:

- `Number.class` chấp nhận đối tượng `Integer` mang giá trị `7`.
- `Integer.class` không chấp nhận đối tượng `Long` mang giá trị `7L`, dù hai số có cùng giá trị toán học.

Lớp bọc không phân tích chuỗi, không chuyển `Long` thành `Integer` và không kiểm tra số dương hay số âm. Việc chuyển đổi và quy tắc nghiệp vụ cần được xử lý ở tầng nhận dữ liệu.

### 3.2. Chọn Integer.class thay cho int.class

Collection chứa tham chiếu đến đối tượng. Với `List<Integer>`, hãy truyền `Integer.class`. Giá trị `7` trong lời gọi `add(7)` được đóng hộp thành `Integer` trước khi tới phương thức.

Một điểm dễ gây bất ngờ là biểu thức `int.class` vẫn có thể được truyền vào lời gọi suy luận `Class<Integer>`. Tuy nhiên, đối tượng lớp đó đại diện cho kiểu nguyên thủy `int`, không đại diện cho lớp `Integer`. `int.class.isInstance(...)` luôn trả về `false`; lớp bọc vì vậy từ chối đối tượng số đã đóng hộp. Chương trình thứ ba sẽ chứng minh tình huống này. Không dùng token nguyên thủy như một cách viết ngắn hơn của token lớp bao.

### 3.3. Null là một chính sách riêng

Một đối tượng `Class` hợp lệ không tự tạo ra quy tắc cấm `null`. Với cấu trúc gốc cho phép `null`, khung nhìn có kiểm tra kiểu thông thường vẫn cho phép phần tử `null`; map còn có thể cho phép khóa hoặc giá trị `null` theo chính sách của map gốc.

Do đó:

- `checkedList(new ArrayList<>(), String.class)` có thể nhận `null`.
- Khi bọc một cấu trúc gốc vốn cấm `null`, lớp bọc không gỡ bỏ lệnh cấm đó.
- Truyền chính đối số collection/map là `null`, hoặc truyền đối tượng mô tả kiểu là `null`, gây `NullPointerException` khi tạo lớp bọc.
- Với `Map.merge`, đối số giá trị đưa vào còn chịu hợp đồng riêng của `merge`; map cho phép lưu `null` không có nghĩa mọi phương thức nhận đối số `null` theo cùng cách.

Không thể suy ra “danh sách này không có giá trị rỗng” từ tên `checkedList`. Nếu miền nghiệp vụ cấm `null`, cần xác thực ràng buộc đó khi nhận dữ liệu.

## 4. Các đường ghi phải cùng đi qua lớp bọc

Với một `List`, ghi dữ liệu không chỉ có `add`. Còn có `set`, `add(index, value)`, `addAll`, `ListIterator.add`, `ListIterator.set` và việc sửa qua `subList`. Một lớp bọc kiểm tra đúng hợp đồng phải giữ ranh giới kiểu qua các đường này.

Với một `Set`, phần tử vẫn phải qua kiểm tra kiểu trước khi có thể được chấp nhận vào tập hợp. Quy tắc phần tử trùng lặp tiếp tục thuộc về tập hợp. `checkedSet` không biến kết quả `add` thành “luôn thêm thành công” và không tạo thứ tự duyệt mới.

Với một `Map`, một ánh xạ mới cần cả khóa lẫn giá trị phù hợp. Cập nhật giá trị của khóa đã có cũng phải được kiểm tra. Đặc biệt, `Map.Entry.setValue` trên entry lấy từ `checkedMap.entrySet()` không được trở thành lối đi vòng qua kiểm tra.

Các phương thức nhận một hàm gọi lại (callback) cũng có thể tạo ra dữ liệu ghi mới. Ví dụ:

| Thao tác | Kết quả cần quan tâm |
| --- | --- |
| `List.replaceAll` | Phần tử do hàm biến đổi trả về |
| `Map.compute`, `computeIfAbsent`, `computeIfPresent` | Giá trị tính ra nếu thao tác sẽ ghi ánh xạ |
| `Map.merge` | Giá trị đầu vào và giá trị hợp nhất sẽ được lưu |
| `Map.replaceAll` | Giá trị thay thế do hàm gọi lại trả về |
| `Map.Entry.setValue` | Giá trị mới đi vào entry |

Với hàm gọi lại có khai báo kiểu đúng, trình biên dịch đã giúp ngăn nhiều sai lệch. Nếu mã thô làm hàm gọi lại trả về đối tượng sai kiểu, lớp bọc vẫn phải chặn dữ liệu ấy khi nó sắp được ghi. Điều này không có nghĩa lớp bọc kiểm soát mọi tác dụng phụ của hàm gọi lại: hàm gọi lại có thể đã ghi log, sửa một đối tượng khác hoặc thực hiện một hành động bên ngoài trước khi kết quả bị từ chối.

Đừng nhầm ràng buộc kiểu với quyền sửa. Bọc một list không cho phép sửa bằng `checkedList` không làm nó hỗ trợ `add`. Một thao tác đúng kiểu vẫn có thể thất bại vì `UnsupportedOperationException`, chỉ số không hợp lệ, giới hạn của cấu trúc gốc hoặc quy tắc so sánh của nó. Khi một lời gọi vi phạm nhiều điều kiện, không xây dựng luồng điều khiển dựa trên giả định thứ tự ưu tiên giữa các ngoại lệ.

[Hợp đồng `List`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html) và [`Map.Entry`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html) giúp phân biệt thao tác cập nhật, thao tác tùy chọn và đối tượng entry gắn với map.

## 5. Chương trình 1: Collection, List và Set

Chương trình này dùng dữ liệu có thứ tự xác định để đầu ra dễ đối chiếu. `LinkedHashSet` được chọn để giữ thứ tự chèn; đây là đặc tính của cấu trúc gốc được chọn, không phải tính năng tạo thứ tự của `checkedSet`.

Các biến kiểu thô và `@SuppressWarnings` chỉ phục vụ thí nghiệm mô phỏng mã cũ. Chương trình vẫn dừng bằng `AssertionError` nếu một trường hợp dự kiến bị chặn lại được chấp nhận.

Tệp `CheckedViewsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public class CheckedViewsDemo {
    private static void rejected(String label, Runnable action) {
        try {
            action.run();
        } catch (ClassCastException expected) {
            System.out.println(label + "=ClassCastException");
            return;
        }
        throw new AssertionError(label + " was accepted");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("first"));
        List<String> checked = Collections.checkedList(source, String.class);
        List rawList = checked;
        rejected("list.add", () -> rawList.add(42));
        rejected("list.set", () -> rawList.set(0, 42));

        ListIterator rawIterator = checked.listIterator();
        rawIterator.next();
        rejected("iterator.set", () -> rawIterator.set(42));
        rejected("iterator.add", () -> rawIterator.add(42));

        List rawSubList = checked.subList(0, 1);
        rejected("subList.add", () -> rawSubList.add(42));
        checked.set(0, "updated");
        checked.add(null);
        source.add("owner");
        System.out.println("list=" + checked);
        System.out.println("listEquals=" +
                checked.equals(java.util.Arrays.asList("updated", null, "owner")));

        Collection<Number> numbers = Collections.checkedCollection(
                new ArrayList<>(), Number.class);
        numbers.add(7);
        numbers.add(2.5);
        Collection rawNumbers = numbers;
        rejected("collection.add", () -> rawNumbers.add("7"));
        System.out.println("numbers=" + numbers);
        System.out.println("genericCollectionEqualsList=" +
                numbers.equals(List.of(7, 2.5)));

        Set<String> tags = Collections.checkedSet(
                new LinkedHashSet<>(), String.class);
        System.out.println("set.addFirst=" + tags.add("java"));
        System.out.println("set.addDuplicate=" + tags.add("java"));
        Set rawSet = tags;
        rejected("set.add", () -> rawSet.add(42));
        System.out.println("set=" + tags);
        System.out.println("setEquals=" + tags.equals(Set.of("java")));

        Collection<CharSequence> texts = Collections.checkedCollection(
                new ArrayList<>(), CharSequence.class);
        StringBuilder builder = new StringBuilder("a");
        texts.add(builder);
        builder.append("b");
        System.out.println("elementCanChange=" + texts);
    }
}
```

Biên dịch và chạy tại thư mục chứa tệp, bằng JDK hỗ trợ Java 17 trở lên:

```shell
javac --release 17 -encoding UTF-8 CheckedViewsDemo.java
java CheckedViewsDemo
```

Đầu ra:

```text
list.add=ClassCastException
list.set=ClassCastException
iterator.set=ClassCastException
iterator.add=ClassCastException
subList.add=ClassCastException
list=[updated, null, owner]
listEquals=true
collection.add=ClassCastException
numbers=[7, 2.5]
genericCollectionEqualsList=false
set.addFirst=true
set.addDuplicate=false
set.add=ClassCastException
set=[java]
setEquals=true
elementCanChange=[ab]
```

### 5.1. Đọc kết quả theo từng lớp trách nhiệm

Năm lỗi đầu tiên cùng bảo vệ một `List<String>`: lời gọi qua biến thô, qua iterator và qua danh sách con đều không đưa số vào dữ liệu gốc. Sau các lần bị chặn, list vẫn cho phép sửa hợp lệ. Việc `source.add("owner")` xuất hiện trong `checked` cho thấy hai tham chiếu cùng quan sát một dữ liệu sống.

`null` được thêm thành công vì `ArrayList` cho phép nó. Đây không phải một lỗ hổng kiểm tra kiểu: thiếu giá trị và sai kiểu là hai điều kiện cần được định nghĩa riêng.

`numbers` nhận cả `Integer` và `Double` nhờ token `Number.class`, rồi từ chối chuỗi `"7"`. Kết quả `genericCollectionEqualsList=false` minh họa sự khác biệt giữa lớp bọc tổng quát và một list có hợp đồng so sánh theo nội dung.

Set vẫn trả về `false` khi thêm lại `"java"`. Ràng buộc không trùng lặp của set vẫn hoạt động bên cạnh kiểm tra kiểu.

Cuối cùng, `StringBuilder` được chấp nhận qua token giao diện `CharSequence.class`. Sau khi đối tượng ấy bị sửa từ `"a"` thành `"ab"`, nội dung nhìn thấy qua collection cũng thay đổi. Lớp bọc kiểm tra loại đối tượng được thêm vào; nó không đóng băng trạng thái của đối tượng đó.

## 6. Chương trình 2: Bảo vệ cả khóa, giá trị và Entry của Map

Một map tên bài học và số lượng ví dụ cần khóa `String`, giá trị `Integer`. Chỉ kiểm tra `put` trực tiếp sẽ chưa đủ nếu bên nhận còn lấy được entry và gọi `setValue` hoặc sử dụng hàm gọi lại để thay giá trị.

Tệp `CheckedMapDemo.java`:

```java
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class CheckedMapDemo {
    private static void rejected(String label, Runnable action) {
        try {
            action.run();
        } catch (ClassCastException expected) {
            System.out.println(label + "=ClassCastException");
            return;
        }
        throw new AssertionError(label + " was accepted");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void main(String[] args) {
        Map<String, Integer> source = new LinkedHashMap<>();
        Map<String, Integer> checked = Collections.checkedMap(
                source, String.class, Integer.class);
        checked.put("a", 1);
        Map rawMap = checked;

        rejected("put.key", () -> rawMap.put(42, 2));
        rejected("put.value", () -> rawMap.put("b", "two"));
        rejected("replace.value", () -> rawMap.replace("a", "two"));

        Map.Entry rawEntry = checked.entrySet().iterator().next();
        rejected("entry.setValue", () -> rawEntry.setValue("two"));
        rawEntry.setValue(3);
        System.out.println("afterEntry=" + checked);

        rejected("compute.result", () ->
                rawMap.compute("b", (key, value) -> "two"));
        rejected("merge.result", () ->
                rawMap.merge("a", 4, (oldValue, newValue) -> "two"));
        rejected("replaceAll.result", () ->
                rawMap.replaceAll((key, value) -> "two"));
        System.out.println("afterRejected=" + checked);
        System.out.println("missingBadKey=" + checked.get(42));

        checked.compute("a", (key, value) -> value + 1);
        checked.merge("a", 4, Integer::sum);
        checked.put(null, null);
        source.put("owner", 9);
        System.out.println("finalMap=" + checked);

        checked.values().remove(9);
        System.out.println("afterViewRemoval=" + source);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 CheckedMapDemo.java
java CheckedMapDemo
```

Đầu ra:

```text
put.key=ClassCastException
put.value=ClassCastException
replace.value=ClassCastException
entry.setValue=ClassCastException
afterEntry={a=3}
compute.result=ClassCastException
merge.result=ClassCastException
replaceAll.result=ClassCastException
afterRejected={a=3}
missingBadKey=null
finalMap={a=8, null=null, owner=9}
afterViewRemoval={a=8, null=null}
```

### 6.1. Vì sao kết quả có a=8?

Ánh xạ ban đầu là `a=1`. Lời gọi `rawEntry.setValue(3)` hợp lệ nên dữ liệu trở thành `a=3`. Các hàm gọi lại cố trả về chuỗi đều bị chặn; trong những trường hợp cụ thể này, map không nhận thêm hay thay bằng giá trị sai.

Sau đó `compute` tăng `3` thành `4`, và `merge` cộng thêm `4` để được `8`. Kiểm tra kiểu không làm thay đổi ý nghĩa tính toán của hàm gọi lại hợp lệ.

`checked.put(null, null)` được phép vì `LinkedHashMap` gốc cho phép cả hai. `source.put("owner", 9)` xuất hiện ngay trong khung nhìn, chứng minh đây không phải bản chụp dữ liệu. Khi xóa `9` qua `checked.values()`, ánh xạ tương ứng cũng biến mất khỏi map gốc.

### 6.2. Entry phải được lấy từ đúng map

`rawEntry` trong thí nghiệm được lấy qua `checked.entrySet()`. Nó vẫn bảo vệ giá trị khi `setValue` được gọi qua kiểu thô.

Nếu ứng dụng giữ một entry lấy trực tiếp từ `source.entrySet()` rồi trao entry ấy ra ngoài, entry đó không tự được thay thế bằng entry của lớp bọc. Nó là một đường truy cập khác tới dữ liệu gốc. Bọc map sau khi đã làm lộ tham chiếu không thu hồi được những tham chiếu đã phát ra.

Chỉ sử dụng entry trong điều kiện mà hợp đồng `Map.Entry` cho phép. Không giữ một entry qua các lần sửa cấu trúc map rồi mặc nhiên cho rằng nó tiếp tục là một “cửa cập nhật” hợp lệ mãi mãi.

### 6.3. Truy vấn sai kiểu không đồng nghĩa với ghi sai kiểu

`checked.get(42)` biên dịch được vì tham số của `Map.get` là `Object`. Trong thí nghiệm với `LinkedHashMap`, nó trả về `null` vì không có khóa ấy. Không có ánh xạ sai kiểu nào được tạo ra.

Không suy ra mọi checked map đều phải ném `ClassCastException` cho mọi truy vấn sai kiểu, hoặc mọi cấu trúc gốc đều phải trả `null`. Hợp đồng truy vấn và giới hạn kiểu của cấu trúc gốc vẫn có vai trò. Ranh giới đang bảo vệ là dữ liệu được lưu, không phải một cơ chế cưỡng chế chữ ký `get(K)` lúc chạy.

[API `Map`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html) quy định riêng các thao tác truy vấn, view và cập nhật bằng hàm gọi lại; cần đọc cùng chính sách của map gốc.

## 7. Hai điều kiện để giữ dữ liệu đúng kiểu lâu dài

Muốn kết luận “collection không chứa phần tử sai kiểu”, chỉ có lớp bọc là chưa đủ. Cần duy trì cả hai điều kiện:

1. Dữ liệu đã đúng kiểu trước khi tạo khung nhìn.
2. Mọi đường có thể đưa dữ liệu mới hoặc giá trị thay thế vào cấu trúc đều đi qua khung nhìn được kiểm tra phù hợp.

Với map, điều kiện thứ nhất áp dụng cho cả khóa và giá trị. Điều kiện thứ hai bao gồm những collection view và entry được lấy từ map bọc.

### 7.1. Tạo lớp bọc không phải là kiểm tra toàn bộ dữ liệu cũ

Một danh sách đã chứa số nhưng bị khai báo/ép thành `List<String>` không tự được làm sạch khi truyền vào `checkedList`. Lớp bọc được tạo thành công không phải giấy xác nhận từng phần tử hiện hữu đã qua kiểm tra.

Lúc đọc, việc dùng kết quả như `String` có thể làm lộ dữ liệu sai. Cần phân biệt nơi kiểm tra khi ghi của lớp bọc với phép ép kiểu mà trình biên dịch chèn ở nơi đọc. Một giá trị đọc ra như `Object` có thể chưa gặp lỗi; yêu cầu dùng nó như `String` mới làm `ClassCastException` xuất hiện.

Vì thế, “đã bọc mà vẫn lỗi ở `get`” chưa chứng minh lớp bọc không hoạt động. Có thể dữ liệu sai đã có từ trước, hoặc đi vào qua một tham chiếu khác.

### 7.2. Một tham chiếu đến cấu trúc gốc vẫn có thể đi vòng

Nếu cùng một `ArrayList` có hai tham chiếu, trong đó một tham chiếu trỏ tới lớp bọc và một tham chiếu trỏ thẳng tới list gốc, lời gọi qua list gốc không đi qua lớp bọc. Kiểu khai báo của tham chiếu gốc cũng có thể bị mã thô làm suy yếu.

Một thiết kế ranh giới rõ hơn là tạo cấu trúc gốc rỗng ngay trong thành phần sở hữu, bọc nó rồi chỉ cung cấp tham chiếu đã bọc. Đối tượng gốc không bị “khóa” bằng một tính năng JVM đặc biệt; cách thiết kế đang giảm số đường truy cập có thể bỏ qua kiểm tra.

Việc sửa dữ liệu hợp lệ qua tham chiếu gốc trong hai chương trình đầu chỉ nhằm chứng minh khung nhìn chia sẻ dữ liệu. Trong một tích hợp cần bảo đảm kiểu, chính đường ghi ngoài lớp bọc đó phải được quản lý, không thể xem như đương nhiên an toàn.

### 7.3. Kiểm tra kiểu bên ngoài không xác nhận kiểu lồng bên trong

`List.class` có thể giúp xác nhận một phần tử là `List`. Nó không cung cấp thông tin “mọi phần tử trong list ấy đều là `String`”. Java không có biểu thức lớp `List<String>.class`.

Đây là hệ quả cần hiểu khi làm việc với xóa kiểu (type erasure): nhiều tham số kiểu chỉ phục vụ kiểm tra khi biên dịch. Một `List<Integer>` và một `List<String>` đều có thể được biểu diễn bằng đối tượng list lúc chạy; một token `Class` cho `List` không mang toàn bộ cấu trúc kiểu lồng.

Ví dụ, nếu bọc một danh sách ngoài với `List.class`, đưa `ArrayList<Integer>` vào vẫn qua được kiểm tra lớp bên ngoài. Một phép ép kiểu tiếp theo không biến số trong danh sách con thành chuỗi. Khi đọc số ấy như `String`, lỗi lại xuất hiện.

Muốn bảo vệ cấu trúc lồng, cần xác thực từng tầng theo cấu trúc dữ liệu thực tế, kiểm soát các tham chiếu tới từng collection con hoặc chuyển dữ liệu sang mô hình miền có ràng buộc rõ ràng. Ép một `Class` thô thành `Class<List<String>>` bằng unchecked cast không bổ sung năng lực kiểm tra sâu mà token không có.

[Tài liệu về xóa kiểu của Java](https://docs.oracle.com/javase/tutorial/java/generics/erasure.html) giải thích vì sao tham số kiểu không tự được giữ như một token lớp đầy đủ để lớp bọc sử dụng.

## 8. Chương trình 3: Dữ liệu cũ, đường đi vòng và kiểu lồng

Chương trình sau cố tình vi phạm giả định của generics ở các điểm được cô lập trong `main`. Đây là thí nghiệm chẩn đoán các giới hạn, không phải mẫu để tiếp nhận dữ liệu không tin cậy trong mã ứng dụng.

Tệp `CheckedLimitationsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CheckedLimitationsDemo {
    private static void rejected(String label, Runnable action) {
        try {
            action.run();
        } catch (ClassCastException expected) {
            System.out.println(label + "=ClassCastException");
            return;
        }
        throw new AssertionError(label + " was accepted");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void main(String[] args) {
        List dirty = new ArrayList();
        dirty.add(42);
        List<String> claimedStrings = dirty;
        List<String> wrappedDirty =
                Collections.checkedList(claimedStrings, String.class);
        Object oldValue = ((List) wrappedDirty).get(0);
        System.out.println("existingType=" + oldValue.getClass().getSimpleName());
        rejected("existingTypedRead", () -> wrappedDirty.get(0).length());

        List<String> source = new ArrayList<>();
        List<String> checked = Collections.checkedList(source, String.class);
        List rawSource = source;
        rawSource.add(99);
        System.out.println("bypassedSize=" + checked.size());
        rejected("bypassedTypedRead", () -> checked.get(0).length());

        List<List> lists = Collections.checkedList(
                new ArrayList<>(), List.class);
        lists.add(new ArrayList<>(List.of(123)));
        System.out.println("nestedType=" +
                lists.get(0).get(0).getClass().getSimpleName());
        List<String> assumedInner = lists.get(0);
        rejected("nestedTypedRead", () -> assumedInner.get(0).length());

        List<Integer> goodIntegers = Collections.checkedList(
                new ArrayList<>(), Integer.class);
        goodIntegers.add(7);
        System.out.println("boxedToken=" + goodIntegers);

        List<Integer> primitiveToken = Collections.checkedList(
                new ArrayList<>(), int.class);
        rejected("primitiveToken", () -> primitiveToken.add(7));

        List<String> partial = Collections.checkedList(
                new ArrayList<>(List.of("a", "b")), String.class);
        List rawPartial = partial;
        rejected("replaceAll.partial", () ->
                rawPartial.replaceAll(value -> value.equals("a") ? "A" : 42));
        System.out.println("afterPartial=" + partial);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 CheckedLimitationsDemo.java
java CheckedLimitationsDemo
```

Đầu ra:

```text
existingType=Integer
existingTypedRead=ClassCastException
bypassedSize=1
bypassedTypedRead=ClassCastException
nestedType=Integer
nestedTypedRead=ClassCastException
boxedToken=[7]
primitiveToken=ClassCastException
replaceAll.partial=ClassCastException
afterPartial=[A, b]
```

### 8.1. Ba nguồn lỗi khác nhau có thể cùng lộ khi đọc

`existingType=Integer` xuất hiện vì danh sách đã sai từ trước khi bọc. Tạo lớp bọc không quét lại và từ chối dữ liệu ấy.

`bypassedSize=1` xuất hiện vì thao tác ghi đi thẳng vào `source`. Lớp bọc nhìn thấy phần tử mới vì nó chia sẻ dữ liệu, nhưng đã không có cơ hội kiểm tra lúc ghi.

`nestedType=Integer` xuất hiện vì token `List.class` chỉ kiểm tra list con là một list. Các phần tử bên trong list con chưa có ràng buộc chuỗi ở thời điểm thêm list đó vào danh sách ngoài.

Cả ba trường hợp đều có thể dẫn đến lỗi ở một biểu thức đọc chuỗi. Cách khắc phục phụ thuộc vào nguồn gốc: xác thực dữ liệu ban đầu, thu hẹp đường ghi ngoài lớp bọc hoặc kiểm tra cấu trúc lồng. Thêm một lớp ép kiểu để hết cảnh báo không giải quyết được bất kỳ nguồn lỗi nào trong ba trường hợp.

### 8.2. Token và tự động đóng hộp không phải cùng một cơ chế

`boxedToken=[7]` cho thấy `Integer.class` phù hợp với đối tượng số mà list thực sự giữ. `primitiveToken=ClassCastException` cho thấy đối số `int.class` không tự được đổi thành token của lớp bao dù lời gọi `add(7)` tự đóng hộp giá trị.

Sự phân biệt này hữu ích khi một hàm tiện ích nhận `Class<E>` từ thành phần khác. Cần xác định hợp đồng token đầu vào; đừng chỉ kiểm tra rằng biến token khác `null` rồi mặc nhiên coi mọi lớp nguyên thủy là tương đương lớp bao.

### 8.3. Kiểm tra kiểu không bao hàm hoàn tác

Trong phần cuối, `replaceAll` biến `"a"` thành `"A"` đúng kiểu. Khi hàm gọi lại tới `"b"`, nó trả về số `42` và bị từ chối. Kết quả `[A, b]` giữ lại phần thay đổi đã thực hiện trước lỗi.

Đây là một minh chứng cụ thể với `ArrayList` được bọc và cách triển khai đã chạy. Bảo đảm “không lưu giá trị sai kiểu qua lớp bọc” không kéo theo “mọi thao tác nhiều phần tử đều hoàn tác nếu có ngoại lệ”.

Trong mã nguồn OpenJDK 17, các đường `addAll` và `putAll` chuẩn bị/kiểm tra dữ liệu đầu vào trước bước ghi; cách đó giúp tránh việc vừa thêm dần vừa mới phát hiện kiểu sai trong lô. Đây là chi tiết triển khai cần phân biệt với một giao dịch tổng quát: giới hạn của cấu trúc gốc, ngoại lệ khác, hàm gọi lại và truy cập đồng thời không vì thế có cơ chế hoàn tác chung.

[Mã nguồn `Collections` của OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/Collections.java) thể hiện rõ bước kiểm tra đầu vào và việc kiểm tra kết quả hàm gọi lại. Không phụ thuộc vào tên lớp lớp bọc nội bộ hoặc thông điệp ngoại lệ cụ thể để viết mã ứng dụng.

## 9. Kiểm tra dữ liệu đầu vào và kiểm tra khi ghi là hai giai đoạn

Giả sử cần nhận một danh sách từ một thư viện cũ rồi cho các thành phần khác tiếp tục cập nhật. Có hai câu hỏi khác nhau:

- Các phần tử đang có đã đúng dạng dữ liệu mong muốn chưa?
- Những cập nhật tiếp theo có đi qua một ranh giới kiểm tra được kiểm soát không?

Chỉ gọi `checkedList` trên danh sách vừa nhận mới trả lời được một phần câu hỏi thứ hai. Một quy trình dễ kiểm soát hơn là:

1. Nhận đầu vào qua kiểu có thể mô tả sự chưa biết, chẳng hạn `Collection<?>`, thay vì giả vờ nó đã là `Collection<String>`.
2. Duyệt và kiểm tra từng phần tử theo kiểu, chính sách `null` và ràng buộc nghiệp vụ đã xác định.
3. Xây dựng cấu trúc mới do thành phần hiện tại sở hữu; việc kiểm tra và tạo kết quả diễn ra trước khi công bố kết quả ấy ra ngoài.
4. Tạo khung nhìn có kiểm tra kiểu phù hợp nếu còn cần cho phép cập nhật từ các ranh giới dễ mất thông tin kiểu.
5. Không trao thêm cấu trúc gốc, iterator hoặc entry có khả năng ghi ngoài ranh giới đã chọn.

Nếu gặp một giá trị sai, quyết định từ chối toàn bộ đầu vào hay báo lỗi từng mục phải thuộc hợp đồng của ứng dụng. Lớp bọc không tự đưa ra chính sách chuyển `"42"` thành số, bỏ các phần tử sai hoặc thay `null` bằng giá trị mặc định.

Một bản sao collection thông thường như `new ArrayList<>(input)` chỉ sao chép các tham chiếu phần tử. Nó không tự trở thành quá trình xác minh sâu toàn bộ cấu trúc kiểu hoặc bản sao sâu trạng thái các đối tượng. Nếu đầu vào có thể thay đổi đồng thời, quy trình nhận dữ liệu còn cần một chiến lược nhất quán phù hợp; lớp bọc không tạo ảnh chụp nguyên tử của dữ liệu đang bị sửa.

### 9.1. Sửa chỗ dùng raw type vẫn là hướng chính

Nếu toàn bộ mã nguồn nằm trong quyền kiểm soát và có thể khai báo generics đúng, hãy sửa chữ ký kiểu và giải quyết cảnh báo không được kiểm tra tại nguyên nhân. Đặt khung nhìn có kiểm tra kiểu ở một ranh giới cần phòng vệ hoặc dùng tạm thời để chẩn đoán có giá trị; rải unchecked cast kèm `@SuppressWarnings` rồi trông chờ lớp bọc xử lý mọi hậu quả sẽ làm mất nhiều thông tin tĩnh hữu ích.

Một khung nhìn có kiểm tra kiểu cũng không chứng minh một hàm nhận collection sử dụng đúng mọi quy tắc nghiệp vụ. Ví dụ, danh sách `String` vẫn có thể nhận chuỗi rỗng, tên trùng hoặc một tên không tồn tại. Các quy tắc ấy không được biểu diễn bởi `String.class`.

## 10. Phân biệt với các loại lớp bọc và bản sao khác

| Cách tạo/cung cấp dữ liệu | Ràng buộc chính | Có giữ liên hệ dữ liệu với cấu trúc gốc không? | Có tự kiểm tra sâu dữ liệu lồng không? |
| --- | --- | --- | --- |
| `checkedList` trên list cho phép sửa | Kiểu của phần tử ghi qua view | Có | Không |
| `unmodifiableList` | Không cho phép sửa cấu trúc qua view | Có | Không |
| `synchronizedList` | Đồng bộ truy cập theo hợp đồng lớp bọc | Có | Không |
| `new ArrayList<>(source)` | Tạo một list khác chứa các tham chiếu phần tử | Không chia sẻ cấu trúc list | Không |
| `List.copyOf(source)` | Tạo kết quả không cho sửa, cấm phần tử `null` | Không phản ánh các lần sửa cấu trúc nguồn về sau | Không |

Bảng chỉ so sánh trách nhiệm trực tiếp liên quan tới bài. Không suy ra một cơ chế thay thế được các cơ chế còn lại.

Ví dụ, một API cho thư viện cũ bổ sung tên có thể cần khung nhìn có kiểm tra kiểu. Một API chỉ để đọc cần xem xét quyền sửa dữ liệu. Một API chia sẻ giữa nhiều luồng cần xem xét cách đồng bộ và tính nguyên tử của chuỗi thao tác. Cả ba yêu cầu có thể xuất hiện trong cùng hệ thống nhưng cần được thiết kế riêng.

Nếu kết hợp nhiều lớp bọc, phải xác định đối tượng nào được trao ra ngoài và những tham chiếu khác còn tồn tại. Một lớp `unmodifiable` bên ngoài khung nhìn có kiểm tra kiểu sẽ chặn các lần sửa qua lớp ngoài; nó không tạo ra cơ hội để bên nhận ghi dữ liệu hợp lệ. Một lớp checked bên ngoài list không cho phép sửa cũng không trao thêm quyền sửa mà list gốc thiếu.

Không cần dựa vào tên lớp thực thi nội bộ, thứ tự của các lớp bọc trong thông điệp lỗi hay kiểm tra `getClass().getName()` để mô tả hợp đồng API công khai.

### 10.1. Chú ý với phần tử của Set và khóa của Map

Một phần tử có thể vẫn thuộc đúng lớp nhưng bị sửa các trường ảnh hưởng đến `equals` hoặc `hashCode`. Khung nhìn có kiểm tra kiểu không ngăn việc đó. Nếu đối tượng đang là phần tử của hash set hoặc khóa của hash map, việc sửa như vậy có thể làm hỏng giả định tra cứu.

Do đó, “mọi khóa đều thuộc lớp `CustomerKey`” và “các khóa có trạng thái phù hợp với hợp đồng map trong suốt thời gian lưu” là hai bất biến khác nhau. Đối tượng mô tả kiểu chỉ giúp với loại đối tượng; thiết kế khóa và quyền sửa trạng thái của khóa vẫn thuộc về ứng dụng. [Hợp đồng `Set`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html) và [`Map`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html) cảnh báo trực tiếp về các đối tượng có trạng thái thay đổi ảnh hưởng đến phép so sánh.

## 11. Chi phí và cách dùng để truy tìm lỗi

Với cấu trúc gốc rỗng do ứng dụng sở hữu, có thể tạo lớp bọc ngay khi khởi tạo, rồi đưa lớp bọc qua ranh giới thư viện. Mỗi lần có dữ liệu cần ghi, ứng dụng trả thêm chi phí cho việc kiểm tra tương thích kiểu và chuyển tiếp lời gọi. Lớp bọc không thay đổi nền tảng lưu trữ để biến một thuật toán tuyến tính thành truy cập hằng số.

Đừng suy luận các thao tác lô luôn chỉ cần bộ nhớ phụ hằng số. Như quan sát ở OpenJDK 17, việc chuẩn bị bản dữ liệu đầu vào đã kiểm tra cho `addAll` hoặc `putAll` có thể cần bộ nhớ phụ theo số phần tử đầu vào. Chi phí thật cần xét theo thao tác đang dùng và cấu trúc gốc; không có một con số tăng thời gian cố định đúng cho mọi khung nhìn có kiểm tra kiểu.

Một quy trình chẩn đoán hữu ích khi đang gặp `ClassCastException` ở nơi đọc:

1. Xác định collection/map chứa dữ liệu sai và loại phần tử, khóa hoặc giá trị mong muốn.
2. Kiểm tra xem dữ liệu đã sai trước khi lớp bọc có hiệu lực hay chưa.
3. Tạo khung nhìn có kiểm tra kiểu tại điểm sở hữu hoặc điểm trao collection cho thành phần nghi ngờ.
4. Đảm bảo lần tái hiện thực sự sử dụng lớp bọc; kiểm tra cả tham chiếu gốc và view/entry từng bị phát ra.
5. Tái hiện và đọc dấu vết ngăn xếp của lần ghi bị chặn.
6. Sửa chữ ký generics hoặc nơi tạo dữ liệu sai, rồi giữ hay bỏ lớp bọc tùy vai trò phòng vệ của ranh giới.

Bắt `ClassCastException` rồi tiếp tục im lặng thường chỉ che mất dữ liệu không hợp lệ. Nếu ứng dụng cần chấp nhận một tập đầu vào hỗn hợp, hãy mô tả kiểu hỗn hợp một cách có chủ ý, chẳng hạn mô hình miền hoặc quy trình chuyển đổi rõ ràng, thay vì gắn nhãn sai `List<String>`.

## 12. Tự thực hành và kiểm tra hiểu biết

### Bài 1: Đổi đối tượng mô tả kiểu

Trong chương trình thứ nhất, thay collection số bằng một collection được kiểm tra với `Integer.class`, rồi thử thêm `7`, `7L`, `2.5` và `"7"`.

**Điểm cần giải thích:** số nguyên `7` được đóng hộp thành `Integer` nên phù hợp; `Long`, `Double` và `String` không trở thành `Integer` chỉ vì có thể diễn giải thành số. Nếu vẫn khai báo `Collection<Number>` nhưng truyền trực tiếp `Integer.class`, cần xem lại chữ ký `Class<E>`: mối quan hệ kiểu tại thời điểm biên dịch cũng phải nhất quán. Hãy khai báo `Collection<Integer>` cho thí nghiệm token này.

### Bài 2: Tìm đường truy cập không được bảo vệ

Một thành phần giữ `Map<String,Integer> source`, cung cấp `checkedMap(source, String.class, Integer.class)` cho thư viện A, nhưng cung cấp `source.entrySet()` cho thư viện B.

**Điểm cần giải thích:** entry lấy trực tiếp từ map gốc là một đường cập nhật khác. Nếu B dùng mã thô để sửa giá trị của entry, khung nhìn có kiểm tra kiểu mà A nhận không can thiệp vào lời gọi đó. Cần quản lý toàn bộ các tham chiếu có thể ghi, không chỉ biến map được trả về từ một phương thức.

### Bài 3: Tạo lớp bọc sau khi nhiễm kiểu

Dùng kiểu thô để thêm số vào `ArrayList`, gán nó thành `List<String>`, rồi mới gọi `checkedList`. So sánh việc đọc như `Object`, gọi `size` và dùng phần tử như `String`.

**Điểm cần giải thích:** `size` và đọc như `Object` không tự xác nhận mọi phần tử là chuỗi. Lỗi lộ ở phép sử dụng yêu cầu `String`. Cách sửa là kiểm tra dữ liệu ban đầu hoặc sửa đường ghi gốc, không phải bọc lại nhiều lần.

### Bài 4: Phân biệt kiểu ngoài và kiểu trong

Bọc danh sách ngoài bằng `List.class`, thêm một list chứa số, rồi đọc số đó như chuỗi qua một phép gán không được kiểm tra.

**Điểm cần giải thích:** danh sách con thỏa kiểm tra lớp ngoài. Đối tượng mô tả kiểu không mô tả tham số kiểu của danh sách con. Muốn đảm bảo sâu phải có một bước xác thực cấu trúc sâu và kiểm soát các lần sửa tiếp theo ở tầng đó.

### Bài 5: Kiểm tra tác động khi hàm gọi lại thất bại

Chạy lại đoạn `replaceAll` cuối chương trình thứ ba, rồi viết một quy trình chỉ công bố danh sách mới sau khi toàn bộ kết quả biến đổi đã được kiểm tra.

**Điểm cần giải thích:** lớp bọc giữ ràng buộc kiểu của từng giá trị được ghi; nó không cung cấp hoàn tác chung. Xây dựng kết quả riêng rồi công bố sau thành công là một lựa chọn thiết kế khác, cần định nghĩa cả quyền sửa và chia sẻ tham chiếu của các phần tử.

Khi có thể tự giải thích tại sao một lời ghi bị chặn, tại sao một lời ghi khác đi vòng được và tại sao dữ liệu lồng vẫn có thể sai, người học đã nắm được ranh giới mà bốn hàm `checked...` thực sự bảo vệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collections-utilities-009">asLifoQueue, newSetFromMap và điều kiện của cấu trúc nền</a>

<details>
<summary>Xem chi tiết</summary>

# asLifoQueue, newSetFromMap và điều kiện của cấu trúc nền

Một cấu trúc dữ liệu đôi khi đã có đúng cách lưu trữ cần dùng, nhưng giao diện phía gọi lại khác. Một thuật toán nhận `Queue` có thể cần lấy phần tử mới nhất trước; một bài toán nhận `Set` có thể cần so thành viên theo định danh đối tượng hoặc dùng khóa yếu.

`Collections.asLifoQueue` và `Collections.newSetFromMap` tạo những giao diện thích nghi dựa trên cấu trúc nền. Chúng không đọc toàn bộ dữ liệu rồi tạo một bản sao độc lập. Hành vi của kết quả phụ thuộc cách chuyển thao tác và hợp đồng của deque/map được truyền vào.

Bài này dùng Java 17. Trước khi chạy mã, cần biết `Queue`, `Deque`, `Map`, `Set` và tham chiếu. Hai trọng tâm là vị trí thêm/lấy của LIFO và điều kiện sở hữu map khi tạo set.

## 1. Hai bộ thích nghi giải quyết hai bài toán khác nhau

| Phương thức | Đầu vào | Kết quả | Mục tiêu |
| --- | --- | --- | --- |
| `Collections.asLifoQueue(deque)` | `Deque<T>` | `Queue<T>` | Dùng các thao tác queue nhưng lấy phần tử theo LIFO |
| `Collections.newSetFromMap(map)` | `Map<E, Boolean>` | `Set<E>` | Dùng khóa map làm nơi lưu thành viên của set |

LIFO là “vào sau, ra trước” (last in, first out). Nếu thêm A, rồi B, rồi C qua giao diện LIFO, phần tử được lấy trước là C.

Set từ map dùng khóa để đại diện thành viên. Kiểu giá trị `Boolean` là phần phục vụ biểu diễn của bộ thích nghi; người dùng làm việc với `Set<E>` thay vì tự quản lý các cặp khóa–giá trị.

Điểm chung là có **cấu trúc nền đang được dùng thật**. Không có lời hứa tự làm bất biến phần tử, thay chính sách `null` hoặc thêm khả năng đồng thời cho một cấu trúc vốn không có.

Điểm khác quan trọng: deque của `asLifoQueue` **không bắt buộc rỗng**. Map của `newSetFromMap` **bắt buộc rỗng lúc tạo**, và sau đó không nên được truy cập trực tiếp nữa.

## 2. Vì sao một `Queue` có thể có thứ tự LIFO?

`Queue` mô tả các thao tác thêm, lấy và xem phần tử đầu sẽ được xử lý. Nó không buộc mọi cách thực hiện đều là FIFO. Hàng đợi ưu tiên và hàng đợi LIFO là các ví dụ có thứ tự khác. [Hợp đồng Queue, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html).

Nếu chỉ truyền một `ArrayDeque` trực tiếp dưới kiểu `Queue`, các thao tác queue thông thường thêm ở cuối và lấy ở đầu, tạo FIFO. Ép kiểu giao diện không thay vị trí thêm.

`asLifoQueue` đổi cách ánh xạ thao tác: thêm qua queue vào **đầu deque**, và lấy cũng ở **đầu deque**. Đó là lý do phần tử vừa thêm được lấy trước.

~~~text
offer(A): đầu [A] cuối
offer(B): đầu [B, A] cuối
offer(C): đầu [C, B, A] cuối
poll():   trả C, còn [B, A]
~~~

Không có bước đảo toàn deque mỗi lần thêm. Quan hệ LIFO đến từ việc dùng cùng một đầu cho thêm và lấy.

### Chuyển đúng từng nhóm thao tác

| Thao tác trên queue thích nghi | Thao tác theo đầu deque | Ý nghĩa khi không có phần tử/chỗ trống |
| --- | --- | --- |
| `add(e)` | `addFirst(e)`, tương ứng push | Thất bại thêm do đầy: ngoại lệ |
| `offer(e)` | `offerFirst(e)` | Có thể trả `false` nếu đầy |
| `remove()` | `removeFirst()`, tương ứng pop | Rỗng: `NoSuchElementException` |
| `poll()` | `pollFirst()` | Rỗng: `null` |
| `element()` | `getFirst()` | Rỗng: `NoSuchElementException` |
| `peek()` | `peekFirst()` | Rỗng: `null` |

Các phương thức queue có hai nhóm xử lý thất bại. Chọn nhóm theo hợp đồng mong muốn; đừng gọi `remove()` khi thật sự muốn thăm dò không có dữ liệu bằng `poll()`.

Bảng đang nói `remove()` **không đối số**. `remove(value)` là thao tác tìm và xóa một phần tử phù hợp, không đồng nghĩa pop phần tử đầu. [Các thao tác hai đầu của Deque, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html).

## 3. `asLifoQueue` là view sống trên deque

### Không tạo một ngăn xếp độc lập

Mã minh họa trong một phương thức:

~~~java
Deque<String> storage = new ArrayDeque<>();
Queue<String> pending = Collections.asLifoQueue(storage);

pending.add("A");
pending.add("B");
~~~

Cả hai giao diện đều dùng cùng dữ liệu. Duyệt deque từ đầu tới cuối sẽ thấy B, A. Lấy qua queue cũng lấy từ cấu trúc ấy.

Deque có thể đã có phần tử trước khi tạo view. Factory không tự đảo những phần tử hiện có để đoán lịch sử thêm của chúng. Nó cung cấp quy tắc cho những thao tác qua view kể từ lúc dùng.

Ví dụ deque đang là `[old-first, old-last]`, thêm `"new"` qua queue cho `[new, old-first, old-last]`. Phần tử new được ưu tiên lấy vì được đặt ở đầu, còn quan hệ của hai phần tử cũ không bị đảo.

### Giữ cả hai tham chiếu thì phải kiểm soát hai đường truy cập

Sửa trực tiếp deque sẽ được nhìn thấy qua queue. Đó là bằng chứng về cùng nơi lưu trữ, nhưng trong mã ứng dụng phải tránh vô tình trộn các quy tắc thêm ở đầu/cuối.

Nếu một thành phần thêm qua `deque.addLast` còn thành phần khác thêm qua queue LIFO, câu chuyện “vào sau ra trước” chỉ có nghĩa theo các thao tác đã được quy định. Không thể bỏ qua đường thêm ở cuối rồi hứa một thứ tự toàn cục khác.

Một cách đóng gói đơn giản khi chỉ muốn công bố LIFO là không để lộ deque:

~~~java
Queue<String> pending =
        Collections.asLifoQueue(new ArrayDeque<String>());
~~~

Người gọi chỉ có giao diện queue đã thích nghi. Nếu toàn bộ mã đã dùng `Deque` trực tiếp và muốn push/pop, dùng chính `Deque` thường đủ; không cần thêm một bộ thích nghi chỉ để viết tên hàm khác.

### Duyệt không có nghĩa là lấy phần tử ra

Iterator của view dùng thứ tự duyệt deque từ đầu tới cuối. Với dữ liệu đã thêm A, B, C qua LIFO, một bản `new ArrayList<>(queue)` biểu diễn C, B, A mà không tự pop các phần tử.

Ngược lại, lặp `poll` là thao tác lấy ra thật. Phải phân biệt việc đọc để hiển thị với việc tiêu thụ công việc.

## 4. `addAll` và giới hạn của cấu trúc nền

### Thêm cả nhóm đảo thứ tự nhóm ở đầu

`asLifoQueue` thực hiện `addAll` bằng một chuỗi lời gọi `addFirst` trên deque, theo các phần tử được đọc từ đầu vào. [API asLifoQueue, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#asLifoQueue(java.util.Deque)).

Với queue đang rỗng:

~~~java
pending.addAll(List.of("A", "B", "C"));
~~~

Sau đó thứ tự đầu tới cuối là `[C, B, A]`. Nếu queue đã có dữ liệu, nhóm ấy nằm phía trước dữ liệu cũ theo cùng quy tắc.

Đừng đánh đồng thao tác này với `deque.addAll` thông thường thêm ở đuôi. Một lần gọi có cùng tên nhưng đối tượng nhận khác có thể dùng chính sách đặt vị trí khác.

### Bounded deque giữ giới hạn của nó

Dùng deque giới hạn hai phần tử:

~~~java
Queue<String> pending =
        Collections.asLifoQueue(new LinkedBlockingDeque<String>(2));
~~~

Thêm A rồi B bằng `offer` thành công. `offer("C")` trả `false` khi đầy; `add("C")` ném `IllegalStateException` trong tình huống ấy.

Bộ thích nghi không cấp phát vùng bổ sung để né giới hạn và cũng không tự đợi cho có chỗ. Kiểu kết quả `Queue` không cung cấp các thao tác chặn như `put` hoặc `take` dù cấu trúc nền có một giao diện chặn riêng.

`LinkedBlockingDeque` có thể có giới hạn dung lượng, còn `ArrayDeque` không có giới hạn cố định kiểu đó và tự tăng khi cần. Chính sách này đến từ loại deque được chọn. [LinkedBlockingDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingDeque.html), [ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html).

### Bulk operation không tự là một giao dịch

Nếu queue giới hạn hai phần tử đang rỗng rồi `addAll(A, B, C)`, hai lần thêm đầu có thể hoàn thành trước khi lần thứ ba thất bại. Ví dụ chạy bên dưới xác nhận trạng thái còn B, A sau ngoại lệ.

Không suy từ “một lời gọi Java” thành “hoặc thêm hết, hoặc không thay gì”. Nếu nghiệp vụ cần cả nhóm được chấp nhận như một đơn vị, phải thiết kế cơ chế khác phù hợp.

### Chính sách `null` không bị xóa bỏ

`ArrayDeque` không cho phần tử `null` nên queue LIFO dựa trên nó cũng từ chối `null`. Một deque khác có thể có chính sách khác, nhưng `null` làm kết quả thăm dò rỗng của `poll`/`peek` dễ nhập nhằng.

Nên dùng phần tử có ý nghĩa rõ với giao thức queue đang chọn. Không coi view là cách hợp thức hóa một phần tử mà cấu trúc nền không nhận.

## 5. `newSetFromMap`: map trống được giao cho set quản lý

Chữ ký:

~~~java
public static <E> Set<E> newSetFromMap(Map<E, Boolean> map)
~~~

Đây là khai báo API để đọc kiểu, không phải phần cài đặt cần chép vào chương trình.

Các khóa map đại diện thành viên set. Thao tác `set.add(e)` được thực hiện thông qua thao tác ghi trên map; các thao tác thành viên/xóa/duyệt đi theo map hoặc góc nhìn khóa của nó.

### Map phải thực sự không có ánh xạ

Một map có khóa `"already"` với giá trị `Boolean.FALSE` vẫn là **map không rỗng**. False không có nghĩa “khóa không tồn tại”. Truyền map ấy vào factory ném `IllegalArgumentException`.

Factory không nhận một map đang có dữ liệu để “chuyển hết khóa sang set”. Nếu chỉ cần một bản tập khóa của map hiện hữu, đó là nhu cầu khác, thường liên quan tới `keySet` hoặc sao chép các khóa theo hợp đồng cần thiết.

### Sau khi tạo, không truy cập trực tiếp map nền

Tài liệu yêu cầu map rỗng lúc gọi và khuyến cáo không truy cập map trực tiếp sau khi factory trả về. Cách dùng đúng và dễ duy trì là tạo map ngay trong lời gọi và không giữ tham chiếu ngoài:

~~~java
Set<Object> visited = Collections.newSetFromMap(
        new IdentityHashMap<Object, Boolean>());
~~~

Mọi thao tác thành viên thực hiện qua `visited`. Không giữ một alias của map, một `keySet` cũ hoặc một view có thể thay dữ liệu ở đường khác.

Điều này khác ví dụ quan sát deque qua hai giao diện ở phần LIFO. Với set từ map, **không dùng đọc/ghi trực tiếp map nền sau tạo để làm thí nghiệm**; chương trình dưới đây chỉ thao tác trên set khi đã tạo thành công.

Quy tắc sở hữu ngăn việc bên ngoài tự đặt một giá trị `Boolean` khác hoặc sửa ánh xạ, làm hỏng những giả định mà thao tác set dựa vào. [API newSetFromMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#newSetFromMap(java.util.Map)).

### Rỗng là điều kiện tạo, không bảo đảm mọi thao tác đều hỗ trợ

Một map rỗng nhưng không sửa được có thể đáp ứng việc kiểm tra rỗng. Set thích nghi từ nó vẫn không thể `add` nếu map không hỗ trợ ghi.

Đừng dùng việc factory tạo xong làm bằng chứng rằng map nền hỗ trợ tất cả thao tác mong muốn. Khi ứng dụng cần thêm/xóa thành viên, hãy chọn một map có quyền cập nhật phù hợp ngay từ đầu.

## 6. Các đặc tính set thừa hưởng từ map

### Thứ tự duyệt

Với `LinkedHashMap` theo thứ tự chèn, set thích nghi duyệt theo thứ tự khóa được chèn. Thêm lại một thành viên đã có không tạo một vị trí mới; xóa rồi thêm lại đưa nó vào cuối thứ tự chèn.

Ví dụ này hữu ích để kiểm chứng cơ chế, nhưng nhu cầu set thường theo thứ tự chèn đã có `LinkedHashSet` diễn đạt trực tiếp. Không cần dùng factory cho mọi map vốn đã có một kiểu set tương ứng quen thuộc. [LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html), [LinkedHashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html).

Không hứa thứ tự chèn cho một set dựa trên `HashMap`, `IdentityHashMap` hoặc `ConcurrentHashMap` chỉ vì code có các lần add theo một thứ tự cụ thể.

### Định danh tham chiếu

`IdentityHashMap` xem hai khóa là cùng nhau khi chúng là cùng tham chiếu, tức `==`. Vì vậy hai `new String("item")` khác nhau có thể cùng là hai thành viên trong set dù `equals` của chúng trả `true`.

Một đối tượng mới khác tham chiếu nhưng bằng nội dung không tự tìm thấy thành viên cũ. Quy tắc này có ích khi theo dõi chính các nút đối tượng đã thăm, không muốn gộp hai nút chỉ vì chúng bằng nhau về dữ liệu. [IdentityHashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html).

Đây là ngữ nghĩa chuyên biệt. Không dùng một set theo định danh như tập giá trị thông thường rồi kỳ vọng mọi phép so sánh set, phép tập hợp hoặc tiện ích kết hợp với một set dựa trên `equals` đều có ý nghĩa thông thường.

Trong thí nghiệm, dùng `new String` để bảo đảm có các tham chiếu khác nhau. Hai string literal giống nhau không là một cách kiểm tra thích hợp vì chúng có thể được dùng chung.

### Khóa yếu

Có thể tạo set dựa trên `WeakHashMap`:

~~~java
Set<Object> tracked = Collections.newSetFromMap(
        new WeakHashMap<Object, Boolean>());
~~~

Đây là mảnh mã minh họa cần import `Set`, `Collections` và `WeakHashMap`. Nó không tạo một tập giữ mạnh mọi đối tượng thành viên.

Khi không còn tham chiếu mạnh phù hợp tới một khóa, việc dọn rác có thể làm thành viên biến mất. Không dùng set khóa yếu làm sổ lưu bắt buộc phải giữ mọi mục cho tới khi gọi remove.

Không viết stdout khẳng định “gọi `System.gc()` xong size chắc chắn bằng 0”. Thời điểm thu gom và việc giữ khóa còn sống không do một dòng yêu cầu GC quyết định. Chương trình chính không dùng một kết quả GC để làm bằng chứng cố định. [WeakHashMap, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html).

### Đồng thời và chính sách `null`

Set dựa trên `ConcurrentHashMap` thừa hưởng khả năng thao tác đồng thời của map, bao gồm việc không nhận khóa `null`. Set không tự thêm khóa ngoài hay biến các thao tác nhiều bước thành một giao dịch.

Đối với nhu cầu set đồng thời thông thường, `ConcurrentHashMap.newKeySet()` là API trực tiếp có sẵn trong Java 17:

~~~java
Set<String> active = ConcurrentHashMap.newKeySet();
~~~

`newSetFromMap` vẫn hữu ích để hiểu và dùng các map nền phù hợp khác. Chọn API thể hiện rõ mục đích, không dùng một lớp thích nghi chỉ để tăng số lớp. [ConcurrentHashMap và newKeySet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#newKeySet()).

Một set theo map thông thường nhận `null` có thể nhận `null`; một map không nhận khóa `null` sẽ truyền giới hạn ấy sang set. Giao diện `Set<E>` không tự xác định chung một chính sách `null` cho mọi cách tạo.

## 7. Chương trình hoàn chỉnh

Lưu toàn bộ mã vào **`BackedAdaptersDemo.java`**. Các map dùng tạo set thành công đều được tạo trực tiếp trong factory, không được giữ alias để đọc hoặc sửa sau đó.

Map không rỗng được tạo riêng chỉ để kiểm tra trường hợp bị từ chối. Các ví dụ set đồng thời chạy trong một luồng nhằm kiểm tra thao tác thêm/trùng/`null`; bảo đảm đồng thời đến từ hợp đồng lớp nền, không được suy ra chỉ từ một lần chạy đơn luồng.

~~~java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class BackedAdaptersDemo {
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
        Deque<String> backing = new ArrayDeque<>();
        Queue<String> lifo = Collections.asLifoQueue(backing);
        lifo.offer("A");
        lifo.offer("B");
        lifo.add("C");
        System.out.println("lifo.view=" + new ArrayList<>(lifo));
        System.out.println("lifo.backing=" + new ArrayList<>(backing));
        System.out.println("lifo.peek=" + lifo.peek());
        System.out.println("lifo.remove=" + lifo.remove());
        System.out.println("lifo.poll=" + lifo.poll());
        System.out.println("lifo.element=" + lifo.element());
        System.out.println("lifo.lastPoll=" + lifo.poll());
        System.out.println("lifo.emptyPoll=" + lifo.poll());
        System.out.println("lifo.emptyPeek=" + lifo.peek());
        rejects("lifo.emptyRemove", NoSuchElementException.class,
                lifo::remove);
        rejects("lifo.null", NullPointerException.class,
                () -> lifo.offer(null));

        Deque<String> existing =
                new ArrayDeque<>(List.of("old-first", "old-last"));
        Queue<String> adapted = Collections.asLifoQueue(existing);
        System.out.println("existing.before=" + new ArrayList<>(adapted));
        adapted.add("new");
        System.out.println("existing.afterAdd=" + new ArrayList<>(adapted));
        System.out.println("existing.removeLast=" + existing.removeLast());
        System.out.println("existing.afterBackingEdit="
                + new ArrayList<>(adapted));

        Queue<String> bulk = Collections.asLifoQueue(new ArrayDeque<>());
        bulk.addAll(List.of("A", "B", "C"));
        System.out.println("bulk.view=" + new ArrayList<>(bulk));

        Queue<String> bounded =
                Collections.asLifoQueue(new LinkedBlockingDeque<>(2));
        System.out.println("bounded.offers="
                + bounded.offer("A") + "/"
                + bounded.offer("B") + "/"
                + bounded.offer("C"));
        System.out.println("bounded.peek=" + bounded.peek());
        rejects("bounded.addFull", IllegalStateException.class,
                () -> bounded.add("C"));
        System.out.println("bounded.polls="
                + bounded.poll() + "/" + bounded.poll());

        Queue<String> partial =
                Collections.asLifoQueue(new LinkedBlockingDeque<>(2));
        rejects("partial.addAll", IllegalStateException.class,
                () -> partial.addAll(List.of("A", "B", "C")));
        System.out.println("partial.afterFailure=" + new ArrayList<>(partial));

        Set<String> ordered = Collections.newSetFromMap(
                new LinkedHashMap<String, Boolean>());
        System.out.println("ordered.firstAdd=" + ordered.add("beta"));
        ordered.add("alpha");
        System.out.println("ordered.equalAdd="
                + ordered.add(new String("beta")));
        System.out.println("ordered.values=" + new ArrayList<>(ordered));
        ordered.remove("beta");
        ordered.add("beta");
        System.out.println("ordered.reinserted=" + new ArrayList<>(ordered));
        ordered.add(null);
        System.out.println("ordered.null=" + ordered.contains(null));

        String first = new String("item");
        String second = new String("item");
        Set<String> identity = Collections.newSetFromMap(
                new IdentityHashMap<String, Boolean>());
        System.out.println("identity.equalValues=" + first.equals(second));
        System.out.println("identity.adds="
                + identity.add(first) + "/" + identity.add(second));
        System.out.println("identity.size=" + identity.size());
        System.out.println("identity.containsSame=" + identity.contains(first));
        System.out.println("identity.containsEqualOther="
                + identity.contains(new String("item")));
        System.out.println("identity.removeSame=" + identity.remove(first));
        System.out.println("identity.afterRemove.size=" + identity.size());

        Map<String, Boolean> nonempty = new HashMap<>();
        nonempty.put("already", Boolean.FALSE);
        rejects("nonempty.map", IllegalArgumentException.class,
                () -> Collections.newSetFromMap(nonempty));
        Set<String> readonlyBacking = Collections.newSetFromMap(
                Collections.<String, Boolean>emptyMap());
        rejects("readonly.mapAdd", UnsupportedOperationException.class,
                () -> readonlyBacking.add("x"));

        Set<String> concurrent = Collections.newSetFromMap(
                new ConcurrentHashMap<String, Boolean>());
        System.out.println("concurrent.adds="
                + concurrent.add("job") + "/" + concurrent.add("job"));
        System.out.println("concurrent.size=" + concurrent.size());
        rejects("concurrent.null", NullPointerException.class,
                () -> concurrent.add(null));
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 BackedAdaptersDemo.java
java BackedAdaptersDemo
~~~

Cần JDK 17 trở lên để dùng lệnh biên dịch này. Mã đã được biên dịch với `--release 17` và chạy trên OpenJDK 24.0.2, thoát thành công. Stdout chính xác:

~~~text
lifo.view=[C, B, A]
lifo.backing=[C, B, A]
lifo.peek=C
lifo.remove=C
lifo.poll=B
lifo.element=A
lifo.lastPoll=A
lifo.emptyPoll=null
lifo.emptyPeek=null
lifo.emptyRemove=NoSuchElementException
lifo.null=NullPointerException
existing.before=[old-first, old-last]
existing.afterAdd=[new, old-first, old-last]
existing.removeLast=old-last
existing.afterBackingEdit=[new, old-first]
bulk.view=[C, B, A]
bounded.offers=true/true/false
bounded.peek=B
bounded.addFull=IllegalStateException
bounded.polls=B/A
partial.addAll=IllegalStateException
partial.afterFailure=[B, A]
ordered.firstAdd=true
ordered.equalAdd=false
ordered.values=[beta, alpha]
ordered.reinserted=[alpha, beta]
ordered.null=true
identity.equalValues=true
identity.adds=true/true
identity.size=2
identity.containsSame=true
identity.containsEqualOther=false
identity.removeSame=true
identity.afterRemove.size=1
nonempty.map=IllegalArgumentException
readonly.mapAdd=UnsupportedOperationException
concurrent.adds=true/false
concurrent.size=1
concurrent.null=NullPointerException
~~~

### Giải thích các nhóm kết quả

**`lifo`:** thêm A, B, C cho thứ tự đầu tới cuối C, B, A trên cả view và deque. Peek không lấy ra; remove/poll lấy từ đầu. Element nhìn thấy A còn lại. Hai phép thăm dò rỗng trả `null`, còn remove rỗng ném `NoSuchElementException`.

**`lifo.null`:** deque nền `ArrayDeque` từ chối `null`; view không thay chính sách đó.

**`existing`:** deque không rỗng vẫn được thích nghi. Factory không đảo hai phần tử cũ; add new đặt new lên đầu. Xóa cuối trực tiếp trên deque được nhìn thấy qua view, chứng minh dữ liệu dùng chung. Đây là thí nghiệm quan hệ view, không là khuyến nghị để nhiều thành phần trộn tùy ý các đường sửa.

**`bulk`:** addAll A, B, C trở thành các lần addFirst nên thứ tự C, B, A.

**`bounded`:** hai offer đầu thành công, lần ba trả false khi hết chỗ. Add khi đầy ném lỗi. Lấy phần tử vẫn theo B rồi A, không theo thứ tự offer FIFO.

**`partial`:** một lời gọi addAll có thể đã thêm hai phần tử trước khi thất bại. Dữ liệu B, A còn lại sau ngoại lệ, không có tự phục hồi về rỗng.

**`ordered`:** thêm beta lần đầu thay đổi set; thêm một chuỗi mới bằng beta không thay đổi thành viên theo equals. Thứ tự ban đầu beta, alpha; xóa rồi thêm beta cho alpha, beta. Map nền đã được giao riêng cho set và không bị truy cập sau tạo.

**`identity`:** hai chuỗi bằng nội dung nhưng khác tham chiếu đều được thêm. Tìm đúng tham chiếu thành công, tìm một chuỗi mới bằng nội dung thất bại. Xóa đúng tham chiếu chỉ xóa một trong hai thành viên.

**`nonempty.map`:** một khóa gắn Boolean.FALSE vẫn làm map không rỗng và bị từ chối. Giá trị false không được hiểu là “không phải thành viên” để vượt điều kiện đầu vào.

**`readonly.mapAdd`:** map rỗng không sửa được không cung cấp khả năng thêm cho set. Kiểm tra rỗng chưa đủ để kết luận mọi quyền sửa.

**`concurrent`:** thêm cùng chuỗi lần thứ hai không làm thay đổi set. Khóa null bị từ chối theo chính sách ConcurrentHashMap. Không in thứ tự của set nền này vì nó không hứa thứ tự chèn.

## 8. Cách dùng trong thiết kế chương trình

### Một thuật toán nhận Queue cần quy tắc lấy khác nhau

Nếu thuật toán chỉ gọi offer/poll và chấp nhận chính sách thứ tự do phía gọi chọn, có thể cấp một queue FIFO hoặc view LIFO của deque.

Ví dụ xử lý công việc chờ có thể dùng FIFO để ưu tiên việc cũ hoặc LIFO cho một quy trình duyệt theo chiều sâu. Nhưng sự thay thế chỉ hợp lệ khi hợp đồng thuật toán không ngầm yêu cầu FIFO cho tính đúng.

Tên giao diện `Queue` không thay thế tài liệu về thứ tự. Khi thứ tự quyết định hành vi, hãy ghi rõ queue được cấp có chính sách nào.

### Theo dõi đối tượng đã thăm theo định danh

Khi duyệt một đồ thị đối tượng, hai nút có thể bằng dữ liệu nhưng vẫn là hai đối tượng khác nhau cần xử lý. Set theo định danh đáp ứng cách nhận diện ấy.

Nếu nghiệp vụ là tập mã hoặc tập giá trị, dùng equals thông thường thường phù hợp hơn. Không chọn định danh chỉ vì nó tránh gọi equals; thay quan hệ bình đẳng là thay ý nghĩa dữ liệu.

### Danh sách theo dõi không giữ sống đối tượng

Set khóa yếu có thể phù hợp khi chỉ muốn theo dõi đối tượng mà không tự trở thành lý do giữ nó tồn tại. Cần chấp nhận việc thành viên biến mất theo vòng đời tham chiếu.

Nếu dữ liệu phải tồn tại chắc chắn, chọn cấu trúc giữ mạnh với vòng đời xóa rõ ràng. Khóa yếu không là một cách giới hạn size chính xác hoặc bộ nhớ đệm có lịch hết hạn xác định.

### Thành viên được chia sẻ giữa nhiều luồng

Set từ map đồng thời có thể phù hợp cho thao tác thành viên đơn lẻ. Tuy vậy, `contains` rồi `add` là hai bước; cần cân nhắc dùng kết quả của chính `add` nếu câu hỏi là “lần này có thêm thành viên mới không?”.

Các nhóm thao tác, việc duyệt toàn bộ, trạng thái size hoặc cập nhật dữ liệu ngoài set vẫn cần hợp đồng nhất quán riêng. Không hứa một snapshot chỉ vì thao tác nền là an toàn đồng thời.

Với queue thích nghi trên một deque đồng thời, thao tác được chuyển cho deque có chính sách của nó. View không tự thêm một giao thức nguyên tử cho addAll, cho vòng lặp tiêu thụ hoặc cho trạng thái của các phần tử đang được xử lý.

## 9. Những điều cần kiểm tra ở cấu trúc nền

Đối với LIFO queue, trước tiên xác định cách thêm/lấy, chính sách đầy/rỗng và null. Sau đó quyết định có giữ lộ deque ra ngoài không. Nếu có, mọi đường sửa phải được đưa vào cách giải thích thứ tự của hệ thống.

Đối với set từ map, kiểm tra map rỗng, hỗ trợ các cập nhật cần dùng và được chuyển quyền sử dụng riêng. Sau đó xác định quan hệ bình đẳng khóa, thứ tự duyệt, quyền giữ sống khóa và chính sách đồng thời.

Không thể kiểm tra các đặc tính ấy bằng mỗi kiểu khai báo `Queue<T>` hay `Set<E>`. Phải biết cách tạo và hợp đồng của lớp nền.

Về chi phí, adapter chuyển các thao tác xuống nền nên chi phí phụ thuộc thao tác tương ứng của nền. addAll là chuỗi thao tác thành phần, không phải phép gộp không có chi phí. Đặc tính thứ tự hay đồng thời của nền không tự biến các phần tử thành dữ liệu bất biến sâu.

Nếu có kiểu trực tiếp như `LinkedHashSet`, `HashSet` hoặc `ConcurrentHashMap.newKeySet()` đáp ứng đúng mục tiêu, lựa chọn trực tiếp giúp người đọc nhận diện ý định nhanh hơn. Điểm mạnh của các factory ở bài này là thích nghi một cấu trúc nền có ngữ nghĩa cần thiết, không phải thay mọi collection tiêu chuẩn bằng lớp bọc.

## 10. Thực hành có đáp án

### Bài 1 — Queue không tự đồng nghĩa FIFO

Một deque rỗng được thích nghi bằng `asLifoQueue`. Gọi add A, add B, add C rồi gọi poll ba lần.

**Câu hỏi:** Thứ tự trả là gì? Nếu chỉ giữ ArrayDeque dưới kiểu Queue mà không dùng factory, cách thêm thông thường khác ở đâu?

**Đáp án:** View LIFO trả C, B, A vì add và poll cùng dùng đầu deque. Queue thông thường của deque thêm ở cuối và lấy ở đầu nên tạo FIFO. Thay kiểu biến không tự thay thao tác nền như adapter làm.

### Bài 2 — Deque đã có dữ liệu

Deque từ đầu tới cuối là `[old1, old2]`. Tạo view LIFO rồi gọi `offer("new")`.

**Câu hỏi:** Có phải factory yêu cầu rỗng hoặc tự đảo thành old2, old1 không? Thứ tự hiện tại là gì?

**Đáp án:** Không. Deque không bắt buộc rỗng; dữ liệu cũ không tự đảo. Sau offer là `[new, old1, old2]`. Quy tắc đặt vào đầu áp dụng cho thao tác qua view, không suy lịch sử tạo các phần tử cũ.

### Bài 3 — Thêm cả nhóm khi đầy

Một view dựa trên deque dung lượng 2 đang rỗng. Gọi addAll với A, B, C.

**Câu hỏi:** Có thể giả định ngoại lệ đồng nghĩa không có phần tử nào được thêm không?

**Đáp án:** Không. addAll thực hiện chuỗi addFirst; hai phần tử đầu có thể đã được thêm trước lỗi ở phần tử thứ ba. Với thí nghiệm trong bài, trạng thái sau lỗi là B, A. Nếu cần chấp nhận cả nhóm như một đơn vị, phải thiết kế giao thức khác.

### Bài 4 — Map có giá trị false

~~~java
Map<String, Boolean> map = new HashMap<>();
map.put("x", Boolean.FALSE);
~~~

**Câu hỏi:** Có thể truyền map ấy cho newSetFromMap vì “x chưa active” không?

**Đáp án:** Không. Map có một ánh xạ nên không rỗng; factory ném IllegalArgumentException. Điều kiện là không có ánh xạ, không phải mọi giá trị đều false. Nếu cần tập khóa từ dữ liệu map hiện hữu, phải dùng một cách khác đúng mục tiêu.

### Bài 5 — Giữ map để debug sau tạo

Một thành phần tạo map rỗng, truyền vào newSetFromMap, rồi vẫn giữ map để gọi put/get hoặc đọc keySet ở một chỗ khác.

**Câu hỏi:** Cách sở hữu này có phù hợp hợp đồng không? Cách tạo nào giảm rủi ro?

**Đáp án:** Không phù hợp khuyến cáo của API: không truy cập trực tiếp map sau khi tạo set. Tạo map ngay trong lời gọi factory và chỉ giữ Set kết quả; mọi thao tác và quan sát cần đi qua set. Không giữ một view của map để tạo đường truy cập gián tiếp khác.

### Bài 6 — Hai chuỗi bằng nhau trong set định danh

Hai biến được tạo bằng hai lời gọi `new String("x")` riêng, rồi thêm vào set dựa trên IdentityHashMap.

**Câu hỏi:** Size có thể là 2 không? contains với một new String("x") thứ ba có tìm thấy không?

**Đáp án:** Size là 2 trong tình huống này vì hai tham chiếu khác nhau. Chuỗi thứ ba không phải một trong các tham chiếu đang lưu nên không tìm thấy. Đây là ngữ nghĩa định danh có chủ đích, khác tập giá trị dùng String.equals.

### Bài 7 — Map rỗng nhưng không ghi được

Map rỗng không sửa được được truyền cho factory, sau đó gọi set.add("x").

**Câu hỏi:** Việc tạo set thành công đã chứng minh add được hỗ trợ chưa?

**Đáp án:** Chưa. Factory kiểm tra rỗng không biến map thành map sửa được; add phải ghi xuống map và có thể ném UnsupportedOperationException như ví dụ. Chọn map nền có quyền sửa cần dùng ngay từ đầu.

### Bài 8 — Khóa yếu và đồng thời

Một set dùng WeakHashMap; set khác dùng ConcurrentHashMap.

**Câu hỏi:** Có được hứa set khóa yếu chỉ mất phần tử khi gọi remove không? Có được hứa set đồng thời nhận null và mọi nhóm addAll là nguyên tử không?

**Đáp án:** Không. Thành viên khóa yếu có thể biến mất khi khóa không còn được giữ mạnh phù hợp và được thu gom. ConcurrentHashMap từ chối null và các bảo đảm thao tác đơn lẻ không tự gộp nhiều bước thành một giao dịch. Chọn cấu trúc nền là chọn cả những chính sách ấy, không chỉ chọn một cách lưu trữ bên trong.

Hai adapter này chỉ hữu ích khi hợp đồng nền được đưa vào hợp đồng sử dụng. Với asLifoQueue, điều cốt lõi là cùng đầu thêm/lấy và view dùng chung deque. Với newSetFromMap, điều cốt lõi là map rỗng, sở hữu riêng và các đặc tính khóa được kế thừa vào set.


</details>

- [Quay lại đầu trang](#back-to-top)
