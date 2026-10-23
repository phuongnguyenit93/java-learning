<a id="back-to-top"></a>

# Set sắp xếp

## Menu
- [SortedSet và thứ tự tự nhiên hoặc Comparator](#sorted-set-001)
- [TreeSet và cấu trúc cây tìm kiếm cân bằng](#sorted-set-002)
- [Tiêu chí trùng phần tử khi phép so sánh trả về 0](#sorted-set-003)
- [Nhất quán giữa Comparator và equals](#sorted-set-004)
- [first, last, headSet, tailSet và subSet](#sorted-set-005)
- [NavigableSet: lower, floor, ceiling và higher](#sorted-set-006)
- [pollFirst, pollLast, descendingSet và descendingIterator](#sorted-set-007)
- [Biên đóng–mở, chính sách null và các view phạm vi](#sorted-set-008)

## <a id="sorted-set-001">SortedSet và thứ tự tự nhiên hoặc Comparator</a>

<details>
<summary>Xem chi tiết</summary>

# SortedSet và thứ tự tự nhiên hoặc Comparator

## 1. Một tập hợp vừa loại trùng vừa duy trì thứ tự

Giả sử chương trình nhận các mức ưu tiên `7, 2, 9, 2, 5`. Bạn cần mỗi mức xuất hiện một lần và luôn duyệt theo thứ tự tăng dần. Nếu chỉ đưa dữ liệu vào một tập bất kỳ, yêu cầu loại trùng chưa bảo đảm yêu cầu sắp xếp. Nếu chỉ sắp một danh sách, hai lần xuất hiện của số 2 vẫn có thể còn nguyên.

`SortedSet<E>` biểu diễn một tập hợp có quy tắc thứ tự trên các phần tử. Một cách triển khai thông dụng là `TreeSet<E>`. Với thứ tự tự nhiên của `Integer`, ví dụ trên cho `[2, 5, 7, 9]`, bất kể số 7 được thêm trước số 2.

Điểm quan trọng hơn “kết quả in ra có sắp xếp” là **quy tắc so sánh còn quyết định phần tử nào được xem là trùng**. Nếu so sánh hai đối tượng cho kết quả 0, tập sắp xếp xem chúng thuộc cùng một vị trí tương đương. Chọn `Comparator` vì thế là chọn cả trình tự lẫn một phần ý nghĩa của sự duy nhất.

Bài học đi từ hai nguồn tạo thứ tự — thứ tự tự nhiên và `Comparator` — đến ví dụ đối tượng tự định nghĩa, tiêu chí phụ, trường hợp bằng nhau, các phương thức cơ bản và lỗi cần tránh. Mã hoàn chỉnh dùng API có trong Java 17.

Bạn chỉ cần biết biến, lớp, phương thức, kiểu tổng quát như `List<Integer>` và vòng lặp. Các khái niệm `Comparable`, `Comparator` và giá trị trả về của phép so sánh được giải thích trước khi dùng.

## 2. SortedSet là hợp đồng; TreeSet là một cách triển khai

Có thể đọc khai báo:

~~~java
SortedSet<Integer> priorities = new TreeSet<>();
~~~

như sau:

- `SortedSet<Integer>` là kiểu mà phần mã sử dụng nhìn thấy: tập hợp số nguyên có thứ tự và các thao tác liên quan đến thứ tự.
- `new TreeSet<>()` tạo đối tượng cụ thể thực hiện hợp đồng đó.
- `Integer` là kiểu phần tử; `int` được đóng gói thành `Integer` khi cần.

Đoạn trên là khai báo minh họa; cần nhập `java.util.SortedSet` và `java.util.TreeSet` khi đặt vào một chương trình.

`SortedSet` mở rộng `Set`, nên vẫn có `add`, `contains`, `remove`, `size` và khả năng duyệt. Nó bổ sung cách lấy phần tử đầu/cuối, hỏi quy tắc so sánh và tạo các vùng theo thứ tự.

Bức tranh khái niệm:

~~~text
Set
  └─ SortedSet: thêm hợp đồng thứ tự
       └─ NavigableSet: thêm các phép tìm lân cận theo thứ tự
            └─ TreeSet: một lớp triển khai cụ thể
~~~

Sơ đồ mô tả quan hệ kiểu, không phải cấu trúc nút của cây trong bộ nhớ. Các phép tìm lân cận như `floor` hoặc `ceiling` thuộc phạm vi mở rộng; để hiểu thứ tự tự nhiên và `Comparator`, trước hết cần nắm đúng hợp đồng `SortedSet`.

Theo API, thứ tự có thể đến từ bản thân phần tử hoặc từ một `Comparator` được cung cấp khi tạo tập. Bộ duyệt đi theo thứ tự tăng dần **của quy tắc đang áp dụng**. “Tăng dần” ở đây không mặc nhiên có nghĩa số nhỏ đến số lớn trong mọi tập. [Java 17: SortedSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SortedSet.html).

## 3. Thứ tự tự nhiên đến từ Comparable

Thứ tự tự nhiên (natural ordering) là quy tắc so sánh mà kiểu phần tử cung cấp qua `Comparable<T>`, với phương thức:

~~~java
int compareTo(T other);
~~~

Đây là chữ ký rút gọn để đọc ý nghĩa, không phải chương trình độc lập.

Với `a.compareTo(b)`:

| Kết quả | Ý nghĩa theo quy tắc của kiểu |
| --- | --- |
| Nhỏ hơn 0 | `a` đứng trước `b` |
| Bằng 0 | `a` và `b` tương đương về thứ tự |
| Lớn hơn 0 | `a` đứng sau `b` |

Chỉ dấu âm, 0 hoặc dương có ý nghĩa chung. Không viết mã chỉ nhận `-1` và `1`, vì một phép so sánh hợp lệ có thể trả một số âm hoặc dương khác.

Ví dụ, thứ tự tự nhiên của `Integer` là thứ tự số học. `new TreeSet<Integer>()` không cần bạn truyền thêm một hàm để biết số 2 đứng trước số 7.

Với `String`, thứ tự tự nhiên là thứ tự từ điển theo giá trị các đơn vị mã ký tự trong chuỗi. Nó không tự trở thành quy tắc sắp tên tiếng Việt, không tự bỏ qua chữ hoa/thường và không diễn giải chuỗi phiên bản như các thành phần số. Chẳng hạn, việc sắp chuỗi `"1.10"` và `"1.9"` không tương đương việc so sánh hai cặp số `(1, 10)` và `(1, 9)`. [Java 17: String.compareTo](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#compareTo(java.lang.String)).

Một lớp không tự có thứ tự tự nhiên chỉ vì có các trường số hoặc có `toString`. `record` cũng không tự triển khai `Comparable`. Nếu muốn `Version` có thứ tự tự nhiên, phải khai báo và viết quy tắc so sánh thích hợp.

Ví dụ trong chương trình bên dưới so sánh phiên bản theo phần chính trước, phần phụ sau:

~~~text
1.9  đứng trước  1.10  đứng trước  2.0
~~~

Đó là quyết định của mô hình `Version` trong bài; nó không cố diễn giải mọi quy ước phiên bản phần mềm ngoài thực tế.

## 4. Comparator là quy tắc thứ tự được cung cấp từ bên ngoài

Một kiểu dữ liệu có thể cần nhiều cách trình bày. Danh sách thí sinh có thể ưu tiên điểm cao; một màn hình khác có thể ưu tiên tên. Không nên ép mọi yêu cầu đó thành một thứ tự tự nhiên duy nhất của đối tượng.

`Comparator<T>` đóng gói một quy tắc so sánh riêng. Phương thức trọng tâm là:

~~~java
int compare(T left, T right);
~~~

Dấu của kết quả có cùng vai trò với `compareTo`. Sự khác nhau là nơi đặt quy tắc:

| Tiêu chí | Comparable | Comparator |
| --- | --- | --- |
| Quy tắc nằm ở đâu? | Trong kiểu phần tử | Trong đối tượng so sánh bên ngoài |
| Phương thức chính | `a.compareTo(b)` | `rule.compare(a, b)` |
| Tình huống phù hợp | Một thứ tự mặc định rõ nghĩa của kiểu | Thứ tự theo một nhu cầu cụ thể hoặc nhiều cách sắp |
| Khi dùng với TreeSet | Không cần truyền bộ so sánh riêng | Truyền quy tắc vào hàm khởi tạo |

Ví dụ minh họa cho số nguyên:

~~~java
SortedSet<Integer> descending =
        new TreeSet<>(Comparator.reverseOrder());
~~~

Tập này dùng thứ tự ngược với thứ tự tự nhiên. Thêm `7, 2, 9, 5` sẽ duyệt thành `[9, 7, 5, 2]`.

Một `Comparator` được cung cấp sẽ quyết định thứ tự của tập đó; không có bước tự động “nếu comparator thấy bằng thì quay sang compareTo”. Nếu muốn tiêu chí phụ, phải viết hoặc ghép tiêu chí phụ rõ ràng.

Đối tượng không triển khai `Comparable` vẫn có thể nằm trong `TreeSet` nếu `Comparator` cung cấp so sánh hợp lệ cho các phần tử. Đó là lý do `Candidate` trong chương trình không cần một phương thức `compareTo`.

## 5. Hiểu “tăng dần” theo chính Comparator

Khi dùng thứ tự ngược, số 9 đứng trước số 2 vì bộ so sánh đang xem 9 là nhỏ hơn 2 theo thứ tự mà nó định nghĩa. Không có mâu thuẫn giữa hợp đồng duyệt tăng dần và kết quả `[9, 7, 5, 2]`.

Hãy dịch các từ “đầu”, “cuối”, “nhỏ hơn” và “lớn hơn” sang quan hệ của quy tắc hiện tại:

| Tập | Thứ tự duyệt | first() | last() |
| --- | --- | --- | --- |
| Tự nhiên của Integer | `[2, 5, 7, 9]` | `2` | `9` |
| Ngược thứ tự tự nhiên | `[9, 7, 5, 2]` | `9` | `2` |

`first()` không có nghĩa “phần tử được thêm đầu tiên”. `last()` cũng không có nghĩa “phần tử vừa được thêm”. Chúng dựa vào thứ tự của tập tại thời điểm gọi.

Với một tập rỗng, `first()` và `last()` ném `NoSuchElementException`. Chúng không dùng `null` làm giá trị chung báo rỗng. Điều này đặc biệt đáng chú ý khi bạn tự cung cấp một quy tắc cho phép `null`: một tập không rỗng có thể có phần tử đầu là `null`.

Phương thức `comparator()` trả bộ so sánh đang được dùng hoặc `null` nếu tập dùng thứ tự tự nhiên. `comparator() == null` không có nghĩa “không sắp xếp”, cũng không chứng minh tập cho phép phần tử `null`. Hai ý nghĩa của từ `null` nằm ở hai câu hỏi khác nhau.

## 6. So sánh bằng 0 quyết định trùng trong tập sắp xếp

Giả sử mô hình `Candidate` có hai thành phần:

~~~text
name: tên
score: điểm
~~~

Và đầu vào là:

~~~text
An:90
Binh:90
Chi:80
~~~

Nếu quy tắc chỉ so sánh `score`, nó xem `An:90` và `Binh:90` tương đương vì:

~~~text
compare(An:90, Binh:90) = 0
~~~

Một `TreeSet` dùng quy tắc đó chỉ giữ một đại diện cho nhóm điểm 90. Khi thêm `An` trước rồi `Binh`, kết quả của ví dụ là `[Chi:80, An:90]`. Số phần tử bằng 2, không phải 3.

Đây không phải lỗi `TreeSet` quên so sánh tên. Bạn đã cung cấp một quy tắc không dùng tên, và tập dùng đúng quy tắc ấy để xác định sự tương đương.

Hệ quả không chỉ xuất hiện ở `add`. `contains(new Candidate("Unknown", 90))` cũng có thể trả `true` trong tập chỉ so sánh điểm: đã có một đại diện tương đương về thứ tự, dù không có thí sinh tên `Unknown`. Nếu dùng `remove` bằng đối tượng như vậy, bạn có thể xóa đại diện của nhóm điểm đó.

Đây là lý do không xem `Comparator` của tập chỉ như một tùy chọn trình bày. Nó tham gia trực tiếp vào việc tìm và phân biệt phần tử. Với `TreeSet`, không có phép băm bổ sung để phân biệt hai đối tượng sau khi so sánh đã cho 0. [Java 17: TreeSet, so sánh phần tử và hợp đồng Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html).

Nếu nghiệp vụ cần giữ mọi thí sinh cùng điểm, “điểm” chưa phải tiêu chí đủ để phân biệt phần tử trong tập. Có thể thêm tiêu chí phù hợp với mô hình, hoặc dùng danh sách được sắp xếp nếu bạn cần giữ mọi bản ghi kể cả bản trùng.

## 7. Ghép tiêu chí: điểm giảm dần, tên tăng dần

Trong mô hình nhỏ của bài, `Candidate` là `record` chỉ gồm `name` và `score`. Phép `equals` mặc định của nó xét cả hai thành phần. Các tên trong ví dụ khác `null`.

Ta chọn thứ tự:

1. Điểm cao đứng trước.
2. Nếu điểm bằng nhau, tên tăng dần.

Quy tắc được viết như sau:

~~~java
Comparator<Candidate> ranking =
        Comparator.comparingInt(Candidate::score).reversed()
                .thenComparing(Candidate::name);
~~~

Đoạn này được dùng nguyên vẹn trong chương trình đầy đủ. `Candidate::score` là tham chiếu phương thức: lấy điểm của một thí sinh. `comparingInt` tạo so sánh theo số nguyên; `reversed` đảo hướng của tiêu chí điểm; `thenComparing` chỉ dùng tên khi tiêu chí trước cho kết quả 0.

Ba thí sinh khi đó được duyệt thành:

~~~text
[An:90, Binh:90, Chi:80]
~~~

`An` và `Binh` cùng điểm nhưng không còn cho kết quả so sánh 0 vì tên khác nhau. Hai đối tượng đều có `name="An"` và `score=90` vẫn cho 0, đúng với phép `equals` của mô hình `record` này.

Không đặt `reversed()` tùy tiện. Biểu thức:

~~~java
Comparator.comparingInt(Candidate::score)
        .thenComparing(Candidate::name)
        .reversed()
~~~

đảo **toàn bộ** thứ tự đã ghép: cả điểm lẫn tên đều đổi hướng. Nó không biểu thị “điểm giảm, tên tăng”. Đây là đoạn minh họa để đối chiếu phạm vi của `reversed`, không phải quy tắc được dùng trong chương trình.

Các hàm ghép bộ so sánh mô tả thứ tự theo từng tiêu chí; đọc từ trái sang phải và xác định chính xác đoạn nào được đảo hướng. [Java 17: Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html).

Tên trong ví dụ không thay thế một định danh thí sinh ngoài đời. Nếu hệ thống thực có mã dự thi, yêu cầu duy nhất và phép `equals` cần được thiết kế theo mô hình đó. Không tự thêm một trường bất kỳ làm tiêu chí phụ rồi cho rằng mọi vấn đề nhận diện đã được giải quyết.

## 8. Nhất quán với equals là điều kiện cần kiểm tra

Một thứ tự nhất quán với `equals` nghĩa là, đối với các phần tử trong miền sử dụng:

~~~text
compare(a, b) == 0  khi và chỉ khi  a.equals(b)
~~~

Với thứ tự tự nhiên, thay `compare(a, b)` bằng `a.compareTo(b)`. Dấu bằng 0 ở đây là quan hệ tương đương về thứ tự, không phải phép so sánh tham chiếu `a == b`.

Hợp đồng chung của `Set` được diễn đạt bằng `equals`. Tập sắp xếp lại dùng phép so sánh để xác định tương đương. Khi hai quan hệ không khớp, tập có thể hoạt động xác định theo bộ so sánh mà vẫn không đáp ứng đầy đủ kỳ vọng của hợp đồng `Set`.

Trong chương trình:

- Quy tắc chỉ theo điểm gộp `An:90` và `Binh:90` dù `equals` của record phân biệt chúng. Đây là ví dụ cố ý làm lộ sai lệch.
- Quy tắc điểm rồi tên xét đủ hai thành phần của record, với miền tên khác `null` của ví dụ. Hai đối tượng chỉ so sánh bằng 0 khi chúng bằng nhau theo `equals`.

Đừng sửa bằng cách “không bao giờ trả 0, kể cả cùng đối tượng”. Khi đó bạn phá vỡ chính hợp đồng của phép so sánh. Tập không phải cách lưu nhiều bản trùng bằng một bộ so sánh giả vờ mọi thứ luôn khác nhau.

Một ngoại lệ nổi tiếng trong JDK là `BigDecimal`. `new BigDecimal("1.0")` và `new BigDecimal("1.00")` bằng nhau về giá trị số theo `compareTo` nhưng khác `equals` vì cách biểu diễn có thang đo khác nhau. `TreeSet` theo thứ tự tự nhiên xem chúng là một nhóm tương đương và lần thêm thứ hai trả `false`. API cũng nêu rõ điểm cần lưu ý này khi dùng `BigDecimal` trong tập hoặc ánh xạ sắp xếp. [Java 17: BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html).

Không cần học toàn bộ phép tính thập phân để rút ra bài học: không mặc định `compareTo == 0` luôn đồng nghĩa `equals == true` với mọi kiểu có sẵn. Khi chọn kiểu phần tử hoặc một bộ so sánh mới, hãy kiểm tra quy tắc bằng nhau thực tế.

## 9. Bộ so sánh phải hợp lệ trên toàn bộ miền dữ liệu

Một quy tắc thứ tự phải cho kết quả có thể dùng nhất quán, không chỉ đúng với một cặp dữ liệu mẫu.

Các điều kiện quan trọng gồm:

- Đảo hai đối số phải đảo dấu kết quả, với 0 vẫn là 0.
- Quan hệ trước/sau phải có tính bắc cầu: nếu `a` đứng trước `b` và `b` đứng trước `c`, không được để `a` đứng sau `c`.
- Nếu `a` và `b` so sánh bằng 0, quan hệ của chúng với một phần tử thứ ba phải tương thích.

Quy tắc này được mô tả trong hợp đồng `Comparable` và `Comparator`. Đừng dùng một phép so sánh phụ thuộc ngẫu nhiên hoặc thay đổi ý nghĩa giữa hai lời gọi cho cùng dữ liệu. [Java 17: Comparable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html).

Một lỗi thường gặp là so sánh số nguyên bằng phép trừ:

~~~java
// Minh họa cách viết cần tránh khi giá trị có thể tràn số.
Comparator<Integer> unsafe = (a, b) -> a - b;
~~~

Hai toán hạng hợp lệ vẫn có thể tạo hiệu vượt phạm vi `int`, khiến dấu của kết quả sai. Dùng `Integer.compare(a, b)` hoặc `Comparator.naturalOrder()` thay vì tự suy dấu từ phép trừ.

Cũng không dùng một biến trạng thái có thể thay đổi để lặng lẽ đổi quy tắc sau khi đã thêm dữ liệu. Tập không tự tháo và dựng lại cấu trúc mỗi khi biến mà bộ so sánh đang đọc bị đổi.

Cách dễ theo dõi là xây dựng bộ so sánh ổn định, dùng các hàm so sánh chuẩn và kiểm tra những cặp có giá trị biên, bằng tiêu chí đầu, khác tiêu chí phụ. Với mô hình record trong bài, bất biến của dữ liệu giúp tránh việc thứ tự thay đổi ngoài các thao tác của tập.

## 10. Chương trình đầy đủ dùng Java 17

Lưu mã sau thành `SortedSetOrderingDemo.java`. Chương trình gồm các nhóm ví dụ độc lập được in bằng nhãn rõ ràng:

- Số nguyên theo thứ tự tự nhiên và thứ tự ngược.
- `Version` tự cung cấp `Comparable`.
- `Candidate` dùng bộ so sánh chỉ theo điểm và bộ so sánh đầy đủ hơn.
- Vùng `subSet`, việc sao chép và chính sách `null`.
- Các lỗi dự kiến và trường hợp `BigDecimal`.

Các khối `try/catch` chỉ minh họa những lỗi cụ thể được dự đoán; chúng không che giấu lỗi bất kỳ của toàn bộ chương trình.

~~~java
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;

public class SortedSetOrderingDemo {
    record Version(int major, int minor) implements Comparable<Version> {
        @Override
        public int compareTo(Version other) {
            int byMajor = Integer.compare(major, other.major);
            return byMajor != 0 ? byMajor : Integer.compare(minor, other.minor);
        }

        @Override
        public String toString() {
            return major + "." + minor;
        }
    }

    record Candidate(String name, int score) {
        @Override
        public String toString() {
            return name + ":" + score;
        }
    }

    public static void main(String[] args) {
        SortedSet<Integer> natural = new TreeSet<>();
        for (int number : new int[] {7, 2, 9, 2, 5}) {
            System.out.println("add " + number + "=" + natural.add(number));
        }
        System.out.println("natural=" + natural);
        System.out.println("natural comparator is null=" + (natural.comparator() == null));
        System.out.println("natural first/last=" + natural.first() + "/" + natural.last());

        SortedSet<Integer> reverse = new TreeSet<>(Comparator.reverseOrder());
        reverse.addAll(natural);
        System.out.println("reverse=" + reverse);
        System.out.println("reverse comparator is null=" + (reverse.comparator() == null));
        System.out.println("reverse first/last=" + reverse.first() + "/" + reverse.last());
        System.out.println("reverse subSet(9,5)=" + reverse.subSet(9, 5));

        SortedSet<Version> versions = new TreeSet<>();
        versions.addAll(List.of(new Version(1, 10), new Version(2, 0), new Version(1, 9)));
        System.out.println("versions=" + versions);
        System.out.println("version duplicate=" + versions.add(new Version(1, 10)));

        List<Candidate> input = List.of(
                new Candidate("An", 90),
                new Candidate("Binh", 90),
                new Candidate("Chi", 80));
        SortedSet<Candidate> scoreOnly =
                new TreeSet<>(Comparator.comparingInt(Candidate::score));
        scoreOnly.addAll(input);
        System.out.println("score only=" + scoreOnly);
        System.out.println("score only size=" + scoreOnly.size());
        Candidate stranger = new Candidate("Unknown", 90);
        System.out.println("different by equals=" + !input.get(0).equals(stranger));
        System.out.println("score only contains stranger=" + scoreOnly.contains(stranger));

        Comparator<Candidate> ranking =
                Comparator.comparingInt(Candidate::score).reversed()
                        .thenComparing(Candidate::name);
        SortedSet<Candidate> ranked = new TreeSet<>(ranking);
        ranked.addAll(input);
        System.out.println("ranked=" + ranked);
        System.out.println("ranked size=" + ranked.size());
        System.out.println("ranked duplicate=" + ranked.add(new Candidate("An", 90)));
        System.out.println("ranked contains stranger=" + ranked.contains(stranger));

        SortedSet<Integer> middle = natural.subSet(2, 9);
        System.out.println("middle initial=" + middle);
        middle.add(6);
        System.out.println("parent after view add=" + natural);
        natural.remove(5);
        System.out.println("middle after parent remove=" + middle);
        System.out.println("headSet(7)=" + natural.headSet(7));
        System.out.println("tailSet(7)=" + natural.tailSet(7));
        try {
            middle.add(9);
        } catch (IllegalArgumentException exception) {
            System.out.println("outside view=" + exception.getClass().getSimpleName());
        }

        SortedSet<Integer> copySame = new TreeSet<>(reverse);
        Collection<Integer> collectionInput = reverse;
        SortedSet<Integer> copyNatural = new TreeSet<>(collectionInput);
        System.out.println("sorted-set copy=" + copySame);
        System.out.println("collection copy=" + copyNatural);

        SortedSet<Integer> nullable =
                new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));
        nullable.addAll(java.util.Arrays.asList(5, null, 2, null));
        System.out.println("nullable=" + nullable);
        System.out.println("nullable first=" + nullable.first());
        try {
            natural.add(null);
        } catch (NullPointerException exception) {
            System.out.println("natural null=" + exception.getClass().getSimpleName());
        }

        SortedSet<Candidate> missingRule = new TreeSet<>();
        try {
            missingRule.add(new Candidate("An", 90));
        } catch (ClassCastException exception) {
            System.out.println("missing ordering=" + exception.getClass().getSimpleName());
        }
        try {
            new TreeSet<Integer>().first();
        } catch (NoSuchElementException exception) {
            System.out.println("empty first=" + exception.getClass().getSimpleName());
        }

        BigDecimal one = new BigDecimal("1.0");
        BigDecimal sameNumber = new BigDecimal("1.00");
        SortedSet<BigDecimal> decimals = new TreeSet<>();
        decimals.add(one);
        System.out.println("decimal equals=" + one.equals(sameNumber));
        System.out.println("decimal compare=" + one.compareTo(sameNumber));
        System.out.println("decimal second add=" + decimals.add(sameNumber));
        System.out.println("decimal size=" + decimals.size());
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 SortedSetOrderingDemo.java
java SortedSetOrderingDemo
~~~

Dùng JDK 17 trở lên. Tùy chọn `--release 17` kiểm tra mã và API theo Java 17; tên tệp khớp với lớp `public`.

Kết quả chuẩn:

~~~text
add 7=true
add 2=true
add 9=true
add 2=false
add 5=true
natural=[2, 5, 7, 9]
natural comparator is null=true
natural first/last=2/9
reverse=[9, 7, 5, 2]
reverse comparator is null=false
reverse first/last=9/2
reverse subSet(9,5)=[9, 7]
versions=[1.9, 1.10, 2.0]
version duplicate=false
score only=[Chi:80, An:90]
score only size=2
different by equals=true
score only contains stranger=true
ranked=[An:90, Binh:90, Chi:80]
ranked size=3
ranked duplicate=false
ranked contains stranger=false
middle initial=[2, 5, 7]
parent after view add=[2, 5, 6, 7, 9]
middle after parent remove=[2, 6, 7]
headSet(7)=[2, 6]
tailSet(7)=[7, 9]
outside view=IllegalArgumentException
sorted-set copy=[9, 7, 5, 2]
collection copy=[2, 5, 7, 9]
nullable=[null, 2, 5]
nullable first=null
natural null=NullPointerException
missing ordering=ClassCastException
empty first=NoSuchElementException
decimal equals=false
decimal compare=0
decimal second add=false
decimal size=1
~~~

## 11. Đọc bằng chứng từ chương trình

### 11.1. Vị trí phụ thuộc quy tắc, không phụ thuộc thứ tự thêm

Số 7 được thêm trước số 2 nhưng `natural` vẫn duyệt `[2, 5, 7, 9]`. Lần thêm số 2 thứ hai trả `false` vì số 2 tương đương theo thứ tự tự nhiên đã có.

`natural comparator is null=true` xác nhận không có bộ so sánh tường minh. Nó không biểu thị tập thiếu thứ tự.

Khi cùng dữ liệu được đưa vào `reverse`, kết quả là `[9, 7, 5, 2]`. `first/last` đổi thành `9/2` vì hai phương thức đọc hai đầu của quy tắc hiện tại.

### 11.2. Comparable và Comparator giải quyết hai cách cung cấp quy tắc

`Version` triển khai `Comparable<Version>`, nên `new TreeSet<Version>()` dùng được thứ tự tự nhiên do lớp cung cấp. So sánh phần chính trước, phần phụ sau cho `[1.9, 1.10, 2.0]`.

`Candidate` không triển khai `Comparable`. Hai tập chứa thí sinh vẫn hoạt động khi nhận `Comparator`. Nhưng `missingRule` không nhận bộ so sánh và cũng không có thứ tự tự nhiên từ phần tử, nên thêm thí sinh dẫn đến `ClassCastException`.

Việc viết được `new TreeSet<Candidate>()` ở thời điểm biên dịch không tự tạo ra `compareTo`. Kiểu tổng quát của `TreeSet` không bắt bạn khai báo `E extends Comparable<E>`, vì còn phải hỗ trợ trường hợp có `Comparator`. Bạn vẫn có trách nhiệm cung cấp một trong hai con đường so sánh hợp lệ.

### 11.3. Bộ so sánh chỉ theo điểm gây hệ quả đến truy vấn

`score only size=2` cho thấy đã có một thí sinh không được thêm như phần tử riêng. `different by equals=true` nhưng `score only contains stranger=true` cho thấy phép tìm của tập đang dùng quan hệ tương đương theo điểm.

Với `ranking` gồm điểm và tên, `ranked size=3` giữ cả ba giá trị record khác nhau trong mẫu. `ranked contains stranger=false` vì tên `Unknown` không khớp bất kỳ đại diện nào khi dùng đủ tiêu chí.

Nhìn vào `size` và `contains` như vậy giúp phát hiện sai mô hình trước khi chỉ tập trung vào việc chuỗi in ra có vẻ “đúng thứ tự”.

### 11.4. Chính sách null cần được chỉ định ở quy tắc

Tập `nullable` nhận `Comparator.nullsFirst(Comparator.naturalOrder())`, nên `null` được xếp trước các số. Hai lần thêm `null` vẫn tạo một thành viên, cho `[null, 2, 5]`.

`nullable first=null` là phần tử thực, không phải dấu hiệu tập rỗng. Trái lại, tập số theo thứ tự tự nhiên không nhận `null` và ví dụ in tên lỗi `NullPointerException`.

### 11.5. Bằng số học và bằng đối tượng có thể khác

`decimal compare=0` đi cùng `decimal equals=false`. Lần thêm số thập phân thứ hai trả `false` và kích thước bằng 1. Tập dùng đúng quan hệ thứ tự của `BigDecimal`, không chuyển sang `equals` để thử phân biệt thêm.

Ví dụ này có chủ đích làm rõ sự không nhất quán giữa hai quan hệ; không phải khuyến nghị bỏ qua khác biệt biểu diễn khi nó quan trọng với dữ liệu của bạn.

## 12. first, last và các vùng theo thứ tự

Ngoài duyệt, `SortedSet` có những phép cơ bản sau:

| Phương thức | Ý nghĩa theo thứ tự hiện tại |
| --- | --- |
| `comparator()` | Trả quy tắc tường minh, hoặc `null` nếu dùng thứ tự tự nhiên |
| `first()` | Phần tử đầu |
| `last()` | Phần tử cuối |
| `subSet(from, to)` | Vùng từ `from`, có tính `from`, đến trước `to` |
| `headSet(to)` | Vùng trước `to`, không tính `to` |
| `tailSet(from)` | Vùng từ `from` trở đi, có tính `from` |

Các phương thức lấy vùng của `SortedSet` trong bảng dùng những quy tắc biên cố định. Những biến thể cho phép chọn tính/không tính biên qua tham số `boolean` thuộc `NavigableSet`, không phải chữ ký hai đối số của `SortedSet.subSet`.

Với thứ tự tự nhiên và dữ liệu `[2, 5, 7, 9]`:

~~~text
subSet(2, 9) → [2, 5, 7]
headSet(7)   → [2, 5]
tailSet(7)   → [7, 9]
~~~

Giá trị biên không bắt buộc phải đang có trong tập. Chúng biểu diễn giới hạn của vùng theo phép so sánh. Chẳng hạn, trên tập ban đầu, `subSet(3, 8)` sẽ chọn các phần tử 5 và 7 dù không có 3 hay 8.

Với thứ tự ngược `[9, 7, 5, 2]`, chương trình dùng `subSet(9, 5)` và nhận `[9, 7]`. Số 9 là đầu vùng theo thứ tự của bộ so sánh; số 5 là biên cuối bị loại. Đừng đổi thành `subSet(5, 9)` chỉ vì 5 nhỏ hơn 9 về số học: hai biên phải hợp lệ theo chính thứ tự của tập.

Nếu hai biên bằng nhau theo so sánh, `subSet(from, to)` là vùng rỗng. Nếu biên đầu đứng sau biên cuối theo quy tắc đang dùng, lời gọi không biểu diễn một vùng hợp lệ.

## 13. Các vùng là khung nhìn liên kết với tập gốc

`subSet` không tạo một bộ dữ liệu độc lập. Nó trả một khung nhìn (view) được tập gốc hậu thuẫn. Chương trình cho bằng chứng hai chiều:

~~~text
natural ban đầu: [2, 5, 7, 9]
middle:          subSet(2, 9) = [2, 5, 7]

middle.add(6)
natural:         [2, 5, 6, 7, 9]

natural.remove(5)
middle:          [2, 6, 7]
~~~

Thêm qua `middle` cập nhật tập gốc. Xóa phần tử thuộc vùng qua tập gốc làm phần tử ấy biến mất khi nhìn qua `middle`.

Khung nhìn vẫn bị ràng buộc bởi biên đã chọn. `middle.add(9)` không hợp lệ vì 9 là biên cuối loại trừ. Nó ném `IllegalArgumentException`, dù tập gốc có thể chứa 9 một cách hợp lệ.

Biên là điều kiện nhận phần tử của khung nhìn, không chỉ là một bộ lọc được dùng một lần lúc tạo biến. Sau khi có khung nhìn, thêm một phần tử phù hợp vào tập gốc cũng có thể làm phần tử đó xuất hiện qua khung nhìn.

Nếu cần một ảnh chụp độc lập, có thể tạo tập mới từ khung nhìn với lựa chọn quy tắc phù hợp. Việc sao chép tập hợp tạo độc lập về thành viên của cấu trúc; nó không tự nhân bản sâu từng đối tượng phần tử.

Cẩn thận khi truyền vùng cho hàm khác: nếu hàm xóa hoặc làm rỗng khung nhìn, dữ liệu tương ứng của tập gốc cũng bị ảnh hưởng. Khi chỉ cần đọc, hợp đồng của hàm nên diễn đạt rõ điều đó thay vì để người gọi tưởng rằng đã nhận bản sao.

## 14. Sao chép có thể giữ hoặc đổi thứ tự tùy hàm khởi tạo

`TreeSet` có nhiều hàm khởi tạo. Hai chữ ký dễ bị đọc như nhau là:

~~~java
new TreeSet<>(aSortedSet);
new TreeSet<>(aCollection);
~~~

Chúng là mẫu khai báo để so sánh, với `aSortedSet` mang kiểu `SortedSet` và `aCollection` mang kiểu `Collection`.

Hàm nhận `SortedSet` giữ quy tắc thứ tự của nguồn. Hàm nhận `Collection` tạo tập theo thứ tự tự nhiên của phần tử. Đó là hai hợp đồng khác nhau, dù đối tượng nguồn thực tế có thể là cùng một `TreeSet`. [Java 17: các hàm khởi tạo TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html#%3Cinit%3E(java.util.SortedSet)).

Chương trình dùng:

~~~java
SortedSet<Integer> copySame = new TreeSet<>(reverse);
Collection<Integer> collectionInput = reverse;
SortedSet<Integer> copyNatural = new TreeSet<>(collectionInput);
~~~

`reverse` có kiểu khai báo `SortedSet<Integer>`, nên lời gọi đầu chọn hàm giữ thứ tự nguồn. `collectionInput` có kiểu khai báo `Collection<Integer>`, nên lời gọi sau chọn hàm tạo theo thứ tự tự nhiên.

Vì vậy, cùng thành viên được sao chép thành:

~~~text
sorted-set copy=[9, 7, 5, 2]
collection copy=[2, 5, 7, 9]
~~~

Điểm cần hiểu là lựa chọn hàm khởi tạo có nhiều chữ ký dựa trên thông tin kiểu khi biên dịch. Không suy rằng lớp thực tế của nguồn tự buộc mọi lời gọi sao chép đều giữ bộ so sánh.

Với đối tượng không có thứ tự tự nhiên, nhầm sang hàm nhận `Collection` còn có thể gây lỗi thay vì chỉ đổi trình tự. Khi quy tắc thứ tự là yêu cầu quan trọng, hãy truyền quy tắc rõ ràng hoặc giữ đúng kiểu nguồn cần thiết.

## 15. null, khả năng so sánh và dữ liệu có thể thay đổi

### 15.1. Chính sách null là một phần của thứ tự

Thứ tự tự nhiên của ví dụ không nhận `null`. Nếu cần hỗ trợ nó, một quy tắc như `Comparator.nullsFirst(Comparator.naturalOrder())` có thể định nghĩa `null` đứng trước phần tử khác. `nullsLast` diễn đạt lựa chọn ở cuối.

Điều này không có nghĩa mọi `Comparator` đều tự nhận `null`. Một bộ so sánh gọi `candidate.score()` sẽ lỗi nếu đối số `candidate` là `null` mà không có lớp bao xử lý phù hợp.

Cũng tách hai trường hợp: bản thân phần tử là `null` và một trường bên trong phần tử là `null`. Bọc bộ so sánh phần tử bằng `nullsFirst` không tự sửa mọi phép so sánh tên có giá trị `null` bên trong một đối tượng khác `null`. Phải định nghĩa chính sách ở đúng cấp dữ liệu.

### 15.2. Các phần tử phải so sánh được với nhau

Một tập theo thứ tự tự nhiên không chỉ yêu cầu “mỗi đối tượng có một phương thức tên `compareTo`”. Các phần tử trong tập phải so sánh được với nhau theo hợp đồng kiểu.

Sử dụng kiểu tổng quát đúng giúp ngăn nhiều trường hợp trộn dữ liệu sai. Đừng dùng kiểu thô để nhét cả `String` lẫn `Integer` vào cùng một tập rồi mong có thứ tự chung chỉ vì cả hai đều triển khai `Comparable` theo cách riêng.

Với `Comparator`, quy tắc phải chấp nhận mọi phần tử hợp lệ của miền dữ liệu bạn đưa vào. Nếu chỉ xử lý một số lớp con hoặc giả định một trường luôn có giá trị trong khi dữ liệu không bảo đảm, lỗi có thể xảy ra lúc thêm, tìm hoặc tạo vùng.

### 15.3. Đừng sửa khóa sắp xếp khi phần tử còn trong tập

Giả sử cây đang được tổ chức theo `score` của một đối tượng có thể sửa. Sau khi thêm đối tượng với điểm 50, bạn đổi trực tiếp trường đó thành 95. `TreeSet` không nhận một lời gọi `add/remove` để biết phải đặt lại phần tử vào đâu.

Kết quả có thể làm thứ tự quan sát và việc tìm kiếm không còn phù hợp với cấu trúc đã xây dựng. Tương tự, thay đổi một biến mà `Comparator` đang đọc cũng có thể làm quy tắc đổi ngầm.

Thiết kế đơn giản là dùng giá trị bất biến cho dữ liệu tham gia so sánh. Nếu phải cập nhật, thực hiện quy trình xóa trong trạng thái cũ rồi thêm giá trị mới dưới một hợp đồng rõ ràng; đừng sửa trước rồi giả định xóa chắc chắn còn tìm đúng vị trí. Hai kiểu record của chương trình giúp giữ ổn định dữ liệu dùng cho thứ tự.

## 16. Chi phí thuộc về cách triển khai, không phải chỉ tên interface

`SortedSet` là hợp đồng hành vi, không phải lời hứa mọi cách triển khai đều dùng cùng thuật toán. Với `TreeSet`, API công bố chi phí `O(log n)` cho `add`, `remove` và `contains`, trong đó `n` là số phần tử. Đó là cam kết của lớp đang dùng trong bài.

Chi phí của chính phép so sánh vẫn quan trọng. So sánh một số nguyên khác với so sánh nhiều trường chuỗi dài. Bộ so sánh gọi thao tác I/O hoặc đọc dữ liệu thay đổi liên tục cũng không phù hợp với một quy tắc thứ tự đơn giản, ổn định.

Đừng mô tả hoạt động của `TreeSet` như “mỗi lần thêm đều sắp xếp lại toàn bộ một danh sách”. Lớp duy trì cấu trúc phù hợp với thứ tự để tìm vị trí và thực hiện các thao tác trên tập. Bài học không cần dựa vào hình dạng nút cụ thể để sử dụng đúng API.

Nếu chỉ cần giữ dữ liệu rồi sắp một lần để hiển thị, một danh sách được sắp xếp có thể phù hợp hơn về ý nghĩa, nhất là khi cần giữ nhiều phần tử cùng khóa sắp xếp. Nếu cần một tập duy nhất luôn có thứ tự và các vùng theo thứ tự trong suốt quá trình cập nhật, `SortedSet` diễn đạt yêu cầu đó trực tiếp hơn.

Dữ liệu được sắp xếp không tự tạo ra tính an toàn khi nhiều luồng cùng sửa. `TreeSet` không được đồng bộ hóa sẵn. Đây là quyết định độc lập với việc dùng `Comparable` hay `Comparator`; chương trình của bài chỉ dùng một luồng.

## 17. Các lỗi thường gặp và cách nhận ra

| Lỗi suy luận | Dấu hiệu hoặc cách sửa |
| --- | --- |
| “TreeSet giữ thứ tự thêm” | Chèn 7 rồi 2 vẫn duyệt 2 trước; phân biệt thứ tự chèn với thứ tự so sánh |
| “Comparator chỉ làm đẹp kết quả in” | Kiểm tra thêm, tìm và loại trùng bằng những đối tượng khác nhau nhưng cùng khóa so sánh |
| “Bằng điểm thì vẫn giữ mọi người” | Nếu chỉ so sánh điểm, phép so sánh trả 0; bổ sung mô hình phân biệt phù hợp hoặc dùng danh sách |
| “Sau khi so sánh 0, TreeSet sẽ xét equals” | Không có bước phân biệt bổ sung đó |
| “compare phải trả đúng -1 hoặc 1” | Kiểm tra dấu của kết quả, không kiểm tra một độ lớn cố định |
| “Lấy hiệu hai số luôn là so sánh đúng” | Có nguy cơ tràn số; dùng `Integer.compare` hoặc hàm chuẩn |
| “first luôn là số nhỏ nhất về số học” | Đọc quy tắc hiện tại; với thứ tự ngược, first là số lớn nhất trong ví dụ |
| “comparator() trả null tức là không có thứ tự” | `null` ở đây biểu thị dùng thứ tự tự nhiên |
| “subSet là bản sao” | Thử thay đổi qua vùng và quan sát tập gốc |
| “TreeSet sao chép luôn giữ comparator” | Kiểm tra chữ ký hàm khởi tạo và kiểu khai báo của nguồn |
| “record tự biết cách sắp xếp” | Record có phép bằng nhau mặc định, không tự có `Comparable` |
| “Đổi trường sắp xếp thì cây tự cập nhật” | Dùng dữ liệu bất biến hoặc quy trình cập nhật qua cấu trúc |
| “Mọi lớp Comparable đều nhất quán với equals” | `BigDecimal` là phản ví dụ cần biết |
| “Set đã sắp xếp thì phù hợp mọi bảng xếp hạng” | Phải kiểm tra yêu cầu giữ nhiều bản ghi có cùng khóa và quy tắc duy nhất |

Khi kết quả thiếu phần tử, hãy in hoặc kiểm tra `compare(a, b)` của hai dữ liệu tưởng là khác nhau. Khi kết quả sai hướng, hãy kiểm tra phạm vi `reversed`. Khi truy vấn vùng bất ngờ, hãy đọc hai biên theo chính bộ so sánh thay vì trực giác số học.

## 18. Thực hành có lời giải

### Bài 1: thứ tự tự nhiên

Thêm lần lượt `8, 3, 8, 1, 5` vào một `TreeSet<Integer>` không truyền `Comparator`. Tập cuối và kết quả lần thêm số 8 thứ hai là gì?

**Lời giải:** Tập cuối là `[1, 3, 5, 8]`. Lần thêm số 8 thứ hai trả `false`. Thứ tự chèn không quyết định trình tự duyệt; lần xuất hiện trùng không tạo thành viên mới.

### Bài 2: đảo quy tắc

Dùng cùng dữ liệu nhưng tạo tập với `Comparator.reverseOrder()`. `first`, `last` và `headSet(5)` là gì?

**Lời giải:** Tập là `[8, 5, 3, 1]`. `first()` bằng 8, `last()` bằng 1. `headSet(5)` chứa phần đứng trước 5 theo quy tắc ngược, nên là `[8]`. Không tính biên 5.

### Bài 3: thiếu tiêu chí phân biệt

Bạn có `Candidate("An", 90)` và `Candidate("Binh", 90)`. Comparator chỉ so sánh điểm. Có thể dùng tập này để giữ đầy đủ danh sách thí sinh không?

**Lời giải:** Không nếu hai thí sinh đó phải là hai thành viên khác nhau. Phép so sánh trả 0 nên tập chỉ giữ một đại diện cho điểm 90. Cần xác định quy tắc duy nhất, thêm tiêu chí tương thích với mô hình hoặc chọn danh sách nếu muốn giữ mọi bản ghi.

### Bài 4: phạm vi reversed

Hai biểu thức “điểm giảm rồi tên tăng” và “điểm tăng rồi tên tăng, cuối cùng đảo toàn bộ” có cùng thứ tự không?

**Lời giải:** Không. Biểu thức thứ nhất chỉ đảo tiêu chí điểm trước khi ghép tên. Biểu thức thứ hai đảo cả hai tiêu chí đã ghép, làm tên cũng giảm khi điểm bằng nhau. Dùng hai thí sinh cùng điểm và tên khác nhau để kiểm tra sự khác biệt.

### Bài 5: khung nhìn theo vùng

Tập tự nhiên là `[1, 3, 5, 7]`. Tạo `view = set.subSet(2, 7)`, rồi gọi `view.remove(3)`. Sau đó tập gốc và vùng là gì? Có thể `view.add(7)` không?

**Lời giải:** Ban đầu vùng là `[3, 5]`. Xóa 3 qua vùng làm tập gốc thành `[1, 5, 7]` và vùng thành `[5]`. Không thêm 7 qua vùng vì biên cuối bị loại; lời gọi ném `IllegalArgumentException`. Việc tập gốc có 7 không nới giới hạn của vùng.

### Bài 6: record không có Comparable

Một record mới chứa `int id` và `String title`. Bạn tạo `new TreeSet<ThatRecord>()` không truyền bộ so sánh. Vì record có `equals`, `hashCode` và `toString`, nó có tự dùng được thứ tự tự nhiên không?

**Lời giải:** Không. Các phương thức đó không tạo `compareTo`. Cần để kiểu triển khai `Comparable` với quy tắc thích hợp hoặc truyền `Comparator` vào tập.

### Bài 7: comparator() và null

Một tập số có `comparator() == null`. Có thể suy rằng nó chưa được sắp xếp hoặc cho phép thêm phần tử `null` không?

**Lời giải:** Không. Giá trị trả về nói rằng tập dùng thứ tự tự nhiên. Chính sách với phần tử `null` là câu hỏi khác; `TreeSet<Integer>` theo thứ tự tự nhiên trong bài từ chối `null`.

### Bài 8: compareTo khác equals

Hai số `BigDecimal("2.0")` và `BigDecimal("2.00")` cho `compareTo` bằng 0 nhưng `equals` bằng `false`. Sau khi thêm cả hai vào `TreeSet` tự nhiên, có hai phần tử không?

**Lời giải:** Không. Phần tử thứ hai tương đương theo thứ tự nên không được thêm thành viên riêng. Nếu khác biệt biểu diễn quan trọng, cần mô hình và quy tắc lưu trữ phù hợp; không mặc định tập theo thứ tự tự nhiên giữ được cả hai.

### Bài 9: tiêu chí tự nhiên của Version

Với `Version` trong chương trình, thêm `2.3, 1.20, 2.1, 1.4`. Thứ tự nào xuất hiện?

**Lời giải:** `[1.4, 1.20, 2.1, 2.3]`. So sánh phần chính trước; chỉ khi phần chính bằng nhau mới so sánh phần phụ dưới dạng số. Không dùng chuỗi hiển thị để suy thứ tự của record.

## 19. Những câu hỏi cần trả lời trước khi chọn quy tắc

Trước khi đưa một kiểu dữ liệu mới vào `SortedSet`, hãy xác định:

- Thứ tự tự nhiên của kiểu có đúng yêu cầu không, hay đây là quy tắc riêng nên dùng `Comparator`?
- Hai đối tượng nào được phép so sánh bằng 0? Chúng có đồng thời bằng nhau theo `equals` không?
- Có những phần tử khác nhau cần giữ dù cùng điểm, cùng tên hoặc cùng thời điểm không?
- Tất cả phần tử và các giá trị biên có so sánh được với nhau không?
- Có cho phép `null` ở cấp phần tử hoặc ở từng trường dùng để so sánh không?
- Dữ liệu hoặc trạng thái mà quy tắc đang đọc có thể thay đổi khi phần tử còn trong tập không?
- Khi dùng `first`, `last` và các vùng, bạn đã đọc “trước/sau” theo đúng bộ so sánh chưa?
- Khi sao chép, bạn cần giữ quy tắc nguồn hay chủ động tạo một thứ tự khác?

Sau khi xác định quy tắc, hãy thử ít nhất một cặp bằng khóa chính nhưng khác khóa phụ. Cặp này thường làm lộ rõ nhất sự khác nhau giữa “sắp xếp bản ghi” và “quyết định bản ghi nào là cùng một thành viên”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sorted-set-002">TreeSet và cấu trúc cây tìm kiếm cân bằng</a>

<details>
<summary>Xem chi tiết</summary>

# TreeSet và cấu trúc cây tìm kiếm cân bằng

## 1. TreeSet giải quyết bài toán nào?

Một hệ thống quản lý các mốc thời gian cần giữ mỗi mốc một lần, duyệt theo thứ tự, kiểm tra một mốc đã có chưa, và tìm mốc gần nhất trước hoặc sau một giá trị. Dữ liệu còn được thêm và xóa trong lúc chương trình chạy.

Đó là tình huống phù hợp để cân nhắc `TreeSet`: tập hợp các phần tử được tổ chức theo một quan hệ thứ tự, có thể duy trì thứ tự khi dữ liệu thay đổi.

Ba ý cần tách biệt ngay từ đầu:

- **Tập hợp:** một giá trị được xem là đã có thì thêm lại không tạo thành viên mới.
- **Thứ tự:** các phần tử được duyệt theo thứ tự tự nhiên hoặc theo bộ so sánh đã chọn.
- **Cấu trúc cây cân bằng:** đường tìm kiếm được giữ ngắn, kể cả khi dữ liệu đầu vào vốn đã tăng dần.

“Được sắp xếp” không có nghĩa mỗi lần thêm một phần tử, TreeSet lấy toàn bộ dữ liệu ra và sắp xếp lại. Nó đặt phần tử vào một vị trí phù hợp trong cây, rồi điều chỉnh cấu trúc cục bộ khi cần.

Theo [tài liệu TreeSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html), TreeSet là một triển khai `NavigableSet` dựa trên `TreeMap`; ba thao tác cơ bản `add`, `remove`, `contains` có bảo đảm chi phí logarit. [TreeMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html) dùng cây đỏ–đen, một dạng cây tìm kiếm nhị phân tự cân bằng.

Bài này đi từ quy tắc tìm kiếm trong cây đến lý do phải cân bằng, sau đó nối cơ chế ấy với hành vi và chi phí của TreeSet. Không cần tự viết một cây đỏ–đen để dùng TreeSet, nhưng hiểu cấu trúc giúp chọn đúng API và tránh suy luận sai về hiệu năng.

## 2. Mối liên hệ giữa Set, SortedSet và NavigableSet

Có thể đọc các giao diện theo khả năng mà chúng bổ sung:

```text
Set<E>
    thành viên và tính duy nhất

SortedSet<E>
    thêm một thứ tự trên các phần tử

NavigableSet<E>
    thêm cách tìm phần tử lân cận và các biên

TreeSet<E>
    triển khai cụ thể dựa trên cây tìm kiếm cân bằng
```

TreeSet không phải List đã sắp xếp. Nó không có thao tác `get(index)` để lấy “phần tử thứ i” như List. Một cây có thứ tự không đương nhiên có thông tin đếm số phần tử ở từng cây con để hỗ trợ truy cập theo vị trí.

Một cách khai báo thường dùng là:

```java
// Trích đoạn; cần import java.util.NavigableSet và java.util.TreeSet.
NavigableSet<Integer> milestones = new TreeSet<>();
milestones.add(30);
milestones.add(10);
milestones.add(20);
```

Biến dùng giao diện `NavigableSet` vì mã gọi cần khả năng điều hướng theo thứ tự. Đối tượng thật là TreeSet, nên cách hiện thực và chi phí đến từ TreeSet.

Nếu chỉ khai báo biến là `Set<Integer>`, đối tượng vẫn là TreeSet và vẫn giữ thứ tự, nhưng các phương thức điều hướng không nằm trên kiểu `Set`. Chọn kiểu biến theo khả năng mà phần mã sử dụng cần biểu đạt, thay vì luôn chọn lớp cụ thể hoặc luôn thu hẹp đến mức mất thông tin cần thiết.

## 3. Quy tắc của cây tìm kiếm nhị phân

Một nút của cây tìm kiếm nhị phân có thể có cây con trái và cây con phải. Với một thứ tự hợp lệ, quy tắc cần đúng cho **toàn bộ cây con**, không chỉ hai nút con ngay cạnh:

```text
mọi giá trị ở cây con trái < giá trị tại nút < mọi giá trị ở cây con phải
```

Dấu `<` ở đây là “đứng trước theo quan hệ so sánh”, không nhất thiết là nhỏ hơn về số học. Nếu dùng thứ tự giảm dần, một số lớn hơn có thể đứng trước một số nhỏ hơn.

Ví dụ minh họa theo thứ tự số nguyên tăng dần:

```text
             40
           /             20      60
        /      /        10   30  50   70
```

Đây là một hình cây minh họa hợp lệ, không phải ảnh chụp nội bộ của một TreeSet sau một chuỗi thao tác cụ thể.

### 3.1. Tìm 30

Bắt đầu ở gốc:

```text
30 so với 40: đứng trước -> sang trái
30 so với 20: đứng sau   -> sang phải
30 so với 30: bằng nhau  -> tìm thấy
```

Không cần xem các nút 50, 60, 70. Quy tắc thứ tự cho phép loại bỏ cả một cây con sau mỗi phép so sánh.

### 3.2. Tìm 35

```text
35 so với 40: sang trái
35 so với 20: sang phải
35 so với 30: sang phải
không có nút ở nhánh tiếp theo -> không tìm thấy
```

Nếu muốn thêm 35, vị trí nhánh trống vừa tìm được là một ứng viên phù hợp theo quy tắc tìm kiếm. Sau khi thêm, cơ chế cân bằng có thể thay đổi hình dạng cây, nhưng phải tiếp tục giữ đúng quan hệ thứ tự.

### 3.3. Giá trị so sánh bằng 0 có vai trò đặc biệt

Với TreeSet, việc gặp kết quả so sánh bằng `0` có nghĩa đã gặp một thành viên tương đương theo thứ tự. `add` không đi tiếp để lưu một nút riêng chỉ vì đối tượng mới khác tham chiếu.

Đó là lý do bộ so sánh vừa điều khiển đường đi trong cây, vừa ảnh hưởng tới tính duy nhất của TreeSet. Bộ so sánh không chỉ được gọi ở thời điểm in dữ liệu.

## 4. Vì sao cây tìm kiếm thông thường chưa đủ?

Cây tìm kiếm nhị phân đúng quy tắc thứ tự vẫn có thể rất cao. Nếu chỉ chèn theo vị trí tìm được mà không điều chỉnh, dãy tăng dần `1, 2, 3, 4, 5, 6, 7` có thể tạo ra:

```text
1
   2
       3
           4
               5
                   6
                       7
```

Tìm phần tử lớn nhất hoặc một giá trị lớn hơn mọi phần tử phải đi gần như qua cả chuỗi. Khi cây có `n` nút, đường đi có thể dài theo `n`, dẫn đến chi phí `O(n)`.

Một hình cân đối hơn với cùng nội dung là:

```text
          4
        /          2     6
      /    /      1   3 5   7
```

Cả hai hình đều có thể cho cùng kết quả duyệt tăng dần:

```text
1, 2, 3, 4, 5, 6, 7
```

Do đó, chỉ kiểm tra dữ liệu in ra đã sắp xếp không chứng minh cây được cân bằng. Thứ tự nội dung và chiều cao cấu trúc là hai thuộc tính khác nhau.

Cây tự cân bằng bổ sung các quy tắc để ngăn đường tìm kiếm suy biến thành một chuỗi dài. TreeSet duy trì điều đó khi thêm và xóa; người gọi không phải xáo trộn dữ liệu đầu vào để “giúp cây cân”.

Cũng không nên hiểu cân bằng là hai cây con của mọi nút luôn có đúng cùng số phần tử, hoặc mọi lá luôn ở cùng độ sâu. Những điều kiện ấy quá chặt và không phải điều TreeSet cần giữ.

## 5. Cây đỏ–đen giữ chiều cao thấp bằng cách nào?

Cây đỏ–đen vẫn là cây tìm kiếm nhị phân. Mỗi nút có thêm thông tin màu để hỗ trợ các bất biến cân bằng.

Một cách mô tả chuẩn gồm:

1. Mỗi nút mang màu đỏ hoặc đen.
2. Gốc là đen.
3. Các lá rỗng quy ước, thường ký hiệu `NIL`, là đen.
4. Nút đỏ không có con đỏ.
5. Từ một nút đến các lá rỗng phía dưới, mọi đường đều có cùng số nút đen theo cùng quy ước đếm.

`NIL` là lá rỗng dùng trong mô hình thuật toán; nó không phải phần tử `null` do người dùng thêm vào Set. Trong triển khai Java, liên kết rỗng có thể được biểu diễn bằng tham chiếu `null`, không cần cấp phát một đối tượng lá riêng cho mọi nhánh.

Thông tin đỏ và đen là metadata của cấu trúc cây. Nó không quyết định thứ tự giá trị và không xuất hiện trong kết quả iterator.

### 5.1. Đường dài không thể dài tùy ý

Do không có hai nút đỏ liên tiếp, giữa các nút đen không thể chen một chuỗi đỏ dài. Mặt khác, mọi đường từ cùng một nút xuống lá rỗng có cùng số nút đen.

Kết hợp hai điều kiện, một đường có thêm nút đỏ vẫn không thể dài gấp tùy ý đường khác. Chiều cao bị khống chế theo logarit của số nút.

Có thể nhìn ý tưởng qua các đường tượng trưng:

```text
đường ngắn hơn:  đen -> đen -> đen -> NIL
đường dài hơn:   đen -> đỏ -> đen -> đỏ -> đen -> NIL
```

Sơ đồ chỉ minh họa ý tưởng chen nút đỏ giữa các mức đen, không khẳng định mọi cây đều có đúng hai đường như thế này.

### 5.2. Một cận chiều cao thường dùng

Với `n >= 1` nút có dữ liệu, cây đỏ–đen có cận:

```text
h <= 2 * log2(n + 1)
```

Ở đây có thể lấy `h` là số cạnh trên đường dài nhất từ gốc tới lá rỗng quy ước. Đổi một quy ước chiều cao khác có thể làm lệch một hằng số nhỏ, nhưng không thay đổi kết luận `O(log n)`.

Ý tưởng chứng minh ngắn:

- Gọi `b` là số nút đen từ sau gốc đến lá rỗng trên mỗi đường.
- Một cây con có chiều cao đen `b` cần ít nhất `2^b - 1` nút có dữ liệu; có thể thấy bằng quy nạp trên hai cây con.
- Vì vậy, `b <= log2(n + 1)`.
- Không có hai nút đỏ liên tiếp nên `h <= 2b`.
- Suy ra cận chiều cao trên.

Điều cần ghi nhớ khi sử dụng collection là: đường tìm kiếm vẫn ngắn theo logarit ngay cả với dữ liệu đầu vào có thứ tự. Không cần dùng công thức để dự đoán chính xác gốc hoặc số phép so sánh của từng lần tìm.

### 5.3. Cân bằng theo chiều cao, không theo hình vẽ đẹp

Một cây đỏ–đen hợp lệ có thể trông lệch về một phía trong hình vẽ. Miễn các bất biến vẫn giữ đúng, chiều cao vẫn nằm trong cận cần thiết.

Nó cũng không phải cây AVL: hai họ cấu trúc dùng tiêu chí cân bằng khác nhau. Khi học TreeSet, nên hiểu chính cơ chế đỏ–đen được TreeMap dùng, thay vì gộp mọi “cây cân bằng” thành một thuật toán giống nhau.

## 6. Phép quay và đổi màu không làm mất thứ tự

Sau thêm hoặc xóa, cây có thể tạm thời vi phạm quy tắc màu. Cơ chế sửa dùng đổi màu và các phép quay cục bộ.

### 6.1. Quay trái

Một phép quay trái có thể minh họa như sau:

```text
Trước quay:                 Sau quay:

      x                          y
     /                         /     A   y                      x   C
       /                     /       B   C                  A   B
```

Trong hình, `A`, `B`, `C` là các cây con, có thể rỗng. Để hình ban đầu là cây tìm kiếm hợp lệ, các giá trị có quan hệ:

```text
A < x < B < y < C
```

Sau quay, duyệt theo thứ tự vẫn cho:

```text
A, x, B, y, C
```

Phép quay đổi quan hệ cha–con và vị trí gốc của vùng nhỏ ấy. Nó không cần sắp xếp lại toàn bộ các phần tử.

### 6.2. Quay phải

Quay phải là thao tác đối xứng:

```text
Trước quay:                 Sau quay:

        y                        x
       /                       /       x   C                    A   y
     /                           /     A   B                        B   C
```

Một phép quay điều chỉnh một số lượng liên kết cố định nên có chi phí cục bộ `O(1)`. Nhưng một thao tác thêm hoặc xóa còn phải tìm vị trí và có thể xử lý các mức tổ tiên. Không được từ “mỗi phép quay là O(1)” suy ra “thêm phần tử là O(1)”.

### 6.3. Đổi màu giải quyết điều gì?

Đổi màu giữ nguyên các liên kết nhưng điều chỉnh cách các mức đen và đỏ phân bố. Tùy tình huống, cơ chế sửa có thể:

- Chỉ cần đổi màu rồi tiếp tục xem xét tổ tiên.
- Quay để đổi hình dạng cục bộ, kết hợp đổi màu.
- Dừng khi các bất biến đã được khôi phục.

Quay đúng quy tắc tìm kiếm chưa đủ để tự bảo đảm mọi quy tắc đỏ–đen. Ngược lại, chỉ đổi màu cũng không giải quyết được mọi trường hợp. Hai công cụ phối hợp trong thuật toán cân bằng.

[Mã nguồn TreeMap của OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/TreeMap.java) có các phần sửa sau chèn, sửa sau xóa và quay trái/phải. Đây là chi tiết triển khai để kiểm tra cơ chế; ứng dụng dùng TreeSet không gọi trực tiếp các hàm ấy.

## 7. Thêm và xóa liên hệ thế nào với cây cân bằng?

### 7.1. Khi thêm

Có thể hình dung quy trình ở mức ý tưởng:

```text
giá trị mới
    -> so sánh từ gốc, đi trái hoặc phải
    -> nếu gặp so sánh bằng 0: đã có thành viên tương đương
    -> nếu gặp vị trí trống: tạo mục mới
    -> sửa các bất biến màu và hình dạng khi cần
    -> hoàn tất
```

Trong thuật toán đỏ–đen thông dụng, nút mới có thể bắt đầu bằng màu đỏ để không tự tăng số nút đen trên riêng đường mới. Nếu cha cũng đỏ, cần sửa vi phạm đỏ–đỏ; gốc cuối cùng phải đen.

Không phải mọi lần thêm đều gây quay. Một số lần chỉ thêm hợp lệ, một số lần cần đổi màu, một số lần cần quay. Không xây dựng mã ứng dụng dựa trên số phép quay dự kiến cho một dãy đầu vào.

### 7.2. Khi xóa

Đầu tiên cần tìm nút tương ứng theo thứ tự. Việc gỡ liên kết phụ thuộc nút có bao nhiêu con:

- Không có con: xử lý một lá có dữ liệu.
- Có một con: nối lại qua nút con phù hợp.
- Có hai con: có thể dùng phần tử kế tiếp theo thứ tự để chuyển bài toán về trường hợp đơn giản hơn.

Nếu việc xóa làm mất một nút đen trên riêng một đường, thuật toán phải khôi phục điều kiện số nút đen bằng nhau. Quá trình sửa có thể xét nút anh em và tổ tiên, đổi màu hoặc quay.

Nội dung quan trọng khi dùng API là: xóa không chỉ “đặt một ô thành rỗng”, và sau khi xóa, cây vẫn phải giữ điều kiện phục vụ các lần tìm tiếp theo. Chi phí được tính cho cả tìm nút lẫn điều chỉnh cấu trúc.

Không cần ghi nhớ mọi nhánh thuật toán sửa xóa để sử dụng đúng TreeSet. Nếu muốn tự triển khai cây đỏ–đen như bài tập cấu trúc dữ liệu, đó là một nhiệm vụ riêng, cần kiểm thử các bất biến sau từng cập nhật.

### 7.3. Xoay cây không đổi ý nghĩa của Set

Sau cân bằng:

- Những thành viên hiện có vẫn là những thành viên ấy, trừ phần tử vừa thêm hoặc xóa theo yêu cầu.
- Thứ tự duyệt vẫn phù hợp với quan hệ so sánh.
- Gốc và cha–con có thể đã thay đổi.
- Người gọi không nhận được một chỉ số vị trí ổn định cho mỗi phần tử.

Do đó, TreeSet cung cấp thứ tự quan sát được qua API, không cung cấp hợp đồng về hình dạng cây nội bộ.

## 8. Chương trình 1: quan sát các khả năng cơ bản

Lưu tệp `TreeSetBasicsDemo.java`:

```java
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public class TreeSetBasicsDemo {
    public static void main(String[] args) {
        List<Integer> input = List.of(40, 10, 30, 20, 10, 50);
        NavigableSet<Integer> values = new TreeSet<>();

        for (int value : input) {
            values.add(value);
        }

        System.out.println("input = " + input);
        System.out.println("ascending = " + values);
        System.out.println("add duplicate 20 = " + values.add(20));
        System.out.println("contains 30 = " + values.contains(30));
        System.out.println("remove 30 = " + values.remove(30));
        System.out.println("after remove = " + values);
        System.out.println("first = " + values.first());
        System.out.println("last = " + values.last());
        System.out.println("lower 25 = " + values.lower(25));
        System.out.println("floor 20 = " + values.floor(20));
        System.out.println("ceiling 25 = " + values.ceiling(25));
        System.out.println("higher 40 = " + values.higher(40));
        System.out.println("descending = " + values.descendingSet());
        System.out.println("poll first = " + values.pollFirst());
        System.out.println("poll last = " + values.pollLast());
        System.out.println("remaining = " + values);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 TreeSetBasicsDemo.java
java TreeSetBasicsDemo
```

Kết quả của lần chạy kiểm chứng:

```text
input = [40, 10, 30, 20, 10, 50]
ascending = [10, 20, 30, 40, 50]
add duplicate 20 = false
contains 30 = true
remove 30 = true
after remove = [10, 20, 40, 50]
first = 10
last = 50
lower 25 = 20
floor 20 = 20
ceiling 25 = 40
higher 40 = 50
descending = [50, 40, 20, 10]
poll first = 10
poll last = 50
remaining = [20, 40]
```

Mã dùng ngôn ngữ và API có ở Java 17 trở lên. Các chương trình trong bài được biên dịch với `--release 17` và chạy kiểm chứng bằng Azul OpenJDK 24.0.2.

### 8.1. Thứ tự đầu vào và thứ tự duyệt

Đầu vào là `40, 10, 30, 20, 10, 50`. Kết quả TreeSet là `10, 20, 30, 40, 50`: tăng dần theo thứ tự tự nhiên của Integer, và chỉ có một giá trị `10`.

Một iterator của TreeSet đi theo thứ tự, không đi theo thứ tự chèn. Kết quả này cũng không có nghĩa các giá trị nằm liên tiếp trong một mảng nội bộ.

### 8.2. Các thao tác tìm và xóa

`contains(30)` và `remove(30)` tìm theo quan hệ so sánh. Sau khi xóa, các phần tử còn lại vẫn được duyệt đúng thứ tự.

Thêm lại `20` trả về `false` vì đã có thành viên tương đương. Đó là sự khác nhau giữa Set với một List cho phép giữ nhiều lần xuất hiện.

### 8.3. Tìm phần tử lân cận

Sau khi xóa 30, nội dung là `[10, 20, 40, 50]`:

| Lời gọi | Điều kiện tìm | Kết quả |
|---|---|---|
| `lower(25)` | Lớn nhất nhưng đứng trước 25 | 20 |
| `floor(20)` | Lớn nhất nhưng không đứng sau 20 | 20 |
| `ceiling(25)` | Nhỏ nhất nhưng không đứng trước 25 | 40 |
| `higher(40)` | Nhỏ nhất nhưng đứng sau 40 | 50 |

Cách nói “trước” và “sau” giúp nhớ rằng các khái niệm này phụ thuộc thứ tự của Set. Với bộ so sánh đảo chiều, không nên suy luận bằng trục số tăng dần rồi bỏ qua comparator.

Những khả năng này liên hệ trực tiếp với cấu trúc cây có thứ tự: trong lúc tìm, chương trình có thể ghi nhận ứng viên biên phù hợp và tiếp tục đi vào cây con cần thiết.

### 8.4. Hai đầu và góc nhìn đảo chiều

`first` và `last` xem hai đầu theo thứ tự. `pollFirst` và `pollLast` vừa lấy vừa gỡ phần tử ở đầu tương ứng.

Trong ví dụ, `descendingSet()` cho một góc nhìn theo thứ tự đảo ngược, không phải yêu cầu sắp xếp rồi sao chép toàn bộ dữ liệu thành một TreeSet độc lập. Việc cập nhật qua các góc nhìn có liên hệ với tập gốc; không coi đó là bản chụp tách biệt.

Với Set rỗng, `first` và `last` ném `NoSuchElementException`; các phép thăm dò lân cận hoặc `pollFirst`/`pollLast` trả về `null` khi không có kết quả. Hãy chọn API theo việc thiếu dữ liệu là tình huống bình thường hay một vi phạm giả định của luồng xử lý.

## 9. Chi phí: phải biết đang đếm điều gì

Gọi `n` là số thành viên hiện có trong một TreeSet đầy đủ, và trước hết giả sử một lần so sánh có chi phí hằng số.

| Thao tác hoặc quá trình | Chi phí cần hiểu | Cơ sở |
|---|---|---|
| `contains` | `O(log n)` | Đi theo một đường tìm kiếm của cây cân bằng |
| `add` | `O(log n)` | Tìm vị trí và duy trì cân bằng |
| `remove` | `O(log n)` | Tìm mục và duy trì cân bằng khi xóa |
| `size` trên TreeSet đầy đủ | `O(1)` trong triển khai TreeMap | Có số lượng thành viên được duy trì |
| Duyệt toàn bộ một lần | `O(n)` về số nút được thăm | Lần lượt đi qua các thành viên theo thứ tự |
| Thêm n giá trị bằng các lời gọi add thông thường | Cận tổng `O(n log n)` | Cộng chi phí cập nhật tăng dần quy mô |
| Bộ nhớ lưu n thành viên | `O(n)` cho cấu trúc lưu trữ | Cần các mục và liên kết cây |

Bảng không khẳng định rằng mọi phương thức của mọi góc nhìn con đều có chi phí giống hệt TreeSet đầy đủ. Ví dụ, không lấy phân tích `size` của cấu trúc gốc để suy ra tự động chi phí `size` của mọi khoảng nhìn.

### 9.1. O(log n) là cận tăng trưởng, không phải một số bước chính xác

Một lần tìm có thể thấy ngay giá trị ở gốc và chỉ cần rất ít so sánh. Lần khác phải đi gần tới lá. Cận logarit mô tả quy mô đường tìm khi `n` tăng, không yêu cầu mọi lời gọi có đúng `log2(n)` phép so sánh.

Cân bằng đỏ–đen cũng không phải tìm kiếm nhị phân trên mảng: cây có các liên kết và hình dạng động; mảng đã sắp xếp dùng truy cập theo chỉ số để chọn điểm giữa. Hai cách đều có đường tìm logarit nhưng chi phí cập nhật và cách bố trí bộ nhớ khác nhau.

### 9.2. Phải cộng chi phí của comparator

Nếu một lần so sánh tốn `C`, phần tìm kiếm có thể được mô tả theo dạng:

```text
O(C * log n)
```

Với so sánh số nguyên đơn giản, coi `C` là hằng số thường phù hợp. Với chuỗi dài, nhiều tiêu chí, tính toán suy ra phức tạp hoặc dữ liệu ngoài bộ nhớ, chi phí mỗi lần so sánh có thể đáng kể.

Không đặt tác vụ chậm hoặc có tác dụng phụ như truy vấn dịch vụ, ghi dữ liệu hay thay đổi phần tử vào comparator. Comparator cần biểu đạt quan hệ thứ tự ổn định; TreeSet có thể gọi nó nhiều lần trong các thao tác khác nhau.

### 9.3. Bộ nhớ và tính cục bộ

Một cây dùng các mục và liên kết thay vì chỉ một dãy tham chiếu liên tiếp. Điều này có chi phí bộ nhớ và việc đi theo liên kết. Không thể chỉ nhìn `O(log n)` rồi kết luận TreeSet luôn chạy nhanh hơn mọi cấu trúc khác.

Nếu dữ liệu được xây một lần rồi đọc rất nhiều, một List sắp xếp có thể là lựa chọn đáng cân nhắc. Nếu chủ yếu kiểm tra thành viên mà không cần thứ tự, cấu trúc băm có thể phù hợp hơn. TreeSet đáng giá khi thứ tự và cập nhật động cùng phục vụ yêu cầu thực tế.

Không nêu một số byte cố định cho mỗi phần tử nếu chưa xác định JVM, chế độ tham chiếu và cách đo. Phân tích `O(n)` cho bộ nhớ không thay thế việc đo dung lượng trong môi trường cụ thể.

## 10. Chương trình 2: dữ liệu tăng dần không làm cây biến thành chuỗi

Chương trình dưới đây thêm các số theo đúng thứ tự tăng dần, rồi tìm một số lớn hơn tất cả số đã thêm. Comparator đếm số lần so sánh, nhưng vẫn luôn trả về kết quả của `Integer.compare`.

Lưu tệp `TreeSetSearchCostDemo.java`:

```java
import java.util.Comparator;
import java.util.TreeSet;

public class TreeSetSearchCostDemo {
    static final class CountingComparator implements Comparator<Integer> {
        private int calls;

        @Override
        public int compare(Integer left, Integer right) {
            calls++;
            return Integer.compare(left, right);
        }

        void reset() {
            calls = 0;
        }

        int calls() {
            return calls;
        }
    }

    static int ceilLog2(int value) {
        if (value <= 1) {
            return 0;
        }
        return 32 - Integer.numberOfLeadingZeros(value - 1);
    }

    public static void main(String[] args) {
        for (int n : new int[] {31, 127, 511, 2047}) {
            CountingComparator order = new CountingComparator();
            TreeSet<Integer> values = new TreeSet<>(order);

            for (int value = 0; value < n; value++) {
                values.add(value);
            }

            order.reset();
            boolean found = values.contains(n);
            int comparisons = order.calls();
            int conservativeBound = 2 * ceilLog2(n + 1);

            System.out.println("n=" + n
                    + ", found=" + found
                    + ", comparisons=" + comparisons
                    + ", bound=" + conservativeBound
                    + ", withinBound=" + (comparisons <= conservativeBound));
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 TreeSetSearchCostDemo.java
java TreeSetSearchCostDemo
```

Kết quả của lần chạy kiểm chứng:

```text
n=31, found=false, comparisons=8, bound=10, withinBound=true
n=127, found=false, comparisons=12, bound=14, withinBound=true
n=511, found=false, comparisons=16, bound=18, withinBound=true
n=2047, found=false, comparisons=20, bound=22, withinBound=true
```

Bộ đếm được đặt lại **sau khi xây Set**, nên các con số chỉ thuộc về một lần `contains(n)`, không bao gồm quá trình thêm dữ liệu.

### 10.1. Đọc kết quả đúng mức

Trong lần chạy này, số phần tử tăng từ 31 lên 2047, còn số so sánh của phép tìm đang đo tăng từ 8 lên 20. Nếu là cây tìm kiếm ngây thơ suy biến sau chèn tăng dần, đường tìm một số lớn hơn tất cả phần tử có thể đi qua toàn bộ chuỗi.

Cột `bound` là `2 * ceil(log2(n + 1))`, một cận bảo thủ dựa trên ý tưởng chiều cao đỏ–đen. Với các kích thước trong chương trình, `n + 1` là lũy thừa của 2 nên phép làm tròn không gây khó đọc.

Bộ đếm này không đo nano giây và không chứng minh TreeSet nhanh hơn một collection khác. Nó chỉ quan sát số lần comparator được gọi ở một kịch bản cụ thể.

### 10.2. Không đóng đinh con số vào hợp đồng API

Các giá trị 8, 12, 16, 20 là output đã kiểm chứng của môi trường chạy trong bài. Hình dạng cây, đường đi cụ thể và số lần gọi comparator có thể phụ thuộc lịch sử cập nhật hoặc triển khai.

Khi kiểm thử ứng dụng, không viết yêu cầu “TreeSet có 511 phần tử thì contains phải gọi comparator đúng 16 lần”. Yêu cầu đáng bảo vệ là kết quả thành viên đúng, thứ tự đúng, và lựa chọn cấu trúc phù hợp với bảo đảm của nó.

Một số phép đo hữu hạn cũng không phải chứng minh toán học cho `O(log n)`. Cơ sở tổng quát đến từ bất biến cân bằng và phân tích chiều cao; chương trình giúp nối lý thuyết với một quan sát có thể tái hiện.

### 10.3. Vì sao bộ đếm không phá thứ tự?

Comparator này có một trường đếm thay đổi, nhưng trường ấy không tham gia quyết định kết quả so sánh. Với cùng hai giá trị, kết quả thứ tự vẫn giữ nguyên.

Điều này khác với một comparator dùng số lần gọi để luân phiên trả về lớn hơn hoặc nhỏ hơn. Comparator như vậy không biểu đạt một thứ tự ổn định và không thể dùng làm nền tảng hợp lệ cho cây tìm kiếm.

## 11. Bộ so sánh là một phần của cấu trúc, không phải tùy chọn trang trí

TreeSet có hai cách phổ biến để xác định thứ tự:

```java
// Trích đoạn minh họa; cần import các kiểu java.util tương ứng.
TreeSet<Integer> natural = new TreeSet<>();

TreeSet<Integer> descending =
        new TreeSet<>(Comparator.reverseOrder());
```

Ở trường hợp đầu, phần tử cần hỗ trợ thứ tự tự nhiên phù hợp, thường qua `Comparable`. Ở trường hợp sau, comparator đã cung cấp quy tắc.

Trong cả hai trường hợp, cùng quy tắc phải được dùng nhất quán cho mọi lần đi trái, đi phải hoặc kết luận tương đương.

Theo [hợp đồng Comparator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html#compare(T,T)), dấu kết quả phải đảo đúng khi đổi vị trí hai đối số, quan hệ phải có tính bắc cầu, và hai giá trị so sánh bằng 0 phải có quan hệ thứ tự nhất quán đối với giá trị thứ ba.

Nếu một comparator tạo chu trình kiểu `A < B`, `B < C` nhưng `C < A`, việc loại bỏ cả cây con sau một phép so sánh không còn có cơ sở. Cây có thể vẫn có hình dạng thấp nhưng không cung cấp ngữ nghĩa tìm kiếm đúng theo một thứ tự hợp lệ.

Đây là hai trục cần giữ riêng:

```text
cân bằng hình dạng -> đường đi ngắn
thứ tự hợp lệ     -> đường đi có ý nghĩa
```

Một cây cân bằng không thể sửa một comparator sai.

### 11.1. So sánh bằng 0 và equals

Đối với TreeSet, `compare(a, b) == 0` hoặc `a.compareTo(b) == 0` xác định hai phần tử tương đương để lưu thành viên.

Muốn tuân thủ hợp đồng Set nói chung, quan hệ tương đương đó cần phù hợp với `equals`. Nếu không phù hợp, hành vi theo thứ tự của TreeSet vẫn có thể rõ ràng, nhưng cách hiểu “duy nhất theo equals” của Set không còn được đáp ứng.

Vì vậy, nếu thêm một đối tượng mới mà `add` trả về `false`, cần hỏi comparator có xem nó bằng một thành viên đã có hay không. Không chỉ kiểm tra `equals` rồi kết luận cây làm mất dữ liệu.

### 11.2. Không so sánh int bằng phép trừ tùy tiện

Một cách dễ sai là:

```java
// Không dùng để so sánh int tổng quát: hiệu có thể tràn.
(left, right) -> left - right
```

Hãy dùng:

```java
// Trích đoạn: so sánh int không dựa vào hiệu có thể tràn.
Integer::compare
```

Hoặc dùng các hàm như `Comparator.comparingInt` khi so sánh một thuộc tính. Quan hệ thứ tự là nền tảng tìm kiếm, nên lỗi tràn trong comparator không chỉ làm thứ tự in ra hơi khác; nó có thể phá giả định đi nhánh.

## 12. Chương trình 3: thứ tự quyết định thành viên nào được giữ

Lưu tệp `TreeSetComparisonDemo.java`:

```java
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class TreeSetComparisonDemo {
    record Entry(int priority, String name) {
    }

    public static void main(String[] args) {
        TreeSet<String> words = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        System.out.println("add java = " + words.add("java"));
        System.out.println("add JAVA = " + words.add("JAVA"));
        System.out.println("String equals = " + "java".equals("JAVA"));
        System.out.println("contains JaVa = " + words.contains("JaVa"));
        System.out.println("case-insensitive members = " + words);

        TreeSet<String> natural = new TreeSet<>(List.of("java", "JAVA"));
        System.out.println("natural members = " + natural);

        Comparator<Entry> byPriorityThenName =
                Comparator.comparingInt(Entry::priority)
                        .thenComparing(Entry::name);
        TreeSet<Entry> entries = new TreeSet<>(byPriorityThenName);
        entries.add(new Entry(2, "B"));
        entries.add(new Entry(1, "A"));
        entries.add(new Entry(2, "A"));

        System.out.println("entry count = " + entries.size());
        for (Entry entry : entries) {
            System.out.println("entry = "
                    + entry.priority() + ":" + entry.name());
        }
        System.out.println("add equal entry = "
                + entries.add(new Entry(2, "A")));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 TreeSetComparisonDemo.java
java TreeSetComparisonDemo
```

Kết quả của lần chạy kiểm chứng:

```text
add java = true
add JAVA = false
String equals = false
contains JaVa = true
case-insensitive members = [java]
natural members = [JAVA, java]
entry count = 3
entry = 1:A
entry = 2:A
entry = 2:B
add equal entry = false
```

### 12.1. Chuỗi không phân biệt hoa–thường

Comparator `String.CASE_INSENSITIVE_ORDER` xem `"java"` và `"JAVA"` tương đương trong so sánh. Vì thế:

- `add("java")` lần đầu thành công.
- `add("JAVA")` trả về `false`.
- `contains("JaVa")` trả về `true`.
- Chuỗi ban đầu `"java"` vẫn là đại diện đang được giữ.

Trong khi đó, `String.equals` phân biệt hoa–thường. Ví dụ này cố ý cho thấy sự không nhất quán giữa quan hệ thứ tự và equals; không nên che điều đó khi thiết kế API nhận một Set.

TreeSet theo thứ tự tự nhiên giữ cả `"JAVA"` và `"java"` vì thứ tự tự nhiên của String phân biệt hai giá trị ấy.

### 12.2. Tiêu chí phụ giúp biểu đạt đúng sự khác nhau

Record `Entry` có hai thành phần `priority` và `name`. Comparator xét độ ưu tiên trước, sau đó xét tên. Vì vậy, `Entry(2, "A")` và `Entry(2, "B")` không bị gộp.

Với các tên khác `null` trong ví dụ, so sánh bằng 0 khi cả hai thành phần bằng nhau, phù hợp với quy tắc bằng nhau của record.

Nếu chỉ dùng:

```java
// Trích đoạn: chỉ phù hợp nếu mỗi priority chỉ được giữ một đại diện.
Comparator<Entry> onlyPriority =
        Comparator.comparingInt(Entry::priority);
```

thì hai Entry cùng độ ưu tiên sẽ tương đương theo TreeSet, dù tên khác nhau. Đây không phải vấn đề cây chưa đủ cân bằng; đó là quan hệ thành viên đã được lập trình.

Không thêm tiêu chí phụ một cách máy móc chỉ để “không mất phần tử”. Phải chọn tiêu chí phản ánh đúng sự khác nhau mà mô hình muốn giữ. Nếu cần nhiều lần xuất hiện của cùng một giá trị theo đúng mọi thành phần, Set vốn không phải cấu trúc biểu đạt số lần xuất hiện.

## 13. Duy trì thứ tự khi dữ liệu thay đổi

TreeSet chỉ có thể tổ chức đúng khi dữ liệu dùng so sánh ổn định trong thời gian phần tử nằm trong cây.

Giả sử đối tượng công việc được đặt trong cây theo `priority`. Sau khi thêm, mã gọi sửa `priority` qua một setter. Đối tượng vẫn ở vị trí được chọn theo giá trị cũ, trong khi các lần tìm tiếp theo dùng giá trị mới.

Cân bằng đỏ–đen không tự theo dõi setter. Các quy tắc màu có thể vẫn đúng về hình dạng, nhưng quy tắc thứ tự giá trị đã sai. Những hậu quả có thể gồm duyệt không còn đúng thứ tự mong đợi, tra cứu không thấy hoặc xóa không đúng kỳ vọng.

Một hướng thiết kế tốt là:

- Dùng giá trị bất biến làm phần tử khi toàn bộ giá trị xác định thứ tự.
- Hoặc giữ ổn định các thuộc tính dùng so sánh, chỉ cho đổi dữ liệu không ảnh hưởng quan hệ thứ tự.
- Nếu phải đổi thuộc tính so sánh, gỡ khi giá trị cũ còn hợp lệ rồi thêm giá trị mới theo một quy trình có kiểm soát.
- Khi có thể, tạo đối tượng mới thay vì sửa cùng một khóa đang được nhiều nơi giữ.

Comparator cũng không nên dựa vào một cấu hình ngoài có thể bị đổi tùy ý sau khi cây đã có dữ liệu. Ví dụ, đảo cờ “tăng dần/giảm dần” trong comparator đang được dùng không tự xây lại cây.

Đổi comparator có ý nghĩa như đổi quy tắc tổ chức. Hãy tạo một cấu trúc mới với quy tắc mới và nạp dữ liệu hợp lệ theo yêu cầu, thay vì giả định cây cũ sẽ tự đảo liên kết sau khi một biến ngoài thay đổi.

Không dùng ngoại lệ fail-fast như một cơ chế phát hiện mọi lỗi này. Việc sửa trường của một phần tử không đồng nghĩa với một thao tác cấu trúc được thực hiện qua TreeSet.

## 14. Một số giới hạn cần biết khi áp dụng

### 14.1. null phụ thuộc quy tắc so sánh

TreeSet theo thứ tự tự nhiên không chấp nhận `null`. Với comparator được cung cấp rõ ràng và hỗ trợ `null`, có thể thiết kế một thứ tự cho giá trị này, chẳng hạn thông qua `Comparator.nullsFirst`.

Điều quan trọng là phần tử phải được so sánh hợp lệ theo cùng một quy tắc. Không suy luận rằng “mọi Set đều nhận null” hoặc “mọi TreeSet tuyệt đối không thể nhận null”.

Nếu cho phép `null`, các phương thức trả về `null` khi không tìm được còn cần được đọc cẩn thận theo ngữ cảnh. Chọn cấm `null` thường làm rõ hơn mô hình dữ liệu nếu nghiệp vụ không cần giá trị thiếu ấy.

### 14.2. Phần tử phải so sánh được với nhau

Có kiểu generic `TreeSet<MyType>` chưa có nghĩa MyType tự nhiên đã có thứ tự. Cần MyType có quy tắc Comparable phù hợp hoặc cung cấp Comparator.

Một lỗi so sánh có thể xuất hiện ngay lúc thêm, kể cả không nên chờ tới khi đã có nhiều phần tử mới kiểm tra. Khả năng biên dịch và khả năng tạo quan hệ thứ tự hợp lệ là hai việc khác nhau.

### 14.3. Các góc nhìn theo khoảng không phải bản sao

Các phương thức như `subSet`, `headSet`, `tailSet` cho phép làm việc với một vùng theo thứ tự. Đây là các góc nhìn có ràng buộc biên và liên hệ với tập gốc.

Điều này hữu ích khi bài toán cần một khoảng giá trị, vì thứ tự đã có sẵn để định vị các biên. Nhưng không coi việc tạo góc nhìn là đã tách dữ liệu sang một bản chụp độc lập.

Thêm ngoài biên qua một góc nhìn bị giới hạn có thể bị từ chối. Khi cần bản sao riêng, tạo một collection mới với thứ tự và chính sách dữ liệu phù hợp.

### 14.4. Không có truy cập theo thứ hạng

`first()` lấy giá trị đầu theo thứ tự; nó không phải `get(0)` của một danh sách có chỉ số. TreeSet không trực tiếp cung cấp “phần tử thứ 1000” với chi phí logarit qua API chuẩn.

Nếu bài toán thường xuyên truy cập theo vị trí trong dãy đã sắp xếp, cần xem lại cấu trúc phù hợp. Không nên lặp từ đầu nhiều lần rồi cho rằng mỗi lần là `O(log n)` chỉ vì dữ liệu nằm trong cây cân bằng.

### 14.5. Không tự đồng bộ giữa nhiều luồng

TreeSet thông thường không tự cung cấp khả năng đồng bộ cho nhiều luồng cập nhật. Việc cây tự cân bằng là tính chất của thuật toán tổ chức, không phải cam kết an toàn khi dùng đồng thời.

Khi có nhiều luồng, hãy chọn cơ chế phối hợp hoặc một cấu trúc đồng thời phù hợp với yêu cầu. Dù chọn cách nào, comparator và dữ liệu dùng so sánh vẫn phải có ngữ nghĩa ổn định.

## 15. Chọn TreeSet khi thứ tự phục vụ thao tác thật

TreeSet phù hợp với các nhu cầu như:

- Kiểm tra thành viên đồng thời thường xuyên thêm và xóa.
- Duyệt các giá trị duy nhất theo cùng một thứ tự ổn định.
- Tìm mốc trước hoặc sau một giá trị.
- Làm việc với các khoảng của một miền có thứ tự.
- Lấy hoặc gỡ phần tử ở một đầu theo thứ tự.

Một vài câu hỏi giúp chọn cấu trúc:

| Nhu cầu chủ đạo | Điều cần cân nhắc |
|---|---|
| Chỉ kiểm tra đã có, không cần thứ tự | Có thể không cần trả chi phí duy trì cây có thứ tự |
| Giữ đúng thứ tự chèn | Đây không phải thứ tự TreeSet biểu đạt |
| Giữ mọi lần xuất hiện của giá trị | Cần mô hình cho phép trùng hoặc đếm số lần |
| Sắp xếp một lần rồi đọc theo chỉ số | Một danh sách sắp xếp có thể phù hợp hơn |
| Thường xuyên cập nhật và tìm lân cận theo thứ tự | Đây là lợi thế trực tiếp của cấu trúc cây có thứ tự |

Không chọn TreeSet chỉ vì chữ “Tree” hoặc vì nghe nói cây luôn nhanh. Lợi ích đến từ việc một cấu trúc duy trì đồng thời tính duy nhất, thứ tự và đường tìm kiếm thấp.

Cũng không bắt TreeSet làm nhiệm vụ của một Map. Nếu mỗi khóa có một giá trị cần cập nhật độc lập, TreeMap có thể thể hiện yêu cầu rõ hơn. TreeSet quản lý thành viên; thêm một phần tử tương đương không phải thao tác ghi đè một bản ghi dữ liệu.

## 16. Kiểm chứng đúng thứ tự, đúng thành viên và đúng giả định

Khi viết kiểm tra cho mã sử dụng TreeSet, nên bảo vệ hành vi quan sát được mà ứng dụng cần:

1. Dữ liệu duyệt ra đúng quan hệ thứ tự đã chọn.
2. Hai phần tử tương đương theo quy tắc mong muốn không tạo thêm thành viên.
3. Hai phần tử khác nhau mà nghiệp vụ muốn giữ đều còn tồn tại.
4. `contains`, `remove` và các phép tìm biên xử lý đúng dữ liệu đại diện.
5. Thuộc tính được phép cập nhật không phá quy tắc so sánh.
6. Comparator xử lý đúng các giá trị biên, tránh lỗi tràn và bảo đảm tính bắc cầu.

Có thể đối chiếu dãy duyệt với một danh sách kỳ vọng trong những ví dụ nhỏ có thứ tự xác định. Khác với một Set không bảo đảm thứ tự, ở đây thứ tự là một phần của yêu cầu.

Nhưng không kiểm tra gốc phải là một giá trị cụ thể, nút nào phải đỏ, hay một lần tìm phải dùng đúng số phép so sánh của máy đang chạy, nếu ứng dụng không tự triển khai cây. Những điều đó thuộc hình dạng nội bộ, không phải hợp đồng TreeSet cung cấp.

Nếu đang tự viết cây đỏ–đen để học thuật toán, phạm vi kiểm tra khác đi: sau mỗi cập nhật cần kiểm tra cả quy tắc cây tìm kiếm, quan hệ cha–con, màu gốc, không đỏ–đỏ, chiều cao đen và số lượng nút. Đừng lấy vài output sắp xếp đúng làm bằng chứng đầy đủ cho một cây tự triển khai.

## 17. Bài tập áp dụng

### Bài 1: đầu vào đã tăng dần

Thêm các số từ 1 đến n vào TreeSet. Có cần xáo trộn trước để tránh cây suy biến không?

**Gợi ý trả lời:** không. TreeSet dựa trên cơ chế tự cân bằng. Xáo trộn đầu vào không phải điều kiện để các thao tác cơ bản giữ cận logarit.

### Bài 2: output giống nhau, cấu trúc khác nhau

Hai cây cùng duyệt ra `1, 2, 3, 4, 5, 6, 7`. Một cây là chuỗi, một cây có chiều cao thấp.

Có thể kết luận chi phí tìm kiếm giống nhau chỉ từ output không?

**Gợi ý trả lời:** không. Output thể hiện thứ tự nội dung, còn chi phí đường tìm phụ thuộc chiều cao và cấu trúc.

### Bài 3: quay trái có đổi thứ tự không?

Dùng sơ đồ `A, x, B, y, C` trong phần phép quay để giải thích vì sao thứ tự duyệt không đổi.

**Gợi ý trả lời:** phép quay giữ các quan hệ `A < x < B < y < C`. Nó đổi liên kết cục bộ, không đổi chuỗi duyệt theo thứ tự.

### Bài 4: chỉ so theo độ dài chuỗi

TreeSet dùng `Comparator.comparingInt(String::length)`. Thêm `"cat"` và `"dog"`.

Có giữ cả hai không, và đây có phải lỗi cân bằng không?

**Gợi ý trả lời:** hai chuỗi có cùng độ dài nên comparator trả về 0; TreeSet chỉ giữ một đại diện. Đây là hệ quả quy tắc tương đương, không phải lỗi hình dạng cây. Nếu muốn giữ cả hai, cần một quan hệ phân biệt phù hợp với nghiệp vụ.

### Bài 5: khóa so sánh bị sửa

Một công việc nằm trong TreeSet theo mức ưu tiên, rồi mức ưu tiên bị sửa trực tiếp.

Cân bằng đỏ–đen có tự chuyển công việc sang vị trí mới không?

**Gợi ý trả lời:** không. Cấu trúc không tự theo dõi setter. Cần khóa ổn định hoặc một luồng gỡ trước, thay đổi rồi thêm lại được quản lý đúng.

### Bài 6: một lần so sánh rất chậm

Comparator phải xử lý một chuỗi dài hoặc tính một thuộc tính tốn kém. Có thể chỉ nhìn `O(log n)` rồi coi thời gian tìm là nhỏ không?

**Gợi ý trả lời:** không. Cần tính cả chi phí mỗi lần so sánh. Số phép so sánh logarit không làm một comparator tốn kém trở thành miễn phí.

### Bài 7: tìm phần tử thứ k

Yêu cầu lấy thường xuyên phần tử thứ k trong dãy theo thứ tự. TreeSet có sẵn `get(k)` với `O(log n)` không?

**Gợi ý trả lời:** không. API chuẩn không cung cấp truy cập theo thứ hạng như vậy. Cần chọn cấu trúc theo đúng thao tác mà bài toán sử dụng.

Khi đánh giá một ứng dụng dùng TreeSet, hãy chỉ rõ quan hệ thứ tự nào đang dẫn đường, các thành viên nào phải được coi là tương đương, và vì sao dữ liệu dùng so sánh giữ ổn định trong suốt thời gian nằm trong cây.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sorted-set-003">Tiêu chí trùng phần tử khi phép so sánh trả về 0</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêu chí trùng phần tử khi phép so sánh trả về 0

## 1. Với TreeSet, phép so sánh quyết định cả thứ tự và phần tử trùng

Khi dùng một tập sắp xếp như `TreeSet`, ta thường chú ý tới câu hỏi “phần tử nào đứng trước?”. Nhưng cùng phép so sánh đó còn trả lời một câu hỏi quan trọng khác: **hai giá trị có được tập xem là cùng một phần tử hay không?**

Quy tắc cần nhớ:

```text
compare(a, b) == 0
    → a và b tương đương theo thứ tự của tập
    → TreeSet không giữ chúng thành hai phần tử riêng
```

Nếu dùng thứ tự tự nhiên, phép so sánh là `a.compareTo(b)`. Nếu tạo tập với một `Comparator`, phép so sánh là `comparator.compare(a, b)`.

Ví dụ, bộ so sánh chỉ theo độ dài chuỗi xem `"An"` và `"Bo"` là tương đương vì cùng dài hai ký tự. Một `TreeSet` dùng bộ so sánh đó chỉ giữ một đại diện, dù:

```java
"An".equals("Bo") // false
```

Vì vậy, chọn `Comparator` cho một tập không chỉ là chọn kiểu trình bày. Nó ảnh hưởng trực tiếp tới số phần tử, kết quả thêm, tìm và xóa.

Tài liệu `TreeSet` giải thích rằng các phần tử được so sánh bằng `compareTo` hoặc `Comparator`; hai giá trị có kết quả so sánh bằng `0` được tập xem là bằng nhau theo thứ tự của nó. [TreeSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)

## 2. Ba loại kết quả của phép so sánh

Một phép so sánh hợp lệ trả về số nguyên có ý nghĩa theo dấu:

| Kết quả compare(a, b) | Ý nghĩa theo thứ tự đã chọn |
| --- | --- |
| Nhỏ hơn 0 | a đứng trước b |
| Bằng 0 | a và b tương đương theo tiêu chí so sánh |
| Lớn hơn 0 | a đứng sau b |

Không mặc định phương thức chỉ được trả về đúng `-1`, `0`, `1`. Với một `Comparator` bất kỳ, dấu âm/dương mới là điều dùng để quyết định trước–sau.

Số `0` cũng không có nghĩa:

- Hai biến trỏ tới cùng đối tượng.
- Hai đối tượng có mọi trường giống nhau.
- Phương thức `equals` chắc chắn trả về `true`.
- Không đủ thông tin để so sánh nên tập sẽ tự xét tiếp bằng `equals`.

Nó có nghĩa bộ so sánh đã kết luận hai giá trị tương đương theo thứ tự đó. Với `TreeSet`, kết luận này đủ để xem là đã có phần tử tương ứng.

Nếu mục đích chỉ là “cùng ưu tiên nhưng vẫn phải giữ riêng”, trả về `0` sau khi chỉ so ưu tiên có thể là thiếu tiêu chí đối với một tập. [Comparator.compare](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html#compare(T,T))

## 3. Thứ tự tự nhiên và Comparator: quy tắc nào được dùng?

### 3.1. Không truyền Comparator: dùng Comparable

```java
Set<Integer> numbers = new TreeSet<>();
```

Các số nguyên được sắp theo thứ tự tự nhiên do `Integer` cung cấp. Các phần tử cần có khả năng so sánh tương thích với nhau theo quy tắc đó.

Với những kiểu giá trị thông thường như `Integer`, hai số nguyên bằng nhau theo `equals` cũng có kết quả so sánh bằng `0`. Nhưng sự phù hợp đó không phải điều được mọi lớp có `Comparable` bắt buộc phải cung cấp; `BigDecimal` là trường hợp cần phân biệt ở phần sau.

Nếu `tree.comparator()` trả về `null`, điều đó có nghĩa tập dùng thứ tự tự nhiên, không phải tập không có quy tắc thứ tự.

### 3.2. Có truyền Comparator: dùng quy tắc được truyền

```java
Comparator<String> byLength = Comparator.comparingInt(String::length);
Set<String> names = new TreeSet<>(byLength);
```

Quy tắc đang dùng chỉ nhìn độ dài:

```text
compare("An", "Bo") = 0
compare("An", "Linh") < 0
compare("Linh", "Chi") > 0
```

Không có bước mặc định “nếu độ dài bằng nhau thì so tiếp theo chữ” trong biểu thức trên. Nếu muốn bước đó, phải thiết kế nó, chẳng hạn bằng `thenComparing`.

Đối tượng phần tử không nhất thiết phải tự cài `Comparable` khi đã có một `Comparator` phù hợp cho chúng. Tuy nhiên, mọi phần tử vẫn phải nằm trong miền dữ liệu mà bộ so sánh có thể xử lý. [Các cách xác định thứ tự của SortedSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SortedSet.html)

## 4. Cơ chế khiến một phần tử bị xem là trùng

Có thể hình dung việc tìm vị trí trong cây bằng quy trình khái niệm:

```text
giá trị cần tìm hoặc thêm
    ↓
so với một mục đang lưu
    ├─ kết quả âm: tiếp tục phía đứng trước
    ├─ kết quả dương: tiếp tục phía đứng sau
    └─ kết quả 0: đã tìm được phần tử tương đương
```

Sơ đồ không mô tả hình dạng cây hay mọi chi tiết cài đặt. Nó cho thấy vì sao kết quả `0` có ý nghĩa dừng tìm một giá trị mới riêng biệt.

### 4.1. add trả false khi có phần tử tương đương

Với bộ so sánh theo độ dài:

```text
ban đầu: []

add("An") → true
tập: [An]

add("Bo") → false
tập: [An]
```

`"Bo"` không được thêm thành phần tử thứ hai. Không có thao tác gộp hai chuỗi thành một giá trị mới và cũng không tự thay `"An"` bằng `"Bo"`.

Một phần tử đã lưu là đại diện cho nhóm tương đương theo bộ so sánh. Trong chuỗi thêm ở ví dụ, `"An"` đã được giữ trước; thêm `"Bo"` vào tập đang có đại diện đó không thay đổi tập.

Thứ tự thêm có thể ảnh hưởng đại diện được giữ trong một nhóm mà các đối tượng không bằng nhau theo `equals`. Đây là hệ quả cần hiểu nếu dùng bộ so sánh thô hơn ý nghĩa giá trị của đối tượng.

### 4.2. contains cũng dùng cùng ý nghĩa tương đương

Nếu tập chỉ đang giữ `"An"`, thì:

```java
names.contains("Tu"); // true với Comparator chỉ theo độ dài
```

Điều đó không có nghĩa tập đang lưu đúng chuỗi `"Tu"`. Nó có nghĩa tập có một phần tử tương đương với `"Tu"` theo quy tắc so sánh hiện tại.

`contains` không phải phương thức lấy đại diện đang lưu ra cho người gọi. Kết quả là một giá trị `boolean`.

### 4.3. remove có thể xóa một đại diện không bằng đối số theo equals

Cũng trong trạng thái đang có `"An"`:

```java
names.remove("Tu"); // true với Comparator chỉ theo độ dài
```

đã loại phần tử đại diện cho nhóm chuỗi dài hai ký tự, tức `"An"` trong ví dụ này.

Nếu người viết chỉ nghĩ “remove chuỗi nào thì chỉ xóa đúng chuỗi đó theo String.equals”, thao tác này có thể gây bất ngờ. Cần đọc quy tắc nhận diện của tập trước khi suy luận kết quả.

### 4.4. hashCode không phân xử thêm sau compare == 0

`TreeSet` không dùng mã băm để tách hai giá trị đã được bộ so sánh xem là tương đương.

Thay đổi `hashCode` của phần tử không làm `TreeSet` giữ riêng hai đối tượng khi phép so sánh vẫn trả `0`. Việc tìm và loại trùng ở đây dựa vào thứ tự, khác với cơ chế tìm trong tập băm.

Điều đó không có nghĩa có thể bỏ mọi hợp đồng `equals`/`hashCode` của kiểu đối tượng. Nó chỉ nói mã băm không phải tiêu chí dự phòng cho đường tìm của tập cây.

## 5. Chương trình đầy đủ: chuỗi cùng độ dài bị gộp

Lưu toàn bộ mã vào **ComparisonZeroDemo.java**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ComparisonZeroDemo {
    public static void main(String[] args) {
        Comparator<String> byLength = Comparator.comparingInt(String::length);
        TreeSet<String> names = new TreeSet<>(byLength);

        System.out.println("An equals Bo: " + "An".equals("Bo"));
        System.out.println("compare An/Bo: " + byLength.compare("An", "Bo"));
        System.out.println("add An: " + names.add("An"));
        System.out.println("add Bo: " + names.add("Bo"));
        names.add("Linh");
        names.add("Chi");
        System.out.println("by length: " + names);
        System.out.println("size: " + names.size());
        System.out.println("contains Tu: " + names.contains("Tu"));
        System.out.println("remove Tu: " + names.remove("Tu"));
        System.out.println("after removal: " + names);

        Comparator<String> byLengthThenText =
                byLength.thenComparing(Comparator.naturalOrder());
        TreeSet<String> precise = new TreeSet<>(byLengthThenText);
        precise.addAll(List.of("An", "Bo", "Linh", "Chi", "An"));
        System.out.println("length then text: " + precise);
        System.out.println("precise size: " + precise.size());
        System.out.println("precise contains Tu: " + precise.contains("Tu"));

        List<String> sequence =
                new ArrayList<>(List.of("An", "Bo", "Linh", "Chi"));
        sequence.sort(byLength);
        System.out.println("list sorted by length: " + sequence);

        Set<String> coarse = new TreeSet<>(byLength);
        coarse.add("An");
        Set<String> ordinary = new HashSet<>(Set.of("Bo"));
        System.out.println("coarse.equals(ordinary): " + coarse.equals(ordinary));
        System.out.println("ordinary.equals(coarse): " + ordinary.equals(coarse));
    }
}
```

Biên dịch theo mức Java 17 và chạy:

```shell
javac --release 17 -encoding UTF-8 ComparisonZeroDemo.java
java ComparisonZeroDemo
```

Stdout:

```text
An equals Bo: false
compare An/Bo: 0
add An: true
add Bo: false
by length: [An, Chi, Linh]
size: 3
contains Tu: true
remove Tu: true
after removal: [Chi, Linh]
length then text: [An, Bo, Chi, Linh]
precise size: 4
precise contains Tu: false
list sorted by length: [An, Bo, Chi, Linh]
coarse.equals(ordinary): true
ordinary.equals(coarse): false
```

Cách đọc kết quả:

1. `"An"` không bằng `"Bo"` theo `String.equals`, nhưng so độ dài cho `0`.
2. Thêm `"Bo"` trả về `false`, nên nhóm dài hai ký tự chỉ có đại diện `"An"`.
3. `"Chi"` dài ba ký tự, `"Linh"` dài bốn ký tự, nên trình tự là `[An, Chi, Linh]`.
4. Tìm `"Tu"` trả về `true`, dù chuỗi được lưu ở nhóm dài hai là `"An"`.
5. Xóa bằng `"Tu"` loại đại diện `"An"`.
6. Thêm tiêu chí văn bản sau độ dài giúp tập `precise` giữ riêng `"An"` và `"Bo"`.
7. Sắp xếp một `List` theo độ dài không loại bỏ các phần tử cùng độ dài.
8. Hai dòng cuối chủ động cho thấy hậu quả khi tập cây dùng quy tắc không nhất quán với `equals`: phép bằng nhau của cả tập có thể mất đối xứng. Phần tiếp theo giải thích vì sao không nên coi đó là một cách dùng bình thường của hợp đồng `Set`.

## 6. Tính nhất quán với equals phải xét theo hai chiều

Một thứ tự **nhất quán với equals (consistent with equals)** khi, trong miền dữ liệu đang dùng:

```text
compare(a, b) == 0
    khi và chỉ khi
a.equals(b) == true
```

Cần cả hai chiều:

| Tình huống | Hệ quả trong TreeSet | Vấn đề với hợp đồng chung của Set |
| --- | --- | --- |
| `equals` false nhưng compare trả 0 | Hai đối tượng khác theo giá trị bị xem là một phần tử | Có thể từ chối một giá trị mà Set thông thường xem là khác |
| `equals` true nhưng compare khác 0 | Tập có thể giữ cả hai đối tượng | Có thể giữ hai phần tử bằng nhau theo equals |
| Hai phép nhận diện phù hợp nhau | Tập sắp xếp và phép bằng nhau giá trị thống nhất | Thuận lợi để tuân thủ hợp đồng Set |

Tài liệu `Comparator` không cấm mọi bộ so sánh không nhất quán với `equals`. Một bộ so sánh có thể hợp lệ để sắp xếp một danh sách nhưng không phù hợp để làm tiêu chí nhận diện của một tập muốn tuân thủ đầy đủ hợp đồng chung.

Tài liệu `TreeSet` cũng nêu rõ: nếu thứ tự không nhất quán với `equals`, tập vẫn hoạt động theo quy tắc so sánh của nó, nhưng không tuân thủ hợp đồng chung của `Set`. [Comparator: nhất quán với equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html), [TreeSet: điều kiện hợp đồng Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)

### Vì sao equals của hai tập có thể khác theo chiều gọi?

Trong chương trình đầu:

- `coarse` là tập cây chỉ có `"An"`, dùng độ dài để nhận diện.
- `ordinary` là tập băm chỉ có `"Bo"`, dùng quy tắc bằng nhau của chuỗi.

Khi tập cây kiểm tra chuỗi `"Bo"`, nó thấy nhóm dài hai đã có. Khi tập băm kiểm tra `"An"`, nó không thấy chuỗi bằng `"An"`.

Vì thế:

```text
coarse.equals(ordinary) → true
ordinary.equals(coarse) → false
```

Đây không phải một quy tắc mong muốn của phép bằng nhau. Nó là minh chứng rằng hai tập đang dùng hai ý nghĩa không thống nhất về thành viên.

Không “sửa” bằng cách luôn gọi `equals` từ bên có kết quả đúng ý hoặc ghép hai chiều bằng toán tử OR. Cần sửa thiết kế nhận diện, hoặc chọn cấu trúc và phép so sánh riêng phù hợp với mục đích khác.

## 7. Bổ sung tiêu chí phụ: thenComparing có tác dụng gì?

### 7.1. Tiêu chí phụ chỉ chạy khi tiêu chí trước trả 0

Nếu muốn sắp chuỗi theo độ dài nhưng vẫn giữ các chuỗi khác nhau có cùng độ dài:

```java
Comparator<String> order =
        Comparator.comparingInt(String::length)
                .thenComparing(Comparator.naturalOrder());
```

Quy tắc là:

1. So độ dài.
2. Nếu độ dài khác nhau, đã có kết quả.
3. Nếu độ dài bằng nhau, so tiếp nội dung chuỗi theo thứ tự tự nhiên.

Với `String`, tiêu chí văn bản tiếp theo phân biệt những nội dung không bằng nhau, nên `"An"` và `"Bo"` không còn có kết quả tổng bằng `0`.

Không có việc `thenComparing` tự biết trường định danh nghiệp vụ. Người thiết kế phải chọn tiêu chí phụ đúng ý nghĩa.

### 7.2. Với đối tượng nhiều trường, cần khớp cả quy tắc equals

Ví dụ dùng record:

```java
private record Task(String id, int priority) {
}
```

Record này có phép bằng nhau theo cả `id` và `priority`. Một thứ tự so ưu tiên rồi so mã phù hợp với hai thành phần đó:

```java
Comparator<Task> order = Comparator.comparingInt(Task::priority)
        .thenComparing(Task::id);
```

Giả định `id` là chuỗi hợp lệ theo hợp đồng đầu vào, hai đối tượng có compare bằng `0` khi cùng ưu tiên và cùng mã, tương ứng với bằng nhau của record trong ví dụ.

Nếu chỉ so `priority`, hai công việc khác mã nhưng cùng ưu tiên bị gộp. Nếu mục đích là giữ mọi công việc cùng ưu tiên, tiêu chí đó chưa đủ cho một tập.

### 7.3. Thêm mã phụ không phải phương thuốc đúng cho mọi mô hình

Giả sử một lớp khác định nghĩa `equals` chỉ theo mã, nhưng bộ so sánh lại nhìn ưu tiên rồi mã. Hai đối tượng cùng mã nhưng ưu tiên khác có thể:

```text
equals → true
compare → khác 0
```

Khi đó, bộ so sánh vẫn không nhất quán với `equals`, dù đã có `thenComparing(id)`.

Vì vậy, đừng áp dụng khẩu quyết “cứ thêm id là xong”. Phải viết rõ:

- Dữ liệu nào xác định một giá trị?
- Hai đối tượng thế nào được xem là trùng?
- Những dữ liệu nào tham gia thứ tự?
- Compare bằng 0 có tương ứng chính xác với equals không?

Nếu chỉ cần giữ duy nhất theo mã rồi hiển thị theo ưu tiên, có thể tách việc quản lý theo mã với việc tạo một danh sách sắp xếp để trình bày. Không bắt một `TreeSet` vừa mang quy tắc định danh này vừa giả vờ mang một quy tắc khác.

## 8. Sắp xếp một List khác với loại trùng trong TreeSet

Cùng một bộ so sánh theo độ dài có hai vai trò khác nhau:

| Nơi sử dụng | Khi hai chuỗi cùng độ dài |
| --- | --- |
| `List.sort(byLength)` | Sắp thứ tự, vẫn giữ các phần tử trong danh sách |
| `new TreeSet<>(byLength)` rồi thêm | Chỉ giữ một đại diện cho nhóm có compare bằng 0 |

Một danh sách có thể chứa nhiều phần tử bằng nhau theo tiêu chí sắp xếp. Không có quy tắc loại trùng tự động chỉ vì gọi `sort`.

Nếu yêu cầu chỉ là “hiển thị mọi công việc theo điểm/ưu tiên”, danh sách sắp xếp có thể đúng ý nghĩa hơn. Nếu yêu cầu thực sự là tập các giá trị phân biệt và cần thứ tự, phải thiết kế tiêu chí phân biệt đầy đủ cho tập.

Chuyển từ danh sách sang tập cây không phải một cách sắp xếp trung tính: nó có thể làm giảm số phần tử. Trước khi thay cấu trúc, hãy kiểm tra điều kiện compare bằng `0`.

## 9. BigDecimal: thứ tự tự nhiên cũng có thể khác equals

Không chỉ `Comparator` tự viết mới có sự khác biệt này. `BigDecimal` là ví dụ được tài liệu `Comparable` nêu rõ:

```java
BigDecimal first = new BigDecimal("4.0");
BigDecimal second = new BigDecimal("4.00");
```

Hai đối tượng biểu diễn cùng giá trị số theo `compareTo`, nhưng có scale khác nhau:

```text
first.compareTo(second) → 0
first.equals(second)    → false

first.scale()  → 1
second.scale() → 2
```

`BigDecimal.equals` xét giá trị và scale; `compareTo` so giá trị số mà không phân biệt hai scale này. Đây là lý do một `HashSet<BigDecimal>` có thể giữ hai giá trị trong khi `TreeSet<BigDecimal>` dùng thứ tự tự nhiên chỉ giữ một đại diện. [Comparable: trường hợp BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html), [BigDecimal.compareTo và equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)

Có ít nhất hai yêu cầu khác nhau:

- Nếu ý nghĩa mong muốn là cùng giá trị số, hai biểu diễn này có thể cần được quản lý như cùng một giá trị theo một mô hình được xác định rõ.
- Nếu cần giữ sự khác nhau mà `BigDecimal.equals` nhìn thấy, không thể chỉ dùng so sánh số tự nhiên để phân biệt trong tập cây.

Ví dụ cho lựa chọn thứ hai là so giá trị số trước, rồi so scale:

```java
Comparator<BigDecimal> valueThenScale =
        Comparator.<BigDecimal>naturalOrder()
                .thenComparingInt(BigDecimal::scale);
```

Khi giá trị số bằng nhau nhưng scale khác, tiêu chí phụ phân biệt chúng. Với `BigDecimal`, cùng giá trị số và cùng scale tương ứng với điều kiện bằng nhau mà `equals` sử dụng.

Đây là lựa chọn ý nghĩa dữ liệu, không phải thay đổi tùy tiện để kích thước tập lớn hơn. Nếu miền dữ liệu xem scale có ý nghĩa, không tự xóa thông tin đó chỉ để làm một loại tập dễ dùng.

## 10. Chương trình đầy đủ: record, tiêu chí phụ và BigDecimal

Lưu mã vào **SortedSetIdentityDemo.java**:

```java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class SortedSetIdentityDemo {
    private record Task(String id, int priority) {
    }

    private static String taskOrder(Set<Task> tasks) {
        StringBuilder result = new StringBuilder("[");
        String separator = "";
        for (Task task : tasks) {
            result.append(separator).append(task.id())
                    .append(":").append(task.priority());
            separator = ", ";
        }
        return result.append("]").toString();
    }

    public static void main(String[] args) {
        Comparator<Task> order = Comparator.comparingInt(Task::priority)
                .thenComparing(Task::id);
        Set<Task> tasks = new TreeSet<>(order);
        tasks.add(new Task("t-3", 2));
        tasks.add(new Task("t-2", 1));
        tasks.add(new Task("t-1", 1));
        System.out.println("tasks: " + taskOrder(tasks));
        System.out.println("duplicate task: " + tasks.add(new Task("t-2", 1)));
        System.out.println("contains same fields: "
                + tasks.contains(new Task("t-2", 1)));
        System.out.println("contains changed priority: "
                + tasks.contains(new Task("t-2", 9)));

        BigDecimal first = new BigDecimal("4.0");
        BigDecimal second = new BigDecimal("4.00");
        System.out.println("decimal equals: " + first.equals(second));
        System.out.println("decimal compareTo: " + first.compareTo(second));

        Set<BigDecimal> hashed = new HashSet<>();
        hashed.add(first);
        hashed.add(second);
        System.out.println("HashSet decimals: " + hashed.size());

        TreeSet<BigDecimal> numeric = new TreeSet<>();
        System.out.println("TreeSet add first: " + numeric.add(first));
        System.out.println("TreeSet add second: " + numeric.add(second));
        System.out.println("TreeSet decimals: " + numeric.size());
        System.out.println("stored scale: " + numeric.first().scale());
        System.out.println("contains 4.000: "
                + numeric.contains(new BigDecimal("4.000")));
        System.out.println("remove second: " + numeric.remove(second));
        System.out.println("numeric empty: " + numeric.isEmpty());

        Comparator<BigDecimal> valueThenScale =
                Comparator.<BigDecimal>naturalOrder()
                        .thenComparingInt(BigDecimal::scale);
        Set<BigDecimal> scaleAware = new TreeSet<>(valueThenScale);
        scaleAware.add(first);
        scaleAware.add(second);
        System.out.println("scale-aware size: " + scaleAware.size());
        System.out.println("scale-aware order: " + scaleAware);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SortedSetIdentityDemo.java
java SortedSetIdentityDemo
```

Stdout:

```text
tasks: [t-1:1, t-2:1, t-3:2]
duplicate task: false
contains same fields: true
contains changed priority: false
decimal equals: false
decimal compareTo: 0
HashSet decimals: 2
TreeSet add first: true
TreeSet add second: false
TreeSet decimals: 1
stored scale: 1
contains 4.000: true
remove second: true
numeric empty: true
scale-aware size: 2
scale-aware order: [4.0, 4.00]
```

Cách đọc kết quả:

1. Hai công việc cùng ưu tiên `1` vẫn được giữ vì mã `t-1` và `t-2` phân biệt ở tiêu chí phụ.
2. Thêm một record có cả hai trường giống record đang có trả về `false`.
3. Cùng mã nhưng ưu tiên `9` không bằng record có ưu tiên `1` trong mô hình record này.
4. `4.0` và `4.00` không bằng nhau theo `BigDecimal.equals`; tập băm giữ hai phần tử.
5. Tập cây tự nhiên chỉ giữ một phần tử. Scale của đại diện đang lưu là `1`, vì `first` được thêm trước và lần thêm `second` không thay thế nó.
6. Truy vấn bằng `4.000` vẫn tìm được đại diện cùng giá trị số.
7. Xóa bằng `second` xóa đại diện đó, làm tập cây tự nhiên rỗng.
8. Bộ so sánh giá trị rồi scale giữ được cả `4.0` và `4.00`.

Hai chương trình độc lập đã được biên dịch với `--release 17` và chạy để đối chiếu đúng stdout. Tùy chọn này kiểm tra mức ngôn ngữ/API và tạo bytecode tương thích Java 17; nó không khẳng định JVM thực thi chính là JDK 17.

## 11. Comparator phải hợp lệ trước khi bàn tới tiêu chí trùng

Một bộ so sánh không nhất quán với `equals` chưa chắc là bộ so sánh sai về các quy tắc thứ tự. Bộ so độ dài là một ví dụ: nó có thể hợp lệ để sắp xếp nhưng nhận diện quá thô đối với tập chuỗi dùng `String.equals`.

Cần phân biệt việc đó với một bộ so sánh vi phạm chính hợp đồng `Comparator`.

### 11.1. Những tính chất cần kiểm tra

Với các giá trị trong miền so sánh:

- So một giá trị với chính nó phải cho `0`.
- Đổi chiều hai đối số phải đảo dấu kết quả.
- Quan hệ trước–sau phải bắc cầu.
- Nếu `x` và `y` có compare bằng `0`, khi so chúng với một `z`, dấu kết quả phải nhất quán.

Tính chất cuối giúp các giá trị được xem là tương đương có quan hệ trước–sau thống nhất với các nhóm còn lại. Nếu không, cấu trúc cây không có một thứ tự đáng tin để tìm kiếm. [Hợp đồng Comparator.compare](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html#compare(T,T))

### 11.2. Không trả một số khác 0 chỉ để ép giữ mọi phần tử

Cách viết “nếu điểm bằng nhau thì cứ trả `1`” không phải tiêu chí phụ hợp lệ. Nó có thể làm:

```text
compare(a, b) > 0
compare(b, a) > 0
```

hoặc thậm chí `compare(a, a) > 0`. Đó là mâu thuẫn của bộ so sánh, không phải một cách biến tập thành danh sách giữ mọi phần tử.

Muốn giữ các giá trị khác nhau, bổ sung tiêu chí thật sự phân biệt chúng. Nếu muốn giữ cả những giá trị hoàn toàn bằng nhau, hãy xem lại việc dùng `Set`.

### 11.3. Không dùng phép trừ int làm cách so sánh tổng quát

Đoạn so `left.priority - right.priority` có thể tràn phạm vi `int`, khiến dấu kết quả sai với giá trị lớn/nhỏ ở biên.

Dùng `Integer.compare` hoặc `Comparator.comparingInt` để thể hiện phép so sánh số nguyên. Thêm tiêu chí phụ bằng các phương thức tương ứng thay vì tự ghép những phép tính khó kiểm tra.

Không dùng ngẫu nhiên, thời gian hiện tại hay một cấu hình thay đổi giữa các lời gọi để phá hòa. Một thứ tự thay đổi trong lúc tập đang giữ dữ liệu làm việc tìm kiếm không còn có nền tảng ổn định.

## 12. Miền dữ liệu và tính ổn định của khóa so sánh

### 12.1. Trường dùng để so sánh cần ổn định khi đang trong tập

Nếu tập sắp xếp công việc theo ưu tiên, rồi chương trình đổi ưu tiên của một đối tượng đang nằm trong tập, vị trí cấu trúc cũ có thể không còn phù hợp với quy tắc hiện tại.

Không dựa vào việc vẫn còn tham chiếu đối tượng để khẳng định `contains` hoặc `remove` sẽ tìm được nó như một khóa ổn định. Nên dùng các giá trị bất biến hoặc thiết kế rõ việc bỏ giá trị cũ rồi thêm giá trị mới theo quy trình phù hợp.

Ngay cả khi đối tượng bất biến, một bộ so sánh đọc dữ liệu từ một bảng cấu hình bên ngoài có thể thay đổi thứ tự nếu bảng đó bị sửa. Cần giữ ổn định cả dữ liệu nhận diện lẫn quy tắc so sánh trong thời gian tập được dùng.

### 12.2. null và kiểu không so sánh được là yêu cầu khác

Compare bằng `0` là kết quả của một phép so sánh hợp lệ. Nó không phải mã thay thế cho lỗi không thể so sánh.

`TreeSet` dùng thứ tự tự nhiên không cho phần tử `null`. Một `Comparator` có thể hỗ trợ `null` nếu được thiết kế như vậy, chẳng hạn bằng `Comparator.nullsFirst`.

Bộ so `Comparator.comparingInt(String::length)` trong ví dụ không tự xử lý `null`: gọi `length` cần một chuỗi hợp lệ. Hợp đồng đầu vào phải rõ ràng.

Tương tự, mọi phần tử phải có thể được so sánh theo cùng quy tắc. Một kiểu khai báo quá rộng không làm các giá trị không tương thích bỗng có thứ tự tự nhiên chung. [TreeSet: yêu cầu so sánh và ngoại lệ](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)

### 12.3. Chi phí so sánh cũng ảnh hưởng thao tác

`TreeSet` có mô hình O(log n) cho các thao tác cơ bản. Nhưng mỗi bước còn thực hiện phép so sánh; một bộ so sánh phải duyệt nhiều trường hoặc nhiều dữ liệu có chi phí riêng.

Không thêm tiêu chí phụ tốn kém mà không hiểu ý nghĩa, nhưng cũng không bỏ tiêu chí định danh cần thiết chỉ để làm phép so sánh ngắn hơn. Tính đúng của việc giữ phần tử là yêu cầu trước tiên.

## 13. Các lỗi thường gặp và cách tự kiểm tra

| Hiện tượng hoặc cách viết | Nguyên nhân có thể có | Hướng kiểm tra |
| --- | --- | --- |
| Thêm nhiều đối tượng nhưng size ít hơn dự kiến | Comparator trả 0 cho nhiều giá trị nghiệp vụ khác nhau | In hoặc kiểm tra cặp khóa so sánh cụ thể |
| contains trả true cho đối tượng chưa từng thêm | Có đại diện tương đương theo thứ tự | Phân biệt đối tượng đang lưu với nhóm compare bằng 0 |
| remove xóa một đại diện khác đối số theo equals | Quy tắc so sánh thô hơn equals | Xem lại tiêu chí nhận diện của tập |
| Đổi hashCode nhưng vẫn mất phần tử khi thêm | TreeSet không dùng mã băm để tách compare bằng 0 | Sửa bộ so sánh hoặc mô hình dữ liệu |
| Thêm thenComparing(id) rồi nghĩ mọi thứ đã nhất quán | Equals của lớp có thể dùng quy tắc khác | Kiểm tra điều kiện khi và chỉ khi ở cả hai chiều |
| Trả 1 khi bằng điểm để ép thêm | Vi phạm hợp đồng thứ tự | Dùng tiêu chí phụ hợp lệ hoặc chọn List |
| Dùng phép trừ số nguyên để so | Có thể tràn phạm vi và sai dấu | Dùng compare/comparingInt |
| Sắp xếp List thì đủ phần tử, chuyển TreeSet lại mất | Tập còn thực hiện loại trùng theo compare | Chọn cấu trúc theo yêu cầu giữ dữ liệu |
| HashSet giữ hai BigDecimal, TreeSet chỉ một | Equals và thứ tự tự nhiên của BigDecimal khác nhau về scale | Xác định scale có thuộc ý nghĩa giá trị cần giữ không |
| Hai chiều equals giữa các tập khác nhau | Các tập dùng quy tắc nhận diện không thống nhất | Sửa thiết kế, không chọn chiều có kết quả thuận ý |

Một cách chẩn đoán cụ thể là lấy hai đối tượng mà người học cho rằng phải được giữ riêng, rồi kiểm tra cả:

```java
a.equals(b);
comparator.compare(a, b);
```

Nếu đang dùng thứ tự tự nhiên, kiểm tra `a.compareTo(b)` thay cho lời gọi comparator. Sau đó xét kết quả có đúng yêu cầu nhận diện hay chỉ đúng yêu cầu trình bày.

## 14. Tự luyện và đối chiếu

### Bài 1: đại diện của nhóm bằng độ dài

Tạo tập cây chuỗi dùng bộ so sánh chỉ theo độ dài. Thêm lần lượt:

```text
"aa", "bb", "c", "ddd", "ee"
```

Dự đoán:

1. Giá trị trả về của năm lần thêm.
2. Nội dung và kích thước tập.
3. `contains("ff")`.
4. Sau `remove("xx")`, phần tử nào còn?

Đối chiếu:

```text
các kết quả add: true, false, true, true, false
tập: [c, aa, ddd]
size: 3
contains("ff"): true
remove("xx"): true
sau xóa: [c, ddd]
```

Phải giải thích được `"ff"` và `"xx"` không cần trùng nội dung với `"aa"` để thuộc cùng nhóm so sánh.

### Bài 2: giữ các chuỗi cùng độ dài

Bổ sung tiêu chí văn bản sau độ dài rồi thêm cùng dữ liệu của bài 1.

Kết quả là:

```text
[c, aa, bb, ee, ddd]
```

Các chuỗi dài hai ký tự được giữ riêng và sắp trong nhóm theo thứ tự văn bản.

### Bài 3: kiểm tra quy tắc của một lớp

Một lớp định nghĩa `equals` chỉ theo `id`. Comparator so theo `priority`, rồi `id`.

Cho hai đối tượng cùng `id` nhưng khác `priority`. Hãy xét:

- `equals` có trả `true` không?
- Comparator có trả `0` không?
- Bộ so sánh có nhất quán với `equals` trên cặp này không?

Đối chiếu: bằng nhau theo equals; khác nhau theo comparator; không nhất quán. Thêm id vào cuối chưa giải quyết được sự khác biệt về ý nghĩa của priority.

### Bài 4: chọn ý nghĩa cho BigDecimal

Với hai giá trị tạo từ `"5.0"` và `"5.00"`:

- Tập cây dùng thứ tự tự nhiên giữ một đại diện.
- Tập băm dùng phép bằng nhau của `BigDecimal` giữ hai phần tử.
- Tập cây so giá trị rồi scale có thể giữ hai phần tử theo ý nghĩa có phân biệt scale.

Không chọn một trong ba chỉ dựa vào kích thước lớn hay nhỏ hơn. Phải xác định hai biểu diễn có cần được giữ riêng trong mô hình dữ liệu hay không.

## 15. Tài liệu tra cứu

- [TreeSet Java 17 — thứ tự và nhận diện phần tử](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)
- [SortedSet — quan hệ giữa thứ tự và hợp đồng Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SortedSet.html)
- [Comparator — compare, thenComparing và tính nhất quán với equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)
- [Comparable — thứ tự tự nhiên và trường hợp BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html)
- [BigDecimal — compareTo, equals và scale](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sorted-set-004">Nhất quán giữa Comparator và equals</a>

<details>
<summary>Xem chi tiết</summary>

# Nhất quán giữa Comparator và equals

## 1. Vì sao cần kiểm tra cả Comparator và equals

Trong `TreeSet`, kết quả `compare(a, b) == 0` quyết định hai đầu vào có thuộc cùng nhóm thành viên. Trong hợp đồng chung của `Set`, phép `equals` lại định nghĩa giá trị nào là trùng. Hai quan hệ có thể cho kết quả khác nhau, ngay cả khi từng quan hệ riêng lẻ được định nghĩa rõ ràng.

Ví dụ, `Comparator.comparingInt(String::length)` xem `"cat"` và `"dog"` là tương đương về độ dài, trong khi `String.equals` phân biệt chúng. `TreeSet` theo độ dài chỉ giữ một đại diện. Còn một hướng sai lệch khác khó thấy hơn: hai đối tượng bằng nhau theo `equals` vẫn có thể được bộ so sánh đặt vào hai vị trí khác nhau.

Bài này kiểm tra **cả hai chiều** của điều kiện nhất quán, nhất là với kiểu dữ liệu định danh bằng mã nhưng hiển thị theo nhãn. Ta cũng kiểm tra hệ quả đối với `Set.equals`, rồi thiết kế quy tắc đúng với mô hình. Cần biết `Set`, `Comparator` và `equals`; không cần biết chi tiết cấu trúc nút cây.

## 2. Ba biểu thức giống nhau về tên nhưng khác ý nghĩa

Trước hết, phân biệt:

| Biểu thức | Câu hỏi được trả lời |
| --- | --- |
| `a == b` | Hai biến tham chiếu có trỏ đến cùng đối tượng không? |
| `a.equals(b)` | Hai phần tử có bằng nhau theo quy tắc của kiểu dữ liệu không? |
| `rule.compare(a, b)` | `a` đứng trước, tương đương hay đứng sau `b` theo quy tắc thứ tự? |

Đối với phép `compare`, chỉ dấu của kết quả có ý nghĩa chung:

~~~text
kết quả < 0 : a đứng trước b
kết quả = 0 : a và b tương đương về thứ tự
kết quả > 0 : a đứng sau b
~~~

“Bằng nhau” trong tiêu đề nói về **`equals` của phần tử**, không phải việc so sánh hai đối tượng `Comparator` với nhau. Interface `Comparator` cũng có phương thức `equals(Object)`, nhưng `rule1.equals(rule2)` là một câu hỏi riêng về hai bộ so sánh. Ghi đè phương thức ấy không phải cách làm `compare(a, b)` khớp với `a.equals(b)`.

Tham chiếu khác nhau vẫn có thể biểu diễn hai giá trị bằng nhau. Chẳng hạn, hai đối tượng thành viên cùng mã định danh có thể được mô hình xem là cùng người. Ngược lại, hai từ cùng độ dài vẫn có thể là hai giá trị chuỗi khác nhau.

Vì vậy, đừng dùng `==` thay cho `equals` khi kiểm tra tính nhất quán. Cũng đừng xem kết quả 0 của `compare` như khoảng cách bằng số giữa hai đối tượng: đó là quan hệ tương đương do quy tắc thứ tự định nghĩa.

## 3. Điều kiện “khi và chỉ khi” gồm hai chiều

Một `Comparator` nhất quán với `equals` trên miền phần tử đang xét khi:

~~~text
(rule.compare(a, b) == 0) == a.equals(b)
~~~

đúng cho mọi cặp phần tử hợp lệ `a, b` khác `null`. Nếu miền cho phép `null`, có thể diễn đạt phép bằng nhau bằng `Objects.equals(a, b)` và vẫn phải có bộ so sánh xử lý được các giá trị ấy.

Từ “mọi” rất quan trọng: không chỉ một cặp đầu tiên hoặc vài phần tử hiện có trong tập. Quy tắc cần phù hợp với miền dữ liệu mà chương trình cho phép đưa vào và truy vấn.

Điều kiện có hai chiều:

1. Nếu so sánh bằng 0 thì hai phần tử phải bằng nhau theo `equals`.
2. Nếu hai phần tử bằng nhau theo `equals` thì phép so sánh phải bằng 0.

Bảng bốn trường hợp giúp đọc chính xác:

| `a.equals(b)` | `compare(a, b) == 0` | Kết luận về cặp này |
| --- | --- | --- |
| `true` | `true` | Hai quan hệ khớp |
| `false` | `false` | Hai quan hệ khớp |
| `false` | `true` | Bộ so sánh gom hai giá trị mà equals phân biệt |
| `true` | `false` | Bộ so sánh tách hai giá trị mà equals xem là một |

Không yêu cầu kết quả `compare` phải bằng `hashCode(a) - hashCode(b)`. Không yêu cầu `equals` trả về vị trí hoặc hướng sắp xếp. Hai cơ chế có nhiệm vụ khác nhau; phần cần khớp là ranh giới giữa “tương đương” và “khác nhau”. [Java 17: Comparator, định nghĩa nhất quán với equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html).

## 4. Vì sao Set cần điều kiện này?

Hợp đồng chung của `Set` không cho phép giữ đồng thời hai phần tử bằng nhau theo `equals`. Các phép thêm, phép bằng nhau của tập và mã băm của tập cũng liên hệ với cách nhận diện đó. [Java 17: Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html).

`TreeSet` lại tìm và phân biệt phần tử bằng thứ tự tự nhiên hoặc `Comparator` được chọn khi tạo tập. Khi phép so sánh cho 0, nó xem như đã tìm thấy một thành viên tương đương. Nó không tự gọi thêm `equals` để tách hai đối tượng mà `Comparator` vừa gom lại.

Có thể hình dung hai cách chia dữ liệu thành nhóm:

~~~text
Theo equals:
  [cat] [dog] [owl]

Theo Comparator chỉ xét độ dài:
  [cat, dog, owl]
~~~

Mỗi dấu ngoặc biểu diễn một nhóm được xem là cùng giá trị theo quan hệ tương ứng. Trong tập sắp xếp theo độ dài, cả ba từ thuộc cùng một nhóm tương đương về thứ tự. Tập giữ một đại diện cho nhóm đó.

Nếu hai cách chia nhóm trùng nhau, tập sắp xếp có thể vừa duy trì thứ tự vừa đáp ứng ý nghĩa duy nhất của `Set`. Nếu chúng khác nhau, “không trùng” theo cây không còn đồng nghĩa “không trùng” theo `equals`.

Một bộ so sánh hợp lệ nhưng không nhất quán với `equals` vẫn có thể tạo hành vi xác định trong `TreeSet`. Tuy nhiên, tập đó không tuân thủ đầy đủ hợp đồng chung của `Set`. Đây là vấn đề hợp đồng dữ liệu, không phải ngẫu nhiên mất phần tử do cây hỏng. [Java 17: TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html).

## 5. Hướng sai lệch thứ nhất: khác theo equals nhưng compare bằng 0

Với `Comparator.comparingInt(String::length)`, ba từ `"cat"`, `"dog"`, `"owl"` là ba giá trị khác nhau theo `String.equals` nhưng cùng một nhóm so sánh vì đều dài ba ký tự:

~~~text
"cat".equals("dog") = false
byLength.compare("cat", "dog") = 0
~~~

Nếu thêm `"cat"` rồi `"dog"` vào `TreeSet` dùng bộ so sánh ấy, lần thêm sau trả `false` và tập chỉ giữ `"cat"`. `contains("owl")` vẫn trả `true`, còn `remove("owl")` có thể xóa đại diện `"cat"`. Các phép tìm/xóa đều dùng cùng quan hệ so sánh; gọi `contains` trước `add` không giải quyết được sai lệch.

Điểm cần kiểm tra là **ý nghĩa định danh**: nếu chỉ muốn lưu những độ dài xuất hiện, tập các số nguyên độ dài có thể biểu đạt rõ hơn. Nếu muốn lưu mọi từ khác nội dung, phải bổ sung tiêu chí phân biệt tương ứng với `String.equals`. Hướng sai lệch thứ hai, nơi `equals` coi hai phần tử là một nhưng comparator tách chúng ra, đòi hỏi xem xét kỹ hơn mô hình đối tượng.

## 6. Khi nào thêm tiêu chí phụ sửa được vấn đề?

Với `String`, bạn có thể sắp theo độ dài trước, rồi theo toàn bộ nội dung chuỗi khi độ dài bằng nhau:

~~~java
Comparator<String> lengthThenText =
        byLength.thenComparing(Comparator.naturalOrder());
~~~

Lúc này `"cat"` và `"dog"` cùng độ dài nhưng được phân biệt bằng nội dung. Chỉ hai chuỗi có cùng nội dung mới còn cho kết quả 0 ở cả hai tiêu chí.

Dữ liệu `dog, a, cat, cat` tạo tập:

~~~text
[a, cat, dog]
~~~

Thứ tự độ dài vẫn được giữ: `a` trước các từ ba ký tự. `cat` và `dog` cùng tồn tại. Bản `cat` lặp lại được loại đúng với `String.equals`.

Điều kiện thành công ở đây không phải “cứ có `thenComparing` là tốt”. Lý do là tiêu chí cuối cùng phân biệt chính xác các giá trị mà `equals` của `String` phân biệt. Nếu tiêu chí phụ vẫn gom nhiều giá trị khác nhau, vấn đề còn nguyên.

Ví dụ, độ dài rồi ký tự đầu vẫn không đủ để phân biệt `"cat"` và `"car"`. Một tiêu chí phụ có ích cho việc trình bày chưa chắc đủ cho sự duy nhất của tập.

Cũng không lấy mã băm làm một định danh chắc chắn để phân biệt mọi phần tử: hai đối tượng khác nhau vẫn có thể trùng mã băm. `hashCode` không phải mã nhận diện toàn cục không va chạm.

## 7. Hướng sai lệch thứ hai: equals bằng nhau nhưng compare khác 0

Giả sử mô hình `Member` có mã `id` và nhãn hiển thị `label`. Nghiệp vụ chọn nhận diện theo `id`: hai đối tượng cùng mã là cùng thành viên, dù nhãn ở hai lần đọc dữ liệu khác nhau.

Trong chương trình, `Member` là record nhưng có `equals` và `hashCode` được ghi đè tường minh để chỉ dựa vào `id`. Không dùng phép `equals` mặc định theo mọi thành phần của record.

Hai giá trị:

~~~text
7:alpha
7:zulu
~~~

bằng nhau theo `equals`, vì cùng mã 7.

Bây giờ chọn quy tắc “nhãn trước, mã sau”:

~~~java
Comparator<Member> labelThenId =
        Comparator.comparing(Member::label).thenComparingInt(Member::id);
~~~

Nhãn `alpha` và `zulu` khác nhau ngay ở tiêu chí đầu. Bộ so sánh trả kết quả khác 0, nên `TreeSet` nhận cả hai:

~~~text
[7:alpha, 7:zulu]
size = 2
~~~

Nhìn bằng thứ tự, tập có hai vị trí khác nhau. Nhìn bằng hợp đồng `Set` dựa trên `equals`, nó đang giữ hai phần tử bằng nhau.

Tiêu chí mã ở cuối không sửa được điều này. `thenComparingInt` chỉ chạy khi tiêu chí trước cho 0. Khi nhãn đã phân biệt hai đối tượng, việc cùng mã không còn cơ hội làm chúng tương đương.

Đảo thành “mã trước, nhãn sau” cũng chưa chắc sửa được: nếu mã bằng nhau rồi nhãn được dùng để tách tiếp, kết quả cuối vẫn khác 0 cho hai đối tượng bằng nhau theo `equals`. Muốn thứ tự tương thích với phép bằng nhau chỉ theo mã, các giá trị cùng mã phải luôn thuộc cùng một nhóm tương đương.

Trong ví dụ, `Comparator.comparingInt(Member::id)` là quy tắc nhất quán với `equals` của `Member`. Lần thêm `7:zulu` sau `7:alpha` trả `false`. Đại diện đang lưu vẫn là `7:alpha`; khớp quy tắc bằng nhau không có nghĩa tự cập nhật nhãn từ đối tượng đến sau.

## 8. Đừng “sửa” bằng một nhánh trả 0 trước rồi sắp tùy ý

Một ý tưởng thường xuất hiện là:

~~~java
Comparator<Member> shortcut = (left, right) ->
        left.id() == right.id() ? 0 : labelThenId.compare(left, right);
~~~

Đoạn này được dùng trong chương trình để **phát hiện lỗi**, không được đưa vào một `TreeSet`. Nó cố bảo đảm cùng mã thì 0, còn khác mã thì sắp theo nhãn.

Với ba giá trị:

~~~text
a = 7:alpha
b = 7:zulu
c = 8:mike
~~~

ta có:

~~~text
compare(a,b) = 0
a đứng trước c
b đứng sau c
~~~

Nếu `a` và `b` được xem là tương đương về thứ tự, chúng phải có quan hệ cùng dấu với mọi phần tử thứ ba `c`. Ở đây, một phần tử của cùng nhóm lại nằm trước `c`, phần tử còn lại nằm sau `c`. Nhóm tương đương không có vị trí nhất quán trong thứ tự.

Đây là lỗi **hợp đồng của Comparator**, không chỉ là thiếu nhất quán với `equals`. Điều kiện từng cặp `compare == 0` khớp `equals` chưa đủ để chứng minh bộ so sánh hợp lệ.

Một thứ tự hợp lệ còn phải bảo đảm đổi hai đối số thì đổi dấu, tính bắc cầu và sự tương thích với phần tử thứ ba khi so sánh bằng 0. Dùng tiêu chí nhãn có thể thay đổi giữa những đối tượng cùng định danh rồi ép chúng bằng nhau là cách dễ phá điều kiện này.

Đừng dựa vào việc “thêm vài phần tử vẫn chạy” để dùng quy tắc ấy trong cây. Chương trình chỉ tính các kết quả so sánh để chỉ ra mâu thuẫn, tránh tạo một cấu trúc dựa trên bộ so sánh đã biết là không hợp lệ.

## 9. Lựa chọn mô hình trước, rồi lựa chọn cấu trúc

Nếu nhận diện theo mã nhưng muốn hiển thị theo nhãn, hãy tách hai nhu cầu rõ ràng.

Một thiết kế thông dụng là lưu dữ liệu theo mã trong một ánh xạ, rồi tạo danh sách giá trị để sắp xếp theo nhãn khi cần hiển thị. Khi đó:

- Mã quyết định đối tượng nào là cùng thực thể và bản ghi nào được cập nhật.
- Bộ so sánh danh sách quyết định cách trình bày.
- Hai phiên bản nhãn của cùng mã không bị một `TreeSet` nhận thành hai thành viên chỉ vì chúng đứng khác vị trí.

Một lựa chọn khác là dùng các giá trị ảnh chụp bất biến có phép `equals` theo toàn bộ dữ liệu của ảnh chụp. Khi đó, hai ảnh chụp cùng mã nhưng nhãn khác có thể cố ý là hai giá trị khác nhau. Tuy nhiên, đây là thay đổi mô hình bằng nhau; phải phù hợp yêu cầu thực, không chỉ nhằm làm một bộ so sánh đang viết trở nên “đúng”.

Nếu yêu cầu thực sự là gom các từ mà không phân biệt chữ hoa/thường, có thể dùng một khóa chuẩn hóa hoặc một kiểu giá trị thể hiện quy tắc bằng nhau đó, cùng `hashCode` tương ứng. Đừng âm thầm để một tập xem `java/JAVA` là cùng giá trị trong khi các phần còn lại của hệ thống tiếp tục xem chúng là hai chuỗi khác nhau.

Với dữ liệu văn bản, quy tắc chuẩn hóa và so sánh phụ thuộc miền sử dụng. Ví dụ ASCII của bài không tự giải quyết mọi quy tắc ngôn ngữ hoặc chữ Unicode. Hãy xác định rõ ý nghĩa khóa thay vì sửa rải rác bằng các thao tác đổi chữ.

## 10. Chương trình đầy đủ kiểm chứng cả hai hướng

Lưu thành `ComparatorEqualsDemo.java`. Chương trình tự tạo dữ liệu, không cần thư viện ngoài JDK và dùng API có trong Java 17.

Hai hàm `matchesEqualsOnSample` và `sameZeroClassSignsOnSample` là công cụ học tập để kiểm tra một mẫu nhỏ. Chúng không phải bộ chứng minh đầy đủ cho mọi đầu vào của một `Comparator`.

~~~java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class ComparatorEqualsDemo {
    record Member(int id, String label) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Member member && id == member.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }

        @Override
        public String toString() {
            return id + ":" + label;
        }
    }

    static <T> boolean matchesEqualsOnSample(
            List<T> values, Comparator<? super T> rule) {
        for (T left : values) {
            for (T right : values) {
                if ((rule.compare(left, right) == 0) != Objects.equals(left, right)) {
                    return false;
                }
            }
        }
        return true;
    }

    static <T> boolean sameZeroClassSignsOnSample(
            List<T> values, Comparator<? super T> rule) {
        for (T left : values) {
            for (T right : values) {
                if (rule.compare(left, right) == 0) {
                    for (T third : values) {
                        if (Integer.signum(rule.compare(left, third))
                                != Integer.signum(rule.compare(right, third))) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    public static void main(String[] args) {
        Comparator<String> byLength = Comparator.comparingInt(String::length);
        SortedSet<String> coarse = new TreeSet<>(byLength);
        System.out.println("coarse add cat=" + coarse.add("cat"));
        System.out.println("coarse add dog=" + coarse.add("dog"));
        System.out.println("cat equals dog=" + "cat".equals("dog"));
        System.out.println("coarse compare=" + byLength.compare("cat", "dog"));
        System.out.println("coarse=" + coarse);
        System.out.println("coarse contains owl=" + coarse.contains("owl"));
        System.out.println("coarse remove owl=" + coarse.remove("owl"));
        System.out.println("coarse after remove=" + coarse);

        Comparator<String> lengthThenText =
                byLength.thenComparing(Comparator.naturalOrder());
        SortedSet<String> refined = new TreeSet<>(lengthThenText);
        refined.addAll(List.of("dog", "a", "cat", "cat"));
        System.out.println("refined=" + refined);
        System.out.println("refined consistent sample="
                + matchesEqualsOnSample(List.of("a", "cat", "dog"), lengthThenText));
        System.out.println("coarse consistent sample="
                + matchesEqualsOnSample(List.of("a", "cat", "dog"), byLength));

        Member alpha = new Member(7, "alpha");
        Member zulu = new Member(7, "zulu");
        Member mike = new Member(8, "mike");
        List<Member> members = List.of(alpha, zulu, mike);
        Comparator<Member> labelThenId =
                Comparator.comparing(Member::label).thenComparingInt(Member::id);
        SortedSet<Member> tooFine = new TreeSet<>(labelThenId);
        System.out.println("member equals=" + alpha.equals(zulu));
        System.out.println("label comparison sign="
                + Integer.signum(labelThenId.compare(alpha, zulu)));
        System.out.println("too fine first add=" + tooFine.add(alpha));
        System.out.println("too fine second add=" + tooFine.add(zulu));
        System.out.println("too fine=" + tooFine);
        System.out.println("too fine size=" + tooFine.size());
        System.out.println("too fine contains same id="
                + tooFine.contains(new Member(7, "probe")));

        Comparator<Member> byId = Comparator.comparingInt(Member::id);
        SortedSet<Member> identitySet = new TreeSet<>(byId);
        identitySet.add(alpha);
        System.out.println("by id second add=" + identitySet.add(zulu));
        System.out.println("by id=" + identitySet);
        System.out.println("by id contains same id="
                + identitySet.contains(new Member(7, "probe")));
        System.out.println("by id consistent sample="
                + matchesEqualsOnSample(members, byId));

        Comparator<Member> shortcut = (left, right) ->
                left.id() == right.id() ? 0 : labelThenId.compare(left, right);
        System.out.println("shortcut pair consistency="
                + matchesEqualsOnSample(members, shortcut));
        System.out.println("shortcut equal pair=" + shortcut.compare(alpha, zulu));
        System.out.println("shortcut third signs="
                + Integer.signum(shortcut.compare(alpha, mike)) + "/"
                + Integer.signum(shortcut.compare(zulu, mike)));
        System.out.println("shortcut zero class compatible="
                + sameZeroClassSignsOnSample(members, shortcut));

        Set<String> folded = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        folded.add("java");
        Set<String> exact = new HashSet<>(List.of("JAVA"));
        System.out.println("sorted equals hashed=" + folded.equals(exact));
        System.out.println("hashed equals sorted=" + exact.equals(folded));
        System.out.println("set hash codes equal=" + (folded.hashCode() == exact.hashCode()));

        BigDecimal shortForm = new BigDecimal("1.0");
        BigDecimal longForm = new BigDecimal("1.00");
        SortedSet<BigDecimal> numeric = new TreeSet<>();
        numeric.add(shortForm);
        System.out.println("decimal equals=" + shortForm.equals(longForm));
        System.out.println("decimal compare=" + shortForm.compareTo(longForm));
        System.out.println("numeric second add=" + numeric.add(longForm));
        System.out.println("numeric size=" + numeric.size());
        Comparator<BigDecimal> valueThenScale =
                Comparator.<BigDecimal>naturalOrder().thenComparingInt(BigDecimal::scale);
        SortedSet<BigDecimal> representations = new TreeSet<>(valueThenScale);
        representations.addAll(List.of(shortForm, longForm));
        System.out.println("representations=" + representations);
        System.out.println("representation consistent sample="
                + matchesEqualsOnSample(List.of(shortForm, longForm), valueThenScale));

        List<String> display = new ArrayList<>(List.of("cat", "dog", "cat"));
        display.sort(byLength);
        System.out.println("sorted list=" + display);
        System.out.println("sorted list size=" + display.size());
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 ComparatorEqualsDemo.java
java ComparatorEqualsDemo
~~~

Dùng JDK 17 trở lên. `--release 17` yêu cầu trình biên dịch kiểm tra mã và API theo Java 17. Tên lớp `public` phải khớp tên tệp.

Kết quả chuẩn:

~~~text
coarse add cat=true
coarse add dog=false
cat equals dog=false
coarse compare=0
coarse=[cat]
coarse contains owl=true
coarse remove owl=true
coarse after remove=[]
refined=[a, cat, dog]
refined consistent sample=true
coarse consistent sample=false
member equals=true
label comparison sign=-1
too fine first add=true
too fine second add=true
too fine=[7:alpha, 7:zulu]
too fine size=2
too fine contains same id=false
by id second add=false
by id=[7:alpha]
by id contains same id=true
by id consistent sample=true
shortcut pair consistency=true
shortcut equal pair=0
shortcut third signs=-1/1
shortcut zero class compatible=false
sorted equals hashed=true
hashed equals sorted=false
set hash codes equal=false
decimal equals=false
decimal compare=0
numeric second add=false
numeric size=1
representations=[1.0, 1.00]
representation consistent sample=true
sorted list=[cat, dog, cat]
sorted list size=3
~~~

## 11. Đọc kết quả theo từng hợp đồng

### 11.1. Gom quá nhiều giá trị

`coarse add dog=false` xác nhận rằng `dog` không có thành viên riêng sau `cat`. `cat equals dog=false` đi cùng `coarse compare=0` xác nhận đúng hướng sai lệch thứ nhất.

`coarse contains owl=true` và `coarse remove owl=true` cho thấy sai lệch ảnh hưởng cả truy vấn và xóa. Sau lời gọi xóa, tập rỗng dù đối tượng dùng để tìm là chuỗi chưa từng được thêm.

Không thể kết luận `remove` đã tìm cùng tham chiếu hoặc cùng nội dung chuỗi. Nó tìm thấy phần tử tương đương theo bộ so sánh.

### 11.2. Tiêu chí phụ khớp phép bằng nhau

`refined=[a, cat, dog]` giữ được các chuỗi khác nhau và vẫn loại bản `cat` trùng. `refined consistent sample=true` cho biết trên mẫu được đưa vào, mọi cặp đã khớp điều kiện `compare == 0` và `equals`.

Lập luận theo cấu trúc quy tắc còn mạnh hơn kết quả mẫu: với chuỗi khác `null`, so sánh toàn bộ nội dung ở tiêu chí cuối chỉ cho 0 khi nội dung bằng nhau. Vì vậy, ta biết lý do quy tắc đó phù hợp với `String.equals`, chứ không chỉ thấy ba từ được xử lý đúng.

### 11.3. Tách những đối tượng bằng nhau

`member equals=true` nhưng `label comparison sign=-1` cho biết hai thành viên cùng mã bị quy tắc nhãn tách ra. Cả hai lần thêm đều trả `true` và kích thước bằng 2.

`too fine contains same id=false` sử dụng một đối tượng mã 7, nhãn `probe`. Mặc dù nó bằng các thành viên mã 7 theo `equals`, tìm kiếm không gặp vị trí tương đương theo nhãn. Như vậy, không chỉ số lượng bị sai với ý nghĩa định danh; cách kiểm tra sự có mặt cũng bị lệch.

### 11.4. Sửa theo đúng định danh

Với `byId`, lần thêm cùng mã trả `false`, tập giữ `[7:alpha]` và truy vấn bằng mã 7 với nhãn khác trả `true`.

Điều này khớp phép bằng nhau của mô hình. Nếu mục tiêu là thay nhãn hiện có, cần thao tác cập nhật dữ liệu theo hợp đồng khác; không suy từ giá trị `false` của `add` rằng thông tin `zulu` đã được nhập vào đối tượng cũ.

### 11.5. Kiểm tra từng cặp chưa đủ

`shortcut pair consistency=true` xuất hiện trên mẫu ba thành viên, nhưng `shortcut third signs=-1/1` và `shortcut zero class compatible=false` chỉ ra mâu thuẫn với phần tử thứ ba.

Hàm kiểm tra cặp không sai: nó chỉ kiểm tra đúng một thuộc tính được giao. Sai lầm sẽ là diễn giải kết quả của hàm ấy như chứng minh đầy đủ về tính hợp lệ của bộ so sánh.

Đó cũng là cách đọc mọi kiểm thử: kết quả `true` chỉ có ý nghĩa trong phạm vi thuộc tính và dữ liệu đã kiểm tra.

## 12. Sai lệch có thể phá đối xứng của phép bằng nhau giữa hai tập

Chương trình tạo hai tập, mỗi tập một phần tử:

~~~text
folded: TreeSet dùng so sánh không phân biệt hoa/thường, chứa "java"
exact:  HashSet dùng String.equals, chứa "JAVA"
~~~

Không có thao tác đổi nội dung chuỗi. Hai cấu trúc đang dùng hai cách hiểu khác nhau về “có chứa giá trị này”.

Kết quả:

~~~text
sorted equals hashed=true
hashed equals sorted=false
~~~

Theo hợp đồng thông thường của `equals`, nếu `x.equals(y)` đúng thì `y.equals(x)` cũng phải đúng. Hai kết quả ở đây làm lộ sự không tương thích do tập sắp xếp dùng thứ tự không nhất quán với `String.equals`. Không nên diễn giải đây là quyền được chọn chiều gọi `equals` để lấy kết quả có lợi. [Java 17: Object.equals, tính đối xứng](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object)).

Lý do trong các lớp của ví dụ có thể theo dõi trực tiếp. Cách triển khai `equals` được kế thừa từ `AbstractSet` kiểm tra cùng kích thước rồi hỏi tập đang nhận lời gọi có chứa mọi thành viên của tập kia không.

- `folded.equals(exact)` dùng phép chứa của `folded`. Chuỗi `JAVA` tương đương với `java` theo bộ so sánh không phân biệt hoa/thường, nên kết quả là `true`.
- `exact.equals(folded)` dùng phép chứa của `HashSet` với `String.equals`. Chuỗi `java` khác `JAVA`, nên kết quả là `false`.

Đây là ví dụ một phần tử để loại bỏ ảnh hưởng của thứ tự duyệt hoặc kích thước. Vấn đề gốc là hai quan hệ bằng nhau không khớp.

Không phải mọi bộ dữ liệu với một comparator không nhất quán đều làm phép bằng nhau lộ sai ngay. Một ví dụ phản chứng là đủ để biết rằng bạn không còn có thể dựa vào hợp đồng chung.

## 13. hashCode vẫn quan trọng dù TreeSet không tìm bằng băm

Có thể nghĩ rằng dùng cây thì không cần quan tâm `hashCode`. Cần tách hai vai trò:

1. Cách `TreeSet` tìm vị trí thành viên chủ yếu dựa vào so sánh.
2. Bản thân tập và phần tử vẫn là đối tượng Java có hợp đồng `equals/hashCode`.

Trong chương trình, `Member.equals` được ghi đè theo mã nên `Member.hashCode` cũng dựa trên mã. Hai thành viên mã 7 bằng nhau phải có cùng mã băm, dù nhãn khác. Đây là yêu cầu của đối tượng, không phụ thuộc bạn đang đặt nó vào `HashSet` hay `TreeSet`.

Mã băm của một tập theo `AbstractSet` được tính từ mã băm các phần tử, không từ ý nghĩa của `Comparator`. Với ví dụ `java/JAVA`, chương trình in:

~~~text
set hash codes equal=false
~~~

Trong khi một chiều `equals` vừa trả `true`, kết quả này còn làm lộ vấn đề với yêu cầu những đối tượng bằng nhau phải có cùng mã băm. Đổi riêng hàm băm của một tập để gom theo bộ so sánh không sửa được toàn bộ sai lệch với `String.equals` ở các nơi khác. [Java 17: AbstractSet.equals và hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSet.html).

Không thể sửa một `Comparator` không nhất quán bằng cách thêm `hashCode` vào quá trình so sánh. Mã băm chỉ cần giống nhau cho các giá trị bằng nhau; nó được phép trùng với các giá trị khác nhau. Nó không cung cấp một thứ tự hoàn chỉnh thay cho mô hình giá trị.

## 14. Thứ tự tự nhiên cũng cần được kiểm tra: BigDecimal

Vấn đề không chỉ nằm ở lambda do bạn tự viết. `TreeSet` không nhận `Comparator` riêng sẽ dùng thứ tự tự nhiên từ `Comparable`, và quan hệ tương đương của `compareTo` cũng có thể khác `equals`.

Với:

~~~text
new BigDecimal("1.0")
new BigDecimal("1.00")
~~~

`compareTo` trả 0 vì cùng giá trị số. `equals` trả `false` vì ngoài giá trị số còn xét cách biểu diễn theo `scale`. Trong mẫu này, thang đo lần lượt là 1 và 2.

Chương trình cho:

~~~text
numeric second add=false
numeric size=1
~~~

Đây là hành vi theo quan hệ thứ tự tự nhiên của kiểu. Tài liệu `BigDecimal` có nêu sự khác nhau đó và lưu ý khi dùng trong các cấu trúc sắp xếp. [Java 17: BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html).

Nếu muốn phân biệt các biểu diễn theo đúng `BigDecimal.equals`, ví dụ dùng:

~~~java
Comparator<BigDecimal> valueThenScale =
        Comparator.<BigDecimal>naturalOrder().thenComparingInt(BigDecimal::scale);
~~~

So sánh giá trị số trước; khi cùng giá trị thì so sánh `scale`. Cùng giá trị số và cùng `scale` phù hợp với phép bằng nhau của `BigDecimal`. Hai biểu diễn trên được giữ thành `[1.0, 1.00]`.

Đây không phải phép làm tròn, cũng không đổi giá trị số hay thang đo của đối tượng. Nó chỉ chọn một thứ tự phân biệt thêm điều mà `equals` đang phân biệt.

Nếu nghiệp vụ chỉ cần duy nhất theo giá trị số, hãy chọn mô hình khóa thể hiện đúng ý đó và dùng nhất quán. Không tự suy rằng khác biệt biểu diễn luôn quan trọng hoặc luôn phải bị loại; đó là quyết định dữ liệu, còn bài học chỉ chỉ ra hai quan hệ hiện có.

## 15. Một Comparator không nhất quán vẫn có thể dùng để sắp danh sách

Không nhất quán với `equals` không tự động có nghĩa phép so sánh vi phạm mọi hợp đồng. Bộ so sánh theo độ dài của chuỗi vẫn cho một thứ tự nhóm rõ ràng và có thể dùng để sắp một danh sách các từ.

Phần cuối chương trình cho:

~~~text
sorted list=[cat, dog, cat]
sorted list size=3
~~~

`List.sort` sắp các phần tử; nó không thực hiện việc loại trùng kiểu `TreeSet`. Vì vậy, dùng tiêu chí độ dài không làm `dog` hoặc bản `cat` lặp biến mất. [Java 17: List.sort](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator)).

Phân biệt hai yêu cầu:

| Nhu cầu | Cấu trúc và quy tắc cần cân nhắc |
| --- | --- |
| Giữ mọi bản ghi, chỉ thay trình tự hiển thị | Danh sách và một Comparator hợp lệ cho cách trình bày |
| Giữ một phần tử cho mỗi giá trị theo equals, luôn có thứ tự | Tập sắp xếp với thứ tự nhất quán với equals |
| Nhận diện và cập nhật theo mã, trình bày theo trường khác | Cấu trúc lưu theo mã, rồi danh sách/khung nhìn phục vụ trình bày |
| Cố ý gom theo một khóa riêng | Mô hình khóa hoặc nhóm rõ ràng, không đánh đồng ngầm với equals của dữ liệu gốc |

`Comparator` dùng cho danh sách vẫn phải tuân thủ hợp đồng so sánh. Ví dụ `shortcut` sai với phần tử thứ ba không trở thành hợp lệ chỉ vì chuyển từ cây sang danh sách.

Điều được phép khác ở đây là quan hệ bằng 0 so với `equals` khi thao tác chỉ cần một thứ tự sắp xếp thích hợp. Không phải mọi lần hai đối tượng cùng hạng đều phải khiến chúng trở thành cùng thực thể của miền nghiệp vụ.

## 16. Cách kiểm tra có ý nghĩa

Đối với một quy tắc mới, nên chuẩn bị các cặp theo đặc tính của mô hình:

- Cùng tham chiếu.
- Khác tham chiếu nhưng bằng nhau theo `equals`.
- Cùng khóa sắp xếp chính nhưng khác một thuộc tính mà `equals` xét.
- Khác khóa sắp xếp chính.
- Những giá trị biên của các trường số hoặc chuỗi.
- Các giá trị `null` chỉ khi hợp đồng thực sự cho phép chúng.

Hàm `matchesEqualsOnSample` so sánh mọi cặp trong mẫu và kiểm tra hai mệnh đề `compare == 0` và `Objects.equals` có cùng giá trị `boolean` không. Nó không cần biết bộ so sánh trả -1, -20 hay 20; chỉ cần phân biệt 0 với khác 0 cho thuộc tính này.

Ngoài ra, kiểm tra các thuộc tính của phép so sánh:

~~~text
đổi hai đối số → đảo dấu
a trước b, b trước c → a trước c
a tương đương b → chúng phải có cùng hướng so với mọi c
~~~

Hàm `sameZeroClassSignsOnSample` chỉ kiểm tra thuộc tính cuối trên mẫu, không kiểm tra đầy đủ tất cả các thuộc tính còn lại. Tên hàm cố ý giới hạn điều nó khẳng định.

Chi phí của kiểm tra cặp tăng theo bình phương số phần tử; kiểm tra bộ ba tăng theo lập phương. Các hàm trong bài phù hợp với mẫu nhỏ được chọn để giải thích và tìm phản ví dụ, không phải thao tác cần chạy lại sau mỗi lần thêm vào tập thật.

Một mẫu kiểm thử đi qua không chứng minh đúng cho miền vô hạn. Kết hợp bằng chứng chạy với lập luận về những thuộc tính được `equals` và `Comparator` dùng. Mẫu có hai phiên bản nhãn của cùng mã thường có ích hơn rất nhiều bản ghi mà mọi mã và mọi nhãn đều khác nhau.

## 17. Dữ liệu và quy tắc phải ổn định trong thời gian lưu

Tính nhất quán không chỉ là ảnh chụp tại một thời điểm. Nếu bạn thay đổi trường dùng trong phép so sánh khi đối tượng còn nằm trong `TreeSet`, cây không tự biết phải tổ chức lại vị trí.

Nếu `equals` dựa vào một nhóm trường và `Comparator` dựa vào nhóm trường khác, cập nhật dữ liệu có thể vừa làm sai cách tìm trong cây, vừa thay đổi quan hệ giữa hai đối tượng.

Cách dễ kiểm soát là dùng giá trị bất biến cho các thuộc tính liên quan đến thứ tự và nhận diện. Ví dụ `Member` dùng các thành phần record không có thao tác gán lại; hai nhãn khác nhau được biểu diễn bằng hai đối tượng khác nhau để ta quan sát quy tắc rõ ràng.

Không giữ một bộ so sánh phụ thuộc vào một cấu hình thay đổi ngầm, chẳng hạn một cờ đảo chiều được đổi sau khi đã tạo tập. Một thứ tự đúng ở thời điểm thêm nhưng đổi nghĩa khi tìm không phải nền tảng ổn định cho cấu trúc sắp xếp.

Cũng không dùng các lối phân biệt giả như trả 1 với hai phần tử tương đương, lấy số ngẫu nhiên hoặc lấy địa chỉ tưởng tượng để buộc tập giữ mọi thứ. Chúng không thay thế mô hình giá trị và có thể phá điều kiện dấu hoặc bắc cầu.

## 18. Những nhầm lẫn cần sửa trong cách đọc mã

| Nhầm lẫn | Điều cần kiểm tra |
| --- | --- |
| “Hai đối tượng khác nhau nên compare phải khác 0” | “Khác tham chiếu” không đồng nghĩa “khác theo equals” |
| “Chỉ cần compare bằng 0 thì equals đúng” | Cần cả chiều equals đúng thì compare bằng 0 |
| “TreeSet sẽ dùng equals nếu comparator phân biệt chưa đủ” | Tập dùng kết quả so sánh để nhận diện tương đương |
| “Thêm thenComparing(id) luôn sửa được” | Kiểm tra trường hợp cùng id nhưng khác tiêu chí đứng trước |
| “Trả 0 sớm khi equals đúng là đủ” | Phải kiểm tra quan hệ với phần tử thứ ba và tính bắc cầu |
| “HashCode là tiêu chí phụ duy nhất đáng tin cậy” | Mã băm được phép va chạm |
| “Có cùng hashCode nghĩa là equals đúng” | Hợp đồng chỉ yêu cầu chiều bằng nhau thì cùng mã băm |
| “Record mặc định nhận diện theo id” | Record mặc định xét các thành phần; bài có ghi đè tường minh cho Member |
| “Một chiều set.equals đúng là đủ” | equals phải đối xứng và tương thích với hashCode |
| “Thứ tự tự nhiên có sẵn chắc chắn khớp equals” | BigDecimal là một trường hợp cần biết |
| “Không nhất quán với equals thì không thể dùng comparator để sort” | Một Comparator hợp lệ vẫn có thể dùng cho danh sách; tách yêu cầu sắp xếp và duy nhất |
| “Mẫu kiểm tra đúng là chứng minh mọi dữ liệu đúng” | Xét miền dữ liệu, thuộc tính mô hình và các phản ví dụ có chủ đích |
| “equals ở đây là Comparator.equals” | Đang kiểm tra equals của phần tử, không phải của đối tượng bộ so sánh |

Các lỗi thường khó thấy nhất khi mỗi khóa sắp xếp trong mẫu đều khác nhau. Hãy chủ động tạo dữ liệu trùng khóa chính và các phiên bản khác thông tin nhưng cùng định danh trước khi kết luận.

## 19. Thực hành có lời giải

### Bài 1: kiểm tra hai chiều

Một cặp đối tượng có `a.equals(b) == false` và `compare(a,b) == 0`. Cặp đó có nhất quán không? Nếu đảo lại thành `equals == true` và `compare != 0` thì sao?

**Lời giải:** Cả hai đều không nhất quán. Trường hợp đầu bộ so sánh gom quá nhiều giá trị; trường hợp sau tách những giá trị mà equals xem là một. Phải kiểm tra điều kiện hai chiều.

### Bài 2: dự đoán tìm và xóa

Tập chuỗi dùng bộ so sánh chỉ theo độ dài, hiện chứa `"red"`. `contains("sky")` và `remove("sky")` trả gì?

**Lời giải:** Cả hai trả `true` trong ví dụ này vì cùng độ dài ba. Lần xóa loại đại diện `red` khỏi tập. Nó không yêu cầu chuỗi sky từng được thêm.

### Bài 3: một tiêu chí phụ chưa đủ

Bạn so sánh chuỗi theo độ dài, rồi theo ký tự đầu. `"cat"` và `"car"` có còn bị gom không?

**Lời giải:** Có. Chúng cùng độ dài và cùng ký tự đầu, nên cả hai tiêu chí đều cho 0. Với phép bằng nhau của String, cần tiêu chí cuối đủ để phân biệt toàn bộ nội dung, chẳng hạn thứ tự tự nhiên của chuỗi.

### Bài 4: mã trước, nhãn sau

Member có equals chỉ theo id. Comparator so sánh id trước, nếu bằng thì so sánh label. Hai giá trị `5:old` và `5:new` có nhất quán không?

**Lời giải:** Không. Equals xem cùng mã 5 là bằng nhau, nhưng tiêu chí label tách chúng thành kết quả so sánh khác 0. Đưa id lên đầu không tự bỏ ảnh hưởng của tiêu chí phụ.

### Bài 5: vì sao nhánh trả 0 chưa đủ?

Cho `a=7:alpha`, `b=7:zulu` và `c=8:mike`. Quy tắc trả 0 khi cùng mã, còn khác mã thì so sánh nhãn. Điều kiện nào bị phá?

**Lời giải:** a và b so sánh bằng 0 nhưng a đứng trước c, b đứng sau c. Những đối tượng tương đương về thứ tự phải có cùng dấu khi so với phần tử thứ ba. Quy tắc này vi phạm hợp đồng Comparator, dù phép kiểm tra compare bằng 0 khớp equals trên mẫu.

### Bài 6: lựa chọn mô hình

Bạn cần một người duy nhất theo mã, thường cập nhật tên hiển thị và đôi khi xuất danh sách theo tên. Có nên dùng TreeSet sắp theo tên rồi mã để vừa lưu vừa cập nhật không?

**Lời giải:** Cần thận trọng: hai phiên bản tên của cùng mã có thể trở thành hai phần tử nếu equals theo mã. Một cấu trúc lưu/cập nhật theo mã và danh sách được sắp theo tên khi trình bày thường tách đúng hai nhu cầu. Chọn mô hình trước khi chọn bộ so sánh.

### Bài 7: hai tập và đối xứng

Một TreeSet không phân biệt hoa/thường chứa `"abc"`; một HashSet chứa `"ABC"`. Có nên dùng kết quả một chiều equals để khẳng định hai tập trao đổi được trong mọi mã xử lý Set không?

**Lời giải:** Không. Hai tập đang dùng hai quan hệ nhận diện khác nhau. Như chương trình đã chỉ ra với java/JAVA, phép bằng nhau có thể khác khi đổi chiều và gây vấn đề hợp đồng hashCode. Cần thống nhất mô hình khóa, không chọn chiều gọi thuận lợi.

### Bài 8: sort danh sách

Một danh sách chứa `cat, dog, cat` được sắp theo độ dài. Comparator không nhất quán với String.equals có làm danh sách tự bỏ dog không?

**Lời giải:** Không. Sắp xếp danh sách không phải loại trùng theo cơ chế của TreeSet. Ba phần tử vẫn tồn tại. Comparator vẫn phải hợp lệ về thứ tự; điều khác là danh sách không dùng compare bằng 0 để quyết định bỏ thành viên.

### Bài 9: BigDecimal

Bạn muốn giữ phân biệt `3.0` và `3.00` theo BigDecimal.equals. Chỉ dùng thứ tự tự nhiên trong TreeSet có đáp ứng không? Một tiêu chí nào có thể bổ sung sau so sánh giá trị?

**Lời giải:** Thứ tự tự nhiên cho compareTo bằng 0 nên không giữ hai thành viên riêng. Ví dụ trong bài bổ sung scale sau so sánh giá trị số để phân biệt các biểu diễn theo phép bằng nhau của BigDecimal. Việc đó không làm tròn hoặc thay dữ liệu.

### Bài 10: đọc phạm vi của một kiểm tra

`matchesEqualsOnSample` trả true trên mười đối tượng. Có thể kết luận comparator hợp lệ và nhất quán trên mọi dữ liệu của lớp không?

**Lời giải:** Không. Hàm chỉ kiểm tra quan hệ compare bằng 0 với equals trên các cặp thuộc mẫu, chưa kiểm tra đầy đủ hợp đồng thứ tự và chưa bao phủ toàn miền. Ví dụ shortcut là bằng chứng trực tiếp rằng một thuộc tính đi qua không thay thế thuộc tính khác.

## 20. Quy trình ngắn khi xem lại một Comparator dùng cho tập

Trước hết, viết bằng lời định nghĩa `equals` của phần tử. Sau đó, viết điều kiện chính xác để `Comparator` trả 0. Hai điều kiện ấy phải trùng nhau trên miền dữ liệu được phép nếu muốn tuân theo hợp đồng chung của `Set`.

Tiếp theo, kiểm tra tính hợp lệ của thứ tự với bộ ba phần tử, đặc biệt khi có nhánh xử lý riêng cho “cùng mã”. Xem những trường dùng để so sánh có thay đổi trong thời gian lưu không.

Cuối cùng, thử cả `add`, `contains` và `remove` bằng một đối tượng khác tham chiếu nhưng cùng giá trị nghiệp vụ. Khi hệ thống trao đổi dữ liệu giữa các cách triển khai Set, kiểm tra cả hai chiều equals và sự phù hợp của hashCode.

Nếu quy tắc bạn cần chỉ phục vụ hiển thị, hãy đặt nó ở lớp xử lý trình tự phù hợp. Không buộc một tập sắp xếp đồng thời giải quyết hai định nghĩa khác nhau về “cùng một phần tử”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sorted-set-005">first, last, headSet, tailSet và subSet</a>

<details>
<summary>Xem chi tiết</summary>

# first, last, headSet, tailSet và subSet

## 1. Làm việc với hai đầu và các khoảng của một Set có thứ tự

Giả sử ứng dụng giữ các mức giá duy nhất theo thứ tự. Nó cần biết mức thấp nhất, mức cao nhất, các mức trước một ngưỡng hoặc các mức nằm giữa hai ngưỡng. Nếu dữ liệu đã nằm trong một Set sắp xếp, không cần chuyển sang một List rồi tự quét lại chỉ để biểu đạt những yêu cầu ấy.

Năm phương thức của `SortedSet` phục vụ hai nhóm thao tác:

- `first()`, `last()`: đọc một phần tử ở hai đầu theo thứ tự.
- `headSet(...)`, `tailSet(...)`, `subSet(...)`: tạo một **góc nhìn theo khoảng** trên tập hiện có.

Điểm dễ nhầm nhất là nhóm thứ hai trả về một góc nhìn liên kết với dữ liệu gốc, không phải một bản sao được lọc độc lập. Khi dùng TreeSet, các thay đổi hợp lệ qua góc nhìn có thể cập nhật trực tiếp tập gốc.

Bài này dùng TreeSet để minh họa `SortedSet` và các overload của `NavigableSet`. Các quy tắc API được đối chiếu với [SortedSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SortedSet.html) và [NavigableSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableSet.html).

Các thuật ngữ cần phân biệt:

| Thuật ngữ | Ý nghĩa trong bài |
|---|---|
| Tập gốc | Collection giữ dữ liệu mà góc nhìn đang dựa vào |
| Góc nhìn (view) | Một cách truy cập tập gốc với giới hạn và cùng dữ liệu nền |
| Biên dưới, biên trên | Các mốc giới hạn khoảng theo thứ tự của Set |
| Biên đóng, inclusive | Cho phép phần tử tương đương với mốc biên |
| Biên mở, exclusive | Không cho phép phần tử tương đương với mốc biên |
| Bản sao, bản chụp | Một collection riêng được tạo từ dữ liệu ở thời điểm sao chép |

“Trước”, “sau”, “đầu” và “cuối” luôn phải được đọc theo **thứ tự của Set**, không mặc định theo số học tăng dần.

## 2. first và last: đọc phần tử, không xóa phần tử

Với Set theo thứ tự số nguyên tăng dần:

```text
[10, 20, 30, 40, 50]
 ^               ^
first           last
```

`first()` trả về `10`, `last()` trả về `50`. Hai lời gọi không gỡ chúng khỏi tập.

Đây không phải hai chỉ số của một List:

- `first` là thành viên đứng đầu theo thứ tự.
- `last` là thành viên đứng cuối theo thứ tự.
- SortedSet không cung cấp `get(index)` chỉ vì nó có một thứ tự.
- Trong TreeSet, phần tử đầu không nhất thiết là nút gốc của cây nội bộ.

Nếu gọi trên một góc nhìn, hai phương thức chỉ xét những thành viên nằm trong góc nhìn ấy. Ví dụ, với khoảng `[20, 50)` của tập trên, `first` là `20`, còn `last` là `40`.

### 2.1. Tập rỗng là một trường hợp cần xử lý

`first()` và `last()` ném `NoSuchElementException` nếu tập đang xét rỗng. “Tập đang xét” có thể là tập gốc có dữ liệu nhưng góc nhìn không có thành viên.

Ví dụ, tập gốc chứa `10, 20, 30`, còn khoảng `[21, 29)` không chứa phần tử nào. Gọi `first` trên khoảng ấy vẫn thất bại.

Trong luồng đơn luồng hoặc đã được phối hợp đúng, có thể kiểm tra `isEmpty()` khi việc không có dữ liệu là bình thường. Không suy luận rằng `first` sẽ trả về `null` để báo rỗng. Nếu comparator cho phép một thành viên `null`, first có thể trả về chính thành viên ấy; đó vẫn là trường hợp có dữ liệu, không phải dấu hiệu tập rỗng.

Các phương thức `pollFirst` và `pollLast` của NavigableSet có hành vi khác: chúng vừa lấy vừa gỡ phần tử và trả về `null` khi không có. Không thay `first` bằng `pollFirst` chỉ để tránh ngoại lệ nếu không muốn thay đổi dữ liệu.

### 2.2. “Nhỏ nhất” phụ thuộc quan hệ thứ tự

Với comparator giảm dần, dãy duyệt có thể là:

```text
[50, 40, 30, 20, 10]
 ^               ^
first           last
```

Lúc này `first()` trả về số lớn nhất theo giá trị số học. Vì vậy, gọi first là “đầu theo comparator” chính xác hơn gọi nó luôn là “số nhỏ nhất”.

## 3. Ba dạng khoảng mặc định của SortedSet

Trước hết xét thứ tự tự nhiên tăng dần, với tập:

```text
S = [10, 20, 30, 40, 50]
```

Các overload không có cờ boolean có quy tắc cố định:

| Lời gọi | Điều kiện theo thứ tự | Ký hiệu khoảng | Ví dụ |
|---|---|---|---|
| `S.headSet(b)` | Phần tử đứng trước b | `(-∞, b)` | `headSet(30)` → `[10, 20]` |
| `S.tailSet(a)` | Phần tử từ a trở đi, gồm a | `[a, +∞)` | `tailSet(30)` → `[30, 40, 50]` |
| `S.subSet(a, b)` | Từ a đến trước b | `[a, b)` | `subSet(20, 50)` → `[20, 30, 40]` |

Ký hiệu vô cực ở đây chỉ diễn tả phía không có biên bổ sung; không cần có một đối tượng “vô cực” trong Set.

Hai quy tắc nên đọc thành câu:

- `headSet` mặc định **không lấy mốc trên**.
- `tailSet` mặc định **có lấy mốc dưới**.

Vì vậy, `headSet(x)` và `tailSet(x)` có thể chia tập thành hai phần không chồng nhau theo cùng một mốc. Nếu thành viên tương đương x tồn tại, nó thuộc phía tail.

Với `subSet(a, b)`, phía đầu đóng và phía cuối mở. Đây là khoảng nửa mở, thường thuận tiện khi chia các vùng liền kề.

### 3.1. Biên là mốc so sánh, không cần là thành viên

Với `S = [10, 20, 30, 40, 50]`:

```text
headSet(25)      -> [10, 20]
tailSet(25)      -> [30, 40, 50]
subSet(25, 45)   -> [30, 40]
```

Không có `25` hoặc `45` trong Set vẫn tạo khoảng hợp lệ. Các mốc được dùng để so sánh, không phải yêu cầu tìm đúng hai đối tượng đang lưu rồi cắt theo vị trí của chúng.

Do đó, không cần gọi `contains(a)` hoặc `contains(b)` như một điều kiện bắt buộc trước khi tạo subSet. Điều cần kiểm tra là mốc có so sánh được, có đúng thứ tự và có nằm trong giới hạn của góc nhìn cha hay không.

### 3.2. Khoảng hợp lệ vẫn có thể rỗng

`subSet(21, 29)` của tập trên hợp lệ nhưng hiện không có phần tử. Điều đó khác với việc tham số sai.

Nếu sau này thêm `25` vào tập gốc, góc nhìn `[21, 29)` có thể thấy nó. Hai mốc vẫn giữ nguyên; nội dung hiện có thay đổi theo tập gốc.

Với `subSet(30, 30)`, khoảng là `[30, 30)`: không giá trị nào vừa từ 30 trở đi vừa đứng trước 30. Khoảng đó rỗng kể cả khi tập gốc có phần tử 30.

## 4. Chương trình 1: biên mặc định và hai đầu

Lưu tệp `SortedSetBoundsDemo.java`:

```java
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;

public class SortedSetBoundsDemo {
    public static void main(String[] args) {
        SortedSet<Integer> values =
                new TreeSet<>(List.of(10, 20, 30, 40, 50));

        System.out.println("values = " + values);
        System.out.println("first = " + values.first());
        System.out.println("last = " + values.last());
        System.out.println("after first and last = " + values);
        System.out.println("headSet 30 = " + values.headSet(30));
        System.out.println("tailSet 30 = " + values.tailSet(30));
        System.out.println("subSet 20 to 50 = " + values.subSet(20, 50));
        System.out.println("headSet 25 = " + values.headSet(25));
        System.out.println("tailSet 25 = " + values.tailSet(25));
        System.out.println("subSet 25 to 45 = " + values.subSet(25, 45));

        SortedSet<Integer> empty = values.subSet(30, 30);
        System.out.println("equal endpoints = " + empty);
        System.out.println("equal endpoints empty = " + empty.isEmpty());

        try {
            empty.first();
        } catch (NoSuchElementException ex) {
            System.out.println("empty first = "
                    + ex.getClass().getSimpleName());
        }
        try {
            empty.last();
        } catch (NoSuchElementException ex) {
            System.out.println("empty last = "
                    + ex.getClass().getSimpleName());
        }
        try {
            values.subSet(50, 20);
        } catch (IllegalArgumentException ex) {
            System.out.println("reversed endpoints = "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 SortedSetBoundsDemo.java
java SortedSetBoundsDemo
```

Kết quả của lần chạy kiểm chứng:

```text
values = [10, 20, 30, 40, 50]
first = 10
last = 50
after first and last = [10, 20, 30, 40, 50]
headSet 30 = [10, 20]
tailSet 30 = [30, 40, 50]
subSet 20 to 50 = [20, 30, 40]
headSet 25 = [10, 20]
tailSet 25 = [30, 40, 50]
subSet 25 to 45 = [30, 40]
equal endpoints = []
equal endpoints empty = true
empty first = NoSuchElementException
empty last = NoSuchElementException
reversed endpoints = IllegalArgumentException
```

Mã dùng ngôn ngữ và API có ở Java 17 trở lên. Các chương trình trong bài được biên dịch với `--release 17` và chạy kiểm chứng bằng Azul OpenJDK 24.0.2.

Những điểm cần đối chiếu từ output:

1. Sau `first` và `last`, tập vẫn giữ nguyên.
2. `headSet(30)` không chứa 30, còn `tailSet(30)` có chứa 30.
3. `subSet(20, 50)` chứa 20 nhưng không chứa 50.
4. Biên không tồn tại như 25 hoặc 45 không tự gây lỗi.
5. Hai biên bằng nhau theo overload mặc định tạo khoảng rỗng.
6. Đảo thứ tự hai biên trong thứ tự tăng dần gây `IllegalArgumentException`.

Đừng bắt tất cả các tình huống này bằng một xử lý chung “không có dữ liệu”. Khoảng rỗng, tập rỗng và tham số sai là những trạng thái có ý nghĩa khác nhau.

## 5. NavigableSet cho phép chọn biên đóng hoặc mở

Các overload có boolean cho phép biểu đạt rõ việc có lấy biên hay không:

```java
headSet(toElement, inclusive)
tailSet(fromElement, inclusive)
subSet(fromElement, fromInclusive, toElement, toInclusive)
```

Đây là chữ ký minh họa cách gọi, không phải các khai báo phương thức độc lập trong một chương trình.

Các dạng khoảng hai phía tương ứng:

| Lời gọi | Kiểu khoảng |
|---|---|
| `subSet(a, true, b, false)` | `[a, b)` |
| `subSet(a, true, b, true)` | `[a, b]` |
| `subSet(a, false, b, false)` | `(a, b)` |
| `subSet(a, false, b, true)` | `(a, b]` |

Trên tập `[10, 20, 30, 40, 50]`, khoảng `(20, 50]` là `[30, 40, 50]`.

Không cố mô phỏng biên đóng bằng cách cộng `1` vào mốc trên cho mọi kiểu dữ liệu. Cách đó có thể tràn với số nguyên, không áp dụng trực tiếp cho chuỗi và không giải quyết tổng quát các miền thời gian. Overload boolean diễn tả chính xác ý định mà không cần phát minh “giá trị kế tiếp”.

### 5.1. Các overload mặc định tương đương dạng nào?

Với một NavigableSet, có thể hiểu các lời gọi cũ như:

```text
headSet(b)       tương đương headSet(b, false)
tailSet(a)       tương đương tailSet(a, true)
subSet(a, b)     tương đương subSet(a, true, b, false)
```

Sự tương đương ở đây là về khoảng được biểu đạt. Kiểu trả về khai báo của các overload cũ trong Java 17 vẫn là `SortedSet`; các overload có cờ trả về `NavigableSet`.

Ví dụ:

```java
// Trích đoạn; cần import java.util.NavigableSet, SortedSet và TreeSet.
NavigableSet<Integer> values = new TreeSet<>();

SortedSet<Integer> defaultRange = values.subSet(20, 50);
NavigableSet<Integer> explicitRange =
        values.subSet(20, true, 50, false);
```

Nếu biến chỉ có kiểu `SortedSet`, không thể gọi trực tiếp overload boolean qua biến ấy. Hãy khai báo kiểu phù hợp với khả năng cần dùng, thay vì ép kiểu tùy tiện một SortedSet bất kỳ thành NavigableSet.

### 5.2. Hai biên tương đương không phải lúc nào cũng rỗng

Khi hai mốc tương đương theo comparator:

- Dạng `[a, a]` có thể chứa thành viên tương đương a.
- Các dạng có ít nhất một phía mở đều rỗng.

Nếu chưa có thành viên tương đương a, góc nhìn `[a, a]` ban đầu vẫn rỗng nhưng có thể cho phép thêm một thành viên tương đương ấy về sau. Ngược lại, `[a, a)` không có vị trí hợp lệ nào để thêm.

“Bằng nhau” ở đây phải đọc theo kết quả so sánh bằng 0, không chỉ theo việc hai mốc là cùng tham chiếu hoặc `equals` trả về true.

## 6. Chương trình 2: biên đóng/mở và góc nhìn lồng nhau

Lưu tệp `NavigableSetInclusiveDemo.java`:

```java
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public class NavigableSetInclusiveDemo {
    public static void main(String[] args) {
        NavigableSet<Integer> values =
                new TreeSet<>(List.of(10, 20, 30, 40, 50));

        System.out.println("head 30 exclusive = "
                + values.headSet(30, false));
        System.out.println("head 30 inclusive = "
                + values.headSet(30, true));
        System.out.println("tail 30 exclusive = "
                + values.tailSet(30, false));
        System.out.println("tail 30 inclusive = "
                + values.tailSet(30, true));
        System.out.println("sub (20, 50] = "
                + values.subSet(20, false, 50, true));
        System.out.println("sub [30, 30] = "
                + values.subSet(30, true, 30, true));
        System.out.println("sub [30, 30) = "
                + values.subSet(30, true, 30, false));
        System.out.println("sub (30, 30) = "
                + values.subSet(30, false, 30, false));

        NavigableSet<Integer> parent = values.subSet(20, false, 50, false);
        System.out.println("parent (20, 50) = " + parent);
        System.out.println("child excludes parent endpoint = "
                + parent.headSet(50, false));
        try {
            parent.headSet(50, true);
        } catch (IllegalArgumentException ex) {
            System.out.println("child includes excluded parent endpoint = "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 NavigableSetInclusiveDemo.java
java NavigableSetInclusiveDemo
```

Kết quả của lần chạy kiểm chứng:

```text
head 30 exclusive = [10, 20]
head 30 inclusive = [10, 20, 30]
tail 30 exclusive = [40, 50]
tail 30 inclusive = [30, 40, 50]
sub (20, 50] = [30, 40, 50]
sub [30, 30] = [30]
sub [30, 30) = []
sub (30, 30) = []
parent (20, 50) = [30, 40]
child excludes parent endpoint = [30, 40]
child includes excluded parent endpoint = IllegalArgumentException
```

Bốn dòng đầu chỉ thay cờ inclusive nhưng làm thay đổi việc giữ lại phần tử đúng mốc 30. Đây là lỗi lệch biên phổ biến nếu chỉ đọc tên `headSet` hoặc `tailSet` rồi bỏ qua overload đang gọi.

Phần hai biên cùng là 30 cho thấy `[30, 30]` giữ 30, còn `[30, 30)` và `(30, 30)` rỗng.

### 6.1. Khoảng con không được mở rộng khoảng cha

Khoảng cha trong chương trình là `(20, 50)`, hiện chứa `[30, 40]`.

Lời gọi:

```java
parent.headSet(50, false)
```

giữ cùng giới hạn trên mở, nên hợp lệ. Nhưng:

```java
parent.headSet(50, true)
```

yêu cầu một biên đóng tại 50, trong khi chính 50 bị khoảng cha loại trừ. Trường hợp này bị từ chối bằng `IllegalArgumentException`.

Điều cần kiểm tra là **phạm vi được phép**, không chỉ các phần tử đang xuất hiện. Dù trong ví dụ hiện tại cả hai yêu cầu trông như sẽ in ra cùng `[30, 40]`, yêu cầu thứ hai vẫn cố thay đổi quyền bao gồm mốc mà cha không cho phép.

### 6.2. Không mặc định các khoảng lồng nhau tự lấy giao

Nếu khoảng cha là `[20, 50)`, gọi `parent.headSet(60)` không có nghĩa “cứ lấy tất cả phần tử của cha vì chúng đều nhỏ hơn 60”. Mốc mới vượt ra ngoài phạm vi cha và có thể bị từ chối.

Hãy xác định biên phù hợp trước khi gọi. Nếu mục tiêu là giao của hai điều kiện độc lập, tính biên giao theo comparator và kiểm tra đóng/mở của chúng, rồi tạo một khoảng nằm trong phạm vi hợp lệ.

Các mốc đúng tại biên mở của cha cần đặc biệt cẩn thận: việc yêu cầu bao gồm hay loại trừ mốc ấy có thể quyết định lời gọi có hợp lệ không, như chương trình vừa minh họa.

## 7. Góc nhìn liên kết: cùng dữ liệu, thêm một hàng rào phạm vi

Một mô hình dễ nhớ là:

```text
tập gốc:     10  20  30  40  50  60
                 |----------|
góc nhìn:       [20, 50)

Không có một bản sao riêng của 20, 30, 40.
Góc nhìn chỉ cho phép làm việc với vùng tương ứng trên cùng dữ liệu.
```

Theo hợp đồng các phương thức tạo khoảng của SortedSet, góc nhìn được hỗ trợ bởi tập gốc: thay đổi qua một phía được phản ánh ở phía còn lại. Điều này bao gồm dữ liệu được thêm hoặc gỡ sau lúc góc nhìn được tạo.

Với TreeSet có thể sửa:

- Thêm vào tập gốc một giá trị trong khoảng thì góc nhìn thấy giá trị ấy.
- Gỡ một giá trị trong khoảng qua góc nhìn thì tập gốc mất giá trị ấy.
- Thêm qua góc nhìn một giá trị hợp lệ trong khoảng thì tập gốc có thêm.
- Thêm qua góc nhìn một giá trị ngoài khoảng bị từ chối.
- `clear()` qua góc nhìn gỡ các thành viên hiện nằm trong khoảng khỏi tập gốc.

Một view không có nghĩa tự động chỉ đọc. Nó cũng không có nghĩa một bảng dữ liệu độc lập.

### 7.1. Biên cố định, nội dung có thể thay đổi

Tạo `window = root.subSet(20, true, 50, false)` xác lập khoảng `[20, 50)`. Nếu root được thêm 25 hoặc 35, window thay đổi nội dung nhưng không tự đổi hai biên.

Nếu gỡ mọi phần tử hiện có trong khoảng, window có thể trở thành rỗng. Sau đó, thêm lại một giá trị hợp lệ qua root hoặc window vẫn có thể làm nó có dữ liệu.

Không dùng `window.first()` và `window.last()` như cách lấy lại hai mốc khai báo. Chúng trả về phần tử hiện có, có thể khác mốc và có thể không tồn tại khi khoảng rỗng.

### 7.2. Thêm ngoài khoảng khác với không có thành viên

Trong một góc nhìn số nguyên `[20, 50)`, thêm `50` vi phạm biên mở trên và gây `IllegalArgumentException`. Việc tập gốc có sẵn 50 không làm lời gọi ấy hợp lệ.

Với một giá trị so sánh hợp lệ nhưng nằm ngoài khoảng, các phép hỏi thành viên hoặc gỡ thường đơn giản không tìm thấy nó trong góc nhìn. Đừng đánh đồng điều đó với quyền thêm ngoài khoảng.

Các lỗi kiểu dữ liệu hoặc `null` không phù hợp comparator là vấn đề riêng; không dựa vào quy tắc ngoài khoảng để bỏ qua khả năng so sánh hợp lệ.

### 7.3. clear không xóa biến view

`window.clear()` không có nghĩa “bỏ liên kết của biến window và giữ root nguyên vẹn”. Nó là một phép thay đổi collection: gỡ những phần tử thuộc window.

Sau đó, biến window vẫn biểu đạt cùng góc nhìn trên cùng root. Nếu cần bỏ tham chiếu tới view, đó là thao tác quản lý biến; nếu cần giữ dữ liệu riêng rồi xóa cục bộ, trước hết phải tạo một bản sao phù hợp.

## 8. Chương trình 3: thay đổi qua góc nhìn và qua tập gốc

Lưu tệp `BackedRangeViewDemo.java`:

```java
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public class BackedRangeViewDemo {
    public static void main(String[] args) {
        NavigableSet<Integer> root =
                new TreeSet<>(List.of(10, 20, 30, 40, 50, 60));
        NavigableSet<Integer> window = root.subSet(20, true, 50, false);

        System.out.println("initial window = " + window);
        root.add(35);
        System.out.println("after root add 35 = " + window);

        System.out.println("window remove 30 = " + window.remove(30));
        System.out.println("root after remove = " + root);
        System.out.println("window add 25 = " + window.add(25));
        System.out.println("root after add = " + root);

        TreeSet<Integer> snapshot = new TreeSet<>(window);

        try {
            window.add(50);
        } catch (IllegalArgumentException ex) {
            System.out.println("add excluded endpoint = "
                    + ex.getClass().getSimpleName());
        }
        try {
            window.headSet(60);
        } catch (IllegalArgumentException ex) {
            System.out.println("widen child bound = "
                    + ex.getClass().getSimpleName());
        }

        root.remove(20);
        System.out.println("window after root remove 20 = " + window);
        System.out.println("snapshot after root remove = " + snapshot);

        window.clear();
        System.out.println("root after window clear = " + root);
        System.out.println("window after clear = " + window);
        System.out.println("snapshot after clear = " + snapshot);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 BackedRangeViewDemo.java
java BackedRangeViewDemo
```

Kết quả của lần chạy kiểm chứng:

```text
initial window = [20, 30, 40]
after root add 35 = [20, 30, 35, 40]
window remove 30 = true
root after remove = [10, 20, 35, 40, 50, 60]
window add 25 = true
root after add = [10, 20, 25, 35, 40, 50, 60]
add excluded endpoint = IllegalArgumentException
widen child bound = IllegalArgumentException
window after root remove 20 = [25, 35, 40]
snapshot after root remove = [20, 25, 35, 40]
root after window clear = [10, 50, 60]
window after clear = []
snapshot after clear = [20, 25, 35, 40]
```

Hãy đọc theo từng giai đoạn.

### 8.1. Thêm qua root làm window thay đổi

Ban đầu window là `[20, 30, 40]`. Sau `root.add(35)`, window thành `[20, 30, 35, 40]`. Không có lời gọi tạo lại subSet.

Đó là bằng chứng trực tiếp của tính liên kết. Nếu muốn một báo cáo giữ đúng dữ liệu tại thời điểm bắt đầu, chỉ giữ biến window là chưa đủ.

### 8.2. Xóa và thêm qua window làm root thay đổi

`window.remove(30)` làm root mất 30. `window.add(25)` làm root có 25. Cả hai thao tác đều tác động lên dữ liệu nền.

Sau đó, `window.add(50)` bị từ chối vì 50 là biên trên bị loại trừ. Lỗi này không làm khoảng tự rộng ra.

Lời gọi `window.headSet(60)` cũng bị từ chối: một góc nhìn con không được yêu cầu biên vượt ra ngoài góc nhìn cha.

### 8.3. Bản chụp có vòng đời riêng

Chương trình tạo:

```java
TreeSet<Integer> snapshot = new TreeSet<>(window);
```

Tại thời điểm ấy, snapshot có `[20, 25, 35, 40]`. Khi root gỡ 20, window mất 20 nhưng snapshot còn 20.

Sau `window.clear()`, root chỉ còn `[10, 50, 60]`: các phần tử thuộc khoảng đã bị gỡ. Snapshot vẫn là `[20, 25, 35, 40]`.

Vì phần tử là Integer bất biến, ví dụ này dễ quan sát sự độc lập của hai container mà không có thêm vấn đề chia sẻ đối tượng khả biến.

## 9. Mọi biên đều được đọc theo comparator

Giả sử `cmp(x, y)` là phép so sánh của Set. Khi không có comparator tường minh, hiểu đó là phép so sánh theo thứ tự tự nhiên.

Có thể viết quy tắc tổng quát:

```text
headSet(b):       cmp(x, b) < 0
tailSet(a):       cmp(x, a) >= 0
subSet(a, b):     cmp(x, a) >= 0 và cmp(x, b) < 0
```

Với comparator giảm dần, “x đứng trước b” có thể có nghĩa x lớn hơn b về số học.

Điều kiện hợp lệ của hai mốc cũng là a không đứng sau b theo comparator, không phải luôn là `a <= b` theo số học.

### 9.1. Đọc head và tail trên một dãy cụ thể

Với dãy theo comparator:

```text
50, 40, 30, 20, 10
```

- Head trước mốc 30 là `50, 40`.
- Tail từ mốc 30 là `30, 20, 10`.
- Khoảng từ 40 đến trước 20 là `40, 30`.

Một cách thực hành hiệu quả là viết dãy theo đúng thứ tự của Set rồi đánh dấu các mốc trên dãy ấy, thay vì luôn vẽ trục số tăng dần.

### 9.2. Biên theo đối tượng nghiệp vụ

Nếu comparator xét một thuộc tính, mốc cũng được hiểu qua thuộc tính đó. Ví dụ, comparator chỉ xét điểm số thì một đối tượng mốc có thể tương đương với nhiều đối tượng cùng điểm theo quy tắc so sánh.

Tuy nhiên, TreeSet chỉ giữ một thành viên trong mỗi lớp tương đương mà comparator xác định. Nếu muốn giữ nhiều người cùng điểm, phải thiết kế thêm tiêu chí phân biệt phù hợp hoặc dùng mô hình dữ liệu khác.

Cách comparator quyết định tương đương ảnh hưởng cả thành viên trong tập lẫn việc có nằm đúng biên hay không. Đừng giả định biên chỉ được bao gồm khi đối tượng đó có cùng tham chiếu với mốc.

## 10. Chương trình 4: khoảng trong thứ tự giảm dần

Lưu tệp `ReverseRangeBoundsDemo.java`:

```java
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public class ReverseRangeBoundsDemo {
    public static void main(String[] args) {
        NavigableSet<Integer> values =
                new TreeSet<>(Comparator.reverseOrder());
        values.addAll(List.of(10, 20, 30, 40, 50));

        System.out.println("reverse values = " + values);
        System.out.println("first = " + values.first());
        System.out.println("last = " + values.last());
        System.out.println("headSet 30 = " + values.headSet(30));
        System.out.println("tailSet 30 = " + values.tailSet(30));
        System.out.println("subSet 40 to 20 = " + values.subSet(40, 20));
        System.out.println("subSet inclusive endpoints = "
                + values.subSet(40, true, 20, true));

        NavigableSet<Integer> view = values.subSet(40, true, 20, false);
        TreeSet<Integer> snapshot = new TreeSet<>(view);
        System.out.println("snapshot preserves order = " + snapshot);
        values.remove(30);
        System.out.println("view after root remove = " + view);
        System.out.println("snapshot after root remove = " + snapshot);

        try {
            values.subSet(20, 40);
        } catch (IllegalArgumentException ex) {
            System.out.println("wrong comparator endpoint order = "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 ReverseRangeBoundsDemo.java
java ReverseRangeBoundsDemo
```

Kết quả của lần chạy kiểm chứng:

```text
reverse values = [50, 40, 30, 20, 10]
first = 50
last = 10
headSet 30 = [50, 40]
tailSet 30 = [30, 20, 10]
subSet 40 to 20 = [40, 30]
subSet inclusive endpoints = [40, 30, 20]
snapshot preserves order = [40, 30]
view after root remove = [40]
snapshot after root remove = [40, 30]
wrong comparator endpoint order = IllegalArgumentException
```

Ở đây, `first` là 50 và `last` là 10. `headSet(30)` chứa `[50, 40]`, khác hoàn toàn cách hiểu “head luôn là các số nhỏ hơn”.

Lời gọi `subSet(40, 20)` hợp lệ vì 40 đứng trước 20 theo comparator giảm dần. Ngược lại, `subSet(20, 40)` đảo thứ tự biên theo comparator và gây `IllegalArgumentException`.

Các cờ inclusive vẫn giữ đúng ý nghĩa: `subSet(40, true, 20, true)` lấy cả hai đầu, cho `[40, 30, 20]`.

Bản chụp được tạo từ một biến có kiểu NavigableSet. Constructor nhận SortedSet của TreeSet giữ lại quan hệ thứ tự, nên snapshot vẫn duyệt `[40, 30]`. Khi tập gốc gỡ 30, view chỉ còn 40 nhưng snapshot giữ nguyên.

Ví dụ này nối hai quyết định độc lập:

1. Muốn view hay bản sao?
2. Muốn giữ comparator nào?

Chọn bản sao mà vô tình đổi comparator có thể làm kết quả khác ý định, dù dữ liệu đầu vào giống nhau.

## 11. Tạo bản sao và giữ đúng thứ tự

### 11.1. Sao chép từ SortedSet

Nếu biến nguồn có kiểu SortedSet hoặc một kiểu con phù hợp:

```java
// Trích đoạn minh họa; view có kiểu SortedSet<Integer>.
SortedSet<Integer> snapshot = new TreeSet<>(view);
```

Constructor `TreeSet(SortedSet)` dùng cùng thứ tự với nguồn. Đây là lựa chọn ngắn gọn để tạo một tập riêng từ một khoảng có thứ tự.

Nhưng chú ý kiểu tĩnh tại vị trí gọi overload:

```java
// Trích đoạn minh họa khác biệt overload.
SortedSet<Integer> orderedView = view;
Collection<Integer> generalCollection = orderedView;

TreeSet<Integer> sameOrder = new TreeSet<>(orderedView);
TreeSet<Integer> naturalOrder = new TreeSet<>(generalCollection);
```

Các import được lược khỏi trích đoạn. Constructor nhận Collection sắp xếp theo thứ tự tự nhiên, trong khi constructor nhận SortedSet giữ thứ tự của SortedSet. Nếu nguồn giảm dần, hai bản sao có thể duyệt khác nhau. Nếu phần tử không có thứ tự tự nhiên phù hợp, cách gọi qua Collection còn có thể thất bại.

Không chọn overload dựa trên phỏng đoán kiểu thực tế lúc chạy. Java chọn overload từ thông tin kiểu tại lúc biên dịch.

Các constructor này được mô tả trong [tài liệu TreeSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html).

### 11.2. Truyền comparator rõ ràng

Có thể thể hiện ý định trực tiếp:

```java
// Trích đoạn; view là một SortedSet<Integer>.
TreeSet<Integer> snapshot = new TreeSet<>(view.comparator());
snapshot.addAll(view);
```

Comparator trả về `null` có nghĩa nguồn dùng thứ tự tự nhiên; constructor tương ứng chấp nhận cách biểu đạt ấy.

Nếu cung cấp một comparator khác, đó là một phép tái tổ chức có chủ đích. Cần kiểm tra xem comparator mới có gộp các phần tử mà nguồn đang phân biệt hay không.

### 11.3. Chỉ đọc không đồng nghĩa bản chụp

Một wrapper không cho sửa quanh view vẫn có thể thấy dữ liệu gốc thay đổi. Nó hạn chế thao tác qua wrapper, không tự sao chép nền dữ liệu.

Nếu cần một bản chụp không cho thêm hoặc xóa qua tham chiếu trả về, có thể kết hợp sao chép rồi bọc:

```java
// Trích đoạn; cần import java.util.Collections.
SortedSet<Integer> snapshot =
        Collections.unmodifiableSortedSet(new TreeSet<>(view));
```

Ở đây, phần tạo TreeSet mới làm nhiệm vụ tách container; wrapper làm nhiệm vụ hạn chế cập nhật qua tham chiếu này. Hai bước có mục đích khác nhau.

### 11.4. Sao chép container vẫn có thể chia sẻ phần tử

Với một kiểu phần tử khả biến, cả bản sao và tập gốc có thể giữ tham chiếu tới cùng đối tượng. Sửa thuộc tính của đối tượng có thể được quan sát ở cả hai nơi.

Đặc biệt, không sửa thuộc tính tham gia comparator khi đối tượng đang nằm trong TreeSet. Việc có thêm một bản sao container không làm khóa trở nên bất biến và còn có thể khiến nhiều cấu trúc cùng bị ảnh hưởng.

Nếu mục tiêu là một bản chụp dữ liệu nghiệp vụ ổn định, hãy xác định có cần sao chép hoặc chuyển từng phần tử thành giá trị bất biến hay không.

## 12. Áp dụng khoảng nửa mở để chia dữ liệu

Khoảng `[a, b)` hữu ích khi chia các vùng liền nhau:

```text
[a, b)    và    [b, c)
```

Mốc b chỉ thuộc vùng thứ hai, nên hai vùng không chồng nhau tại biên ấy.

Ví dụ, khi tổ chức các ngày hoặc thời điểm:

- Từ đầu kỳ đến trước đầu kỳ kế tiếp.
- Từ một ngưỡng điểm trở lên đến trước ngưỡng nhóm sau.
- Các mã đứng từ mốc bắt đầu đến trước mốc kết thúc theo comparator.

Không cần trừ một phần nhỏ khỏi biên trên để biến nó thành “thời điểm cuối cùng”. Cách diễn đạt bằng đầu kỳ kế tiếp và biên mở thường rõ hơn, nhất là khi độ phân giải của dữ liệu có thể thay đổi.

Một trích đoạn cho thời điểm:

```java
// Trích đoạn minh họa; các kiểu nằm trong java.time và java.util.
NavigableSet<Instant> times = new TreeSet<>();
Instant start = Instant.parse("2026-10-01T00:00:00Z");
Instant end = Instant.parse("2026-11-01T00:00:00Z");

NavigableSet<Instant> octoberUtc =
        times.subSet(start, true, end, false);
```

Đây là khoảng theo các mốc UTC đã nêu, không tự suy ra một tháng theo múi giờ địa phương. Nếu nghiệp vụ định nghĩa khoảng theo múi giờ khác, cần tính hai mốc thời gian đúng trước khi truy vấn Set.

Cũng cần nhớ Set loại trùng theo quan hệ thành viên: một Set các thời điểm không tự lưu được nhiều sự kiện khác nhau cùng thời điểm. Nếu cần toàn bộ sự kiện, phải chọn khóa phân biệt hoặc một cấu trúc ánh xạ thời điểm tới các sự kiện.

Nếu khoảng được dùng làm dữ liệu báo cáo cố định, tạo bản chụp vào thời điểm thích hợp. Nếu dùng làm cửa sổ thao tác trên dữ liệu đang quản lý, view liên kết có thể đúng với yêu cầu hơn.

## 13. Hiểu chi phí của khoảng và việc giữ tham chiếu

Trong TreeSet, một khoảng được xây trên cấu trúc cây có thứ tự. Việc tạo view không có nghĩa lập tức sao chép mọi phần tử đang nằm trong khoảng.

Một mô hình triển khai là: góc nhìn giữ tham chiếu tới dữ liệu nền, các mốc biên và cờ đóng/mở, rồi giới hạn các thao tác theo chúng. [Mã nguồn TreeMap OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/TreeMap.java) cho thấy các góc nhìn khoảng và cách tìm thành viên trong giới hạn.

### 13.1. Tách chi phí tạo view khỏi chi phí đọc dữ liệu

Giả sử tập gốc có n thành viên, khoảng hiện có k thành viên và phép so sánh có chi phí hằng số:

| Việc làm | Cách hiểu phù hợp |
|---|---|
| Tạo một view theo biên | Chủ yếu ghi nhận và kiểm tra biên, không phải sao chép k phần tử |
| Tìm đầu hoặc cuối của khoảng trên cây | Có thể cần đường tìm theo biên trong cây gốc |
| Duyệt toàn bộ k phần tử của khoảng | Cần xử lý những phần tử được duyệt; không thể coi là miễn phí |
| Tạo bản sao độc lập | Phải xử lý và lưu các thành viên của bản sao |
| Xóa toàn bộ khoảng | Là nhiều thay đổi dữ liệu nền, không chỉ bỏ một biến view |

Trong phân tích cây thông thường, duyệt một khoảng có thể được hiểu theo `O(log n + k)): tìm điểm bắt đầu rồi đi qua các phần tử phù hợp. Đây là cách phân tích theo cấu trúc, không phải lời hứa rằng mọi triển khai SortedSet đều có cùng chi phí.

Tương tự, không suy ra mọi phương thức trên view đều là `O(1)` chỉ vì tạo view nhẹ.

### 13.2. size của khoảng có thể cần đếm

Một TreeSet đầy đủ có thể duy trì sẵn số thành viên. Một góc nhìn khoảng không nhất thiết có một bộ đếm riêng luôn cập nhật theo mọi thay đổi của nền dữ liệu.

Trong triển khai OpenJDK 17, việc tính kích thước của một khoảng có thể phải duyệt để đếm, và kết quả có thể được lưu lại trong một số điều kiện. Đừng viết thuật toán gọi `view.size()` nhiều lần rồi mặc định mọi lần đều có chi phí hằng số.

Nếu chỉ cần biết có dữ liệu không, `isEmpty()` biểu đạt đúng ý định hơn tự lấy size rồi so với 0. Chi phí cụ thể vẫn phụ thuộc triển khai và góc nhìn.

### 13.3. Một view nhỏ có thể giữ tập gốc tồn tại

Vì góc nhìn liên kết với cấu trúc nền, giữ lâu một khoảng chỉ có ít phần tử có thể đồng thời giữ tham chiếu tới nền dữ liệu lớn hơn.

Nếu cần giữ một kết quả nhỏ lâu dài nhưng không cần nó tiếp tục thay đổi theo nguồn, bản sao riêng có thể phù hợp hơn về cả ngữ nghĩa lẫn vòng đời bộ nhớ.

Điều này không có nghĩa luôn phải sao chép. View phù hợp khi cần tính liên kết; bản sao phù hợp khi cần tách vòng đời. Chọn theo yêu cầu sử dụng, không chỉ theo số dòng mã ngắn hơn.

## 14. Ngoại lệ, iterator và các thao tác không nguyên tử

### 14.1. Phân biệt các loại lỗi

| Tình huống | Hành vi cần dự kiến |
|---|---|
| `first` hoặc `last` trên khoảng rỗng | `NoSuchElementException` |
| Hai mốc đảo thứ tự theo comparator | `IllegalArgumentException` |
| Thêm ngoài giới hạn của một khoảng TreeSet | `IllegalArgumentException` |
| Yêu cầu khoảng con vượt phạm vi cha | Có thể bị từ chối bằng `IllegalArgumentException` |
| Giá trị không so sánh được theo quy tắc của tập | Có thể có `ClassCastException` |
| `null` khi quy tắc so sánh không hỗ trợ | Có thể có `NullPointerException` |
| Cập nhật qua một collection không cho sửa | `UnsupportedOperationException` theo hợp đồng của nó |

Không dùng việc biên không có trong tập làm lý do ném lỗi riêng nếu miền dữ liệu và thứ tự vẫn hợp lệ. Một mốc so sánh không phải một yêu cầu “phần tử này phải tồn tại”.

### 14.2. Iterator không phải bản chụp

Nếu đang duyệt một góc nhìn TreeSet rồi thay đổi cấu trúc qua tập gốc hoặc một đường khác, iterator có thể phát hiện sửa đổi và ném `ConcurrentModificationException`.

Ngay cả thay đổi nằm ngoài khoảng đang duyệt cũng có thể ảnh hưởng cơ chế phát hiện sửa đổi của cùng cấu trúc nền. Không thiết kế mã dựa trên suy luận rằng “không đổi các phần tử trong view thì iterator chắc chắn không bị ảnh hưởng”.

Nếu muốn vừa duyệt vừa gỡ, dùng cách cập nhật phù hợp với hợp đồng iterator hoặc tách rõ giai đoạn xử lý. Nếu cần một danh sách kết quả ổn định để duyệt độc lập, hãy tạo bản sao.

Fail-fast là cơ chế phát hiện lỗi theo khả năng của triển khai, không phải công cụ đồng bộ hoặc bảo đảm ảnh chụp nhất quán cho nhiều luồng.

### 14.3. addAll và các chuỗi kiểm tra không tự thành giao dịch

Nếu thêm nhiều giá trị vào một khoảng, một giá trị ngoài biên có thể khiến lời gọi thất bại sau khi những giá trị hợp lệ trước đó đã được thêm. Không mặc định lỗi là toàn bộ dữ liệu đã tự hoàn tác.

Nếu nghiệp vụ đòi hỏi tất cả thành công hoặc không thay đổi gì, cần một quy trình riêng: xác thực đầy đủ, chuẩn bị dữ liệu và phối hợp cập nhật phù hợp. Chỉ bắt ngoại lệ từ `addAll` không tạo ra tính nguyên tử.

Tương tự, kiểm tra `!view.isEmpty()` rồi gọi `view.first()` là hai thao tác. Trong mã nhiều luồng không có phối hợp, dữ liệu có thể thay đổi giữa hai bước.

Các ví dụ của bài là mã đơn luồng để làm rõ ngữ nghĩa khoảng. Tính liên kết của view không tự cung cấp an toàn đồng thời.

## 15. Những lỗi phổ biến cần tránh

| Cách hiểu hoặc cách dùng | Vấn đề | Cách sửa |
|---|---|---|
| `headSet(x)` có chứa x | Overload mặc định loại biên trên | Dùng `headSet(x, true)` nếu cần |
| `tailSet(x)` loại x | Overload mặc định gồm biên dưới | Dùng `tailSet(x, false)` nếu cần |
| `subSet(a, b)` lấy cả hai đầu | Mặc định là `[a, b)` | Chọn overload boolean rõ ràng |
| Biên phải tồn tại trong tập | Nhầm mốc so sánh với thành viên | Cho phép mốc vắng nhưng hợp lệ |
| first trả về null khi rỗng | API ném ngoại lệ khi không có phần tử | Xử lý rỗng theo ngữ nghĩa cần dùng |
| first luôn là số nhỏ nhất | Bỏ qua comparator | Đọc theo thứ tự của tập |
| Khoảng là bản sao | Thay đổi có thể lan sang tập gốc | Tạo collection riêng khi cần |
| clear view chỉ xóa kết quả tạm | Thực tế gỡ dữ liệu nền trong khoảng | Xác định quyền sở hữu trước khi thay đổi |
| Khoảng con tự giao với phạm vi cha | Mốc ngoài phạm vi có thể bị từ chối | Tính và kiểm tra biên phù hợp |
| Cộng 1 để lấy biên trên đóng | Có thể tràn hoặc sai miền dữ liệu | Dùng cờ inclusive |
| Giữ một wrapper chỉ đọc để có bản chụp | Nguồn vẫn có thể thay đổi | Sao chép trước nếu cần dữ liệu cố định |
| Copy qua Collection vẫn giữ comparator | Overload Collection dùng thứ tự tự nhiên | Giữ kiểu SortedSet hoặc truyền comparator |
| Mọi size trên khoảng đều O(1) | Có thể cần duyệt đếm | Đọc hợp đồng và phân tích đúng thao tác |

Một thói quen hữu ích trước khi viết truy vấn khoảng là ghi ra ba dòng:

```text
Thứ tự đang dùng là gì?
Mỗi đầu có được bao gồm không?
Kết quả cần liên kết với nguồn hay độc lập với nguồn?
```

Ba quyết định này thường giải thích phần lớn khác biệt giữa một đoạn mã nhìn đúng và một đoạn mã đúng yêu cầu.

## 16. Bài tập áp dụng

### Bài 1: khoảng mặc định

Cho `[5, 10, 15, 20, 25]` theo thứ tự tự nhiên. Dự đoán `headSet(15)`, `tailSet(15)` và `subSet(10, 25)`.

**Gợi ý trả lời:** lần lượt là `[5, 10]`, `[15, 20, 25]` và `[10, 15, 20]`.

### Bài 2: biên vắng mặt

Trên cùng tập, `subSet(11, 24)` có hợp lệ không và chứa gì?

**Gợi ý trả lời:** hợp lệ, chứa `[15, 20]`. Hai mốc không cần là thành viên.

### Bài 3: lấy cả hai đầu

Viết khoảng chứa các thành viên từ 10 đến 20, có lấy cả 10 lẫn 20.

**Gợi ý trả lời:** với biến NavigableSet, dùng `subSet(10, true, 20, true)`.

### Bài 4: ba khái niệm rỗng khác nhau

Phân biệt tập gốc rỗng, khoảng hiện chưa có dữ liệu và khoảng `[a, a)`.

**Gợi ý trả lời:** cả ba có thể đang in ra `[]`, nhưng khác về nền dữ liệu và khả năng thêm về sau. Một khoảng có độ rộng hợp lệ có thể nhận thành viên khi nguồn thay đổi; khoảng nửa mở với hai mốc tương đương không có giá trị hợp lệ để chứa.

### Bài 5: thay đổi qua view

Tạo view `[10, 20)`, rồi gọi `view.clear()`. Điều gì xảy ra với tập gốc?

**Gợi ý trả lời:** các thành viên của tập gốc nằm trong khoảng đó bị gỡ. Những thành viên ngoài khoảng vẫn còn; view vẫn liên kết với tập gốc.

### Bài 6: thứ tự giảm dần

Cho `[25, 20, 15, 10, 5]` theo comparator giảm dần. Dự đoán `headSet(15)` và `subSet(20, 5)`.

**Gợi ý trả lời:** `[25, 20]` và `[20, 15, 10]`. Các mốc được đọc theo comparator, không theo điều kiện số học tăng dần.

### Bài 7: khoảng con ở biên mở

Khoảng cha là `(10, 25)`. So sánh yêu cầu `headSet(25, false)` với `headSet(25, true)`.

**Gợi ý trả lời:** yêu cầu thứ nhất giữ biên trên mở nên có thể hợp lệ; yêu cầu thứ hai cố bao gồm mốc cha đã loại trừ và bị từ chối. Không chỉ nhìn nội dung hiện có để quyết định tính hợp lệ.

### Bài 8: dữ liệu báo cáo ổn định

Một hàm trả về view của tập đang tiếp tục được cập nhật. Bên nhận muốn báo cáo giữ nguyên dữ liệu tại lúc gọi. Chỉ bọc view bằng wrapper chỉ đọc có đủ không?

**Gợi ý trả lời:** chưa đủ. Cần tách dữ liệu bằng một bản sao giữ đúng comparator, rồi mới hạn chế cập nhật nếu muốn. Với phần tử khả biến, còn phải xác định mức sao chép dữ liệu.

### Bài 9: đọc đầu khoảng

Khoảng `[11, 14)` hiện không chứa phần tử. Gọi `first()` có tự trả về 11 hoặc thành viên gần nhất ngoài khoảng không?

**Gợi ý trả lời:** không. First chỉ đọc thành viên đang có bên trong khoảng và ném `NoSuchElementException` khi khoảng rỗng. Nếu cần tìm phần tử lân cận một mốc trên tập gốc, xem các phương thức điều hướng phù hợp như `ceiling` hoặc `higher`.

Khi triển khai thao tác theo khoảng, hãy diễn đạt chính xác thứ tự, đóng/mở và vòng đời dữ liệu. Đó là cơ sở để dùng cùng một API cho cả đọc biên, truy vấn khoảng và cập nhật có giới hạn mà không nhầm quyền tác động lên tập gốc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sorted-set-006">NavigableSet: lower, floor, ceiling và higher</a>

<details>
<summary>Xem chi tiết</summary>

# NavigableSet: lower, floor, ceiling và higher

## 1. Từ kiểm tra có phần tử tới tìm phần tử sát một ngưỡng

`contains(x)` chỉ trả lời có một phần tử tương ứng với `x` trong tập hay không. Nhiều bài toán cần một câu hỏi khác:

- Phần tử gần nhất nằm trước ngưỡng là gì?
- Giá trị lớn nhất không vượt quá giới hạn là gì?
- Giá trị nhỏ nhất đạt hoặc vượt yêu cầu là gì?
- Nếu bỏ qua chính ngưỡng đang có, phần tử tiếp theo là gì?

`NavigableSet<E>` mở rộng tập sắp xếp để hỗ trợ các phép tìm theo hướng này. Bốn phương thức trung tâm là `lower`, `floor`, `ceiling`, `higher`.

Với các số nguyên sắp theo thứ tự tự nhiên tăng dần:

| Phương thức | Quan hệ cần thỏa | Chọn phần tử nào trong các ứng viên? |
| --- | --- | --- |
| `lower(x)` | Nhỏ hơn x, ký hiệu `< x` | Lớn nhất trong các phần tử nhỏ hơn x |
| `floor(x)` | Nhỏ hơn hoặc bằng x, `≤ x` | Lớn nhất trong các phần tử không vượt x |
| `ceiling(x)` | Lớn hơn hoặc bằng x, `≥ x` | Nhỏ nhất trong các phần tử đạt hoặc vượt x |
| `higher(x)` | Lớn hơn x, `> x` | Nhỏ nhất trong các phần tử lớn hơn x |

Cả bốn trả về một phần tử phù hợp đang có trong tập, hoặc `null` nếu không có ứng viên. Chúng không trả chỉ số, không tạo một giá trị mới cho vừa ngưỡng và không tự xóa phần tử đã tìm. [NavigableSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableSet.html)

Điểm khác biệt dễ nhớ:

- `lower` và `higher` là quan hệ **chặt**: loại phần tử tương đương với ngưỡng.
- `floor` và `ceiling` là quan hệ **có lấy bằng**: có thể trả về phần tử tương đương với ngưỡng.

Từ “nhỏ hơn”, “lớn hơn” trong hợp đồng là theo **thứ tự của tập**. Với số tự nhiên tăng dần, đó là quan hệ số quen thuộc. Với một `Comparator` khác hoặc một khung nhìn đảo chiều, phải đọc theo thứ tự đang sử dụng.

## 2. Khai báo kiểu phù hợp để gọi các phương thức

Một cách dùng phổ biến trên Java 17:

```java
NavigableSet<Integer> values =
        new TreeSet<>(List.of(10, 20, 30, 40));
```

Quan hệ kiểu:

```text
Set
  ↑
SortedSet
  ↑
NavigableSet
  ↑
TreeSet triển khai các khả năng tương ứng
```

`Set` nói về tập không trùng; `SortedSet` bổ sung thứ tự; `NavigableSet` bổ sung khả năng tìm các giá trị lân cận theo ngưỡng.

Nếu khai báo biến là `Set<Integer>`, giao diện đó không cung cấp `floor` hoặc `ceiling`, dù đối tượng thực tế có thể là `TreeSet`. Khi API hoặc thuật toán thực sự cần khả năng điều hướng này, dùng một kiểu khai báo thể hiện đúng yêu cầu.

Các chữ ký có dạng:

```java
E lower(E value);
E floor(E value);
E ceiling(E value);
E higher(E value);
```

Đoạn trên mô tả chữ ký phương thức, không phải một chương trình độc lập. Với `NavigableSet<Integer>`, kết quả có kiểu `Integer`, nên có thể là `null`.

`TreeSet` là triển khai dùng cây có thứ tự. Không suy ra rằng mọi `Set`, đặc biệt một `HashSet` bất kỳ, đều có bốn phương thức hoặc cùng mô hình tìm kiếm này.

## 3. Thực sự khác nhau ở đâu khi ngưỡng có mặt?

Cho tập:

```text
[10, 20, 30, 40]
```

### 3.1. Ngưỡng 20 đã thuộc tập

```text
10 -------- 20 -------- 30 -------- 40
            ↑
          ngưỡng
```

Kết quả:

| Lời gọi | Kết quả | Giải thích |
| --- | --- | --- |
| `lower(20)` | `10` | Bỏ qua chính 20, chọn phần tử lớn nhất còn nhỏ hơn |
| `floor(20)` | `20` | Có lấy bằng, nên 20 phù hợp và là ứng viên sát ngưỡng nhất phía này |
| `ceiling(20)` | `20` | Có lấy bằng, nên không cần đi tới 30 |
| `higher(20)` | `30` | Bỏ qua chính 20, chọn phần tử nhỏ nhất còn lớn hơn |

Nếu đang tìm “giá trị tiếp theo sau giá trị hiện tại”, `higher` thường diễn đạt đúng việc phải bỏ qua giá trị hiện tại. Dùng `ceiling` có thể trả lại chính giá trị đó.

Tương tự, “giá trị trước đó” theo quan hệ chặt là `lower`; “giá trị gần nhất không vượt quá hiện tại” là `floor`.

### 3.2. Ngưỡng 25 không thuộc tập

```text
10 -------- 20 ---- 25 ---- 30 -------- 40
                    ↑
                  ngưỡng
```

Kết quả:

```text
lower(25)   = 20
floor(25)   = 20
ceiling(25) = 30
higher(25)  = 30
```

Vì không có phần tử tương đương với `25`, việc cho phép hay loại dấu bằng không làm khác tập ứng viên ở ví dụ này.

Điều đó không làm các cặp phương thức đồng nghĩa. Chúng chỉ tình cờ có cùng kết quả khi không có phần tử bằng ngưỡng theo thứ tự của tập.

Ngưỡng không cần có sẵn trong tập. Không thêm `25` vào trước để gọi tìm lân cận rồi xóa nó đi; cách đó sửa dữ liệu không cần thiết và có thể làm thay đổi ý nghĩa tập.

### 3.3. Vì sao floor(25) không phải 10?

Cả `10` và `20` đều không vượt `25`, nhưng `floor` chọn **phần tử lớn nhất** trong các ứng viên. Tương tự, cả `30` và `40` đều vượt `25`, nhưng `ceiling` chọn **phần tử nhỏ nhất** trong các ứng viên phía đó.

Các phương thức không trả về một phần tử bất kỳ chỉ vì nó thỏa dấu so sánh.

## 4. Biên, tập rỗng và ý nghĩa của null

Vẫn với `[10, 20, 30, 40]`:

| Ngưỡng | lower | floor | ceiling | higher |
| --- | --- | --- | --- | --- |
| `5` | `null` | `null` | `10` | `10` |
| `10` | `null` | `10` | `10` | `20` |
| `20` | `10` | `20` | `20` | `30` |
| `25` | `20` | `20` | `30` | `30` |
| `40` | `30` | `40` | `40` | `null` |
| `45` | `40` | `40` | `null` | `null` |

`null` không có nghĩa tập nhất thiết rỗng. Với ngưỡng `5`, tập có bốn phần tử nhưng không phần tử nào nằm ở phía thấp hơn.

Với một tập rỗng và ngưỡng truy vấn hợp lệ, cả bốn đều không có ứng viên và trả về `null`.

### 4.1. Kiểm tra null trước khi mở hộp sang kiểu nguyên thủy

Cách xử lý rõ ràng:

```java
// Đoạn minh họa: values là NavigableSet<Integer> không chứa null.
Integer previous = values.floor(5);
if (previous == null) {
    System.out.println("Không có giá trị không vượt quá 5");
} else {
    int usableValue = previous;
    System.out.println("Giá trị được chọn: " + usableValue);
}
```

Cách dễ gây lỗi:

```java
// Có thể ném NullPointerException khi floor không tìm thấy kết quả.
int previous = values.floor(5);
```

Lỗi xảy ra khi Java tự mở hộp `Integer` là `null` thành `int`. Không phải `floor` đang ném một ngoại lệ “không tìm thấy phần tử” cho truy vấn hợp lệ đó.

Không mặc định dùng `0` hay `-1` làm giá trị thay thế nếu các số đó có thể là dữ liệu hợp lệ. Chính sách khi không tìm thấy phải được quyết định theo yêu cầu ứng dụng.

### 4.2. null trong kết quả và null làm phần tử là hai chuyện khác nhau

Các ví dụ số tự nhiên trong bài không lưu phần tử `null`, nên `null` từ phép điều hướng được hiểu rõ là không có ứng viên.

Một `Comparator` có thể được thiết kế để cho phép `null`, chẳng hạn thông qua `Comparator.nullsFirst`. Khi đó, chính phần tử `null` cũng có thể là một kết quả hợp lệ theo thứ tự, làm giá trị trả về trở nên khó phân biệt.

Ví dụ với thứ tự đặt `null` trước mọi chuỗi và tập `[null, b]`, `lower("b")` có thể trả về phần tử `null` đang được lưu. Kết quả đơn lẻ giống trường hợp không có ứng viên.

Nếu phải dùng kiểu dữ liệu này, cần xét cả việc tập có `null` và quan hệ của nó với ngưỡng. Khi không có yêu cầu lưu `null`, giữ miền phần tử không chứa `null` giúp API điều hướng dễ sử dụng hơn. Tài liệu `NavigableSet` cũng nêu sự mơ hồ này. [NavigableSet: giá trị null](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableSet.html)

Không dùng `null` làm “ngưỡng mặc định” cho một tập không hỗ trợ nó. Đối số không so sánh được hoặc `null` không được chấp nhận có thể dẫn tới ngoại lệ; đây là lỗi miền đầu vào, khác với truy vấn hợp lệ không có kết quả.

## 5. Chương trình đầy đủ: đủ bốn phép tìm và xử lý không có kết quả

Lưu toàn bộ mã vào **NavigableNeighborsDemo.java**:

```java
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public class NavigableNeighborsDemo {
    private static void show(NavigableSet<Integer> values, int target) {
        System.out.println("target " + target
                + ": lower=" + values.lower(target)
                + ", floor=" + values.floor(target)
                + ", ceiling=" + values.ceiling(target)
                + ", higher=" + values.higher(target));
    }

    public static void main(String[] args) {
        NavigableSet<Integer> values =
                new TreeSet<>(List.of(10, 20, 30, 40));
        System.out.println("values: " + values);
        show(values, 20);
        show(values, 25);
        show(values, 5);
        show(values, 45);
        System.out.println("after queries: " + values);
        System.out.println("size: " + values.size());

        NavigableSet<Integer> empty = new TreeSet<>();
        System.out.println("empty set:");
        show(empty, 20);

        Integer previous = values.floor(5);
        if (previous == null) {
            System.out.println("no value at or below 5");
        }
        try {
            int unsafe = values.floor(5);
            System.out.println("unsafe value: " + unsafe);
        } catch (NullPointerException ex) {
            System.out.println("unboxing absent result: NullPointerException");
        }

        NavigableSet<Integer> widths =
                new TreeSet<>(List.of(320, 640, 960, 1280));
        System.out.println("smallest width at least 700: " + widths.ceiling(700));
        System.out.println("largest width at most 700: " + widths.floor(700));
        System.out.println("next above 640: " + widths.higher(640));
        System.out.println("previous below 640: " + widths.lower(640));
    }
}
```

Biên dịch theo mức Java 17 và chạy:

```shell
javac --release 17 -encoding UTF-8 NavigableNeighborsDemo.java
java NavigableNeighborsDemo
```

Stdout:

```text
values: [10, 20, 30, 40]
target 20: lower=10, floor=20, ceiling=20, higher=30
target 25: lower=20, floor=20, ceiling=30, higher=30
target 5: lower=null, floor=null, ceiling=10, higher=10
target 45: lower=40, floor=40, ceiling=null, higher=null
after queries: [10, 20, 30, 40]
size: 4
empty set:
target 20: lower=null, floor=null, ceiling=null, higher=null
no value at or below 5
unboxing absent result: NullPointerException
smallest width at least 700: 960
largest width at most 700: 640
next above 640: 960
previous below 640: 320
```

Cách đọc kết quả:

1. Ngưỡng có sẵn `20` cho thấy khác biệt giữa quan hệ chặt và có lấy bằng.
2. Ngưỡng vắng mặt `25` vẫn truy vấn bình thường.
3. Ngưỡng ngoài hai phía cho thấy không có ứng viên ở một phía không làm tập rỗng.
4. Tập và kích thước giữ nguyên sau các truy vấn.
5. Tập rỗng trả về `null` cho các truy vấn hợp lệ.
6. Đoạn mở hộp không an toàn chủ động bắt `NullPointerException` để minh họa lỗi cần tránh.
7. Ví dụ độ rộng dùng `ceiling` để chọn cỡ nhỏ nhất đáp ứng yêu cầu, dùng `floor` để chọn cỡ lớn nhất không vượt giới hạn.

Bốn phương thức chỉ tìm và trả phần tử; chúng không có tác dụng lấy rồi xóa. Đừng nhầm với các thao tác như `pollFirst` hoặc `pollLast`, vốn có tác dụng sửa tập theo hợp đồng riêng.

## 6. “Nhỏ hơn” và “bằng” phụ thuộc vào thứ tự của tập

### 6.1. Quan hệ được xác định bởi Comparator hoặc Comparable

Với thứ tự `c`, có thể đọc các điều kiện:

```text
lower(target):   c.compare(candidate, target) < 0
floor(target):   c.compare(candidate, target) <= 0
ceiling(target): c.compare(candidate, target) >= 0
higher(target):  c.compare(candidate, target) > 0
```

Sau khi chọn phía thỏa điều kiện, phương thức trả ứng viên sát ngưỡng nhất theo thứ tự đó.

Khi dùng thứ tự tự nhiên, `compareTo` đảm nhiệm vai trò tương ứng. Không tự thay tất cả quan hệ bằng phép so số thông thường nếu tập đang chứa chuỗi, đối tượng miền hoặc có bộ so sánh riêng.

### 6.2. Có lấy bằng là lấy trường hợp compare bằng 0

Nếu một tập chuỗi dùng bộ so sánh không phân biệt hoa thường và đang giữ `"Bee"`, thì truy vấn `"BEE"` có thể cho:

```text
floor("BEE")   → "Bee"
ceiling("BEE") → "Bee"
```

Phần tử trả về là đại diện đang lưu trong tập. Nó không bắt buộc là chính tham chiếu đối số hoặc chuỗi bằng đối số theo `String.equals`.

Bộ so sánh không phân biệt hoa thường của chuỗi không nhất quán với `String.equals`; dùng nó trong `TreeSet` cần hiểu rằng nó không đáp ứng đầy đủ hợp đồng chung của `Set`. Ví dụ này phục vụ việc thấy rõ cơ chế so sánh, không phải khuyến nghị bỏ qua sự nhất quán đó trong thiết kế. [TreeSet: thứ tự và equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)

`lower` và `higher` bỏ qua toàn bộ trường hợp tương đương theo thứ tự, không chỉ bỏ qua một đối tượng có cùng tham chiếu.

### 6.3. Không sửa dữ liệu dùng để sắp thứ tự khi phần tử đang trong tập

Nếu phép so sánh dùng một trường có thể thay đổi, vị trí của phần tử trong cấu trúc có thể không còn phù hợp sau khi trường đó bị sửa.

Kết quả điều hướng khi ấy không còn được suy luận như với tập có quy tắc ổn định. Ưu tiên khóa so sánh bất biến và một bộ so sánh có hành vi ổn định trong thời gian tập được sử dụng.

## 7. descendingSet đảo cả hướng tìm

`descendingSet()` trả về một khung nhìn có thứ tự ngược với tập gốc. Nó không chỉ đổi cách in `for-each`; các phép điều hướng cũng được hiểu theo thứ tự ngược.

Cho tập gốc tăng dần:

```text
ascending:  [10, 20, 30, 40]
descending: [40, 30, 20, 10]
```

Trong thứ tự giảm dần, `30` đứng trước `25`, còn `20` đứng sau `25`. Vì thế:

```text
descending.lower(25)   = 30
descending.floor(25)   = 30
descending.ceiling(25) = 20
descending.higher(25)  = 20
```

Không có mâu thuẫn với bảng định nghĩa: các từ trước/sau, nhỏ/lớn đang được đọc theo bộ thứ tự của khung nhìn.

Nếu `d = s.descendingSet()`, quan hệ đối ngẫu là:

| Truy vấn trên d | Tương ứng trên s |
| --- | --- |
| `d.lower(x)` | `s.higher(x)` |
| `d.floor(x)` | `s.ceiling(x)` |
| `d.ceiling(x)` | `s.floor(x)` |
| `d.higher(x)` | `s.lower(x)` |

Dấu bằng vẫn giữ tính chất có lấy bằng; chỉ hướng quan hệ bị đảo.

Khung nhìn dùng dữ liệu nền của tập gốc. Khi dữ liệu gốc thay đổi, các kết quả truy vấn sau đó cũng có thể thay đổi. Nó không phải một bản sao dữ liệu đóng băng tại thời điểm gọi. [NavigableSet.descendingSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableSet.html#descendingSet())

## 8. Truy vấn trên một khung nhìn chỉ tìm trong miền của khung nhìn

Giả sử tập gốc là `[10, 20, 30, 40]`, nhưng biến hiện tại là khung nhìn chỉ chứa các phần tử trong đoạn từ `20` có lấy bằng tới `40` không lấy bằng:

```java
NavigableSet<Integer> window = ascending.subSet(20, true, 40, false);
```

Nội dung quan sát qua `window` là `[20, 30]`.

Khi gọi:

```text
ascending.lower(20)   → 10
window.lower(20)      → null

ascending.ceiling(35) → 40
window.ceiling(35)    → null
```

`10` và `40` có trong tập gốc, nhưng không thuộc miền mà `window` cung cấp. Phép điều hướng không tự đi ra ngoài miền đó để trả một ứng viên từ tập gốc.

Khi gặp `null` bất ngờ, hãy kiểm tra đối tượng đang nhận lời gọi là toàn bộ tập, khung nhìn đảo chiều hay khung nhìn giới hạn. Tên biến hoặc kiểu `NavigableSet` chưa đủ để biết miền dữ liệu thực tế.

Phần này chỉ minh họa phạm vi ứng viên của truy vấn; việc tạo và sửa các khung nhìn có những hợp đồng riêng. [NavigableSet: các khung nhìn giới hạn](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableSet.html)

## 9. Chương trình đầy đủ: thứ tự đảo, miền giới hạn và Comparator riêng

Lưu mã vào **NavigableOrderDemo.java**:

```java
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public class NavigableOrderDemo {
    public static void main(String[] args) {
        NavigableSet<Integer> ascending =
                new TreeSet<>(List.of(10, 20, 30, 40));
        NavigableSet<Integer> descending = ascending.descendingSet();

        System.out.println("ascending: " + ascending);
        System.out.println("descending: " + descending);
        System.out.println("descending lower 25: " + descending.lower(25));
        System.out.println("descending floor 25: " + descending.floor(25));
        System.out.println("descending ceiling 25: " + descending.ceiling(25));
        System.out.println("descending higher 25: " + descending.higher(25));
        System.out.println("descending floor 20: " + descending.floor(20));
        System.out.println("descending ceiling 20: " + descending.ceiling(20));
        System.out.println("lower/higher dual: "
                + descending.lower(25).equals(ascending.higher(25)));

        NavigableSet<Integer> window = ascending.subSet(20, true, 40, false);
        System.out.println("window: " + window);
        System.out.println("ascending lower 20: " + ascending.lower(20));
        System.out.println("window lower 20: " + window.lower(20));
        System.out.println("ascending ceiling 35: " + ascending.ceiling(35));
        System.out.println("window ceiling 35: " + window.ceiling(35));

        NavigableSet<String> words =
                new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        words.addAll(List.of("ant", "Bee", "dog"));
        String target = "BEE";
        System.out.println("words: " + words);
        System.out.println("word lower: " + words.lower(target));
        System.out.println("word floor: " + words.floor(target));
        System.out.println("word ceiling: " + words.ceiling(target));
        System.out.println("word higher: " + words.higher(target));
        System.out.println("floor equals query text: "
                + words.floor(target).equals(target));
        System.out.println("words after queries: " + words);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 NavigableOrderDemo.java
java NavigableOrderDemo
```

Stdout:

```text
ascending: [10, 20, 30, 40]
descending: [40, 30, 20, 10]
descending lower 25: 30
descending floor 25: 30
descending ceiling 25: 20
descending higher 25: 20
descending floor 20: 20
descending ceiling 20: 20
lower/higher dual: true
window: [20, 30]
ascending lower 20: 10
window lower 20: null
ascending ceiling 35: 40
window ceiling 35: null
words: [ant, Bee, dog]
word lower: ant
word floor: Bee
word ceiling: Bee
word higher: dog
floor equals query text: false
words after queries: [ant, Bee, dog]
```

Cách đọc kết quả:

1. Khung nhìn giảm dần đảo hướng của bốn phép tìm.
2. Khi ngưỡng `20` có phần tử tương đương, `floor` và `ceiling` vẫn có thể trả về `20` ở cả hai chiều.
3. Dòng `lower/higher dual` kiểm tra một trường hợp cụ thể mà hai kết quả đều có mặt; trong mã tổng quát vẫn cần xử lý kết quả `null`.
4. Khung nhìn giới hạn không trả ứng viên nằm ngoài miền của nó.
5. Bộ so sánh riêng khiến `floor("BEE")` và `ceiling("BEE")` trả đại diện `"Bee"`.
6. Các truy vấn chuỗi không sửa tập.

Hai chương trình độc lập trong bài đã được biên dịch bằng `--release 17` và chạy đối chiếu đúng stdout. Tùy chọn này kiểm tra mức ngôn ngữ/API và tạo bytecode tương thích Java 17; nó không khẳng định JVM thực thi chính là JDK 17.

## 10. Chọn phương thức bằng cách viết rõ yêu cầu

Thay vì học thuộc tên rời rạc, hãy chuyển câu hỏi thành một bất đẳng thức và xác định phía cần chọn.

| Câu hỏi trên các số tăng dần | Điều kiện | Phương thức |
| --- | --- | --- |
| Giá trị trước đó, bỏ qua giá trị hiện tại | `candidate < target`, chọn lớn nhất | `lower` |
| Giá trị lớn nhất không vượt mức giới hạn | `candidate <= limit`, chọn lớn nhất | `floor` |
| Cỡ nhỏ nhất đủ đáp ứng yêu cầu | `candidate >= request`, chọn nhỏ nhất | `ceiling` |
| Giá trị tiếp theo phải lớn hơn hiện tại | `candidate > current`, chọn nhỏ nhất | `higher` |

Ví dụ tập độ rộng hỗ trợ `[320, 640, 960, 1280]`:

- Yêu cầu tối thiểu `700`: `ceiling(700)` chọn `960`.
- Giới hạn tối đa `700`: `floor(700)` chọn `640`.
- Cỡ lớn hơn hẳn `640`: `higher(640)` chọn `960`.
- Cỡ nhỏ hơn hẳn `640`: `lower(640)` chọn `320`.

Nếu không có cỡ đáp ứng, kết quả là `null`; chương trình cần một nhánh xử lý yêu cầu không được đáp ứng, không tự giả định phần tử lớn nhất luôn đủ.

### 10.1. Không mô phỏng quan hệ chặt bằng cộng hoặc trừ 1

Đừng mặc định thay `lower(x)` bằng `floor(x - 1)` hoặc `higher(x)` bằng `ceiling(x + 1)`.

Cách đó phụ thuộc miền số nguyên rời rạc, có thể gặp biên tràn số, và không áp dụng cho chuỗi hoặc một bộ so sánh miền khác. Các phương thức chặt đã biểu đạt đúng ý nghĩa mà không cần chế tạo một ngưỡng khác.

### 10.2. Đây không phải phép làm tròn Math.floor

`floor(25)` trên tập `[10, 20, 30, 40]` trả về phần tử `20` đã tồn tại. Nó không thực hiện phép làm tròn số học để tạo ra một số nguyên từ giá trị thực.

Tương tự, `ceiling` của tập không sinh một phần tử mới. Nó chọn một đại diện đang có đáp ứng quan hệ.

### 10.3. Không phương thức nào tự chọn gần nhất theo khoảng cách tuyệt đối

Với tập `[20, 30]`, ngưỡng `29`:

```text
floor(29)   = 20
ceiling(29) = 30
```

`30` gần `29` hơn theo khoảng cách số học, nhưng `floor` vẫn phải trả phía không vượt ngưỡng.

Nếu yêu cầu là gần nhất theo khoảng cách, thường cần lấy ứng viên hai phía rồi so khoảng cách, xử lý thiếu một phía và quy tắc khi bằng khoảng cách. Đó là một thuật toán bổ sung, không phải nghĩa mặc định của `floor` hoặc `ceiling`.

Với các kiểu không có thước đo khoảng cách tự nhiên như mã chuỗi, “gần nhất” cần được định nghĩa theo yêu cầu, không suy ra chỉ từ việc đã có một thứ tự.

## 11. Cơ chế tìm và chi phí cần hiểu tới đâu?

`TreeSet` dựa trên cây có thứ tự từ `TreeMap`. Có thể hiểu phép tìm `floor` như việc thu hẹp dần vùng tìm, đồng thời giữ ứng viên tốt nhất đã thấy ở phía không vượt ngưỡng.

Nếu gặp giá trị bằng ngưỡng theo so sánh, một phép có lấy bằng có thể trả ứng viên đó. Với phép chặt, vẫn phải tìm phía trước hoặc sau thay vì nhận trường hợp bằng.

Phân tích triển khai cây cân bằng của `TreeSet` cho các phép tìm lân cận có số bước theo chiều cao cây, thường được mô tả là O(log n) phép so sánh. Đây là phân tích gắn với cấu trúc triển khai, không phải cam kết một thời gian tuyệt đối hoặc điều kiện bắt buộc cho mọi lớp có giao diện `NavigableSet`. Chi phí của từng phép so sánh cũng cần được xét nếu khóa phức tạp. [TreeMap: cấu trúc cây nền](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html), [Mã nguồn TreeMap OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/TreeMap.java)

Khi đã có một `NavigableSet` phù hợp, không cần chuyển cả tập thành danh sách rồi quét toàn bộ cho từng lần tìm ngưỡng. Cũng không thêm thử đối số để suy ra phần tử cạnh nó, vì bốn phương thức đã là phép đọc trực tiếp.

Các kết quả phụ thuộc vào trạng thái hiện tại của tập. Nhiều lời gọi riêng biệt không tự tạo thành một ảnh chụp nguyên tử nếu chương trình cho phép bên khác sửa dữ liệu xen giữa. Các ví dụ trong bài xét một tập ổn định trong lúc truy vấn.

## 12. Những lỗi thường gặp

| Cách hiểu hoặc cách viết | Vấn đề | Cách sửa |
| --- | --- | --- |
| Nghĩ lower và floor luôn giống nhau | Bỏ qua trường hợp ngưỡng đã có phần tử tương đương | Kiểm tra dấu bằng có được lấy không |
| Dùng ceiling để tìm phần tử sau hiện tại | Có thể nhận lại chính phần tử hiện tại | Dùng higher nếu cần quan hệ chặt |
| Chỉ truy vấn ngưỡng sau khi contains trả true | Ngưỡng không bắt buộc thuộc tập | Gọi trực tiếp phương thức điều hướng |
| Mong floor trả một phần tử bất kỳ nhỏ hơn | Bỏ qua yêu cầu chọn ứng viên lớn nhất ở phía đó | Xác định cả điều kiện và cực trị |
| Gán kết quả trực tiếp vào int | Có thể tự mở hộp null và ném ngoại lệ | Nhận Integer, xử lý trường hợp không có |
| Thấy null rồi kết luận tập rỗng | Có thể chỉ thiếu ứng viên ở một phía hoặc trong một view | Kiểm tra miền và hướng truy vấn |
| Giữ suy nghĩ số tăng dần khi dùng descendingSet | Hướng quan hệ đã đảo | Đọc theo thứ tự của khung nhìn |
| Nghĩ floor trả đúng đối tượng truy vấn | Nó trả đại diện đang lưu theo quy tắc so sánh | Xét Comparator và compare bằng 0 |
| Mong khung nhìn giới hạn tìm ra ngoài phạm vi | Ứng viên phải thuộc miền khung nhìn | Gọi đúng tập nhận truy vấn |
| Dùng floor để chọn gần nhất tuyệt đối | Chỉ chọn phía không vượt ngưỡng | Lấy hai phía rồi áp dụng thước đo nếu cần |
| Cộng/trừ 1 để thay lower/higher | Không tổng quát và có thể gặp biên số | Dùng đúng phương thức |
| Tưởng truy vấn đã lấy và xóa phần tử | Bốn phương thức này là phép đọc | Dùng thao tác sửa đổi riêng khi thực sự cần |

Trước một kết quả bất ngờ, hãy kiểm tra: tập nào đang nhận lời gọi, thứ tự nào đang dùng, ngưỡng có trường hợp tương đương không, phía cần tìm có lấy bằng không, và ứng viên có bị loại bởi miền của một khung nhìn không.

## 13. Tự luyện và đối chiếu

### Bài 1: phân biệt dấu bằng

Cho `NavigableSet<Integer>` theo thứ tự tự nhiên chứa `[5, 12, 18, 27]`.

Điền kết quả:

| Ngưỡng | lower | floor | ceiling | higher |
| --- | --- | --- | --- | --- |
| `12` | ? | ? | ? | ? |
| `15` | ? | ? | ? | ? |
| `4` | ? | ? | ? | ? |
| `30` | ? | ? | ? | ? |

Đối chiếu:

| Ngưỡng | lower | floor | ceiling | higher |
| --- | --- | --- | --- | --- |
| `12` | `5` | `12` | `12` | `18` |
| `15` | `12` | `12` | `18` | `18` |
| `4` | `null` | `null` | `5` | `5` |
| `30` | `27` | `27` | `null` | `null` |

### Bài 2: đảo thứ tự

Lấy khung nhìn giảm dần của tập bài 1. Dự đoán bốn kết quả với ngưỡng `15`.

Đối chiếu:

```text
lower(15)   = 18
floor(15)   = 18
ceiling(15) = 12
higher(15)  = 12
```

Giải thích bằng thứ tự giảm dần, không chỉ nhớ máy móc bốn con số.

### Bài 3: chọn theo yêu cầu

Tập cỡ hỗ trợ là `[100, 250, 500]`:

1. Cỡ nhỏ nhất ít nhất bằng `200`: `ceiling(200)` → `250`.
2. Cỡ lớn nhất không vượt `200`: `floor(200)` → `100`.
3. Cỡ tiếp theo lớn hơn hẳn `250`: `higher(250)` → `500`.
4. Cỡ nhỏ hơn hẳn `100`: `lower(100)` → `null`.

Câu cuối cần một nhánh không có kết quả, không phải một giá trị số thay thế ngầm định.

### Bài 4: giải thích một lỗi mở hộp

Với tập bài 1, đoạn:

```java
int result = values.ceiling(30);
```

có thể ném `NullPointerException` vì `ceiling(30)` không có ứng viên và trả `null`. Hãy sửa bằng một biến `Integer` và một nhánh kiểm tra trước khi sử dụng như `int`.

### Bài 5: phân biệt phạm vi tìm

Tập gốc là `[5, 12, 18, 27]`, khung nhìn chỉ chứa `[12, 18]`.

`floor(25)` trên hai đối tượng đều là `18`, nhưng `ceiling(20)` trên tập gốc là `27`, còn trên khung nhìn không có ứng viên nên là `null`.

Điều cần giải thích là miền ứng viên, không phải nói ngưỡng ngoài tập luôn làm phương thức lỗi.

## 14. Tài liệu tra cứu

- [NavigableSet Java 17 — bốn phép tìm, khung nhìn và giá trị null](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableSet.html)
- [TreeSet Java 17 — triển khai tập có thứ tự và Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)
- [Comparator — quy tắc thứ tự và tính nhất quán với equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html)
- [TreeMap — cấu trúc cây nền để hiểu cơ chế tìm](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sorted-set-007">pollFirst, pollLast, descendingSet và descendingIterator</a>

<details>
<summary>Xem chi tiết</summary>

# pollFirst, pollLast, descendingSet và descendingIterator

## 1. Chọn cách đọc hoặc lấy phần tử theo thứ tự

Giả sử một tập lưu các mức `10, 20, 30, 40` theo thứ tự tăng dần. Bạn có thể cần xem mức nhỏ nhất mà vẫn giữ dữ liệu, lấy mức lớn nhất ra để xử lý, hoặc đọc từ lớn đến nhỏ.

Ba nhu cầu ấy khác nhau ở hai điểm: **chiều đi qua dữ liệu** và **có xóa phần tử hay không**. Nếu nhầm, mã chỉ định hiển thị có thể làm mất dữ liệu; mã định xử lý từng phần tử lại có thể lặp mãi vì chưa xóa gì.

Bốn phương thức trong bài nằm trên `NavigableSet`:

- `pollFirst()`: lấy và xóa phần tử đầu.
- `pollLast()`: lấy và xóa phần tử cuối.
- `descendingSet()`: tạo khung nhìn ngược thứ tự, dùng chung các thành viên với tập nguồn.
- `descendingIterator()`: tạo bộ duyệt đi ngược thứ tự.

“Đầu/cuối” được xác định theo thứ tự của chính tập hoặc khung nhìn mà bạn gọi phương thức, không theo thời điểm phần tử được thêm. Nếu dùng `Comparator` đảo, số lớn có thể ở đầu của tập gốc và số nhỏ ở đầu của khung nhìn đảo.

Bài học dùng `TreeSet` để kiểm chứng các hành vi trên bằng API có trong Java 17. Bạn cần biết biến, vòng lặp và cách dùng một tập hợp; `NavigableSet`, khung nhìn và bộ duyệt sẽ được giải thích trước khi đi vào mã đầy đủ.

## 2. NavigableSet liên hệ thế nào với Set sắp xếp?

`Set` mô tả tập hợp không trùng. `SortedSet` bổ sung một thứ tự cùng các phép như `first()`, `last()` và lấy vùng theo thứ tự. `NavigableSet` mở rộng tiếp với các phép điều hướng và khả năng làm việc từ hai chiều.

`TreeSet` là một lớp triển khai `NavigableSet`. Có thể khai báo:

~~~java
NavigableSet<Integer> values =
        new TreeSet<>(List.of(40, 10, 30, 20));
~~~

Đây là khai báo minh họa; chương trình hoàn chỉnh phía dưới có các lệnh nhập kiểu cần thiết.

Đối tượng dùng thứ tự tự nhiên của `Integer`, nên duyệt thông thường được `[10, 20, 30, 40]`. Phần tử đầu là 10, phần tử cuối là 40, dù 40 được đưa vào danh sách nguồn trước.

Kiểu khai báo có ý nghĩa khi gọi API. Nếu biến chỉ có kiểu `Set<Integer>` hoặc `SortedSet<Integer>` trong Java 17, trình biên dịch không cho gọi mọi phương thức riêng của `NavigableSet` qua biến ấy. Chọn kiểu thể hiện đúng khả năng mà phần mã cần dùng thay vì ép kiểu tùy tiện.

`NavigableSet` còn có các phép tìm lân cận và các vùng với biên tùy chọn. Trong bài, chúng ta chỉ dùng một vùng nhỏ để quan sát phạm vi tác động của bốn phương thức chính. [Java 17: NavigableSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableSet.html).

## 3. Phân biệt đọc, lấy ra và duyệt

Với một `TreeSet` có thể sửa, bảng sau giúp chọn thao tác:

| Thao tác | Kết quả trả về | Tự xóa thành viên? | Khi không có phần tử |
| --- | --- | --- | --- |
| `first()` | Phần tử đầu | Không | `NoSuchElementException` |
| `last()` | Phần tử cuối | Không | `NoSuchElementException` |
| `pollFirst()` | Phần tử đầu bị lấy ra | Có, nếu có phần tử | Trả `null` |
| `pollLast()` | Phần tử cuối bị lấy ra | Có, nếu có phần tử | Trả `null` |
| `descendingSet()` | Một `NavigableSet` nhìn theo chiều ngược | Không vì riêng việc tạo khung nhìn | Khung nhìn rỗng |
| `descendingIterator()` | Một `Iterator` đi theo chiều ngược | Không vì riêng việc tạo hoặc đọc | `hasNext()` là `false` |

`first` và `last` là những phép đọc. `pollFirst` và `pollLast` là những phép thay đổi thành viên. Khác biệt khi rỗng cũng là một phần hợp đồng, không chỉ cách đặt tên. [Java 17: SortedSet.first và last](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SortedSet.html#first()).

Một bộ duyệt hết phần tử không trả `null` khi gọi `next()`. Nó ném `NoSuchElementException`. Dùng `hasNext()` để biết còn phần tử trước khi gọi tiếp.

Đừng gộp tất cả các thao tác trên thành “lấy đầu/cuối”. Khi xem lại một đoạn mã, hãy xác định rõ giá trị được trả về là phần tử, khung nhìn hay bộ duyệt, rồi mới xét tác dụng lên dữ liệu.

## 4. pollFirst và pollLast vừa trả phần tử vừa xóa nó

Từ trạng thái ban đầu:

~~~text
[10, 20, 30, 40]
~~~

thực hiện lần lượt:

~~~text
pollFirst() → trả 10; tập còn [20, 30, 40]
pollLast()  → trả 40; tập còn [20, 30]
~~~

Mỗi lời gọi thành công loại một thành viên khỏi tập. Không cần gọi tiếp `remove` cho chính phần tử vừa được trả về.

Khi tập rỗng, cả hai trả `null`. Chúng không chờ đến khi có dữ liệu mới, cũng không tự nạp thêm dữ liệu từ nơi khác. Nếu chương trình cần chờ công việc mới đến, đó là một yêu cầu khác với thao tác trên `TreeSet` của bài.

Không dùng `first()` trong một vòng lặp với kỳ vọng mỗi lần đọc sẽ bỏ phần tử cũ. Nếu tập không thay đổi, `first()` tiếp tục trả cùng phần tử đầu.

Cũng không hiểu `pollFirst` là phần tử được thêm sớm nhất. `TreeSet` tổ chức theo thứ tự so sánh, nên thứ tự xử lý bằng `pollFirst` đi theo quy tắc đó. Nếu cần thứ tự đến trước xử lý trước, phải chọn một mô hình phù hợp với thứ tự sự kiện.

Một hệ quả quan trọng khi xử lý công việc: sau khi `pollFirst` trả về, phần tử đã rời tập. Nếu đoạn xử lý tiếp theo thất bại, tập không tự khôi phục nó. Có cần đưa lại hay ghi nhận trạng thái xử lý là quyết định của mã ứng dụng, không phải tác dụng mặc định của `poll`.

## 5. descendingSet là một khung nhìn, không phải bản sao

Từ tập `base = [10, 20, 30]` theo thứ tự tự nhiên:

~~~java
NavigableSet<Integer> descending = base.descendingSet();
~~~

cho khung nhìn có thứ tự duyệt `[30, 20, 10]`.

Có thể hình dung:

~~~text
cùng các thành viên: 10, 20, 30

base:        10 → 20 → 30
descending:  30 → 20 → 10
~~~

Hai dòng là hai hướng nhìn trên dữ liệu được chia sẻ. Không có bước đảo vĩnh viễn quy tắc của `base`. Sau khi tạo khung nhìn, duyệt `base` vẫn cho `10, 20, 30`.

Thêm hoặc xóa qua một phía có thể quan sát ở phía kia:

~~~text
descending.add(25)
base        → [10, 20, 25, 30]
descending  → [30, 25, 20, 10]
~~~

Sau đó thêm `40` qua `base`, khung nhìn đảo sẽ thấy `[40, 30, 25, 20, 10]` khi được duyệt lại.

Việc tạo khung nhìn không nhân bản mọi phần tử và không làm dữ liệu độc lập. Nếu truyền `descending` cho một hàm có thể xóa thành viên, hàm ấy cũng có thể làm thay đổi tập gốc. Đây là thông tin quan trọng trong hợp đồng của hàm, kể cả khi tên biến chỉ mang ý nghĩa “dữ liệu để hiển thị”.

Theo API, thứ tự của khung nhìn tương đương đảo quy tắc so sánh của nguồn. Tạo khung nhìn đảo lần nữa cho một khung nhìn về cơ bản tương đương tập ban đầu; không cần giả định hai biến có cùng tham chiếu đối tượng. [Java 17: TreeSet.descendingSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html#descendingSet()).

## 6. Hai đầu đổi vai khi nhìn theo chiều ngược

Giả sử `base` dùng thứ tự tự nhiên và hiện có:

~~~text
base        = [10, 20, 25, 30, 40]
descending  = [40, 30, 25, 20, 10]
~~~

Lúc này:

| Lời gọi | Thành viên bị xóa | Tương ứng trên tập gốc |
| --- | --- | --- |
| `descending.pollFirst()` | `40` | Lấy cuối của `base` |
| `descending.pollLast()` | `10` | Lấy đầu của `base` |

Các hàng mô tả quan hệ hai đầu; nếu chạy liên tiếp thì mỗi lời gọi thấy trạng thái sau lần trước.

Không phải chỉ chuỗi in ra bị đảo. Các thao tác có hướng của khung nhìn cũng được hiểu theo chiều đảo. Vì thế, `descending.first()` là `base.last()` khi tập không rỗng; `descending.last()` là `base.first()`.

Với cùng một trạng thái và cùng phạm vi thành viên, có thể dùng quy tắc nhớ:

~~~text
đầu của khung nhìn đảo  ↔ cuối của nguồn
cuối của khung nhìn đảo ↔ đầu của nguồn
~~~

Nếu nguồn là một vùng giới hạn của tập lớn hơn, “nguồn” ở đây là chính vùng đó. Phần tử cuối của vùng chưa chắc là phần tử cuối của toàn bộ tập lớn.

Tạo `descending` không thay ý nghĩa của `base.pollFirst()`. Lời gọi trên `base` vẫn dùng chiều của `base`; lời gọi trên `descending` dùng chiều của khung nhìn.

## 7. descendingIterator chỉ tạo một lượt duyệt ngược

Nếu chỉ cần đi qua phần tử theo chiều ngược, có thể dùng:

~~~java
Iterator<Integer> iterator = values.descendingIterator();
while (iterator.hasNext()) {
    Integer value = iterator.next();
    System.out.println(value);
}
~~~

Đoạn minh họa giả sử `values` là một `NavigableSet<Integer>`. `hasNext` hỏi còn phần tử; `next` lấy phần tử kế tiếp trong lượt duyệt. Không có lệnh xóa trong đoạn này.

Với `values = [1, 2, 3, 4]` tự nhiên, lượt duyệt cho `4, 3, 2, 1` nhưng tập vẫn giữ đủ bốn phần tử.

`descendingIterator()` tương đương về tác dụng duyệt với `descendingSet().iterator()`. Chọn cách thứ nhất khi chỉ cần một bộ duyệt; chọn khung nhìn khi cần tiếp tục dùng các phép tập hợp theo chiều đảo.

Tên “descending iterator” không biến kiểu trả về thành `ListIterator`. Bạn vẫn gọi `next()` để tiến qua lượt duyệt ngược đã chọn, không gọi `previous()`, không thêm phần tử vào một vị trí danh sách và không dùng chỉ số.

Bộ duyệt là trạng thái của **một lượt đi qua**. Sau khi đã đọc hết, muốn đọc lại phải tạo bộ duyệt mới. Gọi `hasNext` không tự quay lại đầu, còn gọi `next` sau điểm cuối ném lỗi.

## 8. Xóa trong lúc duyệt bằng chính iterator

Bộ duyệt của `TreeSet` hỗ trợ `remove()`. Phương thức này không nhận một giá trị để tìm; nó xóa phần tử cuối cùng mà chính bộ duyệt vừa trả về bằng `next()`.

Ví dụ lọc các số chẵn trong lượt duyệt ngược:

~~~java
Iterator<Integer> iterator = values.descendingIterator();
while (iterator.hasNext()) {
    int value = iterator.next();
    if (value % 2 == 0) {
        iterator.remove();
    }
}
~~~

Đoạn này được kiểm chứng trong chương trình đầy đủ với tập `[1, 2, 3, 4]`. Nó đi qua `4, 3, 2, 1` và cuối cùng giữ `[1, 3]`.

Thứ tự duyệt ngược không đảo điều kiện số chẵn. Nó chỉ thay trình tự các phần tử được đưa vào điều kiện.

Quy tắc trạng thái của `Iterator.remove`:

- Phải có một lần `next` trả phần tử trước đó.
- Sau mỗi lần `next`, chỉ được `remove` một lần.
- Gọi trước `next` hoặc gọi lần hai cho cùng phần tử dẫn đến `IllegalStateException` trong bộ duyệt của ví dụ.
- `remove` là thao tác tùy chọn ở interface `Iterator`; đừng mặc định mọi bộ duyệt của mọi cấu trúc đều hỗ trợ nó.

Đừng thay `iterator.remove()` bằng `values.remove(value)` hoặc `values.pollLast()` trong chính vòng lặp ấy rồi tiếp tục dùng bộ duyệt cũ. Bạn đang thay đổi dữ liệu ngoài đường cập nhật mà bộ duyệt đang theo dõi. [Java 17: Iterator.remove](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#remove()).

## 9. Chương trình đầy đủ dùng Java 17

Lưu thành `NavigableSetEndsDemo.java`. Chương trình tách dữ liệu của từng nhóm ví dụ để việc xóa ở nhóm này không làm nhầm kết quả ở nhóm khác.

`readAll` chỉ tiêu thụ một bộ duyệt và ghi kết quả vào danh sách. `drainFirst` lấy hết thành viên bằng `pollFirst` và làm rỗng tập được truyền vào. Hai hàm có chủ đích khác nhau.

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public class NavigableSetEndsDemo {
    static <E> List<E> readAll(Iterator<E> iterator) {
        List<E> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    static <E> List<E> drainFirst(NavigableSet<E> set) {
        List<E> removed = new ArrayList<>();
        while (!set.isEmpty()) {
            removed.add(set.pollFirst());
        }
        return removed;
    }

    public static void main(String[] args) {
        NavigableSet<Integer> ends = new TreeSet<>(List.of(40, 10, 30, 20));
        System.out.println("ends initial=" + ends);
        System.out.println("read first/last=" + ends.first() + "/" + ends.last());
        System.out.println("after read=" + ends);
        System.out.println("pollFirst=" + ends.pollFirst());
        System.out.println("pollLast=" + ends.pollLast());
        System.out.println("ends remaining=" + ends);
        System.out.println("drained=" + drainFirst(ends));
        System.out.println("ends empty=" + ends.isEmpty());
        System.out.println("empty pollFirst=" + ends.pollFirst());
        System.out.println("empty pollLast=" + ends.pollLast());

        NavigableSet<Integer> base = new TreeSet<>(List.of(10, 20, 30));
        NavigableSet<Integer> descending = base.descendingSet();
        System.out.println("base=" + base);
        System.out.println("descending=" + descending);
        System.out.println("descending first/last="
                + descending.first() + "/" + descending.last());
        descending.add(25);
        System.out.println("base after view add=" + base);
        base.add(40);
        System.out.println("view after base add=" + descending);
        System.out.println("view pollFirst=" + descending.pollFirst());
        System.out.println("base after view pollFirst=" + base);
        System.out.println("view pollLast=" + descending.pollLast());
        System.out.println("base after view pollLast=" + base);
        System.out.println("double reverse order="
                + new ArrayList<>(base).equals(
                        new ArrayList<>(descending.descendingSet())));

        NavigableSet<Integer> copy = new TreeSet<>(descending);
        base.add(35);
        System.out.println("base after later add=" + base);
        System.out.println("view after later add=" + descending);
        System.out.println("independent copy=" + copy);
        System.out.println("copy pollFirst=" + copy.pollFirst());
        System.out.println("base after copy poll=" + base);

        NavigableSet<Integer> iteration = new TreeSet<>(List.of(1, 2, 3, 4));
        System.out.println("descending traversal=" + readAll(iteration.descendingIterator()));
        System.out.println("after traversal=" + iteration);
        Iterator<Integer> iterator = iteration.descendingIterator();
        try {
            iterator.remove();
        } catch (IllegalStateException exception) {
            System.out.println("remove before next=" + exception.getClass().getSimpleName());
        }
        List<Integer> visited = new ArrayList<>();
        while (iterator.hasNext()) {
            int value = iterator.next();
            visited.add(value);
            if (value % 2 == 0) {
                iterator.remove();
            }
        }
        System.out.println("visited while filtering=" + visited);
        System.out.println("after iterator remove=" + iteration);
        try {
            iterator.next();
        } catch (java.util.NoSuchElementException exception) {
            System.out.println("next after end=" + exception.getClass().getSimpleName());
        }

        NavigableSet<Integer> custom = new TreeSet<>(Comparator.reverseOrder());
        custom.addAll(List.of(8, 2, 5));
        System.out.println("custom order=" + custom);
        System.out.println("custom descending=" + custom.descendingSet());
        System.out.println("custom descending iterator="
                + readAll(custom.descendingIterator()));
        System.out.println("custom view pollFirst=" + custom.descendingSet().pollFirst());
        System.out.println("custom after view poll=" + custom);
        System.out.println("custom pollFirst=" + custom.pollFirst());
        System.out.println("custom remaining=" + custom);

        NavigableSet<Integer> all = new TreeSet<>(List.of(1, 2, 3, 4, 5));
        NavigableSet<Integer> range = all.subSet(2, true, 5, false);
        NavigableSet<Integer> reverseRange = range.descendingSet();
        System.out.println("reverse range=" + reverseRange);
        System.out.println("range pollFirst=" + reverseRange.pollFirst());
        System.out.println("all after range poll=" + all);
        try {
            reverseRange.add(5);
        } catch (IllegalArgumentException exception) {
            System.out.println("outside range=" + exception.getClass().getSimpleName());
        }

        NavigableSet<String> nullable =
                new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));
        nullable.addAll(Arrays.asList(null, "b", "a"));
        System.out.println("nullable initial=" + nullable);
        System.out.println("nullable first poll=" + nullable.pollFirst());
        System.out.println("empty after null poll=" + nullable.isEmpty());
        System.out.println("nullable remaining=" + nullable);
        System.out.println("nullable drained=" + drainFirst(nullable));
        System.out.println("nullable empty poll=" + nullable.pollFirst());
        nullable.addAll(Arrays.asList(null, "b", "a"));
        System.out.println("nullable full drain=" + drainFirst(nullable));
        System.out.println("nullable finally empty=" + nullable.isEmpty());
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 NavigableSetEndsDemo.java
java NavigableSetEndsDemo
~~~

Dùng JDK 17 trở lên. `--release 17` yêu cầu kiểm tra mã và API theo Java 17. Tên tệp phải khớp tên lớp `public`.

Kết quả chuẩn:

~~~text
ends initial=[10, 20, 30, 40]
read first/last=10/40
after read=[10, 20, 30, 40]
pollFirst=10
pollLast=40
ends remaining=[20, 30]
drained=[20, 30]
ends empty=true
empty pollFirst=null
empty pollLast=null
base=[10, 20, 30]
descending=[30, 20, 10]
descending first/last=30/10
base after view add=[10, 20, 25, 30]
view after base add=[40, 30, 25, 20, 10]
view pollFirst=40
base after view pollFirst=[10, 20, 25, 30]
view pollLast=10
base after view pollLast=[20, 25, 30]
double reverse order=true
base after later add=[20, 25, 30, 35]
view after later add=[35, 30, 25, 20]
independent copy=[30, 25, 20]
copy pollFirst=30
base after copy poll=[20, 25, 30, 35]
descending traversal=[4, 3, 2, 1]
after traversal=[1, 2, 3, 4]
remove before next=IllegalStateException
visited while filtering=[4, 3, 2, 1]
after iterator remove=[1, 3]
next after end=NoSuchElementException
custom order=[8, 5, 2]
custom descending=[2, 5, 8]
custom descending iterator=[2, 5, 8]
custom view pollFirst=2
custom after view poll=[8, 5]
custom pollFirst=8
custom remaining=[5]
reverse range=[4, 3, 2]
range pollFirst=4
all after range poll=[1, 2, 3, 5]
outside range=IllegalArgumentException
nullable initial=[null, a, b]
nullable first poll=null
empty after null poll=false
nullable remaining=[a, b]
nullable drained=[a, b]
nullable empty poll=null
nullable full drain=[null, a, b]
nullable finally empty=true
~~~

## 10. Đọc kết quả theo tác dụng lên dữ liệu

### 10.1. Đọc đầu/cuối khác với lấy ra

`read first/last=10/40` đi cùng `after read=[10, 20, 30, 40]`: hai phép đọc không xóa.

Sau `pollFirst=10` và `pollLast=40`, tập chỉ còn `[20, 30]`. Hàm `drainFirst` trả danh sách `[20, 30]` và tập trở thành rỗng. Hai lần `poll` cuối trả `null` vì không còn thành viên.

Danh sách được hàm `drainFirst` trả về là dữ liệu đã lấy ra, không phải một khung nhìn tiếp tục thay đổi theo tập vừa bị làm rỗng.

### 10.2. Khung nhìn dùng chung thành viên

`base after view add=[10, 20, 25, 30]` cho thấy thêm qua `descending` có tác dụng lên `base`.

`view after base add=[40, 30, 25, 20, 10]` cho thấy chiều ngược lại: thêm 40 qua `base` xuất hiện khi đọc khung nhìn đảo. Đây là đọc lại sau thay đổi, không phải tiếp tục một bộ duyệt cũ đang hoạt động.

Hai lời gọi `poll` qua khung nhìn lần lượt xóa 40 và 10 khỏi tập gốc. Khác tên biến không tạo hai kho thành viên độc lập.

### 10.3. Đảo hai lần được kiểm tra bằng trình tự

`double reverse order=true` được tính bằng cách chuyển hai phía thành `ArrayList` rồi so sánh. Cách này kiểm tra trình tự, không chỉ cùng thành viên.

Nếu chỉ dùng `base.equals(descending)`, kết quả có thể đúng vì `Set.equals` không dùng thứ tự duyệt. Phép bằng nhau của tập không phải bằng chứng rằng hai phía đang có cùng chiều đi qua dữ liệu.

### 10.4. Bộ duyệt đọc không làm mất phần tử

`descending traversal=[4, 3, 2, 1]` đi cùng `after traversal=[1, 2, 3, 4]` xác nhận chỉ đọc thì tập không bị rút bớt.

Trong lượt lọc kế tiếp, `visited while filtering` vẫn chứa cả bốn số theo chiều ngược. Chỉ số 4 và số 2 bị xóa qua `iterator.remove`, để lại `[1, 3]`.

Lỗi `remove before next` và `next after end` là hai lỗi trạng thái khác nhau: chưa có phần tử hợp lệ để xóa, và không còn phần tử để đọc.

## 11. Comparator đảo có thể làm descending trở thành tăng dần về số học

Chương trình tạo:

~~~java
NavigableSet<Integer> custom =
        new TreeSet<>(Comparator.reverseOrder());
~~~

Với dữ liệu `8, 2, 5`, thứ tự của tập gốc là:

~~~text
custom             = [8, 5, 2]
custom.descendingSet() = [2, 5, 8]
~~~

“Descending” nghĩa là ngược **thứ tự hiện tại của nguồn**, không phải lệnh bắt buộc in số lớn trước số nhỏ.

Do đó:

~~~text
custom.descendingSet().pollFirst() → trả 2
custom sau đó                     → [8, 5]

custom.pollFirst()                → trả 8
custom sau đó                     → [5]
~~~

Cùng tên `pollFirst` nhưng hai đối tượng nhận lời gọi có hai hướng khác nhau. Nếu chỉ nhớ “pollFirst lấy số nhỏ nhất” sẽ dự đoán sai cả hai lời gọi trên.

Khi tập chứa đối tượng nghiệp vụ, thay “số lớn/nhỏ” bằng quy tắc của bộ so sánh: điểm giảm, thời gian tăng hay một chuỗi tiêu chí. Đọc chính xác quy tắc nguồn rồi đảo hướng để hiểu khung nhìn.

Đảo thứ tự không thay đổi những cặp phần tử mà bộ so sánh xem là tương đương. `descendingSet` vẫn là một tập trên cùng thành viên; nó không phục hồi các bản ghi đã bị coi là trùng lúc thêm.

## 12. Bản sao độc lập khác với khung nhìn đảo

Nếu cần thay đổi dữ liệu theo chiều ngược mà không làm biến đổi thành viên của nguồn, hãy tạo bản sao thật sự với quy tắc mong muốn.

Chương trình dùng:

~~~java
NavigableSet<Integer> copy = new TreeSet<>(descending);
~~~

Ở đây `descending` có kiểu `NavigableSet`, cũng là một `SortedSet`. Hàm khởi tạo nhận `SortedSet` giữ thứ tự của nguồn. Vì vậy, bản sao có trình tự `[30, 25, 20]` tại thời điểm sao chép.

Sau đó `base.add(35)`:

~~~text
base        = [20, 25, 30, 35]
descending  = [35, 30, 25, 20]
copy        = [30, 25, 20]
~~~

Khung nhìn thấy thành viên mới; bản sao không nhận nó. Gọi `copy.pollFirst()` trả 30 và không xóa 30 của `base`.

Tuy nhiên, đây là độc lập về cấu trúc chứa thành viên, không phải sao chép sâu các đối tượng. Nếu phần tử là đối tượng có thể sửa, hai tập có thể vẫn giữ tham chiếu tới cùng đối tượng. Trong chương trình, `Integer` là giá trị bất biến nên không có thao tác sửa nội dung phần tử gây nhiễu.

Cũng cần đọc đúng hàm khởi tạo được chọn. Một nguồn được khai báo chỉ là `Collection` có thể chọn hàm tạo theo thứ tự tự nhiên thay vì giữ quy tắc đảo. Khi thứ tự của bản sao quan trọng, truyền rõ quy tắc hoặc dùng đúng kiểu nguồn cho hàm khởi tạo nhận `SortedSet`. [Java 17: TreeSet, các hàm khởi tạo](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html).

## 13. Khung nhìn đảo của một vùng chỉ tác động trong vùng

Trong chương trình:

~~~java
NavigableSet<Integer> all = new TreeSet<>(List.of(1, 2, 3, 4, 5));
NavigableSet<Integer> range = all.subSet(2, true, 5, false);
NavigableSet<Integer> reverseRange = range.descendingSet();
~~~

Vùng `range` chứa `2, 3, 4`: tính biên 2, không tính biên 5. Khung nhìn đảo là `[4, 3, 2]`.

`reverseRange.pollFirst()` lấy 4, không phải 5, vì 5 ở ngoài phạm vi thành viên của vùng. Tập lớn trở thành `[1, 2, 3, 5]`; các thành viên ngoài vùng vẫn còn.

Đảo hướng không xóa giới hạn. `reverseRange.add(5)` vẫn không hợp lệ và ném `IllegalArgumentException`. Việc 5 đang thuộc tập lớn không khiến nó thuộc vùng được truyền vào.

Điều này quan trọng khi viết hàm nhận `NavigableSet` làm tham số. Hàm có thể đang nhận toàn bộ tập hoặc chỉ một khung nhìn giới hạn. “Làm rỗng tập được truyền vào” qua các lần `pollFirst` sẽ xóa hết phần đang nhìn thấy, không nhất thiết xóa hết mọi thành viên của tập lớn ban đầu.

Ngược lại, nếu phần được truyền vào đúng là khung nhìn dùng chung, các phần tử bị lấy ra vẫn biến mất khỏi cấu trúc nguồn. Phạm vi hẹp hơn không đồng nghĩa độc lập dữ liệu.

## 14. null có hai nghĩa khi poll trả về

Tập theo thứ tự tự nhiên trong phần lớn ví dụ không cho phép phần tử `null`. Với mô hình như vậy, nhận `null` từ `pollFirst` hoặc `pollLast` có thể dùng làm dấu hiệu không còn phần tử.

Nhưng `TreeSet` có thể nhận `null` nếu được cung cấp bộ so sánh phù hợp. Phần cuối chương trình dùng `Comparator.nullsFirst(Comparator.naturalOrder())`, tạo thứ tự `[null, a, b]`.

Lời gọi đầu:

~~~text
nullable.pollFirst() → null
nullable.isEmpty()   → false
nullable            → [a, b]
~~~

đã lấy ra **một thành viên thật mang giá trị null**. Nó không có nghĩa tập ban đầu rỗng. Sau khi lấy hết `a, b`, lần `pollFirst` tiếp theo cũng trả `null`, nhưng lần này tập thực sự không còn gì.

Bộ bao `nullsFirst` quy định cách so sánh `null` với các phần tử khác; nó không thay hợp đồng giá trị trả về của `poll`. [Java 17: Comparator.nullsFirst](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html#nullsFirst(java.util.Comparator)).

Vì vậy, mẫu sau chỉ thích hợp khi hợp đồng dữ liệu loại `null`:

~~~java
// Minh họa: chỉ dùng khi null không thể là một thành viên.
Integer value;
while ((value = values.pollFirst()) != null) {
    System.out.println(value);
}
~~~

Nếu tập có `null` ở đầu, vòng lặp dừng sau khi đã lấy nó ra và bỏ lại các thành viên khác chưa xử lý.

Đừng gán ngay kết quả có thể `null` vào `int`. Việc tự mở gói từ `Integer` bằng `null` gây `NullPointerException` trước khi bạn có thể xử lý dấu hiệu rỗng.

## 15. Lấy hết dữ liệu cần chọn điều kiện dừng đúng

Hàm của chương trình:

~~~java
static <E> List<E> drainFirst(NavigableSet<E> set) {
    List<E> removed = new ArrayList<>();
    while (!set.isEmpty()) {
        removed.add(set.pollFirst());
    }
    return removed;
}
~~~

là đoạn trích từ mã đầy đủ. Trong điều kiện một luồng thao tác và không có mã khác xen vào sửa tập, nó xử lý được cả thành viên `null` vì điều kiện dừng dựa vào sự rỗng, không dựa vào giá trị phần tử vừa lấy.

Nếu truyền tập gốc theo thứ tự tự nhiên, danh sách trả về theo chiều tự nhiên. Nếu truyền khung nhìn đảo, danh sách trả về theo chiều đảo và các thành viên tương ứng bị xóa khỏi nguồn.

Dòng `nullable full drain=[null, a, b]` kiểm chứng riêng việc truyền một tập còn chứa null vào hàm: cả null lẫn hai chuỗi đều được lấy ra, và tập rỗng sau đó.

Hàm tạo danh sách chứa toàn bộ kết quả để dễ kiểm chứng. Nếu mục tiêu là xử lý rồi bỏ từng phần tử, ứng dụng có thể xử lý trực tiếp thay vì tích lũy danh sách; cần vẫn xác định rõ chính sách lỗi sau khi phần tử đã bị lấy ra.

Vòng `isEmpty` rồi `pollFirst` không phải một cặp thao tác tự nhiên nguyên tử. Với dữ liệu chia sẻ giữa nhiều luồng, một tác nhân khác có thể thay đổi trạng thái giữa hai lời gọi. Đừng chuyển nguyên xi suy luận một luồng của hàm này thành bảo đảm cạnh tranh cho `TreeSet`.

`TreeSet` không có đồng bộ hóa sẵn. Nếu cần chia sẻ và sửa từ nhiều luồng, phải thiết kế cách phối hợp hoặc chọn cách triển khai đáp ứng hợp đồng cạnh tranh mong muốn, đồng thời xem lại cách xử lý `null` và bộ duyệt của cách triển khai ấy.

## 16. Khung nhìn thấy cập nhật không có nghĩa iterator cũ luôn dùng tiếp được

Hai ý sau khác nhau:

- Khung nhìn dùng chung dữ liệu, nên một lượt đọc mới thấy các thay đổi hợp lệ ở nguồn.
- Bộ duyệt đã được tạo có trạng thái riêng của lượt duyệt và có quy tắc về thay đổi trong lúc đi qua dữ liệu.

Nếu đang dùng bộ duyệt trên `base` mà gọi `descending.pollFirst()`, bạn vẫn đang xóa khỏi cùng dữ liệu nền ngoài chính bộ duyệt đó. Đổi chiều hoặc đổi tên biến không tạo ngoại lệ cho quy tắc sửa dữ liệu khi duyệt.

Bộ duyệt của `TreeSet` có cơ chế báo lỗi sớm khi phát hiện thay đổi cấu trúc ngoài thao tác `remove` của chính nó. `ConcurrentModificationException` hỗ trợ phát hiện lỗi theo khả năng, không được dùng làm cơ chế đồng bộ hay làm điều kiện đúng đắn của chương trình. Không thấy ngoại lệ trong một thử nghiệm không chứng minh cách sửa ấy hợp lệ. [Java 17: TreeSet, chính sách bộ duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html).

Chọn một đường cập nhật cho mỗi lượt làm việc:

- Nếu chỉ đọc, dùng bộ duyệt và không sửa dữ liệu nền trong lượt ấy.
- Nếu lọc khi đang duyệt trên `TreeSet`, dùng `remove` của chính bộ duyệt theo đúng trạng thái.
- Nếu lấy hết phần tử để xử lý, có thể lặp bằng `pollFirst` hoặc `pollLast` mà không giữ một bộ duyệt cũ đang hoạt động.
- Nếu cần lập kế hoạch rồi sửa, kết thúc lượt duyệt trước khi áp dụng thay đổi, hoặc dùng một bản sao độc lập theo yêu cầu.

Các lựa chọn này làm rõ quan hệ giữa cách duyệt và tác dụng lên thành viên, thay vì dựa vào việc mã có tình cờ chạy hết hay không.

## 17. Chọn API theo công việc cần làm

| Công việc | Cách dùng phù hợp trong phạm vi bài |
| --- | --- |
| Xem phần tử đầu/cuối nhưng giữ nó | `first` / `last`, có xử lý trường hợp rỗng |
| Lấy và xóa một phần tử đầu/cuối | `pollFirst` / `pollLast` |
| Chỉ đọc theo chiều ngược | `descendingIterator` hoặc vòng lặp trên `descendingSet` |
| Cần nhiều phép tập hợp theo chiều ngược | Giữ một biến khung nhìn từ `descendingSet` |
| Lọc có điều kiện trong một lượt duyệt ngược | `descendingIterator` và `iterator.remove` |
| Lấy hết từ phía cuối của nguồn | Có thể dùng `pollLast` trên nguồn hoặc `pollFirst` trên khung nhìn đảo |
| Sửa độc lập với thành viên của nguồn | Tạo bản sao thật, xác định rõ quy tắc thứ tự |
| Chỉ xử lý một khoảng | Tạo vùng, rồi chọn chiều nhìn; thay đổi vẫn truyền về nguồn |

Không chọn `poll` chỉ vì tên có vẻ tiện hơn `first` nếu yêu cầu là đọc. Cũng không tạo một bản sao chỉ để đổi chiều nếu khung nhìn hoặc bộ duyệt đã đáp ứng đúng hợp đồng chia sẻ dữ liệu mà bạn cần.

Về chi phí, `descendingSet` không tự tạo một kho chứa độc lập của toàn bộ thành viên. Một bản sao thật phải lưu cấu trúc thành viên riêng. Duyệt toàn bộ hoặc tạo danh sách chứa toàn bộ kết quả phải đi qua các phần tử tương ứng.

`TreeSet` công bố chi phí `O(log n)` cho các phép cơ bản `add`, `remove` và `contains`. Không suy từ việc lấy hai đầu hay có khung nhìn rằng mọi cách triển khai `NavigableSet` đều có cùng chi phí, cùng chính sách bộ duyệt hoặc cùng khả năng chia sẻ giữa các luồng.

Vẫn nhớ đây là tập hợp: những lần thêm được xem là trùng theo phép so sánh không tạo nhiều thành viên để sau này `poll` từng lần. Nếu bạn cần lưu mọi công việc cùng một mức ưu tiên, tiêu chí duy nhất hoặc cấu trúc lưu phải phản ánh yêu cầu đó.

## 18. Thực hành có lời giải

### Bài 1: đọc và lấy ra

Tập tự nhiên ban đầu là `[3, 6, 9]`. Gọi `first()` rồi `pollLast()`. Hai giá trị trả về và tập cuối là gì?

**Lời giải:** `first` trả 3 và không xóa. `pollLast` trả 9 và xóa nó. Tập cuối là `[3, 6]`.

### Bài 2: hai đầu của khung nhìn đảo

Tập gốc tự nhiên là `[2, 4, 8]`. Tạo `reverse = base.descendingSet()`, rồi gọi `reverse.pollFirst()` và `reverse.pollLast()`.

**Lời giải:** Khung nhìn ban đầu là `[8, 4, 2]`. Lần đầu trả 8 và xóa cuối của nguồn. Lần sau trả 2 và xóa đầu của nguồn. Cả tập gốc lẫn khung nhìn còn cùng thành viên 4.

### Bài 3: iterator có tự xóa không?

Bạn duyệt toàn bộ `descendingIterator` của tập `[1, 2, 3]` bằng `next` và không gọi `remove`. Tập còn những gì?

**Lời giải:** Vẫn còn `[1, 2, 3]`. Lượt đọc đi qua `3, 2, 1` nhưng không xóa. Một bộ duyệt đã hết cần được tạo mới nếu muốn đọc lại.

### Bài 4: xóa sai đường trong lúc duyệt

Trong vòng lặp bằng một iterator, bạn gọi `base.pollFirst()` rồi tiếp tục dùng iterator cũ. Có thể xem đó là an toàn vì poll không dùng một phần tử làm đối số không?

**Lời giải:** Không. Poll vẫn thay đổi thành viên của cùng dữ liệu nền ngoài bộ duyệt. Cần dùng đường cập nhật phù hợp: `iterator.remove` cho lọc trong lượt duyệt, hoặc một vòng poll riêng không giữ bộ duyệt cũ.

### Bài 5: tập nguồn đã có Comparator đảo

Tập nguồn dùng `Comparator.reverseOrder()` và chứa số 1, 5, 9. `source.pollFirst()` lấy số nào? `source.descendingSet().pollFirst()` trên trạng thái ban đầu lấy số nào?

**Lời giải:** Nguồn duyệt `[9, 5, 1]` nên `source.pollFirst` lấy 9. Khung nhìn đảo duyệt `[1, 5, 9]` nên lời gọi trên khung nhìn lấy 1. Hai câu trả lời được xét độc lập trên cùng trạng thái ban đầu.

### Bài 6: null không đồng nghĩa rỗng

Tập cho phép null có thứ tự `[null, a, b]`. Vòng lặp dừng khi `pollFirst() == null` có xử lý hết không?

**Lời giải:** Không. Lần đầu đã xóa thành viên null và điều kiện làm vòng lặp dừng, để lại a và b. Trong ví dụ một luồng, có thể kiểm tra `isEmpty` làm điều kiện dừng như hàm `drainFirst`.

### Bài 7: phạm vi vùng

Tập lớn là `[1, 2, 3, 4, 5]`. Vùng gồm từ 2 có tính đến trước 5. Đảo vùng rồi `pollFirst` có lấy 5 không?

**Lời giải:** Không. Vùng chỉ gồm `[2, 3, 4]`, nên khung nhìn đảo lấy 4. Số 5 vẫn ở ngoài vùng và ở lại trong tập lớn.

### Bài 8: bản sao hay khung nhìn?

Bạn cần nhận dữ liệu theo chiều ngược rồi xóa tùy ý mà không xóa các thành viên của nguồn. Chỉ gọi `descendingSet` đã đủ chưa?

**Lời giải:** Chưa. Đó là khung nhìn dùng chung dữ liệu. Cần bản sao thật với thứ tự thích hợp; vẫn phân biệt độc lập cấu trúc tập với sao chép sâu các đối tượng phần tử.

### Bài 9: trạng thái remove

Sau khi tạo descendingIterator, bạn gọi `remove` ngay mà chưa `next`. Hoặc đã `next` một lần rồi `remove` hai lần liên tiếp. Điều gì sai?

**Lời giải:** Chưa có phần tử do bộ duyệt trả về để xóa ở trường hợp đầu. Trường hợp sau xóa hai lần cho cùng một lần next. Bộ duyệt TreeSet trong bài báo `IllegalStateException` cho các lỗi trạng thái này.

### Bài 10: kiểm tra hai lần đảo

Bạn muốn xác nhận `s.descendingSet().descendingSet()` có cùng thứ tự duyệt như s. Chỉ so sánh bằng `Set.equals` có đủ không?

**Lời giải:** Không để chứng minh trình tự, vì phép bằng nhau của tập không xét thứ tự. Chương trình chuyển hai phía sang danh sách và so sánh trình tự. Cũng không cần yêu cầu hai biến trỏ đến cùng một đối tượng bằng `==`.

## 19. Tự kiểm tra khi áp dụng

Trước mỗi đoạn mã, xác định ba điều: đối tượng nhận lời gọi đang nhìn những thành viên nào, nó dùng chiều nào, và thao tác có xóa thành viên không.

Sau đó kiểm tra trường hợp rỗng và chính sách null. Nếu có bộ duyệt đang hoạt động, xác định đường cập nhật được phép thay vì sửa qua một tham chiếu khác rồi tiếp tục dùng nó.

Với cùng một kho thành viên, `descendingSet` chỉ đổi góc nhìn; `descendingIterator` chỉ chọn chiều của một lượt duyệt. Chỉ các phép thay đổi như `poll` hoặc `iterator.remove` mới thực sự rút thành viên ra trong những ví dụ này.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sorted-set-008">Biên đóng–mở, chính sách null và các view phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

# Biên đóng–mở, chính sách null và các view phạm vi

## 1. Một khoảng có thứ tự cần ba quyết định riêng

Giả sử ứng dụng giữ các mức ưu tiên duy nhất trong một TreeSet. Một số mức chưa xác định được biểu diễn bằng `null`. Ứng dụng cần lấy các mức từ 10 đến trước 30 và cho phép cập nhật trong vùng ấy.

Để thao tác đúng, cần trả lời ba câu hỏi khác nhau:

1. **Thứ tự và null:** comparator có chấp nhận `null` không, và nếu có thì đặt nó trước hay sau các giá trị khác?
2. **Biên đóng–mở:** giá trị tương đương mỗi mốc có được lấy vào khoảng không?
3. **Góc nhìn phạm vi:** kết quả là một view liên kết với dữ liệu gốc hay một container độc lập?

Chỉ biết “TreeSet có sắp xếp” chưa trả lời được các câu hỏi đó. Một comparator cho phép `null` không có nghĩa mọi view của Set đều cho phép thêm `null`. Một khoảng hiện không chứa `null` cũng chưa có nghĩa comparator cấm nó.

Trong bài, **view phạm vi** được gọi là **góc nhìn theo khoảng**: nó truy cập cùng dữ liệu nền nhưng có thêm giới hạn. Đây không phải một danh sách kết quả đã được sao chép.

Các quy tắc được dùng dựa trên [SortedSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/SortedSet.html), [NavigableSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableSet.html) và các phương thức phạm vi của [TreeSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html).

Một cách đọc thống nhất là:

```text
phần tử có thể được so sánh hợp lệ
    + vị trí theo comparator
    + điều kiện của hai biên
    -> thuộc hoặc không thuộc khoảng
```

Sau khi xác định thành viên của khoảng, còn phải quyết định có muốn các thao tác qua nó làm thay đổi tập gốc hay không.

## 2. Đọc biên đóng–mở khi null có thể là một mốc

Bốn dạng khoảng của `NavigableSet` được xác định bằng hai cờ lấy biên. Điều kiện đều được xét theo **comparator của tập**:

| Dạng khoảng | Lời gọi |
|---|---|
| `[a, b]` | `subSet(a, true, b, true)` |
| `[a, b)` | `subSet(a, true, b, false)` |
| `(a, b]` | `subSet(a, false, b, true)` |
| `(a, b)` | `subSet(a, false, b, false)` |

Overload không có cờ dùng `headSet(b)` loại b, `tailSet(a)` lấy a và `subSet(a,b)` chọn `[a,b)`. “Lấy mốc” nghĩa là lấy một phần tử **so sánh bằng 0** với mốc, ngay cả khi mốc không nằm sẵn trong tập. Ví dụ trên `[0,10,20,30,40]`, khoảng `[15,35]` chứa `[20,30]`.

Với `nullsFirst`, mốc `null` đứng ở đầu quan hệ: `subSet(null, true, 30, false)` có thể chứa chính thành viên `null`, còn `subSet(null, false, 30, false)` loại nó. Đối với `nullsLast`, thứ tự giữa `null` và 30 đảo lại; không tự dùng `null` làm ký hiệu “không giới hạn”. Khi cần bỏ một phía giới hạn, hãy chọn `headSet` hoặc `tailSet` phù hợp.

Nếu hai mốc so sánh bằng 0, `[a,a]` có thể chứa một phần tử tương đương, còn ba dạng có ít nhất một đầu mở đều rỗng. Một view `[a,a]` rỗng ban đầu vẫn có thể tiếp nhận phần tử tại mốc sau này. Mốc đầu đứng sau mốc cuối theo comparator thì khoảng không hợp lệ, thường ném `IllegalArgumentException` trên `TreeSet`. Dùng cờ inclusive thay vì tự cộng 1 để nới biên, vì cách cộng không đúng tổng quát cho số, chuỗi hoặc thời gian.

## 3. Chương trình 1: kiểm tra đủ bốn dạng biên

Lưu tệp `RangeBoundaryMatrixDemo.java`:

```java
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public class RangeBoundaryMatrixDemo {
    public static void main(String[] args) {
        NavigableSet<Integer> values =
                new TreeSet<>(List.of(0, 10, 20, 30, 40));

        System.out.println("values = " + values);
        System.out.println("[10, 30) = "
                + values.subSet(10, true, 30, false));
        System.out.println("[10, 30] = "
                + values.subSet(10, true, 30, true));
        System.out.println("(10, 30) = "
                + values.subSet(10, false, 30, false));
        System.out.println("(10, 30] = "
                + values.subSet(10, false, 30, true));
        System.out.println("default subSet = " + values.subSet(10, 30));
        System.out.println("absent boundaries = "
                + values.subSet(15, true, 35, true));
        System.out.println("[20, 20] = "
                + values.subSet(20, true, 20, true));
        System.out.println("[20, 20) = "
                + values.subSet(20, true, 20, false));
        System.out.println("(20, 20] = "
                + values.subSet(20, false, 20, true));
        System.out.println("(20, 20) = "
                + values.subSet(20, false, 20, false));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 RangeBoundaryMatrixDemo.java
java RangeBoundaryMatrixDemo
```

Kết quả của lần chạy kiểm chứng:

```text
values = [0, 10, 20, 30, 40]
[10, 30) = [10, 20]
[10, 30] = [10, 20, 30]
(10, 30) = [20]
(10, 30] = [20, 30]
default subSet = [10, 20]
absent boundaries = [20, 30]
[20, 20] = [20]
[20, 20) = []
(20, 20] = []
(20, 20) = []
```

Các chương trình trong bài dùng ngôn ngữ và API có ở Java 17 trở lên, được biên dịch với `--release 17` và chạy kiểm chứng bằng Azul OpenJDK 24.0.2.

Với hai mốc 10 và 30, chỉ các thành viên đúng tại biên thay đổi khi bật hoặc tắt inclusive. Thành viên nằm hẳn bên trong như 20 vẫn được giữ trong cả bốn dạng.

Phần biên vắng mặt cho thấy đóng hay mở không bắt buộc mốc phải tồn tại. Phần hai mốc cùng là 20 giúp tránh quy tắc quá đơn giản “hai biên bằng nhau thì luôn rỗng”: điều đó đúng với overload mặc định `[20, 20)`, nhưng không đúng với `[20, 20]`.

Khi kiểm thử một truy vấn khoảng, hãy có dữ liệu đúng tại từng biên. Nếu chỉ thử các giá trị nằm sâu bên trong, lỗi đặt nhầm `true` và `false` rất dễ không bị phát hiện.

## 4. Chính sách null bắt đầu từ thứ tự, không bắt đầu từ view

### 4.1. TreeSet theo thứ tự tự nhiên không nhận null

Một TreeSet số nguyên theo thứ tự tự nhiên dùng quan hệ so sánh của Integer. Nó không có quy tắc tự nhiên để đặt `null` trước hoặc sau các số.

Vì vậy, không chỉ `add(null)` là vấn đề. Dùng `null` làm biên của `headSet`, `tailSet` hoặc `subSet` cũng cần một quy tắc so sánh phù hợp. Một Set đang rỗng không biến mốc null thành hợp lệ theo thứ tự tự nhiên.

### 4.2. Comparator có thể định nghĩa vị trí cho null

Hai cấu hình thường dùng:

```java
// Trích đoạn; cần import java.util.Comparator và TreeSet.
TreeSet<Integer> nullFirst =
        new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));

TreeSet<Integer> nullLast =
        new TreeSet<>(Comparator.nullsLast(Comparator.naturalOrder()));
```

Ở đây:

- `nullsFirst` đặt null trước các giá trị khác.
- `nullsLast` đặt null sau các giá trị khác.
- `naturalOrder` so sánh các giá trị không null.
- Hai null được xem là tương đương, nên Set không lưu nhiều lần xuất hiện của null.

Các quy tắc này được mô tả trong [Comparator.nullsFirst và nullsLast của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html#nullsFirst(java.util.Comparator)).

Null không tự có nghĩa bằng 0, chuỗi rỗng hoặc giá trị nhỏ nhất của kiểu số. Nó có vị trí vì comparator đã được chọn rõ ràng.

### 4.3. Khả năng lưu null không quyết định ý nghĩa nghiệp vụ

Nếu null biểu diễn “chưa biết mức ưu tiên”, một Set các mức ưu tiên chỉ giữ một thành viên null. Nó không tự ghi nhận được số lượng công việc chưa có ưu tiên.

Nếu cần nhiều bản ghi khác nhau cùng thiếu một thuộc tính, hãy thiết kế khóa cho từng bản ghi hoặc một cấu trúc lưu phù hợp. Đừng dùng một Set các giá trị rồi mong nó bảo toàn số lần xuất hiện.

Tương tự, cho null đứng đầu không đương nhiên có nghĩa các bản ghi thiếu dữ liệu nên được xử lý trước. Chính sách sắp xếp và chính sách nghiệp vụ cần được thống nhất có chủ đích.

## 5. null làm biên là một vị trí thật, không phải bỏ trống biên

Với `nullsFirst`, thứ tự có thể là:

```text
null < 10 < 20 < 30
```

Với `nullsLast`:

```text
10 < 20 < 30 < null
```

Khi truyền null làm mốc, các phương thức dùng đúng vị trí ấy. Null không có nghĩa “không có biên dưới” hoặc “không có biên trên”.

Giả sử cả hai tập đều chứa đủ `null, 10, 20, 30`:

| Lời gọi | nullsFirst | nullsLast |
|---|---|---|
| `headSet(null, false)` | `[]` | `[10, 20, 30]` |
| `headSet(null, true)` | `[null]` | `[10, 20, 30, null]` |
| `tailSet(null, false)` | `[10, 20, 30]` | `[]` |
| `tailSet(null, true)` | `[null, 10, 20, 30]` | `[null]` |

Bảng này phụ thuộc việc tập đang có thành viên null. Nếu chưa thêm null, các khoảng có khả năng lấy nó không tự tạo ra nó.

### 5.1. Cách hiểu “không giới hạn” phải tách khỏi null

Nếu chỉ cần giới hạn trên, dùng `headSet`. Nếu chỉ cần giới hạn dưới, dùng `tailSet`. Nếu cần toàn bộ tập, dùng tập gốc hoặc một góc nhìn phù hợp của nó.

Không viết `subSet(null, true, end, false)` như một công thức tổng quát cho “mọi giá trị trước end”. Với thứ tự tự nhiên nó thất bại; với nullsFirst nó bắt đầu từ vị trí null; với nullsLast nó có thể đảo thứ tự biên.

Một API nghiệp vụ có thể dùng null để biểu diễn “người gọi không truyền mốc”, nhưng lớp xử lý phải chuyển ý nghĩa ấy thành lời gọi collection đúng. Không chuyển thẳng null xuống TreeSet rồi giả định collection hiểu cùng quy ước.

### 5.2. Khoảng có thể chủ động lấy hoặc loại null

Với nullsFirst:

```text
[null, 30)   -> null, 10, 20
(null, 30]   -> 10, 20, 30
```

Với nullsLast:

```text
[20, null]   -> 20, 30, null
[20, null)   -> 20, 30
```

Trong cả hai trường hợp, null là mốc đang tham gia so sánh. Cờ inclusive quyết định có giữ lớp tương đương tại mốc ấy hay không.

## 6. Chương trình 2: null ở hai đầu và sự mơ hồ của kết quả null

Lưu tệp `NullableRangeEndpointsDemo.java`:

```java
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public class NullableRangeEndpointsDemo {
    public static void main(String[] args) {
        NavigableSet<Integer> nullFirst =
                new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));
        nullFirst.addAll(List.of(10, 20, 30));
        nullFirst.add(null);

        System.out.println("nullsFirst = " + nullFirst);
        System.out.println("head null exclusive = "
                + nullFirst.headSet(null, false));
        System.out.println("head null inclusive = "
                + nullFirst.headSet(null, true));
        System.out.println("tail null exclusive = "
                + nullFirst.tailSet(null, false));
        System.out.println("tail null inclusive = "
                + nullFirst.tailSet(null, true));
        System.out.println("[null, 30) = "
                + nullFirst.subSet(null, true, 30, false));
        System.out.println("nullsFirst empty = " + nullFirst.isEmpty());
        System.out.println("nullsFirst first = " + nullFirst.first());

        NavigableSet<Integer> nullLast =
                new TreeSet<>(Comparator.nullsLast(Comparator.naturalOrder()));
        nullLast.addAll(List.of(10, 20, 30));
        nullLast.add(null);

        System.out.println("nullsLast = " + nullLast);
        System.out.println("head null exclusive = "
                + nullLast.headSet(null, false));
        System.out.println("head null inclusive = "
                + nullLast.headSet(null, true));
        System.out.println("tail null exclusive = "
                + nullLast.tailSet(null, false));
        System.out.println("tail null inclusive = "
                + nullLast.tailSet(null, true));
        System.out.println("[20, null] = "
                + nullLast.subSet(20, true, null, true));

        NavigableSet<Integer> after30 = nullLast.tailSet(30, false);
        NavigableSet<Integer> afterNull = nullLast.tailSet(null, false);
        System.out.println("higher 30 = " + nullLast.higher(30));
        System.out.println("after 30 empty = " + after30.isEmpty());
        System.out.println("after 30 first = " + after30.first());
        System.out.println("higher null = " + nullLast.higher(null));
        System.out.println("after null empty = " + afterNull.isEmpty());

        System.out.println("poll first from nullsFirst = "
                + nullFirst.pollFirst());
        System.out.println("after poll first = " + nullFirst);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 NullableRangeEndpointsDemo.java
java NullableRangeEndpointsDemo
```

Kết quả của lần chạy kiểm chứng:

```text
nullsFirst = [null, 10, 20, 30]
head null exclusive = []
head null inclusive = [null]
tail null exclusive = [10, 20, 30]
tail null inclusive = [null, 10, 20, 30]
[null, 30) = [null, 10, 20]
nullsFirst empty = false
nullsFirst first = null
nullsLast = [10, 20, 30, null]
head null exclusive = [10, 20, 30]
head null inclusive = [10, 20, 30, null]
tail null exclusive = []
tail null inclusive = [null]
[20, null] = [20, 30, null]
higher 30 = null
after 30 empty = false
after 30 first = null
higher null = null
after null empty = true
poll first from nullsFirst = null
after poll first = [10, 20, 30]
```

### 6.1. first trả về null không đồng nghĩa tập rỗng

Trong tập nullsFirst, `isEmpty()` là false nhưng `first()` trả về null vì null là thành viên đứng đầu.

Một tập rỗng thực sự làm `first()` ném `NoSuchElementException`. Do đó, kiểm tra “first khác null” không phải cách tổng quát để kiểm tra tập có dữ liệu.

### 6.2. higher có thể dùng null cho hai ý nghĩa

Trong tập nullsLast `[10, 20, 30, null]`:

- `higher(30)` trả về null là **thành viên thật** đứng sau 30.
- `higher(null)` trả về null vì **không có thành viên nào** đứng sau null.

Cùng một giá trị trả về nhưng ý nghĩa khác nhau. Chương trình dùng các view tương ứng để cho thấy sự khác biệt:

```text
sau 30:    view [null], không rỗng
sau null:  view [], rỗng
```

Các phương thức điều hướng trả null khi không tìm thấy được mô tả trong [NavigableSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableSet.html). Khi cho phép chính null là thành viên, người gọi cần một cách riêng để biểu đạt có hoặc không có kết quả.

### 6.3. pollFirst có thể gỡ một thành viên null thật

Lời gọi `pollFirst` trên tập nullsFirst trả null, nhưng sau đó tập mất thành viên null và còn `[10, 20, 30]`.

Đây không phải phép gọi trên một tập rỗng. Vì thế, mẫu lặp sau có thể sai khi collection cho phép null:

```java
// Mẫu dễ sai với Set có thành viên null.
Integer value;
while ((value = values.pollFirst()) != null) {
    process(value);
}
```

Đây là trích đoạn minh họa lỗi, không phải chương trình độc lập. Nếu null đứng đầu, vòng lặp có thể gỡ nó rồi dừng ngay, bỏ lại các giá trị khác.

Trong mã đơn luồng cần lấy hết, có thể phân biệt điều kiện rỗng với giá trị được lấy. Với việc chỉ đọc, iterator cũng tách riêng `hasNext()` và `next()`:

```java
// Trích đoạn đọc; values là một Set cho phép null.
Iterator<Integer> iterator = values.iterator();
while (iterator.hasNext()) {
    Integer value = iterator.next();
    processNullable(value);
}
```

`processNullable` tượng trưng cho xử lý đã được thiết kế chấp nhận null. Không dùng tự động mở hộp `Integer` thành `int` trước khi xử lý trường hợp null.

### 6.4. Optional không giữ được một null có mặt

`Optional.ofNullable(result)` biến null thành Optional rỗng. Nếu result có thể là một thành viên null thật, phép chuyển đó làm mất thông tin “có kết quả”.

Khi yêu cầu phải phân biệt, có thể dùng một kiểu kết quả có cờ tồn tại riêng, hoặc thiết kế mô hình không dùng null làm giá trị thành viên. Quyết định này nên nằm ở giao diện sử dụng, không được suy đoán từ một giá trị null sau khi đã mất ngữ cảnh.

Các ví dụ trên là mã đơn luồng. Việc kiểm tra rỗng rồi đọc hoặc gỡ là nhiều thao tác, không tự có tính nguyên tử trong môi trường nhiều luồng.

## 7. View phạm vi bổ sung giới hạn lên chính sách của tập gốc

Một comparator chấp nhận null mới giải quyết được khả năng so sánh. Một view còn có điều kiện phạm vi của riêng nó.

Ví dụ:

```text
tập gốc theo nullsFirst:
    [null, 10, 20, 30, 40]

view nonNull:
    tailSet(null, false)
    -> [10, 20, 30, 40]

view band bên trong:
    subSet(10, true, 30, false)
    -> [10, 20]
```

Tập gốc cho phép null. Nhưng nonNull loại vị trí null bằng biên mở, nên thêm null qua nonNull không hợp lệ. Tương tự, thêm 30 qua band vi phạm biên trên mở.

Cần tách hai trường hợp lỗi trong các ví dụ này:

| Tình huống | Vấn đề |
|---|---|
| Natural-order TreeSet nhận null | Không có phép so sánh null hợp lệ |
| View nullsFirst đã loại null mà bị thêm null | Giá trị so sánh được nhưng nằm ngoài phạm vi được phép |

Trường hợp thứ nhất thường ném `NullPointerException`; trường hợp thứ hai trên view TreeSet minh họa ném `IllegalArgumentException`.

Đừng thấy IllegalArgumentException rồi cho rằng comparator không biết so sánh null. Cũng đừng thấy comparator hỗ trợ null rồi suy ra mọi lối ghi vào mọi view đều hợp lệ.

### 7.1. Giới hạn liên quan tới phạm vi tương lai, không chỉ dữ liệu hiện có

Một view có giới hạn ngay cả khi không có phần tử. Nó không đơn thuần là danh sách các tham chiếu đã lấy ra ở thời điểm tạo.

Khi thêm một giá trị mới, cấu trúc kiểm tra giá trị ấy có thuộc miền view cho phép hay không. Khi tập gốc thêm một giá trị thuộc khoảng, view thấy nó mà không cần tạo lại.

Ngược lại, một giá trị ngoài khoảng vẫn không thể thêm qua view dù hiện tại tập gốc chưa có giá trị đó.

### 7.2. Góc nhìn con không được mở rộng cha

Một góc nhìn con phải nằm trong giới hạn mà cha cho phép. Khi cha có biên mở, việc đặt mốc con đúng tại biên ấy còn phụ thuộc cờ inclusive.

Với `nonNull = root.tailSet(null, false)`:

- `nonNull.headSet(null, false)` có thể biểu đạt một khoảng rỗng hợp lệ.
- `nonNull.headSet(null, true)` cố bao gồm null mà cha đã loại trừ, nên bị từ chối trong ví dụ.

Không lấy tập các phần tử hiện có để suy luận rằng cả hai yêu cầu “đều rỗng thì chắc giống nhau”. API còn bảo vệ phạm vi có thể nhận dữ liệu về sau.

Cũng không mặc định một view con tự động lấy giao với bất kỳ biên nào được đưa vào. Mốc vượt giới hạn cha có thể gây IllegalArgumentException thay vì trả về phần giao mong muốn.

## 8. Chương trình 3: null hợp lệ ở gốc nhưng bị cấm bởi view

Lưu tệp `NullableRangeViewDemo.java`:

```java
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public class NullableRangeViewDemo {
    public static void main(String[] args) {
        NavigableSet<Integer> root =
                new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));
        root.addAll(List.of(10, 20, 30, 40));
        root.add(null);

        NavigableSet<Integer> nonNull = root.tailSet(null, false);
        NavigableSet<Integer> band = nonNull.subSet(10, true, 30, false);
        System.out.println("root = " + root);
        System.out.println("nonNull view = " + nonNull);
        System.out.println("band = " + band);
        band.add(15);
        System.out.println("root after band add 15 = " + root);

        try {
            nonNull.add(null);
        } catch (IllegalArgumentException ex) {
            System.out.println("view add null = "
                    + ex.getClass().getSimpleName());
        }
        try {
            band.add(30);
        } catch (IllegalArgumentException ex) {
            System.out.println("band add upper endpoint = "
                    + ex.getClass().getSimpleName());
        }

        System.out.println("empty child at excluded null = "
                + nonNull.headSet(null, false));
        try {
            nonNull.headSet(null, true);
        } catch (IllegalArgumentException ex) {
            System.out.println("child includes excluded null = "
                    + ex.getClass().getSimpleName());
        }

        TreeSet<Integer> snapshot = new TreeSet<>(band);
        System.out.println("snapshot initial = " + snapshot);
        System.out.println("snapshot add null = " + snapshot.add(null));
        System.out.println("snapshot after add null = " + snapshot);

        root.remove(10);
        System.out.println("band after root remove 10 = " + band);
        System.out.println("snapshot after root remove = " + snapshot);
        band.clear();
        System.out.println("root after band clear = " + root);
        System.out.println("snapshot after band clear = " + snapshot);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 NullableRangeViewDemo.java
java NullableRangeViewDemo
```

Kết quả của lần chạy kiểm chứng:

```text
root = [null, 10, 20, 30, 40]
nonNull view = [10, 20, 30, 40]
band = [10, 20]
root after band add 15 = [null, 10, 15, 20, 30, 40]
view add null = IllegalArgumentException
band add upper endpoint = IllegalArgumentException
empty child at excluded null = []
child includes excluded null = IllegalArgumentException
snapshot initial = [10, 15, 20]
snapshot add null = true
snapshot after add null = [null, 10, 15, 20]
band after root remove 10 = [15, 20]
snapshot after root remove = [null, 10, 15, 20]
root after band clear = [null, 30, 40]
snapshot after band clear = [null, 10, 15, 20]
```

### 8.1. View và tập gốc thay đổi qua lại

`band.add(15)` làm tập gốc có thêm 15. Khi tập gốc gỡ 10, band chỉ còn `[15, 20]`.

`band.clear()` gỡ các thành viên hiện nằm trong band khỏi tập gốc. Kết quả gốc là `[null, 30, 40]`: null và 30 vẫn còn vì đều không thuộc band đang xét.

Clear không chỉ “xóa dữ liệu tạm của kết quả”. View liên kết cùng dữ liệu nền, nên quyền thay đổi qua nó là một quyền thay đổi dữ liệu gốc có giới hạn.

### 8.2. Chính sách null và hàng rào khoảng không phải một thứ

Root chứa null và comparator chấp nhận nó. Nhưng `nonNull.add(null)` bị từ chối vì hàng rào dưới đang mở tại null.

`band.add(30)` cũng bị từ chối vì hàng rào trên mở tại 30. Cả hai lỗi đều là vi phạm phạm vi, dù các giá trị có thể được so sánh hợp lệ.

### 8.3. Bản sao không mang theo hàng rào của view

Chương trình tạo:

```java
TreeSet<Integer> snapshot = new TreeSet<>(band);
```

Bản sao có các phần tử hiện có và giữ comparator của band. Nhưng nó là một TreeSet đầy đủ mới, không còn hai mốc giới hạn 10 và 30 của band.

Vì comparator được giữ vẫn là nullsFirst, `snapshot.add(null)` thành công. Điều này không mâu thuẫn với việc band cấm null: snapshot giữ thứ tự, không giữ giới hạn phạm vi.

Sau đó, root thay đổi hoặc band bị clear không làm snapshot mất dữ liệu. Đây là sự độc lập ở cấp container.

## 9. Sao chép dữ liệu, giữ comparator và giữ phạm vi là ba yêu cầu khác nhau

Khi chuyển kết quả một khoảng sang nơi khác, hãy xác định riêng:

| Yêu cầu | Cần bảo vệ |
|---|---|
| Tách container khỏi nguồn | Tạo một collection mới |
| Giữ nguyên thứ tự và vị trí null | Giữ comparator phù hợp |
| Giữ giới hạn đóng–mở khi tiếp tục cho sửa | Tạo lại view có biên trên container mới hoặc đóng gói quy tắc |
| Không cho sửa qua tham chiếu trả về | Dùng giao diện và wrapper phù hợp |
| Tách cả dữ liệu phần tử khả biến | Cần chính sách sao chép hoặc giá trị bất biến |

Một constructor không tự hoàn thành mọi hàng trong bảng.

### 9.1. Giữ comparator khi sao chép SortedSet

Với biến có kiểu SortedSet hoặc NavigableSet, `new TreeSet<>(source)` chọn constructor SortedSet và giữ cùng thứ tự.

Nếu nguồn bị nhìn qua kiểu Collection, overload nhận Collection dùng thứ tự tự nhiên. Khi dữ liệu có null hoặc không có Comparable phù hợp, cách sao chép ấy có thể thất bại.

Có thể truyền rõ comparator:

```java
// Trích đoạn; source là một SortedSet<Integer>.
TreeSet<Integer> copy = new TreeSet<>(source.comparator());
copy.addAll(source);
```

Không chỉ hỏi “đã copy chưa”; còn phải hỏi “copy theo thứ tự nào”.

### 9.2. Muốn giữ phạm vi thì xác lập lại phạm vi

Nếu cần một bản riêng nhưng vẫn giới hạn sửa trong `[10, 30)`:

```java
// Trích đoạn; band là một SortedSet<Integer> theo comparator phù hợp.
TreeSet<Integer> detachedRoot = new TreeSet<>(band);
NavigableSet<Integer> detachedBand =
        detachedRoot.subSet(10, true, 30, false);
```

`detachedBand` liên kết với container mới, không phải root cũ. Nó có lại hai biên nên vẫn không cho thêm ngoài khoảng.

Đây là một view riêng trên nền riêng, không phải khẳng định constructor TreeSet đã nhớ giới hạn của nguồn. Nếu không muốn bên gọi vượt hàng rào qua container nền, không cung cấp trực tiếp detachedRoot cho bên ấy.

### 9.3. Chỉ đọc và bản chụp

Bọc một view bằng wrapper chỉ đọc không tự chụp dữ liệu; các thay đổi qua nguồn vẫn có thể được quan sát.

Muốn một bản chụp chỉ đọc, tạo bản sao trước rồi bọc. Khi nguồn chấp nhận null, phải chọn phép sao chép cũng chấp nhận dữ liệu và giữ thứ tự phù hợp.

Nếu phần tử là đối tượng khả biến, sao chép Set vẫn có thể chia sẻ cùng đối tượng. Sửa trường tham gia comparator có thể làm cả hai cấu trúc không còn được tổ chức đúng; một bản sao container không thay thế thiết kế khóa ổn định.

## 10. Chuyển sang API khác có thể làm đổi chính sách null

Không phải mọi Set đều có cùng quy tắc.

- TreeSet theo thứ tự tự nhiên từ chối null.
- TreeSet có comparator phù hợp có thể chấp nhận null.
- Một view có thể loại null bằng phạm vi dù comparator vẫn chấp nhận.
- `Set.copyOf` không chấp nhận collection chứa thành viên null.
- Wrapper không cho sửa và comparator là hai cơ chế khác nhau.

Theo [Set.copyOf Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#copyOf(java.util.Collection)), null trong collection đầu vào gây NullPointerException. Đồng thời, kết quả có kiểu Set nói chung; nó không thay thế một SortedSet có cùng comparator và cùng các API khoảng.

Vì thế, không dùng `Set.copyOf(nullableView)` như một phép mặc định để tạo “bản TreeSet bất biến”. Cần quyết định giữ hay loại null một cách có ý thức, rồi chọn kiểu và cách sao chép phù hợp.

Trong các ví dụ, dữ liệu không null được thêm trước qua `List.of(...)`, rồi null được thêm riêng qua TreeSet có comparator hỗ trợ. Không đưa null vào một factory có chính sách cấm null rồi tưởng rằng lỗi đến từ TreeSet nhận dữ liệu.

Nếu null không có giá trị nghiệp vụ, chính sách đơn giản thường là từ chối ở ranh giới đầu vào. Nếu null có ý nghĩa cần giữ, hãy bảo toàn ý nghĩa ấy qua toàn bộ chuỗi xử lý, thay vì để một phép chuyển collection âm thầm làm đổi yêu cầu.

## 11. Chương trình 4: kiểm tra nơi chính sách thay đổi

Lưu tệp `NullPolicyTransferDemo.java`:

```java
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

public class NullPolicyTransferDemo {
    public static void main(String[] args) {
        NavigableSet<Integer> natural = new TreeSet<>();
        try {
            natural.add(null);
        } catch (NullPointerException ex) {
            System.out.println("natural add null = "
                    + ex.getClass().getSimpleName());
        }
        try {
            natural.headSet(null, true);
        } catch (NullPointerException ex) {
            System.out.println("natural null endpoint = "
                    + ex.getClass().getSimpleName());
        }

        NavigableSet<Integer> nullable =
                new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));
        nullable.addAll(List.of(10, 20));
        nullable.add(null);

        try {
            Set.copyOf(nullable);
        } catch (NullPointerException ex) {
            System.out.println("Set.copyOf nullable = "
                    + ex.getClass().getSimpleName());
        }

        NavigableSet<Integer> snapshot =
                Collections.unmodifiableNavigableSet(new TreeSet<>(nullable));
        nullable.add(15);
        System.out.println("source after add 15 = " + nullable);
        System.out.println("read-only snapshot = " + snapshot);
        try {
            snapshot.add(12);
        } catch (UnsupportedOperationException ex) {
            System.out.println("snapshot add = "
                    + ex.getClass().getSimpleName());
        }

        NavigableSet<String> collapsed =
                new TreeSet<>(Comparator.nullsFirst(null));
        System.out.println("collapsed add A = " + collapsed.add("A"));
        System.out.println("collapsed add B = " + collapsed.add("B"));
        System.out.println("collapsed contains Z = " + collapsed.contains("Z"));
        collapsed.add(null);
        System.out.println("collapsed members = " + collapsed);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 NullPolicyTransferDemo.java
java NullPolicyTransferDemo
```

Kết quả của lần chạy kiểm chứng:

```text
natural add null = NullPointerException
natural null endpoint = NullPointerException
Set.copyOf nullable = NullPointerException
source after add 15 = [null, 10, 15, 20]
read-only snapshot = [null, 10, 20]
snapshot add = UnsupportedOperationException
collapsed add A = true
collapsed add B = false
collapsed contains Z = true
collapsed members = [null, A]
```

### 11.1. Natural order không nhận null, kể cả làm mốc

Hai lời gọi đầu đều ném NullPointerException: một lời gọi thêm null, một lời gọi dùng null làm biên. Điều đó xác nhận rằng null phải so sánh được trước khi bàn tới đóng hoặc mở.

### 11.2. Set.copyOf không tiếp nhận thành viên null

Nguồn nullable có comparator phù hợp nên chứa null hợp lệ. Tuy nhiên, chính sách của Set.copyOf từ chối dữ liệu ấy. Collection đích có hợp đồng riêng.

Bản chụp chỉ đọc trong chương trình được tạo từ `new TreeSet<>(nullable)` rồi mới bọc. Nó giữ null và thứ tự, không thấy giá trị 15 được thêm vào nguồn sau đó, đồng thời không cho thêm 12 qua tham chiếu snapshot.

### 11.3. Comparator.nullsFirst(null) không có nghĩa natural order

Phần cuối là một cấu hình cố ý dễ nhầm:

```java
new TreeSet<>(Comparator.nullsFirst(null))
```

Theo hợp đồng Comparator, khi comparator bên trong nullsFirst là null, các giá trị **không null đều được xem là tương đương**. Nó không tự chọn thứ tự tự nhiên cho chúng.

Vì vậy, sau khi thêm `"A"`:

- Thêm `"B"` trả về false vì comparator xem A và B bằng nhau.
- Hỏi `contains("Z")` trả về true theo cùng quan hệ so sánh.
- Thêm null tạo thêm một thành viên riêng ở đầu.

Đây là một thứ tự không phù hợp với equals của String và không đáp ứng cách hiểu thông thường về duy nhất theo equals của Set. Ví dụ giúp thấy cấu hình comparator có thể thay đổi cả tính duy nhất, không chỉ vị trí null.

Nếu muốn natural order cho các giá trị không null, hãy truyền `Comparator.naturalOrder()` rõ ràng vào nullsFirst hoặc nullsLast.

## 12. Những vị trí null khác nhau trong cấu hình không cùng ý nghĩa

Có ít nhất ba trường hợp dễ bị trộn lẫn:

| Nơi xuất hiện null | Ý nghĩa |
|---|---|
| Comparator tường minh của TreeSet là null | Dùng thứ tự tự nhiên |
| Đối số comparator của `Comparator.nullsFirst(null)` là null | Mọi giá trị không null được xem là tương đương |
| Mốc của `headSet(null, ...)` hoặc `tailSet(null, ...)` | Một giá trị biên phải được so sánh theo thứ tự đã chọn |

Trong Java, lời gọi constructor bằng null không kèm thông tin kiểu còn có thể vướng chọn overload. Khi cần minh họa trường hợp đầu, dùng một biến có kiểu rõ ràng:

```java
// Trích đoạn: comparator null ở constructor nghĩa là natural order.
Comparator<Integer> noExplicitComparator = null;
TreeSet<Integer> values = new TreeSet<>(noExplicitComparator);
```

TreeSet này vẫn không có chính sách chấp nhận phần tử null. Nó khác với TreeSet dùng một đối tượng comparator nullsFirst thật.

### 12.1. Đảo thứ tự có thể đảo cả vị trí null

Hai ý định sau không giống nhau:

```java
// Trích đoạn minh họa hai quan hệ thứ tự khác nhau.
Comparator<Integer> nullFirstDescending =
        Comparator.nullsFirst(Comparator.reverseOrder());

Comparator<Integer> reverseEverything =
        Comparator.nullsFirst(Comparator.<Integer>naturalOrder()).reversed();
```

Trường hợp thứ nhất giữ null ở đầu, nhưng xếp các số giảm dần.

Trường hợp thứ hai đảo toàn bộ quan hệ đã có, nên null chuyển về cuối. Khi tạo khoảng trên một thứ tự đảo, cả vị trí các mốc lẫn ý nghĩa trước/sau đều phải được đọc lại theo thứ tự đó.

Đừng dùng tên biến “nullFirst” như bằng chứng sau khi comparator đã bị `reversed()`; hãy xem chính quan hệ được tạo ra.

### 12.2. null của phần tử khác với null của một thuộc tính

Giả sử TreeSet chứa Task và sắp theo thuộc tính priority có thể null:

```java
// Trích đoạn mô hình; giả sử name không null.
record Task(String name, Integer priority) {
}

Comparator<Task> byPriorityThenName =
        Comparator.comparing(
                Task::priority,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Task::name);
```

Comparator bên trong xử lý null của **priority**, không phải tự cho phép đối tượng Task cũng null. Nếu chính phần tử Task có thể null, cần một chính sách ở lớp so sánh bên ngoài nữa.

Ngược lại, chỉ bọc comparator toàn Task bằng nullsFirst chưa tự sửa một comparator bên trong đang mở hộp priority null thành int.

Hãy xác định null nằm ở cấp nào: phần tử, trường dữ liệu, comparator cấu hình hay mốc truy vấn. Đó là bốn vai trò khác nhau, cần được xử lý ở đúng ranh giới.

## 13. Vòng đời view và quyền cập nhật dữ liệu nền

Các view phạm vi của TreeSet được hỗ trợ bởi tập gốc. Điều này làm chúng hữu ích cho thao tác có giới hạn, đồng thời đặt ra một số giả định cần rõ.

### 13.1. Đọc hiện tại hay chụp tại một thời điểm?

View thể hiện dữ liệu phù hợp đang có trên nền. Thêm hoặc xóa ở nền có thể làm view thay đổi nội dung.

Nếu cần báo cáo cố định, kết quả kiểm tra để xử lý về sau hoặc dữ liệu nhỏ giữ lâu, cân nhắc bản sao. Một view nhỏ vẫn có thể giữ tham chiếu tới nền dữ liệu lớn hơn.

Không coi “tạo view không sửa nguồn” là tương đương với “dùng view sẽ không sửa nguồn”. Việc tạo chỉ đọc cấu hình; các thao tác như add, remove, clear sau đó có thể cập nhật nền.

### 13.2. Ràng buộc phạm vi không thay chính sách nghiệp vụ

Một view `[10, 30)` chỉ kiểm tra điều kiện thứ tự, không biết 10–29 có phải mức ưu tiên hợp lệ về nghiệp vụ hay không.

Nếu comparator đặt null ở một đầu và view loại nó, đó là một hàng rào theo cấu trúc. Những quy tắc khác như chỉ nhận mức chia hết cho 5 cần kiểm tra riêng; view không phải một bộ lọc tùy ý cho mọi điều kiện.

### 13.3. Thay đổi dữ liệu dùng so sánh vẫn là lỗi

Biên được kiểm tra khi collection thực hiện thao tác, không phải một cơ chế theo dõi mọi setter của đối tượng đã lưu.

Nếu phần tử có thuộc tính priority khả biến và bị sửa trực tiếp sau khi thêm, vị trí trong cây và việc thuộc khoảng có thể không còn đúng. Cần phần tử bất biến, khóa so sánh ổn định hoặc một luồng gỡ và thêm lại có kiểm soát.

Cũng không sửa cấu hình comparator đang được một cây có dữ liệu sử dụng rồi mong cây tự tổ chức lại.

### 13.4. Iterator và nhiều thao tác

Iterator không phải ảnh chụp. Thay đổi cấu trúc qua tập gốc hoặc view khác trong lúc duyệt có thể gây ConcurrentModificationException. Cơ chế phát hiện sửa đổi không phải cách đồng bộ nhiều luồng.

Một chuỗi kiểm tra rồi cập nhật cũng không tự nguyên tử. Khi thêm nhiều giá trị vào view, một giá trị ngoài phạm vi có thể gây lỗi sau khi các giá trị trước đã được thêm. Nếu cần tất cả thành công hoặc không thay đổi gì, phải có quy trình riêng.

Những giả định đó độc lập với việc comparator xử lý null đúng. Giải quyết chính sách null không tự giải quyết tính nhất quán của một chuỗi cập nhật.

## 14. Kiểm tra một khoảng có null theo từng lớp

Một cách kiểm tra có hệ thống là đi theo thứ tự sau.

### Bước 1: xác lập quan hệ thứ tự

Ghi rõ:

```text
null bị từ chối
hoặc
null đứng đầu
hoặc
null đứng cuối
```

Sau đó ghi thứ tự giữa các giá trị không null. Nếu có nhiều tiêu chí, xác định khi nào kết quả so sánh bằng 0.

### Bước 2: xác lập hai biên và cờ

Nêu rõ `[a, b)`, `[a, b]`, `(a, b]` hay `(a, b)`. Kiểm tra a không đứng sau b theo comparator.

Nếu mốc là null, đọc nó như một mốc thật. Nếu nghiệp vụ muốn không có biên, chọn lời gọi headSet/tailSet phù hợp thay vì tự gán ý nghĩa đặc biệt cho null.

### Bước 3: xác lập phạm vi cha

Nếu đang tạo view từ một view, kiểm tra các mốc và inclusive có nằm trong quyền mà cha cho phép không. Đặc biệt kiểm tra mốc đúng tại một biên mở của cha.

### Bước 4: xác lập quyền thay đổi

Phân biệt:

- View liên kết và có thể sửa.
- View liên kết nhưng bị bọc chỉ đọc.
- Bản sao riêng có thể sửa.
- Bản sao riêng chỉ đọc.
- Bản sao riêng đã được đặt lại giới hạn phạm vi.

Những trường hợp này có thể in ra cùng nội dung ở thời điểm đầu nhưng khác nhau khi có cập nhật.

### Bước 5: kiểm tra dữ liệu đại diện

Ít nhất nên có:

- Tập rỗng.
- Tập chỉ có null nếu chính sách cho phép.
- Tập có null và giá trị thường.
- Giá trị đúng từng biên.
- Giá trị nằm trong và nằm ngoài khoảng.
- Mốc không tồn tại trong tập.
- Hai mốc tương đương.
- Thứ tự đảo nếu ứng dụng dùng comparator giảm dần.
- Thay đổi qua nguồn và qua view.
- Sao chép rồi thử thêm một giá trị từng bị view nguồn loại.

Không kiểm tra bằng một dãy chỉ có các số ở giữa khoảng. Dãy ấy không bộc lộ được lỗi biên hoặc lỗi null.

## 15. Các lỗi thường gặp và cách nhận ra

| Cách làm | Điều bị bỏ sót | Cách xử lý |
|---|---|---|
| Truyền null để biểu diễn không giới hạn | Collection có thể coi đó là mốc thật hoặc không so sánh được | Chuyển yêu cầu nghiệp vụ thành đúng overload |
| Comparator nhận null nên mọi view nhận null | View có hàng rào riêng | Kiểm tra vị trí null so với biên |
| Đổi inclusive để xử lý một null không so sánh được | Đóng/mở không tạo ra quan hệ thứ tự | Thiết kế comparator trước |
| Kiểm tra first khác null để biết có dữ liệu | Thành viên đầu có thể là null | Dùng trạng thái rỗng hoặc cách biểu đạt có kết quả riêng |
| Lặp pollFirst đến khi trả null | Có thể dừng sau khi gỡ một null thật | Tách điều kiện còn phần tử khỏi giá trị phần tử |
| Dùng Optional.ofNullable mà vẫn muốn giữ null có mặt | Optional rỗng làm mất thông tin | Đổi kiểu kết quả hoặc mô hình giá trị |
| Dùng nullsFirst(null) để lấy natural order | Mọi giá trị không null bị coi tương đương | Truyền naturalOrder rõ ràng |
| Copy một view rồi cho rằng vẫn còn giới hạn | Constructor giữ dữ liệu và thứ tự, không giữ hàng rào | Tạo lại view trên bản sao nếu cần |
| Set.copyOf để giữ nguyên một SortedSet nullable | Null bị từ chối và kiểu thứ tự không được giữ | Chọn bản sao có chính sách phù hợp |
| Chỉ đọc nghĩa là dữ liệu cố định | Nguồn của wrapper vẫn có thể đổi | Sao chép khi cần bản chụp |
| Đảo comparator nhưng cho rằng null vẫn đầu | Đảo toàn quan hệ cũng đảo vị trí null | Kiểm tra thứ tự cuối cùng |
| Bọc nullsFirst bên ngoài để chữa mọi null trong thuộc tính | Chỉ xử lý null ở cấp phần tử | Đặt chính sách ở đúng cấp dữ liệu |

Khi gặp ngoại lệ, hãy xác định lỗi nằm ở lớp nào: không so sánh được, sai thứ tự hai mốc, vượt biên cha, thêm ngoài khoảng hay cập nhật qua một wrapper không cho sửa. Không thử đổi ngẫu nhiên các cờ cho đến khi hết lỗi.

## 16. Bài tập áp dụng

### Bài 1: bốn dạng khoảng

Với `[5, 10, 15, 20]` theo thứ tự tăng dần, dự đoán `[10, 20)`, `(10, 20]` và `[10, 10]`.

**Gợi ý trả lời:** lần lượt là `[10, 15]`, `[15, 20]` và `[10]`.

### Bài 2: null không phải không giới hạn

Tập dùng nullsLast, chứa `[5, 10, null]`. `headSet(null, false)` và `tailSet(null, true)` chứa gì?

**Gợi ý trả lời:** lần lượt là `[5, 10]` và `[null]`. Null là mốc cuối của quan hệ, không phải ký hiệu bỏ trống biên.

### Bài 3: null có mặt nhưng khoảng không rỗng

Tập dùng nullsFirst và chỉ có một thành viên null. `first()` trả gì, `isEmpty()` trả gì?

**Gợi ý trả lời:** first trả null là thành viên thật; isEmpty trả false.

### Bài 4: cùng trả null, khác ý nghĩa

Tập nullsLast là `[5, 10, null]`. Giải thích `higher(10)` và `higher(null)`.

**Gợi ý trả lời:** lời gọi đầu tìm được thành viên null; lời gọi sau không tìm được thành viên nào. Nếu cần phân biệt, phải giữ trạng thái tồn tại riêng.

### Bài 5: root có null, view cấm null

Tạo view `root.tailSet(null, false)` từ root dùng nullsFirst. Thêm null qua view có hợp lệ không?

**Gợi ý trả lời:** không. Null so sánh được nhưng bị biên dưới mở loại trừ; trên TreeSet view ví dụ, thao tác gây IllegalArgumentException.

### Bài 6: sao chép một view không chứa null

View vừa nêu không có null. Tạo một TreeSet mới bằng constructor nhận SortedSet, rồi thêm null vào bản sao.

Có thể thành công không?

**Gợi ý trả lời:** có, vì comparator nullsFirst được giữ nhưng giới hạn tailSet không được mang theo. Muốn giữ giới hạn phải tạo lại view trên container mới.

### Bài 7: comparator bên trong bị bỏ trống

TreeSet dùng `Comparator.nullsFirst(null)`, đã thêm chuỗi A. Tại sao thêm B có thể trả false?

**Gợi ý trả lời:** comparator bên trong null nghĩa mọi chuỗi không null được coi tương đương; không có natural order tự động được chèn vào.

### Bài 8: bản chụp chỉ đọc

Nguồn có null hợp lệ. Cần trả một bản riêng giữ thứ tự và không cho thêm/xóa qua tham chiếu trả về. Có nên mặc định dùng Set.copyOf không?

**Gợi ý trả lời:** không. Set.copyOf từ chối null và không bảo toàn giao diện SortedSet cùng comparator. Có thể sao chép sang TreeSet giữ đúng comparator rồi bọc bằng wrapper phù hợp; đồng thời xác định yêu cầu bất biến của từng phần tử.

### Bài 9: null ở thuộc tính

Một Task không null nhưng priority null. Chỉ bọc toàn comparator Task bằng nullsLast đã đủ chưa?

**Gợi ý trả lời:** chưa chắc. Cần xử lý null trong chính phép so sánh priority; wrapper ngoài chỉ xử lý khi toàn phần tử Task là null.

Một thiết kế khoảng rõ ràng cần ghi nhận được cả miền giá trị, vị trí null, quy tắc bao gồm biên và quyền cập nhật qua view. Khi các quyết định này tách bạch, các lời gọi API mới phản ánh đúng ý nghĩa dữ liệu thay vì chỉ tình cờ cho một output mong muốn.


</details>

- [Quay lại đầu trang](#back-to-top)
