<a id="back-to-top"></a>

# API trong JDK

## Menu
- [Collection, List và AbstractList: hợp đồng và triển khai khung](#jdk-apis-001)
- [InputStream, Reader và các lớp con cụ thể](#jdk-apis-002)
- [Comparable và Comparator: hai cách biểu diễn việc so sánh](#jdk-apis-003)
- [Iterable và Iterator: hợp đồng duyệt dữ liệu](#jdk-apis-004)
- [AutoCloseable và Closeable trong try-with-resources](#jdk-apis-005)
- [Runnable và Callable: hợp đồng công việc và kết quả](#jdk-apis-006)
- [Serializable, Cloneable và giới hạn của marker interface](#jdk-apis-007)
- [Đọc Javadoc về yêu cầu triển khai, phương thức tùy chọn và default method](#jdk-apis-008)

## <a id="jdk-apis-001">Collection, List và AbstractList: hợp đồng và triển khai khung</a>

<details>
<summary>Xem chi tiết</summary>

# Collection, List và AbstractList: hợp đồng và triển khai khung

Trong Java Collections Framework, Collection và List mô tả **hợp đồng sử dụng** một tập hợp phần tử, còn AbstractList cung cấp một phần **triển khai nền** để ta không phải viết lại mọi hành vi của một danh sách. Hiểu rõ ranh giới này giúp chọn đúng kiểu dữ liệu, triển khai List tùy chỉnh an toàn và tránh lỗi về chỉ số, iterator, thay đổi cấu trúc hay hiệu năng.

## 1. Quan hệ giữa các kiểu

~~~text
Iterable<E>
   |
   v
Collection<E>                 Map<K, V> là nhánh riêng
   |
   v
List<E>
   ^
   | implements
AbstractList<E>  (lớp trừu tượng)
   ^
   |
TaskList       (lớp cụ thể do ứng dụng định nghĩa)
~~~

- **Collection&lt;E&gt;** là interface gốc cho nhiều loại nhóm phần tử và mở rộng Iterable&lt;E&gt;. Nó cung cấp các thao tác tổng quát như đọc kích thước, duyệt, tìm, thêm và xóa phần tử.
- **List&lt;E&gt;** là interface con của Collection&lt;E&gt;. List biểu diễn một chuỗi có thứ tự, hỗ trợ truy cập theo chỉ số và cho phép phần tử trùng lặp.
- **AbstractList&lt;E&gt;** là lớp trừu tượng triển khai một phần hợp đồng List. Nó kế thừa AbstractCollection và cung cấp những thuật toán chung dựa trên một số thao tác cốt lõi mà lớp con phải định nghĩa.
- **ArrayList&lt;E&gt;** và **LinkedList&lt;E&gt;** là các triển khai có sẵn. Khi cần kiểu danh sách riêng, có thể kế thừa AbstractList hoặc AbstractSequentialList tùy cấu trúc lưu trữ.
- **Map&lt;K, V&gt;** không kế thừa Collection. Map mô hình hóa ánh xạ khóa–giá trị; các view như keySet(), values() và entrySet() mới cung cấp những Collection tương ứng.

Hãy phân biệt ba câu hỏi: interface quy định người gọi được kỳ vọng làm gì; lớp trừu tượng cung cấp sẵn những thuật toán nào; lớp cụ thể lưu trữ dữ liệu ra sao và hỗ trợ những thao tác nào.

## 2. Hợp đồng của Collection

Collection&lt;E&gt; mô tả một nhóm phần tử mà người gọi có thể thao tác thông qua một API thống nhất, bất kể dữ liệu bên dưới được lưu bằng mảng, danh sách liên kết, bảng băm hay cấu trúc khác.

Các nhóm phương thức chính:

| Nhóm | Phương thức tiêu biểu | Ý nghĩa |
|---|---|---|
| Kích thước | size(), isEmpty() | Đọc số phần tử hoặc kiểm tra rỗng |
| Tìm kiếm | contains(Object), containsAll(Collection) | Kiểm tra phần tử hoặc một nhóm phần tử |
| Duyệt/chuyển đổi | iterator(), toArray() | Duyệt hoặc chuyển phần tử sang mảng |
| Thay đổi | add(E), remove(Object), clear() | Thêm, xóa phần tử hoặc xóa toàn bộ |
| Thao tác nhóm | addAll(), removeAll(), retainAll() | Hợp nhất, loại bỏ hoặc chỉ giữ phần giao theo hợp đồng của từng phương thức |
| API hiện đại | removeIf(), stream(), parallelStream(), spliterator(), forEach() | Lọc, xử lý luồng hoặc duyệt bằng callback |

Một số điểm trong hợp đồng cần nhớ:

- Collection không tự bảo đảm thứ tự duyệt. Thứ tự phụ thuộc interface con và triển khai cụ thể.
- Collection không bắt buộc cho phép phần tử trùng lặp hay null theo một quy tắc chung cho mọi triển khai. Hợp đồng cụ thể có thể cho phép hoặc từ chối chúng.
- Một số thao tác thay đổi là **optional operations**. Triển khai không hỗ trợ thao tác đó có thể ném UnsupportedOperationException.
- add(element) trả về true nếu lời gọi làm Collection thay đổi. Với tập hợp không cho trùng lặp, thêm phần tử đã có có thể trả về false.
- Không nên giả định mọi loại Collection đều định nghĩa equals() và hashCode() theo nội dung giống nhau. List và Set có hợp đồng riêng cho những phương thức này.

Interface giúp code phụ thuộc vào hành vi cần dùng thay vì phụ thuộc vào lớp cụ thể. Ví dụ, một method nhận Collection&lt;String&gt; có thể xử lý nhiều loại tập hợp mà không cần biết dữ liệu đến từ ArrayList hay HashSet, miễn là method chỉ dựa vào các bảo đảm của Collection.

## 3. Hợp đồng riêng của List

List&lt;E&gt; bổ sung ngữ nghĩa của một **chuỗi có thứ tự**. Mỗi phần tử có vị trí, bắt đầu từ chỉ số 0; các phần tử bằng nhau có thể xuất hiện ở nhiều vị trí.

Những quy tắc quan trọng:

- get(index) đọc phần tử ở vị trí đã cho. Chỉ số âm hoặc bằng hay lớn hơn size() sẽ dẫn đến IndexOutOfBoundsException.
- add(index, element) chèn phần tử tại vị trí đó và dịch các phần tử phía sau sang phải. Chỉ số chèn hợp lệ từ 0 đến size(), bao gồm size().
- set(index, element) thay phần tử hiện có tại vị trí đó và trả về phần tử cũ. Nó không làm thay đổi kích thước danh sách.
- remove(index) xóa phần tử ở vị trí đó, trả về phần tử vừa bị xóa và dịch các phần tử phía sau sang trái.
- indexOf(value) tìm vị trí xuất hiện đầu tiên; lastIndexOf(value) tìm vị trí cuối cùng. Nếu không tìm thấy, kết quả là -1.
- iterator() duyệt theo thứ tự của List. listIterator() còn hỗ trợ đi lùi, xem chỉ số và thực hiện một số thao tác sửa đổi nếu triển khai hỗ trợ.
- Hai List bằng nhau theo hợp đồng List khi chúng có cùng kích thước và các phần tử ở từng vị trí tương ứng bằng nhau theo quy tắc equals. Thứ tự vì vậy ảnh hưởng đến equals() và hashCode() của List.

List vẫn có thể là bất biến, chỉ đọc, kích thước cố định hoặc thay đổi được. Interface không có nghĩa mọi phương thức sửa đổi đều được hỗ trợ. Hãy đọc hợp đồng của triển khai đang sử dụng và không suy luận khả năng sửa đổi chỉ từ kiểu List.

## 4. AbstractList cung cấp những gì?

AbstractList là **skeletal implementation**: một phần triển khai có sẵn của interface List. Mục tiêu là giảm số phương thức phải tự viết khi lớp con có thể cung cấp cách đọc phần tử theo chỉ số.

Thông thường, lớp con phải triển khai tối thiểu:

- get(int index): lấy phần tử tại chỉ số.
- size(): trả về số phần tử.

Chỉ với hai phương thức này, lớp con đã có thể kế thừa nhiều hành vi như iterator/listIterator, contains, indexOf, lastIndexOf, equals, hashCode và subList. Một số phương thức sửa đổi trong AbstractList mặc định ném UnsupportedOperationException, nên lớp con chỉ đọc có thể không cần ghi đè chúng.

Nếu muốn danh sách thay đổi được, lớp con thường cần ghi đè thêm những thao tác phù hợp với thiết kế lưu trữ:

- set(int index, E element) nếu cho phép thay thế phần tử.
- add(int index, E element) nếu cho phép chèn.
- remove(int index) nếu cho phép xóa.

AbstractList có thể suy ra các thao tác khác từ những phương thức này. Ví dụ, add(element) gọi add(size(), element); clear() và các thao tác duyệt sửa đổi dựa vào cơ chế xóa phần tử. Nếu chỉ hỗ trợ một số thao tác, cần kiểm tra rõ các thao tác còn lại có được kế thừa an toàn hay phải ném UnsupportedOperationException.

**Chọn lớp nền đúng với cấu trúc dữ liệu:**

- Dùng AbstractList khi truy cập theo chỉ số có hiệu năng phù hợp, chẳng hạn mảng hoặc bộ lưu trữ có thể đọc trực tiếp vị trí.
- Dùng AbstractSequentialList khi cấu trúc dữ liệu truy cập tuần tự, chẳng hạn danh sách liên kết. Nếu get(index) phải đi qua lần lượt nhiều node, các thuật toán mặc định của AbstractList có thể tạo ra chi phí O(n²) khi duyệt cả danh sách.
- Cân nhắc composition (giữ một đối tượng List bên trong và ủy quyền) nếu mục tiêu chỉ là bọc hoặc giới hạn API của một List có sẵn. Kế thừa AbstractList hữu ích khi thực sự muốn định nghĩa một kiểu List với hợp đồng rõ ràng.

## 5. Ví dụ Java 17+: tự triển khai một List bằng AbstractList

Ví dụ sau tạo TaskList, một danh sách chuỗi dùng ArrayList làm nơi lưu trữ. Lớp con chỉ định nghĩa cách đọc, thay thế, chèn và xóa phần tử; các hành vi còn lại được kế thừa từ AbstractList.

~~~java
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;

public class AbstractListDemo {
    private static final class TaskList
            extends AbstractList<String> implements RandomAccess {
        private final ArrayList<String> elements = new ArrayList<>();

        @Override
        public String get(int index) {
            return elements.get(index);
        }

        @Override
        public int size() {
            return elements.size();
        }

        @Override
        public String set(int index, String element) {
            return elements.set(index, element);
        }

        @Override
        public void add(int index, String element) {
            elements.add(index, element);
            modCount++;
        }

        @Override
        public String remove(int index) {
            String removed = elements.remove(index);
            modCount++;
            return removed;
        }
    }

    public static void main(String[] args) {
        List<String> tasks = new TaskList();

        tasks.add("read");
        tasks.add("test");
        System.out.println("initial = " + tasks);
        System.out.println("indexOf(test) = " + tasks.indexOf("test"));

        tasks.set(0, "design");
        System.out.println("after set = " + tasks);
        System.out.println("subList = " + tasks.subList(0, 1));

        tasks.remove(1);
        System.out.println("after remove = " + tasks);

        Iterator<String> iterator = tasks.iterator();
        tasks.add("deploy");
        try {
            iterator.next();
        } catch (ConcurrentModificationException ex) {
            System.out.println("iterator detected structural change");
        }

        System.out.println("final = " + tasks);
    }
}
~~~

Lưu vào file AbstractListDemo.java, rồi biên dịch và chạy:

~~~text
javac AbstractListDemo.java
java AbstractListDemo
~~~

Kết quả stdout:

~~~text
initial = [read, test]
indexOf(test) = 1
after set = [design, test]
subList = [design]
after remove = [design]
iterator detected structural change
final = [design, deploy]
~~~

### Vì sao ví dụ này hoạt động?

1. TaskList cung cấp get() và size(), nên AbstractList có đủ thông tin để triển khai nhiều thao tác đọc và duyệt.
2. add(int, element) và remove(int) ủy quyền thay đổi dữ liệu cho ArrayList bên trong. Sau khi thao tác thành công, chúng tăng modCount của TaskList để iterator do AbstractList cung cấp nhận ra thay đổi cấu trúc.
3. set() chỉ thay giá trị tại một vị trí, không làm đổi kích thước hoặc cấu trúc của danh sách, nên không tăng modCount.
4. indexOf() và subList() được thừa hưởng từ lớp nền. subList() tạo một **view** dựa trên danh sách gốc, thay vì luôn sao chép toàn bộ phần tử sang danh sách mới.
5. Sau khi tạo iterator, chương trình thêm phần tử bằng danh sách. Lần gọi next() tiếp theo phát hiện modCount đã đổi và ném ConcurrentModificationException.

RandomAccess là marker interface không có phương thức. Nó báo hiệu rằng truy cập theo chỉ số của TaskList phù hợp với các thuật toán tối ưu cho truy cập ngẫu nhiên. Trong ví dụ, get(index) gọi ArrayList.get(index), vốn có chi phí truy cập chỉ số hằng số theo mô hình thông thường.

## 6. modCount, thay đổi cấu trúc và iterator

AbstractList cung cấp trường protected modCount để hỗ trợ cơ chế fail-fast của iterator. Khi lớp con thay đổi cấu trúc danh sách, nó cần cập nhật trường này theo hợp đồng của AbstractList.

Một thay đổi cấu trúc thường là thao tác làm thay đổi kích thước hoặc thay đổi cấu trúc theo cách khiến quá trình duyệt hiện tại không còn đáng tin cậy, chẳng hạn thêm hoặc xóa phần tử. Thay giá trị bằng set() thường không phải thay đổi cấu trúc.

Các quy tắc triển khai:

- Chỉ tăng modCount sau khi thay đổi cấu trúc đã thành công. Nếu thao tác thêm hoặc xóa thất bại vì chỉ số không hợp lệ, danh sách chưa đổi thì không được tăng.
- Mỗi thao tác làm thay đổi cấu trúc cần cập nhật modCount đúng theo hợp đồng của AbstractList; không tăng tùy tiện trong các phương thức đọc hoặc set().
- Nếu ghi đè thêm các phương thức sửa đổi hoặc dùng nhiều lớp bọc, phải tránh cập nhật modCount hai lần cho cùng một thay đổi cấu trúc.
- Iterator do lớp nền cung cấp dùng giá trị modCount đã ghi nhận để phát hiện thay đổi ngoài iterator. Cơ chế này giúp phát hiện lỗi lập trình trong nhiều tình huống.
- Fail-fast chỉ là cơ chế phát hiện lỗi theo nỗ lực tốt nhất, không phải bảo đảm đồng bộ hóa. Không dùng ConcurrentModificationException làm cách điều phối luồng và không coi AbstractList là thread-safe.

Khi cần hỗ trợ sửa đổi thông qua ListIterator, hãy kiểm tra đường đi của add(), remove() và set() trên iterator. Các phương thức này phải tương thích với cách lớp con cập nhật dữ liệu và modCount. Nếu tự viết iterator, lớp con cũng phải xác định rõ cách phát hiện thay đổi ngoài iterator.

## 7. subList là view, không nhất thiết là bản sao

List.subList(fromIndex, toIndex) trả về một view của đoạn từ fromIndex (bao gồm) đến toIndex (không bao gồm). Ví dụ, subList(0, 1) của danh sách [design, test] biểu diễn đoạn [design].

- Thay đổi phần tử thông qua view được phản ánh vào danh sách nền và ngược lại theo hợp đồng của List.
- Thay đổi cấu trúc danh sách nền bên ngoài view trong khi view còn được sử dụng có thể làm hành vi của view không còn xác định theo hợp đồng hoặc gây ConcurrentModificationException tùy triển khai.
- Nếu cần một danh sách độc lập, hãy tạo bản sao rõ ràng, chẳng hạn new ArrayList<>(source.subList(from, to)).

Không giữ một subList lâu dài rồi tùy ý chèn/xóa ở danh sách gốc mà không xem xét vòng đời của view. Đây là lỗi dễ xuất hiện khi một đoạn danh sách được truyền sang lớp khác để xử lý.

## 8. Chọn triển khai và tránh nhầm lẫn

| Nhu cầu | Lựa chọn thường phù hợp | Điều cần kiểm tra |
|---|---|---|
| Danh sách tổng quát, truy cập chỉ số nhanh, thay đổi được | ArrayList&lt;E&gt; | Chi phí chèn/xóa ở giữa, khả năng sửa đổi |
| Truy cập chỉ số hoặc duyệt tuần tự theo node | LinkedList&lt;E&gt; hoặc triển khai tuần tự phù hợp | Chi phí truy cập chỉ số và bộ nhớ |
| Kiểu List riêng với lưu trữ ngẫu nhiên | Kế thừa AbstractList&lt;E&gt; | get, size, thao tác sửa đổi, modCount |
| Kiểu List riêng với lưu trữ tuần tự | Cân nhắc AbstractSequentialList&lt;E&gt; | listIterator và hiệu năng duyệt |
| Danh sách chỉ đọc tùy chỉnh | AbstractList với get và size, không mở thao tác sửa đổi | Các thao tác mutator có thể ném UnsupportedOperationException |
| Bản sao độc lập có thể sửa đổi | new ArrayList<>(source) | Sao chép phần tử tham chiếu, không tự sao chép sâu object |

Các nhầm lẫn phổ biến:

- **Cho rằng Collection luôn có thứ tự:** chỉ dựa vào hợp đồng của interface cụ thể. List có thứ tự; Collection tổng quát không bảo đảm điều đó.
- **Cho rằng List luôn sửa đổi được:** List.of(...) là không sửa đổi được; Arrays.asList(...) có kích thước cố định, cho phép set() nhưng không hỗ trợ add/remove theo cách thông thường.
- **Quên trường hợp optional operation:** một thao tác có thể hợp lệ về kiểu nhưng bị triển khai từ chối bằng UnsupportedOperationException.
- **Không kiểm tra chỉ số:** chèn cho phép index == size(); get, set và remove thì không.
- **Quên cập nhật modCount:** iterator của AbstractList có thể không phát hiện đúng thay đổi cấu trúc nếu lớp con quên cập nhật trường này.
- **Tăng modCount trong set():** thay thế phần tử tại vị trí hiện có không phải thay đổi cấu trúc thông thường; tăng không đúng sẽ khiến iterator báo lỗi không cần thiết.
- **Dùng AbstractList cho lưu trữ tuần tự nhưng không xét độ phức tạp:** các thuật toán dựa trên get(index) có thể lặp qua danh sách nhiều lần.
- **Coi subList là bản sao:** view liên kết với danh sách nền; hãy sao chép nếu cần dữ liệu độc lập.
- **Dựa vào fail-fast để đảm bảo an toàn đa luồng:** modCount không thay thế khóa, đồng bộ hóa hoặc thiết kế concurrency phù hợp.

## 9. Checklist khi tự triển khai AbstractList

Trước khi dùng một lớp con trong ứng dụng, hãy kiểm tra các điểm sau:

1. get(index) trả về đúng phần tử và xử lý chỉ số sai nhất quán với hợp đồng.
2. size() luôn phản ánh số phần tử thực tế.
3. set(), add(index, element) và remove(index) chỉ được hỗ trợ khi lớp thực sự định nghĩa được ngữ nghĩa đó.
4. Chèn/xóa cập nhật modCount đúng một lần cho mỗi thay đổi cấu trúc thành công; set() không tăng modCount chỉ vì thay giá trị.
5. Các thao tác kế thừa như clear(), addAll(), iterator.remove() và listIterator() hoạt động đúng với những thao tác mà lớp con hỗ trợ.
6. Độ phức tạp của get(index) phù hợp với AbstractList; nếu truy cập theo chỉ số tuần tự, cân nhắc AbstractSequentialList.
7. Hợp đồng về null, phần tử trùng lặp, khả năng sửa đổi và thread-safety được mô tả rõ.
8. Kiểm tra hành vi của equals(), hashCode(), subList(), iterator và các chỉ số biên, không chỉ kiểm tra add/remove cơ bản.

## 10. Tự kiểm tra

1. Collection và List khác nhau ở điểm cốt lõi nào? Collection mô tả nhóm phần tử tổng quát; List bổ sung thứ tự, chỉ số và ngữ nghĩa chuỗi.
2. Vì sao AbstractList giúp giảm công việc khi triển khai List? Nó cung cấp sẵn nhiều thuật toán dựa trên get(), size() và các thao tác cốt lõi mà lớp con định nghĩa.
3. Một lớp chỉ đọc kế thừa AbstractList cần tối thiểu những gì? Thông thường là get() và size(); các thao tác sửa đổi mặc định có thể ném UnsupportedOperationException.
4. Vì sao ví dụ tăng modCount sau add/remove nhưng không tăng sau set? Thêm/xóa làm thay đổi cấu trúc; thay giá trị tại vị trí có sẵn không làm đổi kích thước.
5. Có nên dùng AbstractList nếu get(index) phải đi qua nhiều node? Thường nên cân nhắc AbstractSequentialList để tránh các thuật toán dựa trên chỉ số gây chi phí lặp lại.
6. subList() có phải bản sao độc lập không? Không; nó là view của đoạn danh sách nền theo hợp đồng của List.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-apis-002">InputStream, Reader và các lớp con cụ thể</a>

<details>
<summary>Xem chi tiết</summary>

# InputStream, Reader và các lớp con cụ thể trong Java

Java cung cấp hai họ API chính để đọc dữ liệu: `InputStream` đọc **byte**, còn `Reader` đọc **ký tự**. Chọn đúng họ API giúp xử lý tệp nhị phân, văn bản có dấu, dữ liệu lớn và tài nguyên hệ thống một cách chính xác.

## 1. Phân biệt byte stream và character stream

| Tiêu chí | `InputStream` | `Reader` |
|---|---|---|
| Đơn vị đọc | Byte, giá trị trả về nằm trong khoảng 0–255 khi đọc một byte đơn | Đơn vị mã UTF-16 (`char`), giá trị trả về nằm trong khoảng 0–65535 khi đọc một ký tự đơn |
| Mục đích thường gặp | Ảnh, PDF, âm thanh, tệp nén, dữ liệu nhị phân, hoặc byte chưa giải mã | Văn bản cần giải mã thành ký tự |
| Phương thức đọc cơ bản | `int read()`, `int read(byte[] b)` | `int read()`, `int read(char[] cbuf)` |
| Báo hết dữ liệu | `-1` | `-1` |
| Lớp chuyển đổi quan trọng | `InputStreamReader` biến byte thành ký tự bằng một charset | `OutputStreamWriter` là lớp ghi ở chiều ngược lại, chuyển ký tự thành byte |

Java `char` là một đơn vị mã UTF-16, không phải lúc nào cũng tương đương một ký tự Unicode hoàn chỉnh. Một số ký tự ngoài Basic Multilingual Plane được biểu diễn bằng một cặp surrogate. Với xử lý văn bản thông thường, hãy dùng `Reader` và các API chuỗi; đừng tự giả định rằng số byte, số `char` và số ký tự người dùng nhìn thấy luôn bằng nhau.

**Quy tắc chọn nhanh:** dùng `InputStream` khi cần byte nguyên bản; dùng `Reader` khi cần văn bản đã giải mã. Không chuyển dữ liệu nhị phân qua `Reader`, vì quá trình giải mã có thể làm thay đổi ý nghĩa của byte.

## 2. Những lớp InputStream thường gặp

Các lớp trong JDK có vai trò khác nhau:

- **`FileInputStream`** đọc byte từ một tệp. Phù hợp khi cần truy cập byte trực tiếp; nếu mục tiêu là văn bản, hãy kết hợp với `InputStreamReader` hoặc dùng API `Files`.
- **`ByteArrayInputStream`** đọc từ một mảng byte trong bộ nhớ. Hữu ích cho ví dụ, kiểm thử và xử lý dữ liệu đã có sẵn.
- **`BufferedInputStream`** thêm bộ đệm để giảm số lần truy cập nguồn dữ liệu khi đọc từng phần nhỏ.
- **`DataInputStream`** đọc các kiểu dữ liệu nhị phân nguyên thủy như `int`, `long`, `boolean` theo định dạng mà chương trình đã thống nhất. Nó không tự biến byte thành văn bản và không phải định dạng lưu trữ có schema.
- **`ObjectInputStream`** đọc đối tượng đã được tuần tự hóa bằng Java Serialization. Chỉ dùng dữ liệu đáng tin cậy hoặc có bộ lọc phù hợp; không xem dữ liệu serialization tùy ý là an toàn.
- **`PipedInputStream`** nhận byte từ một `PipedOutputStream`, thường dùng khi hai luồng thực thi cần trao đổi dữ liệu.
- **`SequenceInputStream`** đọc tuần tự từ nhiều nguồn `InputStream` như thể chúng được nối lại.

Ví dụ đọc byte từ bộ nhớ:

```java
byte[] data = {10, 20, 30};

try (InputStream input = new ByteArrayInputStream(data)) {
    int value;
    while ((value = input.read()) != -1) {
        System.out.println(value);
    }
}
```

Output:

```text
10
20
30
```

`read()` trả về `int` thay vì `byte` để có thể biểu diễn mọi giá trị byte không dấu từ 0 đến 255 và dành `-1` cho EOF (end of stream). Nếu ép ngay kết quả thành `byte`, giá trị lớn hơn 127 có thể trở thành số âm.

## 3. Những lớp Reader thường gặp

- **`InputStreamReader`** là cầu nối giữa byte stream và character stream. Nó giải mã byte bằng charset được chỉ định.
- **`FileReader`** đọc ký tự từ tệp. Với mã mới, nên chỉ định charset tường minh bằng constructor có charset (Java 11+) hoặc dùng `Files.newBufferedReader(path, charset)`.
- **`BufferedReader`** thêm bộ đệm và cung cấp `readLine()`, thuận tiện khi xử lý văn bản theo từng dòng.
- **`StringReader`** đọc văn bản từ một đối tượng `String`, hữu ích khi kiểm thử parser mà không cần tạo tệp.
- **`CharArrayReader`** đọc từ mảng `char`.
- **`PushbackReader`** cho phép đẩy một số ký tự vừa đọc trở lại luồng, thường hữu ích khi viết parser cần nhìn trước một ký tự.

Ví dụ đọc từng dòng từ chuỗi:

```java
String text = "alpha\nbeta\n";

try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
    String line;
    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}
```

Output:

```text
alpha
beta
```

`readLine()` bỏ dấu kết thúc dòng khỏi giá trị trả về. Nó trả về `null` khi đã hết dữ liệu và không còn dòng nào để đọc; vì vậy vòng lặp đúng thường có dạng `while ((line = reader.readLine()) != null)`.

## 4. Charset: bước quan trọng khi đọc văn bản

Tệp văn bản lưu byte theo một encoding cụ thể, ví dụ UTF-8. `Reader` làm việc với ký tự, nên cần giải mã byte bằng charset phù hợp. Nếu dùng charset khác với charset đã dùng để ghi, văn bản có thể thành ký tự lỗi hoặc mojibake.

Nên chỉ định charset rõ ràng:

```java
Path path = Path.of("message.txt");

try (BufferedReader reader =
         Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}
```

Đoạn này cần các import `java.io.BufferedReader`, `java.nio.charset.StandardCharsets`, `java.nio.file.Files` và `java.nio.file.Path`.

Một cách khác khi nguồn đã là `InputStream`:

```java
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
    // Đọc văn bản từ reader
}
```

Trong ví dụ thứ hai, `inputStream` phải là một biến `InputStream` đang mở và được quản lý đúng cách. `InputStreamReader` thực hiện giải mã; `BufferedReader` thêm bộ đệm và tiện ích đọc theo dòng.

Không nên dựa vào charset mặc định của máy để đọc dữ liệu có định dạng đã biết. Khi đọc tệp do một hệ thống khác tạo ra, charset là một phần của giao thức dữ liệu và cần được thống nhất rõ ràng.

## 5. Đọc theo khối và dùng buffering

Đọc từng byte hoặc từng ký tự một có thể gây nhiều lời gọi phương thức. Với dữ liệu lớn, thường nên đọc theo mảng hoặc dùng lớp buffered.

```java
try (InputStream input = new BufferedInputStream(
        Files.newInputStream(Path.of("image.bin")))) {
    byte[] buffer = new byte[8192];
    int count;

    while ((count = input.read(buffer)) != -1) {
        // Xử lý buffer[0] đến buffer[count - 1].
        // Không xử lý toàn bộ mảng nếu count nhỏ hơn buffer.length.
    }
}
```

`count` là số byte hợp lệ trong lần đọc hiện tại. Lần cuối có thể đọc ít hơn kích thước mảng. Các byte phía sau `count - 1` có thể còn dữ liệu cũ từ lần đọc trước, nên chỉ xử lý phần vừa được báo là đã đọc.

Đối với văn bản, hãy dùng bộ đệm ở tầng phù hợp, ví dụ `BufferedReader`. Nó có thể giảm thao tác I/O và cung cấp `readLine()`. Không cần bọc thêm nhiều lớp buffering một cách máy móc; chọn cấu trúc đơn giản phù hợp với nguồn và cách đọc.

Một số phương thức tiện dụng có từ Java 9:

- `InputStream.readAllBytes()` đọc toàn bộ phần còn lại thành mảng byte.
- `InputStream.readNBytes(n)` đọc tối đa số byte yêu cầu.
- `InputStream.transferTo(out)` chuyển byte sang một `OutputStream`.
- `Reader.transferTo(writer)` chuyển ký tự sang một `Writer`.

Các phương thức đọc toàn bộ chỉ nên dùng khi kích thước dữ liệu phù hợp với bộ nhớ. Với tệp lớn hoặc luồng không có giới hạn rõ ràng, đọc theo khối thường an toàn hơn.

## 6. Quản lý tài nguyên bằng try-with-resources

Luồng có thể giữ file descriptor, socket hoặc tài nguyên hệ thống khác. Đóng luồng khi dùng xong là một phần của việc xử lý đúng.

```java
try (InputStream input = Files.newInputStream(Path.of("data.bin"))) {
    // Đọc dữ liệu
}
```

Khi rời khỏi khối `try`, Java tự gọi `close()`, kể cả khi phát sinh ngoại lệ. `Reader` cũng triển khai `Closeable`/ `AutoCloseable` và dùng được theo cách tương tự.

Đóng wrapper thường đóng luôn luồng bên dưới. Chẳng hạn đóng `BufferedReader` sẽ đóng `InputStreamReader` và `InputStream` mà nó bao bọc. Vì vậy, tránh đóng đi đóng lại các lớp lồng nhau không cần thiết; hãy xác định rõ nơi sở hữu và chịu trách nhiệm đóng tài nguyên.

Không nên nuốt ngoại lệ I/O rồi giả vờ dữ liệu đã đọc thành công. Tùy ứng dụng, hãy xử lý hoặc truyền tiếp `IOException` để caller có thể phản ứng với lỗi tệp, quyền truy cập hoặc kết nối.

## 7. Ví dụ Java 17+ chạy hoàn chỉnh

Chương trình dưới đây dùng cùng một nội dung UTF-8 để cho thấy `InputStream` đếm byte, còn `Reader` giải mã thành văn bản. Lưu tệp với tên `StreamReaderDemo.java`.

```java
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class StreamReaderDemo {
    public static void main(String[] args) throws Exception {
        String text = "Xin chào\nJava 17\n";
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);

        int totalBytes = 0;
        try (InputStream input = new ByteArrayInputStream(encoded)) {
            byte[] buffer = new byte[4];
            int count;
            while ((count = input.read(buffer)) != -1) {
                totalBytes += count;
            }
        }
        System.out.println("UTF-8 bytes: " + totalBytes);

        try (Reader decoded = new InputStreamReader(
                    new ByteArrayInputStream(encoded),
                    StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(decoded)) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Line: " + line);
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac StreamReaderDemo.java
java StreamReaderDemo
```

Stdout chính xác:

```text
UTF-8 bytes: 18
Line: Xin chào
Line: Java 17
```

Giải thích:

1. `getBytes(StandardCharsets.UTF_8)` mã hóa chuỗi thành byte UTF-8.
2. Vòng lặp `InputStream.read(buffer)` cộng số byte thực sự đọc được, không cộng kích thước toàn bộ mảng mỗi lần.
3. `InputStreamReader` giải mã cùng mảng byte bằng đúng UTF-8.
4. `BufferedReader.readLine()` trả về từng dòng mà không giữ ký tự xuống dòng.
5. `try-with-resources` tự đóng các luồng. Trong khối thứ hai, đóng `BufferedReader` cũng đóng `Reader` mà nó bao bọc.

Chuỗi mẫu có chữ `à`, được mã hóa UTF-8 thành hai byte; vì thế số byte UTF-8 không nhất thiết bằng số ký tự trong văn bản.

## 8. Lỗi thường gặp

- **Dùng `InputStream` như thể nó đọc văn bản:** byte không tự động là ký tự. Hãy giải mã bằng `InputStreamReader` với charset đúng.
- **Không chỉ định charset:** kết quả có thể phụ thuộc môi trường hoặc không khớp định dạng của tệp. Dùng UTF-8 khi đó là encoding của dữ liệu.
- **Giả định một lần `read(byte[])` sẽ lấp đầy mảng:** một lần đọc có thể trả về ít byte hơn mong đợi. Luôn dùng giá trị trả về để xác định phần hợp lệ.
- **Quên EOF:** `read()` trả về `-1` khi hết dữ liệu; không kiểm tra EOF có thể gây vòng lặp sai hoặc lặp vô hạn.
- **Xem `available()` là kích thước còn lại của tệp:** phương thức này chỉ ước lượng số byte có thể đọc hoặc bỏ qua mà không bị chặn; không dùng nó để cấp phát mảng chứa toàn bộ dữ liệu.
- **Không đóng luồng:** tài nguyên có thể bị giữ lại lâu hơn cần thiết. Dùng try-with-resources.
- **Đọc toàn bộ tệp lớn vào bộ nhớ:** `readAllBytes()` và các cách tương tự có thể tiêu tốn nhiều bộ nhớ. Ưu tiên đọc theo khối với dữ liệu lớn.
- **Dùng `readLine()` rồi mong nhận lại dấu xuống dòng:** phương thức loại bỏ dấu kết thúc dòng. Nếu định dạng yêu cầu giữ nguyên byte hoặc newline, xử lý ở tầng byte hoặc dùng API phù hợp.
- **Tin cậy dữ liệu Java Serialization không rõ nguồn:** `ObjectInputStream` có thể khởi tạo đối tượng khi đọc. Không deserialize dữ liệu không đáng tin cậy nếu không có biện pháp bảo vệ phù hợp.

## 9. Checklist lựa chọn API

- Cần byte chính xác của ảnh, PDF, tệp nén hoặc dữ liệu nhị phân: `InputStream`.
- Cần đọc ký tự từ byte theo UTF-8: `InputStreamReader` với charset tường minh.
- Cần đọc văn bản theo dòng: bọc `Reader` bằng `BufferedReader`.
- Cần đọc từ tệp văn bản: cân nhắc `Files.newBufferedReader(path, charset)`.
- Cần đọc từ chuỗi trong bộ nhớ: `StringReader`.
- Cần đọc từ mảng byte trong bộ nhớ: `ByteArrayInputStream`.
- Cần xử lý dữ liệu lớn: đọc theo khối, chỉ dùng số byte hoặc ký tự thực sự được trả về, và đóng tài nguyên bằng try-with-resources.

Điểm cốt lõi là xác định dữ liệu đang ở dạng byte hay văn bản, chọn đúng lớp chuyển đổi, khai báo charset khi giải mã và quản lý vòng đời luồng một cách rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-apis-003">Comparable và Comparator: hai cách biểu diễn việc so sánh</a>

<details>
<summary>Xem chi tiết</summary>

# Comparable và Comparator: hai cách biểu diễn việc so sánh

Khi cần sắp xếp đối tượng trong Java, câu hỏi đầu tiên là: **thứ tự tự nhiên của kiểu này là gì, và có cần những thứ tự khác theo từng tình huống không?** Java cung cấp hai cơ chế chính để trả lời:

- `Comparable<T>` cho phép chính kiểu dữ liệu định nghĩa thứ tự tự nhiên của nó thông qua `compareTo`.
- `Comparator<T>` biểu diễn một quy tắc so sánh độc lập, có thể tạo nhiều cách sắp xếp cho cùng một kiểu dữ liệu.

Hai cơ chế đều trả về kết quả so sánh, nhưng khác nhau về nơi đặt quy tắc, khả năng thay đổi và cách kết hợp nhiều tiêu chí. Hiểu đúng chúng giúp sắp xếp danh sách, dùng API Collections và tránh lỗi với các cấu trúc dữ liệu có thứ tự.

## 1. Bài toán cần giải quyết

Giả sử ứng dụng quản lý sản phẩm. Mỗi sản phẩm có tên và giá. Có lúc ta muốn sắp xếp theo tên, lúc khác theo giá tăng dần, hoặc theo giá giảm dần rồi dùng tên để phân định các sản phẩm cùng giá.

Nếu viết riêng một thuật toán sắp xếp cho mỗi trường hợp, mã sẽ lặp lại và khó bảo trì. Thư viện Java đã có các thuật toán sắp xếp; ta chỉ cần cung cấp quy tắc so sánh phù hợp.

`Comparable` phù hợp khi kiểu có một thứ tự tự nhiên hợp lý, chẳng hạn số nguyên theo giá trị hoặc chuỗi theo thứ tự từ điển. `Comparator` phù hợp khi thứ tự phụ thuộc vào nhu cầu của nơi sử dụng, hoặc khi không thể hay không muốn sửa kiểu dữ liệu gốc.

## 2. Comparable và phương thức compareTo

`Comparable<T>` nằm trong `java.lang` nên không cần import. Kiểu triển khai interface này phải định nghĩa:

~~~java
public int compareTo(T other)
~~~

Giá trị trả về mang ý nghĩa:

| Kết quả | Ý nghĩa |
| --- | --- |
| Số âm | Đối tượng hiện tại đứng trước `other` |
| Số 0 | Hai đối tượng tương đương theo thứ tự tự nhiên |
| Số dương | Đối tượng hiện tại đứng sau `other` |

Không cần trả về chính xác `-1` hoặc `1`. Bất kỳ số nguyên âm, số 0 hoặc số nguyên dương nào cũng thể hiện được ba trạng thái đó. Người gọi nên kiểm tra dấu của kết quả thay vì giả định giá trị trả về chỉ có thể là -1, 0 hoặc 1.

Ví dụ, một kiểu `Product` có thể định nghĩa thứ tự tự nhiên theo tên:

~~~java
record Product(String name, int price) implements Comparable<Product> {
    @Override
    public int compareTo(Product other) {
        return this.name.compareTo(other.name);
    }
}
~~~

Trong ví dụ này, việc gọi `a.compareTo(b)` so sánh tên của hai sản phẩm. Các API có thể sử dụng thứ tự tự nhiên đó mà không cần nhận thêm một đối tượng so sánh. Với kiểu tự định nghĩa, hãy chọn thứ tự tự nhiên mà người dùng kiểu có thể dự đoán và giải thích được.

## 3. Comparator và quy tắc so sánh bên ngoài

`Comparator<T>` nằm trong `java.util`. Nó cung cấp phương thức:

~~~java
int compare(T left, T right)
~~~

Ý nghĩa dấu giống `compareTo`: âm nghĩa là `left` đứng trước `right`, 0 nghĩa là tương đương theo comparator đó, dương nghĩa là `left` đứng sau `right`.

Ví dụ comparator so sánh sản phẩm theo giá:

~~~java
Comparator<Product> byPrice =
    Comparator.comparingInt(Product::price);
~~~

Quy tắc này tồn tại độc lập với lớp `Product`. Cùng một danh sách có thể được sắp xếp theo tên, giá, ngày tạo hoặc một tiêu chí riêng mà không phải sửa định nghĩa `Product`. Đây là lợi ích chính của `Comparator` khi ứng dụng cần nhiều thứ tự khác nhau.

Một comparator thường được truyền vào các API như `list.sort(comparator)`, `Collections.sort(list, comparator)` hoặc `stream.sorted(comparator)`. Nếu kiểu đã triển khai `Comparable`, các API cũng có thể dùng thứ tự tự nhiên khi không truyền comparator.

## 4. Ví dụ Java 17+ chạy hoàn chỉnh

Chương trình sau minh họa cả hai cơ chế. `Product` định nghĩa thứ tự tự nhiên theo tên; các comparator bổ sung thứ tự theo giá tăng dần và giảm dần. Khi giá bằng nhau, `thenComparing` dùng tên làm tiêu chí tiếp theo.

~~~java
import java.util.Comparator;
import java.util.List;

record Product(String name, int price) implements Comparable<Product> {
    @Override
    public int compareTo(Product other) {
        return this.name.compareTo(other.name);
    }

    String label() {
        return name + ":" + price;
    }
}

public class ComparableComparatorDemo {
    public static void main(String[] args) {
        List<Product> products = List.of(
            new Product("Pen", 15),
            new Product("Notebook", 32),
            new Product("Pencil", 5),
            new Product("Marker", 15)
        );

        System.out.println("Original: " + labels(products));
        System.out.println("Natural order (name): "
            + labels(products.stream().sorted().toList()));

        Comparator<Product> byPriceThenName = Comparator
            .comparingInt(Product::price)
            .thenComparing(Product::name);
        System.out.println("By price ascending, then name: "
            + labels(products.stream().sorted(byPriceThenName).toList()));

        Comparator<Product> byPriceDescendingThenName = Comparator
            .comparingInt(Product::price).reversed()
            .thenComparing(Product::name);
        System.out.println("By price descending, then name: "
            + labels(products.stream().sorted(byPriceDescendingThenName).toList()));
    }

    private static List<String> labels(List<Product> products) {
        return products.stream().map(Product::label).toList();
    }
}
~~~

Lưu chương trình vào tệp `ComparableComparatorDemo.java`. Ví dụ sử dụng record và `Stream.toList()`, nên cần Java 17 trở lên.

~~~text
javac ComparableComparatorDemo.java
java ComparableComparatorDemo
~~~

Kết quả stdout:

~~~text
Original: [Pen:15, Notebook:32, Pencil:5, Marker:15]
Natural order (name): [Marker:15, Notebook:32, Pen:15, Pencil:5]
By price ascending, then name: [Pencil:5, Marker:15, Pen:15, Notebook:32]
By price descending, then name: [Notebook:32, Marker:15, Pen:15, Pencil:5]
~~~

Dòng thứ hai dùng `sorted()` không đối số, vì vậy Java sử dụng `Comparable` của `Product` và sắp xếp theo tên. Dòng thứ ba truyền comparator theo giá tăng dần; hai sản phẩm giá 15 được phân định theo tên, nên `Marker` đứng trước `Pen`. Dòng cuối đảo chiều tiêu chí giá nhưng vẫn giữ tên tăng dần khi giá bằng nhau.

`Stream.sorted()` tạo một luồng đã sắp xếp và không sửa danh sách nguồn. Nếu cần sắp xếp trực tiếp danh sách có thể thay đổi, dùng `products.sort(comparator)`; phương thức này thay đổi thứ tự phần tử trong chính danh sách.

## 5. Kết hợp nhiều tiêu chí bằng Comparator

`Comparator` có các phương thức tiện ích để xây dựng quy tắc dễ đọc, thay vì tự viết logic so sánh dài:

- `Comparator.comparing(Product::name)` so sánh theo khóa được trích xuất; khóa phải có thứ tự tự nhiên phù hợp.
- `Comparator.comparingInt(Product::price)` chuyên dùng cho khóa kiểu `int`; tương tự có `comparingLong` và `comparingDouble`.
- `thenComparing(...)` thêm tiêu chí phụ khi tiêu chí trước trả về 0.
- `reversed()` tạo comparator đảo ngược thứ tự của comparator hiện tại.
- `Comparator.nullsFirst(comparator)` và `nullsLast(comparator)` xác định vị trí của giá trị `null`.
- `Comparator.comparing(keyExtractor, keyComparator)` cho phép chỉ định cách so sánh khóa trích xuất.

Ví dụ sắp xếp theo bộ phận, sau đó theo họ tên, có thể diễn đạt bằng chuỗi `thenComparing`. Comparator sẽ dùng tiêu chí kế tiếp chỉ khi các tiêu chí trước coi hai đối tượng là ngang nhau.

Hãy chú ý vị trí của `reversed()`. Biểu thức `comparingInt(Product::price).reversed().thenComparing(Product::name)` có nghĩa giá giảm dần, sau đó tên tăng dần. Nếu đảo ngược toàn bộ comparator sau khi đã nối tên, cả thứ tự tên trong nhóm đồng giá cũng sẽ bị đảo.

Với khóa số, tránh viết `a.price() - b.price()` làm kết quả so sánh. Phép trừ số nguyên có thể tràn và tạo dấu sai. Hãy dùng `Integer.compare(a.price(), b.price())` hoặc `Comparator.comparingInt(...)` để diễn đạt ý định an toàn hơn.

## 6. Hợp đồng của phép so sánh

Một phép so sánh không chỉ cần cho ra thứ tự mong muốn ở một cặp phần tử; nó phải nhất quán trên toàn bộ dữ liệu. Các thuật toán sắp xếp và cấu trúc dữ liệu có thứ tự dựa vào các quy tắc này.

Với `compareTo` và `Comparator.compare`, hãy bảo đảm các tính chất chính:

1. **Tính đối xứng về dấu:** nếu so sánh `a` với `b` cho kết quả âm, so sánh `b` với `a` phải cho kết quả dương, và ngược lại. Nếu một bên là 0 thì chiều ngược lại cũng phải là 0.
2. **Tính bắc cầu:** nếu `a` đứng trước `b` và `b` đứng trước `c`, thì `a` phải đứng trước `c`.
3. **Tính nhất quán với phần tử thứ ba:** nếu `a` và `b` được xem là bằng nhau theo phép so sánh, việc so sánh chúng với `c` phải cho cùng quan hệ thứ tự.
4. **Xử lý null rõ ràng:** `Comparable` thông thường không hỗ trợ gọi `compareTo(null)` và sẽ ném `NullPointerException`. Comparator cũng có thể không chấp nhận `null`, trừ khi được xây dựng để xử lý trường hợp này.

Đối với `Comparable`, tài liệu Java khuyến nghị mạnh rằng `compareTo` nhất quán với `equals`, nghĩa là `a.compareTo(b) == 0` khi và chỉ khi `a.equals(b)` trả về true. Tuy nhiên, điều này không bắt buộc trong mọi kiểu; nếu hai khái niệm khác nhau, cần ghi rõ để người dùng hiểu hệ quả.

Với `Comparator`, việc nhất quán với `equals` cũng thường hữu ích, nhưng không bắt buộc. Comparator có thể chủ ý coi hai đối tượng khác nhau là tương đương theo một tiêu chí cụ thể, chẳng hạn chỉ so sánh họ tên mà bỏ qua mã định danh.

## 7. Tương tác với List, Stream, TreeSet và TreeMap

Lựa chọn comparator ảnh hưởng đến cách API sử dụng thứ tự:

- `List.sort(comparator)` sắp xếp trực tiếp danh sách có thể thay đổi. `Collections.sort(list)` dùng thứ tự tự nhiên, còn overload nhận comparator cho phép chọn thứ tự khác.
- `stream.sorted()` dùng thứ tự tự nhiên của các phần tử. `stream.sorted(comparator)` dùng comparator đã cung cấp và trả về luồng đã sắp xếp.
- `Arrays.sort(array)` có các overload dùng thứ tự tự nhiên hoặc comparator cho mảng tham chiếu.
- `TreeSet` và `TreeMap` dùng thứ tự tự nhiên hoặc comparator để tổ chức phần tử/khóa.

Cần đặc biệt lưu ý `TreeSet` và `TreeMap`. Trong các cấu trúc này, hai phần tử hoặc khóa được xem là cùng vị trí nếu phép so sánh trả về 0, dù `equals` có thể trả về false. Nếu comparator chỉ so sánh giá, hai sản phẩm khác tên nhưng cùng giá có thể bị xem là trùng trong `TreeSet` và một phần tử sẽ không được thêm như mong đợi.

Nếu cần lưu cả các đối tượng có cùng giá, hãy thêm tiêu chí phân biệt như mã định danh hoặc tên, miễn là tiêu chí đó phù hợp với yêu cầu dữ liệu. Trước khi dùng kiểu tự định nghĩa làm khóa trong cấu trúc có thứ tự, hãy kiểm tra hợp đồng so sánh và quyết định rõ thế nào là hai khóa tương đương.

## 8. Lỗi phổ biến và cách tránh

**Viết phép trừ để so sánh số.** Phép trừ có thể tràn số nguyên. Dùng `Integer.compare`, `Long.compare` hoặc các factory của `Comparator`.

**Cố gắng sửa kiểu thư viện để triển khai Comparable.** Không thể thêm `implements Comparable` vào kiểu nguồn mà mình không sở hữu. Hãy cung cấp `Comparator` từ bên ngoài.

**Dùng một thứ tự tự nhiên không hợp lý.** `Comparable` là cam kết của kiểu; nếu nhiều thứ tự có ý nghĩa tương đương, có thể chọn một thứ tự tự nhiên ổn định và để các thứ tự còn lại cho comparator.

**Cho rằng compare trả về -1, 0 hoặc 1.** Hãy kiểm tra dấu của kết quả, không kiểm tra bằng phép so sánh chính xác với -1 hoặc 1.

**Bỏ qua trường hợp bằng nhau.** Nếu hai sản phẩm cùng giá, comparator theo giá trả về 0. Nếu cần kết quả xác định hơn, thêm `thenComparing` theo tiêu chí phụ.

**Dùng Comparator không nhất quán.** Một comparator vi phạm tính bắc cầu hoặc thay đổi kết quả tùy trạng thái có thể làm sắp xếp sai hoặc gây `IllegalArgumentException` khi thuật toán phát hiện hợp đồng bị vi phạm.

**Nhầm sắp xếp với việc thay đổi danh sách nguồn.** `stream.sorted()` không sắp xếp tại chỗ danh sách ban đầu; `List.sort()` thì thay đổi danh sách đó. Chọn API theo việc có cần giữ nguyên thứ tự ban đầu hay không.

## 9. Nên chọn Comparable hay Comparator?

| Câu hỏi | Lựa chọn phù hợp |
| --- | --- |
| Kiểu dữ liệu có một thứ tự tự nhiên rõ ràng? | Triển khai `Comparable<T>` |
| Cần nhiều cách sắp xếp cho cùng một kiểu? | Tạo các `Comparator<T>` riêng |
| Kiểu đến từ thư viện và không sửa được? | Dùng `Comparator<T>` |
| Muốn kết hợp nhiều tiêu chí ngắn gọn? | Dùng `comparing` và `thenComparing` |
| Cần đảo chiều thứ tự? | Dùng `reversed()` tại đúng cấp tiêu chí |
| Dùng kiểu trong `TreeSet` hoặc `TreeMap`? | Kiểm tra ý nghĩa của kết quả so sánh bằng 0 và tính nhất quán với `equals` |

Hai cơ chế bổ trợ cho nhau. Một kiểu có thể triển khai `Comparable` cho thứ tự tự nhiên và đồng thời được sử dụng với nhiều comparator khác nhau trong từng màn hình hoặc nghiệp vụ. Không cần buộc mọi yêu cầu sắp xếp vào duy nhất một phương thức `compareTo`.

## 10. Ghi nhớ

`Comparable` đặt thứ tự tự nhiên bên trong kiểu dữ liệu qua `compareTo`. `Comparator` đặt quy tắc so sánh bên ngoài qua `compare`, nhờ đó cùng một tập dữ liệu có thể được sắp xếp theo nhiều tiêu chí. Cả hai phải tuân theo hợp đồng về dấu và tính bắc cầu; khi dùng nhiều tiêu chí, `thenComparing` giúp thể hiện thứ tự rõ ràng. Đặc biệt, hãy tránh phép trừ có nguy cơ tràn số, xử lý `null` theo chủ đích và kiểm tra hệ quả của kết quả 0 khi dùng cấu trúc dữ liệu có thứ tự.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-apis-004">Iterable và Iterator: hợp đồng duyệt dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# `Iterable` và `Iterator`: hợp đồng duyệt dữ liệu

`Iterable` và `Iterator` là hai interface nền tảng trong Java giúp duyệt qua một chuỗi phần tử mà không cần biết dữ liệu được lưu trữ hoặc tạo ra như thế nào. `Iterable<T>` biểu thị một đối tượng có thể cung cấp một iterator để duyệt các phần tử kiểu `T`; `Iterator<T>` đại diện cho trạng thái và thao tác của một lượt duyệt cụ thể.

Hai interface này có vai trò liên quan nhưng khác nhau: `Iterable` cung cấp điểm bắt đầu để lấy iterator, còn `Iterator` điều khiển việc kiểm tra phần tử kế tiếp và lấy phần tử đó. Hiểu đúng hợp đồng giúp dùng vòng lặp `for-each`, tự viết kiểu dữ liệu có thể duyệt, và tránh lỗi khi iterator đã đi đến cuối.

## 1. Hợp đồng của `Iterable<T>`

`Iterable<T>` nằm trong `java.lang`, vì vậy không cần import riêng. Hợp đồng quan trọng nhất là phương thức:

```java
Iterator<T> iterator();
```

Phương thức `iterator()` trả về một đối tượng dùng để duyệt các phần tử. Một lớp muốn dùng được với vòng lặp `for-each` theo dạng collection có thể triển khai `Iterable<T>` và cung cấp phương thức này. Ví dụ, `List<T>` kế thừa hợp đồng `Iterable<T>` thông qua `Collection<T>`.

`Iterable` không bắt buộc đối tượng phải lưu tất cả phần tử trong bộ nhớ hoặc phải là một collection. Một đối tượng có thể tạo giá trị khi được duyệt, đọc từ nguồn dữ liệu, hoặc tính toán các phần tử theo nhu cầu. Chi tiết về thứ tự, khả năng lặp lại nhiều lần và việc dữ liệu có thay đổi hay không phụ thuộc vào kiểu cụ thể.

Ngoài `iterator()`, `Iterable` có các phương thức mặc định hữu ích như `forEach(Consumer<? super T>)` và `spliterator()`. `forEach` áp dụng hành động cho các phần tử theo thứ tự duyệt được cung cấp; `spliterator` cung cấp một cơ chế duyệt có thể hỗ trợ phân tách công việc cho các API như Stream.

## 2. Hợp đồng của `Iterator<T>`

Một iterator quản lý một lượt duyệt. Hai phương thức cốt lõi là:

```java
boolean hasNext();
T next();
```

- `hasNext()` trả về `true` nếu lượt duyệt còn ít nhất một phần tử; phương thức này không lấy hoặc tiêu thụ phần tử kế tiếp.
- `next()` trả về phần tử kế tiếp và tiến trạng thái duyệt. Nếu không còn phần tử, `next()` phải ném `NoSuchElementException`.

`Iterator` còn có `remove()`, một thao tác tùy chọn để xóa phần tử gần nhất được trả về bởi `next()`. Một iterator không hỗ trợ xóa có thể ném `UnsupportedOperationException`. Không nên giả định mọi iterator đều cho phép sửa collection trong lúc duyệt. Phương thức mặc định `forEachRemaining(Consumer<? super T>)` thực hiện hành động cho các phần tử còn lại.

Thông thường, hãy gọi `hasNext()` để kiểm tra trước khi gọi `next()` trong vòng lặp. Không gọi `next()` thêm một lần sau khi đã hết phần tử. Một iterator thường là con trỏ tiến về phía trước; muốn duyệt lại, hãy lấy iterator khác từ `Iterable` nếu kiểu dữ liệu hỗ trợ lặp lại.

## 3. Ví dụ Java 17+ có thể chạy

Ví dụ dưới đây định nghĩa `IntRange`, một dãy số nguyên từ `startInclusive` đến trước `endExclusive`. Lớp này triển khai `Iterable<Integer>` và mỗi lời gọi `iterator()` tạo một iterator có trạng thái riêng.

Lưu vào tệp `IterableIteratorDemo.java`:

```java
import java.util.Iterator;
import java.util.NoSuchElementException;

public class IterableIteratorDemo {
    static final class IntRange implements Iterable<Integer> {
        private final int startInclusive;
        private final int endExclusive;

        IntRange(int startInclusive, int endExclusive) {
            if (endExclusive < startInclusive) {
                throw new IllegalArgumentException("endExclusive must be >= startInclusive");
            }
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
        }

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<>() {
                private int current = startInclusive;

                @Override
                public boolean hasNext() {
                    return current < endExclusive;
                }

                @Override
                public Integer next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return current++;
                }
            };
        }
    }

    public static void main(String[] args) {
        Iterable<Integer> numbers = new IntRange(1, 5);

        System.out.println("for-each:");
        for (int number : numbers) {
            System.out.println(number);
        }

        System.out.println("manual iterator:");
        Iterator<Integer> iterator = numbers.iterator();
        while (iterator.hasNext()) {
            System.out.println(iterator.next());
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```text
javac IterableIteratorDemo.java
java IterableIteratorDemo
```

Đầu ra chính xác:

```text
for-each:
1
2
3
4
manual iterator:
1
2
3
4
```

## 4. Cách đọc ví dụ

`IntRange` lưu hai giới hạn. Điều kiện `current < endExclusive` khiến giá trị cuối không được đưa vào dãy; với `new IntRange(1, 5)`, các phần tử là `1`, `2`, `3`, `4`. Constructor từ chối giới hạn cuối nhỏ hơn giới hạn đầu để tránh một phạm vi không hợp lệ theo hợp đồng đã chọn.

Trong `iterator()`, lớp ẩn danh giữ biến `current`. `hasNext()` chỉ kiểm tra điều kiện, còn `next()` kiểm tra lại điều kiện, ném `NoSuchElementException` nếu đã hết, rồi trả về giá trị hiện tại và tăng biến đếm. Kiểm tra trong `next()` vẫn cần thiết vì người gọi có thể gọi `next()` mà không gọi `hasNext()` trước.

Vòng `for (int number : numbers)` là cú pháp duyệt thuận tiện cho một đối tượng `Iterable`. Về ý tưởng, compiler lấy iterator rồi lặp khi `hasNext()` còn đúng, mỗi vòng gọi `next()` để nhận giá trị. Vòng `while` phía sau thể hiện trực tiếp hợp đồng đó. Vì ví dụ `IntRange.iterator()` tạo trạng thái mới mỗi lần được gọi, hai lượt duyệt đều bắt đầu từ `1`.

## 5. So sánh nhanh

| Thành phần | Trách nhiệm | Ví dụ sử dụng |
| --- | --- | --- |
| `Iterable<T>` | Cung cấp iterator cho một đối tượng có thể duyệt. | `for (String s : names)` |
| `Iterator<T>` | Theo dõi vị trí của một lượt duyệt, kiểm tra và trả về phần tử kế tiếp. | `while (it.hasNext()) { use(it.next()); }` |
| `Collection<T>` | Đại diện cho một nhóm phần tử và kế thừa `Iterable<T>`. | `List<String> names = new ArrayList<>();` |

Chọn `Iterable` khi muốn người dùng có thể duyệt kiểu dữ liệu bằng `for-each`. Làm việc trực tiếp với `Iterator` khi cần kiểm soát tiến trình duyệt, gọi `remove()` nếu được hỗ trợ, hoặc dừng tại một điều kiện cụ thể.

## 6. Các lỗi và lưu ý thường gặp

1. **Gọi `next()` sau khi hết phần tử.** Luôn kiểm tra `hasNext()` hoặc dùng `for-each`; nếu không còn phần tử, `next()` ném `NoSuchElementException`.
2. **Nhầm `hasNext()` với thao tác lấy phần tử.** `hasNext()` chỉ kiểm tra xem còn phần tử hay không; `next()` mới trả về phần tử và chuyển iterator tiến lên.
3. **Giả định `remove()` luôn được hỗ trợ.** Đây là thao tác tùy chọn. Đọc tài liệu của kiểu iterator cụ thể và xử lý `UnsupportedOperationException` nếu thiết kế cần tính đến trường hợp đó.
4. **Sửa collection bên ngoài trong lúc duyệt.** Một số iterator của JDK là fail-fast và có thể ném `ConcurrentModificationException` khi phát hiện sửa đổi không được phép. Cơ chế fail-fast chỉ nhằm phát hiện lỗi theo best-effort; không phải bảo đảm đồng bộ hay bảo đảm phát hiện mọi sửa đổi. Hãy dùng API sửa đổi được iterator hỗ trợ hoặc cơ chế đồng bộ phù hợp.
5. **Giả định mọi `Iterable` có thể duyệt lại.** Hợp đồng `Iterable` cung cấp `iterator()`, nhưng khả năng lặp lại và trạng thái của nguồn phụ thuộc vào cách triển khai. Nếu cần nhiều lượt duyệt, hãy xác nhận kiểu cụ thể hỗ trợ điều đó.
6. **Bỏ qua thứ tự duyệt.** Thứ tự phần tử do implementation quy định. Một `List` giữ thứ tự theo chỉ số, trong khi một kiểu collection khác có thể có quy tắc thứ tự khác.

## 7. Ghi nhớ

- `Iterable<T>` cung cấp `iterator()`; `Iterator<T>` cung cấp `hasNext()` và `next()`.
- `hasNext()` kiểm tra, `next()` trả về phần tử kế tiếp và ném `NoSuchElementException` khi đã hết.
- Vòng `for-each` làm mã duyệt `Iterable` gọn hơn; iterator tường minh hữu ích khi cần kiểm soát lượt duyệt.
- `remove()` là thao tác tùy chọn, còn fail-fast không phải cơ chế đồng bộ.
- Khi tự triển khai `Iterable`, xác định rõ thứ tự, điều kiện kết thúc, hành vi khi hết phần tử và khả năng tạo iterator mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-apis-005">AutoCloseable và Closeable trong try-with-resources</a>

<details>
<summary>Xem chi tiết</summary>

# AutoCloseable và Closeable trong try-with-resources

## Mục tiêu

Java cung cấp hai interface quan trọng để biểu diễn đối tượng cần giải phóng tài nguyên: `AutoCloseable` và `Closeable`. Cả hai kết hợp với cú pháp `try-with-resources` để tự động gọi `close()` khi khối `try` kết thúc, kể cả khi có ngoại lệ.

Sau bài này, bạn sẽ biết mối quan hệ giữa hai interface, cách chọn loại phù hợp, thứ tự đóng tài nguyên và cách Java xử lý ngoại lệ phát sinh trong lúc đóng.

## 1. AutoCloseable là gì?

`AutoCloseable` thuộc gói `java.lang` và có từ Java 7. Đây là hợp đồng tổng quát cho đối tượng có thể được đóng hoặc giải phóng tài nguyên. Interface khai báo phương thức:

~~~java
public interface AutoCloseable {
    void close() throws Exception;
}
~~~

Đây là chữ ký khái quát của interface. Một lớp triển khai có thể khai báo `close()` không ném ngoại lệ checked nào, hoặc ném một loại ngoại lệ hẹp hơn `Exception`. Ví dụ, lớp quản lý kết nối cơ sở dữ liệu có thể ném `SQLException`, còn lớp quản lý tài nguyên I/O có thể ném `IOException`.

`AutoCloseable` được dùng bởi nhiều API cần dọn dẹp tài nguyên, chẳng hạn tài nguyên cơ sở dữ liệu, giao dịch, bộ đọc/ghi hoặc tài nguyên do ứng dụng tự định nghĩa. Nó cho phép `try-with-resources` xử lý việc gọi `close()` theo cùng một cơ chế mà không cần biết chi tiết tài nguyên bên trong.

## 2. Closeable là gì và khác ở đâu?

`Closeable` thuộc gói `java.io`, có từ Java 5 và mở rộng `AutoCloseable`. Nó được thiết kế cho tài nguyên I/O:

~~~java
public interface Closeable extends AutoCloseable {
    void close() throws IOException;
}
~~~

Vì `Closeable` là một `AutoCloseable`, mọi đối tượng triển khai `Closeable` cũng có thể được sử dụng trong `try-with-resources`.

| Đặc điểm | `AutoCloseable` | `Closeable` |
|---|---|---|
| Gói | `java.lang` | `java.io` |
| Có từ | Java 7 | Java 5 |
| Phạm vi | Hợp đồng đóng tài nguyên tổng quát | Tập trung vào tài nguyên I/O |
| Chữ ký `close()` | `throws Exception` | `throws IOException` |
| Quan hệ kế thừa | Interface gốc của hợp đồng này | Mở rộng `AutoCloseable` |
| Đóng nhiều lần | Không bắt buộc phải an toàn khi gọi nhiều lần; nên thiết kế có tính lặp an toàn khi phù hợp | Hợp đồng quy định gọi `close()` khi đã đóng không có tác dụng |

Điểm khác biệt đáng chú ý là loại ngoại lệ và hợp đồng khi đóng lặp lại. `Closeable.close()` chỉ khai báo `IOException`, phù hợp với các thao tác I/O. `AutoCloseable.close()` có chữ ký tổng quát hơn nên có thể ném `Exception`; khi triển khai thực tế, nên khai báo loại ngoại lệ cụ thể nhất có thể.

Khi ghi đè một phương thức, Java không cho phép phương thức ghi đè mở rộng danh sách checked exception vượt quá hợp đồng của phương thức gốc. Vì vậy, lớp triển khai `Closeable` không thể khai báo `close() throws Exception`; nó phải tuân thủ chữ ký tương thích, chẳng hạn `throws IOException` hoặc không khai báo checked exception.

## 3. try-with-resources tự động đóng tài nguyên

Cú pháp `try-with-resources` được giới thiệu trong Java 7. Mỗi tài nguyên khai báo trong cặp ngoặc tròn phải triển khai `AutoCloseable` (bao gồm cả `Closeable`):

~~~java
try (BufferedReader reader = Files.newBufferedReader(path)) {
    System.out.println(reader.readLine());
}
~~~

Ví dụ trên minh họa cú pháp; để chạy độc lập, chương trình cần khai báo biến `path` và xử lý `IOException`. Khi khối `try` kết thúc bình thường hoặc do ngoại lệ, Java tự động gọi `reader.close()`. Không cần tự viết khối `finally` chỉ để đóng reader.

Có thể khai báo nhiều tài nguyên, phân cách bằng dấu chấm phẩy:

~~~java
try (InputStream input = Files.newInputStream(source);
     OutputStream output = Files.newOutputStream(destination)) {
    input.transferTo(output);
}
~~~

Tài nguyên được khởi tạo theo thứ tự từ trái sang phải và được đóng theo thứ tự ngược lại, tức tài nguyên được mở sau sẽ được đóng trước. Thứ tự này hữu ích khi một tài nguyên phụ thuộc vào tài nguyên khác.

Từ Java 9, có thể dùng một biến đã được khai báo bên ngoài nếu biến đó là `final` hoặc effectively final:

~~~java
BufferedReader reader = Files.newBufferedReader(path);
try (reader) {
    System.out.println(reader.readLine());
}
~~~

Trong Java 7 và Java 8, phần khai báo tài nguyên trong `try-with-resources` phải tạo biến tài nguyên ngay trong cặp ngoặc. Ví dụ dùng biến đã khai báo trước như trên cần Java 9 trở lên.

## 4. Ngoại lệ khi đóng tài nguyên

Cách xử lý ngoại lệ là một lý do quan trọng để ưu tiên `try-with-resources`.

- Nếu thân `try` kết thúc bình thường và `close()` ném ngoại lệ, ngoại lệ từ `close()` sẽ được truyền ra ngoài.
- Nếu thân `try` đã ném ngoại lệ và `close()` cũng ném ngoại lệ, ngoại lệ từ thân `try` vẫn là ngoại lệ chính. Ngoại lệ khi đóng được gắn vào danh sách suppressed exceptions.
- Nếu có nhiều tài nguyên và nhiều lần đóng cùng ném ngoại lệ, Java vẫn đóng các tài nguyên đã khởi tạo thành công. Khi thân `try` đã có ngoại lệ chính, các ngoại lệ từ thao tác đóng được thêm vào ngoại lệ chính theo thứ tự đóng.
- Nếu việc khởi tạo một tài nguyên về sau thất bại, các tài nguyên đã khởi tạo thành công trước đó vẫn được đóng theo thứ tự ngược.

Có thể đọc các ngoại lệ bị suppressed bằng `Throwable.getSuppressed()`. Điều này giúp giữ lại lỗi ban đầu, đồng thời không làm mất thông tin về lỗi dọn dẹp tài nguyên.

Đây là ưu điểm so với cách đóng thủ công trong `finally`: nếu việc đóng trong `finally` ném ngoại lệ, nó có thể che mất ngoại lệ quan trọng phát sinh từ phần xử lý chính nếu mã không quản lý cả hai trường hợp một cách cẩn thận.

## 5. Ví dụ hoàn chỉnh: thứ tự đóng và suppressed exceptions

Lưu chương trình sau vào tệp `Main.java`. Lớp `DemoResource` triển khai `Closeable`, do đó cũng đáp ứng `AutoCloseable`. Nó in tên khi đóng và cố ý ném `IOException` để minh họa cách Java lưu ngoại lệ phụ.

~~~java
import java.io.Closeable;
import java.io.IOException;

final class DemoResource implements Closeable {
    private final String name;
    private final boolean failOnClose;

    DemoResource(String name, boolean failOnClose) {
        this.name = name;
        this.failOnClose = failOnClose;
    }

    @Override
    public void close() throws IOException {
        System.out.println("close " + name);
        if (failOnClose) {
            throw new IOException("close-" + name);
        }
    }
}

public class Main {
    public static void main(String[] args) {
        try (DemoResource first = new DemoResource("first", true);
             DemoResource second = new DemoResource("second", true)) {
            System.out.println("body");
            throw new IllegalStateException("body-failed");
        } catch (Exception e) {
            System.out.println("primary: " + e.getMessage());
            for (Throwable suppressed : e.getSuppressed()) {
                System.out.println("suppressed: " + suppressed.getMessage());
            }
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~text
javac Main.java
java Main
~~~

Đầu ra chuẩn chính xác:

~~~text
body
close second
close first
primary: body-failed
suppressed: close-second
suppressed: close-first
~~~

Giải thích:

1. Thân `try` in `body` rồi ném `IllegalStateException` với thông điệp `body-failed`. Đây là ngoại lệ chính.
2. Java đóng `second` trước vì nó được khai báo sau `first`. Phương thức `close()` in tên rồi ném `IOException`; ngoại lệ đó được lưu dưới dạng suppressed.
3. Java tiếp tục đóng `first` và cũng lưu ngoại lệ từ thao tác đóng này dưới dạng suppressed.
4. Khối `catch` nhận ngoại lệ chính và in từng ngoại lệ từ `getSuppressed()`.

Trong ví dụ này, cả hai thao tác đóng đều thất bại nhưng việc đóng tài nguyên còn lại vẫn được thực hiện. Ngoại lệ ban đầu từ thân `try` không bị thay thế bởi các ngoại lệ đóng.

## 6. Ví dụ sử dụng API I/O thật

Các lớp như `BufferedReader`, `InputStream` và `OutputStream` triển khai hợp đồng đóng phù hợp với I/O. Khi đọc tệp văn bản, có thể dùng `Files.newBufferedReader`:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReadFirstLine {
    public static void main(String[] args) {
        Path path = Path.of("input.txt");
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            System.out.println(reader.readLine());
        } catch (IOException e) {
            System.err.println("Không thể đọc tệp: " + e.getMessage());
        }
    }
}
~~~

Lưu đoạn mã vào `ReadFirstLine.java`, tạo tệp `input.txt` trong thư mục chạy với nội dung mong muốn, rồi biên dịch bằng `javac ReadFirstLine.java` và chạy bằng `java ReadFirstLine`. Chương trình in dòng đầu tiên của tệp; nếu tệp rỗng, `readLine()` trả về `null`. Nếu việc mở, đọc hoặc đóng tệp phát sinh `IOException`, khối `catch` xử lý lỗi.

## 7. Lỗi thường gặp và cách tránh

| Lỗi | Nguyên nhân | Cách xử lý |
|---|---|---|
| Đối tượng không dùng được trong `try-with-resources` | Lớp không triển khai `AutoCloseable` hoặc một interface con như `Closeable` | Triển khai hợp đồng đóng tài nguyên phù hợp |
| Lớp triển khai `Closeable` khai báo `close() throws Exception` | Checked exception rộng hơn `IOException` không tương thích với hợp đồng | Dùng `IOException` hoặc loại hẹp hơn |
| Giả định tài nguyên được đóng theo thứ tự khai báo | Java đóng theo thứ tự ngược | Tài nguyên khởi tạo cuối cùng được đóng trước |
| Chỉ kiểm tra ngoại lệ chính | Lỗi trong `close()` có thể được lưu thành suppressed exception | Khi cần chẩn đoán, kiểm tra `getSuppressed()` |
| Đóng thủ công lần nữa mà không hiểu hợp đồng | Không phải mọi `AutoCloseable` đều bắt buộc an toàn khi đóng nhiều lần | Thiết kế quyền sở hữu rõ ràng; tuân thủ hợp đồng của từng lớp |
| Bỏ qua ngoại lệ đóng | Việc giải phóng tài nguyên có thể thất bại | Ghi log hoặc xử lý ngoại lệ theo chính sách của ứng dụng khi cần |

## 8. Quy trình áp dụng

1. Xác định tài nguyên nào cần được giải phóng và xem lớp đó có triển khai `AutoCloseable` hoặc `Closeable` hay không.
2. Chọn `try-with-resources` để bảo đảm tài nguyên đã khởi tạo được đóng tự động.
3. Khai báo tài nguyên theo thứ tự phù hợp với quan hệ phụ thuộc; Java sẽ đóng theo thứ tự ngược.
4. Xử lý loại ngoại lệ được khai báo bởi API. Với tài nguyên I/O, thường là `IOException`; API khác có thể dùng loại ngoại lệ chuyên biệt.
5. Khi cần phân tích lỗi, giữ lại ngoại lệ chính và xem cả suppressed exceptions.
6. Chỉ tự quản lý vòng đời bằng cơ chế khác khi có yêu cầu rõ ràng về quyền sở hữu hoặc thời điểm đóng.

## Ghi nhớ

`AutoCloseable` là hợp đồng tổng quát cho tài nguyên có thể đóng; `Closeable` mở rộng nó và tập trung vào I/O với `IOException`. `try-with-resources` gọi `close()` tự động, đóng tài nguyên theo thứ tự ngược với lúc khởi tạo và giữ lại lỗi đóng dưới dạng suppressed khi thân `try` đã ném ngoại lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-apis-006">Runnable và Callable: hợp đồng công việc và kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Runnable và Callable: hợp đồng công việc và kết quả

Trong Java, `Runnable` và `Callable<V>` đều mô tả một đơn vị công việc có thể được thực thi sau đó. Điểm khác biệt cốt lõi nằm ở hợp đồng: `Runnable` thực hiện hành động và không trả kết quả; `Callable<V>` trả về một giá trị kiểu `V` và có thể khai báo checked exception. Chọn đúng kiểu giúp API thể hiện rõ kết quả cần lấy, cách xử lý lỗi và cách quản lý công việc bất đồng bộ.

## 1. Hai hợp đồng khác nhau

| Đặc điểm | `Runnable` | `Callable<V>` |
| --- | --- | --- |
| Phương thức trừu tượng | `void run()` | `V call() throws Exception` |
| Giá trị trả về | Không có | Có, kiểu `V` |
| Khai báo checked exception | `run()` không được khai báo checked exception | `call()` có thể khai báo `Exception` hoặc ngoại lệ checked cụ thể |
| Thuộc package | `java.lang` | `java.util.concurrent` |
| Ví dụ sử dụng | Ghi log, gửi tín hiệu, cập nhật một trạng thái | Tính toán kết quả, đọc dữ liệu, truy vấn cần trả giá trị |
| Hỗ trợ trực tiếp bởi `Thread` | Có | Không; có thể bọc trong `FutureTask<V>` |
| Dùng với `ExecutorService` | `execute(Runnable)`, `submit(Runnable)` | `submit(Callable<V>)` |

Cả hai đều là functional interface, vì mỗi kiểu có một phương thức trừu tượng. Vì vậy, có thể dùng lambda hoặc method reference khi truyền một hành vi vào API.

~~~java
Runnable notify = () -> System.out.println("Da hoan tat");
Callable<Integer> calculate = () -> 6 * 7;
~~~

Lambda thứ nhất không trả kết quả; lambda thứ hai trả về số nguyên. Dòng `Callable<Integer>` cũng cho biết rõ kiểu dữ liệu mà bên gọi sẽ nhận lại.

## 2. Chạy Runnable với Thread

`Thread` nhận một `Runnable`. Khi gọi `start()`, JVM khởi động luồng thực thi mới và gọi `run()` trong luồng đó. Gọi trực tiếp `run()` chỉ thực thi phương thức như một lời gọi thông thường trên luồng hiện tại, không khởi động luồng mới.

~~~java
public class RunnableThreadDemo {
    public static void main(String[] args) throws InterruptedException {
        Runnable work = () -> System.out.println("Work is running");

        Thread thread = new Thread(work);
        thread.start();
        thread.join();
        System.out.println("Main continues");
    }
}
~~~

Chạy:

~~~bash
javac RunnableThreadDemo.java
java RunnableThreadDemo
~~~

Kết quả:

~~~text
Work is running
Main continues
~~~

`join()` khiến luồng main đợi luồng công việc kết thúc, nên thứ tự đầu ra trong ví dụ này được xác định. Nếu bỏ `join()`, không nên giả định thứ tự giữa thông báo của main và thông báo của luồng công việc.

Không cần tạo lớp riêng cho mỗi tác vụ nhỏ; lambda thường đủ rõ ràng. Tuy vậy, nếu công việc cần nhiều trạng thái hoặc nhiều phương thức, một lớp triển khai `Runnable` có thể giúp tổ chức mã tốt hơn.

## 3. Callable trả kết quả và có thể ném ngoại lệ

`Callable<V>` phù hợp khi công việc cần tạo ra kết quả. Phương thức `call()` trả về kiểu `V` và có thể khai báo checked exception, chẳng hạn `IOException`. Nhưng bản thân `Callable` không phải một luồng và cũng không tự chạy khi được khởi tạo; nó cần một thành phần thực thi gọi nó.

Ví dụ dưới đây minh họa việc gọi `Callable` qua `ExecutorService`:

~~~java
import java.util.concurrent.Callable;

public class CallableDefinitionDemo {
    public static void main(String[] args) throws Exception {
        Callable<Integer> calculate = () -> 6 * 7;
        System.out.println(calculate.call());
    }
}
~~~

Chạy:

~~~bash
javac CallableDefinitionDemo.java
java CallableDefinitionDemo
~~~

Kết quả:

~~~text
42
~~~

Ví dụ này gọi `call()` trực tiếp trên luồng hiện tại chỉ để minh họa hợp đồng trả về. Trong ứng dụng bất đồng bộ, thường nên giao tác vụ cho một executor và lấy kết quả thông qua `Future<V>`.

Với `Runnable`, checked exception phải được xử lý bên trong `run()` hoặc chuyển thành unchecked exception. Với `Callable`, ngoại lệ checked có thể được khai báo trên `call()`; khi tác vụ chạy bất đồng bộ qua executor, bên lấy kết quả thường nhận ngoại lệ tác vụ qua `ExecutionException` từ `Future.get()`.

## 4. ExecutorService và Future

`ExecutorService` tách việc mô tả công việc khỏi cách bố trí luồng. Thay vì tự tạo một `Thread` cho mỗi tác vụ, ta nộp công việc cho executor để nó lên lịch và thực thi. Phương thức `submit(...)` trả về một `Future`, đại diện cho trạng thái và kết quả của tác vụ.

- `submit(Runnable)` trả về `Future<?>`. Khi tác vụ hoàn tất bình thường, `get()` trả về `null`, vì `Runnable` không tạo giá trị.
- `submit(Callable<V>)` trả về `Future<V>`. Khi tác vụ hoàn tất bình thường, `get()` trả về giá trị kiểu `V`.
- `execute(Runnable)` chỉ nộp một hành động và không trả về `Future`. Dùng khi không cần lấy kết quả hoặc theo dõi hoàn tất qua một `Future`.
- `submit(Runnable, result)` cho phép gắn một giá trị kết quả do bên gọi cung cấp. `Future.get()` trả về giá trị đó sau khi `Runnable` hoàn tất bình thường; đây không phải giá trị do `run()` trả về.

### Ví dụ chạy được: so sánh hai kiểu

Tệp `RunnableCallableDemo.java`:

~~~java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RunnableCallableDemo {
    public static void main(String[] args)
            throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Future<?> action = pool.submit(
                () -> System.out.println("Runnable: saved")
            );
            Future<Integer> calculation = pool.submit(() -> 20 + 22);
            Future<String> status = pool.submit(() -> "done");

            action.get();
            System.out.println("Callable result: " + calculation.get());
            System.out.println("Callable status: " + status.get());
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Chạy bằng Java 17 trở lên:

~~~bash
javac RunnableCallableDemo.java
java RunnableCallableDemo
~~~

Kết quả:

~~~text
Runnable: saved
Callable result: 42
Callable status: done
~~~

Executor chỉ có một luồng worker, các tác vụ được thực hiện theo thứ tự nộp. Lời gọi `action.get()` đợi tác vụ đầu hoàn tất trước khi in các kết quả sau, vì vậy đầu ra minh họa có thứ tự ổn định. Khối `finally` bảo đảm yêu cầu executor ngừng nhận tác vụ mới ngay cả khi có lỗi khi lấy kết quả.

`shutdown()` cho phép các tác vụ đã nộp hoàn tất. Nó không có nghĩa là main đợi executor kết thúc ngay tại dòng gọi; trong ví dụ, các lời gọi `get()` đã đợi từng tác vụ. Với quy trình cần kiểm soát thời gian chờ, có thể dùng thêm `awaitTermination(...)`.

## 5. Hiểu đúng Future.get() và lỗi

`Future.get()` thường chặn luồng gọi cho đến khi tác vụ hoàn tất. Có ba tình huống quan trọng:

1. **Thành công:** với `Callable<V>`, `get()` trả kết quả; với `Runnable` đã nộp qua `submit`, `get()` trả `null` nếu không truyền giá trị kết quả riêng.
2. **Tác vụ thất bại:** `get()` ném `ExecutionException`; gọi `getCause()` để xem ngoại lệ gốc do tác vụ gây ra.
3. **Luồng đang đợi bị ngắt:** `get()` có thể ném `InterruptedException`. Việc này báo rằng luồng chờ bị ngắt, không tự động có nghĩa tác vụ đã bị hủy.

Ví dụ xử lý kết quả theo kiểu cụ thể:

~~~java
Future<Integer> result = pool.submit(() -> {
    if (System.nanoTime() < 0) {
        throw new IllegalStateException("Unexpected state");
    }
    return 42;
});

try {
    int value = result.get();
    System.out.println(value);
} catch (ExecutionException e) {
    System.err.println("Task failed: " + e.getCause());
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Waiting thread interrupted", e);
}
~~~

Đoạn trên là fragment để minh họa xử lý lỗi; nó giả định biến `pool` đã được khởi tạo và quản lý vòng đời ở bên ngoài. Trong mã thực tế, không nên bắt rồi bỏ qua `InterruptedException`. Nếu không thể ném ngoại lệ lên tầng gọi, khôi phục cờ ngắt bằng `Thread.currentThread().interrupt()` để phần còn lại của chương trình có thể nhận biết việc ngắt.

Có thể dùng `get(timeout, unit)` khi không muốn đợi vô thời hạn; phương thức này còn có thể ném `TimeoutException`. Hết thời gian chờ không tự hủy công việc. Nếu muốn yêu cầu hủy, gọi `cancel(true)` trên Future và thiết kế tác vụ để phản ứng hợp lý với ngắt.

## 6. Hủy tác vụ và vòng đời executor

`Future` cung cấp `isDone()`, `isCancelled()` và `cancel(...)`. Hủy thành công có nghĩa Future được đánh dấu hủy; nó không bảo đảm mọi mã đang chạy dừng tức thì. Tham số `true` yêu cầu ngắt luồng thực thi nếu tác vụ đang chạy, nhưng tác vụ phải hợp tác bằng cách kiểm tra trạng thái ngắt hoặc phản ứng với `InterruptedException`.

Executor cũng cần được quản lý vòng đời:

- `shutdown()`: ngừng nhận tác vụ mới và cho tác vụ đã nộp tiếp tục hoàn tất.
- `shutdownNow()`: cố gắng ngắt tác vụ đang chạy và trả về danh sách tác vụ đang chờ chưa bắt đầu; đây là yêu cầu dừng tốt nhất có thể, không phải bảo đảm dừng tức thì.
- `awaitTermination(timeout, unit)`: chờ executor kết thúc trong một khoảng thời gian tối đa.
- Sau khi executor đã shutdown, các lần nộp tác vụ mới thường thất bại với `RejectedExecutionException`.

Không nên tạo một executor mới cho từng tác vụ nhỏ mà không quản lý việc đóng nó. Trong ứng dụng thật, executor có thể được dùng chung theo vòng đời của thành phần hoặc ứng dụng; nơi sở hữu executor phải có trách nhiệm shutdown đúng lúc.

## 7. Checked exception: lý do Callable hữu ích

Xét API đọc tệp `Files.readString(Path)`, có thể ném `IOException`. Một `Callable<String>` có thể mô tả trực tiếp công việc đọc và cho phép khai báo ngoại lệ checked:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

Callable<String> readText = () -> Files.readString(Path.of("input.txt"));
~~~

Đây là một fragment, không phải tệp độc lập; nó cần đặt trong phương thức hoặc lớp thích hợp. Khi nộp cho executor, hãy lấy kết quả qua `Future<String>`. Nếu đọc tệp thất bại, `Future.get()` sẽ báo `ExecutionException` với nguyên nhân thường là `IOException`.

Với `Runnable`, không thể để `IOException` thoát trực tiếp khỏi `run()` như một checked exception. Có thể bắt và xử lý ngay trong lambda, hoặc bọc thành `UncheckedIOException`, nhưng cách đó làm thay đổi hợp đồng ngoại lệ mà bên gọi nhìn thấy. Nếu kết quả và ngoại lệ là một phần tự nhiên của tác vụ, `Callable<V>` thường phù hợp hơn.

## 8. Callable và Runnable liên hệ với nhau thế nào?

Một `Callable<V>` không thể truyền trực tiếp vào `new Thread(...)`, vì constructor của `Thread` nhận `Runnable`. Nếu cần lấy kết quả trong khi vẫn dùng cơ chế `Runnable`, Java cung cấp `FutureTask<V>`, triển khai `RunnableFuture<V>` và có thể được khởi tạo từ một `Callable<V>`:

~~~java
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

Callable<Integer> work = () -> 21 * 2;
FutureTask<Integer> task = new FutureTask<>(work);
Thread thread = new Thread(task);
thread.start();

Integer answer = task.get();
System.out.println(answer);
~~~

Fragment trên cần đặt trong phương thức có xử lý hoặc khai báo `InterruptedException` và `ExecutionException`. Kết quả là:

~~~text
42
~~~

`FutureTask` vừa có thể được thực thi như một `Runnable`, vừa giữ kết quả và trạng thái Future. Trong phần lớn ứng dụng, `ExecutorService` vẫn là lựa chọn thuận tiện hơn vì cung cấp cơ chế quản lý luồng và hàng đợi tác vụ.

## 9. Các nhầm lẫn phổ biến

- **Chờ kết quả từ Runnable:** `Runnable` không trả dữ liệu qua `run()`. Nếu cần giá trị do tác vụ tạo ra, hãy dùng `Callable<V>` và `Future<V>`, hoặc thiết kế cách trao đổi kết quả rõ ràng.
- **Nghĩ rằng Callable tự chạy:** tạo lambda hoặc đối tượng `Callable` chỉ mô tả công việc. Phải gọi `call()` trực tiếp hoặc nộp nó cho một thành phần thực thi.
- **Nhầm submit với execute:** `submit` trả `Future`; `execute` thì không. Nếu dùng `submit` và không gọi `get()`, lỗi của tác vụ có thể không được quan sát ở nơi gọi.
- **Bỏ qua ExecutionException:** ngoại lệ phát sinh trong tác vụ được bọc trong `ExecutionException` khi lấy kết quả. Hãy kiểm tra `getCause()` để chẩn đoán.
- **Cho rằng cancel(true) dừng tác vụ ngay:** ngắt luồng là cơ chế yêu cầu hợp tác. Công việc không kiểm tra trạng thái ngắt hoặc không phản ứng với ngoại lệ ngắt có thể tiếp tục chạy.
- **Gọi run() rồi kỳ vọng bất đồng bộ:** `run()` là lời gọi phương thức thông thường; dùng `start()` để khởi động luồng mới.
- **Quên đóng executor:** một executor vẫn có thể giữ tài nguyên luồng. Xác định rõ thành phần nào sở hữu nó và khi nào cần shutdown.
- **Chia sẻ trạng thái có thể thay đổi mà không đồng bộ:** Runnable và Callable chỉ mô tả công việc. Chúng không tự làm cho dữ liệu dùng chung trở nên thread-safe.

## 10. Quy tắc lựa chọn nhanh

- Chọn **Runnable** khi chỉ cần mô tả một hành động không trả kết quả, đặc biệt với `Thread`, `Executor.execute` hoặc khi kết quả được truyền qua một cơ chế khác.
- Chọn **Callable&lt;V&gt;** khi tác vụ tạo ra kết quả kiểu `V`, cần khai báo checked exception, hoặc khi API bất đồng bộ cần trả kết quả qua `Future<V>`.
- Chọn **`ExecutorService.submit(...)`** khi cần theo dõi trạng thái, lấy kết quả, hủy hoặc quan sát lỗi qua Future.
- Dùng **execute(Runnable)** khi chỉ cần giao việc cho executor và không cần Future. Cần có chiến lược xử lý lỗi phù hợp vì không có Future để gọi `get()`.
- Chọn **FutureTask&lt;V&gt;** khi cần một đối tượng vừa là Runnable vừa giữ kết quả Future, chẳng hạn khi tích hợp Callable với API chỉ nhận Runnable.

Điểm cần nhớ: `Runnable` trả lời câu hỏi “cần thực hiện hành động nào?”, còn `Callable<V>` trả lời “cần thực hiện công việc nào và kết quả kiểu `V` là gì?”. Khi chạy bất đồng bộ, `Future` là cầu nối để kiểm tra trạng thái, nhận kết quả và quan sát lỗi của công việc đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-apis-007">Serializable, Cloneable và giới hạn của marker interface</a>

<details>
<summary>Xem chi tiết</summary>

# Serializable, Cloneable và giới hạn của marker interface

Trong Java, **marker interface** là interface không khai báo phương thức, được dùng để đánh dấu một kiểu có ý nghĩa đặc biệt đối với thư viện hoặc runtime. Hai ví dụ kinh điển trong JDK là `java.io.Serializable` và `java.lang.Cloneable`.

Cả hai đều không có phương thức trừu tượng mà lớp phải triển khai. Tuy vậy, chúng có tác dụng khác nhau:

- **Serializable** đánh dấu rằng đối tượng thuộc kiểu có thể tham gia cơ chế tuần tự hóa đối tượng Java (*Java object serialization*), với điều kiện các phần dữ liệu cần tuần tự hóa đáp ứng quy tắc tương ứng.
- **Cloneable** đánh dấu rằng cơ chế `Object.clone()` được phép tạo bản sao của instance. Interface này không tự cung cấp một phương thức `clone()` công khai cho người dùng lớp gọi.

Marker interface có thể truyền tải thông tin về một kiểu, nhưng bản thân chúng không đảm bảo rằng mọi thao tác liên quan đều an toàn, đầy đủ hoặc phù hợp về mặt nghiệp vụ. Cần hiểu hợp đồng cụ thể mà JDK gắn với từng marker.

## 1. Marker interface hoạt động như thế nào?

Một interface thông thường thường mô tả hành vi thông qua các phương thức. Ví dụ, `Runnable` khai báo `run()`; một lớp triển khai `Runnable` phải cung cấp hành vi đó.

Marker interface không khai báo phương thức như vậy:

~~~java
public interface Serializable {
    // Không khai báo phương thức.
}

public interface Cloneable {
    // Không khai báo phương thức.
}
~~~

Đây chỉ là minh họa cấu trúc; trong ứng dụng, hãy dùng các interface thật từ JDK thay vì tự định nghĩa lại chúng.

Cơ chế sử dụng marker phụ thuộc vào API kiểm tra kiểu. Chẳng hạn, một API có thể kiểm tra `value instanceof SomeMarker` hoặc kiểm tra thông tin kiểu bằng reflection, rồi áp dụng một quy tắc riêng. Khi một lớp triển khai marker, nó tham gia hợp đồng mà API đó định nghĩa.

Điều này khác với việc một interface khai báo phương thức mà mọi lớp triển khai phải cung cấp. Marker không tự tạo ra phương thức, không tự sao chép dữ liệu, không tự xác thực trạng thái và cũng không đảm bảo mọi đối tượng liên quan đều tuân thủ các điều kiện khác của API.

## 2. Serializable: tuần tự hóa đối tượng Java

`java.io.Serializable` là marker interface dùng với cơ chế tuần tự hóa đối tượng Java. `ObjectOutputStream` có thể ghi đồ thị đối tượng có thể tuần tự hóa; `ObjectInputStream` có thể dựng lại đối tượng từ luồng dữ liệu tương ứng.

Ví dụ tối thiểu:

~~~java
import java.io.Serializable;

final class UserProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;

    UserProfile(String username) {
        this.username = username;
    }

    public String username() {
        return username;
    }
}
~~~

Khai báo `implements Serializable` không buộc lớp phải viết một phương thức như `serialize()`. Thay vào đó, các lớp thư viện đọc metadata và trạng thái đối tượng theo quy tắc serialization của Java.

### Các điểm quan trọng của Serializable

- Các trường instance không được đánh dấu `transient` thường thuộc trạng thái mặc định được tuần tự hóa. Những đối tượng được tham chiếu bởi các trường này cũng cần đáp ứng quy tắc tuần tự hóa, trừ khi cơ chế tùy chỉnh xử lý chúng.
- Nếu một đối tượng chứa trường tham chiếu tới một đối tượng không thể tuần tự hóa, thao tác ghi có thể ném `NotSerializableException`.
- Trường `static` thuộc về lớp, không phải trạng thái instance được lưu theo cách mặc định của đối tượng.
- Trường `transient` được bỏ qua bởi cơ chế tuần tự hóa mặc định. Khi đọc lại, trường đó nhận giá trị mặc định nếu không có logic tùy chỉnh khởi tạo lại nó.
- `serialVersionUID` là định danh phiên bản serialization. Khai báo rõ ràng có thể giúp kiểm soát khả năng tương thích giữa các phiên bản lớp. Giá trị này không tự bảo đảm rằng thay đổi cấu trúc lớp vẫn tương thích.
- Lớp có thể định nghĩa các phương thức đặc biệt như `private void writeObject(ObjectOutputStream out)` hoặc `private void readObject(ObjectInputStream in)` để tùy chỉnh serialization theo quy ước của Java. Các phương thức này không phải phương thức được khai báo trong `Serializable`; chữ ký và quy tắc của chúng phải chính xác.

Một đối tượng được tuần tự hóa có thể tham chiếu tới các đối tượng khác, vì vậy cần xem xét toàn bộ đồ thị đối tượng chứ không chỉ lớp ngoài cùng. Ví dụ, nếu `UserProfile` chứa một đối tượng `Address`, việc lớp `UserProfile` triển khai `Serializable` chưa đủ nếu `Address` không thể được tuần tự hóa và trường đó không được xử lý riêng.

## 3. Chương trình Java hoàn chỉnh: serialize và deserialize

Tạo tệp **SerializableDemo.java**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SerializableDemo {
    static final class UserProfile implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String username;
        private final String city;

        UserProfile(String username, String city) {
            this.username = username;
            this.city = city;
        }

        String username() {
            return username;
        }

        String city() {
            return city;
        }
    }

    public static void main(String[] args)
            throws IOException, ClassNotFoundException {
        UserProfile original = new UserProfile("linh", "Hanoi");

        byte[] bytes;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(original);
            out.flush();
            bytes = buffer.toByteArray();
        }

        UserProfile restored;
        try (ByteArrayInputStream buffer =
                     new ByteArrayInputStream(bytes);
             ObjectInputStream in = new ObjectInputStream(buffer)) {
            restored = (UserProfile) in.readObject();
        }

        System.out.println(restored.username() + ", " + restored.city());
        System.out.println("Serialized bytes: " + (bytes.length > 0));
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac SerializableDemo.java
java SerializableDemo
~~~

Kết quả stdout:

~~~text
linh, Hanoi
Serialized bytes: true
~~~

Chương trình ghi đối tượng vào một luồng byte trong bộ nhớ, sau đó đọc nó trở lại. Nó minh họa cơ chế tuần tự hóa; ứng dụng thực tế có thể ghi ra tệp hoặc một luồng khác, nhưng cần quản lý vòng đời dữ liệu và kiểm tra tính tương thích giữa các phiên bản.

Ví dụ trên sử dụng các trường `String`, vốn có thể tuần tự hóa. Nếu thêm một trường là đối tượng tùy chỉnh, hãy xem xét kiểu của đối tượng đó và các trường mà nó chứa.

## 4. Giới hạn và rủi ro của Serializable

Triển khai `Serializable` không đồng nghĩa với việc mọi trạng thái của đối tượng được lưu theo đúng cách ứng dụng mong muốn.

**Trạng thái bị bỏ qua:** trường `transient` không được ghi theo cơ chế mặc định. Nếu trường đó giữ trạng thái cần thiết để đối tượng hoạt động, lớp cần có cách khôi phục hoặc tái tạo trạng thái hợp lệ.

**Tương thích phiên bản:** việc thêm, xóa hoặc đổi kiểu trường và thay đổi cấu trúc lớp có thể ảnh hưởng quá trình đọc dữ liệu cũ. `serialVersionUID` giúp kiểm soát một phần việc nhận diện phiên bản, nhưng không thay thế việc thiết kế, kiểm thử và quản lý định dạng dữ liệu.

**Bất biến của đối tượng:** khi đọc đối tượng, không nên mặc định rằng mọi điều kiện khởi tạo trong constructor thông thường đều đã được thực thi theo cùng cách như lúc gọi `new`. Cần hiểu quy tắc serialization, cách khôi phục trạng thái và những hook tùy chỉnh có liên quan.

**An toàn dữ liệu:** không nên deserialize dữ liệu không đáng tin cậy bằng cơ chế Java native serialization. Đối tượng được khôi phục có thể kích hoạt các đường xử lý phức tạp thông qua đồ thị đối tượng và mã thư viện. Nếu cần nhận dữ liệu từ bên ngoài, ưu tiên định dạng dữ liệu có schema rõ ràng, xác thực đầu vào và cơ chế chuyển đổi có kiểm soát.

**Không phải định dạng trao đổi bền vững mặc định:** Java serialization thường gắn chặt với mô hình lớp Java và quy tắc tương thích của nó. Với API công khai, dữ liệu cần dùng giữa nhiều ngôn ngữ hoặc lưu trữ lâu dài, định dạng như JSON, Protocol Buffers hoặc một schema dữ liệu rõ ràng có thể phù hợp hơn, tùy yêu cầu.

Chỉ triển khai `Serializable` khi ứng dụng thực sự cần cơ chế này và đã xem xét trạng thái, tương thích, bảo mật cùng vòng đời dữ liệu.

## 5. Cloneable: hợp đồng với Object.clone()

`java.lang.Cloneable` cũng là marker interface, nhưng mục đích của nó rất khác. Nó cho biết rằng `Object.clone()` có thể tạo bản sao của instance đó mà không ném `CloneNotSupportedException` chỉ vì lớp không đánh dấu khả năng clone.

Interface `Cloneable` không khai báo phương thức `clone()`. Phương thức `clone()` trong `Object` là `protected`, vì vậy mã bên ngoài thường không thể gọi `clone()` trực tiếp trên mọi đối tượng. Nếu muốn cung cấp API clone, lớp thường phải tự ghi đè phương thức với mức truy cập phù hợp.

Ví dụ:

~~~java
class Counter implements Cloneable {
    private int value;

    Counter(int value) {
        this.value = value;
    }

    int value() {
        return value;
    }

    @Override
    public Counter clone() {
        try {
            return (Counter) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
~~~

Ở đây, `Counter` triển khai `Cloneable` và cung cấp một phương thức `clone()` công khai. Lệnh `super.clone()` gọi cơ chế sao chép từ `Object`. Với lớp này, giá trị `int` được sao chép trực tiếp nên bản sao có giá trị ban đầu giống đối tượng gốc.

Việc bắt `CloneNotSupportedException` rồi ném `AssertionError` là lựa chọn thường dùng trong ví dụ khi lớp đã triển khai `Cloneable` và lời gọi `super.clone()` được kỳ vọng thành công. Trong thiết kế thực tế, hãy xác định API sao chép và cách xử lý lỗi một cách nhất quán.

## 6. Cloneable không tự tạo ra API clone

Có ba việc riêng biệt cần phân biệt:

1. **Triển khai marker:** lớp khai báo `implements Cloneable`.
2. **Thực hiện sao chép:** mã gọi `super.clone()` hoặc tự tạo một đối tượng mới và sao chép trạng thái.
3. **Cung cấp API cho người dùng:** lớp quyết định có khai báo phương thức sao chép công khai hay không, cùng kiểu trả về và hợp đồng của phương thức đó.

Chỉ thực hiện bước thứ nhất không có nghĩa là người gọi có thể viết `someObject.clone()` từ mã ở bất kỳ đâu. Phương thức `Object.clone()` vẫn là `protected`, và `Cloneable` không khai báo phương thức để thay đổi điều đó.

Nếu lớp không triển khai `Cloneable` nhưng gọi `super.clone()`, cơ chế của `Object.clone()` sẽ ném `CloneNotSupportedException`. Nếu lớp triển khai `Cloneable` nhưng không ghi đè hoặc cung cấp phương thức clone thích hợp, mã sử dụng lớp vẫn có thể không có cách gọi clone phù hợp.

## 7. Shallow copy và deep copy

Điểm giới hạn lớn nhất của `Object.clone()` là nó tạo **shallow copy** (bản sao nông). Các trường primitive được sao chép giá trị; các trường tham chiếu được sao chép giá trị tham chiếu. Các đối tượng mà những tham chiếu đó trỏ tới không tự động được nhân bản.

Ví dụ, nếu một đối tượng `Person` có trường `Address address`, sau khi clone:

- `person != copy` thường là `true`: hai tham chiếu trỏ tới hai instance ngoài khác nhau.
- `person.address == copy.address` là `true`: cả hai instance cùng tham chiếu một đối tượng địa chỉ.
- Nếu địa chỉ có trạng thái mutable, thay đổi thông qua một tham chiếu sẽ được nhìn thấy khi truy cập địa chỉ từ đối tượng còn lại.

**Deep copy** (bản sao sâu) đòi hỏi sao chép thêm các đối tượng mutable bên trong theo một quy tắc rõ ràng. Không có một thao tác clone tổng quát nào tự biết nên sao chép sâu mọi trường, chia sẻ đối tượng bất biến hay giữ lại các tài nguyên dùng chung. Lớp cần xác định hợp đồng sao chép phù hợp.

## 8. Ví dụ hoàn chỉnh: Cloneable và giới hạn shallow copy

Tạo tệp **MarkerInterfacesDemo.java**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class MarkerInterfacesDemo {
    static final class Address implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String city;

        Address(String city) {
            this.city = city;
        }
    }

    static final class Person implements Serializable, Cloneable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final Address address;

        Person(String name, Address address) {
            this.name = name;
            this.address = address;
        }

        @Override
        public Person clone() {
            try {
                return (Person) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        Person deepCopy() {
            return new Person(name, new Address(address.city));
        }
    }

    public static void main(String[] args)
            throws IOException, ClassNotFoundException {
        Person original = new Person("An", new Address("Hanoi"));
        Person shallow = original.clone();
        Person deep = original.deepCopy();

        System.out.println("Clone is a distinct object: "
                + (original != shallow));
        System.out.println("Address is shared after shallow clone: "
                + (original.address == shallow.address));
        System.out.println("Address is independent after deep copy: "
                + (original.address != deep.address));

        byte[] bytes;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(original);
            out.flush();
            bytes = buffer.toByteArray();
        }

        Person restored;
        try (ByteArrayInputStream buffer =
                     new ByteArrayInputStream(bytes);
             ObjectInputStream in = new ObjectInputStream(buffer)) {
            restored = (Person) in.readObject();
        }

        System.out.println("Deserialized person: "
                + restored.name + ", " + restored.address.city);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac MarkerInterfacesDemo.java
java MarkerInterfacesDemo
~~~

Kết quả stdout chính xác:

~~~text
Clone is a distinct object: true
Address is shared after shallow clone: true
Address is independent after deep copy: true
Deserialized person: An, Hanoi
~~~

Trong ví dụ này, `Person.clone()` sử dụng `super.clone()`, nên `address` được sao chép như một tham chiếu và vẫn trỏ đến cùng một instance `Address`. Phương thức `deepCopy()` chủ động tạo một `Address` mới, thể hiện cách lớp định nghĩa bản sao sâu theo mô hình của nó.

Cả `Person` lẫn `Address` đều triển khai `Serializable`, vì vậy đồ thị đối tượng dùng trong ví dụ có thể được ghi bằng cơ chế mặc định. `serialVersionUID` được khai báo cho cả hai lớp để thể hiện chủ đích quản lý phiên bản serialization.

## 9. Những hạn chế của Cloneable và Object.clone()

- **Sao chép nông:** tham chiếu tới các đối tượng mutable vẫn được chia sẻ nếu lớp không tự sao chép chúng.
- **Không có phương thức trong interface:** `Cloneable` không định nghĩa chữ ký `clone()`, nên không cung cấp một API sao chép thống nhất để mọi mã có thể gọi qua kiểu `Cloneable`.
- **Hợp đồng khó hiểu:** người gọi thường không biết bản sao là nông hay sâu, trường nào được chia sẻ, hoặc điều kiện nào được giữ nguyên nếu tài liệu không nêu rõ.
- **Xử lý kế thừa phức tạp:** một lớp con có thể thêm trường mutable. Phương thức clone kế thừa từ lớp cha có thể chưa sao chép sâu những trường mới đó, khiến lớp con cần ghi đè và hoàn thiện hợp đồng sao chép.
- **Constructor không được dùng theo cách khởi tạo thông thường:** `Object.clone()` tạo bản sao theo cơ chế của Java thay vì gọi constructor của lớp đó như khi dùng `new`. Vì vậy, không nên dựa vào constructor để tái lập bất biến khi clone.
- **Tài nguyên bên ngoài:** sao chép tham chiếu đến file handle, socket, khóa, kết nối hoặc tài nguyên có vòng đời riêng thường không tạo ra một tài nguyên độc lập hợp lệ.

Do các giới hạn này, nhiều API hiện đại chọn constructor sao chép, factory method hoặc một phương thức có tên rõ như `copy()` thay vì phơi bày `clone()`. Cách đó cho phép xác định chính xác trường nào được sao chép, trường nào được chia sẻ và cách khởi tạo trạng thái.

## 10. Chọn Serializable, Cloneable hay một cách khác?

| Nhu cầu | Lựa chọn có thể phù hợp | Điều cần làm rõ |
|---|---|---|
| Ghi và đọc đồ thị đối tượng bằng Java native serialization | `Serializable` | Các trường, `transient`, tương thích phiên bản, dữ liệu không đáng tin cậy |
| Cho phép `Object.clone()` tạo bản sao | `Cloneable` cùng API clone phù hợp | Shallow/deep copy, trường mutable, hợp đồng của lớp con |
| Sao chép một đối tượng theo quy tắc nghiệp vụ | Constructor sao chép hoặc `copy()` | Trường nào sao chép sâu, trường nào chia sẻ |
| Trao đổi dữ liệu giữa dịch vụ hoặc ngôn ngữ khác nhau | DTO cùng định dạng dữ liệu có schema | Xác thực, phiên bản schema, biểu diễn dữ liệu |
| Đánh dấu khả năng cho một API tùy chỉnh | Marker interface riêng chỉ khi hợp đồng thực sự phù hợp | Ý nghĩa marker, cách API kiểm tra và hành vi khi không có marker |

Marker interface có thể hữu ích khi một API cần nhận biết một khả năng hoặc điều kiện kiểu. Tuy nhiên, nếu cần hành vi có thể gọi, một interface với phương thức thường diễn đạt hợp đồng rõ hơn. Nếu cần sao chép có kiểm soát, một phương thức `copy()` hoặc constructor sao chép thường làm rõ ý định hơn `Cloneable`.

## 11. Lỗi thường gặp và cách khắc phục

- **Cho rằng Serializable tự ghi được mọi trường:** kiểm tra toàn bộ đồ thị đối tượng và xử lý những trường không thể tuần tự hóa.
- **Cho rằng `transient` được khôi phục tự động:** thiết kế cách khởi tạo lại trường nếu ứng dụng cần giá trị khác giá trị mặc định.
- **Xem `serialVersionUID` là bảo đảm tương thích:** kiểm thử việc đọc dữ liệu cũ và quản lý thay đổi cấu trúc lớp.
- **Deserialize dữ liệu không đáng tin cậy:** tránh native serialization cho đầu vào không tin cậy; ưu tiên định dạng và bộ giải mã có kiểm soát.
- **Cho rằng implements Cloneable là đã có phương thức clone công khai:** cung cấp API sao chép rõ ràng nếu người gọi cần dùng nó.
- **Cho rằng clone luôn sao chép sâu:** kiểm tra mọi trường tham chiếu và quyết định rõ đối tượng nào cần được sao chép.
- **Không xem xét lớp con khi clone:** xác định hợp đồng sao chép qua cây kế thừa và kiểm tra các trường mutable được thêm ở lớp con.
- **Dùng clone cho tài nguyên có vòng đời độc lập:** định nghĩa quy trình mở/tạo tài nguyên mới thay vì sao chép tham chiếu một cách máy móc.

## 12. Quy trình thiết kế và kiểm thử

1. Xác định nhu cầu thật: tuần tự hóa dữ liệu, sao chép đối tượng hay đánh dấu khả năng cho một API.
2. Nếu chọn `Serializable`, rà soát các trường instance, các đối tượng được tham chiếu, trường `transient`, phiên bản lớp và nguồn dữ liệu.
3. Nếu chọn `Cloneable`, quyết định API công khai và hợp đồng shallow/deep copy trước khi triển khai.
4. Với sao chép sâu, liệt kê các trường mutable và xác định trường nào phải tạo bản sao mới, trường nào có thể chia sẻ.
5. Viết kiểm thử xác nhận cả giá trị lẫn danh tính tham chiếu; hai đối tượng có giá trị bằng nhau không nhất thiết phải chia sẻ cùng instance.
6. Kiểm thử serialization/deserialization trên dữ liệu đại diện và phiên bản cần hỗ trợ, đồng thời tránh đọc dữ liệu không đáng tin cậy.
7. Tài liệu hóa giới hạn, các trường bị bỏ qua và cách khôi phục trạng thái để người gọi hiểu chính xác hợp đồng.

`Serializable` và `Cloneable` minh họa cách marker interface truyền tải một ý nghĩa đặc biệt trong JDK mà không cần khai báo phương thức. Nhưng marker chỉ là tín hiệu cho một cơ chế cụ thể: Serializable không bảo đảm mọi dữ liệu đều tương thích hay an toàn, còn Cloneable không tự cung cấp API sao chép sâu. Thiết kế tốt cần nêu rõ hợp đồng, quản lý trạng thái và kiểm thử những giới hạn đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-apis-008">Đọc Javadoc về yêu cầu triển khai, phương thức tùy chọn và default method</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc Javadoc về yêu cầu triển khai, phương thức tùy chọn và default method

Javadoc của một API Java mô tả cách gọi API và những điều mà người triển khai phải bảo đảm. Khi đọc interface trong JDK, đừng chỉ nhìn chữ ký phương thức: cần biết phương thức nào bắt buộc phải triển khai, phương thức nào là tùy chọn, implementation mặc định làm gì, và điều kiện nào khiến lời gọi ném exception.

Ba khái niệm liên quan nhưng không đồng nghĩa:

- **Yêu cầu triển khai** (*implementation requirements/specification*) mô tả điều mà implementation phải làm hoặc phải giữ đúng.
- **Phương thức tùy chọn** (*optional operation*) là thao tác mà một implementation được phép không hỗ trợ theo hợp đồng API. Khi không hỗ trợ, API thường quy định ném `UnsupportedOperationException`.
- **Default method** là phương thức instance trong interface có phần thân mặc định. Lớp triển khai có thể kế thừa hành vi đó hoặc override nó.

Một phương thức có thể vừa là default method vừa là optional operation, nhưng hai khái niệm này độc lập: có default method không tự động có nghĩa là thao tác tùy chọn.

## 1. Đọc Javadoc theo thứ tự nào?

Khi gặp một kiểu hoặc phương thức trong JDK, hãy đọc các phần sau:

1. **Mô tả ngắn và mô tả đầy đủ:** phương thức giải quyết vấn đề gì, có side effect hay không, và hợp đồng chung là gì.
2. **Chữ ký và modifier:** phương thức là abstract, default, static hay phương thức của class; kiểu trả về, tham số và generic type là gì.
3. **`@param` và `@return`:** đầu vào có được phép `null` không, giá trị trả về có thể là `null` không, kết quả có phải bản sao hay view không, và trạng thái nào được bảo đảm.
4. **`@throws`:** exception được ném trong điều kiện nào. Cần phân biệt điều kiện lỗi đầu vào với exception báo rằng implementation không hỗ trợ thao tác.
5. **Implementation Requirements / `@implSpec`:** hợp đồng hoặc yêu cầu dành cho người viết implementation, đặc biệt khi API cung cấp default method.
6. **Implementation Note / `@implNote`:** thông tin về cách implementation cụ thể được xây dựng. Không mặc nhiên xem chi tiết này là bảo đảm cho mọi implementation.
7. **API Note / `@apiNote`:** ngữ cảnh và lời khuyên sử dụng giúp người gọi hiểu cách dùng API.
8. **`@since`, `@see` và liên kết kế thừa:** phiên bản API xuất hiện, các phương thức liên quan và hợp đồng được kế thừa từ interface hoặc superclass.

Chữ ký cho biết mã có thể gọi gì; Javadoc cho biết lời gọi có ý nghĩa gì và những bảo đảm nào được phép dựa vào. Khi hợp đồng ghi rõ điều kiện hỗ trợ, mutability, thứ tự, tính nhất quán hoặc exception, hãy dựa vào hợp đồng đó thay vì đoán từ tên phương thức.

## 2. `@implSpec`, `@implNote` và `@apiNote`

Các block tag này giúp tách biệt những loại thông tin khác nhau trong Javadoc:

| Tag | Dùng để đọc điều gì? | Cách áp dụng |
| --- | --- | --- |
| `@implSpec` | Yêu cầu hoặc hợp đồng mà implementation phải tuân thủ; thường được hiển thị thành phần **Implementation Requirements**. | Dùng khi triển khai hoặc override API; không được vi phạm hành vi đã quy định. |
| `@implNote` | Ghi chú về chi tiết triển khai cụ thể, chẳng hạn cách một implementation thực hiện công việc. | Dùng để hiểu implementation đó; tránh giả định chi tiết là đặc tính chung của interface. |
| `@apiNote` | Ghi chú hữu ích cho người dùng API, ví dụ cách sử dụng hoặc bối cảnh thiết kế. | Dùng để hiểu cách gọi API đúng mục đích. |

Ví dụ Javadoc cho một default method có thể ghi:

```java
/**
 * Removes all entries.
 *
 * @implSpec The default implementation throws
 *           UnsupportedOperationException.
 * @throws UnsupportedOperationException if clearing is not supported
 */
default void clear() {
    throw new UnsupportedOperationException("clear");
}
```

`@implSpec` giải thích hành vi mặc định mà người triển khai cần hiểu khi kế thừa hoặc override phương thức. `@throws` mô tả điều kiện exception. Nếu một implementation override `clear()`, người viết cần đọc toàn bộ hợp đồng để không vô tình phá vỡ điều mà caller được phép trông đợi.

Đừng hiểu `@implNote` như một cam kết chung. Chẳng hạn, ghi chú “implementation này lưu phần tử trong `ArrayList`” không cho phép caller phụ thuộc vào `ArrayList` nếu kiểu API không công bố điều đó.

## 3. Phương thức bắt buộc và phương thức tùy chọn

### Phương thức bắt buộc triển khai

Phương thức abstract trong interface chỉ khai báo hợp đồng, không có phần thân mặc định. Một class concrete triển khai interface phải cung cấp implementation cho các abstract method mà nó chưa thừa hưởng implementation hợp lệ; nếu chưa làm được, class phải được khai báo `abstract`.

Ví dụ:

```java
interface Encoder {
    byte[] encode(String value);
}
```

Class concrete thực hiện `Encoder` phải định nghĩa `encode(String)`, trừ khi phương thức đã được thừa hưởng từ một superclass theo cách hợp lệ. Javadoc của `encode` vẫn cần được đọc để biết cách xử lý `null`, charset, lỗi và ý nghĩa của mảng byte trả về.

### Phương thức tùy chọn

Javadoc gọi một thao tác là **optional operation** khi implementation được phép không hỗ trợ thao tác đó. Caller không thể suy ra rằng mọi object thuộc cùng interface đều hỗ trợ mọi operation chỉ từ việc phương thức xuất hiện trong API.

Một ví dụ JDK là `Collection.add(E)`: tài liệu API mô tả đây là optional operation và quy định `UnsupportedOperationException` nếu collection không hỗ trợ thêm phần tử. Một collection không sửa đổi được có thể cho phép đọc nhưng từ chối thao tác thay đổi. Caller cần biết collection đang dùng có hỗ trợ thao tác cần thiết hay không, hoặc xử lý exception theo hợp đồng phù hợp.

Điểm cần nhớ:

- “Optional” mô tả khả năng hỗ trợ thao tác theo hợp đồng API, không có nghĩa caller được phép bỏ qua mọi quy tắc của phương thức.
- Một optional operation có thể được khai báo là abstract; implementation vẫn phải cung cấp một method body nhưng có thể báo không hỗ trợ bằng exception theo hợp đồng.
- Một API cũng có thể dùng default method ném `UnsupportedOperationException` làm hành vi mặc định cho thao tác tùy chọn.
- Không phải mọi `UnsupportedOperationException` đều chứng minh thao tác là optional trong toàn bộ API; hãy đọc Javadoc của đúng phương thức và kiểu cụ thể.

### `UnsupportedOperationException` khác với lỗi đầu vào

`IllegalArgumentException` thường báo đối số không hợp lệ theo hợp đồng. `UnsupportedOperationException` thường báo object hoặc implementation không hỗ trợ thao tác được yêu cầu. Hai exception có ý nghĩa khác nhau; không nên dùng chúng thay thế tùy tiện hoặc bắt tất cả exception rồi giả vờ thao tác thành công.

## 4. Default method hoạt động ra sao?

Từ Java 8, interface có thể khai báo phương thức instance với từ khóa `default` và cung cấp phần thân:

```java
interface Named {
    String name();

    default String displayName() {
        return "[" + name() + "]";
    }
}
```

Class triển khai `Named` phải cung cấp `name()` nếu chưa có implementation hợp lệ được thừa hưởng. Nó có thể dùng trực tiếp `displayName()` hoặc override phương thức đó. Default method giúp interface bổ sung hành vi chung mà không bắt mỗi class phải viết lại cùng một phần thân.

Default method vẫn là phương thức instance: nó được gọi trên object và có thể gọi các phương thức khác trong hợp đồng. Phần thân mặc định không tự bảo đảm rằng mọi thao tác mà nó gọi đều được implementation hỗ trợ; người đọc vẫn phải kiểm tra Javadoc và các điều kiện của phương thức được gọi.

### Quy tắc chọn implementation

Khi cùng một chữ ký xuất hiện ở class và các interface, hãy nhớ những nguyên tắc chính:

1. **Implementation của class ưu tiên hơn default method của interface.** Nếu class hoặc superclass có phương thức instance phù hợp, nó được ưu tiên theo quy tắc của Java.
2. **Interface cụ thể hơn có thể ưu tiên interface cha.** Nếu một interface con override default method của interface cha, implementation kế thừa qua interface con được ưu tiên.
3. **Hai default method xung đột từ các interface không có quan hệ cụ thể hơn** có thể buộc class phải override và chọn hành vi rõ ràng; nếu không giải quyết được xung đột, mã sẽ không biên dịch.
4. **Default method không thay thế abstract method bằng tên gọi chung chung.** Chỉ phương thức có phần thân `default` mới cung cấp implementation mặc định; static method của interface không được kế thừa như một instance method.

Ví dụ xung đột:

```java
interface A {
    default String label() { return "A"; }
}
interface B {
    default String label() { return "B"; }
}

class Combined implements A, B {
    @Override
    public String label() {
        return A.super.label();
    }
}
```

`Combined` phải xử lý xung đột bằng override; trong ví dụ, nó chủ động chọn phần thân từ `A`. Cú pháp `A.super.label()` chỉ dùng được khi quan hệ interface và method cho phép gọi trực tiếp như vậy.

## 5. Default method và optional operation không phải một khái niệm

Có thể phân biệt bằng hai câu hỏi:

- **Default method:** “Nếu class không override, Java dùng phần thân nào?”
- **Optional operation:** “Theo hợp đồng, implementation có được phép không hỗ trợ thao tác này không?”

Có bốn tình huống cần phân biệt:

| Trường hợp | Ý nghĩa |
| --- | --- |
| Abstract method, bắt buộc có hành vi | Class concrete phải cung cấp implementation và đáp ứng hợp đồng. |
| Abstract method, optional operation | Class concrete vẫn cung cấp method body, nhưng hợp đồng cho phép báo không hỗ trợ bằng cách đã quy định. |
| Default method, hành vi mặc định thông thường | Class có thể kế thừa hành vi và phải giữ đúng hợp đồng khi override. |
| Default method, optional operation | Hành vi mặc định có thể ném `UnsupportedOperationException`; class hỗ trợ thao tác có thể override. |

JDK `Collection.add(E)` là ví dụ hữu ích cho optional operation; `Iterator.remove()` minh họa một optional operation có default implementation ném `UnsupportedOperationException`. Khi đọc tài liệu, hãy tìm cả tuyên bố “optional operation” lẫn phần `@implSpec`/`@throws`, thay vì suy luận từ chữ `default` trong chữ ký.

## 6. Ví dụ Java 17+: implementation kế thừa và override thao tác tùy chọn

Ví dụ này tạo một interface có phương thức `clear()` mặc định ném `UnsupportedOperationException`. `AppendOnlyLog` kế thừa hành vi đó; `EditableLog` override `clear()` để hỗ trợ xóa. Javadoc mô tả rõ rằng thao tác là tùy chọn.

Lưu mã đầy đủ vào `JavadocContractDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class JavadocContractDemo {
    interface MessageLog {
        void append(String message);
        int size();

        /**
         * Removes all messages from this log.
         * This is an optional operation.
         *
         * @implSpec The default implementation throws
         *           UnsupportedOperationException.
         * @throws UnsupportedOperationException if clearing is not supported
         */
        default void clear() {
            throw new UnsupportedOperationException("clear");
        }
    }

    static final class AppendOnlyLog implements MessageLog {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void append(String message) {
            messages.add(message);
        }

        @Override
        public int size() {
            return messages.size();
        }
    }

    static final class EditableLog implements MessageLog {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void append(String message) {
            messages.add(message);
        }

        @Override
        public int size() {
            return messages.size();
        }

        @Override
        public void clear() {
            messages.clear();
        }
    }

    public static void main(String[] args) {
        MessageLog appendOnly = new AppendOnlyLog();
        appendOnly.append("one");
        appendOnly.append("two");
        System.out.println("append-only size before clear=" + appendOnly.size());
        try {
            appendOnly.clear();
        } catch (UnsupportedOperationException ex) {
            System.out.println("append-only clear unsupported");
        }

        MessageLog editable = new EditableLog();
        editable.append("one");
        editable.clear();
        System.out.println("editable size after clear=" + editable.size());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac JavadocContractDemo.java
java JavadocContractDemo
```

Stdout chính xác:

```text
append-only size before clear=2
append-only clear unsupported
editable size after clear=0
```

`AppendOnlyLog` không override `clear()`, nên lời gọi dùng phần thân default và ném `UnsupportedOperationException`. `EditableLog` override cùng phương thức nên thao tác xóa thành công. Cả hai vẫn là `MessageLog`, nhưng khả năng hỗ trợ thao tác khác nhau theo hợp đồng được mô tả. Trong API thật, caller nên đọc tài liệu và không giả định thao tác tùy chọn luôn thành công.

Ví dụ này minh họa cơ chế ngôn ngữ. Với API công khai, cần cân nhắc kỹ việc thêm thao tác tùy chọn vào interface và ghi rõ điều kiện hỗ trợ, side effect, tính an toàn khi gọi lặp lại và exception.

## 7. Thêm default method vào interface đã tồn tại: lợi ích và rủi ro

Default method cho phép tác giả thư viện bổ sung một hành vi chung mà nhiều class cũ có thể kế thừa, thay vì buộc mọi implementation viết lại ngay một method mới. Đây là lý do default method quan trọng với các interface của JDK và thư viện có nhiều implementation.

Tuy nhiên, thêm default method không phải lúc nào cũng hoàn toàn không có rủi ro:

- Một class đã có method cùng chữ ký có thể chọn method của class theo quy tắc ưu tiên, dẫn đến hành vi khác với dự đoán của tác giả interface.
- Khi một class triển khai nhiều interface, default method cùng chữ ký có thể tạo xung đột cần override.
- Hành vi mặc định mới có thể không phù hợp với invariant hoặc giới hạn của một implementation cũ, ngay cả khi mã vẫn biên dịch.
- Javadoc phải mô tả hợp đồng rõ ràng, bao gồm điều kiện hỗ trợ, exception và ảnh hưởng đến trạng thái.

Khi nâng cấp JDK hoặc thư viện, hãy xem tài liệu của phiên bản đang dùng và kiểm tra các implementation liên quan; đừng giả định default method luôn tự động phù hợp với mọi lớp hiện có.

## 8. Checklist khi đọc một API trong JDK

Trước khi dùng hoặc triển khai phương thức, hãy tự trả lời:

- Phương thức abstract, default hay static? Nếu default, phần thân mặc định làm gì?
- Javadoc có ghi “optional operation” không? Nếu có, exception nào được quy định khi không hỗ trợ?
- `@implSpec` có đặt ra điều kiện hoặc yêu cầu mà implementation phải tuân thủ không?
- `@implNote` chỉ mô tả chi tiết nội bộ nào? Chi tiết đó có thực sự là cam kết công khai không?
- Các `@param`, `@return` và `@throws` nói gì về `null`, mutability, trạng thái, kết quả và lỗi?
- Nếu override, implementation có giữ nguyên hợp đồng mà caller dựa vào không?
- Nếu nhiều interface cung cấp default method cùng chữ ký, quy tắc kế thừa nào áp dụng?
- Nếu nâng cấp thư viện, default method mới có tương thích về hành vi với implementation hiện tại không?

## Kết luận

Khi đọc Javadoc, hãy tách hợp đồng API khỏi chi tiết triển khai. `@implSpec` giúp hiểu yêu cầu dành cho implementation; `@implNote` cung cấp ghi chú kỹ thuật; “optional operation” cho biết một thao tác có thể không được hỗ trợ; `default` cho biết interface có sẵn phần thân instance để kế thừa. Đọc đồng thời chữ ký, mô tả, `@throws` và các ghi chú này sẽ giúp triển khai interface đúng hợp đồng và tránh giả định sai về hành vi của API trong JDK.

</details>

- [Quay lại đầu trang](#back-to-top)
