<a id="back-to-top"></a>

# Duyệt và chuyển luồng

## Menu
- [Enhanced for để duyệt mảng hoặc Iterable](#iteration-and-control-flow-001)
- [Biến vòng lặp là bản sao giá trị phần tử](#iteration-and-control-flow-002)
- [Gán lại biến enhanced for không thay phần tử mảng](#iteration-and-control-flow-003)
- [Thay đổi đối tượng được tham chiếu qua biến vòng lặp](#iteration-and-control-flow-004)
- [break và continue không nhãn](#iteration-and-control-flow-005)
- [break và continue có nhãn trong vòng lặp lồng](#iteration-and-control-flow-006)
- [return kết thúc phương thức đang chạy](#iteration-and-control-flow-007)
- [Phân biệt break, continue, return và yield](#iteration-and-control-flow-008)
- [Nhận diện câu lệnh không thể tới và ảnh hưởng tới biên dịch](#iteration-and-control-flow-009)

## <a id="iteration-and-control-flow-001">Enhanced for để duyệt mảng hoặc Iterable</a>

<details>
<summary>Xem chi tiết</summary>

# Enhanced `for` để duyệt mảng hoặc `Iterable` trong Java

> **Java core — language-basics / Duyệt và chuyển luồng**  
> **Phiên bản:** JDK 17+, một file Java độc lập, chỉ dùng thư viện chuẩn.  
> **Mục tiêu:** Dùng đúng vòng lặp *enhanced for* (`for-each`) với mảng và `Iterable`; hiểu cách lấy từng phần tử, thứ tự duyệt, tác dụng của biến vòng lặp, những giới hạn khi cần chỉ số hoặc xóa phần tử.

## 1. Cú pháp và ý nghĩa

~~~java
for (ElementType element : source) {
    // Xử lý element.
}
~~~

`source` phải là **mảng** hoặc biểu thức có kiểu triển khai `java.lang.Iterable`. Trong mỗi lượt, Java lấy một phần tử và gán vào biến cục bộ `element`. Vòng lặp dừng sau khi duyệt hết, hoặc bị ngắt sớm bằng `break`, `return` hay ngoại lệ.

~~~java
int[] scores = {3, 5, 8};
int sum = 0;

for (int score : scores) {
    sum += score;
}
System.out.println(sum); // 16
~~~

`for-each` phù hợp khi chỉ cần **giá trị từng phần tử**, không cần tự quản lý chỉ số. Nó không phải cơ chế tự động chạy song song và cũng không trả về một tập kết quả mới; các thao tác trong thân vẫn được thực hiện tuần tự.

Trong Java 17 cũng có thể viết `for (var item : source)` nếu trình biên dịch suy ra được kiểu phần tử. Với bài nhập môn, viết kiểu rõ như `int` hoặc `String` thường dễ đọc hơn.

## 2. Duyệt mảng: từ đầu đến cuối

`for-each` hỗ trợ mảng kiểu nguyên thủy như `int[]`, `double[]` và mảng tham chiếu như `String[]`:

~~~java
String[] names = {"An", "Binh", "Chi"};

for (String name : names) {
    System.out.println(name);
}
// In An, Binh, Chi theo thứ tự chỉ số 0, 1, 2.
~~~

Về hành vi duyệt phần tử, Java xử lý mảng gần tương đương:

~~~java
for (int index = 0; index < names.length; index++) {
    String name = names[index];
    System.out.println(name);
}
~~~

**Khác biệt:** Với `for-each`, chỉ số không có sẵn dưới tên biến riêng. Nếu cần sửa phần tử tại vị trí cụ thể, duyệt ngược, bỏ qua vị trí chẵn/lẻ hoặc biết chính xác index, dùng vòng `for` theo chỉ số.

| Nhu cầu | Cách thường phù hợp |
| --- | --- |
| Đọc mọi phần tử theo thứ tự | `for (T x : array)` |
| Cập nhật phần tử mảng tại từng chỉ số | `for (int i = 0; i < array.length; i++)` |
| Duyệt từ cuối về đầu | `for` theo chỉ số giảm dần |
| Tính tổng, đếm phần tử, tìm max | `for-each` và biến tích lũy |
| Dừng sớm khi gặp giá trị | `break` hoặc `return` |

## 3. Duyệt `Iterable`: Java sử dụng `Iterator`

`Iterable<T>` là giao diện định nghĩa phương thức `iterator()`. Nhiều collection như `List` và `Set` triển khai `Iterable`:

~~~java
java.util.List<String> colors =
        java.util.List.of("red", "green", "blue");

for (String color : colors) {
    System.out.println(color.toUpperCase(java.util.Locale.ROOT));
}
// In RED, GREEN, BLUE.
~~~

Về ý tưởng, Java thực hiện tương đương với việc lấy một `Iterator` rồi lặp:

~~~java
java.util.Iterator<String> it = colors.iterator();

while (it.hasNext()) {
    String color = it.next();
    System.out.println(color.toUpperCase(java.util.Locale.ROOT));
}
~~~

`Iterable` **không đồng nghĩa** `Iterator`: cái đầu cung cấp `iterator()`, cái sau cung cấp `hasNext()` và `next()` để đi qua dữ liệu. Một `Iterator` đơn lẻ không thể tự đặt sau dấu `:` trong enhanced for nếu không có nguồn `Iterable` thích hợp.

**Thứ tự:** Mảng luôn duyệt theo chỉ số tăng. `List` duyệt theo thứ tự phần tử của list. Với các collection khác, thứ tự phụ thuộc vào contract của iterator; **không nên giả định `HashSet` luôn có thứ tự cố định**. Nếu expected stdout cần xác định, chọn mảng, list hoặc nguồn iterable có thứ tự đã định.

`String` không trực tiếp triển khai `Iterable<Character>`; muốn duyệt ký tự của chuỗi, có thể dùng chỉ số với `charAt` hoặc duyệt mảng từ `toCharArray()`. `Map` không trực tiếp là `Iterable`; để duyệt các cặp key/value, dùng `map.entrySet()`.

## 4. Viết `Iterable` đơn giản của riêng mình

Không nhất thiết phải dùng collection có sẵn. Lớp tự định nghĩa có thể triển khai `Iterable<Integer>` và cung cấp một `Iterator<Integer>`:

~~~java
static final class Range implements Iterable<Integer> {
    private final int from, toExclusive;

    Range(int from, int toExclusive) {
        this.from = from;
        this.toExclusive = toExclusive;
    }

    @Override
    public java.util.Iterator<Integer> iterator() {
        return new java.util.Iterator<>() {
            private int nextValue = from;

            @Override
            public boolean hasNext() {
                return nextValue < toExclusive;
            }

            @Override
            public Integer next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                return nextValue++;
            }
        };
    }
}
~~~

Dùng như mảng hoặc list:

~~~java
int total = 0;
for (int value : new Range(2, 5)) {
    total += value;
}
// Duyệt 2, 3, 4; total == 9.
~~~

Đây là minh họa đơn giản với khoảng tăng và `from <= toExclusive`. Mỗi lần gọi `iterator()` tạo một iterator riêng. Đối với dữ liệu thực, iterator cần duy trì đầy đủ quy tắc `hasNext()/next()`, xác định cách báo hết dữ liệu và cân nhắc thay đổi dữ liệu trong lúc duyệt.

## 5. Giá trị biến vòng lặp không phải ô chứa phần tử gốc

Trong enhanced `for`, mỗi lượt biến cục bộ nhận **bản sao giá trị phần tử**. Với `int[]`, gán lại biến không ghi ngược vào mảng:

~~~java
int[] numbers = {2, 4};
for (int number : numbers) {
    number *= 10;
}
// numbers vẫn là [2, 4]; muốn cập nhật, dùng numbers[i] *= 10.
~~~

Với phần tử kiểu tham chiếu, bản sao tham chiếu vẫn trỏ đến **cùng đối tượng**: `StringBuilder.append` có thể sửa trạng thái đối tượng, nhưng `builder = new StringBuilder("X")` chỉ đổi biến cục bộ. Vì vậy hãy phân biệt *thay đổi đối tượng đang có* với *thay thế phần tử nguồn*. Phần chương trình ở mục 8 cho phép quan sát cả hai trường hợp; các bài tiếp theo đào sâu quy tắc sao chép giá trị và thao tác cập nhật.

## 6. `break`, `continue` và thay đổi collection khi duyệt

`continue` bỏ qua phần còn lại của lượt hiện tại và lấy phần tử tiếp theo; `break` thoát toàn bộ vòng enhanced for hiện tại:

~~~java
int[] values = {1, -2, 0, 5, 7};
int counted = 0;

for (int value : values) {
    if (value < 0) {
        continue; // Bỏ số âm.
    }
    if (value == 5) {
        break; // Không xét 5 và 7.
    }
    counted++; // Chỉ tính 1 và 0.
}
// counted == 2.
~~~

**Không nên thêm/xóa phần tử trực tiếp khỏi một `ArrayList` bên trong `for-each`**:

~~~java
// Không nên làm:
// for (String item : list) {
//     if (item.equals("B")) {
//         list.remove(item); // Có thể gây ConcurrentModificationException.
//     }
// }
~~~

Nhiều iterator của collection thông dụng có cơ chế *fail-fast*, có thể ném `ConcurrentModificationException` khi collection bị thay đổi cấu trúc ngoài iterator. Cơ chế này **không phải bảo đảm đồng bộ hoặc cơ chế phát hiện lỗi tuyệt đối**.

Khi cần xóa phần tử trong lúc duyệt một `ArrayList` có thể thay đổi, dùng `Iterator.remove()` sau `next()` nếu iterator hỗ trợ:

~~~java
java.util.List<String> list = new java.util.ArrayList<>(
        java.util.List.of("A", "B", "C", "B"));

java.util.Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (it.next().equals("B")) {
        it.remove();
    }
}
// list = [A, C].
~~~

Nếu chỉ cần lọc và không phải xử lý thêm trong cùng lượt, `list.removeIf(predicate)` cũng là lựa chọn rõ ràng. Với collection bất biến như `List.of(...)`, không thể xóa/thay phần tử; tạo một danh sách mới nếu cần kết quả biến đổi.

## 7. `null`, nguồn rỗng và bẫy khi chọn cách duyệt

- Mảng rỗng `new int[0]` hoặc `List.of()`: thân không chạy lần nào; các biến tích lũy giữ giá trị ban đầu.
- **Nguồn là null**: enhanced for cần truy cập mảng hoặc gọi `iterator()`, nên gây `NullPointerException`. Guard trước vòng khi dữ liệu có thể null.
- **Phần tử null** trong mảng/list vẫn có thể được duyệt: vòng lặp không tự bỏ qua. Khi gọi phương thức trên phần tử đó, kiểm tra null.
- `Iterable` có thể trả iterator chạy trên dữ liệu đắt đỏ hoặc biến động; không giả định mọi nguồn duyệt đều rẻ hay lặp lại được như một list.
- Nếu cần biết index, có thể duy trì bộ đếm thủ công cho Iterable, nhưng với mảng/list truy cập chỉ số thì `for` có chỉ số thường dễ rõ ràng hơn.

| Tình huống | Kết quả / lựa chọn |
| --- | --- |
| `for (int x : new int[0])` | 0 lượt |
| `for (String s : List.of("A", "B"))` | 2 lượt theo thứ tự A, B |
| `for (int x : (int[]) null)` | `NullPointerException` |
| Thay biến `x` trong `for (int x : arr)` | Không sửa phần tử của mảng |
| Thay đổi cấu trúc `ArrayList` lúc for-each | Tránh; dùng iterator hỗ trợ `remove()` hoặc xử lý sau |

## 8. Chương trình Java 17+ chạy độc lập

Tạo file **EnhancedForDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.StringJoiner;

public final class EnhancedForDemo {
    static final class Range implements Iterable<Integer> {
        private final int from, toExclusive;

        Range(int from, int toExclusive) {
            this.from = from;
            this.toExclusive = toExclusive;
        }

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<>() {
                private int nextValue = from;

                @Override
                public boolean hasNext() {
                    return nextValue < toExclusive;
                }

                @Override
                public Integer next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return nextValue++;
                }
            };
        }
    }

    public static void main(String[] args) {
        int[] scores = {3, 5, 8};
        int sum = 0;
        for (int score : scores) {
            sum += score;
        }
        System.out.println("arraySum=" + sum);

        int[] numbers = {2, 4};
        for (int number : numbers) {
            number *= 10;
        }
        System.out.println("arrayReassign=" + Arrays.toString(numbers));

        for (int i = 0; i < numbers.length; i++) {
            numbers[i] *= 10;
        }
        System.out.println("arrayIndexed=" + Arrays.toString(numbers));

        List<String> colors = List.of("red", "green", "blue");
        StringJoiner joiner = new StringJoiner("|");
        for (String color : colors) {
            joiner.add(color.toUpperCase(Locale.ROOT));
        }
        System.out.println("iterableColors=" + joiner);

        int rangeSum = 0;
        for (int value : new Range(2, 5)) {
            rangeSum += value;
        }
        System.out.println("customRangeSum=" + rangeSum);

        List<StringBuilder> labels = new ArrayList<>();
        labels.add(new StringBuilder("A"));
        labels.add(new StringBuilder("B"));
        for (StringBuilder label : labels) {
            label.append("!");
        }
        System.out.println("objectMutation=" + labels);

        int[] values = {1, -2, 0, 5, 7};
        int counted = 0;
        for (int value : values) {
            if (value < 0) {
                continue;
            }
            if (value == 5) {
                break;
            }
            counted++;
        }
        System.out.println("skipAndBreak=" + counted);

        List<String> removable = new ArrayList<>(
                List.of("A", "B", "C", "B"));
        Iterator<String> it = removable.iterator();
        while (it.hasNext()) {
            if (it.next().equals("B")) {
                it.remove();
            }
        }
        System.out.println("iteratorRemove=" + removable);

        int emptyVisits = 0;
        for (int value : new int[0]) {
            emptyVisits++;
        }
        System.out.println("emptyVisits=" + emptyVisits);

        int[] missing = null;
        try {
            for (int value : missing) {
                System.out.println(value);
            }
        } catch (NullPointerException ex) {
            System.out.println("nullArray=NullPointerException");
        }
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 EnhancedForDemo.java
java EnhancedForDemo
~~~

**Expected stdout chính xác:**

~~~text
arraySum=16
arrayReassign=[2, 4]
arrayIndexed=[20, 40]
iterableColors=RED|GREEN|BLUE
customRangeSum=9
objectMutation=[A!, B!]
skipAndBreak=2
iteratorRemove=[A, C]
emptyVisits=0
nullArray=NullPointerException
~~~

Kết quả `arrayReassign` và `arrayIndexed` làm rõ khác biệt giữa thay **biến vòng lặp** với thay **phần tử thật**. `objectMutation` cho thấy thay đổi trạng thái đối tượng được phản ánh trong list. `customRangeSum` minh họa `Iterable` tự tạo, không cần là một `List`.

## 9. Checklist và bài tập

**Bài 1.** Dùng enhanced for tính tổng và số phần tử dương trong `int[] {4, -3, 0, 7}`. Dự đoán tổng và số đếm.

**Bài 2.** Viết enhanced for duyệt `List<String>` và nối các chuỗi bằng dấu phẩy. Nếu danh sách rỗng, kết quả ra sao?

**Bài 3.** Dự đoán mảng sau `for (int value : numbers) { value++; }` rồi sửa bằng vòng lặp có chỉ số để tăng trực tiếp mọi phần tử.

**Bài 4.** Viết lớp triển khai `Iterable<Integer>` trả các số từ 1 đến 4, kết thúc đúng khi `hasNext()` là false.

**Bài 5.** Với `ArrayList<String>`, xóa các phần tử bằng `"B"` an toàn bằng `Iterator.remove()`. Vì sao không nên sửa cấu trúc list ngay trong vòng for-each?

**Bài 6.** Xử lý một mảng nullable: khi null, trả thông báo rõ ràng; khi rỗng, trả tổng 0; khi có dữ liệu, tính tổng bằng enhanced for.

**Checklist:** Nguồn là mảng hoặc `Iterable` chưa? Có phụ thuộc index/thứ tự không? Có thay cấu trúc collection khi đang duyệt không? Có phân biệt nguồn null với nguồn rỗng không? Nếu muốn cập nhật giá trị gốc, đã chọn cách gán đúng phần tử chưa?

### Ghi nhớ

**Enhanced `for (T element : source)`** là cách duyệt ngắn gọn mảng hoặc `Iterable` khi chỉ cần từng phần tử. Với mảng, duyệt từ chỉ số 0 đến cuối; với `Iterable`, thứ tự do iterator quyết định. Biến `element` không phải địa chỉ để gán lại phần tử nguồn; hãy dùng chỉ số/iterator phù hợp khi cần sửa. Kiểm tra nguồn null, tránh thay đổi cấu trúc collection ngoài iterator và chọn `for` có chỉ số khi cần index.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-and-control-flow-002">Biến vòng lặp là bản sao giá trị phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# Biến vòng lặp là bản sao giá trị phần tử — Java 17+

Trong **enhanced `for`** (còn gọi là *for-each*), biến vòng lặp nhận **giá trị của từng phần tử**, chứ không phải một ô lưu trữ có thể gán thay thế trong mảng hoặc danh sách. Vì Java truyền và gán **theo giá trị**, việc **gán lại biến vòng lặp** không đồng nghĩa với việc sửa phần tử gốc.

~~~java
int[] numbers = {1, 2, 3};

for (int number : numbers) {
    number *= 10;
}
// numbers vẫn là {1, 2, 3}.
~~~

Với **kiểu nguyên thủy**, biến nhận bản sao của số. Với **kiểu tham chiếu**, biến nhận **bản sao của tham chiếu**: nó có thể trỏ tới *cùng đối tượng*, nhưng bản thân biến không phải vị trí chứa phần tử trong collection. Phân biệt này quyết định phép thay đổi nào được phản ánh ra bên ngoài.

## 1. Enhanced `for` đọc phần tử như thế nào?

~~~java
int[] a = {10, 20};
for (int value : a) {
    System.out.println(value);
}
~~~

Về ý nghĩa liên quan đến biến phần tử, có thể hình dung gần như:

~~~java
for (int i = 0; i < a.length; i++) {
    int value = a[i]; // Nhận một bản sao của giá trị phần tử.
    System.out.println(value);
}
~~~

Với `Iterable` như `List`, enhanced `for` dùng `Iterator` để lấy từng giá trị, không phải tự nhiên có một biến đại diện trực tiếp cho ô lưu trữ. Dù giá trị lấy qua mảng hay iterator, **gán lại biến phần tử không ghi ngược trở về nguồn**.

| Loại phần tử | Biến vòng lặp giữ gì? | Gán lại biến có thay phần tử gốc? |
| --- | --- | --- |
| `int`, `double`, `boolean`... | Bản sao giá trị nguyên thủy | **Không** |
| `String`, `Integer` | Bản sao tham chiếu đối tượng bất biến | **Không** |
| Đối tượng có thể thay đổi, như `Box` | Bản sao tham chiếu đến cùng đối tượng | **Không**, nhưng có thể sửa trạng thái đối tượng |
| Mảng con `int[]` của `int[][]` | Bản sao tham chiếu mảng con | **Không**, nhưng có thể sửa phần tử trong mảng con |

Từ “bản sao” ở đây là **sao chép giá trị biến** (đối với đối tượng là địa chỉ tham chiếu theo mô hình ngữ nghĩa), **không** phải tự động tạo *deep copy* của đối tượng.

## 2. Chương trình Java 17+ độc lập, chạy bằng CLI

Tạo tệp UTF-8 tên **`EnhancedForCopyDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class EnhancedForCopyDemo {
    static final class Box {
        int value;

        Box(int value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    public static void main(String[] args) {
        System.out.println("== primitive ==");
        int[] numbers = {1, 2, 3};

        for (int value : numbers) {
            value *= 10;
        }
        System.out.println("afterForEach=" + Arrays.toString(numbers));

        for (int i = 0; i < numbers.length; i++) {
            numbers[i] *= 10;
        }
        System.out.println("afterIndexed=" + Arrays.toString(numbers));

        System.out.println("== immutable references ==");
        String[] words = {"cat", "dog"};
        for (String word : words) {
            word = word.toUpperCase(Locale.ROOT);
        }
        System.out.println("afterRebind=" + Arrays.toString(words));

        for (int i = 0; i < words.length; i++) {
            words[i] = words[i].toUpperCase(Locale.ROOT);
        }
        System.out.println("afterReplace=" + Arrays.toString(words));

        System.out.println("== mutable objects ==");
        Box[] boxes = {new Box(1), new Box(2)};
        for (Box box : boxes) {
            box.value += 5;
        }
        System.out.println("afterMutation=" + Arrays.toString(boxes));

        for (Box box : boxes) {
            box = new Box(999);
        }
        System.out.println("afterObjectRebind=" + Arrays.toString(boxes));

        for (int i = 0; i < boxes.length; i++) {
            boxes[i] = new Box(i + 10);
        }
        System.out.println("afterObjectReplace=" + Arrays.toString(boxes));

        System.out.println("== list of wrappers ==");
        List<Integer> ids = new ArrayList<>(List.of(1, 2, 3));
        for (Integer id : ids) {
            id = id + 10;
        }
        System.out.println("listRebind=" + ids);
        ids.replaceAll(n -> n + 10);
        System.out.println("listReplaceAll=" + ids);

        System.out.println("== nested arrays ==");
        int[][] grid = {{1, 2}, {3, 4}};
        for (int[] row : grid) {
            row[0] *= 10;
        }
        System.out.println("gridMutation=" + Arrays.deepToString(grid));

        for (int[] row : grid) {
            row = new int[] {0, 0};
        }
        System.out.println("gridRebind=" + Arrays.deepToString(grid));

        for (int i = 0; i < grid.length; i++) {
            grid[i] = new int[] {-1};
        }
        System.out.println("gridReplace=" + Arrays.deepToString(grid));
        System.out.println("done");
    }
}
~~~

Dùng **JDK 17 trở lên**, mở PowerShell hoặc Bash tại thư mục chứa file:

~~~bash
java -version
javac -version
javac -encoding UTF-8 EnhancedForCopyDemo.java
java EnhancedForCopyDemo
~~~

**Exact expected stdout** của riêng lệnh `java EnhancedForCopyDemo`:

~~~text
== primitive ==
afterForEach=[1, 2, 3]
afterIndexed=[10, 20, 30]
== immutable references ==
afterRebind=[cat, dog]
afterReplace=[CAT, DOG]
== mutable objects ==
afterMutation=[6, 7]
afterObjectRebind=[6, 7]
afterObjectReplace=[10, 11]
== list of wrappers ==
listRebind=[1, 2, 3]
listReplaceAll=[11, 12, 13]
== nested arrays ==
gridMutation=[[10, 2], [30, 4]]
gridRebind=[[10, 2], [30, 4]]
gridReplace=[[-1], [-1]]
done
~~~

`java -version` và `javac -version` có đầu ra tùy JDK được cài nên không thuộc phần stdout cố định. Chương trình chỉ dùng Java chuẩn và không yêu cầu thư viện ngoài.

## 3. Với kiểu nguyên thủy: chỉ sửa biến cục bộ

~~~java
int[] scores = {10, 20};
for (int score : scores) {
    score += 5;
}
// scores vẫn là {10, 20}.
~~~

Mỗi lượt, `score` nhận **một bản sao số nguyên**. Câu `score += 5` chỉ cập nhật biến cục bộ trong lượt đó. Nếu muốn tăng trực tiếp các phần tử mảng:

~~~java
for (int i = 0; i < scores.length; i++) {
    scores[i] += 5;
}
// scores thành {15, 25}.
~~~

Đây là khác biệt giữa **sửa biến vòng lặp** và **sửa ô mảng bằng chỉ số**. Enhanced `for` đặc biệt thích hợp cho tác vụ **đọc, tính tổng, in hoặc kiểm tra** từng phần tử.

## 4. Với tham chiếu: gán lại khác với thay đổi trạng thái đối tượng

Xét lớp có thể thay đổi:

~~~java
class Box {
    int value;
    Box(int value) { this.value = value; }
}
~~~

Nếu một mảng chứa `Box`:

~~~java
Box[] boxes = {new Box(1), new Box(2)};

for (Box box : boxes) {
    box.value += 5; // Sửa trạng thái đối tượng đang được tham chiếu.
}
// boxes[0].value == 6; boxes[1].value == 7.
~~~

Biến `box` là **bản sao tham chiếu**, nhưng vẫn trỏ tới **cùng một đối tượng `Box`** mà mảng đang giữ. Vì thế `box.value += 5` thay đổi đối tượng và có thể quan sát được thông qua mảng.

Ngược lại:

~~~java
for (Box box : boxes) {
    box = new Box(999); // Chỉ thay đổi biến box trong lượt này.
}
// Mảng vẫn giữ hai đối tượng Box ban đầu.
~~~

`box = ...` không thể thay `boxes[0]` hay `boxes[1]`: phép gán chỉ đổi tham chiếu nằm trong biến cục bộ `box`. Muốn **thay thế đối tượng tại vị trí mảng**, sử dụng `boxes[i] = new Box(...)` với chỉ số.

| Thao tác | Thay ô lưu trữ mảng/list? | Có thể thay trạng thái đối tượng gốc? |
| --- | --- | --- |
| `box = anotherBox;` | Không | Không, chỉ đổi biến cục bộ |
| `box.value = 7;` | Không | Có |
| `boxes[i] = anotherBox;` | Có | Không tự thay đổi đối tượng cũ |
| `list.set(i, anotherBox);` | Có | Không tự thay đổi đối tượng cũ |

**Lưu ý:** Không phải mọi tham chiếu đều trỏ tới đối tượng cho phép sửa. `String` và các wrapper như `Integer` là bất biến; biểu thức tạo “giá trị mới” thường chỉ tạo/trả về một đối tượng khác rồi phải **gán vào phần tử gốc** nếu muốn lưu lại.

## 5. `List` và mảng nhiều chiều

Enhanced `for` duyệt được **mảng** và các đối tượng **`Iterable`** như `List`:

~~~java
List<Integer> ids = new ArrayList<>(List.of(1, 2));

for (Integer id : ids) {
    id++; // Unbox, tăng rồi boxing trở lại vào biến id.
}
// ids vẫn là [1, 2].

ids.replaceAll(n -> n + 1);
// ids thành [2, 3].
~~~

Với danh sách cho phép thay phần tử, có thể dùng `List.set(index, value)` hoặc `replaceAll` tùy ý định. **Đừng xóa/thêm trực tiếp phần tử vào `ArrayList` bằng `list.add/remove` trong lúc đang dùng enhanced `for` duyệt chính danh sách đó**: iterator fail-fast có thể ném `ConcurrentModificationException`. Khi cần xóa an toàn theo điều kiện, dùng `removeIf` hoặc một `Iterator` với `remove()` đúng hợp đồng.

Mảng hai chiều cũng là **mảng chứa các tham chiếu mảng con**:

~~~java
int[][] grid = {{1, 2}, {3, 4}};

for (int[] row : grid) {
    row[0] *= 10; // Sửa phần tử trong mảng con.
}
// grid = {{10, 2}, {30, 4}}.

for (int[] row : grid) {
    row = new int[] {0, 0}; // Không thay tham chiếu trong grid.
}
// grid vẫn = {{10, 2}, {30, 4}}.
~~~

Nếu muốn thay **cả mảng con**, phải cập nhật `grid[index]`. Việc chỉnh sửa phần tử mảng con khác hoàn toàn việc gán lại biến `row`.

## 6. Những lưu ý về phạm vi biến, `final` và `null`

- Biến khai báo trong `for (Type item : items)` là biến của vòng lặp; **không dùng được ngoài phạm vi vòng**.
- Có thể viết `for (final Box box : boxes)` để **cấm gán lại biến tham chiếu `box`**. Tuy nhiên `final` **không khiến đối tượng `Box` bất biến**: `box.value++` vẫn có thể hợp lệ.
- Phần tử tham chiếu có thể là `null`. Lúc đó biến vòng lặp nhận null; gọi `item.method()` sẽ gây `NullPointerException` nếu không kiểm tra.
- Nếu chính mảng hoặc nguồn `Iterable` là null, enhanced `for` không thể duyệt và sẽ gây `NullPointerException`.
- Nếu chỉ cần **đọc từng giá trị**, không cần vị trí, enhanced `for` thường gọn hơn một vòng đếm. Nếu cần **ghi đè chính phần tử**, nên dùng chỉ số hoặc API cập nhật thích hợp.

## 7. Các lỗi thường gặp

| Hiểu nhầm | Cách đúng |
| --- | --- |
| “`for (int x : a) { x++; }` tăng từng phần tử `a`.” | Chỉ tăng **bản sao** trong biến `x` |
| “Biến vòng lặp là bí danh trực tiếp của ô mảng.” | Biến chỉ nhận **giá trị** của phần tử |
| “Tham chiếu được sao chép nghĩa là đối tượng được deep copy.” | Không; hai tham chiếu có thể trỏ cùng đối tượng |
| “`box.value++` cũng không thể thay gì bên ngoài.” | Có thể sửa trạng thái đối tượng gốc |
| “`box = new Box(...)` thay phần tử gốc.” | Không; phải ghi đè vị trí mảng hoặc list |
| “`final Box box` khiến toàn bộ Box bất biến.” | Chỉ không cho gán lại biến `box` |
| “Đổi `String s` bằng `s.toUpperCase()` sẽ cập nhật list.” | Không nếu không gán trở lại phần tử |
| “Enhanced `for` phù hợp nhất để thay mọi phần tử tại chỗ.” | Thường dùng chỉ số, `set` hoặc `replaceAll` để thay |

## 8. Bài tập tự kiểm tra

1. `int[] a = {1,2}; for (int x : a) x += 10;` — mảng có đổi không?  
   *Đáp án:* Không, vẫn `{1,2}`.
2. `String[] a = {"hi"}; for (String s : a) s = "bye";` — phần tử đầu là gì?  
   *Đáp án:* `"hi"`.
3. `Box[] a = {new Box(3)}; for (Box b : a) b.value++;` — giá trị trong `a[0]`?  
   *Đáp án:* 4, vì sửa cùng đối tượng.
4. Nếu thay `b.value++` bằng `b = new Box(9)`, phần tử gốc có bị thay không?  
   *Đáp án:* Không.
5. Với `int[][] grid`, `row[0]++` và `row = new int[1]` khác nhau thế nào?  
   *Đáp án:* Phép đầu sửa mảng con gốc; phép sau chỉ gán lại biến `row`.
6. Muốn thay toàn bộ `Integer` trong `List` theo công thức, dùng gì?  
   *Gợi ý:* `list.replaceAll(...)` hoặc vòng theo chỉ số với `list.set(i, ...)`.

## Checklist

- [ ] Hiểu enhanced `for` gán **giá trị phần tử** vào biến trong mỗi lượt.
- [ ] Phân biệt sao chép **giá trị nguyên thủy** và sao chép **tham chiếu**.
- [ ] Biết **gán lại biến vòng lặp không ghi ngược vào collection**.
- [ ] Nhận diện việc **sửa trạng thái đối tượng** vẫn có thể được phản ánh qua phần tử gốc.
- [ ] Dùng `array[i] = ...`, `list.set` hoặc `replaceAll` khi cần thay thế phần tử.
- [ ] Biết giới hạn phạm vi biến, `final` và cách xử lý phần tử `null`.

**Kết luận:** Enhanced `for` là cách diễn đạt tốt khi **đọc từng phần tử**, nhưng biến vòng lặp chỉ nhận **một bản sao giá trị**. Với đối tượng, bản sao đó là **tham chiếu tới cùng đối tượng**, nên sửa thuộc tính có thể tác động đến đối tượng gốc, còn **gán lại biến tham chiếu** không thay vị trí phần tử trong mảng hoặc danh sách.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-and-control-flow-003">Gán lại biến enhanced for không thay phần tử mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Gán lại biến enhanced `for` không thay phần tử mảng — Java 17+

## 1. Quy tắc cốt lõi

Với vòng lặp **enhanced `for`** (còn gọi là *for-each*), Java lấy lần lượt giá trị từ mảng rồi **gán giá trị đó cho biến lặp cục bộ**. Gán lại biến lặp **không ghi ngược** vào phần tử mảng đang duyệt.

~~~java
import java.util.Arrays;

public class FirstExample {
    public static void main(String[] args) {
        int[] numbers = {1, 2, 3};

        for (int number : numbers) {
            number = number * 10;
        }

        System.out.println(Arrays.toString(numbers));
    }
}
~~~

**Exact expected stdout:**

~~~text
[1, 2, 3]
~~~

Mặc dù mỗi lần lặp `number` nhận giá trị từ mảng, biểu thức `number = number * 10` **chỉ đổi biến cục bộ `number`**, không thay đổi `numbers[0]`, `numbers[1]` hoặc `numbers[2]`.

**Muốn thay chính các phần tử mảng**, dùng vị trí chỉ số để gán:

~~~java
for (int i = 0; i < numbers.length; i++) {
    numbers[i] = numbers[i] * 10;
}
System.out.println(Arrays.toString(numbers));
~~~

**Exact expected stdout (tiếp nối ví dụ trên):**

~~~text
[10, 20, 30]
~~~

Đây là điểm phân biệt quan trọng nhất của bài học: **biến trong `for (T value : array)` không phải một “ô tham chiếu” cho phép thay trực tiếp `array[index]` bằng phép gán vào `value`**.

## 2. Vì sao gán lại biến lặp không ghi ngược mảng?

Với một **mảng**, Java xử lý enhanced for gần tương đương về ý nghĩa với dạng dùng chỉ số ẩn:

~~~java
int[] data = {4, 5};

// Ý nghĩa tương đương về việc lấy giá trị từ mảng:
for (int index = 0; index < data.length; index++) {
    int element = data[index];
    element = 100; // Chỉ đổi element.
}
~~~

Ở mỗi lượt:

1. Java lấy giá trị `data[index]`.
2. Gán giá trị đó cho biến cục bộ `element`.
3. Thực thi thân vòng lặp.
4. Khi tới lượt kế, lấy một phần tử tiếp theo.

**Không có bước tự động kiểu `data[index] = element;` sau thân vòng lặp**. Phép gán vào biến và phép gán vào phần tử mảng là hai thao tác khác nhau.

Một cách dễ nhớ:

~~~text
data[0] ----giá trị----> element
  4                          4

element = 100;

data[0]                      element
  4                             100
~~~

Sơ đồ này dùng cho **giá trị nguyên thủy**. Với mảng đối tượng, giá trị được sao chép vào biến cục bộ là **giá trị tham chiếu**; cần phân biệt tiếp ở phần sau.

## 3. Kiểu nguyên thủy: tăng, giảm hay gán lại đều không sửa phần tử

~~~java
int[] values = {5, 6, 7};

for (int value : values) {
    value++;
    value *= 2;
    value = -1;
}

System.out.println(Arrays.toString(values));
~~~

**Exact expected stdout:**

~~~text
[5, 6, 7]
~~~

Các phép toán `++`, `--`, `+=`, `*=` và `=` đều chỉ thay **biến lặp**:

- `value++` không tương đương `values[i]++`.
- `value += 10` không tương đương `values[i] += 10`.
- `value = -1` không tương đương `values[i] = -1`.

**Cách đúng khi cần thay cả mảng:**

~~~java
for (int i = 0; i < values.length; i++) {
    values[i]++;
}
System.out.println(Arrays.toString(values));
~~~

**Exact expected stdout (tiếp nối):**

~~~text
[6, 7, 8]
~~~

## 4. Mảng `String`: gán lại biến cũng không thay phần tử

`String` là kiểu tham chiếu nhưng **bất biến** (*immutable*): method như `toUpperCase` trả một String, không sửa nội dung String gốc.

~~~java
import java.util.Locale;

String[] names = {"an", "binh"};
for (String name : names) {
    name = name.toUpperCase(Locale.ROOT);
}

System.out.println(Arrays.toString(names));
~~~

**Exact expected stdout:**

~~~text
[an, binh]
~~~

`name = name.toUpperCase(...)` chỉ cho biến `name` tham chiếu một String khác, còn các phần tử của `names` giữ nguyên.

**Cách đúng khi muốn thay String trong mảng:**

~~~java
for (int i = 0; i < names.length; i++) {
    names[i] = names[i].toUpperCase(Locale.ROOT);
}
System.out.println(Arrays.toString(names));
~~~

**Exact expected stdout (tiếp nối):**

~~~text
[AN, BINH]
~~~

Đừng nhầm giữa **tạo giá trị mới rồi gán cho biến lặp** và **gán giá trị mới vào ô mảng**.

## 5. Giới hạn: đối tượng, mảng con và List

**Gán lại phần tử** và **sửa đối tượng mà phần tử trỏ tới** là hai thao tác khác nhau. Với `StringBuilder[] builders`, `builder.append("!")` làm thay đổi nội dung đối tượng chung; `builder = new StringBuilder("NEW")` chỉ đổi biến local, còn `builders[i] = ...` mới thay tham chiếu ở vị trí mảng.

Với `int[][] matrix`, biến `row` trong `for (int[] row : matrix)` là bản sao tham chiếu tới mảng con. `row[0]++` sửa dữ liệu mảng con; `row = new int[]{99}` không thay `matrix[i]`. Khi cần thay cả hàng, dùng chỉ số của mảng ngoài.

Enhanced `for` trên `List<Integer>` cũng không tự gọi `set`: `number++` chỉ gán lại biến cục bộ (unboxing/boxing). Với List sửa được, dùng `set(i, value)` hoặc `replaceAll`. `List.of(...)` không hỗ trợ các thao tác cập nhật phần tử. Các cơ chế aliasing và `ListIterator.set` được bàn sâu hơn trong bài riêng về tham chiếu đối tượng.

## 6. Chương trình Java 17 độc lập và có đầu ra chính xác

Tạo đúng file **`EnhancedForAssignmentDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class EnhancedForAssignmentDemo {
    private EnhancedForAssignmentDemo() {}

    static void primitiveArray() {
        int[] scores = {1, 2, 3};

        for (int score : scores) {
            score *= 10;
        }
        System.out.println("primitive after reassignment="
                + Arrays.toString(scores));

        for (int i = 0; i < scores.length; i++) {
            scores[i] *= 10;
        }
        System.out.println("primitive after indexed update="
                + Arrays.toString(scores));
    }

    static void immutableStringArray() {
        String[] names = {"an", "binh"};

        for (String name : names) {
            name = name.toUpperCase(Locale.ROOT);
        }
        System.out.println("string after reassignment="
                + Arrays.toString(names));

        for (int i = 0; i < names.length; i++) {
            names[i] = names[i].toUpperCase(Locale.ROOT);
        }
        System.out.println("string after indexed update="
                + Arrays.toString(names));
    }

    static void mutableObjectArray() {
        StringBuilder[] builders = {
            new StringBuilder("A"),
            new StringBuilder("B")
        };

        for (StringBuilder builder : builders) {
            builder.append("!");
            builder = new StringBuilder("OTHER");
        }
        System.out.println("objects after mutation="
                + Arrays.toString(builders));

        for (int i = 0; i < builders.length; i++) {
            builders[i] = new StringBuilder("NEW" + i);
        }
        System.out.println("objects after indexed replacement="
                + Arrays.toString(builders));
    }

    static void twoDimensionalArray() {
        int[][] matrix = {
            {1, 2},
            {3, 4}
        };

        for (int[] row : matrix) {
            row[0] += 10;
            row = new int[]{99, 99};
        }
        System.out.println("matrix after row alias mutation="
                + Arrays.deepToString(matrix));

        for (int i = 0; i < matrix.length; i++) {
            matrix[i] = new int[]{20 + i};
        }
        System.out.println("matrix after row replacement="
                + Arrays.deepToString(matrix));
    }

    static void boxedList() {
        List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3));

        for (Integer number : numbers) {
            number++;
        }
        System.out.println("list after variable update=" + numbers);

        for (int i = 0; i < numbers.size(); i++) {
            numbers.set(i, numbers.get(i) + 1);
        }
        System.out.println("list after set=" + numbers);
    }

    static void finalLocalReference() {
        StringBuilder[] items = {new StringBuilder("A")};
        for (final StringBuilder item : items) {
            item.append("!");
        }
        System.out.println("final reference object="
                + Arrays.toString(items));
    }

    public static void main(String[] args) {
        primitiveArray();
        immutableStringArray();
        mutableObjectArray();
        twoDimensionalArray();
        boxedList();
        finalLocalReference();
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; mở terminal tại thư mục chứa file. Code chỉ dùng thư viện tiêu chuẩn JDK, không có tính năng preview.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 EnhancedForAssignmentDemo.java
java -cp . EnhancedForAssignmentDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\EnhancedForAssignmentDemo.java
java -cp . EnhancedForAssignmentDemo
~~~

### Exact expected stdout

~~~text
primitive after reassignment=[1, 2, 3]
primitive after indexed update=[10, 20, 30]
string after reassignment=[an, binh]
string after indexed update=[AN, BINH]
objects after mutation=[A!, B!]
objects after indexed replacement=[NEW0, NEW1]
matrix after row alias mutation=[[11, 2], [13, 4]]
matrix after row replacement=[[20], [21]]
list after variable update=[1, 2, 3]
list after set=[2, 3, 4]
final reference object=[A!]
~~~

**Đối chiếu:**

1. `score *= 10` không sửa `scores`; `scores[i] *= 10` sửa trực tiếp phần tử.
2. `name = name.toUpperCase(...)` không thay String trong mảng; `names[i] = ...` có thay.
3. `builder.append("!")` đổi nội dung object chung; `builder = new ...` không thay object được mảng tham chiếu.
4. `row[0] += 10` sửa mảng con; `row = new int[] {...}` không thay mảng con trong `matrix`.
5. `Integer number++` chỉ cập nhật biến cục bộ; `numbers.set(...)` mới thay phần tử List.
6. `final` ngăn biến tham chiếu đổi sang object khác nhưng không làm object mutable trở thành immutable.

## 7. Bài tập có đáp án

### Bài A — dự đoán mảng sau vòng lặp

~~~java
int[] a = {2, 4};
for (int x : a) {
    x = 0;
}
System.out.println(Arrays.toString(a));
~~~

**Đáp án:**

~~~text
[2, 4]
~~~

Phép gán chỉ tác động lên `x` của mỗi lượt.

### Bài B — sửa đúng cách

Yêu cầu nhân mọi phần tử `a` với 3.

~~~java
for (int i = 0; i < a.length; i++) {
    a[i] *= 3;
}
~~~

Với mảng ban đầu `{2, 4}`, kết quả `[6, 12]`.

### Bài C — object mutable

~~~java
StringBuilder[] a = {new StringBuilder("X")};
for (StringBuilder x : a) {
    x.append("Y");
    x = new StringBuilder("Z");
}
System.out.println(Arrays.toString(a));
~~~

**Đáp án:**

~~~text
[XY]
~~~

`append` đổi object đang nằm trong mảng; gán lại `x` không thay phần tử `a[0]`.

### Bài D — mảng hai chiều

~~~java
int[][] m = {{1, 2}};
for (int[] row : m) {
    row[1] = 9;
    row = new int[]{0};
}
System.out.println(Arrays.deepToString(m));
~~~

**Đáp án:**

~~~text
[[1, 9]]
~~~

`row[1] = 9` sửa phần tử của mảng con; gán lại `row` không thay phần tử mảng ngoài.

### Bài E — biến `final`

~~~java
StringBuilder[] items = {new StringBuilder("A")};
for (final StringBuilder item : items) {
    item.append("B");
    // item = new StringBuilder("C"); // Không biên dịch.
}
~~~

**Đáp án:** object đầu tiên thành `"AB"`; phép gán lại `item` bị cấm vì `final`, nhưng `append` vẫn hợp lệ.

## 8. Checklist và kết luận

- [ ] Hiểu enhanced `for` duyệt từng **giá trị phần tử**, không cung cấp ô mảng để gán ngược.
- [ ] Biết gán `value = ...`, `value++`, `value += ...` không sửa mảng primitive.
- [ ] Biết `String` immutable và gán lại biến String không thay phần tử trong mảng.
- [ ] Phân biệt **gán lại biến tham chiếu** với **thay trạng thái object mutable** được tham chiếu.
- [ ] Biết `row[0]` có thể sửa mảng con, còn `row = new int[]` không thay hàng của mảng ngoài.
- [ ] Biết gán `numbers.set(i, ...)` với List sửa được mới thay phần tử List.
- [ ] Biết `final` ngăn gán lại biến, không làm nội dung object thành bất biến.
- [ ] Chọn for theo chỉ số khi cần thay thế phần tử, enhanced for khi duyệt/đọc dữ liệu.
- [ ] Không nhầm thay đổi cấu trúc collection với gán lại biến lặp.
- [ ] Chạy `EnhancedForAssignmentDemo.java` trên Java 17+, đối chiếu exact stdout.

**Kết luận:** Enhanced `for` trao cho bạn **một biến cục bộ chứa giá trị phần tử ở lượt hiện tại**. Vì Java sao chép **giá trị** (kể cả giá trị tham chiếu), phép gán lại biến ấy **không ghi ngược vào mảng hay List**. Muốn thay phần tử, hãy cập nhật đúng vị trí bằng chỉ số hoặc API collection; nếu biến đang tham chiếu object mutable, có thể thay **trạng thái object** bằng method, nhưng đó là hành động khác với thay phần tử.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-and-control-flow-004">Thay đổi đối tượng được tham chiếu qua biến vòng lặp</a>

<details>
<summary>Xem chi tiết</summary>

# Thay đổi đối tượng được tham chiếu qua biến vòng lặp trong Java

> **Java core — language-basics / Duyệt và chuyển luồng**  
> **Môi trường:** JDK 17+, một file Java, chỉ dùng thư viện chuẩn.  
> **Mục tiêu:** Phân biệt **thay đổi trạng thái đối tượng** qua biến enhanced `for` và **gán lại biến vòng lặp**; biết cách thay phần tử thực sự của mảng/`List`, hiểu alias, kiểu bất biến và những rủi ro khi sửa collection lúc đang duyệt.

## 1. Enhanced `for` nhận giá trị phần tử vào biến cục bộ

~~~java
for (ElementType element : source) {
    // Sử dụng element.
}
~~~

Với mảng hoặc `Iterable`, mỗi lượt lấy một phần tử rồi gán giá trị đó cho biến cục bộ `element`. Cần phân biệt:

- **Kiểu nguyên thủy** như `int`: biến chứa **bản sao giá trị**. Sửa biến không sửa phần tử nguồn.
- **Kiểu tham chiếu** như `StringBuilder`, `Box`: biến chứa **bản sao giá trị tham chiếu** tới cùng đối tượng. Gọi phương thức sửa đối tượng có thể quan sát được qua mảng/list.
- **Gán lại biến** bằng `element = otherObject` chỉ thay **biến cục bộ** của lượt đó; không tự thay tham chiếu lưu ở mảng/list.

Đây là ngữ nghĩa gán và tham chiếu của Java, không phải cơ chế “truyền tham chiếu” cho biến. Enhanced `for` **không cung cấp một vị trí có thể ghi ngược vào collection**.

## 2. Sửa trạng thái đối tượng: phần tử gốc vẫn trỏ cùng đối tượng

Ví dụ với lớp `Box` có trạng thái mutable:

~~~java
static final class Box {
    private int value;

    Box(int value) { this.value = value; }

    void add(int amount) { value += amount; }

    int value() { return value; }

    @Override
    public String toString() { return "Box(" + value + ")"; }
}
~~~

Một mảng chứa các tham chiếu `Box`:

~~~java
Box[] boxes = {new Box(1), new Box(2)};

for (Box box : boxes) {
    box.add(10); // Thay đổi đối tượng mà phần tử trỏ tới.
}
// boxes chứa Box(11), Box(12).
~~~

Trong mỗi lượt, `box` và `boxes[index]` **cùng trỏ đến một đối tượng**. Do đó thay đổi thuộc tính qua `box.add(10)` được nhìn thấy khi truy cập mảng về sau. Cơ chế tương tự áp dụng cho `ArrayList<Box>` và các nguồn `Iterable<Box>` có cùng tham chiếu đối tượng.

Đối tượng được thay đổi, nhưng **vị trí phần tử trong mảng/list không bị thay thế**: không có thao tác `boxes[index] = ...` hoặc `list.set(index, ...)` trong ví dụ.

## 3. Gán lại biến vòng lặp không thay phần tử nguồn

~~~java
Box[] boxes = {new Box(1), new Box(2)};

for (Box box : boxes) {
    box = new Box(99); // Chỉ gán cho biến local box.
}
// Mảng vẫn chứa Box(1), Box(2).
~~~

Việc gán lại có thể làm `box` trỏ tới đối tượng khác **trong phần còn lại của lượt**, nhưng tham chiếu lưu ở mảng không thay đổi. Sang lượt tiếp theo, biến vòng lặp lại nhận phần tử tiếp theo từ mảng.

Thậm chí nếu kết hợp hai thao tác:

~~~java
for (Box box : boxes) {
    box.add(1);        // Sửa Box ban đầu trong mảng.
    box = new Box(99); // Đổi biến local, mảng không nhận Box(99).
}
~~~

Sau vòng này, phần tử mảng là `Box(2)` và `Box(3)`, **không phải** `Box(99)`.

Nếu khai báo `for (final Box box : boxes)`, Java cấm gán lại `box`, nhưng vẫn cho gọi `box.add(1)` nếu đối tượng cho phép sửa trạng thái: `final` bảo vệ **giá trị tham chiếu của biến**, không làm đối tượng tự động bất biến.

## 4. Muốn thay phần tử thật, dùng chỉ số hoặc `ListIterator.set`

Với **mảng**, thay tham chiếu bằng phép gán theo chỉ số:

~~~java
Box[] boxes = {new Box(1), new Box(2)};

for (int i = 0; i < boxes.length; i++) {
    boxes[i] = new Box(boxes[i].value() * 2);
}
// boxes: Box(2), Box(4).
~~~

Với `List` hỗ trợ cập nhật, dùng `list.set(index, value)` khi có chỉ số. Khi muốn duyệt và thay phần tử tại vị trí hiện hành mà không tự quản lý chỉ số, `ListIterator.set(value)` là cách phù hợp:

~~~java
java.util.List<Box> list = new java.util.ArrayList<>();
list.add(new Box(3));
list.add(new Box(4));

java.util.ListIterator<Box> it = list.listIterator();
while (it.hasNext()) {
    Box old = it.next();
    it.set(new Box(old.value() * 2));
}
// list: [Box(6), Box(8)].
~~~

| Thao tác | Thay đối tượng đang được tham chiếu? | Thay phần tử ở mảng/list? |
| --- | --- | --- |
| `box.add(5)` | Có, nếu `Box` mutable | Không thay tham chiếu phần tử |
| `box = new Box(5)` | Không sửa đối tượng cũ | Không |
| `array[i] = new Box(5)` | Không sửa đối tượng cũ | Có |
| `list.set(i, new Box(5))` | Không sửa đối tượng cũ | Có nếu list cho phép |
| `listIterator.set(new Box(5))` | Không sửa đối tượng cũ | Có nếu iterator cho phép |

`List.of(...)` không hỗ trợ thay phần tử bằng `set`; cần dùng `ArrayList` hoặc một cấu trúc cho phép sửa. Tuy vậy, một list không sửa được cấu trúc vẫn có thể **chứa đối tượng mutable**: hạn chế thay phần tử và hạn chế sửa trạng thái đối tượng là hai vấn đề khác nhau.

## 5. Alias: nhiều biến cùng tham chiếu, nhưng thay phần tử không đổi alias cũ

Giả sử hai biến cùng giữ một đối tượng:

~~~java
Box original = new Box(5);
Box alias = original;
alias.add(2);
// original.value() == 7; alias.value() == 7.
~~~

Đây là **aliasing**: thay đổi trạng thái qua một tham chiếu được quan sát qua tham chiếu còn lại.

Nhưng nếu list chứa `original` và ta **thay phần tử** bằng một đối tượng mới, `alias` vẫn trỏ tới `original`:

~~~java
java.util.List<Box> list =
        new java.util.ArrayList<>(java.util.List.of(original));

list.set(0, new Box(100));
// alias.value() == 7, list.get(0).value() == 100.
// alias == list.get(0) là false.
~~~

Do đó, cần quyết định nghiệp vụ đang muốn **điều chỉnh đối tượng chung** hay **thay một phần tử bằng đối tượng khác**. Sửa đối tượng được nhiều nơi tham chiếu có thể ảnh hưởng không chủ ý tới tất cả nơi đang giữ alias.

## 6. Đối tượng bất biến: ví dụ `String`

`String` là kiểu **bất biến** (*immutable*): không có phương thức thay nội dung của chính đối tượng. Phương thức `toUpperCase()` tạo/trả về một giá trị chuỗi thích hợp mà không sửa chuỗi nguồn.

~~~java
java.util.List<String> names = new java.util.ArrayList<>(
        java.util.List.of("an", "binh"));

for (String name : names) {
    name = name.toUpperCase(java.util.Locale.ROOT);
}
// names vẫn là [an, binh].
~~~

Ở đây gán lại biến `name` không thay phần tử list. Để chuyển đổi chính các phần tử nguồn:

~~~java
for (int i = 0; i < names.size(); i++) {
    names.set(i, names.get(i).toUpperCase(java.util.Locale.ROOT));
}
// names là [AN, BINH].
~~~

Ngược lại, `StringBuilder` mutable, nên `builder.append("!")` trong enhanced `for` thay đổi nội dung đối tượng:

~~~java
java.util.List<StringBuilder> builders = new java.util.ArrayList<>();
builders.add(new StringBuilder("A"));

for (StringBuilder builder : builders) {
    builder.append("!");
}
// builders chứa StringBuilder có nội dung "A!".
~~~

**Quy tắc:** “Kiểu tham chiếu” không đồng nghĩa “đối tượng có thể sửa”. Cần xem API của kiểu và tính bất biến của đối tượng.

## 7. Null và sửa cấu trúc collection trong lúc duyệt

- Nếu phần tử là `null`, biến enhanced `for` cũng nhận null. Gọi `box.add(1)` khi `box == null` sẽ ném `NullPointerException`; phải kiểm tra trước.
- Nếu **mảng hoặc Iterable nguồn** là null, vòng enhanced `for` không thể bắt đầu duyệt và thường ném `NullPointerException`.
- Khi duyệt `ArrayList` bằng enhanced `for`, tránh `list.add(...)` / `list.remove(...)` trực tiếp trong thân vì đó là **thay đổi cấu trúc** ngoài iterator, có thể gây `ConcurrentModificationException`. Đừng dựa vào fail-fast như một cơ chế đồng bộ chắc chắn.
- Nếu cần xóa lúc duyệt, dùng `Iterator.remove()` nếu iterator hỗ trợ; nếu cần thay phần tử hiện hành, dùng `ListIterator.set()` nếu list/iterator hỗ trợ.
- Thay **trạng thái bên trong Box** khi đang duyệt `List<Box>` không phải tự động là sửa cấu trúc `List`. Tuy nhiên, các cấu trúc dựa trên thuộc tính để sắp xếp/hash có thể mất tính nhất quán nếu đổi thuộc tính ảnh hưởng khóa sau khi chèn.

Ví dụ xử lý phần tử nullable:

~~~java
Box[] values = {null, new Box(5)};

for (Box box : values) {
    if (box != null) {
        box.add(1);
    }
}
// values chứa null và Box(6).
~~~

## 8. Chương trình Java 17+ chạy độc lập

Tạo file **LoopReferenceMutationDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

public final class LoopReferenceMutationDemo {
    static final class Box {
        private int value;

        Box(int value) {
            this.value = value;
        }

        int value() {
            return value;
        }

        void add(int amount) {
            value += amount;
        }

        @Override
        public String toString() {
            return "Box(" + value + ")";
        }
    }

    public static void main(String[] args) {
        Box[] array = {new Box(1), new Box(2)};
        for (Box box : array) {
            box.add(10);
        }
        System.out.println("arrayMutation=" + Arrays.toString(array));

        for (Box box : array) {
            box = new Box(99);
        }
        System.out.println("arrayRebind=" + Arrays.toString(array));

        for (int i = 0; i < array.length; i++) {
            array[i] = new Box(array[i].value() * 2);
        }
        System.out.println("arrayReplace=" + Arrays.toString(array));

        List<Box> list = new ArrayList<>(
                List.of(new Box(3), new Box(4)));
        for (Box box : list) {
            box.add(5);
        }
        System.out.println("listMutation=" + list);

        for (Box box : list) {
            box = new Box(0);
        }
        System.out.println("listRebind=" + list);

        ListIterator<Box> it = list.listIterator();
        while (it.hasNext()) {
            Box old = it.next();
            it.set(new Box(old.value() + 1));
        }
        System.out.println("listReplace=" + list);

        Box alias = list.get(0);
        alias.add(100);
        System.out.println("aliasMutation=" + list);

        list.set(0, new Box(1));
        System.out.println("listAfterSet=" + list);
        System.out.println("aliasOld=" + alias);
        System.out.println("aliasSame=" + (alias == list.get(0)));

        List<String> names = new ArrayList<>(List.of("an", "binh"));
        for (String name : names) {
            name = name.toUpperCase(Locale.ROOT);
        }
        System.out.println("stringsRebind=" + names);

        for (int i = 0; i < names.size(); i++) {
            names.set(i, names.get(i).toUpperCase(Locale.ROOT));
        }
        System.out.println("stringsReplace=" + names);

        List<StringBuilder> builders = new ArrayList<>();
        builders.add(new StringBuilder("A"));
        for (StringBuilder builder : builders) {
            builder.append("!");
        }
        System.out.println("builderMutation=" + builders);

        Box[] nullable = {null, new Box(5)};
        for (Box box : nullable) {
            if (box != null) {
                box.add(1);
            }
        }
        System.out.println("nullable=" + Arrays.toString(nullable));
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell, Command Prompt):

~~~bash
javac -encoding UTF-8 LoopReferenceMutationDemo.java
java LoopReferenceMutationDemo
~~~

**Expected stdout chính xác:**

~~~text
arrayMutation=[Box(11), Box(12)]
arrayRebind=[Box(11), Box(12)]
arrayReplace=[Box(22), Box(24)]
listMutation=[Box(8), Box(9)]
listRebind=[Box(8), Box(9)]
listReplace=[Box(9), Box(10)]
aliasMutation=[Box(109), Box(10)]
listAfterSet=[Box(1), Box(10)]
aliasOld=Box(109)
aliasSame=false
stringsRebind=[an, binh]
stringsReplace=[AN, BINH]
builderMutation=[A!]
nullable=[null, Box(6)]
~~~

So sánh `arrayMutation`/`arrayRebind` và `listMutation`/`listRebind` để thấy **sửa trạng thái đối tượng ≠ gán lại biến**. `listAfterSet` khác `aliasOld` chứng minh thay phần tử không làm mọi tham chiếu cũ tự động đổi sang đối tượng mới.

## 9. Lỗi thường gặp và bài tập

| Lỗi | Cách sửa |
| --- | --- |
| Cho rằng `box = new Box(...)` trong for-each thay mảng/list | Dùng `array[i] = ...` hoặc `list.set(i, ...)` |
| Cho rằng `box.add(1)` chỉ sửa bản sao đối tượng | Phân biệt bản sao **tham chiếu** với đối tượng gốc dùng chung |
| Dùng `String.toUpperCase()` rồi bỏ kết quả | Gán kết quả vào phần tử theo chỉ số nếu muốn thay nội dung list |
| Nghĩ `final Box box` làm `Box` bất biến | `final` chỉ ngăn gán lại biến tham chiếu |
| Gọi phương thức trên phần tử null | Guard `if (box != null)` |
| Xóa/thêm trực tiếp vào `ArrayList` trong enhanced for | Dùng iterator hỗ trợ xóa, hoặc chuyển cách xử lý |
| Sửa phần tử của `List.of(...)` bằng `set` | Dùng một list cho phép sửa như `ArrayList` |

**Bài 1.** Cho `Box[] array = {new Box(2), new Box(4)}`. Dự đoán kết quả nếu mỗi lượt lần lượt chạy `box.add(3)` và `box = new Box(0)`. Giải thích riêng hai phép.

**Bài 2.** Dùng vòng `for` có chỉ số để thay tất cả phần tử mảng bằng `new Box(old.value() * 2)`. So sánh với sửa đối tượng đang tồn tại bằng `add`.

**Bài 3.** Viết `ArrayList<String>` có hai chuỗi thường, chuyển thành chữ hoa bằng `set` và kiểm tra vì sao gán lại biến trong for-each không đủ.

**Bài 4.** Giữ một alias tới phần tử đầu của `List<Box>`, sau đó thay phần tử bằng `set(0, new Box(...))`. Alias cũ và phần tử mới còn là cùng đối tượng không?

**Bài 5.** Viết đoạn xử lý `List<Box>` có phần tử null; thêm một guard để tránh `NullPointerException` khi thay đổi thuộc tính.

### Ghi nhớ

**Enhanced for chỉ đưa giá trị phần tử vào biến cục bộ.** Với kiểu tham chiếu, giá trị ấy trỏ tới cùng đối tượng: **gọi phương thức mutable** có thể sửa trạng thái đối tượng chung, còn **gán lại biến vòng lặp** không thay phần tử gốc. Muốn thay phần tử, dùng chỉ số, `List.set` hoặc `ListIterator.set`; nhớ phân biệt mutable/immutable và lưu ý alias, null, cũng như các giới hạn khi sửa collection trong lúc duyệt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-and-control-flow-005">break và continue không nhãn</a>

<details>
<summary>Xem chi tiết</summary>

# `break` và `continue` không nhãn — Java 17+

Hai lệnh `break;` và `continue;` giúp đổi luồng thực thi bên trong vòng lặp mà **không cần đặt nhãn (label)**.

- **`break;`**: kết thúc **vòng lặp hoặc `switch` gần nhất** đang bao quanh lệnh.
- **`continue;`**: bỏ phần còn lại của **lượt hiện tại của vòng lặp gần nhất**, chuyển sang bước chuẩn bị lượt tiếp theo. Nó **không** chuyển sang nhánh `case` kế tiếp của `switch`.

~~~java
for (int i = 0; i < 5; i++) {
    if (i == 3) break;
    if (i % 2 == 0) continue;
    System.out.println(i);
}
// Chỉ in 1.
~~~

Với `i = 0` và `i = 2`, `continue` bỏ lệnh in; với `i = 3`, `break` kết thúc hẳn vòng lặp.

## 1. Khác nhau ở đâu?

| Tiêu chí | `break;` không nhãn | `continue;` không nhãn |
| --- | --- | --- |
| Đích tác động | Vòng lặp hoặc `switch` gần nhất | **Vòng lặp** gần nhất |
| Thân hiện tại | Dừng ngay | Bỏ các câu lệnh còn lại trong lượt hiện tại |
| Sau lệnh | Đi đến câu lệnh phía sau cấu trúc bị thoát | Chuẩn bị lượt tiếp theo của vòng lặp |
| Trong basic `for` | Không chạy phần cập nhật của vòng vừa thoát | **Có chạy phần cập nhật** rồi mới kiểm tra điều kiện |
| Trong `while` | Thoát vòng | Quay lại kiểm tra điều kiện |
| Trong `do-while` | Thoát vòng | Tới bước kiểm tra điều kiện ở cuối |
| Trong `switch` | Thoát `switch` gần nhất | Chỉ hợp lệ nếu bên ngoài có vòng lặp thích hợp; chuyển sang lượt lặp |

**Quan trọng:** `if` không phải đích của `break` hay `continue`. Khi viết `if (condition) break;`, lệnh `break` tác động đến vòng lặp/`switch` bao quanh, không chỉ kết thúc khối `if`.

## 2. Demo Java 17+ tự chứa

Tạo file UTF-8 **`UnlabeledBreakContinueDemo.java`**:

~~~java
public class UnlabeledBreakContinueDemo {
    static void breakFor() {
        for (int i = 0; i < 5; i++) {
            if (i == 3) {
                System.out.println("breakAt=" + i);
                break;
            }
            System.out.println("keep=" + i);
        }
        System.out.println("afterBreakFor");
    }

    static int step(int i) {
        System.out.println("update=" + i);
        return i + 1;
    }

    static void continueFor() {
        for (int i = 0; i < 4; i = step(i)) {
            if (i == 1) {
                System.out.println("skip=" + i);
                continue;
            }
            System.out.println("body=" + i);
        }
        System.out.println("afterContinueFor");
    }

    static void continueWhile() {
        int n = 0;
        while (n < 4) {
            n++;  // Tiến triển trước khi có thể continue.
            if (n % 2 == 0) {
                System.out.println("skipEven=" + n);
                continue;
            }
            System.out.println("odd=" + n);
        }
        System.out.println("afterWhile=" + n);
    }

    static void continueDoWhile() {
        int attempt = 0;
        do {
            attempt++;
            if (attempt == 1) {
                System.out.println("doSkip=" + attempt);
                continue;
            }
            System.out.println("doBody=" + attempt);
        } while (attempt < 2);
        System.out.println("afterDo=" + attempt);
    }

    static void nestedLoops() {
        for (int row = 1; row <= 2; row++) {
            for (int col = 1; col <= 3; col++) {
                if (col == 2) {
                    System.out.println("innerContinue=" + row + "," + col);
                    continue;
                }
                if (row == 2 && col == 3) {
                    System.out.println("innerBreak=" + row + "," + col);
                    break;
                }
                System.out.println("grid=" + row + "," + col);
            }
            System.out.println("outerEnd=" + row);
        }
    }

    static void switchInsideFor() {
        for (int value = 0; value < 4; value++) {
            switch (value) {
                case 0:
                    System.out.println("caseZero");
                    break; // Chỉ thoát switch.
                case 1:
                    System.out.println("continueLoop");
                    continue; // Chuyển sang lượt for tiếp theo.
                case 2:
                    System.out.println("caseTwo");
                    break;
                default:
                    System.out.println("caseDefault");
            }
            System.out.println("afterSwitch=" + value);
        }
    }

    public static void main(String[] args) {
        System.out.println("== break for ==");
        breakFor();

        System.out.println("== continue for ==");
        continueFor();

        System.out.println("== continue while ==");
        continueWhile();

        System.out.println("== continue do-while ==");
        continueDoWhile();

        System.out.println("== nested loops ==");
        nestedLoops();

        System.out.println("== switch inside for ==");
        switchInsideFor();
        System.out.println("done");
    }
}
~~~

Dùng **JDK 17 trở lên**, mở PowerShell hoặc Bash tại thư mục chứa file:

~~~bash
java -version
javac -version
javac -encoding UTF-8 UnlabeledBreakContinueDemo.java
java UnlabeledBreakContinueDemo
~~~

**Exact expected stdout** của riêng lệnh `java UnlabeledBreakContinueDemo`:

~~~text
== break for ==
keep=0
keep=1
keep=2
breakAt=3
afterBreakFor
== continue for ==
body=0
update=0
skip=1
update=1
body=2
update=2
body=3
update=3
afterContinueFor
== continue while ==
odd=1
skipEven=2
odd=3
skipEven=4
afterWhile=4
== continue do-while ==
doSkip=1
doBody=2
afterDo=2
== nested loops ==
grid=1,1
innerContinue=1,2
grid=1,3
outerEnd=1
grid=2,1
innerContinue=2,2
innerBreak=2,3
outerEnd=2
== switch inside for ==
caseZero
afterSwitch=0
continueLoop
caseTwo
afterSwitch=2
caseDefault
afterSwitch=3
done
~~~

`java -version` và `javac -version` cho thông tin khác nhau theo môi trường JDK và không nằm trong stdout cố định. Ví dụ không cần thư viện ngoài.

## 3. `break`: thoát hoàn toàn vòng lặp gần nhất

~~~java
int[] numbers = {2, 4, -1, 8};
for (int n : numbers) {
    if (n < 0) {
        break;
    }
    System.out.println(n);
}
// In: 2, 4. Giá trị 8 không được xử lý.
~~~

Gặp `-1` thì `break` lập tức thoát vòng; vòng không được khởi động lượt tiếp theo. Nếu cần tìm phần tử đầu tiên phù hợp, thoát sớm bằng `break` thường tiết kiệm các bước thừa.

`break` không kết thúc phương thức đang chứa nó: Java tiếp tục chạy các câu lệnh **sau vòng lặp**. Muốn kết thúc hẳn phương thức, dùng `return` thích hợp.

Trong các vòng **lồng nhau**, `break;` không nhãn chỉ thoát **vòng trong gần nhất**:

~~~java
for (int row = 0; row < 2; row++) {
    for (int col = 0; col < 3; col++) {
        if (col == 1) break;
        System.out.println(row + ":" + col);
    }
}
// In 0:0 rồi 1:0.
~~~

Vòng ngoài vẫn chạy lượt tiếp theo. Nếu nghiệp vụ muốn thoát nhiều tầng, cần thiết kế lại luồng (ví dụ dùng phương thức hỗ trợ trả `boolean` hoặc `return`); trường hợp dùng `break` có nhãn là một chủ đề riêng, không phải hành vi của `break;` không nhãn.

## 4. `continue`: bỏ phần còn lại của lượt hiện tại

~~~java
int[] values = {3, -1, 5};
for (int value : values) {
    if (value < 0) {
        continue;
    }
    System.out.println(value);
}
// In 3 rồi 5.
~~~

Lệnh `continue` **không thoát vòng hoàn toàn**: nó chỉ bỏ các câu lệnh nằm sau nó trong thân *của lượt hiện tại*. Vòng sẽ tiếp tục nếu còn lượt và điều kiện cho phép.

**Điểm dễ quên:** với basic `for`, `continue` vẫn chạy phần cập nhật:

~~~java
for (int i = 0; i < 3; i++) {
    if (i == 1) continue;
    System.out.println(i);
}
// In 0 rồi 2, kết thúc bình thường.
~~~

Trong chương trình đầy đủ, hàm `step(i)` in log, chứng minh rằng cả lượt có `continue` tại `i == 1` vẫn tạo dòng `update=1`.

Với `while`, `continue` quay thẳng về điều kiện, nên phải bảo đảm biến điều khiển **đã tiến triển**:

~~~java
int i = 0;
while (i < 3) {
    i++;
    if (i == 2) continue;
    System.out.println(i);
}
// In 1 rồi 3.
~~~

Nếu để `i++` sau `continue`, rất dễ tạo vòng lặp vô hạn. Với `do-while`, `continue` chuyển tới **bước kiểm tra điều kiện sau thân**, không nhảy lên đầu thân một cách vô điều kiện.

## 5. `switch` nằm trong vòng lặp: đích có thể khác nhau

~~~java
for (int i = 0; i < 3; i++) {
    switch (i) {
        case 0:
            System.out.println("zero");
            break;    // Thoát switch, KHÔNG thoát for.
        case 1:
            continue; // Đến lượt for kế tiếp.
        default:
            System.out.println("other");
    }
    System.out.println("after-switch");
}
~~~

Với `i == 0`, lệnh `break` đi tới câu lệnh `after-switch` **trong cùng lượt for**. Với `i == 1`, `continue` bỏ luôn lệnh `after-switch`, cập nhật `i++` rồi xét lượt kế. Với `i == 2`, nhánh `default` được chạy và sau đó in `after-switch`.

Đây là khác biệt quan trọng giữa hai từ khóa:

| Vị trí câu lệnh | `break;` | `continue;` |
| --- | --- | --- |
| Trong `for`, không có `switch` bao quanh | Thoát `for` | Lượt tiếp theo của `for` |
| Trong `switch` nằm trong `for` | Thoát `switch` | Lượt tiếp theo của `for` |
| Trong vòng trong của hai vòng lồng nhau | Thoát vòng trong | Lượt tiếp theo của vòng trong |
| Trong `switch` độc lập, không nằm trong vòng lặp | Hợp lệ, thoát `switch` | **Không hợp lệ**, không có vòng lặp để tiếp tục |

Không nhầm `break` của `switch` dạng `case:` với ngữ nghĩa `case ->`: rule dùng dấu mũi tên tự tách nhánh, còn lệnh `break;` không nhãn vẫn dùng để thoát vòng lặp hoặc switch thích hợp.

## 6. Phân biệt với `return` và tác động đến dọn dẹp tài nguyên

| Lệnh | Ý nghĩa chính |
| --- | --- |
| `break;` | Thoát vòng lặp / switch gần nhất |
| `continue;` | Bỏ phần còn lại của lượt lặp gần nhất |
| `return;` hoặc `return value;` | Rời **phương thức** đang chạy |
| `throw ...;` | Ném ngoại lệ, chuyển theo cơ chế xử lý ngoại lệ |

Dù `break` hoặc `continue` đổi điểm đến, các khối `finally` cần chạy theo quy tắc Java vẫn được thực thi khi thoát qua chúng; không dùng hai lệnh này để né đóng tài nguyên. Với stream/file, ưu tiên `try-with-resources` khi cần bảo đảm đóng.

Nếu có vòng vô hạn chủ đích, có thể dùng `while (true)` cộng điều kiện `break`. Còn nếu mục tiêu chỉ là bỏ qua một giá trị không hợp lệ trong các lượt duyệt tiếp, `continue` thường sát ý định hơn.

## 7. Các lỗi thường gặp

| Hiểu nhầm | Cách đúng |
| --- | --- |
| “`break` trong vòng lồng nhau thoát cả hai vòng.” | Không nhãn chỉ thoát cấu trúc gần nhất thích hợp |
| “`continue` kết thúc toàn bộ vòng lặp.” | Chỉ kết thúc **lượt hiện tại** |
| “`continue` trong `for` bỏ luôn `i++`.” | Phần cập nhật vẫn chạy nếu `continue` nhắm vào `for` đó |
| “`continue` trong `while` tự tăng biến đếm.” | Không; tự bảo đảm tiến triển |
| “`break` trong `switch` nằm trong vòng sẽ thoát vòng.” | Chỉ thoát `switch` gần nhất |
| “`continue` chuyển sang `case` kế.” | Nó chuyển sang lượt của **vòng lặp**, không phải nhãn `case` |
| “Chỉ cần nằm trong `if` là có thể dùng `break`.” | Phải có vòng lặp hoặc switch thích hợp bao quanh |
| “`continue` dùng được trong `switch` độc lập.” | Không; phải nằm trong vòng lặp phù hợp |

## 8. Bài tập tự kiểm tra

1. `for (int i=0; i<4; i++) { if (i==2) break; print(i); }` in gì?  
   *Đáp án:* 0, 1.
2. Thay `break` bằng `continue` ở câu trên thì in gì?  
   *Đáp án:* 0, 1, 3.
3. Hai vòng lồng nhau, `break;` trong vòng trong có làm vòng ngoài dừng luôn không?  
   *Đáp án:* Không.
4. `continue;` trong basic `for` có chạy phần cập nhật không?  
   *Đáp án:* Có.
5. `break;` trong `switch` nằm trong `while` thoát cấu trúc nào?  
   *Đáp án:* `switch` gần nhất.
6. `continue;` trong `switch` nằm trong `while` chuyển đến đâu?  
   *Đáp án:* Bước kiểm tra điều kiện của `while` gần nhất.
7. Vì sao `continue` trước lệnh cập nhật trong `while` nguy hiểm?  
   *Đáp án:* Biến điều khiển có thể không đổi và khiến vòng lặp vô hạn.

## Checklist

- [ ] `break;` thoát vòng lặp hoặc switch gần nhất, không tự kết thúc phương thức.
- [ ] `continue;` bỏ phần còn lại của lượt hiện tại của vòng gần nhất.
- [ ] Nắm khác biệt về cập nhật của basic `for`, `while` và `do-while`.
- [ ] Xác định đúng vòng **bên trong** khi có các vòng lồng nhau.
- [ ] Phân biệt `break` của `switch` và `continue` hướng tới vòng lặp bao ngoài.
- [ ] Bảo đảm điều kiện tiến triển và tài nguyên được xử lý đúng.

**Kết luận:** `break;` không nhãn là **thoát cấu trúc lặp/`switch` gần nhất**, còn `continue;` không nhãn là **bỏ phần còn lại của lượt trong vòng lặp gần nhất**. Luôn xác định chính xác cấu trúc bao quanh và bước cập nhật tiếp theo để tránh dừng sai hoặc lặp vô hạn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-and-control-flow-006">break và continue có nhãn trong vòng lặp lồng</a>

<details>
<summary>Xem chi tiết</summary>

# `break` và `continue` có nhãn trong vòng lặp lồng — Java 17+

## 1. Mục tiêu và quy tắc chính

Khi có nhiều vòng lặp lồng nhau, `break;` và `continue;` **không nhãn** mặc định tác động lên **vòng lặp gần nhất** bao quanh câu lệnh:

- `break;`: kết thúc vòng lặp gần nhất, rồi tiếp tục sau vòng đó.
- `continue;`: bỏ các câu lệnh còn lại trong **lượt hiện tại** của vòng lặp gần nhất, chuyển sang bước cập nhật/kiểm tra để đi tới lượt tiếp theo.

Java cho phép đặt **nhãn** cho một vòng lặp, rồi chỉ định đích:

~~~java
outer:
for (int i = 0; i < 3; i++) {
    for (int j = 0; j < 3; j++) {
        if (i == 1 && j == 1) {
            break outer; // Kết thúc toàn bộ vòng for mang nhãn outer.
        }
        System.out.println(i + "," + j);
    }
}
System.out.println("done");
~~~

**Exact expected stdout:**

~~~text
0,0
0,1
0,2
1,0
done
~~~

Nhãn `outer` là tên do lập trình viên đặt, **không phải** một biến, không phải `goto`, và không tạo một vòng lặp mới. Trong bài, ta sẽ phân biệt rõ `break outer;` với `continue outer;` và theo dõi trạng thái các biến đếm khi chuyển luồng.

## 2. Cú pháp nhãn và hai lệnh chuyển luồng

~~~java
myLoop:
for (int i = 0; i < 10; i++) {
    // ...
}
~~~

Dạng đầy đủ:

- `labelName:` đứng **ngay trước câu lệnh** được gắn nhãn.
- `break labelName;` chuyển điều khiển ra **sau câu lệnh có nhãn**.
- `continue labelName;` chuyển tới **lượt tiếp theo của vòng lặp có nhãn**, nếu nhãn đó chỉ một vòng lặp đang bao quanh vị trí `continue`.

Tên nhãn phải là định danh Java hợp lệ (ví dụ `outer`, `search`, `nextRow`). Nhãn có phạm vi trong câu lệnh mà nó gắn vào; không thể nhảy sang một vòng lặp nằm trong phương thức khác.

**Điểm khác biệt của Java:** `break label;` có thể thoát một **câu lệnh có nhãn** phù hợp, kể cả block, nhưng `continue label;` **bắt buộc** nhãn phải thuộc một **vòng lặp** (`for`, `while`, `do-while`). Bài này sử dụng nhãn gắn với vòng lặp.

## 3. Không nhãn: chỉ tác động vòng trong

~~~java
for (int i = 1; i <= 2; i++) {
    for (int j = 1; j <= 3; j++) {
        if (j == 2) break;
        System.out.println("pair " + i + "," + j);
    }
    System.out.println("after inner " + i);
}
~~~

**Exact expected stdout:**

~~~text
pair 1,1
after inner 1
pair 2,1
after inner 2
~~~

Khi `j == 2`, `break;` thoát **vòng `j`**. Vòng `i` vẫn chạy câu lệnh `after inner` và tiếp tục lượt `i` tiếp theo.

Thử thay `break;` bằng `continue;`:

~~~java
for (int i = 1; i <= 2; i++) {
    for (int j = 1; j <= 3; j++) {
        if (j == 2) continue;
        System.out.println("keep " + i + "," + j);
    }
}
~~~

**Exact expected stdout:**

~~~text
keep 1,1
keep 1,3
keep 2,1
keep 2,3
~~~

`continue;` chỉ bỏ lượt `j == 2`, sau đó vòng `j` vẫn thực hiện **`j++`** rồi kiểm tra `j <= 3`. Vì vậy `j == 3` vẫn được duyệt ở cả hai lượt vòng ngoài.

## 4. `break outer`: rời tất cả vòng bên trong đích

~~~java
outer:
for (int i = 0; i < 3; i++) {
    for (int j = 0; j < 4; j++) {
        if (i == 1 && j == 2) {
            System.out.println("found " + i + "," + j);
            break outer;
        }
        System.out.println("scan " + i + "," + j);
    }
}
System.out.println("after search");
~~~

**Exact expected stdout:**

~~~text
scan 0,0
scan 0,1
scan 0,2
scan 0,3
scan 1,0
scan 1,1
found 1,2
after search
~~~

- Chương trình gặp đích tại `i=1, j=2` thì thoát **vòng ngoài có nhãn `outer`**.
- Các câu lệnh và lượt còn lại của hai vòng đều bị bỏ.
- Phần **update `j++` và `i++` ở lượt bị ngắt không tự chạy**.
- Thực thi tiếp tục sau toàn bộ vòng ngoài, tại `after search`.

Nhãn không cần trùng tên biến đếm; nên chọn tên diễn đạt ý nghĩa như `search` hoặc `outer`.

## 5. `continue outer`: bỏ phần còn lại của lượt vòng ngoài

~~~java
nextRow:
for (int row = 0; row < 3; row++) {
    System.out.println("row " + row);

    for (int col = 0; col < 3; col++) {
        if (col == 1) {
            System.out.println("skip rest " + row);
            continue nextRow;
        }
        System.out.println("cell " + row + "," + col);
    }

    System.out.println("row done " + row);
}
System.out.println("after rows");
~~~

**Exact expected stdout:**

~~~text
row 0
cell 0,0
skip rest 0
row 1
cell 1,0
skip rest 1
row 2
cell 2,0
skip rest 2
after rows
~~~

`continue nextRow;` nghĩa là **bỏ phần còn lại của lượt `row` hiện tại**:

1. Không tiếp tục chạy những cột sau `col == 1`.
2. Không chạy câu lệnh `row done` ở cuối body vòng ngoài trong lượt đó.
3. **Vẫn thực hiện phần cập nhật của vòng `for (row)`: `row++`**, rồi xét điều kiện `row < 3`.
4. Nếu điều kiện còn true, tạo **vòng trong mới** cho giá trị `row` kế tiếp.

Do đó `continue outer` **không giống `break outer`**: nó không dừng toàn bộ vòng ngoài; nó chuyển sang lượt tiếp theo của vòng ngoài.

## 6. Bảng đối chiếu theo cấp độ chuyển luồng

Giả sử đang ở trong vòng `j` nằm trong vòng `i` mang nhãn `outer`:

| Câu lệnh | Đích | Cập nhật `j` hiện tại? | Cập nhật `i` hiện tại? | Tiếp tục ở đâu? |
|---|---|---|---|---|
| `break;` | Thoát vòng `j` | Không | Chưa; thân vòng `i` tiếp tục, cập nhật khi hết lượt | Sau vòng `j` |
| `continue;` | Lượt kế vòng `j` | **Có** (basic for) | Không tại thời điểm đó | Phần update/điều kiện vòng `j` |
| `break outer;` | Thoát vòng `i` | Không | **Không** | Sau vòng `i` |
| `continue outer;` | Lượt kế vòng `i` | Không | **Có** (basic for) | Phần update/điều kiện vòng `i` |

Các mô tả cập nhật trong bảng áp dụng cho **basic `for`** có update riêng. Với `while` hoặc `do-while`, `continue` chuyển theo quy tắc xét điều kiện của loại vòng đó; không có biểu thức update tự động như basic `for`.

~~~mermaid
flowchart TD
    A["Đang chạy body vòng trong j"] --> B{"Lệnh gặp phải"}
    B -- "break" --> C["Ra khỏi vòng j, tiếp tục body ngoài"]
    B -- "continue" --> D["Update và kiểm tra vòng j"]
    B -- "break outer" --> E["Ra khỏi vòng i mang nhãn outer"]
    B -- "continue outer" --> F["Update và kiểm tra vòng i"]
~~~

## 7. Truy vết tác động của `continue outer` lên phần update

Để nhìn rõ rằng update của vòng ngoài vẫn chạy:

~~~java
int updates = 0;

outer:
for (int i = 0; i < 3; i++, updates++) {
    for (int j = 0; j < 2; j++) {
        if (j == 0) continue outer;
    }
}

System.out.println("updates=" + updates);
~~~

**Exact expected stdout:**

~~~text
updates=3
~~~

Ở mỗi lượt ngoài, vòng trong gặp `j == 0` và `continue outer;`. Dù bỏ phần còn lại của body, cả `i++` và `updates++` trong update vòng ngoài **vẫn được chạy**. Sau ba lượt ngoài, `updates == 3`.

Trong một danh sách update có dấu phẩy, các biểu thức được thực thi từ trái sang phải. Khi chuyển luồng có thể xảy ra giữa chừng, đọc lại mã update để tránh hiểu nhầm biến nào được đổi.

## 8. Ba cấp vòng lặp: nhãn có thể thoát nhiều cấp

~~~java
stopAll:
for (int a = 0; a < 2; a++) {
    for (int b = 0; b < 2; b++) {
        for (int c = 0; c < 2; c++) {
            if (a == 1 && b == 0 && c == 1) {
                System.out.println("stop " + a + "," + b + "," + c);
                break stopAll;
            }
            System.out.println("triple " + a + "," + b + "," + c);
        }
    }
}
System.out.println("after all");
~~~

**Exact expected stdout:**

~~~text
triple 0,0,0
triple 0,0,1
triple 0,1,0
triple 0,1,1
triple 1,0,0
stop 1,0,1
after all
~~~

`break stopAll` rời cả ba cấp vì vòng ngoài nhất `a` là câu lệnh có nhãn. Nhưng không phải lúc nào dùng nhãn cũng là giải pháp dễ bảo trì: nếu nhiều vòng lặp và nhiều trường hợp dừng phức tạp, hãy cân nhắc **tách thành phương thức** để dùng `return` rõ ràng.

## 9. Nhãn và `switch` lồng trong vòng lặp

Khi trong vòng lặp có thêm một `switch`, `break;` không nhãn thoát **switch gần nhất**, còn `break outer;` có thể thoát vòng lặp mang nhãn ở bên ngoài. `continue outer;` có thể chuyển sang lượt vòng lặp kế tiếp từ bên trong switch:

~~~java
outer:
for (int i = 0; i < 3; i++) {
    switch (i) {
        case 0 -> {
            System.out.println("switch:continue");
            continue outer;
        }
        case 1 -> {
            System.out.println("switch:break");
            break outer;
        }
        default -> System.out.println("switch:other");
    }
    System.out.println("switch:post=" + i);
}
System.out.println("switch:done");
~~~

**Exact expected stdout:**

~~~text
switch:continue
switch:break
switch:done
~~~

Vì `i == 0` chuyển ngay sang cập nhật của vòng ngoài, `switch:post=0` không được in. Với `i == 1`, `break outer` dừng vòng ngoài; `switch:post=1` cũng không được in.

**Không nhầm:** `continue;` không nhãn **không** nhắm vào switch độc lập; nó cần một vòng lặp đang bao quanh. Ngược lại, `break;` trong switch statement thường kết thúc switch.

## 10. Điều kiện hợp lệ, phạm vi nhãn và những lỗi biên dịch

**Ví dụ đúng:** nhãn gắn trực tiếp với vòng lặp:

~~~java
retry:
while (true) {
    break retry;
}
~~~

**Ví dụ sai:** nhãn gắn với block không phải vòng lặp; `continue` không được nhắm tới nhãn đó:

~~~java
// outer: {
//     continue outer; // Lỗi biên dịch: nhãn không chỉ một vòng lặp.
// }
~~~

**Ví dụ đúng với block:** `break` có nhãn có thể rời một block có nhãn:

~~~java
scope: {
    System.out.println("before");
    break scope;
}
System.out.println("after");
~~~

**Exact expected stdout:**

~~~text
before
after
~~~

Các lỗi khác cần tránh:

- Viết `break nonexistent;` hoặc `continue nonexistent;` khi không có nhãn bao quanh đúng tên → lỗi biên dịch.
- Dùng nhãn ở vòng lặp **không bao quanh vị trí đang chuyển luồng** → không hợp lệ.
- Dùng `continue` nhắm vào một `switch` hoặc một block → không hợp lệ; đích phải là vòng lặp.
- Dùng lại một tên nhãn trong vùng lồng nhau gây trùng nhãn → lỗi biên dịch.
- Nghĩ nhãn là biến hay object có thể gán hoặc truyền qua method → sai; tên nhãn chỉ có ý nghĩa cú pháp điều khiển luồng.

`break`/`continue` có nhãn **không thể nhảy xuyên qua ranh giới một phương thức/lambda sang vòng lặp ở bên ngoài**. Nếu luồng phức tạp, truyền kết quả qua `return` thay vì cố “nhảy” giữa các phương thức.

## 11. So sánh `break outer`, `continue outer` và `return`

| Lệnh | Kết thúc cái gì? | Chương trình còn tiếp tục sau vòng ngoài? |
|---|---|---|
| `break outer;` | Câu lệnh vòng lặp mang nhãn | Có, sau vòng ngoài |
| `continue outer;` | **Lượt hiện tại** của vòng mang nhãn | Có thể sau các lượt tiếp theo khi vòng thật sự kết thúc |
| `return;`/`return value;` | Toàn bộ phương thức hiện tại | Không, caller tiếp tục sau lời gọi |
| `throw exception;` | Chuyển luồng bằng ngoại lệ | Phụ thuộc nơi bắt ngoại lệ |

Ví dụ cần tìm phần tử trong ma trận:

~~~java
static int[] find(int[][] matrix, int target) {
    for (int row = 0; row < matrix.length; row++) {
        for (int col = 0; col < matrix[row].length; col++) {
            if (matrix[row][col] == target) {
                return new int[]{row, col}; // Rời phương thức.
            }
        }
    }
    return new int[]{-1, -1};
}
~~~

Đây là tình huống `return` thường **dễ đọc hơn `break label`** nếu mục đích tìm thấy là kết thúc toàn bộ phương thức tìm kiếm. `break outer` phù hợp khi cần rời các vòng nhưng vẫn có xử lý chung sau vòng.

**Cẩn trọng với `finally`:** Nếu chuyển luồng ra khỏi khối `try` có `finally`, Java vẫn thực thi `finally` theo luật của ngôn ngữ trước khi hoàn tất chuyển luồng; đừng đặt side effect hoặc `return` bất ngờ trong `finally` làm khó suy luận.

## 12. Chương trình Java 17 hoàn chỉnh, chạy độc lập

Tạo file đúng tên **`LabeledLoopControlDemo.java`**:

~~~java
public final class LabeledLoopControlDemo {
    private LabeledLoopControlDemo() {}

    static void plainBreak() {
        for (int i = 1; i <= 2; i++) {
            for (int j = 1; j <= 3; j++) {
                if (j == 2) break;
                System.out.println("pair " + i + "," + j);
            }
            System.out.println("after inner " + i);
        }
    }

    static void plainContinue() {
        for (int i = 1; i <= 2; i++) {
            for (int j = 1; j <= 3; j++) {
                if (j == 2) continue;
                System.out.println("keep " + i + "," + j);
            }
        }
    }

    static void labeledBreak() {
        search:
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 4; j++) {
                if (i == 1 && j == 2) {
                    System.out.println("found " + i + "," + j);
                    break search;
                }
                System.out.println("scan " + i + "," + j);
            }
        }
        System.out.println("after search");
    }

    static void labeledContinue() {
        nextRow:
        for (int row = 0; row < 3; row++) {
            System.out.println("row " + row);
            for (int col = 0; col < 3; col++) {
                if (col == 1) {
                    System.out.println("skip rest " + row);
                    continue nextRow;
                }
                System.out.println("cell " + row + "," + col);
            }
            System.out.println("row done " + row);
        }
        System.out.println("after rows");
    }

    static void updateAfterContinue() {
        int updates = 0;
        outer:
        for (int i = 0; i < 3; i++, updates++) {
            for (int j = 0; j < 2; j++) {
                if (j == 0) continue outer;
            }
        }
        System.out.println("updates=" + updates);
    }

    static void threeLevels() {
        stopAll:
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                for (int c = 0; c < 2; c++) {
                    if (a == 1 && b == 0 && c == 1) {
                        System.out.println("stop " + a + "," + b + "," + c);
                        break stopAll;
                    }
                    System.out.println("triple " + a + "," + b + "," + c);
                }
            }
        }
        System.out.println("after all");
    }

    static void insideSwitch() {
        outer:
        for (int i = 0; i < 3; i++) {
            switch (i) {
                case 0 -> {
                    System.out.println("switch:continue");
                    continue outer;
                }
                case 1 -> {
                    System.out.println("switch:break");
                    break outer;
                }
                default -> System.out.println("switch:other");
            }
            System.out.println("switch:post=" + i);
        }
        System.out.println("switch:done");
    }

    public static void main(String[] args) {
        System.out.println("== break ==");
        plainBreak();
        System.out.println("== continue ==");
        plainContinue();
        System.out.println("== break label ==");
        labeledBreak();
        System.out.println("== continue label ==");
        labeledContinue();
        System.out.println("== update ==");
        updateAfterContinue();
        System.out.println("== 3 levels ==");
        threeLevels();
        System.out.println("== switch ==");
        insideSwitch();
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; mở terminal tại thư mục chứa file. Không dùng thư viện ngoài JDK, Maven/Gradle hoặc tính năng preview.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 LabeledLoopControlDemo.java
java -cp . LabeledLoopControlDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\LabeledLoopControlDemo.java
java -cp . LabeledLoopControlDemo
~~~

### Exact expected stdout

~~~text
== break ==
pair 1,1
after inner 1
pair 2,1
after inner 2
== continue ==
keep 1,1
keep 1,3
keep 2,1
keep 2,3
== break label ==
scan 0,0
scan 0,1
scan 0,2
scan 0,3
scan 1,0
scan 1,1
found 1,2
after search
== continue label ==
row 0
cell 0,0
skip rest 0
row 1
cell 1,0
skip rest 1
row 2
cell 2,0
skip rest 2
after rows
== update ==
updates=3
== 3 levels ==
triple 0,0,0
triple 0,0,1
triple 0,1,0
triple 0,1,1
triple 1,0,0
stop 1,0,1
after all
== switch ==
switch:continue
switch:break
switch:done
~~~

**Đối chiếu:** `plainBreak` vẫn cho vòng ngoài chạy tiếp; `plainContinue` bỏ một `j`; `labeledBreak` dừng tìm kiếm sau `1,2`; `labeledContinue` bỏ phần còn lại của từng hàng; `updates=3` xác nhận update vòng ngoài vẫn chạy; `threeLevels` thoát đủ ba tầng; nhãn trong `insideSwitch` nhắm vào vòng ngoài.

## 13. Bài tập có đáp án

### A — `break` không nhãn dừng ở đâu?

~~~java
for (int i = 0; i < 2; i++) {
    for (int j = 0; j < 3; j++) {
        if (j == 1) break;
        System.out.println(i + ":" + j);
    }
}
~~~

**Đáp án:**

~~~text
0:0
1:0
~~~

Vòng `j` dừng tại `j==1` nhưng vòng `i` vẫn có lượt thứ hai.

### B — muốn thoát cả hai vòng

~~~java
outer:
for (int i = 0; i < 2; i++) {
    for (int j = 0; j < 3; j++) {
        if (i == 0 && j == 1) break outer;
        System.out.println(i + "," + j);
    }
}
~~~

**Expected stdout:**

~~~text
0,0
~~~

Dừng toàn bộ vòng `outer` khi tới cặp `0,1`.

### C — muốn bỏ phần còn lại của một hàng

~~~java
nextRow:
for (int i = 0; i < 2; i++) {
    for (int j = 0; j < 3; j++) {
        if (j == 1) continue nextRow;
        System.out.println(i + "," + j);
    }
}
~~~

**Expected stdout:**

~~~text
0,0
1,0
~~~

`continue nextRow` bỏ các cột còn lại của hàng hiện tại, sau đó đi sang hàng sau.

### D — nhãn block có được dùng với `continue`?

~~~java
// block: {
//     continue block;
// }
~~~

**Đáp án:** không; `continue` có nhãn chỉ nhắm tới một vòng lặp. Với block có nhãn, có thể dùng `break block;` để rời block phù hợp.

### E — sau `continue outer`, `i++` có chạy không?

Với `outer: for (int i=0; i<3; i++)`, gọi `continue outer;` từ vòng trong khi `i=1`.

**Đáp án:** có. Lệnh chuyển tới **bước update của vòng for có nhãn**, `i++` đổi 1 thành 2, rồi điều kiện `i<3` được kiểm tra.

## 14. Lỗi thường gặp và kết luận

| Sai lầm | Hậu quả | Sửa |
|---|---|---|
| Dùng `break;` trong vòng trong để thoát cả hai | Vòng ngoài vẫn chạy | `break outer;` |
| Dùng `continue;` trong vòng trong để bỏ cả hàng | Chỉ bỏ một lượt vòng trong | `continue outer;` |
| Nhầm `continue outer` với kết thúc vòng ngoài | Vòng ngoài tiếp tục ở lượt kế | Dùng `break outer` nếu cần dừng hẳn |
| Nghĩ `continue outer` bỏ update của for ngoài | Hiểu sai biến đếm | Basic `for` vẫn chạy update ngoài |
| `continue someBlock;` | Không compile | Nhãn phải gắn với vòng lặp |
| Nhãn không nằm trên cấu trúc bao quanh câu lệnh | Không compile | Đặt nhãn cho vòng ngoài đích |
| Lạm dụng nhiều nhãn, nhiều cấp nhảy | Khó bảo trì | Tách thành method và dùng return khi rõ nghĩa |
| Nhầm `break;` trong switch với `break outer;` | Thoát sai cấp | Xác định switch/vòng có nhãn cần rời |

**Checklist:**

- [ ] Biết `break;` và `continue;` không nhãn mặc định tác động vòng lặp gần nhất.
- [ ] Biết cú pháp `label: for (...) { ... }`.
- [ ] Biết `break label;` thoát hoàn toàn vòng có nhãn.
- [ ] Biết `continue label;` bỏ phần còn lại của lượt hiện tại của vòng có nhãn.
- [ ] Biết `continue outer;` trong basic `for` vẫn chạy update của vòng ngoài.
- [ ] Hiểu `break outer;` không chạy update sau lượt bị ngắt.
- [ ] Phân biệt nhãn block và nhãn vòng lặp; `continue` chỉ chấp nhận nhãn vòng lặp.
- [ ] Nhận diện trường hợp switch lồng vòng lặp và chọn đúng nhãn.
- [ ] Kiểm thử biến đếm, nhánh dừng và code chạy sau vòng lặp.
- [ ] Chạy `LabeledLoopControlDemo.java` bằng JDK 17+ và đối chiếu exact stdout.

**Kết luận:** Trong vòng lặp lồng, **`break outer` rời hẳn vòng ngoài**, còn **`continue outer` chỉ bỏ lượt ngoài đang chạy rồi chuyển tới bước cập nhật/kiểm tra của vòng đó**. Không có nhãn thì `break`/`continue` thông thường chỉ tác động vòng gần nhất. Dùng nhãn khi thật sự cần nhảy giữa các cấp lặp; nếu thuật toán nhiều điểm thoát quá phức tạp, hãy tách phương thức để dễ hiểu và kiểm thử.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-and-control-flow-007">return kết thúc phương thức đang chạy</a>

<details>
<summary>Xem chi tiết</summary>

# `return` kết thúc phương thức đang chạy trong Java

> **Java core — language-basics / Duyệt và chuyển luồng**  
> **Môi trường:** JDK 17+, chương trình một file, không dùng thư viện ngoài.  
> **Mục tiêu:** Hiểu `return` kết thúc **lần gọi phương thức hiện tại**, phân biệt `return` với `break`/`continue`, sử dụng guard clause, trả kết quả đúng kiểu và nhận biết hành vi của `finally` hoặc callback.

## 1. `return` làm gì?

`return` dừng phần thực thi còn lại của **phương thức đang chạy** và chuyển điều khiển về chỗ gọi. Có hai dạng cơ bản:

~~~java
static void logPositive(int x) {
    if (x <= 0) {
        return; // Không có giá trị vì đây là phương thức void.
    }
    System.out.println("positive=" + x);
}

static int absolute(int x) {
    if (x >= 0) {
        return x; // Trả một giá trị int.
    }
    return -x; // Chỉ minh họa khi x không phải Integer.MIN_VALUE.
}
~~~

- `return;` dùng trong phương thức `void` (hoặc constructor để kết thúc sớm), **không trả giá trị**.
- `return expression;` dùng cho phương thức có kiểu trả về tương thích với kiểu của biểu thức.
- Khi đi tới `return`, các câu lệnh bình thường sau nó trong cùng đường thực thi sẽ **không chạy**.
- Sau khi callee `return`, **caller tiếp tục** tại câu lệnh sau lời gọi; `return` không có nghĩa thoát toàn bộ ứng dụng.

Lưu ý: `-Integer.MIN_VALUE` bị tràn trong kiểu `int`, vì vậy ví dụ `absolute` không phải hàm trị tuyệt đối đúng cho mọi giá trị `int`. Khi cần xử lý toàn miền, cân nhắc `Math.absExact` (Java 15+) hoặc nâng kiểu theo yêu cầu.

## 2. `return` khác `break` và `continue`

| Câu lệnh | Đích tác động | Sau đó làm gì? |
| --- | --- | --- |
| `return;` hoặc `return value;` | Phương thức/lambda hiện tại | Rời đơn vị gọi hiện tại, trả quyền điều khiển về nơi gọi |
| `break;` | Vòng lặp hoặc `switch` statement gần nhất thích hợp | Tiếp tục sau vòng lặp/switch |
| `continue;` | Vòng lặp gần nhất | Chuyển sang bước tiếp theo theo cấu trúc vòng lặp |
| `throw exception;` | Luồng xử lý ngoại lệ | Tìm handler; nếu không có thì truyền ngoại lệ ra ngoài |

Ví dụ tìm **phần tử dương đầu tiên** trong một mảng:

~~~java
static int firstPositive(int[] values) {
    for (int value : values) {
        if (value > 0) {
            return value; // Kết thúc cả phương thức, không chỉ vòng lặp.
        }
    }
    return -1; // Chỉ chạy khi không tìm thấy phần tử dương.
}
~~~

Với `{-2, 0, 7, 9}` kết quả là 7; phần tử 9 không cần xem nữa. Với mảng rỗng hoặc không có số dương, kết quả là -1. Vì một số dương không thể bằng -1 nên sentinel này không bị nhầm với kết quả thành công; nhưng trong các bài toán khác, dùng `OptionalInt` hoặc trạng thái riêng có thể rõ ràng hơn.

## 3. `return` bên trong vòng lặp lồng nhau

`return` không chỉ dừng vòng lặp trong cùng khối; nó thoát **toàn bộ phương thức** đang thực thi:

~~~java
static String locate(int[][] matrix, int target) {
    for (int row = 0; row < matrix.length; row++) {
        for (int col = 0; col < matrix[row].length; col++) {
            if (matrix[row][col] == target) {
                return "row=" + row + ",col=" + col;
            }
        }
    }
    return "NOT_FOUND";
}
~~~

Với `{{1, 2}, {3, 4}}` và target 3, phương thức trả `"row=1,col=0"` ngay; vòng trong **và** vòng ngoài đều không lặp tiếp.

Nếu thay `return` trong ví dụ bằng `break` không nhãn thì Java chỉ thoát vòng `for` **trong cùng**, rồi vòng ngoài vẫn có thể chạy. Nếu chỉ muốn dừng vòng trong hoặc điều khiển luồng ở phạm vi hẹp hơn phương thức, `break` hoặc `continue` thường đúng mục đích hơn.

## 4. Guard clause: thoát sớm thay vì lồng nhiều `if`

*Guard clause* kiểm tra trường hợp không đáp ứng tiền đề và `return` sớm:

~~~java
static String normalize(String name) {
    if (name == null || name.isBlank()) {
        return "MISSING";
    }
    return name.trim().toUpperCase(java.util.Locale.ROOT);
}
~~~

Mã này tách xử lý dữ liệu không hợp lệ khỏi đường xử lý chính. `||` có cơ chế ngắn mạch: khi `name == null`, Java không gọi `name.isBlank()`, tránh `NullPointerException`.

Có thể viết nhiều `return` trong một phương thức khi mỗi đường đi rõ ràng. Với hàm có kiểu trả về không phải `void`, **mọi đường kết thúc bình thường** phải tạo một giá trị tương thích. Ví dụ:

~~~java
// KHÔNG BIÊN DỊCH: thiếu return khi x <= 0.
// static String broken(int x) {
//     if (x > 0) return "POSITIVE";
// }
~~~

Nếu tất cả đường đi đã trả kết quả, phần thân còn lại không được chứa câu lệnh chắc chắn không thể thực thi:

~~~java
// KHÔNG BIÊN DỊCH: unreachable statement.
// static int wrong() {
//     return 1;
//     // System.out.println("never");
// }
~~~

Trong Java, `return` không được dùng trong biểu thức như toán tử số học; nó là một **câu lệnh chuyển luồng**.

## 5. Caller tiếp tục sau khi callee trả về

~~~java
static int compute(int value) {
    System.out.println("computeEnter=" + value);
    if (value < 0) {
        return -1;
    }
    System.out.println("computeMultiply");
    return value * 2;
}

static void runCall() {
    System.out.println("callerBefore");
    int result = compute(-4);
    System.out.println("callerAfter=" + result);
}
~~~

Khi gọi `runCall()`, thứ tự sẽ là `callerBefore`, `computeEnter=-4` và `callerAfter=-1`. Dòng `computeMultiply` bị bỏ qua vì callee trả về sớm, nhưng caller **không bị kết thúc**.

Tương tự, `return;` trong `main` chỉ kết thúc phương thức `main`; nó không phải lời gọi `System.exit(...)`. Một chương trình có thể còn công việc trên các luồng khác sau khi `main` kết thúc.

## 6. `finally` và biểu thức trả về

Khi `return` nằm trong `try` kèm `finally`, thông thường Java phải thực thi `finally` **trước khi hoàn tất việc trở về caller**:

~~~java
static int withFinally() {
    try {
        System.out.println("tryBlock");
        return 10;
    } finally {
        System.out.println("finallyBlock");
    }
}
~~~

Lời gọi `System.out.println("finallyValue=" + withFinally());` in lần lượt:

~~~text
tryBlock
finallyBlock
finallyValue=10
~~~

Biểu thức `return 10` được đánh giá trước khi `finally` chạy. `finally` có thể ghi log, giải phóng tài nguyên hoặc làm sạch trạng thái. Nếu `finally` tự `return` hay ném ngoại lệ thì nó **có thể thay thế kết quả/ngoại lệ trước đó**; tránh viết `return` trong `finally` vì dễ che giấu lỗi.

Với tài nguyên như file/stream, **try-with-resources** thường tốt hơn giải phóng thủ công. `finally` không bảo đảm chạy trong mọi hoàn cảnh ngoài mô hình thực thi bình thường (ví dụ khi JVM bị kết thúc đột ngột).

## 7. `return` bên trong lambda không kết thúc caller bên ngoài

Trong Java 17, một lambda có thân dạng khối có thể dùng `return;` để kết thúc **lần gọi lambda** hiện tại. Nó không tự kết thúc phương thức bao ngoài:

~~~java
static void visit() {
    java.util.List.of(1, 2, 3).forEach(number -> {
        if (number == 2) {
            return; // Chỉ thoát callback cho số 2.
        }
        System.out.println("lambda=" + number);
    });
    System.out.println("lambdaAfter");
}
~~~

Ví dụ in `lambda=1`, `lambda=3`, `lambdaAfter`. Mỗi phần tử có một lời gọi callback riêng, nên `return` ở giá trị 2 **không chấm dứt `forEach`**. Muốn dừng sớm toàn bộ việc duyệt theo kiểu thông thường, vòng `for`/enhanced `for` và `break`/`return` ở cấp phương thức thường rõ ràng hơn.

**Phân biệt quan trọng:** `return` từ một phương thức được gọi trong thân loop quay trở lại **caller** của chính phương thức đó; muốn thoát phương thức chứa loop, phải viết `return` trong phương thức chứa loop.

## 8. Ví dụ Java 17+ chạy độc lập

Tạo file **ReturnFlowDemo.java**:

~~~java
import java.util.List;
import java.util.Locale;

public final class ReturnFlowDemo {
    private static String sign(int value) {
        if (value < 0) {
            return "NEGATIVE";
        }
        if (value == 0) {
            return "ZERO";
        }
        return "POSITIVE";
    }

    private static int firstPositive(int[] values) {
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return -1;
    }

    private static String locate(int[][] matrix, int target) {
        for (int row = 0; row < matrix.length; row++) {
            for (int col = 0; col < matrix[row].length; col++) {
                if (matrix[row][col] == target) {
                    return "row=" + row + ",col=" + col;
                }
            }
        }
        return "NOT_FOUND";
    }

    private static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return "MISSING";
        }
        return name.trim().toUpperCase(Locale.ROOT);
    }

    private static void audit(int value) {
        if (value <= 0) {
            System.out.println("auditSkipped=" + value);
            return;
        }
        System.out.println("auditValue=" + value);
    }

    private static int compute(int value) {
        System.out.println("computeEnter=" + value);
        if (value < 0) {
            return -1;
        }
        System.out.println("computeMultiply");
        return value * 2;
    }

    private static int withFinally() {
        try {
            System.out.println("tryBlock");
            return 10;
        } finally {
            System.out.println("finallyBlock");
        }
    }

    private static int countDown(int remaining) {
        if (remaining <= 0) {
            return 0; // Base case của đệ quy.
        }
        return 1 + countDown(remaining - 1);
    }

    private static void visit() {
        List.of(1, 2, 3).forEach(number -> {
            if (number == 2) {
                return; // Chỉ kết thúc một lần gọi lambda.
            }
            System.out.println("lambda=" + number);
        });
        System.out.println("lambdaAfter");
    }

    public static void main(String[] args) {
        System.out.println("signMinus=" + sign(-3));
        System.out.println("signZero=" + sign(0));
        System.out.println("signPlus=" + sign(4));

        System.out.println("firstPositive="
                + firstPositive(new int[] {-2, 0, 7, 9}));
        System.out.println("firstAbsent="
                + firstPositive(new int[] {-5, 0}));
        System.out.println("firstEmpty="
                + firstPositive(new int[] {}));

        int[][] grid = {{1, 2}, {3, 4}};
        System.out.println("locate3=" + locate(grid, 3));
        System.out.println("locate9=" + locate(grid, 9));

        System.out.println("normalizeNull=" + normalize(null));
        System.out.println("normalizeBlank=" + normalize("  "));
        System.out.println("normalizeValue=" + normalize(" java "));

        audit(-2);
        audit(5);

        System.out.println("callerBefore");
        int failed = compute(-4);
        System.out.println("callerAfter=" + failed);

        int success = compute(3);
        System.out.println("callerValue=" + success);

        System.out.println("finallyValue=" + withFinally());
        System.out.println("recursiveSteps=" + countDown(3));

        visit();
        System.out.println("mainDone");
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ReturnFlowDemo.java
java ReturnFlowDemo
~~~

**Expected stdout chính xác:**

~~~text
signMinus=NEGATIVE
signZero=ZERO
signPlus=POSITIVE
firstPositive=7
firstAbsent=-1
firstEmpty=-1
locate3=row=1,col=0
locate9=NOT_FOUND
normalizeNull=MISSING
normalizeBlank=MISSING
normalizeValue=JAVA
auditSkipped=-2
auditValue=5
callerBefore
computeEnter=-4
callerAfter=-1
computeEnter=3
computeMultiply
callerValue=6
tryBlock
finallyBlock
finallyValue=10
recursiveSteps=3
lambda=1
lambda=3
lambdaAfter
mainDone
~~~

Các dòng `callerBefore` đến `callerAfter` cho thấy callee trả về nhưng caller vẫn chạy. `tryBlock` xuất hiện trước `finallyBlock` và giá trị trả về được in sau cùng. `lambdaAfter` chứng minh `return` trong callback không chặn phương thức bao ngoài.

## 9. Checklist và bài tập

| Tình huống | Kiểm tra |
| --- | --- |
| Phương thức `void` cần dừng sớm | Dùng `return;` không kèm giá trị |
| Phương thức trả `T` | Dùng `return expression;` có kiểu tương thích, đủ đường đi |
| Muốn rời toàn bộ phương thức giữa vòng lặp | Dùng `return` trong chính phương thức đó |
| Chỉ muốn rời vòng lặp | Dùng `break` thích hợp |
| Bỏ qua lượt hiện tại | Dùng `continue` |
| `return` trong `try` có `finally` | Hiểu thứ tự cleanup và tránh return trong finally |
| `return` trong callback `forEach` | Chỉ kết thúc callback, không phải phương thức bao ngoài |

**Bài 1.** Viết hàm `String category(int number)` trả `"NEG"`, `"ZERO"` hoặc `"POS"` bằng guard clauses và đủ ba đường kết thúc.

**Bài 2.** Tìm giá trị chẵn đầu tiên trong mảng bằng enhanced `for` và `return`; chọn cách biểu diễn trường hợp không tìm thấy sao cho không nhầm với kết quả hợp lệ.

**Bài 3.** Với mảng hai chiều, so sánh `break` vòng trong và `return` khi đã tìm thấy tọa độ cần tìm.

**Bài 4.** Viết một phương thức `void` bỏ qua dữ liệu null bằng `return;` và in dữ liệu khác null; dự đoán đường nào được thực thi.

**Bài 5.** Thêm một câu lệnh in vào `finally` rồi quan sát thứ tự so với giá trị `return`. Vì sao không nên `return` từ `finally`?

**Bài 6.** Thay vòng `for-each` bằng `List.forEach` lambda chứa `return;`; dự đoán liệu các phần tử phía sau có tiếp tục được duyệt không.

### Ghi nhớ

**`return` kết thúc lần gọi phương thức (hoặc lambda) đang chạy**, không tự động tắt toàn chương trình. Trong vòng lặp lồng nhau, `return` rời cả phương thức; `break` chỉ rời cấu trúc điều khiển phù hợp. Dùng `return;` cho `void` và `return value;` cho phương thức có kết quả. Guard clause làm luồng xử lý rõ ràng; chú ý `finally` và phạm vi lambda khi suy luận thứ tự thực thi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-and-control-flow-008">Phân biệt break, continue, return và yield</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `break`, `continue`, `return` và `yield` — Java 17+

Bốn từ khóa này đều **thay đổi luồng thực thi**, nhưng **khác nhau về nơi chúng chuyển điều khiển tới**. Không thể thay chúng cho nhau chỉ vì tất cả đều có thể kết thúc một đoạn lệnh.

| Lệnh | Tác động chính | Nơi dùng điển hình | Sau lệnh, chương trình đi đâu? |
| --- | --- | --- | --- |
| `break;` | Thoát **vòng lặp hoặc `switch` gần nhất** | `for`, `while`, `do-while`, switch statement | Lệnh sau cấu trúc vừa thoát |
| `continue;` | Bỏ phần còn lại của **lượt lặp hiện tại** | Bên trong vòng lặp | Bước chuẩn bị lượt kế tiếp của vòng lặp gần nhất |
| `return value;` / `return;` | Kết thúc **phương thức** hiện tại | Trong phương thức, nếu loại `return` phù hợp với kiểu trả về | Trở về nơi gọi, không chạy tiếp phần còn lại của phương thức |
| `yield value;` | Tạo giá trị rồi kết thúc **switch expression** đang bao quanh | Nhánh hoặc khối bên trong switch expression | Tiếp tục ở biểu thức/câu lệnh đang sử dụng kết quả `switch` |

**Quy tắc nhớ nhanh:** `break` thoát cấu trúc, `continue` sang lượt lặp kế tiếp, `return` thoát phương thức, còn `yield` trả giá trị **cho một biểu thức `switch`**, không tự thoát phương thức.

## 1. `break`: dừng vòng lặp hoặc `switch` gần nhất

~~~java
for (int i = 0; i < 5; i++) {
    if (i == 3) {
        break;
    }
    System.out.println(i);
}
System.out.println("after-loop");
~~~

In `0`, `1`, `2` rồi `after-loop`. Khi `i == 3`, `break` bỏ phần còn lại của thân, **không** thực hiện phần cập nhật `i++` thêm một lần và thoát hẳn vòng.

Trong một `switch` nằm bên trong vòng lặp, `break;` thoát **`switch`**, không mặc nhiên thoát vòng lặp ngoài:

~~~java
for (int i = 0; i < 2; i++) {
    switch (i) {
        case 0:
            System.out.println("zero");
            break; // Chỉ ra khỏi switch.
        default:
            System.out.println("other");
    }
    System.out.println("after-switch=" + i);
}
~~~

Dù có `break` ở `case 0`, dòng `after-switch=0` vẫn được in và vòng `for` tiếp tục. Với các vòng lặp lồng nhau, `break;` không nhãn chỉ thoát **vòng gần nhất**. Cách thoát nhiều tầng bằng nhãn hoặc tách phương thức là vấn đề riêng, không phải hành vi mặc định.

## 2. `continue`: bỏ phần còn lại của lượt hiện tại

~~~java
for (int i = 0; i < 4; i++) {
    if (i % 2 == 0) {
        continue;
    }
    System.out.println(i);
}
// In 1 rồi 3.
~~~

`continue` không kết thúc toàn bộ vòng. Trong basic `for`, nó chuyển tới **phần cập nhật** rồi mới kiểm tra điều kiện:

~~~java
for (int i = 0; i < 3; i++) {
    if (i == 1) {
        continue; // Vẫn chạy i++ trước khi lặp tiếp.
    }
    System.out.println("i=" + i);
}
~~~

Với `while`, `continue` chuyển tới **kiểm tra điều kiện**; với `do-while`, nó chuyển tới **lần kiểm tra ở cuối thân**. Nếu cập nhật biến đếm nằm sau `continue` trong `while`, vòng có thể **không tiến triển** và lặp vô hạn.

`continue;` không chuyển tới nhãn `case` tiếp theo của `switch`. Nếu một `switch` nằm trong vòng lặp, `continue;` bên trong `switch` sẽ tiếp tục **vòng lặp bao quanh**. Nếu `switch` không nằm trong vòng lặp, `continue;` là không hợp lệ.

## 3. `return`: rời cả phương thức

~~~java
static String find(int wanted) {
    for (int i = 0; i < 3; i++) {
        if (i == wanted) {
            return "found:" + i;
        }
    }
    return "missing";
}
~~~

`return` kết thúc **toàn bộ lời gọi phương thức**, kể cả khi đang nằm trong vòng `for` hoặc `switch`. Nếu tìm thấy phần tử, Java không chạy các lượt lặp tiếp theo hoặc câu lệnh bình thường nằm sau `return`.

- Phương thức trả giá trị, như `String find(...)`, dùng `return expression;` có kiểu tương thích.
- Phương thức `void` có thể dùng `return;` để rời phương thức sớm.
- `return;` không được dùng để trả về từ một phương thức đòi giá trị; ngược lại, `return 5;` không hợp lệ trong phương thức `void`.
- `return` của một phương thức không kết thúc cả chương trình: **nơi gọi** nhận kết quả rồi thực thi tiếp.

## 4. `yield`: đưa giá trị ra khỏi `switch expression`

Từ Java 14, `switch` có thể là **biểu thức trả về một giá trị**. Khi nhánh dạng khối cần thực hiện nhiều lệnh, dùng `yield`:

~~~java
static String classify(int n) {
    String label = switch (n) {
        case 0 -> "zero";
        case 1 -> {
            System.out.println("preparing");
            yield "one";
        }
        default -> "other";
    };
    System.out.println("after-expression");
    return label;
}
~~~

Với `n == 1`, Java in `preparing` rồi **`after-expression`**, và phương thức mới kết thúc tại `return label`. Như vậy `yield "one";` kết thúc nhánh switch expression, **không** kết thúc `classify`.

### Dạng dấu hai chấm cũng dùng `yield` được

~~~java
int score = switch (level) {
    case 1:
        yield 10;
    case 2:
        yield 20;
    default:
        yield -1;
};
~~~

Đây là **switch expression** có các nhãn `case:`, không phải switch statement truyền thống. `yield` cung cấp kết quả cho toàn bộ biểu thức. Không viết `break 20;` để tạo kết quả: Java 17 dùng `yield 20;`.

### `switch statement` khác `switch expression`

~~~java
// SWITCH STATEMENT: thực hiện hành động, không tạo giá trị.
switch (status) {
    case "ON":
        System.out.println("enabled");
        break;
    default:
        System.out.println("unknown");
}

// SWITCH EXPRESSION: tạo giá trị.
String result = switch (status) {
    case "ON" -> "enabled";
    default -> "unknown";
};
~~~

- `yield` thuộc **switch expression**, không dùng như một lệnh thay `break` trong switch statement.
- `return` không được phép dùng **bên trong switch expression** để thay việc cấp giá trị cho nhánh; dùng biểu thức sau `->` hoặc `yield`.
- `case ->` không tự động đồng nghĩa với switch expression: Java 17 cũng cho phép **switch statement** có nhãn mũi tên.
- Không nhất thiết mọi nhánh đều dùng `yield`: nhánh `case X -> expression` tự cung cấp kết quả.
- Switch expression cần bao phủ đầy đủ (*exhaustive*) các giá trị chọn theo quy tắc của Java; thường dùng `default` khi cần.

## 5. Chương trình Java 17+ hoàn chỉnh

Tạo file UTF-8 **`FlowTransferDemo.java`**:

~~~java
public class FlowTransferDemo {
    static void breakLoop() {
        for (int i = 0; i < 5; i++) {
            if (i == 3) {
                System.out.println("break=" + i);
                break;
            }
            System.out.println("seen=" + i);
        }
        System.out.println("afterBreak");
    }

    static void continueLoop() {
        for (int i = 0; i < 4; i++) {
            if (i % 2 == 0) {
                System.out.println("skip=" + i);
                continue;
            }
            System.out.println("keep=" + i);
        }
        System.out.println("afterContinue");
    }

    static String find(int target) {
        for (int i = 0; i < 4; i++) {
            if (i == target) {
                return "found:" + i;
            }
        }
        return "missing";
    }

    static String expression(int n) {
        String result = switch (n) {
            case 0 -> "zero";
            case 1 -> {
                System.out.println("insideYield=" + n);
                yield "one";
            }
            default -> "other";
        };
        System.out.println("afterExpr=" + result);
        return "value:" + result;
    }

    static int colonExpression(int n) {
        int result = switch (n) {
            case 1:
                yield 10;
            case 2:
                yield 20;
            default:
                yield -1;
        };
        System.out.println("afterColon=" + result);
        return result;
    }

    static void switchInsideLoop() {
        for (int n = 0; n < 3; n++) {
            switch (n) {
                case 0:
                    System.out.println("caseZero");
                    break;
                case 1:
                    System.out.println("caseOne");
                    continue;
                default:
                    System.out.println("caseOther");
            }
            System.out.println("afterSwitch=" + n);
        }
    }

    static String returnFromSwitch(String code) {
        if (code == null) {
            return "missing";
        }
        switch (code) {
            case "GO":
                return "run";
            default:
                return "unknown";
        }
    }

    public static void main(String[] args) {
        System.out.println("== break ==");
        breakLoop();

        System.out.println("== continue ==");
        continueLoop();

        System.out.println("== return ==");
        System.out.println("return1=" + find(2));
        System.out.println("return2=" + find(9));

        System.out.println("== yield ==");
        System.out.println("expression1=" + expression(1));
        System.out.println("expression9=" + expression(9));

        System.out.println("== colon-style expression ==");
        System.out.println("colon2=" + colonExpression(2));

        System.out.println("== switch in loop ==");
        switchInsideLoop();

        System.out.println("== return in switch statement ==");
        System.out.println("codeGo=" + returnFromSwitch("GO"));
        System.out.println("codeNull=" + returnFromSwitch(null));
        System.out.println("done");
    }
}
~~~

Dùng **JDK 17 trở lên**, mở PowerShell hoặc Bash tại thư mục chứa file:

~~~bash
java -version
javac -version
javac -encoding UTF-8 FlowTransferDemo.java
java FlowTransferDemo
~~~

**Exact expected stdout** của riêng lệnh `java FlowTransferDemo`:

~~~text
== break ==
seen=0
seen=1
seen=2
break=3
afterBreak
== continue ==
skip=0
keep=1
skip=2
keep=3
afterContinue
== return ==
return1=found:2
return2=missing
== yield ==
insideYield=1
afterExpr=one
expression1=value:one
afterExpr=other
expression9=value:other
== colon-style expression ==
afterColon=20
colon2=20
== switch in loop ==
caseZero
afterSwitch=0
caseOne
caseOther
afterSwitch=2
== return in switch statement ==
codeGo=run
codeNull=missing
done
~~~

`java -version` và `javac -version` cho thông tin phụ thuộc bản JDK cài đặt, không thuộc khối stdout cố định. Ví dụ chỉ dùng API Java chuẩn.

## 6. Những trường hợp dễ nhầm

| Hiểu nhầm | Cách đúng |
| --- | --- |
| “`break` là thoát phương thức.” | Nó chỉ thoát vòng lặp hoặc switch gần nhất |
| “`continue` thoát hẳn vòng.” | Nó bỏ phần còn lại của **lượt hiện tại** |
| “`continue` trong basic `for` bỏ phần cập nhật.” | Vẫn chạy phần cập nhật khi tiếp tục vòng đó |
| “`break` trong switch nằm trong vòng lặp sẽ thoát vòng ngoài.” | Nó thoát switch gần nhất |
| “`continue` trong switch đi tới case sau.” | Nó hướng tới vòng lặp gần nhất, hoặc bị cấm nếu không có vòng |
| “`return` và `yield` đều thoát phương thức.” | Chỉ `return` thoát phương thức; `yield` tạo kết quả switch expression |
| “Switch expression dùng `break value;`.” | Dùng `yield value;` hoặc `case -> expression` |
| “Dùng `return value;` thay `yield value;` trong switch expression.” | Không hợp lệ trong switch expression |
| “Mọi `switch case ->` đều trả giá trị.” | Switch statement cũng có thể dùng dạng mũi tên |

### Thoát bất thường và `finally`

Các lệnh chuyển luồng vẫn tuân thủ cơ chế `finally` của Java. Nếu đường thực thi đi ra khỏi một khối `try` qua `break`, `continue` hoặc `return`, khối `finally` thích hợp vẫn được thực hiện. Không nên dùng các lệnh này với giả định có thể bỏ qua việc dọn dẹp tài nguyên; với tài nguyên cần đóng, ưu tiên `try-with-resources`. Điều này không thay đổi **đích tác động về mặt ngữ nghĩa** của bốn từ khóa.

## 7. Bài tập tự kiểm tra

1. `break;` trong `for` làm phần `i++` của lượt đó chạy tiếp không?  
   *Đáp án:* Không.
2. `continue;` trong basic `for` thì `i++` có chạy không?  
   *Đáp án:* Có, nếu `continue` nhắm tới vòng `for` đó.
3. `return "ok";` bên trong vòng lặp có chạy câu lệnh bình thường sau vòng trong cùng phương thức không?  
   *Đáp án:* Không; phương thức đã kết thúc.
4. `yield "ok";` bên trong switch expression có kết thúc luôn phương thức bao ngoài không?  
   *Đáp án:* Không; nó cung cấp kết quả của switch expression.
5. Khi `switch` nằm trong `while`, `break;` từ một nhánh `case:` thoát cấu trúc nào?  
   *Đáp án:* Thoát switch; vòng while vẫn có thể tiếp tục.
6. `continue;` trong một `switch` không có vòng lặp bao ngoài có biên dịch không?  
   *Đáp án:* Không.
7. Có thể viết `yield` trong switch statement thông thường để trả giá trị của statement không?  
   *Đáp án:* Không; dùng switch expression nếu cần giá trị.

## Checklist

- [ ] Xác định đúng **đích điều khiển**: vòng/switch gần nhất, lượt tiếp theo, phương thức hay switch expression.
- [ ] Dùng `break` khi muốn **dừng** vòng/switch, `continue` khi muốn **bỏ qua một lượt**.
- [ ] Dùng `return` để **kết thúc phương thức** đúng kiểu trả về.
- [ ] Dùng `yield` khi cần **cung cấp giá trị** từ nhánh khối hoặc nhánh dấu hai chấm của switch expression.
- [ ] Không nhầm switch statement với switch expression.
- [ ] Kiểm tra cập nhật vòng lặp và xử lý tài nguyên khi đổi luồng.

**Kết luận:** `break` dừng cấu trúc bao quanh gần nhất; `continue` chuyển đến lượt vòng lặp tiếp theo; `return` rời phương thức; `yield` rời nhánh **switch expression** với một giá trị. Nắm đúng điểm đến của mỗi lệnh giúp tránh lỗi dừng sai phạm vi và viết mã Java 17 rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="iteration-and-control-flow-009">Nhận diện câu lệnh không thể tới và ảnh hưởng tới biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện câu lệnh không thể tới và ảnh hưởng tới biên dịch — Java 17+

## 1. `Unreachable statement` là gì?

Java kiểm tra **tính có thể tới được** (*reachability*) của các câu lệnh khi biên dịch. Nếu quy tắc ngôn ngữ chứng minh rằng không có đường điều khiển hợp lệ nào dẫn đến một câu lệnh, `javac` báo lỗi **`unreachable statement`** và không tạo được chương trình hợp lệ.

Ví dụ sai:

~~~java
static int answer() {
    return 42;
    // System.out.println("after return");
    // Nếu bỏ chú thích ở dòng trên: lỗi unreachable statement.
}
~~~

Sau `return 42;`, phương thức đã kết thúc. Câu lệnh ngay sau nó trong cùng khối không có cơ hội được thực thi.

**Phân biệt hai tình huống:**

- **Không thể tới được theo quy tắc biên dịch:** lỗi *compile-time*, ví dụ câu lệnh đứng trực tiếp sau `return` vô điều kiện.
- **Có thể tới được về cú pháp nhưng thực tế không chạy với dữ liệu đang xét:** thường **không phải lỗi biên dịch**, ví dụ nhánh `if (false)` hoặc một điều kiện phụ thuộc dữ liệu luôn sai trong bài toán.

Đừng dựa chỉ vào quan sát “tôi chạy thử chưa thấy dòng này được in” để kết luận compiler sẽ báo unreachable.

## 2. Bảng nhận diện nhanh

| Mẫu mã | Câu lệnh phía sau có thể tới? | Kết quả |
|---|---|---|
| `return; statement;` cùng khối | Không | **Lỗi biên dịch** |
| `throw ex; statement;` cùng khối | Không | **Lỗi biên dịch** |
| `break; statement;` trong cùng nhánh vòng lặp | Không | **Lỗi biên dịch** |
| `continue; statement;` trong cùng nhánh vòng lặp | Không | **Lỗi biên dịch** |
| `while (true) { ... }` không có đường `break` thoát | Không, đối với câu lệnh tiếp sau vòng | **Lỗi biên dịch** |
| `for (;;) { ... }` không có `break` thoát | Không, đối với câu lệnh tiếp sau vòng | **Lỗi biên dịch** |
| `while (false) { statement; }` | Body không thể tới | **Lỗi biên dịch** |
| `if (false) { statement; }` | Quy tắc `if` cho phép dạng này | **Hợp lệ**, nhánh không chạy |
| `if (flag) return; statement;` | Có đường đi `flag == false` | **Hợp lệ** |
| `while (true) { if (stop) break; }` rồi một câu lệnh | Có đường `break` thoát | **Có thể hợp lệ** |
| `do { statement; } while (false);` | Body chạy một lượt | **Hợp lệ** |

Các dòng dạng mẫu rút gọn không phải những chương trình tự chạy; phần cuối bài có chương trình Java 17+ độc lập với stdout cụ thể.

## 3. Sau `return`: không còn đường đi trong phương thức

~~~java
static String getLabel() {
    return "READY";
    // System.out.println("unreachable"); // Không được bật dòng này.
}
~~~

`return` kết thúc phương thức ngay; mọi câu lệnh đứng trực tiếp phía sau trong cùng khối đều không thể tới.

**Nhưng `return` có điều kiện thì khác:**

~~~java
static String status(boolean active) {
    if (active) {
        return "ACTIVE";
    }
    return "INACTIVE"; // Hợp lệ khi active == false.
}
~~~

`status(true)` trả `"ACTIVE"`, `status(false)` trả `"INACTIVE"`; câu `return` cuối vẫn có đường đi tới.

**Nếu cả hai nhánh đều kết thúc phương thức:**

~~~java
// Ví dụ không biên dịch nếu bật dòng cuối:
static int choose(boolean flag) {
    if (flag) {
        return 1;
    } else {
        return 2;
    }
    // System.out.println("after if/else"); // Unreachable.
}
~~~

Khi phân tích, hãy xem **tất cả đường đi** của các nhánh, không chỉ nhánh được dùng ở một lần gọi cụ thể.

## 4. Sau `throw`: luồng bình thường không tiếp tục

~~~java
static int requirePositive(int n) {
    if (n <= 0) {
        throw new IllegalArgumentException("n must be positive");
        // System.out.println("never"); // Unreachable.
    }
    return n;
}
~~~

Một `throw` kết thúc đường điều khiển thông thường tại vị trí đó. Exception có thể được **bắt ở caller hoặc một `catch` bao ngoài thích hợp**, nhưng Java không quay lại thực hiện câu lệnh đứng ngay sau `throw` trong cùng block.

Một ví dụ bất hợp lệ nếu viết liền:

~~~java
// throw new IllegalStateException("fail");
// System.out.println("after throw"); // Unreachable.
~~~

Không nhầm lỗi unreachable với thông báo về **checked exception**: đó là kiểm tra khác, liên quan đến khai báo và xử lý ngoại lệ.

## 5. Sau `break` và `continue` trong cùng khối

`break` rời vòng lặp hoặc `switch` phù hợp; `continue` chuyển sang lượt tiếp theo của vòng lặp.

~~~java
for (int i = 0; i < 3; i++) {
    if (i == 1) {
        continue;
        // System.out.println("after continue"); // Unreachable.
    }
    System.out.println("i=" + i);
}
~~~

**Exact stdout:**

~~~text
i=0
i=2
~~~

Với `break`:

~~~java
for (int i = 0; i < 3; i++) {
    if (i == 1) {
        break;
        // System.out.println("after break"); // Unreachable.
    }
    System.out.println("visit=" + i);
}
System.out.println("loop finished");
~~~

**Exact stdout:**

~~~text
visit=0
loop finished
~~~

Quan trọng: **câu lệnh trực tiếp sau `break`/`continue` trong cùng nhánh** mới là lỗi. Câu lệnh **sau toàn bộ vòng lặp** không nhất thiết unreachable: với `break`, luồng có thể đến được nó.

## 6. Vòng lặp vô hạn và khả năng hoàn thành bình thường

Một vòng `while (true)` hoặc `for (;;)` không có đường `break` rời vòng lặp **không thể hoàn thành bình thường** theo kiểm tra reachability của Java.

~~~java
// static void bad() {
//     while (true) {
//         System.out.println("repeat");
//     }
//     System.out.println("after loop"); // Lỗi unreachable statement.
// }
~~~

Dòng in sau vòng không thể được chạy theo đường thực thi bình thường. Nếu mục đích là lặp vô hạn có điều kiện thoát, hãy cung cấp một đường `break` hợp lệ:

~~~java
static int countTo(int limit) {
    int count = 0;
    while (true) {
        if (count >= limit) {
            break;
        }
        count++;
    }
    return count; // Hợp lệ: có đường break ra khỏi while.
}
~~~

Nếu `limit=3`, `countTo` trả 3.

**Lưu ý:** Sự tồn tại của một `break` **có thể tới được theo cú pháp**, nhắm đúng vòng lặp, mới có ý nghĩa cho luật hoàn thành bình thường. `break` đặt trong vòng lặp **khác** hoặc trong `switch` không đương nhiên chứng minh được vòng `while(true)` bao ngoài có thể kết thúc.

Vòng vô hạn là hợp lệ nếu không có câu lệnh theo sau cần tới; lỗi nằm ở phần mã bất khả đạt theo luật biên dịch, không phải ở bản thân `while(true)`.

## 7. Ngoại lệ quan trọng: `if (false)` khác `while (false)`

Java cố ý cho phép thân của `if` ngay cả khi điều kiện là **biểu thức hằng `false`**. Điều đó hỗ trợ các nhánh bật/tắt theo cấu hình khi biên dịch:

~~~java
if (false) {
    System.out.println("disabled");
}
System.out.println("after disabled branch");
~~~

**Exact stdout:**

~~~text
after disabled branch
~~~

Trái lại, thân của `while` với điều kiện là hằng `false` bị coi là không thể tới:

~~~java
// KHÔNG BIÊN DỊCH nếu bật đoạn này:
// while (false) {
//     System.out.println("unreachable body");
// }
~~~

`for` cơ bản có condition là hằng `false` cũng có vấn đề tương tự:

~~~java
// KHÔNG BIÊN DỊCH:
// for (int i = 0; false; i++) {
//     System.out.println(i);
// }
~~~

**Với `do-while`:**

~~~java
int count = 0;
do {
    count++;
} while (false);
System.out.println("count=" + count);
~~~

**Exact stdout:**

~~~text
count=1
~~~

Vì `do-while` thực thi thân **trước lần kiểm tra điều kiện đầu tiên**, phần thân vẫn reachable dù điều kiện là `false`.

**Chú ý:** `final boolean OFF = false;` là constant variable; `while (OFF)` cũng bị phân tích như điều kiện hằng false. Một biến boolean được đọc ở runtime (không phải constant expression) thường không bị suy luận theo cách này chỉ vì nó hiện chứa `false`.

## 8. Compile-time reachability không phải bộ giải mọi điều kiện logic

Ví dụ:

~~~java
static void check(int value) {
    if (value > 100 && value < 0) {
        System.out.println("impossible for int");
    }
    System.out.println("checked=" + value);
}
~~~

Điều kiện bên trong `if` không thể đúng với cùng một giá trị `int`, nhưng compiler Java 17 **không bắt buộc chứng minh mọi mâu thuẫn toán học** để cấm thân nhánh. Do vậy đây là code **biên dịch hợp lệ**, dù dòng `"impossible for int"` sẽ không xuất hiện.

Ngược lại, một lỗi unreachable sau `return` được quyết định từ cấu trúc điều khiển rõ ràng, không cần phân tích dữ liệu thực tế.

Ví dụ `if (flag) return;` vẫn có đường đi `flag == false`. Java không cấm câu lệnh theo sau chỉ vì trong một lần chạy bạn luôn truyền `true`.

**Quy tắc thực hành:** khác biệt giữa “không thể đạt **theo đặc tả reachability**” và “không thể xảy ra **theo yêu cầu nghiệp vụ**” cần được kiểm chứng riêng.

## 9. `finally` có thể chạy khi đang chuyển luồng

Với `try/finally`, câu lệnh trong `finally` thường vẫn chạy khi `try` thực hiện `return`, `break` hoặc `continue`:

~~~java
static int readValue() {
    try {
        System.out.println("inside try");
        return 5;
    } finally {
        System.out.println("inside finally");
    }
}
~~~

**Exact stdout nếu gọi `System.out.println("result=" + readValue());`:**

~~~text
inside try
inside finally
result=5
~~~

Vì vậy không được suy luận rằng “mọi code chạy sau khi biểu thức `return` được đánh giá đều unreachable”: `finally` thuộc luồng kết thúc của `try` và có quy tắc đặc biệt.

Tránh đặt một `return` hoặc `throw` mới vào `finally` một cách tùy tiện vì nó có thể **thay đổi kết quả hoặc che ngoại lệ ban đầu**.

## 10. Phân biệt unreachable với các lỗi compile-time khác

| Vấn đề | Ví dụ | Chẩn đoán / bản chất |
|---|---|---|
| Câu lệnh không thể tới | `return 1; println();` | `unreachable statement` |
| Biến chưa được gán chắc chắn | `int x; if (flag) x=1; println(x);` | `variable x might not have been initialized` |
| Thiếu đường trả giá trị | Phương thức trả `int` nhưng có nhánh đi hết không `return` | `missing return statement` |
| Điều kiện không là boolean | `if (1) { ... }` | Lỗi kiểu dữ liệu, không phải unreachable |
| Nhãn `break` sai đích | `break unknown;` | Lỗi giải quyết nhãn |
| Nhánh luôn sai theo nghiệp vụ | `if (n>100 && n<0)` | Vẫn có thể biên dịch, nhưng cần sửa logic |

Các lỗi này có thể cùng xuất hiện trong dự án, nhưng **không nên sửa bằng cùng một thủ thuật**. Lỗi reachability thường được xử lý bằng cách đặt lại trật tự câu lệnh hoặc sửa luồng thoát; lỗi definite assignment phải đảm bảo biến có giá trị trên mọi đường dùng biến.

## 11. Mẫu refactor khi gặp unreachable statement

**Sai — viết log sau `return`:**

~~~java
// static int compute(int x) {
//     return x * 2;
//     System.out.println("finished"); // Unreachable.
// }
~~~

**Đúng — log trước `return`:**

~~~java
static int compute(int x) {
    int result = x * 2;
    System.out.println("finished");
    return result;
}
~~~

**Sai — câu lệnh sau `continue` trong cùng block:**

~~~java
// if (skip) {
//     continue;
//     total++; // Unreachable.
// }
~~~

**Đúng — đặt xử lý ở nhánh không skip:**

~~~java
for (int i = 0; i < 4; i++) {
    if (i % 2 == 0) {
        continue;
    }
    System.out.println("process " + i);
}
~~~

**Exact stdout:**

~~~text
process 1
process 3
~~~

Nếu thật sự cần chạy bước cập nhật ở cả hai nhánh, hãy xem bước đó thuộc thân vòng, phần update của `for` hay `finally` theo mục tiêu. **Đừng xóa một dòng chỉ để compiler hết lỗi** khi nó chứa side effect nghiệp vụ quan trọng; hãy chuyển nó tới đường đi đúng.

## 12. Chương trình Java 17+ chạy độc lập

Lưu đúng tên **`ReachabilityDemo.java`**:

~~~java
public final class ReachabilityDemo {
    private ReachabilityDemo() {}

    static String branch(boolean active) {
        if (active) {
            return "ACTIVE";
        }
        return "INACTIVE";
    }

    static int firstPositive(int[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] > 0) {
                return i;
            }
        }
        return -1;
    }

    static int countTo(int limit) {
        int count = 0;
        while (true) {
            if (count >= limit) {
                break;
            }
            count++;
        }
        return count;
    }

    static int requirePositive(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }
        return n;
    }

    static void testContinue() {
        for (int i = 0; i < 3; i++) {
            if (i == 1) {
                continue;
            }
            System.out.println("continue:visit=" + i);
        }
    }

    static void testBreak() {
        for (int i = 0; i < 3; i++) {
            if (i == 1) {
                break;
            }
            System.out.println("break:visit=" + i);
        }
        System.out.println("break:after loop");
    }

    static void constantIf() {
        if (false) {
            System.out.println("disabled");
        }
        System.out.println("after if(false)");
    }

    static void doOnce() {
        int count = 0;
        do {
            count++;
        } while (false);
        System.out.println("do-while count=" + count);
    }

    static void check(int value) {
        if (value > 100 && value < 0) {
            System.out.println("impossible for int");
        }
        System.out.println("checked=" + value);
    }

    static int withFinally() {
        try {
            System.out.println("inside try");
            return 5;
        } finally {
            System.out.println("inside finally");
        }
    }

    public static void main(String[] args) {
        System.out.println("branch true=" + branch(true));
        System.out.println("branch false=" + branch(false));
        System.out.println("first positive=" + firstPositive(new int[]{-2, 0, 7, 9}));
        System.out.println("first missing=" + firstPositive(new int[]{-2, 0}));
        System.out.println("countTo 3=" + countTo(3));

        try {
            requirePositive(0);
        } catch (IllegalArgumentException ex) {
            System.out.println("throw caught=" + ex.getMessage());
        }
        System.out.println("require 4=" + requirePositive(4));

        testContinue();
        testBreak();
        constantIf();
        doOnce();
        check(5);
        System.out.println("finally result=" + withFinally());
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, mở terminal trong thư mục chứa file. Chương trình chỉ dùng API chuẩn JDK; không có tính năng preview, Maven hay Gradle.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ReachabilityDemo.java
java -cp . ReachabilityDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ReachabilityDemo.java
java -cp . ReachabilityDemo
~~~

### Exact expected stdout

~~~text
branch true=ACTIVE
branch false=INACTIVE
first positive=2
first missing=-1
countTo 3=3
throw caught=n must be positive
require 4=4
continue:visit=0
continue:visit=2
break:visit=0
break:after loop
after if(false)
do-while count=1
checked=5
inside try
inside finally
finally result=5
~~~

**Đọc kết quả:**

1. Hai nhánh của `branch` cho thấy `return` có điều kiện không làm mọi dòng sau `if` unreachable.
2. `firstPositive` có `return` sớm khi tìm thấy và đường `return -1` khi duyệt hết.
3. `countTo` có vòng `while(true)` nhưng vẫn hoàn thành bình thường nhờ `break`.
4. `requirePositive` ném exception ở đường lỗi và trả kết quả ở đường hợp lệ.
5. `continue` bỏ `i=1`, `break` dừng tại `i=1` rồi vẫn in được dòng sau vòng.
6. `if(false)` không thực thi nhánh nhưng chương trình vẫn biên dịch; `do-while(false)` chạy một lượt.
7. Điều kiện `value > 100 && value < 0` không thể đúng với `int`, nhưng compiler không nhất thiết cấm nhánh; chỉ dòng `checked=5` xuất hiện.
8. `finally` được thực hiện trước khi caller in kết quả trả về.

## 13. Bài tập và đáp án

### Bài A — câu lệnh nào không thể tới?

~~~java
static int f() {
    return 10;
    // System.out.println("X"); // Nếu bỏ comment?
}
~~~

**Đáp án:** `System.out.println("X")` là **unreachable statement**, gây lỗi biên dịch. Muốn ghi log hãy đặt nó **trước** `return`.

### Bài B — `if(false)` hay `while(false)`?

~~~java
if (false) {
    System.out.println("A");
}
// while (false) {
//     System.out.println("B");
// }
System.out.println("C");
~~~

**Đáp án:** ví dụ đang được bật biên dịch và in `C`. Nếu bỏ comment cho `while(false)`, compiler Java 17 báo unreachable ở **body của while**.

### Bài C — có `break` thì câu sau vòng có reachable không?

~~~java
int count = 0;
while (true) {
    if (count == 2) break;
    count++;
}
System.out.println(count);
~~~

**Đáp án:** reachable vì có đường `break`; stdout `2`.

### Bài D — `continue` trong nhánh

~~~java
for (int i = 0; i < 3; i++) {
    if (i == 1) {
        continue;
        // System.out.println("X");
    }
}
~~~

**Đáp án:** nếu bật dòng in `X`, nó unreachable; chuyển xử lý mong muốn lên **trước** `continue` hoặc sang nhánh phù hợp.

### Bài E — lỗi chưa khởi tạo có phải unreachable?

~~~java
// static void f(boolean flag) {
//     int x;
//     if (flag) x = 1;
//     System.out.println(x);
// }
~~~

**Đáp án:** không. Câu `println` vẫn có thể tới, nhưng `x` chưa chắc được gán trên đường `flag == false`. Đây là lỗi **definite assignment**, không phải lỗi reachability.

## 14. Checklist và kết luận

- [ ] Phân biệt **câu lệnh unreachable theo compiler** với code không chạy do dữ liệu thực tế.
- [ ] Nhận diện câu lệnh trực tiếp sau `return` hoặc `throw` vô điều kiện.
- [ ] Nhận diện câu lệnh trực tiếp sau `break`/`continue` trong cùng nhánh.
- [ ] Biết `while(true)`/`for(;;)` không có đường break làm mã theo sau unreachable.
- [ ] Hiểu đường `break` hợp lệ cho phép mã sau vòng có thể tới.
- [ ] Nhớ Java cho `if(false)` hợp lệ nhưng `while(false)` có body không hợp lệ.
- [ ] Hiểu `do ... while(false)` vẫn thực thi body một lượt.
- [ ] Không nhầm mâu thuẫn logic trong `if` với lỗi unreachable được compiler phát hiện.
- [ ] Biết `finally` có thể chạy trong quá trình `return` hoặc chuyển luồng khác.
- [ ] Phân biệt unreachable với `missing return` và `variable might not have been initialized`.
- [ ] Khi sửa mã, giữ nguyên ý nghĩa nghiệp vụ và đường đi của side effect.
- [ ] Chạy `ReachabilityDemo.java` bằng `javac`/`java` Java 17+, đối chiếu exact stdout.

**Kết luận:** Java 17 kiểm tra reachability bằng **các quy tắc tĩnh của luồng điều khiển**, không phải bằng thử chạy mọi dữ liệu có thể. Đặt mã trực tiếp sau một chuyển luồng vô điều kiện thường gây **lỗi biên dịch**, trong khi một nhánh không được thực thi ở runtime có thể vẫn hợp lệ (đặc biệt `if(false)`). Phân tích từng đường `return`, `throw`, `break`, `continue` và cơ chế dừng của vòng lặp giúp sửa lỗi **đúng luồng**, thay vì chỉ xóa thông báo từ compiler.

</details>

- [Quay lại đầu trang](#back-to-top)
