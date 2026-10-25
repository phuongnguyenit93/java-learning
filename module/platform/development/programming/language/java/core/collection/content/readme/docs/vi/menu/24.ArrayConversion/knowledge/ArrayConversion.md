<a id="back-to-top"></a>

# Mảng và chuyển đổi

## Menu
- [Arrays.asList và danh sách kích thước cố định dùng chung mảng](#array-conversion-001)
- [Những thay đổi được phép qua set và những thao tác bị từ chối](#array-conversion-002)
- [Bẫy Arrays.asList với mảng kiểu nguyên thủy](#array-conversion-003)
- [toArray(), toArray(T&#91;&#93;) và toArray(IntFunction)](#array-conversion-004)
- [Kiểu thực của mảng, ArrayStoreException và phần tử null kết thúc](#array-conversion-005)
- [Chuyển List sang Set và hệ quả với trùng lặp hoặc thứ tự](#array-conversion-006)
- [Chuyển Map sang danh sách entry, khóa hoặc giá trị](#array-conversion-007)
- [Chọn loại collection đích để bảo toàn hợp đồng cần thiết](#array-conversion-008)

## <a id="array-conversion-001">Arrays.asList và danh sách kích thước cố định dùng chung mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Arrays.asList và danh sách kích thước cố định dùng chung mảng

## 1. Một cách nhìn List vào chính các ô của mảng

Một API đang có mảng String, trong khi một đoạn xử lý khác muốn nhận List. Nếu nhu cầu là thao tác với các phần tử đang nằm trong mảng mà không cần tăng hay giảm số vị trí, `Arrays.asList` cung cấp một cầu nối phù hợp.

Điểm cần hiểu ngay từ đầu là **List trả về dùng chung mảng nền**. Khi truyền một mảng tham chiếu phù hợp vào phương thức, các phần tử không tự được chuyển sang một vùng chứa ArrayList độc lập. List là một khung nhìn (view) cho phép truy cập dữ liệu theo giao diện List.

Có thể hình dung mảng có ba ô: ô 0, ô 1, ô 2. Cách truy cập bằng mảng dùng `array[index]`; cách truy cập qua view dùng `list.get(index)` hoặc `list.set(index, value)`. Chúng vẫn làm việc với các vị trí lưu phần tử của cùng mảng nền.

Hai đặc điểm phải được tách riêng:

- **Kích thước cố định:** List có số vị trí tương ứng với chiều dài mảng, không hỗ trợ thao tác làm số vị trí thay đổi.
- **Phần tử có thể được thay:** việc đặt một tham chiếu khác vào vị trí hiện có vẫn được hỗ trợ nếu đáp ứng ràng buộc kiểu của mảng.

Vì vậy, kích thước cố định không có nghĩa là chỉ đọc, cũng không có nghĩa là bất biến. Sự khác biệt này ảnh hưởng đến quyền sửa, sao chép dữ liệu, việc truyền tham số và cách chọn một List cho bước xử lý tiếp theo.

Bài học bắt đầu bằng quan hệ hai chiều giữa mảng và view, rồi kiểm chứng những thao tác được hỗ trợ. Sau đó mới xét varargs, mảng nguyên thủy, lựa chọn bản sao và ràng buộc kiểu mảng. Người học cần biết tham chiếu, mảng, List, get/set và ngoại lệ cơ bản.

Các chương trình đều độc lập, dùng Java 17; tên tệp, lệnh biên dịch và stdout được ghi kèm từng chương trình. Các ví dụ chỉ dùng thư viện chuẩn Java.

## 2. Hợp đồng của Arrays.asList

Chữ ký phương thức là `static <T> List<T> asList(T... array)`. Cú pháp varargs cho phép truyền một mảng phù hợp hoặc nhiều đối số phần tử; kết quả có giao diện List.

| Thuộc tính | Ý nghĩa khi dùng |
| --- | --- |
| Thứ tự | Theo các vị trí của mảng tham số |
| Kích thước | Cố định bằng số phần tử của mảng tham số |
| Quan hệ với mảng | Thay phần tử ở một bên được nhìn thấy ở bên kia |
| Thay phần tử | Có, trong phạm vi chỉ số và kiểu lưu trữ hợp lệ |
| Thêm/xóa vị trí | Không hỗ trợ |
| Phần tử null | Có thể có trong mảng tham chiếu |
| Chính mảng đầu vào null | Không phải đầu vào hợp lệ |
| Kiểu trả về | List; không cam kết là java.util.ArrayList |

Phương thức không loại trùng. Nếu mảng có cùng một giá trị tại nhiều vị trí, List vẫn có từng vị trí tương ứng. Mảng rỗng hợp lệ tạo List rỗng có kích thước cố định bằng 0.

Các chỉ số dùng trong get/set vẫn từ 0 đến size - 1. “Không cho thay kích thước” không làm chỉ số ngoài phạm vi trở thành hợp lệ. Một List ba phần tử chỉ có các vị trí 0, 1 và 2 để thay phần tử.

Không ép kết quả thành `java.util.ArrayList` để có các khả năng của ArrayList. Hợp đồng được trả ra là List với hành vi đã nêu. Khi cần một ArrayList độc lập, hãy tạo nó bằng hàm khởi tạo nhận Collection.

Tài liệu chính thức: [Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)). Mô tả “dùng chung mảng” là một phần quan trọng của hợp đồng, không phải một chi tiết tình cờ được phát hiện trong công cụ gỡ lỗi.

## 3. Thí nghiệm thay phần tử theo cả hai chiều

Lưu thành `ArrayBackedViewDemo.java`:

```java
import java.util.Arrays;
import java.util.List;

public class ArrayBackedViewDemo {
    public static void main(String[] args) {
        String[] source = {"red", "green", "blue"};
        List<String> view = Arrays.asList(source);

        String old = view.set(1, "lime");
        System.out.println("old=" + old);
        System.out.println("arrayAfterSet=" + Arrays.toString(source));

        source[2] = "navy";
        System.out.println("viewAfterArrayEdit=" + view);
        System.out.println("size=" + view.size());

        source = new String[]{"new"};
        System.out.println("reassignedVariable=" + Arrays.toString(source));
        System.out.println("viewStillOnOriginal=" + view);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ArrayBackedViewDemo.java
java ArrayBackedViewDemo
```

Stdout:

```text
old=green
arrayAfterSet=[red, lime, blue]
viewAfterArrayEdit=[red, lime, navy]
size=3
reassignedVariable=[new]
viewStillOnOriginal=[red, lime, navy]
```

Lời gọi `view.set(1, "lime")` thay ô thứ hai và trả lại phần tử cũ green. Mảng source ngay lập tức có lime ở vị trí ấy. Tiếp theo, việc gán navy vào `source[2]` được quan sát qua view mà không cần gọi lại Arrays.asList.

Hai lần sửa tác động theo hai hướng nhưng có cùng nguyên nhân: mảng và List đang cùng cung cấp đường truy cập đến các vị trí phần tử của mảng nền. Không có bước đồng bộ dữ liệu thủ công nào trong chương trình.

Đoạn cuối gán biến source sang một mảng mới chỉ có một phần tử. View vẫn hiển thị ba phần tử của mảng ban đầu. Phải phân biệt:

| Thao tác | Đối tượng nào thay đổi? |
| --- | --- |
| `source[2] = "navy"` | Một ô trong mảng mà source đang trỏ đến |
| `source = new String[]{"new"}` | Giá trị tham chiếu của riêng biến source |

Gán lại biến không thay đổi mảng cũ và không làm view chuyển sang mảng mới. View đã giữ quan hệ với đối tượng mảng được truyền lúc tạo, không giữ một cơ chế theo dõi biến cục bộ của người gọi.

Nếu mảng ban đầu còn được nhìn thấy qua view, nó vẫn là đối tượng dữ liệu đang được sử dụng dù biến nguồn đã chuyển hướng. Điều này cũng giúp giải thích vì sao tạo view không là một phép sao chép dùng để bảo vệ dữ liệu khỏi mọi tham chiếu bên ngoài.

## 4. Kích thước cố định vẫn cho phép nhiều thao tác sửa

`set` thay phần tử tại một vị trí có sẵn nên không cần đổi chiều dài mảng. Một số thao tác khác cũng chỉ cần thay hoặc sắp lại những tham chiếu trong các vị trí hiện có.

Chẳng hạn, sắp xếp làm thứ tự thay đổi nhưng không tạo thêm vị trí. `List.replaceAll` dùng kết quả của một hàm để thay từng phần tử, cũng không cần tăng kích thước. `ListIterator.set` thay phần tử vừa được bộ duyệt trả về.

Ngược lại, thêm một phần tử mới, xóa một phần tử đang có hoặc xóa toàn bộ một List không rỗng đều đòi thay số vị trí. List từ Arrays.asList không hỗ trợ những yêu cầu này.

Lưu thành `FixedSizeOperationsDemo.java`:

```java
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

public class FixedSizeOperationsDemo {
    public static void main(String[] args) {
        List<String> values = Arrays.asList("C", "A", "B");
        ListIterator<String> cursor = values.listIterator();
        cursor.next();
        cursor.set("D");
        System.out.println("afterIteratorSet=" + values);

        values.sort(Comparator.naturalOrder());
        System.out.println("afterSort=" + values);
        values.replaceAll(value -> value.toLowerCase(Locale.ROOT));
        System.out.println("afterReplaceAll=" + values);

        try {
            values.add("E");
        } catch (UnsupportedOperationException e) {
            System.out.println("add=UnsupportedOperationException");
        }
        try {
            values.remove(0);
        } catch (UnsupportedOperationException e) {
            System.out.println("remove=UnsupportedOperationException");
        }
        try {
            values.clear();
        } catch (UnsupportedOperationException e) {
            System.out.println("clear=UnsupportedOperationException");
        }
        System.out.println("afterAttempts=" + values);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 FixedSizeOperationsDemo.java
java FixedSizeOperationsDemo
```

Stdout:

```text
afterIteratorSet=[D, A, B]
afterSort=[A, B, D]
afterReplaceAll=[a, b, d]
add=UnsupportedOperationException
remove=UnsupportedOperationException
clear=UnsupportedOperationException
afterAttempts=[a, b, d]
```

Bộ duyệt lấy phần tử đầu rồi thay C thành D. Sau đó List được sắp xếp thành A, B, D và chuyển các chuỗi sang chữ thường. Những thao tác này thành công vì chúng chỉ thay nội dung các vị trí đã tồn tại.

Ba ca tiếp theo chủ động yêu cầu đổi kích thước nên đều bị từ chối. Sau các lần thử, nội dung vẫn là a, b, d. Phần bắt ngoại lệ giúp chương trình quan sát nhiều ca, không phải là một chiến lược để API thật đoán quyền sửa bằng cách thử ngẫu nhiên.

Đừng kết luận rằng mọi phương thức có chữ remove luôn cho cùng một kết quả bất kể đối số. Một lời gọi không tìm thấy mục tiêu có thể không cần thay kích thước. Muốn kiểm tra khả năng xóa, chọn một mục thực sự đang tồn tại như chương trình đã làm.

Cũng đừng xem khả năng sắp xếp hoặc thay phần tử là quyền bảo vệ độc lập với nguồn: nếu List đang dựa trên mảng của một thành phần khác, các phép biến đổi ấy có thể sửa chính dữ liệu của thành phần đó.

## 5. Varargs quyết định mảng nào đang làm nền cho List

Chữ ký `asList(T... array)` dùng tham số số lượng biến đổi. Khi truyền vài đối số rời như hai String, Java chuẩn bị một mảng tham số chứa các String đó. View vẫn có kích thước cố định dù người gọi không tự khai báo một biến mảng.

Khi truyền một `String[]` cho lời gọi có T là String, chính mảng String ấy là mảng tham số phù hợp; List có từng String làm phần tử. Nếu ghi rõ T là `String[]`, thì một phần tử lại là cả mảng String, còn mảng varargs ở tầng ngoài cao hơn.

Mảng nguyên thủy tạo thêm một điểm cần phân biệt: `int[]` là một đối tượng mảng nhưng không là `Integer[]`. Java không tự đóng gói cả mảng int thành mảng Integer để tạo List chứa từng số.

Lưu thành `AsListArrayShapesDemo.java`:

```java
import java.util.Arrays;
import java.util.List;

public class AsListArrayShapesDemo {
    public static void main(String[] args) {
        List<String> fromArguments = Arrays.asList("left", "right");
        System.out.println("fromArguments=" + fromArguments);

        String[] words = {"sun", "moon"};
        List<String> wordView = Arrays.asList(words);
        List<String[]> oneArray = Arrays.<String[]>asList(words);
        System.out.println("wordCount=" + wordView.size());
        System.out.println("arrayCount=" + oneArray.size());
        System.out.println("sameArray=" + (oneArray.get(0) == words));

        int[] raw = {10, 20};
        List<int[]> primitiveView = Arrays.asList(raw);
        System.out.println("primitiveListSize=" + primitiveView.size());
        System.out.println("samePrimitiveArray=" + (primitiveView.get(0) == raw));
        raw[0] = 99;
        System.out.println("primitiveElement="
                + Arrays.toString(primitiveView.get(0)));

        Integer[] boxed = {10, 20};
        List<Integer> boxedView = Arrays.asList(boxed);
        System.out.println("boxedListSize=" + boxedView.size());
        System.out.println("boxedView=" + boxedView);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 AsListArrayShapesDemo.java
java AsListArrayShapesDemo
```

Stdout:

```text
fromArguments=[left, right]
wordCount=2
arrayCount=1
sameArray=true
primitiveListSize=1
samePrimitiveArray=true
primitiveElement=[99, 20]
boxedListSize=2
boxedView=[10, 20]
```

Kết quả cần được đọc theo kiểu của **một phần tử List**:

| Biến | Kiểu phần tử | Số phần tử trong ví dụ |
| --- | --- | --- |
| `fromArguments` | String | Hai |
| `wordView` | String | Hai |
| `oneArray` | `String[]` | Một |
| `primitiveView` | `int[]` | Một |
| `boxedView` | Integer | Hai |

Lời gọi `Arrays.<String[]>asList(words)` chủ động giữ words như một phần tử mảng. Dòng `sameArray=true` xác nhận không có bản sao sâu của words được tạo ra.

Đối với raw, List có một phần tử là chính `int[]`, nên `primitiveListSize` bằng một, không bằng hai. Việc sửa raw ở vị trí đầu được nhìn thấy khi đọc mảng lấy từ phần tử List. Mảng Integer ở cuối lại phù hợp với một dãy phần tử Integer, nên List có hai vị trí riêng.

Nếu yêu cầu là `List<Integer>` từ một `int[]`, cần một bước lấy từng giá trị và đóng gói phù hợp. Đừng dùng phép ép kiểu để giả định `int[]` đã là `Integer[]`, và đừng dự đoán size chỉ bằng số dấu ngoặc nhọn trong dữ liệu nguồn.

Khi List chứa mảng, dùng `Arrays.toString` trên mảng phần tử để quan sát các giá trị bên trong. Chuỗi in mặc định của đối tượng mảng không giúp chứng minh rằng các số đã được chuyển thành các phần tử Integer riêng.

## 6. Tạo bản sao khi cần tách phần chứa khỏi mảng

Có ba yêu cầu thường gặp khi bắt đầu từ một view Arrays.asList:

1. Cần List riêng có thể thêm/xóa.
2. Cần ảnh chụp chỉ đọc, không đi theo các lần thay ô mảng về sau.
3. Chỉ cần chặn sửa qua một đường List nhưng vẫn muốn nhìn dữ liệu đang theo nguồn.

Những yêu cầu này khác nhau. Constructor `new ArrayList<>(view)` tạo phần chứa riêng và cho phép đổi kích thước. `List.copyOf(view)` chốt dữ liệu thành List không cho sửa phần chứa, với điều kiện không có null. `Collections.unmodifiableList(view)` chỉ chặn sửa qua lớp bọc; view bên dưới vẫn liên hệ với mảng.

Lưu thành `AsListCopyChoicesDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AsListCopyChoicesDemo {
    public static void main(String[] args) {
        String[] source = {"one", "two"};
        List<String> view = Arrays.asList(source);
        List<String> editableCopy = new ArrayList<>(view);
        List<String> snapshot = List.copyOf(view);
        List<String> readOnlyView = Collections.unmodifiableList(view);

        view.set(0, "changed");
        editableCopy.add("three");
        System.out.println("view=" + view);
        System.out.println("editableCopy=" + editableCopy);
        System.out.println("snapshot=" + snapshot);
        System.out.println("readOnlyView=" + readOnlyView);
        System.out.println("source=" + Arrays.toString(source));

        StringBuilder[] builders = {new StringBuilder("draft")};
        List<StringBuilder> shallowCopy =
                new ArrayList<>(Arrays.asList(builders));
        builders[0].append("-edit");
        System.out.println("sharedElement=" + shallowCopy.get(0));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 AsListCopyChoicesDemo.java
java AsListCopyChoicesDemo
```

Stdout:

```text
view=[changed, two]
editableCopy=[one, two, three]
snapshot=[one, two]
readOnlyView=[changed, two]
source=[changed, two]
sharedElement=draft-edit
```

Các kết quả được tạo trước khi view đổi phần tử đầu. Sau khi đổi:

- View và source cùng có changed ở vị trí đầu.
- EditableCopy vẫn giữ one, two, rồi cho phép bổ sung three vào bản riêng.
- Snapshot vẫn giữ one, two.
- ReadOnlyView hiện changed, two vì lớp bọc đọc xuyên view đang dùng chung mảng.

Khả năng sửa qua một đường truy cập và việc dữ liệu có theo nguồn là hai chiều cần xét độc lập. Không thể dùng một thao tác add bị chặn để chứng minh rằng nguồn không còn thay đổi được nội dung mà người nhận nhìn thấy.

Đối tượng StringBuilder ở cuối chương trình minh họa một giới hạn khác. ArrayList mới tách các vị trí chứa tham chiếu khỏi mảng ban đầu, nhưng vẫn giữ cùng StringBuilder. Nội dung draft-edit được quan sát ở bản sao vì đối tượng phần tử vẫn dùng chung.

Do đó, “bản sao riêng” trong ngữ cảnh này trước hết là bản sao của phần chứa. Nếu cần độc lập cả trạng thái phần tử, hãy chọn phần tử bất biến hoặc một cách chuyển đổi/sao chép phần tử phù hợp với mô hình dữ liệu.

Một cách tạo List vẫn cố định kích thước nhưng tách các ô khỏi mảng nguồn là tạo một mảng sao chép trước rồi lấy view trên mảng mới. Cách đó vẫn không tự sao chép sâu đối tượng phần tử. Không cần đổi mọi yêu cầu thành ArrayList nếu chính yêu cầu là có một dãy ô cố định riêng.

Đối chiếu khả năng thêm/xóa của [ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html) và hợp đồng ảnh chụp của [List.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)) khi lựa chọn kết quả cần dùng.

## 7. Null và kiểu mảng thực tế vẫn là ràng buộc

Arrays.asList nhận được phần tử null trong mảng tham chiếu. Điều đó khác với việc chính mảng truyền vào là null. Cần ghi rõ tầng dữ liệu đang nói đến.

Ngoài ra, mảng Java giữ kiểu thành phần thực tế ở thời điểm chạy. Một biến `Number[]` có thể đang tham chiếu một đối tượng `Integer[]`. Giao diện List được suy ra từ kiểu biến mảng không làm mảng nền mất kiểm tra kiểu khi ghi phần tử.

Lưu thành `AsListTypeConstraintsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AsListTypeConstraintsDemo {
    public static void main(String[] args) {
        List<String> nullable = Arrays.asList("A", null);
        nullable.set(0, null);
        System.out.println("nullable=" + nullable);
        System.out.println("oneNullSize="
                + Arrays.asList((String) null).size());
        try {
            Arrays.asList((String[]) null);
        } catch (NullPointerException e) {
            System.out.println("nullArray=NullPointerException");
        }

        Integer[] storage = {1, 2};
        Number[] wider = storage;
        List<Number> numberView = Arrays.asList(wider);
        numberView.set(0, 3);
        System.out.println("afterInteger=" + Arrays.toString(storage));
        try {
            numberView.set(1, 2.5);
        } catch (ArrayStoreException e) {
            System.out.println("doubleStore=ArrayStoreException");
        }
        System.out.println("afterRejectedStore=" + Arrays.toString(storage));

        List<Number> independent = new ArrayList<>(numberView);
        independent.set(1, 2.5);
        System.out.println("independent=" + independent);
        System.out.println("storage=" + Arrays.toString(storage));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 AsListTypeConstraintsDemo.java
java AsListTypeConstraintsDemo
```

Stdout:

```text
nullable=[null, null]
oneNullSize=1
nullArray=NullPointerException
afterInteger=[3, 2]
doubleStore=ArrayStoreException
afterRejectedStore=[3, 2]
independent=[3, 2.5]
storage=[3, 2]
```

### 7.1. Phần tử null khác mảng null

List nullable có hai vị trí. Vị trí thứ hai đã null, vị trí thứ nhất được set thành null nên kết quả là hai phần tử null. `Arrays.asList((String) null)` tạo một List có một phần tử null.

Trong khi đó, `Arrays.asList((String[]) null)` truyền chính mảng null và gây lỗi. Không dùng null trần rồi mặc định Java sẽ hiểu là “một phần tử null”; viết rõ kiểu khi cần phân biệt ý định.

### 7.2. Kiểu khai báo rộng không đổi kiểu của mảng đã tạo

Storage là một `Integer[]`. Biến wider có kiểu `Number[]` nhưng vẫn giữ cùng đối tượng đó. Đây là một khả năng của hệ thống kiểu mảng, không phải phép chuyển `List<Integer>` thành `List<Number>`.

NumberView được tạo qua biến wider và có kiểu `List<Number>`. Khi thay bằng số 3, giá trị Integer phù hợp với mảng nền nên thành công. Khi thay bằng 2.5, giá trị Double phù hợp với kiểu Number ở lúc biên dịch nhưng không thể được lưu trong `Integer[]` thực tế; phép ghi bị chặn bằng ArrayStoreException.

`ArrayList<Number>` độc lập ở cuối có phần chứa riêng, nên có thể giữ Double và không sửa storage. Chương trình không dùng kiểu thô hay ép kiểu List để tạo khác biệt này.

Điểm cần nhớ là: List từ Arrays.asList vẫn chịu những ràng buộc lưu trữ của mảng nền. Nếu cần một phần chứa Number riêng có khả năng nhận các kiểu con Number khác, hãy tạo phần chứa phù hợp thay vì nghĩ việc khai báo biến rộng đã biến đổi mảng.

Quy tắc kiểm tra khi ghi mảng tham chiếu được giải thích trong [JLS 17, Array Store Exception](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html#jls-10.5).

## 8. Chọn Arrays.asList khi nào?

Arrays.asList hữu ích khi đang có một mảng và muốn làm việc bằng giao diện List mà vẫn chấp nhận dùng chung các vị trí lưu phần tử. Ví dụ, một đoạn xử lý cần đọc theo List hoặc thay các phần tử trong một dãy có độ dài cố định.

Cần xác định trước ba yêu cầu:

1. Có cần thêm/xóa vị trí không?
2. Có muốn sửa qua List ảnh hưởng đến mảng không?
3. Có cần bảo vệ cả trạng thái của đối tượng phần tử không?

| Yêu cầu | Lựa chọn phù hợp về phần chứa | Điểm cần xét thêm |
| --- | --- | --- |
| List dùng chung mảng, không cần đổi số vị trí | `Arrays.asList(array)` | Phép set có thể sửa mảng |
| List riêng có thể thêm/xóa | `new ArrayList<>(Arrays.asList(array))` | Phần tử có thể vẫn dùng chung |
| List chỉ đọc đã chốt từ view | `List.copyOf(Arrays.asList(array))` | Không nhận phần tử null; không sao chép sâu |
| Chặn sửa qua List nhưng vẫn theo dữ liệu mảng | Lớp bọc chỉ đọc trên view | Nguồn mảng còn có thể đổi dữ liệu quan sát được |
| Dãy ô cố định riêng | Sao chép mảng rồi tạo view trên mảng mới | Phần tử đối tượng vẫn cần chính sách riêng |

Nếu phương thức nhận một List rồi định gọi add hoặc remove, việc truyền trực tiếp Arrays.asList có thể không đáp ứng yêu cầu. Không suy từ kiểu khai báo List rằng mọi thao tác tùy chọn đều được hỗ trợ. Hợp đồng phương thức nhận cần nêu rõ quyền sửa mong đợi.

Nếu phương thức định sắp xếp hoặc thay phần tử, view có thể đáp ứng về khả năng thao tác nhưng sẽ tác động lên mảng của bên gọi. Trường hợp chỉ muốn tính ra một kết quả đã biến đổi mà giữ nguồn cũ, nên chuẩn bị phần chứa riêng.

Nếu API trả Arrays.asList của một mảng nội bộ, người nhận có đường set vào mảng ấy. Bản thân từ “kích thước cố định” không đủ để gọi đầu ra là chỉ đọc. Ngược lại, bọc chỉ đọc cũng không hứa dữ liệu cố định nếu chủ sở hữu tiếp tục sửa mảng.

Không cần tạo bản sao trong mọi trường hợp. Chia sẻ dữ liệu có thể chính là mục tiêu của một view. Điều cần làm là công bố đúng quan hệ với nguồn và không hứa mức độc lập mà cách triển khai chưa cung cấp.

Các chương trình trong bài chạy một luồng để quan sát rõ quan hệ dữ liệu. Việc mảng và view dùng chung phần tử không tự tạo một cơ chế phối hợp truy cập khi nhiều luồng cùng đọc/ghi.

## 9. Chẩn đoán lỗi theo đúng tầng dữ liệu

| Tình huống | Nguyên nhân cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| Add hoặc xóa phần tử đang có bị chặn | List cố định số vị trí | Tạo phần chứa có thể thay kích thước nếu cần |
| Set qua List làm mảng nguồn đổi | Đúng hợp đồng dùng chung mảng | Dùng bản sao nếu không muốn sửa nguồn |
| Gán biến mảng sang mảng mới nhưng List cũ không đổi nền | Biến đã đổi tham chiếu, view vẫn giữ mảng cũ | Tạo view mới từ mảng mới nếu đó là mục tiêu |
| `int[]` cho List có một phần tử | Một phần tử có kiểu `int[]` | Chuyển từng số nếu cần `List<Integer>` |
| Gọi với null gây lỗi thay vì có một phần tử null | Null đang được hiểu là mảng tham số | Ghi kiểu rõ để diễn đạt đúng tầng |
| Set Double qua `List<Number>` gây ArrayStoreException | Mảng nền thực tế có kiểu hẹp hơn | Chọn phần chứa độc lập đáp ứng kiểu Number |
| Đã tạo ArrayList mới nhưng đối tượng phần tử vẫn đổi | Sao chép phần chứa còn chia sẻ phần tử | Dùng phần tử bất biến hoặc sao chép/chuyển đổi phù hợp |
| Ép kết quả sang java.util.ArrayList bị lỗi | Kiểu triển khai không được hợp đồng đó hứa | Làm việc qua List hoặc tạo ArrayList thật |

Khi quan sát một lỗi, nên in hoặc kiểm tra cả mảng lẫn List trước–sau thao tác. Để phân biệt view và bản sao, thay một ô mảng sau khi tạo kết quả rồi đọc kết quả. Để phân biệt sao chép nông và độc lập trạng thái, cần kiểm tra việc sửa chính đối tượng phần tử.

Không dựa duy nhất vào tên lớp thấy trong công cụ gỡ lỗi hoặc một phép so sánh định danh. Một List khác đối tượng vẫn có thể là view của cùng mảng. Một ArrayList mới có phần chứa riêng vẫn có thể giữ các đối tượng phần tử dùng chung.

Với thao tác cần thay đổi kích thước, kiểm tra một ca có thay đổi thực sự. Một lời gọi xóa giá trị không tồn tại hoặc xử lý dữ liệu rỗng không phải phép chứng minh chung rằng List cho phép xóa phần tử.

## 10. Thực hành dự đoán và kiểm chứng

### Bài 1: Theo dõi hai đường sửa

Tạo mảng `[north, south]` và một List view. Thay vị trí 0 bằng east qua List, rồi thay vị trí 1 bằng west qua mảng.

Tiêu chí đạt: cả hai cách đọc cùng hiện east, west; kích thước List vẫn bằng hai. Giải thích bằng quan hệ với các ô mảng, không bằng một bước sao chép dữ liệu giữa hai đối tượng.

### Bài 2: Biến tham chiếu và đối tượng mảng

Sau khi tạo view trên mảng hai phần tử, gán biến mảng sang một mảng mới có ba phần tử.

Dự đoán size của view cũ và kiểm chứng. View cũ vẫn có hai vị trí vì nó tiếp tục liên hệ với mảng ban đầu. Muốn nhìn mảng mới qua List, phải tạo kết quả từ mảng mới.

### Bài 3: Chọn quyền sửa đúng

Một phương thức cần thêm một mục vào List nhận được. Người gọi đang có `String[]` và dùng Arrays.asList.

Hãy chọn cách chuẩn bị đầu vào để phương thức thêm được mà không thay số vị trí của mảng gốc. Một ArrayList riêng từ view đáp ứng quyền thêm; cần giải thích rằng đây là bản riêng của phần chứa.

### Bài 4: Từng số hay một mảng số?

Có `int[]` gồm ba số. Hãy dự đoán kiểu phần tử và size khi gọi Arrays.asList trên mảng đó. So sánh với một `Integer[]` cũng gồm ba số.

Tiêu chí đạt: `int[]` tạo một phần tử mảng trong List của lời gọi tương ứng; `Integer[]` tạo các vị trí Integer riêng. Không dùng một phép ép kiểu để coi hai loại mảng là như nhau.

### Bài 5: Chỉ đọc có theo nguồn không?

Từ cùng một view, tạo một lớp bọc chỉ đọc và một List.copyOf với dữ liệu không null. Thay một ô mảng sau khi tạo cả hai.

Dự đoán kết quả: lớp bọc vẫn nhìn thấy giá trị mới qua view; ảnh chụp giữ giá trị đã lấy lúc tạo. Giải thích rằng cả hai đều chặn sửa qua List nhưng quan hệ với nguồn khác nhau.

### Bài 6: Phần tử có trạng thái riêng

Mảng chứa một StringBuilder. Tạo một ArrayList mới từ Arrays.asList của mảng, rồi sửa StringBuilder bằng append.

Tiêu chí đạt: nhận ra phần chứa mới không tự nhân bản builder. Đề xuất chuyển thành String hoặc một kiểu giá trị phù hợp nếu đầu ra cần giữ nội dung văn bản ổn định.

### Bài 7: Kiểu mảng ở thời điểm chạy

Một `Integer[]` được giữ qua biến `Number[]` rồi dùng để tạo `List<Number>`. Thử thay một vị trí bằng Integer và một vị trí bằng Double.

Tiêu chí đạt: phân biệt kiểm tra kiểu ở lời gọi Java với kiểm tra kiểu thành phần mảng khi lưu. So sánh với một `ArrayList<Number>` độc lập để giải thích nguyên nhân của ArrayStoreException.

## 11. Các câu hỏi tự kiểm tra

1. **Arrays.asList có tạo một ArrayList có thể tăng kích thước không?** Không. Nó trả List kích thước cố định dùng chung mảng tham số.
2. **Kích thước cố định có cấm set không?** Không. Vị trí hiện có vẫn thay phần tử được nếu chỉ số và kiểu lưu trữ hợp lệ.
3. **Sửa mảng sau khi tạo view có được thấy ở List không?** Có, khi sửa các ô của chính mảng nền đó.
4. **Gán lại biến mảng có làm view chuyển nguồn không?** Không. View liên hệ với đối tượng mảng cũ, không theo dõi biến.
5. **`int[]` có tự thành `List<Integer>` không?** Không. Cần đọc kiểu varargs và thực hiện bước chuyển từng giá trị nếu đó là yêu cầu.
6. **Mảng null và mảng có phần tử null có giống nhau không?** Không. Một bên thiếu đối tượng mảng hợp lệ, bên kia là dữ liệu phần tử trong mảng.
7. **Tạo ArrayList từ view bảo vệ đến đâu?** Tách phần chứa để có thể thêm/xóa riêng; đối tượng phần tử có thể vẫn được chia sẻ.
8. **List.copyOf và lớp bọc chỉ đọc trên view khác nhau ở đâu?** Một bên chốt dữ liệu phần chứa, một bên tiếp tục đọc qua view đang theo nguồn.
9. **Vì sao `List<Number>` vẫn có thể không lưu được Double trong ví dụ?** Vì mảng nền thực tế là `Integer[]` và vẫn kiểm tra kiểu thành phần khi ghi.
10. **Một lựa chọn API tốt cần xác định gì?** Quyền thay phần tử, quyền thay kích thước, quan hệ với mảng nguồn và mức độc lập của trạng thái phần tử.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="array-conversion-002">Những thay đổi được phép qua set và những thao tác bị từ chối</a>

<details>
<summary>Xem chi tiết</summary>

# Những thay đổi được phép qua set và những thao tác bị từ chối

## 1. Điểm xuất phát: cùng dùng List nhưng quyền thay đổi khác nhau

Trong bài này, đối tượng cần xét là danh sách tạo từ `Arrays.asList` trong nhóm kiến thức **mảng và chuyển đổi**. Một mảng đã có số vị trí cố định. Đưa nó qua `Arrays.asList` giúp dùng giao diện `List` để đọc, tìm kiếm hoặc thay nội dung các vị trí ấy; việc chuyển đổi này không tự tạo ra khả năng thêm và xóa vị trí.

Hãy hình dung một phiếu đăng ký có đúng ba ô tên. Ta có thể sửa tên ở ô thứ hai, đổi chỗ hai tên hoặc viết hoa cả ba tên. Các thao tác ấy vẫn dùng ba ô cũ. Muốn ghi thêm một người thứ tư hoặc bỏ hẳn một ô, ta cần một cấu trúc có quyền thay đổi số ô.

Tài liệu `Arrays.asList` xác định danh sách có kích thước cố định, dùng chung mảng nền; cập nhật qua một phía được nhìn thấy ở phía kia. Các phép làm thay đổi kích thước không được hỗ trợ. [Java SE 17 — Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

Ba câu hỏi phải tách riêng khi đọc một đoạn mã:

1. Có thay được phần tử tại một chỉ số đã tồn tại hay không?
2. Có thay được số phần tử của danh sách hay không?
3. Thao tác có tác động đến một mảng hoặc đối tượng khác đang được dùng chung hay không?

Với `Arrays.asList(array)`, câu trả lời thông thường là: thay vị trí được, đổi kích thước không được, và thay vị trí sẽ tác động tới mảng nền. Việc gọi được một phương thức qua biến kiểu `List` chỉ chứng minh rằng chữ ký phương thức tồn tại; chưa chứng minh rằng đối tượng thực tế hỗ trợ thao tác đó.

Sau bài này, người học cần dự đoán được một lệnh `set` thay đổi gì, giải thích được vì sao `sort` có thể chạy còn `add` bị từ chối, nhận ra lỗi do chỉ số hoặc kiểu mảng, và chọn đúng cách sao chép khi cần một danh sách có thể thêm/xóa.

## 2. set là thay một vị trí đã có

Xét đoạn minh họa bên trong một phương thức; cần nhập `java.util.Arrays` và `java.util.List`:

~~~java
String[] names = {"Lan", "Mai", "An"};
List<String> view = Arrays.asList(names);

String old = view.set(1, "Vy");
~~~

Sau lệnh cuối:

| Thành phần | Giá trị |
| --- | --- |
| `old` | `"Mai"` |
| `view` | `[Lan, Vy, An]` |
| `names[1]` | `"Vy"` |
| `view.size()` | `3` |
| `names.length` | `3` |

Chỉ số bắt đầu từ `0` nên `1` là vị trí thứ hai. `set` trả về phần tử trước đó tại vị trí ấy; nó không trả về danh sách và cũng không trả về giá trị boolean báo thành công. Hợp đồng của `List.set` mô tả thao tác thay thế và điều kiện `0 <= index < size()`. [Java SE 17 — List.set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#set(int,E)).

Điểm quan trọng là **vị trí đã tồn tại trước khi gọi**. Với danh sách ba phần tử, các chỉ số hợp lệ cho `set` là `0`, `1` và `2`. `set(3, value)` không phải cách nối phần tử thứ tư. Chỉ số `3` nằm ngoài vùng có thể thay thế, dù chính số `3` bằng kích thước hiện tại.

`add(index, value)` là yêu cầu khác. Nó chèn thêm một vị trí và đẩy các phần tử phía sau; `size` tăng lên. Vì vậy, `add(1, "Vy")` không thể dùng thay cho `set(1, "Vy")` trên danh sách cố định. Hai câu lệnh có thể giống nhau ở dữ liệu truyền vào nhưng mang ý nghĩa cấu trúc khác nhau.

Cũng không có vùng “chưa dùng” trong danh sách sau:

~~~java
String[] slots = new String[3];
List<String> fixed = Arrays.asList(slots);
~~~

`fixed.size()` đã bằng `3` ngay từ đầu. Ba vị trí đều chứa `null`. Muốn điền vị trí đầu tiên, dùng `fixed.set(0, "Lan")`. Gọi `fixed.add("Lan")` vẫn là yêu cầu tạo vị trí thứ tư và bị từ chối. Giá trị `null` ở một vị trí không có nghĩa rằng vị trí ấy chưa được tính vào kích thước.

Đây cũng là lý do không nên nhầm **kích thước hiện tại** với **dung lượng dự trữ** của `ArrayList`. `new ArrayList<String>(3)` tạo danh sách có kích thước `0`; số `3` chỉ là dung lượng ban đầu. Trong khi đó, `Arrays.asList(new String[3])` đã có ba phần tử. Nếu một thuật toán sẽ gọi `set(0, ...)`, hai cấu trúc này có điều kiện ban đầu khác nhau.

## 3. Phạm vi tác động của set lên mảng nguồn

`Arrays.asList(array)` dùng chung các vị trí của mảng nguồn. Tiếp tục ví dụ `names` và `view` ở trên:

~~~java
names[0] = "Ha";
view.set(2, null);
~~~

Cả mảng lẫn danh sách lúc này đọc được `[Ha, Vy, null]`. Một hàm nhận `List` có thể vô tình sửa mảng của bên gọi thông qua `set`; ngược lại, bên giữ mảng có thể làm dữ liệu mà hàm đang đọc thay đổi. Vì thế, hãy công bố rõ khi hàm sửa đầu vào.

Gán lại biến `names` sang mảng khác không di chuyển view sang mảng mới: view tiếp tục tham chiếu mảng cũ. Quan hệ tham chiếu hai chiều và cách tách bản sao được giải thích đầy đủ trong bài về `Arrays.asList` đứng trước; tại đây, điều cần giữ là **`set` sửa vị trí hiện hữu, kể cả khi không trực tiếp truy cập mảng**.

## 4. Những phép thay đổi được phép khi số vị trí giữ nguyên

`set` là trường hợp trực tiếp nhất. Một số thuật toán thay nhiều vị trí nhưng vẫn giữ kích thước cũng có thể áp dụng lên danh sách của `Arrays.asList`.

| Thao tác | Loại thay đổi | Điều cần nhớ |
| --- | --- | --- |
| `view.set(i, x)` | Thay một vị trí | Chỉ số phải tồn tại, giá trị phải phù hợp kiểu mảng |
| `ListIterator.set(x)` | Thay phần tử vừa được duyệt | Phải thỏa trạng thái của bộ duyệt |
| `view.replaceAll(op)` | Tính lại từng phần tử | Hàm biến đổi có thể ném lỗi |
| `view.sort(comparator)` | Đổi thứ tự phần tử | Các phần tử phải so sánh được theo quy tắc đã chọn |
| `Collections.reverse(view)` | Đảo thứ tự | Mảng nền cũng bị thay thứ tự |
| `Collections.swap(view, i, j)` | Đổi hai vị trí | Hai chỉ số phải hợp lệ |
| `Collections.fill(view, x)` | Ghi cùng giá trị vào các vị trí | Không tạo thêm phần tử |
| `Collections.copy(view, source)` | Ghi đè một đoạn đầu | Đích phải có đủ kích thước trước khi chép |

Các thuật toán thao tác danh sách trong `Collections` công bố yêu cầu riêng đối với khả năng thay thế phần tử. Không thể suy ra rằng mọi đối tượng mang kiểu `List` đều dùng được với chúng. [Java SE 17 — Collections](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html).

Ví dụ minh họa dưới đây cần các import `Arrays`, `Collections`, `List` và `Locale` trong `java.util`:

~~~java
String[] words = {"c", "a", "b"};
List<String> view = Arrays.asList(words);

view.sort(null);
Collections.reverse(view);
view.replaceAll(s -> s.toUpperCase(Locale.ROOT));
Collections.copy(view, List.of("X", "Y"));
~~~

Diễn biến của mảng nền:

~~~text
ban đầu      [c, a, b]
sort         [a, b, c]
reverse      [c, b, a]
replaceAll   [C, B, A]
copy         [X, Y, A]
~~~

`sort(null)` dùng thứ tự tự nhiên của chuỗi. `reverse` đảo các giá trị trong chính các vị trí hiện tại. `replaceAll` viết hoa từng chuỗi bằng `Locale.ROOT` để tránh phụ thuộc thiết lập ngôn ngữ mặc định. `copy` cuối cùng ghi đè hai phần tử đầu, giữ nguyên phần tử thứ ba.

Không thao tác nào trong chuỗi này tạo ra vị trí mới. Tuy nhiên, cả bốn đều là thay đổi dữ liệu. Khi muốn bảo toàn mảng ban đầu để hiển thị hoặc tính toán tiếp, việc “chỉ sắp xếp lại” vẫn có thể làm hỏng giả định của bên gọi.

`Collections.copy` cũng không phải lệnh tự tăng kích thước đích. Nếu đích chỉ có một phần tử mà nguồn có hai phần tử, việc chép không có đủ vị trí để ghi. Một danh sách được tạo với dung lượng dự trữ lớn nhưng `size() == 0` vẫn không đáp ứng yêu cầu này.

`sort` có thể thất bại vì dữ liệu hoặc bộ so sánh, ngay cả khi danh sách cho phép thay vị trí. Chẳng hạn, thứ tự tự nhiên của `String` không xử lý `null` như một chuỗi thông thường; nếu có `null` và thật sự cần sắp xếp, hãy chọn quy tắc rõ ràng, ví dụ bộ so sánh có xử lý `null`. Khả năng sửa danh sách và khả năng so sánh dữ liệu là hai điều kiện khác nhau.

## 5. Những thao tác làm đổi kích thước bị từ chối

Với danh sách cố định không rỗng, yêu cầu thực sự thêm hoặc xóa phần tử sẽ dẫn tới `UnsupportedOperationException`. Lỗi này biểu thị rằng đối tượng không hỗ trợ thao tác được yêu cầu.

| Lệnh điển hình | Vì sao không thực hiện được |
| --- | --- |
| `view.add(x)` | Cần thêm một vị trí ở cuối |
| `view.add(i, x)` | Cần chèn một vị trí |
| `view.addAll(nonEmptySource)` | Cần thêm các vị trí cho nguồn không rỗng |
| `view.remove(i)` với chỉ số hợp lệ | Cần bỏ một vị trí |
| `view.remove(existingValue)` | Cần bỏ vị trí chứa giá trị tìm thấy |
| `view.clear()` khi đang có phần tử | Cần xóa các vị trí hiện có |
| `view.removeIf(predicate)` khi có phần tử cần xóa | Cần xóa vị trí thỏa điều kiện |
| `view.removeAll(values)` khi có phần tử bị loại | Cần giảm số vị trí |
| `view.retainAll(values)` khi có phần tử bị loại | Cần giảm số vị trí |
| `iterator.remove()` sau khi đã duyệt một phần tử | Cần xóa phần tử vừa duyệt |
| `listIterator.add(x)` | Cần chèn một vị trí qua bộ duyệt |

Các ví dụ trong bảng giả định tham số thông thường hợp lệ và thao tác có tác dụng thêm/xóa thật sự. Đừng dùng bảng để đoán thứ tự kiểm tra lỗi khi vừa sai chỉ số, vừa không hỗ trợ thao tác.

Một lỗi thường gặp là dùng `remove` để bỏ các ô `null`:

~~~java
List<String> fixed = Arrays.asList("A", null, "B");
fixed.removeIf(value -> value == null);
~~~

Đây là đoạn minh họa gây lỗi, không phải cách lọc hợp lệ trên danh sách này. `null` là một phần tử đang chiếm vị trí; bỏ nó sẽ làm danh sách từ ba phần tử thành hai. Nếu mục tiêu là giữ ba ô nhưng đánh dấu ô thứ hai là trống, `set(1, null)` diễn tả điều đó. Nếu mục tiêu là tạo danh sách chỉ còn hai phần tử, hãy lọc sang một cấu trúc khác có thể thay kích thước.

Tương tự, thay phần tử thành chuỗi rỗng `""` không xóa vị trí. `set(i, "")` và `remove(i)` có kết quả khác nhau về cả kích thước lẫn ý nghĩa dữ liệu. Chọn một giá trị đánh dấu chỉ phù hợp khi mô hình nghiệp vụ đã định nghĩa giá trị ấy.

Cần đặc biệt chú ý danh sách số nguyên:

~~~java
List<Integer> values = Arrays.asList(10, 20, 30);
values.remove(1);
~~~

Tham số `1` có kiểu `int` nên chọn phép xóa theo chỉ số, tức yêu cầu bỏ phần tử `20`. Dùng `values.remove(Integer.valueOf(20))` chọn phép xóa theo giá trị. Trên danh sách cố định này, cả hai đều không thể hoàn tất khi có phần tử phải xóa. Ép kiểu đúng giúp chọn đúng ý nghĩa của `remove`; nó không bổ sung quyền xóa cho đối tượng.

## 6. Vì sao một lệnh remove hoặc addAll đôi khi không ném lỗi?

Không được dùng một lần gọi không làm thay đổi dữ liệu để kết luận danh sách hỗ trợ thêm/xóa.

Các tình huống điển hình là `remove("không có")`, `addAll` với nguồn rỗng, `removeIf` khi mọi lần kiểm tra đều trả về `false`, hoặc `clear` trên danh sách đang rỗng. Một cách cài đặt có thể kết thúc mà không cần thực hiện bước sửa cấu trúc.

Hợp đồng `Collection` cho phép thao tác tùy chọn không được hỗ trợ vẫn trả về bình thường nếu lời gọi không có tác dụng; nó cũng cho phép ném `UnsupportedOperationException` trong tình huống ấy. Vì thế, không nên viết mã phụ thuộc vào việc “thêm không phần tử chắc chắn không lỗi”. [Java SE 17 — Collection, optional operations](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html).

Ví dụ lập luận:

~~~text
remove("Z") trên [A, B]
    không tìm thấy Z
    có thể kết thúc mà chưa yêu cầu xóa vị trí

remove("A") trên [A, B]
    tìm thấy A
    muốn hoàn tất phải bỏ một vị trí
    danh sách cố định không hỗ trợ bước đó
~~~

Sự khác nhau nằm ở đường thực thi và điều kiện dữ liệu, không phải ở việc danh sách bỗng đổi loại sau lần gọi đầu tiên.

Một hàm lọc như sau sẽ có lỗi phụ thuộc đầu vào:

~~~java
// Đoạn minh họa bên trong phương thức, items được truyền từ bên ngoài.
items.removeIf(item -> item == null);
~~~

Nếu `items` là danh sách cố định và hôm nay không có `null`, hàm có thể chưa bộc lộ lỗi. Ngày mai dữ liệu có một `null`, cùng câu lệnh mới chạm tới bước xóa không được hỗ trợ. Kiểm thử chỉ với dữ liệu không cần xóa dễ bỏ sót lỗi thiết kế.

`Collection.removeIf` còn quy định rằng cách cài đặt mặc định duyệt rồi dùng `Iterator.remove` cho phần tử thỏa điều kiện. Thông tin này giải thích vì sao việc gọi điều kiện lọc và việc thực hiện xóa là hai giai đoạn khác nhau; không nên để điều kiện lọc thực hiện tác dụng phụ rồi dựa vào ngoại lệ để “hủy” chúng.

Giải pháp bền vững là biết trước yêu cầu của thuật toán và quyền thay đổi của đầu vào. Đừng thử một thao tác không có tác dụng để thăm dò khả năng, và đừng bắt mọi `UnsupportedOperationException` rồi bỏ qua như thể yêu cầu đã thành công.

## 7. Thay qua ListIterator và qua subList

### 7.1. ListIterator.set không thêm phần tử

`Iterator` thông thường không có phương thức `set`. `ListIterator` có thể thay phần tử vừa được trả về bởi `next` hoặc `previous` nếu danh sách hỗ trợ thay thế. Nó không tự coi vị trí con trỏ là một phần tử đang được chọn.

Đoạn minh họa:

~~~java
List<String> fixed = Arrays.asList("A", "B");
ListIterator<String> editing = fixed.listIterator();

editing.next();
editing.set("X");
~~~

Kết quả là `[X, B]`. `next` đã trả về `A`, nên `set` thay đúng vị trí của `A`. Nếu gọi `set` ngay sau khi tạo bộ duyệt mà chưa gọi `next` hoặc `previous`, điều kiện trạng thái chưa thỏa. Hợp đồng `ListIterator.set` quy định các trạng thái hợp lệ và `IllegalStateException`. [Java SE 17 — ListIterator.set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ListIterator.html#set(E)).

Hai điều kiện phải đồng thời đúng:

- Bộ duyệt đang có một phần tử vừa được trả về để thay.
- Danh sách phía sau cho phép thay thế phần tử ấy.

Trên `Arrays.asList`, đáp ứng điều kiện trạng thái cho phép `set` hoạt động. Cũng với bộ duyệt đó, `add` cần thêm vị trí nên bị từ chối. `remove` cần bớt vị trí nên cũng không trở thành hợp lệ chỉ vì đã gọi `next`.

Đừng sửa vòng lặp xóa bằng cách chuyển từ `list.remove` sang `iterator.remove` rồi cho rằng đã xử lý xong. Bộ duyệt có thể giải quyết một số yêu cầu về cách duyệt và sửa, nhưng không tự thay đổi hợp đồng thêm/xóa của cấu trúc nền.

### 7.2. subList tiếp tục dùng chung dữ liệu

Với mảng bốn phần tử:

~~~java
String[] days = {"Mon", "Tue", "Wed", "Thu"};
List<String> week = Arrays.asList(days);
List<String> middle = week.subList(1, 3);
middle.set(0, "TUE");
~~~

`middle` chứa các vị trí tương ứng với chỉ số `1` và `2` của `week`. Chỉ số `0` trong danh sách con được ánh xạ về chỉ số `1` trong danh sách gốc. Sau lệnh `set`, mảng thành `[Mon, TUE, Wed, Thu]`.

`subList` là một khung nhìn dùng chung danh sách nền, không phải danh sách độc lập được sao chép ra. Hợp đồng của nó mô tả việc phản ánh thay đổi không cấu trúc giữa hai phía và các thao tác tùy chọn được kế thừa từ danh sách nền. [Java SE 17 — List.subList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#subList(int,int)).

Vì nền là danh sách cố định, `middle.clear()` khi có phần tử vẫn không xóa được. Việc chỉ lấy ra một đoạn không biến đoạn đó thành nơi được phép co giãn. Muốn có một danh sách con độc lập và thêm/xóa được, có thể tạo `new ArrayList<>(week.subList(1, 3))`.

## 8. set được hỗ trợ nhưng vẫn có thể thất bại

`UnsupportedOperationException` chỉ là một nhóm lỗi. Không nên gộp mọi ngoại lệ từ `set` vào kết luận “danh sách không sửa được”.

### 8.1. Sai chỉ số

Với ba phần tử, `set(-1, x)` và `set(3, x)` đều nằm ngoài vùng hợp lệ. Hãy bắt hoặc kiểm tra theo `IndexOutOfBoundsException` khi thực sự cần xử lý lỗi chỉ số; một cách cài đặt có thể dùng lớp con như `ArrayIndexOutOfBoundsException`.

Không nên dùng ngoại lệ để điều khiển vòng lặp thông thường. Điều kiện `i < list.size()` diễn tả ranh giới rõ hơn. Nếu dữ liệu cần nối ở vị trí `size()`, hãy chọn cấu trúc hỗ trợ `add` thay vì cố sửa chỉ số cho `set`.

### 8.2. Kiểu của mảng lúc chạy hẹp hơn kiểu được nhìn thấy qua biến

Đoạn sau biên dịch được:

~~~java
Number[] narrow = new Integer[] {1, 2};
List<Number> numbers = Arrays.asList(narrow);
numbers.set(0, 1.5);
~~~

`1.5` được đóng gói thành `Double`, là một `Number`. Tuy nhiên, đối tượng mảng thật sự là `Integer[]`. Việc lưu một `Double` vào nó vi phạm kiểu thành phần của mảng và ném `ArrayStoreException`.

Đây là hệ quả của kiểm tra lưu phần tử vào mảng lúc chạy. Kiểu tham chiếu rộng hơn không thay đổi kiểu thật của đối tượng mảng. [Java Language Specification 17 — Array Store Exception](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html#jls-10.5).

So sánh hai cách chuẩn bị dữ liệu:

~~~java
// Mảng thật sự là Number[]: có thể chứa Integer hoặc Double.
Number[] broad = new Number[] {1, 2};
List<Number> broadView = Arrays.asList(broad);
broadView.set(0, 1.5);

// Bản sao có cấu trúc riêng: không ghi vào Integer[] ban đầu.
Number[] narrow = new Integer[] {1, 2};
List<Number> detached = new ArrayList<>(Arrays.asList(narrow));
detached.set(0, 1.5);
~~~

Đây là đoạn minh họa, cần các import `Arrays`, `ArrayList` và `List`. Cách thứ nhất chọn đúng loại mảng từ đầu; cách thứ hai chấm dứt việc ghi thay vị trí vào mảng hẹp ban đầu. Chọn theo yêu cầu chia sẻ dữ liệu, không chỉ để làm ngoại lệ biến mất.

### 8.3. null không phải là phép xóa

Mảng tham chiếu `String[]` có thể giữ `null`; thay một vị trí thành `null` trên khung nhìn của nó vẫn giữ nguyên số vị trí. Tuy nhiên, các lệnh sau đó phải xử lý được `null`. Ví dụ `value.toUpperCase(...)` không thể gọi trên một tham chiếu `null`.

Phân biệt giá trị `null` nằm trong mảng với việc bản thân tham chiếu mảng truyền vào `Arrays.asList` là `null`. Trường hợp thứ hai không cung cấp một mảng nền hợp lệ và gây `NullPointerException` khi tạo danh sách. Không nên kiểm tra khả năng chấp nhận phần tử `null` bằng cách truyền một tham chiếu mảng `null`.

## 9. Thay nhiều vị trí không có nghĩa là giao dịch nguyên khối

Một thao tác hợp lệ về kích thước vẫn có thể đang thực hiện dở thì thất bại. `replaceAll` không nên được hiểu là “hoặc thay tất cả, hoặc tự động trả mọi thứ về ban đầu”.

Xét đoạn minh họa:

~~~java
Integer[] amounts = {1, 2, 3};
List<Integer> view = Arrays.asList(amounts);

view.replaceAll(n -> {
    if (n == 2) {
        throw new IllegalStateException("stop at 2");
    }
    return n * 10;
});
~~~

Trong cách cài đặt được chương trình minh chứng bên dưới kiểm tra, phần tử `1` đã đổi thành `10` trước khi hàm gặp `2` và ném lỗi. Mảng lúc ấy là `[10, 2, 3]`. Bắt được ngoại lệ không khôi phục lại giá trị `1`.

Mã nguồn OpenJDK 17 của lớp danh sách nội bộ trong `Arrays` cho thấy việc ghi kết quả `replaceAll` lần lượt vào mảng. Đây là căn cứ cho cách giải thích trạng thái bị thay một phần, không phải lời hứa về tính nguyên tử của mọi cách cài đặt `List`. [OpenJDK 17 — Arrays.java](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/Arrays.java).

Nếu yêu cầu nghiệp vụ là không đụng vào dữ liệu gốc cho đến khi toàn bộ phép tính thành công, có thể tính trên một bản sao trước. Đoạn minh họa cần các import thông thường:

~~~java
List<Integer> working = new ArrayList<>(view);
working.replaceAll(n -> transform(n));
~~~

`transform` ở đây là hàm do ứng dụng định nghĩa, nên đoạn này không phải chương trình độc lập. Trong giai đoạn tính, thay các vị trí của `working` không thay vị trí của `view`. Sau khi kiểm tra thành công, ứng dụng mới quyết định công bố kết quả nào.

Nếu sau đó chép từng vị trí từ `working` về mảng dùng chung, bản thân giai đoạn chép không tự trở thành một giao dịch nguyên khối cho nhiều luồng. Ngoài ra, sao chép tham chiếu sang danh sách mới không ngăn một hàm biến đổi sửa trực tiếp trạng thái của các đối tượng phần tử được dùng chung. Yêu cầu nhất quán cao hơn cần được thiết kế riêng, thay vì suy ra từ tên `replaceAll` hoặc từ việc kích thước không đổi.

## 10. Chọn cấu trúc theo quyền thay đổi cần có

Ba lựa chọn rất dễ bị dùng lẫn:

| Cách tạo | Có thay vị trí bằng set? | Có thêm/xóa? | Quan hệ với mảng nguồn |
| --- | --- | --- | --- |
| `Arrays.asList(array)` | Có, theo chỉ số và kiểu hợp lệ | Không đổi kích thước | Dùng chung các vị trí |
| `new ArrayList<>(Arrays.asList(array))` | Có | Có | Có cấu trúc riêng, ban đầu chép các tham chiếu phần tử |
| `Collections.unmodifiableList(Arrays.asList(array))` | Không qua khung nhìn chỉ đọc | Không qua khung nhìn chỉ đọc | Vẫn nhìn thấy mảng nguồn thay đổi |
| `List.copyOf(Arrays.asList(array))` | Không | Không | Không phản ánh việc thay vị trí ở mảng nguồn; không chấp nhận phần tử null |

Đối với `ArrayList` thông thường, việc có cấu trúc riêng cho phép thêm/xóa và thay vị trí; hàm tạo từ `Collection` là lựa chọn rõ ràng khi cần danh sách làm việc. [Java SE 17 — ArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html).

Đối với `Collections.unmodifiableList`, giới hạn nằm trên đường truy cập qua khung nhìn. Nếu mảng nền còn được bên khác nắm giữ, nó vẫn có thể thay đổi. Không nên coi lớp bọc này là một bản chụp độc lập. [Java SE 17 — Collections.unmodifiableList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)).

`List.of` và `List.copyOf` tạo danh sách không cho phép thay phần tử và không chấp nhận `null`. Đây là hợp đồng khác với danh sách cố định của `Arrays.asList`. [Java SE 17 — Unmodifiable Lists](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#unmodifiable).

Một bản sao cấu trúc cũng chưa phải bản sao sâu. Xét:

~~~java
StringBuilder item = new StringBuilder("seed");
List<StringBuilder> view = Arrays.asList(item);
List<StringBuilder> copy = new ArrayList<>(view);

copy.get(0).append("!");
~~~

Hai danh sách có các vị trí riêng, nhưng cả hai vị trí số `0` vẫn tham chiếu cùng một `StringBuilder`. Đọc `view.get(0)` sẽ thấy `seed!`. Ngược lại, `copy.set(0, new StringBuilder("other"))` chỉ thay vị trí bên `copy`.

Khi cần chấm dứt mọi ảnh hưởng từ thay đổi đối tượng phần tử, phải quyết định cách sao chép từng đối tượng hoặc dùng kiểu phần tử bất biến phù hợp. Không có một phép chuyển từ mảng sang danh sách chung nào tự hiểu quy tắc sao chép sâu của mọi kiểu nghiệp vụ.

### Mảng lấy từ toArray là một trường hợp khác

`Arrays.asList(array)` dùng mảng được đưa vào làm nền; điều đó không có nghĩa mọi phép đổi qua lại giữa mảng và danh sách đều dùng chung chính các vị trí ấy.

Với `String[] snapshot = view.toArray(String[]::new)`, mảng kết quả mới có các vị trí riêng. Thay `snapshot[0]` không ghi vào `view`. Tuy nhiên, nếu phần tử là đối tượng có trạng thái sửa được, các tham chiếu bên trong vẫn có thể cùng trỏ tới một đối tượng.

Hợp đồng `Collection.toArray` là căn cứ để chọn phép chuyển đổi phù hợp với mảng kết quả và kiểu thành phần cần dùng. [Java SE 17 — Collection.toArray](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#toArray(java.util.function.IntFunction)).

## 11. set với một List chứa mảng nguyên thủy

```java
int[] raw = {1, 2};
List<int[]> groups = Arrays.asList(raw);
groups.set(0, new int[] {8, 9});
```

Đây là đoạn minh họa trong phương thức có import `Arrays` và `List`. `groups` chỉ có **một phần tử kiểu `int[]`**, nên `set(0, ...)` thay tham chiếu mảng tại vị trí đó, không sửa hay gán lại biến `raw`. Kích thước `groups` vẫn là `1`. Cách chuyển từng `int` thành `Integer`, kể cả nguồn rỗng và null, được triển khai riêng ở bài kế tiếp.

## 12. Chương trình minh chứng đầy đủ

Lưu toàn bộ mã sau thành **ArrayListChangesDemo.java**. Chương trình dùng API tương thích Java 17 và không cần thư viện bên ngoài. Tên lớp ở đây là tên ví dụ; danh sách cố định từ `Arrays.asList` không phải đối tượng `java.util.ArrayList` có thể co giãn.

Hàm `rejects` kiểm tra ngoại lệ nhận được có thuộc kiểu kỳ vọng hay không. Nó in tên **kiểu kỳ vọng**, nên dòng `IndexOutOfBoundsException` vẫn đúng khi ngoại lệ cụ thể là một lớp con. Nếu thao tác không thất bại hoặc thất bại bằng loại ngoại lệ khác, chương trình dừng bằng lỗi thay vì âm thầm coi đó là kết quả hợp lệ.

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

public class ArrayListChangesDemo {
    private static void rejects(
            String label,
            Class<? extends RuntimeException> expected,
            Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " did not fail");
        } catch (RuntimeException ex) {
            if (!expected.isInstance(ex)) {
                throw ex;
            }
            System.out.println(label + " -> " + expected.getSimpleName());
        }
    }

    public static void main(String[] args) {
        String[] names = {"Lan", "Mai", "An"};
        List<String> view = Arrays.asList(names);
        System.out.println("old=" + view.set(1, "Vy"));
        names[0] = "Ha";
        System.out.println("shared=" + view + " / " + Arrays.toString(names));
        System.out.println("size=" + view.size());
        view.set(2, null);
        System.out.println("null=" + Arrays.toString(names));

        String[] words = {"c", "a", "b"};
        List<String> changes = Arrays.asList(words);
        changes.sort(null);
        System.out.println("sort=" + Arrays.toString(words));
        Collections.reverse(changes);
        System.out.println("reverse=" + Arrays.toString(words));
        changes.replaceAll(s -> s.toUpperCase(Locale.ROOT));
        System.out.println("replaceAll=" + Arrays.toString(words));
        Collections.copy(changes, List.of("X", "Y"));
        System.out.println("copy=" + Arrays.toString(words));

        List<String> blocked = Arrays.asList("A", "B");
        rejects("add", UnsupportedOperationException.class, () -> blocked.add("C"));
        rejects("remove-index", UnsupportedOperationException.class, () -> blocked.remove(0));
        rejects("remove-value", UnsupportedOperationException.class, () -> blocked.remove("A"));
        rejects("clear", UnsupportedOperationException.class, blocked::clear);
        rejects("removeIf", UnsupportedOperationException.class,
                () -> blocked.removeIf(s -> s.equals("A")));
        Iterator<String> iterator = blocked.iterator();
        iterator.next();
        rejects("iterator-remove", UnsupportedOperationException.class, iterator::remove);
        System.out.println("after-rejection=" + blocked);

        ListIterator<String> editing = blocked.listIterator();
        editing.next();
        editing.set("X");
        System.out.println("iterator-set=" + blocked);
        rejects("iterator-add", UnsupportedOperationException.class, () -> editing.add("Y"));

        String[] days = {"Mon", "Tue", "Wed", "Thu"};
        List<String> week = Arrays.asList(days);
        List<String> middle = week.subList(1, 3);
        middle.set(0, "TUE");
        System.out.println("subList=" + Arrays.toString(days));
        rejects("subList-clear", UnsupportedOperationException.class, middle::clear);

        rejects("set-at-size", IndexOutOfBoundsException.class,
                () -> view.set(view.size(), "Out"));
        Number[] narrow = new Integer[] {1, 2};
        List<Number> numbers = Arrays.asList(narrow);
        rejects("runtime-array-type", ArrayStoreException.class, () -> numbers.set(0, 1.5));
        System.out.println("narrow=" + Arrays.toString(narrow));

        Integer[] amounts = {1, 2, 3};
        List<Integer> amountView = Arrays.asList(amounts);
        rejects("operator-failure", IllegalStateException.class,
                () -> amountView.replaceAll(n -> {
                    if (n == 2) {
                        throw new IllegalStateException("stop at 2");
                    }
                    return n * 10;
                }));
        System.out.println("partial=" + Arrays.toString(amounts));

        String[] source = {"A", "B"};
        List<String> fixed = Arrays.asList(source);
        List<String> editable = new ArrayList<>(fixed);
        editable.set(0, "C");
        editable.add("D");
        System.out.println("editable=" + editable + " / source=" + Arrays.toString(source));
        String[] snapshot = fixed.toArray(String[]::new);
        snapshot[0] = "Z";
        System.out.println("snapshot=" + Arrays.toString(snapshot) + " / fixed=" + fixed);
        List<String> readOnly = Collections.unmodifiableList(fixed);
        rejects("readOnly-set", UnsupportedOperationException.class, () -> readOnly.set(0, "X"));
        source[0] = "Q";
        System.out.println("readOnly-shared=" + readOnly);
        rejects("List.of-set", UnsupportedOperationException.class,
                () -> List.of("A", "B").set(0, "X"));

        StringBuilder item = new StringBuilder("seed");
        List<StringBuilder> itemView = Arrays.asList(item);
        List<StringBuilder> itemCopy = new ArrayList<>(itemView);
        itemCopy.get(0).append("!");
        System.out.println("shared-object=" + itemView.get(0));

        int[] raw = {1, 2};
        List<int[]> groups = Arrays.asList(raw);
        groups.get(0)[0] = 7;
        System.out.println("primitive-size=" + groups.size() + " / raw=" + Arrays.toString(raw));
        groups.set(0, new int[] {8, 9});
        System.out.println("primitive-set=" + Arrays.toString(groups.get(0))
                + " / raw=" + Arrays.toString(raw));
    }
}
~~~

Biên dịch và chạy tại thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 ArrayListChangesDemo.java
java ArrayListChangesDemo
~~~

Stdout chính xác:

~~~text
old=Mai
shared=[Ha, Vy, An] / [Ha, Vy, An]
size=3
null=[Ha, Vy, null]
sort=[a, b, c]
reverse=[c, b, a]
replaceAll=[C, B, A]
copy=[X, Y, A]
add -> UnsupportedOperationException
remove-index -> UnsupportedOperationException
remove-value -> UnsupportedOperationException
clear -> UnsupportedOperationException
removeIf -> UnsupportedOperationException
iterator-remove -> UnsupportedOperationException
after-rejection=[A, B]
iterator-set=[X, B]
iterator-add -> UnsupportedOperationException
subList=[Mon, TUE, Wed, Thu]
subList-clear -> UnsupportedOperationException
set-at-size -> IndexOutOfBoundsException
runtime-array-type -> ArrayStoreException
narrow=[1, 2]
operator-failure -> IllegalStateException
partial=[10, 2, 3]
editable=[C, B, D] / source=[A, B]
snapshot=[Z, B] / fixed=[A, B]
readOnly-set -> UnsupportedOperationException
readOnly-shared=[Q, B]
List.of-set -> UnsupportedOperationException
shared-object=seed!
primitive-size=1 / raw=[7, 2]
primitive-set=[8, 9] / raw=[7, 2]
~~~

Mã đã được biên dịch với `--release 17` và chạy kiểm tra trên JDK 24.0.2. Cờ biên dịch kiểm tra mức ngôn ngữ/API Java 17; nó không có nghĩa chương trình đã được chạy trên JVM 17. Dòng `partial` là quan sát từ cách cài đặt đã chạy, không phải cam kết một thuật toán phải để lại đúng trạng thái ấy trên mọi loại `List`.

Đọc kết quả theo từng nhóm:

- `old`, `shared`, `size` và `null` thể hiện thay vị trí, giá trị trả về và việc chia sẻ mảng.
- `sort`, `reverse`, `replaceAll` và `copy` cho thấy thay nhiều vị trí vẫn không cần đổi kích thước.
- Các dòng `UnsupportedOperationException` chứng minh các yêu cầu thêm/xóa được thử với dữ liệu thật sự cần thay đổi cấu trúc.
- `iterator-set` và `subList` cho thấy đường truy cập khác vẫn có thể sửa các vị trí của cùng dữ liệu nền.
- `set-at-size` và `runtime-array-type` tách lỗi chỉ số khỏi lỗi kiểu thành phần mảng.
- `partial` chỉ ra giới hạn khi phép biến đổi thất bại giữa chừng.
- `editable`, `snapshot` và `readOnly-shared` phân biệt bản sao cấu trúc, mảng kết quả riêng và khung nhìn chỉ đọc còn dùng chung dữ liệu.
- `shared-object` và hai dòng `primitive` giúp nhận ra tầng đối tượng phần tử đang bị thay.

Chương trình chạy tuần tự. Các kết quả trên không chứng minh tính an toàn khi nhiều luồng cùng truy cập và sửa dữ liệu.

## 13. Áp dụng trong thiết kế hàm

Giả sử một hàm có nhiệm vụ chuẩn hóa từng tên nhưng phải giữ nguyên số cột của bản ghi. Nhận một danh sách cố định và dùng `set` hoặc `replaceAll` có thể phù hợp, nếu hợp đồng của hàm công bố rõ rằng nó sửa đầu vào và cho phép các giá trị hợp lệ.

Nếu hàm phải bỏ tên rỗng, gộp các phần tử trùng hoặc thêm nhãn tổng kết, yêu cầu số phần tử đã thay đổi. Một bản sao `ArrayList` trước khi xử lý sẽ làm quyền sửa cấu trúc trở nên rõ ràng hơn:

~~~java
// Đoạn minh họa bên trong phương thức.
// incoming là List<String> do bên gọi cung cấp.
List<String> result = new ArrayList<>(incoming);
result.removeIf(name -> name == null || name.isBlank());
result.add("TOTAL");
~~~

Đoạn này không sửa các vị trí của `incoming`. Nó tạo kết quả riêng cho thuật toán cần lọc và nối thêm. Quyết định xem nhãn `TOTAL` có thuộc cùng mô hình dữ liệu hay không là việc của thiết kế ứng dụng; ví dụ chỉ minh họa sự khác biệt về quyền thay đổi.

Khi viết một hàm tổng quát nhận `List<T>`, hãy xác định một trong các hợp đồng:

| Mục đích của hàm | Cách công bố/phân chia trách nhiệm |
| --- | --- |
| Chỉ đọc | Không gọi thao tác sửa và không giả định đầu vào bất biến |
| Sửa giá trị tại chỗ | Nêu rõ yêu cầu đầu vào hỗ trợ thay thế và việc dữ liệu dùng chung có thể bị đổi |
| Thêm/xóa tại chỗ | Nêu rõ yêu cầu đầu vào hỗ trợ thay đổi kích thước |
| Trả kết quả mới | Tạo cấu trúc kết quả phù hợp; nói rõ mức sao chép phần tử |

Không cần ép kiểu sang `ArrayList` để thử lấy quyền sửa. Một cách cài đặt khác cũng có thể hỗ trợ những thao tác cần thiết. Ngược lại, đối tượng do `Arrays.asList` trả về không phải `java.util.ArrayList` thông thường; ép kiểu như vậy không chuyển đổi cấu trúc và có thể gây `ClassCastException`.

Cũng không nên dựa vào tên lớp nội bộ được in ra lúc gỡ lỗi. Các tên như lớp lồng bên trong `Arrays` là chi tiết cài đặt. Quyền thao tác cần được xác định từ cách tạo danh sách và hợp đồng API mà mã đang dùng.

## 14. Thực hành có đáp án

### Bài 1 — Điền các ô đã có

Một hàm nhận `Arrays.asList(new String[2])` và cần ghi `"A"`, `"B"` vào hai ô. Nên dùng `add` hay `set`? Kích thước cuối cùng là bao nhiêu?

**Đáp án:** Dùng `set(0, "A")` rồi `set(1, "B")`. Danh sách vốn đã có hai vị trí chứa `null`, nên kích thước giữ nguyên bằng `2`. `add` yêu cầu tạo thêm vị trí và không đáp ứng hợp đồng của danh sách này.

### Bài 2 — Sắp xếp có sửa mảng không?

Có `String[] data = {"b", "a"}` và `List<String> view = Arrays.asList(data)`. Sau `view.sort(null)`, `data[0]` là gì? Nếu mảng gốc cần giữ nguyên, cần thay đổi cách chuẩn bị dữ liệu như thế nào?

**Đáp án:** `data[0]` là `"a"`. Để không thay thứ tự các vị trí của mảng nguồn, sắp xếp trên một cấu trúc riêng, chẳng hạn `new ArrayList<>(view)`. Việc đổi thuật toán từ `sort` sang `Collections.sort` không tự giải quyết vấn đề dùng chung dữ liệu.

### Bài 3 — Không có lỗi có chứng minh xóa được không?

Một lần gọi `fixed.remove("Z")` trả về `false` với dữ liệu `[A, B]`. Có thể suy ra `fixed.remove("A")` cũng dùng được không?

**Đáp án:** Không. Lần đầu không có phần tử cần xóa nên có thể chưa thực hiện bước sửa cấu trúc. Trên danh sách cố định, lần thứ hai cần bỏ một vị trí và bị từ chối. Hợp đồng thao tác tùy chọn cũng không bảo đảm mọi danh sách đều trả `false` cho lần gọi không có tác dụng ấy.

### Bài 4 — Sửa lỗi set ở cuối

Với `List<String> fixed = Arrays.asList("A", "B")`, `fixed.set(fixed.size(), "C")` gây lỗi. Đổi chỉ số thành `fixed.size() - 1` có phải cách thêm `"C"` không?

**Đáp án:** Không. Chỉ số mới làm lệnh trở thành thay `"B"` bằng `"C"`, kết quả có hai phần tử. Nếu cần giữ cả `A`, `B` và thêm `C`, tạo danh sách hỗ trợ tăng kích thước rồi dùng `add`.

### Bài 5 — Vì sao đúng kiểu Number vẫn lỗi?

`Number[] data = new Integer[] {4, 5}` được bọc thành `List<Number>`. Tại sao `set(0, 2.5)` không hợp lệ lúc chạy?

**Đáp án:** `Double` phù hợp với `Number` nhưng không thể lưu vào đối tượng mảng thật sự có kiểu `Integer[]`. Lỗi là `ArrayStoreException`. Tạo mảng thật sự kiểu `Number[]` hoặc một bản sao danh sách có cấu trúc riêng tùy nhu cầu; không giải thích lỗi này thành việc mọi `set` đều bị cấm.

### Bài 6 — Bản sao đã tách mọi thay đổi chưa?

`copy = new ArrayList<>(Arrays.asList(builder))` với một `StringBuilder`. Sau `copy.get(0).append("!")`, đối tượng `builder` bên ngoài có thay đổi không? Nếu dùng `copy.set(0, new StringBuilder("X"))` thì sao?

**Đáp án:** Lệnh `append` sửa chính đối tượng đang được dùng chung, nên `builder` thay đổi. Lệnh `set` thay vị trí trong `copy` bằng một tham chiếu khác, không gán lại biến `builder` hoặc vị trí của danh sách nguồn. Cần phân biệt sửa đối tượng phần tử và thay tham chiếu nằm ở một vị trí.

### Bài 7 — Khung nhìn chỉ đọc có phải ảnh chụp không?

Tạo `readOnly = Collections.unmodifiableList(Arrays.asList(array))`, rồi bên giữ mảng gán `array[0] = "Q"`. `readOnly.get(0)` có thấy `"Q"` không? `readOnly.set(0, "X")` có hợp lệ không?

**Đáp án:** Có nhìn thấy `"Q"` vì vẫn dùng chung dữ liệu nền. Nhưng thay qua `readOnly.set` bị từ chối. Nếu cần ảnh chụp các vị trí không phản ánh thay đổi nguồn, hãy chọn cách sao chép; nếu còn yêu cầu không cho sửa kết quả, áp dụng thêm hợp đồng chỉ đọc phù hợp.

### Bài 8 — Kiểu phần tử quyết định ý nghĩa của set

Với `int[] raw = {1, 2}` và `List<int[]> groups = Arrays.asList(raw)`, `groups.size()` bằng bao nhiêu? Sau `groups.set(0, new int[] {3, 4})`, biến `raw` có trỏ sang mảng mới không?

**Đáp án:** Kích thước là `1` vì phần tử duy nhất là tham chiếu tới một `int[]`. `set` thay tham chiếu đó trong danh sách; biến `raw` không bị gán lại. Để có danh sách các số nguyên riêng lẻ, phải chuyển từng số sang phần tử `Integer` theo một cách tạo danh sách thích hợp.

## 15. Các điểm cần giữ đúng khi dùng Arrays.asList

Trước một thao tác sửa, hãy xác định phần tử nào đang được nhắm tới, vị trí ấy đã tồn tại chưa, kiểu mảng thật sự có chứa được giá trị mới không, và thuật toán có cần tăng/giảm số vị trí không. Sau đó xét những nơi cùng dùng mảng hoặc đối tượng phần tử.

Mô hình cốt lõi của bài là: **`set` thay nội dung một vị trí hiện có; thêm/xóa thay số vị trí; `Arrays.asList` chỉ cung cấp nhóm quyền thứ nhất trên mảng nền.** Từ mô hình đó có thể giải thích các ví dụ sắp xếp, bộ duyệt, danh sách con, lỗi kiểu và cách tạo bản sao mà không phải học thuộc từng ngoại lệ rời rạc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="array-conversion-003">Bẫy Arrays.asList với mảng kiểu nguyên thủy</a>

<details>
<summary>Xem chi tiết</summary>

# Bẫy Arrays.asList với mảng kiểu nguyên thủy

## 1. Khi ba số lại tạo một List có một phần tử

Một mảng `int[]` chứa ba số thường khiến người đọc dự đoán rằng `Arrays.asList(array)` sẽ tạo `List<Integer>` có ba phần tử. Thực tế, lời gọi thông thường với mảng đó tạo List chứa **một phần tử là cả mảng int**.

Điểm cần phân biệt là đơn vị dữ liệu:

- Một giá trị `int` có thể được đóng gói thành Integer trong ngữ cảnh phù hợp.
- Một `int[]` là một đối tượng mảng, không tự biến thành `Integer[]`.
- Một List có phần tử kiểu `int[]` chứa các mảng số, không chứa từng số nguyên ở tầng ngoài.

Bẫy này có thể xuất hiện dưới nhiều dấu hiệu: size bằng một, tìm một số bằng contains không được, gán kết quả sang `List<Integer>` không biên dịch, hoặc tạo ArrayList mới rồi vẫn chỉ có một mảng bên trong.

Để sửa đúng, cần xác định yêu cầu trước. Nếu muốn quản lý nhiều lô số, List chứa mảng có thể đúng mô hình. Nếu muốn một List các số để tìm kiếm, thêm/xóa từng số hoặc truyền cho API nhận Integer, phải thực hiện quá trình chuyển từng giá trị.

Bài này giải thích cơ chế varargs và đóng gói, kiểm chứng kiểu phần tử thực tế, rồi đưa ra các cách chuyển phù hợp. Các ví dụ chạy được dùng Java 17. Một ví dụ riêng được ghi rõ là ca lỗi biên dịch chủ đích để kiểm tra hiểu biết về kiểu.

## 2. Vì sao cơ chế đóng gói không tự đi vào từng ô mảng?

Chữ ký của Arrays.asList là `<T> List<T> asList(T... elements)`. Tham số cuối là varargs, được xử lý như một mảng `T[]` trong phương thức. T là kiểu tham chiếu, không thể là kiểu nguyên thủy int.

Với `Integer[]`, mảng có thể trực tiếp cung cấp các phần tử Integer cho tham số mảng. Với `int[]`, không có phép chuyển tự động cả mảng thành `Integer[]`. Bản thân `int[]` vẫn là một đối tượng có kiểu tham chiếu, nên nó có thể trở thành một phần tử varargs.

Trong ví dụ khai báo `List<int[]> result = Arrays.asList(source)`, có thể đọc hình dạng kiểu như sau:

| Tầng | Kiểu hoặc ý nghĩa |
| --- | --- |
| Một số trong nguồn | int |
| Đối tượng nguồn | `int[]` |
| Một phần tử List kết quả | `int[]` |
| T của lời gọi trong ví dụ | `int[]` |
| Mảng tham số varargs được chuẩn bị | `int[][]`, có một phần tử là source |
| Số phần tử của List | Một |

Mảng nguồn có bao nhiêu số không quyết định size của List ở tầng ngoài. Điều quyết định là số phần tử trong mảng varargs mà lời gọi này thực sự cung cấp.

Đóng gói tự động (autoboxing) xử lý một giá trị nguyên thủy trong ngữ cảnh phù hợp. Quy tắc đó không là một vòng lặp ngầm để tạo mảng kiểu bao. Có thể đối chiếu [JLS 17, Boxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.7) và [quy tắc chuẩn bị đối số varargs](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12.4.2).

## 3. Thí nghiệm kiểu phần tử, tìm kiếm, mảng rỗng và null

Lưu thành `PrimitiveAsListTrapDemo.java`:

```java
import java.util.Arrays;
import java.util.List;

public class PrimitiveAsListTrapDemo {
    public static void main(String[] args) {
        int[] source = {4, 8, 15};
        List<int[]> result = Arrays.asList(source);
        System.out.println("sourceLength=" + source.length);
        System.out.println("listSize=" + result.size());
        System.out.println("elementType=" + result.get(0).getClass().getTypeName());
        System.out.println("sameArray=" + (result.get(0) == source));
        System.out.println("elementValues=" + Arrays.toString(result.get(0)));
        System.out.println("containsEight=" + result.contains(8));
        System.out.println("containsSource=" + result.contains(source));
        System.out.println("containsEqualNumbers="
                + result.contains(new int[]{4, 8, 15}));
        System.out.println("nestedDisplay=" + Arrays.deepToString(result.toArray()));

        System.out.println("emptyPrimitiveListSize="
                + Arrays.asList(new int[0]).size());
        System.out.println("emptyWrapperListSize="
                + Arrays.asList(new Integer[0]).size());
        int[] missing = null;
        List<int[]> withMissing = Arrays.asList(missing);
        System.out.println("missingListSize=" + withMissing.size());
        System.out.println("missingElementIsNull=" + (withMissing.get(0) == null));
        try {
            Arrays.asList((Integer[]) null);
        } catch (NullPointerException e) {
            System.out.println("nullWrapperArray=NullPointerException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 PrimitiveAsListTrapDemo.java
java PrimitiveAsListTrapDemo
```

Stdout:

```text
sourceLength=3
listSize=1
elementType=int[]
sameArray=true
elementValues=[4, 8, 15]
containsEight=false
containsSource=true
containsEqualNumbers=false
nestedDisplay=[[4, 8, 15]]
emptyPrimitiveListSize=1
emptyWrapperListSize=0
missingListSize=1
missingElementIsNull=true
nullWrapperArray=NullPointerException
```

`sourceLength=3` đếm số ô int trong nguồn. `listSize=1` đếm phần tử của List bên ngoài. Hai con số khác nhau vì đang đếm ở hai tầng dữ liệu.

Dòng `elementType=int[]` và phép so sánh tham chiếu cho thấy phần tử List là chính mảng nguồn. Dùng Arrays.toString trên phần tử ấy mới đọc được các số bên trong.

### 3.1. Vì sao contains(8) trả false?

Contains tìm một phần tử bằng đối số theo hợp đồng List. Ở tầng List này, phần tử là một `int[]`, không phải Integer có giá trị 8. Vì thế việc tìm số 8 không tìm sâu vào mảng.

Tìm chính source thì được vì List đang giữ tham chiếu đó. Tạo một `int[]` khác có cùng ba số không làm nó bằng source theo equals mặc định của mảng; do đó ca chứa cùng các số nhưng là một mảng khác vẫn trả false.

Nếu yêu cầu là so sánh nội dung hai mảng nguyên thủy, cần dùng phép so sánh mảng phù hợp như Arrays.equals, thay vì suy từ việc các số bên trong giống nhau rằng các đối tượng mảng bằng nhau theo equals.

Dòng nestedDisplay dùng Arrays.deepToString để nhìn rõ một tầng List ngoài và một mảng số bên trong. Không dùng chuỗi in mặc định có mã nhận diện của mảng để kết luận đã có từng Integer riêng.

### 3.2. Mảng nguyên thủy rỗng vẫn là một phần tử

`new int[0]` là một đối tượng mảng hợp lệ, có độ dài bằng 0. Khi nó được dùng làm một phần tử varargs, List ngoài vẫn có size bằng một.

Ngược lại, `new Integer[0]` có thể trực tiếp làm mảng tham số Integer của lời gọi tương ứng, nên List có size bằng 0. Đây là một cách kiểm tra rất rõ sự khác biệt giữa số ô trong nguồn và số phần tử ở tầng List.

### 3.3. Null có kiểu int[] và null có kiểu Integer[]

Biến missing trong ví dụ có kiểu `int[]` và giá trị null. Lời gọi tạo một List có một phần tử null ở vị trí dành cho `int[]`. Mảng varargs ngoài vẫn tồn tại và có một ô.

Ép null sang `Integer[]` lại truyền một mảng tham số null cho lời gọi đang nhận các phần tử Integer, nên gây NullPointerException. Muốn hiểu một ca null, phải xác định null nằm ở **tầng mảng tham số** hay **tầng phần tử**.

Không lấy một trường hợp null này để suy ra mọi lời gọi Arrays.asList với null đều có cùng kết quả. Kiểu của biểu thức tại nơi gọi ảnh hưởng đến cách varargs được áp dụng.

Hợp đồng mảng tham số và kích thước cố định được mô tả tại [Arrays.asList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)).

## 4. Trình biên dịch giúp phát hiện yêu cầu sai kiểu

Tệp dưới đây là **ca lỗi biên dịch chủ đích**, không phải chương trình để chạy bằng java. Lưu thành `RejectPrimitiveAsIntegerDemo.java` nếu muốn kiểm tra việc trình biên dịch từ chối phép gán.

```java
import java.util.Arrays;
import java.util.List;

public class RejectPrimitiveAsIntegerDemo {
    public static void main(String[] args) {
        int[] source = {4, 8, 15};
        List<Integer> values = Arrays.asList(source);
    }
}
```

Chỉ biên dịch để kiểm tra lỗi chủ đích:

```shell
javac --release 17 -encoding UTF-8 RejectPrimitiveAsIntegerDemo.java
```

Trình biên dịch từ chối vì không thể vừa đáp ứng phần tử Integer ở vế nhận vừa coi `int[]` đầu vào là các phần tử Integer. Kết quả của lời gọi không có quá trình tự chuyển từng số như yêu cầu của biến values.

Không có stdout của chương trình này vì bước biên dịch không thành công. Nội dung chi tiết của thông báo có thể khác giữa môi trường biên dịch; điều cần kiểm chứng là phép gán không hợp lệ.

Đổi biến nhận thành `List<int[]>` làm đúng kiểu của kết quả trong ví dụ, nhưng chưa đáp ứng yêu cầu “mỗi số là một phần tử”. Muốn đạt yêu cầu đó, cần đổi quá trình chuyển dữ liệu, không chỉ làm dòng mã biên dịch bằng cách đổi hoặc xóa thông tin kiểu.

## 5. Chuyển từng giá trị khi cần List<Integer>

Có thể sửa đúng hình dạng dữ liệu bằng một vòng lặp: lấy từng int từ nguồn rồi thêm vào một `List<Integer>`. Mỗi lần thêm áp dụng việc đóng gói phù hợp cho một giá trị.

Với stream, `Arrays.stream(intArray)` cung cấp IntStream, tức dãy các giá trị int của mảng. Gọi `boxed()` tạo dãy Integer tương ứng. Sau đó chọn cách thu kết quả theo quyền sửa cần có.

Lưu thành `PrimitiveBoxingChoicesDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PrimitiveBoxingChoicesDemo {
    public static void main(String[] args) {
        int[] source = {4, 8, 15};
        List<Integer> manual = new ArrayList<>();
        for (int value : source) {
            manual.add(value);
        }

        List<Integer> snapshot = Arrays.stream(source).boxed().toList();
        ArrayList<Integer> editable = Arrays.stream(source)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        List<Integer> fromScalars = Arrays.asList(4, 8, 15);
        Integer[] wrappers = {4, 8, 15};
        List<Integer> fromWrapperArray = Arrays.asList(wrappers);

        source[0] = 99;
        manual.add(16);
        editable.add(23);
        System.out.println("source=" + Arrays.toString(source));
        System.out.println("manual=" + manual);
        System.out.println("snapshot=" + snapshot);
        System.out.println("editable=" + editable);
        System.out.println("fromScalars=" + fromScalars);
        System.out.println("fromWrapperArray=" + fromWrapperArray);
        System.out.println("containsEight=" + snapshot.contains(8));
        try {
            snapshot.add(42);
        } catch (UnsupportedOperationException e) {
            System.out.println("snapshotAdd=UnsupportedOperationException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 PrimitiveBoxingChoicesDemo.java
java PrimitiveBoxingChoicesDemo
```

Stdout:

```text
source=[99, 8, 15]
manual=[4, 8, 15, 16]
snapshot=[4, 8, 15]
editable=[4, 8, 15, 23]
fromScalars=[4, 8, 15]
fromWrapperArray=[4, 8, 15]
containsEight=true
snapshotAdd=UnsupportedOperationException
```

Ba kết quả đầu được tạo bằng việc lấy từng giá trị từ source trước khi source đổi phần tử đầu thành 99:

| Kết quả | Cách tạo | Quyền sửa phần chứa |
| --- | --- | --- |
| `manual` | Vòng lặp thêm từng int vào ArrayList | Có thể thêm/xóa/thay phần tử |
| `snapshot` | Arrays.stream rồi boxed rồi Stream.toList | Không cho sửa List |
| `editable` | Stream và bộ thu tạo ArrayList cụ thể | Có thể thêm/xóa/thay phần tử |

Dữ liệu đã thu không theo lần gán 99 vào ô mảng về sau. Kết quả chứa các giá trị Integer được lấy lúc xử lý, không giữ source như một phần tử mảng. Vì đã có từng Integer ở tầng List, `contains(8)` trong snapshot trả true.

Manual được thêm 16 và editable được thêm 23 để kiểm chứng khả năng thay kích thước. Snapshot không cho thêm, nên ca cuối ném UnsupportedOperationException. Chuyển đúng kiểu phần tử và chọn đúng quyền sửa là hai quyết định cần làm cùng nhau.

`Stream.toList()` có từ Java 16 và trả List không cho sửa phần chứa. Các ví dụ này dùng Java 17. Nếu cần một ArrayList cụ thể, `Collectors.toCollection(ArrayList::new)` công bố rõ lựa chọn đó. Không dựa vào tên `Collectors.toList()` để mặc định một kiểu triển khai hay quyền sửa mà hợp đồng của nó không hứa.

Hai kết quả fromScalars và fromWrapperArray là các ca so sánh được chuẩn bị riêng trong chương trình. Truyền ba đối số int rời cho Arrays.asList cho phép Java đóng gói từng đối số. Truyền `Integer[]` phù hợp cho phép lấy từng Integer trong mảng. Hai khai báo này không là một phép ép kiểu biến `int[]` thành `Integer[]`.

List tạo bằng Arrays.asList trong hai ca so sánh vẫn có kích thước cố định. Việc nó chứa đúng Integer không tự làm nó trở thành ArrayList có thể thêm/xóa. Với `Integer[]`, nó còn là view dùng chung các ô của mảng ấy.

Các hợp đồng dùng ở đây: [IntStream.boxed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/IntStream.html#boxed()), [Stream.toList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList()) và [Collectors.toCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#toCollection(java.util.function.Supplier)).

## 6. Những cách thay lớp ngoài vẫn giữ một mảng ở bên trong

Một cách sửa thường thấy là bọc kết quả bằng `new ArrayList<>(...)`. Cách đó thay khả năng sửa của List ngoài nhưng không tự mở mảng thành từng số.

Tương tự, `List.of(source)` có thể tạo List chỉ đọc chứa một `int[]`, còn sao chép source trước khi gọi Arrays.asList có thể tạo một mảng độc lập nhưng vẫn giữ mảng ấy như một phần tử.

Lưu thành `PrimitiveOuterListDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrimitiveOuterListDemo {
    public static void main(String[] args) {
        int[] source = {2, 5};
        List<int[]> editableOuter = new ArrayList<>(Arrays.asList(source));
        List<int[]> readOnlyOuter = List.of(source);
        List<int[]> copiedArray = Arrays.asList(source.clone());

        source[0] = 9;
        System.out.println("editableOuterSize=" + editableOuter.size());
        System.out.println("readOnlyOuterSize=" + readOnlyOuter.size());
        System.out.println("editableElement="
                + Arrays.toString(editableOuter.get(0)));
        System.out.println("readOnlyElement="
                + Arrays.toString(readOnlyOuter.get(0)));
        System.out.println("copiedElement="
                + Arrays.toString(copiedArray.get(0)));

        editableOuter.add(new int[]{7, 8});
        System.out.println("afterAddingArray=" + editableOuter.size());
        readOnlyOuter.get(0)[1] = 11;
        System.out.println("sourceAfterElementEdit=" + Arrays.toString(source));
        try {
            readOnlyOuter.set(0, new int[]{0});
        } catch (UnsupportedOperationException e) {
            System.out.println("outerSet=UnsupportedOperationException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 PrimitiveOuterListDemo.java
java PrimitiveOuterListDemo
```

Stdout:

```text
editableOuterSize=1
readOnlyOuterSize=1
editableElement=[9, 5]
readOnlyElement=[9, 5]
copiedElement=[2, 5]
afterAddingArray=2
sourceAfterElementEdit=[9, 11]
outerSet=UnsupportedOperationException
```

EditableOuter bắt đầu có size bằng một. Thêm một mảng `{7, 8}` làm size thành hai: bây giờ có hai phần tử mảng, không có bốn số Integer ở tầng List. Đây là kết quả đúng nếu dữ liệu cần biểu diễn là các lô số.

ReadOnlyOuter cũng có một phần tử là source. Không thể set một phần tử List khác vào vị trí 0, nhưng vẫn có thể sửa ô số trong `int[]` đã lấy ra. Dòng sourceAfterElementEdit cho thấy sửa phần tử mảng qua đường đó đã tác động lên source.

CopiedArray chứa bản sao mảng với các ô số riêng nên giữ `[2, 5]` sau khi source đổi thành `[9, 5]`. Tuy vậy, List ngoài vẫn chỉ có một phần tử mảng. Việc tách trạng thái nguồn và việc đổi tầng dữ liệu là hai bài toán khác nhau.

| Thao tác chuẩn bị | Có thành List các Integer không? | Điều thật sự thay đổi |
| --- | --- | --- |
| Arrays.asList trên `int[]` | Không | Tạo List chứa mảng |
| ArrayList mới từ List ấy | Không | Phần chứa ngoài có thể thêm/xóa |
| List.of với `int[]` như một phần tử | Không | Phần chứa ngoài chỉ đọc |
| Sao chép `int[]` rồi tạo List chứa nó | Không | Mảng phần tử có các ô số riêng |
| Lấy từng int và đóng gói | Có | Mỗi số trở thành phần tử Integer |

Nếu yêu cầu là danh sách số, không dùng việc List ngoài đã có thể add làm bằng chứng rằng bẫy ban đầu đã được sửa. Kiểm tra cả size và kiểu của một phần tử.

Ngược lại, nếu mục tiêu là giữ một bộ sưu tập các mảng, không cần cố chuyển mọi thứ thành Integer. Hãy dùng kiểu `List<int[]>` có chủ đích và mô tả rõ việc mảng phần tử được chia sẻ hay được sao chép.

## 7. Bẫy cũng áp dụng cho các mảng nguyên thủy khác

Cùng nguyên tắc xảy ra với `byte[]`, `short[]`, `long[]`, `float[]`, `double[]`, `char[]` và `boolean[]`. Mỗi mảng ấy là một đối tượng có thể làm phần tử tham chiếu trong một List.

Không suy rằng vì Arrays.stream có một số cách nhận mảng nguyên thủy nên mọi kiểu nguyên thủy đều có cùng một lệnh stream trực tiếp. Trong Java 17, các phương thức chuyên biệt cho mảng nguyên thủy của Arrays.stream nhận `int[]`, `long[]` và `double[]`.

Với những kiểu còn lại, vòng lặp lấy từng giá trị là một cách rõ ràng, không cần giả định một chữ ký không tồn tại. Kiểu phần tử List cần dùng là kiểu bao tương ứng.

| Mảng nguồn | Phần tử trong List nếu truyền cả mảng cho asList | List các giá trị nếu chuyển từng ô |
| --- | --- | --- |
| `byte[]` | `byte[]` | Byte |
| `short[]` | `short[]` | Short |
| `int[]` | `int[]` | Integer |
| `long[]` | `long[]` | Long |
| `float[]` | `float[]` | Float |
| `double[]` | `double[]` | Double |
| `char[]` | `char[]` | Character |
| `boolean[]` | `boolean[]` | Boolean |

Lưu thành `OtherPrimitiveArraysDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OtherPrimitiveArraysDemo {
    public static void main(String[] args) {
        boolean[] flags = {true, false};
        char[] letters = {'a', 'b'};
        float[] weights = {1.5f, 2.5f};
        System.out.println("booleanArrayListSize=" + Arrays.asList(flags).size());
        System.out.println("charArrayListSize=" + Arrays.asList(letters).size());
        System.out.println("floatArrayListSize=" + Arrays.asList(weights).size());

        List<Character> characters = new ArrayList<>();
        for (char value : letters) {
            characters.add(value);
        }
        List<Boolean> booleans = new ArrayList<>();
        for (boolean value : flags) {
            booleans.add(value);
        }
        System.out.println("characters=" + characters);
        System.out.println("booleans=" + booleans);
        System.out.println("longs="
                + Arrays.stream(new long[]{3L, 6L}).boxed().toList());
        System.out.println("doubles="
                + Arrays.stream(new double[]{1.5, 2.5}).boxed().toList());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 OtherPrimitiveArraysDemo.java
java OtherPrimitiveArraysDemo
```

Stdout:

```text
booleanArrayListSize=1
charArrayListSize=1
floatArrayListSize=1
characters=[a, b]
booleans=[true, false]
longs=[3, 6]
doubles=[1.5, 2.5]
```

Ba phép gọi asList đều cho size bằng một, bất kể mảng chứa hai giá trị. Các vòng lặp lại tạo List chứa từng Character hoặc Boolean, đúng với yêu cầu lấy giá trị ở từng ô.

Hai dòng long và double dùng những phương thức nhận mảng tương ứng của Arrays.stream, rồi đóng gói từng giá trị. Chúng không chứng minh rằng mọi mảng nguyên thủy đều tự chuyển theo cùng một chữ ký generic.

Xem các chữ ký được công bố của [Arrays.stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#stream(int%5B%5D)) khi chọn cách chuyển. Nếu chưa cần phép xử lý theo stream, một vòng lặp ngắn có thể thể hiện quá trình chuyển rất rõ.

## 8. Cách kiểm tra một kết quả chuyển đổi

Trước khi chọn cách sửa, hãy trả lời ba câu hỏi theo đúng thứ tự.

**Một phần tử của kết quả phải là gì?** Nếu câu trả lời là Integer, kiểm tra kiểu khai báo `List<Integer>` và cách tạo từng Integer. Nếu câu trả lời là một lô int, kiểu `List<int[]>` có thể là chủ đích đúng.

**Có bao nhiêu phần tử ở tầng ngoài?** Không dùng chiều dài của mảng phần tử để thay cho size của List. Đặc biệt, một `int[]` rỗng vẫn có thể nằm trong một List có một phần tử.

**Những đối tượng nào còn được chia sẻ?** List chứa mảng thường có thể giữ chính mảng nguồn. List chứa các Integer đã lấy từ từng ô là một hình dạng khác; việc nguồn thay ô số về sau không thay các giá trị đã thu.

| Dấu hiệu | Cách đọc đúng | Hướng xử lý khi cần từng số |
| --- | --- | --- |
| Size bằng một dù nguồn có nhiều số | List chứa một mảng | Duyệt từng giá trị hoặc dùng stream nguyên thủy thích hợp |
| Contains một số trả false | Tìm ở tầng List đang chứa mảng | Tạo `List<Integer>` hoặc tìm trực tiếp trong nguồn theo mục tiêu |
| Gán `List<Integer>` không biên dịch | Kiểu phần tử mong muốn khác kiểu từ lời gọi | Sửa quá trình chuyển đổi |
| Bọc bằng ArrayList vẫn có size một | Chỉ thay phần chứa ngoài | Lấy từng ô nguồn trước khi thêm |
| Clone mảng rồi asList vẫn có size một | Mảng đã riêng nhưng vẫn là một phần tử | Phân biệt độc lập trạng thái với đổi hình dạng dữ liệu |
| List chỉ đọc nhưng số trong mảng vẫn đổi được | Chỉ chặn sửa tầng List | Chọn ảnh chụp các giá trị hoặc sao chép mảng theo hợp đồng |
| Đổi sang var khiến không thấy lỗi gán | Kiểu được suy ra vẫn theo lời gọi | Kiểm tra kiểu thực tế và khai báo rõ ý định ở ranh giới API |

Không dùng kiểu thô hoặc ép kiểu để che việc T không đáp ứng yêu cầu. Các thao tác đó không chạy một vòng lặp chuyển int thành Integer. Một lỗi kiểu được phát hiện sớm thường giúp tìm đúng nguyên nhân hơn một kết quả bị hiểu sai sau khi đã truyền qua nhiều lớp.

Đối với dữ liệu null, trước hết quyết định chính sách của API. Các cách chuyển bằng vòng lặp hoặc Arrays.stream trong ví dụ giả định nguồn hợp lệ, không phải cam kết rằng mảng null tự chuyển thành List rỗng. List có một phần tử null của ca varargs cũng không đồng nghĩa với “không có số nào”.

Việc chuyển từng giá trị cần thời gian và bộ nhớ theo lượng dữ liệu kết quả. Nếu thuật toán chỉ cần xử lý số nguyên mà không cần giao diện `List<Integer>`, có thể giữ mảng nguyên thủy hoặc một luồng nguyên thủy phù hợp. Không cần thêm tầng kiểu bao chỉ để làm cho một lời gọi asList trông hợp lệ.

## 9. Thực hành có mục tiêu dữ liệu rõ ràng

### Bài 1: Ba số, một mảng

Tạo `int[]` có ba số và lấy kết quả Arrays.asList với kiểu `List<int[]>`. Dự đoán size, kiểu phần tử đầu và việc so sánh tham chiếu với nguồn.

Tiêu chí đạt: size bằng một, phần tử đầu là `int[]` và cùng tham chiếu với nguồn. Đếm ba ô trong mảng không đồng nghĩa có ba phần tử ở tầng List.

### Bài 2: Từng số là một phần tử

Từ cùng `int[]`, tạo ArrayList Integer bằng vòng lặp và một List bằng Arrays.stream rồi boxed.

Kiểm tra size bằng số ô nguồn, tìm được một số bằng contains, và việc thay một ô nguồn sau khi thu không đổi dữ liệu đã thu. Xác định thêm quyền add/remove của từng List theo cách tạo đã chọn.

### Bài 3: Bản sao ngoài chưa đổi kiểu phần tử

Tạo ArrayList từ Arrays.asList của `int[]`. Sau đó thêm một `int[]` khác vào List.

Tiêu chí đạt: List có hai mảng, không phải tập hợp các Integer của cả hai mảng. Giải thích được tại sao tạo phần chứa mới không tự trải các phần tử lồng.

### Bài 4: Nguồn rỗng

So sánh Arrays.asList của `int[]` rỗng với kết quả vòng lặp chuyển từng int từ mảng rỗng sang `List<Integer>`.

Một kết quả có một phần tử mảng; kết quả còn lại không có Integer nào. Hãy nêu rõ mình đang đếm ở tầng nào khi kiểm tra dữ liệu rỗng.

### Bài 5: Null có kiểu

Kiểm tra biến `int[]` mang null và biểu thức null được ép thành `Integer[]`. Trước khi chạy, mô tả mảng varargs ngoài trong từng trường hợp.

Tiêu chí đạt: giải thích được một bên có một phần tử null, một bên không có mảng tham số hợp lệ. Không thay lời giải bằng quy tắc chung “null luôn giống nhau”.

### Bài 6: Đổi kiểu nguyên thủy

Chọn `char[]` hoặc `boolean[]` và chuyển từng giá trị vào List kiểu bao tương ứng. So sánh size với việc gọi Arrays.asList trực tiếp trên cả mảng.

Tiêu chí đạt: chọn Character hoặc Boolean đúng với dữ liệu; không giả định Arrays.stream có cùng chữ ký trực tiếp cho mọi loại mảng nguyên thủy.

## 10. Các câu hỏi tự kiểm tra

1. **Vì sao asList của `int[]` có thể biên dịch?** Vì chính `int[]` là một đối tượng có kiểu tham chiếu có thể làm một phần tử generic.
2. **Vì sao nó không tự thành `List<Integer>`?** Không có phép đóng gói tự động toàn bộ `int[]` thành `Integer[]` hoặc một vòng lặp chuyển từng ô trong lời gọi đó.
3. **Một `int[]` rỗng tạo size ngoài bằng mấy?** Bằng một trong lời gọi đang coi mảng là một phần tử.
4. **Tìm một số bằng contains có tìm sâu vào mảng không?** Không. Contains làm việc với các phần tử của List ở tầng hiện tại.
5. **Cùng các số có làm hai mảng bằng nhau theo equals mặc định không?** Không. Dùng phép so sánh nội dung mảng phù hợp nếu đó là yêu cầu.
6. **Bọc ArrayList có sửa được hình dạng sai không?** Không. Nó có thể làm List ngoài thay kích thước được nhưng vẫn giữ phần tử mảng.
7. **Clone mảng có làm từng int thành Integer không?** Không. Nó tạo các ô số riêng cho một mảng khác.
8. **Cách chuyển sang `List<Integer>` rõ ràng là gì?** Lấy từng int rồi thêm vào List, hoặc dùng Arrays.stream trên `int[]` rồi boxed và thu kết quả.
9. **Stream.toList có cho thêm/xóa không?** Không. Trong Java 17, kết quả được công bố là List không cho sửa phần chứa.
10. **Khi nào `List<int[]>` là đúng?** Khi mỗi phần tử được chủ đích mô hình hóa là một mảng hoặc một lô số, với chính sách chia sẻ/sao chép rõ ràng.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="array-conversion-004">toArray(), toArray(T&#91;&#93;) và toArray(IntFunction)</a>

<details>
<summary>Xem chi tiết</summary>

# toArray(), toArray(T[]) và toArray(IntFunction)

## 1. Từ collection sang mảng: phải chọn cả kiểu lẫn nơi chứa dữ liệu

Một thư viện có thể trả về `List<String>`, trong khi API tiếp theo lại nhận `String[]`. Cả hai đều biểu diễn nhiều phần tử, nhưng hợp đồng của chúng khác nhau: collection quản lý số lượng phần tử theo cách triển khai của nó; một đối tượng mảng có độ dài cố định và có kiểu thành phần được kiểm tra lúc chạy.

Các phương thức `toArray` là cầu nối theo chiều **collection → mảng**. Chúng đưa các phần tử vào mảng kết quả, không chuyển chuỗi thành số, không sắp xếp dữ liệu và không tự sao chép sâu các đối tượng phần tử.

Trước khi chọn cách gọi, cần trả lời ba câu hỏi:

1. API nhận kết quả cần `Object[]`, `String[]`, `Number[]` hay một kiểu mảng khác?
2. Có muốn cung cấp một mảng đã cấp phát để tái sử dụng không?
3. Có cần mảng kết quả chỉ có đúng số ô chứa phần tử, hay chấp nhận một bộ đệm lớn hơn?

Hai khái niệm đặc biệt quan trọng:

- **Kiểu khai báo của biến:** kiểu mà trình biên dịch nhìn thấy, chẳng hạn `Object[]`.
- **Kiểu thành phần lúc chạy** (runtime component type): loại đối tượng mà mảng thực tế có thể chứa. Một biến `Object[]` vẫn có thể đang trỏ tới đối tượng `String[]`.

Đổi kiểu khai báo hoặc ép kiểu một tham chiếu không tạo lại đối tượng mảng với kiểu thành phần mới. Đây là nguồn gốc của nhiều lỗi khi dùng `toArray`.

Bài học lần lượt xét kiểu mảng, cách tái sử dụng bộ đệm, hàm tạo mảng, rồi đến quyền sở hữu và chia sẻ tham chiếu của dữ liệu kết quả.

## 2. Ba dạng gọi và cách chọn

Các chữ ký dưới đây thuộc `Collection<E>`; đây là khai báo API rút gọn, không phải chương trình chạy độc lập:

```java
Object[] toArray();
<T> T[] toArray(T[] array);
default <T> T[] toArray(IntFunction<T[]> generator);
```

Dạng dùng `IntFunction` có từ Java 11. Các chương trình trong bài dùng API phù hợp Java 17 và lệnh biên dịch `--release 17`.

| Dạng gọi | Bên gọi cung cấp | Kiểu mảng kết quả | Trường hợp thường dùng |
| --- | --- | --- | --- |
| `items.toArray()` | Không có đối số | `Object[]` | Chỉ cần một mảng đối tượng tổng quát |
| `items.toArray(new String[0])` | Mảng mẫu có kiểu `String[]` | Cùng kiểu mảng lúc chạy với mảng đối số | Cần mảng chuỗi; cách viết cũng dùng được trước Java 11 |
| `items.toArray(buffer)` | Một mảng có sẵn | Cùng kiểu mảng lúc chạy với `buffer` | Chủ động cung cấp nơi chứa, chấp nhận quy tắc tái sử dụng |
| `items.toArray(String[]::new)` | Hàm tạo mảng chuỗi | Kiểu mảng do hàm tạo cung cấp | Muốn diễn đạt trực tiếp kiểu mảng trên Java 11+ |

Tên `T` trong hai phương thức có kiểu là tham số kiểu của **phương thức**, không phải một cách viết lại bắt buộc của `E` trong `Collection<E>`. Vì vậy, chọn một kiểu mảng đích không tương thích với phần tử có thể vẫn biên dịch, rồi thất bại lúc chạy.

Một collection chuỗi có thể được đưa vào mảng `Object[]` hoặc `CharSequence[]`. Ngược lại, một collection số chứa cả `Integer` và `Double` không thể được lưu đầy đủ trong `Integer[]`. Phải xét các phần tử thực tế và loại đối tượng mà mảng đích chấp nhận.

[Hợp đồng `Collection.toArray`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#toArray()) xác định kiểu kết quả, thứ tự và các quy tắc nhận mảng/hàm tạo mảng.

## 3. toArray(): kết quả thật sự là Object[]

Khi gọi không đối số, kiểu lúc chạy của mảng kết quả là `Object[]`, kể cả khi tất cả phần tử đều là `String`. Mảng có số ô bằng số phần tử được trả về và không phải mảng lưu trữ nội bộ mà collection giữ để phản ánh các lần sửa ô của kết quả.

Đoạn sau chỉ minh họa ý tưởng, không phải chương trình độc lập:

```java
List<String> words = List.of("red", "blue");
Object[] objects = words.toArray();

// Van bien dich, nhung that bai luc chay:
String[] strings = (String[]) objects;
```

Phép ép kiểu kiểm tra xem đối tượng đang có có phải `String[]` hay không. Nó không duyệt từng ô để thấy mọi phần tử là chuỗi, rồi thay mảng `Object[]` bằng một mảng chuỗi mới. Kết quả vì vậy là `ClassCastException`.

Nếu API tiếp theo nhận `String[]`, yêu cầu tạo đúng loại mảng ngay từ đầu bằng `toArray(new String[0])` hoặc `toArray(String[]::new)`. Thêm phép ép kiểu để tránh sửa lời gọi không giải quyết được vấn đề.

Với `Object[]` nhận từ `toArray()`, sửa một ô của mảng không thay chính ô tương ứng trong cấu trúc collection. Tuy nhiên, đối tượng mà ô trỏ tới vẫn có thể được chia sẻ; phần về sao chép nông sẽ phân biệt hai loại thay đổi này.

## 4. toArray(T[]): mảng đối số vừa là nơi chứa, vừa mang thông tin kiểu

Đặt:

- `n` là số phần tử cần đưa ra mảng.
- `m` là độ dài mảng đối số `array`.
- Các phần tử có kiểu phù hợp để lưu vào mảng đó.

Phần phân tích này giả sử không có đường sửa đồng thời làm thay đổi dữ liệu trong lúc chuyển đổi và lời gọi hoàn tất thành công.

| Quan hệ kích thước | Mảng được trả về | Độ dài kết quả | Điều cần nhớ |
| --- | --- | --- | --- |
| `m < n` | Mảng mới, cùng kiểu lúc chạy với mảng đối số | `n` | Phải dùng giá trị trả về; mảng đã truyền vào chưa đủ chỗ |
| `m == n` | Chính mảng đối số | `m` | Tất cả các ô chứa phần tử của collection |
| `m > n` | Chính mảng đối số | `m` | Ô ở chỉ số `n` được đặt thành `null` sau phần dữ liệu |

Mảng không thể tăng độ dài tại chỗ. Khi quá nhỏ, phương thức tạo một mảng khác rồi trả về tham chiếu mới. Do đó, cách gọi cần giữ kết quả:

```java
// Doan minh hoa: buffer da duoc khai bao la String[].
buffer = names.toArray(buffer);
```

Nếu chỉ gọi `names.toArray(buffer)` rồi bỏ giá trị trả về, mã có thể có vẻ đúng khi `buffer` đủ lớn nhưng đọc nhầm bộ đệm cũ khi collection có thêm phần tử.

### 4.1. Mảng độ dài 0 vẫn cung cấp thông tin kiểu

Trong `names.toArray(new String[0])`, mảng rỗng không có nhiệm vụ tự chứa được mọi phần tử. Nó cung cấp loại mảng cần tạo. Nếu collection không rỗng, mảng kết quả mới có độ dài phù hợp; kiểu lúc chạy vẫn là `String[]`.

Không được diễn giải `new String[0]` thành “chỉ lấy 0 phần tử”. `toArray` đưa ra toàn bộ phần tử theo hợp đồng của collection.

Nếu collection rỗng và mảng đối số cũng rỗng, không cần thêm chỗ chứa: mảng đối số có thể được trả về đúng theo nhánh đủ chỗ. Điều này khác với việc mặc nhiên cho rằng mọi dạng `toArray` luôn tạo một đối tượng mảng mới.

### 4.2. Null sau dữ liệu không phải độ dài mảng mới

Ví dụ, collection có hai chuỗi nhưng mảng đích có năm ô. Sau khi gọi thành công, hai ô đầu chứa dữ liệu, ô thứ ba là `null` và độ dài mảng vẫn là năm.

Không có cam kết rằng toàn bộ phần đuôi sẽ được xóa trắng. Trong ví dụ dùng `ArrayList` bên dưới, các ô sau dấu `null` vẫn giữ giá trị cũ. Vì thế, duyệt toàn bộ `result.length` như thể tất cả đều là phần tử của collection có thể xử lý nhầm dữ liệu cũ.

Nếu collection được biết chắc không chứa `null`, dấu `null` ngay sau vùng dữ liệu có thể giúp nhận biết điểm kết thúc. Nếu collection cho phép `null`, tìm `null` đầu tiên không phải cách xác định số phần tử hợp lệ.

Trường hợp collection rỗng cũng cần chú ý: với mảng đích còn chỗ, ô `0` được đặt thành `null`; các phần tử cũ ở vị trí sau đó có thể vẫn còn.

### 4.3. Quy tắc kiểu dựa trên mảng thực tế

Biến `Number[]` có thể trỏ tới `new Integer[0]`. Truyền biến này vào `toArray` không yêu cầu phương thức tạo `Number[]` rộng hơn. Kiểu mảng thực tế vẫn là `Integer[]`, nên một phần tử `Double` không thể được chứa trong kết quả ấy.

Cần phân biệt:

```java
// Doan minh hoa hai doi tuong mang co kieu luc chay khac nhau.
Number[] wide = new Number[0];
Number[] narrow = new Integer[0];
```

Cùng kiểu khai báo `Number[]` không có nghĩa hai mảng có cùng khả năng chứa phần tử. [Quy tắc mảng của Java](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html#jls-10.5) giải thích phép kiểm tra khi lưu đối tượng vào mảng.

## 5. Chương trình: Quan sát các nhánh kích thước

Chương trình sau dùng `ArrayList` để thứ tự dữ liệu xác định. Các biến `small`, `exact` và `large` lần lượt kiểm tra mảng nhỏ hơn, bằng và lớn hơn số phần tử.

Tệp `ToArrayOverloadsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ToArrayOverloadsDemo {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>(List.of("Lan", "Minh"));

        Object[] objects = names.toArray();
        System.out.println("plainType=" + objects.getClass().getTypeName());
        System.out.println("plain=" + Arrays.toString(objects));
        try {
            String[] invalid = (String[]) objects;
            throw new AssertionError("Cast unexpectedly succeeded: " + invalid.length);
        } catch (ClassCastException expected) {
            System.out.println("plainCast=ClassCastException");
        }

        String[] small = {"old"};
        String[] grown = names.toArray(small);
        System.out.println("smallReused=" + (grown == small));
        System.out.println("smallAfter=" + Arrays.toString(small));
        System.out.println("grown=" + Arrays.toString(grown));

        String[] exact = new String[2];
        String[] exactResult = names.toArray(exact);
        System.out.println("exactReused=" + (exactResult == exact));
        System.out.println("exact=" + Arrays.toString(exact));

        String[] large = {"x0", "x1", "x2", "x3", "x4"};
        String[] largeResult = names.toArray(large);
        System.out.println("largeReused=" + (largeResult == large));
        System.out.println("large=" + Arrays.toString(large));

        String[] zeroResult = names.toArray(new String[0]);
        String[] generated = names.toArray(String[]::new);
        System.out.println("zeroType=" + zeroResult.getClass().getTypeName());
        System.out.println("generated=" + Arrays.toString(generated));

        generated[0] = "changed";
        names.set(1, "owner");
        System.out.println("namesAfter=" + names);
        System.out.println("arrayAfter=" + Arrays.toString(generated));

        String[] emptyTarget = {"old0", "old1", "old2"};
        String[] emptyResult = List.<String>of().toArray(emptyTarget);
        System.out.println("emptyReused=" + (emptyResult == emptyTarget));
        System.out.println("emptyTarget=" + Arrays.toString(emptyTarget));

        List<String> withNull = Arrays.asList(null, "x");
        String[] nullableTarget = {"a", "b", "c", "d"};
        withNull.toArray(nullableTarget);
        System.out.println("nullable=" + Arrays.toString(nullableTarget));
    }
}
```

Biên dịch và chạy tại thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 ToArrayOverloadsDemo.java
java ToArrayOverloadsDemo
```

Đầu ra:

```text
plainType=java.lang.Object[]
plain=[Lan, Minh]
plainCast=ClassCastException
smallReused=false
smallAfter=[old]
grown=[Lan, Minh]
exactReused=true
exact=[Lan, Minh]
largeReused=true
large=[Lan, Minh, null, x3, x4]
zeroType=java.lang.String[]
generated=[Lan, Minh]
namesAfter=[Lan, owner]
arrayAfter=[changed, Minh]
emptyReused=true
emptyTarget=[null, old1, old2]
nullable=[null, x, null, d]
```

### 5.1. Đọc kết quả theo ba loại thông tin

`plainType=java.lang.Object[]` giải thích tại sao ép mảng này thành `String[]` thất bại. Loại đối tượng mảng chưa từng trở thành mảng chuỗi chỉ vì nội dung là hai chuỗi.

`smallReused=false` cho thấy mảng một ô không được trả về làm nơi chứa kết quả hai phần tử. Trong thí nghiệm này, `small` vẫn giữ `"old"`, còn dữ liệu nằm trong `grown`. Đây là lý do phải giữ giá trị trả về.

`exactReused=true` và `largeReused=true` cho thấy phương thức có thể ghi vào chính mảng được cung cấp khi đủ chỗ. Trong mảng lớn, `x2` bị thay bằng dấu `null` ở ngay sau dữ liệu; `x3` và `x4` vẫn còn.

`namesAfter` và `arrayAfter` chứng minh việc thay một ô của mảng kết quả và thay một phần tử trong list không làm hai cấu trúc trở thành một khung nhìn sống của nhau trong ví dụ này.

Cuối cùng, `nullable=[null, x, null, d]` chứa hai giá trị `null` có vai trò khác nhau: ô đầu là phần tử thực sự của collection, ô thứ ba là dấu kết thúc sau hai phần tử. Chỉ đọc tới `null` đầu tiên sẽ bỏ mất phần tử `"x"`.

## 6. Lỗi kiểu trong ba cách chuyển sang mảng

`toArray()` tạo `Object[]`; nếu ép chính đối tượng mảng ấy thành `String[]`, lời ép gây `ClassCastException`. Với hai overload nhận mảng hoặc hàm tạo mảng, hệ thống còn phải kiểm tra từng phần tử có lưu được vào **kiểu thành phần thực** của mảng đích hay không. Nếu không, `ArrayStoreException` xuất hiện.

Ví dụ minh họa trong một phương thức có các import `Collection` và `List`:

```java
Collection<Number> values = List.of(1, 2.5);
Number[] accepted = values.toArray(Number[]::new); // [1, 2.5]
// Cả hai lời gọi sau đều gây ArrayStoreException:
values.toArray(new Integer[0]);
values.toArray(Integer[]::new);
```

`Number[]` nhận được cả `Integer` và `Double`, còn `Integer[]` không chứa được `Double`. Generics không loại bỏ kiểm tra lưu trữ lúc chạy. Ngay cả biến `Number[]` cũng có thể đang giữ một đối tượng `Integer[]` và bị từ chối khi ghi `Double`. Chi tiết về covariance, kiểu mảng thực và kết quả chép dở được phân tích trong bài chuyên đề `ArrayStoreException` tiếp theo.

Cần phân biệt thêm các trường hợp sau:

| Tình huống | Kết quả hoặc yêu cầu |
| --- | --- |
| `(String[]) list.toArray()` | `ClassCastException`, vì kiểu thực là `Object[]` |
| `toArray(new Integer[0])` với dữ liệu chứa `Double` | `ArrayStoreException` lúc lưu phần tử |
| `toArray((String[]) null)` hoặc `toArray((IntFunction<String[]>) null)` | `NullPointerException`; ép kiểu ở đây chỉ để phân giải overload |
| `toArray(null)` không kèm kiểu khi có cả hai overload | Lời gọi nhập nhằng tại thời điểm biên dịch |
| Chuyển `List<Integer>` sang `int[]` | Cần luồng `IntStream`/phép tháo hộp riêng; `Collection.toArray` trả mảng tham chiếu |

Nếu phép chuyển đổi thất bại, không coi bộ đệm được cung cấp là kết quả hoàn chỉnh hoặc mặc định nó đã được hoàn tác. `toArray` sao chép các tham chiếu, không tự chuyển `Integer[]` thành `int[]` và không tự kiểm tra chính sách `null` cho bước tháo hộp.

## 7. toArray(IntFunction): giao trách nhiệm tạo mảng cho một hàm

`IntFunction<R>` là giao diện hàm nhận một số `int` và trả về `R`. Với `toArray`, `R` là `T[]`: hàm nhận độ dài được yêu cầu rồi tạo một mảng có kiểu và độ dài ấy.

Hai cách viết tương đương về ý định tạo mảng chuỗi:

```java
// Doan minh hoa, names la mot Collection<String>.
String[] first = names.toArray(String[]::new);
String[] second = names.toArray(length -> new String[length]);
```

Cú pháp `String[]::new` là tham chiếu tới thao tác tạo mảng. Việc điền phần tử từ collection vào mảng vẫn do `toArray` thực hiện; hàm tạo chỉ cung cấp mảng.

[`IntFunction.apply(int)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/IntFunction.html) giải thích loại đầu vào và kết quả của giao diện này. Một lambda không có tham số như `() -> new String[0]` không đáp ứng hình dạng hàm nhận `int` mà overload yêu cầu.

### 7.1. Đối số nhận được không mặc nhiên là size()

Cách triển khai mặc định được công bố trong `Collection` gọi hàm tạo với `0`, sau đó chuyển mảng nhận được vào `toArray(T[])`. Có thể hình dung bằng đoạn minh họa cơ chế sau:

```java
// Mo hinh rut gon cua default method, khong phai chuong trinh doc lap.
return toArray(generator.apply(0));
```

Với hàm `String[]::new`, bước đầu tạo mảng chuỗi rỗng; phương thức nhận mảng sẽ cấp phát đủ chỗ nếu cần. Như vậy, có collection ba phần tử nhưng hàm tạo mặc định vẫn nhận `0` là hành vi được giải thích rõ, không phải lỗi.

Một lớp collection có thể ghi đè phương thức mặc định. Vì thế:

- Hàm tạo nên dùng đúng độ dài nhận được, không dựa vào một số lượng phần tử đã chụp từ bên ngoài.
- Không viết hàm tạo chỉ đúng khi đối số luôn bằng `0` hoặc luôn bằng `size()`.
- Không dựa vào số lần gọi hay tác dụng phụ của hàm tạo như một phần của nghiệp vụ.
- Hàm tạo không nên sửa collection đang được chuyển đổi.

[Đặc tả phương thức mặc định](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#toArray(java.util.function.IntFunction)) mô tả rõ lời gọi với `0`. Chương trình tiếp theo dùng một lớp kế thừa phương thức mặc định để quan sát đúng trường hợp này.

### 7.2. Khác với việc gọi qua Stream

`collection.toArray(String[]::new)` gọi API trên collection. `collection.stream().toArray(String[]::new)` tạo một luồng rồi gọi phép kết thúc của `Stream`.

Chúng có thể cho cùng dãy phần tử trong một tình huống đơn giản nhưng có hợp đồng thực thi và cơ chế triển khai riêng. Không lấy quan sát “hàm tạo nhận 0” ở default method của `Collection` để suy ra hàm tạo của mọi luồng, đặc biệt khi có xử lý song song, phải được gọi giống hệt.

Nếu chỉ cần chuyển collection thành mảng có kiểu, lời gọi trực tiếp diễn đạt đủ ý định. Nếu cần lọc hoặc biến đổi phần tử trước, một chuỗi thao tác trên luồng có thể đáp ứng thêm nhu cầu ấy; chính bước biến đổi mới giải thích việc sử dụng luồng.

[API `Stream.toArray(IntFunction)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toArray(java.util.function.IntFunction)) có mô tả riêng cho việc tạo các mảng phục vụ kết quả của luồng.

## 8. Quyền sở hữu mảng và sao chép nông

Có ba đối tượng cần phân biệt:

1. Collection chứa các tham chiếu phần tử.
2. Mảng kết quả có các ô chứa tham chiếu.
3. Những đối tượng phần tử mà cả hai cấu trúc có thể cùng trỏ tới.

Tạo một mảng mới không có nghĩa tạo lại mọi đối tượng phần tử. Nếu một phần tử là đối tượng có thể thay đổi, sửa trạng thái của đối tượng ấy qua mảng vẫn có thể được quan sát khi đọc qua collection.

Ngược lại, thay một ô trong một mảng mới bằng tham chiếu khác chỉ thay ô của mảng đó. Nó không tự gọi `set` hay `add` trên collection. Việc collection tăng số phần tử về sau cũng không làm mảng đã có tăng độ dài.

Đây là **sao chép nông** (shallow copy) theo góc nhìn cấu trúc chứa các tham chiếu, không phải sao chép sâu toàn bộ đồ thị đối tượng.

### 8.1. Cung cấp mảng có sẵn không xóa các quan hệ chia sẻ đã tồn tại

Với `toArray()`, collection phải cung cấp một mảng mới an toàn cho bên gọi sửa các ô.

Với `toArray(T[])`, bên gọi chủ động cung cấp một mảng. Nếu mảng ấy đủ chỗ, chính nó được trả về. Phương thức không tự làm cho mảng đó độc lập với tất cả thành phần khác vốn đang sử dụng nó.

Ví dụ đặc biệt đáng chú ý:

- `Arrays.asList(backing)` tạo list được hậu thuẫn bởi `backing`.
- Gọi `list.toArray(backing)` với chính mảng gốc có thể trả về `backing`.
- Kết quả vẫn chia sẻ các ô mảng với list theo quan hệ đã tồn tại từ trước.

Nếu muốn một mảng mới riêng biệt trong tình huống này, truyền hàm tạo mảng mới như `String[]::new` hoặc một mảng đích mới phù hợp. Ngay cả khi các ô mảng đã tách biệt, các đối tượng phần tử vẫn có thể được chia sẻ.

[API `Arrays.asList`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#asList(T...)) quy định list được hậu thuẫn bởi mảng đầu vào. Đọc cả hợp đồng nguồn lẫn đích giúp tránh suy luận rằng một lời gọi chuyển đổi tự xóa mọi quan hệ chia sẻ.

### 8.2. Chương trình: Ba lớp chia sẻ và hàm tạo mặc định

`Counter` là đối tượng có một trường thay đổi được. Phần đầu chương trình phân biệt sửa đối tượng `Counter` với thay ô mảng chứa tham chiếu tới `Counter`.

Phần sau dùng `Arrays.asList` để kiểm tra quan hệ với mảng gốc. Cuối cùng, lớp kế thừa `AbstractCollection` chỉ cung cấp `iterator` và `size`, giữ lại cách triển khai mặc định của overload nhận hàm tạo. Việc ghi lại đối số độ dài trong ví dụ chỉ phục vụ quan sát, không phải tác dụng phụ cần dùng trong ứng dụng.

Tệp `ToArrayOwnershipDemo.java`:

```java
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class ToArrayOwnershipDemo {
    private static final class Counter {
        int value;

        Counter(int value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    public static void main(String[] args) {
        List<Counter> counters = new ArrayList<>(
                List.of(new Counter(1), new Counter(2)));
        Counter[] captured = counters.toArray(Counter[]::new);
        System.out.println("sameElement=" + (captured[0] == counters.get(0)));

        captured[0].value = 10;
        captured[1] = new Counter(99);
        counters.add(new Counter(3));
        System.out.println("collection=" + counters);
        System.out.println("captured=" + Arrays.toString(captured));
        System.out.println("capturedLength=" + captured.length);

        String[] backing = {"left", "right"};
        List<String> backedList = Arrays.asList(backing);
        String[] detached = backedList.toArray(String[]::new);
        detached[0] = "copy";
        backing[1] = "owner";
        System.out.println("backing=" + Arrays.toString(backing));
        System.out.println("detached=" + Arrays.toString(detached));
        System.out.println("backedList=" + backedList);

        String[] alreadyAliased = backedList.toArray(backing);
        System.out.println("suppliedBackingReused=" + (alreadyAliased == backing));
        alreadyAliased[0] = "shared";
        System.out.println("afterAliasedWrite=" + backedList);

        Collection<Integer> inheritedDefault = new AbstractCollection<>() {
            private final List<Integer> values = List.of(3, 4, 5);

            @Override
            public Iterator<Integer> iterator() {
                return values.iterator();
            }

            @Override
            public int size() {
                return values.size();
            }
        };

        List<Integer> requestedLengths = new ArrayList<>();
        Integer[] result = inheritedDefault.toArray(length -> {
            requestedLengths.add(length);
            return new Integer[length];
        });
        System.out.println("defaultRequests=" + requestedLengths);
        System.out.println("defaultResult=" + Arrays.toString(result));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ToArrayOwnershipDemo.java
java ToArrayOwnershipDemo
```

Đầu ra:

```text
sameElement=true
collection=[10, 2, 3]
captured=[10, 99]
capturedLength=2
backing=[left, owner]
detached=[copy, right]
backedList=[left, owner]
suppliedBackingReused=true
afterAliasedWrite=[shared, owner]
defaultRequests=[0]
defaultResult=[3, 4, 5]
```

### 8.3. Giải thích những kết quả có vẻ trái ngược

`sameElement=true` cho thấy `captured[0]` và phần tử đầu của collection cùng trỏ tới một đối tượng. Sửa trường `value` qua `captured[0]` làm collection nhìn thấy `10`.

Thay `captured[1]` bằng một `Counter(99)` lại không thay phần tử thứ hai trong collection. Collection vẫn thấy `2`, còn mảng thấy `99`. Khi collection được thêm phần tử `3`, mảng vẫn có độ dài hai.

Ở ví dụ chuỗi, `detached` được tạo bằng hàm tạo mảng mới. Thay `detached[0]` không đổi `backing[0]`. Thay `backing[1]` được nhìn thấy qua `backedList`, nhưng `detached[1]` vẫn là `"right"`.

Sau đó `suppliedBackingReused=true` xác nhận khi chính `backing` được cung cấp làm mảng đích, phương thức trả về mảng đang hậu thuẫn list. Ghi `"shared"` qua kết quả lúc này được nhìn thấy trong list. Sự khác nhau đến từ đối tượng mảng đã được chọn làm nơi chứa, không phải từ việc `String` có tự thay đổi hay không.

Cuối cùng, `defaultRequests=[0]` khớp với cách triển khai mặc định đã mô tả. Mảng kết quả vẫn chứa đủ ba phần tử. Đối số đưa cho hàm tạo và độ dài mảng trả về cuối cùng không bắt buộc bằng nhau trong cơ chế mặc định này.

[`AbstractCollection`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractCollection.html) là lớp triển khai khung, hữu ích để quan sát phần cơ chế mặc định mà không phải tự xây dựng toàn bộ collection.

## 9. Áp dụng đúng hợp đồng trong mã thực tế

### 9.1. Chọn theo mục tiêu của API nhận kết quả

| Nhu cầu | Cách viết có thể chọn | Điều phải kiểm tra thêm |
| --- | --- | --- |
| API nhận mảng đối tượng tổng quát | `collection.toArray()` | Có thực sự cần `Object[]` không? |
| API nhận mảng chuỗi trên Java 11+ | `collection.toArray(String[]::new)` | Các phần tử phải lưu được trong `String[]` |
| API nhận mảng chuỗi, cần cách viết tương thích trước Java 11 | `collection.toArray(new String[0])` | Luôn giữ mảng trả về |
| Muốn tái sử dụng một bộ đệm | `buffer = collection.toArray(buffer)` | Chấp nhận mảng có thể còn dư ô và các quan hệ chia sẻ sẵn có |
| API cần `int[]` từ `Collection<Integer>` | Biến đổi/tháo hộp rõ ràng, chẳng hạn `mapToInt` | Có chính sách cho `null` và dữ liệu không hợp lệ |

Nếu API phía sau yêu cầu một mảng chỉ chứa đúng `n` phần tử về mặt độ dài vật lý, không nên truyền một bộ đệm lớn hơn rồi mặc nhiên coi `result.length` là kích thước collection. Chọn mảng mới có kích thước phù hợp giúp hợp đồng giữa hai API đơn giản hơn.

Ngược lại, khi API được thiết kế để nhận bộ đệm và độ dài dữ liệu riêng, việc tái sử dụng mảng lớn có thể phù hợp. Độ dài dữ liệu cần được quản lý rõ ràng; dấu `null` không thay thế được độ dài đó khi `null` cũng là dữ liệu hợp lệ.

### 9.2. Thứ tự đi theo collection, không tự được sắp xếp

Nếu collection cam kết thứ tự duyệt, `toArray` phải giữ thứ tự tương ứng. Một `List` cung cấp thứ tự dãy phần tử, nên các chương trình trong bài có đầu ra xác định.

Một collection không có cam kết thứ tự không trở thành có thứ tự chỉ vì đã chuyển thành mảng. Ví dụ, không dùng kết quả của một `HashSet` để khẳng định phần tử ở chỉ số `0` luôn là phần tử nhỏ nhất hay được thêm đầu tiên.

Tương tự, việc đầu hàng đợi ưu tiên có ý nghĩa ưu tiên không có nghĩa toàn bộ iterator hoặc mảng kết quả của `PriorityQueue` đã được sắp xếp. [API `PriorityQueue`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html) phân biệt rõ truy cập phần tử đầu với thứ tự duyệt. Nếu API đích cần một dãy đã sắp xếp, bước sắp xếp phải được thực hiện có chủ ý theo quy tắc phù hợp.

### 9.3. Map không có toArray trực tiếp

`Map` không phải là một `Collection`. Muốn xuất khóa, giá trị hoặc các cặp ánh xạ, phải chọn collection view tương ứng.

Đoạn sau minh họa hai lựa chọn khác nhau, không phải chương trình chạy độc lập:

```java
// scores la Map<String, Integer>.
String[] keys = scores.keySet().toArray(String[]::new);
Integer[] values = scores.values().toArray(Integer[]::new);
```

Cần chọn đúng ý nghĩa dữ liệu cho API đích. Nếu quan hệ khóa–giá trị phải được giữ cùng nhau, xuất hai mảng riêng qua hai lời gọi không tự tạo ra một bản chụp nguyên tử của các cặp ánh xạ khi map có thể thay đổi giữa hai lần gọi.

### 9.4. Mảng kết quả không chứng minh tính nguyên tử khi có sửa đồng thời

Các quy tắc kích thước ở phần trước mô tả một lần chuyển đổi hợp lệ, không mặc nhiên bổ sung một chính sách đồng bộ cho collection.

Ví dụ, gọi `size()` để cấp phát mảng rồi mới gọi `toArray` là hai thao tác riêng. Collection có thể thay đổi giữa chúng nếu có đường sửa đồng thời. Đừng suy luận rằng kích thước đã đọc là số phần tử chính xác của một thời điểm chung với mọi dữ liệu được đưa ra mảng.

Cần tuân thủ chính sách truy cập của loại collection cụ thể hoặc sử dụng cơ chế phối hợp thích hợp cho thao tác xuất dữ liệu. Một mảng nhận được thành công không tự chứng minh rằng toàn bộ dữ liệu nghiệp vụ liên quan đã được chụp ở cùng một thời điểm.

Với collection không cho phép sửa như `List.of`, `toArray` vẫn là thao tác đọc hợp lệ. Mảng mới nhận được có thể cho sửa các ô; điều đó không cấp quyền sửa cấu trúc của list nguồn.

### 9.5. Chi phí và những kết luận hiệu năng cần tránh

Thông thường, xuất `n` phần tử cần đưa `n` tham chiếu vào mảng đích. Với mảng mới đủ chứa dữ liệu, bộ nhớ cho các ô mảng tăng theo số phần tử; các đối tượng phần tử không vì thế được tạo bản sao sâu.

Một mảng đã cấp phát đủ chỗ có thể giảm nhu cầu tạo mảng kết quả mới. Tuy nhiên, còn phải tính chi phí quản lý bộ đệm, số ô dư, các tham chiếu cũ còn được giữ và loại collection đang dùng.

Không có kết luận phổ quát rằng `new T[0]`, `new T[size()]` hay dạng tham chiếu tạo mảng luôn nhanh nhất trên mọi JDK và mọi collection. Cách triển khai, tối ưu của JVM, kích thước dữ liệu và cách đo đều ảnh hưởng đến kết quả. Một đoạn mã đo thời gian quá ngắn hoặc bỏ qua kết quả có thể đo tối ưu của trình biên dịch nhiều hơn là chi phí chuyển đổi thực tế.

Ưu tiên cách viết diễn đạt đúng kiểu mảng và quyền sở hữu cần có. Chỉ chọn một cách phức tạp hơn vì hiệu năng sau khi có số đo phù hợp với đường chạy thực sự.

### 9.6. Quan sát nội dung mảng bằng công cụ đúng

Các ví dụ dùng `Arrays.toString` để hiển thị các ô mảng. In thẳng đối tượng mảng bằng `System.out.println(array)` thường hiển thị dạng nhận diện của đối tượng, không phải dãy phần tử dễ đọc.

Tương tự, khi cần so sánh nội dung hai mảng một chiều, dùng phép so sánh phù hợp như `Arrays.equals` thay vì coi `array1.equals(array2)` là so sánh từng ô. Chuyển đổi đúng nhưng quan sát/so sánh sai cách có thể khiến quá trình kiểm tra đưa ra kết luận sai.

## 10. Thực hành để tự kiểm tra

### Bài 1: Giữ kết quả khi mảng quá nhỏ

Tạo list ba chuỗi và mảng đích một ô. Gọi `toArray` một lần có giữ kết quả, một lần bỏ kết quả.

**Điểm cần giải thích:** mảng không tăng độ dài tại chỗ. Kết quả đầy đủ nằm trong mảng được trả về khi bộ đệm chưa đủ. Mã bỏ kết quả chỉ có vẻ hoạt động nếu vô tình rơi vào nhánh tái sử dụng.

### Bài 2: Phân biệt dấu null với dữ liệu null

Tạo collection có ba phần tử, trong đó phần tử thứ hai là `null`. Cung cấp mảng năm ô đã có giá trị cũ, rồi quan sát toàn bộ mảng.

**Điểm cần giải thích:** có một `null` thuộc dữ liệu và một `null` ngay sau ba phần tử. Không được dùng vị trí `null` đầu tiên để đo kích thước dữ liệu. Các ô ngoài vùng dữ liệu không tự trở thành phần tử mới của collection.

### Bài 3: Thay kiểu khai báo nhưng giữ kiểu mảng thực tế

Khai báo `Number[] buffer = new Integer[0]`, rồi thử xuất một collection chứa `Integer` và `Double` vào `buffer`. Sau đó thay bằng `new Number[0]`.

**Điểm cần giải thích:** giới hạn lưu trữ nằm ở đối tượng mảng thực tế. Kiểu khai báo rộng hơn không biến mảng số nguyên thành mảng số tổng quát.

### Bài 4: Tách thay ô khỏi sửa đối tượng phần tử

Dùng một list đối tượng có trường thay đổi được. Nhận mảng bằng hàm tạo mới, sửa trường qua một ô rồi thay ô khác bằng đối tượng mới.

**Điểm cần giải thích:** sửa trạng thái của đối tượng đang được chia sẻ khác với thay một tham chiếu trong cấu trúc chứa. `toArray` không tự sao chép sâu các phần tử.

### Bài 5: Truy tìm vì sao kết quả vẫn chia sẻ ô với list

Dùng `Arrays.asList(backing)`, rồi so sánh kết quả của `toArray(backing)` với `toArray(String[]::new)`.

**Điểm cần giải thích:** ở lời gọi đầu, bên gọi đã cung cấp chính mảng đang hậu thuẫn list và nó đủ chỗ, nên tái sử dụng giữ lại quan hệ chia sẻ. Ở lời gọi thứ hai, hàm tạo cấp mảng mới. Dù các ô đã tách biệt, loại phần tử vẫn quyết định liệu trạng thái đối tượng có thể được chia sẻ hay không.

### Bài 6: Giải thích đối số 0 của hàm tạo

Chạy phần `inheritedDefault` trong chương trình thứ ba. Thử thay dữ liệu bằng một list rỗng và một list nhiều phần tử hơn, nhưng vẫn tạo mảng theo độ dài mà lambda nhận.

**Điểm cần giải thích:** phương thức mặc định của `Collection` dùng mảng được tạo từ đối số `0` để đi tiếp vào overload nhận mảng. Không cần tự lấy `size()` bên ngoài hàm tạo để “sửa” cơ chế này.

Một cách chọn lời gọi vững chắc là đọc theo thứ tự: **kiểu mảng API đích cần → mảng thực tế được tạo/cung cấp → quy tắc kích thước → khả năng chia sẻ tham chiếu → chính sách thứ tự và truy cập của collection**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="array-conversion-005">Kiểu thực của mảng, ArrayStoreException và phần tử null kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu thực của mảng, ArrayStoreException và phần tử null kết thúc

## 1. Vì sao chuyển Collection thành mảng vẫn cần hiểu kiểu dữ liệu?

Một danh sách có thể được xây dựng từng phần tử, còn một API khác lại yêu cầu nhận `String[]`, `Number[]` hoặc một mảng có sẵn để ghi kết quả. `toArray` là cầu nối giữa hai cách biểu diễn đó. Tuy nhiên, lấy đủ các giá trị chưa phải toàn bộ công việc: mảng kết quả phải có **kiểu thành phần lúc chạy phù hợp**, có đủ vị trí để chứa dữ liệu, và có ranh giới rõ ràng giữa dữ liệu vừa chép với phần dư của một mảng được dùng lại.

Ba câu hỏi của bài này là:

1. Mảng thật sự được tạo là `Object[]`, `String[]` hay một kiểu khác?
2. Điều gì xảy ra nếu có phần tử không thể lưu vào kiểu mảng được yêu cầu?
3. Khi mảng đích dài hơn số phần tử, ô `null` đặt sau dữ liệu có ý nghĩa gì?

Hãy hình dung một khay lưu trữ ghi rõ “chỉ chứa chuỗi”, gồm năm ô. Ta đưa vào hai tên người. Hai tên được xếp ở đầu; ô thứ ba được đặt `null` để đánh dấu điểm ngay sau dữ liệu đã chép. Nhãn “chỉ chứa chuỗi” của khay vẫn tồn tại, dù người cầm khay đang gọi nó chung là “khay chứa đối tượng”. Nếu cố ghi thêm một số nguyên vào khay ấy, vấn đề nằm ở loại khay thật sự, không phải tên biến đang cầm nó.

Bài học dùng các phép chuyển đổi từ `Collection` sang mảng tham chiếu trong Java 17. Nó không biến `List<Integer>` thành `int[]` và không xem việc chuyển sang mảng là một phép chuyển đổi giá trị, chẳng hạn tự đổi số `42` thành chuỗi `"42"`.

## 2. Nhận diện ba dạng toArray

`toArray()` trả về `Object[]`; `toArray(T[] array)` dùng lại mảng đầu vào nếu đủ chỗ hoặc cấp phát mảng khác có **cùng kiểu thực**; `toArray(IntFunction<T[]>)` yêu cầu một hàm tạo mảng có kiểu tương ứng (dạng mặc định của `Collection` truyền đối số `0` tới hàm này). Không dạng nào tự chuyển từng `Integer` thành `int`.

Bài trước đối chiếu cú pháp và các nhánh tạo mảng. Ở đây sẽ kiểm tra ba hệ quả sâu hơn: kiểu thực của nơi chứa, vị trí `null` kết thúc khi dùng lại bộ đệm lớn, và tác động của `ArrayStoreException` trong lúc chép.

## 3. Kiểu của biến không thay đổi kiểu thật của mảng

### 3.1. Mỗi mảng có kiểu thành phần lúc chạy

Trong đoạn minh họa:

~~~java
Object[] values = new String[3];
~~~

kiểu khai báo của biến là `Object[]`, nhưng đối tượng mảng được tạo bởi `new String[3]` vẫn là `String[]`. Nó có ba vị trí, mỗi vị trí có thể chứa một tham chiếu `String` hoặc `null`.

Có thể đọc `values[0]` dưới dạng `Object`. Nhưng điều đó không có nghĩa được ghi mọi `Object` vào mảng:

~~~java
values[0] = "A";  // Hợp lệ.
values[1] = 42;   // ArrayStoreException khi chạy.
~~~

Số `42` được đóng gói thành `Integer`. Nó phù hợp với kiểu `Object` của biểu thức truy cập qua biến, nhưng không phù hợp với kiểu thành phần `String` của mảng thật sự.

Java kiểm tra việc lưu phần tử vào mảng tham chiếu lúc chạy; giá trị không tương thích làm phát sinh `ArrayStoreException`. [Java Language Specification 17 — Array Store Exception](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html#jls-10.5).

Có thể kiểm tra kiểu để học hoặc chẩn đoán:

~~~java
System.out.println(values.getClass().getTypeName());
System.out.println(values.getClass().getComponentType().getTypeName());
~~~

Hai dòng lần lượt là `java.lang.String[]` và `java.lang.String`. Tên kiểu được in giúp thấy đối tượng thật sự đã được tạo. Trong mã ứng dụng thông thường, cách tạo mảng rõ ràng và hợp đồng kiểu vẫn quan trọng hơn việc liên tục dò kiểu rồi thử ghi.

### 3.2. Ép kiểu không phải là tạo một mảng mới

Đoạn sau là minh họa gây lỗi:

~~~java
Object[] plain = names.toArray();
String[] wrong = (String[]) plain;
~~~

`plain` giữ mảng thật sự có kiểu `Object[]`. Phép ép kiểu yêu cầu JVM xác nhận chính đối tượng ấy có phải `String[]` hay không; nó không duyệt từng chuỗi rồi dựng lại một mảng `String[]`. Do đó, kết quả là `ClassCastException` ngay tại phép ép kiểu.

Đối chiếu hai lỗi:

| Lỗi | Đang kiểm tra điều gì? | Ví dụ |
| --- | --- | --- |
| `ClassCastException` | Đối tượng mảng có thể được nhìn bằng kiểu tham chiếu yêu cầu hay không | Ép một `Object[]` thật sự thành `String[]` |
| `ArrayStoreException` | Giá trị phần tử có thể được lưu vào kiểu thành phần thật của mảng hay không | Chép `Integer` vào `String[]` |

Cách sửa ở ví dụ trên là yêu cầu mảng đúng kiểu ngay từ `toArray`:

~~~java
String[] correct = names.toArray(String[]::new);
~~~

Không nên bọc phép ép kiểu sai trong `try/catch` rồi dùng mảng rỗng thay thế. Cách ấy bỏ mất dữ liệu mà chưa xử lý đúng yêu cầu chuyển đổi.

## 4. toArray(T[]) chọn mảng kết quả như thế nào?

Gọi `n` là số phần tử cần chép và `m` là độ dài mảng truyền vào. Khi nguồn ổn định và các phần tử đều tương thích kiểu, có ba tình huống.

| Điều kiện | Mảng được trả về | Dữ liệu và phần kết thúc |
| --- | --- | --- |
| `m < n` | Mảng mới, cùng kiểu thực với mảng đầu vào | Chứa đủ `n` phần tử |
| `m == n` | Chính mảng đầu vào | Vừa đủ `n` vị trí, không có ô bổ sung để đặt dấu kết thúc |
| `m > n` | Chính mảng đầu vào | Chép vào `0..n-1` và đặt `null` ở chỉ số `n` |

Đối với `ArrayList`, tài liệu nêu rõ việc dùng lại hoặc tạo mảng cùng kiểu thực, và quy tắc đặt `null` ngay sau cuối dữ liệu khi mảng dư chỗ. [Java SE 17 — ArrayList.toArray](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html#toArray(T[])).

### 4.1. Mảng nhỏ: phải dùng giá trị trả về

Đoạn minh họa:

~~~java
String[] small = {"old"};
String[] result = names.toArray(small);
~~~

Danh sách có hai phần tử nên `small` chỉ dài một không đủ. Mảng `result` chứa `[Lan, An]`, có kiểu thực `String[]`, và `result == small` là `false`. Mảng `small` vẫn là một đối tượng riêng; nó không tự dài ra.

Sai lầm thường gặp là bỏ giá trị trả về:

~~~java
String[] target = new String[0];
names.toArray(target);
// target vẫn tham chiếu mảng dài 0.
~~~

Mảng không được đổi độ dài tại chỗ. Nếu muốn biến `target` giữ mảng kết quả, cần gán lại:

~~~java
target = names.toArray(target);
~~~

Hoặc dùng `String[] result = names.toArray(String[]::new)` khi không có nhu cầu tái sử dụng mảng cũ.

Một mảng độ dài `0` trong `new String[0]` đóng vai trò chỉ định kiểu mà không cung cấp sẵn chỗ cho tên người. Nó không yêu cầu kết quả cũng phải dài `0`.

### 4.2. Mảng vừa đủ: dùng lại chính mảng đó

~~~java
String[] exact = {"old0", "old1"};
String[] result = names.toArray(exact);
~~~

`result == exact` là `true` và dữ liệu thành `[Lan, An]`. Không có chỉ số `2` trong mảng hai ô, nên không đặt thêm `null` sau hai tên.

Mảng Java đã có thuộc tính `length`; `null` kết thúc trong một mảng dư chỗ là quy tắc riêng của phép chép này. Nó không phải yêu cầu rằng mọi mảng kết quả đều phải có một ô `null` cuối cùng.

### 4.3. Mảng dư chỗ: một ranh giới dữ liệu trong vùng lưu trữ lớn hơn

~~~java
String[] large = {"old0", "old1", "old2", "old3", "old4"};
String[] result = names.toArray(large);
~~~

Trong chương trình minh chứng dùng `ArrayList`, kết quả là:

~~~text
[Lan, An, null, old3, old4]
~~~

Chỉ số `0` và `1` chứa hai tên. Chỉ số `2` được đặt `null` vì đây là vị trí ngay sau dữ liệu đã chép. Hai giá trị cũ ở phía sau vẫn còn trong cách cài đặt được chạy.

Điểm được hợp đồng yêu cầu là ô ngay sau dữ liệu, không phải việc xóa sạch mọi ô còn lại. Không được diễn giải toàn bộ `result.length` thành số phần tử mới của danh sách. Cũng không nên dựa vào quy tắc này như một cơ chế xóa dữ liệu cũ khỏi toàn bộ vùng dư.

Nếu bên nhận sẽ duyệt tất cả phần tử của mảng và cần đúng hai tên, truyền một mảng dài đúng hai là cách biểu đạt đơn giản hơn việc gửi cả bộ đệm năm ô rồi ngầm mong bên nhận bỏ qua đuôi.

## 5. Hiểu đúng phần tử null kết thúc

### 5.1. Chỉ số của ô kết thúc bằng số phần tử đã chép

Với hai tên và mảng năm ô:

~~~text
chỉ số             0      1      2       3       4
sau khi chép       Lan    An     null    old3    old4
vai trò            dữ liệu      kết thúc        vùng dư
~~~

Nói chính xác hơn, hai ô `0` và `1` là dữ liệu; ô `2` là vị trí kết thúc được quy định. Các ô sau đó thuộc phần dư của mảng đã tái sử dụng.

`null` tại chỉ số `2` không phải một phần tử được thêm vào `names`. Danh sách vẫn có `size() == 2`. Mảng vẫn có `length == 5`. Phép chuyển đổi không làm danh sách mọc thêm một phần tử chỉ để đánh dấu điểm cuối.

Nếu nguồn rỗng và mảng đích có ít nhất một ô, vị trí ngay sau dữ liệu là `0`. Vì vậy, ô đầu được đặt `null`. Trong ca thử với mảng ba ô, kết quả quan sát là `[null, old1, old2]`. Nếu cả nguồn và mảng đích đều có độ dài `0`, không có ô nào để ghi và cũng không cần tạo một ô đánh dấu riêng.

### 5.2. null trong dữ liệu khác null dùng làm ranh giới

Một danh sách có thể chứa `null`, nếu loại danh sách đó cho phép. Ví dụ:

~~~java
List<String> values = Arrays.asList("A", null, "B");
String[] target = {"old0", "old1", "old2", "old3", "old4"};
values.toArray(target);
~~~

Kết quả quan sát:

~~~text
[A, null, B, null, old4]
~~~

Có hai vai trò khác nhau:

- `target[1]` là phần tử `null` thật sự của danh sách.
- `target[3]` là ô ngay sau ba phần tử được chép, được đặt `null` do đích dư chỗ.

Nếu quét từ đầu và dừng ở `null` đầu tiên, thuật toán chỉ đọc được `A` rồi bỏ mất `B`. Kết quả tìm được `1` không phải kích thước của nguồn, vốn là `3`.

Dấu kết thúc chỉ có thể giúp suy ra độ dài theo cách quét tới `null` đầu tiên khi đã biết chắc dữ liệu không chứa `null`. Trong thiết kế có dữ liệu nullable, hãy giữ số lượng phần tử bằng một thông tin riêng hoặc dùng mảng kết quả có độ dài đúng nhu cầu.

### 5.3. Mảng mới cũng có null, nhưng lý do có thể khác

Java khởi tạo các vị trí của mảng tham chiếu mới bằng `null`. Do đó, nhìn thấy nhiều `null` ở đuôi một mảng mới chưa chứng minh rằng `toArray` đã chủ động ghi tất cả các ô ấy.

Để hiểu đúng quy tắc, ví dụ của bài cố ý điền dữ liệu cũ `old0`, `old1`... vào mảng đích trước khi chuyển đổi. Nhờ vậy, ta phân biệt được ô nào bị ghi đè, ô nào được đặt dấu kết thúc, và phần nào không thuộc dữ liệu mới.

Đây là một cách kiểm thử hữu ích khi mã sử dụng bộ đệm tái sử dụng: bắt đầu bằng dữ liệu cũ có thể nhận biết, thay vì dùng một bộ đệm vốn đã toàn `null` rồi suy ra rằng mọi ô dư đều được xóa.

## 6. ArrayStoreException trong lúc chuyển đổi

### 6.1. Kiểu của từng phần tử phải phù hợp mảng đích

Đoạn minh họa:

~~~java
Collection<Number> numbers = Arrays.<Number>asList(4, 2.5);

Number[] accepted = numbers.toArray(new Number[0]);
Integer[] rejected = numbers.toArray(new Integer[0]);
~~~

`Number[]` có thể chứa cả `Integer` và `Double`, nên mảng thứ nhất chứa `[4, 2.5]`. `Integer[]` không chứa được `Double`, nên lời gọi thứ hai gây `ArrayStoreException`.

`toArray` không làm các việc sau để cứu lời gọi thứ hai:

- Không cắt `2.5` thành `2`.
- Không gọi `intValue()` trên mỗi số.
- Không bỏ những phần tử không phù hợp.
- Không âm thầm đổi kết quả từ `Integer[]` sang `Number[]`.

Nếu cần mảng `Integer[]`, ứng dụng phải định nghĩa và thực hiện phép chuyển đổi giá trị trước. Việc làm tròn, cắt phần thập phân, kiểm tra tràn số hoặc từ chối dữ liệu đều là quyết định nghiệp vụ riêng.

### 6.2. Vì sao mã vẫn biên dịch?

Dạng `<T> T[] toArray(T[] a)` có tham số kiểu `T` của chính phương thức. Nó không có ràng buộc kiểu `T` buộc mọi phần tử của `Collection<E>` phải tương thích ngay từ lúc biên dịch. Sự tương thích cuối cùng được kiểm tra khi lưu các giá trị thực tế.

Do đó, một nguồn khai báo rộng có thể chuyển thành mảng hẹp nếu mọi phần tử thực tế đều phù hợp:

~~~java
List<Object> values = List.of("A", "B");
String[] strings = values.toArray(new String[0]);
~~~

Cả hai phần tử ở đây đều là `String` nên lời gọi chạy được. Nếu nguồn sau này có thêm một `Integer`, kết quả sẽ khác. Khả năng chạy được với một tập dữ liệu không tạo thành bảo đảm kiểu cho mọi giá trị được phép thêm vào một `List<Object>`.

Khi một API chỉ chấp nhận chuỗi, giữ nguồn ở kiểu `Collection<String>` và kiểm soát dữ liệu đi vào giúp mô hình rõ hơn. Nhưng ngay cả generic nguồn đúng cũng không biến mảng `Object[]` của dạng không tham số thành mảng `String[]`.

### 6.3. Biến Object[] vẫn có thể giữ một mảng hẹp

~~~java
Object[] target = new Integer[4];
numbers.toArray(target);
~~~

Biến `target` có kiểu nhìn từ mã là `Object[]`. Đối tượng thật sự vẫn là `Integer[]` nên vẫn không chứa được `Double`. Độ dài bốn đủ để chứa hai số không giải quyết được lỗi kiểu.

Phải tách hai điều kiện:

~~~text
đủ số ô?
    có thể chép đủ số phần tử

đúng kiểu thành phần?
    có thể lưu từng giá trị vào những ô ấy
~~~

Cả hai cần thỏa. Một mảng có rất nhiều ô nhưng kiểu quá hẹp vẫn không phù hợp. Một mảng đúng kiểu nhưng thiếu ô có thể được thay bằng mảng mới, vì phép tạo mảng mới vẫn giữ cùng kiểu thành phần mà bên gọi đã yêu cầu.

### 6.4. Nguồn rỗng và nguồn chỉ có null không kiểm tra được mọi lỗi kiểu

Nếu nguồn không có phần tử nào không tương thích, lỗi lưu kiểu không xuất hiện chỉ vì tham số generic trông khác nhau. Nguồn rỗng không có giá trị để lưu; `null` cũng có thể lưu vào mảng tham chiếu.

Vì vậy, kiểm thử duy nhất bằng danh sách rỗng có thể bỏ sót lỗi mà dữ liệu thật gây ra. Hãy thêm ít nhất một giá trị thuộc loại không phù hợp khi kiểm tra nhánh `ArrayStoreException`. Ngược lại, đừng xem `null` trong một danh sách cho phép `null` là nguyên nhân mặc định của lỗi lưu mảng.

## 7. Dạng hàm tạo mảng: rõ kiểu nhưng không bỏ kiểm tra

Cách viết:

~~~java
String[] result = names.toArray(String[]::new);
~~~

dùng một tham chiếu hàm tạo mảng. Khi nhận độ dài `n`, `String[]::new` có thể tạo một `String[n]`. Nó diễn tả rõ kiểu thành phần mong muốn mà không cần ép kết quả của `toArray()`.

Có thể chọn kiểu giao diện nếu các phần tử đều phù hợp:

~~~java
CharSequence[] result = names.toArray(CharSequence[]::new);
~~~

Các `String` lưu được trong `CharSequence[]`. Tuy nhiên, hàm tạo mảng không phải hàm chuyển đổi từng phần tử. Lệnh `names.toArray(Integer[]::new)` vẫn yêu cầu chép các chuỗi vào mảng `Integer[]` và gây `ArrayStoreException`.

Cách cài đặt mặc định của `Collection.toArray(IntFunction)` gọi hàm tạo với `0` rồi chuyển mảng đó cho dạng `toArray(T[])`. Đây là chi tiết được nêu cho cách cài đặt mặc định, không phải lý do để viết một hàm tạo bỏ qua độ dài hoặc tạo tác dụng phụ. Hãy dùng một hàm tạo trả về mảng mới đúng kiểu và đúng độ dài được yêu cầu. [Java SE 17 — Collection.toArray(IntFunction)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#toArray(java.util.function.IntFunction)).

Không nên viết chương trình phụ thuộc vào giả định rằng mọi lớp triển khai đều sẽ gọi hàm tạo đúng một lần với kích thước bằng `size()`. API có thể có cách cài đặt riêng; đối với tác vụ chuyển đổi, tham chiếu hàm tạo thuần như `String[]::new` tránh được sự phụ thuộc không cần thiết ấy.

Bài này không kết luận rằng một cách viết luôn nhanh hơn cách khác. Quy tắc kiểu dữ liệu, giá trị trả về và khả năng dùng lại mảng cần được hiểu trước; so sánh hiệu năng chỉ có ý nghĩa khi đo trên phiên bản JDK và tải cụ thể.

## 8. Đừng nhầm ba ý nghĩa của null

| Vị trí của null | Ý nghĩa | Cách xử lý |
| --- | --- | --- |
| Một phần tử của nguồn | Dữ liệu nullable nếu cấu trúc cho phép | Chép như một giá trị tham chiếu hợp lệ; bên đọc phải xử lý |
| Ô ngay sau dữ liệu trong mảng đích dư chỗ | Ranh giới do `toArray(T[])` quy định | Không tính ô đó thành phần tử mới của nguồn |
| Chính tham số mảng hoặc hàm tạo bị null | Thiếu đối tượng để xác định/tạo mảng đích | `NullPointerException` |

Ví dụ nguồn có một phần tử `null` hoàn toàn khác với lời gọi:

~~~java
names.toArray((String[]) null);
~~~

Đây là đoạn minh họa gây `NullPointerException` vì chính mảng đích là `null`. Nó không yêu cầu “tạo một mảng chuỗi mặc định” và cũng không tạo danh sách có một `null`.

Tương tự, tham số hàm tạo `null` là không hợp lệ. Với Java 17 có cả hai overload một tham số, lệnh `names.toArray(null)` còn có thể gây lỗi chọn overload ở lúc biên dịch, vì `null` có thể khớp cả kiểu mảng lẫn kiểu `IntFunction`. Trong ví dụ kiểm tra ngoại lệ, bài ép kiểu tham số `null` một cách tường minh để kiểm tra đúng overload đang nói tới.

Tránh dùng `null` như một tín hiệu ngầm để chọn kiểu mảng. Hãy truyền `new String[0]`, `String[]::new` hoặc dùng dạng không tham số theo hợp đồng đầu ra cần có.

## 9. Lỗi khi chép vào mảng dùng lại có thể để lại dữ liệu dở dang

Nếu cung cấp một mảng đủ chỗ, phép chuyển đổi có thể đang ghi các phần tử thì gặp một giá trị không tương thích. Không nên xem `ArrayStoreException` như một cam kết rằng mảng đầu vào chắc chắn chưa bị thay đổi.

Ca minh họa dùng `ArrayList`:

~~~java
List<Object> mixed = new ArrayList<>(List.of("ok", 42, "later"));
String[] reused = {"old0", "old1", "old2", "old3", "old4"};

mixed.toArray(reused);
~~~

Phần tử `"ok"` có thể được chép vào ô đầu. Khi đến `42`, không thể lưu `Integer` vào `String[]`. Trong chương trình được chạy, sau khi bắt ngoại lệ, mảng còn:

~~~text
[ok, old1, old2, old3, old4]
~~~

Không được coi `reused[3]` là dấu kết thúc hợp lệ của một lần chuyển đổi thành công: lời gọi đã thất bại trước khi hoàn tất.

Mã nguồn OpenJDK 17 của `ArrayList.toArray(T[])` dùng phép chép mảng khi tái sử dụng vùng đích. Hành vi của `System.arraycopy` với phần tử tham chiếu không tương thích có thể chép được đoạn đầu rồi mới ném lỗi. [OpenJDK 17 — ArrayList.java](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/ArrayList.java), [Java SE 17 — System.arraycopy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#arraycopy(java.lang.Object,int,java.lang.Object,int,int)).

Đây là giải thích cho ca thử cụ thể, không phải cam kết rằng mọi loại `Collection` phải để lại cùng một đoạn đầu khi lỗi.

Nếu mảng cũ đang là dữ liệu đã công bố và không được phép bị ghi dở, có thể tạo một kết quả mới trước:

~~~java
String[] next = source.toArray(String[]::new);
// Chỉ sử dụng hoặc công bố next sau khi lời gọi thành công.
~~~

`source` trong đoạn này là nguồn do ứng dụng cung cấp. Nếu lời gọi ném lỗi, không có giá trị `next` mới được gán bởi lần gọi đó. Thiết kế vẫn cần kiểm soát các tác dụng phụ khác và chính sách truy cập đồng thời nếu có; phép chuyển đổi đơn lẻ không cung cấp một giao dịch cho toàn bộ chương trình.

## 10. Mảng kết quả riêng không có nghĩa là sao chép sâu

Với cách tạo mảng kết quả mới, thay vị trí mảng không thay vị trí trong danh sách:

~~~java
String[] snapshot = names.toArray(String[]::new);
snapshot[0] = "Other";
~~~

`names` vẫn chứa `[Lan, An]`. Mảng `snapshot` có những vị trí riêng và trở thành `[Other, An]`.

Nhưng `toArray` chép các tham chiếu phần tử, không tự nhân bản từng đối tượng. Với phần tử sửa được:

~~~java
StringBuilder item = new StringBuilder("seed");
List<StringBuilder> items = List.of(item);
StringBuilder[] result = items.toArray(StringBuilder[]::new);

result[0].append("!");
~~~

`items.get(0)` cũng biểu diễn `seed!` vì cùng đối tượng `StringBuilder` đã bị sửa. Nếu thay `result[0]` bằng một `StringBuilder` khác, chỉ vị trí mảng kết quả thay đổi.

Sự khác biệt này liên quan trực tiếp tới cách hiểu “kiểu thực”: đối tượng mảng có kiểu thành phần riêng, và mỗi phần tử trong đó lại là một tham chiếu tới một đối tượng riêng. Kiểm tra kiểu khi lưu bảo đảm tham chiếu phù hợp loại mảng; nó không làm đối tượng phần tử trở nên bất biến.

Cũng cần phân biệt với `Arrays.asList(array)`: cách đó tạo một danh sách dùng chung các vị trí của mảng nền. `toArray` hướng ngược lại để lấy các phần tử ra mảng không nên bị hiểu là mọi phép chuyển đổi đều giữ cùng một cơ chế dùng chung.

## 11. Chương trình minh chứng đầy đủ

Lưu toàn bộ mã dưới đây thành **ToArrayRuntimeDemo.java**. Chương trình chỉ dùng thư viện chuẩn, tương thích Java 17.

`rejects` xác nhận lời gọi thất bại bằng loại ngoại lệ kỳ vọng; nó không chỉ in thông báo rồi bỏ qua mọi lỗi. Nếu không có ngoại lệ hoặc loại ngoại lệ khác, chương trình sẽ dừng để bộc lộ kết quả sai.

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;

public class ToArrayRuntimeDemo {
    private static void rejects(
            String label,
            Class<? extends RuntimeException> expected,
            Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " did not fail");
        } catch (RuntimeException ex) {
            if (!expected.isInstance(ex)) {
                throw ex;
            }
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        List<String> names = new ArrayList<>(List.of("Lan", "An"));

        Object[] plain = names.toArray();
        System.out.println("plain-type=" + plain.getClass().getTypeName());
        System.out.println("plain-values=" + Arrays.toString(plain));
        rejects("cast-plain", ClassCastException.class, () -> {
            String[] wrong = (String[]) plain;
            System.out.println(wrong.length);
        });

        String[] typed = names.toArray(new String[0]);
        System.out.println("typed-type=" + typed.getClass().getTypeName());
        System.out.println("typed-values=" + Arrays.toString(typed));

        String[] small = {"old"};
        String[] grown = names.toArray(small);
        System.out.println("small-reused=" + (grown == small));
        System.out.println("small-original=" + Arrays.toString(small));
        System.out.println("small-result=" + Arrays.toString(grown));

        String[] exact = {"old0", "old1"};
        String[] exactResult = names.toArray(exact);
        System.out.println("exact-reused=" + (exactResult == exact));
        System.out.println("exact-values=" + Arrays.toString(exactResult));

        String[] large = {"old0", "old1", "old2", "old3", "old4"};
        String[] largeResult = names.toArray(large);
        System.out.println("large-reused=" + (largeResult == large));
        System.out.println("large-values=" + Arrays.toString(largeResult));

        String[] emptyTarget = {"old0", "old1", "old2"};
        List.<String>of().toArray(emptyTarget);
        System.out.println("empty-values=" + Arrays.toString(emptyTarget));

        List<String> nullable = Arrays.asList("A", null, "B");
        String[] nullableTarget = {"old0", "old1", "old2", "old3", "old4"};
        nullable.toArray(nullableTarget);
        int firstNull = 0;
        while (firstNull < nullableTarget.length && nullableTarget[firstNull] != null) {
            firstNull++;
        }
        System.out.println("nullable-values=" + Arrays.toString(nullableTarget));
        System.out.println("nullable-size=" + nullable.size() + " / first-null=" + firstNull);

        Collection<Number> numbers = Arrays.<Number>asList(4, 2.5);
        Number[] numeric = numbers.toArray(new Number[0]);
        System.out.println("number-type=" + numeric.getClass().getTypeName());
        System.out.println("number-values=" + Arrays.toString(numeric));
        rejects("integer-target", ArrayStoreException.class,
                () -> numbers.toArray(new Integer[0]));

        Object[] wideReference = new Integer[4];
        System.out.println("wide-reference-type=" + wideReference.getClass().getTypeName());
        rejects("wide-reference-target", ArrayStoreException.class,
                () -> numbers.toArray(wideReference));

        List<Object> stringObjects = List.of("A", "B");
        String[] narrowed = stringObjects.toArray(new String[0]);
        System.out.println("broad-source-values=" + Arrays.toString(narrowed));

        String[] generated = names.toArray(String[]::new);
        System.out.println("generator-type=" + generated.getClass().getTypeName());
        CharSequence[] sequences = names.toArray(CharSequence[]::new);
        System.out.println("interface-type=" + sequences.getClass().getTypeName());
        rejects("wrong-generator", ArrayStoreException.class,
                () -> names.toArray(Integer[]::new));
        rejects("null-array", NullPointerException.class,
                () -> names.toArray((String[]) null));
        rejects("null-generator", NullPointerException.class,
                () -> names.toArray((IntFunction<String[]>) null));

        List<Object> mixed = new ArrayList<>(List.of("ok", 42, "later"));
        String[] reused = {"old0", "old1", "old2", "old3", "old4"};
        rejects("partial-copy", ArrayStoreException.class, () -> mixed.toArray(reused));
        System.out.println("partial-target=" + Arrays.toString(reused));

        String[] snapshot = names.toArray(String[]::new);
        snapshot[0] = "Other";
        System.out.println("detached-slots=" + names + " / " + Arrays.toString(snapshot));

        StringBuilder item = new StringBuilder("seed");
        List<StringBuilder> items = List.of(item);
        StringBuilder[] itemArray = items.toArray(StringBuilder[]::new);
        itemArray[0].append("!");
        System.out.println("shared-item=" + items.get(0));
        itemArray[0] = new StringBuilder("other");
        System.out.println("replaced-slot=" + items.get(0) + " / " + itemArray[0]);
    }
}
~~~

Biên dịch và chạy:

~~~shell
javac --release 17 -encoding UTF-8 ToArrayRuntimeDemo.java
java ToArrayRuntimeDemo
~~~

Stdout chính xác:

~~~text
plain-type=java.lang.Object[]
plain-values=[Lan, An]
cast-plain -> ClassCastException
typed-type=java.lang.String[]
typed-values=[Lan, An]
small-reused=false
small-original=[old]
small-result=[Lan, An]
exact-reused=true
exact-values=[Lan, An]
large-reused=true
large-values=[Lan, An, null, old3, old4]
empty-values=[null, old1, old2]
nullable-values=[A, null, B, null, old4]
nullable-size=3 / first-null=1
number-type=java.lang.Number[]
number-values=[4, 2.5]
integer-target -> ArrayStoreException
wide-reference-type=java.lang.Integer[]
wide-reference-target -> ArrayStoreException
broad-source-values=[A, B]
generator-type=java.lang.String[]
interface-type=java.lang.CharSequence[]
wrong-generator -> ArrayStoreException
null-array -> NullPointerException
null-generator -> NullPointerException
partial-copy -> ArrayStoreException
partial-target=[ok, old1, old2, old3, old4]
detached-slots=[Lan, An] / [Other, An]
shared-item=seed!
replaced-slot=seed! / other
~~~

Mã đã biên dịch bằng `--release 17` và chạy trên JDK 24.0.2. Cờ biên dịch xác minh mức ngôn ngữ và API Java 17, không đồng nghĩa đã chạy bằng JVM 17.

Đọc kết quả theo từng nhóm:

- `plain-type` và `cast-plain` xác nhận kiểu mảng do dạng không tham số tạo ra và lỗi khi ép sai kiểu.
- `typed-type` xác nhận mảng có kiểu thành phần cụ thể.
- `small`, `exact` và `large` phân biệt tạo mảng mới với tái sử dụng; dấu `==` kiểm tra cùng đối tượng mảng, không kiểm tra nội dung.
- `empty-values` và `nullable-values` cho thấy `null` kết thúc phụ thuộc số phần tử đã chép, còn `null` trong dữ liệu không phải điểm dừng chung.
- `integer-target` và `wide-reference-target` chứng minh kiểu thật của mảng quyết định khả năng lưu giá trị.
- `broad-source-values` minh họa kiểu khai báo nguồn rộng vẫn có thể chứa toàn các chuỗi tương thích.
- `wrong-generator`, `null-array` và `null-generator` tách yêu cầu kiểu khỏi sự hợp lệ của tham số.
- `partial-target` là kết quả của ca lỗi cụ thể với `ArrayList`; không suy rộng thành bảo đảm về trạng thái sau lỗi của mọi cấu trúc.
- `detached-slots` và `shared-item` phân biệt vị trí mảng riêng với đối tượng phần tử được dùng chung.

Các phép chuyển đổi ở đây chạy tuần tự với nguồn ổn định. Không dùng kết quả này để suy ra ảnh chụp nhất quán khi một luồng khác đang sửa một collection không có hợp đồng đồng thời phù hợp.

## 12. Áp dụng đúng tại ranh giới API

Khi một phương thức nhận `String[]`, cách chuyển thường rõ nhất là tạo trực tiếp kiểu ấy:

~~~java
String[] arguments = names.toArray(String[]::new);
~~~

`arguments.length` phản ánh số tên trong ca chuyển đổi với hàm tạo chuẩn và nguồn ổn định. Bên nhận không phải quét tới `null` hoặc biết rằng một bộ đệm dư chỗ còn chứa dữ liệu cũ.

Nếu ứng dụng thật sự cần tái sử dụng mảng, cần giữ cả mảng trả về và số phần tử hợp lệ. Đoạn minh họa:

~~~java
// names ổn định trong suốt đoạn xử lý này.
int count = names.size();
String[] buffer = obtainBuffer();
buffer = names.toArray(buffer);
consumePrefix(buffer, count);
~~~

`obtainBuffer` và `consumePrefix` là các hàm do ứng dụng định nghĩa, nên đây không phải chương trình độc lập. Điều kiện “nguồn ổn định” quan trọng: gọi `size` rồi `toArray` là hai thao tác; nếu có thay đổi đồng thời không được phối hợp, không thể mặc nhiên coi số lượng cũ là độ dài dữ liệu của kết quả mới.

Hợp đồng `consumePrefix` cần nêu rằng chỉ các chỉ số `0` đến `count - 1` thuộc dữ liệu hợp lệ. Việc có một ô `null` sau dữ liệu không thay thế được thông tin số lượng khi nguồn có thể chứa `null`.

Ngoài ra, nếu bên nhận giữ mảng để dùng lâu dài, tái sử dụng cùng mảng đó cho lần chuyển đổi tiếp theo sẽ thay dữ liệu mà bên nhận đang thấy. Quyền sở hữu và thời gian sống của bộ đệm cần được quyết định riêng. `toArray` chỉ quy định phép chép; nó không tự quản lý các bên đang giữ tham chiếu mảng đích.

Khi xử lý số, chọn kiểu mảng chứa được dữ liệu hoặc chuyển đổi giá trị một cách tường minh. `Number[]` chứa được nhiều loại số; `Integer[]` chỉ phù hợp các giá trị có thể lưu như `Integer`. Không có overload `Collection.toArray` trực tiếp nhận `int[]` vì `T[]` ở đây là mảng của kiểu tham chiếu. Chuyển sang mảng nguyên thủy cần một bước chuyển đổi khác phù hợp mục tiêu, không phải ép kiểu mảng kết quả.

## 13. Thực hành có đáp án

### Bài 1 — Mọi phần tử đều là String có ép được không?

Cho `List<String> values = List.of("A", "B")`. Lệnh `String[] result = (String[]) values.toArray()` có hợp lệ lúc chạy không? Sửa thế nào?

**Đáp án:** Không, mảng của dạng `toArray()` có kiểu thực `Object[]` nên phép ép sang `String[]` gây `ClassCastException`. Dùng `values.toArray(String[]::new)` hoặc `values.toArray(new String[0])` để yêu cầu đúng kiểu mảng.

### Bài 2 — Bỏ qua giá trị trả về

Cho `String[] target = new String[1]` và danh sách có ba chuỗi. Sau `values.toArray(target)`, có thể đọc ba phần tử qua biến `target` không?

**Đáp án:** Không. Mảng ban đầu chỉ có một ô; `toArray` trả về mảng mới đủ chỗ nhưng biến `target` chưa được gán lại. Cần nhận giá trị trả về, ví dụ `target = values.toArray(target)`. Không thể làm một đối tượng mảng tự dài ra bằng cách gọi phương thức.

### Bài 3 — Điền chính xác ô kết thúc

Danh sách `[A, B]` được chép vào `String[] target = {"x0", "x1", "x2", "x3"}`. Ô nào bắt buộc được đặt `null` sau khi chuyển thành công? Có bảo đảm mọi ô dư đều được xóa không?

**Đáp án:** Ô chỉ số `2` là vị trí ngay sau hai phần tử và được đặt `null`. Hợp đồng không yêu cầu xóa toàn bộ đuôi. Trong ca `ArrayList` minh chứng, ô `3` vẫn giữ giá trị cũ. Không được coi toàn bộ mảng bốn ô là bốn phần tử dữ liệu mới.

### Bài 4 — null đầu tiên có phải độ dài không?

Nguồn là `[A, null, B]`, mảng kết quả dư chỗ thành `[A, null, B, null, old4]`. Quét tới `null` đầu tiên cho kết quả `1`. Vì sao đây không phải độ dài nguồn?

**Đáp án:** `null` ở chỉ số `1` là một phần tử thật sự của nguồn, còn ô kết thúc nằm ở chỉ số `3`. Dữ liệu có ba phần tử. Chỉ được dùng cách quét tới `null` đầu tiên khi đã có bảo đảm rằng phần dữ liệu không chứa `null`; nếu không, giữ số lượng riêng hoặc lấy mảng đúng kích thước.

### Bài 5 — Object[] nhưng vẫn ArrayStoreException

Cho `Collection<Number> numbers = Arrays.<Number>asList(1, 2.5)` và `Object[] target = new Integer[5]`. Vì sao `numbers.toArray(target)` không chạy được dù mảng đủ dài?

**Đáp án:** Đối tượng mảng thật sự là `Integer[]`. Phần tử `Double` không thể lưu vào đó. `Object[]` chỉ là kiểu của biến tham chiếu; nó không đổi kiểu mảng thật thành `Object[]`. Chọn `Number[]` nếu cần giữ cả hai loại số.

### Bài 6 — Chuyển đổi bằng hàm tạo có đổi từng giá trị không?

`List<Integer> values = List.of(1, 2)` rồi gọi `values.toArray(String[]::new)` có tạo `["1", "2"]` không?

**Đáp án:** Không. Hàm tạo chỉ xác định mảng `String[]`, không gọi `toString` trên phần tử. Việc chép các `Integer` vào mảng chuỗi gây `ArrayStoreException`. Nếu cần chuỗi, phải thực hiện phép chuyển từng giá trị sang chuỗi rồi mới tạo mảng phù hợp.

### Bài 7 — Chép thất bại có giữ nguyên mảng dùng lại không?

Một `ArrayList<Object>` chứa `["ok", 42, "later"]` được chép vào `String[]` đủ chỗ. Sau `ArrayStoreException`, có được mặc nhiên dùng mảng đích như thể nó chưa bị thay đổi không?

**Đáp án:** Không. Ca thử của bài cho thấy phần tử đầu đã được ghi trước khi gặp số không phù hợp. Hợp đồng không cung cấp cơ chế quay lui toàn bộ. Nếu không được phép ghi dở vào mảng đã công bố, tạo kết quả mới rồi chỉ sử dụng nó khi phép chuyển đổi thành công.

### Bài 8 — Mảng riêng có bảo vệ đối tượng bên trong không?

Danh sách chứa một `StringBuilder` được chuyển sang `StringBuilder[]` mới. Gọi `array[0].append("!")` rồi `array[0] = new StringBuilder("other")`. Hai thao tác ảnh hưởng danh sách gốc khác nhau thế nào?

**Đáp án:** `append` sửa đối tượng `StringBuilder` đang được cả hai nơi dùng chung, nên đọc phần tử ở danh sách cũng thấy thay đổi. Phép gán sau chỉ thay vị trí số `0` của mảng kết quả; nó không thay vị trí trong danh sách. `toArray` không tự tạo bản sao sâu cho phần tử.

## 14. Những câu hỏi cần trả lời trước khi chuyển đổi

Trước khi gọi `toArray`, hãy xác định kiểu mảng mà API đích cần, những loại phần tử thực tế có thể xuất hiện, và việc có cần tái sử dụng một mảng cũ hay không. Khi dùng mảng cũ, luôn nhận giá trị trả về và phân biệt độ dài vùng lưu trữ với số phần tử dữ liệu.

Sau lời gọi thành công, ô `null` ngay sau dữ liệu chỉ xuất hiện theo quy tắc mảng dư chỗ; nó không xóa mọi ô dư và không thể phân biệt độ dài bằng cách quét nếu dữ liệu vốn chứa `null`. Khi lời gọi thất bại, đừng giả định một mảng được dùng lại đã tự khôi phục.

Mô hình cần giữ là: **kiểu của đối tượng mảng quyết định giá trị nào lưu được; kích thước quyết định dùng lại hay tạo mới; số phần tử đã chép quyết định vị trí `null` kết thúc.** Ba điều này cùng giải thích hành vi chuyển đổi, thay vì dựa vào tên biến hoặc chỉ nhìn các giá trị đang in ra.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="array-conversion-006">Chuyển List sang Set và hệ quả với trùng lặp hoặc thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển List sang Set và hệ quả với trùng lặp hoặc thứ tự

## 1. Từ danh sách các lần xuất hiện sang tập các giá trị

Một `List` trả lời được cả hai câu hỏi: “có những phần tử nào?” và “chúng xuất hiện ở vị trí nào, bao nhiêu lần?”. Một `Set` tập trung vào việc một phần tử có thuộc tập hay không; nó không lưu nhiều phần tử được xem là trùng nhau theo quy tắc của tập đó.

Vì vậy, chuyển `List` sang `Set` là một quyết định về ý nghĩa dữ liệu. Dãy thao tác của người dùng, danh sách mặt hàng đã quét hoặc lịch sử trạng thái có thể chứa những lần lặp hoàn toàn hợp lệ. Nếu mỗi lần xuất hiện có ý nghĩa riêng, loại trùng có thể làm mất thông tin cần thiết.

Ví dụ, danh sách tên trái cây sau có năm lần xuất hiện:

```text
[pear, apple, pear, banana, apple]
```

Nếu yêu cầu là “các tên đã xuất hiện, giữ tên nào gặp trước đứng trước”, kết quả phù hợp là:

```text
[pear, apple, banana]
```

Nếu yêu cầu là “các tên khác nhau theo thứ tự tự nhiên của chuỗi”, kết quả là:

```text
[apple, banana, pear]
```

Hai kết quả cùng có ba tên, nhưng cung cấp hai cách duyệt khác nhau. Cả hai đều không còn cho biết rằng `pear` và `apple` từng xuất hiện hai lần.

Có ba quyết định cần làm rõ trước khi viết lệnh chuyển đổi:

1. **Trùng theo tiêu chí nào?** Hai đối tượng có bằng nhau theo `equals`, hay chỉ có cùng một mã nghiệp vụ?
2. **Duyệt theo thứ tự nào?** Không cần thứ tự, giữ lần xuất hiện đầu tiên hay sắp xếp?
3. **Thông tin nào phải giữ lại?** Chỉ cần giá trị duy nhất, hay còn cần số lần, vị trí và đối tượng đại diện?

Bài này lần lượt trả lời ba câu hỏi đó bằng các chương trình Java độc lập. Mỗi chương trình được lưu vào tệp có tên trùng với `public class`, biên dịch bằng JDK 17 trở lên. Các lệnh dùng `--release 17` để giữ mã trong phạm vi Java 17.

## 2. Chọn kiểu Set theo ý nghĩa thứ tự

`Set` là giao diện. Khai báo biến là `Set<String>` không tự quyết định thứ tự duyệt; lớp thực tế và cách tạo kết quả mới quyết định điều đó.

| Nhu cầu | Cách tạo từ List | Quy tắc trùng | Thứ tự duyệt |
| --- | --- | --- | --- |
| Chỉ cần tập giá trị duy nhất | `new HashSet<>(source)` | `equals` cùng hợp đồng `hashCode` | Không được bảo đảm |
| Giữ thứ tự gặp giá trị lần đầu | `new LinkedHashSet<>(source)` | `equals` cùng hợp đồng `hashCode` | Theo lần thêm đầu tiên |
| Vừa loại trùng vừa sắp xếp tự nhiên | `new TreeSet<>(source)` | So sánh tự nhiên trả về `0` | Theo thứ tự tự nhiên |
| Sắp xếp bằng tiêu chí riêng | Tạo `TreeSet` với `Comparator`, rồi `addAll(source)` | Bộ so sánh trả về `0` | Theo bộ so sánh |
| Tạo tập không cho sửa cấu trúc, không cần thứ tự | `Set.copyOf(source)` | Các phần tử bằng nhau theo `equals` | Không được bảo đảm |

Trong bảng, `source` là một `List` hợp lệ. Cách viết là những biểu thức minh họa; các chương trình đầy đủ ở dưới có đủ khai báo và `import`.

### HashSet không cam kết giữ thứ tự của List

`HashSet` không bảo đảm thứ tự duyệt, kể cả khi một lần chạy tình cờ cho ra đúng thứ tự mong muốn. “Không được bảo đảm” cũng không có nghĩa là mỗi lần chạy chắc chắn đổi thứ tự hoặc được xáo ngẫu nhiên. Chương trình phải đúng với mọi thứ tự mà hợp đồng của tập cho phép. [Javadoc HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)

Do đó, không dùng chuỗi in trực tiếp từ `HashSet` làm bằng chứng rằng việc chuyển đổi giữ thứ tự. Nếu cần kiểm tra các giá trị trong một ví dụ, có thể sắp xếp riêng để hiển thị, đồng thời nói rõ đó là một bước bổ sung.

### LinkedHashSet giữ lần xuất hiện đầu tiên

Khi duyệt một `List` và đưa các phần tử vào `LinkedHashSet`, phần tử mới được thêm ở cuối thứ tự duyệt. Thêm lại một phần tử đã có không chuyển nó xuống cuối. Vì vậy, dãy `[B, A, B, C, A]` trở thành `[B, A, C]`. [Javadoc LinkedHashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html)

Đây là thứ tự của những giá trị gặp lần đầu, không phải thứ tự của những lần xuất hiện cuối cùng. Nếu cần một chính sách khác, phải mô tả và thực hiện chính sách đó riêng.

### TreeSet áp dụng thứ tự sắp xếp

`TreeSet` không giữ thứ tự gặp trong `List`. Nó tổ chức phần tử theo thứ tự tự nhiên hoặc `Comparator` được cung cấp. Cùng với thứ tự, kết quả so sánh còn quyết định những phần tử nào bị coi là trùng; chi tiết này sẽ được kiểm chứng ở phần 6.

## 3. Thí nghiệm: cùng một List, ba lựa chọn về thứ tự

Lưu chương trình sau vào `ListToSetOrderDemo.java`.

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ListToSetOrderDemo {
    public static void main(String[] args) {
        List<String> source = new ArrayList<>(
                List.of("pear", "apple", "pear", "banana", "apple"));
        Set<String> hashed = new HashSet<>(source);
        Set<String> firstOrder = new LinkedHashSet<>(source);
        Set<String> sorted = new TreeSet<>(source);
        Set<String> collected = source.stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        System.out.println("source=" + source);
        System.out.println("sourceSize=" + source.size());
        System.out.println("hashSize=" + hashed.size());
        System.out.println("hashSortedForDisplay=" + new TreeSet<>(hashed));
        System.out.println("firstOrder=" + firstOrder);
        System.out.println("sorted=" + sorted);
        System.out.println("collected=" + collected);

        List<String> uniqueList = new ArrayList<>(firstOrder);
        System.out.println("uniqueList=" + uniqueList);
        System.out.println("restoresSource=" + uniqueList.equals(source));
        source.add("kiwi");
        System.out.println("setContainsNewSourceValue="
                + firstOrder.contains("kiwi"));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 ListToSetOrderDemo.java
java ListToSetOrderDemo
```

Stdout:

```text
source=[pear, apple, pear, banana, apple]
sourceSize=5
hashSize=3
hashSortedForDisplay=[apple, banana, pear]
firstOrder=[pear, apple, banana]
sorted=[apple, banana, pear]
collected=[pear, apple, banana]
uniqueList=[pear, apple, banana]
restoresSource=false
setContainsNewSourceValue=false
```

Đọc kết quả theo từng thay đổi về dữ liệu:

- `sourceSize=5`, còn `hashSize=3`: các lần xuất hiện lặp đã được gộp.
- `firstOrder=[pear, apple, banana]`: `LinkedHashSet` giữ thứ tự gặp ba tên lần đầu.
- `sorted=[apple, banana, pear]`: `TreeSet` sắp xếp tên theo thứ tự tự nhiên của `String`.
- `collected` trùng với `firstOrder`: chương trình đã yêu cầu rõ `LinkedHashSet` khi thu thập phần tử từ luồng.
- `restoresSource=false`: chuyển tập trở lại `List` tạo được một danh sách mới, nhưng không khôi phục các lần xuất hiện đã bỏ.
- `setContainsNewSourceValue=false`: thêm `kiwi` vào danh sách sau khi tạo tập không tự thêm nó vào tập.

Dòng `hashSortedForDisplay` dùng `new TreeSet<>(hashed)` để có kết quả hiển thị ổn định. Thứ tự `[apple, banana, pear]` trên dòng đó thuộc về `TreeSet` mới tạo, không phải một cam kết về thứ tự của `HashSet`.

Việc in `source` trước bước `source.add("kiwi")` cũng có chủ đích: dòng đầu mô tả đầu vào lúc chuyển đổi. Lệnh thêm ở cuối kiểm tra sự độc lập về cấu trúc sau đó.

### Vì sao Set sang List không phải phép đảo?

Xét ba danh sách:

```text
[B, A]
[B, A, B]
[B, B, A, A]
```

Cả ba đều có thể cho cùng một `LinkedHashSet` với thứ tự `[B, A]`. Từ tập kết quả, không có cách suy ra danh sách ban đầu là danh sách nào.

Các thông tin bị mất gồm:

- Số lần xuất hiện của mỗi giá trị.
- Vị trí của những lần xuất hiện bị gộp.
- Chuỗi đầy đủ ban đầu.
- Những đối tượng bằng nhau đã không được giữ lại.

Nếu chỉ cần một `List` không trùng và giữ thứ tự gặp đầu tiên, cách `new ArrayList<>(new LinkedHashSet<>(source))` đáp ứng được. Danh sách kết quả vẫn có thể thay đổi và có truy cập theo chỉ số, nhưng chỉ chứa các giá trị đã chọn sau loại trùng.

## 4. “Trùng nhau” không đồng nghĩa với “cùng một đối tượng”

Với `HashSet` và `LinkedHashSet`, hai tham chiếu khác nhau vẫn có thể được xem là cùng một phần tử nếu các đối tượng bằng nhau theo `equals`. `hashCode` phải tuân thủ hợp đồng: đối tượng bằng nhau phải có cùng mã băm. Các đối tượng có cùng mã băm chưa chắc bằng nhau; va chạm băm không có nghĩa là tập tự gộp mọi đối tượng trong cùng một nhóm băm.

Điều này ảnh hưởng trực tiếp đến chuyển đổi dữ liệu nghiệp vụ. Giả sử hai đối tượng liên hệ có cùng `id` nhưng khác nhãn hiển thị. Nếu `equals` chỉ dựa trên `id`, tập có thể chỉ giữ một đối tượng. Tập không tự kết hợp các nhãn và không tự quyết định rằng bản ghi sau mới hơn nên phải thay bản ghi trước.

Lớp tự định nghĩa cũng cần có đúng tiêu chí bằng nhau. Nếu lớp dùng `equals` mặc định từ `Object`, hai đối tượng được tạo riêng thường vẫn khác nhau, dù các trường đang chứa cùng dữ liệu. Chuyển sang tập không tự đọc tất cả các trường để suy ra một khóa nghiệp vụ.

### Thí nghiệm về đối tượng đại diện và việc dùng chung tham chiếu

Lưu vào `SetEqualityAndSharingDemo.java`.

```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SetEqualityAndSharingDemo {
    static final class Contact {
        private final int id;
        private String label;

        Contact(int id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Contact contact && id == contact.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    public static void main(String[] args) {
        Contact earlier = new Contact(1, "earlier");
        Contact later = new Contact(1, "later");
        Contact other = new Contact(2, "other");
        List<Contact> source =
                new ArrayList<>(List.of(earlier, later, other));
        Set<Contact> result = new LinkedHashSet<>(source);
        Contact retained = result.iterator().next();

        System.out.println("sourceSize=" + source.size());
        System.out.println("setSize=" + result.size());
        System.out.println("equalById=" + earlier.equals(later));
        System.out.println("retainedIsEarlier=" + (retained == earlier));
        System.out.println("retainedLabel=" + retained.label);

        earlier.label = "edited";
        source.clear();
        System.out.println("labelAfterEdit=" + retained.label);
        System.out.println("setSizeAfterSourceClear=" + result.size());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SetEqualityAndSharingDemo.java
java SetEqualityAndSharingDemo
```

Stdout:

```text
sourceSize=3
setSize=2
equalById=true
retainedIsEarlier=true
retainedLabel=earlier
labelAfterEdit=edited
setSizeAfterSourceClear=2
```

Hai đối tượng `earlier` và `later` có cùng `id=1`, nên `equals` trả về `true`. `LinkedHashSet` nhận `earlier` trước. Lần thêm `later` không làm tập có phần tử thứ ba và không thay thế đối tượng đang giữ.

Vì thế:

- `retainedIsEarlier=true` kiểm tra trực tiếp danh tính đối tượng bằng `==`.
- `retainedLabel=earlier` cho thấy dữ liệu từ đối tượng bằng nhau xuất hiện sau không tự ghi đè.
- `labelAfterEdit=edited` cho thấy phần tử trong tập vẫn là chính đối tượng được tham chiếu từ danh sách trước đó.
- `setSizeAfterSourceClear=2` cho thấy xóa cấu trúc danh sách không xóa cấu trúc tập đã tạo.

Ví dụ cố ý giữ `id` là `final`; trường `label` không tham gia `equals` hoặc `hashCode`. Đổi `label` vì thế không làm thay đổi tiêu chí nhận diện phần tử trong tập.

Nếu một trường tham gia `equals` hoặc `hashCode` thay đổi trong khi đối tượng còn nằm trong tập băm, các thao tác tìm kiếm hoặc loại bỏ có thể không còn hoạt động như mong đợi. Không dùng việc chuyển sang `HashSet` hay `LinkedHashSet` để “đóng băng” những trường này; tập không bảo vệ đối tượng phần tử khỏi thay đổi.

## 5. Container mới và bản sao sâu là hai việc khác nhau

Các constructor `new HashSet<>(source)`, `new LinkedHashSet<>(source)` và `new TreeSet<>(source)` tạo cấu trúc chứa mới. Tập kết quả không phải một khung nhìn cập nhật trực tiếp theo mọi lần thêm hoặc xóa ở danh sách nguồn.

Tuy nhiên, chúng không tạo bản sao mới của từng đối tượng phần tử:

```text
List nguồn ──tham chiếu──> đối tượng Contact
                              ↑
Set mới   ──tham chiếu─────────┘
```

Sơ đồ chỉ minh họa quan hệ tham chiếu. Một số phần tử trùng có thể không đi vào tập, nhưng phần tử được giữ lại vẫn là đối tượng đã có.

Từ đó cần tách hai yêu cầu:

1. **Độc lập về cấu trúc:** thêm hoặc xóa phần tử ở danh sách không tự thêm hoặc xóa phần tử ở tập mới.
2. **Độc lập về trạng thái phần tử:** sửa một đối tượng ở phía này không ảnh hưởng đối tượng được nhìn thấy ở phía kia.

Constructor chuyển đổi đáp ứng yêu cầu đầu. Muốn đáp ứng yêu cầu sau, phải dùng phần tử bất biến hoặc sao chép phần tử theo quy tắc của mô hình dữ liệu. Việc tạo một tập không cho sửa cấu trúc cũng không tự đáp ứng yêu cầu sau.

Nếu hai đối tượng bằng nhau nhưng mang các trường còn lại khác nhau, hãy xác định rõ cần giữ bản đầu, giữ bản cuối, từ chối trùng hay hợp nhất thông tin. Với yêu cầu hợp nhất theo khóa, một `Map` cùng chính sách xử lý xung đột thường diễn đạt ý định rõ hơn việc chỉ đưa các đối tượng vào `Set`.

## 6. TreeSet có thể loại phần tử theo một tiêu chí khác equals

Với tập được sắp xếp, phép so sánh làm hai việc: xác định vị trí theo thứ tự và xác định hai phần tử có chiếm cùng một vị trí hay không. Nếu kết quả so sánh là `0`, `TreeSet` xem chúng là trùng.

Điều đó tạo ra một bẫy khi dùng `Comparator` chỉ để “đổi cách hiển thị”. Chẳng hạn, so sánh chuỗi không phân biệt hoa thường khiến `"Java"`, `"java"` và `"JAVA"` rơi vào cùng một nhóm, dù `String.equals` xem chúng là ba chuỗi khác nhau.

Một `TreeSet` cần có thứ tự nhất quán với `equals` để tuân thủ đầy đủ hợp đồng chung của `Set`. Chương trình dưới đây cố ý dùng bộ so sánh không nhất quán với `String.equals` để quan sát hệ quả, không phải mẫu mặc định cho mọi tập chuỗi. [Javadoc TreeSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html)

Lưu vào `TreeSetDuplicateRuleDemo.java`.

```java
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class TreeSetDuplicateRuleDemo {
    public static void main(String[] args) {
        List<String> source = List.of("Java", "java", "JAVA", "SQL", "Java");
        Set<String> byEquals = new HashSet<>(source);
        Set<String> natural = new TreeSet<>(source);
        Set<String> caseInsensitive = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitive.addAll(source);

        Set<String> normalized = new LinkedHashSet<>();
        for (String value : source) {
            normalized.add(value.toLowerCase(Locale.ROOT));
        }

        System.out.println("equalsSize=" + byEquals.size());
        System.out.println("naturalSize=" + natural.size());
        System.out.println("natural=" + natural);
        System.out.println("ignoreCaseSize=" + caseInsensitive.size());
        System.out.println("ignoreCase=" + caseInsensitive);
        System.out.println("stringEquals=" + "Java".equals("java"));
        System.out.println("compareAsSame="
                + (String.CASE_INSENSITIVE_ORDER.compare("Java", "java") == 0));
        System.out.println("normalized=" + normalized);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TreeSetDuplicateRuleDemo.java
java TreeSetDuplicateRuleDemo
```

Stdout:

```text
equalsSize=4
naturalSize=4
natural=[JAVA, Java, SQL, java]
ignoreCaseSize=2
ignoreCase=[Java, SQL]
stringEquals=false
compareAsSame=true
normalized=[java, sql]
```

Trong đầu vào có năm lần xuất hiện. Chuỗi `"Java"` xuất hiện hai lần, còn ba cách viết `"Java"`, `"java"`, `"JAVA"` không bằng nhau theo `String.equals`.

Kết quả cần đọc như sau:

- `equalsSize=4`: `HashSet` chỉ gộp hai lần xuất hiện của đúng chuỗi `"Java"`.
- `naturalSize=4`: thứ tự tự nhiên của `String` cũng phân biệt những cách viết hoa thường đó.
- `natural=[JAVA, Java, SQL, java]`: đây là thứ tự tự nhiên của các chuỗi trong ví dụ, không phải quy tắc sắp xếp ngôn ngữ dành cho mọi giao diện người dùng.
- `ignoreCaseSize=2`: bộ so sánh không phân biệt hoa thường gộp ba cách viết của Java thành một phần tử.
- `stringEquals=false` nhưng `compareAsSame=true`: cùng một cặp chuỗi được hai cơ chế nhận diện khác nhau.
- `normalized=[java, sql]`: chương trình chủ động chuyển chuỗi sang chữ thường trước khi đưa vào `LinkedHashSet`.

### Chuẩn hóa khóa là một lựa chọn có chủ đích

Đoạn chuẩn hóa dùng `Locale.ROOT` để việc chuyển chữ thường không phụ thuộc ngôn ngữ mặc định của máy. Sau chuẩn hóa, tập chứa chính những chuỗi đã chuyển đổi, nên `equals` làm việc trên dạng đã chọn.

Đổi lại, kết quả không giữ nguyên cách viết ban đầu. Nếu cần giữ `"Java"` để hiển thị nhưng nhận diện trùng bằng khóa `"java"`, phải lưu cả khóa chuẩn hóa và giá trị đại diện, đồng thời quy định giữ cách viết đầu hay cách viết cuối.

Cũng không nên xem “chuyển chữ thường” là quy tắc chuẩn hóa phù hợp cho mọi loại dữ liệu. Mã định danh, tên người, địa chỉ và chuỗi hiển thị có thể có hợp đồng riêng. Điều cần rút ra là phải chọn quy tắc bằng nhau trước, rồi chọn cấu trúc thể hiện đúng quy tắc đó.

Một lỗi tương tự xảy ra khi dùng bộ so sánh chỉ xét độ dài chuỗi: hai tên khác nhau nhưng có cùng độ dài sẽ có kết quả so sánh `0`. Nếu mục tiêu chỉ là sắp các phần tử mà vẫn giữ mọi phần tử khác nhau, tiêu chí so sánh phải có cách phân biệt những trường hợp bằng nhau ở tiêu chí đầu, hoặc nên dùng `List` rồi sắp xếp thay vì biến thành `TreeSet`.

## 7. null và quyền sửa kết quả cũng thay đổi theo cách chuyển

Không có một quy tắc chung rằng mọi cách chuyển `List` sang `Set` đều chấp nhận `null`, hoặc mọi tập kết quả đều cho thêm phần tử.

| Cách tạo | Khi nguồn có nhiều null | Thêm hoặc xóa ở tập kết quả |
| --- | --- | --- |
| `new HashSet<>(source)` | Giữ tối đa một `null` | Được |
| `new LinkedHashSet<>(source)` | Giữ tối đa một `null`, ở vị trí gặp đầu tiên | Được |
| `new TreeSet<>(source)` theo thứ tự tự nhiên | Ném `NullPointerException` | Được nếu đã tạo thành công |
| `Set.copyOf(source)` | Ném `NullPointerException` | Không được |
| `Collections.unmodifiableSet(new LinkedHashSet<>(source))` | Tập nền giữ tối đa một `null` | Không được qua lớp bao |

Hàng về `TreeSet` nói riêng trường hợp thứ tự tự nhiên. Có thể cung cấp bộ so sánh xử lý `null` một cách tường minh; không suy ra rằng mọi cấu hình `TreeSet` đều có cùng cách xử lý.

`Set.copyOf` nhận một `Collection`, nên có thể nhận trực tiếp `List`. Nó chấp nhận phần tử trùng và giữ một phần tử đại diện tùy ý trong nhóm bằng nhau; nó không cam kết giữ đối tượng gặp trước. Kết quả không cho sửa cấu trúc, không chấp nhận phần tử `null` và không bảo đảm thứ tự duyệt. [Javadoc Set.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#copyOf(java.util.Collection))

Điểm “đại diện tùy ý” quan trọng khi phần tử là đối tượng như `Contact` trong phần trước: hai đối tượng có thể bằng nhau theo mã nhưng mang nhãn khác nhau. Không thay `new LinkedHashSet<>(source)` bằng `Set.copyOf(source)` rồi vẫn dựa vào việc giữ đúng đối tượng đầu.

### Thí nghiệm về null, loại trùng và tập chỉ đọc

Lưu vào `SetNullAndReadOnlyDemo.java`.

```java
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class SetNullAndReadOnlyDemo {
    public static void main(String[] args) {
        List<String> withNull = Arrays.asList("A", null, "A", null, "B");
        Set<String> firstOrder = new LinkedHashSet<>(withNull);
        Set<String> hashed = new HashSet<>(withNull);
        System.out.println("firstOrder=" + firstOrder);
        System.out.println("hashSize=" + hashed.size());
        System.out.println("hashContainsNull=" + hashed.contains(null));

        try {
            new TreeSet<>(withNull);
        } catch (NullPointerException e) {
            System.out.println("naturalTree=NullPointerException");
        }
        try {
            Set.copyOf(withNull);
        } catch (NullPointerException e) {
            System.out.println("copyWithNull=NullPointerException");
        }

        Set<String> readOnly = Set.copyOf(List.of("B", "A", "B"));
        System.out.println("copySize=" + readOnly.size());
        System.out.println("copySortedForDisplay=" + new TreeSet<>(readOnly));
        try {
            readOnly.add("C");
        } catch (UnsupportedOperationException e) {
            System.out.println("copyAdd=UnsupportedOperationException");
        }

        Set<String> orderedReadOnly = Collections.unmodifiableSet(
                new LinkedHashSet<>(List.of("B", "A", "B")));
        System.out.println("orderedReadOnly=" + orderedReadOnly);
        try {
            Set.of("A", "A");
        } catch (IllegalArgumentException e) {
            System.out.println("ofDuplicate=IllegalArgumentException");
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SetNullAndReadOnlyDemo.java
java SetNullAndReadOnlyDemo
```

Stdout:

```text
firstOrder=[A, null, B]
hashSize=3
hashContainsNull=true
naturalTree=NullPointerException
copyWithNull=NullPointerException
copySize=2
copySortedForDisplay=[A, B]
copyAdd=UnsupportedOperationException
orderedReadOnly=[B, A]
ofDuplicate=IllegalArgumentException
```

Trong phần đầu, danh sách nguồn được tạo bằng `Arrays.asList` để có thể chứa `null`. Dùng `List.of` tại vị trí này sẽ khiến chương trình gặp lỗi ngay khi tạo nguồn, trước khi kiểm chứng cách chuyển sang tập.

Kết quả xác nhận:

1. `LinkedHashSet` giữ `[A, null, B]`: hai `A` được gộp và hai `null` cũng được gộp.
2. `HashSet` có ba phần tử và chứa `null`. Chương trình kiểm tra kích thước và khả năng chứa thay vì dự đoán thứ tự in.
3. `TreeSet` theo thứ tự tự nhiên và `Set.copyOf` từ chối đầu vào có `null`.
4. `Set.copyOf(List.of("B", "A", "B"))` tạo được tập hai phần tử. Dòng `copySortedForDisplay` chỉ sắp xếp một bản phục vụ hiển thị.
5. Gọi `add` trên tập do `Set.copyOf` tạo ra ném `UnsupportedOperationException`.
6. Tập được bao bằng `Collections.unmodifiableSet` vẫn duyệt theo `LinkedHashSet` nền, nên cho `[B, A]`.
7. `Set.of("A", "A")` ném `IllegalArgumentException`; cách gọi này không có cùng chính sách với `Set.copyOf` khi gặp dữ liệu trùng.

Các khối `catch` trong chương trình in nhãn cố định của trường hợp đã bắt, thay vì in toàn bộ thông điệp ngoại lệ. Vì thế stdout không phụ thuộc đường dẫn máy hoặc chi tiết thông báo của bản JDK.

### “Không cho sửa cấu trúc” không có nghĩa là giữ thứ tự

Hai yêu cầu cần chọn riêng:

- `Set.copyOf` phù hợp khi cần một kết quả không cho sửa cấu trúc và không dựa vào thứ tự.
- Nếu cần giữ thứ tự gặp đầu tiên đồng thời không cho phía nhận thêm hoặc xóa, có thể tạo `LinkedHashSet` rồi bao bằng `Collections.unmodifiableSet`.

`Collections.unmodifiableSet` tạo một khung nhìn chỉ đọc của tập nền. Nếu một phần khác của chương trình vẫn giữ tập nền và sửa nó, khung nhìn phản ánh thay đổi đó. Vì vậy, để bảo vệ một ảnh chụp về cấu trúc, tạo bản sao nền riêng và không để lộ đường sửa bản sao đó. [Javadoc Collections.unmodifiableSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableSet(java.util.Set))

Ví dụ trong chương trình tạo `LinkedHashSet` mới ngay bên trong lời gọi bao. Danh sách nguồn không phải tập nền của lớp bao. Tuy nhiên, phần tử có thể vẫn được dùng chung; cả lớp bao lẫn `Set.copyOf` đều không tự sao chép sâu đối tượng.

## 8. Dùng Stream vẫn phải chọn hợp đồng về thứ tự

Nếu đang có sẵn `List` và chỉ cần chuyển đổi trực tiếp, constructor thường đủ rõ:

```java
// Đoạn minh họa; source là List<String> đã có.
Set<String> unique = new LinkedHashSet<>(source);
```

Nếu còn cần lọc hoặc biến đổi phần tử trong một luồng, phải chọn cách thu thập phù hợp:

```java
// Đoạn minh họa; đặt trong phương thức có source và các import cần thiết.
Set<String> uniqueInFirstOrder = source.stream()
        .collect(Collectors.toCollection(LinkedHashSet::new));
```

Với luồng có thứ tự từ `List` trong ví dụ, kết quả giữ thứ tự gặp các giá trị lần đầu. Chương trình `ListToSetOrderDemo` đã kiểm chứng đúng biểu thức này.

Không thay biểu thức đó bằng `Collectors.toSet()` khi thứ tự là một phần của yêu cầu. `toSet()` không cam kết kiểu tập cụ thể, quyền sửa hoặc thứ tự gặp. Muốn chỉ định lớp chứa, dùng `toCollection` với hàm tạo tương ứng. [Javadoc Collectors.toSet và toCollection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html)

Ví dụ, một bước lọc có thể thay đổi những phần tử được gặp; một bước sắp xếp có thể thay đổi thứ tự được gặp. `LinkedHashSet` chỉ giữ thứ tự thực tế được đưa vào nó, không tự tìm lại thứ tự của danh sách ở một giai đoạn trước trong chuỗi xử lý.

Việc chọn Stream hay constructor vì thế là lựa chọn cách biểu diễn luồng công việc. Nó không làm mất đi yêu cầu phải xác định tiêu chí trùng, thứ tự và quyền sửa kết quả.

## 9. Khi số lần xuất hiện vẫn quan trọng, giữ thêm dữ liệu

Chuyển sang `Set` không phải cách đếm. Nếu đầu vào là `[B, A, B, C, A, B]`, tập các giá trị duy nhất chỉ còn `[B, A, C]`. Từ tập đó không biết `B` xuất hiện ba lần, `A` hai lần và `C` một lần.

Cũng cần phân biệt hai con số:

- **Số lần xuất hiện bị bỏ:** kích thước danh sách trừ kích thước tập duy nhất.
- **Số giá trị bị lặp:** có bao nhiêu giá trị khác nhau xuất hiện từ hai lần trở lên.

Với đầu vào trên, có ba lần xuất hiện bị bỏ nhưng chỉ có hai giá trị bị lặp là `B` và `A`.

Phương thức `add` của tập băm trả về `false` khi phần tử đã có và tập không thay đổi. Có thể dùng kết quả đó ngay trong lúc duyệt để phát hiện trùng, thay vì loại trùng xong mới cố suy ngược dữ liệu nguồn.

Lưu chương trình sau vào `DuplicatesAndCountsDemo.java`.

```java
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DuplicatesAndCountsDemo {
    public static void main(String[] args) {
        List<String> source = List.of("B", "A", "B", "C", "A", "B");
        Set<String> seen = new LinkedHashSet<>();
        Set<String> repeated = new LinkedHashSet<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (String value : source) {
            if (!seen.add(value)) {
                repeated.add(value);
            }
            counts.put(value, counts.getOrDefault(value, 0) + 1);
        }

        System.out.println("sourceSize=" + source.size());
        System.out.println("uniqueFirstOrder=" + seen);
        System.out.println("repeatedValues=" + repeated);
        System.out.println("counts=" + counts);
        System.out.println("droppedOccurrences=" + (source.size() - seen.size()));
        System.out.println("repeatedValueCount=" + repeated.size());
        System.out.println("hasDuplicates=" + !repeated.isEmpty());
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 DuplicatesAndCountsDemo.java
java DuplicatesAndCountsDemo
```

Stdout:

```text
sourceSize=6
uniqueFirstOrder=[B, A, C]
repeatedValues=[B, A]
counts={B=3, A=2, C=1}
droppedOccurrences=3
repeatedValueCount=2
hasDuplicates=true
```

Chương trình duy trì ba cấu trúc với ba mục đích riêng:

| Biến | Thông tin được giữ | Với đầu vào trong chương trình |
| --- | --- | --- |
| `seen` | Những giá trị đã gặp, theo lần gặp đầu tiên | `[B, A, C]` |
| `repeated` | Những giá trị đã gặp lại ít nhất một lần | `[B, A]` |
| `counts` | Số lần xuất hiện của từng giá trị | `{B=3, A=2, C=1}` |

Ở vòng lặp:

1. `seen.add(value)` thử ghi nhận một giá trị mới.
2. Nếu kết quả là `false`, giá trị đó đã từng được gặp, nên thêm vào `repeated`.
3. `counts` tăng bộ đếm ở mọi lần xuất hiện, kể cả lần lặp.

`LinkedHashMap` được dùng để bảng đếm in theo thứ tự các khóa xuất hiện lần đầu. Việc cập nhật bộ đếm cho khóa đã có không biến thứ tự đó thành thứ tự xuất hiện cuối.

Trong ví dụ này, thứ tự của `repeated` là thứ tự phát hiện các giá trị bị lặp: gặp lại `B` trước, rồi gặp lại `A`. Không suy rộng rằng thứ tự này luôn giống thứ tự gặp giá trị lần đầu. Chẳng hạn, ở dãy `[A, B, B, A]`, giá trị được phát hiện lặp trước là `B`, dù `A` xuất hiện đầu tiên trong nguồn.

### Phát hiện đầu vào không hợp lệ khác với tự động loại trùng

Nếu hợp đồng nói “mỗi mã chỉ được gửi một lần”, chuyển thẳng sang tập rồi tiếp tục xử lý sẽ che mất vi phạm. Lúc này nên kiểm tra và báo lỗi rõ ràng.

Với cùng tiêu chí bằng nhau của `HashSet` hoặc `LinkedHashSet`, so sánh kích thước nguồn với kích thước tập có thể cho biết có ít nhất một lần lặp. Nếu cần báo mã nào lặp, cách duyệt với `add` như chương trình trên cho phép giữ bằng chứng cần thiết.

Nếu dùng `TreeSet` với bộ so sánh khác `equals`, kích thước giảm có thể do hai phần tử khác nhau theo `equals` nhưng được bộ so sánh xem là một. Vì vậy, không lấy một `TreeSet` bất kỳ để kiểm tra “trùng theo equals”.

Nếu cần báo đầy đủ vị trí lỗi, phải ghi thêm chỉ số trong lúc duyệt danh sách. Tập chỉ lưu giá trị duy nhất không chứa đủ thông tin vị trí cho việc báo lỗi này.

## 10. Chọn cách chuyển trong tình huống thực tế

### Danh sách nhãn để hiển thị theo thứ tự người dùng nhập

Yêu cầu: bỏ nhãn trùng nhưng giữ nhãn gặp trước, thứ tự hiển thị ổn định theo đầu vào.

Chọn `LinkedHashSet`. Nếu giao diện sau đó vẫn cần một `List`, tạo `ArrayList` từ tập như chương trình đầu. Thống nhất trước việc khoảng trắng hoặc chữ hoa thường có làm hai nhãn được xem là khác nhau hay không; constructor không tự chuẩn hóa những chi tiết này.

### Tập mã để kiểm tra thành viên

Yêu cầu: cần trả lời “mã này có xuất hiện không?”, không dùng thứ tự duyệt làm dữ liệu đầu ra.

`HashSet` thường là lựa chọn trực tiếp khi `equals` và `hashCode` phù hợp. Các thao tác cơ bản có chi phí kỳ vọng hằng số khi hàm băm phân bố tốt; việc chuyển n phần tử thường có chi phí kỳ vọng tuyến tính trong điều kiện đó. Đây không phải cam kết mọi đầu vào đều có cùng thời gian thực thi.

Nếu còn cần danh sách gốc để thể hiện từng lần thao tác, giữ cả danh sách và tập tra cứu với vai trò riêng. Việc có thêm tập hỗ trợ không buộc phải xóa danh sách.

### Các giá trị duy nhất cần sắp xếp

Yêu cầu: chỉ một phần tử cho mỗi giá trị theo quy tắc đã chọn, đồng thời duyệt có thứ tự.

Chọn `TreeSet` với thứ tự tự nhiên hoặc bộ so sánh phù hợp. Với cách thêm từng phần tử, các thao tác thêm có chi phí logarit theo kích thước tập; chuyển n phần tử thường được xét ở mức O(n log n), ngoài chi phí so sánh thực tế.

Nếu mọi lần xuất hiện đều phải còn, giữ `List` và sắp xếp danh sách. Sắp xếp không tự yêu cầu loại trùng.

### Dữ liệu thống kê hoặc giao dịch

Yêu cầu: cùng một mã có thể xuất hiện nhiều lần và mỗi lần ảnh hưởng kết quả.

Giữ `List`, hoặc tạo `Map` đếm số lần, hoặc giữ từng bản ghi với số lượng tương ứng. Chỉ giữ `Set` khi câu hỏi thực sự chỉ cần sự có mặt.

Ví dụ, một giỏ hàng biểu diễn mỗi lần quét là một phần tử không thể chuyển sang tập rồi dùng kích thước tập làm tổng số sản phẩm. Hai lần quét cùng mã có thể là hai đơn vị hàng, không phải dữ liệu lỗi.

### Danh sách đối tượng cần chọn bản ghi đại diện

Yêu cầu: cùng mã nghiệp vụ chỉ giữ một bản ghi nhưng các bản ghi có thể mang dữ liệu khác nhau.

Trước hết chọn chính sách: bản đầu, bản cuối, bản có thời gian mới nhất hay báo xung đột. `LinkedHashSet` phù hợp với việc giữ đối tượng bằng nhau gặp trước khi `equals` đúng tiêu chí. Những chính sách khác cần được biểu diễn rõ, thường bằng việc xử lý theo khóa trong `Map`.

Không để tình cờ của thứ tự nguồn hoặc cách triển khai tập trở thành chính sách nghiệp vụ mà người đọc mã không thấy.

## 11. Những lỗi cần nhận ra khi đọc hoặc sửa mã

| Cách nghĩ hoặc cách viết dễ sai | Hệ quả | Cách sửa theo yêu cầu |
| --- | --- | --- |
| “Set nào cũng giữ thứ tự List” | Thứ tự đầu ra phụ thuộc kiểu tập, có thể không được bảo đảm | Chọn `LinkedHashSet` khi cần lần xuất hiện đầu |
| “HashSet in đúng thứ tự ở máy tôi là đủ” | Đặt yêu cầu lên hành vi không có cam kết | Kiểm tra hợp đồng API; sắp xếp riêng nếu cần hiển thị có thứ tự |
| “Chuyển Set về List sẽ có lại nguồn” | Không thể khôi phục số lần hoặc vị trí đã bỏ | Giữ nguồn hoặc lưu thông tin cần thiết trước khi chuyển |
| “Cùng dữ liệu nhìn thấy thì Set tự gộp” | Lớp có thể chưa có `equals` và `hashCode` theo tiêu chí cần dùng | Định nghĩa tiêu chí bằng nhau hoặc chuyển sang khóa phù hợp |
| “Trùng mã nghĩa là giữ bản ghi cuối” | `LinkedHashSet` không tự thay đối tượng đầu bằng đối tượng sau | Dùng chính sách lựa chọn đại diện tường minh |
| “Comparator chỉ ảnh hưởng cách sắp xếp” | `compare(a, b) == 0` cũng quyết định loại trùng trong `TreeSet` | Kiểm tra tiêu chí so sánh và tính nhất quán với `equals` |
| “Set.copyOf nhận dữ liệu trùng thì phải ném lỗi” | Nhầm với `Set.of`; không hiểu phần tử đại diện | Phân biệt hai API và kiểm tra yêu cầu về trùng |
| “Có Set mới là phần tử đã được sao chép” | Sửa đối tượng có thể nhìn thấy từ cả hai cấu trúc | Dùng phần tử bất biến hoặc quy tắc sao chép riêng |
| “Tập chỉ đọc sẽ giữ thứ tự hiện có” | Quyền sửa và thứ tự là hai hợp đồng khác nhau | Chọn cả kiểu tập và cách bảo vệ kết quả |
| “Loại trùng xong vẫn đếm được số lần” | Chỉ còn sự có mặt, mất tần suất | Đếm khi còn dữ liệu gốc hoặc giữ bảng đếm |

Khi nguồn rỗng, các cách chuyển hợp lệ cho ra tập rỗng. Khi chính tham chiếu nguồn là `null`, đó là trường hợp khác với danh sách có một phần tử `null`; cần xử lý đầu vào theo hợp đồng của phương thức nhận dữ liệu. Không dùng quy tắc “HashSet chứa được null” để kết luận rằng constructor có thể nhận một `Collection` nguồn là `null`.

## 12. Thực hành: dự đoán đúng dữ liệu còn lại

### Bài 1: một nguồn, hai quy tắc thứ tự

Cho danh sách `[C, A, C, B, A]`. Dự đoán kết quả khi chuyển sang `LinkedHashSet` và `TreeSet` theo thứ tự tự nhiên. Có thể dự đoán chính xác thứ tự in trực tiếp của `HashSet` từ hợp đồng API hay không?

**Đối chiếu:**

- `LinkedHashSet`: `[C, A, B]`.
- `TreeSet`: `[A, B, C]`.
- `HashSet`: có ba phần tử `A`, `B`, `C`, nhưng không dự đoán thứ tự in dựa vào hợp đồng.
- Cả ba không còn cho biết số lần và vị trí xuất hiện của từng chữ trong nguồn.

### Bài 2: số lần bị bỏ và số giá trị bị lặp

Cho danh sách `[A, A, A, B, B, C]`. Nếu loại trùng theo `String.equals`, có bao nhiêu lần xuất hiện bị bỏ? Có bao nhiêu giá trị bị lặp?

**Đối chiếu:**

Nguồn có sáu phần tử và ba giá trị duy nhất, nên có ba lần xuất hiện bị bỏ. Hai giá trị bị lặp là `A` và `B`. Muốn biết riêng số lần của từng giá trị, cần nguồn hoặc bảng đếm; tập `[A, B, C]` không đủ.

### Bài 3: thứ tự phát hiện trùng

Thay đầu vào của `DuplicatesAndCountsDemo` bằng `List.of("A", "B", "B", "A")`. Trước khi chạy, viết kết quả của `seen`, `repeated` và `counts`.

**Đối chiếu:**

- `seen` có thứ tự `[A, B]`.
- `repeated` có thứ tự `[B, A]`, vì lần lặp của `B` được phát hiện trước.
- `counts` có nội dung và thứ tự `{A=2, B=2}`.
- Có hai lần xuất hiện bị bỏ và hai giá trị bị lặp.

Bài này kiểm tra việc không đánh đồng thứ tự gặp đầu tiên với thứ tự phát hiện lặp.

### Bài 4: chọn một đối tượng theo mã

Hai đối tượng liên hệ cùng `id=1` có nhãn `old` và `new`; nguồn đưa `old` trước. `equals` và `hashCode` chỉ dựa trên `id`. Sau khi chuyển sang `LinkedHashSet`, nhãn nào được giữ? Nếu muốn giữ `new`, đổi sang `Set.copyOf` có giải quyết được yêu cầu không?

**Đối chiếu:**

`LinkedHashSet` giữ đối tượng mang nhãn `old`. `Set.copyOf` không cam kết giữ đối tượng sau cùng nên không diễn đạt yêu cầu “giữ new”. Cần viết chính sách chọn bản ghi cuối hoặc bản ghi mới nhất một cách rõ ràng, chẳng hạn khi đưa dữ liệu vào `Map` theo `id`.

### Bài 5: dữ liệu không được phép mất lần xuất hiện

Danh sách `[P1, P1, P2]` biểu diễn ba lần quét hàng. Phần xử lý chỉ nhận tập `{P1, P2}`. Có thể tính đúng tổng ba đơn vị hàng từ tập đó mà không có dữ liệu khác không?

**Đối chiếu:**

Không. Tập này cũng có thể đến từ `[P1, P2]` hoặc rất nhiều danh sách dài hơn. Cần giữ các lần quét hoặc truyền kèm số lượng, ví dụ `P1 → 2`, `P2 → 1`.

### Bài 6: điều kiện nghiệm thu một phép chuyển

Tự chọn một đầu vào có phần tử lặp và viết chương trình kiểm chứng:

1. Kích thước trước và sau chuyển.
2. Quy tắc nhận diện trùng.
3. Thứ tự duyệt có phải yêu cầu bắt buộc hay không.
4. Đối tượng nào được giữ khi các đối tượng bằng nhau.
5. Kết quả có cho sửa cấu trúc không.
6. Thêm hoặc xóa ở nguồn có tác động đến tập không.
7. Sửa trạng thái đối tượng được dùng chung có tác động gì.

Một lời giải đạt yêu cầu phải giải thích được vì sao chọn kiểu tập đó. Chỉ in một tập có vẻ đúng trên một lần chạy chưa chứng minh được rằng chương trình giữ đúng hợp đồng về trùng lặp và thứ tự.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="array-conversion-007">Chuyển Map sang danh sách entry, khóa hoặc giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển Map sang danh sách entry, khóa hoặc giá trị

## 1. Chọn thông tin cần giữ trước khi chọn câu lệnh

`Map<K, V>` biểu diễn quan hệ từ khóa tới giá trị. `List<T>` biểu diễn một dãy phần tử có chỉ số. Khi chuyển từ Map sang danh sách, câu hỏi đầu tiên là **mỗi phần tử của danh sách cần chứa điều gì**.

Ví dụ một bảng điểm:

~~~text
B -> 20
A -> 10
C -> 20
~~~

Có ba kết quả khác mục đích:

| Kết quả | Dữ liệu minh họa | Thông tin còn giữ |
| --- | --- | --- |
| Danh sách khóa | `[B, A, C]` | Những mã nào đang tồn tại |
| Danh sách giá trị | `[20, 10, 20]` | Những điểm số đang được lưu, kể cả điểm trùng |
| Danh sách entry | `[B=20, A=10, C=20]` | Từng cặp mã và điểm đi cùng nhau |

Một entry là một cặp khóa–giá trị, được biểu diễn bằng `Map.Entry<K, V>`. Giữ entry giúp sắp xếp hoặc lọc cả dòng mà không làm mất việc điểm nào thuộc mã nào.

Nếu chỉ lấy giá trị rồi bỏ khóa, ta không thể tự suy ra rằng điểm `20` là của `B` hay `C`. Nếu lấy riêng khóa rồi sắp xếp mà không điều chỉnh dữ liệu tương ứng, việc ghép lại theo chỉ số có thể tạo những cặp chưa từng tồn tại trong Map.

Sau khi chọn phần thông tin cần giữ, cần quyết định thêm ba điều: thứ tự mong muốn, danh sách có được sửa hay không, và dữ liệu cần tách khỏi Map ở mức nào. Đây là các quyết định độc lập; một danh sách không cho thêm/xóa vẫn có thể chứa những đối tượng phần tử thay đổi được.

## 2. Ba khung nhìn của Map là nguồn để chuyển đổi

`Map` cung cấp ba khung nhìn:

~~~java
Set<K> keys = map.keySet();
Collection<V> values = map.values();
Set<Map.Entry<K, V>> entries = map.entrySet();
~~~

Đây là minh họa kiểu dữ liệu, không phải một chương trình độc lập; `K`, `V` và `map` thuộc ngữ cảnh khai báo của ứng dụng.

Các phương thức trên trả về những khung nhìn dùng chung Map nền. Nếu Map thay đổi, kết quả quan sát qua khung nhìn cũng thay đổi. Các thao tác xóa được hỗ trợ trên khung nhìn có thể xóa ánh xạ tương ứng khỏi Map. [Java SE 17 — Map collection views](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#keySet()).

Cần phân biệt:

~~~java
Set<String> keyView = scores.keySet();
List<String> keyList = new ArrayList<>(scores.keySet());
~~~

`keyView` vẫn là khung nhìn của `scores`. `keyList` có cấu trúc danh sách riêng, được điền bằng những khóa được duyệt lúc tạo.

Trong các ví dụ của bài, Map không bị sửa đồng thời trong lúc chuyển đổi. Nếu không giữ điều kiện này, không nên gọi mọi phép chép là “ảnh chụp tại cùng một thời điểm” mà chưa xem hợp đồng đồng thời của nguồn.

### Map không trực tiếp là Collection

Không thể dùng `new ArrayList<>(map)` như thể Map là một tập các phần tử thuộc một kiểu duy nhất. Hàm tạo từ collection cần nhận một `Collection`; Map cung cấp các khung nhìn để người viết chọn rõ cần khóa, giá trị hay entry.

`Arrays.asList(map)` cũng không phải phép trải Map thành các cặp. Với lời gọi một đối số như vậy, danh sách chứa một phần tử là chính đối tượng Map. Nó giải quyết một yêu cầu khác.

Hãy chọn một trong ba đường đi sau:

~~~text
Map<K,V>
   |-- keySet()  ------> danh sách K
   |-- values()  ------> danh sách V
   |-- entrySet() -----> danh sách cặp K,V
~~~

## 3. Danh sách khóa: sao chép các khóa đã có

Với một `Map<String, Integer>`:

~~~java
List<String> keys = new ArrayList<>(scores.keySet());
~~~

Danh sách chứa các tham chiếu khóa theo thứ tự nguồn được duyệt. Nếu Map nguồn là `LinkedHashMap` theo thứ tự chèn `B`, `A`, `C`, kết quả là `[B, A, C]`.

Sau khi chép:

- `keys.remove("B")` chỉ bỏ phần tử trong danh sách, không xóa khóa `B` của `scores`.
- `keys.add("X")` chỉ thêm vào danh sách, không tạo ánh xạ `X` trong Map.
- Nếu Map có thêm khóa `D`, danh sách đã chép không tự xuất hiện `D`.
- Có thể sắp xếp danh sách mà không sắp xếp lại cấu trúc Map.

Việc Map có khóa duy nhất không biến danh sách kết quả thành một `Set`. Sau khi đã có `ArrayList` riêng, người dùng vẫn có thể thêm một khóa trùng vào danh sách. Tính duy nhất của nguồn mô tả dữ liệu ban đầu; nó không trở thành quy tắc cấm phần tử trùng của mọi cấu trúc được tạo từ nguồn.

Sự độc lập ở đây nằm ở **các vị trí của danh sách**, không nhất thiết ở toàn bộ đối tượng khóa. Nếu khóa là một đối tượng sửa được, danh sách và Map vẫn giữ tham chiếu tới cùng đối tượng ấy. Đặc biệt, sửa khóa theo cách ảnh hưởng đến `equals` hoặc `hashCode` khi nó đang nằm trong Map có thể phá vỡ giả định của cấu trúc tìm kiếm. Chuyển sang danh sách không làm việc sửa khóa đó trở nên an toàn.

Khi chỉ cần danh sách mã để đưa vào một bộ chọn trên giao diện, đây thường là phép chuyển đơn giản và đúng thông tin. Nếu còn phải hiện tên hoặc điểm đi kèm từng mã, nên giữ cặp ngay từ đầu thay vì liên tục suy đoán quan hệ theo chỉ số.

## 4. Danh sách giá trị: giữ cả những giá trị trùng nhau

Cách tạo tương ứng:

~~~java
List<Integer> values = new ArrayList<>(scores.values());
~~~

Với ba ánh xạ `B=20`, `A=10`, `C=20`, danh sách có ba phần tử `[20, 10, 20]`. `values()` là một `Collection<V>` vì nhiều khóa có thể ánh xạ tới những giá trị bằng nhau.

Nếu tự đưa kết quả qua một `Set`, hai lần xuất hiện của `20` có thể bị gộp. Đó là một phép đổi ý nghĩa dữ liệu, không phải bước bắt buộc để chuyển Map thành danh sách.

Danh sách giá trị phù hợp khi cần tính tổng, thống kê hoặc chuyển dữ liệu sang một hàm chỉ làm việc với điểm số. Nhưng nó đã bỏ thông tin khóa. Muốn loại đúng học viên `B`, không nên chỉ xóa một phần tử `20` rồi cho rằng đã chỉ định được `B`.

Cũng cần tách phép sửa hai đối tượng sau:

~~~java
scores.values().remove(20);
values.remove(Integer.valueOf(20));
~~~

Dòng đầu, khi khung nhìn hỗ trợ xóa, yêu cầu xóa một ánh xạ có giá trị tương ứng trong Map; không nên dùng nó để chọn một khóa cụ thể khi nhiều khóa cùng giá trị. Dòng sau xóa trong danh sách `values` đã sao chép và không xóa ánh xạ của Map.

Tham số `Integer.valueOf(20)` ở dòng sau chọn overload xóa theo đối tượng. Với `List<Integer>`, `remove(20)` dùng tham số `int` sẽ chọn xóa theo chỉ số `20`, là một ý nghĩa khác.

Giá trị `null` cũng là dữ liệu nếu Map cho phép. `new ArrayList<>(map.values())` giữ giá trị đó trong danh sách. Nếu một bước xử lý sau không chấp nhận `null`, cần có quy tắc rõ ràng: giữ, thay thế, lọc bỏ hay báo lỗi; đừng để việc đổi cấu trúc âm thầm quyết định thay ứng dụng.

## 5. Danh sách entry: phải tách cấu trúc danh sách và đối tượng entry

### 5.1. Câu lệnh ngắn chỉ sao chép các tham chiếu entry

Cách viết thường thấy:

~~~java
List<Map.Entry<String, Integer>> entries =
        new ArrayList<>(scores.entrySet());
~~~

Câu lệnh tạo một danh sách riêng. Thêm/xóa các vị trí trong danh sách không tự thêm/xóa các ánh xạ của Map. Tuy nhiên, hàm tạo `ArrayList` không tự tạo một cặp khóa–giá trị độc lập cho từng entry; nó chép những đối tượng phần tử mà nguồn cung cấp.

Vì vậy, không được diễn giải câu lệnh này thành “các khóa và giá trị đã được đóng băng độc lập khỏi Map”. Cũng không được cho rằng việc bọc danh sách bằng một lớp chỉ đọc sẽ tự thay thế mọi entry bên trong bằng entry độc lập.

Hợp đồng Java 17 đặt giới hạn đáng chú ý cho entry lấy từ quá trình duyệt `entrySet`: sự kết nối với Map chỉ được xác định trong thời gian duyệt; hành vi ngoài quá trình ấy hoặc sau những thay đổi nền không được cho phép có thể không xác định. Muốn giữ cặp để dùng về sau, hãy tạo bản sao entry độc lập. [Java SE 17 — Map.Entry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.Entry.html).

Điều này còn có nghĩa không nên viết một phép thử bằng một loại Map cụ thể rồi nâng kết quả thành hợp đồng chung. Một entry có thể trông như vẫn phản ánh giá trị mới trên một cách cài đặt, nhưng đó chưa phải bảo đảm cho mọi Map hoặc cho mọi thay đổi tiếp theo.

### 5.2. Bản sao độc lập bằng Map.Entry.copyOf

Với khóa và giá trị không `null`, Java 17 cho phép:

~~~java
List<Map.Entry<String, Integer>> entries = scores.entrySet().stream()
        .map(Map.Entry::copyOf)
        .toList();
~~~

Phần `map(Map.Entry::copyOf)` có vai trò khác với phần `toList()`:

- `copyOf` tạo biểu diễn entry không liên kết với Map nền và không hỗ trợ thay giá trị bằng `setValue`.
- `toList` gom các entry đó vào một danh sách không cho sửa qua các phương thức thay đổi danh sách.

`Map.Entry.copyOf` có từ Java 17, không nhận entry `null`, khóa `null` hoặc giá trị `null`. Nó không bảo đảm luôn cấp phát một đối tượng entry mới nếu đầu vào đã là loại bản sao phù hợp; điều cần dùng là hợp đồng độc lập với Map, không phải so sánh danh tính đối tượng.

Nếu muốn danh sách có thể thêm/xóa hoặc sắp xếp tại chỗ, chọn đích tường minh:

~~~java
List<Map.Entry<String, Integer>> entries = scores.entrySet().stream()
        .map(Map.Entry::copyOf)
        .collect(Collectors.toCollection(ArrayList::new));
~~~

Danh sách ở đây sửa được, nhưng từng entry bên trong vẫn không cho `setValue`. Không có mâu thuẫn: một quyền thuộc danh sách, quyền kia thuộc đối tượng phần tử.

### 5.3. Cặp độc lập có cho sửa giá trị hay không?

Có thể tạo một entry độc lập bằng `AbstractMap.SimpleEntry`:

~~~java
Map.Entry<String, Integer> local =
        new AbstractMap.SimpleEntry<>("B", 20);
local.setValue(25);
~~~

Entry `local` trở thành `B=25`. Vì nó không liên kết với `scores`, lời gọi này không cập nhật Map. Đây là lựa chọn khi ứng dụng cần một cặp có thể sửa trước khi quyết định ghi vào nơi khác. [Java SE 17 — SimpleEntry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.SimpleEntry.html).

Nếu entry độc lập đã được sửa, muốn lưu lại thay đổi thì cần một thao tác Map tường minh như `scores.put(local.getKey(), local.getValue())`, cùng quy tắc xử lý xung đột phù hợp. Tạo danh sách làm việc không tự xây dựng một cơ chế đồng bộ hai chiều.

Ngược lại, trong lúc đang duyệt `entrySet`, entry của một Map hỗ trợ cập nhật có thể dùng `setValue` để ghi trở lại Map. Chương trình đầy đủ minh họa cách này ngay trong vòng duyệt và không giữ entry sống để dùng sau đó.

## 6. Map có null: chọn dạng bản sao phù hợp

`LinkedHashMap` có thể chứa khóa `null` và giá trị `null`. Nếu dùng `Map.Entry.copyOf` cho mọi entry của nó, quá trình chuyển đổi sẽ ném `NullPointerException` khi gặp phần tương ứng.

Không nên đổi dữ liệu `null` sang chuỗi `"null"` chỉ để tránh lỗi, vì hai giá trị có ý nghĩa khác nhau. Có thể dùng `AbstractMap.SimpleImmutableEntry` để giữ cả cặp có khóa hoặc giá trị `null`.

Đoạn helper có thể đặt trong một lớp:

~~~java
static <K, V> List<Map.Entry<K, V>> snapshotAllowingNulls(Map<K, V> source) {
    List<Map.Entry<K, V>> result = new ArrayList<>();
    for (Map.Entry<K, V> entry : source.entrySet()) {
        result.add(new AbstractMap.SimpleImmutableEntry<>(entry));
    }
    return result;
}
~~~

Cần import `Map`, `List`, `ArrayList` và `AbstractMap`. Helper tạo một danh sách sửa được, mỗi phần tử là một entry độc lập không hỗ trợ `setValue`.

`SimpleImmutableEntry` không gắn với khung nhìn của Map và không cho thay các tham chiếu khóa, giá trị qua `setValue`. Tên lớp không có nghĩa mọi đối tượng được khóa hoặc giá trị tham chiếu tới đều bất biến. [Java SE 17 — SimpleImmutableEntry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/AbstractMap.SimpleImmutableEntry.html).

Ví dụ nguồn:

~~~text
null  -> root
empty -> null
~~~

Bản sao có hai entry `null=root` và `empty=null`. Sau khi Map đổi `empty` thành `filled`, entry bản sao vẫn giữ tham chiếu giá trị `null` đã được chép trước đó.

Nếu còn cần không cho sửa danh sách, có thể công bố một lớp bọc không sửa được quanh danh sách helper tạo ra, với điều kiện không tiếp tục để nơi khác sửa chính danh sách bên trong. Quyết định chấp nhận `null` nằm ở kiểu entry, cách tạo danh sách và dữ liệu đầu vào; không thể suy ra chỉ từ chữ “bản sao”.

## 7. Thứ tự của danh sách đến từ đâu?

Danh sách có chỉ số, nhưng việc chuyển đổi không tự tạo ra một thứ tự nghiệp vụ mới. Nó thu các phần tử theo thứ tự duyệt mà nguồn cung cấp.

### 7.1. LinkedHashMap: ví dụ có thứ tự chèn rõ ràng

`new LinkedHashMap<>()` mặc định giữ thứ tự chèn. Khi đưa `B` rồi `A` rồi `C` vào, các danh sách minh họa bắt đầu theo thứ tự ấy. Thay giá trị của khóa đã có bằng `put` không đưa khóa đó xuống cuối trong chế độ thứ tự chèn. [Java SE 17 — LinkedHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html).

Đó là lý do chương trình dùng `LinkedHashMap` để có stdout dễ đối chiếu, thay vì in một thứ tự bất kỳ rồi gọi nó là chuẩn.

`LinkedHashMap` cũng có chế độ thứ tự truy cập được chọn bằng constructor khác. Trong chế độ ấy, một số thao tác đọc hoặc cập nhật có thể ảnh hưởng thứ tự. Không nên nói chung rằng mọi `LinkedHashMap` luôn dùng thứ tự chèn.

### 7.2. TreeMap: thứ tự theo khóa

`TreeMap` giữ khóa theo thứ tự tự nhiên hoặc comparator được cung cấp. Danh sách khóa lấy từ một `TreeMap<String, ...>` với thứ tự tự nhiên sẽ theo thứ tự chuỗi, không theo thời điểm chèn. [Java SE 17 — TreeMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html).

Một `TreeMap` được tạo từ các khóa `B`, `A`, `C` sẽ cho danh sách khóa `[A, B, C]` khi dùng thứ tự tự nhiên của các chuỗi đó.

### 7.3. HashMap và các Map không cam kết thứ tự

Không được dựa vào thứ tự chèn, thứ tự tăng dần hoặc kết quả đã in một lần của `HashMap` như một hợp đồng. Tạo `ArrayList` từ khung nhìn giữ thứ tự quan sát trong lần duyệt đó; nó không bổ sung một cam kết cho những lần chuyển đổi khác.

Nếu cần thứ tự để xuất báo cáo, kiểm thử, phân trang hay hiển thị ổn định, hãy chọn nguồn có hợp đồng thứ tự phù hợp hoặc sắp xếp kết quả bằng tiêu chí rõ ràng.

Cũng đừng dùng `Map.of` để tạo dữ liệu rồi mặc nhiên kỳ vọng danh sách giữ đúng thứ tự đối số của lời gọi. Các factory đó không phải cách yêu cầu thứ tự chèn.

## 8. Sắp xếp cặp trước khi tách khóa hoặc giá trị

Để xếp điểm giảm dần, nếu bằng điểm thì mã tăng dần:

~~~java
List<Map.Entry<String, Integer>> sorted = scores.entrySet().stream()
        .map(Map.Entry::copyOf)
        .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.<String, Integer>comparingByKey()))
        .toList();
~~~

Đoạn minh họa giả định khóa và điểm không `null`. `Comparator.reverseOrder()` đảo thứ tự của điểm; `thenComparing` bổ sung quy tắc theo mã khi điểm bằng nhau. [Java SE 17 — Comparator.thenComparing](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html#thenComparing(java.util.Comparator)).

Với nguồn `C=20`, `B=20`, `A=10`, kết quả là:

~~~text
[B=20, C=20, A=10]
~~~

Điểm không bị tách khỏi mã trong quá trình sắp xếp. Nếu cần danh sách mã theo đúng thứ hạng ấy, lấy khóa sau khi đã có thứ tự cặp:

~~~java
List<String> rankedKeys = sorted.stream()
        .map(Map.Entry::getKey)
        .toList();
~~~

Danh sách kết quả là `[B, C, A]`. Nếu chỉ cần danh sách điểm theo thứ hạng, dùng `Map.Entry::getValue` ở bước cuối.

Tiêu chí phụ có ích khi nhiều cặp có cùng điểm. Sắp xếp chỉ theo điểm có thể giữ một thứ tự gặp ban đầu giữa các cặp bằng điểm, nhưng nếu nguồn không có thứ tự được cam kết thì đó không tạo ra thứ tự ổn định cho mọi lần chạy. Thêm mã làm tiêu chí phụ diễn tả yêu cầu rõ ràng hơn.

Nếu có `null`, cần quyết định `null` đứng đầu hay cuối trước khi chọn comparator. Ví dụ có thể truyền một comparator xử lý `null` cho khóa hoặc giá trị. Nhưng bước `Map.Entry.copyOf` vẫn không chấp nhận `null`, nên phải chọn cách tạo entry phù hợp trước; comparator xử lý `null` không sửa được lỗi đã xảy ra ở giai đoạn sao chép entry.

### Không sắp xếp hai danh sách độc lập rồi ghép theo chỉ số

Từ `B=20`, `A=10`, `C=20`, nếu chỉ sắp xếp danh sách khóa thành `[A, B, C]` rồi ghép với danh sách giá trị cũ `[20, 10, 20]`, ta tạo `A=20` và `B=10`, sai dữ liệu gốc.

Ngay cả khi hai lần lấy danh sách lúc đầu có thể khớp trong một nguồn ổn định có thứ tự tương ứng, các bước sửa, lọc hoặc thay đổi Map giữa hai lần lấy vẫn dễ phá quan hệ. Nếu nhiệm vụ cần cặp, dùng cặp làm đơn vị xử lý.

## 9. Lọc và chuyển thành dữ liệu đầu ra

Có thể chỉ lấy những cặp có điểm từ `20` trở lên:

~~~java
List<Map.Entry<String, Integer>> qualified = scores.entrySet().stream()
        .filter(entry -> entry.getValue() >= 20)
        .map(Map.Entry::copyOf)
        .toList();
~~~

Đây là đoạn minh họa với các điểm `Integer` khác `null`. Nếu giá trị có thể `null`, điều kiện phải xử lý điều đó trước khi so sánh số.

Việc lọc tạo một kết quả mới; nó không xóa các ánh xạ không đạt từ Map. Nếu muốn sửa Map, đó là một yêu cầu khác và cần thao tác đúng trên cấu trúc nguồn.

Đôi khi dữ liệu đầu ra nên có tên nghiệp vụ thay vì `Map.Entry`. Ví dụ một kiểu `record ScoreRow(String code, Integer score)` có thể thể hiện rõ cột mã và điểm trong một bảng hiển thị. Khi chuyển thành record, đọc khóa và giá trị trong cùng một lần duyệt rồi tạo một dòng độc lập.

Dù dùng entry hay record, việc đổi kiểu bao ngoài vẫn chỉ chép các tham chiếu nếu trường bên trong là đối tượng. Nó không tự tạo bản sao sâu của hồ sơ, địa chỉ hoặc danh sách con trong giá trị.

Một danh sách mới cũng không phải một bộ đồng bộ trở lại Map. Chỉnh dòng trong danh sách làm việc không tự biết phải cập nhật khóa nào, có cho thêm khóa không, và xử lý thế nào nếu Map đã thay đổi kể từ lúc lấy dữ liệu. Những quy tắc ấy cần được thiết kế tường minh ở thời điểm ghi kết quả.

## 10. Quyền sửa danh sách và quyền sửa phần tử là hai tầng

| Cách tạo | Cấu trúc danh sách | Các entry được giữ |
| --- | --- | --- |
| `new ArrayList<>(map.entrySet())` | Có thể sửa | Chỉ chép các tham chiếu entry từ nguồn; không được xem là bản sao entry độc lập |
| `map.entrySet().stream().toList()` | Không cho sửa danh sách | Vẫn chưa có bước tạo entry độc lập |
| `stream().map(Map.Entry::copyOf).toList()` | Không cho sửa danh sách | Entry độc lập, không cho `setValue`, không chấp nhận phần khóa/giá trị null |
| `stream().map(Map.Entry::copyOf).collect(toCollection(ArrayList::new))` | Có thể sửa | Entry độc lập nhưng vẫn không cho `setValue` |
| Helper dùng `SimpleImmutableEntry` và `ArrayList` | Có thể sửa | Entry độc lập, không cho `setValue`, có thể biểu diễn phần khóa/giá trị null |

`Stream.toList()` từ Java 16 trả về danh sách không cho sửa và giữ thứ tự gặp nếu stream có thứ tự. Điều đó không làm các đối tượng phần tử tự bất biến. [Java SE 17 — Stream.toList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList()).

Nếu cần chắc chắn một `ArrayList` sửa được, dùng constructor hoặc `Collectors.toCollection(ArrayList::new)`. Không coi `Collectors.toList()` như hợp đồng bắt buộc phải trả `ArrayList` sửa được: tài liệu không bảo đảm kiểu triển khai hay quyền sửa của kết quả ấy. [Java SE 17 — Collectors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html#toCollection(java.util.function.Supplier)).

Hãy kiểm tra hai câu lệnh khác nhau:

~~~java
entries.remove(0);
entries.get(0).setValue(500);
~~~

Lệnh đầu yêu cầu quyền sửa danh sách. Lệnh sau yêu cầu quyền sửa đối tượng entry đang ở vị trí `0`. Một trong hai chạy được không chứng minh lệnh kia chạy được; và entry độc lập sửa được cũng không có nghĩa nó ghi về Map.

## 11. Bản sao cặp vẫn có thể dùng chung đối tượng giá trị

Xét giá trị là `StringBuilder`:

~~~java
StringBuilder item = new StringBuilder("seed");
Map<String, StringBuilder> source = new LinkedHashMap<>();
source.put("item", item);

List<Map.Entry<String, StringBuilder>> snapshot = source.entrySet().stream()
        .map(Map.Entry::copyOf)
        .toList();
~~~

Entry đã tách khỏi Map. Tuy nhiên, giá trị nó giữ vẫn là tham chiếu tới `item`.

Sau `item.append("!")`, đọc giá trị trong entry bản sao sẽ thấy `seed!`. Không có tham chiếu giá trị nào bị thay ở cấp entry; chính đối tượng được tham chiếu đã đổi trạng thái.

Nếu sau đó gọi:

~~~java
source.put("item", new StringBuilder("replacement"));
~~~

Map trỏ sang đối tượng mới. Entry bản sao vẫn giữ đối tượng cũ `seed!`. Đây là khác biệt giữa **thay ánh xạ tới đối tượng khác** và **sửa chính đối tượng đang được dùng chung**.

Bản sao sâu đòi hỏi quy tắc cho từng kiểu giá trị. Với một mô hình nghiệp vụ, có thể tạo đối tượng dữ liệu bất biến riêng hoặc chép từng trường cần giữ. Không thể yêu cầu một phương thức chuyển Map thành List tổng quát tự hiểu mọi quan hệ đối tượng và tạo đúng bản sao sâu cho chúng.

## 12. Khi Map có thể bị sửa đồng thời

Một phép chuyển đổi phải duyệt nguồn. `new ArrayList<>(map.values())` và stream sao chép entry không tự khóa toàn bộ Map hoặc biến nhiều lần đọc thành một ảnh chụp nguyên tử.

Với một Map thông thường được bảo vệ bằng khóa, toàn bộ quá trình tạo danh sách cần tuân thủ cùng giao thức khóa với các nơi sửa Map. Riêng `Collections.synchronizedMap` yêu cầu đồng bộ trên chính Map bọc khi duyệt các khung nhìn; không thay bằng việc tự khóa `keySet` hoặc `entrySet`. [Java SE 17 — Collections.synchronizedMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedMap(java.util.Map)).

Với `ConcurrentHashMap`, việc duyệt có hợp đồng phù hợp truy cập đồng thời nhưng không mặc nhiên cho một ảnh chụp toàn bộ tại một thời điểm duy nhất. Danh sách cuối cùng có thể là tập các quan sát trong thời gian duyệt. [Java SE 17 — ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

Hai tính chất phải được đánh giá riêng:

- Sau khi tạo, entry bản sao không còn liên kết với các thay đổi ánh xạ của Map.
- Trong lúc tạo, các cặp được thu thập có thuộc cùng một trạng thái nhất quán toàn cục hay không.

`Map.Entry.copyOf` giải quyết tính chất thứ nhất ở cấp cặp tham chiếu; nó không tự cung cấp tính chất thứ hai. Nếu nghiệp vụ cần một báo cáo nhất quán giữa nhiều khóa, chọn chiến lược phối hợp phù hợp thay vì chỉ đổi collection đầu ra.

Chương trình của bài giữ nguồn ổn định khi tạo từng danh sách và chỉ thay Map sau khi các bước tương ứng hoàn tất.

## 13. Chương trình minh chứng đầy đủ

Lưu mã thành **MapListsDemo.java**. Các API được dùng tương thích Java 17; `Map.Entry.copyOf` là phần cần Java 17 trong ví dụ.

Hàm `rejects` xác nhận loại ngoại lệ dự kiến. Helper `snapshotAllowingNulls` tạo bản sao entry độc lập trong lúc duyệt, giữ được khóa hoặc giá trị `null`. Lần cập nhật bằng `entry.setValue` được thực hiện ngay trong vòng duyệt, không lưu entry từ khung nhìn để dùng sau.

~~~java
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class MapListsDemo {
    private static void rejects(
            String label,
            Class<? extends RuntimeException> expected,
            Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " did not fail");
        } catch (RuntimeException ex) {
            if (!expected.isInstance(ex)) {
                throw ex;
            }
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }

    private static <K, V> List<Map.Entry<K, V>> snapshotAllowingNulls(Map<K, V> source) {
        List<Map.Entry<K, V>> result = new ArrayList<>();
        for (Map.Entry<K, V> entry : source.entrySet()) {
            result.add(new AbstractMap.SimpleImmutableEntry<>(entry));
        }
        return result;
    }

    public static void main(String[] args) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("B", 20);
        scores.put("A", 10);
        scores.put("C", 20);

        Set<String> keyView = scores.keySet();
        Collection<Integer> valueView = scores.values();
        List<String> keys = new ArrayList<>(keyView);
        List<Integer> values = new ArrayList<>(valueView);
        List<Map.Entry<String, Integer>> entries = scores.entrySet().stream()
                .map(Map.Entry::copyOf)
                .collect(Collectors.toCollection(ArrayList::new));
        System.out.println("keys=" + keys);
        System.out.println("values=" + values);
        System.out.println("entries=" + entries);

        scores.put("A", 99);
        scores.put("D", 30);
        System.out.println("map-after-put=" + scores);
        System.out.println("key-view=" + keyView);
        System.out.println("value-view=" + valueView);
        System.out.println("copied-keys=" + keys);
        System.out.println("copied-values=" + values);
        System.out.println("snapshot-entries=" + entries);

        keys.remove("B");
        values.add(777);
        entries.remove(0);
        System.out.println("edited-lists=" + keys + " / " + values + " / " + entries);
        System.out.println("map-after-list-edits=" + scores);
        rejects("snapshot-setValue", UnsupportedOperationException.class,
                () -> entries.get(0).setValue(500));

        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getKey().equals("B")) {
                System.out.println("iterator-setValue-old=" + entry.setValue(25));
            }
        }
        System.out.println("map-after-entry-update=" + scores);
        keyView.remove("D");
        System.out.println("map-after-view-remove=" + scores);

        List<Map.Entry<String, Integer>> frozen = scores.entrySet().stream()
                .map(Map.Entry::copyOf)
                .toList();
        rejects("stream-list-add", UnsupportedOperationException.class,
                () -> frozen.add(Map.entry("X", 1)));
        System.out.println("frozen=" + frozen);

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(frozen);
        sorted.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.<String, Integer>comparingByKey()));
        System.out.println("sorted=" + sorted);
        System.out.println("map-order=" + new ArrayList<>(scores.keySet()));

        Map<String, Integer> tieMap = new LinkedHashMap<>();
        tieMap.put("C", 20);
        tieMap.put("B", 20);
        tieMap.put("A", 10);
        List<Map.Entry<String, Integer>> tieSorted = tieMap.entrySet().stream()
                .map(Map.Entry::copyOf)
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.<String, Integer>comparingByKey()))
                .toList();
        System.out.println("tie-sorted=" + tieSorted);

        Map<String, Integer> tree = new TreeMap<>(scores);
        System.out.println("tree-keys=" + new ArrayList<>(tree.keySet()));

        Map<String, String> nullable = new LinkedHashMap<>();
        nullable.put(null, "root");
        nullable.put("empty", null);
        List<Map.Entry<String, String>> nullableEntries = snapshotAllowingNulls(nullable);
        System.out.println("nullable-keys=" + new ArrayList<>(nullable.keySet()));
        System.out.println("nullable-values=" + new ArrayList<>(nullable.values()));
        System.out.println("nullable-entries=" + nullableEntries);
        rejects("copyOf-null-entry-part", NullPointerException.class,
                () -> nullable.entrySet().stream().map(Map.Entry::copyOf).toList());
        rejects("nullable-entry-setValue", UnsupportedOperationException.class,
                () -> nullableEntries.get(0).setValue("changed"));
        nullable.put("empty", "filled");
        System.out.println("nullable-map=" + nullable);
        System.out.println("nullable-snapshot=" + nullableEntries);

        AbstractMap.SimpleEntry<String, Integer> local =
                new AbstractMap.SimpleEntry<>("local", 1);
        local.setValue(2);
        System.out.println("detached-editable-entry=" + local);

        StringBuilder item = new StringBuilder("seed");
        Map<String, StringBuilder> objects = new LinkedHashMap<>();
        objects.put("item", item);
        List<Map.Entry<String, StringBuilder>> shallow = objects.entrySet().stream()
                .map(Map.Entry::copyOf)
                .toList();
        item.append("!");
        System.out.println("shared-value-object=" + shallow.get(0).getValue());
        objects.put("item", new StringBuilder("replacement"));
        System.out.println("rebound-map-value=" + objects.get("item"));
        System.out.println("snapshot-old-reference=" + shallow.get(0).getValue());
    }
}
~~~

Biên dịch và chạy tại thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 MapListsDemo.java
java MapListsDemo
~~~

Stdout chính xác:

~~~text
keys=[B, A, C]
values=[20, 10, 20]
entries=[B=20, A=10, C=20]
map-after-put={B=20, A=99, C=20, D=30}
key-view=[B, A, C, D]
value-view=[20, 99, 20, 30]
copied-keys=[B, A, C]
copied-values=[20, 10, 20]
snapshot-entries=[B=20, A=10, C=20]
edited-lists=[A, C] / [20, 10, 20, 777] / [A=10, C=20]
map-after-list-edits={B=20, A=99, C=20, D=30}
snapshot-setValue -> UnsupportedOperationException
iterator-setValue-old=20
map-after-entry-update={B=25, A=99, C=20, D=30}
map-after-view-remove={B=25, A=99, C=20}
stream-list-add -> UnsupportedOperationException
frozen=[B=25, A=99, C=20]
sorted=[A=99, B=25, C=20]
map-order=[B, A, C]
tie-sorted=[B=20, C=20, A=10]
tree-keys=[A, B, C]
nullable-keys=[null, empty]
nullable-values=[root, null]
nullable-entries=[null=root, empty=null]
copyOf-null-entry-part -> NullPointerException
nullable-entry-setValue -> UnsupportedOperationException
nullable-map={null=root, empty=filled}
nullable-snapshot=[null=root, empty=null]
detached-editable-entry=local=2
shared-value-object=seed!
rebound-map-value=replacement
snapshot-old-reference=seed!
~~~

Mã đã biên dịch bằng `--release 17` và chạy trên JDK 24.0.2. Đây là kiểm tra tương thích mức API/ngôn ngữ Java 17 và kết quả chạy trên JVM đã nêu; không phải tuyên bố đã chạy bằng JVM 17.

Những điểm cần đối chiếu trong stdout:

- Ba dòng đầu chứa cùng ba ánh xạ nhìn dưới ba dạng, trong đó danh sách giá trị giữ cả hai lần `20`.
- Sau `put`, các khung nhìn thấy dữ liệu mới nhưng danh sách khóa, giá trị và bản sao entry đã tạo vẫn giữ dữ liệu của lần chép trước.
- `edited-lists` thay đổi mà `map-after-list-edits` không đổi; `snapshot-setValue` bị từ chối vì entry bản sao không hỗ trợ thay giá trị.
- `iterator-setValue-old` trả về điểm cũ và `map-after-entry-update` thấy điểm mới vì thao tác diễn ra qua entry đang được duyệt.
- `map-after-view-remove` minh họa xóa trực tiếp qua khung nhìn, khác với xóa trên danh sách đã chép.
- `sorted` và `tie-sorted` giữ khóa đi với điểm; `map-order` cho thấy sắp xếp danh sách không sắp xếp lại Map.
- Các dòng `nullable` phân biệt bản sao cho phép phần dữ liệu null với `Map.Entry.copyOf` không chấp nhận chúng.
- Ba dòng cuối phân biệt sửa đối tượng giá trị dùng chung với thay ánh xạ Map sang một đối tượng khác.

## 14. Thực hành có đáp án

### Bài 1 — Chọn kiểu danh sách

Một hàm chỉ cần kiểm tra những mã đang có; một hàm khác cần xếp hạng điểm và hiện mã đi kèm. Mỗi hàm nên lấy khung nhìn nào để tạo danh sách?

**Đáp án:** Hàm đầu lấy `keySet` và tạo `List<K>`. Hàm xếp hạng lấy `entrySet`, tạo các entry độc lập rồi sắp xếp cặp trước khi trình bày. Chỉ lấy `values` sẽ đánh mất quan hệ giữa mã và điểm.

### Bài 2 — Giá trị trùng có bị mất không?

Map có `A=10`, `B=10`, `C=20`. `new ArrayList<>(map.values())` có bao nhiêu phần tử? Nếu thay bằng một Set thì điều gì thay đổi?

**Đáp án:** Danh sách có ba phần tử, gồm hai lần `10`. Set có thể gộp hai giá trị bằng nhau và làm mất số lần xuất hiện. Việc loại trùng cần là yêu cầu rõ ràng, không phải một bước chuyển đổi mặc định.

### Bài 3 — Xóa trên khung nhìn và danh sách

Sau `List<String> keys = new ArrayList<>(map.keySet())`, gọi `keys.remove("A")` có xóa `A` của Map không? Còn `map.keySet().remove("A")` khi Map hỗ trợ xóa?

**Đáp án:** Lệnh đầu chỉ sửa danh sách độc lập. Lệnh sau thao tác trên khung nhìn và xóa ánh xạ của Map. Hai biến có thể chứa cùng các khóa ban đầu nhưng mang quan hệ khác với nguồn.

### Bài 4 — Danh sách entry mới đã đủ làm bản sao chưa?

Vì sao `new ArrayList<>(map.entrySet())` chưa đủ để cam kết các cặp giữ nguyên khi Map thay đổi?

**Đáp án:** Constructor chỉ tạo danh sách và chép tham chiếu phần tử, không tạo entry độc lập. Entry lấy từ khung nhìn còn có giới hạn sử dụng theo hợp đồng duyệt. Dùng bước sao chép entry trong lúc duyệt, chẳng hạn `Map.Entry.copyOf` với dữ liệu không null hoặc `SimpleImmutableEntry` khi cần giữ phần khóa/giá trị null.

### Bài 5 — Danh sách sửa được, entry không sửa được

Một danh sách được tạo bằng `map(Map.Entry::copyOf).collect(Collectors.toCollection(ArrayList::new))`. Vì sao `remove(0)` có thể chạy mà `get(0).setValue(...)` lại ném lỗi?

**Đáp án:** Quyền sửa danh sách thuộc `ArrayList`. Quyền sửa giá trị entry thuộc đối tượng do `copyOf` cung cấp, vốn không cho `setValue`. Hai tầng quyền thay đổi độc lập với nhau.

### Bài 6 — Dữ liệu null cần giữ nguyên

Map cho phép khóa null và giá trị null. Không được bỏ hoặc đổi dữ liệu. Có thể dùng thẳng `Map.Entry.copyOf` cho mọi entry không?

**Đáp án:** Không, phương thức đó từ chối cả khóa hoặc giá trị null. Tạo từng `new AbstractMap.SimpleImmutableEntry<>(entry)` khi duyệt để giữ dữ liệu và tách entry khỏi Map. Sau đó chọn quyền sửa của danh sách theo yêu cầu riêng.

### Bài 7 — Giữ quan hệ khi sắp xếp

Nguồn theo thứ tự chèn là `C=20`, `B=20`, `A=10`. Cần điểm giảm dần và mã tăng dần khi bằng điểm. Kết quả nào đúng và vì sao không chỉ sắp danh sách khóa?

**Đáp án:** `[B=20, C=20, A=10]`. Sắp xếp cả cặp theo điểm rồi theo mã giữ quan hệ dữ liệu. Sắp riêng khóa rồi ghép với các giá trị chưa được sắp tương ứng có thể tạo cặp sai. Tiêu chí phụ theo mã cũng làm thứ tự giữa hai điểm `20` trở nên rõ ràng.

### Bài 8 — Bản sao và đối tượng dùng chung

Giá trị Map là một `StringBuilder`. Sau khi tạo entry bằng `copyOf`, sửa `StringBuilder` ban đầu có hiện trong bản sao không? Nếu dùng `put` để thay bằng một `StringBuilder` mới thì sao?

**Đáp án:** Sửa đối tượng ban đầu được nhìn thấy qua tham chiếu giá trị đã chép. Thay ánh xạ bằng `put` không đổi tham chiếu mà entry độc lập đang giữ. Muốn tách cả trạng thái bên trong, cần quy tắc sao chép đối tượng hoặc kiểu giá trị bất biến phù hợp.

## 15. Chọn kết quả theo hợp đồng đầu ra

Nếu cần mã, lấy khóa; nếu cần dữ liệu tổng hợp không cần quan hệ mã, lấy giá trị; nếu cần từng dòng khóa–giá trị, giữ entry hoặc tạo đối tượng dòng rõ nghĩa. Sau đó xác định thứ tự và quyền sửa của kết quả.

Khi cần giữ dữ liệu để dùng về sau, hãy tạo entry độc lập trong quá trình duyệt, xử lý null theo một chính sách đã chọn, và nhớ rằng khóa hoặc giá trị là đối tượng sửa được vẫn có thể được dùng chung. Một bản sao danh sách chỉ bảo đảm cấu trúc danh sách mới; mức tách dữ liệu phải đến từ cách sao chép phần tử.

Cách suy luận này giúp chuyển Map sang danh sách để hiển thị, sắp xếp, lọc hoặc truyền qua API mà vẫn giữ đúng quan hệ và hiểu rõ nơi nào có thể bị thay đổi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="array-conversion-008">Chọn loại collection đích để bảo toàn hợp đồng cần thiết</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn loại collection đích để bảo toàn hợp đồng cần thiết

## 1. Chuyển đổi thành công chưa có nghĩa giữ đúng ý nghĩa dữ liệu

Giả sử một danh sách dòng đặt hàng chứa hai dòng có cùng giá trị. Nếu chuyển nó thành `HashSet` để “tra cứu cho nhanh”, những dòng bằng nhau có thể bị gộp và thứ tự không còn được cam kết. Chương trình vẫn biên dịch, không nhất thiết có ngoại lệ, nhưng ý nghĩa của dữ liệu đã thay đổi.

Trong một yêu cầu khác, ứng dụng lại muốn danh sách mã không trùng, giữ lần xuất hiện đầu tiên. Khi đó, việc gộp phần tử là chủ ý và `LinkedHashSet` có thể phù hợp hơn. Cùng một nguồn dữ liệu nhưng mục tiêu sử dụng khác nhau dẫn đến lựa chọn đích khác nhau.

**Hợp đồng** (contract) là những điều API hứa với bên sử dụng: cách hiểu phần tử, thứ tự, quy tắc trùng, thao tác được phép, giới hạn dữ liệu và cách truy cập. Chọn collection đích là chọn một tập hợp các cam kết ấy cho dữ liệu sau chuyển đổi.

Cần tách hai mức:

- **Trạng thái hiện tại:** kết quả đang chứa phần tử nào và đang được duyệt theo thứ tự nào.
- **Hành vi về sau:** khi thêm, xóa, truy vấn hoặc xử lý phần tử tiếp theo, cấu trúc sẽ duy trì quy tắc gì.

Ví dụ, một `ArrayList` sao chép từ `TreeSet` có thể ban đầu được duyệt theo thứ tự đã sắp xếp của nguồn. Nhưng việc thêm phần tử vào cuối list không tự sắp xếp lại nó. Giữ đúng dãy hiện tại không đồng nghĩa giữ hợp đồng “luôn có thứ tự theo bộ so sánh”.

Một mô hình suy nghĩ hữu ích:

```text
Nguồn
  -> xác định những thông tin và hành vi phải giữ
  -> chọn giao diện, lớp triển khai và cách tạo đích
  -> chuyển phần tử theo chính sách đã chọn
  -> kiểm tra cả nội dung lẫn thao tác về sau
```

Mục tiêu không phải sao chép mọi đặc điểm của nguồn bằng mọi giá. Mục tiêu là giữ đúng những cam kết mà API đích và nghiệp vụ thực sự cần, đồng thời nêu rõ các thay đổi có chủ ý.

## 2. Xác định hợp đồng trước khi chọn tên lớp

Trước một phép chuyển đổi, nên xác định các trục sau:

| Trục cần quyết định | Câu hỏi cụ thể |
| --- | --- |
| Số lần xuất hiện | Hai phần tử bằng nhau có còn là hai dòng riêng không? |
| Thứ tự | Cần thứ tự gặp, thứ tự sắp xếp hay thứ tự lấy ra xử lý? |
| Quy tắc bằng nhau | Dùng `equals/hashCode`, bộ so sánh hay nhận dạng tham chiếu? |
| Giá trị hợp lệ | Có `null`, enum, kiểu không hỗ trợ so sánh tự nhiên hoặc giới hạn miền không? |
| Quyền sửa | Bên nhận được thêm/xóa/thay phần tử, hay chỉ đọc? |
| Liên hệ với nguồn | Cần khung nhìn phản ánh thay đổi của nguồn hay kết quả độc lập về cấu trúc? |
| Khả năng thao tác | Cần chỉ số, truy vấn phạm vi, hai đầu hàng đợi, lấy theo ưu tiên hay chờ khi đầy/rỗng? |
| Truy cập đồng thời | Kết quả có được nhiều luồng chia sẻ; cần những thao tác nguyên tử nào? |

Kiểu `Collection<E>` không biểu diễn đầy đủ các trục này. Nó không hứa một thứ tự chung, không yêu cầu mọi lớp đều cho thêm phần tử và không mang theo một bộ so sánh của nguồn như tham số kiểu.

Ngay cả biến `List<String>` cũng không tự cam kết rằng `add` sẽ được phép. Một list không cho sửa và một `ArrayList` cùng có thể được nhìn qua giao diện `List`. Khai báo biến là `final` cũng chỉ ngăn gán lại biến, không tự cấm sửa đối tượng collection.

Vì vậy, một phép chuyển đổi cần được đánh giá qua cả **loại đích** và **cách tạo đích**, không chỉ qua việc kết quả có gán được cho biến cùng tham số kiểu hay không. [Hợp đồng `Collection`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html) mô tả các khác biệt về thứ tự, thao tác tùy chọn và chính sách của từng lớp triển khai.

### 2.1. Bảng định hướng một số đích thường gặp

| Đích | Đặc điểm nổi bật | Điều có thể thay đổi so với nguồn |
| --- | --- | --- |
| `ArrayList` | Dãy phần tử, giữ thứ tự duyệt khi sao chép, giữ phần tử trùng | Không tự giữ bộ so sánh, tính duy nhất hay chính sách chỉ đọc của nguồn |
| `LinkedHashSet` | Gộp phần tử theo `equals/hashCode`, giữ thứ tự chèn | Mất số lần xuất hiện của phần tử trùng; không tự duy trì thứ tự sắp xếp |
| `HashSet` | Tập hợp theo `equals/hashCode` | Không cam kết thứ tự duyệt |
| `TreeSet` | Sắp xếp và nhận diện trùng theo phép so sánh | Có thể gộp các giá trị khác nhau theo `equals` nếu bộ so sánh không phù hợp |
| `EnumSet` | Tập hợp chuyên cho một kiểu enum, duyệt theo thứ tự khai báo | Không giữ số lần xuất hiện hoặc thứ tự tùy ý của nguồn |
| `ArrayDeque` | Thao tác hai đầu, hỗ trợ cách dùng hàng đợi/ngăn xếp | Không nhận `null`, không mang sẵn giới hạn dung lượng cố định |
| `PriorityQueue` | Lấy phần tử theo ưu tiên | Thứ tự duyệt không phải dãy lấy ra theo ưu tiên |
| `List.copyOf(source)` | List không cho sửa, giữ dãy phần tử, không phản ánh thay đổi cấu trúc nguồn về sau | Không nhận `null`, không làm phần tử thành bất biến sâu |
| `Collections.unmodifiableList(new ArrayList<>(source))` | List chỉ đọc qua kết quả, sao chép cấu trúc và chấp nhận phần tử `null` | Các đối tượng phần tử vẫn có thể được chia sẻ |

Bảng là điểm bắt đầu để chọn hợp đồng, không phải bảng thay thế mọi API cho nhau. Những yêu cầu chuyên biệt như ràng buộc phạm vi, đồng bộ hoặc dung lượng cần được xử lý thêm.

## 3. Giữ số lần xuất hiện, thứ tự và quy tắc trùng

### 3.1. Chuyển list thành set là một phép biến đổi có mất thông tin

Một dãy `[b, a, b]` có ba vị trí, trong đó `b` xuất hiện hai lần. Một set thông thường theo `equals` chỉ giữ một đại diện của `b`. Chuyển set ấy ngược về list không khôi phục được vị trí và số lần xuất hiện đã mất.

Nếu mỗi lần xuất hiện có ý nghĩa nghiệp vụ, hãy giữ kiểu dãy hoặc một mô hình biểu diễn số lượng. Đừng chọn set chỉ vì muốn loại bỏ việc duyệt tuyến tính mà chưa xác định liệu dữ liệu có được phép bị gộp hay không.

Nếu cần **sắp xếp nhưng giữ phần tử trùng**, có thể tạo `ArrayList` rồi sắp xếp list bằng bộ so sánh phù hợp. `TreeSet` đồng thời áp dụng tính duy nhất theo phép so sánh; nó không phải một cách thay thế trong suốt cho “list được sắp xếp”.

### 3.2. Thứ tự gặp không phải lịch sử có thể khôi phục từ mọi nguồn

`new ArrayList<>(source)` lấy phần tử theo thứ tự iterator của nguồn. `LinkedHashSet` cũng giữ thứ tự chèn các đại diện được chấp nhận trong quá trình sao chép.

Nếu nguồn đã có thứ tự xác định, điều này hữu ích. Nếu nguồn là một `HashSet` không cam kết thứ tự, sao chép sang `LinkedHashSet` chỉ cố định thứ tự đã quan sát trong lần sao chép ấy. Nó không tìm lại thứ tự chèn lịch sử vốn không được nguồn cung cấp.

Với phần tử trùng, thêm lại một giá trị đã có vào `LinkedHashSet` không đẩy giá trị ấy xuống cuối. Điều đó phù hợp với yêu cầu giữ vị trí lần gặp đầu tiên theo quy tắc bằng nhau của set.

[`ArrayList`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayList.html) và [`LinkedHashSet`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashSet.html) nêu rõ cách nhận thứ tự từ nguồn; [`HashSet`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html) không cung cấp cam kết thứ tự tương ứng.

### 3.3. Có ba quy tắc nhận diện khác nhau cần phân biệt

- Set băm thông thường dùng ý nghĩa của `equals` kết hợp hợp đồng `hashCode`.
- `TreeSet` xác định hai phần tử trùng về phía cây khi phép so sánh trả về `0`.
- Một cấu trúc chuyên dùng theo nhận dạng tham chiếu có thể coi hai đối tượng chỉ trùng nếu chúng là cùng đối tượng theo `==`.

Hai chuỗi `"ID"` và `"id"` không bằng nhau theo `String.equals`, nhưng bộ so sánh không phân biệt hoa thường xem chúng tương đương. Chọn bộ so sánh ấy cho `TreeSet` có thể làm số phần tử giảm.

Đây là một tình huống để phát hiện lựa chọn không phù hợp, không phải lời khuyên bỏ qua hợp đồng chung của `Set`. Bộ so sánh của tập hợp được sắp xếp cần nhất quán với `equals` để tuân thủ hợp đồng set thông thường. Nếu nghiệp vụ cần một dạng chuẩn hóa khác, hãy thiết kế khóa/giá trị chuẩn hóa và quy tắc bằng nhau một cách rõ ràng.

Cấu trúc theo nhận dạng tham chiếu cũng là một lựa chọn chuyên biệt. Chuyển nó sang `HashSet` thông thường có thể gộp các đối tượng khác nhau nhưng có giá trị bằng nhau. Không dùng một set kiểu đó thay thế trong suốt cho mọi set theo giá trị. [`IdentityHashMap`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html) giải thích rõ trường hợp đặc biệt cần so sánh tham chiếu.

### 3.4. Chương trình 1: Một nguồn, nhiều hợp đồng đích

Tệp `TargetSemanticsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class TargetSemanticsDemo {
    public static void main(String[] args) {
        List<String> source = Arrays.asList("b", "a", "b", null);
        List<String> list = new ArrayList<>(source);
        Set<String> insertionSet = new LinkedHashSet<>(source);
        Set<String> hashSet = new HashSet<>(source);

        System.out.println("source=" + source);
        System.out.println("list=" + list);
        System.out.println("insertionSet=" + insertionSet);
        System.out.println("hashSize=" + hashSet.size());
        System.out.println("hashContainsNull=" + hashSet.contains(null));
        System.out.println("roundTrip=" + new ArrayList<>(insertionSet));

        try {
            new TreeSet<>(source);
            throw new AssertionError("Natural-order TreeSet accepted null");
        } catch (NullPointerException expected) {
            System.out.println("naturalTree=NullPointerException");
        }

        TreeSet<String> nullableSorted =
                new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));
        nullableSorted.addAll(source);
        System.out.println("nullableSorted=" + nullableSorted);

        List<String> sortedSequence = new ArrayList<>(source);
        sortedSequence.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        System.out.println("sortedSequence=" + sortedSequence);

        List<String> codes = List.of("ID", "id");
        Set<String> exactCodes = new LinkedHashSet<>(codes);
        TreeSet<String> foldedCodes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        foldedCodes.addAll(codes);
        System.out.println("equalsDistinct=" + exactCodes.size());
        System.out.println("comparatorDistinct=" + foldedCodes.size());

        Set<String> identitySet =
                Collections.newSetFromMap(new IdentityHashMap<>());
        identitySet.add(new String("same"));
        identitySet.add(new String("same"));
        System.out.println("identitySize=" + identitySet.size());
        System.out.println("equalsCopySize=" + new HashSet<>(identitySet).size());

        list.add("tail");
        System.out.println("editableCopy=" + list);
        System.out.println("sourceSize=" + source.size());
    }
}
```

Biên dịch và chạy bằng JDK hỗ trợ Java 17 trở lên:

```shell
javac --release 17 -encoding UTF-8 TargetSemanticsDemo.java
java TargetSemanticsDemo
```

Đầu ra:

```text
source=[b, a, b, null]
list=[b, a, b, null]
insertionSet=[b, a, null]
hashSize=3
hashContainsNull=true
roundTrip=[b, a, null]
naturalTree=NullPointerException
nullableSorted=[null, a, b]
sortedSequence=[null, a, b, b]
equalsDistinct=2
comparatorDistinct=1
identitySize=2
equalsCopySize=1
editableCopy=[b, a, b, null, tail]
sourceSize=4
```

Trong kết quả:

- `list` giữ đủ bốn vị trí, gồm hai lần `b` và một `null`.
- `insertionSet` giữ lần gặp đầu tiên của mỗi giá trị theo `equals`.
- `hashSet` chỉ được kiểm tra kích thước và khả năng chứa `null`; chương trình không dựng một đầu ra có thứ tự mà API không cam kết.
- `nullableSorted` vừa sắp xếp vừa gộp, còn `sortedSequence` sắp xếp nhưng giữ hai lần `b`.
- `equalsDistinct` và `comparatorDistinct` cho thấy thay quy tắc trùng có thể đổi số phần tử.
- `identitySize` và `equalsCopySize` cho thấy hai đối tượng chuỗi khác nhau có thể bị gộp khi chuyển sang quy tắc bằng nhau theo giá trị.

Nguồn được tạo bằng `Arrays.asList`, nên có kích thước cố định. Bản sao `ArrayList` vẫn cho thêm `"tail"`; nguồn giữ kích thước bốn. Đây là một thay đổi về khả năng thao tác, dù trước lời gọi `add` cả hai dãy có cùng nội dung.

Bộ so sánh `nullsFirst` trong ví dụ là lựa chọn tường minh để xử lý `null`. Nó không có nghĩa mọi `TreeSet` mặc định đều nhận `null`.

## 4. Bộ so sánh và giới hạn của nguồn không tự đi theo mọi bản sao

### 4.1. Cùng một đối tượng, khác overload, khác thứ tự

`TreeSet` có các hàm khởi tạo (constructor) được nạp chồng (overload): một hàm nhận `Collection`, hàm khác nhận `SortedSet`. Chúng có hợp đồng khác nhau:

| Cách tạo | Quy tắc thứ tự |
| --- | --- |
| `new TreeSet<>(collection)` | Thứ tự tự nhiên của phần tử |
| `new TreeSet<>(sortedSet)` | Giữ quy tắc thứ tự của tập hợp được sắp xếp được cung cấp |
| `new TreeSet<>(comparator)` rồi `addAll` | Dùng bộ so sánh được truyền tường minh |

Lựa chọn overload diễn ra dựa trên kiểu mà trình biên dịch thấy ở biểu thức đối số. Một đối tượng `TreeSet` dùng thứ tự giảm dần nhưng được truyền qua biến `Collection<Integer>` không buộc hàm khởi tạo nhận `Collection` tự sao chép bộ so sánh của nó.

Điều này có thể chỉ làm đổi thứ tự nếu phần tử có thứ tự tự nhiên. Với một kiểu đối tượng chỉ được so sánh nhờ bộ so sánh tùy chỉnh, mất bộ so sánh còn có thể khiến việc thêm phần tử vào cây thất bại bằng `ClassCastException`.

Nếu phương thức chuyển đổi phải giữ sắp xếp, có thể thiết kế chữ ký nhận `SortedSet` phù hợp hoặc nhận thêm `Comparator`. Đừng giấu thông tin cần thiết trong một tham số `Collection` rồi kỳ vọng mọi hàm khởi tạo đoán lại được.

`TreeMap` có cặp hàm khởi tạo tương tự nhận `Map` và `SortedMap`. `Map` không thuộc giao diện `Collection`, nhưng nguyên tắc chọn đích và giữ bộ so sánh cũng áp dụng khi chuyển đổi các ánh xạ.

[`TreeSet`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html) và [`TreeMap`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html) công bố rõ hợp đồng của từng hàm khởi tạo. Cần đọc theo overload thực sự được gọi.

### 4.2. Giữ bộ so sánh chưa có nghĩa giữ giới hạn của khung nhìn

Một khung nhìn (view) lấy từ `subSet` có thể chỉ cho phép các giá trị nằm trong một khoảng. Tạo `new TreeSet<>(view)` sao chép các phần tử hiện có và có thể giữ bộ so sánh nhờ overload nhận `SortedSet`. Kết quả là một cây mới, không tự mang theo giới hạn khoảng của khung nhìn gốc.

Nếu API cần một đích độc lập về cấu trúc nhưng vẫn giới hạn miền giá trị, phải áp dụng giới hạn đó một cách tường minh sau khi xây dựng cấu trúc riêng, hoặc đặt quy tắc xác thực ở một lớp API phù hợp. Một bản sao hiện chứa toàn giá trị hợp lệ không tự từ chối giá trị ngoài miền trong tương lai.

Tương tự, sao chép nội dung hiện tại của một map có thứ tự truy cập không có nghĩa sao chép luôn chính sách cập nhật thứ tự khi `get`. Chẳng hạn, hàm khởi tạo `LinkedHashMap(Map)` tạo map theo thứ tự chèn; muốn chính sách thứ tự truy cập phải chọn cách cấu hình tương ứng. [API `LinkedHashMap`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html) tách hai chính sách ấy.

### 4.3. Chương trình 2: Comparator, overload và giới hạn khoảng

Tệp `SortedCopyContractDemo.java`:

```java
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableSet;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class SortedCopyContractDemo {
    public static void main(String[] args) {
        TreeSet<Integer> source = new TreeSet<>(Collections.reverseOrder());
        source.addAll(java.util.List.of(5, 9, 7));

        SortedSet<Integer> sortedInput = source;
        Collection<Integer> generalInput = source;
        TreeSet<Integer> preserved = new TreeSet<>(sortedInput);
        TreeSet<Integer> natural = new TreeSet<>(generalInput);
        TreeSet<Integer> explicit = new TreeSet<>(source.comparator());
        explicit.addAll(generalInput);

        preserved.add(6);
        natural.add(6);
        explicit.add(6);
        System.out.println("source=" + source);
        System.out.println("preserved=" + preserved);
        System.out.println("natural=" + natural);
        System.out.println("explicit=" + explicit);

        NavigableSet<Integer> bounded = source.subSet(9, true, 5, false);
        TreeSet<Integer> unboundedCopy = new TreeSet<>(bounded);
        try {
            bounded.add(3);
            throw new AssertionError("Range accepted an out-of-range value");
        } catch (IllegalArgumentException expected) {
            System.out.println("rangeAdd=IllegalArgumentException");
        }
        unboundedCopy.add(3);
        System.out.println("bounded=" + bounded);
        System.out.println("unboundedCopy=" + unboundedCopy);

        TreeMap<Integer, String> sortedMap =
                new TreeMap<>(Collections.reverseOrder());
        sortedMap.put(1, "one");
        sortedMap.put(3, "three");
        sortedMap.put(2, "two");

        SortedMap<Integer, String> sortedMapInput = sortedMap;
        Map<Integer, String> generalMapInput = sortedMap;
        TreeMap<Integer, String> sameOrder = new TreeMap<>(sortedMapInput);
        TreeMap<Integer, String> naturalOrder = new TreeMap<>(generalMapInput);
        System.out.println("mapSortedInput=" + sameOrder);
        System.out.println("mapGeneralInput=" + naturalOrder);
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 SortedCopyContractDemo.java
java SortedCopyContractDemo
```

Đầu ra:

```text
source=[9, 7, 5]
preserved=[9, 7, 6, 5]
natural=[5, 6, 7, 9]
explicit=[9, 7, 6, 5]
rangeAdd=IllegalArgumentException
bounded=[9, 7]
unboundedCopy=[9, 7, 3]
mapSortedInput={3=three, 2=two, 1=one}
mapGeneralInput={1=one, 2=two, 3=three}
```

Hai biến `sortedInput` và `generalInput` cùng trỏ tới `source`. Chúng làm hàm khởi tạo được chọn khác nhau. Khi thêm `6`, một đích tiếp tục duy trì thứ tự giảm dần, còn đích chọn thứ tự tự nhiên duy trì tăng dần.

Biến `explicit` cho thấy cách cung cấp bộ so sánh rõ ràng khi đầu vào được nhìn như collection tổng quát. Khi `comparator()` trả về `null`, điều đó biểu diễn thứ tự tự nhiên; nó không tự là một lỗi thiếu cấu hình.

Trong khoảng giảm dần từ `9` đến trước `5`, view `bounded` chứa `9` và `7`. Thêm `3` qua khung nhìn bị từ chối. Bản sao cây lại nhận `3`, vì giới hạn khoảng không thuộc hợp đồng của cây mới.

Phần map xác nhận cùng một khác biệt: nhận nguồn qua `SortedMap` giữ thứ tự giảm dần, còn nhận qua `Map` tạo cây theo thứ tự tự nhiên. Các ánh xạ hiện có giống nhau nhưng hợp đồng thứ tự đã khác.

## 5. Quyền sửa, tính độc lập và trạng thái phần tử là ba điều khác nhau

Khi bàn về “bản sao không thay đổi”, cần diễn đạt chính xác điều muốn giữ:

| Câu hỏi | Ý nghĩa |
| --- | --- |
| Có được gọi `add/remove/set` qua kết quả không? | Quyền sửa cấu trúc qua tham chiếu được cung cấp |
| Nguồn đổi cấu trúc thì kết quả có đổi theo không? | Kết quả là khung nhìn sống hay độc lập về cấu trúc |
| Đối tượng phần tử bị sửa thì có nhìn thấy qua kết quả không? | Các phần tử có đang được chia sẻ và có thể thay đổi không? |

`Collections.unmodifiableList(source)` chặn các thao tác sửa qua lớp bọc nhưng vẫn đọc dữ liệu từ `source`. Nếu bên khác còn được sửa nguồn, kết quả có thể thay đổi theo.

`List.copyOf(source)` cung cấp list không cho sửa, không phản ánh các lần thay đổi cấu trúc nguồn về sau. Tuy nhiên, nó cấm phần tử `null` và không tự sao chép sâu từng đối tượng phần tử.

`new ArrayList<>(readOnlySource)` lại tạo một list cho phép sửa. Điều này có thể đúng khi cần vùng làm việc riêng, nhưng không được coi là đã giữ chính sách chỉ đọc của API nguồn.

Nếu cần giữ dãy phần tử, chấp nhận `null` và không cho người nhận sửa cấu trúc, có thể tạo một `ArrayList` riêng rồi bọc nó bằng `unmodifiableList`. Không trao thêm tham chiếu có quyền sửa tới list riêng đó nếu hợp đồng cần sự ổn định về cấu trúc.

[`Collections.unmodifiableList`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)) và [`List.copyOf`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)) phục vụ hai quan hệ với nguồn khác nhau.

### 5.1. Kết quả không cho sửa không làm đối tượng phần tử thành bất biến

Các hàm khởi tạo sao chép collection thông thường chủ yếu sao chép tham chiếu. Một `Customer` có trường địa chỉ thay đổi được vẫn có thể được cùng nguồn và đích tham chiếu tới. Sửa đối tượng ấy không giống với thêm hay xóa phần tử của collection.

Nếu yêu cầu là một ảnh chụp dữ liệu nghiệp vụ ổn định hoàn toàn, cần xét cả mô hình phần tử: dùng đối tượng bất biến hoặc tạo bản sao phù hợp cho từng đối tượng. Chỉ đổi lớp collection không đáp ứng được yêu cầu sâu đó.

Tương tự, đưa một đối tượng có trạng thái thay đổi vào set/map không sửa được một thiết kế `equals/hashCode` không ổn định. Nếu các trường tham gia băm hoặc so sánh thay đổi khi đối tượng đang là phần tử/khóa, collection đích có thể không còn đáp ứng cách tra cứu mà ứng dụng kỳ vọng.

### 5.2. Không suy ra giữ thứ tự từ tên copyOf

`Set.copyOf` trả về một set không cho sửa và cấm `null`, nhưng không hứa giữ thứ tự gặp của một list hay thứ tự chèn của `LinkedHashSet`. Khi đầu vào có phần tử trùng, một đại diện được giữ mà API không hứa đó là đại diện gặp đầu tiên.

Nếu yêu cầu là “không trùng, giữ lần gặp đầu, thứ tự xác định theo nguồn, không cho sửa qua kết quả”, có thể chủ động tạo `LinkedHashSet` rồi bọc bằng `Collections.unmodifiableSet`. Nếu còn cần giao diện sorted/navigable, chọn lớp bọc bảo toàn chính giao diện ấy thay vì chỉ trả một set tổng quát.

[`Set.copyOf`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Set.html#copyOf(java.util.Collection)) định nghĩa hợp đồng riêng. Một hàm có tên “copy” không cam kết giữ mọi đặc điểm của nguồn, cũng không bắt buộc tạo một đối tượng mới nếu có thể đáp ứng hợp đồng bằng cách khác.

## 6. Những khả năng chuyên biệt cần được chọn tường minh

### 6.1. EnumSet: kiểu enum và đầu vào rỗng

`EnumSet` phù hợp khi miền giá trị là một kiểu enum và cần ý nghĩa tập hợp. Nó không giữ các lần lặp và duyệt theo thứ tự khai báo hằng enum, không theo thứ tự tùy ý của list nguồn.

Với một collection rỗng thông thường, `EnumSet.copyOf(collection)` không có phần tử để suy ra kiểu enum và ném `IllegalArgumentException`. Có thể cung cấp kiểu tường minh qua `EnumSet.noneOf(Permission.class)`, rồi thêm các phần tử.

Không diễn giải thành “mọi EnumSet rỗng đều không sao chép được”. Một `EnumSet` đã giữ thông tin kiểu của nó ngay cả khi rỗng. Hợp đồng `copyOf` cũng xử lý nguồn là một đối tượng `EnumSet` theo cách phù hợp, kể cả khi nhận qua overload `Collection`.

[API `EnumSet`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/EnumSet.html) quy định rõ kiểu phần tử, thứ tự và khác biệt của nguồn rỗng.

### 6.2. Hàng đợi ưu tiên: giữ quy tắc lấy ra, không chỉ sao chép thứ tự duyệt

`PriorityQueue` giữ phần tử để lấy ra theo bộ so sánh. Iterator của nó không cam kết duyệt thành một dãy đã sắp xếp. Vì vậy, `new ArrayList<>(priorityQueue)` không có nghĩa đã tạo danh sách theo thứ tự các lần `poll`.

Nếu cần một bản sao vẫn xử lý theo ưu tiên, hàm khởi tạo nhận một `PriorityQueue` khác giữ quy tắc thứ tự tương ứng. Muốn một list theo thứ tự xử lý, có thể lấy dần từ một bản sao hàng đợi để không làm cạn nguồn.

Cũng không nên tổng quát hóa rằng mọi hàm khởi tạo nhận `Collection` đều bỏ bộ so sánh. `PriorityQueue(Collection)` có quy tắc riêng khi nguồn thực tế là `SortedSet` hoặc `PriorityQueue`. Đây là lý do phải đọc API của loại đích cụ thể.

Nếu các phần tử có cùng mức ưu tiên còn phải được xử lý theo thứ tự đến, cần một quy tắc phụ và thông tin đủ để phân biệt thứ tự ấy. Chỉ chọn `PriorityQueue` không tự tạo cam kết ổn định cho mọi cặp cùng ưu tiên.

[API `PriorityQueue`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html) phân biệt thứ tự lấy phần tử với thứ tự duyệt và mô tả các hàm khởi tạo.

### 6.3. Dung lượng, chờ và truy cập đồng thời không nằm trong các phần tử

Chuyển một `ArrayBlockingQueue` đầy sang `ArrayDeque` có thể giữ dãy phần tử đang quan sát nhưng mất giới hạn dung lượng cố định và các thao tác chờ của hàng đợi có khả năng chờ (blocking queue).

Giá trị dung lượng ban đầu truyền cho `ArrayDeque` cũng không biến nó thành hàng đợi có giới hạn cố định như `ArrayBlockingQueue`. Hai loại cùng có thể phục vụ một luồng công việc theo FIFO nhưng có hợp đồng nhận thêm dữ liệu khác nhau.

Tương tự, tạo một `ArrayList` từ một collection an toàn cho nhiều luồng không làm list mới tự được đồng bộ. Chính sách của đích phải được chọn lại theo cách mà các luồng sẽ sử dụng kết quả.

Nếu cần đích có giới hạn, phải chọn dung lượng, chính sách khi đầy và các thao tác phù hợp. Nếu cần truy cập đồng thời, phải xét thêm các chuỗi thao tác nguyên tử, không chỉ việc một phương thức đơn lẻ có cơ chế đồng bộ hay không.

[`ArrayDeque`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html) và [`ArrayBlockingQueue`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html) công bố các chính sách rất khác nhau dù đều có thể chứa một dãy công việc.

### 6.4. Chương trình 3: Chỉ đọc, enum, ưu tiên và dung lượng

Tệp `TargetCapabilitiesDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ArrayBlockingQueue;

public class TargetCapabilitiesDemo {
    private enum Permission {
        READ, WRITE
    }

    private static void failsWith(String name,
            Class<? extends RuntimeException> expected, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException failure) {
            if (!expected.isInstance(failure)) {
                throw new AssertionError(name + " threw the wrong exception", failure);
            }
            System.out.println(name + "=" + failure.getClass().getSimpleName());
            return;
        }
        throw new AssertionError(name + " did not fail");
    }

    private static void ownership() {
        List<String> source = new ArrayList<>(List.of("alpha"));
        List<String> live = Collections.unmodifiableList(source);
        List<String> snapshot = List.copyOf(source);
        List<String> editable = new ArrayList<>(snapshot);

        source.add("beta");
        editable.add("local");
        System.out.println("live=" + live);
        System.out.println("snapshot=" + snapshot);
        System.out.println("editable=" + editable);
        failsWith("snapshotAdd", UnsupportedOperationException.class,
                () -> snapshot.add("other"));

        List<String> nullable = Arrays.asList("value", null);
        failsWith("copyOfNull", NullPointerException.class,
                () -> List.copyOf(nullable));
        List<String> nullableSnapshot =
                Collections.unmodifiableList(new ArrayList<>(nullable));
        System.out.println("nullableSnapshot=" + nullableSnapshot);
    }

    private static void enumTarget() {
        List<Permission> input =
                List.of(Permission.WRITE, Permission.READ, Permission.WRITE);
        System.out.println("enumInput=" + input);
        System.out.println("enumTarget=" + EnumSet.copyOf(input));

        List<Permission> empty = List.of();
        failsWith("enumEmptyList", IllegalArgumentException.class,
                () -> EnumSet.copyOf(empty));
        EnumSet<Permission> safeEmpty = EnumSet.noneOf(Permission.class);
        safeEmpty.addAll(empty);
        System.out.println("enumExplicitEmpty=" + safeEmpty);
        System.out.println("enumEmptySetCopy=" + EnumSet.copyOf(safeEmpty));
    }

    private static void queueTarget() {
        PriorityQueue<Integer> priority =
                new PriorityQueue<>(Collections.reverseOrder());
        priority.addAll(List.of(2, 9, 4));
        PriorityQueue<Integer> priorityCopy = new PriorityQueue<>(priority);
        List<Integer> processingOrder = new ArrayList<>();
        while (!priorityCopy.isEmpty()) {
            processingOrder.add(priorityCopy.remove());
        }
        System.out.println("priorityProcessing=" + processingOrder);
        System.out.println("prioritySourceSize=" + priority.size());

        failsWith("dequeNull", NullPointerException.class,
                () -> new ArrayDeque<>(Arrays.asList("job", null)));

        ArrayBlockingQueue<String> bounded =
                new ArrayBlockingQueue<>(2, false, List.of("first", "second"));
        ArrayDeque<String> unboundedCopy = new ArrayDeque<>(bounded);
        System.out.println("boundedOffer=" + bounded.offer("third"));
        System.out.println("unboundedOffer=" + unboundedCopy.offerLast("third"));
        System.out.println("unboundedCopy=" + unboundedCopy);
    }

    public static void main(String[] args) {
        ownership();
        enumTarget();
        queueTarget();
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 TargetCapabilitiesDemo.java
java TargetCapabilitiesDemo
```

Đầu ra:

```text
live=[alpha, beta]
snapshot=[alpha]
editable=[alpha, local]
snapshotAdd=UnsupportedOperationException
copyOfNull=NullPointerException
nullableSnapshot=[value, null]
enumInput=[WRITE, READ, WRITE]
enumTarget=[READ, WRITE]
enumEmptyList=IllegalArgumentException
enumExplicitEmpty=[]
enumEmptySetCopy=[]
priorityProcessing=[9, 4, 2]
prioritySourceSize=3
dequeNull=NullPointerException
boundedOffer=false
unboundedOffer=true
unboundedCopy=[first, second, third]
```

`live` nhìn thấy `beta` được thêm vào nguồn; `snapshot` vẫn giữ dãy ban đầu. Bản sao `editable` được phép thêm một phần tử riêng. Ba kết quả đáp ứng ba nhu cầu khác nhau.

`nullableSnapshot` cho thấy một kết quả không cho sửa cấu trúc vẫn có thể giữ `null` khi cách xây dựng được chọn có chủ ý. `List.copyOf` không đáp ứng chính sách phần tử ấy.

Đích enum chuyển `[WRITE, READ, WRITE]` thành `[READ, WRITE]`: vừa gộp trùng, vừa dùng thứ tự khai báo. Phần rỗng chứng minh khác biệt giữa collection thông thường rỗng và một `EnumSet` đã biết kiểu.

`priorityProcessing` được tạo bằng cách lấy hết từ bản sao hàng đợi ưu tiên, nên phản ánh thứ tự xử lý giảm dần. Nguồn vẫn có ba phần tử. Chương trình không dựa vào một dạng sắp xếp nội bộ của iterator.

Cuối cùng, cùng đang có hai công việc, hàng đợi giới hạn từ chối lần `offer` thứ ba còn bản sao `ArrayDeque` chấp nhận. Giữ hai phần tử ban đầu không bảo toàn chính sách dung lượng.

## 7. Dùng Stream vẫn phải chọn hợp đồng đích

### 7.1. Một phương thức thu thập không phải lời hứa về mọi lớp triển khai

Các cách sau không có cùng hợp đồng:

| Cách nhận kết quả | Cam kết cần hiểu |
| --- | --- |
| `collect(Collectors.toList())` | Trả một `List`, không bảo đảm lớp cụ thể, quyền sửa, khả năng tuần tự hóa hay an toàn nhiều luồng |
| `collect(Collectors.toSet())` | Trả một `Set`, không bảo đảm thứ tự hoặc lớp cụ thể |
| `stream.toList()` | List không cho sửa, giữ thứ tự gặp của luồng nếu luồng có thứ tự |
| `collect(Collectors.toCollection(factory))` | Chủ động chọn collection bằng hàm tạo được cung cấp |

Không ép kết quả `Collectors.toList()` thành `ArrayList` rồi coi một lớp triển khai đang quan sát được là hợp đồng API. Nếu cần chắc chắn có một `ArrayList` cho phép sửa, hãy yêu cầu nó qua hàm tạo.

Tương tự, `Collectors.toSet()` không phải cách diễn đạt yêu cầu giữ thứ tự gặp đầu tiên. Cần một đích có hợp đồng thích hợp như `LinkedHashSet`, đồng thời nguồn/luồng phải có thứ tự cần bảo toàn.

[`Collectors`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Collectors.html) nêu giới hạn của các bộ thu thập tổng quát; [`Stream.toList`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#toList()) cam kết kết quả không cho sửa.

### 7.2. Chọn hàm tạo khi cần quyền kiểm soát

Đoạn sau là phần thân minh họa, không phải chương trình chạy độc lập; cần các kiểu collection, `Comparator` và `Collectors` tương ứng:

```java
List<Integer> input = List.of(3, 1, 3, 2);

List<Integer> sequence = input.stream()
        .collect(Collectors.toCollection(ArrayList::new));

Set<Integer> firstEncounter = input.stream()
        .collect(Collectors.toCollection(LinkedHashSet::new));

NavigableSet<Integer> descending = input.stream()
        .collect(Collectors.toCollection(
                () -> new TreeSet<Integer>(Comparator.reverseOrder())));
```

Ba kết quả lần lượt biểu diễn:

- `sequence`: `[3, 1, 3, 2]`, giữ mọi vị trí và thứ tự.
- `firstEncounter`: `[3, 1, 2]`, gộp giá trị trùng theo `equals` và giữ thứ tự gặp đầu.
- `descending`: `[3, 2, 1]`, gộp trùng và duy trì thứ tự giảm dần.

Hàm tạo phải cấp một collection mới, rỗng, phù hợp cho việc tích lũy kết quả. Không trả lại một đối tượng collection đang chứa dữ liệu dùng chung từ bên ngoài rồi trông chờ phép thu thập tự biến nó thành vùng làm việc độc lập.

Ví dụ dùng một luồng tuần tự xuất phát từ list có thứ tự. Nếu quy trình trước đó đã loại bỏ cam kết thứ tự, chọn `LinkedHashSet` ở cuối không tìm lại được thứ tự lịch sử đã mất.

### 7.3. Khi đích là Map, phải xác định chính sách khóa trùng

Chuyển các bản ghi thành map còn cần xác định khóa. Nếu hai bản ghi tạo ra cùng khóa, phải biết điều gì được phép xảy ra: báo lỗi, giữ bản ghi đầu, giữ bản ghi cuối, cộng gộp hay lưu một danh sách các bản ghi dưới khóa đó.

Dạng `Collectors.toMap(keyMapper, valueMapper)` báo `IllegalStateException` khi gặp khóa trùng theo hợp đồng của nó. Việc bổ sung một hàm hợp nhất là quyết định nghiệp vụ, không chỉ là cách làm cho ngoại lệ biến mất.

Đoạn minh họa sau chọn **giữ từ đầu tiên của mỗi độ dài** và dùng `LinkedHashMap` để giữ thứ tự xuất hiện của các khóa trong ví dụ:

```java
List<String> words = List.of("ax", "cat", "by");
Map<Integer, String> firstWordByLength = words.stream().collect(
        Collectors.toMap(
                String::length,
                Function.identity(),
                (first, later) -> first,
                LinkedHashMap::new));
```

Với dữ liệu đã cho, kết quả là `{2=ax, 3=cat}`. Từ `"by"` có cùng độ dài với `"ax"`, nên bị bỏ theo chính sách đã viết. Nếu mọi từ đều phải được giữ, chính sách này sai dù map được tạo thành công; cần một mô hình đích như `Map<Integer,List<String>>` hoặc một cấu trúc phù hợp khác.

Khi cần thứ tự khóa, chọn hàm tạo map có hợp đồng thứ tự tương ứng. Khi cần thứ tự sắp xếp, xác định thêm bộ so sánh và quy tắc nhận diện khóa. Không xem loại map, hàm tạo khóa và chính sách hợp nhất là ba quyết định độc lập về ý nghĩa dữ liệu.

## 8. Một quy trình chọn đích có thể kiểm tra được

### 8.1. Viết yêu cầu bằng các mệnh đề cụ thể

Thay cho yêu cầu mơ hồ “trả về bản sao collection”, có thể viết:

- Mọi dòng phải được giữ, kể cả các dòng có giá trị bằng nhau.
- Dãy kết quả đi theo thứ tự nhận vào.
- Phần tử `null` được chấp nhận hoặc bị từ chối theo chính sách đã nêu.
- Bên nhận không được thêm, xóa hoặc thay một phần tử của cấu trúc.
- Nguồn thay đổi cấu trúc về sau không làm kết quả thay đổi.
- Quyền sửa trạng thái của từng đối tượng phần tử được quy định riêng.

Với những yêu cầu này và nguồn có thứ tự, một bản `ArrayList` riêng được bọc bằng `unmodifiableList` có thể phù hợp. Chọn `Set` sẽ vi phạm yêu cầu giữ mọi dòng; chọn `List.copyOf` không phù hợp nếu bắt buộc giữ `null`; chỉ bọc trực tiếp nguồn không đáp ứng tính độc lập về cấu trúc.

Nếu yêu cầu thay thành “loại trùng, giữ lần gặp đầu”, đích khác có thể phù hợp. Khi sửa yêu cầu, cần xét lại cấu trúc đích thay vì thêm các phép ép kiểu để giữ nguyên một lựa chọn cũ.

### 8.2. Tách biến đổi dữ liệu khỏi chính sách chứa kết quả

Chuẩn hóa chữ hoa/thường, chuyển kiểu, loại `null`, gộp bản ghi hoặc lọc giá trị ngoài miền đều là những phép biến đổi có thể thay đổi ý nghĩa dữ liệu. Chúng phải được nêu rõ, không nên ẩn dưới một thao tác được gọi là “copy”.

Một luồng xử lý rõ ràng có thể là:

```text
Đầu vào
  -> xác thực điều kiện đầu vào
  -> biến đổi có chủ ý, nếu nghiệp vụ yêu cầu
  -> tích lũy vào cấu trúc đích được chọn
  -> áp dụng giới hạn/quyền sửa cần cung cấp
  -> công bố kết quả sau khi hoàn tất
```

Nếu xây dựng đích thất bại vì `null`, phần tử không so sánh được hoặc giới hạn dung lượng, không công bố một kết quả còn dở như thể đã đáp ứng đầy đủ hợp đồng. Khi thêm vào một collection đã tồn tại, cũng không mặc nhiên coi `addAll` là một giao dịch tự hoàn tác mọi thay đổi khi có ngoại lệ.

Việc xây dựng một kết quả riêng trước khi công bố giúp kiểm soát trạng thái mà bên nhận có thể quan sát. Nó không tự tạo tính nguyên tử cho các tác dụng phụ bên ngoài hoặc một nguồn đang bị sửa đồng thời.

### 8.3. Kiểm tra đúng các tính chất mà hợp đồng đã chọn

Chỉ kiểm tra `containsAll` là chưa đủ để chứng minh một phép chuyển đổi bảo toàn dãy. Phép kiểm tra ấy không thể hiện vị trí và không đếm đầy đủ số lần xuất hiện.

Có thể chọn các phép kiểm tra phù hợp hơn:

| Yêu cầu | Cách quan sát/kiểm tra phù hợp |
| --- | --- |
| Giữ toàn bộ dãy | So sánh các list theo đúng thứ tự và số lần xuất hiện |
| Giữ số lượng nhưng không cần thứ tự | So sánh tần suất theo quy tắc bằng nhau đã chọn |
| Giữ thứ tự duyệt của set | Quan sát một dãy được tạo từ iterator; `Set.equals` không kiểm tra thứ tự |
| Giữ quy tắc sắp xếp | Kiểm tra các giá trị đại diện và việc thêm phần tử mới, không chỉ ảnh chụp ban đầu |
| Giữ giới hạn khoảng | Thử một giá trị trong miền và một giá trị ngoài miền qua API được cung cấp |
| Giữ quyền chỉ đọc | Kiểm tra các đường sửa mà giao diện công bố |
| Giữ tính độc lập với nguồn | Sửa cấu trúc nguồn có kiểm soát và quan sát kết quả |
| Giữ thứ tự xử lý ưu tiên | Lấy dần từ một bản sao hàng đợi thay vì suy luận từ iterator |
| Giữ chính sách rỗng/null/dung lượng | Kiểm tra riêng những trường hợp biên này |

Không nên kiểm tra thứ tự của một lớp vốn không cam kết thứ tự rồi cố định kết quả của một lần chạy thành yêu cầu. Cũng không dùng tên lớp nội bộ hoặc một phép so sánh tham chiếu để thay cho kiểm tra hợp đồng của hàm tạo có quyền tái sử dụng kết quả.

### 8.4. Nguồn an toàn nhiều luồng không bảo đảm một bản chụp nguyên tử toàn cục

Một nguồn có thể cung cấp iterator với mức nhất quán phù hợp cho việc duyệt đồng thời, nhưng phép duyệt ấy không nhất thiết tương ứng với một ảnh chụp toàn bộ dữ liệu ở đúng một thời điểm.

Chọn một đích an toàn nhiều luồng giải quyết chính sách truy cập **đích**. Nó không tự tăng mức nhất quán của thao tác **đọc nguồn**. Nếu dữ liệu xuất ra phải phản ánh một trạng thái nghiệp vụ thống nhất, cần một giao thức phối hợp hoặc cơ chế ảnh chụp được nguồn hỗ trợ.

Ví dụ, [`ConcurrentHashMap`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html) mô tả riêng hành vi của các thao tác tổng hợp và iterator trong lúc cập nhật đồng thời. Không suy từ việc từng thao tác của map an toàn nhiều luồng thành một cam kết “copy mọi mục là giao dịch nguyên tử”.

Một lần chạy không gặp ngoại lệ cũng không phải bằng chứng rằng các yêu cầu đồng thời đã được đáp ứng.

### 8.5. Xét chi phí sau khi đã chọn đúng ngữ nghĩa

Các cấu trúc có chi phí truy cập, cập nhật, sắp xếp và bộ nhớ khác nhau. Nhưng một đích nhanh hơn không phải thay thế hợp lệ nếu nó làm mất số lần xuất hiện, thứ tự hay khả năng mà API cần.

Nếu vừa cần giữ mọi dòng theo thứ tự vừa cần tra cứu nhanh một thông tin, có thể cần một list làm dữ liệu chính và một chỉ mục riêng. Khi đó phải có quy tắc cập nhật nhất quán giữa hai cấu trúc. Thay toàn bộ list bằng set không giải quyết được yêu cầu giữ đầy đủ các dòng.

Tương tự, tạo `TreeSet` chỉ để lấy một dãy sắp xếp có thể vừa tốn công duy trì cây, vừa làm mất phần tử trùng. Chọn list rồi sắp xếp có thể diễn đạt đúng nhu cầu hơn. Quyết định hiệu năng nên được đánh giá trên tập thao tác thật, sau khi các điều kiện bảo toàn dữ liệu đã được thỏa mãn.

## 9. Thực hành lựa chọn và giải thích

### Tình huống 1: Dãy giao dịch có các dòng bằng nhau

Đầu vào có hai giao dịch cùng giá trị nhưng chúng là hai lần phát sinh khác nhau. API cần giữ mọi dòng và thứ tự nhận vào.

**Phân tích:** không chuyển thành set chỉ vì hai dòng bằng nhau theo đối tượng hiện tại. Một list phù hợp hơn với ý nghĩa dãy. Nếu cần phân biệt danh tính nghiệp vụ, nên biểu diễn bằng mã giao dịch phù hợp, không dựa vào tình cờ hai đối tượng có địa chỉ khác nhau.

### Tình huống 2: Danh sách nhãn không trùng, giữ lần gặp đầu

Đầu vào là một list có thứ tự xác định, có nhãn lặp và cho phép một nhãn `null`. Người nhận chỉ cần đọc kết quả.

**Phân tích:** chọn chính sách gộp theo `equals`, tích lũy vào `LinkedHashSet`, rồi cung cấp lớp bọc không cho sửa nếu cần. `HashSet` không giữ cam kết thứ tự; `Set.copyOf` không đáp ứng chính sách `null` và không hứa giữ thứ tự gặp.

### Tình huống 3: Cần sắp xếp mọi dòng, không loại bản ghi

Đầu vào là các kết quả học tập, có nhiều bản ghi nhận cùng điểm. Muốn trả về dãy theo điểm nhưng vẫn giữ từng bản ghi.

**Phân tích:** một list được sắp xếp giữ được nhiều bản ghi có cùng khóa sắp xếp. Nếu dùng `TreeSet` với bộ so sánh chỉ theo điểm, những bản ghi so sánh bằng `0` có thể bị gộp. Nếu có yêu cầu thứ tự giữa các bản ghi cùng điểm, quy tắc phụ cũng cần được định nghĩa.

### Tình huống 4: API sao chép một tập hợp được sắp xếp giảm dần

Một phương thức nhận `Collection<Integer>` nhưng người gọi mong đích vẫn giảm dần và hỗ trợ truy vấn phạm vi.

**Phân tích:** hợp đồng đầu vào đang giấu thông tin cần thiết. Có thể yêu cầu `SortedSet`/`NavigableSet` phù hợp hoặc nhận bộ so sánh tường minh. Chọn `ArrayList` chỉ giữ dãy hiện tại; gọi hàm khởi tạo `TreeSet(Collection)` lại chọn thứ tự tự nhiên. Nếu còn phải giữ giới hạn của khung nhìn, cần áp dụng giới hạn riêng.

### Tình huống 5: Kết quả quyền truy cập enum có thể rỗng

Đầu vào là collection các quyền enum; có trường hợp không có quyền nào.

**Phân tích:** cung cấp kiểu enum tường minh để tạo đích rỗng, rồi thêm dữ liệu. Không dựa vào một phần tử đầu tiên vốn có thể không tồn tại. Xác định rõ rằng kết quả là tập hợp theo enum, không giữ lần lặp hay thứ tự tùy ý của đầu vào.

### Tình huống 6: Sao chép hàng đợi đang đầy để bàn giao xử lý

Nguồn có giới hạn dung lượng và dùng cơ chế chờ khi đầy/rỗng. Bên nhận còn tiếp tục thêm việc.

**Phân tích:** chỉ sao chép các phần tử vào `ArrayDeque` không giữ giới hạn hoặc cơ chế chờ. Phải chọn chính sách hàng đợi đích, dung lượng và giao thức bàn giao. Nếu chỉ cần một bản báo cáo các công việc hiện có, một list riêng lại có thể phù hợp vì không cần cam kết của hàng đợi vận hành.

Một lựa chọn đích có thể được giải thích rõ khi người viết chỉ ra được **điều gì được giữ, điều gì được thay đổi có chủ ý và thao tác tương lai nào chứng minh hợp đồng đó**.


</details>

- [Quay lại đầu trang](#back-to-top)
