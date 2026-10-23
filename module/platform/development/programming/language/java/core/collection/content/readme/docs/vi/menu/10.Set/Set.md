<a id="back-to-top"></a>

# Set

## Menu
- [Ý nghĩa không trùng lặp và hợp đồng của Set](#set-001)
- [Kết quả add khi phần tử đã tồn tại](#set-002)
- [Kiểm tra thuộc và xóa phần tử](#set-003)
- [Hợp, giao, hiệu và tập con bằng các thao tác collection](#set-004)
- [Loại trùng nhưng bảo toàn thứ tự mong muốn](#set-005)
- [equals và hashCode không phụ thuộc thứ tự của Set](#set-006)
- [Chọn HashSet, LinkedHashSet, TreeSet hoặc EnumSet](#set-007)

## <a id="set-001">Ý nghĩa không trùng lặp và hợp đồng của Set</a>

<details>
<summary>Xem chi tiết</summary>

# Ý nghĩa không trùng lặp và hợp đồng của Set

## 1. Set biểu diễn quan hệ “có hay không”, không đếm số lần xuất hiện

`Set<E>` là một collection mô hình hóa tập hợp: mỗi giá trị phân biệt chỉ có một lần hiện diện trong tập theo quan hệ bằng nhau mà hợp đồng yêu cầu.

Ví dụ, một người có quyền `read` hoặc không có quyền đó. Việc dữ liệu đầu vào nhắc đến `read` ba lần thường không làm người đó có “ba lần quyền read”. Set có thể phù hợp để biểu diễn tập mã quyền.

Ngược lại, nếu dữ liệu là ba lần quét một sản phẩm, số lần xuất hiện có thể có ý nghĩa. Chuyển thẳng sang Set làm mất thông tin về số lần quét. Phải xác định yêu cầu trước khi loại trùng.

```text
Dữ liệu đầu vào: read, write, read

Nhìn như các lần xuất hiện: có 3 lần.
Nhìn như tập giá trị: có read và write, tức 2 giá trị phân biệt.
```
Điều quan trọng không chỉ là “Set không trùng”. Phải trả lời được:

1. Hai phần tử được coi là trùng theo tiêu chí nào?
2. Khi thêm một phần tử đã có, tập làm gì và trả về gì?
3. Tập có cho sửa, có nhận null, có giữ thứ tự không?
4. Nếu phần tử thay đổi sau khi được thêm, điều gì xảy ra?
5. Hai đối tượng Set được so bằng nhau như thế nào?

Hợp đồng gốc được mô tả trong [Set của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html). Bài này dùng Java 17 trở lên và các ví dụ thao tác tuần tự trong một luồng.

## 2. “Trùng” là một quan hệ bằng nhau, không phải một hình thức giống nhau tùy ý

### 2.1. Hợp đồng chung dựa trên equals

Với hai phần tử không null, Set không được giữ đồng thời hai phần tử bằng nhau theo equals nếu nó tuân thủ đầy đủ hợp đồng Set.

Ở mức mô tả có xét null, các phương thức như add và contains được quy định bằng quan hệ `Objects.equals`:

```text
Objects.equals(a, b)
- true nếu cả hai là null;
- với a không null, dựa vào a.equals(b);
- false nếu chỉ một bên là null.
```
Đây là mô tả ngữ nghĩa. Một cách triển khai không bắt buộc gọi equals với mọi phần tử theo một vòng lặp. HashSet có thể dùng hashCode để định tuyến; TreeSet có quy tắc so sánh sẽ được giải thích riêng.

### 2.2. Tham chiếu khác nhau vẫn có thể bằng nhau

```java
// Đoạn minh họa.
String first = new String("read");
String second = new String("read");

System.out.println(first == second);      // false: hai đối tượng khác nhau.
System.out.println(first.equals(second)); // true: cùng nội dung chuỗi.
```
Hai String này không được giữ như hai giá trị phân biệt trong một Set tuân thủ quan hệ equals của String.

Toán tử == trên tham chiếu hỏi “có cùng đối tượng hay không”. equals hỏi “hai đối tượng có bằng nhau theo quy tắc của kiểu này hay không”. Hai câu hỏi có thể có đáp án khác nhau.

Ngược lại, hai đối tượng in ra cùng một chuỗi qua toString chưa chắc bằng nhau. Set không dùng hình thức in ra làm quy tắc nhận diện.

### 2.3. Set không tự tìm “khóa nghiệp vụ”

Nếu một lớp có id, name và status, Set không tự biết phải dùng id để phân biệt. Nó dùng quan hệ bằng nhau do kiểu phần tử và cách triển khai liên quan quy định.

Một lớp không ghi đè equals thường giữ quy tắc bằng nhau theo danh tính đối tượng kế thừa từ Object. Hai đối tượng có các trường trông giống nhau vẫn có thể được giữ riêng.

Theo mặc định, trình biên dịch sinh equals cho record dựa trên các thành phần record. Ví dụ record gồm id và displayName có thể coi hai bản ghi cùng id nhưng khác displayName là hai giá trị khác nhau. Đó có thể là đúng với một ảnh chụp dữ liệu, nhưng chưa đáp ứng yêu cầu “duy nhất theo id”.

Phải thiết kế tiêu chí phân biệt trước khi chọn Set làm cơ chế loại trùng.

### 2.4. Những khác biệt văn bản không tự được chuẩn hóa

Với String, `"read"` và `"READ"` không bằng nhau theo equals. Khoảng trắng và các khác biệt nội dung khác cũng không tự bị bỏ đi.

Nếu nghiệp vụ coi một số hình thức là tương đương, cần một quy tắc chuẩn hóa rõ ràng trước khi thêm hoặc một kiểu khóa có ngữ nghĩa phù hợp. Không áp một phép đổi chữ hoa/thường hoặc cắt chuỗi cho mọi loại định danh mà chưa xác định yêu cầu.

Set chỉ thực thi quan hệ được cung cấp; nó không quyết định thay nghiệp vụ rằng hai cách viết nào phải được gộp.

## 3. Hợp đồng của add, contains, remove và size

### 3.1. add trả về việc tập có thay đổi hay không

Đối với một Set có hỗ trợ thêm và phần tử hợp lệ:

| Trạng thái trước lời gọi add(e) | Trạng thái sau | Giá trị trả về |
| --- | --- | --- |
| Chưa có phần tử bằng e | Có thêm e | true |
| Đã có phần tử bằng e | Tập giữ nguyên | false |

Khi add trả false vì đã có phần tử bằng nhau, hợp đồng yêu cầu tập không đổi. Đây không phải thao tác “thay bản cũ bằng bản mới” hoặc “cập nhật toàn bộ các trường mới nhất”.

Một đối tượng mới được tạo bằng new có thể bị từ chối như phần tử trùng. Việc nó có danh tính khác không tạo quyền hiện diện thứ hai nếu equals coi hai đối tượng bằng nhau.

Nếu Set không hỗ trợ sửa hoặc phần tử không hợp lệ, add có thể ném ngoại lệ. Không hiểu false là tín hiệu chung cho mọi lỗi thêm.

### 3.2. contains kiểm tra hiện diện theo quan hệ bằng nhau

contains(query) có ý nghĩa: có phần tử trong tập bằng query hay không. Query không cần là chính tham chiếu từng được thêm trước đó.

Ví dụ, sau khi thêm `new String("read")`, việc hỏi bằng một String khác có cùng nội dung vẫn phải phản ánh cùng giá trị hiện diện nếu các hợp đồng được giữ đúng.

Không suy ra contains luôn tìm tuyến tính hoặc luôn O(1) từ tên giao diện Set. Cách tìm và chi phí phụ thuộc triển khai.

### 3.3. remove xóa sự hiện diện

Với Set có hỗ trợ xóa:

- remove(query) trả true nếu xóa một phần tử bằng query và tập thay đổi.
- Trả false khi không có phần tử phù hợp để xóa.
- Nó không cần query là cùng tham chiếu với phần tử đang giữ.

Sau khi xóa thành công, giá trị đó không còn trong tập. Điều này khác với xóa một lần xuất hiện trong một List có nhiều phần tử bằng nhau.

### 3.4. size đếm số phần tử phân biệt đang có

size không đếm số lần đã thử gọi add. Nếu add A ba lần và B một lần thành công theo quan hệ bằng nhau thông thường, tập có hai phần tử phân biệt.

Từ một Set đã tạo xong, không thể khôi phục chắc chắn A từng xuất hiện bao nhiêu lần trong nguồn. Nếu cần số lần, dùng một mô hình đếm hoặc giữ dữ liệu gốc phù hợp.

### 3.5. Những điều Set không định nghĩa

Giao diện Set không cung cấp chỉ số vị trí như List.get(i). Nó cũng không tự mang ý nghĩa:

- Phần tử đầu tiên theo thời gian thêm.
- Phần tử được sắp theo giá trị.
- Tự cập nhật đối tượng đang có khi thêm một đối tượng bằng nó.
- Mọi phép sửa đều được hỗ trợ.
- Mọi loại phần tử, null hoặc truy vấn sai kiểu đều được chấp nhận.
- An toàn cho nhiều luồng.
- Tốc độ của add/contains/remove là một mức cố định.

Những thuộc tính này phải được lấy từ hợp đồng của cách triển khai cụ thể.

## 4. Ví dụ chạy được: thêm trùng, kiểm tra hiện diện và xóa

Lưu chương trình vào `SetContractDemo.java`:

```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SetContractDemo {
    public static void main(String[] args) {
        Set<String> permissions = new LinkedHashSet<>();

        System.out.println("add read = " + permissions.add("read"));
        System.out.println("add write = " + permissions.add("write"));
        System.out.println("add equal read = "
                + permissions.add(new String("read")));
        System.out.println("values = " + permissions);
        System.out.println("size = " + permissions.size());
        System.out.println("contains equal read = "
                + permissions.contains(new String("read")));

        Set<String> other = new LinkedHashSet<>(List.of("write", "read"));
        System.out.println("equal sets in different orders = "
                + permissions.equals(other));
        System.out.println("equal set hash codes = "
                + (permissions.hashCode() == other.hashCode()));

        List<String> list = new ArrayList<>(List.of("read", "write"));
        System.out.println("set equals list = " + permissions.equals(list));

        System.out.println("addAll changed = "
                + permissions.addAll(List.of("write", "admin", "admin")));
        System.out.println("after addAll = " + permissions);
        System.out.println("remove missing = " + permissions.remove("missing"));
        System.out.println("remove read = " + permissions.remove("read"));
        System.out.println("final = " + permissions);
    }
}
```
Biên dịch và chạy:

```shell
javac --release 17 SetContractDemo.java
java SetContractDemo
```
Kết quả:

```text
add read = true
add write = true
add equal read = false
values = [read, write]
size = 2
contains equal read = true
equal sets in different orders = true
equal set hash codes = true
set equals list = false
addAll changed = true
after addAll = [read, write, admin]
remove missing = false
remove read = true
final = [write, admin]
```
Giải thích:

1. read được thêm lần đầu nên true.
2. write là giá trị khác nên true.
3. String mới có nội dung read vẫn bằng giá trị đã có, nên add trả false.
4. size là 2 dù đã có ba lần thử thêm.
5. contains bằng một String mới vẫn nhận ra giá trị read.
6. Thêm cả lô write, admin, admin chỉ thêm được giá trị phân biệt mới là admin.
7. Xóa missing không đổi tập; xóa read làm tập đổi.

Ví dụ dùng LinkedHashSet để thứ tự in ra xác định theo thứ tự thêm. Không lấy output `[read, write]` làm bảo đảm rằng mọi Set đều duyệt theo thứ tự đó.

Các dòng về equals và hashCode của hai tập sẽ được giải thích ngay sau đây. Chúng là hợp đồng của **đối tượng tập hợp**, khác với equals/hashCode của từng phần tử bên trong.

## 5. Hai Set bằng nhau theo thành viên, không theo thứ tự duyệt

### 5.1. Hợp đồng Set.equals

Hai Set tuân thủ các hợp đồng liên quan bằng nhau khi:

1. Đối tượng bên kia cũng là một Set.
2. Hai tập có cùng kích thước.
3. Có cùng các phần tử theo quan hệ hiện diện phù hợp với hợp đồng chung.

Vì vậy, tập có read và write bằng tập có write và read. Thứ tự chèn hoặc duyệt không tham gia định nghĩa này.

Một List có cùng các giá trị không tự bằng Set. List còn có ngữ nghĩa thứ tự và số lần xuất hiện; Set không đại diện cùng một loại giá trị collection.

Hợp đồng này cho phép những cách triển khai Set khác nhau vẫn so sánh giá trị với nhau khi chúng dùng các quy tắc bằng nhau nhất quán.

### 5.2. Hợp đồng Set.hashCode

Hash code của Set được định nghĩa từ tổng hash code các phần tử, trong đó null đóng góp 0. Phép tính dùng quy tắc số nguyên Java.

Thứ tự cộng không làm thay đổi kết quả. Vì vậy, đổi thứ tự duyệt không được làm hai tập bằng nhau có hash code khác nhau.

Quan hệ bắt buộc là:

```text
setA.equals(setB) == true
=> setA.hashCode() == setB.hashCode()
```
Chiều ngược lại không đúng. Hai tập khác nhau có thể có cùng hash code. Hash code không phải mã duy nhất của nội dung tập và không phải bằng chứng hai tập bằng nhau.

Định nghĩa này cũng nhắc rằng sửa nội dung một Set có thể làm hashCode của chính Set đó đổi. Nếu đặt một Set có thể sửa vào một cấu trúc băm bên ngoài như một phần tử hoặc khóa, phải tính cả rủi ro thay đổi giá trị dùng để nhận diện.

Set cũng không được tự chứa chính nó như một phần tử theo lưu ý của hợp đồng Set. Không dùng các cấu trúc tự tham chiếu để thử “đánh đố” equals hoặc hashCode rồi coi kết quả là cách sử dụng hợp lệ.

## 6. equals và hashCode của phần tử phải hợp tác đúng

### 6.1. equals cần có các tính chất của quan hệ bằng nhau

Khi tự định nghĩa equals, cần giữ các yêu cầu trong [Object.equals và Object.hashCode của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html):

- Phản xạ: một đối tượng bằng chính nó.
- Đối xứng: a bằng b thì b phải bằng a.
- Bắc cầu: a bằng b, b bằng c thì a bằng c.
- Nhất quán khi dữ liệu liên quan không đổi.
- Đối tượng không null không bằng null.

Nếu phá các tính chất này, thêm cùng dữ liệu theo thứ tự khác nhau có thể dẫn đến những hành vi không còn là mô hình tập hợp đáng tin cậy.

### 6.2. Bằng nhau thì phải có cùng hashCode

Với cấu trúc băm như HashSet hoặc LinkedHashSet:

```text
a.equals(b) == true
=> a.hashCode() == b.hashCode()
```
Hash code giống nhau không bắt buộc equals phải true. Hai phần tử khác nhau có thể rơi vào cùng vùng tra cứu và vẫn được phân biệt bằng quan hệ bằng nhau.

Nếu ghi đè equals mà giữ hashCode không phù hợp, cấu trúc băm có thể định tuyến hai phần tử bằng nhau sang các vị trí khác nhau. Không quy kết hiện tượng đó thành “HashSet không biết loại trùng”; kiểu phần tử đã phá tiền đề mà cấu trúc cần.

Ngược lại, chỉ ghi đè hashCode mà không có equals đúng cũng không tạo ra tiêu chí duy nhất nghiệp vụ. Hash code không thay thế quan hệ bằng nhau.

### 6.3. Đừng gắn tiêu chí duy nhất sai vào cả kiểu dữ liệu

Có thể một nghiệp vụ cần duy nhất theo id, nghiệp vụ khác cần phân biệt từng ảnh chụp id + tên + thời điểm. Không nên tùy tiện thay equals của một kiểu dùng khắp chương trình chỉ để giải quyết một phép lọc cục bộ.

Một lựa chọn là tách khóa dùng để nhận diện: Set<String> các id hoặc một kiểu khóa bất biến. Nếu cần liên hệ id với bản ghi mới nhất, Map từ id đến bản ghi thường thể hiện mục tiêu đó rõ hơn một Set chỉ biết hiện diện.

Quan hệ bằng nhau là một quyết định của mô hình, không phải thủ thuật để làm size của một ví dụ giảm xuống.

## 7. Ví dụ chạy được: cùng id có thể trùng hoặc không, tùy mô hình

Lưu vào `SetBusinessEqualityDemo.java`:

```java
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class SetBusinessEqualityDemo {
    record UserSnapshot(String id, String displayName) {}

    static final class Member {
        private final String id;
        private String displayName;

        Member(String id, String displayName) {
            this.id = Objects.requireNonNull(id, "id");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Member member && id.equals(member.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    public static void main(String[] args) {
        Set<UserSnapshot> snapshots = new LinkedHashSet<>();
        snapshots.add(new UserSnapshot("U1", "Lan"));
        snapshots.add(new UserSnapshot("U1", "Linh"));
        System.out.println("record values with same id = " + snapshots.size());

        Member first = new Member("U1", "Lan");
        Member incoming = new Member("U1", "Linh");
        Set<Member> members = new LinkedHashSet<>();

        System.out.println("add first member = " + members.add(first));
        System.out.println("add equal incoming = " + members.add(incoming));
        System.out.println("member count = " + members.size());

        Member retained = members.iterator().next();
        System.out.println("retained the first reference = " + (retained == first));
        System.out.println("retained display name = " + retained.displayName);

        first.displayName = "Lan updated";
        System.out.println("contains same id after rename = "
                + members.contains(new Member("U1", "ignored")));
        System.out.println("display name after rename = "
                + members.iterator().next().displayName);
    }
}
```
Biên dịch và chạy:

```shell
javac --release 17 SetBusinessEqualityDemo.java
java SetBusinessEqualityDemo
```
Kết quả:

```text
record values with same id = 2
add first member = true
add equal incoming = false
member count = 1
retained the first reference = true
retained display name = Lan
contains same id after rename = true
display name after rename = Lan updated
```
Có hai mô hình riêng:

- UserSnapshot là record có id và displayName. Hai snapshot cùng id nhưng khác tên là hai giá trị record khác nhau trong ví dụ, nên được giữ cả hai.
- Member có equals/hashCode dựa trên id. Hai Member cùng U1 là bằng nhau, nên lần thêm incoming trả false.

Điểm dễ bỏ sót: incoming có tên Linh không thay Member đầu tiên có tên Lan. Sau lần thêm trùng, tham chiếu đang giữ vẫn là first.

Nếu mục tiêu là nhận bản ghi mới nhất theo id, cần một chính sách cập nhật rõ ràng. Set.add không thực hiện thay thế dữ liệu đó.

Trong lớp Member:

- id là String không null, được giữ ổn định và là thành phần của equals/hashCode.
- displayName không tham gia nhận diện.
- Đổi displayName trong ví dụ không làm thay quy tắc hiện diện của Member.

Đây là một ví dụ cố ý cho thấy “phần tử có thể sửa” không đồng nghĩa mọi thay đổi đều phá Set. Vấn đề là **thay đổi có ảnh hưởng tới equals, hashCode hoặc quy tắc sắp xếp mà cấu trúc đang dựa vào hay không**.

Không lấy thiết kế Member này áp cho mọi loại thực thể. Cần chọn tiêu chí bằng nhau phù hợp với vòng đời và các tình huống sử dụng của kiểu dữ liệu thực tế.

## 8. Không thay dữ liệu nhận diện khi phần tử đang ở trong tập

### 8.1. Vì sao một đối tượng vẫn tồn tại nhưng có thể khó tìm lại?

Một HashSet đặt phần tử dựa trên thông tin băm lúc thêm. Nếu dữ liệu dùng để tính hashCode thay đổi, truy vấn sau có thể đi theo một đường khác với nơi phần tử đã được đặt.

Với TreeSet, thay trường dùng để so sánh có thể làm vị trí hiện tại trong cây không còn phù hợp thứ tự.

Hợp đồng Set cảnh báo hành vi không được xác định nếu thay giá trị phần tử theo cách ảnh hưởng equals trong lúc nó đang nằm trong tập. Các cấu trúc cụ thể còn phụ thuộc hashCode hoặc comparator tương ứng.

Không viết mã dựa vào một kết quả cố định như “sửa khóa thì contains chắc chắn trả false” hoặc “remove chắc chắn vẫn sửa được”. Hành vi sau khi phá tiền điều kiện không phải một bảo đảm để dùng tiếp.

### 8.2. final không tự làm mọi nội dung bất biến

Một trường final giữ tham chiếu không thể bị gán lại, nhưng đối tượng được tham chiếu có thể vẫn sửa được. Nếu hashCode hoặc equals đọc nội dung có thể thay đổi đó, dữ liệu nhận diện vẫn có thể thay đổi.

Cần nhìn các giá trị thực sự tham gia nhận diện, không chỉ nhìn từ khóa final trên một biến.

### 8.3. Cách tổ chức an toàn hơn

- Ưu tiên khóa giá trị ổn định hoặc bất biến.
- Nếu cần thay một giá trị nhận diện, xử lý như một thay đổi hiện diện: bỏ giá trị cũ khi nó còn hợp lệ, rồi thêm giá trị mới theo chính sách rõ ràng.
- Kiểm tra kết quả thêm giá trị mới vì nó có thể đã tồn tại trong tập.
- Nếu sửa trực tiếp một đối tượng có thể thay đổi, phải loại nó khỏi cấu trúc trước khi sửa phần nhận diện; không sửa trước rồi giả định remove sẽ khôi phục mọi thứ.
- Nếu dữ liệu đã bị làm sai, cần sửa mô hình và khôi phục từ dữ liệu đáng tin cậy; đừng coi một mẹo gọi contains/remove là biện pháp bảo đảm.

Set không sao chép sâu phần tử để bảo vệ khỏi thay đổi bên ngoài. Một biến khác giữ cùng tham chiếu có thể thay đối tượng, nên quyền sở hữu và khả năng sửa cũng là một phần thiết kế.

## 9. TreeSet và yêu cầu nhất quán giữa comparator với equals

### 9.1. So sánh bằng 0 có ý nghĩa quyết định trong TreeSet

TreeSet dùng thứ tự tự nhiên hoặc Comparator để nhận diện các phần tử tại vị trí trong cây. Hai giá trị được so sánh bằng 0 được TreeSet coi như cùng một phần tử.

Để thực hiện đúng hợp đồng Set chung dựa trên equals, quy tắc so sánh cần nhất quán với equals:

```text
compare(a, b) == 0
phải tương ứng với
a.equals(b) == true
trong miền phần tử đang dùng.
```
Nếu chỉ so độ dài String, cat và dog cùng độ dài nên comparator trả 0, nhưng String.equals trả false. TreeSet vẫn có hành vi xác định theo comparator đó, nhưng nó không còn tuân thủ đầy đủ hợp đồng Set chung.

Đây là một bẫy khi thiết kế quy tắc so sánh, không phải một định nghĩa mới rằng “Set tùy ý quyết định thế nào là bằng nhau” mà không cần quan tâm hợp đồng.

Tài liệu [TreeSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html) nêu rõ yêu cầu nhất quán và hệ quả khi thứ tự không nhất quán với equals.

### 9.2. Ví dụ chạy được về sự không nhất quán

Lưu vào `TreeSetEqualityPitfallDemo.java`:

```java
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class TreeSetEqualityPitfallDemo {
    public static void main(String[] args) {
        TreeSet<String> byLength = new TreeSet<>(
                Comparator.comparingInt(String::length));

        System.out.println("add cat = " + byLength.add("cat"));
        System.out.println("add dog = " + byLength.add("dog"));
        System.out.println("values by length = " + byLength);
        System.out.println("contains owl = " + byLength.contains("owl"));
        System.out.println("cat equals dog = " + "cat".equals("dog"));

        Set<String> ordinary = new HashSet<>(Set.of("dog"));
        System.out.println("byLength equals ordinary = " + byLength.equals(ordinary));
        System.out.println("ordinary equals byLength = " + ordinary.equals(byLength));

        Comparator<String> lengthThenText = Comparator
                .comparingInt(String::length)
                .thenComparing(Comparator.naturalOrder());
        TreeSet<String> consistent = new TreeSet<>(lengthThenText);
        consistent.add("cat");
        consistent.add("dog");
        System.out.println("consistent values = " + consistent);
        System.out.println("consistent contains owl = " + consistent.contains("owl"));
    }
}
```
Biên dịch và chạy:

```shell
javac --release 17 TreeSetEqualityPitfallDemo.java
java TreeSetEqualityPitfallDemo
```
Kết quả:

```text
add cat = true
add dog = false
values by length = [cat]
contains owl = true
cat equals dog = false
byLength equals ordinary = true
ordinary equals byLength = false
consistent values = [cat, dog]
consistent contains owl = false
```
Ở phần byLength:

- dog không được thêm sau cat vì comparator trả 0.
- contains("owl") trả true vì owl cũng dài ba ký tự, dù phần tử được giữ là cat.
- So với tập HashSet chứa dog, equals có thể trả khác nhau theo hai hướng như ví dụ.

Sự bất đối xứng này trái với hợp đồng equals thông thường. Nó cho thấy rủi ro khi đem một TreeSet có comparator không phù hợp tương tác như một Set chung.

Ở phần consistent, quy tắc so sánh là độ dài rồi đến nội dung chuỗi. Với String, việc so bằng 0 theo quy tắc này tương ứng cùng nội dung; cat và dog được giữ riêng, owl không được báo có chỉ vì cùng độ dài.

Nếu mục tiêu thực sự chỉ là tập các độ dài khác nhau, dùng Set<Integer> các độ dài có thể mô tả yêu cầu rõ hơn. Nếu mục tiêu giữ các chuỗi khác nhau nhưng sắp theo độ dài, cần cả quy tắc phân biệt khi độ dài bằng nhau.

Không thêm một quy tắc phụ tùy ý mà làm hai giá trị equals lại so khác 0. Tính nhất quán phải được kiểm tra theo cả mô hình bằng nhau và mô hình thứ tự.

## 10. Không trùng, thứ tự, khả năng sửa và null là các thuộc tính riêng

### 10.1. Không trùng không có nghĩa không có thứ tự

Set chung không quy định một thứ tự duyệt duy nhất. Một cách triển khai cụ thể có thể bổ sung bảo đảm.

| Cách tạo hoặc triển khai | Thứ tự duyệt cần hiểu | Khả năng sửa và null |
| --- | --- | --- |
| HashSet | Không bảo đảm thứ tự duyệt | Thông thường sửa được; cho phép một null |
| LinkedHashSet | Thứ tự thêm được bảo đảm theo hợp đồng lớp | Thông thường sửa được; cho phép một null |
| TreeSet | Theo thứ tự tự nhiên hoặc comparator | Sửa được; thứ tự tự nhiên không nhận null, comparator riêng phải hỗ trợ nếu muốn dùng null |
| Set.of / Set.copyOf | Không dựa vào một thứ tự duyệt được bảo đảm | Không sửa cấu trúc được; không nhận null |

[LinkedHashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html) bảo đảm thứ tự thêm. Điều đó không biến thứ tự thành thành phần của Set.equals.

Đừng dùng kết quả một lần duyệt HashSet hoặc Set.of để quyết định định dạng đầu ra cần ổn định. Nếu yêu cầu cần thứ tự, chọn cách biểu diễn có hợp đồng tương ứng.

### 10.2. Không trùng không có nghĩa mọi thao tác đều O(1)

Set là giao diện về hành vi. HashSet có các đặc điểm dựa trên băm; TreeSet có các đặc điểm dựa trên cấu trúc có thứ tự.

Ngay cả với cấu trúc băm, chi phí thực còn phụ thuộc hashCode, equals và phân bố băm. Không biến khẩu quyết “Set tìm nhanh” thành một bảo đảm độ phức tạp của mọi Set.

Bài tập trung vào hợp đồng. Việc chọn triển khai theo hiệu năng cần xem tài liệu lớp và mẫu thao tác thực tế.

### 10.3. Không sửa cấu trúc được khác với bất biến sâu

Một Set không cho add/remove vẫn có thể chứa các đối tượng có thể sửa. Đổi trường của phần tử có thể làm dữ liệu đọc được thay đổi; nếu trường đó tham gia nhận diện, còn có thể phá các giả định của cấu trúc.

Vì vậy, “unmodifiable Set” không có nghĩa mọi thứ đi tới qua phần tử đều bất biến.

Tương tự, một bản sao collection thường không tự là bản sao sâu của các phần tử. Cần xác định rõ tầng nào đang được bảo vệ.

## 11. Set.of khác Set.copyOf khi đầu vào có phần tử trùng

### 11.1. Set.of từ các đối số

Set.of tạo một tập không sửa được và từ chối phần tử trùng ngay lúc tạo bằng IllegalArgumentException. Nó cũng từ chối null.

Đây là hành vi khác với gọi add vào một HashSet có thể sửa: trường hợp thêm trùng bình thường trả false thay vì ném lỗi.

Không dùng một quy tắc “gặp trùng lúc nào cũng im lặng bỏ qua” cho mọi cách tạo Set.

### 11.2. Set.copyOf từ một Collection

Set.copyOf nhận collection nguồn, tạo một Set không sửa được với các giá trị phân biệt. Nếu nguồn có các phần tử bằng nhau, một đại diện bất kỳ trong nhóm trùng được giữ theo hợp đồng; không dựa vào việc luôn giữ bản đầu hoặc bản cuối.

Nếu nguồn thay đổi cấu trúc sau đó, Set trả về không phản ánh thay đổi đó. Nhưng điều này không tạo ra bản sao sâu của phần tử.

Set.copyOf có thể tái sử dụng một Set không sửa được thích hợp. Không dùng danh tính đối tượng mới/cũ làm ý nghĩa của phép copyOf.

### 11.3. Ví dụ chạy được

Lưu vào `SetFactoriesDemo.java`:

```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SetFactoriesDemo {
    public static void main(String[] args) {
        try {
            Set.of("A", "A");
        } catch (IllegalArgumentException ex) {
            System.out.println("Set.of duplicate = "
                    + ex.getClass().getSimpleName());
        }

        List<String> source = new ArrayList<>(List.of("A", "A", "B"));
        Set<String> snapshot = Set.copyOf(source);
        System.out.println("Set.copyOf distinct size = " + snapshot.size());

        source.add("C");
        System.out.println("copy contains later C = " + snapshot.contains("C"));

        try {
            snapshot.add("C");
        } catch (UnsupportedOperationException ex) {
            System.out.println("copy add = " + ex.getClass().getSimpleName());
        }

        try {
            Set.of("A", null);
        } catch (NullPointerException ex) {
            System.out.println("Set.of null = " + ex.getClass().getSimpleName());
        }

        Set<String> nullable = new LinkedHashSet<>();
        System.out.println("add first null = " + nullable.add(null));
        System.out.println("add second null = " + nullable.add(null));
        System.out.println("nullable size = " + nullable.size());
    }
}
```
Biên dịch và chạy:

```shell
javac --release 17 SetFactoriesDemo.java
java SetFactoriesDemo
```
Kết quả:

```text
Set.of duplicate = IllegalArgumentException
Set.copyOf distinct size = 2
copy contains later C = false
copy add = UnsupportedOperationException
Set.of null = NullPointerException
add first null = true
add second null = false
nullable size = 1
```
Giải thích:

- Set.of nhận A hai lần nên ném IllegalArgumentException.
- Set.copyOf từ List A, A, B tạo tập có hai giá trị phân biệt.
- Thêm C vào List nguồn sau đó không làm C xuất hiện trong bản Set trả về.
- Thử add vào bản Set không sửa được ném UnsupportedOperationException.
- Set.of từ chối null.
- LinkedHashSet cho thêm null lần đầu và không tạo lần hiện diện thứ hai.

Chương trình không in thứ tự của Set.copyOf vì không có yêu cầu thứ tự mà ta được phép dựa vào. Nó cũng không kiểm tra “đại diện đầu hay cuối” trong nhóm phần tử bằng nhau, vì hợp đồng không bảo đảm lựa chọn đó.

## 12. Những phép toán tập hợp thay đổi gì?

Các thao tác hàng loạt thường tương ứng với ý tưởng toán học, nhưng chúng là các lời gọi phương thức có hợp đồng sửa dữ liệu.

Giả sử A và B dùng quy tắc hiện diện nhất quán:

| Phương thức trên A | Ý nghĩa kết quả của A |
| --- | --- |
| A.addAll(B) | Hợp: thêm các giá trị của B chưa có trong A |
| A.retainAll(B) | Giao: giữ các giá trị thuộc cả A và B |
| A.removeAll(B) | Hiệu: bỏ khỏi A các giá trị có trong B |
| A.containsAll(B) | Chỉ hỏi B có là tập con của A không, không sửa A |

addAll, retainAll và removeAll sửa tập nhận lời gọi nếu chúng được hỗ trợ. Chúng không mặc nhiên tạo một tập kết quả mới.

Nếu muốn giữ A nguyên trạng, tạo một bản sao có thể sửa rồi thao tác trên bản sao đó. Cần chọn cách sao chép giữ đúng các yêu cầu thứ tự hoặc comparator nếu những yêu cầu đó là một phần mục tiêu.

containsAll không đếm số lần xuất hiện trong nguồn. Tập có A vẫn có thể containsAll một List gồm A, A; nó chỉ kiểm tra hiện diện của các giá trị được hỏi.

Các thao tác sửa hàng loạt cũng không phải giao dịch chung của mọi Set. Nếu một phần tử không hợp lệ hoặc một lời gọi bên trong gặp lỗi, không được tự giả định mọi thay đổi trước đó đều được hoàn tác. Nếu nghiệp vụ cần tất cả-hoặc-không, phải thiết kế cơ chế thích hợp.

Không nhầm phép hợp với nối dãy: hợp bỏ tính lặp theo quan hệ hiện diện, còn nối hai List có thể giữ đủ mọi lần xuất hiện.

## 13. Áp dụng Set đúng vào luồng xử lý

### 13.1. Dùng kết quả add để nhận diện lần đầu

Trong một lượt xử lý tuần tự, có thể dùng:

```java
// Đoạn minh họa: seen là Set<String> có thể sửa,
// id đã được chuẩn hóa theo yêu cầu của chương trình.
if (seen.add(id)) {
    processFirstOccurrence(id);
}
```
Điều này diễn đạt “chỉ xử lý khi id vừa xuất hiện lần đầu trong tập đang dùng”. Nó tránh phải tách thành hai lời gọi contains rồi add trong một luồng.

Nhưng Set không tự biến processFirstOccurrence thành một giao dịch. Nếu hàm xử lý lỗi sau khi id đã được thêm, lần thử lại sẽ cần một chính sách rõ ràng. Cũng không suy từ một lời gọi add rằng HashSet thường đã an toàn với nhiều luồng cùng thao tác.

Tập hiện diện trong bộ nhớ của một tiến trình không tự là ràng buộc duy nhất của cơ sở dữ liệu hoặc cơ chế chống trùng giữa nhiều hệ thống.

### 13.2. Phải quyết định có được bỏ trùng hay phải báo dữ liệu sai

Nếu nguồn không được phép có mã trùng, việc dựng Set rồi bỏ qua các lần thêm false có thể che mất lỗi dữ liệu.

Có thể kiểm tra kết quả add để báo phần tử trùng theo yêu cầu. Đây là một chính sách nghiệp vụ khác với “loại trùng và tiếp tục”.

Nếu muốn thống kê tần suất, đừng hủy dữ liệu lặp trước khi đếm. Nếu muốn giữ bản mới nhất theo khóa, đừng kỳ vọng add false cập nhật phần tử cũ.

### 13.3. Kiểm tra lớp phần tử bằng một đối tượng mới tương đương

Một bộ kiểm tra có ý nghĩa nên gồm:

- Hai đối tượng khác tham chiếu nhưng bằng nhau theo mô hình.
- Hai đối tượng phải khác nhau dù có một vài trường giống nhau.
- equals đối xứng và hashCode nhất quán cho các giá trị bằng nhau.
- add trùng trả false và không thay đối tượng đang giữ khi đó là yêu cầu.
- contains/remove bằng một đối tượng tương đương mới tạo.
- Chính sách null và thao tác không hỗ trợ.
- Tính nhất quán comparator/equals nếu dùng TreeSet.

Chỉ thử add rồi contains trên đúng cùng một tham chiếu có thể bỏ sót lỗi equals/hashCode. Chỉ nhìn size sau một ví dụ cũng chưa chứng minh tiêu chí duy nhất phù hợp nghiệp vụ.

## 14. Những nhầm lẫn phổ biến

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “Set loại các đối tượng có cùng địa chỉ” | Hợp đồng chung dựa trên quan hệ bằng nhau; hai tham chiếu khác vẫn có thể trùng |
| “Có cùng id thì Set tự loại” | Phải chọn mô hình equals/hashCode hoặc khóa thích hợp |
| “Có cùng hashCode là trùng” | Hash code có thể va chạm; không thay equals |
| “Override equals là đủ với HashSet” | Phải giữ cả hợp đồng hashCode |
| “add trùng sẽ ghi dữ liệu mới đè lên dữ liệu cũ” | Với hợp đồng add bình thường, tập không đổi và trả false |
| “Set không có thứ tự nên LinkedHashSet không phải Set đúng nghĩa” | Không trùng và thứ tự là hai thuộc tính độc lập |
| “Hai tập duyệt khác thứ tự thì không bằng nhau” | Set.equals xét thành viên, không xét thứ tự |
| “Set không sửa được thì phần tử không sửa được” | Không sửa cấu trúc không tạo bất biến sâu |
| “Set.of và Set.copyOf xử lý đầu vào trùng giống nhau” | Set.of từ chối đối số trùng; Set.copyOf lấy các giá trị phân biệt từ collection |
| “TreeSet chỉ đổi cách sắp xếp, không ảnh hưởng nhận diện” | compare bằng 0 quyết định hiện diện; cần nhất quán với equals để giữ hợp đồng chung |
| “Sửa khóa sau khi thêm rồi remove sẽ luôn khôi phục được” | Đã phá giả định nhận diện thì không có bảo đảm như vậy |
| “Set tự giải quyết mọi vấn đề trùng trong nhiều luồng hoặc nhiều hệ thống” | Cần cơ chế đồng thời, phạm vi dữ liệu và giao dịch phù hợp |

## 15. Thực hành xác định đúng hợp đồng

### Bài 1: Hai String khác tham chiếu

Thêm hai `new String("A")` vào HashSet bình thường. Vì sao chỉ có một giá trị phân biệt?

Vì String.equals so nội dung, hai đối tượng bằng nhau và có hashCode nhất quán. Danh tính khác nhau không làm chúng thành hai giá trị tập khác nhau.

### Bài 2: Hai record cùng id nhưng khác tên

Một record có hai thành phần id và name. Thêm hai giá trị có cùng id nhưng tên khác. Có chắc size bằng 1 không?

Không. Quan hệ tự sinh của record xét các thành phần record. Muốn duy nhất theo id, cần một mô hình khóa hoặc quan hệ bằng nhau phù hợp; không suy ra từ tên trường id.

### Bài 3: add false có cập nhật không?

Tập đã có Member U1 tên Lan. Thêm Member U1 tên Linh trong mô hình bằng nhau theo id.

add trả false và tập không thay phần tử cũ. Nếu cần tên mới, phải thực hiện một chính sách cập nhật riêng. Kiểm tra đúng đối tượng được giữ quan trọng hơn chỉ nhìn size vẫn là 1.

### Bài 4: Thứ tự và bằng nhau

Một LinkedHashSet thêm A rồi B; một tập khác thêm B rồi A. Nếu các quan hệ phần tử đúng, hai tập có bằng nhau không?

Có. Chúng có cùng thành viên. Thứ tự duyệt có thể khác nhưng equals và hashCode của Set phải vẫn phù hợp.

### Bài 5: Hash code trùng

Hai Set có cùng hashCode có chắc bằng nhau không?

Không. Hợp đồng chỉ bắt buộc các tập bằng nhau có cùng hashCode; va chạm không bị cấm. Dùng equals để xét giá trị tập.

### Bài 6: Quy tắc độ dài trong TreeSet

TreeSet<String> chỉ so độ dài đã chứa cat. Vì sao thêm dog có thể không tăng size, và điều này đặt ra vấn đề gì?

Comparator coi hai chuỗi cùng độ dài là tương đương trong cây. Nhưng String.equals không coi chúng bằng nhau, nên quy tắc đó không đáp ứng đầy đủ hợp đồng Set chung. Cần điều chỉnh cách biểu diễn yêu cầu hoặc quy tắc so sánh.

### Bài 7: Tập không sửa được chứa Member có thể đổi tên

Không gọi được add/remove có bảo đảm tên Member giữ nguyên không?

Không. Nếu bên khác giữ cùng Member và sửa tên, dữ liệu đọc được có thể đổi. Nếu sửa trường tham gia nhận diện thì còn có rủi ro làm hỏng giả định của cấu trúc.

### Bài 8: Loại trùng và kiểm tra trùng khác nhau thế nào?

Tự động bỏ mọi phần tử trùng là một cách xử lý dữ liệu. Báo lỗi khi thấy phần tử trùng là một yêu cầu khác. Hãy quyết định chính sách trước, rồi sử dụng kết quả add hoặc cấu trúc thích hợp để thực hiện.

Một thiết kế Set rõ ràng phải nêu được **tiêu chí bằng nhau, ý nghĩa hiện diện, chính sách khi gặp trùng, khả năng sửa, thứ tự và vòng đời dữ liệu nhận diện**. Khi những phần này đúng, thuộc tính “không trùng lặp” mới phản ánh đúng điều chương trình muốn biểu diễn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="set-002">Kết quả add khi phần tử đã tồn tại</a>

<details>
<summary>Xem chi tiết</summary>

# Set.add: kết quả khi phần tử đã tồn tại

Với một `Set` hỗ trợ thêm và một phần tử hợp lệ, `add(element)` trả về:

- `true` nếu phần tử chưa tồn tại và được thêm vào tập hợp.
- `false` nếu phần tử đã tồn tại; tập hợp được giữ nguyên.

`false` trong trường hợp này là một kết quả bình thường, không phải ngoại lệ và không có nghĩa là phần tử đang vắng mặt. Ngược lại, nó cho biết tập hợp đã có phần tử tương đương nên không cần thêm một bản trùng.

Điểm quan trọng là hiểu “đã tồn tại” được xác định thế nào, tập hợp có quyền sửa hay không, và mã gọi nên dùng giá trị boolean đó vào việc gì. Các ví dụ dưới đây dùng Java 17 trở lên.

## 1. Vì sao add cần trả boolean?

Một tập hợp biểu diễn tư cách thành viên, không phải số lần xuất hiện. Nếu A đã thuộc tập hợp, thêm A lần nữa vẫn chỉ có A.

~~~text
truoc: {A, B}

add(C) -> true
sau:   {A, B, C}

add(A) -> false
sau:   {A, B, C}
~~~

Boolean của `add` giúp người gọi biết **lần gọi này có làm thay đổi tập hợp bằng việc bổ sung một thành viên mới không**. Nó không trả về tổng số phần tử, không trả về phần tử đã có và không đếm số lần A được gửi tới phương thức.

Theo [hợp đồng Set.add trong Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#add(E)), nếu đã có phần tử phù hợp thì lời gọi giữ nguyên tập hợp và trả `false`. Phép thêm là một thao tác tùy chọn; một số set không hỗ trợ thay đổi.

### 1.1. Bảng kết quả trong trường hợp thêm hợp lệ

| Trạng thái trước gọi | Kết quả | Tập hợp sau gọi |
|---|---|---|
| Chưa có phần tử tương đương | `true` | Có thêm một thành viên |
| Đã có phần tử tương đương | `false` | Giữ nguyên các thành viên |
| Không hỗ trợ thêm hoặc phần tử không hợp lệ | Có thể ném ngoại lệ | Không được suy ra một kết quả boolean bình thường |

Hai hàng đầu giả định lớp triển khai hỗ trợ `add`, phần tử được chấp nhận và quy tắc so sánh của dữ liệu đúng hợp đồng.

### 1.2. False không phải “thêm bị lỗi”

Nếu tập hợp đang chứa `"java"`, lời gọi `add("java")` trả `false` là hành vi đúng: yêu cầu “bảo đảm phần tử có mặt” đã được đáp ứng từ trước.

Không nên ném lỗi hệ thống chỉ vì kết quả là `false`, trừ khi hợp đồng nghiệp vụ thực sự cấm đầu vào trùng và muốn báo trường hợp đó cho người dùng.

Ví dụ:

- Thu thập các thẻ duy nhất: gặp thẻ cũ thường chỉ cần bỏ qua.
- Kiểm tra một danh sách đăng ký bắt buộc không trùng: kết quả `false` có thể được dùng để báo lỗi dữ liệu.
- Đếm tổng số lần nhận dữ liệu: chỉ dùng set là chưa đủ, vì lần trùng không được lưu thêm.

### 1.3. True chỉ nói về trạng thái hiện tại

Nếu thêm A, xóa A rồi thêm A lại, lần thêm cuối có thể trả `true`: lúc đó A không còn trong tập hợp.

Do đó, `true` không có nghĩa “đây là lần đầu tiên trong toàn bộ lịch sử chương trình từng gặp A”. Nó phản ánh trạng thái tại thời điểm thao tác.

Muốn ghi nhớ một lịch sử đã thấy, ứng dụng phải giữ tập hợp đó trong khoảng thời gian cần thiết và không xóa các thành viên liên quan. Một tập hợp cục bộ mới tạo cho mỗi lô chỉ ghi nhận trong phạm vi lô đó.

## 2. “Đã tồn tại” không đồng nghĩa cùng tham chiếu

### 2.1. Hai đối tượng khác nhau vẫn có thể là một thành viên theo equals

Đoạn thân phương thức minh họa:

~~~java
Set<String> tags = new HashSet<>();

String first = new String("java");
String second = new String("java");

boolean addedFirst = tags.add(first);   // true
boolean addedSecond = tags.add(second); // false
~~~

`first == second` là false vì đây là hai đối tượng khác nhau. Nhưng `first.equals(second)` là true vì nội dung chuỗi bằng nhau.

Với các set dùng cơ chế băm thông thường như `HashSet`, các đối tượng bằng nhau theo hợp đồng `equals/hashCode` không được giữ thành hai thành viên riêng chỉ vì khác địa chỉ đối tượng.

Nếu một lớp không ghi đè `equals`, nó có thể vẫn dùng cách so sánh danh tính kế thừa từ `Object`. Khi ấy hai đối tượng có các trường nhìn giống nhau chưa chắc được coi là bằng nhau. Set không tự đoán quy tắc “trùng về nghiệp vụ”.

### 2.2. hashCode bằng nhau chưa đủ để kết luận trùng

`HashSet` dùng mã băm để hỗ trợ tìm kiếm, nhưng các đối tượng khác nhau có thể có cùng mã băm. Đó là va chạm hash.

Ví dụ hai chuỗi `"Aa"` và `"BB"` có cùng mã băm Java nhưng không bằng nhau theo `equals`. Cả hai vẫn có thể được thêm vào cùng một `HashSet`, và hai lần thêm đều trả `true` khi tập hợp ban đầu chưa có chúng.

Ngược lại, nếu hai đối tượng bằng nhau theo `equals`, chúng phải có cùng `hashCode`. Đây là yêu cầu của [hợp đồng Object.hashCode trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#hashCode()).

Có thể ghi nhớ:

~~~text
equals true  -> hashCode phai bang nhau
hashCode bang nhau -> chua du de ket luan equals true
~~~

Không triển khai `equals` đúng rồi bỏ quên `hashCode` khi dùng đối tượng làm phần tử của set băm.

### 2.3. Kết quả phụ thuộc quy tắc bằng nhau mà lớp đã định nghĩa

Nếu khách hàng được xem là cùng một thực thể khi có cùng mã, lớp dữ liệu có thể định nghĩa `equals/hashCode` theo mã đó.

Hai đối tượng:

~~~text
Customer(id="C1", name="An")
Customer(id="C1", name="Binh")
~~~

có thể được coi là bằng nhau dù tên khác nhau. Trong một `HashSet` dùng quy tắc ấy, thêm đối tượng thứ hai sau đối tượng thứ nhất trả `false`.

Nếu mục đích thực tế là giữ mọi phiên bản tên hoặc giữ nhiều bản ghi lịch sử, định nghĩa bằng nhau theo mã duy nhất có thể không phù hợp với dữ liệu cần lưu. Trước khi kết luận set “làm mất dữ liệu”, cần xác định đúng quy tắc thành viên.

### 2.4. Không sửa trường tham gia equals/hashCode khi đối tượng đang nằm trong set

Nếu một đối tượng đã được thêm vào `HashSet` rồi bị đổi mã định danh dùng trong `equals/hashCode`, cấu trúc có thể không còn tìm và quản lý nó theo cách ứng dụng mong đợi.

Tài liệu `Set` không quy định hành vi khi phần tử bị thay đổi theo cách ảnh hưởng so sánh bằng nhau trong lúc vẫn là thành viên. Không dùng các kết quả bất thường sau đó để suy ra rằng hợp đồng “thêm trùng trả false” đã bị thay đổi.

Cách đơn giản là dùng khóa/giá trị ổn định cho tiêu chí thành viên. Nếu cần thay tiêu chí đó, hãy thiết kế quá trình loại bỏ và thêm lại phù hợp; không chỉ sửa trường ngay trong phần tử đang được dùng làm khóa thành viên.

## 3. Thêm trùng không phải cập nhật đối tượng đã có

Giả sử đã có một `Customer` tên An với mã C1. Sau đó gọi `add` với một đối tượng mới cùng mã C1 nhưng tên Binh.

Nếu `HashSet` coi hai đối tượng bằng nhau theo mã, lần gọi thứ hai trả `false`. Nó không có ý nghĩa “thay khách hàng cũ bằng khách hàng mới”.

Trong ví dụ chạy bên dưới, đối tượng gốc vẫn là đối tượng được giữ; tên vẫn là An. `contains` với đối tượng tên Binh lại trả true vì nó đang kiểm tra thành viên theo mã, không kiểm tra xem chính tham chiếu mới có được lưu hay không.

Ba câu hỏi khác nhau:

| Câu hỏi | Điều cần kiểm tra |
|---|---|
| Có một thành viên tương đương không? | `contains` theo quy tắc của set |
| Lần add này có thêm thành viên mới không? | Giá trị boolean của `add` |
| Dữ liệu của đối tượng cũ có được thay bằng dữ liệu mới không? | Cần một hợp đồng cập nhật riêng; `add` không tự thực hiện việc đó |

Nếu yêu cầu là “theo mã C1, lưu dữ liệu mới nhất”, một `Map<Id, Customer>` có thể diễn đạt việc ánh xạ/cập nhật rõ hơn. Không dùng `Set.add` như một lệnh upsert rồi bỏ qua việc nó trả `false`.

Nếu tự chọn cách xóa rồi thêm để thay phần tử, phải xét ai đang giữ dữ liệu và các yêu cầu về đồng thời, lỗi và thứ tự. Không coi hai lời gọi rời là một thao tác cập nhật nguyên tử.

## 4. Dùng kết quả add để điều khiển luồng xử lý

### 4.1. Chỉ thực hiện một nhánh khi gặp thành viên mới

Mẫu thường dùng:

~~~java
if (seen.add(id)) {
    firstSeen.add(id);
} else {
    duplicates.add(id);
}
~~~

Các tên trong đoạn minh họa biểu diễn một tập `seen` có thể sửa và hai danh sách kết quả. Với đầu vào:

~~~text
e1, e2, e1, e3, e2
~~~

ta nhận:

~~~text
firstSeen:  e1, e2, e3
duplicates: e1, e2
~~~

Danh sách `firstSeen` giữ thứ tự xuất hiện đầu tiên vì ứng dụng thêm vào danh sách theo vòng duyệt đầu vào. Không được suy ra `HashSet` tự bảo đảm thứ tự duyệt đó; nó không có bảo đảm thứ tự như vậy.

Kết quả boolean đã trả lời phần tử có vừa được thêm không, nên trong mẫu này thường không cần một lần `contains` riêng trước `add`.

### 4.2. Contains rồi add không làm điều kiện trở nên nguyên tử

Mẫu hai bước:

~~~java
if (!seen.contains(id)) {
    seen.add(id);
    firstSeen.add(id);
}
~~~

có thể thực hiện hai lần tìm kiếm trong sử dụng thông thường. Nếu có nhiều luồng cùng truy cập, hai bước còn có thể quan sát trạng thái xen kẽ.

Điều đó không có nghĩa đổi sang `HashSet.add` rồi mặc nhiên an toàn đa luồng. `HashSet` thông thường không tự đồng bộ; hợp đồng truy cập đồng thời cần được thiết kế riêng. Tài liệu [HashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html) mô tả cả đặc điểm băm và yêu cầu đồng bộ khi có sửa đồng thời.

Giá trị trả về của một thao tác collection và tính nguyên tử của cả quy trình nghiệp vụ là những vấn đề khác nhau.

### 4.3. Không coi nhánh add thành công là bảo đảm xử lý đúng một lần bên ngoài

Nếu đã thêm id vào `seen` rồi bước xử lý sau đó ném lỗi, id vẫn có thể đang được đánh dấu là đã thấy. Lần thử lại có thể nhận `false` và bỏ qua việc cần làm.

Trong các ví dụ học tập, nhánh chỉ đưa dữ liệu vào các danh sách cục bộ. Nếu dùng cho quy trình có tác động bên ngoài hoặc lưu trữ lâu dài, phải thiết kế rõ trạng thái đã nhận, đang xử lý, thành công và thử lại theo yêu cầu.

Set trong bộ nhớ không tự cung cấp một giao dịch với cơ sở dữ liệu, cũng không tự làm lịch sử sống qua lần khởi động lại. Đây là giới hạn của cách áp dụng boolean, không phải một lý do để đổi nghĩa `false`.

## 5. addAll trả “có thay đổi không”, không trả số phần tử mới

`addAll(collection)` bổ sung các thành viên chưa có và trả:

- `true` nếu có ít nhất một thành viên mới được thêm.
- `false` nếu không có thành viên mới nào.

Ví dụ bắt đầu từ `{A, B}`:

| Lời gọi | Kết quả | Thành viên sau gọi |
|---|---|---|
| `addAll([A, B, A])` | `false` | `{A, B}` |
| `addAll([B, C, C])` | `true` | `{A, B, C}` |

Lô thứ hai có ba phần tử đầu vào nhưng chỉ bổ sung một thành viên C. Boolean không cho biết con số 1.

Nếu cần biết chính xác những phần tử nào vừa được thêm, có thể duyệt từng phần tử và ghi nhận các lần `add` trả `true`. Nếu chỉ cần tổng số mới trong một trạng thái không có sửa xen vào, ứng dụng có thể tính theo quá trình thích hợp; đừng ép một boolean thành một bộ đếm.

### 5.1. Cẩn thận khi gộp boolean với thao tác có tác động

Mã này có lỗi nếu mục đích là thử thêm mọi phần tử:

~~~java
boolean changed = false;
for (String value : input) {
    changed = changed || values.add(value);
}
~~~

Toán tử `||` dừng đánh giá khi vế trái đã true. Sau lần đầu thêm thành công, các lần `add` tiếp theo có thể không được gọi nữa.

Một cách gộp có chủ đích là:

~~~java
boolean changed = false;
for (String value : input) {
    changed |= values.add(value);
}
~~~

Với toán hạng boolean, `|` không ngắn mạch, nên mỗi lần `add` vẫn được đánh giá. Nếu chỉ cần bổ sung một lô và biết có thay đổi hay không, dùng `addAll` thường diễn đạt mục tiêu trực tiếp hơn.

Ví dụ hoàn chỉnh kiểm tra cả hai vòng lặp: vòng dùng `||` chỉ giữ được một thành viên, vòng dùng `|=` giữ đủ ba thành viên khác nhau.

## 6. Các trường hợp phải phân biệt với kết quả false bình thường

### 6.1. Set không cho sửa có thể ném ngoại lệ dù phần tử đã tồn tại

`Set.of("A")` tạo một set không sửa được. Gọi `add("A")` trên nó ném `UnsupportedOperationException`; không được mong sẽ trả `false` vì A đã có.

Một hợp đồng có thể từ chối thao tác sửa ngay từ đầu. Do đó, quy tắc “phần tử cũ → false” phải được đọc cùng điều kiện thao tác thêm được hỗ trợ.

Khai báo biến là `Set<String>` không cho biết chắc đối tượng thật là set có thể sửa. Nếu một phương thức cần thêm, hợp đồng của nó phải yêu cầu nguồn có quyền sửa hoặc tự tạo một tập hợp làm việc.

### 6.2. Đầu vào trùng khi gọi Set.of là một phép tạo khác

`Set.of("A", "A")` ném `IllegalArgumentException` vì factory này không nhận các phần tử trùng khi tạo.

Điều đó không mâu thuẫn với `HashSet.add("A")` lần thứ hai trả `false`. Một bên là hợp đồng factory tạo tập hợp; một bên là thao tác bổ sung vào một tập hợp đã tồn tại và có thể sửa.

Không suy rộng rằng mọi constructor/factory của mọi set đều xử lý đầu vào trùng giống `Set.of`; phải đọc hợp đồng của cách tạo cụ thể.

### 6.3. Null phụ thuộc lớp triển khai

Một `HashSet` cho phép một thành viên `null`:

~~~text
add(null) lan dau -> true
add(null) lan sau -> false
so thanh vien null van la 1
~~~

Nhưng không phải mọi set đều nhận `null`. Ví dụ `TreeSet` dùng thứ tự tự nhiên cho chuỗi từ chối `null` bằng `NullPointerException`.

Khi phần tử không hợp lệ, lời gọi có thể ném ngoại lệ thay vì trả `false`. Không bắt mọi ngoại lệ rồi quy thành “đã tồn tại”, vì sẽ che khuất vấn đề quyền sửa, kiểu dữ liệu hoặc chính sách `null`.

### 6.4. TreeSet xác định tương đương bằng phép so sánh thứ tự

`TreeSet` dùng `compareTo` hoặc `Comparator` để tổ chức phần tử. Hai phần tử có kết quả so sánh bằng 0 được coi là cùng vị trí thành viên của cây.

Ví dụ với `String.CASE_INSENSITIVE_ORDER`:

~~~text
add("Java") -> true
add("java") -> false
~~~

trong khi `"Java".equals("java")` là false.

Đây là một điểm phải giải thích cẩn thận: thứ tự của `TreeSet` cần nhất quán với `equals` để tuân thủ đầy đủ hợp đồng chung của `Set`. Ví dụ so sánh không phân biệt hoa/thường ở đây cố ý cho thấy sự khác biệt; không nên dùng nó để khẳng định mọi set định nghĩa tính trùng tùy ý mà không ảnh hưởng hợp đồng.

[Tài liệu TreeSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html) mô tả rõ việc so sánh theo thứ tự và yêu cầu nhất quán đó. Trong thiết kế thực tế, hãy chọn kiểu khóa, quy tắc chuẩn hóa và phép so sánh phù hợp với cùng một định nghĩa thành viên.

## 7. Chương trình hoàn chỉnh: kết quả boolean và trạng thái thật

Chương trình dùng các set thật để kiểm tra:

- Đối tượng khác tham chiếu nhưng bằng nhau.
- Va chạm hash mà không bằng nhau.
- Đối tượng nghiệp vụ bằng nhau theo mã nhưng có dữ liệu khác.
- Ghi nhận lần đầu/trùng.
- Kết quả `addAll`, `null`, set không sửa được và `TreeSet`.
- Bẫy toán tử ngắn mạch khi vẫn cần gọi mọi lần `add`.

Không in thứ tự duyệt của một `HashSet` nhiều phần tử để làm kết quả kỳ vọng. Thứ tự trong hai danh sách kết quả đến từ đầu vào; ví dụ `TreeSet` chỉ giữ một phần tử.

### 7.1. Mã nguồn

Lưu thành `SetAddResultDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class SetAddResultDemo {
    static final class Customer {
        private final String id;
        private final String name;

        Customer(String id, String name) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Customer customer && id.equals(customer.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    static void addTag(Set<String> tags, String value) {
        int before = tags.size();
        boolean added = tags.add(value);
        System.out.printf("add(%s): returned=%s, before=%d, after=%d%n",
                value, added, before, tags.size());
    }

    public static void main(String[] args) {
        Set<String> tags = new HashSet<>();
        String firstJava = new String("java");
        String equalJava = new String("java");
        System.out.printf("strings: sameReference=%s, equals=%s%n",
                firstJava == equalJava, firstJava.equals(equalJava));
        addTag(tags, firstJava);
        addTag(tags, equalJava);
        addTag(tags, "spring");
        System.out.println("remove java: " + tags.remove("java"));
        addTag(tags, "java");

        String left = "Aa";
        String right = "BB";
        Set<String> collision = new HashSet<>();
        System.out.printf("collision: sameHash=%s, equals=%s%n",
                left.hashCode() == right.hashCode(), left.equals(right));
        System.out.printf("collision adds: first=%s, second=%s, size=%d%n",
                collision.add(left), collision.add(right), collision.size());

        Customer original = new Customer("C1", "An");
        Customer equalId = new Customer("C1", "Binh");
        Set<Customer> customers = new HashSet<>();
        System.out.println("customer first add: " + customers.add(original));
        System.out.println("customer equal-id add: " + customers.add(equalId));
        Customer stored = customers.iterator().next();
        System.out.printf("customer retained: sameReference=%s, name=%s, containsOther=%s%n",
                stored == original, stored.name, customers.contains(equalId));

        Set<String> seen = new HashSet<>();
        List<String> firstSeen = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        for (String id : List.of("e1", "e2", "e1", "e3", "e2")) {
            if (seen.add(id)) {
                firstSeen.add(id);
            } else {
                duplicates.add(id);
            }
        }
        System.out.println("first seen: " + firstSeen);
        System.out.println("duplicates: " + duplicates);

        Set<String> bulk = new HashSet<>(List.of("A", "B"));
        System.out.printf("addAll existing: returned=%s, size=%d%n",
                bulk.addAll(List.of("A", "B", "A")), bulk.size());
        System.out.printf("addAll mixed: returned=%s, size=%d%n",
                bulk.addAll(List.of("B", "C", "C")), bulk.size());

        Set<String> nullable = new HashSet<>();
        System.out.printf("null adds: first=%s, second=%s, size=%d%n",
                nullable.add(null), nullable.add(null), nullable.size());

        Set<String> insensitive = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        System.out.println("case-insensitive first: " + insensitive.add("Java"));
        System.out.println("case-insensitive second: " + insensitive.add("java"));
        System.out.printf("case-insensitive: equals=%s, values=%s%n",
                "Java".equals("java"), insensitive);

        Set<String> fixed = Set.of("A");
        try {
            fixed.add("A");
        } catch (UnsupportedOperationException exception) {
            System.out.println("Set.of add existing: UnsupportedOperationException");
        }
        try {
            Set.of("A", "A");
        } catch (IllegalArgumentException exception) {
            System.out.println("Set.of duplicate input: IllegalArgumentException");
        }
        try {
            new TreeSet<String>().add(null);
        } catch (NullPointerException exception) {
            System.out.println("natural TreeSet null: NullPointerException");
        }

        Set<String> skipped = new HashSet<>();
        boolean anySkipped = false;
        for (String value : List.of("A", "B", "C")) {
            anySkipped = anySkipped || skipped.add(value);
        }
        Set<String> complete = new HashSet<>();
        boolean anyComplete = false;
        for (String value : List.of("A", "B", "C")) {
            anyComplete |= complete.add(value);
        }
        System.out.printf("short-circuit: changed=%s, size=%d%n",
                anySkipped, skipped.size());
        System.out.printf("non-short-circuit: changed=%s, size=%d%n",
                anyComplete, complete.size());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 SetAddResultDemo.java
java SetAddResultDemo
~~~

### 7.2. Kết quả stdout

~~~text
strings: sameReference=false, equals=true
add(java): returned=true, before=0, after=1
add(java): returned=false, before=1, after=1
add(spring): returned=true, before=1, after=2
remove java: true
add(java): returned=true, before=1, after=2
collision: sameHash=true, equals=false
collision adds: first=true, second=true, size=2
customer first add: true
customer equal-id add: false
customer retained: sameReference=true, name=An, containsOther=true
first seen: [e1, e2, e3]
duplicates: [e1, e2]
addAll existing: returned=false, size=2
addAll mixed: returned=true, size=3
null adds: first=true, second=false, size=1
case-insensitive first: true
case-insensitive second: false
case-insensitive: equals=false, values=[Java]
Set.of add existing: UnsupportedOperationException
Set.of duplicate input: IllegalArgumentException
natural TreeSet null: NullPointerException
short-circuit: changed=true, size=1
non-short-circuit: changed=true, size=3
~~~

### 7.3. Đối chiếu kết quả với hợp đồng

Hai chuỗi java khác tham chiếu nhưng bằng nhau về nội dung: lần đầu true, lần sau false. Kích thước không tăng ở lần trùng. Sau khi xóa java, thêm lại trả true, cho thấy giá trị boolean nói về trạng thái hiện tại.

Hai chuỗi Aa và BB có hash bằng nhau nhưng equals false: cả hai được thêm, kích thước là 2. Một va chạm hash không tự tạo ra quan hệ trùng.

Lớp `Customer` chủ động định nghĩa bằng nhau theo id và dùng cùng id để tính hash. Các trường là `final`, nên tiêu chí thành viên không bị đổi sau khi thêm.

Đối tượng tên Binh cùng mã C1 không được thêm thành một thành viên khác. `sameReference=true` chứng minh `HashSet` trong ví dụ vẫn giữ đối tượng gốc; `containsOther=true` kiểm tra sự tương đương theo mã, không chứng minh tham chiếu Binh đang được lưu.

Hai danh sách `first seen` và `duplicates` phản ánh kết quả từng lần `seen.add`. Ta không duyệt `HashSet` để tạo thứ tự đó.

`addAll existing` false không phải lỗi; tất cả phần tử yêu cầu đã có. Lô mixed trả true vì C mới được thêm, không phải vì có ba đầu vào.

Nhóm cuối tách rõ ba kết quả: false khi thêm trùng hợp lệ, ngoại lệ khi thao tác không được hỗ trợ hoặc đầu vào không hợp lệ, và không gọi thao tác vì biểu thức ngắn mạch. Ba tình huống này có thể đều làm số thành viên không tăng, nhưng nguyên nhân và cách xử lý hoàn toàn khác nhau.

### 7.4. Phạm vi của minh chứng

Chương trình chạy trong một luồng với dữ liệu ổn định. Nó kiểm chứng kết quả và nội dung quan sát được, không đo hiệu năng hoặc tính đúng của một quy trình xử lý đồng thời.

`HashSet.add` trả false vẫn có thể phải tính hash và so sánh phần tử. Không thêm mới không đồng nghĩa không làm công việc. Chi phí còn phụ thuộc triển khai set và các phương thức của đối tượng phần tử.

## 8. Những lỗi suy luận thường gặp

| Hiểu nhầm | Cách hiểu đúng |
|---|---|
| false nghĩa là phần tử không có trong set | Với phép thêm trùng hợp lệ, false vì phần tử tương đương đã có |
| false là một ngoại lệ cần bắt | Nó là một giá trị boolean bình thường |
| add trả tổng số phần tử | Nó trả có bổ sung thành viên mới không |
| Khác tham chiếu thì luôn được thêm | Quy tắc bằng nhau có thể coi chúng là một thành viên |
| Trùng hash thì add trả false | Vẫn cần xét bằng nhau; va chạm không đủ |
| equals đúng thì không cần hashCode | Các set băm cần hai phương thức đúng hợp đồng |
| add một đối tượng tương đương sẽ cập nhật dữ liệu cũ | Thêm trùng không phải lệnh thay thế hoặc upsert |
| contains(obj) true nghĩa là đúng tham chiếu obj đang được giữ | Có thể là một đối tượng khác tương đương |
| Set luôn trả false khi đã có phần tử | Set không cho sửa có thể ném ngoại lệ cho add |
| Mọi Set đều cho một null | Chính sách null tùy triển khai |
| TreeSet luôn dùng equals để nhận biết vị trí trùng | Nó dùng phép so sánh thứ tự; cần nhất quán với equals |
| addAll true nghĩa là mọi đầu vào đều mới | Chỉ cần ít nhất một thành viên mới |
| changed || add luôn gọi add | Khi changed đã true, vế phải có thể bị bỏ qua |
| seen.add true bảo đảm cả quy trình đã xử lý đúng một lần | Đó chỉ là kết quả thao tác trên set trong trạng thái đang xét |

## 9. Bài tập tự kiểm tra

### Bài 1: theo dõi trạng thái

Tập hợp ban đầu rỗng, lần lượt thêm A, B, A, C, B. Dãy boolean là gì và cuối cùng có bao nhiêu thành viên?

**Giải thích:** true, true, false, true, false; cuối cùng có 3 thành viên khác nhau. Năm lần gọi không có nghĩa là năm thành viên.

### Bài 2: xóa rồi thêm lại

A đã có trong set, sau đó `remove("A")` thành công rồi `add("A")` được gọi. Lần thêm cuối trả gì?

**Giải thích:** true trong điều kiện bình thường của một set có thể sửa, vì A không còn ở trạng thái ngay trước lần thêm đó. Kết quả không mô tả toàn bộ lịch sử.

### Bài 3: dữ liệu mới của cùng thực thể

Hai đối tượng khác tên nhưng `equals/hashCode` cùng dựa trên một id. Sau khi thêm đối tượng đầu, thêm đối tượng sau có tự thay tên không?

**Giải thích:** Không. Trong mô hình `HashSet` của bài, lần thêm sau trả false và không phải một lệnh cập nhật đối tượng đã giữ. Cần thiết kế thao tác cập nhật riêng nếu đó là mục tiêu.

### Bài 4: va chạm hash

Hai phần tử có cùng `hashCode` nhưng `equals` trả false. Có bắt buộc lần thêm thứ hai trả false không?

**Giải thích:** Không. Với hợp đồng đúng và đầu vào hợp lệ, hai giá trị khác nhau vẫn có thể cùng là thành viên. Hash giúp tổ chức tìm kiếm, không thay thế tiêu chí bằng nhau.

### Bài 5: quyền sửa

`Set<String> values = Set.of("A");` rồi gọi `values.add("A")`. Có thể dùng nhánh `if (!add(...))` để bỏ qua phần tử trùng không?

**Giải thích:** Không theo giả định đó, vì lời gọi ném `UnsupportedOperationException` trước khi có một kết quả boolean bình thường. Phải biết đối tượng có hỗ trợ thêm hay không.

### Bài 6: lô có trùng

Set đã có A. Gọi `addAll` với A, B, B. Boolean trả về là gì, có bao nhiêu thành viên mới?

**Giải thích:** true; chỉ có một thành viên mới là B. Kết quả không phải số lượng phần tử đầu vào hoặc số lần phương thức thử thêm.

### Bài 7: ngắn mạch

Vì sao `changed = changed || set.add(x)` có thể làm thiếu dữ liệu trong một vòng lặp?

**Giải thích:** Khi changed đã true, toán tử OR ngắn mạch không đánh giá vế phải. Những lời gọi add sau đó không xảy ra. Nếu cần thêm toàn bộ lô, dùng cách đánh giá mọi thao tác hoặc dùng addAll.

Đọc đúng `Set.add` là đọc một câu hỏi về **sự thay đổi thành viên tại lần gọi đó**. Khi tách rõ kết quả boolean, tiêu chí tương đương, quyền sửa và mục đích nghiệp vụ, ta có thể dùng `true/false` để phát hiện trùng mà không nhầm với lỗi, cập nhật dữ liệu hay bảo đảm xử lý toàn quy trình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="set-003">Kiểm tra thuộc và xóa phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra thuộc và xóa phần tử trong Set

## 1. Hai câu hỏi khác nhau: có phần tử hay không, và có xóa được hay không?

`Set<E>` biểu diễn một tập hợp các phần tử không trùng nhau theo quy tắc xác định phần tử của tập. Khi làm việc với tập thẻ, mã công việc hoặc mã người dùng, ta thường cần trả lời hai câu hỏi:

- Một giá trị đã thuộc tập hay chưa? Dùng `contains`.
- Sau khi yêu cầu xóa một giá trị, tập có thực sự thay đổi không? Dùng `remove`.

Ví dụ, một tập đang chứa `"java"` và `"sql"`. Gọi `contains("java")` trả lời `true` nhưng vẫn giữ cả hai phần tử. Gọi `remove("java")` trên một tập cho phép sửa đổi sẽ xóa phần tử đó và trả về `true`. Gọi lại `remove("java")` trả về `false`, vì lúc này không còn phần tử phù hợp để xóa.

Điểm cần hiểu trước khi đọc mã là **kiểm tra thuộc không phải tìm vị trí**, còn **xóa trả về một cờ thay đổi, không trả về phần tử bị xóa**. `Set` không cung cấp phép truy cập theo chỉ số như `List`.

Có triển khai giữ thứ tự, chẳng hạn `LinkedHashSet`, nhưng thứ tự duyệt không biến tập thành danh sách. Với `Set<Integer>`, `remove(1)` có nghĩa là xóa phần tử có giá trị `Integer` bằng `1`; nó không có nghĩa là xóa phần tử ở vị trí số 1. [Đặc tả Set trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

Sau bài này, người học cần tự giải thích được:

1. `contains` và `remove` nhận đối số nào, trả về gì, có làm thay đổi tập không.
2. Vì sao một đối tượng mới tạo vẫn có thể tìm và xóa đối tượng đang lưu.
3. Vì sao `HashSet` và một `TreeSet` có bộ so sánh riêng có thể cho kết quả khác nhau.
4. Cách xóa một phần tử, nhiều phần tử hoặc phần tử thỏa điều kiện.
5. Cách xóa trong lúc duyệt mà không sửa tập qua đường không phù hợp.

## 2. Hợp đồng cơ bản của contains và remove

### 2.1. contains chỉ trả lời việc thuộc tập

Chữ ký của phương thức:

```java
boolean contains(Object value)
```

Về hợp đồng chung, `contains(value)` trả về `true` nếu có một phần tử `element` sao cho `Objects.equals(value, element)` là `true`. Nếu không có phần tử phù hợp thì trả về `false`, với lưu ý rằng một số triển khai có thể từ chối đối số không hợp lệ bằng ngoại lệ.

`contains` không thêm, xóa hoặc lấy phần tử ra khỏi tập. Kết quả của nó là một giá trị `boolean`, không phải phần tử, chỉ số hay số lần xuất hiện. Vì tập không lưu nhiều bản sao bằng nhau, câu hỏi “có bao nhiêu bản sao của giá trị này?” không phải mục đích của phương thức.

Với một tập ổn định, không có luồng khác sửa đổi và các đối tượng không thay đổi quy tắc bằng nhau khi đang được lưu:

| Trước lời gọi | Lời gọi | Kết quả | Sau lời gọi |
| --- | --- | --- | --- |
| Có phần tử bằng `"java"` | `contains("java")` | `true` | Tập giữ nguyên |
| Không có phần tử bằng `"xml"` | `contains("xml")` | `false` | Tập giữ nguyên |

Đối số có kiểu `Object`, không phải `E`. Vì vậy, việc một lời gọi biên dịch được chưa chứng minh rằng kiểu giá trị truy vấn phù hợp với các phần tử của tập. Phần sau sẽ phân tích trường hợp `Integer` và `Long`.

### 2.2. remove xóa một phần tử phù hợp và trả về cờ thay đổi

Chữ ký:

```java
boolean remove(Object value)
```

Với tập cho phép sửa đổi, `remove(value)` xóa phần tử phù hợp nếu có. Nó trả về:

- `true`: tập đã thay đổi vì một phần tử bị xóa.
- `false`: không có phần tử phù hợp để xóa, nên tập không thay đổi.

Do một `Set` không có hai phần tử bằng nhau theo quy tắc của nó, xóa một giá trị không có nghĩa là phải lặp để xóa nhiều bản sao.

| Trạng thái của tập cho phép sửa đổi | Giá trị trả về | Thay đổi kích thước |
| --- | --- | --- |
| Có phần tử phù hợp với đối số | `true` | Giảm 1 |
| Không có phần tử phù hợp với đối số | `false` | Không đổi |
| Tập đang rỗng, đối số truy vấn hợp lệ | `false` | Vẫn bằng 0 |

Các dòng trên giả định thao tác không bị ngoại lệ, không có sửa đổi đồng thời và phần tử có quy tắc bằng nhau ổn định. Chúng không có nghĩa là mọi triển khai đều bắt buộc chấp nhận mọi đối số hoặc hỗ trợ xóa. Các thao tác sửa đổi của `Set` là thao tác tùy chọn; tập không sửa đổi được có thể ném `UnsupportedOperationException`. [Hợp đồng contains và remove](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

### 2.3. Không cần contains trước mỗi lần remove

Nếu mục đích chỉ là xóa nếu có và báo kết quả, giá trị trả về của `remove` đã cung cấp thông tin cần thiết:

```java
// Đoạn mã minh họa: tags là một Set<String> cho phép sửa đổi.
if (tags.remove("sql")) {
    System.out.println("Đã xóa thẻ sql");
} else {
    System.out.println("Không có thẻ sql để xóa");
}
```

Viết `if (tags.contains("sql")) { tags.remove("sql"); }` thường làm thêm một lượt tìm kiếm. Nó cũng không tự tạo ra một thao tác nguyên tử: giữa hai lời gọi, luồng khác có thể thay đổi tập nếu chương trình có truy cập đồng thời.

Dùng trực tiếp `remove` giúp biểu đạt đúng một yêu cầu, nhưng không làm `HashSet` hoặc `LinkedHashSet` tự trở nên an toàn khi nhiều luồng cùng sửa đổi. Cần chọn cấu trúc và cơ chế đồng bộ phù hợp nếu bài toán có nhiều luồng; đó là một yêu cầu riêng, không suy ra từ kiểu trả về `boolean`.

Nếu chỉ cần kiểm tra và muốn giữ nguyên dữ liệu, dùng `contains`. Không dùng `remove` để “thử xem có không”, vì một phép thử như vậy làm mất phần tử.

## 3. “Cùng một phần tử” được xác định như thế nào?

### 3.1. Bằng giá trị không đồng nghĩa cùng tham chiếu

Hai biến có thể trỏ tới hai đối tượng khác nhau nhưng biểu diễn cùng một giá trị:

```java
// Đoạn minh họa về String, không phải chương trình độc lập.
String stored = "java";
String probe = new String("java");

System.out.println(stored == probe);      // false
System.out.println(stored.equals(probe)); // true
```

Một tập dựa trên bằng nhau của giá trị có thể nhận ra `probe` là phần tử đã có. Người gọi không bắt buộc giữ đúng tham chiếu đã dùng khi thêm phần tử.

Điều này hữu ích khi dữ liệu truy vấn được tạo từ chỗ khác: mã người dùng được đọc từ tệp, thẻ được nhập từ bàn phím hoặc mã công việc được nhận trong một thông điệp. Ta muốn tìm theo ý nghĩa của giá trị, không phải theo vị trí đối tượng trong bộ nhớ.

Tuy nhiên, một lớp do người dùng định nghĩa mà không có quy tắc `equals` theo giá trị sẽ không tự có ý nghĩa đó. Nếu lớp chỉ kế thừa cách bằng nhau của `Object`, hai đối tượng riêng biệt vẫn khác nhau dù các trường có cùng nội dung. Phải thiết kế `equals`/`hashCode` phù hợp hoặc dùng một kiểu giá trị phù hợp, chẳng hạn `record` có các thành phần bất biến.

### 3.2. HashSet và LinkedHashSet: hashCode hỗ trợ tìm, equals xác nhận bằng nhau

`HashSet` dùng cấu trúc băm. Có thể hiểu cơ chế tìm và xóa qua hai bước:

1. Dùng mã băm để thu hẹp vùng cần tìm.
2. Dùng phép so sánh bằng nhau để xác định phần tử phù hợp.

Do đó, thiết kế một phần tử dùng trong tập băm cần tuân thủ:

- Nếu hai đối tượng bằng nhau theo `equals`, chúng phải có cùng `hashCode`.
- Cùng `hashCode` không bắt buộc bằng nhau. Va chạm băm không làm hai giá trị khác nhau trở thành một phần tử.
- Các dữ liệu ảnh hưởng đến `equals` và `hashCode` cần ổn định trong thời gian đối tượng nằm trong tập.

`LinkedHashSet` giữ thêm thứ tự chèn khi duyệt nhưng vẫn có đặc tính tìm theo băm và bằng nhau tương ứng. Không được hiểu rằng nó tìm bằng số thứ tự chèn. [HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html), [LinkedHashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)

Ví dụ `record UserId(String value)` trong chương trình thứ hai dùng quy tắc bằng nhau theo thành phần của record. Vì `String` là bất biến, đây là một khóa đơn giản, ổn định cho ví dụ. Hai `UserId("u-1")` được tạo riêng vẫn bằng nhau theo giá trị, nên có thể dùng đối tượng thứ hai để kiểm tra hoặc xóa phần tử thứ nhất.

Record không làm mọi kiểu thành phần tự trở thành bất biến. Nếu một record chứa đối tượng có thể sửa đổi và phép bằng nhau của đối tượng đó thay đổi, vẫn cần xét nguy cơ khóa thay đổi khi đang ở trong tập.

### 3.3. Đúng kiểu giá trị truy vấn vẫn quan trọng

Xét một `HashSet<Integer>` đang có số nguyên `1`:

```java
// Đoạn minh họa; các import và chương trình đầy đủ nằm ở ví dụ sau.
Set<Integer> numbers = new HashSet<>(Set.of(1, 2));

numbers.contains(1);   // true: 1 được boxing thành Integer
numbers.contains(1L);  // false: 1L được boxing thành Long
numbers.remove(1L);    // false: không xóa Integer 1
```

`Integer.valueOf(1)` và `Long.valueOf(1L)` không bằng nhau theo `equals`. Cùng viết một trị số toán học không có nghĩa chúng là cùng giá trị theo quy tắc của hai lớp.

Các lời gọi trên vẫn biên dịch vì đối số là `Object`. Kiểu tổng quát của tập không buộc phương thức truy vấn phải nhận đúng `E` tại thời điểm biên dịch.

Kết quả `false` ở đây là kết quả của triển khai và dữ liệu cụ thể trong ví dụ. Không khái quát thành “truy vấn sai kiểu luôn trả về false với mọi Set”. Hợp đồng cho phép một số triển khai ném `ClassCastException` khi kiểu đối số không phù hợp. Với `TreeSet`, đối số còn phải có thể được so sánh bằng thứ tự mà tập sử dụng. [Set: ngoại lệ tùy chọn của truy vấn và xóa](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

### 3.4. TreeSet xác định phần tử qua phép so sánh

`TreeSet` dùng thứ tự tự nhiên qua `Comparable` hoặc một `Comparator` được cung cấp. Với các thao tác tìm và xóa, hai giá trị được xem là cùng phần tử khi kết quả so sánh bằng `0`.

Ví dụ:

```java
// Đoạn minh họa; có bản chạy đầy đủ ở chương trình thứ hai.
Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
names.add("Java");

names.contains("JAVA"); // true
names.remove("java");   // true
```

Bộ so sánh không phân biệt hoa thường xem `"Java"`, `"JAVA"` và `"java"` là tương đương. Nhưng `String.equals` lại phân biệt hoa thường: `"Java".equals("JAVA")` là `false`.

Đây là ví dụ nhằm thấy rõ cách `TreeSet` hoạt động, đồng thời chỉ ra một điểm cần thiết kế cẩn thận: thứ tự của tập nên nhất quán với `equals` để tuân thủ hợp đồng chung của `Set`. Một thứ tự không nhất quán với `equals` vẫn tạo ra hành vi xác định theo bộ so sánh của `TreeSet`, nhưng làm tập không tuân thủ hợp đồng chung ấy. Không nên coi sự khác biệt trong ví dụ là một lời khuyên mặc định cho mọi tập chuỗi. [Đặc tả TreeSet về thứ tự và equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)

Nếu nghiệp vụ cần mã không phân biệt hoa thường, hãy xác định quy tắc chuẩn hóa và bằng nhau của mã ngay từ thiết kế. Chẳng hạn, chuẩn hóa khi tạo một kiểu mã chuyên biệt, rồi giữ cùng một ý nghĩa cho `equals`, `hashCode` và thứ tự so sánh. Đừng để mỗi chỗ tìm kiếm tự chọn một quy tắc khác nhau.

### 3.5. Đừng thay đổi khóa sau khi đã thêm vào tập

Giả sử `User` dùng `email` trong `equals` và `hashCode`. Sau khi thêm một `User` vào `HashSet`, chương trình đổi `email` của chính đối tượng đó. Bây giờ mã băm được tính lại có thể khác mã băm lúc chèn.

Khi ấy, việc đối tượng vẫn đang được tham chiếu trong tập không đảm bảo `contains` hay `remove` tìm được nó theo cách người viết mong đợi. Hợp đồng `Set` không quy định hành vi nếu giá trị của phần tử bị thay đổi theo cách ảnh hưởng đến phép bằng nhau khi phần tử vẫn ở trong tập. Không có một stdout di động cho mọi chương trình kiểu này để học thuộc. [Set: phần tử có thể sửa đổi](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

Với `TreeSet`, thay đổi trường dùng để sắp thứ tự cũng làm đối tượng không còn phù hợp với vị trí của nó trong cấu trúc cây.

Cách thiết kế dễ kiểm soát là dùng khóa bất biến. Nếu thực sự cần cập nhật một giá trị định danh, phải thiết kế thao tác xóa giá trị cũ trước khi thay đổi rồi thêm giá trị mới, đồng thời xử lý trường hợp giá trị mới đã tồn tại. Chuỗi thao tác đó không tự có tính nguyên tử hoặc cơ chế khôi phục; ứng dụng cần quyết định chính sách phù hợp.

## 4. Kiểm tra nhiều giá trị và xóa theo nhóm

Không phải mọi yêu cầu đều chỉ liên quan một phần tử. `Set` kế thừa các thao tác tập hợp từ `Collection`, vì vậy cần phân biệt rõ đối tượng nhận lời gọi với tập đối số.

### 4.1. containsAll: mọi giá trị yêu cầu có thuộc tập không?

```java
// Đoạn minh họa.
Set<String> skills = new LinkedHashSet<>(List.of("java", "sql", "http"));

boolean enough = skills.containsAll(List.of("java", "http")); // true
boolean missing = skills.containsAll(List.of("java", "xml")); // false
```

`containsAll(required)` trả về `true` khi tất cả phần tử của `required` đều được chứa trong `skills`. Phương thức không làm thay đổi `skills`.

Có ba điểm dễ nhầm:

- Nó không kiểm tra “có ít nhất một phần tử chung”. Một yêu cầu vắng mặt đã đủ làm kết quả thành `false`.
- Nó không kiểm tra hai tập bằng nhau. `skills` có thể có thêm nhiều phần tử mà kết quả vẫn là `true`.
- Nó không đếm số bản sao trong đối số. Nếu tập có `"java"`, thì `containsAll(List.of("java", "java"))` vẫn là `true`.

Với một tập bình thường hỗ trợ các truy vấn tương ứng, `containsAll` của một tập yêu cầu rỗng trả về `true`: không có yêu cầu nào bị thiếu. Đây là cùng ý nghĩa “mọi phần tử của tập rỗng đều thỏa”, không phải lỗi đặc biệt cần xử lý bằng cách đảo kết quả.

### 4.2. Chọn thao tác đúng mục đích

Trong bảng dưới, `current` là tập nhận lời gọi, `other` là tập hợp dùng để đối chiếu:

| Thao tác | Ý nghĩa đối với current | Giá trị trả về |
| --- | --- | --- |
| `current.containsAll(other)` | Kiểm tra mọi phần tử của other có thuộc current không; giữ nguyên current | `boolean` trả lời truy vấn |
| `current.removeAll(other)` | Xóa khỏi current những phần tử có trong other | `true` nếu current thay đổi |
| `current.retainAll(other)` | Chỉ giữ trong current những phần tử cũng có trong other | `true` nếu current thay đổi |
| `current.removeIf(predicate)` | Xóa các phần tử mà điều kiện trả về true | `true` nếu có phần tử bị xóa |
| `current.clear()` | Xóa mọi phần tử | `void` |

Phương thức được gọi trên tập nào thì tập đó là nơi bị sửa đổi. Truyền một tập khác vào `removeAll` hoặc `retainAll` không có nghĩa là yêu cầu xóa dữ liệu trong tập đối số.

Các giá trị `boolean` của thao tác sửa đổi cũng không phải số lượng phần tử đã xóa. `true` có thể ứng với một hoặc nhiều phần tử bị xóa. Nếu cần biết số lượng, với một tập cho phép sửa đổi và không có tác nhân khác làm thay đổi tập, có thể lấy kích thước trước trừ kích thước sau khi thao tác thành công.

Ví dụ với các tập có cùng quy tắc bằng nhau:

```text
current ban đầu: [java, sql, http, xml]

current.removeAll([sql, missing])
→ còn [java, http, xml]
→ true, vì sql đã bị xóa; missing vắng mặt không gây lỗi

current.retainAll([java, http])
→ còn [java, http]
→ true, vì xml không nằm trong tập cần giữ

current.removeIf(s -> s.startsWith("h"))
→ còn [java]
→ true, vì http thỏa điều kiện xóa
```

Tránh ghi nhớ `retainAll` như “xóa các phần tử đối số”. Nó làm điều ngược lại: phần tử không được tập đối số giữ lại mới bị xóa. Nếu đối số rỗng và thao tác được hỗ trợ, `retainAll` sẽ làm rỗng tập đang có phần tử.

Các thao tác sửa đổi vẫn chịu giới hạn của triển khai. Tập không sửa đổi được không trở nên sửa được chỉ vì chuyển từ `remove` sang `removeAll` hay `removeIf`. [Các thao tác kế thừa của Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

### 4.3. removeIf và điều kiện xóa

`removeIf` nhận một `Predicate`, tức điều kiện nhận phần tử và trả về `boolean`. `true` có nghĩa là **xóa** phần tử đó:

```java
// Đoạn minh họa: pending là Set<String> cho phép sửa đổi.
pending.removeIf(value -> value.startsWith("done:"));
```

Với tập có thể chứa `null`, điều kiện cũng cần xử lý `null` nếu muốn duyệt an toàn:

```java
// Xóa null và các chuỗi rỗng; tags có thể chứa null.
tags.removeIf(value -> value == null || value.isBlank());
```

Ở biểu thức này, `||` đánh giá ngắn mạch: khi `value == null` là `true`, Java không gọi `isBlank()` trên `null`.

Điều kiện nên chỉ quyết định xóa phần tử đang xét. Không tự gọi `add`, `remove` hoặc `clear` trên chính tập từ bên trong điều kiện; việc sửa cấu trúc trong lúc nó đang được duyệt có thể làm thao tác thất bại hoặc không có ý nghĩa mà người viết mong đợi.

Ngoại lệ trong điều kiện được truyền ra ngoài. Không mặc định rằng `removeIf` sẽ khôi phục mọi phần tử đã xóa trước khi ngoại lệ xảy ra. Nếu bài toán cần “hoặc xóa tất cả đúng yêu cầu, hoặc giữ nguyên toàn bộ”, phải thiết kế thêm cơ chế đáp ứng yêu cầu đó. [Collection.removeIf trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#removeIf(java.util.function.Predicate))

## 5. Chương trình đầy đủ: quan sát kết quả của từng thao tác

Chương trình sau dùng `LinkedHashSet` để thứ tự in ra theo thứ tự chèn, giúp stdout dễ đối chiếu. Đây là lựa chọn của ví dụ, không phải cam kết thứ tự của giao diện `Set`.

Lưu toàn bộ mã vào tệp **SetMembershipRemovalDemo.java**:

```java
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SetMembershipRemovalDemo {
    public static void main(String[] args) {
        Set<String> tags =
                new LinkedHashSet<>(Arrays.asList("java", "sql", null));
        System.out.println("initial: " + tags);
        System.out.println("contains equal String: "
                + tags.contains(new String("java")));
        System.out.println("contains missing: " + tags.contains("xml"));
        System.out.println("contains null: " + tags.contains(null));
        System.out.println("size after contains: " + tags.size());

        System.out.println("remove sql: " + tags.remove("sql"));
        System.out.println("after removal: " + tags);
        System.out.println("remove sql again: " + tags.remove("sql"));
        System.out.println("remove null: " + tags.remove(null));
        System.out.println("remaining: " + tags);

        Set<Integer> numbers =
                new LinkedHashSet<>(List.of(1, 2, 3));
        System.out.println("Set.remove(1): " + numbers.remove(1));
        System.out.println("numbers: " + numbers);

        Set<String> skills =
                new LinkedHashSet<>(List.of("java", "sql", "http", "xml"));
        System.out.println("contains repeated requirement: "
                + skills.containsAll(List.of("java", "java")));
        System.out.println("contains empty requirement: "
                + skills.containsAll(List.of()));

        System.out.println("removeAll changed: "
                + skills.removeAll(Set.of("sql", "missing")));
        System.out.println("after removeAll: " + skills);
        System.out.println("retainAll changed: "
                + skills.retainAll(Set.of("java", "http")));
        System.out.println("after retainAll: " + skills);
        System.out.println("removeIf changed: "
                + skills.removeIf(value -> value.startsWith("h")));
        System.out.println("after removeIf: " + skills);

        skills.clear();
        System.out.println("after clear: " + skills
                + "; size = " + skills.size());
    }
}
```

Biên dịch theo mức Java 17 và chạy:

```shell
javac --release 17 -encoding UTF-8 SetMembershipRemovalDemo.java
java SetMembershipRemovalDemo
```

Stdout:

```text
initial: [java, sql, null]
contains equal String: true
contains missing: false
contains null: true
size after contains: 3
remove sql: true
after removal: [java, null]
remove sql again: false
remove null: true
remaining: [java]
Set.remove(1): true
numbers: [2, 3]
contains repeated requirement: true
contains empty requirement: true
removeAll changed: true
after removeAll: [java, http, xml]
retainAll changed: true
after retainAll: [java, http]
removeIf changed: true
after removeIf: [java]
after clear: []; size = 0
```

Cách đọc kết quả:

1. `new String("java")` là đối tượng riêng nhưng bằng chuỗi đã lưu, nên `contains` trả về `true`.
2. Sau các truy vấn, kích thước vẫn là 3. `contains` không lấy phần tử ra khỏi tập.
3. Lần đầu xóa `"sql"` làm tập thay đổi; lần sau không tìm thấy nên trả về `false`.
4. Tập `tags` cho phép `null`, nên có thể kiểm tra và xóa `null` như một giá trị được tập chấp nhận.
5. `numbers.remove(1)` xóa số `1`; kết quả còn `[2, 3]`, chứng minh đây không phải thao tác xóa theo chỉ số.
6. Hai lần yêu cầu `"java"` trong `containsAll` không đòi hai bản sao trong tập.
7. `removeAll`, `retainAll` và `removeIf` lần lượt thay đổi cùng tập `skills`; cần đọc kết quả theo trạng thái trước đó.
8. `clear` không có giá trị trả về. Chương trình kiểm tra hậu trạng thái bằng cách in tập và `size()`.

Các tập tạo bởi `Set.of(...)` trong đối số của `removeAll` và `retainAll` chỉ được dùng để đọc và đối chiếu. Tập nhận lời gọi ở đây vẫn là `LinkedHashSet` có thể sửa đổi, nên không có mâu thuẫn với tính không sửa đổi được của `Set.of`.

## 6. Xóa phần tử trong khi đang duyệt

### 6.1. Vì sao remove trực tiếp trong for-each là vấn đề?

Một vòng `for-each` trên tập dùng bộ duyệt ở phía sau. Nếu đang duyệt một `HashSet` hoặc `LinkedHashSet` thông thường mà lại gọi `set.remove(value)` trực tiếp, cấu trúc bị thay đổi qua một đường khác với bộ duyệt hiện tại.

Đoạn sau minh họa cách viết cần tránh, không phải chương trình để chạy và dựa vào một kết quả ngoại lệ cố định:

```java
// Không dùng cách này để xóa khỏi HashSet/LinkedHashSet khi đang for-each.
for (Integer value : values) {
    if (value % 2 == 0) {
        values.remove(value);
    }
}
```

Bộ duyệt kiểu fail-fast của các tập này có thể ném `ConcurrentModificationException` khi phát hiện sửa đổi cấu trúc không hợp lệ. Tên ngoại lệ không có nghĩa bắt buộc phải có hai luồng; một luồng cũng có thể vừa duyệt vừa sửa sai cách.

Phát hiện fail-fast chỉ có tính nỗ lực tốt nhất. Không thấy ngoại lệ trong một lần chạy không chứng minh vòng lặp là đúng, và không nên dùng ngoại lệ đó làm cơ chế điều khiển thuật toán. [HashSet: bộ duyệt và fail-fast](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)

### 6.2. Iterator.remove xóa phần tử vừa được next trả về

Khi cần chủ động điều khiển việc duyệt và xóa, dùng bộ duyệt của tập cho phép thao tác này:

```java
// Đoạn minh họa; chương trình đầy đủ xuất hiện ở mục tiếp theo.
Iterator<Integer> iterator = values.iterator();
while (iterator.hasNext()) {
    Integer value = iterator.next();
    if (value % 2 == 0) {
        iterator.remove();
    }
}
```

`iterator.remove()` không nhận đối số. Nó xóa phần tử mà lần `next()` gần nhất đã trả về. Quy tắc quan trọng:

- Phải gọi `next()` trước lần xóa tương ứng.
- Mỗi lần `next()` chỉ cho phép một lần `remove()`.
- Gọi xóa trước `next()`, hoặc gọi xóa hai lần liên tiếp cho cùng một phần tử, có thể dẫn đến `IllegalStateException`.
- Bộ duyệt vẫn phải hỗ trợ thao tác xóa; không phải mọi bộ duyệt đều có khả năng này.

Các quy tắc trên áp dụng cho `Iterator.remove` khi thao tác này được hỗ trợ. [Hợp đồng Iterator.remove](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#remove())

Nếu chỉ cần một điều kiện xóa rõ ràng, `removeIf` thường ngắn hơn. Nếu cần nhiều bước xử lý trong từng lượt hoặc cần dùng bộ duyệt tường minh, `Iterator.remove` thể hiện rõ phần tử nào đang bị xóa. Không trộn hai cách bằng việc vừa giữ một bộ duyệt đang hoạt động vừa gọi `removeIf` trên cùng tập.

## 7. null, tập không sửa đổi được và các ngoại lệ

### 7.1. null có được xem là một phần tử không?

Không có một câu trả lời chung cho mọi `Set`:

| Loại tập | Điều cần nhớ khi kiểm tra và xóa |
| --- | --- |
| `HashSet` | Cho phép một phần tử `null`; `contains(null)` và `remove(null)` dùng được |
| `LinkedHashSet` | Cũng cho phép một phần tử `null` |
| `TreeSet` dùng thứ tự tự nhiên | Không chấp nhận `null` như phần tử so sánh; không dùng `contains(null)` như phép thử phổ quát |
| `TreeSet` dùng Comparator | Khả năng xử lý `null` phụ thuộc bộ so sánh |
| `Set.of(...)` | Không cho phần tử `null`; tập cũng không sửa đổi được |

Nếu một tập cho phép `null`, nguyên tắc không trùng vẫn áp dụng: tối đa một `null`.

Đừng gộp hai tình huống khác nhau:

- Giá trị truy vấn hợp lệ nhưng không có trong tập: thường nhận `false`.
- Giá trị truy vấn không được triển khai chấp nhận: có thể nhận ngoại lệ.

Đặc tả cho phép `NullPointerException` khi đối số là `null` mà tập không cho phép, và `ClassCastException` khi kiểu đối số không tương thích. Một số truy vấn đối số không hợp lệ có thể trả về `false` tùy triển khai; vì thế không xây dựng logic dựa trên giả định rằng mọi tập sẽ xử lý chúng giống hệt nhau. [Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html), [TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)

### 7.2. Có thể đọc nhưng chưa chắc có thể xóa

`Set.of("java", "sql")` tạo tập không sửa đổi được. Nó vẫn cho phép truy vấn hợp lệ như `contains("java")`, nhưng các thao tác sửa đổi bị từ chối bằng `UnsupportedOperationException`.

Biến có kiểu `Set<String>` chỉ cho biết giao diện mà người gọi đang dùng. Nó không chứng minh đối tượng thực tế phía sau là một tập có thể sửa đổi.

Nếu cần một tập làm việc riêng có thể xóa, có thể tạo bản sao cấu trúc:

```java
// Đoạn minh họa.
Set<String> source = Set.of("java", "sql");
Set<String> working = new LinkedHashSet<>(source);

working.remove("sql"); // sửa working
// source vẫn chứa "sql"
```

Thứ tự ban đầu của `working` theo thứ tự duyệt của `source`, nên không dự đoán một thứ tự in cố định cho đoạn này chỉ từ `Set.of`. Bản sao tập cũng không phải bản sao sâu của các phần tử: nếu phần tử là đối tượng có thể sửa đổi, hai tập vẫn có thể chứa tham chiếu đến cùng đối tượng.

Không dùng `remove` rồi bắt ngoại lệ như một cách mặc định để khám phá xem mọi dữ liệu có thể sửa đổi không. Hãy thiết kế rõ quyền sở hữu và loại tập từ chỗ tạo hoặc nhận dữ liệu.

## 8. Chương trình đầy đủ: quy tắc bằng nhau và cách xóa khi duyệt

Lưu mã vào **SetLookupRulesDemo.java**:

```java
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class SetLookupRulesDemo {
    private record UserId(String value) {
    }

    public static void main(String[] args) {
        UserId stored = new UserId("u-1");
        UserId probe = new UserId("u-1");
        Set<UserId> ids = new HashSet<>();
        ids.add(stored);

        System.out.println("same UserId instance: " + (stored == probe));
        System.out.println("contains equal UserId: " + ids.contains(probe));
        System.out.println("remove equal UserId: " + ids.remove(probe));
        System.out.println("ids size: " + ids.size());

        Set<Integer> numeric = new HashSet<>(Set.of(1, 2));
        System.out.println("contains Integer 1: " + numeric.contains(1));
        System.out.println("contains Long 1: " + numeric.contains(1L));
        System.out.println("remove Long 1: " + numeric.remove(1L));

        Set<String> ordered = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ordered.add("Java");
        System.out.println("TreeSet contains JAVA: " + ordered.contains("JAVA"));
        System.out.println("String equals JAVA: " + "Java".equals("JAVA"));
        System.out.println("TreeSet remove java: " + ordered.remove("java"));
        System.out.println("TreeSet size: " + ordered.size());

        Set<Integer> values =
                new LinkedHashSet<>(List.of(1, 2, 3, 4, 5));
        Iterator<Integer> iterator = values.iterator();
        while (iterator.hasNext()) {
            Integer value = iterator.next();
            if (value % 2 == 0) {
                iterator.remove();
            }
        }
        System.out.println("after iterator removal: " + values);

        Set<String> fixed = Set.of("java", "sql");
        System.out.println("unmodifiable contains java: " + fixed.contains("java"));
        try {
            fixed.remove("java");
        } catch (UnsupportedOperationException ex) {
            System.out.println("unmodifiable remove: UnsupportedOperationException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SetLookupRulesDemo.java
java SetLookupRulesDemo
```

Stdout:

```text
same UserId instance: false
contains equal UserId: true
remove equal UserId: true
ids size: 0
contains Integer 1: true
contains Long 1: false
remove Long 1: false
TreeSet contains JAVA: true
String equals JAVA: false
TreeSet remove java: true
TreeSet size: 0
after iterator removal: [1, 3, 5]
unmodifiable contains java: true
unmodifiable remove: UnsupportedOperationException
```

Các phần cần tự kiểm chứng khi đọc:

- `stored == probe` là `false`, nhưng `ids.contains(probe)` và `ids.remove(probe)` đều là `true`: đối tượng truy vấn có giá trị bằng đối tượng đã lưu.
- `1L` không bằng `Integer` có giá trị `1`; truy vấn và xóa bằng `Long` trong ví dụ `HashSet<Integer>` đều cho `false`.
- Bộ so sánh của `TreeSet` nhận ra chuỗi khác hoa thường là cùng phần tử; `String.equals` thì không. Ví dụ này chủ động bộc lộ sự không nhất quán cần tránh khi muốn tuân thủ đầy đủ hợp đồng chung của `Set`.
- `Iterator.remove` loại số chẵn ngay trong quá trình duyệt, nên tập còn `[1, 3, 5]`.
- Tập `fixed` hỗ trợ truy vấn nhưng từ chối xóa, dù biến vẫn có kiểu `Set<String>`.

Hai chương trình trong bài đã được biên dịch với `--release 17` và chạy để đối chiếu đúng các stdout đã ghi. `--release 17` kiểm tra mức ngôn ngữ/API và sinh bytecode tương thích Java 17; tùy chọn này không có nghĩa JVM đang chạy chính là JDK 17.

## 9. Hiệu năng: phân biệt giao diện và triển khai

Không thể nhìn thấy kiểu biến `Set<E>` rồi kết luận mọi lời gọi `contains` hoặc `remove` đều có cùng độ phức tạp.

| Triển khai | contains/remove cơ bản | Điều kiện và ý nghĩa |
| --- | --- | --- |
| `HashSet` | Thời gian hằng số kỳ vọng, thường ký hiệu O(1) | Mã băm cần phân bố hợp lý; không phải cam kết thời gian hằng số trong mọi trường hợp |
| `LinkedHashSet` | Tương tự tập băm, O(1) kỳ vọng | Giữ thêm thứ tự chèn khi duyệt; không cần tìm theo chỉ số |
| `TreeSet` | O(log n) | Dựa trên cây có thứ tự; mỗi bước còn có chi phí so sánh |
| Một `Set` bất kỳ | Phụ thuộc triển khai | Giao diện không bắt buộc dùng băm hay cây |

Các ký hiệu trên mô tả thao tác cơ bản theo số phần tử `n`; chi phí thực tế còn phụ thuộc cách tính `hashCode`, `equals`, bộ so sánh và dữ liệu. Không suy ra rằng `containsAll`, `removeAll`, `retainAll` hoặc `removeIf` cũng chỉ mất O(1). Chúng làm việc với nhiều phần tử, có thể duyệt và có thể thực hiện nhiều phép kiểm tra. [HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html), [LinkedHashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html), [TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)

Trong mã ứng dụng, trước hết chọn ý nghĩa đúng:

- Cần trả lời “đã có chưa?”: `contains`.
- Cần xóa nếu có và biết có thay đổi không: trực tiếp `remove`.
- Cần xóa theo một danh sách giá trị: `removeAll`.
- Cần chỉ giữ phần giao: `retainAll`.
- Cần xóa theo điều kiện trên từng phần tử: `removeIf`.
- Cần vừa duyệt có điều khiển vừa xóa phần tử hiện tại: `Iterator.remove`.

Tối ưu số lần tìm chỉ có giá trị sau khi quy tắc bằng nhau, khả năng sửa đổi và quyền sở hữu dữ liệu đã rõ.

## 10. Áp dụng vào một tình huống nhỏ

Giả sử một màn hình quản lý thẻ đang có tập làm việc `selectedTags`:

```java
// Đoạn minh họa, selectedTags là tập làm việc cho phép sửa đổi.
if (selectedTags.contains("java")) {
    System.out.println("Đang chọn thẻ java");
}

boolean removed = selectedTags.remove("archived");
if (removed) {
    System.out.println("Đã bỏ thẻ archived khỏi lựa chọn");
}
```

Hai lời gọi có mục đích riêng:

- Kiểm tra `"java"` để hiển thị trạng thái lựa chọn, nên giữ nguyên tập.
- Xóa `"archived"` vì người dùng thực sự yêu cầu bỏ lựa chọn. Dùng giá trị trả về để biết thao tác có làm thay đổi tập không.

Không cần chuyển tập thành `List` hoặc mảng chỉ để gọi tìm kiếm hay xóa giá trị. Việc chuyển đổi đó thêm công việc và có thể che mất ý nghĩa tập hợp của dữ liệu.

Nếu đầu vào thuộc quyền sở hữu của một thành phần khác, hãy xác định có được sửa trực tiếp không. Một tham số `Set<String>` không tự nói rõ điều này. Khi yêu cầu là tạo kết quả đã lọc mà giữ nguyên đầu vào, tạo tập làm việc riêng rồi xóa trên tập đó:

```java
// Đoạn minh họa; input là Set<String> do bên gọi cung cấp.
Set<String> result = new LinkedHashSet<>(input);
result.removeIf(tag -> tag.startsWith("draft:"));
// Dùng result làm kết quả; không sửa cấu trúc input.
```

Điều kiện ở đoạn cuối giả định `input` không chứa `null`. Nếu hợp đồng đầu vào cho phép `null`, phải chọn rõ giữ hay xóa `null` và viết điều kiện tương ứng, thay vì để một lời gọi `startsWith` quyết định bằng ngoại lệ.

## 11. Những lỗi thường gặp và cách tự chẩn đoán

| Hiện tượng hoặc cách hiểu sai | Nguyên nhân thường gặp | Cách sửa suy nghĩ hoặc cách viết |
| --- | --- | --- |
| “remove trả về phần tử đã xóa” | Nhầm với API có hợp đồng khác | Đọc chữ ký: `Set.remove` trả về `boolean` |
| `Set<Integer>.remove(1)` bị hiểu là xóa vị trí thứ hai | Mang mô hình chỉ số của List sang Set | Set không có chỉ số; đối số là giá trị cần xóa |
| Có cùng nội dung trường nhưng contains trả false | Lớp chưa định nghĩa bằng nhau theo giá trị, hoặc hashCode không khớp equals | Thiết kế và kiểm tra hợp đồng bằng nhau |
| Đổi trường định danh rồi không tìm/xóa được như dự kiến | Phần tử thay đổi quy tắc bằng nhau hoặc sắp thứ tự khi đang trong tập | Dùng khóa bất biến và quy trình cập nhật rõ ràng |
| Tập Integer không tìm thấy truy vấn Long | Kiểu đóng gói khác nhau, equals không coi chúng bằng nhau | Chuẩn hóa kiểu dữ liệu truy vấn |
| TreeSet tìm được chuỗi khác hoa thường | Bộ so sánh trả 0 | Kiểm tra Comparator và sự nhất quán với equals |
| Truy vấn null ném ngoại lệ | Triển khai hoặc bộ so sánh không chấp nhận null | Đọc hợp đồng của loại tập đang dùng |
| Xóa từ Set.of ném ngoại lệ | Tập không sửa đổi được | Tạo tập làm việc có thể sửa đổi nếu đúng yêu cầu |
| removeIf xóa ngược phần muốn giữ | Nhầm true là giữ lại | Trong removeIf, true nghĩa là xóa |
| retainAll xóa nhiều hơn dự kiến | Nhầm tập đối số là danh sách cần xóa | retainAll chỉ giữ phần tử có trong đối số |
| For-each và remove trực tiếp phát sinh lỗi | Sửa cấu trúc qua đường khác với bộ duyệt đang dùng | Dùng Iterator.remove hoặc removeIf phù hợp |
| Chạy không thấy ConcurrentModificationException nên tưởng vòng lặp đúng | Dùng fail-fast làm bằng chứng tính đúng | Tuân thủ hợp đồng duyệt, không dựa vào khả năng phát hiện lỗi |

Khi `contains` hoặc `remove` cho kết quả bất ngờ, lần lượt kiểm tra: loại tập thực tế; kiểu của đối số; quy tắc `equals`/`hashCode` hoặc `Comparator`; phần tử có bị thay đổi khi đang ở trong tập không; tập có cho sửa đổi không. Trình tự này thường hữu ích hơn việc thêm một vòng lặp thủ công để “tìm lại”.

## 12. Tự luyện và đối chiếu

### Bài 1: dự đoán kết quả mà không dùng chỉ số

Bắt đầu bằng:

```java
Set<Integer> values = new LinkedHashSet<>(List.of(10, 20, 30));
```

Thực hiện lần lượt và ghi kết quả của mỗi biểu thức cùng trạng thái tập sau đó:

```java
values.contains(20);
values.remove(1);
values.remove(20);
values.containsAll(List.of(10, 10));
values.retainAll(Set.of(30));
values.removeIf(value -> value > 25);
```

Đối chiếu:

| Biểu thức | Kết quả | Tập sau biểu thức |
| --- | --- | --- |
| `contains(20)` | `true` | `[10, 20, 30]` |
| `remove(1)` | `false` | `[10, 20, 30]` |
| `remove(20)` | `true` | `[10, 30]` |
| `containsAll(List.of(10, 10))` | `true` | `[10, 30]` |
| `retainAll(Set.of(30))` | `true` | `[30]` |
| `removeIf(value -> value > 25)` | `true` | `[]` |

Điểm cần giải thích: `remove(1)` không xóa `20`, vì tập không có khái niệm “phần tử ở chỉ số 1”. Hai yêu cầu `10` trong `containsAll` cũng không đòi hai bản sao.

### Bài 2: chọn đúng phương thức

Với một tập thẻ cho phép sửa đổi:

1. Chỉ xem đã có thẻ `"java"` chưa, không đổi lựa chọn.
2. Bỏ thẻ `"old"` nếu có và biết lựa chọn có thay đổi không.
3. Bỏ mọi thẻ trong một tập `blockedTags`.
4. Chỉ giữ các thẻ thuộc `allowedTags`.
5. Bỏ các thẻ có tiền tố `"temp:"`, với đầu vào đã bảo đảm không có `null`.

Đáp án lần lượt là `contains`, `remove`, `removeAll`, `retainAll` và `removeIf`. Cần nêu được mục đích của giá trị trả về; riêng truy vấn `contains` không làm thay đổi tập.

### Bài 3: tìm nguyên nhân trước khi sửa

Một tập chứa các đối tượng `AccountKey`. Hai đối tượng có cùng mã nhưng `contains` trả về `false`. Hãy đặt câu hỏi trước khi viết mã sửa:

- Đây là `HashSet`, `LinkedHashSet`, `TreeSet` hay một triển khai khác?
- Lớp đã định nghĩa `equals` và `hashCode` theo đúng ý nghĩa mã chưa?
- Nếu dùng `TreeSet`, phép so sánh có nhất quán với ý nghĩa bằng nhau không?
- Có trường nào liên quan đến nhận diện phần tử đã bị thay đổi sau khi thêm không?
- Kiểu và dạng chuẩn hóa của mã truy vấn có khớp dữ liệu đã lưu không?

Không thể chọn một bản vá đúng chỉ từ triệu chứng “contains trả false”. Cần xác định quy tắc nhận diện phần tử trước; sau đó `contains` và `remove` mới có ý nghĩa ổn định.

## 13. Tài liệu để tra cứu tiếp

- [Set — hợp đồng truy vấn, xóa, thao tác tập hợp, phần tử có thể sửa đổi và tập không sửa đổi được](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)
- [HashSet — phép băm, null, hiệu năng và bộ duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)
- [LinkedHashSet — thứ tự chèn và các đặc tính của tập băm](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)
- [TreeSet — quy tắc so sánh và yêu cầu nhất quán với equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)
- [Iterator.remove — xóa phần tử vừa duyệt và trạng thái hợp lệ của bộ duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#remove())
- [Collection.removeIf — hợp đồng điều kiện xóa và ngoại lệ](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#removeIf(java.util.function.Predicate))


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="set-004">Hợp, giao, hiệu và tập con bằng các thao tác collection</a>

<details>
<summary>Xem chi tiết</summary>

# Hợp, giao, hiệu và tập con bằng các thao tác collection

## 1. Từ câu hỏi nghiệp vụ đến phép toán tập hợp

Khi có hai nhóm mã, quyền, nhãn hoặc định danh, ta thường cần trả lời bốn câu hỏi:

- Những giá trị nào xuất hiện ở ít nhất một trong hai nhóm?
- Những giá trị nào có trong cả hai nhóm?
- Những giá trị nào có ở nhóm thứ nhất nhưng không có ở nhóm thứ hai?
- Mọi giá trị của nhóm thứ nhất có nằm trong nhóm thứ hai không?

Đó lần lượt là **hợp, giao, hiệu và quan hệ tập con**.

Giả sử:

```text
A = {A, B, C}
B = {B, D}

Hợp A ∪ B:         {A, B, C, D}
Giao A ∩ B:        {B}
Hiệu A − B:        {A, C}
Hiệu B − A:        {D}
```
Dấu ngoặc nhọn ở đây biểu diễn một tập về mặt toán học, không nói đến thứ tự duyệt.

Trong Java, Set kế thừa các thao tác từ Collection và gắn chúng với ý nghĩa tập hợp. Điểm dễ nhầm là các thao tác sửa dữ liệu **không trả về tập kết quả mới**: chúng sửa đối tượng nhận lời gọi và trả boolean cho biết đối tượng đó có thay đổi hay không.

Vì vậy, mỗi phép tính cần xác định hai điều riêng:

1. Muốn lấy kết quả toán học nào?
2. Có được phép thay đổi tập đầu vào hay cần một tập kết quả độc lập?

Bài dùng Java 17 trở lên. Các ví dụ dùng String, có quan hệ equals/hashCode phù hợp, dữ liệu không null và không bị thay đổi đồng thời. Những điều kiện về kiểu phần tử và cách triển khai sẽ được giải thích ở phần sau.

## 2. Bảng ánh xạ giữa toán học và API

| Mục tiêu | Ý nghĩa theo từng giá trị x | Cách thực hiện trên một bản sao của A |
| --- | --- | --- |
| Hợp A ∪ B | x thuộc A hoặc thuộc B | result.addAll(B) |
| Giao A ∩ B | x thuộc A và thuộc B | result.retainAll(B) |
| Hiệu A − B | x thuộc A và không thuộc B | result.removeAll(B) |
| A là tập con của B | Mọi x thuộc A đều thuộc B | B.containsAll(A), không cần bản sao |

Ba dòng đầu bắt đầu từ `result` có các thành viên của A. Dòng tập con là một truy vấn, không tạo tập kết quả.

Hợp đồng chính thức được mô tả trong [Set Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html) và các phương thức tương ứng của [Collection Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html).

Có thể kiểm tra ba phép tạo tập bằng bảng hiện diện:

| x thuộc A? | x thuộc B? | x thuộc A ∪ B? | x thuộc A ∩ B? | x thuộc A − B? |
| --- | --- | --- | --- | --- |
| Không | Không | Không | Không | Không |
| Không | Có | Có | Không | Không |
| Có | Không | Có | Không | Có |
| Có | Có | Có | Có | Không |

Bảng này giúp tự suy luận mà không cần nhớ máy móc tên phương thức. Chẳng hạn retainAll có nghĩa giữ lại các thành viên thỏa điều kiện thuộc B; removeAll làm điều ngược lại với những thành viên đó.

## 3. Hợp bằng addAll: giữ những gì có ở ít nhất một phía

### 3.1. Cách làm giữ nguyên đầu vào

```java
// Đoạn minh họa: a và b là hai Set<String> tương thích.
Set<String> union = new LinkedHashSet<>(a);
union.addAll(b);
```
Sau constructor, union có các giá trị của a. addAll đưa vào những giá trị của b chưa có.

Các giá trị chung không được nhân thành nhiều lần xuất hiện. Nếu A có B và tập B cũng có B, hợp vẫn chỉ có một giá trị B.

Nếu đối số là một List có phần tử trùng, kết quả nhận là Set vẫn chỉ giữ các giá trị phân biệt. Điều đó không biến phép hợp thành phép nối List.

### 3.2. Tính giao hoán nói về nội dung tập

Về nội dung, A ∪ B bằng B ∪ A. Nhưng nếu dùng LinkedHashSet, cách bắt đầu từ A hoặc B có thể tạo thứ tự duyệt khác nhau.

Ví dụ, bắt đầu bằng A rồi thêm các phần tử mới của B khác với bắt đầu bằng B rồi thêm phần mới của A. Hai kết quả vẫn có thể Set.equals bằng nhau.

Không dùng chuỗi toString có thứ tự khác để kết luận phép hợp sai. Cần so nội dung theo hợp đồng Set khi thứ tự không phải một phần yêu cầu.

### 3.3. addAll trả boolean, không trả tập

```java
// Đoạn minh họa.
boolean changed = union.addAll(b);
```
Nếu union đã có tất cả giá trị của b, changed là false dù union có thể rất lớn và không rỗng.

Không thể viết `Set<String> union = a.addAll(b)`: kiểu trả về là boolean. Cũng không được dùng giá trị boolean đó như câu trả lời cho “hợp có rỗng không”.

Nếu chỉ cần biết hợp có rỗng không và hai đầu vào ổn định, có thể suy ra từ việc cả hai tập đều rỗng, thay vì nhất thiết dựng toàn bộ hợp. Hãy chọn công việc tương ứng câu hỏi cần trả lời.

## 4. Giao bằng retainAll: giữ phần có ở cả hai phía

### 4.1. Bắt đầu từ một bản sao rồi lọc theo tập kia

```java
// Đoạn minh họa.
Set<String> intersection = new LinkedHashSet<>(a);
intersection.retainAll(b);
```
retainAll bỏ khỏi intersection mọi phần tử không có trong b. Vì ban đầu intersection là bản sao thành viên của a, những gì còn lại chính là A ∩ B.

Trong ví dụ A = {A, B, C}, B = {B, D}, chỉ B còn lại.

### 4.2. “Giữ tất cả của B” không có nghĩa sao chép B

retainAll không thêm các phần tử đang thiếu. Nếu D có trong b nhưng không có trong a, D không xuất hiện trong giao.

Đọc tên phương thức theo nghĩa “giữ các phần tử hiện có mà thuộc collection đối chiếu”, không phải “đưa toàn bộ collection đối chiếu vào đây”.

### 4.3. false không có nghĩa không có phần chung

Nếu A = {A, B} và B = {A, B, C}, retainAll trên một bản sao của A không phải xóa gì nên trả false. Nhưng giao là {A, B}, không rỗng.

Để kiểm tra giao có rỗng sau khi tính:

```java
// Đoạn minh họa.
boolean noCommonElements = intersection.isEmpty();
```
Nếu chỉ cần biết có phần chung hay không, xem phần Collections.disjoint để tránh dựng tập kết quả không cần thiết.

## 5. Hiệu bằng removeAll: chiều tính là một phần ý nghĩa

### 5.1. A − B bắt đầu từ A

```java
// Đoạn minh họa.
Set<String> difference = new LinkedHashSet<>(a);
difference.removeAll(b);
```
Kết quả giữ các giá trị có trong a nhưng không có trong b.

Nếu cần B − A, phải bắt đầu từ b:

```java
// Đoạn minh họa.
Set<String> reverseDifference = new LinkedHashSet<>(b);
reverseDifference.removeAll(a);
```
Hiệu không giao hoán. Trong ví dụ, {A, C} khác {D}. Đảo hai toán hạng là đổi câu hỏi.

### 5.2. Hiệu là loại một nhóm giá trị, không phải trừ số lượng

Set không giữ số lần xuất hiện. Nếu collection đối chiếu liệt kê B ba lần, việc loại B khỏi một Set không có ba lần hiện diện để trừ dần.

Với một List làm bên nhận, removeAll còn có hành vi collection khác về số lần xuất hiện: nó bỏ các phần tử của List thuộc collection đối chiếu. Không gọi đó là phép trừ đa tập theo số lượng từng giá trị.

Bài đang xây dựng đại số tập hợp trên Set. Nếu bài toán cần “có ba sản phẩm A, bớt một sản phẩm A”, cần mô hình số lượng riêng.

### 5.3. Hiệu không tự là phần bù tuyệt đối

A − B là hiệu tương đối: giới hạn trong các thành viên đang có của A.

Muốn nói “mọi giá trị không thuộc B” cần xác định tập vũ trụ U. Khi đó phần bù của B trong phạm vi U là U − B.

Trong mã nghiệp vụ, hãy chỉ rõ phạm vi: chẳng hạn các mã được cấu hình nhưng chưa được chọn, thay vì nói mơ hồ “tất cả phần tử còn lại” mà không có nguồn giới hạn.

## 6. Ví dụ chạy được: tính độc lập và giữ hai nguồn

Lưu chương trình vào `SetAlgebraDemo.java`:

```java
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SetAlgebraDemo {
    public static void main(String[] args) {
        Set<String> a = new LinkedHashSet<>(List.of("A", "B", "C"));
        Set<String> b = new LinkedHashSet<>(List.of("B", "D"));

        Set<String> union = new LinkedHashSet<>(a);
        union.addAll(b);

        Set<String> intersection = new LinkedHashSet<>(a);
        intersection.retainAll(b);

        Set<String> aMinusB = new LinkedHashSet<>(a);
        aMinusB.removeAll(b);

        Set<String> bMinusA = new LinkedHashSet<>(b);
        bMinusA.removeAll(a);

        System.out.println("A union B = " + union);
        System.out.println("A intersection B = " + intersection);
        System.out.println("A minus B = " + aMinusB);
        System.out.println("B minus A = " + bMinusA);
        System.out.println("A after calculations = " + a);
        System.out.println("B after calculations = " + b);
    }
}
```
Biên dịch và chạy:

```shell
javac --release 17 SetAlgebraDemo.java
java SetAlgebraDemo
```
Kết quả:

```text
A union B = [A, B, C, D]
A intersection B = [B]
A minus B = [A, C]
B minus A = [D]
A after calculations = [A, B, C]
B after calculations = [B, D]
```
Mỗi kết quả bắt đầu từ một bản sao riêng. Vì vậy, tính giao không làm mất dữ liệu cần cho phép hiệu hoặc phép hợp về sau.

Nếu thay bằng một biến duy nhất rồi lần lượt addAll, retainAll và removeAll, các bước sau sẽ nhận dữ liệu đã bị bước trước sửa. Khi đó không còn là bốn phép tính độc lập trên cùng A và B ban đầu.

Ví dụ dùng LinkedHashSet vì nó có hợp đồng thứ tự thêm, giúp output ổn định theo dữ liệu đã cho. [Tài liệu LinkedHashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html) mô tả bảo đảm này.

Nội dung toán học của Set không phụ thuộc thứ tự đó. Với HashSet hoặc nguồn có thứ tự duyệt không được bảo đảm, không được tự gán một thứ tự in cố định.

## 7. Tập con bằng containsAll: đọc đúng chiều

### 7.1. A là tập con của B khi B chứa tất cả A

```text
A ⊆ B
tương ứng với
B.containsAll(A)
```
Đối tượng nhận lời gọi là tập đóng vai trò chứa. Đối số là tập được kiểm tra có nằm trọn bên trong hay không.

Hai câu:

- a.containsAll(b): hỏi B có là tập con của A không.
- b.containsAll(a): hỏi A có là tập con của B không.

Đảo đối tượng nhận và đối số sẽ đảo quan hệ. Đặt tên như candidate và container có thể giúp lời gọi dễ đọc hơn, nhưng trong phần giải thích tiếng Việt hãy nghĩ là “tập cần kiểm tra” và “tập chứa”.

### 7.2. Tập bằng nhau vẫn là tập con

Theo nghĩa không nghiêm ngặt, mọi tập là tập con của chính nó. Hai tập có cùng thành viên đều là tập con của nhau.

Nếu muốn một **tập con thực sự** đối với các tập hữu hạn trong phạm vi size() biểu diễn chính xác kích thước, cần vừa được chứa vừa có ít phần tử hơn:

```java
// Đoạn minh họa: a và b đều là Set với quy tắc hiện diện tương thích.
boolean properSubset = a.size() < b.size() && b.containsAll(a);
```
Điều kiện size một mình không đủ: A có ít phần tử hơn B nhưng vẫn có thể chứa một giá trị không thuộc B.

### 7.3. Tập rỗng là tập con của mọi tập

Một tập chứa tất cả phần tử của tập rỗng vì không có phần tử nào trong tập rỗng vi phạm điều kiện.

Do đó:

- Tập rỗng là tập con của một tập không rỗng.
- Tập rỗng là tập con của chính nó.
- Tập rỗng không phải tập con thực sự của chính nó.
- Tập rỗng là tập con thực sự của một tập không rỗng trong phạm vi tập hữu hạn đang dùng.

Đây là trường hợp cần đưa vào suy luận và kiểm chứng, không coi containsAll(empty) trả true là lỗi.

### 7.4. containsAll không kiểm tra đủ số lần xuất hiện

Một Set có duy nhất A vẫn có thể containsAll một List [A, A]. Mọi giá trị được hỏi đều có, nhưng Set không cần có “hai bản A”.

Vì vậy, không lấy containsAll trên một List làm kiểm tra đủ tồn kho, đủ số lượng ký tự hoặc đủ số lần xuất hiện.

Công thức dùng size để phân biệt tập con thực sự chỉ áp dụng như trên khi hai phía là **tập**, không thay size của một List có trùng vào như thể đó là số phần tử phân biệt.

## 8. Không có phần chung bằng Collections.disjoint

Theo [Collections.disjoint của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#disjoint(java.util.Collection,java.util.Collection)), kết quả true nghĩa là hai collection không có phần tử chung.

Với những đầu vào dùng cùng quy tắc hiện diện:

```text
Collections.disjoint(A, B) == true
tương ứng với
A ∩ B rỗng.
```
Muốn hỏi “có ít nhất một phần tử chung” thì phủ định kết quả đó.

disjoint là truy vấn boolean. Nó không tạo tập giao và không xóa dữ liệu của hai phía.

Khi chỉ cần một câu trả lời có/không, không nhất thiết phải sao chép A rồi retainAll(B) chỉ để gọi isEmpty. disjoint cho thấy ý định rõ ràng và có thể dừng khi tìm được phần tử chung, tùy triển khai.

Đừng nhầm disjoint với “hai tập khác nhau”. Hai tập có thể khác nhau nhưng vẫn giao nhau. A = {A, B} và B = {B, C} không bằng nhau, nhưng không rời nhau vì cùng có B.

## 9. Ví dụ chạy được: tập con, tập rỗng và dữ liệu lặp

Lưu vào `SetSubsetDemo.java`:

```java
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SetSubsetDemo {
    static boolean isSubset(Set<?> candidate, Set<?> container) {
        return container.containsAll(candidate);
    }

    static boolean isProperSubset(Set<?> candidate, Set<?> container) {
        return candidate.size() < container.size()
                && container.containsAll(candidate);
    }

    public static void main(String[] args) {
        Set<String> small = new LinkedHashSet<>(List.of("A"));
        Set<String> large = new LinkedHashSet<>(List.of("A", "B"));
        Set<String> sameMembers = new LinkedHashSet<>(List.of("B", "A"));
        Set<String> empty = Set.of();

        System.out.println("small subset of large = " + isSubset(small, large));
        System.out.println("large subset of small = " + isSubset(large, small));
        System.out.println("small proper subset of large = "
                + isProperSubset(small, large));
        System.out.println("large subset of sameMembers = "
                + isSubset(large, sameMembers));
        System.out.println("large proper subset of sameMembers = "
                + isProperSubset(large, sameMembers));
        System.out.println("empty subset of large = " + isSubset(empty, large));
        System.out.println("empty proper subset of empty = "
                + isProperSubset(empty, empty));

        List<String> repeatedRequest = List.of("A", "A");
        System.out.println("request occurrence count = " + repeatedRequest.size());
        System.out.println("small contains repeated request = "
                + small.containsAll(repeatedRequest));

        System.out.println("small and only B are disjoint = "
                + Collections.disjoint(small, Set.of("B")));
        System.out.println("small and large are disjoint = "
                + Collections.disjoint(small, large));
    }
}
```
Biên dịch và chạy:

```shell
javac --release 17 SetSubsetDemo.java
java SetSubsetDemo
```
Kết quả:

```text
small subset of large = true
large subset of small = false
small proper subset of large = true
large subset of sameMembers = true
large proper subset of sameMembers = false
empty subset of large = true
empty proper subset of empty = false
request occurrence count = 2
small contains repeated request = true
small and only B are disjoint = true
small and large are disjoint = false
```
Những điều được kiểm chứng:

1. {A} được chứa trong {A, B}, còn chiều ngược lại là false.
2. Hai tập {A, B} và {B, A} có cùng thành viên, nên quan hệ tập con đúng nhưng tập con thực sự sai.
3. Tập rỗng được chứa trong tập không rỗng.
4. Tập rỗng không nhỏ hơn chính nó để trở thành tập con thực sự.
5. List yêu cầu A hai lần vẫn được containsAll bởi Set chỉ có một A.
6. {A} và {B} rời nhau; {A} và {A, B} có phần chung.

Hai hàm trong chương trình nhận Set<?> vì việc kiểm tra hiện diện không cần thêm phần tử mới vào tập. Chúng giả định các tập không null và các phần tử có thể được hỏi lẫn nhau theo hợp đồng của cách triển khai.

Kiểu tham số Set<?> không tự biến các kiểu phần tử không tương thích thành các truy vấn hợp lệ. Những giới hạn kiểu và null của collection cụ thể vẫn có hiệu lực.

## 10. Boolean báo thay đổi không phải kết quả đại số

Với một tập nhận lời gọi có thể sửa và dữ liệu hợp lệ, có thể hiểu như sau:

| Lời gọi trên A ban đầu | Khi nào trả true? | false có nghĩa gì? |
| --- | --- | --- |
| A.addAll(B) | Có ít nhất một giá trị mới được thêm từ B | A đã chứa các giá trị của B |
| A.retainAll(B) | Có ít nhất một giá trị của A bị loại vì không thuộc B | A không cần bị loại phần tử nào |
| A.removeAll(B) | Có ít nhất một giá trị chung bị xóa khỏi A | Không có phần tử phù hợp bị xóa |
| A.containsAll(B) | Mọi giá trị của B được chứa trong A | Có ít nhất một giá trị của B không được chứa |

Ba dòng đầu báo tác động của một thao tác sửa. Dòng cuối báo chân trị của một truy vấn. Chúng đều trả boolean nhưng không trả lời cùng loại câu hỏi.

Ví dụ retainAll trả true có thể để lại tập rỗng hoặc không rỗng, tùy những phần tử bị loại và được giữ. RetainAll trả false cũng có thể để lại một tập lớn nếu mọi phần tử vốn đã thuộc B.

Không dùng changed như thể nó là:

- Tập kết quả.
- Tín hiệu “hai tập có giao nhau”.
- Tín hiệu “kết quả không rỗng”.
- Số lượng phần tử thêm hoặc xóa.

Muốn số lượng thay đổi trong một luồng ổn định, có thể ghi size trước và sau khi phép sửa hoàn tất; vẫn phải diễn giải đúng thao tác đang làm. Nếu có các thay đổi khác xen vào, hiệu size không còn là một phép đếm đáng tin của riêng lời gọi đó.

## 11. Sao chép tham chiếu không tạo bản sao tập độc lập

### 11.1. Hai biến có thể cùng chỉ đến một Set

```java
// Đoạn minh họa.
Set<String> result = a;
result.removeAll(b);
```
Dòng gán chỉ tạo thêm một biến tham chiếu đến cùng đối tượng. a cũng bị thay đổi vì result chính là a.

Mẫu giữ nguồn là:

```java
// Đoạn minh họa.
Set<String> result = new LinkedHashSet<>(a);
result.removeAll(b);
```
Đối tượng Set kết quả có cấu trúc riêng. Việc thêm/xóa thành viên của nó không trực tiếp thêm/xóa thành viên trong a.

### 11.2. Cấu trúc riêng chưa phải bản sao sâu của phần tử

Constructor nhận collection không tự nhân bản từng đối tượng phần tử. Nếu phần tử là một đối tượng có thể sửa, a và result có thể vẫn trỏ đến cùng đối tượng đó.

Đừng thay các trường dùng cho equals/hashCode hoặc quy tắc so sánh khi phần tử đang ở trong các tập. Một phép tính tập hợp đúng còn cần quan hệ hiện diện ổn định trong lúc thao tác.

Ở các ví dụ dùng String, phần tử là giá trị ổn định nên có thể tập trung vào tác động thêm/xóa cấu trúc.

### 11.3. Tập nhận có thể không hỗ trợ sửa

Set.of và Set.copyOf tạo các tập không cho sửa cấu trúc. Gọi retainAll hoặc removeAll trực tiếp trên những tập này có thể ném UnsupportedOperationException; chúng không tự trả một tập kết quả mới để thay thế việc sửa.

Có thể đọc một Set.of làm nguồn hoặc làm collection đối chiếu với dữ liệu hợp lệ. Điều cần là **đối tượng nhận thao tác sửa** phải hỗ trợ thao tác đó.

Nếu muốn trả về kết quả không sửa được, trước hết tính trên một bản sao có thể sửa rồi áp chính sách xuất kết quả phù hợp. Nếu dùng Set.copyOf, phải tính thêm chính sách không nhận null và việc không được dựa vào thứ tự duyệt của kết quả.

### 11.4. Ví dụ chạy được về alias, bản sao và giá trị trả về

Lưu vào `SetMutationResultDemo.java`:

```java
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SetMutationResultDemo {
    public static void main(String[] args) {
        Set<String> unchanged = new LinkedHashSet<>(List.of("A", "B"));
        System.out.println("addAll changed = "
                + unchanged.addAll(List.of("A", "B")));
        System.out.println("retainAll changed = "
                + unchanged.retainAll(List.of("A", "B", "C")));
        System.out.println("removeAll changed = "
                + unchanged.removeAll(List.of("Z")));
        System.out.println("unchanged result = " + unchanged);

        Set<String> original = new LinkedHashSet<>(List.of("A", "B"));
        Set<String> alias = original;
        alias.addAll(List.of("C"));
        System.out.println("alias is original = " + (alias == original));
        System.out.println("original after alias.addAll = " + original);

        Set<String> independent = new LinkedHashSet<>(original);
        independent.removeAll(List.of("A"));
        System.out.println("copy is original = " + (independent == original));
        System.out.println("independent result = " + independent);
        System.out.println("original after copy.removeAll = " + original);

        Set<String> fixed = Set.of("A", "B");
        try {
            fixed.retainAll(Set.of("B"));
        } catch (UnsupportedOperationException ex) {
            System.out.println("retainAll on Set.of = "
                    + ex.getClass().getSimpleName());
        }
    }
}
```
Biên dịch và chạy:

```shell
javac --release 17 SetMutationResultDemo.java
java SetMutationResultDemo
```
Kết quả:

```text
addAll changed = false
retainAll changed = false
removeAll changed = false
unchanged result = [A, B]
alias is original = true
original after alias.addAll = [A, B, C]
copy is original = false
independent result = [B, C]
original after copy.removeAll = [A, B, C]
retainAll on Set.of = UnsupportedOperationException
```
Ba lời gọi đầu đều trả false nhưng tập kết quả vẫn là [A, B]. Điều này bác bỏ việc hiểu false là “không có kết quả”.

alias và original là cùng đối tượng nên thêm qua alias làm original đổi. independent là một Set khác, nên xóa A khỏi independent không xóa A khỏi original.

Cuối cùng, retainAll không được hỗ trợ trên Set.of trong ví dụ. Đây là lỗi về khả năng sửa, không phải vì biểu thức giao {A, B} ∩ {B} không có ý nghĩa toán học.

## 12. Các điều kiện để phép toán có cùng ngữ nghĩa ở hai phía

### 12.1. Cùng một tiêu chí hiện diện

Các biểu thức tập hợp ở trên giả định những tập liên quan dùng quan hệ bằng nhau tương thích.

Với lớp tự định nghĩa, cần equals/hashCode nhất quán. Với TreeSet, comparator phải nhất quán với equals nếu muốn sử dụng đầy đủ hợp đồng Set chung.

Nếu một phía coi chuỗi không phân biệt hoa/thường là cùng giá trị, còn phía kia dùng String.equals phân biệt hoa/thường, việc gọi các thao tác qua lại có thể không phản ánh cùng một phép toán toán học.

Một cách rõ ràng hơn là chuẩn hóa thành một kiểu khóa có ngữ nghĩa đã chọn trước khi tính. Không kỳ vọng addAll/retainAll/removeAll tự hòa giải hai định nghĩa “bằng nhau” khác nhau.

Collections.disjoint cũng yêu cầu chú ý tới ngữ nghĩa hiện diện của đầu vào; triển khai có thể chọn phía nào để duyệt và phía nào để hỏi contains. Không xây dựng tính đúng dựa vào phỏng đoán một hướng duyệt nội bộ cụ thể.

### 12.2. Chính sách null và kiểu truy vấn vẫn còn hiệu lực

Một Set có thể cho giữ null, một Set khác có thể từ chối null ngay cả khi chỉ kiểm tra hiện diện. Các thao tác như retainAll có thể kiểm tra hiện diện ở phía đối chiếu, nên không tự suy ra “đã sao chép sang LinkedHashSet thì mọi null đều an toàn”.

Tương tự, một tập có thứ tự có thể yêu cầu các phần tử so sánh được hoặc được comparator hỗ trợ. Set<?> không loại bỏ các yêu cầu lúc chạy này.

Các ví dụ cố ý dùng String không null ở mọi phía để hợp đồng có thể được đọc nhất quán.

### 12.3. Thứ tự của kết quả là quyết định riêng

Dùng LinkedHashSet giữ thứ tự duyệt được gặp khi dựng kết quả và thứ tự thêm giá trị mới. Nhưng nếu nguồn không bảo đảm thứ tự, việc sao chép không khôi phục một “thứ tự thêm ban đầu” đã không được nguồn cung cấp.

Nếu yêu cầu kết quả phải được sắp theo comparator cụ thể, cần chọn một Set có thứ tự và giữ đúng comparator đó. Đừng sao chép mù quáng sang một kiểu khác rồi coi mọi bảo đảm cũ vẫn còn.

Nội dung tập và thứ tự trình bày là hai yêu cầu khác nhau. Chọn cách tạo kết quả sao cho đáp ứng cả hai nếu cần.

### 12.4. Không có bảo đảm giao dịch hoặc bản chụp dữ liệu chung

Các phép sửa hàng loạt không mặc nhiên là tất cả-hoặc-không. Nếu một phần tử không hợp lệ hoặc thao tác bên trong ném lỗi, không nên giả định mọi thay đổi trước đó đều được hoàn tác.

Cũng không sửa collection nguồn trong lúc addAll đang dùng nó; hợp đồng quy định giới hạn với việc nguồn bị thay đổi trong quá trình thao tác. Với các tập nhận, nguồn và vùng nhìn có dùng chung dữ liệu, cần hiểu quan hệ tham chiếu trước khi sửa.

Trong môi trường nhiều luồng, phải có cách giữ dữ liệu phù hợp với yêu cầu nhất quán. Một lớp Set tự an toàn cho từng thao tác không tự bảo đảm mọi chuỗi “sao chép rồi tính rồi áp dụng” là một giao dịch nguyên tử.

## 13. Chi phí: số lời gọi ít không có nghĩa O(1)

Một lời gọi addAll hay retainAll có thể đi qua nhiều phần tử. Hãy tách:

1. Chi phí sao chép A để giữ nguồn.
2. Chi phí duyệt các phần tử cần xét.
3. Chi phí kiểm tra hiện diện hoặc sửa trong cấu trúc.
4. Bộ nhớ cho tập kết quả.

Ví dụ với những tập băm thông thường, băm phân bố tốt và equals/hashCode có chi phí phù hợp:

- Dựng bản sao A có công việc theo số phần tử của A.
- Tạo hợp bằng cách sao chép A rồi thêm B thường có chi phí kỳ vọng dạng O(n + m), với n = |A|, m = |B|.
- Một cách tính giao là xét các phần tử của A và hỏi có trong B không; nếu mỗi truy vấn hiện diện có chi phí kỳ vọng hằng số, phần xét tăng tuyến tính theo số phần tử được duyệt.
- Truy vấn tập con có thể dừng sớm khi gặp một phần tử thiếu, nhưng trường hợp xấu cần xét mọi phần tử của tập được kiểm tra.

Đó là phân tích với giả định về cách triển khai, không phải bảo đảm của mọi Set.

Nếu collection đối chiếu là một List dài m và contains phải tìm tuyến tính, việc kiểm tra n phần tử có thể tạo O(n * m) công việc. Một số phương thức hoặc triển khai có cách tối ưu khác, nên phải nhìn thuật toán cụ thể thay vì gán một độ phức tạp cố định cho tên phương thức.

Tạo một cấu trúc tra cứu trước có thể hữu ích khi truy vấn nhiều lần, nhưng phải cộng chi phí dựng, bộ nhớ và bảo toàn tiêu chí bằng nhau. Không đổi sang HashSet nếu việc đó làm mất một quan hệ hiện diện khác mà nghiệp vụ đang dựa vào.

Nếu chỉ cần biết giao có rỗng hoặc một quan hệ tập con, dùng truy vấn tương ứng có thể tránh tạo toàn bộ tập kết quả. Nếu thật sự cần các thành viên kết quả, phải dành công việc và bộ nhớ để thu thập chúng.

## 14. Ứng dụng: so trạng thái hiện tại với trạng thái mong muốn

Giả sử:

```text
Hiện tại A = {A, B, C}
Mong muốn B = {B, D}
```
Có thể phân chia rõ:

| Nhóm | Công thức | Kết quả |
| --- | --- | --- |
| Cần bổ sung | B − A | {D} |
| Cần loại bỏ | A − B | {A, C} |
| Đã có và vẫn cần | A ∩ B | {B} |

Đây là một cách tổ chức suy luận khi đối chiếu các nhóm mã hoặc cấu hình.

Cần giữ hai trạng thái đầu vào để các phép tính độc lập. Nếu xóa khỏi A trước rồi dùng A đã sửa để tính những phần khác, có thể mất thông tin về trạng thái ban đầu.

Các nhóm kết quả chỉ mô tả chênh lệch theo khóa hoặc phần tử đã chọn. Chúng không tự áp dụng thay đổi vào một hệ thống bên ngoài và không tự giải quyết lỗi khi thực hiện từng thay đổi. Phần áp dụng cần hợp đồng riêng theo bối cảnh chương trình.

Nếu phần tử là bản ghi đầy đủ nhưng câu hỏi chỉ là “mã nào được thêm/bỏ”, nên tính trên tập khóa ổn định thích hợp. Hai bản ghi cùng mã nhưng khác thuộc tính có thể cần một bước so cập nhật riêng, thay vì coi mọi khác biệt là thêm hoặc xóa.

## 15. Các quy tắc giúp tự kiểm tra kết quả

Với các tập dùng cùng quan hệ hiện diện:

| Quy tắc | Ý nghĩa |
| --- | --- |
| A ∪ ∅ = A | Hợp với rỗng không thêm giá trị |
| A ∩ ∅ = ∅ | Không có giá trị chung với rỗng |
| A − ∅ = A | Không có gì cần loại |
| ∅ − A = ∅ | Không có phần tử ở phía trái để giữ |
| A ∩ A = A | Mọi phần tử của A đều thuộc A |
| A − A = ∅ | Mọi phần tử đều bị loại theo cùng tập |
| A ∩ B là tập con của A và của B | Giao không tạo phần tử ngoài hai phía |
| A − B là tập con của A | Hiệu không thêm thành viên mới |
| A − B rời B | Không giữ giá trị còn thuộc B |

Khi kiểm tra một hàm tạo kết quả độc lập, có thể giữ bản sao hai nguồn và xác nhận chúng không bị sửa sau phép gọi. Đừng chỉ kiểm tra output trong khi bỏ qua tác động lên đầu vào.

Với tập hữu hạn:

```text
|A ∪ B| = |A| + |B| − |A ∩ B|
|A − B| = |A| − |A ∩ B|
```
Trong ví dụ, hợp có 3 + 2 − 1 = 4 phần tử; hiệu A − B có 3 − 1 = 2 phần tử.

Nếu cần tính kích thước rất lớn trong mã, chọn kiểu số phù hợp và chú ý giới hạn của size theo hợp đồng collection. Không biến một công thức toán học đúng thành phép cộng int có thể tràn mà không xem miền dữ liệu.

## 16. Lỗi thường gặp và thực hành

### 16.1. Những lỗi cần nhận ra

| Lỗi | Cách sửa suy luận |
| --- | --- |
| Dùng A.containsAll(B) để kiểm tra A là tập con B | Đọc “B chứa tất cả A”: B.containsAll(A) |
| Dùng boolean retainAll làm kết quả giao | Lấy các phần tử trong tập nhận sau thao tác |
| Trả false của retainAll rồi kết luận hai tập rời nhau | Kiểm tra isEmpty của giao hoặc dùng disjoint |
| Tính nhiều kết quả liên tiếp trên cùng một biến đã sửa | Mỗi kết quả độc lập bắt đầu từ dữ liệu gốc phù hợp |
| Gán result = A rồi tưởng A được giữ nguyên | Tạo một đối tượng Set sao chép độc lập |
| Đảo A − B thành B − A | Xác định phía cần giữ trước, phía cần loại sau |
| Gọi retainAll trên Set.of và mong tập mới được trả về | Tính trên bản sao có thể sửa |
| Dùng List có trùng như một đa tập với containsAll | containsAll kiểm tra hiện diện, không kiểm tra đủ số lần |
| So hai chuỗi toString để kiểm tra bằng nhau về tập | Dùng hợp đồng Set.equals khi thứ tự không thuộc yêu cầu |
| Trộn các tiêu chí so sánh khác nhau | Chuẩn hóa hoặc chọn kiểu khóa và quan hệ tương thích |
| Cho rằng một lời gọi collection là hằng số | Tính số phần tử duyệt và chi phí kiểm tra hiện diện |
| Nghĩ bản sao Set là bản sao sâu của phần tử | Xác định riêng quyền sửa đối tượng phần tử |

### Bài 1: Tính bốn nhóm

Cho A = {1, 2, 3, 4}, B = {3, 4, 5}.

- Hợp: {1, 2, 3, 4, 5}.
- Giao: {3, 4}.
- A − B: {1, 2}.
- B − A: {5}.

Hai tập ban đầu không là tập con của nhau. Giao là tập con của cả hai.

### Bài 2: RetainAll false

A = {1, 2}, B = {1, 2, 3}. Một bản sao của A gọi retainAll(B).

Không có phần tử bị xóa nên false. Kết quả vẫn {1, 2}; không được kết luận giao rỗng.

### Bài 3: Kiểm tra tập con thực sự

A có hai phần tử, B có ba phần tử. Có đủ để kết luận A là tập con thực sự của B không?

Không. Cần thêm B.containsAll(A). Kích thước nhỏ hơn không bảo đảm hiện diện.

### Bài 4: List yêu cầu lặp

Set có {A}; List được hỏi là [A, A]. containsAll trả true có chứng minh có đủ hai lần A không?

Không. Đây chỉ là kiểm tra mọi giá trị hỏi đều hiện diện. Muốn so số lượng cần mô hình đếm.

### Bài 5: Đổi chiều hiệu trong đối chiếu

A là nhóm mã hiện tại, B là nhóm mã mong muốn. Muốn biết mã cần bổ sung dùng B − A; muốn biết mã cần bỏ dùng A − B. Đảo chiều sẽ đổi ý nghĩa kết quả.

### Bài 6: Tập kết quả có thứ tự

Một hàm trả LinkedHashSet được sao chép từ HashSet không có bảo đảm thứ tự nguồn. Có thể tuyên bố nó giữ đúng thứ tự thêm ban đầu của dữ liệu không?

Không. Nó chỉ giữ thứ tự gặp được khi duyệt nguồn và thêm vào kết quả. Bảo đảm không có ở nguồn không tự xuất hiện sau một phép sao chép.

### Bài 7: Chỉ cần biết có phần chung

Nếu câu hỏi chỉ là “A và B có giao nhau không”, có thể dùng `!Collections.disjoint(a, b)` với dữ liệu tương thích. Không cần nhất thiết tạo một Set giao nếu không dùng các thành viên của nó.

Trước mỗi phép tính, hãy xác định **phía bắt đầu, phía đối chiếu, tập có được sửa không và kết quả cần là tập hay boolean**. Bốn câu hỏi này giúp dùng đúng các thao tác collection để biểu diễn các phép toán tập hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="set-005">Loại trùng nhưng bảo toàn thứ tự mong muốn</a>

<details>
<summary>Xem chi tiết</summary>

# Loại trùng nhưng bảo toàn thứ tự mong muốn

“Loại trùng” mới trả lời cần giữ bao nhiêu đại diện cho một nhóm dữ liệu tương đương. Nó chưa trả lời những đại diện đó phải xuất hiện theo thứ tự nào.

Với đầu vào:

~~~text
B, A, B, C, A
~~~

có ít nhất ba kết quả hợp lý tùy yêu cầu:

| Quy tắc | Kết quả |
|---|---|
| Giữ lần xuất hiện đầu tiên, theo thứ tự gặp đầu tiên | B, A, C |
| Giữ theo vị trí xuất hiện cuối cùng | B, C, A |
| Các giá trị duy nhất theo thứ tự chữ cái | A, B, C |

Cả ba đều không có phần tử trùng, nhưng không thay thế cho nhau. Chọn một `Set` bất kỳ rồi thấy đủ thành viên chưa chứng minh rằng thứ tự đúng.

Bài này giải thích cách kết hợp tiêu chí trùng, lựa chọn đại diện và thứ tự đầu ra; các ví dụ chạy với Java 17 trở lên.

## 1. Tách ba quyết định trước khi chọn cấu trúc

### 1.1. Thế nào là trùng?

Hai chuỗi có thể được xem là trùng theo nội dung chính xác. Hai bản ghi có thể được xem là trùng theo một mã nghiệp vụ, dù các trường còn lại khác nhau.

Một quy tắc chuẩn hóa cũng có thể được áp dụng trước khi so sánh, chẳng hạn xử lý khoảng trắng hoặc quy ước hoa/thường. Khi đó, phải nói rõ khóa chuẩn hóa là gì và có giữ nguyên dữ liệu hiển thị gốc hay không.

`LinkedHashSet` và `HashSet` dùng hợp đồng `equals/hashCode` để quản lý thành viên. `TreeSet` dựa vào phép so sánh thứ tự. Không nên chuyển cấu trúc mà chưa kiểm tra liệu định nghĩa trùng có bị đổi theo không.

### 1.2. Giữ đại diện nào trong nhóm trùng?

Nếu hai bản ghi cùng mã nhưng khác tên, có thể cần giữ:

- Bản ghi đầu tiên.
- Bản ghi cuối cùng.
- Bản ghi có phiên bản mới nhất.
- Một kết quả được tổng hợp từ cả nhóm.

Đây là quy tắc chọn dữ liệu, không chỉ là quy tắc thứ tự. Một set loại trùng toàn đối tượng không tự biết cần chọn theo khóa hay phiên bản.

### 1.3. Sắp các đại diện theo thứ tự nào?

Có thể cần thứ tự gặp đầu tiên, gặp cuối cùng, tăng dần theo khóa hoặc một tiêu chí hiển thị.

Ví dụ, “giữ bản ghi cuối cùng nhưng giữ thứ tự mã được gặp lần đầu” hoàn toàn khác “giữ bản ghi cuối cùng và sắp theo lần gặp cuối”.

Nên diễn đạt hợp đồng đủ cả ba phần:

~~~text
trung theo key
giu du lieu cua lan cuoi
sap cac key theo lan xuat hien dau tien
~~~

Một dòng yêu cầu như vậy giúp chọn thuật toán rõ ràng hơn câu chung chung “đưa vào set để loại trùng”.

## 2. Giữ thứ tự xuất hiện đầu tiên bằng LinkedHashSet

`LinkedHashSet` vừa theo dõi tính duy nhất, vừa duy trì thứ tự chèn để dùng khi duyệt.

Đoạn thân phương thức minh họa:

~~~java
List<String> input = List.of("B", "A", "B", "C", "A");
Set<String> unique = new LinkedHashSet<>(input);
List<String> result = new ArrayList<>(unique);
~~~

Kết quả là `[B, A, C]`.

### 2.1. Cơ chế khi duyệt đầu vào

| Phần tử đang đọc | Đã có chưa? | Thứ tự trong LinkedHashSet sau bước này |
|---|---|---|
| B | Chưa | B |
| A | Chưa | B, A |
| B | Có | B, A |
| C | Chưa | B, A, C |
| A | Có | B, A, C |

Một phần tử mới được đưa vào cuối thứ tự chèn. Thêm lại phần tử tương đương đã có không tạo thành viên mới và không đưa nó về cuối.

Đó là bảo đảm của [LinkedHashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html): chèn lại một thành viên hiện có không thay đổi thứ tự chèn.

### 2.2. Thêm lại không phải “vừa sử dụng gần nhất”

Sau khi có `[B, A, C]`, gọi `add("A")` trả `false` và thứ tự vẫn là B, A, C.

Không dùng `LinkedHashSet.add` như một lệnh tự động đánh dấu “phần tử vừa xuất hiện nên chuyển về cuối”. Nếu cần hành vi đó, phải thực hiện một thuật toán khác, như loại bỏ rồi thêm lại.

### 2.3. Vì sao không dùng HashSet rồi đổi sang List?

`HashSet` không có hợp đồng giữ thứ tự chèn. Theo [HashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html), thứ tự duyệt không được bảo đảm và có thể thay đổi.

Nếu dùng:

~~~java
List<String> result = new ArrayList<>(new HashSet<>(input));
~~~

kết quả có các thành viên duy nhất, nhưng không được dựa vào nó để bảo toàn thứ tự đầu vào.

Một lần chạy tình cờ in đúng B, A, C không làm thành một bảo đảm. Không viết kiểm thử hay luồng nghiệp vụ dựa vào thứ tự đã quan sát của một `HashSet`.

### 2.4. LinkedHashSet chỉ giữ thứ tự mà nó thực sự nhận được

Nếu nguồn ban đầu là một cấu trúc không có hợp đồng thứ tự, constructor `LinkedHashSet` ghi nhận thứ tự duyệt của nguồn tại lúc sao chép. Nó không khôi phục được lịch sử chèn trước đó đã không còn được cung cấp.

Đầu vào có thứ tự là một điều kiện của bài toán “giữ thứ tự xuất hiện”. Nếu yêu cầu kết quả lặp lại ổn định giữa các lần chạy, cần một nguồn có thứ tự xác định hoặc một bước sắp xếp theo tiêu chí đầy đủ.

## 3. Giữ thứ tự xuất hiện cuối cùng

Nếu cần kết quả theo vị trí xuất hiện cuối, có thể đi từ trái sang phải, mỗi lần xóa thành viên cũ rồi thêm lại:

~~~java
Set<String> lastOrder = new LinkedHashSet<>();
for (String value : input) {
    lastOrder.remove(value);
    lastOrder.add(value);
}
~~~

Với đầu vào B, A, B, C, A:

| Đọc đến | Thứ tự sau xóa cũ và thêm lại |
|---|---|
| B | B |
| A | B, A |
| B | A, B |
| C | A, B, C |
| A | B, C, A |

Mỗi phần tử đang giữ nằm ở vị trí tương ứng với lần gặp gần nhất của nó trong phần đầu vào đã duyệt. Kết thúc vòng lặp, thứ tự là thứ tự các lần xuất hiện cuối cùng.

### 3.1. Chọn đại diện cuối và đổi vị trí là hai tác động

Với các giá trị bằng nhau nhưng là đối tượng khác nhau, xóa cũ rồi thêm lại còn có thể thay đối tượng đại diện bằng đối tượng của lần hiện tại.

Nếu chỉ gọi `add` mà không xóa, phần tử tương đương bị từ chối và đại diện đã giữ không được tự cập nhật thành dữ liệu mới.

Vì vậy, hãy kiểm tra xem mong muốn là:

- Chỉ thay dữ liệu đại diện nhưng giữ vị trí cũ.
- Hay thay dữ liệu đại diện và chuyển vị trí theo lần cuối.

Với dữ liệu nghiệp vụ loại trùng theo khóa, một `Map` thường thể hiện hai lựa chọn này rõ hơn; phần sau minh họa.

### 3.2. Không gọi thao tác này trong lúc đang duyệt cùng set

Vòng lặp trên duyệt một nguồn đầu vào riêng rồi thay đổi tập kết quả. Nó không duyệt `lastOrder` trong khi xóa/thêm vào chính nó qua những lời gọi rời.

Giữ nguồn và kết quả tách biệt giúp tránh làm mất vị trí duyệt và tránh các thay đổi cấu trúc không hợp lệ với bộ lặp. Bài tập trung vào một luồng, không coi cặp `remove` rồi `add` là thao tác nguyên tử giữa nhiều luồng.

## 4. Muốn thứ tự sắp xếp: TreeSet hoặc loại trùng rồi sắp List

### 4.1. TreeSet không giữ thứ tự đầu vào

Với chuỗi B, A, B, C, A:

~~~java
Set<String> sorted = new TreeSet<>(input);
~~~

kết quả duyệt là A, B, C theo thứ tự tự nhiên của chuỗi.

Điều này phù hợp nếu yêu cầu là các thành viên theo thứ tự sắp xếp, chứ không phải bảo toàn thứ tự gặp. Tài liệu [TreeSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html) mô tả việc dùng thứ tự tự nhiên hoặc `Comparator` để tổ chức cây.

Với chuỗi, thứ tự tự nhiên của Java không tự đồng nghĩa với thứ tự từ điển của mọi ngôn ngữ. Nếu ứng dụng cần quy tắc địa phương hóa, phải thiết kế bộ so sánh tương ứng thay vì mặc định thứ tự Unicode đáp ứng yêu cầu.

### 4.2. Bộ so sánh của TreeSet còn quyết định phần tử nào được giữ

Một bẫy là dùng comparator chỉ so một thuộc tính như độ dài chuỗi:

~~~java
Set<String> byLength = new TreeSet<>(Comparator.comparingInt(String::length));
~~~

Nếu thêm pear và plum, cả hai cùng dài 4 nên comparator trả 0. Cây coi chúng tương đương và chỉ giữ một phần tử, dù `"pear".equals("plum")` là false.

Nếu yêu cầu chỉ là “các từ khác nhau về nội dung, sắp theo độ dài”, cách trên đã đổi tiêu chí loại trùng và làm mất một từ cần giữ.

Thứ tự của `TreeSet` phải nhất quán với `equals` để đáp ứng đầy đủ hợp đồng chung của `Set`. Comparator chỉ theo độ dài trong ví dụ là minh họa một thiết kế không đáp ứng yêu cầu đó đối với chuỗi.

### 4.3. Tách loại trùng khỏi thứ tự hiển thị khi hai tiêu chí khác nhau

Có thể loại trùng theo `equals/hashCode` trước, rồi sắp danh sách kết quả:

~~~java
List<String> result = new ArrayList<>(new LinkedHashSet<>(words));
result.sort(Comparator.comparingInt(String::length)
        .thenComparing(Comparator.naturalOrder()));
~~~

Với `[pear, fig, plum, fig]`, kết quả là `[fig, pear, plum]`.

Cả pear và plum được giữ vì việc bằng độ dài không còn là lý do loại trùng. So sánh theo tên ở bước thứ hai cũng định nghĩa rõ thứ tự của các từ cùng độ dài.

Nếu chỉ dùng comparator độ dài khi sắp một `List`, các phần tử bằng nhau theo comparator không bị loại bỏ. Đây là khác biệt giữa **sắp xếp danh sách** và **dùng thứ tự để xác định thành viên của TreeSet**.

Nếu không muốn tự quy định thứ tự phụ, sắp xếp ổn định có thể giữ thứ tự trước đó của các phần tử cùng tiêu chí. Nhưng khi nguồn không có thứ tự ổn định và kết quả phải lặp lại được, một tiêu chí phụ đầy đủ thường cần thiết.

## 5. Loại trùng theo khóa nghiệp vụ, vẫn giữ đúng đại diện và thứ tự

Giả sử đầu vào gồm các bản ghi:

~~~text
B:b1, A:a1, B:b2, C:c1, A:a2
~~~

Phần trước dấu hai chấm là khóa, phần sau là dữ liệu. Cùng khóa B có hai bản ghi khác nhau.

Nếu một record có hai thành phần `key` và `detail`, cách `equals` mặc định của record so cả hai thành phần. Tạo `LinkedHashSet<Row>` không tự biến nó thành “duy nhất theo key”; cả B:b1 và B:b2 vẫn khác nhau.

### 5.1. Giữ bản ghi đầu tiên theo khóa

Dùng một set chỉ để kiểm tra khóa, còn kết quả là một danh sách:

~~~java
Set<String> seenKeys = new HashSet<>();
List<Row> result = new ArrayList<>();

for (Row row : rows) {
    if (seenKeys.add(row.key())) {
        result.add(row);
    }
}
~~~

Kết quả: `[B:b1, A:a1, C:c1]`.

Set kiểm tra thành viên không cần có thứ tự, vì ta không dùng nó làm nguồn duyệt đầu ra. Danh sách kết quả nhận phần tử trực tiếp từ vòng duyệt có thứ tự.

Đây là một ví dụ quan trọng: có thể dùng `HashSet` trong thuật toán vẫn bảo toàn thứ tự, miễn thứ tự nằm ở cấu trúc kết quả và không được lấy lại từ việc duyệt `HashSet`.

### 5.2. Giữ bản ghi cuối, theo thứ tự khóa được gặp đầu tiên

Dùng `LinkedHashMap` mặc định và ghi đè giá trị theo khóa:

~~~java
Map<String, Row> byKey = new LinkedHashMap<>();
for (Row row : rows) {
    byKey.put(row.key(), row);
}
List<Row> result = new ArrayList<>(byKey.values());
~~~

Kết quả: `[B:b2, A:a2, C:c1]`.

B và A nhận dữ liệu mới, nhưng vị trí khóa vẫn theo lần chèn khóa đầu tiên: B, A, C. Thay giá trị của một khóa đã có không tự chuyển khóa đó về cuối trong `LinkedHashMap` mặc định.

Đây là chính sách thứ tự chèn, khác với chế độ thứ tự truy cập có thể được cấu hình ở `LinkedHashMap`. Bài đang dùng constructor mặc định, theo [LinkedHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

### 5.3. Giữ bản ghi cuối, theo vị trí xuất hiện cuối của khóa

Nếu mỗi lần đọc đều xóa khóa cũ rồi put lại:

~~~java
Map<String, Row> byKey = new LinkedHashMap<>();
for (Row row : rows) {
    byKey.remove(row.key());
    byKey.put(row.key(), row);
}
List<Row> result = new ArrayList<>(byKey.values());
~~~

kết quả là `[B:b2, C:c1, A:a2]`.

Dữ liệu đại diện vẫn là bản cuối. Khác biệt nằm ở việc khóa được đưa về cuối mỗi khi xuất hiện lại.

### 5.4. Bảng chính sách cho cùng một nguồn

| Chính sách | Dữ liệu đầu ra |
|---|---|
| Bản đầu, thứ tự khóa gặp đầu | B:b1, A:a1, C:c1 |
| Bản cuối, thứ tự khóa gặp đầu | B:b2, A:a2, C:c1 |
| Bản cuối, thứ tự khóa gặp cuối | B:b2, C:c1, A:a2 |

Không chọn tên “deduplicate” cho một helper rồi để người gọi đoán hàng nào trong bảng là hợp đồng của nó. Tên và tài liệu nên nói rõ quy tắc chọn đại diện và thứ tự.

Nếu cần “phiên bản mới nhất”, lần xuất hiện cuối chỉ tương đương phiên bản mới nhất khi nguồn đã đáp ứng điều kiện thứ tự thích hợp. Nếu dữ liệu đến không theo phiên bản, phải so sánh phiên bản theo quy tắc riêng.

## 6. Stream: distinct giữ thứ tự khi nguồn có thứ tự

### 6.1. distinct trên stream có thứ tự

Với nguồn `List` có thứ tự:

~~~java
List<String> result = input.stream().distinct().toList();
~~~

`distinct` dùng `equals` để xác định phần tử trùng. Với ordered stream, nó giữ đại diện xuất hiện đầu tiên theo **thứ tự gặp (encounter order)**, như mô tả tại [Stream.distinct Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#distinct()).

Kết quả của nguồn B, A, B, C, A là `[B, A, C]`.

“Có thứ tự” ở đây không có nghĩa các giá trị đã được sắp tăng dần. Nó có nghĩa nguồn và các bước trước đó xác định một thứ tự gặp mà pipeline cần tôn trọng.

### 6.2. Đích collect cũng cần có hợp đồng thứ tự

Nếu muốn một set theo thứ tự gặp:

~~~java
Set<String> result = input.stream()
        .collect(Collectors.toCollection(LinkedHashSet::new));
~~~

Collector được chỉ định tạo `LinkedHashSet` và tích lũy theo thứ tự gặp. Trong khi đó, `Collectors.toSet()` là collector không có bảo đảm thứ tự, không có hợp đồng phải trả về `LinkedHashSet`.

Theo [Collectors Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#toCollection(java.util.function.Supplier)), `toCollection` cho phép xác định loại collection cần dùng. Nếu yêu cầu có thứ tự, không chỉ kiểm tra bước `distinct` rồi bỏ qua loại kết quả cuối cùng.

### 6.3. Parallel không tự làm mất thứ tự, nhưng unordered có ý nghĩa riêng

Một ordered stream chạy song song vẫn phải đáp ứng hợp đồng ổn định của `distinct` nếu không bỏ ràng buộc thứ tự. Ví dụ hoàn chỉnh kiểm tra kết quả của pipeline song song bằng pipeline tuần tự.

Tuy nhiên, giữ ổn định khi distinct song song có thể cần phối hợp và bộ đệm đáng kể. Không mặc định gọi `parallelStream` sẽ nhanh hơn cho bài toán loại trùng.

Nếu nguồn không có encounter order, hoặc gọi `unordered()`, không còn bảo đảm giữ đúng đại diện đầu tiên theo một thứ tự mong muốn trước đó. `unordered()` cho phép bỏ ràng buộc thứ tự; nó không phải lời yêu cầu tạo một thứ tự ngẫu nhiên cụ thể.

### 6.4. Không dùng trạng thái dùng chung để giả lập distinct theo khóa mà bỏ qua quy tắc stream

Một predicate như “đưa key vào set bên ngoài rồi trả kết quả add” có trạng thái thay đổi. Nếu dùng với pipeline song song, còn phát sinh vấn đề đồng bộ và việc luồng nào giữ đại diện trước.

Ngay cả khi thay set bằng một set đồng thời, “luồng nào thêm key trước” không tự tương đương “bản ghi nào đứng trước trong nguồn”. Với yêu cầu giữ đại diện theo encounter order, vòng lặp tuần tự rõ ràng hoặc một phép thu thập có hợp đồng phù hợp thường dễ đánh giá hơn.

Các ví dụ trong bài không sửa chung một collection từ nhiều nhánh `forEach` song song.

## 7. Giữ thứ tự qua bước chuyển giao kết quả

### 7.1. List là lựa chọn rõ khi người gọi cần một dãy

Nếu kết quả cần hiển thị tuần tự hoặc truy cập vị trí, có thể chuyển:

~~~java
List<String> result = new ArrayList<>(uniqueInOrder);
~~~

Constructor đọc thứ tự duyệt của nguồn và giữ nó trong danh sách mới. Nếu nguồn là `LinkedHashSet` đã được xây đúng, danh sách giữ thứ tự đó.

Danh sách mới có thể sửa cấu trúc; việc thêm hoặc xóa trong danh sách không tự sửa cấu trúc set nguồn. Tuy nhiên, các đối tượng phần tử vẫn có thể là cùng tham chiếu; đây không phải sao chép sâu.

### 7.2. Kết quả không sửa được cũng phải giữ hợp đồng thứ tự

Với kết quả không chứa `null`, có thể tạo:

~~~java
List<String> result = List.copyOf(uniqueInOrder);
~~~

`List.copyOf` giữ thứ tự lặp của collection nguồn và không cho sửa danh sách kết quả, theo [List.copyOf Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)).

Ngược lại, không dùng `Set.copyOf` như một lời hứa giữ thứ tự chèn. Các set do factory này tạo không có bảo đảm thứ tự duyệt như `LinkedHashSet`, theo [hợp đồng các set không sửa được](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#unmodifiable).

Nếu phải trả một set không cho sửa mà vẫn giữ thứ tự duyệt của bản sao, có thể bọc một `LinkedHashSet` mới tạo:

~~~java
Set<String> result =
        Collections.unmodifiableSet(new LinkedHashSet<>(input));
~~~

Lớp bọc là view chỉ đọc trên set hậu thuẫn. Trong cách viết này, set hậu thuẫn là một bản sao mới và không được đưa cho nơi khác sửa. Nếu bọc trực tiếp một set đang được chia sẻ rồi nguồn đó bị sửa về sau, view sẽ phản ánh nguồn. Xem [Collections.unmodifiableSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableSet(java.util.Set)).

### 7.3. Chính sách null phải tương thích qua toàn chuỗi

`LinkedHashSet` cho phép một phần tử `null` và giữ vị trí chèn của nó như một thành viên. Ví dụ `[B, null, A, null, B]` loại trùng giữ lần đầu thành `[B, null, A]`.

Nhưng `List.copyOf` không chấp nhận phần tử `null`. Một pipeline làm đúng thứ tự ở bước đầu vẫn có thể bị từ chối ở bước chuyển kết quả.

Hãy chọn rõ một trong các hướng: dữ liệu không được có `null`, chủ động xử lý/bỏ nó theo quy tắc nghiệp vụ, hoặc dùng kết quả có chính sách phù hợp. Không loại bỏ `null` âm thầm chỉ để phương thức chuyển đổi chạy được.

### 7.4. Set.equals không kiểm tra thứ tự

Hai `LinkedHashSet` duyệt lần lượt A, B và B, A vẫn có thể bằng nhau theo `Set.equals`, vì hai tập hợp có cùng thành viên.

Nếu yêu cầu kiểm tra cả thứ tự, có thể so sánh các danh sách thu từ thứ tự duyệt:

~~~java
new ArrayList<>(actualSet).equals(expectedList)
~~~

Đây là một cách kiểm chứng thứ tự, không đổi hợp đồng `equals` của set. Đừng dùng một phép so sánh tập hợp để xác nhận yêu cầu về dãy.

## 8. Chương trình hoàn chỉnh: nhiều chính sách trên cùng dữ liệu

Chương trình kiểm tra thứ tự gặp đầu, gặp cuối, thứ tự sắp xếp, stream, khóa nghiệp vụ và việc chuyển kết quả.

### 8.1. Mã nguồn

Lưu thành `OrderedDedupDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class OrderedDedupDemo {
    record Row(String key, String detail) {
        Row {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(detail, "detail");
        }

        @Override
        public String toString() {
            return key + ":" + detail;
        }
    }

    static <E> List<E> firstOccurrences(List<? extends E> input) {
        Set<E> unique = new LinkedHashSet<>(input);
        return new ArrayList<>(unique);
    }

    static <E> List<E> lastOccurrences(List<? extends E> input) {
        Set<E> unique = new LinkedHashSet<>();
        for (E element : input) {
            unique.remove(element);
            unique.add(element);
        }
        return new ArrayList<>(unique);
    }

    static List<Row> firstRowsByKey(List<Row> input) {
        Set<String> seenKeys = new HashSet<>();
        List<Row> result = new ArrayList<>();
        for (Row row : input) {
            if (seenKeys.add(row.key())) {
                result.add(row);
            }
        }
        return result;
    }

    public static void main(String[] args) {
        List<String> input = List.of("B", "A", "B", "C", "A");
        System.out.println("input: " + input);
        System.out.println("first occurrences: " + firstOccurrences(input));
        System.out.println("last occurrences: " + lastOccurrences(input));
        System.out.println("natural sorted: " + new TreeSet<>(input));

        Set<String> insertionOrder = new LinkedHashSet<>(input);
        boolean added = insertionOrder.add("A");
        System.out.printf("add existing A: returned=%s, values=%s%n",
                added, insertionOrder);
        insertionOrder.remove("A");
        insertionOrder.add("A");
        System.out.println("remove then add A: " + insertionOrder);

        List<String> streamDistinct = input.stream().distinct().toList();
        Set<String> collected = input.stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> parallelDistinct = input.parallelStream().distinct().toList();
        System.out.println("ordered stream distinct: " + streamDistinct);
        System.out.println("collected LinkedHashSet: " + collected);
        System.out.println("ordered parallel matches: "
                + streamDistinct.equals(parallelDistinct));

        List<String> words = List.of("pear", "fig", "plum", "fig");
        List<String> uniqueWords = firstOccurrences(words);
        uniqueWords.sort(Comparator.comparingInt(String::length)
                .thenComparing(Comparator.naturalOrder()));
        Set<String> lengthOnly = new TreeSet<>(Comparator.comparingInt(String::length));
        lengthOnly.addAll(words);
        System.out.println("unique then length/name sort: " + uniqueWords);
        System.out.println("TreeSet length-only: " + lengthOnly);

        List<Row> rows = List.of(
                new Row("B", "b1"),
                new Row("A", "a1"),
                new Row("B", "b2"),
                new Row("C", "c1"),
                new Row("A", "a2"));
        System.out.println("record-equality unique count: "
                + new LinkedHashSet<>(rows).size());
        System.out.println("first row per key: " + firstRowsByKey(rows));

        Map<String, Row> lastValueFirstKeyOrder = new LinkedHashMap<>();
        Map<String, Row> lastValueLastKeyOrder = new LinkedHashMap<>();
        for (Row row : rows) {
            lastValueFirstKeyOrder.put(row.key(), row);
            lastValueLastKeyOrder.remove(row.key());
            lastValueLastKeyOrder.put(row.key(), row);
        }
        System.out.println("last row, first key order: "
                + new ArrayList<>(lastValueFirstKeyOrder.values()));
        System.out.println("last row, last key order: "
                + new ArrayList<>(lastValueLastKeyOrder.values()));

        Set<String> orderAB = new LinkedHashSet<>(List.of("A", "B"));
        Set<String> orderBA = new LinkedHashSet<>(List.of("B", "A"));
        System.out.println("set equality ignores order: " + orderAB.equals(orderBA));
        System.out.println("list equality sees order: "
                + new ArrayList<>(orderAB).equals(new ArrayList<>(orderBA)));

        List<String> nullableInput = Arrays.asList("B", null, "A", null, "B");
        List<String> nullableResult = firstOccurrences(nullableInput);
        System.out.println("nullable first occurrences: " + nullableResult);
        try {
            List.copyOf(nullableResult);
        } catch (NullPointerException exception) {
            System.out.println("List.copyOf nullable: NullPointerException");
        }
        List<String> snapshot = List.copyOf(firstOccurrences(input));
        try {
            snapshot.add("D");
        } catch (UnsupportedOperationException exception) {
            System.out.println("snapshot.add: UnsupportedOperationException");
        }
        System.out.println("input unchanged: " + input);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 OrderedDedupDemo.java
java OrderedDedupDemo
~~~

### 8.2. Kết quả stdout

~~~text
input: [B, A, B, C, A]
first occurrences: [B, A, C]
last occurrences: [B, C, A]
natural sorted: [A, B, C]
add existing A: returned=false, values=[B, A, C]
remove then add A: [B, C, A]
ordered stream distinct: [B, A, C]
collected LinkedHashSet: [B, A, C]
ordered parallel matches: true
unique then length/name sort: [fig, pear, plum]
TreeSet length-only: [fig, pear]
record-equality unique count: 5
first row per key: [B:b1, A:a1, C:c1]
last row, first key order: [B:b2, A:a2, C:c1]
last row, last key order: [B:b2, C:c1, A:a2]
set equality ignores order: true
list equality sees order: false
nullable first occurrences: [B, null, A]
List.copyOf nullable: NullPointerException
snapshot.add: UnsupportedOperationException
input unchanged: [B, A, B, C, A]
~~~

### 8.3. Đối chiếu các nhóm kết quả

Ba dòng đầu ra về first, last và sorted có cùng ba thành viên nhưng các thứ tự khác nhau. Chúng kiểm tra ba hợp đồng, không phải ba cách viết được coi là tương đương.

`add existing A` trả false và không đổi thứ tự. Chỉ khi xóa rồi thêm A, vị trí của A mới chuyển về cuối.

Hai cách stream tạo kết quả theo thứ tự gặp đầu. Pipeline song song có nguồn ordered vẫn cho cùng dãy; đây là kiểm tra tính đúng theo hợp đồng, không phải phép đo nhanh/chậm.

Nhóm từ giữ được cả pear và plum khi loại trùng theo nội dung rồi sắp danh sách. `TreeSet` chỉ so độ dài lại giữ một đại diện cho độ dài 4, nên mất plum đối với yêu cầu này.

Record `Row` dùng `equals/hashCode` mặc định theo cả key và detail. Vì năm bản ghi khác nhau ở tổ hợp hai trường, set toàn record vẫn có kích thước 5. Helper theo key và hai map sau đó mới biểu diễn đúng ba chính sách lựa chọn bản ghi.

Hai phép equality chứng minh tại sao chỉ kiểm tra tập thành viên chưa đủ để kiểm tra thứ tự. Nhóm null và snapshot kiểm chứng thêm hợp đồng dữ liệu/quyền sửa ở bước trả kết quả.

Các cấu trúc kết quả được tạo riêng; danh sách input vẫn là `[B, A, B, C, A]`. Ví dụ không làm thay đổi nguồn để loại trùng.

## 9. Chi phí và tính ổn định của thuật toán

Gọi n là số phần tử đầu vào, u là số thành viên hoặc số khóa duy nhất.

| Cách làm | Công việc dự kiến | Bộ nhớ phụ chính |
|---|---|---|
| LinkedHashSet giữ lần đầu | O(n) khi hash phân bố tốt và phép hash/equals có chi phí phù hợp | O(u) |
| Xóa rồi thêm để giữ lần cuối | O(n) theo cùng giả định băm | O(u) |
| Set khóa + List kết quả đầu tiên | O(n), cộng chi phí lấy khóa/hash/equals | O(u) |
| LinkedHashMap theo khóa | O(n) theo giả định băm phù hợp | O(u) |
| TreeSet | O(n log(u+1)) trong phân tích tổng quát, cộng chi phí so sánh | O(u) |
| Loại trùng rồi sắp List | O(n) dự kiến cho loại trùng, cộng O(u log u) so sánh khi sắp tổng quát | O(u) và vùng làm việc của sắp xếp |

Các giới hạn trên không có nghĩa mỗi phép băm, so sánh chuỗi hoặc trích khóa đều miễn phí. Nếu key dài hoặc việc chuẩn hóa tốn kém, cần tính công việc đó.

### 9.1. Tránh contains trên List cho từng phần tử nếu đầu vào lớn

Một cách đơn giản là giữ một danh sách kết quả và trước mỗi lần thêm gọi `result.contains(value)`. Nó có thể giữ thứ tự đầu tiên, nhưng mỗi lần kiểm tra có thể duyệt qua nhiều phần tử đã giữ; tổng có thể thành O(n²).

Set giúp tách việc kiểm tra thành viên khỏi thứ tự đầu ra với mô hình hiệu quả hơn khi dữ liệu phù hợp. Không cần chuyển mọi danh sách nhỏ sang một thuật toán phức tạp, nhưng phải nhận ra công việc bị lặp khi quy mô lớn.

### 9.2. Tính đúng của equals/hashCode vẫn là điều kiện

Nếu tiêu chí trùng dùng các trường có thể thay đổi trong lúc phần tử đang được giữ bởi set băm, kết quả có thể không còn được quản lý đúng theo mong muốn. Dùng khóa ổn định hoặc dữ liệu bất biến giúp làm hợp đồng rõ hơn.

Một trường hợp va chạm hash không có nghĩa hai phần tử trùng nhau; vẫn phải xét `equals`. Không loại bỏ dữ liệu chỉ vì so sánh riêng mã băm thấy bằng nhau.

### 9.3. Giữ thứ tự không có nghĩa là an toàn đa luồng

`LinkedHashSet` và `LinkedHashMap` thông thường không tự đồng bộ. Cặp xóa/thêm hoặc xóa/put trong thuật toán giữ lần cuối không tự nguyên tử.

Nếu nguồn bị sửa đồng thời, khái niệm “đầu tiên” hay “cuối cùng” còn phải được định nghĩa theo một trật tự nghiệp vụ rõ ràng. Các ví dụ dùng một nguồn hữu hạn ổn định và một luồng xây kết quả, ngoại trừ pipeline parallel có cơ chế thu thập do thư viện quản lý.

## 10. Các lỗi thường gặp

| Nhận định | Điều cần xem lại |
|---|---|
| Set nào cũng giữ thứ tự đầu vào | Chỉ các triển khai/hợp đồng cụ thể có bảo đảm đó |
| HashSet in đúng thứ tự một lần nên có thể dựa vào | Kết quả quan sát không thay thế hợp đồng |
| LinkedHashSet khôi phục lịch sử của một HashSet | Nó chỉ ghi nhận thứ tự nguồn đang cung cấp |
| Add phần tử cũ vào LinkedHashSet sẽ đưa nó về cuối | Add lại không đổi vị trí; xóa rồi thêm là thao tác khác |
| Giữ bản cuối luôn có nghĩa thứ tự theo lần cuối | Dữ liệu đại diện và thứ tự khóa là hai quyết định |
| TreeSet chỉ loại trùng rồi sắp theo equals | Comparator còn quyết định những phần tử bị coi là tương đương |
| Comparator độ dài giữ mọi từ khác nhau | Các từ cùng độ dài có thể bị gom thành một thành viên cây |
| distinct luôn giữ một “bản đầu” xác định | Cần encounter order; unordered stream không có bảo đảm đó |
| toSet giữ thứ tự của pipeline phía trước | Collector này không có hợp đồng thứ tự |
| Đổi sang Set.copyOf vẫn giữ thứ tự chèn | Không có bảo đảm đó |
| Set.equals true chứng minh thứ tự đúng | Nó kiểm tra thành viên, không kiểm tra dãy |
| Record có key nên tự loại trùng theo key | Equals mặc định còn xét các thành phần khác |
| Bản ghi đến sau chắc chắn mới hơn | Chỉ đúng nếu thứ tự nguồn thể hiện điều đó |
| Loại trùng không cần quyết định đại diện | Các bản ghi cùng khóa có thể mang dữ liệu khác nhau |

## 11. Bài tập áp dụng

### Bài 1: giữ lần đầu

Đầu vào `[C, A, C, B, A, D]`. Dùng `LinkedHashSet` theo thứ tự nguồn cho dãy nào?

**Giải thích:** `[C, A, B, D]`. Mỗi phần tử được xếp tại lần đầu được nhận vào set; lần trùng không chuyển vị trí.

### Bài 2: giữ theo lần cuối

Cùng đầu vào trên, mỗi bước xóa phần tử cũ rồi thêm lại. Kết quả là gì?

**Giải thích:** `[C, B, A, D]`. Các lần xuất hiện cuối có vị trí tương ứng C ở 2, B ở 3, A ở 4, D ở 5 nếu đếm chỉ số từ 0.

### Bài 3: chọn bản ghi và thứ tự độc lập

Dữ liệu `A:a1, B:b1, A:a2, C:c1`. LinkedHashMap mặc định, mỗi bước put theo key, cho values theo thứ tự nào?

**Giải thích:** `[A:a2, B:b1, C:c1]`. A có dữ liệu bản cuối nhưng vẫn đứng ở vị trí khóa gặp đầu tiên. Nếu xóa key rồi put lại ở mỗi bước, kết quả là `[B:b1, A:a2, C:c1]`.

### Bài 4: comparator không đủ

Cần giữ các từ khác nội dung và sắp theo độ dài. Dùng TreeSet chỉ có comparator độ dài có đáp ứng không?

**Giải thích:** Không nếu phải giữ nhiều từ cùng độ dài. Có thể loại trùng theo nội dung trước, sau đó sắp List; hoặc thiết kế một thứ tự đầy đủ phù hợp và nhất quán với định nghĩa thành viên.

### Bài 5: kiểm tra kết quả có thứ tự

Hai set đều có A và B, nhưng một set duyệt A, B và set còn lại duyệt B, A. Set.equals có phát hiện khác thứ tự không?

**Giải thích:** Không. Muốn kiểm tra yêu cầu thứ tự, so sánh dãy thu được khi duyệt với một danh sách kỳ vọng.

### Bài 6: nguồn đã mất thứ tự

Dữ liệu được đưa vào HashSet trước, sau đó mới đổi sang LinkedHashSet. Có bảo đảm khôi phục thứ tự ban đầu không?

**Giải thích:** Không. Cần giữ thứ tự ở nguồn ngay từ đầu hoặc còn thông tin khác đủ để tái tạo, chẳng hạn một khóa thứ tự đã được định nghĩa.

Một cách loại trùng đúng phải làm rõ **tiêu chí tương đương**, **đại diện được giữ** và **thứ tự đầu ra**. Sau khi ba điều đó được xác định, LinkedHashSet, List, TreeSet hoặc Map trở thành các công cụ cụ thể để thực hiện hợp đồng thay vì những lựa chọn thay thế cho việc định nghĩa yêu cầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="set-006">equals và hashCode không phụ thuộc thứ tự của Set</a>

<details>
<summary>Xem chi tiết</summary>

# equals và hashCode không phụ thuộc thứ tự của Set

## 1. Giá trị của một tập nằm ở các phần tử, không nằm ở thứ tự duyệt

Giả sử hai người chọn cùng ba thẻ `"java"`, `"sql"`, `"http"`. Người thứ nhất chọn theo thứ tự đó, người thứ hai chọn `"http"` trước rồi mới chọn hai thẻ còn lại. Nếu bài toán chỉ quan tâm các thẻ đã chọn, hai lựa chọn biểu diễn cùng một giá trị tập hợp.

Đó là ý nghĩa của phép bằng nhau trên `Set`: **hai tập có cùng các phần tử thì bằng nhau, bất kể thứ tự thêm hoặc thứ tự duyệt**, với điều kiện các tập và phần tử tuân thủ hợp đồng bằng nhau thông thường.

Ví dụ, cách viết toán học `{1, 2, 3}` và `{3, 2, 1}` chỉ hai tập bằng nhau. Nhưng hai danh sách `[1, 2, 3]` và `[3, 2, 1]` là hai dãy khác nhau vì vị trí có ý nghĩa đối với danh sách.

Điều này không có nghĩa mọi `Set` đều duyệt theo thứ tự ngẫu nhiên:

- `LinkedHashSet` có thể giữ thứ tự chèn.
- `TreeSet` duyệt theo thứ tự sắp xếp.
- `HashSet` không cam kết thứ tự duyệt.

Các đặc tính thứ tự này được mô tả trong [LinkedHashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html) và [HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html).

**Quy tắc duyệt** và **quy tắc bằng nhau của cả tập** giải quyết hai câu hỏi khác nhau. Một tập có thể có thứ tự duyệt ổn định nhưng `equals` vẫn bỏ qua thứ tự ấy.

Để làm việc đúng với tập, cần nối ba tầng suy nghĩ:

1. `equals` của phần tử xác định thế nào là cùng giá trị.
2. `equals` của `Set` xác định hai tập có cùng các giá trị không.
3. `hashCode` của `Set` phải phù hợp với phép bằng nhau đó.

Nếu tầng thứ hai bỏ qua thứ tự nhưng tầng thứ ba lại trộn mã băm theo vị trí, hai tập bằng nhau có thể cho mã băm khác nhau. Vì vậy, hợp đồng của `Set` quy định một công thức mã băm không phụ thuộc thứ tự. [Hợp đồng Set.equals và Set.hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

## 2. Điều kiện để hai Set bằng nhau

### 2.1. Cùng kiểu trừu tượng Set, cùng kích thước, cùng phần tử

Với một tập `a` khác `null`, ý nghĩa của `a.equals(b)` là:

1. `b` cũng phải là một `Set`.
2. Hai tập phải có cùng số phần tử.
3. Mọi phần tử của một tập đều phải có trong tập còn lại.

Không có điều kiện “phải cùng lớp triển khai” và cũng không có điều kiện “phần tử thứ nhất của tập này bằng phần tử thứ nhất của tập kia”.

Với các phần tử nguyên thông thường, bảng sau cho thấy sự khác biệt:

| a | b | a.equals(b) | Lý do |
| --- | --- | --- | --- |
| Tập `{1, 2, 3}` | Tập `{3, 1, 2}` | `true` | Cùng các phần tử |
| Tập `{1, 2, 3}` | Tập `{1, 2}` | `false` | Khác số phần tử |
| Tập `{1, 2, 3}` | Tập `{1, 2, 4}` | `false` | Cùng kích thước nhưng khác nội dung |
| Tập `{1, 2, 3}` | List `[1, 2, 3]` | `false` | Đối tượng bên phải không phải Set |
| Tập rỗng | Một Set rỗng khác | `true` | Cùng không có phần tử |
| Tập rỗng | `null` | `false` | null không phải một tập rỗng |

Dấu ngoặc nhọn trong bảng mô tả nội dung tập theo ý nghĩa toán học; đó không phải cú pháp tạo tập trong Java.

`AbstractSet` cung cấp cách triển khai nền cho phép so sánh này: nhận ra cùng tham chiếu, kiểm tra đối tượng là tập và kích thước tương ứng, rồi dùng kiểm tra bao hàm các phần tử. Đây là cơ chế hợp lý cho các tập có quy tắc nhận diện phần tử nhất quán; không phải phép so sánh từng cặp theo vị trí của hai bộ duyệt. [AbstractSet.equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSet.html#equals(java.lang.Object))

### 2.2. Cùng tham chiếu và cùng giá trị là hai khái niệm

```java
// Đoạn minh họa; các import có trong chương trình đầy đủ bên dưới.
Set<Integer> first = new LinkedHashSet<>(List.of(1, 2, 3));
Set<Integer> second = new LinkedHashSet<>(List.of(3, 2, 1));

System.out.println(first == second);      // false
System.out.println(first.equals(second)); // true
```

`==` ở đây hỏi hai biến có cùng tham chiếu đến một đối tượng không. Hai lần `new` tạo hai đối tượng tập khác nhau, nên kết quả là `false`.

`equals` hỏi về giá trị tập hợp. Hai đối tượng riêng vẫn có thể bằng nhau vì cùng chứa `1`, `2`, `3`.

Không dùng `==` để kiểm tra hai kết quả truy vấn có cùng nội dung tập. Ngược lại, khi muốn biết hai biến có phải đang chia sẻ đúng một tập có thể sửa đổi không, `==` là một câu hỏi khác và có thể có giá trị chẩn đoán.

### 2.3. Khác cách triển khai vẫn có thể bằng nhau

Một `HashSet<Integer>`, một `LinkedHashSet<Integer>`, một `TreeSet<Integer>` dùng thứ tự tự nhiên và một tập tạo bằng `Set.of(...)` có thể bằng nhau nếu cùng chứa các số nguyên tương ứng.

Khả năng sửa đổi, cách lưu trữ và thứ tự duyệt không phải thành phần của phép bằng nhau này. Chúng vẫn là những khác biệt quan trọng khi sử dụng tập, nhưng không làm hai tập có cùng phần tử trở thành khác giá trị.

Đây là lý do không nên kiểm tra `getClass()` trước khi so sánh các tập thông thường. Một điều kiện như `a.getClass() == b.getClass()` có thể loại bỏ hai tập vốn bằng nhau theo hợp đồng của `Set`.

Kiểu khai báo của biến cũng không thay đổi đối tượng phía sau. Nếu một đối tượng `HashSet` được tham chiếu qua biến kiểu `Collection` hoặc `Object`, lời gọi `equals` vẫn dùng cách triển khai của đối tượng thực tế.

### 2.4. containsAll một chiều chưa đủ

Giả sử `large = {1, 2, 3}` và `small = {1, 2}`:

```java
// Đoạn minh họa.
Set<Integer> large = Set.of(1, 2, 3);
Set<Integer> small = Set.of(1, 2);

large.containsAll(small); // true
large.equals(small);      // false
```

`containsAll` ở đây chỉ chứng minh `small` là tập con của `large`. Nó không loại trừ việc `large` còn chứa phần tử khác.

Khi cần hỏi hai `Set` bằng nhau hay không, dùng trực tiếp `equals`. Không tự thay thế bằng một chiều `containsAll`, và cũng không cần tự viết hai lượt bao hàm nếu dữ liệu đã là các `Set` đúng hợp đồng.

## 3. Vì sao hashCode của Set là tổng mã băm phần tử?

### 3.1. Công thức của cả tập

Mã băm của `Set` được xác định bằng tổng mã băm của các phần tử; đóng góp của phần tử `null` là `0`.

Có thể hình dung công thức:

```text
hash(Set) = tổng hash(element) của mọi phần tử trong tập

Nếu element là null:
    phần đóng góp của element = 0
```

Đoạn mã sau chỉ minh họa cách tính theo hợp đồng, không phải yêu cầu phải viết một hàm thay cho `Set.hashCode()`:

```java
static int sumElementHashes(Set<?> values) {
    int result = 0;
    for (Object value : values) {
        result += value == null ? 0 : value.hashCode();
    }
    return result;
}
```

Trong mã sử dụng tập, cách gọi bình thường vẫn là:

```java
int hash = values.hashCode();
```

Với `Integer`, mã băm chính là giá trị số nguyên được biểu diễn. Vì vậy:

```text
hash({1, 2, 3}) = 1 + 2 + 3 = 6
hash({3, 2, 1}) = 3 + 2 + 1 = 6
```

Đổi thứ tự các số hạng không đổi tổng. Đó là cơ chế khiến công thức phù hợp với phép bằng nhau không xét thứ tự của tập. [Set.hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#hashCode()), [Integer.hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#hashCode())

### 3.2. Không đưa vị trí phần tử vào công thức của Set

Một công thức kiểu `result = 31 * result + elementHash` trộn kết quả trước với phần tử tiếp theo. Nó có thể phụ thuộc thứ tự, nên không phải công thức hợp đồng cho `Set`.

Sắp xếp tập rồi băm chuỗi hiển thị cũng không phải cách thay thế `Set.hashCode()`. Chuỗi hiển thị phục vụ con người; nó có thể chịu ảnh hưởng của định dạng, cách biểu diễn phần tử và thứ tự xuất dữ liệu. Mã băm của tập đã có quy tắc riêng.

Nếu đang viết một lớp có trường là `Set`, hãy dùng `equals` và `hashCode` của trường đó theo ý nghĩa tập hợp. Nếu đang tự triển khai `Set`, phải giữ hợp đồng của `Set`; không chọn công thức dựa trên thứ tự chỉ vì bộ duyệt của lớp có thứ tự.

### 3.3. equals true bắt buộc cùng hashCode; cùng hashCode chưa đủ

Hợp đồng chung yêu cầu:

```text
a.equals(b) == true
    ⇒ a.hashCode() == b.hashCode()
```

Chiều ngược lại không được bảo đảm:

```text
a.hashCode() == b.hashCode()
    ⇏ a.equals(b) == true
```

Ví dụ hoàn toàn hợp lệ:

```text
A = {1, 4}       hash(A) = 5
B = {2, 3}       hash(B) = 5

A.equals(B) = false
```

Hai tập này có cùng kích thước và cùng tổng mã băm, nhưng khác các phần tử. Đây là **va chạm mã băm (hash collision)**. Một cấu trúc băm phải phân biệt được các khóa không bằng nhau dù chúng có cùng mã băm; không được coi mã băm là danh tính duy nhất. [Object.hashCode: quan hệ với equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#hashCode())

Do đó, đoạn sau là cách so sánh sai:

```java
// Sai nếu dùng để kết luận hai tập bằng nhau:
boolean same = first.hashCode() == second.hashCode();
```

Nếu cần so sánh giá trị tập, viết `first.equals(second)`. Mã băm giúp cấu trúc băm tổ chức và tìm dữ liệu; nó không thay thế phép bằng nhau.

### 3.4. Mã băm bằng 0 không có nghĩa tập rỗng

Tập rỗng có tổng rỗng nên mã băm là `0`. Nhưng tập chỉ chứa `null` cũng có mã băm là `0`.

Hai tập này vẫn khác nhau:

- Tập rỗng có kích thước `0`.
- Tập chứa `null` có kích thước `1`.
- Một tập có phần tử `null`, tập kia không có.

Tương tự, `{0}` hoặc `{-1, 1}` cũng có thể có tổng mã băm bằng `0`. Hãy dùng `isEmpty()` để hỏi tập có rỗng không, không dùng mã băm.

Không phải mọi `Set` đều cho phép `null`. Ví dụ trong bài dùng `HashSet` khi cần minh họa tập chứa `null`; không dùng `Set.of(null)`.

## 4. Chương trình đầy đủ: tách thứ tự duyệt khỏi phép bằng nhau

Lưu mã vào tệp **SetEqualityDemo.java**:

```java
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class SetEqualityDemo {
    public static void main(String[] args) {
        Set<Integer> forward =
                new LinkedHashSet<>(List.of(1, 2, 3));
        Set<Integer> reverse =
                new LinkedHashSet<>(List.of(3, 2, 1));

        System.out.println("forward iteration: " + forward);
        System.out.println("reverse iteration: " + reverse);
        System.out.println("same reference: " + (forward == reverse));
        System.out.println("forward.equals(reverse): " + forward.equals(reverse));
        System.out.println("reverse.equals(forward): " + reverse.equals(forward));
        System.out.println("forward hash: " + forward.hashCode());
        System.out.println("reverse hash: " + reverse.hashCode());

        Set<Integer> hashed = new HashSet<>(List.of(3, 1, 2));
        Set<Integer> sorted = new TreeSet<>(List.of(2, 3, 1));
        Set<Integer> fixed = Set.of(2, 1, 3);
        System.out.println("HashSet equals forward: " + hashed.equals(forward));
        System.out.println("TreeSet equals forward: " + sorted.equals(forward));
        System.out.println("unmodifiable equals forward: " + fixed.equals(forward));

        List<Integer> list = List.of(1, 2, 3);
        System.out.println("set equals list: " + forward.equals(list));
        System.out.println("list equals set: " + list.equals(forward));
        System.out.println("reversed lists equal: "
                + list.equals(List.of(3, 2, 1)));

        Set<Integer> subset = Set.of(1, 2);
        System.out.println("contains subset: " + forward.containsAll(subset));
        System.out.println("equals subset: " + forward.equals(subset));

        Set<Integer> left = Set.of(1, 4);
        Set<Integer> right = Set.of(2, 3);
        System.out.println("left hash: " + left.hashCode());
        System.out.println("right hash: " + right.hashCode());
        System.out.println("collision sets equal: " + left.equals(right));

        Set<Integer> empty = new HashSet<>();
        Set<Integer> onlyNull = new HashSet<>();
        onlyNull.add(null);
        System.out.println("empty hash: " + empty.hashCode());
        System.out.println("null-only hash: " + onlyNull.hashCode());
        System.out.println("empty equals null-only: " + empty.equals(onlyNull));

        Set<Integer> integers = Set.of(1);
        Set<Long> longs = Set.of(1L);
        System.out.println("Integer set equals Long set: " + integers.equals(longs));
        System.out.println("numeric hashes equal: "
                + (integers.hashCode() == longs.hashCode()));
    }
}
```

Biên dịch theo mức Java 17 và chạy:

```shell
javac --release 17 -encoding UTF-8 SetEqualityDemo.java
java SetEqualityDemo
```

Stdout:

```text
forward iteration: [1, 2, 3]
reverse iteration: [3, 2, 1]
same reference: false
forward.equals(reverse): true
reverse.equals(forward): true
forward hash: 6
reverse hash: 6
HashSet equals forward: true
TreeSet equals forward: true
unmodifiable equals forward: true
set equals list: false
list equals set: false
reversed lists equal: false
contains subset: true
equals subset: false
left hash: 5
right hash: 5
collision sets equal: false
empty hash: 0
null-only hash: 0
empty equals null-only: false
Integer set equals Long set: false
numeric hashes equal: true
```

Chương trình chủ động chỉ in nội dung hai `LinkedHashSet` đầu tiên, vì thứ tự chèn của chúng giúp nhìn rõ hai trình tự khác nhau. Các tập có thứ tự duyệt không được bảo đảm chỉ được dùng trong phép so sánh hoặc lấy mã băm; stdout không dựa vào việc chúng tình cờ in ra theo thứ tự nào trên một JVM.

Các điểm cần đọc từ kết quả:

1. Hai dòng đầu khác nhau, nhưng hai chiều `equals` đều là `true`.
2. Cùng tham chiếu là `false`; cùng giá trị tập hợp vẫn là `true`.
3. Hai mã băm đều bằng `6`, đúng bằng tổng `1 + 2 + 3`.
4. Khác lớp triển khai, có hoặc không có khả năng sửa đổi, không ngăn các tập bằng nhau khi nội dung phù hợp.
5. `Set` không bằng `List`, dù cùng nhìn thấy ba số theo một thứ tự giống nhau.
6. Bao hàm một tập con không đủ để hai tập bằng nhau.
7. Hai tập khác nhau có thể cùng mã băm `5`.
8. Tập rỗng và tập chỉ chứa `null` cùng mã băm `0` nhưng không bằng nhau.
9. `Set<Integer>` chứa `1` không bằng `Set<Long>` chứa `1L`, dù hai mã băm ở ví dụ này trùng nhau.

## 5. Bỏ qua thứ tự không có nghĩa bỏ qua ý nghĩa phần tử

### 5.1. Set dùng quy tắc bằng nhau của phần tử

Hai tập chuỗi `{"Java"}` và `{"java"}` thông thường không bằng nhau, vì `String.equals` phân biệt hoa thường.

Hai đối tượng riêng biệt có cùng dữ liệu cũng chưa chắc bằng nhau. Nếu một lớp chỉ dùng `Object.equals` kế thừa, phép bằng nhau của phần tử vẫn dựa trên cùng tham chiếu. Việc đặt các đối tượng vào `Set` không tự tạo quy tắc “bằng nhau theo tất cả trường”.

Chẳng hạn, với một lớp `Tag` chưa định nghĩa bằng nhau theo tên, hai lần `new Tag("java")` có thể là hai giá trị khác nhau đối với tập. Nếu muốn hai thẻ cùng tên biểu diễn cùng giá trị, cần thiết kế `equals` và `hashCode` của `Tag` nhất quán.

Quy tắc nền của phần tử cần đáp ứng tính phản xạ, đối xứng, bắc cầu và ổn định khi dữ liệu liên quan không đổi. Nếu phần tử vi phạm các tính chất này, không thể trông chờ tập tự sửa lại ý nghĩa bằng nhau. [Object.equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object))

### 5.2. Kiểu tổng quát không tạo phép chuyển đổi số khi so sánh

`Integer.valueOf(1)` và `Long.valueOf(1L)` không bằng nhau theo `equals`. Vì vậy, hai tập chỉ chứa hai giá trị đó cũng không bằng nhau.

Ngược lại, một `Set<Number>` đang chứa đối tượng `Integer` có giá trị `1` có thể bằng một `Set<Integer>` chứa cùng giá trị `Integer`. Phép bằng nhau xét phần tử thực tế, không đòi chuỗi ký hiệu kiểu tổng quát ở hai biến phải giống hệt nhau.

Điều cần kiểm tra là kiểu và quy tắc bằng nhau của giá trị được lưu, không phải chỉ nhìn rằng cả hai bên “đều là số”.

### 5.3. Set không lưu số lần xuất hiện

Nếu tạo tập từ một danh sách có phần tử lặp, các phần tử bằng nhau được gộp thành một giá trị tập hợp. Khi đó, phép bằng nhau của hai tập không thể khôi phục số lần xuất hiện đã bị bỏ đi.

Ví dụ:

```text
Dữ liệu đầu vào thứ nhất: [java, java, sql]
Dữ liệu đầu vào thứ hai:  [java, sql, sql]

Sau khi chuyển từng đầu vào thành tập:
    cả hai đều có nội dung {java, sql}
    hai tập bằng nhau
```

Nếu bài toán quan tâm số lần mỗi giá trị xuất hiện, chuyển hai dãy thành `Set` rồi gọi `equals` sẽ trả lời một câu hỏi khác với yêu cầu ban đầu. Có thể cần đếm tần suất từng phần tử thay vì loại trùng.

Nếu bài toán quan tâm vị trí và trình tự, dùng mô hình danh sách. Nếu chỉ quan tâm sự hiện diện của từng giá trị, mô hình tập mới phù hợp.

## 6. So sánh Set và List đúng theo yêu cầu nghiệp vụ

| Yêu cầu | Mô hình cần so sánh | Lý do |
| --- | --- | --- |
| Hai lựa chọn có cùng các thẻ không, mỗi thẻ xuất hiện tối đa một lần | `Set.equals` | Thứ tự chọn không tạo khác biệt giá trị |
| Hai chuỗi thao tác có đúng cùng bước theo cùng thứ tự không | `List.equals` | Vị trí của mỗi bước là một phần ý nghĩa |
| Hai danh sách có cùng tần suất mỗi giá trị nhưng không quan tâm thứ tự | Cấu trúc đếm tần suất phù hợp | Set sẽ làm mất số lần lặp |
| Hai tập có cùng trình tự hiển thị không | So sánh một biểu diễn dãy theo yêu cầu riêng | `Set.equals` chủ động bỏ qua trình tự |

`List.equals` so sánh các phần tử tương ứng theo thứ tự. Vì vậy, hai `List` đảo thứ tự có thể không bằng nhau trong khi hai `Set` tạo từ chúng lại bằng nhau. [List.equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#equals(java.lang.Object))

Ví dụ, hai `LinkedHashSet` có cùng thành viên nhưng lịch sử chèn khác nhau vẫn bằng nhau. Nếu màn hình cần xác nhận hai thứ tự trình bày giống nhau, đó là một phép so sánh riêng, không nên sửa nghĩa `equals` của tập để đáp ứng.

Tương tự, không so sánh `toString()` của hai tập rồi kết luận giá trị tập khác nhau. Chuỗi in có thể khác chỉ vì thứ tự duyệt khác. Cũng không nên biến mọi phần tử thành chuỗi rồi so sánh để thay cho `equals`: hai kiểu khác nhau có thể có cùng cách hiển thị nhưng không có cùng ý nghĩa giá trị.

## 7. Dùng Set làm phần tử của Set hoặc làm khóa của Map

### 7.1. Vì sao thứ tự không được làm hỏng phép tìm khóa?

Xét một bảng tra cứu mà khóa là tập kỹ năng:

```java
// Đoạn minh họa, không phải chương trình độc lập.
Map<Set<String>, String> labels = new HashMap<>();
labels.put(Set.of("java", "sql"), "backend");

String label = labels.get(Set.of("sql", "java"));
```

Nếu các phần tử ổn định và tập tuân thủ hợp đồng, khóa truy vấn biểu diễn cùng giá trị tập hợp nên có thể tìm được mục đã lưu. Người gọi không cần tái tạo đúng thứ tự thêm.

Để điều đó hoạt động, tập khóa truy vấn và tập khóa đã lưu phải đồng thời:

- Bằng nhau theo `equals`.
- Có cùng mã băm theo hợp đồng.

Nếu `Set.hashCode` phụ thuộc thứ tự, một khóa bằng nhau có thể đi vào một vùng tìm kiếm khác. Công thức tổng mã băm tránh mâu thuẫn đó.

Đây cũng là lý do một `Set<Set<Integer>>` có thể nhận ra `{1, 2}` và `{2, 1}` là cùng một mẫu tập. Tầng ngoài dùng phép bằng nhau và mã băm của tập ở tầng trong.

### 7.2. Va chạm không làm hai khóa khác nhau bị gộp

Nếu hai khóa là `{1, 4}` và `{2, 3}`, cả hai có mã băm `5`. Một `HashMap` đúng vẫn có thể giữ hai mục riêng vì các tập khóa không bằng nhau.

Tương tự, đưa hai tập này vào một `HashSet<Set<Integer>>` không có nghĩa tập ngoài phải loại bỏ một tập chỉ vì mã băm trùng. Trùng mã băm là tín hiệu cần phân biệt tiếp bằng phép bằng nhau, không phải điều kiện phần tử trùng.

### 7.3. Không sửa nội dung tập khi nó đang là khóa băm

`hashCode` của tập được tạo từ các phần tử. Thêm hoặc xóa phần tử có thể thay đổi mã băm của cả tập.

Nếu một tập đang được dùng làm khóa trong `HashMap` hoặc làm phần tử của `HashSet`, sửa tập đó có thể làm thay đổi dữ liệu mà cấu trúc bên ngoài dùng để nhận diện khóa. Phép tìm và xóa sau đó không còn có thể được suy luận như với khóa ổn định.

Không dùng một kết quả chạy tình cờ để kết luận cách làm này an toàn. Hợp đồng `Set` không quy định hành vi khi phần tử đang nằm trong tập bị thay đổi theo cách ảnh hưởng đến phép bằng nhau của nó. Một tập bên trong cũng là một đối tượng phần tử đối với tập bên ngoài. [Set: lưu ý về phần tử có thể sửa đổi](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

Thiết kế dễ kiểm soát là tạo một bản sao không sửa đổi được cho khóa và dùng các phần tử ổn định. Trong ví dụ sau, thành phần `members` là `Set<String>`, các chuỗi là bất biến, và hàm tạo record dùng `Set.copyOf` để tách nội dung khóa khỏi tập đầu vào.

Cần phân biệt hai điều:

- Không cho thêm/xóa thành viên của tập khóa.
- Không cho thay đổi dữ liệu bên trong từng phần tử theo cách ảnh hưởng đến bằng nhau.

`Set.copyOf` giải quyết việc sửa cấu trúc tập qua tham chiếu đầu vào trong ví dụ, không tự sao chép sâu mọi đối tượng phần tử. Với kiểu phần tử có thể sửa đổi, vẫn phải thiết kế tính ổn định riêng.

Về cấu trúc tập, bản sao từ `Set.copyOf` không phản ánh các thay đổi tiếp theo của tập hợp đầu vào. [Hợp đồng Set.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#copyOf(java.util.Collection))

Một record chỉ giữ trường tham chiếu là `final` cũng chưa đủ nếu trường đó trỏ tới một tập có thể sửa đổi. Bản sao bảo vệ trong hàm tạo giúp ví dụ giữ đúng ý nghĩa khóa.

## 8. TreeSet có bộ so sánh không nhất quán với equals

### 8.1. Quy tắc của TreeSet cần khớp quy tắc bằng nhau của phần tử

`TreeSet` xác định việc hai phần tử có tương đương trong cấu trúc cây qua kết quả `compareTo` hoặc `Comparator.compare`. Kết quả so sánh bằng `0` được tập cây xem là cùng phần tử.

Để tuân thủ đầy đủ hợp đồng `Set`, thứ tự đó phải nhất quán với `equals`:

```text
compare(x, y) == 0
    phải phù hợp với
x.equals(y) == true
```

Với các số nguyên và thứ tự tự nhiên, điều này phù hợp với cách bằng nhau thường dùng. Nhưng một bộ so sánh không phân biệt hoa thường cho `String` lại có thể xem `"a"` và `"A"` là tương đương, trong khi `String.equals` trả về `false`.

Tài liệu `TreeSet` nêu rõ rằng một thứ tự không nhất quán với `equals` vẫn có hành vi xác định theo phép so sánh của nó, nhưng không tuân thủ hợp đồng chung của `Set`. [TreeSet: yêu cầu nhất quán với equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)

### 8.2. Có thể thấy phép bằng nhau mất tính đối xứng

Giả sử:

- Tập cây dùng bộ so sánh không phân biệt hoa thường và chứa `"a"`.
- Tập băm dùng `String.equals` và chứa `"A"`.

Khi tập cây kiểm tra thành viên của tập băm, nó có thể xem `"A"` là đã có vì bộ so sánh cho kết quả `0`. Khi tập băm kiểm tra `"a"`, nó không xem đó là chuỗi `"A"`.

Kết quả của ví dụ chạy ở phần sau:

```text
tree.equals(hash): true
hash.equals(tree): false
tree hash: 97
hash-set hash: 65
```

Đây không phải ví dụ về thứ tự duyệt làm thay đổi `equals`. Nguyên nhân là **hai cấu trúc đang dùng hai ý nghĩa khác nhau về việc phần tử có bằng nhau không**.

Nó cũng không bác bỏ quan hệ “hai đối tượng bằng nhau phải cùng mã băm”. Dữ liệu và bộ so sánh trong ví dụ đã làm tập cây rời khỏi hợp đồng chung của `Set`; vì vậy các bảo đảm dựa trên hợp đồng đó không còn áp dụng đầy đủ.

Không dùng `a.equals(b) || b.equals(a)` để “sửa” kết quả này. Cách đó che mất lỗi thiết kế quy tắc nhận diện và tạo ra một phép so sánh mới khó suy luận.

### 8.3. Chọn một quy tắc nhận diện thống nhất

Nếu miền dữ liệu cần chuỗi không phân biệt hoa thường, hãy quy định một cách biểu diễn chuẩn hoặc một kiểu giá trị chuyên biệt có `equals`, `hashCode` và thứ tự so sánh phù hợp với cùng ý nghĩa.

Nếu chỉ cần hiển thị không phân biệt hoa thường nhưng giá trị vẫn phân biệt hoa thường, có thể sắp xếp một biểu diễn để hiển thị mà không thay đổi quy tắc bằng nhau của dữ liệu.

Đừng suy ra rằng mọi `TreeSet` đều có vấn đề. Một thứ tự phù hợp với `equals`, chẳng hạn thứ tự tự nhiên của các số nguyên trong ví dụ đầu tiên, vẫn làm việc được với hợp đồng chung.

## 9. Chương trình đầy đủ: khóa tập hợp, va chạm và trường hợp Comparator

Lưu mã vào **SetKeyAndComparatorDemo.java**:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SetKeyAndComparatorDemo {
    private record GroupKey(String name, Set<String> members) {
        private GroupKey {
            members = Set.copyOf(members);
        }
    }

    public static void main(String[] args) {
        Set<String> source =
                new LinkedHashSet<>(List.of("java", "sql"));
        GroupKey first = new GroupKey("backend", source);
        GroupKey second = new GroupKey("backend", Set.of("sql", "java"));

        Map<GroupKey, String> groups = new HashMap<>();
        groups.put(first, "found");
        source.add("http");

        System.out.println("key copied membership: "
                + !first.members().contains("http"));
        System.out.println("group keys equal: " + first.equals(second));
        System.out.println("group hashes equal: "
                + (first.hashCode() == second.hashCode()));
        System.out.println("lookup with reordered set: " + groups.get(second));

        Set<Set<Integer>> patterns = new HashSet<>();
        System.out.println("add first pattern: " + patterns.add(Set.of(1, 2)));
        System.out.println("add reordered pattern: "
                + patterns.add(new LinkedHashSet<>(List.of(2, 1))));
        System.out.println("unique patterns: " + patterns.size());

        Map<Set<Integer>, String> collisions = new HashMap<>();
        collisions.put(Set.of(1, 4), "first");
        collisions.put(Set.of(2, 3), "second");
        System.out.println("collision keys stored: " + collisions.size());
        System.out.println("lookup {4, 1}: " + collisions.get(Set.of(4, 1)));
        System.out.println("lookup {3, 2}: " + collisions.get(Set.of(3, 2)));

        Set<String> caseInsensitive =
                new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitive.add("a");
        Set<String> caseSensitive = new HashSet<>(Set.of("A"));

        System.out.println("tree.equals(hash): "
                + caseInsensitive.equals(caseSensitive));
        System.out.println("hash.equals(tree): "
                + caseSensitive.equals(caseInsensitive));
        System.out.println("tree hash: " + caseInsensitive.hashCode());
        System.out.println("hash-set hash: " + caseSensitive.hashCode());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SetKeyAndComparatorDemo.java
java SetKeyAndComparatorDemo
```

Stdout:

```text
key copied membership: true
group keys equal: true
group hashes equal: true
lookup with reordered set: found
add first pattern: true
add reordered pattern: false
unique patterns: 1
collision keys stored: 2
lookup {4, 1}: first
lookup {3, 2}: second
tree.equals(hash): true
hash.equals(tree): false
tree hash: 97
hash-set hash: 65
```

Cách đọc từng nhóm kết quả:

1. `source.add("http")` không thay đổi các thành viên đã chụp vào khóa `first`. Trong hàm tạo, `members = Set.copyOf(members)` tạo hoặc dùng một biểu diễn không sửa đổi được với nội dung tương ứng, thay vì giữ tập đầu vào có thể sửa đổi.
2. Hai `GroupKey` có cùng tên và cùng các thành viên nên bằng nhau. Thứ tự tạo các thành viên không làm chúng thành hai khóa khác nhau.
3. `groups.get(second)` tìm được giá trị đã lưu bằng `first`, dù hai đối tượng khóa được tạo riêng.
4. Tập ngoài chỉ chứa một mẫu `{1, 2}`; thêm mẫu đảo thứ tự không tạo phần tử mới.
5. Hai khóa tập khác nhau nhưng cùng mã băm được giữ thành hai mục trong `HashMap`.
6. Nhóm cuối chủ động dùng bộ so sánh không nhất quán với `String.equals` để quan sát sự mất đối xứng. Đây là tình huống cần nhận diện và tránh khi muốn dùng đầy đủ hợp đồng chung của `Set`, không phải mẫu thiết kế khóa được khuyến nghị.

Hai chương trình độc lập trong bài đã được biên dịch với `--release 17` và chạy đối chiếu đúng stdout. Tùy chọn này kiểm tra mức ngôn ngữ/API và tạo bytecode tương thích Java 17; nó không khẳng định JVM thực thi đang là JDK 17.

## 10. hashCode không phải chữ ký nội dung bền vững

Mã băm trả về `int`, có thể âm, có thể bằng `0` và có thể trùng giữa nhiều tập khác nhau. Nó phục vụ hợp đồng của đối tượng và các cấu trúc băm.

Không dùng riêng `set.hashCode()` để:

- Kết luận hai tập chắc chắn giống nhau.
- Tạo mã định danh duy nhất cho nội dung.
- Xác nhận dữ liệu không bị thay đổi chỉ vì mã băm trước và sau giống nhau.
- Làm chữ ký lưu trữ dài hạn mà không có một định dạng và thuật toán riêng được thiết kế cho yêu cầu đó.

Hợp đồng chung không bắt buộc mã băm của mọi đối tượng phải giữ nguyên giữa các lần chạy ứng dụng. Với những kiểu cụ thể như `Integer` hoặc `String`, có quy tắc mã băm được định nghĩa, nhưng một `Set` có thể chứa các kiểu khác. Không mở rộng quan sát trên một ví dụ thành bảo đảm cho mọi kiểu phần tử. [Object.hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#hashCode())

Ngay trong một lần chạy, hai nội dung khác nhau cũng có thể trùng mã băm như `{1, 4}` và `{2, 3}`. Vì vậy, mã băm bằng nhau trước và sau một thao tác không chứng minh thao tác không thay đổi nội dung.

Nếu yêu cầu chỉ là kiểm tra hai tập đang có bằng nhau, dùng `equals`. Nếu yêu cầu là một dấu kiểm chứng dữ liệu được lưu hoặc truyền đi, cần xác định rõ yêu cầu khác đó, thay vì coi mã băm của đối tượng là đủ.

## 11. Chi phí tính toán và cách viết phép kiểm tra đúng

Một biến có kiểu `Set` không có nghĩa `equals` hoặc `hashCode` của cả tập là thao tác O(1).

Cách triển khai nền của `AbstractSet.hashCode` duyệt các phần tử và cộng mã băm của chúng. Vì vậy, tính mã băm của cả tập khác với việc kiểm tra một phần tử trong một tập băm.

Phép bằng nhau có thể kết thúc sớm khi hai biến cùng tham chiếu, bên kia không phải `Set`, kích thước khác nhau hoặc tìm thấy phần tử thiếu. Khi hai tập bằng nhau nhưng khác đối tượng, việc xác nhận cần đối chiếu nội dung theo cách triển khai tương ứng. Chi phí còn phụ thuộc phép kiểm tra thành viên, mã băm và phép bằng nhau của phần tử. [AbstractSet.equals và hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSet.html)

Những lựa chọn có ý nghĩa trong mã sử dụng tập:

- Cần kiểm tra giá trị tập: gọi `equals`.
- Cần kiểm tra tập con: dùng phép bao hàm thích hợp như `containsAll`.
- Cần kiểm tra rỗng: gọi `isEmpty`.
- Cần so sánh trình tự: dùng một mô hình dãy phù hợp.
- Cần làm khóa: giữ dữ liệu ảnh hưởng đến bằng nhau và mã băm ổn định.

Không thêm một bước “so sánh hashCode trước” vào mọi lời gọi `equals` như một tối ưu mặc định. Bước đó có thể phải duyệt thêm cả tập, vẫn phải gọi `equals` khi mã băm trùng, và làm mã khó đọc hơn nếu không có bằng chứng nó giải quyết một chi phí thực tế.

Cũng không lưu sẵn mã băm của một tập có thể sửa đổi rồi tiếp tục dùng mã băm cũ để đại diện nội dung mới. Nếu thiết kế một giá trị bất biến có lưu mã băm để tái sử dụng, tính bất biến và tính đúng của phép bằng nhau phải được bảo đảm ở thiết kế đó.

## 12. Lỗi thường gặp và cách sửa cách hiểu

| Cách hiểu hoặc cách viết sai | Vì sao sai | Hướng đúng |
| --- | --- | --- |
| Hai LinkedHashSet in khác thứ tự nên không bằng nhau | Nhầm thứ tự duyệt với giá trị tập | Gọi `equals` để so thành viên |
| Hai Set phải cùng lớp mới bằng nhau | Hợp đồng cho phép so sánh khác triển khai | Kiểm tra theo hợp đồng Set |
| Cùng kích thước là đủ | Có thể chứa các phần tử khác nhau | Phải cùng thành viên |
| containsAll một chiều là bằng nhau | Có thể chỉ chứng minh quan hệ tập con | Dùng `equals` khi hai bên là Set |
| Cùng hashCode thì bằng nhau | Mã băm có thể va chạm | Mã băm không thay cho equals |
| hashCode bằng 0 thì tập rỗng | Nhiều tập không rỗng cũng có tổng bằng 0 | Dùng `isEmpty` |
| So sánh chuỗi toString của tập | Thứ tự và cách hiển thị không phải hợp đồng bằng nhau | So sánh trực tiếp các tập |
| Chuyển mọi danh sách thành Set để so sánh | Làm mất số lần lặp và thứ tự | Chọn mô hình theo yêu cầu dữ liệu |
| Đổi nội dung tập khóa sau khi put vào HashMap | Có thể thay đổi mã băm và ý nghĩa bằng nhau của khóa | Dùng khóa có nội dung ổn định |
| Dùng record là đủ để mọi thành phần bất biến | Trường final vẫn có thể trỏ tới tập có thể sửa đổi | Bảo vệ cấu trúc và xét tính ổn định của phần tử |
| TreeSet và HashSet so hai chiều khác nhau nhưng chỉ chọn chiều “đúng ý” | Có thể đang có Comparator không nhất quán với equals | Thống nhất quy tắc nhận diện giá trị |

Khi một phép so sánh gây bất ngờ, hãy kiểm tra theo thứ tự: hai bên có thực sự là `Set` không; nội dung và kích thước có đúng không; phần tử dùng quy tắc bằng nhau nào; có bộ so sánh riêng không; có dữ liệu nào liên quan đến bằng nhau đã bị thay đổi không. Thứ tự hiển thị chỉ là một quan sát, không phải tiêu chí quyết định.

## 13. Tự luyện và đối chiếu

### Bài 1: tính bằng nhau và mã băm bằng tay

Xét các tập chứa `Integer`:

```text
A = {2, 5}
B = {5, 2}
C = {1, 6}
D = {2, 5, 0}
E = {}
```

Dự đoán:

1. `A.equals(B)` và mã băm của hai tập.
2. `A.equals(C)` và mã băm của `C`.
3. `A.equals(D)` và mã băm của `D`.
4. Có thể dùng mã băm để kết luận `A`, `C`, `D` là cùng tập không?

Đối chiếu:

| Tập | Mã băm | Bằng A không? |
| --- | --- | --- |
| A | `7` | Có |
| B | `7` | Có, chỉ khác thứ tự viết |
| C | `7` | Không, khác phần tử |
| D | `7` | Không, có thêm phần tử 0 |
| E | `0` | Không |

Việc thêm `0` làm thay đổi nội dung và kích thước của tập nhưng không thay đổi tổng mã băm trong ví dụ này. Đây là một lý do trực tiếp để không dùng mã băm như phép kiểm tra duy nhất về thay đổi nội dung.

### Bài 2: phân biệt tập con, tập bằng nhau và dãy bằng nhau

Với `a = Set.of(1, 2)`, `b = Set.of(1, 2, 3)` và `list = List.of(1, 2)`, hãy giải thích:

```text
b.containsAll(a)  → true
b.equals(a)       → false
a.equals(list)    → false
```

Đáp án cần nêu cả quan hệ bao hàm, số phần tử và loại cấu trúc. Không dùng lý do “vì thứ tự khác” cho ba dòng trên.

### Bài 3: đánh giá một thiết kế khóa

Một lớp dùng `Set<String> tags` làm thành phần của khóa tra cứu:

1. Nếu hai người thêm cùng các thẻ theo thứ tự khác nhau, khóa có nên khác không?
2. Nếu nhận trực tiếp một `HashSet` từ bên ngoài rồi bên ngoài tiếp tục sửa nó, điều gì cần xem xét?
3. Nếu thay bằng `Set.copyOf(tags)`, phần nào đã được bảo vệ?
4. Nếu thay kiểu phần tử `String` bằng một lớp có thể sửa đổi, cần xét thêm điều gì?

Gợi ý đối chiếu:

- Khi thẻ có ý nghĩa tập hợp, thứ tự thêm không nên tạo khác biệt giá trị.
- Khóa cần ổn định; giữ một tập do bên ngoài tiếp tục sửa có thể làm thay đổi phép bằng nhau và mã băm.
- `Set.copyOf` cho một tập không sửa đổi được với nội dung tương ứng và tránh việc tiếp tục sửa cấu trúc tập đầu vào làm thay đổi thành viên khóa.
- Tập không sửa đổi được không tự làm đối tượng phần tử bất biến. Dữ liệu ảnh hưởng đến `equals`/`hashCode` của từng phần tử vẫn cần ổn định.

### Bài 4: giải thích đúng ví dụ TreeSet

Nếu thấy `tree.equals(hash)` khác `hash.equals(tree)`, đừng kết luận rằng “equals của Set phụ thuộc bên nào có thứ tự”. Hãy tìm bộ so sánh và kiểm tra liệu kết quả so sánh bằng `0` có tương ứng với `equals` của phần tử không.

Một lời giải đúng phải chỉ ra sự không nhất quán trong nhận diện phần tử và liên hệ với tính đối xứng của phép bằng nhau, thay vì chọn một chiều so sánh làm kết quả sử dụng.

## 14. Tài liệu tra cứu

- [Set — hợp đồng bằng nhau, mã băm và phần tử có thể sửa đổi](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)
- [AbstractSet — cách triển khai nền cho equals và hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSet.html)
- [Object — hợp đồng equals/hashCode của đối tượng](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html)
- [List — phép bằng nhau có xét thứ tự](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html)
- [TreeSet — thứ tự cần nhất quán với equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)
- [Integer — mã băm của số nguyên đóng gói](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#hashCode())


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="set-007">Chọn HashSet, LinkedHashSet, TreeSet hoặc EnumSet</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn HashSet, LinkedHashSet, TreeSet hoặc EnumSet

## 1. Chọn từ yêu cầu dữ liệu trước khi chọn tên lớp

Cả bốn lựa chọn đều là Set: mục tiêu chung là biểu diễn các giá trị không trùng lặp. Tuy nhiên, chúng giải quyết những yêu cầu khác nhau về thứ tự, cách tìm kiếm và miền phần tử.

Một yêu cầu như “lưu các mã duy nhất” vẫn chưa đủ để chọn. Cần hỏi thêm:

1. Có cần giữ thứ tự xuất hiện đầu tiên không?
2. Có cần các giá trị luôn được sắp xếp không?
3. Có cần tìm giá trị gần nhất hoặc lấy một khoảng theo thứ tự không?
4. Mọi phần tử có thuộc cùng một kiểu enum không?
5. Có chấp nhận null không?
6. Chi phí chủ yếu nằm ở kiểm tra hiện diện, duyệt, cập nhật hay các phép tập hợp?

Bốn vai trò khái quát:

| Lựa chọn | Vai trò nổi bật |
| --- | --- |
| HashSet | Tập giá trị tổng quát dựa trên băm, không cần thứ tự duyệt được bảo đảm |
| LinkedHashSet | Tập dựa trên băm, đồng thời giữ thứ tự thêm |
| TreeSet | Tập có thứ tự theo Comparable hoặc Comparator, hỗ trợ truy vấn theo thứ tự |
| EnumSet | Tập chuyên biệt cho các hằng của một kiểu enum |

Không có một lớp “tốt nhất” độc lập với yêu cầu. Chọn sai ngữ nghĩa thứ tự có thể là lỗi tính đúng, không chỉ là khác biệt tốc độ.

Bài dùng Java 17 trở lên. Các chương trình chạy độc lập trong một luồng, với các phần tử không bị thay đổi dữ liệu nhận diện trong lúc ở trong tập.

## 2. Bảng so sánh nền tảng

| Tiêu chí | HashSet | LinkedHashSet | TreeSet | EnumSet |
| --- | --- | --- | --- | --- |
| Miền phần tử | Kiểu tham chiếu phù hợp với Set | Như HashSet | Các phần tử so sánh được theo quy tắc đã chọn | Các hằng thuộc cùng một kiểu enum |
| Quy tắc hiện diện | equals/hashCode | equals/hashCode | compareTo hoặc Comparator; cần nhất quán với equals để giữ hợp đồng Set chung | Danh tính các hằng của kiểu enum đó |
| Thứ tự duyệt | Không được bảo đảm | Thứ tự thêm | Thứ tự so sánh | Thứ tự khai báo hằng enum |
| Thêm/xóa/tìm cơ bản | Hằng số khi băm phân bố tốt, không phải bảo đảm thời gian cho mọi dữ liệu | Tương tự, thêm chi phí quản lý thứ tự | O(log n), tính thêm chi phí một phép so sánh | Thao tác cơ bản hằng số với miền enum đã xác định theo tài liệu lớp |
| Truy vấn gần nhất/khoảng theo thứ tự | Không có API chuyên biệt | Không có API chuyên biệt | Có qua SortedSet/NavigableSet | Không triển khai SortedSet/NavigableSet |
| null | Cho phép tối đa một null | Cho phép tối đa một null | Thứ tự tự nhiên không nhận; comparator riêng phải hỗ trợ nếu muốn nhận | Không được thêm null |
| Cấu trúc chủ đạo | Bảng băm | Bảng băm và liên kết giữ thứ tự | Cấu trúc có thứ tự dựa trên TreeMap | Vectơ bit |
| Tự đồng bộ cho nhiều luồng sửa | Không | Không | Không | Không |

“Hằng số” và “logarit” là mô hình công việc theo kích thước. Không đổi chúng thành một tỷ lệ nano giây giữa các lớp.

Null là một chính sách cần xác định, không phải lý do duy nhất để chọn cấu trúc. Một chương trình hoàn toàn có thể chủ động cấm null dù lớp được chọn có hỗ trợ.

## 3. Một lối quyết định dễ áp dụng

### 3.1. Có phải tập các hằng cùng một enum không?

Nếu có, EnumSet là ứng viên rất phù hợp khi thứ tự khai báo là đủ và không cần giữ lịch sử thêm hoặc một comparator riêng.

Nhưng đừng dừng ở câu “có enum”:

- Cần thứ tự người dùng đã chọn các hằng: LinkedHashSet<EnumType> có thể phù hợp hơn.
- Cần thứ tự ưu tiên tùy chỉnh hoặc truy vấn lân cận theo một thứ tự riêng: xem xét TreeSet với quy tắc so sánh đúng.
- Chỉ cần các cờ hoặc nhóm hằng theo miền enum: EnumSet thường diễn đạt ý định rõ và có biểu diễn gọn.

### 3.2. Có cần thứ tự so sánh luôn được duy trì không?

Nếu cần tìm trước/sau một mốc, lấy khoảng hoặc duy trì giá trị đã sắp trong khi cập nhật, TreeSet đáp ứng các năng lực đó.

Nếu chỉ cần in một bản báo cáo đã sắp ở cuối, không bắt buộc phải dùng TreeSet cho toàn bộ quá trình. Có thể dùng một Set phù hợp cho khâu thu thập rồi tạo một danh sách và sắp khi trình bày. Phải tính tần suất cập nhật và số lần cần dữ liệu đã sắp.

### 3.3. Có cần thứ tự thêm không?

Nếu cần loại trùng nhưng giữ thứ tự xuất hiện đầu tiên, LinkedHashSet thể hiện yêu cầu đó.

Thứ tự thêm khác với thứ tự chữ cái, thứ tự số, mức ưu tiên hoặc lần truy cập gần nhất. Đừng gọi tất cả những yêu cầu này chung là “có thứ tự”.

### 3.4. Không cần những bảo đảm thứ tự trên?

HashSet là lựa chọn tổng quát hợp lý cho kiểm tra hiện diện hoặc gom các giá trị phân biệt, khi không cần thứ tự duyệt được bảo đảm và phần tử có equals/hashCode phù hợp.

Việc một lần chạy tình cờ in HashSet theo một thứ tự dễ nhìn không làm xuất hiện một hợp đồng thứ tự mới.

## 4. HashSet: phù hợp khi cần tập tổng quát và không dựa vào thứ tự

### 4.1. Băm để tìm hiện diện

HashSet dùng bảng băm để lưu các phần tử. Với một lớp tự định nghĩa:

- Các đối tượng equals bằng nhau phải có cùng hashCode.
- Cùng hashCode không có nghĩa chắc chắn bằng nhau.
- Dữ liệu tham gia equals/hashCode cần giữ ổn định khi phần tử nằm trong tập.

Nếu phá hợp đồng phần tử, đổi từ HashSet sang LinkedHashSet không giải quyết được, vì cả hai đều dựa trên băm.

Theo [HashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html), các thao tác cơ bản có chi phí hằng số khi hàm băm phân bố phù hợp. Điều kiện này quan trọng; đừng viết “HashSet luôn O(1)” cho mọi loại khóa và mọi phép đo.

### 4.2. Không có bảo đảm thứ tự duyệt

HashSet không giữ thứ tự thêm và không tự sắp. Thứ tự có thể bị ảnh hưởng bởi nội dung băm, sức chứa và các thay đổi của tập.

Vì thế, không dùng thứ tự duyệt của HashSet cho:

- Định dạng đầu ra phải ổn định theo thứ tự thêm.
- Quy tắc “phần tử đầu tiên đã gặp”.
- Chọn giá trị nhỏ nhất bằng cách lấy iterator.next.
- Dữ liệu phải theo thứ tự ưu tiên cố định.

Những yêu cầu đó cần cách tổ chức khác.

### 4.3. Chi phí duyệt còn liên quan đến sức chứa

Tài liệu HashSet nêu việc duyệt phụ thuộc cả số phần tử và sức chứa (capacity) của bảng băm bên trong. Dành capacity quá lớn có thể làm việc duyệt tốn thêm công việc, ngay cả khi số phần tử hiện tại ít.

Điều này khác với khẩu quyết đơn giản “duyệt Set luôn chỉ phụ thuộc size”. Cần xét cách triển khai và cách cấp phát.

Không cần tự chỉnh capacity trong mọi chương trình. Trước tiên xác định kích thước dữ liệu và mẫu dùng; chỉ tối ưu thông số khi có lý do cụ thể.

## 5. LinkedHashSet: giữ thứ tự thêm mà vẫn dùng quy tắc băm

### 5.1. Giá trị xuất hiện lần đầu quyết định vị trí

Với đầu vào:

```text
gamma, alpha, beta, alpha
```
Một LinkedHashSet giữ các giá trị theo thứ tự:

```text
gamma, alpha, beta
```
Lần thêm alpha thứ hai không tạo bản sao và không đẩy alpha xuống cuối.

Nếu xóa alpha rồi thêm lại, đó là một lần thêm mới sau khi alpha đã không còn trong tập, nên nó có thể xuất hiện ở cuối thứ tự thêm hiện tại.

Đây là khác biệt giữa:

- Thử thêm lại một phần tử đang có.
- Xóa rồi thêm lại một phần tử.

### 5.2. Lợi ích và chi phí

[LinkedHashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html) duy trì liên kết thứ tự giữa các mục của bảng băm.

Các thao tác cơ bản vẫn dựa trên băm. Đổi lại, cấu trúc cần thêm trạng thái và công việc để giữ thứ tự. Không coi bảo đảm đó là miễn phí về bộ nhớ.

Duyệt LinkedHashSet có thời gian tỷ lệ với số phần tử, không phụ thuộc capacity theo cách quét bảng của HashSet. Vì thế, không kết luận LinkedHashSet chắc chắn chậm hơn ở mọi loại tác vụ chỉ vì nó quản lý thêm liên kết.

### 5.3. Không phải tập được sắp xếp hoặc cơ chế theo lần truy cập

LinkedHashSet không tự đưa alpha lên trước beta theo thứ tự chữ cái. Nó cũng không đổi thứ tự mỗi lần contains hoặc mỗi lần một phần tử được đọc.

Nếu cần một chính sách về lần truy cập gần nhất, không suy ra nó từ chữ “Linked”. Hợp đồng lớp trong Java 17 là thứ tự thêm.

Sao chép một collection có thứ tự vào LinkedHashSet có thể giữ thứ tự gặp được khi thêm. Nhưng sao chép HashSet không khôi phục một lịch sử thêm mà HashSet không cung cấp.

## 6. Ví dụ chạy được: phân biệt không có thứ tự, thứ tự thêm và thứ tự sắp

Lưu vào `SetOrderChoiceDemo.java`:

```java
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class SetOrderChoiceDemo {
    public static void main(String[] args) {
        List<String> input = List.of("gamma", "alpha", "beta", "alpha");
        Set<String> hash = new HashSet<>(input);
        LinkedHashSet<String> linked = new LinkedHashSet<>(input);
        TreeSet<String> sorted = new TreeSet<>(input);

        System.out.println("HashSet size = " + hash.size());
        System.out.println("HashSet contains alpha = " + hash.contains("alpha"));
        System.out.println("HashSet members sorted for display = "
                + new TreeSet<>(hash));
        System.out.println("LinkedHashSet = " + linked);
        System.out.println("TreeSet = " + sorted);
        System.out.println("hash equals linked = " + hash.equals(linked));

        System.out.println("add existing alpha = " + linked.add("alpha"));
        System.out.println("order after existing add = " + linked);

        linked.remove("alpha");
        linked.add("alpha");
        System.out.println("order after remove and add = " + linked);

        System.out.println("HashSet add first null = " + hash.add(null));
        System.out.println("HashSet add second null = " + hash.add(null));
    }
}
```
Biên dịch và chạy:

```shell
javac --release 17 SetOrderChoiceDemo.java
java SetOrderChoiceDemo
```
Kết quả:

```text
HashSet size = 3
HashSet contains alpha = true
HashSet members sorted for display = [alpha, beta, gamma]
LinkedHashSet = [gamma, alpha, beta]
TreeSet = [alpha, beta, gamma]
hash equals linked = true
add existing alpha = false
order after existing add = [gamma, alpha, beta]
order after remove and add = [gamma, beta, alpha]
HashSet add first null = true
HashSet add second null = false
```
Chương trình cố ý không in trực tiếp thứ tự HashSet như một output cố định.

Dòng `HashSet members sorted for display` tạo **một TreeSet khác** để trình bày nội dung đã sắp. Nó không chứng minh HashSet gốc đã có thứ tự.

Các điểm được kiểm chứng:

- Cả ba cách triển khai giữ ba giá trị phân biệt.
- LinkedHashSet giữ gamma, alpha, beta theo lần xuất hiện đầu tiên.
- TreeSet dùng thứ tự tự nhiên của String nên in alpha, beta, gamma.
- HashSet và LinkedHashSet vẫn Set.equals bằng nhau khi có cùng thành viên.
- Thêm lại alpha đang có không đổi thứ tự.
- Xóa rồi thêm alpha tạo thứ tự gamma, beta, alpha.
- HashSet nhận một null nhưng không tạo lần hiện diện thứ hai.

Chương trình chưa đo tốc độ hoặc bộ nhớ của ba lớp. Output về thứ tự không phải một benchmark.

## 7. TreeSet: chọn khi thứ tự là một năng lực cần duy trì

### 7.1. Thứ tự tự nhiên hoặc Comparator

TreeSet dùng Comparable của phần tử hoặc Comparator được cung cấp. Các phần tử được giữ theo thứ tự đó, không theo thời điểm thêm.

Ví dụ:

- Số nguyên theo thứ tự tăng dần.
- Mã String theo thứ tự so sánh String.
- Một thứ tự riêng được mô tả bởi Comparator, nếu nó phù hợp với mô hình phần tử.

Theo [TreeSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html), add, remove và contains có chi phí O(log n) cho các thao tác cơ bản, với chi phí so sánh cần được tính phù hợp. Comparator đọc chuỗi dài hoặc dữ liệu phức tạp không tự có chi phí hằng số.

Không chọn TreeSet chỉ vì tên lớp gợi “cây nên tìm nhanh hơn băm”. Lý do nổi bật là tập duy trì thứ tự và cung cấp các truy vấn theo thứ tự.

### 7.2. So sánh bằng 0 quyết định hiện diện

Hai giá trị được comparator hoặc compareTo coi là bằng 0 sẽ được TreeSet coi như cùng một phần tử.

Nếu comparator chỉ so độ dài String, hai chuỗi khác nhau nhưng cùng độ dài có thể làm lần thêm thứ hai trả false. Nếu nghiệp vụ muốn giữ cả hai chuỗi, comparator đó đã thiếu tiêu chí phân biệt.

Để giữ đúng hợp đồng Set chung, thứ tự cần nhất quán với equals. Với String, có thể sắp theo độ dài rồi theo nội dung:

```java
// Đoạn minh họa: cần import java.util.Comparator và java.util.TreeSet.
Comparator<String> order = Comparator
        .comparingInt(String::length)
        .thenComparing(Comparator.naturalOrder());

TreeSet<String> values = new TreeSet<>(order);
```
Quy tắc phụ ở đây giúp các chuỗi khác nội dung vẫn được phân biệt khi cùng độ dài. Không thêm một tiêu chí phụ làm hai đối tượng vốn equals lại so khác 0.

Cũng không sửa dữ liệu tham gia comparator khi phần tử đang nằm trong cây. Vị trí trong cấu trúc có thể không còn phù hợp sau thay đổi đó.

### 7.3. Các truy vấn gần nhất và khoảng

Với NavigableSet:

| Phương thức | Giá trị được hỏi |
| --- | --- |
| lower(x) | Giá trị lớn nhất nhỏ hơn x |
| floor(x) | Giá trị lớn nhất nhỏ hơn hoặc bằng x |
| ceiling(x) | Giá trị nhỏ nhất lớn hơn hoặc bằng x |
| higher(x) | Giá trị nhỏ nhất lớn hơn x |
| subSet(from, fromInclusive, to, toInclusive) | Vùng giá trị giữa hai mốc theo quy tắc biên |
| first(), last() | Hai cực theo thứ tự hiện tại |

lower/floor/ceiling/higher trả null nếu không tìm thấy giá trị phù hợp. first/last ném NoSuchElementException khi tập rỗng. Đây là những hợp đồng khác nhau.

Thứ tự của comparator mới là thứ tự cần dùng để diễn giải “trước”, “sau” hoặc “khoảng”; với comparator đảo ngược, đừng tiếp tục đọc kết quả như thứ tự số tăng dần.

### 7.4. Vùng nhìn không phải bản sao

subSet trả một vùng nhìn (view) được hậu thuẫn bởi tập gốc. Thêm/xóa qua vùng nhìn có thể thay đổi tập gốc; thay đổi phù hợp ở gốc cũng được thấy qua vùng nhìn.

Thêm một giá trị ngoài biên của vùng nhìn bị từ chối. Nếu cần một tập kết quả độc lập, phải sao chép có chủ đích và giữ đúng quy tắc so sánh cần dùng.

## 8. Ví dụ chạy được: TreeSet cho truy vấn mà HashSet không cung cấp

Lưu vào `TreeSetRangeChoiceDemo.java`:

```java
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public class TreeSetRangeChoiceDemo {
    public static void main(String[] args) {
        NavigableSet<Integer> values = new TreeSet<>(
                List.of(40, 10, 30, 20));

        System.out.println("sorted values = " + values);
        System.out.println("first = " + values.first());
        System.out.println("last = " + values.last());
        System.out.println("lower than 20 = " + values.lower(20));
        System.out.println("floor of 20 = " + values.floor(20));
        System.out.println("ceiling of 25 = " + values.ceiling(25));
        System.out.println("higher than 30 = " + values.higher(30));

        NavigableSet<Integer> middle = values.subSet(20, true, 40, false);
        System.out.println("range [20, 40) = " + middle);

        middle.remove(30);
        System.out.println("original after range removal = " + values);

        try {
            middle.add(50);
        } catch (IllegalArgumentException ex) {
            System.out.println("add outside range = "
                    + ex.getClass().getSimpleName());
        }

        try {
            values.add(null);
        } catch (NullPointerException ex) {
            System.out.println("natural-order TreeSet null = "
                    + ex.getClass().getSimpleName());
        }
    }
}
```
Biên dịch và chạy:

```shell
javac --release 17 TreeSetRangeChoiceDemo.java
java TreeSetRangeChoiceDemo
```
Kết quả:

```text
sorted values = [10, 20, 30, 40]
first = 10
last = 40
lower than 20 = 10
floor of 20 = 20
ceiling of 25 = 30
higher than 30 = 40
range [20, 40) = [20, 30]
original after range removal = [10, 20, 40]
add outside range = IllegalArgumentException
natural-order TreeSet null = NullPointerException
```
Giải thích:

- Dữ liệu được thêm theo 40, 10, 30, 20 nhưng duyệt tăng dần.
- lower(20) bỏ qua chính 20, còn floor(20) có thể trả 20.
- ceiling(25) tìm được 30 mà không yêu cầu 25 phải tồn tại.
- subSet chọn từ 20 kể cả 20 đến trước 40, nên ban đầu có 20 và 30.
- Xóa 30 qua middle cũng xóa 30 khỏi tập gốc.
- Thêm 50 qua middle sai biên nên ném IllegalArgumentException.
- TreeSet theo thứ tự tự nhiên trong ví dụ từ chối null.

Nếu chỉ cần hỏi có chứa 25 không, HashSet cũng có contains. Khi cần “giá trị nhỏ nhất không dưới 25”, đó mới là yêu cầu theo thứ tự mà TreeSet đáp ứng trực tiếp.

TreeSet có comparator xử lý null có thể có chính sách khác ví dụ. Không biến trường hợp comparator riêng thành một giả định rằng mọi TreeSet chấp nhận null.

## 9. EnumSet: khai thác miền phần tử đã là một enum

### 9.1. Miền đóng và biểu diễn bằng bit

EnumSet dành cho các hằng của cùng một kiểu enum. Nó biết miền các giá trị có thể xuất hiện và dùng vectơ bit để biểu diễn hiện diện.

Ví dụ, một nhóm Phase được chọn có thể được biểu diễn mà không cần một mục băm tổng quát riêng cho từng giá trị hiện diện. Theo [EnumSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/EnumSet.html), đây là biểu diễn gọn và các thao tác cơ bản có chi phí hằng số; những thao tác hàng loạt cũng có thể được tối ưu khi đối số là EnumSet.

Đừng suy từ “bit” rằng người dùng phải tự dịch bit theo ordinal hoặc giữ một mặt nạ int riêng. EnumSet cung cấp kiểu dữ liệu an toàn hơn để thao tác với các hằng enum.

EnumSet cũng không chỉ dùng được cho enum có tối đa 64 hằng. [Mã nguồn EnumSet của OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/EnumSet.java) chọn cách triển khai nội bộ theo kích thước miền. Những tên lớp triển khai đó không phải API mà mã ứng dụng nên phụ thuộc.

### 9.2. Thứ tự khai báo, không phải thứ tự thêm

Nếu enum khai báo NEW trước RUNNING trước FAILED, EnumSet duyệt theo thứ tự đó dù thêm FAILED đầu tiên.

Đây không phải thứ tự chữ cái và không phải lịch sử người dùng chọn. Nếu yêu cầu là “thể hiện theo thứ tự người dùng đã chọn các trạng thái”, EnumSet không đáp ứng riêng bảo đảm đó; LinkedHashSet<Phase> có thể phù hợp.

Nếu cần thứ tự tùy chỉnh hoặc truy vấn theo một comparator, EnumSet không tự biến thành TreeSet. Nó không triển khai SortedSet/NavigableSet chỉ vì có thứ tự duyệt.

### 9.3. Các phương thức tạo cần nhận ra

| Factory | Ý nghĩa |
| --- | --- |
| EnumSet.noneOf(Phase.class) | Tập rỗng nhưng biết kiểu enum |
| EnumSet.of(a, b, ...) | Tập chứa các hằng được chỉ định |
| EnumSet.allOf(Phase.class) | Tập chứa mọi hằng của kiểu đó |
| EnumSet.copyOf(existingEnumSet) | Bản sao tập với cùng kiểu enum |
| EnumSet.range(from, to) | Các hằng từ from tới to, kể cả hai đầu, theo thứ tự khai báo |
| EnumSet.complementOf(selected) | Các hằng của cùng kiểu chưa có trong selected |

EnumSet là lớp trừu tượng, nên không tạo bằng `new EnumSet<>()`. Dùng phương thức tạo phù hợp để cung cấp kiểu miền.

### 9.4. Hai bẫy khi dùng range và copyOf

**range không phải subSet của một tập đang có.** EnumSet.range tạo các hằng trong đoạn khai báo của miền enum, dù chúng chưa từng được thêm vào một tập selected nào. TreeSet.subSet lại chọn vùng từ những giá trị đang hiện diện trong tập gốc và trả một vùng nhìn.

**copyOf từ collection thường rỗng không suy ra được kiểu enum.** Nếu đối số thực tế không phải EnumSet và không có phần tử, EnumSet.copyOf(Collection) ném IllegalArgumentException. Khi biết kiểu cần dùng, noneOf(Type.class) rồi addAll là cách thể hiện rõ.

Một EnumSet rỗng vẫn giữ thông tin kiểu nên có thể được copy. Điều này không chỉ là vấn đề tên kiểu biến ở lúc biên dịch: overload nhận Collection cũng xét trường hợp đối số thực tế là EnumSet theo hợp đồng của phương thức tạo.

### 9.5. allOf và complementOf cần được đọc theo miền

Nếu thêm hằng mới vào kiểu enum ở một phiên bản sau, tập “mọi hằng” và phần bù theo miền có thể bao gồm hằng mới đó. Hãy chắc rằng ý định nghiệp vụ thật sự là chọn theo toàn miền.

Thứ tự khai báo enum cũng không tự là một máy trạng thái. EnumSet.range giữa hai Phase chỉ nói về vị trí khai báo; nó không chứng minh mọi bước chuyển trạng thái trong khoảng đều hợp lệ về nghiệp vụ.

Không dùng ordinal hiện tại như một định danh nghiệp vụ ổn định chỉ vì cách biểu diễn nội bộ có liên quan đến vị trí hằng. Tách cách lưu/truyền định danh khỏi chi tiết tối ưu của collection.

## 10. Ví dụ chạy được: EnumSet, thứ tự miền và tập rỗng

Lưu vào `EnumSetChoiceDemo.java`:

```java
import java.util.EnumSet;
import java.util.List;

public class EnumSetChoiceDemo {
    enum Phase {
        NEW, VALIDATED, RUNNING, COMPLETED, FAILED
    }

    public static void main(String[] args) {
        EnumSet<Phase> selected = EnumSet.of(
                Phase.FAILED, Phase.NEW, Phase.RUNNING);
        System.out.println("selected in declaration order = " + selected);
        System.out.println("none = " + EnumSet.noneOf(Phase.class));
        System.out.println("all = " + EnumSet.allOf(Phase.class));
        System.out.println("range VALIDATED through COMPLETED = "
                + EnumSet.range(Phase.VALIDATED, Phase.COMPLETED));
        System.out.println("complement = " + EnumSet.complementOf(selected));

        EnumSet<Phase> copy = EnumSet.copyOf(selected);
        copy.remove(Phase.FAILED);
        System.out.println("copy after removal = " + copy);
        System.out.println("selected remains = " + selected);

        EnumSet<Phase> empty = EnumSet.noneOf(Phase.class);
        System.out.println("copy empty EnumSet = " + EnumSet.copyOf(empty));

        try {
            EnumSet.copyOf(List.<Phase>of());
        } catch (IllegalArgumentException ex) {
            System.out.println("copy empty ordinary collection = "
                    + ex.getClass().getSimpleName());
        }

        try {
            selected.add(null);
        } catch (NullPointerException ex) {
            System.out.println("EnumSet add null = "
                    + ex.getClass().getSimpleName());
        }
    }
}
```
Biên dịch và chạy:

```shell
javac --release 17 EnumSetChoiceDemo.java
java EnumSetChoiceDemo
```
Kết quả:

```text
selected in declaration order = [NEW, RUNNING, FAILED]
none = []
all = [NEW, VALIDATED, RUNNING, COMPLETED, FAILED]
range VALIDATED through COMPLETED = [VALIDATED, RUNNING, COMPLETED]
complement = [VALIDATED, COMPLETED]
copy after removal = [NEW, RUNNING]
selected remains = [NEW, RUNNING, FAILED]
copy empty EnumSet = []
copy empty ordinary collection = IllegalArgumentException
EnumSet add null = NullPointerException
```
Các điều được kiểm chứng:

1. FAILED được đưa vào of trước, nhưng được duyệt sau NEW và RUNNING vì thứ tự khai báo.
2. noneOf tạo tập rỗng có kiểu.
3. allOf chọn toàn bộ năm hằng.
4. range kể cả hai đầu VALIDATED và COMPLETED, nên có cả RUNNING ở giữa.
5. complementOf chọn các hằng trong miền chưa nằm trong selected.
6. Sửa bản copy không làm selected mất FAILED.
7. Copy một EnumSet rỗng vẫn hợp lệ.
8. Copy một List rỗng thông thường không có dữ liệu để suy ra kiểu enum.
9. Thêm null bị từ chối bằng NullPointerException.

Bản sao ở đây là cấu trúc tập độc lập. Các hằng enum vẫn là các hằng của cùng một kiểu, không có việc nhân bản thành hằng mới.

Ví dụ không đo bộ nhớ hoặc tốc độ, nên không rút ra một tỷ lệ nhanh hơn HashSet. Lý do lựa chọn EnumSet đến từ miền kiểu phù hợp và biểu diễn chuyên biệt.

## 11. Chọn kiểu khai báo để thể hiện đúng năng lực cần dùng

### 11.1. Chỉ cần hiện diện: Set thường là đủ

```java
// Đoạn minh họa.
Set<String> visitedIds = new HashSet<>();
Set<String> orderedTags = new LinkedHashSet<>();
```
Mã gọi có thể dùng add, contains, remove hoặc duyệt qua Set. Cách triển khai giữ những đặc điểm đã chọn bên trong.

Tuy nhiên, nếu thứ tự là một phần hợp đồng mà bên gọi cần dựa vào, phải mô tả rõ bảo đảm đó. Chỉ nhìn kiểu Set không biết kết quả giữ thứ tự thêm, thứ tự sắp hay không có thứ tự bảo đảm.

### 11.2. Cần truy vấn thứ tự: khai báo năng lực tương ứng

```java
// Đoạn minh họa.
NavigableSet<Integer> thresholds = new TreeSet<>();
Integer next = thresholds.ceiling(25);
```
Nếu biến chỉ có kiểu Set, không thể gọi ceiling trực tiếp. NavigableSet làm rõ rằng mã cần năng lực lân cận theo thứ tự, không chỉ phép kiểm tra hiện diện.

Không ép kiểu một Set bất kỳ thành TreeSet chỉ để lấy những phương thức này. Kiểu khai báo và hợp đồng đầu vào cần phù hợp ngay từ thiết kế.

### 11.3. Miền enum: có thể giữ EnumSet ở nơi cần thao tác chuyên biệt

```java
// Đoạn minh họa, Phase là kiểu enum đã định nghĩa.
EnumSet<Phase> selected = EnumSet.noneOf(Phase.class);
selected.add(Phase.RUNNING);
```
Nếu một hàm chỉ cần kiểm tra hiện diện, nó có thể nhận Set<Phase>. Nếu hàm cần copy/complement theo miền hoặc muốn bảo đảm dùng biểu diễn chuyên biệt, có thể cần hợp đồng cụ thể hơn.

Đừng trộn các kiểu enum khác nhau qua kiểu thô (raw type) để vượt qua giới hạn kiểu. Mỗi EnumSet được gắn với một miền enum.

## 12. Đổi cách triển khai có thể đổi bảo đảm, không chỉ đổi tốc độ

### 12.1. Sao chép sang kiểu khác

| Phép chuyển | Điều cần kiểm tra |
| --- | --- |
| HashSet → LinkedHashSet | Giữ thứ tự gặp được lúc duyệt nguồn, không khôi phục lịch sử thêm |
| LinkedHashSet → HashSet | Có thể mất bảo đảm thứ tự trình bày |
| Set → TreeSet | Cần kiểu so sánh được hoặc comparator phù hợp; tiêu chí compare bằng 0 ảnh hưởng hiện diện |
| EnumSet → HashSet | Vẫn có các hằng nhưng mất bảo đảm thứ tự khai báo của EnumSet |
| Set thông thường rỗng → EnumSet.copyOf | Không có kiểu miền để suy ra nếu nguồn không phải EnumSet |
| Tập có comparator → tập kết quả mới | Phải giữ comparator nếu yêu cầu thứ tự phụ thuộc nó |

Khi sao chép TreeSet, đừng chỉ nhìn các phần tử rồi quên quy tắc so sánh. Cách tạo tập mới phải tương ứng với comparator cần giữ.

### 12.2. Không sửa được là một yêu cầu khác

Cả bốn loại trong bài đều có các cấu trúc có thể sửa thông thường. Việc muốn xuất một Set không cho bên ngoài thêm/xóa cần chính sách riêng.

Không tự giả định `Set.copyOf(enumSet)` vẫn là EnumSet hoặc vẫn bảo đảm thứ tự khai báo. Set.copyOf có hợp đồng của phương thức tạo Set, không phải một lời hứa bảo toàn mọi đặc điểm lớp nguồn.

Bọc hoặc sao chép cũng không tự tạo bất biến sâu cho đối tượng phần tử. Khi phần tử có trường có thể sửa, cần giữ dữ liệu nhận diện ổn định và xác định quyền sửa ở mức mô hình.

### 12.3. Đổi TreeSet sang HashSet có thể bộc lộ tiêu chí trùng khác

Nếu TreeSet dùng comparator không nhất quán với equals, nó có thể đã gộp những giá trị mà HashSet coi là khác nhau. Chuyển sang HashSet không khôi phục được những phần tử đã không được giữ.

Vì vậy, kiểm tra tiêu chí bằng nhau trước khi đổi lớp. Một phép chuyển collection không thể tái tạo dữ liệu đã bị loại từ trước.

## 13. Chi phí, bộ nhớ và mô hình nhiều luồng

### 13.1. Nhìn toàn bộ mẫu thao tác

Nếu chủ yếu contains và không cần thứ tự, băm là một hướng tự nhiên. Nếu cần luôn trả một dải theo thứ tự sau mỗi cập nhật, chi phí duy trì TreeSet có thể phục vụ trực tiếp yêu cầu đó.

Nếu chỉ duyệt một lần ở cuối, so cả chi phí thu thập rồi sắp một bản trình bày. Nếu miền là enum, xem lợi ích của biểu diễn chuyên biệt và các phép tập hợp trên EnumSet.

Không chọn bằng một phép đo duy nhất trên vài phần tử hoặc một bảng O(...) thiếu điều kiện của hàm băm hoặc phép so sánh.

### 13.2. Không có tỷ lệ bộ nhớ cố định

HashSet có bảng băm và các mục quản lý. LinkedHashSet thêm trạng thái liên kết thứ tự. TreeSet có cấu trúc quản lý thứ tự. EnumSet dùng bit cho miền enum và có thông tin miền liên quan.

Số byte thực tế phụ thuộc JVM, kích thước tập, miền kiểu, capacity và đồ thị đối tượng phần tử. Đừng nói một lớp luôn tốn đúng X byte/phần tử trên mọi môi trường.

EnumSet gọn không có nghĩa có thể dùng nó cho các mã String phát sinh động. Nó chỉ phù hợp khi miền thực sự là enum.

### 13.3. Bốn lớp không tự giải quyết truy cập đồng thời

HashSet, LinkedHashSet, TreeSet và EnumSet không tự đồng bộ cho nhiều luồng cùng sửa. Lựa chọn trong bài dựa trên miền và thứ tự; yêu cầu đồng thời là một chiều khác.

Iterator fail-fast của một số lớp giúp phát hiện một số thay đổi không hợp lệ ở mức best effort. Iterator của EnumSet có tính nhất quán yếu (weakly consistent) theo tài liệu, nhưng việc đó không làm EnumSet thành một collection an toàn luồng cho mọi cách truy cập.

Nếu chương trình cần nhiều luồng sửa hoặc một ảnh chụp nhất quán, phải chọn cơ chế hoặc cấu trúc phù hợp với yêu cầu đó. Không chọn lớp chỉ vì một lần thử chưa thấy ConcurrentModificationException.

## 14. Các tình huống lựa chọn cụ thể

| Tình huống | Lựa chọn có cơ sở ban đầu | Lý do và điều kiện |
| --- | --- | --- |
| Gom mã duy nhất để hỏi đã gặp chưa, không xuất theo thứ tự | HashSet | Không cần trả chi phí duy trì một thứ tự không dùng đến |
| Loại trùng nhãn nhưng giữ thứ tự người dùng nhập lần đầu | LinkedHashSet | Thứ tự thêm là yêu cầu |
| Theo dõi các mốc số và thường xuyên hỏi mốc nhỏ nhất không dưới x | TreeSet qua NavigableSet | Có ceiling và năng lực theo thứ tự |
| Duy trì các giá trị đang có và lấy vùng [a, b) | TreeSet | Cần phạm vi theo thứ tự; chú ý vùng nhìn |
| Nhóm các giá trị của cùng một enum, thứ tự khai báo là đủ | EnumSet | Miền kiểu cố định và biểu diễn chuyên biệt |
| Các hằng enum nhưng phải giữ thứ tự người dùng chọn | LinkedHashSet<EnumType> | EnumSet sẽ dùng thứ tự khai báo, không giữ lịch chọn |
| Chỉ cần một bản xuất đã sắp sau khi thu thập xong | Set phù hợp để thu thập, rồi sắp bản trình bày khi cần | Không nhất thiết phải duy trì cây suốt quá trình |
| Chuỗi có thể trùng theo một khóa nghiệp vụ riêng | Chọn mô hình khóa trước, rồi chọn loại Set | Lớp collection không tự suy ra tiêu chí khóa |

Một lựa chọn ban đầu có cơ sở không thay thế việc đo tác vụ thật khi dữ liệu hoặc tải lớn. Nhưng tính đúng của thứ tự và hiện diện phải rõ trước khi tối ưu thời gian.

## 15. Các nhầm lẫn dễ dẫn đến chọn sai

| Nhầm lẫn | Điều cần phân biệt |
| --- | --- |
| “HashSet hiện đang in tăng dần nên có thể dựa vào” | Thứ tự quan sát được không phải bảo đảm API |
| “LinkedHashSet sắp theo giá trị” | Nó giữ thứ tự thêm |
| “add lại phần tử hiện có sẽ đẩy xuống cuối” | LinkedHashSet Java 17 không đổi vị trí chỉ vì add trùng |
| “TreeSet chỉ thay thứ tự in” | Quy tắc compare bằng 0 còn quyết định phần tử có được giữ |
| “Comparator không cần quan tâm equals” | Muốn tuân thủ Set chung phải nhất quán với equals |
| “Có enum thì mọi yêu cầu đều nên chọn EnumSet” | Cần xem thứ tự thêm, thứ tự riêng và năng lực truy vấn |
| “EnumSet duyệt theo thứ tự gọi add” | Nó duyệt theo thứ tự khai báo |
| “EnumSet chỉ là một Set.of cho enum” | EnumSet thông thường sửa được; Set.of tạo tập không sửa cấu trúc |
| “EnumSet.range lọc tập selected hiện tại” | Nó tạo đoạn các hằng trong miền enum |
| “copyOf từ mọi collection rỗng đều biết kiểu enum” | Nguồn không phải EnumSet và rỗng thì không suy được kiểu |
| “Set có thứ tự là SortedSet” | LinkedHashSet và EnumSet có thứ tự duyệt nhưng không cung cấp API SortedSet/NavigableSet |
| “Cùng O(1) nghĩa mọi lớp chạy như nhau” | Giả định băm, hằng số, bộ nhớ và tác vụ vẫn khác |
| “Một Set không sửa được là ảnh chụp sâu bất biến” | Cần phân biệt cấu trúc tập và vòng đời phần tử |

## 16. Thực hành chọn theo hợp đồng

### Bài 1: Giữ dòng đầu tiên đã gặp

Nguồn có các mã C, A, B, A. Muốn loại trùng nhưng xuất C, A, B.

LinkedHashSet là lựa chọn trực tiếp theo thứ tự thêm. TreeSet sẽ sắp thành một thứ tự khác; HashSet không bảo đảm yêu cầu; EnumSet chỉ dùng được nếu miền được mô hình hóa thành enum và thứ tự khai báo chưa chắc đúng yêu cầu.

### Bài 2: Hỏi ngưỡng gần nhất

Có các ngưỡng 10, 20, 30, 40; cần ngưỡng nhỏ nhất không dưới 25.

TreeSet qua NavigableSet cho ceiling(25) bằng 30. contains(25) của một tập băm chỉ trả lời có chính giá trị 25 hay không, không trả cùng loại kết quả.

### Bài 3: Enum được thêm ngược thứ tự khai báo

Nếu enum khai báo A, B, C và gọi EnumSet.of(C, A), khi duyệt sẽ gặp A rồi C. Nếu cần C rồi A theo thứ tự chọn, dùng cấu trúc có bảo đảm thứ tự thêm hoặc lưu lịch chọn riêng.

### Bài 4: Có cần TreeSet chỉ để in đẹp?

Nếu ứng dụng chỉ cần duy nhất trong lúc thu thập và xuất một báo cáo đã sắp một lần, có thể thu thập bằng HashSet rồi sắp một danh sách kết quả. Nếu cần truy vấn thứ tự thường xuyên trong lúc cập nhật, TreeSet có lý do mạnh hơn.

### Bài 5: Comparator chỉ so một thuộc tính

Hai bản ghi khác nhau có cùng độ ưu tiên. Comparator chỉ so độ ưu tiên có thể trả 0, khiến TreeSet không giữ cả hai. Cần xác định đúng tiêu chí hiện diện và quy tắc thứ tự nhất quán; không coi đây là lỗi ngẫu nhiên của cây.

### Bài 6: Khởi tạo một nhóm enum rỗng

Biết kiểu Phase nhưng chưa chọn giá trị nào. Dùng EnumSet.noneOf(Phase.class), không cố suy kiểu từ một List rỗng thông thường.

### Bài 7: Thay đổi qua một phạm vi

Một biến nhận kết quả TreeSet.subSet rồi gọi remove. Nếu cần giữ tập nguồn, phải nhớ đó là vùng nhìn, không phải bản sao độc lập. Sao chép có chủ đích trước khi sửa nếu yêu cầu là tính một kết quả riêng.

### Bài 8: Kiểm tra lựa chọn sau khi đổi yêu cầu

Ban đầu không cần thứ tự nên dùng HashSet. Sau đó đầu ra phải giữ thứ tự người dùng thêm. Đây là thay đổi hợp đồng dữ liệu; chuyển sang LinkedHashSet có thể phù hợp hơn việc dựa vào một thứ tự tình cờ của HashSet.

Một quyết định tốt nêu được **miền phần tử, tiêu chí trùng, thứ tự cần giữ, kiểu truy vấn, quyền sửa và mẫu thao tác**. Từ những yêu cầu đó mới chọn HashSet, LinkedHashSet, TreeSet hoặc EnumSet và kiểm chứng các bảo đảm quan trọng bằng ví dụ phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)
