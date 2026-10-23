<a id="back-to-top"></a>

# View và sao chép

## Menu
- [Phân biệt collection sở hữu dữ liệu với view dùng chung nguồn](#views-copies-001)
- [subList và thay đổi cấu trúc qua view hoặc nguồn](#views-copies-002)
- [keySet, values, entrySet và các thao tác ghi xuyên về Map](#views-copies-003)
- [View phạm vi của TreeSet, TreeMap và view đảo thứ tự](#views-copies-004)
- [Sao chép bằng constructor, addAll và putAll](#views-copies-005)
- [Sao chép nông, sao chép sâu và đối tượng phần tử dùng chung](#views-copies-006)
- [Map.Entry còn gắn nguồn so với entry độc lập](#views-copies-007)
- [clone, serialization và giới hạn của từng cách sao chép](#views-copies-008)

## <a id="views-copies-001">Phân biệt collection sở hữu dữ liệu với view dùng chung nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt collection sở hữu dữ liệu với view dùng chung nguồn

## 1. Một danh sách trả về có thể là dữ liệu riêng hoặc một cửa sổ nhìn vào nguồn

Giả sử một đối tượng quản lý danh sách công việc và trả một List cho bên gọi. Bên gọi thêm hoặc xóa phần tử trên kết quả rồi thấy dữ liệu của đối tượng quản lý cũng đổi.

Một nguyên nhân có thể là kết quả đang dùng chung cấu trúc nền: đó có thể là chính đối tượng ban đầu hoặc một view gắn với nó.

Ngược lại, hai danh sách có cấu trúc riêng vẫn có thể cùng giữ một đối tượng công việc. Khi sửa trường của đối tượng ấy, cả hai cùng nhìn thấy thay đổi.

Vì vậy không thể chỉ hỏi “đây có phải bản sao không?”. Cần tách ba câu hỏi:

1. Tập phần tử, vị trí hoặc ánh xạ có được quản lý độc lập với nguồn không?
2. Những phép sửa nào được phép qua bề mặt đang cầm?
3. Các đối tượng phần tử có được dùng chung không?

Bài dùng cụm **sở hữu cấu trúc lưu trữ** để nói về collection tự quản lý các liên kết phần tử của nó, chẳng hạn một ArrayList mới được tạo từ dữ liệu nguồn.

Đây không phải quyền sở hữu độc quyền được hệ thống kiểu Java kiểm tra. Một collection vẫn có thể có nhiều biến cùng trỏ tới nó; một cấu trúc có các liên kết riêng vẫn có thể dùng chung các object phần tử.

**View (khung nhìn)** cung cấp một bề mặt truy cập dựa trên dữ liệu nền. Nó có thể giới hạn miền, đổi cách biểu diễn hoặc hạn chế phép sửa, trong khi vẫn gắn với nguồn.

## 2. Hai tầng dữ liệu: cấu trúc chứa và đối tượng phần tử

### 2.1. Collection quản lý cấu trúc riêng

Một biểu thức như new ArrayList<>(source) tạo một ArrayList quản lý cấu trúc danh sách của nó.

Sau khi sao chép xong trong điều kiện nguồn hợp lệ, thêm hoặc xóa một liên kết trong danh sách nguồn không tự thêm/xóa liên kết tương ứng ở ArrayList mới.

Có thể hình dung:

```text
nguồn ──> cấu trúc danh sách 1 ──> Item A, Item B
bản chép ─> cấu trúc danh sách 2 ──> Item A, Item B
                                     các Item vẫn có thể dùng chung
```

Sự độc lập nằm ở cấu trúc danh sách. Không suy ra Item A và Item B đã được nhân bản.

### 2.2. View dùng nguồn để thực hiện thao tác

Một view thường giữ quan hệ tới dữ liệu nền và chuyển các thao tác thích hợp về nguồn thay vì giữ một tập phần tử độc lập.

Ví dụ Collections.unmodifiableList(source) cho một bề mặt đọc danh sách nguồn. Source còn thay đổi hợp lệ thì người đọc view có thể nhìn thấy thay đổi ấy; phép sửa qua view bị chặn theo hợp đồng.

```text
biến source ────────> danh sách nguồn ──> Item A, Item B
biến view ──> lớp view ────────┘
```

Các view có điều kiện sử dụng riêng. Không hiểu sơ đồ thành mọi thay đổi nguồn đều hợp lệ cho mọi view, đặc biệt với List.subList.

Phân biệt collection quản lý lưu trữ và view được giới thiệu trong [Collection Java 17, phần View Collections](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html).

### 2.3. Phép gán tham chiếu còn đơn giản hơn một view

Nếu viết alias = source thì hai biến cùng trỏ tới một đối tượng. Không có collection mới và không có lớp view mới được tạo chỉ bởi phép gán.

Một view có thể là object khác với nguồn mà vẫn chia sẻ dữ liệu nền. Vì vậy kết quả so == khác nhau không chứng minh hai cấu trúc độc lập.

Equals cũng không chứng minh độc lập. Hai collection có thể có cùng dữ liệu ở một thời điểm nhưng khác hoàn toàn chính sách chia sẻ.

### 2.4. Kiểu List/Set/Collection không ghi hết chính sách sở hữu

Một biến List có thể giữ ArrayList, subList, lớp bao chỉ đọc, danh sách gắn với mảng hoặc kết quả List.copyOf.

Chữ List không nói kết quả là bản sao, có cho thêm hay không, hoặc có gắn với một nguồn khác không.

Cần đọc hợp đồng của phương thức tạo/trả kết quả. Những từ như backed by, view, copy và unmodifiable trong tài liệu có ý nghĩa khác nhau.

## 3. Ví dụ 1: alias, view, bản sao cấu trúc và phần tử dùng chung

Lưu thành **StructureOwnerAndView.java**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StructureOwnerAndView {
    static final class Line {
        final String code;
        int quantity;

        Line(String code, int quantity) {
            this.code = code;
            this.quantity = quantity;
        }

        @Override
        public String toString() {
            return code + ":" + quantity;
        }
    }

    public static void main(String[] args) {
        Line first = new Line("A", 1);
        List<Line> owner = new ArrayList<>(List.of(first, new Line("B", 2)));
        List<Line> alias = owner;
        List<Line> view = Collections.unmodifiableList(owner);
        List<Line> copy = new ArrayList<>(owner);
        List<Line> snapshot = List.copyOf(owner);

        System.out.println("alias is owner: " + (alias == owner));
        System.out.println("view is owner: " + (view == owner));
        owner.add(new Line("C", 3));
        System.out.println("view after owner add: " + view);
        System.out.println("copy after owner add: " + copy);
        System.out.println("snapshot after owner add: " + snapshot);

        first.quantity = 9;
        System.out.println("owner after element edit: " + owner);
        System.out.println("copy after element edit: " + copy);
        System.out.println("snapshot after element edit: " + snapshot);

        copy.remove(0);
        System.out.println("copy after its remove: " + copy);
        System.out.println("owner after copy remove: " + owner);
        try {
            view.add(new Line("D", 4));
        } catch (UnsupportedOperationException expected) {
            System.out.println("view add: rejected");
        }
        try {
            snapshot.clear();
        } catch (UnsupportedOperationException expected) {
            System.out.println("snapshot clear: rejected");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 StructureOwnerAndView.java
java StructureOwnerAndView
```

Stdout:

```text
alias is owner: true
view is owner: false
view after owner add: [A:1, B:2, C:3]
copy after owner add: [A:1, B:2]
snapshot after owner add: [A:1, B:2]
owner after element edit: [A:9, B:2, C:3]
copy after element edit: [A:9, B:2]
snapshot after element edit: [A:9, B:2]
copy after its remove: [B:2]
owner after copy remove: [A:9, B:2, C:3]
view add: rejected
snapshot clear: rejected
```

Các ví dụ trong bài được biên dịch với --release 17 và chạy trên OpenJDK 24.0.2. Cờ --release chọn mức ngôn ngữ/API và bytecode đích, không thay phiên bản JVM thực thi.

### 3.1. Alias là cùng đối tượng, còn view khác đối tượng vẫn gắn nguồn

`alias == owner` cho true vì chỉ có thêm một biến trỏ tới danh sách owner.

`view != owner` trong ví dụ này, nhưng khi owner thêm C, view vẫn đọc được C. Việc có object view khác không tạo dữ liệu độc lập.

Đừng dùng một phép kiểm tra nhận dạng object để thay cho hợp đồng chia sẻ dữ liệu.

### 3.2. Copy và snapshot giữ riêng tập liên kết đã lấy

Biến copy được tạo bằng constructor ArrayList; snapshot được tạo bằng List.copyOf. Cả hai được tạo trước khi C được thêm vào owner.

Owner thêm C không làm copy hoặc snapshot tự có C. Xóa A khỏi copy cũng không xóa A khỏi owner.

Tên snapshot ở đây nói về tập liên kết đã lấy trong điều kiện ví dụ không có cập nhật đồng thời. Nó không hứa đã sao chép sâu mọi dữ liệu hoặc thực hiện một giao dịch đọc nguyên tử trong mọi chương trình.

### 3.3. Sửa Line A tác động tầng phần tử

Đối tượng first được cả owner, copy và snapshot cùng tham chiếu. Đổi quantity của nó thành 9 làm cả ba cùng in A:9.

Đó không phải việc các danh sách liên tục sao chép trạng thái cho nhau. Chúng đang đọc cùng một object Line.

Nếu muốn không chia sẻ dữ liệu khả biến của phần tử, cần một mô hình bất biến hoặc một chính sách sao chép phần tử riêng.

### 3.4. Không cho sửa qua kết quả chưa đủ để phân loại view hay bản sao

Lời gọi `view.add` bị từ chối vì lớp bao chỉ đọc.

Lời gọi `snapshot.clear` cũng bị từ chối, nhưng snapshot không phản ánh phần tử C được thêm vào owner sau lần tạo.

Hai kết quả cùng chặn thao tác sửa vẫn khác quan hệ với cấu trúc nguồn. Tài liệu [Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)) và [List.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)) công bố những hợp đồng khác nhau ấy.

## 4. View theo miền và view gắn với mảng

### 4.1. SubList là một miền của List nguồn

List.subList(from, to) trả view từ chỉ số from, có tính phần tử ấy, tới to, không tính phần tử ở to.

Nó không mặc định chép các phần tử trong miền sang một ArrayList riêng. Nếu nguồn hỗ trợ, các phép sửa qua subList có thể tác động chính danh sách nguồn.

Một thao tác như subList(...).clear có thể xóa cả miền trong danh sách gốc. Đây là công dụng của view khi người gọi chủ động muốn tác động nguồn.

### 4.2. Giới hạn quan trọng của subList

Theo hợp đồng [List.subList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#subList(int,int)), thay đổi không cấu trúc được phản ánh giữa hai cách nhìn.

Nhưng nếu danh sách nền bị sửa cấu trúc theo đường khác với subList trả về, ngữ nghĩa của subList có thể trở thành không được xác định. Sửa cấu trúc gồm thay đổi kích thước hoặc những thay đổi làm hoạt động duyệt không còn đúng.

Ví dụ dùng full.set để thay một phần tử và dùng window.remove/add/clear qua chính view. Nó không thêm trực tiếp vào full rồi tiếp tục mặc định window còn hợp lệ.

Không viết một quy tắc “thêm vào nguồn thì subList luôn cập nhật như danh sách độc lập”. Cũng không đóng đinh ConcurrentModificationException như kết quả API bắt buộc của mọi trường hợp dùng sai view.

Nếu cần giữ một miền độc lập với những thay đổi cấu trúc về sau của nguồn, hãy chép miền ấy trong lúc dữ liệu còn hợp lệ.

### 4.3. Arrays.asList là view có kích thước cố định gắn với mảng

Arrays.asList(array) tạo một List gắn với mảng được truyền vào. Set phần tử qua List có thể đổi ô mảng; gán ô mảng có thể được nhìn thấy qua List.

Nó không cho tăng/giảm số phần tử như một ArrayList thông thường. Cố định kích thước không đồng nghĩa cấm thay thế phần tử.

Các quyền này được mô tả trong [Arrays.asList Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

### 4.4. Ví dụ 2: sửa một miền và một mảng qua view

Lưu thành **RangeAndArrayViews.java**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RangeAndArrayViews {
    public static void main(String[] args) {
        List<String> full = new ArrayList<>(
                List.of("zero", "one", "two", "three", "four"));
        List<String> window = full.subList(1, 4);
        List<String> copy = new ArrayList<>(window);

        window.set(1, "TWO");
        System.out.println("full after window set: " + full);
        full.set(1, "ONE");
        System.out.println("window after full set: " + window);

        window.remove(0);
        window.add("extra");
        System.out.println("window after its edits: " + window);
        System.out.println("full after window edits: " + full);
        window.clear();
        System.out.println("full after window clear: " + full);
        System.out.println("window size: " + window.size());
        System.out.println("copy remains: " + copy);

        String[] names = {"Lan", "Mai"};
        List<String> arrayView = Arrays.asList(names);
        names[0] = "Linh";
        System.out.println("view after array edit: " + arrayView);
        arrayView.set(1, "MAI");
        System.out.println("array after view set: " + Arrays.toString(names));
        try {
            arrayView.add("An");
        } catch (UnsupportedOperationException expected) {
            System.out.println("array view add: rejected");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 RangeAndArrayViews.java
java RangeAndArrayViews
```

Stdout:

```text
full after window set: [zero, one, TWO, three, four]
window after full set: [ONE, TWO, three]
window after its edits: [TWO, three, extra]
full after window edits: [zero, TWO, three, extra, four]
full after window clear: [zero, four]
window size: 0
copy remains: [one, two, three]
view after array edit: [Linh, Mai]
array after view set: [Linh, MAI]
array view add: rejected
```

### 4.5. Đọc kết quả theo nguồn thật sự bị tác động

Biến window ban đầu gồm one,two,three. `window.set(1,"TWO")` đổi phần tử two trong full; full.set(1,"ONE") lại được nhìn qua window.

Các thao tác remove/add qua window làm thay đổi đúng miền trong full. `window.clear` xóa miền hiện tại, nên full chỉ còn zero,four.

Copy vẫn giữ one,two,three vì đã lấy các liên kết trước các thao tác ấy. Ví dụ dùng String để tập trung vào cấu trúc, không có thay đổi trường khả biến của phần tử.

Ở phần mảng, names[0] đổi thành Linh và được nhìn qua arrayView. `arrayView.set` đổi ô thứ hai thành MAI. Add bị từ chối vì view không quản lý một mảng mới có thể tự tăng kích thước.

## 5. Các collection view của Map dựa trên ánh xạ nguồn

### 5.1. KeySet, values và entrySet không phải ba bản dữ liệu độc lập

Map.keySet trả Set view của khóa; values trả Collection view của giá trị; entrySet trả Set view của các ánh xạ.

Thêm một ánh xạ vào map có thể được nhìn thấy qua các view đã lấy. Khi view cho phép xóa, xóa qua view tác động ánh xạ trong map.

Chỉ có một khóa chưa đủ thông tin tạo value mới. Vì vậy không suy keySet.add được hỗ trợ chỉ vì kiểu trả về là Set.

Các hợp đồng backed view và quyền thêm/xóa được quy định trong [Map Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

### 5.2. Entry còn là một bề mặt sửa khác

Một Map.Entry nhận trong lúc duyệt một map cho phép cập nhật có thể hỗ trợ setValue để sửa value của ánh xạ.

Cần đọc hợp đồng về thời gian kết nối của entry tới map và quyền sửa của triển khai. Không giữ một entry tùy ý qua mọi lần sửa map rồi mặc định kết nối luôn còn hợp lệ.

Trong ví dụ dưới, setValue được thực hiện ngay trong lần duyệt entrySet của LinkedHashMap, không dùng entry sau một thao tác sửa cấu trúc bên ngoài lần duyệt.

### 5.3. Ví dụ 3: view xóa ánh xạ và giới hạn của lớp bao tập entry

Lưu thành **MapViewsAndEntryPermissions.java**:

```java
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class MapViewsAndEntryPermissions {
    public static void main(String[] args) {
        Map<String, Integer> source = new LinkedHashMap<>();
        source.put("alpha", 1);
        source.put("beta", 2);
        source.put("gamma", 3);
        Set<String> keys = source.keySet();
        Collection<Integer> values = source.values();
        Set<Map.Entry<String, Integer>> entries = source.entrySet();
        Map<String, Integer> copy = new LinkedHashMap<>(source);

        source.put("delta", 4);
        System.out.println("keys after map put: " + keys);
        keys.remove("beta");
        values.remove(3);
        System.out.println("map after view removals: " + source);

        for (Map.Entry<String, Integer> entry : entries) {
            if (entry.getKey().equals("alpha")) {
                entry.setValue(10);
                break;
            }
        }
        System.out.println("map after entry setValue: " + source);
        try {
            keys.add("new-key");
        } catch (UnsupportedOperationException expected) {
            System.out.println("key view add: rejected");
        }

        Set<Map.Entry<String, Integer>> setOnlyWrapper =
                Collections.unmodifiableSet(entries);
        for (Map.Entry<String, Integer> entry : setOnlyWrapper) {
            if (entry.getKey().equals("alpha")) {
                entry.setValue(11);
                break;
            }
        }
        System.out.println("map after set-only entry edit: " + source);

        Map<String, Integer> readOnlyMap = Collections.unmodifiableMap(source);
        try {
            for (Map.Entry<String, Integer> entry : readOnlyMap.entrySet()) {
                entry.setValue(99);
                break;
            }
        } catch (UnsupportedOperationException expected) {
            System.out.println("unmodifiable map entry edit: rejected");
        }
        source.put("epsilon", 5);
        System.out.println("read-only map after source put: " + readOnlyMap);
        System.out.println("copy remains: " + copy);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 MapViewsAndEntryPermissions.java
java MapViewsAndEntryPermissions
```

Stdout:

```text
keys after map put: [alpha, beta, gamma, delta]
map after view removals: {alpha=1, delta=4}
map after entry setValue: {alpha=10, delta=4}
key view add: rejected
map after set-only entry edit: {alpha=11, delta=4}
unmodifiable map entry edit: rejected
read-only map after source put: {alpha=11, delta=4, epsilon=5}
copy remains: {alpha=1, beta=2, gamma=3}
```

### 5.4. Xóa qua key/value view xóa trên map

Sau khi map thêm delta, view keys đã lấy trước đó cũng có delta.

`keys.remove("beta")` bỏ ánh xạ beta=2. `values.remove(3)` bỏ ánh xạ gamma=3 trong dữ liệu mẫu này.

`entry.setValue` thay alpha thành 10. Bản copy được tạo trước đó vẫn giữ alpha=1,beta=2,gamma=3 vì nó có tập liên kết ánh xạ riêng.

LinkedHashMap được dùng để thứ tự in trong ví dụ ổn định theo thứ tự chèn. Không suy mọi Map đều có thứ tự duyệt ấy.

### 5.5. Bọc Set chỉ đọc không tự làm các entry bên trong chỉ đọc

Biến setOnlyWrapper là Collections.unmodifiableSet(entries). Nó chặn những thao tác sửa tập qua bề mặt Set, nhưng không tự biến mỗi phần tử Map.Entry thành một object không sửa được.

Trong lần duyệt này, entry.setValue(11) vẫn thay alpha ở map nguồn.

Đây là biểu hiện cụ thể của quy tắc “không sửa collection” khác “không sửa object phần tử”. Với entry, sửa object ấy còn có thể là đường cập nhật map.

Nếu mục tiêu là cung cấp một map không cho sửa qua chính map và các collection view của nó, Collections.unmodifiableMap cung cấp hợp đồng phù hợp hơn. Ví dụ cho thấy setValue từ entrySet của lớp bao map này bị từ chối.

Tài liệu [Collections.unmodifiableMap Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableMap(java.util.Map)) mô tả việc chặn sửa trực tiếp và qua các view.

### 5.6. Map chỉ đọc vẫn có thể đọc dữ liệu nguồn mới

Source còn được giữ và tiếp tục put epsilon. Biến readOnlyMap nhìn thấy ánh xạ mới vì nó là view, không phải bản sao độc lập.

Nếu cần một ảnh chụp không đổi về tập ánh xạ khi nguồn thay đổi, phải tạo một cấu trúc ánh xạ riêng theo chính sách mong muốn rồi áp quyền sửa phù hợp.

Các object key/value vẫn cần được xét ở tầng dữ liệu phần tử. Một lớp bao map không tự sao chép sâu chúng.

## 6. Ba trục phân loại phải được đọc độc lập

| Kết quả được tạo | Quan hệ cấu trúc với nguồn | Phép sửa qua kết quả | Phần tử có thể dùng chung? |
| --- | --- | --- | --- |
| alias = source | Cùng một đối tượng | Theo chính đối tượng ấy | Có |
| new ArrayList<>(source) | Danh sách riêng sau khi chép | Theo ArrayList | Có |
| Collections.unmodifiableList(source) | View gắn nguồn | Không cho sửa List qua view | Có |
| source.subList(from, to) | View miền, có điều kiện hợp lệ riêng | Các phép tùy chọn mà nguồn hỗ trợ | Có |
| Arrays.asList(array) | View gắn mảng | Thay phần tử được; tăng/giảm kích thước không được | Có |
| List.copyOf(source) | Không phản ánh sửa cấu trúc nguồn sau lần lấy | Không cho sửa List kết quả | Có |
| map.keySet() | View của ánh xạ map | Xóa nếu được hỗ trợ; không thêm khóa bằng add | Dùng các khóa của map |

Bảng không thay hợp đồng chi tiết của từng view, đặc biệt giới hạn sửa cấu trúc ở subList hoặc sửa map trong lúc duyệt.

### 6.1. Sửa được không đồng nghĩa sở hữu riêng

SubList sửa được vẫn là view. Arrays.asList thay phần tử được vẫn dựa vào mảng. KeySet xóa được vẫn tác động map nguồn.

Việc gọi một phép sửa thành công chỉ chứng minh phép đó được hỗ trợ trong điều kiện hiện tại.

### 6.2. Không sửa được chưa có nghĩa là snapshot hoặc bất biến sâu

Một view chỉ đọc có thể phản ánh sửa từ nguồn. Một List.copyOf có tập liên kết ổn định với sửa cấu trúc nguồn nhưng vẫn giữ phần tử khả biến dùng chung.

Không có một từ duy nhất thay cho cả quyền sửa, quan hệ chia sẻ và độ sâu sao chép.

### 6.3. Không dựa vào việc factory cấp phát object mới

List.copyOf công bố hành vi kết quả; API có thể tái sử dụng kết quả không sửa được thích hợp thay vì bắt buộc tạo object mới cho mọi lời gọi.

Điều cần dựa vào là kết quả không phản ánh những thay đổi cấu trúc về sau của Collection nguồn theo hợp đồng. Không kiểm tra object phải khác bằng != để chứng minh một factory đã thực hiện đúng.

“Sở hữu cấu trúc” trong thiết kế không phải một lời hứa về cách JVM cấp phát bộ nhớ bên trong mọi phương thức tạo collection.

### 6.4. Chính sách dữ liệu đích cũng có thể khác

List.copyOf từ chối phần tử null. Constructor ArrayList có thể chép một danh sách chứa null.

Một Set đích còn có tính duy nhất; một cấu trúc sorted có quy tắc thứ tự riêng. Sao chép sang một kiểu khác không tự bảo toàn mọi đặc tính của nguồn.

Hãy chọn kiểu đích theo dữ liệu và yêu cầu sử dụng, không chỉ theo mong muốn “tạo bản sao”.

## 7. Quyền sở hữu ở ranh giới API

### 7.1. Nhận dữ liệu đầu vào

Nếu một đối tượng giữ trực tiếp List mà bên gọi truyền vào, bên gọi có thể còn một biến tham chiếu và tiếp tục sửa List ấy.

Khai báo trường giữ List là final chỉ ngăn gán trường sang đối tượng khác. Nó không tự ngăn bên ngoài thêm/xóa phần tử ở collection đang được chia sẻ.

Nếu đối tượng cần giữ tập dữ liệu ổn định của riêng nó, hãy sao chép đầu vào theo chính sách rõ ràng trước khi lưu. Nếu dữ liệu cần bất biến sâu, còn phải chọn kiểu phần tử hoặc cách chép phần tử phù hợp.

Nếu đối tượng chủ động chia sẻ một nguồn thay đổi theo thời gian, cũng có thể giữ view/tham chiếu, nhưng đó phải là một hợp đồng được công bố.

### 7.2. Trả dữ liệu đầu ra

| Ý nghĩa muốn công bố | Cách trả cần đánh giá |
| --- | --- |
| Bên gọi được sửa chính trạng thái nội bộ | Trả tham chiếu hoặc view sửa được có chủ đích |
| Bên gọi chỉ đọc nhưng cần theo dữ liệu nội bộ mới | View chỉ đọc của nguồn |
| Bên gọi được sửa kết quả riêng mà không đổi cấu trúc nội bộ | Bản sao cấu trúc sửa được |
| Bên gọi giữ tập liên kết riêng không sửa được qua kết quả | Kết quả sao chép không sửa được phù hợp |
| Bên gọi không được tác động cả dữ liệu phần tử nội bộ | Phần tử bất biến hoặc chính sách sao chép sâu |

Không gọi một getter là “an toàn” chỉ vì kiểu trả về là List hoặc vì nó đã bọc unmodifiableList. Cần nói an toàn đối với thao tác và quan hệ chia sẻ nào.

### 7.3. Bọc một bản sao riêng có thể kết hợp hai mục tiêu

Collections.unmodifiableList(new ArrayList<>(source)) kết hợp việc lấy các liên kết vào một danh sách riêng với việc chặn sửa List qua kết quả.

Nếu không để lộ thêm tham chiếu tới danh sách riêng ấy, các lần thêm/xóa source về sau không tự đổi tập liên kết trong kết quả.

Đó vẫn là sao chép nông đối với các object phần tử. Và quá trình đọc nguồn để chép vẫn cần diễn ra trong điều kiện nguồn hợp lệ.

List.copyOf là một lựa chọn khác với hợp đồng riêng, trong đó có việc không nhận null. Hai lựa chọn không thay thế nhau vô điều kiện cho mọi dữ liệu.

### 7.4. Sao chép sâu phải được định nghĩa theo miền dữ liệu

Một Line có thể chứa tham chiếu tới sản phẩm, lịch sử hoặc các phần tử con khác. Tạo một Line mới chưa chắc đã tách mọi dữ liệu phía dưới.

Cần quyết định thành phần nào dùng chung được, thành phần nào phải sao chép và có cần giữ quan hệ giữa các đối tượng hay không.

Với một mô hình dữ liệu bất biến phù hợp, chia sẻ phần tử có thể là lựa chọn đúng và đơn giản hơn việc nhân bản đồ thị đối tượng.

Không dùng serialization hay clone một cách máy móc rồi coi đã giải quyết mọi vấn đề quyền sở hữu.

## 8. Vòng đời, chi phí và các điều kiện sử dụng

### 8.1. View có thể giữ quan hệ tới nguồn lâu hơn dự tính

Một view nhỏ có thể còn giữ liên hệ tới một cấu trúc nguồn lớn trong triển khai cụ thể. Giữ view không mặc định tách vòng đời lưu trữ của phần đang nhìn khỏi toàn bộ nguồn.

Khi cần giữ riêng một miền lâu dài mà không phụ thuộc cấu trúc gốc, sao chép miền đó có thể là lựa chọn phù hợp.

Không gọi mọi view là rò rỉ bộ nhớ; cần xem đồ thị tham chiếu và thời gian sử dụng thực tế.

### 8.2. Chép có chi phí nhưng cũng cung cấp tính độc lập có giá trị

Một cấu trúc riêng cần lưu các liên kết của chính nó và đọc dữ liệu để khởi tạo. Với nguồn lớn, điều này có thể có chi phí đáng kể.

View giúp tránh chủ động chép toàn bộ dữ liệu khi mục tiêu là chia sẻ có kiểm soát. Nhưng dùng view để “tiết kiệm” mà phá vỡ yêu cầu kết quả phải ổn định là thay đổi ý nghĩa API.

Không khẳng định mọi view tạo trong O(1), mọi thao tác view có cùng độ phức tạp, hoặc mọi bản sao có cùng chi phí. Đọc hợp đồng và triển khai thích hợp khi hiệu năng là yêu cầu.

### 8.3. View và bản sao không tự tạo an toàn luồng

Một view của ArrayList không làm nguồn trở thành một cấu trúc cập nhật đồng thời an toàn.

Một bản sao được tạo trong lúc nguồn đang bị sửa cũng không tự là một ảnh chụp nguyên tử. Việc duyệt để chép cần tuân hợp đồng của nguồn và cơ chế nhất quán đang dùng.

Nếu cần một phiên bản ổn định để xử lý, hãy phối hợp các bên sửa/đọc hoặc công bố các phiên bản dữ liệu ổn định theo một mô hình phù hợp.

### 8.4. Không sửa nguồn theo đường làm view mất điều kiện hợp lệ

Với subList, cần đặc biệt tránh sửa cấu trúc trực tiếp trên danh sách nền rồi tiếp tục sử dụng view như không có gì thay đổi.

Với map view, sửa cấu trúc map trong một lần duyệt phải tuân những đường được phép như iterator.remove; không dựa vào việc chưa thấy ngoại lệ trong một lần chạy.

Một collection chứa dữ liệu riêng cũng vẫn có hợp đồng iterator và đồng thời của nó. Sở hữu cấu trúc riêng không tự miễn trừ các quy tắc duyệt.

## 9. Cách đọc tài liệu và kiểm tra quan hệ chia sẻ

### 9.1. Tìm những cam kết quan sát được

Khi gặp một API trả Collection/List/Set/Map, hãy tìm:

1. Kết quả là view hay bản chép?
2. Nguồn nền là gì?
3. Thay đổi nào được phản ánh theo chiều nào?
4. Các phép sửa nào được hỗ trợ?
5. Có điều kiện làm view mất hiệu lực không?
6. Phần tử được dùng chung hay được sao chép theo mức nào?
7. Thứ tự, null và quy tắc dữ liệu của kết quả là gì?

Đây là cách đọc hợp đồng sử dụng, không phải suy tên lớp nội bộ hoặc dùng reflection để đoán.

### 9.2. Viết phép kiểm tra đủ phân biệt

Chỉ so dữ liệu ngay sau khi tạo chưa phân biệt được một view với một bản sao có cùng nội dung.

Các kiểm tra có ý nghĩa gồm:

- Sửa hợp lệ nguồn rồi quan sát kết quả có phản ánh không.
- Sửa qua kết quả nếu được hỗ trợ rồi quan sát nguồn.
- Xóa một liên kết ở bản sao rồi xác nhận nguồn vẫn giữ liên kết riêng.
- Sửa một object phần tử để nhận diện chia sẻ ở tầng phần tử.
- Kiểm tra các quyền khác nhau: set, thêm, xóa, cập nhật entry.
- Kiểm tra những điều kiện dữ liệu như null nếu lựa chọn copy thay đổi chúng.

Không cố tạo các kết quả đã vượt điều kiện hợp đồng rồi coi một stdout hoặc ngoại lệ tình cờ là đặc tả của API.

### 9.3. Dùng phần tử đơn giản trước, phần tử khả biến sau

String hoặc số giúp nhìn rõ việc thêm/xóa/thay vị trí vì không có một trường nghiệp vụ khả biến cần theo dõi.

Sau đó thêm một kiểu như Line để kiểm tra tầng phần tử dùng chung. Nếu trộn cả hai vấn đề ngay từ đầu, rất dễ kết luận sai rằng bản sao cấu trúc “không hoạt động”.

Các ví dụ trong bài cố ý tách những giai đoạn đó để từng kết luận có bằng chứng rõ.

## 10. Những nhầm lẫn phổ biến

| Nhận định | Cách hiểu đúng |
| --- | --- |
| Khác object nghĩa là không dùng chung dữ liệu | Một view khác object vẫn có thể gắn nguồn |
| Cùng dữ liệu ban đầu nghĩa là cùng chính sách sở hữu | View và copy có thể có cùng nội dung tại lúc tạo |
| Mutable nghĩa tự có dữ liệu riêng | Nhiều view sửa được và sửa xuyên về nguồn |
| Unmodifiable nghĩa không bao giờ thay đổi | Nguồn hoặc phần tử bên trong vẫn có thể thay đổi |
| List.copyOf là sao chép sâu | Các object phần tử vẫn có thể được dùng chung |
| Final List là List bất biến | Final chỉ giữ tham chiếu trường không bị gán lại |
| SubList là ArrayList mới của một đoạn | Nó là view miền có điều kiện hợp lệ riêng |
| SubList luôn tiếp tục đúng sau mọi lần thêm vào full | Sửa cấu trúc ngoài view có thể làm ngữ nghĩa view không còn xác định |
| Arrays.asList là ArrayList tăng kích thước được | Nó là List cố định kích thước gắn với mảng |
| Map.keySet là một Set độc lập | Nó là view của khóa trong các ánh xạ map |
| Bọc entrySet bằng unmodifiableSet bảo vệ toàn bộ map | Các entry bên trong vẫn cần xét quyền setValue |
| Một bản sao đọc từ nguồn đang sửa là ảnh chụp nguyên tử | Cần một cơ chế nhất quán phù hợp |
| Có chữ copy nghĩa luôn phải tạo object khác | Factory có thể tái sử dụng kết quả hợp lệ theo hợp đồng |
| Có cấu trúc riêng nghĩa sở hữu độc quyền mọi phần tử | Java không tự áp mô hình sở hữu độc quyền ấy |

## 11. Bài tập tự kiểm tra

### Bài 1. Hai tên khác nhau có tạo hai collection không?

Sau alias = source, xóa phần tử qua alias có tác động source không?

**Gợi ý trả lời:** Có nếu thao tác được đối tượng ấy hỗ trợ. Hai biến cùng trỏ một đối tượng, không có bản sao do phép gán.

### Bài 2. View khác object có dữ liệu riêng không?

Một unmodifiableList khác object với ArrayList nguồn. Nguồn thêm một phần tử hợp lệ, view đọc được phần tử ấy. Điều gì được chứng minh?

**Gợi ý trả lời:** Khác nhận dạng object không tạo độc lập cấu trúc. Kết quả là view gắn với nguồn.

### Bài 3. Không add được đã đủ biết là snapshot chưa?

Hai kết quả cùng ném UnsupportedOperationException khi add. Có thể kết luận chúng đều không phản ánh sửa ở nguồn không?

**Gợi ý trả lời:** Không. Quyền sửa qua kết quả và quan hệ với nguồn là hai trục khác nhau.

### Bài 4. Vì sao copy vẫn thấy quantity mới?

Danh sách được chép bằng new ArrayList<>(source). Một Line chung bị sửa quantity. Việc bản sao cũng thấy số mới có chứng minh cấu trúc vẫn gắn nguồn không?

**Gợi ý trả lời:** Không. Nó cho thấy cùng tham chiếu phần tử được chia sẻ; cấu trúc danh sách vẫn có thể độc lập.

### Bài 5. SubList.clear xóa ở đâu?

Một subList hợp lệ của ArrayList nguồn gọi clear. Có nên mong danh sách gốc giữ nguyên cả miền không?

**Gợi ý trả lời:** Không. Phép xóa qua view tác động miền tương ứng ở danh sách nguồn khi được hỗ trợ.

### Bài 6. Sửa full ngoài window có luôn hợp lệ không?

Đã tạo window = full.subList(...), sau đó full.add thêm một phần tử rồi tiếp tục dùng window. Có thể coi mọi kết quả về sau đều được hợp đồng bảo đảm không?

**Gợi ý trả lời:** Không. Sửa cấu trúc danh sách nền ngoài đường subList có thể làm ngữ nghĩa của view không còn được xác định.

### Bài 7. List gắn với mảng có sửa được không?

Arrays.asList(array) không cho add. Có suy ra set cũng phải bị từ chối không?

**Gợi ý trả lời:** Không. Nó có kích thước cố định nhưng hỗ trợ thay phần tử và phản ánh thay đổi với mảng theo hợp đồng.

### Bài 8. Xóa một khóa qua keySet làm gì?

Map sửa được có một keySet view. Keys.remove("x") thành công. Ánh xạ x trong map có còn không?

**Gợi ý trả lời:** Không. Xóa qua view khóa bỏ ánh xạ tương ứng trong map.

### Bài 9. Vì sao unmodifiableSet(entrySet) chưa đủ?

Trong một LinkedHashMap, tập entry bị bọc unmodifiableSet nhưng entry trong lần duyệt vẫn hỗ trợ setValue. Có thể sửa value của map qua đường ấy không?

**Gợi ý trả lời:** Có trong trường hợp ấy. Cần dùng hợp đồng bảo vệ map và các view của nó, như unmodifiableMap, nếu đó là mục tiêu.

### Bài 10. Chọn gì cho kết quả riêng nhưng không cho sửa List?

Muốn kết quả không phản ánh thêm/xóa về sau ở nguồn, và bên nhận không được sửa cấu trúc qua kết quả. Chỉ unmodifiableList(source) đã đủ chưa?

**Gợi ý trả lời:** Chưa. Cần kết hợp chính sách lấy dữ liệu riêng với quyền sửa phù hợp, chẳng hạn List.copyOf nếu các điều kiện dữ liệu của nó phù hợp. Vẫn phải xét các object phần tử.

## 12. Tài liệu đối chiếu

- [Collection — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html): view, collection không sửa được và chính sách đồng bộ.
- [ArrayList — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html): danh sách quản lý cấu trúc riêng và constructor chép Collection.
- [List.subList — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#subList(int,int)): view miền và điều kiện sửa cấu trúc của nguồn.
- [List.copyOf — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)): kết quả không phản ánh sửa cấu trúc nguồn, null và khả năng tái sử dụng.
- [Collections.unmodifiableList — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)): view chỉ đọc của List.
- [Collections.unmodifiableMap — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableMap(java.util.Map)): chặn sửa map trực tiếp và qua các collection view.
- [Map — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): keySet, values, entrySet và hợp đồng của các view ánh xạ.
- [Arrays.asList — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)): List cố định kích thước gắn với mảng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-copies-002">subList và thay đổi cấu trúc qua view hoặc nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# subList và thay đổi cấu trúc qua view hoặc nguồn

## 1. Một khoảng nhìn lên danh sách, không phải bản sao

Khi cần xử lý một đoạn trong List, subList cho phép làm việc với khoảng đó qua một đối tượng List khác. Điểm quan trọng là **List được trả về là view được hỗ trợ bởi danh sách nguồn**. Nó không tự tạo một danh sách chứa độc lập.

Với nguồn A,B,C,D,E, lời gọi subList(1,4) nhìn thấy B,C,D:

```text
Chỉ số nguồn:   0   1   2   3   4
Nguồn:         A   B   C   D   E
                   └───────┘
View:              B   C   D
Chỉ số view:       0   1   2
```

Nếu nguồn cho sửa và view còn hợp lệ, set trong view thay tham chiếu ở vị trí tương ứng của nguồn. Thêm, xóa hoặc clear qua view cũng có thể thay đổi cấu trúc nguồn.

Ngược lại, tự thêm/xóa trực tiếp trên nguồn rồi tiếp tục dùng view cũ là tình huống khác: hợp đồng List không còn quy định hành vi của view đó khi cấu trúc nguồn bị sửa ngoài đường view.

Bài này tập trung vào quan hệ giữa nguồn, view, các thay đổi cấu trúc và bản sao. Các chương trình đầy đủ dùng Java 17 trở lên, với ArrayList làm nguồn chính để có hành vi kiểm chứng rõ ràng.

### 1.1. Các từ cần nắm trước

| Từ trong bài | Ý nghĩa |
|---|---|
| Nguồn, backing list | Danh sách hỗ trợ dữ liệu cho view |
| View | Đối tượng cho phép nhìn/thao tác trên một phần dữ liệu của nguồn |
| Khoảng nửa mở | Bao gồm đầu fromIndex, không bao gồm cuối toIndex |
| Thay đổi không thuộc cấu trúc | Ví dụ set thay phần tử tại vị trí có sẵn, không thêm/xóa vị trí |
| Thay đổi cấu trúc | Thêm/xóa làm đổi kích thước hoặc thay đổi gây ảnh hưởng tính đúng của phép duyệt |
| Bản sao cấu trúc chứa | List có vùng chứa riêng, không tiếp tục bám vào các vị trí của nguồn |
| Sao chép nông | Sao chép tham chiếu phần tử; đối tượng phần tử vẫn có thể được chia sẻ |
| Fail-fast | Phát hiện một số thay đổi không phù hợp và báo lỗi sớm; không phải cơ chế bảo đảm đồng thời |

## 2. Khoảng và chỉ số của subList

### 2.1. from được lấy, to không được lấy

Với `source.subList(fromIndex, toIndex)`, điều kiện khoảng là:

```text
0 <= fromIndex <= toIndex <= source.size()
Số phần tử ban đầu của view = toIndex - fromIndex
```

subList(1,4) lấy các chỉ số nguồn 1,2,3. Nó không lấy phần tử ở chỉ số 4.

subList(0,source.size()) nhìn toàn danh sách. subList(i,i) là khoảng rỗng hợp lệ khi i nằm từ 0 đến size. subList(size,size) cũng hợp lệ; đó là khoảng rỗng tại cuối nguồn.

Các điều kiện này nói về chỉ số, không phải tìm theo giá trị. subList không tự lọc mọi phần tử có giá trị nằm giữa hai số.

### 2.2. View đánh chỉ số lại từ 0

view.get(0) là phần tử đầu của khoảng, không nhất thiết là source.get(0). Trong ví dụ B,C,D, view.get(1) là C tại chỉ số nguồn 2.

Khi chèn vào một view ArrayList còn hợp lệ, vị trí chèn là vị trí tương đối trong view. view.add(0,x) chèn trước phần tử đầu của view; view.add(view.size(),x) chèn ở cuối view, ngay trước phần nguồn đứng sau khoảng nếu có.

Sau khi thêm/xóa qua view, kích thước và các chỉ số trong view thay đổi. Không tiếp tục dùng các chỉ số cũ mà không tính lại.

### 2.3. Không ép view thành một ArrayList độc lập

Kiểu trả về của subList là List<E>. Đối tượng view không có nghĩa là một ArrayList chứa riêng toàn bộ phần tử của khoảng.

Nếu cần một ArrayList độc lập về cấu trúc, tạo bằng constructor nhận Collection, chẳng hạn new ArrayList<>(view), trong lúc view còn hợp lệ. Ép kiểu không thực hiện sao chép và có thể gây ClassCastException.

Hợp đồng khoảng và quan hệ backing được mô tả tại [List.subList — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html).

## 3. Sửa nội dung và sửa cấu trúc qua đường nào?

### 3.1. set thay phần tử tại vị trí có sẵn

Với ArrayList, set thay tham chiếu tại một chỉ số mà không thêm/xóa vị trí. Khi nguồn và view còn hợp lệ:

- view.set(i,x) phản ánh lên vị trí tương ứng của nguồn.
- source.set(j,x) tại một vị trí trong khoảng phản ánh khi đọc view.
- set ngoài khoảng không làm một phần tử ngoài khoảng tự xuất hiện trong view.

Đây là quan hệ chia sẻ vị trí dữ liệu. Nó không có nghĩa view tự tìm lại một tập phần tử theo giá trị sau mọi thay đổi của nguồn.

### 3.2. Thêm/xóa qua view có thể sửa nguồn hợp lệ

Nếu nguồn hỗ trợ, view.add, view.remove và view.clear làm thay đổi nguồn. View đang thực hiện thao tác cập nhật kích thước tương ứng của chính nó.

clear trên view xóa phần hiện đang thuộc view, không xóa toàn bộ nguồn. Vì thế mẫu source.subList(from,to).clear() hữu ích khi cần loại bỏ một khoảng ngay trên nguồn.

Không có quy tắc “view không bao giờ được làm đổi size”. Điều cần xét là nguồn có hỗ trợ thao tác không và thay đổi có được thực hiện qua đường view phù hợp không.

### 3.3. Thêm/xóa trực tiếp trên nguồn là điều kiện khác

Khi backing list bị sửa cấu trúc bằng đường khác ngoài view đang dùng, hợp đồng subList không còn quy định hành vi của view đó. Không tiếp tục dựa vào size, get, vòng lặp hoặc toString của view cũ.

Điều này đúng ngay cả khi:

- Thêm ở cuối nguồn, ngoài khoảng của view.
- Xóa phần tử trước khoảng.
- Sửa qua một view anh em không chồng lấn.
- Xóa rồi thêm lại để size cuối bằng size ban đầu.
- Nguồn và view cùng được thao tác chỉ trong một luồng.

Không coi view là một cửa sổ tự động trượt để giữ cùng from/to hoặc tự tìm lại các đối tượng cũ sau thay đổi cấu trúc nguồn.

### 3.4. Đường đối tượng quan trọng hơn tên biến

Nếu alias và view trỏ đến cùng một đối tượng view, gọi alias.add vẫn là gọi qua view đó. Đổi tên biến không làm thao tác trở thành sửa nguồn bên ngoài.

Ngược lại, tạo hai subList riêng từ cùng nguồn là hai đối tượng view có trạng thái theo dõi riêng. Một thay đổi cấu trúc hợp lệ qua view thứ nhất không làm mọi view thứ hai tự đồng bộ và tiếp tục hợp lệ.

## 4. Chương trình 1: set, thêm, xóa và clear qua view

Lưu vào `SubListBasicsDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class SubListBasicsDemo {
    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("A", "B", "C", "D", "E"));
        List<String> view = source.subList(1, 4);
        System.out.println("initialView=" + view);
        System.out.println("oldViewValue=" + view.set(1, "C2"));
        System.out.println("sourceAfterViewSet=" + source);
        source.set(1, "B2");
        System.out.println("viewAfterSourceSet=" + view);
        view.add(1, "X");
        System.out.println("sourceAfterViewAdd=" + source);
        System.out.println("viewAfterAdd=" + view);
        System.out.println("removedFromView=" + view.remove(2));
        System.out.println("sourceAfterViewRemove=" + source);
        view.clear();
        System.out.println("sourceAfterViewClear=" + source);
        System.out.println("clearedView=" + view);
        view.add("Y");
        System.out.println("sourceAfterReusingEmptyView=" + source);
        System.out.println("viewAfterReuse=" + view);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SubListBasicsDemo.java
java SubListBasicsDemo
```

Stdout:

```text
initialView=[B, C, D]
oldViewValue=C
sourceAfterViewSet=[A, B, C2, D, E]
viewAfterSourceSet=[B2, C2, D]
sourceAfterViewAdd=[A, B2, X, C2, D, E]
viewAfterAdd=[B2, X, C2, D]
removedFromView=C2
sourceAfterViewRemove=[A, B2, X, D, E]
sourceAfterViewClear=[A, E]
clearedView=[]
sourceAfterReusingEmptyView=[A, Y, E]
viewAfterReuse=[Y]
```

### 4.1. Hai đường set nhìn cùng vị trí

view ban đầu là B,C,D. view.set(1,C2) trả giá trị cũ C và làm nguồn thành A,B,C2,D,E.

source.set(1,B2) thay vị trí B trong nguồn. View đọc được B2,C2,D. Hai thao tác không thêm/xóa vị trí, nên ví dụ không làm view mất hiệu lực.

### 4.2. Chèn và xóa theo chỉ số của view

view.add(1,X) chèn X trước phần tử C2 của view. Nguồn thành A,B2,X,C2,D,E, còn view là B2,X,C2,D.

view.remove(2) xóa C2, không phải phần tử có chỉ số nguồn 2. Giá trị trả về xác nhận removedFromView là C2; nguồn còn A,B2,X,D,E.

### 4.3. clear chỉ xóa khoảng và view có thể còn được dùng

Lúc clear, view chứa B2,X,D. Xóa qua view làm nguồn còn A,E và view trở thành rỗng.

view.add(Y) sau đó vẫn hợp lệ trong ví dụ ArrayList này. Nó chèn tại vị trí của khoảng đã rỗng, làm nguồn thành A,Y,E. View không bị mất hiệu lực chỉ vì nó tự clear qua thao tác được hỗ trợ.

Đừng nhầm trường hợp này với source.clear() rồi tiếp tục dùng view cũ: clear trực tiếp trên nguồn là một thay đổi cấu trúc bên ngoài view.


## 5. View mất bảo đảm không có nghĩa luôn báo cùng một ngoại lệ

### 5.1. Hợp đồng và dấu hiệu phát hiện là hai tầng khác nhau

List.subList quy định rằng hành vi của view không còn được xác định nếu backing list bị sửa cấu trúc qua đường khác. Đây là điều người viết mã phải tránh.

ArrayList thường phát hiện một số trường hợp bằng cơ chế fail-fast và ném ConcurrentModificationException. Ngoại lệ này là dấu hiệu đã vi phạm cách sử dụng, không phải quyền cho phép sửa nguồn tùy ý rồi chờ thư viện “tự sửa view”.

Không biến một lần chạy chưa ném ngoại lệ thành bằng chứng view vẫn hợp lệ. Cũng không hứa mọi triển khai List sẽ ném đúng ngoại lệ tại cùng một phương thức sau thay đổi bên ngoài.

[ArrayList — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html) nêu giới hạn bảo đảm của fail-fast. [AbstractList — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html) mô tả một cơ chế triển khai view theo dõi kích thước, độ lệch và số lần sửa đổi dự kiến.

### 5.2. Không chỉ nhìn size hiện tại

Một view có thể theo dõi lịch sử thay đổi cấu trúc, không chỉ so sánh kích thước cuối. Xóa một phần tử rồi thêm một phần tử khác để size trở về số cũ không làm view tự hợp lệ lại.

Ngoài thêm/xóa, cần đọc chính sách của thao tác và lớp cụ thể. Với ArrayList, tài liệu còn tính việc chủ động đổi kích thước mảng chứa vào thay đổi cấu trúc; chỉ so size không đủ làm phép kiểm tra an toàn.

set tại một vị trí có sẵn là trường hợp đã được phân biệt rõ. Đừng mở rộng kết luận “set được” thành “mọi thao tác không đổi size đều không ảnh hưởng view hoặc iterator”.

### 5.3. Sửa qua một view có thể làm view khác lỗi

Giả sử left và right là hai subList riêng của cùng một ArrayList. left.remove có thể là thao tác hợp lệ đối với left và cập nhật nguồn. Nhưng từ phía right, nguồn vừa bị sửa cấu trúc qua đường khác.

Hai khoảng không chồng nhau cũng không loại bỏ vấn đề này. View không phải một thuê bao độc lập tự tính lại mọi độ lệch sau sửa đổi của các view khác.

Tương tự, một iterator của nguồn tạo trước khi view.add vẫn có thể mất hiệu lực. “View vừa sửa còn dùng được” không có nghĩa mọi iterator hay view khác trên cùng nguồn đều dùng tiếp được.

### 5.4. ConcurrentModificationException không đòi phải có hai luồng

Tên ngoại lệ dễ khiến người học nghĩ chỉ đa luồng mới gặp lỗi. Một luồng tạo view, tự sửa nguồn rồi dùng view cũ cũng đủ tạo tình huống không phù hợp.

Vấn đề trước hết là có nhiều đường thao tác lên cùng cấu trúc chứa. Đồng bộ truy cập có thể giải quyết tranh chấp giữa các luồng, nhưng không tự thay đổi quy tắc hợp lệ của view sau sửa cấu trúc nguồn bên ngoài.

## 6. Chương trình 2: các cách làm view hoặc iterator cũ mất hiệu lực

Chương trình này **cố ý minh họa cách dùng không hợp lệ**. Các dòng ConcurrentModificationException bên dưới là kết quả quan sát với ArrayList trên JDK 17.0.15. Đây không phải cam kết rằng mọi List sẽ phát hiện cùng lỗi ở cùng thời điểm.

Mỗi trường hợp độc lập tạo một danh sách mới. Sau khi quan sát lỗi, chương trình không tiếp tục sử dụng view hoặc iterator đã lỗi.

Lưu vào `SubListInvalidationDemo.java`:

```java
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

public class SubListInvalidationDemo {
    static void expectConcurrent(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected ConcurrentModificationException");
        } catch (ConcurrentModificationException observed) {
            System.out.println(label + "=ConcurrentModificationException");
        }
    }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("A", "B", "C", "D"));
        List<String> view = source.subList(1, 3);
        source.add("E");
        System.out.println("sourceAfterOutsideAdd=" + source);
        expectConcurrent("oldViewGet", () -> view.get(0));

        List<String> sameSize = new ArrayList<>(List.of("A", "B", "C", "D"));
        List<String> oldRange = sameSize.subList(1, 3);
        sameSize.remove(0);
        sameSize.add(0, "Z");
        System.out.println("sourceSameSize=" + sameSize.size());
        expectConcurrent("oldRangeSize", () -> oldRange.size());

        List<String> shared = new ArrayList<>(List.of("A", "B", "C", "D", "E", "F"));
        List<String> left = shared.subList(0, 2);
        List<String> right = shared.subList(3, 5);
        left.remove(0);
        System.out.println("sharedAfterLeftRemove=" + shared);
        System.out.println("leftStillUsable=" + left);
        expectConcurrent("rightGet", () -> right.get(0));

        List<String> root = new ArrayList<>(List.of("A", "B", "C"));
        Iterator<String> rootIterator = root.iterator();
        List<String> current = root.subList(1, 3);
        current.add("D");
        System.out.println("currentView=" + current);
        expectConcurrent("oldRootIteratorNext", () -> rootIterator.next());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SubListInvalidationDemo.java
java SubListInvalidationDemo
```

Stdout:

```text
sourceAfterOutsideAdd=[A, B, C, D, E]
oldViewGet=ConcurrentModificationException
sourceSameSize=4
oldRangeSize=ConcurrentModificationException
sharedAfterLeftRemove=[B, C, D, E, F]
leftStillUsable=[B]
rightGet=ConcurrentModificationException
currentView=[B, C, D]
oldRootIteratorNext=ConcurrentModificationException
```

### 6.1. Thêm ngoài khoảng vẫn làm view cũ lỗi

view nhìn khoảng B,C của nguồn A,B,C,D. source.add(E) không chèn vào giữa B,C, nhưng vẫn sửa cấu trúc trực tiếp trên nguồn.

oldViewGet ném ConcurrentModificationException trong môi trường đã kiểm tra. Không có quy tắc cho phép dùng tiếp view chỉ vì E nằm sau khoảng.

### 6.2. Kích thước trở lại 4 không khôi phục view

sameSize ban đầu có 4 phần tử. remove(0) rồi add(0,Z) làm số phần tử cuối vẫn bằng 4. Tuy vậy oldRange được tạo trước chuỗi sửa cấu trúc, nên gọi size trên view đó vẫn bị phát hiện lỗi.

Không sửa chữa một view bằng cách cố khôi phục kích thước hay dữ liệu nguồn về “gần giống ban đầu”. Cần dừng dùng đối tượng view cũ.

### 6.3. View anh em không đồng bộ theo nhau

left ban đầu nhìn A,B, right nhìn D,E. left.remove(0) xóa A khỏi nguồn và left còn B.

leftStillUsable là `[B]` vì chính left thực hiện thao tác. rightGet báo lỗi vì right không phải đường thực hiện thay đổi cấu trúc. Không cần hai khoảng chồng lấn để gặp vấn đề này.

### 6.4. View hợp lệ không cứu iterator nguồn đã tạo trước

rootIterator được tạo trước current.add(D). current view vẫn dùng được và chứa B,C,D sau khi thêm qua nó.

rootIterator.next sau thay đổi bị phát hiện lỗi. Cần xét vòng đời của từng iterator riêng, không chỉ của đối tượng view.

## 7. Cần độc lập thì tạo bản sao khi view còn hợp lệ

### 7.1. Bản sao sửa được

new ArrayList<>(view) thu các phần tử hiện thấy trong view vào một cấu trúc ArrayList mới. Sau đó thêm/xóa trên nguồn không làm bản sao tự thay đổi kích thước hay vị trí theo nguồn.

Ngược lại, add/remove/set trên bản sao không tác động cấu trúc nguồn. Các phần tử được sao chép theo tham chiếu, nên đối tượng bên trong có thể vẫn được chia sẻ.

Tạo bản sao **trước** khi nguồn bị sửa ngoài view. new ArrayList<>(view) không phải công cụ chữa một view đã mất hiệu lực; thao tác sao chép cũng phải đọc view và có thể báo lỗi.

### 7.2. Kết quả không cho sửa

List.copyOf(view) tạo kết quả List không cho thêm, xóa hoặc thay phần tử. Những sửa đổi tiếp theo của collection nguồn không được phản ánh như một view sống lên kết quả này.

Không hứa mỗi lần copyOf luôn cấp một đối tượng hoàn toàn mới: API có thể tận dụng một List không cho sửa phù hợp. Điều cần dựa vào là hợp đồng nội dung kết quả, không phải so sánh danh tính đối tượng.

List.copyOf không nhận phần tử null. Đây là điều kiện cần xét nếu khoảng nguồn có null. new ArrayList<>(view) có thể dùng khi cần giữ null và muốn bản sao sửa được.

Nguồn đối chiếu: [List.copyOf — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html).

### 7.3. Không cho sửa không đồng nghĩa sao chép sâu

Nếu phần tử là StringBuilder, cả nguồn và bản sao nông có thể trỏ đến cùng StringBuilder. Thay cấu trúc List và thay trạng thái đối tượng phần tử là hai việc khác nhau.

Nếu cần toàn bộ dữ liệu độc lập sâu, phải định nghĩa cách nhân bản theo kiểu phần tử. Không thể suy ra cách sao chép sâu tổng quát chỉ từ List<E>.

### 7.4. Bọc unmodifiable không cắt quan hệ backing

Collections.unmodifiableList(view) chặn sửa qua wrapper, nhưng vẫn dựa trên view bên dưới. Nó không tạo bản sao, cũng không làm một view cũ trở lại hợp lệ sau sửa cấu trúc nguồn.

Nếu yêu cầu là giữ một phần dữ liệu lâu hơn vòng đời nguồn đang thay đổi, chọn bản sao phù hợp thay vì chỉ bọc quyền đọc.

## 8. Chương trình 3: nguồn thay đổi nhưng bản sao vẫn độc lập về cấu trúc

Lưu vào `SubListCopyDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SubListCopyDemo {
    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("A", "B", "C", "D"));
        List<String> view = source.subList(1, 3);
        List<String> copy = new ArrayList<>(view);
        List<String> frozen = List.copyOf(view);
        source.add("E");
        System.out.println("sourceAfterAdd=" + source);
        System.out.println("copyAfterSourceAdd=" + copy);
        System.out.println("frozenAfterSourceAdd=" + frozen);
        copy.set(0, "B2");
        copy.add("X");
        System.out.println("editedCopy=" + copy);
        System.out.println("sourceAfterCopyEdit=" + source);
        try {
            frozen.set(0, "changed");
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println("frozenSet=UnsupportedOperationException");
        }

        StringBuilder value = new StringBuilder("shared");
        List<StringBuilder> builders = new ArrayList<>(List.of(value));
        List<StringBuilder> shallow = new ArrayList<>(builders.subList(0, 1));
        shallow.get(0).append("!");
        System.out.println("sourceMutableValue=" + builders.get(0));
        System.out.println("sameElement=" + (builders.get(0) == shallow.get(0)));

        List<String> nullable = new ArrayList<>(Arrays.asList("A", null, "C"));
        System.out.println("nullableCopy=" + new ArrayList<>(nullable.subList(1, 3)));
        try {
            List.copyOf(nullable.subList(1, 3));
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            System.out.println("nullableCopyOf=NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SubListCopyDemo.java
java SubListCopyDemo
```

Stdout:

```text
sourceAfterAdd=[A, B, C, D, E]
copyAfterSourceAdd=[B, C]
frozenAfterSourceAdd=[B, C]
editedCopy=[B2, C, X]
sourceAfterCopyEdit=[A, B, C, D, E]
frozenSet=UnsupportedOperationException
sourceMutableValue=shared!
sameElement=true
nullableCopy=[null, C]
nullableCopyOf=NullPointerException
```

### 8.1. Hai kết quả được tạo trước khi sửa nguồn

view nhìn B,C. copy và frozen đều được tạo trong lúc view còn hợp lệ. source.add(E) sau đó sửa cấu trúc nguồn trực tiếp, nên chương trình không dùng lại view.

copyAfterSourceAdd và frozenAfterSourceAdd đều là `[B, C]`. Chúng không trở thành các khoảng tự động cập nhật của nguồn.

### 8.2. Quyền sửa kết quả khác nhau

copy.set và copy.add làm copy thành `[B2, C, X]`; nguồn vẫn A,B,C,D,E. frozen.set bị từ chối bằng UnsupportedOperationException.

Tên biến frozen chỉ giúp đọc ví dụ. Đặc tính không cho sửa đến từ List.copyOf; nó không đóng băng sâu mọi đối tượng phần tử.

### 8.3. Cùng StringBuilder thì cùng nhìn thay đổi

shallow nhận tham chiếu StringBuilder đã có trong nguồn. Append qua shallow làm sourceMutableValue thành shared!, còn sameElement là true.

Điều này không mâu thuẫn với việc cấu trúc chứa đã độc lập. Nếu thay tham chiếu ở một vị trí của bản sao, nguồn không bị set theo; nếu sửa chính đối tượng chung, những nơi trỏ đến nó cùng quan sát được trạng thái đó.

### 8.4. Null ảnh hưởng lựa chọn kiểu sao chép

Khoảng nullable.subList(1,3) có null,C. Constructor ArrayList giữ cả hai vị trí, nên nullableCopy là `[null, C]`.

List.copyOf cùng khoảng ném NullPointerException. Không dùng copyOf như một phép lọc null tự động; nếu chính sách nghiệp vụ cần loại hoặc xử lý null, phải làm rõ điều đó trước khi tạo kết quả.


## 9. View chỉ có các quyền thao tác mà nguồn hỗ trợ

### 9.1. ArrayList sửa được khác danh sách kích thước cố định

subList không tạo thêm quyền thay đổi cấu trúc nếu nguồn không hỗ trợ.

Arrays.asList trả List có kích thước cố định được hỗ trợ bởi mảng. Nó cho set nhưng không cho thêm/xóa làm đổi số phần tử. View của nó cũng không thể biến thành một khoảng thêm/xóa tự do.

Vì vậy cần tách hai câu hỏi:

1. Có được thay phần tử tại vị trí hiện có không?
2. Có được làm đổi số vị trí không?

Một nguồn có thể trả lời “có” cho set và “không” cho add/remove. Nguồn đối chiếu: [Arrays.asList — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html).

### 9.2. List không cho sửa

List.of trả List không cho thêm, xóa hoặc thay phần tử. subList của nó cũng không mở thêm quyền sửa.

Với Collections.unmodifiableList(backing), wrapper chặn sửa qua wrapper. Nếu backing là ArrayList và có tham chiếu khác gọi set hợp lệ trên nguồn, view đọc vẫn có thể thấy thay đổi đó.

Không mô tả mọi đối tượng “không cho sửa” là dữ liệu bất biến sâu hoặc bản sao độc lập. Cần biết nó đến từ factory tạo nội dung không cho sửa hay từ wrapper còn dựa trên nguồn.

Hợp đồng wrapper được mô tả tại [Collections.unmodifiableList — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html).

### 9.3. Lỗi quyền sửa khác lỗi chỉ số và lỗi view cũ

| Điều kiện trong các ví dụ | Loại vấn đề |
|---|---|
| add vào view của Arrays.asList | Nguồn không hỗ trợ đổi kích thước |
| set qua view không cho sửa | Không có quyền thay phần tử qua đường đó |
| from âm hoặc to vượt size | Khoảng không hợp lệ |
| from lớn hơn to | Hai cận không đúng thứ tự |
| get dùng chỉ số bằng size của view | Chỉ số phần tử vượt khoảng |
| Dùng view cũ sau sửa cấu trúc nguồn bên ngoài | Đã vi phạm hợp đồng backing của subList |

Không catch tất cả lỗi rồi quy về “subList không dùng được”. Phải xác định sai khoảng, sai quyền hay sai vòng đời quan hệ với nguồn.

## 10. Chương trình 4: quyền sửa và giới hạn chỉ số

Lưu vào `SubListCapabilitiesDemo.java`. Phần kiểm tra cận dùng ArrayList, nên tên ngoại lệ chi tiết bên dưới được đối chiếu với hợp đồng của lớp này.

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SubListCapabilitiesDemo {
    static void expect(String label, Class<? extends RuntimeException> type,
                       Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected " + type.getSimpleName());
        } catch (RuntimeException observed) {
            if (!type.isInstance(observed)) {
                throw observed;
            }
            System.out.println(label + "=" + type.getSimpleName());
        }
    }

    public static void main(String[] args) {
        List<String> fixed = Arrays.asList("A", "B", "C");
        List<String> fixedView = fixed.subList(1, 3);
        fixedView.set(0, "B2");
        System.out.println("fixedAfterSet=" + fixed);
        expect("fixedViewAdd", UnsupportedOperationException.class,
                () -> fixedView.add("D"));
        expect("fixedViewClear", UnsupportedOperationException.class,
                () -> fixedView.clear());

        List<String> constantView = List.of("A", "B", "C").subList(1, 3);
        expect("constantViewSet", UnsupportedOperationException.class,
                () -> constantView.set(0, "B2"));

        List<String> backing = new ArrayList<>(List.of("A", "B", "C"));
        List<String> readOnly = Collections.unmodifiableList(backing).subList(0, 2);
        expect("readOnlyRemove", UnsupportedOperationException.class,
                () -> readOnly.remove(0));
        backing.set(0, "A2");
        System.out.println("readOnlyAfterSourceSet=" + readOnly);

        ArrayList<String> checked = new ArrayList<>(List.of("A", "B", "C"));
        expect("negativeFrom", IndexOutOfBoundsException.class,
                () -> checked.subList(-1, 2));
        expect("pastEnd", IndexOutOfBoundsException.class,
                () -> checked.subList(0, 4));
        expect("reversedBounds", IllegalArgumentException.class,
                () -> checked.subList(2, 1));
        List<String> one = checked.subList(1, 2);
        expect("viewIndexPastEnd", IndexOutOfBoundsException.class,
                () -> one.get(1));
        System.out.println("emptyEndRange=" + checked.subList(3, 3));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SubListCapabilitiesDemo.java
java SubListCapabilitiesDemo
```

Stdout:

```text
fixedAfterSet=[A, B2, C]
fixedViewAdd=UnsupportedOperationException
fixedViewClear=UnsupportedOperationException
constantViewSet=UnsupportedOperationException
readOnlyRemove=UnsupportedOperationException
readOnlyAfterSourceSet=[A2, B]
negativeFrom=IndexOutOfBoundsException
pastEnd=IndexOutOfBoundsException
reversedBounds=IllegalArgumentException
viewIndexPastEnd=IndexOutOfBoundsException
emptyEndRange=[]
```

### 10.1. Nguồn cố định kích thước vẫn cho set

fixedView.set(0,B2) thay B trong nguồn, nên fixedAfterSet là `[A, B2, C]`. Nhưng add và clear cần đổi kích thước, nên bị từ chối bằng UnsupportedOperationException.

Không đánh đồng fixed-size với “không thể sửa bất kỳ điều gì”. set được còn add không được là hai điều kiện nhất quán của nguồn này.

### 10.2. View không cho sửa vẫn có thể thấy source.set

constantView đến từ List.of nên set bị từ chối. readOnly đến từ wrapper không cho sửa nên remove cũng bị từ chối.

backing.set(0,A2) thực hiện qua nguồn ArrayList, không qua readOnly. Đó là thay đổi không thêm/xóa vị trí, nên readOnly đọc được `[A2, B]` trong ví dụ. Wrapper không làm phần dữ liệu nhìn thấy trở thành một ảnh chụp cố định.

### 10.3. Khoảng nguồn khác chỉ số trong view

Với ArrayList checked có size bằng 3:

- subList(-1,2) và subList(0,4) vượt cận.
- subList(2,1) đảo thứ tự cận.
- subList(1,2) chỉ có một phần tử, nên get(1) vượt chỉ số của view.
- subList(3,3) là một khoảng rỗng hợp lệ ở cuối.

Các lỗi cận được thử trên dữ liệu chưa bị thay đổi cấu trúc ngoài view. Điều đó giúp tách lỗi chỉ số khỏi lỗi view đã mất hiệu lực.

## 11. Dùng view để thao tác một khoảng ngay trên nguồn

### 11.1. Thuật toán nhận List có thể nhận view

Khi một thuật toán nhận List và chỉ cần làm việc trên một đoạn, có thể truyền subList thay cho cả nguồn. Ví dụ, Collections.sort trên view hợp lệ sắp xếp đoạn đó, không tự sắp toàn bộ danh sách nguồn.

Đó là một cách mở rộng thao tác theo khoảng mà không cần viết lại thuật toán với from/to cho từng phương thức.

Điều kiện vẫn giữ nguyên: nguồn hỗ trợ những thao tác mà thuật toán cần; view còn hợp lệ; không có đường khác làm thay đổi cấu trúc nguồn ngoài sự phối hợp của thuật toán.

### 11.2. Xóa nhiều phần tử qua chính view

clear hoặc removeIf qua view sửa được có thể loại bỏ một phần dữ liệu nguồn. Các phần tử ngoài view không tự trở thành đối tượng của bộ lọc.

Khi thuật toán đang duyệt, không tự gọi thêm các thao tác cấu trúc từ bên ngoài cơ chế sửa được phép của phép duyệt. Một vòng for-each có iterator riêng; việc gọi view.remove tùy ý bên trong vòng đó không đồng nghĩa đang xóa qua iterator.

Nếu cần vừa duyệt vừa thêm/xóa tại vị trí con trỏ, một ListIterator do view cung cấp có thể phù hợp, miễn tuân theo vòng đời next/remove/add/set và khả năng của nguồn.

### 11.3. View lồng nhau cũng có vòng đời riêng

Có thể gọi subList trên một view. Chỉ số của view con được tính theo view cha, không trực tiếp theo nguồn gốc.

Trong ví dụ ArrayList tiếp theo, sửa qua view con cập nhật các view cha trong chuỗi đó và nguồn. Nhưng điều này không mở rộng thành bảo đảm mọi view anh em hoặc mọi view tạo riêng từ nguồn cũng còn hợp lệ.

Sau khi một view cha bị sửa cấu trúc bằng đường khác ngoài view con đang giữ, không tiếp tục dựa vào view con cũ. Khi có nhiều tầng view, cần quản lý rõ tầng nào còn được dùng sau mỗi thao tác.

## 12. Chương trình 5: sắp xếp khoảng, lọc, view con và ListIterator

Lưu vào `SubListRangeOperationsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

public class SubListRangeOperationsDemo {
    public static void main(String[] args) {
        List<Integer> source = new ArrayList<>(List.of(9, 8, 7, 6, 5, 4));
        List<Integer> middle = source.subList(1, 5);
        Collections.sort(middle);
        System.out.println("sourceAfterRangeSort=" + source);
        middle.removeIf(value -> value % 2 == 0);
        System.out.println("sourceAfterRangeFilter=" + source);
        System.out.println("middleAfterFilter=" + middle);

        List<Integer> outer = source.subList(1, 3);
        List<Integer> inner = outer.subList(1, 2);
        inner.clear();
        System.out.println("sourceAfterInnerClear=" + source);
        System.out.println("outerAfterInnerClear=" + outer);
        System.out.println("innerAfterClear=" + inner);

        List<String> words = new ArrayList<>(List.of("A", "B", "C", "D"));
        List<String> selected = words.subList(1, 3);
        ListIterator<String> cursor = selected.listIterator();
        System.out.println("cursorNext=" + cursor.next());
        cursor.remove();
        cursor.add("X");
        System.out.println("cursorNextAgain=" + cursor.next());
        cursor.set("C2");
        System.out.println("wordsAfterCursorEdits=" + words);
        System.out.println("selectedAfterCursorEdits=" + selected);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SubListRangeOperationsDemo.java
java SubListRangeOperationsDemo
```

Stdout:

```text
sourceAfterRangeSort=[9, 5, 6, 7, 8, 4]
sourceAfterRangeFilter=[9, 5, 7, 4]
middleAfterFilter=[5, 7]
sourceAfterInnerClear=[9, 5, 4]
outerAfterInnerClear=[5]
innerAfterClear=[]
cursorNext=B
cursorNextAgain=C
wordsAfterCursorEdits=[A, X, C2, D]
selectedAfterCursorEdits=[X, C2]
```

### 12.1. Chỉ khoảng giữa được sắp xếp và lọc

middle nhìn 8,7,6,5 trong nguồn 9,8,7,6,5,4. Sắp xếp qua middle làm nguồn thành `[9, 5, 6, 7, 8, 4]`: 9 và 4 ngoài khoảng không tham gia việc sắp xếp đó.

middle.removeIf loại số chẵn trong view, nên nguồn còn `[9, 5, 7, 4]`, middle còn `[5, 7]`. Đây là thay đổi cấu trúc thực hiện qua chính middle.

### 12.2. View con clear làm nguồn đổi

outer được tạo mới từ khoảng 5,7; inner nhìn phần tử 7 trong outer. inner.clear làm nguồn còn `[9, 5, 4]`, outer còn `[5]`, inner rỗng.

Những kết quả đó được kiểm tra trên chuỗi view ArrayList cụ thể. middle là một view tạo riêng trước đó, nên chương trình không dùng lại middle sau thay đổi cấu trúc qua inner. Không suy ra nó vẫn hợp lệ chỉ vì lúc trước nó cũng nhìn 5,7.

### 12.3. Sửa qua ListIterator của view

selected ban đầu nhìn B,C trong nguồn A,B,C,D. cursor.next lấy B, cursor.remove xóa B; cursor.add(X) chèn X tại vị trí con trỏ. Lần next tiếp theo lấy C, rồi cursor.set(C2) thay nó.

Nguồn thành `[A, X, C2, D]`, selected thành `[X, C2]`. Mã dùng các thao tác của chính cursor trong suốt phép duyệt; không xen một lệnh selected.add hoặc words.remove từ đường khác.

Không cần thêm một bản sao chỉ để thực hiện thao tác theo khoảng ngay trên nguồn, nếu chủ đích là sửa nguồn và vòng đời view được kiểm soát.

## 13. Chọn view hoặc bản sao theo thời gian sử dụng

### 13.1. View phù hợp khi muốn thao tác trực tiếp một khoảng

Ví dụ, cần xóa một khoảng một lần trong danh sách sửa được. Đoạn sau là minh họa đặt trong phương thức đã có items, start, end hợp lệ; nó không phải tệp Java độc lập:

```java
items.subList(start, end).clear();
```

Tác dụng là xóa ngay khoảng đó khỏi items. Không giữ thêm một view lâu dài nếu chỉ cần một thao tác khoảng duy nhất.

Một view ngắn hạn cũng phù hợp để tìm kiếm hay sắp xếp riêng một đoạn, khi tất cả đường sửa nguồn được phối hợp.

### 13.2. Bản sao phù hợp khi cần kết quả sống độc lập với cấu trúc nguồn

Nếu trả một phần dữ liệu cho nơi khác lưu giữ, trong khi nguồn còn tiếp tục thêm/xóa, nên cân nhắc một bản sao theo yêu cầu quyền sửa.

Đoạn minh họa dưới đây cần đặt trong phương thức có source, start, end và import ArrayList/List:

```java
List<String> result = new ArrayList<>(source.subList(start, end));
```

Chọn List.copyOf cho kết quả không cho sửa khi các phần tử không null. Cả hai lựa chọn vẫn cần giải quyết sao chép sâu riêng nếu trạng thái của phần tử có thể thay đổi.

Không có lựa chọn đúng chỉ theo tên “subList nhanh hơn”. Quyết định phải xét việc người nhận có muốn sửa nguồn không, nguồn có còn bị sửa ngoài view không và kết quả cần tồn tại bao lâu.

### 13.3. Đừng giữ một view nhỏ với giả định đã cắt rời nguồn lớn

View có thể còn giữ quan hệ tham chiếu tới cấu trúc nguồn. Một view chỉ vài phần tử không đồng nghĩa phần dữ liệu ngoài khoảng chắc chắn đã không còn được giữ sống.

Nếu cần một tập phần tử lưu lâu độc lập về cấu trúc chứa, tạo bản sao là cách thể hiện rõ ý định hơn. Dù vậy, các phần tử được chọn vẫn có thể tham chiếu tiếp đến những đối tượng khác; sao chép nông không tự giải quyết mọi quan hệ bộ nhớ trong đồ thị đối tượng.

### 13.4. Xử lý sau khi đã sửa cấu trúc nguồn bên ngoài

Dừng dùng view cũ. Nếu vẫn cần một khoảng của nguồn hiện tại, tính lại cận và tạo view mới hoặc bản sao mới từ nguồn hiện tại.

Cần phân biệt “cùng chỉ số hiện tại” với “cùng nhóm đối tượng cũ”. Sau khi nguồn bị thêm/xóa, giữ nguyên hai con số from/to có thể chọn dữ liệu khác trước.

Không catch ConcurrentModificationException rồi tiếp tục đọc view cũ. Cũng không catch rồi cố copy chính view đó để chữa lỗi: bản sao phải đọc dữ liệu hợp lệ mới có ý nghĩa.

## 14. Lỗi phổ biến và tiêu chí kiểm tra

| Lỗi | Hậu quả hoặc cách hiểu sai | Cách xử lý |
|---|---|---|
| Nghĩ subList là bản sao | Sửa view vô tình sửa nguồn | Chọn bản sao nếu cần cấu trúc độc lập |
| Nhầm toIndex được lấy vào khoảng | Lệch một phần tử | Dùng khoảng nửa mở [from,to) |
| Dùng chỉ số nguồn để remove trong view | Xóa sai phần tử hoặc vượt cận | Tính chỉ số tương đối của view |
| source.add ở ngoài khoảng rồi dùng view cũ | Hợp đồng view không còn được bảo đảm | Dừng dùng view, tạo kết quả mới từ nguồn hiện tại |
| Xóa rồi thêm lại cho size bằng cũ | Lịch sử sửa cấu trúc vẫn đã thay đổi | Không coi size là cách khôi phục view |
| Sửa qua view thứ nhất rồi dùng tiếp view anh em | View thứ hai không tự đồng bộ hợp lệ | Phối hợp vòng đời hoặc dùng bản sao |
| Nghĩ chỉ đa luồng mới có ConcurrentModificationException | Bỏ sót lỗi do nhiều tham chiếu trong cùng luồng | Kiểm tra mọi đường sửa cấu trúc |
| Mong chắc chắn luôn có ngoại lệ ngay khi sửa nguồn | Fail-fast không phải bảo đảm cho mọi trường hợp | Tránh cách dùng sai theo hợp đồng |
| Bọc unmodifiable để làm view độc lập | Wrapper vẫn dựa trên dữ liệu bên dưới | Dùng copy phù hợp nếu cần độc lập |
| Dùng List.copyOf cho khoảng có null | NullPointerException | Chọn chính sách null rõ ràng hoặc ArrayList khi phù hợp |
| Coi bản sao nông là sao chép sâu | Sửa đối tượng phần tử vẫn ảnh hưởng nơi khác | Thiết kế cách sao chép phần tử |
| add/remove trên view của Arrays.asList | Nguồn không hỗ trợ đổi kích thước | Tạo ArrayList nếu cần danh sách sửa kích thước được |
| Đổi cấu trúc nguồn qua view rồi dùng iterator nguồn cũ | Iterator có thể mất hiệu lực | Quản lý vòng đời iterator riêng |
| Vừa duyệt bằng iterator vừa sửa qua đường khác | Vi phạm chính sách sửa trong phép duyệt | Dùng đúng thao tác của iterator hoặc đổi cách xử lý |

Một bộ kiểm tra có ích nên bao gồm khoảng đầu/giữa/cuối, khoảng rỗng, nguồn chỉ có một phần tử, set từ hai phía, thêm/xóa/clear qua view, sửa nguồn bên ngoài, view anh em, các quyền sửa khác nhau và bản sao nông.

Khi kiểm tra dữ liệu nguồn sau mỗi thao tác, đừng vô tình đọc lại một view đã mất hiệu lực chỉ để in tiến độ. Ngay cả toString của một view cũ cũng không phải phép quan sát được miễn khỏi hợp đồng.

## 15. Tự kiểm tra và thực hành

### 15.1. Câu hỏi với đáp án

1. **subList(1,4) lấy bao nhiêu phần tử ban đầu?** Ba phần tử ở chỉ số nguồn 1,2,3.
2. **View có chỉ số bắt đầu từ fromIndex không?** Không. View đánh chỉ số lại từ 0.
3. **view.set thay nguồn không?** Có, khi thao tác được nguồn hỗ trợ và view còn hợp lệ; vị trí tương ứng trong nguồn được thay.
4. **source.set trong khoảng có thể phản ánh lên view không?** Có. Với ArrayList, thay phần tử tại vị trí có sẵn không phải thêm/xóa cấu trúc.
5. **view.add có luôn gây lỗi vì làm đổi size nguồn không?** Không. Trên nguồn hỗ trợ, thêm qua view có thể là thao tác hợp lệ và cập nhật kích thước view.
6. **source.add ở cuối ngoài khoảng có bảo đảm view cũ vẫn dùng được không?** Không. Đó vẫn là sửa cấu trúc ngoài đường view.
7. **Hai view không chồng lấn có thể dùng tiếp độc lập sau một view xóa phần tử không?** Không có bảo đảm như vậy; view còn lại có nguồn bị sửa qua đường khác.
8. **ConcurrentModificationException có chứng minh phải có nhiều luồng không?** Không. Một luồng với nhiều tham chiếu cũng có thể gây tình huống đó.
9. **Copy sau khi view đã lỗi có phải cách sửa không?** Không. Phải tạo bản sao từ dữ liệu còn hợp lệ, thường trước khi nguồn tiếp tục bị sửa cấu trúc.
10. **new ArrayList<>(view) có sao chép sâu StringBuilder không?** Không. Nó sao chép tham chiếu phần tử.
11. **subList(size,size) có hợp lệ không?** Có, đó là khoảng rỗng ở cuối nguồn nếu size được lấy trong ngữ cảnh còn hợp lệ.
12. **View của Arrays.asList có set được nhưng add không được không?** Có. Kích thước cố định khác với cấm mọi thao tác sửa.

### 15.2. Bài tập: tính chỉ số và tác dụng lên nguồn

Tạo ArrayList số 0,1,2,3,4,5; lấy view bằng subList(2,5). Dự đoán rồi chạy view.remove(1), view.add(0,9), view.clear.

Kỳ vọng: view ban đầu là 2,3,4. Xóa chỉ số view 1 làm nguồn còn 0,1,2,4,5. Thêm 9 ở đầu view làm nguồn thành 0,1,9,2,4,5. Clear view sau đó làm nguồn còn 0,1,5.

Giải thích vì sao remove(1) xóa số 3 chứ không phải số 1.

### 15.3. Bài tập: tách lỗi quyền và lỗi vòng đời

Tạo ba nguồn riêng: ArrayList sửa được, Arrays.asList và List.of. Với mỗi nguồn, lấy cùng khoảng không rỗng rồi thử set, add, clear trên view trong các phép thử độc lập.

Với ArrayList khác, tạo view rồi sửa cấu trúc trực tiếp nguồn và ngừng dùng view cũ trong mã đúng. Có thể viết một phép minh họa lỗi riêng để quan sát trên JDK đang dùng, nhưng không biến loại/thời điểm ngoại lệ thành bảo đảm chung cho List.

Kết quả cần trình bày theo hai trục: quyền thao tác của nguồn và tính hợp lệ của view tại thời điểm gọi.

### 15.4. Bài tập: thiết kế kết quả trả về

Một phương thức nhận List<String> và chọn một khoảng để trả về cho nơi gọi lưu lâu. Nguồn sẽ còn được thêm/xóa sau khi phương thức kết thúc.

Hãy chọn giữa subList, new ArrayList<>(subList) và List.copyOf(subList), rồi nêu rõ quyền sửa kết quả và chính sách null. Nếu cần kết quả độc lập về cấu trúc, không trả trực tiếp view.

Đổi kiểu phần tử sang một đối tượng có trạng thái có thể thay đổi. Nêu thêm điều gì còn chưa được giải quyết bởi bản sao nông.

## 16. Nguồn đối chiếu và phạm vi kiểm chứng

- [List — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html): hợp đồng subList, khoảng nửa mở, thay đổi cấu trúc nguồn bên ngoài và copyOf.
- [ArrayList — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html): khả năng sửa, cận subList, thay đổi cấu trúc và giới hạn fail-fast.
- [AbstractList — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractList.html): một cơ chế view dựa trên độ lệch, kích thước và số lần sửa đổi dự kiến.
- [Arrays — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html): List kích thước cố định được hỗ trợ bởi mảng từ asList.
- [Collections — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html): wrapper unmodifiableList và các thuật toán nhận List.

Năm chương trình đầy đủ đã biên dịch bằng `javac --release 17 -encoding UTF-8` và chạy trên Corretto JDK 17.0.15, cho đúng stdout được ghi. Các ngoại lệ ConcurrentModificationException trong chương trình minh họa lỗi là quan sát trên ArrayList của môi trường đó; quy tắc sử dụng đúng vẫn là tránh sửa cấu trúc nguồn bên ngoài view đang dùng. Hai đoạn ở phần lựa chọn view/bản sao được đánh dấu là minh họa, cần đặt trong phương thức có biến và import phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-copies-003">keySet, values, entrySet và các thao tác ghi xuyên về Map</a>

<details>
<summary>Xem chi tiết</summary>

# keySet, values, entrySet và các thao tác ghi xuyên về Map

## 1. View là một cách truy cập dữ liệu đang có

Một map lưu vai trò theo tên người dùng:

~~~text
ada -> READER
lin -> EDITOR
sam -> READER
~~~

Một chức năng chỉ cần tên; một chức năng thống kê vai trò; một chức năng khác cần cả tên và vai trò. Java cung cấp ba góc nhìn (view) của cùng map:

~~~java
Set<K> keySet();
Collection<V> values();
Set<Map.Entry<K, V>> entrySet();
~~~

Đây là hình dạng chữ ký API để đọc hợp đồng, không phải chương trình Java độc lập.

**View không mặc nhiên là bản sao.** Với một map sửa được như `LinkedHashMap`, các đối tượng view gắn với map nguồn: sửa nguồn được phản ánh qua view; các thao tác ghi được hỗ trợ trên view truyền về nguồn. Vì vậy, trả `keySet` cho một hàm khác có thể trao cả quyền xóa ánh xạ, chứ không chỉ đưa ra danh sách tên.

`Map` quy định ba collection view này; thứ tự duyệt phụ thuộc loại map. [Java 17: Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html).

Bài này giải thích những đường ghi xuyên, các thao tác bị từ chối, cách duyệt và sửa đúng hợp đồng, cùng khác biệt giữa view, lớp bọc chỉ đọc và bản sao. Chương trình đầy đủ dùng API tương thích **Java 17 trở lên**.

## 2. Ba loại view khác nhau ở đơn vị phần tử

Với nguồn:

~~~text
map                 {ada=READER, lin=EDITOR, sam=READER}

keySet              [ada, lin, sam]
values              [READER, EDITOR, READER]
entrySet            [ada=READER, lin=EDITOR, sam=READER]
~~~

Dấu ngoặc vuông ở đây biểu diễn trình tự in của ví dụ dùng map liên kết, không có nghĩa cả ba đối tượng đều là `List`.

| View | Kiểu trả về | Phần tử biểu diễn | Có thể trùng theo ý nghĩa thông thường? |
| --- | --- | --- | --- |
| `keySet()` | `Set<K>` | Khóa | Không |
| `values()` | `Collection<V>` | Giá trị của từng ánh xạ | Có |
| `entrySet()` | `Set<Map.Entry<K,V>>` | Cặp khóa–giá trị | Mỗi ánh xạ là một entry |

### 2.1. Vì sao values không phải Set?

Hai khóa `ada` và `sam` cùng có giá trị `READER`. Map vẫn có ba ánh xạ, nên view giá trị cũng có ba phần tử: hai lần xuất hiện của `READER` và một `EDITOR`.

Nếu dùng set giá trị để biểu diễn view ấy, bạn sẽ mất thông tin số lần xuất hiện theo ánh xạ. Khi muốn thống kê số người của mỗi vai trò, không được tự loại trùng trước khi đếm.

### 2.2. Entry xét cả khóa lẫn giá trị

Một `Map.Entry` mô tả một cặp. Entry `ada=READER` khác entry `ada=EDITOR` vì giá trị khác. Điều này quan trọng với `entrySet.contains` hoặc `entrySet.remove`: dùng entry đối chiếu không chỉ là đưa một khóa vào một hình thức viết dài hơn.

Phép bằng của entry chuẩn xét cả khóa và giá trị; vai trò kết nối của một entry với map còn phụ thuộc cách bạn lấy nó. [Java 17: Map.Entry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html).

### 2.3. Không phải mọi view có cùng hợp đồng equals

`keySet` và `entrySet` là set, nên có hợp đồng so sánh theo thành viên của set. `values` chỉ được khai báo là `Collection`, không phải danh sách có vị trí hay set loại trùng.

Đừng dùng `map.values().equals(expectedList)` như một cách tổng quát để kiểm chứng nội dung hoặc thứ tự giá trị. Nếu cần kiểm tra trình tự, hãy tạo một danh sách theo lần duyệt phù hợp. Nếu không quan tâm thứ tự nhưng quan tâm số lần lặp, cần so sánh tần suất, không chuyển thẳng sang set.

`Collection` không tự đặt cùng hợp đồng `equals` như `List` hoặc `Set`. [Java 17: Collection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html).

## 3. Liên kết hai chiều giữa nguồn và view

Có thể giữ view rồi sửa map:

~~~java
Map<String, String> users = new LinkedHashMap<>();
users.put("ada", "READER");
Set<String> names = users.keySet();

users.put("lin", "EDITOR");
System.out.println(names);
~~~

Đây là đoạn minh họa đặt trong một phương thức có import tương ứng. Kết quả đọc qua `names` là `[ada, lin]`. Không cần gọi lại `keySet` để view “tải dữ liệu mới”.

Ngược lại:

~~~java
names.remove("ada");
~~~

Nếu nguồn hỗ trợ xóa, ánh xạ của `ada` biến mất khỏi `users`. Nó không chỉ mất khỏi một collection tên riêng.

Mô hình cần nhớ:

~~~text
                 cùng map nguồn
                      |
        +-------------+-------------+
        |             |             |
      keySet        values       entrySet
        |             |             |
      xóa khóa     xóa giá trị    xóa cặp phù hợp
        +-------------+-------------+
                      |
                 xóa ánh xạ nguồn
~~~

“Ghi xuyên” là việc thao tác qua view làm thay đổi dữ liệu nguồn. Nó không phải ghi ra tệp, cơ sở dữ liệu hay mạng; bài này chỉ nói về các đối tượng Java trong bộ nhớ.

### 3.1. Live view khác iterator đang hoạt động

View có thể tiếp tục phản ánh nguồn qua những lần truy vấn mới. Iterator là một quá trình duyệt cụ thể, có quy tắc riêng khi nguồn bị sửa.

Bạn không thể tạo iterator, thêm một khóa bằng `map.put`, rồi viện lý do “đây là live view” để tiếp tục duyệt tùy ý. Phải tuân hợp đồng iterator và chính sách đồng thời của map.

### 3.2. Việc đọc view không tạo ảnh chụp nguyên tử

Nếu một luồng khác thay map giữa lúc bạn xem kích thước, in khóa rồi in giá trị, ba quan sát có thể không thuộc cùng một trạng thái. Đặc tính liên kết không đồng nghĩa có khóa ngầm cho toàn bộ chuỗi thao tác.

Chương trình trong bài chạy một luồng để làm rõ hành vi. Khi dùng thực tế, quyền sở hữu và chính sách đồng bộ phải được thiết kế ở nơi quản lý nguồn.

## 4. Những thao tác xóa ghi xuyên về map

### 4.1. keySet.remove(k): chỉ cần khớp khóa

Với `{ada=READER, lin=EDITOR, sam=READER}`:

~~~text
keySet.remove("lin")       true
map sau                   {ada=READER, sam=READER}
~~~

Giá trị của `lin` không cần được truyền vào. Trên view sửa được, giá trị trả về `true` nghĩa có phần tử khóa bị xóa, nên ánh xạ ấy cũng bị xóa.

Điểm này hữu ích khi giá trị có thể là `null`. `map.remove(k)` trả giá trị cũ và có thể trả `null` cho cả ánh xạ vắng mặt lẫn ánh xạ từng mang `null`. `keySet.remove(k)` trả cờ về việc xóa khóa, giúp phân biệt trong cách dùng phù hợp.

Cả hai vẫn là thao tác tùy chọn và phải tuân chính sách kiểu/`null` của nguồn.

### 4.2. values.remove(v): xóa một lần xuất hiện

Nếu có hai người mang vai trò `READER`, `values().remove("READER")` xóa **một ánh xạ** có giá trị phù hợp, không xóa hết hai người.

Trên `LinkedHashMap` của chương trình, việc duyệt có thứ tự chèn nên ví dụ cho kết quả cụ thể. Trong mã nhận `Map` bất kỳ, không dùng thao tác này như cách chỉ định danh tính một người. Nếu mục tiêu là xóa `sam`, hãy xóa theo khóa `sam`.

`Collection.remove` xóa một lần xuất hiện phù hợp nếu có; `removeAll` có ý nghĩa khác. [Java 17: Collection.remove](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#remove(java.lang.Object)).

Đừng giả định xóa qua view giá trị chỉ bỏ một giá trị nhưng giữ khóa “không có giá trị”. Một ánh xạ map luôn liên quan đến khóa và giá trị; xóa phần tử view này loại cả ánh xạ tương ứng.

### 4.3. entrySet.remove(entry): phải khớp cặp

Với nguồn chứa `maya=REVIEWER`:

~~~java
entries.remove(Map.entry("maya", "WRONG"));     // false
entries.remove(Map.entry("maya", "REVIEWER"));  // true
~~~

Đây là đoạn minh họa với các khóa/giá trị không `null` và entry view sửa được. Lần thứ nhất giữ nguyên nguồn vì cặp không khớp. Lần thứ hai xóa ánh xạ hiện có.

Nếu muốn xóa khi khóa đang mang đúng một giá trị, `map.remove(key, expectedValue)` cũng diễn đạt ý định rõ. Đừng suy một chuỗi `contains` rồi `remove` qua view là một thao tác nguyên tử trong môi trường đồng thời.

### 4.4. clear: xóa toàn bộ ánh xạ

Với các view có quyền xóa:

~~~java
map.keySet().clear();
map.values().clear();
map.entrySet().clear();
~~~

Mỗi dòng riêng lẻ đều là một đường xóa toàn bộ map. Nếu chạy dòng đầu trước, hai dòng sau chỉ còn gặp nguồn đã rỗng; chúng không nhắm ba bản dữ liệu riêng.

Đây là một lý do quan trọng để không trả view nguồn cho bên gọi khi bạn chỉ muốn cung cấp dữ liệu đọc. Một collection trả về có thể chứa quyền tác động rộng hơn vẻ ngoài của tên phương thức.

### 4.5. removeAll, retainAll và removeIf

Các phép lọc trên view có thể rất thuận tiện:

| Ý định | Cách dùng trên nguồn có quyền xóa |
| --- | --- |
| Xóa các khóa thuộc một danh sách cấm | `map.keySet().removeAll(blockedKeys)` |
| Chỉ giữ các khóa được phép | `map.keySet().retainAll(allowedKeys)` |
| Xóa mọi ánh xạ có giá trị trong nhóm | `map.values().removeAll(rejectedValues)` |
| Xóa theo điều kiện cả khóa và giá trị | `map.entrySet().removeIf(predicate)` |

`retainAll` có nghĩa xóa phần tử không được giữ; nó không chỉ “đánh dấu các phần tử được chọn”. Nếu tập cho phép rỗng, thao tác giữ lại theo tập rỗng có thể làm map rỗng.

`removeIf` xóa các phần tử thỏa điều kiện; cách mặc định dựa trên iterator và thao tác xóa của iterator. Nếu điều kiện ném ngoại lệ sau một số lần xóa, không tự coi các lần xóa trước đã được rollback.

Điều kiện nên tập trung vào quyết định, không đồng thời sửa cấu trúc map qua một đường khác. Ví dụ `entry -> entry.getValue().equals("REVIEWER")` phù hợp khi miền dữ liệu cấm `null`; nếu cho phép `null`, cần so sánh an toàn theo chính sách ấy.

## 5. Vì sao thêm vào view thường bị từ chối?

`keySet` và `values` không đủ thông tin để tạo một ánh xạ mới theo ý nghĩa của map:

- thêm một khóa không nói giá trị phải là gì;
- thêm một giá trị không nói khóa nào sở hữu nó.

Ngay cả `entrySet` có cặp đủ thông tin, hợp đồng view cũng không cung cấp `add` hay `addAll` như một đường thay thế `Map.put`. Các view chuẩn này không hỗ trợ các thao tác thêm ấy.

Với nguồn sửa được trong ví dụ:

~~~java
map.keySet().add("new-user");
map.values().add("READER");
map.entrySet().add(Map.entry("new-user", "READER"));
~~~

Mỗi lệnh được kiểm tra riêng và gây `UnsupportedOperationException`. Muốn thêm hoặc cập nhật ánh xạ, dùng API map phù hợp.

Không nên gọi thử một thao tác không được hỗ trợ với dữ liệu trùng hoặc rỗng rồi kết luận “có thể dùng trong trường hợp này”. Các thao tác tùy chọn có thể bị từ chối ngay cả khi bạn cho rằng chúng không tạo thay đổi.

Cũng không có `set(index, value)` trên `values` vì kiểu trả về là `Collection`, không phải danh sách. Muốn thay giá trị cho một khóa, dùng API map hoặc entry iterator có quyền cập nhật.

## 6. entry.setValue: đường cập nhật giá trị trực tiếp

Khi duyệt `entrySet` của một `LinkedHashMap` sửa được, entry được iterator trả về có thể dùng để cập nhật giá trị của ánh xạ:

~~~java
for (Map.Entry<String, String> entry : users.entrySet()) {
    if (entry.getValue().equals("EDITOR")) {
        entry.setValue("REVIEWER");
    }
}
~~~

Đoạn minh họa này thay `EDITOR` thành `REVIEWER` mà giữ khóa và số ánh xạ. Nó không “đổi biến entry cục bộ”; lời gọi ghi vào giá trị của ánh xạ nguồn nếu được hỗ trợ.

### 6.1. Thay biến value cục bộ khác thay giá trị trong map

Đoạn sau không thay ánh xạ:

~~~java
for (String role : users.values()) {
    role = "REVIEWER";
}
~~~

Nó chỉ gán biến vòng lặp đến chuỗi khác. Biến `role` không phải một ô tham chiếu có thể dùng để thay giá trị đang được map lưu.

Ngược lại, nếu giá trị là một đối tượng có thể sửa như `List`, gọi một phương thức sửa trên chính đối tượng đó có thể làm trạng thái thấy qua map thay đổi. Đó là thay trạng thái phần tử, khác với việc thay ánh xạ đến một đối tượng mới.

### 6.2. setValue là tùy chọn và có vòng đời

Không phải mọi `Map.Entry` đều cho `setValue`. Entry từ `Map.entry` là entry độc lập không sửa được; entry lấy qua một map/view chỉ đọc cũng không phải đường mở quyền cập nhật.

Với entry iterator, kết nối với nguồn gắn với quá trình duyệt. Không giữ entry ấy như một tay nắm lâu dài rồi xóa/thay cấu trúc nguồn qua đường khác và tiếp tục dựa vào nó. Nếu cần lưu một cặp ở thời điểm hiện tại, hãy tạo một entry độc lập phù hợp.

Không sửa khóa của entry qua API này; chỉ có `setValue`. Danh tính khóa và các quy tắc bằng/băm của nó vẫn thuộc hợp đồng map.

### 6.3. Giá trị có thể bị hạn chế

Một map có thể cấm giá trị `null` hoặc có ràng buộc khác. Entry cập nhật không tự bỏ qua ràng buộc của nguồn. Đừng dùng `setValue(null)` như cách xóa ánh xạ: nếu được phép, nó gán giá trị `null` nhưng khóa vẫn tồn tại.

Muốn loại ánh xạ đang duyệt, hãy dùng thao tác xóa đúng hợp đồng.

## 7. Duyệt và xóa: dùng iterator của chính lần duyệt

Cách làm rõ ràng là:

~~~java
var it = users.entrySet().iterator();
while (it.hasNext()) {
    var entry = it.next();
    if (shouldDelete(entry)) {
        it.remove();
    }
}
~~~

Đây là đoạn minh họa cần một phương thức quyết định `shouldDelete` do ứng dụng cung cấp; chưa phải chương trình độc lập.

`Iterator.remove` xóa phần tử vừa được `next` trả về; với entry view, đó là ánh xạ tương ứng. Nó chỉ được gọi đúng trạng thái và tối đa một lần cho mỗi lần `next` theo hợp đồng iterator. [Java 17: Iterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#remove()).

Không viết vòng `for-each` trên `keySet` rồi gọi `map.remove(key)` bên trong đối với các map thông thường và mong quá trình duyệt luôn hợp lệ. Điều kiện xóa có thể đúng, nhưng đường sửa nằm ngoài iterator đang duyệt.

### 7.1. Fail-fast không phải cơ chế đồng bộ

`HashMap` không bảo đảm thứ tự duyệt và không tự đồng bộ; iterator fail-fast dùng để phát hiện một số sửa cấu trúc ngoài quy trình cho phép, không phải bảo đảm mọi tranh chấp đều bị phát hiện. [Java 17: HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).

Không thấy `ConcurrentModificationException` không chứng minh mã sửa map trong vòng lặp là đúng. Cũng đừng dùng một kiểm thử “phải ném ngoại lệ ở đúng dòng này” làm định nghĩa nghiệp vụ cho thao tác sai hợp đồng.

### 7.2. LinkedHashMap được dùng để kiểm chứng stdout có trình tự

Ví dụ đầy đủ chọn `LinkedHashMap` mặc định theo thứ tự chèn để các dòng in có thể dự đoán. Điều đó không áp đặt thứ tự ấy lên mọi `Map`.

Nếu dùng `LinkedHashMap` access-order, một số lời gọi `get` có thể thay trình tự. Vì vậy, khi đã cần cả khóa lẫn giá trị, duyệt entry thường rõ hơn duyệt khóa rồi tra `get` lặp lại. [Java 17: LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

### 7.3. Map đồng thời có chính sách khác, nhưng không tự tạo giao dịch

Một nguồn như `ConcurrentHashMap` có iterator nhất quán yếu và các thao tác đồng thời được mô tả riêng. Nó cấm khóa và giá trị `null`; các quan sát trong khi nguồn đổi không nên bị hiểu là một ảnh chụp nguyên tử của toàn bộ map. [Java 17: ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

Khi chuyển thuật toán sang map đồng thời, phải đọc hợp đồng của nguồn, phép cập nhật và view đang dùng. Không giữ nguyên mọi giả định fail-fast của `HashMap`, cũng không suy rằng cả một phép lọc nhiều mục luôn là một giao dịch.

## 8. null và phép đối chiếu entry

Với một map cho phép `null`, một khóa có thể tồn tại dù giá trị là `null`:

~~~java
Map<String, String> state = new LinkedHashMap<>();
state.put("pending", null);
boolean removed = state.keySet().remove("pending");
~~~

`removed` là `true` vì khóa đã có và bị xóa. Trường hợp này không cần suy từ giá trị cũ `null`.

Muốn tạo entry đối chiếu có giá trị `null`, không dùng `Map.entry("pending", null)` vì factory đó từ chối `null`. Có thể dùng:

~~~java
Map.Entry<String, String> expected =
        new AbstractMap.SimpleImmutableEntry<>("pending", null);
boolean removed = state.entrySet().remove(expected);
~~~

Đây là đoạn minh họa với nguồn đã có ánh xạ phù hợp. `SimpleImmutableEntry` tạo một cặp độc lập và không cho thay giá trị qua `setValue`. [Java 17: AbstractMap.SimpleImmutableEntry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.SimpleImmutableEntry.html).

Các phép kiểm tra/xóa trên nguồn cấm `null` có thể có hành vi theo hợp đồng riêng. Không khái quát một lần thử `HashMap` sang mọi map.

## 9. View, bản sao collection và bản sao entry là ba mức khác nhau

### 9.1. Sao chép khóa hoặc giá trị

Nếu muốn một danh sách tên độc lập về cấu trúc:

~~~java
List<String> savedNames = new ArrayList<>(users.keySet());
~~~

Thêm/xóa khóa trong `users` sau đó không tự thêm/xóa trong `savedNames`. Danh sách mới nhận phần tử theo lần duyệt nguồn khi sao chép. `ArrayList` tạo một cấu trúc danh sách riêng và cho phép thao tác danh sách theo hợp đồng lớp. [Java 17: ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

Tương tự, `new ArrayList<>(users.values())` lưu các tham chiếu giá trị tại lúc sao chép. Nếu giá trị là đối tượng có thể sửa, bản sao collection không tự sao chép sâu chúng.

### 9.2. Chỉ sao chép collection entry chưa đủ chứng minh cặp đã độc lập

Đoạn này tạo một list mới:

~~~java
List<Map.Entry<String, String>> saved =
        new ArrayList<>(users.entrySet());
~~~

Nhưng các phần tử được đưa vào vẫn là các đối tượng entry do nguồn cung cấp. Không dựa vào list mới để suy rằng mọi entry đã bị tách khỏi nguồn hoặc tiếp tục có quyền cập nhật hợp lệ ngoài lần duyệt.

Muốn giữ các cặp độc lập, hãy sao chép từng entry ngay trong lần duyệt:

~~~java
List<Map.Entry<String, String>> saved = new ArrayList<>();
for (Map.Entry<String, String> entry : users.entrySet()) {
    saved.add(new AbstractMap.SimpleImmutableEntry<>(entry));
}
~~~

Các entry mới không còn là đối tượng entry của view nguồn. Đây vẫn là sao chép nông các tham chiếu khóa/giá trị.

Java 17 cũng có `Map.Entry.copyOf` để tạo entry độc lập, nhưng nó không nhận khóa hoặc giá trị `null`. Chọn công cụ theo miền dữ liệu, không chỉ theo độ ngắn của câu lệnh.

### 9.3. Sao chép map

`new LinkedHashMap<>(users)` tạo một map mới có các ánh xạ được sao chép, giữ thứ tự duyệt đầu vào trong cách dựng này. Sau đó thay giá trị của khóa trong map gốc không tự thay ánh xạ của bản sao.

Nhưng nếu cả hai map cùng giữ một `List` làm giá trị, sửa `List` ấy có thể nhìn thấy qua cả hai. Bản sao cấu trúc map và bản sao sâu dữ liệu là hai mức bảo đảm khác nhau.

Một bản sao được tạo trong khi nhiều luồng đang sửa nguồn cũng không tự được nâng thành ảnh chụp nghiệp vụ nguyên tử. Cần điều kiện truy cập phù hợp trong suốt việc sao chép.

## 10. Bọc chỉ đọc cần bảo vệ đúng tầng

### 10.1. Collections.unmodifiableMap bảo vệ các đường sửa qua map và view

`Collections.unmodifiableMap(source)` trả view chặn sửa ánh xạ qua map và qua các collection view mà nó trả về. Các lần đọc vẫn nhìn nguồn phía dưới, nên nguồn có thể thay đổi qua một tham chiếu khác. [Java 17: Collections.unmodifiableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableMap(java.util.Map)).

Điều này hữu ích khi muốn giữ nguồn đang sống nhưng không trao quyền thêm/xóa/thay giá trị ánh xạ cho bên gọi.

Nó không làm các đối tượng giá trị trở thành bất biến sâu. Nếu nguồn giữ một `ArrayList`, bên gọi có thể nhận tham chiếu danh sách qua `get` và sửa chính danh sách đó, trừ khi mô hình dữ liệu cũng bảo vệ tầng phần tử.

### 10.2. Chỉ bọc entrySet bằng unmodifiableSet có thể để lọt setValue

Xét ý định “trả một set entry không cho bên gọi sửa map”:

~~~java
Set<Map.Entry<String, Integer>> result =
        Collections.unmodifiableSet(scores.entrySet());
~~~

Lớp bọc chặn thêm/xóa phần tử set và xóa qua iterator của nó. Nhưng nó không tự thay mỗi entry bằng một entry chỉ đọc. Khi các entry từ nguồn còn cho cập nhật trong lúc duyệt, `entry.setValue` có thể vẫn ghi xuyên.

Chương trình đầy đủ chứng minh điều này trên một `LinkedHashMap`: vòng lặp qua set bọc đổi điểm `math` từ `80` thành `90`. Bọc `scores` bằng `unmodifiableMap` rồi lấy `entrySet` mới chặn đường cập nhật entry ấy.

Bài học về thiết kế API là kiểm tra **quyền trên đối tượng phần tử**, không chỉ quyền của collection bên ngoài. Tương tự, một `List` không sửa được vẫn có thể chứa phần tử sửa được.

### 10.3. Khi nào chọn bản sao không sửa được?

Nếu bên gọi cần trạng thái tại một thời điểm, có thể sao chép cấu trúc phù hợp rồi bọc bản sao. Nếu cần bất biến sâu, phải sao chép hoặc dùng kiểu bất biến cho các khóa/giá trị liên quan.

`Map.copyOf` tạo map không sửa được và không phản ánh các lần thay đổi ánh xạ tiếp theo của nguồn, nhưng cấm khóa/giá trị `null` và không bảo đảm cùng thứ tự duyệt mong muốn. Không dùng nó như một phép sao chép mọi khía cạnh vô điều kiện.

Lựa chọn cần dựa trên bốn yêu cầu riêng: nguồn có tiếp tục sống không, có được sửa cấu trúc không, phần tử có thể sửa không, và thứ tự có quan trọng không.

## 11. Chương trình đầy đủ

Lưu toàn bộ mã dưới vào **`MapWriteThroughDemo.java`**. Chương trình chạy một luồng, dùng `LinkedHashMap` để stdout có thứ tự xác định và không dùng API chỉ có sau Java 17.

Các chuỗi vai trò là dữ liệu minh họa cho hành vi collection; ví dụ không triển khai một hệ thống phân quyền.

~~~java
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MapWriteThroughDemo {
    static String unsupported(Runnable action) {
        try {
            action.run();
            return "no exception";
        } catch (UnsupportedOperationException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        Map<String, String> users = new LinkedHashMap<>();
        users.put("ada", "READER");
        users.put("lin", "EDITOR");
        users.put("sam", "READER");
        Set<String> keys = users.keySet();
        Collection<String> roles = users.values();
        Set<Map.Entry<String, String>> entries = users.entrySet();

        users.put("maya", "REVIEWER");
        users.put("lin", "ADMIN");
        System.out.println("live keys: " + keys);
        System.out.println("live values: " + roles);
        System.out.println("live entries: " + entries);

        boolean changed = roles.remove("READER");
        System.out.println("remove one value: " + changed + ", map=" + users);
        changed = keys.remove("lin");
        System.out.println("remove key: " + changed + ", map=" + users);
        changed = entries.remove(Map.entry("maya", "WRONG"));
        System.out.println("remove wrong pair: " + changed + ", map=" + users);
        changed = entries.remove(Map.entry("maya", "REVIEWER"));
        System.out.println("remove matching pair: " + changed + ", map=" + users);

        users.put("nora", "READER");
        users.put("oli", "EDITOR");
        users.put("pia", "READER");
        changed = roles.removeAll(Set.of("READER"));
        System.out.println("remove all reader values: " + changed + ", map=" + users);
        users.put("uma", "VIEWER");
        users.put("vic", "ADMIN");
        users.put("wen", "EDITOR");
        keys.retainAll(Set.of("oli", "uma", "vic"));
        System.out.println("retain allowed keys: " + users);

        Iterator<Map.Entry<String, String>> it = entries.iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            if (entry.getValue().equals("VIEWER")) {
                it.remove();
            } else if (entry.getValue().equals("EDITOR")) {
                entry.setValue("REVIEWER");
            }
        }
        System.out.println("iterator remove and setValue: " + users);
        entries.removeIf(entry -> entry.getValue().equals("REVIEWER"));
        System.out.println("entry predicate removal: " + users);

        List<String> savedKeys = new ArrayList<>(keys);
        List<String> savedValues = new ArrayList<>(roles);
        List<Map.Entry<String, String>> savedEntries = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries) {
            savedEntries.add(new AbstractMap.SimpleImmutableEntry<>(entry));
        }
        Map<String, String> savedMap = new LinkedHashMap<>(users);
        users.put("vic", "OWNER");
        users.put("zoe", "EDITOR");
        System.out.println("live map after changes: " + users);
        System.out.println("saved keys: " + savedKeys);
        System.out.println("saved values: " + savedValues);
        System.out.println("saved entries: " + savedEntries);
        System.out.println("saved map: " + savedMap);

        roles.clear();
        System.out.println("clear values, map: " + users);
        System.out.println("other views after clear: keys=" + keys + ", entries=" + entries);
        System.out.println("key add: " + unsupported(() -> keys.add("new-user")));
        System.out.println("value add: " + unsupported(() -> roles.add("READER")));
        System.out.println("entry add: "
                + unsupported(() -> entries.add(Map.entry("new-user", "READER"))));

        Map<String, String> nullable = new LinkedHashMap<>();
        nullable.put("pending", null);
        System.out.println("null mapping key remove: "
                + nullable.keySet().remove("pending") + ", empty=" + nullable.isEmpty());
        nullable.put("pending", null);
        Map.Entry<String, String> nullPair =
                new AbstractMap.SimpleImmutableEntry<>("pending", null);
        System.out.println("null pair remove: " + nullable.entrySet().remove(nullPair));

        users.put("r1", "VIEWER");
        Map<String, String> readOnly = Collections.unmodifiableMap(users);
        System.out.println("read-only key remove: "
                + unsupported(() -> readOnly.keySet().remove("r1")));
        System.out.println("read-only entry setValue: " + unsupported(() -> {
            for (Map.Entry<String, String> entry : readOnly.entrySet()) {
                entry.setValue("OTHER");
            }
        }));
        users.put("r2", "EDITOR");
        System.out.println("read-only view sees source: " + readOnly);

        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("math", 80);
        Set<Map.Entry<String, Integer>> setOnly =
                Collections.unmodifiableSet(scores.entrySet());
        for (Map.Entry<String, Integer> entry : setOnly) {
            entry.setValue(90);
        }
        System.out.println("set wrapper entry still writes: " + scores);
        Map<String, Integer> protectedMap = Collections.unmodifiableMap(scores);
        System.out.println("map wrapper blocks entry write: " + unsupported(() -> {
            for (Map.Entry<String, Integer> entry : protectedMap.entrySet()) {
                entry.setValue(99);
            }
        }));

        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("team", new ArrayList<>(List.of("a")));
        Map<String, List<String>> groupCopy = new LinkedHashMap<>(groups);
        Map<String, List<String>> groupView = Collections.unmodifiableMap(groups);
        groupView.get("team").add("b");
        System.out.println("nested value changes source: " + groups);
        System.out.println("shallow copy shares value: "
                + (groups.get("team") == groupCopy.get("team")));
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 MapWriteThroughDemo.java
java MapWriteThroughDemo
~~~

Có thể dùng JDK 17 trở lên để biên dịch mức release 17 và runtime Java 17 trở lên để chạy. Ví dụ đã được biên dịch ở mức release 17 và chạy thành công bằng OpenJDK 24.0.2.

Stdout chính xác:

~~~text
live keys: [ada, lin, sam, maya]
live values: [READER, ADMIN, READER, REVIEWER]
live entries: [ada=READER, lin=ADMIN, sam=READER, maya=REVIEWER]
remove one value: true, map={lin=ADMIN, sam=READER, maya=REVIEWER}
remove key: true, map={sam=READER, maya=REVIEWER}
remove wrong pair: false, map={sam=READER, maya=REVIEWER}
remove matching pair: true, map={sam=READER}
remove all reader values: true, map={oli=EDITOR}
retain allowed keys: {oli=EDITOR, uma=VIEWER, vic=ADMIN}
iterator remove and setValue: {oli=REVIEWER, vic=ADMIN}
entry predicate removal: {vic=ADMIN}
live map after changes: {vic=OWNER, zoe=EDITOR}
saved keys: [vic]
saved values: [ADMIN]
saved entries: [vic=ADMIN]
saved map: {vic=ADMIN}
clear values, map: {}
other views after clear: keys=[], entries=[]
key add: UnsupportedOperationException
value add: UnsupportedOperationException
entry add: UnsupportedOperationException
null mapping key remove: true, empty=true
null pair remove: true
read-only key remove: UnsupportedOperationException
read-only entry setValue: UnsupportedOperationException
read-only view sees source: {r1=VIEWER, r2=EDITOR}
set wrapper entry still writes: {math=90}
map wrapper blocks entry write: UnsupportedOperationException
nested value changes source: {team=[a, b]}
shallow copy shares value: true
~~~

### 11.1. Các view được lấy trước khi nguồn đổi

Ba view được tạo khi nguồn mới có ba người. Sau khi thêm `maya` và đổi giá trị của `lin`, lần in view đã thấy trạng thái mới. Đây là bằng chứng view không phải bản sao tạo ở thời điểm lấy.

### 11.2. Các phép xóa có đối tượng nhắm đến khác nhau

Xóa một giá trị `READER` chỉ loại một ánh xạ. Xóa khóa `lin` không cần truyền vai trò. Xóa entry `maya=WRONG` không khớp nên giữ nguồn, còn cặp đúng xóa được.

`removeAll` trên view giá trị loại hết các ánh xạ mang vai trò bị chọn; `retainAll` trên view khóa loại các khóa ngoài nhóm cho phép.

### 11.3. Iterator phối hợp xóa và cập nhật

Trong vòng lặp iterator, `uma` bị xóa qua `it.remove`. `oli` được đổi giá trị qua entry hiện tại. Vòng lặp không thêm/xóa qua `users.put` hay `users.remove` ở bên ngoài iterator.

Sau đó, `entrySet.removeIf` loại các entry thỏa điều kiện. Mỗi giai đoạn kết thúc việc duyệt trước khi bước kế tiếp làm thay đổi cấu trúc.

### 11.4. Các bản sao và lớp bọc

Bốn bản lưu giữ dữ liệu lúc nguồn chỉ còn `vic=ADMIN`. Thay `vic` và thêm `zoe` trên nguồn không tự đổi cấu trúc hay entry độc lập đã lưu.

`roles.clear` làm nguồn rỗng và hai view còn lại cũng rỗng. Các lệnh thêm vào view bị từ chối.

Cuối chương trình, hai ví dụ lớp bọc chứng minh giới hạn ở từng tầng: bọc set không tự bọc entry, còn bọc map không tự làm một `List` giá trị trở thành bất biến.

## 12. Thiết kế hàm trả dữ liệu từ map

Trước khi trả một view, hãy xác định bên nhận được phép làm gì:

| Nhu cầu của bên nhận | Lựa chọn có thể phù hợp | Điều cần nêu rõ |
| --- | --- | --- |
| Theo dõi nguồn và được phép xóa ánh xạ theo khóa | `keySet` trực tiếp | Đây là quyền sửa nguồn |
| Chỉ đọc nguồn đang sống | Map/view được bọc chỉ đọc đúng tầng | Nguồn vẫn có thể đổi qua đường khác |
| Giữ danh sách tên tại một thời điểm | Bản sao khóa vào collection phù hợp | Không tự là ảnh chụp nguyên tử nếu nguồn đổi đồng thời |
| Giữ các cặp độc lập về cấu trúc | Sao chép từng entry hoặc sao chép map | Vẫn phải xác định mức sao chép sâu |
| Lọc dữ liệu hiển thị mà không xóa nguồn | Tạo kết quả lọc riêng | Không dùng remove trên live view |
| Cập nhật giá trị trong lúc duyệt nguồn hỗ trợ | Entry iterator và `setValue` | Tôn trọng vòng đời, kiểu và quyền sửa |

Một hàm có tên `getUserNames` nhưng trả thẳng `users.keySet()` có thể hợp lý nếu hợp đồng chủ động trao quyền ấy. Nếu mục tiêu là một ranh giới chỉ đọc, tên hàm không tự bảo vệ dữ liệu.

Cũng đừng bắt mọi `UnsupportedOperationException` rồi bỏ qua để làm một hàm “chạy với mọi map”. Khi chức năng cần xóa/cập nhật, nguồn không hỗ trợ là một khác biệt về hợp đồng cần được xử lý hoặc báo rõ.

## 13. Lỗi thường gặp

| Hiểu nhầm | Vấn đề | Cách sửa theo ý định |
| --- | --- | --- |
| `keySet` là bản sao danh sách khóa | Xóa qua nó có thể xóa nguồn | Chọn view hoặc bản sao có chủ đích |
| `values.remove` chỉ bỏ một giá trị hiển thị | Ánh xạ tương ứng bị xóa | Dùng kết quả hiển thị riêng |
| `values` là set | Bỏ mất số lần xuất hiện của giá trị | Giữ ngữ nghĩa collection có thể lặp |
| `entrySet.remove` chỉ cần khóa khớp | Cặp sai giá trị không bị xóa | Xóa theo khóa hoặc đối chiếu đủ cặp |
| Thêm qua view để cập nhật map | Thao tác không được hỗ trợ | Dùng API map |
| Gán lại biến value trong for-each | Không thay ánh xạ | Dùng `put`, `replace` hoặc entry có quyền sửa |
| Xóa nguồn trực tiếp trong for-each | Phá quy tắc duyệt thông thường | Dùng iterator.remove hoặc removeIf phù hợp |
| Bọc entrySet bằng unmodifiableSet là đủ | Entry có thể còn cho setValue | Bọc đúng map hoặc sao chép entry đúng mục đích |
| Sao chép list entry là sao chép sâu | Entry/phần tử có thể còn gắn nguồn hoặc chia sẻ tham chiếu | Tách từng mức sao chép |
| Không thấy ngoại lệ nên coi là an toàn luồng | Không có bảo đảm như vậy | Thiết kế truy cập theo nguồn và đơn vị nghiệp vụ |

## 14. Thực hành có đáp án

### Bài 1 — View hay bản sao?

Tạo `Set<String> names = map.keySet()` rồi thêm một khóa qua `map.put`. `names` có nhìn thấy khóa mới không? Nếu xóa khóa qua `names` thì nguồn ra sao?

**Đáp án:** view gắn với nguồn nhìn thấy cập nhật qua các lần truy vấn. Với nguồn/view hỗ trợ xóa, xóa qua `names` xóa ánh xạ tương ứng trong map. Iterator đã tạo trước thay đổi vẫn phải tuân hợp đồng duyệt riêng.

### Bài 2 — Giá trị trùng

Nguồn có `{A=OPEN, B=OPEN, C=DONE}`. So sánh ý định của `values().remove("OPEN")` và `values().removeAll(Set.of("OPEN"))`.

**Đáp án:** phép đầu xóa một ánh xạ có giá trị phù hợp; không dùng nó để chỉ định chắc chắn khóa `A` trên mọi map. Phép sau loại các ánh xạ có giá trị thuộc nhóm, nên cả hai giá trị `OPEN` không còn sau khi hoàn tất thành công trên nguồn hỗ trợ. Muốn xóa đúng một khóa, dùng khóa ấy.

### Bài 3 — Đối chiếu đủ entry

Nguồn có `{item=7}`. `entrySet().remove(Map.entry("item", 8))` trả gì và có làm map rỗng không?

**Đáp án:** trên nguồn sửa được thông thường, trả `false` vì giá trị không khớp, nguồn vẫn `{item=7}`. Xóa theo entry đối chiếu cả khóa lẫn giá trị; xóa theo khóa là ý định khác.

### Bài 4 — Gán biến trong vòng lặp

Vì sao `for (String v : map.values()) { v = "NEW"; }` không thay mọi giá trị trong map? Một hướng cập nhật đúng là gì?

**Đáp án:** `v` là biến cục bộ nhận tham chiếu; gán lại nó không sửa ánh xạ. Có thể dùng entry iterator và `setValue` trên nguồn hỗ trợ, hoặc API cập nhật map phù hợp như `replaceAll` với hợp đồng quyền sửa rõ ràng. Không đồng thời sửa cấu trúc ngoài quy tắc duyệt.

### Bài 5 — Lớp bọc thiếu một tầng

API trả `Collections.unmodifiableSet(map.entrySet())` của một map sửa được và tuyên bố bên gọi không thể cập nhật giá trị. Tuyên bố đó có đủ cơ sở không?

**Đáp án:** không. Lớp bọc set chặn thay thành viên set nhưng không tự chặn phương thức sửa của từng entry. Nếu entry nguồn hỗ trợ `setValue` trong lúc duyệt, bên gọi vẫn có thể ghi xuyên. Dùng lớp bọc map phù hợp rồi lấy view của nó, hoặc trả các entry độc lập không sửa được theo mục tiêu dữ liệu.

### Bài 6 — Bản sao entry

Một hàm cần lưu các cặp hiện tại để dùng sau khi map tiếp tục đổi. Chỉ `new ArrayList<>(map.entrySet())` đã chứng minh các entry độc lập chưa?

**Đáp án:** chưa. Collection mới vẫn nhận các đối tượng entry từ nguồn; không dùng vòng đời entry iterator như một bảo đảm cho dữ liệu giữ lâu. Sao chép từng entry ngay trong lúc duyệt, chẳng hạn `new SimpleImmutableEntry<>(entry)`. Đây vẫn là sao chép nông khóa/giá trị; nếu muốn dùng `Map.Entry.copyOf` phải xử lý điều kiện không `null`.

### Bài 7 — null và cờ xóa

Map cho phép `null` và có `pending=null`. `keySet().remove("pending")` trả gì? Vì sao nó khác việc suy từ `map.remove("pending")` trả `null`?

**Đáp án:** xóa khóa qua view trả `true` khi khóa tồn tại và bị xóa. Giá trị cũ do `map.remove` trả có thể là `null` ở cả tình huống vắng ánh xạ và tình huống từng có giá trị `null`. Chọn kiểu kết quả đúng điều bạn cần biết và tuân chính sách nguồn.

### Bài 8 — Không sửa map nhưng sửa giá trị

Một map chỉ đọc chứa giá trị là `ArrayList`. Gọi `readOnly.get("team").add("new")` có thể làm dữ liệu thấy qua nguồn đổi không?

**Đáp án:** có nếu bạn nhận chính danh sách sửa được mà nguồn đang giữ. Chặn thêm/xóa/thay ánh xạ không tự làm đối tượng giá trị bất biến sâu. Cần quyết định sao chép hoặc bọc dữ liệu ở tầng phần tử, đồng thời cân nhắc vòng đời và việc chia sẻ tham chiếu.

Khi tự mở rộng ví dụ, hãy thử mỗi thao tác trên nguồn sửa được, lớp bọc chỉ đọc và một bản sao độc lập. Sau mỗi lần, kiểm tra cả map lẫn các view đã lấy trước đó. Phân biệt được nơi dữ liệu thực sự thay đổi là điều kiện để dùng collection view mà không làm mất ranh giới sở hữu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-copies-004">View phạm vi của TreeSet, TreeMap và view đảo thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# View phạm vi của TreeSet, TreeMap và view đảo thứ tự

## 1. Nhìn đúng mô hình: một dữ liệu, nhiều cửa sổ

Giả sử hệ thống giữ các mốc thời gian đã sắp thứ tự. Một chức năng cần xem những mốc từ 20 đến trước 50; chức năng khác cần duyệt chính phạm vi đó từ cuối về đầu. Nếu mỗi lần đều lọc rồi chép sang một tập hợp mới, hai kết quả nhanh chóng trở thành dữ liệu độc lập, phải tự cập nhật khi nguồn đổi.

**View phạm vi** giải quyết nhu cầu làm việc trực tiếp với một khoảng của cấu trúc có thứ tự. **View đảo thứ tự** cho phép đọc cùng dữ liệu theo chiều ngược lại. Với TreeSet và TreeMap, các view này dựa trên cấu trúc nguồn: thay đổi được phép qua view đi vào nguồn; thay đổi ở nguồn hiện ra trong view khi thỏa điều kiện phạm vi.

Hãy tách ba câu hỏi:

- **Dữ liệu ở đâu?** Các view vẫn dùng dữ liệu được quản lý bởi cây nguồn.
- **Nhìn thấy phần nào?** Phạm vi chọn phần tử của set hoặc khóa của map bằng phép so sánh.
- **Nhìn theo chiều nào?** Chiều của bộ so sánh hiện tại quyết định đầu, cuối, trước và sau.

Một view có thể vừa giới hạn phạm vi vừa đảo thứ tự. Đảo chiều không bỏ giới hạn; giới hạn phạm vi không tạo bản sao.

Ví dụ nguồn tăng dần có các phần tử 10, 20, 30, 40, 50:

| Đối tượng đang nhìn | Nội dung theo thứ tự duyệt |
|---|---|
| Nguồn | 10, 20, 30, 40, 50 |
| Phạm vi từ 20, lấy 20, đến 50, không lấy 50 | 20, 30, 40 |
| View đảo của phạm vi trên | 40, 30, 20 |
| Bản sao được tạo từ phạm vi | Các phần tử tại thời điểm sao chép; cấu trúc độc lập với nguồn |

Nếu nguồn có thêm 35, hai view phạm vi lập tức có thêm phần tử đó. Bản sao đã tạo trước đó không tự có 35. Nếu thêm 60 vào nguồn, cả hai view phạm vi vẫn không nhìn thấy 60 vì nó vượt biên.

Bài này đi theo thứ tự: đọc biên → thao tác với phạm vi → đọc thứ tự đảo → kết hợp hai phép biến đổi → quyết định lúc nào nên sao chép. Cần biết cách dùng TreeSet, TreeMap và Comparator cơ bản; không cần biết cách cài đặt cây cân bằng.

Các chương trình đầy đủ dùng JDK 21 trở lên và không cần thư viện ngoài. API phạm vi cùng descendingSet()/descendingMap() đã có trước Java 17; tên reversed() được bổ sung ở Java 21. Những đoạn ngắn được ghi rõ là đoạn minh họa, không phải tệp chạy độc lập.

## 2. API phạm vi: biên được hiểu bằng phép so sánh

### 2.1. Set xét phần tử; map xét khóa

TreeSet triển khai NavigableSet, mở rộng SortedSet. TreeMap triển khai NavigableMap, mở rộng SortedMap. Hai họ API tương ứng nhau:

| Mục đích | NavigableSet | NavigableMap |
|---|---|---|
| Giới hạn hai đầu | subSet(from, fromInclusive, to, toInclusive) | subMap(from, fromInclusive, to, toInclusive) |
| Lấy phần trước một mốc | headSet(to, inclusive) | headMap(to, inclusive) |
| Lấy phần từ một mốc trở đi | tailSet(from, inclusive) | tailMap(from, inclusive) |

Tên gọi “trước”, “từ”, “đầu” đều theo thứ tự của đối tượng nhận lời gọi. Với cây số nguyên tăng dần, thứ tự ấy trùng thứ tự số thông thường. Với bộ so sánh giảm dần, quan hệ đảo lại.

Có thể diễn đạt chính xác bằng c(a,b), kết quả so sánh của bộ so sánh hiện tại:

- Biên dưới đóng chấp nhận x khi c(x,from) >= 0; biên dưới mở yêu cầu > 0.
- Biên trên đóng chấp nhận x khi c(x,to) <= 0; biên trên mở yêu cầu < 0.
- head lấy phía trước mốc theo thứ tự này; tail lấy phía sau mốc theo thứ tự này.

Khi dùng thứ tự tự nhiên, c(a,b) là ý nghĩa của a.compareTo(b), không cần tự tạo Comparator chỉ để gọi các phương thức phạm vi.

### 2.2. Bốn tổ hợp biên

Trong bảng sau, nguồn có thứ tự số tăng dần và chứa mọi số nguyên từ 1 đến 5:

| Lời gọi | Ký hiệu khoảng | Kết quả |
|---|---|---|
| subSet(2, true, 5, true) | [2, 5] | 2, 3, 4, 5 |
| subSet(2, true, 5, false) | [2, 5) | 2, 3, 4 |
| subSet(2, false, 5, true) | (2, 5] | 3, 4, 5 |
| subSet(2, false, 5, false) | (2, 5) | 3, 4 |

Với map, thay subSet bằng subMap và đọc các số ở bảng là khóa. Giá trị đi kèm khóa không tham gia kiểm tra biên.

Hai mốc không phải tồn tại sẵn trong nguồn. Nguồn có 10, 20, 30 nhưng subSet(12, true, 28, false) vẫn hợp lệ và hiện 20. View mô tả một điều kiện theo thứ tự, không lưu “vị trí của phần tử 12” trong cây.

Khoảng hợp lệ cũng có thể chưa chứa phần tử nào. Khi nguồn về sau nhận một phần tử thuộc khoảng đó, view sẽ nhìn thấy nó.

### 2.3. Các dạng cũ có biên mặc định

SortedSet và SortedMap cung cấp các dạng không có boolean:

| Dạng rút gọn | Tương đương về biên |
|---|---|
| subSet(a, b) | subSet(a, true, b, false) |
| headSet(b) | headSet(b, false) |
| tailSet(a) | tailSet(a, true) |
| subMap(a, b) | subMap(a, true, b, false) |
| headMap(b) | headMap(b, false) |
| tailMap(a) | tailMap(a, true) |

Nghĩa là khoảng hai đầu mặc định **lấy đầu, bỏ cuối**; head bỏ mốc, tail lấy mốc. Không dùng trí nhớ “cả hai đều lấy” để suy luận kết quả. Có thể đối chiếu trực tiếp các chữ ký trong [SortedSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/SortedSet.html) và [SortedMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/SortedMap.html).

Dạng rút gọn của TreeSet có kiểu trả về khai báo là SortedSet, và của TreeMap là SortedMap. Vì vậy, viết var v = tree.subSet(a,b) không cho biến v các phương thức điều hướng như floor() qua kiểu khai báo. Nếu cần NavigableSet hoặc NavigableMap, dùng dạng có boolean. Không cần ép kiểu để tiếp tục làm việc.

### 2.4. Biên trùng nhau và biên ngược chiều

Khi hai mốc so sánh bằng nhau:

- Cả hai phía đóng: view có thể chứa phần tử/khóa tương đương mốc đó, nếu nguồn đang có.
- Có ít nhất một phía mở: view rỗng.
- Dạng rút gọn [a,a) vì thế luôn rỗng.

“Bằng nhau” ở đây theo phép so sánh mà cây sử dụng. Một Comparator không phân biệt chữ hoa và chữ thường có thể xem hai chuỗi khác nhau theo equals là cùng một mốc.

Ngược lại, from đứng sau to theo bộ so sánh hiện tại là yêu cầu sai và gây IllegalArgumentException. Đây không phải cách yêu cầu một khoảng rỗng. Trên cây tăng dần, subSet(40,true,20,true) sai; trên view giảm dần, chính cặp mốc ấy có thể đúng.

## 3. TreeSet: view sống và thao tác đi vào nguồn

Chương trình sau kiểm tra cả hai chiều cập nhật, truy vấn ngoài phạm vi, phạm vi lồng nhau và một view đã bị xóa hết dữ liệu.

Tệp **RangeSetDemo.java**:

```java
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

public class RangeSetDemo {
    private static void expectIAE(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + ": no exception");
        } catch (IllegalArgumentException expected) {
            System.out.println(label + "=IllegalArgumentException");
        }
    }

    public static void main(String[] args) {
        TreeSet<Integer> source =
                new TreeSet<>(List.of(10, 20, 30, 40, 50));
        NavigableSet<Integer> middle =
                source.subSet(20, true, 50, false);

        System.out.println("initial=" + middle);
        System.out.println("legacy=" + source.subSet(20, 50));
        System.out.println("headClosed=" + source.headSet(20, true));
        System.out.println("tailOpen=" + source.tailSet(40, false));
        System.out.println("contains50=" + middle.contains(50));
        System.out.println("remove50=" + middle.remove(50));
        expectIAE("add50", () -> middle.add(50));

        middle.add(25);
        middle.remove(30);
        source.add(35);
        source.add(60);
        System.out.println("middle=" + middle);
        System.out.println("source=" + source);
        System.out.println("floor100=" + middle.floor(100));
        System.out.println("ceiling0=" + middle.ceiling(0));
        System.out.println("higher40=" + middle.higher(40));

        NavigableSet<Integer> nested = middle.tailSet(25, true);
        System.out.println("nested=" + nested);
        System.out.println("sameExcludedEnd=" + middle.headSet(50, false));
        expectIAE("includeExcludedEnd", () -> middle.headSet(50, true));
        expectIAE("expandNested", () -> middle.headSet(70, false));
        expectIAE("wrongDirection", () -> source.subSet(40, true, 20, true));

        middle.clear();
        System.out.println("afterClear=" + source);
        System.out.println("nestedAfterClear=" + nested);
        source.add(32);
        System.out.println("liveAgain=" + middle);
        System.out.println("nestedLiveAgain=" + nested);
        System.out.println("equalClosed=" + source.subSet(32, true, 32, true));
        System.out.println("equalHalfOpen=" + source.subSet(32, true, 32, false));
    }
}
```

Chạy bằng JDK 21 trở lên:

```shell
javac --release 21 -encoding UTF-8 RangeSetDemo.java
java RangeSetDemo
```

Stdout chính xác:

```text
initial=[20, 30, 40]
legacy=[20, 30, 40]
headClosed=[10, 20]
tailOpen=[50]
contains50=false
remove50=false
add50=IllegalArgumentException
middle=[20, 25, 35, 40]
source=[10, 20, 25, 35, 40, 50, 60]
floor100=40
ceiling0=20
higher40=null
nested=[25, 35, 40]
sameExcludedEnd=[20, 25, 35, 40]
includeExcludedEnd=IllegalArgumentException
expandNested=IllegalArgumentException
wrongDirection=IllegalArgumentException
afterClear=[10, 50, 60]
nestedAfterClear=[]
liveAgain=[32]
nestedLiveAgain=[32]
equalClosed=[32]
equalHalfOpen=[]
```

### 3.1. Đọc những thay đổi chính

middle bắt đầu với 20, 30, 40. Biên 50 bị loại, dù nguồn thực sự có 50.

Lệnh middle.add(25) thêm 25 vào cây nguồn. Lệnh middle.remove(30) xóa 30 khỏi cây nguồn. Sau đó source.add(35) được nhìn thấy trong middle, còn source.add(60) nằm ngoài phạm vi nên chỉ xuất hiện ở source.

Việc cập nhật qua nguồn không bị view giới hạn. Chủ sở hữu source vẫn có quyền thao tác với toàn bộ cây; việc tạo middle không “khóa” nguồn vào [20,50).

Một điểm khác dễ nhầm là: **giới hạn chỗ có thể tìm thấy kết quả khác với giới hạn đối số truy vấn**. middle.floor(100) hợp lệ và trả 40, phần tử lớn nhất đang có trong view không vượt 100. middle.ceiling(0) trả 20. Cả hai đều không được trả phần tử ở ngoài view, mặc dù nguồn còn phần tử khác.

middle.higher(40) trả null vì không có phần tử nào sau 40 trong phạm vi. Nó không vượt ra nguồn để lấy 50.

### 3.2. Hỏi, xóa và chèn ngoài phạm vi không có cùng kết quả

Trong chương trình, đối số là Integer hợp lệ với thứ tự tự nhiên:

| Thao tác với middle = [20,50) | Kết quả | Nguồn bị đổi? |
|---|---|---|
| contains(50) | false | Không |
| remove(50) | false | Không |
| add(50) | IllegalArgumentException | Không thêm 50 qua view |
| floor(100) | Phần tử phù hợp trong view, ở đây 40 | Không |
| clear() | Xóa tất cả phần tử đang thuộc view | Có |

50 đã tồn tại ở source cũng không làm add(50) qua middle hợp lệ. Phép chèn được kiểm tra với phạm vi của đối tượng nhận lời gọi.

Bảng này không nói rằng mọi đối số bất kỳ đều được chấp nhận: kiểu không so sánh được hoặc null không được bộ so sánh hỗ trợ vẫn có thể gây ngoại lệ. Đừng tổng quát kết quả với Integer thành “mọi truy vấn ngoài biên đều an toàn”.

Đặc tả các phương thức phạm vi của [TreeSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeSet.html#subSet(E,boolean,E,boolean)) xác nhận view dùng chung nguồn và từ chối chèn phần tử ngoài khoảng.

### 3.3. clear() xóa phần nào?

middle.clear() không chỉ làm một đối tượng trung gian trống. Nó xóa các phần tử 20, 25, 35, 40 khỏi cấu trúc nguồn. Những phần tử ngoài khoảng là 10, 50, 60 vẫn còn.

nested cũng dựa vào dữ liệu này nên lập tức rỗng. Sau đó source.add(32) làm cả middle và nested hiện 32 trở lại. Biên của hai view vẫn tồn tại dù dữ liệu hiện có bằng không.

Đó là lý do không nên mô tả clear() là “bỏ bộ lọc” hoặc “ngắt kết nối view”. Khi muốn ngắt kết nối, phải sao chép sang cấu trúc khác.

## 4. Phạm vi lồng nhau: thu hẹp có kiểm tra, không tự cắt biên

Xét middle đã giới hạn [20,50), rồi gọi middle.tailSet(25,true). nested nhận khoảng [25,50). Nó không có cấu trúc dữ liệu riêng; cập nhật nguồn và các view liên quan vẫn dùng chung dữ liệu.

Tuy nhiên, không thể yêu cầu một view con với mốc ngoài giới hạn của view cha rồi mong Java tự lấy phần giao:

- middle.headSet(70,false) gây IllegalArgumentException.
- middle.tailSet(10,true) cũng yêu cầu mốc ngoài phạm vi cha và không hợp lệ.
- middle.headSet(50,false) hợp lệ: nó giữ mốc 50 ở trạng thái không được lấy.
- middle.headSet(50,true) không hợp lệ: nó yêu cầu nhận thêm chính mốc cha đã loại.

Hai lời gọi cuối rất hữu ích để hiểu biên. Mốc nằm đúng tại giới hạn mở của cha có thể được dùng làm giới hạn mở của con; yêu cầu lấy mốc đó sẽ vượt quyền của khoảng cha.

Ngay cả khi nguồn hiện chưa có 50, lỗi includeExcludedEnd vẫn là lỗi. Ràng buộc dựa trên biên đã khai báo, không phụ thuộc việc có tìm thấy khóa/phần tử vi phạm ở thời điểm tạo view hay không.

Để kết hợp hai khoảng nghiệp vụ tùy ý, hãy tự tính biên giao bằng đúng Comparator và xử lý trường hợp giao rỗng trước, hoặc tạo view từ nguồn đủ rộng với biên cuối đã hợp lệ. Đừng coi chuỗi headSet()/tailSet() là phép giao tự động cho mọi mốc.

Quy tắc tương ứng áp dụng với subMap/headMap/tailMap trên phạm vi TreeMap. Hướng dẫn về giới hạn của [NavigableMap.subMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/NavigableMap.html#subMap(K,boolean,K,boolean)) là căn cứ để xử lý lỗi biên ngay tại nơi tạo view.

## 5. TreeMap: chọn theo khóa, cập nhật cả ánh xạ

Với TreeMap, phạm vi áp dụng lên **khóa**. Ví dụ bảng tra cứu theo mã đơn hàng có thể dùng [100,300) để xem các đơn có mã từ 100 đến 299. Giá trị có tên “ngoài khoảng”, “ưu tiên”, số tiền lớn hay nhỏ không ảnh hưởng đến việc ánh xạ được chọn.

Chương trình này còn minh họa một view đảo của phạm vi, view tập khóa và một lớp bọc không cho sửa.

Tệp **RangeMapDemo.java**:

```java
import java.util.Collections;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;

public class RangeMapDemo {
    private static void expectIAE(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + ": no exception");
        } catch (IllegalArgumentException expected) {
            System.out.println(label + "=IllegalArgumentException");
        }
    }

    public static void main(String[] args) {
        TreeMap<Integer, String> source = new TreeMap<>();
        source.put(50, "outside-low");
        source.put(100, "A");
        source.put(200, "B");
        source.put(300, "outside-high");

        NavigableMap<Integer, String> window =
                source.subMap(100, true, 300, false);
        System.out.println("initial=" + window);
        System.out.println("get300=" + window.get(300));
        System.out.println("remove300=" + window.remove(300));
        System.out.println("sourceKeeps300=" + source.containsKey(300));
        expectIAE("put300", () -> window.put(300, "blocked"));

        window.put(150, "C");
        window.put(100, "A2");
        source.put(250, "D");
        source.put(375, "outside-later");
        System.out.println("updated=" + window);

        NavigableMap<Integer, String> reverse = window.descendingMap();
        System.out.println("reverse=" + reverse);
        System.out.println("reverseKeys=" + reverse.navigableKeySet());
        System.out.println("reverseValues=" + reverse.values());
        System.out.println("reverseHead200=" + reverse.headMap(200, false));
        System.out.println("reverseFloor175=" + reverse.floorKey(175));
        System.out.println("reverseCeiling175=" + reverse.ceilingKey(175));

        System.out.println("pollReverseFirst=" + reverse.pollFirstEntry());
        System.out.println("afterPoll=" + window);
        NavigableSet<Integer> keys = window.navigableKeySet();
        keys.remove(150);
        System.out.println("afterKeyRemoval=" + window);

        NavigableMap<Integer, String> readOnly =
                Collections.unmodifiableNavigableMap(window);
        source.put(175, "E");
        System.out.println("readOnlyLive=" + readOnly);
        System.out.println("firstOutside=" + source.firstEntry());
        window.clear();
        System.out.println("afterWindowClear=" + source);
        System.out.println("readOnlyAfterClear=" + readOnly);
    }
}
```

Lệnh biên dịch và chạy:

```shell
javac --release 21 -encoding UTF-8 RangeMapDemo.java
java RangeMapDemo
```

Stdout chính xác:

```text
initial={100=A, 200=B}
get300=null
remove300=null
sourceKeeps300=true
put300=IllegalArgumentException
updated={100=A2, 150=C, 200=B, 250=D}
reverse={250=D, 200=B, 150=C, 100=A2}
reverseKeys=[250, 200, 150, 100]
reverseValues=[D, B, C, A2]
reverseHead200={250=D}
reverseFloor175=200
reverseCeiling175=150
pollReverseFirst=250=D
afterPoll={100=A2, 150=C, 200=B}
afterKeyRemoval={100=A2, 200=B}
readOnlyLive={100=A2, 175=E, 200=B}
firstOutside=50=outside-low
afterWindowClear={50=outside-low, 300=outside-high, 375=outside-later}
readOnlyAfterClear={}
```

### 5.1. Chèn mới và thay giá trị đều đi qua cùng cửa sổ

window bao gồm khóa 100 và 200, loại khóa 300. window.put(150,"C") thêm một ánh xạ vào source; window.put(100,"A2") thay giá trị đang có ở source. source.put(250,"D") xuất hiện trong window, còn khóa 375 không xuất hiện.

window.get(300) trả null và window.remove(300) trả null trong ví dụ này, dù source thật sự có ánh xạ cho 300. Khóa ấy nằm ngoài góc nhìn của window. Ngược lại, window.put(300,...) bị từ chối vì nó cố ghi ở ngoài khoảng.

Đối với TreeMap cho phép giá trị null, get() trả null không đủ phân biệt “không có ánh xạ” với “có ánh xạ mang giá trị null”. Khi cần phân biệt trong view, dùng containsKey() trên chính view đó. Phạm vi không làm thay đổi quy tắc cơ bản này của Map.

Đừng dựa vào kết quả truy vấn source để kết luận một khóa có thể ghi qua window. Điều kiện là khóa nằm trong khoảng của window, kể cả khi source đã có khóa ấy.

### 5.2. View của view vẫn thay đổi nguồn

reverse = window.descendingMap() chỉ đổi thứ tự của cửa sổ [100,300). Khi gọi reverse.pollFirstEntry(), phần tử “đầu” theo chiều giảm dần là khóa 250. Ánh xạ 250 bị xóa khỏi source và window.

keys = window.navigableKeySet() là view các khóa trong cùng phạm vi. keys.remove(150) xóa toàn bộ ánh xạ 150 khỏi source. Nó không để lại một khóa với giá trị null.

Tập khóa này hỗ trợ xóa nhưng không hỗ trợ add()/addAll(): chỉ một khóa không cung cấp giá trị cần thiết để thêm ánh xạ. Vì cùng mang kiểu NavigableSet không có nghĩa mọi đối tượng hỗ trợ các phép thêm giống TreeSet.

reverse.values() duyệt giá trị theo thứ tự khóa giảm dần; nó không tự sắp xếp giá trị theo chữ cái hay theo một Comparator dành cho giá trị. Vì vậy kết quả D, B, C, A2 là hệ quả thứ tự khóa 250, 200, 150, 100.

### 5.3. Lớp bọc không cho sửa không ngăn nguồn thay đổi

readOnly được tạo bằng Collections.unmodifiableNavigableMap(window). Nó chặn các thao tác cập nhật thông qua giao diện được trả về, nhưng không giữ một bản chụp của window.

source.put(175,"E") vẫn hiện trong readOnly. Sau window.clear(), readOnly rỗng. Chương trình cho thấy rõ hai thuộc tính khác nhau:

- **Không sửa được qua tham chiếu này**: thuộc tính của lớp bọc.
- **Không thay đổi khi nguồn thay đổi**: thuộc tính cần có bản sao hoặc cơ chế lưu phiên bản riêng.

Các lớp bọc của [Collections](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collections.html#unmodifiableNavigableMap(java.util.NavigableMap)) giữ việc đọc qua cấu trúc được bọc. Bọc một view không tự tách dữ liệu.

## 6. Đảo thứ tự: đảo cả ý nghĩa điều hướng

### 6.1. descending và reversed

| Đối tượng | API đã có ở Java 17 | API từ Java 21 | Kiểu kết quả |
|---|---|---|---|
| TreeSet/NavigableSet | descendingSet() | reversed() | NavigableSet |
| TreeMap/NavigableMap | descendingMap() | reversed() | NavigableMap |

Trong hai họ Navigable này, reversed() tương đương descendingSet()/descendingMap(). Với TreeSet và TreeMap, view đảo dùng chung nguồn, nên các cập nhật được phép tiếp tục phản ánh qua lại. Không cần duyệt rồi chèn lại toàn bộ dữ liệu chỉ để đọc từ cuối về đầu. Đặc tả tương đương nằm ở [NavigableSet.reversed](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/NavigableSet.html#reversed()) và [NavigableMap.reversed](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/NavigableMap.html#reversed()).

Đừng suy từ tính chất cụ thể này rằng mọi phương thức reversed() trên mọi cấu trúc Java đều có cùng bảo đảm cập nhật. Bài này đang xét cây và các view của chúng.

Đảo thứ tự hai lần khôi phục thứ tự ban đầu về mặt hành vi. Không dùng so sánh tham chiếu == làm điều kiện đúng của thuật toán; đặc tả không yêu cầu mọi triển khai phải trả đúng cùng một đối tượng sau hai lần đảo.

### 6.2. “Đầu” là đầu theo Comparator của view

Nguồn tăng dần có 10, 20, 30, 40, 50. Trong view giảm dần:

| Lời gọi trên view giảm dần | Kết quả |
|---|---|
| first() | 50 |
| last() | 10 |
| headSet(30,false) | 50, 40 |
| tailSet(30,true) | 30, 20, 10 |
| lower(30) | 40 |
| higher(30) | 20 |
| floor(35) | 40 |
| ceiling(35) | 30 |

lower(30) trả 40 không phải một ngoại lệ của số học. Theo thứ tự giảm dần, 40 đứng trước 30. lower tìm phần tử lớn nhất còn nhỏ hơn đối số **theo thứ tự đang dùng**; trong chiều giảm dần, kết quả nhìn bằng số thông thường nằm ở phía lớn hơn.

Tương tự, floor(35) đi về phía không vượt 35 theo thứ tự giảm dần, nên chọn 40; ceiling(35) chọn 30. Để tự tính, hãy viết dãy đang duyệt ra giấy trước khi đọc tên hàm theo cảm giác “sàn là số nhỏ”.

Đối với một view v và view đảo d của chính v, các quan hệ sau giúp kiểm tra suy luận, miễn là dùng cùng phạm vi dữ liệu hiện tại:

| Trên d | Tương ứng trên v |
|---|---|
| first | last |
| last | first |
| lower(x) | higher(x) |
| floor(x) | ceiling(x) |
| ceiling(x) | floor(x) |
| higher(x) | lower(x) |

Với map, thay bằng firstKey/lastKey, lowerKey/higherKey và floorKey/ceilingKey; các phương thức trả entry cũng đổi chiều tương ứng. Kết quả vẫn bị giới hạn trong phạm vi của v. Không so một view giảm dần của khoảng nhỏ với toàn bộ nguồn tăng dần rồi áp bảng một cách máy móc.

### 6.3. Đảo cây vốn đã có Comparator giảm dần

descendingSet() không có nghĩa tuyệt đối là “sắp số giảm dần”. Nó đảo **thứ tự hiện có**. Nếu TreeSet ban đầu dùng Comparator.reverseOrder() cho số nguyên, thứ tự nguồn đã là 50, 40, 30, 20, 10; view descending của nó sẽ duyệt 10, 20, 30, 40, 50.

Điều tương tự đúng với Comparator sắp mã, chuỗi theo độ dài hay đối tượng theo thời gian. Hãy xác định bộ so sánh của nguồn trước; đừng gắn cố định tên descending với cách in giá trị.

## 7. Ghép phạm vi với chiều đảo: phải đổi mốc và giữ cờ tương ứng

Có hai cách tạo view cho cùng khoảng dữ liệu:

1. Chọn phạm vi trên nguồn, sau đó đảo chiều.
2. Đảo chiều nguồn, sau đó chọn phạm vi trong chiều mới.

Cách thứ hai phải đổi vị trí hai mốc. Với nguồn tăng dần s, hai đoạn minh họa sau cùng chọn 20, 30, 40 và cùng duyệt 40, 30, 20:

```java
// Doan minh hoa; s la NavigableSet<Integer> theo thu tu tang dan.
NavigableSet<Integer> a = s.subSet(20, true, 50, false).descendingSet();
NavigableSet<Integer> b = s.descendingSet().subSet(50, false, 20, true);
```

Cờ “lấy 20” phải đi cùng mốc 20; cờ “bỏ 50” phải đi cùng mốc 50. Đổi vị trí mốc nhưng không đổi cờ sẽ chọn nhầm biên.

Biểu thức s.descendingSet().subSet(20,true,50,false) không còn hợp lệ vì 20 đứng sau 50 trong chiều giảm dần. Java không tự hiểu hai số ấy là “cận số học nhỏ/lớn”.

Quy tắc tổng quát đối với view và biên hợp lệ:

| Tạo từ view đảo của s | Cách nhìn tương đương từ s |
|---|---|
| descendingSet().subSet(b, bi, a, ai) | subSet(a, ai, b, bi).descendingSet() |
| descendingSet().headSet(k, inclusive) | tailSet(k, inclusive).descendingSet() |
| descendingSet().tailSet(k, inclusive) | headSet(k, inclusive).descendingSet() |

Map dùng cùng phép biến đổi với descendingMap, subMap, headMap và tailMap. Chẳng hạn window.descendingMap().headMap(200,false) ở chương trình trước chỉ còn khóa 250: “trước 200” theo chiều giảm dần chính là các khóa lớn hơn 200 trong window.

Các quan hệ này nói về nội dung, thứ tự và việc dùng chung dữ liệu, không khẳng định hai biểu thức trả cùng tham chiếu. Chúng cũng không cho phép nới biên ra ngoài một view cha đã bị giới hạn.

Một mẹo thực tế: nếu điều kiện nghiệp vụ được mô tả bằng khoảng số tăng dần hoặc khoảng thời gian từ sớm đến muộn, thường nên tạo khoảng theo chiều đó trước, rồi đảo để hiển thị. Cách viết này giữ mốc nghiệp vụ dễ đọc hơn.

## 8. Chương trình kết hợp: đảo phạm vi và sao chép đúng thứ tự

Ví dụ đầy đủ sau kiểm tra:

- Hai cách tạo cùng một view phạm vi đảo.
- Các phép head/tail và điều hướng theo chiều đảo.
- Bản sao giữ Comparator so với bản sao dùng thứ tự tự nhiên.
- Nguồn đổi nhưng bản sao không đổi.
- Bản sao không tiếp tục mang giới hạn của view ban đầu.
- API định vị đầu/cuối bị từ chối trên cấu trúc có thứ tự do phép so sánh quyết định.

Tệp **ReverseRangeDemo.java**, yêu cầu JDK 21 trở lên:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class ReverseRangeDemo {
    private static void expectUOE(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + ": no exception");
        } catch (UnsupportedOperationException expected) {
            System.out.println(label + "=UnsupportedOperationException");
        }
    }

    public static void main(String[] args) {
        TreeSet<Integer> source =
                new TreeSet<>(List.of(10, 20, 30, 40, 50));
        NavigableSet<Integer> descending = source.reversed();
        NavigableSet<Integer> rangeThenReverse =
                source.subSet(20, true, 50, false).reversed();
        NavigableSet<Integer> reverseThenRange =
                descending.subSet(50, false, 20, true);

        System.out.println("descending=" + descending);
        System.out.println("rangeThenReverse=" + rangeThenReverse);
        System.out.println("reverseThenRange=" + reverseThenRange);
        System.out.println("sameMembers=" +
                rangeThenReverse.equals(reverseThenRange));
        System.out.println("head30=" + descending.headSet(30, false));
        System.out.println("tail30=" + descending.tailSet(30, true));
        System.out.println("lower30=" + descending.lower(30));
        System.out.println("higher30=" + descending.higher(30));
        System.out.println("floor35=" + descending.floor(35));
        System.out.println("ceiling35=" + descending.ceiling(35));

        TreeSet<Integer> sortedCopy = new TreeSet<>(rangeThenReverse);
        Collection<Integer> widened = rangeThenReverse;
        TreeSet<Integer> naturalCopy = new TreeSet<>(widened);
        List<Integer> listCopy = new ArrayList<>(rangeThenReverse);
        System.out.println("sortedCopy=" + sortedCopy);
        System.out.println("naturalCopy=" + naturalCopy);
        System.out.println("listCopy=" + listCopy);

        source.add(45);
        System.out.println("viewAfterAdd45=" + rangeThenReverse);
        System.out.println("copyAfterAdd45=" + sortedCopy);
        sortedCopy.add(100);
        System.out.println("copyOutsideFormerRange=" + sortedCopy);
        System.out.println("sourceHas100=" + source.contains(100));
        expectUOE("addFirst", () -> descending.addFirst(99));
        System.out.println("doubleReverseEquals=" +
                descending.reversed().equals(source));

        TreeMap<Integer, String> map = new TreeMap<>();
        map.put(10, "A");
        map.put(20, "B");
        map.put(30, "C");
        NavigableMap<Integer, String> mapView =
                map.subMap(10, true, 30, false).reversed();
        TreeMap<Integer, String> sortedMapCopy = new TreeMap<>(mapView);
        Map<Integer, String> widenedMap = mapView;
        TreeMap<Integer, String> naturalMapCopy = new TreeMap<>(widenedMap);
        System.out.println("mapView=" + mapView);
        System.out.println("sortedMapCopy=" + sortedMapCopy);
        System.out.println("naturalMapCopy=" + naturalMapCopy);
        map.put(15, "D");
        System.out.println("mapViewAfterPut15=" + mapView);
        System.out.println("mapCopyAfterPut15=" + sortedMapCopy);
        expectUOE("putFirst", () -> mapView.putFirst(12, "X"));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 21 -encoding UTF-8 ReverseRangeDemo.java
java ReverseRangeDemo
```

Stdout chính xác:

```text
descending=[50, 40, 30, 20, 10]
rangeThenReverse=[40, 30, 20]
reverseThenRange=[40, 30, 20]
sameMembers=true
head30=[50, 40]
tail30=[30, 20, 10]
lower30=40
higher30=20
floor35=40
ceiling35=30
sortedCopy=[40, 30, 20]
naturalCopy=[20, 30, 40]
listCopy=[40, 30, 20]
viewAfterAdd45=[45, 40, 30, 20]
copyAfterAdd45=[40, 30, 20]
copyOutsideFormerRange=[100, 40, 30, 20]
sourceHas100=false
addFirst=UnsupportedOperationException
doubleReverseEquals=true
mapView={20=B, 10=A}
sortedMapCopy={20=B, 10=A}
naturalMapCopy={10=A, 20=B}
mapViewAfterPut15={20=B, 15=D, 10=A}
mapCopyAfterPut15={20=B, 10=A}
putFirst=UnsupportedOperationException
```

Các kết quả cùng thứ tự giữa rangeThenReverse và reverseThenRange được thấy trực tiếp ở hai dòng in danh sách. Riêng equals() của Set chỉ kiểm tra tập phần tử, không kiểm tra thứ tự duyệt; dòng sameMembers=true một mình chưa chứng minh thứ tự trùng nhau.

source.add(45) hiện trong view thành 45, 40, 30, 20. sortedCopy vẫn là 40, 30, 20. Khi sortedCopy.add(100), phần tử 100 được chấp nhận: đó đã là một TreeSet độc lập với Comparator giảm dần, không còn ràng buộc [20,50) của view gốc.

Ở nửa map, map.put(15,"D") thay đổi mapView nhưng không thay sortedMapCopy. Thứ tự của bản sao và liên kết với nguồn là hai thuộc tính cần kiểm tra riêng.

Nếu đang dùng Java 17, dùng descendingSet()/descendingMap() để tạo các view đảo. Không gọi reversed(), addFirst() hay putFirst() của ví dụ Java 21 trên nền API 17. Những API mới không cần thiết để hiểu hoặc sử dụng phạm vi TreeSet/TreeMap.

## 9. Sao chép view: giữ nội dung, thứ tự hay ràng buộc?

### 9.1. Constructor nhận SortedSet/SortedMap giữ bộ so sánh

Trong ReverseRangeDemo:

- rangeThenReverse có kiểu NavigableSet, là một SortedSet, nên new TreeSet<>(rangeThenReverse) chọn constructor nhận SortedSet và giữ thứ tự của view.
- widened có kiểu Collection, nên new TreeSet<>(widened) chọn constructor nhận Collection và sử dụng thứ tự tự nhiên.
- mapView có kiểu NavigableMap, là SortedMap, nên new TreeMap<>(mapView) chọn constructor nhận SortedMap và giữ Comparator.
- widenedMap có kiểu Map, nên new TreeMap<>(widenedMap) chọn constructor nhận Map và sử dụng thứ tự tự nhiên của khóa.

Cùng một đối tượng lúc chạy nhưng **kiểu khai báo của đối số** dẫn tới chọn constructor khác khi biên dịch. Kết quả in [40,30,20] và [20,30,40] làm khác biệt này nhìn thấy rõ.

Với phần tử/khóa tùy chỉnh không triển khai Comparable, chọn nhầm constructor dùng thứ tự tự nhiên còn có thể gây ClassCastException, dù nguồn hoạt động tốt với Comparator riêng.

Nếu API đầu vào chỉ lộ Collection hoặc Map, muốn giữ một thứ tự tùy chỉnh thì phải truyền Comparator đã biết một cách tường minh vào cấu trúc đích rồi mới thêm dữ liệu. Không đoán Comparator từ vài phần tử được in ra.

### 9.2. Sao chép không sao chép luôn “hàng rào”

TreeSet mới được dựng từ view không lưu lại giới hạn cũ. TreeMap mới cũng vậy. Bản sao giữ các phần tử/ánh xạ được chọn lúc sao chép, và có thể giữ bộ so sánh tùy constructor, nhưng không tự có lại điều kiện chỉ được chèn trong khoảng ban đầu.

Nếu muốn bản sao riêng vẫn chỉ cho phép sửa trong một khoảng, đó là yêu cầu mới: tạo cấu trúc riêng rồi tạo view phạm vi trên chính cấu trúc riêng ấy, hoặc đóng gói các phương thức cập nhật với kiểm tra nghiệp vụ phù hợp.

Phân biệt ba mục tiêu:

| Mục tiêu | Lựa chọn |
|---|---|
| Cập nhật trực tiếp phần dữ liệu trong nguồn | View phạm vi |
| Cùng nguồn, chỉ đọc theo chiều ngược | View đảo |
| Dữ liệu riêng, còn khả năng tìm kiếm có thứ tự | TreeSet/TreeMap mới với Comparator đúng |
| Giữ thứ tự duyệt hiện tại thành dãy độc lập | ArrayList mới từ set/view |
| Chặn sửa qua tham chiếu trả ra nhưng vẫn theo dõi nguồn | Lớp bọc unmodifiable trên view |
| Không theo dõi nguồn và chặn sửa cấu trúc qua tham chiếu trả ra | Tạo bản sao riêng rồi bọc bản sao |

ArrayList từ view giữ thứ tự gặp phần tử lúc sao chép, nhưng không trở thành một tập có điều hướng; nó cho phép trùng và không tự duy trì thứ tự sau các lần thêm. Bản sao bằng Set.copyOf()/Map.copyOf() cũng không phải cam kết giữ Comparator hay khả năng điều hướng của cây.

### 9.3. Bản sao thường chỉ tách cấu trúc

Sao chép một TreeMap<Integer,MutableValue> sang TreeMap khác không tự nhân bản các MutableValue. Hai map có các cấu trúc ánh xạ riêng nhưng vẫn có thể giữ tham chiếu tới cùng đối tượng giá trị.

Tương tự, sao chép set không tự nhân bản từng phần tử. Nếu cần ảnh chụp độc lập cả trạng thái bên trong, phải chọn dữ liệu bất biến hoặc thiết kế phép sao chép đối tượng phù hợp. Không gọi một bản sao cấu trúc là “bất biến sâu”.

Tạo bản sao rồi bọc unmodifiable chỉ đủ để chặn cập nhật cấu trúc qua lớp bọc. Nó không tự làm các đối tượng được chứa trở nên bất biến và không bảo đảm ảnh chụp nhất quán nếu có luồng khác đang sửa nguồn mà thiếu phối hợp.

## 10. Cập nhật qua view: phạm vi là một điều kiện, không phải giao dịch

### 10.1. Đảo thứ tự không cho quyền tự đặt vị trí

Cây duy trì thứ tự theo phép so sánh. Thêm qua view đảo vẫn dùng add()/put() và vị trí do Comparator quyết định, đồng thời phải thỏa phạm vi nếu view đã bị giới hạn.

Java 21 có addFirst/addLast ở họ sequenced collection và putFirst/putLast ở họ sequenced map. TreeSet và TreeMap không hỗ trợ định vị tường minh như vậy: các phép này gây UnsupportedOperationException. Đảo chiều không thay đổi quy tắc ấy.

Trong chương trình, descending.addFirst(99) và mapView.putFirst(12,"X") đều bị từ chối. Để thêm 12 vào mapView, thao tác phù hợp là put(12,"X"), vì 12 thuộc khoảng [10,30); nó tự xuất hiện sau 15 và trước 10 theo thứ tự giảm dần hiện tại.

### 10.2. Các phép xóa có thể ảnh hưởng nhiều dữ liệu nguồn

Không chỉ remove() mới thay đổi nguồn. Các phép sau, khi được cấu trúc/view hỗ trợ, cũng tác động vào phần nguồn đang nhìn thấy:

- clear() xóa toàn bộ phạm vi hiện tại.
- pollFirst()/pollLast() trên set và pollFirstEntry()/pollLastEntry() trên map lấy rồi xóa đầu/cuối của view.
- removeIf(), retainAll(), removeAll() hoặc xóa qua iterator của set có thể loại nhiều phần tử trong khoảng.
- Xóa qua tập khóa hoặc tập entry của một subMap có thể xóa ánh xạ trong nguồn.

Do đó trả một view có thể sửa cho nơi gọi tương đương trao quyền sửa một phần dữ liệu của mình. Nếu chức năng chỉ nên đọc, hãy thể hiện điều đó bằng API/lớp bọc phù hợp.

Các entry lấy bằng phương thức điều hướng như TreeMap.firstEntry()/floorEntry() là ảnh chụp ánh xạ, không phải một lối tùy ý gọi setValue() để cập nhật cây. Muốn thay giá trị, dùng put() trên map/view với khóa hợp lệ. Đừng suy mọi Map.Entry đều có hành vi cập nhật giống nhau.

### 10.3. Không giả định thao tác nhiều phần tử sẽ hoàn tác khi lỗi

Một lệnh addAll()/putAll() lên view có thể gặp phần tử hoặc khóa ngoài khoảng. Không có bảo đảm giao dịch rằng mọi cập nhật hợp lệ trước đó sẽ được hoàn tác nếu phần sau gây lỗi.

Với yêu cầu “tất cả hợp lệ mới cập nhật”, kiểm tra toàn bộ dữ liệu trước bằng cùng quy tắc biên/Comparator rồi mới ghi trong cơ chế đồng bộ hoặc giao dịch do ứng dụng sở hữu. Việc kiểm tra rồi ghi riêng lẻ cũng không tự chống được thay đổi đồng thời.

Không bắt IllegalArgumentException rồi tiếp tục coi nguồn như chưa bị động tới sau một thao tác hàng loạt. Tùy thuật toán đang dùng, nguồn có thể đã nhận một phần cập nhật.

Phạm vi là điều kiện chọn dữ liệu và kiểm tra chỗ ghi; nó không thay thế ràng buộc nghiệp vụ tổng thể, xử lý lỗi hay cơ chế nguyên tử.

## 11. Các giới hạn thực tế khi giữ và duyệt view

### 11.1. Không sửa dữ liệu so sánh khi phần tử/khóa còn trong cây

Khoảng được quyết định bởi thứ tự của cây. Nếu đối tượng phần tử của TreeSet hoặc khóa của TreeMap có trường tham gia Comparator, đổi trực tiếp trường ấy khi đối tượng còn trong cây có thể làm thứ tự cấu trúc sai với trạng thái hiện tại.

Không thể dùng view để sửa chữa tình huống này. Chọn phần tử/khóa bất biến; nếu cần thay thuộc tính sắp xếp, xóa bằng cách hợp lệ trước, thay đổi đối tượng rồi thêm lại. Comparator cũng phải ổn định trong quá trình cây được sử dụng.

Đổi trường của một giá trị trong map không tự đổi phạm vi theo khóa. Nếu nghiệp vụ muốn lọc theo thuộc tính của giá trị, subMap không phải công cụ trực tiếp đáp ứng yêu cầu ấy; cần chỉ mục theo khóa phù hợp hoặc phép lọc khác.

### 11.2. View sống không đồng nghĩa với iterator chịu mọi sửa đổi

Một view vẫn có thể dùng để quan sát dữ liệu sau khi nguồn được cập nhật tuần tự. Nhưng iterator đang đi dở trên nguồn/view là đối tượng khác với bản thân view.

TreeSet và TreeMap không tự đồng bộ giữa các luồng. Sửa cấu trúc khi đang duyệt qua tham chiếu khác có thể khiến iterator báo ConcurrentModificationException. Cơ chế phát hiện này là cố gắng phát hiện lỗi, không phải bảo đảm đồng bộ và không nên là điều kiện đúng của chương trình.

Nếu cần xóa đúng phần tử đang duyệt và iterator hỗ trợ, dùng iterator.remove(). Khi cần cập nhật theo đợt, thường dễ kiểm soát hơn nếu xác định dữ liệu cần xử lý rồi thực hiện cập nhật trong một bước có quyền sở hữu/đồng bộ rõ ràng.

Tạo view đảo, view phạm vi hoặc lớp bọc unmodifiable không biến TreeMap thành ConcurrentNavigableMap. Bản sao cũng chỉ là ảnh chụp đáng tin khi việc đọc nguồn lúc sao chép được phối hợp đúng với các bên ghi.

### 11.3. Đừng đánh đồng chi phí tạo view với chi phí xử lý dữ liệu

Tạo view không chủ động sao chép mọi phần tử của khoảng sang một cây mới. Vì vậy nó phù hợp khi cần làm việc với cửa sổ dữ liệu hiện tại.

Tuy nhiên, duyệt k phần tử vẫn phải đọc k phần tử; xóa một khoảng có thể phải xóa nhiều ánh xạ; sao chép phạm vi vẫn phải tạo cấu trúc mới chứa dữ liệu được chọn. Không suy ra rằng size(), clear() hay mọi thao tác trên view đều O(1).

Cũng không coi một view nhỏ là một bản lưu nhỏ độc lập: vì view liên kết với nguồn, giữ nó lâu có thể giữ cả cấu trúc nguồn trong vòng đời bộ nhớ. Nếu chỉ cần vài dữ liệu lịch sử và muốn giải phóng nguồn lớn, một bản sao thích hợp có thể thể hiện mục đích tốt hơn.

## 12. Áp dụng: cửa sổ thời gian và xử lý theo đợt

Giả sử TreeMap lưu sự kiện theo Instant, mỗi khóa đại diện một thời điểm. Nhu cầu “các sự kiện từ đầu ngày, không lấy đầu ngày kế tiếp, hiển thị mới nhất trước” có ba bước rõ ràng:

1. Xác định hai mốc Instant bằng quy tắc múi giờ của nghiệp vụ.
2. Tạo subMap(start,true,nextStart,false) theo thứ tự thời gian tăng dần.
3. Lấy descendingMap() của phạm vi để hiển thị ngược.

Khoảng đóng/mở [start,nextStart) cho phép hai ngày liền nhau ghép nối mà không cùng nhận một sự kiện tại ranh giới. Không cần tự tính “giây cuối cùng” hoặc “nano giây cuối cùng” của ngày. Mốc thời gian cụ thể vẫn phải được tạo đúng múi giờ; API phạm vi không giải quyết chuyển đổi lịch và múi giờ thay ứng dụng.

Sau khi có phạm vi, hãy quyết định quyền cập nhật:

- Một màn hình phải theo dữ liệu đang thay đổi có thể dùng view được bảo vệ khỏi sửa qua phía đọc, với cơ chế đồng bộ phù hợp.
- Một báo cáo cố định cần tạo bản sao vào thời điểm được kiểm soát.
- Một tác vụ dọn dữ liệu cũ có thể dùng headMap(cutoff,false).clear(), nhưng đó là xóa dữ liệu nguồn trước cutoff, cần đúng quyền sở hữu nghiệp vụ.
- Một tác vụ lấy sự kiện mới nhất trong khoảng có thể dùng pollFirstEntry() trên view giảm dần; sau lời gọi, sự kiện đó không còn trong nguồn.

Nếu nhiều sự kiện có cùng thời điểm, TreeMap<Instant,Event> chỉ giữ một giá trị cho một khóa theo phép so sánh. Muốn giữ tất cả, chọn giá trị là danh sách sự kiện hoặc khóa ghép có thứ tự phân biệt thêm mã sự kiện. View không thay đổi tính duy nhất của khóa.

Biên của view cố định theo các mốc đã truyền. Nó không tự “trượt theo đồng hồ” qua ngày mới. Muốn một cửa sổ “24 giờ gần nhất”, phải tính lại mốc theo yêu cầu và tạo view tương ứng; tính sống chỉ nói dữ liệu trong khoảng hiện tại còn dùng chung nguồn.

## 13. Những lỗi cần nhận ra trong lúc đọc code

| Cách hiểu/lựa chọn sai | Hậu quả | Cách xử lý |
|---|---|---|
| Xem subSet/subMap là bản sao | Sửa view làm nguồn đổi ngoài dự tính | Xác định có cần cấu trúc độc lập ngay khi nhận dữ liệu |
| Nghĩ subMap lọc theo giá trị | Chọn nhầm tập ánh xạ | Xây khóa/chỉ mục theo điều kiện cần tìm |
| Nhớ sai biên của dạng rút gọn | Thừa hoặc thiếu dữ liệu tại ranh giới | Dùng dạng boolean khi cần đọc rõ biên |
| Đảo chiều nhưng giữ nguyên thứ tự hai mốc | IllegalArgumentException | Đổi vị trí mốc và mang cờ lấy/bỏ theo mốc |
| Hiểu head là các số nhỏ hơn trong mọi view | Kết quả phía đối diện dự tính | Đọc head theo Comparator hiện tại |
| Dùng view con để tự động cắt một khoảng vượt cha | Lỗi khi tạo phạm vi con | Tính giao biên hoặc tạo từ nguồn đủ rộng |
| Nghĩ view rỗng đã tách khỏi nguồn | Dữ liệu mới xuất hiện bất ngờ | Phân biệt rỗng hiện tại với bản sao |
| Nghĩ clear chỉ xóa đối tượng trung gian | Xóa dữ liệu thật trong nguồn | Kiểm tra phạm vi và quyền ghi trước khi xóa |
| Mở rộng kiểu đầu vào thành Collection/Map khi sao chép | Mất Comparator tùy chỉnh | Chọn constructor nhận SortedSet/SortedMap hoặc truyền Comparator |
| Nghĩ bản sao tiếp tục chặn phần tử ngoài khoảng cũ | Thêm được dữ liệu ngoài phạm vi ban đầu | Tạo lại ràng buộc trên cấu trúc riêng nếu cần |
| Bọc unmodifiable rồi gọi là bản chụp | Nguồn đổi, dữ liệu đọc vẫn đổi | Sao chép trước nếu cần tách liên kết |
| Dùng equals để kiểm tra thứ tự của Set/Map | Hai thứ tự khác nhau vẫn bằng nhau | So dãy duyệt khi yêu cầu là thứ tự |
| Dựa vào ConcurrentModificationException để điều phối | Hành vi đồng thời thiếu bảo đảm | Dùng quyền sở hữu và cơ chế đồng bộ đúng |

## 14. Bài tập tự kiểm tra và lời giải

### Bài 1 — Bốn biên, không đổi dữ liệu

Nguồn tăng dần chứa 1, 2, 3, 4, 5, 6. Viết kết quả theo thứ tự duyệt cho:

1. subSet(2,false,5,true).
2. headSet(3).
3. tailSet(3).
4. subSet(4,true,4,true).
5. subSet(4,false,4,true).

**Lời giải:** lần lượt là [3,4,5], [1,2], [3,4,5,6], [4], []. Hai trường hợp cuối có cùng mốc nhưng khác việc lấy mốc.

### Bài 2 — Nguồn và phạm vi rỗng

Nguồn có 10, 50. Tạo v = subSet(20,true,40,false), sau đó thêm 30 và 45 qua nguồn. v là gì? Nếu gọi v.clear(), nguồn còn gì?

**Lời giải:** v ban đầu rỗng nhưng sau khi thêm hiện [30]. 45 nằm ngoài khoảng. v.clear() xóa 30 khỏi nguồn, để lại [10,45,50]. View vẫn giữ khoảng [20,40), không mất kết nối.

### Bài 3 — Biên cha đang mở

p là view [10,40) trên nguồn tăng dần. So sánh p.headSet(40,false), p.headSet(40,true) và p.headSet(50,false).

**Lời giải:** trường hợp đầu hợp lệ và không mở rộng phạm vi; trường hợp thứ hai cố nhận 40 đã bị cha loại, trường hợp thứ ba dùng mốc 50 vượt biên cha. Hai trường hợp sau gây IllegalArgumentException, không phải tự cắt về [10,40).

### Bài 4 — Phạm vi đảo tương đương

Nguồn tăng dần có 10, 20, 30, 40, 50. Muốn cùng dữ liệu và thứ tự với s.subSet(20,false,50,true).descendingSet(), hãy viết từ s.descendingSet().

**Lời giải:** s.descendingSet().subSet(50,true,20,false), kết quả [50,40,30]. Cờ true vẫn đi cùng 50; cờ false vẫn đi cùng 20. Nếu chỉ đổi thứ tự mốc mà giữ vị trí hai cờ, kết quả sẽ sai ở ranh giới.

### Bài 5 — Điều hướng trên chiều giảm dần

d là view giảm dần của nguồn tăng dần [10,20,30,40,50]. Tính d.headSet(30,true), d.floor(25), d.ceiling(25) và phần tử bị d.pollLast() xóa.

**Lời giải:** headSet là [50,40,30]; floor(25) là 30; ceiling(25) là 20; pollLast() xóa 10 khỏi nguồn. “Cuối” của d là phần tử nhỏ nhất theo số thông thường trong ví dụ này.

### Bài 6 — TreeMap chọn khóa

Nguồn chứa 1→"Z", 2→"A", 3→"M". Tạo w = subMap(1,true,3,false), rồi đọc w.descendingMap().values(). Nếu gọi w.navigableKeySet().remove(2), nguồn đổi thế nào?

**Lời giải:** dãy giá trị là A, Z vì khóa duyệt theo 2, 1. Xóa khóa 2 qua tập khóa xóa cả ánh xạ 2→"A" khỏi nguồn; còn 1→"Z" và 3→"M". Khóa 3 chưa từng nằm trong w, dù giá trị của nó có thể giống một giá trị trong khoảng.

### Bài 7 — Cùng view, constructor khác

rev có kiểu NavigableSet<Integer> và hiện [9,7,5]. So sánh new TreeSet<>(rev) với việc gán rev vào Collection<Integer> rồi truyền biến đó vào new TreeSet<>(...).

**Lời giải:** cách đầu chọn constructor SortedSet và giữ thứ tự giảm dần; cách sau chọn constructor Collection và dùng thứ tự tự nhiên, nên duyệt [5,7,9]. Cả hai đều tách cấu trúc khỏi nguồn; không bản nào tự giữ giới hạn chèn của rev. Kết luận giữ Comparator phụ thuộc constructor được chọn, không chỉ phụ thuộc tên TreeSet.

### Bài 8 — Chọn API cho nơi gọi

Một dịch vụ lưu TreeMap theo mã. Nơi gọi chỉ được xem khoảng [100,200), cần dữ liệu không tự thay đổi sau khi trả về và không được sửa cấu trúc nhận được. Nên trả unmodifiableNavigableMap(source.subMap(...)) hay tạo bản sao rồi bọc?

**Lời giải:** cần tạo bản sao của phạm vi trong điều kiện đọc nguồn nhất quán rồi bọc bản sao. Chỉ bọc view sẽ chặn thao tác ghi qua tham chiếu đó nhưng vẫn thấy cập nhật nguồn. Nếu giá trị là đối tượng có thể sửa, còn phải quyết định có cần sao chép giá trị hoặc chuyển sang dữ liệu bất biến hay không.

### Bài 9 — Comparator nguồn vốn đã đảo

Nguồn dùng Comparator.reverseOrder() cho Integer và có 1, 2, 3, 4. source.headSet(3,false) hiện gì? source.descendingSet().headSet(3,false) hiện gì?

**Lời giải:** nguồn duyệt [4,3,2,1], nên head trước 3 là [4]. View đảo duyệt [1,2,3,4], nên head trước 3 là [1,2]. Không thể tính kết quả đúng chỉ từ chữ head hoặc descending mà bỏ qua Comparator của đối tượng nhận lời gọi.

## 15. Tài liệu để đối chiếu tiếp

Các giải thích và chương trình trong bài có thể kiểm tra với những hợp đồng API chính thức sau:

- [TreeSet — constructor, phạm vi, descendingSet và giới hạn thao tác](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeSet.html).
- [TreeMap — constructor, subMap, descendingMap và các phương thức điều hướng](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeMap.html).
- [NavigableSet — biên tường minh và reversed từ Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/NavigableSet.html).
- [NavigableMap — phạm vi theo khóa và view đảo](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/NavigableMap.html).
- [Collections — các lớp bọc không cho sửa](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collections.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-copies-005">Sao chép bằng constructor, addAll và putAll</a>

<details>
<summary>Xem chi tiết</summary>

# Sao chép bằng constructor, addAll và putAll

## 1. Trước khi sao chép, cần nói rõ thứ gì phải độc lập

Một hàm nhận danh sách công việc và muốn sắp xếp riêng để dựng báo cáo. Nếu nó chỉ gán `List<Task> report = source;` rồi sửa danh sách, cấu trúc nguồn cũng bị sửa vì hai biến cùng tham chiếu một đối tượng.

Tạo một collection mới giải quyết được việc chia sẻ **cấu trúc chứa**, nhưng không mặc nhiên giải quyết việc chia sẻ **các đối tượng phần tử**. Đây là hai mức độc lập cần phân biệt trước khi chọn cách sao chép.

Ba công cụ thường gặp là:

- constructor nhận nguồn, chẳng hạn `new ArrayList<>(source)` hoặc `new LinkedHashMap<>(source)`;
- `destination.addAll(source)` để thêm các phần tử vào collection đích đã có;
- `destination.putAll(sourceMap)` để đưa các ánh xạ vào map đích đã có.

Bài này giải thích ý nghĩa, kiểu trả về, dữ liệu trùng, khóa đụng nhau, thứ tự, comparator, khả năng sửa và các giới hạn về sao chép sâu/đồng thời. Mọi ví dụ đầy đủ bên dưới tương thích **Java 17 trở lên**.

## 2. Gán tham chiếu, sao chép cấu trúc và sao chép phần tử

### 2.1. Gán không tạo collection mới

~~~java
List<String> alias = source;
~~~

Đây là đoạn minh họa trong phương thức có các biến và import phù hợp. `alias == source` là `true`. Thêm/xóa qua một biến tác động cùng đối tượng mà biến kia đang nhìn.

Không có bước “đợi đến lúc sửa rồi Java tự sao chép”. Các collection Java thông thường không có hành vi tự tách cấu trúc theo kiểu đó.

### 2.2. Constructor tạo cấu trúc chứa mới

~~~java
List<String> copy = new ArrayList<>(source);
~~~

`copy` là một danh sách khác với `source`. Thêm hoặc xóa phần tử khỏi một danh sách không tự thêm hoặc xóa ở danh sách kia.

Constructor `ArrayList(Collection)` tạo danh sách chứa các phần tử nguồn theo thứ tự iterator của nguồn. Lớp đích là danh sách mảng động, cho phép các thao tác danh sách thông thường và `null`. [Java 17: ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

Tuy nhiên, nếu phần tử là đối tượng thì danh sách mới nhận các tham chiếu đến chúng. Đó là **sao chép nông (shallow copy)**.

### 2.3. Sao chép nông chia sẻ đối tượng phần tử

Mô hình:

~~~text
source ----> danh sách S ----+
                            +----> đối tượng công việc T
copy ------> danh sách C ----+

S và C khác nhau.
Tham chiếu phần tử trong S và C cùng trỏ đến T.
~~~

Nếu đổi trạng thái của `T`, cả hai danh sách đều có thể thấy trạng thái ấy vì cùng trỏ đến nó. Nếu thay một vị trí trong `C` bằng đối tượng khác, bạn thay cấu trúc tham chiếu của `C`; vị trí tương ứng trong `S` không tự bị thay.

Các ví dụ chỉ dùng `String` thường làm người học bỏ sót điều này vì chuỗi không cho sửa nội dung tại chỗ. Chương trình chính dùng một lớp `Note` có trường thay đổi được để kiểm chứng rõ.

### 2.4. Sao chép sâu là trách nhiệm của mô hình dữ liệu

Nếu cần phần tử độc lập, phải tạo phần tử mới theo quy tắc của ứng dụng. Với một đối tượng phẳng chỉ chứa một chuỗi bất biến, tạo `new Note(old.text)` có thể đủ.

Với đối tượng chứa danh sách con, tham chiếu dùng chung hoặc chu trình, cần xác định sao chép đến tầng nào và giữ quan hệ dùng chung ra sao. Constructor collection, `addAll` và `putAll` không tự duyệt toàn bộ đồ thị đối tượng để làm việc ấy.

Không dùng từ “bản sao độc lập” mà bỏ trống câu hỏi độc lập về cấu trúc hay cả trạng thái phần tử.

## 3. Sao chép bằng constructor: lớp đích quyết định hợp đồng

Ví dụ phổ biến:

~~~java
List<E> listCopy = new ArrayList<>(sourceCollection);
Set<E> setCopy = new LinkedHashSet<>(sourceCollection);
Map<K, V> mapCopy = new LinkedHashMap<>(sourceMap);
~~~

Đây là các mẫu với tham số kiểu `E`, `K`, `V` do ngữ cảnh khai báo. Constructor nhận collection hoặc map là quy ước phổ biến của các lớp tổng quát trong JDK; nó không phải một phương thức của chính giao diện `Collection` hay `Map`.

Cần đọc theo ý nghĩa: “tạo **loại đích này** từ dữ liệu nguồn”, không phải “nhân bản nguyên trạng mọi thuộc tính của nguồn”.

### 3.1. Đổi loại collection có thể đổi số phần tử và quy tắc thứ tự

Nguồn danh sách:

~~~text
[B, A, B, C]
~~~

Các lựa chọn đích có ý nghĩa khác nhau:

| Đích | Kết quả theo hợp đồng tương ứng |
| --- | --- |
| `new ArrayList<>(source)` | Giữ các lần xuất hiện và thứ tự duyệt: `[B, A, B, C]` |
| `new LinkedHashSet<>(source)` | Loại trùng theo set, giữ thứ tự chèn đầu tiên: `[B, A, C]` |
| `new TreeSet<>(source)` với chuỗi tự nhiên | Loại trùng và sắp xếp: `[A, B, C]` |

`LinkedHashSet` duy trì thứ tự chèn, và thêm lại phần tử đã có không đổi vị trí của nó. [Java 17: LinkedHashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html).

Số phần tử giảm khi đổi list thành set không nhất thiết là lỗi sao chép; có thể đó chính là mục tiêu loại trùng. Nhưng nếu dữ liệu lặp biểu thị nhiều lần đặt hàng, đổi thành set sẽ làm mất ý nghĩa. Chọn theo mô hình nghiệp vụ.

### 3.2. Constructor nhận capacity không nhận dữ liệu

Hai lệnh trông gần nhau nhưng khác việc:

~~~java
new ArrayList<String>(10);
new ArrayList<String>(existingItems);
~~~

Lệnh đầu tạo một danh sách rỗng với dung lượng ban đầu dự kiến. `size()` vẫn bằng 0. Nó không tạo mười phần tử `null` và cũng không tạo một bản sao nào.

Lệnh thứ hai nhận collection nguồn để tạo danh sách chứa dữ liệu. Đừng dùng constructor số nguyên rồi gọi `set(0, value)` khi chưa có phần tử ở vị trí 0.

### 3.3. Chính sách sửa của nguồn không tự đi theo bản sao

Một `List.of("A")` không cho thêm/xóa. Nhưng `new ArrayList<>(thatList)` tạo một danh sách mới có quyền sửa theo `ArrayList`.

Tương tự, nguồn map không sửa được có thể được đọc để dựng một `LinkedHashMap` sửa được. Việc này không mở quyền sửa nguồn; nó chỉ tạo một đối tượng mới mang hợp đồng khác.

Ngược lại, constructor collection không tự tạo một bản sao không sửa được. Nếu API cần đầu ra chỉ đọc, phải chọn factory hoặc lớp bọc phù hợp sau khi xác định yêu cầu về `null`, thứ tự và vòng đời.

## 4. addAll: thêm vào đích hiện có, không thay toàn bộ đích

Chữ ký tổng quát:

~~~java
boolean addAll(Collection<? extends E> source);
~~~

Nếu hoàn tất, phương thức đã áp dụng việc thêm các phần tử nguồn theo quy tắc của collection đích. Cờ trả về nói collection đích có thay đổi hay không.

**addAll không tự clear đích trước.** Ví dụ:

~~~text
đích trước              [start]
nguồn                   [B, A, B, C]
đích.addAll(nguồn)
đích sau                [start, B, A, B, C]
~~~

Phần `start` vẫn còn. Nếu mục tiêu là một cấu trúc mới chỉ chứa nguồn, constructor thường thể hiện ý định trực tiếp hơn việc vô tình thêm vào một đích còn dữ liệu cũ.

`Collection.addAll` là thao tác tùy chọn; nguồn không được bị sửa trong lúc thao tác đang diễn ra theo hợp đồng chung. Cờ trả về là `true` khi đích thay đổi. [Java 17: Collection.addAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#addAll(java.util.Collection)).

### 4.1. Trên List: giữ các lần xuất hiện và chèn theo trình tự

Với danh sách thường hỗ trợ thêm, `addAll(source)` nối các phần tử vào cuối theo thứ tự iterator nguồn.

`List` còn có overload:

~~~java
boolean addAll(int index, Collection<? extends E> source);
~~~

Nó chèn nhóm vào vị trí `index`, dịch phần phía sau. Vị trí chèn có thể từ `0` đến `size()`, bao gồm `size()` là nối cuối.

Ví dụ đích `[start, B, A]`, chèn `[X, Y]` tại `1` cho `[start, X, Y, B, A]`. Bên trong nhóm, `X` vẫn trước `Y`. [Java 17: List.addAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#addAll(int,java.util.Collection)).

Nếu nguồn là một set không bảo đảm thứ tự, list đích chỉ giữ trình tự nguồn được quan sát trong lần sao chép; nó không tự khôi phục một thứ tự nghiệp vụ chưa được nguồn bảo đảm.

### 4.2. Trên Set: không tăng số thành viên với phần tử đã có

Với `LinkedHashSet` đang là `[B, A, C]`:

~~~text
addAll([B, A])          false, vẫn [B, A, C]
addAll([C, D])          true, thành [B, A, C, D]
~~~

Cờ `false` không nói “không đọc nguồn” hay “sao chép gặp lỗi”. Nó nói không có thay đổi thành viên theo thao tác được thực hiện. Các giá trị đã tồn tại không tạo bản trùng.

Tính duy nhất do hợp đồng set và cách triển khai quyết định. Nếu dùng phần tử tự định nghĩa trong set băm, cần `equals`/`hashCode` đúng; nếu dùng set sắp xếp, quy tắc so sánh cũng liên quan. `Set` không cho các phần tử trùng theo hợp đồng của nó. [Java 17: Set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html).

### 4.3. addAll không sao chép sâu và không chuyển quyền sở hữu

Nếu nguồn chứa đối tượng `Note`, `addAll` đưa các tham chiếu đến chúng vào đích. Xóa một phần tử khỏi nguồn sau khi hoàn tất không tự xóa nó khỏi đích, nhưng sửa chính đối tượng dùng chung có thể thấy qua cả hai.

Thao tác cũng không “lấy” phần tử ra khỏi nguồn. Một nguồn vẫn giữ dữ liệu nếu bạn không chủ động xóa bằng một hành động riêng.

### 4.4. Nguồn là view của đích: tránh chồng lấn không rõ hợp đồng

Hợp đồng chung nêu hành vi không xác định khi nguồn bị sửa trong lúc `addAll` đang chạy; trường hợp source chính là collection đích không rỗng nằm trong cảnh báo này.

Không dùng việc một phiên bản `ArrayList` có thể xử lý một trường hợp `list.addAll(list)` để viết helper tổng quát dựa vào nó. Đích cũng có thể chồng lấn nguồn qua một `subList` hoặc view khác.

Nếu thật sự muốn nhân đôi các phần tử hiện tại, tạo dữ liệu trung gian độc lập về cấu trúc rồi thêm:

~~~java
List<E> snapshot = new ArrayList<>(items);
items.addAll(snapshot);
~~~

Đây là mẫu bên trong ngữ cảnh có kiểu `E`, nguồn/đích hỗ trợ và không bị sửa đồng thời. Dữ liệu phần tử vẫn được chia sẻ theo sao chép nông.

## 5. putAll: hợp nhất ánh xạ theo khóa, không đồng bộ hai map

Chữ ký:

~~~java
void putAll(Map<? extends K, ? extends V> source);
~~~

`Map` không phải `Collection`, nên thao tác hàng loạt của nó làm việc với ánh xạ khóa–giá trị. Hiệu ứng được mô tả tương đương việc áp dụng `put(k, v)` cho từng ánh xạ nguồn. Nó không trả cờ `boolean` hoặc map kết quả. [Java 17: Map.putAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#putAll(java.util.Map)).

### 5.1. Khóa trùng bị cập nhật; khóa đích không có trong nguồn vẫn giữ

Giả sử:

~~~text
đích                  {timeout=5, mode=SAFE, owner=team}
nguồn                 {mode=FAST, retries=3}
~~~

Sau `destination.putAll(source)`:

~~~text
{timeout=5, mode=FAST, owner=team, retries=3}
~~~

`mode` được thay vì khóa đã có; `retries` được thêm; `timeout` và `owner` vẫn còn vì `putAll` không xóa những khóa vắng ở nguồn.

Một mẫu kết hợp cấu hình rõ ý định là:

~~~java
Map<String, String> effective = new LinkedHashMap<>(defaults);
effective.putAll(overrides);
~~~

Đây là đoạn minh họa trong phương thức. `defaults` không bị thay cấu trúc; `effective` nhận mặc định rồi lớp ghi đè. Thứ tự áp dụng quan trọng: đảo hai bước có thể khiến mặc định đè ngược giá trị người dùng cung cấp.

### 5.2. putAll không phải cộng dồn theo nghĩa nghiệp vụ

Nếu hai map cùng có `"apples"` với số lượng `2` và `3`, `putAll` không tự tính tổng `5`. Nó áp dụng quy tắc `put` của đích, tức thay giá trị cho khóa phù hợp.

Nếu cần cộng, giữ giá trị cũ hay phát hiện xung đột rồi báo lỗi, phải chọn quy tắc kết hợp riêng, ví dụ một vòng lặp dùng `merge` với hàm phù hợp. Không gọi mọi kiểu hợp nhất là cùng một thao tác “copy”.

### 5.3. Giá trị null là dữ liệu khi đích cho phép

Đưa ánh xạ `owner=null` vào `LinkedHashMap` bằng `putAll` làm khóa `owner` tồn tại với giá trị `null`. Nó không tự xóa khóa đó.

Một số định dạng cấu hình bên ngoài có thể quy ước `null` nghĩa là xóa, nhưng quy ước ấy cần một bước xử lý của ứng dụng. Nó không phải hành vi tự có của `Map.putAll`.

### 5.4. Không tạo liên kết sống giữa hai map

Sau khi `target.putAll(source)` hoàn tất, thay ánh xạ của `source` không tự cập nhật `target`. Hai cấu trúc map độc lập nếu chúng vốn là hai đối tượng riêng không có quan hệ view.

Tuy vậy, khóa và giá trị là các tham chiếu được sao chép. Nếu giá trị là một danh sách, cả hai map có thể cùng trỏ tới danh sách ấy. Thay ánh xạ khác với sửa trạng thái đối tượng giá trị.

### 5.5. Thứ tự sau putAll do đích quyết định

Với `LinkedHashMap` theo thứ tự chèn, khóa đã có giữ vị trí khi `put` cập nhật giá trị; khóa mới được thêm theo thứ tự các ánh xạ nguồn được xử lý. Một map access-order có quy tắc truy cập khác.

Constructor `LinkedHashMap(Map)` tạo map theo thứ tự chèn của bản mới, không tự sao chép chế độ access-order hoặc toàn bộ chính sách của nguồn. [Java 17: LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

Với map sắp xếp, thứ tự do khóa và bộ so sánh quyết định, không phải trình tự chèn. Không đoán kết quả in của một map không bảo đảm thứ tự chỉ từ cách viết `putAll`.

## 6. Comparator và overload constructor: cùng đối tượng nguồn, khác lựa chọn

Một constructor sao chép không phải luôn chỉ có một dạng. Kiểu khai báo của biểu thức đối số tham gia chọn overload lúc biên dịch.

### 6.1. TreeSet(Collection) và TreeSet(SortedSet)

Giả sử nguồn là một set số nguyên theo thứ tự giảm:

~~~java
SortedSet<Integer> descending =
        new TreeSet<>(Comparator.reverseOrder());
descending.addAll(List.of(3, 1, 2));

Collection<Integer> broad = descending;
TreeSet<Integer> a = new TreeSet<>(descending);
TreeSet<Integer> b = new TreeSet<>(broad);
~~~

Đây là đoạn minh họa với import tương ứng.

- `new TreeSet<>(descending)` chọn constructor nhận `SortedSet`, giữ quy tắc thứ tự của nguồn: `[3, 2, 1]`.
- `new TreeSet<>(broad)` chọn constructor nhận `Collection`, dùng thứ tự tự nhiên: `[1, 2, 3]`.

Hai biến nguồn cùng trỏ đến một đối tượng, nhưng overload được chọn khác vì kiểu tĩnh khác. Constructor nhận `Collection` không tự biến thành constructor nhận `SortedSet` chỉ vì đối tượng thực tế là set sắp xếp. [Java 17: TreeSet constructors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html).

Nếu muốn xây một set mới theo một comparator cụ thể từ nguồn tổng quát, tạo đích với comparator ấy rồi `addAll`:

~~~java
TreeSet<Integer> copy = new TreeSet<>(Comparator.reverseOrder());
copy.addAll(broad);
~~~

Dùng một `TreeSet` rỗng mặc định rồi `addAll` không tự học comparator của nguồn.

### 6.2. TreeMap(Map) và TreeMap(SortedMap)

`TreeMap` có sự phân biệt tương tự:

- constructor nhận `SortedMap` giữ thứ tự so sánh của nguồn;
- constructor nhận `Map` dùng thứ tự tự nhiên của khóa.

Nếu khóa không có thứ tự tự nhiên phù hợp, việc mất comparator không chỉ đổi trình tự mà còn có thể khiến constructor thất bại. [Java 17: TreeMap constructors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html).

Chương trình chính dùng số nguyên để cả hai cách đều chạy được và sự khác biệt thể hiện rõ ở thứ tự khóa.

### 6.3. Sao chép còn có thể đổi quan hệ “trùng”

Nếu đích so sánh các phần tử theo quy tắc khác nguồn, số phần tử hoặc ánh xạ có thể thay đổi theo quan hệ tương đương của đích.

Không dùng comparator chỉ dựa trên độ dài tên rồi mặc định vẫn giữ được mọi tên khác nhau. Với set/cây, các giá trị được so sánh bằng `0` có thể bị xem là cùng một vị trí logic. Muốn giữ hợp đồng set/map thông thường, quan hệ so sánh cần nhất quán với phép bằng cần thiết của kiểu dữ liệu.

Đây là lựa chọn mô hình dữ liệu, không phải một lỗi mà constructor sao chép có trách nhiệm tự sửa.

## 7. Quy tắc kiểu, null và khả năng sửa vẫn áp dụng

### 7.1. Kiểu con của phần tử nguồn có thể đi vào kiểu cha ở đích

`Collection<? extends E>` cho phép nguồn có phần tử là kiểu con của `E`:

~~~java
List<Integer> source = List.of(1, 2);
List<Number> target = new ArrayList<>();
target.addAll(source);
target.add(3.5);
~~~

Đây là đoạn chạy trong phương thức. Nó không có nghĩa `List<Integer>` trở thành kiểu con của `List<Number>`. Ta đọc các `Integer` từ nguồn và đưa chúng vào một đích có thể chứa `Number`.

`putAll` cũng cho nguồn có khóa và giá trị phù hợp dưới dạng kiểu con qua các wildcard tương ứng. Không cần dùng raw type hay ép kiểu mất an toàn chỉ để chuyển dữ liệu hợp lệ.

### 7.2. Đích quyết định có nhận null hay không

Một nguồn hợp lệ có thể chứa phần tử `null`, nhưng đích không nhất thiết nhận. `new ArrayList<>(sourceWithNull)` có thể thành công, trong khi `new ArrayDeque<>(sourceWithNull)` ném `NullPointerException` vì `ArrayDeque` cấm phần tử `null`. [Java 17: ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html).

Với map, chính sách khóa/giá trị `null` cũng phụ thuộc loại đích. Việc “dữ liệu đã nằm trong một map hợp lệ” không chứng minh nó hợp lệ với mọi map khác.

Phân biệt nguồn chứa một phần tử `null` với chính tham chiếu nguồn bằng `null`. Constructor hoặc thao tác hàng loạt không tự hiểu nguồn `null` là nguồn rỗng.

### 7.3. addAll và putAll cần quyền sửa đích

Đích từ `List.of` hoặc `Map.of` không cho sửa. Việc thao tác có tên “copy” trong suy nghĩ của bạn không mở quyền thay đổi đối tượng ấy.

Các lớp bọc không sửa được cũng giữ giới hạn tương ứng. Một nguồn không sửa được vẫn có thể được đọc để dựng một đích sửa được, nhưng muốn cập nhật tại chỗ thì chính đích phải hỗ trợ thao tác.

Không dùng một lần gọi với nguồn rỗng để thăm dò quyền sửa, rồi suy mọi lần sau sẽ hợp lệ. Các API có thể từ chối thao tác tùy chọn ngay cả khi bạn cho rằng không có gì thay đổi.

## 8. Ngoại lệ và thay đổi đồng thời: không tự có “tất cả hoặc không gì”

### 8.1. Thao tác hàng loạt không phải giao dịch tổng quát

`addAll` và `putAll` có thể gặp lỗi kiểu, `null`, comparator hoặc giới hạn của đích. Hợp đồng chung không hứa rollback mọi thay đổi đã làm trước ngoại lệ cho mọi cách triển khai.

Nếu muốn một chức năng thay thế dữ liệu chỉ công bố trạng thái mới sau khi chuẩn bị hợp lệ, có thể dựng một ứng viên riêng, kiểm tra rồi mới chuyển tham chiếu ở tầng sở hữu phù hợp. Đừng `clear` nguồn hiện tại trước khi biết dữ liệu mới hợp lệ rồi coi ngoại lệ là không có hậu quả.

Việc tạo ứng viên không tự giải quyết đồng thời: thao tác công bố trạng thái, vòng đời các view cũ và quy tắc khóa vẫn phải được thiết kế.

### 8.2. clear rồi addAll không tương đương trong mọi quan hệ tham chiếu

Giả sử hai biến cùng trỏ một list. Nếu gọi `target.clear()` rồi `target.addAll(source)` và source thực ra chính là target hoặc view dựa trên nó, bạn có thể đã xóa dữ liệu cần đọc trước khi bắt đầu thêm.

Khi mục đích là thay nội dung, hãy xác định nguồn/đích có chung cấu trúc không. Nếu cần giữ nguồn trước, sao chép dữ liệu thích hợp trước khi xóa. Nhưng nếu phải giữ nguyên danh tính đối tượng đích vì nơi khác đang giữ tham chiếu, thay biến sang đối tượng mới cũng có ý nghĩa khác việc sửa tại chỗ.

### 8.3. Sao chép không tự tạo ảnh chụp nguyên tử

Nếu nguồn bị sửa trong lúc constructor hoặc thao tác hàng loạt đang đọc, phải tuân chính sách đồng thời của nguồn và công cụ dùng. Không xem việc kết quả là một đối tượng mới như bằng chứng mọi phần tử được lấy ở cùng một thời điểm.

Một `ConcurrentHashMap` hỗ trợ truy cập đồng thời, nhưng các quan sát tổng hợp và cập nhật nhiều ánh xạ không tự trở thành một giao dịch toàn map. [Java 17: ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

Nếu nghiệp vụ cần một ảnh chụp nhất quán của nhiều trường/ánh xạ, cần một cơ chế phù hợp bao trùm việc đọc, hoặc một mô hình dữ liệu bất biến/phiên bản hóa rõ ràng. Không dựa vào việc có hay không có ngoại lệ fail-fast để chứng minh tính nhất quán.

## 9. Chương trình đầy đủ

Lưu toàn bộ mã dưới vào **`BulkCopyDemo.java`**. Ví dụ dùng một luồng và các nguồn có thứ tự xác định để stdout có thể kiểm tra chính xác.

Lớp `Note` cố tình có một trường thay đổi được để thấy tham chiếu chia sẻ. Bản sao phần tử tường minh trong ví dụ chỉ cần sao chép một chuỗi bất biến; không coi cách đó là thuật toán sao chép sâu cho mọi đồ thị đối tượng.

~~~java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class BulkCopyDemo {
    static final class Note {
        String text;

        Note(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    static String failure(Runnable action) {
        try {
            action.run();
            return "no exception";
        } catch (UnsupportedOperationException | NullPointerException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("B", "A", "B"));
        List<String> alias = source;
        List<String> copied = new ArrayList<>(source);
        System.out.println("alias same list: " + (alias == source));
        System.out.println("constructor same list: " + (copied == source));
        source.add("C");
        System.out.println("source after add: " + source);
        System.out.println("constructor list stays: " + copied);

        List<String> merged = new ArrayList<>(List.of("start"));
        boolean changed = merged.addAll(source);
        System.out.println("list addAll: " + changed + ", result=" + merged);
        merged.addAll(1, List.of("X", "Y"));
        System.out.println("indexed addAll: " + merged);
        System.out.println("empty addAll: " + merged.addAll(List.of()));
        ArrayList<String> reserved = new ArrayList<>(10);
        System.out.println("capacity constructor size: " + reserved.size());

        LinkedHashSet<String> unique = new LinkedHashSet<>(source);
        System.out.println("set constructor: " + unique);
        System.out.println("set addAll existing: " + unique.addAll(List.of("B", "A")));
        changed = unique.addAll(List.of("C", "D"));
        System.out.println("set addAll new: " + changed + ", result=" + unique);

        Note shared = new Note("draft");
        List<Note> notes = new ArrayList<>(List.of(shared));
        List<Note> constructorNotes = new ArrayList<>(notes);
        List<Note> bulkNotes = new ArrayList<>();
        bulkNotes.addAll(notes);
        shared.text = "reviewed";
        System.out.println("shared element in both copies: "
                + (notes.get(0) == constructorNotes.get(0)
                && notes.get(0) == bulkNotes.get(0)));
        System.out.println("after element edit: " + notes + " / "
                + constructorNotes + " / " + bulkNotes);
        notes.add(new Note("extra"));
        System.out.println("independent list sizes: "
                + notes.size() + " / " + constructorNotes.size());

        List<Note> independentNotes = new ArrayList<>();
        for (Note note : notes) {
            independentNotes.add(new Note(note.text));
        }
        notes.get(0).text = "final";
        System.out.println("shallow after second edit: " + constructorNotes);
        System.out.println("explicit element copies: " + independentNotes);

        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("timeout", "5");
        defaults.put("mode", "SAFE");
        defaults.put("owner", "team");
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("mode", "FAST");
        overrides.put("retries", "3");
        Map<String, String> combined = new LinkedHashMap<>(defaults);
        combined.putAll(overrides);
        System.out.println("map constructor plus putAll: " + combined);
        System.out.println("defaults unchanged: " + defaults);
        overrides.put("mode", "LATER");
        System.out.println("combined mode stays: " + combined.get("mode"));

        Map<String, String> nullOverride = new LinkedHashMap<>();
        nullOverride.put("owner", null);
        combined.putAll(nullOverride);
        System.out.println("null is a value, not deletion: " + combined);

        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("alpha", new ArrayList<>(List.of("one")));
        Map<String, List<String>> groupCopy = new LinkedHashMap<>(groups);
        groupCopy.get("alpha").add("two");
        groupCopy.put("beta", new ArrayList<>(List.of("x")));
        System.out.println("map shared value: " + groups);
        System.out.println("map structures independent: "
                + groups.keySet() + " / " + groupCopy.keySet());

        SortedSet<Integer> descending = new TreeSet<>(Comparator.reverseOrder());
        descending.addAll(List.of(3, 1, 2));
        Collection<Integer> broadCollection = descending;
        TreeSet<Integer> keepSetOrder = new TreeSet<>(descending);
        TreeSet<Integer> naturalSetOrder = new TreeSet<>(broadCollection);
        System.out.println("SortedSet constructor: " + keepSetOrder);
        System.out.println("Collection constructor: " + naturalSetOrder);

        SortedMap<Integer, String> descendingMap =
                new TreeMap<>(Comparator.reverseOrder());
        descendingMap.put(3, "c");
        descendingMap.put(1, "a");
        descendingMap.put(2, "b");
        Map<Integer, String> broadMap = descendingMap;
        TreeMap<Integer, String> keepMapOrder = new TreeMap<>(descendingMap);
        TreeMap<Integer, String> naturalMapOrder = new TreeMap<>(broadMap);
        System.out.println("SortedMap constructor keys: " + keepMapOrder.keySet());
        System.out.println("Map constructor keys: " + naturalMapOrder.keySet());

        List<String> nullable = Arrays.asList("ok", null);
        System.out.println("ArrayList accepts copied null: " + new ArrayList<>(nullable));
        System.out.println("ArrayDeque rejects copied null: "
                + failure(() -> new ArrayDeque<>(nullable)));

        List<String> readOnlyList = List.of("A");
        System.out.println("read-only list addAll: "
                + failure(() -> readOnlyList.addAll(List.of("B"))));
        List<String> writableList = new ArrayList<>(readOnlyList);
        writableList.add("B");
        System.out.println("new mutable list from read-only source: " + writableList);

        Map<String, Integer> readOnlyMap = Map.of("A", 1);
        System.out.println("read-only map putAll: "
                + failure(() -> readOnlyMap.putAll(Map.of("B", 2))));
        Map<String, Integer> writableMap = new LinkedHashMap<>(readOnlyMap);
        writableMap.put("B", 2);
        System.out.println("new mutable map from read-only source: " + writableMap);

        List<Number> numbers = new ArrayList<>();
        numbers.addAll(List.of(1, 2));
        numbers.add(3.5);
        System.out.println("subtype source: " + numbers);
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 BulkCopyDemo.java
java BulkCopyDemo
~~~

Dùng JDK 17 trở lên để biên dịch ở mức release 17 và runtime Java 17 trở lên để chạy. Ví dụ đã được biên dịch với mức release 17 và chạy thành công trên OpenJDK 24.0.2.

Stdout chính xác:

~~~text
alias same list: true
constructor same list: false
source after add: [B, A, B, C]
constructor list stays: [B, A, B]
list addAll: true, result=[start, B, A, B, C]
indexed addAll: [start, X, Y, B, A, B, C]
empty addAll: false
capacity constructor size: 0
set constructor: [B, A, C]
set addAll existing: false
set addAll new: true, result=[B, A, C, D]
shared element in both copies: true
after element edit: [reviewed] / [reviewed] / [reviewed]
independent list sizes: 2 / 1
shallow after second edit: [final]
explicit element copies: [reviewed, extra]
map constructor plus putAll: {timeout=5, mode=FAST, owner=team, retries=3}
defaults unchanged: {timeout=5, mode=SAFE, owner=team}
combined mode stays: FAST
null is a value, not deletion: {timeout=5, mode=FAST, owner=null, retries=3}
map shared value: {alpha=[one, two]}
map structures independent: [alpha] / [alpha, beta]
SortedSet constructor: [3, 2, 1]
Collection constructor: [1, 2, 3]
SortedMap constructor keys: [3, 2, 1]
Map constructor keys: [1, 2, 3]
ArrayList accepts copied null: [ok, null]
ArrayDeque rejects copied null: NullPointerException
read-only list addAll: UnsupportedOperationException
new mutable list from read-only source: [A, B]
read-only map putAll: UnsupportedOperationException
new mutable map from read-only source: {A=1, B=2}
subtype source: [1, 2, 3.5]
~~~

### 9.1. Danh sách và tập hợp

Các dòng đầu phân biệt alias với cấu trúc mới. Thêm `C` vào nguồn không làm bản sao constructor tăng kích thước. `addAll` vào danh sách có `start` giữ phần tử cũ và nối nguồn; overload có chỉ số chèn cả nhóm vào vị trí mong muốn.

Set đích loại trùng. `addAll` chỉ gồm thành viên đã có trả `false`; thêm `D` mới trả `true`.

### 9.2. Đối tượng Note và giá trị map có thể sửa

Hai danh sách sao chép cùng nhận tham chiếu `Note` nên thấy lần sửa `reviewed` và `final`. Nhưng việc thêm một note mới vào nguồn không làm cấu trúc bản sao tự tăng.

Vòng tạo note mới tạo các đối tượng độc lập cho mô hình đơn giản này. Bản ấy giữ `reviewed` sau khi nguồn đổi thành `final`.

Map có giá trị danh sách cũng cho thấy cùng nguyên tắc: sửa danh sách `alpha` nhìn thấy qua nguồn, còn thêm khóa `beta` vào bản sao không tự thêm khóa đó vào nguồn.

### 9.3. putAll và cấu hình ghi đè

Map kết hợp nhận mặc định trước, rồi cập nhật `mode` và thêm `retries`. `owner` không biến mất vì vắng ở map ghi đè.

Thay `overrides` sau khi thao tác hoàn tất không cập nhật map kết hợp. Đưa `owner=null` vào map kết hợp vẫn giữ khóa, chỉ thay giá trị.

### 9.4. Comparator và các điều kiện đích

Hai cặp constructor cho kết quả giảm hoặc tăng chỉ vì overload khác, dù nguồn thực tế là cùng đối tượng sắp xếp giảm.

Các dòng cuối phân biệt chính sách `null` và quyền sửa của đích. Bản `ArrayList`/`LinkedHashMap` mới tạo từ nguồn không sửa được lại có quyền sửa theo lớp đích đã chọn.

## 10. Những công cụ trông giống nhưng có ý nghĩa khác

### 10.1. View không phải constructor sao chép

`subList`, `keySet` hoặc các lớp bọc view có thể gắn với nguồn. `new ArrayList<>(view)` tạo cấu trúc danh sách mới chứa dữ liệu đọc từ view, không biến bản sao thành một phần tiếp tục ghi xuyên tự động.

Riêng phần tử `Map.Entry` cần thận trọng: sao chép collection chứa các entry không tự chứng minh từng entry đã độc lập với nguồn. Khi cần giữ các cặp ổn định, hãy sao chép từng cặp hoặc dựng map mới theo mục đích. [Java 17: Map.Entry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html).

### 10.2. Collections.copy không phải addAll

`Collections.copy(destination, source)` ghi đè các vị trí đã tồn tại của list đích; đích phải đủ **size**, không chỉ capacity. Nó không phải phép tạo collection mới hoặc nối phần tử. [Java 17: Collections.copy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#copy(java.util.List,java.util.List)).

Vì thế, `new ArrayList<>(10)` không đủ làm đích cho việc copy mười phần tử bằng công cụ này nếu list vẫn rỗng. Với yêu cầu tạo bản sao sửa được, constructor nhận collection thường là lựa chọn trực tiếp.

### 10.3. List.copyOf và Map.copyOf có mục tiêu khác

Các factory `copyOf` tạo kết quả không sửa được theo hợp đồng của chúng và có giới hạn như không nhận `null`. Chúng không phải một tên khác của constructor lớp đích sửa được.

Không suy bảo đảm thứ tự cho mọi factory map hoặc suy rằng “không sửa được” đồng nghĩa phần tử bất biến sâu. Hãy xác định mục tiêu trước: kết quả mới có cần sửa không, có cần giữ thứ tự/comparator không, có `null` không và có được chia sẻ phần tử không.

## 11. Chọn cách sao chép theo yêu cầu

| Yêu cầu | Cách thường phù hợp | Điều phải kiểm tra |
| --- | --- | --- |
| Tạo list sửa được từ dữ liệu nguồn | `new ArrayList<>(source)` | Thứ tự nguồn, phần tử chia sẻ |
| Nối dữ liệu vào list đang có | `target.addAll(source)` | Dữ liệu cũ vẫn giữ, quyền thêm |
| Chèn một nhóm tại vị trí | `target.addAll(index, source)` | Chỉ số, thứ tự nhóm, chi phí dịch |
| Loại trùng và giữ lần xuất hiện đầu theo nguồn | `new LinkedHashSet<>(source)` | Phép bằng/băm và ý nghĩa dữ liệu lặp |
| Tạo map cấu trúc riêng | `new LinkedHashMap<>(source)` | Thứ tự duyệt, giá trị/khóa chia sẻ |
| Áp lớp ghi đè lên map đang có | `target.putAll(overrides)` | Khóa trùng bị thay, khóa khác không bị xóa |
| Giữ comparator của nguồn sắp xếp | Constructor/đích được cấu hình đúng comparator | Overload do kiểu tĩnh chọn |
| Giữ trạng thái phần tử độc lập | Sao chép phần tử theo mô hình dữ liệu | Độ sâu, tham chiếu dùng chung, chu trình |
| Công bố một trạng thái thay thế sau khi chuẩn bị | Dựng ứng viên, kiểm tra, công bố theo chính sách sở hữu | Đồng thời, lỗi giữa chừng, view/alias cũ |

Hiệu năng cũng phụ thuộc loại đích. Tạo bản sao nông của n tham chiếu vẫn cần làm việc với dữ liệu nguồn; không phải miễn phí vì không sao chép sâu. Với list mảng, cấp phát trước hợp lý hoặc thêm cả nhóm có thể giảm một số chi phí tăng dung lượng/dịch chuyển, nhưng không nên hứa mọi constructor luôn nhanh hơn mọi dạng `addAll`. Đo trường hợp thực tế khi hiệu năng có ý nghĩa.

## 12. Lỗi thường gặp

1. **Gán biến rồi gọi là bản sao:** hai biến vẫn chung cấu trúc.
2. **Có collection mới nên phần tử cũng mới:** constructor và thao tác hàng loạt thông thường sao chép tham chiếu.
3. **addAll thay dữ liệu cũ:** nó thêm theo hợp đồng đích, không tự clear.
4. **putAll làm đích giống hệt nguồn:** các khóa chỉ có ở đích vẫn giữ.
5. **Khóa trùng được cộng hoặc gộp tự động:** `putAll` dùng ngữ nghĩa `put`, không tự định nghĩa phép cộng.
6. **Nguồn không sửa được nên bản sao cũng không sửa được:** lớp/factory đích mới quyết định hợp đồng đó.
7. **Capacity bằng size:** constructor số nguyên không tạo phần tử.
8. **Comparator luôn đi theo dữ liệu:** overload hoặc cấu hình đích quyết định.
9. **Null ở nguồn hợp lệ nên đích nào cũng nhận:** chính sách đích có thể khác.
10. **Bulk operation là giao dịch:** không tự có rollback hoặc ảnh chụp nguyên tử.
11. **Self-copy hay nguồn view chồng lấn luôn an toàn:** phải theo hợp đồng; tách dữ liệu khi cần.
12. **Bản sao giữ nguyên mọi chế độ lớp nguồn:** loại đích có thể đổi thứ tự, tính duy nhất, comparator và chính sách truy cập.

## 13. Thực hành có đáp án

### Bài 1 — Phân biệt alias và list mới

`a` là một `ArrayList` chứa `[A, B]`. Đặt `b = a` và `c = new ArrayList<>(a)`. Sau `b.add("C")`, ba danh sách ra sao?

**Đáp án:** `a` và `b` cùng là `[A, B, C]` vì chung đối tượng. `c` vẫn `[A, B]` về cấu trúc. Nếu phần tử là đối tượng có trạng thái thay đổi, bản `c` vẫn có thể chia sẻ những đối tượng ấy.

### Bài 2 — addAll có xóa phần trước không?

Đích là `[old]`, nguồn `[X, X]`. Sau `ArrayList.addAll(source)` và sau `LinkedHashSet.addAll(source)` trên hai đích riêng cùng ban đầu chứa `old`, khác nhau gì?

**Đáp án:** list thành `[old, X, X]`; set liên kết thành `[old, X]`. Cả hai giữ `old`. Phần tử lặp được xử lý theo hợp đồng loại đích, không theo một quy tắc sao chép chung bắt buộc giữ nguyên số lượng.

### Bài 3 — Khóa trùng và khóa chỉ có ở đích

Đích `{A=1, B=2}`, nguồn `{B=20, C=3}`. Sau `putAll` có mất `A` không? `B` bằng bao nhiêu?

**Đáp án:** `A` vẫn có giá trị `1`, `B` thành `20` và `C` được thêm với `3`. Đây là áp các ánh xạ nguồn, không phải thao tác đồng bộ để xóa mọi khóa ngoài nguồn. Thứ tự in tùy nguồn/đích cụ thể.

### Bài 4 — Sao chép một danh sách đối tượng

Tạo `copy = new ArrayList<>(notes)` rồi sửa `notes.get(0).text`. Tại sao `copy` thấy thay đổi dù `copy != notes`?

**Đáp án:** hai list khác cấu trúc nhưng cùng chứa tham chiếu đến đối tượng note. Cần tạo note mới nếu yêu cầu độc lập về trạng thái ấy. Độ sâu sao chép phải theo mô hình dữ liệu, không mặc định mọi trường con cũng độc lập.

### Bài 5 — Constructor của set sắp xếp

Một `SortedSet<Integer>` dùng comparator giảm được gán thêm vào biến `Collection<Integer> broad`. Vì sao `new TreeSet<>(sorted)` và `new TreeSet<>(broad)` có thể khác thứ tự?

**Đáp án:** kiểu tĩnh chọn overload. Dạng nhận `SortedSet` giữ thứ tự so sánh nguồn; dạng nhận `Collection` dùng thứ tự tự nhiên. Muốn comparator cụ thể từ nguồn tổng quát, tạo đích với comparator rồi thêm dữ liệu.

### Bài 6 — Capacity và dữ liệu

Một lập trình viên tạo `new ArrayList<String>(100)` rồi cho rằng list có 100 vị trí để gọi `set` hoặc `Collections.copy`. Sai ở đâu?

**Đáp án:** con số là dung lượng ban đầu, `size` vẫn 0. Phải có phần tử thực sự nếu thao tác yêu cầu vị trí đã tồn tại. Nếu cần bản sao dữ liệu nguồn, dùng constructor nhận collection hoặc `addAll` đúng mục đích.

### Bài 7 — Ghi đè bằng null

Map cho phép `null`, có `mode=SAFE`. Nguồn chứa `mode=null`. `putAll` có xóa khóa `mode` không?

**Đáp án:** không; ánh xạ tồn tại với giá trị `null`. Nếu nghiệp vụ quy định `null` là tín hiệu xóa, cần một thuật toán xử lý quy ước đó riêng. Không nhầm quy tắc dữ liệu bên ngoài với ngữ nghĩa chuẩn của map.

### Bài 8 — Thất bại và nguồn chồng lấn

Một hàm làm `target.clear(); target.addAll(source);` để thay dữ liệu. Cần kiểm tra những vấn đề nào trước khi coi đây là phép thay thế an toàn?

**Đáp án:** nguồn có cùng đối tượng hoặc là view dựa trên đích không; đích có hỗ trợ xóa/thêm không; dữ liệu có hợp kiểu, `null`, comparator hay giới hạn không; thao tác có thể thất bại giữa chừng không; có thay đổi đồng thời không; các alias/view khác cần nhìn cùng đối tượng hay một trạng thái mới. Không tự có rollback. Có thể cần dựng ứng viên trước và công bố theo chính sách sở hữu phù hợp.

Khi tự luyện, hãy viết ra bốn dòng cho mỗi phép sao chép: đối tượng chứa mới hay cũ, phần tử/khóa/giá trị có dùng chung tham chiếu không, quy tắc dữ liệu của đích là gì, và thao tác có thay đổi dữ liệu đã có ở đích ra sao. Bốn câu trả lời ấy xác định đúng ý nghĩa hơn việc chỉ nhìn tên có chữ “copy”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-copies-006">Sao chép nông, sao chép sâu và đối tượng phần tử dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Sao chép nông, sao chép sâu và đối tượng phần tử dùng chung

## 1. Cần độc lập ở lớp nào?

Một chương trình nhận List rồi tạo List khác để chỉnh sửa. Nó có thể muốn tránh sửa danh sách đầu vào, nhưng vẫn vô tình thay trạng thái của một phần tử mà cả hai danh sách đang trỏ đến.

Để hiểu kết quả, cần nhìn dữ liệu theo nhiều lớp: biến tham chiếu, đối tượng collection, rồi các đối tượng phần tử và dữ liệu lồng bên trong chúng.

Ba hành động khác nhau:

| Hành động | Có collection mới? | Các phần tử có được nhân bản? |
|---|---|---|
| Gán alias = source | Không | Không; hai biến trỏ cùng collection |
| new ArrayList<>(source) | Có cấu trúc chứa riêng | Không; các tham chiếu phần tử được sao chép |
| Tạo collection mới và sao chép các đối tượng theo chính sách đã chọn | Có | Những đối tượng thuộc phạm vi sao chép được tạo lại |

Sao chép nông, gọi là shallow copy, tạo một lớp đối tượng mới nhưng giữ lại các tham chiếu bên trong theo chính sách của thao tác. Sao chép sâu, gọi là deep copy, tạo lại những đối tượng có thể thay đổi trong phạm vi cần độc lập.

Không có một độ sâu đúng cho mọi bài toán. Có lúc chỉ cần sửa danh sách chứa mà không sửa phần tử; có lúc cần một bản dữ liệu chỉnh sửa độc lập; có lúc phải sao chép cả đồ thị có nhiều đường tham chiếu và vòng.

Các chương trình đầy đủ trong bài dùng Java 17 trở lên. Các lớp dữ liệu nhỏ cố ý cho sửa trực tiếp để có thể quan sát rõ từng quan hệ tham chiếu.

### 1.1. Một sơ đồ để đọc các thay đổi

Sau new ArrayList<>(source), có thể hình dung:

```text
source ──→ List thứ nhất ──┬──→ Person A
                          └──→ Person B

copy   ──→ List thứ hai  ──┬──→ Person A
                          └──→ Person B
```

Hai List khác nhau, nhưng cả hai mũi tên ở vị trí đầu đều đến cùng Person A. Thêm một vị trí vào List thứ hai không tự thêm vào List thứ nhất; sửa tên của Person A thì những nơi trỏ đến Person A cùng thấy.

Sau một bản sao sâu phù hợp, các đối tượng Person có trạng thái cần độc lập cũng được tạo lại. Những giá trị bất biến như String có thể tiếp tục được dùng chung mà không tạo đường sửa trạng thái qua chúng.

## 2. Tham chiếu, danh tính và loại thay đổi

### 2.1. Gán một tham chiếu không tạo lại đối tượng

Một biến kiểu tham chiếu giữ giá trị tham chiếu đến đối tượng hoặc null. Gán giá trị đó cho biến khác không tự chạy constructor để nhân bản đối tượng.

Do đó alias = source tạo thêm một đường đến cùng collection. Đây chưa phải thao tác tạo shallow copy của collection; thậm chí collection mới còn chưa tồn tại.

Cơ sở về tham chiếu và đối tượng được mô tả tại [JLS 17, mục 4.3.1](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.3.1).

### 2.2. == và equals trả lời hai câu hỏi khác nhau

Với tham chiếu đối tượng, == kiểm tra cùng đối tượng hay không. equals kiểm tra sự bằng nhau theo hợp đồng của kiểu đó.

Hai List khác nhau có thể equals vì chứa các phần tử bằng nhau theo thứ tự. Hai đối tượng phần tử khác nhau cũng có thể equals nếu lớp định nghĩa bằng nhau theo dữ liệu.

Vì vậy, equals của kết quả không chứng minh sao chép sâu hay nông. Cần kiểm tra các tham chiếu ở đúng lớp dữ liệu và thử những thay đổi mà bản sao được yêu cầu phải cô lập.

Không dùng hashCode hoặc chuỗi toString mặc định như bằng chứng chắc chắn về danh tính. Các ví dụ dùng so sánh ==/!= cho điều đó.

### 2.3. Thay một tham chiếu khác với sửa đối tượng được tham chiếu

Với một bản sao nông List<Person>:

- copy.set(i, new Person(...)) thay tham chiếu trong một vị trí của copy.
- copy.add(...) thêm một vị trí trong copy.
- copy.get(i).name = ... sửa đối tượng Person lấy từ vị trí đó.

Hai hành động đầu thay collection đích. Hành động thứ ba có thể sửa chính Person đang được nguồn dùng chung.

Sự khác biệt này giải thích vì sao “tôi chỉ thay dữ liệu ở bản sao” đôi khi vẫn làm dữ liệu nguồn đổi: cần xác định đang sửa lớp đối tượng nào.

### 2.4. final không làm mọi đối tượng phía sau bất biến

Một tham chiếu final không được gán lại sau khi đã khởi tạo theo quy tắc ngôn ngữ. Nó không tự ngăn việc sửa nội dung List hay trạng thái của phần tử phía sau.

Tương tự, một List không cho add/remove/set chưa chứng minh các phần tử bên trong không có phương thức hay trường cho sửa. Quyền sửa collection và tính bất biến của phần tử là hai điều kiện riêng.

## 3. Chương trình 1: collection mới, phần tử vẫn dùng chung

Lưu vào `ShallowCollectionDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class ShallowCollectionDemo {
    static final class Person {
        String name;

        Person(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static void main(String[] args) {
        List<Person> source = new ArrayList<>(
                List.of(new Person("Ada"), new Person("Bob")));
        List<Person> alias = source;
        List<Person> copy = new ArrayList<>(source);
        System.out.println("aliasSameList=" + (alias == source));
        System.out.println("copySameList=" + (copy == source));
        System.out.println("sameFirstElement=" + (copy.get(0) == source.get(0)));
        copy.get(0).name = "Ada2";
        System.out.println("sourceAfterElementEdit=" + source);
        copy.set(1, new Person("Cid"));
        System.out.println("sourceAfterCopySet=" + source);
        System.out.println("copyAfterSet=" + copy);
        copy.add(new Person("Dan"));
        System.out.println("sourceSize=" + source.size());
        System.out.println("copySize=" + copy.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ShallowCollectionDemo.java
java ShallowCollectionDemo
```

Stdout:

```text
aliasSameList=true
copySameList=false
sameFirstElement=true
sourceAfterElementEdit=[Ada2, Bob]
sourceAfterCopySet=[Ada2, Bob]
copyAfterSet=[Ada2, Cid]
sourceSize=2
copySize=3
```

### 3.1. Ba phép so sánh chỉ rõ ba lớp quan hệ

aliasSameList là true: alias và source trỏ cùng List. copySameList là false: constructor ArrayList tạo cấu trúc chứa khác.

sameFirstElement là true: phần tử đầu trong hai List vẫn là cùng Person. Không có thao tác sao chép từng Person trong new ArrayList<>(source).

### 3.2. Sửa Person khác với thay vị trí của List

Đổi tên qua copy.get(0) làm source in Ada2,Bob vì Person đầu được dùng chung.

copy.set(1,new Person(Cid)) chỉ thay vị trí thứ hai của copy. Source vẫn có Bob, còn copy có Cid. Thêm Dan làm copySize thành 3 nhưng sourceSize vẫn là 2.

Từ các kết quả đó có thể nói bản sao độc lập về cấu trúc chứa, nhưng chưa độc lập về trạng thái của mọi phần tử.

Hợp đồng constructor nhận Collection và khả năng chứa phần tử của ArrayList được mô tả tại [ArrayList — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

## 4. View không cho sửa và bản sao không cho sửa

### 4.1. Hai cách chặn quyền sửa có quan hệ nguồn khác nhau

Collections.unmodifiableList(source) là một view chặn sửa qua đường wrapper. Khi source thay đổi qua một tham chiếu khác, việc đọc view vẫn đọc dữ liệu nguồn.

List.copyOf(source) trả List không cho thêm, xóa hoặc thay phần tử. Những thay đổi tiếp theo của collection nguồn không được phản ánh như một view sống lên kết quả này. Tuy nhiên, các đối tượng phần tử có thể vẫn được dùng chung.

Không gọi cả hai trường hợp là “deep copy bất biến”. Chúng giải quyết quyền sửa collection theo hai hợp đồng khác nhau.

### 4.2. Chương trình 2: không add được nhưng vẫn sửa được Task

Lưu vào `UnmodifiableElementDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UnmodifiableElementDemo {
    static final class Task {
        String title;

        Task(String title) {
            this.title = title;
        }
    }

    public static void main(String[] args) {
        Task task = new Task("draft");
        List<Task> source = new ArrayList<>(List.of(task));
        List<Task> view = Collections.unmodifiableList(source);
        List<Task> snapshot = List.copyOf(source);
        source.add(new Task("extra"));
        System.out.println("sourceSize=" + source.size());
        System.out.println("viewSize=" + view.size());
        System.out.println("snapshotSize=" + snapshot.size());
        snapshot.get(0).title = "done";
        System.out.println("sourceTitle=" + source.get(0).title);
        System.out.println("viewTitle=" + view.get(0).title);
        System.out.println("sameTask=" + (snapshot.get(0) == source.get(0)));
        try {
            snapshot.add(new Task("blocked"));
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println("snapshotAdd=UnsupportedOperationException");
        }

        List<String> names = new ArrayList<>(List.of("A", "B"));
        List<String> savedNames = List.copyOf(names);
        names.set(0, "A2");
        System.out.println("savedNames=" + savedNames);
        System.out.println("currentNames=" + names);
        try {
            List.copyOf(Arrays.asList("A", null));
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            System.out.println("nullCopyOf=NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 UnmodifiableElementDemo.java
java UnmodifiableElementDemo
```

Stdout:

```text
sourceSize=2
viewSize=2
snapshotSize=1
sourceTitle=done
viewTitle=done
sameTask=true
snapshotAdd=UnsupportedOperationException
savedNames=[A, B]
currentNames=[A2, B]
nullCopyOf=NullPointerException
```

### 4.3. Cấu trúc nguồn và snapshot khác nhau

Sau source.add(extra), source và view đều có size bằng 2; snapshot vẫn có size bằng 1.

snapshot.add bị từ chối. Nhưng snapshot.get(0).title = done không gọi một phương thức sửa cấu trúc của List: nó sửa Task được trả về. Source và view cùng đọc title done, còn sameTask là true.

Không có ngoại lệ là điều dễ hiểu: quyền cấm add/remove/set của List không phải một lớp bảo vệ tự động cho từng đối tượng được get.

### 4.4. Phần tử bất biến có thể được dùng chung an toàn

savedNames giữ A,B khi names thay vị trí đầu sang A2. String là bất biến; thao tác names.set không sửa đối tượng String A, mà thay tham chiếu trong names.

Trong một mô hình chỉ cần List không cho sửa và các phần tử String bất biến, không cần tạo một String mới chỉ để bảo đảm không ai sửa nội dung String cũ.

List.copyOf không cho phép phần tử null, nên phép thử có null ném NullPointerException. Ngoài ra, API có thể tận dụng một List không cho sửa phù hợp; không dựa vào == để hứa mỗi lần copyOf luôn có một đối tượng List mới.

Nguồn đối chiếu: [List.copyOf — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html), [Collections.unmodifiableList — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html).


## 5. Sao chép sâu cần biết cấu trúc và phạm vi dữ liệu

### 5.1. Tạo đối tượng phần tử mới vẫn có thể chưa đủ

Giả sử Task có title kiểu String và tags kiểu List<String>. Tạo một Task khác rồi gán tags = other.tags vẫn làm hai Task dùng chung danh sách tags.

Có thể vẽ các lớp như sau:

```text
Task gốc   ──→ tags List ──→ các String
Task mới   ──→ tags List ──→ các String
               ↑ cùng List nếu chỉ gán trường tags
```

Muốn hai Task sửa danh sách tags độc lập, cần tạo một List tags mới. Vì phần tử trong tags là String bất biến, có thể tiếp tục dùng chung các String theo chính sách của ví dụ.

Nếu đổi thành List<MutableTag>, tạo riêng List vẫn chưa cô lập MutableTag. Phạm vi sao chép phải được xem tiếp theo kiểu dữ liệu thực tế.

### 5.2. Copy constructor chỉ sâu đến mức người viết cài đặt

Java không tự quy định một constructor nhận đối tượng cùng kiểu phải là deep copy. Tên “copy constructor” là cách thiết kế API; phần thân constructor mới quyết định những tham chiếu nào tiếp tục được chia sẻ.

Một phương thức tên copy, duplicate hay clone cũng cần một hợp đồng rõ: sao chép lớp nào, giữ phần tử nào, có cho null không và kết quả có sửa được không.

Trong ví dụ tiếp theo, constructor nhận Task cố ý sao chép nông các trường. independentCopy tạo riêng List tags để đáp ứng yêu cầu độc lập của mô hình nhỏ này. Hai đường được đặt cạnh nhau để quan sát, không phải khuyến nghị để một API nghiệp vụ có hành vi sao chép mơ hồ.

## 6. Chương trình 3: Task mới nhưng danh sách bên trong có thể còn chung

Lưu vào `NestedObjectCopyDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class NestedObjectCopyDemo {
    static final class Task {
        String title;
        List<String> tags;

        Task(String title, List<String> tags) {
            this.title = title;
            this.tags = tags;
        }

        Task(Task other) {
            this.title = other.title;
            this.tags = other.tags;
        }

        static Task independentCopy(Task source) {
            return new Task(source.title, new ArrayList<>(source.tags));
        }
    }

    public static void main(String[] args) {
        Task original = new Task("report", new ArrayList<>(List.of("urgent")));
        Task fieldCopy = new Task(original);
        Task independent = Task.independentCopy(original);
        System.out.println("differentTask=" + (fieldCopy != original));
        System.out.println("fieldCopySharesTags=" + (fieldCopy.tags == original.tags));
        System.out.println("independentSharesTags="
                + (independent.tags == original.tags));
        fieldCopy.tags.add("shared");
        System.out.println("originalTagsAfterFieldCopyEdit=" + original.tags);
        System.out.println("independentTagsBeforeEdit=" + independent.tags);
        independent.tags.add("local");
        independent.title = "copy-report";
        System.out.println("originalTagsAfterIndependentEdit=" + original.tags);
        System.out.println("independentTags=" + independent.tags);
        System.out.println("originalTitle=" + original.title);
        System.out.println("independentTitle=" + independent.title);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 NestedObjectCopyDemo.java
java NestedObjectCopyDemo
```

Stdout:

```text
differentTask=true
fieldCopySharesTags=true
independentSharesTags=false
originalTagsAfterFieldCopyEdit=[urgent, shared]
independentTagsBeforeEdit=[urgent]
originalTagsAfterIndependentEdit=[urgent, shared]
independentTags=[urgent, local]
originalTitle=report
independentTitle=copy-report
```

### 6.1. Danh tính Task và danh tính tags được kiểm tra riêng

differentTask là true, nên fieldCopy và original là hai Task khác nhau. Nhưng fieldCopySharesTags cũng true: hai trường tags trỏ cùng List.

independentSharesTags là false vì independentCopy tạo một ArrayList tags khác.

Chỉ kiểm tra Task mới khác Task cũ là chưa đủ. Phải đi xuống lớp trạng thái mà yêu cầu độc lập thực sự cần bảo vệ.

### 6.2. Sửa tags qua fieldCopy làm nguồn đổi

fieldCopy.tags.add(shared) sửa List tags đang dùng chung. originalTagsAfterFieldCopyEdit là urgent,shared.

independent đã được tạo trước thao tác đó, nên independentTagsBeforeEdit chỉ có urgent. Sau independent.tags.add(local), tags của bản độc lập là urgent,local; original vẫn urgent,shared.

### 6.3. Dùng chung String không đồng nghĩa dùng chung biến trường

title được gán cùng tham chiếu String lúc tạo bản sao. Nhưng independent.title = copy-report chỉ thay trường title của independent, không gán lại trường của original. originalTitle vẫn report.

String không bị sửa nội dung bởi phép gán đó. Đây là trường hợp có thể dùng chung đối tượng bất biến mà hai bản dữ liệu vẫn độc lập với những thay đổi được phép của mô hình.

independentCopy giả định source và source.tags khác null. Nếu API nghiệp vụ muốn nhận null hoặc có các kiểu phần tử khác, phải thêm chính sách đó một cách tường minh.

## 7. Khi dữ liệu là đồ thị chứ không phải một cây đơn giản

### 7.1. Một đối tượng có thể được tham chiếu nhiều lần

Một List có thể chứa cùng một đối tượng tại hai vị trí. Hai phần tử khác nhau cũng có thể cùng trỏ đến một Address, một danh sách con hoặc một nút chung.

Nếu mỗi lần gặp tham chiếu đều tạo một bản mới độc lập, quan hệ dùng chung trong kết quả sẽ bị phá. Ví dụ, hai người cùng tham chiếu một địa chỉ ban đầu có thể trở thành hai Address riêng sau sao chép, làm thay đổi ý nghĩa mô hình.

Điều đó không phải luôn bị cấm trong mọi nghiệp vụ, nhưng phải là chính sách có chủ đích. Khi yêu cầu là bảo toàn hình dạng tham chiếu của đồ thị, một đối tượng nguồn phải tương ứng một đối tượng đích trong cùng lượt sao chép.

### 7.2. Đồ thị có thể có vòng

root có thể trỏ đến child, còn child trỏ ngược root. Một thuật toán đệ quy chỉ “tạo mới rồi sao chép mọi con” mà không ghi nhớ đối tượng đã gặp sẽ tiếp tục đi vòng.

Cần một bảng ánh xạ đối tượng nguồn sang đối tượng đích đã tạo:

1. Nếu nguồn là null, xử lý theo chính sách null.
2. Nếu nguồn đã có trong bảng, dùng lại bản đích.
3. Nếu chưa có, tạo đối tượng đích và ghi vào bảng ngay.
4. Sau đó mới sao chép các đường tham chiếu con.

Ghi nhận trước bước đệ quy là điểm giúp vòng tham chiếu tìm lại đối tượng đích đang được xây dựng.

### 7.3. Bảng ghi nhớ phải phân biệt danh tính với equals

Hai nút khác nhau có thể equals vì cùng dữ liệu. Khi sao chép bảo toàn cấu trúc tham chiếu, không được vì equals mà tự gộp hai đối tượng nguồn khác nhau thành một nút đích.

IdentityHashMap so sánh khóa theo danh tính tham chiếu thay vì equals. Nó phù hợp cho bảng ghi nhớ của ví dụ này. Đây là một mục đích chuyên biệt, không phải lý do thay mọi HashMap nghiệp vụ bằng IdentityHashMap.

Nguồn đối chiếu: [IdentityHashMap — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html).

### 7.4. Chính sách của ví dụ đồ thị

Mô hình tiếp theo sao chép các Node và các List links mới, giữ thứ tự các cạnh, giữ các cạnh lặp và giữ vòng trong đồ thị kết quả. String name được dùng chung theo tính bất biến.

Các Node có equals đơn giản theo name để cố ý tạo hai nút bằng nhau về dữ liệu nhưng khác danh tính. Bảng sao chép vẫn phải phân biệt chúng. Đây là mô hình minh họa cho sự khác nhau giữa equals và danh tính, không phải một quy tắc chung chọn định danh nghiệp vụ cho mọi Node.

## 8. Chương trình 4: bảo toàn phần dùng chung và vòng trong đồ thị mới

Lưu vào `GraphCopyDemo.java`:

```java
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

public class GraphCopyDemo {
    static final class Node {
        String name;
        final List<Node> links = new ArrayList<>();

        Node(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Node
                    && Objects.equals(name, ((Node) other).name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
    }

    static Node copy(Node source) {
        return copy(source, new IdentityHashMap<>());
    }

    static Node copy(Node source, IdentityHashMap<Node, Node> seen) {
        if (source == null) {
            return null;
        }
        Node existing = seen.get(source);
        if (existing != null) {
            return existing;
        }
        Node result = new Node(source.name);
        seen.put(source, result);
        for (Node link : source.links) {
            result.links.add(copy(link, seen));
        }
        return result;
    }

    public static void main(String[] args) {
        Node root = new Node("root");
        Node shared = new Node("leaf");
        Node equalButDistinct = new Node("leaf");
        root.links.add(shared);
        root.links.add(shared);
        root.links.add(equalButDistinct);
        shared.links.add(root);

        Node copied = copy(root);
        System.out.println("sourceLeavesEqual=" + shared.equals(equalButDistinct));
        System.out.println("newRoot=" + (copied != root));
        System.out.println("newLeaf=" + (copied.links.get(0) != shared));
        System.out.println("sharedLinkPreserved="
                + (copied.links.get(0) == copied.links.get(1)));
        System.out.println("distinctEqualNodesPreserved="
                + (copied.links.get(0) != copied.links.get(2)));
        System.out.println("cyclePreserved="
                + (copied.links.get(0).links.get(0) == copied));
        copied.links.get(0).name = "copy-leaf";
        System.out.println("originalLeafName=" + shared.name);
        System.out.println("copiedLeafName=" + copied.links.get(0).name);
        System.out.println("copiedOtherLeafName=" + copied.links.get(2).name);
        System.out.println("nullCopy=" + copy(null));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 GraphCopyDemo.java
java GraphCopyDemo
```

Stdout:

```text
sourceLeavesEqual=true
newRoot=true
newLeaf=true
sharedLinkPreserved=true
distinctEqualNodesPreserved=true
cyclePreserved=true
originalLeafName=leaf
copiedLeafName=copy-leaf
copiedOtherLeafName=leaf
nullCopy=null
```

### 8.1. Có ba cạnh nhưng chỉ hai nút lá nguồn khác nhau

root.links chứa shared hai lần rồi equalButDistinct. Hai nút lá có cùng name nên sourceLeavesEqual là true, nhưng chúng là hai đối tượng khác nhau.

shared còn trỏ ngược root. Không in toàn đồ thị bằng một toString đệ quy; ví dụ kiểm tra các quan hệ cần thiết bằng ==/!= để tránh phụ thuộc cách hiển thị vòng.

### 8.2. Nút đích mới, phần dùng chung được giữ trong đồ thị đích

newRoot và newLeaf đều true. Đồ thị đích không dùng lại các Node mutable đó của nguồn.

sharedLinkPreserved là true: hai cạnh đầu trong bản sao vẫn cùng đến một Node đích. distinctEqualNodesPreserved cũng true: cạnh thứ ba đến một Node đích khác, dù hai nút nguồn equals.

Nếu dùng bảng ghi nhớ gộp theo equals, hai nút lá có nguy cơ bị gộp sai. IdentityHashMap giúp thể hiện đúng chính sách một danh tính nguồn tương ứng một danh tính đích.

### 8.3. Vòng được giữ mà không trỏ ngược về nguồn

cyclePreserved là true vì nút lá đích trỏ về copied, không phải root nguồn. Ghi seen.put trước khi đi vào các links giúp thuật toán đạt được điều này.

Sau khi đổi tên nút lá đích đầu thành copy-leaf, originalLeafName vẫn leaf. Nút lá đích thứ ba cũng vẫn leaf, chứng minh hai nút đích riêng không bị gộp theo tên.

nullCopy là null theo chính sách của phương thức copy trong ví dụ.

### 8.4. Phạm vi của thuật toán không phải Object bất kỳ

Thuật toán biết rõ trường name và links của Node. Nếu Node có thêm một payload mutable, phải đưa payload đó vào chính sách sao chép; mã hiện tại không tự phát hiện và sao chép mọi trường mới.

Mỗi lời gọi copy(root) tạo bảng ghi nhớ mới. Nếu sao chép nhiều gốc và cần giữ quan hệ dùng chung giữa các gốc ấy, phải thiết kế một ngữ cảnh sao chép chung cho cả lượt, thay vì mặc nhiên tạo một bảng riêng cho từng gốc.

Đệ quy có bảng ghi nhớ xử lý được vòng, nhưng một chuỗi nút rất sâu vẫn có thể vượt khả năng ngăn xếp. Khi dữ liệu yêu cầu điều đó, có thể thiết kế cách duyệt với danh sách công việc tường minh; đó là quyết định triển khai riêng của mô hình.


## 9. clone không tự có nghĩa sao chép sâu

### 9.1. Object.clone sao chép các trường theo giá trị đang giữ

Object.clone tạo bản sao các trường như khi gán. Trường primitive được sao chép giá trị primitive; trường tham chiếu được sao chép giá trị tham chiếu. Các đối tượng mà trường tham chiếu trỏ đến không tự được clone theo.

Vì vậy super.clone thường chỉ là bước sao chép nông. Nếu lớp cần độc lập những trường mutable, phần cài đặt còn phải xử lý các trường đó theo hợp đồng đã chọn.

Cloneable là một giao diện đánh dấu liên quan đến quyền dùng cơ chế Object.clone. Nó không khai báo phương thức clone và không tự sinh một phương thức public sao chép sâu cho lớp.

Nguồn đối chiếu: [Object.clone — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html), [Cloneable — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Cloneable.html).

### 9.2. Mảng cũng có nhiều lớp tham chiếu

Clone một int[] tạo mảng mới chứa các giá trị số, nên thay một ô của mảng đích không thay ô nguồn.

Clone một StringBuilder[] tạo mảng ngoài mới nhưng các phần tử StringBuilder vẫn có thể là cùng đối tượng. Append lên phần tử qua mảng đích vẫn sửa đối tượng mà nguồn dùng.

Ngay cả int[][] cũng là mảng chứa các tham chiếu đến int[] con. Clone mảng ngoài không tự tạo lại từng mảng con. Cần nhìn từng lớp, không chỉ thấy từ int rồi kết luận không có tham chiếu bên trong.

### 9.3. ArrayList.clone cũng không nhân bản phần tử

ArrayList công bố clone là shallow copy: List mới không có nghĩa mọi phần tử bên trong mới. Khi cần viết mã dễ đọc qua giao diện List, constructor nhận Collection thường diễn tả rõ việc sao chép cấu trúc chứa mà không cần suy đoán về clone.

Điều quan trọng vẫn là hợp đồng, không phải chỉ chọn giữa từ khóa new và tên phương thức clone. Một constructor tùy chỉnh cũng có thể gán lại tham chiếu cũ; một phương thức sao chép tùy chỉnh cũng có thể tạo nhiều lớp đối tượng mới.

Hợp đồng cụ thể của ArrayList.clone được nêu tại [ArrayList — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

## 10. Chương trình 5: clone của trường và mảng

Lưu vào `CloneFieldsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CloneFieldsDemo {
    static final class Box implements Cloneable {
        int quantity;
        List<String> tags;

        Box(int quantity, List<String> tags) {
            this.quantity = quantity;
            this.tags = tags;
        }

        @Override
        public Box clone() {
            try {
                return (Box) super.clone();
            } catch (CloneNotSupportedException impossible) {
                throw new AssertionError(impossible);
            }
        }

        Box independentCopy() {
            return new Box(quantity, new ArrayList<>(tags));
        }
    }

    public static void main(String[] args) {
        Box original = new Box(3, new ArrayList<>(List.of("base")));
        Box shallow = original.clone();
        Box independent = original.independentCopy();
        System.out.println("newBox=" + (shallow != original));
        System.out.println("cloneSharesTags=" + (shallow.tags == original.tags));
        shallow.quantity = 7;
        shallow.tags.add("via-clone");
        System.out.println("originalQuantity=" + original.quantity);
        System.out.println("cloneQuantity=" + shallow.quantity);
        System.out.println("originalTags=" + original.tags);
        System.out.println("independentTagsBeforeEdit=" + independent.tags);
        independent.tags.add("local");
        System.out.println("originalTagsAfterIndependentEdit=" + original.tags);

        int[] numbers = {1, 2};
        int[] numberCopy = numbers.clone();
        numberCopy[0] = 9;
        System.out.println("originalNumbers=" + Arrays.toString(numbers));
        StringBuilder[] builders = {new StringBuilder("x")};
        StringBuilder[] builderCopy = builders.clone();
        builderCopy[0].append("!");
        System.out.println("sharedArrayElement=" + builders[0]);
        int[][] grid = {{1, 2}};
        int[][] gridCopy = grid.clone();
        gridCopy[0][0] = 9;
        System.out.println("sharedInnerArray=" + grid[0][0]);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 CloneFieldsDemo.java
java CloneFieldsDemo
```

Stdout:

```text
newBox=true
cloneSharesTags=true
originalQuantity=3
cloneQuantity=7
originalTags=[base, via-clone]
independentTagsBeforeEdit=[base]
originalTagsAfterIndependentEdit=[base, via-clone]
originalNumbers=[1, 2]
sharedArrayElement=x!
sharedInnerArray=9
```

### 10.1. Box mới nhưng tags vẫn chung

Box cài clone bằng cách gọi super.clone. newBox là true, nhưng cloneSharesTags cũng true.

shallow.quantity = 7 thay trường int của Box đích; originalQuantity vẫn 3. shallow.tags.add(via-clone) lại sửa List tags chung, nên originalTags trở thành base,via-clone.

independentCopy tạo Box và ArrayList tags riêng trước thay đổi này. independentTagsBeforeEdit chỉ có base. Thêm local vào bản đó không thêm vào original.tags.

Trong mô hình này, tags chứa String bất biến nên tạo riêng List đủ cho phạm vi độc lập cần minh họa. Nếu tags chứa đối tượng mutable, cần xem tiếp những phần tử đó.

### 10.2. CloneNotSupportedException không được dùng để che lỗi khác

Box đã implements Cloneable nên ví dụ xem việc super.clone từ chối là một lỗi bất thường và chuyển thành AssertionError. Không dùng catch này như cách xử lý mọi lỗi dữ liệu nghiệp vụ.

Lớp tự công bố clone public và trả Box. Chỉ viết implements Cloneable mà không thiết kế API gọi thích hợp không tự giải quyết toàn bộ việc sao chép.

### 10.3. Ba kết quả mảng phản ánh ba loại lớp chứa

numberCopy[0] được đổi thành 9 nhưng originalNumbers vẫn là `[1, 2]`.

builderCopy[0] vẫn trỏ cùng StringBuilder đầu, nên sharedArrayElement là x! sau append.

gridCopy[0] vẫn là cùng mảng int[] con với grid[0], nên đổi ô trong mảng con làm sharedInnerArray thành 9. Nếu muốn độc lập toàn bộ các hàng của một ma trận, phải tạo lại các mảng hàng theo chính sách phù hợp; clone lớp ngoài chưa đủ.

## 11. Thiết kế một hợp đồng sao chép có thể kiểm chứng

### 11.1. Chọn phạm vi độc lập trước khi viết mã

Một API sao chép nên trả lời được:

- Đối tượng ngoài có được tạo mới không?
- Collection lồng bên trong có vùng chứa riêng không?
- Đối tượng phần tử mutable nào phải được tạo lại?
- Đối tượng bất biến nào được tiếp tục dùng chung?
- Quan hệ dùng chung và vòng trong nguồn có cần được giữ trong đồ thị đích không?
- Nguồn null, phần tử null và trường null được xử lý thế nào?
- Kết quả có cho sửa collection và phần tử không?

Không gọi một kết quả là deep copy chỉ vì đã có nhiều lệnh new. Phải đánh giá nó theo những đường trạng thái có thể bị thay đổi trong phạm vi nghiệp vụ.

### 11.2. Sao chép phòng vệ ở ranh giới sở hữu dữ liệu

Nếu một lớp không muốn người gọi sửa dữ liệu nội bộ qua List đầu vào, có thể giữ một bản sao phù hợp ngay khi nhận dữ liệu. Nếu không muốn người gọi sửa dữ liệu qua getter, kết quả trả về cũng cần một chính sách thích hợp.

Chỉ sao chép lớp List bảo vệ quan hệ chứa trước add/remove/set từ ngoài. Nếu phần tử mutable vẫn được chia sẻ, người gọi còn có thể sửa chúng. Muốn cô lập cả trạng thái đó cần sao chép phần tử, chuyển sang mô hình giá trị bất biến hoặc công bố rõ quan hệ chia sẻ.

Một List không cho sửa chứa các phần tử bất biến thường cho phép chia sẻ dữ liệu an toàn với những thao tác được phép của mô hình. Không cần tạo lại mọi đối tượng chỉ để tăng số lớp sao chép.

### 11.3. List, Set và Map có thêm hợp đồng của cấu trúc chứa

Khi đổi cấu trúc trong lúc sao chép, cần giữ các đặc tính mà người gọi dựa vào:

| Loại dữ liệu | Điều cần quyết định ngoài việc tạo đối tượng mới |
|---|---|
| List | Thứ tự, phần tử lặp, null, quyền sửa |
| List của List | List ngoài mới đã đủ chưa, các List con có cần mới không |
| Mảng tham chiếu | Có sao chép từng đối tượng phần tử không |
| Set | Quan hệ bằng nhau, tính duy nhất, thứ tự nếu có |
| Map | Khóa và giá trị nào mutable, thứ tự, Comparator nếu có |
| Đồ thị đối tượng | Cạnh dùng chung, vòng, đối tượng ngoài phạm vi sao chép |

Ví dụ, chỉ deep-copy các giá trị của Map trong khi vẫn dùng chung khóa mutable chưa chắc đáp ứng yêu cầu cô lập dữ liệu. Nếu tạo một TreeMap mới mà bỏ chính sách Comparator, kết quả có thể đổi thứ tự hoặc điều kiện chấp nhận khóa.

Không sửa các thuộc tính dùng trong equals/hashCode của một đối tượng khi nó còn đang làm khóa của map băm hay phần tử tập băm rồi kỳ vọng cấu trúc chứa tự tổ chức lại. Chính sách sao chép không thay thế các hợp đồng sử dụng collection đó.

### 11.4. Không có một hàm deepCopy(Object) đơn giản biết mọi ý nghĩa

Một đối tượng có thể giữ file đang mở, kết nối, luồng thực thi, bộ nhớ đệm hoặc một dịch vụ dùng chung. “Nhân bản tất cả” không cho biết tài nguyên bên ngoài phải được chia sẻ, mở mới hay loại khỏi bản sao.

Với dữ liệu nghiệp vụ đã biết kiểu, constructor/factory sao chép hoặc một bộ chuyển đổi có hợp đồng cụ thể thường giúp đọc và kiểm chứng phạm vi rõ hơn. Nếu chọn một cơ chế dựa trên serialization, JSON hay thư viện, vẫn phải đọc hợp đồng của cơ chế đó và kiểm tra kiểu, quan hệ tham chiếu, vòng cùng các phần dữ liệu được giữ lại.

Không xem một lần chuyển đổi thành công là bằng chứng mặc định rằng mọi đặc tính của đồ thị đã được sao chép đúng.

### 11.5. Sao chép không tự tạo một ảnh chụp nguyên tử khi có nhiều luồng

Trong các ví dụ, nguồn không bị nơi khác sửa trong lúc sao chép. Đó là điều kiện giúp kết quả cố định và dễ kiểm chứng.

Một vòng lặp tạo lại các phần tử từ nguồn đang thay đổi không mặc nhiên thấy toàn bộ trạng thái tại cùng một thời điểm. Cần hợp đồng truy cập thích hợp của nguồn hoặc cách phối hợp riêng nếu nghiệp vụ yêu cầu một ảnh chụp nhất quán.

Tương tự, bản sao đã tạo xong chưa tự trở thành đối tượng an toàn cho nhiều luồng nếu sau đó nhiều nơi cùng sửa nó. Độ sâu sao chép và cách phối hợp truy cập là hai quyết định khác nhau.

## 12. Chi phí và cách kiểm tra độ độc lập

### 12.1. Chi phí phụ thuộc số đối tượng phải tạo lại

Sao chép nông một List có n phần tử cần thu n tham chiếu vào cấu trúc chứa mới. Công việc còn phụ thuộc cách nguồn cho duyệt, nhưng nó không tự bao gồm chi phí sao chép toàn bộ trạng thái của từng phần tử.

Với đồ thị Node trong ví dụ và bảng ghi nhớ có chi phí tra cứu kỳ vọng hằng số, việc sao chép đi qua các nút và cạnh cần xử lý, tương ứng công việc kỳ vọng theo O(V+E). Nó cũng cần bộ nhớ cho bảng ghi nhớ và đồ thị đích; đệ quy còn dùng ngăn xếp theo độ sâu duyệt.

Nếu một phần tử có dữ liệu lớn, chi phí tạo lại phần tử đó phải được tính riêng. Không chọn sao chép sâu theo thói quen khi yêu cầu chỉ là thay cấu trúc List ngoài.

### 12.2. Kiểm tra theo từng đường sửa

Một phép kiểm tra tốt không chỉ dùng equals giữa nguồn và đích. Nên kiểm tra:

1. Danh tính đối tượng ngoài và những phần tử yêu cầu phải khác.
2. Danh tính của phần bất biến được phép dùng chung nếu điều đó có ý nghĩa.
3. Thêm/xóa/thay vị trí của collection đích có ảnh hưởng nguồn không.
4. Sửa trạng thái phần tử đích có ảnh hưởng nguồn không.
5. Các List hoặc đối tượng lồng bên trong còn chung ở đâu.
6. Cạnh dùng chung và vòng có được giữ theo chính sách không.
7. Hai đối tượng nguồn bằng nhau nhưng khác danh tính có bị gộp nhầm không.
8. Null và collection rỗng có được xử lý đúng hợp đồng không.

Nên thử cả chiều sửa nguồn rồi đọc đích nếu yêu cầu là độc lập hai chiều. Một kết quả đúng ngay sau sao chép chưa đủ chứng minh những thay đổi sau đó được cô lập đúng.

## 13. Những lỗi thường gặp

| Kết luận hoặc cách làm | Vì sao sai | Cách kiểm tra hoặc điều chỉnh |
|---|---|---|
| alias = source là đã sao chép List | Chỉ thêm tham chiếu đến cùng List | Kiểm tra alias == source |
| new ArrayList<>(source) tạo lại mọi phần tử | Nó sao chép cấu trúc chứa và tham chiếu | Kiểm tra danh tính phần tử và thử sửa trạng thái |
| Đối tượng ngoài khác thì mọi dữ liệu đã khác | Trường lồng có thể còn cùng tham chiếu | Kiểm tra từng lớp mutable cần độc lập |
| List.copyOf làm phần tử bất biến | Chỉ chặn thao tác sửa List và không theo cấu trúc nguồn về sau | Xem quyền sửa của từng phần tử |
| Wrapper unmodifiable là một bản sao dữ liệu | Wrapper còn dựa trên nguồn | Phân biệt view với kết quả sao chép |
| final List nghĩa List không đổi nội dung | final chặn gán lại tham chiếu, không chặn sửa đối tượng | Thiết kế quyền sửa và mô hình bất biến riêng |
| equals giữa nguồn và đích chứng minh deep copy | equals kiểm tra bằng nhau theo kiểu, không đo toàn bộ chia sẻ tham chiếu | Dùng ==/!= ở lớp cần xét và thử các thay đổi |
| clone luôn deep copy | Object.clone sao chép trường theo giá trị đang giữ | Đọc hợp đồng lớp, xử lý trường mutable khi cần |
| Clone mảng hai chiều là mọi hàng đều mới | Mảng ngoài chứa các tham chiếu đến mảng con | Kiểm tra danh tính các mảng hàng |
| Đệ quy sao chép mà không ghi nhớ nút | Có thể lặp vòng hoặc phá quan hệ dùng chung | Dùng ngữ cảnh ghi nhớ theo chính sách đồ thị |
| Dùng equals để gộp nút trong bản sao bảo toàn danh tính | Hai nguồn khác nhau nhưng equals có thể bị gộp | Ghi nhớ bằng danh tính khi đó là yêu cầu |
| Tạo List con mới nhưng quên phần tử mutable bên trong | Chỉ thêm một lớp độc lập | Xác định ranh giới sao chép theo loại dữ liệu |
| List.copyOf với phần tử null | API từ chối null | Chọn chính sách null và cách tạo kết quả phù hợp |
| Sao chép xong là an toàn đồng thời | Bản sao vẫn có thể được nhiều nơi cùng sửa | Thiết kế cách phối hợp truy cập riêng |

## 14. Tự kiểm tra và thực hành

### 14.1. Câu hỏi với đáp án

1. **Vì sao thêm phần tử vào shallow copy không đổi size nguồn nhưng sửa tên phần tử có thể đổi nguồn?** Cấu trúc List mới độc lập, còn đối tượng phần tử có thể vẫn dùng chung.
2. **copy.set thay một Person mới có giống copy.get(i).name = ... không?** Không. set thay tham chiếu trong List đích; sửa name tác động lên Person được tham chiếu.
3. **Một constructor Task(Task other) có mặc định deep copy không?** Không. Phần thân constructor quyết định cách sao chép từng trường.
4. **Có cần nhân bản String mới để tránh việc ai đó sửa nội dung String không?** Không. String bất biến; có thể chia sẻ theo yêu cầu của mô hình.
5. **Vì sao List.copyOf chứa Task vẫn thấy Task đổi?** Collection đích không cho sửa cấu trúc nhưng vẫn có thể giữ cùng tham chiếu Task mutable.
6. **Sao chép sâu có nhất thiết phá mọi chia sẻ trong bản sao không?** Không. Nếu yêu cầu bảo toàn đồ thị, các cạnh đến cùng một nút nguồn cần đến cùng một nút đích, trong khi nút mutable đích tách khỏi nguồn.
7. **Tại sao phải ghi nút vào bảng trước khi sao chép con?** Để một đường tham chiếu quay lại tìm được đối tượng đích đang xây, tránh đệ quy vô hạn.
8. **Vì sao IdentityHashMap phù hợp với bảng ghi nhớ trong ví dụ?** Nó phân biệt danh tính đối tượng, không gộp hai nguồn chỉ vì equals.
9. **Object.clone có tự clone mọi trường tham chiếu không?** Không. Các nội dung trường được sao chép như phép gán.
10. **int[][] clone có thể còn dùng chung mảng con không?** Có. Mảng ngoài mới vẫn chứa các tham chiếu mảng hàng cũ.
11. **Một List không cho sửa chứa phần tử bất biến có luôn cần deep copy mọi phần tử không?** Không cần tạo lại phần bất biến chỉ để ngăn sửa trạng thái qua nó; vẫn phải xét các yêu cầu khác của hợp đồng.
12. **Deep copy có đủ để có ảnh chụp nhất quán của nguồn đang bị sửa bởi luồng khác không?** Không. Còn cần hợp đồng hoặc cơ chế phối hợp việc đọc nguồn.

### 14.2. Bài tập: ba lớp dữ liệu

Tạo List<List<StringBuilder>> có hai hàng. Viết ba cách tạo kết quả: chỉ gán tham chiếu, tạo List ngoài mới, rồi tạo cả các List hàng và StringBuilder mới.

Với từng cách, kiểm tra ba thao tác: thêm một hàng vào kết quả; thêm một StringBuilder vào một hàng; append vào một StringBuilder. Dự đoán những thao tác nào còn ảnh hưởng nguồn.

Cách thứ ba cần thể hiện rõ chính sách nếu cùng StringBuilder xuất hiện nhiều lần: có giữ cùng một bản đích tương ứng hay chủ động tách thành các bản độc lập không?

### 14.3. Bài tập: kiểm tra hợp đồng sao chép một Order

Thiết kế Order có id kiểu String và List<Line> mutable, mỗi Line có số lượng và mô tả. Viết rõ hợp đồng bản sao trước khi cài đặt.

Nếu yêu cầu chỉnh số lượng trong bản sao mà không ảnh hưởng nguồn, cần tạo List mới và các Line mới. Nếu mô tả là String, có thể dùng chung String. Nếu một Line lại tham chiếu một đối tượng mutable khác, phải quyết định nó có nằm trong phạm vi cần độc lập không.

Kiểm tra thay List, thay Line và sửa trường trong Line như những hành động riêng.

### 14.4. Bài tập: hai nút bằng nhau và một vòng

Dựa vào GraphCopyDemo, tạo hai Node khác danh tính có cùng name và cho cả hai liên kết đến một Node chung. Thêm đường liên kết quay về gốc.

Xác minh kết quả có hai nút riêng cho hai nguồn riêng, một nút đích chung cho nguồn chung, và vòng trong kết quả trỏ về nút đích. Sau đó sửa một Node đích và chứng minh nguồn không đổi.

Không dùng equals làm phép kiểm tra duy nhất; các quan hệ danh tính chính là phần cần được kiểm chứng.

## 15. Nguồn đối chiếu và phạm vi kiểm chứng

- [JLS 17, Objects và Reference Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.3.1): cơ sở tham chiếu và đối tượng; cùng chương mô tả biến final.
- [ArrayList — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html): constructor nhận Collection và clone nông không sao chép phần tử.
- [List — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html): copyOf, quyền sửa kết quả, phần tử mutable và điều kiện null.
- [Collections — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html): wrapper unmodifiableList.
- [Object — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): cơ chế sao chép trường của clone.
- [Cloneable — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Cloneable.html): giao diện đánh dấu không tự cung cấp một phương thức clone public.
- [IdentityHashMap — Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html): so sánh danh tính và ứng dụng trong biến đổi đồ thị đối tượng.

Năm chương trình đầy đủ đã được biên dịch với `javac --release 17 -encoding UTF-8` và chạy trên Corretto JDK 17.0.15, cho đúng stdout được ghi. Các kết quả danh tính được in bằng ==/!=; ví dụ đồ thị kiểm tra trực tiếp cạnh dùng chung và vòng. Các phương thức sao chép tùy chỉnh trong bài chỉ áp dụng cho mô hình được khai báo, không được trình bày như bộ sao chép tổng quát cho mọi Object.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-copies-007">Map.Entry còn gắn nguồn so với entry độc lập</a>

<details>
<summary>Xem chi tiết</summary>

# Map.Entry còn gắn nguồn so với entry độc lập

## 1. Cùng kiểu Entry nhưng khác quan hệ sở hữu

`Map.Entry<K,V>` biểu diễn một cặp khóa–giá trị. Nhìn chữ ký ấy, bạn biết có thể hỏi `getKey()` và `getValue()`, còn `setValue(v)` là một thao tác tùy chọn.

Nhưng kiểu giao diện chưa nói cặp này **còn kết nối với một map** hay **chỉ là dữ liệu độc lập**. Đây là khác biệt quyết định khi muốn cập nhật map, giữ kết quả để dùng sau, trả dữ liệu qua API hoặc tạo ảnh chụp.

Hãy hình dung hai tình huống:

~~~text
Tình huống A:
entry đang được iterator của map trả về
        |
        +--- setValue được nguồn hỗ trợ ---> giá trị trong map đổi

Tình huống B:
entry được tạo bằng new SimpleEntry(...)
        |
        +--- setValue ---> chỉ cặp độc lập đổi
~~~

Tài liệu `Map.Entry` phân biệt rõ entry có thể sửa hoặc không sửa được, có thể độc lập hoặc đại diện cho entry trong view của map. Đối với entry iterator, kết nối với nguồn có phạm vi hợp lệ gắn với quá trình duyệt. [Java 17: Map.Entry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html).

Bài này giúp bạn trả lời ba câu hỏi riêng: entry lấy từ đâu, sửa nó có ghi vào map nào không, và dữ liệu bên trong có còn được chia sẻ không. Chương trình đầy đủ dùng **Java 17 trở lên**, bao gồm `Map.Entry.copyOf` có từ Java 17.

## 2. Ba trục không được gộp làm một

Một entry cần được đánh giá theo ba trục:

| Trục | Hai khả năng thường gặp | Câu hỏi thực tế |
| --- | --- | --- |
| Kết nối nguồn | Gắn với map hoặc độc lập | Sửa entry có thể tác động map không? |
| Thay giá trị của cặp | `setValue` được hỗ trợ hoặc bị từ chối | Có thể đổi tham chiếu value của entry không? |
| Trạng thái khóa/giá trị | Đối tượng bất biến hoặc sửa được | Dù cặp không đổi tham chiếu, dữ liệu bên trong có đổi không? |

Một entry độc lập có thể **sửa được**, như `SimpleEntry`. Một entry không sửa được có thể chứa **giá trị sửa được**, như một `ArrayList`. Một entry đang được duyệt qua map chỉ đọc không phải lối mở để cập nhật nguồn.

Vì thế, các từ “copy”, “immutable” hoặc “view” cần gắn với chính xác tầng dữ liệu đang nói đến. Đừng dùng một từ để suy hết mọi quyền và vòng đời.

## 3. Entry lấy từ iterator của entrySet

### 3.1. Cập nhật giá trị trong phạm vi duyệt được hỗ trợ

Ví dụ với một `LinkedHashMap` sửa được:

~~~java
for (Map.Entry<String, Integer> entry : stock.entrySet()) {
    if (entry.getKey().equals("sku-A")) {
        Integer oldValue = entry.setValue(11);
    }
}
~~~

Đây là đoạn minh họa đặt trong phương thức có import và nguồn `stock` phù hợp.

Lời gọi `setValue(11)` thay giá trị của ánh xạ hiện tại trong map, nếu nguồn hỗ trợ thao tác ấy. Nó trả giá trị cũ, không trả cờ `boolean` và không trả entry mới.

Khóa không bị đổi bởi `setValue`. Nếu nguồn trước có `sku-A=10`, sau cập nhật có `sku-A=11`. Số ánh xạ vẫn giữ nguyên.

`Map.entrySet` cho phép thao tác `setValue` trên entry do iterator trả về trong phạm vi mà nguồn hỗ trợ, bên cạnh đường xóa qua iterator. [Java 17: Map.entrySet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#entrySet()).

### 3.2. Gán lại biến entry không phải cập nhật nguồn

Đoạn sau chỉ thay một biến cục bộ:

~~~java
entry = new AbstractMap.SimpleEntry<>(entry.getKey(), 11);
~~~

Nó không gọi một phương thức cập nhật map. Biến `entry` trỏ đến cặp mới, nhưng ánh xạ gốc không tự nhận giá trị ấy.

Tương tự, gán `value = 11` cho biến value đọc được trước đó không biến biến ấy thành một ô tham chiếu của map.

Cập nhật nguồn phải đi qua một API có hợp đồng ghi xuyên, như `setValue` hợp lệ trên entry đang kết nối hoặc phương thức cập nhật của map.

### 3.3. Vòng đời: không giữ entry iterator làm tay nắm lâu dài

Bảo đảm kết nối của entry iterator không phải lời hứa “entry này sẽ theo khóa ấy mãi mãi”. Hợp đồng Java 17 giới hạn việc dùng entry vào quá trình duyệt; nếu map bị sửa sau khi entry được iterator trả về, ngoài việc sửa qua chính `setValue` theo hợp đồng, bạn không được tiếp tục dựa vào hành vi của entry cũ như một API ổn định.

Đặc biệt, không lấy một entry, kết thúc vòng lặp, xóa khóa khỏi map, rồi gọi `setValue` trên entry đó và đoán rằng nó sẽ thêm khóa trở lại hoặc chắc chắn ném một ngoại lệ cụ thể.

Một lần thử trên một cách triển khai có thể còn thấy dữ liệu cũ hay một nút nội bộ. Điều đó không nâng thành bảo đảm của giao diện. Nếu cần dùng về sau, hãy tạo cặp độc lập khi entry còn đang được đọc hợp lệ.

### 3.4. setValue không thay cho xóa

Nếu nguồn cho phép `null`, `entry.setValue(null)` gán giá trị `null`; khóa vẫn tồn tại. Nếu nguồn cấm `null`, thao tác có thể bị từ chối theo ràng buộc của nó.

Muốn xóa ánh xạ hiện tại trong lần duyệt, dùng `Iterator.remove` theo quy tắc iterator hoặc chọn một phép lọc thích hợp. Muốn thay khóa, phải thiết kế một thao tác khác; entry không có `setKey`.

## 4. Các cách tạo entry độc lập

### 4.1. SimpleEntry: cặp độc lập có thể thay value

~~~java
Map.Entry<String, Integer> local =
        new AbstractMap.SimpleEntry<>("sku-A", 50);
Integer oldValue = local.setValue(60);
~~~

Cặp `local` trở thành `sku-A=60`; `oldValue` là `50`. Không map nào tự được cập nhật chỉ vì trong chương trình có một map cũng chứa khóa `sku-A`.

`SimpleEntry` giữ khóa và giá trị riêng, hỗ trợ `setValue` và không thuộc entry-set view của map nào. Nó có constructor nhận hai thành phần hoặc nhận một entry để sao chép cặp. [Java 17: AbstractMap.SimpleEntry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.SimpleEntry.html).

Nếu muốn đưa giá trị cặp này vào map, phải thao tác rõ:

~~~java
stock.put(local.getKey(), local.getValue());
~~~

Sau đó sửa `local` lần nữa không tự sửa map. Lệnh `put` nhận khóa/giá trị, không thiết lập một quan hệ liên kết sống với đối tượng entry độc lập này.

### 4.2. SimpleImmutableEntry: độc lập, không hỗ trợ thay value của cặp

~~~java
Map.Entry<String, Integer> saved =
        new AbstractMap.SimpleImmutableEntry<>("sku-A", 50);
~~~

Cặp độc lập này không cho đổi value qua `setValue`; thao tác ấy ném `UnsupportedOperationException` trên lớp cụ thể này. Các tham chiếu khóa/giá trị được giữ, nhưng đối tượng mà chúng trỏ đến có thể vẫn sửa được.

Tài liệu lớp nói rõ tính không sửa được của cặp không đồng nghĩa khóa và giá trị bất biến sâu. [Java 17: AbstractMap.SimpleImmutableEntry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.SimpleImmutableEntry.html).

Nếu dùng constructor nhận entry, bạn sao chép các thành phần của entry tại thời điểm đọc hợp lệ. Bạn không tiếp tục giữ kết nối entry-set của đối tượng nguồn.

### 4.3. Map.entry(k, v): factory gọn cho cặp không sửa được

~~~java
Map.Entry<String, Integer> pair = Map.entry("sku-A", 50);
~~~

Cặp được tạo độc lập, không cho `setValue` và không nhận khóa hoặc giá trị `null`. Chỉ tạo entry không tự chèn nó vào một map nào.

Các entry từ factory này là đối tượng theo giá trị; không dựa vào đồng nhất tham chiếu `==` hay dùng chúng làm khóa đồng bộ. Chúng không được bảo đảm là những đối tượng cấp phát mới khác nhau mỗi lần gọi. [Java 17: Map.entry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#entry(K,V)).

Nếu cần lưu trữ lâu dài hoặc truyền qua tầng ứng dụng, một kiểu dữ liệu miền rõ tên có thể diễn đạt ý nghĩa tốt hơn một cặp chung. Nhưng `Map.entry` là lựa chọn hữu ích khi bạn thực sự cần hợp đồng `Map.Entry` và miền dữ liệu không có `null`.

### 4.4. Map.Entry.copyOf(existing): tách một cặp khỏi nguồn từ Java 17

~~~java
Map.Entry<String, Integer> snapshot = Map.Entry.copyOf(entry);
~~~

Kết quả không gắn với map nào, không hỗ trợ `setValue` và có cùng đặc tính thành phần như `Map.entry`. Đối số entry, khóa và value đều không được `null`.

Đừng kiểm chứng việc “đã tách” bằng cách bắt buộc `snapshot != entry`. Nếu đầu vào vốn đã là entry độc lập thích hợp, factory có thể tái sử dụng đối tượng. Điều cần kiểm chứng là hợp đồng dữ liệu và không có đường cập nhật map nguồn qua cặp ấy, không phải một điều kiện định danh không được hứa.

### 4.5. Bảng chọn nhanh

| Cách nhận/tạo | Gắn nguồn? | Thay value qua setValue? | Thành phần null |
| --- | --- | --- | --- |
| Entry iterator của nguồn sửa được, trong lúc duyệt | Có kết nối theo hợp đồng | Tùy nguồn; ví dụ LinkedHashMap hỗ trợ | Theo nguồn |
| `new SimpleEntry(k, v)` | Không | Có trên lớp này | Có thể giữ `null` |
| `new SimpleImmutableEntry(k, v)` | Không | Không trên lớp này | Có thể giữ `null` |
| `Map.entry(k, v)` | Không | Không | Từ chối |
| `Map.Entry.copyOf(e)` | Không | Không | Từ chối |
| Entry từ view của map không sửa được | Không phải quyền cập nhật nguồn | Bị chặn qua đường này | Theo nguồn/lớp bọc |

“Có thể giữ `null`” ở hai lớp entry độc lập nói về chính cặp. Khi đưa các thành phần của nó vào một map cấm `null`, map ấy vẫn có quyền từ chối.

## 5. Tạo ảnh chụp entry đúng tầng

### 5.1. Sao chép collection không tự sao chép từng entry

Đoạn sau tạo một list mới:

~~~java
List<Map.Entry<String, Integer>> saved =
        new ArrayList<>(stock.entrySet());
~~~

Nhưng phần tử của list vẫn là những đối tượng entry lấy từ nguồn. Không thể chỉ nhìn `new ArrayList` rồi kết luận các cặp đã được tách độc lập.

Ngay cả `List.copyOf(stock.entrySet())` chỉ giải quyết hợp đồng của list bên ngoài, không tự biến mỗi phần tử thành một `Map.Entry.copyOf`.

### 5.2. Sao chép từng cặp trong lúc duyệt hợp lệ

Với miền không có khóa/giá trị `null`:

~~~java
List<Map.Entry<String, Integer>> snapshots = new ArrayList<>();
for (Map.Entry<String, Integer> entry : stock.entrySet()) {
    snapshots.add(Map.Entry.copyOf(entry));
}
~~~

Mỗi entry được đọc và sao chép trong vòng lặp. Sau đó có thể giữ các cặp độc lập dù map tiếp tục thêm/xóa/thay ánh xạ.

Nếu cần các cặp độc lập nhưng có thể sửa cục bộ, tạo `new SimpleEntry<>(entry)`. Nếu có `null` và muốn cặp không cho thay value, có thể tạo `new SimpleImmutableEntry<>(entry)` theo chính sách dữ liệu.

Danh sách bên ngoài ở đây là `ArrayList` nên vẫn thêm/xóa phần tử danh sách được. Việc cặp không cho `setValue` không tự làm list không sửa được. Nếu muốn cả hai tầng bị giới hạn, cần lựa chọn thêm cho tầng list.

### 5.3. Dạng stream cũng cần bước biến đổi entry

Trong Java 17, có thể viết:

~~~java
List<Map.Entry<String, Integer>> snapshots =
        stock.entrySet().stream()
                .map(Map.Entry::copyOf)
                .toList();
~~~

Bước quan trọng là `map(Map.Entry::copyOf)`. Chỉ `entrySet().stream().toList()` không tự tách từng cặp khỏi nguồn.

`Stream.toList` ở cách dùng này cho list không sửa được, còn quyền của entry do bước tạo entry quyết định. Tài liệu `Map.Entry` giới thiệu chính cách sao chép từng cặp để có dữ liệu không gắn nguồn; các quy tắc `null` vẫn cần được thỏa mãn.

## 6. Entry không sửa được vẫn có thể chia sẻ trạng thái sửa được

Ví dụ:

~~~java
List<String> tags = new ArrayList<>(List.of("a"));
Map.Entry<String, List<String>> pair = Map.entry("tags", tags);
Map.Entry<String, List<String>> snapshot = Map.Entry.copyOf(pair);
tags.add("b");
~~~

`snapshot.getValue()` nhìn thấy `[a, b]` vì cặp giữ tham chiếu đến cùng danh sách `tags`. Bạn không đổi tham chiếu value của entry; bạn sửa chính đối tượng value.

Mô hình:

~~~text
pair.value --------+
                   +----> List tags đang sửa được
snapshot.value ----+
~~~

Đây là **sao chép nông**. Tính độc lập với map nguồn nói về quan hệ của cặp với ánh xạ, không tự nói mọi đối tượng phía sau tham chiếu đã được nhân bản.

### 6.1. Khi dữ liệu con là danh sách chuỗi

Có thể tạo một giá trị danh sách riêng không sửa được:

~~~java
Map.Entry<String, List<String>> safe =
        Map.entry("tags", List.copyOf(tags));
~~~

Sau đó thêm phần tử vào danh sách `tags` gốc không làm list của `safe` tăng phần tử. Với các phần tử chuỗi bất biến, cách này tạo mức độc lập cần thiết cho ví dụ.

`List.copyOf` không nhận `null` và không tự sao chép sâu từng đối tượng phần tử. Nếu phần tử danh sách là đối tượng có thể sửa, cần tiếp tục xác định độ sâu phù hợp. [Java 17: List.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)).

Đừng gọi mọi cặp chứa một collection không sửa được là “bất biến sâu” khi các phần tử bên trong còn thay đổi.

### 6.2. Khóa cũng có thể là đối tượng thay đổi

Không có `setKey` không có nghĩa đối tượng khóa bất biến. Một entry có thể giữ tham chiếu đến một khóa tự định nghĩa có các trường sửa được.

Nếu chính đối tượng ấy đang dùng làm khóa trong map hoặc thành viên trong set băm, thay các trường ảnh hưởng phép bằng/băm có thể phá giả định tra cứu. Chọn các kiểu khóa ổn định theo mục đích sử dụng; entry độc lập không sửa được không tự làm khóa ổn định.

## 7. Entry từ API điều hướng thường là ảnh chụp

`NavigableMap` có `firstEntry`, `lastEntry`, `floorEntry`, `ceilingEntry` và các phương thức lấy bỏ như `pollFirstEntry`. Chúng trả cặp phục vụ việc tìm một ánh xạ ở thời điểm gọi, khác việc bạn đang duyệt entry-set.

Tài liệu `NavigableMap` mô tả các entry điều hướng theo hướng ảnh chụp và nói chúng nhìn chung không hỗ trợ `setValue`. Chương trình dùng `TreeMap.firstEntry` để kiểm chứng cụ thể. [Java 17: NavigableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/NavigableMap.html).

Ví dụ nguồn `{1=one, 2=two}`:

~~~text
before = firstEntry()             before là 1=one
nguồn put(1, ONE)                 nguồn là {1=ONE, 2=two}
before                           vẫn mô tả 1=one
before.setValue(X)                bị từ chối ở entry ảnh chụp này
~~~

Muốn cập nhật map, dùng API map phù hợp. Muốn tìm ánh xạ đầu rồi cập nhật trong môi trường nhiều luồng như một thao tác thống nhất, cần một hợp đồng đồng thời riêng; việc nhận ảnh chụp không tự giữ chỗ cho khóa hay ánh xạ.

### 7.1. Poll trả entry của ánh xạ đã bị lấy bỏ

`pollFirstEntry` vừa xóa ánh xạ đầu vừa trả thông tin về ánh xạ ấy, hoặc trả `null` nếu không có. Entry kết quả không phải một cách ngầm để đưa ánh xạ trở lại nguồn bằng `setValue`.

Nếu muốn khôi phục hoặc đưa dữ liệu vào map, phải gọi thao tác map rõ ràng, có xét xem khóa hiện đã được một thao tác khác thêm lại hay chưa.

### 7.2. Liên hệ với SequencedMap từ Java 21

Từ Java 21, `SequencedMap` quy định `firstEntry`, `lastEntry`, `pollFirstEntry` và `pollLastEntry` trả ảnh chụp không hỗ trợ sửa nguồn bằng `setValue`.

Giao diện này cũng nhấn mạnh rằng entry nhận bằng một cách khác như `sequencedEntrySet().getFirst()` không có cùng bảo đảm chung về kết nối hay khả năng sửa. Không suy chỉ từ kiểu `Map.Entry` và chữ “first” rằng hai cách lấy có hợp đồng giống nhau. [Java 21: SequencedMap, vòng đời entry](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/SequencedMap.html).

Đoạn liên hệ này nói về API Java 21. Chương trình chính không dùng `SequencedMap` để vẫn biên dịch với mục tiêu Java 17.

## 8. Lớp bọc chỉ đọc và việc trả entry qua API

`Collections.unmodifiableMap(source)` chặn sửa map qua các phương thức map và collection view của lớp bọc, bao gồm đường `setValue` qua entry do nó cung cấp.

Đó vẫn là view nguồn, không phải một bản sao trạng thái cố định. Thay map gốc qua một tham chiếu khác có thể được nhìn thấy qua lớp bọc. [Java 17: Collections.unmodifiableMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableMap(java.util.Map)).

Cần tách hai mục tiêu:

- **không trao quyền cập nhật map:** dùng lớp bọc đúng hoặc trả cặp độc lập;
- **giữ dữ liệu tại một thời điểm:** sao chép theo mức cần thiết và điều kiện đọc nhất quán.

Chỉ bọc collection bên ngoài, chẳng hạn `Collections.unmodifiableSet(source.entrySet())`, không tự làm các đối tượng entry bên trong mất quyền `setValue`. Bảo vệ một tầng không tự bảo vệ tầng phần tử.

Nếu một hàm tìm “mục tốt nhất” rồi trả entry để xử lý về sau, cặp độc lập thường biểu đạt dữ liệu kết quả rõ hơn entry iterator bị kéo dài vòng đời. Nếu hàm thật sự cần trao một lệnh cập nhật có kiểm soát, một API cập nhật theo khóa và điều kiện nghiệp vụ thường rõ hơn trả tay nắm entry.

## 9. equals và hashCode không cho biết entry còn gắn nguồn hay không

Entry chuẩn so sánh cả khóa lẫn giá trị. Một `SimpleEntry("item", 1)` và `Map.entry("item", 1)` có thể `equals` nhau dù quyền sửa và nguồn gốc khác nhau.

Sau `simple.setValue(2)`, cặp ấy không còn bằng cặp `item=1`. Vì phép bằng và mã băm phụ thuộc các thành phần, thay value có thể thay `hashCode` của entry.

Do đó:

- `equals` nhau không chứng minh cùng quyền cập nhật;
- `equals` nhau không chứng minh có chung nguồn;
- `==` không phải kiểm chứng đúng cho các factory theo giá trị;
- không nên đặt một entry có nội dung thay đổi làm khóa băm hoặc thành viên set băm rồi sửa thành phần ảnh hưởng phép bằng/băm khi nó đang nằm trong cấu trúc ấy.

`Set` cảnh báo về phần tử có trạng thái thay đổi theo cách ảnh hưởng phép bằng trong khi vẫn thuộc set. [Java 17: Set, phần tử có thể thay đổi](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html).

Nếu cần định danh lâu dài cho một ánh xạ, dùng khóa có hợp đồng phù hợp; nếu cần ghi lại một cặp, dùng cặp dữ liệu theo giá trị và ổn định ở mức mà việc ghi nhận yêu cầu.

## 10. Chương trình đầy đủ

Lưu toàn bộ mã dưới vào **`EntryOwnershipDemo.java`**. Chương trình không giữ hoặc sử dụng entry iterator sau khi kết thúc quá trình duyệt. Các cặp cần giữ đều được tạo độc lập ngay trong vòng lặp.

Nguồn chính là `LinkedHashMap` mặc định theo thứ tự chèn để stdout có thể dự đoán. Nó không làm mọi map khác có cùng thứ tự. [Java 17: LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

~~~java
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class EntryOwnershipDemo {
    static String failure(Runnable action) {
        try {
            action.run();
            return "no exception";
        } catch (UnsupportedOperationException | NullPointerException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("sku-A", 10);
        stock.put("sku-B", 20);
        List<Map.Entry<String, Integer>> snapshots = new ArrayList<>();
        List<AbstractMap.SimpleEntry<String, Integer>> localCopies = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : stock.entrySet()) {
            if (entry.getKey().equals("sku-A")) {
                int previous = entry.setValue(11);
                System.out.println("connected setValue previous: " + previous);
            }
            snapshots.add(Map.Entry.copyOf(entry));
            localCopies.add(new AbstractMap.SimpleEntry<>(entry));
        }
        System.out.println("source after iteration: " + stock);
        System.out.println("captured snapshots: " + snapshots);

        stock.put("sku-A", 15);
        stock.remove("sku-B");
        stock.put("sku-C", 30);
        System.out.println("source changed later: " + stock);
        System.out.println("snapshots stay: " + snapshots);
        System.out.println("mutable copies stay: " + localCopies);

        AbstractMap.SimpleEntry<String, Integer> local = localCopies.get(0);
        int previous = local.setValue(99);
        System.out.println("local setValue previous: " + previous + ", local=" + local);
        System.out.println("source unaffected by local edit: " + stock);
        stock.put(local.getKey(), local.getValue());
        local.setValue(100);
        System.out.println("explicit put then local edit: local=" + local + ", source=" + stock);

        Map.Entry<String, Integer> frozen = Map.Entry.copyOf(local);
        local.setValue(101);
        System.out.println("copyOf detached pair: " + frozen + ", local now=" + local);
        System.out.println("copyOf setValue: " + failure(() -> frozen.setValue(500)));

        Map.Entry<String, Integer> literal = Map.entry("fixed", 7);
        Map.Entry<String, Integer> immutable =
                new AbstractMap.SimpleImmutableEntry<>("fixed", 7);
        System.out.println("Map.entry setValue: " + failure(() -> literal.setValue(8)));
        System.out.println("SimpleImmutableEntry setValue: "
                + failure(() -> immutable.setValue(8)));
        System.out.println("independent pair auto-inserted: " + stock.containsKey("fixed"));

        AbstractMap.SimpleEntry<String, Integer> nullLocal =
                new AbstractMap.SimpleEntry<>(null, null);
        Map.Entry<String, Integer> nullFrozen =
                new AbstractMap.SimpleImmutableEntry<>(null, null);
        System.out.println("independent null pair: " + nullFrozen);
        System.out.println("null local old value: " + nullLocal.setValue(10)
                + ", now=" + nullLocal);
        System.out.println("Map.entry null value: "
                + failure(() -> Map.entry("N", null)));
        System.out.println("copyOf null components: "
                + failure(() -> Map.Entry.copyOf(nullFrozen)));

        NavigableMap<Integer, String> sorted = new TreeMap<>();
        sorted.put(1, "one");
        sorted.put(2, "two");
        Map.Entry<Integer, String> first = sorted.firstEntry();
        sorted.put(1, "ONE");
        System.out.println("navigation snapshot: " + first + ", current=" + sorted);
        System.out.println("navigation setValue: " + failure(() -> first.setValue("X")));
        Map.Entry<Integer, String> removed = sorted.pollFirstEntry();
        System.out.println("polled snapshot: " + removed + ", source=" + sorted);

        Map<String, Integer> readOnly = Collections.unmodifiableMap(stock);
        System.out.println("read-only iterator entry: " + failure(() -> {
            for (Map.Entry<String, Integer> entry : readOnly.entrySet()) {
                entry.setValue(0);
            }
        }));

        List<String> tags = new ArrayList<>(List.of("a"));
        Map.Entry<String, List<String>> pair = Map.entry("tags", tags);
        Map.Entry<String, List<String>> shallow = Map.Entry.copyOf(pair);
        tags.add("b");
        System.out.println("unmodifiable pair shares payload: " + shallow);
        Map.Entry<String, List<String>> protectedPayload =
                Map.entry("tags", List.copyOf(tags));
        tags.add("c");
        System.out.println("shallow after another edit: " + shallow);
        System.out.println("copied string-list payload: " + protectedPayload);
        System.out.println("protected payload add: "
                + failure(() -> protectedPayload.getValue().add("d")));

        AbstractMap.SimpleEntry<String, Integer> left =
                new AbstractMap.SimpleEntry<>("item", 1);
        Map.Entry<String, Integer> right = Map.entry("item", 1);
        System.out.println("equal across entry implementations: " + left.equals(right));
        left.setValue(2);
        System.out.println("equal after value replacement: " + left.equals(right));
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 EntryOwnershipDemo.java
java EntryOwnershipDemo
~~~

Dùng JDK 17 trở lên để biên dịch ở mức release 17 và runtime Java 17 trở lên để chạy. Ví dụ đã được biên dịch với mức release 17 và chạy thành công bằng OpenJDK 24.0.2.

Stdout chính xác:

~~~text
connected setValue previous: 10
source after iteration: {sku-A=11, sku-B=20}
captured snapshots: [sku-A=11, sku-B=20]
source changed later: {sku-A=15, sku-C=30}
snapshots stay: [sku-A=11, sku-B=20]
mutable copies stay: [sku-A=11, sku-B=20]
local setValue previous: 11, local=sku-A=99
source unaffected by local edit: {sku-A=15, sku-C=30}
explicit put then local edit: local=sku-A=100, source={sku-A=99, sku-C=30}
copyOf detached pair: sku-A=100, local now=sku-A=101
copyOf setValue: UnsupportedOperationException
Map.entry setValue: UnsupportedOperationException
SimpleImmutableEntry setValue: UnsupportedOperationException
independent pair auto-inserted: false
independent null pair: null=null
null local old value: null, now=null=10
Map.entry null value: NullPointerException
copyOf null components: NullPointerException
navigation snapshot: 1=one, current={1=ONE, 2=two}
navigation setValue: UnsupportedOperationException
polled snapshot: 1=ONE, source={2=two}
read-only iterator entry: UnsupportedOperationException
unmodifiable pair shares payload: tags=[a, b]
shallow after another edit: tags=[a, b, c]
copied string-list payload: tags=[a, b]
protected payload add: UnsupportedOperationException
equal across entry implementations: true
equal after value replacement: false
~~~

### 10.1. Ghi xuyên trong vòng lặp, sao chép trước khi rời vòng lặp

Entry `sku-A` đang được duyệt cập nhật nguồn từ `10` lên `11` và trả giá trị cũ `10`. Cả ảnh chụp không sửa được lẫn bản cặp sửa được đều được tạo sau bước cập nhật ấy, nên lưu `sku-A=11`.

Sau vòng lặp, nguồn đổi thành `{sku-A=15, sku-C=30}`. Các cặp đã sao chép vẫn giữ `sku-A=11` và `sku-B=20`. Việc `sku-B` bị xóa khỏi nguồn không khiến các cặp độc lập mất dữ liệu.

### 10.2. Sửa cặp cục bộ và áp dữ liệu vào map là hai hành động

`local.setValue(99)` chỉ đổi bản cặp cục bộ. Lệnh `stock.put(local.getKey(), local.getValue())` sau đó mới cập nhật map thành `sku-A=99`.

Tiếp tục đổi `local` thành `100` không làm map nhận `100`. `copyOf(local)` lấy cặp `sku-A=100` độc lập; đổi `local` thành `101` cũng không làm ảnh chụp ấy đổi.

### 10.3. null, điều hướng và lớp bọc

Hai lớp entry độc lập có thể giữ thành phần `null`, trong khi hai factory `Map.entry` và `Map.Entry.copyOf` được kiểm tra trong ví dụ từ chối thành phần `null`.

Entry đầu của `TreeMap` giữ giá trị trước khi map được cập nhật, rồi từ chối `setValue`. Entry từ `pollFirstEntry` mô tả ánh xạ đã bị lấy bỏ. Entry qua map chỉ đọc cũng từ chối cập nhật.

### 10.4. Bất biến cặp khác bất biến dữ liệu con

Cặp `tags` không đổi tham chiếu nhưng danh sách giá trị được chia sẻ vẫn tăng từ `[a]` lên `[a, b]` rồi `[a, b, c]`.

Bản giữ `List.copyOf(tags)` được tạo trước lần thêm `c`, nên còn `[a, b]` và không cho thêm `d`. Điều này đủ cho ví dụ danh sách chuỗi; không tự là giải pháp sao chép sâu cho mọi kiểu giá trị.

## 11. Lưu và truyền entry trong mã thực tế

### 11.1. Trả dữ liệu cho xử lý về sau

Khi nhận một entry từ nguồn, hãy xác định bên nhận còn dùng trong cùng lần duyệt hay sau đó. Nếu vượt phạm vi duyệt, sao chép cặp hoặc chuyển sang kiểu dữ liệu miền phù hợp ngay lúc đọc hợp lệ.

Đối với lịch sử hoặc sự kiện, còn cần xác định giá trị con có thể đổi không. Một sự kiện “đã ghi giá trị tại thời điểm T” không đạt yêu cầu nếu nó chỉ giữ tham chiếu đến một danh sách còn được sửa về sau.

### 11.2. Ảnh chụp một cặp khác ảnh chụp nguyên tử toàn map

Sao chép từng entry bảo đảm cặp mới không còn là đường ghi xuyên vào ánh xạ nguồn. Nó không tự bảo đảm tất cả cặp của một list ảnh chụp được đọc tại cùng một thời điểm nếu nguồn đang thay đổi đồng thời.

Ngay cả nguồn đồng thời như `ConcurrentHashMap` cũng có chính sách quan sát tổng hợp riêng; iterator có thể phản ánh các trạng thái từ thời điểm khác nhau trong quá trình duyệt. [Java 17: ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

Nếu yêu cầu là một báo cáo nhất quán trên nhiều ánh xạ, phải có cơ chế đọc/công bố trạng thái phù hợp ở tầng sở hữu. Đừng dùng chữ “snapshot” của từng cặp để ngầm hứa một giao dịch của toàn bộ dữ liệu.

### 11.3. Không dùng ảnh chụp làm giấy phép cập nhật vô điều kiện

Giả sử bạn sao chép `product=10`, rồi một tác vụ khác đổi nguồn thành `product=12`. Sau đó lấy value từ ảnh chụp cũ để `put` trở lại có thể ghi đè dữ liệu mới.

Entry độc lập giúp kiểm soát vòng đời dữ liệu, nhưng không giải quyết xung đột cập nhật. Nếu yêu cầu phải cập nhật trên nền một giá trị kỳ vọng, hãy chọn API có điều kiện và cơ chế đồng thời phù hợp với nguồn.

### 11.4. Kiểm thử bằng hành vi được cam kết

Một kiểm thử tốt có thể xác nhận:

- `setValue` hợp lệ trong vòng lặp trên nguồn hỗ trợ cập nhật đúng map;
- cặp độc lập sửa cục bộ không tự ghi nguồn;
- ảnh chụp giữ cặp sau các cập nhật nguồn về sau;
- thao tác không được hỗ trợ ném đúng ngoại lệ theo lớp/factory đã chọn;
- dữ liệu con được chia sẻ hoặc sao chép đúng mức đã thiết kế.

Không viết kiểm thử dựa vào việc sử dụng một entry iterator đã hết phạm vi rồi đoán nó “nên” còn liên kết hay “nên” bị tách. Tránh biến hành vi ngoài hợp đồng thành yêu cầu phải duy trì.

## 12. Lỗi thường gặp

| Hiểu nhầm | Điều bị bỏ sót | Cách xử lý |
| --- | --- | --- |
| Mọi Map.Entry là một ô sống của map | Entry có thể độc lập hoặc là ảnh chụp | Xác định nơi tạo/lấy |
| Mọi setValue đều sửa nguồn | SimpleEntry chỉ sửa cặp riêng | Dùng API cập nhật đúng ý định |
| Entry iterator giữ được mãi | Kết nối có phạm vi duyệt | Sao chép khi cần giữ |
| new ArrayList(entrySet) tách mọi cặp | Chỉ có collection ngoài mới | Sao chép từng entry |
| Map.entry tự chèn dữ liệu | Factory chỉ tạo cặp | Gọi map.put nếu muốn thêm |
| copyOf phải trả đối tượng khác == | Factory có thể tái sử dụng | Kiểm chứng hợp đồng theo giá trị |
| Tên Immutable làm dữ liệu con bất biến | Tham chiếu có thể trỏ đối tượng sửa được | Sao chép/bọc theo từng tầng |
| Entry điều hướng luôn cho ghi nguồn | Nhiều API trả ảnh chụp không sửa được | Dùng API map phù hợp |
| Cặp equals nhau có cùng quyền sửa | Phép bằng chỉ xét khóa/value | Tách quyền thao tác khỏi dữ liệu |
| Sao chép cặp giải quyết mọi tranh chấp | Vẫn có dữ liệu cũ và thay đổi đồng thời | Thiết kế nhất quán và điều kiện cập nhật |

## 13. Thực hành có đáp án

### Bài 1 — Nguồn gốc quyết định tác dụng

Bạn có entry `e` do iterator của một `LinkedHashMap` sửa được trả về trong vòng lặp và entry `p = new SimpleEntry<>(e)`. Gọi `e.setValue(8)` rồi `p.setValue(9)` trong điều kiện hợp lệ. Lệnh nào cập nhật nguồn?

**Đáp án:** `e.setValue(8)` cập nhật ánh xạ nguồn nếu được hỗ trợ. `p` là cặp độc lập; `p.setValue(9)` chỉ đổi cặp ấy. Thời điểm tạo `p` quyết định value nó ghi nhận ban đầu, nhưng không tạo kết nối sống về sau.

### Bài 2 — Giữ entry để dùng sau

Một hàm duyệt map rồi trả thẳng một entry iterator tìm được để bên gọi sửa vào ngày hôm sau. Có thể dựa vào hợp đồng chung không?

**Đáp án:** không. Entry iterator không phải tay nắm cập nhật lâu dài được giao diện bảo đảm. Nếu cần dữ liệu để dùng sau, tạo cặp độc lập lúc đang đọc hợp lệ. Nếu cần cập nhật, thiết kế API theo khóa/điều kiện và chính sách sở hữu phù hợp.

### Bài 3 — List mới có đủ chưa?

`new ArrayList<>(map.entrySet())` có bảo đảm từng entry là ảnh chụp độc lập không? Cần thêm bước gì khi dữ liệu không có null?

**Đáp án:** chưa. Nó chỉ tạo list ngoài. Có thể sao chép từng entry bằng `Map.Entry.copyOf` trong lúc duyệt, hoặc dùng phép biến đổi tương ứng trong stream. Nếu cần giữ `null`, chọn một cách tạo cặp phù hợp như `SimpleImmutableEntry` theo chính sách dữ liệu.

### Bài 4 — Factory và null

So sánh `new SimpleImmutableEntry<>("K", null)` với `Map.entry("K", null)` và `Map.Entry.copyOf` của cặp đầu.

**Đáp án:** lớp entry cụ thể đầu có thể giữ giá trị `null` và không hỗ trợ `setValue`. `Map.entry` từ chối thành phần `null`; `copyOf` cũng từ chối cặp có khóa/value `null`. Việc nguồn map cho `null` không tự làm factory khác nhận `null`.

### Bài 5 — Bất biến cặp nhưng không bất biến List

Cặp được tạo bằng `Map.entry("items", mutableList)` và sao chép bằng `copyOf`. Sau đó thêm phần tử vào `mutableList`. Ảnh chụp có thể nhìn thấy không?

**Đáp án:** có vì các cặp giữ tham chiếu đến cùng list. Không đổi tham chiếu value không đồng nghĩa list không đổi trạng thái. Với danh sách chuỗi, có thể sao chép list thành `List.copyOf` theo yêu cầu; với phần tử sửa được, cần xác định tiếp độ sâu.

### Bài 6 — Entry của firstEntry

`TreeMap` đang có `1=one`. Lấy `before = firstEntry()` rồi cập nhật map thành `1=ONE`. Có dùng `before.setValue` để cập nhật tiếp được trong ví dụ không?

**Đáp án:** không; entry điều hướng của nguồn này là ảnh chụp không hỗ trợ `setValue`. Nó ghi nhận `1=one` tại lúc lấy. Dùng API map để cập nhật, đồng thời cân nhắc xung đột nếu nhiều tác vụ cùng sửa.

### Bài 7 — Phép bằng và quyền sửa

`new SimpleEntry<>("K", 1)` bằng `Map.entry("K", 1)`. Có suy cả hai cùng cho `setValue` không?

**Đáp án:** không. Phép bằng xét cùng khóa và giá trị, không xét quyền sửa hay nguồn gốc. Cặp thứ nhất sửa cục bộ được, cặp factory không cho đổi value. Đừng dùng equals hoặc == để suy quyền thao tác.

### Bài 8 — Ảnh chụp và cập nhật có điều kiện

Bạn giữ cặp độc lập `K=10`. Nguồn sau đó đổi thành `K=12`. Một tác vụ dùng cặp cũ để `put(K, 11)`. Sao chép entry đã ngăn ghi đè dữ liệu mới chưa?

**Đáp án:** chưa. Sao chép tách quan hệ sống và giữ dữ liệu đã đọc, không giữ khóa hay giải quyết xung đột cập nhật. Cần chính sách giá trị kỳ vọng, phiên bản hoặc cơ chế cập nhật đồng thời phù hợp với yêu cầu.

Khi luyện thêm, hãy ghi rõ mỗi entry trong chương trình thuộc loại nào trước khi gọi một phương thức: đang duyệt nguồn, cặp độc lập sửa được, hay cặp độc lập không sửa được. Sau đó xét riêng liệu khóa/value có chứa đối tượng còn sửa được hay không. Hai bước này giúp bạn sử dụng cặp khóa–giá trị đúng vòng đời và đúng quyền sở hữu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-copies-008">clone, serialization và giới hạn của từng cách sao chép</a>

<details>
<summary>Xem chi tiết</summary>

# clone, serialization và giới hạn của từng cách sao chép

## 1. Muốn “một bản khác” thì khác ở những chỗ nào?

Một danh sách chứa các giỏ hàng. Mỗi giỏ hàng lại giữ danh sách sản phẩm và thông tin giao nhận. Khi tạo bản sao danh sách để thử chỉnh sửa, việc có một đối tượng List mới chưa đủ để bảo đảm giỏ hàng gốc không đổi.

Điểm xuất phát của bài này là **đồ thị đối tượng**:

- Mỗi đối tượng là một nút.
- Trường tham chiếu, phần tử trong collection và khóa/giá trị của map tạo ra các liên kết.
- Biến đang có chỉ giữ một tham chiếu đến nút đầu vào, thường gọi là gốc của đồ thị.
- Hai liên kết có thể trỏ đến cùng một nút; một chuỗi liên kết cũng có thể quay về nút đã đi qua.

Vì vậy, “sao chép” cần có một phạm vi rõ ràng:

| Mức độ | Có gì mới? | Còn dùng chung gì? |
|---|---|---|
| Gán một tham chiếu sang biến khác | Chỉ có biến khác | Cùng đối tượng gốc |
| Sao chép nông (shallow copy) | Đối tượng hoặc cấu trúc chứa bên ngoài | Các đối tượng được tham chiếu bên trong |
| Sao chép sâu theo hợp đồng | Những nút được quy định phải tách trong đồ thị | Có thể vẫn dùng chung dữ liệu bất biến, dịch vụ hoặc đối tượng được quy định dùng chung |
| Chuyển đổi sang mô hình dữ liệu khác | Các đối tượng của mô hình đích | Chỉ giữ phần thông tin mà phép chuyển đổi định nghĩa |

**clone() không mặc định sao chép sâu. Serialization cũng không phải cam kết nhân bản độc lập mọi trạng thái đang có.** Mỗi cách có quy tắc và điều kiện riêng.

Trước khi chọn, hãy trả lời ba câu hỏi: thao tác nào ở bản sao được phép ảnh hưởng nguồn, liên kết dùng chung nào phải được giữ trong bản sao, và trạng thái nào không thể hoặc không nên mang sang?

Bài học tập trung vào sao chép collection cùng đồ thị được chứa bên trong, không thiết kế một giao thức lưu trữ tổng quát. Bốn chương trình đầy đủ dùng Java 17 trở lên, chỉ dùng thư viện chuẩn. Các kết quả được in bằng giá trị và so sánh tham chiếu, tránh phụ thuộc địa chỉ đối tượng hay thứ tự duyệt của HashMap.

## 2. Object.clone() và Cloneable: cơ chế cơ bản

### 2.1. Cloneable là dấu hiệu cho Object.clone(), không phải API sao chép chung

Object khai báo phương thức clone() với quyền protected và kiểu trả về Object. Cloneable là một interface đánh dấu, không khai báo phương thức clone().

Điều này dẫn đến hai khác biệt:

- Lớp triển khai Cloneable nhưng không công khai một phương thức phù hợp thì nơi gọi bên ngoài vẫn chưa có API clone() công khai.
- Một biến có kiểu Cloneable không thể chỉ dựa vào interface đó để gọi clone() như gọi một phương thức trong hợp đồng interface.

Theo cách dùng thông thường, lớp hỗ trợ clone sẽ ghi đè clone(), tăng quyền truy cập thành public khi muốn công khai, và có thể dùng kiểu trả về cụ thể của lớp.

Nếu thực thi Object.clone() trên đối tượng không triển khai Cloneable, nó ném CloneNotSupportedException. Đây là điều kiện của cơ chế Object.clone(); một phương thức tự viết có tên clone nhưng không dùng cơ chế đó còn có thể định nghĩa chính sách khác. Tài liệu [Cloneable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Cloneable.html) giải thích rõ vai trò đánh dấu và việc interface không khai báo clone().

### 2.2. Object.clone() sao chép từng trường như phép gán

Đối với cách triển khai của Object:

- Giá trị kiểu nguyên thủy được chép sang trường tương ứng.
- Giá trị của trường tham chiếu cũng được chép, nghĩa là bản sao giữ tham chiếu tới cùng đối tượng bên trong.
- Các đối tượng được tham chiếu không tự được clone tiếp.
- Không chạy constructor và bộ khởi tạo trường của lớp để tạo lại trạng thái như khi dùng new theo cách thông thường.
- Một trường instance được đánh dấu transient vẫn được chép bởi cơ chế clone; transient có ý nghĩa khác trong serialization.

Hãy hình dung một đối tượng có int count và List<Item> items. Sau super.clone(), count là một giá trị riêng trên đối tượng mới, còn items ở hai phía vẫn chỉ cùng một danh sách.

Để tạo bản sao sâu bằng clone, lớp phải chủ động thay các tham chiếu cần tách sau super.clone(), đồng thời xử lý quan hệ dùng chung và vòng tham chiếu đúng chính sách. [Object.clone](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#clone()) mô tả phép sao chép từng trường; nó không làm bước đệ quy ấy thay lớp.

### 2.3. Đối tượng mới không đồng nghĩa equals hoặc tài nguyên mới

Thông lệ clone thường tạo đối tượng khác tham chiếu và cùng lớp chạy với nguồn. Tuy vậy, không dùng một tên phương thức đơn lẻ để suy ra mọi thuộc tính của kết quả. Cần đọc hợp đồng của chính lớp.

Cũng không có yêu cầu chung rằng bản clone luôn equals nguồn. Nếu lớp chưa ghi đè equals(), hai đối tượng khác tham chiếu thường không bằng nhau theo equals() của Object.

Một tham chiếu tới kết nối cơ sở dữ liệu, luồng nhập/xuất hoặc dịch vụ không trở thành tài nguyên độc lập chỉ vì đối tượng chứa nó được clone. Sao chép tài nguyên bên ngoài phải có chính sách riêng.

## 3. Clone của collection và mảng: tách lớp chứa, còn chung phần tử

ArrayList.clone() tạo một ArrayList mới với các phần tử vẫn là những tham chiếu cũ. HashMap.clone() tạo map mới nhưng không clone khóa hoặc giá trị. Đây là sao chép nông ở cấp cấu trúc chứa.

Chương trình dưới đây dùng các Box có trường value thay đổi được. Key là record chỉ chứa String để việc kiểm tra map không bị nhiễu bởi vấn đề khóa thay đổi mã băm.

Tệp **CollectionCloneDemo.java**:

```java
import java.util.ArrayList;
import java.util.HashMap;

public class CollectionCloneDemo {
    static final class Box {
        int value;

        Box(int value) {
            this.value = value;
        }
    }

    record Key(String id) {}

    @SuppressWarnings("unchecked")
    private static ArrayList<Box> cloneList(ArrayList<Box> source) {
        return (ArrayList<Box>) source.clone();
    }

    @SuppressWarnings("unchecked")
    private static HashMap<Key, Box> cloneMap(HashMap<Key, Box> source) {
        return (HashMap<Key, Box>) source.clone();
    }

    public static void main(String[] args) {
        ArrayList<Box> source = new ArrayList<>();
        source.add(new Box(10));
        ArrayList<Box> copy = cloneList(source);
        System.out.println("newList=" + (copy != source));
        System.out.println("sharedElement=" + (copy.get(0) == source.get(0)));

        copy.add(new Box(20));
        System.out.println("listSizes=" + source.size() + "," + copy.size());
        copy.get(0).value = 99;
        System.out.println("originalAfterElementEdit=" + source.get(0).value);
        copy.set(0, new Box(7));
        System.out.println("afterReplace=" +
                source.get(0).value + "," + copy.get(0).value);

        Key key = new Key("K");
        HashMap<Key, Box> map = new HashMap<>();
        map.put(key, new Box(50));
        HashMap<Key, Box> mapCopy = cloneMap(map);
        System.out.println("sharedKey=" +
                (mapCopy.keySet().iterator().next() == key));
        System.out.println("sharedValue=" + (mapCopy.get(key) == map.get(key)));
        mapCopy.get(key).value = 66;
        mapCopy.remove(key);
        System.out.println("mapSizes=" + map.size() + "," + mapCopy.size());
        System.out.println("originalMapValue=" + map.get(key).value);

        int[] numbers = {10, 20};
        int[] numbersCopy = numbers.clone();
        numbersCopy[0] = 90;
        System.out.println("primitiveArray=" + numbers[0] + "," + numbersCopy[0]);

        Box[] objects = {new Box(3)};
        Box[] objectsCopy = objects.clone();
        objectsCopy[0].value = 4;
        System.out.println("objectArrayShared=" + (objects[0] == objectsCopy[0]));
        System.out.println("objectArrayOriginal=" + objects[0].value);

        int[][] matrix = {{1, 2}, {3, 4}};
        int[][] matrixCopy = matrix.clone();
        matrixCopy[0][0] = 8;
        System.out.println("newOuterArray=" + (matrix != matrixCopy));
        System.out.println("sharedRow=" + (matrix[0] == matrixCopy[0]));
        System.out.println("matrixOriginal=" + matrix[0][0]);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 CollectionCloneDemo.java
java CollectionCloneDemo
```

Stdout chính xác:

```text
newList=true
sharedElement=true
listSizes=1,2
originalAfterElementEdit=99
afterReplace=99,7
sharedKey=true
sharedValue=true
mapSizes=1,0
originalMapValue=66
primitiveArray=10,90
objectArrayShared=true
objectArrayOriginal=4
newOuterArray=true
sharedRow=true
matrixOriginal=8
```

### 3.1. Thêm, thay phần tử và sửa phần tử là ba việc khác nhau

Hai list có cấu trúc riêng nên copy.add(...) làm kích thước bản sao tăng mà không thêm vào nguồn. Tuy nhiên, copy.get(0) ban đầu vẫn là Box của nguồn.

Bởi vậy copy.get(0).value = 99 thay chính đối tượng Box đang được cả hai list giữ. Sau đó copy.set(0,new Box(7)) chỉ thay tham chiếu ở một vị trí của list copy; nó không thay tham chiếu ở list source. Kết quả hai bên lúc này là 99 và 7.

Ở map, mapCopy.get(key).value = 66 sửa giá trị dùng chung. mapCopy.remove(key) chỉ xóa ánh xạ của bản sao. Ánh xạ của nguồn vẫn tồn tại với Box đã có value bằng 66.

Các kết quả này minh họa đúng hợp đồng sao chép nông của [ArrayList.clone](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html#clone()) và [HashMap.clone](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html#clone()).

### 3.2. Mảng nguyên thủy và mảng tham chiếu

Mảng có hỗ trợ clone; kiểu trả về của clone trên một kiểu mảng cụ thể là chính kiểu mảng đó. Không cần ép Object về int[] hoặc Box[] khi gọi trực tiếp như ví dụ.

int[] chứa giá trị int, nên clone tạo mảng khác và sửa số trong mảng mới không sửa số trong mảng cũ.

Box[] chứa tham chiếu, nên mảng mới vẫn trỏ tới các Box cũ. int[][] cũng là mảng chứa tham chiếu đến các hàng int[]. Vì vậy clone() trên int[][] chỉ tách mảng ngoài; các hàng vẫn dùng chung.

Nếu một ma trận thật sự là các hàng độc lập và muốn tách mọi hàng, có thể sao chép từng hàng theo chính sách của ứng dụng. Nhưng nếu nhiều vị trí cố ý trỏ cùng một hàng, clone từng hàng một cách độc lập sẽ làm mất quan hệ dùng chung đó. Ngay cả “mảng hai chiều” cũng cần hiểu hình dạng đồ thị.

### 3.3. Không nâng hỗ trợ clone của một lớp thành bảo đảm của Collection

Các interface Collection, List, Set và Map không cung cấp một phương thức clone() công khai chung. Một biến khai báo List không tự có clone chỉ vì đối tượng lúc chạy có thể là ArrayList.

Một lớp cụ thể hỗ trợ clone cũng không có nghĩa mọi view, lớp bọc hoặc kết quả trả về từ một factory có cùng khả năng. Không ép kiểu một đối tượng không biết rõ sang ArrayList chỉ để dùng clone.

Với yêu cầu sao chép cấu trúc thông thường, constructor nhận collection/map hoặc factory sao chép tường minh thường dễ biểu đạt hơn. Chúng cũng cần được đọc đúng hợp đồng: tạo cấu trúc mới không tự sao chép sâu từng đối tượng.

## 4. Sao chép sâu phải giữ đúng quan hệ giữa các đối tượng

Giả sử root có hai liên kết cùng trỏ đến child, còn child trỏ lại root. Có hai yêu cầu đồng thời:

1. Bản sao phải có root và child riêng để có thể sửa độc lập.
2. Hai liên kết ở root mới vẫn phải cùng chỉ đến một child mới; đường quay lại phải đến root mới.

Nếu chỉ viết một hàm đệ quy “gặp đối tượng nào thì new đối tượng đó”, nó có thể lặp vô hạn ở chu trình hoặc tạo nhiều bản sao cho cùng một nút, làm mất quan hệ ban đầu.

Bảng ánh xạ **tham chiếu nguồn → tham chiếu đích đã tạo** giải quyết cả hai vấn đề. Dùng IdentityHashMap để nhận diện cùng đối tượng bằng ==, thay vì gộp các đối tượng khác nhau chỉ vì equals() trả true.

### 4.1. Chương trình giữ tham chiếu trùng và vòng tham chiếu

Node bên dưới có một clone công khai, dùng super.clone() để minh họa sao chép nông. Hàm deepCopy() là phép sao chép được viết riêng cho mô hình Node; nó không dùng serialization và không gọi clone cho từng nút.

Tệp **GraphCopyDemo.java**:

```java
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

public class GraphCopyDemo {
    static final class Node implements Cloneable {
        String name;
        final List<Node> links = new ArrayList<>();

        Node(String name) {
            this.name = name;
        }

        @Override
        public Node clone() {
            try {
                return (Node) super.clone();
            } catch (CloneNotSupportedException impossible) {
                throw new AssertionError(impossible);
            }
        }
    }

    private static Node deepCopy(Node source) {
        return copy(source, new IdentityHashMap<>());
    }

    private static Node copy(Node source, IdentityHashMap<Node, Node> seen) {
        if (source == null) {
            return null;
        }
        Node existing = seen.get(source);
        if (existing != null) {
            return existing;
        }

        Node target = new Node(source.name);
        seen.put(source, target);
        for (Node link : source.links) {
            target.links.add(copy(link, seen));
        }
        return target;
    }

    public static void main(String[] args) {
        Node root = new Node("root");
        Node child = new Node("child");
        root.links.add(child);
        root.links.add(child);
        child.links.add(root);

        Node shallow = root.clone();
        System.out.println("shallowNewRoot=" + (shallow != root));
        System.out.println("shallowSharedList=" + (shallow.links == root.links));
        System.out.println("shallowCycleUsesOriginal=" +
                (shallow.links.get(0).links.get(0) == root));

        Node deep = deepCopy(root);
        Node copiedChild = deep.links.get(0);
        System.out.println("deepNewRoot=" + (deep != root));
        System.out.println("deepNewList=" + (deep.links != root.links));
        System.out.println("deepNewChild=" + (copiedChild != child));
        System.out.println("aliasPreserved=" +
                (deep.links.get(0) == deep.links.get(1)));
        System.out.println("cyclePreserved=" + (copiedChild.links.get(0) == deep));

        copiedChild.name = "child-copy";
        deep.links.add(null);
        System.out.println("names=" + child.name + "," + copiedChild.name);
        System.out.println("linkCounts=" + root.links.size() + "," + deep.links.size());
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 GraphCopyDemo.java
java GraphCopyDemo
```

Stdout chính xác:

```text
shallowNewRoot=true
shallowSharedList=true
shallowCycleUsesOriginal=true
deepNewRoot=true
deepNewList=true
deepNewChild=true
aliasPreserved=true
cyclePreserved=true
names=child,child-copy
linkCounts=2,3
```

### 4.2. Đăng ký nút mới trước khi đi theo liên kết

Hàm copy() làm bốn việc theo đúng thứ tự:

1. Trả null nếu liên kết nguồn là null.
2. Nếu nút nguồn đã có trong bảng seen, trả đúng nút đích tương ứng.
3. Tạo nút đích rồi đưa vào seen ngay lập tức.
4. Sau đó mới sao chép các liên kết của nút nguồn.

Bước thứ ba phải đi trước đệ quy. Khi child trỏ lại root, bảng đã biết root mới là đối tượng nào; hàm không tạo root lần nữa và không đệ quy vô hạn.

Hai liên kết cùng đến child cũng gặp cùng một mục trong seen, nên aliasPreserved bằng true. Đây là giữ quan hệ cùng trỏ một đối tượng trong đồ thị mới, không phải giữ liên kết trở về đối tượng mutable ở nguồn.

[IdentityHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html) nêu việc biến đổi đồ thị và sao chép sâu bảo toàn quan hệ tham chiếu là một trường hợp sử dụng điển hình.

### 4.3. final không làm danh sách trở nên bất biến

Node.links là final, nghĩa là không thể gán một danh sách khác vào trường đó sau khi được khởi tạo theo quy tắc thông thường. Nội dung danh sách vẫn có thể thêm, xóa hoặc sửa.

super.clone() chép tham chiếu của trường final này. shallow.links và root.links vì thế là cùng một danh sách. Nếu trong một bản clone nông mà gọi links.clear() với ý định “làm sạch trước khi chép lại”, sẽ xóa luôn danh sách của nguồn.

Hàm deepCopy() dùng new Node(...) nên constructor tạo danh sách riêng ngay từ đầu. Nó không phải dùng thủ thuật phản chiếu để đổi một trường final của bản clone.

Đây là một lý do chọn constructor/factory sao chép tường minh khi thiết kế lớp mới: khởi tạo đối tượng đích và các bất biến có thể đọc được ngay trong code.

### 4.4. Phạm vi của thuật toán phải được công khai

GraphCopyDemo chỉ biết mô hình Node gồm tên String và các liên kết Node. Nó chủ động dùng chung String vì String bất biến. Nó không phải hàm sao chép mọi đối tượng Java, không xử lý tự động tài nguyên hệ điều hành, mọi kiểu con hoặc bất kỳ trường mới nào được thêm về sau.

Nếu Node có thêm thuộc tính mutable, người thiết kế phải quyết định sao chép, dùng chung, bỏ qua hay tính lại thuộc tính đó. Nếu cần giữ quan hệ giữa nhiều gốc, phải dùng chung một bảng seen cho toàn bộ nhóm gốc cần sao chép; mỗi lần tạo bảng mới là một đồ thị sao chép riêng.

Với đồ thị rất sâu, cách đệ quy có thể vượt ngăn xếp. Một thuật toán dùng hàng đợi/ngăn xếp tường minh có thể cần thiết khi quy mô dữ liệu yêu cầu. “Có bảng đã gặp” giải quyết chu trình, không tự bảo đảm độ sâu đệ quy vô hạn.

## 5. Serialization: mã hóa đồ thị rồi dựng lại

Java Object Serialization dùng ObjectOutputStream để ghi trạng thái thành byte và ObjectInputStream để đọc lại. Khi dùng nó để sao chép, thường có quy trình:

1. Ghi một gốc đối tượng vào bộ đệm byte.
2. Tạo luồng đọc mới từ các byte đó.
3. Đọc gốc được dựng lại và kiểm tra kiểu mong đợi.

Đây là sao chép thông qua **hợp đồng biểu diễn tuần tự hóa**, không phải chụp nguyên trạng bộ nhớ của JVM.

### 5.1. Cơ chế giữ tham chiếu trong một luồng

Với các đối tượng serializable thông thường, luồng ghi theo dõi những đối tượng đã gặp và cấp định danh nội bộ cho chúng. Gặp lại cùng một đối tượng, nó có thể ghi liên kết tới định danh đã có thay vì ghi một bản độc lập khác.

Khi đọc lại, cơ chế này giữ được nhiều liên kết cùng trỏ một đối tượng và các chu trình trong đồ thị thông thường. Nó không tự nhân đôi một nút chỉ vì có hai đường dẫn tới nút đó.

Đặc tả [kiến trúc Java Object Serialization](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/serial-arch.html) mô tả cách dùng định danh để tái tạo quan hệ dùng chung và vòng tham chiếu. Các ngoại lệ do kiểu đặc biệt hoặc cơ chế thay thế đối tượng sẽ được xét ở phần giới hạn.

### 5.2. Serializable của đối tượng gốc chưa đủ

Serializable cũng là interface đánh dấu. Theo cơ chế mặc định, các trường instance không static và không transient của lớp serializable tham gia trạng thái được ghi. Phần được ghi có thể được tùy biến bằng các cơ chế serialization riêng của lớp.

Một ArrayList triển khai Serializable không làm mọi phần tử bất kỳ trong nó trở nên serializable. Khi cơ chế đi tới một đối tượng không hỗ trợ cần được ghi, có thể nhận NotSerializableException. Điều tương tự xảy ra với khóa, giá trị, Comparator hoặc cấu hình được đồ thị tham chiếu tới.

Ngay cả một hàm generic dạng T extends Serializable cũng chỉ kiểm tra được kiểu gốc qua hệ kiểu; nó không chứng minh mọi nút thực tế được duyệt đều hợp lệ. Cần hiểu mô hình cụ thể và xử lý lỗi lúc chạy.

Đánh dấu một trường transient có thể loại nó khỏi cơ chế mặc định, nhưng đó là quyết định bỏ hoặc dựng lại trạng thái, không phải đã sao chép thành công trạng thái ấy.

## 6. Chương trình: sao chép đồ thị và thấy phần trạng thái bị thay đổi

Ví dụ sau có hai Node với một vòng tham chiếu và hai liên kết trùng tới child. Nó còn có:

- cache là trường transient có giá trị khởi tạo.
- global là trường static.
- inherited thuộc Base không triển khai Serializable.
- policy là enum.
- token có readResolve() trả lại một đối tượng dùng chung.

Chương trình chỉ đọc byte vừa tự tạo trong bộ nhớ. Bộ lọc luồng đọc giới hạn những lớp và quy mô cần cho chính ví dụ này; đây không phải hàm tiếp nhận byte tùy ý từ bên ngoài.

Tệp **SerializationCopyDemo.java**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SerializationCopyDemo {
    static class Base {
        static int constructions;
        int inherited = 7;

        Base() {
            constructions++;
        }
    }

    enum Policy { STANDARD }

    static final class Token implements Serializable {
        private static final long serialVersionUID = 1L;
        static final Token INSTANCE = new Token();

        private Token() {}

        private Object readResolve() {
            return INSTANCE;
        }
    }

    static final class Node extends Base implements Serializable {
        private static final long serialVersionUID = 1L;
        static int constructions;
        static String global = "before";

        String name;
        List<Node> links = new ArrayList<>();
        transient String cache = "warm";
        Policy policy = Policy.STANDARD;
        Token token = Token.INSTANCE;

        Node(String name) {
            constructions++;
            this.name = name;
        }
    }

    private static byte[] serialize(Serializable source) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(source);
        }
        return buffer.toByteArray();
    }

    private static ObjectInputFilter.Status filter(ObjectInputFilter.FilterInfo info) {
        if (info.depth() > 20 || info.references() > 1000 ||
                info.arrayLength() > 1000 || info.streamBytes() > 1_000_000) {
            return ObjectInputFilter.Status.REJECTED;
        }
        Class<?> type = info.serialClass();
        if (type == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        if (type == Node.class || type == Token.class ||
                type == Policy.class || type == Enum.class ||
                type == ArrayList.class || type == Object[].class ||
                type == String.class) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        return ObjectInputFilter.Status.REJECTED;
    }

    private static Node readLocalBytes(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream in =
                new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(SerializationCopyDemo::filter);
            return Node.class.cast(in.readObject());
        }
    }

    public static void main(String[] args) throws Exception {
        Node root = new Node("root");
        Node child = new Node("child");
        root.links.add(child);
        root.links.add(child);
        child.links.add(root);
        root.inherited = 99;

        byte[] bytes = serialize(root);
        Base.constructions = 0;
        Node.constructions = 0;
        Node.global = "changed-after-write";
        Node copy = readLocalBytes(bytes);

        System.out.println("newRoot=" + (copy != root));
        System.out.println("newChild=" + (copy.links.get(0) != child));
        System.out.println("aliasPreserved=" +
                (copy.links.get(0) == copy.links.get(1)));
        System.out.println("cyclePreserved=" +
                (copy.links.get(0).links.get(0) == copy));
        System.out.println("cache=" + copy.cache);
        System.out.println("global=" + Node.global);
        System.out.println("inherited=" + copy.inherited);
        System.out.println("baseConstructors=" + Base.constructions);
        System.out.println("nodeConstructors=" + Node.constructions);
        System.out.println("sameEnum=" + (copy.policy == root.policy));
        System.out.println("sameResolvedToken=" + (copy.token == root.token));

        copy.links.get(0).name = "child-copy";
        System.out.println("names=" + child.name + "," + copy.links.get(0).name);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SerializationCopyDemo.java
java SerializationCopyDemo
```

Stdout chính xác:

```text
newRoot=true
newChild=true
aliasPreserved=true
cyclePreserved=true
cache=null
global=changed-after-write
inherited=7
baseConstructors=2
nodeConstructors=0
sameEnum=true
sameResolvedToken=true
names=child,child-copy
```

### 6.1. Các nút thường được tách, quan hệ trong đồ thị được giữ

copy khác root; child trong copy cũng khác child gốc. Tuy vậy, hai vị trí trong copy.links cùng trỏ tới một child mới. Liên kết từ child mới quay về chính copy.

Sửa tên child ở bản sao không sửa tên ở nguồn. Đây là phần hành vi thường khiến serialization được dùng như một cách sao chép sâu cho các mô hình đã hỗ trợ nó.

Nhưng chính chương trình cũng cho thấy không thể kết luận “mọi trạng thái đều độc lập” chỉ từ việc gốc và child đã được tạo mới.

### 6.2. transient và static không đi theo cơ chế mặc định

cache được khai báo transient và không có code khôi phục riêng. Giá trị warm được khởi tạo khi gọi constructor Node ở chương trình gốc, nhưng phần khởi tạo của lớp Node không chạy lại theo cách new Node(...) trong lần đọc này. cache của bản đọc lại là null.

Nếu cần cache sau khi đọc, phải tính lại khi dùng hoặc khôi phục bằng cơ chế phù hợp. Không hiểu transient là “tự đặt lại về giá trị viết ở khai báo trường”.

global là static, thuộc lớp trong JVM, không phải trạng thái riêng của mỗi Node. Sau khi đổi thành changed-after-write rồi đọc byte, nó vẫn là changed-after-write. Dữ liệu đã ghi không phục hồi giá trị before cho biến static này.

Tương phản đáng nhớ: clone nông chép cả giá trị của trường instance transient; serialization mặc định bỏ trường đó. Từ khóa transient không phải một chỉ dẫn chung cho mọi kiểu sao chép.

### 6.3. Phần lớp cha không serializable được khởi tạo lại

Base không triển khai Serializable. Trạng thái inherited = 99 của root không được ghi tự động như trạng thái của Node. Khi dựng lại hai Node, constructor không đối số của Base được gọi cho mỗi đối tượng, làm inherited trở lại 7 và bộ đếm Base tăng lên 2.

Constructor Node không chạy, nên bộ đếm Node vẫn là 0. Cần đọc cả hai dòng, không nói chung rằng “deserialization không gọi constructor nào”.

Với lớp Serializable thông thường, constructor không đối số của lớp cha không serializable đầu tiên phải truy cập được theo quy tắc dành cho lớp con. Nếu không có điều kiện này, quá trình dựng lại có thể thất bại. Hợp đồng này được mô tả ở [Serializable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serializable.html).

### 6.4. readResolve và enum giữ đối tượng được quy định dùng chung

Token.readResolve() trả Token.INSTANCE. Kết quả đọc cho trường token vì thế trỏ lại singleton đã có; sameResolvedToken bằng true.

policy là enum. Trong cùng định nghĩa kiểu enum đã được nạp, đọc lại hằng enum nhận chính hằng tương ứng, không tạo một bản enum độc lập như một Node thông thường; sameEnum cũng bằng true.

Việc dùng chung enum bất biến thường đúng mong muốn. Nhưng nếu một lớp có readResolve() trả một singleton mutable, bản đọc lại có thể tiếp tục dùng chung trạng thái thay đổi được qua singleton ấy.

Do đó kết luận phù hợp là: serialization tái tạo đồ thị **theo giao thức của các lớp tham gia**, bao gồm quy tắc thay thế và dùng chung của chúng. Nó không bảo đảm “mọi đối tượng reachable đều khác tham chiếu so với nguồn”.

## 7. Constructor, bất biến và các cơ chế tùy biến

### 7.1. Không dùng constructor thông thường làm bằng chứng đã kiểm tra dữ liệu

Một lớp có constructor kiểm tra giá trị hợp lệ không tự có nghĩa đọc Serializable thông thường sẽ chạy lại chính constructor ấy. Nếu một bất biến cần được duy trì sau khi đọc, thiết kế serialization của lớp phải có bước kiểm tra/khôi phục thích hợp.

Lớp có thể định nghĩa readObject() để đọc trạng thái, kiểm tra điều kiện và từ chối dữ liệu không hợp lệ, chẳng hạn bằng InvalidObjectException. Một số thiết kế dùng mẫu đại diện tuần tự hóa để dựng lại qua một đường khởi tạo được kiểm soát.

Không thêm readObject một cách máy móc: thứ tự đọc, trạng thái đang được khôi phục, quan hệ với lớp cha và vòng tham chiếu đều cần được xem xét. Với nhu cầu sao chép dữ liệu ứng dụng mới, một factory/constructor sao chép rõ ràng có thể đơn giản hơn việc mở thêm một đường dựng đối tượng bằng serialization.

### 7.2. writeObject, writeReplace và readResolve làm thay đổi ý nghĩa sao chép

Các lớp có thể ghi/đọc trạng thái tùy biến qua writeObject()/readObject(), thay đối tượng được ghi bằng writeReplace(), hoặc thay kết quả đọc bằng readResolve().

Điều này có nghĩa:

- Không phải mọi trường transient đều nhất định bị mất; lớp có thể ghi hoặc dựng lại thông tin liên quan bằng code riêng.
- Không phải mọi trường không transient đều được sao chép đúng theo cách nhìn trực tiếp vào trường; biểu diễn tuần tự hóa có thể được tùy biến.
- Không phải mọi đối tượng đọc ra đều có quan hệ tham chiếu như một bản nhân đôi thuần túy của nguồn.
- Code của các lớp tham gia có thể chạy trong khi ghi và đọc.

Hãy đánh giá hợp đồng của **toàn bộ mô hình tham gia**, không chỉ việc đối tượng gốc có implements Serializable.

Đặc tả [luồng ghi](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/output.html) và [luồng đọc](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/input.html) phân định các điểm tùy biến này.

### 7.3. Externalizable, enum và record có quy tắc riêng

Externalizable mở rộng Serializable nhưng giao trách nhiệm ghi/đọc cho writeExternal()/readExternal(). Khi dựng lại, nó dùng constructor public không đối số rồi gọi readExternal(). Không áp nguyên quy tắc “constructor lớp serializable không chạy” của lớp thông thường sang trường hợp này. Xem [Externalizable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Externalizable.html).

Record có thể triển khai Serializable; khi đọc, constructor chuẩn tắc của record được dùng để tạo đối tượng từ các thành phần. Một vòng tham chiếu đi qua thành phần của record cũng không được bảo toàn như chu trình của lớp thông thường trong GraphCopyDemo hoặc SerializationCopyDemo.

Enum có cách xử lý riêng theo tên hằng. Các phương thức tùy biến serialization không có cùng tác dụng như với lớp thông thường. Vì vậy không tổng quát kết quả constructor hoặc chu trình của một ví dụ cho mọi kiểu dữ liệu Java.

Các khác biệt của [record và vòng tham chiếu](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/serial-arch.html#serialization-of-records) là một giới hạn thực tế khi cân nhắc dùng serialization làm công cụ sao chép tổng quát.

## 8. Chương trình kiểm chứng lỗi và việc dùng lại một luồng

SerializationLimitsDemo giải quyết bốn hiểu nhầm:

1. Collection serializable thì mọi phần tử cũng ghi được.
2. Comparator dùng được cho cây thì đương nhiên ghi được.
3. Ghi cùng một đối tượng lần nữa luôn tạo ảnh chụp mới.
4. Bộ lọc không cho phép kiểu vẫn có thể bị bỏ qua chỉ vì sau đó ép kiểu đúng.

Tệp **SerializationLimitsDemo.java**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

public class SerializationLimitsDemo {
    static final class Box implements Serializable {
        private static final long serialVersionUID = 1L;
        int value;

        Box(int value) {
            this.value = value;
        }
    }

    private static byte[] serialize(Object source) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(source);
        }
        return buffer.toByteArray();
    }

    private static void expectNotSerializable(String label, Object source)
            throws IOException {
        try {
            serialize(source);
            throw new AssertionError(label + ": no exception");
        } catch (NotSerializableException expected) {
            System.out.println(label + "=NotSerializableException");
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        expectNotSerializable("badElement",
                new ArrayList<>(List.of(new Object())));

        Comparator<Integer> order = (a, b) -> Integer.compare(b, a);
        TreeMap<Integer, String> tree = new TreeMap<>(order);
        tree.put(1, "one");
        tree.put(2, "two");
        TreeMap<Integer, String> treeClone =
                (TreeMap<Integer, String>) tree.clone();
        System.out.println("cloneOrder=" + treeClone.navigableKeySet());
        System.out.println("sharedComparator=" +
                (treeClone.comparator() == tree.comparator()));
        expectNotSerializable("badComparator", tree);

        Box original = new Box(10);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(original);
            original.value = 20;
            out.writeObject(original);
            out.reset();
            out.writeObject(original);
        }

        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(buffer.toByteArray()))) {
            in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                    "maxdepth=10;maxrefs=100;maxbytes=100000;" +
                    "SerializationLimitsDemo$Box;!*"));
            Box first = (Box) in.readObject();
            Box second = (Box) in.readObject();
            Box third = (Box) in.readObject();
            System.out.println("sameWithoutReset=" + (first == second));
            System.out.println("valuesWithoutReset=" +
                    first.value + "," + second.value);
            System.out.println("newAfterReset=" + (third != first));
            System.out.println("valueAfterReset=" + third.value);
        }

        byte[] local = serialize(new Box(1));
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(local))) {
            in.setObjectInputFilter(info -> ObjectInputFilter.Status.REJECTED);
            in.readObject();
            throw new AssertionError("filter did not reject");
        } catch (InvalidClassException expected) {
            System.out.println("filter=InvalidClassException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SerializationLimitsDemo.java
java SerializationLimitsDemo
```

Stdout chính xác:

```text
badElement=NotSerializableException
cloneOrder=[2, 1]
sharedComparator=true
badComparator=NotSerializableException
sameWithoutReset=true
valuesWithoutReset=10,10
newAfterReset=true
valueAfterReset=20
filter=InvalidClassException
```

### 8.1. Cấu trúc chứa hợp lệ, phần được duyệt không hợp lệ

ArrayList trong chương trình chứa new Object(). ArrayList hỗ trợ Serializable nhưng Object này không hỗ trợ, nên ghi cả đồ thị bị NotSerializableException.

Comparator của tree là lambda chỉ được gán vào Comparator<Integer>, không phải một kiểu đích yêu cầu Serializable. Nó hoạt động khi so sánh và vẫn được dùng chung bởi treeClone, nhưng không ghi được theo ví dụ này. Serialization tree vì thế cũng thất bại.

Đổi lambda sang một Comparator được thiết kế có hỗ trợ Serializable có thể giải quyết riêng lỗi khả năng ghi của đối tượng đó; vẫn phải kiểm tra trạng thái mà Comparator giữ và hợp đồng tương thích. Không đánh dấu Serializable như một cách làm dữ liệu tùy ý tự trở nên sao chép được.

Một bản clone cây có thể giữ Comparator cũ mà không cần ghi thành byte. Vì vậy điều kiện thành công của clone và serialization khác nhau. Với Comparator thay đổi được, việc clone dùng chung Comparator cũng là một liên kết phải đưa vào đánh giá mức độ độc lập.

### 8.2. Luồng ghi có bảng đối tượng đã gặp

Lần đầu ghi original có value = 10. Sau khi sửa original.value thành 20, lần ghi thứ hai trên cùng ObjectOutputStream vẫn có thể chỉ ghi tham chiếu ngược tới đối tượng đã biết.

Khi đọc, first và second là cùng một đối tượng, cùng value bằng 10. Lần ghi thứ hai không tạo ra một bản chụp mới của trạng thái value = 20.

out.reset() xóa trạng thái theo dõi của luồng tại thời điểm hợp lệ và làm lần ghi sau coi original là đối tượng cần ghi lại. Vì thế third khác first và có value = 20. [ObjectOutputStream.reset](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectOutputStream.html#reset()) mô tả tác dụng làm mới trạng thái này.

Nếu muốn mỗi lần sao chép là một đồ thị độc lập, cách dễ đọc thường là dùng cặp luồng mới cho mỗi lần sao chép, thay vì vô tình giữ một ObjectOutputStream lâu dài. Nếu cần giữ quan hệ giữa nhiều gốc trong cùng một bản sao, lại phải xác định rõ chúng thuộc cùng lần ghi/đọc nào.

writeUnshared()/readUnshared() có những quy tắc riêng, không phải lối tắt nhân bản độc lập mọi đối tượng con. Không thay một phép gọi rồi bỏ qua quan hệ toàn đồ thị.

### 8.3. Lỗi lọc là một lỗi đọc, không phải lỗi generic

Đoạn cuối tạo byte hợp lệ từ Box, sau đó bộ lọc chủ động từ chối tất cả. readObject() thất bại bằng InvalidClassException; việc luồng có thể chứa đúng kiểu Box không làm nó vượt bộ lọc.

Tên ngoại lệ ở đây được in có chủ đích, không phụ thuộc thông báo hoặc stack trace của từng JDK. Trong thực tế, InvalidClassException cũng có thể liên quan đến bất tương thích lớp; cần xem nguyên nhân/ngữ cảnh để phân loại.

Các lần ghi lỗi được bỏ đi cùng bộ đệm và luồng của chúng. Không coi dữ liệu của một lần serialize đã thất bại là một bản sao một phần có thể dùng tiếp bình thường.

## 9. Giới hạn an toàn khi dùng serialization để sao chép

Serialization có thể gọi code của lớp trong quá trình đọc. Không nhận một mảng byte tùy ý từ nguồn không tin cậy rồi đưa thẳng vào ObjectInputStream chỉ vì mục tiêu được đặt tên là “sao chép”.

Trong các ví dụ, byte được tạo tại chỗ từ mô hình đã biết. Bộ lọc được đặt trước readObject() và giới hạn kiểu/quy mô theo từng ví dụ. Không biến các hàm minh họa thành tiện ích nhận mọi đối tượng hoặc mọi byte.

### 9.1. Bộ lọc cần đúng ngữ cảnh

ObjectInputFilter có thể xem lớp đang được đọc và các chỉ số như độ sâu, số tham chiếu, độ dài mảng và số byte đã đọc. Một danh sách lớp cho phép cùng giới hạn tài nguyên giúp thu hẹp dữ liệu được chấp nhận.

Bộ lọc của SerializationCopyDemo cho phép cả Object[] vì cách đọc ArrayList của JDK có kiểm tra mảng chứa phần tử. Việc cho phép mảng ấy không cho phép mọi lớp phần tử tùy ý; những đối tượng bên trong vẫn chịu các lượt kiểm tra phù hợp của luồng.

Danh sách này chỉ dành cho đồ thị minh họa gồm Node, Token, enum và ArrayList. Thay đổi mô hình hoặc cách triển khai cấu trúc có thể đòi hỏi xem lại bộ lọc; không mở rộng thành cho phép toàn bộ thư viện chỉ để “hết lỗi”.

Bộ lọc cũng không thay kiểm tra nghiệp vụ, không bảo đảm mọi nội dung String đã được kiểm tra, và các chỉ số được kiểm tra tại các điểm gọi lọc chứ không biến mọi thao tác thành một hạn mức tuyệt đối tổng quát. Khi thiết kế một biên tiếp nhận dữ liệu, còn phải giới hạn dữ liệu đầu vào, quyền của code chạy và các bất biến cần giữ.

Tài liệu [ObjectInputFilter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html) và [Serialization Filtering](https://docs.oracle.com/en/java/javase/17/core/serialization-filtering1.html) trình bày cơ chế và các giới hạn sử dụng. Bộ lọc bổ sung lớp bảo vệ; nó không làm deserialization dữ liệu không tin cậy trở thành lựa chọn mặc định.

### 9.2. Kiểm tra kiểu sau readObject không đủ

Node.class.cast(in.readObject()) xác nhận đối tượng gốc sau khi đọc là Node. Nó không chứng minh mọi bước chạy trước đó an toàn và không kiểm tra toàn bộ ý nghĩa nghiệp vụ của các nút con.

Một chữ ký T extends Serializable hay một phép ép kiểu chỉ là một phần hợp đồng. Hợp đồng sao chép còn phải nêu nguồn được tin cậy, lớp được chấp nhận, giới hạn dữ liệu, bất biến và chính sách đối với tài nguyên.

### 9.3. Byte không phải dữ liệu đã mã hóa bảo mật

Serialization chuyển trạng thái thành biểu diễn byte; nó không tự mã hóa để giữ bí mật. Những trường được ghi có thể chứa thông tin nhạy cảm ngay cả khi code không in chúng.

transient loại một trường khỏi cơ chế mặc định, nhưng cùng thông tin có thể tồn tại ở trường/đối tượng khác hoặc được phương thức tùy biến ghi ra. Không dùng transient như chứng minh rằng mọi bản byte chắc chắn không có dữ liệu nhạy cảm.

Nếu chỉ cần sao chép cục bộ, không cần ghi byte ra tệp để rồi đọc lại; bộ đệm trong bộ nhớ đã minh họa được cơ chế. Việc tạo thêm tệp không giải quyết vấn đề mức độ sâu của bản sao.

## 10. Tương thích lớp và serialVersionUID

Một bản serialize thường chứa thông tin về lớp và trạng thái cần đọc lại. Nó không đóng gói toàn bộ mã thực thi của chương trình thành một bản chạy độc lập.

Nơi đọc vẫn cần các lớp phù hợp và khả năng nạp chúng. Thiếu lớp có thể gây ClassNotFoundException. Thay đổi tên, cấu trúc lớp hoặc hợp đồng đọc có thể khiến dữ liệu cũ không dùng được.

Đối với lớp Serializable thông thường, serialVersionUID tham gia kiểm tra tương thích. Các lớp trong ví dụ khai báo rõ:

```java
// Doan khai bao nam ben trong lop Serializable thong thuong.
private static final long serialVersionUID = 1L;
```

UID khác giữa biểu diễn ghi và lớp đọc có thể gây InvalidClassException. Nếu không khai báo rõ, UID được tính từ các chi tiết của lớp và có thể thay đổi khi lớp được sửa theo cách người viết không chủ ý coi là thay đổi dữ liệu.

Nhưng giữ cùng UID **không tự biến mọi thay đổi thành tương thích**. Nó không chuyển kiểu trường tùy ý, không khôi phục logic cũ và không bảo đảm dữ liệu mới đọc lên thỏa bất biến. Tương thích là hợp đồng thiết kế và kiểm thử, không chỉ là một hằng long.

Enum và record có quy tắc riêng, nên không áp toàn bộ kết luận UID của lớp thông thường sang chúng. Bài này không dùng native serialization làm định dạng trao đổi lâu dài; nếu mục tiêu thật là lưu trữ hoặc liên hệ giữa nhiều hệ thống, cần thiết kế định dạng và phiên bản dữ liệu theo mục tiêu đó.

[Đặc tả versioning](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/version.html) mô tả những thay đổi tương thích và không tương thích. Dùng serialization chỉ để sao chép trong cùng lần chạy có thể tránh một số vấn đề phiên bản, nhưng vẫn không tránh được transient, hook tùy biến, chi phí và các giới hạn đồ thị.

## 11. Chi phí và tài nguyên: “ghi rồi đọc” không phải phép gán rẻ

Clone nông của một collection phải tạo cấu trúc chứa mới và chép các tham chiếu cần thiết. Nó không đi sao chép mọi thuộc tính sâu của từng phần tử.

Sao chép thủ công một đồ thị cần tạo những nút được quy định, đi theo các liên kết và có thể giữ bảng đã gặp. Thuật toán của GraphCopyDemo còn cần bộ nhớ cho bảng IdentityHashMap và ngăn xếp đệ quy.

Serialization thêm bước dựng biểu diễn byte, lưu thông tin giao thức, theo dõi tham chiếu, đọc và dựng lại đối tượng. Trong cách dùng bộ đệm, có thể tồn tại đồng thời nguồn, byte trung gian và đồ thị mới; không giả định bộ nhớ đỉnh chỉ bằng kích thước bản sao.

Không có một con số thời gian chung đúng cho mọi collection hoặc mọi đồ thị. Nếu sao chép nằm trên đường xử lý thường xuyên, đo với dữ liệu đại diện, bao gồm kích thước, phần dùng chung, độ sâu và các phương thức tùy biến. Đừng lấy một ví dụ vài nút rồi kết luận serialization có chi phí tương đương clone.

Các tài nguyên bên ngoài cần xét riêng:

| Loại trạng thái | Câu hỏi phải trả lời |
|---|---|
| Kết nối, socket, file đang mở | Bản sao cần mở mới, dùng chung có kiểm soát hay không được mang theo? |
| Cache | Cần chép, bỏ rồi tính lại hay giữ một cache dùng chung? |
| Khóa đồng bộ, luồng, bộ thực thi | Ý nghĩa một “bản sao” là gì và ai sở hữu vòng đời? |
| ID thực thể nghiệp vụ | Bản sao là ảnh chụp cùng thực thể hay một thực thể mới cần ID khác? |
| Callback, listener, dịch vụ | Có nên nhân bản liên kết đó hay chuyển thành dữ liệu thuần? |
| Comparator hoặc chiến lược xử lý | Dùng chung có an toàn không, trạng thái có ghi/đọc được không? |

Không có clone() hay serialization mặc định nào thay người thiết kế trả lời tất cả câu hỏi trên.

## 12. Chọn cách sao chép theo hợp đồng cần đạt

| Nhu cầu | Hướng phù hợp | Điều còn phải kiểm tra |
|---|---|---|
| Chỉ cần một tên biến khác cho cùng đối tượng | Gán tham chiếu | Hai nơi cùng sửa một đối tượng |
| Tách cấu trúc collection, phần tử có thể dùng chung | Constructor/factory sao chép hoặc clone đã được lớp hỗ trợ | Phần tử/khóa/giá trị vẫn có thể dùng chung |
| Tách dữ liệu mutable của một mô hình mình quản lý | Constructor/factory hoặc hàm sao chép theo mô hình | Quan hệ trùng, chu trình, kiểu con, trường mới |
| Giữ quan hệ đồ thị bằng cơ chế serialization sẵn có | Cặp luồng ghi/đọc mới trong ngữ cảnh tin cậy và kiểm soát | Toàn đồ thị, hook, transient, UID, lọc, chi phí |
| Chỉ cần dữ liệu để hiển thị/truyền đi | Ánh xạ sang DTO/mô hình dữ liệu phù hợp | Những thông tin nào được giữ, bỏ hoặc đổi kiểu |
| Chặn sửa cấu trúc qua tham chiếu trả về | Lớp bọc/factory không cho sửa theo hợp đồng | Nguồn có còn liên kết không; phần tử có bất biến không |

Không buộc lớp nghiệp vụ phải triển khai Serializable chỉ để tránh viết vài dòng sao chép có ý nghĩa. Nếu mô hình đã có giao thức Serializable được kiểm soát và đồ thị không lớn, cách ghi/đọc có thể đáp ứng một mục đích cục bộ; cần chấp nhận những giới hạn đã nêu.

Ngược lại, không tự viết đệ quy tùy tiện rồi gọi là sao chép sâu tổng quát. Hàm thủ công chỉ đúng trong phạm vi những kiểu và chính sách mà nó xử lý.

### 12.1. Constructor sao chép và bất biến

Constructor/factory cho phép quyết định tường minh: tạo danh sách mới, tái sử dụng phần tử bất biến, sao chép giá trị mutable, giữ hoặc cấp ID, và bỏ cache. Với lớp có trường final, cách khởi tạo có chủ đích thường thể hiện hợp đồng rõ hơn việc sửa lại kết quả super.clone().

Một constructor nhận List rồi lưu nguyên List ấy chưa phải bản sao cấu trúc. Tên “copy constructor” cũng cần được code thực tế thực hiện đúng. Nếu phần tử thay đổi được, tạo List mới chỉ giải quyết lớp chứa.

### 12.2. JSON hoặc chuyển đổi dữ liệu không tự thay thế serialization

Ghi JSON rồi đọc lại cũng là đi qua một biểu diễn dữ liệu. Nó có thể là lựa chọn đúng cho một mô hình truyền dữ liệu, nhưng không tự giữ kiểu chạy, Comparator, quan hệ cùng tham chiếu hoặc chu trình như Java Object Serialization của lớp thông thường.

Khả năng giữ những thông tin đó phụ thuộc thư viện và cấu hình cụ thể. Không đưa một bước chuyển JSON vào giữa rồi mặc định đã có sao chép sâu đúng với mọi đồ thị.

### 12.3. Collection không cho sửa không đồng nghĩa sao chép sâu

Một collection không cho sửa cấu trúc vẫn có thể chứa đối tượng mutable. Nếu một phần tử được dùng chung, sửa thuộc tính của nó vẫn ảnh hưởng mọi nơi giữ nó.

“Không sửa được qua API này”, “cấu trúc độc lập với nguồn” và “mọi trạng thái bên trong độc lập” là ba thuộc tính cần kiểm tra riêng. Clone nông chỉ đáp ứng một phần; serialization cũng chỉ đáp ứng theo giao thức lớp, không phải cả ba một cách tự động.

## 13. Kiểm thử một bản sao bằng hành vi và tham chiếu

Chỉ in toString() giống nhau hoặc kiểm tra equals() là chưa đủ. Hai collection có thể hiển thị cùng giá trị nhưng vẫn dùng chung phần tử; hai đối tượng sao chép đúng cũng có thể không equals nếu lớp dùng so sánh danh tính.

Một bộ kiểm tra phù hợp với hợp đồng thường xét:

1. **Gốc:** khi yêu cầu đối tượng mới, copy != source.
2. **Cấu trúc chứa:** thêm/xóa/thay vị trí ở copy có ảnh hưởng cấu trúc nguồn không?
3. **Phần tử mutable:** sửa thuộc tính ở copy có làm nguồn đổi không, và điều đó có đúng chính sách không?
4. **Liên kết trùng:** hai đường cùng đến một nút nguồn còn cùng đến một nút đích không?
5. **Vòng tham chiếu:** đường quay lại trỏ tới nút đích đúng hay lẫn nút nguồn?
6. **Dữ liệu bị bỏ/tính lại:** cache, transient và trạng thái lớp cha có kết quả đã định nghĩa không?
7. **Đối tượng cố ý dùng chung:** enum, singleton hoặc dịch vụ có đúng chính sách không?
8. **Lỗi:** phần tử không được hỗ trợ, giới hạn lọc và dữ liệu không tương thích có thất bại rõ ràng không?

Các chương trình trong bài cố tình kiểm tra cả thay đổi cấu trúc và sửa thuộc tính. Đây là hai loại thay đổi khác nhau, thường là nơi bản sao “có vẻ độc lập” bộc lộ liên kết còn sót.

Với nguồn bị nhiều luồng cập nhật, cả clone, thuật toán tự viết và serialization đều không mặc định tạo một ảnh chụp nguyên tử của toàn bộ đồ thị. Cần cơ chế quyền sở hữu, bất biến hoặc đồng bộ phù hợp trong lúc đọc. Bản sao có thể độc lập sau khi tạo nhưng vẫn đã chụp một trạng thái không nhất quán nếu bước tạo thiếu phối hợp.

## 14. Các lỗi thường gặp

| Nhầm lẫn | Biểu hiện | Cách sửa suy luận |
|---|---|---|
| Cloneable khai báo clone() | Không gọi được qua biến kiểu Cloneable | Đọc API công khai của lớp cụ thể |
| List nào cũng clone được | Lỗi biên dịch hoặc ép kiểu sai | Dùng hợp đồng của interface và constructor/factory phù hợp |
| Clone list là clone từng phần tử | Sửa Box ở bản sao làm nguồn đổi | Kiểm tra lớp chứa và phần tử riêng |
| final làm collection bất biến | Vẫn thêm/xóa được nội dung | final giữ tham chiếu, không khóa nội dung đối tượng |
| transient làm mọi kiểu sao chép bỏ trường | Clone vẫn giữ tham chiếu của trường | Phân biệt quy tắc clone và serialization |
| Đệ quy new cho mọi liên kết | Lặp ở chu trình hoặc phá liên kết trùng | Có bảng nguồn→đích theo danh tính và ghi trước đệ quy |
| Serializable ở gốc đủ cho toàn bộ đồ thị | NotSerializableException khi ghi | Kiểm tra các nút thực tế cần serialize |
| Đọc lại luôn chạy constructor lớp | Mất bước kiểm tra hoặc cache không khởi tạo | Hiểu cơ chế của lớp thông thường và kiểu đặc biệt |
| Serialization luôn tách mọi đối tượng | Singleton hoặc enum vẫn dùng chung | Xét readResolve và giao thức của từng kiểu |
| Ghi lại cùng đối tượng là ảnh chụp mới | Đọc lại cùng đối tượng với trạng thái cũ | Hiểu bảng tham chiếu và vòng đời luồng |
| Giữ UID thì mọi phiên bản đều đọc đúng | Đọc lỗi hoặc trạng thái không hợp lệ | Thiết kế, kiểm thử tương thích dữ liệu |
| Ép kiểu sau đọc là đủ an toàn | Code khác có thể đã chạy trong lúc đọc | Kiểm soát nguồn, lớp, bộ lọc và bất biến |
| Serialization là mã hóa bí mật | Byte vẫn chứa dữ liệu cần bảo vệ | Phân biệt biểu diễn byte với bảo mật dữ liệu |

## 15. Bài tập và lời giải

### Bài 1 — Hai kiểu sửa trên một clone list

List gốc là ArrayList chứa một Box có value = 5. Clone list thành c, gọi c.get(0).value = 8 rồi c.set(0,new Box(9)). Box ở nguồn có value bằng bao nhiêu?

**Lời giải:** bằng 8. Lệnh đầu sửa Box dùng chung; lệnh sau chỉ thay tham chiếu trong c. Nguồn không tự chuyển sang Box(9).

### Bài 2 — transient trong clone và serialization

Một lớp hỗ trợ super.clone() có transient List<String> cache đang trỏ tới một ArrayList. Nếu không có xử lý tùy biến, so sánh cache của bản clone nông với cache của bản deserialization thông thường.

**Lời giải:** clone nông giữ cùng tham chiếu cache với nguồn. Serialization mặc định bỏ trường transient, nên trường cache của lớp Serializable thông thường sau khi đọc không được khôi phục từ nguồn; nếu không có code dựng lại, nó là null. Một biểu thức khởi tạo trường ở lớp serializable không tự được chạy như constructor new trong quá trình đọc đó.

### Bài 3 — Ma trận có hàng dùng chung

int[] row = {1,2}; int[][] matrix = {row,row}; Nếu clone mảng ngoài, hai hàng của bản sao có khác nguồn không? Nếu clone riêng từng hàng, có giữ quan hệ hai vị trí cùng một hàng không?

**Lời giải:** clone mảng ngoài giữ cùng tham chiếu row ở cả hai vị trí, cũng là row của nguồn. Clone mỗi hàng riêng tách dữ liệu khỏi nguồn nhưng tạo hai hàng khác nhau, làm mất quan hệ dùng chung giữa hai vị trí. Nếu cần vừa tách nguồn vừa giữ quan hệ đó, tạo đúng một bản row mới rồi dùng nó cho cả hai vị trí, hoặc dùng thuật toán có bảng theo dõi danh tính.

### Bài 4 — Khi nào đưa nút vào bảng đã gặp?

Trong GraphCopyDemo, có thể chuyển seen.put(source,target) xuống sau vòng lặp sao chép links không?

**Lời giải:** không giữ đúng khả năng xử lý chu trình. Khi child trỏ lại root mà root chưa được đăng ký, hàm tiếp tục tạo/sao chép root thay vì quay về root đích đã có. Cần đưa vào bảng trước khi đi theo liên kết con.

### Bài 5 — equals không thay được danh tính của nút

Hai nút nguồn khác tham chiếu nhưng cùng tên và equals() trả true. Nếu dùng HashMap theo equals để ghi nhớ bản sao thay vì IdentityHashMap, rủi ro là gì?

**Lời giải:** thuật toán có thể coi hai nút nguồn khác nhau là một nút và gộp chúng vào cùng bản đích. Khi mục tiêu là bảo toàn hình dạng đồ thị theo danh tính, phải phân biệt hai tham chiếu ấy ngay cả khi giá trị nghiệp vụ bằng nhau.

### Bài 6 — Collection serializable có phần tử không serializable

Một ArrayList<Object> chứa một new Object(). Gốc ArrayList triển khai Serializable. Có bảo đảm serialize thành công không? Đổi trường chứa list ở một lớp khác thành transient giải quyết điều gì?

**Lời giải:** không, cơ chế có thể gặp phần tử không hỗ trợ và ném NotSerializableException. Đánh dấu trường list transient chỉ bỏ đường ghi đó khỏi trạng thái mặc định của lớp chứa, không sao chép nội dung list; phải định nghĩa trạng thái mong muốn sau khi đọc. Nếu còn một đường tham chiếu khác cần ghi tới cùng đối tượng không hỗ trợ, lỗi vẫn có thể xảy ra.

### Bài 7 — Ghi hai lần trên cùng luồng

Ghi một Box(1), đổi value thành 2 rồi writeObject cùng Box lần nữa trên cùng ObjectOutputStream, không reset. Hai lần readObject tương ứng có bắt buộc trả hai Box khác nhau với giá trị 1 và 2 không?

**Lời giải:** không. Với đối tượng thông thường như ví dụ, lần ghi sau dùng tham chiếu ngược; hai lần đọc trả cùng đối tượng và giữ trạng thái được ghi ở lần đầu, là 1. Muốn ghi trạng thái mới phải thiết kế vòng đời luồng/định danh phù hợp, chẳng hạn lần sao chép dùng cặp luồng mới.

### Bài 8 — Bản sao có singleton

Một trường của đối tượng serializable trỏ đến Config.INSTANCE. Config có readResolve() trả INSTANCE. Sau serialization rồi đọc lại, có thể mặc định sửa Config ở bản đọc không ảnh hưởng Config gốc không?

**Lời giải:** không. Trường có thể lại trỏ tới chính singleton dùng chung. Nếu Config mutable, việc sửa nó ảnh hưởng các nơi giữ cùng INSTANCE. Muốn độc lập phải đổi hợp đồng mô hình, không chỉ đổi tên hàm thành deepCopy.

### Bài 9 — Constructor lớp cha

Child triển khai Serializable, extends Parent không serializable. Parent có constructor không đối số truy cập được, khởi tạo x = 10. Trước khi ghi, child.x = 80. Không có code tùy biến lưu x. Sau đọc, x là gì?

**Lời giải:** là 10 theo khởi tạo của Parent, không phải 80. Trạng thái phần lớp cha không serializable không được tự ghi; constructor lớp cha phù hợp được gọi khi dựng lại. Không suy kết quả này sang Externalizable hoặc record mà bỏ qua quy tắc riêng.

### Bài 10 — Chọn bản sao cho màn hình chỉnh sửa

Một màn hình cần chỉnh sửa danh sách đơn hàng thử nghiệm mà không sửa đơn gốc. Các đơn chứa List<LineItem> mutable và một mã String bất biến. Clone ArrayList ngoài đã đủ chưa? Nêu hợp đồng sao chép tối thiểu.

**Lời giải:** chưa đủ. Cần cấu trúc danh sách riêng, đơn hàng riêng cho phần trạng thái được phép chỉnh, danh sách LineItem riêng và các LineItem mutable riêng theo yêu cầu. Mã String có thể dùng chung. Nếu nhiều đơn cố ý dùng chung một đối tượng bên trong, cần quyết định giữ quan hệ dùng chung trong bản sao hay tách theo nghiệp vụ, rồi kiểm thử điều đó. ID và dịch vụ không được tự sao chép theo cảm giác “cứ sâu hết là đúng”.

## 16. Nguồn đối chiếu chính thức

- [Object.clone và hợp đồng sao chép từng trường](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#clone()).
- [Cloneable và giới hạn của interface đánh dấu](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Cloneable.html).
- [Serializable và các phương thức tùy biến](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serializable.html).
- [Java Object Serialization Specification — kiến trúc, record và chu trình](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/serial-arch.html).
- [ObjectOutputStream — định danh tham chiếu và reset](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectOutputStream.html).
- [Serialization Filtering](https://docs.oracle.com/en/java/javase/17/core/serialization-filtering1.html).


</details>

- [Quay lại đầu trang](#back-to-top)
