<a id="back-to-top"></a>

# Sao chép và độc lập

## Menu
- [Sao chép nông khác với sao chép sâu](#copy-independence-001)
- [Trường trỏ tới đối tượng bất biến có thể chia sẻ an toàn](#copy-independence-002)
- [Sao chép thành phần có thể thay đổi khi cần độc lập](#copy-independence-003)
- [Tham chiếu dùng chung và chu trình trong đồ thị đối tượng](#copy-independence-004)
- [Trường final và giới hạn của cách bổ sung sao chép sau super.clone()](#copy-independence-005)
- [Hàm khởi tạo sao chép và phương thức tạo bản sao có tên](#copy-independence-006)
- [Chọn phạm vi sao chép theo quyền sở hữu dữ liệu](#copy-independence-007)
- [Kiểm tra thay đổi bản sao có ảnh hưởng bản gốc hay không](#copy-independence-008)
- [Không xem serialize rồi deserialize là cách sao chép mặc định cho mọi lớp](#copy-independence-009)

## <a id="copy-independence-001">Sao chép nông khác với sao chép sâu</a>

<details>
<summary>Xem chi tiết</summary>

# Sao chép nông và sao chép sâu trong Java

## 1. Vì sao cần phân biệt?

Trong Java, biến có kiểu tham chiếu lưu **tham chiếu đến đối tượng**, không chứa nguyên đối tượng. Khi gán một biến tham chiếu cho biến khác, Java sao chép **giá trị tham chiếu**: cả hai biến cùng trỏ đến một đối tượng. Gán biến không tạo ra đối tượng mới.

```java
Person a = new Person(...);
Person b = a; // b và a cùng tham chiếu một Person
```

Dòng khai báo trên là đoạn minh họa; `Person` cần được định nghĩa riêng. Muốn có bản sao, chương trình phải chủ động tạo đối tượng khác, chẳng hạn qua constructor, phương thức sao chép, `clone()` hoặc cơ chế ánh xạ phù hợp.

Vấn đề quan trọng xuất hiện khi đối tượng chứa **trường tham chiếu đến các đối tượng khác**. Một đối tượng `Person` có trường `address` tham chiếu đến `Address`; `Address` lại có thể có danh sách dữ liệu khác. Tổng thể các đối tượng và liên kết đó gọi là *đồ thị đối tượng* (*object graph*).

- **Sao chép nông (shallow copy):** tạo đối tượng gốc mới, sao chép các giá trị trường; trường nguyên thủy được sao chép theo giá trị, còn trường tham chiếu vẫn tham chiếu đến đối tượng con cũ.
- **Sao chép sâu (deep copy):** tạo bản sao độc lập cho những đối tượng con **cần độc lập** trong phạm vi sao chép đã xác định; các liên kết trong bản sao trỏ đến bản sao tương ứng. Những giá trị bất biến như `String` thường có thể chia sẻ an toàn.
- **Độc lập** phải được hiểu theo phần trạng thái có thể thay đổi (*mutable state*): sửa trạng thái của bản sao không được vô tình sửa trạng thái của bản gốc và ngược lại, trong phạm vi đã cam kết.

Minh họa với một trường tham chiếu có thể thay đổi:

```text
Bản gốc:          Person P1 ──address──> Address A1
Bản sao nông:     Person P2 ──address──> Address A1  (dùng chung)
Bản sao sâu:      Person P3 ──address──> Address A2  (độc lập với A1)
```

`P1`, `P2` và `P3` là ba đối tượng `Person` khác nhau. Điểm khác biệt nằm ở đối tượng `Address` mà chúng giữ.

## 2. Ví dụ Java 17: quan sát sự khác biệt

Tạo tệp `CopyDemo.java`:

```java
public class CopyDemo {
    static class Address {
        String city;

        Address(String city) {
            this.city = city;
        }
    }

    static class Person {
        String name;
        Address address;

        Person(String name, Address address) {
            this.name = name;
            this.address = address;
        }

        Person shallowCopy() {
            return new Person(name, address);
        }

        Person deepCopy() {
            return new Person(name, new Address(address.city));
        }
    }

    public static void main(String[] args) {
        Person original = new Person("An", new Address("Hà Nội"));
        Person shallow = original.shallowCopy();
        Person deep = original.deepCopy();

        System.out.println("original == shallow: " + (original == shallow));
        System.out.println("same address in shallow: "
                + (original.address == shallow.address));
        System.out.println("same address in deep: "
                + (original.address == deep.address));

        shallow.address.city = "Đà Nẵng";
        System.out.println("original after shallow edit: " + original.address.city);

        deep.address.city = "Huế";
        System.out.println("original after deep edit: " + original.address.city);
        System.out.println("deep after edit: " + deep.address.city);
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 CopyDemo.java
java CopyDemo
```

Kết quả:

```text
original == shallow: false
same address in shallow: true
same address in deep: false
original after shallow edit: Đà Nẵng
original after deep edit: Đà Nẵng
deep after edit: Huế
```

Giải thích từng bước:

1. `shallowCopy()` tạo một `Person` mới nhưng truyền thẳng `address` cũ vào constructor; sửa `shallow.address.city` cũng sửa thành phố nhìn thấy từ `original`.
2. `deepCopy()` tạo cả `Person` và `Address` mới; sửa `deep.address.city` không tác động đến `original.address.city`.
3. `name` là `String` bất biến, nên dùng chung tham chiếu `name` không gây rò rỉ thay đổi trạng thái.
4. Ví dụ giả định `address` khác `null` và `Address` chỉ có một trường `String`. Khi cấu trúc dữ liệu phức tạp hơn, hàm sao chép phải xử lý thêm các trường và quan hệ tương ứng.

**Chú ý:** `original == shallow` trả về `false` chỉ xác nhận hai đối tượng `Person` khác nhau. Nó **không** chứng minh tất cả đối tượng con đã được sao chép.

## 3. So sánh theo các tiêu chí quan trọng

| Tiêu chí | Gán tham chiếu | Sao chép nông | Sao chép sâu |
| --- | --- | --- | --- |
| Đối tượng ở mức gốc | Dùng chung | Tạo mới | Tạo mới |
| Trường kiểu nguyên thủy | Cùng đối tượng gốc | Sao chép giá trị | Sao chép giá trị |
| Trường tham chiếu có thể thay đổi | Dùng chung | Thường dùng chung đối tượng con | Sao chép đối tượng con thuộc phạm vi cần độc lập |
| Thay đổi trường của đối tượng gốc | Nhìn thấy qua mọi biến cùng trỏ đến nó | Không làm đổi trường tương ứng của bản sao nếu trường được gán lại trên một đối tượng gốc riêng | Không làm đổi trường tương ứng của bản sao |
| Thay đổi *trạng thái bên trong* đối tượng con | Nhìn thấy ở cả hai | Có thể lan sang bản kia | Không lan sang bản kia trong phạm vi sao chép |
| Chi phí | Thường là thao tác gán rất nhỏ | Thường thấp hơn deep copy | Tùy số đối tượng/quan hệ thực sự phải tạo |
| Phù hợp | Cố ý chia sẻ đúng một đối tượng | Muốn đối tượng bao mới nhưng có thể chia sẻ phần bên trong | Cần cô lập trạng thái mutable |

Không thể nhận biết loại sao chép chỉ từ việc có dùng `new` hay không. `new Person(old.name, old.address)` chỉ sao chép nông đối với `address`; constructor copy cũng có thể là shallow hoặc deep tùy cách hiện thực.

## 4. Bộ sưu tập và mảng: tạo container mới chưa phải deep copy

`new ArrayList<>(source)` tạo danh sách mới và sao chép các **tham chiếu phần tử**, không tự tạo bản sao từng đối tượng. `HashMap` copy constructor tương tự: cấu trúc map mới nhưng khóa và giá trị thường vẫn được dùng chung. `array.clone()` hoặc `Arrays.copyOf(...)` sao chép mảng; nếu phần tử là tham chiếu, các đối tượng phần tử vẫn được chia sẻ.

Ví dụ chạy độc lập trong `CollectionCopyDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class CollectionCopyDemo {
    static class Task {
        String title;

        Task(String title) {
            this.title = title;
        }
    }

    public static void main(String[] args) {
        List<Task> original = new ArrayList<>();
        original.add(new Task("Học Java"));

        List<Task> shallow = new ArrayList<>(original);
        List<Task> unmodifiable = List.copyOf(original);

        shallow.get(0).title = "Học Java 17";
        System.out.println(original.get(0).title);
        System.out.println(unmodifiable.get(0).title);

        shallow.add(new Task("Viết test"));
        System.out.println(original.size());
        System.out.println(shallow.size());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CollectionCopyDemo.java
java CollectionCopyDemo
```

Kết quả:

```text
Học Java 17
Học Java 17
1
2
```

`List.copyOf(original)` tạo danh sách **không cho sửa cấu trúc qua danh sách kết quả**, nhưng không làm cho từng `Task` trở nên bất biến và không deep-copy `Task`. Nếu danh sách đầu vào đã là danh sách không thể sửa, Java có thể tái sử dụng đối tượng danh sách đó. `List.copyOf` cũng không chấp nhận phần tử `null`.

Phân biệt thêm:

- `Collections.unmodifiableList(list)` thường là *view*: không cho sửa qua view, nhưng thay đổi cấu trúc trên `list` gốc có thể xuất hiện trong view.
- `List.copyOf(list)` cho danh sách không thể sửa với tập phần tử tại thời điểm gọi; nó không bảo vệ trạng thái mutable của các phần tử.
- Sao chép sâu một `List<Task>` đòi hỏi tạo container phù hợp **và** tạo `Task` mới cho từng phần tử cần độc lập, đồng thời xem xét trường tham chiếu bên trong `Task`.
- Nếu toàn bộ phần tử là giá trị bất biến (ví dụ `String`), sao chép container có thể đủ để cô lập thay đổi cấu trúc; sao chép thêm từng `String` không đem lại lợi ích.

Ví dụ hướng xử lý danh sách đơn giản, khi `Task` chỉ có trường `title` như trên:

```java
List<Task> deepTasks = original.stream()
        .map(task -> new Task(task.title))
        .toList();
```

Đây là **đoạn mã minh họa nằm trong ngữ cảnh** của `CollectionCopyDemo`, không phải một tệp chạy riêng. `Stream.toList()` (Java 16+) trả về danh sách không thể sửa; các `Task` mới vẫn có thể thay đổi. Nếu cần thêm/xóa phần tử, bọc kết quả trong `new ArrayList<>(...)`.


## 5. Từ khái niệm đến hợp đồng sao chép

Shallow copy và deep copy là cách mô tả **mức độ chia sẻ tham chiếu**, không phải tên của một API cụ thể. Copy constructor, factory, `clone()` hay cơ chế ánh xạ chỉ đạt được mức độc lập mà phần triển khai thật sự bảo đảm. Một lớp có thể chia sẻ `String` bất biến nhưng phải sao chép mảng, collection hay phần tử mutable nếu hợp đồng yêu cầu tách trạng thái.

Với đồ thị có hai cạnh cùng trỏ một nút hoặc có chu trình, việc sao chép đệ quy từng nhánh có thể làm mất cấu trúc hoặc lặp vô hạn. Khi cần bảo toàn quan hệ, phải ghi nhớ mỗi nút gốc theo **identity** trong cùng một phiên sao chép. Với trường `final` trỏ tới dữ liệu mutable, `super.clone()` vẫn sao chép nông và không cho phép gán lại trường sau clone; constructor/factory có thể khởi tạo trường final bằng bản sao riêng.

Các bài tiếp theo lần lượt đi sâu vào chia sẻ immutable, defensive copy, đồ thị có chu trình, ràng buộc `final`, copy constructor/factory, ownership, kiểm thử hai chiều và giới hạn của serialization. Ở bài này, chỉ cần nắm nguyên lý và biết cách chọn phạm vi sao chép.

## 6. Chọn chiến lược trong thực tế

1. **Chỉ cần một tên tham chiếu khác cho cùng đối tượng:** dùng phép gán, hiểu rằng trạng thái được chia sẻ.
2. **Cần một đối tượng/container mới nhưng vẫn chủ động dùng chung các phần tử:** dùng shallow copy và ghi rõ hợp đồng chia sẻ.
3. **Cần chỉnh sửa bản sao mà không ảnh hưởng bản gốc:** xây dựng deep copy trong phạm vi mutable phù hợp; ưu tiên copy constructor/factory rõ ràng.
4. **Muốn tránh chi phí và lỗi sao chép:** cân nhắc immutable value object, defensive copy ở ranh giới API, hoặc snapshot chuyên dụng.
5. **Đồ thị có chu trình/alias:** thiết kế thuật toán duyệt có bảng identity, kiểm tra quan hệ liên kết và độ sâu dữ liệu.
6. **Đối tượng chứa resource hoặc tham gia xử lý đồng thời:** xác định vòng đời, quyền sở hữu và snapshot consistency trước khi chọn cách copy.

Khi thiết kế một API copy, hãy mô tả: bản sao có instance gốc mới không; các trường mutable nào được tách; những giá trị nào có chủ đích dùng chung; quan hệ alias/cycle có được giữ không; xử lý `null`, kế thừa và lỗi ra sao; và việc sao chép có nhất quán trước cập nhật đồng thời hay không.

## 7. Tự kiểm tra kiến thức

- Nếu một `List<Employee>` được tạo bằng `new ArrayList<>(oldList)`, thao tác `add()` vào danh sách mới có ảnh hưởng `oldList` không? Nếu sửa `newList.get(0).name`, tác động sẽ ra sao khi `Employee` mutable?
- Nếu `a.left == a.right`, bản sao sâu đúng phải thỏa quan hệ gì giữa `copy.left` và `copy.right`? Tại sao chỉ clone đệ quy từng trường lại có thể sai?
- Vì sao `String` dùng chung thường không phá tính độc lập, còn `ArrayList` dùng chung có thể phá?
- Một phương thức copy tạo đối tượng gốc mới nhưng giữ nguyên `Socket` và `List<MutableItem>`. Hãy giải thích phần nào chỉ được sao chép nông và cách mô tả hợp đồng an toàn.

**Ghi nhớ:** Nông/sâu không phụ thuộc tên phương thức hay sự xuất hiện của `new`. Điều quyết định là **đồ thị tham chiếu và trạng thái mutable nào được chia sẻ sau khi copy**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="copy-independence-002">Trường trỏ tới đối tượng bất biến có thể chia sẻ an toàn</a>

<details>
<summary>Xem chi tiết</summary>

# Trường trỏ tới đối tượng bất biến có thể chia sẻ an toàn

## 1. Ý nghĩa trong sao chép đối tượng

Khi tạo bản sao của một đối tượng Java, “độc lập” thường có nghĩa là **thay đổi trạng thái của bản gốc không làm thay đổi trạng thái quan sát được của bản sao, và ngược lại**. Điều này không bắt buộc mọi trường tham chiếu phải trỏ đến một đối tượng mới.

Nếu trường trỏ tới một đối tượng **bất biến (immutable)**, bản gốc và bản sao có thể giữ **cùng tham chiếu**: không bên nào có thể sửa trạng thái của đối tượng được chia sẻ. Sao chép tham chiếu trong trường hợp này vừa đúng về ngữ nghĩa vừa tránh tạo đối tượng không cần thiết.

Ví dụ: hai hồ sơ cùng trỏ đến một `String` biểu diễn tên hoặc cùng trỏ đến một `LocalDate` biểu diễn ngày tham gia. Nếu sửa riêng các phần dữ liệu có thể thay đổi của một hồ sơ, hồ sơ kia vẫn độc lập.

Cần phân biệt:

- **Sao chép tham chiếu:** `copy.name = original.name`; cả hai trường cùng trỏ đến một đối tượng.
- **Sao chép trạng thái:** tạo đối tượng mới chứa dữ liệu tương đương; thường cần khi dữ liệu có thể thay đổi.
- **Độc lập về trạng thái:** hai đối tượng chứa không vô tình chia sẻ trạng thái *có thể bị sửa*. Hai bản sao không nhất thiết phải khác nhau về định danh ở mọi đối tượng con.

## 2. Khi nào chia sẻ tham chiếu là an toàn?

Một đối tượng phù hợp để chia sẻ phải có trạng thái quan sát được không thay đổi sau khi được tạo, kể cả qua các đối tượng con mà nó tham chiếu. Không được có đường truy cập công khai nào làm thay đổi trạng thái đó.

Trong Java, ví dụ điển hình gồm:

- `String`: nội dung chuỗi không đổi; nối chuỗi tạo kết quả mới.
- `LocalDate`, `LocalDateTime`, `Instant`: các phương thức như `plusDays` trả về giá trị mới thay vì sửa đối tượng hiện có.
- `BigInteger` và `BigDecimal`: phép toán trả về đối tượng kết quả mới.
- `UUID`: giá trị định danh không đổi sau khi tạo.

**Quy tắc:** nếu một trường có kiểu và *giá trị thực tế* thực sự bất biến, copy constructor, factory sao chép hoặc phương thức `copy` có thể gán lại nguyên tham chiếu đó.

~~~java
this.name = source.name;                 // String bất biến: chia sẻ an toàn
this.joinedDate = source.joinedDate;     // LocalDate bất biến: chia sẻ an toàn
~~~

Hai dòng trên là **đoạn minh họa bên trong copy constructor**, không phải chương trình Java độc lập.

Sơ đồ tham chiếu:

~~~text
bản gốc.name ---------+
                      +----> String "An" (bất biến)
bản sao.name ---------+

bản gốc.skills ------------> List A (có thể thay đổi)
bản sao.skills ------------> List B (có thể thay đổi)
~~~

Trong sơ đồ, chia sẻ đối tượng `String` an toàn; hai danh sách `List` được tách riêng để thay đổi danh sách A không tác động danh sách B.

## 3. Ví dụ Java 17: chia sẻ immutable, sao chép mutable

Ví dụ sau dùng copy constructor. Hai trường bất biến (`String` và `LocalDate`) được dùng chung. Danh sách kỹ năng có thể thay đổi nên mỗi `Profile` sở hữu một `ArrayList` riêng. Constructor cũng tạo bản sao phòng vệ của danh sách đầu vào để người gọi không giữ quyền sửa ngầm trạng thái nội bộ.

Tạo tệp `SafeImmutableSharingDemo.java`:

~~~java
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SafeImmutableSharingDemo {
    static final class Profile {
        private final String name;
        private final LocalDate joinedDate;
        private final List<String> skills;

        Profile(String name, LocalDate joinedDate, List<String> skills) {
            this.name = Objects.requireNonNull(name);
            this.joinedDate = Objects.requireNonNull(joinedDate);
            this.skills = new ArrayList<>(Objects.requireNonNull(skills));
        }

        Profile(Profile source) {
            Objects.requireNonNull(source);
            this.name = source.name;             // Chia sẻ String bất biến
            this.joinedDate = source.joinedDate; // Chia sẻ LocalDate bất biến
            this.skills = new ArrayList<>(source.skills); // Tách List mutable
        }

        void addSkill(String skill) {
            skills.add(Objects.requireNonNull(skill));
        }

        List<String> getSkills() {
            return List.copyOf(skills); // Trả snapshot không sửa được
        }
    }

    public static void main(String[] args) {
        Profile original = new Profile(
                "An",
                LocalDate.of(2026, 10, 23),
                List.of("Java", "SQL")
        );
        Profile copy = new Profile(original);

        System.out.println("Cùng name: " + (original.name == copy.name));
        System.out.println("Cùng joinedDate: "
                + (original.joinedDate == copy.joinedDate));
        System.out.println("skills độc lập: "
                + (original.skills != copy.skills));

        original.addSkill("Spring");

        System.out.println("Original skills: " + original.getSkills());
        System.out.println("Copy skills: " + copy.getSkills());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac SafeImmutableSharingDemo.java
java SafeImmutableSharingDemo
~~~

Kết quả chuẩn:

~~~text
Cùng name: true
Cùng joinedDate: true
skills độc lập: true
Original skills: [Java, SQL, Spring]
Copy skills: [Java, SQL]
~~~

**Giải thích kết quả:**

1. `original.name == copy.name` là `true` vì copy constructor gán cùng tham chiếu `String`. Bất biến giúp việc dùng chung không gây rò rỉ thay đổi.
2. `original.joinedDate == copy.joinedDate` cũng là `true` vì cả hai cùng giữ tham chiếu `LocalDate`; gọi `plusDays` trên ngày này sẽ nhận một đối tượng ngày khác.
3. `original.skills != copy.skills` là `true` vì `new ArrayList<>(source.skills)` tạo **danh sách mới**.
4. Thêm `"Spring"` vào danh sách của bản gốc không làm đổi bản sao.
5. `getSkills()` trả về snapshot không cho sửa trực tiếp danh sách nội bộ. Vì các phần tử ở đây là `String` bất biến, không có trạng thái mutable bị chia sẻ qua các phần tử.

**Điểm quan trọng:** `private final List<String> skills` chỉ ngăn gán lại trường `skills` sau khi khởi tạo; bản thân `ArrayList` vẫn thay đổi được qua `addSkill`. Chính việc tạo danh sách riêng mới bảo đảm hai `Profile` không cùng sửa một danh sách.

## 4. Phản ví dụ: chia sẻ nhầm trạng thái mutable

Nếu copy constructor viết:

~~~java
this.skills = source.skills; // SAI nếu List này có thể thay đổi
~~~

thì hai `Profile` cùng trỏ đến **một danh sách**. Thêm/xóa phần tử qua một bản sẽ xuất hiện ở bản kia. Đây là *shallow copy* gây lỗi aliasing khi yêu cầu là hai hồ sơ độc lập.

Cách sửa phụ thuộc hợp đồng dữ liệu:

- Danh sách được phép thay đổi trong từng `Profile`: dùng `new ArrayList<>(source.skills)` như ví dụ chính.
- Danh sách phải bất biến từ lúc khởi tạo và các phần tử cũng bất biến: có thể dùng `List.copyOf(source.skills)` để lấy snapshot không thể sửa rồi chia sẻ snapshot đó giữa các bản sao.
- Danh sách chứa đối tượng mutable: sao chép mỗi phần tử mutable theo cách phù hợp; chỉ tạo `ArrayList` mới vẫn để hai danh sách trỏ đến **cùng các phần tử**.

Ví dụ về điểm cuối cùng: với `List<Address>` và `Address` có setter, biểu thức `new ArrayList<>(originalAddresses)` tạo danh sách mới nhưng *không* tạo `Address` mới. Nếu cần độc lập cả thông tin địa chỉ, hãy copy từng `Address`. Đây là mô tả cơ chế, không phải đoạn chương trình độc lập.

## 5. Không nhầm “không sửa được qua biến này” với “bất biến”

| Trường/giá trị | Có thể dùng chung khi copy? | Lý do/cách xử lý |
| --- | --- | --- |
| `String`, `LocalDate`, `Instant` | Có | Đối tượng bất biến. |
| `BigDecimal`, `BigInteger` | Có | Phép toán tạo giá trị mới. |
| `int[]`, `byte[]` | Không, nếu cần bản sao độc lập | Mảng mutable; dùng `clone()` hoặc `Arrays.copyOf(...)`. |
| `ArrayList<String>` mutable | Không | Tạo danh sách mới; phần tử `String` có thể dùng chung. |
| `List.copyOf(values)` với các phần tử immutable | Có, nếu không có đường sửa trạng thái ẩn | Danh sách snapshot không sửa được; giá trị lồng bên trong cũng bất biến. |
| `Collections.unmodifiableList(backing)` | Chưa chắc | Đây là view chỉ đọc; người giữ `backing` vẫn sửa được dữ liệu và view phản ánh thay đổi đó. |
| `List<MutableAddress>` | Không, nếu cần độc lập cả phần tử | Tạo danh sách và bản sao các phần tử mutable. |
| `Date`, `StringBuilder` | Không | Đây là các lớp mutable; cân nhắc copy hoặc đổi sang kiểu bất biến. |

Một số bẫy thường gặp:

### 5.1. `final` không biến đối tượng thành bất biến

~~~java
final int[] scores = {10, 20};
scores[0] = 99; // Hợp lệ: chỉ nội dung mảng thay đổi
~~~

`final` chỉ ngăn `scores` trỏ sang mảng khác. Ngay cả khi lớp chứa cũng khai báo `final`, mảng vẫn mutable.

### 5.2. `record` chỉ bảo đảm trường thành phần không gán lại được

~~~java
record Report(int[] totals) {}

int[] data = {1, 2};
Report report = new Report(data);
data[0] = 99; // report.totals()[0] bây giờ bằng 99
~~~

`record` tự sinh constructor/accessor nhưng không tự sao chép sâu mảng hoặc collection. Muốn một record bất biến về trạng thái, cần phòng vệ trong constructor và accessor đối với thành phần mutable.

### 5.3. “Unmodifiable” không đủ nếu phần tử mutable

`List.copyOf` tạo collection không thể thêm, xóa hay thay phần tử qua giao diện đó. Tuy nhiên nó **không tự sao chép sâu** các đối tượng phần tử. Nếu danh sách chứa một đối tượng `Address` có thể thay đổi và bên ngoài còn tham chiếu tới `Address`, thay đổi địa chỉ vẫn hiện ra khi đọc từ danh sách.

### 5.4. Giá trị bất biến được chia sẻ; thay trường là chuyện khác

Với trường có thể gán lại (`non-final`), phép `copy.name = "Tên mới"` chỉ thay **tham chiếu trong bản sao**, không sửa đối tượng `String` cũ và không đổi `original.name`. Cần phân biệt *gán lại tham chiếu* với *sửa trạng thái của đối tượng được tham chiếu*.

### 5.5. Chia sẻ giữa các luồng cần xét việc công bố đối tượng

Bất biến giúp loại bỏ tranh chấp về *thay đổi trạng thái của chính đối tượng immutable*. Nhưng khi truyền cả đối tượng chứa giữa các luồng, vẫn phải bảo đảm quá trình khởi tạo và công bố đối tượng đúng quy tắc Java Memory Model; các trường mutable khác vẫn cần chiến lược đồng bộ phù hợp. Không suy ra rằng toàn bộ object graph tự động thread-safe chỉ từ một trường `String` hay `LocalDate`.

## 6. Cách quyết định khi viết copy constructor

Với từng trường tham chiếu, kiểm tra theo trình tự:

1. **Có thể thay đổi được không?** Xét cả đối tượng cụ thể, các phần tử lồng bên trong và các alias còn tồn tại, không chỉ kiểu khai báo hay modifier `final`.
2. **Hợp đồng bản sao yêu cầu độc lập đến đâu?** Nếu cần độc lập về trạng thái, có thể chia sẻ bất biến. Nếu API yêu cầu mỗi thành phần phải có identity riêng, phải tuân thủ điều kiện identity đó.
3. **Nếu chắc chắn bất biến:** gán cùng tham chiếu; không cần tạo đối tượng tương đương một cách vô ích.
4. **Nếu mutable:** tạo bản sao thích hợp (mảng, collection hoặc từng đối tượng con). Với object graph có vòng tham chiếu hoặc nhiều alias, thiết kế copy sâu cẩn thận để giữ cấu trúc mong muốn.
5. **Chặn đường sửa ngầm:** copy phòng vệ ở constructor/getter khi cần; không để một alias bên ngoài tiếp tục sửa dữ liệu được coi là bất biến.

Ví dụ nhanh: một `Person` chứa `String fullName`, `LocalDate birthDate`, `int[] scores` và `List<Address> addresses`, trong đó `Address` mutable. Để sao chép độc lập trạng thái:

- Dùng lại tham chiếu `fullName` và `birthDate`.
- Dùng `scores.clone()` để tạo mảng mới.
- Tạo danh sách `addresses` mới **và** copy các `Address`; chỉ copy danh sách chưa đủ.

**Ghi nhớ:** *Độc lập trạng thái không đồng nghĩa phải tạo mới mọi đối tượng.* Hãy chia sẻ những đối tượng **thực sự bất biến**, và sao chép mọi phần trạng thái **có thể thay đổi** mà hợp đồng bản sao yêu cầu phải độc lập.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="copy-independence-003">Sao chép thành phần có thể thay đổi khi cần độc lập</a>

<details>
<summary>Xem chi tiết</summary>

# Sao chép thành phần có thể thay đổi khi cần độc lập

## 1. Vấn đề: sao chép đối tượng chưa chắc đã tạo ra trạng thái độc lập

Trong Java, biến kiểu tham chiếu giữ **tham chiếu tới đối tượng**, không giữ toàn bộ nội dung của đối tượng. Hai đối tượng bên ngoài có thể khác nhau nhưng vẫn trỏ tới **cùng một thành phần bên trong**. Khi thành phần ấy có thể thay đổi (mutable), việc sửa qua một bên có thể làm trạng thái quan sát được của bên còn lại thay đổi.

Ví dụ, khi tạo một hồ sơ từ `Address` do bên gọi cung cấp, việc chỉ gán `this.address = address` khiến hồ sơ và bên gọi dùng chung một `Address`. Nếu bên gọi thay đổi thành phố, hồ sơ cũng thấy thành phố mới. Điều tương tự xảy ra với mảng, `ArrayList`, `HashMap` hoặc đối tượng mutable lồng trong collection.

**Mục tiêu của sao chép phòng vệ (defensive copying):** ở những ranh giới mà hợp đồng yêu cầu độc lập, mỗi bên phải sở hữu trạng thái có thể thay đổi của mình; thay đổi hợp lệ từ một phía không được làm biến đổi trạng thái của phía kia ngoài ý muốn.

Cần phân biệt ba ý:

- **Khác tham chiếu:** `a != b` chỉ nói `a` và `b` không phải cùng một đối tượng bên ngoài.
- **Bằng giá trị:** `a.equals(b)` phụ thuộc hợp đồng `equals()`; hai bản sao độc lập vẫn có thể bằng giá trị.
- **Độc lập về trạng thái mutable:** không còn thành phần mutable bị chia sẻ mà một bên có thể sửa và gây ảnh hưởng ngoài ý muốn. Đây mới là điều cần kiểm tra khi bài toán đòi hỏi độc lập.

Không phải mọi thành phần đều phải sao chép. `String`, `Integer`, `BigDecimal` và phần lớn kiểu trong `java.time` (như `LocalDate`, `Instant`) là immutable, nên thường có thể chia sẻ an toàn. Trọng tâm là **thành phần thực sự mutable và có thể bị sửa qua tham chiếu được chia sẻ**.

## 2. Shallow copy, deep copy và mức sao chép thực sự cần

Giả sử `Order` có `List<OrderLine> lines` và mỗi `OrderLine` có thể thay đổi số lượng.

| Cách xử lý | Điều gì được tách? | Rủi ro còn lại |
| --- | --- | --- |
| `Order b = a;` | Không có bản sao nào; cùng đối tượng `Order` | Mọi trạng thái đều dùng chung |
| Sao chép `Order` nhưng giữ `lines` cũ | Chỉ đối tượng `Order` bên ngoài | Thao tác thêm/xóa/sửa phần tử có thể ảnh hưởng cả hai |
| `new ArrayList<>(a.lines)` | Tạo danh sách mới, tách thao tác thêm/xóa phần tử | Các `OrderLine` bên trong vẫn dùng chung |
| Tạo danh sách mới và sao chép từng `OrderLine` mutable | Tách cả cấu trúc danh sách và từng phần tử mutable | Cần tiếp tục xét thành phần mutable lồng sâu hơn |
| Sao chép toàn bộ đồ thị đối tượng | Tách mọi đối tượng mutable theo chính sách đã chọn | Tốn chi phí và phải xử lý alias, chu trình, tài nguyên đặc biệt |

**Shallow copy** (sao chép nông) thường chỉ tạo vỏ mới, còn tham chiếu bên trong vẫn trỏ tới đối tượng cũ. **Deep copy** (sao chép sâu) sao chép đệ quy các thành phần mutable cần tách; khái niệm này phải được xác định theo hợp đồng của mô hình dữ liệu, không đơn giản là “copy càng nhiều càng tốt”.

Ví dụ: nếu `OrderLine` chứa mã sản phẩm kiểu `String`, không cần tạo `String` mới. Nếu chứa `DiscountPolicy` immutable được dùng chung theo thiết kế, có thể giữ tham chiếu. Nếu chứa `Address` mutable mà đơn hàng cần tự quản lý, phải sao chép nó.

**Nguyên tắc quyết định:** hỏi “Ai sở hữu và có thể sửa đối tượng này?”; sau đó chỉ tách những phần mutable mà hợp đồng yêu cầu độc lập.

## 3. Sao chép ở cả đầu vào và đầu ra

Một lớp muốn kiểm soát trạng thái của mình thường cần bảo vệ **hai chiều**:

1. **Khi nhận dữ liệu** qua constructor, setter hoặc phương thức cập nhật: sao chép mutable input trước khi lưu, ngăn bên gọi sửa đối tượng sau khi đã truyền vào.
2. **Khi trả dữ liệu** qua getter hoặc phương thức xuất trạng thái: trả bản sao, hoặc trả giao diện immutable thực sự phù hợp, ngăn bên gọi sửa trạng thái nội bộ.

Chỉ sao chép khi khởi tạo nhưng getter lại trả thẳng trường nội bộ thì vẫn rò rỉ trạng thái. Ngược lại, chỉ sao chép trong getter nhưng constructor giữ tham chiếu đầu vào thì bên gọi vẫn có thể sửa từ bên ngoài.

**Chú ý:** `final` chỉ ngăn gán lại trường tham chiếu; `private final List<Item> items` vẫn có thể chứa danh sách hoặc phần tử mutable. `Collections.unmodifiableList(list)` tạo view cấm sửa qua view, nhưng vẫn phản ánh các thay đổi thực hiện qua `list` gốc. `List.copyOf(list)` cung cấp danh sách không thể sửa về mặt cấu trúc, nhưng **không sao chép sâu các phần tử**.

## 4. Ví dụ Java 17+: tách đối tượng mutable lồng trong danh sách

Ví dụ có hai lớp: `Address` thay đổi được thành phố; `Profile` cần giữ ảnh chụp trạng thái địa chỉ lúc khởi tạo. Vì vậy `Profile` sao chép `Address` ở cả trường đơn và từng phần tử của `List<Address>`. Khi trả dữ liệu, nó cũng tạo bản sao để không lộ trạng thái nội bộ.

Lưu vào tệp **DefensiveCopyDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DefensiveCopyDemo {
    static final class Address {
        private String city;

        Address(String city) {
            this.city = Objects.requireNonNull(city);
        }

        Address(Address source) {
            this(Objects.requireNonNull(source).city);
        }

        String city() {
            return city;
        }

        void setCity(String city) {
            this.city = Objects.requireNonNull(city);
        }
    }

    static final class Profile {
        private final String name;
        private final Address currentAddress;
        private final List<Address> priorAddresses;

        Profile(String name, Address currentAddress,
                List<Address> priorAddresses) {
            this.name = Objects.requireNonNull(name);
            this.currentAddress = new Address(
                    Objects.requireNonNull(currentAddress));

            Objects.requireNonNull(priorAddresses);
            List<Address> copies = new ArrayList<>(priorAddresses.size());
            for (Address address : priorAddresses) {
                copies.add(new Address(address));
            }
            this.priorAddresses = List.copyOf(copies);
        }

        Address currentAddress() {
            return new Address(currentAddress);
        }

        List<Address> priorAddresses() {
            List<Address> copies = new ArrayList<>(priorAddresses.size());
            for (Address address : priorAddresses) {
                copies.add(new Address(address));
            }
            return copies;
        }

        String describe() {
            List<String> cities = priorAddresses.stream()
                    .map(Address::city)
                    .toList();
            return name + " @ " + currentAddress.city()
                    + ", previous=" + cities;
        }
    }

    public static void main(String[] args) {
        Address current = new Address("Ha Noi");
        Address previous = new Address("Da Nang");
        List<Address> history = new ArrayList<>();
        history.add(previous);

        Profile profile = new Profile("An", current, history);

        // Thay đổi nguồn sau khi truyền vào constructor.
        current.setCity("Hue");
        previous.setCity("Can Tho");
        history.clear();
        System.out.println(profile.describe());

        // Thay đổi bản sao nhận từ getter.
        profile.currentAddress().setCity("Nha Trang");
        List<Address> exported = profile.priorAddresses();
        exported.get(0).setCity("Hai Phong");
        exported.clear();
        System.out.println(profile.describe());
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 DefensiveCopyDemo.java
java DefensiveCopyDemo
~~~

Đầu ra:

~~~text
An @ Ha Noi, previous=[Da Nang]
An @ Ha Noi, previous=[Da Nang]
~~~

**Phân tích từng lớp bảo vệ:**

- `new Address(currentAddress)` ngăn `current.setCity(...)` bên ngoài làm thay đổi `Profile`.
- Vòng lặp sao chép từng phần tử `Address` tách các đối tượng mutable mà `history` ban đầu tham chiếu tới. Chỉ `new ArrayList<>(history)` sẽ không đủ.
- `List.copyOf(copies)` giữ cấu trúc danh sách nội bộ không thể thêm/xóa; sự độc lập của phần tử đến từ những lệnh `new Address(address)` trước đó.
- Getter `currentAddress()` trả bản sao; getter `priorAddresses()` tạo danh sách mới **và** sao chép từng phần tử. Caller có thể sửa hoặc xóa dữ liệu nhận về mà không làm đổi hồ sơ.
- `name` dùng `String` immutable; không cần sao chép nó.

Ví dụ này chọn chính sách **ảnh chụp khi tạo**. Nếu nghiệp vụ muốn `Profile` luôn nhìn thấy địa chỉ mới của một đối tượng sống bên ngoài, việc cố tách sâu sẽ làm sai ý nghĩa nghiệp vụ. Chính sách sở hữu phải quyết định trước cách sao chép.

## 5. Các kiểu dữ liệu và lựa chọn sao chép thông dụng

### Mảng

Với mảng phần tử nguyên thủy như `int[]`, `Arrays.copyOf` sao chép các giá trị nên đủ để tách trạng thái của mảng một chiều.

Đoạn sau minh họa lớp có thể dùng trong chương trình Java có import `java.util.Arrays` và `java.util.Objects` (đây là đoạn minh họa, không có `main` độc lập):

~~~java
final class ScoreBoard {
    private final int[] scores;

    ScoreBoard(int[] scores) {
        int[] source = java.util.Objects.requireNonNull(scores);
        this.scores = java.util.Arrays.copyOf(source, source.length);
    }

    int[] scores() {
        return java.util.Arrays.copyOf(scores, scores.length);
    }
}
~~~

Với `Address[]`, `Arrays.copyOf` chỉ tạo **mảng mới chứa cùng tham chiếu Address**; cần sao chép từng phần tử nếu `Address` mutable. Với `int[][]`, `array.clone()` hoặc `Arrays.copyOf(array, ...)` chỉ tách mảng ngoài; muốn tách tất cả hàng, phải sao chép từng `int[]` bên trong.

### Collection và Map

- `new ArrayList<>(source)`, `new HashSet<>(source)` hoặc `new HashMap<>(source)`: sao chép cấu trúc chứa; phần tử, key, value là cùng tham chiếu nếu chúng là object.
- `List.copyOf(source)`, `Set.copyOf(source)`, `Map.copyOf(source)`: tạo collection không thể cập nhật qua API của chúng, **không đảm bảo phần tử/key/value mutable được tách**; các factory này cũng không chấp nhận phần tử hoặc entry null.
- Nếu giá trị của `Map<String, MutableValue>` cần độc lập, tạo map mới **và sao chép từng MutableValue**. Nếu key cũng mutable, cần một thiết kế key an toàn: thay đổi key đang nằm trong `HashMap` có thể phá hợp đồng `hashCode/equals` và làm việc tra cứu thất bại.
- Với nested collection, xét lần lượt từng tầng: sao chép danh sách ngoài không đồng nghĩa đã sao chép danh sách con hay các object trong nó.

### Đối tượng ngày giờ cũ và kiểu immutable

`java.util.Date` và `Calendar` mutable; nếu vẫn phải dùng, sao chép khi nhận/trả (ví dụ `new Date(original.getTime())`, với `Calendar` dùng `clone()` khi phù hợp). Các kiểu hiện đại trong `java.time` như `LocalDate`, `LocalDateTime`, `Instant` là immutable và thường là lựa chọn tốt hơn cho mô hình dữ liệu mới.

**Không có công thức duy nhất cho mọi kiểu:** luồng dữ liệu, buffer, socket, file handle, thread, lock và đối tượng chứa tài nguyên hệ thống không thể được “deep copy” một cách có nghĩa chỉ bằng duyệt trường; cần xác định cơ chế tạo mới, chia sẻ có kiểm soát hoặc cấm sao chép.

## 6. Copy constructor, factory, clone và serialization

**Copy constructor** (ví dụ `new Address(existing)`) hoặc **factory** (ví dụ `Address.copyOf(existing)`) diễn đạt rõ quy tắc sao chép. Chúng dễ đọc và cho phép kiểm soát các trường nào được giữ, sao chép, chuẩn hóa hoặc từ chối.

`Object.clone()` chỉ thực hiện sao chép theo trường ở mức cơ bản; với trường tham chiếu, bản sao vẫn chia sẻ object bên trong. `Cloneable` và override `clone()` không tự biến một lớp thành deep-copy. Nếu kế thừa có trạng thái mutable, việc đảm bảo sao chép đúng còn phức tạp hơn.

Không nên dùng serialization rồi deserialize như giải pháp sao chép chung: đòi hỏi đồ thị có thể tuần tự hóa, tốn tài nguyên, có ràng buộc bảo mật và có thể làm sai ý nghĩa các object/tài nguyên đặc biệt. Chỉ cân nhắc khi chính hợp đồng dữ liệu và định dạng serialization đã được thiết kế cho mục tiêu đó.

Một phương thức `copy()` nên ghi rõ: shallow hay deep theo những trường nào, có giữ quan hệ cùng tham chiếu trong đồ thị hay không, xử lý null, chu trình và tài nguyên như thế nào.

## 7. Những điểm khó khi đồ thị đối tượng phức tạp

### Giữ cấu trúc chia sẻ bên trong bản sao

Giả sử đối tượng gốc có `left` và `right` cùng trỏ đến **một** `Node`. Khi sao chép sâu, có hai chính sách khác nhau: (a) trong bản sao, `left` và `right` vẫn cùng trỏ tới **một Node mới**; hoặc (b) tạo hai `Node` khác nhau. Cả hai đều tách khỏi bản gốc, nhưng hành vi nội bộ của bản sao khác nhau.

Nếu cần bảo toàn cấu trúc alias, dùng ánh xạ `IdentityHashMap<Original, Copy>` theo **định danh tham chiếu** và kiểm tra node đã sao chép trước khi tạo mới. Cách này còn giúp xử lý đồ thị có chu trình `a.next = b; b.next = a` mà không đệ quy vô hạn. Không dùng `HashMap` dựa trên `equals()` cho tác vụ phân biệt các node gốc có giá trị bằng nhau nhưng khác định danh.

### Kế thừa, invariant và logic miền

Copy constructor phải giữ các invariant: kiểm tra đầu vào, tránh alias không mong muốn, và không gọi những phương thức có thể bị override trong lúc khởi tạo nếu điều đó phá tính đúng đắn. Không mặc định sao chép cả bộ nhớ cache, listener hay ID nghiệp vụ: một bản sao mới có thể cần ID riêng, cache tính lại hoặc listener riêng theo yêu cầu nghiệp vụ.

### Đồng thời và hiệu năng

Sao chép bảo vệ khỏi **sửa đổi thông qua tham chiếu chia sẻ sau thời điểm copy**, nhưng không tự bảo đảm ảnh chụp nhất quán khi thread khác đang sửa nguồn trong lúc sao chép. Với dữ liệu concurrent, cần cơ chế đồng bộ, snapshot do nguồn cung cấp hoặc cấu trúc immutable phù hợp.

Deep copy thường tốn thời gian và bộ nhớ theo số lượng thành phần được sao chép. Với dữ liệu lớn hoặc copy nhiều lần, có thể cân nhắc mô hình immutable, persistent data structure, thao tác cập nhật qua API kín hoặc thiết kế ownership rõ ràng. Tối ưu hóa chỉ sau khi hợp đồng độc lập được xác định.

## 8. Lỗi phổ biến và cách phát hiện

| Lỗi | Vì sao sai | Cách khắc phục |
| --- | --- | --- |
| `this.items = input;` khi cần độc lập | Giữ nguyên danh sách đầu vào | Sao chép cấu trúc, và cả phần tử mutable nếu cần |
| `new ArrayList<>(input)` rồi coi là deep copy | Phần tử object vẫn dùng chung | Copy từng phần tử mutable |
| Getter trả thẳng mảng/list/Date nội bộ | Người dùng sửa qua getter | Trả bản sao phòng vệ hoặc API immutable |
| Chỉ dựa vào `final` hoặc `record` | Component tham chiếu tới object mutable vẫn có thể bị sửa | Constructor chuẩn hóa/copy và accessor phòng vệ nếu cần |
| Dùng `Collections.unmodifiableList(input)` để “chụp” dữ liệu | View vẫn thấy thay đổi qua `input` | Tạo snapshot rồi quyết định có copy sâu phần tử không |
| Dùng `clone()` rồi giả định mọi trường độc lập | Clone mặc định là nông | Định nghĩa rõ và kiểm tra mọi trường mutable |
| Deep copy mù quáng mọi object | Tốn kém hoặc phá identity/tài nguyên nghiệp vụ | Copy theo hợp đồng ownership và invariant |

**Kiểm thử quan trọng hơn chỉ so sánh `equals()`:**

1. Khởi tạo bản sao; xác nhận dữ liệu ban đầu tương đương.
2. Sửa từng thành phần mutable của **nguồn**; kiểm tra bản sao giữ nguyên.
3. Sửa từng thành phần mutable được **xuất ra qua getter**; kiểm tra trạng thái nội bộ giữ nguyên.
4. Nếu lớp có chức năng copy đối tượng, sửa bản sao và đảm bảo bản gốc không đổi.
5. Với cấu trúc lồng nhau, thử cả thêm/xóa phần tử và sửa **nội dung phần tử**; với graph có alias/chu trình, kiểm tra quan hệ tham chiếu theo hợp đồng.
6. Thử các trường hợp null, danh sách rỗng, object trùng tham chiếu và concurrent access nếu lớp cam kết hỗ trợ.

Ví dụ, kiểm tra `copy != original` mới chỉ xác nhận khác vỏ; cần thử thay đổi sâu hơn mới phát hiện shared mutable state.

## 9. Quy trình áp dụng vào thiết kế

Khi thiết kế một lớp Java yêu cầu độc lập:

1. **Lập danh sách trường và phụ thuộc lồng nhau.** Xác định kiểu nào immutable, kiểu nào mutable, kiểu nào sở hữu tài nguyên.
2. **Xác định ranh giới ownership.** Dữ liệu có phải snapshot? Ai được phép sửa? Việc cập nhật nguồn có cần tự lan sang đối tượng này không?
3. **Chọn chiến lược theo từng trường.** Chia sẻ immutable; sao chép mutable khi cần độc lập; dùng API cập nhật có kiểm soát cho trạng thái phải chia sẻ.
4. **Bảo vệ mọi đường ra/vào.** Constructor, setter, getter, builder, copy constructor, factory và các phương thức trả collection đều có thể làm lộ tham chiếu.
5. **Kiểm thử bằng đột biến từ cả hai phía.** Tránh chỉ kiểm tra giá trị ban đầu hoặc định danh của đối tượng ngoài.
6. **Đánh giá hiệu năng và tính nhất quán.** Đảm bảo quy tắc sao chép không phá hợp đồng nghiệp vụ hay yêu cầu concurrency.

**Tự kiểm tra:** Nếu có `List<MutableAddress> addresses`, việc đặt `this.addresses = List.copyOf(addresses)` đã đủ để một đối tượng độc lập chưa?

**Đáp án:** Chưa, nếu bên gọi vẫn giữ các `MutableAddress` và có thể sửa chúng. `List.copyOf` chỉ kiểm soát cấu trúc danh sách; phải sao chép từng địa chỉ mutable, rồi lưu collection snapshot. Khi xuất danh sách, cũng phải tránh đưa các địa chỉ mutable nội bộ ra ngoài nếu không muốn caller sửa chúng.

**Ghi nhớ:** Mục tiêu không phải ép mọi object thành deep copy; mục tiêu là **không vô tình chia sẻ trạng thái có thể thay đổi ở nơi hợp đồng đòi hỏi sự độc lập**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="copy-independence-004">Tham chiếu dùng chung và chu trình trong đồ thị đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Tham chiếu dùng chung và chu trình trong đồ thị đối tượng (Java)

**Bối cảnh:** Sao chép và độc lập. Khi sao chép một đối tượng Java, cần nhìn toàn bộ mạng lưới đối tượng mà nó trỏ tới, vì bản sao của đối tượng gốc chưa chắc đã độc lập với bản gốc.

## 1. Đồ thị đối tượng là gì?

Một **đồ thị đối tượng (object graph)** có thể được hình dung như sau:

- **Đỉnh (node)**: một đối tượng trong bộ nhớ.
- **Cạnh (edge)**: một trường tham chiếu, phần tử mảng hay phần tử collection trỏ tới đối tượng khác.
- **Gốc (root)**: tham chiếu từ đó ta bắt đầu duyệt, ví dụ biến `customer` hoặc `head`.
- **Danh tính (identity)**: hai tham chiếu trỏ đến *chính cùng một đối tượng*, được kiểm tra bằng `==`; khác với `equals()`, vốn có thể kiểm tra giá trị theo quy ước của lớp.

Một đồ thị không bắt buộc là cây: nhiều cạnh có thể tới cùng một đỉnh, một đỉnh có thể quay lại chính nó hoặc trở về đỉnh đã thăm.

~~~text
A.next --------> B
^                 |
|                 |
+------ B.next ---+

A.shared ---+
            +----> S
B.shared ---+
~~~

Trong ví dụ, `A` và `B` tạo **chu trình** (`A → B → A`); đồng thời cả `A.shared` và `B.shared` cùng trỏ tới **một** đối tượng `S`. Hai đặc tính này tồn tại độc lập: có thể có chia sẻ mà không có chu trình, hoặc có chu trình mà không có nút chia sẻ phụ.

### Tham chiếu dùng chung (aliasing)

~~~java
StringBuilder shared = new StringBuilder("x");
StringBuilder left = shared;
StringBuilder right = shared;
left.append("y");
System.out.println(right); // xy
System.out.println(left == right); // true
~~~

`left` và `right` là hai biến tham chiếu nhưng chỉ có **một** `StringBuilder`. Thao tác thay đổi qua một đường truy cập sẽ hiện ra ở đường còn lại. Đối tượng vẫn chỉ được cấp phát một lần.

Điều này khác với hai đối tượng có nội dung bằng nhau nhưng danh tính khác nhau, chẳng hạn `new StringBuilder("x")` và `new StringBuilder("x")`. Không được dựa vào `toString()`, `equals()` hoặc nội dung giống nhau để kết luận hai đỉnh là một.

## 2. Vì sao sao chép một đối tượng có thể chưa độc lập?

**Sao chép nông (shallow copy)** tạo một đối tượng vỏ mới rồi sao chép giá trị các trường. Với trường primitive, giá trị được sao chép. Với trường tham chiếu, *địa chỉ tham chiếu logic* được sao chép: đối tượng con vẫn là đối tượng cũ.

Ví dụ, nếu tạo `A'` bằng cách sao chép nông `A`:

~~~text
A  --next--> B --next--> A
|            |
shared       shared
|            |
+----> S <---+

A' --next--> B     (vẫn là B cũ)
 |
 shared-----> S     (vẫn là S cũ)
~~~

`A' != A` đúng, nhưng `A'.next == A.next` và `A'.shared == A.shared` cũng đúng. Nếu đi `A'.next.next`, ta về **`A` gốc**, không về `A'`. Đổi dữ liệu trong `S` bằng `A'` có thể làm dữ liệu mà `A` thấy thay đổi.

Các thao tác quen thuộc cũng thường chỉ sao chép nông đối với phần tử tham chiếu:

- `Object.clone()` mặc định thực hiện bản sao theo trường; cần tự xử lý trường mutable nếu muốn độc lập sâu. Việc gọi `clone()` còn phụ thuộc vào hợp đồng `Cloneable` và cách lớp triển khai.
- `array.clone()` tạo mảng mới, nhưng với mảng đối tượng, từng phần tử vẫn là tham chiếu đến đối tượng cũ.
- `new ArrayList<>(oldList)` tạo cấu trúc danh sách mới, không nhân bản từng đối tượng phần tử.
- `List.copyOf(oldList)` tạo danh sách không cho sửa cấu trúc qua API đó, **không biến các phần tử mutable thành immutable**. Danh sách kết quả còn có thể dùng lại danh sách đầu vào nếu đủ điều kiện.

Ngược lại, **sao chép sâu (deep copy)** theo phạm vi đã xác định phải sao chép các đối tượng mutable cần sở hữu độc lập trong đồ thị. Chữ “sâu” phải đi kèm hợp đồng rõ ràng: những nút nào được sao chép, những nút bất biến nào được phép dùng chung, các tài nguyên bên ngoài được xử lý thế nào.

## 3. Hợp đồng đúng khi sao chép đồ thị

Giả sử `f(x)` là bản sao của đối tượng gốc `x`. Với mọi nút thuộc phạm vi cần sao chép, mục tiêu là:

1. **Tách danh tính:** `f(x) != x` cho những nút mutable cần độc lập.
2. **Giữ các cạnh:** nếu `x.next == y` thì `f(x).next == f(y)` (kể cả `null`).
3. **Giữ tham chiếu dùng chung:** nếu `x.left == x.right` thì `f(x).left == f(x).right`. Một nút gốc có nhiều đường trỏ tới chỉ tạo **một** nút sao chép.
4. **Giữ chu trình:** nếu `A.next.next == A` thì `A'.next.next == A'`.
5. **Không gộp nhầm nút:** hai nút gốc *khác danh tính*, dù `equals()` trả về `true`, vẫn phải có hai bản sao khác danh tính nếu cả hai thuộc phạm vi sao chép.

Nếu tạo hai bản sao mới mỗi lần đi tới `S`, ta làm mất tính chia sẻ (`A'.shared != B'.shared`). Nếu cứ đệ quy qua `A → B → A` mà không nhớ các nút đã xử lý, chương trình có thể đệ quy vô hạn và gây `StackOverflowError`.

Vì thế, **một lần sao chép đồ thị cần bảng ánh xạ theo danh tính**: `đối tượng gốc → đối tượng sao chép`.

## 4. Thuật toán: tạo vỏ, ghi nhớ, rồi nối các cạnh

Với một nút `source`:

1. Nếu `source == null`, trả về `null`.
2. Nếu bảng đã có `source`, trả về **bản sao đã tạo**, không tạo nút mới.
3. Tạo đối tượng sao chép ban đầu với các trường dữ liệu không tham chiếu.
4. **Đưa cặp `source → copy` vào bảng ngay lập tức**, trước khi duyệt bất kỳ cạnh nào.
5. Sao chép lần lượt các cạnh bằng cùng một bảng; trả về `copy`.

Bước 4 là trọng tâm. Khi đi từ `A` qua `B` rồi quay lại `A`, bảng đã có `A'` để nối cạnh quay về. Khi đi tới `S` lần thứ hai, bảng trả về cùng `S'` đã tạo.

### Vì sao dùng `IdentityHashMap`?

`IdentityHashMap<K,V>` so sánh khóa bằng `==` và dùng hàm băm theo danh tính, phù hợp với yêu cầu “một nút gốc chỉ ánh xạ tới một nút sao chép”.

`HashMap` thông thường dựa vào `equals()` và `hashCode()`; hai đối tượng khác nhau nhưng bằng nhau về giá trị có thể bị gộp thành một khóa. Tệ hơn, `equals()`/`hashCode()` do lập trình viên viết có thể duyệt tham chiếu vòng hoặc phụ thuộc vào các trường đang thay đổi. `IdentityHashMap` tránh nhầm lẫn giữa *bằng giá trị* và *cùng danh tính*.

## 5. Ví dụ Java 17 chạy được: sao chép nông, chia sẻ và chu trình

Lưu toàn bộ chương trình dưới tên **`GraphCopyDemo.java`**:

~~~java
import java.util.IdentityHashMap;

public class GraphCopyDemo {
    static final class Node {
        final String label;  // String bất biến nên có thể dùng chung
        int count;           // trạng thái có thể thay đổi
        Node next;           // cạnh có thể tạo chu trình
        Node shared;         // cạnh có thể có nhiều nguồn trỏ tới

        Node(String label, int count) {
            this.label = label;
            this.count = count;
        }
    }

    static Node shallowCopyRoot(Node original) {
        if (original == null) {
            return null;
        }
        Node result = new Node(original.label, original.count);
        result.next = original.next;
        result.shared = original.shared;
        return result;
    }

    static Node deepCopy(Node root) {
        return copy(root, new IdentityHashMap<>());
    }

    private static Node copy(
            Node original,
            IdentityHashMap<Node, Node> visited) {
        if (original == null) {
            return null;
        }

        Node existing = visited.get(original);
        if (existing != null) {
            return existing;
        }

        Node result = new Node(original.label, original.count);
        visited.put(original, result); // Ghi nhớ TRƯỚC khi duyệt cạnh

        result.next = copy(original.next, visited);
        result.shared = copy(original.shared, visited);
        return result;
    }

    public static void main(String[] args) {
        Node a = new Node("A", 1);
        Node b = new Node("B", 2);
        Node s = new Node("S", 7);

        a.next = b;
        b.next = a;       // chu trình A -> B -> A
        a.shared = s;
        b.shared = s;     // cùng một S

        Node shallow = shallowCopyRoot(a);
        Node deep = deepCopy(a);

        System.out.println("shallow root new: " + (shallow != a));
        System.out.println("shallow B is original: " + (shallow.next == b));
        System.out.println("shallow back edge to original A: "
                + (shallow.next.next == a));

        System.out.println("deep root new: " + (deep != a));
        System.out.println("deep B new: " + (deep.next != b));
        System.out.println("deep cycle closes on copy: "
                + (deep.next.next == deep));
        System.out.println("deep sharing preserved: "
                + (deep.shared == deep.next.shared));
        System.out.println("deep S separate: " + (deep.shared != s));

        deep.shared.count = 99;
        System.out.println("S counts original/copy: "
                + s.count + "/" + deep.shared.count);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac GraphCopyDemo.java
java GraphCopyDemo
~~~

Kết quả chuẩn:

~~~text
shallow root new: true
shallow B is original: true
shallow back edge to original A: true
deep root new: true
deep B new: true
deep cycle closes on copy: true
deep sharing preserved: true
deep S separate: true
S counts original/copy: 7/99
~~~

**Đọc kết quả:**

- `shallow` có danh tính mới, nhưng `shallow.next` vẫn là `b` cũ và đường vòng vẫn quay về `a` cũ.
- `deep.next.next == deep` xác nhận chu trình đã được dựng lại *bên trong* đồ thị mới.
- `deep.shared == deep.next.shared` xác nhận hai cạnh ở đồ thị mới vẫn chia sẻ cùng một nút; `deep.shared != s` xác nhận đó là nút mới.
- Thay `deep.shared.count` thành `99` không làm thay đổi `s.count` của bản gốc (`7`). Đây là bằng chứng độc lập đối với trạng thái mutable được sao chép trong ví dụ.

Chương trình chỉ dùng `String` (bất biến), `int` và liên kết tới `Node`. Với trường chứa `List`, `Map`, mảng, hoặc một lớp mutable khác, phải áp dụng tiếp chính sách sao chép cho các đối tượng tương ứng; không thể chỉ gán lại tham chiếu và gọi đó là sao chép sâu hoàn chỉnh.

## 6. Những trường hợp dễ bỏ sót

### Nhiều gốc nhưng chung một đồ thị

Nếu hai biến gốc `root1` và `root2` cùng trỏ tới `S`, gọi `deepCopy(root1)` và `deepCopy(root2)` **riêng biệt** sẽ tạo hai bảng ánh xạ và có thể tạo hai bản sao khác nhau của `S`. Nếu hợp đồng yêu cầu giữ chia sẻ *giữa các gốc*, phải sao chép cả tập gốc trong **cùng một phiên** với một `IdentityHashMap` duy nhất. Khởi tạo bảng trước vòng lặp các gốc rồi dùng lại ở mọi lời gọi `copy(root, visited)`.

### Tự tham chiếu và chu trình dài

`node.next = node` là chu trình độ dài một. Chu trình `A → B → C → A` có độ dài ba. Cùng thuật toán trên xử lý cả hai vì mỗi nút được ghi nhớ trước khi đi tới nút tiếp theo. Đừng chỉ kiểm tra chu trình hai nút.

### Hai đối tượng “bằng nhau” nhưng không phải một

Giả sử hai đối tượng mutable `p` và `q` có cùng dữ liệu và `p.equals(q) == true`, nhưng `p != q`. Sao chép đúng cấu trúc danh tính vẫn cần `copy(p) != copy(q)`. Đây là lý do cần bản đồ danh tính, nhất là khi sao chép đồ thị các entity có `equals()` dựa trên mã định danh.

### Dữ liệu bất biến và chia sẻ có chủ đích

Các giá trị bất biến như `String` trong ví dụ có thể dùng chung an toàn; sao chép chúng không làm tăng tính độc lập của phần trạng thái mutable. Đồ thị “deep copy” không nhất thiết phải nhân bản *mọi* đối tượng vật lý nếu hợp đồng cho phép chia sẻ nút bất biến. Tương tự, thiết kế **immutable graph** hoặc **copy-on-write** có thể chia sẻ cấu trúc để tiết kiệm bộ nhớ, với điều kiện các đường dùng chung không bị sửa ngoài kiểm soát.

### Collection, mảng và đồ thị hỗn hợp kiểu

Một `List<Node>` có thể chứa cùng `Node` hai lần, hoặc collection có thể tự tham chiếu qua kiểu phần tử phù hợp. Bản sao collection cần đăng ký container vào bảng trước khi duyệt phần tử nếu muốn xử lý chu trình. Đồ thị gồm nhiều lớp đối tượng cần cơ chế ánh xạ chung theo danh tính (chẳng hạn `IdentityHashMap<Object, Object>`), rồi xử lý từng kiểu theo hợp đồng. Chỉ gọi `new ArrayList<>(oldList)` hoặc `array.clone()` sẽ giữ nguyên tham chiếu phần tử.

### Trường `final`, constructor và bất biến của lớp

Thuật toán trên khởi tạo nút trước, rồi gán cạnh; cách này thuận tiện vì `next` và `shared` không phải `final`. Với mô hình có trường tham chiếu `final` hoặc constructor yêu cầu toàn bộ liên kết hợp lệ ngay khi tạo, không thể tùy tiện gán cạnh sau xây dựng. Có thể cần thiết kế lại API copy/builder hai giai đoạn, một mô hình immutable riêng hoặc cơ chế chuyên dụng bảo toàn invariant. Không dùng reflection để phá vỡ `final` chỉ nhằm ép thuật toán này hoạt động.

### Tài nguyên ngoài bộ nhớ và trạng thái đặc biệt

Không nên sao chép mù quáng `Thread`, socket, file handle, khóa đồng bộ, kết nối cơ sở dữ liệu hay tài nguyên hệ thống. Một entity có ID từ cơ sở dữ liệu có thể cần chính sách ID mới, giữ ID, hoặc không được nhân bản. `transient`, cache và quan hệ tới dịch vụ ngoài đồ thị cũng cần quyết định rõ. **Sao chép đối tượng** và **tạo bản ghi nghiệp vụ mới** là hai hợp đồng khác nhau.

### Chu trình và garbage collector

Chu trình không tự nó gây rò rỉ bộ nhớ trong Java: garbage collector dựa trên tính *có thể truy cập từ GC roots*, nên cả một nhóm đối tượng tham chiếu vòng vẫn có thể được thu gom nếu không còn đường truy cập từ các gốc đang sống. Rò rỉ giữ tham chiếu thường xảy ra khi cache, static field, listener hoặc collection còn giữ đường tới đồ thị không cần thiết.

## 7. Chi phí, giới hạn và cách kiểm thử

Với `V` nút khác danh tính và `E` cạnh được sao chép, thuật toán duyệt mỗi nút một lần và mỗi cạnh một lần: thời gian kỳ vọng **`O(V + E)`**, bộ nhớ bảng ánh xạ và các bản sao **`O(V)`** (không kể dữ liệu tùy kích thước bên trong mỗi nút). Đệ quy còn sử dụng stack tối đa theo độ sâu đường duyệt; một chuỗi cực dài có thể gây `StackOverflowError`. Nếu đồ thị rất sâu, thay đệ quy bằng stack/queue tường minh và vẫn phải lưu `visited` **trước** khi đưa cạnh vào hàng đợi.

Khi dữ liệu đang được nhiều thread sửa đồng thời, không có gì bảo đảm bản sao phản ánh một thời điểm nhất quán. Cần snapshot bất biến, đồng bộ hóa hoặc chính sách versioning phù hợp. `IdentityHashMap` tự thân không thread-safe.

Kiểm thử nên bao gồm:

1. `null`, một nút độc lập và các trường tham chiếu `null`.
2. Tự tham chiếu (`node.next == node`) và chu trình nhiều nút.
3. Hai cạnh tới cùng một nút (hình thoi) và hai nút có nội dung bằng nhau nhưng khác danh tính.
4. Nhiều root dùng chung nút nếu API hỗ trợ sao chép nhiều gốc.
5. Đổi trạng thái mutable của bản sao và kiểm tra bản gốc không đổi; đổi bản gốc và kiểm tra bản sao không đổi.
6. Bảo toàn đúng các cạnh với `==`; đồng thời bảo đảm mọi nút mutable cần độc lập đều khác nút gốc tương ứng.
7. Dữ liệu lớn hoặc rất sâu nếu dùng trong hệ thống thực tế.

Một số lỗi thường gặp và dấu hiệu nhận biết:

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Chỉ tạo root mới rồi gán tham chiếu con cũ | Bản sao và bản gốc còn sửa chung trạng thái | Sao chép tiếp các đối tượng mutable trong phạm vi |
| Gặp cạnh nào cũng tạo nút mới | Mất quan hệ dùng chung; với cycle có thể đệ quy vô hạn | Dùng bảng `original → copy` theo danh tính |
| Thêm vào bảng **sau** khi sao chép hết con | Chu trình quay lại nút chưa được ghi nhớ | Đăng ký bản sao ngay sau khi khởi tạo |
| Dùng `HashMap` khi `equals()` gộp các nút khác nhau | Sai topology, có thể mất nút | Dùng `IdentityHashMap` |
| Mỗi root có một bảng riêng dù cùng phiên sao chép | Mất chia sẻ giữa các root mới | Dùng chung một bảng xuyên suốt phiên |
| Sao chép container nhưng giữ nguyên các phần tử mutable | Chỉ sao chép nông lớp container | Sao chép phần tử theo cùng bảng ánh xạ |
| Nghĩ `List.copyOf` là deep copy | Container khó sửa nhưng phần tử vẫn mutable | Tách hợp đồng bất biến container và phần tử |
| Đệ quy trên đồ thị cực sâu | `StackOverflowError` | Duyệt lặp với stack/queue tường minh |

## 8. Tự kiểm tra

**Tình huống 1:** `A.left == A.right` trước khi sao chép. Sau deep copy đúng, `A'.left == A'.right` phải là `true`; đồng thời `A'.left != A.left` nếu đứa con đó mutable và nằm trong phạm vi cần sao chép.

**Tình huống 2:** `A.next = B; B.next = A`. Nếu bản sao của `A` là `A'` thì `A'.next.next` phải là `A'`, không phải `A`. Một bản sao root mới nhưng cạnh ngược tới nút cũ là bằng chứng sao chép nông.

**Tình huống 3:** Có hai nút `X`, `Y` khác danh tính nhưng `X.equals(Y)` trả `true`. Bảng ánh xạ sao chép phải xem đây là hai khóa riêng; dùng `IdentityHashMap` vì đích là giữ danh tính của đồ thị.

**Ghi nhớ:** Khi sao chép đồ thị đối tượng, cần giữ đúng **ai trỏ tới ai**, **những đường nào cùng trỏ một đối tượng** và **chu trình nào quay về điểm đã có**. Mẫu cốt lõi là **tạo bản sao → đăng ký theo identity → nối các cạnh bằng cùng một bảng**; sau đó kiểm tra độc lập của toàn bộ trạng thái mutable thuộc phạm vi sao chép.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="copy-independence-005">Trường final và giới hạn của cách bổ sung sao chép sau super.clone()</a>

<details>
<summary>Xem chi tiết</summary>

# Trường `final` và giới hạn của cách bổ sung sao chép sau `super.clone()`

## 1. Vấn đề cần giải quyết

Khi sao chép một đối tượng Java, ta thường muốn tạo **một instance mới** nhưng bảo đảm những dữ liệu có thể thay đổi bên trong không bị chia sẻ ngoài ý muốn với đối tượng gốc. Với mảng, `List`, `Map` hoặc những object mutable, chỉ sao chép tham chiếu là chưa đủ.

Một cách được nhắc đến trong mã Java kế thừa là:

1. Gọi `super.clone()` để tạo một instance có các trường ban đầu được sao chép từ đối tượng hiện tại.
2. Sau đó, trên object bản sao, **gán lại các trường tham chiếu mutable** thành bản sao riêng (ví dụ `result.items = new ArrayList<>(items)`).

Kỹ thuật này **có giới hạn quan trọng khi field được khai báo `final`**. Với:

```java
private final int[] values;
```

câu lệnh `result.values = values.clone();` bên trong `clone()` **không biên dịch được**, vì Java không cho phép gán lại trường `final` trong một phương thức thông thường, dù trường đó thuộc instance mới do `super.clone()` tạo ra.

Trong khi đó, `super.clone()` vẫn sao chép được **giá trị tham chiếu** của trường `final`. Hệ quả là object gốc và object clone có thể giữ **cùng một mảng mutable**:

```text
Trước clone:
original.values ─────> int[] {1, 2}

Sau super.clone() (bản sao nông):
original.values ──┐
                  ├────> cùng một int[] {1, 2}
copy.values ──────┘

Mong muốn nếu độc lập:
original.values ─────> int[] {1, 2}
copy.values ─────────> int[] {1, 2} (mảng khác)
```

Đây là trọng tâm của bài: **`final` làm tham chiếu không thể bị gán lại sau khởi tạo thông thường, nhưng không làm đối tượng được tham chiếu trở nên bất biến; `super.clone()` lại thực hiện sao chép nông, còn cách sửa bằng gán lại field sau clone bị chặn bởi `final`.**

## 2. Hiểu chính xác `final` trên trường dữ liệu

So sánh ba trường hợp:

| Khai báo | Điều `final` bảo vệ | Điều nó **không** bảo vệ |
| --- | --- | --- |
| `private final int count;` | Sau khi khởi tạo, không thể gán lại `count` từ phương thức thông thường | Không có object con mutable trong trường primitive này |
| `private final String name;` | Không thể gán lại tham chiếu `name` | `String` tự nó bất biến; đó là thuộc tính của lớp `String`, không phải phép màu của `final` |
| `private final int[] scores;` | Không thể gán lại tham chiếu `scores` trong method | Vẫn có thể gọi `scores[0] = 99`; phần tử mảng mutable |
| `private final List<Item> items;` | Không thể gán lại tham chiếu `items` | Có thể sửa collection nếu loại list cho phép; cũng có thể sửa từng `Item` mutable |

Ví dụ:

```java
// Mảnh code trong một class; không phải chương trình độc lập.
private final int[] scores = {5, 10};

void update() {
    scores[0] = 20;            // Hợp lệ: sửa phần tử mảng
    // scores = new int[] {5}; // LỖI BIÊN DỊCH: gán lại final field
}
```

Một field `final` có thể được khởi tạo trong khai báo hoặc trong constructor theo quy tắc Java về gán một lần; không được gán lại tùy tiện trong các method khác. Với tham chiếu mutable, điều đó là **cố định địa chỉ tham chiếu ở cấp ngôn ngữ**, không phải *deep immutability*.

Cần phân biệt `final class` với `final field`: lớp `final` không thể bị kế thừa, nhưng các object được lưu trong field của nó vẫn có thể mutable. Ngược lại, một trường `final` có thể xuất hiện trong lớp không `final`.

## 3. `Cloneable`, `Object.clone()` và bản sao nông

### 3.1. `Cloneable` không tự cung cấp một phương thức `clone()` public

`java.lang.Cloneable` là một **marker interface**. Nó không khai báo phương thức `clone()` để bên gọi tùy ý sử dụng. `Object.clone()` là phương thức `protected`, có thể được lớp con override để công bố API clone phù hợp.

Khi `Object.clone()` được gọi trên instance không hỗ trợ `Cloneable`, nó có thể ném `CloneNotSupportedException`. Các mảng Java là trường hợp đặc biệt: mảng hỗ trợ clone và `array.clone()` tạo mảng mới với phần tử được sao chép nông theo cấp mảng.

Một mẫu truyền thống:

```java
@Override
public Example clone() {
    try {
        return (Example) super.clone();
    } catch (CloneNotSupportedException e) {
        throw new AssertionError(e);
    }
}
```

Đây là mảnh code minh họa bên trong lớp `Example implements Cloneable`. Bắt exception thành `AssertionError` chỉ hợp lý nếu bạn **biết chắc** class thực hiện `Cloneable` và `super.clone()` có thể hoạt động theo hợp đồng; không sao chép cơ học sang mọi cây kế thừa.

### 3.2. `super.clone()` không chạy constructor của lớp đang được clone

`Object.clone()` tạo một đối tượng có các field ban đầu nhận các giá trị như đối tượng nguồn theo cơ chế shallow copy. Nó **không gọi constructor thông thường** của lớp đó để khởi tạo lại các trường hoặc kiểm tra lại toàn bộ bất biến.

Do vậy:

- Trường primitive nhận cùng giá trị primitive.
- Trường `String` hay object bất biến có thể giữ cùng tham chiếu; thường an toàn vì nội dung không thể sửa.
- Trường tham chiếu tới mảng, collection hoặc object mutable được sao chép dưới dạng **cùng một tham chiếu**.
- Trường `final` cũng được chép giá trị ban đầu, nhưng code Java trong `clone()` không được gán lại nó sau đó.

Điều này không đồng nghĩa Java nói “không thể clone class có `final`”: **vẫn có thể clone**, nhưng nếu field `final` trỏ tới dữ liệu mutable cần độc lập, kỹ thuật “`super.clone()` rồi gán lại field” không đáp ứng được.

## 4. Vì sao đoạn sửa shallow clone thông thường không áp dụng cho `final`?

Với field không `final`, lớp cũ thường viết:

```java
// Mảnh code trong một lớp implements Cloneable, có List<String> items.
@Override
public Example clone() {
    try {
        Example result = (Example) super.clone();
        result.items = new ArrayList<>(this.items);
        return result;
    } catch (CloneNotSupportedException e) {
        throw new AssertionError(e);
    }
}
```

Mảnh này giả định `items` là trường **không final** và các phần tử `String` bất biến. `new ArrayList<>(items)` tạo list mới, nhưng chỉ shallow copy các phần tử. Nếu `items` chứa `MutableItem`, hai list khác nhau vẫn chia sẻ chính các phần tử mutable.

Đổi khai báo thành:

```java
private final List<String> items;
```

thì dòng:

```java
result.items = new ArrayList<>(this.items);
```

trở thành **compile-time error**: không thể gán lại trường `final`. Không thể lách giới hạn chỉ bằng cách cast, gọi setter hay bỏ `private`; setter vẫn không có quyền gán lại field final đã khởi tạo.

**Không sửa bằng cách thao tác trực tiếp vào collection đã chia sẻ**:

```java
// KHÔNG đúng nếu original.items và result.items trỏ cùng một list!
result.items.clear();
result.items.addAll(this.items);
```

Thao tác như thế thay đổi **chính collection mà bản gốc cũng sở hữu**. Với `this.items` trỏ cùng đối tượng, `clear()` còn làm mất dữ liệu nguồn trước khi `addAll` chạy. Đây không phải cách tạo bản sao độc lập.

Tương tự, gọi `result.values[0] = ...` trên mảng `final` sau clone chỉ sửa phần tử của cùng mảng, không tách thành mảng mới.

## 5. Ví dụ Java 17 hoàn chỉnh: chia sẻ nguy hiểm và hai phương án an toàn

Ví dụ sau kiểm tra ba thiết kế:

1. `ShallowFinalClone` có `private final int[]` và override `clone()` bằng `super.clone()`. Mảng nguồn được copy vào constructor nhưng bản gốc và clone **vẫn chung mảng** sau clone.
2. `SafeCopy` giữ `private final int[]` và có **copy constructor**, tạo mảng mới khi xây object. Bản gốc và bản copy có thể thay đổi độc lập.
3. `DeepCloneMutableField` cố ý để field mảng **không final**, cho phép clone bằng `super.clone()` rồi gán lại một bản sao mảng. Nó minh họa điểm giới hạn cú pháp: kỹ thuật này hoạt động khi field không final nhưng phải đánh đổi sự cố định tham chiếu ở cấp field.

Lưu tệp **`FinalCloneDemo.java`**:

```java
import java.util.Arrays;
import java.util.Objects;

public class FinalCloneDemo {
    static final class ShallowFinalClone implements Cloneable {
        private final int[] values;

        ShallowFinalClone(int[] input) {
            values = Objects.requireNonNull(input, "input").clone();
        }

        void update(int index, int value) {
            values[index] = value;
        }

        int[] snapshot() {
            return values.clone();
        }

        boolean sharesArrayWith(ShallowFinalClone other) {
            return values == other.values;
        }

        @Override
        public ShallowFinalClone clone() {
            try {
                return (ShallowFinalClone) super.clone();
                // Không thể viết:
                // result.values = this.values.clone();
                // vì values là final.
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    static final class SafeCopy {
        private final int[] values;

        SafeCopy(int[] input) {
            values = Objects.requireNonNull(input, "input").clone();
        }

        SafeCopy(SafeCopy other) {
            Objects.requireNonNull(other, "other");
            values = other.values.clone();
        }

        void update(int index, int value) {
            values[index] = value;
        }

        int[] snapshot() {
            return values.clone();
        }

        boolean sharesArrayWith(SafeCopy other) {
            return values == other.values;
        }
    }

    static final class DeepCloneMutableField implements Cloneable {
        private int[] values; // Không final để có thể gán lại trong clone()

        DeepCloneMutableField(int[] input) {
            values = Objects.requireNonNull(input, "input").clone();
        }

        void update(int index, int value) {
            values[index] = value;
        }

        int[] snapshot() {
            return values.clone();
        }

        boolean sharesArrayWith(DeepCloneMutableField other) {
            return values == other.values;
        }

        @Override
        public DeepCloneMutableField clone() {
            try {
                DeepCloneMutableField copy =
                    (DeepCloneMutableField) super.clone();
                copy.values = values.clone();
                return copy;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    public static void main(String[] args) {
        int[] external = {1, 2};
        ShallowFinalClone original = new ShallowFinalClone(external);
        ShallowFinalClone clone = original.clone();

        external[0] = 99;
        System.out.println("Constructor detached input: "
            + Arrays.toString(original.snapshot()));
        System.out.println("Final clone shares array: "
            + original.sharesArrayWith(clone));

        clone.update(0, 7);
        System.out.println("Original after clone update: "
            + Arrays.toString(original.snapshot()));
        System.out.println("Clone after update: "
            + Arrays.toString(clone.snapshot()));

        SafeCopy safeOriginal = new SafeCopy(new int[] {1, 2});
        SafeCopy safeCopy = new SafeCopy(safeOriginal);
        safeCopy.update(0, 7);
        System.out.println("Copy constructor shares array: "
            + safeOriginal.sharesArrayWith(safeCopy));
        System.out.println("Safe original: "
            + Arrays.toString(safeOriginal.snapshot()));
        System.out.println("Safe copy: "
            + Arrays.toString(safeCopy.snapshot()));

        DeepCloneMutableField deepOriginal =
            new DeepCloneMutableField(new int[] {1, 2});
        DeepCloneMutableField deepCopy = deepOriginal.clone();
        deepCopy.update(0, 7);
        System.out.println("Repaired clone shares array: "
            + deepOriginal.sharesArrayWith(deepCopy));
        System.out.println("Repaired original: "
            + Arrays.toString(deepOriginal.snapshot()));
        System.out.println("Repaired copy: "
            + Arrays.toString(deepCopy.snapshot()));
    }
}
```

Biên dịch và chạy bằng **JDK 17 trở lên**, trong thư mục chứa tệp:

```bash
javac FinalCloneDemo.java
java FinalCloneDemo
```

Kết quả xác định:

```text
Constructor detached input: [1, 2]
Final clone shares array: true
Original after clone update: [7, 2]
Clone after update: [7, 2]
Copy constructor shares array: false
Safe original: [1, 2]
Safe copy: [7, 2]
Repaired clone shares array: false
Repaired original: [1, 2]
Repaired copy: [7, 2]
```

**Diễn giải:**

- Constructor của `ShallowFinalClone` đã `clone()` mảng input nên sửa `external[0]` không ảnh hưởng nội bộ. Nhưng đó chỉ là ranh giới **caller → original**. Khi gọi `original.clone()`, `super.clone()` lại tạo **hai object giữ chung một field tham chiếu**, và `final` không tách được object được tham chiếu.
- `clone.update(0, 7)` thay đổi mảng chung; kết quả snapshot của cả gốc và clone đều `[7, 2]`. Đây là lỗi độc lập của bản sao, không phải do getter làm lộ tham chiếu.
- `SafeCopy(SafeCopy other)` **tạo object bằng constructor**, nên có quyền gán giá trị mới cho field `final` ngay trong giai đoạn khởi tạo. Mảng được clone riêng, các lần sửa sau đó độc lập.
- `DeepCloneMutableField.clone()` dùng được `copy.values = values.clone()` vì `values` không final. Nó vẫn cần copy đúng mọi thành phần mutable khác của lớp nếu có; đây chỉ là ví dụ với một mảng primitive.
- Các bản sao đều là instance khác nhau. Mục tiêu “độc lập” đề cập **không chia sẻ nội dung mutable cần sở hữu riêng**, không phải chỉ kiểm tra `clone != original`.

## 6. Giải pháp ưu tiên: copy constructor hoặc factory tạo object mới

Khi class cần trường `final` và cả trạng thái được sao chép độc lập, **copy constructor** là cách trực tiếp:

```java
public final class Snapshot {
    private final int[] values;

    public Snapshot(int[] values) {
        this.values = values.clone();
    }

    public Snapshot(Snapshot source) {
        this.values = source.values.clone();
    }

    public int[] values() {
        return values.clone();
    }
}
```

Lưu ý: đoạn này là một class độc lập không có `main`; nếu biên dịch riêng, lưu `Snapshot.java`. Việc kiểm tra `null` phải được bổ sung nếu input được phép không hợp lệ và cần thông báo lỗi rõ ràng.

Cách làm này đáp ứng ba yêu cầu:

1. **Khởi tạo field final hợp lệ:** constructor gán tham chiếu một lần trong vòng đời tạo instance.
2. **Copy-in:** tránh bên gọi sửa mảng đầu vào rồi làm thay đổi snapshot.
3. **Copy-out:** getter không trả trực tiếp mảng nội bộ.

Có thể tạo factory đặt tên thể hiện rõ chính sách:

```java
public static Snapshot copyOf(Snapshot source) {
    return new Snapshot(source);
}
```

Đây là mảnh code thêm **bên trong** lớp `Snapshot` ở trên, không phải phương thức độc lập nằm ngoài class.

### 6.1. “Copy constructor” không mặc định là deep copy

Nếu field là `List<MutableItem>`, viết `new ArrayList<>(other.items)` chỉ sao chép **collection vỏ ngoài**; các `MutableItem` vẫn có thể được chia sẻ. Nếu mỗi phần tử cần sở hữu riêng, constructor phải copy đúng cấp dữ liệu mutable:

```java
// Mảnh code: giả sử MutableItem có copy constructor.
List<MutableItem> copiedItems = other.items.stream()
    .map(MutableItem::new)
    .toList();
```

`Stream.toList()` trong Java 17 tạo list không cho sửa cấu trúc, nhưng nếu `MutableItem` vẫn là mutable thì bản sao cho phép sửa từng phần tử; đó có thể đúng với một lớp sở hữu riêng phần tử, miễn các tham chiếu ấy không bị lộ trái phép. Nếu muốn trạng thái bất biến sâu, hãy dùng immutable value object hoặc snapshot record cho từng phần tử.

### 6.2. Khi nào không cần copy từng thành phần?

Không phải tham chiếu nào cũng phải clone:

- `String`, `LocalDate`, `Instant`, `BigInteger`, các value object bất biến được thiết kế đúng: chia sẻ tham chiếu thường an toàn.
- `List<String>` đã tạo bằng `List.copyOf`, với phần tử `String` bất biến và không có alias mutable ảnh hưởng, có thể chia sẻ như một immutable value.
- Immutable record với **mọi component bất biến sâu** có thể được tái sử dụng trong bản sao mà không phá sự độc lập quan sát được.

Ngược lại, chỉ gắn `final` vào `List<MutableItem>` hoặc dùng `List.copyOf` chưa tự biến `MutableItem` thành bất biến. Tập hợp có cấu trúc không sửa được nhưng chứa object mutable **không phải** immutable graph.

## 7. Có thể giữ `clone()` public mà không dùng `super.clone()` không?

Với một lớp `final` tự thiết kế, nếu API đã yêu cầu tên `clone()`, bạn có thể **tạo instance mới bằng constructor** trong override thay vì dùng `super.clone()`:

```java
public final class CloneByConstructor implements Cloneable {
    private final int[] values;

    public CloneByConstructor(int[] input) {
        this.values = input.clone();
    }

    @Override
    public CloneByConstructor clone() {
        return new CloneByConstructor(this.values);
    }
}
```

Đây là một class có thể biên dịch nếu lưu `CloneByConstructor.java`; không chứa `main`. Trong ví dụ này, constructor sẽ clone mảng đầu vào lần nữa, bảo đảm bản sao có mảng riêng. Implement `Cloneable` là lựa chọn để giữ một API quen thuộc, nhưng **không phải điều kiện cần** để phương thức tự viết ở đây dùng `new` tạo object mới; nó chỉ quan trọng khi sử dụng cơ chế `Object.clone()`.

Tuy vậy, **đừng áp dụng ngây thơ cho cây kế thừa mở**: constructor của class cha/con có thể có bất biến, loại runtime của object có thể là subclass, và một bản sao được tạo bằng constructor của lớp cha có thể làm mất trạng thái/lớp con. Thường nên thiết kế copy constructor hoặc factory rõ ràng cho từng kiểu, hoặc ưu tiên value object bất biến.

Nếu class là `final` như `CloneByConstructor`, không có subclass nên việc tạo đúng kiểu bản sao dễ kiểm soát hơn.

## 8. Vì sao bỏ `final` chỉ để phục vụ `super.clone()` chưa chắc tốt?

Một lựa chọn về mặt cú pháp là bỏ `final`:

```java
private int[] values; // Có thể gán lại trong clone(), nhưng không còn final.
```

Điều đó cho phép:

```java
Example copy = (Example) super.clone();
copy.values = values.clone();
```

Đây là mảnh code giả định lớp `Example implements Cloneable`, có field `values` không final và đang trong method `clone()` có xử lý checked exception phù hợp.

Tuy nhiên việc bỏ `final` đánh đổi:

- **Bất biến tham chiếu**: các method khác trong class có thể gán lại field, tăng không gian trạng thái cần kiểm soát.
- **Tín hiệu thiết kế**: người đọc không còn được bảo đảm rằng field tham chiếu không bị thay sau khi object được khởi tạo.
- **An toàn công bố và concurrency**: `final` có ngữ nghĩa bộ nhớ riêng theo Java Memory Model khi object được xây đúng và không làm lộ `this` trong constructor. Loại bỏ `final` có thể khiến lý luận về khởi tạo/công bố khó hơn; nó không tự động khiến code sai, nhưng không nên bỏ qua lợi ích này chỉ vì clone.
- **Bảo trì**: phải luôn nhớ cập nhật `clone()` khi thêm field mutable mới; quên copy một field là thêm một đường chia sẻ không mong muốn.

Không cần khẳng định mọi class nên bất biến. Nếu yêu cầu thật sự cần một class mutable được clone theo giao thức riêng, field không final và deep-copy có kiểm soát là thiết kế có thể chấp nhận. Nhưng nếu yêu cầu lõi là một snapshot không đổi hoặc một value object, constructor/factory và các field final thường dễ chứng minh đúng hơn.

## 9. Tác động tới `equals()`, `hashCode()` và collection băm

Khi sao chép một object, cần tách **độc lập trạng thái** khỏi **bình đẳng theo giá trị**:

- `original == copy` phải là `false` nếu tạo instance mới thật sự.
- `original.equals(copy)` có thể là `true` hoặc `false` tùy ngữ nghĩa equality của class. Với value object cùng thành phần, thường là `true`; với entity so theo danh tính riêng, tùy chiến lược ID.
- `original.hashCode() == copy.hashCode()` **bắt buộc** khi `equals` nói bằng nhau; hai object khác nhau vẫn có thể trùng hash hợp lệ.
- `toString()` có thể hiển thị cùng nội dung dù clone chia sẻ mutable object; chuỗi dễ đọc **không chứng minh** sự độc lập.

Nếu `equals/hashCode` dựa trên mảng mutable được chia sẻ bởi clone, thao tác thay đổi thông qua clone có thể làm hash của object gốc **thay đổi ngầm**. Nếu gốc đang là khóa `HashMap` hoặc phần tử `HashSet`, lookup có thể thất bại.

Ví dụ:

```java
// Mảnh code: giả sử key.hashCode() dựa trên nội dung một mảng mutable.
set.add(original);
CloneType copy = original.clone(); // super.clone() chia sẻ mảng final
copy.updateValue(0, 99);          // có thể khiến original.hashCode() đổi
boolean found = set.contains(original); // không được bảo đảm như kỳ vọng
```

Mã trên minh họa hệ quả thiết kế; không phải một chương trình Java độc lập. Nếu object được dùng làm khóa băm, những trường tham gia `equals/hashCode` cần ổn định trong thời gian nó nằm trong cấu trúc băm, hoặc phải chọn biểu diễn identity phù hợp. Defensive copying chỉ giải quyết alias từ bên ngoài; bản thân class vẫn có method sửa trạng thái thì đối tượng vẫn mutable.

## 10. Các cách xử lý khi có tập hợp hoặc đối tượng lồng nhau

### 10.1. `final List<String>`

Nếu class nhận list chứa `String` và muốn chụp snapshot cấu trúc:

```java
// Mảnh code bên trong constructor; input: List<String>.
this.tags = List.copyOf(input);
```

Với field `private final List<String> tags;`, lời gọi `List.copyOf` tạo/gắn một list không sửa được với các String bất biến. Khi tạo một bản sao nghiệp vụ sau này, có thể dùng lại `tags` nếu bảo đảm tính immutable của nội dung. **Không cần** `super.clone()` rồi gán lại `tags` để đạt độc lập giá trị.

### 10.2. `final List<MutableItem>`

Nếu `MutableItem` thay đổi được, `List.copyOf(items)` chỉ tạo/giữ list không cho sửa **cấu trúc**; các phần tử mutable vẫn chia sẻ. Cần copy hoặc chuyển thành immutable item trong constructor:

```java
// Mảnh code: ItemSnapshot là record chỉ chứa immutable fields.
this.items = input.stream()
    .map(x -> new ItemSnapshot(x.id(), x.name()))
    .toList();
```

Đoạn này giả định field có kiểu `List<ItemSnapshot>`, input có `id()` và `name()`, còn các component của `ItemSnapshot` bất biến. `Stream.toList()` trả list không cho sửa cấu trúc trong Java 17. Đây là snapshot các trường được chọn, không phải “clone tất cả” tự động.

### 10.3. `final int[][]`

`matrix.clone()` chỉ sao chép mảng ngoài; các hàng `int[]` vẫn chia sẻ. Nếu cần độc lập toàn bộ ma trận:

```java
static int[][] copyMatrix(int[][] source) {
    int[][] result = new int[source.length][];
    for (int i = 0; i < source.length; i++) {
        result[i] = source[i].clone();
    }
    return result;
}
```

Đây là hàm minh họa giả định `source` và mọi hàng khác `null`; chính sách `null` phải được thiết kế thêm nếu dữ liệu cho phép. Hãy gọi `copyMatrix` trong constructor để gán field final, và khi trả ma trận ra ngoài để không làm lộ alias.

### 10.4. Object graph có vòng tham chiếu

Với nhiều node tham chiếu qua lại, “deep copy” có thể cần bảng ánh xạ từ node cũ sang node mới để giữ cấu trúc và xử lý vòng; copy constructor đơn giản trên từng node có thể lặp vô hạn hoặc vô tình tạo nhiều bản sao của cùng node. Nếu yêu cầu nghiệp vụ chỉ cần vài giá trị, thiết kế immutable DTO/snapshot thường ít lỗi hơn việc clone toàn bộ graph.

## 11. Đặt câu hỏi đúng trước khi chọn giải pháp

Một bảng lựa chọn thực tế:

| Nhu cầu | Giải pháp phù hợp | Ghi chú |
| --- | --- | --- |
| Snapshot/value object có `final` và dữ liệu cần độc lập | Copy constructor hoặc factory + defensive copy | Dễ bảo toàn final field và bất biến |
| Chỉ có field primitive và immutable references | Chia sẻ các giá trị immutable có thể chấp nhận | Không cần deep-copy vô ích |
| Bắt buộc tương thích clone cũ, field mutable không final | `super.clone()` rồi thay từng reference mutable bằng bản sao | Cập nhật mỗi khi thêm trường cần copy |
| Bắt buộc API tên `clone()` nhưng class `final` có mutable final field | Override `clone()` để gọi constructor/factory mới | Không bắt buộc dùng `super.clone()` |
| Đang có `final List<MutableItem>` cần bất biến sâu | Chuyển từng item thành immutable value snapshot | `List.copyOf` đơn lẻ chưa đủ |
| Class/graph có kế thừa hoặc quan hệ phức tạp | Thiết kế giao thức copy chuyên biệt hoặc snapshot theo miền | Cẩn thận mất subtype và vòng tham chiếu |

**Không có một “deep clone” tự động đúng cho mọi object Java**: tài nguyên file/socket, lock, entity ID, kết nối database và object graph có thể có quy tắc sở hữu riêng. Clone một connection hoặc copy cùng business ID có thể không mang ý nghĩa hợp lệ. Cần quyết định rõ đối tượng mới được phép chia sẻ gì và phải độc lập với cái gì.

## 12. Các lỗi thường gặp

| Sai lầm | Vì sao sai? | Hướng khắc phục |
| --- | --- | --- |
| Nghĩ `final int[]` làm mảng bất biến | Chỉ cố định tham chiếu, phần tử vẫn sửa được | Defensive copy và API không lộ alias |
| Nghĩ `super.clone()` tự deep copy | Chỉ sao chép nông các field | Thiết kế copy từng thành phần mutable |
| Viết `copy.finalField = ...` sau `super.clone()` | Không biên dịch: không được gán lại final field | Copy constructor/factory tạo instance mới |
| Gọi `copy.items.clear()/addAll` để “sửa” alias | Hai object có thể chung list, sửa clone cũng sửa gốc | Tạo collection mới trong constructor hoặc cho field không final nếu đúng thiết kế |
| Dùng `new ArrayList<>(source)` và cho rằng phần tử mutable đã độc lập | Chỉ copy vỏ collection | Copy phần tử mutable hoặc snapshot immutable |
| Chỉ copy input ở constructor rồi `super.clone()` | Caller đã tách nhưng clone mới vẫn chia sẻ nội bộ với gốc | Áp dụng riêng chiến lược copy khi tạo clone |
| Bỏ `final` mà không xét thiết kế | Mất cam kết cố định tham chiếu và thay đổi cách lý luận concurrency | Ưu tiên constructor khi muốn field final |
| Để clone kế thừa từ class cha bỏ sót field mới của subclass | Bản sao có thể chia sẻ trạng thái mutable hoặc mất bất biến | Thiết kế copy cho cả lớp cha/con và kiểm thử |
| Dùng `clone()` mà không có chiến lược xử lý lỗi/`Cloneable` | `Object.clone()` có thể ném `CloneNotSupportedException` | Dùng API copy rõ ràng hoặc triển khai Cloneable đúng |
| Gọi clone rồi chỉ kiểm tra `original != copy` | Hai object khác tham chiếu vẫn có thể chung mảng | Thử sửa bản sao và xác nhận gốc không đổi |

## 13. Checklist kiểm thử

Đối với lớp có mutable final field và yêu cầu copy độc lập:

1. **Kiểm tra tạo instance khác:** `original != copy`.
2. **Kiểm tra nội dung khởi đầu:** các giá trị cần tương đương sau khi copy.
3. **Kiểm tra alias:** sửa qua phương thức của bản copy, quan sát xem bản gốc có đổi không.
4. **Kiểm tra input alias:** sửa mảng/list đầu vào sau constructor; object đã tạo có đổi không?
5. **Kiểm tra output alias:** nếu getter trả array/list, sửa giá trị nhận về có ảnh hưởng nội bộ không?
6. **Kiểm tra nested mutable:** mảng nhiều chiều hoặc phần tử mutable có được copy đến đúng độ sâu không?
7. **Kiểm tra equals/hashCode:** bản sao độc lập không được làm hash của object gốc thay đổi ngoài ý muốn; quan hệ equality phù hợp domain.
8. **Kiểm tra null, collection order và invariant:** chiến lược copy không vô ý đổi điều kiện hợp lệ, thứ tự hay tính duy nhất.
9. **Kiểm tra khai báo `final` và compile:** những giải pháp dùng constructor gán được field trong thời điểm hợp lệ; các giải pháp gán lại trong `clone()` sẽ bị Java từ chối.
10. **Kiểm tra các subtype:** nếu có inheritance, clone có giữ đúng runtime type và các invariant của từng lớp không?

Một kiểm thử chỉ so mảng ban đầu bằng nhau không đủ để chứng minh độc lập. Cần chủ động **thay đổi bản sao sau khi tạo** rồi đối chiếu bản gốc; đây là cách nhanh nhất để phát hiện shared mutable state.

## 14. Tự kiểm tra

**Câu 1.** Một lớp có `private final int[] numbers`. `super.clone()` có sao chép các phần tử sang mảng mới không?

**Đáp án:** Không. Bản sao shallow giữ cùng tham chiếu mảng. Field `final` vẫn được sao chép giá trị tham chiếu ban đầu, chứ không tự tạo mảng mới.

**Câu 2.** Vì sao `copy.numbers = numbers.clone()` sau `super.clone()` không biên dịch nếu `numbers` là `final`?

**Đáp án:** Vì đó là phép gán lại final field trong phương thức `clone()`, không phải thao tác khởi tạo field hợp lệ ở constructor hay initializer. Object được sinh bởi `super.clone()` không làm mất quy tắc `final` của ngôn ngữ.

**Câu 3.** Có thể viết `numbers[0] = 3` dù `numbers` là final không? Điều đó giải quyết deep copy không?

**Đáp án:** Có thể sửa phần tử nếu mảng mutable; nhưng không tạo mảng mới, nên không giải quyết vấn đề chia sẻ alias. Nếu clone đang chung mảng với gốc, cả hai cùng quan sát thay đổi.

**Câu 4.** Cách nào giữ được `private final int[] numbers` nhưng tạo hai instance sở hữu mảng riêng?

**Đáp án:** Copy constructor hoặc factory gọi constructor, và clone mảng khi gán field lúc tạo instance. Nếu cần API `clone()`, có thể tự tạo một instance bằng constructor trong phương thức đó đối với lớp có thiết kế phù hợp.

**Câu 5.** `List.copyOf(List<MutableItem>)` có tạo immutable graph không?

**Đáp án:** Không. Nó không cho sửa cấu trúc list nhưng vẫn tham chiếu các `MutableItem`; để có bất biến sâu cần immutable item hoặc copy từng item đúng mức.

**Câu 6.** Sau khi `super.clone()`, hai object có `original != copy` và `original.toString().equals(copy.toString())`. Đã chứng minh độc lập chưa?

**Đáp án:** Chưa. Cả hai chỉ xác nhận instance khác và biểu diễn chuỗi tương tự. Cần thử thay đổi dữ liệu mutable thông qua một bên và quan sát bên kia.

**Câu 7.** Có nhất thiết phải bỏ `final` để có bản sao độc lập không?

**Đáp án:** Không. Chỉ việc **gán lại sau `super.clone()`** bị chặn. Tạo object mới qua constructor/factory cho phép khởi tạo field final bằng tham chiếu đã được sao chép, mà vẫn giữ thiết kế cố định tham chiếu.

---

**Ghi nhớ:** `final` cố định **tham chiếu của field**, không làm bất biến dữ liệu được tham chiếu. `super.clone()` tạo **bản sao nông**, không tự sao chép mảng/collection; cách “`super.clone()` rồi gán lại field” không áp dụng cho trường `final`. Khi cần độc lập thực sự mà vẫn giữ field final, ưu tiên **copy constructor/factory với defensive copying**, và kiểm thử bằng cách sửa bản sao rồi xác nhận bản gốc không thay đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="copy-independence-006">Hàm khởi tạo sao chép và phương thức tạo bản sao có tên</a>

<details>
<summary>Xem chi tiết</summary>

# Hàm khởi tạo sao chép và phương thức tạo bản sao có tên trong Java

## 1. Bài toán: một đối tượng mới có độc lập với đối tượng gốc không?

Trong Java, biến có kiểu tham chiếu chứa **tham chiếu đến đối tượng**, không chứa toàn bộ đối tượng. Vì vậy, gán `Profile b = a;` chỉ tạo thêm một tên cùng trỏ tới đối tượng `a`. Thay đổi trạng thái qua `b` cũng chính là thay đổi trạng thái thấy được qua `a`.

Khi cần hai đối tượng có dữ liệu ban đầu tương đương nhưng có thể phát triển độc lập, ta **tạo một đối tượng mới** và chủ động xác định cách sao chép từng thành phần. Hai cách phổ biến trong Java là:

- **Copy constructor (hàm khởi tạo sao chép)**: `new Profile(existing)`. Constructor nhận một đối tượng cùng loại và khởi tạo trạng thái của đối tượng mới từ nó.
- **Named copy method / named copy factory (phương thức tạo bản sao có tên)**: `Profile.copyOf(existing)`, `Profile.from(existing)` hoặc `existing.copy()`. Tên phương thức biểu đạt ý định, còn phần triển khai quyết định có tạo đối tượng mới và sao chép sâu đến đâu.

Java **không tự tạo copy constructor** cho class. Khác một số ngôn ngữ, truyền đối tượng vào constructor cùng loại không tự động có nghĩa “copy”: tác giả class phải định nghĩa hành vi đó.

Ví dụ ngắn về khác biệt giữa **gán tham chiếu** và **tạo bản sao**:

```java
Profile alias = original;                   // Cùng một đối tượng.
Profile copy = new Profile(original);       // Đối tượng mới theo hợp đồng copy.
boolean sameObject = alias == original;     // true
boolean copiedObject = copy == original;    // false nếu constructor tạo object mới.
```

Đoạn trên dùng class `Profile` được định nghĩa trong chương trình đầy đủ ở phần 4.

## 2. Copy constructor hoạt động như thế nào?

Mẫu cơ bản:

```java
public final class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Point(Point source) {
        this(source.x, source.y);
    }
}
```

- `Point(Point source)` là **constructor**, không có kiểu trả về.
- `new Point(source)` luôn cấp phát một `Point` mới nếu khởi tạo thành công.
- Dòng `this(source.x, source.y)` dùng lại constructor chính, giảm nguy cơ bỏ sót trường hoặc áp dụng quy tắc kiểm tra khác nhau.
- `int` được sao chép theo giá trị. Với trường là tham chiếu, điều cần quyết định là **sao chép tham chiếu, sao chép container, hay sao chép luôn đối tượng bên trong**.

Một copy constructor tốt nên công khai hợp đồng: sau khi sao chép, những thay đổi nào ở bản gốc hoặc bản sao có thể ảnh hưởng lẫn nhau? Hợp đồng ấy quan trọng hơn việc “có constructor nhận cùng kiểu”.

## 3. Sao chép nông, sâu và chia sẻ có chủ ý

Giả sử `Person` chứa một `Address` có thể đổi `city` và một `List<String>` có thể thêm phần tử.

| Cách làm | Thành phần được tạo mới | Hệ quả |
| --- | --- | --- |
| `this.address = source.address;` | Không tạo `Address` mới | Hai `Person` cùng thấy thay đổi địa chỉ: **shallow copy** đối với trường này |
| `this.address = new Address(source.address);` | Tạo `Address` mới | Thay đổi `city` ở một bên không tác động bên kia |
| `this.tags = source.tags;` | Không tạo list mới | Thêm/xóa phần tử ở một bên ảnh hưởng list bên kia |
| `this.tags = new ArrayList<>(source.tags);` | Tạo list mới, giữ tham chiếu đến phần tử | Hai list độc lập về thêm/xóa; **các phần tử** không tự được deep-copy |

**Shallow copy** tạo đối tượng bao ngoài mới nhưng vẫn chia sẻ một hay nhiều tham chiếu bên trong. **Deep copy** sao chép tiếp những đối tượng mutable mà hợp đồng yêu cầu phải độc lập. “Deep” có phạm vi theo đồ thị đối tượng: không có quy tắc rằng mọi reference phải luôn tạo mới. `String`, enum và kiểu giá trị bất biến thường an toàn khi dùng chung.

Ví dụ, `new ArrayList<>(source.tags)` đủ để tách hai danh sách `String` vì các chuỗi là bất biến. Nếu đổi sang `List<MutableTask>`, hai list vẫn có thể chứa **cùng các `MutableTask`**. Khi đó cần tạo từng `MutableTask` mới nếu đòi hỏi thay đổi task độc lập.

**Tính độc lập cần xét cả hai chiều**: thay đổi bản gốc không làm hỏng bản sao, và thay đổi bản sao cũng không làm hỏng bản gốc. Ngoài ra, constructor nhận dữ liệu từ bên ngoài có thể cần **defensive copy** ngay lúc đầu để tránh caller giữ và sửa tham chiếu vào nội bộ.

## 4. Ví dụ hoàn chỉnh Java 17+: ba cách sao chép, một hợp đồng độc lập

Lưu thành tệp **`CopyPatternsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CopyPatternsDemo {
    static final class Address {
        private String city;

        Address(String city) {
            this.city = Objects.requireNonNull(city, "city");
        }

        Address(Address source) {
            this(Objects.requireNonNull(source, "source").city);
        }

        String city() {
            return city;
        }

        void setCity(String city) {
            this.city = Objects.requireNonNull(city, "city");
        }
    }

    static final class Profile {
        private final String name;
        private final Address address;       // Reference final, Address vẫn mutable.
        private final List<String> tags;     // Reference final, List vẫn mutable.

        Profile(String name, Address address, List<String> tags) {
            this.name = Objects.requireNonNull(name, "name");
            this.address = new Address(
                    Objects.requireNonNull(address, "address"));
            this.tags = new ArrayList<>(
                    Objects.requireNonNull(tags, "tags"));
        }

        // (1) Copy constructor: tái sử dụng constructor chính.
        Profile(Profile source) {
            this(Objects.requireNonNull(source, "source").name,
                    source.address,
                    source.tags);
        }

        // (2) Named static factory: diễn đạt rõ ý định ở phía caller.
        static Profile copyOf(Profile source) {
            return new Profile(source);
        }

        // (3) Named instance method: thuận tiện khi đã có đối tượng.
        Profile copy() {
            return new Profile(this);
        }

        void moveTo(String city) {
            address.setCity(city);
        }

        void addTag(String tag) {
            tags.add(Objects.requireNonNull(tag, "tag"));
        }

        String describe() {
            return name + " / " + address.city() + " / " + tags;
        }
    }

    public static void main(String[] args) {
        Address inputAddress = new Address("Ha Noi");
        List<String> inputTags = new ArrayList<>(List.of("java"));

        Profile original = new Profile("An", inputAddress, inputTags);
        Profile byConstructor = new Profile(original);
        Profile byFactory = Profile.copyOf(original);
        Profile byMethod = original.copy();

        // Chỉ thay đổi bản gốc và các đầu vào vẫn do caller giữ.
        original.moveTo("Da Nang");
        original.addTag("oop");
        inputAddress.setCity("Hue");
        inputTags.add("external");

        System.out.println("original: " + original.describe());
        System.out.println("constructor: " + byConstructor.describe());
        System.out.println("factory: " + byFactory.describe());
        System.out.println("method: " + byMethod.describe());
        System.out.println("distinct objects: "
                + (original != byConstructor
                && original != byFactory
                && original != byMethod));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac CopyPatternsDemo.java
java CopyPatternsDemo
```

Kết quả chính xác:

```text
original: An / Da Nang / [java, oop]
constructor: An / Ha Noi / [java]
factory: An / Ha Noi / [java]
method: An / Ha Noi / [java]
distinct objects: true
```

Giải thích theo luồng:

1. Constructor chính **chép phòng vệ** `Address` và list từ đầu vào. Vì thế sửa `inputAddress` hay `inputTags` sau đó không đổi `original`.
2. Copy constructor gọi constructor chính; địa chỉ được sao chép bằng `new Address(...)`, còn list tạo bằng `new ArrayList<>(...)`. Do phần tử trong list là `String` bất biến, mức sao chép này đủ cho ví dụ.
3. `copyOf` và `copy()` **ủy quyền cùng một điểm thực thi**, tránh ba bản logic copy lệch nhau.
4. Khi `original` chuyển thành “Da Nang” và được thêm tag “oop”, ba bản sao vẫn giữ ảnh chụp dữ liệu trước sửa đổi.
5. `!=` chỉ chứng minh các đối tượng ngoài khác tham chiếu. Tính độc lập của **trường bên trong** được thể hiện bằng kết quả địa chỉ và list.

Dấu `final` ở `address` và `tags` chỉ ngăn **gán lại reference của trường**; bản thân `Address` và `List` vẫn mutable. Vì vậy không thể dựa vào `final` để bỏ bước sao chép phòng vệ.

## 5. Phương thức tạo bản sao có tên: khi nào dùng và đặt tên gì?

Hai hình thức thường gặp:

```java
Profile a = new Profile(original);  // Constructor nhận Profile.
Profile b = Profile.copyOf(original); // Static factory có tên.
Profile c = original.copy();         // Instance method có tên.
```

| API | Ưu điểm chính | Điểm phải nói rõ trong tài liệu |
| --- | --- | --- |
| `new T(source)` | Dễ tìm, gần với cú pháp tạo object, bắt buộc tạo đối tượng `T` mới nếu thành công | Có sao chép sâu hay chỉ chép trường? Điều kiện input hợp lệ? |
| `T.copyOf(source)` | Ý định dễ đọc, có thể giấu chi tiết khởi tạo, tập trung kiểm tra/chuẩn hóa | **Tên không bảo đảm** luôn trả object mới: vài API bất biến có thể tái sử dụng instance |
| `source.copy()` | Tự nhiên khi thao tác trên instance; có thể hỗ trợ hành vi riêng theo loại đối tượng | Trả về cùng kiểu hay kiểu cơ sở? Mức độ độc lập? |
| `T.from(source)` | Hữu ích khi chuyển từ kiểu nguồn khác hoặc biểu diễn khác | `from` thường thiên về **chuyển đổi**, không mặc nhiên có nghĩa deep copy |
| `withX(newValue)` | Diễn đạt bản sao có chỉnh một thuộc tính, hợp với immutable value object | Các trường không đổi có được chia sẻ hay không? |

Trong ví dụ, `Profile.copyOf` **luôn** gọi `new Profile(source)`, nên trả object mới. Đừng suy rộng quy tắc này cho mọi thư viện: chẳng hạn factory cho kiểu bất biến có thể trả lại chính input theo hợp đồng riêng. Nếu caller bắt buộc cần **định danh đối tượng mới** (`result != source`), cần ghi rõ trong API và kiểm chứng bằng test.

Một biến thể hữu ích khi muốn “sao chép và đổi một trường” là phương thức `withName`:

```java
// Phương thức thêm vào class Profile ở phần 4 (không phải chương trình độc lập).
Profile withName(String newName) {
    return new Profile(newName, address, tags);
}
```

Vì constructor chính đã sao chép `Address` và list, `withName` vẫn duy trì hợp đồng độc lập trong ví dụ. Với class bất biến thực sự, các giá trị bất biến thường có thể chia sẻ an toàn để giảm cấp phát.

## 6. Thiết kế hợp đồng sao chép chính xác

Trước khi viết một copy constructor hay copy method, hãy xác định các điểm sau:

**(a) Những trường nào là giá trị, những trường nào có thể thay đổi?** Primitive, `String`, enum và các value object bất biến có thể sao chép giá trị/tham chiếu. Các object mutable phải được xem xét theo quyền sở hữu dữ liệu và mức độc lập mong muốn.

**(b) Có giữ được bất biến của class không?** Tái sử dụng constructor hoặc một hàm kiểm tra nội bộ duy nhất để các bản sao thỏa cùng điều kiện: trường bắt buộc không null, collection có phần tử hợp lệ, dữ liệu ở trạng thái nhất quán. Copy constructor không nên bỏ qua các quy tắc mà constructor bình thường tuân thủ.

**(c) Chia sẻ đối tượng con có chủ ý hay vô tình?** Nếu hai bản sao **được phép** cùng nhìn một cache bất biến, một service, hay nguồn dữ liệu dùng chung, ghi rõ đó là quan hệ chia sẻ. Không cố copy file handle, thread, lock, socket, connection hoặc tài nguyên ngoài chỉ để “deep copy mọi thứ”. Với tài nguyên, cần hợp đồng vòng đời và ownership riêng.

**(d) Có trạng thái dẫn xuất hoặc bí mật nội bộ không?** Trường cache suy ra từ dữ liệu chính thường nên tính lại hoặc làm rỗng có chủ ý; identity, version, thời điểm tạo, id nghiệp vụ và trạng thái đăng ký event cần quyết định rõ là **giữ** hay **cấp mới**. Sao chép toàn bộ field máy móc có thể phá bất biến.

**(e) Có null hoặc phần tử mutable lồng nhau không?** `Objects.requireNonNull(source, "source")` giúp lỗi đầu vào rõ ràng. Với trường nullable, tự định nghĩa trường hợp null thay vì gọi constructor copy trên null. Với `List<MutableItem>`, phải copy từng item nếu mỗi item cần độc lập. Đồ thị có **tham chiếu vòng** hoặc nhiều nhánh cùng trỏ một object cần cơ chế theo dõi identity khi deep copy để tránh đệ quy vô hạn và không làm mất quan hệ chia sẻ nội bộ.

**(f) Có nhiều luồng sửa dữ liệu đồng thời không?** Copy nhiều mutable field theo từng bước **không tự tạo snapshot nguyên tử**. Nếu thread khác có thể sửa nguồn giữa chừng, dùng lock, immutable snapshot hoặc chiến lược đồng bộ phù hợp. Copy constructor không tự giải quyết thread safety.

### Gợi ý JavaDoc cho hợp đồng

```java
/**
 * Creates a new Profile with the same name, a separate Address,
 * and a separate mutable list of immutable String tags.
 * Later mutations of either Profile do not affect the other.
 *
 * @throws NullPointerException if source is null
 */
Profile(Profile source) {
    this(Objects.requireNonNull(source, "source").name,
            source.address, source.tags);
}
```

Đoạn JavaDoc này minh họa cách mô tả **phạm vi độc lập** cụ thể; constructor đầy đủ đã có ở phần 4.

## 7. Về kế thừa, record và clone()

**Class có kế thừa:** Copy constructor trong lớp cha chỉ biết phần state của lớp cha; nếu gọi `new Base(subclassInstance)`, đối tượng nhận được là `Base`, phần riêng của subclass không được giữ. Với phân cấp phức tạp, hãy cân nhắc copy method có contract đa hình, protected copy hook hoặc một thiết kế bất biến/không kế thừa. Tránh gọi phương thức có thể override từ constructor vì trạng thái subclass có thể chưa được khởi tạo hoàn chỉnh.

**Record Java:** Record thường là lựa chọn tốt cho dữ liệu dạng giá trị ít thay đổi; record không tự deep-copy reference mutable. Một record chứa `List` vẫn cần snapshot/defensive copy nếu muốn trạng thái ổn định. Non-canonical constructor trong record phải ủy quyền cho canonical constructor:

```java
record Coordinate(int x, int y) {
    Coordinate(Coordinate source) {
        this(source.x(), source.y());
    }
}
```

Với một `Coordinate` bất biến, việc tạo bản sao mới thường **không cần thiết** về mặt hành vi; chia sẻ cùng instance là an toàn nếu API không yêu cầu identity khác nhau.

**`Object.clone()`:** Cơ chế `Cloneable`/`clone()` mang các quy ước và góc cạnh riêng, và phép sao chép mặc định của `Object.clone()` là shallow. Copy constructor hoặc named factory thường dễ diễn đạt kiểm tra đầu vào, bất biến và ownership hơn. Không cần dùng `clone()` chỉ vì bài toán có chữ “copy”.

## 8. Lỗi phổ biến và cách tránh

1. **Nhầm gán tham chiếu với sao chép:** `b = a` không tạo object mới. Dùng constructor hoặc factory được định nghĩa rõ hợp đồng.
2. **Sao chép bao ngoài nhưng giữ chung object mutable:** `this.address = source.address` làm hai bản ghi liên đới. Sao chép `Address` khi cần tách độc lập.
3. **Tưởng constructor `new ArrayList<>(list)` sao chép sâu mọi phần tử:** thao tác này chỉ tạo list mới; từng element mutable có thể vẫn chung tham chiếu.
4. **Tạo hai logic copy riêng:** constructor một kiểu, `copyOf` một kiểu khiến mỗi đường cho kết quả khác nhau. Chọn một chỗ cài đặt và ủy quyền từ API còn lại.
5. **Quên cập nhật copy khi class thêm trường:** bản sao mất thông tin hoặc sai invariant. Rà soát toàn bộ state và test khi thêm trường mới.
6. **Sao chép nguồn chưa nhất quán trong môi trường đa luồng:** snapshot có thể chứa dữ liệu từ nhiều thời điểm. Tổ chức đồng bộ trước khi chép.
7. **Dùng `==` để kết luận hai bản sao có cùng giá trị:** `==` so sánh identity với object; kiểm tra giá trị theo contract (accessor, `equals()` phù hợp) và kiểm tra độc lập bằng thay đổi state.
8. **Đặt tên `copyOf` nhưng không mô tả hành vi:** người đọc có thể kỳ vọng object mới hoặc deep copy. Viết rõ có tái sử dụng instance, chép nông/sâu, và xử lý null thế nào.

## 9. Cách kiểm tra bản sao

Một bộ kiểm tra có ý nghĩa cần chứng minh cả **giá trị ban đầu** lẫn **độc lập sau khi sửa**, thay vì chỉ kiểm tra object khác reference:

- Tạo nguồn có ít nhất một trường mutable lồng nhau và một collection; copy bằng từng API được công khai.
- So sánh dữ liệu quan sát được ngay sau copy.
- Thay đổi trường mutable ở nguồn; kiểm tra bản sao vẫn giữ giá trị cũ.
- Thay đổi trường mutable ở bản sao; kiểm tra nguồn không thay đổi.
- Thử null, dữ liệu không hợp lệ và phần tử mutable trong collection theo hợp đồng đã công bố.
- Với đối tượng giữ quan hệ tham chiếu vòng hoặc alias giữa các nhánh, kiểm tra cấu trúc quan hệ sau deep copy, nếu hợp đồng yêu cầu.

**Quy tắc chọn cách dùng:** `new T(existing)` ngắn gọn khi cần tạo bản sao kiểu `T` một cách trực tiếp. `T.copyOf(existing)` dễ đọc khi muốn API có tên, kiểm soát cách khởi tạo và thống nhất chính sách sao chép. `existing.copy()` thuận tiện cho thao tác theo instance. Dù chọn cách nào, điều quyết định là **hợp đồng về dữ liệu được sao chép và dữ liệu được chia sẻ**, được bảo đảm bằng triển khai và kiểm thử.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="copy-independence-007">Chọn phạm vi sao chép theo quyền sở hữu dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn phạm vi sao chép theo quyền sở hữu dữ liệu trong Java

**Bối cảnh:** Java core → object-contract → sao chép và độc lập. Khi tạo bản sao của một đối tượng, câu hỏi quyết định không phải “sao chép nông hay sâu?” ngay lập tức, mà là **ai sở hữu dữ liệu nào, ai được phép thay đổi dữ liệu đó, và sau khi sao chép những thay đổi nào phải độc lập?** Quyền sở hữu ở đây là **quy ước thiết kế/API**, không phải cơ chế ownership được trình biên dịch Java thực thi.

## 1. Sao chép để bảo vệ ranh giới sở hữu

Biến tham chiếu Java trỏ tới đối tượng; phép gán chỉ sao chép giá trị tham chiếu. Hai biến có thể cùng trỏ tới một đối tượng và cùng quan sát được thay đổi của nó (*aliasing*). Ví dụ:

~~~java
List<String> input = new ArrayList<>(List.of("A"));
List<String> alias = input;                 // Cùng một danh sách
List<String> separate = new ArrayList<>(input); // Danh sách khác
input.add("B");
// alias thấy ["A", "B"]; separate vẫn ["A"].
~~~

Ví dụ trên độc lập ở **cấu trúc danh sách** vì phần tử `String` bất biến. Nếu danh sách chứa các đối tượng có thể thay đổi, `new ArrayList<>(input)` chỉ sao chép danh sách, **vẫn chia sẻ từng phần tử**.

Có thể mô tả một đối tượng như một đồ thị: nút gốc tham chiếu tới các đối tượng con; đối tượng con có thể tiếp tục tham chiếu tới đối tượng khác. **Phạm vi sao chép** là tập các nút cần tạo mới để bảo đảm đúng hợp đồng độc lập, không nhất thiết là toàn bộ đồ thị có thể đi tới được.

Ba kết quả thường gặp:

- **Chia sẻ có chủ đích:** hai bên cùng dùng một giá trị bất biến hoặc tài nguyên chung; không cần bản sao.
- **Sao chép chọn lọc:** tạo mới các nút mutable thuộc sở hữu riêng, đồng thời chia sẻ các giá trị bất biến hoặc các phụ thuộc dùng chung.
- **Snapshot:** tạo ảnh chụp của trạng thái tại một thời điểm; cần xác định snapshot chỉ cố định cấu trúc hay cố định cả trạng thái các phần tử.

Một bản sao có thể bằng nhau theo `equals()` nhưng khác nhau về identity (`original != copy`). Nếu chính identity là ý nghĩa nghiệp vụ (ví dụ một entity đang được ORM quản lý), tự tạo bản sao như một entity mới có thể sai, dù trường dữ liệu giống nhau.

## 2. Xác định quyền sở hữu trước khi chọn kỹ thuật

Với từng trường, tham số và giá trị trả về, cần trả lời:

1. **Ai tạo, ai quản lý vòng đời?** Đối tượng đang được lưu nội bộ, được caller cung cấp tạm thời, hay do framework/tài nguyên ngoài quản lý?
2. **Ai có quyền ghi?** Caller có thể sửa sau khi truyền vào không? Người nhận kết quả có thể sửa không?
3. **Thay đổi nào phải bị cô lập?** Chỉ thêm/xóa phần tử danh sách, hay cả sửa thuộc tính của mỗi phần tử?
4. **Đối tượng có thật sự bất biến?** Không chỉ trường `final`; mọi trạng thái có thể quan sát qua đối tượng đó cũng phải không thể thay đổi bằng các đường tham chiếu được cấp.
5. **Đồ thị có chia sẻ/cycle/identity quan trọng không?** Hai đường dẫn trỏ chung tới một nút có phải vẫn trỏ chung trong bản sao không?
6. **Chi phí có phù hợp không?** Đối tượng lớn, dữ liệu nhị phân, tải cao và luồng đồng thời có thể cần snapshot, immutable value hoặc copy-on-write thay vì deep copy tất cả.

### Ma trận quyết định theo ownership

| Dữ liệu tại ranh giới | Chính sách thường phù hợp | Vì sao |
| --- | --- | --- |
| `String`, `BigInteger`, `LocalDate`, value object bất biến đúng nghĩa | Chia sẻ tham chiếu | Không có mutation quan sát được để rò rỉ |
| `byte[]`, mảng mutable do caller đưa vào và đối tượng cần sở hữu | `array.clone()` hoặc `Arrays.copyOf()` | Tách vùng lưu trữ mảng; nếu mảng đa chiều thì cần xét phần tử bên trong |
| `List<String>` thuộc trạng thái nội bộ | `List.copyOf(input)` hoặc bản sao cấu trúc tương đương | Tách cấu trúc danh sách; `String` bất biến nên phần tử có thể dùng chung |
| `List<MutableItem>` do đối tượng sở hữu | Tạo danh sách mới **và sao chép từng phần tử** | Chỉ sao chép list vẫn làm lọt mutable item |
| Đồ thị nút mutable thuộc sở hữu riêng | Copy constructor/factory có ánh xạ nút đã sao chép | Bảo đảm độc lập, bảo toàn alias/cycle khi hợp đồng đòi hỏi |
| Repository, logger, service, pool, socket, file handle | Giữ tham chiếu chung theo hợp đồng hoặc quản lý vòng đời riêng | Đây là phụ thuộc/tài nguyên, không phải dữ liệu value có thể deep-copy tùy tiện |
| Thực thể JPA/ORM, aggregate có identity nghiệp vụ | Dùng DTO/value snapshot hoặc thao tác qua API quản lý entity | Tạo bản sao object không tự tạo ra entity nghiệp vụ hợp lệ |
| Cache hay cấu trúc chia sẻ chủ đích | Định nghĩa rõ quyền ghi và tính nhất quán | Copy vô điều kiện có thể làm mất semantics chia sẻ |

**Quy tắc hữu ích:** chỉ cần tạo bản sao của các đối tượng **mutable mà ranh giới mới phải sở hữu độc lập**; còn immutable có thể chia sẻ an toàn. Với mutable được chia sẻ có chủ đích, hợp đồng phải chỉ ra bên nào được sửa và khi nào.

## 3. Ba mức độc lập dễ bị nhầm

Giả sử `Order` chứa một `List<Line>`, mỗi `Line` chứa một `Price`:

- **Sao chép tham chiếu:** `Order second = first`. Cả hai biến chỉ đến cùng một đối tượng.
- **Shallow copy:** có `Order` mới và `List` mới, nhưng vẫn tham chiếu tới các `Line` cũ. Sửa danh sách (thêm/xóa) của bản này không trực tiếp thay đổi cấu trúc danh sách bản kia; sửa một `Line` có thể hiện ở cả hai.
- **Deep/ownership copy:** có `Order`, `List` và các `Line` thuộc sở hữu riêng; `Price` chỉ cần copy nếu mutable. Nếu `Price` là value bất biến thì chia sẻ vẫn cho mức độc lập mong muốn.

Đừng gọi một giải pháp là “deep copy” nếu chưa xác định **deep tới đâu**. Một cây mutable, một đồ thị có vòng lặp và một tập object chứa resource sống là ba trường hợp khác nhau. Kết quả đúng được đo bằng invariant và tình huống sửa dữ liệu, không bởi số lần gọi `clone()`.

Sao chép theo ownership thường cần ở hai hướng:

- **Defensive copy đầu vào:** constructor/setter nhận mutable từ caller, tạo phần thuộc sở hữu riêng để caller không thể lén sửa trạng thái nội bộ sau đó.
- **Defensive copy đầu ra:** getter/API trả dữ liệu mutable phải trả bản copy/snapshot hoặc giao diện chỉ đọc phù hợp để bên ngoài không sửa trạng thái thuộc sở hữu nội bộ.

Chỉ copy đầu vào mà trả trực tiếp mutable ra ngoài vẫn có lỗ hổng. Chỉ copy đầu ra mà giữ tham chiếu đầu vào cũng vậy.

## 4. Quy trình chọn phạm vi sao chép

**Bước 1 — Viết hợp đồng độc lập.** Chẳng hạn: “Sau khi tạo `Team` từ danh sách thành viên, caller được sửa danh sách, sửa thành viên và địa chỉ ban đầu mà không làm đổi `Team`. Caller được sửa snapshot trả về mà không làm đổi `Team`.”

**Bước 2 — Vẽ những cạnh tham chiếu quan trọng.** Ví dụ: `Team → List<Member> → Member → Address`. Đánh dấu `List`, `Member`, `Address` là mutable; `String` là immutable.

**Bước 3 — Đánh dấu chủ sở hữu.** `Team` sở hữu danh sách, các phần tử và địa chỉ riêng. Caller tiếp tục sở hữu các object đã truyền vào. Hai phía không được giữ chung mutable node.

**Bước 4 — Chỉ định hành vi ở từng ranh giới.** Constructor `Team` sao chép list, mỗi member và address. Method xuất snapshot lặp lại chính sách ấy. Các phương thức vận hành nội bộ được quyền sửa object mà `Team` sở hữu.

**Bước 5 — Kiểm chứng hai chiều.** Sửa dữ liệu gốc rồi quan sát bản sao; sửa bản sao rồi quan sát dữ liệu gốc. Kiểm tra cả cấu trúc và các phần tử lồng nhau, vì kiểm tra mỗi `==` hoặc size danh sách là chưa đủ.

## 5. Ví dụ Java 17 chạy độc lập: copy theo quyền sở hữu

Lưu thành tệp **OwnershipCopyDemo.java**. `Team` sở hữu độc lập cây `List<Member> → Member → Address`. Dữ liệu chuỗi được chia sẻ an toàn vì `String` bất biến. Snapshot trả về có cấu trúc danh sách không sửa được và các member tách biệt; **member trong snapshot vẫn mutable**, nên snapshot này là dữ liệu tách rời chứ chưa phải immutable value.

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OwnershipCopyDemo {
    static final class Address {
        private String city;

        Address(String city) {
            this.city = Objects.requireNonNull(city);
        }

        Address(Address source) {
            this(Objects.requireNonNull(source).city);
        }

        String city() {
            return city;
        }

        void setCity(String city) {
            this.city = Objects.requireNonNull(city);
        }
    }

    static final class Member {
        private String name;
        private final Address address;

        Member(String name, Address address) {
            this.name = Objects.requireNonNull(name);
            this.address = new Address(Objects.requireNonNull(address));
        }

        Member(Member source) {
            this(Objects.requireNonNull(source).name, source.address);
        }

        String name() {
            return name;
        }

        String city() {
            return address.city();
        }

        void rename(String name) {
            this.name = Objects.requireNonNull(name);
        }

        void move(String city) {
            address.setCity(city);
        }
    }

    static final class Team {
        private final List<Member> members;

        Team(List<Member> suppliedMembers) {
            Objects.requireNonNull(suppliedMembers);
            List<Member> copies = new ArrayList<>(suppliedMembers.size());
            for (Member member : suppliedMembers) {
                copies.add(new Member(Objects.requireNonNull(member)));
            }
            members = List.copyOf(copies);
        }

        List<Member> membersSnapshot() {
            List<Member> copies = new ArrayList<>(members.size());
            for (Member member : members) {
                copies.add(new Member(member));
            }
            return List.copyOf(copies);
        }

        void renameFirst(String newName) {
            members.get(0).rename(newName);
        }

        String describe() {
            Member first = members.get(0);
            return first.name() + "@" + first.city();
        }
    }

    public static void main(String[] args) {
        Address originalAddress = new Address("Ha Noi");
        Member originalMember = new Member("Lan", originalAddress);
        List<Member> supplied = new ArrayList<>();
        supplied.add(originalMember);

        Team team = new Team(supplied);

        originalAddress.setCity("Da Nang");
        originalMember.move("Hue");
        supplied.clear();
        System.out.println("Team after caller changes: " + team.describe());

        List<Member> snapshot = team.membersSnapshot();
        snapshot.get(0).rename("Minh");
        snapshot.get(0).move("Can Tho");
        System.out.println("Team after snapshot changes: " + team.describe());
        System.out.println("Snapshot: "
                + snapshot.get(0).name() + "@" + snapshot.get(0).city());

        try {
            snapshot.add(new Member("Mai", new Address("Hue")));
        } catch (UnsupportedOperationException ex) {
            System.out.println("List structure is unmodifiable");
        }

        team.renameFirst("Hoa");
        System.out.println("Team now: " + team.describe());
        System.out.println("Old snapshot remains: "
                + snapshot.get(0).name() + "@" + snapshot.get(0).city());
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp:

~~~bash
javac OwnershipCopyDemo.java
java OwnershipCopyDemo
~~~

Stdout chính xác:

~~~text
Team after caller changes: Lan@Ha Noi
Team after snapshot changes: Lan@Ha Noi
Snapshot: Minh@Can Tho
List structure is unmodifiable
Team now: Hoa@Ha Noi
Old snapshot remains: Minh@Can Tho
~~~

**Vì sao ví dụ hoạt động?**

1. Constructor `Member(String, Address)` copy `Address`, nên sửa `originalAddress` không tác động tới thành viên.
2. Constructor `Team(List<Member>)` copy từng `Member`, và constructor `Member(Member)` lại copy `Address`. Vì vậy sửa `originalMember` hoặc xóa phần tử trong danh sách input không tác động tới `Team`.
3. `List.copyOf(copies)` ngăn thêm/xóa phần tử thông qua list được lưu, nhưng **không khóa trạng thái Member**; `Team.renameFirst()` vẫn đổi member nội bộ được.
4. `membersSnapshot()` tạo bản sao mọi phần tử mutable trước khi trả về. Caller sửa snapshot không ảnh hưởng tới `Team`; `Team` thay đổi sau đó cũng không ảnh hưởng snapshot cũ.
5. Nếu chỉ viết `List.copyOf(suppliedMembers)` ở constructor, cấu trúc list sẽ riêng nhưng phần tử vẫn là object caller sở hữu — vi phạm yêu cầu độc lập.

Đây là ví dụ dùng một luồng thực thi. Các thao tác copy/rename/move không tự mang lại thread safety; mục về concurrency bên dưới chỉ ra cách có snapshot nhất quán.

## 6. Hiểu đúng công cụ trong Java

### Copy constructor hoặc static factory

Với mutable value có quy tắc sở hữu rõ, copy constructor như `new Member(source)` hoặc factory như `Member.copyOf(source)` là nơi dễ mô tả chính sách copy cho từng field. Nên triển khai rõ ở lớp quản lý dữ liệu, kiểm tra tham số và invariant, để caller không phải biết cấu trúc nội bộ. Copy constructor tự viết **không đương nhiên deep**: nếu nó gán tham chiếu của field mutable thì vẫn là shallow copy.

### Arrays

- `byte[] result = input.clone();` sao chép các byte sang mảng mới — đủ để độc lập vì byte là primitive.
- `Address[] result = input.clone();` chỉ sao chép mảng chứa tham chiếu; từng `Address` vẫn dùng chung.
- `int[][] result = input.clone();` chỉ sao chép mảng ngoài; các mảng hàng vẫn dùng chung. Muốn độc lập dữ liệu hai chiều phải sao chép từng hàng và quy định xử lý hàng `null` nếu cho phép.

### Collections

~~~java
List<StringBuilder> source = new ArrayList<>();
source.add(new StringBuilder("A"));

List<StringBuilder> structuralCopy = new ArrayList<>(source);
List<StringBuilder> unmodifiableStructure = List.copyOf(source);

source.get(0).append("B");
// Cả structuralCopy.get(0) và unmodifiableStructure.get(0) đều là "AB".
~~~

- `new ArrayList<>(source)` tách cấu trúc, không sao chép phần tử.
- `List.copyOf(source)` tạo danh sách **không hỗ trợ thao tác thay đổi cấu trúc**, nhưng phần tử mutable vẫn có thể bị sửa; API này từ chối `null` và có thể trả lại cùng instance nếu input đã là danh sách không sửa được phù hợp.
- `Collections.unmodifiableList(source)` tạo **view** không cho sửa qua view, nhưng nếu `source` thay đổi thì view phản ánh thay đổi; không phải snapshot.
- `Stream.toList()` cho danh sách không hỗ trợ thao tác thay đổi cấu trúc, nhưng cũng không sao chép mutable item bên trong.
- `Map.copyOf()`, `Set.copyOf()` cũng không tự deep-copy key/value/element. Nếu key mutable ảnh hưởng `equals()/hashCode()`, sửa key sau khi cho vào map có thể làm hỏng khả năng tra cứu.

Nếu muốn snapshot thực sự bất biến, hãy **chuyển từng phần tử thành immutable DTO/value** (ví dụ record chỉ chứa `String`, `int`, `LocalDate` hay immutable value đúng nghĩa) rồi dùng danh sách bất biến.

### Java records không tự deep-copy

Record làm component reference trở thành `private final` và sinh accessor, nhưng không biến đối tượng được tham chiếu thành bất biến. Ví dụ sau **chỉ bảo vệ cấu trúc danh sách** khi element là `String` (bất biến):

~~~java
import java.util.List;
import java.util.Objects;

record Tags(List<String> values) {
    Tags {
        values = List.copyOf(Objects.requireNonNull(values));
    }
}
~~~

Đây là khai báo record minh họa, không phải chương trình chạy độc lập có `main`. Trong compact canonical constructor, gán lại tham số `values` khiến giá trị sau kiểm tra được dùng để gán field tương ứng khi constructor kết thúc. Nếu component là `List<MutableItem>`, phải copy sâu các item hoặc đổi sang immutable snapshot. Với mảng mutable, accessor mặc định của record còn trả ra chính mảng bên trong; cần override accessor để trả bản sao khi muốn bảo vệ trạng thái.

### `Object.clone()` không phải giải pháp tự động

Cài đặt `Object.clone()` mặc định tạo bản sao **shallow** ở mức field. `Cloneable` là marker interface; nó không định nghĩa public `clone()` cho caller. Kế thừa, field `final`, mutable nested state và invariant làm quy tắc clone khó bảo trì; các lớp không hỗ trợ clone có thể ném `CloneNotSupportedException`. Đối với ownership copy có hợp đồng cụ thể, constructor/factory rõ ràng thường dễ đọc và dễ kiểm chứng hơn. Không gọi `clone()` trên resource/ORM object với kỳ vọng hệ thống ngoài cũng được copy.

## 7. Khi không nên deep-copy

**Dữ liệu bất biến:** `String`, `UUID`, `LocalDate`, `BigDecimal` và immutable domain values có thể được chia sẻ vì sửa một instance là không thể theo API của chúng. Lưu ý một lớp tự khai báo “immutable” vẫn không bất biến nếu nó trả mảng, collection view có backing mutable, hoặc chứa mutable child truy cập được.

**Dịch vụ hoặc tài nguyên chia sẻ:** logger, executor, kết nối mạng, stream mở, transaction và repository có vòng đời/quyền truy cập riêng. Sao chép field reference có thể đúng khi hai bên cố ý cùng dùng một dependency; “deep-copy” tài nguyên thường không được định nghĩa. Đừng biến dependency thành một phần của cây dữ liệu cần clone.

**Entity và identity nghiệp vụ:** bản sao của một entity có ID cũ không tự trở thành bản ghi độc lập trong database. Tách DTO immutable/snapshot khi cần chuyển dữ liệu qua API hoặc event; thay đổi entity nên đi qua lifecycle/transaction theo hợp đồng hệ thống.

**Chia sẻ mutable có chủ đích:** hai component có thể cùng sở hữu một cache hoặc state store. Khi đó sự phụ thuộc là điều được thiết kế; copy tự tiện làm hai bên không còn quan sát cùng trạng thái. Ghi rõ owner của mutation, đồng bộ, thời gian sống và mức nhất quán.

### Đồ thị có alias hoặc chu trình

Ví dụ `left.child == right.child` trước copy. Nếu tạo riêng hai bản copy cho hai đường đi, invariant “hai cạnh cùng trỏ một nút” bị mất. Với đồ thị có cycle, đệ quy naïve còn có thể lặp vô hạn. Khi hợp đồng yêu cầu clone toàn đồ thị và **bảo toàn topology**, sử dụng bảng ánh xạ dựa trên identity, chẳng hạn `IdentityHashMap<OriginalNode, CopiedNode>`: tạo node copy, ghi vào map **trước** khi đệ quy copy các cạnh, và tái dùng copy đã có khi gặp lại node. Không áp dụng cách này như công thức chung nếu business semantics muốn tách các node logic khác nhau hoặc giữ reference tới resource ngoài.

## 8. Snapshot, hiệu năng và concurrency

Sao chép tốn thời gian và bộ nhớ tỷ lệ với lượng dữ liệu được tạo mới. Chọn phạm vi đúng giúp tránh clone toàn bộ đồ thị không cần thiết:

- Với read-only dữ liệu lớn, ưu tiên immutable value/immutable aggregate và chia sẻ chúng.
- Với mutable state ít được sửa nhưng đọc nhiều, cân nhắc **copy-on-write** hoặc thay thế snapshot immutable nguyên khối khi cập nhật. Đánh giá chi phí ghi và phân bổ bộ nhớ.
- Với dữ liệu trao đổi qua API, cân nhắc DTO snapshot chỉ chứa các trường caller thật sự cần; đừng serialize/deserialize toàn bộ object graph chỉ để tạo bản sao nếu có cách rõ ràng hơn.
- Với mảng lớn như `byte[]`, copy tại ranh giới trust/ownership khi cần chống mutation từ caller; nếu cố tình cho phép zero-copy, ghi rõ lifetime và quyền ghi.

**Thread safety là một yêu cầu khác với ownership independence.** Nếu thread A sửa `Member` đúng lúc thread B copy nó, snapshot có thể quan sát dữ liệu tại nhiều thời điểm khác nhau (đặc biệt khi có nhiều field). Hai bản sao độc lập sau khi tạo **không bảo đảm** snapshot được tạo ra một cách nhất quán.

Để snapshot nhất quán, chọn một cơ chế mà toàn bộ các thread liên quan đều tuân thủ: khóa khi ghi và khi đọc/sao chép, immutable snapshots được phát hành qua cơ chế đồng bộ thích hợp, hoặc một protocol versioning/snapshot có chứng minh nhất quán. `final` bảo vệ việc gán lại tham chiếu field, không làm những object lồng bên trong trở nên thread-safe. Copy một danh sách trong lúc một thread khác sửa danh sách đó mà không đồng bộ còn có thể gặp kết quả không nhất quán hoặc lỗi.

## 9. Lỗi phổ biến và cách nhận diện

| Lỗi | Dấu hiệu | Cách sửa |
| --- | --- | --- |
| Gán `this.items = input` khi cần ownership riêng | Caller sửa input, object nội bộ đổi theo | Copy cấu trúc và mọi phần tử mutable cần tách |
| Chỉ dùng `new ArrayList<>(input)` với mutable elements | Sửa thuộc tính item từ caller ảnh hưởng bản sao | Copy từng item hoặc chuyển thành immutable item |
| Trả trực tiếp mảng/list mutable qua getter | Caller sửa kết quả, trạng thái nội bộ đổi | Defensive copy đầu ra hoặc immutable representation |
| Nghĩ `List.copyOf()` làm mọi phần tử immutable | List không thêm được nhưng item vẫn bị sửa | Tách hoặc đóng băng element |
| Nghĩ `Collections.unmodifiableList()` là snapshot | Backing list sửa thì view đổi | Copy dữ liệu trước khi tạo view |
| Deep-copy luôn mọi field | Tốn kém, sai semantics với service/resource/identity | Phân loại theo ownership và mutability |
| Clone đệ quy mà không xử lý alias/cycle | Mất cấu trúc chia sẻ hoặc lặp vô hạn | Identity memoization nếu cần giữ graph topology |
| Chỉ test `original != copy` | Root khác nhưng nested state vẫn chung | Test thay đổi ở cả hai hướng và mọi lớp mutable |
| Copy khi có concurrent writes mà không điều phối | Snapshot chứa trạng thái trộn thời điểm | Đồng bộ writers/readers hoặc immutable snapshot protocol |

## 10. Checklist review hợp đồng sao chép

Khi thiết kế một constructor, factory hoặc API trả snapshot, hãy kiểm tra:

1. **Định nghĩa ownership:** Sau lời gọi, mỗi mutable object trong graph do ai kiểm soát? Có chỗ nào chủ đích chia sẻ mutable state không?
2. **Đầu vào:** Nếu caller sửa list, array, từng phần tử, object nested hoặc alias sau lời gọi, trạng thái bên nhận có còn đúng không?
3. **Đầu ra:** Caller có thể sửa kết quả và vô tình thay đổi object gốc không? Nếu chỉ trả view, có hợp đồng live-view rõ ràng không?
4. **Identity & invariant:** Object copy phải có identity mới, identity nghiệp vụ giữ nguyên trong DTO, hay phải bảo toàn alias/cycle? `equals()/hashCode()` còn đúng không?
5. **Dữ liệu đặc biệt:** Có `null`, collections chứa `null`, generic mutable object, resource bên ngoài, cache, JPA entity hoặc class kế thừa không?
6. **Concurrency:** Có mutation trong lúc copy không? Tại đâu bảo đảm snapshot nhất quán và visibility giữa thread?
7. **Chi phí:** Lượng dữ liệu cần copy bao nhiêu? Có thể chia sẻ immutable value hoặc chỉ copy đúng phần caller sở hữu không?
8. **Kiểm thử:** Sửa sau khi copy theo cả hai hướng; kiểm tra nested state, các đường alias, danh sách/mảng, snapshot cũ sau khi object gốc thay đổi.

### Tình huống áp dụng nhanh

**Tình huống A:** Constructor nhận `List<LocalDate>`, về sau caller được thay đổi list. Copy cấu trúc một lần bằng `List.copyOf(input)` (nếu không có `null`) là đủ; không cần copy từng `LocalDate`.

**Tình huống B:** Constructor nhận `List<MutableAddress>` và cần dữ liệu độc lập. Tạo list mới và copy mỗi address; chỉ dùng `List.copyOf(input)` thì chưa đạt hợp đồng.

**Tình huống C:** Một getter trả `byte[]` dùng để giữ bí mật trong object. Trả `data.clone()`; constructor cũng copy mảng caller cấp. Nếu không, hai phía có thể sửa trực tiếp cùng bộ nhớ.

**Tình huống D:** Có hai object cùng trỏ tới một `ExecutorService` được truyền từ bên ngoài. Chia sẻ theo hợp đồng và quyết định rõ bên nào chịu trách nhiệm `shutdown()`; không clone executor như dữ liệu domain.

## 11. Tự kiểm tra

**Câu 1.** Sau khi chạy `List.copyOf(mutableList)`, sửa một object mutable vốn là phần tử của `mutableList` có thể hiện trong kết quả không?  
**Đáp án:** Có. Chỉ cấu trúc collection bị ngăn sửa; object phần tử vẫn dùng chung nếu không được copy riêng.

**Câu 2.** Copy constructor phải sao chép tất cả field mới là “đúng”?  
**Đáp án:** Không. Copy những mutable value mà bản sao phải sở hữu riêng; immutable value và shared dependency có thể giữ cùng reference theo hợp đồng.

**Câu 3.** Hai object có `equals() == true` và `a != b` là bằng chứng deep copy đã thành công?  
**Đáp án:** Không. Chúng có thể chia sẻ nested mutable state. Phải thử mutation theo từng ranh giới ownership; với đồ thị, còn phải xem alias/cycle.

**Câu 4.** Vì sao một snapshot deep-copy sau khi tạo vẫn có thể sai trong chương trình đa luồng?  
**Đáp án:** Vì quá trình đọc để copy có thể chồng lấn với thread đang ghi, tạo dữ liệu không nhất quán. Cần protocol đồng bộ cho **thời điểm chụp**, ngoài việc tách alias sau khi chụp.

**Nguyên tắc kết thúc:** Hãy chọn phạm vi sao chép dựa trên **quyền sở hữu, tính mutable và hợp đồng độc lập có thể kiểm chứng**. Deep copy mọi thứ thường thừa hoặc sai; shallow copy mặc định thường thiếu. Thiết kế tốt chỉ sao chép đúng phần trạng thái mutable cần tách và làm rõ mọi phần được chia sẻ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="copy-independence-008">Kiểm tra thay đổi bản sao có ảnh hưởng bản gốc hay không</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra thay đổi bản sao có ảnh hưởng bản gốc hay không

## 1. Mục tiêu: kiểm tra tính độc lập của bản sao

Khi sao chép một đối tượng Java, câu hỏi quan trọng không chỉ là “hai biến có trỏ tới hai đối tượng khác nhau không?”, mà là: **nếu thay đổi trạng thái có thể thay đổi được (mutable state) ở bản sao, trạng thái quan sát được của bản gốc có bị thay đổi theo hay không, và ngược lại?**

Ví dụ, một đối tượng hồ sơ có tên (String), địa chỉ (Address) và danh sách kỹ năng (List<String>). Hai hồ sơ có thể là hai đối tượng khác nhau nhưng cùng trỏ tới một Address hoặc cùng trỏ tới một List. Khi sửa địa chỉ hay danh sách ở bản sao, bản gốc vẫn bị ảnh hưởng.

Cần phân biệt hai khái niệm:

- **Khác danh tính (identity):** kiểm tra bằng toán tử **==** hoặc **!=** trên tham chiếu. **copy != original** chỉ chứng minh đối tượng ngoài cùng khác nhau.
- **Độc lập về trạng thái mutable:** những thành phần được yêu cầu độc lập không có đường tham chiếu chia sẻ khiến thao tác sửa ở một phía làm thay đổi phía kia. Đây là hành vi phải kiểm thử bằng các thao tác thay đổi và quan sát cụ thể.

Không phải mọi tham chiếu dùng chung đều là lỗi: có thể chia sẻ đối tượng bất biến như String, Integer hoặc một value object thực sự immutable mà không tạo rủi ro sửa chung. Mức độc lập cần đạt phụ thuộc vào **hợp đồng sao chép** của lớp/API; “deep copy” thường có nghĩa sao chép phần đồ thị mutable thuộc quyền sở hữu của đối tượng, chứ không nhất thiết tạo một bản mới cho mọi giá trị bất biến.

## 2. Vì sao bản sao có thể ảnh hưởng bản gốc?

Các biến kiểu tham chiếu chứa tham chiếu tới đối tượng, không chứa toàn bộ cấu trúc đối tượng con. Gán biến hay sao chép trường tham chiếu có thể khiến hai đường truy cập cùng đến một đối tượng mutable.

| Cách tạo | Đối tượng ngoài cùng | Trường tham chiếu mutable bên trong | Kết quả khi sửa |
| --- | --- | --- | --- |
| **alias = original** | Chính cùng một đối tượng | Dùng chung | Mọi thay đổi trạng thái qua alias đều nhìn thấy qua original |
| **Sao chép nông (shallow copy)** | Thường là đối tượng mới | Thường vẫn dùng chung đối tượng con | Sửa đối tượng con dùng chung có thể ảnh hưởng bản gốc |
| **Sao chép sâu (deep copy)** | Đối tượng mới | Sao chép các phần mutable thuộc phạm vi cần độc lập | Thay đổi phần đã sao chép độc lập giữa hai phía |
| **Thiết kế immutable / chia sẻ an toàn** | Có thể mới hoặc chia sẻ | Dữ liệu chia sẻ không thể bị thay đổi qua API hợp lệ | Không có thay đổi mutable để lan truyền qua phần được chia sẻ |

Một biến String được gán sang bản sao có thể dùng chung đối tượng String an toàn, vì String immutable. Ngược lại, nếu cùng một đối tượng Address được giữ trong hai đối tượng hồ sơ, lệnh **copy.address.city = "Huế"** sửa chính Address mà bản gốc đang giữ; đây không phải lệnh gán lại trường address của riêng bản sao.

Tương tự, việc gọi **new ArrayList<>(original.skills)** tạo một List mới nhưng chỉ sao chép các tham chiếu phần tử. Nếu các phần tử là String thì việc chia sẻ phần tử an toàn; nếu các phần tử là Address mutable thì thay đổi nội dung Address vẫn có thể lan qua hai danh sách.

## 3. Quy trình kiểm thử đúng

1. **Xác định hợp đồng:** bản sao cần độc lập ở những trường nào? Liệt kê cả đối tượng lồng nhau, mảng, collection, phần tử mutable và trạng thái có thể sửa thông qua getter hoặc phương thức khác.
2. **Arrange – tạo dữ liệu gốc có thể quan sát:** ví dụ địa chỉ “Hà Nội”, danh sách [Java]. Tạo bản sao bằng đúng phương thức/API cần kiểm thử. Chụp giá trị trước khi sửa bằng bản snapshot độc lập hoặc expected value bất biến; tránh gọi một tham chiếu alias là “snapshot”.
3. **Kiểm tra cấu trúc tham chiếu khi phù hợp:** **assertNotSame(original, copy)** chưa đủ; với những thành phần mutable phải tách, kiểm tra thêm **assertNotSame(original.address, copy.address)** và các collection hay phần tử liên quan. Nếu hợp đồng cố ý chia sẻ tài nguyên, không ép mọi tham chiếu đều khác nhau.
4. **Act – sửa trực tiếp bản sao:** gán trường mutable, sửa trạng thái đối tượng con, thêm/xóa phần tử, thay đổi phần tử mutable. **Assert – xác nhận bản gốc không đổi** theo hợp đồng.
5. **Kiểm tra chiều ngược lại:** sửa chính bản gốc rồi xác nhận phần tương ứng của bản sao không đổi. Hai chiều giúp phát hiện tham chiếu chung ngay cả khi chỉ có một phía phơi bày API sửa.
6. **Lặp với những nhánh mutable quan trọng:** nhiều cấp lồng nhau, mảng, List, Map, phần tử mutable, null/collection rỗng và cấu trúc có vòng tham chiếu nếu mô hình dữ liệu có hỗ trợ.

Cần tách bạch **sửa đối tượng con** với **gán lại tham chiếu**. Ví dụ **copy.address = new Address("Huế")** chỉ đổi trường address của copy, nên bản gốc không đổi ngay cả khi trước đó cả hai dùng chung Address. Để thử phát hiện shallow copy phải dùng **copy.address.city = "Huế"** hoặc một phương thức thực sự sửa đối tượng Address hiện có.

## 4. Ví dụ Java 17+: so sánh alias, shallow copy và deep copy

Đoạn sau là chương trình hoàn chỉnh. **UserProfile.shallowCopy()** tạo đối tượng UserProfile mới nhưng giữ nguyên tham chiếu Address và List. **UserProfile.deepCopy()** tạo Address và List mới. Vì phần tử của List là String immutable, cách sao chép List này đủ để độc lập với các trường mutable trong ví dụ.

Lưu thành tệp **CopyIsolationDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class CopyIsolationDemo {

    static final class Address {
        String city;

        Address(String city) {
            this.city = city;
        }

        Address(Address other) {
            this.city = other.city;
        }
    }

    static final class UserProfile {
        String name;
        Address address;
        List<String> skills;

        UserProfile(String name, Address address, List<String> skills) {
            this.name = name;
            this.address = address;
            this.skills = skills;
        }

        UserProfile shallowCopy() {
            return new UserProfile(name, address, skills);
        }

        UserProfile deepCopy() {
            return new UserProfile(
                    name,
                    new Address(address),
                    new ArrayList<>(skills)
            );
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        UserProfile original = new UserProfile(
                "An",
                new Address("Hà Nội"),
                new ArrayList<>(List.of("Java"))
        );

        UserProfile alias = original;
        UserProfile shallow = original.shallowCopy();

        System.out.println("Alias cùng đối tượng: " + (alias == original));
        System.out.println("Shallow khác đối tượng: " + (shallow != original));
        System.out.println("Shallow dùng chung Address: "
                + (shallow.address == original.address));
        System.out.println("Shallow dùng chung List: "
                + (shallow.skills == original.skills));

        shallow.address.city = "Huế";
        shallow.skills.add("SQL");
        System.out.println("Gốc sau sửa shallow: "
                + original.address.city + " | " + original.skills);

        require("Huế".equals(original.address.city),
                "Shallow phải nhìn thấy thay đổi trên Address dùng chung");
        require(original.skills.contains("SQL"),
                "Shallow phải nhìn thấy thay đổi trên List dùng chung");

        UserProfile source = new UserProfile(
                "Bình",
                new Address("Hà Nội"),
                new ArrayList<>(List.of("Java"))
        );
        UserProfile deep = source.deepCopy();

        require(deep != source, "Đối tượng ngoài cùng phải khác");
        require(deep.address != source.address, "Address phải khác");
        require(deep.skills != source.skills, "List phải khác");

        deep.address.city = "Đà Nẵng";
        deep.skills.add("Spring");
        System.out.println("Gốc sau sửa deep: "
                + source.address.city + " | " + source.skills);

        require("Hà Nội".equals(source.address.city),
                "Sửa Address của deep không được sửa bản gốc");
        require(source.skills.equals(List.of("Java")),
                "Sửa List của deep không được sửa bản gốc");

        source.address.city = "Cần Thơ";
        source.skills.add("SQL");
        System.out.println("Deep sau sửa gốc: "
                + deep.address.city + " | " + deep.skills);

        require("Đà Nẵng".equals(deep.address.city),
                "Sửa Address gốc không được sửa deep");
        require(deep.skills.equals(List.of("Java", "Spring")),
                "Sửa List gốc không được sửa deep");

        System.out.println("Các phép kiểm tra đã thành công.");
    }
}
~~~

Biên dịch và chạy (trong terminal có JDK 17+):

~~~bash
javac -encoding UTF-8 CopyIsolationDemo.java
java CopyIsolationDemo
~~~

Kết quả stdout chính xác:

~~~text
Alias cùng đối tượng: true
Shallow khác đối tượng: true
Shallow dùng chung Address: true
Shallow dùng chung List: true
Gốc sau sửa shallow: Huế | [Java, SQL]
Gốc sau sửa deep: Hà Nội | [Java]
Deep sau sửa gốc: Đà Nẵng | [Java, Spring]
Các phép kiểm tra đã thành công.
~~~

**Giải thích kết quả:**

- **alias == original** là true: hai biến cùng trỏ tới một UserProfile.
- **shallow != original** là true nhưng cả Address và List của shallow vẫn trỏ đến các đối tượng con của original; các sửa đổi qua shallow xuất hiện ở original.
- **deep.address != source.address** và **deep.skills != source.skills**. Sau khi sửa deep, source vẫn giữ dữ liệu cũ. Sau khi sửa source, deep vẫn giữ dữ liệu riêng. Điều này chứng minh sự độc lập đối với các trường mutable đã kiểm tra trong mô hình này.
- Phương thức **require(...)** ném AssertionError nếu điều kiện sai, không phụ thuộc cờ chạy **-ea** như câu lệnh assert của Java. Ví dụ cố ý kiểm tra shallow *có* tác động chung để minh họa hành vi, còn deep *không* có tác động chung để chứng minh yêu cầu độc lập.

Lưu ý: deepCopy trên chỉ đúng trong phạm vi ví dụ vì Address không có trường mutable lồng nhau khác và List chứa String. Nếu Address có một đối tượng mutable bên trong hoặc List chứa phần tử mutable thì phải tiếp tục sao chép những nhánh đó theo hợp đồng.

## 5. Chuyển tiêu chí trên thành kiểm thử tự động

Trong dự án có **JUnit Jupiter (JUnit 5)**, có thể viết một test riêng với các phép assert rõ ràng. Đoạn dưới giả định lớp test ở **cùng package** với CopyIsolationDemo và dự án đã khai báo dependency JUnit 5; đây là mã test bổ sung, không phải chương trình chạy độc lập bằng lệnh java trực tiếp.

~~~java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CopyIsolationDemoTest {

    @Test
    void deepCopyIsIndependentInBothDirections() {
        var original = new CopyIsolationDemo.UserProfile(
                "An",
                new CopyIsolationDemo.Address("Hà Nội"),
                new ArrayList<>(List.of("Java"))
        );
        var copy = original.deepCopy();

        assertNotSame(original, copy);
        assertNotSame(original.address, copy.address);
        assertNotSame(original.skills, copy.skills);

        copy.address.city = "Huế";
        copy.skills.add("SQL");
        assertEquals("Hà Nội", original.address.city);
        assertEquals(List.of("Java"), original.skills);

        original.address.city = "Đà Nẵng";
        original.skills.add("Docker");
        assertEquals("Huế", copy.address.city);
        assertEquals(List.of("Java", "SQL"), copy.skills);
    }
}
~~~

Khi kiểm thử một phương thức sao chép thật trong dự án, thay **original.deepCopy()** bằng API thực tế, chọn bộ dữ liệu bao phủ các nhánh mutable và khẳng định theo hợp đồng. Một test duy nhất cho đối tượng phẳng có thể bỏ sót việc chia sẻ ở một trường ít dùng.

## 6. Ma trận tình huống cần kiểm tra

| Kiểu dữ liệu / cấu trúc | Tác động cần thử | Dấu hiệu bản sao chưa độc lập |
| --- | --- | --- |
| Trường nguyên thủy **int**, **boolean** | Sửa trường ở copy rồi kiểm tra original | Thông thường không thể lan qua phép copy giá trị nguyên thủy; nếu lan, có thể đang thao tác cùng đối tượng ngoài cùng |
| **String**, kiểu wrapper bất biến | Gán giá trị mới cho trường ở copy | Gán trường riêng không làm sửa đối tượng String/Integer; không cần sao chép sâu chúng |
| Đối tượng mutable lồng nhau, ví dụ **Address** | Sửa **copy.address.city** | **original.address.city** đổi theo |
| Mảng nguyên thủy, ví dụ **int[]** | Sửa **copy.values[0]** | Phần tử của mảng original đổi theo nếu dùng chung mảng |
| **List<String>** | **copy.skills.add(...)**, remove, set | List gốc đổi theo nếu dùng chung chính List |
| **List<Address>** | Sửa **copy.addresses.get(0).city** | List mới nhưng phần tử Address vẫn dùng chung |
| **Map<K, MutableValue>** | Sửa một value, thêm/xóa entry | Value gốc đổi theo nếu phần tử chung; thêm/xóa entry lan nếu Map chung |
| Getter trả tham chiếu mutable | Lấy **copy.getAddress()** rồi sửa | Bản gốc đổi dù trường có thể được khai báo private |
| Đồ thị có nhánh dùng chung/vòng tham chiếu | Sửa phần tử sâu hơn, kiểm tra hai chiều | Một nhánh còn chia sẻ ngoài phạm vi hợp đồng; sao chép đệ quy ngây thơ có thể lặp vô hạn |

Với List, Map và mảng, phải phân biệt **sao chép vỏ chứa** với **sao chép phần tử**. Ví dụ:

~~~java
List<Address> original = new ArrayList<>();
original.add(new Address("Hà Nội"));

List<Address> copy = new ArrayList<>(original); // List mới, Address cũ
copy.get(0).city = "Huế";

// original.get(0).city cũng là "Huế" vì phần tử Address dùng chung.
~~~

Đoạn này minh họa và sử dụng lớp Address đã định nghĩa ở ví dụ trước; không phải một tệp Java độc lập. Nếu muốn độc lập với thay đổi của từng Address, cần tạo Address mới cho từng phần tử, chẳng hạn duyệt source và gọi **new Address(item)**, đồng thời xử lý null và các mối liên hệ tham chiếu theo hợp đồng.

## 7. Chọn chiến lược sao chép và giới hạn của từng API

- **Copy constructor / factory method:** dễ diễn đạt chính xác cách sao chép các trường. Với lớp có trường mutable, cần quyết định sao chép đệ quy, giữ immutable hay chia sẻ một tài nguyên có chủ đích. Một copy constructor chỉ gán lại các tham chiếu có thể vẫn là shallow copy.
- **clone():** **Object.clone()** sao chép trạng thái trường ở mức nông; nó không tự tạo bản sao độc lập cho mọi đối tượng con. Muốn dùng clone theo hợp đồng sâu phải cài đặt xử lý các trường mutable phù hợp. Việc lớp hỗ trợ Cloneable không có nghĩa đồ thị đối tượng đã được deep copy.
- **Mảng:** **array.clone()** hoặc **Arrays.copyOf(...)** tạo mảng mới; đối với mảng chứa tham chiếu, các phần tử vẫn có thể cùng trỏ tới những đối tượng mutable.
- **Collection:** **new ArrayList<>(source)** tạo List mới nhưng phần tử vẫn được chia sẻ theo tham chiếu. **List.copyOf(source)** tạo danh sách không cho sửa bằng các thao tác List của nó, nhưng không sao chép sâu các phần tử mutable; nó cũng từ chối null trong các phần tử đầu vào. **Collections.unmodifiableList(source)** là view không cho sửa qua view nhưng có thể phản ánh thay đổi từ danh sách nguồn bên dưới.
- **record trong Java:** các component tham chiếu vẫn có thể trỏ tới đối tượng mutable. Cú pháp record không tự tạo bản sao sâu hoặc bảo đảm bất biến sâu; constructor và accessor có thể cần defensive copy nếu mục tiêu là bảo vệ trạng thái.
- **Đối tượng immutable:** có thể chia sẻ an toàn khi tính bất biến là thật (bao gồm các phần con được phơi bày). Không nên cố sao chép mọi String hay value object immutable chỉ để mọi identity khác nhau.
- **Đồ thị phức tạp:** với tham chiếu vòng, tham chiếu trỏ chung giữa nhiều trường hoặc đối tượng quản lý tài nguyên (file, socket, thread), khái niệm “deep copy toàn bộ” cần định nghĩa rõ. Một số thành phần phải giữ dùng chung hoặc không thể sao chép hợp lý; nên thiết kế API và test theo hợp đồng sở hữu.

Ví dụ một cách hạn chế rò rỉ qua getter: thay vì trả trực tiếp một List mutable nội bộ, có thể trả **List.copyOf(internalList)** nếu phần tử an toàn để chia sẻ. Với phần tử mutable, cần tạo bản sao từng phần tử hoặc cung cấp giao diện chỉ đọc thực sự theo hợp đồng.

## 8. Các sai lầm phổ biến khi kết luận “bản sao độc lập”

1. **Chỉ kiểm tra copy != original:** kết luận sai vì đối tượng ngoài cùng khác nhau nhưng nhánh mutable có thể cùng tham chiếu.
2. **Chỉ kiểm tra equals():** **equals()** trả lời câu hỏi bằng nhau về giá trị theo cách lớp định nghĩa, không nói hai đồ thị đối tượng có độc lập hay không. Hai bản sao có thể equals() true ngay sau khi sao chép mà vẫn độc lập.
3. **Chỉ gán lại trường ở copy:** thay đổi tham chiếu riêng không thử được sharing của đối tượng con. Cần sửa *bên trong* đối tượng con mutable.
4. **Chỉ kiểm tra chiều copy → original:** có thể bỏ sót những đường cập nhật qua bản gốc hay getter; thử cả hai chiều khi hợp đồng yêu cầu.
5. **Dùng snapshot bị alias:** ví dụ **var before = original.address;** rồi đọc **before.city** sau khi sửa không phải là lưu giá trị ban đầu. Hãy lưu String city hoặc tạo snapshot độc lập.
6. **Nhầm collection không sửa được với phần tử bất biến:** một List không cho add/remove vẫn có thể chứa Address mutable và chia sẻ Address với bản gốc.
7. **Bỏ qua đường truy cập gián tiếp:** setter, getter, phần tử mảng, Map value và một nhánh con sâu vẫn có thể sửa chung trạng thái.
8. **Dựa vào lệnh assert thông thường nhưng không bật -ea:** Java bỏ qua câu lệnh assert nếu assertions disabled. Dùng JUnit assertions hoặc kiểm tra luôn hoạt động như **require(...)** trong ví dụ.
9. **Quên hợp đồng của hashCode/equals với đối tượng mutable:** nếu thay đổi trường tham gia equals/hashCode khi đối tượng là khóa HashMap hoặc phần tử HashSet, có thể làm tra cứu sai kỳ vọng. Đây là lý do bổ sung để thiết kế bản sao và tính bất biến cẩn thận; nó không phải phép thử thay thế cho independence.

## 9. Checklist đánh giá kết quả

Một bài kiểm thử về độc lập bản sao chỉ nên kết luận **PASS cho phạm vi được kiểm tra** khi:

- Xác định được bản sao bằng đúng cơ chế sản phẩm sử dụng và đối tượng ngoài cùng có danh tính phù hợp hợp đồng.
- Những nhánh mutable cần tách có quan hệ identity đúng; nếu có chia sẻ, đó là chia sẻ được phép và không tạo ảnh hưởng trạng thái trái hợp đồng.
- Sửa trực tiếp từng nhóm trạng thái mutable được yêu cầu trên copy không làm thay đổi trạng thái tương ứng của original.
- Sửa original không làm thay đổi phần tương ứng của copy.
- Những trường hợp trọng yếu như collection chứa phần tử mutable, null, dữ liệu rỗng và độ sâu nhiều cấp được bao phủ theo cấu trúc thật; expected value được lưu độc lập.
- Kết quả quan sát bằng assertion có thể thất bại khi code sao chép bị hỏng; kiểm thử thực sự phát hiện shallow sharing ngoài ý muốn.

**Kết luận:** Để trả lời “thay đổi bản sao có ảnh hưởng bản gốc không?”, hãy **thao tác lên trạng thái mutable được sao chép, kiểm tra trạng thái gốc trước/sau và thử cả chiều ngược lại**. Kiểm tra identity giúp định vị nguyên nhân, còn kiểm thử hành vi và hợp đồng sở hữu mới quyết định bản sao có độc lập đúng yêu cầu hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="copy-independence-009">Không xem serialize rồi deserialize là cách sao chép mặc định cho mọi lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Không xem serialize rồi deserialize là cách sao chép mặc định cho mọi lớp

## 1. Phân biệt sao chép đối tượng và Java Serialization

Một kỹ thuật thường được gọi là "deep copy bằng serialization" là dùng `ObjectOutputStream` để chuyển một object sang dãy byte, sau đó dùng `ObjectInputStream` để đọc dãy byte ấy thành object. Quy trình khái niệm:

```text
Đối tượng gốc → ghi ObjectOutputStream → byte[]
byte[] → đọc ObjectInputStream → đối tượng khôi phục
```

Với một đồ thị gồm các object hỗ trợ Java Serialization và không có cơ chế thay thế đặc biệt, cách này *có thể* tạo ra những object mới chứa nhiều dữ liệu tương đương với bản gốc. Tuy nhiên, mục đích gốc của Java Object Serialization là biểu diễn và khôi phục trạng thái theo một **giao thức serialization**, không phải cung cấp hợp đồng sao chép mọi lớp.

Muốn gọi một thao tác là "copy" cần xác định rõ: bản mới có phải khác tham chiếu không, những trường nào được giữ, trường nào phải tách khỏi bản gốc, quan hệ dùng chung giữa các trường có được bảo toàn không, tài nguyên ngoài bộ nhớ có được dùng chung hay tạo lại, và constructor/invariant của lớp có được đáp ứng không.

**Kết luận:** serialize rồi deserialize không phải lựa chọn mặc định cho mọi lớp. Hãy xem nó là một kỹ thuật có nhiều điều kiện và giới hạn, không phải một hàm `deepCopy(Object)` tự động đúng trong mọi trường hợp.

## 2. Điều kiện Serializable áp dụng cho cả đồ thị

`java.io.Serializable` là một *marker interface*: không bắt lớp phải triển khai phương thức nào. Khi gọi `ObjectOutputStream.writeObject(root)`, bộ ghi có thể đi theo các trường tham chiếu của `root` để ghi những object trong đồ thị.

Nếu gặp một object không hỗ trợ Serializable qua một trường cần ghi, quá trình có thể ném `NotSerializableException`, ngay cả khi lớp `root` đã `implements Serializable`.

```java
import java.io.Serializable;

// Minh họa hai lớp, chưa phải chương trình độc lập.
final class Attachment {
    String location = "folder/item";
}
final class Report implements Serializable {
    private static final long serialVersionUID = 1L;
    Attachment attachment = new Attachment();
}
```

`Report` là Serializable, nhưng `Attachment` không phải. Việc serialize một `Report` chứa `attachment` theo quy tắc mặc định sẽ thất bại. Thêm `implements Serializable` cho lớp gốc không tự động làm những đối tượng được tham chiếu trở nên Serializable.

| Loại trạng thái | Hành vi điển hình khi serialize/deserialze |
| --- | --- |
| Trường nguyên thủy | Giá trị được ghi và khôi phục theo dạng serialized. |
| Trường tham chiếu bình thường | Đối tượng tham chiếu cần thỏa cơ chế serialization tương ứng. |
| Trường `transient` | Bị bỏ qua ở dạng ghi mặc định; khi đọc lại thường nhận giá trị mặc định nếu không có logic khôi phục khác. |
| Trường `static` | Không phải trạng thái instance; không tạo một bản copy static độc lập. |
| Tài nguyên bên ngoài | Socket, thread, stream, lock, kết nối, transaction... không tự được nhân bản đúng nghĩa nghiệp vụ. |

Dùng `transient` để tránh lỗi ghi **không đồng nghĩa** copy thành công. Nếu trường là cache có thể tính lại, việc mất nó có thể chấp nhận; nếu nó là dependency cần thiết để object hoạt động, bản khôi phục có thể thiếu trạng thái quan trọng.

Đối với lớp Serializable thông thường, deserialization **không gọi constructor của lớp đó như lệnh `new`**. Với một superclass không Serializable, Java có quy tắc gọi constructor không tham số có thể truy cập của superclass gần nhất không Serializable. Vì thế cách khôi phục trường và invariant không nhất thiết giống copy constructor.

## 3. Sao chép đồ thị: giữ tham chiếu lặp và chu trình

Serialization mặc định có cơ chế ghi nhận các object đã xuất hiện trong stream:

```text
Gốc:
Container
 ├─ first ──┐
 └─ second ─┴──> Node A ─next─> Node A

Khôi phục:
Container'
 ├─ first ──┐
 └─ second ─┴──> Node A' ─next─> Node A'
```

Trong một đồ thị Serializable thích hợp, `A'` là node khác `A` nhưng hai trường `first` và `second` của container mới vẫn trỏ **cùng A'**; chu trình `next` cũng có thể được khôi phục. Đây là ưu điểm kỹ thuật so với một hàm clone đệ quy ngây thơ làm mất alias hoặc lặp vô hạn.

Nhưng sao chép sâu theo nghiệp vụ không chỉ là "mọi object khác tham chiếu": có object cần tiếp tục dùng chung, có tài nguyên không được nhân bản, có ID phải giữ hoặc phải cấp mới. Hơn nữa, các lớp có `readResolve`, `writeReplace`, `writeObject`, `readObject` hoặc `Externalizable` có thể chủ động thay đổi dạng ghi/đọc hoặc đối tượng kết quả. `enum` cũng có ngữ nghĩa đặc biệt về instance. Không thể tuyên bố mọi lần round-trip đều trả về một object độc lập mới.

## 4. Ví dụ Java 17 hoàn chỉnh: những gì được bảo toàn, những gì mất

Tạo tệp `SerializationCopyDemo.java`:

```java
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SerializationCopyDemo {
    static final class Node implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        String title;
        List<String> tags = new ArrayList<>();
        Node next;
        transient String cache = "ready";

        Node(String title) {
            this.title = title;
        }
    }

    static final class Pair implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        Node left;
        Node right;

        Pair(Node value) {
            left = value;
            right = value;
        }
    }

    static Pair roundTrip(Pair value)
            throws IOException, ClassNotFoundException {
        byte[] data;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
            out.flush();
            data = bytes.toByteArray();
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(data))) {
            return (Pair) in.readObject();
        }
    }

    public static void main(String[] args) throws Exception {
        Node n = new Node("Java");
        n.tags.add("core");
        n.next = n;

        Pair original = new Pair(n);
        Pair copied = roundTrip(original);

        System.out.println("different root: "
                + (original != copied));
        System.out.println("different child: "
                + (original.left != copied.left));
        System.out.println("alias kept: "
                + (copied.left == copied.right));
        System.out.println("cycle kept: "
                + (copied.left.next == copied.left));
        System.out.println("original cache: " + original.left.cache);
        System.out.println("copied cache: " + copied.left.cache);

        copied.left.tags.add("copy");
        System.out.println("original tags: " + original.left.tags);
        System.out.println("copied tags: " + copied.left.tags);
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac SerializationCopyDemo.java
java SerializationCopyDemo
```

Stdout:

```text
different root: true
different child: true
alias kept: true
cycle kept: true
original cache: ready
copied cache: null
original tags: [core]
copied tags: [core, copy]
```

Giải thích: `Pair` và `Node` ở đồ thị khôi phục là các instance khác đồ thị gốc. Tuy nhiên, cả hai trường `left` và `right` ở bản mới vẫn dùng chung một node mới, và `next` tạo vòng tới chính node đó. `tags` trong đồ thị mới độc lập nên có thể thêm phần tử mà không sửa `tags` gốc.

Trường `cache` là `transient`, vì vậy không có trong dạng ghi mặc định. Field initializer `"ready"` của lớp Serializable không chạy như lúc tạo instance thông thường trong quá trình đọc, nên bản copy có `cache == null`. Một object trông giống bản copy "sâu" vẫn có thể **thiếu dữ liệu**.

Chương trình chỉ deserialize dãy byte tự nó vừa tạo ra từ các lớp đã biết. Không sử dụng đoạn mã này như một API tiếp nhận dữ liệu serialized từ nguồn không tin cậy.

## 5. Một trường không Serializable gây lỗi; `transient` thay đổi ý nghĩa bản sao

Đặt thêm vào chương trình minh họa khác:

```java
class NativeResource {
    String name = "R1";
}

class Job implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    NativeResource resource = new NativeResource();
}
```

Hai lớp trên minh họa, chưa có `main`. `Job` implements Serializable nhưng `resource` giữ một `NativeResource` không Serializable. Nếu ghi `new Job()` qua `ObjectOutputStream.writeObject`, việc ghi sẽ ném `NotSerializableException`.

Có thể thử thay bằng:

```java
transient NativeResource resource = new NativeResource();
```

Dòng trên là khai báo trường minh họa trong `Job`. Khi đó việc ghi mặc định sẽ bỏ trường `resource`. Tuy nhiên, **bản đọc lại không còn object `resource`**, và constructor của `Job` không tự chạy lại để tạo `new NativeResource()`. Nếu các phương thức `Job` cần `resource`, chúng có nguy cơ gặp `NullPointerException` hoặc vi phạm invariant, trừ khi lớp có quy tắc khôi phục riêng được thiết kế cẩn thận.

Một lựa chọn rõ ràng hơn có thể là copy constructor, factory tạo `NativeResource` độc lập khi cần, hoặc cấm copy khi resource không có ngữ nghĩa nhân bản an toàn. Không nên dùng `transient` như mẹo làm cho mọi lớp ghi được.

## 6. Vì sao không thể mặc định gọi đây là deep copy?

Cần phân biệt **sao chép trạng thái Serializable** và **sao chép theo hợp đồng nghiệp vụ**.

1. **Đồ thị có thể không Serializable hoàn chỉnh.** Một phần tử lồng sâu không Serializable đủ để làm luồng ghi thất bại.
2. **Có trường không được ghi.** `transient` mất dữ liệu theo mặc định; `static` không được sao chép theo từng instance.
3. **Đối tượng có logic serialization riêng.** `readResolve` có thể trả về instance dùng chung, `readObject` có thể khôi phục hoặc thay đổi trạng thái theo logic lớp, các hook có thể ảnh hưởng cách khôi phục.
4. **Tài nguyên không đơn thuần là byte.** File descriptor, kết nối, luồng xử lý, lock và handle bên ngoài JVM cần chính sách vòng đời, không thể clone bằng cách khôi phục các trường Java.
5. **Bất biến constructor có thể khác.** Các bước validation, đăng ký vào registry, cấp ID, khởi tạo lock hoặc cache thường được thực hiện trong constructor/factory; serializer không mặc định lặp lại những bước đó.
6. **Độc lập và chia sẻ là quyết định nghiệp vụ.** Một bản sao có thể phải tạo riêng danh sách nhưng giữ chung object immutable; hoặc phải tách con trỏ về parent và giữ quan hệ alias. Kỹ thuật serializer không tự hiểu ý nghĩa của từng trường.
7. **Trạng thái có thể bị thay đổi đồng thời.** Nếu luồng khác sửa object khi đang ghi, ứng dụng phải bảo đảm snapshot nhất quán; serialization không tự thay thế đồng bộ hóa ở cấp nghiệp vụ.
8. **Định dạng có thể không tương thích về sau.** Thay đổi cấu trúc class, serial form hoặc serialVersionUID có thể khiến khôi phục thất bại hoặc tạo dữ liệu không còn hợp lệ.

Đừng chỉ kiểm thử `copy != original`. Một phép copy đáng tin cần thử chỉnh sửa mutable field ở bản mới có ảnh hưởng tới bản cũ hay không, các quan hệ alias và chu trình, toàn bộ invariant và các tài nguyên bắt buộc sau copy.

## 7. So sánh với `clone()`, copy constructor và factory

| Cách sao chép | Đặc điểm | Phù hợp khi |
| --- | --- | --- |
| `super.clone()` đi tới `Object.clone()` | Sao chép nông trường instance; tham chiếu object con vẫn dùng chung. | API clone đã có hợp đồng rõ; có thể tự xử lý trường mutable. |
| Copy constructor `new Type(source)` | Constructor chủ động chọn trường cần sao chép, có thể gán trường `final` mới và xác nhận invariant. | Cấu trúc dữ liệu tương đối rõ, cần chính sách copy minh bạch. |
| Factory `Type.copyOf(source)` | Có thể tạo snapshot, object mới hoặc tái dùng immutable object theo hợp đồng. | Muốn API dễ tiến hóa và kiểm soát trạng thái. |
| Serialize → deserialize | Ghi/đọc đồ thị theo quy tắc Java Serialization, có thể giữ alias và chu trình. | Chỉ khi đồ thị phù hợp, hệ thống thực sự cần serial form, nguồn dữ liệu được kiểm soát và chi phí được chấp nhận. |
| Dữ liệu immutable | Có thể chia sẻ an toàn mà không cần deep clone. | Tất cả thành phần liên quan thực sự bất biến. |

Với những trường hợp đơn giản, copy constructor thường dễ đọc hơn:

```java
import java.util.ArrayList;
import java.util.List;

final class Profile {
    private final String name;
    private final List<String> labels;

    Profile(String name, List<String> labels) {
        this.name = name;
        this.labels = new ArrayList<>(labels);
    }

    Profile(Profile source) {
        this(source.name, source.labels);
    }
}
```

Đây là khai báo lớp, không có `main`. Constructor sao chép tạo **danh sách mới**; các phần tử là `String` bất biến nên có thể được chia sẻ. Nếu `labels` chứa object mutable, `new ArrayList<>(labels)` vẫn chỉ là copy nông **các phần tử**, cần thêm chính sách tách object con.

Nếu bản sao cần tạo ID mới, đóng/mở tài nguyên khác, bỏ cache, hoặc thay thông tin môi trường thì nên thể hiện rõ trong constructor/factory. Những quy tắc đó không nên bị giấu sau một utility serialize/deserialize tổng quát.

## 8. Hiệu năng, tương thích và bảo mật

### Chi phí thực thi

Một round-trip yêu cầu đi qua đồ thị, mã hóa byte, cấp phát buffer và tạo lại object từ byte. Điều này có thể tiêu tốn CPU, bộ nhớ tạm, gây áp lực thu gom rác và chạy các callback serialization. Không thể suy luận nó **luôn** chậm hơn mọi copy constructor trong mọi tình huống, nhưng rõ ràng phải thực hiện những bước bổ sung ngoài sao chép trực tiếp các dữ liệu cần thiết. Với thao tác copy thường xuyên hoặc đồ thị lớn, nên cân nhắc thiết kế khác và đo bằng benchmark phù hợp.

### Tương thích phiên bản

`serialVersionUID` hỗ trợ cơ chế kiểm tra tương thích khi đọc dữ liệu đã ghi bởi các phiên bản class khác nhau. Nó **không bảo đảm** mọi thay đổi field, kế thừa hoặc bất biến nghiệp vụ đều tương thích. Thay đổi không tương thích có thể dẫn tới `InvalidClassException` hoặc khôi phục trạng thái thiếu/sai so với yêu cầu mới.

Nếu cần một định dạng lưu trữ hay trao đổi lâu dài, hãy thiết kế DTO/schema, versioning, validation và chiến lược migration phù hợp. Đó là yêu cầu khác với việc muốn tạo bản sao trong bộ nhớ.

### An toàn dữ liệu

`ObjectInputStream` không nên được dùng để xử lý dữ liệu Java serialized từ **nguồn không tin cậy**. Cơ chế khôi phục có thể kích hoạt logic của các class có trong hệ thống, và dạng byte này không tự có mã hóa, xác thực hay validation nghiệp vụ. Khi phải duy trì Java Serialization trong hệ thống cũ, cần giới hạn kiểu/kích thước và áp dụng các biện pháp bảo vệ thích hợp, chẳng hạn object input filtering, kiểm soát nguồn và kiểm tra dữ liệu sau khi đọc. Các biện pháp này không biến mọi đầu vào bên ngoài thành an toàn.

**Không thêm `implements Serializable` hàng loạt chỉ để có hàm copy tiện.** Điều đó mở rộng trách nhiệm về định dạng, tương thích và bề mặt xử lý dữ liệu serialized.

## 9. Thiết kế hợp đồng copy đúng cho object model

Trước khi quyết định cách làm, trả lời:

- Bản copy phải có **danh tính mới** hay được phép trả chính object immutable cũ?
- Những trường nào là **giá trị cốt lõi**, trường nào chỉ là cache/metadata/tài nguyên phụ?
- Cần giữ nguyên ID nghiệp vụ hay cấp ID mới?
- Các trường `List`, `Map`, mảng, object con mutable có cần độc lập không? Độc lập đến độ sâu nào?
- Hai trường đang cùng trỏ một node có phải tiếp tục cùng trỏ **một node mới** trong bản copy không?
- Có tham chiếu vòng không? Nếu tự copy đồ thị, nên dùng bảng ánh xạ theo danh tính từ node gốc sang node bản sao để tránh lặp và giữ quan hệ chia sẻ.
- Có trường `final`, invariant constructor, lock hoặc resource cần tạo lại bằng factory không?
- Object có thể bị sửa bởi luồng khác khi copy không? Có cơ chế snapshot nhất quán chưa?
- Các trường tham gia `equals`/`hashCode` có yêu cầu bất biến khi đối tượng dùng làm khóa trong map/set không?

Sau đó kiểm thử trên bản sao: kiểm tra giá trị, sửa dữ liệu mutable ở một phía, kiểm tra alias và chu trình, xác minh invariant, tài nguyên và việc đóng/mở đúng ownership.

**Gợi ý thực hành:** dữ liệu nhỏ và dễ hiểu thường phù hợp với copy constructor/factory; object value immutable ít khi cần copy sâu; đồ thị mutable phức tạp cần hàm sao chép có chính sách, có thể dùng bản đồ object theo identity. Serialization có thể phù hợp nếu định dạng và ngữ nghĩa của nó đã là yêu cầu thực tế, chứ không phải cách mặc định.

## 10. Các nhầm lẫn thường gặp và tự kiểm tra

| Nhầm lẫn | Vì sao không đúng |
| --- | --- |
| Lớp gốc Serializable thì mọi trường đều copy được. | Đồ thị có thể chứa object không Serializable. |
| Dùng `transient` thì copy tương đương bản gốc. | Trường bị bỏ qua, có thể phá invariant. |
| Deserialize luôn gọi constructor của lớp Serializable. | Cơ chế khôi phục không giống `new` thông thường. |
| Mọi reference trong bản sao đều độc lập. | Alias và chu trình có thể được bảo toàn; một số loại có logic thay thế instance. |
| Serialization tự copy socket/lock/connection. | Tài nguyên cần hợp đồng vòng đời riêng. |
| Có `serialVersionUID` là tương thích mọi phiên bản. | Class vẫn có thể thay đổi không tương thích hoặc sai ý nghĩa dữ liệu. |
| Deserialize byte bất kỳ là thao tác vô hại. | Dữ liệu serialized không tin cậy phải tránh hoặc được xử lý theo chính sách bảo vệ nghiêm ngặt. |
| Deep copy nghĩa là sao chép mọi thứ. | Deep copy phải được định nghĩa theo dữ liệu nào cần độc lập và những quan hệ nào cần giữ. |

**Câu hỏi tự kiểm tra:**

1. Một `Report implements Serializable` chứa trường `Attachment` không Serializable sẽ có vấn đề gì khi `writeObject`?
2. Vì sao `transient` có thể làm thao tác ghi thành công nhưng bản đọc lại thiếu dữ liệu?
3. Nếu hai trường cùng trỏ tới một node, bản serialize/deserialize mặc định có nhất thiết tách hai trường thành hai node khác nhau không?
4. Vì sao cần kiểm tra cả quan hệ alias và chu trình khi sao chép một đồ thị?
5. Tại sao không nên copy `Socket`, `Thread` hay một kết nối cơ sở dữ liệu bằng serialization?
6. Copy constructor mang lại lợi ích gì khi lớp có nhiều trường `final` hoặc logic validation quan trọng?
7. Khi nào chia sẻ một object immutable tốt hơn tạo bản sao?
8. Vì sao không dùng `ObjectInputStream` tùy tiện với byte do bên ngoài cung cấp?
9. Sau một bản copy, có nên tự động giữ nguyên một ID phải là duy nhất trong hệ thống không?
10. Cần những phép thử nào để biết một bản sao thực sự độc lập với những trường mutable quan trọng?

**Ghi nhớ:** Java Serialization có thể ghi và khôi phục một **đồ thị Serializable**, nhưng không phải một hợp đồng sao chép mặc định cho mọi lớp. `NotSerializableException`, trường `transient`, cơ chế custom serialization, tài nguyên ngoại vi, tương thích phiên bản, chi phí và bảo mật đều là lý do phải **chọn chính sách copy trước** rồi mới chọn kỹ thuật. Với nhiều lớp ứng dụng, copy constructor, factory hoặc thiết kế immutable rõ ràng và dễ kiểm soát hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
