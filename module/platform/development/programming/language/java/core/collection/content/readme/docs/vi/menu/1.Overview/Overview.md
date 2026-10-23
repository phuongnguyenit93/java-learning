<a id="back-to-top"></a>

# Tổng quan

## Menu
- [Collection là gì và bài toán quản lý nhóm đối tượng](#overview-001)
- [Phân biệt Collection, Collections và Java Collections Framework](#overview-002)
- [Quan hệ Iterable, Collection, List, Set, Queue và Deque](#overview-003)
- [Vì sao Map thuộc framework nhưng không kế thừa Collection](#overview-004)
- [So sánh mảng với collection: kích thước, kiểu và thao tác](#overview-005)
- [Phân biệt thứ tự chèn, thứ tự duyệt, chỉ số và thứ tự sắp xếp](#overview-006)
- [Các tiêu chí chọn cấu trúc: trùng lặp, null, truy cập và đồng thời](#overview-007)
- [Kiến thức nền và trình tự học các nhóm collection](#overview-008)

## <a id="overview-001">Collection là gì và bài toán quản lý nhóm đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Collection là gì và bài toán quản lý nhóm đối tượng

Một chương trình thường phải làm việc với **một nhóm dữ liệu có liên quan**: danh sách công việc, giỏ hàng, người tham gia một sự kiện hoặc các yêu cầu đang chờ xử lý. Khi số lượng phần tử thay đổi, chương trình cần cách thêm, tìm, xóa, đếm và duyệt cả nhóm mà vẫn giữ được quy tắc dữ liệu mong muốn.

Trong Java, **collection** là cách biểu diễn một nhóm phần tử thành một đối tượng để quản lý và thao tác. Giao diện `Collection<E>` cung cấp hợp đồng chung cho nhiều kiểu tập hợp; những kiểu như `List`, `Set` và `Queue` bổ sung quy tắc sử dụng cụ thể.

Sau bài này, bạn có thể:

- Giải thích collection giải quyết bài toán gì.
- Phân biệt `Collection`, Java Collections Framework và `Collections`.
- Sử dụng các thao tác quản lý nhóm đối tượng trong một chương trình nhỏ.
- Đặt câu hỏi về thứ tự, trùng lặp và cách truy cập trước khi chọn kiểu tập hợp.

Các chương trình hoàn chỉnh trong bài dùng API có sẵn từ Java 17. Chúng đã được biên dịch với `--release 17` và chạy để kiểm tra kết quả.

## 1. Từ một đối tượng đến một nhóm đối tượng

Giả sử bạn viết ứng dụng quản lý công việc. Một biến có thể giữ một công việc:

```java
String task = "Đọc đề";
```

Đây là đoạn minh họa có thể đặt trong một phương thức. Khi có nhiều công việc, việc tạo các biến `task1`, `task2`, `task3` sẽ gây khó khăn:

- Số công việc thực tế chỉ biết khi chương trình chạy.
- Muốn tìm một công việc phải tự kiểm tra từng biến.
- Muốn xử lý tất cả công việc phải lặp lại các câu lệnh.
- Xóa một công việc khiến việc theo dõi các biến còn lại trở nên rối.
- Mỗi phần của chương trình có thể tự đặt ra một cách quản lý khác nhau.

Ta cần biểu diễn **cả nhóm** bằng một biến, rồi thao tác theo mục đích:

| Nhu cầu của chương trình | Thao tác mong muốn |
| --- | --- |
| Người dùng tạo công việc | Thêm phần tử |
| Người dùng tìm công việc đã có | Kiểm tra sự tồn tại |
| Người dùng bỏ một công việc | Xóa phần tử |
| Hiển thị số công việc hiện tại | Đếm phần tử |
| Hiển thị các công việc | Duyệt từng phần tử |
| Bắt đầu lại danh sách | Xóa toàn bộ phần tử |

Đây là bài toán quản lý nhóm đối tượng. Collection cung cấp mô hình dữ liệu và những thao tác có thể tái sử dụng cho bài toán đó.

### Quản lý nhóm còn bao gồm quy tắc của nhóm

Cùng có đầu vào `Lan, Minh, Lan`, hai yêu cầu sau sẽ cho kết quả khác nhau:

- **Lưu lượt đăng ký:** có ba lượt; hai lượt mang tên Lan vẫn phải được giữ.
- **Lưu người tham gia duy nhất:** nếu tên được dùng làm định danh trong ví dụ đơn giản này, chỉ còn hai người.

Vì vậy, chọn collection không chỉ là tìm một chỗ chứa được nhiều phần tử. Bạn còn phải quyết định phần tử có được trùng không, thứ tự có ý nghĩa không và phần tử sẽ được truy cập như thế nào.

Trong hệ thống thực tế, định danh người tham gia nên là một giá trị ổn định như mã người dùng; tên hiển thị có thể trùng.

## 2. Phân biệt các tên dễ nhầm

| Tên | Ý nghĩa |
| --- | --- |
| collection | Khái niệm một nhóm phần tử được quản lý cùng nhau |
| `Collection<E>` | Giao diện trong `java.util`, khai báo các thao tác chung cho nhiều kiểu tập hợp |
| Java Collections Framework | Hệ thống giao diện, lớp triển khai và công cụ phục vụ việc lưu trữ, thao tác trên nhóm dữ liệu |
| `Collections` | Lớp tiện ích trong `java.util`, cung cấp các phương thức tĩnh làm việc với collection |

Cách viết hoa có ý nghĩa. `Collection` là một giao diện; `Collections` là một lớp khác. Tên “Collections Framework” chỉ phạm vi rộng hơn một giao diện đơn lẻ.

Ký hiệu `E` trong `Collection<E>` đại diện cho kiểu phần tử. Ví dụ, `Collection<String>` biểu diễn một collection có các phần tử thuộc kiểu `String`.

### Collection và Map

`Map<K, V>` lưu quan hệ **khóa → giá trị**, chẳng hạn `mã công việc → thông tin công việc`. `Map` thuộc Java Collections Framework nhưng **không kế thừa `Collection`**.

Sự phân biệt này ảnh hưởng đến cách thiết kế:

- Nếu cần quản lý một nhóm công việc, hãy xét các kiểu collection.
- Nếu thường xuyên cần tìm công việc theo mã riêng, hãy xét mô hình khóa–giá trị của `Map`.

Một số góc nhìn dữ liệu của map có thể được biểu diễn bằng collection, nhưng điều đó không làm bản thân `Map` trở thành `Collection`.

## 3. Mảng giải quyết được gì?

Mảng cũng lưu nhiều phần tử và rất hữu ích khi số vị trí đã xác định. Tuy nhiên, độ dài của **một đối tượng mảng** không thay đổi sau khi được tạo.

Ví dụ minh họa trong một phương thức:

```java
String[] tasks = new String[3];
int used = 0;

tasks[used++] = "Đọc đề";
tasks[used++] = "Viết mã";
```

Ở thời điểm này:

- `tasks.length` là `3`: số vị trí của mảng.
- `used` là `2`: số công việc mà chương trình đang quản lý.
- Vị trí chưa được gán còn giữ giá trị mặc định `null`.

Nếu muốn thêm nhiều hơn ba công việc, bạn phải tạo mảng khác và chuyển dữ liệu. Nếu muốn xóa ở giữa, bạn phải tự quyết định có dồn phần tử và cập nhật `used` hay không.

Một lớp như `ArrayList` đóng gói phần việc quản lý cấu trúc lưu trữ đó. Chương trình có thể tập trung vào “thêm công việc” thay vì tự quản lý chỗ trống trong mảng.

| Đặc điểm | Mảng | Collection, tùy kiểu triển khai |
| --- | --- | --- |
| Số phần tử đang quản lý | Thường phải tự theo dõi nếu chỉ dùng một phần mảng | Hỏi qua `size()` |
| Khả năng thay đổi số phần tử | Cần tự tổ chức trên mảng hiện có hoặc thay mảng | Có thể được lớp triển khai hỗ trợ |
| Thêm, tìm, xóa theo đối tượng | Tự viết logic hoặc dùng công cụ phù hợp | Có hợp đồng thao tác chung |
| Kiểu phần tử | Hỗ trợ kiểu nguyên thủy và kiểu tham chiếu | Tham số kiểu dùng kiểu tham chiếu |
| Quy tắc thứ tự, trùng lặp | Theo cấu trúc mảng và logic tự viết | Phụ thuộc loại collection |

Không phải collection nào cũng cho phép thêm hoặc xóa. Ví dụ, danh sách tạo bởi `List.of(...)` không cho phép thay đổi cấu trúc. Cần đọc hợp đồng của đối tượng cụ thể thay vì suy ra “collection nào cũng tự tăng giảm được”.

Mảng vẫn là lựa chọn phù hợp khi mô hình dữ liệu cần số vị trí cố định hoặc cần lưu trực tiếp giá trị nguyên thủy. Collection có thêm chi phí và các quy tắc riêng; việc lựa chọn nên xuất phát từ bài toán.

## 4. Giao diện, lớp triển khai và phần tử

Xét khai báo:

```java
Collection<String> tasks = new ArrayList<>();
```

Có ba ý cần đọc được:

1. **`Collection<String>`** là kiểu của biến: phần chương trình sử dụng biến này dựa vào hợp đồng quản lý các phần tử `String`.
2. **`new ArrayList<>()`** tạo đối tượng thực tế: lớp `ArrayList` quyết định cách lưu trữ và những đặc tính cụ thể.
3. **`tasks`** giữ tham chiếu đến đối tượng collection; đối tượng đó quản lý các phần tử được thêm vào.

Không thể tạo đối tượng trực tiếp bằng `new Collection<>()` vì `Collection` là giao diện.

Khai báo theo giao diện giúp đoạn mã chỉ phụ thuộc vào những khả năng nó cần. Tuy nhiên, việc đổi lớp triển khai vẫn phải kiểm tra lại ý nghĩa dữ liệu: thay một danh sách cho phép trùng bằng một tập hợp loại trùng có thể làm thay đổi kết quả nghiệp vụ.

### Các nhóm giao diện thường gặp

| Nhóm | Câu hỏi dẫn tới lựa chọn |
| --- | --- |
| `Collection<E>` | Đoạn mã chỉ cần những thao tác chung như thêm, đếm, tìm hoặc duyệt? |
| `List<E>` | Có cần một chuỗi phần tử có thứ tự, truy cập theo chỉ số và thường cho phép trùng? |
| `Set<E>` | Có cần bảo đảm không trùng theo quy tắc của tập hợp? |
| `Queue<E>` | Có cần nhận và xử lý phần tử theo chính sách hàng đợi? |
| `Deque<E>` | Có cần thao tác ở cả hai đầu của nhóm? |
| `Map<K, V>` | Có cần liên kết và tra cứu giá trị theo khóa? |

`List`, `Set` và `Queue` nằm trong hệ thống kiểu của `Collection`; `Deque` mở rộng `Queue`. Bảng thể hiện vai trò chính, không liệt kê toàn bộ cây kế thừa của từng phiên bản Java.

Một `Queue` không nhất thiết luôn tuân theo thứ tự vào trước ra trước; chẳng hạn, hàng đợi ưu tiên có chính sách chọn phần tử riêng. Tương tự, giao diện `Collection` không hứa rằng mọi lớp triển khai đều có thứ tự duyệt giống nhau.

### Chỉ yêu cầu khả năng thực sự cần dùng

Nếu một phương thức chỉ duyệt các phần tử, nó không cần yêu cầu riêng `ArrayList`. Nếu một phương thức cần lấy phần tử bằng chỉ số, `Collection` chưa đủ vì giao diện này không khai báo `get(int)`; khi đó cần xem hợp đồng `List`.

`Collection` mở rộng `Iterable`, nên có thể được duyệt bằng vòng lặp for-each.

## 5. Chương trình hoàn chỉnh: quản lý công việc

Tạo tệp **`CollectionIntroDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collection;

public class CollectionIntroDemo {
    public static void main(String[] args) {
        Collection<String> tasks = new ArrayList<>();

        System.out.println("Ban đầu: " + tasks.size());

        tasks.add("Đọc đề");
        tasks.add("Viết mã");
        tasks.add("Kiểm thử");
        tasks.add("Viết mã");

        System.out.println("Sau khi thêm: " + tasks.size());
        System.out.println("Có Kiểm thử: " + tasks.contains("Kiểm thử"));
        System.out.println("Xóa Viết mã: " + tasks.remove("Viết mã"));
        System.out.println("Xóa Triển khai: " + tasks.remove("Triển khai"));
        System.out.println("Còn lại: " + tasks.size());

        for (String task : tasks) {
            System.out.println("- " + task);
        }

        tasks.clear();
        System.out.println("Sau clear, rỗng: " + tasks.isEmpty());
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```text
javac -encoding UTF-8 --release 17 CollectionIntroDemo.java
java -Dfile.encoding=UTF-8 CollectionIntroDemo
```

Kết quả stdout:

```text
Ban đầu: 0
Sau khi thêm: 4
Có Kiểm thử: true
Xóa Viết mã: true
Xóa Triển khai: false
Còn lại: 3
- Đọc đề
- Kiểm thử
- Viết mã
Sau clear, rỗng: true
```

### Đọc diễn biến của chương trình

1. Collection mới chưa có phần tử nên `size()` trả về `0`.
2. Bốn lần `add` tạo bốn phần tử. `ArrayList` cho phép trùng nên hai chuỗi `"Viết mã"` cùng tồn tại.
3. `contains("Kiểm thử")` kiểm tra sự tồn tại và trả về `true`.
4. `remove("Viết mã")` trên danh sách này xóa **một lần xuất hiện đầu tiên**, trả về `true`; một phần tử `"Viết mã"` vẫn còn.
5. `remove("Triển khai")` không tìm thấy phần tử để xóa nên trả về `false`.
6. Vòng lặp for-each duyệt ba phần tử còn lại theo thứ tự của `ArrayList`.
7. `clear()` loại bỏ toàn bộ phần tử khỏi collection, nên `isEmpty()` trả về `true`.

Biến có kiểu `Collection<String>`, nhưng kết quả về thứ tự và phần tử trùng trong ví dụ đến từ đối tượng `ArrayList` thực tế.

### Các thao tác vừa sử dụng

| Thao tác | Ý nghĩa trong ví dụ |
| --- | --- |
| `add(element)` | Thêm một phần tử |
| `size()` | Lấy số phần tử hiện có |
| `contains(element)` | Kiểm tra có phần tử phù hợp hay không |
| `remove(element)` | Xóa một phần tử phù hợp nếu có |
| `isEmpty()` | Kiểm tra collection không chứa phần tử nào |
| `clear()` | Loại bỏ toàn bộ phần tử |
| for-each | Lần lượt xử lý các phần tử |

Đây là hợp đồng sử dụng, không phải cam kết rằng mọi lớp đều hỗ trợ sửa đổi hay có cùng chi phí thực thi.

Với chuỗi trong ví dụ, việc so khớp giá trị dựa trên quy tắc `equals` của `String`. Khi dùng đối tượng tự định nghĩa, bạn cần thiết kế quy tắc bằng nhau phù hợp; đặc biệt, các tập hợp dùng bảng băm còn phụ thuộc sự nhất quán giữa `equals` và `hashCode`.

## 6. Cùng hợp đồng, khác quy tắc nhóm

Chương trình sau dùng một phương thức nhận `Collection<String>` để hiển thị hai nhóm có quy tắc khác nhau.

Tạo tệp **`GroupPolicyDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public class GroupPolicyDemo {
    public static void main(String[] args) {
        List<String> input = List.of("Lan", "Minh", "Lan");

        Collection<String> registrations = new ArrayList<>();
        Collection<String> uniquePeople = new LinkedHashSet<>();

        registrations.addAll(input);
        uniquePeople.addAll(input);

        printGroup("Lượt đăng ký", registrations);
        printGroup("Người tham gia", uniquePeople);
    }

    private static void printGroup(String label, Collection<String> values) {
        System.out.println(label + " (" + values.size() + "): " + values);
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 GroupPolicyDemo.java
java -Dfile.encoding=UTF-8 GroupPolicyDemo
```

Kết quả stdout:

```text
Lượt đăng ký (3): [Lan, Minh, Lan]
Người tham gia (2): [Lan, Minh]
```

### Vì sao kết quả khác nhau?

- `ArrayList` giữ cả ba lượt đăng ký, bao gồm hai lần `"Lan"`.
- `LinkedHashSet` giữ mỗi giá trị chuỗi một lần và duy trì thứ tự chèn, nên kết quả là `[Lan, Minh]`.
- `printGroup` chỉ cần `size()` và biểu diễn của nhóm để hiển thị, nên tham số `Collection<String>` đáp ứng nhu cầu.

Bài dùng `LinkedHashSet` để thứ tự đầu ra được xác định theo thứ tự chèn. Không nên đổi sang một kiểu set khác rồi mặc định rằng thứ tự in vẫn như cũ.

`List.of(...)` ở đây tạo danh sách đầu vào không thể sửa đổi. Chương trình chỉ đọc nó và dùng `addAll` để đưa các phần tử sang hai collection có khả năng sửa đổi khác. `addAll` thêm các phần tử; nó không biến hai nhóm đích thành góc nhìn tự cập nhật của danh sách đầu vào.

Tình huống này cho thấy: **hợp đồng chung giúp dùng lại mã thao tác, còn loại tập hợp quyết định các quy tắc của dữ liệu**.

## 7. Collection quản lý tham chiếu đến đối tượng

Với `Collection<String>`, các phần tử là tham chiếu tới đối tượng `String`. Với `Collection<Customer>`, các phần tử là tham chiếu tới đối tượng `Customer`.

Cần phân biệt hai loại thay đổi:

- **Thay đổi cấu trúc nhóm:** thêm, xóa hoặc thay thế phần tử theo khả năng của kiểu tập hợp.
- **Thay đổi đối tượng bên trong:** cập nhật dữ liệu của một đối tượng khả biến đang được tham chiếu.

Một collection không cho phép sửa đổi cấu trúc không tự làm cho các đối tượng khả biến bên trong trở nên bất biến. Thêm một đối tượng vào collection cũng không mặc nhiên tạo bản sao độc lập của đối tượng đó.

Nếu hai biến cùng tham chiếu tới một collection, thay đổi qua một biến có thể được quan sát qua biến còn lại. Vì vậy, khi truyền collection giữa các phần của chương trình, cần thống nhất bên nào có quyền sửa và bên nhận có cần một bản sao hay không.

Xóa một phần tử khỏi collection chỉ loại bỏ phần tử đó khỏi nhóm. Đối tượng có thể vẫn được tham chiếu từ nơi khác; lệnh xóa không phải lời yêu cầu hủy ngay đối tượng.

### Kiểu nguyên thủy và lớp bao

Không thể khai báo `Collection<int>`. Kiểu tham số của generics phải là kiểu tham chiếu, nên thường dùng `Collection<Integer>` để quản lý các số nguyên theo mô hình collection.

Java có thể tự đóng hộp giá trị `int` thành `Integer` trong những ngữ cảnh phù hợp. Khi lựa chọn giữa mảng số nguyên và collection số nguyên, cần tính đến sự khác biệt này thay vì coi hai mô hình lưu trữ là giống hệt nhau.

## 8. Những điều hợp đồng Collection chưa quyết định

Biết một đối tượng có kiểu `Collection<E>` vẫn chưa trả lời được mọi câu hỏi:

| Câu hỏi | Điều cần kiểm tra |
| --- | --- |
| Có cho phép phần tử trùng? | Quy tắc của kiểu tập hợp |
| Duyệt theo thứ tự nào? | Cam kết thứ tự của lớp triển khai |
| Có cho phép `null`? | Hợp đồng cụ thể của collection |
| Có thể thêm hoặc xóa? | Khả năng sửa đổi và thao tác tùy chọn |
| Có lấy bằng chỉ số được không? | Giao diện sử dụng có cung cấp thao tác đó không |
| Có an toàn khi nhiều luồng cùng dùng? | Cơ chế đồng thời hoặc đồng bộ hóa được quy định |
| Thao tác tìm hoặc xóa có nhanh không? | Cấu trúc lưu trữ, kích thước và mẫu sử dụng |

Ví dụ:

- `List.of(...)` không chấp nhận phần tử `null` và không hỗ trợ các thay đổi cấu trúc như `add`.
- Một collection khả biến thông thường không mặc nhiên an toàn cho nhiều luồng cùng sửa.
- `size()` là số phần tử; nó không phải thông tin về dung lượng bộ nhớ dự phòng bên trong.
- Các thao tác `contains` hoặc `remove` có thể có chi phí khác nhau giữa các lớp triển khai.

Nên xem giao diện là hợp đồng tối thiểu mà đoạn mã dựa vào, rồi kiểm tra các bảo đảm bổ sung của đối tượng thực tế.

## 9. Chọn collection theo bài toán

Có thể bắt đầu bằng một quy trình ngắn:

1. **Xác định một phần tử là gì.** Trong ứng dụng công việc, đó có thể là chuỗi mô tả hoặc một đối tượng `Task` có mã và trạng thái.
2. **Xác định quy tắc của nhóm.** Có được trùng, có cần thứ tự, có cần chọn phần tử ở đầu hay cần tra theo khóa?
3. **Xác định những thao tác chính.** Chủ yếu duyệt, tìm, lấy theo chỉ số, thêm cuối hay lấy phần tử để xử lý?
4. **Chọn giao diện phù hợp.** Dựa vào nhu cầu sử dụng thay vì chọn ngay tên một lớp quen thuộc.
5. **Chọn lớp triển khai.** Kiểm tra khả năng sửa đổi, thứ tự, giá trị hợp lệ, chi phí và yêu cầu đồng thời.
6. **Kiểm chứng bằng ví dụ nhỏ.** Dùng dữ liệu có phần tử trùng, nhóm rỗng và thao tác không tìm thấy.

Một số cách đặt bài toán:

| Bài toán | Hướng mô hình hóa ban đầu |
| --- | --- |
| Danh sách các công việc theo vị trí người dùng sắp xếp | `List` |
| Tập mã công việc đã hoàn thành, mỗi mã chỉ xuất hiện một lần | `Set` |
| Các công việc đang chờ được lấy ra xử lý | `Queue`, với chính sách lấy phần tử được xác định |
| Tìm thông tin công việc bằng mã | `Map` |

Đây là điểm xuất phát. Ví dụ, “danh sách công việc” trong ngôn ngữ tự nhiên chưa đủ để kết luận phải dùng `List`; nếu yêu cầu chính là tìm theo mã, cấu trúc khóa–giá trị có thể cần thiết hơn.

## 10. Lỗi thường gặp

### Nhầm Collection với ArrayList

`Collection` là giao diện rộng hơn một lớp triển khai. Những điều đúng với `ArrayList` như truy cập theo chỉ số hoặc thứ tự danh sách không thể áp dụng nguyên trạng cho mọi collection.

### Mặc định collection nào cũng thay đổi được

Lệnh `add` có mặt trong giao diện nhưng đối tượng cụ thể có thể không hỗ trợ thao tác đó và ném `UnsupportedOperationException`. Cần kiểm tra nguồn tạo collection và hợp đồng của nó.

### Dùng size như độ dài mảng hoặc dung lượng dự phòng

Mảng dùng thuộc tính `length`; collection dùng phương thức `size()`. Hai mô hình không có cùng cơ chế quản lý chỗ trống.

### Mặc định dữ liệu trùng sẽ tự bị loại

Ví dụ `ArrayList` giữ hai lần `"Viết mã"`. Nếu muốn duy nhất, hãy chọn mô hình và quy tắc so sánh phù hợp thay vì kỳ vọng thao tác `add` tự làm sạch dữ liệu.

### Vừa duyệt vừa tự ý thay đổi cấu trúc

Không nên thêm hoặc xóa trực tiếp theo một cách tùy ý trong lúc duyệt bằng for-each. Hành vi phụ thuộc loại collection và cơ chế duyệt; một số cách dùng có thể gây lỗi khi thay đổi đồng thời cấu trúc.

Ở bước học thao tác duyệt, hãy chọn cơ chế được hỗ trợ, chẳng hạn thao tác của iterator hoặc `removeIf` trên collection cho phép xóa. Việc một lần chạy chưa báo lỗi không chứng minh cách duyệt và sửa là hợp lệ.

### Chọn kiểu chỉ vì quen tên

Hãy viết ra quy tắc dữ liệu trước. Nếu yêu cầu “không được trùng” hoặc “tìm theo mã” chưa được đáp ứng, đổi một vòng lặp đẹp hơn cũng chưa giải quyết đúng bài toán.

## 11. Thực hành

### Bài tập 1: dự đoán trước khi chạy

Với `CollectionIntroDemo`:

1. Đổi chuỗi cần xóa từ `"Viết mã"` thành `"Chưa có"`.
2. Dự đoán giá trị boolean của lần xóa đó.
3. Dự đoán số phần tử và hai lần xuất hiện của `"Viết mã"` còn lại.
4. Chạy lại và đối chiếu với dự đoán.

Điểm cần kiểm chứng: một lần xóa không tìm thấy phải không làm giảm số phần tử; khả năng chứa phần tử trùng vẫn do loại tập hợp quyết định.

### Bài tập 2: làm rõ quy tắc duy nhất

Trong `GroupPolicyDemo`, đổi đầu vào thành `Lan, Minh, Lan, An, Minh`.

Trước khi chạy, hãy viết ra:

- Số lượt đăng ký.
- Số giá trị tên duy nhất.
- Thứ tự xuất hiện trong nhóm `LinkedHashSet`.

Với quy tắc đang dùng, hai nhóm cần có kích thước lần lượt là `5` và `3`; nhóm duy nhất có thứ tự `Lan, Minh, An`.

### Bài tập 3: chọn mô hình trước khi viết mã

Bạn cần quản lý các đơn hàng. Hãy chọn mô hình cho từng yêu cầu độc lập và giải thích:

- Giữ các lần khách thêm hàng theo thứ tự.
- Ghi nhận tập mã đơn đã gửi thông báo, không gửi trùng theo mã.
- Tra thông tin đơn hàng bằng mã đơn.
- Nhận các đơn đang chờ xử lý theo một chính sách rõ ràng.

Với mỗi trường hợp, nêu loại phần tử, yêu cầu về trùng lặp, thứ tự và thao tác chính. Sau đó mới chọn giao diện cùng lớp triển khai để làm ví dụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-002">Phân biệt Collection, Collections và Java Collections Framework</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt Collection, Collections và Java Collections Framework

Khi đọc mã Java, ba tên này thường xuất hiện gần nhau nhưng chỉ ba phạm vi khác nhau:

- **`Collection<E>`** là một interface mô tả một nhóm phần tử và những thao tác chung trên nhóm đó.
- **`Collections`** là một lớp tiện ích cung cấp các phương thức `static` để thao tác với collection hoặc tạo các đối tượng bao bọc.
- **Java Collections Framework (JCF)** là hệ thống các interface, lớp triển khai, thuật toán và hợp đồng liên quan đến việc tổ chức, truy cập và xử lý các nhóm dữ liệu.

Ví dụ, trong câu “dùng `Collections.sort` để sắp xếp một `ArrayList` thuộc Java Collections Framework”, `Collections` là nơi chứa thuật toán, `ArrayList` là đối tượng chứa dữ liệu, còn framework là toàn bộ hệ thống mà các thành phần đó tham gia.

## 1. Vì sao cần phân biệt ba tên?

Giả sử ứng dụng cần lưu danh sách người đăng ký và tạo một bản được sắp xếp để hiển thị. Có ba câu hỏi khác nhau:

1. **Đối tượng cần cung cấp khả năng gì?** Có thể hỏi số phần tử, duyệt qua và kiểm tra sự có mặt của một tên hay không? Đây là câu hỏi về hợp đồng, chẳng hạn `Collection<E>`.
2. **Đối tượng tổ chức dữ liệu bằng cách nào?** Dùng danh sách `ArrayList`, tập hợp `LinkedHashSet` hay một cấu trúc khác? Đây là lựa chọn lớp triển khai.
3. **Có công cụ dùng chung nào xử lý dữ liệu?** Chẳng hạn sắp xếp danh sách hoặc đếm số lần một tên xuất hiện bằng các phương thức của `Collections`.

Framework kết nối các lựa chọn này. Nhờ các hợp đồng thống nhất, mã xử lý không phải viết lại cho từng lớp lưu trữ.

| Tiêu chí | `Collection<E>` | `Collections` | Java Collections Framework |
| --- | --- | --- | --- |
| Bản chất | Interface có tham số kiểu `E` | Lớp tiện ích | Một hệ thống API và quy ước thiết kế |
| Câu hỏi trả lời | Một nhóm phần tử cung cấp những thao tác chung nào? | Dùng công cụ nào để xử lý hoặc bao bọc collection? | Những thành phần này phối hợp với nhau ra sao? |
| Cách xuất hiện trong mã | `Collection<String> names` | `Collections.sort(names)` khi `names` có kiểu phù hợp | Không phải một kiểu để khai báo biến |
| Có tự chứa dữ liệu cụ thể không? | Interface không cung cấp cấu trúc lưu trữ cụ thể | Không phải đối tượng chứa các phần tử của người dùng | Bao gồm nhiều lớp chứa dữ liệu |
| Có thể gọi `new` trực tiếp không? | Không thể khởi tạo trực tiếp một interface | Không dùng `new Collections()` | Không có lớp tên `JavaCollectionsFramework` để khởi tạo |
| Ví dụ liên quan | `List`, `Set`, `Queue` kế thừa `Collection` | `sort`, `frequency`, `unmodifiableList` | `ArrayList`, `HashSet`, `HashMap`, các interface và thuật toán |

Tên lớp có thêm chữ **s** là `Collections`. Sự khác nhau không chỉ là số ít và số nhiều trong tiếng Anh, mà là khác nhau giữa **interface**, **lớp tiện ích** và **toàn bộ framework**.

## 2. Collection<E>: hợp đồng cho một nhóm phần tử

### 2.1. Interface khác lớp triển khai

`java.util.Collection<E>` biểu diễn một nhóm phần tử có kiểu `E`. Nó kế thừa `Iterable<E>`, vì vậy một collection có thể cung cấp iterator và được duyệt bằng vòng lặp `for-each`.

Đoạn mã minh họa sau đặt bên trong một phương thức, với các import tương ứng từ `java.util`:

```java
Collection<String> names = new ArrayList<>();
names.add("Lan");
names.add("An");

System.out.println(names.size());       // 2
System.out.println(names.contains("An")); // true
```

Trong khai báo đầu tiên:

- `Collection<String>` là **kiểu của biến tham chiếu**: mã sử dụng biến chỉ dựa vào những khả năng mà kiểu này công bố.
- `ArrayList<>` là **lớp của đối tượng được tạo**: nó quyết định cách lưu trữ và thực hiện thao tác.
- `String` là **kiểu phần tử** trong chương trình này.

Interface đóng vai trò hợp đồng; đối tượng `ArrayList` thực hiện hợp đồng đó thông qua `List`.

Không thể viết `new Collection<String>()` như cách khởi tạo một lớp cụ thể. Có thể tự triển khai interface, nhưng trong mã thông thường nên bắt đầu từ lớp triển khai phù hợp đã có trong JDK.

### 2.2. Những khả năng chung

Các thao tác quen thuộc ở mức `Collection` gồm:

| Nhóm thao tác | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Hỏi về kích thước | `size()`, `isEmpty()` | Xem số phần tử và collection có rỗng không |
| Hỏi về nội dung | `contains(value)`, `containsAll(other)` | Kiểm tra sự có mặt của phần tử |
| Thêm hoặc xóa | `add(value)`, `remove(value)`, `clear()` | Yêu cầu thay đổi các phần tử |
| Xử lý nhiều phần tử | `addAll(other)`, `removeAll(other)`, `retainAll(other)` | Thao tác với một nhóm phần tử |
| Duyệt hoặc chuyển dạng | `iterator()`, `toArray()`, `stream()` | Cung cấp cách đi qua hoặc xử lý dữ liệu |

Bảng này giúp nhận diện phạm vi của interface; không có nghĩa mọi đối tượng đều hỗ trợ mọi thao tác thay đổi.

Chẳng hạn, `Collection` có phương thức `add`, nhưng một danh sách được tạo bằng `List.of(...)` không cho thêm phần tử. Lời gọi vẫn hợp lệ về mặt kiểu, rồi bị từ chối lúc chạy bằng `UnsupportedOperationException`.

Ngoài ra, chỉ nhìn kiểu `Collection<E>` chưa đủ để kết luận:

- Các phần tử có được giữ theo thứ tự thêm vào hay không.
- Có cho phần tử trùng lặp hoặc `null` hay không.
- Có thể sửa nội dung hay không.
- Có an toàn khi nhiều luồng đồng thời truy cập và sửa hay không.
- Chi phí tra cứu hoặc thêm phần tử là bao nhiêu.

Những bảo đảm đó cần đọc ở interface cụ thể hơn và lớp triển khai đang dùng. Hợp đồng và các thao tác tùy chọn được mô tả trong [API Collection của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html).

### 2.3. Kiểu tham chiếu quyết định phương thức có thể gọi

Ví dụ sau vẫn là đoạn mã bên trong phương thức:

```java
Collection<String> names = new ArrayList<>(List.of("Lan", "An"));

// names.get(0); // Không biên dịch: Collection không khai báo get(int).
```

Đối tượng thực sự là `ArrayList`, nhưng biến `names` được khai báo là `Collection`. Trình biên dịch kiểm tra lời gọi dựa trên kiểu của biến.

Nếu yêu cầu cần truy cập theo vị trí, có thể khai báo bằng `List<String>`:

```java
List<String> names = new ArrayList<>(List.of("Lan", "An"));
System.out.println(names.get(0)); // Lan
```

Không nên khai báo `Collection` trong mọi tình huống chỉ vì nó tổng quát. Kiểu phải thể hiện đúng những khả năng mà đoạn mã cần dùng.

## 3. Collections: lớp tiện ích, không phải nơi chứa phần tử

### 3.1. Gọi phương thức static

`java.util.Collections` cung cấp các thuật toán và phương thức tiện ích. Cách dùng điển hình là gọi qua tên lớp:

```java
List<Integer> scores = new ArrayList<>(List.of(30, 10, 20, 10));

Collections.sort(scores);
System.out.println(scores); // [10, 10, 20, 30]

System.out.println(Collections.frequency(scores, 10)); // 2
```

Đây là đoạn mã minh họa trong phương thức, không phải chương trình độc lập.

`Collections.sort(scores)` tác động lên danh sách `scores` được truyền vào; lớp `Collections` không trở thành chủ sở hữu của một danh sách riêng chứa các điểm số.

Không viết:

```java
// Collections<String> names;       // Sai: Collections không phải kiểu generic này.
// Collections tools = new Collections(); // Sai: không khởi tạo lớp tiện ích này.
```

Một phương thức của `Collections` có thể có tham số kiểu generic riêng. Điều đó khác với việc bản thân lớp có khai báo `Collections<E>`.

### 3.2. Mỗi phương thức có yêu cầu đầu vào riêng

Không phải phương thức nào trong `Collections` cũng nhận một `Collection` bất kỳ.

| Ví dụ | Đầu vào điển hình | Điểm cần nhớ |
| --- | --- | --- |
| `Collections.frequency(c, x)` | `Collection<?>` | Đếm số phần tử bằng giá trị cần tìm |
| `Collections.sort(list)` | `List` có phần tử so sánh được theo yêu cầu | Sắp xếp chính danh sách truyền vào |
| `Collections.reverse(list)` | `List` hỗ trợ thay phần tử | Đảo thứ tự trong danh sách |
| `Collections.binarySearch(list, key)` | `List` đã được sắp xếp phù hợp | Không dùng trên dữ liệu chưa sắp xếp rồi tin kết quả |
| `Collections.unmodifiableList(list)` | `List` | Tạo một view không cho sửa qua tham chiếu trả về |
| `Collections.unmodifiableMap(map)` | `Map` | Cho thấy tiện ích của lớp này còn phục vụ nhánh `Map` |

Vì vậy, nếu biến có kiểu `Collection<String>`, không thể truyền thẳng nó cho `Collections.sort` chỉ vì đối tượng hiện tại là `ArrayList`. Một cách rõ ràng là tạo bản danh sách:

```java
Collection<String> source = new ArrayList<>(List.of("Lan", "An"));

List<String> sorted = new ArrayList<>(source);
Collections.sort(sorted);

System.out.println(source); // [Lan, An]
System.out.println(sorted); // [An, Lan]
```

Đoạn này tạo một danh sách mới chứa các tham chiếu phần tử từ `source`. Việc đổi thứ tự của danh sách mới không đổi thứ tự của danh sách gốc.

Cũng không nên gọi một thuật toán sửa danh sách trên đối tượng không hỗ trợ thao tác đó. Chẳng hạn, `Collections.sort(List.of(3, 1, 2))` sẽ ném `UnsupportedOperationException`. Có thể tạo `new ArrayList<>(...)` trước khi cần sửa.

Các điều kiện và tác động cụ thể thuộc hợp đồng của từng phương thức trong [API Collections của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html).

## 4. Java Collections Framework: bức tranh lớn hơn

Framework không phải một interface lớn tên `Collection`, cũng không chỉ là các phương thức trong lớp `Collections`.

Có thể nhìn nó qua các nhóm thành phần:

1. **Interface** mô tả các mô hình và khả năng: `Collection`, `List`, `Set`, `Queue`, `Deque`, `Map`...
2. **Lớp triển khai** cung cấp cách tổ chức dữ liệu: `ArrayList`, `LinkedList`, `HashSet`, `LinkedHashSet`, `ArrayDeque`, `HashMap`...
3. **Thuật toán và tiện ích** xử lý hoặc bao bọc các cấu trúc đó, tiêu biểu là lớp `Collections`.
4. **Các hợp đồng chung** giúp các thành phần phối hợp: cách duyệt phần tử, ý nghĩa của phép so sánh, điều kiện sửa đổi và những bảo đảm mà từng kiểu công bố.

Điểm có ích là tách nhu cầu sử dụng khỏi một cách lưu trữ cố định. Một phương thức chỉ cần duyệt các tên có thể nhận `Collection<String>`; bên gọi có thể cung cấp một `ArrayList<String>` hoặc một `LinkedHashSet<String>`, miễn đáp ứng hợp đồng mà phương thức cần.

### 4.1. Sơ đồ quan hệ rút gọn

Sơ đồ dưới dùng các interface quen thuộc trong Java 17; đây không phải toàn bộ cây API:

```text
Iterable<E>
└── Collection<E>
    ├── List<E>          ví dụ: ArrayList
    ├── Set<E>           ví dụ: HashSet, LinkedHashSet
    └── Queue<E>
        └── Deque<E>     ví dụ: ArrayDeque

Map<K, V>               ví dụ: HashMap, LinkedHashMap
                        là một nhánh riêng trong framework

Collections             lớp tiện ích phục vụ các kiểu phù hợp
```

Đọc sơ đồ theo quan hệ kế thừa của **interface**. Các tên lớp ở bên phải là ví dụ, không phải mỗi lớp chỉ có thể thực hiện đúng một interface. Chẳng hạn, `LinkedList` thực hiện cả `List` và `Deque`.

`Iterable` nằm ở `java.lang`; `Collection`, `Collections` và nhiều kiểu liên quan nằm ở `java.util`. Một hệ thống API có thể trải trên nhiều package; framework không đồng nghĩa với một package hay một câu lệnh `import` duy nhất.

Tài liệu [Collections Framework Overview](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/doc-files/coll-overview.html) là điểm vào để xem toàn bộ các thành phần và quan hệ của JCF.

### 4.2. Map thuộc framework nhưng không phải Collection

`Map<K, V>` biểu diễn ánh xạ từ khóa sang giá trị. Nó không kế thừa `Collection<E>`.

Lý do cần phân biệt về mô hình: một collection được nhìn như một nhóm phần tử, trong khi map được thao tác chủ yếu bằng quan hệ **khóa → giá trị**. Với map, có những câu hỏi riêng như “giá trị ứng với khóa này là gì?” hoặc “khóa này đã có ánh xạ chưa?”.

Từ một map, ta có thể lấy các view dạng collection:

| Phương thức | Kiểu kết quả | Nội dung |
| --- | --- | --- |
| `keySet()` | `Set<K>` | Các khóa |
| `values()` | `Collection<V>` | Các giá trị; có thể có giá trị lặp |
| `entrySet()` | `Set<Map.Entry<K, V>>` | Các cặp khóa–giá trị |

Các view này gắn với map gốc. Chúng không phải bản sao độc lập chỉ vì có kiểu `Collection` hoặc `Set`; những thay đổi được hỗ trợ phải được hiểu theo hợp đồng view.

Không thể kết luận “`HashMap` không thuộc JCF” chỉ vì nó không triển khai `Collection`. Cũng không thể gán một `HashMap` vào biến `Collection`. Quan hệ chính xác được công bố trong [API Map của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

## 5. Chương trình hoàn chỉnh để kiểm chứng

Lưu nguyên chương trình sau vào tệp **CollectionsOverviewDemo.java**. Chương trình dùng API tương thích Java 17 trở lên:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CollectionsOverviewDemo {
    public static void main(String[] args) {
        Collection<String> names =
                new ArrayList<>(List.of("Lan", "An", "Lan"));
        names.add("Binh");

        System.out.println("Collection: " + names);
        System.out.println("Size: " + names.size());
        System.out.println("Contains Lan: " + names.contains("Lan"));

        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        System.out.println("Sorted copy: " + sorted);
        System.out.println("Original: " + names);
        System.out.println("Lan count: " + Collections.frequency(names, "Lan"));

        Set<String> unique = new LinkedHashSet<>(names);
        System.out.println("Set: " + unique);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : names) {
            counts.merge(name, 1, Integer::sum);
        }
        System.out.println("Map: " + counts);
        Collection<Integer> values = counts.values();
        System.out.println("Map values: " + values);

        List<String> readOnly = Collections.unmodifiableList(sorted);
        sorted.add("Vy");
        System.out.println("Read-only view: " + readOnly);

        try {
            readOnly.add("Hoa");
        } catch (UnsupportedOperationException ex) {
            System.out.println("View add: " + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp, với một JDK hỗ trợ biên dịch cho Java 17:

```shell
javac --release 17 -encoding UTF-8 CollectionsOverviewDemo.java
java CollectionsOverviewDemo
```

Stdout:

```text
Collection: [Lan, An, Lan, Binh]
Size: 4
Contains Lan: true
Sorted copy: [An, Binh, Lan, Lan]
Original: [Lan, An, Lan, Binh]
Lan count: 2
Set: [Lan, An, Binh]
Map: {Lan=2, An=1, Binh=1}
Map values: [2, 1, 1]
Read-only view: [An, Binh, Lan, Lan, Vy]
View add: UnsupportedOperationException
```

### Đọc kết quả theo ba tầng

- **Hợp đồng:** biến `names` có kiểu `Collection<String>`, nên có thể gọi `add`, `size`, `contains` và dùng `for-each`.
- **Lớp triển khai:** đối tượng ban đầu là `ArrayList`, vì vậy ví dụ giữ thứ tự thêm và có hai phần tử `"Lan"`. `LinkedHashSet` loại phần tử lặp và giữ thứ tự thêm của các phần tử còn lại trong ví dụ.
- **Tiện ích:** `Collections.sort` sắp xếp bản `sorted`; `Collections.frequency` đếm hai lần xuất hiện của `"Lan"`; `Collections.unmodifiableList` tạo view không cho sửa.
- **Framework:** tất cả những thành phần đó, cùng nhánh `Map`, phối hợp trong một chương trình mà không cần một đối tượng “framework” đứng ra điều khiển.

Hai lớp có tiền tố `LinkedHash` được chọn để thứ tự in trong ví dụ có thể dự đoán. Không nên thay chúng bằng `HashSet` hoặc `HashMap` rồi mặc định stdout vẫn có cùng thứ tự.

## 6. View không cho sửa khác bản sao bất biến

Trong chương trình:

```java
List<String> readOnly = Collections.unmodifiableList(sorted);
sorted.add("Vy");
```

`readOnly` không cho gọi `add` để sửa danh sách **qua view đó**, nhưng nó vẫn đọc dữ liệu từ danh sách `sorted`. Khi `sorted` được thêm `"Vy"`, view cũng nhìn thấy phần tử mới. Lời gọi `readOnly.add("Hoa")` bị từ chối và được bắt trong ví dụ để chương trình tiếp tục kết thúc bình thường.

Phân biệt ba câu hỏi:

1. Có được sửa qua tham chiếu đang cầm không?
2. Nội dung có thể đổi qua một tham chiếu khác không?
3. Bản thân các đối tượng phần tử có thể bị sửa không?

Một lớp bao bọc không cho sửa không tự trả lời “không” cho cả ba câu hỏi. Ngay cả khi đã tạo bản sao cấu trúc danh sách, các phần tử bên trong vẫn có thể là cùng các đối tượng có thể thay đổi.

Chi tiết này quan trọng khi đọc tên các phương thức trong `Collections`: phải hiểu đối tượng trả về là view, bản sao hay kết quả thuật toán, thay vì suy ra hành vi từ tên gọi chung chung.

## 7. Chọn đúng kiểu khi thiết kế mã

Bắt đầu từ yêu cầu, rồi chọn kiểu biểu đạt yêu cầu đó:

| Yêu cầu | Kiểu thường phù hợp |
| --- | --- |
| Chỉ cần xử lý một nhóm phần tử bằng các thao tác chung | `Collection<E>` |
| Cần vị trí, thứ tự danh sách và truy cập bằng chỉ số | `List<E>` |
| Cần thể hiện yêu cầu không có phần tử lặp | `Set<E>` |
| Cần lấy giá trị theo khóa | `Map<K, V>` |
| Cần xử lý phần tử theo chính sách hàng đợi | `Queue<E>` hoặc kiểu cụ thể hơn |
| Cần thuật toán dùng chung | Tìm phương thức phù hợp của `Collections`, hoặc API trên kiểu dữ liệu |

Một tham số kiểu `Collection<String>` chỉ nói về hợp đồng được chấp nhận. Nó không tự bảo đảm đầu vào có thể sửa, không rỗng hoặc không chứa `null`. Nếu phương thức cần những điều kiện đó, phải nêu rõ hợp đồng và xử lý phù hợp.

Lớp triển khai vẫn cần được chọn theo dữ liệu và thao tác thực tế. Cùng thuộc JCF không có nghĩa `ArrayList`, `HashSet` và `HashMap` có cùng ngữ nghĩa, chi phí hay mức an toàn đồng thời.

## 8. Những nhầm lẫn cần tránh

| Nhầm lẫn | Cách hiểu chính xác |
| --- | --- |
| “Collection và Collections là hai cách viết của một thứ” | Một bên là interface; một bên là lớp tiện ích |
| “Collections chứa dữ liệu của ứng dụng” | Dữ liệu nằm trong các đối tượng được truyền vào hoặc được phương thức trả về |
| “Mọi lớp trong framework đều kế thừa Collection” | Nhánh `Map` là phản ví dụ quan trọng |
| “Có phương thức add thì chắc chắn thêm được” | Thao tác thay đổi có thể không được hỗ trợ |
| “Collection nào cũng có thứ tự và truy cập bằng chỉ số” | Phải dựa vào kiểu cụ thể hơn; `Collection` không có `get(int)` |
| “Các tiện ích Collections luôn tạo bản sao” | Nhiều thuật toán sửa đầu vào; nhiều phương thức trả về view |
| “unmodifiable nghĩa là mọi dữ liệu sẽ bất biến” | Cần xem dữ liệu nền và tính có thể thay đổi của phần tử |
| “Thuộc JCF nghĩa là tự động an toàn luồng” | Phải kiểm tra bảo đảm của lớp, đối tượng bao bọc và cách sử dụng |

## 9. Tự kiểm tra

1. Trong `Collection<String> names = new ArrayList<>();`, đâu là interface và đâu là lớp triển khai?
2. Vì sao `Collections.sort(names)` không biên dịch nếu `names` chỉ có kiểu khai báo `Collection<String>`?
3. `Map<String, Integer>` có thuộc Java Collections Framework không? Nó có phải `Collection` không?
4. Sau khi tạo `Collections.unmodifiableList(list)`, nếu sửa `list` gốc thì view có đổi không?
5. Nếu một API yêu cầu truy cập theo chỉ số, vì sao `List<E>` diễn đạt yêu cầu tốt hơn `Collection<E>`?

Gợi ý đối chiếu: câu 1 và 2 liên quan đến kiểu tham chiếu; câu 3 liên quan đến phạm vi framework; câu 4 liên quan đến view; câu 5 liên quan đến hợp đồng mà mã sử dụng cần.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-003">Quan hệ Iterable, Collection, List, Set, Queue và Deque</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ Iterable, Collection, List, Set, Queue và Deque

Khi chọn cấu trúc dữ liệu trong Java, câu hỏi đầu tiên không phải là “dùng lớp nào?”, mà là “đoạn mã cần hợp đồng nào?”. Chỉ cần duyệt các phần tử, cần truy cập theo vị trí, cần loại bỏ phần tử trùng, hay cần lấy phần tử ở một hoặc hai đầu? Sáu interface trong bài này mô tả các nhu cầu đó ở những mức khác nhau.

Một đối tượng có thể được nhìn qua nhiều interface. Chẳng hạn, một `ArrayList<String>` đồng thời là `List<String>`, `Collection<String>` và `Iterable<String>`. Đây là cùng một đối tượng, không phải ba bản sao. Kiểu của biến quyết định những thao tác mà trình biên dịch cho phép gọi qua biến đó; lớp thực tế quyết định cách thao tác được thực hiện.

## 1. Bản đồ các hợp đồng

| Interface | Câu hỏi mà hợp đồng trả lời | Đặc điểm chính |
| --- | --- | --- |
| `Iterable<E>` | Có thể duyệt lần lượt các phần tử bằng iterator không? | Cung cấp điểm bắt đầu duyệt; không mặc nhiên có kích thước hay thao tác thêm/xóa. |
| `Collection<E>` | Có thể làm việc với một tập hợp phần tử như một đơn vị không? | Có các thao tác chung như kiểm tra kích thước, kiểm tra chứa phần tử và các thao tác thay đổi tùy chọn. |
| `List<E>` | Có cần một dãy có vị trí và truy cập theo chỉ số không? | Thứ tự của dãy có ý nghĩa; cho phép phần tử trùng theo hợp đồng chung. |
| `Set<E>` | Có cần bảo đảm không chứa phần tử trùng không? | Tính duy nhất là đặc điểm cốt lõi; thứ tự phụ thuộc hợp đồng cụ thể. |
| `Queue<E>` | Có cần thêm phần tử chờ xử lý và lấy phần tử ở đầu hàng không? | Cách xác định phần tử ở đầu phụ thuộc loại hàng đợi, không nhất thiết là FIFO. |
| `Deque<E>` | Có cần thao tác ở cả đầu và cuối dãy không? | Hàng đợi hai đầu; có thể dùng theo cách FIFO hoặc LIFO. |

`E` là tham số kiểu của phần tử, ví dụ `String`, `Integer` hoặc `Order`. Các interface này không phải các lớp lưu trữ cụ thể.

`Iterable` thuộc gói `java.lang`, được nhập ngầm như `String`. Các interface còn lại trong danh sách thuộc `java.util`.

## 2. Quan hệ kế thừa và khác biệt phiên bản

Trong Java 17, các quan hệ `extends` trực tiếp giữa sáu interface được biểu diễn như sau. Mũi tên đi từ interface con lên interface cha:

~~~mermaid
flowchart BT
    C["Collection<E>"] -->|extends| I["Iterable<E>"]
    L["List<E>"] -->|extends| C
    S["Set<E>"] -->|extends| C
    Q["Queue<E>"] -->|extends| C
    D["Deque<E>"] -->|extends| Q
~~~

Từ sơ đồ có thể suy ra:

- Mọi `List`, `Set`, `Queue` và `Deque` đều là `Collection`, nên đều là `Iterable`.
- Mọi `Deque` đều là `Queue`; chiều ngược lại không đúng.
- Không phải mọi `Iterable` đều là `Collection`.
- `List`, `Set` và `Queue` không kế thừa lẫn nhau.
- `Deque` không kế thừa `List`, dù cả hai có thể biểu diễn dữ liệu theo một thứ tự.

### Từ Java 21: có thêm SequencedCollection

Từ Java 21, Java bổ sung các interface cho tập hợp có thứ tự duyệt xác định, trong đó có `SequencedCollection<E>`. Khi mô tả quan hệ trực tiếp, cần cập nhật:

| Interface | Java 17 | Từ Java 21 |
| --- | --- | --- |
| `Collection<E>` | Kế thừa `Iterable<E>` | Vẫn kế thừa `Iterable<E>`. |
| `List<E>` | Kế thừa trực tiếp `Collection<E>` | Kế thừa `SequencedCollection<E>`, rồi đến `Collection<E>`. |
| `Set<E>` | Kế thừa `Collection<E>` | Vẫn kế thừa `Collection<E>`; không phải mọi `Set` đều có thứ tự duyệt xác định. |
| `Queue<E>` | Kế thừa `Collection<E>` | Vẫn kế thừa `Collection<E>`. |
| `Deque<E>` | Kế thừa `Queue<E>` | Kế thừa cả `Queue<E>` và `SequencedCollection<E>`. |

`SequencedCollection<E>` kế thừa `Collection<E>`. `SequencedSet<E>` kết hợp hợp đồng của `Set<E>` với `SequencedCollection<E>`. Điều này không biến mọi `Set` thành một dãy có chỉ số.

Các thao tác như `getFirst()`, `getLast()` và `reversed()` có thể xuất hiện qua hợp đồng sequenced ở Java 21. `reversed()` cung cấp một góc nhìn theo thứ tự ngược, không phải lệnh tự đảo dãy tại chỗ. Khả năng thêm/xóa vẫn phụ thuộc hỗ trợ của đối tượng cụ thể.

Những suy luận về kiểu con giữa sáu interface ở trên vẫn đúng. Thay đổi quan trọng là không vẽ `List extends Collection` như một quan hệ trực tiếp cho Java 21. Các ví dụ chạy trong bài chỉ sử dụng API có từ Java 17 để người học có thể chạy trên Java 17 trở lên.

## 3. Iterable: khả năng duyệt, chưa phải tập hợp

Hợp đồng trung tâm của `Iterable<E>` là cung cấp `iterator()`. Vòng lặp for-each có thể dùng iterator đó để lấy từng phần tử.

~~~java
// Đoạn mã trong một phương thức; không phải chương trình độc lập.
Iterable<String> values = List.of("A", "B");

for (String value : values) {
    System.out.println(value);
}
~~~

Qua biến `values`, có thể duyệt nhưng không thể gọi `size()`, `add(...)` hoặc `get(...)`, vì những phương thức đó không thuộc hợp đồng `Iterable`.

`Iterable` không yêu cầu đối tượng phải lưu toàn bộ dữ liệu sẵn trong bộ nhớ. Nó có thể đại diện cho một nguồn tạo phần tử khi duyệt. Chỉ biết một đối tượng là `Iterable` chưa đủ để kết luận:

- Có thể biết trước số phần tử hay không.
- Có thể chỉnh sửa nguồn hay không.
- Dữ liệu có hữu hạn hay không.
- Các lần duyệt có cho cùng kết quả hay không.

### Iterable và Iterator là hai vai trò khác nhau

`Iterable` là đối tượng cung cấp cách bắt đầu duyệt. `Iterator` giữ trạng thái của một lượt duyệt, với các thao tác như `hasNext()` và `next()`.

Không dùng hai tên này thay thế nhau. `Iterator` không kế thừa `Iterable`, và không thể mặc nhiên đưa một biến `Iterator` vào for-each. Một iterator đã đi qua phần tử cũng không tự quay lại đầu.

Mảng Java dùng được trong for-each nhưng không triển khai `Iterable`. Cú pháp for-each hỗ trợ riêng cả mảng lẫn đối tượng `Iterable`; sự giống nhau về cú pháp không chứng minh quan hệ kế thừa.

## 4. Collection: hợp đồng chung cho tập hợp phần tử

`Collection<E>` bổ sung các thao tác chung cho một tập hợp phần tử, chẳng hạn:

- `size()`, `isEmpty()`: hỏi về kích thước và trạng thái rỗng.
- `contains(Object)`: hỏi một phần tử có thuộc tập hợp không.
- `iterator()`: duyệt các phần tử.
- `add(E)`, `remove(Object)`, `clear()`: các thao tác thay đổi tùy chọn.
- `addAll(...)`, `containsAll(...)`, `removeAll(...)`: làm việc với nhiều phần tử.
- `toArray()`, `stream()`: chuyển sang các cách xử lý khác.

Chỉ biết một đối tượng là `Collection` không cho biết nó có cho phép trùng phần tử, có thứ tự nào, có cho phép `null`, có an toàn khi dùng đồng thời hay không. Những điều đó phải được xác định từ interface cụ thể hơn và lớp triển khai.

### Có phương thức không có nghĩa là được phép thay đổi

`Collection` khai báo các thao tác thay đổi, nhưng nhiều thao tác là tùy chọn. Ví dụ:

~~~java
// Đoạn mã minh họa trong một phương thức.
Collection<String> values = List.of("A", "B");

// values.add("C"); // Biên dịch được, nhưng nếu chạy sẽ ném UnsupportedOperationException.
~~~

`List.of(...)` trả về một danh sách không cho sửa đổi cấu trúc hay thay thế phần tử. Việc gán nó cho biến `Collection` không làm nó trở thành đối tượng có thể chỉnh sửa.

Một ví dụ khác là `Arrays.asList(...)`: danh sách có kích thước cố định, cho phép `set(...)` nhưng không hỗ trợ `add(...)` hoặc `remove(...)` để đổi kích thước. Vì vậy, “không sửa được”, “kích thước cố định” và “có thể sửa” là những khả năng khác nhau.

## 5. List: dãy có vị trí

`List<E>` mô tả một dãy mà vị trí của phần tử có ý nghĩa. Chỉ số bắt đầu từ `0`.

~~~java
// Cần import java.util.ArrayList và java.util.List.
List<String> names = new ArrayList<>(List.of("An", "Bình", "An"));

System.out.println(names.get(1)); // Bình
System.out.println(names.size()); // 3
~~~

Hai phần tử `"An"` cùng tồn tại vì `List` không đặt điều kiện duy nhất như `Set`.

Các thao tác thể hiện đặc điểm của `List` gồm `get(index)`, `set(index, value)`, `add(index, value)`, `remove(index)` và `listIterator()`.

Cần phân biệt:

- **Có thứ tự**: các phần tử tạo thành một dãy với các vị trí xác định.
- **Đã sắp xếp**: các phần tử tuân theo một quy tắc so sánh, chẳng hạn tăng dần.

Một `List` có thứ tự nhưng không mặc nhiên được sắp xếp. `List.of(30, 10, 20)` giữ dãy `30, 10, 20`, không tự đổi thành `10, 20, 30`.

Hợp đồng truy cập theo chỉ số cũng không bảo đảm `get(index)` luôn nhanh như nhau. `ArrayList` và `LinkedList` cùng là `List` nhưng có cấu trúc và chi phí thao tác khác nhau.

### Chú ý hai dạng remove của List

Với `List<Integer>`, `remove(1)` chọn phương thức nhận chỉ số `int`, còn `remove(Integer.valueOf(1))` chọn phương thức nhận đối tượng cần xóa. Đây là sự khác nhau về chữ ký phương thức, không phải Java tự đoán mục đích từ dữ liệu.

Không dùng ví dụ này để suy ra mọi `Collection<Integer>` đều có `remove(index)`: phương thức theo chỉ số thuộc `List`, không thuộc hợp đồng chung của `Collection`.

## 6. Set: tính duy nhất, không phải “luôn không có thứ tự”

`Set<E>` bảo đảm không chứa phần tử trùng theo hợp đồng của tập hợp. Ví dụ với một tập hợp giữ thứ tự chèn:

~~~java
// Cần import java.util.LinkedHashSet và java.util.Set.
Set<String> tags = new LinkedHashSet<>();

System.out.println(tags.add("java")); // true
System.out.println(tags.add("java")); // false
System.out.println(tags.size());      // 1
~~~

Lần thêm thứ hai không làm thay đổi tập hợp.

Không nên định nghĩa `Set` là “Collection không có thứ tự”, vì các triển khai có hành vi khác nhau:

| Triển khai | Thứ tự duyệt cần hiểu |
| --- | --- |
| `HashSet` | Không cam kết thứ tự duyệt. Không dựa vào một thứ tự tình cờ quan sát được. |
| `LinkedHashSet` | Thường dùng khi cần giữ thứ tự chèn trong các thao tác thêm thông thường. |
| `TreeSet` | Duyệt theo thứ tự so sánh tự nhiên hoặc `Comparator`. |

`Set` không cung cấp `get(index)` như `List`. Việc một triển khai có thứ tự duyệt không đồng nghĩa nó có truy cập ngẫu nhiên bằng chỉ số.

Với các tập hợp dựa trên băm như `HashSet`, thiết kế `equals()` và `hashCode()` phải nhất quán. Với `TreeSet`, kết quả so sánh bằng `0` quyết định việc xem hai phần tử là cùng vị trí trong tập; thứ tự so sánh nên nhất quán với `equals()` để đáp ứng đầy đủ hợp đồng `Set`. Đây là lý do không nên giải thích tính duy nhất bằng cách chỉ nhìn chuỗi hiển thị của đối tượng.

Không thay đổi tùy tiện những thuộc tính ảnh hưởng đến băm hoặc so sánh khi đối tượng đang nằm trong tập hợp, vì có thể làm các phép tra cứu hoạt động sai với kỳ vọng.

## 7. Queue: lấy phần tử theo quy tắc của hàng đợi

`Queue<E>` mô tả tập hợp phục vụ việc giữ phần tử trước khi xử lý. Một khái niệm quan trọng là **đầu hàng**: phần tử mà thao tác lấy tiếp theo sẽ trả về.

Nhiều hàng đợi hoạt động theo FIFO, tức phần tử vào trước được lấy trước. Nhưng FIFO không phải quy tắc duy nhất của toàn bộ `Queue`.

Ví dụ, `PriorityQueue` xác định đầu hàng theo độ ưu tiên. Với thứ tự tự nhiên của số nguyên, thêm `30, 10, 20` rồi liên tục `poll()` sẽ nhận `10, 20, 30`.

Không suy ra thứ tự `poll()` từ kết quả for-each hay `toString()` của `PriorityQueue`. Iterator của nó không cam kết duyệt theo thứ tự ưu tiên; phải lấy bằng các thao tác hàng đợi nếu mục tiêu là xử lý theo ưu tiên.

### Hai nhóm phương thức

| Mục đích | Dạng báo lỗi bằng ngoại lệ | Dạng trả giá trị đặc biệt |
| --- | --- | --- |
| Thêm phần tử | `add(e)` | `offer(e)` |
| Lấy và bỏ đầu hàng | `remove()` | `poll()` |
| Xem đầu hàng, không bỏ | `element()` | `peek()` |

Khi hàng đợi rỗng, `remove()` và `element()` ném `NoSuchElementException`; `poll()` và `peek()` trả `null`.

Khi không thể thêm do giới hạn sức chứa, `offer(e)` trả `false`, còn `add(e)` có thể ném `IllegalStateException`. “Trả giá trị đặc biệt” không có nghĩa là không bao giờ có ngoại lệ: đầu vào không hợp lệ, chẳng hạn `null` ở một triển khai cấm `null`, vẫn có thể bị từ chối bằng ngoại lệ.

`remove()` không tham số của `Queue` khác với `remove(Object)` kế thừa từ `Collection`. Cái đầu lấy đầu hàng; cái sau tìm và xóa phần tử được chỉ định.

Nên tránh dùng `null` làm dữ liệu hàng đợi vì `null` còn được dùng làm tín hiệu “không có phần tử”. Không phải mọi triển khai đều có cùng chính sách với `null`.

## 8. Deque: cùng một dãy, thao tác được ở hai đầu

`Deque` là viết tắt của double-ended queue: hàng đợi hai đầu.

Ngoài việc là một `Queue`, nó cho phép thao tác rõ ràng ở đầu và cuối:

| Vị trí | Thêm | Lấy và bỏ | Chỉ xem |
| --- | --- | --- | --- |
| Đầu dãy | `offerFirst(e)` | `pollFirst()` | `peekFirst()` |
| Cuối dãy | `offerLast(e)` | `pollLast()` | `peekLast()` |

Ngoài các dạng này còn có các dạng dùng ngoại lệ như `addFirst`, `removeFirst`, `getFirst`, `addLast`, `removeLast` và `getLast`.

### Dùng như hàng đợi FIFO

Thêm ở cuối, lấy ở đầu:

~~~java
// Cần import java.util.ArrayDeque và java.util.Deque.
Deque<String> tasks = new ArrayDeque<>();
tasks.offerLast("A");
tasks.offerLast("B");

System.out.println(tasks.pollFirst()); // A
System.out.println(tasks.pollFirst()); // B
~~~

Khi nhìn `Deque` qua hợp đồng `Queue`, `offer(e)` tương ứng với `offerLast(e)`, `poll()` với `pollFirst()`, và `peek()` với `peekFirst()`.

### Dùng như ngăn xếp LIFO

Thêm và lấy ở cùng đầu:

~~~java
// Đoạn mã trong một phương thức, với các import tương tự ví dụ trên.
Deque<String> stack = new ArrayDeque<>();
stack.push("A");
stack.push("B");

System.out.println(stack.pop()); // B
System.out.println(stack.pop()); // A
~~~

`push(e)` tương ứng với `addFirst(e)`; `pop()` tương ứng với `removeFirst()`. `pop()` trên dãy rỗng ném ngoại lệ, còn `pollFirst()` trả `null`.

`Deque` cung cấp cách dùng ngăn xếp mà không cần dựa vào lớp `Stack` cũ. `ArrayDeque` là một lựa chọn thường gặp cho ngăn xếp hoặc hàng đợi trong mã một luồng; nó không cho phép phần tử `null`.

Có hai đầu không có nghĩa là có `get(index)`. `Deque` không phải một `List`.

## 9. Kiểu khai báo và đối tượng thực tế

Đoạn mã sau không tạo thêm tập hợp khi đổi kiểu tham chiếu:

~~~java
// Đoạn mã trong một phương thức, với import java.util tương ứng.
List<String> list = new ArrayList<>(List.of("A", "B"));
Collection<String> collection = list;
Iterable<String> iterable = collection;

System.out.println(list.get(0));       // A
System.out.println(collection.size()); // 2

// collection.get(0); // Không biên dịch: Collection không khai báo get(index).
// iterable.size();   // Không biên dịch: Iterable không khai báo size().

list.add("C");
System.out.println(collection.size()); // 3
~~~

Cả ba biến cùng trỏ đến đối tượng `ArrayList`. Vì vậy, thay đổi thông qua `list` được nhìn thấy qua `collection`. Tuy nhiên, việc đối tượng thực tế có phương thức `get` không làm trình biên dịch cho phép gọi `get` trên một biến có kiểu `Collection`.

Nên khai báo tham số bằng hợp đồng đáp ứng nhu cầu của phương thức:

- Chỉ duyệt: cân nhắc `Iterable<?>` hoặc `Iterable<? extends T>`.
- Cần thêm `size()` hay `contains(...)`: cân nhắc `Collection<T>`.
- Cần vị trí: dùng `List<T>`.
- Cần biểu đạt điều kiện duy nhất: dùng `Set<T>`.
- Cần thao tác hàng đợi: dùng `Queue<T>`.
- Cần cả hai đầu hoặc ngăn xếp: dùng `Deque<T>`.

Không khai báo kiểu quá chung rồi ép kiểu trở lại chỉ để gọi phương thức mà thiết kế đã cần từ đầu. Ép một `Collection` tùy ý thành `List` có thể gây `ClassCastException` nếu đối tượng thực tế là một `HashSet`.

### Một lớp có thể đáp ứng nhiều hợp đồng

`LinkedList` triển khai cả `List` lẫn `Deque`:

~~~java
// Đoạn mã trong một phương thức; cần import java.util.LinkedList.
LinkedList<String> actual = new LinkedList<>();
List<String> listView = actual;
Deque<String> dequeView = actual;
~~~

Điều này không tạo ra quan hệ `List extends Deque` hay `Deque extends List`. Quan hệ của lớp triển khai không được suy ngược thành quan hệ kế thừa giữa hai interface.

Tương tự, `Collection<Integer>` không phải kiểu con của `Collection<Number>`, dù `Integer` là kiểu con của `Number`. Đây là quy tắc về generic, tách biệt với quan hệ `List<E>` là kiểu con của `Collection<E>`. Khi nhận nhiều kiểu phần tử để chỉ đọc, wildcard có thể biểu đạt ý định phù hợp.

## 10. Chương trình hoàn chỉnh

Lưu mã dưới đây trong tệp **CollectionRelationshipsDemo.java**. Chương trình chỉ dùng API có trong Java 17; phần `Iterable` không cần import vì thuộc `java.lang`.

~~~java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.StringJoiner;

public class CollectionRelationshipsDemo {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>(List.of("A", "B", "A"));
        Collection<String> collectionView = list;
        Iterable<String> iterableView = collectionView;

        printAll("Iterable view", iterableView);
        System.out.println("Collection size = " + collectionView.size());
        System.out.println("List get(1) = " + list.get(1));

        Set<String> unique = new LinkedHashSet<>(list);
        System.out.println("Set = " + unique);

        Queue<Integer> priority = new PriorityQueue<>(List.of(30, 10, 20));
        List<Integer> removed = new ArrayList<>();
        Integer next;
        while ((next = priority.poll()) != null) {
            removed.add(next);
        }
        System.out.println("Queue poll order = " + removed);

        Deque<String> deque = new ArrayDeque<>();
        deque.offerLast("A");
        deque.offerLast("B");
        System.out.println("Deque after offerLast = " + deque);
        System.out.println("Deque pollFirst = " + deque.pollFirst());

        deque.offerFirst("Z");
        System.out.println("Deque after offerFirst = " + deque);
        System.out.println("Deque pollLast = " + deque.pollLast());

        Queue<String> queueView = deque;
        System.out.println("Queue view peek = " + queueView.peek());

        Iterable<Integer> onlyIterable = () -> List.of(1, 2, 3).iterator();
        printAll("Only Iterable", onlyIterable);
        System.out.println(
                "Only Iterable is Collection = "
                        + (onlyIterable instanceof Collection<?>)
        );
    }

    private static void printAll(String label, Iterable<?> values) {
        StringJoiner result = new StringJoiner(", ", "[", "]");
        for (Object value : values) {
            result.add(String.valueOf(value));
        }
        System.out.println(label + " = " + result);
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 CollectionRelationshipsDemo.java
java CollectionRelationshipsDemo
~~~

Stdout:

~~~text
Iterable view = [A, B, A]
Collection size = 3
List get(1) = B
Set = [A, B]
Queue poll order = [10, 20, 30]
Deque after offerLast = [A, B]
Deque pollFirst = A
Deque after offerFirst = [Z, B]
Deque pollLast = B
Queue view peek = Z
Only Iterable = [1, 2, 3]
Only Iterable is Collection = false
~~~

### Đọc kết quả theo hợp đồng

1. `Iterable view` và `Collection size` quan sát cùng danh sách ban đầu, không tạo bản sao.
2. `List get(1)` dùng khả năng truy cập theo vị trí chỉ có ở hợp đồng phù hợp.
3. `LinkedHashSet` loại phần tử `A` lặp, đồng thời giữ thứ tự chèn trong ví dụ nên kết quả là `[A, B]`.
4. `PriorityQueue` lấy số theo thứ tự ưu tiên; kết quả được thu bằng `poll()`, không phải bằng iterator.
5. `Deque` cho phép thêm/lấy ở cả hai đầu. Khi đổi sang biến `Queue`, thao tác `peek()` nhìn đầu dãy của cùng đối tượng.
6. Đối tượng lambda `onlyIterable` chỉ cung cấp `Iterable`; chính đối tượng đó không phải `Collection`, dù iterator mà nó trả về được lấy từ một danh sách.

Phương thức `printAll` nhận `Iterable<?>` vì nó chỉ cần duyệt. Nó không đòi người gọi phải đưa một `List` hay một tập hợp có thể thay đổi.

## 11. Chọn lớp triển khai sau khi đã chọn hợp đồng

| Nhu cầu của đoạn mã | Kiểu khai báo thường phù hợp | Triển khai minh họa | Điều cần kiểm tra thêm |
| --- | --- | --- | --- |
| Danh sách có vị trí, truy cập theo chỉ số | `List<E>` | `ArrayList<E>` | Chi phí chèn/xóa, kích thước và khả năng thay đổi. |
| Thành viên duy nhất, không cần thứ tự duyệt | `Set<E>` | `HashSet<E>` | Hợp đồng `equals/hashCode`; không dựa vào thứ tự tình cờ. |
| Thành viên duy nhất, cần thứ tự chèn | `Set<E>` | `LinkedHashSet<E>` | Nếu cần API sequenced, xem phiên bản Java và kiểu khai báo cụ thể hơn. |
| Thành viên duy nhất theo thứ tự so sánh | `Set<E>` hoặc interface tập hợp sắp xếp phù hợp | `TreeSet<E>` | Quy tắc so sánh và sự nhất quán với `equals`. |
| Xử lý theo độ ưu tiên | `Queue<E>` | `PriorityQueue<E>` | Cách so sánh phần tử; iterator không phải thứ tự lấy. |
| Hàng đợi FIFO hoặc ngăn xếp LIFO một luồng | `Queue<E>` hoặc `Deque<E>` | `ArrayDeque<E>` | Chọn thao tác đầu/cuối đúng ý định; không cho phép `null`. |

Bảng không khẳng định một triển khai phù hợp cho mọi khối lượng dữ liệu. Khi có nhiều luồng, giới hạn sức chứa, chờ phần tử hoặc yêu cầu truy cập đồng thời, phải xét thêm hợp đồng và triển khai chuyên biệt. Chỉ đổi biến từ `List` sang `Collection` không làm đối tượng trở nên an toàn khi dùng đồng thời.

## 12. Những quan hệ dễ nhầm

### Collection và Collections

`Collection` là interface mô tả tập hợp phần tử. `Collections` là lớp tiện ích có các phương thức tĩnh, chẳng hạn sắp xếp, đảo danh sách hoặc tạo một số góc nhìn bao bọc.

Tên gần nhau không có nghĩa `Collections` là interface cha hoặc lớp triển khai của mọi `Collection`.

### Map không kế thừa Collection

`Map<K, V>` mô tả ánh xạ khóa–giá trị, không kế thừa `Collection` và không mặc nhiên là `Iterable`.

Nó cung cấp các góc nhìn liên quan:

- `keySet()`: một `Set<K>`.
- `values()`: một `Collection<V>`, vì các giá trị có thể trùng nhau.
- `entrySet()`: một `Set<Map.Entry<K, V>>`.

Có thể for-each trên những góc nhìn này. Đây không phải bằng chứng rằng chính `Map` là `Collection`. Một góc nhìn cũng không nhất thiết là bản sao độc lập, và không phải mọi thao tác thay đổi đều được hỗ trợ.

### Stream không phải Iterable hay Collection

`Stream` mô tả một chuỗi xử lý dữ liệu, không phải tập hợp dùng để lưu và truy cập các phần tử như `List`. `Collection.stream()` là cách tạo stream từ tập hợp, không phải quan hệ kế thừa.

Không mặc nhiên dùng lại một stream sau khi đã thực hiện thao tác kết thúc, và không dùng `Stream` thay cho `Iterable` chỉ vì cả hai có thể xử lý từng phần tử.

## 13. Các lỗi thường gặp khi dùng quan hệ interface

| Cách hiểu sai | Cách hiểu cần giữ |
| --- | --- |
| Mọi `Iterable` đều có `size()`. | Chỉ khả năng duyệt không kéo theo hợp đồng về kích thước. |
| Mọi `Collection` đều cho phép thêm/xóa. | Các thao tác thay đổi có thể không được hỗ trợ. |
| `List` là dữ liệu đã sắp xếp. | `List` là dãy có vị trí; sắp xếp là một điều kiện riêng. |
| `Set` luôn không có thứ tự. | `Set` đặt trọng tâm vào tính duy nhất; triển khai quyết định thêm về thứ tự. |
| Mọi `Queue` đều là FIFO. | Phần tử ở đầu có thể được chọn theo độ ưu tiên hoặc quy tắc khác. |
| Có thể xem `PriorityQueue` như một danh sách đã sắp xếp. | Thứ tự lấy bằng `poll` và thứ tự duyệt là hai hợp đồng khác nhau. |
| `Deque` là một `List` vì đều có đầu và cuối. | `Deque` không cung cấp hợp đồng truy cập theo chỉ số. |
| Đổi kiểu biến tạo ra một tập hợp mới. | Phép gán tham chiếu không sao chép đối tượng. |
| Lớp triển khai hai interface nghĩa là hai interface kế thừa nhau. | Đó là quan hệ của lớp với từng interface, không phải của hai interface với nhau. |
| Sơ đồ quan hệ trực tiếp của Java 17 luôn đúng nguyên dạng ở Java 21. | Java 21 thêm tầng `SequencedCollection` cho `List` và thêm nó vào các cha của `Deque`. |

Một điểm thực hành khác là không tùy tiện sửa cấu trúc tập hợp trong for-each. Tùy triển khai, iterator có thể phát hiện thay đổi và ném `ConcurrentModificationException`; cơ chế phát hiện này không phải một giải pháp đồng bộ giữa các luồng. Nếu cần xóa khi duyệt, phải dùng thao tác được hợp đồng và đối tượng cụ thể hỗ trợ, chẳng hạn `Iterator.remove()` hoặc `removeIf(...)` trong bối cảnh phù hợp.

## 14. Tự kiểm tra

1. Một phương thức chỉ in lần lượt phần tử có cần nhận `List` không? Vì sao `Iterable` có thể là hợp đồng phù hợp hơn?
2. Vì sao gọi `add` qua biến `Collection` có thể biên dịch nhưng vẫn thất bại khi chạy?
3. Nếu cần danh sách các tên không trùng và giữ thứ tự nhập, chỉ biết “dùng `Set`” đã đủ để chọn triển khai chưa?
4. Vì sao for-each trên `PriorityQueue` không phải cách lấy các phần tử theo độ ưu tiên?
5. Hãy dùng `ArrayDeque` để xử lý ba tác vụ theo FIFO, rồi đổi cách thao tác để xử lý theo LIFO.
6. Vì sao `LinkedList` có thể gán cho cả `List` và `Deque`, nhưng không thể kết luận `List` kế thừa `Deque`?
7. Khi nâng mô tả kiến trúc từ Java 17 lên Java 21, cần sửa điều gì trong quan hệ trực tiếp của `List` và `Deque`?

Khi tự giải thích được các câu hỏi này, người học có thể đọc một khai báo kiểu và biết đoạn mã được quyền kỳ vọng những gì trước khi xét đến lớp lưu trữ, hiệu năng hoặc tính đồng thời.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-004">Vì sao Map thuộc framework nhưng không kế thừa Collection</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao Map thuộc framework nhưng không kế thừa Collection?

**`Map` thuộc Java Collections Framework vì nó là một thành phần của hệ thống API tổ chức và xử lý dữ liệu của Java. Nó không kế thừa `Collection` vì hợp đồng của một ánh xạ khóa–giá trị khác hợp đồng của một nhóm phần tử.**

Cần tách hai quan hệ:

- **Thuộc framework:** cùng tham gia một hệ thống các interface, lớp triển khai, thuật toán và quy ước.
- **Kế thừa interface:** một kiểu cam kết thực hiện đầy đủ hợp đồng của interface cha.

Không có quy tắc rằng mọi thành phần trong Java Collections Framework đều phải nằm dưới interface `Collection`. Nhánh `Map` tồn tại song song với nhánh `Collection`, rồi kết nối với nhánh này qua các view như `keySet()`, `values()` và `entrySet()`.

## 1. Hai mô hình dữ liệu khác nhau ở đâu?

### 1.1. Collection nhìn dữ liệu như các phần tử

Với `Collection<E>`, đơn vị được xử lý là phần tử kiểu `E`.

Ví dụ về một nhóm mã sản phẩm:

```text
["pen", "book", "bag"]
```

Những câu hỏi tự nhiên là:

- Nhóm này có bao nhiêu phần tử?
- Có phần tử `"book"` không?
- Có thể thêm hoặc xóa một phần tử không?
- Duyệt qua các phần tử bằng cách nào?

Các interface con bổ sung ngữ nghĩa cụ thể. `List` có vị trí và thứ tự danh sách; `Set` thể hiện tập không có phần tử trùng; `Queue` phục vụ cách xử lý theo chính sách hàng đợi.

### 1.2. Map nhìn dữ liệu như quan hệ khóa–giá trị

Với `Map<K, V>`, đơn vị nghiệp vụ là một **ánh xạ** từ khóa kiểu `K` sang giá trị kiểu `V`.

Ví dụ số lượng tồn kho:

```text
"pen"  -> 10
"book" -> 3
"bag"  -> 3
```

Những câu hỏi tự nhiên chuyển thành:

- Số lượng ứng với mã `"pen"` là bao nhiêu?
- Mã `"book"` đã có ánh xạ chưa?
- Cập nhật số lượng cho một mã bằng cách nào?
- Xóa ánh xạ ứng với khóa nào?

Trong một map, mỗi khóa chỉ có tối đa một giá trị hiện tại. Nhiều khóa khác nhau có thể ánh xạ tới các giá trị bằng nhau. Ở ví dụ trên, hai giá trị `3` không vi phạm hợp đồng của map.

Một giá trị cũng có thể là một đối tượng chứa nhiều phần tử, chẳng hạn `Map<String, List<String>>`. Khi đó mỗi khóa vẫn ánh xạ tới **một đối tượng List**, không làm thay đổi hợp đồng của `Map`.

Đặc điểm khóa–giá trị và vị trí của `Map` trong framework được mô tả trong [API Map của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

## 2. Vì sao không dùng Collection<Map.Entry<K, V>> làm cha?

Có thể biểu diễn một cặp khóa–giá trị bằng `Map.Entry<K, V>`. Từ đó, ta hoàn toàn có thể tạo một collection chứa các cặp. Nhưng **chứa các cặp dữ liệu** chưa đồng nghĩa với **thực hiện hợp đồng của map**.

### 2.1. Một tập các cặp chưa bảo đảm khóa duy nhất

Xét hai cặp:

```text
("pen", 10)
("pen", 12)
```

Đối với hai `Map.Entry` thông thường, khác giá trị thì hai cặp này không bằng nhau. Do đó một `HashSet<Map.Entry<String, Integer>>` có thể giữ đồng thời cả hai cặp.

Đối với một map, lần lượt gọi:

```java
map.put("pen", 10);
map.put("pen", 12);
```

sẽ tạo một ánh xạ hiện tại cho `"pen"` với giá trị `12`, không phải hai ánh xạ cùng khóa.

Đoạn trên minh họa các lệnh trong một phương thức, với `map` là một `Map<String, Integer>` cho phép cập nhật.

Sự khác nhau nằm ở **quy tắc nhận diện và cập nhật phần tử**, không nằm ở việc Java có lưu được hai giá trị trong một đối tượng hay không. Cũng không thể giải thích đơn giản bằng “`Collection` có một tham số kiểu còn `Map` có hai”, vì `E` hoàn toàn có thể là một kiểu cặp.

### 2.2. Các thao tác cốt lõi cần hợp đồng riêng

| Nhu cầu | Hợp đồng Collection | Hợp đồng Map |
| --- | --- | --- |
| Thêm dữ liệu | `add(E)` yêu cầu đưa phần tử vào nhóm | `put(K, V)` tạo hoặc thay ánh xạ cho khóa |
| Hỏi dữ liệu có mặt | `contains(Object)` hỏi về một phần tử | `containsKey(Object)` và `containsValue(Object)` hỏi hai phía khác nhau |
| Tra cứu theo khóa | Không có thao tác chung tương đương | `get(Object)` trả giá trị ứng với khóa |
| Xóa dữ liệu | `remove(Object)` xóa phần tử phù hợp | `remove(Object)` xóa ánh xạ theo khóa |
| Kích thước | Số phần tử | Số ánh xạ khóa–giá trị |
| Duyệt | Từng phần tử kiểu `E` | Cần chọn duyệt khóa, giá trị hay cặp |

Nếu giả sử một map có phương thức `add(entry)`, chỉ tên gọi đó chưa nói rõ điều gì xảy ra khi khóa đã tồn tại: từ chối cặp mới, giữ hai cặp hay thay giá trị cũ? `Map.put` có hợp đồng riêng để bên sử dụng biết hành vi phải chờ đợi.

Tương tự, `contains(x)` trên một map sẽ cần xác định `x` được hiểu là khóa, giá trị hay một cặp đầy đủ. API `Map` làm rõ ý định bằng các phương thức riêng.

### 2.3. Xung đột chữ ký cho thấy đây không chỉ là đổi cây kế thừa

Một khác biệt cụ thể trong API hiện có:

```java
// Chữ ký minh họa, không phải một lớp hoặc chương trình độc lập:

// Trong Collection<E>:
boolean remove(Object element);

// Trong Map<K, V>:
V remove(Object key);
```

Hai phương thức có cùng tên và cùng danh sách kiểu tham số, nhưng một bên trả `boolean`, bên kia trả `V`.

Với `Collection`, kết quả trả lời câu hỏi “nhóm có thay đổi do xóa phần tử không?”. Với `Map`, kết quả là giá trị trước đó gắn với khóa, hoặc `null` theo hợp đồng của phương thức.

Không thể chỉ thêm `extends Collection<...>` vào `Map` hiện có rồi giữ nguyên hai hợp đồng này: Java không phân biệt hai phương thức chỉ bằng kiểu trả về, và `boolean` không tương thích với kiểu tham chiếu `V`.

Đây là một biểu hiện rõ của khác biệt ngữ nghĩa giữa hai interface. Mục tiêu của thiết kế API là cung cấp hợp đồng rõ ràng cho mô hình dữ liệu, thay vì buộc hai mô hình vào chung một interface vì cùng “chứa nhiều thứ”.

Có thể đối chiếu chữ ký trong [API Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#remove(java.lang.Object)) và [API Map.remove](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#remove(java.lang.Object)).

## 3. Map nằm ở đâu trong framework?

Sơ đồ rút gọn sau thể hiện các nhánh interface quen thuộc trong Java 17:

```text
Java Collections Framework
│
├── Nhánh nhóm phần tử
│   └── Collection<E> extends Iterable<E>
│       ├── List<E>
│       ├── Set<E>
│       └── Queue<E>
│           └── Deque<E>
│
└── Nhánh ánh xạ
    └── Map<K, V>
        └── SortedMap<K, V>
            └── NavigableMap<K, V>
```

Đây là sơ đồ phân nhóm và kế thừa được rút gọn, không phải danh sách đầy đủ mọi interface của framework.

Một số lớp triển khai:

| Interface sử dụng | Ví dụ lớp triển khai |
| --- | --- |
| `List<E>` | `ArrayList<E>`, `LinkedList<E>` |
| `Set<E>` | `HashSet<E>`, `LinkedHashSet<E>` |
| `Deque<E>` | `ArrayDeque<E>`, `LinkedList<E>` |
| `Map<K, V>` | `HashMap<K, V>`, `LinkedHashMap<K, V>` |
| `NavigableMap<K, V>` | `TreeMap<K, V>` |

Việc `HashMap` triển khai `Map` đã đặt nó vào nhánh ánh xạ của framework. Nó không cần và không có quan hệ kế thừa `Collection`.

Framework còn có những tiện ích hỗ trợ cả hai nhánh. Ví dụ, lớp `Collections` có các phương thức `unmodifiableCollection` và `unmodifiableMap`. Vì vậy, **Java Collections Framework**, **interface Collection** và **lớp Collections** là ba tên ở ba phạm vi khác nhau.

Xem cấu trúc chung trong [Collections Framework Overview của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/doc-files/coll-overview.html).

## 4. Ba view kết nối Map với Collection

Một map có thể được nhìn từ ba góc độ mà không cần biến chính nó thành một `Collection`:

| Phương thức | Kiểu trả về | Phần dữ liệu nhìn thấy |
| --- | --- | --- |
| `keySet()` | `Set<K>` | Tập khóa |
| `values()` | `Collection<V>` | Nhóm giá trị |
| `entrySet()` | `Set<Map.Entry<K, V>>` | Tập cặp khóa–giá trị |

### 4.1. Vì sao keySet là Set?

Khóa trong một map không lặp theo quy tắc khóa của map đó. Một view kiểu `Set<K>` thể hiện đúng đặc điểm này.

### 4.2. Vì sao values chỉ là Collection?

Giá trị có thể trùng nhau. Nếu `"book"` và `"bag"` đều có số lượng `3`, `values()` phải phản ánh đủ hai lần xuất hiện đó.

Nếu biến kết quả thành một `Set<V>`, những giá trị bằng nhau có thể bị gộp, làm mất thông tin rằng chúng thuộc những ánh xạ khác nhau.

### 4.3. Vì sao entrySet là Set?

Một entry chứa khóa và giá trị của một ánh xạ. Vì mỗi khóa có tối đa một ánh xạ, tập entry của một map không chứa hai cặp trùng nhau.

Chiều suy luận ngược lại không đúng: một `Set<Map.Entry<K, V>>` bất kỳ vẫn có thể chứa hai cặp cùng khóa nhưng khác giá trị. Chính map chịu trách nhiệm bảo đảm tính duy nhất của khóa; việc kết quả có kiểu `Set` không thay map thực hiện ràng buộc đó.

### 4.4. View là đối tượng liên kết với map gốc

Các collection trả về là **khung nhìn (view)** được map gốc hỗ trợ. Khi nội dung map thay đổi, các view phản ánh nội dung hiện tại đó; không cần gọi lại `keySet()` chỉ để nhận biết khóa vừa được thêm.

Ở hướng ngược lại, một số thao tác sửa được hỗ trợ trên view tác động lên map. Chẳng hạn, xóa một khóa qua `keySet().remove(key)` sẽ xóa cả ánh xạ tương ứng nếu map và view hỗ trợ xóa.

Điều này không có nghĩa view có thể sửa tùy ý:

- Ba view chuẩn này không hỗ trợ thêm bằng `add` hoặc `addAll`.
- Xóa qua `values()` là xóa ánh xạ tương ứng với giá trị bị loại bỏ. Nếu nhiều ánh xạ có giá trị bằng nhau, `remove(value)` chỉ loại một ánh xạ phù hợp; không nên dùng nó thay cho việc chọn chính xác một khóa.
- `Map.Entry.setValue` là thao tác tùy chọn. Entry lấy từ map không cho sửa có thể từ chối nó.
- Entry do `Map.entry(k, v)` tạo không cho sửa bằng `setValue`.

Hành vi và giới hạn của entry được quy định trong [API Map.Entry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html).

## 5. Ví dụ Java hoàn chỉnh

Chương trình sau cho thấy sự khác nhau giữa một tập các cặp và map, sau đó kiểm chứng ba view.

Lưu vào **MapFrameworkDemo.java**:

```java
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class MapFrameworkDemo {
    public static void main(String[] args) {
        Set<Map.Entry<String, Integer>> pairs = new HashSet<>();
        pairs.add(Map.entry("pen", 10));
        pairs.add(Map.entry("pen", 12));

        Map<String, Integer> sameKey = new LinkedHashMap<>();
        sameKey.put("pen", 10);
        sameKey.put("pen", 12);

        System.out.println("Pair set size: " + pairs.size());
        System.out.println("Map size for same key: " + sameKey.size());
        System.out.println("Current pen value: " + sameKey.get("pen"));

        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("pen", 10);
        stock.put("book", 3);
        stock.put("bag", 3);

        Set<String> keys = stock.keySet();
        Collection<Integer> values = stock.values();
        Set<Map.Entry<String, Integer>> entries = stock.entrySet();

        System.out.println("Initial map: " + stock);
        Integer old = stock.put("pen", 12);
        System.out.println("Previous pen value: " + old);
        System.out.println("After update: " + stock);
        System.out.println("Keys: " + keys);
        System.out.println("Values: " + values);
        System.out.println("Entries: " + entries);

        stock.put("eraser", 2);
        System.out.println("Keys after put: " + keys);

        values.remove(2);
        System.out.println("After values.remove: " + stock);

        keys.remove("book");
        System.out.println("After keys.remove: " + stock);

        Map.Entry<String, Integer> first = entries.iterator().next();
        first.setValue(15);
        System.out.println("After entry.setValue: " + stock);

        try {
            keys.add("ruler");
        } catch (UnsupportedOperationException ex) {
            System.out.println("keySet.add: " + ex.getClass().getSimpleName());
        }

        Map<String, Integer> copy = new LinkedHashMap<>(stock);
        stock.put("pen", 99);
        System.out.println("Map after later change: " + stock);
        System.out.println("Independent map copy: " + copy);
    }
}
```

Biên dịch và chạy bằng JDK có hỗ trợ Java 17:

```shell
javac --release 17 -encoding UTF-8 MapFrameworkDemo.java
java MapFrameworkDemo
```

Stdout:

```text
Pair set size: 2
Map size for same key: 1
Current pen value: 12
Initial map: {pen=10, book=3, bag=3}
Previous pen value: 10
After update: {pen=12, book=3, bag=3}
Keys: [pen, book, bag]
Values: [12, 3, 3]
Entries: [pen=12, book=3, bag=3]
Keys after put: [pen, book, bag, eraser]
After values.remove: {pen=12, book=3, bag=3}
After keys.remove: {pen=12, bag=3}
After entry.setValue: {pen=15, bag=3}
keySet.add: UnsupportedOperationException
Map after later change: {pen=99, bag=3}
Independent map copy: {pen=15, bag=3}
```

### 5.1. Đối chiếu kết quả

**Ba dòng đầu** so sánh hai mô hình:

- Tập `pairs` giữ hai entry khác nhau: `("pen", 10)` và `("pen", 12)`.
- Map `sameKey` chỉ có một khóa `"pen"`, giá trị hiện tại là `12`.

**Lần cập nhật `stock.put("pen", 12)`** trả lại giá trị cũ `10`. Khóa đã có nên map vẫn có ba ánh xạ.

**View `values` in `[12, 3, 3]`**, bảo toàn việc hai khóa khác nhau cùng có giá trị `3`.

**Sau khi thêm `"eraser"`**, biến `keys` đã lấy từ trước vẫn nhìn thấy khóa mới. Đây là bằng chứng view không phải bản sao tách rời.

**Xóa qua view** tác động lên map:

- `values.remove(2)` xóa ánh xạ `"eraser" -> 2`. Giá trị `2` được chọn vì chỉ xuất hiện một lần trong dữ liệu này.
- `keys.remove("book")` xóa ánh xạ ứng với `"book"`.
- Entry đầu của `LinkedHashMap` lúc này là entry của `"pen"`; `setValue(15)` cập nhật giá trị trong map.

**`keySet.add` bị từ chối** bằng `UnsupportedOperationException`, cho thấy việc kết quả là `Set` không bảo đảm mọi thao tác của set đều được hỗ trợ.

**Hai dòng cuối** phân biệt view với bản sao cấu trúc: `new LinkedHashMap<>(stock)` tạo một map riêng chứa các ánh xạ tại thời điểm sao chép. Cập nhật ánh xạ của map gốc sau đó không cập nhật ánh xạ của map bản sao.

`LinkedHashMap` ở ví dụ được tạo bằng constructor mặc định, giữ thứ tự thêm khóa. Cập nhật giá trị của khóa đã có không chuyển vị trí của khóa đó. Không nên dùng thứ tự stdout này làm bảo đảm cho một `HashMap` bất kỳ; xem [API LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

## 6. Áp dụng khi viết API và duyệt dữ liệu

### 6.1. Chọn view theo dữ liệu thực sự cần

Nếu phương thức chỉ cần các giá trị, có thể truyền `map.values()` cho tham số `Collection<V>`. Nếu cần cả khóa lẫn giá trị, dùng `entrySet()`. Nếu cần tra cứu hoặc cập nhật theo khóa, giữ tham số kiểu `Map<K, V>`.

Đoạn minh họa sau nằm trong một phương thức có biến `stock`:

```java
for (Map.Entry<String, Integer> entry : stock.entrySet()) {
    System.out.println(entry.getKey() + " has " + entry.getValue());
}

stock.forEach((key, value) ->
        System.out.println(key + " has " + value));
```

Hai cách đều xử lý các ánh xạ. `Map.forEach` nhận thao tác với cả khóa và giá trị; sự tồn tại của phương thức này không làm `Map` kế thừa `Iterable`.

Không thể viết vòng `for-each` trực tiếp lên một biến chỉ có kiểu `Map`:

```java
// Không biên dịch: Map không phải Iterable.
// for (var item : stock) {
// }
```

Dùng `stock.entrySet()`, `stock.keySet()` hoặc `stock.values()` tùy mục tiêu.

### 6.2. Không ép kiểu để tạo quan hệ kế thừa không tồn tại

Đây là phép gán không hợp lệ:

```java
// Map<String, Integer> stock = new LinkedHashMap<>();
// Collection<?> data = stock; // Không biên dịch.
```

Thay vì ép kiểu vòng qua `Object`, hãy chọn đúng view. `stock.entrySet()` có kiểu `Set<Map.Entry<String, Integer>>`, nên có thể được sử dụng như một `Collection<Map.Entry<String, Integer>>`.

Chuyển đổi view là chọn một cách nhìn dữ liệu; ép kiểu không biến một map thành đối tượng tuân theo hợp đồng `Collection`.

### 6.3. Phân biệt sao chép cấu trúc với sao chép sâu

Bản sao bằng `new LinkedHashMap<>(stock)` có cấu trúc ánh xạ riêng. Tuy nhiên, nó không tự nhân bản các đối tượng khóa và giá trị.

Trong ví dụ, `String` và `Integer` không cho sửa giá trị nội tại, nên sự khác nhau dễ quan sát. Nếu giá trị là một `List` có thể sửa và hai map cùng giữ tham chiếu đến list đó, thay đổi nội dung list có thể nhìn thấy từ cả hai map.

Nếu cần dữ liệu tách rời hoàn toàn, phải xác định chính sách sao chép cho từng loại đối tượng, không suy ra từ constructor sao chép map.

## 7. Lỗi thường gặp

| Cách hiểu hoặc thao tác sai | Vấn đề | Cách tiếp cận phù hợp |
| --- | --- | --- |
| “Không kế thừa Collection thì không thuộc JCF” | Nhầm quan hệ kế thừa với phạm vi framework | Nhìn hai nhánh Collection và Map |
| “Map chỉ là một Set các cặp” | Set các cặp có thể giữ cùng khóa với các giá trị khác nhau | Dùng Map để thể hiện ràng buộc theo khóa |
| “values() là Set vì map không có khóa trùng” | Khóa duy nhất không làm giá trị duy nhất | Giữ kiểu Collection cho nhóm giá trị |
| “Lấy keySet rồi lưu lại là đã chụp dữ liệu” | View vẫn gắn với map gốc | Tạo bản sao khi cần dữ liệu theo thời điểm |
| “Có kiểu Set thì keySet.add chắc chắn dùng được” | Thao tác thêm không được hỗ trợ | Thêm ánh xạ qua Map.put |
| “Xóa value là cách xóa một mã cụ thể” | Nhiều khóa có thể cùng giá trị | Xóa theo khóa nếu ý định gắn với mã |
| “Mọi entry đều hỗ trợ setValue” | Có entry không cho sửa | Kiểm tra hợp đồng của nguồn entry |
| “Map nào cũng in theo thứ tự đã thêm” | Mỗi lớp triển khai có bảo đảm riêng | Chọn lớp theo yêu cầu thứ tự |

Khi đang duyệt các view, cũng không nên tùy tiện thêm hoặc xóa cấu trúc map bằng đường khác. Hành vi phụ thuộc hợp đồng của map và iterator; với các map thông thường, đó có thể là nguồn lỗi `ConcurrentModificationException`. Nếu cần xóa trong lúc duyệt, chọn thao tác được hỗ trợ như `Iterator.remove()` và tuân thủ hợp đồng, thay vì dựa vào việc một lần chạy thử không báo lỗi.

## 8. Bài tập tự kiểm tra

1. Viết hai câu phân biệt “`Map` thuộc framework” và “`Map` kế thừa `Collection`”.
2. Với hai cặp `("A", 1)` và `("A", 2)`, dự đoán kích thước của một `HashSet<Map.Entry<String, Integer>>` và một map được cập nhật lần lượt bằng hai cặp đó.
3. Giải thích vì sao `values()` không có kiểu trả về `Set<V>`.
4. Trong chương trình, thay `keys.remove("book")` bằng `stock.remove("book")`. View `keys` sau đó có còn khóa đó không?
5. Tạo `Map<String, List<String>>`, sao chép bằng constructor của `LinkedHashMap`, rồi sửa list giá trị. Giải thích khác biệt giữa sửa ánh xạ và sửa đối tượng đang được ánh xạ.
6. Đối chiếu hai chữ ký `remove(Object)`: vì sao không thể ghép trực tiếp các hợp đồng hiện có vào một quan hệ kế thừa chỉ bằng thay đổi phần khai báo?

Mục tiêu là giải thích được lựa chọn mô hình và dự đoán đúng tác động của view, thay vì chỉ ghi nhớ câu “Map không extends Collection”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-005">So sánh mảng với collection: kích thước, kiểu và thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh mảng với collection: kích thước, kiểu và thao tác

Mảng và collection đều có thể quản lý nhiều giá trị, nhưng chúng tổ chức dữ liệu và cung cấp thao tác theo những hợp đồng khác nhau. Việc chọn đúng mô hình giúp tránh những lỗi như coi dung lượng dự phòng là số phần tử, xóa nhầm theo chỉ số hoặc tưởng một danh sách được tạo từ mảng có thể tự tăng kích thước.

Ba câu hỏi dẫn đường cho bài này:

1. **Kích thước:** số vị trí lưu trữ có thay đổi không, và số phần tử đang được quản lý là bao nhiêu?
2. **Kiểu:** phần tử là giá trị nguyên thủy hay đối tượng; kiểm tra kiểu diễn ra khi biên dịch hay khi chạy?
3. **Thao tác:** cần lấy theo vị trí, thêm, xóa, tìm, sắp xếp hay chuyển đổi giữa các dạng dữ liệu?

Các ví dụ hoàn chỉnh sử dụng API Java 17. Chúng được biên dịch với `--release 17` và đã chạy để kiểm tra stdout.

## 1. Hai mô hình cần hình dung

### Mảng: một số vị trí được xác định khi tạo

Khai báo `new String[3]` tạo một đối tượng mảng có ba vị trí. Các vị trí được truy cập bằng chỉ số từ `0` đến `2`. Độ dài đó được giữ suốt vòng đời của đối tượng mảng.

Các vị trí luôn tồn tại sau khi mảng được tạo, kể cả khi bạn chưa gán dữ liệu riêng cho chúng. Java khởi tạo phần tử bằng giá trị mặc định của kiểu:

| Kiểu phần tử | Ví dụ giá trị mặc định |
| --- | --- |
| `int` | `0` |
| `boolean` | `false` |
| Kiểu tham chiếu như `String` | `null` |

Nếu chỉ hai vị trí đang chứa dữ liệu có ý nghĩa nghiệp vụ, chương trình phải tự phân biệt “độ dài mảng là ba” với “đang có hai bản ghi hợp lệ”.

### Collection: một nhóm phần tử với hợp đồng thao tác

`Collection<E>` là giao diện chung của nhiều kiểu tập hợp trong `java.util`. Một collection cho biết số phần tử hiện có qua `size()`; loại tập hợp cụ thể quy định thứ tự, trùng lặp và các thao tác được hỗ trợ.

Khi cần so sánh trực tiếp với mảng có chỉ số, `List<E>` là ví dụ gần gũi. `ArrayList<E>` là một lớp triển khai danh sách có khả năng thay đổi số phần tử.

Tuy nhiên:

- `Collection` rộng hơn `List`; một `Set` không cung cấp `get(index)`.
- Một collection có thể không hỗ trợ thêm hoặc xóa.
- `Map` thuộc hệ thống Java Collections Framework nhưng không kế thừa `Collection`.

Vì vậy, câu “collection thay đổi kích thước được” cần được gắn với loại và đối tượng thực tế. Với `ArrayList` khả biến, điều đó đúng; với danh sách từ `List.of`, việc thêm hoặc xóa bị cấm.

## 2. Bảng so sánh nền tảng

| Tiêu chí | Mảng | Collection |
| --- | --- | --- |
| Ví dụ kiểu | `int[]`, `String[]` | `Collection<String>`, `List<Integer>`, `Set<String>` |
| Đại lượng đếm | Thuộc tính `length` của mảng | Phương thức `size()` của collection |
| Số vị trí/phần tử | Độ dài đối tượng mảng cố định | Số phần tử có thể thay đổi nếu đối tượng hỗ trợ |
| Kiểu nguyên thủy | Có thể chứa trực tiếp `int`, `double`, ... | Tham số kiểu dùng kiểu tham chiếu như `Integer`, `Double` |
| Truy cập theo chỉ số | `array[index]` | Có ở `List` qua `get(index)`, không có ở mọi `Collection` |
| Thay một phần tử | Gán vào vị trí hợp lệ | `List.set` nếu được hỗ trợ |
| Thêm hoặc xóa phần tử | Tự tổ chức dữ liệu hoặc tạo mảng khác | Có thao tác trong hợp đồng; khả năng thực thi phụ thuộc đối tượng |
| Quy tắc trùng lặp | Các vị trí có thể chứa cùng giá trị | Theo loại tập hợp; `List` thường cho trùng, `Set` bảo đảm duy nhất theo quy tắc của nó |
| Công cụ thao tác | Cú pháp mảng và `java.util.Arrays` | Phương thức của giao diện/lớp và các công cụ phù hợp |
| Thông tin kiểu lúc chạy | Mảng giữ kiểu phần tử thực tế | Generics chủ yếu kiểm tra lúc biên dịch, có cơ chế xóa kiểu |

Bảng không có nghĩa một bên luôn tốt hơn. Ví dụ, việc cần thao tác thêm/xóa thường xuyên khác với việc cần lưu một khối số nguyên có số vị trí đã biết.

## 3. Kích thước, số phần tử và capacity

### Độ dài của mảng không tự tăng

Đoạn minh họa trong một phương thức:

```java
int[] scores = new int[3];
```

`scores.length` là `3`. Gán `scores[0] = 10` chỉ thay dữ liệu của một vị trí, không thay độ dài. Truy cập `scores[3]` vượt giới hạn và gây `ArrayIndexOutOfBoundsException`.

Có thể tạo một mảng lớn hơn bằng `Arrays.copyOf` rồi gán lại biến:

```java
int[] scores = {10, 20, 30};
scores = Arrays.copyOf(scores, 5);
```

Đoạn này cần `import java.util.Arrays;`. Nó tạo **đối tượng mảng khác** rồi cho biến `scores` tham chiếu tới mảng mới. Đối tượng mảng ban đầu không bị kéo dài. Hai vị trí mới của mảng số nguyên được điền giá trị mặc định `0`.

Nếu một biến khác vẫn tham chiếu tới mảng cũ, biến đó vẫn thấy mảng ba phần tử.

### Size và capacity của ArrayList khác nhau

Với:

```java
List<String> values = new ArrayList<>(3);
```

- `3` là dung lượng dự phòng ban đầu được yêu cầu cho cấu trúc lưu trữ.
- `values.size()` vẫn là `0`.
- Chưa có phần tử ở chỉ số `0`.
- `values.set(0, "A")` sẽ không tạo phần tử mới; chỉ số đó chưa hợp lệ.
- `values.add("A")` mới thêm phần tử đầu tiên.

`ArrayList` quản lý một vùng lưu trữ nội bộ. Khi cần, nó có thể tăng dung lượng và chuyển dữ liệu. Người sử dụng không phải tự theo dõi mảng bên trong.

`Collection` không cung cấp khái niệm capacity chung cho mọi kiểu tập hợp. Cũng không nên dựa vào một hệ số tăng dung lượng cụ thể để viết logic nghiệp vụ.

### Chương trình hoàn chỉnh

Tạo tệp **`ArrayAndListDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArrayAndListDemo {
    public static void main(String[] args) {
        String[] unusedSlots = new String[3];
        String[] array = {"A", "B", "C"};
        List<String> list = new ArrayList<>(3);

        System.out.println("default array = " + Arrays.toString(unusedSlots));
        System.out.println("array.length = " + array.length);
        System.out.println("list.size before add = " + list.size());

        list.add("A");
        list.add("B");
        list.add("C");
        list.add("D");
        System.out.println("list.size after add = " + list.size());

        array[1] = "X";
        list.set(1, "X");
        list.remove("C");

        System.out.println("array = " + Arrays.toString(array));
        System.out.println("list = " + list);
        System.out.println("list.size after remove = " + list.size());
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 ArrayAndListDemo.java
java -Dfile.encoding=UTF-8 ArrayAndListDemo
```

Stdout:

```text
default array = [null, null, null]
array.length = 3
list.size before add = 0
list.size after add = 4
array = [A, X, C]
list = [A, X, D]
list.size after remove = 3
```

Những điểm quan sát được:

1. Mảng mới có ba phần tử tham chiếu, đều là `null`.
2. Danh sách dự phòng chỗ cho ba phần tử vẫn có kích thước ban đầu bằng không.
3. Sau bốn lần `add`, danh sách chứa bốn phần tử.
4. Gán `array[1]` và gọi `list.set(1, ...)` đều thay phần tử đã tồn tại.
5. `list.remove("C")` làm giảm số phần tử từ bốn xuống ba; mảng vẫn có độ dài ba.

Số `3` ở cuối hai mô hình tình cờ bằng nhau, nhưng quá trình thay đổi dữ liệu và hợp đồng thao tác khác nhau.

## 4. Kiểu phần tử và kiểm tra kiểu

### Mảng chứa trực tiếp được giá trị nguyên thủy

```java
int[] quantities = {2, 4, 6};
String[] labels = {"small", "medium"};
```

Hai đoạn này minh họa mảng nguyên thủy và mảng tham chiếu.

Với generics, khai báo `List<int>` không hợp lệ. Ta dùng kiểu bao:

```java
List<Integer> quantities = new ArrayList<>();
quantities.add(2);
```

Các dòng về `List` cần những import tương ứng trong `java.util`. Giá trị `int` có thể được tự đóng hộp thành `Integer` khi thêm.

Hệ quả:

- `int[]` chứa các giá trị số nguyên trực tiếp.
- `List<Integer>` quản lý các tham chiếu đến đối tượng `Integer`.
- Một danh sách cho phép `null` có thể chứa phần tử không thể mở hộp thành `int`.
- Mô hình bộ nhớ và chi phí không giống nhau.

Không nên đổi mảng nguyên thủy sang collection chỉ để thống nhất cú pháp mà bỏ qua những khác biệt này.

### Mảng tham chiếu có tính hiệp biến

Trong Java, có thể gán một `String[]` cho biến `Object[]`:

```java
Object[] values = new String[1];
```

Biến được khai báo là `Object[]`, nhưng đối tượng thực tế vẫn là `String[]`. Khi gán một `Integer` vào mảng đó, Java kiểm tra kiểu phần tử lúc chạy và ném `ArrayStoreException`.

Sự cho phép gán giữa các kiểu mảng theo quan hệ kiểu con được gọi là **hiệp biến (covariance)**. Nó đem lại tính linh hoạt nhưng cần kiểm tra khi lưu phần tử.

### Generics có quy tắc gán khác

Đoạn dưới **cố tình không biên dịch**:

```java
List<Object> values = new ArrayList<String>();
```

`List<String>` không phải kiểu con của `List<Object>`. Nếu phép gán được cho phép, mã dùng biến `List<Object>` có thể thêm một đối tượng không phải chuỗi vào danh sách chuỗi.

Các kiểu tham số hóa như `List<T>` có tính **bất biến (invariance)** đối với tham số kiểu. Khi cần nhận nhiều kiểu phần tử trong một API, Java có wildcard như `? extends ...` hoặc `? super ...`, với các giới hạn đọc/ghi riêng. Đó là một lựa chọn thiết kế kiểu, không phải lý do dùng kiểu thô để bỏ kiểm tra.

### Thông tin kiểu lúc chạy khác nhau

- Một mảng `String[]` giữ kiểu thành phần thực tế để kiểm tra khi lưu.
- Một `List<String>` dùng generics để kiểm tra phần lớn việc sử dụng kiểu ở bước biên dịch.
- Khi dùng kiểu thô hoặc ép kiểu không an toàn, có thể phá vỡ bảo đảm của generics và gặp `ClassCastException` khi lấy dữ liệu.

Xóa kiểu không có nghĩa “generics không an toàn”. Nó giải thích vì sao cần giữ kiểm tra kiểu lúc biên dịch và tránh thao tác không an toàn.

## 5. Đóng hộp và bẫy mảng nguyên thủy

`Arrays.asList` nhận các đối số theo một kiểu tham chiếu. Một `int[]` tự nó là một đối tượng, nên lời gọi `Arrays.asList(intArray)` tạo danh sách có **một phần tử là cả mảng**, chứ không tạo danh sách các số đã đóng hộp.

Chương trình sau còn minh họa kiểm tra kiểu của mảng và lỗi mở hộp `null`.

Tạo tệp **`ArrayTypeDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArrayTypeDemo {
    public static void main(String[] args) {
        Object[] values = new String[1];
        try {
            values[0] = Integer.valueOf(7);
        } catch (ArrayStoreException exception) {
            System.out.println("array store: " + exception.getClass().getSimpleName());
        }

        int[] numbers = {2, 4, 6};
        List<int[]> oneArray = Arrays.asList(numbers);
        System.out.println("asList(int[]).size = " + oneArray.size());
        System.out.println("same array = " + (oneArray.get(0) == numbers));

        List<Integer> boxed = new ArrayList<>();
        for (int number : numbers) {
            boxed.add(number);
        }
        System.out.println("boxed = " + boxed);
        System.out.println("boxed.size = " + boxed.size());

        List<Integer> withNull = Arrays.asList(1, null, 3);
        try {
            int number = withNull.get(1);
            System.out.println(number);
        } catch (NullPointerException exception) {
            System.out.println("unboxing: " + exception.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 ArrayTypeDemo.java
java -Dfile.encoding=UTF-8 ArrayTypeDemo
```

Stdout:

```text
array store: ArrayStoreException
asList(int[]).size = 1
same array = true
boxed = [2, 4, 6]
boxed.size = 3
unboxing: NullPointerException
```

Giải thích:

1. Đối tượng `String[]` từ chối lưu một `Integer`, dù biến có kiểu `Object[]`.
2. `oneArray` có kiểu `List<int[]>`, kích thước bằng `1`.
3. Phần tử duy nhất của danh sách chính là tham chiếu tới `numbers`.
4. Vòng lặp chủ động thêm từng giá trị `int` vào `List<Integer>`, tạo ba phần tử qua đóng hộp.
5. Lấy `null` từ `List<Integer>` rồi gán cho biến `int` cần mở hộp, nên gây `NullPointerException`.

Chọn cách chuyển đổi theo kết quả mong muốn. “Danh sách các mảng” và “danh sách các số” là hai mô hình khác nhau.

## 6. So sánh các thao tác thường dùng

Với collection, cột cuối cần được đọc theo đúng giao diện: thao tác chỉ số thuộc `List`, không phải mọi `Collection`.

| Mục đích | Mảng | Collection hoặc List phù hợp |
| --- | --- | --- |
| Đếm | `array.length` | `group.size()` |
| Kiểm tra rỗng | `array.length == 0` | `group.isEmpty()` |
| Lấy theo vị trí | `array[index]` | `list.get(index)` |
| Thay phần tử đã có | `array[index] = value` | `list.set(index, value)` nếu hỗ trợ |
| Thêm phần tử | Tự quản lý chỗ trống hoặc tạo mảng mới | `add`, hoặc `List.add(index, value)` nếu hỗ trợ |
| Xóa theo giá trị | Tự tìm và tổ chức lại dữ liệu | `remove(value)` theo hợp đồng |
| Tìm sự tồn tại | Viết vòng lặp hoặc dùng công cụ phù hợp | `contains(value)` |
| Duyệt phần tử | for, for-each | for-each; iterator và các cơ chế của loại cụ thể |
| Sắp xếp tại chỗ | `Arrays.sort(array)` | `list.sort(comparator)` khi được hỗ trợ |
| Hiển thị nội dung | `Arrays.toString(array)` cho mảng một chiều | Các lớp tập hợp thông dụng có biểu diễn qua `toString()` |
| So sánh nội dung | `Arrays.equals(a, b)` cho mảng một chiều phù hợp | Theo hợp đồng `equals` của loại tập hợp |

### Thay phần tử khác với thêm phần tử

Với mảng, gán vào một vị trí không tạo thêm vị trí mới. Với danh sách, `set` thay phần tử còn `add` thêm phần tử. Hai thao tác không thể thay thế nhau.

Trong một danh sách có thứ tự, thêm hoặc xóa ở giữa còn có thể làm thay đổi chỉ số của các phần tử phía sau. Khi lưu một chỉ số để sử dụng về sau, cần tính đến khả năng cấu trúc đã thay đổi.

### Xóa phần tử số nguyên có hai cách hiểu

`List<Integer>` có thể gặp nhầm lẫn giữa:

- `remove(int index)`: xóa theo chỉ số.
- `remove(Object value)`: xóa theo giá trị.

Ví dụ minh họa đặt trong một phương thức:

```java
List<Integer> byIndex = new ArrayList<>(List.of(1, 2, 1));
byIndex.remove(1); // Xóa phần tử ở chỉ số 1: giá trị 2.

List<Integer> byValue = new ArrayList<>(List.of(1, 2, 1));
byValue.remove(Integer.valueOf(1)); // Xóa một lần xuất hiện của giá trị 1.
```

Sau đó `byIndex` chứa `[1, 1]`, còn `byValue` chứa `[2, 1]`. Đây là khác biệt do nạp chồng phương thức và kiểu đối số.

### Quy tắc bằng nhau khác nhau

Gọi `a.equals(b)` trên hai mảng không so từng phần tử như `Arrays.equals(a, b)`; mảng sử dụng quy tắc đồng nhất đối tượng cho `equals`.

Đối với danh sách, hợp đồng `List.equals` xét phần tử tương ứng và thứ tự. Đối với tập hợp, hợp đồng `Set.equals` xét thành viên theo quy tắc tập hợp.

Không nên suy ra một quy tắc `equals` theo nội dung duy nhất cho mọi kiểu `Collection`. Cần xem hợp đồng của loại tập hợp đang dùng. Với mảng lồng nhau, khi cần so sánh sâu, phải chọn công cụ phù hợp như `Arrays.deepEquals`.

## 7. Từ mảng sang danh sách: góc nhìn hay bản sao?

Đây là nơi các khái niệm “cố định kích thước”, “không sửa đổi được” và “độc lập dữ liệu” thường bị nhầm.

Giả sử `source` là một `String[]` không chứa `null`:

| Cách tạo | Thêm/xóa phần tử | Thay phần tử bằng set | Quan hệ với mảng nguồn |
| --- | --- | --- | --- |
| `Arrays.asList(source)` | Không | Có | Góc nhìn được chống lưng bởi mảng nguồn |
| `new ArrayList<>(Arrays.asList(source))` | Có | Có | Cấu trúc danh sách được sao chép |
| `List.copyOf(Arrays.asList(source))` | Không | Không | Snapshot không phản ánh thay đổi cấu trúc/phần tử của nguồn sau khi tạo |

Ở đây, “snapshot” có nghĩa danh sách kết quả giữ tập phần tử ở thời điểm tạo theo hợp đồng. Không nên suy luận rằng lời gọi luôn cấp phát một đối tượng mới trong mọi trường hợp đầu vào.

### Arrays.asList là danh sách cố định kích thước

Với một mảng tham chiếu, danh sách từ `Arrays.asList` có cùng số phần tử với mảng:

- `set` cập nhật một vị trí và có thể được quan sát từ mảng.
- Thay một phần tử trong mảng cũng được quan sát từ danh sách.
- `add` hoặc `remove` làm thay đổi số phần tử nên không được hỗ trợ.

Nó là **góc nhìn (view)** cùng dữ liệu nền, không phải một `java.util.ArrayList` có khả năng tự tăng kích thước.

### Danh sách không sửa đổi được có chính sách khác

`List.of(...)` và `List.copyOf(...)` trả về danh sách không cho phép sửa đổi. Những lời gọi thay đổi như `add`, `remove`, `set` không được hỗ trợ; các cách tạo này cũng không chấp nhận phần tử `null`.

Tính không sửa đổi được của danh sách chỉ nói về cấu trúc và các phần tử được lưu ở các vị trí của nó. Nếu phần tử là một đối tượng khả biến, dữ liệu bên trong đối tượng đó vẫn có thể thay đổi.

Các cách sao chép thông dụng trong phần này đều là **sao chép nông (shallow copy)** đối với đối tượng phần tử: chúng sao chép các tham chiếu, không tự nhân bản toàn bộ đồ thị đối tượng.

## 8. Chương trình hoàn chỉnh: phân biệt view, copy và snapshot

Tạo tệp **`ArrayViewCopyDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArrayViewCopyDemo {
    public static void main(String[] args) {
        String[] source = {"red", "blue"};
        List<String> view = Arrays.asList(source);
        List<String> copy = new ArrayList<>(view);

        view.set(0, "green");
        copy.add("black");

        List<String> snapshot = List.copyOf(view);
        source[1] = "white";

        System.out.println("source = " + Arrays.toString(source));
        System.out.println("view = " + view);
        System.out.println("copy = " + copy);
        System.out.println("snapshot = " + snapshot);

        try {
            view.add("yellow");
        } catch (UnsupportedOperationException exception) {
            System.out.println("view.add: " + exception.getClass().getSimpleName());
        }

        try {
            snapshot.set(0, "yellow");
        } catch (UnsupportedOperationException exception) {
            System.out.println("snapshot.set: " + exception.getClass().getSimpleName());
        }

        String[] exported = view.toArray(String[]::new);
        exported[0] = "detached";

        System.out.println("exported = " + Arrays.toString(exported));
        System.out.println("source after export = " + Arrays.toString(source));
    }
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 --release 17 ArrayViewCopyDemo.java
java -Dfile.encoding=UTF-8 ArrayViewCopyDemo
```

Stdout:

```text
source = [green, white]
view = [green, white]
copy = [red, blue, black]
snapshot = [green, blue]
view.add: UnsupportedOperationException
snapshot.set: UnsupportedOperationException
exported = [detached, white]
source after export = [green, white]
```

### Theo dõi từng biến

- `view.set(0, "green")` thay phần tử trong dữ liệu dùng chung với `source`.
- `copy` được tạo trước thay đổi đó, nên vẫn giữ `"red"` ở vị trí đầu. Việc `copy.add("black")` chỉ tăng danh sách bản sao.
- `snapshot` được tạo sau lần thay đầu tiên, nên giữ `[green, blue]`.
- `source[1] = "white"` tác động đến `view` nhưng không đổi tập phần tử của `copy` hoặc `snapshot`.
- `view.add` thất bại vì kích thước gắn với mảng cố định.
- `snapshot.set` thất bại vì danh sách không cho phép sửa đổi.
- Mảng `exported` do `toArray` tạo ra có cấu trúc riêng; gán lại `exported[0]` không thay phần tử của `source`.

Các phần tử đều là `String`, một kiểu bất biến. Khi thay chuỗi ở một vị trí, chương trình thay tham chiếu của vị trí đó. Nếu dùng phần tử là đối tượng khả biến, việc sửa bên trong đối tượng chung vẫn có thể được nhìn thấy từ nhiều cấu trúc.

## 9. Từ collection sang mảng

### Dùng kiểu mảng phù hợp

Với một `Collection<String>`, có thể viết:

```java
String[] result = values.toArray(String[]::new);
```

Đây là đoạn minh họa cần một biến `values` đã được khai báo và chứa các phần tử hợp lệ. Cú pháp `String[]::new` cung cấp cách tạo mảng đúng kiểu. Các phần tử được đưa vào theo thứ tự duyệt của collection, nếu collection có cam kết thứ tự.

Một cách khác là:

```java
String[] result = values.toArray(new String[0]);
```

Cả hai lựa chọn này tránh nhầm kiểu mảng kết quả. Chương trình hoàn chỉnh ở phần trước dùng cách thứ nhất.

### Không ép Object[] thành String[] theo kiểu suy đoán

`values.toArray()` không có đối số trả về `Object[]`. Không nên viết:

```java
String[] result = (String[]) values.toArray();
```

Đoạn này có thể biên dịch nhưng mảng kết quả thực tế là `Object[]`, nên phép ép sang `String[]` gây `ClassCastException`. Việc các phần tử đang là chuỗi không làm đổi kiểu thực của đối tượng mảng.

### Trở lại mảng nguyên thủy cần chuyển từng giá trị

Từ một `List<Integer>`, việc gọi `toArray` không tạo trực tiếp được `int[]`. Cần chuyển từng giá trị, ví dụ bằng vòng lặp và mở hộp hoặc công cụ chuyển đổi phù hợp.

Khi mở hộp, phải xác định cách xử lý `null`. Đừng để một giả định “mọi phần tử đều có số” chỉ được phát hiện bằng ngoại lệ lúc chương trình đang chạy.

## 10. Chi phí và cách chọn cấu trúc

Mảng và collection không thể được xếp hạng bằng một câu “cái nào nhanh hơn”.

### Những chi phí cần nhìn vào

- **Lưu số nguyên thủy:** `int[]` tránh mô hình đối tượng bao của `List<Integer>`.
- **Thay đổi số phần tử:** `ArrayList` quản lý tăng dung lượng; một số lần thêm có thể phải chuyển dữ liệu.
- **Thêm/xóa giữa danh sách:** `ArrayList` thường cần dịch chuyển các phần tử phía sau.
- **Tìm theo giá trị:** duyệt mảng hay duyệt danh sách có thể phải kiểm tra nhiều phần tử.
- **Tập hợp có cấu trúc tìm kiếm riêng:** có những lựa chọn tối ưu cho tìm hoặc tính duy nhất, với chi phí và quy tắc khác.
- **Sao chép và chuyển đổi:** thêm cấp phát, đọc dữ liệu và có thể phát sinh đóng hộp hoặc mở hộp.

Cả truy cập chỉ số của mảng và `ArrayList.get` đều có chi phí không tăng theo số phần tử trong mô hình sử dụng thông thường. Điều đó không làm mọi thao tác của chúng có cùng chi phí.

Cũng không thể suy ra rằng tất cả collection truy cập chỉ số hiệu quả như nhau hoặc đều hỗ trợ chỉ số. Hãy đọc đặc điểm của lớp cụ thể và đo đạc nếu hiệu năng là yêu cầu thực tế.

### Một quy trình lựa chọn

1. **Xác định ý nghĩa dữ liệu.** Một vectơ số có độ dài cố định khác với giỏ hàng có số dòng thay đổi.
2. **Xác định kiểu phần tử.** Có cần lưu trực tiếp giá trị nguyên thủy không?
3. **Xác định quy tắc nhóm.** Có cần trùng lặp, thứ tự, chỉ số hay tính duy nhất?
4. **Xác định thao tác chủ yếu.** Đọc, duyệt, tìm, thêm cuối, thêm giữa hoặc xóa?
5. **Xác định khả năng sửa đổi.** Dữ liệu là bản sao độc lập, góc nhìn dùng chung hay snapshot?
6. **Kiểm tra hợp đồng nơi nhận dữ liệu.** API yêu cầu mảng hay một giao diện tập hợp?
7. **Đánh giá đồng thời và chi phí.** Không mặc định mảng hoặc `ArrayList` tự bảo vệ trước các truy cập sửa đổi đồng thời.

Ví dụ định hướng:

| Tình huống | Lựa chọn nên xét |
| --- | --- |
| Lưu một khối số nguyên với số vị trí xác định | `int[]` |
| Danh sách dòng dữ liệu được thêm/xóa và có thứ tự | `List` với lớp triển khai phù hợp |
| Tập mã không được trùng | `Set` |
| Tra dữ liệu theo khóa | `Map` |
| Gọi một API nhận tham số mảng | Chuyển sang mảng đúng kiểu ở ranh giới API |

Tên gọi “danh sách” trong yêu cầu thường ngày không đủ để quyết định cấu trúc. Quy tắc và thao tác mới là dữ liệu cần dùng để chọn.

## 11. Các lỗi dễ mắc

### Nhầm capacity với size

`new ArrayList<>(100)` tạo một danh sách rỗng với yêu cầu dung lượng ban đầu; nó không tạo một trăm phần tử `null`. Hãy dùng `add` để tạo phần tử.

### Gán null để coi là đã xóa khỏi mảng

`array[index] = null` chỉ thay giá trị vị trí đó. `array.length` không giảm. Nếu dùng mảng như một vùng chứa có phần đang hoạt động, phải tự cập nhật quy tắc và số phần tử đang dùng.

### Tin mọi collection đều có get(index)

`get` là thao tác của `List`. Khi đoạn mã chỉ có `Collection<E>`, cần làm việc theo hợp đồng chung hoặc lựa chọn lại kiểu tham số theo đúng nhu cầu.

### Tin mọi danh sách từ mảng đều tự tăng được

`Arrays.asList` tạo danh sách cố định kích thước. Khi cần thêm/xóa, tạo collection khả biến phù hợp, chẳng hạn `new ArrayList<>(...)`.

### Dùng Arrays.asList để tự động đóng hộp một int[]

Lời gọi này tạo `List<int[]>` có một phần tử. Hãy kiểm tra kiểu khai báo và số phần tử thay vì chỉ nhìn tên hàm.

### Đánh đồng bản sao cấu trúc với bản sao sâu

Mảng mới hoặc danh sách mới có thể vẫn tham chiếu tới cùng đối tượng phần tử. Cần chính sách sao chép riêng nếu muốn phần tử cũng độc lập.

### Nhầm cố định kích thước với không sửa đổi được

Danh sách từ `Arrays.asList` cho phép `set`; danh sách từ `List.of` không cho phép `set`. Cả hai đều không thêm/xóa được, nhưng mức cho phép thay đổi không giống nhau.

## 12. Thực hành kiểm chứng

### Bài 1: dự đoán size và nội dung

Sửa `ArrayAndListDemo`:

1. Đổi dung lượng ban đầu của `ArrayList` thành `1`.
2. Giữ nguyên các lần `add`, `set` và `remove`.
3. Dự đoán có bao nhiêu phần tử sau bốn lần thêm.
4. Chạy và đối chiếu với kết quả ban đầu.

Dung lượng dự phòng ban đầu không phải giới hạn số phần tử của `ArrayList`. Với các thao tác được giữ nguyên, số phần tử sau thêm vẫn là `4`.

### Bài 2: theo dõi dữ liệu dùng chung

Trong `ArrayViewCopyDemo`, đổi:

```java
source[1] = "white";
```

thành:

```java
source[0] = "orange";
```

Trước khi chạy, viết nội dung dự đoán của `source`, `view`, `copy` và `snapshot`. Giải thích dự đoán dựa trên thời điểm tạo và quan hệ dùng chung, thay vì học thuộc stdout.

### Bài 3: chọn kiểu và thao tác

Với từng yêu cầu sau, hãy xác định cấu trúc, kiểu phần tử và cách thêm/xóa:

- Lưu 24 nhiệt độ theo từng giờ trong một ngày.
- Lưu các dòng sản phẩm trong một giỏ hàng được chỉnh sửa.
- Lưu những mã sản phẩm đã được đánh dấu, không chấp nhận trùng.
- Trả một mảng chuỗi cho API cũ từ dữ liệu đang có trong collection.

Hãy chỉ ra thao tác nào thay giá trị, thao tác nào thay số phần tử, trường hợp nào cần chuyển đổi và liệu có dữ liệu dùng chung sau chuyển đổi hay không.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-006">Phân biệt thứ tự chèn, thứ tự duyệt, chỉ số và thứ tự sắp xếp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt thứ tự chèn, thứ tự duyệt, chỉ số và thứ tự sắp xếp

Trong Java Collections, từ “thứ tự” có thể đang nói đến những điều khác nhau. Một tập hợp có thể giữ thứ tự chèn nhưng không có chỉ số. Một tập hợp khác có thể duyệt theo thứ tự sắp xếp nhưng không cho phép gọi `get(0)`. Một hàng đợi có thể lấy phần tử theo độ ưu tiên nhưng iterator lại không duyệt theo thứ tự ưu tiên đó.

Để chọn đúng cấu trúc dữ liệu và đọc đúng kết quả chương trình, cần tách bốn khái niệm: **thứ tự chèn**, **thứ tự duyệt**, **chỉ số** và **thứ tự sắp xếp**.

## 1. Bốn câu hỏi khác nhau

| Khái niệm | Câu hỏi cần trả lời | Ví dụ |
| --- | --- | --- |
| Thứ tự chèn (insertion order) | Các phần tử được thêm vào tập hợp theo trình tự nào, và tập hợp có giữ trình tự đó không? | Thêm `B`, rồi `A`, rồi `C` vào `LinkedHashSet`; duyệt nhận `B, A, C`. |
| Thứ tự duyệt (iteration order) | Trong một lượt duyệt, iterator trả về các phần tử theo trình tự nào? | `TreeSet` duyệt theo quy tắc so sánh, không theo lịch sử thêm. |
| Chỉ số (index) | Phần tử đang nằm tại vị trí đánh số nào trong một dãy? | Trong `List.of(30, 10, 20)`, chỉ số `1` chứa giá trị `10`. |
| Thứ tự sắp xếp (sorted order) | Các phần tử có tuân theo một quan hệ so sánh không? | Với số nguyên tăng dần: `10, 20, 30`. |

Bốn khái niệm có thể cùng xuất hiện, nhưng không thay thế nhau:

- Thứ tự chèn có thể quyết định thứ tự duyệt, như các thao tác thêm thông thường của `LinkedHashSet`.
- Thứ tự sắp xếp có thể quyết định thứ tự duyệt, như `TreeSet`.
- Chỉ số gắn với dãy có vị trí như `List`; thứ tự duyệt xác định không tự tạo ra API chỉ số.
- Một dãy có chỉ số có thể chưa được sắp xếp.

Chỉ nhìn kết quả `[10, 20, 30]` chưa đủ để biết dữ liệu đã được sắp xếp, được chèn sẵn theo thứ tự đó, hay tình cờ được duyệt như vậy.

## 2. Thứ tự chèn: lịch sử thêm và cam kết giữ thứ tự

Giả sử các lệnh được thực hiện theo trình tự:

~~~java
// Đoạn mã trong một phương thức; cần import java.util.LinkedHashSet.
LinkedHashSet<String> values = new LinkedHashSet<>();
values.add("B");
values.add("A");
values.add("C");

System.out.println(values); // [B, A, C]
~~~

Với các thao tác `add` thông thường này, `LinkedHashSet` giữ thứ tự chèn. Thứ tự duyệt phản ánh trình tự thêm `B`, `A`, `C`.

Điều đó không có nghĩa mọi phương thức tên `add` trên mọi loại tập hợp đều giữ lịch sử chèn theo cùng một cách.

### Thêm lại phần tử đã tồn tại

`Set` không thêm một bản sao chỉ vì gọi `add` lần nữa:

~~~java
// Tiếp tục với values ở trên.
System.out.println(values.add("A")); // false
System.out.println(values);          // [B, A, C]
~~~

Phần tử `A` đã có trong tập hợp. Lần gọi `add("A")` thông thường không chuyển nó xuống cuối.

Nhưng xóa rồi thêm lại là một tình huống khác:

~~~java
values.remove("A");
values.add("A");

System.out.println(values); // [B, C, A]
~~~

Sau khi bị xóa, `A` không còn vị trí cũ. Lần thêm mới đặt nó vào cuối thứ tự chèn hiện tại.

### List có thể chèn vào giữa

Một danh sách thường được tạo bằng cách thêm vào cuối nên ban đầu thứ tự của nó trùng với trình tự các lần thêm. Tuy nhiên, `List` còn cho chèn vào vị trí cụ thể:

~~~java
// Cần import java.util.ArrayList và java.util.List.
List<String> list = new ArrayList<>();
list.add("B");
list.add("C");
list.add(0, "A");

System.out.println(list); // [A, B, C]
~~~

Lịch sử gọi thêm là `B, C, A`, nhưng dãy hiện tại là `A, B, C`. Phần tử mới không nhất thiết đứng sau phần tử cũ: thao tác chèn theo chỉ số có ý nghĩa riêng.

Vì vậy, khi đọc `List`, nên nói rõ **thứ tự vị trí hiện tại**. Không suy ra lịch sử tạo hoặc thêm phần tử chỉ từ kết quả duyệt sau nhiều thao tác chỉnh sửa.

### Deque cho thấy rõ sự khác nhau này

~~~java
// Cần import java.util.ArrayDeque và java.util.Deque.
Deque<String> deque = new ArrayDeque<>();
deque.addLast("B");
deque.addLast("C");
deque.addFirst("A");

System.out.println(deque); // [A, B, C]
~~~

Phần tử `A` được thêm sau cùng nhưng được đặt ở đầu. Iterator thông thường duyệt từ đầu đến cuối deque, nên thứ tự duyệt không trùng với lịch sử thời gian của các lời gọi thêm.

## 3. Thứ tự duyệt: điều iterator thực sự trả về

Thứ tự duyệt là trình tự phần tử quan sát được khi dùng iterator. For-each trên một `Iterable` sử dụng cơ chế đó:

~~~java
// Đoạn mã minh họa; source là một Iterable<String> đã có.
for (String value : source) {
    System.out.println(value);
}
~~~

Câu hỏi cần đặt ra không chỉ là “lần chạy này in gì?”, mà còn là “hợp đồng của loại tập hợp có cam kết trình tự đó không?”.

### Ba trường hợp điển hình

- `List`: iterator thông thường đi theo thứ tự vị trí của danh sách.
- `LinkedHashSet`: với các thao tác thêm thông thường, duyệt theo thứ tự chèn.
- `TreeSet`: duyệt theo thứ tự tăng dần của quan hệ so sánh đang dùng.

`HashSet` không cam kết thứ tự duyệt. Điều này không đồng nghĩa “mỗi lần duyệt đều ngẫu nhiên” hay “mỗi lần chạy chắc chắn khác nhau”.

Một `HashSet` có thể nhiều lần hiển thị cùng trình tự. Với một số dữ liệu số nguyên, trình tự tình cờ còn có thể trông như đã sắp xếp. Kết quả quan sát đó không trở thành cam kết API. Thay dữ liệu, thay lịch sử thêm/xóa, thay cấu trúc nội bộ hoặc thay phiên bản triển khai có thể làm kết quả khác đi.

Không viết kiểm thử dựa trên một thứ tự mà hợp đồng không cung cấp.

### toString không chứng minh có sắp xếp

Nhiều lớp tập hợp tạo chuỗi hiển thị dựa trên iterator. Nếu nhìn thấy:

~~~text
[10, 20, 30]
~~~

không thể kết luận nguồn có hợp đồng sắp xếp. Muốn kết luận, cần biết lớp triển khai, comparator nếu có, và thao tác đã thực hiện.

Việc sao chép `HashSet` sang `ArrayList` cũng chỉ lấy một thứ tự duyệt của nguồn tại lần sao chép đó:

~~~java
// Đoạn mã trong một phương thức; cần các import java.util tương ứng.
Set<Integer> source = new HashSet<>(List.of(30, 10, 20));
List<Integer> copy = new ArrayList<>(source);
~~~

`copy` có chỉ số và giữ dãy đã sao chép, nhưng thao tác này không khôi phục lịch sử chèn của `source` và không tự sắp xếp dữ liệu. Ví dụ giả định không có thay đổi đồng thời trong khi sao chép.

## 4. Chỉ số: vị trí hiện tại, không phải danh tính

Chỉ số của `List` bắt đầu từ `0`. Phần tử đầu tiên ở chỉ số `0`, phần tử thứ hai ở chỉ số `1`.

~~~java
List<Integer> values = new ArrayList<>(List.of(30, 10, 20));

System.out.println(values.get(1)); // 10
values.add(1, 99);

System.out.println(values);       // [30, 99, 10, 20]
System.out.println(values.get(1)); // 99
System.out.println(values.get(2)); // 10
~~~

Giá trị `10` vẫn có trong danh sách nhưng chuyển từ chỉ số `1` sang chỉ số `2`. Điều này cho thấy chỉ số mô tả **vị trí ở thời điểm hiện tại**, không phải mã nhận dạng ổn định.

Các thao tác sau có thể thay đổi phần tử mà một chỉ số đang trỏ đến:

- Chèn hoặc xóa trước vị trí đó.
- Sắp xếp lại danh sách.
- Đảo dãy.
- Thay thế phần tử bằng `set(index, value)`.

Không lưu chỉ số của một đơn hàng rồi coi nó như ID của đơn hàng qua nhiều lần thay đổi danh sách. Khi cần nhận diện một thực thể, hãy dùng định danh thuộc về dữ liệu hoặc một cấu trúc tra cứu phù hợp.

### Thứ tự duyệt không kéo theo get(index)

`LinkedHashSet`, `TreeSet` và `ArrayDeque` có thể có thứ tự duyệt xác định, nhưng không cung cấp hợp đồng `List.get(index)`.

Có thể tự duyệt để đếm đến phần tử thứ ba, nhưng đó là một thuật toán duyệt, không phải bằng chứng rằng tập hợp có API truy cập theo chỉ số. Nếu tạo `new ArrayList<>(set)`, API chỉ số thuộc danh sách mới.

Ngược lại, có chỉ số cũng không bảo đảm chi phí truy cập luôn giống nhau: `ArrayList` và `LinkedList` đều triển khai `List`, nhưng cách tìm phần tử tại chỉ số khác nhau.

## 5. Thứ tự sắp xếp: cần một quy tắc so sánh

Một dãy được sắp xếp khi các phần tử tuân theo một quan hệ so sánh đã chọn. Quy tắc đó có thể là:

- Thứ tự tự nhiên của phần tử qua `Comparable`.
- Một `Comparator` do chương trình cung cấp.
- Nhiều tiêu chí ghép lại, chẳng hạn độ ưu tiên rồi đến mã tác vụ.

“Sắp xếp tăng dần” chỉ rõ ý nghĩa khi biết tăng theo thuộc tính nào.

Với số nguyên, thứ tự tự nhiên thường được hiểu là thứ tự giá trị số. Với `String`, thứ tự tự nhiên là thứ tự từ điển theo quy tắc so sánh của `String`, không mặc nhiên là thứ tự ngôn ngữ tự nhiên theo từng địa phương. Nếu nghiệp vụ cần thứ tự theo ngôn ngữ, phải chọn công cụ và quy tắc so sánh tương ứng.

### List không tự duy trì thứ tự sắp xếp

~~~java
// Đoạn mã trong một phương thức; cần import java.util.Comparator.
List<Integer> numbers = new ArrayList<>(List.of(30, 10, 20));
numbers.sort(Comparator.naturalOrder());

System.out.println(numbers); // [10, 20, 30]

numbers.add(5);
System.out.println(numbers); // [10, 20, 30, 5]
~~~

Sau `sort`, danh sách thỏa quy tắc tăng dần. Nhưng `List` không tự sắp lại mỗi khi thêm phần tử. Sau `add(5)`, kết quả không còn tăng dần.

Cần phân biệt “đã được sắp xếp ở bước này” với “cấu trúc dữ liệu luôn duy trì bất biến sắp xếp”.

### TreeSet duy trì quy tắc sắp xếp

~~~java
// Cần import java.util.Set và java.util.TreeSet.
Set<Integer> numbers = new TreeSet<>();
numbers.add(30);
numbers.add(10);
numbers.add(20);
numbers.add(5);

System.out.println(numbers); // [5, 10, 20, 30]
~~~

`TreeSet` đặt các phần tử theo quan hệ so sánh khi quản lý dữ liệu. Thêm `5` không có nghĩa nó sẽ đứng cuối thứ tự duyệt.

Khai báo biến bằng `Set<Integer>` không xóa hành vi của đối tượng `TreeSet`, nhưng làm mất khả năng gọi trực tiếp một số API riêng của các interface tập hợp sắp xếp qua biến đó. Nếu phương thức cần hợp đồng sắp xếp, nên biểu đạt điều đó bằng kiểu tham số phù hợp như `SortedSet` hoặc `NavigableSet`, thay vì chỉ dựa vào giả định ngầm.

Không tự ý sửa thuộc tính dùng để so sánh khi đối tượng đang nằm trong `TreeSet`. Thay đổi bên trong phần tử không khiến cấu trúc tự tìm lại vị trí đúng. Khi cần đổi khóa sắp xếp, phải thiết kế thao tác cập nhật phù hợp, chẳng hạn lấy ra trước rồi thêm lại sau khi thay đổi.

## 6. Queue: thứ tự lấy chưa chắc là thứ tự duyệt

Với hàng đợi FIFO, thường có thể hình dung phần tử được thêm trước sẽ được lấy trước. Nhưng `PriorityQueue` chọn phần tử ở đầu theo độ ưu tiên.

~~~java
// Đoạn mã trong một phương thức; cần import Queue và PriorityQueue.
Queue<Integer> queue = new PriorityQueue<>(List.of(40, 10, 30, 20));
~~~

Với thứ tự tự nhiên của số nguyên:

- Liên tục gọi `poll()` sẽ lấy `10, 20, 30, 40`.
- For-each trên `queue` không được cam kết trả về trình tự `10, 20, 30, 40`.
- In trực tiếp `queue` không phải cách chứng minh toàn bộ các phần tử đã được sắp xếp.

`PriorityQueue` bảo đảm về phần tử ở đầu theo quy tắc của nó, không bảo đảm iterator là một danh sách ưu tiên đã sắp xếp hoàn chỉnh.

Muốn xử lý theo độ ưu tiên, dùng thao tác lấy của hàng đợi. Muốn tạo một bản hiển thị đã sắp xếp mà không tiêu thụ hàng đợi gốc, có thể sao chép dữ liệu rồi sắp xếp bản sao theo quy tắc mong muốn.

Nếu nhiều phần tử có cùng mức ưu tiên, `PriorityQueue` không tự bảo đảm xử lý chúng theo thứ tự chèn. Nếu nghiệp vụ yêu cầu “cùng độ ưu tiên thì ai đến trước được xử lý trước”, cần một tiêu chí phụ, chẳng hạn số thứ tự được cấp lúc tiếp nhận, và comparator phải xét tiêu chí đó.

## 7. So sánh các cấu trúc thường gặp

Bảng dưới xét các thao tác thông thường trong một luồng; không giả định có chỉnh sửa đồng thời.

| Cấu trúc | Điều quyết định trình tự hiện tại | Cam kết duyệt đáng chú ý | Có API chỉ số không? | Tự duy trì sắp xếp theo so sánh không? |
| --- | --- | --- | --- | --- |
| `ArrayList` | Các vị trí sau khi thêm, chèn, xóa, thay thế hoặc sắp xếp | Theo thứ tự vị trí danh sách | Có | Không |
| `LinkedList` khi dùng như `List` | Các vị trí của danh sách | Theo thứ tự vị trí danh sách | Có | Không |
| `HashSet` | Cấu trúc nội bộ; không có cam kết thứ tự duyệt | Không dựa vào một trình tự cụ thể | Không | Không |
| `LinkedHashSet` | Thứ tự chèn với các thao tác `add` thông thường | Theo thứ tự được giữ trong tập hợp | Không | Không |
| `TreeSet` | Quan hệ so sánh tự nhiên hoặc comparator | Theo thứ tự so sánh tăng dần | Không | Có |
| `ArrayDeque` | Các thao tác thêm/xóa ở đầu và cuối | Iterator thông thường đi từ đầu đến cuối | Không | Không |
| `PriorityQueue` | Quy tắc ưu tiên cho phần tử ở đầu | Iterator không cam kết theo thứ tự ưu tiên | Không | Không có cam kết sắp toàn bộ thứ tự duyệt; lấy đầu theo ưu tiên |

Một cấu trúc có thể có đầu và cuối mà không có chỉ số. Nó cũng có thể có phần tử nhỏ nhất ở đầu mà không có thứ tự duyệt đã sắp xếp.

## 8. Phần tử bằng nhau theo so sánh và sắp xếp ổn định

Khi comparator trả `0`, hai phần tử được xem là ngang nhau **theo tiêu chí so sánh đó**. Điều này không nhất thiết có nghĩa hai đối tượng `equals` nhau.

Giả sử các tác vụ ban đầu là:

~~~text
C:2, D:1, A:2, B:1
~~~

Mỗi cặp có dạng `mã:độ_ưu_tiên`. Comparator chỉ xét độ ưu tiên, từ nhỏ đến lớn.

### Stable sort giữ thứ tự tương đối trước khi sort

`List.sort` có tính ổn định: các phần tử được comparator xem là bằng nhau giữ thứ tự tương đối trước khi sắp xếp.

Kết quả:

~~~text
D:1, B:1, C:2, A:2
~~~

`D` vẫn đứng trước `B` trong nhóm ưu tiên `1`; `C` vẫn đứng trước `A` trong nhóm ưu tiên `2`.

“Ổn định” ở đây nói đến thứ tự của dãy ngay trước lần sort, không phải lịch sử chèn ban đầu của toàn bộ vòng đời dữ liệu. Nếu dãy đã bị đảo hoặc chỉnh sửa, thứ tự được bảo toàn trong các nhóm bằng nhau là thứ tự sau những thao tác đó.

Stable sort cũng không tự làm dữ liệu đầu vào có thứ tự xác định. Nếu danh sách được lấy từ nguồn không cam kết thứ tự, các phần tử cùng mức ưu tiên có thể xuất hiện theo thứ tự khác nhau ở các lần chạy; stable sort chỉ giữ thứ tự đầu vào mà nó nhận được.

### Tiêu chí phụ tạo ra quy tắc rõ ràng hơn

Nếu muốn cùng độ ưu tiên thì sắp theo mã tác vụ:

~~~java
// Job là kiểu có các phương thức priority() và id().
Comparator<Job> order = Comparator.comparingInt(Job::priority)
        .thenComparing(Job::id);
~~~

Kết quả cho dữ liệu trên là:

~~~text
B:1, D:1, A:2, C:2
~~~

Đây không phải stable sort bị thay đổi hành vi. Comparator đã phân biệt thêm các phần tử mà trước đây nó xem là bằng nhau.

Đối với phân trang hoặc kết quả cần tái lập, nên chọn tiêu chí phụ đủ để xác định thứ tự theo yêu cầu nghiệp vụ. Chỉ sắp theo một thuộc tính có nhiều giá trị trùng thường chưa đủ để có ranh giới trang ổn định.

### TreeSet dùng so sánh để quyết định trùng phần tử

Với cùng bốn tác vụ và comparator chỉ xét độ ưu tiên, `TreeSet` chỉ giữ một phần tử cho mỗi mức ưu tiên. Theo thứ tự thêm `C:2, D:1, A:2, B:1`, kết quả là:

~~~text
[D:1, C:2]
~~~

`A:2` và `B:1` không được thêm như phần tử độc lập vì comparator trả `0` khi so với phần tử đã có ở cùng mức ưu tiên.

Nếu các tác vụ có mã khác nhau phải được xem là khác nhau, comparator chỉ xét ưu tiên là không phù hợp cho `TreeSet`. Với kiểu `Job` trong chương trình dưới đây, `equals` còn xét mã; comparator chỉ xét ưu tiên không nhất quán với `equals`, nên không đáp ứng đầy đủ hợp đồng chung của `Set`.

Có thể:

- Dùng `List` rồi sort nếu cần giữ tất cả các tác vụ, kể cả những tác vụ ngang nhau theo tiêu chí.
- Hoặc dùng comparator có tiêu chí phụ phù hợp với định nghĩa duy nhất của dữ liệu khi thật sự cần `TreeSet`.

Không chuyển từ “danh sách cần được sắp xếp” sang `TreeSet` chỉ vì muốn các phần tử có thứ tự; thao tác đó có thể làm mất dữ liệu theo quy tắc trùng của tập hợp.

## 9. Từ Java 21: sequenced không có nghĩa là indexed hay sorted

Java 21 bổ sung các interface như `SequencedCollection` và `SequencedSet` để biểu đạt tập hợp có thứ tự duyệt xác định, cùng các thao tác liên quan đến đầu, cuối và góc nhìn đảo thứ tự.

Cần giữ ba ranh giới:

1. **Sequenced không đồng nghĩa sorted.** Một dãy `B, A, C` có thứ tự duyệt xác định nhưng không nhất thiết thỏa một comparator.
2. **Sequenced không đồng nghĩa indexed.** `LinkedHashSet` có thể có đầu và cuối nhưng không trở thành `List` có `get(index)`.
3. **Có API chung không có nghĩa mọi thao tác thay đổi đều được hỗ trợ.** Một tập hợp có quy tắc sắp xếp phải bảo toàn quy tắc đó, không thể mặc nhiên cho chèn tùy ý vào đầu hoặc cuối.

Các API mới còn cho phép định vị lại phần tử trong một số cấu trúc. Chẳng hạn, `LinkedHashSet` từ Java 21 có `addFirst` và `addLast` để đặt phần tử vào đầu hoặc cuối. Vì vậy, khi phân tích thứ tự của một đối tượng thực tế, phải xét các thao tác đã được gọi; không chỉ nhìn tên lớp rồi giả định mọi thay đổi đều đơn thuần nối theo thời gian chèn.

`reversed()` cung cấp một góc nhìn đảo thứ tự, không phải cách truy xuất “lịch sử thêm theo thời gian ngược”, và không phải lệnh tự sắp xếp.

Chương trình ở phần tiếp theo chỉ dùng API có từ Java 17 để có thể chạy trên Java 17 trở lên. Những phân biệt giữa bốn khái niệm vẫn áp dụng khi dùng Java 21.

## 10. Chương trình hoàn chỉnh

Lưu thành **OrderDimensionsDemo.java**:

~~~java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

public class OrderDimensionsDemo {
    record Job(String id, int priority) {
        @Override
        public String toString() {
            return id + ":" + priority;
        }
    }

    public static void main(String[] args) {
        List<Integer> positions = new ArrayList<>(List.of(30, 10, 20));
        System.out.println("List initial = " + positions);
        System.out.println("Index 1 before insert = " + positions.get(1));
        positions.add(1, 99);
        System.out.println("List after insert = " + positions);
        System.out.println("Index 1 after insert = " + positions.get(1));
        System.out.println("Old value 10 now has index = " + positions.indexOf(10));

        LinkedHashSet<String> insertion = new LinkedHashSet<>();
        insertion.add("B");
        insertion.add("A");
        insertion.add("C");
        System.out.println("Adding existing A = " + insertion.add("A"));
        System.out.println("LinkedHashSet = " + insertion);
        insertion.remove("A");
        insertion.add("A");
        System.out.println("Remove then add A = " + insertion);

        Set<Integer> sortedSet = new TreeSet<>(List.of(30, 10, 20));
        System.out.println("TreeSet = " + sortedSet);

        Set<Integer> hashSet = new HashSet<>(List.of(30, 10, 20));
        List<Integer> sortedCopy = new ArrayList<>(hashSet);
        sortedCopy.sort(Comparator.naturalOrder());
        System.out.println("HashSet sorted copy = " + sortedCopy);

        Deque<String> ends = new ArrayDeque<>();
        ends.addLast("B");
        ends.addLast("C");
        ends.addFirst("A");
        System.out.println("Deque encounter order = " + ends);

        Queue<Integer> priorityQueue =
                new PriorityQueue<>(List.of(40, 10, 30, 20));
        List<Integer> polled = new ArrayList<>();
        Integer value;
        while ((value = priorityQueue.poll()) != null) {
            polled.add(value);
        }
        System.out.println("PriorityQueue poll order = " + polled);

        List<Job> jobs = List.of(
                new Job("C", 2),
                new Job("D", 1),
                new Job("A", 2),
                new Job("B", 1)
        );
        Comparator<Job> byPriority = Comparator.comparingInt(Job::priority);

        List<Job> stable = new ArrayList<>(jobs);
        stable.sort(byPriority);
        System.out.println("Stable sort = " + stable);

        Comparator<Job> byPriorityThenId = byPriority.thenComparing(Job::id);
        List<Job> explicit = new ArrayList<>(jobs);
        explicit.sort(byPriorityThenId);
        System.out.println("Sort with tie-breaker = " + explicit);

        Set<Job> priorityOnly = new TreeSet<>(byPriority);
        priorityOnly.addAll(jobs);
        System.out.println("TreeSet priority only = " + priorityOnly);

        Set<Job> priorityAndId = new TreeSet<>(byPriorityThenId);
        priorityAndId.addAll(jobs);
        System.out.println("TreeSet priority then id = " + priorityAndId);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 OrderDimensionsDemo.java
java OrderDimensionsDemo
~~~

Stdout:

~~~text
List initial = [30, 10, 20]
Index 1 before insert = 10
List after insert = [30, 99, 10, 20]
Index 1 after insert = 99
Old value 10 now has index = 2
Adding existing A = false
LinkedHashSet = [B, A, C]
Remove then add A = [B, C, A]
TreeSet = [10, 20, 30]
HashSet sorted copy = [10, 20, 30]
Deque encounter order = [A, B, C]
PriorityQueue poll order = [10, 20, 30, 40]
Stable sort = [D:1, B:1, C:2, A:2]
Sort with tie-breaker = [B:1, D:1, A:2, C:2]
TreeSet priority only = [D:1, C:2]
TreeSet priority then id = [B:1, D:1, A:2, C:2]
~~~

### Vì sao chương trình cho kết quả này?

- Danh sách ban đầu giữ dãy `30, 10, 20`; chèn `99` vào chỉ số `1` làm giá trị `10` chuyển sang chỉ số `2`.
- `LinkedHashSet.add("A")` không thay đổi vị trí khi `A` đã tồn tại. Xóa rồi thêm lại đưa `A` xuống cuối.
- `TreeSet` duyệt số theo thứ tự tự nhiên, dù thứ tự thêm không tăng dần.
- Dòng `HashSet sorted copy` in **bản sao đã được sort**, không in thứ tự duyệt của `HashSet`. Vì vậy stdout không dựa vào một trình tự mà `HashSet` không cam kết.
- `ArrayDeque` đặt `A` ở đầu dù `A` được thêm sau `B` và `C`.
- Dòng `PriorityQueue poll order` được tạo bằng cách lấy lần lượt qua `poll()`, không bằng iterator.
- `List.sort` giữ cả bốn tác vụ; comparator chỉ ưu tiên cho kết quả ổn định theo thứ tự trước khi sort.
- `TreeSet` với comparator chỉ ưu tiên gộp các tác vụ có cùng mức ưu tiên thành một vị trí. Comparator xét thêm mã phân biệt đủ bốn tác vụ trong ví dụ.

Trong chương trình, `Job` là một record bất biến. `toString()` được viết lại chỉ để kết quả hiển thị gọn dạng `mã:độ_ưu_tiên`; nó không quyết định thứ tự hay tính duy nhất.

## 11. Áp dụng theo nhu cầu thực tế

| Nhu cầu | Cách chọn hoặc xử lý |
| --- | --- |
| Giữ một dãy và cho truy cập phần tử ở vị trí cụ thể | Dùng `List`; xác định rõ những thao tác có thể đổi vị trí. |
| Loại phần tử trùng nhưng giữ thứ tự xuất hiện đầu tiên qua các lần thêm thông thường | Dùng `LinkedHashSet` với `add` thông thường; kiểm tra hành vi khi xóa rồi thêm lại. |
| Chỉ cần kiểm tra thành viên, không có yêu cầu về trình tự | Có thể dùng `HashSet`; kiểm thử theo nội dung, không theo chuỗi hiển thị. |
| Cần duyệt tập hợp duy nhất theo thứ tự so sánh | Cân nhắc `TreeSet`, sau khi xác định comparator và ý nghĩa “trùng”. |
| Cần tất cả phần tử, có thể trùng, được hiển thị theo comparator | Dùng danh sách hoặc bản sao danh sách rồi sort. |
| Cần xử lý tác vụ ưu tiên nhất tiếp theo | Dùng `PriorityQueue` và các thao tác lấy đầu; thêm tiêu chí phụ nếu cần FIFO trong cùng ưu tiên. |
| Cần kiểm thử, xuất dữ liệu hoặc phân trang theo thứ tự tái lập | Quy định một thứ tự rõ ràng và tiêu chí xử lý hòa; không dựa vào thứ tự tình cờ của nguồn. |

### Các thao tác giống nhau về kết quả tạm thời nhưng khác ý nghĩa

| Thao tác | Điều thực sự xảy ra |
| --- | --- |
| `new ArrayList<>(source)` | Tạo dãy từ trình tự nguồn cung cấp lúc sao chép; không tự sort. |
| `list.sort(comparator)` | Thay thứ tự của danh sách có thể sửa theo comparator. |
| `Collections.reverse(list)` | Đảo dãy hiện tại; không tự chọn quy tắc so sánh. |
| `deque.addFirst(value)` | Đặt phần tử ở đầu; không có ý nghĩa sắp xếp theo giá trị. |
| `stream.sorted(comparator)` | Tạo bước sắp xếp trong pipeline; không tự sắp xếp lại collection nguồn. |

Với stream song song, `forEach` không bảo đảm hành động quan sát được diễn ra theo encounter order. Nếu cần hành động tuân theo thứ tự duyệt đã xác định của pipeline, xem xét `forEachOrdered`. Tuy nhiên, `forEachOrdered` không tạo lại thứ tự chèn đã mất từ một nguồn không cam kết thứ tự; muốn một quy tắc sắp xếp cụ thể vẫn cần định nghĩa quy tắc đó.

Nếu đang cần một dãy mới được sắp xếp mà không đổi nguồn, hãy tạo bản sao phù hợp rồi sort, hoặc dùng pipeline tạo kết quả mới. Không gọi sort trực tiếp lên một danh sách không hỗ trợ thay đổi rồi kỳ vọng mọi `List` đều thực hiện được.

## 12. Lỗi thường gặp và cách tự kiểm tra

| Hiểu nhầm | Điều cần sửa |
| --- | --- |
| “Thêm `30, 10, 20` thì tập hợp nào cũng duyệt đúng thứ tự đó.” | Chỉ tin thứ tự chèn khi hợp đồng và thao tác cụ thể bảo đảm. |
| “`HashSet` đã in tăng dần nên có thể dựa vào kết quả đó.” | Một kết quả quan sát được không phải cam kết sắp xếp. |
| “Có thứ tự duyệt thì chắc gọi được `get(2)`.” | API chỉ số thuộc các hợp đồng như `List`, không thuộc mọi tập hợp có thứ tự. |
| “Chỉ số là mã cố định của phần tử.” | Chèn, xóa, thay thế hoặc sort có thể đổi phần tử tại chỉ số. |
| “List đã sort thì thêm gì vào cũng tự đứng đúng chỗ.” | `List` không tự duy trì bất biến sắp xếp. |
| “Duyệt PriorityQueue là lấy các phần tử theo ưu tiên.” | Hợp đồng lấy đầu và hợp đồng iterator khác nhau. |
| “Stable sort luôn cho kết quả giống nhau giữa các lần chạy.” | Nó giữ thứ tự tương đối của đầu vào với các phần tử so sánh bằng nhau; đầu vào có thể không xác định. |
| “TreeSet là List đã sort và loại các đối tượng equals nhau.” | `TreeSet` dùng so sánh để xác định trùng, nên comparator phải phù hợp với dữ liệu và hợp đồng `Set`. |

Hãy tự giải các tình huống sau:

1. Thêm `C`, `A`, `B` vào `LinkedHashSet`; thêm lại `A`; sau đó xóa `C` rồi thêm `C` một lần nữa. Viết thứ tự duyệt sau từng bước.
2. Cho danh sách `[8, 3, 5]`. Chèn `1` vào chỉ số `0`, rồi sort tăng dần. Giá trị ở chỉ số `1` thay đổi như thế nào?
3. Cần in các mã không trùng theo thứ tự xuất hiện đầu tiên trong đầu vào. Vì sao `HashSet` hoặc `TreeSet` có thể không đáp ứng đúng yêu cầu?
4. Cần xử lý các tác vụ theo ưu tiên, cùng ưu tiên thì theo số thứ tự tiếp nhận. Comparator cần xét những thuộc tính nào?
5. Từ một nguồn không cam kết thứ tự, sort danh sách người dùng chỉ theo tuổi. Vì sao những người cùng tuổi có thể đổi vị trí giữa các lần xuất dữ liệu?
6. Cần giữ mọi bản ghi nhưng sắp theo ngày tạo. Điều gì có thể xảy ra nếu dùng `TreeSet` với comparator chỉ xét ngày tạo?

Với mỗi tình huống, hãy nêu riêng: nguồn có cam kết thứ tự chèn không, iterator duyệt theo quy tắc nào, có API chỉ số không, và quy tắc so sánh có làm hai phần tử khác nhau được xem là bằng nhau không.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-007">Các tiêu chí chọn cấu trúc: trùng lặp, null, truy cập và đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Các tiêu chí chọn cấu trúc: trùng lặp, null, truy cập và đồng thời

Chọn collection là chọn **quy tắc dữ liệu và cách chương trình làm việc với dữ liệu**. Hai lớp cùng có phương thức `add` hoặc `get` có thể khác hẳn nhau về việc giữ phần tử trùng, chấp nhận `null`, thứ tự duyệt và tính an toàn khi nhiều luồng cùng truy cập.

Ví dụ, ba yêu cầu sau đều nói đến “danh sách mã đơn hàng” nhưng cần ba cách nhìn khác nhau:

- Nhật ký ghi nhận mỗi lần đơn hàng được xử lý cần giữ cả những lần lặp.
- Tập mã đơn hàng đã xử lý cần nhận biết mã đã có mà không lưu lặp.
- Bảng tra cứu trạng thái cần tìm một đơn hàng bằng mã, nên quan hệ khóa–giá trị mới là trọng tâm.

Bài này thuộc phần tổng quan Java Collections Framework, sử dụng API có trong Java 17. Mục tiêu là biết đặt câu hỏi đúng trước khi chọn `ArrayList`, `HashSet`, `HashMap`, `ArrayDeque` hoặc một cấu trúc đồng thời.

## 1. Bắt đầu bằng hợp đồng dữ liệu

Trước khi xét tốc độ, hãy viết ngắn gọn yêu cầu theo bốn trục:

| Trục quyết định | Câu hỏi cần trả lời | Hệ quả khi chọn sai |
| --- | --- | --- |
| Trùng lặp | Hai phần tử hoặc hai khóa như thế nào được xem là cùng một giá trị? Có giữ mọi lần xuất hiện không? | Mất dữ liệu hoặc giữ dữ liệu đáng lẽ phải loại trùng |
| `null` | `null` có ý nghĩa hợp lệ không? Có cần phân biệt “không có” với “có nhưng chưa biết giá trị” không? | Ngoại lệ khi chạy hoặc diễn giải sai trạng thái |
| Truy cập | Thường lấy theo chỉ số, theo khóa, kiểm tra tồn tại, lấy đầu/cuối hay truy vấn theo thứ tự? | Phải tìm kiếm hoặc dịch chuyển dữ liệu nhiều lần |
| Đồng thời | Ai được đọc, ai được ghi, và những bước nào phải diễn ra nguyên tử? | Mất cập nhật, đọc trạng thái không nhất quán hoặc lỗi duyệt |

Sau bốn câu hỏi đó, bổ sung thứ tự cần giữ, khả năng sửa đổi, kích thước dự kiến và chi phí bộ nhớ. Đây là các điều kiện giúp chọn lớp cụ thể trong cùng một nhóm.

`Collection` không bảo đảm rằng mọi cách triển khai đều chấp nhận cùng loại phần tử hoặc cùng cơ chế đồng bộ. Một biến khai báo là `Collection<String>` cũng không đủ để suy ra có được thêm `null` hay sửa nội dung hay không. Cần đọc hợp đồng của cách triển khai hoặc phương thức tạo ra đối tượng. [Java 17: Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html)

## 2. Trùng lặp: giữ mọi lần xuất hiện hay chỉ giữ một đại diện?

### 2.1. List, Set và Map giải quyết những nhu cầu khác nhau

| Kiểu dữ liệu cần biểu diễn | Lựa chọn thường gặp | Ý nghĩa của phần tử trùng |
| --- | --- | --- |
| Chuỗi phần tử có vị trí | `List<E>` | Có thể giữ nhiều phần tử bằng nhau |
| Tập các giá trị duy nhất | `Set<E>` | Không thêm một phần tử được xem là đã có |
| Quan hệ tra cứu theo khóa | `Map<K,V>` | Khóa duy nhất; nhiều khóa có thể cùng một giá trị |
| Công việc chờ xử lý theo quy tắc lấy ra | `Queue<E>` hoặc `Deque<E>` | Tùy cách triển khai, thường có thể giữ phần tử trùng |

Ví dụ minh họa trong một phương thức:

```java
List<String> events = new ArrayList<>(List.of("B", "A", "B"));
Set<String> unique = new LinkedHashSet<>(events);

System.out.println(events); // [B, A, B]
System.out.println(unique); // [B, A]
```

`LinkedHashSet` ở đây vừa loại trùng vừa giữ thứ tự chèn. Nếu chỉ cần kiểm tra tồn tại mà không yêu cầu thứ tự duyệt, `HashSet` là một lựa chọn khác. Không dựa vào thứ tự tình cờ quan sát được khi in `HashSet`. [Java 17: LinkedHashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)

`Map` không kế thừa `Collection`: nó biểu diễn ánh xạ. Khi gọi `put` với một khóa đã có, ánh xạ hiện tại của khóa được thay giá trị, chứ không tạo thêm một khóa giống hệt.

```java
Map<String, Integer> quantities = new HashMap<>();
quantities.put("BOOK", 2);
Integer previous = quantities.put("BOOK", 5);

System.out.println(previous);             // 2
System.out.println(quantities.get("BOOK")); // 5
System.out.println(quantities.size());    // 1
```

Nếu yêu cầu là “mỗi khách hàng có nhiều đơn hàng”, có thể dùng `Map<CustomerId, List<Order>>`; nếu chỉ dùng `Map<CustomerId, Order>`, các lần ghi cùng khóa sẽ thay thế nhau. Cấu trúc phải diễn đạt đúng quan hệ một–nhiều. [Java 17: Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)

### 2.2. Phải định nghĩa “bằng nhau” trước khi loại trùng

Đối với `HashSet` và khóa của `HashMap`, thiết kế `equals` và `hashCode` phải nhất quán:

- Nếu hai đối tượng bằng nhau theo `equals`, chúng phải có cùng `hashCode`.
- Hai đối tượng có cùng mã băm chưa chắc bằng nhau; va chạm mã băm không đồng nghĩa dữ liệu trùng.
- Không thay đổi các trường tham gia tính `equals`/`hashCode` khi đối tượng đang được dùng làm khóa hoặc phần tử trong tập băm.

Chẳng hạn, một đối tượng khách hàng được tìm theo mã khách hàng nhưng sau khi thêm vào `HashSet` lại bị đổi mã có thể không còn được tìm ở vị trí mà cấu trúc đã lưu. Dùng khóa bất biến hoặc tạo một khóa giá trị riêng thường dễ kiểm soát hơn.

`String` và những kiểu giá trị bất biến thích hợp thường giúp giảm rủi ro này. Với lớp tự viết, cần quyết định rõ “cùng mã” hay “mọi trường đều giống nhau” mới là bằng nhau. Một `record` tự sinh phép so sánh theo các thành phần của nó; điều đó chỉ đúng nhu cầu khi chính các thành phần ấy tạo nên danh tính giá trị. [Java 17: HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)

### 2.3. Cây có thứ tự nhận biết trùng bằng phép so sánh

`TreeSet` và khóa của `TreeMap` dùng thứ tự tự nhiên hoặc `Comparator`. Hai phần tử/khóa có kết quả so sánh bằng `0` được xử lý như cùng một vị trí trong cấu trúc.

Giả sử hai sản phẩm có mã khác nhau nhưng cùng tên “Pen”. Nếu comparator chỉ xét tên, `TreeSet` chỉ giữ một phần tử dù `equals` của hai sản phẩm là `false`.

```java
Comparator<Product> byName =
        Comparator.comparing(Product::name);

// Nếu muốn phân biệt sản phẩm cùng tên:
Comparator<Product> byNameThenId =
        Comparator.comparing(Product::name)
                  .thenComparing(Product::id);
```

Đây là đoạn minh họa; kiểu `Product` được định nghĩa trong chương trình hoàn chỉnh ở cuối bài. Chỉ dùng tên để loại trùng nếu “một tên là một giá trị duy nhất” thực sự là yêu cầu. Nếu muốn collection tuân thủ đầy đủ hợp đồng `Set`/`Map` dựa trên `equals`, thứ tự so sánh cần nhất quán với `equals`. Cũng không sửa trường dùng để sắp thứ tự khi đối tượng đang nằm trong cây. [Java 17: TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html), [TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html)

Một điểm khác: `Set.of("A", "A")` báo `IllegalArgumentException`; phương thức này không âm thầm giữ một bản như cách thêm lặp vào `HashSet`. Không suy diễn hành vi của phương thức tạo từ hành vi của `add`. [Java 17: Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

## 3. Null: lựa chọn mô hình dữ liệu, không phải chi tiết phụ

### 3.1. Chính sách null phụ thuộc lớp cụ thể

Bảng sau mô tả các lựa chọn phổ biến trong Java 17, không phải quy tắc cho mọi lớp cùng triển khai một interface.

| Cấu trúc hoặc cách tạo | Phần tử / khóa `null` | Giá trị `null` của Map |
| --- | --- | --- |
| `ArrayList`, `LinkedList` | Cho phép phần tử `null` | Không áp dụng |
| `HashSet`, `LinkedHashSet` | Cho phép một phần tử `null` | Không áp dụng |
| `HashMap`, `LinkedHashMap` | Cho phép một khóa `null` | Cho phép |
| `TreeSet` | Thứ tự tự nhiên không cho; comparator có thể hỗ trợ | Không áp dụng |
| `TreeMap` | Thứ tự tự nhiên không cho khóa `null`; comparator có thể hỗ trợ | Cho phép |
| `ArrayDeque`, `PriorityQueue` | Không cho phần tử `null` | Không áp dụng |
| `ConcurrentHashMap` | Không cho khóa `null` | Không cho |
| `CopyOnWriteArrayList` | Cho phép phần tử `null` | Không áp dụng |
| `List.of`, `Set.of`, `Map.of` | Không cho phần tử/khóa `null` | `Map.of` không cho |

Các hợp đồng tương ứng được mô tả tại [ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html), [LinkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html), [HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html), [ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html) và [PriorityQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html).

Không phải cứ dùng cây là tuyệt đối không được có `null`. Ví dụ minh họa sau đặt quy tắc so sánh `null` rõ ràng:

```java
Set<String> names =
        new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));
names.add("Lan");
names.add(null);
System.out.println(names); // [null, Lan]
```

Tuy vậy, “có thể hỗ trợ” không đồng nghĩa “nên cho phép”. Nếu `null` không có ý nghĩa nghiệp vụ, hãy chặn từ đầu. Việc đổi lớp sau này cũng dễ hơn khi mô hình dữ liệu không phụ thuộc vào một ngoại lệ riêng của lớp hiện tại.

### 3.2. Get trả null chưa đủ để biết khóa có tồn tại

Trong một `HashMap` cho phép giá trị `null`, hai trạng thái khác nhau có thể cho cùng kết quả:

```java
Map<String, String> notes = new HashMap<>();
notes.put("A", null);

System.out.println(notes.get("A"));         // null: khóa có, giá trị null
System.out.println(notes.get("B"));         // null: không có khóa
System.out.println(notes.containsKey("A")); // true
System.out.println(notes.containsKey("B")); // false
```

Nếu cần phân biệt, dùng `containsKey` hoặc thiết kế một kiểu giá trị diễn đạt trạng thái rõ hơn. `getOrDefault("A", "unknown")` vẫn trả `null` vì khóa `A` thực sự có ánh xạ; giá trị mặc định chỉ dùng khi không có ánh xạ.

Với dữ liệu được sửa đồng thời, hai lệnh `containsKey` rồi `get` cũng không tự tạo một ảnh chụp nguyên tử: dữ liệu có thể đổi giữa hai lệnh. Cần xem xét điều kiện đồng thời riêng, không chỉ giải quyết sự nhập nhằng của `null`.

### 3.3. Queue thường dùng null để báo không có phần tử

`poll()` có thể trả `null` khi hàng đợi rỗng. Nếu cho phép đưa `null` vào dữ liệu, kết quả đó khó phân biệt với một phần tử thật.

`ArrayDeque` không nhận `null`, nên kết quả `poll()` dễ diễn giải hơn. `LinkedList` cho phép `null`, kể cả khi được sử dụng qua `Queue`; khả năng này là một lý do cần tự đặt chính sách dữ liệu chặt hơn.

Đối với `BlockingQueue`, `null` không được chấp nhận. Nếu cần báo kết thúc công việc, dùng một thông điệp kết thúc có kiểu hoặc quy ước riêng; không dùng `null` làm phần tử đánh dấu. [Java 17: BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)

### 3.4. Chuyển sang bản không sửa được có thể thay đổi điều kiện đầu vào

`List.of` và `List.copyOf` tạo danh sách không sửa được và từ chối `null`. Không thể mặc định rằng một `ArrayList` đang có `null` sẽ luôn chuyển được bằng `List.copyOf`.

Ngược lại, `Collections.unmodifiableList` tạo một lớp nhìn không cho sửa qua chính nó; nếu danh sách gốc thay đổi thì lớp nhìn phản ánh thay đổi. Nó cũng không làm các đối tượng phần tử trở nên bất biến.

Hãy phân biệt ba yêu cầu: không cho sửa qua một tham chiếu, không thay đổi cấu trúc dữ liệu, và không thay đổi trạng thái phần tử. [Java 17: List](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html), [Collection: view và khả năng sửa đổi](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html)

## 4. Truy cập: tối ưu thao tác thật sự diễn ra

### 4.1. Chọn theo đường truy cập chính

| Nhu cầu chính | Lựa chọn khởi đầu | Điều cần kiểm tra thêm |
| --- | --- | --- |
| Lấy theo chỉ số, nối cuối và duyệt tuần tự | `ArrayList` | Có chèn/xóa nhiều ở giữa không? |
| Kiểm tra thành viên, không cần thứ tự | `HashSet` | `equals`/`hashCode` đúng chưa? |
| Loại trùng nhưng giữ thứ tự chèn | `LinkedHashSet` | Có cần sắp theo giá trị thay vì thứ tự chèn không? |
| Tra cứu theo khóa | `HashMap` | Có cần thứ tự hoặc truy vấn khoảng khóa không? |
| Duyệt khóa theo thứ tự, tìm lân cận/khoảng | `TreeMap` | Comparator có phản ánh đúng ý nghĩa khóa không? |
| Tập có thứ tự và tìm lân cận | `TreeSet` | Tiêu chí duy nhất có nhất quán với so sánh không? |
| Thêm/lấy hai đầu; ngăn xếp hoặc hàng đợi thường | `ArrayDeque` | Có nhiều luồng hoặc cần chờ dữ liệu không? |
| Lấy phần tử ưu tiên cao nhất theo quy tắc | `PriorityQueue` | Có thật sự cần duyệt toàn bộ theo thứ tự không? |

Một interface thể hiện mục đích sẽ giúp mã gọi dễ đọc:

```java
List<String> labels = new ArrayList<>();
Set<String> processedIds = new HashSet<>();
Map<String, Integer> countsById = new HashMap<>();
Deque<String> pendingJobs = new ArrayDeque<>();
```

Đây là đoạn khai báo minh họa; cần import các kiểu tương ứng. Khai báo interface không làm mọi lớp thay thế được cho nhau về `null`, thứ tự, hiệu năng hay đồng thời. Khi cần thao tác `floorKey` hoặc `ceilingKey`, `NavigableMap` mới diễn tả đủ khả năng cần dùng.

### 4.2. Hiểu độ phức tạp cùng giả định

Với `n` là số phần tử, các đặc điểm thường dùng để so sánh là:

| Cấu trúc | Thao tác | Đặc điểm chi phí |
| --- | --- | --- |
| `ArrayList` | `get(index)`, `set(index, value)` | Thời gian hằng số |
| `ArrayList` | Thêm cuối | Hằng số khấu hao; một lần mở rộng có thể phải sao chép |
| `ArrayList` | Chèn/xóa ở giữa, tìm theo giá trị | Tuyến tính trong trường hợp tổng quát |
| `LinkedList` | `get(index)` | Phải đi qua các nút, tuyến tính trong trường hợp tổng quát |
| `LinkedList` | Thêm/xóa đầu hoặc cuối | Thời gian hằng số |
| `HashMap`, `HashSet` | Tra cứu, thêm cơ bản | Thường hằng số khi mã băm phân bố tốt |
| `TreeMap`, `TreeSet` | Tra cứu, thêm, xóa cơ bản | Theo logarit của số phần tử |
| `ArrayDeque` | Thêm/lấy hai đầu | Phần lớn thao tác có chi phí hằng số khấu hao |
| `PriorityQueue` | Thêm hoặc lấy đầu ưu tiên | Theo logarit; xem đầu là hằng số |

Các bảo đảm và giới hạn cụ thể nằm trong tài liệu [ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html), [LinkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html), [HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html), [TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html) và [PriorityQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html).

“Hằng số khấu hao” mô tả chi phí trung bình theo một chuỗi thao tác, không cam kết từng lần gọi đều tốn thời gian như nhau. Đồng thời, ký hiệu độ phức tạp không tính hết bộ nhớ, chi phí cấp phát, độ cục bộ dữ liệu và nội dung của phép so sánh.

Đừng chọn `LinkedList` chỉ vì nghĩ “chèn giữa nhanh”: trước hết phải tìm đến vị trí. Thêm ngay tại vị trí mà `ListIterator` đã đứng khác với gọi thêm theo chỉ số khi còn phải duyệt đến đó.

Một vòng lặp `for (int i = 0; i < list.size(); i++) list.get(i)` phù hợp khác nhau tùy lớp. Với danh sách liên kết, các lần truy cập theo chỉ số có thể khiến toàn vòng lặp thành bậc hai. Nếu chỉ cần duyệt, dùng vòng `for-each` hoặc iterator.

### 4.3. Thứ tự chèn, thứ tự sắp và thứ tự ưu tiên khác nhau

- `ArrayList` giữ thứ tự vị trí của danh sách.
- `LinkedHashSet` giữ thứ tự chèn.
- `TreeSet` sắp theo thứ tự tự nhiên hoặc comparator.
- `PriorityQueue` bảo đảm phần tử ở đầu theo thứ tự ưu tiên; iterator không bảo đảm duyệt toàn bộ theo thứ tự sắp.

Vì vậy, in `PriorityQueue` không phải bằng chứng rằng cả hàng đợi đã được sắp thành danh sách. Khi cần lấy theo mức ưu tiên, gọi `poll` liên tiếp; việc này đồng thời làm mất phần tử khỏi hàng đợi. Nếu chỉ muốn xuất báo cáo có thứ tự và giữ dữ liệu gốc, cần tạo bản sao hoặc sắp một danh sách riêng.

## 5. Đồng thời: an toàn từng phương thức chưa đủ cho quy trình

### 5.1. Xác định có thật sự chia sẻ dữ liệu không

Một collection chỉ dùng trong một luồng thường không cần cấu trúc đồng thời. Đây là lựa chọn đơn giản khi có thể giữ quyền sở hữu dữ liệu trong một tác vụ.

Nếu nhiều luồng chỉ đọc sau khi dữ liệu đã được tạo hoàn tất, không còn sửa và được công bố an toàn qua cơ chế đồng bộ phù hợp, một cấu trúc thông thường có thể được chia sẻ. Nhưng `final` trên biến tham chiếu không cấm sửa collection; “không thấy lệnh ghi ở đây” cũng không chứng minh không có bên khác giữ tham chiếu và sửa.

Khi có ghi đồng thời, lựa chọn cơ chế phải dựa trên **bất biến cần giữ**. Ví dụ “mỗi lần tăng phải được tính” khác với “có thể đọc một tổng quan gần thời điểm hiện tại”.

### 5.2. Ba hướng bảo vệ phổ biến

| Hướng | Phù hợp khi | Điều phải tự kiểm soát |
| --- | --- | --- |
| Giữ dữ liệu riêng theo luồng hoặc chia sẻ bản không đổi | Có thể tránh chia sẻ dữ liệu đang sửa | Công bố an toàn, tham chiếu đến phần tử có thể sửa |
| Dùng cùng một khóa bên ngoài hoặc synchronized wrapper | Muốn bảo vệ cả chuỗi thao tác bằng một khóa | Mọi bên truy cập cùng giao thức khóa, kể cả lúc duyệt |
| Dùng lớp trong `java.util.concurrent` | Nhiều luồng cần truy cập trực tiếp với hợp đồng phù hợp | Nguyên tử của từng API, cách duyệt và bất biến nhiều bước |

`Collections.synchronizedList` tạo wrapper được đồng bộ hóa. Khi dùng iterator, stream hoặc spliterator, cần tự đồng bộ trên wrapper trong suốt quá trình duyệt. Không giữ một tham chiếu tới danh sách gốc rồi bỏ qua wrapper.

```java
List<String> shared =
        Collections.synchronizedList(new ArrayList<>());

synchronized (shared) {
    if (!shared.contains("A")) {
        shared.add("A");
    }
}

synchronized (shared) {
    for (String value : shared) {
        System.out.println(value);
    }
}
```

Đây là đoạn minh họa trong một phương thức. Cùng một khóa bao trùm cả kiểm tra và thêm. Nếu nhiều nơi tự viết `contains` rồi `add` ngoài khóa chung, hai luồng vẫn có thể cùng quan sát “chưa có” và thêm lặp.

Với `synchronizedMap`, khi duyệt `keySet` hoặc `entrySet`, đồng bộ trên wrapper của map, không trên đối tượng view. [Java 17: Collections, synchronized wrappers](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html)

### 5.3. Chọn lớp đồng thời theo đúng cách sử dụng

| Nhu cầu | Lớp hoặc nhóm thường phù hợp | Đánh đổi chính |
| --- | --- | --- |
| Tra cứu/cập nhật theo khóa giữa nhiều luồng | `ConcurrentHashMap` | Không nhận `null`; không tạo giao dịch cho nhiều khóa |
| Map đồng thời có thứ tự khóa | `ConcurrentSkipListMap` | Chi phí và hợp đồng khác map băm |
| Danh sách đọc/duyệt rất nhiều, sửa ít | `CopyOnWriteArrayList` | Sao chép mảng khi sửa; iterator giữ ảnh chụp |
| Tập đồng thời không cần thứ tự | `ConcurrentHashMap.newKeySet()` | Theo đặc tính của tập dựa trên concurrent map |
| Trao đổi công việc giữa bên sản xuất và tiêu thụ | `BlockingQueue`, như `ArrayBlockingQueue` | Phải chọn dung lượng và cách xử lý đầy/rỗng |
| Hàng đợi đồng thời không cần chặn chờ | `ConcurrentLinkedQueue` | Không tự giới hạn tải như một hàng đợi có sức chứa cố định |

`ConcurrentSkipListMap` phù hợp khi cần cả đồng thời và truy cập theo thứ tự; nó không nhận khóa hoặc giá trị `null`. [Java 17: ConcurrentSkipListMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListMap.html)

Hàng đợi có dung lượng hữu hạn giúp giới hạn số công việc đang chờ. Chọn `put` để chờ, `offer` để thử hoặc `offer` có thời hạn tùy luồng xử lý. Một queue đồng thời không có giới hạn dung lượng không tự bảo vệ ứng dụng trước tốc độ sản xuất công việc lớn hơn tốc độ tiêu thụ.

### 5.4. Thao tác ghép cần API nguyên tử phù hợp

Đoạn sau **sai khi có nhiều luồng tăng cùng khóa**, kể cả `counts` là `ConcurrentHashMap`:

```java
Integer oldValue = counts.get("requests");
counts.put("requests", oldValue == null ? 1 : oldValue + 1);
```

Hai luồng có thể cùng đọc `10` rồi cùng ghi `11`, làm mất một lần tăng. Dùng một thao tác cập nhật nguyên tử theo khóa:

```java
counts.merge("requests", 1, Integer::sum);
```

Tương tự, cân nhắc `putIfAbsent`, `compute`, `computeIfAbsent` hoặc các phương thức thay thế có điều kiện thay vì tự ghép đọc–kiểm tra–ghi.

Hàm tính toán nên ngắn, tránh tác vụ chặn lâu và không sửa lại chính map trong quá trình tính. Nguyên tử theo một khóa không bảo vệ bất biến nhiều khóa: chuyển tiền giữa hai số dư cần thiết kế đồng bộ hoặc giao dịch riêng.

Một `ConcurrentHashMap<String, ArrayList<String>>` chỉ bảo vệ cấu trúc map. Việc hai luồng cùng gọi `add` trên cùng `ArrayList` lấy từ map vẫn không được map tự bảo vệ. Cần chọn giá trị bất biến, cấu trúc giá trị đồng thời hoặc cơ chế khóa phù hợp. [Java 17: ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

### 5.5. Duyệt collection có nhiều hợp đồng khác nhau

- Iterator fail-fast của nhiều collection thông thường có thể báo `ConcurrentModificationException` khi phát hiện sửa cấu trúc không hợp lệ. Đây là cơ chế phát hiện lỗi theo khả năng, không phải bảo đảm đồng bộ và không phải cách làm chương trình an toàn.
- Iterator của `ConcurrentHashMap` là weakly consistent: có thể phản ánh một phần thay đổi diễn ra trong lúc duyệt, không cam kết ảnh chụp nguyên tử của toàn map.
- Iterator của `CopyOnWriteArrayList` dùng mảng tại lúc iterator được tạo. Những thay đổi sau đó không xuất hiện trong iterator ấy. Chi phí là việc sửa phải sao chép, và iterator có thể giữ mảng cũ còn sống.

Không gọi chung mọi cách duyệt không ném `ConcurrentModificationException` là “fail-safe” rồi suy ra cùng một ý nghĩa.

Ảnh chụp của `CopyOnWriteArrayList` cũng chỉ là ảnh chụp các tham chiếu phần tử. Nếu một đối tượng phần tử bị sửa trạng thái, danh sách không tự tạo bản sao sâu hoặc bảo vệ trạng thái đó. [Java 17: CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)

## 6. Chương trình hoàn chỉnh: quan sát các tiêu chí

Lưu toàn bộ chương trình sau vào `CollectionCriteriaDemo.java`. Chương trình sử dụng `record`, collection thông thường và các lớp đồng thời có trong Java 17.

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CollectionCriteriaDemo {
    record Product(String id, String name) {}

    public static void main(String[] args) throws InterruptedException {
        List<String> events = new ArrayList<>(List.of("B", "A", "B"));
        System.out.println("list = " + events);
        System.out.println("unique by insertion = " + new LinkedHashSet<>(events));
        System.out.println("unique sorted = " + new TreeSet<>(events));

        Product p1 = new Product("P1", "Pen");
        Product p2 = new Product("P2", "Pen");
        Set<Product> byEquals = new HashSet<>(List.of(p1, p2));
        Set<Product> byName = new TreeSet<>(Comparator.comparing(Product::name));
        byName.add(p1);
        byName.add(p2);
        System.out.println("hash size = " + byEquals.size());
        System.out.println("tree by name size = " + byName.size());

        Map<String, String> notes = new HashMap<>();
        notes.put("A", null);
        System.out.println("get(A) = " + notes.get("A"));
        System.out.println("get(B) = " + notes.get("B"));
        System.out.println("contains A/B = "
                + notes.containsKey("A") + "/" + notes.containsKey("B"));

        ArrayDeque<String> queue = new ArrayDeque<>();
        System.out.println("poll empty = " + queue.poll());
        try {
            queue.add(null);
        } catch (NullPointerException expected) {
            System.out.println("ArrayDeque rejects null");
        }

        ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread worker = new Thread(() -> {
                for (int j = 0; j < 1_000; j++) {
                    counts.merge("requests", 1, Integer::sum);
                }
            });
            workers.add(worker);
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        System.out.println("requests = " + counts.get("requests"));

        CopyOnWriteArrayList<String> listeners =
                new CopyOnWriteArrayList<>(List.of("A", "B"));
        Iterator<String> beforeChange = listeners.iterator();
        listeners.add("C");
        List<String> snapshot = new ArrayList<>();
        beforeChange.forEachRemaining(snapshot::add);
        System.out.println("iterator snapshot = " + snapshot);
        System.out.println("current list = " + listeners);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 CollectionCriteriaDemo.java
java CollectionCriteriaDemo
```

Kết quả chuẩn:

```text
list = [B, A, B]
unique by insertion = [B, A]
unique sorted = [A, B]
hash size = 2
tree by name size = 1
get(A) = null
get(B) = null
contains A/B = true/false
poll empty = null
ArrayDeque rejects null
requests = 4000
iterator snapshot = [A, B]
current list = [A, B, C]
```

Cách đọc kết quả:

1. `List` giữ ba lần xuất hiện; `LinkedHashSet` chỉ giữ hai giá trị theo thứ tự chèn, còn `TreeSet` sắp theo chuỗi.
2. Hai `Product` khác mã nên khác nhau theo `equals` do record sinh ra. Comparator chỉ xét tên lại cho kết quả bằng `0`, khiến tập cây giữ một sản phẩm.
3. `get` của map trả `null` cho cả khóa có ánh xạ `null` lẫn khóa vắng; `containsKey` phân biệt hai trạng thái.
4. `poll` hàng đợi rỗng trả `null`, nhưng không thể đưa `null` vào `ArrayDeque`.
5. Bốn luồng cùng tăng một khóa bằng `merge`. Mỗi luồng tăng 1.000 lần; `join` chờ tất cả hoàn tất trước khi đọc nên tổng là 4.000.
6. Iterator của danh sách copy-on-write được tạo trước khi thêm `C`, nên vẫn chỉ duyệt `A` và `B`.

Ví dụ không in trực tiếp thứ tự của `HashSet` hoặc `ConcurrentHashMap`, nên output không phụ thuộc thứ tự duyệt mà các lớp đó không cam kết. Không dùng ví dụ “thường chạy đúng” của cập nhật có tranh chấp để chứng minh an toàn đồng thời.

## 7. Quy trình áp dụng vào bài toán thực tế

Giả sử cần tiếp nhận mã công việc, tránh xử lý trùng và chuyển cho các luồng xử lý:

1. **Xác định ý nghĩa trùng:** cùng mã công việc là một việc hay một lần thử mới? Nếu một công việc được chạy lại hợp lệ, có thể cần định danh lần thử.
2. **Đặt chính sách null:** từ chối mã `null` ngay lúc nhận, vì nó không đại diện cho công việc hợp lệ.
3. **Chọn đường truy cập:** cần kiểm tra mã đã có và lấy công việc theo thứ tự, nên một collection đơn lẻ chưa chắc thể hiện đủ cả hai.
4. **Xác định nguyên tử:** “đánh dấu đã nhận” và “đưa vào hàng đợi” là hai thay đổi. Một concurrent set cộng với concurrent queue không tự tạo giao dịch giữa chúng.
5. **Xem giới hạn hệ thống:** nhiều tiến trình hoặc nhiều máy cần cơ chế phối hợp bền vững; collection trong một JVM không bảo đảm loại trùng toàn hệ thống.
6. **Chọn chiến lược lỗi:** nếu tiến trình dừng sau đánh dấu nhưng trước xếp hàng, phải có cơ chế khôi phục thay vì làm mất công việc.

Ví dụ này cho thấy tiêu chí đồng thời phải xét ở cấp quy trình. Chọn đúng lớp là nền tảng; bảo toàn bất biến nghiệp vụ còn cần thiết kế phối hợp giữa các bước.

Trước khi tối ưu, có thể viết một bảng yêu cầu nhỏ cho từng dữ liệu: có trùng không, có `null` không, thứ tự gì, thao tác nào thường xuyên, ai được sửa và điều gì phải nguyên tử. Sau đó mới đo trên kích thước và kiểu truy cập đại diện cho thực tế.

## 8. Lỗi thường gặp cần tự kiểm tra

| Lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Chọn `Set` chỉ để “đỡ trùng” nhưng chưa định nghĩa bằng nhau | Có thể giữ hoặc loại sai dữ liệu | Thiết kế `equals`/`hashCode` hoặc comparator theo yêu cầu |
| Coi khóa trùng trong Map là một mục mới | `put` thay ánh xạ hiện có | Chọn giá trị dạng danh sách/tập nếu quan hệ là một–nhiều |
| Coi mọi `List` đều nhận `null` và sửa được | Interface không quy định mọi đối tượng giống nhau | Kiểm tra lớp hoặc phương thức tạo |
| Dùng `get(key) == null` để kết luận khóa không tồn tại | Có thể là ánh xạ đến `null` | Dùng hợp đồng dữ liệu rõ hoặc kiểm tra sự tồn tại phù hợp |
| Chọn `LinkedList` vì “chèn giữa O(1)” | Có thể vẫn phải tìm vị trí O(n) | Tính cả chi phí xác định vị trí |
| Trông chờ thứ tự `HashMap` hoặc thứ tự iterator của `PriorityQueue` | Không có bảo đảm thứ tự cần dùng | Chọn cấu trúc có hợp đồng thứ tự hoặc tạo bản sắp riêng |
| Coi `ConcurrentHashMap` làm mọi chuỗi thao tác nguyên tử | Đọc rồi ghi vẫn có khoảng xen kẽ | Dùng API nguyên tử hoặc cơ chế phối hợp thích hợp |
| Coi collection an toàn thì đối tượng phần tử cũng an toàn | Collection thường chỉ quản lý tham chiếu | Thiết kế trạng thái phần tử và cơ chế sửa riêng |
| Bắt `ConcurrentModificationException` để thử lại như cơ chế đồng bộ | Fail-fast chỉ hỗ trợ phát hiện lỗi | Sửa quyền sở hữu dữ liệu hoặc giao thức đồng bộ |
| Dùng copy-on-write khi ghi liên tục trên danh sách lớn | Sao chép gây chi phí thời gian và bộ nhớ | Chỉ chọn khi mô hình đọc nhiều, sửa ít phù hợp |

## 9. Tự luyện

1. Cần loại mã khách hàng trùng nhưng giữ thứ tự người dùng nhập. Vì sao `LinkedHashSet` phù hợp hơn `TreeSet`?  
   **Gợi ý:** yêu cầu giữ thứ tự chèn, không phải sắp theo giá trị.

2. Hai sản phẩm khác mã nhưng cùng giá bán được thêm vào `TreeSet` có comparator chỉ xét giá. Tập giữ bao nhiêu phần tử?  
   **Gợi ý:** phép so sánh bằng `0` làm chúng tương đương đối với tập cây. Muốn giữ cả hai, phải thêm tiêu chí phân biệt phù hợp.

3. Một `HashMap` chứa khóa `A` với giá trị `null`. `getOrDefault("A", "unknown")` trả gì?  
   **Đáp án:** `null`, vì có ánh xạ cho khóa `A`.

4. Thay `merge` trong ví dụ bằng `get` rồi `put`. Có được kỳ vọng luôn thấy 4.000 không?  
   **Đáp án:** không. Hai luồng có thể cùng tính từ một giá trị cũ và làm mất cập nhật.

5. Cần chuyển công việc giữa nhiều luồng, giới hạn tối đa 100 công việc chờ và cho bên nhận chờ khi rỗng. Bắt đầu đánh giá cấu trúc nào?  
   **Gợi ý:** một `BlockingQueue` có dung lượng hữu hạn, như `ArrayBlockingQueue`; sau đó quyết định hành vi khi đầy, ngắt chờ và dừng hệ thống.

Khi trả lời, luôn giải thích cả quy tắc dữ liệu lẫn cách truy cập. Tên lớp chỉ có ý nghĩa khi gắn với các điều kiện đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-008">Kiến thức nền và trình tự học các nhóm collection</a>

<details>
<summary>Xem chi tiết</summary>

# Kiến thức nền và trình tự học các nhóm collection

Học Java Collections Framework dễ bị biến thành việc ghi nhớ tên lớp: `ArrayList`, `HashSet`, `HashMap`, rồi chọn một lớp theo thói quen. Cách học đó có thể giúp viết được vài câu lệnh nhưng chưa giúp trả lời câu hỏi quan trọng: **dữ liệu này cần được tổ chức theo quy tắc nào, và lựa chọn đó có đúng khi yêu cầu thay đổi không?**

Bài này xây dựng nền tảng và trình tự học cho các nhóm collection trong Java Core. Mục tiêu là biết mình cần học gì trước, vì sao nên học theo thứ tự đó, khi nào có thể chuyển sang nhóm tiếp theo và cách nối các nhóm thành một lời giải hoàn chỉnh.

Các ví dụ dùng API có trong Java 17. Chương trình đầy đủ ở phần minh họa có thể biên dịch bằng JDK 17 trở lên. Những đoạn ngắn còn lại chỉ minh họa một ý; chúng không phải các chương trình độc lập.

## 1. Bắt đầu từ bài toán, không bắt đầu từ tên lớp

Giả sử một chương trình nhận các lượt đăng ký có mã:

```text
S2, S1, S2
```

Cùng một dữ liệu đầu vào có thể sinh ra nhiều yêu cầu:

| Yêu cầu | Ý nghĩa cần giữ | Nhóm nên tìm hiểu |
| --- | --- | --- |
| Giữ đầy đủ từng lượt đăng ký theo vị trí | Có thứ tự, cho phép lặp | `List` |
| Lấy các mã đã xuất hiện | Không lặp | `Set` |
| Đếm số lần đăng ký theo mã | Một khóa gắn với một giá trị đếm | `Map` |
| Xử lý lần lượt theo thứ tự chờ | Chính sách lấy phần tử ở đầu | `Queue` hoặc `Deque` dùng như hàng đợi |
| Xử lý công việc ưu tiên trước | Chính sách chọn phần tử theo độ ưu tiên | `PriorityQueue` |
| Lấy mã theo thứ tự tăng dần hoặc tìm mã gần nhất | Có quan hệ sắp xếp và điều hướng | `SortedSet`, `NavigableSet`, `SortedMap`, `NavigableMap` |

Không có một collection nào tự diễn đạt tốt mọi yêu cầu trên. Đổi từ `List` sang `Set` có thể làm mất thông tin về các lượt lặp; đổi sang một lớp không bảo đảm thứ tự có thể làm mất thứ tự cần cho nghiệp vụ.

Trước khi viết `new`, hãy trả lời:

1. Đơn vị được lưu là một phần tử hay một cặp khóa–giá trị?
2. Phần tử lặp có mang ý nghĩa không?
3. Cần vị trí, thứ tự thêm, thứ tự sắp xếp hay thứ tự ưu tiên?
4. Thao tác thường xuyên là tra cứu, thêm cuối, lấy đầu hay tìm theo khoảng?
5. Ai được sửa dữ liệu? Có nhiều luồng cùng truy cập không?

Năm câu hỏi này là sợi dây nối toàn bộ lộ trình.

## 2. Kiến thức nền cần chuẩn bị

Không cần thành thạo toàn bộ Java trước khi dùng `ArrayList`. Tuy nhiên, một số kiến thức phải được học trước khi giải thích sâu cơ chế của từng nhóm.

### 2.1. Biến, vòng lặp, phương thức và mảng

Cần biết khai báo biến, truyền tham số, nhận giá trị trả về, dùng `if`, `for`, `while` và đọc một phương thức đơn giản.

Mảng là điểm so sánh tốt:

- Mảng có độ dài cố định sau khi tạo.
- Mảng có chỉ số bắt đầu từ 0.
- Mảng lưu được kiểu nguyên thủy hoặc kiểu tham chiếu.
- Khi muốn thêm nhiều phần tử hơn sức chứa, phải tự tạo mảng khác và chuyển dữ liệu nếu làm thủ công.

Collection giải quyết nhiều nhu cầu tổ chức dữ liệu mà người học không nên phải tự xây lại mỗi lần. Tuy vậy, collection không phải chỉ là “mảng có thể tăng kích thước”: tập hợp không lặp, bảng ánh xạ và hàng đợi có những quy tắc riêng.

**Dấu hiệu đã đủ nền:** tự viết được vòng lặp tính tổng một mảng và giải thích vì sao truy cập chỉ số bằng độ dài mảng là sai.

### 2.2. Đối tượng, tham chiếu và sự thay đổi

Với collection chứa đối tượng, cần hiểu rằng collection thông thường giữ các tham chiếu đến đối tượng. Thêm một đối tượng vào danh sách không tự tạo bản sao sâu của đối tượng đó.

Hai biến có thể cùng trỏ đến một danh sách; hai danh sách khác nhau có thể cùng trỏ đến những phần tử giống nhau về danh tính đối tượng.

Đoạn minh họa:

```java
List<StringBuilder> first = new ArrayList<>();
first.add(new StringBuilder("A"));

List<StringBuilder> second = new ArrayList<>(first);
second.get(0).append("B");
```

Sau các câu lệnh này:

- `first` và `second` là hai đối tượng danh sách khác nhau.
- Phần tử ở vị trí 0 của chúng trỏ đến cùng một `StringBuilder`.
- Nội dung đối tượng đó là `"AB"`.

Đây là lý do cần phân biệt **sao chép cấu trúc chứa tham chiếu** với **sao chép toàn bộ các đối tượng bên trong**. Kiến thức này sẽ được dùng khi học constructor sao chép, view, defensive copy và collection không cho sửa.

### 2.3. Interface, lớp triển khai và đa hình

Người học cần đọc được khai báo:

```java
List<String> names = new ArrayList<>();
```

Trong đó:

- `List<String>` diễn đạt hợp đồng mà đoạn mã cần.
- `ArrayList<>` là lớp triển khai được chọn để đáp ứng hợp đồng đó.
- Biến `names` chỉ cho gọi trực tiếp các phương thức mà kiểu khai báo cung cấp.

Lập trình theo interface giúp giảm phụ thuộc vào một lớp cụ thể. Tuy nhiên, thay lớp triển khai vẫn cần kiểm tra thứ tự, hiệu năng, quy tắc `null`, khả năng sửa và yêu cầu đồng bộ. Cùng triển khai một interface không có nghĩa là mọi đặc điểm đều giống nhau.

**Dấu hiệu đã đủ nền:** giải thích được vì sao không thể viết `new List<String>()`, và vì sao một biến kiểu `Collection<String>` không cung cấp thao tác truy cập chỉ số của `List`.

### 2.4. Generics và kiểu phần tử

Cần hiểu `List<String>`, `Set<Integer>`, `Map<String, Integer>` và tác dụng kiểm tra kiểu khi biên dịch.

Những điều cần biết sớm:

- Tham số kiểu của generics là kiểu tham chiếu: dùng `Integer`, không dùng `int` trong `List<...>`.
- Boxing và unboxing có thể xuất hiện khi đưa số nguyên vào hoặc lấy ra khỏi collection.
- Unboxing một giá trị `null` có thể gây `NullPointerException`.
- Raw type như `List` làm suy yếu kiểm tra kiểu và nên tránh trong mã mới.
- `List<Integer>` không phải là kiểu con của `List<Number>`.

Không cần bắt đầu bằng toàn bộ lý thuyết wildcard. Trình tự phù hợp là dùng collection có kiểu cụ thể trước; sau đó học `?`, `? extends T`, `? super T` khi thiết kế phương thức nhận hoặc chuyển dữ liệu giữa các collection.

**Dấu hiệu đã đủ nền:** đọc được một phương thức nhận `List<String>` mà không phải ép kiểu từng phần tử về `String`.

### 2.5. Ngoại lệ và hợp đồng phương thức

Không phải lỗi nào khi dùng collection cũng là lỗi của cấu trúc dữ liệu. Cần biết đọc tên ngoại lệ cùng điều kiện gây lỗi:

| Ngoại lệ hoặc kết quả | Câu hỏi nên kiểm tra |
| --- | --- |
| `IndexOutOfBoundsException` | Chỉ số có nằm trong phạm vi hợp lệ không? |
| `UnsupportedOperationException` | Collection này có hỗ trợ thao tác sửa đang gọi không? |
| `NullPointerException` | Đã truyền `null` vào nơi không cho phép hoặc unbox `null` chưa? |
| `ClassCastException` | Có raw type, ép kiểu sai hoặc phần tử không thể so sánh không? |
| `ConcurrentModificationException` | Có sửa cấu trúc ngoài cách iterator cho phép khi đang duyệt không? |
| `false`, `null` hoặc giá trị cũ | Phương thức này dùng giá trị trả về để báo điều gì? |

Từ “concurrent” trong tên ngoại lệ không chứng minh chương trình đang chạy nhiều luồng. Một luồng cũng có thể gây sửa đổi không hợp lệ trong lúc duyệt. Không dùng ngoại lệ này như cơ chế bảo đảm an toàn giữa các luồng.

### 2.6. Bình đẳng, băm và so sánh

Ba khái niệm phải được tách biệt:

- `==` trên tham chiếu kiểm tra có cùng đối tượng hay không.
- `equals` diễn đạt bình đẳng theo giá trị mà lớp quy định.
- `Comparator` hoặc `Comparable` diễn đạt thứ tự so sánh.

Trước `HashSet` và `HashMap`, phải học hợp đồng `equals`–`hashCode`: hai đối tượng bằng nhau theo `equals` phải có cùng mã băm; cùng mã băm không bắt buộc là bằng nhau.

Trước `TreeSet`, `TreeMap` và `PriorityQueue`, phải học so sánh tự nhiên hoặc `Comparator`. Kết quả so sánh âm, bằng 0, dương diễn đạt quan hệ thứ tự; không nên viết bộ so sánh số bằng cách trừ nếu phép trừ có thể tràn.

Đặc biệt, `TreeSet` xem hai phần tử là cùng vị trí trong tập khi phép so sánh trả về 0. `TreeMap` cũng dùng phép so sánh để nhận diện khóa tương đương trong cây. Nếu chỉ so sánh sinh viên theo tên, hai sinh viên khác mã nhưng cùng tên có thể bị coi là trùng trong một `TreeSet`. Bộ so sánh nên nhất quán với `equals` để tập hợp hoặc bảng ánh xạ tuân thủ đúng hợp đồng tổng quát.

### 2.7. Độ phức tạp và cấu trúc dữ liệu cơ bản

Chỉ cần bắt đầu với:

- `O(1)`: số bước không tăng theo số phần tử ở mức mô hình phân tích.
- `O(log n)`: số bước tăng chậm theo kích thước.
- `O(n)`: có thể phải xem qua số phần tử tỷ lệ với kích thước.
- `O(n log n)`: thường gặp ở các thuật toán sắp xếp dựa trên so sánh.

Sau đó mới tìm hiểu mảng động, danh sách liên kết, bảng băm, cây cân bằng và heap.

Độ phức tạp không phải thời gian đo bằng mili giây. Cần phân biệt trường hợp trung bình, trường hợp xấu nhất và chi phí khấu hao; dữ liệu nhỏ, cấp phát bộ nhớ và cách truy cập cũng ảnh hưởng kết quả thực tế.

## 3. Bản đồ các nhóm trước khi đi sâu

Trong Java 17, có thể dùng sơ đồ rút gọn sau. Sơ đồ chỉ thể hiện các interface chính phục vụ lộ trình này, không phải toàn bộ cây kiểu của JDK.

```text
Iterable<E>
└── Collection<E>
    ├── List<E>
    ├── Set<E>
    │   └── SortedSet<E>
    │       └── NavigableSet<E>
    └── Queue<E>
        └── Deque<E>

Map<K, V>
└── SortedMap<K, V>
    └── NavigableMap<K, V>
```

`Map` thuộc Java Collections Framework nhưng không kế thừa `Collection`. Nó tổ chức các cặp khóa–giá trị; `keySet()`, `values()` và `entrySet()` cung cấp các góc nhìn dạng collection. Vì vậy, không được suy ra rằng phương thức nhận `Collection<E>` sẽ nhận trực tiếp một `Map<K, V>`. Xem [tổng quan Collections Framework của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/doc-files/coll-overview.html).

Ba tên dễ nhầm:

| Tên | Vai trò |
| --- | --- |
| “collection” trong lời giải thích | Tên gọi chung cho cấu trúc chứa nhiều phần tử |
| `Collection<E>` | Interface nền của `List`, `Set`, `Queue` và các nhánh liên quan |
| `Collections` | Lớp chứa các phương thức tiện ích tĩnh như sắp xếp, đảo thứ tự hoặc tạo wrapper |

Không học `Collections` bằng cách tưởng đó là phiên bản số nhiều của interface `Collection`.

## 4. Trình tự học đề xuất

Đây là thứ tự phục vụ người mới: từ thao tác dễ quan sát đến các hợp đồng dễ sai. `Queue` và `Deque` cơ bản có thể học ngay sau `List`; phần hàng đợi ưu tiên nên học sau kiến thức so sánh. Không có yêu cầu mọi người phải học đúng một thứ tự cứng.

### Chặng 1: Collection và cách duyệt

Học các thao tác chung như `size`, `isEmpty`, `add`, `contains`, `remove`, `clear` và vòng lặp `for-each`.

Mục tiêu chưa phải là phân tích cơ chế bên trong. Mục tiêu là hiểu “chứa phần tử”, đọc giá trị trả về và biết thao tác nào làm thay đổi dữ liệu.

Tiếp theo học `Iterable`, `Iterator`, `hasNext()`, `next()` và thao tác xóa qua iterator khi được hỗ trợ. `for-each` thuận tiện cho đọc tuần tự; không phải lời cho phép gọi trực tiếp mọi phương thức sửa collection trong thân vòng lặp.

Bài tập đạt chặng: nhận một danh sách số, đếm các số dương bằng vòng lặp; sau đó loại số âm bằng cách phù hợp như `removeIf` trên danh sách cho phép sửa.

**Có thể chuyển chặng khi:** giải thích được vì sao kết quả `add` hoặc `remove` phải được đọc theo hợp đồng cụ thể, và vì sao biến chứa collection không tự cho biết nó hỗ trợ mọi thao tác sửa.

### Chặng 2: List, bắt đầu với ArrayList

`List` phù hợp khi vị trí và các phần tử lặp có ý nghĩa. Học:

1. Thêm cuối, chèn tại chỉ số, đọc bằng `get`, thay bằng `set`.
2. Xóa theo chỉ số khác với xóa theo giá trị.
3. Duyệt theo thứ tự và tìm kiếm.
4. Quan hệ giữa kích thước logic với sức chứa của mảng bên trong `ArrayList`.
5. Chi phí dịch phần tử khi chèn hoặc xóa ở giữa.

Bắt đầu với `ArrayList` vì dễ nối với kiến thức mảng. Sau đó so sánh `LinkedList` để thấy cùng là `List` nhưng cơ chế truy cập khác nhau.

Không kết luận “thêm xóa nhiều thì dùng `LinkedList`” nếu chưa hỏi vị trí cần sửa đã được xác định như thế nào. `LinkedList.get(i)` cần đi qua các nút; nếu mỗi vòng lại gọi `get(i)`, một lần duyệt có thể bị biến thành chi phí bậc hai.

Bài tập đạt chặng: quản lý các lượt đăng ký và giữ cả những lượt có cùng mã. Quy định rõ đổi một vị trí khác với xóa mọi lượt của một mã.

**Có thể chuyển chặng khi:** chọn được cách duyệt phù hợp, giải thích được chi phí chèn giữa `ArrayList`, và không nhầm thứ tự danh sách với thứ tự đã sắp xếp. Hợp đồng nền ở [List của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html).

### Chặng 3: Set và ý nghĩa “không trùng”

Học `Set` sau khi đã có khái niệm `equals` và `hashCode`.

So sánh:

- `HashSet`: kiểm tra tồn tại và loại trùng, không bảo đảm thứ tự duyệt.
- `LinkedHashSet`: loại trùng và giữ thứ tự thêm trong cách dùng thông thường.
- `TreeSet`: quản lý phần tử theo thứ tự so sánh; để dành các truy vấn điều hướng cho chặng sau.

Câu hỏi quan trọng là **thế nào là hai phần tử trùng nhau trong mô hình của bài toán?** Với `String`, đáp án tương đối trực quan. Với một lớp `Student`, phải xác định bình đẳng theo mã sinh viên hay theo cả mã và tên.

Bài tập đạt chặng: từ các lượt đăng ký, lấy mã duy nhất; lần lượt yêu cầu không quan tâm thứ tự, giữ thứ tự xuất hiện và tăng dần. Chỉ ra thay đổi lớp triển khai nào đáp ứng từng yêu cầu.

**Có thể chuyển chặng khi:** giải thích được vì sao một tập hợp vẫn có thể chứa hai đối tượng khác tham chiếu nhưng chỉ giữ một phần tử theo bình đẳng giá trị; đồng thời hiểu thứ tự của tập không được suy ra từ vài lần in thử. Xem [hợp đồng Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html).

### Chặng 4: Map và tổ chức dữ liệu theo khóa

Học `Map` sau `Set` giúp tái sử dụng kiến thức bình đẳng và băm cho khóa.

Nắm các thao tác `put`, `get`, `containsKey`, `remove`, `getOrDefault` và duyệt `entrySet`. Khi đã hiểu phiên bản vòng lặp, mới thêm `merge`, `computeIfAbsent` hoặc các thao tác nhận hàm.

Những quy tắc cần quan sát:

- Khóa không lặp theo quy tắc của map.
- Giá trị có thể lặp.
- `put` một khóa đã có thường thay giá trị gắn với khóa đó.
- Một khóa có thể gắn với một collection, ví dụ `Map<String, List<String>>`, nếu bài toán cần nhiều giá trị.
- `get(key) == null` chưa luôn đủ để kết luận khóa không tồn tại trong map cho phép giá trị `null`.

So sánh `HashMap`, `LinkedHashMap` và `TreeMap` theo yêu cầu tra cứu, thứ tự và chi phí.

Bài tập đạt chặng: đếm số lượt đăng ký theo mã. Sau đó đổi sang gom danh sách lượt theo mã; giải thích vì sao hai bài toán cần kiểu giá trị khác nhau.

**Có thể chuyển chặng khi:** phân biệt được “khóa chưa có” với “giá trị cũ bị thay”, và dùng được `entrySet` để xử lý từng cặp. Xem [hợp đồng Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

### Chặng 5: Queue và Deque theo chính sách xử lý

Ở các chặng trước, câu hỏi chính là “lưu thế nào?”. Với hàng đợi, cần hỏi thêm “lấy phần tử nào tiếp theo?”.

Học `Deque` với `ArrayDeque` để thấy:

- Thêm cuối, lấy đầu tạo cách dùng FIFO.
- Thêm và lấy cùng một đầu tạo cách dùng LIFO.
- `Deque` cung cấp thao tác ở cả hai đầu.
- Các cặp phương thức có thể khác cách báo thất bại: ném ngoại lệ hoặc trả giá trị đặc biệt.

Ví dụ, khi hàng đợi rỗng, `remove()` ném ngoại lệ còn `poll()` trả `null`. Không nên đưa `null` vào hàng đợi vì sẽ gây mơ hồ với kết quả rỗng; `ArrayDeque` không cho phần tử `null`.

Sau đó học `PriorityQueue`. Đây không phải hàng đợi FIFO. Với thứ tự tự nhiên của số nguyên, phần tử nhỏ nhất được lấy trước. Một bộ so sánh khác có thể định nghĩa độ ưu tiên khác. Duyệt trực tiếp `PriorityQueue` không bảo đảm thứ tự toàn bộ các phần tử; muốn quan sát thứ tự xử lý phải lấy dần từ đầu.

Bài tập đạt chặng: mô phỏng hàng chờ và một danh sách công việc có ưu tiên. Chứng minh thứ tự thêm với thứ tự xử lý có thể khác nhau.

**Có thể chuyển chặng khi:** phân biệt được FIFO, LIFO và ưu tiên; biết chọn phương thức báo rỗng phù hợp. Xem [Queue của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html).

### Chặng 6: Các nhóm có sắp xếp và điều hướng

Quay lại `TreeSet` và `TreeMap` sau khi đã học kỹ `Comparable` và `Comparator`.

Phân biệt:

- `SortedSet` và `SortedMap`: duy trì thứ tự cùng các thao tác theo miền giá trị.
- `NavigableSet` và `NavigableMap`: thêm các truy vấn như phần tử hoặc khóa nhỏ hơn, nhỏ hơn hoặc bằng, lớn hơn hoặc bằng, lớn hơn.

Không cần học thuộc ngay mọi tên phương thức. Trước hết nối yêu cầu với ý nghĩa của `lower`, `floor`, `ceiling`, `higher`.

Bài tập đạt chặng: từ tập điểm `{50, 70, 90}`, phân biệt điểm gần nhất không vượt quá 80 với điểm nhỏ nhất từ 80 trở lên. Kết quả lần lượt là 70 và 90.

**Có thể chuyển chặng khi:** diễn đạt được điều kiện biên và hiểu comparator ảnh hưởng cả thứ tự lẫn nhận diện phần tử hoặc khóa trong cấu trúc cây.

### Chặng 7: Khả năng sửa, view và sao chép

Đây là phần cần học trước khi thiết kế API trả collection ra ngoài một lớp.

Phân biệt:

| Cách tạo hoặc lấy danh sách | Ý nghĩa cần nhớ |
| --- | --- |
| `new ArrayList<>(source)` | Tạo danh sách mới có thể sửa; không sao chép sâu phần tử |
| `List.of(...)` | Danh sách không cho sửa và không nhận phần tử `null` |
| `List.copyOf(source)` | Lấy danh sách không cho sửa với các phần tử của nguồn tại thời điểm gọi; không sao chép sâu phần tử |
| `Arrays.asList(array)` | Danh sách kích thước cố định gắn với mảng; có thể thay phần tử bằng `set`, không thêm hoặc xóa phần tử |
| `Collections.unmodifiableList(source)` | Góc nhìn không cho sửa qua wrapper; thay đổi ở nguồn vẫn có thể được nhìn thấy |
| `list.subList(from, to)` | Góc nhìn một phần của danh sách, cần tôn trọng hợp đồng với danh sách nền |

Không cho sửa qua một tham chiếu không đồng nghĩa toàn bộ dữ liệu là bất biến sâu. Nếu phần tử là đối tượng có thể thay đổi, nội dung của chính phần tử vẫn có thể thay đổi. Với `List.copyOf`, không nên dựa vào việc luôn tạo ra một đối tượng mới; điều cần dựa vào là hợp đồng của kết quả.

Bài tập đạt chặng: cung cấp danh sách từ một lớp quản lý dữ liệu; chọn giữa cho sửa trực tiếp, trả view chỉ đọc và trả bản chụp cấu trúc. Giải thích ảnh hưởng khi nguồn hoặc phần tử thay đổi.

### Chặng 8: Thuật toán tiện ích và xử lý bằng Stream

Học sắp xếp, đảo thứ tự, tìm kiếm và sao chép sau khi hiểu collection nguồn có thể sửa hay không.

Một số điều kiện phải kiểm tra:

- Sắp xếp cần cách so sánh đúng.
- Tìm kiếm nhị phân cần dữ liệu được sắp theo đúng thứ tự tương ứng.
- Sắp xếp tại chỗ khác với tạo danh sách kết quả mới.
- Loại trùng vẫn phụ thuộc ý nghĩa bình đẳng.
- Kết quả chuyển đổi có thể có hợp đồng sửa khác với nguồn.

Sau vòng lặp, học lambda, method reference và Stream để viết lọc, ánh xạ, gom nhóm, đếm. Cần phân biệt collection lưu dữ liệu với stream biểu diễn một luồng xử lý; stream không phải collection thứ hai và không dùng lại sau khi đã thực hiện thao tác kết thúc.

Không dùng Stream để che việc chưa hiểu kiểu dữ liệu, thứ tự hoặc quy tắc trùng. Trước hết viết được lời giải bằng vòng lặp, sau đó so sánh độ rõ ràng của hai cách.

### Chặng 9: Collection dùng chung giữa nhiều luồng

Chỉ học phần này sau kiến thức cơ bản về luồng, đồng bộ, hiển thị bộ nhớ và thao tác nguyên tử.

Các nhánh cần khảo sát gồm wrapper đồng bộ, `ConcurrentHashMap`, `BlockingQueue`, `CopyOnWriteArrayList` và các collection đồng thời chuyên dụng.

Cần phân biệt:

- Một phương thức an toàn giữa các luồng.
- Một chuỗi thao tác nghiệp vụ nguyên tử.
- Cách duyệt mà cấu trúc cụ thể cho phép.
- Khả năng nhìn thấy thay đổi từ luồng khác.

Chẳng hạn, “nếu chưa có thì thêm” bằng hai lời gọi riêng không tự trở thành một thao tác nguyên tử chỉ vì map có hỗ trợ truy cập đồng thời. Phải chọn thao tác hoặc cơ chế đồng bộ phù hợp.

`parallelStream()` cũng không biến mọi thao tác sửa collection trong lambda thành an toàn.

**Dấu hiệu đã đạt:** giải thích được vì sao đổi lớp collection không đủ để sửa mọi lỗi đồng thời. Cơ chế đồng bộ phải được đọc theo tài liệu của từng lớp; interface `Collection` không đưa ra một bảo đảm đồng bộ chung cho mọi lớp triển khai. Xem [Collection: hợp đồng đồng bộ, view và khả năng sửa](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html).

## 5. Hiệu năng cần học sau ngữ nghĩa

Khi đã biết kiểu dữ liệu phù hợp, mới so sánh chi phí của những lựa chọn đáp ứng cùng yêu cầu.

| Cấu trúc và thao tác | Mô hình chi phí thường dùng | Điều phải giữ trong đầu |
| --- | --- | --- |
| `ArrayList.get(i)` | `O(1)` | Chỉ số hợp lệ |
| Thêm cuối `ArrayList` | `O(1)` khấu hao | Một lần tăng sức chứa có thể phải sao chép nhiều phần tử |
| Chèn hoặc xóa giữa `ArrayList` | `O(n)` | Có dịch các phần tử phía sau |
| `LinkedList.get(i)` | `O(n)` | Phải tìm nút theo vị trí |
| Thêm hoặc xóa qua vị trí iterator phù hợp ở danh sách liên kết | Thao tác nối hoặc bỏ nút là `O(1)` | Việc đi đến vị trí trước đó vẫn có chi phí |
| Tra cứu cơ bản bằng bảng băm | Kỳ vọng gần `O(1)` với điều kiện băm phù hợp | Không phải bảo đảm vô điều kiện cho mọi đầu vào |
| Tra cứu trong cây cân bằng | `O(log n)` | Chi phí so sánh cũng cần tính đến |
| Thêm hoặc lấy phần tử đầu `PriorityQueue` | Thường học theo `O(log n)` | Không áp dụng cùng kết luận cho tìm phần tử bất kỳ |

Bảng này giúp xây dựng trực giác, không thay thế tài liệu của từng phương thức. Chỉ đo hiệu năng sau khi bảo đảm hai lời giải có cùng kết quả, cùng hợp đồng và khối lượng công việc đại diện.

Ví dụ, dùng `HashSet` nhanh hơn một vòng tìm trên danh sách không có giá trị nếu bài toán cần giữ các phần tử lặp mà tập hợp đã loại bỏ.

## 6. Chương trình tổng hợp: một đầu vào, nhiều cách tổ chức

Chương trình sau nối các chặng cơ bản. Không có xử lý đa luồng hoặc phụ thuộc bên ngoài.

Lưu nguyên nội dung vào tệp `CollectionLearningPathDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class CollectionLearningPathDemo {
    public static void main(String[] args) {
        List<String> registrations =
                new ArrayList<>(List.of("S2", "S1", "S2"));

        Set<String> uniqueCodes = new LinkedHashSet<>(registrations);

        Map<String, Integer> counts = new TreeMap<>();
        for (String code : registrations) {
            counts.put(code, counts.getOrDefault(code, 0) + 1);
        }

        Set<String> sortedCodes = new TreeSet<>(uniqueCodes);

        Deque<String> waiting = new ArrayDeque<>(uniqueCodes);
        List<String> processed = new ArrayList<>();
        while (!waiting.isEmpty()) {
            processed.add(waiting.removeFirst());
        }

        PriorityQueue<Integer> priorities =
                new PriorityQueue<>(List.of(30, 10, 20));
        List<Integer> priorityOrder = new ArrayList<>();
        while (!priorities.isEmpty()) {
            priorityOrder.add(priorities.remove());
        }

        List<String> snapshot = List.copyOf(registrations);
        registrations.add("S3");

        System.out.println("Unique codes: " + uniqueCodes);
        System.out.println("Counts by code: " + counts);
        System.out.println("Sorted codes: " + sortedCodes);
        System.out.println("FIFO order: " + processed);
        System.out.println("Priority order: " + priorityOrder);
        System.out.println("Source size: " + registrations.size());
        System.out.println("Snapshot size: " + snapshot.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 CollectionLearningPathDemo.java
java CollectionLearningPathDemo
```

Kết quả chuẩn:

```text
Unique codes: [S2, S1]
Counts by code: {S1=1, S2=2}
Sorted codes: [S1, S2]
FIFO order: [S2, S1]
Priority order: [10, 20, 30]
Source size: 4
Snapshot size: 3
```

Giải thích theo yêu cầu thay vì theo từng câu lệnh:

1. `registrations` ban đầu giữ ba lượt, trong đó `"S2"` xuất hiện hai lần. `List` bảo toàn thông tin này.
2. `uniqueCodes` chỉ giữ hai mã và giữ thứ tự xuất hiện đầu tiên nhờ `LinkedHashSet`.
3. `counts` dùng mã làm khóa, số lần làm giá trị. `TreeMap` giúp thứ tự in khóa ổn định theo thứ tự tự nhiên của chuỗi.
4. `sortedCodes` biểu diễn một tập có sắp xếp, nên `"S1"` đứng trước `"S2"`.
5. `waiting` được thêm theo thứ tự của `uniqueCodes`, rồi lấy đầu bằng `removeFirst()`; đây là cách dùng FIFO.
6. `priorities` nhận 30, 10, 20 nhưng lấy ra 10, 20, 30. Chương trình lấy từng phần tử bằng `remove()`, không dựa vào thứ tự iterator của heap.
7. `snapshot` lấy từ ba lượt ban đầu. Thêm `"S3"` vào danh sách nguồn khiến kích thước nguồn thành 4, còn kích thước danh sách chụp vẫn là 3.

Chương trình dùng `String` và `Integer` để tránh phải định nghĩa bình đẳng cùng khả năng so sánh của lớp nghiệp vụ ngay trong ví dụ nhập môn. Khi chuyển sang đối tượng tự định nghĩa, phải quay lại kiến thức `equals`, `hashCode` và `Comparator`.

## 7. Những lỗi học và dùng collection thường gặp

### 7.1. Nhầm thứ tự thêm với thứ tự sắp xếp

`List` có thứ tự vị trí nhưng không tự sắp xếp giá trị. `LinkedHashSet` giữ thứ tự thêm nhưng không phải thứ tự tăng dần. `HashSet` không bảo đảm thứ tự dù một lần in trông có vẻ được sắp.

Hãy ghi rõ loại thứ tự mà bài toán cần trước khi chọn lớp.

### 7.2. Dùng Set rồi phát hiện đã mất dữ liệu lặp

Nếu một sinh viên có thể đăng ký nhiều lần và mỗi lượt là một sự kiện cần thống kê, chuyển ngay tất cả sang `Set` làm mất thông tin.

Có thể giữ `List` làm dữ liệu nguồn và tạo thêm `Set` cho nhu cầu riêng. Chọn collection cho một góc nhìn không nhất thiết thay thế nguồn dữ liệu gốc.

### 7.3. Nhầm hai phương thức remove của List<Integer>

Đoạn minh họa:

```java
List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));
numbers.remove(1);
numbers.remove(Integer.valueOf(30));
```

Lời gọi đầu dùng đối số `int`, nên xóa phần tử ở chỉ số 1, tức 20. Lời gọi sau dùng `Integer`, nên xóa theo giá trị 30. Danh sách cuối còn `[10]`.

Đây là kiến thức overload và boxing được áp dụng vào collection, không phải một quy tắc ngẫu nhiên của danh sách.

### 7.4. Thay khóa sau khi đã đưa vào HashMap

Nếu một trường được dùng trong `equals` hoặc `hashCode` bị đổi sau khi đối tượng đã làm khóa, cấu trúc băm không tự bố trí lại khóa theo giá trị mới. Việc tra cứu có thể không còn hoạt động như người viết mong đợi.

Nên thiết kế khóa có giá trị ổn định. Vấn đề tương tự xuất hiện khi sửa trạng thái dùng để so sánh của phần tử đang ở trong một tập hoặc map có sắp xếp.

### 7.5. Coi view là bản sao

Lấy `map.keySet()` hoặc `list.subList(...)` không mặc nhiên tạo bản sao độc lập. Xóa qua một view được phép sửa có thể tác động đến cấu trúc nền.

Trước mỗi API trả collection, hỏi: **đây là đối tượng mới độc lập, view sống theo nguồn hay wrapper hạn chế thao tác?**

### 7.6. Coi unmodifiable là bất biến sâu

`List.of` không cho thêm, xóa hoặc thay phần tử trong danh sách. Nó không khóa các đối tượng có thể thay đổi mà danh sách đang tham chiếu.

Nếu cần một kết quả không thay đổi về mặt nghiệp vụ, phải xét cả loại phần tử, quyền sở hữu dữ liệu và khả năng người khác đang giữ tham chiếu tới phần tử.

### 7.7. Học Stream quá sớm

Một chuỗi `filter`–`map`–`collect` có thể ngắn hơn vòng lặp, nhưng không giải thích thay cho người viết về trùng lặp, thứ tự, bình đẳng hay khả năng sửa.

Thứ tự nên là hiểu dữ liệu và hợp đồng, làm đúng bằng các thao tác cơ bản, rồi mới chọn cách diễn đạt bằng Stream.

### 7.8. Chọn cấu trúc theo một mẹo hiệu năng

“Map luôn nhanh”, “LinkedList luôn tốt cho thêm xóa”, hoặc “collection đồng thời giải quyết mọi lỗi luồng” đều thiếu điều kiện.

Hãy nêu thao tác, dữ liệu, bảo đảm cần có và chi phí liên quan. Khi chưa chứng minh được lựa chọn, quay lại hợp đồng và bài toán thay vì thay lớp ngẫu nhiên.

## 8. Bài tập theo trình tự và tiêu chí hoàn thành

| Bài tập | Kiến thức chính | Bằng chứng đã hiểu |
| --- | --- | --- |
| Giữ danh sách lượt đăng ký và sửa một lượt theo vị trí | `List` | Không vô tình xóa mọi lượt cùng mã |
| Lấy mã duy nhất theo ba yêu cầu thứ tự | `Set` | Phân biệt không bảo đảm thứ tự, thứ tự thêm và thứ tự so sánh |
| Đếm lượt theo mã và gom lượt theo mã | `Map` | Chọn đúng kiểu giá trị số đếm hoặc danh sách |
| Mô phỏng hàng chờ, hoàn tác và ưu tiên công việc | `Deque`, `PriorityQueue` | Giải thích rõ FIFO, LIFO và ưu tiên |
| Tìm mốc gần nhất không vượt giá trị cho trước | `NavigableSet` hoặc `NavigableMap` | Xử lý đúng bằng, nhỏ hơn và lớn hơn |
| Trả dữ liệu từ một lớp quản lý ra bên ngoài | View, copy, unmodifiable | Biết thao tác nào của bên gọi có thể ảnh hưởng nguồn |
| Viết lại phép thống kê bằng Stream | Lambda, Stream, collector | Kết quả và quy tắc dữ liệu giữ nguyên |
| Nhiều tác vụ cùng cập nhật bộ đếm | Đồng thời | Chọn được thao tác nguyên tử và giải thích cách duyệt |

Với mỗi bài, nên tự kiểm tra ít nhất các đầu vào: rỗng, một phần tử, có phần tử lặp, không tìm thấy dữ liệu và có nhiều phần tử bằng nhau theo tiêu chí nghiệp vụ.

Chỉ kiểm tra `null` khi bài toán hoặc API cho phép nó. Không mặc nhiên đưa `null` vào mọi cấu trúc rồi mong một hành vi chung.

## 9. Câu hỏi tự đánh giá

1. Vì sao `Map` thuộc framework nhưng không thể truyền trực tiếp vào tham số `Collection`?
2. Một `List` có thứ tự có đồng nghĩa các giá trị được sắp tăng dần không?
3. Yêu cầu nào khiến `LinkedHashSet` phù hợp hơn `HashSet`?
4. Hai đối tượng khác tham chiếu có thể bị `HashSet` coi là trùng không? Điều kiện nào quyết định?
5. Vì sao bộ so sánh chỉ theo tên có thể làm mất một sinh viên trong `TreeSet`?
6. Khi nào một lần `put` vào map thay dữ liệu thay vì thêm một khóa mới?
7. Vì sao không thể dùng thứ tự in trực tiếp của `PriorityQueue` để chứng minh thứ tự ưu tiên?
8. `List.copyOf` và `Collections.unmodifiableList` khác nhau thế nào khi danh sách nguồn được sửa?
9. Sao chép `ArrayList` chứa đối tượng có tạo bản sao của từng đối tượng không?
10. Vì sao sửa collection trong lúc duyệt có thể lỗi dù chỉ có một luồng?
11. Vì sao phải hiểu chi phí tìm vị trí trước khi nói thêm hoặc xóa ở danh sách liên kết là nhanh?
12. Một lớp collection an toàn giữa các luồng có bảo đảm nguyên tử cho mọi chuỗi thao tác nghiệp vụ không?

Người học đã sẵn sàng đi sâu từng nhóm khi có thể trả lời bằng quy tắc, ví dụ hoặc một chương trình nhỏ; chỉ nhớ tên phương thức chưa đủ.

## 10. Cách dùng tài liệu để tiếp tục học

Mỗi khi gặp một lớp mới, đọc theo thứ tự:

1. Hợp đồng interface: cấu trúc biểu diễn điều gì?
2. Bảo đảm riêng của lớp: thứ tự, `null`, khả năng sửa, đồng bộ.
3. Hợp đồng phương thức: đầu vào, giá trị trả về, ngoại lệ.
4. Chi phí và điều kiện sử dụng.
5. Ví dụ nhỏ để kiểm chứng điều vừa đọc.

Các trang nên dùng cùng bài học:

- [Collections Framework Overview — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/doc-files/coll-overview.html): bản đồ interface, lớp triển khai và thuật toán.
- [Collection — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html): nền chung, thao tác tùy chọn, view và khả năng sửa.
- [List — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html), [Set — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html), [Map — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): hợp đồng của từng kiểu tổ chức dữ liệu.
- [Queue — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html): chính sách phần tử đầu và các cặp phương thức báo thất bại.

Với JDK khác, mở tài liệu đúng phiên bản đang dùng. Sơ đồ của một phiên bản không nên được coi là danh sách bất biến của mọi interface hoặc API trong các phiên bản sau.

Lộ trình đạt mục tiêu khi người học có thể đi từ yêu cầu nghiệp vụ đến hợp đồng dữ liệu, chọn cấu trúc phù hợp, viết ví dụ kiểm chứng và giải thích được giới hạn của lựa chọn đó.


</details>

- [Quay lại đầu trang](#back-to-top)
