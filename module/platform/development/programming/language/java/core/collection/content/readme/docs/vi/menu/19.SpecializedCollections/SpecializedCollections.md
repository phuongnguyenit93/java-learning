<a id="back-to-top"></a>

# Cấu trúc chuyên biệt

## Menu
- [EnumSet: tập hợp enum và biểu diễn gọn](#specialized-collections-001)
- [EnumMap: ánh xạ với khóa enum](#specialized-collections-002)
- [WeakHashMap: khóa yếu và tác động của thu gom rác](#specialized-collections-003)
- [IdentityHashMap: so sánh khóa theo tham chiếu](#specialized-collections-004)
- [BitSet và khác biệt với Set&lt;Integer&gt;](#specialized-collections-005)
- [Vector và Stack: hợp đồng cũ và lựa chọn thay thế](#specialized-collections-006)
- [Hashtable, Dictionary và Properties trong mã đời cũ](#specialized-collections-007)
- [Enumeration và chuyển đổi sang Iterator hoặc List](#specialized-collections-008)

## <a id="specialized-collections-001">EnumSet: tập hợp enum và biểu diễn gọn</a>

<details>
<summary>Xem chi tiết</summary>

# EnumSet: tập hợp enum và biểu diễn gọn

Một enum định nghĩa một tập hằng hữu hạn, chẳng hạn các chế độ READ, WRITE, EXECUTE, DELETE và SHARE. Một đối tượng có thể chọn không hằng nào, một vài hằng hoặc tất cả. Khi cần giữ một tập con như vậy, EnumSet là cấu trúc chuyên biệt phù hợp để xem xét.

`EnumSet` vẫn có ngữ nghĩa Set: không giữ bản sao trùng lặp và hỗ trợ kiểm tra thành viên, thêm, xóa, hợp, giao, hiệu. Điểm chuyên biệt là mọi phần tử thuộc cùng một kiểu enum; tập hợp biết miền hằng có thể xuất hiện nên có thể biểu diễn tư cách thành viên bằng các bit.

Bài dùng Java 17. Mục tiêu là hiểu mô hình tập con hữu hạn, dùng các factory đúng, giải thích sự gọn của biểu diễn mà không gán số byte cố định, và biết giới hạn về thay đổi dữ liệu, thứ tự và đồng thời.

## 1. Từ một hằng enum đến một tập con

Giả sử có khai báo minh họa:

~~~java
enum Permission {
    READ, WRITE, EXECUTE, DELETE, SHARE
}
~~~

Một biến `Permission` giữ một hằng trong miền ấy. Một `EnumSet<Permission>` giữ tập con của miền: ví dụ READ và DELETE.

~~~text
miền hằng khả dụng:
{READ, WRITE, EXECUTE, DELETE, SHARE}

một tập con:
{READ, DELETE}

tập rỗng:
{}
~~~

Gọi miền hằng khả dụng là U. Nếu U có 5 hằng, số tập con khác nhau là 2^5. Không cần tạo một enum khác cho từng tổ hợp; một EnumSet biểu diễn được lựa chọn tổ hợp trên cùng miền.

### 1.1. Vì sao không dùng nhiều boolean?

Năm boolean riêng có thể dùng được trong một lớp nhỏ, nhưng chúng không tự cung cấp phép hợp/giao, duyệt các lựa chọn đang bật hoặc truyền một tập lựa chọn qua API.

EnumSet đặt những thao tác ấy vào một cấu trúc tập hợp thống nhất. Tên hằng cho biết ý nghĩa; kiểu phần tử giúp trình biên dịch ngăn việc đưa một hằng của enum khác vào cùng tập.

Điều này không có nghĩa mọi boolean đều phải đổi thành EnumSet. Nếu dữ liệu chỉ có một thuộc tính độc lập, boolean vẫn rõ ràng. EnumSet có giá trị khi nhiều hằng cùng một miền được chọn theo dạng tập con.

### 1.2. Một kiểu enum cho mỗi tập

`EnumSet<Permission>` không phải một túi chứa mọi enum bất kỳ. Miền phần tử được xác định khi tạo tập, dù tập đang rỗng.

Các kiểu enum khác nhau có thể có hằng cùng tên READ nhưng vẫn không trở thành cùng miền. Giữ generic đúng giúp tránh trộn chúng bằng raw type hoặc ép kiểu không an toàn.

EnumSet là lớp trừu tượng; mã ứng dụng tạo qua các phương thức factory, không dùng `new EnumSet<>()`. Có thể công bố biến bằng giao diện Set nếu API chỉ cần thao tác tập hợp:

~~~java
Set<Permission> enabled =
        EnumSet.of(Permission.READ, Permission.WRITE);
~~~

Đây là đoạn minh họa một phần. Đối tượng vẫn là EnumSet, nên thứ tự duyệt và các đặc điểm của nó vẫn có hiệu lực. [Java 17: EnumSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/EnumSet.html)

## 2. Biểu diễn gọn bằng tư cách thành viên

### 2.1. Mỗi hằng là một vị trí

Với năm hằng, có thể lập mô hình:

~~~text
vị trí:   4       3       2        1      0
hằng:   SHARE   DELETE  EXECUTE   WRITE  READ

tập {READ, DELETE}:
bit:      0       1       0        0      1
~~~

Bit 1 nghĩa là hằng tương ứng thuộc tập; bit 0 nghĩa là không thuộc. Sơ đồ là mô hình giải thích, không phải định dạng mà toString của EnumSet in ra.

Không cần cấp một nút riêng để ghi lại từng lần thêm một hằng đã có. Tư cách thành viên chỉ là có hoặc không, phù hợp với ngữ nghĩa không trùng của Set.

### 2.2. Thao tác tập hợp nối với phép toán bit

Nếu hai tập cùng miền có các vectơ bit tương ứng:

~~~text
A = {READ, DELETE} -> 01001
B = {READ, WRITE}  -> 00011

hợp A ∪ B:        01011 -> READ, WRITE, DELETE
giao A ∩ B:       00001 -> READ
hiệu A \ B:       01000 -> DELETE
~~~

Đây là lý do cấu trúc chuyên biệt có thể xử lý hiệu quả các phép tập hợp trên cùng miền. Ứng dụng vẫn gọi addAll, retainAll và removeAll; không cần tự viết các mặt nạ bit.

Phần bù cần giới hạn trong miền enum:

~~~text
miền 5 hằng:      11111
A:               01001
U \ A:           10110 -> WRITE, EXECUTE, SHARE
~~~

Không được hiểu phần bù là mọi bit của một số nguyên hoặc mọi giá trị Java khác. Miền enum quyết định những thành viên có ý nghĩa.

### 2.3. Gọn không có nghĩa toàn bộ đối tượng chỉ vài byte

API mô tả EnumSet dùng vectơ bit. Trong mã factory của OpenJDK 17, miền không quá 64 hằng được chọn triển khai RegularEnumSet; miền lớn hơn được chọn JumboEnumSet. Quyết định này dựa vào số hằng của kiểu enum, không phải chỉ số thành viên hiện đang được chọn. [OpenJDK 17: EnumSet.java](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/util/EnumSet.java)

Hai điểm cần giữ:

- EnumSet không bị giới hạn tối đa 64 hằng enum.
- Không nên dựa vào tên lớp triển khai riêng để quyết định nghiệp vụ hoặc ép kiểu.

Mô hình bit cho thấy một miền lớn hơn có thể cần nhiều nhóm bit để biểu diễn. Nhưng tổng bộ nhớ còn có đối tượng tập, thông tin kiểu và các tham chiếu liên quan; không được nói “cả EnumSet luôn chỉ tốn 8 byte”.

Cũng không phải mỗi tập tạo lại toàn bộ đối tượng hằng enum. Các hằng enum là những đối tượng đã có của kiểu; cấu trúc đang biểu diễn những hằng nào thuộc tập.

Muốn có số byte hoặc thời gian chính xác cần môi trường và phép đo cụ thể. Bài không dùng các kết quả in tên thành viên để suy ra tốc độ hay dung lượng bộ nhớ.

## 3. Tạo tập theo ý định

| Factory | Ý nghĩa |
|---|---|
| noneOf(Permission.class) | Tập rỗng nhưng biết rõ miền Permission |
| allOf(Permission.class) | Tất cả hằng của Permission |
| of(READ, WRITE, ...) | Chọn rõ các hằng |
| range(WRITE, DELETE) | Các hằng từ WRITE đến DELETE theo thứ tự khai báo, gồm hai đầu |
| copyOf(enumSet) | Tập mới cùng kiểu và cùng thành viên ban đầu |
| copyOf(collection) | Suy miền theo hợp đồng nguồn Collection |
| complementOf(enumSet) | Tập mới gồm các hằng cùng miền chưa có trong nguồn |

Factory tạo ra EnumSet có thể thay đổi thành viên. Tên `of` không làm nó có cùng tính chất bất biến như `Set.of`.

### 3.1. Tập rỗng phải biết kiểu

Đoạn minh họa:

~~~java
EnumSet<Permission> selected =
        EnumSet.noneOf(Permission.class);
~~~

Dù chưa có phần tử nào để nhìn vào, tập đã biết miền Permission. Vì vậy sau đó có thể thêm READ, hoặc tạo phần bù là toàn bộ Permission.

Không có dạng `EnumSet.of()` không đối số để thay cho noneOf. Dùng class token là cách nói rõ miền khi chưa chọn hằng nào.

### 3.2. Of không giữ trùng và không giữ thứ tự chèn

`EnumSet.of(DELETE, READ, READ)` chỉ có READ và DELETE. Thêm READ một lần nữa trả false vì tập không đổi.

Factory này khác `Set.of` ở việc đối số trùng không bị dùng như lỗi khai báo tập bất biến. Tuy nhiên, không nên đưa trùng để cố ghi lại số lần hoặc mức ưu tiên. Nếu cần số lần, bài toán không còn chỉ là một Set.

### 3.3. Range gồm cả hai đầu

Với thứ tự READ, WRITE, EXECUTE, DELETE, SHARE:

~~~text
range(WRITE, DELETE) -> WRITE, EXECUTE, DELETE
range(READ, READ)    -> READ
~~~

Range theo thứ tự khai báo, không theo tên chữ cái và không theo trường số riêng bên trong enum. Truyền đầu đứng sau cuối ném IllegalArgumentException.

Nó tạo tập thành viên ban đầu, không tạo một cửa sổ tự động cập nhật theo một tập nguồn khác. Đừng mang trực giác view của một số cấu trúc có thứ tự sang factory range của EnumSet.

## 4. CopyOf và trường hợp nguồn rỗng

### 4.1. Một EnumSet rỗng vẫn giữ miền

CopyOf nhận một EnumSet có thể sao chép ngay cả khi nguồn rỗng. Miền phần tử đã là thông tin của tập.

Trường hợp Collection cần đọc kỹ: nếu đối tượng nguồn thực sự là một EnumSet, factory Collection xử lý như sao chép EnumSet. Điều đó vẫn đúng khi biến nguồn được khai báo bằng Collection.

Ví dụ minh họa:

~~~java
Collection<Permission> source =
        EnumSet.noneOf(Permission.class);

EnumSet<Permission> copy = EnumSet.copyOf(source);
copy.add(Permission.READ);
~~~

Không nên kết luận chỉ từ kiểu biến Collection rằng nguồn rỗng luôn thất bại.

### 4.2. Collection rỗng thông thường không cho biết miền để suy

Với một List rỗng không phải EnumSet, `EnumSet.copyOf(list)` ném IllegalArgumentException. Factory không có phần tử để suy miền mới theo hợp đồng của nó.

Đây không phải việc “EnumSet không hỗ trợ tập rỗng”. NoneOf và copy từ EnumSet rỗng đã cho thấy tập rỗng hoàn toàn hợp lệ.

Nếu API biết kiểu và chấp nhận một Collection có thể rỗng, có thể tạo noneOf trước rồi addAll:

~~~java
EnumSet<Permission> result =
        EnumSet.noneOf(Permission.class);
result.addAll(input);
~~~

Đoạn này là minh họa với input đã có, không null và chỉ chứa phần tử hợp lệ. Nó xác định miền độc lập với việc input có thành viên hay không.

Hợp đồng copyOf(Collection) phân biệt rõ nguồn EnumSet và nguồn thông thường rỗng. [Java 17: các factory copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/EnumSet.html)

### 4.3. Sao chép độc lập về thành viên

CopyOf và clone tạo tập mới; thêm hoặc xóa ở tập nguồn về sau không tự làm đổi thành viên của bản sao.

Các phần tử vẫn là những hằng enum tương ứng, không phải các bản clone mới. Nếu một enum được thiết kế có trạng thái có thể thay đổi bên trong, sao chép tập không tự sao chép trạng thái đó.

Trong thiết kế thông thường, nên để ý nghĩa các hằng và dữ liệu đi kèm ổn định. Cấu trúc tập hợp không tự quản lý tính bất biến của mọi đối tượng mà nó tham chiếu.

## 5. Duyệt theo thứ tự khai báo

EnumSet duyệt các thành viên theo thứ tự tự nhiên của enum, tức thứ tự hằng được khai báo. Không phải thứ tự thêm và không phải thứ tự chữ cái.

Với Permission trong bài:

~~~text
of(SHARE, WRITE, READ)
duyệt -> READ, WRITE, SHARE
~~~

EnumSet không phải SortedSet và không nhận một comparator riêng để đổi thứ tự duyệt của nó. Nếu cần một thứ tự hiển thị khác, hãy tạo một danh sách kết quả và sắp xếp theo yêu cầu, thay vì giả định đổi tên hằng sẽ thay cách tổ chức mong muốn.

Thứ tự khai báo cũng là cơ sở cho compareTo và ordinal của Enum. Ordinal bắt đầu từ 0, phản ánh vị trí khai báo; API mô tả nó chủ yếu phục vụ các cấu trúc chuyên biệt như EnumSet và EnumMap. [Java 17: Enum](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Enum.html)

### 5.1. Không tự dùng ordinal như mã lưu trữ bền vững

Nếu thêm một hằng vào giữa hoặc đổi thứ tự khai báo, các vị trí có thể thay đổi. Một số bit tự lưu ra bên ngoài theo ordinal có thể được diễn giải sai sau thay đổi phiên bản.

Ứng dụng cần định dạng lưu trữ hoặc trao đổi phải thiết kế định danh ổn định phù hợp. Không biến mô hình nội bộ của EnumSet thành giao thức tự chế chỉ vì nó gọn trong một phiên bản enum.

Tên hằng hoặc mã tường minh cũng cần chính sách tương thích khi đổi tên hoặc thêm giá trị; điểm chính là tách thiết kế dữ liệu ngoài hệ thống khỏi vị trí khai báo trong mã nguồn.

### 5.2. AllOf và complementOf phụ thuộc miền hiện tại

Khi thêm một hằng mới vào enum rồi chạy phiên bản mới, allOf chứa cả hằng mới. ComplementOf cũng xét miền mới, không chỉ danh sách hằng từng tồn tại trong phiên bản cũ.

Nếu một tập biểu diễn các khả năng được cho phép, “tất cả trừ vài hằng” khác với “chỉ các hằng đã liệt kê”. Hãy chọn factory theo chính sách nghiệp vụ, không chỉ theo độ ngắn của mã.

Đây là hệ quả của mô hình tập con hữu hạn: thay U có thể thay kết quả U trừ A ngay cả khi danh sách A giữ nguyên.

## 6. Các phép tập hợp và việc thay đổi đối tượng nhận

Các thao tác quen thuộc từ Set vẫn có ý nghĩa:

| Phương thức | Ngữ nghĩa |
|---|---|
| add(e) | Thêm thành viên nếu chưa có |
| remove(e) | Loại thành viên nếu có |
| contains(e) | Kiểm tra một thành viên |
| addAll(B) | Biến tập nhận A thành A hợp B |
| retainAll(B) | Biến tập nhận A thành A giao B |
| removeAll(B) | Biến tập nhận A thành A trừ B |
| containsAll(B) | Hỏi B có là tập con của A không |
| clear() | Loại toàn bộ thành viên |

Ba phép cập nhật hàng loạt sửa chính đối tượng nhận. Nếu muốn giữ A và B, sao chép A rồi áp thao tác lên bản sao.

Ví dụ minh họa:

~~~java
EnumSet<Permission> allowed =
        EnumSet.of(Permission.READ, Permission.DELETE);
EnumSet<Permission> required =
        EnumSet.of(Permission.READ, Permission.WRITE);

EnumSet<Permission> missing = EnumSet.copyOf(required);
missing.removeAll(allowed);
~~~

Missing là WRITE: những yêu cầu chưa nằm trong allowed. Nếu làm ngược `allowed.removeAll(required)`, kết quả sẽ là các phần cho phép không nằm trong yêu cầu, một câu hỏi khác.

Phép hiệu không có tính đổi chỗ. Đặt tên biến giúp người đọc hiểu tập nào đang bị trừ.

### 6.1. ContainsAll hỏi đủ, không hỏi có giao hay không

Nếu required có READ và WRITE, allowed chỉ có READ thì containsAll trả false. Có một thành viên chung chưa đủ.

Nếu muốn hỏi “có ít nhất một phần chung không?”, đó là câu hỏi giao khác rỗng. Không thay thế nó bằng containsAll.

### 6.2. Ngữ nghĩa Set vẫn được giữ

EnumSet có thể bằng một Set triển khai khác nếu có cùng các thành viên theo hợp đồng Set. Không cần hai đối tượng có cùng tên lớp để tập hợp bằng nhau.

Ngược lại, thứ tự in khác nhau không làm hai tập khác nội dung. Đừng so các chuỗi toString để thay cho equals của Set. [Java 17: Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html)

## 7. Null, tính có thể sửa và quyền sở hữu

### 7.1. Không cho thêm null

Thêm null vào EnumSet ném NullPointerException. Contains(null) và remove(null) hoạt động bình thường và không tìm thấy phần tử null.

Điều này giúp phân biệt “không chọn hằng nào” với “thêm một phần tử null”. Muốn biểu diễn không có lựa chọn, dùng tập rỗng.

Cũng không gán null cho cả biến EnumSet rồi mong các thao tác tập rỗng vẫn dùng được. Biến tham chiếu null và một tập rỗng có miền là hai trạng thái khác nhau.

### 7.2. Final không khóa thành viên

Một trường final tham chiếu EnumSet ngăn gán lại tham chiếu, không ngăn add hoặc remove trên đối tượng ấy. Nếu công bố nó trực tiếp, bên nhận có thể thay đổi thành viên khi có quyền gọi các phương thức sửa.

Nếu mục đích chỉ đọc, có thể trả một lớp bọc không sửa được. Nhưng lớp bọc và bản sao cần được phân biệt.

### 7.3. View chỉ đọc và bản chụp độc lập

Ví dụ:

~~~java
Set<Permission> view = Collections.unmodifiableSet(owner);

Set<Permission> snapshot =
        Collections.unmodifiableSet(EnumSet.copyOf(owner));
~~~

View không cho bên nhận sửa qua view, nhưng vẫn phản ánh thay đổi thành viên của owner. Snapshot bọc một bản sao nên không theo các lần add/remove về sau trên owner.

Cần giữ quyền kiểm soát đối tượng bản sao bên trong nếu muốn nó không tiếp tục bị sửa qua một tham chiếu khác. Đây là vấn đề quyền sở hữu dữ liệu, không chỉ tên phương thức unmodifiable.

Lớp bọc unmodifiableSet là một góc nhìn chỉ đọc qua chính lớp bọc, không tự tách khỏi nguồn. [Java 17: Collections.unmodifiableSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableSet(java.util.Set))

## 8. Iterator và truy cập đồng thời

Iterator của EnumSet là loại nhất quán yếu: không ném ConcurrentModificationException và có thể có hoặc không phản ánh thay đổi trong lúc duyệt theo hợp đồng của lớp.

Không suy ra từ đó rằng EnumSet an toàn cho nhiều luồng. Nó không tự đồng bộ hóa; nếu nhiều luồng cùng truy cập và có sửa đổi, cần cơ chế bảo vệ bên ngoài phù hợp. [Java 17: chính sách iterator của EnumSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/EnumSet.html)

Hai kết luận nên tránh:

- “Không có ngoại lệ nên cập nhật cạnh tranh đúng.”
- “Iterator chắc chắn là ảnh chụp đầy đủ và bất biến của mọi EnumSet.”

Ví dụ chương trình không cố dự đoán chính xác một lịch sửa đổi trong lúc duyệt. Nó dùng iterator.remove để minh họa xóa một thành viên theo lượt duyệt đơn luồng rõ ràng.

Nếu cần đọc một bộ lựa chọn ổn định, có thể dùng bản sao được tạo trong trạng thái được bảo vệ đúng. Việc sao chép giữa lúc nguồn bị sửa không có cơ chế phối hợp không tự giải quyết vấn đề.

## 9. Khi nào nên chọn EnumSet?

EnumSet là ứng viên tự nhiên khi dữ liệu là tập con của một enum cụ thể và cần thao tác thành viên hoặc phép tập hợp.

Nó không thay thế mọi Set:

| Yêu cầu | Điều cần cân nhắc |
|---|---|
| Tập hằng của cùng một enum | EnumSet phù hợp để xem xét |
| Mã hoặc tên được thêm động lúc chạy | Không phải miền enum hữu hạn đã khai báo |
| Giữ thứ tự chèn | EnumSet duyệt theo thứ tự khai báo, cần cấu trúc khác nếu thứ tự chèn quan trọng |
| Sắp theo comparator riêng | EnumSet không có comparator tùy chọn |
| Lưu số lần hoặc nhiều bản sao | Set không biểu diễn tần suất |
| Chia sẻ nhiều luồng có cập nhật | Cần thiết kế đồng thời, không suy từ biểu diễn bit |

Biểu diễn chuyên biệt thường tạo lợi thế về cấu trúc và thao tác. Không dùng câu “luôn nhanh hơn mọi Set” như một bảo đảm cho mọi chương trình. Tổng chi phí còn phụ thuộc dữ liệu, tần suất sao chép, cách dùng và môi trường.

Một enum có hơn 64 hằng vẫn dùng được. Chương trình cố ý tạo miền 65 hằng rồi chỉ chọn ba hằng ở các vị trí khác nhau để bác bỏ nhầm lẫn “EnumSet chỉ hỗ trợ 64 phần tử enum”.

## 10. Chương trình Java đầy đủ

Lưu mã sau vào `EnumSetCompactDemo.java`. Các enum nằm trong lớp để tệp có thể biên dịch trực tiếp mà không cần tạo thêm tệp nguồn.

Chương trình kiểm chứng ngữ nghĩa thành viên, thứ tự duyệt, các phép tập hợp, sao chép nguồn rỗng, null, quyền sửa và miền lớn. Nó không truy cập các trường riêng của JDK hoặc đo số byte.

~~~java
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class EnumSetCompactDemo {
    enum Permission {
        READ, WRITE, EXECUTE, DELETE, SHARE
    }

    enum Wide {
        F00, F01, F02, F03, F04, F05, F06, F07, F08, F09,
        F10, F11, F12, F13, F14, F15, F16, F17, F18, F19,
        F20, F21, F22, F23, F24, F25, F26, F27, F28, F29,
        F30, F31, F32, F33, F34, F35, F36, F37, F38, F39,
        F40, F41, F42, F43, F44, F45, F46, F47, F48, F49,
        F50, F51, F52, F53, F54, F55, F56, F57, F58, F59,
        F60, F61, F62, F63, F64
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Unexpected enum set result");
        }
    }

    private static <E extends Enum<E>> EnumSet<E> copyKnownType(
            Class<E> type, Collection<E> source) {
        EnumSet<E> result = EnumSet.noneOf(type);
        result.addAll(source);
        return result;
    }

    public static void main(String[] args) {
        EnumSet<Permission> empty = EnumSet.noneOf(Permission.class);
        System.out.println("empty=" + empty);

        EnumSet<Permission> selected =
                EnumSet.of(Permission.DELETE, Permission.READ, Permission.READ);
        boolean duplicateAdded = selected.add(Permission.READ);
        require(selected.size() == 2 && !duplicateAdded);
        System.out.println("selected=" + selected
                + ", duplicateAdded=" + duplicateAdded);
        System.out.println("order=" + EnumSet.of(
                Permission.SHARE, Permission.WRITE, Permission.READ));
        System.out.println("all=" + EnumSet.allOf(Permission.class));
        System.out.println("complement=" + EnumSet.complementOf(selected));

        EnumSet<Permission> range =
                EnumSet.range(Permission.WRITE, Permission.DELETE);
        System.out.println("range=" + range);
        boolean reverseRangeRejected = false;
        try {
            EnumSet.range(Permission.DELETE, Permission.WRITE);
        } catch (IllegalArgumentException exception) {
            reverseRangeRejected = true;
        }
        require(reverseRangeRejected);
        System.out.println("range/reversedRejected=" + reverseRangeRejected);

        EnumSet<Permission> required =
                EnumSet.of(Permission.READ, Permission.WRITE);
        EnumSet<Permission> union = EnumSet.copyOf(selected);
        union.addAll(required);
        EnumSet<Permission> intersection = EnumSet.copyOf(selected);
        intersection.retainAll(required);
        EnumSet<Permission> difference = EnumSet.copyOf(selected);
        difference.removeAll(required);
        require(union.equals(EnumSet.of(
                Permission.READ, Permission.WRITE, Permission.DELETE)));
        require(intersection.equals(EnumSet.of(Permission.READ)));
        require(difference.equals(EnumSet.of(Permission.DELETE)));
        System.out.println("algebra/union=" + union);
        System.out.println("algebra/intersection=" + intersection);
        System.out.println("algebra/difference=" + difference);
        System.out.println("algebra/containsRequired="
                + selected.containsAll(required));

        EnumSet<Permission> copy = EnumSet.copyOf(selected);
        selected.add(Permission.SHARE);
        require(!copy.contains(Permission.SHARE));
        System.out.println("copy/source=" + selected + ", copy=" + copy);

        boolean emptyListRejected = false;
        try {
            EnumSet.copyOf(List.<Permission>of());
        } catch (IllegalArgumentException exception) {
            emptyListRejected = true;
        }
        require(emptyListRejected);
        System.out.println("copy/emptyListRejected=" + emptyListRejected);
        Collection<Permission> emptyEnumSet =
                EnumSet.noneOf(Permission.class);
        EnumSet<Permission> fromEnumSet = EnumSet.copyOf(emptyEnumSet);
        fromEnumSet.add(Permission.READ);
        System.out.println("copy/emptyEnumSetAsCollection=" + fromEnumSet);
        System.out.println("copy/knownTypeEmpty="
                + copyKnownType(Permission.class, List.<Permission>of()));

        boolean nullRejected = false;
        try {
            selected.add(null);
        } catch (NullPointerException exception) {
            nullRejected = true;
        }
        require(nullRejected);
        System.out.println("null/addRejected=" + nullRejected
                + ", contains=" + selected.contains(null)
                + ", remove=" + selected.remove(null));

        EnumSet<Permission> removable = EnumSet.copyOf(range);
        Iterator<Permission> iterator = removable.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == Permission.EXECUTE) {
                iterator.remove();
            }
        }
        require(removable.equals(EnumSet.of(
                Permission.WRITE, Permission.DELETE)));
        System.out.println("iterator/afterRemove=" + removable);
        System.out.println("equality/hashSet=" + copy.equals(
                new HashSet<>(List.of(Permission.READ, Permission.DELETE))));

        EnumSet<Permission> owner = EnumSet.of(Permission.READ);
        Set<Permission> view = Collections.unmodifiableSet(owner);
        Set<Permission> snapshot =
                Collections.unmodifiableSet(EnumSet.copyOf(owner));
        owner.add(Permission.WRITE);
        System.out.println("ownership/view=" + view + ", snapshot=" + snapshot);
        boolean viewAddRejected = false;
        try {
            view.add(Permission.DELETE);
        } catch (UnsupportedOperationException exception) {
            viewAddRejected = true;
        }
        require(viewAddRejected);
        System.out.println("ownership/addRejected=" + viewAddRejected);

        EnumSet<Wide> wide = EnumSet.of(Wide.F64, Wide.F00, Wide.F63);
        require(wide.size() == 3 && wide.contains(Wide.F64));
        require(EnumSet.allOf(Wide.class).size() == 65);
        require(EnumSet.complementOf(wide).size() == 62);
        System.out.println("wide/members=" + wide + ", size=" + wide.size());
        System.out.println("wide/universe=" + Wide.values().length
                + ", complementSize=" + EnumSet.complementOf(wide).size());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 EnumSetCompactDemo.java
java EnumSetCompactDemo
~~~

Stdout của lần chạy kiểm chứng:

~~~text
empty=[]
selected=[READ, DELETE], duplicateAdded=false
order=[READ, WRITE, SHARE]
all=[READ, WRITE, EXECUTE, DELETE, SHARE]
complement=[WRITE, EXECUTE, SHARE]
range=[WRITE, EXECUTE, DELETE]
range/reversedRejected=true
algebra/union=[READ, WRITE, DELETE]
algebra/intersection=[READ]
algebra/difference=[DELETE]
algebra/containsRequired=false
copy/source=[READ, DELETE, SHARE], copy=[READ, DELETE]
copy/emptyListRejected=true
copy/emptyEnumSetAsCollection=[READ]
copy/knownTypeEmpty=[]
null/addRejected=true, contains=false, remove=false
iterator/afterRemove=[WRITE, DELETE]
equality/hashSet=true
ownership/view=[READ, WRITE], snapshot=[READ]
ownership/addRejected=true
wide/members=[F00, F63, F64], size=3
wide/universe=65, complementSize=62
~~~

Mã đã được biên dịch với `--release 17` và chạy thành công. Tùy chọn release xác định mức API và mã đích tương thích; không khẳng định JVM thực thi là đúng bản JDK 17.

## 11. Đọc kết quả theo từng ý nghĩa

### 11.1. Thành viên và thứ tự

Selected chỉ có READ và DELETE dù READ xuất hiện lặp trong factory. Add READ trả false. Dòng order có thứ tự READ, WRITE, SHARE dù đối số được đưa theo SHARE, WRITE, READ.

Các kết quả ấy thuộc ngữ nghĩa tập và thứ tự khai báo, không phải một phép sắp theo tên hoặc lịch sử thêm.

### 11.2. Phần bù, range và các phép toán

Complement gồm WRITE, EXECUTE, SHARE vì đó là phần của miền năm hằng chưa có trong selected ban đầu. Range WRITE đến DELETE gồm cả WRITE và DELETE.

Phép hợp, giao và hiệu đều được tính trên bản sao nên không phá tập nguồn trước khi nó còn được dùng để đối chiếu. ContainsRequired trả false vì selected thiếu WRITE.

### 11.3. Sao chép và nguồn rỗng

Source được thêm SHARE sau khi copy; copy vẫn chỉ có READ và DELETE. List rỗng bị copyOf từ chối, còn đối tượng EnumSet rỗng được khai báo qua Collection vẫn sao chép được.

CopyKnownType nhận class token để xử lý nguồn rỗng thông thường. Nó chọn miền trước rồi mới thêm các thành viên nguồn.

### 11.4. Null và quyền sửa

Add null bị từ chối; contains/remove null đều false. View chỉ đọc phản ánh owner có thêm WRITE, trong khi snapshot vẫn chỉ có READ. Lần thêm qua view bị từ chối.

Không có mâu thuẫn giữa “view không cho sửa” và “nội dung view thay đổi”: người sở hữu đã sửa nguồn mà view đang đọc xuyên tới.

### 11.5. Miền 65 hằng

Wide có 65 hằng; tập chọn F00, F63, F64 có ba thành viên và được duyệt theo thứ tự khai báo. AllOf có 65 thành viên, phần bù của tập ba thành viên có 62.

Kết quả chứng minh hành vi API vượt miền 64 hằng. Nó không chứng minh một kích thước bộ nhớ cụ thể và không yêu cầu ứng dụng biết đang dùng lớp triển khai riêng nào.

## 12. Những lỗi thường gặp

| Lỗi | Cách hiểu đúng |
|---|---|
| EnumSet chứa mọi kiểu enum lẫn nhau | Mỗi tập có một miền enum cụ thể |
| Tập rỗng là tham chiếu null | NoneOf tạo đối tượng rỗng biết miền |
| Of luôn tạo tập không sửa được | EnumSet.of trả tập có thể sửa thành viên |
| Thêm hằng trùng tạo bản sao | Set chỉ có tư cách thành viên có/không |
| EnumSet giữ thứ tự thêm | Nó duyệt theo thứ tự khai báo enum |
| Range loại đầu cuối như một khoảng nửa mở | EnumSet.range gồm cả hai đầu |
| CopyOf mọi Collection rỗng đều được | Nguồn thông thường rỗng không cho suy miền |
| CopyOf Collection rỗng luôn lỗi | Nguồn thực sự là EnumSet vẫn giữ thông tin miền |
| Complement là đảo mọi bit vô hạn | Phần bù nằm trong miền enum |
| AddAll hoặc retainAll không sửa tập nguồn nhận | Chúng cập nhật đối tượng nhận |
| Final làm tập bất biến | Nó không cấm add/remove trên đối tượng |
| UnmodifiableSet tự tạo bản sao | Nó là view chỉ đọc; muốn tách thành viên cần copy |
| Không ném CME nghĩa là an toàn đồng thời | Chính sách iterator không thay thế đồng bộ hóa |
| EnumSet bị giới hạn 64 hằng | API hỗ trợ miền lớn hơn |
| Gọn nghĩa là cả đối tượng luôn chỉ 8 byte | Biểu diễn bit không phải tổng bố cục đối tượng |
| Ordinal phù hợp làm mã bền vững mặc định | Nó gắn với vị trí khai báo và cần tách khỏi giao thức ngoài |

## 13. Thực hành và lời giải

### Bài 1. Tập hợp hay danh sách?

Cần giữ ba chế độ enum, không có phần tử trùng và thường hỏi có bật một chế độ không. EnumSet phù hợp ở đâu? Nếu cần ghi số lần chế độ xuất hiện thì sao?

**Lời giải:** EnumSet phù hợp với tập con của một enum và các thao tác tư cách thành viên. Nếu cần số lần, Set không chứa thông tin tần suất; phải thiết kế cấu trúc đếm phù hợp.

### Bài 2. Đọc thứ tự

Enum khai báo A, B, C, D. Tạo of(D, B, A) rồi thêm B lần nữa. Duyệt theo thứ tự nào, size bằng bao nhiêu và add B trả gì?

**Lời giải:** A, B, D; size bằng ba; add B trả false vì tập đã có B. Thứ tự khai báo quyết định đường duyệt.

### Bài 3. Hợp, giao, hiệu

A có READ, DELETE; B có READ, WRITE. Hãy tính A hợp B, A giao B và A trừ B. Có thể gọi removeAll trên A mà vẫn giữ nguyên A cho tác vụ sau không?

**Lời giải:** Hợp là READ, WRITE, DELETE; giao là READ; hiệu là DELETE. RemoveAll sửa chính tập nhận, nên cần sao chép A trước nếu muốn giữ A nguyên vẹn.

### Bài 4. Copy nguồn rỗng

Một hàm nhận Collection có thể là List rỗng hoặc EnumSet rỗng. Vì sao copyOf không có cùng kết quả cho hai đối tượng? Nếu hàm biết kiểu Permission, cách xử lý ổn định là gì?

**Lời giải:** EnumSet giữ miền phần tử ngay khi rỗng; Collection thông thường rỗng không cung cấp phần tử để factory suy miền. Có thể tạo noneOf(Permission.class) rồi addAll nguồn hợp lệ. Kiểu biến Collection không làm mất việc đối tượng nguồn thực sự là EnumSet.

### Bài 5. Giữ dữ liệu chỉ đọc

Owner là EnumSet có READ. Tạo unmodifiableSet(owner), sau đó owner thêm WRITE. View có thấy WRITE không? Làm gì nếu cần bản thành viên tách khỏi cập nhật của owner?

**Lời giải:** View thấy WRITE vì đọc xuyên tới owner. Nếu cần tách thành viên, sao chép owner rồi bọc bản sao, đồng thời không để một tham chiếu khác tiếp tục sửa bản sao ngoài ý định.

### Bài 6. Thêm hằng mới

Enum trước đây có A, B, C; A được chọn. Sang phiên bản mới thêm D. AllOf và complementOf tập chỉ có A thay đổi thế nào? Điều đó ảnh hưởng gì tới ý định “cho phép mọi khả năng trừ A”?

**Lời giải:** AllOf có A, B, C, D; phần bù có B, C, D. Hằng mới tự nằm trong các kết quả dựa trên toàn miền. Cần phân biệt chính sách liệt kê rõ với chính sách mọi hằng trừ một số hằng.

### Bài 7. Tại sao không tự dùng int?

Một người muốn thay EnumSet bằng int và tự gán bit theo ordinal để lưu ra tệp. Hãy nêu hai vấn đề ngoài việc viết được phép AND/OR.

**Lời giải:** Cần bảo đảm đúng miền và tránh trộn cờ của các enum khác, đồng thời phải có định dạng tương thích khi thêm hoặc đổi thứ tự hằng. Ordinal là vị trí khai báo; không tự là mã bền vững. Một int còn có giới hạn bit riêng không giống khả năng hỗ trợ miền lớn của EnumSet.

### Bài 8. Không có ngoại lệ khi duyệt

Một chương trình sửa EnumSet trong lúc duyệt và không có ConcurrentModificationException. Có đủ để kết luận cập nhật nhiều luồng đúng không?

**Lời giải:** Không. Iterator của EnumSet vốn không ném ngoại lệ đó theo hợp đồng và chỉ có tính nhất quán yếu. Cần thiết kế đồng thời và bảo vệ trạng thái đúng; không dùng sự vắng ngoại lệ làm bằng chứng.

## 14. Tự kiểm tra mô hình

Hãy xác định miền enum, tập thành viên hiện tại và ai có quyền sửa tập. Sau đó chọn factory, phép tập hợp và cách công bố kết quả theo đúng ba thông tin ấy.

Biểu diễn bit giải thích vì sao EnumSet là cấu trúc chuyên biệt gọn. API Set giúp ứng dụng thao tác bằng tên hằng và ý nghĩa tập hợp, còn các giới hạn về thứ tự, sao chép và đồng thời vẫn cần được hiểu rõ để dùng đúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="specialized-collections-002">EnumMap: ánh xạ với khóa enum</a>

<details>
<summary>Xem chi tiết</summary>

# EnumMap: ánh xạ với khóa enum

## 1. Khi miền khóa là một tập hằng đã biết

Giả sử chương trình có ba giai đoạn `PLANNED`, `RUNNING`, `DONE`. Mỗi giai đoạn cần một nhãn hiển thị, số lượng công việc hoặc danh sách dữ liệu liên quan. Đây là một quan hệ **mỗi khóa enum ánh xạ đến một giá trị**, chứ không chỉ là một tập các giai đoạn đang được chọn.

`EnumMap<K,V>` là cách triển khai `Map` chuyên dùng khi toàn bộ khóa thuộc **một kiểu enum**. Ta vẫn dùng những thao tác quen thuộc như `put`, `get`, `remove`, `containsKey`, nhưng cấu trúc biết trước miền hằng của kiểu khóa.

Ví dụ, với `EnumMap<Stage, String>`, khóa là các hằng của `Stage`, còn giá trị là chuỗi. Với `EnumMap<Stage, List<Task>>`, cùng miền khóa đó được dùng để chia các công việc thành nhóm. Kiểu giá trị không bị giới hạn là enum; chỉ kiểu khóa có ràng buộc này.

Ba điều cần nắm trước khi viết mã:

1. Một EnumMap chỉ nhận khóa của một kiểu enum đã xác định khi tạo map.
2. Thứ tự duyệt là thứ tự khai báo hằng enum, không phải thứ tự gọi put.
3. Biết toàn bộ miền khóa không có nghĩa map đã chứa mapping cho mọi hằng.

Bài học sẽ bắt đầu từ một map rỗng, quan sát các thao tác và chính sách null, rồi đi đến sao chép, view và ứng dụng đếm/nhóm dữ liệu. Các chương trình đầy đủ dùng Java 17 trở lên.

## 2. Mối quan hệ giữa enum, EnumMap và Map

### 2.1. Khóa là hằng enum, không phải tên chuỗi của hằng

Với khai báo `enum Stage { PLANNED, RUNNING, DONE }`, các khóa hợp lệ của map giai đoạn là `Stage.PLANNED`, `Stage.RUNNING`, `Stage.DONE`. Chuỗi `"RUNNING"` không phải cùng kiểu với `Stage.RUNNING`.

`EnumMap` không tự chuyển chuỗi người dùng nhập thành hằng enum. Nếu đầu vào đến từ chuỗi, việc phân tích và kiểm tra giá trị phải xảy ra ở ranh giới đầu vào theo quy tắc ứng dụng. Không thay toàn bộ khóa bằng chuỗi chỉ để né việc mô hình hóa miền dữ liệu.

Hằng enum có định danh ổn định trong chính kiểu enum đó. Hai enum khác nhau có thể cùng khai báo tên `RUNNING` nhưng các hằng không vì thế trở thành cùng một khóa. Một EnumMap cho Stage không phải map chung cho mọi enum có tên hằng giống nhau.

### 2.2. Ràng buộc kiểu khóa

Chữ ký lớp là `EnumMap<K extends Enum<K>, V>`. Điều đó buộc K là kiểu enum thích hợp. Cách khởi tạo thường dùng, minh họa bên trong một phương thức, là:

```java
EnumMap<Stage, String> labels = new EnumMap<>(Stage.class);
```

`Stage.class` là đối tượng `Class` mô tả kiểu enum, không phải một khóa đang được chèn. Constructor dùng thông tin đó ngay cả khi map chưa có entry nào.

Nếu chỉ cần công bố khả năng ánh xạ cho phần mã sử dụng, có thể khai báo biến theo interface:

```java
// Minh họa, với Stage đã được khai báo.
Map<Stage, String> labels = new EnumMap<>(Stage.class);
```

Đối tượng vẫn là EnumMap và vẫn duyệt theo thứ tự khai báo enum. Kiểu biến Map cho biết phần mã sử dụng đang cần các thao tác map chung; nó không thay đổi cách đối tượng thực tế lưu và duyệt dữ liệu.

### 2.3. Không trộn nhiều kiểu enum trong một EnumMap

Generics thông thường giúp phát hiện việc truyền sai kiểu khóa ngay lúc biên dịch. Không bỏ generics hoặc dùng raw type để chèn một enum không thuộc miền khóa. Việc ép kiểu không biến hằng của enum khác thành hằng hợp lệ của enum mà map đã chọn.

Đối với việc tìm kiếm bằng `get(Object)` hay `containsKey(Object)`, một đối tượng không phải khóa của miền này không tự được diễn giải từ tên của nó. API nhận Object không có nghĩa map trở thành một bảng tra cứu chuỗi hoặc một kho khóa tùy ý.

Nếu phải lấy kiểu enum từ một hằng ở mã tổng quát, API thích hợp là `getDeclaringClass()`. Không mặc định `constant.getClass()` luôn chính là lớp enum khai báo, vì một hằng có phần thân riêng có thể có lớp thực thi riêng. Với mã cụ thể như các ví dụ, truyền trực tiếp `Stage.class` là rõ ràng nhất. Có thể đối chiếu tại [Enum — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Enum.html).

## 3. Cơ chế và thứ tự duyệt

### 3.1. Miền enum hữu hạn giúp dùng biểu diễn chuyên biệt

Tài liệu [EnumMap — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/EnumMap.html) mô tả cách biểu diễn nội bộ dựa trên mảng. Vì map biết một kiểu enum và tập hằng của kiểu đó, nó có thể liên kết mỗi hằng với vị trí tương ứng trong cấu trúc chuyên biệt.

Điều này khác với việc phải hỗ trợ các đối tượng khóa tùy ý như một map tổng quát. Người dùng không cần tự tạo mảng rồi tự dùng `ordinal()` ở mọi nơi để tìm ô chứa giá trị. EnumMap cung cấp hợp đồng Map đầy đủ cho việc thêm, xóa, kiểm tra tồn tại và các view.

`ordinal()` là vị trí hằng trong khai báo enum và được thiết kế cho các cấu trúc chuyên biệt như thế này. Không vì hiểu cơ chế bên trong mà dùng ordinal làm mã nghiệp vụ hoặc định danh lưu trữ dài hạn: thay thứ tự khai báo có thể làm các số vị trí thay đổi.

### 3.2. Thứ tự là thứ tự khai báo, không phải chữ cái hoặc lần chèn

Với `Stage { PLANNED, RUNNING, DONE }`, thứ tự duyệt các khóa đang có luôn bám theo chuỗi khai báo đó. Dù chèn DONE trước, PLANNED sau và RUNNING cuối, map vẫn duyệt PLANNED, RUNNING, DONE.

Các view `keySet()`, `values()`, `entrySet()` đều phản ánh thứ tự khóa này. View giá trị được duyệt theo khóa tương ứng; nó không tự sắp các chuỗi hoặc các con số giá trị.

`EnumMap` không có constructor nhận comparator để chọn lại thứ tự. Có thứ tự duyệt không có nghĩa nó triển khai `SortedMap` hay `NavigableMap`; không có các phép như `lowerKey`, `subMap` hay `descendingMap` chỉ vì kết quả in nhìn có trật tự.

Nếu một màn hình cần thứ tự trình bày khác thứ tự enum, hãy xác định thứ tự trình bày riêng cho lượt xuất dữ liệu. Không đổi thứ tự khai báo enum một cách tùy tiện chỉ để tác động đến một dòng hiển thị mà chưa xét các nơi khác phụ thuộc nó.

### 3.3. Map ban đầu vẫn rỗng

`new EnumMap<>(Stage.class)` chỉ xác định miền khóa. Nó không tự thêm ba mapping và không tự gán giá trị mặc định cho các hằng.

Nếu mới put RUNNING, kích thước là một và chỉ RUNNING xuất hiện khi duyệt. Biết rằng PLANNED và DONE là những khóa có thể dùng khác với việc map đang có dữ liệu cho chúng.

## 4. Chương trình 1: tạo map và quan sát các thao tác

Lưu chương trình đầy đủ vào `EnumMapBasicsDemo.java`:

```java
import java.util.EnumMap;
import java.util.Map;

public class EnumMapBasicsDemo {
    enum Stage { PLANNED, RUNNING, DONE }

    public static void main(String[] args) {
        EnumMap<Stage, String> labels = new EnumMap<>(Stage.class);
        System.out.println("initialSize=" + labels.size());
        labels.put(Stage.DONE, "finished");
        labels.put(Stage.PLANNED, "waiting");
        labels.put(Stage.RUNNING, "working");
        System.out.println("labels=" + labels);
        System.out.println("keys=" + labels.keySet());
        System.out.println("values=" + labels.values());
        System.out.println("oldValue=" + labels.put(Stage.RUNNING, "active"));
        System.out.println("sizeAfterUpdate=" + labels.size());
        System.out.println("running=" + labels.get(Stage.RUNNING));
        for (Map.Entry<Stage, String> entry : labels.entrySet()) {
            System.out.println(entry.getKey() + ":" + entry.getValue());
        }
        System.out.println("removed=" + labels.remove(Stage.PLANNED));
        System.out.println("remaining=" + labels);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 EnumMapBasicsDemo.java
java EnumMapBasicsDemo
```

Stdout:

```text
initialSize=0
labels={PLANNED=waiting, RUNNING=working, DONE=finished}
keys=[PLANNED, RUNNING, DONE]
values=[waiting, working, finished]
oldValue=working
sizeAfterUpdate=3
running=active
PLANNED:waiting
RUNNING:active
DONE:finished
removed=waiting
remaining={RUNNING=active, DONE=finished}
```

### 4.1. Thứ tự chèn và thứ tự kết quả

`initialSize=0` xác nhận constructor không tạo mapping cho tất cả hằng. Các lời gọi put theo thứ tự DONE, PLANNED, RUNNING nhưng dòng `labels` và `keys` vẫn theo khai báo PLANNED, RUNNING, DONE.

`values=[waiting, working, finished]` đi cùng thứ tự khóa. Nó không được sắp theo thứ tự chữ cái của chuỗi và cũng không theo thời điểm chuỗi được đưa vào map.

### 4.2. Cập nhật cùng khóa và loại mapping

`put(Stage.RUNNING, "active")` trả giá trị cũ `working`. Map vẫn có ba mapping; khóa RUNNING không bị tạo thêm một bản thứ hai và không chuyển xuống cuối như thứ tự chèn.

Vòng duyệt entry cho thấy giá trị mới active nằm đúng tại RUNNING. `remove(Stage.PLANNED)` trả waiting và loại mapping đó, sau đó map chỉ còn RUNNING và DONE theo đúng thứ tự tương đối vốn có.

Những quy tắc cơ bản vẫn là Map: một khóa tương ứng nhiều nhất một mapping; put khóa đã có thay giá trị; remove loại mapping chứ không xóa hằng enum khỏi ngôn ngữ hay khỏi kiểu Stage.

## 5. Null và việc thiếu mapping

### 5.1. Không nhận khóa null nhưng nhận giá trị null

EnumMap từ chối `put(null, value)` bằng `NullPointerException`. Null không phải hằng của kiểu enum mà map đã chọn.

Tuy nhiên, map cho phép mapping có giá trị null. Vì vậy, sau `put(Day.MON, null)`, khóa MON vẫn đang có mapping và được tính vào `size()`.

Các thao tác kiểm tra hoặc loại khóa null không có cùng hành vi với chèn khóa null: với EnumMap, `containsKey(null)` trả false, `get(null)` trả null và `remove(null)` trả null. Không suy ra rằng mọi phương thức nhận null đều nhất thiết ném lỗi, cũng không suy ra từ việc get(null) chạy được rằng put(null, ...) được phép.

### 5.2. Ba trạng thái cần phân biệt

| Trạng thái theo một khóa enum hợp lệ | `get(key)` | `containsKey(key)` | Có tính vào size không? |
|---|---|---|---|
| Chưa có mapping | null | false | Không |
| Có mapping với giá trị null | null | true | Có |
| Có mapping với giá trị khác null | Giá trị đó | true | Có |

`getOrDefault` chỉ dùng mặc định khi không có mapping. Nó không thay giá trị null của một mapping đang tồn tại bằng mặc định.

Ngược lại, các phương thức như `putIfAbsent`, `computeIfAbsent`, `merge` có các quy tắc riêng liên quan đến giá trị null. Không thể dùng một câu “có khóa rồi thì mọi thao tác đều giữ nguyên” để mô tả tất cả chúng. Trong ví dụ tiếp theo, putIfAbsent sẽ xem mapping giá trị null là trường hợp có thể đặt giá trị.

### 5.3. Đủ khóa và đủ giá trị là hai yêu cầu khác nhau

Nếu ứng dụng đòi hỏi mọi ngày đều có cấu hình, cần kiểm tra tất cả hằng có mapping. Nếu còn đòi hỏi cấu hình khác null, phải kiểm tra thêm điều kiện giá trị.

Một EnumMap có số mapping bằng số hằng của miền khóa đã có mapping cho mọi hằng, nhưng vẫn có thể có giá trị null. Ngược lại, các mapping hiện có đều khác null chưa chứng minh mọi hằng đã được phủ.

## 6. Chương trình 2: giá trị null và kiểm tra độ phủ khóa

Lưu vào `EnumMapNullCoverageDemo.java`. `EnumSet` được dùng để tính các ngày chưa có mapping; nó không thay thế EnumMap chứa lịch.

```java
import java.util.EnumMap;
import java.util.EnumSet;

public class EnumMapNullCoverageDemo {
    enum Day { MON, TUE, WED }

    public static void main(String[] args) {
        EnumMap<Day, String> schedule = new EnumMap<>(Day.class);
        schedule.put(Day.MON, null);
        schedule.put(Day.WED, "work");
        System.out.println("monGet=" + schedule.get(Day.MON));
        System.out.println("monPresent=" + schedule.containsKey(Day.MON));
        System.out.println("tueGet=" + schedule.get(Day.TUE));
        System.out.println("tuePresent=" + schedule.containsKey(Day.TUE));
        System.out.println("size=" + schedule.size());
        System.out.println("monDefault=" + schedule.getOrDefault(Day.MON, "off"));
        System.out.println("tueDefault=" + schedule.getOrDefault(Day.TUE, "off"));
        System.out.println("nullGet=" + schedule.get(null));
        System.out.println("nullPresent=" + schedule.containsKey(null));
        System.out.println("nullRemove=" + schedule.remove(null));
        try {
            schedule.put(null, "invalid");
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            System.out.println("nullPut=NullPointerException");
        }
        EnumSet<Day> missing = EnumSet.allOf(Day.class);
        missing.removeAll(schedule.keySet());
        System.out.println("missingMappings=" + missing);
        System.out.println("oldNullValue=" + schedule.putIfAbsent(Day.MON, "study"));
        System.out.println("afterPutIfAbsent=" + schedule);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 EnumMapNullCoverageDemo.java
java EnumMapNullCoverageDemo
```

Stdout:

```text
monGet=null
monPresent=true
tueGet=null
tuePresent=false
size=2
monDefault=null
tueDefault=off
nullGet=null
nullPresent=false
nullRemove=null
nullPut=NullPointerException
missingMappings=[TUE]
oldNullValue=null
afterPutIfAbsent={MON=study, WED=work}
```

### 6.1. MON có mapping, TUE không có mapping

Cả `monGet` lẫn `tueGet` đều in null, nhưng `monPresent=true` còn `tuePresent=false`. `size=2` đếm MON và WED.

`monDefault=null` vì MON đã có mapping null. `tueDefault=off` vì TUE chưa có mapping. Đây là khác biệt thiết yếu khi viết cấu hình mặc định hoặc kiểm tra dữ liệu thiếu.

### 6.2. Tính tập khóa còn thiếu

`EnumSet.allOf(Day.class)` tạo tập tất cả hằng Day. Loại các khóa đang có trong schedule để lại `[TUE]`. MON không bị đánh dấu thiếu mapping dù giá trị của nó là null.

Nếu quy tắc là mọi ngày phải có chuỗi lịch khác null, phép kiểm tra này chưa đủ; cần xét giá trị của từng ngày. EnumMap không tự biết một giá trị null có hợp lệ về nghiệp vụ hay không.

### 6.3. putIfAbsent với giá trị cũ null

`putIfAbsent(Day.MON, "study")` trả null và đặt MON thành study. Số mapping không tăng vì MON đã có; nội dung giá trị thay đổi vì chính sách của putIfAbsent áp dụng cả trường hợp khóa đang ánh xạ tới null.

Khi quyết định cho phép giá trị null trong API của ứng dụng, phải tính cả ảnh hưởng lên các phương thức Map được dùng thêm. Một lớp bọc có thể chọn cấm giá trị null để hợp đồng đơn giản hơn, nhưng đó là chính sách bổ sung của ứng dụng, không phải giới hạn vốn có của EnumMap.

## 7. Chọn đúng constructor khi tạo hoặc sao chép

### 7.1. Biết trước kiểu khóa: truyền Class của enum

Constructor `EnumMap(Class<K> keyType)` tạo map rỗng với miền khóa đã xác định. Đây là lựa chọn rõ ràng khi khởi tạo cấu hình, bảng nhãn hoặc nhóm dữ liệu mới.

Không có constructor không đối số kiểu `new EnumMap<Stage, String>()`. Thông tin generic trong mã nguồn không tự thay thế được thông tin Class cần ở lúc chạy để cấu trúc biết miền hằng.

`Stage.class` có thể dùng dù chưa có bất kỳ mapping hay hằng nào được đưa vào map. Một kiểu enum không khai báo hằng nào cũng có thể có một EnumMap rỗng theo kiểu đó; biết kiểu và có phần tử là hai vấn đề riêng.

### 7.2. Sao chép từ EnumMap

Constructor nhận một EnumMap lấy được kiểu khóa từ chính map nguồn, nên map nguồn rỗng vẫn hợp lệ. Nó tạo cấu trúc map mới ban đầu có cùng mapping nếu có.

Điều này hữu ích khi muốn một bản cấu hình có thể sửa độc lập về mapping với bản hiện tại. Tuy nhiên, giá trị là đối tượng có thể thay đổi thì các bản map vẫn có thể chia sẻ tham chiếu; constructor không sao chép sâu dữ liệu bên trong giá trị.

`clone()` của EnumMap cũng được mô tả là bản sao nông. Trong các ví dụ, constructor copy được dùng để ý định tạo một map mới hiện rõ ngay tại nơi khai báo.

### 7.3. Sao chép từ Map tổng quát

Constructor `EnumMap(Map<K, ? extends V> source)` có hai trường hợp:

| Đối tượng nguồn thực tế | Có thể rỗng không? | Cách xác định miền enum |
|---|---|---|
| Là một EnumMap | Có | Lấy kiểu khóa đã được EnumMap nguồn giữ |
| Là map khác, chẳng hạn HashMap | Không | Cần ít nhất một mapping để xác định kiểu enum từ khóa |

Vì vậy, không đúng khi nói mọi constructor nhận Map đều từ chối map rỗng. Một biến được khai báo là Map nhưng đang trỏ đến EnumMap rỗng vẫn được xử lý như nguồn EnumMap.

Ngược lại, một `HashMap<Size, String>` rỗng vẫn không cung cấp một khóa thực tế để suy ra enum ở lúc chạy, dù người viết mã nhìn thấy Size trong generic. Constructor ném `IllegalArgumentException` thay vì tự đoán kiểu.

### 7.4. Khi nguồn có thể rỗng, truyền kiểu rõ ràng rồi putAll

Nếu hàm đã biết kiểu khóa Size nhưng nhận một Map nguồn có thể rỗng, có thể tạo `new EnumMap<>(Size.class)` trước rồi `putAll(source)`. Việc chép một nguồn rỗng vào map đã biết miền khóa là hợp lệ.

Đây là cách tách hai trách nhiệm: xác định miền khóa bằng Class, sau đó đưa các mapping hiện có vào. Không cần thêm một mapping giả chỉ để làm constructor suy ra kiểu.

## 8. Chương trình 3: các trường hợp constructor và tính độc lập

Lưu chương trình đầy đủ vào `EnumMapConstructorsDemo.java`:

```java
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class EnumMapConstructorsDemo {
    enum Size { SMALL, MEDIUM, LARGE }

    public static void main(String[] args) {
        EnumMap<Size, String> empty = new EnumMap<>(Size.class);
        EnumMap<Size, String> directCopy = new EnumMap<>(empty);
        Map<Size, String> emptyAsMap = empty;
        EnumMap<Size, String> runtimeEnumCopy = new EnumMap<>(emptyAsMap);
        System.out.println("emptyDirectCopy=" + directCopy.size());
        System.out.println("emptyRuntimeEnumCopy=" + runtimeEnumCopy.size());
        try {
            new EnumMap<Size, String>(new HashMap<Size, String>());
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.out.println("emptyHashCopy=IllegalArgumentException");
        }
        Map<Size, String> source = new HashMap<>();
        source.put(Size.LARGE, "L");
        source.put(Size.SMALL, "S");
        EnumMap<Size, String> inferred = new EnumMap<>(source);
        System.out.println("inferred=" + inferred);
        source.put(Size.SMALL, "S2");
        inferred.put(Size.MEDIUM, "M");
        System.out.println("sourceSmall=" + source.get(Size.SMALL));
        System.out.println("copiedSmall=" + inferred.get(Size.SMALL));
        System.out.println("sourceHasMedium=" + source.containsKey(Size.MEDIUM));
        System.out.println("independentCopy=" + inferred);
        EnumMap<Size, String> explicit = new EnumMap<>(Size.class);
        explicit.putAll(new HashMap<Size, String>());
        System.out.println("explicitWithEmptySource=" + explicit.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 EnumMapConstructorsDemo.java
java EnumMapConstructorsDemo
```

Stdout:

```text
emptyDirectCopy=0
emptyRuntimeEnumCopy=0
emptyHashCopy=IllegalArgumentException
inferred={SMALL=S, LARGE=L}
sourceSmall=S2
copiedSmall=S
sourceHasMedium=false
independentCopy={SMALL=S, MEDIUM=M, LARGE=L}
explicitWithEmptySource=0
```

### 8.1. Hai cách chép EnumMap rỗng đều chạy được

`emptyDirectCopy=0` là bản sao từ biến EnumMap. `emptyRuntimeEnumCopy=0` là bản sao từ biến Map đang trỏ đến chính EnumMap rỗng. Cả hai thành công vì đối tượng nguồn giữ được miền Size.

`emptyHashCopy=IllegalArgumentException` cho thấy trường hợp khác: HashMap rỗng không giữ miền enum theo cơ chế chuyên biệt đó để EnumMap suy ra.

### 8.2. Nguồn không rỗng được chép theo thứ tự enum

Nguồn HashMap có SMALL và LARGE. Khi tạo inferred, map mới có `{SMALL=S, LARGE=L}` theo thứ tự khai báo Size, không phụ thuộc thứ tự duyệt của HashMap nguồn. Ví dụ không in cả HashMap để tránh dùng một thứ tự mà HashMap không bảo đảm.

Sau đó, sửa SMALL trong source thành S2 không thay mapping SMALL trong bản sao. Thêm MEDIUM vào inferred không tự thêm MEDIUM vào source. Dòng `independentCopy` cho đủ ba mapping theo thứ tự SMALL, MEDIUM, LARGE.

Cuối cùng, tạo map có Size.class rồi putAll một HashMap rỗng cho kích thước 0, hoàn toàn hợp lệ. Cách này cũng dùng được khi nguồn có dữ liệu phù hợp.

## 9. Các view của EnumMap

### 9.1. keySet, values, entrySet nhìn cùng dữ liệu

Các view của EnumMap là các cửa nhìn liên kết với map, không phải collection sao chép. Một lần thêm hoặc loại mapping trong map sẽ được phản ánh khi xem các view đã lấy trước đó.

| View | Phần tử | Thứ tự duyệt |
|---|---|---|
| `keySet()` | Hằng enum đang có mapping | Thứ tự khai báo enum |
| `values()` | Giá trị của các mapping | Theo thứ tự khóa tương ứng |
| `entrySet()` | Cặp khóa–giá trị | Theo thứ tự khóa enum |

Map không tự chứa mọi hằng thì keySet cũng không tự là tập tất cả hằng. Khi cần toàn miền, dùng `values()` của kiểu enum hoặc một EnumSet tất cả hằng; đừng nhầm `Stage.values()` với `labels.values()`.

### 9.2. Xóa qua view tác động đến map

`keys.remove(enumKey)` loại mapping của khóa đó. `values.remove(value)` loại một mapping có giá trị tương ứng theo hành vi collection view, không có nghĩa xóa mọi mapping cùng giá trị. `clear()` qua một view hỗ trợ xóa sẽ làm các mapping tương ứng rời map.

View khóa không hỗ trợ `add` hay `addAll`. Một khóa riêng không mô tả giá trị cần gán cho mapping. Muốn thêm, dùng `put` với cả khóa và giá trị. Không suy ra rằng map sửa được thì mọi phương thức sửa của mọi view đều được hỗ trợ.

### 9.3. Cập nhật qua entry đang duyệt

Trong một lượt duyệt `entrySet()` của EnumMap, có thể dùng `entry.setValue(newValue)` để cập nhật giá trị của mapping tương ứng. Khi làm vậy, khóa vẫn là cùng hằng enum và thứ tự không đổi.

Không giữ một entry lấy từ iterator rồi mặc định nó là một bản sao bền vững sau các lần thay đổi cấu trúc. Nếu cần ghi nhớ dữ liệu độc lập, hãy sao chép thành dữ liệu có hợp đồng phù hợp thay vì dựa vào vòng đời kết nối không được thiết kế cho mục đích đó.

## 10. Chương trình 4: view liên kết, cập nhật entry và Map.equals

Lưu vào `EnumMapViewsDemo.java`. Mọi thao tác được thực hiện tuần tự; ví dụ không dựa vào việc iterator có nhìn thấy hay bỏ qua thay đổi cấu trúc xen giữa.

```java
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class EnumMapViewsDemo {
    enum Mode { FAST, SAFE, DEBUG }

    public static void main(String[] args) {
        EnumMap<Mode, Integer> map = new EnumMap<>(Mode.class);
        map.put(Mode.DEBUG, 30);
        map.put(Mode.SAFE, 10);
        map.put(Mode.FAST, 10);
        Set<Mode> keys = map.keySet();
        Collection<Integer> values = map.values();
        System.out.println("initial=" + map);
        System.out.println("removeSafe=" + keys.remove(Mode.SAFE));
        System.out.println("removeValue10=" + values.remove(10));
        System.out.println("afterViewRemovals=" + map);
        map.put(Mode.SAFE, 20);
        System.out.println("sameKeyView=" + keys);
        System.out.println("sameValueView=" + values);
        for (Map.Entry<Mode, Integer> entry : map.entrySet()) {
            entry.setValue(entry.getValue() + 1);
        }
        System.out.println("afterEntryUpdate=" + map);
        try {
            keys.add(Mode.FAST);
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println("keyAdd=UnsupportedOperationException");
        }
        Map<Mode, Integer> hash = new HashMap<>(map);
        System.out.println("enumEqualsHash=" + map.equals(hash));
        System.out.println("hashEqualsEnum=" + hash.equals(map));
        EnumMap<Mode, Integer> copy = new EnumMap<>(map);
        map.clear();
        System.out.println("keyViewAfterClear=" + keys);
        System.out.println("valueViewAfterClear=" + values);
        System.out.println("copyAfterClear=" + copy);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 EnumMapViewsDemo.java
java EnumMapViewsDemo
```

Stdout:

```text
initial={FAST=10, SAFE=10, DEBUG=30}
removeSafe=true
removeValue10=true
afterViewRemovals={DEBUG=30}
sameKeyView=[SAFE, DEBUG]
sameValueView=[20, 30]
afterEntryUpdate={SAFE=21, DEBUG=31}
keyAdd=UnsupportedOperationException
enumEqualsHash=true
hashEqualsEnum=true
keyViewAfterClear=[]
valueViewAfterClear=[]
copyAfterClear={SAFE=21, DEBUG=31}
```

### 10.1. Xóa qua khóa và qua giá trị

Map được chèn DEBUG, SAFE, FAST nhưng in `{FAST=10, SAFE=10, DEBUG=30}`. Xóa SAFE qua keys loại đúng mapping SAFE=10.

Sau đó, values chỉ còn một giá trị 10 ứng với FAST. `values.remove(10)` loại mapping đó, để map chỉ còn DEBUG=30. Lệnh này không trả giá trị bị xóa mà trả boolean cho biết collection đã thay đổi.

Thêm SAFE=20 qua map làm các view cũ in `[SAFE, DEBUG]` và `[20, 30]`, phản ánh trạng thái mới theo thứ tự enum.

### 10.2. Thay giá trị không đổi khóa

Vòng duyệt entry cộng một vào từng giá trị hiện có, tạo `{SAFE=21, DEBUG=31}`. Đây là cập nhật hai giá trị đang có, không tạo hai khóa mới hoặc làm đầy mọi hằng còn thiếu. FAST vẫn chưa có mapping.

`keys.add(Mode.FAST)` bị từ chối bằng `UnsupportedOperationException`. Muốn thêm FAST cần chỉ rõ giá trị bằng một lệnh put.

### 10.3. Bằng nhau với map khác và bản sao

HashMap được tạo từ map hiện tại có cùng các mapping nên hai chiều equals đều true. Phép bằng nhau của Map không đòi hai đối tượng cùng cách triển khai, và cũng không kiểm tra cùng thứ tự duyệt.

EnumMap copy được tạo trước clear giữ SAFE=21 và DEBUG=31. Sau `map.clear()`, các view cũ của map gốc rỗng nhưng copy còn dữ liệu. View và bản sao là hai quan hệ khác nhau.

## 11. Iterator nhất quán yếu không có nghĩa map an toàn cho nhiều luồng

Iterator từ các collection view của EnumMap được mô tả là **nhất quán yếu (weakly consistent)**: nó không ném `ConcurrentModificationException`, và có thể có hoặc không phản ánh các sửa đổi xảy ra trong lúc lượt duyệt đang diễn ra.

Không nên viết một ví dụ có thêm/xóa xen giữa rồi đưa ra một stdout duy nhất như bảo đảm cho mọi môi trường nếu hợp đồng cho phép nhiều khả năng quan sát. Các ví dụ ở đây giữ thao tác theo giai đoạn rõ ràng để kết quả xác định.

Việc không có ngoại lệ không chứng minh map đã có cơ chế đồng bộ. EnumMap không tự đồng bộ cho truy cập nhiều luồng. Nếu có luồng sửa trong khi các luồng khác dùng cùng map, cần một thiết kế phối hợp toàn bộ các đường truy cập.

Đặc biệt, những chuỗi như kiểm tra thiếu rồi put, hoặc lấy số đếm rồi cộng và ghi lại, không tự trở thành nguyên tử vì cấu trúc là EnumMap. Các phương thức mặc định Map như merge hay computeIfAbsent trên cấu trúc này cũng không tự mang bảo đảm cập nhật đồng thời của một concurrent map.

Nếu chương trình cần một bản dữ liệu nhất quán để xử lý, việc tạo bản sao cũng phải diễn ra dưới điều kiện truy cập phù hợp. Sao chép trong khi nguồn bị sửa mà không phối hợp không tự tạo một giao dịch chụp dữ liệu.

## 12. Ứng dụng: đếm và nhóm dữ liệu theo enum

### 12.1. Số đếm đầy đủ và nhóm chỉ tạo khi cần

Một luồng sự kiện có mức LOW, MEDIUM, HIGH thường cần hai dạng dữ liệu:

- Số đếm cho mọi mức, kể cả mức chưa xuất hiện.
- Danh sách thông điệp ở những mức thật sự có sự kiện.

Cả hai dùng miền khóa Severity nhưng có chính sách khởi tạo khác. Map đếm được điền 0 cho tất cả hằng ngay từ đầu. Map nhóm bắt đầu rỗng và chỉ tạo danh sách khi gặp mức tương ứng.

Lưu chương trình đầy đủ vào `EnumMapGroupingDemo.java`:

```java
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class EnumMapGroupingDemo {
    enum Severity { LOW, MEDIUM, HIGH }
    record Event(Severity severity, String message) {}

    public static void main(String[] args) {
        List<Event> events = List.of(
                new Event(Severity.HIGH, "server"),
                new Event(Severity.LOW, "typo"),
                new Event(Severity.HIGH, "disk"));
        EnumMap<Severity, Integer> counts = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            counts.put(severity, 0);
        }
        EnumMap<Severity, List<String>> groups = new EnumMap<>(Severity.class);
        for (Event event : events) {
            counts.merge(event.severity(), 1, Integer::sum);
            groups.computeIfAbsent(event.severity(), key -> new ArrayList<>())
                    .add(event.message());
        }
        System.out.println("counts=" + counts);
        System.out.println("groups=" + groups);
        System.out.println("independentLists="
                + (groups.get(Severity.LOW) != groups.get(Severity.HIGH)));
        for (Severity severity : Severity.values()) {
            System.out.println(severity + ": count=" + counts.get(severity)
                    + ", messages=" + groups.getOrDefault(severity, List.of()));
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 EnumMapGroupingDemo.java
java EnumMapGroupingDemo
```

Stdout:

```text
counts={LOW=1, MEDIUM=0, HIGH=2}
groups={LOW=[typo], HIGH=[server, disk]}
independentLists=true
LOW: count=1, messages=[typo]
MEDIUM: count=0, messages=[]
HIGH: count=2, messages=[server, disk]
```

### 12.2. Tại sao MEDIUM vẫn được in dù không có sự kiện?

Dữ liệu đầu vào chỉ có HIGH, LOW, HIGH. Vòng lặp qua `Severity.values()` đã tạo mapping đếm cho cả LOW, MEDIUM, HIGH, nên kết quả đếm có MEDIUM=0.

Map groups không được điền sẵn mọi khóa. `computeIfAbsent` chỉ tạo danh sách cho LOW và HIGH khi gặp sự kiện. Vì chưa có MEDIUM, groups không chứa khóa này. Khi trình bày kết quả, `getOrDefault(severity, List.of())` cung cấp danh sách rỗng để đọc.

Map đếm và map nhóm không mâu thuẫn. Chúng có cùng miền khóa nhưng được dùng để biểu diễn hai loại thông tin với chính sách thiếu mapping khác nhau.

### 12.3. merge cập nhật số đếm

`counts.merge(severity, 1, Integer::sum)` cộng một vào số đếm khác null đang có. Trong ví dụ, mọi mức đã được khởi tạo bằng 0, nên sau ba sự kiện ta có LOW=1, MEDIUM=0, HIGH=2.

Theo hợp đồng Map, nếu một khóa chưa có hoặc đang gắn với null, merge dùng giá trị được cung cấp để thiết lập mapping thay vì gọi hàm gộp như trường hợp giá trị khác null. Giá trị truyền làm đối số merge trong ví dụ là 1, khác null.

Việc dùng merge ở đây làm rõ thao tác cập nhật trong mã tuần tự. Nó không biến EnumMap thành một cấu trúc cập nhật đồng thời nguyên tử.

### 12.4. Mỗi nhóm cần một danh sách riêng

`key -> new ArrayList<>()` tạo danh sách mới khi một mức chưa có danh sách khác null. Các sự kiện HIGH được thêm vào cùng danh sách của HIGH theo thứ tự xuất hiện: server rồi disk.

`independentLists=true` chứng minh LOW và HIGH không trỏ cùng một danh sách. Nếu dùng một danh sách mutable chung làm giá trị cho mọi khóa, thêm thông điệp cho một mức sẽ làm các mức khác nhìn thấy cùng thông điệp qua tham chiếu chung.

`List.of()` trong getOrDefault chỉ dùng làm kết quả rỗng để đọc khi thiếu nhóm. Nó không được put vào groups rồi gọi add, và không được dùng thay cho ArrayList cần thay đổi.

Thứ tự khóa LOW, MEDIUM, HIGH do enum quyết định; thứ tự thông điệp bên trong từng List do cách xử lý dữ liệu đầu vào quyết định. EnumMap không tự sắp xếp phần tử bên trong giá trị.

## 13. Chọn đúng cấu trúc cho miền enum

| Nhu cầu | Cấu trúc phù hợp để cân nhắc | Điều cần phân biệt |
|---|---|---|
| Mỗi hằng enum gắn một giá trị | `EnumMap<E,V>` | Có quan hệ khóa–giá trị |
| Chỉ ghi nhận những hằng nào được chọn | `EnumSet<E>` | Chỉ cần có/không, không có giá trị V riêng |
| Khóa thuộc nhiều loại hoặc miền khóa mở | Map tổng quát theo yêu cầu | Không phải miền khóa của một enum duy nhất |
| Cần tra lân cận hoặc phạm vi theo comparator riêng | Map có thứ tự điều hướng phù hợp | EnumMap không cung cấp API NavigableMap |

EnumMap thường là điểm bắt đầu hợp lý khi yêu cầu đúng là ánh xạ theo một kiểu enum. Biểu diễn mảng giúp tận dụng miền khóa hữu hạn; các thao tác cơ bản có chi phí hằng số theo mô tả triển khai. Tuy nhiên, không được biến điều này thành khẳng định mọi chương trình đều nhanh hơn mọi cách dùng HashMap.

Việc duyệt, sao chép hoặc xử lý các giá trị lớn vẫn có chi phí riêng. Một EnumMap chứa danh sách dài không làm công việc trên các danh sách trở thành hằng số. Khi hiệu năng là yêu cầu cụ thể, cần đo đúng thao tác và dữ liệu sử dụng.

### 13.1. Bảng ánh xạ không tự kiểm tra tính đầy đủ như một biểu thức lựa chọn

EnumMap thích hợp cho bảng nhãn, cấu hình theo trạng thái, nhóm dữ liệu và có thể cả bảng hằng enum ánh xạ đến hàm xử lý. Tuy nhiên, tạo map không khiến trình biên dịch buộc mọi hằng phải có mapping.

Khi thêm một hằng enum mới, bảng ánh xạ có thể vẫn biên dịch nhưng thiếu cấu hình cho hằng đó. Nếu API yêu cầu bảng đầy đủ, hãy kiểm tra miền hằng tại lúc khởi tạo hoặc bằng phép kiểm tra phù hợp, thay vì chờ đến khi get trả null ở một đường xử lý hiếm gặp.

### 13.2. Quyền sửa và chia sẻ dữ liệu

Khai báo biến là `Map` không làm dữ liệu chỉ đọc. Trả trực tiếp EnumMap hoặc view khóa cho nơi khác có thể trao quyền sửa hoặc xóa mapping. Nếu API cần quyền đọc giới hạn hoặc một bản dữ liệu riêng, phải thiết kế kết quả trả về theo đúng yêu cầu đó.

Cũng cần phân biệt thay mapping với thay đối tượng giá trị. Một bản copy EnumMap độc lập về cấu trúc vẫn có thể giữ cùng List hoặc đối tượng cấu hình với nguồn. Khi cần tách cả dữ liệu mutable, phải sao chép giá trị theo hợp đồng của kiểu đó.

## 14. Kiểm tra một bảng EnumMap

### 14.1. Các kiểm tra đáng làm

1. Map tạo bằng Class ban đầu rỗng và nhận đúng miền enum.
2. Chèn theo thứ tự bất kỳ nhưng duyệt theo thứ tự khai báo.
3. Put cùng khóa trả giá trị cũ, không tăng size và không đổi thứ tự.
4. Khóa null bị từ chối khi chèn; giá trị null được biểu diễn khác với thiếu mapping.
5. Nếu bảng cần đầy đủ, mọi hằng có mapping và thỏa điều kiện giá trị của ứng dụng.
6. Sao chép từ EnumMap rỗng thành công, từ map tổng quát rỗng được xử lý bằng cách xác định Class rõ ràng.
7. Xóa qua view có ảnh hưởng nguồn đúng như hợp đồng; bản sao không tự cập nhật theo nguồn.
8. Với nhóm dữ liệu, các khóa không vô tình dùng chung một collection mutable.

Kiểm tra thứ tự bằng danh sách khóa theo thứ tự duyệt. Map.equals hay Set.equals kiểm tra các mapping hoặc tập phần tử, không phải thứ tự.

Không dùng việc iterator không ném ConcurrentModificationException để kiểm tra an toàn nhiều luồng. Không dựa vào một kết quả quan sát sửa đổi xen giữa mà hợp đồng nhất quán yếu cho phép có hoặc không nhìn thấy.

### 14.2. Lưu ý khi dùng giá trị số

Ví dụ view cộng một vào các Integer đã được khởi tạo khác null. Nếu ứng dụng cho phép giá trị null, phép toán số có thể cần chính sách riêng trước khi Java tự mở hộp Integer. Việc EnumMap cho phép null không tự cung cấp một giá trị số mặc định.

Trong ví dụ đếm, điền số 0 cho mọi mức là một quyết định biểu diễn rõ ràng, không phải hành vi tự động của constructor.

## 15. Lỗi thường gặp

| Nhầm lẫn | Hậu quả | Cách xử lý |
|---|---|---|
| EnumMap nhận mọi enum trong cùng một map | Sai miền khóa | Xác định một kiểu enum duy nhất |
| Tên chuỗi của hằng là khóa enum | Không tra được theo ý định hoặc sai kiểu | Chuyển và kiểm tra đầu vào trước khi dùng hằng |
| Generic đủ để dùng constructor không đối số | Không có constructor phù hợp | Truyền Class của enum |
| Map biết miền enum nên đã có đủ mapping | Get trả null, size nhỏ hơn mong đợi | Chủ động khởi tạo hoặc kiểm tra độ phủ |
| Thứ tự duyệt theo các lần put | Dự đoán sai kết quả | Theo thứ tự khai báo enum |
| Có thứ tự nên có comparator/subMap | Dùng nhầm API | Phân biệt EnumMap với SortedMap/NavigableMap |
| Get null nghĩa là thiếu khóa | Bỏ sót mapping giá trị null | Dùng containsKey khi cần phân biệt |
| getOrDefault thay mọi giá trị null | Mặc định không được dùng như dự đoán | Hiểu nó chỉ áp dụng khi thiếu mapping |
| Mọi Map rỗng đều copy sang EnumMap được | IllegalArgumentException với nguồn không phải EnumMap | Truyền Class rồi putAll |
| Mọi constructor nhận Map đều từ chối nguồn rỗng | Bỏ sót nguồn thực tế là EnumMap | Đọc đúng trường hợp suy ra miền khóa |
| KeySet là tập độc lập | Remove làm mất mapping nguồn | Tạo bản sao collection nếu cần độc lập |
| Copy map tạo bản sao sâu mọi giá trị | Chia sẻ dữ liệu mutable ngoài ý định | Xác định cách sao chép giá trị |
| Iterator không ném lỗi nghĩa là an toàn cho nhiều luồng | Có thể có truy cập trạng thái không được phối hợp | Thiết kế đồng bộ theo yêu cầu nhiều luồng |

## 16. Tự kiểm tra

### Câu hỏi

1. Vì sao cần `Stage.class` khi tạo một EnumMap rỗng?
2. Chèn DONE trước PLANNED có làm DONE được duyệt trước không?
3. Sau put một hằng hai lần, size có tự tăng hai đơn vị không?
4. EnumMap có thể chứa khóa null không? Có thể chứa giá trị null không?
5. Get trả null có đủ để phân biệt MON thiếu mapping và MON có giá trị null không?
6. `getOrDefault` trên mapping null trả mặc định hay null?
7. Vì sao chép HashMap rỗng gây lỗi nhưng chép EnumMap rỗng không gây lỗi tương tự?
8. Một biến Map đang trỏ EnumMap rỗng có chép được bằng constructor nhận Map không?
9. Iterator nhất quán yếu có bảo đảm nhìn thấy mọi thay đổi diễn ra trong lượt duyệt không?
10. EnumSet và EnumMap khác nhau ở vai trò nào?

### Đáp án

1. Class cung cấp miền enum ở lúc chạy dù chưa có khóa để suy ra.
2. Không; thứ tự khai báo hằng quyết định thứ tự duyệt.
3. Không; lần sau thay giá trị của cùng mapping.
4. Không nhận khóa null khi put; nhận giá trị null.
5. Không; cần kiểm tra containsKey hoặc một hợp đồng không có giá trị null của lớp bọc.
6. Null, vì mapping đang tồn tại.
7. EnumMap nguồn đã giữ kiểu khóa; HashMap rỗng không có mapping để constructor suy ra kiểu enum.
8. Có, vì constructor nhận diện trường hợp đối tượng nguồn thực tế là EnumMap.
9. Không; có thể thấy hoặc không thấy sửa đổi. Điều đó cũng không làm map tự đồng bộ.
10. EnumSet lưu những hằng có mặt; EnumMap lưu quan hệ từ mỗi hằng đang có mapping đến một giá trị.

## 17. Thực hành có kết quả đối chiếu

### Bài 1: thứ tự không phụ thuộc lần chèn

Khai báo `enum Color { RED, GREEN, BLUE }`. Chèn BLUE=3, RED=1 rồi GREEN=2 vào một EnumMap. Dự đoán thứ tự keySet trước khi chạy.

Kết quả đối chiếu là `[RED, GREEN, BLUE]`. Sau đó cập nhật RED thành 10: kích thước vẫn ba, thứ tự vẫn vậy, chỉ giá trị RED đổi.

### Bài 2: đủ khóa nhưng chưa đủ giá trị

Tạo map cho Color và put cả ba khóa, trong đó GREEN có giá trị null. Tính tập hằng chưa có mapping bằng EnumSet.allOf rồi removeAll keySet.

Kết quả đối chiếu: tập thiếu mapping rỗng nhưng vẫn có một giá trị null. Nếu yêu cầu mọi màu có nhãn khác null, phép kiểm tra độ phủ phải đi kèm kiểm tra giá trị.

### Bài 3: nguồn có thể rỗng

Viết một hàm nhận Map<Color,String> có thể rỗng. Khởi tạo EnumMap bằng Color.class rồi putAll nguồn. Chạy với nguồn rỗng và nguồn chỉ có BLUE.

Kết quả đối chiếu: trường hợp rỗng trả map size 0; trường hợp có BLUE trả đúng một mapping BLUE. Không cần chèn dữ liệu giả để xác định kiểu khóa.

### Bài 4: nhóm dữ liệu khi đầu vào rỗng

Trong chương trình 5, thay events bằng danh sách rỗng rồi dự đoán counts, groups và phần in theo mọi Severity.

Kết quả đối chiếu: counts vẫn có LOW=0, MEDIUM=0, HIGH=0 vì đã khởi tạo toàn miền; groups rỗng vì chưa có mức nào cần danh sách. Mỗi dòng trình bày có count=0 và messages=[]. Điều này xác nhận sự khác nhau giữa điền sẵn giá trị mặc định và tạo nhóm khi cần.

## 18. Môi trường chạy và tài liệu

Năm chương trình trong bài độc lập, dùng cú pháp và API tương thích Java 17. Chúng đã được biên dịch bằng `javac --release 17 -encoding UTF-8` và chạy trên JDK 24.0.2; stdout được đối chiếu từ các lần chạy đó. Mỗi lớp public được lưu trong tệp trùng tên, không dùng thư viện ngoài.

- [EnumMap — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/EnumMap.html): miền khóa enum, constructor, thứ tự, null, view và iterator.
- [Enum — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Enum.html): định danh hằng, thứ tự khai báo, ordinal và getDeclaringClass.
- [EnumSet — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/EnumSet.html): tập hằng chuyên biệt và phép tạo toàn miền.
- [Map — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): mapping, null, getOrDefault, putIfAbsent, computeIfAbsent và merge.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="specialized-collections-003">WeakHashMap: khóa yếu và tác động của thu gom rác</a>

<details>
<summary>Xem chi tiết</summary>

# WeakHashMap: khóa yếu và tác động của thu gom rác

## 1. Khi một bảng tra cứu không nên quyết định tuổi thọ của đối tượng

Giả sử một công cụ phân tích giao diện lưu thêm thông tin cho từng đối tượng cửa sổ. Cửa sổ được quản lý ở nơi khác; bảng này chỉ gắn dữ liệu phụ trợ với nó.

Nếu dùng một HashMap sống rất lâu, chính bảng có thể tiếp tục giữ đối tượng cửa sổ sau khi mọi nơi khác đã ngừng sử dụng nó. Dữ liệu phụ trợ vô tình trở thành chủ sở hữu tuổi thọ của đối tượng.

WeakHashMap cung cấp một lựa chọn khác: **ánh xạ trong map không tự giữ sống khóa bằng tham chiếu mạnh**. Khi đối tượng khóa không còn được giữ theo cách ngăn thu gom và GC xử lý các tham chiếu yếu của nó, ánh xạ có thể biến mất.

Đây là cấu trúc phù hợp để suy nghĩ về dữ liệu phụ trợ gắn với vòng đời đối tượng. Nó không đưa ra cam kết “giữ mục trong năm phút”, “giữ tối đa một nghìn mục” hay “xóa mục ít dùng nhất”.

Ba khái niệm cần tách rõ ngay từ đầu:

| Khái niệm | Câu hỏi nó trả lời |
| --- | --- |
| Quan hệ bằng của khóa | Một lần get đang tìm ánh xạ của khóa nào? |
| Khả năng truy cập đối tượng | Đối tượng khóa còn được giữ sống qua những đường tham chiếu nào? |
| Dọn ánh xạ hết hiệu lực | Khi nào phần tử có khóa đã bị xử lý được tháo khỏi bảng? |

Hai khóa có equals bằng nhau không nhất thiết là cùng một đối tượng được GC theo dõi. Một khóa không còn tham chiếu mạnh cũng không có nghĩa map phải giảm size ngay tại câu lệnh tiếp theo.

Bài này đi từ đồ thị tham chiếu tới hành vi Map, rồi chạy ba chương trình Java 17 để phân biệt điều được bảo đảm với kết quả phụ thuộc GC.

## 2. “Yếu” là thuộc tính của một đường tham chiếu

### 2.1. Tham chiếu mạnh và tham chiếu yếu

Trong mô hình thông thường, một đường tham chiếu mạnh còn dùng được từ chương trình tới đối tượng giúp giữ đối tượng sống.

WeakReference cho phép giữ một liên hệ tới đối tượng mà liên hệ yếu ấy không tự ngăn đối tượng được thu gom. Đối tượng được tham chiếu gọi là đối tượng đích của tham chiếu, thường gặp tên referent trong tài liệu API.

Với WeakHashMap còn sống, mô hình đơn giản là:

```text
chương trình --mạnh--> map --mạnh--> phần tử lưu trữ
phần tử lưu trữ --yếu--> khóa
phần tử lưu trữ --mạnh--> giá trị
```

Mũi tên yếu chỉ nằm ở quan hệ từ phần tử lưu trữ tới khóa. Không suy ra map, các nút lưu trữ hay value đều được giữ yếu.

### 2.2. Phải xét mọi đường tới khóa

Nếu chương trình còn giữ khóa trong một danh sách đang dùng, khóa vẫn sống dù map chỉ tham chiếu yếu tới nó.

Nếu value chứa một trường trỏ mạnh ngược về khóa, đường map → value → key cũng có thể giữ khóa sống.

Nếu chỉ còn chu kỳ không thể truy cập từ chương trình, việc có chu kỳ không tự làm đối tượng sống mãi. Vấn đề là có đường giữ sống tới chu kỳ ấy hay không, chứ không chỉ đếm số tham chiếu.

Vì vậy, “tôi đã gán biến key bằng null” chỉ nói đã bỏ một đường tham chiếu. Nó chưa chứng minh các đường khác đã mất.

### 2.3. Phạm vi biến không đồng nghĩa phạm vi sống của đối tượng

Một tên biến còn xuất hiện trong khối mã không tự bảo đảm JVM giữ đối tượng tới cuối dấu ngoặc. Ngược lại, một đối tượng không còn tên biến cục bộ vẫn có thể được giữ qua trường, collection hoặc tác vụ đang chạy.

[JLS 17, mục 12.6.1](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.6.1) mô tả khả năng biến đổi tối ưu làm đối tượng hết khả năng truy cập sớm hơn cách đọc mã đơn giản.

Các ví dụ cần giữ khóa chắc chắn sẽ dùng Reference.reachabilityFence ở cuối đoạn quan sát. Mục đích là xác định ranh giới giữ sống cho thí nghiệm, không phải ép GC hay khóa một luồng khác.

### 2.4. Không cần học mọi loại reference để dùng đúng bài này

Java còn có SoftReference và PhantomReference với mục đích, quy tắc khác. WeakHashMap dùng khóa yếu; không tự có chính sách mềm dựa trên áp lực bộ nhớ hoặc cơ chế đóng tài nguyên của các loại khác.

Khái niệm khả năng truy cập và các nhóm tham chiếu được giới thiệu trong [gói java.lang.ref của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/package-summary.html). Trong bài này, trọng tâm là đường giữ khóa, không phải thuật toán của từng bộ thu gom rác.

## 3. Từ khóa hết được giữ sống tới ánh xạ biến mất

### 3.1. Không phải cứ hết dùng là có thao tác remove đồng bộ ngay

Một trình tự khái niệm có thể hình dung như sau:

1. Ứng dụng bỏ những đường giữ sống thông thường tới đối tượng khóa.
2. GC xác định đối tượng ở trạng thái có thể xử lý qua tham chiếu yếu.
3. Các tham chiếu yếu thích hợp được xóa liên hệ tới đối tượng đích.
4. Tham chiếu đã đăng ký có thể được đưa vào `ReferenceQueue`.
5. Map xử lý thông tin nội bộ và dọn phần tử có khóa đã hết hiệu lực.

Không gắn mỗi bước với một câu lệnh Java cụ thể hay một khoảng chờ cố định.

Tài liệu [WeakReference Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html) phân biệt lúc xóa các tham chiếu yếu với lúc đưa chúng vào hàng đợi; việc đưa vào hàng đợi có thể xảy ra cùng lúc hoặc sau đó.

### 3.2. `ReferenceQueue` thông báo về reference, không trả lại khóa đã mất

`ReferenceQueue` cho phép nhận các đối tượng Reference đã được đưa vào hàng đợi. Nó không phải một hàng đợi chứa bản sao đầy đủ của các khóa đã bị thu gom.

Trong ví dụ phía sau, một WeakReference quan sát riêng được đăng ký với hàng đợi riêng. Khi poll trả lại đúng reference ấy, ta biết reference quan sát đã được đưa vào hàng đợi. Không suy ra đã trực tiếp đọc được hàng đợi nội bộ của WeakHashMap.

[`ReferenceQueue` Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/ReferenceQueue.html) là API đối chiếu cho poll và cơ chế đăng ký reference.

### 3.3. Phân biệt khả năng tìm ánh xạ với phần lưu trữ chờ dọn

Chi tiết OpenJDK 17: WeakHashMap sử dụng một hàng đợi nội bộ; các đường truy cập bảng và size có thể xử lý những phần tử đã được báo hết hiệu lực. Khi dọn, triển khai tháo phần tử và bỏ liên kết tới value.

Vì có nhiều bước, không nên dùng một số đo size làm bằng chứng rằng mọi bộ nhớ liên quan đã được thu hồi tức thì. Một map ít được truy cập có thể còn phần lưu trữ chờ dọn; một value còn được nơi khác giữ vẫn sống sau khi ánh xạ mất.

Đó là phần giải thích theo [mã nguồn WeakHashMap của OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/WeakHashMap.java). Không coi tên hàm nội bộ hay thời điểm dọn cụ thể là hợp đồng cho mọi triển khai Java.

## 4. Yếu về vòng đời không có nghĩa so khóa bằng identity

WeakHashMap vẫn là một Map dựa trên bảng băm. Tra cứu tuân quan hệ equals/hashCode của khóa, không mặc định thay equals bằng toán tử ==.

Phân biệt hai trục:

| Trục | Ví dụ câu hỏi |
| --- | --- |
| Định danh theo equals | Một Key(7) mới có tìm ánh xạ đang có của Key(7) không? |
| Tuổi thọ đối tượng cụ thể | Đối tượng Key(7) nào thực sự được giữ làm đích của tham chiếu yếu? |

Nếu hai đối tượng khác nhau nhưng equals true, một đối tượng mới có thể tìm hoặc cập nhật ánh xạ đang tồn tại. Nhưng việc giữ đối tượng mới không tự tạo một đường tham chiếu mạnh tới đối tượng khóa cũ.

Các kiểu khóa phải có equals/hashCode đúng và ổn định trong lúc được dùng làm khóa. Đổi trường dùng tính hash hoặc equals có thể làm tra cứu sai theo quy tắc của map băm; dùng khóa yếu không chữa lỗi đó. Xem hợp đồng khóa của [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

WeakHashMap nhận cả một khóa null và các value null. Vì vậy get trả null có thể là khóa vắng mặt hoặc một ánh xạ có value null; containsKey hữu ích khi cần phân biệt.

Khóa null không phải một đối tượng ứng dụng có thể mất tham chiếu mạnh như new Object(). Trong OpenJDK 17 nó được biểu diễn bằng một đối tượng đánh dấu được giữ tĩnh. Không trông đợi ánh xạ null tự hết hạn bằng GC; hãy remove(null) hoặc clear khi cần.

## 5. Ví dụ 1: hành vi Map khi các khóa còn được giữ sống

Lưu thành **WeakKeyBasics.java**:

```java
import java.lang.ref.Reference;
import java.util.Map;
import java.util.WeakHashMap;

public class WeakKeyBasics {
    record Key(int id) {}

    public static void main(String[] args) {
        Map<Key, String> map = new WeakHashMap<>();
        Key original = new Key(7);
        Key equalKey = new Key(7);
        Key nullableValueKey = new Key(8);

        map.put(original, "first");
        System.out.println("same object: " + (original == equalKey));
        System.out.println("equal keys: " + original.equals(equalKey));
        System.out.println("lookup equal key: " + map.get(equalKey));
        System.out.println("previous value: " + map.put(equalKey, "updated"));
        System.out.println("size after update: " + map.size());

        map.put(nullableValueKey, null);
        map.put(null, "null-key value");
        System.out.println("null value lookup: " + map.get(nullableValueKey));
        System.out.println("null value key present: " + map.containsKey(nullableValueKey));
        System.out.println("absent key present: " + map.containsKey(new Key(9)));
        System.out.println("null key lookup: " + map.get(null));
        System.out.println("total size: " + map.size());

        System.out.println("removed value: " + map.remove(equalKey));
        System.out.println("original key present: " + map.containsKey(original));
        System.out.println("size after remove: " + map.size());

        Reference.reachabilityFence(original);
        Reference.reachabilityFence(equalKey);
        Reference.reachabilityFence(nullableValueKey);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 WeakKeyBasics.java
java WeakKeyBasics
```

Stdout:

```text
same object: false
equal keys: true
lookup equal key: first
previous value: first
size after update: 1
null value lookup: null
null value key present: true
absent key present: false
null key lookup: null-key value
total size: 3
removed value: updated
original key present: false
size after remove: 2
```

Các chương trình trong bài được biên dịch với --release 17 và chạy trên OpenJDK 24.0.2. --release 17 chọn mức ngôn ngữ/API và bytecode đích; JVM thực thi vẫn là phiên bản đã nêu.

### 5.1. Equal key không cần là cùng object

Hai biến original và equalKey giữ hai record khác nhau, nên == cho false. Dữ liệu id bằng nhau nên equals cho true.

`get` bằng equalKey tìm được "first"; put bằng equalKey trả về giá trị cũ và cập nhật thành "updated". Số ánh xạ vẫn là 1.

Ví dụ này chứng minh WeakHashMap không mặc định là IdentityHashMap. Nó chưa chứng minh ánh xạ tồn tại sau khi đối tượng khóa được lưu không còn được giữ sống.

### 5.2. Các giá trị null không liên quan việc thu gom ở đây

Khóa nullableValueKey có một ánh xạ với value null, còn Key(9) vắng mặt. `containsKey` phân biệt hai trường hợp.

Ánh xạ khóa null là một mục khác, có value "null-key value". Tổng size là 3 trước remove.

Không có chuyện value null tự khiến khóa bị thu gom. Tuổi thọ khóa phụ thuộc đồ thị tham chiếu, không phụ thuộc việc value tình cờ là null.

### 5.3. Vì sao giữ cả original lẫn equalKey?

Thí nghiệm đang kiểm tra hành vi Map, nên không muốn kết quả bị lẫn với việc khóa hết được giữ sống giữa các lần in.

`reachabilityFence` ở cuối bảo đảm các đối tượng cần thiết vẫn được giữ sống qua phần quan sát. Việc giữ cả hai cũng tránh phải dựa vào giả định constructor hay put sẽ chọn đối tượng nào làm đích lưu trữ cho một khóa bằng nhau.

Không dùng một lệnh put với equal key như một giao thức cam kết “đổi chủ sở hữu tuổi thọ” của ánh xạ.

## 6. Ví dụ 2: quan sát GC mà không giả vờ kiểm soát GC

### 6.1. Điều cần quan sát

Ta tạo ba ánh xạ:

- Một khóa held được giữ sống tới hết đoạn đo.
- Một khóa null.
- Một khóa tạm chỉ được trả ra ngoài qua WeakReference.

Hàm addTemporaryKey tạo khóa tạm rồi kết thúc, giúp đoạn main không giữ thêm biến mạnh tới khóa ấy.

Một WeakReference quan sát được đăng ký với `ReferenceQueue` riêng. Nó là công cụ đo; WeakHashMap có cơ chế nội bộ riêng.

### 6.2. Chương trình đầy đủ

Lưu thành **WeakKeyGcObservation.java**:

```java
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

public class WeakKeyGcObservation {
    private static WeakReference<Object> addTemporaryKey(
            WeakHashMap<Object, String> map, ReferenceQueue<Object> queue) {
        Object temporary = new Object();
        map.put(temporary, "temporary value");
        return new WeakReference<>(temporary, queue);
    }

    public static void main(String[] args) throws InterruptedException {
        WeakHashMap<Object, String> map = new WeakHashMap<>();
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object held = new Object();
        map.put(held, "held value");
        map.put(null, "null-key value");
        WeakReference<Object> observer = addTemporaryKey(map, queue);

        boolean cleared = false;
        boolean enqueued = false;
        boolean removed = false;
        for (int attempt = 0; attempt < 100; attempt++) {
            System.gc();
            Thread.sleep(10);
            cleared = observer.refersTo(null);

            Reference<?> notification;
            while ((notification = queue.poll()) != null) {
                if (notification == observer) {
                    enqueued = true;
                }
            }

            removed = map.size() == 2;
            if (cleared && enqueued && removed) {
                break;
            }
        }

        System.out.println("observer cleared: " + cleared);
        System.out.println("observer enqueued: " + enqueued);
        System.out.println("temporary mapping removed: " + removed);
        System.out.println("held lookup: " + map.get(held));
        System.out.println("null-key lookup: " + map.get(null));
        System.out.println("remaining size: " + map.size());
        Reference.reachabilityFence(held);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 WeakKeyGcObservation.java
java WeakKeyGcObservation
```

**Stdout dưới đây là kết quả quan sát của một lần chạy trên OpenJDK 24.0.2, không phải kết quả Java bảo đảm xuất hiện trong mọi lần chạy:**

```text
observer cleared: true
observer enqueued: true
temporary mapping removed: true
held lookup: held value
null-key lookup: null-key value
remaining size: 2
```

### 6.3. Ba điều được đo riêng

Observer cleared cho biết reference quan sát đã không còn đối tượng đích. `refersTo`(null) kiểm tra điều đó mà không cần lấy một tham chiếu mạnh mới bằng get.

Observer enqueued cho biết reference quan sát đã được lấy ra từ hàng đợi của thí nghiệm. Không dùng isEnqueued để chờ, vì trạng thái hàng đợi có thể thay đổi và API ấy đã bị đánh dấu deprecated.

Temporary mapping removed được suy ra từ size bằng 2 trong điều kiện chương trình chỉ có ba ánh xạ đã mô tả, không có luồng ứng dụng khác sửa map, held được giữ sống và khóa null không bị xóa. Đó không phải một công thức phát hiện mọi khóa chết trong một map bất kỳ.

Các lần gọi size cũng tạo cơ hội cho triển khai dọn những phần tử nội bộ đã hết hiệu lực.

### 6.4. Nếu ba cờ chưa thành true thì sao?

Vòng lặp có tối đa 100 lượt; mỗi lượt gửi gợi ý GC và ngủ 10 ms. Nó không phải cam kết GC phải hoàn tất trong một giây, cũng không bảo đảm tổng thời gian thực thi đúng một giây.

Nếu quan sát chưa hoàn tất, chương trình vẫn kết thúc và in trạng thái đã thấy. Không có AssertionError ép thời điểm thu gom thành điều kiện đúng của ứng dụng.

Theo [System.gc Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#gc()), lời gọi này chỉ gợi ý JVM nỗ lực thu hồi. Nó không bảo đảm thu hồi một đối tượng cụ thể, xóa/enqueue một số reference cụ thể hay hoàn tất vào thời điểm yêu cầu.

Thread.sleep không tạo bảo đảm ấy. Lặp System.gc thật nhiều cũng không biến yêu cầu thành một đồng hồ hết hạn đáng tin cậy.

### 6.5. Một lần chạy khác trên cùng JVM

OpenJDK/HotSpot hỗ trợ tùy chọn thử nghiệm sau:

```shell
java -XX:+DisableExplicitGC WeakKeyGcObservation
```

Trong môi trường kiểm tra của bài, lần chạy này in:

```text
observer cleared: false
observer enqueued: false
temporary mapping removed: false
held lookup: held value
null-key lookup: null-key value
remaining size: 3
```

Tùy chọn này làm các yêu cầu GC tường minh bị bỏ qua trong JVM đang dùng; nó không có nghĩa mọi hoạt động GC tự động đều bị tắt. JVM khác có thể không nhận tùy chọn này.

Hai stdout khác nhau đều phù hợp với mục đích chương trình. Lần chạy chưa thấy khóa tạm biến mất không đủ kết luận WeakHashMap bị rò rỉ. Cần phân tích đường giữ sống và khả năng GC đã thực sự xử lý reference hay chưa.

## 7. Bẫy quan trọng nhất: value giữ mạnh ngược về key

### 7.1. Bỏ biến bên ngoài chưa chắc bỏ đường giữ khóa

Nếu một value có trường owner trỏ về chính khóa, đồ thị có thêm đường mạnh; tài liệu [WeakHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html) cũng chỉ rõ trường hợp này:

```text
chương trình --mạnh--> map --mạnh--> value --mạnh--> key
                             phần tử --yếu--> key
```

Cạnh yếu không làm cạnh mạnh mất tác dụng. Map còn được giữ sống, value còn ở trong map và value còn giữ key thì khóa vẫn có đường được giữ sống.

Tương tự, value A có thể giữ khóa B, còn value B giữ khóa A. Nếu cả hai value được map giữ mạnh, một vòng liên hệ gián tiếp vẫn có thể khiến các khóa sống theo map.

Đây là lý do cần đọc toàn bộ đồ thị đối tượng, không chỉ nhìn kiểu WeakHashMap ở khai báo.

### 7.2. Chương trình nhìn thấy đường giữ mạnh

Lưu thành **ValueBackReference.java**:

```java
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

public class ValueBackReference {
    static final class Metadata {
        Object owner;

        Metadata(Object owner) {
            this.owner = owner;
        }
    }

    public static void main(String[] args) {
        WeakHashMap<Object, Metadata> map = new WeakHashMap<>();
        Object key = new Object();
        Metadata metadata = new Metadata(key);
        WeakReference<Object> observer = new WeakReference<>(key);
        map.put(key, metadata);

        key = null;
        metadata = null;
        System.gc();

        Metadata stored = map.values().iterator().next();
        Object owner = stored.owner;
        System.out.println("value holds key: " + (owner != null));
        System.out.println("observer refers to owner: " + observer.refersTo(owner));
        System.out.println("lookup through owner: " + (map.get(owner) == stored));

        stored.owner = null;
        System.out.println("owner field detached: " + (stored.owner == null));
        System.out.println("local owner still finds value: " + (map.get(owner) == stored));

        map.clear();
        System.out.println("size after explicit clear: " + map.size());
        System.out.println("observer still refers to local owner: " + observer.refersTo(owner));
        Reference.reachabilityFence(owner);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ValueBackReference.java
java ValueBackReference
```

Stdout:

```text
value holds key: true
observer refers to owner: true
lookup through owner: true
owner field detached: true
local owner still finds value: true
size after explicit clear: 0
observer still refers to local owner: true
```

Đây là ví dụ kiểm tra một đường tham chiếu mạnh đang tồn tại. Nó không cần GC thực hiện một lần thu gom thành công mới cho kết quả như trên.

### 7.3. Cách đọc từng giai đoạn

Sau khi key và metadata được gán null, map vẫn giữ Metadata, và Metadata vẫn giữ owner. Vì vậy có thể đi từ value trong map tới khóa, rồi dùng khóa ấy tra lại chính value.

Lệnh System.gc không thể biến một khóa còn đường giữ sống hợp lệ thành khóa không được sử dụng chỉ vì map tên là WeakHashMap.

Sau khi stored.owner được gán null, biến owner trong chương trình vẫn còn giữ khóa. `reachabilityFence` ở cuối giữ ranh giới này rõ ràng. Chỉ cắt trường owner chưa đủ làm mọi đường mạnh biến mất.

`clear` tháo các ánh xạ một cách tường minh, nên size bằng 0. Điều đó vẫn không thu gom đối tượng owner ngay, vì chương trình còn giữ nó.

Ba việc khác nhau là: cắt một đường tham chiếu, xóa một ánh xạ và GC thu hồi một đối tượng. Không dùng cùng từ “xóa” để suy ra cả ba đã xảy ra.

### 7.4. Chọn value phù hợp với mục đích phụ trợ

Nếu muốn dữ liệu phụ trợ không giữ đối tượng chính sống, value nên chứa thông tin không dẫn mạnh về đối tượng ấy: chẳng hạn số đo đã tính hoặc dữ liệu mô tả tách rời.

Cần kiểm tra cả liên hệ gián tiếp qua callback, đối tượng bao ngoài, collection và các value khác.

Một lựa chọn khác là chủ động dùng tham chiếu yếu trong phần value phù hợp với nghiệp vụ. Đó là thay đổi chính sách giữ dữ liệu: value có thể biến mất và nơi đọc phải chấp nhận điều đó.

Không thay mọi trường bằng WeakReference một cách máy móc. Nếu ứng dụng cần dữ liệu tồn tại ổn định, chính sách yếu có thể làm sai yêu cầu thay vì giải quyết nó.

## 8. Đối tượng khóa thật khác với “cùng một mã nghiệp vụ”

### 8.1. Vì sao tài liệu nhắc tới khóa theo identity?

Với một đối tượng dùng equals mặc định của Object, chỉ chính đối tượng ấy bằng nó theo identity. Sau khi đối tượng thật sự không còn dùng được nữa, ứng dụng không tạo một object mới rồi đòi nó phải bằng đối tượng đã mất.

Điều này làm chính sách vòng đời dễ hiểu: dữ liệu phụ trợ theo đúng đối tượng đang sống.

Tài liệu [WeakHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html) mô tả nhóm khóa này là mục đích sử dụng chính, đồng thời vẫn cho phép các kiểu dùng equals theo giá trị.

Đó là hướng dẫn chọn khóa, không phải việc WeakHashMap tự thay đổi cách so bằng của mọi kiểu.

### 8.2. Khóa theo giá trị có thể được tạo lại nhưng ánh xạ không được hứa giữ lại

Giả sử một lời gọi tạo một đối tượng mã khách hàng mới rồi put kết quả tra cứu. Khi lời gọi kết thúc, không còn nơi nào giữ đối tượng khóa đã lưu.

Lời gọi sau có thể tạo một đối tượng mã mới equals với mã cũ. Tuy nhiên, đối tượng mới không tự giữ đối tượng cũ sống, và ánh xạ cũ có thể đã biến mất sau GC.

Nếu mục đích là nhớ kết quả theo chuỗi mã qua nhiều yêu cầu, vòng đời của một object mã tạm thường không phải chính sách lưu giữ mong muốn.

Một kiểu khóa bất biến giúp equals/hashCode ổn định. Nó không tự làm object ấy được giữ sống lâu hơn trong một map khóa yếu.

### 8.3. Các nguồn giữ khóa ngoài dự tính

Nếu thí nghiệm không thấy khóa mất, cần xét những nơi có thể còn giữ nó:

- Một HashMap, danh sách, trường static hoặc đối tượng quản lý đang sống.
- Một value trỏ ngược về khóa.
- Một tác vụ hoặc callback còn nắm đối tượng liên quan.
- Một bản sao dữ liệu chứa chính đối tượng khóa.
- Công cụ quan sát hoặc đoạn mã đo đã giữ lại khóa vừa đọc.

Với String, dữ liệu dùng chung và các đối tượng có sẵn trong bộ nhớ đệm của thư viện cũng có thể làm thí nghiệm khó suy luận. Không dùng một literal hoặc một đối tượng dùng chung rồi mặc định chỉ còn map tham chiếu tới nó.

Trong ví dụ GC, new Object() được tạo ở hàm riêng nhằm tạo một khóa tạm không chia sẻ theo chủ đích. Cách đó giúp lập luận về thí nghiệm; vẫn không điều khiển thời điểm thu gom.

### 8.4. Chính việc quan sát có thể kéo dài tuổi thọ

WeakReference.get trả về một tham chiếu thông thường tới đối tượng đích nếu còn. Nếu giữ kết quả trong một biến đang dùng, thí nghiệm đã thêm đường mạnh trong giai đoạn ấy.

`refersTo`(null) phù hợp khi chỉ cần hỏi reference đã được xóa liên hệ hay chưa. `ReferenceQueue` phù hợp khi cần theo dõi thông báo đã enqueue.

Một danh sách chứa các khóa lấy từ map hoặc một HashMap sao chép các ánh xạ sẽ giữ mạnh những khóa được chép khi bản sao còn được sử dụng. Không giữ bản sao như vậy trong lúc đo rồi kết luận WeakHashMap không nhả khóa.

Riêng view keySet không đồng nghĩa một bản sao giữ mạnh toàn bộ khóa. Cần phân biệt view, iterator và dữ liệu đã chép ra.

Các thao tác reference và ranh giới giữ sống được mô tả trong [Reference Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html).

## 9. Những thay đổi có thể xảy ra dù ứng dụng không gọi remove

Một chương trình chỉ có một luồng vẫn có thể thấy ánh xạ giảm đi khi GC xử lý khóa. Đây là hành vi được mô tả trong [WeakHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html). Không có lời gọi remove trong mã nghiệp vụ không có nghĩa nội dung phải giữ nguyên.

Các quan sát hợp lệ có thể gồm size nhỏ hơn, isEmpty thay đổi, một phép tìm sau không còn thấy ánh xạ từng tồn tại.

Điều đó có ý nghĩa trực tiếp với cách viết ứng dụng:

- Không dùng size trước đó để cam kết chính xác số mục sẽ xử lý trong một lần duyệt sau.
- Không dùng dữ liệu phụ trợ yếu làm bằng chứng bắt buộc về việc một giao dịch đã tồn tại.
- Khi việc thiếu ánh xạ là trạng thái bình thường của thiết kế, chuẩn bị cách tính lại hoặc bỏ qua thích hợp.
- Khi cần giữ một đối tượng trong suốt thao tác, giữ đường tham chiếu mạnh có chủ đích tới chính đối tượng cần dùng.

Một ngoại lệ quan trọng trong cách suy luận: nếu chương trình đang giữ mạnh chính khóa đã lưu qua toàn bộ thao tác và không có ai xóa/tác động khác, GC không được tự làm đối tượng khóa ấy mất. Đừng diễn đạt tính không chắc chắn thành “mọi mục đều biến mất tùy tiện ngay cả khi khóa còn được dùng”.

### 9.1. Khả năng truy cập đồng thời là một vấn đề riêng

WeakHashMap không tự đồng bộ. Nếu nhiều luồng cùng truy cập có sửa đổi, phải chọn cơ chế bảo vệ thích hợp.

Collections.synchronizedMap có thể bao map; mọi truy cập cần đi qua lớp bao. Khi duyệt collection view bằng iterator, cần đồng bộ trên map được trả về theo quy tắc API. Xem [Collections.synchronizedMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map)).

Đồng bộ bảo vệ truy cập của các luồng ứng dụng. Nó không biến khóa yếu thành khóa mạnh và không cấm GC xử lý một khóa không còn được giữ sống.

Tương tự, một chuỗi kiểm tra rồi hành động vẫn cần được thiết kế như một thao tác nghiệp vụ nhất quán nếu có dữ liệu dùng chung. Một lớp bao không tự suy ra ý nghĩa nghiệp vụ của cả chuỗi.

### 9.2. Fail-fast không phải thông báo khóa đã bị thu gom

Iterator có cơ chế dừng sớm (fail-fast) đối với sửa cấu trúc theo hợp đồng và giới hạn best effort của nó. Không viết chương trình dựa vào ConcurrentModificationException để biết một khóa đã chết hoặc để bảo đảm an toàn luồng.

Trong OpenJDK 17, đường dọn phần tử do GC không vận hành như một lời gọi put/remove thông thường làm tăng bộ đếm sửa đổi. Iterator còn có thể giữ tạm khóa đang xét để dùng nó.

Vì thế không giả định mỗi lần GC bỏ khóa đều phải ném ngoại lệ, cũng không giả định một iterator là ảnh chụp bất biến của toàn map.

## 10. Giới hạn thực tế: đây không phải chính sách bộ nhớ đệm hoàn chỉnh

### 10.1. Không có thời gian hết hạn hoặc giới hạn số mục

WeakHashMap có thể giữ một ánh xạ rất lâu nếu khóa còn được dùng ở nơi khác. Nó cũng có thể mất ánh xạ sớm hơn mong đợi nếu khóa chỉ là object tạm.

Nó không định nghĩa thời hạn tồn tại (TTL), không tự giới hạn số ánh xạ theo một con số nghiệp vụ và không chọn mục ít dùng nhất.

Initial capacity là tham số tổ chức bảng băm, không phải số phần tử tối đa được phép lưu. Đổi load factor cũng không biến vòng đời khóa thành hạn sử dụng.

Nếu yêu cầu là giữ dữ liệu theo thời gian hoặc dung lượng, phải chọn chính sách thể hiện yêu cầu đó. Không thay câu hỏi “khi nào khóa hết sống?” cho câu hỏi “khi nào dữ liệu phải hết hạn?”.

### 10.2. Khóa yếu không đồng nghĩa giá trị yếu

Giả sử một khóa còn được ứng dụng giữ mạnh rất lâu và value chứa một mảng lớn. WeakHashMap vẫn giữ mạnh mảng ấy khi ánh xạ tồn tại.

Nếu chọn Map<K, WeakReference<V>> hoặc kết hợp thêm value yếu, lúc đọc còn phải phân biệt: không có ánh xạ, có wrapper nhưng đích đã bị xóa, hoặc có value đang dùng được.

Nên lấy value từ một WeakReference một lần vào biến cục bộ rồi kiểm tra và sử dụng giá trị đã lấy. Hai lần gọi get liên tiếp không phải một cam kết giá trị còn nguyên giữa hai lần nếu không giữ nó.

Đây là thiết kế giữ dữ liệu khác, không phải tính năng được kích hoạt sẵn bởi tên WeakHashMap.

### 10.3. Không dùng thời điểm GC để đóng tài nguyên bắt buộc

Nếu một thao tác cần đóng tệp, kết nối hoặc giải phóng tài nguyên ở một thời điểm xác định, hãy có cơ chế sở hữu và đóng tường minh phù hợp.

Việc một mục tự biến mất không tương đương lời gọi close đã chạy thành công. WeakHashMap không cung cấp một giao thức hoàn tất tài nguyên hay thông báo nghiệp vụ đáng tin cậy.

Không gắn tính đúng của ứng dụng với việc một vòng GC có diễn ra hay không.

### 10.4. Chi phí gồm cả cơ chế reference và dọn bảng

Cấu trúc vẫn dùng băm, nhưng có thêm đối tượng/cơ chế cho tham chiếu yếu và xử lý các phần tử hết hiệu lực. Một thao tác truy cập có thể kèm công việc dọn.

Không đo một lời gọi rồi kết luận mọi get đều có độ trễ cố định. Phân bố hash, kích thước bảng và công việc dọn đang chờ đều ảnh hưởng chi phí quan sát.

WeakHashMap là một lớp triển khai riêng; không mặc định mọi chi tiết nội bộ tối ưu của HashMap đều được sao chép sang nó.

## 11. Chọn cách dùng và cách kiểm tra

### 11.1. Một tình huống phù hợp

Một công cụ lưu kết quả phân tích phụ trợ cho các đối tượng đang được hệ thống chính giữ sống. Kết quả có thể tính lại, việc thiếu kết quả được xử lý bình thường, và value không dẫn mạnh về khóa.

Đây là một thiết kế có thể cân nhắc khóa yếu vì dữ liệu phụ trợ không nên quyết định tuổi thọ đối tượng chính.

Trước khi triển khai, vẫn cần trả lời ai giữ map, ai giữ key, value có giữ lại key gián tiếp không và các luồng truy cập thế nào.

### 11.2. Những yêu cầu nên được diễn đạt bằng chính sách khác

| Yêu cầu | Điều cần thiết kế rõ |
| --- | --- |
| Lưu bằng chứng nghiệp vụ bắt buộc | Cơ chế lưu giữ có tuổi thọ do nghiệp vụ quyết định |
| Giữ kết quả ít nhất một khoảng thời gian | Chính sách thời gian rõ ràng |
| Không vượt dung lượng cho phép | Chính sách dung lượng và loại bỏ rõ ràng |
| Gắn dữ liệu phụ trợ với một đối tượng đang sống | Quan hệ tham chiếu và khả năng tính lại khi thiếu |
| Đóng tài nguyên khi kết thúc thao tác | Quy trình đóng tường minh và xử lý lỗi |
| Tìm theo mã có thể tạo lại giữa các yêu cầu | Chính sách lưu theo mã, không mặc định theo tuổi thọ object mã tạm |

Không chọn cấu trúc chỉ vì nó có khả năng tự làm size nhỏ đi.

### 11.3. Tách kiểm thử hành vi Map khỏi quan sát GC

Kiểm thử hành vi Map có thể giữ mạnh khóa và xác nhận equals/hashCode, cập nhật giá trị, remove, null và cách ứng dụng xử lý thiếu dữ liệu.

Thí nghiệm GC nên:

1. Tạo khóa tạm không được chia sẻ theo chủ đích.
2. Tránh giữ bản sao khóa trong dữ liệu đo.
3. Dùng reference quan sát và hàng đợi nếu cần.
4. Có số lần quan sát hữu hạn.
5. Ghi rõ đã quan sát được hay chưa, không coi một hạn chờ tùy ý là hợp đồng JVM.
6. Kiểm tra riêng các đường giữ mạnh nếu kết quả khác mong đợi.

Để kiểm tra một cơ chế xử lý “không có dữ liệu phụ trợ”, có thể tạo trực tiếp tình huống ánh xạ vắng mặt bằng remove/clear trong kiểm thử nghiệp vụ. Không cần bắt GC làm đồng hồ để xác nhận nhánh ấy.

Nếu tự gọi clear trên một WeakReference do mình tạo, cần ghi rõ đó là xóa reference tường minh; không coi nó là bằng chứng GC đã thu gom khóa hay WeakHashMap đã dọn reference nội bộ.

## 12. Bài tập tự kiểm tra

### Bài 1. Vì sao put không bảo đảm giữ khóa sống?

Một object chỉ còn được WeakHashMap giữ làm khóa; value là dữ liệu không trỏ về object ấy. Việc có ánh xạ trong map có ngăn khóa được thu gom không?

**Gợi ý trả lời:** Cạnh từ phần tử tới khóa là yếu nên bản thân ánh xạ không giữ sống khóa theo cách tham chiếu mạnh. Thời điểm GC xử lý vẫn không được cam kết.

### Bài 2. Tại sao gán key bằng null chưa đủ?

Sau khi put(key, metadata), metadata.owner vẫn bằng key. Gán biến key bằng null có làm khóa chỉ còn được tham chiếu yếu không?

**Gợi ý trả lời:** Chưa. Nếu map còn sống, đường map → metadata → owner vẫn giữ mạnh khóa.

### Bài 3. Equal key có giữ được object cũ không?

Tạo một Key(12) mới equals với khóa đã lưu. Chỉ giữ object mới có tự giữ object cũ sống không?

**Gợi ý trả lời:** Không. Quan hệ equals là quy tắc bằng, không phải đường tham chiếu từ object mới tới object cũ.

### Bài 4. Một giây chưa thấy size giảm có phải lỗi không?

Sau System.gc và một khoảng ngủ, size vẫn như cũ. Có thể kết luận WeakHashMap không hoạt động không?

**Gợi ý trả lời:** Không. Chưa có bảo đảm GC đã xóa reference/enqueue/dọn ánh xạ đúng trong hạn đó; cũng cần kiểm tra còn đường mạnh nào hay không.

### Bài 5. Vì sao value lớn vẫn tồn tại?

Ứng dụng giữ mạnh key lâu dài. Value là một mảng lớn không còn nơi nào khác dùng. WeakHashMap có tự giữ value yếu không?

**Gợi ý trả lời:** Không. Value vẫn được map giữ mạnh khi ánh xạ còn; yếu là chính sách cho key.

### Bài 6. Key null có tự hết hạn không?

Đưa null → dữ liệu vào WeakHashMap rồi không giữ thêm biến khóa nào. Có nên chờ mục null được thu gom không?

**Gợi ý trả lời:** Không. Null không phải object khóa tạm có thể hết được giữ sống; triển khai OpenJDK dùng đối tượng đánh dấu được giữ tĩnh. Khi cần bỏ mục ấy, xóa tường minh.

### Bài 7. Đồng bộ có đóng băng map không?

Bao WeakHashMap bằng synchronizedMap và đồng bộ đúng khi duyệt. Các khóa đã không còn được giữ sống có bị ngăn GC chỉ nhờ đồng bộ map không?

**Gợi ý trả lời:** Không. Bảo vệ truy cập ứng dụng không thay chính sách tham chiếu tới khóa.

### Bài 8. Bản sao để đo có làm lệch thí nghiệm không?

Chép các khóa đang thấy vào một danh sách rồi giữ danh sách trong suốt thí nghiệm GC. Điều gì đã thay đổi?

**Gợi ý trả lời:** Danh sách giữ mạnh các object khóa đã chép. Chính dụng cụ đo có thể kéo dài tuổi thọ của chúng.

### Bài 9. Poll nhận về thứ gì?

Một WeakReference được đăng ký với `ReferenceQueue` và sau đó xuất hiện khi poll. Poll trả về object khóa cũ hay object reference?

**Gợi ý trả lời:** Object reference đã được enqueue. Không phải một bản sao khóa để tiếp tục dùng như trước.

### Bài 10. Khi nào nên chọn chính sách khác?

Một bảng kết quả cần giữ chính xác tối đa N mục theo thời gian truy cập gần nhất. Chỉ dùng WeakHashMap có đáp ứng yêu cầu không?

**Gợi ý trả lời:** Không. Cần chính sách dung lượng và loại bỏ theo truy cập; tuổi thọ object khóa không diễn đạt quy tắc đó.

## 13. Tài liệu đối chiếu

- [WeakHashMap — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html): hợp đồng khóa yếu, giá trị mạnh, null và những thay đổi do GC.
- [WeakReference — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html): xử lý tham chiếu yếu và thời điểm enqueue.
- [Reference — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html): get, refersTo, clear và reachabilityFence.
- [ReferenceQueue — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/ReferenceQueue.html): hàng đợi thông báo về reference.
- [System.gc — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#gc()): giới hạn của yêu cầu GC tường minh.
- [Map — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): hợp đồng equals, hashCode và khóa khả biến.
- [Collections.synchronizedMap — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map)): quy tắc truy cập qua lớp bao và đồng bộ khi duyệt.
- [JLS 17, mục 12.6.1](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.6.1): khả năng truy cập đối tượng và tối ưu vòng đời.
- [WeakHashMap.java — OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/WeakHashMap.java): đối tượng đánh dấu null, hàng đợi nội bộ, dọn phần tử và iterator. Các chi tiết này được gắn với phiên bản triển khai, không thay thế hợp đồng API.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="specialized-collections-004">IdentityHashMap: so sánh khóa theo tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# IdentityHashMap: so sánh khóa theo tham chiếu

Hai đối tượng có thể mang cùng dữ liệu nhưng vẫn là hai đối tượng khác nhau. Một bảng tra cứu theo mã khách hàng thường muốn gộp chúng theo giá trị mã. Một công cụ theo dõi các đối tượng trong bộ nhớ lại có thể cần giữ chúng riêng.

`IdentityHashMap` phục vụ yêu cầu thứ hai: đối với khóa, điều kiện khớp là hai tham chiếu cùng trỏ một đối tượng, tức `k1 == k2`. Nó không thay phép so sánh đó bằng `k1.equals(k2)`.

Bài này giải thích khi nào định danh đối tượng là điều cần giữ, cách phân biệt nó với bằng nhau theo giá trị và những hệ quả khi đưa IdentityHashMap qua các API Map thông thường. Ví dụ dùng mã nguồn tương thích Java 17+, không dựa vào thứ tự duyệt hoặc giá trị số của mã băm định danh.

## 1. Ba câu hỏi khác nhau

Giả sử tạo hai khóa có cùng mã:

~~~java
record Key(String code) {}

Key a = new Key("same");
Key b = new Key("same");
Key alias = a;
~~~

Đây là đoạn minh họa một phần. Với bản ghi này:

~~~text
a.equals(b) -> true
a == b      -> false
alias == a  -> true
~~~

Ba kết quả không mâu thuẫn:

- Equals hỏi hai khóa có bằng nhau theo quy tắc giá trị của lớp không.
- Phép == trên tham chiếu hỏi có cùng đối tượng không.
- Alias là tên biến khác giữ cùng tham chiếu, không phải một đối tượng khóa mới.

Có thể hình dung:

~~~text
a --------> đối tượng K1, code = "same"
alias ----> đối tượng K1

b --------> đối tượng K2, code = "same"
~~~

Một HashMap dùng quy tắc bằng nhau theo giá trị có thể coi K1 và K2 là cùng khóa logic. IdentityHashMap coi chúng là hai khóa vì K1 và K2 là hai đối tượng.

### 1.1. Chọn map là chọn ý nghĩa của khóa

Câu hỏi quyết định không phải “map nào lạ hơn hoặc nhanh hơn?”, mà là “hai đối tượng bằng dữ liệu có cần được xem như cùng một khóa không?”.

| Yêu cầu | Ngữ nghĩa phù hợp để xem xét |
|---|---|
| Hai yêu cầu có cùng mã nghiệp vụ tìm cùng dữ liệu | Khóa theo giá trị, thường là map thông thường |
| Theo dõi riêng từng đối tượng đang được xử lý | Khóa theo tham chiếu |
| Sao chép đồ thị, giữ hai nút khác đối tượng dù cùng nhãn | Khóa theo tham chiếu |
| Tra tên người dùng được tạo từ nhiều chuỗi bằng nội dung | Khóa theo giá trị |

IdentityHashMap được thiết kế cho các tình huống chuyên biệt như biến đổi đồ thị đối tượng và bảng liên hệ đối tượng với bản đại diện. Nó không phải cách thay thế mặc định cho HashMap. [Java 17: IdentityHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html)

### 1.2. Không dùng nó để che lỗi equals hoặc hashCode

Nếu nghiệp vụ muốn các mã bằng nhau là cùng khóa, lỗi triển khai equals/hashCode cần được sửa theo ý nghĩa đó. Chuyển sang IdentityHashMap chỉ để một lỗi băm tạm biến mất sẽ đổi ngữ nghĩa tra cứu.

Sau khi đổi, một đối tượng khóa mới có cùng mã có thể không tìm được dữ liệu. Đó không phải tối ưu miễn phí; ứng dụng đã chọn một quan hệ khóa khác.

## 2. Get, put và remove theo đúng tham chiếu

### 2.1. Hai đối tượng bằng nhau vẫn có hai ánh xạ

Với a và b ở trên:

~~~java
IdentityHashMap<Key, String> byObject = new IdentityHashMap<>();
byObject.put(a, "first");
byObject.put(b, "second");
~~~

ByObject có hai ánh xạ. Get a nhận first, get b nhận second. Get một Key mới có code same không nhận hai giá trị ấy, vì tham chiếu mới không trùng a hoặc b.

Ngược lại, đưa alias vào put là dùng lại đúng khóa a:

~~~java
byObject.put(alias, "updated");
~~~

Số ánh xạ vẫn là hai; giá trị gắn với đối tượng K1 được thay. Biến khác tên không làm xuất hiện khóa thứ ba.

### 2.2. ContainsKey và remove một đối số

ContainsKey cũng hỏi đối tượng tham chiếu đó có đang là khóa không. Remove(key) loại ánh xạ của đúng khóa tham chiếu được truyền.

Tạo một đối tượng mới bằng nội dung rồi gọi remove không có nghĩa “xóa mọi đối tượng cùng mã”. Nếu cần quy tắc ấy, phải dùng cấu trúc hoặc thuật toán biểu diễn rõ quan hệ theo mã.

Chương trình đầy đủ kiểm chứng hai lượt remove: một khóa mới bằng nội dung không xóa được a; alias của a xóa đúng ánh xạ a.

### 2.3. Giá trị null và khóa null

IdentityHashMap cho phép một khóa null và các giá trị null. Put null lần nữa cập nhật ánh xạ của khóa null, không tạo nhiều khóa null riêng.

Get trả null vẫn có hai khả năng: không tìm thấy đúng khóa tham chiếu hoặc khóa ấy đang ánh xạ tới null. Dùng containsKey khi cần phân biệt, trong bối cảnh truy cập được tổ chức phù hợp.

Khả năng có khóa null không làm những tham chiếu không null bằng nhau. Quy tắc == vẫn phân biệt các đối tượng bình thường.

## 3. Mã băm định danh không phải định danh duy nhất

### 3.1. Khác với phương thức hashCode bị ghi đè

`System.identityHashCode(object)` trả mã băm theo cách mặc định của Object ngay cả khi lớp đã ghi đè hashCode. Điều đó phục vụ việc tổ chức tra cứu theo định danh đối tượng. [Java 17: System.identityHashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#identityHashCode(java.lang.Object))

Không nên suy từ đó rằng:

- Mã băm chính là địa chỉ bộ nhớ.
- Hai đối tượng khác nhau luôn có hai số khác nhau.
- Số ấy là khóa bền vững để lưu và dùng lại ở một lần chạy khác.

Một bảng băm vẫn phải xử lý va chạm; bước kiểm tra đúng khóa là so sánh tham chiếu. Mã băm là công cụ phân bố, không phải phép thay thế cho ==.

Hợp đồng hashCode không yêu cầu mọi đối tượng khác nhau có mã băm khác nhau. [Java 17: Object.hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#hashCode())

### 3.2. Không thay IdentityHashMap bằng Map<Integer, V> của identityHashCode

Nếu chỉ dùng số identityHashCode làm khóa trong một HashMap, hai đối tượng va chạm có thể bị coi là cùng khóa số nguyên. Cách đó đã bỏ mất bước phân biệt tham chiếu mà IdentityHashMap cung cấp.

Tương tự, không cần in mã băm cụ thể để kiểm chứng bài học. Các số phụ thuộc lần chạy và không diễn đạt quan hệ nghiệp vụ. Chương trình dùng các phép ==, equals, size và tra cứu có kết quả ổn định.

### 3.3. Đừng dùng đối tượng được chia sẻ tình cờ làm phép thử duy nhất

Chuỗi có thể được dùng chung, và các lớp bao số có thể có cơ chế dùng chung đối tượng trong những trường hợp nhất định. Một ví dụ chỉ dùng các giá trị hằng có thể vô tình đang truyền lại cùng tham chiếu.

Muốn kiểm chứng khác biệt, hãy tạo rõ hai đối tượng mới rồi kiểm tra a != b. Bản ghi Key trong bài làm điều đó trực tiếp.

Các lệnh new String trong phần so giá trị cũng chỉ để tạo hai đối tượng chắc chắn khác tham chiếu cho thí nghiệm. Không phải khuyến nghị tạo chuỗi dư thừa trong mã ứng dụng thông thường.

## 4. Nội bộ bảng băm và ý nghĩa hiệu năng

Tài liệu Java 17 mô tả cách triển khai dạng dò tuyến tính: bảng chứa xen kẽ khóa và giá trị, việc va chạm được xử lý bằng tìm vị trí phù hợp trong bảng.

Mô hình khái niệm:

~~~text
tính vị trí bắt đầu từ mã băm định danh
    ↓
xem vị trí đang xét
    ↓
so khóa bằng ==; nếu chưa khớp thì tiếp tục theo cách dò của bảng
~~~

Sơ đồ này giải thích vì sao vẫn cần kiểm tra tham chiếu dù đã có mã băm. Nó không phải mã nguồn hoặc cam kết bố cục cho mọi phiên bản JVM.

Get và put có hiệu năng thời gian hằng số khi hàm băm định danh phân bố phù hợp. Không được biến điều kiện ấy thành lời hứa thời gian như nhau cho mọi dữ liệu hoặc cho mọi thao tác.

Constructor nhận `expectedMaxSize` là số ánh xạ tối đa dự kiến. Nó không phải số ô mảng được API hứa cấp đúng bằng tham số, cũng không phải giới hạn cứng khiến map ngừng nhận phần tử khi vượt số đó. Chọn quá lớn còn ảnh hưởng duyệt và bộ nhớ. [Java 17: mô hình và tham số của IdentityHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html)

Không chọn IdentityHashMap chỉ vì nghe nói bảng dò tuyến tính có thể nhanh. Quan hệ khóa phải đúng trước, rồi mới xem xét số liệu của tải công việc.

## 5. Khi đối tượng khóa thay đổi nội dung

Một lớp có thể định nghĩa equals/hashCode theo trường có thể thay đổi. IdentityHashMap vẫn tra theo cùng đối tượng khi trường đó đổi, vì định danh tham chiếu chưa đổi.

Ví dụ trong chương trình:

~~~text
đối tượng mutable có code "old"
put(mutable, "tracked")

đổi mutable.code thành "new"
get(mutable) -> "tracked"
get(new MutableKey("new")) -> không tìm thấy
~~~

Điều này có ích khi đang theo dõi chính đối tượng qua các bước biến đổi. Nhưng không chứng minh IdentityHashMap là cách sửa đúng cho mọi khóa có thể thay đổi.

Nếu mục đích là tra cứu theo mã mới, cần thiết kế khóa theo giá trị ổn định hoặc quản lý thay đổi ánh xạ thích hợp. Theo dõi đối tượng và lập chỉ mục theo trường là hai yêu cầu khác nhau.

IdentityHashMap cũng giữ tham chiếu tới khóa và giá trị như dữ liệu của map; nó không tự trở thành một map khóa yếu chỉ vì tên có chữ Identity. Bảng theo dõi lâu dài cần có vòng đời và cách giải phóng phù hợp với tác vụ.

## 6. Giá trị, góc nhìn và hợp đồng Map thông thường

### 6.1. ContainsValue cũng có ý nghĩa tham chiếu

Với một String value và một String equalValue khác đối tượng nhưng cùng nội dung:

~~~text
value.equals(equalValue) -> true
value == equalValue     -> false
~~~

Nếu map giữ value, containsValue(value) trả true, còn containsValue(equalValue) trả false. Điều này khác kỳ vọng phổ biến từ map theo bằng nhau giá trị.

Không nên suy từ việc get trả một chuỗi có nội dung label rằng mọi đối tượng chuỗi label đều được xem là cùng giá trị tham chiếu trong các thao tác này.

### 6.2. Các view mang theo ngữ nghĩa đặc biệt

KeySet là view theo định danh khóa. Values và các entry của entrySet cũng có các quy tắc so sánh theo tham chiếu được lớp mô tả.

Ví dụ, một entry biểu diễn cùng tham chiếu key và value có thể được tìm thấy trong entrySet. Nếu chỉ thay value bằng một đối tượng bằng nội dung nhưng khác tham chiếu, kết quả kiểm tra có thể khác.

Không coi keySet của IdentityHashMap như một Set giá trị thông thường rồi trộn các thao tác so sánh, removeAll hoặc chuyển đổi mà không đọc hợp đồng của bên nhận. View còn dựa trên map gốc; sửa được hỗ trợ qua view có thể sửa dữ liệu gốc.

### 6.3. Map.equals với map thông thường có thể mất tính đối xứng

IdentityHashMap cố ý khác hợp đồng Map tổng quát vốn dựa vào equals. Vì vậy, so sánh nó với một map thông thường có thể cho hai kết quả khác nhau theo chiều gọi.

Chương trình dùng cùng key tham chiếu nhưng hai value khác đối tượng, bằng nội dung:

~~~text
normal.equals(identity) -> true
identity.equals(normal) -> false
~~~

Normal so giá trị theo equals; identity đòi cặp tham chiếu theo ngữ nghĩa của nó. Đây là hệ quả đã được tài liệu lớp nêu, không phải một cơ chế nên dùng để xây dựng quan hệ bằng nhau chung cho mọi map. [Java 17: IdentityHashMap.equals và các view](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html)

Do đó, nên giữ bảng định danh ở ranh giới nội bộ rõ ràng khi tác vụ cần nó. Một tham số kiểu Map không tự bảo đảm mọi map truyền vào có cùng quan hệ khóa.

### 6.4. Điểm khác biệt phiên bản ở các overload có điều kiện

Có một chi tiết thực tế cần tách khỏi quy tắc khóa: Oracle công bố trong JDK 20 rằng remove(key, value) và replace(key, oldValue, newValue) của IdentityHashMap đã được sửa. Các bản trước đó dùng equals khi so giá trị đối số, trái với ngữ nghĩa tham chiếu mà lớp quy định; sau sửa, các phép so ấy dùng định danh đối tượng. [Oracle: ghi chú sửa đổi IdentityHashMap trong JDK 20](https://www.oracle.com/java/technologies/javase/20-relnote-issues.html)

Vì ví dụ hướng tới mã tương thích 17+, bài không dùng hai overload đó để đưa ra stdout chung cho mọi runtime. Khi kiểm tra một môi trường cũ hoặc chuyển phiên bản, cần đọc hành vi phương thức trên bản JDK thực tế.

Điều này không thay đổi điểm đang học: get, containsKey, put và remove(key) phải được hiểu theo đúng tham chiếu khóa. Đồng thời, không nên mở rộng một nhận xét đúng về khóa thành một lời hứa thiếu điều kiện cho mọi overload về giá trị.

## 7. Chuyển đổi map có thể đổi ý nghĩa dữ liệu

### 7.1. Sao chép sang HashMap có thể gộp khóa

IdentityHashMap có thể chứa hai khóa a và b khác tham chiếu nhưng a.equals(b) là true. Sao chép sang HashMap đưa các khóa đó vào quy tắc bằng nhau giá trị của đích, nên có thể chỉ còn một ánh xạ.

Không hứa giá trị của a hoặc b chắc chắn thắng. IdentityHashMap không cam kết thứ tự duyệt, nên không dùng thứ tự tình cờ khi sao chép làm chính sách giải quyết xung đột.

Chương trình chỉ kiểm tra số ánh xạ sau chuyển đổi. Nó cố ý không khẳng định giá trị nào được giữ.

### 7.2. Chuyển ngược không phục hồi thông tin đã mất

Nếu HashMap đã gộp hai đối tượng bằng giá trị thành một ánh xạ, tạo IdentityHashMap từ nó không tự tìm lại đối tượng hoặc ánh xạ bị loại trước đó.

Cần quyết định ngữ nghĩa từ lúc xây dựng bảng, không đợi chuyển kiểu ở cuối rồi mong lấy lại định danh đã không được lưu.

### 7.3. Sao chép identity map không sao chép sâu đối tượng

Tạo một IdentityHashMap mới từ bảng định danh có thể giữ các cặp tham chiếu với cấu trúc ánh xạ riêng. Xóa ở bản sao không tự xóa ở nguồn.

Tuy vậy, các key và value vẫn là những đối tượng được tham chiếu, không phải bản clone sâu. Cần phân biệt “sao chép bảng liên hệ” với “sao chép đồ thị đối tượng mà bảng đang mô tả”.

## 8. Tình huống đúng: sao chép đồ thị giữ quan hệ tham chiếu

### 8.1. Vì sao nhãn bằng nhau không được gộp nút?

Xét đồ thị:

~~~text
root
 ├─ left  (label = "same") ─┐
 └─ right (label = "same") ─┴─> shared ("leaf") ─> root
~~~

Left và right là hai đối tượng khác nhau nhưng được định nghĩa equals theo label nên bằng nhau. Chúng cần có hai bản sao riêng.

Shared là một đối tượng được cả hai nhánh trỏ tới, nên trong đồ thị sao chép cũng phải chỉ có một bản sao shared được dùng chung. Cạnh từ shared quay lại root tạo chu trình; thuật toán phải kết thúc thay vì gọi đệ quy vô hạn.

Như vậy có hai điều đồng thời cần giữ:

- Khác đối tượng ở nguồn không bị gộp chỉ vì bằng nhãn.
- Cùng đối tượng được nhiều nơi tham chiếu phải đi tới cùng một đối tượng bản sao.

Đây là quan hệ hình dạng tham chiếu của đồ thị, không chỉ danh sách nhãn.

### 8.2. Bảng đã xử lý ánh xạ nguồn sang bản sao

Dùng bảng:

~~~text
đúng đối tượng nguồn -> đúng đối tượng bản sao đã tạo
~~~

Mỗi lần sao chép một Node:

1. Nếu nguồn null, trả null.
2. Nếu bảng đã có bản sao cho đúng nguồn đó, dùng lại bản sao.
3. Nếu chưa có, tạo Node đích.
4. Đặt cặp nguồn–đích vào bảng trước khi đi sâu vào các con.
5. Sao chép các cạnh con bằng cùng bảng.

Bước đặt vào bảng trước đệ quy là điều giúp xử lý chu trình. Lúc quay về root, bảng đã có root đích để trả lại.

Trong chương trình, mọi value của bảng seen là Node bản sao khác null, nên get trả null được dùng làm dấu chưa xử lý. Đó là điều kiện riêng của thuật toán này; không áp nó cho một map tùy ý có thể lưu value null.

### 8.3. Phạm vi của ví dụ

Hàm copy chỉ sao chép lớp Node được định nghĩa trong bài và danh sách cạnh của nó. Nó không phải thư viện sao chép sâu mọi đối tượng Java, không tự xử lý mọi tài nguyên hoặc mọi chính sách chia sẻ.

Nguồn được xem là ổn định trong lúc sao chép. Với đồ thị rất sâu hoặc nguồn đang được sửa cạnh tranh, thiết kế duyệt và chính sách truy cập cần được tổ chức theo yêu cầu thực tế.

## 9. Chương trình Java đầy đủ

Lưu toàn bộ mã sau vào `IdentityKeysDemo.java`. Chương trình không in bản đồ theo thứ tự duyệt và không in các số identity hash, nên stdout không phụ thuộc những chi tiết không được cam kết.

Nó dùng các kiểu lồng trong một lớp để có thể biên dịch từ một tệp. Ví dụ bao gồm cả HashMap để đối chiếu, nhưng không chọn IdentityHashMap dựa vào kết quả thời gian chạy.

~~~java
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class IdentityKeysDemo {
    record Key(String code) {}

    static final class MutableKey {
        String code;

        MutableKey(String code) {
            this.code = code;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MutableKey key && code.equals(key.code);
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }
    }

    static final class Node {
        final String label;
        final List<Node> children = new ArrayList<>();

        Node(String label) {
            this.label = label;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Node node && label.equals(node.label);
        }

        @Override
        public int hashCode() {
            return label.hashCode();
        }
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Unexpected identity result");
        }
    }

    private static Node copy(Node source, IdentityHashMap<Node, Node> seen) {
        if (source == null) {
            return null;
        }
        Node existing = seen.get(source);
        if (existing != null) {
            return existing;
        }
        Node target = new Node(source.label);
        seen.put(source, target);
        for (Node child : source.children) {
            target.children.add(copy(child, seen));
        }
        return target;
    }

    public static void main(String[] args) {
        Key a = new Key("same");
        Key b = new Key("same");
        Key alias = a;
        require(a.equals(b) && a != b && alias == a);
        System.out.println("keys/equals=" + a.equals(b)
                + ", sameReference=" + (a == b)
                + ", aliasSame=" + (alias == a));

        Map<Key, String> ordinary = new HashMap<>();
        ordinary.put(a, "first");
        ordinary.put(b, "second");
        require(ordinary.size() == 1);
        System.out.println("hash/size=" + ordinary.size()
                + ", getA=" + ordinary.get(a));

        IdentityHashMap<Key, String> identity = new IdentityHashMap<>();
        identity.put(a, "first");
        identity.put(b, "second");
        require(identity.size() == 2);
        System.out.println("identity/size=" + identity.size()
                + ", getA=" + identity.get(a) + ", getB=" + identity.get(b));
        Key equalNew = new Key("same");
        System.out.println("identity/equalNewContains="
                + identity.containsKey(equalNew)
                + ", get=" + identity.get(equalNew));
        String previous = identity.put(alias, "updated");
        require(identity.size() == 2 && previous.equals("first"));
        System.out.println("identity/aliasPrevious=" + previous
                + ", size=" + identity.size());
        System.out.println("identity/removeEqualNew=" + identity.remove(equalNew)
                + ", size=" + identity.size());
        System.out.println("identity/removeAlias=" + identity.remove(alias)
                + ", size=" + identity.size());

        IdentityHashMap<Key, Integer> distinct = new IdentityHashMap<>();
        distinct.put(a, 1);
        distinct.put(b, 2);
        Map<Key, Integer> hashCopy = new HashMap<>(distinct);
        IdentityHashMap<Key, Integer> identityFromHash =
                new IdentityHashMap<>(hashCopy);
        require(distinct.size() == 2 && hashCopy.size() == 1
                && identityFromHash.size() == 1);
        System.out.println("conversion/identity=" + distinct.size()
                + ", hashCopy=" + hashCopy.size()
                + ", identityFromHash=" + identityFromHash.size());
        IdentityHashMap<Key, Integer> shallowCopy =
                new IdentityHashMap<>(distinct);
        shallowCopy.remove(a);
        require(distinct.size() == 2 && shallowCopy.size() == 1);
        System.out.println("copy/sourceSize=" + distinct.size()
                + ", copySize=" + shallowCopy.size());

        Object handle = new Object();
        String value = new String("label");
        String equalValue = new String("label");
        IdentityHashMap<Object, String> values = new IdentityHashMap<>();
        values.put(handle, value);
        System.out.println("values/equals=" + value.equals(equalValue)
                + ", sameContains=" + values.containsValue(value)
                + ", equalContains=" + values.containsValue(equalValue));
        System.out.println("entries/sameReferences="
                + values.entrySet().contains(
                        new AbstractMap.SimpleEntry<>(handle, value))
                + ", equalValue=" + values.entrySet().contains(
                        new AbstractMap.SimpleEntry<>(handle, equalValue)));
        Map<Object, String> normal = new HashMap<>();
        normal.put(handle, equalValue);
        require(normal.equals(values) && !values.equals(normal));
        System.out.println("equals/normalToIdentity=" + normal.equals(values)
                + ", identityToNormal=" + values.equals(normal));

        IdentityHashMap<Object, String> nullable = new IdentityHashMap<>();
        Object presentWithNull = new Object();
        nullable.put(null, "null-key");
        nullable.put(presentWithNull, null);
        System.out.println("null/keyValue=" + nullable.get(null)
                + ", present=" + nullable.containsKey(presentWithNull)
                + ", mappedValue=" + nullable.get(presentWithNull));

        MutableKey mutable = new MutableKey("old");
        IdentityHashMap<MutableKey, String> tracking = new IdentityHashMap<>();
        tracking.put(mutable, "tracked");
        mutable.code = "new";
        require(tracking.get(mutable).equals("tracked"));
        System.out.println("mutable/sameReference=" + tracking.get(mutable)
                + ", equalNew=" + tracking.get(new MutableKey("new")));

        Node root = new Node("root");
        Node left = new Node("same");
        Node right = new Node("same");
        Node shared = new Node("leaf");
        root.children.add(left);
        root.children.add(right);
        left.children.add(shared);
        right.children.add(shared);
        shared.children.add(root);

        IdentityHashMap<Node, Node> seen = new IdentityHashMap<>();
        Node copiedRoot = copy(root, seen);
        Node copiedLeft = copiedRoot.children.get(0);
        Node copiedRight = copiedRoot.children.get(1);
        Node copiedShared = copiedLeft.children.get(0);
        require(left.equals(right) && copiedLeft != copiedRight);
        require(copiedShared == copiedRight.children.get(0));
        require(copiedShared.children.get(0) == copiedRoot);
        require(copiedRoot != root && seen.size() == 4);
        System.out.println("graph/equalSources=" + left.equals(right)
                + ", distinctCopies=" + (copiedLeft != copiedRight));
        System.out.println("graph/sharedChild="
                + (copiedShared == copiedRight.children.get(0)));
        System.out.println("graph/cycleReturnsCopiedRoot="
                + (copiedShared.children.get(0) == copiedRoot));
        System.out.println("graph/sourceSeparate=" + (copiedRoot != root)
                + ", memoSize=" + seen.size());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 IdentityKeysDemo.java
java IdentityKeysDemo
~~~

Stdout của lần chạy kiểm chứng:

~~~text
keys/equals=true, sameReference=false, aliasSame=true
hash/size=1, getA=second
identity/size=2, getA=first, getB=second
identity/equalNewContains=false, get=null
identity/aliasPrevious=first, size=2
identity/removeEqualNew=null, size=2
identity/removeAlias=updated, size=1
conversion/identity=2, hashCopy=1, identityFromHash=1
copy/sourceSize=2, copySize=1
values/equals=true, sameContains=true, equalContains=false
entries/sameReferences=true, equalValue=false
equals/normalToIdentity=true, identityToNormal=false
null/keyValue=null-key, present=true, mappedValue=null
mutable/sameReference=tracked, equalNew=null
graph/equalSources=true, distinctCopies=true
graph/sharedChild=true
graph/cycleReturnsCopiedRoot=true
graph/sourceSeparate=true, memoSize=4
~~~

Mã đã biên dịch với `--release 17` và chạy thành công. Tùy chọn release xác định mức API và mã đích tương thích; nó không khẳng định JVM thực thi là đúng bản JDK 17.

## 10. Đọc các nhóm kết quả

### 10.1. Khóa bằng giá trị và alias

HashMap giữ một ánh xạ sau hai lần put với a và b bằng nội dung. IdentityHashMap giữ hai. Khóa mới bằng nội dung không tìm thấy ánh xạ identity.

Alias cập nhật ánh xạ a, không tăng size. Remove khóa mới không xóa gì; remove alias xóa đúng ánh xạ a. Các bước này tách được khái niệm một biến mới khỏi một đối tượng mới.

### 10.2. Chuyển đổi và sao chép

Chuyển hai khóa định danh bằng giá trị sang HashMap chỉ còn một ánh xạ. Tạo identity map từ kết quả ấy vẫn chỉ có một. Bài không in hoặc khẳng định người thắng của xung đột.

Bản sao identity có cấu trúc riêng nên xóa a khỏi copy không làm nguồn mất a. Nó vẫn dùng các tham chiếu khóa tương ứng, không tự tạo a và b mới.

### 10.3. Giá trị và phép so sánh map

Hai String value bằng nội dung nhưng khác đối tượng cho kết quả containsValue khác nhau. EntrySet cũng phân biệt hai cặp có cùng key nhưng value khác tham chiếu.

Dòng equals thể hiện sự bất đối xứng khi trộn ngữ nghĩa identity với map thông thường. Nó nhắc rằng tính phù hợp với một API không thể chỉ suy từ việc đối tượng triển khai Map.

### 10.4. Null và trường có thể đổi

Map có thể giữ khóa null và một ánh xạ khác có value null. ContainsKey giúp phân biệt ánh xạ tồn tại với vắng.

Đổi code của mutable không làm nó thành một đối tượng khác. Bảng vẫn tìm được bằng đúng tham chiếu; một đối tượng mới cùng code không khớp.

### 10.5. Đồ thị

Hai nguồn cùng label được sao chép thành hai Node riêng. Hai nhánh dùng cùng bản sao shared, và cạnh vòng quay về copiedRoot.

Seen có bốn cặp, tương ứng bốn đối tượng nguồn root, left, right, shared. Nếu dùng quan hệ equals theo label cho bảng ghi nhớ, left và right có thể bị gộp sai dù chúng cần giữ hai định danh.

## 11. Giới hạn khi sử dụng trong ứng dụng

IdentityHashMap không cam kết thứ tự duyệt và không tự đồng bộ hóa. Nếu cần hiển thị có thứ tự, phải tạo kết quả theo một quy tắc rõ ràng; không đoán thứ tự từ mã băm hoặc thứ tự thêm.

Iterator fail-fast là cơ chế phát hiện một số sửa đổi không phù hợp theo nỗ lực tốt nhất. Nó không thay cho cơ chế phối hợp nhiều luồng và không được dùng làm điều kiện tính đúng.

Với bảng ghi nhớ phục vụ một lần biến đổi đồ thị, giữ nó cục bộ theo vòng đời tác vụ thường giúp ranh giới ý nghĩa rõ ràng: nó nói về định danh các đối tượng của tác vụ ấy. Không tự đưa bảng đó thành một chỉ mục nghiệp vụ lâu dài nếu hệ thống cần tra theo mã giá trị.

## 12. Các lỗi dễ gặp

| Lỗi | Cách hiểu đúng |
|---|---|
| Hai đối tượng equals thì luôn là cùng khóa | IdentityHashMap xét == cho khóa |
| Hai tên biến khác nhau là hai khóa | Hai biến có thể cùng tham chiếu |
| Tạo lại một chuỗi cùng nội dung để get là đủ | Phải có đúng tham chiếu nếu dùng làm khóa identity |
| IdentityHashMap sửa được mọi lỗi khóa mutable | Nó đổi quan hệ khóa; chỉ đúng khi định danh đối tượng là yêu cầu |
| IdentityHashCode luôn là số duy nhất | Mã băm có thể va chạm; vẫn cần so tham chiếu |
| Identity hash là địa chỉ hoặc mã bền vững | API không hứa như vậy |
| Map<Integer,V> của identity hash thay được identity map | Nó mất bước phân biệt tham chiếu khi mã va chạm |
| Map.equals luôn đối xứng khi trộn mọi map | Ngữ nghĩa identity có thể phá kỳ vọng với map thông thường |
| Chuyển sang HashMap giữ nguyên mọi ánh xạ | Các khóa equals có thể bị gộp |
| Chuyển ngược phục hồi các khóa đã gộp | Thông tin đã mất không tự được tái tạo |
| Sao chép bảng là sao chép sâu các đối tượng | Cấu trúc mới vẫn có thể giữ cùng tham chiếu |
| Đặt memo sau khi sao chép con vẫn xử lý được mọi chu trình | Cần ghi nhận bản sao trước khi đi sâu để dùng lại khi gặp vòng |
| Mọi overload đã giống nhau trên mọi JDK cũ | Phải xét các sửa đổi phiên bản đã công bố |
| Không có CME nghĩa là an toàn đồng thời | Fail-fast không phải cơ chế đồng bộ hóa |

## 13. Thực hành và lời giải

### Bài 1. Hai đối tượng, ba biến

A và B là hai Key mới có cùng code; C được gán bằng A. Put cả A, B, C vào IdentityHashMap. Có tối đa bao nhiêu ánh xạ của ba lần put này?

**Lời giải:** Hai, nếu A và B khác tham chiếu. C dùng lại khóa A nên cập nhật cùng ánh xạ. Số biến không quyết định số định danh khóa.

### Bài 2. Tra cứu theo mã nghiệp vụ

Một dịch vụ tạo đối tượng Key mới cho mỗi yêu cầu, nhưng cùng code phải tra cùng thông tin. IdentityHashMap có phù hợp chỉ vì các đối tượng Key là bất biến không?

**Lời giải:** Không. Tính bất biến không thay đổi việc hai đối tượng mới khác tham chiếu. Yêu cầu đang là tra theo giá trị code, cần cấu trúc và quy tắc equals/hashCode phù hợp.

### Bài 3. Mã băm định danh

Hai đối tượng có cùng identityHashCode. Có được kết luận chúng là cùng đối tượng không? Có nên dùng số ấy làm khóa duy nhất của bảng thay cho hai tham chiếu không?

**Lời giải:** Không. Va chạm mã băm không chứng minh cùng định danh. Cần giữ bước so tham chiếu; một HashMap chỉ có khóa số đã không còn phân biệt được hai nguồn chỉ từ số ấy.

### Bài 4. Chuyển sang map thông thường

IdentityMap chứa hai khóa khác đối tượng nhưng equals và có giá trị khác nhau. Sao chép sang HashMap rồi sao chép ngược có bảo toàn hai ánh xạ không? Có thể hứa giá trị nào thắng không?

**Lời giải:** Không bảo toàn: HashMap có thể gộp thành một khóa logic, chuyển ngược không phục hồi thông tin. Không hứa người thắng dựa trên thứ tự duyệt không được cam kết của nguồn; nếu cần giải quyết xung đột phải có chính sách rõ ràng.

### Bài 5. Memo trong đồ thị

Hai Node cùng label nhưng khác đối tượng đều trỏ tới cùng một leaf. Khi sao chép, cần bao nhiêu bản sao cho hai Node và leaf? Vì sao bảng theo equals của label không đáp ứng đúng?

**Lời giải:** Cần hai bản sao riêng cho hai Node và một bản sao leaf dùng chung. Equals theo label có thể gộp hai Node khác định danh, làm sai quan hệ đồ thị.

### Bài 6. Ghi memo trước hay sau?

Đồ thị có A trỏ B, B trỏ A. Vì sao phải đặt cặp nguồn–đích vào memo trước khi sao chép các con?

**Lời giải:** Khi duyệt từ B quay về A, thuật toán phải tìm thấy A đích đã được tạo. Nếu chưa ghi nhận, nó có thể tiếp tục tạo và đi sâu lặp lại thay vì đóng cạnh vòng về bản sao đã có.

### Bài 7. Null và ánh xạ tồn tại

Get trả null với một khóa tham chiếu. Điều đó có đủ chứng minh khóa chưa có không?

**Lời giải:** Không. Khóa có thể đang ánh xạ tới null. ContainsKey phân biệt trong bối cảnh truy cập hợp lệ. Với thuật toán memo chỉ lưu bản sao khác null, điều kiện riêng ấy mới cho phép dùng null làm dấu chưa xử lý.

### Bài 8. Sự cố khi so map

Một map thông thường bằng nội dung với identity map theo một chiều gọi equals nhưng không theo chiều ngược. Có nên “sửa” bằng cách so chuỗi toString của hai map không?

**Lời giải:** Không. ToString không thiết lập quan hệ bằng nhau đúng, thứ tự identity map không được cam kết và nội dung in có thể giống dù tham chiếu khác. Cần chọn quan hệ so sánh mà bài toán thực sự yêu cầu và kiểm tra dữ liệu theo quan hệ ấy.

## 14. Tự xác định ranh giới định danh

Trước khi dùng IdentityHashMap, hãy mô tả được hai đối tượng bằng nội dung nhưng khác tham chiếu có phải giữ hai ánh xạ hay không. Sau đó xác định cách tạo khóa, vòng đời bảng và các API sẽ nhận bảng hoặc view của nó.

Khi định danh đối tượng là yêu cầu, phép == là ý nghĩa cốt lõi của khóa. Khi giá trị nghiệp vụ là yêu cầu, giữ nguyên ngữ nghĩa theo giá trị sẽ giúp tránh biến một cấu trúc chuyên biệt thành nguồn lỗi tra cứu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="specialized-collections-005">BitSet và khác biệt với Set&lt;Integer&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# BitSet và khác biệt với Set<Integer>

## 1. Từ tập chỉ số đến một dãy bit

Giả sử một chương trình có các vị trí đánh số từ 0 trở lên và cần ghi nhận vị trí nào đang được chọn. Nếu các vị trí 2 và 5 được chọn, có thể lưu hai số trong `Set<Integer>`. Một cách biểu diễn khác là đặt bit tại chỉ số 2 và 5 thành true, còn các bit khác là false.

`BitSet` là một dãy bit có thể mở rộng khi cần. Mỗi chỉ số nguyên không âm tương ứng một trạng thái boolean. Khi dùng như tập hợp, ta quy ước:

```text
bit i = true  → chỉ số i thuộc tập đang biểu diễn
bit i = false → chỉ số i không thuộc tập đang biểu diễn

Chỉ số: 0 1 2 3 4 5 6 7
Bit:    0 0 1 0 0 1 0 0
Tập:    {2, 5}
```

Số 0 trong hàng Bit nghĩa là false, không có nghĩa phần tử số 0 đang được chọn. Muốn số 0 thuộc tập, phải đặt chính bit ở chỉ số 0 thành true.

BitSet phù hợp với các mặt nạ đánh dấu, tập chỉ số, các nhóm điều kiện có cùng miền đánh số và phép giao/hợp trên nhiều cờ. Tuy nhiên, nó **không triển khai Set hoặc Collection**. Tên có chữ Set không làm nó có cùng kiểu API và hợp đồng với `Set<Integer>`.

Bài học sẽ đi từ cách đánh dấu từng chỉ số, phân biệt ba đại lượng kích thước, đến phép toán tập hợp, phạm vi và chuyển đổi. Các chương trình đầy đủ dùng Java 17 trở lên.

## 2. BitSet và Set<Integer> biểu diễn điều gì?

| Đặc điểm | BitSet | Set<Integer> |
|---|---|---|
| Cách hiểu dữ liệu | Trạng thái bit ở các chỉ số | Tập các đối tượng Integer |
| Miền số | Chỉ số nguyên không âm | Có thể chứa số âm, tùy cách triển khai và ràng buộc ứng dụng |
| Null | Không có phần tử null; chỉ số là int | Có nhận null hay không tùy cách triển khai; HashSet cho phép |
| Đánh dấu có mặt | `set(i)` | `add(i)` |
| Kiểm tra có mặt | `get(i)` | `contains(i)` |
| Bỏ đánh dấu | `clear(i)` | `remove(i)` |
| Đếm phần tử được chọn | `cardinality()` | `size()` |
| Phép toán tập hợp | Có and/or/xor/andNot chuyên cho bit | Dùng thao tác collection hoặc thuật toán phù hợp |
| Thứ tự duyệt | Chỉ số tăng dần qua stream/nextSetBit | Phụ thuộc cách triển khai Set |
| Kiểu Collection/Set | Không | Có |

BitSet không lưu đối tượng Integer riêng cho từng chỉ số được bật. Đó là một lý do nó có thể gọn với miền chỉ số dày. Nhưng cấu trúc đóng gói bit còn phụ thuộc vị trí các chỉ số, không chỉ số lượng phần tử. Hai bit bật rất xa nhau có ý nghĩa chi phí khác với hai bit bật ở gần đầu.

`set`, `clear` và các phép logic sửa trạng thái không trả boolean “tập có thay đổi không” như một số thao tác Set. Chúng có kiểu trả về void. Không thể thay máy móc `set.add(i)` bằng một lời gọi rồi vẫn dùng kết quả boolean của nó.

Đối chiếu hợp đồng chính tại [BitSet — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/BitSet.html) và [Set — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html).

## 3. Khởi tạo và thao tác từng bit

### 3.1. Mọi bit ban đầu là false

`new BitSet()` tạo trạng thái chưa có bit true. `new BitSet(128)` yêu cầu không gian ban đầu đủ biểu diễn các chỉ số 0 đến 127, nhưng vẫn chưa bật bit nào.

Số 128 không phải số phần tử đang có và cũng không phải một giới hạn cứng. Có thể đặt bit 130 về sau; BitSet sẽ mở rộng khi cần. Nếu ứng dụng chỉ cho phép 128 vị trí, chính ứng dụng phải giữ giới hạn và kiểm tra chỉ số.

### 3.2. set, clear, flip và get

| Lời gọi | Hiệu ứng |
|---|---|
| `set(i)` | Đặt bit i thành true |
| `set(i, false)` | Đặt bit i thành false |
| `clear(i)` | Đặt bit i thành false |
| `flip(i)` | Đảo false thành true hoặc true thành false |
| `get(i)` | Đọc boolean ở bit i |
| `clear()` | Đặt mọi bit thành false |

Đặt một bit đã true thành true lần nữa không tạo bản sao phần tử. BitSet không đếm số lần một chỉ số được đưa vào. Nếu cần tần suất xuất hiện của từng số, cần cấu trúc lưu số đếm thay vì chỉ một bit có/không.

`get(i)` với chỉ số không âm chưa được đặt trả false, kể cả khi i nằm sau bit true cao nhất hiện tại. Đó không phải dấu hiệu lỗi chỉ số theo miền ứng dụng. Chỉ số âm bị từ chối bằng `IndexOutOfBoundsException`.

## 4. cardinality, length và size không thay thế cho nhau

### 4.1. Ba câu hỏi khác nhau

| Phương thức | Câu hỏi được trả lời | Ví dụ khi bật 2, 5, 100 |
|---|---|---|
| `cardinality()` | Có bao nhiêu bit true? | 3 |
| `length()` | Độ dài logic tới sau bit true cao nhất là bao nhiêu? | 101 |
| `size()` | Bao nhiêu bit không gian hiện được cấu trúc dùng để biểu diễn? | Phụ thuộc phần không gian và cách triển khai |

`length()` bằng chỉ số bit true cao nhất cộng một; nếu không có bit true thì bằng 0. Nó không phải số phần tử được chọn. Xóa bit cao nhất có thể làm length giảm dù chỉ vừa bỏ một phần tử.

`size()` nói về không gian biểu diễn hiện tại, khác với Set.size. Tài liệu nêu đại lượng này liên quan cách triển khai và có thể thay đổi theo cách triển khai. Không dùng nó để đếm phần tử hoặc làm số vị trí nghiệp vụ.

Ví dụ một BitSet tạo với không gian ban đầu cho 128 vị trí nhưng chưa bật bit nào có cardinality 0 và length 0. Ngược lại, chỉ bật bit 100 đã làm length thành 101 nhưng cardinality vẫn là một.

### 4.2. Độ dài miền ứng dụng phải được lưu riêng

Nếu có đúng N ghế, N trang hoặc N cờ, giá trị N vẫn có ý nghĩa ngay cả khi mọi bit đều false. Không thể khôi phục N chỉ từ length vì length khi đó bằng 0.

Số vị trí hợp lệ cũng không tự được xác định bằng size. Cách biểu diễn có thể dành không gian khác với N, và constructor không cấm mở rộng. Phần ví dụ phân bổ vị trí cuối bài sẽ giữ trường limit riêng.

## 5. Chương trình 1: đánh dấu, đọc, đếm và mở rộng

Lưu chương trình đầy đủ vào `BitSetBasicsDemo.java`:

```java
import java.util.BitSet;

public class BitSetBasicsDemo {
    public static void main(String[] args) {
        BitSet bits = new BitSet(128);
        System.out.println("initialCardinality=" + bits.cardinality());
        System.out.println("initialLength=" + bits.length());
        System.out.println("capacityAtLeastRequested=" + (bits.size() >= 128));
        bits.set(2);
        bits.set(5);
        bits.set(100);
        bits.set(5);
        System.out.println("selected=" + bits);
        System.out.println("cardinality=" + bits.cardinality());
        System.out.println("length=" + bits.length());
        System.out.println("get3=" + bits.get(3));
        System.out.println("get500=" + bits.get(500));
        bits.clear(100);
        System.out.println("afterClear100=" + bits);
        System.out.println("lengthAfterClear=" + bits.length());
        bits.flip(5);
        bits.flip(7);
        bits.set(130);
        System.out.println("afterFlipAndGrow=" + bits);
        System.out.println("grownLength=" + bits.length());
        bits.clear();
        System.out.println("empty=" + bits.isEmpty());
        System.out.println("emptyLength=" + bits.length());
        System.out.println("emptyCardinality=" + bits.cardinality());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 BitSetBasicsDemo.java
java BitSetBasicsDemo
```

Stdout:

```text
initialCardinality=0
initialLength=0
capacityAtLeastRequested=true
selected={2, 5, 100}
cardinality=3
length=101
get3=false
get500=false
afterClear100={2, 5}
lengthAfterClear=6
afterFlipAndGrow={2, 7, 130}
grownLength=131
empty=true
emptyLength=0
emptyCardinality=0
```

### 5.1. Không gian ban đầu không làm tăng số bit true

`initialCardinality=0` và `initialLength=0` xác nhận chưa có chỉ số được chọn. Ví dụ chỉ kiểm tra không gian ban đầu đủ yêu cầu, không chốt một con số size cụ thể làm hợp đồng có thể dựa vào cho mọi cách triển khai.

Bật 2, 5, 100 rồi bật 5 lần nữa vẫn chỉ có `{2, 5, 100}`. Cardinality là 3, length là 101. `get3=false` và `get500=false` đều là những lần đọc hợp lệ ở chỉ số không âm chưa được bật.

### 5.2. Xóa bit cao nhất và mở rộng trở lại

Xóa 100 để lại `{2, 5}`, nên length còn 6, không phải 100 hoặc 2. Đảo bit 5 tắt nó, đảo bit 7 bật nó, rồi đặt 130 làm kết quả `{2, 7, 130}`.

`grownLength=131` cho thấy chỉ số 130 hợp lệ dù constructor ban đầu nhận 128. Nếu 128 là giới hạn của một yêu cầu thực tế, cần chặn trước thao tác set thay vì dựa vào constructor.

Cuối cùng clear làm trạng thái rỗng: isEmpty true, length 0 và cardinality 0. Không nên suy ra từ đó rằng JVM đã lập tức thu hồi toàn bộ phần không gian mà đối tượng từng dùng.

## 6. Phép toán tập hợp trên BitSet

### 6.1. Mỗi chỉ số là một phép tính boolean

Với hai BitSet A và B cùng dùng một hệ đánh số:

| Phép toán | API trên A | Bit i thuộc kết quả khi nào? |
|---|---|---|
| Giao | `A.and(B)` | A và B đều có i |
| Hợp | `A.or(B)` | Ít nhất một trong A, B có i |
| Hiệu đối xứng | `A.xor(B)` | Chính xác một trong hai có i |
| Hiệu A trừ B | `A.andNot(B)` | A có i nhưng B không có i |
| Chỉ hỏi có giao không | `A.intersects(B)` | Trả true nếu có ít nhất một chỉ số cùng được bật |

XOR không phải phép trừ tập hợp: các phần tử chỉ có trong B vẫn xuất hiện trong XOR, nhưng không xuất hiện trong A trừ B. AND NOT không đối xứng; đổi chỗ hai toán hạng có thể cho kết quả khác.

### 6.2. Các phép and/or/xor/andNot sửa chính đối tượng nhận lời gọi

`A.or(B)` sửa A; nó không tạo một BitSet kết quả mới rồi trả ra. B không vì là đối số mà bị đổi theo trong trường hợp A và B là các đối tượng khác nhau.

Nếu cần giữ nguyên hai nguồn, hãy tạo bản sao A trước rồi áp dụng phép logic lên bản sao. Một phép gán tham chiếu `result = A` không tạo bản sao, nên thao tác trên result vẫn có thể làm A thay đổi.

`intersects` phù hợp khi chỉ cần biết có phần tử chung, không cần lưu cả kết quả giao. Nó trả boolean và không sửa hai tập.

## 7. Chương trình 2: giao, hợp, XOR và hiệu

Lưu vào `BitSetAlgebraDemo.java`. Hàm of trong chương trình là hàm hỗ trợ tự viết để đặt các chỉ số; không phải phương thức tĩnh BitSet.of của API Java 17.

```java
import java.util.BitSet;

public class BitSetAlgebraDemo {
    private static BitSet of(int... indices) {
        BitSet result = new BitSet();
        for (int index : indices) {
            result.set(index);
        }
        return result;
    }

    public static void main(String[] args) {
        BitSet a = of(1, 3, 5, 9);
        BitSet b = of(3, 4, 9);
        BitSet intersection = (BitSet) a.clone();
        intersection.and(b);
        BitSet union = (BitSet) a.clone();
        union.or(b);
        BitSet symmetricDifference = (BitSet) a.clone();
        symmetricDifference.xor(b);
        BitSet difference = (BitSet) a.clone();
        difference.andNot(b);

        System.out.println("intersection=" + intersection);
        System.out.println("union=" + union);
        System.out.println("symmetricDifference=" + symmetricDifference);
        System.out.println("difference=" + difference);
        System.out.println("intersects=" + a.intersects(b));
        System.out.println("originalA=" + a);
        System.out.println("originalB=" + b);
        a.or(b);
        System.out.println("aAfterDirectOr=" + a);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 BitSetAlgebraDemo.java
java BitSetAlgebraDemo
```

Stdout:

```text
intersection={3, 9}
union={1, 3, 4, 5, 9}
symmetricDifference={1, 4, 5}
difference={1, 5}
intersects=true
originalA={1, 3, 5, 9}
originalB={3, 4, 9}
aAfterDirectOr={1, 3, 4, 5, 9}
```

### 7.1. Đối chiếu từng kết quả

Với A={1,3,5,9}, B={3,4,9}, giao là `{3, 9}`, hợp là `{1, 3, 4, 5, 9}`, XOR là `{1, 4, 5}`, còn A trừ B là `{1, 5}`. Phần tử 4 giúp thấy rõ khác biệt giữa XOR và hiệu.

Vì các phép trước được thực hiện trên clone, originalA và originalB vẫn nguyên. Lần `a.or(b)` cuối cùng tác động trực tiếp vào a, nên a trở thành hợp. Đây là bằng chứng cần theo dõi đối tượng nhận lời gọi khi dùng các phương thức trả void.

Các tập muốn kết hợp phải thống nhất ý nghĩa của chỉ số: bit 5 phải cùng đại diện một đối tượng hoặc một thuộc tính ở cả hai nguồn. Nếu một nguồn dùng số thứ tự trang còn nguồn kia dùng mã người dùng, phép AND vẫn chạy nhưng kết quả không có ý nghĩa nghiệp vụ.

## 8. Thao tác một khoảng chỉ số

### 8.1. Đầu có bao gồm, cuối không bao gồm

Các overload nhận `fromIndex, toIndex` dùng khoảng nửa mở `[fromIndex, toIndex)`:

- `set(2, 8)` bật 2, 3, 4, 5, 6, 7.
- `clear(4, 6)` tắt 4 và 5.
- `flip(6, 9)` đảo 6, 7, 8.

Chỉ số toIndex không nằm trong khoảng. Muốn xử lý các vị trí 0 đến N-1 thì dùng cận cuối N. Việc dùng khoảng nửa mở giúp độ dài khoảng được tính bằng toIndex-fromIndex.

Khoảng có hai cận bằng nhau là khoảng rỗng và không chứa bit cần thay đổi. Cận âm hoặc fromIndex lớn hơn toIndex bị từ chối bằng `IndexOutOfBoundsException`.

Constructor nhận số bit ban đầu âm có hợp đồng lỗi riêng: `NegativeArraySizeException`. Không nhầm nó với lỗi truy cập chỉ số âm của get/set.

### 8.2. get(from, to) trả một bản mới và đổi gốc chỉ số

`get(from, to)` không phải view liên kết với BitSet nguồn. Nó trả một BitSet mới chứa các bit trong khoảng, với vị trí from của nguồn được đặt thành chỉ số 0 của kết quả.

Ví dụ nguồn bật 2, 3, 8; lấy khoảng `[2,9)` thì kết quả bật 0, 1, 6. Công thức đối chiếu là chỉ số mới = chỉ số gốc - fromIndex.

Xóa bit 0 trên kết quả không xóa bit 2 trên nguồn. Nếu cần giữ nguyên hệ đánh số ban đầu, không được dùng get(range) rồi giả định các số in ra vẫn là các số gốc.

## 9. Chương trình 3: khoảng, bản sao và phép bằng nhau

Lưu chương trình đầy đủ vào `BitSetRangeCopyDemo.java`:

```java
import java.util.BitSet;

public class BitSetRangeCopyDemo {
    public static void main(String[] args) {
        BitSet bits = new BitSet();
        bits.set(2, 8);
        System.out.println("setRange=" + bits);
        bits.clear(4, 6);
        System.out.println("clearRange=" + bits);
        bits.flip(6, 9);
        System.out.println("flipRange=" + bits);

        BitSet slice = bits.get(2, 9);
        System.out.println("slice=" + slice);
        slice.clear(0);
        System.out.println("sliceAfterClear=" + slice);
        System.out.println("originalBit2=" + bits.get(2));

        BitSet copy = (BitSet) bits.clone();
        copy.clear(3);
        System.out.println("copyAfterClear=" + copy);
        System.out.println("originalBit3=" + bits.get(3));

        BitSet small = new BitSet(1);
        BitSet large = new BitSet(1024);
        small.set(6);
        large.set(6);
        System.out.println("equalDespiteCapacity=" + small.equals(large));
        System.out.println("equalHashCodes=" + (small.hashCode() == large.hashCode()));
        try {
            bits.get(-1);
            throw new AssertionError("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            System.out.println("negativeIndex=IndexOutOfBoundsException");
        }
        try {
            bits.set(9, 3);
            throw new AssertionError("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            System.out.println("reversedRange=IndexOutOfBoundsException");
        }
        try {
            new BitSet(-1);
            throw new AssertionError("Expected NegativeArraySizeException");
        } catch (NegativeArraySizeException expected) {
            System.out.println("negativeInitialSize=NegativeArraySizeException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 BitSetRangeCopyDemo.java
java BitSetRangeCopyDemo
```

Stdout:

```text
setRange={2, 3, 4, 5, 6, 7}
clearRange={2, 3, 6, 7}
flipRange={2, 3, 8}
slice={0, 1, 6}
sliceAfterClear={1, 6}
originalBit2=true
copyAfterClear={2, 8}
originalBit3=true
equalDespiteCapacity=true
equalHashCodes=true
negativeIndex=IndexOutOfBoundsException
reversedRange=IndexOutOfBoundsException
negativeInitialSize=NegativeArraySizeException
```

### 9.1. Theo dõi ba thao tác khoảng

Sau `set(2,8)`, các bit 2 đến 7 được bật. `clear(4,6)` bỏ 4 và 5, còn `{2, 3, 6, 7}`. `flip(6,9)` tắt 6, 7 và bật 8, nên nguồn thành `{2, 3, 8}`.

`slice={0, 1, 6}` thể hiện việc dịch gốc chỉ số khi lấy đoạn từ 2 đến trước 9. Xóa 0 của slice vẫn để `originalBit2=true`.

### 9.2. Clone giữ nguyên chỉ số nhưng tách trạng thái bit

`clone()` tạo BitSet mới có cùng các bit true, giữ nguyên hệ đánh số. Xóa 3 trên clone để lại `{2, 8}` trong bản sao, trong khi `originalBit3=true` xác nhận nguồn không đổi.

Đây là khác biệt với phép gán một biến tham chiếu thứ hai tới cùng BitSet. Một tham chiếu khác không làm trạng thái bit được nhân bản.

### 9.3. Equals xét bit true, không xét phần không gian dự trữ

small và large được tạo với kích thước ban đầu khác nhau, nhưng cùng chỉ bật bit 6. `equalDespiteCapacity=true` và `equalHashCodes=true` xác nhận nội dung bit quyết định phép bằng nhau và mã băm.

Không có số lượng bit false ở đuôi được ghi thành một độ dài nghiệp vụ riêng trong phép bằng nhau. Hai cấu hình ứng dụng có N khác nhau nhưng cùng mẫu bit true có thể cần được phân biệt bằng một lớp chứa cả N và BitSet, thay vì dùng riêng BitSet.equals.

BitSet có thể thay đổi, và hashCode phụ thuộc các bit true. Không sửa nội dung một BitSet đang được dùng làm khóa trong cấu trúc băm hoặc làm phần tử của một HashSet nếu việc đó làm thay đổi mã băm và quan hệ bằng nhau mà cấu trúc đang dựa vào.

### 9.4. Các lỗi đầu vào có chủ đích

Ba ngoại lệ được bắt trong chương trình kiểm tra ba lỗi: chỉ số âm, khoảng đảo cận và kích thước ban đầu âm. Mỗi trường hợp in đúng loại lỗi đã được đối chiếu.

Việc chỉ số chưa được bật trả false khác với việc chỉ số âm không hợp lệ. Đó là lý do `get500` ở chương trình 1 chạy bình thường, còn `get(-1)` ở đây ném lỗi.

## 10. Duyệt các chỉ số đang được bật

### 10.1. nextSetBit bỏ qua các bit false

`nextSetBit(from)` tìm bit true đầu tiên ở vị trí từ from trở đi, có bao gồm from. Nếu không còn bit true, nó trả -1.

Muốn duyệt tiếp sau chỉ số i, lần tìm sau bắt đầu ở i+1. Nếu vẫn tìm từ i, có thể gặp lại chính bit đang true và không tiến lên được.

Mẫu vòng lặp trong chương trình tiếp theo có chặn i bằng `Integer.MAX_VALUE` trước khi tính i+1, tránh tràn số ở mốc lớn nhất. Đây cũng là điểm được nêu trong ví dụ duyệt của API BitSet. Không cần tạo một BitSet khổng lồ để chạy các dữ liệu nhỏ của bài.

### 10.2. stream trả chỉ số int, không trả các boolean

`bits.stream()` cho một IntStream chứa các chỉ số bit true theo thứ tự tăng dần. Với `{1,7,11}`, mảng từ stream là `[1,7,11]`, không phải một mảng gồm true/false ở từng vị trí.

Khi thu lại vào `Set<Integer>`, các chỉ số int được đóng hộp thành Integer theo cơ chế Java. BitSet không vì có stream mà trở thành một Collection nhận trực tiếp ở mọi API cần Set.

BitSet stream gắn với nguồn khi bắt đầu thao tác kết thúc. Tài liệu quy định kết quả không xác định nếu BitSet bị thay đổi trong thao tác đó. Khi cần một nguồn ổn định, phải bảo đảm điều kiện truy cập hoặc tạo bản sao trong điều kiện phù hợp trước khi duyệt.

## 11. Chương trình 4: chuyển giữa BitSet và Set<Integer>

Lưu vào `BitSetIntegerSetDemo.java`. Chương trình dùng TreeSet khi cần in thứ tự tăng dần xác định; không dựa vào thứ tự của một Set bất kỳ.

```java
import java.util.Arrays;
import java.util.BitSet;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;

public class BitSetIntegerSetDemo {
    public static void main(String[] args) {
        Set<Integer> original = new TreeSet<>(Set.of(1, 7, 11));
        BitSet bits = new BitSet();
        for (int index : original) {
            bits.set(index);
        }
        System.out.println("bits=" + bits);
        System.out.println("streamIndices=" + Arrays.toString(bits.stream().toArray()));
        StringJoiner visited = new StringJoiner(", ");
        for (int index = bits.nextSetBit(0);
                index >= 0;
                index = bits.nextSetBit(index + 1)) {
            visited.add(Integer.toString(index));
            if (index == Integer.MAX_VALUE) {
                break;
            }
        }
        System.out.println("nextSetBitIndices=" + visited);
        Set<Integer> restored = new TreeSet<>();
        bits.stream().forEach(restored::add);
        System.out.println("restored=" + restored);
        System.out.println("sameMembers=" + restored.equals(original));
        System.out.println("bitSetEqualsSet=" + bits.equals(original));
        System.out.println("setEqualsBitSet=" + original.equals(bits));

        Set<Integer> signed = new TreeSet<>(Set.of(-1, 1));
        System.out.println("setHasNegative=" + signed.contains(-1));
        try {
            bits.get(-1);
            throw new AssertionError("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            System.out.println("negativeBitLookup=IndexOutOfBoundsException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 BitSetIntegerSetDemo.java
java BitSetIntegerSetDemo
```

Stdout:

```text
bits={1, 7, 11}
streamIndices=[1, 7, 11]
nextSetBitIndices=1, 7, 11
restored=[1, 7, 11]
sameMembers=true
bitSetEqualsSet=false
setEqualsBitSet=false
setHasNegative=true
negativeBitLookup=IndexOutOfBoundsException
```

### 11.1. Giữ cùng tập phần tử trong miền không âm

Vòng lặp set từng chỉ số tạo BitSet `{1, 7, 11}`. Stream và vòng nextSetBit đều duyệt 1, 7, 11. Khi thu lại thành một TreeSet, `sameMembers=true` xác nhận tập Integer khôi phục có cùng các thành viên với tập nguồn.

Phép chuyển này dựa trên điều kiện đầu vào là các số nguyên không âm. Với một Set tổng quát, cần chọn rõ cách xử lý số âm và null trước khi chuyển: từ chối, ánh xạ lại qua một hệ chỉ số riêng, hoặc chọn cấu trúc khác. Không âm thầm bỏ những phần tử mà BitSet không biểu diễn trực tiếp.

### 11.2. Cùng ý nghĩa tập hợp không đồng nghĩa equals giữa hai kiểu

`bitSetEqualsSet=false` và `setEqualsBitSet=false` là đúng hợp đồng. BitSet.equals yêu cầu đối tượng khác cũng là BitSet với cùng các bit true; Set.equals yêu cầu một Set với cùng phần tử.

Không dùng phép equals trực tiếp giữa hai kiểu để kiểm tra chuyển đổi. Hãy so hai BitSet cùng cách biểu diễn, hoặc chuyển về hai Set rồi so các thành viên như chương trình đã làm.

### 11.3. Set có thể giữ số âm, BitSet không có chỉ số âm

Set trong phần cuối chứa -1 và 1 hợp lệ. BitSet.get(-1) lại ném `IndexOutOfBoundsException`. Điều này thể hiện khác biệt miền dữ liệu, không phải một cách tra cứu tương đương chỉ khác tên phương thức.

Set là interface; từng cách triển khai có thêm chính sách khác nhau về null và thứ tự. Chẳng hạn [HashSet — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html) cho phép phần tử null, còn BitSet không có một vị trí đặc biệt nào để biểu diễn “phần tử null”.

## 12. Khi nào biểu diễn bit có lợi?

### 12.1. Miền chỉ số dày và có chung hệ đánh số

Nếu các định danh đã nằm trong khoảng 0 đến U-1 và nhiều vị trí trong khoảng có thể được dùng, một bit cho mỗi vị trí là biểu diễn tự nhiên. Các phép logic có thể xử lý các khối bit thay vì phải thao tác từng đối tượng Integer.

Trong OpenJDK, BitSet đóng gói các bit trong các khối long 64 bit; có thể đối chiếu [mã nguồn BitSet của OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/BitSet.java). Đây là giải thích triển khai cho khả năng xử lý theo khối, không phải cam kết rằng mọi phương thức đều có thời gian hằng số.

Các phép như đếm bit, duyệt hoặc kết hợp nhiều khối bit vẫn có công việc theo phần dữ liệu cần xét. Không dùng trực giác “chỉ có một lời gọi and” để suy ra phép giao luôn có cùng chi phí dù miền dữ liệu nhỏ hay rất lớn.

### 12.2. Tập thưa với chỉ số rất cao có thể bất lợi

Nếu chỉ có hai phần tử 0 và một tỷ, một biểu diễn bit tuyến tính phải có khả năng đi đến vị trí rất cao đó. Phần bit thuần đã có quy mô khoảng 125 triệu byte, chưa tính các chi tiết cấu trúc và cấp phát. Cardinality bằng hai không phản ánh chi phí này.

Một HashSet chứa hai số không cần dành một bit cho mọi vị trí ở giữa hai số. Vì vậy không nên chọn BitSet chỉ từ câu “chỉ cần lưu số nguyên”. Cần xét cả chỉ số lớn nhất và mật độ dữ liệu.

Không tạo chỉ số cực lớn trong một ví dụ chỉ để xem kết quả true; phân tích miền dữ liệu đã đủ cho quyết định biểu diễn. Nếu định danh thật rất thưa, có thể giữ chúng trong Set hoặc thiết kế bảng ánh xạ sang chỉ số dày, nhưng khi đó mọi BitSet được kết hợp phải dùng cùng bảng ánh xạ.

### 12.3. Tiêu chí lựa chọn

| Nhu cầu | Điều nên ưu tiên xét |
|---|---|
| Nhiều cờ trên một khoảng chỉ số vừa phải | BitSet với miền N được mô tả rõ |
| Giao/hợp nhiều nhóm cùng hệ chỉ số | Các phép bit của BitSet |
| Một vài số nằm rất xa nhau | Chi phí theo chỉ số cao nhất; Set có thể phù hợp hơn |
| Cần số âm hoặc null như phần tử | Chính sách phần tử của Set hoặc một ánh xạ riêng |
| Cần truyền vào API nhận Collection/Set | BitSet không thay thế trực tiếp được interface đó |
| Cần đếm số lần xuất hiện của mỗi số | Một cấu trúc lưu tần suất, không chỉ một bit có/không |

Không có lựa chọn tốt nhất cho mọi dữ liệu. So sánh cần dựa trên miền chỉ số, số phần tử, các phép toán chủ yếu và yêu cầu chia sẻ trạng thái.

## 13. Miền hữu hạn và phần bù của một tập

### 13.1. Phải biết phần bù trong miền nào

Với tập được chọn `{0, 2}`, câu hỏi “những vị trí chưa được chọn là gì?” chưa có một đáp án hữu hạn nếu không biết miền hợp lệ. Trong miền `[0,3)`, phần còn lại là `{1}`. Trong miền `[0,5)`, phần còn lại là `{1,3,4}`.

`flip(0, N)` đảo đúng các bit trong miền nửa mở đó; nó không phải thao tác tạo một phần bù vô hạn. Các bit ngoài khoảng không bị thao tác này đổi. Nếu dùng một bản sao rồi flip để tạo tập vị trí rảnh, phải bảo đảm bản sao không chứa các bit true ngoài miền ứng dụng, hoặc tạo mặt nạ miền rồi thực hiện hiệu tập hợp thích hợp.

Không dùng length thay N: khi vị trí cuối cùng đang rảnh, length có thể nhỏ hơn N. Cũng không dùng size thay N vì không gian biểu diễn có thể lớn hơn miền nghiệp vụ.

### 13.2. nextClearBit không biết giới hạn tài nguyên

`nextClearBit(0)` tìm một vị trí false từ 0 trở đi. Với ba vị trí 0,1,2 đều bận, nó có thể trả 3; điều đó chỉ nói bit 3 đang false, không nói hệ thống có vị trí tài nguyên thứ tư.

Muốn phân bổ trong miền hữu hạn, cần kiểm tra chỉ số tìm thấy nhỏ hơn giới hạn rồi mới đánh dấu bận. Đây là một khác biệt thực tế giữa trạng thái bit có thể mở rộng và một danh sách tài nguyên cố định.

## 14. Chương trình 5: quản lý các vị trí từ 0 đến limit−1

Lưu chương trình đầy đủ vào `BitSetSlotsDemo.java`. Quy ước của lớp Slots là bit true nghĩa vị trí đang bận; phương thức allocate trả -1 khi không còn vị trí hợp lệ. Ví dụ được thiết kế cho một luồng.

```java
import java.util.BitSet;

public class BitSetSlotsDemo {
    static final class Slots {
        private final int limit;
        private final BitSet busy;

        Slots(int limit) {
            if (limit < 0) {
                throw new IllegalArgumentException("negative limit");
            }
            this.limit = limit;
            busy = new BitSet(limit);
        }

        int allocate() {
            int index = busy.nextClearBit(0);
            if (index >= limit) {
                return -1;
            }
            busy.set(index);
            return index;
        }

        void release(int index) {
            if (index < 0 || index >= limit) {
                throw new IndexOutOfBoundsException("slot");
            }
            busy.clear(index);
        }

        BitSet busySnapshot() {
            return (BitSet) busy.clone();
        }

        BitSet freeSnapshot() {
            BitSet free = (BitSet) busy.clone();
            free.flip(0, limit);
            return free;
        }
    }

    public static void main(String[] args) {
        Slots slots = new Slots(3);
        System.out.println("allocate=" + slots.allocate());
        System.out.println("allocate=" + slots.allocate());
        System.out.println("allocate=" + slots.allocate());
        System.out.println("fullAllocate=" + slots.allocate());
        slots.release(1);
        System.out.println("busyAfterRelease=" + slots.busySnapshot());
        System.out.println("freeAfterRelease=" + slots.freeSnapshot());
        System.out.println("allocateAgain=" + slots.allocate());
        BitSet snapshot = slots.busySnapshot();
        snapshot.clear();
        System.out.println("busyAfterSnapshotClear=" + slots.busySnapshot());
        System.out.println("zeroLimitAllocate=" + new Slots(0).allocate());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 BitSetSlotsDemo.java
java BitSetSlotsDemo
```

Stdout:

```text
allocate=0
allocate=1
allocate=2
fullAllocate=-1
busyAfterRelease={0, 2}
freeAfterRelease={1}
allocateAgain=1
busyAfterSnapshotClear={0, 1, 2}
zeroLimitAllocate=-1
```

### 14.1. Giới hạn được kiểm tra trước khi bật

Ba lần cấp đầu trả 0,1,2. Lần thứ tư không tự mở rộng tài nguyên bằng cách dùng bit 3: phương thức kiểm tra `index >= limit` và trả -1.

Giải phóng 1 để busy còn `{0,2}`. Bản sao free đảo miền `[0,3)` nên cho `{1}`. Lần cấp tiếp theo lấy lại vị trí 1 và busy trở về `{0,1,2}`.

Giới hạn 0 được định nghĩa là không có vị trí hợp lệ, nên allocate trả -1 ngay cả khi nextClearBit tìm thấy chỉ số 0. Giới hạn âm bị constructor lớp bọc từ chối bằng `IllegalArgumentException` trước khi tạo BitSet.

### 14.2. Bản sao bảo vệ trạng thái nội bộ

`busySnapshot()` trả clone, không trả chính busy. Vì vậy xóa hết các bit trên biến snapshot không làm đối tượng Slots mất dấu các vị trí bận; `busyAfterSnapshotClear` vẫn là `{0,1,2}`.

`freeSnapshot()` hợp lệ vì lớp chỉ bật các bit trong miền `[0,limit)`, nên không có bit true ngoài miền để còn sót lại sau phép flip. Điều kiện đó được giữ bằng trường private và kiểm tra trong các thao tác của lớp.

Phương thức release kiểm tra chỉ số nằm trong miền rồi clear bit. Theo cách viết này, release một vị trí hợp lệ vốn đã rảnh vẫn để nó rảnh; ví dụ không đặt thêm chính sách báo lỗi cho lần giải phóng lặp. Khi dùng vào một bài toán cụ thể, hợp đồng đó phải được chọn rõ ràng.

### 14.3. Đây là quy trình nhiều bước

`allocate` gồm tìm bit rảnh rồi đánh dấu nó bận. BitSet không làm hai bước trở thành một thao tác cấp phát nguyên tử giữa nhiều luồng. Hai luồng cùng dùng mà không phối hợp có thể cùng tìm một chỉ số rảnh.

Nếu cần dùng đồng thời, phải thiết kế cách phối hợp toàn bộ trạng thái và quy trình. Các kết quả của chương trình chỉ xác nhận hành vi một luồng đang được mô tả, không tự cung cấp bảo đảm đồng thời.

## 15. Chuyển sang mảng: danh sách chỉ số khác dữ liệu bit đóng gói

Ba API có tên gần nhau nhưng trả dữ liệu khác:

| Cách lấy dữ liệu | Nội dung kết quả |
|---|---|
| `bits.stream().toArray()` | Mảng int gồm các chỉ số đang true |
| `bits.toByteArray()` | Mảng byte đóng gói các bit |
| `bits.toLongArray()` | Mảng long đóng gói các bit thành các khối |

`BitSet.valueOf(byte[])` hoặc `valueOf(long[])` đọc biểu diễn bit đóng gói, không đọc danh sách các số nguyên được chọn. Nếu muốn biểu diễn các thành viên 1,7,11, hãy set từng chỉ số hoặc chuyển theo cách ở chương trình 4; không đưa một mảng các thành viên vào valueOf rồi coi nó là phép chuyển Set.

Tài liệu BitSet mô tả quy tắc đánh số bit và biểu diễn little-endian của các mảng đóng gói. Khi cần định nghĩa một định dạng lưu/truyền, phải theo đúng hợp đồng đó, không đồng nhất dữ liệu này với một chuỗi ký tự nhị phân hay mã hóa văn bản.

Mẫu bit cũng không tự giữ số bit false ở đuôi như một độ dài miền riêng. Nếu định dạng cần ghi nhớ N vị trí hợp lệ, hãy lưu N cùng dữ liệu theo thiết kế định dạng. Khôi phục cùng các bit true không tự chứng minh đã khôi phục mọi thông tin của bài toán ứng dụng.

## 16. Trạng thái thay đổi, chia sẻ và kiểm tra

### 16.1. Chọn rõ đối tượng nào được phép thay đổi

BitSet là đối tượng có thể thay đổi. Các phương thức set/clear/flip và các phép logic có thể làm dữ liệu mà các biến tham chiếu khác cùng nhìn thấy đổi theo.

Khi một hàm chỉ muốn tính kết quả mà giữ nguồn, hãy sao chép chủ đích như chương trình đại số. Khi trả dữ liệu ra ngoài nhưng muốn bảo vệ nội bộ, dùng một bản sao phù hợp như lớp Slots. Đặt tên biến mới không tự tạo ranh giới dữ liệu.

Không có bảo đảm an toàn nhiều luồng nếu thiếu đồng bộ bên ngoài. Một phép toán logic theo khối bit hoặc một lời gọi set không được tự diễn giải thành một giao dịch đầy đủ cho nhiều thao tác ứng dụng.

### 16.2. Những kiểm tra có giá trị

- Với tập `{2,5,100}`, xác nhận cardinality 3 và length 101; không dùng size để thay một trong hai.
- Set lại cùng bit phải giữ số thành viên, còn flip hai lần cùng bit phải trở lại trạng thái cũ.
- Khoảng có nhận đầu nhưng không nhận cuối; kiểm tra thêm khoảng rỗng và khoảng đảo cận.
- get(range) phải đổi gốc chỉ số và tạo bản riêng; clone tạo bản riêng nhưng giữ chỉ số.
- Giao/hợp/XOR/hiệu phải có kết quả đúng và chỉ sửa đối tượng được chủ đích cho phép sửa.
- Chuyển qua Set rồi về BitSet phải giữ các thành viên trong miền được hỗ trợ.
- Với miền hữu hạn, mọi bit được bật phải nhỏ hơn N và nextClearBit ngoài N phải được xử lý.

Có thể dùng một Set<Integer> không âm làm dữ liệu đối chiếu cho các phép toán: giao tương ứng retainAll trên một bản sao; hợp tương ứng addAll; hiệu tương ứng removeAll. Cần tạo bản sao Set nếu muốn giữ nguồn, tương tự việc clone BitSet.

Kiểm tra cả kết quả lẫn các nguồn còn nguyên khi đó là hợp đồng. Một phép giao trả đúng nhưng vô tình sửa nguồn dùng ở bước sau vẫn là lỗi của cách sử dụng.

### 16.3. Không suy ra tốc độ từ tên cấu trúc

Biểu diễn gọn và phép toán theo khối có thể có lợi lớn khi miền chỉ số phù hợp. Tuy nhiên, việc mở rộng, sao chép, duyệt hay đếm bit đều có chi phí; số phần tử n và miền chỉ số U không phải cùng một đại lượng.

Nếu cần đánh giá hiệu năng, dùng dữ liệu đại diện cho mật độ và chỉ số cao nhất thực tế. Một phép đo trên các số 0 đến 100 không trả lời được chi phí của vài số nằm gần một tỷ.

## 17. Lỗi thường gặp

| Nhầm lẫn | Hậu quả | Cách xử lý |
|---|---|---|
| BitSet là một cách triển khai Set<Integer> | Truyền sai kiểu vào API Collection hoặc hiểu sai equals | Chuyển đổi rõ ràng khi cần Set |
| new BitSet(N) đã có N phần tử | Đếm sai ngay sau khởi tạo | Mọi bit ban đầu false; xem cardinality |
| N trong constructor là giới hạn cứng | Đặt bit vượt miền ứng dụng mà không bị chặn | Lưu và kiểm tra giới hạn riêng |
| size là số phần tử được chọn | Nhầm không gian với thành viên | Dùng cardinality |
| length là số phần tử được chọn | Sai khi có khoảng trống giữa các bit | Dùng định nghĩa bit true cao nhất cộng một |
| Clear bit cao nhất làm giảm cardinality bằng cả khoảng dài | Nhầm số vị trí với số bit true | Cardinality chỉ giảm theo bit true bị tắt |
| or/and trả một tập mới | Sửa nguồn ngoài ý định | Clone trước nếu cần kết quả riêng |
| XOR là A trừ B | Giữ thêm các phần tử chỉ thuộc B | Phân biệt xor với andNot |
| Khoảng có nhận cả hai cận | Bật hoặc tắt thừa một bit | Dùng khoảng [from,to) |
| get(from,to) giữ chỉ số gốc hoặc tạo view | Đọc sai vị trí, dự đoán sai thay đổi nguồn | Hiểu bản mới được dịch gốc chỉ số |
| nextClearBit luôn trả tài nguyên hợp lệ | Cấp vượt giới hạn N | Kiểm tra kết quả so với N |
| Hai phần tử thì BitSet chắc chắn rất nhỏ | Bỏ qua chỉ số cao nhất | Xét cả miền U và mật độ |
| valueOf(long[]) nhận danh sách thành viên | Dựng sai mẫu bit | Đó là các khối bit đóng gói |

## 18. Tự kiểm tra

### Câu hỏi

1. Bật 0 và 100 thì cardinality và length bằng bao nhiêu?
2. new BitSet(64) có cấm set(100) không?
3. Get ở một chỉ số không âm chưa được đặt trả false hay null?
4. Vì sao `bits.equals(aSet)` không phải cách kiểm tra hai tập thành viên tương đương?
5. `a.and(b)` sửa a hay trả một BitSet mới?
6. Với A={1,3}, B={3,4}, XOR và A trừ B khác nhau ở đâu?
7. Nguồn bật 5,7; get(5,8) có các bit nào?
8. Vì sao không dùng length làm tổng số ghế trong một hệ thống có N ghế?
9. Ba vị trí 0,1,2 đã bận; nextClearBit(0) tìm thấy 3 có nghĩa đã có thêm một vị trí hợp lệ không?
10. Vì sao một BitSet có cardinality nhỏ vẫn có thể tốn nhiều không gian?

### Đáp án

1. Cardinality là 2, length là 101.
2. Không; kích thước ban đầu không phải giới hạn cố định.
3. False; giá trị boolean không có trạng thái null.
4. Hai kiểu có hợp đồng equals khác nhau; BitSet không phải Set.
5. Sửa a, trả void. Clone a trước nếu cần giữ nguồn.
6. XOR là `{1,4}`, hiệu A trừ B là `{1}`.
7. Các bit 0 và 2, vì chỉ số gốc được trừ đi 5.
8. Bit cuối có thể đang false hoặc mọi bit đều false; length mô tả trạng thái đang bật chứ không giữ N.
9. Không; ứng dụng phải kiểm tra 3 có nhỏ hơn giới hạn không.
10. Biểu diễn bit tuyến tính cần tới vị trí chỉ số cao; cardinality không đo khoảng này.

## 19. Thực hành với kết quả đối chiếu

### Bài 1: ba đại lượng kích thước

Tạo BitSet với không gian ban đầu cho 256 vị trí, chỉ bật 4 và 200. Trước khi chạy, dự đoán cardinality và length, rồi xóa 200 và dự đoán lại.

Kết quả đối chiếu: ban đầu cardinality 2, length 201; sau clear(200), cardinality 1, length 5. Không chốt size bằng cardinality hoặc bằng length.

### Bài 2: phép logic giữ nguyên nguồn

Tạo A={0,2,4}, B={2,3}. Dùng clone để tính giao, hợp, XOR và A trừ B. Sau mỗi phép, xác nhận A và B vẫn là hai tập nguồn ban đầu.

Kết quả đối chiếu: giao `{2}`, hợp `{0,2,3,4}`, XOR `{0,3,4}`, hiệu `{0,4}`. Nếu A đã đổi trong quá trình, xem lại có vô tình dùng cùng tham chiếu làm kết quả hay không.

### Bài 3: lấy đoạn khác với lọc giữ nguyên chỉ số

Nguồn bật 5 và 7. Lấy get(5,8), xóa bit 0 trên kết quả rồi in cả nguồn và đoạn. Tiếp theo tạo clone nguồn, clear bit 5 trên clone rồi đối chiếu lại.

Kết quả đối chiếu: đoạn ban đầu `{0,2}`, sau xóa 0 còn `{2}`; nguồn vẫn `{5,7}`. Clone giữ hệ chỉ số gốc nên sau clear(5) còn `{7}`. Hai kiểu sao chép đều tách trạng thái nhưng có ý nghĩa chỉ số khác nhau.

### Bài 4: phần bù trong miền được chọn

Trong miền `[0,5)`, tập bận là `{0,2}`. Tạo bản sao chỉ có các bit đó rồi flip(0,5). Kiểm tra tập rảnh và tổng cardinality của bận với rảnh.

Kết quả đối chiếu: rảnh `{1,3,4}`, tổng số bit true là 5 và giao hai tập rỗng. Nếu thay miền thành `[0,3)`, kết quả rảnh chỉ còn `{1}`; miền là một phần của đề bài, không tự có trong mẫu bit.

## 20. Môi trường chạy và tài liệu

Năm chương trình trong bài độc lập, dùng cú pháp và API tương thích Java 17. Chúng đã được biên dịch bằng `javac --release 17 -encoding UTF-8` và chạy trên JDK 24.0.2; stdout được đối chiếu từ các lần chạy đó. Mỗi lớp public được lưu trong tệp trùng tên, không phụ thuộc thư viện ngoài.

- [BitSet — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/BitSet.html): chỉ số, kích thước, phạm vi, phép logic, stream và mảng đóng gói.
- [Set — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html): hợp đồng tập đối tượng và equals.
- [HashSet — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html): một cách triển khai Set tổng quát và chính sách phần tử null.
- [BitSet — mã nguồn OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/BitSet.java): đối chiếu biểu diễn đóng gói theo khối long.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="specialized-collections-006">Vector và Stack: hợp đồng cũ và lựa chọn thay thế</a>

<details>
<summary>Xem chi tiết</summary>

# Vector và Stack: hợp đồng cũ và lựa chọn thay thế

Vector và Stack vẫn xuất hiện trong thư viện, giao diện cũ và mã Java đã tồn tại lâu. Hiểu chúng giúp đọc đúng hành vi hiện tại và chọn cấu trúc phù hợp khi viết mới hoặc chuyển đổi.

Không nên chỉ thay tên lớp. Vector là một List có các đặc điểm về đồng bộ hóa và API lịch sử. Stack kế thừa Vector, bổ sung cách dùng vào sau ra trước nhưng vẫn mang cả các thao tác danh sách. Thay chúng có thể đổi thứ tự duyệt, cách báo rỗng, khả năng chứa null và cả phạm vi đồng bộ hóa.

Bài dùng Java 17. “Cũ” ở đây nói về lịch sử thiết kế và hướng lựa chọn API; không có nghĩa hai lớp đã biến mất hoặc cứ gặp là phải sửa ngay.

## 1. Hai kiểu trừu tượng cần phân biệt

### 1.1. Danh sách có chỉ số

Vector là một mảng đối tượng có thể tăng giảm kích thước và truy cập bằng chỉ số. Nó triển khai List, nên có ý nghĩa danh sách: giữ thứ tự phần tử, chấp nhận phần tử trùng và hỗ trợ vị trí.

Một đối tượng Vector có thể được dùng như:

~~~java
List<String> names = new Vector<>();
names.add("A");
names.add("B");
String first = names.get(0);
~~~

Đây là đoạn minh họa một phần. Kiểu biến List giúp nói nhu cầu danh sách, nhưng đối tượng cụ thể vẫn là Vector.

### 1.2. Ngăn xếp vào sau ra trước

Ngăn xếp là quy tắc thao tác: thêm ở đỉnh và lấy ra ở cùng đỉnh. Với chuỗi đẩy A, B, C, thứ tự lấy ra là C, B, A.

~~~text
đẩy A, rồi B, rồi C

đỉnh -> C
        B
đáy  -> A

lấy ra: C, B, A
~~~

Stack mở rộng Vector để cung cấp push, pop, peek, empty và search. Tài liệu Java khuyến nghị dùng Deque và các cách triển khai của nó cho bộ thao tác ngăn xếp mới. [Java 17: Stack](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Stack.html)

Nhu cầu “danh sách theo chỉ số” và “ngăn xếp chỉ thao tác ở đỉnh” không phải cùng một hợp đồng. Đây là điểm bắt đầu để chọn thay thế.

## 2. Vector mang theo những hợp đồng nào?

Vector có từ Java 1.0 và được đưa vào Java Collections Framework bằng cách triển khai List ở các phiên bản sau. Vì vậy, nó có cả tên phương thức lịch sử và tên theo giao diện List.

### 2.1. Tên cũ và cách đọc theo List

| API cũ | API List tương ứng về mục đích | Điểm cần chú ý |
|---|---|---|
| addElement(x) | add(x) | AddElement trả void; add trả boolean |
| elementAt(i) | get(i) | Đọc theo chỉ số |
| insertElementAt(x, i) | add(i, x) | Thứ tự hai đối số khác nhau |
| setElementAt(x, i) | set(i, x) | Set trả giá trị cũ; API cũ trả void |
| removeElement(x) | remove(Object) | Xóa theo đối tượng |
| removeElementAt(i) | remove(i) | Xóa theo chỉ số; kiểu trả về khác |
| removeAllElements() | clear() | Loại mọi phần tử |

Bảng nói về mục đích thao tác, không hứa rằng có thể đổi chuỗi tên trong mọi biểu thức mà bỏ qua kiểu trả về hoặc cách xử lý lỗi.

Với List<Integer>, remove(i) còn dễ bị hiểu nhầm giữa xóa chỉ số và xóa giá trị Integer. Đó là lý do phải đọc kiểu đối số, không chỉ tên phương thức.

### 2.2. Size khác capacity

Size là số phần tử thực tế. Capacity là sức chứa của mảng nền, ít nhất bằng size.

`ensureCapacity(n)` chuẩn bị khả năng lưu trữ và không tự thêm n phần tử. Ngược lại, `setSize(n)` thay số phần tử: tăng size thêm các phần tử null; giảm size bỏ phần tử ở phía cuối.

Ví dụ:

~~~text
ban đầu có A, B: size = 2
ensureCapacity(8): vẫn có A, B
setSize(4): A, B, null, null
~~~

Không dùng setSize chỉ để “đặt trước bộ nhớ”. Các null mới thêm trở thành phần tử danh sách và có thể ảnh hưởng xử lý hoặc việc chuyển sang cấu trúc cấm null.

Vector còn có capacityIncrement và các phương thức như trimToSize. Khi thay thế, phải xác định mã cũ có thật sự phụ thuộc những API riêng đó hay chỉ cần một List. [Java 17: Vector](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Vector.html)

### 2.3. Đồng bộ hóa không phải phép ghép nguyên tử tự động

Vector được đồng bộ hóa. Nhưng nhiều lời gọi liên tiếp không tự trở thành một thao tác không thể bị xen vào.

Ví dụ:

~~~java
if (!values.contains(item)) {
    values.add(item);
}
~~~

Đây là mẫu minh họa có thể sai với yêu cầu “chỉ thêm nếu chưa có” khi nhiều luồng cùng thao tác. Hai luồng có thể đều thấy chưa có trước khi một trong hai thêm.

Không được suy ra rằng dữ liệu mảng bên trong Vector chắc chắn bị hỏng trong ví dụ đó. Vấn đề là điều kiện nghiệp vụ trải qua nhiều lời gọi, còn phạm vi bảo vệ của từng lời gọi không bao phủ cả điều kiện và cập nhật.

## 3. Enumeration và Iterator: đọc hợp đồng duyệt

Vector cung cấp `elements()` trả Enumeration với hasMoreElements/nextElement. Nó cũng hỗ trợ Iterator và ListIterator theo Collections Framework.

Iterator có cơ chế fail-fast theo nỗ lực tốt nhất. Enumeration của Vector không fail-fast; nếu Vector bị thay đổi cấu trúc trong lúc enumeration hoạt động, kết quả duyệt không được xác định theo hợp đồng đó.

Vì vậy, không đổi Iterator sang Enumeration chỉ để “hết ConcurrentModificationException”. Không có ngoại lệ không chứng minh một lượt duyệt đang thấy dữ liệu nhất quán.

Khi cần lượt duyệt được bảo vệ, phải tổ chức cả phạm vi duyệt theo chính sách khóa phù hợp. Khi cần bản dữ liệu tách cấu trúc để xử lý ở ngoài vùng được bảo vệ, có thể tạo bản sao trong lúc nguồn ở trạng thái hợp lệ.

Bản sao danh sách vẫn giữ tham chiếu phần tử; nó không tự sao chép sâu các đối tượng.

## 4. Stack: các thao tác ngăn xếp và phần kế thừa

### 4.1. Năm phương thức cần đọc đúng

| Phương thức Stack | Ý nghĩa |
|---|---|
| push(x) | Thêm x ở đỉnh và trả lại x |
| pop() | Lấy và xóa đỉnh; rỗng ném EmptyStackException |
| peek() | Xem đỉnh, không xóa; rỗng ném EmptyStackException |
| empty() | Hỏi không có phần tử |
| search(x) | Vị trí tính từ đỉnh, bắt đầu 1; không thấy trả -1 |

Search dùng quy tắc bằng nhau của phần tử và tìm lần xuất hiện gần đỉnh nhất. Nó không phải chỉ số List bắt đầu từ 0.

Với Stack chứa A, B, C theo thứ tự chỉ số từ 0:

~~~text
indexOf(B) = 1
search(B)  = 2
peek()     = C
~~~

Search và indexOf trả hai loại tọa độ khác nhau. Khi chuyển đổi, không được dùng nguyên một kết quả search làm chỉ số truy cập danh sách. [Java 17: phương thức của Stack](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Stack.html)

### 4.2. Stack vẫn lộ thao tác Vector

Do kế thừa Vector, Stack vẫn có add(index, value), get, set, remove(index), setSize và nhiều thao tác danh sách.

Ví dụ minh họa:

~~~java
Stack<Integer> stack = new Stack<>();
stack.push(10);
stack.push(20);
stack.add(0, 99);
~~~

Dữ liệu thành 99, 10, 20. Lệnh thêm theo chỉ số đã sửa phần đáy; nó không phải thao tác chỉ ở đỉnh của một ngăn xếp trừu tượng.

Điều này không làm các phương thức push/pop mất quy tắc, nhưng bề mặt API rộng hơn nhu cầu LIFO. Khai báo bằng Deque giúp diễn đạt thao tác hai đầu mà không mang API truy cập chỉ số của List.

## 5. Thay Vector bằng gì?

### 5.1. Danh sách không cần đồng bộ hóa nội bộ

Với danh sách được giữ cục bộ hoặc có chính sách truy cập khác đã rõ, ArrayList thường là lựa chọn tự nhiên. Tài liệu Vector cũng hướng đến ArrayList khi không cần cách triển khai đồng bộ hóa của Vector.

ArrayList vẫn có chỉ số, thứ tự và khả năng chứa null, nhưng không tự đồng bộ hóa. Đừng thay trong mã dùng chung nhiều luồng rồi vô tình bỏ một cơ chế bảo vệ mà mã cũ đang phụ thuộc. [Java 17: ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

Chuyển cấu trúc bằng `new ArrayList<>(vector)` tạo danh sách mới theo thứ tự duyệt nguồn. Nó không tạo alias của cấu trúc Vector, nhưng phần tử bên trong vẫn có thể là cùng các đối tượng.

### 5.2. Danh sách dùng chung với khóa

`Collections.synchronizedList(new ArrayList<>())` cung cấp một List được bọc đồng bộ. Việc ghép nhiều thao tác và duyệt vẫn cần chính sách bao phủ phạm vi cần tính nhất quán.

Tài liệu yêu cầu đồng bộ hóa trên chính danh sách bọc khi duyệt bằng iterator, spliterator hoặc stream; mọi truy cập phải đi qua cơ chế được chọn. [Java 17: Collections.synchronizedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedList(java.util.List))

Nó không phải phép biến đổi tự động làm mọi đoạn check-then-act đúng. Nếu đổi từ Vector sang wrapper chỉ để đổi tên lớp mà giữ nguyên lỗi ghép thao tác, lỗi logic vẫn còn.

### 5.3. Nhiều lượt đọc, ít thay đổi

CopyOnWriteArrayList là lựa chọn cần cân nhắc cho một số danh sách dùng chung có lượng duyệt vượt xa lượng sửa. Các thao tác sửa sao chép mảng, còn iterator thấy ảnh chụp tại lúc tạo và không hỗ trợ sửa qua iterator.

Đổi lại là chi phí sao chép và bộ nhớ khi cập nhật; nó không phù hợp mặc định cho mọi danh sách nhiều luồng, nhất là tải ghi thường xuyên. [Java 17: CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)

Chọn từ tải công việc và hợp đồng quan sát cần có, không từ nhãn “có hỗ trợ nhiều luồng” chung chung.

## 6. Thay Stack bằng Deque: chọn đúng đầu

### 6.1. Quy ước đỉnh ở đầu deque

Cách phổ biến:

~~~java
Deque<String> stack = new ArrayDeque<>();
stack.push("A");
stack.push("B");
String top = stack.pop();
~~~

Push tương ứng addFirst; pop tương ứng removeFirst. Khi dùng nhất quán, chuỗi trên lấy B trước.

ArrayDeque là mảng có thể mở rộng cho Deque, không tự đồng bộ hóa và không cho phép null. Nhiều thao tác ở hai đầu có chi phí hằng số phân bổ; không cần dùng một LinkedList chỉ vì mô hình khái niệm ngăn xếp thường được vẽ bằng các nút. [Java 17: ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html)

### 6.2. Peek không có cùng hợp đồng rỗng

| Stack cũ | Với Deque, đỉnh ở đầu | Khác biệt cần giữ |
|---|---|---|
| push(x) | push(x), tương đương addFirst(x) | Stack trả x; Deque.push trả void |
| pop() | pop() hoặc removeFirst() | Rỗng: EmptyStackException đổi thành NoSuchElementException |
| peek() | peekFirst() hoặc peek() | Deque trả null khi rỗng |
| peek() muốn dạng ném lỗi khi rỗng | getFirst() | Ném NoSuchElementException, vẫn khác loại lỗi cũ |
| empty() | isEmpty() | Cùng mục đích hỏi rỗng |
| search(x) | Không có phương thức trực tiếp cùng tên và hợp đồng | Phải xác định có thật sự cần vị trí từ đỉnh không |

Cùng tên peek không đủ để kết luận hai phương thức có thể thay trực tiếp ở mọi nơi. Mã bắt EmptyStackException hoặc dùng null như dữ liệu cần được xem xét riêng. [Java 17: Deque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html)

### 6.3. Không trộn push với add nếu chưa xác định ý nghĩa

Trên Deque, push thêm ở đầu, còn add theo hợp đồng Queue thêm ở cuối. Nếu mã gọi cả hai theo thói quen “đều là thêm”, nó có thể không còn tạo chuỗi LIFO mong muốn.

Có thể chọn đỉnh ở cuối và dùng addLast/removeLast/peekLast nhất quán. Nhưng khi đã chọn quy ước đó, push/pop của Deque lại tác động ở đầu, không ở cuối.

Giữ một quy ước rõ ràng trong API của lớp sở hữu dữ liệu giúp tránh việc người gọi trộn hai kiểu thao tác.

## 7. Chuyển dữ liệu Stack sang ArrayDeque không chỉ là constructor

### 7.1. Thứ tự iterator khác chiều ngăn xếp

Sau khi push A, B, C:

~~~text
Stack theo chỉ số và iterator:  [A, B, C]
đáy                            A
đỉnh                                 C

ArrayDeque dùng push ở đầu:
iterator:                     [C, B, A]
đỉnh                           C
~~~

Cả hai có thể pop theo C, B, A, nhưng thứ tự iterator khác nhau.

Nếu tạo `new ArrayDeque<>(oldStack)`, constructor tiếp nhận thứ tự iterator của nguồn, nên deque có A ở đầu. Gọi pop ngay sẽ lấy A, đảo ngữ nghĩa lấy ra so với oldStack.

### 7.2. Cách giữ đỉnh ở đầu

Duyệt nguồn từ đáy lên đỉnh và push từng phần tử:

~~~java
Deque<String> replacement = new ArrayDeque<>();
for (String item : oldStack) {
    replacement.push(item);
}
~~~

Kết quả đầu deque là C, rồi B, A. Pop đi theo C, B, A. Vòng lặp không làm cạn nguồn.

Ví dụ xét nguồn ổn định và không chứa null. Nếu dữ liệu cũ có null hoặc đang có sửa đổi cạnh tranh, cần xử lý các điều kiện ấy trước hoặc trong chính sách chuyển đổi; không lặng lẽ bỏ phần tử.

### 7.3. Cách giữ biểu diễn đáy trước, đỉnh cuối

Có thể sao chép theo iterator:

~~~java
Deque<String> replacement = new ArrayDeque<>(oldStack);
String top = replacement.removeLast();
~~~

Cách này lấy C ở cuối. Các thao tác về sau phải dùng đầu cuối nhất quán. Nó không tương đương với việc sau constructor tiếp tục gọi Deque.pop.

Hai cách đều có thể đúng nếu ý định rõ. Khi chuyển đổi, kiểm tra chuỗi lấy ra, không chỉ kiểm tra số phần tử hoặc chuỗi toString.

### 7.4. Hợp đồng equals cũng khác

Stack thừa hưởng quan hệ bằng nhau theo nội dung và thứ tự của List. ArrayDeque không cung cấp equals dựa trên chuỗi phần tử như List; hai deque khác đối tượng có thể cùng phần tử nhưng equals vẫn false.

Không chuyển các kiểm tra nội dung sang deque.equals rồi tưởng dữ liệu đã bị mất. Nếu muốn so một chuỗi theo thứ tự duyệt, hãy tạo hoặc so chuỗi phần tử theo một hợp đồng rõ ràng; nếu muốn so thứ tự pop, hãy kiểm tra đúng thứ tự đó.

Tương tự, không đổi đối tượng dùng làm khóa của bảng băm mà bỏ qua thay đổi equals/hashCode.

## 8. Đồng bộ từng phương thức chưa đủ cho một thuật toán

### 8.1. Check rồi pop vẫn có thể thất bại

Mẫu có vấn đề:

~~~java
if (!shared.empty()) {
    shared.pop();
}
~~~

Hai lời gọi có thể được đồng bộ riêng nhưng lịch xen kẽ vẫn là:

~~~text
luồng 1: thấy không rỗng
luồng 2: thấy không rỗng
luồng 1: pop phần tử duy nhất
luồng 2: pop trên ngăn xếp đã rỗng
~~~

Không có bước nào đòi một lần pop nội bộ bị hỏng. Mỗi pop thực hiện đúng tại trạng thái nó nhận. Điều sai là kỳ vọng kiểm tra rỗng còn hiệu lực tới lần pop sau đó.

### 8.2. Bảo vệ cả thao tác ghép khi giữ Stack cũ

Một cách tổ chức trong mô hình dùng chung khóa:

~~~java
synchronized (shared) {
    if (!shared.empty()) {
        shared.pop();
    }
}
~~~

Những thao tác liên quan khác phải tuân thủ cùng chính sách bảo vệ. Không chỉ đặt synchronized ở một nơi rồi cho các đường gọi khác bỏ qua những bất biến nghiệp vụ cần giữ.

Khóa ở đây giải thích phạm vi nguyên tử mong muốn. Nó không phải lý do giữ Stack cho mọi mã mới; lựa chọn cấu trúc và thiết kế thao tác vẫn cần được tách ra.

### 8.3. Với cấu trúc đồng thời, ưu tiên thao tác đã gộp đúng mục đích

Nếu dùng một deque đồng thời không cho null, pollFirst là một thao tác lấy nếu có và trả null nếu rỗng. Nó tránh ghép isEmpty rồi pop chỉ để hỏi “có thì lấy”.

Một loạt nhiều thao tác vẫn không tự trở thành giao dịch chỉ vì từng thao tác của cấu trúc là an toàn đồng thời. Ví dụ, chuyển phần tử giữa hai cấu trúc hoặc kiểm tra một điều kiện liên quan nhiều đối tượng cần thiết kế riêng.

## 9. Chọn thay thế theo yêu cầu

| Nhu cầu | Lựa chọn để cân nhắc |
|---|---|
| List cục bộ hoặc không cần khóa nội bộ | ArrayList |
| List dùng chung với chính sách khóa tường minh | SynchronizedList hoặc cấu trúc dưới cùng khóa đã thiết kế |
| List duyệt rất nhiều, sửa ít, cần ảnh chụp iterator | CopyOnWriteArrayList |
| Ngăn xếp không chia sẻ cạnh tranh, không lưu null | Deque với ArrayDeque |
| Deque dùng chung, thao tác không cần chờ dữ liệu | ConcurrentLinkedDeque |
| Deque có phối hợp chờ phần tử hoặc chờ chỗ trống | BlockingDeque thích hợp, chẳng hạn LinkedBlockingDeque |
| API bên ngoài bắt buộc Vector/Stack cụ thể | Giữ tương thích tại ranh giới và đánh giá cách dùng nội bộ |

ConcurrentLinkedDeque cung cấp thao tác đồng thời, không cho null và có iterator nhất quán yếu. Nó không hứa các thao tác hàng loạt là nguyên tử, và size không phải phép đếm hằng số như có thể quen ở danh sách. [Java 17: ConcurrentLinkedDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedDeque.html)

LinkedBlockingDeque có thể có giới hạn dung lượng. Nếu cần chờ, dùng họ put/take hoặc thao tác có timeout phù hợp; push không tự biến thành lệnh chờ chỉ vì đối tượng triển khai BlockingDeque. [Java 17: LinkedBlockingDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingDeque.html)

Không chọn một cấu trúc có cơ chế mạnh hơn chỉ theo thói quen. Cần xác định dạng dữ liệu, quy tắc rỗng/null, tải đọc ghi và cách các luồng phối hợp.

## 10. Ranh giới tương thích cần đọc trước khi thay

Trong mã cũ, có thể có API nhận hoặc trả Vector cụ thể, hợp đồng dữ liệu tuần tự hóa, lớp con dùng trường protected hoặc người gọi phụ thuộc Enumeration. Đổi biến nội bộ sang List không tự làm tất cả ranh giới đó tương thích.

Một quy trình xem xét hợp lý là:

1. Xác định mã đang dùng List hay thật sự cần các API riêng của Vector.
2. Với Stack, ghi rõ đỉnh nằm ở đâu và thứ tự lấy ra.
3. Kiểm tra dữ liệu null, lỗi rỗng, giá trị trả về và search.
4. Xác định iterator hoặc thứ tự hiển thị có phải phần của hợp đồng không.
5. Xác định các thao tác ghép và chính sách đồng thời.
6. Đổi cấu trúc tại phạm vi đã hiểu, rồi kiểm chứng hành vi cần giữ.

Đó là đánh giá hợp đồng, không phải yêu cầu sửa mọi lớp cũ ngay lập tức. Hai lớp vẫn có thể cần được duy trì ở một ranh giới tương thích cụ thể.

## 11. Chương trình Java đầy đủ

Lưu mã sau vào `LegacyCollectionsDemo.java`. Chương trình dùng dữ liệu riêng cho các tình huống để các phép lấy ra không làm nhiễu tình huống khác.

Phần hai luồng dùng CountDownLatch để cả hai cùng hoàn tất kiểm tra không rỗng trước khi pop. Không dùng sleep để đoán lịch chạy. Chỉ in tổng kết sau khi join, nên stdout không phụ thuộc luồng nào lấy được phần tử.

~~~java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EmptyStackException;
import java.util.Enumeration;
import java.util.List;
import java.util.Stack;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class LegacyCollectionsDemo {
    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Unexpected collection result");
        }
    }

    private static String exceptionName(Supplier<?> operation) {
        try {
            operation.get();
            return "none";
        } catch (RuntimeException exception) {
            return exception.getClass().getSimpleName();
        }
    }

    private static <E> List<E> drainFirst(Deque<E> deque) {
        List<E> result = new ArrayList<>();
        while (!deque.isEmpty()) {
            result.add(deque.removeFirst());
        }
        return result;
    }

    private static <E> List<E> drainLast(Deque<E> deque) {
        List<E> result = new ArrayList<>();
        while (!deque.isEmpty()) {
            result.add(deque.removeLast());
        }
        return result;
    }

    private static void awaitBoth(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Both checks did not complete");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Vector<String> vector = new Vector<>(2);
        vector.addElement("A");
        vector.add("B");
        vector.ensureCapacity(8);
        require(vector.size() == 2 && vector.capacity() >= 8);
        System.out.println("vector/size=" + vector.size()
                + ", capacityAtLeast8=" + (vector.capacity() >= 8));
        vector.setSize(4);
        require(vector.size() == 4 && vector.get(2) == null);
        System.out.println("vector/afterSetSize=" + vector);
        List<String> copiedList = new ArrayList<>(vector);
        vector.removeElement("B");
        require(copiedList.size() == 4 && copiedList.get(1).equals("B"));
        System.out.println("vector/afterRemove=" + vector
                + ", arrayListCopy=" + copiedList);
        Enumeration<String> enumeration = vector.elements();
        List<String> enumerated = new ArrayList<>();
        while (enumeration.hasMoreElements()) {
            enumerated.add(enumeration.nextElement());
        }
        System.out.println("vector/enumeration=" + enumerated);

        Stack<String> legacy = new Stack<>();
        legacy.push("A");
        legacy.push("B");
        String pushed = legacy.push("C");
        require(pushed.equals("C") && legacy.search("B") == 2);
        System.out.println("stack/pushReturn=" + pushed
                + ", top=" + legacy.peek()
                + ", searchB=" + legacy.search("B")
                + ", indexB=" + legacy.indexOf("B")
                + ", missing=" + legacy.search("missing"));
        System.out.println("stack/iteration=" + new ArrayList<>(legacy));

        Deque<String> modern = new ArrayDeque<>();
        modern.push("A");
        modern.push("B");
        modern.push("C");
        System.out.println("deque/iteration=" + new ArrayList<>(modern));

        Stack<String> emptyStack = new Stack<>();
        Deque<String> emptyDeque = new ArrayDeque<>();
        System.out.println("empty/stackPop=" + exceptionName(emptyStack::pop)
                + ", dequePop=" + exceptionName(emptyDeque::pop));
        System.out.println("empty/stackPeek=" + exceptionName(emptyStack::peek)
                + ", dequePeek=" + emptyDeque.peek());

        Stack<String> nullable = new Stack<>();
        nullable.push(null);
        String nullFromStack = nullable.pop();
        boolean dequeNullRejected = false;
        try {
            modern.push(null);
        } catch (NullPointerException exception) {
            dequeNullRejected = true;
        }
        require(dequeNullRejected && nullFromStack == null);
        System.out.println("null/stackPop=" + nullFromStack
                + ", dequeRejected=" + dequeNullRejected);

        Stack<Integer> inherited = new Stack<>();
        inherited.push(10);
        inherited.push(20);
        inherited.add(0, 99);
        List<Integer> beforePop = new ArrayList<>(inherited);
        Integer inheritedPop = inherited.pop();
        require(beforePop.equals(List.of(99, 10, 20)) && inheritedPop == 20);
        System.out.println("inheritance/afterIndexedAdd=" + beforePop
                + ", pop=" + inheritedPop);

        Deque<String> naive = new ArrayDeque<>(legacy);
        System.out.println("migration/naivePop=" + naive.pop());
        Deque<String> topFirst = new ArrayDeque<>();
        for (String item : legacy) {
            topFirst.push(item);
        }
        Deque<String> topLast = new ArrayDeque<>(legacy);
        List<String> firstSequence = drainFirst(topFirst);
        List<String> lastSequence = drainLast(topLast);
        require(firstSequence.equals(List.of("C", "B", "A"))
                && lastSequence.equals(firstSequence));
        System.out.println("migration/topFirst=" + firstSequence);
        System.out.println("migration/topLast=" + lastSequence);
        System.out.println("migration/source=" + legacy);

        Stack<String> sameStack = new Stack<>();
        sameStack.addAll(legacy);
        Deque<String> dequeA = new ArrayDeque<>(legacy);
        Deque<String> dequeB = new ArrayDeque<>(legacy);
        require(legacy.equals(sameStack) && !dequeA.equals(dequeB));
        System.out.println("equals/stack=" + legacy.equals(sameStack)
                + ", arrayDeque=" + dequeA.equals(dequeB));

        Stack<String> shared = new Stack<>();
        shared.push("only");
        CountDownLatch bothChecked = new CountDownLatch(2);
        AtomicInteger successful = new AtomicInteger();
        AtomicInteger emptyFailures = new AtomicInteger();
        Runnable separateCalls = () -> {
            if (!shared.empty()) {
                bothChecked.countDown();
                awaitBoth(bothChecked);
                try {
                    shared.pop();
                    successful.incrementAndGet();
                } catch (EmptyStackException exception) {
                    emptyFailures.incrementAndGet();
                }
            }
        };
        Thread first = new Thread(separateCalls);
        Thread second = new Thread(separateCalls);
        first.start();
        second.start();
        first.join();
        second.join();
        require(successful.get() == 1 && emptyFailures.get() == 1
                && shared.empty());
        System.out.println("compound/methodsOnlyPops=" + successful.get()
                + ", emptyFailures=" + emptyFailures.get()
                + ", remaining=" + shared.size());

        Stack<String> protectedStack = new Stack<>();
        protectedStack.push("only");
        AtomicInteger protectedPops = new AtomicInteger();
        Runnable wholeOperation = () -> {
            synchronized (protectedStack) {
                if (!protectedStack.empty()) {
                    protectedStack.pop();
                    protectedPops.incrementAndGet();
                }
            }
        };
        Thread third = new Thread(wholeOperation);
        Thread fourth = new Thread(wholeOperation);
        third.start();
        fourth.start();
        third.join();
        fourth.join();
        require(protectedPops.get() == 1 && protectedStack.empty());
        System.out.println("compound/lockedPops=" + protectedPops.get()
                + ", remaining=" + protectedStack.size());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 -encoding UTF-8 LegacyCollectionsDemo.java
java LegacyCollectionsDemo
~~~

Stdout của lần chạy kiểm chứng:

~~~text
vector/size=2, capacityAtLeast8=true
vector/afterSetSize=[A, B, null, null]
vector/afterRemove=[A, null, null], arrayListCopy=[A, B, null, null]
vector/enumeration=[A, null, null]
stack/pushReturn=C, top=C, searchB=2, indexB=1, missing=-1
stack/iteration=[A, B, C]
deque/iteration=[C, B, A]
empty/stackPop=EmptyStackException, dequePop=NoSuchElementException
empty/stackPeek=EmptyStackException, dequePeek=null
null/stackPop=null, dequeRejected=true
inheritance/afterIndexedAdd=[99, 10, 20], pop=20
migration/naivePop=A
migration/topFirst=[C, B, A]
migration/topLast=[C, B, A]
migration/source=[A, B, C]
equals/stack=true, arrayDeque=false
compound/methodsOnlyPops=1, emptyFailures=1, remaining=0
compound/lockedPops=1, remaining=0
~~~

Mã đã được biên dịch với `--release 17` và chạy thành công. Tùy chọn release xác định mức API và mã đích tương thích; không khẳng định JVM thực thi là đúng bản JDK 17.

## 12. Giải thích các nhóm kết quả

### 12.1. Vector và bản sao List

EnsureCapacity giữ size bằng hai; setSize tăng thành bốn và thêm hai null. ArrayListCopy được tạo trước khi nguồn bỏ B nên vẫn giữ bốn phần tử ban đầu.

Enumeration được dùng khi dữ liệu không thay đổi trong lượt duyệt. Bài không dùng kết quả ấy để hứa điều gì khi nguồn bị sửa cạnh tranh.

### 12.2. Ngăn xếp và thứ tự

Stack trả C từ push, peek C và search B bằng hai trong khi index B bằng một. Iterator của Stack đi A, B, C; iterator của deque được xây bằng push đi C, B, A.

Chèn 99 tại chỉ số 0 của Stack thể hiện các thao tác Vector kế thừa vẫn hiện diện. Pop vẫn lấy 20 ở đỉnh sau thao tác đó.

### 12.3. Rỗng, null và chuyển đổi

EmptyStackPop và emptyDequePop cho hai tên ngoại lệ khác nhau. Peek của Stack rỗng ném lỗi, còn Deque.peek cho null.

Stack có thể giữ và pop null trong ví dụ. ArrayDeque từ chối đẩy null. Đây là thay đổi hợp đồng dữ liệu cần được nhận diện khi chuyển đổi.

NaivePop lấy A vì constructor sao chép thứ tự đáy trước của Stack. Hai cách có chủ đích đều lấy C, B, A và không làm cạn nguồn. Kiểm tra không chỉ dừng ở việc cùng chứa ba phần tử.

### 12.4. Equals

Hai Stack riêng có cùng chuỗi phần tử bằng nhau theo List. Hai ArrayDeque riêng có cùng chuỗi phần tử không bằng nhau theo equals của lớp.

Chương trình kiểm tra chuỗi lấy ra bằng List kết quả khi cần bằng nhau nội dung; không dùng deque.equals cho nhiệm vụ ấy.

### 12.5. Thao tác ghép

Khi chỉ dựa vào đồng bộ hóa từng lời gọi Stack, một luồng lấy được phần tử và một luồng gặp EmptyStackException sau khi cả hai đều đã thấy không rỗng.

Khi giữ khóa suốt kiểm tra và pop, chỉ một luồng pop; luồng sau thấy rỗng rồi bỏ qua. Kết quả này chứng minh phạm vi bảo vệ của thao tác ghép, không phải phép đo hiệu năng hay mô phỏng toàn bộ một hệ thống đồng thời.

## 13. Các lỗi thường gặp

| Lỗi | Cách hiểu đúng |
|---|---|
| Vector cũ nghĩa là đã bị loại khỏi Java | Nó vẫn tồn tại; cần hiểu lý do tương thích và hướng API mới |
| Synchronized từng phương thức làm mọi thuật toán nguyên tử | Chuỗi kiểm tra–cập nhật cần phạm vi riêng |
| SetSize chỉ đặt trước dung lượng | Nó thay số phần tử và có thể thêm null |
| Enumeration không ném CME nên dùng an toàn khi sửa | Không fail-fast không có nghĩa duyệt nhất quán |
| Stack chỉ cho phép thao tác ở đỉnh | Nó còn kế thừa nhiều thao tác List từ Vector |
| Search trả chỉ số bắt đầu từ 0 | Nó trả khoảng cách từ đỉnh bắt đầu 1 |
| Push của Stack và Deque có cùng kiểu trả về | Stack trả phần tử; Deque.push trả void |
| Peek hai lớp có cùng hành vi rỗng | Stack ném lỗi; Deque.peek trả null |
| ArrayDeque giữ được mọi dữ liệu null của Stack | ArrayDeque cấm null |
| New ArrayDeque của Stack rồi pop luôn giữ LIFO cũ | Phải xem chiều iterator và đầu pop |
| Dùng add xen push không thay thứ tự | Add cuối, push đầu trên Deque |
| Deque.equals so nội dung như Stack | ArrayDeque không có equals theo List |
| Chuyển sang ArrayList vẫn tự có đồng bộ hóa của Vector | ArrayList không tự đồng bộ hóa |
| BlockingDeque.push luôn chờ khi đầy | Chờ phụ thuộc phương thức được chọn |
| Cấu trúc đồng thời làm nhiều thao tác thành giao dịch | Vẫn cần thiết kế các bất biến trải qua nhiều bước |

## 14. Thực hành và lời giải

### Bài 1. Size hay capacity?

Vector có hai phần tử. Gọi ensureCapacity(100), rồi setSize(5). Số phần tử sau từng bước là bao nhiêu và các vị trí mới chứa gì?

**Lời giải:** Sau ensureCapacity vẫn là hai. Sau setSize là năm, ba vị trí mới là null. Hai thao tác có mục đích khác nhau.

### Bài 2. Search và index

Stack được push A, B, C. Search A và indexOf A trả gì? Nếu có một A khác ở đỉnh thì search A chọn lần nào?

**Lời giải:** Ban đầu search A bằng ba, indexOf A bằng không. Nếu có A ở đỉnh, search A bằng một vì chọn lần xuất hiện gần đỉnh nhất.

### Bài 3. Đổi peek

Mã cũ bắt EmptyStackException từ peek khi không có dữ liệu. Đổi sang ArrayDeque.peek có giữ hành vi đó không? GetFirst khác gì?

**Lời giải:** Không. Peek của deque trả null. GetFirst ném NoSuchElementException khi rỗng, vẫn không cùng loại lỗi với Stack. Cần chọn và cập nhật chính sách báo vắng theo hợp đồng mong muốn.

### Bài 4. Chuyển nội dung

Stack có A, B, C theo thứ tự iterator. New ArrayDeque từ nguồn rồi pop lấy gì? Nêu hai quy ước để giữ thứ tự lấy C, B, A.

**Lời giải:** Pop lấy A. Có thể duyệt Stack rồi push từng phần tử vào deque mới để đỉnh ở đầu, hoặc sao chép theo iterator nhưng dùng removeLast/addLast/peekLast với đỉnh ở cuối. Không được trộn quy ước.

### Bài 5. Vì sao hai luồng vẫn lỗi?

Stack có một phần tử; cả hai luồng đều chạy empty rồi pop mà không có khóa bao cả chuỗi. Các phương thức đều có cơ chế đồng bộ hóa. Lỗi có thể xuất hiện ở đâu?

**Lời giải:** Cả hai kiểm tra không rỗng trước khi ai pop. Luồng thứ nhất lấy phần tử; luồng thứ hai pop trên rỗng. Từng lời gọi đúng nhưng điều kiện kiểm tra không được giữ tới cập nhật. Cần thao tác ghép được bảo vệ hoặc API gộp đúng mục đích trên cấu trúc phù hợp.

### Bài 6. Null khi thay thế

Dữ liệu Stack cũ có null như một phần tử hợp lệ. Có thể đổi ngay sang ArrayDeque rồi bỏ qua null trong lúc sao chép không?

**Lời giải:** Không thể gọi đó là chuyển đổi bảo toàn hành vi. ArrayDeque không nhận null; lặng lẽ bỏ nó thay dữ liệu và có thể thay thứ tự hoặc số tác vụ. Cần xác định biểu diễn dữ liệu phù hợp và chính sách chuyển đổi rõ ràng.

### Bài 7. Chọn danh sách dùng chung

Danh sách có rất nhiều lượt duyệt và rất ít cập nhật. Vì sao CopyOnWriteArrayList đáng xem xét nhưng không phải lựa chọn mặc định cho mọi tải ghi?

**Lời giải:** Iterator ảnh chụp cho phép duyệt không bị cập nhật cấu trúc sau đó can thiệp vào mảng đang duyệt. Đổi lại, mỗi thao tác sửa tạo mảng mới; ghi thường xuyên có thể gây chi phí sao chép và bộ nhớ lớn.

### Bài 8. So sánh sau chuyển đổi

Một bài kiểm tra trước đây dùng Stack.equals để so hai chuỗi. Sau khi thay bằng ArrayDeque, equals trả false dù phần tử in giống nhau. Có đủ kết luận chuyển dữ liệu sai không?

**Lời giải:** Không. ArrayDeque không có equals theo nội dung List. Cần so chuỗi phần tử theo thứ tự cần bảo toàn, đặc biệt là thứ tự lấy ra nếu đây là ngăn xếp.

## 15. Quyết định thay thế dựa vào hợp đồng

Hãy xác định cần List hay LIFO, có cho null không, rỗng được báo thế nào, chiều duyệt có ý nghĩa gì và có thao tác ghép giữa nhiều luồng hay không.

ArrayList và ArrayDeque giúp diễn đạt nhiều nhu cầu mới rõ ràng hơn, còn các cấu trúc đồng thời cung cấp những hợp đồng phối hợp khác nhau. Hiểu các điều đang được giữ mới cho phép thay Vector hoặc Stack mà không đổi hành vi ngoài ý định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="specialized-collections-007">Hashtable, Dictionary và Properties trong mã đời cũ</a>

<details>
<summary>Xem chi tiết</summary>

# Hashtable, Dictionary và Properties trong mã đời cũ

## 1. Đọc đúng vai trò trước khi thay lớp

Khi bảo trì một ứng dụng Java lâu năm, có thể gặp Dictionary trong chữ ký phương thức, Hashtable trong vùng dữ liệu dùng chung và Properties ở chỗ nạp cấu hình.

Ba tên này có quan hệ kế thừa, nhưng không có cùng mục đích:

| Thành phần | Vai trò cần nhận diện |
| --- | --- |
| Dictionary<K,V> | Lớp trừu tượng cũ biểu diễn ánh xạ khóa–giá trị |
| Hashtable<K,V> | Một triển khai bảng băm có đồng bộ, đồng thời hỗ trợ giao diện Map |
| Properties | Bộ thuộc tính dạng chuỗi với đọc/ghi cấu hình và cơ chế giá trị mặc định |

Nếu coi cả ba chỉ là “Map cũ”, có thể thay mã biên dịch được nhưng làm đổi null, đồng bộ, cách duyệt hoặc cách tìm giá trị mặc định.

Mục tiêu khi đọc mã đời cũ là xác định hợp đồng mà đoạn mã đang dựa vào. Sau đó mới quyết định giữ một ranh giới tương thích, chuyển sang Map hay tiếp tục dùng Properties cho đúng công việc cấu hình của nó.

Từ “đời cũ” ở đây không đồng nghĩa cả ba lớp đều có annotation @Deprecated. Trong API Java 17, Dictionary được mô tả là obsolete; Hashtable và Properties vẫn là các API có mặt để sử dụng và tương thích. Phương thức Properties.save mới là một ví dụ có đánh dấu deprecated cụ thể.

Bài dùng Java 17+ và bốn chương trình độc lập. Mỗi ví dụ có tên tệp, lệnh chạy và stdout; không dựa vào thứ tự duyệt ngẫu nhiên của bảng băm.

## 2. Quan hệ giữa Dictionary, Hashtable, Map và Properties

Có thể đọc quan hệ kiểu như sau:

```text
Object
└── Dictionary<K,V>
    └── Hashtable<K,V>  implements Map<K,V>
        └── Properties  extends Hashtable<Object,Object>
```

### 2.1. Dictionary là lớp trừu tượng, không phải tên khác của Map

Dictionary có các phép cơ bản như get, put, remove, size, isEmpty và hai phương thức keys/elements trả Enumeration.

Nó không khai báo triển khai Map. Vì vậy một biến có kiểu Dictionary không tự có keySet hay entrySet chỉ vì đối tượng thực tế bên dưới có thể là Hashtable.

Ngược lại, một Map bất kỳ không tự là Dictionary. Không thể đổi tất cả chữ Dictionary thành Map ở một phía rồi giả định mọi bên gọi cũ vẫn nhận đúng kiểu.

Tài liệu [Dictionary Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Dictionary.html) hướng các triển khai mới tới Map thay vì mở rộng Dictionary.

### 2.2. Hashtable là cầu nối qua quan hệ kiểu

Một đối tượng Hashtable có thể được nhìn qua biến Dictionary hoặc biến Map mà không cần sao chép dữ liệu.

Ba biến trỏ tới cùng đối tượng vẫn cùng nhìn một bảng. Đổi kiểu biến tham chiếu không biến Hashtable thành HashMap, không bỏ đồng bộ và không mở quyền nhận null.

Ngược lại, tạo một HashMap mới rồi chép các ánh xạ là một hành động tạo bảng khác. Bản sao không tự cập nhật theo bảng nguồn.

### 2.3. Properties kế thừa một bề mặt rộng hơn ý nghĩa cấu hình

Properties kế thừa Hashtable<Object,Object>, không phải Hashtable<String,String>.

API cấu hình của nó muốn tên thuộc tính và giá trị đều là String. Tuy nhiên API kế thừa như put(Object,Object) vẫn tồn tại, nên trình biên dịch không chặn mọi trường hợp đưa sai kiểu vào.

Đây là một điểm cần đọc bằng ý nghĩa API, không chỉ nhìn phương thức nào gọi được.

## 3. Dictionary và Enumeration: đọc một API cũ có kiểm soát

### 3.1. Cách duyệt cơ bản

Enumeration có hai phương thức quen thuộc:

| Enumeration | Ý nghĩa |
| --- | --- |
| hasMoreElements() | Còn phần tử để lấy hay không |
| nextElement() | Lấy phần tử kế tiếp; hết phần tử thì có thể ném NoSuchElementException |

Để đọc từng cặp từ Dictionary, có thể duyệt keys rồi gọi get(key). Không cần ép kiểu thô hoặc giả định hai luồng duyệt keys và elements có thể ghép từng vị trí với nhau.

Một Enumeration không trực tiếp cung cấp thao tác remove như Iterator. Java 9+ có asIterator để thích nghi với mã nhận Iterator; thao tác remove của bộ thích nghi mặc định không được hỗ trợ. Không tiếp tục dùng cả Enumeration gốc lẫn Iterator ấy xen kẽ để điều khiển cùng lần duyệt. Xem [Enumeration Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Enumeration.html).

### 3.2. Ví dụ 1: cùng đối tượng khác với bản sao

Lưu thành **LegacyDictionaryBridge.java**:

```java
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.TreeMap;

public class LegacyDictionaryBridge {
    static <K, V> Map<K, V> copyEntries(Dictionary<K, V> source) {
        Map<K, V> copy = new HashMap<>();
        Enumeration<K> keys = source.keys();
        while (keys.hasMoreElements()) {
            K key = keys.nextElement();
            copy.put(key, source.get(key));
        }
        return copy;
    }

    public static void main(String[] args) {
        Hashtable<String, Integer> table = new Hashtable<>();
        Dictionary<String, Integer> dictionary = table;
        Map<String, Integer> alias = table;
        dictionary.put("alpha", 10);
        dictionary.put("beta", 20);

        Map<String, Integer> copy;
        synchronized (table) {
            copy = copyEntries(dictionary);
        }
        System.out.println("dictionary alpha: " + dictionary.get("alpha"));
        System.out.println("contains beta as value: " + table.contains("beta"));
        System.out.println("containsKey beta: " + table.containsKey("beta"));
        System.out.println("contains value 20: " + table.contains(20));
        System.out.println("copy before update: " + new TreeMap<>(copy));

        table.put("alpha", 99);
        System.out.println("alias alpha after update: " + alias.get("alpha"));
        System.out.println("copy alpha after update: " + copy.get("alpha"));

        try {
            table.put(null, 1);
        } catch (NullPointerException expected) {
            System.out.println("null key: rejected");
        }
        try {
            table.put("gamma", null);
        } catch (NullPointerException expected) {
            System.out.println("null value: rejected");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 LegacyDictionaryBridge.java
java LegacyDictionaryBridge
```

Stdout:

```text
dictionary alpha: 10
contains beta as value: false
containsKey beta: true
contains value 20: true
copy before update: {alpha=10, beta=20}
alias alpha after update: 99
copy alpha after update: 10
null key: rejected
null value: rejected
```

Các chương trình trong bài được biên dịch với --release 17 và chạy trên OpenJDK 24.0.2. Cờ --release chọn mức ngôn ngữ/API và bytecode đích, không đổi phiên bản JVM thực thi.

### 3.3. Vì sao alias thấy 99 còn copy vẫn là 10?

Các biến dictionary, table và alias cùng trỏ tới đối tượng Hashtable. Bởi vậy thay alpha qua table được nhìn thấy qua alias.

Biến copy là HashMap mới chứa các ánh xạ đã đọc trước đó. Việc thay liên kết alpha trong bảng nguồn không thay liên kết alpha ở bản sao.

Đây là bản sao các ánh xạ, không phải sao chép sâu mọi đối tượng khóa/giá trị. Nếu value là một đối tượng khả biến được chia sẻ, hai bảng vẫn có thể nhìn cùng đối tượng value.

TreeMap trong lệnh in chỉ làm thứ tự stdout ổn định theo khóa String. Không phải bằng chứng Hashtable hay HashMap giữ thứ tự chữ cái.

### 3.4. Đồng bộ khi chép phụ thuộc đối tượng nguồn thực tế

Hàm copyEntries không tự cung cấp một giao dịch sao chép an toàn cho mọi triển khai Dictionary.

Trong ví dụ, nguồn thực tế là Hashtable và đoạn gọi bao toàn bộ lần duyệt bằng synchronized(table). Điều này dùng cùng đối tượng giám sát với các thao tác đồng bộ của Hashtable; không có thao tác sửa nào khác trong chương trình chen vào.

Với một triển khai Dictionary khác, phải đọc hợp đồng đồng thời của nó. Chỉ đặt synchronized lên một biến tham chiếu bất kỳ không tự bảo đảm mọi bên ghi đều phối hợp trên cùng khóa đồng bộ.

## 4. Những đặc điểm Hashtable thường làm thay đổi kết quả bảo trì

### 4.1. Không nhận null ở cả khóa lẫn giá trị

Hashtable từ chối put(null, value) và put(key, null). Các phép tra như get(null) cũng không phải cách hỏi về một khóa null hợp lệ.

Với trạng thái hợp lệ không có value null, get trả null có nghĩa không có ánh xạ cho khóa đang hỏi tại lần truy cập đó.

Không chuyển ý này thành bảo đảm rằng ánh xạ vẫn vắng mặt sau đó khi có luồng khác đang cập nhật.

[Tài liệu Hashtable Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Hashtable.html) nêu chính sách null và quan hệ equals/hashCode của khóa.

### 4.2. `contains` trong mã cũ tìm value

Tên contains(Object) dễ bị đọc nhầm thành “có khóa này không”. Ở Hashtable, nó kiểm tra value, cùng ý nghĩa chức năng với containsValue.

Trong ví dụ:

- `contains`("beta") là false vì không có value String "beta".
- `containsKey`("beta") là true vì có khóa beta.
- `contains`(20) là true vì beta gắn với value 20.

Khi chuyển mã sang Map, hãy dịch đúng ý nghĩa sang containsValue hoặc containsKey. Không thay theo tên gần giống.

### 4.3. Không có cam kết thứ tự duyệt

Hashtable không cung cấp hợp đồng thứ tự chèn hay thứ tự sắp xếp khóa.

Nếu một kiểm thử so cả chuỗi toString có nhiều khóa, kết quả có thể đang phụ thuộc chi tiết triển khai không cần thiết. Khi thứ tự là yêu cầu thật, hãy biểu diễn yêu cầu ấy bằng cấu trúc hoặc bước sắp xếp thích hợp.

Khóa vẫn cần equals/hashCode nhất quán. Việc có synchronized không sửa được một khóa bị đổi các trường dùng tính hash khi đang nằm trong bảng.

### 4.4. Enumeration và Iterator không có cùng hợp đồng

Với Hashtable:

| Cách duyệt | Điều cần nhớ |
| --- | --- |
| keys()/elements() trả Enumeration | Không fail-fast; nếu sửa cấu trúc trong lúc duyệt, kết quả không được xác định theo hợp đồng |
| Iterator từ các collection view | Có cơ chế fail-fast theo best effort đối với sửa cấu trúc ngoài đường được cho phép |
| Duyệt một lần với nhiều lời gọi | Không tự là một thao tác nguyên tử chỉ vì từng phương thức map có đồng bộ |

Không nhận được ConcurrentModificationException không chứng minh Enumeration đang duyệt an toàn. Ngoại lệ fail-fast cũng không phải cơ chế bảo vệ dữ liệu dùng chung.

Đừng suy tiếp bảng này sang mọi lớp con: Properties có mô tả iterator riêng trong Java 17.

### 4.5. Đồng bộ là một đặc điểm, không phải câu trả lời cho mọi thao tác

Hashtable bảo vệ các thao tác của nó bằng cơ chế đồng bộ. Điều đó không tự làm một chuỗi do ứng dụng viết thành một thao tác không thể bị chen giữa.

Cũng không có bảo đảm mọi trường bên trong một value khả biến được giữ trong bảng đều được bảo vệ khi ứng dụng tự sửa chúng ngoài map.

Khi bảo trì, cần xác định dữ liệu được bảo vệ, ranh giới thao tác và tất cả nơi truy cập cùng dữ liệu.

## 5. Ví dụ 2: vì sao hai lời gọi an toàn riêng vẫn có thể mất cập nhật?

### 5.1. Một lịch xen kẽ đủ gây lỗi

Muốn tăng hits, hai luồng đều có thể đọc cùng giá trị 0 rồi mỗi luồng ghi lại 1:

| Thời điểm | Luồng A | Luồng B |
| --- | --- | --- |
| 1 | get nhận 0 | |
| 2 | | get nhận 0 |
| 3 | put 1 | |
| 4 | | put 1 |

Không có get hay put nào cần làm hỏng cấu trúc bảng. Lỗi nằm ở ý định “đọc rồi tăng rồi ghi” chưa được bảo vệ như một đơn vị.

Chương trình dưới dùng CyclicBarrier để buộc cả hai luồng đọc xong trước khi ghi. Vì vậy kết quả 1 ở phần đầu được tạo có chủ đích, không phải mong một lỗi tranh chấp tình cờ xuất hiện.

### 5.2. Chương trình đầy đủ

Lưu thành **HashtableCompoundActions.java**:

```java
import java.util.Hashtable;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class HashtableCompoundActions {
    static int separatedReadWrite(ExecutorService pool) throws Exception {
        Hashtable<String, Integer> counter = new Hashtable<>();
        counter.put("hits", 0);
        CyclicBarrier bothRead = new CyclicBarrier(2);
        Callable<Void> increment = () -> {
            int before = counter.get("hits");
            bothRead.await(5, TimeUnit.SECONDS);
            counter.put("hits", before + 1);
            return null;
        };
        Future<Void> first = pool.submit(increment);
        Future<Void> second = pool.submit(increment);
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        return counter.get("hits");
    }

    static int protectedIncrements(ExecutorService pool, boolean useMerge)
            throws Exception {
        Hashtable<String, Integer> counter = new Hashtable<>();
        counter.put("hits", 0);
        Runnable increment = () -> {
            for (int i = 0; i < 1000; i++) {
                if (useMerge) {
                    counter.merge("hits", 1, Integer::sum);
                } else {
                    synchronized (counter) {
                        int before = counter.get("hits");
                        counter.put("hits", before + 1);
                    }
                }
            }
        };
        Future<?> first = pool.submit(increment);
        Future<?> second = pool.submit(increment);
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        return counter.get("hits");
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            System.out.println("two separated increments: " + separatedReadWrite(pool));
            System.out.println("locked increments: " + protectedIncrements(pool, false));
            System.out.println("merge increments: " + protectedIncrements(pool, true));
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 HashtableCompoundActions.java
java HashtableCompoundActions
```

Stdout:

```text
two separated increments: 1
locked increments: 2000
merge increments: 2000
```

### 5.3. Hai cách bảo vệ phù hợp với ví dụ

Khối synchronized(counter) giữ cả get và put trong cùng phạm vi đồng bộ. Với cùng đối tượng Hashtable và các bên truy cập phối hợp đúng, một lần tăng được thực hiện trọn trước khi lần tăng khác vào đoạn ấy.

Cách merge dùng thao tác cập nhật phù hợp thay cho hai lời gọi tách rời. Trong triển khai OpenJDK 17, Hashtable override merge bằng phương thức đồng bộ; có thể đối chiếu [Hashtable.java](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/Hashtable.java).

Ví dụ dùng hàm cộng ngắn, không sửa lại map từ bên trong hàm truyền vào. Không mở rộng kết luận thành “mọi merge của mọi Map đều nguyên tử”: cần đọc hợp đồng của triển khai cụ thể.

Hai luồng trong mỗi phần bảo vệ thực hiện 1.000 lần tăng, nên kết quả cuối là 2.000. Time-out của barrier/Future giúp thí nghiệm báo lỗi nếu không hoàn tất; chúng không quyết định thứ tự in đầu ra.

### 5.4. Thay Hashtable bằng ConcurrentHashMap vẫn cần chọn đúng thao tác

ConcurrentHashMap cung cấp các thao tác cập nhật đồng thời có hợp đồng riêng. Nó không tự gộp một đoạn get rồi put do ứng dụng viết thành một lần tăng nguyên tử.

Nếu yêu cầu là tăng theo khóa, phải dùng thao tác thích hợp hoặc một mô hình bảo vệ phù hợp. Nếu yêu cầu liên quan nhiều khóa cùng lúc, còn cần xem bất biến của cả nhóm.

Tài liệu [ConcurrentHashMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html) là cơ sở để đánh giá việc thay thế, không chỉ so chữ “thread-safe”.

## 6. Properties là dữ liệu cấu hình dạng chuỗi với lớp mặc định

### 6.1. Cặp API nên dùng là setProperty/getProperty

Một thuộc tính cấu hình có tên String và giá trị String. Ví dụ port là chuỗi "8080" khi còn ở lớp đọc cấu hình; ứng dụng sẽ phân tích và kiểm tra thành số nguyên ở ranh giới phù hợp.

`setProperty` nhận hai String. `getProperty` trả String hoặc giá trị dự phòng theo hợp đồng. Cặp này thể hiện ý định rõ hơn API put/get kế thừa.

Do kế thừa Hashtable<Object,Object>, Properties vẫn cho phép một lệnh put("port", 9090) biên dịch. Điều đó đưa Integer vào vùng dữ liệu vốn được kỳ vọng là chuỗi.

Không coi việc gọi được phương thức là bằng chứng kiểu dữ liệu phù hợp với toàn bộ API Properties. Tài liệu [Properties Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Properties.html) khuyến cáo dùng setProperty và mô tả lỗi khi lưu một bộ thuộc tính có phần tử sai kiểu.

### 6.2. Defaults là nguồn dự phòng, không phải putAll

Một Properties có thể nhận Properties khác làm defaults. `getProperty` tìm trong lớp hiện tại rồi tiếp tục qua chuỗi defaults khi cần.

Các phần tử mặc định không tự được chép vào bảng hiện tại. Do đó get, containsKey và size của phần ánh xạ hiện tại không có cùng ý nghĩa với một phép tra cấu hình hiệu lực qua getProperty.

| Cách đọc | Phạm vi cần hiểu |
| --- | --- |
| get(key) | Ánh xạ trực tiếp trong đối tượng hiện tại, trả Object |
| containsKey(key), size() | Ánh xạ trực tiếp trong đối tượng hiện tại |
| getProperty(name) | Tra thuộc tính String có xét chuỗi defaults |
| getProperty(name, fallback) | Xét chuỗi defaults trước; nếu không có thì trả fallback truyền vào |
| stringPropertyNames() | Tập tên thuộc tính chuỗi, gồm các tên phù hợp từ defaults |
| propertyNames() | Enumeration tên có xét defaults; có thể lỗi nếu bị đưa khóa không phải String |

Xóa một phần ghi đè ở lớp hiện tại có thể làm giá trị mặc định xuất hiện trở lại. `remove` không có nghĩa tên thuộc tính biến mất khỏi toàn bộ chuỗi dự phòng.

### 6.3. Defaults có thể còn được chia sẻ

Nếu ứng dụng tiếp tục giữ đối tượng defaults và sửa nó, kết quả tra ở đối tượng con có thể đổi theo.

Đó là quan hệ tới một nguồn mặc định, không phải lời hứa đã tạo ảnh chụp độc lập lúc gọi constructor.

Khi cần một cấu hình ổn định cho một phiên bản chạy, có thể đọc các giá trị hiệu lực, kiểm tra chúng rồi tạo một cấu trúc dữ liệu ổn định riêng. Không để việc chia sẻ đối tượng vô tình quyết định lúc cấu hình thay đổi.

## 7. Ví dụ 3: get, getProperty, defaults và sai kiểu

Lưu thành **PropertiesDefaultsAndTypes.java**:

```java
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

public class PropertiesDefaultsAndTypes {
    public static void main(String[] args) throws Exception {
        Properties defaults = new Properties();
        defaults.setProperty("host", "localhost");
        defaults.setProperty("mode", "safe");
        defaults.setProperty("port", "8080");

        Properties config = new Properties(defaults);
        config.setProperty("mode", "fast");
        System.out.println("get host: " + config.get("host"));
        System.out.println("getProperty host: " + config.getProperty("host"));
        System.out.println("containsKey host: " + config.containsKey("host"));
        System.out.println("local size: " + config.size());
        System.out.println("effective names: " + new TreeSet<>(config.stringPropertyNames()));

        config.remove("mode");
        System.out.println("mode after remove override: " + config.getProperty("mode"));
        config.setProperty("mode", "fast");
        defaults.setProperty("host", "server");
        System.out.println("host after defaults change: " + config.getProperty("host"));

        Map<String, String> effective = new TreeMap<>();
        for (String name : config.stringPropertyNames()) {
            effective.put(name, config.getProperty(name));
        }
        System.out.println("effective copy: " + effective);

        StringWriter writer = new StringWriter();
        config.store(writer, null);
        Properties reloaded = new Properties();
        try (StringReader reader = new StringReader(writer.toString())) {
            reloaded.load(reader);
        }
        System.out.println("reloaded mode: " + reloaded.getProperty("mode"));
        System.out.println("reloaded host: " + reloaded.getProperty("host"));
        System.out.println("reloaded names: " + new TreeSet<>(reloaded.stringPropertyNames()));

        config.put("port", 9090);
        System.out.println("raw port type: " + config.get("port").getClass().getSimpleName());
        System.out.println("getProperty port: " + config.getProperty("port"));
        try {
            config.store(new StringWriter(), null);
        } catch (ClassCastException expected) {
            System.out.println("store non-string value: rejected");
        }
        config.put(7, "bad-key");
        try {
            config.propertyNames();
        } catch (ClassCastException expected) {
            System.out.println("propertyNames non-string key: rejected");
        }
        System.out.println("string names after pollution: "
                + new TreeSet<>(config.stringPropertyNames()));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 PropertiesDefaultsAndTypes.java
java PropertiesDefaultsAndTypes
```

Stdout:

```text
get host: null
getProperty host: localhost
containsKey host: false
local size: 1
effective names: [host, mode, port]
mode after remove override: safe
host after defaults change: server
effective copy: {host=server, mode=fast, port=8080}
reloaded mode: fast
reloaded host: null
reloaded names: [mode]
raw port type: Integer
getProperty port: 8080
store non-string value: rejected
propertyNames non-string key: rejected
string names after pollution: [host, mode, port]
```

### 7.1. Host tồn tại về mặt cấu hình nhưng không nằm ở bảng hiện tại

Config chỉ có phần ghi đè mode, nên local size bằng 1. `get`("host") trả null và containsKey("host") trả false.

`getProperty`("host") vẫn tìm được "localhost" từ defaults. Tập stringPropertyNames gồm host, mode và port.

Sau khi xóa phần ghi đè mode, getProperty lại thấy "safe". Sau khi sửa host ở defaults, config thấy "server".

Những kết quả này cho thấy không thể thay getProperty bằng get rồi ép kiểu một cách máy móc.

### 7.2. Chép giá trị hiệu lực khác với chép phần tử trực tiếp

Vòng lặp stringPropertyNames/getProperty tạo một bản đồ String–String chứa các giá trị hiệu lực trong điều kiện ví dụ không có ai đồng thời thay cấu hình.

Đây là lựa chọn rõ ràng để đưa dữ liệu cấu hình đã đọc sang một tầng dùng Map. new HashMap<>(config) sẽ chép phần ánh xạ trực tiếp kiểu Object–Object; nó không tự thực hiện cùng phép tra defaults.

Nếu có thay đổi đồng thời hoặc cần một phiên bản cấu hình nhất quán, vòng lặp nhiều lời gọi phải được đặt trong một quy trình công bố/đọc cấu hình phù hợp. Không coi nó là ảnh chụp nguyên tử trong mọi trường hợp.

### 7.3. `store` không tự lưu toàn bộ defaults

Config chỉ ghi trực tiếp mode vào dữ liệu lưu. Khi nạp lại vào Properties mới không có defaults, mode vẫn là fast nhưng host không có.

Nếu muốn một tệp chứa toàn bộ giá trị hiệu lực, cần quyết định rõ việc làm phẳng chuỗi defaults, tạo bộ thuộc tính chuỗi sạch rồi lưu bộ ấy.

Nếu muốn tệp chỉ lưu phần ghi đè để còn dùng nguồn mặc định lúc chạy, hành vi hiện tại lại là điều cần giữ. Không tự thay chính sách trong quá trình bảo trì.

### 7.4. Phần sai kiểu có thể tạo biểu hiện gây hiểu nhầm

Sau put("port", 9090), get trả Integer. Trong ví dụ này, getProperty vẫn trả chuỗi "8080" từ defaults thay vì chuyển Integer 9090 thành String.

Mã nguồn [Properties.java của OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/Properties.java) cho thấy getProperty chỉ nhận giá trị kiểu String ở lớp hiện tại trước khi dùng đường dự phòng.

Không dùng đặc điểm đó làm kỹ thuật cấu hình. Nó cho thấy một phần tử sai kiểu có thể bị che bởi giá trị dự phòng, khiến mã đọc thô và mã đọc thuộc tính nhìn dữ liệu khác nhau.

`store` từ chối bộ thuộc tính có value không phải String. `propertyNames` từ chối khi có khóa Integer. `stringPropertyNames` bỏ qua phần tử sai kiểu và vẫn có thể đưa tên port từ defaults vào kết quả.

Tập tên chuỗi không phải phép xác nhận toàn bộ đối tượng không chứa dữ liệu sai kiểu. Cần ngăn hoặc kiểm tra sai kiểu ở ranh giới nhập.

## 8. Định dạng .properties và mã hóa: nhìn rõ hai lớp xử lý

### 8.1. Byte thành ký tự rồi ký tự thành thuộc tính

Khi đọc một tệp, có hai việc khác nhau:

1. Giải mã byte thành ký tự theo một bảng mã.
2. Phân tích cú pháp dòng thuộc tính, dấu phân cách và escape.

`load`(InputStream) có quy tắc ISO-8859-1 riêng. Nó không tự trở thành UTF-8 chỉ vì môi trường Java hiện tại có bộ ký tự mặc định là UTF-8.

`load`(Reader) nhận ký tự đã được Reader giải mã. Muốn đọc tệp UTF-8, tạo Reader với UTF-8 rõ ràng, chẳng hạn qua Files.newBufferedReader hoặc InputStreamReader.

[Files Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html) mô tả các API Reader/Writer theo Charset; [Properties Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Properties.html) quy định overload byte và overload ký tự.

### 8.2. Một mẫu dữ liệu thật, không phải chuỗi literal Java

Phần dưới là nội dung thuộc tính sau khi tạo chuỗi trong chương trình, tương đương dữ liệu một tệp UTF-8:

```properties
# Example properties
message=Chào bạn
path=C:\\temp\\data
joined=first\
  second
escaped\ key:some\:value
empty=
bare
quoted="hello"
duplicate=old
duplicate=new
```

Ở định dạng này:

- Dòng chú thích có dấu # hoặc ! ở vị trí bắt đầu thích hợp.
- Tên và giá trị có thể được tách bằng dấu =, dấu : hoặc khoảng trắng chưa escape theo quy tắc cú pháp.
- Khoảng trắng hoặc dấu phân cách thuộc về tên cần được escape khi có thể gây nhầm.
- Một dòng có tên nhưng không còn phần giá trị tạo giá trị chuỗi rỗng.
- Dấu nháy không tự tạo cú pháp trích dẫn giống JSON; ký tự nháy ở giá trị có thể được giữ nguyên.
- Dấu gạch chéo ngược liên quan tới escape và nối dòng, không phải ký tự luôn được giữ nguyên.

Dòng joined có gạch chéo ngược nối dòng. Dấu ấy, ký tự kết thúc dòng và khoảng trắng đầu dòng kế tiếp bị bỏ; không tự chèn một khoảng trắng vào giữa first và second.

### 8.3. Cẩn thận với đường dẫn và hai lần escape

Muốn dữ liệu giá trị chứa một gạch chéo ngược, tệp .properties thường phải biểu diễn nó bằng hai gạch chéo ngược.

Nếu viết chính nội dung tệp ấy bằng chuỗi literal Java, còn phải xử lý lớp escape của Java. Vì vậy đoạn Java trong chương trình có nhiều gạch chéo hơn nội dung tệp ở trên.

Không sửa số dấu chỉ dựa vào hình thức trông có vẻ dư. Cần xác định đang đọc mã nguồn Java, văn bản properties hay giá trị đã parse.

Một escape không hợp lệ theo kiểu ký tự thông thường có thể bị bộ đọc Properties bỏ dấu gạch chéo; nó không luôn báo lỗi giống trình biên dịch Java. Unicode escape sai dạng lại có thể gây IllegalArgumentException.

Properties cũng không tự mở rộng biến môi trường hay tính biểu thức chỉ vì value chứa một chuỗi có hình thức đặc biệt. Nếu một framework có thêm cơ chế đó, phải phân biệt nó với java.util.Properties.

### 8.4. Chương trình đầy đủ kiểm tra UTF-8, escape và nạp lại

Lưu thành **PropertiesTextAndEncoding.java**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class PropertiesTextAndEncoding {
    public static void main(String[] args) throws Exception {
        String text = "# Example properties\n"
                + "message=Chào bạn\n"
                + "path=C:\\\\temp\\\\data\n"
                + "joined=first\\\n"
                + "  second\n"
                + "escaped\\ key:some\\:value\n"
                + "empty=\n"
                + "bare\n"
                + "quoted=\"hello\"\n"
                + "duplicate=old\n"
                + "duplicate=new\n";
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);

        Properties fromReader = new Properties();
        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(utf8), StandardCharsets.UTF_8)) {
            fromReader.load(reader);
        }
        Properties fromBytes = new Properties();
        try (ByteArrayInputStream input = new ByteArrayInputStream(utf8)) {
            fromBytes.load(input);
        }

        System.out.println("UTF-8 Reader message: "
                + "Chào bạn".equals(fromReader.getProperty("message")));
        System.out.println("byte overload message: "
                + "Chào bạn".equals(fromBytes.getProperty("message")));
        System.out.println("path: " + fromReader.getProperty("path"));
        System.out.println("joined: " + fromReader.getProperty("joined"));
        System.out.println("escaped key: " + fromReader.getProperty("escaped key"));
        System.out.println("empty length: " + fromReader.getProperty("empty").length());
        System.out.println("bare length: " + fromReader.getProperty("bare").length());
        System.out.println("quoted: " + fromReader.getProperty("quoted"));
        System.out.println("duplicate: " + fromReader.getProperty("duplicate"));

        try (StringReader reader = new StringReader("duplicate=latest\n")) {
            fromReader.load(reader);
        }
        System.out.println("second load keeps path: " + fromReader.containsKey("path"));
        System.out.println("second load duplicate: " + fromReader.getProperty("duplicate"));

        StringWriter writer = new StringWriter();
        fromReader.store(writer, null);
        Properties writerRoundTrip = new Properties();
        try (StringReader reader = new StringReader(writer.toString())) {
            writerRoundTrip.load(reader);
        }
        System.out.println("Writer round-trip message: "
                + "Chào bạn".equals(writerRoundTrip.getProperty("message")));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        fromReader.store(output, null);
        Properties byteRoundTrip = new Properties();
        try (ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray())) {
            byteRoundTrip.load(input);
        }
        System.out.println("byte round-trip message: "
                + "Chào bạn".equals(byteRoundTrip.getProperty("message")));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 PropertiesTextAndEncoding.java
java PropertiesTextAndEncoding
```

Stdout:

```text
UTF-8 Reader message: true
byte overload message: false
path: C:\temp\data
joined: firstsecond
escaped key: some:value
empty length: 0
bare length: 0
quoted: "hello"
duplicate: new
second load keeps path: true
second load duplicate: latest
Writer round-trip message: true
byte round-trip message: true
```

### 8.5. Vì sao byte overload sai nhưng byte round-trip lại đúng?

Lần đọc đầu đưa các byte UTF-8 vào overload giả định ISO-8859-1. Hai phía không cùng quy tắc giải mã, nên chuỗi tiếng Việt nhận được không bằng dữ liệu mong muốn.

Ở lượt store(OutputStream), Properties viết theo định dạng byte và escape thích hợp của API ấy. `load`(InputStream) đọc lại dữ liệu do chính định dạng tương ứng tạo ra, nên giá trị tiếng Việt được khôi phục.

Điều này không có nghĩa tệp đầu vào ban đầu là UTF-8 nhưng load(InputStream) vẫn nhận đúng mọi trường hợp.

Reader/Writer cũng tạo một cặp nạp–lưu ở tầng ký tự. Khi Writer nối tới một tệp byte thật, người gọi vẫn phải chọn bảng mã cho nó.

### 8.6. `load` thêm dữ liệu, không tự xóa sạch cấu hình cũ

Dữ liệu mẫu có duplicate xuất hiện hai lần; giá trị sau ghi đè giá trị trước.

Sau đó chương trình load một tài liệu khác chỉ có duplicate=latest vào cùng đối tượng. Duplicate đổi thành latest, còn path vẫn có.

Vì vậy “nạp lại” có thể là trộn/ghi đè các khóa được đọc, không phải thay toàn bộ tập cấu hình bằng nội dung tệp mới.

Khi yêu cầu là thay toàn bộ cấu hình, đọc vào đối tượng mới, kiểm tra rồi công bố phiên bản mới thường dễ suy luận hơn việc sửa từng phần trên đối tượng đang được dùng.

## 9. Những quyết định đọc/ghi cần giữ khi bảo trì

### 9.1. `store` bảo toàn dữ liệu thuộc tính, không bảo toàn văn bản gốc

Không dùng load rồi store như một công cụ giữ nguyên chú thích, thứ tự dòng và cách căn lề của tệp gốc.

`store` có thể viết lại escape và có dòng thời gian. Vì vậy chương trình chỉ kiểm tra giá trị sau khi nạp lại, không in cả tệp store để làm stdout cố định.

Nếu một bài kiểm thử cần đối chiếu cấu hình, hãy so dữ liệu và chính sách lưu đúng phạm vi. Nếu yêu cầu là chỉnh sửa một tệp mà giữ nguyên trình bày do con người quản lý, Properties không cung cấp mô hình bảo toàn mọi chi tiết văn bản ấy.

### 9.2. Ai mở tài nguyên thì phải biết ai đóng nó

`load`(Reader) và load(InputStream) không tự đóng đối tượng được truyền vào. `store` thông thường cũng không tự đóng Writer/OutputStream.

Dùng try-with-resources tại nơi sở hữu tài nguyên khi đọc/ghi tệp thật. Các stream trong ví dụ là bộ nhớ; chúng giúp tập trung kiểm tra dữ liệu mà không tạo tệp bên ngoài.

`loadFromXML` là API riêng có quy tắc riêng, trong đó phương thức đóng input stream được truyền vào. Không suy một quy tắc đóng chung chỉ từ việc mọi phương thức đều thuộc Properties.

### 9.3. XML không dùng cùng đường đọc với định dạng dòng

Properties có loadFromXML/storeToXML để đọc/ghi định dạng XML thuộc tính của Java. `storeToXML` không chỉ là store rồi đổi đuôi tệp; overload mặc định dùng UTF-8.

Nếu hệ thống cũ đang dùng XML, giữ đúng cặp API và hợp đồng encoding tương ứng. Không nạp XML bằng load thông thường rồi kỳ vọng một bộ phân tích XML sẽ xuất hiện.

### 9.4. Save là một API deprecated cụ thể

Properties.save là phương thức cũ có thể che IOException; store là đường được tài liệu ưu tiên khi cần nhận lỗi ghi.

Điều cần sửa trong mã đời cũ có thể là chọn API báo lỗi đúng, không phải thay toàn bộ lớp Properties bằng một HashMap rồi mất chức năng định dạng, defaults và I/O.

## 10. Chuyển dần sang mã hiện đại mà giữ đúng ý nghĩa

### 10.1. Tách ranh giới tương thích với mô hình sử dụng bên trong

Nếu một thư viện cũ yêu cầu Dictionary, có thể cần tiếp tục truyền một đối tượng phù hợp ở ranh giới ấy. Bên trong ứng dụng mới, khai báo theo Map khi đó là hợp đồng thật sự cần dùng.

Không ép một Map bất kỳ sang Dictionary chỉ để qua trình biên dịch. Không coi bản sao là liên kết trực tiếp hoặc coi liên kết trực tiếp là một bản sao cách ly.

Nếu sửa chữ ký API công khai, cần xem các bên gọi và yêu cầu tương thích hiện có. Một thay đổi cấu trúc dữ liệu có thể ảnh hưởng nhiều hơn vài dòng khởi tạo.

### 10.2. Chọn cấu trúc theo yêu cầu, không theo tuổi đời tên lớp

| Nhu cầu | Hướng cần đánh giá |
| --- | --- |
| Ánh xạ thông thường không cần truy cập sửa đồng thời | Một Map phù hợp, thường bắt đầu đánh giá HashMap |
| Ánh xạ được nhiều luồng cập nhật | Cơ chế đồng bộ hoặc ConcurrentMap phù hợp với từng thao tác |
| Một chuỗi cập nhật nhiều bước hoặc nhiều khóa | Bất biến nghiệp vụ và phạm vi bảo vệ trọn chuỗi |
| Giao tiếp với API yêu cầu Dictionary | Ranh giới thích nghi có hợp đồng rõ |
| Nạp/lưu cấu hình chuỗi chuẩn Java | Properties vẫn có vai trò cụ thể |
| Đọc cấu hình vào logic nghiệp vụ | Phân tích, kiểm tra rồi chuyển thành mô hình có kiểu |

Đừng thay Hashtable bằng HashMap nếu mã đang dựa vào đồng bộ mà chưa thiết kế lại chỗ truy cập.

Ngược lại, không giữ Hashtable chỉ vì “đã thread-safe” khi ứng dụng cần khả năng cập nhật đồng thời với mức tranh chấp khác. Hãy đọc hợp đồng và đo tải thực tế nếu hiệu năng là yêu cầu.

### 10.3. Với cấu hình, kiểm tra giá trị trước khi dùng

`getProperty` trả String, không tự xác nhận chuỗi là số, nằm trong phạm vi cho phép hay là một tùy chọn hợp lệ.

Một giá trị dự phòng chỉ xử lý trường hợp không tìm được thuộc tính phù hợp; nó không biến một chuỗi sai như tên cổng không phải số thành một giá trị đã hợp lệ.

Hãy xác định giá trị bắt buộc, giá trị rỗng, khoảng trắng, lỗi phân tích và miền cho phép tại ranh giới nạp cấu hình. Sau đó truyền kiểu dữ liệu phù hợp vào phần nghiệp vụ.

Không tùy tiện gọi toString lên mọi Object sai kiểu để “sửa” Properties. Cách đó có thể che nguồn dữ liệu sai và làm mất ý nghĩa xác thực cấu hình.

### 10.4. Properties có hợp đồng đồng thời và iterator riêng

API Java 17 mô tả Properties là thread-safe. Điều đó không có nghĩa nhiều lần đọc các khóa liên quan luôn là một ảnh chụp cùng phiên bản, hoặc cả chuỗi defaults được khóa như một giao dịch.

Đặc biệt, tài liệu của Properties mô tả iterator ở collection view khác Hashtable: không áp máy móc kết luận mọi iterator Properties phải ném ConcurrentModificationException khi có thay đổi.

Việc cần một cấu hình nhất quán để thực hiện một yêu cầu nghiệp vụ nên được giải quyết bằng quy trình nạp, kiểm tra và công bố cấu hình phù hợp.

Quan hệ kế thừa trong API không bảo đảm mọi chi tiết tổ chức lưu trữ, tham số hiệu năng hay cách duyệt giống lớp cha.

## 11. Lỗi thường gặp và cách đọc lại yêu cầu

| Cách hiểu hoặc sửa | Điều bị bỏ sót |
| --- | --- |
| Dictionary là interface Map đời cũ | Nó là lớp trừu tượng và không tự khai báo Map |
| Đổi kiểu biến từ Hashtable sang Map là đổi triển khai | Đối tượng thực tế và chính sách của nó vẫn còn |
| new HashMap là một view của bảng cũ | Đó là bảng mới chứa bản sao ánh xạ |
| Hashtable.contains tìm khóa | Phương thức cũ này tìm value |
| Hashtable đồng bộ nên get rồi put là một lần tăng nguyên tử | Hai lời gọi có thể bị chen giữa |
| Enumeration không ném lỗi nên an toàn khi sửa bảng | Không fail-fast không tạo hợp đồng ảnh chụp hay an toàn đồng thời |
| Properties là Map<String,String> được bảo vệ kiểu hoàn toàn | Nó kế thừa API Object–Object và có thể bị đưa sai kiểu |
| `getProperty` luôn giống get rồi ép String | Còn có defaults và xử lý giá trị không phải String |
| `containsKey`/size tính cả defaults | Chúng phản ánh phần ánh xạ trực tiếp |
| `store` tự ghi cả defaults | Cần chủ động làm phẳng nếu muốn lưu cấu hình hiệu lực |
| `load`(InputStream) tự dùng UTF-8 hiện hành | Overload này có quy tắc ISO-8859-1 riêng |
| Dấu nháy trong properties hoạt động như JSON | Chúng có thể là ký tự của chính giá trị |
| `load` lần hai thay sạch lần một | Các khóa không được đọc lại có thể còn nguyên |
| `load`/store giữ nguyên tệp do con người định dạng | Đây là mô hình dữ liệu thuộc tính, không phải trình chỉnh sửa bảo toàn văn bản |
| Legacy nghĩa tất cả API đều @Deprecated | Phải xem từng lớp, từng phương thức cụ thể |

## 12. Bài tập tự kiểm tra

### Bài 1. Quan hệ kiểu có tạo bản sao không?

Một Hashtable được gán cho cả biến Dictionary và Map. `put` qua một biến có được nhìn thấy qua biến kia không?

**Gợi ý trả lời:** Có nếu chúng cùng trỏ tới đối tượng ấy. Việc gán tham chiếu không tạo bảng mới.

### Bài 2. `contains` đang tìm gì?

Hashtable có "name" → "Lan". `contains`("name") có nghĩa cùng với containsKey("name") không?

**Gợi ý trả lời:** Không. `contains` tìm value; containsKey tìm khóa. Trong dữ liệu này contains("name") là false còn containsKey("name") là true.

### Bài 3. Hai lần tăng còn một là lỗi ở đâu?

Hai luồng cùng get 0 rồi mỗi luồng put 1 vào Hashtable. Vì sao đồng bộ từng phương thức không ngăn kết quả cuối bằng 1?

**Gợi ý trả lời:** Ý định đọc–tăng–ghi nằm ở nhiều lời gọi. Phải bảo vệ cả chuỗi hoặc dùng thao tác cập nhật phù hợp của triển khai.

### Bài 4. Defaults có được chép vào size không?

Một Properties mới chỉ có defaults chứa ba tên, chưa setProperty trực tiếp tên nào. `size` và số tên stringPropertyNames có buộc bằng nhau không?

**Gợi ý trả lời:** Không. `size` chỉ phản ánh phần ánh xạ trực tiếp; tập tên chuỗi có xét defaults.

### Bài 5. Xóa phần ghi đè nghĩa là xóa cấu hình?

Defaults có mode=safe, lớp hiện tại có mode=fast. `remove`("mode") ở lớp hiện tại làm getProperty("mode") trả gì?

**Gợi ý trả lời:** Chuỗi safe, vì phần ghi đè đã mất và giá trị dự phòng lại được tìm thấy.

### Bài 6. Integer trong Properties có an toàn không?

`put`("port", 8080) biên dịch được. Có nên kết luận mọi API đọc/lưu Properties sẽ chấp nhận dữ liệu ấy không?

**Gợi ý trả lời:** Không. Thuộc tính cần tên và giá trị String; getProperty không phải phép chuyển Object thành String và store có thể từ chối phần tử sai kiểu.

### Bài 7. Tệp UTF-8 tiếng Việt nên đi qua đường nào?

Dữ liệu byte thực sự là UTF-8. `load`(InputStream) có tự dùng đúng UTF-8 không?

**Gợi ý trả lời:** Không. Tạo Reader với UTF-8 rõ ràng rồi dùng load(Reader), hoặc dùng đúng định dạng byte/escape của API byte nếu đó là hợp đồng tệp.

### Bài 8. Nạp hai lần có giữ khóa cũ không?

Lần một nạp a=1 và b=2. Lần hai nạp riêng a=9 vào cùng đối tượng. B có tự mất không?

**Gợi ý trả lời:** Không. A được ghi đè; b còn nếu không có thao tác xóa hay chính sách thay toàn bộ khác.

### Bài 9. Lưu rồi đọc lại có giữ defaults không?

Một đối tượng chỉ có phần ghi đè cục bộ, còn host nằm trong defaults. `store` rồi load vào Properties mới không có defaults có tự phục hồi host không?

**Gợi ý trả lời:** Không. Nếu muốn tệp đầy đủ, cần chủ động tạo dữ liệu chứa các giá trị hiệu lực để lưu.

### Bài 10. Đổi Hashtable thành ConcurrentHashMap đã đủ chưa?

Đoạn mã vẫn thực hiện get rồi put để tăng bộ đếm. Chỉ đổi lớp khởi tạo có bảo đảm không mất cập nhật không?

**Gợi ý trả lời:** Không. Cần chọn đúng thao tác nguyên tử hoặc mô hình bảo vệ phù hợp với toàn bộ ý định cập nhật.

## 13. Tài liệu đối chiếu

- [Dictionary — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Dictionary.html): lớp trừu tượng cũ và định hướng dùng Map cho triển khai mới.
- [Hashtable — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Hashtable.html): null, đồng bộ, contains và các hợp đồng duyệt.
- [Enumeration — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Enumeration.html): hasMoreElements, nextElement và asIterator.
- [Properties — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Properties.html): API chuỗi, defaults, định dạng dòng, encoding, I/O và iterator.
- [ConcurrentHashMap — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): thao tác cập nhật đồng thời và giới hạn của kết luận nguyên tử.
- [Files — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): tạo Reader/Writer theo Charset.
- [Hashtable.java — OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/Hashtable.java): đối chiếu các phương thức đồng bộ của triển khai được nêu.
- [Properties.java — OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/Properties.java): đối chiếu đường getProperty và dữ liệu chuỗi. Chi tiết triển khai cần được gắn với phiên bản, không thay hợp đồng API.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="specialized-collections-008">Enumeration và chuyển đổi sang Iterator hoặc List</a>

<details>
<summary>Xem chi tiết</summary>

# Enumeration và chuyển đổi sang Iterator hoặc List

## 1. Khi API cũ trả về một đối tượng dùng để duyệt

Một số API Java trả về `Enumeration<E>` thay vì List hoặc Iterator. Ví dụ, `Vector.elements()` cho phép lấy lần lượt các phần tử của Vector. Khi cần đưa dữ liệu đó vào mã dùng Iterator hoặc xử lý như một danh sách, trước hết phải xác định mình đang chuyển **cách duyệt** hay đang **thu dữ liệu thành một collection mới**.

`Enumeration` là một giao diện lấy tuần tự từng phần tử. Nó không phải nơi chứa toàn bộ dữ liệu theo hợp đồng của một List, cũng không phải từ khóa `enum` dùng để khai báo tập hằng.

Ba kết quả sau có ý nghĩa khác nhau:

| Đối tượng | Vai trò | Điều nên nghĩ đến |
|---|---|---|
| `Enumeration<E>` | Một lượt đọc tuần tự qua các phần tử | Còn gì chưa được lấy? |
| `Iterator<E>` | Giao diện duyệt phổ biến trong Collections Framework | Cách dùng hasNext/next và thao tác tùy chọn |
| `List<E>` | Collection chứa các phần tử theo vị trí | Lưu lại dữ liệu, xem theo chỉ số, tạo các lượt duyệt mới |

Chuyển từ Enumeration sang Iterator bằng `asIterator()` thích nghi giao diện của phần còn lại. Chuyển sang List bằng `Collections.list(...)` lấy hết phần còn lại để tạo một ArrayList mới. Cả hai đều không tự quay về phần tử đầu đã đọc.

Các chương trình đầy đủ trong bài dùng Java 17 trở lên. Phần adapter thủ công cuối bài còn được kiểm tra biên dịch với Java 8 để minh họa cách làm khi chưa có `Enumeration.asIterator()`.

## 2. Hợp đồng duyệt của Enumeration

### 2.1. Hai phương thức chính

| Phương thức | Kiểu trả về | Ý nghĩa |
|---|---|---|
| `hasMoreElements()` | boolean | Còn phần tử để lấy không? |
| `nextElement()` | E | Lấy phần tử tiếp theo và tiến lượt duyệt |

Gọi nextElement liên tiếp cho các phần tử liên tiếp. Khi đã hết, nextElement ném `NoSuchElementException`; nó không dùng null làm tín hiệu kết thúc.

Đoạn minh họa dưới đây đặt trong một phương thức đã có biến elements, không phải tệp Java độc lập:

```java
while (elements.hasMoreElements()) {
    String value = elements.nextElement();
    System.out.println(value);
}
```

Không gọi nextElement chỉ để kiểm tra còn dữ liệu hay không, vì mỗi lần gọi hợp lệ đều tiêu thụ một phần tử của lượt duyệt. Cũng không gọi nextElement hai lần cho cùng một vòng nếu chỉ muốn xử lý một phần tử: lần thứ hai sẽ đọc phần tử kế tiếp.

### 2.2. Một vị trí đọc đang tiến, không phải một collection tự lặp lại

Có thể hình dung nguồn có A, B, C và Enumeration đã trả A:

```text
Nguồn dữ liệu:       [A, B, C]
Đã đọc:              A
Phần còn lại:        B, C
Vị trí đọc tiếp:        ↑
```

Enumeration không có phương thức reset, get(index), size hoặc remove trong giao diện của nó. Nếu muốn duyệt lại từ đầu, cần một lượt duyệt mới do nguồn cung cấp, hoặc cần lưu các phần tử vào một collection riêng.

Việc duyệt hết Enumeration không đồng nghĩa xóa các phần tử khỏi Vector hay collection nguồn. **Trạng thái con trỏ duyệt** khác với **nội dung nguồn dữ liệu**.

### 2.3. Thứ tự và null phụ thuộc dữ liệu nguồn

Enumeration không tự sắp xếp hoặc loại phần tử trùng. Nó trả phần tử theo hợp đồng của API cung cấp nó. Với Vector trong các ví dụ, đó là thứ tự phần tử của Vector.

Một phần tử null có thể xuất hiện nếu nguồn cho phép. Vì vậy, mẫu dừng bằng `nextElement() == null` không đúng tổng quát: nó vừa làm tiến lượt duyệt, vừa có thể dừng ở một dữ liệu null hợp lệ. Dùng hasMoreElements để kiểm tra còn phần tử.

Tài liệu [Enumeration — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Enumeration.html) là nguồn đối chiếu các quy tắc này.

## 3. Chương trình 1: một lượt duyệt cơ bản

Lưu chương trình đầy đủ vào `EnumerationBasicsDemo.java`. Vector được dùng ở đây để minh họa một API thực sự trả Enumeration.

```java
import java.util.Arrays;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Vector;

public class EnumerationBasicsDemo {
    public static void main(String[] args) {
        Vector<String> source = new Vector<>(Arrays.asList("alpha", "beta", "gamma"));
        Enumeration<String> elements = source.elements();
        int count = 0;
        while (elements.hasMoreElements()) {
            String value = elements.nextElement();
            System.out.println("value=" + value);
            count++;
        }
        System.out.println("count=" + count);
        System.out.println("hasMore=" + elements.hasMoreElements());
        System.out.println("sourceSize=" + source.size());
        try {
            elements.nextElement();
            throw new AssertionError("Expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
            System.out.println("exhaustedNext=NoSuchElementException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 EnumerationBasicsDemo.java
java EnumerationBasicsDemo
```

Stdout:

```text
value=alpha
value=beta
value=gamma
count=3
hasMore=false
sourceSize=3
exhaustedNext=NoSuchElementException
```

### 3.1. Đọc hết không làm nguồn rỗng

Ba vòng lặp lấy alpha, beta, gamma. count bằng 3 và hasMore sau đó là false, nhưng sourceSize vẫn là 3. Không có thao tác xóa nào được gọi.

nextElement sau khi hết ném `NoSuchElementException`, đúng hợp đồng. Không được viết logic dựa trên kỳ vọng nó trả một giá trị cuối đặc biệt.

Nếu cần đọc lại chính Vector, có thể gọi source.elements() để nhận một Enumeration mới của dữ liệu nguồn khi đó. Lấy một biến khác trỏ vào Enumeration cũ không làm vị trí đọc quay lại.

## 4. Enumeration và Iterator khác nhau như thế nào?

| Enumeration | Iterator | Nhận xét |
|---|---|---|
| `hasMoreElements()` | `hasNext()` | Kiểm tra còn phần tử |
| `nextElement()` | `next()` | Lấy và tiến tới phần tử tiếp theo |
| Không có remove | `remove()` là thao tác tùy chọn | Không phải mọi Iterator đều xóa được |
| Có `asIterator()` từ Java 9 | Có `forEachRemaining()` từ Java 8 | Giúp kết nối các API dùng giao diện khác nhau |

Enumeration và Iterator đều không tự trở thành Iterable. Chuyển sang Iterator cho phép dùng hasNext/next hoặc forEachRemaining, nhưng không vì vậy mà một biến Iterator dùng trực tiếp được ở mọi vòng for-each yêu cầu Iterable.

Nếu viết API mới cần cung cấp khả năng duyệt, thường nên chọn các giao diện phù hợp của Collections Framework thay vì tạo thêm một API Enumeration không cần thiết. Còn khi nhận Enumeration từ API đã có, có thể thích nghi tại ranh giới dùng nó.

## 5. Chuyển sang Iterator bằng asIterator

### 5.1. Duyệt phần còn lại, không tạo bản sao toàn bộ

Với Java 9 trở lên, gọi `elements.asIterator()` để nhận Iterator duyệt các phần tử còn lại mà Enumeration bao phủ. Java 17 hỗ trợ phương thức này.

Phần triển khai mặc định ánh xạ hasNext sang hasMoreElements, next sang nextElement. Nó không đọc trước toàn bộ dữ liệu thành một List mới. Nếu trước đó đã lấy A thì Iterator tiếp tục từ B.

### 5.2. Không dùng xen kẽ Enumeration gốc sau khi thích nghi

API quy định kết quả duyệt không xác định nếu tiếp tục gọi phương thức trên Enumeration sau lời gọi asIterator. Vì vậy, sau khi tạo adapter, phần mã sử dụng phải tiếp tục qua Iterator, không xen `elements.nextElement()` hay trực tiếp kiểm tra Enumeration gốc.

Đừng xem Enumeration và Iterator kết quả là hai con trỏ độc lập. Cũng đừng tạo nhiều adapter từ cùng Enumeration rồi giả định mỗi adapter có thể đọc một bản dữ liệu riêng từ đầu.

Trong chương trình tiếp theo, có một lần đọc Enumeration trước asIterator; sau thời điểm thích nghi, mọi thao tác duyệt đều qua Iterator. Việc xem nội dung Vector nguồn không phải lời gọi lại vào Enumeration.

### 5.3. Iterator kết quả không tự có quyền xóa

Phần triển khai mặc định của asIterator tạo Iterator có remove ném `UnsupportedOperationException`. Enumeration không cung cấp một thao tác xóa để adapter có thể tự chuyển tiếp.

Điều đó vẫn đúng với adapter mặc định khi dữ liệu nguồn là một Vector có thể sửa. Khả năng sửa của collection nguồn không đồng nghĩa mọi lượt duyệt bọc quanh nó đều cho xóa.

`Iterator.remove` nói chung là thao tác tùy chọn. Muốn xóa trong quá trình duyệt, cần một Iterator mà API nguồn thật sự hỗ trợ thao tác đó và phải tuân theo vòng đời next/remove. Không ép quyền xóa vào một adapter bằng cách đoán cách sửa collection phía sau.

## 6. Chương trình 2: thích nghi phần còn lại

Lưu vào `EnumerationIteratorDemo.java`:

```java
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Vector;

public class EnumerationIteratorDemo {
    public static void main(String[] args) {
        Vector<String> source = new Vector<>(Arrays.asList("A", "B", "C"));
        Enumeration<String> elements = source.elements();
        System.out.println("beforeAdapt=" + elements.nextElement());
        Iterator<String> iterator = elements.asIterator();
        System.out.println("iteratorHasNext=" + iterator.hasNext());
        System.out.println("iteratorNext=" + iterator.next());
        try {
            iterator.remove();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println("remove=UnsupportedOperationException");
        }
        iterator.forEachRemaining(value -> System.out.println("remaining=" + value));
        System.out.println("iteratorHasNextAfter=" + iterator.hasNext());
        System.out.println("sourceAfterTraversal=" + source);
        try {
            iterator.next();
            throw new AssertionError("Expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
            System.out.println("exhaustedIteratorNext=NoSuchElementException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 EnumerationIteratorDemo.java
java EnumerationIteratorDemo
```

Stdout:

```text
beforeAdapt=A
iteratorHasNext=true
iteratorNext=B
remove=UnsupportedOperationException
remaining=C
iteratorHasNextAfter=false
sourceAfterTraversal=[A, B, C]
exhaustedIteratorNext=NoSuchElementException
```

### 6.1. A đã được đọc trước khi thích nghi

`beforeAdapt=A` đến từ nextElement gọi trước asIterator. Iterator bắt đầu với B, nên iteratorNext in B, rồi forEachRemaining xử lý C. Không có việc A được phát lại.

hasNext sau khi duyệt là false. Gọi next trên Iterator hết cũng ném `NoSuchElementException`. Source vẫn `[A, B, C]` vì duyệt không loại dữ liệu.

### 6.2. Phương thức remove bị từ chối

Lời gọi remove đặt sau một next hợp lệ nhưng vẫn ném `UnsupportedOperationException`, vì adapter mặc định không hỗ trợ xóa. Đây khác với trường hợp một Iterator có hỗ trợ remove nhưng người dùng gọi sai vòng đời.

forEachRemaining chỉ là cách xử lý phần còn lại tiện hơn một vòng while. Nó không tự tạo một bộ lưu dữ liệu và không tự cung cấp khả năng duyệt lại sau đó.

## 7. Chuyển sang List bằng Collections.list

### 7.1. Thu hết phần còn lại vào một ArrayList mới

`Collections.list(elements)` trả một `ArrayList<E>` chứa các phần tử Enumeration còn trả được, theo đúng thứ tự chúng được lấy ra. Phương thức cần duyệt nguồn đến hết để hoàn thành danh sách.

Đoạn minh họa bên trong một phương thức đã có Enumeration<String>:

```java
ArrayList<String> items = Collections.list(elements);
```

Có thể khai báo kết quả bằng `List<String>` nếu chỉ cần giao diện List. Kiểu đối tượng được API trả về vẫn là ArrayList.

Danh sách kết quả không tự có các phần tử đã đọc trước đó. Với nguồn A,B,C và A đã được lấy, list chỉ thu B,C. Nếu Enumeration đã hết, kết quả là ArrayList rỗng.

Hợp đồng tại [Collections.list — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html) bảo đảm thứ tự phần tử theo Enumeration. Nó không tự sắp thứ tự chữ cái, không khử trùng và không quay lại nguồn để đọc một lượt mới.

### 7.2. Trạng thái Enumeration bị tiêu thụ, dữ liệu nguồn không bị xóa

Collections.list phải gọi các thao tác duyệt để thu phần tử. Vì vậy Enumeration đã đi đến cuối sau khi phương thức hoàn thành bình thường trên một nguồn hữu hạn.

Điều đó không tương đương remove khỏi collection nguồn. Nếu Enumeration đọc một Vector, các phần tử của Vector vẫn có thể còn nguyên; chỉ lượt duyệt đã bị tiêu thụ.

Lấy list hai lần liên tiếp từ cùng Enumeration không tạo hai bản giống nhau: lần đầu lấy phần còn lại, lần sau thường chỉ có phần rỗng vì lượt duyệt đã hết. Nếu cần nhiều nơi cùng dùng dữ liệu đã thu, giữ List kết quả và tạo các Iterator của List khi cần.

### 7.3. Kết quả có thể sửa cấu trúc, nhưng không sao chép sâu phần tử

ArrayList mới có thể add, remove hoặc set theo hợp đồng của nó. Các thay đổi cấu trúc hay thay tham chiếu phần tử trong list mới không tự sửa Vector nguồn.

Tuy nhiên, phương thức sao chép các tham chiếu phần tử; không nhân bản sâu từng đối tượng. Nếu phần tử là một StringBuilder có thể thay đổi, source và list mới có thể cùng nhìn thấy thay đổi trên chính đối tượng đó.

Ba câu hỏi cần tách biệt:

1. Danh sách chứa có phải cấu trúc mới không? Có.
2. Các phần tử bên trong có được nhân bản sâu không? Không.
3. Việc thu list có khôi phục Enumeration từ đầu không? Không.

### 7.4. Null và phần tử trùng được giữ

Nếu Enumeration trả B,B,C, danh sách có hai vị trí mang B. Nếu nguồn trả một phần tử null hợp lệ, ArrayList kết quả giữ null ở vị trí tương ứng. Chuyển sang List không tự áp dụng chính sách loại null hoặc duy nhất hóa dữ liệu.

Phải phân biệt phần tử null với bản thân biến Enumeration là null. Một Enumeration hợp lệ có thể trả null; còn truyền một tham chiếu Enumeration null không phải cách yêu cầu một danh sách rỗng.

## 8. Chương trình 3: phần còn lại, bản sao nông và null

Lưu chương trình đầy đủ vào `EnumerationListDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;

public class EnumerationListDemo {
    public static void main(String[] args) {
        Vector<String> source = new Vector<>(Arrays.asList("A", "B", "B", "C"));
        Enumeration<String> elements = source.elements();
        System.out.println("consumedBeforeCopy=" + elements.nextElement());
        ArrayList<String> remaining = Collections.list(elements);
        System.out.println("remainingCopy=" + remaining);
        System.out.println("sourceHasMore=" + elements.hasMoreElements());
        remaining.set(0, "B2");
        remaining.add("D");
        System.out.println("modifiedCopy=" + remaining);
        System.out.println("unchangedSource=" + source);
        System.out.println("freshFullCopy=" + Collections.list(source.elements()));
        System.out.println("copyOfExhausted=" + Collections.list(elements));

        Vector<String> nullable = new Vector<>(Arrays.asList("x", null, "y"));
        ArrayList<String> nullCopy = Collections.list(nullable.elements());
        System.out.println("nullCopy=" + nullCopy);
        System.out.println("nullCopySize=" + nullCopy.size());

        Vector<StringBuilder> builders = new Vector<>();
        builders.add(new StringBuilder("shared"));
        ArrayList<StringBuilder> shallow = Collections.list(builders.elements());
        shallow.get(0).append("!");
        System.out.println("mutableSourceValue=" + builders.get(0));
        System.out.println("sameElementReference=" + (builders.get(0) == shallow.get(0)));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 EnumerationListDemo.java
java EnumerationListDemo
```

Stdout:

```text
consumedBeforeCopy=A
remainingCopy=[B, B, C]
sourceHasMore=false
modifiedCopy=[B2, B, C, D]
unchangedSource=[A, B, B, C]
freshFullCopy=[A, B, B, C]
copyOfExhausted=[]
nullCopy=[x, null, y]
nullCopySize=3
mutableSourceValue=shared!
sameElementReference=true
```

### 8.1. Chỉ B,B,C được thu vào bản sao đầu

`consumedBeforeCopy=A` lấy phần tử A trước lời gọi list. Vì vậy `remainingCopy=[B, B, C]`, giữ cả hai lần B. `sourceHasMore=false` xác nhận Enumeration đã được duyệt hết.

Đổi phần tử đầu của remaining thành B2 rồi thêm D tạo `[B2, B, C, D]`. Vector nguồn vẫn `[A, B, B, C]`; không có xóa hoặc set nào được chuyển ngược vào nó.

### 8.2. Lượt duyệt mới khác với dùng lại lượt đã hết

`Collections.list(source.elements())` nhận Enumeration mới từ Vector đang giữ dữ liệu, nên `freshFullCopy` có đủ A,B,B,C. `Collections.list(elements)` trên Enumeration cũ đã hết cho `copyOfExhausted=[]`.

Điểm khác biệt nằm ở đối tượng cung cấp lượt duyệt, không phải việc đổi tên biến kết quả. Nếu nguồn đã thay đổi trước khi tạo Enumeration mới, lượt mới đọc dữ liệu nguồn theo hợp đồng tại thời điểm đó; không mặc nhiên là một bản lịch sử của lần trước.

### 8.3. Phần tử null không kết thúc lượt đọc

`nullCopy=[x, null, y]` và size bằng 3 cho thấy null ở giữa không khiến quá trình thu dừng trước y. Đây là lý do dùng hasMoreElements làm điều kiện còn dữ liệu.

### 8.4. Cấu trúc mới vẫn chia sẻ một đối tượng mutable

Phần cuối tạo một Vector có StringBuilder mang nội dung shared. Collections.list tạo ArrayList mới, nhưng phần tử đầu vẫn là cùng StringBuilder. Append dấu chấm than qua list làm nguồn đọc được `shared!`.

`sameElementReference=true` giải thích kết quả đó. Một danh sách mới không đồng nghĩa toàn bộ cây đối tượng bên trong đã được sao chép. Nếu dữ liệu cần độc lập sâu, cách sao chép phải được thiết kế theo kiểu phần tử.

## 9. Chọn Iterator hay List theo công việc tiếp theo

| Nhu cầu | Cách xử lý | Ảnh hưởng chính |
|---|---|---|
| Xử lý tuần tự đúng phần còn lại một lần | Duyệt Enumeration trực tiếp hoặc thích nghi Iterator | Không bắt buộc giữ toàn bộ phần tử |
| Đưa vào API nhận Iterator | `asIterator()` trên Java 9+ | Thích nghi lượt duyệt còn lại |
| Cần size, truy cập theo vị trí hoặc duyệt nhiều lần | `Collections.list(...)` | Thu hết phần còn lại thành ArrayList mới |
| Cần vừa giữ dữ liệu vừa có Iterator | Thu List trước, rồi lấy Iterator từ List | Có dữ liệu chứa để tạo các lượt duyệt riêng |
| API cũ yêu cầu Enumeration nhưng đầu vào là Collection | `Collections.enumeration(collection)` | Cầu nối theo chiều ngược |

Đừng thu một List lớn chỉ để gọi forEach một lần nếu không có nhu cầu giữ dữ liệu. Ngược lại, đừng truyền cùng một lượt Enumeration đi nhiều nơi rồi kỳ vọng mỗi nơi nhận toàn bộ dữ liệu từ đầu.

Trong trường hợp vừa cần lưu vừa cần Iterator, đoạn minh họa là:

```java
// Minh họa, source là Enumeration<String> chưa được thích nghi.
List<String> items = Collections.list(source);
Iterator<String> firstPass = items.iterator();
Iterator<String> secondPass = items.iterator();
```

Hai Iterator của ArrayList có vị trí duyệt riêng trên cùng danh sách. Chúng không đến từ hai lần gọi asIterator trên Enumeration ban đầu. Danh sách vẫn có thể thay đổi nên các quy tắc sửa nguồn khi duyệt của nó vẫn phải được tôn trọng.

### 9.1. Chi phí thu dữ liệu

Nếu còn n phần tử, Collections.list phải lấy n phần tử và dành cấu trúc chứa các tham chiếu tương ứng. Ngoài công việc tuyến tính để xây danh sách, còn có chi phí của chính nguồn mỗi lần cung cấp phần tử.

Enumeration không công bố size trong giao diện và có thể lấy dữ liệu theo cơ chế riêng của API nguồn. Khi nguồn lớn, chỉ chọn thu toàn bộ nếu công việc cần nó. Với một nguồn không kết thúc, thao tác cố thu tất cả cũng không thể hoàn thành theo cách của một danh sách hữu hạn.

Adapter Iterator không tự nạp trước toàn bộ n phần tử. Nó phù hợp cho xử lý từng phần tử nhưng không biến nguồn thành nơi có thể tua lại.

### 9.2. Không ép kiểu để giả làm chuyển đổi

Một đối tượng Enumeration không tự triển khai Iterator hoặc List chỉ vì các kiểu này đều liên quan đến duyệt. Ép kiểu chỉ kiểm tra/khẳng định loại đối tượng, không tạo adapter và không thu dữ liệu. Nếu đối tượng không thực hiện giao diện đích, việc ép kiểu có thể gây ClassCastException.

Dùng asIterator hoặc Collections.list thể hiện đúng hành động cần thực hiện và cho phép người đọc đánh giá ảnh hưởng lên phần còn lại của lượt duyệt.

## 10. Chiều ngược: Collection sang Enumeration

Khi một thư viện cũ yêu cầu Enumeration, `Collections.enumeration(collection)` tạo cầu nối từ một Collection có sẵn. Ví dụ một List đang lưu tên có thể được đưa vào API cũ bằng một Enumeration tương ứng.

Đây là một lựa chọn tại ranh giới giữa hai API. Không cần đổi toàn bộ mô hình dữ liệu của chương trình về Vector chỉ để có kiểu trả về Enumeration.

Phương thức enumeration không có hợp đồng tạo một bản sao sâu hay đóng băng dữ liệu nguồn. Nếu cần một cấu trúc chứa độc lập trước khi đưa cho mã khác, hãy tạo bản sao collection một cách tường minh, rồi mới tạo Enumeration từ bản sao đó. Thứ tự và quy tắc thay đổi nguồn trong khi duyệt vẫn cần đọc theo collection/API thực tế.

Với List, lượt duyệt theo thứ tự danh sách. Cầu nối không tự sắp xếp, loại trùng hay bỏ null. Một Iterator có sẵn không phải là đối số Collection: cần xem API đầu vào thật sự đang có dữ liệu chứa hay chỉ còn một lượt đọc.

## 11. Adapter thủ công khi cần tương thích Java 8

### 11.1. Chuyển tiếp hai thao tác đọc

`Enumeration.asIterator()` được thêm từ Java 9. Nếu mã phải biên dịch cho Java 8, có thể tạo một Iterator chuyển tiếp hasNext sang hasMoreElements và next sang nextElement.

Adapter này chỉ bọc lượt đọc; nó không tạo một List. Cần dùng một đường duyệt nhất quán qua adapter sau khi đã chuyển quyền sử dụng, tránh các nơi khác cùng tiêu thụ Enumeration gốc làm mất phần tử mà bên đọc chưa biết.

Chương trình tiếp theo kiểm tra tham chiếu nguồn bằng Objects.requireNonNull ngay khi tạo adapter. Khi nguồn null, lỗi được phát hiện tại ranh giới chuyển đổi. Một nguồn hợp lệ nhưng rỗng vẫn là tình huống bình thường.

### 11.2. Nhận nguồn chỉ cung cấp phần tử

Khai báo `Enumeration<? extends T>` có nghĩa nguồn có thể cung cấp một kiểu con của T. Enumeration chỉ trả dữ liệu theo giao diện của nó; adapter không đưa thêm phần tử vào nguồn.

Vì Integer là Number, phương thức trong ví dụ có thể tạo `Iterator<Number>` từ `Enumeration<Integer>`. Các đối tượng trả về vẫn là Integer ban đầu. Generic không nhân bản phần tử hoặc chuyển Integer thành một loại số khác.

Không cần dùng kiểu raw hay ép kiểu không kiểm tra để viết cầu nối này. Nếu API cũ trả một Enumeration raw, generic ở phía nhận không tự kiểm chứng được mọi đối tượng bên trong; cần kiểm tra và xử lý kiểu thực tế theo hợp đồng của API đó.

### 11.3. Không cài remove đồng nghĩa dùng mặc định của Iterator

Lớp vô danh chỉ cài hasNext và next. Nó kế thừa remove mặc định của Iterator, nên thao tác xóa ném UnsupportedOperationException. Đừng viết remove rỗng để giả vờ thành công: mã gọi sẽ hiểu nhầm dữ liệu đã được xóa.

Nếu thực sự cần xóa từ collection, hãy dùng cơ chế được collection đó hỗ trợ. Không thể suy ra thao tác xóa đúng chỉ từ giao diện Enumeration.

## 12. Chương trình 4: cầu nối, generic và nguồn rỗng

Lưu chương trình đầy đủ vào `EnumerationCompatibilityDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class EnumerationCompatibilityDemo {
    static <T> Iterator<T> adapt(Enumeration<? extends T> source) {
        Objects.requireNonNull(source, "source");
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return source.hasMoreElements();
            }

            @Override
            public T next() {
                return source.nextElement();
            }
        };
    }

    public static void main(String[] args) {
        List<String> modern = new ArrayList<>(Arrays.asList("red", "blue"));
        Enumeration<String> forLegacy = Collections.enumeration(modern);
        ArrayList<String> copyBack = Collections.list(forLegacy);
        modern.clear();
        System.out.println("copyBack=" + copyBack);
        System.out.println("modernSizeAfterClear=" + modern.size());

        Enumeration<Integer> integers =
                Collections.enumeration(Arrays.asList(10, 20));
        Iterator<Number> numbers = adapt(integers);
        System.out.println("number=" + numbers.next());
        try {
            numbers.remove();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println("compatRemove=UnsupportedOperationException");
        }
        numbers.forEachRemaining(number -> System.out.println("number=" + number));
        System.out.println("hasNextAfter=" + numbers.hasNext());
        ArrayList<String> emptyCopy =
                Collections.list(Collections.<String>emptyEnumeration());
        emptyCopy.add("later");
        System.out.println("mutableEmptyCopy=" + emptyCopy);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 EnumerationCompatibilityDemo.java
java EnumerationCompatibilityDemo
```

Stdout:

```text
copyBack=[red, blue]
modernSizeAfterClear=0
number=10
compatRemove=UnsupportedOperationException
number=20
hasNextAfter=false
mutableEmptyCopy=[later]
```

### 12.1. Bản thu List tồn tại độc lập với cấu trúc nguồn

modern ban đầu là List chứa red và blue. Collections.enumeration tạo lượt đọc để Collections.list thu thành một ArrayList khác. Sau khi đã thu xong, clear modern làm kích thước nguồn thành 0, nhưng copyBack vẫn chứa red và blue.

Ví dụ không sửa nguồn khi Enumeration còn đang được sử dụng. Nó minh họa tính độc lập của cấu trúc List đã thu, không chứng minh rằng mọi Enumeration là ảnh chụp cố định của nguồn.

### 12.2. Adapter chỉ thay giao diện đọc

Enumeration<Integer> cho hai số 10, 20. Iterator<Number> nhận được từ adapt lấy số 10 bằng next; remove bị từ chối; forEachRemaining tiếp tục với 20. hasNext sau đó là false.

Lời gọi remove đặt ngay sau next và trước forEachRemaining để thử đúng trường hợp adapter không hỗ trợ xóa. Không dùng ví dụ này làm mẫu gọi remove sau khi forEachRemaining đã chạy: hợp đồng Iterator không quy định hành vi remove trong tình huống đó.

### 12.3. Danh sách rỗng thu được vẫn có thể thêm phần tử

Collections.emptyEnumeration cung cấp một Enumeration rỗng. Collections.list từ nó trả một ArrayList rỗng có thể sửa. Thêm later vào danh sách cho kết quả `[later]`; thao tác không làm Enumeration có thêm dữ liệu.

Nguồn rỗng, nguồn null và nguồn đã đọc hết là ba điều cần phân biệt. Hai trường hợp có Enumeration hợp lệ đều có thể tạo List rỗng; tham chiếu null cần được xử lý như lỗi đầu vào hoặc theo chính sách tường minh của chương trình.

Chương trình này được biên dịch và chạy với mức API Java 17 như lệnh đã ghi. Cùng mã nguồn còn được kiểm tra biên dịch thành công với `--release 8`; nó không dùng asIterator.

## 13. Khi dữ liệu nguồn thay đổi trong lúc duyệt

### 13.1. Enumeration không bảo đảm chung về fail-fast

Không suy ra mọi Enumeration sẽ ném ConcurrentModificationException nếu nguồn đổi. Giao diện Enumeration không cung cấp một chính sách phát hiện sửa đổi áp dụng cho mọi triển khai.

Đặc biệt, [Vector — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Vector.html) nêu rằng Enumeration từ Vector không có cơ chế fail-fast. Nếu Vector bị thay đổi cấu trúc sau khi Enumeration được tạo, kết quả liệt kê không được xác định theo hợp đồng đó.

Vì vậy, không lấy một lần chạy tình cờ vẫn in được vài phần tử làm bằng chứng rằng vừa add/remove Vector vừa đọc Enumeration là an toàn. Các ví dụ đầy đủ ở trên giữ cấu trúc nguồn ổn định trong mỗi lượt duyệt đang hoạt động.

### 13.2. Bọc sang Iterator không tạo thêm bảo đảm đồng thời

asIterator không tự thay đổi chính sách của nguồn thành một chính sách an toàn cho nhiều luồng. Một Iterator được tạo từ Enumeration cũng không tự có cơ chế phát hiện mọi sửa đổi như Iterator riêng do một collection khác cung cấp.

Tương tự, Collections.list chỉ thu dữ liệu bằng các thao tác đọc. Nếu cần một bản dữ liệu nhất quán khi nhiều nơi có thể sửa nguồn, phải phối hợp việc đọc và sửa theo hợp đồng đồng bộ của nguồn. Gọi một hàm chuyển đổi không thay thế việc phối hợp đó.

Việc Vector đồng bộ một số thao tác riêng lẻ không có nghĩa toàn bộ chuỗi kiểm tra còn phần tử rồi lấy phần tử trở thành một giao dịch nguyên khối. Không dùng sự hiện diện của Vector làm lý do bỏ qua thiết kế truy cập đồng thời.

### 13.3. Bản sao List không phải cam kết bất biến

Sau khi thu xong trong điều kiện nguồn hợp lệ, List mới có cấu trúc chứa riêng. Nó vẫn là ArrayList có thể sửa và vẫn có thể chia sẻ các đối tượng phần tử có thể thay đổi.

Nếu yêu cầu là “đọc lại nhiều lần cùng một tập tham chiếu”, bản sao List thường phù hợp. Nếu yêu cầu là “mọi trạng thái của từng đối tượng phải giữ nguyên”, còn cần một thiết kế bất biến hoặc sao chép phần tử riêng; Collections.list không giải quyết yêu cầu đó.

## 14. Những lỗi dễ gặp

| Cách hiểu hoặc cách làm | Vấn đề | Cách xử lý |
|---|---|---|
| Xem Enumeration như nơi lưu dữ liệu có size/index | Giao diện chỉ cung cấp lượt đọc tuần tự | Thu List nếu cần thao tác theo vị trí |
| Dùng null làm dấu kết thúc | Null có thể là một phần tử hợp lệ | Kiểm tra hasMoreElements |
| Gọi nextElement hai lần trong một vòng cho một phần tử | Hai lần gọi tiêu thụ hai phần tử | Lấy một lần, giữ trong biến rồi dùng |
| Mong asIterator đọc lại phần đã lấy | Adapter tiếp tục phần còn lại | Tạo lượt mới từ nguồn hoặc lưu dữ liệu trước |
| Dùng trực tiếp Enumeration gốc sau asIterator | API không quy định kết quả duyệt của cách dùng đó | Tiếp tục duyệt qua Iterator |
| Ép Enumeration thành List | Ép kiểu không thu dữ liệu | Dùng Collections.list |
| Gọi Collections.list hai lần trên cùng lượt đã hết | Lần sau không có dữ liệu cũ để đọc lại | Giữ List đầu tiên hoặc xin lượt mới |
| Cho rằng adapter mặc định hỗ trợ remove vì nguồn sửa được | Enumeration không có thao tác xóa để chuyển tiếp | Dùng API xóa được hỗ trợ thực sự |
| Xem ArrayList mới như bản sao sâu | Phần tử có thể còn cùng tham chiếu | Thiết kế sao chép theo loại phần tử nếu cần |
| Mong đổi sang Iterator sẽ phát hiện mọi sửa đổi nguồn | Wrapper không thêm hợp đồng đó | Tuân theo chính sách của nguồn |
| Chọn thu toàn bộ mà không xét kích thước nguồn | Giữ thêm mọi tham chiếu của phần còn lại | Chọn duyệt tuần tự nếu công việc chỉ cần một lượt |
| Gọi asIterator trong mã phải biên dịch Java 8 | Phương thức có từ Java 9 | Dùng adapter tương thích hoặc nâng mức Java |

## 15. Tự kiểm tra

1. **Enumeration khác enum ở điểm nào?** Enumeration là giao diện duyệt tuần tự các phần tử; enum là cơ chế ngôn ngữ khai báo kiểu tập hằng.
2. **Nguồn có A,B,C, đã gọi nextElement lấy A. Collections.list nhận được gì?** B,C theo thứ tự đó, nếu nguồn giữ ổn định và lượt duyệt còn đúng hai phần tử này.
3. **Thu List có làm Vector nguồn rỗng không?** Không. Nó tiêu thụ lượt duyệt, không gọi xóa dữ liệu nguồn.
4. **Sau asIterator, có nên tiếp tục gọi hasMoreElements trên Enumeration gốc không?** Không. API Enumeration không quy định kết quả duyệt khi tiếp tục gọi trực tiếp phương thức trên Enumeration sau khi thích nghi.
5. **asIterator có cung cấp hai lượt độc lập nếu giữ cả hai biến không?** Không có bảo đảm đó. Dùng Iterator để tiếp tục phần còn lại; nếu cần các lượt riêng, thu vào List rồi lấy các Iterator của List.
6. **Collections.list tạo loại đối tượng gì?** ArrayList mới, có thể khai báo biến nhận là List; phần tử được giữ theo tham chiếu chứ không được sao chép sâu.
7. **Một nguồn trả x,null,y có khiến quá trình thu dừng tại null không?** Không. Khi null là phần tử hợp lệ, cả ba vị trí được đưa vào ArrayList.
8. **Vì sao adapter thủ công có thể trả Iterator<Number> từ Enumeration<Integer>?** Nguồn chỉ cần cung cấp các đối tượng thuộc Number; Integer thỏa mãn quan hệ đó. Kiểu tham chiếu rộng hơn không đổi bản chất đối tượng.
9. **Có được dựa vào ConcurrentModificationException để phát hiện mọi lỗi sửa Vector khi đọc Enumeration không?** Không. Enumeration từ Vector không fail-fast; sửa cấu trúc trong tình huống API cảnh báo có thể cho kết quả không được xác định.
10. **Java 8 có gọi Enumeration.asIterator được không?** Không. asIterator có từ Java 9; có thể dùng adapter thủ công khi buộc tương thích Java 8.

## 16. Bài tập áp dụng

### 16.1. Giữ thứ tự và phần tử trùng của phần còn lại

Tạo Vector<Integer> chứa 4,4,8; lấy phần tử đầu bằng nextElement rồi thu phần còn lại bằng Collections.list.

Kết quả cần kiểm tra: phần đã đọc là 4, List kết quả là `[4, 8]`, Vector nguồn vẫn là `[4, 4, 8]`. Đổi phần tử đầu của List kết quả thành 40 chỉ làm List thành `[40, 8]`, không tự sửa Vector nguồn.

Sau đó thu lần nữa từ đúng Enumeration cũ. Kết quả phải rỗng. Giải thích được vì sao một Enumeration mới từ Vector vẫn có thể đọc đầy đủ.

### 16.2. Chọn một đường đọc sau khi thích nghi

Tạo Vector<String> chứa X,Y,Z; đọc X qua Enumeration rồi dùng asIterator. Chỉ dùng Iterator cho phần còn lại.

Kết quả cần kiểm tra: Iterator đọc Y và Z, hasNext sau đó false. Không thêm lời gọi trực tiếp vào Enumeration sau khi tạo adapter. Giải thích vì sao “đang giữ hai biến” không đồng nghĩa “có hai lượt độc lập”.

### 16.3. Chọn chuyển đổi theo yêu cầu của mã gọi

Có một phương thức nhận Enumeration<String> và chỉ cần in từng tên một lần. Một phương thức khác cần xem tên ở chỉ số 0 và duyệt dữ liệu hai lần.

Với phương thức thứ nhất, có thể duyệt trực tiếp hoặc thích nghi Iterator mà không cần thu toàn bộ vào List. Với phương thức thứ hai, thu List một lần từ phần còn lại, kiểm tra rỗng trước khi lấy chỉ số 0, rồi thực hiện các lượt duyệt từ List đó.

Nêu rõ điều kiện nguồn còn đủ dữ liệu mong muốn. Nếu Enumeration đã bị đọc trước, chuyển sang List không khôi phục phần đã mất khỏi lượt đọc.

## 17. Nguồn đối chiếu và phạm vi kiểm chứng

- [Enumeration — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Enumeration.html): hasMoreElements, nextElement, asIterator, mốc Java 9 và ràng buộc sử dụng Enumeration sau khi thích nghi.
- [Collections — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html): list trả ArrayList, thứ tự thu phần tử, enumeration và emptyEnumeration.
- [Iterator — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html): các phương thức duyệt, remove tùy chọn, forEachRemaining và giới hạn hành vi liên quan.
- [Vector — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Vector.html): nguồn Enumeration trong ví dụ và cảnh báo khi nguồn thay đổi cấu trúc.

Bốn chương trình đầy đủ đã được biên dịch bằng javac với `--release 17` và chạy trên JDK 24.0.2, cho đúng stdout được ghi trong bài. Chương trình adapter thủ công còn được biên dịch thành công với `--release 8`. Các đoạn được ghi là minh họa cần đặt trong ngữ cảnh có sẵn biến và import tương ứng; chúng không được trình bày như tệp chạy độc lập.


</details>

- [Quay lại đầu trang](#back-to-top)
