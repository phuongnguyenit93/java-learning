<a id="back-to-top"></a>

# Cài đặt tùy chỉnh

## Menu
- [Khi nào cần tự cài đặt collection](#custom-collections-001)
- [AbstractCollection, AbstractList, AbstractSequentialList và AbstractSet](#custom-collections-002)
- [AbstractQueue và AbstractMap](#custom-collections-003)
- [Các phương thức tối thiểu và hợp đồng thao tác tùy chọn](#custom-collections-004)
- [Viết Iterator, hỗ trợ remove và xử lý trạng thái con trỏ](#custom-collections-005)
- [modCount và việc phát hiện thay đổi cấu trúc khi phù hợp](#custom-collections-006)
- [Giữ nhất quán equals, hashCode, view và các phương thức mặc định](#custom-collections-007)
- [Kiểm thử theo hợp đồng thay vì phụ thuộc lớp cài đặt](#custom-collections-008)

## <a id="custom-collections-001">Khi nào cần tự cài đặt collection</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào cần tự cài đặt collection

## 1. Quyết định bắt đầu từ yêu cầu chưa được đáp ứng

Tự cài đặt collection nghĩa là chịu trách nhiệm cho một kiểu tập hợp dữ liệu có hợp đồng sử dụng rõ ràng: cách chứa hoặc biểu diễn phần tử, cách truy cập, duyệt, so sánh và thay đổi khi được phép.

Việc một bài toán có nhiều phần tử chưa đủ để cần một collection mới. Thường có ba lựa chọn khác nhau:

1. Dùng collection có sẵn và cấu hình hoặc kết hợp chúng.
2. Viết một lớp nghiệp vụ chứa collection bên trong.
3. Cài đặt một giao diện collection bằng cách biểu diễn dữ liệu riêng hoặc tạo một cách nhìn mới.

Một lớp `TagCatalog` chứa `LinkedHashSet` không đồng nghĩa đã viết lại một cấu trúc set. Một lớp kế thừa `AbstractList` nhưng tính phần tử từ công thức có thể là một cài đặt `List` mới, dù không có mảng chứa toàn bộ phần tử.

Cần cân nhắc lựa chọn thứ ba khi có một khoảng trống cụ thể:

- Cách biểu diễn dữ liệu hiện có không phù hợp về bộ nhớ hoặc chi phí thao tác.
- Cần tích hợp dữ liệu hoặc một view vào API collection chuẩn.
- Có ngữ nghĩa tập hợp hợp lệ mà các lựa chọn sẵn có chưa đáp ứng.
- Có yêu cầu đo được, có thể kiểm chứng, đủ giá trị để duy trì một cài đặt riêng.

Sau đó còn phải trả lời: có thực hiện đúng hợp đồng giao diện, kiểm tra được các đường sử dụng và chịu trách nhiệm bảo trì được không?

Mục tiêu không phải tránh mọi mã tự viết. Mục tiêu là chọn đúng phần cần tự sở hữu. Nếu thiếu chỉ một quy tắc nghiệp vụ, chưa nên mặc định nhận luôn trách nhiệm cho toàn bộ `List`, `Set` hoặc `Map`.

Bài dùng Java 17 trở lên. Bốn chương trình bên dưới độc lập, có tên tệp, lệnh biên dịch và stdout. Chúng minh họa các mức lựa chọn, một lỗi kế thừa có chủ ý và một trường hợp cài đặt collection có lý do rõ.

## 2. Kiểm tra công cụ có sẵn trước

### 2.1. Nhu cầu thường có một giải pháp gần hơn

| Yêu cầu thực tế | Điều nên kiểm tra trước khi viết collection mới |
|---|---|
| Chỉ cho bên nhận đọc | View không cho sửa hoặc bản sao không cho sửa |
| Cần một danh sách kết quả cố định sau khi chụp | Factory sao chép phù hợp và chính sách dữ liệu con |
| Cần một API hàng đợi theo LIFO từ deque | Bộ chuyển đổi giao diện có sẵn |
| Cần thứ tự duyệt theo lần thêm | Cài đặt có thứ tự duyệt phù hợp |
| Tập phần tử chỉ thuộc một kiểu enum | `EnumSet` |
| Tập vị trí số nguyên không âm có thể biểu diễn bằng bit | `BitSet`, nếu API bit phù hợp |
| Cần ràng buộc nghiệp vụ và tên thao tác riêng | Lớp nghiệp vụ bọc collection |

`EnumSet` là set chuyên cho các hằng thuộc một kiểu enum. `BitSet` là vector bit đánh chỉ số bằng số nguyên không âm; nó không trực tiếp triển khai `Set<Integer>`. Cần so cả khả năng biểu diễn lẫn giao diện mà bên gọi thật sự cần. Xem [EnumSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/EnumSet.html) và [BitSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/BitSet.html).

Một cấu trúc sẵn có đáp ứng đúng yêu cầu giúp giảm lượng hợp đồng phải tự quản lý. Đó là lợi ích về khả năng kiểm chứng và bảo trì, không chỉ số dòng mã.

### 2.2. Chương trình với view, bản sao và bộ chuyển đổi

Tệp `ExistingToolsDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

public class ExistingToolsDemo {
    public static void main(String[] args) {
        var source = new ArrayList<>(List.of("alpha", "beta"));
        List<String> view = Collections.unmodifiableList(source);
        List<String> snapshot = List.copyOf(source);

        source.add("gamma");
        System.out.println("view=" + view);
        System.out.println("snapshot=" + snapshot);
        try {
            view.add("delta");
        } catch (UnsupportedOperationException ex) {
            System.out.println("viewWrite=" + ex.getClass().getSimpleName());
        }

        Queue<String> stackView =
                Collections.asLifoQueue(new ArrayDeque<String>());
        stackView.offer("A");
        stackView.offer("B");
        System.out.println("lifo=" + stackView.poll() + "," + stackView.poll());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ExistingToolsDemo.java
java ExistingToolsDemo
```

Stdout:

```text
view=[alpha, beta, gamma]
snapshot=[alpha, beta]
viewWrite=UnsupportedOperationException
lifo=B,A
```

View `unmodifiableList` không cho sửa thông qua view nhưng vẫn đọc từ nguồn. Khi nguồn thêm `"gamma"`, view phản ánh thay đổi đó.

`List.copyOf` tạo một danh sách không phản ánh các thay đổi cấu trúc sau này của nguồn. Trong ví dụ chỉ có chuỗi bất biến, nên dễ quan sát sự khác biệt. Sao chép cấu trúc không tự là sao chép sâu các đối tượng phần tử. Xem [unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)) và [List.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)).

Phần sau dùng `Collections.asLifoQueue` để nhìn một deque như hàng đợi LIFO. B được đưa sau và được lấy trước A. Nếu chỉ cần cách nhìn này, không cần tự cài một lớp queue với thuật toán lưu trữ mới.

Bài học từ ví dụ là phải phát biểu đúng yêu cầu. “Không cho sửa qua API trả về” và “kết quả không đổi theo nguồn” là hai điều khác nhau. Một collection tự viết không có đặc quyền bỏ qua khác biệt ấy.

### 2.3. Kết hợp cấu trúc chưa chắc là collection mới

Nếu cần vừa giữ thứ tự vừa tra cứu theo mã, có thể ứng dụng cần kết hợp các cấu trúc hoặc một lớp quản lý chỉ mục. Việc kết hợp ấy có chi phí nhất quán riêng: thêm, xóa hoặc thay dữ liệu phải cập nhật đúng các phần liên quan.

Tuy vậy, phần đó có thể nằm trong một lớp nghiệp vụ với vài thao tác rõ. Không bắt buộc phơi bày nó như một `List` hoặc `Map` mới.

Một API hẹp thường cho phép giữ ranh giới thay đổi rõ hơn. Bên gọi không được tùy ý thực hiện mọi phép cập nhật chỉ vì có tham chiếu đến một giao diện collection rộng.

## 3. Quy tắc nghiệp vụ thường phù hợp với lớp bọc

### 3.1. Khi tên thao tác nói nhiều hơn add và remove

Giả sử ứng dụng quản lý các thẻ văn bản:

- Bỏ khoảng trắng đầu/cuối.
- Chuẩn hóa chữ thường theo một quy tắc đã chọn.
- Không nhận giá trị trống.
- Không đăng ký trùng theo dạng đã chuẩn hóa.
- Trả danh sách theo thứ tự đăng ký.

Đó là một quy trình đăng ký với danh tính dữ liệu riêng. Bên gọi cần `register`, `isRegistered` và `snapshot`, chưa cần toàn bộ quyền thay thế từng vị trí, sửa qua iterator hay ghép sublist.

Tệp `DomainWrapperDemo.java`:

```java
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class DomainWrapperDemo {
    static final class TagCatalog {
        private final LinkedHashSet<String> tags = new LinkedHashSet<>();

        private static String normalize(String input) {
            String value = Objects.requireNonNull(input, "input")
                    .strip().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) {
                throw new IllegalArgumentException("tag must not be blank");
            }
            return value;
        }

        boolean register(String input) {
            return tags.add(normalize(input));
        }

        boolean isRegistered(String input) {
            return tags.contains(normalize(input));
        }

        List<String> snapshot() {
            return List.copyOf(tags);
        }
    }

    public static void main(String[] args) {
        var catalog = new TagCatalog();
        System.out.println("first=" + catalog.register(" Java "));
        System.out.println("duplicate=" + catalog.register("java"));
        System.out.println("second=" + catalog.register("SPRING"));
        List<String> before = catalog.snapshot();
        catalog.register("Gradle");

        System.out.println("before=" + before);
        System.out.println("now=" + catalog.snapshot());
        System.out.println("find=" + catalog.isRegistered(" JAVA "));
        try {
            catalog.register("   ");
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid=" + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 DomainWrapperDemo.java
java DomainWrapperDemo
```

Stdout:

```text
first=true
duplicate=false
second=true
before=[java, spring]
now=[java, spring, gradle]
find=true
invalid=IllegalArgumentException
```

Lớp dùng `LinkedHashSet` để giữ tính duy nhất và thứ tự đăng ký; thứ tự duyệt theo lần thêm là đặc tính sẵn có của lớp đó. Xem [LinkedHashSet Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html).

Hai đầu vào `" Java "` và `"java"` được quy về cùng một thẻ theo chính sách của `TagCatalog`. Kết quả `false` của lần đăng ký thứ hai là kết quả nghiệp vụ đã định nghĩa, không phải một thay đổi tự ý vào hợp đồng của một collection tổng quát.

Danh sách `before` được chụp trước khi đăng ký Gradle nên không thay đổi theo catalog. Collection nền không bị trả trực tiếp ra ngoài.

### 3.2. Lớp này chưa nhận trách nhiệm của Set

`TagCatalog` không công bố mình là `Set<String>`. Nó có thể định nghĩa cách chuẩn hóa đầu vào của riêng mình mà không khiến người nhận một `Set` chuẩn phải đoán lại ý nghĩa `contains` hoặc `equals`.

Nếu cố tạo một collection chỉ lưu chuỗi đã chuẩn hóa, cần xem lại toàn bộ hợp đồng. Chẳng hạn, nếu `add(" Java ")` trả bình thường nhưng `contains(" Java ")` lại là `false`, không thể coi đó là cài đặt `Collection.add` thông thường đã đáp ứng yêu cầu.

Một lớp bọc cũng giúp hạn chế đường sửa: mọi đăng ký đi qua một hàm chuẩn hóa duy nhất, thay vì phải theo dõi hàng loạt phương thức thay đổi dữ liệu của một lớp cha công khai.

### 3.3. Phạm vi ví dụ cần được giữ rõ

Ví dụ dùng trong một luồng, không cung cấp hợp đồng đồng thời. Dùng một cấu trúc có sẵn bên trong không tự làm mọi phương thức của lớp bọc an toàn khi nhiều luồng gọi.

`Locale.ROOT` là một lựa chọn chuẩn hóa trong ví dụ. Định nghĩa danh tính thẻ của sản phẩm thực có thể cần quy tắc khác; đó là việc phải phát biểu trước khi chọn collection nền.

Nếu sau này cần thêm “đổi tên thẻ” hoặc “hợp nhất thẻ”, có thể thêm thao tác nghiệp vụ với ranh giới riêng. Không cần mở toàn bộ các phép sửa của set cho mọi bên gọi.

## 4. Những lý do thực sự có thể dẫn đến cài đặt mới

### 4.1. Biểu diễn đặc biệt nhưng vẫn cần giao diện chuẩn

Một dãy số có thể được xác định bằng phần tử đầu, bước và số lượng. Nếu bên gọi cần một `List<Integer>` để truy cập theo vị trí, không nhất thiết phải tạo và giữ sẵn mọi số.

Đây là một lý do cụ thể cho cài đặt mới: giữ một mô tả ngắn, tính giá trị khi được hỏi, nhưng vẫn có số lượng hữu hạn và thứ tự vị trí rõ ràng.

Các dạng biểu diễn nén, vùng dữ liệu riêng hoặc view tính toán cũng có thể thuộc nhóm này. Mỗi trường hợp phải nêu được ngữ nghĩa của dữ liệu và chi phí những thao tác API có thể kích hoạt.

### 4.2. Mô hình thao tác chưa được cấu trúc sẵn có đáp ứng

Nếu dữ liệu có cách truy cập đặc biệt, một thuật toán hoặc chỉ mục riêng có thể cần thiết. Nhưng trước khi triển khai, phải mô tả tải:

- Thao tác nào xảy ra thường xuyên?
- Tỷ lệ đọc, thêm, xóa là bao nhiêu?
- Dữ liệu lớn đến đâu?
- Chi phí đang gặp nằm ở cấu trúc hay ở xử lý bên ngoài?
- Giải pháp mới cải thiện đại lượng nào và đánh đổi điều gì?

“Có vẻ nhanh hơn” chưa là yêu cầu đủ rõ. Nếu đo cho thấy chi phí nằm ở truy vấn mạng hoặc dữ liệu đầu vào, viết lại collection có thể không giải quyết vấn đề chính.

### 4.3. Tạo view để tương thích với thư viện khác

Một thư viện có thể yêu cầu `List` trong khi ứng dụng giữ dữ liệu theo một biểu diễn khác. Một view phù hợp có thể giúp tích hợp mà không sao chép toàn bộ.

Lợi ích đó đi kèm các câu hỏi: view phản ánh thay đổi nguồn hay cố định, có được sửa không, ai giữ nguồn sống, các phép duyệt và `size` có tốn I/O không?

Không dùng `List` chỉ để giấu một nguồn không có số lượng hay vòng đời phù hợp. Nếu dữ liệu cần đóng tài nguyên hoặc chỉ có thể đọc một lượt, một API mô tả đúng vòng đời có thể rõ hơn một collection trông như truy cập trong bộ nhớ.

### 4.4. Học cấu trúc dữ liệu là một mục tiêu khác

Tự viết để học thuật toán, kiểm tra bất biến hoặc hiểu iterator là mục tiêu hợp lệ. Cần ghi rõ đó là cài đặt phục vụ thí nghiệm, với khả năng và giới hạn đã biết.

Một cấu trúc chạy đúng vài ví dụ học tập chưa tự trở thành lựa chọn thay thế cho thư viện đã được sử dụng rộng rãi. Mức kiểm chứng, tài liệu và trách nhiệm bảo trì phải tương ứng với nơi định dùng.

Không đánh giá thành công chỉ bằng việc tên lớp đã giống một collection chuẩn hoặc đã biên dịch được.

## 5. Chi phí lớn nhất là hợp đồng mà bên gọi được quyền dựa vào

### 5.1. Giao diện không chỉ là danh sách chữ ký phương thức

Khi một đối tượng được đưa cho mã khác dưới kiểu `List`, bên nhận có quyền sử dụng các phép toán đúng hợp đồng `List`, không chỉ các phép mà tác giả đã dùng trong một bài kiểm tra.

Các nhóm cần xác định gồm:

| Nhóm | Câu hỏi phải trả lời |
|---|---|
| Tập phần tử | Có trùng, có `null`, có giới hạn miền dữ liệu không? |
| Thứ tự | Theo vị trí, theo comparator, theo lần thêm hay không hứa thứ tự? |
| Thay đổi | Hỗ trợ thêm, xóa, thay thế nào? Phép nào không được hỗ trợ? |
| Duyệt | Có đủ phần tử, đúng thứ tự và hành vi khi hết dữ liệu không? |
| So sánh | `equals` và `hashCode` có đúng với giao diện cụ thể không? |
| Cách nhìn liên quan | Iterator, sublist hoặc view map có quan hệ gì với nguồn? |
| Chi phí | `get`, `contains`, `size`, `toArray` và duyệt tốn bao nhiêu? |
| Đồng thời | Có hỗ trợ nhiều luồng không, và ở mức bảo đảm nào? |

Một lớp chỉ thêm/xóa đúng chưa đủ nếu iterator bỏ phần tử, mảng trả về sai nội dung hoặc `equals` không đối xứng với một `List` hợp lệ khác.

### 5.2. Có thể giới hạn thao tác, nhưng phải giới hạn đúng cách

Collection được phép có các thao tác tùy chọn không được hỗ trợ; khi cần báo, dùng `UnsupportedOperationException` theo hợp đồng. Một collection không cho sửa vẫn là cài đặt hợp lệ nếu các khả năng còn lại đúng.

Giới hạn phần tử cũng có thể hợp lệ, chẳng hạn không nhận `null` hoặc không nhận một giá trị ngoài miền. Nhưng cần tài liệu và ngoại lệ đúng, không âm thầm bỏ phần tử.

Đặc biệt, `Collection.add` phải bảo đảm phần tử có mặt sau khi trở về bình thường. Nếu từ chối vì lý do khác việc đã có phần tử theo quy tắc không nhận trùng, phải báo ngoại lệ, không tùy ý trả `false`. Xem [hợp đồng Collection.add](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#add(E)).

Do đó, một “list có dung lượng tối đa” không được âm thầm bỏ lần thêm rồi báo như không có gì. Nếu thật sự muốn chính sách thử thêm trả `false` khi đầy, phải chọn một API có ngữ nghĩa phù hợp hoặc một thao tác nghiệp vụ riêng.

### 5.3. Không thay nghĩa giao diện để thuận tiện cho cài đặt

Một `List` phải có nghĩa vị trí và thứ tự phần tử phù hợp. Tự sắp xếp lại sau một phép chèn theo vị trí mà không giữ ý nghĩa hợp đồng không thể được biện minh chỉ bằng tên lớp `SortedList`.

Một `Set` cần giữ tính duy nhất và quy tắc so sánh bằng phù hợp với hợp đồng của set. Không chỉ dùng một cấu trúc có comparator rồi bỏ qua quan hệ với `equals`.

Một `Map` còn có các view khóa, giá trị và entry với quan hệ tới map. `Map` thuộc Java Collections Framework nhưng không phải giao diện con của `Collection`.

Không ép một kiểu có ngữ nghĩa khác vào giao diện chuẩn chỉ để truyền được cho một hàm. Nếu bên gọi phải học lại ý nghĩa cơ bản của hầu hết phương thức, có thể một API riêng phù hợp hơn.

## 6. Kế thừa lớp cụ thể có thể mở nhiều đường sửa hơn dự tính

### 6.1. Một override không tự bao phủ toàn bộ API

Nếu chỉ muốn thêm kiểm tra đầu vào, cách ngắn nhất nhìn có vẻ là kế thừa `ArrayList` rồi override `add`. Nhưng lớp cha còn `addAll`, `add(index, element)`, `set`, list iterator và các view liên quan.

Không có một nguyên tắc chung bảo đảm mọi phương thức thay đổi dữ liệu của lớp cha sẽ gọi đúng override mà tác giả chọn.

Tệp sau cố ý cài đặt thiếu để chứng minh vấn đề; không dùng nó làm lớp danh sách hợp lệ cho yêu cầu “không có chuỗi trắng”.

Tệp `IncompleteSubclassDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IncompleteSubclassDemo {
    // Intentionally incomplete: overriding add does not protect all writes.
    static final class NonBlankList extends ArrayList<String> {
        @Override
        public boolean add(String value) {
            Objects.requireNonNull(value, "value");
            if (value.isBlank()) {
                throw new IllegalArgumentException("blank value");
            }
            return super.add(value);
        }
    }

    public static void main(String[] args) {
        var values = new NonBlankList();
        try {
            values.add("   ");
        } catch (IllegalArgumentException ex) {
            System.out.println("directAdd=" + ex.getClass().getSimpleName());
        }

        values.add("valid");
        values.addAll(List.of("   "));
        System.out.println("afterAddAllHasBlank=" +
                values.stream().anyMatch(String::isBlank));
        values.set(0, "");
        System.out.println("afterSetFirstLength=" + values.get(0).length());
        System.out.println("size=" + values.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 IncompleteSubclassDemo.java
java IncompleteSubclassDemo
```

Stdout:

```text
directAdd=IllegalArgumentException
afterAddAllHasBlank=true
afterSetFirstLength=0
size=2
```

Lần gọi `add("   ")` trực tiếp bị từ chối. Nhưng `addAll` đưa được chuỗi trắng vào, và `set` thay phần tử đầu bằng chuỗi rỗng.

Trên JDK 17 dùng để chạy ví dụ, `ArrayList.addAll` có đường xử lý riêng, không đi qua `add(E)` của lớp con cho từng phần tử. Có thể đối chiếu [cài đặt ArrayList trong OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/ArrayList.java).

Không cần biến chi tiết gọi nội bộ này thành cam kết cho mọi phiên bản. Điểm thiết kế quan trọng hơn là hợp đồng lớp cha không hứa một override duy nhất sẽ chặn mọi đường cập nhật của lớp con.

### 6.2. Sửa thêm vài phương thức vẫn cần xem toàn bộ bề mặt API

Có thể tiếp tục override thêm các phương thức thay đổi dữ liệu, nhưng phải xem cả:

- Các biến thể thêm và thêm theo lô.
- Thay thế theo vị trí và thay thế theo hàm.
- Thay đổi qua iterator hoặc list iterator.
- Thay đổi qua view được trả ra.
- Cách constructor khởi tạo dữ liệu.
- Hành vi kế thừa khi API thư viện phát triển.

Điều này không có nghĩa kế thừa luôn sai. Nó có nghĩa khi kế thừa một lớp cụ thể, phải biết những điểm mở rộng nào được thiết kế và được tài liệu hóa.

Nếu chỉ cần một vài thao tác nghiệp vụ như catalog ở mục trước, chứa cấu trúc bên trong có thể giữ đường sửa hẹp hơn và dễ kiểm tra hơn.

Nếu thật sự cần một collection mới, các lớp cài đặt khung giúp thể hiện rõ các thao tác nền mà phương thức khác được xây lên, thay vì dựa vào giả định về một lớp cụ thể.

## 7. Khi thực sự cần cài đặt: bắt đầu từ khung phù hợp

### 7.1. Các lớp khung giảm công việc lặp lại

| Kiểu cần cung cấp | Khung nên khảo sát | Phần nền cần hiểu |
|---|---|---|
| Collection tổng quát | `AbstractCollection` | Iterator và kích thước, cùng phép sửa nếu hỗ trợ |
| List truy cập theo vị trí hiệu quả | `AbstractList` | `get`, `size` và các phép sửa cần có |
| List dựa trên truy cập tuần tự | `AbstractSequentialList` | List iterator và kích thước |
| Set | `AbstractSet` | Cách giữ phần tử duy nhất, iterator và kích thước |
| Map | `AbstractMap` | `entrySet` và khả năng sửa đã công bố |

Đây là điểm bắt đầu để đọc hợp đồng, không phải bảng bảo đảm “chỉ cần viết vài phương thức là mọi yêu cầu đều tự đúng”.

`AbstractCollection` cung cấp nhiều thao tác từ iterator và kích thước. `AbstractSet` bổ sung ngữ nghĩa so sánh của set nhưng không tự biến một nguồn có phần tử trùng thành set hợp lệ. Xem [AbstractCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractCollection.html) và [AbstractSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSet.html).

`AbstractSequentialList` xây thao tác vị trí trên list iterator, phù hợp hơn khi nguồn truy cập tuần tự. `AbstractMap` lấy `entrySet` làm phần nền cho nhiều khả năng. Xem [AbstractSequentialList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSequentialList.html) và [AbstractMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.html).

Phải xem chi phí của phương thức kế thừa. Một cách viết đúng chức năng vẫn có thể không phù hợp nếu mọi `contains` phải quét một nguồn rất lớn hoặc mỗi lần `get` lại thực hiện I/O.

### 7.2. Trường hợp hợp lý: List tính từ mô tả dãy số

Giả sử dãy được xác định bởi:

```text
value(index) = first + index × step
index hợp lệ: từ 0 đến count - 1
```

Ta cần một danh sách hữu hạn, chỉ đọc, dùng được qua `List<Integer>`. Có thể lưu mô tả bằng ba tham số thay vì nạp sẵn toàn bộ giá trị.

`AbstractList` cho phép tạo một list không cho sửa bằng cách cài `get(int)` và `size()`; iterator và nhiều thao tác khác được xây trên những thao tác nền đó. Xem [AbstractList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html).

Tệp `ComputedListDemo.java`:

```java
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

public class ComputedListDemo {
    static final class ProgressionList extends AbstractList<Integer>
            implements RandomAccess {
        private final int first;
        private final int step;
        private final int count;

        ProgressionList(int first, int step, int count) {
            if (count < 0) {
                throw new IllegalArgumentException("negative count");
            }
            if (count > 0) {
                long last = first + (long) (count - 1) * step;
                if (last < Integer.MIN_VALUE || last > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("values exceed int range");
                }
            }
            this.first = first;
            this.step = step;
            this.count = count;
        }

        @Override
        public Integer get(int index) {
            Objects.checkIndex(index, count);
            return Math.toIntExact(first + (long) index * step);
        }

        @Override
        public int size() {
            return count;
        }
    }

    public static void main(String[] args) {
        List<Integer> values = new ProgressionList(10, 3, 5);
        List<Integer> reference = List.of(10, 13, 16, 19, 22);
        System.out.println("values=" + values);
        System.out.println("get2=" + values.get(2));
        System.out.println("subList=" + values.subList(1, 4));
        System.out.println("equalsForward=" + values.equals(reference));
        System.out.println("equalsBackward=" + reference.equals(values));
        System.out.println("sameHash=" +
                (values.hashCode() == reference.hashCode()));
        System.out.println("array=" + Arrays.toString(values.toArray()));
        System.out.println("sum=" +
                values.stream().mapToInt(Integer::intValue).sum());

        var large = new ProgressionList(0, 1, 1_000_000);
        System.out.println("largeSize=" + large.size());
        System.out.println("largeLast=" + large.get(large.size() - 1));
        System.out.println("descending=" + new ProgressionList(10, -2, 3));
        System.out.println("repeated=" + new ProgressionList(5, 0, 3));
        System.out.println("emptyHasNext=" +
                new ProgressionList(7, 1, 0).iterator().hasNext());

        try {
            values.add(25);
        } catch (UnsupportedOperationException ex) {
            System.out.println("write=" + ex.getClass().getSimpleName());
        }
        try {
            values.get(5);
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("badIndex=" + ex.getClass().getSimpleName());
        }
        try {
            new ProgressionList(Integer.MAX_VALUE, 1, 2);
        } catch (IllegalArgumentException ex) {
            System.out.println("overflow=" + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ComputedListDemo.java
java ComputedListDemo
```

Stdout:

```text
values=[10, 13, 16, 19, 22]
get2=16
subList=[13, 16, 19]
equalsForward=true
equalsBackward=true
sameHash=true
array=[10, 13, 16, 19, 22]
sum=80
largeSize=1000000
largeLast=999999
descending=[10, 8, 6]
repeated=[5, 5, 5]
emptyHasNext=false
write=UnsupportedOperationException
badIndex=IndexOutOfBoundsException
overflow=IllegalArgumentException
```

### 7.3. Vì sao đây là một cài đặt collection có căn cứ?

Biểu diễn trong lớp không phải một `ArrayList` được giấu dưới tên mới. Nó giữ mô tả dãy và tính phần tử theo chỉ số. Constructor không tạo một triệu đối tượng số để thiết lập dãy `large`.

Mọi vị trí có nghĩa rõ, số lượng hữu hạn, thứ tự ổn định và các giá trị được xác định từ dữ liệu không thay đổi. Giao diện `List` phù hợp với điều bên gọi được cung cấp.

Ví dụ cũng không tự viết lại iterator, `equals`, `hashCode`, `toArray` hoặc `subList`. Nó dùng phần khung và kiểm chứng một số tương tác quan trọng:

- Nội dung theo vị trí đúng.
- So sánh với một list chuẩn đúng ở cả hai chiều.
- List bằng nhau có hash bằng nhau.
- View theo khoảng và mảng có đúng nội dung.
- Stream nhận được toàn bộ các giá trị để tính tổng.
- Ghi không được hỗ trợ.
- Chỉ số sai và dãy vượt miền số được từ chối.

### 7.4. Kiểm tra biên và tràn số là một phần của thiết kế

`count` âm không có nghĩa hợp lệ nên constructor từ chối. Khi có phần tử, phần tử cuối được tính bằng `long` để tránh phép nhân trung gian tràn `int`.

Với một dãy tuyến tính, mọi giá trị nằm giữa giá trị đầu và cuối. Nếu hai đầu nằm trong miền `int`, các giá trị trung gian cũng nằm trong miền ấy. Vì thế kiểm tra đầu cuối phù hợp với biểu diễn này.

`get` dùng `Objects.checkIndex` trước khi tính. Không trả một giá trị mặc định cho chỉ số sai, vì như vậy sẽ giấu lỗi của bên gọi và làm sai ngữ nghĩa vị trí.

Bước âm tạo dãy giảm, bước 0 tạo phần tử trùng; cả hai đều có ý nghĩa với list trong ví dụ. Không vô tình áp một điều kiện “tăng nghiêm ngặt” chỉ vì mẫu dữ liệu đầu tiên đang tăng.

### 7.5. Lợi ích biểu diễn không làm mọi phép toán thành O(1)

`get` và `size` của ví dụ có công việc không tăng theo số phần tử. Vì vậy lớp dùng `RandomAccess` để biểu thị khả năng truy cập vị trí nhanh. Marker này giúp thuật toán tổng quát chọn cách làm phù hợp; nó không tự tăng tốc một cài đặt chậm. Xem [RandomAccess](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/RandomAccess.html).

Ngược lại, in toàn bộ dãy, tạo mảng, tính hash hoặc duyệt để tìm kiếm có thể phải đi qua nhiều phần tử. `toArray` trên dãy một triệu phần tử vẫn tạo một mảng đủ chứa kết quả.

Các giá trị được trả về dưới kiểu `Integer`; biểu diễn tính toán không loại bỏ mọi chi phí boxing khi API dùng kiểu đối tượng.

Phạm vi ví dụ là dãy hữu hạn chỉ đọc với quy tắc đơn giản. Nó chưa phải một thư viện số đầy đủ hoặc một lời hứa rằng mọi cài đặt `List` tính toán đều có cùng đặc tính.

## 8. Bằng chứng cần có trước khi dùng một cài đặt riêng

### 8.1. Kiểm chứng hợp đồng trước tối ưu hóa

Một kế hoạch kiểm tra có giá trị nên dựa vào hành vi mà bên gọi được quyền dùng, không chỉ sao lại thuật toán nội bộ.

Với list, có thể so một cài đặt mới với list tham chiếu qua các thao tác đã công bố: thêm/xóa/thay thế nếu hỗ trợ, truy cập biên, duyệt, mảng, so sánh và view.

Với cấu trúc chuyên biệt, không phải mọi hành vi đều giống một collection chuẩn bất kỳ. Phải xác định phần tương đương và phần có hạn chế hợp lệ, rồi kiểm tra đúng những gì đã hứa.

Ví dụ `ProgressionList` không cần giả vờ hỗ trợ thêm/xóa. Điều phải kiểm tra là nó chỉ đọc đúng cách, nội dung và phép đọc vẫn hợp lệ với `List`.

### 8.2. Iterator là một API thật, không chỉ một vòng for

Nếu tự cài iterator, cần tuân thủ trạng thái: `hasNext`, `next` khi còn/hết dữ liệu, và `remove` nếu được hỗ trợ. Khi hết lượt, `Iterator.next()` phải báo `NoSuchElementException`; không dùng `null` làm dấu kết thúc. Xem [Iterator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html).

Với list có thể thay đổi, còn phải xác định hành vi khi cấu trúc đổi trong lúc duyệt. Nếu dùng cơ chế `modCount` của `AbstractList`, cần cập nhật đúng theo thiết kế của khung. Cơ chế phát hiện sửa đổi không phải một khóa đồng bộ nhiều luồng.

Nếu tự tối ưu spliterator hoặc stream, không khai báo các đặc tính về thứ tự, kích thước hay tính bất biến chỉ để thuật toán chạy nhanh hơn. Những đặc tính đó phải đúng với dữ liệu và cách cập nhật.

Một cài đặt đúng nhờ dùng iterator kế thừa thường dễ kiểm chứng hơn một iterator tự viết chưa có yêu cầu đặc biệt.

### 8.3. Kiểm tra mọi đường sửa và cả trường hợp thất bại

Với lớp có điều kiện miền dữ liệu, cần kiểm tra `addAll`, cập nhật theo vị trí, iterator, view và các phương thức mặc định liên quan. Ví dụ kế thừa thiếu ở trên cho thấy chỉ thử đường `add` là không đủ.

Nếu thao tác theo lô gặp phần tử không hợp lệ, trạng thái sau lỗi phải phù hợp với điều đã công bố. Không tự giả định hợp đồng collection luôn đảm bảo hoàn tác tất cả phần đã làm trước khi ném lỗi.

Nếu nghiệp vụ yêu cầu một nhóm thay đổi phải cùng thành công hoặc cùng thất bại, phải thiết kế ranh giới đó rõ ràng. Đó có thể là lý do dùng một phương thức nghiệp vụ riêng thay vì hứa ngầm bằng tên `addAll`.

Với phép tùy chọn không làm thay đổi gì, việc có ném `UnsupportedOperationException` hay không có thể phụ thuộc hợp đồng cụ thể. Khi kiểm thử, đọc đúng API đã chọn thay vì đưa ra một quy tắc chung quá rộng cho mọi collection chỉ đọc.

### 8.4. Hiệu năng phải được so với phương án thay thế thật

Một cài đặt mới nên được đối chiếu với cấu trúc sẵn có đã cấu hình phù hợp, dữ liệu và tải đại diện. Không chỉ đo trường hợp mà thuật toán mới được thiết kế thuận lợi nhất.

Cần tính cả:

- Chi phí tạo và thu hồi dữ liệu.
- Thao tác thường gặp và thao tác hiếm nhưng đắt.
- Mức sử dụng bộ nhớ ở trạng thái thực.
- Độ trễ, không chỉ tổng số thao tác mỗi giây.
- Chi phí chuyển đổi khi đi qua API khác.
- Chi phí giữ view hoặc nguồn dữ liệu sống.

Ví dụ dãy tính toán chứng minh không phải nạp sẵn mọi giá trị để đọc một chỉ số. Chương trình không đo số byte hoặc so thông lượng, nên không dùng stdout của nó làm kết luận định lượng về hiệu năng.

## 9. Chốt quyết định bằng một mô tả thiết kế ngắn

Trước khi viết một collection mới, có thể ghi lại các điểm sau:

1. **Yêu cầu:** bên gọi cần những thao tác nào và với dữ liệu ra sao?
2. **Khoảng trống:** phương án sẵn có không đáp ứng chính xác điểm gì?
3. **Giao diện:** vì sao chọn `List`, `Set`, `Map`, `Collection`, hay chỉ một API riêng?
4. **Biểu diễn:** dữ liệu được lưu, tính hoặc lấy từ nguồn nào?
5. **Hợp đồng:** thứ tự, phần tử hợp lệ, khả năng sửa, view và vòng đời.
6. **Bằng chứng:** điều gì đã được kiểm chứng về chức năng, biên và chi phí?
7. **Trách nhiệm duy trì:** ai sẽ cập nhật khi yêu cầu hoặc môi trường thay đổi?

Các điểm này làm cho quyết định có thể được xem xét bằng yêu cầu và bằng chứng, thay vì chỉ bằng cảm giác mã ngắn hay thuật toán thú vị.

Áp vào các ví dụ:

| Trường hợp | Quyết định có căn cứ |
|---|---|
| Cần view không cho sửa, bản sao hoặc cách nhìn LIFO | Dùng công cụ có sẵn |
| Cần đăng ký thẻ với chuẩn hóa và quy tắc riêng | Dùng lớp nghiệp vụ bọc `LinkedHashSet` |
| Muốn cấm chuỗi trắng bằng cách override một `add` | Thiết kế chưa giữ được điều kiện trên mọi đường sửa |
| Cần `List<Integer>` hữu hạn được mô tả bằng công thức | Cài list chỉ đọc dựa trên `AbstractList` có thể phù hợp |

Nếu chưa mô tả được khoảng trống hoặc lợi ích cần kiểm chứng, nên tiếp tục tìm một cách kết hợp đơn giản hơn trước khi mở rộng bề mặt API.

Nếu ngữ nghĩa mới cố ý khác phần lớn hợp đồng chuẩn, một kiểu riêng có thể giúp người dùng hiểu đúng hơn việc gắn nhãn `List` hoặc `Set`.

Nếu cần nhiều luồng, dữ liệu ngoài heap, bộ nhớ nén hoặc xử lý tài nguyên ngoài, phạm vi kiểm chứng lớn hơn đáng kể. Không xem chúng là vài cờ có thể thêm vào sau một cài đặt tuần tự đã chạy thử.

## 10. Thực hành và tự kiểm tra

### Bài 1: có thật sự cần collection mới không?

Một dịch vụ cần trả danh sách cấu hình cho bên gọi, không cho bên gọi sửa. Sau khi trả, cấu hình nguồn có thể được cập nhật.

Hãy nêu hai yêu cầu khác nhau:

- Bên gọi luôn nhìn thấy cấu hình mới nhất qua một view.
- Bên gọi giữ đúng nội dung tại thời điểm đã chụp.

Vì sao không nên gộp cả hai thành “immutable list” rồi tự viết một lớp mới? Dùng `ExistingToolsDemo` để giải thích điều phải quyết định về quan hệ với nguồn và dữ liệu phần tử.

### Bài 2: nghiệp vụ hay Set?

Một bộ đăng ký mã cần chuẩn hóa chữ hoa/chữ thường, kiểm tra định dạng và có thao tác hợp nhất mã.

Bên gọi thật sự cần mọi phương thức của `Set` hay cần một vài thao tác đăng ký? Nếu triển khai trực tiếp `Set<String>`, phải giữ những nghĩa nào của `contains`, `equals` và giá trị được lưu?

Hướng giải thích: một API nghiệp vụ có thể mô tả danh tính đã chuẩn hóa rõ ràng. Chỉ nhận hợp đồng set khi có lý do tương thích và có thể giữ đúng hợp đồng đó.

### Bài 3: tìm đường sửa bị bỏ sót

Với `NonBlankList` trong ví dụ cố ý sai, hãy liệt kê thêm các đường sửa ngoài `add(E)`. Sau đó giải thích vì sao chỉ thêm một hoặc hai override mà không đọc lớp cha vẫn chưa là một chứng minh đầy đủ.

Không sửa bằng cách trả `false` cho mọi đầu vào không hợp lệ. Cần chọn đúng hợp đồng từ chối hoặc thu hẹp API.

### Bài 4: đánh đổi của danh sách tính toán

Với `ProgressionList`, đọc phần tử cuối của một dãy một triệu số không cần nạp sẵn tất cả giá trị. Vì sao gọi `toArray`, in toàn bộ hoặc tính hash vẫn có thể tốn nhiều công việc?

Nếu bên gọi chỉ cần một lần duyệt và không cần truy cập theo vị trí, giao diện nào hẹp hơn có thể đủ? Nếu phải tương thích một thư viện nhận `List`, vì sao cài đặt ví dụ lại có lý do rõ hơn?

Hướng giải thích: biểu diễn nén hoặc tính toán giúp một số thao tác, không thay đổi yêu cầu đầu ra của mọi thao tác tổng quát. Giao diện nên xuất phát từ cách sử dụng cần thiết.

### Bài 5: viết điều kiện để đồng ý duy trì cài đặt riêng

Chọn một yêu cầu thực của bản thân và ghi một mô tả ngắn theo bảy điểm ở mục trước.

Nếu bỏ collection tự viết đi, phương án có sẵn tốt nhất là gì? Nó thiếu điều gì? Cần những kiểm tra nào để chứng minh phương án mới giữ đúng nghĩa và tạo lợi ích?

Một câu trả lời có căn cứ cần nêu rõ phần phải tự sở hữu, phần có thể tái sử dụng và rủi ro đã được kiểm chứng đến đâu.

### Những câu hỏi cuối trước khi bắt đầu

1. Điều thiếu là cấu trúc dữ liệu, cách nhìn dữ liệu hay quy tắc nghiệp vụ?
2. Công cụ có sẵn hoặc lớp bọc có giải quyết được không?
3. Giao diện chuẩn có đúng với ngữ nghĩa cần cung cấp không?
4. Có biết toàn bộ đường đọc, duyệt và sửa mà giao diện mở ra không?
5. Đã chọn lớp khung phù hợp với cách truy cập chưa?
6. Lợi ích được giải thích bằng biểu diễn hoặc phép đo nào?
7. Có kiểm tra biên, ngoại lệ, so sánh và view không?
8. Phạm vi đồng thời, tài nguyên và bảo trì đã được phát biểu rõ chưa?

Tự cài đặt collection có giá trị khi nó lấp một khoảng trống cụ thể và vẫn giữ đúng điều bên gọi được quyền dựa vào. Lựa chọn lớp bọc hoặc công cụ có sẵn cũng là một quyết định thiết kế đầy đủ khi chúng đáp ứng đúng yêu cầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-collections-002">AbstractCollection, AbstractList, AbstractSequentialList và AbstractSet</a>

<details>
<summary>Xem chi tiết</summary>

# AbstractCollection, AbstractList, AbstractSequentialList và AbstractSet

## 1. Vì sao một collection tùy chỉnh cần lớp khung?

Giả sử bạn có dữ liệu được lưu theo cách riêng: một vùng mảng, một chuỗi nút liên kết, hoặc một bộ dữ liệu chỉ cho phép đọc. Bạn muốn người dùng của lớp vẫn gọi được những thao tác quen thuộc như `contains`, `toArray`, `iterator` hoặc `addAll`.

Nếu viết `implements Collection<E>` từ đầu, bạn phải tự cung cấp nhiều hành vi vốn có thể suy ra từ những thao tác nền tảng. Ví dụ, khi đã biết cách duyệt phần tử, bạn có thể tìm một phần tử bằng cách duyệt; khi đã biết cách thêm một phần tử, bạn có thể thêm cả nhóm bằng cách lặp lại thao tác ấy.

Các lớp `AbstractCollection`, `AbstractList`, `AbstractSequentialList` và `AbstractSet` cung cấp **cài đặt khung**: phần thuật toán có thể tái sử dụng được viết sẵn, còn lớp con cung cấp những thao tác gắn với dữ liệu và các ràng buộc của mình.

Hãy phân biệt ba tầng:

- **Giao diện** như `Collection`, `List`, `Set` mô tả hợp đồng mà phía sử dụng nhìn thấy.
- **Lớp khung** tổ chức việc thực hiện nhiều phương thức từ một số thao tác nền tảng.
- **Lớp cụ thể** quyết định lưu dữ liệu ở đâu, cho phép sửa những gì và giữ các bất biến nào.

Lớp khung không tự tạo một kho lưu phần tử cho bạn. Kế thừa `AbstractList` không tự có mảng; kế thừa `AbstractSet` không tự có bảng băm và cũng không tự lọc phần tử trùng trong dữ liệu của lớp con.

Quan hệ giữa bốn lớp:

```text
AbstractCollection<E> implements Collection<E>
├── AbstractList<E> implements List<E>
│   └── AbstractSequentialList<E>
└── AbstractSet<E> implements Set<E>
```

Đây đều là lớp trừu tượng. Muốn có đối tượng, bạn cần một lớp con cụ thể hoàn thiện những phương thức còn thiếu; lớp con ấy có thể được đặt tên hoặc được khai báo ẩn danh. Trong các ví dụ, ta đặt tên để nhìn rõ trách nhiệm và đường gọi.

**Điểm cần học không phải số lượng phương thức phải viết.** Bạn cần biết phương thức kế thừa sẽ gọi trở lại đâu, điều gì xảy ra nếu thao tác nền tảng không được hỗ trợ, và độ phức tạp có phù hợp với cách lưu dữ liệu hay không.

Lộ trình của bài là: chọn lớp khung → đọc sự phụ thuộc giữa phương thức → viết một lớp cụ thể nhỏ → kiểm chứng thao tác kế thừa → rà soát ràng buộc và chi phí.

## 2. Chọn lớp khung từ ngữ nghĩa và cách truy cập

| Lớp khung | Ngữ nghĩa cần cung cấp | Điểm xuất phát để đọc dữ liệu | Muốn sửa đầy đủ cần chú ý |
| --- | --- | --- | --- |
| `AbstractCollection<E>` | Một nhóm phần tử, chưa khẳng định là danh sách hay tập hợp | `iterator()`, `size()` | Thêm qua `add(E)`; xóa qua bộ duyệt có `remove()`. |
| `AbstractList<E>` | Danh sách có vị trí và thứ tự | `get(int)`, `size()` | Thay bằng `set(int,E)`; đổi kích thước bằng `add(int,E)`, `remove(int)`. |
| `AbstractSequentialList<E>` | Danh sách mà đi theo con trỏ là cách truy cập tự nhiên | `listIterator(int)`, `size()` | Bộ duyệt phải có các thao tác `set`, `add`, `remove` tương ứng với khả năng sửa. |
| `AbstractSet<E>` | Tập hợp không có phần tử trùng theo hợp đồng `Set` | `iterator()`, `size()` | Tự giữ tính duy nhất trên mọi đường đưa dữ liệu vào; thêm/xóa tương tự nền `AbstractCollection`. |

Bảng này mô tả hướng xây dựng thông dụng, không bắt buộc mọi collection đều phải hỗ trợ tất cả thao tác sửa. Một danh sách có thể chỉ đọc; một danh sách khác có thể cho phép thay phần tử nhưng không thêm/xóa.

Nguồn hợp đồng của bốn lớp là [AbstractCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractCollection.html), [AbstractList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html), [AbstractSequentialList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSequentialList.html) và [AbstractSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSet.html) trong Java 17.

Hai tiêu chí cần được tách riêng:

1. **Ngữ nghĩa:** người dùng cần danh sách có vị trí, tập hợp không trùng, hay chỉ một nhóm phần tử?
2. **Cơ chế dữ liệu:** truy cập phần tử theo chỉ số có rẻ không, hay giữ một con trỏ để đi tiếp mới rẻ?

Không chọn `AbstractSet` chỉ vì muốn ít phương thức; việc đã khai báo là `Set` kéo theo nghĩa vụ không trùng và quy tắc bằng nhau của tập hợp. Không chọn `AbstractList` chỉ vì lớp dữ liệu có thể miễn cưỡng cung cấp `get(i)` bằng cách đi lại từ đầu mỗi lần.

### 2.1. Phương thức tùy chọn không có nghĩa là có thể bỏ qua khi thiết kế

Trong Collections Framework, một số thao tác sửa là tùy chọn: một cài đặt có thể không hỗ trợ và ném `UnsupportedOperationException`. Lớp khung thường dùng chính cách này làm hành vi mặc định cho những thao tác sửa chưa được lớp con cung cấp.

Ví dụ, `AbstractCollection.add(E)` không tự tìm một chỗ lưu để thêm. Nếu lớp con không ghi đè, lời gọi thêm sẽ không được hỗ trợ. Nhưng `contains()` có thể hoạt động ngay khi lớp con có bộ duyệt đúng.

Một thao tác có tên liên quan đến sửa cũng không nhất thiết luôn ném ngoại lệ trên mọi đầu vào. Với một cài đặt kế thừa cách duyệt để xóa, `remove("khong-co")` có thể trả `false` mà chưa cần đến thao tác xóa của bộ duyệt. Khi tìm thấy một phần tử cần xóa, nó mới gặp thao tác không được hỗ trợ.

Vì vậy, đừng dùng kết quả của một lần gọi không làm thay đổi dữ liệu để kết luận lớp hỗ trợ sửa. Phải xác định hợp đồng và kiểm tra trường hợp thật sự cần thay đổi. [Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html) là nguồn tra cứu cho khái niệm thao tác tùy chọn và các hạn chế phần tử.

### 2.2. Kế thừa không thay thế việc giữ bất biến

Một vài bất biến phải do lớp cụ thể duy trì:

- Số lượng trả về phù hợp với dữ liệu và cách duyệt trong điều kiện sử dụng được hỗ trợ.
- Chỉ số hợp lệ được xử lý đúng, chỉ số ngoài miền báo lỗi thích hợp.
- Bộ duyệt tiến lên và kết thúc đúng, không trả một giá trị giả để báo hết.
- Nếu là `Set`, không có phần tử trùng theo quy tắc bằng nhau đã chọn.
- Nếu công bố chỉ đọc, không để lộ một đường sửa qua bộ duyệt hoặc đối tượng lưu nền.
- Nếu dùng `modCount` cho danh sách, các thay đổi cấu trúc được ghi nhận đúng.

Một lớp biên dịch được mới chỉ hoàn thành yêu cầu kiểu. Các bất biến này quyết định nó có thực hiện đúng hợp đồng collection hay không.

## 3. AbstractCollection: tái sử dụng từ cách duyệt

### 3.1. Những đường gọi quan trọng

Với `AbstractCollection`, hãy bắt đầu bằng `iterator()` và `size()`. Các thao tác kế thừa có thể dùng chúng:

```text
isEmpty()              → size()
contains(x)            → iterator() và kiểm tra phần tử
remove(x)              → tìm bằng iterator(), rồi iterator.remove()
addAll(source)         → duyệt source, gọi add(e)
removeAll / retainAll  → duyệt và dùng iterator.remove() khi cần
clear()                → duyệt rồi xóa từng phần tử
toArray() / toString() → dựa vào việc duyệt dữ liệu
```

Đây là bản đồ phụ thuộc giúp đọc ví dụ, không phải cam kết rằng mọi lớp con phải giữ nguyên các thuật toán ấy. Nếu kho lưu có cách tìm/xóa nhanh hơn, lớp con có thể ghi đè phương thức phù hợp mà vẫn giữ hợp đồng.

`removeIf` có một điểm về nơi khai báo: đây là phương thức mặc định của `Collection`, không phải một phương thức do `AbstractCollection` tự khai báo. Cài đặt mặc định duyệt rồi dùng `Iterator.remove()` cho phần tử thỏa điều kiện. Nguồn chi tiết là [Collection.removeIf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#removeIf(java.util.function.Predicate)).

Như vậy, chỉ hỗ trợ `add(E)` chưa làm một collection trở thành có thể xóa. Chỉ viết `remove(Object)` riêng cũng chưa tự làm `retainAll` kế thừa hoạt động nếu bộ duyệt vẫn không thể xóa. Cần đọc đúng đường gọi của phương thức mà bạn định công bố.

### 3.2. Chương trình: một nhóm cho sửa và một nhóm chỉ đọc

Ví dụ sau dùng `ArrayList` làm nơi lưu để tập trung vào cài đặt khung. `Bag` chấp nhận phần tử trùng và `null`; nó không khai báo mình là `List` hay `Set`. `ReadOnlyBag` sao chép đầu vào rồi chỉ trả bộ duyệt không thể sửa.

Lưu thành `AbstractCollectionDemo.java`:

```java
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class AbstractCollectionDemo {
    public static void main(String[] args) {
        Bag<String> bag = new Bag<>(Arrays.asList("red", "red", null, "blue"));
        System.out.println("initial = " + bag);
        System.out.println("remove one red = " + bag.remove("red"));
        System.out.println("after remove = " + bag);
        bag.removeIf(Objects::isNull);
        bag.addAll(List.of("blue", "green"));
        System.out.println("after removeIf and addAll = " + bag);
        bag.retainAll(Set.of("blue"));
        System.out.println("after retainAll = " + bag);
        bag.clear();
        System.out.println("after clear = " + bag);

        ReadOnlyBag<String> readOnly = new ReadOnlyBag<>(List.of("A", "B"));
        System.out.println("read-only contains A = " + readOnly.contains("A"));
        expectUnsupported("read-only add", () -> readOnly.add("C"));
        System.out.println("read-only remove missing = " + readOnly.remove("Z"));
        expectUnsupported("read-only remove present", () -> readOnly.remove("A"));
        System.out.println("same contents equal = "
                + readOnly.equals(new ReadOnlyBag<>(List.of("A", "B"))));
    }

    private static final class Bag<E> extends AbstractCollection<E> {
        private final List<E> data;

        private Bag() {
            data = new ArrayList<>();
        }

        private Bag(Collection<? extends E> source) {
            data = new ArrayList<>(source);
        }

        @Override
        public Iterator<E> iterator() {
            return data.iterator();
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public boolean add(E element) {
            return data.add(element);
        }
    }

    private static final class ReadOnlyBag<E> extends AbstractCollection<E> {
        private final List<E> data;

        private ReadOnlyBag() {
            this(List.of());
        }

        private ReadOnlyBag(Collection<? extends E> source) {
            data = Collections.unmodifiableList(new ArrayList<>(source));
        }

        @Override
        public Iterator<E> iterator() {
            return data.iterator();
        }

        @Override
        public int size() {
            return data.size();
        }
    }

    private static void expectUnsupported(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("operation unexpectedly succeeded");
        } catch (UnsupportedOperationException ex) {
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all AbstractCollectionDemo.java
java AbstractCollectionDemo
```

Kết quả:

```text
initial = [red, red, null, blue]
remove one red = true
after remove = [red, null, blue]
after removeIf and addAll = [red, blue, blue, green]
after retainAll = [blue, blue]
after clear = []
read-only contains A = true
read-only add -> UnsupportedOperationException
read-only remove missing = false
read-only remove present -> UnsupportedOperationException
same contents equal = false
```

### 3.3. Đọc kết quả theo thao tác nền tảng

`Bag` chỉ viết ba phương thức làm việc với phần tử: `iterator`, `size` và `add`. Những biến đổi còn lại trong `main` đến từ hành vi kế thừa:

- `remove("red")` chỉ xóa một lần xuất hiện, nên vẫn còn một `red`.
- `removeIf` xóa phần tử `null` bằng bộ duyệt có hỗ trợ xóa.
- `addAll` dùng `add` để thêm từng mục, nên `blue` được giữ cả hai lần.
- `retainAll` không biến nhóm thành tập hợp; nó giữ cả hai lần xuất hiện của `blue`.
- `clear` xóa hết thông qua bộ duyệt.

`ReadOnlyBag` không ghi đè `add`, nên lời gọi thêm bị từ chối. Với `remove("Z")`, thuật toán không tìm thấy mục cần xóa và trả `false`. Với `remove("A")`, nó tìm thấy mục nhưng bộ duyệt không hỗ trợ xóa, nên ném ngoại lệ. Hai kết quả này cùng phù hợp với cài đặt đã chọn.

Hai `ReadOnlyBag` có cùng dữ liệu vẫn cho `equals` bằng `false`. `AbstractCollection` không tự thêm quy tắc so sánh theo nội dung; lớp ví dụ dùng hành vi của `Object`. Không được suy ra từ hình thức in `[A, B]` rằng mọi collection đều có nghĩa bằng nhau giống danh sách.

Bản sao trong `ReadOnlyBag` là bản sao cấu trúc. Nếu phần tử là một đối tượng có thể thay đổi, mã bên ngoài vẫn có thể thay đổi chính đối tượng ấy qua một tham chiếu khác. Không cho sửa cấu trúc collection khác với làm toàn bộ đồ thị đối tượng trở nên bất biến.

Các lớp con được đặt `private` và `final` trong một chương trình minh họa; chúng chưa phải một thư viện công khai. Với lớp tổng quát dùng lại, thiết kế thêm mức truy cập, tài liệu về phần tử được phép, và các hàm tạo thích hợp. Hàm tạo không đối số và hàm tạo nhận `Collection<? extends E>` là một quy ước hữu ích, không phải phương thức do giao diện bắt buộc.


## 4. AbstractList: dùng thao tác theo chỉ số làm nền

### 4.1. Hướng xây dựng của danh sách truy cập trực tiếp

Với kho dữ liệu kiểu mảng, thao tác `get(i)` tự nhiên và rẻ. `AbstractList` tận dụng điều đó: bạn cung cấp thao tác theo chỉ số, lớp khung xây bộ duyệt và nhiều hành vi danh sách dựa trên chúng.

Đường gọi cần nhớ:

```text
get(i), size()                 → nền đọc
iterator(), listIterator(...)  → dùng các thao tác theo chỉ số
add(e)                         → add(size(), e)
iterator.remove()              → remove(index) của danh sách
listIterator.set(e)            → set(index, e)
listIterator.add(e)            → add(index, e)
clear()                        → removeRange(0, size())
```

Hai phương thức đọc `get(int)` và `size()` đủ để bắt đầu một danh sách không cho sửa. Để thay phần tử, cung cấp `set(int,E)`. Để thay đổi kích thước đầy đủ, cung cấp thêm `add(int,E)` và `remove(int)`. Bộ duyệt kế thừa dựa vào chính những thao tác ấy. Xem [AbstractList trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html).

Một lỗi rất dễ mắc là chỉ ghi đè `add(E)`. Khi đó một số thao tác thêm cuối có thể hoạt động, nhưng `add(index,E)` và thao tác thêm qua `ListIterator` vẫn có thể không được hỗ trợ. Với hướng xây dựng của lớp khung này, thao tác thêm theo vị trí mới là nền thích hợp để triển khai.

Cũng cần phân biệt chữ ký:

- `remove(int index)` xóa theo vị trí và trả phần tử cũ.
- `remove(Object value)` xóa một lần xuất hiện theo giá trị và trả `boolean`.

Đây là hai thao tác khác nhau. Với danh sách số nguyên, chọn nhầm chữ ký có thể xóa nhầm vị trí, dù lớp cụ thể đã cài đặt hoàn toàn đúng.

### 4.2. Miền chỉ số của phần tử khác miền vị trí con trỏ

Với danh sách có `n` phần tử:

| Thao tác | Miền hợp lệ |
| --- | --- |
| `get(i)`, `set(i,e)`, `remove(i)` | `0 <= i < n` |
| `add(i,e)` | `0 <= i <= n` |
| `listIterator(i)` | `0 <= i <= n` |
| `subList(from,to)` | `0 <= from <= to <= n`; đầu `from` được lấy, đầu `to` bị loại |

Vị trí `n` không chứa phần tử để đọc, nhưng là chỗ hợp lệ để chèn cuối hoặc đặt con trỏ ở cuối. Đừng dùng cùng một phép kiểm tra cho tất cả phương thức.

Phương thức `set` trả phần tử cũ và giữ nguyên kích thước. `add` chèn, làm các phần tử phía sau dịch chỉ số. `remove(int)` xóa và dịch phần còn lại về trước. Lớp khung không tự sửa các lỗi lệch một vị trí trong phần lưu trữ của lớp con.

### 4.3. Chương trình: danh sách nhỏ lưu trên mảng

`ArrayBuffer` dưới đây tự lưu dữ liệu trong `Object[]`. Nó không dùng `ArrayList` làm nơi lưu, nên bạn nhìn thấy rõ phần việc mà lớp con phải thực hiện: kiểm tra chỉ số, mở rộng mảng, dịch phần tử, cập nhật số lượng và ghi nhận thay đổi cấu trúc.

Lưu thành `AbstractListDemo.java`:

```java
import java.util.AbstractList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.RandomAccess;

public class AbstractListDemo {
    public static void main(String[] args) {
        ArrayBuffer<String> list = new ArrayBuffer<>();
        list.addAll(List.of("A", "B", "C"));
        System.out.println("initial = " + list);

        ListIterator<String> cursor = list.listIterator(1);
        System.out.println("cursor next = " + cursor.next());
        cursor.set("B*");
        cursor.add("X");
        System.out.println("after cursor add = " + list);
        System.out.println("cursor previous = " + cursor.previous());
        cursor.remove();
        System.out.println("after cursor remove = " + list);

        List<String> middle = list.subList(1, 3);
        middle.set(0, "B2");
        middle.remove(1);
        System.out.println("backing after view edits = " + list);
        System.out.println("equals standard list = " + list.equals(List.of("A", "B2")));
        System.out.println("same list hash = "
                + (list.hashCode() == List.of("A", "B2").hashCode()));

        Iterator<String> afterSet = list.iterator();
        list.set(0, "A2");
        System.out.println("iterator after set = " + afterSet.next());

        Iterator<String> stale = list.iterator();
        list.add("D");
        try {
            stale.next();
            throw new AssertionError("structural change was not detected");
        } catch (ConcurrentModificationException ex) {
            System.out.println("stale iterator -> " + ex.getClass().getSimpleName());
        }
        list.subList(1, list.size()).clear();
        System.out.println("after range clear = " + list);
    }

    private static final class ArrayBuffer<E>
            extends AbstractList<E> implements RandomAccess {
        private Object[] elements = new Object[2];
        private int count;

        @Override
        public int size() {
            return count;
        }

        @Override
        @SuppressWarnings("unchecked")
        public E get(int index) {
            Objects.checkIndex(index, count);
            return (E) elements[index];
        }

        @Override
        public E set(int index, E element) {
            E previous = get(index);
            elements[index] = element;
            return previous;
        }

        @Override
        public void add(int index, E element) {
            if (index < 0 || index > count) {
                throw new IndexOutOfBoundsException("index: " + index);
            }
            if (count == elements.length) {
                int capacity = Math.addExact(count, Math.max(1, count / 2));
                elements = Arrays.copyOf(elements, capacity);
            }
            System.arraycopy(elements, index, elements, index + 1, count - index);
            elements[index] = element;
            count++;
            modCount++;
        }

        @Override
        public E remove(int index) {
            E previous = get(index);
            System.arraycopy(elements, index + 1, elements, index, count - index - 1);
            elements[--count] = null;
            modCount++;
            return previous;
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all AbstractListDemo.java
java AbstractListDemo
```

Kết quả:

```text
initial = [A, B, C]
cursor next = B
after cursor add = [A, B*, X, C]
cursor previous = X
after cursor remove = [A, B*, C]
backing after view edits = [A, B2]
equals standard list = true
same list hash = true
iterator after set = A2
stale iterator -> ConcurrentModificationException
after range clear = [A2]
```

### 4.4. Vì sao chỉ năm phương thức mà bộ duyệt sửa được danh sách?

Lớp cụ thể cung cấp `get`, `size`, `set`, `add(int,E)` và `remove(int)`. Nó không tự viết `iterator()` hay `listIterator()`.

Bộ duyệt danh sách được tạo ở vị trí 1, trước `B`. Sau `next()`, phần tử vừa trả là `B`. `set("B*")` thay nó. `add("X")` chèn trước phần tử mà lần `next()` tiếp theo sẽ trả, nên danh sách thành `[A, B*, X, C]`. Con trỏ đã ở sau `X`, vì vậy `previous()` trả `X`, rồi `remove()` xóa chính mục vừa được trả đó.

Các phương thức kế thừa đã thực hiện việc quản lý con trỏ và gọi đúng thao tác của lớp con. Nhưng tính đúng của mảng vẫn phụ thuộc lớp con:

- Khi chèn, dịch đoạn từ `index` sang phải trước khi ghi phần tử mới.
- Khi xóa, dịch phần phía sau sang trái.
- Sau khi xóa, đặt ô thừa cuối mảng về `null` để không giữ lại một tham chiếu không còn thuộc danh sách.
- Tăng `count` hoặc giảm `count` đúng một lần.
- Chỉ trả về giá trị tại chỉ số đã được xác thực.

Phép ép từ `Object` về `E` ở `get` được giới hạn trong một phương thức và có `@SuppressWarnings("unchecked")`. Cơ sở của nó là mảng chỉ được ghi qua những đường nhận `E` của lớp, không được công khai cho mã ngoài. Đây không phải lý do để tắt cảnh báo cho cả dự án hoặc dùng kiểu thô.

Mảng được tăng dung lượng khi đầy; `Math.addExact` tránh cho phép tính dung lượng âm thầm tràn. Mã vẫn là ví dụ học tập nhỏ, chưa có toàn bộ chính sách dung lượng tối đa và tối ưu hóa của một collection dùng trong thư viện. Nó không hứa có thể cấp phát mảng lớn tùy ý.

### 4.5. modCount ghi nhận thay đổi cấu trúc

`AbstractList` có trường `protected modCount`. Bộ duyệt kế thừa có thể dùng nó để phát hiện một thay đổi cấu trúc không đi qua bộ duyệt đang dùng.

Trong `ArrayBuffer`:

- `add(int,E)` thành công làm thay đổi kích thước, nên tăng `modCount` một lần.
- `remove(int)` thành công cũng tăng một lần.
- `set(int,E)` chỉ thay giá trị tại một vị trí, nên không tăng.
- Một yêu cầu bị từ chối do chỉ số sai không được sửa dữ liệu hay tăng bộ đếm.

[Hợp đồng modCount](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html#modCount) lưu ý không tăng hơn một lần trong một lời gọi đơn `add(int,E)` hoặc `remove(int)` khi dùng cơ chế bộ duyệt có sẵn. Tăng ở cả phương thức ngoài lẫn hàm trợ giúp bên trong có thể khiến bộ duyệt nhận nhầm rằng đã có một sửa đổi ngoài ý muốn.

Không đồng nhất `modCount` với kích thước: xóa một mục rồi thêm một mục có thể giữ nguyên kích thước cuối cùng nhưng đã thay đổi cấu trúc hai lần. Bộ duyệt tạo trước hai thao tác ấy không thể coi dữ liệu như chưa đổi.

Chương trình minh họa hai trường hợp trong **một luồng**:

1. Tạo bộ duyệt, dùng `set` thay `A` bằng `A2`, rồi gọi `next()`. Nó đọc `A2` bình thường.
2. Tạo bộ duyệt khác, thêm `D` trực tiếp vào danh sách, rồi gọi `next()`. Bộ duyệt kế thừa phát hiện thay đổi và ném `ConcurrentModificationException`.

Ngoại lệ này không chứng minh có hai luồng. Nó báo một kiểu sử dụng không phù hợp với bộ duyệt. Cũng không được dùng khả năng báo lỗi ấy làm cơ chế đồng bộ hóa: [ConcurrentModificationException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ConcurrentModificationException.html) mô tả hành vi phát hiện lỗi là cố gắng tốt nhất, không phải bảo đảm an toàn khi nhiều luồng sửa không đồng bộ.

`modCount` không tự khóa dữ liệu, không tự làm một nhóm thao tác trở nên nguyên tử và không tạo một bản chụp cho bộ duyệt.

### 4.6. subList là một khung nhìn có liên hệ với danh sách gốc

`middle = list.subList(1,3)` không sao chép `B*` và `C` sang một danh sách độc lập. `middle.set(0,"B2")` thay phần tử ở vị trí tương ứng trong danh sách gốc. `middle.remove(1)` xóa `C` khỏi danh sách gốc. Vì vậy kết quả là `[A, B2]`.

Các sửa đổi đi qua khung nhìn được ánh xạ về danh sách nền; thông tin kích thước và thay đổi cấu trúc phải được theo dõi nhất quán. Khi lớp con tự quản lý lưu trữ, một `modCount` bị bỏ sót có thể gây lỗi cả ở khung nhìn, không chỉ ở bộ duyệt đơn.

Sau khi thay đổi cấu trúc trực tiếp trên danh sách gốc ngoài khung nhìn, đừng tiếp tục dựa vào khung nhìn cũ. Theo hợp đồng [List.subList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#subList(int,int)), ngữ nghĩa của khung nhìn trở nên không xác định khi nền bị sửa cấu trúc theo cách không đi qua nó. Một triển khai có thể phát hiện và ném ngoại lệ, nhưng ngoại lệ không phải cách “làm mới” khung nhìn.

Chương trình không dùng lại `middle` sau khi thêm `D` ở danh sách gốc. Nó tạo một khung nhìn mới để xóa đoạn cuối.

### 4.7. Tái sử dụng đúng chưa chắc đã tối ưu

`get(i)` của `ArrayBuffer` là truy cập mảng sau kiểm tra chỉ số, nên phù hợp với hướng `AbstractList`. Việc triển khai `RandomAccess` cũng phù hợp với đặc điểm này. [RandomAccess](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/RandomAccess.html) là giao diện đánh dấu để thuật toán tổng quát nhận biết kiểu truy cập nhanh; kế thừa `AbstractList` không tự có dấu ấy và cũng không làm `get` nhanh hơn.

Ngược lại, việc chèn/xóa giữa mảng cần dịch dữ liệu. Bộ duyệt kế thừa có thể dùng đúng thao tác nhưng vẫn phải trả chi phí ấy.

`clear()` kế thừa gọi `removeRange(0,size())`. Cài đặt nền của `removeRange` dùng bộ duyệt để xóa từng phần tử. Với mảng, xóa lặp từ đầu có thể dịch dữ liệu nhiều lần và đạt chi phí bậc hai. Một bước tối ưu thích hợp là ghi đè `removeRange` để dịch đoạn còn lại một lần, xóa các ô thừa, cập nhật số lượng và `modCount` đúng.

Không cần viết lại mọi phương thức ngay từ đầu. Trước hết giữ một cài đặt đúng và hiểu đường gọi; sau đó chọn điểm ghi đè giúp cải thiện tình huống sử dụng thật. `removeRange` là một điểm như vậy vì cả `clear` của danh sách lẫn thao tác xóa qua `subList` có thể hưởng lợi.


## 5. AbstractSequentialList: đảo hướng phụ thuộc sang ListIterator

### 5.1. Có get(int) không đồng nghĩa với truy cập trực tiếp rẻ

Một danh sách liên kết vẫn có thể cung cấp `get(i)`. Nhưng để tìm vị trí ấy, nó cần di chuyển qua các nút. Nếu mỗi lần lấy phần tử lại bắt đầu một lượt tìm vị trí, vòng lặp theo chỉ số có thể đi qua dữ liệu nhiều lần.

Hướng tự nhiên hơn là đặt con trỏ một lần rồi gọi `next()` để đi tiếp. `AbstractSequentialList` chọn hướng ấy: lớp con cung cấp `listIterator(int)` và `size()`; các thao tác theo chỉ số được xây trên bộ duyệt.

```text
get(i)      → listIterator(i).next()
set(i,e)    → đặt bộ duyệt ở i, next(), rồi set(e)
add(i,e)    → đặt bộ duyệt ở i, rồi add(e)
remove(i)   → đặt bộ duyệt ở i, next(), rồi remove()
iterator()  → dùng bộ duyệt danh sách
```

So với `AbstractList`, hướng phụ thuộc đã đảo lại. Hai lớp đều thực hiện `List` nhưng khác điểm tựa của thuật toán. Đây là khác biệt thiết kế cốt lõi được mô tả trong [AbstractSequentialList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSequentialList.html).

Một lỗi khi chọn lớp này là viết `listIterator(i)` bằng cách gọi lại `get(i)` kế thừa. `get(i)` vốn đang gọi `listIterator(i)`, nên bạn tạo vòng gọi đệ quy thay vì một nền truy cập dữ liệu. Phương thức nền phải đi đến nơi lưu thật hoặc một bộ duyệt nền độc lập.

### 5.2. Bộ duyệt phải có hợp đồng đầy đủ

`ListIterator` có con trỏ nằm **giữa** các phần tử. Danh sách có `n` mục có `n+1` vị trí con trỏ, từ trước mục đầu đến sau mục cuối.

Các phương thức đọc cần được cài đặt nhất quán: `hasNext`, `next`, `hasPrevious`, `previous`, `nextIndex`, `previousIndex`. Khi hết hướng tương ứng, `next` hoặc `previous` phải ném `NoSuchElementException`, không trả `null` để báo hết vì `null` có thể là một phần tử hợp lệ.

Các phương thức sửa `remove`, `set`, `add` phải được triển khai hoặc từ chối rõ ràng bằng `UnsupportedOperationException`. Không có một phương thức chung tên `index()` thay thế hai phương thức chỉ số của `ListIterator`.

Với bộ duyệt có hỗ trợ sửa:

- `set` và `remove` tác động lên phần tử vừa được `next` hoặc `previous` trả về.
- `add` chèn tại vị trí con trỏ và đặt con trỏ sau phần tử mới.
- Không được gọi `remove` liên tiếp hai lần cho cùng một lần trả phần tử.
- Sau `add`, không thể dùng ngay `set` hoặc `remove` như thể phần tử mới được trả bởi `next`/`previous`.

Nguồn quy tắc trạng thái là [ListIterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ListIterator.html). Lớp khung sẽ dựa vào những điều này để thực hiện thao tác khác; một bộ duyệt “gần đúng” có thể làm nhiều phương thức kế thừa sai theo.

### 5.3. Chương trình: một lớp tuần tự ủy quyền bộ duyệt

Ví dụ dùng `LinkedList` làm cấu trúc lưu nền, rồi bọc bộ duyệt của nó. Mục đích là nhìn thấy hướng tái sử dụng của `AbstractSequentialList`, không viết lại toàn bộ thuật toán nút liên kết.

Phần bọc có hai công việc riêng:

1. Đếm số lần bắt đầu bộ duyệt để quan sát đường gọi.
2. Cập nhật `modCount` của lớp ngoài khi thêm/xóa thành công, để khung nhìn kế thừa theo dõi được thay đổi cấu trúc.

Lưu thành `AbstractSequentialListDemo.java`:

```java
import java.util.AbstractSequentialList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class AbstractSequentialListDemo {
    public static void main(String[] args) {
        SequentialBuffer<String> list = new SequentialBuffer<>(List.of("A", "B", "C"));
        list.iteratorStarts = 0;
        StringBuilder indexed = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            indexed.append(list.get(i));
        }
        int indexedStarts = list.iteratorStarts;

        list.iteratorStarts = 0;
        StringBuilder traversed = new StringBuilder();
        for (String value : list) {
            traversed.append(value);
        }
        int traversalStarts = list.iteratorStarts;
        System.out.println("indexed values = " + indexed);
        System.out.println("indexed iterator starts = " + indexedStarts);
        System.out.println("traversal values = " + traversed);
        System.out.println("traversal iterator starts = " + traversalStarts);

        ListIterator<String> cursor = list.listIterator(1);
        System.out.println("cursor next = " + cursor.next());
        cursor.set("B*");
        cursor.add("X");
        System.out.println("after cursor add = " + list);
        System.out.println("cursor previous = " + cursor.previous());
        cursor.remove();
        System.out.println("after cursor remove = " + list);
        list.subList(1, 3).clear();
        System.out.println("after view clear = " + list);
        try {
            list.get(list.size());
            throw new AssertionError("end position is not an element");
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("get at end -> " + ex.getClass().getSimpleName());
        }
    }

    private static final class SequentialBuffer<E>
            extends AbstractSequentialList<E> {
        private final LinkedList<E> data;
        private int iteratorStarts;

        private SequentialBuffer() {
            data = new LinkedList<>();
        }

        private SequentialBuffer(Collection<? extends E> source) {
            data = new LinkedList<>(source);
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public ListIterator<E> listIterator(int index) {
            ListIterator<E> delegate = data.listIterator(index);
            iteratorStarts++;
            return new ListIterator<>() {
                @Override
                public boolean hasNext() {
                    return delegate.hasNext();
                }

                @Override
                public E next() {
                    return delegate.next();
                }

                @Override
                public boolean hasPrevious() {
                    return delegate.hasPrevious();
                }

                @Override
                public E previous() {
                    return delegate.previous();
                }

                @Override
                public int nextIndex() {
                    return delegate.nextIndex();
                }

                @Override
                public int previousIndex() {
                    return delegate.previousIndex();
                }

                @Override
                public void remove() {
                    delegate.remove();
                    modCount++;
                }

                @Override
                public void set(E element) {
                    delegate.set(element);
                }

                @Override
                public void add(E element) {
                    delegate.add(element);
                    modCount++;
                }
            };
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all AbstractSequentialListDemo.java
java AbstractSequentialListDemo
```

Kết quả:

```text
indexed values = ABC
indexed iterator starts = 3
traversal values = ABC
traversal iterator starts = 1
cursor next = B
after cursor add = [A, B*, X, C]
cursor previous = X
after cursor remove = [A, B*, C]
after view clear = [A]
get at end -> IndexOutOfBoundsException
```

### 5.4. Ba lần bắt đầu khác một lần đi tiếp

Trong vòng lặp theo chỉ số, mỗi `get(i)` kế thừa tạo một bộ duyệt ở `i` rồi lấy phần tử kế tiếp. Với ba mục, bộ đếm ghi nhận ba lần bắt đầu.

Trong `for-each`, `iterator()` cung cấp một bộ duyệt, rồi vòng lặp dùng tiếp nó. Bộ đếm chỉ là 1. Hai cách đều cho chuỗi `ABC`, nhưng đường đi qua tầng truy cập không giống nhau.

Đây là phép quan sát số lần gọi trong chính ví dụ. Nó không đo nano giây, không chứng minh một máy cụ thể nhanh hơn bao nhiêu và không suy ra chi phí cho mọi cài đặt `AbstractSequentialList`.

Với dữ liệu liên kết, chi phí định vị mới là lý do phải quan tâm. [LinkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html) mô tả việc truy cập theo chỉ số bằng cách đi từ đầu hoặc cuối gần hơn. Lặp lại việc định vị cho từng chỉ số có thể cho tổng chi phí bậc hai; giữ một bộ duyệt rồi đi tiếp thường tránh phần lặp lại đó.

Nếu bạn dùng `AbstractList` cho một kho liên kết mà chỉ cung cấp `get(i)` bằng cách đi từ đầu, bộ duyệt kế thừa dựa trên `get` cũng có thể kéo theo chi phí xấu. Tên giao diện `List` không quyết định tốc độ; cần xem cách các phương thức gọi nhau và cách kho lưu thực hiện việc tìm vị trí.

### 5.5. Vì sao không trả trực tiếp bộ duyệt LinkedList rồi bỏ qua phần bọc?

Bộ duyệt của `LinkedList` theo dõi thay đổi trong chính `LinkedList`. Nhưng lớp ngoài cũng kế thừa các cơ chế của `AbstractList`, chẳng hạn khung nhìn `subList` dựa vào trạng thái thay đổi cấu trúc của danh sách ngoài.

Nếu lớp ngoài thay đổi nền mà không cập nhật `modCount` của mình, hai tầng có thể nhìn thấy các phiên bản cấu trúc khác nhau. Ở ví dụ, mỗi `delegate.add` hoặc `delegate.remove` thành công được theo sau bởi đúng một lần tăng bộ đếm ngoài. `delegate.set` không đổi kích thước nên không tăng.

Nếu thao tác của `delegate` ném ngoại lệ, câu lệnh tăng sau đó không chạy. Điều này tránh ghi nhận một sửa đổi chưa xảy ra. Các trạng thái hợp lệ của bộ duyệt, phép kiểm tra biên và phát hiện sửa đổi trên nền được ủy quyền cho bộ duyệt `LinkedList`.

Đây là một thiết kế cụ thể sử dụng hai tầng đối tượng. Nếu tự viết cấu trúc nút và bộ duyệt, bạn phải tự quản lý con trỏ, phần tử vừa trả, số lượng và cơ chế phát hiện sửa đổi. Không có một yêu cầu rằng mọi lớp con của `AbstractSequentialList` đều phải bọc `LinkedList`.

Lần `subList(1,3).clear()` xóa được `B*` và `C` thông qua các đường gọi đã thiết kế. `get(list.size())` sau đó ném `IndexOutOfBoundsException`: vị trí cuối là chỗ đặt con trỏ hợp lệ, nhưng không phải phần tử hợp lệ để đọc. `AbstractSequentialList` chuyển tình huống hết phần tử khi đọc theo chỉ số thành ngoại lệ chỉ số phù hợp.

### 5.6. Khi nào nên chọn hướng này?

Chọn `AbstractSequentialList` khi thao tác đi tiếp/lùi qua vị trí hiện tại là nền truy cập tốt, còn truy cập ngẫu nhiên phải định vị. Bạn không cần cố tạo một `get` trông giống mảng để rồi xây mọi thao tác khác trên nó.

Ngược lại, với kho mảng có `get(i)` rẻ, `AbstractList` thường giúp biểu diễn trực tiếp hơn. Việc `AbstractSequentialList` là lớp con của `AbstractList` không làm nó “đầy đủ hơn” cho mọi tình huống. Nó chuyên biệt hóa hướng xây dựng cho một kiểu truy cập khác.

Trong cả hai hướng, các thao tác sửa có thể bị giới hạn theo thiết kế: chỉ đọc, cho thay nhưng cố định kích thước, hoặc cho thêm/xóa. Hợp đồng công khai phải phản ánh đúng các khả năng ấy; không suy ra từ chữ “sequential” rằng danh sách được phép sửa hay an toàn cho nhiều luồng.


## 6. AbstractSet: có khung hợp đồng, vẫn phải tự giữ tính duy nhất

### 6.1. Không có bảng băm được tạo ngầm

`AbstractSet` kế thừa nền duyệt của `AbstractCollection` và thực hiện giao diện `Set`. Lớp con vẫn cần cung cấp `iterator` và `size`. Nếu muốn thêm phần tử, cần cung cấp `add` thích hợp.

Điều được bổ sung ở đây là ngữ nghĩa tập hợp, đặc biệt các cài đặt `equals` và `hashCode` phù hợp với `Set`. Ngoài ra, `AbstractSet` còn có cài đặt `removeAll` riêng. Nó không phải `HashSet` rút gọn: không có yêu cầu rằng dữ liệu phải được lưu bằng băm và không có thuật toán chống trùng tự động chặn mọi lần ghi vào kho riêng của lớp con.

Nếu lớp con dùng một mảng hoặc danh sách nền, rồi `add` chỉ nối phần tử vào cuối, các phần tử bằng nhau vẫn có thể xuất hiện nhiều lần. Kế thừa tên lớp không sửa được bất biến bị vi phạm đó.

Với ví dụ nhỏ, cách giữ tính duy nhất là kiểm tra có phần tử bằng nhau trước khi thêm. Nếu đã có, trả `false` và không thay đổi. Nếu chưa có, lưu phần tử rồi trả `true`. Khi chọn một thuật toán tốt hơn, bạn vẫn phải giữ ý nghĩa của kết quả và quan hệ bằng nhau.

Các ràng buộc này thuộc [Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html); phần tái sử dụng cụ thể thuộc [AbstractSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSet.html).

### 6.2. Chương trình: tập hợp nhỏ lưu bằng danh sách

`TinySet` cố ý dùng `ArrayList` để bạn thấy rõ rằng một `Set` không nhất thiết được lưu bằng bảng băm. Nó chấp nhận một `null`, duyệt theo thứ tự thêm trong cài đặt này và tìm bằng cách duyệt.

Lưu thành `AbstractSetDemo.java`:

```java
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class AbstractSetDemo {
    public static void main(String[] args) {
        TinySet<String> set = new TinySet<>();
        System.out.println("add alpha = " + set.add("alpha"));
        System.out.println("add equal alpha = " + set.add(new String("alpha")));
        set.add("beta");
        set.add(null);
        System.out.println("initial = " + set);

        Set<String> other = new HashSet<>(Arrays.asList(null, "beta", "alpha"));
        System.out.println("equals standard set = " + set.equals(other));
        System.out.println("reverse equality = " + other.equals(set));
        System.out.println("same set hash = " + (set.hashCode() == other.hashCode()));
        System.out.println("equals list = " + set.equals(Arrays.asList("alpha", "beta", null)));
        set.removeAll(Collections.singleton("alpha"));
        System.out.println("after removeAll = " + set);

        Iterator<String> cursor = set.iterator();
        System.out.println("iterator next = " + cursor.next());
        cursor.remove();
        System.out.println("after iterator remove = " + set);

        TinySet<String> copied = new TinySet<>(Arrays.asList("x", "x", null));
        System.out.println("copy = " + copied);
        System.out.println("copy size = " + copied.size());
        System.out.println("add second null = " + copied.add(null));
    }

    private static final class TinySet<E> extends AbstractSet<E> {
        private final List<E> data = new ArrayList<>();

        private TinySet() {
        }

        private TinySet(Collection<? extends E> source) {
            addAll(source);
        }

        @Override
        public Iterator<E> iterator() {
            return data.iterator();
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public boolean add(E element) {
            if (contains(element)) {
                return false;
            }
            return data.add(element);
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all AbstractSetDemo.java
java AbstractSetDemo
```

Kết quả:

```text
add alpha = true
add equal alpha = false
initial = [alpha, beta, null]
equals standard set = true
reverse equality = true
same set hash = true
equals list = false
after removeAll = [beta, null]
iterator next = beta
after iterator remove = [null]
copy = [x, null]
copy size = 2
add second null = false
```

### 6.3. Không trùng phải đúng trên mọi đường đưa dữ liệu vào

Lần `add("alpha")` thay đổi tập hợp nên trả `true`. Lần thêm `new String("alpha")` có một đối tượng khác nhưng nội dung bằng nhau; nó trả `false`. Việc tạo `String` mới chỉ phục vụ kiểm chứng bằng nhau theo giá trị, không phải một cách viết chuỗi nên áp dụng thường ngày.

Hàm tạo nhận `Collection` không sao chép thẳng mọi mục vào `data`. Nó gọi `addAll(source)`, mà đường kế thừa thêm từng phần tử qua `add` đã kiểm tra trùng. Vì vậy đầu vào `["x", "x", null]` tạo tập hợp có hai mục `[x, null]`.

Nếu thay bằng `data.addAll(source)`, ta có thể bỏ qua quy tắc chống trùng của lớp ngoài. Đây là lý do phải kiểm tra cả hàm tạo, phương thức hàng loạt và mọi đường cập nhật riêng, không chỉ một lần gọi `set.add(...)` ở phía người dùng.

`data` được giữ riêng và không trả trực tiếp cho mã sử dụng. Bộ duyệt cho phép xóa nhưng không có thao tác chèn, nên đường này không tạo thêm bản trùng. Chính sách cho `null` đến từ thiết kế `TinySet` và kho nền, không phải một cam kết rằng mọi lớp con của `AbstractSet` đều nhận `null`.

Thứ tự in trong ví dụ là đặc điểm của `TinySet` với nền `ArrayList`. Không lấy nó làm bảo đảm thứ tự cho mọi `Set`. Nghĩa bằng nhau của tập hợp vẫn không phụ thuộc thứ tự duyệt.

### 6.4. equals và hashCode của tập hợp khác danh sách

`set` và `other` trong chương trình có cùng các phần tử nhưng được xây theo thứ tự khác nhau và thuộc hai lớp khác nhau. Hai chiều `equals` đều là `true`; mã băm cũng bằng nhau. Điều này giúp các cài đặt tuân thủ `Set` làm việc với nhau.

Trong khi đó, một `List` chứa đúng các mục ấy vẫn không bằng `Set`. Không dùng phép so sánh “cùng các mục” tùy ý để xóa bỏ ranh giới ngữ nghĩa giữa danh sách và tập hợp.

So sánh ba hướng:

| Nền lớp | Ý nghĩa bằng nhau kế thừa | Thứ tự có ảnh hưởng không? |
| --- | --- | --- |
| `AbstractCollection` | Không tự thêm quy tắc nội dung; nếu lớp con không đổi thì theo `Object` | Không có một quy tắc so sánh nội dung chung được áp đặt tại đây. |
| `AbstractList` và `AbstractSequentialList` | Bằng một `List` khác khi cùng độ dài và từng cặp phần tử tương ứng bằng nhau | Có. |
| `AbstractSet` | Bằng một `Set` khác khi cùng các phần tử và cùng số lượng | Không. |

Mã băm của danh sách tích lũy theo thứ tự với hệ số 31; mã băm của tập hợp là tổng mã băm các phần tử, với `null` đóng góp 0. Không đổi một công thức thành công thức kia chỉ vì dữ liệu lưu nền giống nhau.

Nếu tự định nghĩa so sánh theo nội dung cho một nhóm không phải `List` hoặc `Set`, cần giữ tính đối xứng và quy tắc mã băm. Không thể cho nhóm ấy tự nhận bằng một danh sách trong khi danh sách lại từ chối bằng nhóm ấy. Trong ví dụ `Bag`, giữ hành vi `Object` tránh tự đặt thêm một quy tắc ngoài mục tiêu minh họa.

Tính duy nhất cũng phụ thuộc quan hệ bằng nhau của phần tử. Nếu thay đổi các trường dùng trong `equals` khi đối tượng còn trong tập hợp, bạn có thể làm những phần tử trước đó khác nhau trở nên bằng nhau. Chọn phần tử có giá trị ổn định hoặc xác định một chính sách cập nhật rõ ràng; lớp khung không theo dõi mọi sửa đổi bên trong đối tượng.

### 6.5. removeAll có thể đi theo hai đường khác nhau

`AbstractSet.removeAll` xem xét kích thước của hai collection để chọn một trong các hướng:

- Duyệt đối số rồi gọi `remove(Object)` của tập hợp nhận.
- Hoặc duyệt tập hợp nhận, kiểm tra đối số có chứa mục hay không, rồi xóa bằng bộ duyệt của tập hợp nhận.

Chi tiết được thể hiện trong [AbstractSet.removeAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSet.html#removeAll(java.util.Collection)) và [mã OpenJDK 17u](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/AbstractSet.java).

Vì vậy, nếu chỉ viết một `remove(Object)` riêng nhưng bộ duyệt không cho xóa, bạn chưa thể kết luận mọi đầu vào của `removeAll` sẽ hoạt động. Đường được chọn còn phụ thuộc dữ liệu. Muốn công bố hỗ trợ thao tác ấy, cần bảo đảm các phụ thuộc phù hợp hoặc ghi đè nó bằng một cài đặt đúng.

`TinySet` trả bộ duyệt của danh sách nền có hỗ trợ xóa, nên các đường sửa đang dùng đều có nền tương ứng. Sau `removeAll` xóa `alpha`, bộ duyệt lấy `beta` rồi xóa nó, để lại `null`.

Đây cũng là ví dụ vì sao không nên đoán thuật toán chỉ từ tên phương thức. Cùng một thao tác giao diện có thể được lớp khung chuyên biệt hóa để phù hợp hơn với loại collection.

### 6.6. Tính đúng của Set không kéo theo chi phí của HashSet

Trong `TinySet`, `contains` kế thừa duyệt các phần tử. Thêm một mục mới cần kiểm tra trùng, nên chi phí tăng theo số mục hiện có. Thêm hàng loạt nhiều mục khác nhau có thể có tổng chi phí bậc hai.

Mã băm của tập hợp cần duyệt phần tử. So sánh hai tập hợp còn phụ thuộc chi phí tìm trong phía được hỏi; không được mặc định mọi `Set` đều có tra cứu thời gian hằng số.

Một cấu trúc nhỏ dựa trên danh sách có thể thích hợp để học hoặc phục vụ một miền dữ liệu rất nhỏ. Nếu yêu cầu chính là tra cứu lớn, hãy chọn một cấu trúc lưu phù hợp. Kế thừa `AbstractSet` tái sử dụng hợp đồng và thuật toán nền, không tự chọn cấu trúc dữ liệu thay bạn.

## 7. Rà soát một cài đặt tùy chỉnh trước khi công bố

### 7.1. Kiểm tra cả phương thức kế thừa

Đừng chỉ thử `add` rồi `get`. Hãy thử những đường mà người dùng có thể gọi gián tiếp:

- Với `AbstractCollection`: `contains`, `remove(Object)`, `addAll`, `retainAll`, `removeIf`, `clear` và chuyển sang mảng.
- Với danh sách: bộ duyệt thường, `ListIterator`, thêm ở đầu/cuối, biên chỉ số, `subList` và xóa một đoạn.
- Với tập hợp: thêm đối tượng bằng nhau nhưng khác tham chiếu, hàm tạo nhận đầu vào có trùng, `addAll`, so sánh với một cài đặt `Set` chuẩn và mã băm.
- Với kiểu chỉ đọc: thử cả thao tác thực sự cần đổi dữ liệu; không chỉ thử các trường hợp không làm gì.

Các phương thức hàng loạt mặc định thường thực hiện từng bước. Nếu một bước ném ngoại lệ, đừng tự suy ra mọi thay đổi trước đó đã được hoàn tác. Nếu bài toán cần kiểm tra toàn bộ đầu vào trước khi sửa, hoặc cần bảo đảm nguyên tử giữa các luồng, phải thiết kế riêng yêu cầu ấy.

Bốn lớp khung không tự cung cấp một chính sách an toàn cho truy cập đồng thời. Dùng bộ đếm thay đổi hay bắt `ConcurrentModificationException` không thay thế việc đồng bộ hóa.

### 7.2. Chọn kế thừa khi cần công bố đúng hợp đồng

Nếu mục tiêu là làm một `List` hoặc `Set` tùy chỉnh để tham gia vào các thuật toán của Collections Framework, lớp khung có giá trị vì tái sử dụng đúng ngữ nghĩa.

Nếu mục tiêu chỉ là giữ một danh sách riêng bên trong một đối tượng nghiệp vụ, bạn có thể dùng một collection có sẵn bằng cách chứa nó trong lớp và chỉ công bố các thao tác cần thiết. Không cần biến mọi lớp có nhiều phần tử thành một collection kế thừa.

Ví dụ trong bài cố ý cho thấy cả hai cơ chế cùng xuất hiện: lớp cụ thể kế thừa lớp khung, còn việc lưu trữ có thể dùng một đối tượng nền. Nhưng khi thiết kế API thật, phải quyết định bạn muốn công bố toàn bộ hợp đồng collection hay chỉ một nhóm hành vi nghiệp vụ hẹp hơn.

### 7.3. Ghi đè để tối ưu nhưng giữ hợp đồng

Một điểm ghi đè tốt có thể giúp nhiều thao tác kế thừa cùng nhanh hơn: `contains` cho một kho có chỉ mục phù hợp, `removeRange` cho mảng, hoặc `addAll` khi có thể sao chép một đoạn hiệu quả.

Mỗi tối ưu phải giữ các điều kiện quan sát được: kết quả trả về, thứ tự với danh sách, không trùng với tập hợp, xử lý chỉ số và phần tử không hợp lệ, chính sách sửa, bộ duyệt và khung nhìn. “Chạy nhanh hơn” không bù được việc một đường thêm lách qua kiểm tra trùng hay một thay đổi cấu trúc không được ghi nhận.

Khi phân tích hiệu năng, lần theo cả chuỗi gọi. Một phương thức kế thừa có vẻ ngắn vẫn có thể gọi lặp một thao tác tuyến tính. Ngược lại, việc cùng thực hiện giao diện `List` không buộc hai cấu trúc phải có cùng chi phí.

## 8. Thực hành và tự kiểm tra có lời giải

### Bài 1. Chỉ đọc với AbstractCollection

Bạn viết đúng `iterator()` và `size()`, không ghi đè `add`. Bộ duyệt không hỗ trợ xóa. `contains(x)`, `add(x)`, `remove(x)` sẽ ra sao?

**Lời giải:** `contains` có thể hoạt động dựa trên việc duyệt. `add` mặc định ném `UnsupportedOperationException`. `remove` có thể trả `false` khi không tìm thấy mục; nếu tìm thấy và cần gọi thao tác xóa không được hỗ trợ của bộ duyệt, nó ném ngoại lệ. Không suy luận khả năng sửa từ một lần xóa mục vắng mặt.

**Thực hành:** với `ReadOnlyBag` ở chương trình đầu, thử một `removeIf` không khớp mục nào và một điều kiện khớp `A`. Giải thích khác biệt theo đường gọi `Iterator.remove`, không theo tên phương thức.

### Bài 2. Danh sách cố định kích thước

Một lớp con của `AbstractList` cung cấp `get`, `size`, `set`, nhưng giữ nguyên hành vi mặc định của `add(int,E)` và `remove(int)`. Có thể thay phần tử qua `ListIterator` không? Có thể thêm qua bộ duyệt không?

**Lời giải:** có thể thay khi trạng thái bộ duyệt cho phép `set`, vì nó gọi `set` của lớp con. Không thể thêm qua bộ duyệt vì thao tác nền `add(int,E)` chưa được hỗ trợ. Khả năng thay giá trị không đồng nghĩa với khả năng đổi kích thước.

**Thực hành:** tạo một danh sách cố định từ bản sao một mảng, triển khai ba phương thức đọc/thay nói trên. Kiểm tra kích thước giữ nguyên và các chỉ số biên được xử lý đúng.

### Bài 3. Hai lần tăng modCount trong một lần thêm

`add(int,E)` gọi hàm trợ giúp; cả hai nơi đều tăng `modCount`. Vì sao bộ duyệt có thể báo lỗi dù bạn chỉ gọi `listIterator.add(e)`?

**Lời giải:** bộ duyệt kế thừa có cơ chế theo dõi số lần thay đổi cấu trúc mong đợi. Một thao tác đơn bị đếm hai lần có thể làm cơ chế này nhận nhầm sự chênh lệch. Chọn một nơi duy nhất ghi nhận lần thêm thành công. Không tăng chỉ vì gọi một hàm trợ giúp hoặc vì yêu cầu thêm đã được gửi tới.

**Thực hành:** đọc đường gọi của `ArrayBuffer.add` và xác định dòng nào thực sự hoàn tất cập nhật cấu trúc. Giải thích vì sao `set` không tăng bộ đếm trong cài đặt này.

### Bài 4. Dữ liệu liên kết nhưng dùng bộ duyệt dựa trên get

Bạn kế thừa `AbstractList` và viết `get(i)` bằng cách đi từ nút đầu đến vị trí `i`. `for-each` có chắc là tuyến tính không?

**Lời giải:** không. Bộ duyệt kế thừa có thể gọi `get` theo từng chỉ số. Tổng số bước đi qua nút có thể là `1 + 2 + ... + n`, tức bậc hai. Chọn nền `AbstractSequentialList` với bộ duyệt đi tiếp hiệu quả, hoặc tự cung cấp bộ duyệt phù hợp và rà soát các đường còn lại.

**Thực hành:** ở chương trình tuần tự, tăng số phần tử rồi quan sát số lần bắt đầu bộ duyệt. Phân biệt số lần gọi đã đo được với số nút phải đi qua và thời gian chạy thực tế.

### Bài 5. Set chống trùng trong add nhưng không chống trong hàm tạo

Một lớp con `AbstractSet` kiểm tra trùng trong `add`, nhưng hàm tạo dùng thẳng `new ArrayList<>(source)`. Nếu `source` có hai mục bằng nhau, lớp đã đúng hợp đồng chưa?

**Lời giải:** chưa. Dữ liệu được tạo ra đã vi phạm tính duy nhất trước khi ai gọi `add` trên đối tượng mới. Cần đưa đầu vào qua một đường giữ bất biến, hoặc tự lọc đúng trong quá trình dựng. `TinySet` dùng `addAll` đi qua `add`.

**Thực hành:** thử đầu vào có cùng tham chiếu lặp lại, hai đối tượng khác tham chiếu nhưng bằng nhau, và nhiều `null` nếu chính sách cho phép. Kiểm tra cả kích thước lẫn nội dung.

### Bài 6. Dùng cùng công thức mã băm cho List và Set

Bạn muốn dùng tổng mã băm phần tử cho mọi collection để tái sử dụng thêm. Có giữ đúng hợp đồng danh sách không?

**Lời giải:** không thể thay tùy ý công thức chuẩn của `List`. Danh sách có so sánh theo thứ tự và công thức mã băm tương ứng; tập hợp có so sánh không phụ thuộc thứ tự và công thức riêng. Hai danh sách bằng nhau phải tương thích mã băm với những cài đặt `List` khác, không chỉ với lớp tự viết.

**Thực hành:** so sánh một danh sách tùy chỉnh với `List.of("A","B")` ở cả hai chiều, rồi kiểm tra mã băm. Làm tương tự với `TinySet` và `HashSet`, cố ý đổi thứ tự thêm.

### Bài 7. Chỉ viết remove(Object) cho tập hợp

Bộ duyệt không hỗ trợ xóa, nhưng `remove(Object)` đã được ghi đè và hoạt động. Có thể công bố rằng `removeAll` kế thừa luôn được hỗ trợ không?

**Lời giải:** chưa đủ. `AbstractSet.removeAll` có thể chọn đường xóa qua bộ duyệt tùy kích thước. Phải hỗ trợ các thao tác nền mà thuật toán có thể dùng hoặc ghi đè `removeAll` phù hợp với khả năng đã công bố.

**Thực hành:** thiết kế hai đầu vào làm tập hợp nhận lớn hơn và nhỏ hơn đối số. Theo dõi đường nào được chọn trong mã nguồn đang dùng; không chỉ kiểm tra một kích thước rồi suy ra mọi trường hợp.

## 9. Kiểm chứng và tài liệu đối chiếu

Bốn chương trình trong bài được biên dịch với `javac --release 17 -encoding UTF-8 -Xlint:all`, chạy trên OpenJDK 24.0.2 và cho đúng các khối đầu ra đã nêu. Mức `--release 17` kiểm tra cú pháp/API theo Java 17 lúc biên dịch; nó không có nghĩa lần chạy kiểm chứng đã dùng JVM 17.

Các quan sát như số lần bắt đầu bộ duyệt, thứ tự in của `TinySet` và việc tăng `modCount` thuộc đúng các lớp ví dụ đã viết. Những đường tái sử dụng được đối chiếu với hợp đồng Java 17; khi lớp con ghi đè thuật toán, cần phân tích lại cài đặt thực tế ấy.

Nguồn chính để tra cứu:

- [AbstractCollection — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractCollection.html): nền duyệt, thêm/xóa tùy chọn và thuật toán kế thừa.
- [AbstractList — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html): thao tác theo chỉ số, bộ duyệt, `modCount`, `removeRange` và khung nhìn.
- [AbstractSequentialList — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSequentialList.html): cách xây thao tác theo chỉ số từ bộ duyệt danh sách.
- [AbstractSet — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSet.html): ràng buộc tập hợp, so sánh, mã băm và `removeAll`.
- [Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html), [List](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html), [Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html): hợp đồng phải giữ dù chọn cách lưu nào.
- [ListIterator — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ListIterator.html): con trỏ, trạng thái sửa và ngoại lệ.
- [Mã AbstractList trong OpenJDK 17u](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/AbstractList.java) và [mã AbstractSet](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/AbstractSet.java): đối chiếu chi tiết đường gọi của cài đặt nền; không thay thế hợp đồng của mọi lớp con.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-collections-003">AbstractQueue và AbstractMap</a>

<details>
<summary>Xem chi tiết</summary>

# AbstractQueue và AbstractMap

## 1. Lớp khung cung cấp hành vi, cài đặt con cung cấp nền dữ liệu

Khi viết collection tùy chỉnh, phần khó không chỉ nằm ở chỗ lưu phần tử. Bên gọi còn trông đợi nhiều phương thức có quan hệ nhất quán: thêm, xóa, duyệt, xem nội dung, chuyển thành mảng, so sánh và thao tác qua các view.

Các lớp cài đặt khung (skeletal implementation) giảm công việc lặp bằng cách xây nhiều phương thức từ một số thao tác nền. `AbstractQueue` và `AbstractMap` là hai ví dụ:

- `AbstractQueue` xây một số thao tác Queue trên `offer`, `poll`, `peek`.
- `AbstractMap` xây nhiều thao tác Map trên cách nhìn các cặp khóa–giá trị qua `entrySet()`.

Chúng không chọn sẵn mọi thứ: thứ tự lấy phần tử, giới hạn dung lượng, cấu trúc chứa khóa, khả năng sửa hay chi phí tra cứu vẫn cần được quyết định.

Mô hình chung:

```text
Hợp đồng giao diện
    ↓
Lớp khung: triển khai các phương thức dựa trên thao tác nền
    ↓
Lớp con: giữ dữ liệu và thực hiện đúng các thao tác nền
```

Một phương thức nền sai có thể làm nhiều phương thức kế thừa cùng sai. Ngược lại, khi nền đúng và phù hợp, lớp con có thể tái sử dụng khá nhiều hành vi mà không tự viết lại.

Bài dùng Java 17 trở lên. Ba chương trình độc lập lần lượt minh họa một queue FIFO có giới hạn, một map nhỏ có thể sửa và một map không cho sửa. Chúng tái sử dụng cấu trúc chứa có sẵn để tập trung vào quan hệ giữa các phương thức của lớp khung; không đề xuất thay các collection chuẩn bằng các lớp minh họa này trong mọi ứng dụng.

## 2. Vị trí trong hệ thống kiểu và trách nhiệm của lớp con

| Khía cạnh | AbstractQueue | AbstractMap |
|---|---|---|
| Giao diện chính | `Queue<E>` | `Map<K, V>` |
| Lớp cha trực tiếp | `AbstractCollection<E>` | `Object` |
| Trọng tâm của nền cài đặt | Thêm thử, lấy đầu, xem đầu, duyệt và đếm | Set-view của các liên kết khóa–giá trị |
| Dữ liệu lưu ở đâu? | Do lớp con chọn | Do lớp con chọn |
| Có tự cung cấp tính đồng thời? | Không | Không |
| Có tự chọn thứ tự? | Không | Không |
| Có thể kế thừa trực tiếp để tạo đối tượng? | Cần lớp con cụ thể | Cần lớp con cụ thể |

`Map` thuộc Java Collections Framework nhưng không phải giao diện con của `Collection`. Các tập khóa, tập entry và collection giá trị của map mới là các view collection.

Cũng không nên đồng nhất `AbstractQueue` với `BlockingQueue`. Kế thừa nó không tự tạo các phương thức `put`, `take` hay thao tác chờ có timeout.

Hai lớp khung cung cấp thuật toán mặc định chứ không tự làm dữ liệu của lớp con an toàn khi nhiều luồng truy cập. Các ví dụ có thể sửa trong bài được thiết kế cho cách dùng một luồng hoặc cách phối hợp bên ngoài đã được xác định.

## 3. AbstractQueue: từ giá trị báo thất bại đến ngoại lệ

### 3.1. Năm phương thức nền cần cung cấp

Một lớp con cụ thể tối thiểu cần thực hiện:

| Phương thức | Hợp đồng cần giữ |
|---|---|
| `offer(e)` | Thử thêm; thành công trả `true`, chưa thể thêm do giới hạn phù hợp thì trả `false`; không nhận `null` |
| `poll()` | Lấy và xóa đầu queue; rỗng trả `null` |
| `peek()` | Xem đầu queue mà không xóa; rỗng trả `null` |
| `size()` | Số phần tử hiện có theo hợp đồng của cài đặt |
| `iterator()` | Duyệt đúng dữ liệu theo thứ tự đã công bố |

`AbstractQueue` dùng kết quả `false` hoặc `null` để tạo các phiên bản báo ngoại lệ. Vì vậy, nó phù hợp với queue không lưu phần tử `null`. Xem [hợp đồng nền của AbstractQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractQueue.html).

Chính lớp con xác định “đầu queue” là phần tử nào. FIFO, ưu tiên hoặc LIFO có thể có quy tắc khác nhau. Không suy ra mọi queue kế thừa khung này đều FIFO.

`offer` trả `false` cho tình huống không thêm được theo chính sách thông thường, chẳng hạn đầy. Điều đó không yêu cầu chuyển mọi lỗi đầu vào thành `false`; giá trị không hợp lệ vẫn có thể phải báo ngoại lệ theo hợp đồng.

### 3.2. Quan hệ giữa các cặp phương thức

| Phương thức kế thừa | Thao tác nền được dùng | Khi thao tác chưa thành công |
|---|---|---|
| `add(e)` | `offer(e)` | `offer` trả `false` thì báo `IllegalStateException` |
| `remove()` | `poll()` | `poll` trả `null` thì báo `NoSuchElementException` |
| `element()` | `peek()` | `peek` trả `null` thì báo `NoSuchElementException` |

Đây là hai cách biểu đạt cùng nhu cầu Queue với chính sách thất bại khác nhau. Xem [các cặp phương thức của Queue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html).

Không cần tự override cả sáu phương thức chỉ để giữ quan hệ mặc định này. Nhưng nếu override một phương thức, phải giữ sự nhất quán với các phương thức còn lại.

Ví dụ, nếu `peek` trả phần tử B mà `poll` lại lấy A trong một trạng thái không thay đổi, bên gọi không còn có một ý nghĩa “đầu queue” nhất quán.

### 3.3. clear và addAll cũng dựa vào nền đã chọn

`clear()` mặc định gọi `poll()` lặp đến khi nhận `null`. Nếu cài `poll` chỉ xem mà không xóa, thao tác dọn có thể không tiến triển. Nếu `poll` ném lỗi khi rỗng thay vì trả `null`, đường dọn cũng không còn đúng với nền mà lớp khung yêu cầu.

`addAll` mặc định thêm từng phần tử theo đường `add`. Nếu một lần thêm ném lỗi, các phần tử trước đó có thể đã vào queue. Nó không phải một giao dịch tự hoàn tác tất cả.

```text
addAll([C, D, E])
    → add(C) → offer(C) → thành công
    → add(D) → offer(D) → thành công
    → add(E) → offer(E) → không còn chỗ → ngoại lệ

C và D có thể vẫn nằm trong queue.
```

Khả năng sửa qua iterator cũng quan trọng. `remove(Object)` là thao tác khác với `remove()`: phiên bản có đối tượng tìm phần tử phù hợp và có thể dựa vào iterator để xóa.

Nếu iterator không hỗ trợ xóa, không được mặc định mọi phép xóa kế thừa đều sẽ làm được. Cần công bố và kiểm tra đúng những thao tác được hỗ trợ.

## 4. Chương trình Queue: năm phương thức nền, nhiều hành vi kế thừa

Tệp `QueueSkeletonDemo.java`:

```java
import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class QueueSkeletonDemo {
    static final class BoundedFifo<E> extends AbstractQueue<E> {
        private final int capacity;
        private final ArrayDeque<E> elements = new ArrayDeque<>();

        BoundedFifo(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            this.capacity = capacity;
        }

        @Override
        public boolean offer(E element) {
            Objects.requireNonNull(element, "element");
            if (elements.size() == capacity) {
                return false;
            }
            return elements.offerLast(element);
        }

        @Override
        public E poll() {
            return elements.pollFirst();
        }

        @Override
        public E peek() {
            return elements.peekFirst();
        }

        @Override
        public int size() {
            return elements.size();
        }

        @Override
        public Iterator<E> iterator() {
            return elements.iterator();
        }
    }

    public static void main(String[] args) {
        var queue = new BoundedFifo<String>(2);
        System.out.println("offerA=" + queue.offer("A"));
        System.out.println("offerB=" + queue.offer("B"));
        System.out.println("peek=" + queue.peek());
        System.out.println("offerC=" + queue.offer("C"));
        try {
            queue.add("C");
        } catch (IllegalStateException ex) {
            System.out.println("fullAdd=" + ex.getClass().getSimpleName());
        }

        System.out.println("removeHead=" + queue.remove());
        System.out.println("removeB=" + queue.remove("B"));
        try {
            queue.addAll(List.of("C", "D", "E"));
        } catch (IllegalStateException ex) {
            System.out.println("bulkFailure=" + ex.getClass().getSimpleName());
        }
        System.out.println("afterBulk=" + queue);
        queue.clear();
        System.out.println("emptyPoll=" + queue.poll());
        try {
            queue.element();
        } catch (java.util.NoSuchElementException ex) {
            System.out.println("emptyElement=" + ex.getClass().getSimpleName());
        }

        queue.addAll(List.of("X", "Y"));
        Iterator<String> iterator = queue.iterator();
        System.out.println("iteratorNext=" + iterator.next());
        iterator.remove();
        System.out.println("afterIteratorRemove=" + queue);
        try {
            queue.offer(null);
        } catch (NullPointerException ex) {
            System.out.println("nullOffer=" + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 QueueSkeletonDemo.java
java QueueSkeletonDemo
```

Stdout:

```text
offerA=true
offerB=true
peek=A
offerC=false
fullAdd=IllegalStateException
removeHead=A
removeB=true
bulkFailure=IllegalStateException
afterBulk=[C, D]
emptyPoll=null
emptyElement=NoSuchElementException
iteratorNext=X
afterIteratorRemove=[Y]
nullOffer=NullPointerException
```

### 4.1. Biểu diễn và điều kiện của ví dụ

`BoundedFifo` chứa dữ liệu trong một `ArrayDeque` riêng, thêm cuối và lấy đầu. Dung lượng là giới hạn số phần tử do lớp bọc quản lý, không phải một lời hứa mảng bên trong chỉ có đúng chừng đó ô.

`offer` kiểm tra `null` trước khi xét đầy. Khi đủ chỗ nó thêm cuối; khi đã đạt giới hạn nó trả `false`. Các đường thêm hợp lệ đi qua phương thức này.

`poll` và `peek` cùng dùng đầu deque nhưng một bên xóa, một bên chỉ đọc. Iterator được lấy từ chính dữ liệu nền nên duyệt cùng nội dung và hỗ trợ xóa phần tử đã duyệt.

Không trả iterator của một bản sao nếu muốn phép xóa qua iterator tác động vào queue gốc.

### 4.2. Các kết quả thể hiện đường gọi nào?

A và B được nhận; C chưa vào được nên `offer` trả `false`. Khi gọi `add("C")`, chính lớp khung chuyển thất bại đó thành ngoại lệ.

`remove()` lấy A theo `poll`. `remove("B")` tìm B qua đường thao tác collection và xóa khỏi dữ liệu nền.

Sau khi queue đã rỗng, thêm C, D, E theo lô chỉ nhận được C và D. Ngoại lệ ở E không xóa ngược C, D. Kết quả `afterBulk=[C, D]` là một phần kiến thức cần quan sát, không phải lỗi của chương trình kiểm chứng.

`clear` làm dữ liệu rỗng qua đường lấy. Lần `poll` sau trả `null`, còn `element` báo ngoại lệ vì không có đầu queue.

Cuối cùng, iterator lấy X rồi xóa X, nên queue chỉ còn Y. Điều này kiểm chứng khả năng sửa của iterator có ảnh hưởng đến các phương thức kế thừa.

### 4.3. Những gì lớp khung chưa tự tạo

Ví dụ không có thao tác chờ, đồng bộ nhiều luồng hay chính sách ưu tiên. Nếu cần một queue có các khả năng đó, phải chọn cài đặt phù hợp hoặc thiết kế thêm cơ chế riêng.

Queue cũng không tự có quy tắc so sánh theo thứ tự phần tử giống `List`. Với lớp ví dụ không override `equals`/`hashCode`, không dùng hai queue cùng nội dung để suy ra chúng bằng nhau theo giá trị.

Hợp đồng Queue và cài đặt khung không phải lời hứa rằng mọi phép của một collection khác sẽ có cùng ngữ nghĩa.


## 5. AbstractMap: entrySet là nền cài đặt, không chỉ là kết quả để in

### 5.1. Phương thức trừu tượng trung tâm

`AbstractMap<K, V>` yêu cầu lớp con cung cấp `entrySet()`, trả về một `Set<Map.Entry<K, V>>` nhìn vào các liên kết của map. Từ đó, lớp khung cung cấp nhiều thao tác đọc và các view liên quan.

`put` mặc định không được hỗ trợ. Muốn hỗ trợ thêm hoặc thay liên kết, lớp con phải cung cấp đường `put` phù hợp. Muốn dùng các đường xóa mặc định, iterator của entry-set cần hỗ trợ xóa theo khả năng đã công bố. Xem [AbstractMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.html).

Điểm cần chú ý là `entrySet` trả về **view của map**, không phải một báo cáo chụp dữ liệu không liên hệ với nguồn. Một map có thể sửa mà trả ra một tập entry sao chép rời rạc sẽ không giữ được quan hệ view mà bên gọi được quyền dựa vào.

### 5.2. Những phương thức nào được xây từ entrySet?

| Phương thức hoặc nhóm | Nền mặc định cần xem xét |
|---|---|
| `size()` | Kích thước của entry-set |
| `isEmpty()` | Kết quả kích thước |
| `get(key)`, `containsKey(key)` | Duyệt các entry để tìm khóa |
| `containsValue(value)` | Duyệt các entry để tìm giá trị |
| `remove(key)` | Tìm entry rồi xóa qua iterator |
| `clear()` | Gọi `entrySet().clear()` |
| `putAll(map)` | Gọi `put` cho các liên kết nguồn |
| `keySet()`, `values()` | Các view dựa trên liên kết và iterator của map |
| `equals`, `hashCode`, `toString` | Nội dung các liên kết |

Bảng mô tả cài đặt mặc định của lớp khung. Một lớp con có thể override để dùng biểu diễn hiệu quả hơn, nhưng phải giữ nghĩa hợp đồng.

Đặc biệt, kế thừa `AbstractMap` không tự tạo một bảng băm. `get` mặc định duyệt entry-set; nếu cần tra cứu có chỉ mục, phải cung cấp biểu diễn và đường tra cứu tương ứng.

### 5.3. Entry-set phải giữ nghĩa của một Set các liên kết

Một entry gồm khóa và giá trị. Khi so một entry với entry khác, không chỉ xét khóa rồi bỏ qua giá trị.

Ví dụ, nếu map đang có `a → 20`:

- Xóa `"a"` qua `keySet` xét khóa.
- Xóa entry `("a", 999)` qua `entrySet` không được coi là khớp chỉ vì cùng khóa.
- Xóa entry `("a", 20)` qua `entrySet` có thể xóa đúng liên kết nếu phép xóa được hỗ trợ.

Map không được có hai liên kết độc lập cho cùng một khóa theo quy tắc nhận diện khóa đã công bố. Nguồn lưu entry là một list cũng phải tự giữ điều kiện ấy, vì `AbstractSet` không tự loại trùng giúp mọi biểu diễn bên dưới.

`entrySet` không phải cửa thêm cặp mới thay cho `put`. Phương thức thêm vào entry-set không được mở tùy ý chỉ vì kiểu trả về là `Set`; các khả năng của view phải đúng với hợp đồng Map.

### 5.4. get trả null chưa xác định khóa có tồn tại hay không

Nếu map cho phép giá trị `null`, hai trạng thái khác nhau đều có thể làm `get(k)` trả `null`:

```text
Không có liên kết cho k.
Có liên kết k → null.
```

Trong cách dùng tuần tự, `containsKey` giúp phân biệt. Trong cách dùng nhiều luồng, hai lần đọc riêng không tự trở thành một thao tác nguyên tử.

Khi tự viết `put`, không dùng riêng `get(key) == null` để kết luận chắc chắn khóa chưa tồn tại rồi thêm một entry nữa. Với giá trị null được phép, cách đó có thể tạo hai entry cho cùng khóa.

## 6. Chương trình Map: một view thật nối với dữ liệu nền

Tệp `MapSkeletonDemo.java`:

```java
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MapSkeletonDemo {
    static final class TinyMap<K, V> extends AbstractMap<K, V> {
        private final List<Entry<K, V>> entries = new ArrayList<>();
        private final Set<Entry<K, V>> entryView = new AbstractSet<>() {
            @Override
            public Iterator<Entry<K, V>> iterator() {
                return entries.iterator();
            }

            @Override
            public int size() {
                return entries.size();
            }

            @Override
            public void clear() {
                entries.clear();
            }
        };

        @Override
        public Set<Entry<K, V>> entrySet() {
            return entryView;
        }

        @Override
        public V put(K key, V value) {
            for (Entry<K, V> entry : entries) {
                if (Objects.equals(entry.getKey(), key)) {
                    return entry.setValue(value);
                }
            }
            entries.add(new SimpleEntry<>(key, value));
            return null;
        }
    }

    public static void main(String[] args) {
        var map = new TinyMap<String, Integer>();
        System.out.println("firstPut=" + map.put("a", 1));
        System.out.println("replaceA=" + map.put("a", 2));
        map.put("b", null);
        System.out.println("size=" + map.size());
        System.out.println("getB=" + map.get("b"));
        System.out.println("hasB=" + map.containsKey("b"));
        System.out.println("hasMissing=" + map.containsKey("missing"));

        Set<String> keys = map.keySet();
        var values = map.values();
        map.put("c", 3);
        System.out.println("liveKeys=" + keys);
        System.out.println("liveValues=" + values);

        Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
        Map.Entry<String, Integer> first = iterator.next();
        System.out.println("oldEntryValue=" + first.setValue(20));
        System.out.println("mapAfterEntryWrite=" + map);
        System.out.println("removeNullValue=" + values.remove(null));
        System.out.println("removeKeyC=" + keys.remove("c"));

        Map<String, Integer> reference = Map.of("a", 20);
        System.out.println("equalsForward=" + map.equals(reference));
        System.out.println("equalsBackward=" + reference.equals(map));
        System.out.println("sameHash=" +
                (map.hashCode() == reference.hashCode()));

        System.out.println("removeWrongEntry=" +
                map.entrySet().remove(new AbstractMap.SimpleImmutableEntry<>("a", 999)));
        System.out.println("removeExactEntry=" +
                map.entrySet().remove(new AbstractMap.SimpleImmutableEntry<>("a", 20)));
        System.out.println("emptyAfterEntryRemove=" + map.isEmpty());
        map.put(null, 7);
        System.out.println("nullKeyValue=" + map.get(null));
        map.clear();
        System.out.println("sizeAfterClear=" + map.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapSkeletonDemo.java
java MapSkeletonDemo
```

Stdout:

```text
firstPut=null
replaceA=1
size=2
getB=null
hasB=true
hasMissing=false
liveKeys=[a, b, c]
liveValues=[2, null, 3]
oldEntryValue=2
mapAfterEntryWrite={a=20, b=null, c=3}
removeNullValue=true
removeKeyC=true
equalsForward=true
equalsBackward=true
sameHash=true
removeWrongEntry=false
removeExactEntry=true
emptyAfterEntryRemove=true
nullKeyValue=7
sizeAfterClear=0
```

### 6.1. Lớp con đã cài điều gì?

`TinyMap` giữ các entry trong một list riêng. Đây là biểu diễn nhỏ để nhìn rõ cơ chế, không phải bảng băm tối ưu.

`entryView` là một `AbstractSet`:

- `size` đọc từ list dữ liệu thật.
- Iterator duyệt chính list ấy và hỗ trợ `remove`.
- `clear` gọi trực tiếp `entries.clear()`.
- Thêm entry trực tiếp không được mở; lớp không override `add` của set-view.

`put` tìm khóa bằng `Objects.equals`. Nếu thấy khóa, nó thay giá trị của entry hiện có và trả giá trị cũ. Nếu chưa thấy, nó thêm một `SimpleEntry` mới.

Vì chỉ có `put` tạo liên kết mới và nó đã kiểm tra khóa, biểu diễn giữ được điều kiện không có hai liên kết cho cùng khóa. `SimpleEntry` cung cấp phần so sánh entry theo cả khóa và giá trị.

Lớp không tự override `get`, `containsKey`, `keySet`, `values`, `equals` hay `hashCode`. Các kết quả ở stdout đi qua nhiều đường mặc định của khung.

### 6.2. Cập nhật một khóa không làm tăng số liên kết

Lần đầu đặt `a → 1` trả `null`; đặt lại `a → 2` trả 1. Sau khi thêm `b → null`, kích thước là 2.

Khóa b có mặt dù `get("b")` trả `null`. `containsKey("b")` là `true`, còn khóa `"missing"` không có.

Ví dụ cũng kiểm tra khóa `null`, vì cài đặt này chủ ý cho phép nó và dùng phép so sánh phù hợp. Một cài đặt khác có thể không cho phép null, nhưng phải đưa ra chính sách và thực hiện nhất quán.

### 6.3. keySet và values là view đang nối với map

`keys` và `values` được lấy trước khi thêm c. Chúng vẫn phản ánh c khi được duyệt sau lần thêm, chứng tỏ đây không phải hai bản sao độc lập.

Xóa giá trị `null` qua `values.remove(null)` làm mất cả liên kết tương ứng, không chỉ đổi giá trị của nó sang một giá trị khác. Xóa c qua `keys.remove("c")` cũng làm mất liên kết c.

`values()` là collection, không phải set: nhiều khóa khác nhau có thể cùng trỏ đến các giá trị bằng nhau. Phép xóa một giá trị phù hợp không có nghĩa mọi liên kết có cùng giá trị đều tự bị xóa.

Các view khóa và giá trị hỗ trợ các đường xóa phù hợp khi map hỗ trợ, nhưng không cung cấp `add` như một cách tạo liên kết thiếu thông tin. Chỉ một khóa chưa đủ xác định giá trị, và chỉ một giá trị chưa đủ xác định khóa.

Quan hệ giữa map và các view là một phần của [hợp đồng Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#entrySet()), không phải tiện ích phụ chỉ phục vụ in dữ liệu.

### 6.4. Entry.setValue là một đường cập nhật riêng

Trong ví dụ, entry đầu được lấy qua iterator, rồi `setValue(20)` đổi trực tiếp giá trị mà map đang dùng. Nó trả giá trị cũ là 2.

Một entry có thể hỗ trợ hoặc không hỗ trợ sửa giá trị. Nếu cài đặt hỗ trợ thì phải giữ đúng quan hệ với map trong phạm vi sử dụng entry hợp lệ. Đừng hứa một entry đã lấy sẽ luôn giữ liên hệ sau mọi lần map thay đổi hay sau khi entry bị loại. Xem [hợp đồng Map.Entry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html).

Vì ví dụ lưu chính các `SimpleEntry` trong list, thao tác thay giá trị tác động vào liên kết thật. Nếu trả entry sao chép rời mà vẫn cho `setValue`, người gọi có thể sửa bản sao mà map không đổi, trái với điều họ trông đợi ở entry của view hỗ trợ sửa.

Khả năng `put`, khả năng xóa và khả năng sửa qua entry cần được quyết định riêng. Một cài đặt không bắt buộc phải hỗ trợ mọi phép tùy chọn chỉ vì hỗ trợ một phép khác, nhưng không được để các đường API đưa ra những lời hứa mâu thuẫn.

### 6.5. Xóa một entry cần khớp cả khóa và giá trị

Khi map còn `a → 20`, xóa entry `a → 999` trả `false`; xóa entry `a → 20` trả `true`.

Đường này dùng khả năng iterator của entry-set có thể xóa phần tử trong list nền. Nếu iterator chỉ duyệt một bản sao hoặc không hỗ trợ xóa, không thể coi các đường xóa mặc định đã hoạt động đầy đủ.

Đối chiếu `equals` theo cả hai chiều với `Map.of("a", 20)` cùng hash bằng nhau kiểm chứng một phần nghĩa Map được kế thừa. Không cần hai đối tượng thuộc cùng lớp cài đặt mới có thể bằng nhau theo nội dung.

### 6.6. Chi phí của biểu diễn vẫn thuộc trách nhiệm lớp con

Với list entry trong ví dụ, tìm khóa và thêm/thay một khóa phải xét lần lượt nhiều liên kết. `get` mặc định cũng tìm như vậy. Không có chỉ mục băm được tạo tự động.

`entryView.clear` được override để gọi đường dọn của list trực tiếp. Nếu chỉ dựa vào xóa từng mục bằng iterator trên một list phải dồn phần tử, có thể phát sinh nhiều công việc lặp không cần thiết.

Đây là một điểm mở rộng có mục đích: giữ nguyên nghĩa view nhưng thay đường thực hiện cho phù hợp biểu diễn. Tối ưu hợp lệ phải bắt đầu từ việc xác định phương thức nào đang gọi phương thức nào, thay vì override ngẫu nhiên nhiều phương thức cùng một lúc.


## 7. Map không cho sửa: phải kiểm soát cả entry và view

### 7.1. Chỉ không override put chưa đóng mọi đường thay đổi

`AbstractMap.put` mặc định ném `UnsupportedOperationException`. Điều đó chưa đủ để kết luận map chỉ đọc nếu entry-set của lớp con vẫn cho xóa hoặc các entry vẫn cho đổi giá trị.

Các đường cần xem gồm:

```text
map.put(...)
map.remove(...)
map.clear()
map.entrySet().iterator().remove()
entry.setValue(...)
map.keySet().remove(...)
map.values().remove(...)
```

Nếu muốn không cho thay đổi, nguồn entry phải có các giới hạn phù hợp trên tất cả đường đã phơi bày.

Một `Set<Map.Entry<K,V>>` không cho thêm/xóa phần tử chưa chắc ngăn được `setValue` của những đối tượng entry bên trong. Giống một list không cho sửa vẫn có thể chứa đối tượng con thay đổi được, lớp bao của set không tự làm mọi entry bất biến.

### 7.2. Chương trình đóng cả đường sửa entry

Tệp `ReadOnlyMapSkeletonDemo.java`:

```java
import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ReadOnlyMapSkeletonDemo {
    static final class FrozenMap<K, V> extends AbstractMap<K, V> {
        private final Map<K, V> frozen;

        FrozenMap(Map<? extends K, ? extends V> source) {
            frozen = Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return frozen.entrySet();
        }
    }

    public static void main(String[] args) {
        var source = new LinkedHashMap<String, Integer>();
        source.put("x", 1);
        var frozen = new FrozenMap<>(source);
        source.put("y", 2);
        System.out.println("frozen=" + frozen);
        System.out.println("hasSourceAddition=" + frozen.containsKey("y"));

        try {
            frozen.put("z", 3);
        } catch (UnsupportedOperationException ex) {
            System.out.println("put=" + ex.getClass().getSimpleName());
        }
        try {
            frozen.entrySet().iterator().next().setValue(9);
        } catch (UnsupportedOperationException ex) {
            System.out.println("entryWrite=" + ex.getClass().getSimpleName());
        }
        try {
            frozen.keySet().remove("x");
        } catch (UnsupportedOperationException ex) {
            System.out.println("keyRemove=" + ex.getClass().getSimpleName());
        }
        System.out.println("unchanged=" + frozen);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ReadOnlyMapSkeletonDemo.java
java ReadOnlyMapSkeletonDemo
```

Stdout:

```text
frozen={x=1}
hasSourceAddition=false
put=UnsupportedOperationException
entryWrite=UnsupportedOperationException
keyRemove=UnsupportedOperationException
unchanged={x=1}
```

Constructor tạo một `LinkedHashMap` riêng từ nguồn rồi bọc bằng `Collections.unmodifiableMap`. `entrySet` được trả từ map đã được bọc đúng cách, nên các entry nhìn qua đó cũng không cung cấp đường sửa giá trị vào dữ liệu nền. Xem [unmodifiableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableMap(java.util.Map)).

Lớp con chỉ cài `entrySet`; các phép đọc còn lại đi qua khung. `put` mặc định bị từ chối, `setValue` trên entry bị từ chối và xóa khóa x qua view cũng bị từ chối.

Nguồn bên ngoài thêm y sau khi tạo đối tượng không làm map riêng xuất hiện y. Điều này đến từ bước sao chép, không chỉ từ việc bọc không cho sửa.

Ví dụ phục vụ học cách nối một entry-set an toàn vào khung. Nếu chỉ cần một map không cho sửa trong ứng dụng, có thể dùng trực tiếp công cụ có sẵn; không phải lúc nào cũng cần thêm một lớp kế thừa.

### 7.3. Sao chép cấu trúc chưa là sao chép sâu

Bước tạo map mới vẫn giữ các tham chiếu khóa và giá trị. Nếu các đối tượng đó có thể thay đổi, chúng không tự bị đóng băng.

Đặc biệt, khóa phải giữ quy tắc nhận diện phù hợp trong thời gian được dùng làm khóa. Việc chỉ đọc qua một tham chiếu map không sửa được một đối tượng khóa mà nơi khác tiếp tục thay đổi.

Ví dụ dùng khóa chuỗi và giá trị số nguyên để tránh trộn thêm vấn đề dữ liệu con khi quan sát các đường ghi.

### 7.4. Đừng dựa vào entry sau mọi thay đổi của nguồn

Một entry của map có quy tắc sử dụng gắn với view và việc duyệt. Nếu cần một bản ghi độc lập, phải tạo bản sao entry phù hợp; đừng giữ entry rồi mặc định nó luôn là một tay cầm cập nhật hợp lệ sau mọi thao tác cấu trúc.

Cũng không được trả bản sao rời khi API đang hứa một view có thể sửa xuyên về nguồn. Cần chọn đúng giữa “entry độc lập để đọc” và “entry của view map” rồi giữ nhất quán.

## 8. Những lỗi nền làm nhiều phương thức cùng hỏng

### 8.1. Vòng gọi giữa size và entrySet.size

Một map có thể biên dịch được nhưng không thể tính kích thước nếu hai phía gọi lại nhau:

```text
map.size()
    → entrySet().size()
        → map.size()
            → entrySet().size()
                → tiếp tục không có thao tác đọc dữ liệu thật
```

Đó là một vòng đệ quy, không phải hai cách tái sử dụng hợp lệ.

Trong `TinyMap`, `entryView.size` gọi trực tiếp `entries.size`. Đây là điểm chạm dữ liệu thật, giúp đường gọi kết thúc.

Một lỗi cùng nhóm là viết iterator của entry-set bằng cách duyệt `keySet()` của chính map, trong khi `keySet()` mặc định lại xây trên iterator entry-set. Cần vẽ đồ thị phụ thuộc trước khi nối các view qua nhau.

### 8.2. Trả snapshot thay cho live view của một map có thể sửa

Nếu mỗi `entrySet` chỉ trả một tập entry sao chép không nối với nguồn, một view đã lấy trước đó có thể không phản ánh lần thêm/xóa sau. Nếu cho phép xóa qua tập đó, việc xóa cũng chỉ làm đổi bản sao.

Không bắt buộc các lời gọi phải trả cùng danh tính đối tượng view trong mọi cài đặt. Điều cần giữ là quan hệ dữ liệu và khả năng thao tác mà hợp đồng đã hứa.

Trong `TinyMap`, một `entryView` được giữ ổn định và đọc trực tiếp list nền. Trong map chỉ đọc, view trỏ đến map riêng không cho sửa. Cả hai đều tránh một bản sao rời không phù hợp với nghĩa của view.

### 8.3. Lẫn peek với poll hoặc dùng null như dữ liệu hợp lệ của nền Queue

Nếu `peek` xóa phần tử, một lần xem có thể làm mất dữ liệu trước khi người dùng gọi lấy. Nếu `poll` không xóa, `remove` và `clear` không thể giữ nghĩa đúng.

Nếu queue chứa `null`, lớp khung không còn phân biệt kết quả `poll` trả null vì rỗng hay vì một phần tử thật. Đó là lý do yêu cầu không nhận null phải được thực hiện ở đường thêm.

Không trả một phần tử tùy ý để tránh giá trị null khi rỗng. Giá trị báo thất bại phải đúng theo hợp đồng.

### 8.4. Map trùng khóa do chỉ nhìn giá trị

Map cho phép null cần phân biệt “không có khóa” và “có khóa với giá trị null”. Đường `put` chỉ dựa vào kết quả `get` có thể thêm trùng liên kết nếu xử lý sai trường hợp này.

Ví dụ `TinyMap` tìm chính khóa trong các entry. Khi tìm thấy nó thay giá trị, không tạo thêm một entry chỉ vì giá trị cũ là null.

Tương tự, xóa một entry qua view phải xét đúng cả khóa và giá trị. So sánh theo khóa đơn thuần là nghĩa của một phép khác.

### 8.5. Khả năng sửa bị công bố không đồng nhất

Một map có thể hỗ trợ `put` nhưng không hỗ trợ sửa qua entry, hoặc có các phép tùy chọn khác bị từ chối. Điều quan trọng là công bố và giữ đúng mỗi khả năng.

Nếu chỉ cài `put` rồi mặc định `remove` cũng làm được, có thể gặp `UnsupportedOperationException` khi iterator entry-set không hỗ trợ xóa.

Nếu một queue cho phép `poll` nhưng iterator không xóa được, `clear` mặc định vẫn có đường qua `poll`; `remove(Object)` lại là đường khác. Không suy từ một phép xóa thành công sang mọi phép xóa đều được hỗ trợ.

Với các phép không làm thay đổi gì, chẳng hạn xóa khóa không có, cách báo ngoại lệ còn phụ thuộc hợp đồng và đường cài đặt cụ thể. Đừng viết một quy tắc kiểm tra quá rộng rằng mọi lời gọi có tên giống cập nhật đều bắt buộc ném lỗi trong mọi trạng thái.

## 9. Chi phí, phương thức mặc định và tính đồng thời

### 9.1. Tái sử dụng thuật toán không tự có cùng chi phí với HashMap

Trong `TinyMap`, `get` và `containsKey` mặc định phải duyệt list entry. `put` của ví dụ cũng tìm tuyến tính.

Nếu thêm rất nhiều khóa mới bằng `putAll`, mỗi lần `put` có thể tìm trên một map đang lớn dần. Việc lớp khung đã viết vòng lặp không làm tổng công việc ấy tự trở thành tuyến tính.

Các phép so sánh map cũng cần xét chi phí tra cứu của bên được đối chiếu. Một thao tác có vẻ chỉ duyệt một lần vẫn có thể gọi những phép đắt bên trong.

Nên đọc phần mô tả cài đặt của những phương thức được kế thừa, rồi override các điểm cần thiết khi biểu diễn mới có cách làm tốt hơn. Có thể đối chiếu [đường gọi của AbstractMap trong OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.base/share/classes/java/util/AbstractMap.java).

### 9.2. Nền iterator quyết định nhiều chi phí xóa

Iterator xóa trực tiếp trong một cấu trúc liên kết có thể có đặc tính khác iterator xóa trong một mảng phải dồn phần tử. Khi một phương thức mặc định lặp lại phép xóa, khác biệt đó có thể được nhân lên.

Đó là lý do `entryView.clear` trong ví dụ gọi `entries.clear` thay vì chủ động đi qua từng entry để xóa. Nó giữ đúng kết quả nhưng sử dụng đường dọn phù hợp với nguồn.

Với queue, `clear` mặc định dùng `poll`. Nếu biểu diễn có một cách dọn toàn bộ hiệu quả hơn và giữ đúng yêu cầu, lớp con có thể override. Không tối ưu bằng cách bỏ qua việc giải phóng tham chiếu hoặc làm các view trở nên sai.

### 9.3. Phương thức mặc định của giao diện vẫn là một phần của bề mặt sử dụng

Một lớp Map không chỉ được gọi qua các phương thức xuất hiện trong thân `AbstractMap`. Giao diện còn có những phương thức như `compute`, `merge`, `replaceAll` hoặc các phép cập nhật có điều kiện.

Ví dụ, một đường mặc định có thể cần `put`, `remove` hoặc `Entry.setValue`. Nếu khả năng nền không được hỗ trợ, phương thức tổng hợp cũng có thể không thực hiện được trong trường hợp cần thay đổi.

Không dùng việc lớp đã biên dịch để suy ra mọi phương thức giao diện đều đã được kiểm tra trên biểu diễn mới. Cần đọc hợp đồng và chọn những hành vi thật sự sẽ cung cấp cho bên dùng.

### 9.4. Lớp khung không cung cấp hợp đồng ConcurrentMap hoặc BlockingQueue

Một `get` rồi `put` trong `TinyMap` không tự trở thành một hành động nguyên tử. Các phương thức mặc định thông thường cũng không tự có bảo đảm đồng thời chỉ vì tên trông giống phương thức ở một lớp concurrent.

Tương tự, điều kiện “còn dung lượng” rồi thêm trong `BoundedFifo.offer` là logic cho cách dùng đã công bố. Gọi nó tự do từ nhiều luồng trên `ArrayDeque` riêng chưa được đồng bộ không phải một queue đồng thời hợp lệ.

Nếu cần hỗ trợ nhiều luồng, phải có một thiết kế riêng cho trạng thái, thao tác tổ hợp, iterator và vòng đời. Không thể thêm một vài `synchronized` rời rạc rồi bỏ qua các view hoặc các phương thức kế thừa còn lại.

## 10. Cách kiểm chứng một lớp con của hai khung

### 10.1. Với Queue

Một bộ kiểm tra có ý nghĩa nên bao phủ:

- Khi rỗng: `poll`/`peek` trả null và `remove`/`element` báo đúng ngoại lệ.
- Khi còn chỗ: `offer` và `add` đưa phần tử vào đúng thứ tự.
- Khi đầy nếu có giới hạn: `offer` báo false, `add` báo lỗi phù hợp.
- Null được từ chối đúng ở các đường thêm.
- `poll` thật sự xóa, `peek` thật sự không xóa.
- Iterator có đúng nội dung và khả năng sửa đã công bố.
- `clear`, `remove(Object)` và `addAll` đi qua các đường hợp lệ.
- Trạng thái sau khi thao tác theo lô thất bại được hiểu đúng.

Ví dụ trong bài đã chạy các đường tiêu biểu để người học đối chiếu. Nếu viết cài đặt mới, có thể so chuỗi thao tác với một mô hình tham chiếu có cùng chính sách giới hạn và thứ tự, thay vì chỉ kiểm tra các trường nội bộ.

### 10.2. Với Map

Cần bao phủ ít nhất:

- Thêm khóa mới và thay khóa đã có.
- Giá trị trả về của `put` và `remove`.
- Chính sách null cho khóa và giá trị.
- Không có hai liên kết cho cùng một khóa.
- `get`, `containsKey`, `containsValue`, `size` nhất quán.
- View đã lấy vẫn nhìn đúng nguồn sau các thao tác hợp lệ.
- Xóa qua key-set, values hoặc entry-set đúng với khả năng đã công bố.
- Entry có hoặc không có `setValue` theo đúng chính sách.
- `equals` đối xứng với map tham chiếu và hash tương ứng.
- `clear` và các phép theo lô không tạo trạng thái ngoài hợp đồng.

Không chỉ kiểm tra `put` và `get` rồi bỏ qua view: các view là nền của chính lớp khung nên lỗi ở đó có thể lan sang nhiều phương thức.

### 10.3. Điều ví dụ đã và chưa chứng minh

Ba chương trình trong bài đã được biên dịch và chạy với Java 17. Stdout thể hiện các nhánh chức năng cụ thể, bao gồm trạng thái sau lỗi có chủ ý và khả năng sửa xuyên qua view.

Các ví dụ không đo tốc độ, số byte bộ nhớ hay chứng minh một thuật toán đồng thời. Chúng cũng không thay thế việc kiểm tra sâu hơn nếu cấu trúc được mở rộng sang dữ liệu lớn, khả năng sửa mới hoặc nhiều luồng.

Giữ bằng chứng gắn với điều đã thực sự kiểm tra giúp quyết định lúc nào có thể tái sử dụng phương thức nền và lúc nào phải thiết kế lại.

## 11. Thực hành

### Bài 1: vẽ đường gọi trước khi override

Với queue ví dụ, hãy vẽ đường gọi cho:

1. `add("A")`.
2. `remove()`.
3. `element()`.
4. `clear()`.
5. `remove("A")`.

Vì sao mục 2 và mục 5 không nhất thiết có cùng đường thực hiện? Nếu bỏ khả năng `iterator.remove`, phép nào vẫn còn làm được và phép nào phải xem lại?

Hướng giải thích: `remove()` dựa vào `poll`, còn `remove(Object)` là thao tác tìm phần tử phù hợp. Các khả năng sửa phải được xét riêng.

### Bài 2: thêm theo lô khi chỉ còn hai chỗ

Queue đang rỗng, dung lượng 2. Gọi `addAll` với ba phần tử không null.

Hãy dự đoán ngoại lệ và trạng thái dữ liệu sau đó theo cài đặt mặc định của `AbstractQueue`. Nếu nghiệp vụ yêu cầu cả lô cùng vào hoặc không phần tử nào vào, cần thiết kế ranh giới nào thay vì giả định lớp khung sẽ hoàn tác?

Không sửa bằng cách âm thầm bỏ phần tử cuối mà báo thành công.

### Bài 3: sửa vòng đệ quy kích thước

Một lớp con giữ dữ liệu trong list `records`. `map.size()` dùng mặc định, nhưng `entrySet().size()` lại gọi `map.size()`.

Hãy chỉ ra vì sao không có điểm kết thúc. Thao tác nào cần chạm trực tiếp `records.size()`? Vì sao chỉ đổi tên một trong hai phương thức không giải quyết quan hệ gọi?

Hướng giải thích: cần một thao tác nền đọc trạng thái thật, không phải hai lớp view chỉ gọi lại nhau.

### Bài 4: map chỉ đọc bị sửa qua entry

Một lớp không override `put`, nhưng trả `Collections.unmodifiableSet` của một set chứa các `SimpleEntry` có thể sửa.

Nếu lấy một entry rồi gọi `setValue`, việc set không cho thêm/xóa có đủ chặn thay đổi giá trị không? Hãy đối chiếu với cách `FrozenMap` trong bài lấy entry-set từ map đã được bọc đúng cách.

Hướng giải thích: phải bảo vệ cả thành phần entry, không chỉ tư cách thành viên trong set.

### Bài 5: giá trị null và nhận diện khóa

Map có liên kết `b → null`. Một cài đặt `put` mới quyết định “nếu get(b) bằng null thì thêm entry mới”.

Vì sao đường đó có thể làm hỏng điều kiện khóa duy nhất? Cần tìm sự tồn tại theo khóa thế nào? Nếu cấm null thay vì hỗ trợ, cần thực hiện chính sách ở những đường nào?

Hướng giải thích: giá trị thiếu và khóa thiếu là hai khái niệm khác nhau khi map nhận null.

### Các câu hỏi cần trả lời trước khi công bố lớp con

1. Những thao tác nào là nền và chúng đọc dữ liệu thật ở đâu?
2. Những phương thức kế thừa nào gọi các thao tác đó?
3. Có vòng gọi qua view nào không?
4. Null, thứ tự và giá trị báo thất bại có nhất quán không?
5. Iterator và entry mở những đường sửa nào?
6. View nối với dữ liệu nguồn hay là bản sao theo hợp đồng nào?
7. Chi phí của phương thức mặc định có phù hợp với biểu diễn không?
8. Khả năng đồng thời và các thao tác tùy chọn đã được phát biểu đúng chưa?

Dùng `AbstractQueue` và `AbstractMap` hiệu quả là hiểu đồ thị gọi và giữ đúng nền cài đặt. Khi phần nền nhất quán, lớp khung giảm được nhiều mã lặp; khi phần nền sai, lỗi có thể xuất hiện ở những phương thức mà lớp con không tự viết.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-collections-004">Các phương thức tối thiểu và hợp đồng thao tác tùy chọn</a>

<details>
<summary>Xem chi tiết</summary>

# Các phương thức tối thiểu và hợp đồng thao tác tùy chọn

## Hiểu đúng chữ “tối thiểu” khi tự cài đặt collection

Khi tự cài đặt một collection, câu hỏi đầu tiên thường là: “Phải viết bao nhiêu phương thức?”. Câu trả lời phụ thuộc lớp nền và khả năng muốn cung cấp. Một danh sách chỉ đọc có điểm khởi đầu khác một danh sách thay đổi được kích thước; một map có các đường cập nhật qua view mà một collection thông thường không có.

Java Collections Framework cung cấp các lớp cài đặt khung, còn gọi là `skeletal implementation`: `AbstractCollection`, `AbstractList`, `AbstractSequentialList`, `AbstractSet`, `AbstractQueue` và `AbstractMap`. Ta viết một nhóm phương thức nền tảng, còn lớp khung xây dựng nhiều phương thức khác trên nhóm đó.

Mối quan hệ cần hiểu là:

~~~text
Hợp đồng của interface
    -> Lớp khung chọn một cách cài đặt chung
        -> Phương thức nền tảng của lớp con
            -> Hành vi của các phương thức kế thừa
~~~

Ví dụ, một `AbstractList` có thể tạo iterator từ `get(int)` và `size()`. Nếu lớp con cài `remove(int)`, thao tác xóa qua iterator kế thừa có thêm một đường thực hiện. Nếu `remove(int)` không được hỗ trợ, iterator vẫn duyệt được nhưng không vì thế mà xóa được.

Có hai mức tối thiểu khác nhau:

- **Đủ để trở thành lớp cụ thể:** các phương thức `abstract` cần có phần cài đặt, để trình biên dịch chấp nhận.
- **Đủ để thực hiện đúng lời hứa:** mọi phương thức đọc, ghi, iterator và view phải tuân thủ hợp đồng đã công bố, kể cả các phương thức kế thừa.

Mức thứ hai mới quyết định lớp có dùng đúng như một `List`, `Set`, `Queue` hay `Map` hay không. Biên dịch thành công không chứng minh không có phần tử trùng trong set, không làm map view trở thành bản sao hợp lệ, và không tự tạo đồng bộ cho nhiều luồng.

Bài dùng API Java 17 làm mốc. Khi nâng phiên bản Java, cần xem lại các phương thức interface mới và các phần cài đặt mặc định mới có ảnh hưởng đến lớp của mình hay không.

## Thao tác tùy chọn vẫn có hợp đồng

Một phương thức được ghi `optional operation` không có nghĩa người cài đặt được bỏ phương thức khỏi đối tượng hoặc tùy ý trả kết quả. Nó có nghĩa một triển khai có quyền không hỗ trợ khả năng đó; khi lời gọi yêu cầu khả năng không được hỗ trợ, ngoại lệ phù hợp là `UnsupportedOperationException`.

Ví dụ, một danh sách có kích thước cố định có thể hỗ trợ `set` nhưng không hỗ trợ `add` hoặc `remove`. Nó vẫn là một `List`. Một danh sách chỉ đọc có thể không hỗ trợ cả ba. Các lựa chọn này là một phần của hợp đồng của từng triển khai, không suy ra chỉ từ kiểu `List<E>`.

Cần phân biệt:

| Kết quả hoặc ngoại lệ | Ý nghĩa điển hình |
| --- | --- |
| `UnsupportedOperationException` | Triển khai không hỗ trợ thao tác được yêu cầu |
| `false` từ `Set.add` | Phần tử đã có nên set không đổi |
| `false` từ `Queue.offer` | Lần thêm không thực hiện được ngay, chẳng hạn queue đã đầy |
| `IndexOutOfBoundsException` | Chỉ số không thuộc miền hợp lệ |
| `IllegalStateException` từ iterator hỗ trợ xóa | Lời gọi xóa không hợp lệ với trạng thái duyệt hiện tại |
| `NoSuchElementException` từ `Iterator.next` | Đã hết phần tử |
| `NullPointerException` | Đối số hoặc phần tử `null` không hợp lệ với hợp đồng đang áp dụng |

Một `add` không được hỗ trợ không nên giả vờ trả `false` cho mọi phần tử. Một `set` không được hỗ trợ không nên im lặng bỏ qua rồi trả `null`. Những kết quả đó làm bên gọi hiểu sai rằng thao tác đã được xử lý theo hợp đồng.

Quy tắc thao tác tùy chọn và các hạn chế phần tử được nêu trong [Collection của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html). Tên phương thức giống nhau không bảo đảm mọi triển khai có cùng khả năng ghi.

### Không làm thay đổi dữ liệu có thể không ném ngoại lệ

Đây là điểm dễ làm một bài kiểm tra khả năng bị sai:

~~~java
collection.addAll(List.of());
~~~

Collection không hỗ trợ thêm có thể ném `UnsupportedOperationException`, nhưng với hợp đồng tổng quát, một lời gọi không có tác động cũng có thể kết thúc bình thường. Tương tự, xóa phần tử không tồn tại có thể trả `false`, dù xóa phần tử đang có sẽ ném ngoại lệ.

Vì thế:

- `addAll` với tập rỗng thành công không chứng minh `add` được hỗ trợ.
- `remove` trả `false` cho giá trị không có không chứng minh xóa giá trị đang có sẽ thành công.
- `removeIf` với điều kiện luôn `false` không chứng minh iterator hỗ trợ xóa.
- Một lần gọi `putAll` với map rỗng không chứng minh map có thể thêm ánh xạ mới.

Đừng dùng các lời gọi không làm gì để dò khả năng, rồi dựa vào kết quả đó quyết định có thể ghi dữ liệu thật. Collections Framework không có một phương thức chung như `supportsAdd()`. Bên sử dụng cần biết hợp đồng của đối tượng được nhận hoặc thiết kế đầu vào với khả năng rõ ràng.

Một triển khai có thể đưa ra bảo đảm mạnh hơn. Chẳng hạn, các list tạo bằng `List.of` công bố rằng các phương thức thay đổi đều ném `UnsupportedOperationException`. Không lấy bảo đảm riêng ấy áp ngược cho mọi lớp con tối thiểu của `AbstractList`. Xem phần danh sách không sửa được trong [List](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html).

## Chọn lớp khung và nhóm phương thức gốc

Bảng dưới đây là điểm khởi đầu, không thay cho việc đọc hợp đồng của mọi thao tác lớp muốn hỗ trợ.

| Lớp khung | Các phương thức nền tảng cần cung cấp | Để mở thêm khả năng ghi |
| --- | --- | --- |
| `AbstractCollection<E>` | `iterator()` và `size()` | Cài `add(E)` để thêm; iterator hỗ trợ `remove()` để nhiều thao tác xóa kế thừa dùng được |
| `AbstractSet<E>` | `iterator()` và `size()`, đồng thời bảo đảm không trùng theo hợp đồng set | Cài thêm/xóa mà vẫn giữ tính duy nhất |
| `AbstractList<E>` | `get(int)` và `size()` cho danh sách chỉ đọc | `set(int,E)` để thay phần tử; `add(int,E)` và `remove(int)` để đổi kích thước |
| `AbstractSequentialList<E>` | `listIterator(int)` và `size()` | Iterator hai chiều hỗ trợ `set`; thêm `add/remove` nếu đổi kích thước |
| `AbstractQueue<E>` | `offer(E)`, `peek()`, `poll()`, `iterator()` và `size()` | Quyết định riêng khả năng xóa qua iterator, xóa theo giá trị và các thao tác hàng loạt |
| `AbstractMap<K,V>` | `entrySet()` cho map chỉ đọc | `put(K,V)` để ghi; iterator của `entrySet` hỗ trợ xóa để các đường xóa kế thừa dùng được; `Entry.setValue` nếu hỗ trợ cập nhật qua entry |

Nếu cài trực tiếp một interface, phải cung cấp mọi phương thức trừ những phần đã được thừa kế hợp lệ, chẳng hạn các phương thức `default`. Lớp khung giảm số phương thức phải viết bằng cách chọn sẵn thuật toán chung.

Nhưng việc viết ít cũng tạo sự phụ thuộc: một sai sót trong `iterator` hoặc `get` có thể làm sai đồng thời `contains`, `toArray`, `equals`, `toString` và nhiều thao tác khác.

## AbstractCollection: đọc dựa trên iterator, xóa thường cũng dựa trên iterator

Với `AbstractCollection`, `size()` cho số lượng còn `iterator()` cho cách đi qua dữ liệu. Lớp khung có thể dùng iterator để tìm một giá trị, tạo mảng hoặc xây chuỗi hiển thị.

Đối với collection chỉ đọc, iterator vẫn phải:

- Trả `hasNext()` nhất quán với việc còn phần tử.
- Trả đúng phần tử tiếp theo từ `next()`.
- Ném `NoSuchElementException` khi `next()` được gọi sau phần tử cuối.
- Tuân thủ thứ tự nếu collection công bố thứ tự.
- Không để lộ một đường xóa ngoài ý muốn.

`Iterator.remove` có phần cài đặt mặc định ném `UnsupportedOperationException`. Vì vậy, một iterator chỉ viết `hasNext` và `next` có thể duyệt nhưng không hỗ trợ xóa. Xem [Iterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html).

Điểm cuối quan trọng với việc đóng gói. Nếu `iterator()` trả trực tiếp iterator của một `ArrayList` nội bộ, bên gọi có thể xóa qua iterator dù `add` của collection đã bị chặn. Muốn không cho sửa qua collection, phải kiểm tra cả đường iterator.

### Chuỗi gọi của thao tác xóa

Hình dung đường thực hiện thường gặp của phần cài đặt khung:

~~~text
remove(value)
    -> tìm value bằng iterator
    -> iterator.remove() nếu tìm thấy

clear()
    -> duyệt bằng iterator
    -> iterator.remove() cho từng phần tử

removeIf(predicate) mặc định
    -> duyệt bằng iterator
    -> iterator.remove() cho các phần tử được chọn
~~~

Nếu iterator không hỗ trợ xóa, `remove(value)` có thể chỉ thất bại lúc tìm thấy giá trị. Nếu không tìm thấy, nó trả `false` mà chưa cần gọi `remove` trên iterator.

Đây là lý do phải kiểm tra cả đầu vào có tác động lẫn không có tác động. Một lớp chỉ được thử với collection rỗng có thể trông như hỗ trợ nhiều thao tác, nhưng thất bại khi dữ liệu thật xuất hiện.

Đối với collection sửa được đầy đủ theo mô hình này, cài thêm `add(E)` và hỗ trợ `Iterator.remove` là nhóm mở rộng quan trọng. Có thể ghi đè các phương thức khác khi có thuật toán hiệu quả hơn, nhưng phải giữ cùng kết quả, ngoại lệ và tác động đã cam kết. Xem [AbstractCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractCollection.html).

## AbstractList: đọc, thay thế và đổi kích thước là ba khả năng khác nhau

`AbstractList` xây dựng các thao tác theo vị trí. Đối với dữ liệu hỗ trợ truy cập chỉ số hiệu quả, `get(int)` và `size()` tạo được một danh sách đọc được, có iterator và nhiều thuật toán sẵn.

Có ba mức hữu ích:

| Loại danh sách | `get/size` | `set` | `add/remove` |
| --- | --- | --- | --- |
| Chỉ đọc | Có | Không | Không |
| Kích thước cố định, thay phần tử được | Có | Có | Không |
| Thay đổi kích thước được | Có | Có nếu công bố hỗ trợ thay thế | Có theo khả năng công bố |

Khả năng thêm/xóa và khả năng thay thế không phải một cờ duy nhất “mutable”. Khi thiết kế một lớp chuyên biệt, hãy mô tả từng nhóm thao tác chính.

### Vì sao ghi đè add(E) chưa đủ?

`AbstractList.add(E)` kế thừa gọi `add(size(), e)`. Nếu muốn tích hợp với phần cài đặt khung, điểm mở đúng thường là `add(int,E)`.

Nếu chỉ viết `add(E)` mà để `add(int,E)` không được hỗ trợ, các đường thêm theo chỉ số hoặc `ListIterator.add` có thể vẫn ném `UnsupportedOperationException`. Khi ấy lớp chỉ hỗ trợ một tập thao tác hẹp hơn; không được mô tả rằng “thêm phần tử ở mọi vị trí”.

Tương tự, `remove(int)` và `remove(Object)` là hai overload khác nhau. Đối với `List<Integer>`:

~~~java
numbers.remove(1);                 // Xóa tại chỉ số 1.
numbers.remove(Integer.valueOf(1)); // Xóa giá trị 1 đầu tiên nếu có.
~~~

Đây là minh họa chọn overload, không phải một chương trình độc lập. Khi cài đặt và kiểm thử, phải phân biệt đúng loại đối số để không thử nhầm khả năng.

### Iterator kế thừa mở rộng từ phương thức của list

Với phần cài đặt của `AbstractList`, có thể hình dung:

~~~text
Iterator.next()          -> get(index)
Iterator.remove()        -> remove(index)

ListIterator.set(value)  -> set(index, value)
ListIterator.add(value)  -> add(index, value)
~~~

Điều đó không bỏ qua quy tắc trạng thái của iterator. Chẳng hạn, iterator hỗ trợ xóa vẫn không cho xóa trước khi `next` trả phần tử, và không cho xóa hai lần sau cùng một `next`.

`ListIterator` có con trỏ nằm giữa các phần tử. `set/remove` tác động đến phần tử được `next` hoặc `previous` trả gần nhất, không phải tùy ý phần tử tại một chỉ số con trỏ đang hiển thị. Sau các thao tác làm mất điều kiện ấy, phải duyệt lại theo hợp đồng trước khi gọi tiếp. Xem [ListIterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ListIterator.html).

### set còn ảnh hưởng replaceAll và sort

Danh sách kích thước cố định có thể sắp xếp và thay toàn bộ giá trị mà không thêm hoặc xóa phần tử. Các phần cài đặt mặc định liên quan của `List` dùng khả năng thay thế qua list iterator.

Vì vậy, không hỗ trợ đổi kích thước không đồng nghĩa không thể `sort`. Ngược lại, hỗ trợ `add/remove` nhưng không hỗ trợ `set` cũng không tự làm mọi thuật toán thay thế hoạt động.

Trong ví dụ phía dưới, `FixedList` chỉ bổ sung `set` vào `get/size`. Nó sắp xếp, chuyển chữ thường và thay qua `ListIterator.set` thành công; `add` và `remove(int)` vẫn không được hỗ trợ.

### modCount khi dùng iterator và subList kế thừa

`AbstractList` cung cấp `modCount` cho việc phát hiện thay đổi cấu trúc trong iterator/list iterator kế thừa. Với lớp muốn dùng cơ chế ấy, các phương thức thay đổi cấu trúc phải cập nhật `modCount` đúng. Một lần thêm hoặc xóa đơn lẻ không tăng quá một lần.

Trong ví dụ, thêm/xóa thành công tăng `modCount`; `set` chỉ thay phần tử nên không tăng cho mô hình danh sách này. Nếu lời gọi thất bại trước khi thay đổi dữ liệu, không tăng như thể đã thay đổi.

Không chỉ dựa vào việc kho chứa nội bộ là `ArrayList`. Iterator do `AbstractList` ngoài tạo theo dõi trạng thái lớp ngoài; một bộ đếm riêng trong `ArrayList` bên trong không tự thay thế bộ đếm đó.

Việc dùng `modCount` cho phát hiện thay đổi là lựa chọn của lớp con theo tài liệu [AbstractList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html). Nó không là khóa, không làm lớp an toàn cho nhiều luồng và không phải cách xác minh chương trình đồng thời đúng.

`subList` là view, không phải bản sao tách rời. Nếu cho phép sửa qua view, thay đổi phải đi vào list gốc theo hợp đồng. Khi sửa cấu trúc list gốc qua đường khác trong lúc đang dùng view, không được tiếp tục giả định view còn hợp lệ.

## AbstractSequentialList: đảo chiều phụ thuộc

Nếu kho dữ liệu đi theo nút liên kết, việc liên tục gọi `get(0)`, `get(1)`, `get(2)` có thể phải đi lại từ đầu và trở nên đắt.

`AbstractSequentialList` đi theo hướng ngược với `AbstractList`: cung cấp `listIterator(int)` rồi xây các thao tác theo vị trí trên iterator đó. Nhóm nền tảng là `listIterator(int)` và `size()`. Xem [AbstractSequentialList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSequentialList.html).

Nếu tự viết một `ListIterator` chỉ đọc, vẫn phải có phần thân cho mọi phương thức trừu tượng trong `ListIterator`:

- Duyệt tới/lùi: `hasNext`, `next`, `hasPrevious`, `previous`.
- Chỉ số con trỏ: `nextIndex`, `previousIndex`.
- Các thao tác tùy chọn `add`, `set`, `remove`: nếu không hỗ trợ thì cài để ném `UnsupportedOperationException`.

Điểm cần lưu ý là `ListIterator` khai báo lại các thao tác này; không nên nhìn phần mặc định của `Iterator.remove` rồi nghĩ mọi lớp cài trực tiếp `ListIterator` có thể bỏ hết chúng.

Muốn hỗ trợ thay phần tử, iterator cần `set` đúng. Muốn đổi kích thước, iterator cần `add/remove` đúng, gồm cả việc chỉnh con trỏ và trạng thái phần tử vừa trả. Khi đó nhiều thao tác theo chỉ số kế thừa mới có nền tảng thích hợp để hoạt động.

Lựa chọn lớp khung không chỉ để ít mã: nó còn chọn đường đi của thuật toán. `RandomAccess` là dấu hiệu cho thuật toán bên ngoài về kiểu truy cập, không tự làm `get` nhanh lên và không nên gắn vào một danh sách liên kết chỉ để có vẻ đầy đủ.

## AbstractSet: lớp khung không tự loại phần tử trùng

`AbstractSet` dùng nhóm phương thức nền tảng tương tự `AbstractCollection`, đồng thời cung cấp ngữ nghĩa `equals/hashCode` của set. Nhưng nó không biến bất kỳ kho lưu trữ nào thành set đúng.

Nếu lớp con giữ `[A, A]` trong một list nội bộ rồi trả iterator của list đó và `size = 2`, việc chỉ đổi tên lớp thành `CustomSet` không sửa được vi phạm. Mọi điểm nhập dữ liệu, gồm hàm tạo và `add`, phải duy trì tính duy nhất.

Đoạn minh họa dưới đây là một hướng ủy quyền cho phần thêm, nếu kho nội bộ vốn đã có ngữ nghĩa set:

~~~java
@Override
public boolean add(E value) {
    return backingSet.add(value);
}
~~~

Nếu kho nội bộ là list, phải tự kiểm tra và giữ bất biến phù hợp; không có bảo đảm loại trùng nào xuất hiện chỉ nhờ `extends AbstractSet`.

Cần giữ đúng tính bằng nhau giữa các set khác triển khai: thứ tự duyệt khác nhau không làm hai set có cùng phần tử trở thành khác nhau. `hashCode` cũng phải theo hợp đồng set. Ngược lại, list so sánh theo thứ tự. Một collection dạng “túi” cho phép trùng không nên tự nhận ngữ nghĩa list hoặc set chỉ vì muốn tái dùng `equals`. Xem [AbstractSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSet.html).

Một số thao tác như `removeAll` có thể chọn đường thực hiện khác dựa trên kích thước hai phía. Vì vậy, không kiểm thử đúng một kích thước rồi suy ra các khả năng xóa đã nhất quán. Đối chiếu hợp đồng công bố với cả phương thức xóa trực tiếp và xóa qua iterator.

## AbstractQueue: một nhóm phương thức khác với Collection thông thường

`AbstractQueue` yêu cầu nền tảng `offer`, `poll`, `peek`, `iterator` và `size`. Nó phù hợp với queue không chấp nhận phần tử `null`.

Lý do `null` quan trọng: `poll/peek` dùng `null` làm kết quả báo rỗng. Nếu cho phép chèn `null` như phần tử thật, các phần cài đặt kế thừa không còn phân biệt được phần tử thật và tín hiệu rỗng.

Các phương thức kế thừa tạo hai cách báo kết quả:

| Phương thức nền | Phương thức xây trên nó | Khác biệt khi không có kết quả |
| --- | --- | --- |
| `offer(e)` | `add(e)` | `offer` trả `false` vì đầy; `add` ném `IllegalStateException` |
| `poll()` | `remove()` | `poll` trả `null` khi rỗng; `remove` ném `NoSuchElementException` |
| `peek()` | `element()` | `peek` trả `null` khi rỗng; `element` ném `NoSuchElementException` |

Đây là hợp đồng của [AbstractQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractQueue.html). Một queue đã đầy vẫn có hỗ trợ thêm khi có chỗ trở lại; không dùng `UnsupportedOperationException` để diễn đạt trạng thái đầy tạm thời.

### remove() khác remove(Object)

`remove()` của queue lấy phần tử đầu. `remove(Object)` tìm và xóa một giá trị cụ thể.

Một queue có thể hỗ trợ lấy đầu bằng `poll` nhưng không cho iterator xóa phần tử bất kỳ. Với phần cài đặt khung, `remove()` vẫn hoạt động qua `poll`; `remove(existingValue)` có thể ném `UnsupportedOperationException` do cần `Iterator.remove`.

Khả năng `clear` cũng phụ thuộc lớp khung. `AbstractCollection.clear` dựa trên xóa qua iterator, còn `AbstractQueue.clear` gọi `poll` lặp đến khi rỗng. Do đó trong ví dụ, queue không hỗ trợ iterator xóa nhưng `clear` vẫn chạy được.

Đây là một lý do thực tế để đọc phần “Implementation Requirements” của đúng lớp được kế thừa, thay vì áp một mô hình gọi chung cho mọi collection.

## AbstractMap: entrySet là nền tảng và cũng là đường ghi

`Map` không phải kiểu con của `Collection`. Với `AbstractMap`, điểm nền tảng là `entrySet()`: một set view biểu diễn các ánh xạ.

Từ đó, lớp khung có thể suy ra `size`, tìm khóa, đọc giá trị, so sánh map và tạo các view khóa hoặc giá trị. Một map chỉ cần `entrySet` để bắt đầu đọc được, nhưng chất lượng của view này quyết định phần lớn hành vi còn lại.

Đối với map sửa được theo cách cài đặt khung, `put` là điểm mở thêm/cập nhật và iterator của `entrySet` cần hỗ trợ xóa nếu muốn các đường xóa kế thừa hoạt động. Xem [AbstractMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.html).

### Không trả snapshot nếu hợp đồng đòi view sống

Giả sử map còn có thể được cập nhật. Nếu `entrySet()` luôn tạo một bản sao rời rạc, một view đã lấy trước đó sẽ không phản ánh thay đổi map theo hợp đồng.

Hơn nữa, `entrySet().iterator().remove()` nếu được hỗ trợ phải xóa ánh xạ trong map gốc, không chỉ xóa một phần tử trong bản sao tạm. View `keySet` và `values` cũng có ý nghĩa liên kết với map.

Trong ví dụ, map sao chép nguồn một lần trong hàm tạo và giữ bản sao đó không sửa được. `entrySet` trả view của chính dữ liệu map đang sở hữu; đó không phải việc tạo snapshot mới ở mỗi lần gọi.

### Chặn put chưa đủ để làm map chỉ đọc

Một map có nhiều đường có thể thay đổi:

~~~text
put / putAll / remove / clear
keySet().remove(...)
values().remove(...)
entrySet().iterator().remove()
entrySet().iterator().next().setValue(...)
replaceAll(...) và các phương thức cập nhật mặc định khác
~~~

Nếu chỉ để `put` ném `UnsupportedOperationException` nhưng trả các entry ghi được, `Entry.setValue` có thể vẫn đổi giá trị.

Đặc biệt, `Collections.unmodifiableSet(backingMap.entrySet())` chỉ chặn thao tác thay đổi cấu trúc set qua wrapper đó; nó không tự bọc từng đối tượng `Map.Entry` để chặn `setValue`. Để dùng một map không sửa được, trả `entrySet` từ một `Collections.unmodifiableMap` đúng cách là một hướng rõ ràng hơn.

Nếu tự cài entry, phải xác định nó gắn với map hay độc lập. Một `SimpleEntry` có thể đổi giá trị của chính nó, nhưng một entry rời rạc không tự viết ngược vào kho map. Không trả một entry “trông có vẻ ghi được” mà `setValue` chỉ đổi bản sao khi hợp đồng view yêu cầu cập nhật ánh xạ gốc. Xem [Map.Entry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html).

### put hỗ trợ không đồng nghĩa replaceAll mặc định hỗ trợ

Phần cài đặt mặc định của `Map.replaceAll` đi qua entry và gọi `Entry.setValue`. Do đó một map có `put` nhưng entry không hỗ trợ `setValue` có thể không dùng được `replaceAll` mặc định.

Muốn công bố hỗ trợ, phải cung cấp đường cài đặt đúng hoặc ghi đè phương thức phù hợp. Không suy ra từ việc một thao tác cập nhật đơn lẻ hoạt động rằng mọi phương thức mặc định đều hoạt động.

Ngược lại, `putAll` trong `AbstractMap` sử dụng `put` cho từng ánh xạ. Vì nguồn rỗng không có lượt `put` nào, map ví dụ nhận `putAll(Map.of())` bình thường dù `put` bị chặn. Quy tắc này phù hợp với trường hợp không có tác động được [Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html) cho phép.

## Chương trình đầy đủ kiểm chứng các khả năng

Lưu mã sau vào `MinimalCollectionsDemo.java`. Các lớp nhỏ dùng cấu trúc có sẵn làm kho để tập trung vào đường gọi của lớp khung, không nhằm thay thế các collection chuẩn.

Chương trình có sáu lớp minh họa:

- `ReadOnlyBag`: chỉ `iterator/size`, giữ cả phần tử trùng.
- `ReadOnlyList`: chỉ `get/size`, sao chép nguồn.
- `FixedList`: thêm `set`, giữ nguyên kích thước.
- `EditableList`: thêm `add(int,E)/remove(int)` và cập nhật `modCount`.
- `LimitedQueue`: năm phương thức nền của queue, cố ý không hỗ trợ iterator xóa.
- `ReadOnlyMap`: chỉ `entrySet` dựa trên bản sao map không sửa được.

`outcome` chỉ dùng để ghi tên ngoại lệ cho thí nghiệm. Trong mã ứng dụng, không nên biến mọi lỗi `RuntimeException` thành một chuỗi rồi tiếp tục như thể thao tác đã thành công.

~~~java
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;

public class MinimalCollectionsDemo {
    static final class ReadOnlyBag<E> extends AbstractCollection<E> {
        private final List<E> data;

        ReadOnlyBag(Collection<? extends E> source) {
            data = List.copyOf(source);
        }

        @Override public int size() { return data.size(); }

        @Override public Iterator<E> iterator() {
            Iterator<E> delegate = data.iterator();
            return new Iterator<>() {
                @Override public boolean hasNext() { return delegate.hasNext(); }
                @Override public E next() { return delegate.next(); }
            };
        }
    }

    static final class ReadOnlyList<E> extends AbstractList<E>
            implements RandomAccess {
        private final List<E> data;

        ReadOnlyList(Collection<? extends E> source) {
            data = List.copyOf(source);
        }

        @Override public E get(int index) { return data.get(index); }
        @Override public int size() { return data.size(); }
    }

    static class FixedList<E> extends AbstractList<E> implements RandomAccess {
        protected final ArrayList<E> data;

        FixedList(Collection<? extends E> source) {
            data = new ArrayList<>(source);
        }

        @Override public E get(int index) { return data.get(index); }
        @Override public int size() { return data.size(); }
        @Override public E set(int index, E value) {
            return data.set(index, value);
        }
    }

    static final class EditableList<E> extends FixedList<E> {
        EditableList(Collection<? extends E> source) {
            super(source);
        }

        @Override public void add(int index, E value) {
            data.add(index, value);
            modCount++;
        }

        @Override public E remove(int index) {
            E previous = data.remove(index);
            modCount++;
            return previous;
        }
    }

    static final class LimitedQueue<E> extends AbstractQueue<E> {
        private final int capacity;
        private final ArrayDeque<E> data = new ArrayDeque<>();

        LimitedQueue(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            this.capacity = capacity;
        }

        @Override public int size() { return data.size(); }
        @Override public E peek() { return data.peekFirst(); }
        @Override public E poll() { return data.pollFirst(); }

        @Override public boolean offer(E value) {
            Objects.requireNonNull(value);
            if (data.size() == capacity) {
                return false;
            }
            data.addLast(value);
            return true;
        }

        // Head removal is supported; iterator removal is not supported.
        @Override public Iterator<E> iterator() {
            Iterator<E> delegate = data.iterator();
            return new Iterator<>() {
                @Override public boolean hasNext() { return delegate.hasNext(); }
                @Override public E next() { return delegate.next(); }
            };
        }
    }

    static final class ReadOnlyMap<K, V> extends AbstractMap<K, V> {
        private final Map<K, V> data;

        ReadOnlyMap(Map<? extends K, ? extends V> source) {
            data = Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }

        @Override public Set<Entry<K, V>> entrySet() {
            return data.entrySet();
        }
    }

    static String outcome(Runnable action) {
        try {
            action.run();
            return "OK";
        } catch (RuntimeException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        ReadOnlyBag<String> bag = new ReadOnlyBag<>(List.of("A", "B", "A"));
        System.out.println("bag = " + bag);
        System.out.println("bag-contains-A = " + bag.contains("A"));
        System.out.println("bag-add = " + outcome(() -> bag.add("C")));
        System.out.println("bag-remove-missing = " + bag.remove("Z"));
        System.out.println("bag-remove-existing = " + outcome(() -> bag.remove("A")));
        System.out.println("bag-addAll-empty = " + bag.addAll(List.of()));
        System.out.println("bag-removeIf-no-match = " + bag.removeIf(s -> s.equals("Z")));
        System.out.println("bag-removeIf-match = "
                + outcome(() -> bag.removeIf(s -> s.equals("A"))));

        ArrayList<String> source = new ArrayList<>(List.of("one", "two"));
        ReadOnlyList<String> readOnly = new ReadOnlyList<>(source);
        source.set(0, "changed");
        System.out.println("read-only-snapshot = " + readOnly);
        System.out.println("list-equals = " + readOnly.equals(List.of("one", "two")));
        System.out.println("list-hash-equals = "
                + (readOnly.hashCode() == List.of("one", "two").hashCode()));
        System.out.println("read-only-set = " + outcome(() -> readOnly.set(0, "X")));

        FixedList<String> fixed = new FixedList<>(List.of("B", "A"));
        System.out.println("fixed-set-old = " + fixed.set(0, "C"));
        fixed.sort(null);
        fixed.replaceAll(String::toLowerCase);
        System.out.println("fixed-after-sort-replace = " + fixed);
        ListIterator<String> fixedIt = fixed.listIterator();
        fixedIt.next();
        fixedIt.set("first");
        System.out.println("fixed-after-iterator-set = " + fixed);
        System.out.println("fixed-add = " + outcome(() -> fixed.add("D")));
        System.out.println("fixed-remove-index = " + outcome(() -> fixed.remove(0)));

        EditableList<String> editable = new EditableList<>(List.of("A", "B", "C"));
        editable.add("D");
        ListIterator<String> it = editable.listIterator();
        System.out.println("editable-remove-before-next = " + outcome(it::remove));
        it.next();
        it.remove();
        it.add("X");
        System.out.println("editable-after-iterator = " + editable);
        List<String> view = editable.subList(1, 3);
        view.clear();
        System.out.println("editable-after-sublist-clear = " + editable);
        Iterator<String> oldIterator = editable.iterator();
        editable.add("E");
        System.out.println("editable-old-iterator = " + outcome(oldIterator::next));

        LimitedQueue<String> queue = new LimitedQueue<>(2);
        System.out.println("queue-first-offer = " + queue.offer("A"));
        System.out.println("queue-add = " + queue.add("B"));
        System.out.println("queue-full-offer = " + queue.offer("C"));
        System.out.println("queue-full-add = " + outcome(() -> queue.add("C")));
        System.out.println("queue-remove-existing = "
                + outcome(() -> queue.remove("B")));
        System.out.println("queue-remove-head = " + queue.remove());
        queue.clear();
        System.out.println("queue-after-clear = " + queue);
        System.out.println("queue-empty-poll = " + queue.poll());
        System.out.println("queue-empty-remove = " + outcome(queue::remove));
        System.out.println("queue-null-offer = " + outcome(() -> queue.offer(null)));

        LinkedHashMap<String, Integer> input = new LinkedHashMap<>();
        input.put("A", 1);
        input.put("B", 2);
        ReadOnlyMap<String, Integer> map = new ReadOnlyMap<>(input);
        input.put("A", 99);
        System.out.println("map-snapshot = " + map);
        System.out.println("map-get = " + map.get("A"));
        System.out.println("map-put = " + outcome(() -> map.put("C", 3)));
        System.out.println("map-remove-missing = " + map.remove("Z"));
        System.out.println("map-remove-existing = " + outcome(() -> map.remove("A")));
        System.out.println("map-putAll-empty = " + outcome(() -> map.putAll(Map.of())));
        Map.Entry<String, Integer> entry = map.entrySet().iterator().next();
        System.out.println("map-entry-setValue = " + outcome(() -> entry.setValue(8)));
        System.out.println("map-keySet-remove = " + outcome(() -> map.keySet().remove("A")));
        System.out.println("map-replaceAll = "
                + outcome(() -> map.replaceAll((key, value) -> value + 1)));
        System.out.println("map-after-attempts = " + map);
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 MinimalCollectionsDemo.java
java MinimalCollectionsDemo
~~~

Mã đã được biên dịch với `--release 17` và chạy bằng OpenJDK Zulu 24.0.2. Stdout chính xác của lần chạy:

~~~text
bag = [A, B, A]
bag-contains-A = true
bag-add = UnsupportedOperationException
bag-remove-missing = false
bag-remove-existing = UnsupportedOperationException
bag-addAll-empty = false
bag-removeIf-no-match = false
bag-removeIf-match = UnsupportedOperationException
read-only-snapshot = [one, two]
list-equals = true
list-hash-equals = true
read-only-set = UnsupportedOperationException
fixed-set-old = B
fixed-after-sort-replace = [a, c]
fixed-after-iterator-set = [first, c]
fixed-add = UnsupportedOperationException
fixed-remove-index = UnsupportedOperationException
editable-remove-before-next = IllegalStateException
editable-after-iterator = [X, B, C, D]
editable-after-sublist-clear = [X, D]
editable-old-iterator = ConcurrentModificationException
queue-first-offer = true
queue-add = true
queue-full-offer = false
queue-full-add = IllegalStateException
queue-remove-existing = UnsupportedOperationException
queue-remove-head = A
queue-after-clear = []
queue-empty-poll = null
queue-empty-remove = NoSuchElementException
queue-null-offer = NullPointerException
map-snapshot = {A=1, B=2}
map-get = 1
map-put = UnsupportedOperationException
map-remove-missing = null
map-remove-existing = UnsupportedOperationException
map-putAll-empty = OK
map-entry-setValue = UnsupportedOperationException
map-keySet-remove = UnsupportedOperationException
map-replaceAll = UnsupportedOperationException
map-after-attempts = {A=1, B=2}
~~~

## Giải thích kết quả và giới hạn của ví dụ

### Bag chỉ đọc vẫn có lời gọi xóa trả về bình thường

`bag-remove-missing = false` và `bag-addAll-empty = false` là các lời gọi không thay đổi dữ liệu. Chúng không cần gọi đường ghi bị chặn.

`bag-remove-existing` và `bag-removeIf-match` lại cần iterator xóa và nhận `UnsupportedOperationException`. Cùng một đối tượng, sự khác nhau nằm ở đường thực hiện thực tế của lời gọi, không phải khả năng tự nhiên thay đổi giữa các dòng.

`ReadOnlyBag` sao chép dữ liệu bằng `List.copyOf`, nên hàm tạo không nhận phần tử `null`. Đây là lựa chọn của lớp ví dụ; không phải mọi lớp con `AbstractCollection` đều buộc cấm `null`.

Nó không ghi đè `equals/hashCode` thành ngữ nghĩa list chỉ vì dữ liệu bên trong được lưu bằng list. Cấu trúc lưu trữ là chi tiết cài đặt; kiểu trừu tượng được công bố mới quyết định ngữ nghĩa bằng nhau cần có.

### ReadOnlyList tách dữ liệu nguồn và có ngữ nghĩa list

Sau khi thay phần tử trong `source`, `readOnly` vẫn hiển thị `[one, two]`. Hàm tạo đã lấy bản sao danh sách phần tử tại thời điểm tạo; đối tượng không là view của `source`.

`list-equals = true` và `list-hash-equals = true` cho thấy `AbstractList` cung cấp ngữ nghĩa so sánh theo phần tử và thứ tự, tương thích với một list chuẩn có cùng dữ liệu. Không cần tự viết lại thuật toán ấy khi nó đã phù hợp hợp đồng.

Đây vẫn là sao chép nông. Nếu phần tử là đối tượng mutable, bản sao chứa cùng tham chiếu tới đối tượng đó. Ví dụ dùng `String` để sự thay đổi nội dung phần tử không làm nhiễu bài học về khả năng sửa collection.

### FixedList chỉ cần set để mở một nhóm thao tác khác

`fixed-set-old = B` nhắc lại rằng `set` phải trả phần tử cũ, không phải phần tử mới hay một giá trị báo thành công tùy ý.

Sau `sort` rồi `replaceAll`, list là `[a, c]`; sau `ListIterator.set`, nó là `[first, c]`. Số phần tử vẫn là hai.

`fixed-add` và `fixed-remove-index` ném `UnsupportedOperationException`. Đó là khác biệt giữa thay thế và thay đổi kích thước. Một API nhận `List` và chỉ cần hoán vị/thay thế có yêu cầu khả năng khác API cần thêm dòng mới.

### EditableList kế thừa cả đường sửa qua iterator và subList

Từ `[A, B, C]`, lệnh `add("D")` kế thừa đi vào `add(int,E)` của lớp con. Sau đó iterator lấy `A`, xóa `A` rồi chèn `X` tại con trỏ, tạo `[X, B, C, D]`.

Lời gọi `remove` trước `next` nhận `IllegalStateException`: khả năng xóa có được hỗ trợ, nhưng lời gọi diễn ra sai trạng thái. Không đánh đồng ngoại lệ này với `UnsupportedOperationException`.

`subList(1,3)` chọn `[B,C]`. Gọi `clear` trên view làm list gốc còn `[X,D]`. Đây là bằng chứng đường ghi qua view hoạt động trên dữ liệu gốc.

Cuối cùng, chương trình tạo iterator rồi thêm `E` trực tiếp qua list. Lần `next` trên iterator cũ ném `ConcurrentModificationException` trong phép thử đơn luồng này. Nó cho thấy `modCount` đã được nối vào cơ chế kế thừa; không chứng minh cơ chế đó cung cấp đồng bộ cho cập nhật từ nhiều luồng.

`data` trong `FixedList` là `protected` để lớp minh họa `EditableList` dùng chung kho. Khi phát triển một thư viện có nhiều lớp con không kiểm soát được, phải thiết kế quyền truy cập cẩn thận để lớp con không sửa kho mà bỏ qua bất biến hoặc bộ đếm.

### LimitedQueue hỗ trợ lấy đầu nhưng không hỗ trợ xóa tùy ý

Queue dung lượng hai nhận `A` và `B`. Khi đầy, `offer("C")` trả `false` còn `add("C")` ném `IllegalStateException`. Đây là một cặp hợp đồng được `AbstractQueue` xây từ `offer`.

`queue.remove("B")` tìm đúng phần tử nhưng iterator không có `remove`, nên ném `UnsupportedOperationException`. `queue.remove()` lại lấy `A` thành công qua `poll`.

`queue.clear()` lấy bỏ phần tử còn lại bằng `poll` nên hoạt động được. Sau đó `poll` trả `null`, còn `remove()` ném `NoSuchElementException`. Thêm `null` bị `Objects.requireNonNull` chặn trước khi lưu dữ liệu.

Lớp ví dụ không cung cấp chờ, khóa hay bảo đảm dùng đồng thời. Một queue giới hạn dung lượng chưa tự trở thành `BlockingQueue` hoặc một cấu trúc an toàn cho nhiều luồng.

### ReadOnlyMap chặn cả đường entry và view

Map giữ bản sao `{A=1, B=2}` dù map đầu vào đã đổi `A` thành `99`. Chặn sửa được thực hiện trên bản sao riêng, tránh việc thành phần giữ map nguồn có thể sửa trực tiếp kho của đối tượng ví dụ.

`get`, `size` và hiển thị được suy ra từ `entrySet`. Nhưng `AbstractMap.get` theo cách kế thừa có thể phải duyệt entry; việc kho bên trong là `LinkedHashMap` không tự làm đường `get` lớp ngoài thành truy cập băm. Nếu hiệu năng đọc quan trọng, có thể ghi đè `get/containsKey/size` để ủy quyền phù hợp sau khi giữ đúng hợp đồng.

`map-remove-missing = null` là một thao tác không có tác động. `map-remove-existing`, `map-keySet-remove` và `map-entry-setValue` đều bị chặn. `map-replaceAll` cũng bị chặn tại đường cập nhật entry. Map cuối vẫn là `{A=1, B=2}`.

Bản sao map cũng là sao chép nông. Với khóa mutable, sửa những trường ảnh hưởng `equals/hashCode` sau khi đưa vào map có thể làm hỏng giả định tra cứu. Không dùng nhãn “chỉ đọc” để suy ra mọi đối tượng được chứa đều bất biến.

## Tối thiểu về số phương thức không có nghĩa tối thiểu về chất lượng

### Chỉ số và số lượng phải đúng

Với list, miền hợp lệ của `get/set/remove(int)` là từ `0` đến nhỏ hơn `size()`. `add(int,E)` cho phép thêm tại `size()`, tức ngay sau phần tử cuối.

Nếu tự viết kho dữ liệu, phải kiểm tra đúng hai loại biên. Đừng dùng chung một điều kiện `index < size` rồi vô tình không cho thêm vào list rỗng hoặc không cho nối cuối.

`size` phải nhất quán với số phần tử iterator có thể duyệt trong điều kiện sử dụng được hỗ trợ. Nếu kho có thể quá lớn để biểu diễn bằng `int`, phải theo quy tắc `Collection.size`; không tự để tràn thành số âm. Với lớp nhỏ trong ví dụ, cấu trúc chuẩn nội bộ xử lý phần này.

`next` khi hết phải ném `NoSuchElementException`, không trả `null` làm tín hiệu kết thúc: collection có thể chứa `null` hợp lệ theo chính sách riêng. `hasNext` không được tự tiêu thụ một phần tử rồi bỏ mất nó.

### Chính sách phần tử phải có tên rõ ràng

Khi cài lớp chuyên biệt, hãy mô tả các điều kiện như:

- Cho phép `null` hay không.
- Có chấp nhận trùng hay phải giữ duy nhất.
- Thứ tự có được bảo đảm hay không.
- Đối tượng được sao chép hay giữ view tới nguồn.
- Có giới hạn số lượng, miền giá trị hoặc loại dữ liệu hay không.
- Có hỗ trợ truy cập từ nhiều luồng hay cần đồng bộ bên ngoài.

Không phải mọi lỗi dữ liệu đều là thao tác không hỗ trợ. Nếu lớp có hỗ trợ thêm nhưng phần tử không hợp lệ, hãy ném loại ngoại lệ đúng với điều kiện đã công bố. Với phép truy vấn một giá trị không hợp lệ, hợp đồng một số interface cho phép trả kết quả không tìm thấy hoặc ném ngoại lệ tùy triển khai; không tự hứa tất cả collection có cùng chính sách.

Constructor nhận `Collection` hoặc `Map` là khuyến nghị cho các triển khai dùng chung, không phải một phương thức interface mà trình biên dịch bắt mọi lớp phải có. Một cấu trúc chuyên biệt có thể cần tham số dung lượng hoặc nguồn dữ liệu riêng; API phải giải thích được cách tạo một đối tượng hợp lệ.

### Các phương thức mặc định cũng phải được đưa vào danh sách khả năng

Không chỉ kiểm tra những phương thức mình viết bằng tay. Với một list, còn có `removeIf`, `replaceAll`, `sort`, iterator, list iterator và sublist. Với map, còn có `putIfAbsent`, `replace`, `compute`, `merge`, `replaceAll` và các view.

Ví dụ, phương thức mặc định có thể chỉ cần ghi trong một nhánh. Một `computeIfAbsent` có thể trả giá trị đã có mà chưa cần gọi `put`; điều đó không chứng minh map hỗ trợ ghi cho khóa vắng.

Nếu lớp hứa “mọi thao tác ghi đều thất bại ngay”, chỉ giữ phần cài đặt tối thiểu có thể không đủ để tạo đúng bảo đảm mạnh đó. Cần xem và có thể ghi đè mọi đường liên quan, gồm cả đường không có tác động và view. Nếu chỉ hứa không thể thay dữ liệu qua API, vẫn phải chắc không có đường ghi bị bỏ sót.

### Không có bảo đảm hoàn tác tự động cho thao tác hàng loạt

Một `addAll` kế thừa có thể thêm lần lượt. Nếu thêm vài phần tử rồi gặp dữ liệu không hợp lệ hoặc giới hạn dung lượng, phương thức có thể ném ngoại lệ sau khi đã thêm một phần.

Tương tự, một `removeIf` có thể đã xóa vài phần tử trước khi điều kiện ném lỗi; một phép cập nhật hàng loạt có thể đã thay một số giá trị. Không được công bố tính “tất cả hoặc không gì” chỉ vì phương thức có một tên duy nhất.

Nếu một lớp cần bảo đảm mạnh hơn, phải thiết kế cơ chế phù hợp: kiểm tra trước với điều kiện đúng, tạo trạng thái mới rồi công bố, hoặc dùng giao thức cập nhật khác. Việc kiểm tra trước cũng cần xem xét trạng thái có thể thay đổi giữa lúc kiểm tra và lúc ghi nếu có nhiều luồng.

## Một cách kiểm tra có hệ thống trước khi công bố lớp

Bắt đầu từ lời hứa công khai, rồi đi qua từng đường thực hiện.

| Nhóm kiểm tra | Ví dụ đầu vào có giá trị |
| --- | --- |
| Đọc | Rỗng, một phần tử, nhiều phần tử, có trùng nếu cho phép |
| Chỉ số | `-1`, `0`, chỉ số cuối, `size()`, vượt `size()` |
| Thêm | Thêm mới, thêm trùng, thêm tại đầu/cuối, khi đầy |
| Thay thế | Giá trị cũ trả về, cùng giá trị, giá trị không hợp lệ |
| Xóa | Có giá trị, không có giá trị, xóa đầu, xóa qua iterator |
| Trạng thái iterator | Trước `next`, sau một `next`, xóa hai lần, hết dữ liệu |
| Hàng loạt | Nguồn rỗng, không khớp, có khớp, dữ liệu lỗi giữa chừng |
| View | Sửa qua `subList/keySet/values/entrySet` có đi đúng vào gốc? |
| Bằng nhau | So với một triển khai chuẩn cùng kiểu trừu tượng |
| Đóng gói | Nguồn ban đầu, iterator hay entry có sửa ngoài ý muốn được không? |

Không cần viết lại một bộ kiểm thử cho toàn bộ JDK để thử một lớp nhỏ, nhưng các đường kế thừa gắn trực tiếp với khả năng đã hứa phải có bằng chứng. Một bài thử “tạo đối tượng rồi in ra” chỉ kiểm tra được rất ít phần của hợp đồng.

Đối với hiệu năng, trước hết đếm đường gọi. Nếu `iterator.next` gọi `get(i)` mà `get(i)` tốn thời gian theo `i`, duyệt toàn bộ có thể tốn bậc hai. Nếu `AbstractMap.get` duyệt `entrySet`, nhiều lần tra cứu có thể đắt dù kho vật lý có bảng băm. Sau khi đúng hợp đồng mới tối ưu các điểm đã đo hoặc có phân tích rõ.

## Bài tập tự kiểm tra

### 1. Chỉ cài size có tạo được AbstractCollection cụ thể không?

Một lớp con chỉ cài `size()` và muốn dựa vào lớp khung cho mọi thứ khác.

**Đáp án:** Chưa đủ. Cần cung cấp `iterator()`. Iterator phải duyệt đúng dữ liệu; một iterator luôn rỗng trong khi `size` khác không cũng không phải cài đặt đúng.

### 2. AbstractSet có tự bỏ phần tử trùng trong constructor không?

Một lớp set tự giữ `new ArrayList<>(source)` rồi trả iterator của list. `source` chứa hai phần tử bằng nhau.

**Đáp án:** Không. Lớp con phải duy trì bất biến không trùng ngay từ hàm tạo và các đường cập nhật. `AbstractSet` không tự lọc kho dữ liệu cho lớp con.

### 3. Vì sao addAll rỗng thành công mà add thật lại thất bại?

Một collection chỉ đọc kế thừa `addAll` từ lớp khung.

**Đáp án:** Với nguồn rỗng không có lượt `add` nào được gọi. Hợp đồng tổng quát cho phép lời gọi không có tác động kết thúc bình thường. Không dùng trường hợp này để dò khả năng thêm.

### 4. List chỉ hỗ trợ set có sort được không?

Một lớp có `get/size/set` đúng nhưng không cho thêm và xóa.

**Đáp án:** Có thể dùng phần sắp xếp mặc định phù hợp vì số lượng không thay đổi và việc ghi lại qua list iterator dùng khả năng `set`. Cần kiểm tra đúng hợp đồng của triển khai; không suy ra từ việc `add` bị chặn rằng mọi thao tác thay đổi đều bị chặn.

### 5. Tại sao queue xóa đầu được nhưng xóa giá trị không được?

Lớp con `AbstractQueue` có `poll` hoạt động, còn iterator chỉ hỗ trợ đọc.

**Đáp án:** `remove()` dựa trên `poll`. `remove(Object)` kế thừa tìm bằng iterator rồi cần `iterator.remove` khi gặp giá trị. Hai overload đi qua hai nhóm phương thức nền khác nhau.

### 6. Map chặn put nhưng entry vẫn ghi được có phải chỉ đọc không?

`entrySet().iterator().next().setValue(v)` sửa được dữ liệu map.

**Đáp án:** Không. Map có một đường cập nhật qua entry. Phải chặn hoặc thiết kế đúng cả entry, iterator và các view; chỉ chặn `put` không đủ.

### 7. Tăng modCount trong set có luôn là việc đúng không?

Lớp list dùng `set` thay một phần tử mà không đổi cấu trúc, nhưng mọi `set` đều tăng `modCount`.

**Đáp án:** Không nên tăng theo thói quen. Với mô hình này, thay phần tử không là thay đổi cấu trúc. Bộ đếm phải theo loại thay đổi mà cơ chế iterator đã công bố cần theo dõi; cập nhật sai có thể làm iterator báo lỗi không phù hợp. Không dùng bộ đếm để thay thế đồng bộ.

### 8. put hỗ trợ nhưng replaceAll thất bại ở setValue có mâu thuẫn không?

Map tự cài `put`, trả entry không hỗ trợ `setValue` và giữ `replaceAll` mặc định.

**Đáp án:** `replaceAll` mặc định đi qua entry, nên có thể không được hỗ trợ trong thiết kế đó. Nếu lớp muốn công bố hỗ trợ `replaceAll`, cần cung cấp một cách cài đặt đúng; kiểm thử `put` thành công không kiểm tra được đường cập nhật này.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-collections-005">Viết Iterator, hỗ trợ remove và xử lý trạng thái con trỏ</a>

<details>
<summary>Xem chi tiết</summary>

# Viết Iterator, hỗ trợ remove và xử lý trạng thái con trỏ

## Iterator là một lượt duyệt có trạng thái

Một collection chứa dữ liệu; một `Iterator<E>` mô tả một lượt đi qua dữ liệu ấy. Iterator không chỉ là hàm “lấy phần tử ở chỉ số i”. Nó còn phải nhớ đã đi đến đâu và phần tử nào đang đủ điều kiện để xóa.

Giả sử collection có các phần tử:

~~~text
[A, B, C, D]
~~~

Sau khi iterator trả `B`, có hai thông tin khác nhau:

- Lần `next()` tiếp theo cần trả `C`.
- Lần `remove()` hợp lệ tiếp theo cần xóa `B`.

Nếu dùng một biến không có ý nghĩa rõ ràng cho cả hai, việc xóa dễ làm con trỏ nhảy quá `C`, xóa nhầm `C` hoặc cho phép xóa lại `B` lần thứ hai.

Bài học tập trung vào một iterator đi tới trên kho mảng, rồi liên hệ với iterator trên nút liên kết và `ListIterator` hai chiều. Ví dụ đầy đủ tự quản lý mảng, thao tác dồn phần tử và trạng thái xóa để có thể nhìn thấy nguyên nhân của từng bước cập nhật.

Ba thuật ngữ sẽ dùng:

| Thuật ngữ | Ý nghĩa trong ví dụ |
| --- | --- |
| `cursor` | Chỉ số phần tử sẽ được `next()` trả ở lần thành công tiếp theo |
| `lastReturned` | Chỉ số phần tử được `next()` trả gần nhất và chưa bị iterator này xóa |
| `expectedModCount` | Số phiên bản cấu trúc mà iterator đang mong đợi |

Tên biến chỉ là lựa chọn của ví dụ. Điều cần giữ là ý nghĩa và bất biến của chúng.

## Hợp đồng cần đáp ứng trước khi viết mã

`Iterator` có hai phương thức đọc nền tảng:

- `hasNext()` cho biết còn phần tử để đọc tiếp.
- `next()` trả phần tử tiếp theo; nếu đã hết thì ném `NoSuchElementException`.

`remove()` là thao tác tùy chọn: nếu hỗ trợ, nó xóa phần tử được iterator trả gần nhất, chỉ một lần cho mỗi lần `next()` thành công. Phần cài đặt mặc định của `Iterator.remove` ném `UnsupportedOperationException`. Các điều kiện này được quy định trong [Iterator của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html).

Điều đó dẫn tới vài yêu cầu quan trọng:

1. Gọi `next` không bắt buộc phải gọi `hasNext` trước.
2. Gọi `hasNext` nhiều lần không được tự làm mất phần tử chưa trả.
3. `null` có thể là một phần tử hợp lệ nếu collection cho phép; không dùng nó để báo hết dữ liệu.
4. Một iterator hỗ trợ xóa phải từ chối `remove` trước lần đọc hợp lệ đầu tiên.
5. Sau khi xóa thành công, phải có một lần đọc thành công mới trước khi xóa tiếp.
6. `hasNext` trả `false` không tự hủy quyền xóa phần tử cuối vừa được trả.

Điểm cuối dễ nhầm. “Không còn phần tử phía trước” khác “không còn phần tử vừa trả để xóa”. Một iterator có thể vừa trả phần tử cuối, kiểm tra đã hết, rồi xóa chính phần tử cuối ấy.

### Phân biệt ba loại lỗi

| Tình huống | Ngoại lệ phù hợp |
| --- | --- |
| Không hỗ trợ xóa qua iterator | `UnsupportedOperationException` |
| Có hỗ trợ xóa, nhưng chưa có phần tử vừa trả hợp lệ hoặc đã xóa nó | `IllegalStateException` |
| `next` được gọi khi hết dữ liệu | `NoSuchElementException` |

Nếu triển khai chọn chính sách phát hiện thay đổi cấu trúc từ bên ngoài, nó còn có thể ném `ConcurrentModificationException` tại các điểm đã quy định.

Khi một lời gọi đồng thời vi phạm nhiều điều kiện, không nên dựa vào một thứ tự kiểm tra ngoại lệ chung cho mọi triển khai. Ví dụ phía dưới chủ động kiểm tra trạng thái `remove` trước, rồi mới kiểm tra thay đổi cấu trúc. Các phép thử dùng từng điều kiện riêng để kết quả có ý nghĩa rõ ràng.

## Thiết kế trạng thái cho iterator trên mảng

Giả sử mảng hoạt động được dùng ở đoạn `[0, size)`. Các ô từ `size` trở đi là vùng không có phần tử của collection.

Khi chưa có thay đổi ngoài giao thức và iterator còn hợp lệ, giữ các bất biến:

~~~text
0 <= cursor <= size

lastReturned == -1
    hoặc là chỉ số phần tử được next trả gần nhất, chưa bị remove

lastReturned hợp lệ sau next:
    lastReturned == cursor - 1
~~~

Khởi tạo:

~~~text
cursor = 0
lastReturned = -1
expectedModCount = modCount của collection lúc tạo iterator
~~~

`-1` là tín hiệu “chưa có đích xóa hợp lệ”. Không dùng `null` cho ý nghĩa ấy, vì dữ liệu có thể chứa `null`.

### hasNext chỉ quan sát

Trong thiết kế đơn giản này:

~~~java
@Override
public boolean hasNext() {
    return cursor < size;
}
~~~

Nó không tăng `cursor`, không đặt `lastReturned`, không đọc rồi bỏ phần tử.

Nếu gọi hai lần `hasNext` trước `next`, phần tử tiếp theo vẫn phải là phần tử ban đầu. Nếu `hasNext` làm tăng chỉ số, vòng lặp thường dùng dưới đây sẽ bỏ qua dữ liệu:

~~~java
while (iterator.hasNext()) {
    E value = iterator.next();
    use(value);
}
~~~

Đoạn trên chỉ minh họa cách sử dụng; `use` là hành động của ứng dụng.

Trong ví dụ, `hasNext` không kiểm tra phiên bản cấu trúc. Điều này là lựa chọn cụ thể, không phải bảo đảm rằng sau mọi thay đổi bên ngoài `hasNext` vẫn có ý nghĩa. Các lời gọi kiểm tra phiên bản nằm ở `next` và `remove` hợp lệ.

### next phải tự kiểm tra đã hết

Một lần `next` thành công làm ba việc:

1. Kiểm tra iterator chưa bị vô hiệu hóa bởi thay đổi cấu trúc bên ngoài.
2. Kiểm tra `cursor < size`.
3. Đọc phần tử tại `cursor`, nhớ vị trí vừa trả rồi tăng `cursor`.

Dạng cốt lõi:

~~~java
E value = elementAt(cursor);
lastReturned = cursor;
cursor++;
return value;
~~~

Phần đọc được thực hiện sau khi đã kiểm tra giới hạn. Không tăng con trỏ trước rồi mới phát hiện hết, và không dùng `ArrayIndexOutOfBoundsException` của kho mảng thay cho `NoSuchElementException` của iterator.

Một `next` hợp lệ trả `null` vẫn là một lần đọc thành công. Sau đó `remove` có thể xóa phần tử `null` vừa đọc nếu iterator hỗ trợ xóa.

## remove trên mảng: phải tính đến phần tử bị dồn

Trước khi xóa `B`:

~~~text
Chỉ số:         0   1   2   3
Dữ liệu:       [A,  B,  C,  D]
                       ^
cursor = 2 ------------+
lastReturned = 1
~~~

`cursor = 2` nghĩa là lần tới đọc `C`. Khi xóa tại chỉ số `1`, `C` và `D` dồn sang trái:

~~~text
Chỉ số:         0   1   2
Dữ liệu:       [A,  C,  D]
                   ^
cursor mới = 1 -+
~~~

Nếu giữ `cursor = 2`, lần tiếp theo trả `D` và bỏ sót `C`. Vì vậy, sau khi xóa thành công:

~~~java
cursor = lastReturned;
lastReturned = -1;
~~~

Đối với iterator đi tới trong ví dụ, `lastReturned` bằng `cursor - 1` ngay trước lúc xóa, nên đặt `cursor = lastReturned` tương đương lùi con trỏ một vị trí. Viết theo đích xóa giúp lý do rõ hơn.

`lastReturned = -1` ngăn xóa hai lần sau cùng một `next`. Nếu chỉ chỉnh con trỏ mà quên xóa dấu đích cũ, lần `remove` tiếp theo có thể xóa phần tử vừa bị dồn vào vị trí đó.

### Ba bước xóa dữ liệu và một bước giữ trạng thái

Hàm xóa của kho mảng cần:

1. Dồn đoạn sau đích xóa sang trái.
2. Giảm `size`.
3. Xóa tham chiếu ở ô đuôi không còn được dùng.
4. Tăng phiên bản thay đổi cấu trúc.

Ví dụ với `[A,B,C,D]` có dung lượng mảng lớn hơn bốn, sau khi xóa `B`, phần hoạt động là `[A,C,D]`. Ô cũ từng giữ `D` ở phía sau `size` mới phải được đặt thành `null`.

Đặt ô đuôi thành `null` không “xóa đối tượng khỏi JVM”. Nó chỉ bỏ một tham chiếu không còn cần từ mảng. Đối tượng vẫn sống nếu nơi khác còn giữ tham chiếu, chẳng hạn biến được `next` trả trước đó.

Phần cập nhật trạng thái iterator chỉ thực hiện sau khi thao tác xóa dữ liệu thành công. Không đặt `lastReturned = -1` rồi mới làm một thao tác có thể thất bại và khiến iterator không còn biết nó đã thực sự xóa hay chưa.

## Xóa theo vị trí, không xóa theo equals

Một cách viết dễ sai:

~~~java
@Override
public void remove() {
    collection.remove(lastValue);
}
~~~

Đây là minh họa lỗi nếu collection có phần tử bằng nhau và `remove(Object)` xóa phần tử bằng nhau đầu tiên.

Giả sử dữ liệu có:

~~~text
[X#first, X#second, Y#tail]
~~~

Hai đối tượng `X#first` và `X#second` khác định danh nhưng `equals` coi chúng bằng nhau. Iterator đã đọc cả hai và vừa trả `X#second`.

`Iterator.remove` phải xóa đúng lần xuất hiện vừa được trả, để lại `X#first`. Nếu gọi `collection.remove(lastValue)`, nó có thể tìm từ đầu và xóa `X#first`.

Lưu vị trí hoặc nút vừa trả là cách liên kết thao tác xóa với đúng phần tử của lượt duyệt. Một giá trị so sánh bằng nhau không đủ để xác định vị trí.

Bài toán tương tự xuất hiện với map entry, hàng đợi có phần tử trùng hoặc một view qua bộ lọc. Trước khi ủy quyền `remove`, phải biết đối tượng được ủy quyền còn đang chỉ đúng phần tử mà iterator ngoài đã trả hay không.

## Thay đổi cấu trúc và expectedModCount

Nếu collection bị thêm hoặc xóa trực tiếp trong khi iterator đang giữ chỉ số, các chỉ số có thể không còn mô tả đúng đoạn dữ liệu đã duyệt. Một chính sách thông dụng là phát hiện và từ chối tiếp tục, thường gọi là fail-fast.

Ví dụ dùng:

~~~text
Collection:
    modCount

Mỗi iterator:
    expectedModCount
~~~

Mỗi lần thêm hoặc xóa thành công, collection tăng `modCount`. Iterator ghi nhớ phiên bản lúc được tạo. Trước `next` và trước một `remove` có đích hợp lệ, nó so sánh hai số. Nếu khác, nó ném `ConcurrentModificationException`.

### Vì sao iterator tự xóa không làm chính nó hỏng?

`remove` hợp lệ cũng đổi cấu trúc, nên `modCount` tăng. Iterator biết chính nó đã thực hiện thay đổi và đã sửa con trỏ. Vì vậy sau xóa, nó cập nhật:

~~~java
expectedModCount = modCount;
~~~

Nếu quên dòng này, chính lần `next` sau khi iterator xóa hợp lệ sẽ báo lỗi thay đổi cấu trúc.

Nếu có hai iterator, mỗi iterator giữ phiên bản riêng. Iterator thứ nhất xóa và tự đồng bộ phiên bản của nó. Iterator thứ hai không được tự động sửa con trỏ; nó vẫn giữ phiên bản cũ và sẽ bị phát hiện khi đi tới điểm kiểm tra.

Không cập nhật phiên bản kỳ vọng của mọi iterator để “tránh lỗi”. Làm vậy chỉ che việc con trỏ của iterator khác chưa được điều chỉnh.

### Fail-fast không phải cơ chế đồng bộ

Một số phiên bản bằng nhau không chứng minh không có tranh chấp dữ liệu. Một bộ đếm `int` không tạo khóa, không làm các thao tác đọc/ghi thành giao dịch, và không cung cấp chính sách truy cập đồng thời an toàn.

`ConcurrentModificationException` có thể xuất hiện ngay trong một luồng nếu luồng đó sửa collection trực tiếp rồi tiếp tục dùng iterator cũ. Ngược lại, khi cập nhật không có đồng bộ giữa nhiều luồng, không được dựa vào việc ngoại lệ chắc chắn xuất hiện. Tài liệu [ConcurrentModificationException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ConcurrentModificationException.html) nêu rõ đây là cơ chế hỗ trợ phát hiện lỗi theo khả năng quan sát được.

Với collection tự viết, cần công bố riêng chính sách đồng thời. Ví dụ trong bài dành cho sử dụng tuần tự hoặc đồng bộ bên ngoài đúng cách; không cung cấp iterator snapshot hay iterator cho cập nhật đồng thời.

## Chương trình đầy đủ: viết iterator có remove

Lưu vào `IteratorCursorDemo.java`. Chương trình dùng Java 17+, không có thư viện ngoài.

`ArrayBag` là collection dung lượng cố định để phần quản lý tăng kích thước mảng không làm nhiễu bài học. Nó cho phép trùng và `null`, duyệt theo thứ tự được thêm. Khi đầy, `add` ném `IllegalStateException`. Hàm tiện ích `bag(...)` tạo đủ dung lượng cho các thí nghiệm nhỏ.

`Cursor` là lớp trong không `static`, nên truy cập đúng dữ liệu của đối tượng `ArrayBag` chứa nó. Mỗi lần `iterator()` tạo một `Cursor` mới với trạng thái riêng.

Các phép thử gồm xóa giữa/cuối, xóa liên tiếp, phần tử trùng theo `equals`, hai iterator, thay đổi ngoài iterator, dữ liệu `null`, iterator chỉ đọc, `removeIf`, `forEachRemaining` và một đoạn dùng `ListIterator` chuẩn để đối chiếu con trỏ hai chiều.

~~~java
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public class IteratorCursorDemo {
    static final class ArrayBag<E> extends AbstractCollection<E> {
        private final Object[] elements;
        private int size;
        private int modCount;

        ArrayBag(int capacity) {
            if (capacity < 0) {
                throw new IllegalArgumentException("negative capacity");
            }
            elements = new Object[capacity];
        }

        @Override public int size() {
            return size;
        }

        @Override public boolean add(E value) {
            if (size == elements.length) {
                throw new IllegalStateException("bag is full");
            }
            elements[size++] = value;
            modCount++;
            return true;
        }

        private void removeAt(int index) {
            Objects.checkIndex(index, size);
            int moved = size - index - 1;
            if (moved > 0) {
                System.arraycopy(elements, index + 1, elements, index, moved);
            }
            elements[--size] = null;
            modCount++;
        }

        @SuppressWarnings("unchecked")
        private E elementAt(int index) {
            return (E) elements[index];
        }

        @Override public Iterator<E> iterator() {
            return new Cursor();
        }

        private final class Cursor implements Iterator<E> {
            private int cursor;
            private int lastReturned = -1;
            private int expectedModCount = modCount;

            private void checkForModification() {
                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException();
                }
            }

            @Override public boolean hasNext() {
                return cursor < size;
            }

            @Override public E next() {
                checkForModification();
                if (cursor >= size) {
                    throw new NoSuchElementException();
                }
                E value = elementAt(cursor);
                lastReturned = cursor;
                cursor++;
                return value;
            }

            @Override public void remove() {
                if (lastReturned < 0) {
                    throw new IllegalStateException();
                }
                checkForModification();
                removeAt(lastReturned);
                cursor = lastReturned;
                lastReturned = -1;
                expectedModCount = modCount;
            }
        }
    }

    static final class Token {
        private final String value;
        private final String tag;

        Token(String value, String tag) {
            this.value = value;
            this.tag = tag;
        }

        @Override public boolean equals(Object other) {
            return other instanceof Token token && value.equals(token.value);
        }

        @Override public int hashCode() {
            return value.hashCode();
        }

        @Override public String toString() {
            return value + "#" + tag;
        }
    }

    @SafeVarargs
    static <E> ArrayBag<E> bag(E... values) {
        ArrayBag<E> bag = new ArrayBag<>(Math.max(16, values.length));
        for (E value : values) {
            bag.add(value);
        }
        return bag;
    }

    static String outcome(Runnable action) {
        try {
            action.run();
            return "OK";
        } catch (RuntimeException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    static <E> Iterator<E> readOnly(Iterator<E> source) {
        return new Iterator<>() {
            @Override public boolean hasNext() { return source.hasNext(); }
            @Override public E next() { return source.next(); }
        };
    }

    public static void main(String[] args) {
        ArrayBag<String> words = bag("A", "B", "C", "D");
        Iterator<String> it = words.iterator();
        System.out.println("remove-before-next = " + outcome(it::remove));
        System.out.println("repeated-hasNext = " + it.hasNext() + "," + it.hasNext());
        System.out.println("first = " + it.next());
        System.out.println("second = " + it.next());
        it.remove();
        System.out.println("after-remove-B = " + words);
        System.out.println("next-after-shift = " + it.next());
        it.remove();
        System.out.println("after-remove-C = " + words);
        System.out.println("remove-twice = " + outcome(it::remove));
        System.out.println("last = " + it.next());
        System.out.println("hasNext-at-end = " + it.hasNext());
        it.remove();
        System.out.println("after-remove-last = " + words);
        System.out.println("next-at-end = " + outcome(it::next));

        ArrayBag<Integer> all = bag(1, 2, 3, 4);
        List<Integer> visited = new ArrayList<>();
        Iterator<Integer> every = all.iterator();
        while (every.hasNext()) {
            visited.add(every.next());
            every.remove();
        }
        System.out.println("remove-all-visited = " + visited);
        System.out.println("remove-all-result = " + all);

        Token first = new Token("X", "first");
        Token second = new Token("X", "second");
        ArrayBag<Token> duplicates = bag(first, second, new Token("Y", "tail"));
        Iterator<Token> duplicateIt = duplicates.iterator();
        duplicateIt.next();
        System.out.println("duplicate-last-returned = " + duplicateIt.next());
        duplicateIt.remove();
        System.out.println("duplicate-after-remove = " + duplicates);

        ArrayBag<String> shared = bag("A", "B", "C");
        Iterator<String> left = shared.iterator();
        Iterator<String> right = shared.iterator();
        System.out.println("independent-first = " + left.next() + "," + right.next());
        left.remove();
        System.out.println("removing-iterator-continues = " + left.next());
        System.out.println("other-iterator = " + outcome(right::next));
        Iterator<String> stale = shared.iterator();
        shared.add("D");
        System.out.println("external-add = " + outcome(stale::next));

        ArrayBag<String> nullable = bag(null, "tail");
        Iterator<String> nullableIt = nullable.iterator();
        System.out.println("null-is-value = " + nullableIt.next());
        System.out.println("after-null-hasNext = " + nullableIt.hasNext());
        nullableIt.remove();
        System.out.println("after-remove-null = " + nullable);

        Iterator<String> readOnly = readOnly(bag("R").iterator());
        System.out.println("read-only-next = " + readOnly.next());
        System.out.println("read-only-remove = " + outcome(readOnly::remove));
        Iterator<String> empty = IteratorCursorDemo.<String>bag().iterator();
        System.out.println("empty-hasNext = " + empty.hasNext());
        System.out.println("empty-next = " + outcome(empty::next));

        ArrayBag<Integer> numbers = bag(1, 2, 3, 4, 5, 6);
        System.out.println("removeIf-changed = " + numbers.removeIf(n -> n % 2 == 0));
        System.out.println("removeIf-result = " + numbers);
        Iterator<String> rest = bag("F", "G", "H").iterator();
        System.out.println("before-forEachRemaining = " + rest.next());
        List<String> remaining = new ArrayList<>();
        rest.forEachRemaining(remaining::add);
        System.out.println("forEachRemaining-result = " + remaining);
        System.out.println("forEachRemaining-null = "
                + outcome(() -> rest.forEachRemaining(null)));

        List<String> list = new ArrayList<>(List.of("A", "B", "C"));
        ListIterator<String> both = list.listIterator(2);
        System.out.println("list-previous = " + both.previous());
        both.remove();
        System.out.println("list-next-after-remove = " + both.next());
        System.out.println("list-previous-again = " + both.previous());
        both.set("X");
        both.add("Y");
        System.out.println("list-after-set-add = " + list);
        System.out.println("list-set-after-add = " + outcome(() -> both.set("Z")));
        System.out.println("list-previous-after-add = " + both.previous());
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 IteratorCursorDemo.java
java IteratorCursorDemo
~~~

Mã đã được biên dịch với `--release 17` và chạy bằng OpenJDK Zulu 24.0.2. Stdout chính xác:

~~~text
remove-before-next = IllegalStateException
repeated-hasNext = true,true
first = A
second = B
after-remove-B = [A, C, D]
next-after-shift = C
after-remove-C = [A, D]
remove-twice = IllegalStateException
last = D
hasNext-at-end = false
after-remove-last = [A]
next-at-end = NoSuchElementException
remove-all-visited = [1, 2, 3, 4]
remove-all-result = []
duplicate-last-returned = X#second
duplicate-after-remove = [X#first, Y#tail]
independent-first = A,A
removing-iterator-continues = B
other-iterator = ConcurrentModificationException
external-add = ConcurrentModificationException
null-is-value = null
after-null-hasNext = true
after-remove-null = [tail]
read-only-next = R
read-only-remove = UnsupportedOperationException
empty-hasNext = false
empty-next = NoSuchElementException
removeIf-changed = true
removeIf-result = [1, 3, 5]
before-forEachRemaining = F
forEachRemaining-result = [G, H]
forEachRemaining-null = NullPointerException
list-previous = B
list-next-after-remove = C
list-previous-again = C
list-after-set-add = [A, Y, X]
list-set-after-add = IllegalStateException
list-previous-after-add = Y
~~~

## Đọc kết quả theo trạng thái con trỏ

### hasNext không tiêu thụ phần tử

`repeated-hasNext = true,true` được in trước khi đọc. Hai lần kiểm tra không làm mất `A`; `first = A` vẫn là phần tử đầu.

`remove-before-next = IllegalStateException` xác nhận chưa có đích xóa hợp lệ. Đây là iterator có hỗ trợ `remove` nhưng lời gọi diễn ra sai trạng thái, khác trường hợp iterator chỉ đọc không hỗ trợ thao tác.

### Xóa không làm bỏ qua phần tử dồn vào

Sau khi đọc `A` và `B`, `remove` để lại `[A,C,D]`. Dòng `next-after-shift = C` là bằng chứng con trỏ đã được trả về vị trí chứa `C` mới.

Xóa tiếp `C` để lại `[A,D]`. Một lần xóa nữa mà chưa đọc thêm nhận `IllegalStateException`. Sau đó iterator vẫn đọc được `D`.

`hasNext-at-end = false` xuất hiện sau khi trả `D`. Dù không còn phần tử tiếp theo, `remove` vẫn xóa `D` và collection còn `[A]`. Không dùng `hasNext` làm điều kiện quyết định có được xóa phần tử vừa trả hay không.

### Xóa mọi phần tử kiểm tra cả biên đầu và biên rỗng

Vòng lặp đọc rồi xóa từng phần tử thu được `[1,2,3,4]` đầy đủ và collection cuối `[]`. Nếu quên lùi con trỏ sau mỗi lần dồn mảng, phép thử thường bỏ qua một số phần tử.

Nó cũng kiểm tra rằng khi xóa phần tử ở chỉ số không, các phần tử sau được xử lý đúng; và sau lần xóa cuối, `cursor` cùng `size` đều trở về không.

### Hai phần tử bằng nhau vẫn phải xóa đúng lần xuất hiện

`Token.equals` chỉ so trường `value`, còn `tag` giúp nhìn thấy từng đối tượng trong stdout.

Iterator vừa trả `X#second`. Sau xóa, kết quả `[X#first,Y#tail]` cho thấy đích xóa được xác định bằng vị trí vừa trả. Việc giữ lại `X#second` và xóa `X#first` sẽ là sai, dù một phép so sánh danh sách chỉ dựa trên `equals` có thể không phát hiện.

Với kiểm thử cấu trúc có phần tử trùng, nên có dữ liệu phân biệt được định danh hoặc vị trí. Chỉ thử các chuỗi khác nhau chưa kiểm tra được lỗi xóa theo giá trị.

### Hai iterator có con trỏ và phiên bản độc lập

`independent-first = A,A` cho thấy hai iterator cùng bắt đầu từ đầu, không dùng chung một biến chỉ số trong collection.

Sau khi `left` xóa `A`, chính nó tiếp tục trả `B` vì đã điều chỉnh con trỏ và phiên bản. `right` giữ phiên bản cũ, nên `right.next` ném `ConcurrentModificationException`.

Tạo một iterator khác rồi gọi `shared.add("D")` trực tiếp cũng làm iterator cũ bị từ chối ở `next`. Đây là phép thử đơn luồng có thứ tự xác định, không phải phép thử lịch chạy ngẫu nhiên giữa nhiều luồng.

### null không phải hết dữ liệu

`null-is-value = null` là kết quả đọc thành công. `after-null-hasNext = true` cho biết `tail` vẫn còn.

Sau `nullableIt.remove`, collection là `[tail]`. Nếu dùng `lastValue == null` để suy ra chưa gọi `next`, iterator sẽ từ chối nhầm lần xóa này. Biến `lastReturned = -1` tránh việc trộn trạng thái điều khiển với giá trị dữ liệu.

### Iterator chỉ đọc có hợp đồng khác về remove

Hàm `readOnly` chỉ ủy quyền `hasNext` và `next`, giữ phần cài đặt mặc định của `Iterator.remove`. Nó đọc `R` được nhưng xóa nhận `UnsupportedOperationException`.

Đây là một lớp bọc minh họa, không biến toàn bộ collection gốc thành collection bất biến: nếu nơi khác còn giữ collection, nơi đó có thể sửa theo API được phép. Nó chỉ chặn xóa qua iterator được trả.

### Các thao tác kế thừa dùng được khi iterator đúng

`ArrayBag` không tự viết `removeIf`. Phần mặc định của `Collection` dùng iterator và xóa các phần tử thỏa điều kiện. Kết quả `[1,3,5]` cho thấy việc xóa nhiều phần tử trong một lượt không làm hỏng con trỏ.

Các thao tác khác của `AbstractCollection` cũng có thể dựa vào iterator. Vì vậy, hỗ trợ `remove` đúng không chỉ phục vụ vòng lặp tự viết; nó còn mở khả năng cho các thao tác xóa kế thừa. Xem [AbstractCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractCollection.html) và [Collection.removeIf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#removeIf(java.util.function.Predicate)).

## Không trộn remove với các cách duyệt không cùng giao thức

### for-each che iterator khỏi phần thân

Với một `Iterable` thông thường, vòng `for-each` dùng một iterator để duyệt. Bên trong thân vòng, biến phần tử không phải iterator; gọi `collection.remove(value)` là sửa qua đường khác.

Đoạn minh họa cần tránh nếu collection không cho phép sửa ngoài giao thức duyệt:

~~~java
for (String value : values) {
    if (shouldDelete(value)) {
        values.remove(value);
    }
}
~~~

`shouldDelete` ở đây là điều kiện của ứng dụng. Cách viết rõ ràng khi muốn xóa qua iterator:

~~~java
Iterator<String> iterator = values.iterator();
while (iterator.hasNext()) {
    String value = iterator.next();
    if (shouldDelete(value)) {
        iterator.remove();
    }
}
~~~

Cách thứ hai vẫn cần iterator có hỗ trợ `remove`. Một collection chỉ đọc, một iterator snapshot hoặc một triển khai khác có thể từ chối. Không có quy tắc rằng mọi iterator của mọi collection đều sửa được.

Với trường hợp đơn giản, `values.removeIf(this::shouldDelete)` có thể thể hiện ý định gọn hơn nếu collection hỗ trợ. Tuy nhiên, callback không nên tự sửa chính collection đang được phương thức đó duyệt.

Cách dịch `for-each` với `Iterable` được nêu trong [JLS 14.14.2](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.14.2). Đối với mảng, `for-each` có cơ chế khác và không tạo một `Iterator` cho phép gọi `remove`.

### forEachRemaining không phải vòng xóa qua iterator

`forEachRemaining` chuyển các phần tử còn lại cho một hành động. Trong chương trình, iterator đã đọc `F`, nên phần còn lại được gom là `[G,H]`.

Có ba giới hạn cần nhớ:

- Hành động không nên thay đổi collection nguồn, kể cả gọi `iterator.remove` từ trong callback, trừ khi triển khai công bố chính sách cho phép.
- Hành vi `remove` sau `forEachRemaining` không được hợp đồng `Iterator` tổng quát xác định.
- Nếu callback ném ngoại lệ, không giả định trạng thái tiếp tục của iterator vẫn có một ý nghĩa chung cho mọi triển khai.

Do đó, đừng suy ra rằng vì phần mặc định có hình thức “lặp `hasNext/next`” thì mọi cách xen `remove` đều được bảo đảm như vòng lặp tường minh. Với nhu cầu xóa, dùng vòng lặp `next/remove` hoặc `removeIf` theo đúng hợp đồng.

Chương trình không gọi `remove` sau `forEachRemaining`. Nó chỉ kiểm tra dữ liệu đã được chuyển và việc truyền hành động `null` nhận `NullPointerException`, kể cả iterator đã được duyệt hết. Các giới hạn này nằm trong [Iterator.forEachRemaining](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#forEachRemaining(java.util.function.Consumer)).

## Mở rộng sang ListIterator: con trỏ nằm giữa phần tử

`Iterator` đi tới chỉ cần khái niệm phần tử sẽ đọc tiếp. `ListIterator` còn đi lùi, thêm ở con trỏ và thay phần tử vừa trả. Vì vậy cần tách rõ con trỏ với phần tử đích của `remove/set`.

Với danh sách ba phần tử, có bốn vị trí con trỏ:

~~~text
Vị trí con trỏ:
    0     1     2     3
    |  A  |  B  |  C  |
~~~

Nếu `cursor = 2`:

- `next` trả `C` rồi đưa con trỏ thành `3`.
- `previous` trả `B` rồi đưa con trỏ thành `1`.

Không có “phần tử hiện tại” duy nhất tại con trỏ; nó là ranh giới giữa phần tử trước và sau. `remove/set` tác động đến phần tử mà `next` hoặc `previous` đã trả gần nhất. Xem [ListIterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ListIterator.html).

### Công thức điều chỉnh sau xóa hai chiều

Với cách biểu diễn con trỏ là chỉ số phần tử mà `next` sẽ trả, giả sử `lastReturned` là chỉ số đích xóa trước khi dồn mảng:

~~~text
Xóa tại lastReturned:

nếu lastReturned < cursor:
    cursor giảm 1
ngược lại:
    cursor giữ nguyên

lastReturned = -1
~~~

Tại sao không luôn giảm?

- Sau `next`, phần tử vừa trả nằm trước con trỏ. Xóa nó làm các phần tử sau dồn trái, nên con trỏ cần giảm.
- Sau `previous`, phần tử vừa trả nằm ngay tại chỉ số con trỏ mới. Xóa nó làm phần tử kế tiếp dồn vào đúng chỉ số đó, nên giữ con trỏ.

Trong mô hình mảng này, đặt `cursor = lastReturned` sau xóa cũng cho kết quả đúng ở cả hai hướng vì quan hệ giữa hai biến thay đổi theo hướng vừa đi. Điều không được áp dụng máy móc là luôn thực hiện `cursor--`: sau `previous`, cách đó sẽ lùi quá một vị trí. Khi đổi cách biểu diễn con trỏ hoặc chuyển sang các nút liên kết, phải suy ra cập nhật từ đúng bất biến của cấu trúc đó.

### add và set có tác động trạng thái khác nhau

`ListIterator.add(e)` chèn trước phần tử mà `next` sẽ trả và sau phần tử mà `previous` sẽ trả. Theo chỉ số, thêm tại `cursor` rồi tăng `cursor` để con trỏ nằm sau phần tử mới.

Sau thêm:

- Một lần `next` vẫn đi tới phần tử vốn ở phía sau con trỏ.
- Một lần `previous` trả phần tử vừa thêm.
- Không được `remove` hoặc `set` dựa trên lần đọc trước thao tác thêm; phải có lần `next/previous` mới.

`set(e)` chỉ thay phần tử vừa trả, không dịch con trỏ và không thêm/xóa vị trí. Nếu được hỗ trợ, nó không tự tiêu thụ quyền `set` như `remove` tiêu thụ quyền xóa. Nhưng `remove/add` sau lần đọc làm điều kiện `set` trước đó hết hiệu lực.

Đoạn cuối chương trình dùng `ArrayList.listIterator(2)`:

1. `previous` trả `B`.
2. Xóa `B`.
3. `next` trả `C`, không bỏ qua nó.
4. `previous` lại trả `C`.
5. `set("X")` thay `C` bằng `X`.
6. `add("Y")` tạo `[A,Y,X]`.
7. `set` ngay sau `add` nhận `IllegalStateException`.
8. `previous` trả `Y`, tức phần tử vừa thêm.

Đây là đối chiếu hành vi của API chuẩn. Lớp `ArrayBag.Cursor` trong bài chỉ cài `Iterator` đi tới, không công bố hỗ trợ `previous/add/set`.

## Nếu kho dữ liệu là các nút liên kết

Với danh sách liên kết, xóa một nút thường không dồn cả đoạn dữ liệu. Nhưng trạng thái đích xóa vẫn cần được giữ.

Ví dụ:

~~~text
A -> B -> C -> D

đã trả B
nút sẽ trả tiếp = C
nút vừa trả    = B
~~~

Xóa `B` phải nối đường từ `A` tới `C`, còn vị trí đi tiếp vẫn là `C`.

Với liên kết đơn, iterator thường cần đủ thông tin để tìm nút đứng trước đích xóa, hoặc dùng cấu trúc sentinel và theo dõi các nút tiền nhiệm. Chỉ giữ `nextNode` chưa chắc đủ để xóa nút vừa trả trong thời gian hằng số.

Các trường hợp phải xét riêng:

- Xóa nút đầu: cập nhật đầu danh sách.
- Xóa nút cuối: cập nhật đuôi nếu có.
- Xóa nút duy nhất: đầu và đuôi trở về trạng thái rỗng.
- Xóa nhiều nút liên tiếp qua iterator: tiền nhiệm và đích mới không được trỏ vào nút đã tháo.
- Hai iterator: thay đổi qua một iterator không tự sửa con trỏ của iterator kia.

Với liên kết đôi, biết đúng nút có thể giúp nối lại hai phía, nhưng `ListIterator` vẫn phải cập nhật vị trí và hướng vừa duyệt đúng. Cấu trúc dữ liệu khác nhau thay đổi thuật toán xóa, không bỏ được hợp đồng của iterator. `LinkedList` chuẩn là một ví dụ danh sách liên kết đôi có iterator và list iterator, được mô tả trong [LinkedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedList.html).

## Iterator bọc và việc đọc trước trong hasNext

Đối với iterator trên mảng, `hasNext` chỉ so sánh chỉ số. Với iterator lọc dữ liệu, để biết có phần tử phù hợp hay không, `hasNext` có thể cần đọc trước từ một iterator bên dưới và giữ phần tử đó trong bộ nhớ đệm.

Đọc trước là được nếu hợp đồng quan sát bên ngoài vẫn đúng: gọi `hasNext` nhiều lần không làm bỏ phần tử, `next` trả phần tử đã giữ, và hết dữ liệu được báo chính xác.

Nhưng `remove` trở nên khó hơn. Hình dung:

1. Iterator ngoài trả phần tử `A`.
2. Bên gọi hỏi `hasNext`.
3. Iterator ngoài dùng `delegate.next` nhiều lần để tìm phần tử phù hợp tiếp theo `C`.
4. Bên gọi yêu cầu `remove` của iterator ngoài để xóa `A`.

Nếu lúc này chỉ gọi `delegate.remove`, đích của delegate có thể là `C`, không còn là `A`. Chỉ ủy quyền tên phương thức giống nhau là chưa đủ.

Có ba hướng thiết kế:

- Không hỗ trợ `remove` ở lớp bọc và công bố rõ.
- Giữ cơ chế xóa chính xác đích đã trả, với kho hoặc giao thức cho phép điều đó.
- Thiết kế lại cách duyệt/đọc trước sao cho giữ được hợp đồng đã công bố.

Không được bí mật xóa phần tử khác vì “iterator bên dưới chỉ xóa được phần tử gần nhất”. Việc hỗ trợ thao tác tùy chọn là lựa chọn; xóa đúng đích khi đã hỗ trợ là nghĩa vụ.

Tương tự, một iterator snapshot không nên tự gọi `source.remove(value)` để giả lập xóa nếu việc đó không xác định được đúng lần xuất hiện và vòng đời của phần tử gốc.

## Những lỗi cài đặt thường gặp

| Lỗi | Hậu quả | Cách sửa theo mô hình trong bài |
| --- | --- | --- |
| `hasNext` tăng `cursor` | Kiểm tra làm mất phần tử | Chỉ quan sát; nếu đọc trước thì phải giữ bộ đệm ổn định |
| `next` tin rằng bên gọi luôn kiểm tra trước | Hết dữ liệu gây lỗi kho hoặc trả kết quả sai | Tự kiểm tra và ném `NoSuchElementException` |
| Dùng `null` làm dấu chưa đọc | Không xóa được phần tử `null` hợp lệ | Giữ cờ/chỉ số trạng thái riêng |
| Xóa mảng nhưng không sửa `cursor` | Bỏ qua phần tử vừa dồn trái | Đưa con trỏ về đúng vị trí cần đọc tiếp |
| Quên đặt lại `lastReturned` | Xóa hai lần sau một lần đọc | Xóa đích hợp lệ sau `remove` thành công |
| Gọi `remove(Object)` theo giá trị | Xóa nhầm lần xuất hiện bằng nhau | Xóa theo chỉ số/nút đã trả |
| Quên cập nhật phiên bản kỳ vọng sau tự xóa | Iterator tự báo lỗi ở lượt tiếp | Đồng bộ phiên bản của chính iterator vừa điều chỉnh |
| Dùng chung cursor cho mọi iterator | Hai vòng duyệt làm lệch nhau | Trạng thái nằm trong từng đối tượng iterator |
| Không xóa ô mảng dư | Giữ tham chiếu không còn cần | Đặt ô sau đoạn hoạt động thành `null` |
| Nuốt lỗi thay đổi rồi tiếp tục với chỉ số cũ | Đọc/xóa sai dữ liệu | Không tự phục hồi nếu chưa có giao thức sửa trạng thái |

Một lỗi khác là trả chính đối tượng iterator đã dùng dở từ mọi lần `collection.iterator()`. Với một collection thông thường hỗ trợ nhiều lượt duyệt, các lượt cần trạng thái riêng. Hai vòng `for-each` lồng nhau không nên dùng chung một con trỏ.

Một nguồn dữ liệu một lượt có thể có hợp đồng khác, nhưng phải mô tả rõ đó là nguồn một lượt. Không đem lựa chọn ấy áp vào một collection mà bên dùng mong đợi tạo được các lượt duyệt độc lập. Vai trò của `Iterable.iterator` và vòng lặp được giới thiệu trong [Iterable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Iterable.html).

## Cách kiểm tra iterator có hỗ trợ remove

Kiểm tra theo chuỗi thao tác, không chỉ theo từng phương thức độc lập.

### Chuỗi trạng thái cơ bản

~~~text
tạo iterator
-> remove: lỗi trạng thái
-> hasNext, hasNext: không di chuyển
-> next: nhận phần tử đầu
-> remove: xóa được
-> remove: lỗi trạng thái
-> next: nhận phần tử tiếp theo đúng
~~~

Lặp lại trên collection rỗng, một phần tử và nhiều phần tử. Với một phần tử, cần kiểm tra trường hợp đã hết phần tử phía trước nhưng vẫn xóa được phần tử vừa trả.

### Chuỗi dữ liệu có phần tử trùng và null

Dùng hai đối tượng bằng nhau nhưng nhận diện được khác nhau; đọc tới đối tượng thứ hai rồi xóa. Xác nhận còn đúng đối tượng thứ nhất.

Dùng một phần tử `null` nếu collection cho phép. Đọc `null` phải thành công, lần kiểm tra tiếp theo phải đúng, và xóa `null` phải tuân thủ cùng quy tắc trạng thái.

### Chuỗi làm thay đổi cấu trúc

Với chính sách fail-fast đã công bố:

- Tạo iterator, sửa collection trực tiếp, rồi gọi điểm kiểm tra của iterator.
- Tạo hai iterator, xóa qua một iterator, xác nhận iterator đó vẫn đi tiếp đúng.
- Xác nhận iterator còn lại không được âm thầm tự đồng bộ mà không sửa con trỏ.
- Kiểm tra riêng thao tác thất bại không làm tăng phiên bản như thể cấu trúc đã đổi.

Các kiểm tra tuần tự này tìm lỗi logic con trỏ tốt hơn một bài thử nhiều luồng ngẫu nhiên. Nếu cần hỗ trợ cập nhật đồng thời, phải có thiết kế và kiểm thử đúng loại đồng thời đó, không chỉ bổ sung bộ đếm.

### Chuỗi tương tác với phương thức kế thừa

Thử `remove(Object)`, `removeIf`, `clear` và những thao tác kế thừa mà collection công bố hỗ trợ. Nếu chúng dựa vào iterator, lỗi con trỏ có thể xuất hiện dưới một tên phương thức khác.

Đừng tuyên bố thao tác hàng loạt có tính hoàn tác nếu chưa thiết kế điều đó. Khi callback hoặc bước xử lý ném lỗi, một số phần tử có thể đã bị xóa trước đó. Giao thức iterator bảo đảm đích xóa và trạng thái duyệt trong điều kiện đã công bố, không cung cấp giao dịch nghiệp vụ.

## Bài tập tự kiểm tra

### 1. Tại sao xóa B rồi next lại trả D là sai?

Collection `[A,B,C,D]`, iterator đã trả `B`, sau đó xóa `B` và `next` trả `D`.

**Đáp án:** `C` bị dồn từ chỉ số 2 về 1 nhưng con trỏ vẫn giữ 2. Cần đưa con trỏ về vị trí chứa phần tử tiếp theo sau khi dồn, trong mô hình đi tới là `cursor = lastReturned`.

### 2. hasNext trả false rồi có được remove không?

Iterator vừa trả phần tử cuối, chưa xóa nó, và xác nhận không còn phần tử tiếp theo.

**Đáp án:** Có, nếu iterator hỗ trợ xóa và chưa có sự kiện làm mất điều kiện đó. `hasNext` chỉ quan sát phần phía trước; nó không xóa dấu phần tử vừa trả.

### 3. Vì sao không dùng lastValue == null để kiểm tra chưa gọi next?

Collection cho phép `null` và iterator vừa trả một phần tử `null`.

**Đáp án:** `null` lúc này là dữ liệu hợp lệ. Phải giữ trạng thái riêng như `lastReturned = -1` hoặc một cờ hợp lệ, không trộn với giá trị phần tử.

### 4. Xóa theo equals có đủ không?

Iterator vừa trả đối tượng thứ hai trong hai đối tượng bằng nhau. `collection.remove(lastValue)` xóa đối tượng bằng nhau đầu tiên.

**Đáp án:** Không đủ. `Iterator.remove` xóa đúng lần xuất hiện vừa được trả. Cần giữ vị trí hoặc nút, không chỉ giữ giá trị dùng cho tìm kiếm.

### 5. Iterator tự xóa rồi bị CME ở next vì sao?

Xóa đã chỉnh con trỏ và tăng `modCount` nhưng chưa cập nhật `expectedModCount`.

**Đáp án:** Iterator đang so phiên bản cũ với thay đổi do chính nó thực hiện. Sau khi sửa dữ liệu và trạng thái thành công, cập nhật phiên bản kỳ vọng của iterator đó.

### 6. Hai iterator có nên dùng chung cursor không?

Một vòng duyệt đọc một phần tử và vòng duyệt khác bắt đầu từ cùng collection.

**Đáp án:** Với collection hỗ trợ các lượt độc lập, không. Dữ liệu và phiên bản cấu trúc thuộc collection; con trỏ, phần tử vừa trả và phiên bản kỳ vọng thuộc từng iterator.

### 7. ListIterator.previous rồi remove có luôn giảm cursor không?

Con trỏ là chỉ số của phần tử sẽ được `next` trả. `previous` vừa giảm con trỏ rồi trả phần tử tại vị trí mới.

**Đáp án:** Không luôn giảm. Khi xóa phần tử nằm tại con trỏ sau `previous`, giữ chỉ số để phần tử kế tiếp dồn vào đó. Chỉ giảm khi đích xóa nằm trước con trỏ theo mô hình chỉ số đã chọn.

### 8. Có thể gọi iterator.remove trong forEachRemaining không?

Một callback muốn xóa từng phần tử vừa nhận bằng `iterator.remove`.

**Đáp án:** Không dựa vào hợp đồng `Iterator` tổng quát để làm vậy. Hành vi callback sửa nguồn không được xác định nếu triển khai không công bố chính sách riêng; `remove` sau `forEachRemaining` cũng không có bảo đảm chung. Dùng vòng `next/remove` tường minh hoặc `removeIf` phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-collections-006">modCount và việc phát hiện thay đổi cấu trúc khi phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# modCount và việc phát hiện thay đổi cấu trúc khi phù hợp

## 1. Vấn đề cần giải quyết khi tự cài đặt collection

Một iterator phải nhớ nó đang đứng ở đâu. Với danh sách dựa trên chỉ số, nó có thể lưu vị trí phần tử kế tiếp; với danh sách liên kết, nó có thể giữ tham chiếu tới nút tiếp theo. Nếu collection bị chèn, xóa hoặc tổ chức lại trong lúc iterator còn được sử dụng, thông tin đó có thể trở nên lỗi thời.

Ví dụ, danh sách ban đầu là `[A, B, C]`. Iterator đã trả về `A` và chuẩn bị đọc chỉ số `1`. Nếu một đoạn mã khác xóa phần tử đầu, danh sách thành `[B, C]`. Đọc tiếp chỉ số `1` sẽ trả về `C`, bỏ qua `B`. Các phương thức truy cập chỉ số vẫn có thể chạy bình thường, nhưng quá trình duyệt không còn tương ứng với giả định ban đầu.

Trong một collection thay đổi được, người thiết kế phải chọn chính sách cho tình huống này. Một lựa chọn phổ biến là **phát hiện thay đổi không được phép rồi báo lỗi sớm**, gọi là fail-fast. Với lớp kế thừa `AbstractList`, trường được bảo vệ `modCount` hỗ trợ lựa chọn đó; lớp con có thể dùng các iterator mà lớp cha cung cấp. Đây là một phần của việc thiết kế hành vi collection, không phải một bước tự động có sẵn cho mọi lớp triển khai `Collection`. [AbstractList trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html#modCount)

Bài học đi từ tình huống iterator bị lỗi thời đến ba việc thực hành:

1. Phân biệt thay đổi cấu trúc với thay thế một giá trị.
2. Cập nhật bộ đếm đúng ở lớp tùy chỉnh và hiểu giá trị kỳ vọng của từng iterator.
3. Kiểm tra các đường thay đổi gián tiếp, khung nhìn và giới hạn của fail-fast.

Kiến thức nền cần có là `List`, `Iterator`, `ListIterator`, kế thừa lớp trừu tượng và ngoại lệ. Các chương trình đầy đủ bên dưới dùng Java 17, không cần thư viện ngoài.

## 2. modCount ghi nhận điều gì?

### 2.1. Thay đổi cấu trúc và thay đổi giá trị

Trong ngữ cảnh danh sách, thêm hoặc xóa phần tử là thay đổi cấu trúc rõ ràng: số phần tử và quan hệ giữa chỉ số với phần tử đều có thể đổi. Khái niệm còn bao gồm những thay đổi làm xáo trộn cách duyệt đang diễn ra; không thể định nghĩa nó chỉ bằng phép so sánh hai giá trị `size()`.

Một phép `set(index, value)` của danh sách thông thường thay giá trị tại một vị trí đã tồn tại. Nó không chèn vị trí mới, không xóa vị trí cũ và thường không được tính là thay đổi cấu trúc. Chẳng hạn, tài liệu `ArrayList` phân biệt việc thêm/xóa hoặc thay đổi kích thước mảng lưu trữ với việc đặt lại giá trị phần tử. Chính sách cụ thể vẫn phải được đọc theo lớp đang sử dụng. [ArrayList trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html)

Trong lớp `TrackedList` sẽ xây dựng ở phần sau, ta chủ động chọn chính sách đơn giản:

| Thao tác của TrackedList | Có thay đổi cấu trúc? | Xử lý bộ đếm của lớp này |
| --- | --- | --- |
| `get(i)`, `size()` | Không | Giữ nguyên |
| `set(i, x)` với chỉ số hợp lệ | Không | Giữ nguyên |
| `add(i, x)` thành công | Có | Tăng một |
| `remove(i)` thành công | Có | Tăng một |
| `remove(Object)` không tìm thấy phần tử | Không có phần tử nào bị xóa | Giữ nguyên qua đường thực thi kế thừa |
| `add(i, x)` hoặc `remove(i)` thất bại do chỉ số sai | Không cập nhật dữ liệu | Giữ nguyên |
| Xóa một khoảng chứa hai phần tử bằng cách xóa từng phần tử | Có hai lần xóa cơ bản | Tăng hai qua hai lần `remove(int)` |

Bảng này là hợp đồng của ví dụ, không phải bảng quy định giá trị nội bộ của mọi lớp JDK. Những phép như tối ưu dung lượng, sắp xếp hoặc xử lý hàng loạt cần được xem xét theo chính sách và cách cài đặt riêng; không suy ra rằng “kích thước không đổi thì iterator chắc chắn còn dùng được”.

### 2.2. Không đồng nhất modCount với size hoặc số lần gọi phương thức

Hai danh sách có cùng kích thước cuối cùng vẫn có thể có lịch sử cấu trúc khác nhau:

```text
Trước: [A, B], size = 2
Xóa A: [B], size = 1
Chèn X: [X, B], size = 2
```

Iterator được tạo trước khi xóa không được phép kết luận rằng mọi thứ vẫn như cũ chỉ vì kích thước lại bằng `2`. Bộ đếm giúp nhận biết đã xảy ra thay đổi trong khoảng thời gian đó.

Ngược lại, gọi `get(0)` một trăm lần không tạo một trăm thay đổi cấu trúc. Trong ví dụ của bài, một lần `remove("missing")` cũng không tạo thay đổi nếu không tìm thấy phần tử. Thiết kế bộ đếm cần bám vào các đường cập nhật dữ liệu thực tế.

Giá trị này không phải mã định danh nghiệp vụ, số phiên bản bền vững hay nhật ký thay đổi. Không đưa nó vào API để người dùng suy luận danh sách đã từng chứa gì. Phương thức `revisionForDemo()` ở chương trình đầu chỉ phục vụ quan sát khi học.

### 2.3. Thay đổi đối tượng phần tử là vấn đề khác

Nếu một danh sách chứa các đối tượng thay đổi được, gọi một phương thức của đối tượng phần tử thường không đi qua phương thức thay đổi của danh sách. Bộ đếm cấu trúc không tự theo dõi mọi trường bên trong từng đối tượng.

Vì vậy, không có `ConcurrentModificationException` không chứng minh nội dung phần tử bất biến. Với phần tử làm khóa băm hoặc tham gia so sánh thứ tự, thay đổi trạng thái của chính phần tử còn có thể phá vỡ những giả định riêng của cấu trúc lưu trữ. Một bộ đếm trên collection không tự sửa được vấn đề đó.

## 3. Một bộ đếm của collection, một kỳ vọng cho mỗi iterator

Có thể hình dung hai vai trò:

- Collection giữ `modCount`: thông tin dùng chung về lịch sử thay đổi cấu trúc.
- Mỗi iterator giữ `expectedModCount`: giá trị mà iterator đó đang kỳ vọng.

Sơ đồ sau chỉ minh họa ý tưởng, không phải toàn bộ mã iterator:

```text
Tạo iterator:
    expectedModCount = modCount

Trước một bước cần kiểm tra:
    nếu modCount khác expectedModCount:
        báo ConcurrentModificationException

Sau khi iterator tự thực hiện một thay đổi được hỗ trợ:
    sửa lại vị trí duyệt
    expectedModCount = modCount
```

Trong mã nguồn OpenJDK 17 của `AbstractList`, `next()` kiểm tra bộ đếm trước khi đọc phần tử. `remove()` của iterator gọi đường xóa của danh sách, điều chỉnh con trỏ rồi nhận giá trị bộ đếm mới. `ListIterator` cũng quản lý kỳ vọng khi thực hiện các thao tác thay đổi của chính nó. Đây là mô tả cài đặt cụ thể dùng để giải thích thí nghiệm trong bài, không phải lời hứa rằng mọi iterator đều có cùng điểm kiểm tra. [Mã nguồn AbstractList của OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/AbstractList.java)

Ví dụ với hai iterator `a` và `b`:

```text
Collection.modCount = 7
a.expected = 7
b.expected = 7

a xóa phần tử qua a.remove():
Collection.modCount = 8
a.expected = 8
b.expected = 7
```

Iterator `a` biết chính nó vừa xóa phần tử nào và đã điều chỉnh vị trí. Iterator `b` không được cập nhật kỳ vọng cùng với `a`; nó phải nhận ra dữ liệu mình dựa vào đã thay đổi. Nếu mọi iterator đều tự chấp nhận bộ đếm mới mỗi khi đọc, cơ chế phát hiện sẽ mất tác dụng.

Các giá trị `7`, `8` ở đây chỉ giúp giải thích quan hệ bằng/khác. Chương trình sử dụng collection không cần biết bộ đếm đang mang số cụ thể nào.

## 4. Cài đặt đúng khi kế thừa AbstractList

### 4.1. Đặt trách nhiệm ở các phép thêm và xóa cơ bản

Ví dụ này dùng một `ArrayList` riêng để lưu dữ liệu, nhưng trả iterator do `AbstractList` của lớp ngoài cung cấp. Vì vậy, bộ đếm nội bộ của `ArrayList` lưu trữ không thay thế được `modCount` của `TrackedList`.

Các phương thức cần chú ý:

- `get` và `size` đọc dữ liệu.
- `set` thay giá trị, giữ nguyên bộ đếm theo chính sách đã chọn.
- `add(int, E)` và `remove(int)` thay đổi dữ liệu thành công rồi tăng bộ đếm của lớp ngoài.
- Không trả `data` ra ngoài, để các đường thay đổi cấu trúc không bỏ qua trách nhiệm cập nhật bộ đếm.

Tài liệu `AbstractList` yêu cầu lớp con muốn dùng cơ chế fail-fast này phải cập nhật trường trong các phương thức thay đổi cấu trúc được ghi đè; một lần gọi phép thêm/xóa cơ bản được nêu trong hợp đồng không được tăng trường quá một. Với cách cài đặt dưới đây, mỗi phép cơ bản thành công tăng đúng một lần. [Hợp đồng modCount của AbstractList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html#modCount)

### 4.2. Chương trình đầy đủ

Lưu vào tệp `TrackedListDemo.java`. Mọi thao tác trong chương trình chạy tuần tự trên một luồng.

```java
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class TrackedListDemo {
    static final class TrackedList<E> extends AbstractList<E> {
        private final ArrayList<E> data = new ArrayList<>();

        @Override
        public E get(int index) {
            return data.get(index);
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public E set(int index, E element) {
            return data.set(index, element);
        }

        @Override
        public void add(int index, E element) {
            data.add(index, element);
            modCount++;
        }

        @Override
        public E remove(int index) {
            E old = data.remove(index);
            modCount++;
            return old;
        }

        int revisionForDemo() {
            return modCount;
        }
    }

    private static void expectCme(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected CME: " + label);
        } catch (ConcurrentModificationException ex) {
            System.out.println(label + "=CME");
        }
    }

    public static void main(String[] args) {
        TrackedList<Integer> list = new TrackedList<>();
        list.addAll(List.of(10, 20, 30));
        System.out.println("initial=" + list);
        System.out.println("revision=" + list.revisionForDemo());

        Iterator<Integer> first = list.iterator();
        System.out.println("first=" + first.next());
        list.set(1, 200);
        System.out.println("after-set revision=" + list.revisionForDemo());
        System.out.println("next-after-set=" + first.next());
        list.add(40);
        expectCme("external-add", () -> first.next());

        Iterator<Integer> owner = list.iterator();
        Iterator<Integer> observer = list.iterator();
        System.out.println("owner-next=" + owner.next());
        owner.remove();
        System.out.println("after-owner-remove=" + list);
        System.out.println("owner-continues=" + owner.next());
        expectCme("other-iterator", () -> observer.next());

        int beforeNoOp = list.revisionForDemo();
        System.out.println("remove-missing=" + list.remove(Integer.valueOf(999)));
        System.out.println("missing-keeps-revision="
                + (beforeNoOp == list.revisionForDemo()));
        try {
            list.remove(99);
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("bad-index=IndexOutOfBoundsException");
        }
        System.out.println("failure-keeps-revision="
                + (beforeNoOp == list.revisionForDemo()));

        ListIterator<Integer> editor = list.listIterator();
        System.out.println("editor-next=" + editor.next());
        editor.set(201);
        editor.add(202);
        System.out.println("editor-continues=" + editor.next());
        System.out.println("after-editor=" + list);

        List<Integer> view = list.subList(1, 3);
        list.set(1, 222);
        System.out.println("view-after-set=" + view);
        view.remove(0);
        System.out.println("view-after-own-remove=" + view);
        System.out.println("root-after-view-remove=" + list);
        list.add(50);
        expectCme("old-view", () -> view.size());

        TrackedList<Integer> batch = new TrackedList<>();
        batch.addAll(List.of(1, 2, 3, 4));
        int batchStart = batch.revisionForDemo();
        batch.subList(1, 3).clear();
        System.out.println("batch=" + batch);
        System.out.println("batch-revision-delta="
                + (batch.revisionForDemo() - batchStart));
        int emptyStart = batch.revisionForDemo();
        batch.subList(1, 1).clear();
        System.out.println("empty-range-keeps-revision="
                + (emptyStart == batch.revisionForDemo()));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TrackedListDemo.java
java TrackedListDemo
```

Stdout:

```text
initial=[10, 20, 30]
revision=3
first=10
after-set revision=3
next-after-set=200
external-add=CME
owner-next=10
after-owner-remove=[200, 30, 40]
owner-continues=200
other-iterator=CME
remove-missing=false
missing-keeps-revision=true
bad-index=IndexOutOfBoundsException
failure-keeps-revision=true
editor-next=200
editor-continues=30
after-editor=[201, 202, 30, 40]
view-after-set=[222, 30]
view-after-own-remove=[30]
root-after-view-remove=[201, 30, 40]
old-view=CME
batch=[1, 4]
batch-revision-delta=2
empty-range-keeps-revision=true
```

### 4.3. Đọc kết quả theo đường thay đổi dữ liệu

`revision=3` xuất hiện vì ví dụ thêm ba phần tử qua các phép thêm cơ bản. Sau `set(1, 200)`, bộ đếm vẫn bằng `3`; iterator đầu tiên đọc tiếp được giá trị mới `200`. Điều đó cũng cho thấy iterator này không giữ một bản sao độc lập của toàn bộ nội dung ban đầu.

Sau `list.add(40)`, iterator đầu tiên không còn có kỳ vọng phù hợp. Lời gọi `first.next()` báo lỗi trong thí nghiệm. Không có luồng thứ hai: thay đổi ngoài iterator trong cùng một luồng đã đủ tạo tình huống cần phát hiện.

`owner.remove()` xóa phần tử mà chính `owner` vừa trả về. Iterator này tiếp tục được, nhưng `observer`, vốn được tạo trước lần xóa, bị vô hiệu hóa. Đây là lý do chỉ cập nhật kỳ vọng cho iterator thực hiện thay đổi.

Tiếp theo, xóa giá trị không tồn tại trả về `false`; xóa theo chỉ số `99` báo `IndexOutOfBoundsException`. Hai dòng kiểm tra bộ đếm đều là `true`, vì các nhánh này không thực hiện một lần xóa thành công trong lớp ví dụ.

Phần `ListIterator` thay `200` thành `201`, chèn `202`, rồi tiếp tục đến `30`. Sau thao tác chèn, con trỏ nằm sau phần tử vừa chèn; không phải chương trình quên đọc `202`. Điều chỉnh vị trí và cập nhật kỳ vọng đều thuộc trách nhiệm iterator. Các quy tắc `set`, `add`, `remove` của `ListIterator` cần được tuân thủ cùng với cơ chế bộ đếm. [ListIterator trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ListIterator.html)

Hai đoạn cuối về khung nhìn và xóa khoảng sẽ được phân tích riêng ở các phần sau. Chúng nằm trong cùng chương trình để kiểm tra một lớp qua nhiều đường gọi, không chỉ qua `add` và `remove` trực tiếp.

### 4.4. Vì sao tăng sau khi thay đổi thành công?

Trong `add(int, E)`, `data.add` có thể từ chối chỉ số sai trước khi sửa dữ liệu. Nếu viết `modCount++` trước lời gọi đó, một thao tác thất bại có thể làm iterator đang hợp lệ bị vô hiệu hóa, dù danh sách không thay đổi.

Tương tự, `remove(int)` chỉ tăng sau khi lấy được phần tử đã xóa. Quy tắc này rõ ràng khi phép cơ bản của lớp lưu trữ hoặc thành công, hoặc từ chối trước khi sửa dữ liệu.

Không mở rộng kết luận đó thành “mọi phép phức hợp ném ngoại lệ thì không thay đổi gì”. Một thao tác hàng loạt có thể thay đổi một phần rồi thất bại. Nếu tự cài đặt một thuật toán như vậy, phải đảm bảo những thay đổi đã thực sự xảy ra được phản ánh vào bộ đếm; hoặc thiết kế rõ tính nguyên vẹn của thao tác trước khi bắt đầu sửa dữ liệu. Bộ đếm không tự cung cấp khả năng hoàn tác.

### 4.5. Không tăng hai lần trên cùng một đường gọi

Nếu `add(E)` gọi tiếp `add(size(), E)`, không tăng thêm lần nữa chỉ vì cả hai phương thức đều mang tên `add`. Hãy xác định nơi sở hữu lần thay đổi dữ liệu thực tế.

Tương tự, nếu `remove(Object)` kế thừa sử dụng iterator rồi iterator gọi `remove(int)`, trách nhiệm tăng đã được thực hiện ở `remove(int)`. Thêm một phép tăng ở lớp bao ngoài có thể làm chính sách mất nhất quán.

Khi tối ưu một phương thức bằng cách truy cập trực tiếp kho lưu trữ, hãy kiểm tra lại đường đi. Việc trước đây bộ đếm được cập nhật gián tiếp qua iterator không đảm bảo phiên bản tối ưu vẫn đi qua đường đó.


## 5. Ba lỗi thiết kế mà bộ đếm cần giúp phân biệt

Chương trình tiếp theo cố ý xây dựng những biến thể sai so với mục tiêu **danh sách thay đổi được, thay thế giá trị không phá cấu trúc, iterator có cơ chế phát hiện thay đổi cấu trúc ngoài nó**.

`countStructural` và `countReplacement` chỉ là hai công tắc phục vụ thí nghiệm. Một lớp thực tế không nên cung cấp công tắc để lúc bật lúc tắt hợp đồng iterator như thế này.

### 5.1. Chương trình đầy đủ

Lưu vào tệp `CounterDefectsDemo.java`.

```java
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

public class CounterDefectsDemo {
    static final class BrokenList<E> extends AbstractList<E> {
        private final ArrayList<E> data;
        private final boolean countStructural;
        private final boolean countReplacement;

        BrokenList(List<E> initial, boolean countStructural,
                   boolean countReplacement) {
            data = new ArrayList<>(initial);
            this.countStructural = countStructural;
            this.countReplacement = countReplacement;
        }

        @Override
        public E get(int index) {
            return data.get(index);
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public E set(int index, E element) {
            E old = data.set(index, element);
            if (countReplacement) {
                modCount++;
            }
            return old;
        }

        @Override
        public void add(int index, E element) {
            data.add(index, element);
            if (countStructural) {
                modCount++;
            }
        }

        @Override
        public E remove(int index) {
            E old = data.remove(index);
            if (countStructural) {
                modCount++;
            }
            return old;
        }
    }

    public static void main(String[] args) {
        BrokenList<String> missing =
                new BrokenList<>(List.of("A", "B", "C"), false, false);
        Iterator<String> stale = missing.iterator();
        System.out.println("missing-first=" + stale.next());
        missing.remove(0);
        System.out.println("missing-current=" + missing);
        System.out.println("missing-next=" + stale.next());

        BrokenList<String> tooEager =
                new BrokenList<>(List.of("A", "B"), true, true);
        Iterator<String> reader = tooEager.iterator();
        tooEager.set(1, "B2");
        try {
            reader.next();
            throw new AssertionError("Expected false positive");
        } catch (ConcurrentModificationException ex) {
            System.out.println("replacement=CME");
        }

        BrokenList<String> sameSize =
                new BrokenList<>(List.of("A", "B"), true, false);
        Iterator<String> old = sameSize.iterator();
        int savedSize = sameSize.size();
        sameSize.remove(0);
        sameSize.add(0, "X");
        System.out.println("same-size=" + (savedSize == sameSize.size()));
        try {
            old.next();
            throw new AssertionError("Expected CME");
        } catch (ConcurrentModificationException ex) {
            System.out.println("same-size-history=CME");
        }

        BrokenList<String> boundary =
                new BrokenList<>(List.of("A", "B"), true, false);
        Iterator<String> cursor = boundary.iterator();
        System.out.println("boundary-first=" + cursor.next());
        boundary.remove(0);
        System.out.println("boundary-hasNext=" + cursor.hasNext());
        System.out.println("boundary-current=" + boundary);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 CounterDefectsDemo.java
java CounterDefectsDemo
```

Stdout:

```text
missing-first=A
missing-current=[B, C]
missing-next=C
replacement=CME
same-size=true
same-size-history=CME
boundary-first=A
boundary-hasNext=false
boundary-current=[B]
```

### 5.2. Bỏ quên cập nhật bộ đếm: không báo lỗi nhưng duyệt sai giả định

Biến thể `missing` xóa dữ liệu thật nhưng không tăng `modCount`. Iterator đã đọc `A`, sau đó danh sách thành `[B, C]`. Lần đọc kế tiếp trả về `C`.

Vì lớp này kế thừa iterator dựa trên chỉ số, con trỏ tiếp tục từ chỉ số `1`. Không có thông tin nào báo cho iterator rằng phần tử ở chỉ số `0` vừa bị xóa. Trong chính thí nghiệm này, hậu quả quan sát được là `B` bị bỏ qua.

Đừng sửa bằng cách bắt ngoại lệ rồi bỏ qua, vì ở đây không có ngoại lệ để bắt. Cần sửa đường thay đổi cấu trúc đang bỏ quên bộ đếm. Đồng thời, dùng mã ứng dụng đúng hợp đồng: nếu muốn xóa trong quá trình duyệt, sử dụng thao tác được iterator hỗ trợ hoặc tổ chức hai giai đoạn duyệt rồi cập nhật.

Sự thiếu vắng bộ đếm chỉ là lỗi khi nó trái với cơ chế và chính sách đã chọn. `AbstractList` cho phép lớp con không sử dụng cơ chế fail-fast; không được từ ví dụ này suy ra mọi collection bắt buộc phải có một trường mang tên `modCount`.

### 5.3. Đếm cả thay thế giá trị: báo lỗi không đúng với chính sách mong muốn

Biến thể `tooEager` tăng bộ đếm trong `set`. Nó không thay đổi số vị trí nhưng vẫn khiến iterator báo lỗi ngay khi đọc.

Nếu lớp được thiết kế với chính sách thay thế giá trị thông thường như `TrackedList`, đây là báo động giả do chính cách ghi nhận của lớp tạo ra. Người viết đã làm bộ đếm mang nghĩa “có phương thức sửa dữ liệu được gọi”, trong khi iterator đang dùng nó như dấu hiệu “giả định về cấu trúc đã hết hiệu lực”.

Một collection khác có thể công bố chính sách duyệt nghiêm ngặt hơn, nhưng chính sách đó phải được thiết kế và diễn giải rõ. Không thay nghĩa của trường chỉ để tăng số trường hợp ném ngoại lệ.

### 5.4. Kiểm tra kích thước: bỏ sót lịch sử xóa rồi chèn

Biến thể `sameSize` xóa `A`, sau đó chèn `X`. Kết quả `same-size=true` xác nhận phép so sánh kích thước không thấy khác biệt giữa trước và sau.

Bộ đếm của biến thể này tăng ở cả hai phép thay đổi cấu trúc. Iterator cũ vẫn nhận ra có thay đổi và báo lỗi. Điều cần phát hiện là giả định của quá trình duyệt đã bị tác động, không phải danh sách cuối cùng có nhiều phần tử hơn hay ít phần tử hơn.

Đây cũng là lý do không đặt `modCount = size()`. Hai đại lượng biểu diễn hai ý nghĩa khác nhau. Không đặt lại bộ đếm về `0` khi `clear()` chỉ vì danh sách lúc đó có kích thước bằng `0`.

### 5.5. hasNext trả về bình thường không phải giấy chứng nhận iterator còn hợp lệ

Phần `boundary` đọc `A`, rồi xóa phần tử đầu bằng danh sách. Iterator đang có con trỏ bằng `1`; danh sách mới cũng có kích thước bằng `1`. `hasNext()` trả về `false`, nên một vòng lặp chỉ tiếp tục khi nó trả `true` sẽ kết thúc mà không đi tới bước đọc tiếp.

Kết quả này được kiểm chứng với iterator kế thừa từ `AbstractList` trên Java 17 đang chạy thí nghiệm. Nó phù hợp với mã OpenJDK 17, nơi `hasNext()` của iterator đó so sánh con trỏ với kích thước mà không kiểm tra bộ đếm ở bước này. Không biến chi tiết cài đặt đó thành quy tắc cho tất cả các iterator. [Mã iterator trong AbstractList của OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/AbstractList.java)

Vì vậy, không viết quy trình “gọi `hasNext()`; nếu chưa lỗi thì cứ sửa danh sách rồi dùng tiếp iterator”. Điều cần làm là tránh vi phạm chính sách duyệt ngay từ thiết kế, thay vì chờ một lời gọi cụ thể bắt lỗi thay mình.

## 6. Theo dõi mọi đường thay đổi, kể cả phương thức kế thừa

### 6.1. Một API công khai có thể đi qua nhiều lớp

Ở `TrackedList`, chỉ ghi đè các phép cơ bản đã đủ để một số phương thức kế thừa hoạt động thông qua chúng. Ví dụ trong chương trình:

```text
addAll
  → thêm từng phần tử
  → add(int, E) của TrackedList
  → sửa data
  → tăng modCount

subList(...).clear()
  → đường xóa khoảng kế thừa
  → iterator xóa từng phần tử trong khoảng
  → remove(int) của TrackedList
  → sửa data
  → tăng modCount
```

Sơ đồ mô tả đường chạy đang được sử dụng trong lớp ví dụ và được kiểm tra bằng stdout. Khi ghi đè thêm phương thức để tăng hiệu năng, đường chạy có thể thay đổi.

Tài liệu `AbstractCollection` mô tả nhiều phương thức mặc định dựa trên `iterator()`, `add` và `Iterator.remove()`. Vì vậy, tự cài đặt iterator không chỉ tác động vòng lặp `for`; nó còn ảnh hưởng những phép kế thừa sử dụng iterator bên trong. [AbstractCollection trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractCollection.html)

### 6.2. Phép hàng loạt không nhất thiết tăng bằng số phần tử đầu vào

Ở chương trình đầu:

```text
batch=[1, 4]
batch-revision-delta=2
```

Hai phần tử `2` và `3` đã được xóa qua hai phép xóa cơ bản, nên chênh lệch bằng `2`. Đây là kết quả của lớp ví dụ, không phải yêu cầu rằng mọi `clear`, `removeAll` hoặc `addAll` phải tăng bộ đếm đúng bằng số phần tử bị tác động.

Nếu một lớp khác xóa cả khoảng bằng một thuật toán trực tiếp trên kho lưu trữ, nó có thể ghi nhận thay đổi theo đơn vị thao tác khác. Cần giữ cơ chế phát hiện nhất quán, tôn trọng hợp đồng lớp cha và không bỏ qua các thay đổi mà iterator cũ phải coi là không còn hợp lệ.

Khi thiết kế phương thức hàng loạt, xác định một trong các đường rõ ràng:

- **Gọi các phép cơ bản đã cập nhật bộ đếm:** không bổ sung một lần tăng chung chỉ vì phương thức bao ngoài cũng là phương thức thay đổi.
- **Sửa kho lưu trữ trực tiếp:** phương thức trực tiếp đó chịu trách nhiệm phản ánh các thay đổi vào cơ chế kiểm tra.

Không trộn hai đường một cách vô tình. Nếu phương thức thực hiện được một phần rồi thất bại, xem xét trạng thái còn lại và iterator hiện có, thay vì chỉ kiểm tra trường hợp thành công hoàn toàn.

### 6.3. No-op cần được hiểu theo lớp cụ thể

Trong `TrackedList`, xóa khoảng rỗng không gọi phép xóa cơ bản, nên kết quả là:

```text
empty-range-keeps-revision=true
```

Đây là một đặc điểm kiểm chứng được của lớp đang xây dựng. Không lấy nó làm khẳng định về mọi thao tác “trông như không làm gì” trên mọi collection.

Một thao tác không làm đổi `size()` vẫn có thể ảnh hưởng giả định của iterator. Ngược lại, một lời gọi không tìm thấy phần tử để xóa có thể chỉ đọc dữ liệu và trả `false`. Phải đọc hợp đồng và lần theo đường thực thi cụ thể trước khi quyết định nó thuộc trường hợp nào.

### 6.4. Những đường dễ bỏ sót trong lớp tùy chỉnh

Khi bổ sung tính năng, rà soát theo khả năng sửa kho lưu trữ:

| Đường vào | Điều cần kiểm tra trong chính lớp đang viết |
| --- | --- |
| `add`, `remove` cơ bản | Thay đổi thành công được ghi nhận đúng một lần ở nơi sở hữu |
| `clear`, xóa khoảng | Có đi qua phép xóa cơ bản hay tự sửa dữ liệu trực tiếp? |
| `addAll`, `removeAll`, `retainAll` | Nhánh tối ưu có còn cập nhật cơ chế kiểm tra? |
| `removeIf` | Có dùng iterator đúng hợp đồng, và xử lý thế nào nếu điều kiện ném ngoại lệ? |
| Iterator tự xóa/chèn | Cập nhật cả dữ liệu, vị trí và kỳ vọng riêng của iterator |
| Khung nhìn có thể thay đổi | Thay đổi được chuyển đúng về cấu trúc gốc |
| Mã truy cập kho lưu trữ trực tiếp | Có đường nào vượt qua toàn bộ cơ chế theo dõi? |

Không cần ghi đè mọi phương thức trong bảng. Điều cần chứng minh là những phương thức lớp cho phép đều đi qua đường phù hợp. Kế thừa đúng có thể đơn giản và đáng tin hơn một tập lớn các bản ghi đè gần giống nhau.

## 7. subList: cùng dữ liệu nền, nhiều trạng thái quan sát

`subList` là khung nhìn của một phần danh sách, không phải một danh sách sao chép độc lập. Những thay thế giá trị không làm thay đổi cấu trúc được phản ánh giữa hai phía. Hợp đồng `List` quy định rằng ngữ nghĩa của khung nhìn trở nên không xác định nếu danh sách nền bị thay đổi cấu trúc qua đường khác với chính khung nhìn đó. [List.subList trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#subList(int,int))

Phần khung nhìn của `TrackedListDemo` thể hiện ba tình huống tách biệt.

### 7.1. Thay giá trị qua danh sách gốc

Sau khi tạo `view = list.subList(1, 3)`, chương trình gọi `list.set(1, 222)`. Giá trị đầu của khung nhìn đổi thành `222`:

```text
view-after-set=[222, 30]
```

Lớp ví dụ không coi `set` là thay đổi cấu trúc. Iterator và khung nhìn không phải ảnh chụp của giá trị cũ; chúng vẫn truy cập dữ liệu nền.

### 7.2. Xóa qua chính khung nhìn

Chương trình gọi `view.remove(0)`. Kết quả:

```text
view-after-own-remove=[30]
root-after-view-remove=[201, 30, 40]
```

Thao tác được chuyển về danh sách gốc với vị trí phù hợp; trạng thái của chính khung nhìn được cập nhật qua đường mà nó hỗ trợ. Danh sách gốc và khung nhìn tiếp tục nhất quán với lần xóa này.

Điều đó không có nghĩa mọi iterator được tạo từ trước đều còn hợp lệ. Nếu đã có một iterator riêng trên dữ liệu nền, lần xóa qua khung nhìn vẫn là thay đổi bên ngoài iterator đó.

### 7.3. Thêm qua danh sách gốc rồi dùng lại khung nhìn cũ

Sau `list.add(50)`, chương trình thử `view.size()` và quan sát `old-view=CME`. Đây là cơ chế phát hiện của khung nhìn được kế thừa trong thí nghiệm.

Không suy diễn từ kết quả này rằng mọi lớp triển khai `List` bắt buộc phải báo cùng ngoại lệ tại cùng phương thức sau khi người dùng đã vi phạm điều kiện sử dụng khung nhìn. Cũng không tiếp tục sử dụng khung nhìn cũ sau khi bắt lỗi. Hãy tổ chức các thay đổi qua đường hợp lệ, hoặc tạo lại khung nhìn từ danh sách đã cập nhật khi bắt đầu một công việc mới.

Nếu cần một danh sách độc lập để duyệt trong lúc danh sách gốc thay đổi về sau, việc sao chép cần được lựa chọn có chủ đích. Sao chép không tự làm các đối tượng phần tử bất biến, và việc tạo bản sao trong môi trường có ghi đồng thời vẫn cần chính sách truy cập an toàn.


## 8. Khi không kế thừa iterator của AbstractList

### 8.1. Một trường mang tên modCount không tự tạo cơ chế kiểm tra

`AbstractCollection` yêu cầu lớp con cung cấp iterator và kích thước để hình thành một collection tối thiểu. Nó không cung cấp sẵn trường `modCount` cùng iterator như `AbstractList`. Nếu chọn một cơ chế phát hiện thay đổi bằng bộ đếm, người viết phải kết nối bộ đếm đó với iterator của mình. [AbstractCollection trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractCollection.html)

Chương trình dưới đây xây dựng một collection nhỏ cho phép trùng phần tử. Nó không phải `Set` và không có API truy cập theo chỉ số ở bên ngoài. Dữ liệu được lưu trong một `ArrayList` riêng; iterator tự cài đặt đọc dữ liệu theo chỉ số và dùng `structureVersion` của lớp ngoài.

Tên `structureVersion` được dùng có chủ đích: đây là trường do lớp này định nghĩa, không phải trường kế thừa từ `AbstractList`. Cơ chế vẫn có hai vế là phiên bản hiện tại và phiên bản kỳ vọng riêng của iterator.

### 8.2. Chương trình đầy đủ

Lưu vào tệp `ExplicitIteratorDemo.java`.

```java
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class ExplicitIteratorDemo {
    static final class VersionedBag<E> extends AbstractCollection<E> {
        private final ArrayList<E> data = new ArrayList<>();
        private int structureVersion;

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public boolean add(E value) {
            data.add(value);
            structureVersion++;
            return true;
        }

        private void removeAt(int index) {
            data.remove(index);
            structureVersion++;
        }

        @Override
        public Iterator<E> iterator() {
            return new Iterator<E>() {
                private int cursor;
                private int lastReturned = -1;
                private int expectedVersion = structureVersion;

                private void checkVersion() {
                    if (structureVersion != expectedVersion) {
                        throw new ConcurrentModificationException();
                    }
                }

                @Override
                public boolean hasNext() {
                    checkVersion();
                    return cursor < data.size();
                }

                @Override
                public E next() {
                    checkVersion();
                    if (cursor >= data.size()) {
                        throw new NoSuchElementException();
                    }
                    E value = data.get(cursor);
                    lastReturned = cursor;
                    cursor++;
                    return value;
                }

                @Override
                public void remove() {
                    checkVersion();
                    if (lastReturned < 0) {
                        throw new IllegalStateException();
                    }
                    removeAt(lastReturned);
                    cursor = lastReturned;
                    lastReturned = -1;
                    expectedVersion = structureVersion;
                }
            };
        }
    }

    public static void main(String[] args) {
        VersionedBag<String> bag = new VersionedBag<>();
        bag.addAll(List.of("A", "B", "C"));
        Iterator<String> it = bag.iterator();
        System.out.println("first=" + it.next());
        it.remove();
        System.out.println("after-remove=" + bag);
        try {
            it.remove();
        } catch (IllegalStateException ex) {
            System.out.println("double-remove=IllegalStateException");
        }
        System.out.println("continues=" + it.next());

        Iterator<String> stale = bag.iterator();
        bag.add("D");
        try {
            stale.hasNext();
            throw new AssertionError("Expected CME");
        } catch (ConcurrentModificationException ex) {
            System.out.println("custom-hasNext=CME");
        }

        System.out.println("remove-B=" + bag.remove("B"));
        System.out.println("after-inherited-remove=" + bag);
        System.out.println("remove-missing=" + bag.remove("missing"));
        bag.clear();
        System.out.println("after-inherited-clear=" + bag);
        Iterator<String> empty = bag.iterator();
        System.out.println("empty-hasNext=" + empty.hasNext());
        try {
            empty.next();
        } catch (NoSuchElementException ex) {
            System.out.println("empty-next=NoSuchElementException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ExplicitIteratorDemo.java
java ExplicitIteratorDemo
```

Stdout:

```text
first=A
after-remove=[B, C]
double-remove=IllegalStateException
continues=B
custom-hasNext=CME
remove-B=true
after-inherited-remove=[C, D]
remove-missing=false
after-inherited-clear=[]
empty-hasNext=false
empty-next=NoSuchElementException
```

### 8.3. Các bất biến cần giữ khi tự viết iterator

Trong chương trình này, ba trường riêng của iterator phối hợp với nhau:

| Trường | Ý nghĩa trong VersionedBag |
| --- | --- |
| `cursor` | Chỉ số sẽ đọc ở lần `next()` kế tiếp |
| `lastReturned` | Vị trí phần tử vừa trả về và còn được phép xóa; `-1` nghĩa là chưa có |
| `expectedVersion` | Phiên bản cấu trúc mà iterator hiện tại chấp nhận |

Sau khi `next()` trả về một phần tử, `cursor` tăng lên. Khi iterator xóa phần tử đó, những phần tử phía sau dồn trái, nên chương trình đưa `cursor` về vị trí vừa xóa. Nếu quên bước này, phần tử kế tiếp có thể bị bỏ qua.

Sau lần xóa hợp lệ:

1. Collection tăng `structureVersion` trong `removeAt`.
2. Iterator điều chỉnh vị trí đọc.
3. Iterator đặt lại `lastReturned` để chặn xóa lặp cùng một kết quả.
4. Iterator nhận phiên bản mới vào `expectedVersion`.

Chỉ thay đổi bước cuối mà không sửa con trỏ có thể làm iterator “không báo lỗi” nhưng vẫn đọc sai. Chỉ sửa con trỏ mà không nhận phiên bản mới lại làm iterator báo lỗi vì chính lần xóa hợp lệ của nó.

Quy tắc `remove()` chỉ được gọi sau một lần `next()` hợp lệ và không được gọi lặp cho cùng kết quả thuộc hợp đồng iterator. `next()` khi hết phần tử phải báo `NoSuchElementException`; đây không phải tình huống thay đổi cấu trúc. [Iterator trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html)

### 8.4. Kiểm tra ở hasNext là lựa chọn của lớp này

`VersionedBag` chủ động gọi `checkVersion()` trong cả `hasNext()`, `next()` và `remove()`. Vì vậy, `stale.hasNext()` báo lỗi sau khi collection được thêm phần tử.

So sánh với thí nghiệm `boundary` ở phần trước cho thấy không nên học thuộc một điểm ném ngoại lệ cố định cho toàn bộ Java Collections Framework. Tự cài đặt iterator nghĩa là phải chọn rõ các điểm kiểm tra và đáp ứng hợp đồng đã công bố.

Việc thêm một điểm kiểm tra có thể giúp phát hiện một số cách sử dụng sai sớm hơn. Nó không tạo một vùng thực thi nguyên tử bao quanh toàn bộ thao tác đọc.

### 8.5. remove và clear kế thừa cũng dùng iterator này

`bag.remove("B")` trả về `true` rồi dữ liệu còn `[C, D]`. `bag.clear()` xóa hết phần tử. Trong chương trình, cả hai hoạt động đúng dù lớp không ghi đè các phương thức đó: đường thực thi kế thừa sử dụng iterator có hỗ trợ xóa.

Ví dụ này kiểm tra nhiều hơn một lời gọi `Iterator.remove()` riêng lẻ. Nó cho thấy cài đặt iterator là một phần nền tảng của các phép collection kế thừa.

Dẫu vậy, đây vẫn là lớp minh họa nhỏ: chưa có cam kết dùng đồng thời, chưa phân tích hiệu năng xóa hàng loạt và chưa xây dựng tập kiểm thử đầy đủ cho một thư viện. Không diễn giải một lần chạy thành bằng chứng về mọi hợp đồng mà một collection công khai cần đáp ứng.

## 9. Chọn fail-fast khi nào là phù hợp?

### 9.1. Khi lớp có cấu trúc thay đổi và muốn phát hiện iterator bị lỗi thời

Fail-fast hữu ích khi thao tác chèn/xóa làm vị trí duyệt cũ không còn đáng tin, người dùng được yêu cầu không thay đổi cấu trúc qua đường ngoài iterator, và báo lỗi sớm giúp tìm ra cách sử dụng sai.

Với lớp `TrackedList`, tận dụng iterator của `AbstractList` cùng `modCount` là cách tự nhiên. Với `VersionedBag`, iterator được viết riêng nên bộ đếm và các điểm kiểm tra cũng phải được nối riêng.

Điểm quyết định là **chính sách duyệt và cấu trúc dữ liệu**, không phải tên trường. Đổi `structureVersion` thành `modCount` trong chương trình thứ ba không làm nó tự động trở thành một lớp kế thừa `AbstractList`.

### 9.2. Không áp đặt cùng cơ chế cho mọi kiểu collection

Một số lựa chọn có mục tiêu khác nhau:

| Chính sách hoặc cấu trúc | Điều người viết cần quyết định |
| --- | --- |
| Duyệt trực tiếp dữ liệu thay đổi được, có quy tắc cấm thay đổi ngoài iterator | Có thể dùng bộ đếm để phát hiện vi phạm |
| Dữ liệu thực sự không thay đổi cấu trúc sau khi tạo | Không thêm bộ đếm chỉ để có một trường cùng tên |
| Iterator duyệt bản sao nội dung tại thời điểm tạo | Thay đổi của nguồn về sau được xử lý theo hợp đồng ảnh chụp |
| Collection dùng đồng thời với iterator có tính nhất quán yếu | Tuân theo chính sách quan sát của cấu trúc đồng thời đó |
| Lớp bao hoặc khung nhìn của một collection khác | Xác định dữ liệu nào được dùng chung và cơ chế nào chịu trách nhiệm phát hiện |

Ví dụ, iterator của `CopyOnWriteArrayList` dùng ảnh chụp mảng và không báo `ConcurrentModificationException`; các thao tác thay đổi qua iterator đó không được hỗ trợ. Đây là lựa chọn thiết kế của lớp này, không phải một bản fail-fast bị thiếu bộ đếm. [CopyOnWriteArrayList trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)

Iterator của `ConcurrentHashMap` có thể phản ánh trạng thái ở những thời điểm khác nhau trong quá trình quan sát và không báo `ConcurrentModificationException`. Không thay chính sách đó bằng cơ chế iterator thông thường chỉ vì cũng đang viết một collection. [ConcurrentHashMap trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

Một khung nhìn không cho phép sửa qua chính nó cũng chưa chắc là dữ liệu bất biến: cấu trúc gốc có thể vẫn được sửa qua tham chiếu khác. Khi chọn cơ chế kiểm tra, phải nhìn vào toàn bộ đường sở hữu và chia sẻ dữ liệu.

### 9.3. Với Map và Set tùy chỉnh, bắt đầu từ chính sách của cấu trúc đó

`AbstractMap` không cung cấp sẵn cơ chế `modCount` như `AbstractList`. Các phương thức của nó được xây dựng quanh `entrySet`; iterator và khả năng xóa qua iterator của tập entry là một phần quan trọng khi cài đặt map thay đổi được. [AbstractMap trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.html)

`AbstractSet` kế thừa nền tảng từ `AbstractCollection` và bổ sung các yêu cầu của tập hợp; không vì kế thừa nó mà tự nhận được toàn bộ iterator fail-fast. [AbstractSet trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractSet.html)

Nếu xây dựng map với cơ chế kiểm tra riêng, cần xem cả các khung nhìn khóa, giá trị và entry. Một thay đổi làm iterator của cấu trúc gốc lỗi thời có thể được thực hiện qua khung nhìn; đó vẫn là đường phải được quản lý.

Với tập hợp, thử thêm một phần tử vốn đã có không đồng nghĩa chắc chắn phát sinh một lần chèn. Với map, cập nhật giá trị của khóa đã có cũng cần được phân biệt với tạo ánh xạ mới theo hợp đồng đang chọn. Không sao chép máy móc bảng của danh sách sang mọi cấu trúc.

## 10. modCount không làm collection an toàn khi dùng đồng thời

`ConcurrentModificationException` có thể xuất hiện trong một luồng. Tên ngoại lệ không chứng minh có hai luồng chạy cùng lúc. Các chương trình của bài cố ý dùng thứ tự gọi tuần tự để kiểm tra được chính xác nguyên nhân và stdout.

Ở chiều ngược lại, khi có các luồng sửa dữ liệu mà không có cơ chế phối hợp phù hợp, fail-fast chỉ phát hiện theo nỗ lực tốt nhất. Tài liệu ngoại lệ yêu cầu không dựa vào việc nó chắc chắn được ném để làm nền tảng cho tính đúng của chương trình. [ConcurrentModificationException trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ConcurrentModificationException.html)

Một phép so sánh bộ đếm không:

- Khóa cấu trúc đang được đọc.
- Làm toàn bộ quá trình kiểm tra rồi lấy phần tử trở thành một thao tác nguyên tử.
- Tự bảo đảm các trường dữ liệu được nhìn thấy nhất quán giữa các luồng.
- Hoàn tác phần thay đổi đã xảy ra trước khi báo lỗi.

Trong `VersionedBag`, một luồng khác vẫn có thể sửa dữ liệu giữa `checkVersion()` và `data.get(cursor)` nếu chương trình cho phép truy cập như vậy. Thêm kiểm tra vào nhiều vị trí không loại bỏ được vấn đề thiết kế truy cập đồng thời.

Đổi riêng bộ đếm sang `volatile`, `AtomicInteger` hoặc một kiểu số lớn hơn cũng không biến toàn bộ collection thành cấu trúc an toàn cho nhiều luồng. Các thao tác trên kho dữ liệu, con trỏ và quan hệ giữa nhiều bước vẫn cần một chính sách phối hợp chung.

Những cách tổ chức phù hợp có thể là giới hạn collection trong một luồng, dùng khóa theo một giao thức nhất quán, duyệt bản sao được tạo an toàn, hoặc chọn cấu trúc trong `java.util.concurrent` có hợp đồng thích hợp. Nếu dùng lớp bao đồng bộ từ `Collections`, tài liệu còn yêu cầu đồng bộ thủ công quanh quá trình duyệt theo đúng đối tượng khóa được chỉ định; đồng bộ từng lời gọi riêng lẻ không tự bao trùm cả vòng lặp. [Collections.synchronizedList trong Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedList(java.util.List))

Cũng không dùng vòng lặp “gặp `ConcurrentModificationException` thì thử lại cho đến khi hết lỗi” như một cơ chế phối hợp. Công việc đã làm trước lúc lỗi có thể bị lặp lại, dữ liệu có thể tiếp tục đổi, và không có giới hạn bảo đảm sẽ thành công. Sửa cách sở hữu, thay đổi và duyệt dữ liệu trước.

## 11. Kiểm chứng một cài đặt thay vì chỉ đợi thấy ngoại lệ

### 11.1. Những nhóm tình huống cần thử

Khi viết collection có cơ chế này, kiểm tra từng nhóm riêng:

1. **Duyệt bình thường:** đúng thứ tự hoặc đúng tập phần tử theo hợp đồng; hết dữ liệu thì `next()` báo đúng ngoại lệ.
2. **Thay đổi ngoài iterator:** thử thêm, xóa và xóa rồi thêm để khôi phục kích thước; kiểm tra tại những điểm mà lớp hứa phát hiện.
3. **Thay đổi qua iterator:** xóa hợp lệ phải cho phép iterator đó tiếp tục, đồng thời điều chỉnh con trỏ đúng.
4. **Trạng thái thao tác iterator:** xóa trước `next()`, xóa hai lần cho cùng kết quả và tiếp tục sau khi hết dữ liệu.
5. **Nhiều iterator và khung nhìn:** thay đổi qua một đường có làm các đối tượng quan sát khác nhận ra trạng thái cũ?
6. **Nhánh không thay đổi hoặc thất bại:** phần tử không tồn tại, khoảng rỗng, chỉ số sai; kết quả phải phù hợp chính sách của lớp.
7. **Phương thức hàng loạt và phiên bản tối ưu:** không bỏ qua bộ đếm, không tăng trùng, không che giấu phần thay đổi đã xảy ra nếu thao tác thất bại giữa chừng.

Đừng coi số lần nhìn thấy `ConcurrentModificationException` là thước đo chất lượng. Một iterator báo lỗi với cả những thao tác mà lớp công bố là hợp lệ cũng có thể được cài đặt sai.

Ba chương trình trong bài kiểm chứng một phần các nhóm trên bằng thao tác tuần tự. Chúng không kiểm tra tranh chấp luồng và không được dùng để kết luận collection an toàn khi dùng đồng thời.

### 11.2. Bài tập áp dụng và gợi ý tự kiểm tra

**Bài 1 — xóa qua hai đường.** Trong `TrackedListDemo`, tạo iterator rồi gọi `list.remove(Integer.valueOf(30))`. Sau đó tạo lại tình huống tương đương nhưng dùng chính `iterator.remove()` sau khi iterator trả về `30`. Hai đường có ý nghĩa khác nhau với iterator nào?

Gợi ý: phương thức `remove(Object)` có thể dùng một iterator nội bộ riêng. Điều đó không làm nó trở thành phép xóa của iterator mà mã ứng dụng đang giữ.

**Bài 2 — lỗi cập nhật bộ đếm.** Bỏ dòng `modCount++` trong `TrackedList.remove(int)`, giữ nguyên trong `add`. Chỉ kiểm tra thêm ngoài iterator có phát hiện được lỗi này không?

Gợi ý: không. Cần kiểm tra từng đường xóa, kể cả xóa qua khung nhìn và các phép kế thừa. Một đường được cập nhật đúng không đại diện cho đường khác.

**Bài 3 — sửa iterator tự cài đặt.** Trong `VersionedBag`, bỏ bước `cursor = lastReturned`, giữ nguyên việc cập nhật phiên bản kỳ vọng. Sau khi đọc rồi xóa `A` khỏi `[A, B, C]`, phần tử đọc kế tiếp là gì?

Gợi ý: con trỏ vẫn ở chỉ số `1`, dữ liệu đã thành `[B, C]`, nên lần đọc kế tiếp có thể bỏ qua `B` và trả về `C`. Bộ đếm đúng không thay thế được logic con trỏ đúng.

**Bài 4 — đếm không đồng nghĩa an toàn.** Nếu thay `structureVersion` bằng `AtomicInteger` và dùng phép tăng nguyên tử, những bước nào của `next()` và `remove()` vẫn chưa được bảo vệ như một thao tác thống nhất?

Gợi ý: kiểm tra phiên bản, kiểm tra giới hạn, truy cập kho dữ liệu, sửa dữ liệu và cập nhật vị trí vẫn là nhiều bước liên quan. Phải thiết kế giao thức truy cập toàn bộ cấu trúc.

**Bài 5 — tối ưu xóa khoảng.** Nếu ghi đè đường xóa khoảng để dịch chuyển dữ liệu một lần thay vì xóa từng phần tử, những kiểm tra nào cần giữ?

Gợi ý: kiểm tra giới hạn trước khi sửa, cập nhật kho lưu trữ và kích thước đúng, phản ánh thay đổi vào cơ chế phát hiện, không tăng trùng với đường xóa cơ bản, và kiểm tra cả khung nhìn lẫn iterator được tạo trước thao tác. Không dùng stdout chênh lệch bằng `2` của phiên bản cũ làm yêu cầu cho mọi thuật toán mới.

### 11.3. Các điều cần nắm sau bài học

`modCount` hữu ích khi nó gắn với một chính sách duyệt được xác định và mọi đường thay đổi cấu trúc đều tuân theo chính sách đó. Iterator phải giữ kỳ vọng riêng, đồng thời quản lý đúng vị trí và quyền thực hiện thao tác.

Khi kế thừa `AbstractList`, cập nhật các phép cơ bản đúng cách giúp tận dụng iterator và các đường thực thi kế thừa. Khi viết iterator riêng, tự chịu trách nhiệm nối tất cả thành phần đó lại. Khi dùng cấu trúc có chính sách ảnh chụp hoặc dùng đồng thời, đọc hợp đồng tương ứng trước khi lựa chọn cơ chế.

Một lần không báo lỗi không chứng minh dữ liệu không thay đổi. Một lần báo lỗi cũng không sửa được cách sử dụng sai. Giá trị của cơ chế nằm ở việc giúp phát hiện một nhóm lỗi, còn tính đúng vẫn đến từ hợp đồng và thiết kế truy cập dữ liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-collections-007">Giữ nhất quán equals, hashCode, view và các phương thức mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ nhất quán equals, hashCode, view và các phương thức mặc định

## 1. Một cấu trúc dữ liệu có nhiều đường đi vào cùng một trạng thái

Bạn viết một danh sách chỉ nhận số không âm. `add(-1)` đã bị chặn. Nhưng người dùng vẫn có thể gọi `listIterator().set(-1)`, `subList(...).set(...)` hoặc `replaceAll(...)`. Nếu một đường cập nhật bỏ qua kiểm tra, điều kiện “mọi phần tử không âm” không còn đúng.

Với map, vấn đề còn dễ bị che khuất hơn: bạn kiểm tra trong `put`, nhưng một đối tượng `Map.Entry` được trả ra có thể cho phép gọi `setValue` trực tiếp. Một số phương thức mặc định dùng chính đường này.

Bên cạnh đó, cấu trúc còn phải làm việc với những cài đặt khác của cùng giao diện. Một `List` tự viết có cùng các phần tử theo cùng thứ tự phải có quan hệ bằng nhau và mã băm phù hợp với `ArrayList` hoặc `LinkedList`. Không thể chỉ kiểm tra hai đối tượng cùng lớp rồi kết luận hợp đồng đã đúng.

Bài này tập trung vào bốn điều liên hệ với nhau:

1. **`equals`:** hai trạng thái nào được xem là bằng nhau?
2. **`hashCode`:** trạng thái bằng nhau có cho mã băm tương thích không?
3. **Khung nhìn (view):** những đối tượng nào cùng đọc/sửa một dữ liệu nền?
4. **Phương thức mặc định và kế thừa:** những thuật toán có sẵn sẽ cập nhật dữ liệu qua thao tác nào?

Một **bất biến** là điều kiện bạn muốn giữ sau mọi thao tác được hỗ trợ, chẳng hạn không có phần tử trùng, giá trị không âm hoặc không cho sửa từ một giao diện chỉ đọc. Giữ nhất quán nghĩa là mọi đường quan sát và cập nhật đều tôn trọng cùng hợp đồng, kể cả đường bạn không tự viết mã trong lớp con.

Mô hình tư duy cần dùng:

```text
Lời gọi trực tiếp ───────────────┐
Bộ duyệt / ListIterator ────────┤
Khung nhìn ────────────────────┼─→ thao tác cập nhật thực tế
Phương thức mặc định/kế thừa ──┘       ↓
                              dữ liệu và bất biến chung
                                      ↓
                          equals / hashCode / cách quan sát
```

Mục tiêu là lần theo các đường này, không phải ghi đè mọi phương thức một cách máy móc. Ba chương trình sẽ lần lượt kiểm chứng hợp đồng bằng nhau, danh sách có khung nhìn và map có các cửa cập nhật được kiểm tra.

## 2. equals và hashCode phải khớp ngữ nghĩa của giao diện

### 2.1. Hợp đồng nền của Object

Theo [Object.equals và Object.hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html), phép bằng nhau cần có tính phản xạ, đối xứng, bắc cầu và nhất quán khi dữ liệu liên quan không đổi; một đối tượng khác `null` phải trả `false` khi so với `null`.

Điều kéo theo cho mã băm là:

```text
a.equals(b) == true  ⇒  a.hashCode() == b.hashCode()
```

Chiều ngược lại không bắt buộc đúng. Hai đối tượng khác nhau có thể có cùng mã băm. Nếu dùng `hashCode` bằng nhau để kết luận `equals` bằng nhau, bạn biến va chạm băm hợp lệ thành một lỗi so sánh.

Không nhất thiết phải viết lại hai phương thức trong mọi lớp. Nhưng khi tự thay quy tắc `equals`, bạn phải giữ quy tắc `hashCode` tương ứng. Nếu lớp nền đã cung cấp đúng hợp đồng collection mà bạn cần, giữ cài đặt ấy thường ít rủi ro hơn việc tự viết lại.

### 2.2. Cùng chứa phần tử chưa có nghĩa dùng cùng phép bằng nhau

| Loại API | Điều kiện bằng nhau theo nội dung | Mã băm cần tương thích |
| --- | --- | --- |
| `List` | Đối phương cũng là `List`, cùng độ dài, từng cặp ở cùng vị trí bằng nhau | Tích lũy theo thứ tự, bắt đầu từ 1, mỗi bước nhân 31 rồi cộng mã băm phần tử. |
| `Set` | Đối phương cũng là `Set`, cùng các phần tử, không xét thứ tự | Tổng mã băm của các phần tử. |
| `Map` | Đối phương cũng là `Map`, cùng các ánh xạ khóa–giá trị | Tổng mã băm của các mục trong `entrySet`. |
| `Map.Entry` | Đối phương cũng là `Entry`, khóa bằng nhau và giá trị bằng nhau | Mã băm khóa XOR mã băm giá trị. |
| `Collection` không phải `List`/`Set` | Không có một phép so sánh nội dung chung được tự động áp đặt | Giữ hợp đồng `Object` hoặc một hợp đồng riêng được thiết kế đúng. |

Trong các công thức phần tử, `null` đóng góp 0. Các phép tính mã băm dùng `int` có thể tràn theo phép tính số nguyên của Java; không thay công thức chuẩn chỉ vì tổng vượt một giá trị dương lớn.

Nguồn định nghĩa cụ thể là [List](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html), [Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html), [Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html) và [Map.Entry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html).

Một danh sách không bằng một tập hợp chỉ vì cả hai in ra `[A, B]`. Thứ tự và số lần xuất hiện thuộc ngữ nghĩa của danh sách, trong khi tập hợp không có phần tử trùng và không dùng thứ tự để xác định bằng nhau.

Nếu lớp tự viết thực hiện `List`, đừng giới hạn `equals` bằng kiểm tra “phải cùng lớp cụ thể với tôi”. Điều này có thể khiến lớp bạn từ chối bằng một danh sách chuẩn trong khi danh sách chuẩn lại nhận bằng lớp bạn. Tính đối xứng bị phá vỡ dù dữ liệu giống nhau.

Với một kiểu giá trị thông thường không thực hiện các giao diện collection này, việc chọn phạm vi lớp được phép bằng nhau là một quyết định thiết kế riêng. Ở đây, hợp đồng giao diện công khai đã đặt yêu cầu tương tác giữa các cài đặt.

### 2.3. Chương trình kiểm chứng giữa các cài đặt

`Words` là một danh sách chỉ đọc nhỏ, dùng mảng sao chép và kế thừa phép bằng nhau/mã băm của `AbstractList`. Chương trình còn kiểm tra tập hợp, map và mục ánh xạ thuộc những lớp khác nhau.

Lưu thành `EqualityContractsDemo.java`:

```java
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public class EqualityContractsDemo {
    public static void main(String[] args) {
        List<String> custom = new Words("A", "B");
        List<String> standard = Arrays.asList("A", "B");
        System.out.println("list equality both ways = "
                + (custom.equals(standard) && standard.equals(custom)));
        System.out.println("list hash agrees = "
                + (custom.hashCode() == standard.hashCode()));
        System.out.println("reordered list equals = " + custom.equals(List.of("B", "A")));

        Set<String> firstSet = new LinkedHashSet<>(List.of("A", "B"));
        Set<String> secondSet = new HashSet<>(List.of("B", "A"));
        System.out.println("set equality = " + firstSet.equals(secondSet));
        System.out.println("set hash agrees = "
                + (firstSet.hashCode() == secondSet.hashCode()));
        System.out.println("list equals set = " + custom.equals(firstSet));

        Map<String, Integer> firstMap = new HashMap<>(Map.of("A", 1, "B", 2));
        Map<String, Integer> secondMap = new TreeMap<>(Map.of("B", 2, "A", 1));
        System.out.println("map equality = " + firstMap.equals(secondMap));
        System.out.println("map hash agrees = "
                + (firstMap.hashCode() == secondMap.hashCode()));

        Map.Entry<String, Integer> entry = Map.entry("A", 3);
        Map.Entry<String, Integer> copied =
                new AbstractMap.SimpleImmutableEntry<>("A", 3);
        System.out.println("entry equality both ways = "
                + (entry.equals(copied) && copied.equals(entry)));
        int requiredHash = Objects.hashCode(entry.getKey())
                ^ Objects.hashCode(entry.getValue());
        System.out.println("entry XOR hash agrees = " + (entry.hashCode() == requiredHash));
        System.out.println("Objects.hash pair agrees = "
                + (entry.hashCode() == Objects.hash("A", 3)));

        CollisionKey one = new CollisionKey(1);
        CollisionKey two = new CollisionKey(2);
        System.out.println("collision has same hash = " + (one.hashCode() == two.hashCode()));
        System.out.println("collision keys equal = " + one.equals(two));
        System.out.println("collision set size = " + Set.of(one, two).size());
    }

    private static final class Words extends AbstractList<String> {
        private final String[] data;

        private Words(String... words) {
            data = words.clone();
        }

        @Override
        public String get(int index) {
            Objects.checkIndex(index, data.length);
            return data[index];
        }

        @Override
        public int size() {
            return data.length;
        }
    }

    private record CollisionKey(int value) {
        @Override
        public int hashCode() {
            return 0;
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all EqualityContractsDemo.java
java EqualityContractsDemo
```

Kết quả:

```text
list equality both ways = true
list hash agrees = true
reordered list equals = false
set equality = true
set hash agrees = true
list equals set = false
map equality = true
map hash agrees = true
entry equality both ways = true
entry XOR hash agrees = true
Objects.hash pair agrees = false
collision has same hash = true
collision keys equal = false
collision set size = 2
```

### 2.4. Điều mỗi nhóm kết quả chứng minh

Với danh sách, cả hai chiều so sánh đều đúng và mã băm bằng nhau. Khi đảo thứ tự, kết quả bằng nhau đổi thành `false`. `Words` không tự viết một phiên bản `equals` khác làm mất khả năng tương tác.

Với tập hợp và map, thứ tự xây dựng khác nhau không làm thay đổi quan hệ bằng nhau. Không dựa vào thứ tự duyệt của `HashMap` hoặc `HashSet` để in kết quả; chương trình chỉ in các giá trị logic đã xác định.

Với `Map.Entry`, công thức XOR được kiểm chứng trực tiếp. `Objects.hash("A",3)` không phải công thức thay thế cho `Entry.hashCode()`: nó tính mã băm kết hợp theo một cách khác. Ví dụ cụ thể cho kết quả khác nhau; không được suy ra hai công thức tương đương chỉ vì cả hai đều nhận khóa và giá trị.

`CollisionKey` cố ý trả mã băm 0 cho mọi giá trị. Hai khóa có giá trị khác nhau vẫn không bằng nhau và cùng được giữ trong tập hợp. Đây là ví dụ về mã băm có nhiều va chạm nhưng chưa vi phạm điều kiện “bằng nhau thì cùng mã băm”. Nó có thể gây hiệu năng xấu ở cấu trúc băm, nhưng không phải lý do hợp lệ để coi hai đối tượng là một.

### 2.5. Trạng thái thay đổi kéo theo phép so sánh thay đổi

Một collection có thể có mã băm thay đổi khi phần tử, thứ tự hoặc ánh xạ của nó thay đổi. Vì thế, đừng lưu một mã băm tính ở lần đầu rồi dùng mãi nếu dữ liệu liên quan còn có thể sửa. Bộ đếm thay đổi cấu trúc cũng không đủ: thay giá trị bằng `set` có thể không đổi kích thước nhưng làm mã băm danh sách thay đổi.

Tương tự, khóa trong map hoặc phần tử trong set cần được dùng cẩn thận nếu các trường tham gia `equals` còn có thể sửa. Hợp đồng [Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html) và [Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html) không xác định hành vi khi sự thay đổi ấy ảnh hưởng phép bằng nhau trong lúc đối tượng đang được lưu. Không nên dạy rằng “sau khi sửa chắc chắn contains sẽ trả false”; đó là một cách dự đoán quá mức cho trường hợp đã ra ngoài hợp đồng.

Nếu cần thay giá trị nhận diện, một hướng rõ ràng là lấy/xóa đối tượng theo trạng thái cũ trước, tạo giá trị mới phù hợp rồi đưa vào lại theo chính sách của bài toán. Dùng khóa giá trị bất biến giúp giảm vấn đề này.

Với cấu trúc sắp xếp, còn phải xét quan hệ `compare(a,b) == 0`. Nếu quan hệ này không nhất quán với `equals`, cấu trúc có thể coi hai phần tử là một theo phép so sánh nhưng đối tượng lại nói chúng khác nhau. [Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html) giải thích ảnh hưởng tới hợp đồng `Set`/`Map`. Đừng tự thêm việc bỏ qua hoa thường hoặc chuẩn hóa khóa ở một vài phương thức rồi giả định mọi phép tìm, xóa, so sánh và mã băm đã tự nhất quán.


## 3. Khung nhìn chia sẻ dữ liệu, không chỉ chia sẻ kiểu

### 3.1. Ba quan hệ cần phân biệt

| Đối tượng được trả về | Có theo dõi thay đổi cấu trúc/nội dung ở nền không? | Có cho sửa từ đối tượng này không? |
| --- | --- | --- |
| Khung nhìn có thể sửa như `subList` | Có liên hệ với danh sách nền, trong phạm vi hợp đồng sử dụng | Có, nếu nền và thao tác tương ứng hỗ trợ. |
| Khung nhìn không cho sửa như `Collections.unmodifiableList` | Các truy vấn đọc qua nền nên thấy thay đổi hợp lệ ở nền | Không cho sửa qua khung nhìn. |
| Bản sao cấu trúc như `List.copyOf` | Không phản ánh các thay đổi sau đó của cấu trúc nguồn | Không cho sửa bản danh sách trả về. |

“Không cho sửa” mô tả khả năng của một đường truy cập. Nó không có nghĩa dữ liệu nền không thể thay đổi qua một tham chiếu khác. “Bản sao” cũng không mặc nhiên là sao chép sâu mọi đối tượng phần tử.

[Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)) và [List.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)) là hai API cần đọc tách biệt. `List.copyOf` không nhận phần tử `null`; nó không phải lựa chọn thay thế vô điều kiện cho một bản sao có chính sách cho phép `null`.

Nếu bạn tự cài một phương thức được công bố là trả khung nhìn nhưng lại trả một bản sao không liên hệ với nguồn, phía gọi sẽ có kỳ vọng sai về việc cập nhật. Ngược lại, trả thẳng dữ liệu nền khi đã công bố bản sao có thể làm thay đổi ngoài ý muốn đi ngược vào cấu trúc gốc.

### 3.2. Một chính sách phải tồn tại trên mọi đường sửa của List

Với danh sách không âm, hãy lần theo các đường:

```text
addAll(...)               → thêm từng phần tử → add(index, value)
ListIterator.add(value)   → add(index, value)
ListIterator.set(value)   → set(index, value)
subList(...).set(...)     → set ở chỉ số tương ứng trên nền
List.replaceAll(...)      → ListIterator.set(...) trong cài đặt mặc định
List.sort(...)            → ghi kết quả sắp xếp qua bộ duyệt trong cài đặt mặc định
removeIf(...)             → Iterator.remove() trong cài đặt mặc định
```

Đây là bản đồ cho kiểu danh sách dùng lớp khung và không ghi đè các thuật toán ấy. Lớp cụ thể khác có thể ghi đè, nên khi tối ưu hoặc viết lớp bọc, phải xem lại cài đặt thực tế.

Cần phân biệt thuật ngữ Java:

- `Collection.removeIf`, `List.replaceAll`, `List.sort` là các phương thức `default` được khai báo ở giao diện.
- Một phương thức như `addAll` kế thừa từ lớp trừu tượng là phương thức có thân của lớp nền; nó không trở thành phương thức `default` của giao diện chỉ vì bạn không tự viết nó.

Cả hai loại đều có thể gọi trở lại các thao tác của lớp bạn. Vì thế, việc kiểm tra bất biến phải bao phủ cả phương thức mặc định lẫn phương thức kế thừa.

### 3.3. Chương trình: danh sách không âm, khung nhìn và thuật toán có sẵn

`NonNegativeList` chấp nhận các `Integer` khác `null` và lớn hơn hoặc bằng 0. Nó giữ nơi lưu riêng, kiểm tra ở `add(int,Integer)` và `set(int,Integer)`, hỗ trợ xóa, và ghi nhận thay đổi cấu trúc cho cơ chế kế thừa.

Lưu thành `ViewsAndDefaultsDemo.java`:

```java
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.RandomAccess;

public class ViewsAndDefaultsDemo {
    public static void main(String[] args) {
        NonNegativeList list = new NonNegativeList();
        list.addAll(List.of(1, 2, 3, 4));
        List<Integer> readOnlyView = Collections.unmodifiableList(list);
        List<Integer> snapshot = List.copyOf(list);
        List<Integer> middle = list.subList(1, 3);

        middle.set(0, 20);
        list.replaceAll(value -> value * 2);
        System.out.println("after view set and replaceAll = " + list);
        middle.clear();
        System.out.println("after subList clear = " + list);
        System.out.println("read-only view = " + readOnlyView);
        System.out.println("snapshot = " + snapshot);

        list.removeIf(value -> value > 5);
        list.addAll(List.of(5, 3));
        list.sort(Comparator.reverseOrder());
        System.out.println("after removeIf and sort = " + list);
        expect("negative replaceAll", IllegalArgumentException.class,
                () -> list.replaceAll(value -> -value));
        ListIterator<Integer> cursor = list.listIterator();
        cursor.next();
        expect("negative iterator set", IllegalArgumentException.class,
                () -> cursor.set(-1));
        expect("read-only add", UnsupportedOperationException.class,
                () -> readOnlyView.add(6));
        System.out.println("after rejected changes = " + list);
        System.out.println("updated read-only view = " + readOnlyView);
    }

    private static final class NonNegativeList
            extends AbstractList<Integer> implements RandomAccess {
        private final List<Integer> data = new ArrayList<>();

        @Override
        public Integer get(int index) {
            return data.get(index);
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public Integer set(int index, Integer value) {
            Objects.checkIndex(index, data.size());
            check(value);
            return data.set(index, value);
        }

        @Override
        public void add(int index, Integer value) {
            if (index < 0 || index > data.size()) {
                throw new IndexOutOfBoundsException("index: " + index);
            }
            check(value);
            data.add(index, value);
            modCount++;
        }

        @Override
        public Integer remove(int index) {
            Integer previous = data.remove(index);
            modCount++;
            return previous;
        }

        private static void check(Integer value) {
            Objects.requireNonNull(value, "value");
            if (value < 0) {
                throw new IllegalArgumentException("nonnegative value required");
            }
        }
    }

    private static void expect(String label, Class<? extends RuntimeException> type,
                               Runnable action) {
        try {
            action.run();
            throw new AssertionError("operation unexpectedly succeeded");
        } catch (RuntimeException ex) {
            if (!type.isInstance(ex)) {
                throw ex;
            }
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all ViewsAndDefaultsDemo.java
java ViewsAndDefaultsDemo
```

Kết quả:

```text
after view set and replaceAll = [2, 40, 6, 8]
after subList clear = [2, 8]
read-only view = [2, 8]
snapshot = [1, 2, 3, 4]
after removeIf and sort = [5, 3, 2]
negative replaceAll -> IllegalArgumentException
negative iterator set -> IllegalArgumentException
read-only add -> UnsupportedOperationException
after rejected changes = [5, 3, 2]
updated read-only view = [5, 3, 2]
```

### 3.4. Những cập nhật nào đã đi qua kiểm tra?

`middle` ban đầu trỏ tới vị trí 1 và 2 của danh sách. `middle.set(0,20)` cập nhật danh sách nền thành `[1,20,3,4]`. `replaceAll(value -> value * 2)` gọi các thao tác thay thế phù hợp và cho `[2,40,6,8]`.

`middle.clear()` sau đó xóa đoạn tương ứng, để lại `[2,8]`. Khung nhìn chỉ đọc nhìn thấy chính trạng thái này, còn `snapshot` vẫn giữ `[1,2,3,4]`. Không có yêu cầu sao chép nào được suy ra từ tên biến; kết quả đến từ API đã dùng để tạo từng đối tượng.

Tiếp theo, `removeIf` xóa 8, rồi danh sách nhận thêm 5 và 3. `sort` sắp xếp lại thành `[5,3,2]`. Trong ví dụ, các giá trị vẫn hợp lệ nên việc ghi lại bằng `set` không vi phạm bất biến.

Hai nỗ lực ghi giá trị âm đều thất bại:

- `replaceAll` tạo kết quả âm ngay tại phần tử đầu, rồi bị `set` từ chối.
- `ListIterator.set(-1)` cũng đi qua `set` có kiểm tra.

Điều quan trọng là cả hai không cần biết một phương thức kiểm tra riêng của ứng dụng. Chúng dùng cùng thao tác nền, nên nhận cùng chính sách. Khung nhìn chỉ đọc vẫn từ chối `add` của chính nó nhưng tiếp tục thấy những sửa đổi hợp lệ qua danh sách nền.

Trong lớp này, `set` không đổi kích thước và không tăng `modCount`; thêm/xóa thành công tăng một lần. Đây là quyết định của cài đặt minh họa dùng bộ duyệt kế thừa. Không suy ra mọi lớp `List` khác, đặc biệt lớp đã ghi đè `sort` hoặc bộ duyệt, phải ghi nhận thay đổi theo hệt cách này.

### 3.5. subList là một vùng vị trí, không phải nhóm phần tử được gắn danh tính

Khung nhìn của danh sách ánh xạ chỉ số và phạm vi về nền. Nó không giữ lời hứa “luôn đi theo đúng hai đối tượng ban đầu” khi bạn thay nội dung hoặc sắp xếp lại các vị trí.

Sau một thay đổi cấu trúc trên nền ngoài đường khung nhìn, đừng tiếp tục dùng khung nhìn cũ như thể nó được tự đồng bộ lại. [List.subList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#subList(int,int)) quy định ngữ nghĩa không xác định trong tình huống sửa cấu trúc nền ngoài khung nhìn. Một triển khai có thể ném `ConcurrentModificationException` để phát hiện, nhưng ngoại lệ không phải giao thức làm mới vùng dữ liệu.

Chương trình ngừng dùng `middle` sau khi xóa đoạn qua chính nó. Những sửa đổi cấu trúc sau đó diễn ra trên danh sách gốc và chỉ được quan sát qua khung nhìn chỉ đọc toàn danh sách, không qua `subList` cũ.

### 3.6. Không cho sửa container chưa đủ để chặn sửa đối tượng bên trong

Trong ví dụ, phần tử là `Integer` nên không có thao tác thay đổi nội dung của chính đối tượng số. Nếu phần tử là một đối tượng có thể sửa, khung nhìn không cho thêm/xóa vẫn có thể trả đối tượng ấy cho phía gọi.

Điều tương tự xảy ra với một collection chứa `Map.Entry` có `setValue`. Chỉ bọc tập hợp chứa entry bằng `Collections.unmodifiableSet` không tự biến các entry bên trong thành bất biến. Một entry còn có khả năng ghi xuyên về map nếu vẫn giữ liên hệ với map nền.

Nếu cần một map chỉ đọc, dùng [Collections.unmodifiableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableMap(java.util.Map)) phù hợp giúp áp dụng chính sách qua cả các khung nhìn của map. Nếu tự bọc, bạn phải bảo vệ cả container lẫn khả năng ghi xuyên của những đối tượng được trả ra.

Ngoài ra, đừng cho rằng mọi lớp bọc `Collection` đều ủy quyền phép bằng nhau theo nội dung. `Collections.unmodifiableCollection` dùng `Object.equals` và `Object.hashCode`, còn các API chuyên biệt cho `List`/`Set` giữ nghĩa của giao diện tương ứng. Chọn API đúng loại ngữ nghĩa và đọc hợp đồng, thay vì dựa vào một giả định chung cho mọi lớp bọc.


## 4. Map: put không phải cửa cập nhật duy nhất

### 4.1. Ba khung nhìn cần cùng mô tả một map

Map có ba khung nhìn chính:

| Khung nhìn | Phần dữ liệu được nhìn thấy | Xóa một mục có nghĩa gì? |
| --- | --- | --- |
| `keySet()` | Các khóa | Xóa ánh xạ mang khóa tương ứng. |
| `values()` | Các giá trị, có thể trùng | Xóa ánh xạ tương ứng với lần xuất hiện bị xóa. |
| `entrySet()` | Các cặp khóa–giá trị | Xóa ánh xạ khớp cả khóa và giá trị của mục. |

Đây là các khung nhìn có nền, không phải những bản sao tùy ý. Khi thao tác xóa được hỗ trợ, việc xóa qua chúng phải phản ánh vào map. Các khung nhìn chuẩn này không hỗ trợ thêm bằng `add`/`addAll`; chỉ có một khóa hoặc một giá trị không xác định đầy đủ ánh xạ cần thêm.

`values` không phải một `Set`: hai khóa có thể cùng ánh xạ đến một giá trị. `values().remove(v)` không có nghĩa xóa mọi ánh xạ cùng giá trị; nếu muốn lọc mọi mục thỏa điều kiện, cần một thao tác có ý nghĩa tương ứng, chẳng hạn `removeIf`.

Với `entrySet().contains` hoặc `entrySet().remove`, không được chỉ so khóa rồi bỏ qua giá trị. Một mục `A=10` khác `A=20` dù cùng khóa.

Nguồn hợp đồng là [các khung nhìn của Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html). Cách lớp khung xây chúng có thể được xem thêm ở [AbstractMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.html).

### 4.2. Lần theo các thuật toán trước khi đặt kiểm tra

Với một lớp con của `AbstractMap` dùng các phương thức giao diện mặc định, những đường quan trọng gồm:

```text
putAll(...)                          → put(...) từng ánh xạ
putIfAbsent / replace                → đọc trạng thái, rồi put(...) khi cần
compute / computeIfAbsent / merge    → tính kết quả, rồi put/remove khi cần
Map.replaceAll(...)                  → entrySet() → Entry.setValue(...)
keySet / values / entrySet removal   → đường xóa được khung nhìn hỗ trợ
```

`putAll` trong ví dụ là phương thức kế thừa của `AbstractMap`; `compute`, `merge`, `replaceAll` là các phương thức `default` của `Map`. Không gộp hai khái niệm khi đọc mã.

Điểm dễ bỏ sót là `replaceAll`: cài đặt mặc định không bắt buộc gọi `put` của bạn cho từng kết quả. Nó cập nhật bằng `Entry.setValue`. Nếu `entrySet` trả entry của kho nền nguyên trạng, kiểm tra trong `put` có thể bị vượt qua.

Bài toán “map chỉ giữ số không âm” cần bảo vệ ít nhất hai đường ghi đang công bố: `put` và `Entry.setValue`. Các đường xóa vẫn có thể được hỗ trợ vì xóa một ánh xạ không tạo số âm.

### 4.3. Chương trình: map kiểm tra cả put lẫn entry

`ScoreMap` dưới đây dùng `LinkedHashMap` để kết quả duyệt trong minh họa ổn định. Nó không cho thêm khóa `null`, giá trị `null` hoặc giá trị âm. Nó vẫn cho phép xóa qua các khung nhìn.

Điểm cốt lõi nằm ở `checked`: entry trả ra được bọc để kiểm tra `setValue` và vẫn thực hiện đúng `equals`/`hashCode` của `Map.Entry`. Dữ liệu nền không được công khai.

Lưu thành `CheckedMapDemo.java`:

```java
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

public class CheckedMapDemo {
    public static void main(String[] args) {
        ScoreMap<String> scores = new ScoreMap<>();
        scores.put("A", 10);
        scores.put("B", 20);
        System.out.println("initial = " + scores);
        Map<String, Integer> reference = Map.of("A", 10, "B", 20);
        System.out.println("map equality both ways = "
                + (scores.equals(reference) && reference.equals(scores)));
        System.out.println("map hash agrees = " + (scores.hashCode() == reference.hashCode()));
        expect("negative put", IllegalArgumentException.class, () -> scores.put("X", -1));
        expect("negative compute", IllegalArgumentException.class,
                () -> scores.compute("A", (key, value) -> -1));
        expect("negative replace", IllegalArgumentException.class,
                () -> scores.replace("A", -2));
        scores.compute("A", (key, value) -> value + 5);
        scores.merge("B", 3, Integer::sum);
        scores.putIfAbsent("C", 7);
        System.out.println("after compute merge putIfAbsent = " + scores);

        Iterator<Entry<String, Integer>> cursor = scores.entrySet().iterator();
        Entry<String, Integer> first = cursor.next();
        Entry<String, Integer> standard = Map.entry("A", 15);
        System.out.println("entry equality agrees = "
                + (first.equals(standard) && standard.equals(first)));
        System.out.println("entry hash agrees = " + (first.hashCode() == standard.hashCode()));
        expect("negative entry setValue", IllegalArgumentException.class,
                () -> first.setValue(-3));
        System.out.println("entry previous value = " + first.setValue(16));
        scores.replaceAll((key, value) -> value + 1);
        System.out.println("after replaceAll = " + scores);

        scores.values().removeIf(value -> value >= 20);
        scores.keySet().remove("C");
        System.out.println("after view removals = " + scores);
        expect("keySet add", UnsupportedOperationException.class,
                () -> scores.keySet().add("X"));
        scores.computeIfPresent("A", (key, value) -> null);
        System.out.println("after null compute result = " + scores);
        expect("null put", NullPointerException.class, () -> scores.put("N", null));

        Map<String, Integer> updates = new LinkedHashMap<>();
        updates.put("D", 4);
        updates.put("E", -1);
        expect("partly applied putAll", IllegalArgumentException.class,
                () -> scores.putAll(updates));
        System.out.println("after rejected putAll = " + scores);

        ScoreMap<String> partial = new ScoreMap<>();
        partial.put("A", 1);
        partial.put("B", 2);
        expect("partly applied replaceAll", IllegalArgumentException.class,
                () -> partial.replaceAll((key, value) -> key.equals("B") ? -1 : value + 10));
        System.out.println("after rejected replaceAll = " + partial);
    }

    private static final class ScoreMap<K> extends AbstractMap<K, Integer> {
        private final Map<K, Integer> data = new LinkedHashMap<>();
        private final Set<Entry<K, Integer>> entries = new AbstractSet<>() {
            @Override
            public int size() {
                return data.size();
            }

            @Override
            public Iterator<Entry<K, Integer>> iterator() {
                Iterator<Entry<K, Integer>> delegate = data.entrySet().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return delegate.hasNext();
                    }

                    @Override
                    public Entry<K, Integer> next() {
                        return checked(delegate.next());
                    }

                    @Override
                    public void remove() {
                        delegate.remove();
                    }
                };
            }
        };

        @Override
        public Integer put(K key, Integer value) {
            Objects.requireNonNull(key, "key");
            check(value);
            return data.put(key, value);
        }

        @Override
        public Set<Entry<K, Integer>> entrySet() {
            return entries;
        }

        private Entry<K, Integer> checked(Entry<K, Integer> delegate) {
            return new Entry<>() {
                @Override
                public K getKey() {
                    return delegate.getKey();
                }

                @Override
                public Integer getValue() {
                    return delegate.getValue();
                }

                @Override
                public Integer setValue(Integer value) {
                    check(value);
                    return delegate.setValue(value);
                }

                @Override
                public boolean equals(Object other) {
                    return other instanceof Entry<?, ?> entry
                            && Objects.equals(getKey(), entry.getKey())
                            && Objects.equals(getValue(), entry.getValue());
                }

                @Override
                public int hashCode() {
                    return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
                }

                @Override
                public String toString() {
                    return getKey() + "=" + getValue();
                }
            };
        }

        private static void check(Integer value) {
            Objects.requireNonNull(value, "value");
            if (value < 0) {
                throw new IllegalArgumentException("nonnegative value required");
            }
        }
    }

    private static void expect(String label, Class<? extends RuntimeException> type,
                               Runnable action) {
        try {
            action.run();
            throw new AssertionError("operation unexpectedly succeeded");
        } catch (RuntimeException ex) {
            if (!type.isInstance(ex)) {
                throw ex;
            }
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 -Xlint:all CheckedMapDemo.java
java CheckedMapDemo
```

Kết quả:

```text
initial = {A=10, B=20}
map equality both ways = true
map hash agrees = true
negative put -> IllegalArgumentException
negative compute -> IllegalArgumentException
negative replace -> IllegalArgumentException
after compute merge putIfAbsent = {A=15, B=23, C=7}
entry equality agrees = true
entry hash agrees = true
negative entry setValue -> IllegalArgumentException
entry previous value = 15
after replaceAll = {A=17, B=24, C=8}
after view removals = {A=17}
keySet add -> UnsupportedOperationException
after null compute result = {}
null put -> NullPointerException
partly applied putAll -> IllegalArgumentException
after rejected putAll = {D=4}
partly applied replaceAll -> IllegalArgumentException
after rejected replaceAll = {A=11, B=2}
```

### 4.4. Hai điểm kiểm tra bao phủ những đường nào?

Ở đầu chương trình, map tùy chỉnh bằng map tham chiếu theo cả hai chiều và có cùng mã băm. Điều này kiểm chứng khả năng tương tác, không chỉ việc tự in một chuỗi trông giống map.

Ba cách ghi số âm qua `put`, `compute` và `replace` đều bị từ chối. Hai phương thức sau dùng thao tác `put` có kiểm tra khi cần lưu kết quả. Sau đó các phép tính hợp lệ cập nhật A thành 15, B thành 23 và thêm C với 7.

Một entry của A được lấy trong quá trình duyệt. Nó bằng `Map.entry("A",15)` ở cả hai chiều và có mã băm tương thích. Thử `setValue(-3)` thất bại; `setValue(16)` thành công và trả giá trị cũ 15.

`replaceAll` tiếp theo đi qua các entry có kiểm tra, làm map thành `{A=17, B=24, C=8}`. Nếu chỉ trả entry của kho nền mà không bọc, thao tác này là một đường có thể đưa giá trị không hợp lệ vào mà không qua `put`.

Phần xóa qua khung nhìn chứng minh chiều liên hệ ngược về map: `values().removeIf(...)` xóa ánh xạ B, `keySet().remove("C")` xóa C. `keySet().add("X")` bị từ chối; một khóa đơn không tự tạo được giá trị ánh xạ.

`computeIfPresent` trả kết quả `null` cho A, làm A bị xóa. Điều đó khác với `put("N",null)`, vốn cố lưu một giá trị không được phép và bị từ chối.

### 4.5. Vì sao wrapper Entry phải giữ cả hợp đồng đọc và bằng nhau?

Nếu chỉ chặn `setValue` nhưng entry bọc vẫn dùng `Object.equals` và `Object.hashCode`, `entrySet` có thể không còn biểu diễn đúng một tập hợp ánh xạ. So sánh hoặc mã băm map kế thừa cũng có thể sai theo.

`checked` dùng khóa và giá trị hiện có của entry nền để thực hiện ba việc:

- Trả dữ liệu của cùng ánh xạ đang được duyệt.
- So sánh khóa và giá trị theo hợp đồng entry, kể cả khi đối phương thuộc lớp khác.
- Tính mã băm XOR đúng; không dựa vào danh tính của đối tượng bọc.

Đối tượng bọc không được coi là một vé cập nhật tồn tại vĩnh viễn. [Map.Entry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html) giới hạn liên hệ của entry lấy từ khung nhìn trong quá trình duyệt và quy định hành vi không xác định khi map thay đổi theo những cách ngoài các trường hợp được hỗ trợ. Đừng giữ entry rồi dùng lại sau khi xóa ánh xạ để kỳ vọng một cơ chế gắn lại tự động.

Nếu cần giữ lại một cặp dữ liệu độc lập, tạo bản sao entry phù hợp. `Map.Entry.copyOf` tạo entry độc lập không cho sửa, với chính sách không nhận khóa/giá trị `null` như `Map.entry`. `AbstractMap.SimpleImmutableEntry` là một lựa chọn khác cần đọc theo chính sách phần tử của nó. Các cách ấy chỉ tách cặp ánh xạ; chúng không sao chép sâu đối tượng khóa hay giá trị.

### 4.6. Cẩn thận khi tối ưu đường trả entry

Trong ví dụ, `entrySet` dựa trên `AbstractSet` và chỉ cung cấp cách duyệt đã bọc. Những đường kế thừa dùng bộ duyệt sẽ nhận entry được kiểm tra.

Nếu sau này tự ghi đè `toArray`, `forEach` hoặc `spliterator` để chạy nhanh hơn bằng cách chuyển thẳng sang kho nền, bạn có thể vô tình trả entry chưa bọc ra ngoài. Khi đó chính sách bị hở trở lại dù `iterator().next()` vẫn trông đúng.

Vì vậy, khi một đối tượng trả ra còn có khả năng sửa dữ liệu nền, kiểm tra mọi đường trả đối tượng ấy, không chỉ bộ duyệt thông thường. Việc giữ một trường `data` là `private` không đủ nếu các đối tượng có quyền ghi xuyên vẫn thoát ra ngoài.

Lớp ví dụ ưu tiên đường gọi rõ ràng hơn tối ưu tra cứu. Một số thao tác kế thừa của `AbstractMap` tìm bằng cách duyệt `entrySet`. Nó là minh họa về hợp đồng và kiểm tra đầu vào, chưa phải một map tối ưu thay thế mọi nhu cầu thực tế.

## 5. Phương thức mặc định không bổ sung mọi bảo đảm bạn muốn

### 5.1. null có nhiều ý nghĩa khác nhau trong Map

Một map cho phép giá trị `null` cần phân biệt “không có khóa” và “có khóa nhưng giá trị là null”. Chỉ nhìn `get(k) == null` không phân biệt được hai trạng thái này; `containsKey` có thể cần thiết.

Các thao tác có quy tắc riêng:

| Thao tác | Vai trò của null cần nhớ |
| --- | --- |
| `getOrDefault(k,d)` | Nếu khóa tồn tại với giá trị `null`, kết quả vẫn là `null`, không phải `d`. |
| `putIfAbsent` | Giá trị hiện tại `null` được xử lý như trường hợp cần gán giá trị mới. |
| `computeIfAbsent` | Có thể tính khi giá trị hiện tại là `null`; kết quả `null` không tạo một ánh xạ mới. |
| `computeIfPresent` | Chỉ tính khi đang có giá trị khác `null`; kết quả `null` xóa ánh xạ đó. |
| `compute` | Có thể nhận giá trị cũ `null`; kết quả `null` làm ánh xạ không còn tồn tại. |
| `merge` | Giá trị truyền vào phải khác `null`; khi không có giá trị khác `null`, dùng giá trị truyền vào mà không cần gọi hàm gộp. Kết quả `null` của hàm gộp xóa ánh xạ. |
| `replaceAll` | Kết quả `null` là một giá trị muốn gán, không phải tín hiệu xóa. Map có thể từ chối nếu không nhận `null`. |

Riêng `computeIfAbsent`, nếu khóa đã có ánh xạ `null` và hàm tính trả `null`, đừng suy ra ánh xạ ấy được xóa. Hành vi “không ghi giá trị mới” khác hành vi xóa của `compute` hay `computeIfPresent`.

`ScoreMap` không lưu giá trị `null`, nên `get` trả `null` biểu diễn trường hợp không có ánh xạ. Nhưng khi tự thiết kế một lớp tổng quát nhận `null`, bạn phải giữ sự phân biệt ở các thao tác đọc, thay có điều kiện và tính toán.

Nguồn đối chiếu là từng phương thức trong [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html). Đừng gán một quy tắc chung “null luôn là xóa” cho mọi API.

### 5.2. Giữ bất biến khác với hoàn tác toàn bộ thao tác

Cuối chương trình có hai kết quả dễ hiểu nhầm:

- `putAll` thêm D=4 trước, rồi từ chối E=-1. D vẫn còn.
- `replaceAll` thay A=1 thành A=11 trước, rồi từ chối kết quả âm cho B. A vẫn là 11, B vẫn là 2.

Cả hai trạng thái cuối đều giữ bất biến không có giá trị âm. Nhưng thao tác hàng loạt chưa hoàn tất toàn bộ. Ngoại lệ không tự làm những cập nhật hợp lệ trước đó quay lại trạng thái cũ.

Trong ví dụ, thứ tự này ổn định vì dùng `LinkedHashMap` cho nguồn/cấu trúc nền liên quan. Với cấu trúc không cam kết thứ tự duyệt, không được hứa phần tử nào đã thay đổi trước khi lỗi xảy ra.

Nếu yêu cầu nghiệp vụ là “tất cả cùng thành công hoặc không thay đổi gì”, cần một thiết kế khác có bước chuẩn bị/kiểm tra và cơ chế công bố phù hợp. Chỉ thêm vòng kiểm tra đầu vào trước chưa tự giải quyết việc nhiều luồng thay đổi hoặc hàm tính có tác dụng phụ.

Các hàm truyền vào `compute`/`merge` nên tính kết quả mà không sửa chính map đang được tính. Một phép kiểm tra ở lớp bọc không thể hoàn tác các tác dụng phụ tùy ý mà hàm gọi đã gây ra ở nơi khác.

### 5.3. Phương thức default của Map không mặc nhiên nguyên tử

Một chuỗi “đọc hiện tại → tính → ghi” có thể thực hiện đúng trong một luồng nhưng không cung cấp bảo đảm nguyên tử khi nhiều luồng truy cập. Hợp đồng các phương thức mặc định của `Map` không tự hứa đồng bộ hóa hoặc nguyên tử.

Không đổi tên lớp bọc thành một map đồng thời chỉ vì nó có `compute` hay `putIfAbsent`. Nếu cần những bảo đảm ấy, phải dùng hoặc cài đặt một hợp đồng đồng thời phù hợp, áp dụng nhất quán qua phương thức và khung nhìn, rồi công bố rõ ràng.

Các chương trình trong bài chỉ chạy một luồng. Chúng kiểm chứng đường cập nhật, bằng nhau và kết quả quan sát; chúng không phải bằng chứng an toàn cho truy cập đồng thời.

### 5.4. Không được hỗ trợ cũng là một chính sách phải nhất quán

Một cấu trúc chỉ đọc có thể từ chối thao tác sửa bằng `UnsupportedOperationException`. Nhưng đừng lấy một lần gọi không làm thay đổi dữ liệu, chẳng hạn xóa mục không tồn tại hoặc thêm một tập rỗng, để khẳng định cấu trúc hỗ trợ sửa.

Ngược lại, khi viết lớp bọc chỉ đọc, không chỉ chặn `put`/`add` rồi để mặc các phương thức mới, bộ duyệt hay khung nhìn ghi xuyên. Cần rà soát cả những phương thức giao diện mặc định và những đối tượng có thể trả ra.

Khi phiên bản Java mục tiêu thay đổi, kiểm tra bề mặt API mới và các phương thức mới được kế thừa. Bản chất yêu cầu vẫn là: mọi đường vào cùng một dữ liệu phải giữ chính sách mà lớp đã công bố.


## 6. Cách rà soát một cài đặt tùy chỉnh có hệ thống

### 6.1. Viết hợp đồng trước khi chọn điểm ghi đè

Trước khi kiểm tra mã, ghi rõ những điều lớp muốn công bố:

- Nó thực hiện `Collection`, `List`, `Set` hay `Map`? Phép bằng nhau theo hợp đồng nào?
- Cho phép phần tử/khóa/giá trị `null` không? Chấp nhận trùng, thứ tự và phép so sánh nào?
- Cho thêm, thay thế, xóa hay chỉ một phần trong số đó?
- Khung nhìn nào có thể sửa? Đối tượng trả ra có quyền ghi xuyên không?
- Lỗi ở thao tác hàng loạt có cho phép trạng thái áp dụng một phần không?
- Có bảo đảm đồng thời nào thật sự được cung cấp không?

Không cần thêm một chính sách nghiệp vụ vào mọi phương thức bằng cách sao chép cùng một đoạn mã. Tìm các thao tác nền bao phủ được nhiều đường, rồi xác nhận không có đường vòng. Ví dụ danh sách đặt kiểm tra ở `add` theo vị trí và `set`; ví dụ map đặt ở `put` và entry ghi xuyên.

Nếu kế thừa một lớp cụ thể rồi ủy quyền thẳng một phương thức tối ưu sang kho nền, hãy kiểm tra lại: bạn có đang bỏ qua các điểm bảo vệ ở lớp ngoài không? Mục tiêu tái sử dụng mã không cho phép thay đổi hợp đồng một cách vô tình.

### 6.2. Kiểm tra theo nhóm hợp đồng, không chỉ theo tên phương thức

| Nhóm cần kiểm chứng | Tình huống có giá trị |
| --- | --- |
| Quan hệ bằng nhau | Cùng nội dung với một cài đặt chuẩn; kiểm tra cả hai chiều; cùng loại API nhưng khác lớp. |
| Mã băm | Mọi cặp đã được xác định bằng nhau có cùng mã băm; không đòi cặp khác nhau phải khác mã băm. |
| Ngữ nghĩa loại dữ liệu | Danh sách đổi thứ tự phải được xét lại; tập hợp không trùng; map so ánh xạ chứ không so thứ tự duyệt. |
| Đường sửa trực tiếp | Thêm, thay, xóa, đầu vào không hợp lệ và giá trị biên. |
| Đường qua bộ duyệt | `Iterator.remove`, `ListIterator.add/set/remove` khi được hỗ trợ. |
| Đường qua khung nhìn | `subList`, `keySet`, `values`, `entrySet` và entry ghi xuyên. |
| Thuật toán mặc định/kế thừa | `removeIf`, `replaceAll`, `sort`, `putAll`, các phép tính và thay có điều kiện của map. |
| Lỗi giữa chừng | Theo dõi chính xác những cập nhật đã thành công và bất biến còn được giữ hay không. |
| Đường trả dữ liệu | Bộ duyệt, mảng, duyệt bằng hàm, stream: có trả đối tượng bỏ qua kiểm tra không? |

Một phép thử chỉ so `custom.equals(custom)` không kiểm chứng được tương tác giữa các cài đặt. Một phép thử chỉ gọi `put` không nhìn thấy lỗi qua `Entry.setValue`. Một phép thử chỉ dùng đầu vào hợp lệ không xác nhận chính sách từ chối.

Khi có ngẫu nhiên trong kiểm thử, nên giữ đầu vào hoặc hạt giống có thể lặp lại. Nhưng với các vấn đề trong bài, những ví dụ nhỏ được chọn đúng thường cho bằng chứng rõ hơn một lần chạy nhiều thao tác mà không biết đường gọi nào đang được kiểm tra.

### 6.3. So sánh trạng thái quan sát được sau từng bước

Với một thao tác được hỗ trợ, kiểm tra cả:

1. Kết quả trả về hoặc loại ngoại lệ.
2. Nội dung, kích thước và quan hệ giữa các khung nhìn sau đó.
3. Bất biến còn đúng trên mọi phần tử/ánh xạ.
4. Quan hệ bằng nhau và mã băm khi so với trạng thái tham chiếu tương ứng.

Đừng chỉ kiểm tra “đã có ngoại lệ”. Ví dụ `replaceAll` từ chối số âm nhưng để một số âm lọt vào trước khi ném vẫn là lỗi bất biến. Ngược lại, việc đã thay A hợp lệ trước khi từ chối B không tự là lỗi nếu hợp đồng không hứa hoàn tác cả thao tác.

Cũng không dùng chuỗi `toString` làm định nghĩa duy nhất của bằng nhau. Nó hữu ích để quan sát ví dụ, nhưng thứ tự in có thể khác trong những cấu trúc không hứa thứ tự; hai đối tượng in giống nhau cũng không tự có cùng hợp đồng API.

### 6.4. Tách hợp đồng, cài đặt nền và bằng chứng chạy

Ba lớp thông tin cần được giữ riêng:

- **Hợp đồng:** điều mà API Java và lớp của bạn công bố cho phía gọi.
- **Cài đặt:** thuật toán cụ thể đang dùng, gồm lớp nền và những phương thức đã ghi đè.
- **Bằng chứng:** một phép chạy hoặc kiểm thử xác nhận một số tình huống xác định.

Trong bài, Java 17 là mức API/cú pháp được chọn khi biên dịch. Các đường mặc định được đối chiếu với tài liệu Java 17 và mã nguồn OpenJDK 17u. Ba chương trình xác nhận các tình huống đã in trên một runtime thực tế.

Không nâng một kết quả đơn lẻ thành bảo đảm ngoài hợp đồng, chẳng hạn mọi lỗi sẽ ném trước khi sửa, mọi map đều duyệt theo thứ tự thêm hoặc mọi phương thức tên `compute` đều nguyên tử.

## 7. Thực hành có lời giải

### Câu 1. List tùy chỉnh chỉ bằng đối tượng cùng lớp

Bạn viết `equals` của một `List` bằng cách yêu cầu `other.getClass() == getClass()`. Nó chứa `["A","B"]` và một `ArrayList` cũng chứa như vậy. Có thể xảy ra lỗi gì?

**Lời giải:** danh sách tùy chỉnh có thể trả `false` khi so với `ArrayList`, trong khi `ArrayList` trả `true` khi so ngược. Điều này vi phạm tính đối xứng và hợp đồng bằng nhau giữa các cài đặt `List`. Giữ quy tắc danh sách hoặc dùng cài đặt nền phù hợp, không tự hạn chế bằng nhau theo lớp cụ thể.

**Thực hành:** lấy `Words` ở chương trình đầu, kiểm tra thêm với `LinkedList` và một danh sách cùng phần tử nhưng khác thứ tự. So sánh cả hai chiều và mã băm cho các cặp bằng nhau.

### Câu 2. Hai entry bằng nhau nhưng mã băm khác

Một wrapper entry so khóa/giá trị đúng, nhưng `hashCode` dùng `Objects.hash(key,value)`. Vì sao việc tự so hai wrapper vẫn có thể bỏ sót lỗi?

**Lời giải:** hai wrapper cùng công thức sai có thể cho cùng kết quả, nhưng không tương thích với một `Map.Entry` chuẩn biểu diễn cùng ánh xạ. Phải dùng công thức XOR của hợp đồng entry và kiểm tra chéo với một cài đặt khác. Sai ở entry còn có thể ảnh hưởng mã băm của `entrySet` và map.

**Thực hành:** thử cả khóa/giá trị khác `null` và các trường hợp `null` nếu kiểu entry mục tiêu cho phép. Không tạo trường hợp `null` bằng một API vốn từ chối `null` rồi hiểu nhầm ngoại lệ là lỗi của công thức.

### Câu 3. Chặn số âm trong put nhưng trả entry nền

Một lớp bọc map ghi đè `put` để từ chối số âm, còn `entrySet` trả thẳng `data.entrySet()`. Hai cách nào có thể vượt qua lớp kiểm tra?

**Lời giải:** gọi `setValue(-1)` trên entry được trả ra; hoặc dùng cài đặt mặc định `replaceAll` tạo kết quả âm, vì nó cập nhật qua `Entry.setValue`. Cần bảo vệ entry ghi xuyên hoặc từ chối rõ ràng khả năng ấy và cung cấp những thao tác được công bố theo cách phù hợp.

**Thực hành:** trong một bản thử riêng, thay đường entry được bọc của `ScoreMap` bằng đường nền rồi quan sát bất biến bị phá. Sau đó phục hồi wrapper và kiểm chứng cả phép bằng nhau/mã băm của entry, không chỉ ngoại lệ khi cập nhật.

### Câu 4. Bọc entrySet bằng unmodifiableSet đã đủ làm map chỉ đọc chưa?

**Lời giải:** chưa. Việc cấm thêm/xóa khỏi tập hợp không tự cấm `setValue` của đối tượng entry nằm trong tập hợp. Một khung nhìn map chỉ đọc cần bảo vệ khả năng ghi xuyên của entry, các phương thức sửa và các khung nhìn liên quan. API `Collections.unmodifiableMap` là điểm xuất phát thích hợp khi cần chính sách này với một map có sẵn.

**Thực hành:** phân biệt container không cho sửa với phần tử không cho sửa. Liệt kê các đường có thể nhận entry: bộ duyệt, mảng và stream; xác định đường nào phải cung cấp entry được bảo vệ.

### Câu 5. Khung nhìn chỉ đọc thay đổi sau khi nền đổi

Bạn tạo `Collections.unmodifiableList(source)` rồi thêm vào `source`. Khung nhìn thấy phần tử mới có vi phạm lời hứa chỉ đọc không?

**Lời giải:** không. Khung nhìn không cho sửa qua chính nó nhưng vẫn đọc từ nguồn. Nếu cần không phản ánh các thay đổi cấu trúc sau đó, chọn một bản sao với hợp đồng phù hợp. Với `List.copyOf`, cần lưu ý chính sách từ chối `null` và việc không sao chép sâu phần tử.

**Thực hành:** dùng hai đối tượng như chương trình danh sách: một khung nhìn chỉ đọc và một bản sao. Thay phần tử, thêm và xóa ở nguồn; dự đoán từng trạng thái trước khi chạy.

### Câu 6. null từ compute và null từ replaceAll

Map không cho lưu giá trị `null`. Hàm của `computeIfPresent` trả `null`, còn hàm của `replaceAll` cũng trả `null`. Chúng có cùng ý nghĩa không?

**Lời giải:** không. `computeIfPresent` dùng kết quả `null` để xóa ánh xạ đang có giá trị khác `null`. `replaceAll` muốn gán kết quả ấy vào entry; map không nhận `null` có thể từ chối. Không suy ra “map cấm null nên mọi hàm tính trả null đều phải ném lỗi”.

**Thực hành:** mở rộng `ScoreMap` bằng một phép thử cho từng phương thức và kiểm tra trạng thái sau đó. Khi thử thao tác hàng loạt, ghi nhận cả khả năng áp dụng một phần.

### Câu 7. Đã giữ bất biến thì thao tác có nguyên tử chưa?

`replaceAll` thay A thành 11 rồi bị từ chối ở B, và mọi giá trị còn lại đều không âm. Có thể kết luận thao tác đã được hoàn tác hoặc nguyên tử không?

**Lời giải:** không. Bất biến không âm vẫn được giữ nhưng trạng thái đã áp dụng một phần. Tính nguyên tử và hoàn tác là những yêu cầu riêng, không được thêm vào chỉ từ sự tồn tại của kiểm tra giá trị. Với nhiều luồng, các phương thức mặc định `Map` cũng không tự mang bảo đảm đồng thời.

**Thực hành:** viết trạng thái trước và sau của phần `partial` trong chương trình map. Nêu chính xác điều đã được chứng minh: không có giá trị âm, A đã đổi, B giữ nguyên; không thêm kết luận về giao dịch hoặc nhiều luồng.

## 8. Kiểm chứng và tài liệu tra cứu

Ba chương trình trong bài được biên dịch với `javac --release 17 -encoding UTF-8 -Xlint:all` và chạy trên OpenJDK 24.0.2; đầu ra khớp các khối `text` đã nêu. `--release 17` xác định mức cú pháp/API khi biên dịch, không có nghĩa lần chạy này dùng JVM 17.

Các ví dụ không phụ thuộc thứ tự duyệt của `HashMap`/`HashSet` để in một danh sách phần tử. Ví dụ map kiểm tra cập nhật một phần dùng `LinkedHashMap` để thứ tự liên quan được xác định. Mọi phép chạy đều trong một luồng.

Nguồn chính:

- [Object — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): hợp đồng bằng nhau và mã băm.
- [Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html), [List](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html), [Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html): ngữ nghĩa collection, khung nhìn và thuật toán mặc định liên quan.
- [Map — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): các khung nhìn, tính toán, thay có điều kiện, quy tắc `null` và giới hạn bảo đảm nguyên tử mặc định.
- [Map.Entry — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html): phép bằng nhau, XOR mã băm, ghi xuyên và vòng đời của entry.
- [Collections — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html): các lớp bọc không cho sửa.
- [AbstractMap — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.html): cách dùng `entrySet` làm nền cho một cài đặt.
- [Mã Map trong OpenJDK 17u](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/Map.java) và [mã List](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/List.java): đối chiếu những đường gọi của phương thức mặc định đang bàn; lớp cụ thể có thể ghi đè chúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-collections-008">Kiểm thử theo hợp đồng thay vì phụ thuộc lớp cài đặt</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử theo hợp đồng thay vì phụ thuộc lớp cài đặt

## Một bài kiểm thử cần bảo vệ lời hứa nào?

Giả sử một hàm nhận `List<String>`. Hôm nay nó được gọi với `ArrayList`, sau này chuyển sang `LinkedList` hoặc một danh sách do bạn tự viết. Nếu hành vi mà hàm cần vẫn được giữ, bài kiểm thử nên tiếp tục đúng.

Một kiểm tra như “đối tượng phải có lớp chính xác là `ArrayList`” thường không bảo vệ nhu cầu đó. Nó khóa vào cách tạo đối tượng. Ngược lại, “phần tử thêm ở vị trí 1 xuất hiện tại vị trí 1, các phần tử sau dịch đúng và kích thước tăng một” mô tả hành vi công khai.

**Kiểm thử theo hợp đồng** là kiểm tra những điều API cam kết, với các điều kiện sử dụng tương ứng. Hợp đồng gồm nhiều thứ hơn dữ liệu cuối:

- Điều kiện đầu vào và trạng thái trước khi gọi.
- Kết quả trả về.
- Dữ liệu nào thay đổi và dữ liệu nào được giữ.
- Ngoại lệ trong từng trường hợp.
- Thứ tự, tính duy nhất, cách so sánh bằng nhau.
- Khả năng cập nhật qua iterator và view.
- Chính sách `null`, thao tác tùy chọn và giới hạn miền dữ liệu.
- Các bảo đảm về đồng thời nếu API thật sự công bố chúng.

Một **chi tiết cài đặt** là cách lớp thực hiện lời hứa: dùng mảng hay nút liên kết, tên trường nội bộ, số ô mảng đang cấp phát, thuật toán tăng dung lượng hoặc đường gọi helper riêng.

Thay đổi chi tiết ấy có thể hợp lệ nếu hợp đồng bên ngoài còn đúng. Một bộ kiểm thử tốt giúp sửa cách cài đặt mà vẫn phát hiện việc phá vỡ hành vi cần giữ.

## Ba nguồn hợp đồng cần phân biệt

| Nguồn lời hứa | Ví dụ | Bộ kiểm thử áp dụng |
| --- | --- | --- |
| Interface | `List.set` trả phần tử cũ; iterator list duyệt theo thứ tự | Nhóm kiểm thử dùng chung cho các triển khai đáp ứng khả năng cần thiết |
| Họ cấu trúc hoặc hồ sơ khả năng | Danh sách kích thước cố định; list cho phép `null`; iterator chỉ đọc | Nhóm kiểm thử có điều kiện theo khả năng đã công bố |
| API công khai riêng của lớp | Queue có dung lượng cụ thể; view có thứ tự cụ thể; iterator snapshot | Nhóm kiểm thử mở rộng dành cho lời hứa đó |

Không phải mọi đặc điểm của một lớp cụ thể đều là chi tiết nội bộ. Nếu lớp công bố thứ tự duyệt hoặc chính sách snapshot, đó là hợp đồng có thể kiểm thử. Mục tiêu là xác định nguồn của từng kỳ vọng, không phải cấm nhắc tên lớp trong mọi bài kiểm thử.

Ví dụ, kiểm thử một thuật toán nhận `Set` không nên tự đòi thứ tự chèn. Nhưng kiểm thử một API công bố giữ thứ tự chèn cần xác nhận thứ tự đó. Hai mục tiêu khác nhau dẫn tới hai kiểu kiểm tra khác nhau.

Với lớp kế thừa `AbstractList` hoặc các lớp khung khác, đừng chỉ kiểm tra những phương thức mới viết. Iterator, view và phương thức mặc định có thể mở các đường hành vi khác. `AbstractList` cung cấp nhiều phần cài đặt trên các phương thức nền; xem [AbstractList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html).

## Một vài kỳ vọng dễ khóa nhầm vào cài đặt

| Kỳ vọng dễ sai | Vì sao không là hợp đồng chung | Kỳ vọng phù hợp hơn |
| --- | --- | --- |
| Kết quả phải có `getClass() == ArrayList.class` | Một triển khai khác có thể cung cấp cùng hành vi | Kiểm tra dữ liệu và các khả năng mà bên dùng yêu cầu |
| Mọi `Set` duyệt theo một thứ tự cố định | `Set` không đưa ra một thứ tự duyệt chung | Kiểm tra thành viên/tính duy nhất; thêm kiểm tra thứ tự khi API công bố |
| Iterator của priority queue trả dãy đã sắp | Thứ tự phần tử đầu để lấy khác thứ tự iterator | Kiểm tra thứ tự qua các lần lấy đầu, theo comparator đã chọn |
| Mọi `List` đều thêm, xóa và thay được | Nhiều thao tác là tùy chọn | Xác định hồ sơ khả năng rồi kiểm tra đúng từng thao tác |
| List sửa được thì iterator cũng sửa được | Một iterator snapshot có thể chỉ đọc | Kiểm tra khả năng của iterator riêng |
| Sửa ngoài iterator luôn lập tức gây CME | Phụ thuộc chính sách và điểm kiểm tra | Chỉ kiểm tra bảo đảm đã công bố, không dùng CME làm điều kiện đúng đắn |
| Ngoại lệ phải có đúng câu chữ của JDK đang chạy | Thông điệp thường không là hợp đồng chung | Kiểm tra loại ngoại lệ và trạng thái liên quan |
| Thêm N phần tử xong phải có dung lượng nội bộ M | Đó có thể là chính sách cấp phát riêng | Kiểm tra nội dung, kích thước và khả năng đã hứa |
| Một thao tác phải xong dưới vài mili giây trong mọi lần CI | Chịu ảnh hưởng tải máy và môi trường | Tách kiểm thử hành vi khỏi phép đo hiệu năng có phương pháp |

`HashSet` và `HashMap` không bảo đảm thứ tự duyệt cố định; một kết quả hiển thị lặp lại nhiều lần trên máy hiện tại chưa tạo ra hợp đồng mới. Xem [HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html) và [HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

Đối với `PriorityQueue`, lời hứa về phần tử ưu tiên không khiến iterator trở thành phép sắp xếp. Kiểm thử cần chọn đúng thao tác để quan sát tính chất đó; xem [PriorityQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html).

## Viết kỳ vọng từ tiền điều kiện và hậu điều kiện

Xét `set(index, value)` của một list hỗ trợ thay thế.

Một trường hợp có ý nghĩa:

~~~text
Trước:
    [A, B, A]
    index = 1
    value = X

Sau:
    giá trị trả về = B
    nội dung = [A, X, A]
    kích thước = 3
~~~

Nếu chỉ kiểm tra nội dung cuối, một lớp trả nhầm `X` thay vì `B` vẫn vượt qua. Nếu chỉ kiểm tra giá trị trả về, một lớp không thực sự thay phần tử cũng có thể vượt qua.

Với một lần xóa theo giá trị trên list:

~~~text
Trước:
    [A, B, A]

remove("A"):
    trả true
    còn [B, A]
~~~

Hợp đồng xóa lần xuất hiện đầu tiên làm thứ tự sau thao tác có ý nghĩa. Chỉ đổi kết quả thành set `{A,B}` sẽ làm mất thông tin về vị trí và số lần xuất hiện.

Các hành vi về vị trí, thứ tự, giá trị trả về và phép so sánh list được quy định trong [List của Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html). Một ca kiểm thử nên giải thích được nó đang bảo vệ phần nào của lời hứa ấy.

### Kiểm tra ngoại lệ cũng có tiền điều kiện

Để kiểm tra `get` ngoài biên, dùng một list hợp lệ và chỉ số không hợp lệ. Để kiểm tra `UnsupportedOperationException` của `set`, dùng một vị trí hợp lệ trên list không hỗ trợ thay thế.

Nếu vừa dùng chỉ số sai vừa yêu cầu thao tác không được hỗ trợ, bài kiểm thử có thể tự đòi một thứ tự ưu tiên ngoại lệ mà API không hứa.

Tương tự, iterator không hỗ trợ `remove` và iterator hỗ trợ nhưng chưa gọi `next` là hai trường hợp khác. Không dùng một lời gọi sai đồng thời nhiều điều kiện để kết luận khả năng của đối tượng.

## Hồ sơ khả năng phải được khai báo rõ

Một bộ kiểm thử dùng chung không có nghĩa áp cùng một thao tác ghi cho mọi đối tượng.

Có thể mô tả một hồ sơ nhỏ như sau:

| Khả năng | Câu hỏi cần trả lời |
| --- | --- |
| Thay đổi kích thước | Có cho thêm/xóa theo các đường đang kiểm tra không? |
| Thay thế phần tử | `set(index,value)` có được hỗ trợ không? |
| Iterator xóa | `Iterator.remove` có được hỗ trợ không? |
| List iterator thay thế | `ListIterator.set` có được hỗ trợ không? |
| Chính sách `null` | Khởi tạo/ghi/đọc `null` có quy tắc gì? |
| View | `subList`, `keySet`, `values` hoặc `entrySet` ghi ngược thế nào? |
| Đồng thời | Cần đồng bộ ngoài, có snapshot hay cho phép cập nhật đồng thời? |

Không dùng một cờ “mutable” để suy ra tất cả. `CopyOnWriteArrayList` cho sửa list nhưng iterator của nó không hỗ trợ `remove/set/add`. Đó là hợp đồng được công bố, không phải lỗi để bộ kiểm thử chung phải từ chối. Xem [CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html).

Hồ sơ phải lấy từ API đã duyệt, không được suy ra bằng cách thử ngẫu nhiên rồi đổi cờ cho tới khi bài kiểm thử xanh. Nếu lớp được yêu cầu hỗ trợ xóa mà xóa đang hỏng, việc đổi cờ thành “không hỗ trợ” chỉ làm mất yêu cầu ban đầu.

Cũng không phải mọi khả năng đều độc lập theo đúng năm cờ của một ví dụ. Một collection chỉ cho xóa qua view, một list hạn chế trùng hoặc một cấu trúc có miền phần tử riêng có thể cần hồ sơ khác. Bộ kiểm thử phải có tiền điều kiện phù hợp với đối tượng của nó.

### Trường hợp không có tác động không đủ để dò khả năng

Một collection không hỗ trợ thêm có thể nhận `addAll` với nguồn rỗng mà không ném ngoại lệ. Một thao tác xóa giá trị không có có thể trả `false` dù xóa giá trị đang có sẽ bị chặn.

Do đó, khi kiểm tra khả năng không được hỗ trợ, chọn đầu vào thật sự cần đường ghi đó, chẳng hạn thêm một phần tử vào vị trí hợp lệ hoặc xóa phần tử đang tồn tại.

Khi kiểm tra một lời gọi không có tác động theo hợp đồng tổng quát, đừng tự ép tất cả triển khai phải có cùng kết quả ngoại lệ. Một lớp cụ thể có thể công bố chính sách mạnh hơn và được kiểm thử riêng. Quy tắc thao tác tùy chọn và các trường hợp không có tác động nằm trong [Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html).

## Chuẩn bị dữ liệu và bộ tạo đối tượng

Một bộ tạo đối tượng, thường gọi là `factory` trong mã kiểm thử, nhận dữ liệu đầu vào rồi tạo đối tượng cần thử. Bộ kiểm thử hành vi làm việc qua interface, không cần biết bên trong là mảng hay liên kết.

Ví dụ về ý tưởng:

~~~java
Function<List<String>, List<String>> factory = ArrayList::new;
List<String> actual = factory.apply(List.of("A", "B", "A"));
~~~

Đoạn này minh họa cách truyền bộ tạo; các phần `import` và bộ kiểm thử đầy đủ có ở chương trình phía dưới.

Dữ liệu nên có lý do:

- Collection rỗng kiểm tra biên không có phần tử.
- Một phần tử kiểm tra xóa cuối và chuyển sang rỗng.
- `[A,B,A]` kiểm tra thứ tự cùng hai lần xuất hiện bằng nhau.
- `null` chỉ đưa vào hồ sơ cho phép hoặc ca từ chối rõ ràng.
- Phần tử có cùng hash nhưng không bằng nhau hữu ích khi kiểm tra cấu trúc băm.
- Các đối tượng bằng nhau nhưng khác định danh giúp phát hiện xóa nhầm lần xuất hiện.

Mỗi ca có thay đổi nên dùng một đối tượng mới hoặc khôi phục trạng thái theo giao thức rõ ràng. Một ca kiểm thử không nên phụ thuộc vào việc ca khác đã chạy trước.

Với đối tượng sửa được, các lần tạo cần không vô tình chia sẻ kho có thể bị sửa lẫn nhau. Với đối tượng bất biến, việc bộ tạo có thể dùng lại cùng một đối tượng không tự là lỗi; đừng ép danh tính khác nhau khi hợp đồng chỉ hứa giá trị.

## Chương trình đầy đủ: một bộ kiểm thử, nhiều triển khai

Lưu vào `CollectionContractDemo.java`. Chương trình không cần thư viện kiểm thử ngoài. `Verify` dùng các phép kiểm tra tường minh nên không phụ thuộc việc bật `-ea` cho câu lệnh `assert` của Java.

Phạm vi bộ kiểm thử này là các hồ sơ list đã chọn:

- Bộ tạo nhận danh sách nguồn, giữ thứ tự và các lần xuất hiện, đồng thời tạo được list rỗng.
- Những hồ sơ đánh dấu `resizable` hỗ trợ nhóm thêm/xóa trực tiếp và xóa khoảng qua `subList` được dùng trong bài.
- `replaceable` chỉ nói về `List.set`; khả năng thay/xóa qua iterator được khai báo riêng.
- Chính sách `allowsNull` trong ví dụ bao gồm khởi tạo chứa `null`, đọc/tìm nó và thay bằng `null` nếu hỗ trợ `set`.
- Bộ kiểm thử chạy tuần tự, không xác minh chính sách cập nhật đồng thời.

Đây là một bộ kiểm thử có phạm vi xác định, không phải bộ chứng nhận mọi triển khai `List` có thể có.

`SlotList` là lớp tùy chỉnh giữ số ô cố định, hỗ trợ `get/size/set` và sao chép nguồn vào mảng riêng. `WrongReturnList` cố ý trả phần tử mới từ `set` để kiểm tra chất lượng bộ kiểm thử. Không dùng lớp cố ý sai làm cài đặt cho ứng dụng.

~~~java
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.concurrent.CopyOnWriteArrayList;

public class CollectionContractDemo {
    record Spec(
            String name,
            Function<List<String>, List<String>> factory,
            boolean resizable,
            boolean replaceable,
            boolean iteratorRemovable,
            boolean iteratorReplaceable,
            boolean allowsNull) {
        List<String> fresh(String... values) {
            return factory.apply(Arrays.asList(values));
        }
    }

    static class SlotList extends AbstractList<String> {
        private final String[] data;

        SlotList(List<String> source) {
            data = source.toArray(String[]::new);
        }

        @Override public int size() {
            return data.length;
        }

        @Override public String get(int index) {
            return data[Objects.checkIndex(index, data.length)];
        }

        @Override public String set(int index, String value) {
            Objects.checkIndex(index, data.length);
            String old = data[index];
            data[index] = value;
            return old;
        }
    }

    static final class WrongReturnList extends SlotList {
        WrongReturnList(List<String> source) {
            super(source);
        }

        @Override public String set(int index, String value) {
            super.set(index, value);
            return value;
        }
    }

    static final class ContractFailure extends AssertionError {
        ContractFailure(String message) {
            super(message);
        }
    }

    static final class Verify {
        private int checks;

        void check(boolean condition, String message) {
            checks++;
            if (!condition) {
                throw new ContractFailure(message);
            }
        }

        void equal(Object expected, Object actual, String message) {
            check(Objects.equals(expected, actual), message);
        }

        void throwsType(Class<? extends Throwable> type,
                        Runnable operation, String message) {
            checks++;
            try {
                operation.run();
            } catch (Throwable failure) {
                if (type.isInstance(failure)) {
                    return;
                }
                throw new ContractFailure(message + ": wrong exception "
                        + failure.getClass().getSimpleName());
            }
            throw new ContractFailure(message + ": no exception");
        }
    }

    static int contract(Spec spec) {
        Verify v = new Verify();
        List<String> list = spec.fresh("A", "B", "A");
        List<String> expected = List.of("A", "B", "A");
        v.equal(3, list.size(), "seed size");
        v.check(!list.isEmpty(), "seed must be nonempty");
        v.equal(expected, list, "list order and multiplicity");
        v.check(list.equals(expected) && expected.equals(list), "symmetric equality");
        v.equal(expected.hashCode(), list.hashCode(), "equal lists need equal hashes");
        v.equal("B", list.get(1), "indexed read");
        v.equal(0, list.indexOf("A"), "first equal occurrence");
        v.equal(2, list.lastIndexOf("A"), "last equal occurrence");
        v.check(list.contains("A") && !list.contains("missing"), "membership");
        v.throwsType(IndexOutOfBoundsException.class,
                () -> list.get(-1), "negative read index");
        v.throwsType(IndexOutOfBoundsException.class,
                () -> list.get(list.size()), "read index at size");

        Object[] array = list.toArray();
        v.equal(3, array.length, "array length");
        array[0] = "outside";
        v.equal("A", list.get(0), "array must not write through");
        String[] typed = list.toArray(String[]::new);
        v.check(Arrays.equals(new String[]{"A", "B", "A"}, typed), "typed array contents");

        Iterator<String> iterator = list.iterator();
        v.check(iterator.hasNext() && iterator.hasNext(), "hasNext must not consume");
        v.equal("A", iterator.next(), "first iterator value");
        v.equal("B", iterator.next(), "next without hasNext");
        v.equal("A", iterator.next(), "last iterator value");
        v.check(!iterator.hasNext(), "iterator exhausted");
        v.throwsType(NoSuchElementException.class, iterator::next, "next past end");

        List<String> empty = spec.fresh();
        v.check(empty.isEmpty() && empty.size() == 0, "empty construction");
        v.check(!empty.iterator().hasNext(), "empty iterator");
        v.throwsType(NoSuchElementException.class,
                empty.iterator()::next, "next on empty");

        List<String> forSet = spec.fresh("A", "B", "A");
        if (spec.replaceable()) {
            v.equal("B", forSet.set(1, "X"), "set must return previous value");
            v.equal(List.of("A", "X", "A"), forSet, "set must replace one position");
        } else {
            v.throwsType(UnsupportedOperationException.class,
                    () -> forSet.set(1, "X"), "replacement unsupported");
            v.equal(expected, forSet, "rejected replacement must not change list");
        }
        ListIterator<String> listIterator = forSet.listIterator();
        listIterator.next();
        if (spec.iteratorReplaceable()) {
            listIterator.set("Z");
            v.equal(List.of("Z", "X", "A"), forSet, "list iterator set");
        } else {
            v.throwsType(UnsupportedOperationException.class,
                    () -> listIterator.set("Z"), "iterator replacement unsupported");
        }

        List<String> forResize = spec.fresh("A", "B", "A");
        if (spec.resizable()) {
            forResize.add(1, "X");
            v.equal(List.of("A", "X", "B", "A"), forResize, "indexed insertion");
            v.check(forResize.add("Y"), "append reports a change");
            v.equal("B", forResize.remove(2), "indexed removal returns removed value");
            v.check(forResize.remove("A"), "remove present value");
            v.equal(List.of("X", "A", "Y"), forResize, "remove first equal occurrence");
            v.check(!forResize.remove("missing"), "missing removal reports false");

            List<String> byView = spec.fresh("A", "B", "A");
            byView.subList(1, 3).clear();
            v.equal(List.of("A"), byView, "subList removal writes through");
            forResize.clear();
            v.check(forResize.isEmpty(), "clear removes all values");
        } else {
            v.throwsType(UnsupportedOperationException.class,
                    () -> forResize.add(0, "X"), "insertion unsupported");
            v.throwsType(UnsupportedOperationException.class,
                    () -> forResize.remove(0), "indexed removal unsupported");
            v.throwsType(UnsupportedOperationException.class,
                    () -> forResize.remove("A"), "value removal unsupported");
            v.equal(expected, forResize, "rejected resize must preserve values");
        }


        List<String> byIterator = spec.fresh("A", "B", "A");
        Iterator<String> removing = byIterator.iterator();
        if (spec.iteratorRemovable()) {
            v.throwsType(IllegalStateException.class,
                    removing::remove, "remove before next");
            removing.next();
            removing.next();
            removing.next();
            removing.remove();
            v.equal(List.of("A", "B"), byIterator, "iterator removes last returned occurrence");
            v.throwsType(IllegalStateException.class,
                    removing::remove, "remove twice");
        } else {
            removing.next();
            v.throwsType(UnsupportedOperationException.class,
                    removing::remove, "iterator removal unsupported");
            v.equal(expected, byIterator, "rejected iterator removal preserves list");
        }

        if (spec.allowsNull()) {
            List<String> nullable = spec.fresh("A", null, "A");
            v.equal(null, nullable.get(1), "null value can be read");
            v.equal(1, nullable.indexOf(null), "null value can be found");
            if (spec.replaceable()) {
                List<String> target = spec.fresh("A");
                v.equal("A", target.set(0, null), "replace with null");
                v.equal(null, target.get(0), "null replacement stored");
            }
        } else {
            v.throwsType(NullPointerException.class,
                    () -> spec.fresh("A", null), "factory rejects null");
        }
        return v.checks;
    }

    static int modelSequence(Spec spec, long seed, int steps) {
        Verify v = new Verify();
        Random random = new Random(seed);
        List<String> actual = spec.fresh();
        List<String> model = new ArrayList<>();
        for (int step = 0; step < steps; step++) {
            int action = random.nextInt(5);
            String value = "v" + random.nextInt(7);
            if (model.isEmpty() || action == 0) {
                int index = random.nextInt(model.size() + 1);
                model.add(index, value);
                actual.add(index, value);
            } else if (action == 1) {
                int index = random.nextInt(model.size());
                v.equal(model.remove(index), actual.remove(index),
                        "model remove result at step " + step);
            } else if (action == 2) {
                int index = random.nextInt(model.size());
                v.equal(model.set(index, value), actual.set(index, value),
                        "model set result at step " + step);
            } else if (action == 3) {
                v.equal(model.remove(value), actual.remove(value),
                        "model value removal at step " + step);
            } else {
                model.clear();
                actual.clear();
            }
            v.equal(model.size(), actual.size(), "model size at step " + step);
            v.equal(model, actual, "model sequence at step " + step);
        }
        return steps;
    }

    public static void main(String[] args) {
        List<Spec> specs = List.of(
                new Spec("ArrayList", ArrayList::new, true, true, true, true, true),
                new Spec("LinkedList", LinkedList::new, true, true, true, true, true),
                new Spec("CopyOnWriteArrayList", CopyOnWriteArrayList::new,
                        true, true, false, false, true),
                new Spec("array-backed fixed list",
                        source -> Arrays.asList(source.toArray(String[]::new)),
                        false, true, false, true, true),
                new Spec("List.copyOf", List::copyOf, false, false, false, false, false),
                new Spec("SlotList", SlotList::new, false, true, false, true, true)
        );

        for (Spec spec : specs) {
            int checks = contract(spec);
            System.out.println(spec.name() + " = PASS (" + checks + " checks)");
        }
        for (Spec spec : specs) {
            if (spec.resizable() && spec.replaceable()) {
                int steps = modelSequence(spec, 2026L, 200);
                System.out.println(spec.name() + " model = PASS (" + steps + " steps)");
            }
        }

        Spec broken = new Spec("WrongReturnList",
                WrongReturnList::new, false, true, false, true, true);
        try {
            contract(broken);
            throw new AssertionError("Broken implementation was not detected");
        } catch (ContractFailure expected) {
            System.out.println("deliberately broken = DETECTED: " + expected.getMessage());
        }
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 CollectionContractDemo.java
java CollectionContractDemo
~~~

Mã đã được biên dịch với `--release 17` và chạy bằng OpenJDK Zulu 24.0.2. Stdout chính xác:

~~~text
ArrayList = PASS (41 checks)
LinkedList = PASS (41 checks)
CopyOnWriteArrayList = PASS (40 checks)
array-backed fixed list = PASS (36 checks)
List.copyOf = PASS (33 checks)
SlotList = PASS (36 checks)
ArrayList model = PASS (200 steps)
LinkedList model = PASS (200 steps)
CopyOnWriteArrayList model = PASS (200 steps)
deliberately broken = DETECTED: set must return previous value
~~~

## Bộ kiểm thử đã kiểm tra điều gì?

### Phần đọc chung giữ nguyên qua các triển khai

Các ca đầu kiểm tra:

- Kích thước, rỗng/không rỗng.
- Thứ tự và số lần xuất hiện.
- `get`, `indexOf`, `lastIndexOf`, `contains`.
- `equals` hai chiều với một list có cùng dữ liệu và tính nhất quán `hashCode`.
- Chỉ số đọc âm và bằng `size`.
- `toArray` giữ nội dung và không cho mảng trả về ghi ngược vào list.
- Iterator không tiêu thụ dữ liệu khi hỏi `hasNext` nhiều lần.
- `next` hoạt động không cần hỏi `hasNext` trước và báo hết đúng loại ngoại lệ.

Không có phép kiểm tra tên lớp, tên trường hay dung lượng mảng nội bộ.

`toArray` không ghi ngược là hợp đồng dữ liệu công khai, dù một triển khai dùng mảng làm kho. Ngược lại, bắt mảng nội bộ phải có chiều dài gấp đôi số phần tử sẽ khóa vào chính sách cấp phát riêng.

### Phần thay thế kiểm tra cả kết quả và tác động

Với hồ sơ cho `set`, bộ kiểm thử xác nhận giá trị cũ trả về và nội dung mới. Sau đó nó tạo một list iterator mới rồi kiểm tra khả năng thay qua iterator theo cờ riêng.

Tạo iterator sau thao tác sửa trực tiếp giúp ca không dựa vào giả định chung về việc một iterator đang tồn tại sẽ quan sát mọi thay thế từ bên ngoài. Nếu muốn kiểm tra snapshot hoặc khả năng quan sát thay đổi, đó nên là một nhóm có chính sách được khai báo riêng.

Đối với hồ sơ không cho thay thế, thao tác dùng chỉ số hợp lệ và giá trị mới thật. Sau ngoại lệ, bộ kiểm thử còn xác nhận nội dung cần giữ nguyên.

### Phần thay đổi kích thước kiểm tra nhiều đường ghi

Với list hỗ trợ, chương trình thêm theo chỉ số, nối cuối, xóa theo chỉ số, xóa giá trị đầu tiên bằng nhau, xóa giá trị không có, xóa khoảng qua view và `clear`.

Khả năng xóa qua iterator được kiểm tra riêng. Hai lần xuất hiện `A` trong `[A,B,A]` giúp phân biệt việc xóa phần tử cuối vừa trả với việc tìm từ đầu rồi xóa nhầm phần tử bằng nhau đầu tiên.

Với list cố định hoặc chỉ đọc, ca kiểm tra những thao tác có tác động thật sự nhận `UnsupportedOperationException`, đồng thời dữ liệu không bị sửa ngoài ý muốn.

`Arrays.asList` là một list kích thước cố định liên kết với mảng được cung cấp. Trong factory ví dụ, mảng được tạo mới từ nguồn cho mỗi lần gọi, nên các ca không chia sẻ một mảng mutable ngoài ý muốn. Xem [Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

### CopyOnWriteArrayList cho thấy giá trị của hồ sơ riêng

Cấu hình này vượt qua các ca sửa list trực tiếp và chuỗi mô hình, nhưng các ca iterator lại xác nhận rằng ghi qua iterator không được hỗ trợ.

Nếu bộ kiểm thử dùng một cờ `resizable` rồi tự suy ra iterator phải xóa được, nó sẽ báo lỗi cho một đối tượng đang làm đúng hợp đồng của chính nó.

Đây cũng là lý do không nên chia bài kiểm thử chỉ theo tên lớp rồi viết một bản hoàn toàn khác cho từng tên. Phần hành vi chung vẫn dùng lại, còn khác biệt được biểu diễn bằng khả năng thật sự.

### Lớp cố ý sai giúp kiểm tra bộ kiểm thử

`WrongReturnList` thay dữ liệu đúng nhưng trả giá trị mới thay vì giá trị cũ. Nếu bộ kiểm thử chỉ quan sát nội dung, lỗi này có thể bị bỏ qua.

Dòng `deliberately broken = DETECTED: set must return previous value` xác nhận ca về giá trị trả về đã phát hiện lỗi được cài vào.

Đây là một thí nghiệm đơn giản về việc tạo biến thể sai để xem bài kiểm thử có phát hiện không. Nó không chứng minh bộ kiểm thử phát hiện mọi lỗi. Có thể tiếp tục thiết kế các biến thể có lý do khác: bỏ qua chèn cuối, xóa sai lần xuất hiện, iterator trả thiếu phần tử, view sửa bản sao thay vì gốc.

Không cần giữ các lớp sai trong mã sản phẩm. Ý nghĩa của thí nghiệm là xem mỗi lời hứa quan trọng có một phép kiểm tra thực sự phân biệt được đúng và sai hay chưa.

## Kiểm thử bằng mô hình sau mỗi bước

Một lỗi cấu trúc có thể chỉ xuất hiện sau một chuỗi thao tác: thêm, thay, xóa, xóa hết rồi thêm lại. Một ca ngắn độc lập cho từng phương thức chưa chắc tạo ra chuỗi đó.

Trong `modelSequence`, chương trình giữ:

- `model`: một `ArrayList` làm mô hình tham chiếu cho dữ liệu có thứ tự.
- `actual`: đối tượng cần kiểm tra.
- Một nguồn số giả ngẫu nhiên với seed cố định.
- Một chuỗi thêm, thay, xóa theo chỉ số, xóa theo giá trị và xóa hết.

Sau mỗi bước, chương trình so cả giá trị trả về liên quan, kích thước và dãy phần tử. Nó không chỉ so ở cuối, vì lỗi trung gian có thể bị thao tác sau vô tình che mất.

Chỉ số được sinh dựa trên trạng thái mô hình để tạo thao tác hợp lệ. Những ca chỉ số không hợp lệ đã có nhóm riêng. Tách hai nhóm giúp biết lỗi đến từ xử lý trạng thái hay kiểm tra tiền điều kiện.

### Seed cố định làm lỗi tái hiện được

Seed `2026L` và cùng chuỗi gọi `Random` cho cùng chuỗi số giả ngẫu nhiên. Đây là tính chất được `java.util.Random` công bố, xem [Random](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html).

Trong bài, mỗi hồ sơ phù hợp chạy 200 bước với seed ấy. Nếu mở rộng bộ sinh, nên lưu cả seed và nhật ký thao tác khi lỗi, vì thay đổi cách sinh số hoặc thêm một lần gọi `nextInt` cũng làm chuỗi thao tác phía sau đổi.

Khi gặp lỗi, rút chuỗi về một phản ví dụ ngắn:

~~~text
Chuỗi dài gây lỗi
    -> bỏ bớt các đoạn không cần
    -> giữ lại chuỗi vẫn tái hiện
    -> xác định tiền điều kiện và lời hứa bị phá
    -> thêm ca hồi quy ngắn, có tên rõ
~~~

Ví dụ trên chỉ minh họa quy trình phân tích lỗi; chương trình đầy đủ không cài thuật toán tự rút gọn chuỗi.

### Mô hình tham chiếu cũng có giới hạn

Dùng `ArrayList` làm tham chiếu phù hợp với hồ sơ list có cùng thứ tự, cùng miền giá trị và cùng khả năng trực tiếp đang được thử. Nó không phù hợp để bắt một set giữ phần tử trùng, hay bắt một priority queue phục vụ FIFO.

Chạy candidate cũng là `ArrayList` đối chiếu với mô hình `ArrayList` chủ yếu xác minh cách tổ chức bộ sinh; nó không tạo một bằng chứng độc lập mạnh về mọi hành vi của `ArrayList`. Vì vậy, bài còn có các kỳ vọng tường minh và lớp tùy chỉnh.

Số 200 bước hoặc số checks trong stdout là thông tin về thí nghiệm đã chạy, không là thước đo đầy đủ của hợp đồng. Một phép kiểm tra đúng vào giá trị trả về có thể quan trọng hơn hàng nghìn thao tác không quan sát giá trị đó.

## So sánh đúng theo loại cấu trúc

### List: giữ thứ tự và số lần xuất hiện

Với `List`, `[A,B,A]` khác `[A,A,B]`. Chuyển cả hai sang set làm mất thông tin và có thể khiến bài kiểm thử bỏ qua lỗi thứ tự hoặc xóa thiếu phần tử trùng.

Nếu dữ liệu có đối tượng mutable, hãy giữ kỳ vọng ổn định. Nếu kỳ vọng và kết quả cùng trỏ tới một đối tượng bị sửa, cả hai có thể đổi giống nhau và khiến một ca sai vẫn vượt qua.

Khi điều cần bảo vệ là danh tính của đúng lần xuất hiện, kiểm tra thêm tham chiếu hoặc trường nhận diện riêng phù hợp. Kiểm thử theo hợp đồng không cấm dùng phép so sánh danh tính; nó chỉ yêu cầu danh tính thật sự là điều cần quan sát trong ca đó.

### Set: kiểm tra tính duy nhất, không bịa thứ tự

Với một set theo ngữ nghĩa bằng nhau thông thường:

- Thêm một phần tử mới làm thay đổi set.
- Thêm một đối tượng bằng phần tử đã có không tạo thêm thành viên.
- Các phần tử bằng nhau không xuất hiện hai lần khi duyệt.
- Hai set có cùng phần tử cần tương thích về `equals/hashCode`, bất kể thứ tự duyệt.

Không chỉ chuẩn hóa kết quả bằng `new HashSet<>(actual)` rồi kết luận candidate không có phần tử trùng. Phép chuẩn hóa đó tự loại trùng và có thể che một set cài sai. Cần kiểm tra thêm số phần tử duyệt, kích thước và tính duy nhất của candidate.

Với một collection dạng túi cho phép trùng nhưng không có thứ tự, so sánh bảng tần suất thường phù hợp hơn so sánh set. Với `Set` có thứ tự được công bố, bổ sung nhóm kiểm tra thứ tự riêng. Hợp đồng nền về tính duy nhất của set nằm trong [Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html).

### Map: so ánh xạ và các view

Với map không có thứ tự được công bố, nội dung được mô tả bằng các cặp khóa–giá trị, không phải chuỗi `toString`.

Những ca có ích khi thao tác được hỗ trợ:

1. Thêm khóa mới, kiểm tra kích thước và giá trị.
2. `put` vào khóa đã có, kiểm tra giá trị cũ trả về và kích thước không tăng.
3. Xóa khóa có và không có.
4. Với map cho phép giá trị `null`, phân biệt khóa vắng với khóa hiện diện có giá trị `null` bằng `containsKey`.
5. Lấy các view rồi kiểm tra tác động ghi ngược được công bố.
6. Kiểm tra `Entry.setValue` theo khả năng hỗ trợ, không chỉ `map.put`.

`get(key) == null` không đủ chứng minh khóa vắng trong map cho phép giá trị `null`. Tương tự, chặn `put` chưa chứng minh mọi entry và view đều chỉ đọc.

Một model map phải dùng cùng ngữ nghĩa so sánh khóa của đối tượng cần thử. Không dùng mô hình dựa trên `equals` cho một cấu trúc cố ý công bố ngữ nghĩa khóa khác rồi gọi mọi khác biệt là lỗi. Xem các quy tắc ánh xạ và view trong [Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

### Queue: phân biệt thứ tự lấy với thứ tự duyệt

`Queue` không có một quy tắc FIFO áp dụng cho mọi triển khai. Có queue ưu tiên và cách tổ chức khác; mỗi triển khai phải nêu chính sách của mình.

Với một queue FIFO đã công bố, kiểm tra các lần lấy trả đúng thứ tự thêm. Với queue ưu tiên, kiểm tra phần tử lấy đầu phù hợp comparator; nếu comparator coi hai phần tử bằng nhau mà API không hứa ổn định, đừng tự buộc thứ tự chèn giữa chúng.

Cũng cần kiểm tra cặp hành vi:

| Tình huống | Phương thức báo bằng giá trị | Phương thức báo bằng ngoại lệ |
| --- | --- | --- |
| Queue rỗng, lấy đầu | `poll()` trả `null` | `remove()` ném `NoSuchElementException` |
| Queue rỗng, xem đầu | `peek()` trả `null` | `element()` ném `NoSuchElementException` |
| Queue giới hạn đầy | `offer(e)` trả `false` | `add(e)` ném `IllegalStateException` |

Chính sách `null` cần xem riêng theo queue, không tự suy ra mọi queue đều có cùng hạn chế. Các khác biệt này được mô tả trong [Queue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html).

## Các điều không nên đưa vào bộ kiểm thử chung

### Không khẳng định kết quả trong vùng hành vi không được xác định

Ví dụ, sửa cấu trúc một list trực tiếp trong khi đang dùng view hoặc iterator có thể rơi vào trường hợp mà hợp đồng không định nghĩa một kết quả chung. Một bài kiểm thử không nên vừa vi phạm điều kiện sử dụng vừa đòi dữ liệu cuối cụ thể cho mọi list.

Với iterator có chính sách fail-fast, có thể kiểm tra các trường hợp tuần tự theo tài liệu triển khai. Nhưng không biến một lần không có CME thành bằng chứng không có tranh chấp, và không dùng việc chắc chắn có CME làm nền tảng đúng đắn của thuật toán đồng thời. Xem [ConcurrentModificationException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ConcurrentModificationException.html).

Tương tự, không viết ca chung yêu cầu `Iterator.remove` sau `forEachRemaining` luôn hoạt động. Hợp đồng tổng quát không bảo đảm hành vi đó. Xem [Iterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html).

### Không mặc định mọi thao tác hàng loạt là giao dịch

Nếu `addAll` xử lý lần lượt rồi gặp phần tử không hợp lệ, một phần dữ liệu có thể đã được thêm. Nếu callback của một phép cập nhật ném lỗi giữa chừng, cũng không tự có hoàn tác.

Bộ kiểm thử chỉ yêu cầu “toàn bộ thành công hoặc dữ liệu không đổi” khi API thật sự hứa bảo đảm ấy. Nếu không, nên kiểm tra những hậu điều kiện được xác định và tách ca kiểm tra bảo đảm mạnh của lớp riêng.

Khi cố ý tạo ngoại lệ trong callback, giữ lại thông tin về thứ tự thao tác có bảo đảm. Không tự suy ra callback của một cấu trúc không thứ tự sẽ đi qua cùng một khóa trước trong mọi lần chạy.

### Không kiểm tra số lần gọi nội bộ nếu API không hứa

Một cấu trúc băm có thể dùng hash để loại phần tử không thể bằng nhau mà không gọi `equals` cho mọi phần tử. Một thuật toán có thể dùng iterator hoặc chỉ số. Bắt buộc số lần `equals/get/iterator` chính xác dễ làm bài kiểm thử thất bại khi có một tối ưu hợp lệ.

Nếu số lần gọi có ý nghĩa nghiệp vụ, chẳng hạn một API công bố điều kiện gọi hàm tính giá trị, kiểm tra đúng bảo đảm công khai đó. Đừng tự áp một sơ đồ gọi quan sát được trong một phiên bản thành hợp đồng của interface.

## Tách ba loại bằng chứng

### Bằng chứng hành vi

Quan sát qua API công khai: kết quả, dữ liệu, ngoại lệ, view, thứ tự và các quan hệ giữa thao tác. Đây là phần có thể dùng chung rộng nhất.

### Bằng chứng bất biến nội bộ

Khi tự phát triển cấu trúc dữ liệu, có thể cần kiểm tra các nút liên kết khớp hai chiều, phần mảng ngoài `size` không giữ tham chiếu thừa, hoặc số entry ở các vùng lưu trữ khớp số đếm.

Đây là các kiểm tra có giá trị cho đúng cài đặt đó. Hãy gọi đúng tên và đặt chúng ở phạm vi phù hợp; không ép lớp có kho dữ liệu khác cung cấp cùng trường hoặc cùng bất biến vật lý.

Một thay đổi từ mảng sang liên kết có thể cần viết lại nhóm nội bộ mà vẫn giữ nguyên nhóm hành vi. Điều đó là dấu hiệu ranh giới kiểm thử được tổ chức đúng.

### Bằng chứng hiệu năng và đồng thời

Nếu có yêu cầu hiệu năng, cần phương pháp đo thích hợp với dữ liệu, trạng thái JVM và điều kiện môi trường. Một assertion thời gian rất nhỏ trong ca chức năng thường không chứng minh được độ phức tạp hoặc khả năng chịu tải.

Nếu có yêu cầu đồng thời, phải kiểm tra đúng bảo đảm đồng thời. Một bài tuần tự vượt qua không chứng minh tính nguyên tử. Dùng mốc phối hợp rõ thay cho `sleep` để đoán lịch chạy, và kiểm tra kết quả sau khi các luồng được chờ hoàn tất.

Ví dụ, với `ConcurrentHashMap`, không yêu cầu các phép đọc tổng hợp trong lúc có ghi đồng thời phải là snapshot toàn map. Chính sách quan sát được mô tả riêng trong [ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html). Một model tuần tự có thể dùng ở trạng thái đã ổn định, còn kiểm tra trong thời gian cập nhật phải theo hợp đồng đồng thời tương ứng.

## Quy trình xây một bộ kiểm thử cho collection tùy chỉnh

1. Viết các lời hứa công khai bằng ngôn ngữ hành vi: loại cấu trúc, khả năng ghi, `null`, thứ tự, view và đồng thời.
2. Chọn interface cùng hồ sơ phù hợp; ghi tiền điều kiện của dữ liệu sinh.
3. Tạo factory cho các đối tượng độc lập theo nhu cầu kiểm thử.
4. Viết ca tường minh cho kết quả trả về, tác động và ngoại lệ ở biên.
5. Đưa nhiều triển khai hợp lệ vào cùng nhóm để tìm giả định vô tình về lớp.
6. Thêm chuỗi thao tác và so mô hình sau từng bước khi có model phù hợp.
7. Tạo một số biến thể sai có chủ đích để xem nhóm kiểm tra quan trọng có phát hiện không.
8. Tách các kiểm tra nội bộ, hiệu năng và đồng thời khỏi phần hợp đồng chung.
9. Khi có lỗi mới, giữ phản ví dụ ngắn thành ca hồi quy có tên rõ.

Nếu thay một lớp hợp lệ khác mà bài kiểm thử thất bại, đừng vội sửa lớp mới hoặc xóa kỳ vọng. Hãy tìm nguồn của kỳ vọng: nó là hợp đồng thật, khả năng đã chọn sai, hay chi tiết cài đặt bị mang vào ca chung?

## Bài tập tự kiểm tra

### 1. Hàm trả List nhưng test bắt ArrayList

Yêu cầu của hàm chỉ nói trả danh sách giữ thứ tự kết quả và cho đọc theo chỉ số. Test kiểm tra chính xác lớp runtime là `ArrayList`.

**Đáp án:** Kỳ vọng về lớp quá chặt so với yêu cầu đã nêu. Kiểm tra dãy phần tử và hành vi đọc; chỉ kiểm tra lớp nếu API cố ý có lời hứa cụ thể về lớp đó.

### 2. Vì sao đổi list thành set trước khi so có thể che lỗi?

Kết quả mong đợi là `[A,B,A]` nhưng kết quả thật là `[B,A]`.

**Đáp án:** Cả hai có thể trở thành cùng set `{A,B}`. Phép chuyển mất số lần xuất hiện và thứ tự, trong khi đó là phần của hợp đồng list.

### 3. List thêm được nhưng iterator.remove ném UOE có luôn sai không?

Đối tượng là một list có iterator snapshot chỉ đọc được công bố rõ.

**Đáp án:** Không. Khả năng sửa list và sửa qua iterator là hai điều khác. Bộ kiểm thử phải khai báo riêng và chọn đúng kỳ vọng cho từng đường.

### 4. addAll rỗng không lỗi có chứng minh thêm được không?

Collection không thay đổi sau `addAll(List.of())` và phương thức kết thúc bình thường.

**Đáp án:** Không. Không có phần tử nào cần được thêm. Dùng hợp đồng đã công bố, và kiểm tra thao tác có tác động thật nếu muốn xác minh khả năng thêm.

### 5. Lớp set có iterator chứa trùng nhưng new HashSet(actual) đúng

Sau khi chuẩn hóa kết quả sang `HashSet`, tập phần tử đúng như mong đợi.

**Đáp án:** Phép chuẩn hóa có thể đã tự loại trùng và che lỗi. Cần kiểm tra số lần duyệt, kích thước và tính duy nhất của candidate trước khi làm mất thông tin.

### 6. set thay đúng dữ liệu nhưng trả giá trị mới

Các kiểm tra nội dung đều vượt qua.

**Đáp án:** Vẫn vi phạm hợp đồng `List.set`, vốn trả giá trị cũ. Cần assertion riêng cho kết quả trả về; `WrongReturnList` trong chương trình minh họa đúng lỗi này.

### 7. Một seed chạy 200 bước thành công có chứng minh hoàn chỉnh không?

Ba triển khai vượt qua cùng một chuỗi thao tác với seed cố định.

**Đáp án:** Không. Đó là bằng chứng cho chuỗi đã thử. Vẫn cần ca biên, các khả năng chưa được sinh, model đúng ngữ nghĩa và kiểm tra những lời hứa quan trọng bằng các phép quan sát độc lập.

### 8. Kiểm tra nội bộ có phải luôn là việc không nên làm?

Một cấu trúc liên kết tự viết có thể bị sai con trỏ ngược nhưng các ca đơn giản chưa thấy lỗi.

**Đáp án:** Kiểm tra bất biến nội bộ có giá trị cho cài đặt đó. Tách nó khỏi bộ hợp đồng công khai dùng chung để có thể thay kho dữ liệu mà không làm lẫn lời hứa bên ngoài với cách tổ chức bên trong.


</details>

- [Quay lại đầu trang](#back-to-top)
