<a id="back-to-top"></a>

# Set dùng băm

## Menu
- [HashSet và quan hệ với bảng băm](#hash-based-set-001)
- [Thêm, tìm, xóa và điều kiện đạt hiệu năng mong đợi](#hash-based-set-002)
- [Capacity, load factor và chi phí duyệt HashSet](#hash-based-set-003)
- [Chính sách null và việc không bảo đảm thứ tự duyệt](#hash-based-set-004)
- [LinkedHashSet và thứ tự chèn](#hash-based-set-005)
- [Thêm lại phần tử có sẵn và thứ tự của LinkedHashSet](#hash-based-set-006)
- [Khóa khả biến, hợp đồng equals–hashCode và lỗi mất dấu phần tử](#hash-based-set-007)
- [So sánh HashSet với LinkedHashSet theo thời gian và bộ nhớ](#hash-based-set-008)

## <a id="hash-based-set-001">HashSet và quan hệ với bảng băm</a>

<details>
<summary>Xem chi tiết</summary>

# HashSet và quan hệ với bảng băm

Một `HashSet` trả lời câu hỏi “phần tử này có thuộc tập hợp không?” và bảo đảm không lưu nhiều thành viên tương đương. Để không phải quét lần lượt mọi phần tử cho mỗi lần tìm, nó dùng một bảng băm để thu hẹp nơi cần tìm.

Trong Java, quan hệ còn cụ thể hơn: `HashSet` được hậu thuẫn bởi một `HashMap`. Phần tử của set được đặt làm **khóa** của map; giá trị đi kèm chỉ đóng vai trò đánh dấu có mặt.

Hiểu mối quan hệ này giúp giải thích các hành vi của `add`, `contains`, `remove`, việc cho phép `null`, xử lý va chạm, tăng bảng và giới hạn của kết luận O(1). Các ví dụ dùng Java 17 trở lên; chi tiết thuật toán nội bộ được gắn với bản OpenJDK 17 cụ thể khi cần.

## 1. Set là yêu cầu về dữ liệu, bảng băm là cách thực hiện

Một set mô tả các thành viên duy nhất. Nếu đã có A, thêm A nữa không tạo thêm một thành viên A thứ hai.

Một bảng băm là cấu trúc lưu trữ và tìm kiếm dựa trên mã băm của khóa. Nó không tự mang ý nghĩa “tập hợp” hay “từ điển”; ý nghĩa đó phụ thuộc cách cấu trúc được sử dụng.

Có thể nhìn các lớp theo ba tầng:

~~~text
Set<E>
  hop dong: khong co thanh vien trung

HashSet<E>
  mot cach thuc hien hop dong Set

HashMap<E, gia tri danh dau>
  co che bang bam giup quan ly cac khoa duy nhat
~~~

Ở đây, “bảng băm” là thuật ngữ cấu trúc dữ liệu. Nó không có nghĩa là `HashSet` dùng lớp Java tên `Hashtable`. [Tài liệu HashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html) nêu rõ map hậu thuẫn là `HashMap`.

### 1.1. Vì sao không chỉ giữ một danh sách?

Nếu lưu các phần tử trong một danh sách thông thường, để kiểm tra phần tử mới đã tồn tại chưa có thể phải so sánh với nhiều phần tử trước đó.

Bảng băm dùng một phép tính nhanh để chọn một vùng ứng viên nhỏ hơn. Sau đó, nó vẫn phải kiểm tra khóa phù hợp; mã băm không thay thế hoàn toàn việc xác định bằng nhau.

Đổi lại, cấu trúc cần bộ nhớ cho bảng, các mục lưu khóa và thông tin quản lý. Lợi ích tìm kiếm không phải miễn phí, và phụ thuộc chất lượng phân bố mã băm.

### 1.2. HashSet không cần một giá trị nghiệp vụ cho mỗi phần tử

Một `HashMap<CustomerId, Customer>` lưu ánh xạ từ mã đến dữ liệu khách hàng. Một `HashSet<CustomerId>` chỉ cần ghi nhận mã nào có mặt.

Vì thế, set có thể dùng map mà bỏ ý nghĩa nghiệp vụ của phần giá trị. Mọi khóa được ánh xạ tới cùng một giá trị đánh dấu khác `null`.

Cách dùng này tận dụng việc `HashMap` vốn đã quản lý khóa duy nhất. Không cần phát minh lại một bộ xử lý băm chỉ để xây một tập hợp.

## 2. Phần tử của HashSet nằm ở phía khóa của HashMap

Giả sử set có các thành viên java và spring. Có thể mô hình hóa map hậu thuẫn như sau:

~~~text
HashSet: {java, spring}

HashMap hau thuan:
key       value
java      PRESENT
spring    PRESENT
~~~

`PRESENT` biểu diễn một đối tượng đánh dấu. Nó không phải dữ liệu mà người dùng của `HashSet` cần truy cập.

Trong [mã nguồn HashSet OpenJDK 17, bản jdk-17+35](https://raw.githubusercontent.com/openjdk/jdk/jdk-17%2B35/src/java.base/share/classes/java/util/HashSet.java), map chứa khóa kiểu E và một giá trị đánh dấu dùng chung.

### 2.1. Bảng đối chiếu thao tác

| Thao tác trên set | Việc tương ứng trên map hậu thuẫn |
|---|---|
| Thêm phần tử e | Đưa e vào phía khóa, gắn giá trị đánh dấu |
| Kiểm tra có e | Kiểm tra có khóa e |
| Xóa e | Xóa ánh xạ có khóa e |
| Số thành viên | Số ánh xạ trong map |
| Duyệt phần tử | Duyệt các khóa |
| Xóa toàn bộ | Xóa các ánh xạ |

Một phần tử không phải được lưu ở phía value để tìm bằng `containsValue`. Tính duy nhất đến từ các khóa.

### 2.2. Vì sao add biết lần này có mới không?

`HashMap.put` trả về giá trị cũ gắn với khóa, hoặc `null` khi không có ánh xạ trước đó. Trong map nội bộ của `HashSet`, mọi ánh xạ hiện có đều có giá trị đánh dấu khác `null`.

Do đó:

~~~text
put khoa moi:
  khong co gia tri cu -> tra null
  HashSet.add -> true

put khoa da co:
  gia tri cu la dau PRESENT
  HashSet.add -> false
~~~

Không cần một lần tìm toàn set riêng để xác định kết quả sau khi thêm. Bước đưa vào map đã biết khóa có tồn tại hay chưa.

Trong chương trình học tập bên dưới, `Boolean.TRUE` được dùng làm dấu để minh họa. Nó không phải mô tả rằng `HashSet` thật lưu giá trị Boolean; triển khai được tham chiếu dùng đối tượng đánh dấu riêng.

### 2.3. Tại sao phải nhấn mạnh giá trị đánh dấu khác null?

Với một `HashMap` tổng quát, map có thể có một khóa đang được ánh xạ tới giá trị `null`. Khi put khóa đó, giá trị trả về cũ vẫn là `null`, dù khóa đã có mặt.

Bởi vậy, quy tắc:

~~~text
put(...) tra null -> chac chan khoa truoc do chua ton tai
~~~

không đúng cho mọi cách dùng `HashMap`.

Nó dùng được để nhận diện thêm mới trong mô hình của set vì có điều kiện bổ sung: mọi thành viên hiện có luôn mang một dấu khác `null`.

Nếu tự xây một cấu trúc dựa trên map, phải bảo toàn điều kiện của mô hình. Đừng lấy một biểu thức đúng trong map nội bộ có kiểm soát rồi áp dụng cho một map cho phép mọi giá trị tùy ý.

### 2.4. Map hậu thuẫn là chi tiết nội bộ

Người dùng không nhận được map nội bộ để tự thay giá trị hoặc thêm/xóa khóa theo đường riêng. API `HashSet` cung cấp các thao tác set.

Mối quan hệ này giải thích cơ chế, không phải lời mời ép kiểu `HashSet` thành `HashMap`, hay dùng reflection vào trường riêng để viết mã ứng dụng. `HashSet` cũng không kế thừa `HashMap`; nó sử dụng map như một thành phần bên trong.

## 3. Bảng băm tổ chức việc tìm khóa như thế nào?

Một mô hình phổ biến của bảng băm gồm:

- Một mảng các **bucket** — các ngăn chứa theo chỉ số.
- Mã băm của mỗi khóa.
- Các mục lưu khóa và thông tin liên kết trong bucket.
- Cách kiểm tra bằng nhau khi có nhiều khóa cùng vùng.

Quá trình khái quát:

~~~text
phan tu / khoa
      |
      v
hashCode cua khoa
      |
      v
xu ly ma bam, tinh chi so bucket
      |
      v
chon bucket ung vien
      |
      v
kiem tra khoa phu hop trong bucket
      |
      v
ket luan co / khong co
~~~

Mục tiêu là không phải xét mọi bucket và mọi phần tử cho một lần `contains`.

### 3.1. Mã băm không phải chỉ số danh sách

`hashCode()` trả một số nguyên `int`, có thể âm. Nó không trực tiếp là một vị trí từ 0 đến size−1.

Bảng cần biến mã băm thành chỉ số nằm trong số bucket hiện có. Kích thước bảng là một thông tin của cấu trúc, tách khỏi số phần tử.

Không có hợp đồng rằng phần tử có hash bằng 100 luôn nằm ở “ô 100”. Cũng không có `HashSet.get(100)` để lấy phần tử theo bucket.

### 3.2. Ví dụ với 16 bucket

Ở mô hình sử dụng các bit thấp như OpenJDK được tham chiếu, các số nguyên 1, 17 và 33 có thể cùng rơi vào bucket 1 khi bảng dài 16.

~~~text
bucket 0:  ...
bucket 1:  [key=1] -> [key=17] -> [key=33]
bucket 2:  ...
...
bucket 15: ...
~~~

Sơ đồ chỉ minh họa một bucket có nhiều mục; nó không được đọc trực tiếp từ một đối tượng `HashSet` đang chạy.

Nếu ba khóa khác nhau được lưu, size của set là 3 dù mới dùng một bucket trong ví dụ này. Một bucket không bị giới hạn chỉ chứa một thành viên.

### 3.3. Size, capacity và số bucket đang dùng là ba con số khác nhau

| Đại lượng | Ý nghĩa |
|---|---|
| `size` | Số thành viên/khóa hiện có |
| Capacity | Số bucket của bảng nền khi đã cấp |
| Số bucket không rỗng | Bao nhiêu ngăn đang có ít nhất một mục |

Va chạm làm nhiều khóa cùng bucket, nên số bucket không rỗng có thể nhỏ hơn size.

Capacity lớn hơn size không có nghĩa đã có các thành viên rỗng hoặc `null` trong set. Các bucket chưa dùng là vùng lưu trữ nội bộ, không phải phần tử của tập hợp.

## 4. Va chạm bucket khác phần tử trùng

### 4.1. Cùng bucket chưa chắc cùng hash

Với 16 bucket, hai mã băm khác nhau vẫn có thể cho cùng chỉ số sau khi thu hẹp vào khoảng 0..15.

Ví dụ hash 1 và hash 17 cùng có thể vào bucket 1. Nếu chỉ so chỉ số bucket rồi kết luận trùng, cấu trúc sẽ làm mất rất nhiều giá trị khác nhau.

Triển khai có thể kiểm tra mã băm đã lưu trước khi cần so sánh khóa. Không phải mọi khóa cùng bucket nhất thiết làm phát sinh một lần gọi `equals`; các bước lọc nội bộ có thể loại nhanh ứng viên không phù hợp.

### 4.2. Cùng hash cũng chưa chắc bằng nhau

Hai đối tượng khác nhau có thể có cùng mã băm hoàn chỉnh. Đây là một loại va chạm hash.

Một `HashSet` đúng phải vẫn giữ cả hai nếu chúng không bằng nhau theo hợp đồng của khóa. Chương trình dùng hai khóa thử nghiệm A và B đều có hash 7 để kiểm chứng việc này.

~~~text
hash(A) = 7
hash(B) = 7
A khong equals B

add(A) -> true
add(B) -> true
size = 2
~~~

Mã băm không phải mã định danh duy nhất, không phải bằng chứng hai đối tượng giống nhau và không phải giá trị nên dùng để loại trùng một mình.

### 4.3. Điều kiện một chiều của equals/hashCode

Hai đối tượng bằng nhau theo `equals` phải có cùng `hashCode`. Chiều ngược không bắt buộc.

Đây là yêu cầu trong [hợp đồng Object.hashCode Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#hashCode()).

~~~text
equals true -> hashCode phai bang nhau
hashCode bang nhau -> van can xet equals
~~~

Nếu một lớp ghi đè `equals` nhưng không cung cấp `hashCode` tương ứng, set băm có thể không quản lý các đối tượng theo định nghĩa trùng mà tác giả muốn. Cấu trúc không thể sửa hộ một hợp đồng khóa sai.

### 4.4. Hash ổn định theo dữ liệu khóa, không nhất thiết theo cả đời ứng dụng

Khi phần tử đang là khóa trong set băm, các thông tin dùng để so sánh và băm phải được quản lý ổn định. Nếu sửa các trường đó ngay khi phần tử vẫn nằm trong set, việc tìm/xóa có thể không còn hoạt động theo mong muốn; hợp đồng set không quy định hành vi cho thay đổi ảnh hưởng equality như vậy.

Điều này không có nghĩa mọi trường trong đối tượng đều phải bất biến trong mọi thiết kế. Điều quan trọng là những trường tham gia tiêu chí thành viên.

Nếu bảng tăng kích thước, không được coi đó là cơ chế tự sửa vấn đề một khóa đã bị đổi hash sau khi thêm.

## 5. Một số chi tiết của OpenJDK 17: trộn hash, chọn bucket và tăng bảng

Các thông tin dưới đây mô tả bản mã nguồn được tham chiếu, không phải yêu cầu bắt buộc mọi triển khai `Set` phải dùng cùng công thức.

### 5.1. Trộn bit trước khi chọn bucket

Trong [HashMap OpenJDK 17, bản jdk-17+35](https://raw.githubusercontent.com/openjdk/jdk/jdk-17%2B35/src/java.base/share/classes/java/util/HashMap.java), hash của khóa khác `null` được xử lý bằng cách kết hợp nó với bản dịch phải không dấu 16 bit.

Mô hình phép tính:

~~~text
raw    = key.hashCode()
spread = raw XOR (raw >>> 16)
index  = spread AND (capacity - 1)
~~~

Capacity trong cách tính này là lũy thừa của 2. Việc đưa ảnh hưởng các bit cao xuống thấp giúp tránh chỉ nhìn một nhóm bit thấp chưa có phân bố tốt.

Bước trộn không làm hai mã băm bằng nhau thành khác nhau, và không bảo đảm mọi khóa đều vào bucket riêng. Nó cũng không biến `hashCode` thông thường thành một hàm băm mật mã.

### 5.2. Bucket phụ thuộc kích thước bảng

Khi đổi từ 16 lên 32 bucket, thêm một bit được dùng trong mặt nạ. Khóa 17 đang ở bucket 1 của mô hình 16 có thể chuyển sang bucket 17 của mô hình 32.

Số 1 vẫn ở bucket 1; số 33 có thể vẫn cùng bucket với 1. Tăng bảng có thể tách một số va chạm, không có nghĩa loại bỏ mọi va chạm.

Chương trình tính hai cột bucket với capacity giả định 16 và 32. Nó không đọc capacity thật của `HashSet` và không hứa các đối tượng thử nghiệm đang dùng đúng những bảng đó ở thời điểm in.

### 5.3. Load factor diễn đạt mức tải cho việc tăng

**Hệ số tải (load factor)** là tham số giúp xác định khi nào bảng cần tăng theo số khóa đang có. Nó không phải tỷ lệ bucket không rỗng được đo trực tiếp.

Với capacity c và hệ số tải α, ngưỡng số khóa thường được tính gần c×α, có xử lý số nguyên và giới hạn trong triển khai.

Ví dụ thông thường với c = 16 và α = 0,75 cho ngưỡng 12. Ở triển khai đang xét, thêm một khóa mới làm size vượt ngưỡng này có thể kích hoạt tăng bảng. Nếu chỉ thêm lại khóa cũ, size không tăng theo cách đó.

[Tài liệu HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html) giải thích quan hệ giữa capacity, load factor và việc tăng bảng.

### 5.4. Tăng bảng là công việc thật

Khi tăng, phải có bảng bucket lớn hơn và phân bố lại các mục theo vị trí mới. Mục đích là giữ quá trình tìm kiếm hiệu quả khi số phần tử tăng.

Trong OpenJDK được nêu, mã băm đã lưu trong các mục được dùng khi phân bố lại; không nên diễn đạt đơn giản rằng mọi lần resize nhất thiết gọi lại `hashCode()` của từng đối tượng.

Một lần thêm gây tăng có thể tốn nhiều công việc hơn lần thêm bình thường. Vì vậy, hiệu năng tốt trung bình/khấu hao không phải một cam kết thời gian tối đa cố định cho từng lời gọi.

### 5.5. Bucket đông có thể đổi cách tổ chức

Một bucket có thể chứa một chuỗi các mục. Khi số mục và kích thước bảng đạt điều kiện nội bộ phù hợp, OpenJDK có thể chuyển vùng đó sang cấu trúc cây; khi bảng còn nhỏ, việc tăng bảng có thể được ưu tiên.

Không nên học một câu thiếu điều kiện như “cứ có đúng N phần tử va chạm là luôn thành cây”. Cũng không suy ra rằng mọi lần truy cập sau đó được bảo đảm O(log n) trong mọi tình huống khóa và so sánh.

Cơ chế cây là một biện pháp xử lý một số tình huống va chạm nặng. Nó không thay thế việc thiết kế `hashCode` hợp lý, không làm HashSet trở thành TreeSet và không tạo thứ tự sắp xếp toàn bộ set.

## 6. Null, thứ tự duyệt và những điều cơ chế băm không bảo đảm

### 6.1. Vì sao HashSet cho một null?

Map hậu thuẫn cho phép khóa `null`. `HashSet` dùng điều đó để biểu diễn một thành viên `null`, vẫn gắn giá trị đánh dấu khác `null`.

Lần đầu `add(null)` có thể trả true; lần sau trả false. Kích thước chỉ tính một thành viên null.

Ở mô hình chọn bucket của OpenJDK đang xét, `null` có hash nội bộ 0. Bucket 0 không phải khu vực dành riêng cấm các khóa khác: một khóa khác có chỉ số bucket 0 vẫn có thể cùng được quản lý ở đó.

### 6.2. HashSet không lưu thứ tự chèn hay thứ tự tăng dần

Thứ tự duyệt chịu ảnh hưởng bảng và cách các mục được sắp bên trong; API không hứa giữ một thứ tự cụ thể. Khi bảng tăng hoặc dữ liệu thay đổi, không nên dựa vào một thứ tự từng quan sát.

Nếu cần thứ tự gặp đầu tiên, hãy chọn một cấu trúc/hợp đồng giữ thứ tự. Nếu cần sắp xếp, hãy thực hiện bước sắp xếp hoặc dùng một tập hợp có hợp đồng phù hợp.

Trong ví dụ, việc in các thành viên theo thứ tự tăng dần được làm bằng một bước riêng chỉ để kết quả dễ đối chiếu. Nó không chứng minh `HashSet` tự sắp xếp.

### 6.3. Mã băm của cả set khác mã băm từng phần tử

`set.hashCode()` là mã băm của đối tượng tập hợp theo hợp đồng `Set`, được xây từ các mã băm thành viên. Nó không trả bucket của một phần tử, capacity hay size.

[Set.hashCode Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#hashCode()) định nghĩa mã băm set bằng tổng các mã băm thành viên, với null đóng góp 0. Quy tắc này giúp các set bằng nhau có mã băm bằng nhau mà không phụ thuộc thứ tự duyệt.

Cần phân biệt:

| Khái niệm | Dùng để hiểu điều gì? |
|---|---|
| `element.hashCode()` | Mã băm của khóa/phần tử |
| Hash đã trộn nội bộ | Hỗ trợ tính vị trí trong bảng cụ thể |
| Chỉ số bucket | Vùng ứng viên của một bảng có capacity cụ thể |
| `set.hashCode()` | Mã băm của cả tập hợp theo hợp đồng equality của set |
| `set.size()` | Số thành viên |

## 7. Chương trình hoàn chỉnh: đối chiếu HashSet với mô hình map

Ví dụ dưới đây không tự triển khai một bảng băm hoàn chỉnh. Nó dùng `HashMap` công khai để mô hình hóa cách ghi nhận thành viên, rồi so kết quả với một `HashSet` thật.

Phần bucket là một phép tính giải thích của OpenJDK 17 với capacity cho trước, không phải kiểm tra các trường riêng của `HashSet`.

### 7.1. Mã nguồn

Lưu thành `HashSetTableDemo.java`:

~~~java
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class HashSetTableDemo {
    // Khoa thi nghiem: co tinh cho moi id cung hash de tao va cham.
    record SameHashKey(String id) {
        SameHashKey {
            Objects.requireNonNull(id, "id");
        }

        @Override
        public int hashCode() {
            return 7;
        }
    }

    // Mo hinh vi tri bucket cua OpenJDK 17, khong doc bang noi bo.
    static int modeledBucket(Object key, int capacity) {
        if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("capacity must be a positive power of two");
        }
        int raw = key == null ? 0 : key.hashCode();
        int spread = raw ^ (raw >>> 16);
        return spread & (capacity - 1);
    }

    static List<String> sortedMembers(Set<String> values) {
        Set<String> ordered = new TreeSet<>(
                Comparator.nullsFirst(Comparator.naturalOrder()));
        ordered.addAll(values);
        return new java.util.ArrayList<>(ordered);
    }

    public static void main(String[] args) {
        Set<String> actual = new HashSet<>();
        Map<String, Boolean> membershipModel = new HashMap<>();
        for (String key : Arrays.asList("java", "spring", "java", null, null)) {
            boolean setAdded = actual.add(key);
            boolean modelAdded = membershipModel.put(key, Boolean.TRUE) == null;
            System.out.printf("add(%s): set=%s, model=%s, sameKeys=%s, size=%d%n",
                    key, setAdded, modelAdded,
                    actual.equals(membershipModel.keySet()), actual.size());
        }
        System.out.println("members sorted for display: " + sortedMembers(actual));
        System.out.printf("contains java: set=%s, model=%s%n",
                actual.contains("java"), membershipModel.containsKey("java"));
        boolean setRemoved = actual.remove("java");
        boolean modelRemoved = membershipModel.remove("java") != null;
        System.out.printf("remove java: set=%s, model=%s, sameKeys=%s%n",
                setRemoved, modelRemoved, actual.equals(membershipModel.keySet()));

        System.out.println("modeled buckets: key, capacity16, capacity32");
        for (Integer key : Arrays.asList(1, 17, 33, null)) {
            System.out.printf("%s, %d, %d%n", key,
                    modeledBucket(key, 16), modeledBucket(key, 32));
        }

        Set<SameHashKey> collisions = new HashSet<>();
        System.out.println("same-hash A: " + collisions.add(new SameHashKey("A")));
        System.out.println("same-hash B: " + collisions.add(new SameHashKey("B")));
        System.out.println("same-hash A again: " + collisions.add(new SameHashKey("A")));
        System.out.println("same-hash size: " + collisions.size());
        System.out.println("same-hash ids sorted: "
                + collisions.stream().map(SameHashKey::id).sorted().toList());

        Map<String, Boolean> unsafeNullValueModel = new HashMap<>();
        unsafeNullValueModel.put("present", null);
        boolean alreadyPresent = unsafeNullValueModel.containsKey("present");
        Boolean previous = unsafeNullValueModel.put("present", Boolean.TRUE);
        System.out.printf("general map: alreadyPresent=%s, previousIsNull=%s%n",
                alreadyPresent, previous == null);

        Set<Integer> resizedByImplementation = new HashSet<>();
        for (int value = 0; value < 100; value++) {
            resizedByImplementation.add(value);
        }
        boolean allFound = true;
        for (int value = 0; value < 100; value++) {
            allFound &= resizedByImplementation.contains(value);
        }
        System.out.printf("after 100 distinct adds: size=%d, allFound=%s%n",
                resizedByImplementation.size(), allFound);

        Set<String> differentSize = new HashSet<>(List.of("java", "spring"));
        System.out.println("set hashCode is not size: "
                + (differentSize.hashCode() != differentSize.size()));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 HashSetTableDemo.java
java HashSetTableDemo
~~~

### 7.2. Kết quả stdout

~~~text
add(java): set=true, model=true, sameKeys=true, size=1
add(spring): set=true, model=true, sameKeys=true, size=2
add(java): set=false, model=false, sameKeys=true, size=2
add(null): set=true, model=true, sameKeys=true, size=3
add(null): set=false, model=false, sameKeys=true, size=3
members sorted for display: [null, java, spring]
contains java: set=true, model=true
remove java: set=true, model=true, sameKeys=true
modeled buckets: key, capacity16, capacity32
1, 1, 1
17, 1, 17
33, 1, 1
null, 0, 0
same-hash A: true
same-hash B: true
same-hash A again: false
same-hash size: 2
same-hash ids sorted: [A, B]
general map: alreadyPresent=true, previousIsNull=true
after 100 distinct adds: size=100, allFound=true
set hashCode is not size: true
~~~

### 7.3. Đọc kết quả theo từng cơ chế

Mỗi lần thêm vào `HashSet` được đối chiếu với việc đặt cùng khóa vào map mô hình. `sameKeys=true` cho biết các tập khóa bằng nhau; đây là so sánh thành viên, không phải so sánh thứ tự.

Hai lần thêm java cho true rồi false; hai lần thêm null cũng vậy. Điều này xuất phát từ tính duy nhất của khóa trong map, không từ việc các value đánh dấu có khác nhau hay không.

`members sorted for display` dùng một `TreeSet` với comparator chấp nhận null để in ổn định. HashSet gốc vẫn không có bảo đảm thứ tự.

Cột modeled buckets cho thấy 1, 17, 33 cùng bucket ở capacity 16, nhưng 17 tách sang bucket 17 khi dùng capacity 32. Cả hai bảng chỉ là giả định đầu vào cho hàm `modeledBucket`.

`SameHashKey` là record có equality theo id và một `hashCode` cố ý luôn trả 7. Cách băm này đúng điều kiện “bằng nhau thì cùng hash” nhưng có phân bố rất kém; chỉ dùng để tạo va chạm trong bài học, không phải mẫu thiết kế băm nên dùng trong ứng dụng.

A và B cùng hash vẫn được giữ thành hai thành viên. Thêm một A khác lại trả false vì equality theo id nhận diện A đã có.

Map tổng quát ở cuối có khóa present đang gắn value null. `containsKey` báo đã có khóa trong khi giá trị cũ của put vẫn là null. Đây là lý do mô hình set cần quản lý một giá trị đánh dấu không null.

Sau 100 lần thêm số nguyên khác nhau, size là 100 và mọi giá trị đều tìm thấy. Chương trình chỉ kiểm tra nội dung qua API; nó không đọc được số lần resize hay capacity thật.

Dòng cuối dùng một ví dụ có mã băm set khác size để nhắc rằng hai phương thức có hai hợp đồng. Nó không khẳng định chúng không bao giờ tình cờ cho cùng một số.

## 8. Mô hình hiệu năng phải có điều kiện

### 8.1. Khi nào nói các thao tác cơ bản gần O(1)?

Với hàm băm có phân bố tốt, số ứng viên trong bucket được giữ hợp lý và chi phí `hashCode/equals` không quá lớn, các thao tác tìm, thêm, xóa có hiệu năng kỳ vọng tốt.

Đối với thêm nhiều phần tử, còn cần phân bổ chi phí những lần tăng bảng trên cả chuỗi thao tác. Không được biến kết luận kỳ vọng/khấu hao thành lời hứa trường hợp xấu nhất hằng số cho mọi đầu vào.

Một cách tách chi phí là:

~~~text
tinh hash cua khoa
+ chon bucket
+ kiem tra cac ung vien can thiet
+ cap/cap nhat muc
+ cong viec tang bang neu lan nay can
~~~

Nếu `hashCode` quét một chuỗi rất dài hoặc `equals` làm công việc nặng, thời gian đó vẫn phải được tính. Hằng số theo số thành viên không đồng nghĩa hằng số theo mọi kích thước dữ liệu.

### 8.2. Vì sao capacity ảnh hưởng cả bộ nhớ và duyệt?

Tài liệu HashSet mô tả chi phí duyệt tỷ lệ với số thành viên cộng capacity của map hậu thuẫn. Một bảng rất rộng nhưng ít phần tử có thể lãng phí bộ nhớ và khiến việc đi qua bảng kém phù hợp hơn.

Đặt capacity quá thấp có thể tăng số lần cấp/tăng bảng; đặt quá cao có thể giữ nhiều bucket chưa dùng. Load factor là một đánh đổi liên quan, không phải tham số cứ càng thấp càng tốt.

Vì thế, không nên cấp trước một số cực lớn chỉ để khẳng định “không bao giờ resize”. Cần dựa vào số lượng thành viên dự kiến và kiểu thao tác thực tế.

### 8.3. Constructor capacity không phải số thành viên được điền

`new HashSet<>(16)` tạo một set rỗng, không tạo 16 phần tử và không bảo đảm rằng phải thêm đủ 16 khóa mới rồi mới xét tăng bảng.

Với hệ số tải mặc định 0,75, cần phân biệt số bucket dự kiến với ngưỡng số khóa. Trong bản OpenJDK được nêu, việc cấp mảng bucket còn được trì hoãn đến lúc cần; constructor rỗng không đồng nghĩa ngay lập tức đã tạo một mảng chứa đủ các mục dữ liệu.

Không có getter công khai của `HashSet` để lấy capacity thật. Ví dụ học tập dùng capacity cố định phải nói rõ đó là mô hình, như chương trình ở bài này.

### 8.4. Chọn cấu trúc theo câu hỏi dữ liệu

HashSet phù hợp khi cần quản lý thành viên duy nhất và kiểm tra có/không theo equality. Nếu cần lưu giá trị ứng với khóa, dùng một `Map` có thể biểu diễn mục tiêu trực tiếp hơn.

Nếu cần giữ số lần xuất hiện, set một mình không giữ thông tin đó. Nếu cần thứ tự, set băm thông thường cũng chưa đáp ứng hợp đồng trình bày.

Không dùng khả năng tìm kiếm nhanh để bỏ qua việc xác định đúng mô hình dữ liệu.

## 9. Các điều kiện sử dụng cần giữ

### 9.1. Khóa có hợp đồng đúng và ổn định

Định nghĩa `equals/hashCode` theo cùng tiêu chí thành viên. Khi có thể, dùng khóa có các thành phần ổn định như record chứa chuỗi bất biến hoặc các kiểu giá trị phù hợp.

Nếu phần tử có các trường có thể thay đổi, hãy biết trường nào tham gia equality/hash. Không sửa chúng ngay trong thời gian cấu trúc đang dùng phần tử làm khóa.

### 9.2. HashSet không tự đồng bộ

Sử dụng map hậu thuẫn không làm các thao tác trên `HashSet` tự an toàn cho nhiều luồng cùng sửa. Cũng không thể dựa vào fail-fast để bảo vệ một quy trình đồng thời.

Nếu cần chia sẻ để ghi, phải có cách phối hợp hoặc cấu trúc đáp ứng yêu cầu. Ví dụ trong bài dùng một luồng và không chứng minh hành vi đồng thời.

### 9.3. Đọc mã nguồn để hiểu, không ràng buộc nghiệp vụ vào chi tiết riêng

Các công thức trộn hash, hình thức bucket và điều kiện chuyển cây có thể khác giữa các triển khai/phiên bản. Mã ứng dụng nên dựa vào hợp đồng set và những bảo đảm hiệu năng được tài liệu hóa phù hợp.

Những điều cần giữ bền vững hơn các con số nội bộ là: phần tử là khóa, equality/hash phải đúng, va chạm không đồng nghĩa trùng, và thứ tự không được suy đoán từ bảng băm.

## 10. Những hiểu nhầm thường gặp

| Nhận định | Cách hiểu đúng |
|---|---|
| HashSet kế thừa HashMap | Nó dùng map như một thành phần bên trong |
| Phần tử set là value của map | Phần tử được dùng làm key; value chỉ là dấu hiện diện |
| Mỗi phần tử cần một giá trị đánh dấu riêng | Dấu có thể dùng chung; tính duy nhất nằm ở key |
| Put trả null luôn nghĩa là key mới | Chỉ suy ra được khi biết map không lưu value null cho key đã có |
| Hash bằng nhau thì phải là cùng phần tử | Vẫn phải xét equality |
| Cùng bucket thì hash chắc chắn bằng nhau | Chỉ số bucket là kết quả thu hẹp, nhiều hash khác nhau có thể trùng bucket |
| Một bucket chỉ được một phần tử | Có cơ chế quản lý nhiều mục trong cùng bucket |
| Capacity bằng size | Capacity là số bucket, size là số khóa |
| Null chiếm một bucket riêng cấm khóa khác | Nó là một khóa được xử lý đặc biệt; bucket vẫn có thể có khóa khác |
| Resize sửa được mọi khóa đã đổi hash | Không được dựa vào resize để sửa hợp đồng khóa bị vi phạm |
| Bucket thành cây thì cả set có thứ tự | Tổ chức cục bộ của bucket không tạo hợp đồng sắp xếp toàn set |
| Tìm kiếm O(1) nghĩa là mọi lần đều cùng độ trễ | Cần xét băm, so sánh, va chạm, cấp phát và tăng bảng |
| set.hashCode là vị trí bucket hoặc số phần tử | Đó là mã băm của cả set theo hợp đồng Set |
| new HashSet(16) chứa sẵn 16 phần tử | Nó vẫn rỗng; tham số liên quan dung lượng bảng |

## 11. Bài tập tự kiểm tra

### Bài 1: quan hệ key/value

Set chứa ba thành viên A, B, C. Trong mô hình map hậu thuẫn, có bao nhiêu key và cần bao nhiêu loại value nghiệp vụ?

**Giải thích:** Có ba key; không cần value nghiệp vụ riêng cho từng key. Các key có thể cùng gắn một đối tượng đánh dấu hiện diện.

### Bài 2: trùng hash

Hai khóa có hash 7 nhưng equals false. HashSet có buộc chỉ giữ một khóa không?

**Giải thích:** Không. Bảng phải xử lý va chạm và giữ cả hai thành viên khác nhau khi dữ liệu hợp lệ.

### Bài 3: cùng bucket

Vì sao hash 1 và 17 có thể cùng vào bucket 1 khi có 16 bucket nhưng không cùng bucket khi có 32 trong mô hình của bài?

**Giải thích:** Với lũy thừa 2, mặt nạ dùng các bit thấp theo capacity. Tăng từ 16 lên 32 dùng thêm một bit; hash 17 có thể được tách sang chỉ số 17. Đây là vị trí của bảng cụ thể, không phải danh tính khóa.

### Bài 4: map có value null

Map đã có `K -> null`. Put K với một dấu khác null trả null. Có thể kết luận K trước đó chưa có không?

**Giải thích:** Không. Giá trị trả về cũ có thể null vì key đã ánh xạ tới null. Mô hình set tránh nhập nhằng bằng cách không dùng null làm value đánh dấu cho thành viên hiện có.

### Bài 5: size và bucket

Có 20 thành viên nhưng chỉ 7 bucket không rỗng do va chạm. Size của set là 7 hay 20?

**Giải thích:** 20. Số bucket được dùng không phải số thành viên.

### Bài 6: đầu vào thay đổi

Một đối tượng đã được thêm rồi bị đổi trường dùng tính hash. Có nên đợi lần tăng bảng để “tính lại và sửa vị trí” không?

**Giải thích:** Không. Hãy bảo toàn hợp đồng khóa trong suốt thời gian nó được giữ. Resize không phải cơ chế sửa một thay đổi dữ liệu làm vi phạm các giả định của set.

### Bài 7: thứ tự

Một HashSet các số nguyên tình cờ in theo thứ tự tăng dần. Có thể coi đó là một sorted set không?

**Giải thích:** Không. Cần một hợp đồng sắp xếp hoặc bước sắp xếp rõ ràng. Thứ tự quan sát được không trở thành bảo đảm của HashSet.

Nhìn `HashSet` như một tập các khóa của bảng băm giúp nối hợp đồng “thành viên duy nhất” với cơ chế lưu trữ cụ thể. Khả năng tìm nhanh đến từ việc chọn vùng ứng viên bằng hash; tính đúng vẫn dựa vào equality, quản lý va chạm và các điều kiện của khóa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-based-set-002">Thêm, tìm, xóa và điều kiện đạt hiệu năng mong đợi</a>

<details>
<summary>Xem chi tiết</summary>

# Thêm, tìm, xóa và điều kiện đạt hiệu năng mong đợi của Set dùng băm

## 1. HashSet giải quyết bài toán gì?

Một chương trình xử lý nhiều mã công việc thường phải trả lời liên tục: “mã này đã xuất hiện chưa?”, “đây có phải lần đầu nhận mã này không?” hoặc “mã này còn nằm trong nhóm đang chờ không?”.

Nếu tự duyệt toàn bộ danh sách cho mỗi câu hỏi, số lần so sánh có thể tăng theo số phần tử đã lưu. `HashSet<E>` tổ chức dữ liệu bằng băm để thu hẹp nơi cần tìm, thay vì buộc mỗi truy vấn phải quét mọi phần tử.

Ba thao tác trọng tâm là:

| Mục đích | Phương thức | Ý nghĩa giá trị trả về |
| --- | --- | --- |
| Thêm nếu chưa có | `boolean add(E value)` | true nếu tập có thêm phần tử |
| Kiểm tra có thuộc tập không | `boolean contains(Object value)` | true nếu tìm thấy phần tử phù hợp |
| Xóa nếu đang có | `boolean remove(Object value)` | true nếu tập có phần tử bị xóa |

Tài liệu `HashSet` mô tả các thao tác cơ bản có thời gian hằng số khi hàm băm phân bố phần tử phù hợp giữa các ngăn của bảng. Trong thực hành thường nói `add`, `contains`, `remove` có O(1) kỳ vọng; với thêm mới còn phải xét chi phí tăng dung lượng theo cách phân tích khấu hao.

Điều kiện phân bố băm là một phần của kết luận, không phải chi tiết có thể bỏ qua. O(1) kỳ vọng không có nghĩa mỗi lời gọi luôn mất đúng cùng số nano giây, không có va chạm, không phải cấp phát bộ nhớ hoặc không bao giờ tăng dung lượng. [HashSet: mô hình và hiệu năng](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)

Bài này tập trung vào `HashSet` trên Java 17+. Khi một biến chỉ có kiểu `Set`, cần biết đối tượng thực tế là gì trước khi suy ra mô hình hiệu năng. Giao diện `Set` không bắt buộc mọi triển khai phải dùng băm.

## 2. Mô hình tìm phần tử bằng băm

### 2.1. Bảng băm giúp thu hẹp vùng tìm

`HashSet` dùng một `HashMap` ở phía sau để quản lý các phần tử. Với người sử dụng tập, không cần tự thao tác trên map đó; điều cần hiểu là quan hệ giữa hai phương thức của phần tử:

- `hashCode()` giúp xác định vùng có thể chứa phần tử.
- `equals(...)` xác định hai giá trị có thực sự bằng nhau không.

Có thể hình dung một truy vấn bằng sơ đồ:

```text
giá trị cần tìm
    ↓
tính mã băm
    ↓
chọn ngăn (bucket) tương ứng trong bảng
    ↓
đối chiếu các ứng viên trong vùng đó
    ↓
phân biệt bằng nhau bằng quy tắc equals
    ↓
tìm thấy / không tìm thấy
```

Đây là mô hình để suy luận, không phải công thức tính chỉ số ngăn hoặc mô tả bắt buộc của mọi phiên bản JDK.

Một `HashSet` không sắp xếp phần tử theo giá trị để thực hiện các thao tác trên. Nó cũng không có chỉ số để người dùng yêu cầu “xóa phần tử ở vị trí 3”. Nếu cần loại bỏ một giá trị, truyền chính giá trị truy vấn vào `remove`.

### 2.2. Trùng mã băm chưa phải trùng phần tử

Hai đối tượng khác nhau có thể có cùng mã băm. Đó là **va chạm băm (hash collision)**.

Ví dụ, hai khóa công việc `id=10` và `id=20` có thể bị một hàm băm kém đưa tới cùng vùng. Tập vẫn cần dùng quy tắc bằng nhau để giữ chúng là hai phần tử nếu chúng không bằng nhau.

Do đó:

```text
equals true  → bắt buộc cùng hashCode
cùng hashCode → chưa đủ để kết luận equals true
```

Nếu chỉ có một vài va chạm trong một bảng được phân bố tốt, cơ chế này vẫn hữu ích. Nếu gần như mọi khóa dồn vào cùng một vùng, việc thu hẹp vùng tìm mất phần lớn lợi ích.

Cùng mã băm không làm tập tự bỏ mất các khóa khác nhau. Ngược lại, định nghĩa sai `equals` có thể làm các giá trị mà nghiệp vụ muốn giữ riêng bị xem là trùng. Tính đúng của quy tắc bằng nhau phải được giải quyết trước khi tối ưu phân bố băm. [Object: hợp đồng equals và hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html)

## 3. Đọc đúng kết quả add, contains và remove

### 3.1. add trả lời tập có nhận thêm một phần tử không

Với `HashSet` có quy tắc phần tử ổn định:

- Chưa có phần tử bằng giá trị cần thêm: tập thêm phần tử, kích thước tăng 1, trả về `true`.
- Đã có phần tử bằng giá trị đó: tập không thêm bản sao, kích thước không đổi, trả về `false`.

Hai đối tượng được tạo riêng vẫn có thể là một giá trị đối với tập. Vì vậy, “đã dùng `new` lần nữa” không phải lý do để mong kích thước tăng.

Khi chỉ muốn xử lý lần đầu gặp một mã, dùng trực tiếp kết quả `add`:

```java
// Đoạn minh họa: seen là HashSet<String> do luồng hiện tại quản lý.
if (seen.add(code)) {
    System.out.println("Lần đầu gặp mã: " + code);
}
```

Không cần viết trước `if (!seen.contains(code))` rồi mới `seen.add(code)`. Cách đó thường làm thêm một lần tra cứu mà `add` vốn đã phải thực hiện.

### 3.2. contains là phép đọc, không lấy phần tử ra khỏi tập

`contains(value)` trả về `boolean`, không phải phần tử đang lưu hoặc vị trí phần tử. Nó không thay đổi kích thước tập.

Đối số là `Object`. Ví dụ, với một `HashSet<Integer>` chứa `1`, gọi `contains(1L)` vẫn có thể biên dịch, nhưng `Long` có giá trị `1L` không bằng `Integer` có giá trị `1`.

Không tự suy ra rằng mọi kiểu giá trị “trông giống nhau” trong giao diện người dùng sẽ được tập coi là bằng nhau. Kiểu và cách chuẩn hóa giá trị truy vấn phải khớp quy tắc định danh đang dùng.

### 3.3. remove trả lời có phần tử bị xóa không

`remove(value)` xóa phần tử bằng giá trị truy vấn nếu có và trả về `true`. Nếu không có, tập giữ nguyên và trả về `false`.

Nếu chỉ cần xóa rồi báo tập có thay đổi không:

```java
// Đoạn minh họa: pending là HashSet<TaskId> có thể sửa đổi.
if (pending.remove(taskId)) {
    System.out.println("Đã bỏ công việc khỏi nhóm đang chờ");
}
```

Không cần `contains` trước mỗi lần `remove`. Kết quả xóa đã trả lời việc tập có thay đổi không.

| Lời gọi với một giá trị hợp lệ | Trước thao tác | Kết quả | Sau thao tác |
| --- | --- | --- | --- |
| `add(x)` | Chưa có x | `true` | Có x; size tăng 1 |
| `add(x)` | Đã có giá trị bằng x | `false` | Giữ nguyên |
| `contains(x)` | Có giá trị bằng x | `true` | Giữ nguyên |
| `contains(x)` | Không có x | `false` | Giữ nguyên |
| `remove(x)` | Có giá trị bằng x | `true` | Không còn x; size giảm 1 |
| `remove(x)` | Không có x | `false` | Giữ nguyên |

Bảng giả định phần tử không bị sửa theo cách làm thay đổi quy tắc bằng nhau trong khi đang nằm trong tập và không có tác nhân khác sửa tập trong lúc đang xét.

Gọi trực tiếp `add` hoặc `remove` giúp tránh một phép tra cứu thừa; nó không tự làm `HashSet` an toàn cho nhiều luồng cùng sửa đổi. `HashSet` không cung cấp cơ chế đồng bộ sẵn. [Các thao tác và giới hạn đồng bộ của HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)

## 4. Chương trình đầy đủ: thêm trùng, tìm theo giá trị và xóa

Lưu mã vào **HashSetOperationsDemo.java**:

```java
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HashSetOperationsDemo {
    private record TaskId(String value) {
    }

    public static void main(String[] args) {
        Set<TaskId> pending = new HashSet<>();
        TaskId original = new TaskId("t-10");
        TaskId sameValue = new TaskId("t-10");

        System.out.println("same reference: " + (original == sameValue));
        System.out.println("add first: " + pending.add(original));
        System.out.println("add equal key: " + pending.add(sameValue));
        System.out.println("size after duplicate: " + pending.size());
        System.out.println("contains equal key: "
                + pending.contains(new TaskId("t-10")));
        System.out.println("contains missing key: "
                + pending.contains(new TaskId("t-99")));
        System.out.println("remove equal key: "
                + pending.remove(new TaskId("t-10")));
        System.out.println("remove again: "
                + pending.remove(new TaskId("t-10")));
        System.out.println("empty: " + pending.isEmpty());

        Set<String> tags = new HashSet<>();
        System.out.println("add null: " + tags.add(null));
        System.out.println("add null again: " + tags.add(null));
        System.out.println("contains null: " + tags.contains(null));
        System.out.println("remove null: " + tags.remove(null));

        Set<String> seen = new HashSet<>();
        for (String value : List.of("java", "sql", "java", "http", "sql")) {
            if (seen.add(value)) {
                System.out.println("first encounter: " + value);
            }
        }
        System.out.println("distinct values: " + seen.size());
    }
}
```

Biên dịch theo mức Java 17 và chạy:

```shell
javac --release 17 -encoding UTF-8 HashSetOperationsDemo.java
java HashSetOperationsDemo
```

Stdout:

```text
same reference: false
add first: true
add equal key: false
size after duplicate: 1
contains equal key: true
contains missing key: false
remove equal key: true
remove again: false
empty: true
add null: true
add null again: false
contains null: true
remove null: true
first encounter: java
first encounter: sql
first encounter: http
distinct values: 3
```

Cách đọc kết quả:

1. Hai `TaskId("t-10")` khác tham chiếu nhưng bằng nhau theo giá trị thành phần record.
2. Thêm đối tượng đầu thành công; thêm đối tượng bằng nó không làm kích thước tăng.
3. Có thể dùng một khóa mới tạo nhưng bằng giá trị để tìm hoặc xóa khóa đang lưu.
4. Lần xóa thứ hai trả về `false` vì phần tử đã bị xóa.
5. `HashSet` cho phép `null`, nhưng vẫn chỉ có tối đa một phần tử `null`.
6. Vòng lặp dùng `seen.add(value)` để phát hiện lần đầu gặp từng giá trị.

Ba dòng `first encounter` xuất hiện theo thứ tự duyệt **danh sách đầu vào**. Chương trình in ngay khi `add` trả về `true`; nó không duyệt `HashSet` để in kết quả. Vì vậy, stdout đó không có nghĩa `HashSet` giữ thứ tự chèn.

Record `TaskId` dùng một thành phần `String` bất biến, nên phù hợp để làm khóa giá trị đơn giản trong ví dụ. Không suy ra rằng mọi record đều tự có các thành phần bất biến.

## 5. Điều kiện thứ nhất: equals và hashCode phải đúng, ổn định

### 5.1. Đối tượng bằng nhau phải có cùng mã băm

Nếu viết một lớp mà `equals` cho rằng hai mã công việc bằng nhau nhưng `hashCode` cho ra hai kết quả khác nhau, chương trình đã vi phạm hợp đồng đối tượng.

Khi đó, một phép tìm có thể bắt đầu ở vùng khác với vùng đã dùng lúc chèn. Tăng dung lượng, giảm hệ số tải hoặc viết thêm `contains` trước `remove` không sửa được lỗi hợp đồng này.

Nếu tự định nghĩa `equals`, cần đồng thời thiết kế `hashCode` từ dữ liệu tương ứng. Nếu dùng một kiểu giá trị có sẵn, cần hiểu quy tắc bằng nhau của kiểu đó.

### 5.2. Không thay đổi dữ liệu nhận diện khi phần tử đang ở trong tập

Giả sử một đối tượng dùng trường `code` để tính cả `equals` và `hashCode`. Sau khi đối tượng được thêm vào tập, chương trình đổi `code`.

Từ đó, mã băm và ý nghĩa bằng nhau của đối tượng có thể khác lúc nó được đặt vào cấu trúc. Dù vẫn giữ đúng tham chiếu đối tượng, không thể suy luận rằng `contains` hoặc `remove` sẽ hoạt động như với một khóa ổn định.

Hợp đồng `Set` không quy định hành vi khi một phần tử bị thay đổi theo cách ảnh hưởng đến phép bằng nhau trong khi nó vẫn là phần tử của tập. Không dùng một stdout tình cờ trên một JDK để biến trường hợp này thành quy tắc có thể dựa vào. [Set: phần tử có thể sửa đổi](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

Ưu tiên khóa bất biến. Nếu nghiệp vụ cần đổi định danh, hãy thiết kế rõ quy trình xóa khóa cũ trước khi đổi rồi thêm khóa mới, cùng cách xử lý nếu khóa mới đã tồn tại. Đây là một chuỗi thao tác cần được quản lý, không phải “cập nhật vị trí trong bảng băm” mà tập tự làm khi trường thay đổi.

### 5.3. Quy tắc nhận diện phải khớp dữ liệu thực tế

Một hệ thống có thể quyết định mã `"TASK-1"` và `"task-1"` là cùng một mã hoặc là hai mã khác nhau. `HashSet<String>` mặc định không tự chọn quy tắc nghiệp vụ không phân biệt hoa thường.

Nếu cần chuẩn hóa, thực hiện theo một quy tắc thống nhất tại ranh giới tạo giá trị. Dùng cùng dạng chuẩn khi thêm, tìm và xóa. Tránh việc chỉ chuẩn hóa ở một phía rồi xem kết quả không tìm thấy là vấn đề hiệu năng.

Không đưa những trường chỉ phục vụ hiển thị vào quy tắc định danh nếu chúng không có ý nghĩa định danh. Ngược lại, không bỏ một trường quan trọng chỉ để giảm chi phí tính băm; điều đó có thể làm hai giá trị cần phân biệt bị xem là một.

## 6. Điều kiện thứ hai: mã băm phải phân bố phù hợp

### 6.1. Đúng hợp đồng chưa đủ để chạy nhanh

Một hàm `hashCode` luôn trả về `7` vẫn đáp ứng yêu cầu “hai đối tượng bằng nhau phải cùng mã băm”. Nhưng mọi đối tượng khác nhau cũng bị đưa tới cùng một vùng băm.

Hàm đó có thể đúng về hợp đồng mã băm nhưng rất kém về phân bố. Đây là hai tiêu chí khác nhau:

| Tiêu chí | Câu hỏi cần trả lời |
| --- | --- |
| Tính đúng | Hai giá trị bằng nhau có cùng mã băm không? |
| Chất lượng phân bố | Các giá trị khác nhau trong dữ liệu thực tế có bị dồn vào quá ít ngăn không? |
| Chi phí tính toán | Tính mã băm và so sánh bằng nhau có đắt không? |
| Tính ổn định | Dữ liệu liên quan có giữ nguyên khi đối tượng đang trong tập không? |

Một công thức có nhiều trường không tự bảo đảm phân bố tốt. Nếu dữ liệu thực tế tập trung vào một vài mẫu mà công thức xử lý kém, vẫn có thể tạo nhiều va chạm.

### 6.2. Tăng capacity không cứu được một hàm băm hằng

Nếu tất cả khóa đều có cùng mã băm, việc tạo thêm nhiều ngăn trống không tự làm các khóa tách đều ra những ngăn đó. Nguyên nhân nằm ở thông tin băm của khóa.

Tài liệu `HashMap`, cấu trúc nền của `HashSet`, cảnh báo rằng nhiều khóa cùng `hashCode` làm giảm hiệu năng. Nó có các cơ chế hỗ trợ xử lý va chạm trong một số trường hợp, nhưng đó không phải lý do để bỏ qua chất lượng băm. [HashMap: va chạm và chi phí tìm kiếm](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)

Không mặc định rằng mọi trường hợp nhiều va chạm đều được cứu thành O(1), hoặc rằng mọi `HashSet` trên mọi JDK có cùng một cận O(log n) cho tất cả khóa. Hợp đồng hiệu năng thông thường vẫn gắn với giả định phân bố phù hợp.

Chương trình va chạm ở phần sau dùng khóa có mã băm hằng để chứng minh rằng **tập vẫn cần phân biệt bằng nhau đúng**. Chương trình không đo thời gian và không chứng minh cách băm đó đủ nhanh cho một khối lượng dữ liệu lớn.

### 6.3. Chi phí khóa không biến mất nhờ ký hiệu O(1)

Gọi một thao tác là O(1) theo số phần tử `n` không có nghĩa mọi công việc bên trong đều miễn phí.

Có thể suy nghĩ về một lời gọi theo các thành phần:

```text
chi phí thao tác
    = chi phí tính hashCode của khóa
    + chi phí tìm trong vùng ứng viên
    + chi phí các phép equals cần thiết
    + chi phí thay đổi cấu trúc hoặc cấp phát nếu có
```

Đây là phân rã để phân tích, không phải công thức thời gian chính xác.

Nếu `hashCode` phải duyệt một cấu trúc dài theo độ dài khóa `k`, hoặc `equals` phải so sánh nhiều dữ liệu, độ dài khóa trở thành một yếu tố riêng. Không gộp cả `n` và `k` rồi kết luận thao tác “luôn hằng số”.

Tránh thiết kế `hashCode`/`equals` có công việc không liên quan đến định danh, chẳng hạn truy cập mạng hay đọc tệp. Các phương thức nhận diện nên có ý nghĩa ổn định và chi phí có thể kiểm soát.

## 7. Điều kiện thứ ba: hiểu capacity, load factor và việc tăng dung lượng

### 7.1. Ba đại lượng khác nhau

Trong ngữ cảnh bảng băm:

- `size` là số phần tử đang lưu.
- `capacity` là số ngăn của bảng nền.
- `load factor`, thường gọi là hệ số tải, liên quan tới mức đầy được cho phép trước khi bảng tăng dung lượng.

`size()` không trả về capacity. Tham số truyền vào `new HashSet<>(initialCapacity)` cũng không phải kích thước tối đa mà tập được phép chứa.

Java 17 có các cách tạo phổ biến:

```java
Set<String> defaults = new HashSet<>();
Set<String> withHint = new HashSet<>(1_334);
Set<String> withLoadFactor = new HashSet<>(1_334, 0.75f);
```

Các biến trên đều bắt đầu là tập rỗng. Các con số trong hàm tạo không thêm sẵn bất kỳ phần tử nào.

Hàm tạo mặc định của `HashSet` dùng các tham số khởi tạo mặc định của bảng nền là capacity `16` và load factor `0.75`. Điều này không có nghĩa tập chỉ chứa được 16 phần tử. [Các hàm tạo HashSet trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)

### 7.2. Tăng dung lượng làm một số lần thêm tốn nhiều việc hơn

Về mô hình được tài liệu bảng nền mô tả, khi số mục vượt quá tích capacity và load factor, bảng tăng dung lượng và xây dựng lại cấu trúc lưu tương ứng. Vì nhiều phần tử đã có phải được tổ chức lại, một lần thêm gây tăng dung lượng có thể tốn nhiều việc hơn một lần thêm thông thường.

Đó là lý do phân biệt:

- **Kỳ vọng theo phân bố băm:** vùng tìm nhỏ khi các khóa được phân bố phù hợp.
- **Khấu hao qua nhiều lần thêm:** chi phí những lần tăng dung lượng được phân bổ khi phân tích một chuỗi thao tác, thay vì khẳng định mọi lần thêm riêng lẻ đều rẻ như nhau.

Từ “khấu hao” không có nghĩa chương trình trả một khoản thời gian cố định sau mỗi lần thêm, mà là cách phân tích tổng công việc của một chuỗi thao tác. Nếu ứng dụng quan tâm độ trễ của từng yêu cầu riêng, các lần tăng dung lượng vẫn cần được xét.

Ví dụ mô hình với capacity `16` và load factor `0.75` cho ngưỡng liên quan tới `16 × 0.75 = 12`. Đừng biến phép nhân minh họa này thành cách đọc chính xác bộ nhớ hoặc quy luật mọi chi tiết nội bộ của tất cả JDK.

### 7.3. Ước lượng theo số phần tử khác nhau

Nếu biết sẽ giữ khoảng `1_000` phần tử **khác nhau**, có thể dùng ước lượng:

```text
capacity hint ≈ ceil(expectedDistinct / loadFactor)

Với expectedDistinct = 1_000 và loadFactor = 0.75:
    ceil(1_000 / 0.75) = 1_334
```

Vì vậy, trong ví dụ có giới hạn nhỏ, rõ ràng:

```java
int expectedDistinct = 1_000;
int capacityHint = (int) Math.ceil(expectedDistinct / 0.75d);

Set<Integer> values = new HashSet<>(capacityHint, 0.75f);
```

Đây là giá trị gợi ý cho hàm tạo, không phải lời khẳng định rằng bảng thực tế có đúng `1_334` ngăn, cũng không phải cách đo bộ nhớ cấp phát.

Không dùng nguyên phép ép kiểu trên như một hàm tổng quát cho mọi giá trị đầu vào khổng lồ. Với dữ liệu không giới hạn, còn cần xét miền số, giới hạn dung lượng và bộ nhớ thực tế. Trong chương trình minh họa, con số cố định `1_000` đủ nhỏ để phép tính rõ ràng và không có vấn đề tràn phạm vi.

Tập loại trùng, nên số phần tử lưu được quyết định bởi số giá trị khác nhau, không phải tổng số bản ghi đã đi qua. Một triệu bản ghi chỉ có một nghìn mã khác nhau là tình huống khác với một triệu mã khác nhau.

### 7.4. Load factor là sự đánh đổi, không phải nút “càng nhỏ càng nhanh”

Giảm hệ số tải có thể làm bảng thưa hơn nhưng tốn nhiều không gian hơn. Tăng hệ số tải có thể giảm chi phí không gian tương đối nhưng làm tìm kiếm nặng hơn. Giá trị mặc định `0.75` là lựa chọn cân bằng được tài liệu bảng nền mô tả. [HashMap: capacity, load factor và tăng dung lượng](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)

Không chọn capacity cực lớn chỉ để tránh mọi khả năng tăng dung lượng. Điều đó có thể làm lãng phí bộ nhớ và ảnh hưởng việc duyệt toàn bộ tập. Cũng không chỉnh hệ số tải để bù cho một `hashCode` sai hoặc có phân bố rất kém.

Hãy bắt đầu từ cấu hình thông thường, số phần tử khác nhau dự kiến và kiểu thao tác thực tế. Chỉ thay đổi cấu hình khi đã xác định được lợi ích và chi phí liên quan.

## 8. Chương trình đầy đủ: va chạm băm và gợi ý dung lượng

Lưu mã vào **HashCollisionDemo.java**:

```java
import java.util.HashSet;
import java.util.Set;

public class HashCollisionDemo {
    private record DistributedKey(int id) {
    }

    private static final class CollisionKey {
        private final int id;

        private CollisionKey(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CollisionKey key && id == key.id;
        }

        @Override
        public int hashCode() {
            return 7;
        }
    }

    public static void main(String[] args) {
        Set<DistributedKey> distributed = new HashSet<>(128);
        Set<CollisionKey> colliding = new HashSet<>(128);
        for (int id = 0; id < 100; id++) {
            distributed.add(new DistributedKey(id));
            colliding.add(new CollisionKey(id));
        }

        System.out.println("distributed size: " + distributed.size());
        System.out.println("colliding size: " + colliding.size());
        System.out.println("distributed contains 40: "
                + distributed.contains(new DistributedKey(40)));
        System.out.println("colliding contains 40: "
                + colliding.contains(new CollisionKey(40)));
        System.out.println("colliding contains 999: "
                + colliding.contains(new CollisionKey(999)));
        System.out.println("colliding add duplicate: "
                + colliding.add(new CollisionKey(40)));
        System.out.println("colliding remove 40: "
                + colliding.remove(new CollisionKey(40)));
        System.out.println("colliding contains removed: "
                + colliding.contains(new CollisionKey(40)));
        System.out.println("colliding remaining: " + colliding.size());

        int expectedDistinct = 1_000;
        int capacityHint = (int) Math.ceil(expectedDistinct / 0.75d);
        Set<Integer> sized = new HashSet<>(capacityHint, 0.75f);
        for (int value = 0; value < expectedDistinct; value++) {
            sized.add(value);
        }
        System.out.println("capacity hint: " + capacityHint);
        System.out.println("sized count: " + sized.size());
        System.out.println("sized contains 999: " + sized.contains(999));
        System.out.println("sized remove 999: " + sized.remove(999));
        System.out.println("sized final count: " + sized.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 HashCollisionDemo.java
java HashCollisionDemo
```

Stdout:

```text
distributed size: 100
colliding size: 100
distributed contains 40: true
colliding contains 40: true
colliding contains 999: false
colliding add duplicate: false
colliding remove 40: true
colliding contains removed: false
colliding remaining: 99
capacity hint: 1334
sized count: 1000
sized contains 999: true
sized remove 999: true
sized final count: 999
```

Hai kiểu khóa trong chương trình có cùng ý nghĩa định danh là trường `id`, nhưng chất lượng mã băm khác nhau:

- `DistributedKey` là record chỉ có một thành phần `int`; với dãy mã trong ví dụ, nó cung cấp mã băm theo thành phần.
- `CollisionKey` chủ động trả về cùng số `7` cho mọi mã, dù `equals` vẫn phân biệt các mã khác nhau.

Cả hai tập đều giữ được 100 phần tử. Điều đó chứng minh va chạm không đồng nghĩa với việc các phần tử bị coi là trùng. Tìm khóa có mặt, từ chối thêm trùng, xóa và kiểm tra sau xóa vẫn có ý nghĩa đúng.

**Không có số đo thời gian nào trong stdout.** Không thể dùng việc hai tập có cùng số phần tử và cùng kết quả truy vấn để kết luận chúng có hiệu năng như nhau.

Hai tập đầu được tạo với gợi ý `128`, nhưng chương trình không kiểm tra hoặc khẳng định số lần tăng dung lượng của chúng. Một tham số dung lượng không phải bằng chứng thực nghiệm về mọi chi tiết cấp phát.

Phần cuối tính gợi ý `1_334` cho `1_000` phần tử khác nhau, thêm chúng rồi kiểm tra một giá trị. Số `1_000` được in là `size`; số `1_334` được in là biến đầu vào do chương trình tính. Chúng không phải hai cách đọc cùng một thuộc tính của tập.

Hai chương trình trong bài đã được biên dịch bằng `--release 17` và chạy để đối chiếu đúng stdout. Tùy chọn này kiểm tra mức ngôn ngữ/API và sinh bytecode tương thích Java 17; nó không có nghĩa JVM đang thực thi chính là JDK 17.

## 9. Chọn luồng xử lý để hưởng lợi từ tập băm

### 9.1. Nhiều truy vấn nên dùng lại một tập đã xây dựng

Giả sử có `n` mã hợp lệ và cần kiểm tra `m` mã đầu vào. Nếu với mỗi truy vấn lại quét tuần tự mọi mã hợp lệ, số lần kiểm tra có thể tăng theo `m × n`.

Một phương án là xây dựng tập băm một lần, rồi dùng lại cho các truy vấn:

```java
// Đoạn minh họa; validIds và requests là các tập hợp đầu vào đã có.
Set<String> allowed = new HashSet<>(validIds);

for (String requestId : requests) {
    if (allowed.contains(requestId)) {
        System.out.println("Mã hợp lệ: " + requestId);
    }
}
```

Với giả định mã băm phân bố phù hợp và chi phí khóa được xem là hằng số, việc xây dựng và thực hiện các truy vấn có thể được phân tích theo công việc kỳ vọng gần O(n + m), thay cho một thuật toán quét O(mn).

Nhưng nếu tạo lại `new HashSet<>(validIds)` bên trong mỗi lượt truy vấn, chương trình liên tục trả chi phí xây dựng tập. Lợi ích của cấu trúc dữ liệu có thể bị mất ở cách tổ chức luồng xử lý.

Cũng cần tính tới bộ nhớ bổ sung và thời gian tồn tại của tập. Dùng tập băm cho một truy vấn duy nhất trên một đầu vào rất nhỏ chưa chắc đem lại lợi ích đáng kể. Chọn theo số lần truy vấn và quy mô dữ liệu thực tế.

### 9.2. Tránh lặp lại phép tìm không cần thiết

| Yêu cầu | Cách viết trực tiếp |
| --- | --- |
| Chỉ xử lý lần đầu gặp giá trị | Dùng giá trị trả về của `add` |
| Chỉ xóa nếu có và biết có thay đổi không | Dùng giá trị trả về của `remove` |
| Chỉ cần kiểm tra, không sửa tập | Dùng `contains` |
| Cần lấy đối tượng đang lưu theo khóa | Xem lại mô hình dữ liệu; `contains` không trả đối tượng |

Nếu cần ánh xạ từ một mã tới dữ liệu liên quan, một `Map` có thể biểu đạt nhu cầu phù hợp hơn việc liên tục tìm thủ công trong `Set`. Quyết định đó xuất phát từ yêu cầu truy cập dữ liệu, không phải chỉ từ tên cấu trúc.

### 9.3. Tránh giữ tập tăng mãi ngoài yêu cầu

Một tập “đã thấy” cần có phạm vi rõ ràng: trong một lô dữ liệu, một phiên xử lý hay toàn bộ tiến trình.

Nếu bài toán chỉ cần loại trùng trong một lô nhưng tập được giữ qua vô hạn lô, số phần tử khác nhau và bộ nhớ tiếp tục tăng. Khi đó, vấn đề không chỉ là chọn capacity ban đầu. Cần xác định lúc nào dữ liệu hết hiệu lực và được loại bỏ theo đúng yêu cầu.

Không tự xóa tùy ý chỉ để giảm bộ nhớ nếu việc xóa làm mất ý nghĩa “đã xử lý”. Vòng đời dữ liệu là một phần của thiết kế bài toán.

## 10. Chi phí duyệt toàn bộ khác với chi phí tìm một phần tử

`contains(x)` tận dụng băm để thu hẹp vùng tìm. Một vòng `for-each` đi qua toàn bộ tập không có cùng mô hình công việc.

Với `HashSet`, tài liệu mô tả thời gian duyệt tỷ lệ với tổng **size + capacity** của bảng nền. Vì vậy, một tập rất thưa có thể làm việc duyệt kém hiệu quả hơn dự đoán chỉ từ `size()`. [HashSet: chi phí iteration](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)

Điều này giải thích vì sao cấp capacity quá lớn hoặc chọn load factor quá thấp có thể không phù hợp khi chương trình thường xuyên duyệt toàn bộ tập.

Nếu yêu cầu cần giữ thứ tự chèn và duyệt nhiều, `LinkedHashSet` là một lựa chọn cần cân nhắc: nó giữ liên kết phục vụ thứ tự duyệt, có chi phí bộ nhớ riêng, và việc duyệt tỷ lệ với số phần tử thay vì capacity theo mô tả của lớp. Nó vẫn dựa vào băm cho các thao tác thành viên và vẫn cần quy tắc `equals`/`hashCode` phù hợp. [LinkedHashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)

Không thay cấu trúc chỉ để làm output của một ví dụ “đẹp hơn” rồi suy ra đó là lựa chọn nhanh nhất cho mọi tải công việc. Cần biết chương trình chủ yếu tìm thành viên, thêm/xóa hay duyệt toàn bộ.

Khi cần xóa trong lúc duyệt một `HashSet`, dùng cơ chế được hỗ trợ như `Iterator.remove` hoặc một thao tác lọc phù hợp. Không vừa `for-each` vừa tự gọi `set.remove` rồi dựa vào việc “lần này không có ngoại lệ”. Điều này liên quan đến tính đúng của quá trình duyệt, không phải tối ưu băm.

## 11. Kiểm chứng tính đúng và đánh giá hiệu năng là hai việc khác nhau

Các chương trình trong bài kiểm chứng được các hành vi như thêm trùng, tìm theo khóa bằng nhau, xóa và xử lý va chạm. Chúng không phải phép đo hiệu năng.

Một phép đo có ý nghĩa cần xác định câu hỏi cụ thể, chẳng hạn:

- `contains` với khóa có mặt khác với khóa vắng mặt ra sao?
- `add` phần tử mới khác với `add` giá trị đã tồn tại ra sao?
- Khi phần tử tăng từ một nghìn lên nhiều hơn, chi phí thay đổi thế nào?
- Tải chủ yếu tra cứu hay thường xuyên gây tăng dung lượng?
- Mã băm của dữ liệu đại diện có phân bố phù hợp không?
- Độ dài và cấu trúc của khóa có thay đổi theo quy mô không?

Không đo thời gian bằng cách đặt `println` trong phần đang muốn so sánh rồi xem đó là thời gian của riêng `contains`. Công việc in output cũng trở thành một phần của phép đo.

Cũng cần tránh việc đo `add` lặp đi lặp lại cùng một giá trị nhưng lại mô tả kết quả như chi phí thêm nhiều phần tử mới. Sau lần đầu, tải công việc đã đổi thành thử thêm trùng. Với `remove`, nếu không chuẩn bị lại dữ liệu, sau một lần xóa các lần tiếp theo có thể chỉ là xóa giá trị vắng mặt.

Khi cần một phép đo nhỏ có kiểm soát trên JVM, có thể dùng [OpenJDK JMH](https://github.com/openjdk/jmh) và đọc các mẫu đi kèm. Công cụ hỗ trợ xây dựng, chạy và phân tích phép đo; vẫn phải thiết kế dữ liệu và thao tác đúng với câu hỏi cần trả lời.

Một bản mô tả kết quả nên nêu tối thiểu phiên bản JDK, cấu hình tập, quy mô và dạng dữ liệu, kiểu thao tác, tỷ lệ tìm thấy/không tìm thấy và cách tách chuẩn bị dữ liệu khỏi thao tác được đo. Không biến một con số trên một máy thành thời gian được bảo đảm bởi API.

## 12. Những lỗi thường gặp khi mong đợi HashSet chạy nhanh

| Hiện tượng hoặc giả định | Điểm cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| Tìm/xóa bằng một đối tượng bằng giá trị nhưng không ra kết quả như dự kiến | equals/hashCode có đúng và nhất quán không? | Sửa hợp đồng nhận diện trước |
| Sửa trường mã rồi không tìm lại được ổn định | Khóa có thay đổi khi đang trong tập không? | Dùng định danh bất biến hoặc quy trình cập nhật rõ |
| Tập có nhiều khóa nhưng mọi hashCode như nhau | Phân bố băm kém | Thiết kế mã băm phù hợp với dữ liệu |
| Tăng capacity rất nhiều mà va chạm vẫn tập trung | Có phải hashCode hằng hoặc quá ít giá trị không? | Không dùng capacity để che lỗi phân bố |
| Một số lần add chậm hơn nhiều | Có tăng dung lượng hoặc cấp phát không? | Xem cấu hình và đặc điểm tải, phân biệt từng lần với khấu hao |
| Nghĩ new HashSet(1000) chỉ chứa được 1000 phần tử | Nhầm gợi ý dung lượng với giới hạn số phần tử | Tách size, capacity và ngưỡng tăng |
| Tập ít phần tử nhưng duyệt tốn việc | Capacity có quá lớn so với size không? | Cân nhắc cấu hình và mô hình duyệt |
| Luôn contains trước add/remove | Đang lặp lại việc tìm kiếm | Dùng cờ kết quả của thao tác sửa đổi |
| Thấy stdout cùng thứ tự nên cho rằng HashSet giữ thứ tự chèn | Phụ thuộc một quan sát không được bảo đảm | Chọn cấu trúc có hợp đồng thứ tự khi cần |
| Một phép đo rất nhanh nên kết luận mọi thao tác đều O(1) | Có đo đúng thao tác và dữ liệu đại diện không? | Tách tính đúng, phép đo và phân tích độ phức tạp |
| Đã dùng add trực tiếp nên tưởng an toàn khi nhiều luồng cùng sửa | Nhầm ít lời gọi với cơ chế đồng bộ | Thiết kế truy cập đồng thời phù hợp |

Nếu hiệu năng không như mong đợi, trước tiên hãy xác nhận dữ liệu đang đúng. Sau đó mới kiểm tra phân bố băm, chi phí khóa, tăng dung lượng, số lần xây dựng tập và kiểu thao tác thực tế. Chỉnh một tham số mà chưa biết công việc nào đang tốn thời gian thường không giải quyết được nguyên nhân.

## 13. Tự luyện và đối chiếu

### Bài 1: theo dõi kết quả và kích thước

Với một `HashSet<String>` mới tạo, thực hiện:

```java
set.add("a");
set.add(new String("a"));
set.contains("a");
set.remove("a");
set.remove("a");
```

Đối chiếu:

| Thao tác | Kết quả | size sau thao tác |
| --- | --- | --- |
| `add("a")` | `true` | 1 |
| `add(new String("a"))` | `false` | 1 |
| `contains("a")` | `true` | 1 |
| `remove("a")` | `true` | 0 |
| `remove("a")` | `false` | 0 |

Cần giải thích vì sao một lần `new` không tạo phần tử mới trong tập khi hai chuỗi bằng nhau theo giá trị.

### Bài 2: tính đúng hay chất lượng băm?

Một lớp khóa có `equals` đúng theo mã và `hashCode` luôn trả `0`.

1. Nó có nhất thiết vi phạm điều kiện “bằng nhau phải cùng mã băm” không?
2. Những khóa khác nhau có bắt buộc bị loại trùng không?
3. Có thể dựa vào hàm băm đó để mong đợi phân bố tốt không?
4. Tăng initial capacity có tự sửa được việc mọi khóa cùng mã băm không?

Đối chiếu: không nhất thiết vi phạm điều kiện đó; các khóa khác nhau vẫn được phân biệt bằng `equals`; phân bố rất kém; tăng capacity không tự tạo thêm thông tin phân biệt trong mã băm.

### Bài 3: ước lượng dung lượng

Dự kiến giữ `750` phần tử khác nhau với load factor `0.75`. Theo phép ước lượng trong bài, giá trị gợi ý là:

```text
ceil(750 / 0.75) = 1_000
```

Hãy nêu hai điều không thể kết luận từ con số đó.

Gợi ý: không thể nói tập đã có 1.000 phần tử, và cũng không thể nói bảng thực tế chắc chắn có đúng 1.000 ngăn. Đây là đầu vào cấu hình, không phải kết quả quan sát cấu trúc nội bộ.

Nếu có 50.000 bản ghi nhưng chỉ 750 mã khác nhau, hãy giải thích vì sao số giá trị khác nhau mới liên quan trực tiếp đến số phần tử mà tập giữ.

### Bài 4: sửa một phép đo sai câu hỏi

Một vòng lặp gọi một triệu lần `set.add("java")` rồi báo “đã đo thêm một triệu phần tử”.

Điểm sai: nếu tập ban đầu chưa có chuỗi đó, chỉ lần đầu là thêm phần tử mới. Các lần còn lại thử thêm giá trị đã có.

Muốn đo thêm mới, cần chuẩn bị dữ liệu có các giá trị khác nhau và kiểm soát trạng thái tập cho mỗi phép đo. Muốn đo thêm trùng, có thể dùng dữ liệu đã có nhưng phải mô tả đúng tải công việc đó. Việc chuẩn bị dữ liệu, tăng dung lượng và công việc đo phải được phân biệt theo câu hỏi cần trả lời.

## 14. Tài liệu tra cứu

- [HashSet Java 17 — thao tác, hiệu năng, thứ tự, null và hàm tạo](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)
- [HashMap Java 17 — cấu trúc nền, capacity, load factor và va chạm](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)
- [Object — hợp đồng equals/hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html)
- [Set — giới hạn khi phần tử thay đổi phép bằng nhau](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)
- [LinkedHashSet — đánh đổi về thứ tự và chi phí duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)
- [OpenJDK JMH — công cụ xây dựng phép đo trên JVM](https://github.com/openjdk/jmh)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-based-set-003">Capacity, load factor và chi phí duyệt HashSet</a>

<details>
<summary>Xem chi tiết</summary>

# Capacity, load factor và chi phí duyệt HashSet

Một `HashSet` có 100 phần tử không nhất thiết có cùng chi phí duyệt với mọi `HashSet` khác cũng có 100 phần tử. Ngoài số thành viên, còn phải xét kích thước bảng bucket mà nó đang giữ.

Ba khái niệm liên quan nhưng khác nhau:

- **Size:** có bao nhiêu thành viên.
- **Capacity:** bảng nền có bao nhiêu bucket.
- **Load factor:** tham số quyết định mức tải dùng để xét tăng bảng.

Chọn capacity quá nhỏ có thể dẫn tới nhiều lần tăng bảng. Chọn quá lớn hoặc load factor quá thấp có thể tốn bộ nhớ và làm việc duyệt một bảng thưa trở nên đắt. Bài này giải thích cách cân bằng các yếu tố đó, với ví dụ tương thích Java 17.

## 1. Mô hình cần dùng: thành viên và bucket

`HashSet` được hậu thuẫn bởi `HashMap`; các phần tử của set được dùng làm khóa. Bảng bucket giúp chọn một vùng ứng viên khi tìm hoặc thêm khóa.

Một bảng có 8 bucket nhưng chỉ giữ 3 thành viên có thể được hình dung:

~~~text
bucket 0:  rong
bucket 1:  [A]
bucket 2:  rong
bucket 3:  [B] -> [C]
bucket 4:  rong
bucket 5:  rong
bucket 6:  rong
bucket 7:  rong

size = 3
capacity = 8
so bucket khong rong = 2
~~~

Sơ đồ là mô hình giải thích, không phải trạng thái đọc trực tiếp từ một đối tượng Java cụ thể.

### 1.1. Capacity không phải giới hạn số phần tử

Một bucket có thể quản lý nhiều khóa do va chạm. Khi cần, bảng có thể tăng kích thước. Vì vậy, capacity không phải “số phần tử tối đa mà set này được phép chứa”.

Ngược lại, cấp capacity lớn không làm set có sẵn thành viên. `new HashSet<>(1000)` vẫn có size bằng 0 ngay sau khi tạo.

### 1.2. Size không cho biết capacity hiện tại

Hai set có cùng ba thành viên có thể khác lịch sử:

- Một set vừa được tạo rồi thêm ba phần tử.
- Một set được đặt trước bảng rất rộng.
- Một set từng có hàng trăm nghìn thành viên rồi xóa gần hết.

Cùng nội dung hiện tại không có nghĩa cùng cấu trúc lưu trữ đang giữ. Đây là nguyên nhân cần xét capacity khi nói về chi phí duyệt.

[Tài liệu HashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html) mô tả thời gian duyệt theo cả số thành viên và capacity của map hậu thuẫn.

## 2. Load factor: phân biệt tham số với mức tải thực tế

Gọi n là size, c là capacity của bảng đã cấp.

### 2.1. Mức tải thực tế là n/c

Ta có thể dùng tỷ số:

~~~text
muc tai thuc te = n / c
~~~

Nếu n = 3 và c = 16, mức tải thực tế là 0,1875. Nếu n = 12 và c = 16, nó là 0,75.

Đây không phải tỷ lệ bucket không rỗng. Một bucket chứa nhiều khóa làm size tăng nhưng chỉ đóng góp một bucket không rỗng.

Ví dụ cả ba khóa cùng một bucket, với c = 16:

~~~text
n/c = 3/16
so bucket khong rong / c = 1/16
~~~

Hai tỷ lệ mô tả hai điều khác nhau.

### 2.2. Load factor được cấu hình không luôn bằng n/c

Khi tạo `new HashSet<>(16, 0.75f)`, giá trị 0,75 là tham số dùng để quyết định ngưỡng tăng bảng. Nó không buộc bảng luôn được lấp đúng 75%.

Sau vài lần thêm, sau tăng bảng hoặc sau nhiều lần xóa, n/c có thể nhỏ hơn rất nhiều. Đừng đọc giá trị cấu hình như một phép đo trạng thái hiện tại.

Theo [HashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html), capacity và load factor cùng ảnh hưởng việc tăng bảng và đánh đổi giữa thời gian với không gian.

### 2.3. Ngưỡng thường liên quan capacity × load factor

Gọi α là load factor cấu hình. Trong các trường hợp thông thường, ngưỡng số khóa được xác định gần:

~~~text
threshold = capacity * alpha
~~~

Triển khai cần xử lý chuyển thành số nguyên, làm tròn và giới hạn. Với các giá trị mặc định nhỏ đang xét, ví dụ c = 16, α = 0,75 cho threshold = 12.

Threshold không đếm số bucket không rỗng. Nhiều khóa va chạm cùng một bucket vẫn góp vào size.

### 2.4. Load factor lớn hơn 1 không mặc nhiên là sai kiểu tham số

Không nên học quy tắc “load factor bắt buộc nằm giữa 0 và 1”. Trong triển khai OpenJDK được tham chiếu, tham số dương lớn hơn 1 có thể được chấp nhận; ví dụ 2,0.

Ý nghĩa là ngưỡng theo số khóa có thể lớn hơn số bucket. Cấu trúc vẫn có thể chứa nhiều khóa trong một bucket.

Giá trị 0, âm và NaN bị từ chối trong bản OpenJDK đang xét. Chấp nhận một giá trị không chứng minh đó là lựa chọn tốt cho tải thực tế. Giá trị mặc định 0,75 là điểm bắt đầu thông dụng; chỉ thay đổi khi có yêu cầu và bằng chứng phù hợp.

## 3. Tăng bảng: khi nào và phải trả công việc gì?

Các chi tiết số học trong phần này tham chiếu [HashMap OpenJDK 17, bản jdk-17+35](https://raw.githubusercontent.com/openjdk/jdk/jdk-17%2B35/src/java.base/share/classes/java/util/HashMap.java), không là công thức bắt buộc mọi triển khai set phải dùng.

### 3.1. Ví dụ ngưỡng với bảng 16 bucket

Trong một chuỗi thêm khóa khác nhau, không có tình huống va chạm đặc biệt gây tăng sớm, mô hình thông thường là:

| Size sau thêm | Capacity theo mô hình | Threshold theo mô hình |
|---:|---:|---:|
| 1 | 16 | 12 |
| 12 | 16 | 12 |
| 13 | 32 | 24 |
| 24 | 32 | 24 |
| 25 | 64 | 48 |

Một khóa mới làm size vượt threshold thì có thể kích hoạt tăng. Thêm lại một khóa đã có không làm size tăng nên không phải một “thành viên mới” để vượt ngưỡng theo cách đó.

Cần phân biệt “đã tới ngưỡng” với “vượt ngưỡng” trong mô hình này: size 12 vẫn có thể nằm ở bảng 16, size 13 mới qua ngưỡng 12.

### 3.2. Capacity thường được làm tròn theo cách của bảng nền

HashMap trong bản này dùng chiều dài bảng là lũy thừa của 2. Một yêu cầu capacity ban đầu 1000 có thể dẫn tới bảng 1024 khi cấp phát, không phải một mảng bucket dài đúng 1000.

Vì thế, tham số constructor, capacity đã được làm tròn và threshold là ba giá trị cần phân biệt. Không suy ra capacity thật chỉ bằng cách đọc lại con số từng truyền cho constructor.

Bảng còn được cấp lười: tạo set rỗng không đồng nghĩa ngay lúc đó đã cấp toàn bộ mảng bucket. Chương trình không có getter công khai của `HashSet` để đọc capacity.

### 3.3. Tăng bảng có cấp phát và phân bố lại

Khi bảng tăng, cần vùng bucket mới và phải tổ chức lại vị trí của các mục đang có. Một số khóa chuyển sang bucket khác vì mặt nạ chỉ số đã thay đổi.

Do vậy, thêm một phần tử có thể rẻ trong lần thường nhưng đắt hơn khi phải tăng bảng. Phân tích chuỗi thao tác phải tính phần việc tăng, rồi mới nói về chi phí kỳ vọng/khấu hao.

Không được dùng kết luận O(1) cho thêm theo nghĩa “mọi lần add đều không cấp phát và không xử lý các mục cũ”.

### 3.4. Ngưỡng size không phải lý do tăng duy nhất trong mọi đường đi

Khi có nhiều va chạm trong một bucket và bảng còn nhỏ, bản OpenJDK này có thể tăng bảng như một phần của cơ chế xử lý va chạm, trước khi lời giải thích đơn giản bằng size×load factor mô tả hết tình huống.

Vì vậy, bảng số ở trên và chương trình mô phỏng chỉ trình bày đường đi tăng thông thường. Không biến nó thành một kiểm thử rằng mọi tập khóa đều phải có cùng capacity sau từng lần thêm.

Tính toán đặt trước dung lượng giúp giảm tăng theo ngưỡng size; nó không là một bảo đảm vô điều kiện cho mọi hành vi nội bộ.

## 4. Vì sao duyệt HashSet phụ thuộc cả size và capacity?

### 4.1. Tìm một phần tử và duyệt mọi phần tử là hai công việc khác nhau

Một lần `contains(key)` tính hash và chọn bucket ứng viên. Nó không cần đi qua toàn bộ bảng nếu khóa và cấu trúc đang được quản lý bình thường.

Một iterator phải tìm mọi thành viên. Trong mô hình bảng băm, nó đi qua các vị trí bucket và các mục nằm trong những bucket không rỗng.

Có thể tách công việc tổng quát thành:

~~~text
cong viec di qua bang bucket     ~ c
cong viec tra/xu ly cac thanh vien ~ n

tong ~ c + n
~~~

Do đó, chi phí duyệt thường được biểu diễn là **O(c + n)**, chưa tính phần việc ứng dụng làm với từng phần tử.

### 4.2. Ba lần chạy thân for-each không có nghĩa chỉ ba bước bên trong

Nếu set chứa đúng ba phần tử, thân vòng `for-each` được gọi ba lần. Nhưng để tìm các phần tử tiếp theo, iterator có thể phải bỏ qua những vùng bucket rỗng giữa chúng.

Hai set có cùng size = 3 và cùng tổng giá trị sẽ cho cùng số lần chạy thân vòng lặp. Điều đó không đo được phần việc tìm các mục trong bảng nền.

Chương trình ở phần thực hành in hai nhóm riêng:

- Số phần tử do iterator thật trả ra.
- Số bucket và phần tử được quét trong một mô hình cố định có chủ đích.

Không cộng các con số đó thành một phép đo thời gian của JVM.

### 4.3. Khi c cùng bậc với n, O(c+n) có thể rút thành O(n)

Nếu bảng tăng hợp lý theo dữ liệu và chưa bị xóa thưa đi nhiều, capacity có thể cùng bậc với số thành viên. Khi ấy cách nói duyệt tuyến tính theo n là hợp lý trong điều kiện đó.

Nhưng nếu c lớn hơn n rất nhiều, bỏ c khỏi phân tích làm mất yếu tố quan trọng. Điều này thường xảy ra do:

- Đặt trước capacity vượt xa số thành viên thực tế.
- Chọn load factor rất thấp.
- Set từng lớn rồi bị xóa phần lớn dữ liệu.
- Ước lượng theo tổng bản ghi đầu vào trong khi chỉ có ít khóa duy nhất.

Không cần một benchmark mới để nhận ra rằng “duyệt luôn chỉ phụ thuộc size” là phát biểu thiếu điều kiện; hợp đồng tài liệu đã nêu cả capacity.

### 4.4. Không biến c+n thành một số lệnh hoặc số nano giây chính xác

Mô hình cho biết các đại lượng nào ảnh hưởng lượng công việc. Thời gian thực còn phụ thuộc bố trí bộ nhớ, loại khóa, bộ gom rác, JIT và công việc trong thân vòng lặp.

Triển khai cũng có đường đi riêng cho những trường hợp đặc biệt, như kết thúc nhanh khi không có phần tử. Không nên hiểu c+n là mệnh lệnh rằng mọi set rỗng đều bắt buộc đọc từng bucket.

Điểm cần giữ là: khi bảng rộng còn có dữ liệu, các bucket rỗng có thể tạo chi phí mà việc chỉ đếm số phần tử trả ra không thể hiện.

## 5. Chọn capacity ban đầu theo số thành viên dự kiến

### 5.1. Một con số truyền vào không tự là expected size

Nếu dự kiến giữ 1000 thành viên duy nhất, `new HashSet<>(1000)` không có nghĩa ngưỡng tăng sẽ bằng 1000.

Theo mô hình bản OpenJDK đang xét:

~~~text
yeu cau ban dau = 1000
capacity lam tron = 1024
load factor = 0.75
threshold = 768
~~~

Nếu tiếp tục thêm tới 1000 khóa khác nhau, ngưỡng theo size sẽ bị vượt trước khi đủ 1000.

### 5.2. Ước lượng để giảm tăng theo ngưỡng size

Một phép tính tham khảo với số nhỏ, load factor α và số thành viên dự kiến N là:

~~~text
yeu cau capacity ban dau gan ceil(N / alpha)
~~~

Với N = 1000, α = 0,75:

~~~text
ceil(1000 / 0.75) = 1334

lam tron ve luy thua 2 -> 2048
threshold -> 1536
~~~

Điều này giải thích vì sao số bucket thực tế có thể lớn hơn đáng kể số thành viên dự kiến. Nó không có nghĩa cần luôn cấp dư theo một hệ số cố định trong mọi bài toán.

Nếu xây một helper dùng cho đầu vào lớn, phải xử lý phạm vi số nguyên, chuyển kiểu và giới hạn bộ nhớ. Đoạn tính trong chương trình chỉ dùng ví dụ 1000 đã biết, không được trình bày như một hàm sizing hoàn chỉnh cho mọi giá trị.

Ngoài ra, những đường xử lý va chạm đặc biệt đã nêu có thể làm điều kiện tăng phức tạp hơn công thức size. Mục tiêu ở đây là ước lượng có căn cứ, không hứa kiểm soát tuyệt đối nội bộ.

### 5.3. Dự kiến số khóa duy nhất, không chỉ số bản ghi

Nếu một nguồn có một triệu bản ghi nhưng chỉ có vài trăm mã khác nhau, capacity của set phục vụ vài trăm thành viên, không phải một triệu lần gọi add.

Ước lượng theo mọi bản ghi có thể làm bảng thưa quá mức. Hãy dùng số thành viên duy nhất dự kiến nếu biết, hoặc để set tăng theo dữ liệu thực tế rồi đánh giá.

Constructor nhận collection cũng phải ước lượng trước khi hoàn tất loại trùng. Trong [HashSet OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk/jdk-17%2B35/src/java.base/share/classes/java/util/HashSet.java), việc khởi tạo dựa trên kích thước collection nguồn có thể tạo một bảng rộng hơn nhiều so với số khóa duy nhất cuối cùng nếu đầu vào rất nhiều trùng.

Vì thế, “truyền toàn bộ nguồn vào constructor” thuận tiện về cú pháp nhưng không thay thế việc xét phân bố dữ liệu khi bộ nhớ và các lượt duyệt sau đó là vấn đề.

### 5.4. Không giảm load factor theo thói quen

Load factor thấp hơn thường thúc đẩy dùng nhiều bucket hơn cho cùng số khóa. Điều đó có thể giảm một số va chạm khi hash phân bố phù hợp, nhưng tăng vùng bảng và có thể làm duyệt thưa hơn.

Load factor cao hơn có thể tiết kiệm bucket nhưng để nhiều khóa cùng vùng hơn và làm phần tìm kiếm khó hơn. Hai khóa có cùng mã băm không tự tách ra chỉ vì bảng rộng hơn.

Không có quy tắc “load factor càng thấp thì mọi thao tác càng nhanh”. Hãy xét tỷ lệ tìm kiếm, thêm/xóa, duyệt toàn bộ và ngân sách bộ nhớ.

## 6. Sau khi xóa nhiều phần tử, capacity có tự nhỏ lại không?

### 6.1. Xóa thành viên không có nghĩa thu bảng theo size mới

Ở bản OpenJDK đang tham chiếu, việc xóa khóa thông thường không tự giảm chiều dài bảng bucket theo từng lần xóa.

Một set từng có nhiều khóa rồi chỉ còn ba khóa có thể vẫn giữ bảng lớn. Nội dung đã nhỏ lại, nhưng lịch sử tăng bảng vẫn ảnh hưởng bộ nhớ và những lần duyệt tiếp theo.

Việc một bucket có thể đổi hình thức tổ chức bên trong khi số mục thay đổi không đồng nghĩa toàn bảng được thu nhỏ.

### 6.2. clear làm rỗng nhưng không hứa trả lại mảng bucket

Trong bản này, `HashSet.clear` dùng việc clear map: xóa các liên kết mục khỏi bảng và đặt số thành viên về 0, còn mảng bucket vẫn có thể được giữ để tái sử dụng.

Không kết luận rằng size về 0 thì capacity cũng về 0, hoặc bộ nhớ đã được trả ngay cho hệ điều hành. Thời điểm thu hồi còn phụ thuộc tham chiếu và JVM.

Set rỗng có thể được nhận diện nhanh khi duyệt; điều đó không chứng minh mảng nền đã biến mất. Khi thêm lại một ít thành viên, bảng vẫn có thể ở trạng thái rất rộng so với nội dung.

### 6.3. Tạo lại set có thể phù hợp ở một ranh giới vòng đời

Nếu dữ liệu đã giảm ổn định và set sẽ được giữ lâu, có thể cân nhắc:

~~~java
Set<Integer> compacted = new HashSet<>(existing);
~~~

Việc này tạo một cấu trúc set khác từ các thành viên hiện có, không giữ nguyên lịch sử capacity của bảng cũ. Với set nhỏ trong bản đang xét, cách khởi tạo dùng lại chính sách mặc định phù hợp với kích thước nguồn.

Nhưng quá trình tạo lại không miễn phí:

- Phải duyệt set cũ, nên phần đọc nguồn có thể vẫn chịu chi phí c+n của bảng rộng.
- Cần bộ nhớ cho set mới và các mục mới trong lúc set cũ còn tồn tại.
- Các phần tử được chia sẻ bằng tham chiếu, không được sao chép sâu.
- Những nơi còn giữ tham chiếu tới set cũ không tự chuyển sang set mới.

Nếu set sắp lớn lại ngay, tạo lại thường xuyên có thể chỉ làm thêm công việc cấp phát. Hãy chọn theo vòng đời, không rebuild sau mọi lần remove.

`HashSet` không có phương thức công khai `trimToSize()` như `ArrayList`. Không dùng một API của collection khác để suy ra khả năng thu gọn của set.

## 7. Chương trình hoàn chỉnh: số phần tử trả ra và mô hình quét bảng

Chương trình có ba loại minh chứng tách biệt:

1. Set thật: kiểm tra thành viên, số phần tử iterator trả ra và tổng.
2. Mô hình bảng cố định: đếm rõ bucket và phần tử được quét.
3. Phép tính tăng/sizing: minh họa các ngưỡng nhỏ theo điều kiện đã nêu.

Không có reflection, không đọc capacity thật và không đo thời gian.

### 7.1. Mã nguồn

Lưu thành `HashSetCapacityDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HashSetCapacityDemo {
    record Returned(int elements, long sum) {}
    record ModelScan(int buckets, int elements, long sum) {}

    static Returned returnedByIterator(Set<Integer> values) {
        int elements = 0;
        long sum = 0;
        for (int value : values) {
            elements++;
            sum += value;
        }
        return new Returned(elements, sum);
    }

    // Mo hinh bang co dinh, khong phai noi bo HashSet hay benchmark.
    static ModelScan scanFixedBuckets(int capacity, List<Integer> input) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("positive capacity required");
        }
        List<List<Integer>> buckets = new ArrayList<>(capacity);
        for (int index = 0; index < capacity; index++) {
            buckets.add(new ArrayList<>());
        }
        for (int value : input) {
            List<Integer> bucket = buckets.get(Math.floorMod(value, capacity));
            if (!bucket.contains(value)) {
                bucket.add(value);
            }
        }
        int bucketVisits = 0;
        int elementVisits = 0;
        long sum = 0;
        for (List<Integer> bucket : buckets) {
            bucketVisits++;
            for (int value : bucket) {
                elementVisits++;
                sum += value;
            }
        }
        return new ModelScan(bucketVisits, elementVisits, sum);
    }

    // Chi dung cac so nho de minh hoa lam tron ve luy thua cua 2.
    static int demoRoundedCapacity(int requested) {
        if (requested < 1 || requested > 1_048_576) {
            throw new IllegalArgumentException("outside demo range");
        }
        int capacity = 1;
        while (capacity < requested) {
            capacity *= 2;
        }
        return capacity;
    }

    static void showInvalid(String name, Runnable constructor) {
        try {
            constructor.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            System.out.println(name + ": IllegalArgumentException");
        }
    }

    public static void main(String[] args) {
        List<Integer> keys = List.of(1, 2, 3);
        Set<Integer> small = new HashSet<>(16);
        Set<Integer> wide = new HashSet<>(1024);
        small.addAll(keys);
        wide.addAll(keys);
        System.out.println("same members: " + small.equals(wide));
        System.out.println("actual small returned: " + returnedByIterator(small));
        System.out.println("actual wide returned: " + returnedByIterator(wide));
        System.out.println("model capacity16: " + scanFixedBuckets(16, keys));
        System.out.println("model capacity1024: " + scanFixedBuckets(1024, keys));
        System.out.printf("duplicate add: returned=%s, size=%d%n",
                small.add(2), small.size());

        int capacity = 16;
        int threshold = 12;
        for (int size = 1; size <= 25; size++) {
            if (size > threshold) {
                capacity *= 2;
                threshold = (int) (capacity * 0.75f);
            }
            if (size == 1 || size == 12 || size == 13 || size == 24 || size == 25) {
                System.out.printf("growth model: size=%d, capacity=%d, threshold=%d%n",
                        size, capacity, threshold);
            }
        }

        int expected = 1000;
        int rawRequested = expected;
        int plannedRequested = (int) Math.ceil(expected / 0.75d);
        int rawCapacity = demoRoundedCapacity(rawRequested);
        int plannedCapacity = demoRoundedCapacity(plannedRequested);
        System.out.printf("request1000 model: capacity=%d, threshold=%d%n",
                rawCapacity, (int) (rawCapacity * 0.75f));
        System.out.printf("plan1000 model: requested=%d, capacity=%d, threshold=%d%n",
                plannedRequested, plannedCapacity, (int) (plannedCapacity * 0.75f));

        Set<Integer> sparse = new HashSet<>();
        for (int value = 0; value < 1000; value++) {
            sparse.add(value);
        }
        for (int value = 3; value < 1000; value++) {
            sparse.remove(value);
        }
        Set<Integer> rebuilt = new HashSet<>(sparse);
        System.out.println("after removals: " + returnedByIterator(sparse));
        System.out.printf("rebuilt: sameMembers=%s, returned=%s%n",
                sparse.equals(rebuilt), returnedByIterator(rebuilt));
        sparse.clear();
        System.out.println("after clear: size=" + sparse.size());

        showInvalid("negative capacity", () -> new HashSet<>(-1));
        showInvalid("zero load factor", () -> new HashSet<>(16, 0.0f));
        showInvalid("NaN load factor", () -> new HashSet<>(16, Float.NaN));
        Set<Integer> greaterThanOne = new HashSet<>(16, 2.0f);
        System.out.println("load factor 2.0 accepted: size=" + greaterThanOne.size());
        System.out.println("capacity zero accepted: size=" + new HashSet<Integer>(0).size());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 HashSetCapacityDemo.java
java HashSetCapacityDemo
~~~

### 7.2. Kết quả stdout

~~~text
same members: true
actual small returned: Returned[elements=3, sum=6]
actual wide returned: Returned[elements=3, sum=6]
model capacity16: ModelScan[buckets=16, elements=3, sum=6]
model capacity1024: ModelScan[buckets=1024, elements=3, sum=6]
duplicate add: returned=false, size=3
growth model: size=1, capacity=16, threshold=12
growth model: size=12, capacity=16, threshold=12
growth model: size=13, capacity=32, threshold=24
growth model: size=24, capacity=32, threshold=24
growth model: size=25, capacity=64, threshold=48
request1000 model: capacity=1024, threshold=768
plan1000 model: requested=1334, capacity=2048, threshold=1536
after removals: Returned[elements=3, sum=3]
rebuilt: sameMembers=true, returned=Returned[elements=3, sum=3]
after clear: size=0
negative capacity: IllegalArgumentException
zero load factor: IllegalArgumentException
NaN load factor: IllegalArgumentException
load factor 2.0 accepted: size=0
capacity zero accepted: size=0
~~~

### 7.3. Giải thích kết quả

Hai set thật có cùng các thành viên 1, 2, 3. Iterator trả đúng ba phần tử, tổng là 6, dù các yêu cầu capacity ban đầu khác nhau.

Mô hình cố định vẫn xử lý ba phần tử nhưng quét 16 hoặc 1024 bucket. Đây là cách làm lộ phần c trong công thức, không phải một tuyên bố rằng thời gian thực chênh đúng 64 lần.

`scanFixedBuckets` dùng các danh sách Java để biểu diễn bucket và một phép chia lấy dư đơn giản. Nó không tái hiện toàn bộ cách cấp phát, hash, xử lý va chạm, cây hay tối ưu iterator của HashMap. Số đối tượng và số byte trong mô hình không được dùng để ước lượng bộ nhớ thật của HashSet.

Thêm lại 2 trả false và giữ size = 3. Điều đó khác một lần thêm khóa mới làm size tiến qua threshold.

Nhóm growth model chỉ dùng chuỗi size và điều kiện ngưỡng thông thường; nó không quan sát một HashSet để đếm số lần resize. Các dòng 12→13 và 24→25 kiểm tra phép tính trong bảng giải thích.

Hai dòng request1000 và plan1000 phân biệt số yêu cầu constructor với capacity làm tròn và threshold. Hàm làm tròn được giới hạn cố ý cho các số nhỏ của bài học, không là API sizing sản xuất.

Sau khi xóa 997 trong số 1000 khóa, set còn 0, 1, 2 nên số phần tử là 3 và tổng là 3. Bản sao mới có cùng thành viên; chương trình không suy từ equality ra rằng hai cấu trúc có cùng capacity.

Các kiểm tra constructor chứng minh capacity âm, load factor 0 và NaN bị từ chối, còn 2,0 được chấp nhận. Capacity 0 cũng hợp lệ; nó tạo một set rỗng có thể tiếp tục thêm, không là yêu cầu cấm mọi phần tử về sau.

### 7.4. Kết quả này chưa phải benchmark

Không có giá trị nano giây hoặc số byte nào được đo. Các bộ đếm mô hình là công việc đã được định nghĩa rõ trong mã, còn set thật chỉ được quan sát qua API công khai.

Muốn so sánh hiệu năng thực, phải chuẩn bị đúng kiểu dữ liệu, trạng thái đầy/thưa, số lần duyệt và phạm vi tính cấp phát. Một phép đo chỉ chạy một vòng có thể chịu ảnh hưởng khởi động JVM, JIT, bộ gom rác và tải máy.

Đặc biệt, đừng đếm số lần chạy thân for-each rồi coi đó là toàn bộ công việc của iterator; đây chính là điều bài muốn phân biệt.

## 8. Khi phần lớn công việc là duyệt, có lựa chọn nào khác?

### 8.1. LinkedHashSet đi qua chuỗi các mục

`LinkedHashSet` duy trì liên kết giữa các mục để có thứ tự duyệt xác định. Tài liệu [LinkedHashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html) mô tả thời gian duyệt tỷ lệ với số phần tử, không phụ thuộc capacity theo cách duyệt bảng của HashSet.

Điều này có thể hữu ích nếu ứng dụng vừa cần tính duy nhất vừa thường xuyên duyệt một bảng thưa. Tuy nhiên, nó cần thêm thông tin liên kết và công việc bảo trì các liên kết; không có kết luận rằng nó luôn tốt hơn về mọi khía cạnh.

Nếu không cần thứ tự, vẫn có thể cân nhắc nó theo mẫu truy cập, nhưng cần đo khi hiệu năng thực là tiêu chí quyết định.

### 8.2. Sau pha loại trùng, một List có thể đủ cho pha đọc

Nếu chỉ cần set để lọc trùng lúc xây dữ liệu, rồi nhiều lần duyệt mà không cần kiểm tra thành viên nữa, có thể tạo một danh sách kết quả tại ranh giới giữa hai pha.

Việc chuyển cần một lần đọc set hiện tại và bộ nhớ cho danh sách. Những lần duyệt danh sách về sau không cần quét bảng bucket của set.

Cách này chỉ phù hợp nếu hợp đồng cho phép một bản chụp độc lập về cấu trúc. Nếu set nguồn tiếp tục thay đổi, danh sách không tự cập nhật.

### 8.3. Giữ HashSet nhưng chọn sizing hợp lý cũng có thể đủ

Không cần đổi cấu trúc chỉ vì đọc thấy công thức c+n. Nếu capacity và size có tỷ lệ hợp lý, lượng công việc có thể phù hợp với mục tiêu.

Thay đổi đơn giản như bỏ một con số pre-size quá lớn, ước lượng theo số khóa duy nhất hoặc dựng lại ở đúng vòng đời có thể giải quyết vấn đề.

Hãy chọn theo phần việc chiếm ưu thế: tìm thành viên, xây dữ liệu, xóa nhiều, hay duyệt lặp. Một cấu hình tối ưu cho một pha chưa chắc tối ưu cho pha khác.

## 9. Những hiểu nhầm thường gặp

| Hiểu nhầm | Cách hiểu đúng |
|---|---|
| Capacity là số phần tử hiện có | Đó là số bucket của bảng khi đã cấp |
| Capacity là số bucket không rỗng | Va chạm khiến nhiều khóa cùng một bucket |
| Load factor 0,75 nghĩa là luôn có 75% bucket được dùng | Đây là tham số ngưỡng, không phải tỷ lệ bucket không rỗng |
| Actual load luôn bằng load factor cấu hình | n/c thay đổi theo thêm, xóa và resize |
| new HashSet(1000) giữ được 1000 khóa trước mọi lần tăng | Còn làm tròn bảng, threshold và đường xử lý va chạm |
| Load factor bắt buộc nhỏ hơn hoặc bằng 1 | Giá trị dương lớn hơn 1 có thể hợp lệ |
| Hạ load factor luôn làm mọi thao tác nhanh hơn | Có thể tăng bộ nhớ và chi phí duyệt bảng thưa |
| Chỉ cần biết size để biết chi phí duyệt | Cần xét cả capacity |
| Thân for-each chạy n lần nên iterator chỉ làm n bước | Còn phần tìm mục qua các bucket |
| Xóa gần hết thì bảng tự nhỏ ngay | Bản OpenJDK đang xét không tự thu bảng theo mỗi remove |
| Clear bảo đảm capacity về 0 | Làm rỗng nội dung không đồng nghĩa bỏ mảng bucket |
| Rebuild không tốn gì vì set mới nhỏ | Vẫn phải đọc nguồn và cấp cấu trúc mới |
| Bản sao set độc lập luôn có các đối tượng phần tử độc lập | Cấu trúc riêng, tham chiếu phần tử có thể chia sẻ |
| Công thức c+n cho được tỷ lệ tốc độ chính xác | Nó mô tả bậc công việc, không thay thế phép đo thời gian |

## 10. Bài tập tự kiểm tra

### Bài 1: tải thực tế

Size = 20, capacity = 64, load factor cấu hình = 0,75. Mức tải n/c có phải 0,75 không?

**Giải thích:** Không; 20/64 = 0,3125. Giá trị cấu hình là tham số ngưỡng, không tự bằng tỷ số hiện tại.

### Bài 2: ngưỡng thông thường

Capacity = 32, load factor = 0,75. Threshold theo mô hình thông thường là bao nhiêu? Size 24 và 25 khác nhau ra sao?

**Giải thích:** Threshold = 24. Size 24 chưa vượt ngưỡng; thêm khóa mới thành 25 vượt ngưỡng và có thể gây tăng theo đường đi đang xét.

### Bài 3: cùng size nhưng khác capacity

Hai set đều có 10 thành viên, một bảng 32 bucket, một bảng 65.536 bucket. Vì sao không thể chỉ dùng n = 10 để giải thích mọi công việc duyệt?

**Giải thích:** Iterator còn phải tìm các mục qua bảng. Khi c lớn hơn n rất nhiều, phần c có thể chiếm ưu thế. Không suy ra một tỷ lệ thời gian chính xác chỉ từ hai con số.

### Bài 4: đầu vào nhiều trùng

Có 100.000 dòng nhưng chỉ 100 mã khác nhau. Khi tính capacity cho một set mã, nên ưu tiên dự kiến 100.000 hay 100 thành viên nếu đã biết rõ dữ liệu?

**Giải thích:** Số thành viên duy nhất dự kiến là 100. Số dòng ảnh hưởng công việc đọc/xử lý đầu vào nhưng không bắt buộc set cần giữ 100.000 khóa khác nhau.

### Bài 5: thu bảng sau xóa

Một set từng rất lớn, nay còn ít khóa và sẽ được đọc nhiều lần trong thời gian dài. Tạo set mới từ các khóa hiện tại có thể có lợi nhưng cần tính gì?

**Giải thích:** Tính chi phí duyệt bảng cũ, cấp set mới, bộ nhớ hai cấu trúc cùng tồn tại, các tham chiếu bên ngoài tới set cũ và việc dữ liệu có sắp tăng lại không.

### Bài 6: kết quả của mô hình

Mô hình quét 1024 bucket và trả 3 phần tử. Có được kết luận một HashSet thật cũng có đúng 1027 lệnh máy cho lần duyệt không?

**Giải thích:** Không. Bộ đếm biểu diễn hai loại công việc trừu tượng; JVM và triển khai có nhiều bước và tối ưu khác. Nó giúp thấy yếu tố capacity, không phải đếm lệnh máy.

Capacity và load factor quyết định cách HashSet dành không gian và tăng bảng; số thành viên quyết định lượng dữ liệu logic. Khi phân tích các lượt duyệt, hãy giữ cả hai đại lượng c và n, rồi đối chiếu với vòng đời và mẫu truy cập của ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-based-set-004">Chính sách null và việc không bảo đảm thứ tự duyệt</a>

<details>
<summary>Xem chi tiết</summary>

# Chính sách null và việc không bảo đảm thứ tự duyệt

## 1. Hai câu hỏi độc lập khi dùng một Set dựa trên băm

Khi dùng HashSet, cần phân biệt:

1. **Những giá trị nào được phép có mặt?** HashSet cho phép null, nhưng vẫn chỉ có tối đa một lần hiện diện của null.
2. **Các giá trị được duyệt theo thứ tự nào?** HashSet không bảo đảm thứ tự thêm, thứ tự sắp xếp hay một thứ tự duyệt cố định theo thời gian.

Một chính sách cho phép null không tạo ra vị trí ưu tiên cho null. Việc null xuất hiện đầu trong một lần in không phải hợp đồng rằng nó luôn đứng đầu.

Tương tự, “dùng băm” là một cơ chế lưu trữ và tìm kiếm, không tự quyết định mọi chính sách của lớp. LinkedHashSet cũng dựa trên băm nhưng bổ sung bảo đảm thứ tự thêm. Một tập khóa của ConcurrentHashMap cũng dựa trên băm nhưng không cho thêm null.

Vì vậy, câu đúng cần gắn với **lớp hoặc cách tạo cụ thể**, không chỉ với nhãn Set dùng băm.

Tài liệu [HashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html) nêu cả việc cho phép null và việc không bảo đảm thứ tự duyệt. Bài này lấy HashSet làm trường hợp chính, dùng Java 17 trở lên và thao tác tuần tự trong một luồng.

## 2. null trong HashSet là một giá trị hiện diện, không phải tập rỗng

### 2.1. Có tối đa một null

Với một HashSet có thể sửa thông thường:

| Lời gọi | Ý nghĩa |
| --- | --- |
| add(null) khi chưa có null | Thêm null, trả true |
| add(null) khi đã có null | Không thêm bản thứ hai, trả false |
| contains(null) | Hỏi trong tập có null không |
| remove(null) khi đang có | Xóa null, trả true |
| remove(null) khi không có | Không đổi tập, trả false |

Set không giữ số lần xuất hiện. Nếu nguồn có mười giá trị null, chuyển chúng vào HashSet không giữ lại thông tin “đã có mười lần thiếu dữ liệu”.

null vẫn được tính vào size. Một tập chỉ có null có size bằng 1 và isEmpty trả false.

### 2.2. null khác chuỗi rỗng và chuỗi "null"

Ba trường hợp này khác nhau:

```text
null       : không có tham chiếu đến một đối tượng String.
""         : một String có nội dung rỗng.
"null"     : một String chứa bốn ký tự n, u, l, l.
```
Một HashSet<String> có thể đồng thời chứa cả ba, vì chúng không phải cùng một giá trị theo quan hệ bằng nhau.

Kiểu tham số String không có nghĩa mọi phần tử chắc chắn khác null. Hệ thống kiểu Java thông thường không tự cấm null chỉ vì biến được khai báo Set<String>.

Đừng tự thay null bằng chuỗi `"null"` hoặc chuỗi rỗng nếu chưa có chính sách nghiệp vụ. Việc thay như vậy có thể gộp dữ liệu thiếu với một giá trị văn bản thật.

### 2.3. HashSet xử lý null không có nghĩa mã gọi được bỏ qua null

HashSet hỗ trợ lưu và hỏi null. Nhưng sau khi lấy phần tử, gọi một phương thức trên tham chiếu null vẫn có thể gây NullPointerException.

```java
// Đoạn minh họa: values có thể chứa null.
for (String value : values) {
    if (value == null) {
        handleMissingValue();
    } else {
        handleText(value);
    }
}
```
Việc HashSet không ném lỗi ở add(null) không bảo đảm một hàm xử lý chuỗi, một comparator hoặc một API nhận collection ở phía sau cũng chấp nhận null.

Nếu ứng dụng không cho dữ liệu thiếu, nên kiểm tra ở ranh giới đầu vào thay vì dựa vào việc HashSet tình cờ cho lưu nó.

## 3. Ví dụ chạy được: thêm, đếm và xóa null

Lưu chương trình vào `HashSetNullSemanticsDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;

public class HashSetNullSemanticsDemo {
    public static void main(String[] args) {
        Set<String> values = new HashSet<>();

        System.out.println("add first null = " + values.add(null));
        System.out.println("add second null = " + values.add(null));
        System.out.println("size after null adds = " + values.size());
        System.out.println("contains null = " + values.contains(null));
        System.out.println("empty while containing null = " + values.isEmpty());

        values.add("");
        values.add("null");
        values.add("A");
        System.out.println("size with distinct text values = " + values.size());
        System.out.println("contains empty text = " + values.contains(""));
        System.out.println("contains literal null text = " + values.contains("null"));

        int nullCount = 0;
        int textCount = 0;
        for (String value : values) {
            if (value == null) {
                nullCount++;
            } else {
                textCount++;
            }
        }
        System.out.println("null elements seen = " + nullCount);
        System.out.println("non-null elements seen = " + textCount);

        System.out.println("remove null = " + values.remove(null));
        System.out.println("remove null again = " + values.remove(null));
        System.out.println("contains null after removal = " + values.contains(null));
        System.out.println("remaining size = " + values.size());
    }
}
```
Biên dịch và chạy:

```shell
javac --release 17 HashSetNullSemanticsDemo.java
java HashSetNullSemanticsDemo
```
Kết quả:

```text
add first null = true
add second null = false
size after null adds = 1
contains null = true
empty while containing null = false
size with distinct text values = 4
contains empty text = true
contains literal null text = true
null elements seen = 1
non-null elements seen = 3
remove null = true
remove null again = false
contains null after removal = false
remaining size = 3
```
Giải thích:

- Hai lần add(null) chỉ tạo một lần hiện diện.
- Tập chứa null không rỗng.
- Thêm chuỗi rỗng, chuỗi "null" và A làm tổng số giá trị phân biệt thành bốn.
- Một lượt duyệt thấy một null và ba String không null.
- Xóa null lần đầu làm tập đổi; lần sau không còn null để xóa.

Chương trình không in cả HashSet theo một thứ tự cố định. Bộ đếm duyệt cho cùng kết quả dù các phần tử được gặp theo thứ tự nào.

Đây cũng là cách kiểm chứng phù hợp với hợp đồng: kiểm tra hiện diện, size và số phần tử thỏa điều kiện, thay vì gán ý nghĩa cho vị trí quan sát được của null.

## 4. Chính sách của collection và ý nghĩa nghiệp vụ không phải một thứ

### 4.1. Cho phép không có nghĩa bắt buộc phải dùng

HashSet cho phép null, nhưng một lớp nghiệp vụ có thể yêu cầu mọi mã đều khác null. Khi đó, có thể kiểm tra trước khi đưa vào tập.

```java
// Đoạn minh họa: Objects thuộc java.util.
String validCode = Objects.requireNonNull(code, "code");
codes.add(validCode);
```
Đây là một chính sách dữ liệu rõ ràng, không cần đổi sang một loại tập khác chỉ để tạo lỗi null.

Ngược lại, nếu null có ý nghĩa được chấp nhận, phải mô tả ý nghĩa đó: thiếu giá trị, chưa xác định hay một trạng thái đặc biệt nào. Set chỉ giữ hiện diện, không tự phân biệt nhiều nguyên nhân khác nhau cùng được mã hóa bằng null.

### 4.2. null của biến Set khác với null là một phần tử

```text
Set<String> values = null;
-> Không có đối tượng Set để gọi phương thức.

Một HashSet đã tạo, có chứa null:
-> Có đối tượng Set; null là một thành viên của nó.
```
Gọi values.contains(null) khi chính values là null vẫn gây NullPointerException trước khi có một thao tác collection hợp lệ.

Một hàm có thể yêu cầu đối số Set không null nhưng cho phép phần tử null. Hoặc nó có thể yêu cầu cả hai đều khác null. Hợp đồng cần nói rõ hai tầng này.

### 4.3. Không dùng null để giữ nhiều bản ghi thiếu riêng biệt

Nếu mỗi bản ghi thiếu cần được theo dõi riêng, đưa tất cả vào cùng một giá trị null trong Set sẽ gộp chúng thành một lần hiện diện.

Có thể cần một kiểu bản ghi có định danh riêng hoặc một collection giữ các lần xuất hiện. Quyết định này thuộc mô hình dữ liệu; một HashSet cho phép null không giải quyết được yêu cầu đếm hay phân biệt từng trường hợp thiếu.

## 5. “Không bảo đảm thứ tự” có nghĩa gì?

### 5.1. Không phải thứ tự thêm

Với đầu vào z, a, m, HashSet không hứa iterator sẽ trả z rồi a rồi m.

Nếu cần giữ thứ tự xuất hiện đầu tiên trong khi loại trùng, phải giữ thông tin đó từ nguồn và chọn một cách biểu diễn có hợp đồng tương ứng, chẳng hạn LinkedHashSet.

### 5.2. Không phải thứ tự sắp xếp

HashSet không tự sắp theo chữ cái, số, id, độ ưu tiên hoặc hashCode.

Ngay cả khi một tập số nhỏ tình cờ in tăng dần, không được lấy quan sát đó làm quy tắc của API. Một bộ dữ liệu khác, cách tạo khác hoặc môi trường khác có thể không tạo ra cùng kết quả.

Muốn phần tử nhỏ nhất, phải dùng một phép chọn theo thứ tự đã định nghĩa, không lấy phần tử đầu iterator rồi đặt tên là minimum.

### 5.3. Không có nghĩa “luôn bị xáo ngẫu nhiên”

Không bảo đảm không đồng nghĩa mỗi lần duyệt đều phải khác lần trước. Trong một môi trường cụ thể, với một trạng thái không đổi, ta có thể quan sát nhiều lần in giống nhau.

Điều đó chưa tạo ra một cam kết cho chương trình được phép dựa vào. Cũng không chứng minh HashSet là một bộ sinh lựa chọn ngẫu nhiên công bằng.

Nếu mục tiêu là chọn ngẫu nhiên, cần thuật toán chọn phù hợp. Nếu mục tiêu là đầu ra xác định, cần thứ tự được quy định rõ. HashSet không tự cung cấp hai điều đó qua “phần tử đầu”.

### 5.4. Không có vị trí đặc biệt được bảo đảm cho null

Một triển khai có thể dùng các quy ước nội bộ để lưu null, nhưng API HashSet không quy định “null luôn được duyệt đầu tiên” hay “null luôn nằm cuối”.

Không kiểm thử hoặc viết thuật toán bằng giả định đó. Chính sách hiện diện và vị trí duyệt là hai hợp đồng khác nhau.

## 6. Vì sao cấu trúc băm không tự giữ thứ tự đầu vào?

Một tập băm tổ chức các giá trị để phục vụ tìm kiếm hiện diện, sử dụng thông tin băm và cấu trúc lưu trữ bên trong.

Thứ tự duyệt quan sát được có thể chịu ảnh hưởng của:

- Hash code và cách các phần tử được phân bố trong bảng.
- Sức chứa và các lần thay đổi cấu trúc.
- Các lần thêm, xóa và trạng thái hiện tại.
- Chi tiết triển khai của phiên bản thư viện/JVM.

Đây là lời giải thích cơ chế, không phải một công thức API để dự đoán thứ tự.

Không cần thuộc cách ánh xạ từng bucket để dùng HashSet đúng. Điều cần nắm là **thông tin về thứ tự thêm không nằm trong bảo đảm của lớp**, nên không thể dùng nó như một danh sách đã lưu lịch sử nhập.

Thêm hoặc xóa có thể làm thứ tự thay đổi, nhưng không bắt buộc mọi thao tác đều phải tạo thứ tự khác. Một bài kiểm tra cố tình thêm phần tử rồi bắt buộc thứ tự đảo đi cũng đang kiểm tra một điều API không yêu cầu.

LinkedHashSet bổ sung liên kết để giữ thứ tự thêm. Nó cho thấy “dùng băm” và “có thứ tự duyệt được bảo đảm” có thể cùng tồn tại nếu lớp có hợp đồng bổ sung.

## 7. Khi đầu ra cần ổn định, phải chọn thứ tự có ý nghĩa

### 7.1. Cần giữ thứ tự xuất hiện đầu tiên

Dùng một nguồn có thứ tự phù hợp rồi đưa trực tiếp vào LinkedHashSet:

```java
// Đoạn minh họa: input có thứ tự gặp được mà chương trình cần giữ.
Set<String> values = new LinkedHashSet<>(input);
```
[LinkedHashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html) cho phép null và giữ thứ tự thêm. Vì vậy, null có vị trí theo lúc nó được thêm lần đầu như các giá trị khác.

Thêm lại một phần tử đang có không tự đẩy nó xuống cuối. Nếu xóa rồi thêm lại, đó là lần thêm mới và cần được hiểu riêng.

Nếu đã bỏ nguồn và chỉ còn một HashSet, sao chép nó sang LinkedHashSet chỉ giữ thứ tự gặp được từ HashSet lúc sao chép. Nó không khôi phục được lịch sử nhập ban đầu đã không còn được cung cấp.

### 7.2. Cần thứ tự so sánh

Có thể tạo một danh sách từ tập rồi sắp theo quy tắc rõ ràng. Khi có null, comparator cũng phải xử lý null.

```java
// Đoạn minh họa.
List<String> display = new ArrayList<>(values);
display.sort(Comparator.nullsLast(Comparator.naturalOrder()));
```
Trong ví dụ String này:

- Các String không null được so theo thứ tự tự nhiên.
- null được đặt sau các String.
- Việc sắp tạo ra một thứ tự trình bày có chủ đích.
- HashSet nguồn không vì thế mà đổi thành một tập có thứ tự được bảo đảm.

Có thể chọn nullsFirst nếu yêu cầu là đặt null trước. Xem [Comparator của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html) để dùng quy tắc so sánh null phù hợp.

Với kiểu phần tử phức tạp, nếu comparator coi nhiều phần tử khác nhau là ngang nhau, một phép sắp ổn định có thể vẫn giữ thứ tự nguồn không được bảo đảm giữa các phần tử đó. Nếu cần đầu ra hoàn toàn xác định, phải có quy tắc phân biệt phù hợp cho những trường hợp bằng nhau về tiêu chí chính.

### 7.3. Cần duy trì tập đã sắp trong lúc cập nhật

Một cấu trúc có thứ tự như TreeSet có thể đáp ứng yêu cầu đó, nhưng phải kiểm tra comparator, tiêu chí hiện diện và chính sách null.

Không dùng TreeSet theo thứ tự tự nhiên để sao chép mù quáng một HashSet có null rồi mong mọi dữ liệu được chấp nhận. Một nhu cầu mới về thứ tự có thể kéo theo chính sách đầu vào khác.

Bài tập trung vào hợp đồng của HashSet; lựa chọn thứ tự đầu ra cần được quyết định theo yêu cầu cụ thể.

## 8. Ví dụ chạy được: không cần giả định thứ tự HashSet

Lưu vào `HashSetOrderContractDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class HashSetOrderContractDemo {
    public static void main(String[] args) {
        List<String> input = Arrays.asList("z", null, "a", "z", "m");
        Set<String> hash = new HashSet<>(input);

        System.out.println("distinct count = " + hash.size());
        System.out.println("contains every input value = " + hash.containsAll(input));

        Set<String> sameMembers = new HashSet<>(
                Arrays.asList("m", "a", null, "z"));
        System.out.println("same members from another input order = "
                + hash.equals(sameMembers));

        LinkedHashSet<String> insertionOrder = new LinkedHashSet<>(input);
        System.out.println("LinkedHashSet insertion order = " + insertionOrder);

        List<String> sortedForDisplay = new ArrayList<>(hash);
        sortedForDisplay.sort(Comparator.nullsLast(Comparator.naturalOrder()));
        System.out.println("explicit sorted display = " + sortedForDisplay);

        String arbitrary = hash.iterator().next();
        System.out.println("iterator first belongs to set = "
                + hash.contains(arbitrary));
    }
}
```
Biên dịch và chạy:

```shell
javac --release 17 HashSetOrderContractDemo.java
java HashSetOrderContractDemo
```
Kết quả:

```text
distinct count = 4
contains every input value = true
same members from another input order = true
LinkedHashSet insertion order = [z, null, a, m]
explicit sorted display = [a, m, z, null]
iterator first belongs to set = true
```
Những điều chương trình làm có chủ đích:

1. Dùng Arrays.asList để tạo đầu vào có null; không dùng List.of vì factory đó từ chối null.
2. HashSet có bốn giá trị phân biệt từ năm lần xuất hiện đầu vào.
3. Hai HashSet tạo từ thứ tự khác nhau vẫn bằng nhau về thành viên.
4. LinkedHashSet giữ z, null, a, m theo thứ tự gặp lần đầu.
5. Danh sách được sắp rõ ràng có kết quả a, m, z, null.
6. Phần tử đầu iterator được gọi là arbitrary, tức một lựa chọn theo thứ tự duyệt của triển khai, không được gọi là phần tử nhập đầu hay phần tử nhỏ nhất.

Dòng cuối chỉ kiểm tra rằng phần tử được iterator trả về thật sự thuộc tập. Chương trình không in một giá trị cố định cho arbitrary.

Ngay cả nếu một lần chạy cụ thể trả null trước, output hợp đồng vẫn không được viết thành “iterator luôn trả null”. Tập này không rỗng, nên next có một phần tử để trả; nếu tập rỗng, Iterator.next có hợp đồng lỗi khác, không dùng null như dấu hiệu kết thúc chung.

## 9. Không phải mọi Set dùng băm đều nhận null

### 9.1. Phải xem lớp hoặc cách tạo cụ thể

| Cách tạo | Chính sách null cần hiểu | Thứ tự cần hiểu |
| --- | --- | --- |
| new HashSet<>() | Cho phép tối đa một null | Không bảo đảm thứ tự duyệt |
| new LinkedHashSet<>() | Cho phép tối đa một null | Thứ tự thêm |
| ConcurrentHashMap.newKeySet() | Không cho thêm null | Không dùng như tập giữ thứ tự thêm hoặc đã sắp |
| Set.of(...) / Set.copyOf(...) | Từ chối phần tử null | Không dựa vào thứ tự duyệt được bảo đảm |
| Collections.unmodifiableSet(source) | Đọc qua source; không tự lọc hay cấm null đã có | Đọc thứ tự qua source, không tự tạo thứ tự mới |

Hai dòng factory Set là ví dụ về hợp đồng collection; không cần và không nên dựa vào một giả định rằng chúng luôn có cùng triển khai nội bộ với HashSet.

[ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html) không cho null làm khóa; tập tạo bởi newKeySet được hậu thuẫn bằng cấu trúc đó. Điều này cho thấy null là chính sách của lớp, không phải hệ quả bắt buộc của việc có hay không có bảng băm.

Không suy tiếp rằng “mọi collection đồng thời đều có cùng chính sách null”. Cần xem hợp đồng của lựa chọn thực tế.

### 9.2. Thay lớp có thể làm lộ dữ liệu null đang tồn tại

Một đoạn mã đang dùng HashSet có thể chấp nhận null từ lâu. Khi chuyển sang một cách tạo khác để có đầu ra không sửa được hoặc để dùng cấu trúc khác, null có thể bị từ chối.

Vì vậy, thay lớp collection không chỉ là thay tốc độ. Cần kiểm tra xem chính sách dữ liệu và bảo đảm thứ tự có còn phù hợp.

Nếu quyết định bỏ null, hãy làm rõ đó là lọc dữ liệu theo chính sách, không coi nó là một phép sao chép giữ nguyên nội dung. Nếu quyết định báo lỗi, cần để người gọi hiểu điều kiện đầu vào.

## 10. Bản sao, vùng nhìn không sửa được và null

### 10.1. HashSet copy giữ null nhưng không tạo bảo đảm thứ tự

new HashSet<>(source) có thể tạo cấu trúc Set độc lập với cùng các giá trị hợp lệ, kể cả null nếu nguồn có null.

Việc thêm một thành viên mới vào source sau khi sao chép không tự thêm nó vào bản sao. Nhưng bản sao đó vẫn không bảo đảm thứ tự duyệt, và không tự sao chép sâu từng đối tượng phần tử.

### 10.2. Set.copyOf không nhận null

[Set.copyOf trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#copyOf(java.util.Collection)) trả một Set không sửa cấu trúc được và từ chối nguồn chứa null.

Do đó, ý định “chỉ muốn bảo vệ khỏi add/remove” không có nghĩa thay mọi phép copy bằng Set.copyOf sẽ luôn giữ cùng hợp đồng dữ liệu.

Nếu null là dữ liệu hợp lệ, cần một cách xuất kết quả phù hợp với null. Nếu null không hợp lệ, có thể dùng bước kiểm tra đó như một phần chính sách rõ ràng, nhưng đừng giấu việc dữ liệu bị từ chối.

### 10.3. unmodifiableSet là vùng nhìn đọc qua nguồn

Theo [Collections.unmodifiableSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableSet(java.util.Set)), vùng nhìn không cho sửa qua chính nó nhưng các truy vấn đọc qua tập nguồn.

Nếu source là HashSet có null, vùng nhìn có thể đọc thấy null. Nếu một biến khác tiếp tục thêm/xóa source, vùng nhìn phản ánh thay đổi đó.

Đây không phải ảnh chụp độc lập.

Nếu cần vừa tách cấu trúc khỏi nguồn, vừa không cho sửa qua kết quả và vẫn chấp nhận null, có thể tạo bản sao riêng rồi bọc nó:

```java
// Đoạn minh họa: bản sao không được đưa ra ngoài qua tham chiếu khác.
Set<String> result = Collections.unmodifiableSet(new HashSet<>(source));
```
Mẫu này vẫn không tạo thứ tự duyệt được bảo đảm hoặc bất biến sâu của phần tử. Nếu cần thứ tự, phải chọn bản sao có hợp đồng thứ tự tương ứng ngay trong thiết kế.

## 11. Ví dụ chạy được: chính sách thay đổi khi sao chép hoặc bọc

Lưu vào `HashSetNullCopyPolicyDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HashSetNullCopyPolicyDemo {
    public static void main(String[] args) {
        Set<String> source = new HashSet<>();
        source.add("B");
        source.add(null);
        source.add("A");

        Set<String> independent = new HashSet<>(source);
        System.out.println("HashSet copy contains null = "
                + independent.contains(null));

        try {
            Set.copyOf(source);
        } catch (NullPointerException ex) {
            System.out.println("Set.copyOf nullable source = "
                    + ex.getClass().getSimpleName());
        }

        Set<String> readOnlyView = Collections.unmodifiableSet(source);
        System.out.println("read-only view contains null = "
                + readOnlyView.contains(null));

        source.add("C");
        System.out.println("view sees later C = " + readOnlyView.contains("C"));
        System.out.println("independent copy sees later C = "
                + independent.contains("C"));

        try {
            readOnlyView.add("D");
        } catch (UnsupportedOperationException ex) {
            System.out.println("read-only view add = "
                    + ex.getClass().getSimpleName());
        }

        List<String> display = new ArrayList<>(readOnlyView);
        display.sort(Comparator.nullsLast(Comparator.naturalOrder()));
        System.out.println("sorted nullable view = " + display);

        Set<String> concurrent = ConcurrentHashMap.newKeySet();
        try {
            concurrent.add(null);
        } catch (NullPointerException ex) {
            System.out.println("concurrent key set add null = "
                    + ex.getClass().getSimpleName());
        }
    }
}
```
Biên dịch và chạy:

```shell
javac --release 17 HashSetNullCopyPolicyDemo.java
java HashSetNullCopyPolicyDemo
```
Kết quả:

```text
HashSet copy contains null = true
Set.copyOf nullable source = NullPointerException
read-only view contains null = true
view sees later C = true
independent copy sees later C = false
read-only view add = UnsupportedOperationException
sorted nullable view = [A, B, C, null]
concurrent key set add null = NullPointerException
```
Đọc kết quả theo từng hợp đồng:

- Bản sao HashSet nhận null.
- Set.copyOf từ nguồn có null ném NullPointerException.
- Vùng nhìn unmodifiableSet đọc thấy null từ nguồn.
- Thêm C ở nguồn xuất hiện qua vùng nhìn nhưng không xuất hiện trong bản sao độc lập.
- Thử thêm D qua vùng nhìn bị từ chối.
- Danh sách trình bày được sắp rõ ràng với null cuối.
- Tập khóa concurrent trong ví dụ từ chối null.

Chương trình không chạy nhiều luồng; dòng newKeySet chỉ minh họa chính sách null của một cách tạo Set cụ thể.

Bằng cách tách các dòng output về thành viên, quyền sửa và thứ tự trình bày, ví dụ tránh gộp chúng thành một thuộc tính mơ hồ “Set này an toàn hơn” hoặc “Set này tốt hơn”.

## 12. So sánh và kiểm chứng đúng thứ mà hợp đồng bảo đảm

### 12.1. So nội dung tập khi thứ tự không thuộc yêu cầu

Hai Set tuân thủ cùng quan hệ bằng nhau có thể equals nhau dù duyệt khác thứ tự. Muốn kiểm tra có cùng thành viên, dùng hợp đồng Set.equals hoặc các kiểm tra hiện diện/kích thước phù hợp.

Không chuyển cả hai HashSet thành List rồi so List.equals để quyết định nội dung tập có đúng hay không. List.equals có xét vị trí.

Tương tự, toArray hoặc new ArrayList<>(set) chỉ thu các phần tử theo thứ tự gặp được, không tự sắp hoặc khôi phục lịch sử thêm.

### 12.2. Khi thứ tự là yêu cầu, kiểm tra trên đầu ra đã có chính sách

Nếu API đầu ra phải sắp tăng dần và để null cuối, hãy kiểm tra danh sách kết quả sau phép sắp với comparator đã chọn.

Nếu API đầu ra phải giữ thứ tự thêm, hãy giữ nguồn có thứ tự và cấu trúc tương ứng rồi kiểm tra thứ tự đó.

Không kiểm tra một thứ tự mà HashSet chưa từng được yêu cầu cung cấp.

### 12.3. Không dựa vào hình thức in để phân biệt null

Một null và String có nội dung "null" có thể được hiển thị giống nhau khi in một collection thông thường. Điều đó không làm chúng bằng nhau.

Khi chẩn đoán dữ liệu, cần biểu diễn đủ rõ để phân biệt không có tham chiếu với một giá trị văn bản. Ví dụ, có thể dùng dấu trích dẫn cho chuỗi và một ký hiệu riêng cho null trong phần trình bày.

Nhưng ký hiệu trình bày không nên được đưa ngược thành khóa dữ liệu thay cho null nếu điều đó làm va chạm với một chuỗi thật.

### 12.4. Những điều không nên bắt buộc trong bài kiểm tra HashSet

- Không bắt buộc null luôn ở đầu hoặc luôn ở cuối.
- Không bắt buộc hai lần duyệt phải khác nhau.
- Không bắt buộc thêm/xóa một phần tử luôn làm thứ tự đổi.
- Không coi HashSet có cùng nội dung là phải có cùng chuỗi toString.
- Không dùng việc chưa gặp ConcurrentModificationException làm chứng cứ an toàn luồng.

Hợp đồng là cơ sở để chọn điều phải đúng. Một quan sát nội bộ hoặc một lần chạy không mở rộng hợp đồng đó.

## 13. Các lỗi phổ biến và cách sửa mô hình

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “Set<String> nghĩa là không thể có null” | Kiểu tham chiếu vẫn có thể nhận null nếu triển khai cho phép |
| “Thêm null hai lần phải tăng size hai lần” | HashSet chỉ giữ một lần hiện diện của null |
| “Tập chỉ có null là tập rỗng” | Nó có một thành viên |
| “null và chuỗi 'null' là cùng dữ liệu” | Chúng khác nhau về giá trị và ý nghĩa |
| “HashSet hỗ trợ null nên mọi hàm xử lý phần tử đều hỗ trợ null” | Phải xem hợp đồng của từng hàm ở phía sau |
| “null có quy ước băm nên chắc chắn đứng đầu khi duyệt” | Không có bảo đảm vị trí đó trong API HashSet |
| “Không có thứ tự nghĩa là mỗi lần phải xáo lại” | Có thể quan sát ổn định nhưng không được dựa vào như hợp đồng |
| “HashSet đang in giống thứ tự thêm nên dùng luôn” | Khi cần thứ tự thêm, chọn cấu trúc có bảo đảm đó |
| “Bọc unmodifiableSet là một bản sao cố định” | Nó là vùng nhìn đọc qua nguồn |
| “Set.copyOf chỉ đổi quyền sửa, không đổi chính sách null” | Nó từ chối phần tử null |
| “Chuyển HashSet thành LinkedHashSet khôi phục lịch nhập” | Chỉ giữ thứ tự gặp được từ nguồn lúc sao chép |
| “Sắp dữ liệu String luôn xử lý null tự động” | Cần comparator hoặc chính sách null phù hợp |
| “Cùng dùng băm thì mọi Set cùng nhận null” | Chính sách thuộc lớp/cách tạo cụ thể |

## 14. Thực hành xác định hợp đồng đầu ra

### Bài 1: Đếm dữ liệu thiếu

Nguồn có null, null, A. Đưa vào HashSet rồi hỏi size không thể cho biết có hai bản ghi thiếu. Tập chỉ biểu diễn hiện diện của null và A. Muốn thống kê số lần thiếu phải giữ hoặc đếm các lần xuất hiện.

### Bài 2: Kết quả in có hai chữ null

Một Set giữ null và String "null" không nhất thiết vi phạm loại trùng dù hình thức in có thể làm chúng giống nhau. Kiểm tra bằng value == null hoặc một định dạng chẩn đoán rõ ràng thay vì chỉ nhìn chữ in ra.

### Bài 3: Cần thứ tự nhập

Nguồn có B, null, A, B. Muốn đầu ra B, null, A theo lần xuất hiện đầu tiên thì dùng cấu trúc giữ thứ tự từ chính nguồn đó. Đừng gom vào HashSet rồi sau đó cố suy lại thứ tự nhập.

### Bài 4: Cần sắp với null cuối

Có các giá trị B, null, A. Tạo bản danh sách và dùng Comparator.nullsLast với quy tắc so các giá trị không null. Kết quả trình bày A, B, null là do comparator, không phải do HashSet vốn có thứ tự đó.

### Bài 5: Cần kết quả không sửa được nhưng null hợp lệ

Set.copyOf không đáp ứng chính sách nhận null. Có thể tạo bản sao riêng bằng cấu trúc cho phép null rồi bọc không sửa được, đồng thời chọn rõ có cần bảo đảm thứ tự hay không. Phần tử có thể sửa vẫn cần chính sách riêng.

### Bài 6: Chuyển sang tập khóa của ConcurrentHashMap

Không được giả định null được giữ chỉ vì cả cấu trúc cũ và mới dùng băm. Cần kiểm tra và quyết định cách xử lý null trước khi chuyển. Chương trình ví dụ đã xác nhận cách tạo newKeySet từ chối thêm null.

### Bài 7: Chọn phần tử đầu

HashSet không rỗng và iterator.next trả một phần tử. Điều đó không chứng minh đây là giá trị nhỏ nhất, giá trị nhập đầu hay một lựa chọn ngẫu nhiên công bằng. Hãy dùng thuật toán tương ứng với mục tiêu thực.

Trước khi dùng một Set dựa trên băm, hãy nêu riêng **null có ý nghĩa gì, collection nào nhận nó, các hàm phía sau xử lý ra sao và đầu ra có cần thứ tự nào**. Hai chính sách về dữ liệu và thứ tự phải được giữ rõ ràng từ đầu vào đến lúc trình bày kết quả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-based-set-005">LinkedHashSet và thứ tự chèn</a>

<details>
<summary>Xem chi tiết</summary>

# LinkedHashSet và thứ tự chèn

## 1. Khi cần loại trùng nhưng vẫn giữ thứ tự gặp đầu tiên

Giả sử dữ liệu đầu vào là:

```text
sql, java, sql, http, java
```

Yêu cầu là giữ mỗi giá trị một lần và trình bày theo lần xuất hiện đầu tiên:

```text
sql, java, http
```

Ở đây có hai yêu cầu cùng tồn tại:

- Không giữ nhiều phần tử bằng nhau: ý nghĩa của `Set`.
- Có thứ tự duyệt xác định theo lịch sử chèn: đặc tính của `LinkedHashSet`.

`LinkedHashSet<E>` là một tập dùng băm và duy trì thêm liên kết giữa các phần tử để duyệt theo thứ tự chèn. Nó phù hợp khi cần loại trùng mà không muốn mất trình tự đầu vào đã có ý nghĩa.

Điều quan trọng là hiểu đúng “thứ tự chèn”:

> Các phần tử đang có trong tập được duyệt theo thứ tự chúng được thêm mới. Thử thêm lại một phần tử đã có không làm phần tử đó chuyển xuống cuối.

Nó không có nghĩa phần tử được tự sắp xếp theo bảng chữ cái hoặc theo số. Nó cũng không có nghĩa phần tử vừa được tìm bằng `contains` sẽ trở thành phần tử cuối.

Các ví dụ trong bài dùng API tương thích Java 17. Ta tập trung vào `add`, `contains`, `remove`, cách duyệt và sao chép để thấy rõ quy tắc thứ tự. [Đặc tả LinkedHashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)

## 2. Hai cấu trúc phối hợp: băm để tìm, liên kết để duyệt

Có thể hiểu `LinkedHashSet` qua hai trách nhiệm:

| Cơ chế | Trách nhiệm |
| --- | --- |
| Cấu trúc băm | Kiểm tra phần tử đã có chưa, tìm và xóa theo giá trị |
| Chuỗi liên kết giữa các mục | Ghi lại thứ tự duyệt của các phần tử đang được lưu |

Ví dụ sau ba lần thêm mới `"sql"`, `"java"`, `"http"`, có thể hình dung phần thứ tự như sau:

```text
đầu → sql ↔ java ↔ http ← cuối
```

Đây là sơ đồ khái niệm về liên kết hai chiều phục vụ thứ tự, không phải một danh sách mà người gọi có thể truy cập theo chỉ số.

Khi gọi `contains("java")`, tập dùng cơ chế băm để tìm theo quy tắc bằng nhau. Nó không cần coi `"java"` là “phần tử số 1” trong một danh sách.

Khi duyệt bằng `for-each`, bộ duyệt đi qua thứ tự đã được duy trì:

```java
// Đoạn minh họa: topics là một LinkedHashSet<String>.
for (String topic : topics) {
    System.out.println(topic);
}
```

Với trạng thái trong sơ đồ, thứ tự các dòng là `sql`, `java`, `http`.

Thay đổi dung lượng cấu trúc băm không biến quy tắc duyệt thành sắp xếp theo mã băm. Trật tự liên kết phục vụ duyệt là một phần riêng của thiết kế. Người dùng không cần biết chỉ số ngăn băm nội bộ để suy luận thứ tự.

## 3. Quy tắc thứ tự sau từng thao tác

### 3.1. Thêm một giá trị chưa có: đặt ở cuối

Bắt đầu từ tập rỗng:

```text
add("sql")  → [sql]
add("java") → [sql, java]
add("http") → [sql, java, http]
```

Các lời gọi đều trả về `true`, vì mỗi lần có một phần tử mới được thêm vào tập.

Thứ tự này là thứ tự chèn, không phải thứ tự chữ cái. Nếu chèn `"http"` trước `"sql"`, trình tự duyệt cũng phản ánh lựa chọn đó.

### 3.2. Thêm một giá trị đã có: giữ vị trí cũ

Nếu tập hiện là `[sql, java, http]`:

```java
// Đoạn minh họa.
boolean changed = topics.add(new String("sql"));
```

Kết quả là `false`; tập vẫn là:

```text
[sql, java, http]
```

Chuỗi được tạo bằng `new String("sql")` khác tham chiếu với một chuỗi khác nhưng bằng nhau theo giá trị. Tập không thêm bản sao và không chuyển `"sql"` về cuối.

Từ “chèn” không được hiểu là đếm mọi lần gọi `add`. Chỉ khi phần tử chưa có và thực sự được thêm mới thì nó nhận vị trí mới trong thứ tự.

Hợp đồng của `LinkedHashSet` nêu rõ việc thêm lại một phần tử đang tồn tại không thay đổi thứ tự chèn. [LinkedHashSet: quy tắc thêm lại phần tử](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)

### 3.3. contains không thay đổi thứ tự

Một phép kiểm tra thuộc tập:

```java
boolean exists = topics.contains("java");
```

không đưa `"java"` lên đầu hoặc xuống cuối. Nếu trạng thái trước là `[sql, java, http]`, trạng thái sau vẫn như vậy.

Vì thế, `LinkedHashSet` thông thường không phải cấu trúc “phần tử vừa truy cập sẽ thành mới nhất”. Nếu bài toán cần thứ tự theo lần truy cập hoặc một cơ chế bộ nhớ đệm riêng, phải chọn thiết kế đáp ứng yêu cầu đó; không suy ra từ chữ `Linked`.

### 3.4. Xóa phần tử: bỏ vị trí của nó, giữ thứ tự tương đối của phần còn lại

Từ `[sql, java, http]`, gọi `remove("java")` thành công:

```text
trước: [sql, java, http]
sau:   [sql, http]
```

`"sql"` vẫn trước `"http"`. Tập không sắp xếp lại các phần tử còn lại.

Xóa một giá trị không có trong tập trả về `false` và không thay đổi thứ tự. `remove` nhận giá trị cần xóa; `LinkedHashSet` trên Java 17 không cung cấp phép xóa theo chỉ số như một `List`.

### 3.5. Xóa rồi thêm lại: đây là một lần thêm mới

Nếu `"java"` đã được xóa khỏi tập `[sql, http]`, sau đó gọi `add("java")`:

```text
[sql, http, java]
```

Phần tử không còn tồn tại ngay trước lần thêm, nên nó được thêm mới ở cuối.

Cần phân biệt hai chuỗi thao tác:

| Thao tác khi tập đang là [sql, java, http] | Kết quả cuối |
| --- | --- |
| Chỉ gọi `add("java")` | `[sql, java, http]` |
| Gọi `remove("java")`, rồi `add("java")` | `[sql, http, java]` |

Nếu dùng chuỗi xóa–thêm để thay đổi vị trí, hãy hiểu rằng đó là sửa nội dung tập qua hai thao tác. Nó không tự có tính nguyên tử, và không phù hợp để thực hiện tùy tiện khi một bộ duyệt khác đang hoạt động.

### 3.6. addAll: phần tử cũ giữ chỗ, phần tử mới nối theo thứ tự gặp

Giả sử tập đang là:

```text
[sql, http, java]
```

Thêm một danh sách theo thứ tự:

```java
topics.addAll(List.of("http", "git", "sql", "docker"));
```

Kết quả:

```text
[sql, http, java, git, docker]
```

`"http"` và `"sql"` đã có nên giữ nguyên chỗ. `"git"` và `"docker"` được thêm ở cuối theo thứ tự chúng được gặp trong danh sách.

Nếu nguồn của `addAll` không có cam kết thứ tự duyệt, không thể suy ra một trình tự mới được bảo đảm chỉ từ các giá trị mà nguồn chứa. `LinkedHashSet` giữ thứ tự nó tiếp nhận; nó không tạo ra lịch sử chèn đã bị mất ở phía nguồn.

### 3.7. null cũng chịu cùng quy tắc không trùng và thứ tự

`LinkedHashSet` cho phép `null`, nhưng tối đa một phần tử `null`.

Nếu tập là `[a, null, b]`:

- `add(null)` trả về `false`, thứ tự không đổi.
- Xóa `null` rồi thêm lại tạo thứ tự `[a, b, null]`.

Để tạo nguồn có `null` trong ví dụ, dùng `Arrays.asList(...)`. `List.of(...)` không cho phần tử `null`, nên không phù hợp cho đầu vào đó.

## 4. Chương trình đầy đủ: quan sát thứ tự sau mỗi thao tác

Lưu toàn bộ mã vào **LinkedHashSetOrderDemo.java**:

```java
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LinkedHashSetOrderDemo {
    public static void main(String[] args) {
        Set<String> topics = new LinkedHashSet<>();
        topics.add("sql");
        topics.add("java");
        topics.add("http");
        System.out.println("initial: " + topics);

        System.out.println("contains java: " + topics.contains("java"));
        System.out.println("after contains: " + topics);
        System.out.println("add equal sql: " + topics.add(new String("sql")));
        System.out.println("after duplicate: " + topics);

        System.out.println("remove java: " + topics.remove("java"));
        System.out.println("after removal: " + topics);
        System.out.println("add java again: " + topics.add("java"));
        System.out.println("after re-add: " + topics);

        System.out.println("addAll changed: "
                + topics.addAll(List.of("http", "git", "sql", "docker")));
        System.out.println("after addAll: " + topics);

        Iterator<String> iterator = topics.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().equals("git")) {
                iterator.remove();
            }
        }
        System.out.println("after iterator removal: " + topics);

        Set<String> nullable =
                new LinkedHashSet<>(Arrays.asList("a", null, "b"));
        System.out.println("nullable: " + nullable);
        System.out.println("add null again: " + nullable.add(null));
        System.out.println("after duplicate null: " + nullable);
        nullable.remove(null);
        nullable.add(null);
        System.out.println("after null re-add: " + nullable);
    }
}
```

Biên dịch theo mức Java 17 và chạy:

```shell
javac --release 17 -encoding UTF-8 LinkedHashSetOrderDemo.java
java LinkedHashSetOrderDemo
```

Stdout:

```text
initial: [sql, java, http]
contains java: true
after contains: [sql, java, http]
add equal sql: false
after duplicate: [sql, java, http]
remove java: true
after removal: [sql, http]
add java again: true
after re-add: [sql, http, java]
addAll changed: true
after addAll: [sql, http, java, git, docker]
after iterator removal: [sql, http, java, docker]
nullable: [a, null, b]
add null again: false
after duplicate null: [a, null, b]
after null re-add: [a, b, null]
```

Các điểm cần tự giải thích từ kết quả:

1. Ban đầu `sql` đứng trước `java` dù đó không phải thứ tự chữ cái.
2. `contains("java")` chỉ trả lời câu hỏi thuộc tập; nó không đổi trật tự.
3. Thử thêm một chuỗi bằng `"sql"` không tạo phần tử mới và không đẩy `sql` về cuối.
4. Sau khi xóa `java`, việc thêm lại nó đưa nó sau `http`.
5. `addAll` giữ vị trí của phần tử cũ, chỉ nối thêm `git` và `docker`.
6. Xóa `git` bằng `Iterator.remove` không thay đổi thứ tự tương đối của các phần tử còn lại.
7. `null` được xử lý như một giá trị được triển khai này chấp nhận, vẫn chỉ xuất hiện một lần.

Ví dụ dùng `Set<String>` ở kiểu khai báo và `LinkedHashSet` ở đối tượng thực tế. Kiểu biến là `Set` không làm mất hành vi của đối tượng thực tế. Tuy nhiên, nếu một API chỉ hứa trả về một `Set` bất kỳ mà không hứa thứ tự, bên gọi không nên tự giả định mọi triển khai thay thế sau này đều giữ thứ tự chèn.

## 5. Loại trùng ổn định từ một nguồn có thứ tự

Một cách dùng thường gặp:

```java
// Đoạn minh họa.
List<String> input = List.of("sql", "java", "sql", "http", "java");
Set<String> distinct = new LinkedHashSet<>(input);
```

Thứ tự của `distinct` là:

```text
[sql, java, http]
```

Ý nghĩa là:

- Lần đầu gặp `sql`: thêm vào.
- Lần đầu gặp `java`: thêm vào sau `sql`.
- Gặp lại `sql`: không đổi.
- Lần đầu gặp `http`: thêm ở cuối.
- Gặp lại `java`: không đổi.

Đây là loại trùng nhưng giữ thứ tự lần xuất hiện đầu tiên. Danh sách `input` không bị thay đổi cấu trúc bởi việc tạo tập mới.

Nếu cần một danh sách kết quả sau khi loại trùng:

```java
List<String> result = new ArrayList<>(distinct);
```

Danh sách được xây dựng theo thứ tự duyệt của tập tại thời điểm sao chép. Từ đó, danh sách có thể phục vụ một API yêu cầu dãy hoặc cần truy cập theo chỉ số.

Không tạo `HashSet` trước rồi mới chuyển sang `LinkedHashSet` nếu yêu cầu là giữ thứ tự gốc của danh sách. Bước đầu đã không cam kết giữ thứ tự đó:

```java
// Không dùng chuỗi chuyển đổi này để bảo đảm thứ tự gốc của input.
Set<String> intermediate = new HashSet<>(input);
Set<String> tooLate = new LinkedHashSet<>(intermediate);
```

`tooLate` sẽ giữ thứ tự mà nó nhận từ `intermediate`, không khôi phục thứ tự lần xuất hiện đầu tiên trong danh sách ban đầu.

Tương tự, sao chép một `HashSet` thành `LinkedHashSet` giúp giữ trình tự duyệt đã quan sát ở lần sao chép đó cho bản sao. Nó không biến thứ tự không được cam kết của nguồn thành lịch sử chèn có thể suy ra cho mọi lần chạy.

## 6. Phần tử nào được giữ khi gặp một giá trị bằng nhau?

Loại trùng dựa trên `equals` và `hashCode`, không dựa trên cách phần tử in ra hoặc việc hai tham chiếu có giống nhau không.

Khi `add(candidate)` trả về `false` vì đã có phần tử bằng nó, tập giữ nguyên. Không hiểu thao tác đó là “cập nhật đối tượng đang lưu bằng đối tượng mới”.

Ví dụ, giả sử một lớp thẻ định nghĩa bằng nhau chỉ theo mã. Hai đối tượng có cùng mã nhưng khác nhãn hiển thị được xem là bằng nhau theo quy tắc của lớp. Thêm đối tượng thứ hai vào tập đã có đối tượng thứ nhất không phải lệnh cập nhật nhãn.

Có ba yêu cầu khác nhau cần phân biệt:

| Yêu cầu | Điều cần thiết kế |
| --- | --- |
| Giữ đại diện được gặp đầu tiên | Cách loại trùng bằng LinkedHashSet từ nguồn có thứ tự phù hợp |
| Giữ dữ liệu mới nhất theo một mã | Cần quy tắc cập nhật/ghi đè theo khóa, thường hợp với Map |
| Giữ vị trí đầu tiên nhưng cập nhật một số dữ liệu | Cần thiết kế riêng cả nhận diện, dữ liệu cập nhật và thứ tự |

Không sửa các trường tham gia `equals` hoặc `hashCode` khi đối tượng đang ở trong tập. `LinkedHashSet` vẫn dựa vào băm; có chuỗi liên kết phục vụ thứ tự không làm khóa thay đổi trở nên an toàn.

Nếu lớp phần tử dùng bằng nhau theo tham chiếu, hai đối tượng có nội dung giống nhau vẫn có thể cùng được lưu. Muốn loại trùng theo giá trị, phải thiết kế quy tắc giá trị trước. Không có một chế độ “tự hiểu các trường giống nhau” chỉ nhờ chọn `LinkedHashSet`. [Set: hợp đồng phần tử và dữ liệu có thể sửa đổi](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

## 7. Thứ tự chèn không trở thành tiêu chí equals của Set

Xét hai tập:

```text
first:  [sql, java, http]
second: [http, java, sql]
```

Chúng có cùng các phần tử, nên có thể bằng nhau theo `Set.equals` dù trình tự duyệt khác nhau.

`LinkedHashSet` giữ thứ tự để duyệt; nó vẫn tuân thủ ý nghĩa bằng nhau của tập hợp. Mã băm của tập cũng không đưa thứ tự duyệt vào tiêu chí phân biệt: hai tập bằng nhau phải có cùng mã băm. [Set.equals và hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

Nếu bài toán cần so sánh hai thứ tự hiển thị, có thể so sánh các danh sách được tạo từ thứ tự duyệt của chúng:

```java
// Đoạn minh họa: first và second là hai LinkedHashSet.
boolean sameMembers = first.equals(second);
boolean sameSequence =
        new ArrayList<>(first).equals(new ArrayList<>(second));
```

Hai câu hỏi khác nhau:

- `sameMembers`: hai tập có cùng thành viên không?
- `sameSequence`: hai trình tự duyệt hiện tại có giống nhau không?

Không dùng `Set.equals` để kiểm tra thứ tự, rồi kết luận phương thức bị lỗi khi nó trả về `true` cho hai trình tự khác nhau.

## 8. Giữ thứ tự qua sao chép, khung nhìn và Stream

### 8.1. Bản sao cấu trúc bằng LinkedHashSet

```java
Set<String> copy = new LinkedHashSet<>(source);
```

Bản sao tiếp nhận các phần tử theo thứ tự duyệt của `source` tại lúc tạo. Sau đó, thêm hoặc xóa trong `source` không tự thêm hoặc xóa thành viên trong `copy`.

Đây là bản sao cấu trúc tập, không phải bản sao sâu của từng phần tử. Hai tập vẫn có thể chứa tham chiếu đến cùng đối tượng phần tử.

Nếu nguồn có thứ tự được cam kết, chẳng hạn một `List` hoặc `LinkedHashSet`, có thể suy luận thứ tự sao chép từ cam kết đó. Nếu nguồn không có cam kết, bản sao chỉ lưu thứ tự đã tiếp nhận, không tự tạo ra bảo đảm về thứ tự nguồn.

### 8.2. Chỉ đọc không có nghĩa là bản sao

```java
Set<String> view = Collections.unmodifiableSet(source);
```

Đây là một **khung nhìn không cho sửa đổi** qua tham chiếu `view`. Nó vẫn đọc dữ liệu từ `source`. Khi nơi khác sửa tập gốc, thay đổi có thể được quan sát qua khung nhìn.

Nếu `source` là một `LinkedHashSet`, xóa rồi thêm lại một phần tử trong `source` có thể làm thứ tự quan sát qua `view` thay đổi, dù bản thân `view` không cho gọi sửa đổi.

Khi yêu cầu là một bản chụp riêng, giữ thứ tự hiện tại và không cho bên nhận sửa cấu trúc, có thể sao chép rồi mới bọc:

```java
Set<String> snapshot =
        Collections.unmodifiableSet(new LinkedHashSet<>(source));
```

Cách này tách cấu trúc tập của bản chụp khỏi tập nguồn. Các đối tượng phần tử vẫn cần được xét riêng nếu chúng có thể sửa đổi. [Collections.unmodifiableSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableSet(java.util.Set))

### 8.3. Set.copyOf không phải cách hứa giữ thứ tự chèn

`Set.copyOf(source)` tạo một tập không sửa đổi được theo hợp đồng của nó, nhưng các tập do những phương thức factory tương ứng tạo ra không cam kết thứ tự duyệt theo thứ tự chèn của nguồn.

Vì vậy, không thay biểu thức sao chép có yêu cầu thứ tự bằng `Set.copyOf` rồi mặc định output phải giữ nguyên trình tự. Tính không sửa đổi được và thứ tự duyệt là hai thuộc tính khác nhau. [Set: các tập không sửa đổi được và thứ tự duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

### 8.4. Dùng collector chỉ rõ LinkedHashSet khi cần kết quả đó

Với một danh sách có thứ tự:

```java
Set<String> distinct = input.stream()
        .collect(Collectors.toCollection(LinkedHashSet::new));
```

`toCollection` tích lũy theo thứ tự gặp của nguồn và dùng factory được chỉ định để tạo tập kết quả. Kết hợp với `LinkedHashSet`, cách viết này phù hợp với yêu cầu loại trùng giữ thứ tự gặp đầu tiên.

Không dùng `Collectors.toSet()` rồi giả định kết quả luôn là `LinkedHashSet` hoặc luôn giữ thứ tự. Collector đó không cam kết kiểu tập cụ thể và là collector không yêu cầu giữ thứ tự.

Nguồn cũng phải có thứ tự gặp có ý nghĩa nếu muốn hứa một thứ tự kết quả tương ứng. Chọn kiểu tập ở cuối không khôi phục một thứ tự đã bị bỏ đi trước đó. [Collectors.toCollection và toSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html)

## 9. Chương trình đầy đủ: loại trùng, so sánh và tạo bản chụp

Lưu mã vào **LinkedHashSetCopyDemo.java**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LinkedHashSetCopyDemo {
    public static void main(String[] args) {
        List<String> input = List.of("sql", "java", "sql", "http", "java");
        Set<String> distinct = new LinkedHashSet<>(input);
        Set<String> viaStream = input.stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        System.out.println("input: " + input);
        System.out.println("distinct: " + distinct);
        System.out.println("distinct as list: " + new ArrayList<>(distinct));
        System.out.println("via stream: " + viaStream);

        Set<String> otherOrder =
                new LinkedHashSet<>(List.of("http", "java", "sql"));
        System.out.println("other order: " + otherOrder);
        System.out.println("set equality: " + distinct.equals(otherOrder));
        System.out.println("hash equality: "
                + (distinct.hashCode() == otherOrder.hashCode()));
        System.out.println("iteration lists equal: "
                + new ArrayList<>(distinct).equals(new ArrayList<>(otherOrder)));

        Set<String> source =
                new LinkedHashSet<>(List.of("first", "second"));
        Set<String> view = Collections.unmodifiableSet(source);
        Set<String> snapshot =
                Collections.unmodifiableSet(new LinkedHashSet<>(source));

        source.remove("first");
        source.add("first");
        System.out.println("source after reorder: " + source);
        System.out.println("read-only view: " + view);
        System.out.println("copied snapshot: " + snapshot);
        try {
            view.add("third");
        } catch (UnsupportedOperationException ex) {
            System.out.println("view mutation: UnsupportedOperationException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 LinkedHashSetCopyDemo.java
java LinkedHashSetCopyDemo
```

Stdout:

```text
input: [sql, java, sql, http, java]
distinct: [sql, java, http]
distinct as list: [sql, java, http]
via stream: [sql, java, http]
other order: [http, java, sql]
set equality: true
hash equality: true
iteration lists equal: false
source after reorder: [second, first]
read-only view: [second, first]
copied snapshot: [first, second]
view mutation: UnsupportedOperationException
```

Cách đọc kết quả:

1. Đầu vào vẫn giữ các phần tử lặp; việc tạo tập mới không sửa danh sách gốc.
2. Tập và danh sách được sao chép từ tập giữ thứ tự lần xuất hiện đầu tiên: `sql`, `java`, `http`.
3. Collector chỉ rõ `LinkedHashSet` tạo cùng trình tự cho danh sách đầu vào có thứ tự.
4. Hai tập có cùng thành viên nhưng thứ tự khác nhau vẫn bằng nhau và cùng mã băm.
5. Hai danh sách tạo từ hai thứ tự duyệt không bằng nhau, vì danh sách xét vị trí.
6. Sau khi tập gốc xóa rồi thêm lại `first`, khung nhìn chỉ đọc phản ánh thứ tự mới.
7. Bản chụp đã sao chép trước đó vẫn giữ thứ tự `first`, `second`.
8. Thử sửa qua `view` bị từ chối bằng `UnsupportedOperationException`.

Hai chương trình trong bài đã được biên dịch bằng `--release 17` và chạy để đối chiếu đúng stdout. Tùy chọn này kiểm tra mức ngôn ngữ/API và tạo bytecode tương thích Java 17; nó không khẳng định JVM thực thi chính là JDK 17.

## 10. Hiệu năng và chi phí của việc duy trì thứ tự

`LinkedHashSet` vẫn dùng băm cho các thao tác `add`, `contains`, `remove`. Thời gian hằng số kỳ vọng phụ thuộc vào giả định mã băm phân bố phần tử phù hợp và chi phí nhận diện phần tử được kiểm soát.

Nó duy trì thêm liên kết phục vụ thứ tự, nên có chi phí bộ nhớ và cập nhật liên kết. Không có căn cứ để nói nó luôn nhanh hơn `HashSet` chỉ vì thứ tự dễ đoán.

Một điểm khác biệt hữu ích là duyệt toàn bộ:

| Cấu trúc | Thứ tự duyệt | Công việc duyệt được tài liệu mô tả |
| --- | --- | --- |
| `HashSet` | Không cam kết | Tỷ lệ với size cộng capacity của bảng nền |
| `LinkedHashSet` | Theo thứ tự chèn | Tỷ lệ với số phần tử, không phụ thuộc capacity theo mô tả của lớp |

Điều này không làm capacity hoặc load factor trở nên vô nghĩa với `LinkedHashSet`. Chúng vẫn ảnh hưởng cấu trúc băm và tài nguyên dùng để lưu. Chỉ riêng việc đi qua chuỗi phần tử có mô hình chi phí khác. [LinkedHashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html), [HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)

Không dùng số lần in output hoặc thứ tự in làm thước đo tốc độ tìm kiếm. Muốn đánh giá hiệu năng thực tế cần xét số phần tử, số lượt thêm/xóa/tra cứu/duyệt và kiểu khóa.

## 11. Chọn cấu trúc theo ý nghĩa thứ tự

| Yêu cầu chính | Cấu trúc hoặc hướng phù hợp |
| --- | --- |
| Loại trùng, không có yêu cầu thứ tự duyệt | Có thể dùng `HashSet` |
| Loại trùng, giữ thứ tự gặp đầu tiên từ nguồn có thứ tự | `LinkedHashSet` |
| Duyệt theo thứ tự sắp xếp của giá trị | Cân nhắc `TreeSet` với quy tắc so sánh phù hợp |
| Giữ cả phần tử lặp và vị trí | `List` |
| Lưu dữ liệu mới nhất theo mã, kèm yêu cầu thứ tự | Thiết kế bằng `Map` và quy tắc cập nhật/thứ tự rõ ràng |

`TreeSet` dùng thứ tự tự nhiên hoặc bộ so sánh, không dùng thứ tự chèn để quyết định trình tự duyệt. Đồng thời, quy tắc so sánh cần được thiết kế phù hợp với ý nghĩa bằng nhau của phần tử. [TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)

Một vài tình huống phù hợp với `LinkedHashSet`:

- Tập thẻ được chọn cần loại trùng nhưng hiển thị theo trình tự chọn.
- Các tùy chọn cấu hình được hợp nhất theo thứ tự đọc, giữ vị trí lần đầu gặp.
- Danh sách mã nhập từ tệp cần loại trùng mà không tự sắp xếp lại.
- Kết quả của một bước xử lý cần giữ thứ tự đã tiếp nhận để dễ đối chiếu với nguồn.

Nếu thứ tự gốc không có ý nghĩa hoặc đã không xác định từ đầu, việc dùng `LinkedHashSet` không tự tạo ra một thứ tự nghiệp vụ đúng. Ví dụ, nó không thể biết thứ tự ưu tiên, thời điểm tạo hay thứ tự bảng chữ cái nếu các thông tin đó không được thể hiện trong nguồn hoặc quy tắc xử lý.

## 12. Duyệt và sửa đổi đúng cách

`LinkedHashSet` không tự đồng bộ cho nhiều luồng cùng sửa. Thứ tự chèn dễ dự đoán không đồng nghĩa các thao tác đồng thời đã có một thứ tự nghiệp vụ được bảo đảm.

Trong một luồng, khi cần xóa phần tử ngay trong lúc duyệt, dùng cơ chế phù hợp như `Iterator.remove`, như chương trình đầu đã làm.

Không dùng cách sau để xóa qua đường khác với bộ duyệt đang hoạt động:

```java
// Đoạn minh họa cách cần tránh, không phải mẫu xóa đúng.
for (String topic : topics) {
    if (topic.equals("git")) {
        topics.remove(topic);
    }
}
```

Bộ duyệt có thể phát hiện sửa đổi cấu trúc không hợp lệ và ném `ConcurrentModificationException`. Cơ chế fail-fast chỉ có tính nỗ lực tốt nhất; không thấy ngoại lệ trong một lần chạy không chứng minh cách duyệt là đúng.

Xóa rồi thêm một phần tử để đổi vị trí cũng là sửa cấu trúc. Không thực hiện thao tác đó tùy tiện trong một vòng duyệt hiện tại rồi mong bộ duyệt tự điều chỉnh cho đúng yêu cầu. [LinkedHashSet: đồng bộ và fail-fast](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)

## 13. Các lỗi thường gặp

| Cách hiểu hoặc cách viết | Vấn đề | Cách suy nghĩ đúng |
| --- | --- | --- |
| Gọi add phần tử đang có để đẩy nó về cuối | Thêm trùng không thay đổi thứ tự | Phân biệt thêm lại khi đang có với xóa rồi thêm mới |
| Gọi contains rồi mong phần tử trở thành mới nhất | Truy vấn không đổi thứ tự chèn | Đây không phải thứ tự theo lần truy cập |
| Nghĩ LinkedHashSet tự sắp xếp | Nó giữ lịch sử thêm mới | Muốn thứ tự giá trị cần quy tắc sắp xếp riêng |
| Tạo HashSet trước rồi mới chuyển sang LinkedHashSet | Thứ tự gốc có thể đã mất cam kết | Tạo LinkedHashSet trực tiếp từ nguồn có thứ tự |
| Dùng equals để so thứ tự hiển thị | equals của Set bỏ qua thứ tự | So một biểu diễn dãy khi cần thứ tự |
| Dùng Set.copyOf để hứa giữ thứ tự chèn | Factory đó không có bảo đảm thứ tự tương ứng | Chọn cách sao chép đáp ứng cả quyền sửa và thứ tự |
| Cho rằng unmodifiableSet là bản sao | Nó là khung nhìn đọc qua tập gốc | Sao chép trước khi bọc nếu cần bản chụp |
| Dùng Collectors.toSet và giả định LinkedHashSet | Collector không hứa loại tập đó | Chỉ rõ toCollection khi cần |
| Thêm đối tượng bằng giá trị để cập nhật dữ liệu đang lưu | add trùng giữ tập nguyên trạng | Thiết kế cập nhật theo khóa phù hợp |
| Sửa trường định danh khi phần tử đang trong tập | Vẫn có rủi ro của khóa dùng băm | Giữ equals/hashCode ổn định |
| Dựa vào không có ngoại lệ khi vừa duyệt vừa sửa trực tiếp | Fail-fast không phải bằng chứng tính đúng | Dùng cơ chế xóa khi duyệt được hỗ trợ |

Khi kết quả sai thứ tự mong muốn, hãy kiểm tra từ đầu nguồn: nguồn có cam kết thứ tự không, đã đi qua một bước làm mất cam kết đó chưa, phần tử có thực sự mới hay chỉ bằng phần tử cũ, và yêu cầu cuối cùng là so thành viên hay so trình tự.

## 14. Tự luyện và đối chiếu

### Bài 1: theo dõi thứ tự

Khởi tạo:

```java
Set<String> values =
        new LinkedHashSet<>(List.of("p", "q", "p", "r"));
```

Thực hiện lần lượt:

```java
values.add("q");
values.contains("p");
values.remove("p");
values.add("p");
values.addAll(List.of("r", "s", "q"));
```

Đối chiếu:

| Bước | Kết quả thao tác | Thứ tự sau bước |
| --- | --- | --- |
| Khởi tạo | Không phải biểu thức boolean cần xét | `[p, q, r]` |
| `add("q")` | `false` | `[p, q, r]` |
| `contains("p")` | `true` | `[p, q, r]` |
| `remove("p")` | `true` | `[q, r]` |
| `add("p")` | `true` | `[q, r, p]` |
| `addAll(...)` | `true` | `[q, r, p, s]` |

Giải thích vì sao hai lời gọi `add` cho `q` và `p` có tác động thứ tự khác nhau ở thời điểm tương ứng.

### Bài 2: cùng thành viên hay cùng trình tự?

Cho:

```java
Set<Integer> a = new LinkedHashSet<>(List.of(3, 1, 2));
Set<Integer> b = new LinkedHashSet<>(List.of(1, 2, 3));
```

Dự đoán:

- `a.equals(b)`: `true`.
- `a.hashCode() == b.hashCode()`: `true`.
- `new ArrayList<>(a).equals(new ArrayList<>(b))`: `false`.

Một lời giải đúng cần phân biệt hợp đồng của tập với phép so sánh dãy. Không dùng việc “đã có thứ tự” để kết luận equals của tập phải xét thứ tự.

### Bài 3: chọn đúng kiểu sao chép

Một thành phần nhận `LinkedHashSet<String> source` và cần trả kết quả không cho bên nhận sửa cấu trúc, giữ nguyên thứ tự tại thời điểm tạo, đồng thời không phản ánh việc nơi khác thêm/xóa vào `source`.

Chọn giữa:

```java
Collections.unmodifiableSet(source);
Collections.unmodifiableSet(new LinkedHashSet<>(source));
Set.copyOf(source);
```

Cách thứ hai đáp ứng đầy đủ các yêu cầu đã nêu cho cấu trúc và thứ tự. Cách thứ nhất còn đọc qua nguồn. Cách thứ ba không cung cấp cam kết giữ thứ tự chèn như yêu cầu.

Nếu đổi phần tử từ `String` sang một lớp có thể sửa đổi, cần xét thêm tính ổn định và quyền sửa dữ liệu của từng đối tượng phần tử; một bản sao tập không tự là bản sao sâu.

### Bài 4: nguồn không có cam kết thứ tự

Một `HashSet` được truyền vào `new LinkedHashSet<>(source)`. Có thể kết luận bản sao:

1. Giữ thứ tự duyệt mà nó nhận ở lần sao chép đó.
2. Khôi phục được thứ tự người dùng đã thêm vào HashSet từ trước.
3. Luôn giống thứ tự sao chép trên mọi lần chạy ứng dụng.

Chỉ kết luận thứ nhất phù hợp. Hai kết luận sau đòi hỏi thông tin hoặc bảo đảm mà nguồn không cung cấp.

## 15. Tài liệu tra cứu

- [LinkedHashSet Java 17 — thứ tự chèn, thêm lại, hiệu năng và bộ duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)
- [HashSet Java 17 — thứ tự không được cam kết và cơ chế dùng băm](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)
- [Set — hợp đồng bằng nhau và các tập không sửa đổi được](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)
- [Collections.unmodifiableSet — khung nhìn không cho sửa đổi](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableSet(java.util.Set))
- [Collectors — chỉ rõ kiểu tập kết quả bằng toCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html)
- [TreeSet — thứ tự tự nhiên hoặc Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-based-set-006">Thêm lại phần tử có sẵn và thứ tự của LinkedHashSet</a>

<details>
<summary>Xem chi tiết</summary>

# Thêm lại phần tử có sẵn và thứ tự của LinkedHashSet

## 1. Vấn đề cần giải quyết: giữ thứ tự nào khi dữ liệu xuất hiện nhiều lần?

Giả sử chương trình ghi nhận các giai đoạn công việc theo thứ tự `draft → review → done`. Một sự kiện `review` lại được gửi đến. Bạn muốn loại bản trùng, nhưng thứ tự kết quả nên là `draft → review → done` hay `draft → done → review`?

Hai kết quả phục vụ hai yêu cầu khác nhau. Kết quả thứ nhất giữ vị trí của lần ghi nhận đầu tiên khi phần tử vẫn còn trong tập. Kết quả thứ hai phản ánh một lần xuất hiện gần đây hơn. Chỉ biết “tập không chứa phần tử trùng” chưa đủ để phân biệt chúng.

`LinkedHashSet` phù hợp khi bạn cần tập hợp không trùng cùng một thứ tự duyệt xác định. Với phương thức `add` thông thường, thêm một phần tử đã có **không đưa phần tử đó về cuối**. Lần gọi trả về `false` và vị trí cũ được giữ lại. Xóa phần tử rồi thêm lại là một chuỗi thao tác khác: phần tử thực sự rời tập, sau đó được thêm mới ở cuối.

Bài học tập trung vào ranh giới này: `add` trùng, xóa rồi thêm, thêm theo lô và đối tượng tương đương. Các ví dụ chính chỉ dùng API có trong Java 17. Phần cuối nêu riêng các phương thức định vị xuất hiện từ Java 21 để tránh áp dụng nhầm quy tắc của `add` cho mọi phương thức.

Bạn cần biết cách khai báo biến, gọi phương thức, dùng `List` và vòng lặp. Khái niệm `equals`, `hashCode`, thứ tự duyệt và ý nghĩa giá trị trả về sẽ được nhắc lại tại chỗ.

## 2. Ba câu hỏi phải tách riêng

Đứng trước một lần gọi `set.add(element)`, hãy hỏi lần lượt:

1. Trước lời gọi, tập đã có phần tử tương đương hay chưa?
2. Sau lời gọi, tập có thêm một thành viên không?
3. Thứ tự các thành viên khi duyệt thay đổi thế nào?

Câu thứ nhất quyết định đây là thêm mới hay thêm trùng. Câu thứ hai được phản ánh bằng giá trị `boolean` trả về. Câu thứ ba liên quan đến cam kết thứ tự của cách triển khai cụ thể.

Những thuật ngữ chính:

| Thuật ngữ | Ý nghĩa trong bài |
| --- | --- |
| Thành viên | Một phần tử hiện thuộc tập |
| Phần tử tương đương | Hai đối tượng được xem là cùng giá trị theo quy tắc bằng nhau của tập |
| Thứ tự duyệt | Trình tự nhận phần tử qua `iterator` hoặc vòng lặp `for-each` |
| Thứ tự chèn | Thứ tự những lần thêm mới thành công, có tính đến việc phần tử đã bị xóa |
| Đối tượng đại diện | Đối tượng thực tế đang được lưu cho một giá trị trong tập |
| Thứ tự truy cập | Thứ tự dựa vào các lần sử dụng gần đây; khác yêu cầu giữ vị trí khi thêm mới |

Một tập có thể có cùng các thành viên với tập khác mà duyệt ra thứ tự khác. Một đối tượng mới được tạo cũng có thể tương đương với đối tượng cũ. Vì vậy, không suy thứ tự từ số lượng phần tử và không suy “mới đối với tập” từ toán tử `new`.

## 3. Quy tắc của add trong LinkedHashSet

Với dữ liệu có quy tắc `equals/hashCode` hợp lệ và không bị sửa sai trong thời gian lưu, có thể đọc hành vi của `add` như sau:

| Trước `add(e)` | Giá trị trả về | Số phần tử | Thứ tự duyệt |
| --- | --- | --- | --- |
| Chưa có phần tử tương đương với `e` | `true` | Tăng một | `e` được thêm ở cuối |
| Đã có phần tử tương đương với `e` | `false` | Không đổi | Vị trí hiện có được giữ nguyên |

Tài liệu `LinkedHashSet` quy định rõ việc gọi lại `add` khi phần tử đã có không làm đổi thứ tự chèn. Đây là cam kết API, không phải kết quả tình cờ của một lần chạy. [Java 17: LinkedHashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html).

Đoạn minh họa dưới đây giả sử đã nhập `java.util.LinkedHashSet` và `java.util.List`:

~~~java
LinkedHashSet<String> stages =
        new LinkedHashSet<>(List.of("draft", "review", "done"));

boolean changed = stages.add("review");
System.out.println(changed);
System.out.println(stages);
~~~

Kết quả:

~~~text
false
[draft, review, done]
~~~

Không có vị trí mới cho `review`. Cũng không có bản `review` thứ hai bị ẩn ở cuối. Tập vẫn chứa đúng ba phần tử.

`false` không có nghĩa là “phần tử vắng mặt sau lời gọi”. Trong ví dụ, `review` đã có trước và vẫn có sau lời gọi. Nó biểu thị rằng lời gọi `add` không làm tập nhận thêm thành viên. Đừng dùng `if (!set.add(x))` như bằng chứng rằng `x` bị thất lạc.

Với `contains("draft")`, chương trình chỉ hỏi về sự hiện diện. Lời gọi không chuyển `draft` ra sau các phần tử khác. Do đó, cả việc thấy lại một giá trị qua `add` lẫn việc kiểm tra qua `contains` đều không biến tập thành danh sách “vừa dùng gần đây”.

## 4. Vì sao bảng băm vẫn đi cùng thứ tự chèn?

Tên `LinkedHashSet` biểu thị hai vai trò phối hợp:

- Phần băm hỗ trợ tìm xem giá trị đã có hay chưa.
- Các liên kết giữa phần tử hỗ trợ lần lượt duyệt theo thứ tự được duy trì.

Có thể hình dung dữ liệu hiện tại bằng sơ đồ đơn giản:

~~~text
Thành viên theo quy tắc bằng nhau: {draft, review, done}

Chuỗi phục vụ duyệt:
đầu → draft ⇄ review ⇄ done ← cuối
~~~

Sơ đồ không mô tả vị trí vật lý trong bộ nhớ hoặc số ngăn của bảng băm. Nó giúp phân biệt thao tác tìm thành viên với thao tác thay đổi chuỗi thứ tự.

Khi thêm một giá trị mới, tập tạo thêm sự hiện diện và nối nó vào cuối chuỗi. Khi giá trị đã tồn tại, không cần tạo một thành viên thứ hai để nối thêm. Việc tìm được thành viên cũ không tự mang ý nghĩa “chuyển thành viên này đến cuối”.

Trong OpenJDK 17, `HashSet` có một bảng ánh xạ làm nơi lưu phần tử; `LinkedHashSet` dùng biến thể dựa trên `LinkedHashMap` theo thứ tự chèn. Các phần tử của tập được lưu ở phía khóa. Vì vậy, hành vi thêm trùng có liên hệ với việc tìm thấy một khóa cũ, không phải xóa khóa rồi tạo lại. Đây là mô tả cách triển khai giúp giải thích kết quả; ứng dụng vẫn nên dựa vào hợp đồng API. [Mã nguồn HashSet, OpenJDK 17](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/util/HashSet.java).

Không cần biết công thức chọn ngăn băm để dự đoán ví dụ `draft/review/done`. Thứ tự chèn của `LinkedHashSet` được cam kết ở cấp API. Thay đổi dung lượng bảng hoặc có va chạm băm không phải lý do để ứng dụng chấp nhận thứ tự duyệt tùy ý.

## 5. Xóa rồi thêm lại tạo vị trí mới

So sánh hai chuỗi thao tác trên trạng thái ban đầu `[draft, review, done]`.

Trường hợp A:

~~~text
add(review) → false
kết quả     → [draft, review, done]
~~~

Trường hợp B:

~~~text
remove(review) → true
trạng thái     → [draft, done]
add(review)    → true
kết quả        → [draft, done, review]
~~~

Ở trường hợp B, khi gọi `add`, tập không còn `review`. Lời gọi đó là thêm mới đối với trạng thái hiện tại, dù chương trình từng chứa cùng giá trị trong quá khứ.

Cách hiểu “thứ tự lần đầu xuất hiện” cần có giới hạn này. `LinkedHashSet` không lưu vĩnh viễn vị trí lịch sử của những phần tử đã xóa. Nếu xóa `review` rồi thêm lại, tập không tự đưa nó về giữa `draft` và `done`.

Cũng cần kiểm tra kết quả `remove`. Nếu xóa một phần tử chưa có, `remove` trả `false`. Sau đó `add` có thể tạo một thành viên mới, làm tăng kích thước. Ví dụ, từ `[draft, done]`, thực hiện `remove("archive")` rồi `add("archive")` sẽ cho `[draft, done, archive]`. Đây không còn là “chỉ chuyển phần tử đang có”.

Nếu gọi `clear`, mọi thành viên đều bị loại khỏi tập. Những lần `add` sau đó thiết lập thứ tự mới theo dữ liệu được đưa vào, không phục hồi thứ tự trước `clear`.

Trong yêu cầu nghiệp vụ, hãy chọn rõ một trong hai ý:

- “Đã có thì giữ nguyên vị trí”: chỉ cần `add`.
- “Nếu đang có thì đưa về cuối”: cần thao tác định vị phù hợp với phiên bản Java, hoặc một quy trình xóa/thêm được thiết kế rõ.

## 6. addAll không đưa các phần tử cũ về cuối

Giả sử trạng thái hiện tại là:

~~~text
[draft, review, done]
~~~

Lô tiếp theo có thứ tự:

~~~text
[review, publish, draft, archive]
~~~

Trong lô này, `review` và `draft` đã có. Chúng giữ vị trí cũ. `publish` và `archive` chưa có nên được nối thêm theo thứ tự chúng được gặp trong lô:

~~~text
[draft, review, done, publish, archive]
~~~

`addAll` trả `true` vì có ít nhất một thành viên mới. Giá trị đó không cho biết “mọi phần tử trong lô đều mới” hay “tập đã được sắp lại giống lô đầu vào”. Cách triển khai `addAll` được kế thừa từ `AbstractCollection` duyệt nguồn và gọi `add` cho các phần tử. [Java 17: AbstractCollection.addAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractCollection.html#addAll(java.util.Collection)).

Nếu cả lô chỉ gồm những phần tử đã có, tập không nhận thành viên mới và thứ tự vẫn giữ nguyên. Không có cơ chế tự làm mới vị trí cho mỗi lần gặp bản trùng.

Tuy nhiên, phải biết thứ tự của nguồn. `List.of("review", "publish", "draft", "archive")` cho một trình tự rõ ràng. Nếu nguồn là `HashSet`, thứ tự duyệt của nguồn không được cam kết. Sao chép sang `LinkedHashSet` chỉ ghi nhận trình tự mà nguồn cung cấp trong lần duyệt đó; nó không suy ra thứ tự chèn ban đầu đã mất.

Với nguồn có thứ tự rõ, bạn có thể giải thích kết quả bằng việc gạch bỏ các giá trị đã có trong tập đích, rồi nối những giá trị mới vào cuối theo trình tự nguồn. Khi cùng một giá trị mới lặp lại trong lô, chỉ lần thêm mới thành công đầu tiên tạo vị trí.

## 7. Khác tham chiếu vẫn có thể là thêm trùng

Đối với tập dùng băm, việc nhận diện phần tử cần quy tắc `equals` và `hashCode` nhất quán. Nếu hai đối tượng được xem là bằng nhau thì chúng phải có cùng mã băm. Hai đối tượng có cùng mã băm chưa chắc bằng nhau.

Hãy xét vé có `number` và `note`. Giả sử nghiệp vụ xem số vé là định danh: hai vé cùng `number` là cùng một phần tử đối với tập, bất kể `note`. Chương trình đầy đủ phía dưới tự định nghĩa `equals` và `hashCode` của `Ticket` chỉ từ `number`. Đây là lựa chọn tường minh; `record Ticket(int number, String note)` mặc định sẽ so sánh cả hai thành phần nếu không ghi đè.

Ta có hai đối tượng:

~~~text
original    = #10:old
replacement = #10:new
~~~

Hai tham chiếu khác nhau nhưng `original.equals(replacement)` bằng `true`. Khi tập đang có `original` và nhận `add(replacement)`, không có thành viên mới. Trong cách triển khai của `LinkedHashSet`, đối tượng cũ vẫn được giữ; việc gọi `add` không cập nhật trường `note` của nó.

Nếu tập hiện là:

~~~text
[#10:old, #20:other]
~~~

thì thêm `#10:new` không tạo kết quả `[#10:new, #20:other]` và cũng không tạo `[#20:other, #10:new]`. Kết quả vẫn là:

~~~text
[#10:old, #20:other]
~~~

Muốn thay đại diện bằng đối tượng mới qua xóa/thêm, bạn phải chấp nhận cả hệ quả về thứ tự:

~~~text
remove(#10:new) → xóa phần tử tương đương #10:old
add(#10:new)    → thêm đối tượng mới ở cuối
kết quả        → [#20:other, #10:new]
~~~

Vì `remove` cũng tìm theo quy tắc bằng nhau, đối tượng dùng để tìm không nhất thiết là chính tham chiếu đang được lưu.

Nếu yêu cầu là “giữ vị trí ban đầu nhưng luôn cập nhật thông tin theo mã vé”, đừng kỳ vọng `Set.add` làm việc thay dữ liệu. Một `LinkedHashMap<Integer, Ticket>` với số vé làm khóa có thể diễn đạt yêu cầu cập nhật giá trị rõ hơn: ở chế độ thứ tự chèn, cập nhật giá trị cho khóa đang có không tự chuyển vị trí khóa. Đây là thiết kế khác với xóa rồi thêm một phần tử của tập. [Java 17: LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

Để các suy luận trên đúng, đừng thay đổi những thuộc tính tham gia `equals/hashCode` khi đối tượng còn trong tập. Nếu mã băm hoặc quan hệ bằng nhau đổi trong thời gian lưu, việc tìm/xóa có thể sai với điều bạn tưởng. Bài học dùng thành phần bất biến của `record` và mã vé cố định để tách vấn đề đó khỏi quy tắc thứ tự.

## 8. null cũng có vị trí, nhưng chỉ một thành viên

`LinkedHashSet` cho phép `null`. Từ trạng thái `[start, null, end]`:

- `add(null)` trả `false` và giữ `[start, null, end]`.
- Xóa `null` rồi thêm `null` lại cho `[start, end, null]`.

Không có quy tắc riêng “`null` luôn ở đầu” hay “`null` luôn ở cuối”. Vị trí quan sát được đến từ quá trình đưa nó vào tập và các thao tác sau đó.

`null` là giá trị đặc biệt không có phương thức để gọi. Bạn không cần gọi `null.equals(...)`; cách triển khai tập xử lý trường hợp này.

Đừng dùng `List.of("start", null, "end")` để tạo đầu vào cho ví dụ vì `List.of` không cho phép `null`. Chương trình bên dưới thêm từng phần tử để kiểm chứng đúng hành vi của `LinkedHashSet` mà không bị dừng trước bởi lỗi tạo danh sách.

## 9. Chương trình đầy đủ: dự đoán rồi mới chạy

Lưu đoạn sau thành `LinkedHashSetReinsertDemo.java`. Chương trình không phụ thuộc thư viện ngoài JDK. Nó kiểm tra riêng việc thêm trùng, thêm theo lô, xóa/thêm, đại diện của đối tượng tương đương, `null`, so sánh thứ tự và bộ duyệt.

Trước khi chạy, hãy dự đoán ba điều:

1. `review` đứng ở đâu sau `add("review")` và sau xóa/thêm?
2. Vé số 10 giữ `note` nào khi thêm đối tượng tương đương?
3. Hai tập `[A, B]` và `[B, A]` có bằng nhau theo `Set.equals` không?

~~~java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

public class LinkedHashSetReinsertDemo {
    record Ticket(int number, String note) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Ticket ticket
                    && number == ticket.number;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(number);
        }

        @Override
        public String toString() {
            return "#" + number + ":" + note;
        }
    }

    static <E> boolean moveExistingToEnd(LinkedHashSet<E> set, E element) {
        if (!set.remove(element)) {
            return false;
        }
        set.add(element);
        return true;
    }

    public static void main(String[] args) {
        LinkedHashSet<String> steps =
                new LinkedHashSet<>(List.of("draft", "review", "done"));

        System.out.println("initial=" + steps);
        System.out.println("add(review)=" + steps.add("review"));
        System.out.println("after duplicate=" + steps);
        System.out.println("contains(draft)=" + steps.contains("draft"));
        System.out.println("after contains=" + steps);
        System.out.println("addAll="
                + steps.addAll(List.of("review", "publish", "draft", "archive")));
        System.out.println("after addAll=" + steps);
        System.out.println("remove(review)=" + steps.remove("review"));
        System.out.println("add(review) after remove=" + steps.add("review"));
        System.out.println("after reinsert=" + steps);
        System.out.println("remove(missing)=" + steps.remove("missing"));
        System.out.println("after missing remove=" + steps);
        System.out.println("move(draft)=" + moveExistingToEnd(steps, "draft"));
        System.out.println("after move=" + steps);
        System.out.println("move(missing)=" + moveExistingToEnd(steps, "missing"));
        System.out.println("after missing move=" + steps);

        Ticket original = new Ticket(10, "old");
        Ticket replacement = new Ticket(10, "new");
        LinkedHashSet<Ticket> tickets = new LinkedHashSet<>();
        tickets.add(original);
        tickets.add(new Ticket(20, "other"));
        System.out.println("tickets initial=" + tickets);
        System.out.println("equal ticket=" + original.equals(replacement));
        System.out.println("add equal ticket=" + tickets.add(replacement));
        System.out.println("tickets after duplicate=" + tickets);
        System.out.println("original retained=" + (tickets.iterator().next() == original));
        System.out.println("remove equal ticket=" + tickets.remove(replacement));
        System.out.println("add replacement=" + tickets.add(replacement));
        System.out.println("tickets after replace=" + tickets);
        System.out.println("replacement retained="
                + (new ArrayList<>(tickets).get(1) == replacement));

        LinkedHashSet<String> withNull = new LinkedHashSet<>();
        withNull.add("start");
        withNull.add(null);
        withNull.add("end");
        System.out.println("null initial=" + withNull);
        System.out.println("add(null)=" + withNull.add(null));
        System.out.println("null after duplicate=" + withNull);
        withNull.remove(null);
        withNull.add(null);
        System.out.println("null after reinsert=" + withNull);

        LinkedHashSet<String> forward =
                new LinkedHashSet<>(List.of("A", "B"));
        LinkedHashSet<String> backward =
                new LinkedHashSet<>(List.of("B", "A"));
        System.out.println("set equality=" + forward.equals(backward));
        System.out.println("iteration order equality="
                + new ArrayList<>(forward).equals(new ArrayList<>(backward)));

        LinkedHashSet<Integer> many = new LinkedHashSet<>(1);
        List<Integer> expected = new ArrayList<>();
        for (int value : new int[] {40, 10, 30}) {
            many.add(value);
            expected.add(value);
        }
        for (int value = 100; value < 200; value++) {
            many.add(value);
            expected.add(value);
        }
        System.out.println("many size=" + many.size());
        System.out.println("many insertion order="
                + new ArrayList<>(many).equals(expected));
        System.out.println("many add(10)=" + many.add(10));
        System.out.println("many order after duplicate="
                + new ArrayList<>(many).equals(expected));

        LinkedHashSet<String> directions =
                new LinkedHashSet<>(List.of("north", "south", "west"));
        Iterator<String> iterator = directions.iterator();
        System.out.println("iterator first=" + iterator.next());
        System.out.println("iterator duplicate=" + directions.add("south"));
        System.out.println("iterator next=" + iterator.next());
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 LinkedHashSetReinsertDemo.java
java LinkedHashSetReinsertDemo
~~~

`--release 17` yêu cầu trình biên dịch kiểm tra mã và API theo Java 17. Dùng JDK 17 trở lên cho các lệnh này. Các lớp `record` có mặt trong phạm vi phiên bản đó.

Kết quả chuẩn:

~~~text
initial=[draft, review, done]
add(review)=false
after duplicate=[draft, review, done]
contains(draft)=true
after contains=[draft, review, done]
addAll=true
after addAll=[draft, review, done, publish, archive]
remove(review)=true
add(review) after remove=true
after reinsert=[draft, done, publish, archive, review]
remove(missing)=false
after missing remove=[draft, done, publish, archive, review]
move(draft)=true
after move=[done, publish, archive, review, draft]
move(missing)=false
after missing move=[done, publish, archive, review, draft]
tickets initial=[#10:old, #20:other]
equal ticket=true
add equal ticket=false
tickets after duplicate=[#10:old, #20:other]
original retained=true
remove equal ticket=true
add replacement=true
tickets after replace=[#20:other, #10:new]
replacement retained=true
null initial=[start, null, end]
add(null)=false
null after duplicate=[start, null, end]
null after reinsert=[start, end, null]
set equality=true
iteration order equality=false
many size=103
many insertion order=true
many add(10)=false
many order after duplicate=true
iterator first=north
iterator duplicate=false
iterator next=south
~~~

## 10. Đọc kết quả theo từng thay đổi trạng thái

### 10.1. Thêm trùng và kiểm tra sự hiện diện

Hai dòng `add(review)=false` và `after duplicate=[draft, review, done]` trả lời hai câu hỏi khác nhau: không có thành viên mới và thứ tự không đổi.

Sau `contains(draft)=true`, `draft` vẫn đứng đầu. Tập đang ghi nhận thứ tự đưa phần tử vào, không ghi thời điểm từng được tra cứu.

### 10.2. Thêm theo lô

`addAll=true` xuất hiện vì `publish` và `archive` mới. `review` không bị kéo ra sau `done`; `draft` không bị kéo ra sau `publish`. Trình tự của các phần tử đã có vẫn được bảo toàn.

Điểm cần quan sát là phần đuôi mới `publish, archive`. Đó là thứ tự các giá trị mới được gặp trong lô `List` đầu vào.

### 10.3. Xóa/thêm và chuyển phần tử đang có

Sau khi xóa rồi thêm `review`, kết quả là `[draft, done, publish, archive, review]`. Số phần tử cuối cùng vẫn là năm nhưng đã xảy ra hai lần thay đổi thành viên.

Hàm `moveExistingToEnd` sau đó chuyển `draft` về cuối. Hàm trả `false` khi tên cần chuyển không có trong tập và không tạo thêm tên ấy. Điều kiện `if (!set.remove(element))` là phần quyết định ngữ nghĩa “chỉ chuyển phần tử đang có”.

Nếu dùng một hàm luôn `remove` rồi `add` không kiểm tra kết quả xóa, giá trị chưa có sẽ được thêm mới. Hai hàm có thể cùng cho kết quả với dữ liệu đã có nhưng khác hành vi với dữ liệu vắng mặt.

### 10.4. Đại diện cũ và đại diện mới

`equal ticket=true` xác nhận hai vé số 10 tương đương theo quy tắc đã chọn. `add equal ticket=false` xác nhận không tăng thành viên.

`original retained=true` dùng `==` có chủ đích: nó kiểm tra tham chiếu thực tế của đại diện đang lưu, không chỉ tính bằng nhau theo nghiệp vụ. Kết quả `#10:old` cho biết `add` trùng không cập nhật nội dung từ đối tượng `replacement`.

Sau xóa/thêm, vé số 20 lên trước và `#10:new` xuống cuối. `replacement retained=true` xác nhận đối tượng mới đã trở thành đại diện. Muốn kết luận về việc thay đại diện, chỉ kiểm tra `contains(replacement)` là chưa đủ, vì kết quả đó cũng có thể là `true` khi đại diện cũ còn trong tập.

### 10.5. So sánh thành viên và so sánh trình tự

`set equality=true` trong khi `iteration order equality=false` là hoàn toàn nhất quán. Hai tập chứa cùng `A` và `B`, nhưng có trình tự duyệt khác.

Chuyển từng tập sang `ArrayList` tạo hai ảnh chụp thứ tự để so sánh bằng `List.equals`. Bước này giúp kiểm tra điều mà `Set.equals` không kiểm tra. Không dùng việc hai tập bằng nhau làm bằng chứng rằng chúng sẽ in cùng một chuỗi thứ tự. Hợp đồng `Set.equals` dựa trên thành viên, không dựa trên vị trí. [Java 17: Set.equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#equals(java.lang.Object)).

### 10.6. Nhiều lần thêm không làm mất cam kết thứ tự

Phần `many` bắt đầu với dung lượng khởi tạo nhỏ và thêm 103 số khác nhau theo một trình tự được lưu riêng trong `expected`. Chương trình in `many insertion order=true` và vẫn in `true` sau khi thêm trùng số 10.

Phép thử này đối chiếu kết quả duyệt với trình tự thao tác. Nó không đo số lần mở rộng bảng, số ngăn hay bố trí bên trong. Không suy một công thức dung lượng từ số `103`. Điều được kiểm chứng ở đây là nhiều lần thêm vẫn giữ thứ tự theo hợp đồng.

### 10.7. Thêm trùng khi đang dùng iterator

Bộ duyệt `directions` đã lấy `north`. Lời gọi thêm `south` trả `false`, không thêm hoặc xóa thành viên; lần `next` kế tiếp vẫn lấy `south`.

Trường hợp này khác với việc xóa `south` rồi thêm lại giữa lúc bộ duyệt cũ đang hoạt động. Chuỗi xóa/thêm thay đổi cấu trúc dù kích thước cuối cùng có thể bằng kích thước ban đầu. Đừng dùng phép so sánh `size` trước/sau để quyết định bộ duyệt cũ có còn hợp lệ hay không.

## 11. Hàm chuyển về cuối cần hợp đồng riêng

Hàm trong chương trình:

~~~java
static <E> boolean moveExistingToEnd(LinkedHashSet<E> set, E element) {
    if (!set.remove(element)) {
        return false;
    }
    set.add(element);
    return true;
}
~~~

Đây là đoạn trích từ chương trình hoàn chỉnh, không phải API có sẵn trong JDK. Nó có ba đặc điểm cần đọc đúng.

Thứ nhất, giá trị trả về `true` biểu thị phần tử đã tồn tại và quy trình xóa/thêm được thực hiện. Nó không khẳng định vị trí cuối cùng khác vị trí trước đó. Nếu phần tử vốn đã cuối, hàm vẫn xóa rồi thêm và trả `true`.

Thứ hai, hàm đưa chính đối tượng `element` truyền vào làm đại diện khi thêm lại. Với `String` bất biến và cùng giá trị, khác biệt tham chiếu thường không đáng chú ý. Với `Ticket` mà `note` không tham gia phép bằng nhau, hành vi này có thể thay dữ liệu đang lưu. Tên hàm chỉ nói về vị trí; người thiết kế vẫn phải quyết định có cho phép thay đại diện hay cần giữ đối tượng cũ.

Thứ ba, hai thao tác không phải một hành động nguyên tử. Một luồng khác có thể xen vào nếu dữ liệu được chia sẻ mà không phối hợp đồng bộ. Nếu nghiệp vụ cần “kiểm tra rồi chuyển” như một bước không thể bị xen ngang, phải bảo vệ cả quy trình cùng các truy cập liên quan. Chỉ thấy phương thức ngắn hoặc kết quả cuối có cùng số phần tử không tạo ra tính an toàn khi nhiều luồng cùng dùng.

Trong ví dụ, dữ liệu ổn định, quy tắc bằng nhau hợp lệ và chỉ một luồng thao tác. Hàm giúp quan sát cách tạo vị trí mới bằng Java 17. Với đối tượng phức tạp hoặc yêu cầu cạnh tranh giữa nhiều luồng, cần xác định hợp đồng dữ liệu trước khi tái sử dụng nguyên xi.

## 12. Giữ thứ tự chèn không phải tự động theo dõi lần dùng gần nhất

Một hàng đợi “vừa dùng gần đây” thường cần chuyển phần tử lên vị trí ưu tiên sau mỗi lần sử dụng. Ví dụ, người dùng vừa mở lại tài liệu `draft` thì tài liệu đó phải trở thành mục mới dùng nhất.

`LinkedHashSet.add("draft")` trên một tập đã có `draft` không làm việc đó. Gọi `contains("draft")` cũng không làm việc đó. Nếu dùng những lời gọi này làm cách “chạm để làm mới”, giao diện có thể giữ thứ tự cũ dù sự kiện mới đã đến.

Phân biệt các yêu cầu:

| Yêu cầu | Ý nghĩa của lần gặp lại |
| --- | --- |
| Giữ vị trí ghi nhận ban đầu khi phần tử còn trong tập | Bỏ qua bản trùng và giữ chỗ cũ |
| Sắp theo lần xuất hiện mới nhất | Phải cập nhật vị trí khi có sự kiện mới |
| Cập nhật dữ liệu theo định danh, giữ thứ tự định danh | Thay thông tin gắn với định danh, giữ chỗ của khóa |
| Ghi lại mọi sự kiện | Mỗi lần xuất hiện phải được lưu, kể cả giá trị lặp |

Bốn yêu cầu không thể suy ra chỉ từ câu “cần giữ thứ tự”. Cần làm rõ thứ tự theo sự kiện nào và có giữ mọi lần xuất hiện hay không.

`LinkedHashMap` có chế độ thứ tự truy cập khi dùng hàm khởi tạo với `accessOrder=true`; những thao tác truy cập được quy định, chẳng hạn `get`, có thể ảnh hưởng vị trí. Khả năng này không trở thành hành vi của `LinkedHashSet.add` chỉ vì hai lớp có liên hệ trong cách triển khai. Chế độ thứ tự truy cập cũng không tự cung cấp toàn bộ chính sách dung lượng, hết hạn hay đồng bộ cho một bộ nhớ đệm. [Java 17: LinkedHashMap, chế độ thứ tự truy cập](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

Nếu cần ghi lịch sử mọi lần xảy ra, một tập loại trùng đã làm mất thông tin về số lần xuất hiện. Khi đó phải chọn mô hình lưu sự kiện phù hợp, thay vì cố suy lại lịch sử từ `LinkedHashSet` cuối cùng.

## 13. Lưu ý phiên bản: add khác addFirst và addLast

Quy tắc “thêm phần tử đã có không đổi vị trí” trong bài áp dụng cho `add` thông thường. Từ Java 21, `LinkedHashSet` triển khai `SequencedSet` và có các phương thức định vị tường minh như `addFirst` và `addLast`.

Theo API Java 21, khi phần tử đã tồn tại, `addFirst` có thể chuyển nó về đầu và `addLast` có thể chuyển nó về cuối. Nếu phần tử chưa có, chúng thêm nó ở vị trí tương ứng. Hai phương thức trả `void` và không cùng chữ ký hoặc ý nghĩa với `boolean add(E)`. [Java 21: LinkedHashSet.addFirst và addLast](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashSet.html#addFirst(E)).

Bởi vậy, hãy đọc chính xác tên phương thức trong mã. Một nhận xét đúng với `set.add(e)` không chứng minh kết quả của `set.addLast(e)`. Ngược lại, thấy `addLast` chuyển vị trí trong mã Java 21 không có nghĩa rằng `add` cũng đổi hành vi.

Chương trình của bài dùng xóa/thêm để giữ khả năng biên dịch theo Java 17. Các phương thức mới không có trong bộ API mà `--release 17` cho phép. Nếu dự án đã chọn Java 21 trở lên và cần định vị tường minh, có thể cân nhắc API đó với đúng hợp đồng của nó.

Cũng không tự đồng nhất “chuyển vị trí phần tử đã có” với “xóa rồi thêm đối tượng tương đương khác”. Ví dụ vé số 10 cho thấy chuỗi xóa/thêm của bài có thêm hệ quả về đại diện được lưu. Khi yêu cầu cập nhật cả dữ liệu lẫn vị trí, hãy kiểm tra từng hệ quả riêng.

## 14. Chi phí và giới hạn ảnh hưởng trực tiếp đến cách sử dụng

Với hàm băm phân tán tốt, các thao tác cơ bản `add`, `contains` và `remove` có hiệu năng thời gian hằng theo kỳ vọng sử dụng thông thường. Điều đó không có nghĩa mọi kiểu đối tượng, mọi phân bố mã băm đều cho cùng chi phí. Thời gian tính `hashCode` và `equals` của phần tử cũng góp vào chi phí thực tế.

`LinkedHashSet` phải duy trì liên kết để giữ thứ tự. Khi duyệt toàn bộ, thời gian tỷ lệ với số phần tử đang có, không phải phải đi tìm vị trí bằng cách quét danh sách từ đầu mỗi lần thêm. Đổi lại, có thông tin liên kết cần lưu và cập nhật. Không dùng suy luận “có liên kết thì luôn nhanh hơn” để chọn cấu trúc.

Chuỗi xóa/thêm thực hiện hai thao tác thay đổi thành viên và làm mất vị trí cũ. Nếu yêu cầu chỉ là bỏ qua bản trùng, gọi `add` trực tiếp vừa thể hiện đúng ý vừa tránh những hệ quả không cần thiết.

Khi đang duyệt, không tự xóa/thêm qua tập rồi tiếp tục dùng bộ duyệt cũ. Bộ duyệt của `LinkedHashSet` có cơ chế phát hiện thay đổi để báo lỗi sớm; `ConcurrentModificationException` hỗ trợ phát hiện lỗi và không phải cơ chế đồng bộ được bảo đảm. Dù không thấy ngoại lệ trong một thử nghiệm, bạn vẫn không được dựa vào điều đó để chứng minh mã sửa tập trong lúc duyệt là đúng. [Java 17: HashSet, bộ duyệt và giới hạn fail-fast](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html).

Nếu cần đổi thứ tự nhiều phần tử dựa trên kết quả duyệt, cách rõ ràng hơn là ghi nhận kế hoạch vào một cấu trúc riêng, kết thúc lượt duyệt, rồi áp dụng thay đổi. Cần quy định rõ kết quả khi nhiều phần tử cùng được chuyển để thứ tự cuối không phụ thuộc một vòng lặp sửa dữ liệu khó theo dõi.

## 15. Những lỗi suy luận dễ gặp

| Nhận định | Cách kiểm tra đúng |
| --- | --- |
| “Gọi `add` là đưa phần tử về cuối” | Kiểm tra phần tử đã có hay chưa; `add` trùng giữ vị trí |
| “`add` trả `false` nên phần tử không có trong tập” | `false` biểu thị không thêm thành viên mới; phần tử có thể đã tồn tại |
| “Đối tượng vừa `new` chắc chắn là phần tử mới” | So sánh theo `equals/hashCode`, không chỉ tham chiếu |
| “Thêm đối tượng cùng mã sẽ cập nhật dữ liệu cũ” | Kiểm tra đối tượng đại diện; `Set.add` không phải phép cập nhật bản ghi |
| “Xóa rồi thêm chỉ đổi vị trí” | Nó còn có thể thay đại diện và làm bộ duyệt cũ mất hiệu lực |
| “Số phần tử không đổi nên không có thay đổi cấu trúc” | Xóa một rồi thêm một vẫn là hai thay đổi |
| “`addAll` sắp tập theo lô mới nhất” | Các thành viên cũ giữ chỗ; chỉ thành viên mới được nối thêm |
| “Hai tập bằng nhau thì thứ tự giống nhau” | `Set.equals` không xét vị trí; so sánh ảnh chụp `List` nếu thứ tự là yêu cầu |
| “Giữ thứ tự nghĩa là đã sắp xếp tăng dần” | Thứ tự chèn phụ thuộc thao tác; không tự thành thứ tự theo giá trị |
| “Bản sao từ `HashSet` khôi phục lịch sử chèn” | Bản sao chỉ nhận thứ tự duyệt mà nguồn cung cấp |
| “Có LinkedHashMap bên trong thì đây là tập theo lần truy cập” | Đọc hợp đồng `LinkedHashSet`; `add` và `contains` không làm mới vị trí |
| “Mọi phương thức thêm đều giống `add`” | Tách API định vị `addFirst/addLast` của Java 21 khỏi `add` |

Một thói quen hữu ích là ghi trạng thái trước, thao tác, giá trị trả về và trạng thái sau trên bốn dòng. Cách đó buộc bạn phân biệt “không có thành viên mới” với “không có phần tử”, cũng như “cùng kích thước” với “cùng lịch sử thay đổi”.

## 16. Thực hành có lời giải

### Bài 1: dự đoán add trùng

Trạng thái ban đầu là `[red, green, blue]`. Thực hiện `add("green")`, sau đó `add("yellow")`. Hai giá trị trả về và thứ tự cuối là gì?

**Lời giải:** Lần đầu trả `false` vì `green` đã có và giữ vị trí giữa. Lần sau trả `true` và nối `yellow` vào cuối. Kết quả là `[red, green, blue, yellow]`. Không có bước nào chuyển `green`.

### Bài 2: phân biệt thêm trùng với tái gia nhập

Từ `[red, green, blue]`, thực hiện `remove("red")`, `add("green")`, `add("red")`.

**Lời giải:** Xóa `red` thành công cho `[green, blue]`. Thêm `green` trùng trả `false` và giữ nguyên. Thêm `red` sau khi đã xóa trả `true`, cho `[green, blue, red]`. Tập không lưu chỗ đầu cũ của `red`.

### Bài 3: lô có cả phần tử cũ lẫn mới

Tập ban đầu là `[A, B]`. Nguồn có thứ tự `[B, C, A, D, C]`. Gọi `addAll` thì kết quả nào?

**Lời giải:** `B` và `A` đã có nên không đổi chỗ. `C` lần đầu và `D` là mới. `C` lần cuối lại trùng. Kết quả là `[A, B, C, D]` và `addAll` trả `true`. Đây không phải thứ tự của lần xuất hiện cuối.

### Bài 4: thay thông tin của phần tử

Dùng quy tắc `Ticket` trong chương trình. Tập là `[#10:old, #20:other]`. Thêm `new Ticket(10, "new")` rồi kiểm tra `contains` bằng vé mới. Có thể kết luận thông tin `new` đã được lưu không?

**Lời giải:** Không. `contains` tìm theo số vé nên có thể trả `true` dù đại diện vẫn là `#10:old`. Phải kiểm tra đại diện hoặc chọn cấu trúc hỗ trợ cập nhật thông tin theo khóa. `add` trùng không có nghĩa cập nhật thành công.

### Bài 5: viết phép kiểm tra đúng cho thứ tự

Bạn tạo hai `LinkedHashSet` có cùng thành viên nhưng chèn theo hai trình tự khác nhau. Kiểm tra `first.equals(second)` trả `true`. Làm sao phát hiện khác thứ tự?

**Lời giải:** Trong một thời điểm dữ liệu ổn định, tạo `new ArrayList<>(first)` và `new ArrayList<>(second)`, rồi so sánh hai danh sách. `List.equals` xét trình tự. Phép kiểm tra này bổ sung yêu cầu thứ tự mà phép bằng nhau của tập không thể hiện.

### Bài 6: hàm chuyển và phần tử chưa có

Với `[A, B]`, gọi `moveExistingToEnd(set, "C")` trong chương trình. Sau đó gọi `moveExistingToEnd(set, "A")`. Tập và giá trị trả về là gì?

**Lời giải:** Lần đầu trả `false` và vẫn `[A, B]` vì hàm không tự thêm giá trị chưa có. Lần sau trả `true` và cho `[B, A]`. Nếu hàm luôn xóa rồi thêm không kiểm tra kết quả, lần đầu đã tạo `C` và mang ngữ nghĩa khác.

### Bài 7: chọn lời gọi theo phiên bản Java

Bạn cần đưa một phần tử đang có về cuối. Mã phải biên dịch với `--release 17`. Có thể thay `add` bằng `addLast` không?

**Lời giải:** Không trong bộ API Java 17. Bạn cần một quy trình tương thích phiên bản, chẳng hạn xóa/thêm với hợp đồng rõ về phần tử vắng mặt, đại diện và việc đồng bộ. Nếu chuyển dự án sang Java 21 trở lên, có thể xem xét `addLast` theo hợp đồng của phương thức đó. Đổi tên lời gọi không làm API mới xuất hiện trong Java 17.

## 17. Tự kiểm tra khi áp dụng vào mã thật

Trước khi dùng `LinkedHashSet` cho một luồng dữ liệu, hãy trả lời được các câu sau:

- Quy tắc xác định trùng là gì và `equals/hashCode` có phản ánh nó không?
- Bạn cần giữ chỗ của lần thêm thành công đầu tiên đang còn hiệu lực hay cần ưu tiên lần xuất hiện mới nhất?
- Giá trị đến sau chỉ là bản trùng hay mang thông tin cần cập nhật?
- Khi xóa rồi thêm đối tượng tương đương, có chấp nhận thay đại diện được lưu không?
- Nguồn của `addAll` có thứ tự được cam kết không?
- Phép kiểm tra đang so sánh thành viên hay thực sự so sánh trình tự duyệt?
- Có bộ duyệt cũ hoặc luồng khác đang dùng tập trong lúc bạn thay đổi không?
- Phiên bản Java của dự án có đúng với API định vị mà mã gọi không?

Điểm phải giữ chính xác trong mọi ví dụ là thời điểm xét sự hiện diện. Nếu phần tử đã có ngay trước `add`, lời gọi đó không đưa nó đến cuối. Nếu nó đã bị xóa, lần `add` tiếp theo là một lần gia nhập mới và tạo vị trí mới ở cuối trong các thao tác thông thường của bài.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-based-set-007">Khóa khả biến, hợp đồng equals–hashCode và lỗi mất dấu phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# Khóa khả biến, hợp đồng equals–hashCode và lỗi mất dấu phần tử

## 1. Tại sao một phần tử vẫn ở trong Set nhưng lại không tìm thấy?

Giả sử ứng dụng đưa một khách hàng vào `HashSet<Customer>` để tránh trùng theo mã khách hàng. Sau đó, một đoạn mã đổi chính mã ấy. Khi duyệt Set, ứng dụng vẫn nhìn thấy khách hàng; nhưng `contains` hoặc `remove` có thể trả về `false`. Đối tượng không tự biến mất khỏi bộ nhớ. Thông tin dùng để tìm nó đã không còn khớp với thông tin lúc nó được đưa vào cấu trúc băm.

Bài này nằm trong chủ đề **Set dùng băm**. Mục tiêu là hiểu ba việc liên quan nhưng khác nhau:

- `equals` quyết định hai đối tượng có được xem là cùng một giá trị hay cùng một thực thể theo mô hình đã chọn.
- `hashCode` cung cấp mã băm nhất quán với cách xem bằng nhau đó.
- Trong thời gian một phần tử nằm trong Set, dữ liệu quyết định việc nhận diện nó phải giữ ổn định.

Từ **khóa** trong bài không có nghĩa Set chứa cặp khóa–giá trị như Map. Với `HashSet<E>`, bản thân phần tử `E` được dùng để tra cứu thành viên. Những trường mà `equals` và `hashCode` dựa vào tạo thành **khóa logic** của phần tử.

Ví dụ, nếu hai khách hàng được xem là bằng nhau khi có cùng `id`, thì `id` là dữ liệu định danh; tên hiển thị có thể chỉ là dữ liệu mô tả. Nếu hai tọa độ bằng nhau khi cả `x` và `y` bằng nhau, thì cặp `(x, y)` là dữ liệu nhận diện giá trị.

Cần phân biệt thêm **đồng nhất tham chiếu** và **bằng nhau theo giá trị**:

```java
a == b       // Hai biến có cùng trỏ tới một đối tượng không?
a.equals(b)  // Theo định nghĩa của lớp, hai đối tượng có bằng nhau không?
```

Đây là hai biểu thức minh họa, không phải chương trình độc lập. Một Set dùng `equals` không có nghĩa nó sẽ bỏ qua mã băm và quét mọi đối tượng bằng `==` mỗi lần tìm kiếm.

Hướng học phù hợp là: xác định quy tắc bằng nhau → hiểu điều kiện của mã băm → xem điều gì xảy ra khi khóa đổi → chọn thiết kế khóa ổn định → kiểm tra toàn bộ vòng đời của phần tử.

## 2. Hợp đồng equals–hashCode cần bảo vệ điều gì?

Với các tham chiếu khác `null`, `equals` cần có tính phản xạ, đối xứng, bắc cầu và nhất quán khi dữ liệu dùng so sánh không đổi; so sánh với `null` trả về `false`.

Điều kiện quan trọng của mã băm là:

```text
a.equals(b) == true  =>  a.hashCode() == b.hashCode()
```

Chiều ngược lại không bắt buộc. Hai đối tượng không bằng nhau vẫn có thể trùng mã băm. Mã băm của một đối tượng cần ổn định trong một lần chạy khi thông tin dùng cho `equals` không đổi; không cần giữ nguyên giữa các lần chạy ứng dụng. Đây là hợp đồng của [Object.equals và Object.hashCode trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object)).

Hai trường hợp sau phải được đọc khác nhau:

| Quan hệ giữa hai đối tượng | Quan hệ mã băm | Ý nghĩa |
|---|---|---|
| Bằng nhau | Cùng mã băm | Phù hợp với điều kiện bắt buộc |
| Bằng nhau | Khác mã băm | Lỗi triển khai hợp đồng |
| Không bằng nhau | Cùng mã băm | Va chạm băm; tự nó không phải lỗi đúng đắn |
| Không bằng nhau | Khác mã băm | Trường hợp bình thường |

Mã băm không phải số căn cước duy nhất của đối tượng. Không dùng một `Set<Integer>` chứa các giá trị `hashCode()` để thay cho `Set<Customer>` nếu cần khử trùng khách hàng: hai khách hàng khác nhau có thể bị gộp chỉ vì va chạm băm.

Khi viết `equals` theo `id`, một cách dễ kiểm tra là viết `hashCode` theo cùng `id`. Không được đưa thêm một trường có thể khác giữa hai đối tượng bằng nhau vào mã băm theo cách làm chúng ra hai kết quả khác nhau. Ví dụ, `equals` chỉ xét `id` nhưng `hashCode` còn xét `displayName` sẽ gây lỗi khi hai tên khác nhau vẫn thuộc cùng một `id`.

Ngược lại, mã băm không nhất thiết phải chứa mọi trường của `equals`. Nó có thể dùng ít thông tin hơn và vẫn thỏa điều kiện một chiều ở trên, nhưng dễ tạo nhiều va chạm hơn. Vì vậy, cần đánh giá cả tính đúng đắn lẫn phân bố, thay vì coi “băm tất cả trường” là một quy tắc áp dụng máy móc.

## 3. Hợp đồng của lớp và điều kiện sử dụng Set là hai tầng khác nhau

Một lớp khả biến có thể triển khai `equals` và `hashCode` hợp lý tại từng thời điểm:

```java
// Trích đoạn minh họa trong một lớp có trường int code.
@Override
public boolean equals(Object other) {
    return this == other
            || (other instanceof Key key && code == key.code);
}

@Override
public int hashCode() {
    return Integer.hashCode(code);
}
```

Nếu `code` đổi từ `1` thành `2`, cả quan hệ bằng nhau lẫn mã băm đổi theo dữ liệu mới. Không thể chỉ nhìn thấy mã băm đổi rồi kết luận mọi lớp như vậy đều vi phạm hợp đồng của `Object`: hợp đồng có điều kiện liên quan đến việc dữ liệu dùng so sánh có thay đổi hay không.

Vấn đề xuất hiện khi đối tượng ấy đang là phần tử của một Set. Tài liệu [Set của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html) nêu rằng hành vi của Set không được đặc tả nếu giá trị phần tử bị thay đổi theo cách ảnh hưởng đến so sánh `equals` trong lúc nó đang ở trong Set.

Vì vậy, cần kiểm tra hai tầng:

1. **Tầng lớp:** hai đối tượng bằng nhau có luôn cho cùng mã băm, các tính chất của `equals` có đúng không?
2. **Tầng vòng đời trong cấu trúc:** dữ liệu quyết định bằng nhau có bị sửa từ khi thêm đến khi gỡ phần tử khỏi Set không?

Một lớp có thể vượt qua kiểm tra tầng thứ nhất nhưng vẫn được sử dụng sai ở tầng thứ hai. Đó là lý do một bộ kiểm thử chỉ tạo hai đối tượng, gọi `equals`, rồi kết thúc chưa đủ để phát hiện lỗi mất dấu.

Cũng không nên cấm mọi thay đổi trên mọi phần tử. Nếu định danh chỉ phụ thuộc vào một `id` ổn định, đổi ghi chú hoặc tên hiển thị không tham gia `equals`–`hashCode` có thể phù hợp. Điều cần giữ ổn định là **ngữ nghĩa nhận diện**, không nhất thiết toàn bộ trạng thái của đối tượng.

## 4. Cấu trúc băm giữ tham chiếu nhưng không theo dõi setter

`HashSet` được hỗ trợ bởi một `HashMap`, theo [tài liệu HashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html). Có thể hình dung thao tác thêm và tìm như sau:

```text
Khi thêm:
    đọc khóa logic hiện tại
        -> tính mã băm
        -> chọn vùng tìm kiếm
        -> kiểm tra phần tử phù hợp
        -> lưu một mục chứa tham chiếu tới đối tượng

Khi tìm hoặc xóa bằng đối tượng:
    đọc khóa logic tại thời điểm gọi
        -> tính mã băm hiện tại
        -> tìm trong vùng tương ứng
        -> kiểm tra phần tử ứng viên
```

Trong triển khai [HashMap của OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/HashMap.java), một mục giữ mã băm đã tính khi tạo và tham chiếu khóa. Tra cứu tính lại mã băm từ đối tượng được đưa vào, kiểm tra mã băm của mục rồi mới xét tham chiếu hoặc `equals`. Thay đổi trường của đối tượng không tự cập nhật mục đã lưu.

Sơ đồ này chỉ là mô hình phục vụ hiểu lỗi:

```text
Thời điểm thêm:
    đối tượng K: code = 1
    mục lưu: tham chiếu K, thông tin băm từ code = 1

Sau khi gọi setter:
    đối tượng K: code = 2
    mục cũ: vẫn giữ thông tin băm lúc thêm

Khi contains(K):
    yêu cầu tìm sử dụng thông tin băm từ code = 2
```

Không cần học thuộc số ngăn hoặc công thức nội bộ để tránh lỗi. Chỉ cần nhận ra rằng một chỉ mục được tạo theo thông tin cũ không tự điều chỉnh khi đối tượng được sửa qua một tham chiếu khác.

Đặc biệt, `==` đúng giữa đối tượng cần tìm và đối tượng đã lưu không bảo đảm tra cứu sẽ thành công: bước tìm vùng ứng viên và kiểm tra mã băm có thể đã loại mục cũ trước đó.

Cũng không nên kết luận “hai mã băm cùng rơi vào một ngăn là an toàn”. Trong triển khai được mô tả, mã băm lưu ở từng mục còn tham gia kiểm tra. Quan trọng hơn, việc nhận diện thành viên đã bị thay đổi thì điều kiện sử dụng Set đã không còn được bảo vệ.

## 5. Chương trình 1: phần tử mất dấu rồi cùng tham chiếu được thêm lần nữa

Lưu tệp `MutableKeyFailureDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;

public class MutableKeyFailureDemo {
    static final class Key {
        private int code;

        Key(int code) {
            this.code = code;
        }

        void changeCode(int newCode) {
            code = newCode;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof Key key && code == key.code);
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(code);
        }
    }

    public static void main(String[] args) {
        Key key = new Key(1);
        Set<Key> keys = new HashSet<>();
        keys.add(key);

        System.out.println("contains before change = " + keys.contains(key));
        key.changeCode(2);
        System.out.println("size after change = " + keys.size());

        long references = 0;
        for (Key stored : keys) {
            if (stored == key) {
                references++;
            }
        }
        System.out.println("same reference seen by iteration = " + references);
        System.out.println("contains after change = " + keys.contains(key));
        System.out.println("remove after change = " + keys.remove(key));
        System.out.println("add same reference again = " + keys.add(key));
        System.out.println("size after re-add = " + keys.size());

        references = 0;
        for (Key stored : keys) {
            if (stored == key) {
                references++;
            }
        }
        System.out.println("same reference occurrences = " + references);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 MutableKeyFailureDemo.java
java MutableKeyFailureDemo
```

Kết quả của lần chạy kiểm chứng:

```text
contains before change = true
size after change = 1
same reference seen by iteration = 1
contains after change = false
remove after change = false
add same reference again = true
size after re-add = 2
same reference occurrences = 2
```

Đây là **thí nghiệm cố ý dùng sai Set**. Mã dùng ngôn ngữ và API có ở Java 17 trở lên. Các kết quả đã được chạy kiểm chứng bằng Azul OpenJDK 24.0.2 sau khi biên dịch với `--release 17`; cơ chế được đối chiếu với mã nguồn OpenJDK 17. Những dòng sau khi đổi khóa là quan sát của triển khai này, không phải lời hứa rằng mọi triển khai Set hoặc mọi dữ liệu sai sẽ cho cùng kết quả.

### 5.1. Vì sao size vẫn bằng 1?

Lời gọi `key.changeCode(2)` chỉ sửa đối tượng. Nó không gọi `keys.remove`, không gọi `keys.add` và không làm Set chủ động đếm lại các lớp tương đương. Mục đã lưu vẫn tồn tại.

Vòng lặp thấy cùng tham chiếu một lần. Điều đó giúp xác định rằng đối tượng chưa bị gỡ khỏi các mục của cấu trúc. Nó không chứng minh đường tra cứu theo mã băm hiện tại còn đúng.

### 5.2. Vì sao contains và remove cùng thất bại?

Hai thao tác đều được gọi bằng khóa đã đổi. Trong ví dụ này, đường tìm hiện tại không khớp mục được tạo lúc `code = 1`. Do đó, cả kiểm tra lẫn xóa bằng đối tượng đều không tìm được mục cũ.

Không nên “sửa” bằng cách thay `remove(key)` thành `remove(new Key(2))`: đối tượng thăm dò mới cũng mô tả định danh mới và dùng mã băm mới. Vấn đề nằm ở trạng thái chỉ mục cũ, không nằm ở việc có dùng đúng biến tham chiếu hay không.

### 5.3. Vì sao add lại cùng tham chiếu có thể tạo size bằng 2?

Lần `add` sau dùng thông tin băm mới. Trong lần chạy này, nó không phát hiện mục cũ và tạo thêm một mục nữa trỏ vào chính đối tượng ấy. Khi duyệt, cùng tham chiếu được gặp hai lần.

Đây không phải khả năng hợp lệ để khai thác như một Set chứa phần tử trùng. Nó là hậu quả quan sát được sau khi chương trình đã thay đổi thông tin nhận diện của phần tử trong Set.

Điểm thực hành quan trọng là: **không chữa phần tử mất dấu bằng cách add lại**. Việc đó có thể làm trạng thái khó giải thích hơn, thay vì khôi phục tính duy nhất.

### 5.4. Vì sao không có ConcurrentModificationException?

Đổi trường `code` không tương đương với thao tác cấu trúc như thêm hoặc gỡ một mục qua Set. Không có cơ chế chung buộc mọi setter của phần tử phải thông báo cho iterator.

Một chương trình không ném ngoại lệ vẫn có thể đang dùng sai điều kiện của cấu trúc dữ liệu. Không dùng việc “vòng lặp chạy xong bình thường” làm bằng chứng rằng mọi khóa đều còn tìm được.

## 6. Chương trình 2: hợp đồng sai khác với va chạm băm hợp lệ

Ví dụ tiếp theo đặt cạnh nhau hai loại:

- `BrokenKey` so sánh theo `id`, nhưng cho phép hai đối tượng cùng `id` có mã băm khác nhau. Nó sai ngay từ thiết kế, dù mọi trường đều `final`.
- `CollidingKey` là record có định danh theo `id`, nhưng cố ý trả về mã băm `0` cho mọi giá trị. Phân bố này kém, nhưng điều kiện “bằng nhau thì cùng mã băm” vẫn đúng.

Lưu tệp `EqualityHashContractDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;

public class EqualityHashContractDemo {
    static final class BrokenKey {
        private final int id;
        private final int suppliedHash;

        BrokenKey(int id, int suppliedHash) {
            this.id = id;
            this.suppliedHash = suppliedHash;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof BrokenKey key && id == key.id);
        }

        @Override
        public int hashCode() {
            return suppliedHash;
        }
    }

    record CollidingKey(int id) {
        @Override
        public int hashCode() {
            return 0;
        }
    }

    public static void main(String[] args) {
        BrokenKey a = new BrokenKey(7, 1);
        BrokenKey b = new BrokenKey(7, 2);
        Set<BrokenKey> broken = new HashSet<>();

        System.out.println("broken keys equal = " + a.equals(b));
        System.out.println("broken hashes equal = "
                + (a.hashCode() == b.hashCode()));
        System.out.println("add broken a = " + broken.add(a));
        System.out.println("add broken b = " + broken.add(b));
        System.out.println("broken set size = " + broken.size());

        CollidingKey x = new CollidingKey(1);
        CollidingKey y = new CollidingKey(2);
        Set<CollidingKey> valid = new HashSet<>();

        System.out.println("collision keys equal = " + x.equals(y));
        System.out.println("collision hashes equal = "
                + (x.hashCode() == y.hashCode()));
        System.out.println("add collision x = " + valid.add(x));
        System.out.println("add collision y = " + valid.add(y));
        System.out.println("contains equal probe = "
                + valid.contains(new CollidingKey(1)));
        System.out.println("add equal probe = "
                + valid.add(new CollidingKey(1)));
        System.out.println("valid set size = " + valid.size());
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 EqualityHashContractDemo.java
java EqualityHashContractDemo
```

Kết quả của lần chạy kiểm chứng:

```text
broken keys equal = true
broken hashes equal = false
add broken a = true
add broken b = true
broken set size = 2
collision keys equal = false
collision hashes equal = true
add collision x = true
add collision y = true
contains equal probe = true
add equal probe = false
valid set size = 2
```

Các dòng của `BrokenKey` minh họa một lớp vi phạm hợp đồng, không phải một cách tạo Set hợp lệ có hai thành viên bằng nhau. Kết quả của ví dụ lỗi đã được kiểm chứng trên môi trường nêu ở chương trình 1; không dùng kết quả ấy làm yêu cầu tương thích cho các thư viện khác.

Ở phần `CollidingKey`, `x` và `y` có cùng mã băm nhưng không bằng nhau nên cả hai được giữ. Khi thêm `new CollidingKey(1)`, Set nhận ra phần tử bằng nhau đã có và trả về `false`.

Từ ví dụ này có hai kết luận khác nhau:

1. Các trường `final` không tự làm cho một cặp `equals`–`hashCode` sai trở thành đúng.
2. Va chạm băm không cho phép bỏ qua `equals`; cũng không tự làm mất tính duy nhất nếu các điều kiện của cấu trúc được giữ.

Không chọn mã băm hằng trong mã thực tế chỉ để làm bài kiểm tra “hai giá trị bằng nhau có cùng hash” luôn qua. Bài kiểm tra đó là điều kiện cần; nó chưa đánh giá chất lượng phân bố hoặc việc thay đổi quan hệ bằng nhau trong thời gian phần tử nằm trong Set.

## 7. Vì sao các cách vá chỉ sửa hash vẫn chưa đủ?

### 7.1. Giữ hash hằng không giữ được ý nghĩa duy nhất

Giả sử lớp có `equals` theo một trường `code` khả biến nhưng `hashCode` luôn trả về `0`. Thêm hai đối tượng có mã `1` và `2` lúc đầu là hai thành viên khác nhau. Sau đó sửa mã của đối tượng thứ hai thành `1`.

Hai đối tượng bây giờ bằng nhau theo `equals`, mặc dù cấu trúc vẫn có hai mục đã được thêm trước đó. Set không chủ động hợp nhất chúng sau setter.

Ở đây, mã băm không hề đổi. Lỗi vẫn tồn tại vì **quan hệ bằng nhau đã đổi trong lúc các phần tử còn ở trong Set**. Do đó, yêu cầu “chỉ giữ hash ổn định là đủ” bỏ sót phần quan trọng nhất.

### 7.2. Lưu mã băm cũ trong một trường

Nếu `equals` đọc mã hiện tại nhưng `hashCode` trả về kết quả đã lưu từ mã cũ, một đối tượng thăm dò được tạo theo giá trị hiện tại có thể bằng đối tượng cũ nhưng lại cho mã băm khác. Cách vá này chuyển lỗi vòng đời thành cả lỗi nhất quán giữa hai phương thức.

Chỉ nên lưu sẵn mã băm khi dữ liệu quyết định bằng nhau đã bất biến hoặc đã được chụp thành giá trị ổn định phù hợp. Không dùng bộ nhớ đệm để che việc thay đổi định danh.

### 7.3. Dùng identityHashCode trong khi equals vẫn theo dữ liệu

Mã băm theo đồng nhất đối tượng không tự phù hợp với `equals` theo giá trị. Hai đối tượng khác tham chiếu có thể cùng `id` và được xem là bằng nhau, nhưng cách băm theo từng đối tượng không bảo đảm chúng có cùng kết quả.

Nếu mục tiêu của mô hình thực sự là đồng nhất tham chiếu, hãy thiết kế và chọn cấu trúc theo đúng mục tiêu ấy. Không ghép một nửa quy tắc theo giá trị với một nửa quy tắc theo tham chiếu để chữa một lỗi tra cứu.

### 7.4. Băm ngẫu nhiên hoặc băm theo thời gian

Nếu dữ liệu nhận diện không đổi mà mỗi lần gọi mã băm lại cho kết quả khác, lớp đã không đáp ứng hợp đồng ngay cả trước khi xét đến khả biến. Tra cứu không có cơ sở ổn định để tìm mục trước đó.

Nên sửa **mô hình nhận diện và vòng đời**, sau đó mới cải thiện phân bố mã băm. Việc thay công thức hash đơn lẻ không giải quyết được một định danh đang đổi ngoài sự kiểm soát của cấu trúc.

## 8. Chọn dữ liệu nào làm khóa logic?

Hai mô hình thường gặp cần được quyết định theo nghiệp vụ:

| Mô hình | Câu hỏi nhận diện | Dữ liệu phù hợp | Cách thay đổi |
|---|---|---|---|
| Giá trị | Hai giá trị có cùng nội dung quan trọng không? | Thành phần của giá trị, được giữ bất biến | Tạo giá trị mới |
| Thực thể | Hai đối tượng có đại diện cùng thực thể không? | Mã định danh ổn định, đã được xác lập | Đổi dữ liệu mô tả, giữ định danh |
| Bản chụp | Hai ảnh chụp tại thời điểm thu thập có cùng thông tin khóa không? | Thành phần đã sao chép và đóng băng đúng mức | Thu thập bản chụp mới |
| Đồng nhất tham chiếu | Có đúng là cùng một đối tượng không? | Ngữ nghĩa đồng nhất đối tượng | Chỉ dùng khi bài toán thật sự yêu cầu |

Không có quy tắc “mọi trường luôn thuộc equals” hoặc “chỉ một id luôn là đủ” cho mọi lớp. Ví dụ:

- Một khách hàng có thể đổi tên nhưng vẫn là cùng khách hàng.
- Một điểm tọa độ đổi `x` thì đã thành một giá trị tọa độ khác.
- Một địa chỉ email dùng làm tên đăng nhập có thể thay đổi theo quy trình nghiệp vụ; nó không đương nhiên là định danh ổn định suốt đời của tài khoản.
- Một thực thể chưa có mã được cấp chính thức cần chính sách nhận diện trước khi tham gia Set theo mã ấy. Không đưa nhiều đối tượng “chưa có id” vào cùng một quy tắc bằng nhau mơ hồ, rồi hy vọng Set sẽ tự điều chỉnh khi id được cấp.

Một khóa giá trị đơn giản có thể được biểu diễn bằng record:

```java
// Trích đoạn khai báo; có thể đặt trong một lớp phù hợp.
record CustomerKey(long id) {
}
```

`long` là giá trị nguyên thủy; `id` của record không có setter. Nếu định danh đã được xác lập đúng và không phải đổi, kiểu này giúp biểu diễn ý định rõ hơn một đối tượng lớn có nhiều dữ liệu mô tả.

Với khóa gồm `String`, bản thân `String` là bất biến; nhưng vẫn phải quyết định chính sách nghiệp vụ: có phân biệt chữ hoa–thường không, có chuẩn hóa khoảng trắng không, có cho phép giá trị trống không. Chuẩn hóa khi tạo khóa giúp mọi nơi dùng cùng một giá trị chuẩn. Không gọi chuẩn hóa có tác dụng phụ từ bên trong `equals`.

## 9. Chương trình 3: dữ liệu mô tả có thể đổi khi định danh ổn định

Ví dụ sau chọn `id` đã được cấp làm định danh của `Customer`. Tên hiển thị không tham gia so sánh hoặc băm.

Lưu tệp `StableIdentitySetDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;

public class StableIdentitySetDemo {
    static final class Customer {
        private final long id;
        private String displayName;

        Customer(long id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        void rename(String newName) {
            displayName = newName;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof Customer customer && id == customer.id);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(id);
        }
    }

    public static void main(String[] args) {
        Customer original = new Customer(7L, "Lan");
        Set<Customer> customers = new HashSet<>();
        customers.add(original);
        int hashBefore = original.hashCode();

        original.rename("Minh");
        Customer probe = new Customer(7L, "Another name");

        System.out.println("hash unchanged after rename = "
                + (hashBefore == original.hashCode()));
        System.out.println("contains original = " + customers.contains(original));
        System.out.println("contains equal probe = " + customers.contains(probe));
        System.out.println("add equal probe = " + customers.add(probe));
        System.out.println("size = " + customers.size());
        System.out.println("stored reference is original = "
                + (customers.iterator().next() == original));
        System.out.println("stored name = "
                + customers.iterator().next().displayName);
        System.out.println("remove equal probe = " + customers.remove(probe));
        System.out.println("empty after remove = " + customers.isEmpty());
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 StableIdentitySetDemo.java
java StableIdentitySetDemo
```

Kết quả của lần chạy kiểm chứng:

```text
hash unchanged after rename = true
contains original = true
contains equal probe = true
add equal probe = false
size = 1
stored reference is original = true
stored name = Minh
remove equal probe = true
empty after remove = true
```

Đổi tên từ `Lan` sang `Minh` không thay đổi việc khách hàng mang `id = 7` là ai. Đối tượng thăm dò có tên khác vẫn được Set nhận diện là cùng một thành viên.

Lời gọi `add(probe)` trả về `false` và không thay thế đối tượng đã lưu. Sau đó, Set vẫn giữ tham chiếu `original` cùng tên `Minh`. Đây là điều cần nhớ khi dùng Set để “cập nhật bản ghi”: thêm một đối tượng bằng nhau không đồng nghĩa với cập nhật dữ liệu của đối tượng cũ.

Ví dụ chọn lớp `final` để không mở thêm vấn đề đối xứng khi lớp con thay đổi quy tắc bằng nhau. Trong một thiết kế có kế thừa, cần xem xét hợp đồng giữa lớp cha và lớp con, thay vì sao chép `instanceof` một cách máy móc.

Thiết kế này chỉ phù hợp khi:

- `id` thực sự ổn định trong vòng đời cần quản lý.
- Hai đối tượng cùng `id` phải được xem là cùng khách hàng trong phạm vi ứng dụng.
- Tên hiển thị không phải một phần của giá trị dùng để phân biệt thành viên.

Nếu yêu cầu nghiệp vụ là lưu nhiều phiên bản tên của cùng khách hàng dưới các thời điểm khác nhau, cần một khóa phiên bản phù hợp. Không giữ nguyên `equals` theo `id` rồi mong Set chứa mọi phiên bản.

Một lựa chọn khác là tách khóa khỏi dữ liệu có thể sửa:

```java
// Trích đoạn mô hình, không phải chương trình độc lập.
record CustomerKey(long id) {
}

final class CustomerProfile {
    private String displayName;

    CustomerProfile(String displayName) {
        this.displayName = displayName;
    }

    void rename(String newName) {
        displayName = newName;
    }
}

// Khi cần cả tra cứu theo khóa lẫn cập nhật dữ liệu mô tả:
// Map<CustomerKey, CustomerProfile> profiles = new HashMap<>();
```

Đây là cách thể hiện rằng định danh và nội dung được cập nhật có trách nhiệm khác nhau. Tuy nhiên, chọn Map còn tùy thao tác của bài toán; không cần thay mọi Set bằng Map chỉ vì phần tử có một trường mô tả.

## 10. final và record chỉ giúp đúng trong phạm vi chúng bảo vệ

Một trường `final` kiểu tham chiếu ngăn thay chính tham chiếu sau khởi tạo; nó không ngăn sửa đối tượng được tham chiếu.

Ví dụ:

```java
// Trích đoạn minh họa.
final List<String> tags = new ArrayList<>();
tags.add("A");   // Hợp lệ: thay nội dung List.
tags.add("B");   // Vẫn hợp lệ.
```

Record cũng không tự đóng băng toàn bộ cây đối tượng. [Tài liệu Record Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html) mô tả record là bất biến nông: các thành phần được cố định theo tham chiếu hoặc giá trị trực tiếp, nhưng đối tượng nằm sau một tham chiếu có thể vẫn khả biến.

Nếu record chứa `List<String>`, phương thức bằng nhau và mã băm được sinh mặc định sẽ phụ thuộc vào giá trị thành phần List. Thay đổi nội dung của List có thể làm thay đổi nhận diện của record.

### 10.1. Chương trình 4: bản sao bảo vệ cho thành phần List

`UnsafeKey` nhận thẳng List do bên ngoài giữ. `SafeKey` dùng hàm khởi tạo rút gọn (compact constructor) để nhận một bản chụp không cho sửa qua List đó.

Lưu tệp `RecordKeySnapshotDemo.java`:

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecordKeySnapshotDemo {
    record UnsafeKey(List<String> tags) {
    }

    record SafeKey(List<String> tags) {
        SafeKey {
            tags = List.copyOf(tags);
        }
    }

    public static void main(String[] args) {
        List<String> input = new ArrayList<>(List.of("A"));
        UnsafeKey unsafe = new UnsafeKey(input);
        SafeKey safe = new SafeKey(input);
        Set<UnsafeKey> unsafeSet = new HashSet<>();
        Set<SafeKey> safeSet = new HashSet<>();
        unsafeSet.add(unsafe);
        safeSet.add(safe);

        System.out.println("unsafe contains before change = "
                + unsafeSet.contains(unsafe));
        input.add("B");

        System.out.println("input size = " + input.size());
        System.out.println("unsafe tags size = " + unsafe.tags().size());
        System.out.println("safe tags size = " + safe.tags().size());
        System.out.println("unsafe contains after change = "
                + unsafeSet.contains(unsafe));
        System.out.println("safe contains original = " + safeSet.contains(safe));
        System.out.println("safe contains equal snapshot = "
                + safeSet.contains(new SafeKey(List.of("A"))));

        try {
            safe.tags().add("C");
        } catch (UnsupportedOperationException ex) {
            System.out.println("mutate safe tags = "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 RecordKeySnapshotDemo.java
java RecordKeySnapshotDemo
```

Kết quả của lần chạy kiểm chứng:

```text
unsafe contains before change = true
input size = 2
unsafe tags size = 2
safe tags size = 1
unsafe contains after change = false
safe contains original = true
safe contains equal snapshot = true
mutate safe tags = UnsupportedOperationException
```

Phần `unsafeSet` là thí nghiệm cố ý thay đổi dữ liệu dùng nhận diện sau khi thêm phần tử; các kết quả sau thay đổi là quan sát trên triển khai đã kiểm chứng. Phần `safeSet` giữ khóa ổn định trong ví dụ này.

### 10.2. Sao chép cái gì, bảo vệ được tới đâu?

Trong constructor:

```java
SafeKey {
    tags = List.copyOf(tags);
}
```

Tham số `tags` được thay bằng kết quả `List.copyOf` trước khi được gán cho thành phần của record. Theo [List.copyOf trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)), List trả về không cho sửa cấu trúc, không phản ánh sửa đổi tiếp theo của collection đầu vào, và không chấp nhận đầu vào hoặc phần tử `null`.

Trong ví dụ, phần tử là `String` bất biến, nên không còn đường sửa nội dung nhận diện qua các chuỗi ấy. Nếu thành phần là `List<MutableTag>`, chỉ sao chép List chưa đủ: đối tượng `MutableTag` vẫn có thể bị sửa qua tham chiếu được chia sẻ.

Khi đó, cần một trong những lựa chọn có chủ đích:

- Chuyển từng phần tử thành một giá trị bất biến dùng làm khóa.
- Sao chép phòng vệ đủ sâu đối với những phần tham gia nhận diện.
- Chỉ dùng một định danh bất biến của phần tử lồng bên trong.
- Đổi mô hình để phần dữ liệu lồng có thể sửa không tham gia khóa.

Không cần sao chép sâu toàn bộ đồ thị đối tượng nếu dữ liệu không liên quan đến nhận diện. Nhưng phải biết chính xác đường tham chiếu nào có thể làm đổi `equals` hoặc `hashCode`.

### 10.3. Các đường sửa gián tiếp dễ bị bỏ sót

Khóa có thể đổi mà không có setter trực tiếp trên lớp khóa:

- Constructor giữ lại mảng hoặc List đầu vào mà bên gọi vẫn sở hữu.
- Getter trả thẳng collection nội bộ.
- Một đối tượng lồng trong khóa có setter.
- Một collection làm khóa được thêm, xóa hoặc thay phần tử.
- Mã gọi giữ một tham chiếu khác tới cùng đối tượng, rồi sửa qua tham chiếu đó.

Vì vậy, kiểm tra “lớp không có setter” mới là bước đầu. Cần kiểm tra cả dữ liệu đầu vào, dữ liệu đầu ra và thành phần lồng trong khóa.

Với mảng, còn phải quyết định ngữ nghĩa bằng nhau theo tham chiếu hay theo nội dung. Đừng giả định rằng mọi thành phần tham chiếu của record đều tự được so sánh sâu theo cách mong muốn. Hãy chọn một kiểu khóa biểu đạt rõ ngữ nghĩa, thay vì chỉ chọn cú pháp ngắn.

## 11. Nếu nghiệp vụ thật sự cần đổi khóa thì làm thế nào?

### 11.1. Ưu tiên tạo khóa mới

Với khóa giá trị bất biến, “đổi mã” thường nên được hiểu là gỡ giá trị cũ và đưa giá trị mới vào:

```java
// Trích đoạn trong luồng xử lý đơn luồng, Set ban đầu hợp lệ.
record CodeKey(int code) {
}

Set<CodeKey> keys = new HashSet<>();
CodeKey oldKey = new CodeKey(1);
CodeKey replacement = new CodeKey(2);
keys.add(oldKey);

if (keys.contains(replacement)) {
    throw new IllegalStateException("Replacement key already exists");
}
if (!keys.remove(oldKey)) {
    throw new IllegalStateException("Old key is missing");
}
if (!keys.add(replacement)) {
    throw new IllegalStateException("Replacement was not added");
}
```

Đây là đoạn minh họa thứ tự thao tác, không phải một giao dịch tổng quát. Nó giả định không có luồng khác chen vào, không có tác dụng phụ trong `equals`–`hashCode`, và không có lỗi giữa các bước. Các kiểu cần thiết như `Set`, `HashSet` được import trong chương trình bao quanh.

Kiểm tra xung đột trước giúp tránh gỡ khóa cũ rồi mới phát hiện khóa mới đã được dùng. Trong ứng dụng có nhiều cấu trúc liên quan, việc đổi định danh cần một quy trình nghiệp vụ thống nhất, có chính sách thất bại phù hợp.

Tạo khóa mới không làm thay đổi khóa cũ mà một cấu trúc khác đang giữ. Đây là ưu điểm lớn so với sửa tại chỗ cùng một đối tượng được chia sẻ rộng rãi.

### 11.2. Nếu bắt buộc giữ đối tượng khả biến

Thứ tự tối thiểu trong một tình huống đã kiểm soát là:

```text
1. Khi khóa cũ còn nguyên, xác định xung đột với giá trị mới.
2. Gỡ phần tử khỏi mọi chỉ mục phụ thuộc vào khóa sắp đổi.
3. Kiểm tra thao tác gỡ đã thành công.
4. Thay đổi dữ liệu nhận diện.
5. Thêm lại vào các chỉ mục theo khóa mới.
6. Xử lý lỗi và xung đột theo chính sách đã thiết kế.
```

Điều kiện “khóa cũ còn nguyên” là thiết yếu. Nếu đã sửa trước rồi mới gọi `remove`, chương trình đã rơi vào chính tình huống mất dấu đang học.

Một đối tượng có thể đồng thời là:

- Phần tử trong `HashSet`.
- Khóa trong `HashMap`.
- Thành phần của một khóa tổng hợp ở nơi khác.
- Phần tử trong một cấu trúc sắp xếp theo thuộc tính ấy.

Gỡ khỏi một Set không sửa được các chỉ mục khác. Khi có nhiều nơi giữ tham chiếu, việc bảo đảm quy trình trở nên khó hơn; đó là lý do khóa bất biến thường giảm đáng kể gánh nặng kiểm soát.

### 11.3. Đừng bỏ qua xung đột

Giả sử đã có khách hàng mã `2`, và một khách hàng mã `1` được đổi thành `2`. Sau khi gỡ khách hàng cũ, `add` theo khóa mới có thể trả về `false` vì thành viên tương đương đã tồn tại.

Set không thể quyết định nên hợp nhất, từ chối, ghi đè, hay giữ một lịch sử phiên bản. Đó là chính sách nghiệp vụ. Bỏ qua giá trị trả về của `add` dễ biến một lỗi định danh thành mất dữ liệu trong luồng xử lý.

Đối với hệ thống có nhiều luồng, chuỗi `contains` → `remove` → `add` cũng không tự trở thành nguyên tử chỉ vì cấu trúc có hỗ trợ đồng thời ở từng thao tác. Phải thiết kế cơ chế phối hợp cho toàn bộ yêu cầu cập nhật.

## 12. Chẩn đoán một Set đã có dấu hiệu mất dấu

Một dấu hiệu đáng điều tra là: duyệt thấy đối tượng nhưng `contains` bằng chính đối tượng ấy lại trả về `false`. Hãy bắt đầu từ dữ liệu và đường sửa của khóa.

### 12.1. Thu thập thông tin có ý nghĩa

Ở ranh giới thêm phần tử, có thể ghi nhận phục vụ điều tra:

- Giá trị định danh lúc thêm.
- Giá trị định danh hiện tại.
- Mã băm lúc thêm và hiện tại, nếu đã có ghi nhận.
- Thời điểm và nơi thực hiện thao tác thay đổi.
- Những cấu trúc khác đang giữ cùng đối tượng.

Không thể lấy lại “mã băm lúc thêm” bằng cách gọi `hashCode` sau khi đối tượng đã đổi. Lời gọi mới đọc trạng thái hiện tại; nó không cung cấp lịch sử.

Cũng không chỉ nhìn `toString`: phương thức đó có thể không in trường định danh, hoặc cũng đọc giá trị mới. Cần quan sát đúng dữ liệu mà `equals`–`hashCode` thực sự sử dụng.

### 12.2. Kiểm tra đường sửa, kể cả gián tiếp

Tìm các setter, thao tác cập nhật collection lồng bên trong, constructor giữ lại dữ liệu đầu vào và getter làm lộ đối tượng có thể sửa. Kiểm tra cả mã ánh xạ dữ liệu hoặc gán định danh sau khi đã thêm vào Set.

Một đối tượng có mã băm không đổi chưa đủ để kết luận an toàn: hai đối tượng vốn không bằng nhau có thể đã trở thành bằng nhau sau thay đổi dữ liệu. Cần so cả ngữ nghĩa nhận diện, không chỉ con số hash.

### 12.3. Tạm dừng thay đổi trước khi phục hồi

Nếu dữ liệu vẫn tiếp tục đổi trong lúc kiểm tra, một lần dựng lại Set có thể lập tức sai trở lại. Trước hết phải chặn hoặc sửa đường tạo lỗi.

Không đưa ra một phép chữa chung như:

```java
// Cách vá thiếu cơ sở: không dùng làm quy trình phục hồi.
keys.add(lostKey);
```

Ví dụ đầu đã cho thấy thêm lại có thể tạo thêm một mục cho cùng tham chiếu.

Tương tự, không mặc định rằng tăng dung lượng, ép Set phát triển, bọc bằng một wrapper khác hoặc gọi lại `contains` nhiều lần sẽ phục hồi chỉ mục. Những việc ấy không xác lập lại một mô hình khóa hợp lệ.

### 12.4. Dựng lại có kiểm soát và giải quyết trùng lặp

Khi đã biết dữ liệu chuẩn hiện tại và đã dừng thay đổi định danh, một hướng phục hồi là đọc dữ liệu cần giữ, chuyển thành khóa ổn định, rồi dựng một cấu trúc mới.

Nhưng trước khi loại trùng, phải xác định chính sách. Hai mục hiện tại cùng một khóa có thể chứa hai hồ sơ khác nhau hoặc hai lịch sử cập nhật. Dựng một `HashSet` mới có thể làm một trong các giá trị tương đương không được thêm; việc đó chưa phải quyết định nghiệp vụ hợp lệ.

Một quy trình điều tra cẩn thận thường gồm:

1. Lưu dấu vết các mục đang có để có thể đối chiếu.
2. Xác định khóa chuẩn hiện tại của từng mục.
3. Nhóm các mục xung đột theo khóa chuẩn.
4. Quyết định giữ, hợp nhất hoặc từ chối theo nghiệp vụ.
5. Tạo khóa ổn định và dựng lại chỉ mục.
6. Chỉ chuyển sang chỉ mục mới khi kiểm tra hoàn tất.

Với một Set đã bị dùng ngoài điều kiện đặc tả, không xem hành vi của mọi phép tìm hoặc xóa là nền tảng chắc chắn để sửa tự động. Cần dựa trên dữ liệu chuẩn và quy trình do ứng dụng kiểm soát.

Dựng một Set mới từ các tham chiếu cũ cũng không tự làm cho phần tử bất biến. Nếu vẫn chia sẻ các đối tượng có thể đổi khóa, lỗi có thể tái diễn.

## 13. Đổi loại collection hoặc bọc chỉ đọc có giải quyết được không?

| Cách thay đổi | Điều nó có thể cung cấp | Điều vẫn phải giữ đúng |
|---|---|---|
| `HashSet` sang `LinkedHashSet` | Thứ tự gặp phần tử theo quy tắc của cấu trúc | Khóa dùng băm và bằng nhau vẫn phải ổn định |
| Chuyển sang `TreeSet` | Tính duy nhất và thứ tự theo so sánh | Dữ liệu dùng so sánh phải ổn định; không phải cách chữa khóa tùy ý đổi |
| Set dựa trên cấu trúc đồng thời | Khả năng phối hợp các thao tác theo hợp đồng riêng | Không tự theo dõi setter hoặc tái lập định danh cho phần tử |
| Wrapper không cho sửa Set | Hạn chế thêm hoặc gỡ qua wrapper | Không tự đóng băng các đối tượng nằm bên trong |
| Sao chép Set | Tạo một cấu trúc chứa khác | Không đương nhiên sao chép hoặc làm bất biến phần tử |
| `final Set<Key> keys` | Không gán biến `keys` sang Set khác | Không ngăn sửa Set hoặc sửa từng Key |

Bảng này là cách suy luận thiết kế, không phải yêu cầu rằng mọi cấu trúc đều có cùng chi tiết nội bộ. Khi đổi loại collection, phải đọc hợp đồng riêng của nó. Một cây sắp xếp và một bảng băm có cơ chế khác nhau, nhưng đều cần dữ liệu dùng lập chỉ mục đáp ứng điều kiện sử dụng.

Chỉ đọc ở cấp container và bất biến ở cấp phần tử là hai mức bảo vệ khác nhau. Ví dụ, không cho gọi `add` không có nghĩa không ai còn tham chiếu để gọi `customer.rename` hoặc sửa một List trong khóa.

Tài liệu [Set Java 17 về các Set không cho sửa](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#unmodifiable) cũng lưu ý rằng thành phần khả biến có thể làm Set hoạt động không nhất quán hoặc làm nội dung có vẻ thay đổi. Vì vậy, `Set.copyOf` không phải công cụ “đóng băng sâu” mọi đối tượng.

Đối với lập trình nhiều luồng, có hai câu hỏi riêng: các thao tác trên cấu trúc có được phối hợp đúng không, và định danh của phần tử có ổn định không. Giải quyết câu hỏi thứ nhất không tự giải quyết câu hỏi thứ hai.

## 14. Kiểm tra theo hợp đồng và theo vòng đời

Bộ kiểm tra tốt nên dùng đối tượng đại diện cho quy tắc nghiệp vụ, thay vì chỉ xác nhận một output thuận mắt của HashSet.

### 14.1. Kiểm tra hai phương thức cùng nhau

Với các đối tượng `a`, `b`, `c` được dự kiến là cùng một định danh, hãy kiểm tra:

- Phản xạ: `a.equals(a)`.
- Đối xứng: kết quả của `a.equals(b)` và `b.equals(a)` thống nhất.
- Bắc cầu khi các quan hệ đầu vào tương ứng đúng.
- So với `null` trả về `false`.
- Khi `a.equals(b)`, hai mã băm bằng nhau.
- Khi dữ liệu nhận diện không đổi, gọi lại mã băm không đổi trong cùng lần chạy.

Không viết kiểm tra bắt buộc hai đối tượng không bằng nhau phải có mã băm khác nhau. Một kiểm tra như vậy từ chối cả những va chạm hợp lệ.

### 14.2. Kiểm tra hành vi thành viên

Với thiết kế khóa hợp lệ, thêm `a` rồi kiểm tra bằng một đối tượng `b` khác tham chiếu nhưng cùng định danh:

```text
add(a)             -> true
contains(b)        -> true
add(b)             -> false
size()             -> 1
remove(b)          -> true
isEmpty()          -> true
```

Đây là kịch bản mong đợi cho trường hợp đơn giản bắt đầu từ Set rỗng, với hai phần tử hợp lệ bằng nhau và không có thay đổi xen vào. Nó không yêu cầu một giá trị hash cụ thể.

### 14.3. Kiểm tra thuộc tính được phép cập nhật

Nếu thiết kế cho phép đổi `displayName` mà không đổi `id`, hãy kiểm tra sau đổi tên:

- Mã băm vẫn giữ nguyên.
- Đối tượng thăm dò theo cùng `id` vẫn bằng nhau.
- `contains` và `remove` vẫn hoạt động theo cùng định danh.
- Thêm một đối tượng cùng `id` không tạo thêm thành viên.

Đây là bài kiểm tra vòng đời như chương trình 3. Nó bảo vệ trực tiếp quyết định rằng tên không phải định danh.

### 14.4. Kiểm tra sự chia sẻ dữ liệu trong khóa

Với một khóa nhận vào collection, thử thay đổi collection gốc sau khi tạo khóa. Nếu mục tiêu là bản chụp, giá trị khóa không được đổi theo đầu vào.

Cũng thử sửa qua getter. Nếu getter trả ra cấu trúc có thể sửa nội dung nhận diện, constructor sao chép một lần có thể vẫn chưa bảo vệ đủ.

Với collection chứa đối tượng khả biến, kiểm tra thêm việc sửa thành phần lồng bên trong. Một bài kiểm tra chỉ thêm phần tử vào List gốc sẽ chưa phát hiện đường sửa qua một đối tượng con.

### 14.5. Kiểm tra luồng đổi định danh và xung đột

Khi nghiệp vụ có đổi mã, ít nhất cần các tình huống:

- Khóa mới chưa có.
- Khóa mới đã có một thành viên khác.
- Khóa cũ không còn ở trong cấu trúc.
- Lỗi phát sinh giữa các bước nếu luồng cập nhật có thể ném lỗi.
- Một đối tượng được lập chỉ mục ở nhiều nơi.
- Các yêu cầu cập nhật có thể chạy đồng thời.

Không viết bài kiểm tra yêu cầu “sửa khóa trong Set thì contains phải false”. Đó là biểu hiện có thể quan sát của một cách sử dụng sai, không phải hành vi đúng mà ứng dụng nên phụ thuộc.

## 15. Những lỗi đọc hiểu và triển khai thường gặp

| Nhận định hoặc cách làm | Vấn đề | Cách suy nghĩ phù hợp |
|---|---|---|
| “Set không cho trùng nên không thể có trạng thái sai” | Bỏ qua điều kiện đối với phần tử và quy tắc bằng nhau | Kiểm tra hợp đồng lớp và vòng đời khóa |
| Chỉ override `equals` khi cần bằng nhau theo giá trị | Cách băm có thể không còn phù hợp | Thiết kế hai phương thức như một cặp |
| `equals` theo `id`, hash theo cả tên | Hai đối tượng cùng id có thể bị băm khác | Chọn thông tin băm nhất quán với bằng nhau |
| Đưa mọi trường vào equals/hash bằng công cụ sinh mã | Trường mô tả hay collection khả biến có thể thành định danh ngoài ý muốn | Chọn ngữ nghĩa trước khi sinh mã |
| Thấy `final` nên cho rằng dữ liệu bất biến | Chỉ cố định tham chiếu ở mức trực tiếp | Kiểm tra đối tượng được tham chiếu |
| Thấy `record` nên bỏ qua List hoặc đối tượng lồng | Record không tự sao chép sâu | Chụp và bảo vệ thành phần nhận diện |
| Gọi setter khóa rồi mới remove | Tra cứu xóa dùng khóa mới | Gỡ khi khóa cũ còn hợp lệ hoặc dùng giá trị mới bất biến |
| Add lại phần tử bị mất dấu | Có thể tạo thêm mục cho cùng đối tượng | Chẩn đoán và phục hồi có kiểm soát |
| Giữ hash hằng để “không mất ngăn” | Quan hệ bằng nhau vẫn có thể đổi; phân bố cũng kém | Giữ ổn định định danh, không chỉ con số hash |
| Bỏ qua kết quả `add` khi cập nhật | Không biết khóa mới đã có | Thiết kế chính sách xung đột |
| Dùng Set chỉ đọc để bảo vệ khóa | Đối tượng bên trong vẫn có thể bị sửa | Bảo vệ ở cấp dữ liệu nhận diện |
| Cho rằng không có ngoại lệ là sử dụng đúng | Một lỗi ngữ nghĩa có thể diễn ra bình thường | Kiểm tra các bất biến của mô hình |

Một thói quen hữu ích là đọc mọi trường tham gia `equals` và đặt câu hỏi: “Ai có thể thay đổi dữ liệu này, qua những tham chiếu nào, vào thời điểm nào?” Nếu câu trả lời vượt ra ngoài ranh giới kiểm soát của Set, nên cân nhắc tách khóa bất biến.

Không chỉ rà setter trong chính lớp. Một mảng, List hoặc đối tượng con được chia sẻ thường là nguồn lỗi khó thấy hơn một phương thức tên rõ ràng như `setId`.

## 16. Bài tập áp dụng

### Bài 1: khách hàng đổi tên

Một lớp có `id` bất biến và `displayName` khả biến. `equals` theo `id`; `hashCode` theo cả `id` và `displayName`.

Hãy chỉ ra lỗi trước khi chạy Set.

**Gợi ý trả lời:** hai đối tượng cùng `id` nhưng khác tên được `equals` xem là bằng nhau, trong khi công thức hash có thể cho kết quả khác. Đây là lỗi hợp đồng, không cần chờ một đối tượng được sửa sau khi thêm.

### Bài 2: tọa độ thay đổi

Một điểm có `x`, `y` khả biến; cả hai phương thức dựa vào `x`, `y`. Chương trình thêm điểm vào HashSet, rồi tăng `x`.

Hãy giải thích vì sao lớp có thể so sánh hợp lý tại từng thời điểm mà luồng sử dụng Set vẫn sai.

**Gợi ý trả lời:** quy tắc nhận diện giá trị đã đổi trong thời gian phần tử nằm trong Set. Nên dùng điểm bất biến và tạo một điểm mới, hoặc quản lý việc gỡ và thêm lại trước–sau thay đổi trong một luồng được kiểm soát.

### Bài 3: hash luôn bằng 0

Hai phần tử ban đầu khác nhau được thêm vào Set. Sau đó một phần tử được sửa để bằng phần tử kia. Hash của cả hai luôn bằng `0`.

Việc hash không đổi có làm quá trình này hợp lệ không?

**Gợi ý trả lời:** không. Điều kiện liên quan đến quan hệ bằng nhau đã bị phá vỡ. Set không tự hợp nhất hai mục khi setter làm chúng trở thành bằng nhau.

### Bài 4: record chứa List

Tạo `record SearchKey(List<String> filters)`. Constructor nhận một `ArrayList`, rồi bên gọi thêm bộ lọc vào List ấy sau khi SearchKey đã nằm trong Set.

Hãy thiết kế một phiên bản dùng bản chụp và nêu giới hạn.

**Gợi ý trả lời:** sao chép bằng `List.copyOf` trong hàm khởi tạo rút gọn có thể phù hợp nếu muốn cấm `null` và thành phần là chuỗi bất biến. Nếu thành phần lồng bên trong khả biến, phải bảo vệ thêm dữ liệu tham gia nhận diện.

### Bài 5: id được cấp sau

Đối tượng được tạo chưa có mã chính thức, được đưa vào Set theo mã đó, rồi mới nhận mã sau khi lưu dữ liệu.

Hãy đề xuất ranh giới vòng đời hợp lý.

**Gợi ý trả lời:** xác lập định danh ổn định trước khi tham gia Set theo định danh ấy, hoặc dùng một khóa ổn định khác có ngữ nghĩa rõ. Không mặc định mã “chưa có” và mã đã cấp là hai trạng thái mà cùng một chỉ mục sẽ tự cập nhật.

### Bài 6: Set có size bằng 1 nhưng contains bằng false

Hãy mô tả dữ liệu cần thu thập và một hành động không nên làm.

**Gợi ý trả lời:** đối chiếu định danh lúc thêm và hiện tại, kiểm tra đường sửa trực tiếp hoặc gián tiếp, xem nơi khác cùng giữ tham chiếu. Không add lại ngay để “chắc chắn đã có”; trước hết phải ngăn đường làm đổi khóa và xác định trạng thái chuẩn.

### Bài 7: đổi khóa sang một giá trị đã tồn tại

Một Set quản lý khách hàng theo mã. Muốn đổi mã của A thành mã hiện có của B.

Hãy nêu quyết định mà Set không thể tự đưa ra.

**Gợi ý trả lời:** giữ A, giữ B, hợp nhất, từ chối hay lưu phiên bản là chính sách nghiệp vụ. Luồng cập nhật phải kiểm tra và xử lý xung đột; việc `add` trả về `false` không tự tạo ra một kết quả cập nhật đúng.

Khi tự thiết kế một khóa mới, hãy viết rõ ba điều ngay trong quyết định thiết kế: dữ liệu nào định nghĩa bằng nhau, dữ liệu ấy có thể đổi bằng những đường nào, và thời điểm nào đối tượng được phép tham gia hoặc rời khỏi các cấu trúc lập chỉ mục.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-based-set-008">So sánh HashSet với LinkedHashSet theo thời gian và bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh HashSet với LinkedHashSet theo thời gian và bộ nhớ

## 1. Chọn theo công việc cần làm, không chỉ theo tên cấu trúc

`HashSet` và `LinkedHashSet` đều là tập dùng băm: loại trùng và hỗ trợ thêm, kiểm tra thành viên, xóa theo giá trị. Khác biệt chính là `LinkedHashSet` duy trì thêm thứ tự chèn để việc duyệt có trình tự được bảo đảm.

Câu hỏi lựa chọn không chỉ là “lớp nào nhanh hơn?” mà gồm:

1. Kết quả có cần giữ thứ tự chèn không?
2. Chương trình chủ yếu tra cứu hay thường xuyên duyệt toàn bộ?
3. Có bao nhiêu phần tử khác nhau và bảng băm có thưa không?
4. Bộ nhớ dành cho cấu trúc có đáng kể so với dữ liệu phần tử không?
5. Đang cần phân tích độ phức tạp hay cần một số đo trên JVM cụ thể?

Nhận định ban đầu:

- Không cần thứ tự và chủ yếu kiểm tra thành viên: `HashSet` thường là lựa chọn trực tiếp, tránh phần liên kết thứ tự không cần thiết.
- Cần giữ thứ tự chèn: `LinkedHashSet` cung cấp đúng hành vi đó.
- Thường xuyên duyệt, đặc biệt khi bảng băm thưa: cần xét lợi thế của cách duyệt `LinkedHashSet` theo số phần tử.
- Cần số byte hoặc số nano giây: phải đo trong điều kiện cụ thể; tên lớp và ký hiệu O lớn chưa đủ.

Đây là các hướng suy luận từ cấu trúc và hợp đồng, không phải khẳng định một lớp thắng mọi phép đo. [HashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html), [LinkedHashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)

## 2. Cấu trúc nào tạo ra sự khác biệt?

### 2.1. Phần chung: bảng băm để nhận diện thành viên

Cả hai dựa vào cơ chế băm để thu hẹp vùng tìm:

```text
giá trị truy vấn
    → hashCode
    → vùng ứng viên trong bảng
    → so sánh bằng nhau
    → tìm thấy hoặc không tìm thấy
```

`LinkedHashSet` không phải một `LinkedList` mà mỗi lần `contains` đều phải đi từ đầu tới cuối. Từ “Linked” ở đây chỉ phần liên kết bổ sung phục vụ thứ tự duyệt; khả năng tìm thành viên vẫn dựa vào băm.

Vì vậy, cả hai đều cần:

- `equals` và `hashCode` nhất quán.
- Dữ liệu nhận diện của phần tử ổn định khi đang trong tập.
- Mã băm phân bố các giá trị phù hợp.
- Chi phí tính mã băm và so sánh bằng nhau có thể kiểm soát.

Đổi `HashSet` thành `LinkedHashSet` không tự sửa một khóa có `hashCode` sai, luôn trả một hằng số hoặc thay đổi sau khi chèn.

### 2.2. Phần thêm của LinkedHashSet: liên kết thứ tự

Có thể hình dung phần thứ tự như một chuỗi liên kết hai chiều giữa các mục:

```text
đầu → mục thứ nhất ↔ mục thứ hai ↔ mục thứ ba ← cuối
```

Liên kết đó giúp duyệt các phần tử đang lưu theo thứ tự chèn. Khi thêm mới hoặc xóa một mục, triển khai cần cập nhật các liên kết tương ứng.

`HashSet` không cần cung cấp chuỗi thứ tự chèn này. Nó không cam kết thứ tự duyệt, kể cả khi một ví dụ nhỏ tình cờ in ra giống thứ tự thêm.

Phần bổ sung của `LinkedHashSet` có hai mặt:

- Tốn tài nguyên để lưu và duy trì thứ tự.
- Cho phép duyệt mà không phải tìm phần tử qua mọi ngăn trống của bảng.

Đó là lý do một cấu trúc có thêm thông tin quản lý vẫn có thể phù hợp hơn cho một kiểu công việc cụ thể.

## 3. So sánh thời gian theo từng thao tác

Đặt:

- `n`: số phần tử đang lưu.
- `C`: capacity, tức số ngăn của bảng nền.
- Chi phí của `hashCode` và `equals` được xét riêng khi khóa phức tạp.

Bảng sau mô tả mô hình thông thường với băm phân bố phù hợp:

| Thao tác | HashSet | LinkedHashSet | Cách hiểu |
| --- | --- | --- | --- |
| `contains` | O(1) kỳ vọng | O(1) kỳ vọng | Cả hai dùng băm, không cần quét hết tập |
| `add` | O(1) kỳ vọng/khấu hao trong chuỗi thêm thông thường | Tương tự, có thêm duy trì liên kết khi thêm mới | Một lần tăng dung lượng vẫn có thể tốn nhiều việc |
| `remove` | O(1) kỳ vọng | O(1) kỳ vọng, có thêm bỏ liên kết thứ tự | Không suy ra một lớp luôn nhanh hơn với mọi khóa |
| Duyệt toàn bộ | O(n + C) theo mô hình của lớp | O(n) theo mô hình của lớp | Khác biệt đáng chú ý khi C lớn hơn nhiều so với n |
| `size` | O(1) | O(1) | Số phần tử được quản lý sẵn |

Các nhận định hằng số của thao tác cơ bản gắn với giả định phân bố băm. Ký hiệu O(1) không phải cam kết thời gian tuyệt đối cho mọi đầu vào hoặc mọi lần gọi. [HashSet: thao tác và iteration](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html), [LinkedHashSet: thao tác và iteration](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)

### 3.1. Tìm thành viên

Với cùng dữ liệu và cấu hình hợp lý, cả hai có cùng mô hình tìm kiếm dựa trên băm. `LinkedHashSet.contains` không cần sửa thứ tự chèn.

Không kết luận rằng thời gian thực tế của hai phương thức bắt buộc bằng nhau vì cùng O(1). Chúng có thể chịu ảnh hưởng của cách bố trí đối tượng, lượng dữ liệu nằm trong bộ nhớ và công việc mà JVM thực hiện.

Ngược lại, cũng không kết luận `LinkedHashSet.contains` là O(n) chỉ vì nhìn thấy một chuỗi liên kết trong mô tả cấu trúc.

### 3.2. Thêm mới, thêm trùng và tăng dung lượng

Thêm phần tử mới khác với thử thêm phần tử đã có:

- Thêm mới cần tạo thành viên mới trong cấu trúc; với `LinkedHashSet`, còn phải đặt nó vào chuỗi thứ tự.
- Thêm trùng trả về `false` và không tăng số phần tử.
- Một lần thêm mới có thể kéo theo tăng dung lượng bảng băm.

Vì vậy, một phép đo gọi `add("java")` nhiều lần sau khi tập đã chứa `"java"` không đại diện cho việc thêm nhiều giá trị khác nhau.

Chi phí tăng dung lượng giải thích cách nói “khấu hao” khi phân tích chuỗi thêm: một số lần làm nhiều việc hơn, nhưng không được coi mọi lần thêm riêng lẻ đều có cùng độ trễ. Cả hai cấu trúc vẫn có phần bảng băm chịu ảnh hưởng của capacity và load factor.

### 3.3. Xóa phần tử

Sau khi tìm được một mục, `LinkedHashSet` cần bỏ nó khỏi thứ tự liên kết. Với liên kết hai chiều và thông tin mục đã tìm, không phải quét cả chuỗi chỉ để tìm mục trước.

Phần công việc bổ sung thường làm chi phí hằng số khác với `HashSet`. Nhưng muốn biết mức khác biệt trên một tải cụ thể phải đo, không thể suy ra một tỷ lệ cố định như “chậm hơn 20%”.

### 3.4. Duyệt là trường hợp phải tách riêng

Với `HashSet`, mô hình duyệt bao gồm số ngăn của bảng và các phần tử đang có. Với `LinkedHashSet`, bộ duyệt đi theo chuỗi phần tử.

Nếu `C` luôn cùng bậc với `n`, cả `n + C` và `n` đều có thể được viết gọn là O(n). Nhưng điều đó không xóa khác biệt khi bảng thực tế rất thưa hoặc các hằng số khác nhau.

Ví dụ khái niệm:

```text
n = 1_000 phần tử
C lớn hơn nhiều so với n

HashSet:
    công việc duyệt liên quan cả vùng bảng và các phần tử

LinkedHashSet:
    công việc duyệt theo chuỗi 1_000 phần tử
```

Đây không phải phép tính số nano giây hoặc số lần truy cập bộ nhớ chính xác. Nó chỉ ra yếu tố cần đưa vào phân tích và thiết kế phép đo.

## 4. Capacity và load factor nối thời gian với bộ nhớ

`size` là số phần tử; `capacity` là số ngăn. Hai đại lượng không giống nhau.

Load factor, hay hệ số tải, liên quan tới mức đầy được cho phép trước khi bảng tăng dung lượng. Các hàm tạo mặc định của hai lớp sử dụng những tham số mặc định tương ứng `16` và `0.75`.

Điều chỉnh cấu hình có sự đánh đổi:

| Lựa chọn | Khía cạnh thời gian | Khía cạnh bộ nhớ |
| --- | --- | --- |
| Capacity ban đầu đủ cho số phần tử dự kiến | Có thể giảm số lần tăng dung lượng khi xây dựng | Cần dành chỗ cho bảng tương ứng |
| Capacity quá lớn | Không tự sửa được băm kém; duyệt HashSet chịu ảnh hưởng của bảng thưa | Có thể giữ nhiều ngăn trống |
| Load factor thấp hơn | Có thể làm bảng thưa hơn; chưa chắc mọi kiểu công việc đều nhanh hơn | Thường cần nhiều không gian bảng hơn |
| Load factor cao hơn | Có thể tăng công việc tìm ứng viên | Có thể giảm không gian bảng tương đối trong một số cấu hình |

`LinkedHashSet` không phải duyệt các ngăn trống theo cùng cách, nhưng bảng thưa vẫn có chi phí lưu trữ. Lợi thế duyệt không có nghĩa capacity lớn là miễn phí về bộ nhớ.

Tham số `new HashSet<>(1_000)` hoặc `new LinkedHashSet<>(1_000)` là gợi ý dung lượng ban đầu, không phải lệnh thêm sẵn 1.000 phần tử hoặc giới hạn tập chỉ chứa được từng đó.

Khi so sánh, cần giữ cấu hình tương đương. Một `HashSet` có bảng quá lớn và một `LinkedHashSet` có bảng vừa đủ không chỉ khác nhau ở liên kết thứ tự. [HashMap: capacity, load factor và tăng dung lượng](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)

## 5. So sánh bộ nhớ: phần tử dữ liệu và cấu trúc quản lý là hai phần khác nhau

### 5.1. Cả hai không sao chép sâu đối tượng phần tử

Nếu đưa cùng một đối tượng khóa vào hai tập, hai tập có thể cùng giữ tham chiếu tới đối tượng đó. Chúng không tự nhân bản toàn bộ dữ liệu của khóa.

Khi hỏi “tập dùng bao nhiêu bộ nhớ?”, cần xác định đang tính:

1. Chính đối tượng `Set`.
2. Bảng nền và các mục quản lý.
3. Các đối tượng phần tử mà tập tham chiếu tới.
4. Mọi đối tượng khác có thể đi tới từ các phần tử đó.
5. Bộ nhớ cấp phát tạm thời trong quá trình xây dựng hay bộ nhớ còn sống sau xây dựng.

Nếu không tách các ý nghĩa này, hai con số cùng mang nhãn “memory” có thể đang đo hai thứ khác nhau.

### 5.2. Một mô hình định tính

Với các mục thông thường, có thể hình dung:

```text
bộ nhớ HashSet
    ≈ đối tượng quản lý
      + bảng tham chiếu theo capacity
      + các mục theo số phần tử
      + đồ thị đối tượng phần tử được tính vào phép đo

bộ nhớ LinkedHashSet
    ≈ những thành phần tương ứng
      + thông tin liên kết thứ tự ở cấu trúc quản lý và từng mục
```

Nếu viết theo bậc tăng trưởng, cả hai đều cần bộ nhớ cho số ngăn và số phần tử: O(C + n), chưa kể kích thước dữ liệu phần tử. Khi `C` tỷ lệ với `n`, cả hai thường được mô tả là O(n).

Cùng O(n) không có nghĩa cùng số byte. `LinkedHashSet` giữ thêm thông tin thứ tự, nên với cấu hình tương đương và các mục thông thường, thường có chi phí cấu trúc lớn hơn.

Nhưng không dùng câu “LinkedHashSet dùng nhiều bộ nhớ hơn” để so hai cấu hình hoàn toàn khác nhau. Một `HashSet` có capacity cực lớn có thể lãng phí bảng nhiều hơn một `LinkedHashSet` được cấu hình vừa đủ. Cần giữ các điều kiện so sánh rõ ràng.

### 5.3. Vì sao thường nhắc tới hai liên kết thêm cho mỗi mục?

Trong triển khai OpenJDK 17, mục thông thường của cấu trúc liên kết nền có thêm các tham chiếu trước và sau so với dạng mục băm thông thường. Phần map nền còn quản lý đầu và cuối chuỗi.

Đó là cơ sở để nói có chi phí bổ sung theo số phần tử. Đây là chi tiết triển khai dùng để giải thích, không phải một định dạng đối tượng được giao diện Java cam kết. [Mục liên kết trong mã nguồn OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/LinkedHashMap.java)

Không chuyển thẳng “hai tham chiếu thêm” thành “luôn thêm đúng 8 byte” hoặc “luôn thêm đúng 16 byte” cho mọi phần tử trên mọi JVM. Số byte còn phụ thuộc:

- Kích thước biểu diễn tham chiếu trên JVM đang dùng.
- Header và cách bố trí trường.
- Căn chỉnh kích thước đối tượng.
- Loại mục thực tế mà triển khai sử dụng khi xử lý va chạm.
- Các thông tin quản lý ở đối tượng nền.

Mô hình hai liên kết đặc biệt nói về mục thông thường. Một số dạng mục xử lý va chạm có cấu trúc khác; không lấy mô hình giản lược để khẳng định mọi đối tượng nội bộ luôn khác nhau đúng cùng số byte.

### 5.4. Một phép tính giả định để hiểu quy mô

Giả sử chỉ xét hai trường tham chiếu bổ sung, mỗi tham chiếu chiếm `r` byte, có `n` mục:

```text
phần kích thước trường bổ sung theo mô hình = n × 2 × r
```

Nếu **giả định** `n = 100_000` và `r = 4`:

```text
100_000 × 2 × 4 = 800_000 byte
```

Đây chỉ là phép tính của mô hình trường tham chiếu với các giả định đã nêu. Nó không phải số đo heap, không bao gồm căn chỉnh, các đối tượng quản lý khác hoặc dữ liệu phần tử, và không phải một mức chênh lệch được bảo đảm.

Điểm học tập là phần chi phí nhỏ ở mỗi mục có thể trở nên đáng kể khi có nhiều mục; số byte cụ thể cần được kiểm tra trên môi trường thực thi.

### 5.5. Dữ liệu phần tử lớn có thể che khác biệt tương đối

Nếu mỗi phần tử là một đối tượng rất nhỏ, chi phí quản lý tập có thể chiếm tỷ trọng đáng chú ý. Nếu mỗi phần tử giữ một đồ thị dữ liệu lớn, tỷ trọng phần liên kết có thể nhỏ hơn trong tổng footprint.

Điều đó không làm chi phí liên kết biến mất. Nó chỉ giải thích vì sao cùng một mức tăng tuyệt đối có thể có tỷ lệ khác nhau khi đổi loại dữ liệu.

Một phép so sánh dùng khóa nhỏ không tự đại diện cho một ứng dụng có khóa lớn và nhiều đối tượng đi kèm.

## 6. Chương trình đầy đủ: cùng giá trị tập, khác bảo đảm thứ tự

Lưu mã vào **HashSetChoiceDemo.java**:

```java
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class HashSetChoiceDemo {
    public static void main(String[] args) {
        List<String> input = List.of("sql", "java", "sql", "http");
        Set<String> hashed = new HashSet<>(input);
        Set<String> linked = new LinkedHashSet<>(input);

        System.out.println("HashSet size: " + hashed.size());
        System.out.println("LinkedHashSet size: " + linked.size());
        System.out.println("same members: " + hashed.equals(linked));
        System.out.println("linked order: " + linked);
        System.out.println("hash contains java: " + hashed.contains("java"));
        System.out.println("linked contains java: " + linked.contains("java"));
        System.out.println("hash add duplicate: " + hashed.add("sql"));
        System.out.println("linked add duplicate: " + linked.add("sql"));
        System.out.println("hash remove java: " + hashed.remove("java"));
        System.out.println("linked remove java: " + linked.remove("java"));
        System.out.println("linked after removal: " + linked);

        hashed.add("java");
        linked.add("java");
        System.out.println("linked after re-add: " + linked);
        System.out.println("same members again: " + hashed.equals(linked));
        System.out.println("same set hash: "
                + (hashed.hashCode() == linked.hashCode()));
    }
}
```

Biên dịch theo mức Java 17 và chạy:

```shell
javac --release 17 -encoding UTF-8 HashSetChoiceDemo.java
java HashSetChoiceDemo
```

Stdout:

```text
HashSet size: 3
LinkedHashSet size: 3
same members: true
linked order: [sql, java, http]
hash contains java: true
linked contains java: true
hash add duplicate: false
linked add duplicate: false
hash remove java: true
linked remove java: true
linked after removal: [sql, http]
linked after re-add: [sql, http, java]
same members again: true
same set hash: true
```

Chương trình chỉ in trực tiếp nội dung của `LinkedHashSet`, vì thứ tự của nó được bảo đảm. Nó không in `HashSet` rồi dựa vào một thứ tự tình cờ để viết stdout.

Các kết quả cho thấy:

- Hai tập có cùng số phần tử và cùng thành viên.
- Cả hai từ chối thêm bản sao của một giá trị đã có.
- Cả hai tìm và xóa theo giá trị.
- `LinkedHashSet` cung cấp trình tự chèn có thể quan sát.
- Xóa rồi thêm lại `java` đưa nó xuống cuối trong tập liên kết.
- Khác thứ tự duyệt không làm hai tập có cùng thành viên khác giá trị tập.

Chương trình kiểm chứng hành vi. Nó không có phép đo thời gian hoặc bộ nhớ, nên không thể đọc các dòng stdout như một kết quả so sánh tốc độ.

## 7. Chuẩn bị dữ liệu tương đương trước khi đo

Một lỗi phổ biến là so hai tập có số phần tử khác nhau, dùng hai tập khóa được tạo theo cách khác nhau hoặc vô tình cho một bên nhận nhiều bản sao hơn.

Để chuẩn bị một phép so sánh có ý nghĩa, cần kiểm soát:

| Yếu tố | Cách giữ tương đương |
| --- | --- |
| Nội dung | Cùng các giá trị khóa khác nhau |
| Kiểu khóa | Cùng lớp, cùng quy tắc equals/hashCode |
| Đối tượng phần tử | Có thể dùng chung các đối tượng đầu vào khi so riêng cấu trúc |
| Cấu hình | Cùng gợi ý capacity và load factor trong từng cặp |
| Trạng thái | Cùng số phần tử trước thao tác |
| Công việc | Cùng chuỗi truy vấn hoặc cùng loại thêm/xóa |
| Kết quả | Kiểm tra không có bên nào làm ít việc vì dữ liệu sai |

Cùng gợi ý capacity không phải một API để đọc số ngăn thực tế. Nếu kết luận cần dựa vào cấu trúc heap thực tế, phải kiểm tra cấu trúc đó bằng công cụ phù hợp.

### Chương trình chuẩn bị hai cặp tập

Lưu mã vào **SetComparisonSetupDemo.java**:

```java
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class SetComparisonSetupDemo {
    private static Set<Integer> fill(Set<Integer> target, Integer[] keys) {
        target.addAll(Arrays.asList(keys));
        return target;
    }

    private static long checksum(Set<Integer> values) {
        long result = 0;
        for (Integer value : values) {
            result += value;
        }
        return result;
    }

    public static void main(String[] args) {
        int count = 1_000;
        Integer[] keys = new Integer[count];
        for (int value = 0; value < count; value++) {
            keys[value] = Integer.valueOf(value);
        }

        Set<Integer> hashDense =
                fill(new HashSet<>(2_048, 0.75f), keys);
        Set<Integer> linkedDense =
                fill(new LinkedHashSet<>(2_048, 0.75f), keys);
        Set<Integer> hashSparse =
                fill(new HashSet<>(262_144, 0.75f), keys);
        Set<Integer> linkedSparse =
                fill(new LinkedHashSet<>(262_144, 0.75f), keys);

        System.out.println("shared input keys: " + keys.length);
        System.out.println("hash dense size: " + hashDense.size());
        System.out.println("linked dense size: " + linkedDense.size());
        System.out.println("hash sparse size: " + hashSparse.size());
        System.out.println("linked sparse size: " + linkedSparse.size());
        System.out.println("dense sets equal: " + hashDense.equals(linkedDense));
        System.out.println("sparse sets equal: " + hashSparse.equals(linkedSparse));
        System.out.println("hash dense checksum: " + checksum(hashDense));
        System.out.println("linked dense checksum: " + checksum(linkedDense));
        System.out.println("hash sparse checksum: " + checksum(hashSparse));
        System.out.println("linked sparse checksum: " + checksum(linkedSparse));
        System.out.println("all contain 999: "
                + (hashDense.contains(999) && linkedDense.contains(999)
                && hashSparse.contains(999) && linkedSparse.contains(999)));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SetComparisonSetupDemo.java
java SetComparisonSetupDemo
```

Stdout:

```text
shared input keys: 1000
hash dense size: 1000
linked dense size: 1000
hash sparse size: 1000
linked sparse size: 1000
dense sets equal: true
sparse sets equal: true
hash dense checksum: 499500
linked dense checksum: 499500
hash sparse checksum: 499500
linked sparse checksum: 499500
all contain 999: true
```

Chương trình tạo một mảng đối tượng khóa chung, rồi đưa cùng các đối tượng đó vào bốn tập. Hai cặp dùng cùng số phần tử nhưng hai gợi ý capacity khác nhau.

Tên `Dense` và `Sparse` biểu đạt chủ đích cấu hình của ví dụ: một gợi ý vừa với quy mô hơn, một gợi ý rất lớn so với 1.000 phần tử. Chúng không phải kết quả đo tỷ lệ đầy hoặc kết quả đọc thuộc tính capacity từ API.

Checksum giúp kiểm tra các vòng duyệt đều xử lý nội dung tương ứng. Tổng từ `0` tới `999` là `499_500`, không phụ thuộc thứ tự duyệt.

Không có số đo thời gian hay số byte trong stdout. Chương trình là bước chuẩn bị và kiểm tra dữ liệu trước phép đo, không phải một benchmark.

Hai chương trình độc lập trong bài đã được biên dịch với `--release 17` và chạy đối chiếu đúng stdout. Tùy chọn này kiểm tra mức ngôn ngữ/API và tạo bytecode tương thích Java 17; nó không khẳng định JVM thực thi chính là JDK 17.

## 8. Đo thời gian: phải tách từng câu hỏi

[OpenJDK JMH](https://github.com/openjdk/jmh) là công cụ dùng để xây dựng và chạy các phép đo trên JVM. Công cụ không thay thế việc chọn đúng tải công việc.

Một kế hoạch đo có thể tách thành:

| Phép đo | Trạng thái và thao tác cần chuẩn bị | Kết luận có thể phục vụ |
| --- | --- | --- |
| Tìm có mặt | Tập đã xây dựng, truy vấn các khóa đang có | Chi phí tra cứu thành công |
| Tìm vắng mặt | Tập đã xây dựng, truy vấn khóa không có | Chi phí tra cứu thất bại |
| Thêm mới | Bảo đảm khóa chưa có và kiểm soát việc tăng dung lượng | Chi phí thay đổi tập theo tải thêm mới |
| Thêm trùng | Khóa đã có | Chi phí thử thêm giá trị trùng |
| Xóa có mặt | Chuẩn bị lại phần tử cho lượt đo tiếp theo theo thiết kế | Chi phí xóa thành công |
| Duyệt | Cùng tập đã xây dựng, xử lý toàn bộ phần tử | Ảnh hưởng của số phần tử và độ thưa |
| Xây dựng | Tạo tập và nạp cùng dữ liệu | Chi phí khởi tạo tổng thể, gồm cấp phát |

Những sai lệch cần tránh:

- Đặt `println` trong phần muốn đo riêng thao tác tập.
- Đo `add` cùng một khóa nhiều lần nhưng gọi đó là thêm mới.
- Xóa một lần rồi đo tiếp việc xóa vắng mặt như thể mọi lượt đều xóa thành công.
- Tạo dữ liệu hoặc xây dựng lại tập trong một phép đo được mô tả là chỉ `contains`.
- Bỏ kết quả mà không thiết kế đúng cách quan sát kết quả trong benchmark.
- Chỉ đo một kích thước hoặc một kiểu khóa rồi khái quát cho mọi ứng dụng.

Trên JVM, khởi động, biên dịch JIT, cấp phát và thu gom rác có thể làm phép đo tùy tiện khó diễn giải. Dùng cấu hình warmup, các lượt đo và các lần chạy độc lập phù hợp theo hướng dẫn của công cụ; vẫn phải xem dữ liệu có đại diện không.

Nếu mục tiêu là đánh giá độ trễ của từng yêu cầu có thể tăng dung lượng, đừng chỉ nhìn một giá trị trung bình đã gộp cả những lần thêm thông thường. Nếu mục tiêu là duyệt nhiều lần trên tập đã xây dựng, hãy tách thời gian xây dựng khỏi phần duyệt.

Một báo cáo cần nêu JDK/JVM, cấu hình chạy, loại khóa, kích thước tập, cấu hình dung lượng, phân bố truy vấn và đơn vị đo. Không ghi “LinkedHashSet luôn nhanh hơn” chỉ vì nó thắng một phép duyệt trên một bảng thưa.

## 9. Đo bộ nhớ: xem cả đồ thị đối tượng, không chỉ vỏ Set

### 9.1. Kích thước nông và footprint khác nhau

Kích thước nông của một đối tượng chỉ nói về chính đối tượng đó, không tự cộng toàn bộ các đối tượng mà nó tham chiếu.

Đo riêng vỏ `HashSet` và `LinkedHashSet` có thể không thể hiện được phần lớn khác biệt, vì bảng và các mục nằm ở những đối tượng khác trong đồ thị.

Muốn hiểu bộ nhớ của cấu trúc, cần xem các đối tượng có thể đi tới từ tập và xác định rõ có tính dữ liệu phần tử không.

### 9.2. Dùng công cụ để quan sát thay vì lấy một con số cố định từ trí nhớ

[OpenJDK JOL](https://github.com/openjdk/jol) hỗ trợ xem bố trí và footprint đối tượng trên JVM. Các thông tin như kích thước tham chiếu và căn chỉnh đối tượng cần được ghi cùng kết quả.

Với dữ liệu đã chuẩn bị trong chương trình thứ hai, một quy trình có thể là:

1. Thiết lập JOL trong một chương trình đo riêng theo hướng dẫn của công cụ.
2. Ghi cấu hình JVM và kiểu đối tượng đang đo.
3. Quan sát đồ thị bắt đầu từ `hashDense` và `linkedDense`, dùng khả năng phân tích đồ thị như `GraphLayout.parseInstance`.
4. Đọc tổng kích thước và phân bố các lớp đối tượng trong mỗi đồ thị, thay vì chỉ một con số tổng.
5. Lặp lại với cặp gợi ý capacity lớn để thấy phần bảng ảnh hưởng thế nào.
6. Giữ rõ cách tính các khóa chung giữa các tập.

`GraphLayout.totalSize()` mô tả tổng footprint của đồ thị được công cụ phân tích. Bài này không cung cấp output JOL giả định làm số đo: cần chạy công cụ trên môi trường thực tế để có kết quả tương ứng. [GraphLayout trong JOL](https://github.com/openjdk/jol/blob/master/jol-core/src/main/java/org/openjdk/jol/info/GraphLayout.java)

### 9.3. Không cộng hai footprint riêng rồi coi đó là tổng bộ nhớ độc quyền

Trong ví dụ, nhiều tập dùng chung các đối tượng khóa. Nếu phân tích từng tập riêng, đồ thị của mỗi tập đều có thể bao gồm các khóa đó.

Cộng thẳng hai tổng riêng có thể đếm các đối tượng dùng chung nhiều lần. Nếu đo cả hai như các gốc trong một đồ thị, cách đếm đối tượng dùng chung lại khác. Cần nói rõ phép đo đang trả lời câu hỏi nào.

Footprint các đối tượng có thể đi tới cũng không tự có nghĩa toàn bộ số byte đó sẽ được giải phóng khi bỏ riêng một tập, vì các khóa hoặc đối tượng khác còn có thể được nơi khác tham chiếu tới.

### 9.4. Bộ nhớ còn sống và lượng cấp phát là hai thước đo khác nhau

Một phép đo sau khi xây dựng có thể cho biết những đối tượng còn sống trong cấu trúc được quan sát. Nó chưa mô tả toàn bộ lượng cấp phát tạm thời trong quá trình tăng dung lượng hoặc tạo khóa truy vấn.

Nếu quan tâm áp lực thu gom rác trong tải thêm/xóa liên tục, cần đánh giá lượng cấp phát và vòng đời đối tượng tương ứng. Nếu quan tâm sức chứa ổn định của một bảng tra cứu ít thay đổi, footprint trạng thái đã xây dựng có thể gần câu hỏi hơn.

Không lấy một phép trừ bộ nhớ trống trước–sau một lần chạy nhỏ rồi coi đó là kích thước chính xác của từng mục. Các công việc khác của JVM và trạng thái thu gom rác làm cách diễn giải đó thiếu cơ sở.

## 10. Các tình huống lựa chọn cụ thể

### 10.1. Chủ yếu kiểm tra mã đã thấy, không cần thứ tự

Nếu chương trình giữ một tập mã và gọi `contains` rất nhiều, ít khi duyệt toàn bộ, không có yêu cầu trình bày theo thứ tự, `HashSet` thường là điểm bắt đầu phù hợp.

Nó không cần chi phí duy trì thứ tự chèn mà bài toán không sử dụng. Vẫn cần kiểm tra quy mô, cấu hình dung lượng và chất lượng khóa.

### 10.2. Loại trùng rồi xuất theo thứ tự gặp đầu tiên

Nếu đầu vào có thứ tự và kết quả cần giữ lần xuất hiện đầu tiên, `LinkedHashSet` cung cấp đúng hành vi trong một cấu trúc.

Chọn `HashSet` rồi cố suy ra thứ tự chèn từ output không đáp ứng hợp đồng. Việc tiết kiệm một phần bộ nhớ không bù được kết quả sai yêu cầu.

### 10.3. Tập được duyệt nhiều và có bảng thưa

`LinkedHashSet` có mô hình duyệt theo số phần tử, nên đáng được đánh giá trong tình huống này. Nhưng cần so cả bộ nhớ của liên kết lẫn bảng và xem có thể cấu hình lại `HashSet` cho hợp lý hơn không.

Không thể chỉ từ mô hình O(n) mà bảo đảm tốc độ cụ thể trên mọi tập dữ liệu. Cách bố trí bộ nhớ và công việc làm với từng phần tử vẫn ảnh hưởng kết quả thực tế.

### 10.4. Có rất nhiều phần tử và bộ nhớ là giới hạn chính

Khi không cần thứ tự, phần liên kết bổ sung có thể là chi phí đáng tránh. Hãy đo cấu trúc với loại khóa thật và cùng điều kiện dung lượng.

Nếu các khóa chiếm phần lớn heap, tối ưu biểu diễn khóa hoặc vòng đời dữ liệu có thể có ảnh hưởng lớn hơn đổi giữa hai loại tập. Điều này phải dựa trên phân tích ứng dụng, không phải một giả định chung cho mọi hệ thống.

## 11. Các điều kiện chung không được bỏ qua

Cả hai cấu trúc đều không tự đồng bộ để nhiều luồng cùng sửa đổi. So thời gian trong một bài toán nhiều luồng cần đưa cơ chế đồng bộ hoặc cấu trúc phù hợp vào thiết kế; không so một cấu trúc được bảo vệ với một cấu trúc bị dùng sai rồi gọi đó là chênh lệch tự nhiên giữa hai lớp.

Cả hai đều cần quy tắc phần tử ổn định. Đổi lớp tập không giải quyết việc sửa trường tham gia `equals`/`hashCode` khi phần tử đang được lưu.

Cả hai đều có thể chịu tác động của phân bố băm kém. Liên kết thứ tự không tự làm các truy vấn băm hết va chạm.

Các điểm này thuộc tính đúng và điều kiện phân tích. Một phép đo nhanh của chương trình sai không phải cơ sở lựa chọn cấu trúc.

## 12. Những kết luận sai thường gặp

| Kết luận | Vì sao chưa đúng | Cách sửa |
| --- | --- | --- |
| Cùng O(1) nên nhanh như nhau | Độ phức tạp không xác định mọi hằng số và chi phí JVM | Đo đúng kiểu thao tác khi cần con số |
| LinkedHashSet chứa linked list nên contains là O(n) | Liên kết phục vụ thứ tự; tìm thành viên vẫn dùng băm | Tách đường tìm với đường duyệt |
| HashSet luôn duyệt nhanh hơn vì ít liên kết hơn | Duyệt còn phụ thuộc capacity | Xét n và C, đặc biệt khi bảng thưa |
| LinkedHashSet luôn nhanh hơn vì duyệt O(n) | Ứng dụng có thể chủ yếu thêm/xóa hoặc tra cứu | So đúng tải công việc |
| LinkedHashSet luôn thêm đúng một số byte cố định trên mọi JVM | Bố trí đối tượng, tham chiếu và loại mục khác nhau | Ghi môi trường và đo footprint |
| Chỉ đo chính đối tượng Set là đủ | Bảng và các mục nằm ở đồ thị phía sau | Xem phạm vi đo bộ nhớ |
| Hai tập cùng size thì bộ nhớ nền chắc như nhau | Capacity và loại mục có thể khác | Giữ cấu hình và quan sát cấu trúc |
| Cộng footprint hai tập dùng chung khóa là tổng heap thực tế | Có thể đếm lặp đối tượng dùng chung | Xác định gốc và quy tắc đếm |
| Một vòng add cùng khóa đo chi phí thêm mới | Hầu hết lượt là thêm trùng | Chuẩn bị trạng thái đúng câu hỏi |
| Đổi sang LinkedHashSet sửa được hashCode kém | Cả hai vẫn dùng băm | Sửa quy tắc khóa và phân bố băm |

## 13. Tự luyện và đối chiếu

### Bài 1: đọc đúng hai công thức duyệt

Có hai tập cùng `n = 2_000`. Một bảng có capacity cùng quy mô với số phần tử, bảng khác có capacity lớn hơn rất nhiều.

1. Vì sao chỉ biết `n` chưa đủ để mô tả công việc duyệt `HashSet`?
2. Theo mô hình tài liệu, capacity ảnh hưởng đường duyệt `LinkedHashSet` như thế nào?
3. Có thể từ đó kết luận chắc chắn một phép duyệt mất bao nhiêu nano giây không?

Đối chiếu: `HashSet` có mô hình O(n + C); `LinkedHashSet` duyệt theo chuỗi phần tử với mô hình O(n); các công thức chưa cung cấp số đo thời gian cụ thể.

### Bài 2: kiểm tra một phép tính bộ nhớ

Một người nói: “Có 100.000 mục, giả sử mỗi tham chiếu 4 byte, hai tham chiếu bổ sung tương ứng 800.000 byte, vậy tổng chênh lệch heap chắc chắn đúng 800.000 byte”.

Phần tính trường theo giả định là đúng. Phần kết luận tổng heap chắc chắn là không đủ cơ sở vì còn căn chỉnh, cấu trúc quản lý, loại mục, cấu hình JVM và phạm vi tính dữ liệu phần tử.

### Bài 3: phát hiện so sánh không tương đương

Một phép đo tạo `HashSet` với gợi ý dung lượng rất lớn, còn `LinkedHashSet` dùng cấu hình mặc định; sau đó nạp cùng 1.000 giá trị và đo duyệt.

Kết quả có thể hữu ích để quan sát hai cấu hình cụ thể. Nhưng nó chưa tách được ảnh hưởng của “loại tập” khỏi ảnh hưởng của “cấu hình bảng”. Muốn so có kiểm soát, cần thêm các cặp cấu hình tương đương và mô tả rõ mục tiêu.

### Bài 4: chọn theo yêu cầu

- Tập mã chỉ cần kiểm tra thành viên, không cần thứ tự: bắt đầu cân nhắc `HashSet`.
- Kết quả loại trùng phải theo lần xuất hiện đầu tiên: `LinkedHashSet` đáp ứng ý nghĩa thứ tự.
- Tập được duyệt nhiều, có dấu hiệu bảng quá thưa: kiểm tra capacity/cấu hình và đánh giá cả hai theo tải duyệt.
- Cần quyết định dựa trên giới hạn heap: đo cùng dữ liệu, cùng cấu hình và ghi rõ cách tính đối tượng dùng chung.

Mỗi lựa chọn cần được giải thích bằng yêu cầu và bằng chứng, không bằng một khẩu hiệu “cấu trúc này luôn tốt hơn”.

## 14. Tài liệu tra cứu

- [HashSet Java 17 — điều kiện hiệu năng và chi phí duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)
- [LinkedHashSet Java 17 — liên kết thứ tự, thao tác và duyệt](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)
- [HashMap Java 17 — dung lượng và hệ số tải](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)
- [OpenJDK 17 LinkedHashMap — chi tiết mục liên kết dùng để giải thích chi phí](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/LinkedHashMap.java)
- [OpenJDK JMH — thiết kế và chạy benchmark trên JVM](https://github.com/openjdk/jmh)
- [OpenJDK JOL — bố trí và footprint đối tượng](https://github.com/openjdk/jol)


</details>

- [Quay lại đầu trang](#back-to-top)
