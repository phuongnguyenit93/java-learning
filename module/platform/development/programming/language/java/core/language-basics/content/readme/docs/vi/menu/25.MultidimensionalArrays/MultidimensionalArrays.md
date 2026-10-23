<a id="back-to-top"></a>

# Mảng nhiều chiều

## Menu
- [Mảng nhiều chiều là mảng chứa tham chiếu tới mảng con](#multidimensional-arrays-001)
- [Tạo ma trận chữ nhật và mảng có hàng dài khác nhau](#multidimensional-arrays-002)
- [Khởi tạo từng mảng con và hàng chưa được tạo](#multidimensional-arrays-003)
- [Lấy số hàng và độ dài của từng hàng](#multidimensional-arrays-004)
- [Duyệt bằng chỉ số hoặc enhanced for lồng nhau](#multidimensional-arrays-005)
- [Sao chép nông và chia sẻ các mảng con](#multidimensional-arrays-006)
- [Arrays.deepToString() và Arrays.deepEquals()](#multidimensional-arrays-007)
- [Mảng tham chiếu hiệp biến và nguy cơ ArrayStoreException](#multidimensional-arrays-008)
- [Phân biệt int&#91;&#93; với Integer&#91;&#93; và khả năng gán qua Object](#multidimensional-arrays-009)

## <a id="multidimensional-arrays-001">Mảng nhiều chiều là mảng chứa tham chiếu tới mảng con</a>

<details>
<summary>Xem chi tiết</summary>

# Mảng nhiều chiều là mảng chứa tham chiếu tới mảng con trong Java

> **Java core — language-basics / Mảng nhiều chiều**  
> **Yêu cầu:** JDK 17+; ví dụ Java độc lập, không cần thư viện ngoài.  
> **Mục tiêu:** Hiểu đúng cấu trúc `int[][]` là **một mảng ngoài chứa các tham chiếu tới những mảng `int[]`**, biết phân biệt mảng con chưa tạo (`null`) với mảng con đã tạo, lý giải hàng có độ dài khác nhau, chia sẻ tham chiếu và thao tác với hai tầng chỉ số.

## 1. Mô hình “mảng chứa các mảng” thay vì một bảng nguyên khối

Trong Java, `int[][]` được đọc là “mảng có phần tử kiểu `int[]`”. Mỗi phần tử của **mảng ngoài** là **một tham chiếu** tới một mảng `int[]` hoặc giá trị `null`. Chỉ các phần tử **bên trong mảng `int[]`** mới chứa giá trị nguyên thủy `int`.

~~~text
int[][] grid = new int[2][3];

grid (tham chiếu tới mảng ngoài int[][])
       │
       ▼
  +----------+       +-------------+
  | [0]  ----|------>| int[3]      |  [0, 0, 0]
  | [1]  ----|---+   +-------------+
  +----------+   |   +-------------+
                 +-->| int[3]      |  [0, 0, 0]
                     +-------------+
~~~

Ở mức ngữ nghĩa Java, `new int[2][3]` tạo **một mảng ngoài có hai phần tử tham chiếu**, và mỗi phần tử đó được trỏ tới **một mảng con `int[3]` riêng**. Vì thế:

~~~java
int[][] grid = new int[2][3];
System.out.println(grid.length);       // 2: số phần tử của mảng ngoài.
System.out.println(grid[0].length);    // 3: số phần tử hàng 0.
System.out.println(grid[1].length);    // 3: số phần tử hàng 1.
System.out.println(grid[0] == grid[1]); // false: hai mảng con khác nhau.
~~~

Cách diễn đạt “2 hàng × 3 cột” hữu ích để hình dung, nhưng **không nên suy ra rằng Java bắt buộc lưu cả ma trận thành một vùng `int` liên tục**. Hai hàng là hai đối tượng mảng độc lập, được tham chiếu từ mảng ngoài.

## 2. Khởi tạo cả hai tầng hoặc chỉ mảng ngoài

Sự khác biệt quan trọng nằm ở **các tầng đã được `new` tạo**:

| Cú pháp | Mảng ngoài | Các mảng con ban đầu |
| --- | --- | --- |
| `new int[2][3]` | Có, độ dài 2 | Hai `int[3]` khác nhau, chứa `0` |
| `new int[2][]` | Có, độ dài 2 | Cả hai tham chiếu đều `null` |
| `new int[2][0]` | Có, độ dài 2 | Hai `int[0]` hợp lệ và khác `null` |
| `new int[0][3]` | Có, độ dài 0 | Không có phần tử mảng ngoài, nên không có hàng để truy cập |
| `new String[2][3]` | Có, độ dài 2 | Hai `String[3]`, từng phần tử chuỗi là `null` |

Trong `new int[2][]`, các phần tử của mảng ngoài có **kiểu tham chiếu `int[]`**, nên nhận mặc định `null`, dù loại phần tử ở tầng sâu hơn là `int`.

~~~java
int[][] partial = new int[2][];
System.out.println(partial.length);       // 2
System.out.println(partial[0] == null);   // true
System.out.println(partial[1] == null);   // true

partial[0] = new int[3]; // Tạo hàng số 0, gồm [0, 0, 0].
partial[1] = new int[0]; // Tạo hàng số 1, độ dài 0.
System.out.println(partial[0].length); // 3
System.out.println(partial[1].length); // 0
~~~

Từ `partial[1] == null` trước khi gán đến `partial[1].length == 0` sau khi gán là **hai trạng thái hoàn toàn khác nhau**: hàng chưa có đối tượng và hàng có đối tượng mảng rỗng.

Với cú pháp `new` có nhiều chiều, không được chỉ định chiều sâu hơn khi chiều trước còn để trống, chẳng hạn `new int[][3]` không hợp lệ. Có thể bỏ các chiều ở cuối, ví dụ `new int[2][]` hoặc `new int[2][3][]`.

## 3. Hai tầng chỉ số và kiểm tra hàng `null`

Biểu thức `matrix[row][column]` tương đương về ý nghĩa với việc **lấy tham chiếu mảng con** `matrix[row]` rồi đánh chỉ số `[column]` trên mảng con đó:

~~~java
int[][] matrix = {{10, 20}, {30, 40}};
int[] firstRow = matrix[0];

System.out.println(firstRow[1]); // 20
System.out.println(matrix[0][1]); // 20
~~~

Để đọc `matrix[r][c]` thành công, cần:

1. Biến `matrix` tham chiếu tới một mảng ngoài thực sự (không `null`).
2. `r` nằm từ `0` đến `matrix.length - 1`.
3. `matrix[r]` không phải `null`.
4. `c` nằm từ `0` đến `matrix[r].length - 1`.

Thiếu một trong các điều kiện này có thể gây `NullPointerException` hoặc `ArrayIndexOutOfBoundsException`. Một mảng ngoài đã được tạo **không** bảo đảm rằng mọi mảng con cũng đã được tạo.

Ví dụ kiểm tra an toàn khi chỉ muốn đọc nếu vị trí tồn tại:

~~~java
static boolean containsCell(int[][] matrix, int r, int c) {
    return matrix != null
            && r >= 0 && r < matrix.length
            && matrix[r] != null
            && c >= 0 && c < matrix[r].length;
}
~~~

Phương thức trên dùng toán tử `&&` có đánh giá ngắn mạch: nếu điều kiện trước sai, điều kiện sau không được đánh giá. Vì vậy không vô tình truy cập `matrix[r]` trước khi biết `r` hợp lệ.

## 4. Các hàng không cần có cùng độ dài: mảng “răng cưa”

Mỗi phần tử của mảng ngoài là một tham chiếu tới `int[]`, nên các hàng có thể có độ dài **khác nhau** (*jagged/ragged array*):

~~~java
int[][] rows = new int[3][];
rows[0] = new int[] {2, 4};
rows[1] = new int[0];
rows[2] = new int[] {9};

System.out.println(rows.length);    // 3
System.out.println(rows[0].length); // 2
System.out.println(rows[1].length); // 0
System.out.println(rows[2].length); // 1
~~~

Có thể khởi tạo ngắn gọn bằng danh sách:

~~~java
int[][] triangle = {
    {1},
    {2, 3},
    {4, 5, 6}
};
~~~

Trong vòng lặp, đừng áp đặt số cột bằng `rows[0].length` cho mọi hàng. Hãy kiểm tra độ dài **của từng hàng**:

~~~java
static int sumAll(int[][] rows) {
    int sum = 0;
    for (int[] row : rows) {
        if (row == null) {
            continue; // Bỏ qua hàng chưa được tạo theo hợp đồng ví dụ.
        }
        for (int value : row) {
            sum += value;
        }
    }
    return sum;
}
~~~

Phương thức `sumAll` xử lý cả hàng rỗng và hàng `null`, miễn chính mảng ngoài `rows` khác `null`. Với `triangle` phía trên, kết quả là 21. Với ví dụ `rows` ban đầu, tổng là 15.

## 5. Hai hàng có thể tham chiếu cùng một mảng con

Cũng giống biến tham chiếu đối tượng, **hai phần tử của mảng ngoài có thể giữ cùng một tham chiếu**:

~~~java
int[] shared = {10, 20};
int[][] aliases = {shared, shared};

System.out.println(aliases[0] == aliases[1]); // true
aliases[0][0] = 99;
System.out.println(aliases[1][0]);             // 99
~~~

Cập nhật `aliases[0][0]` thay đổi **đối tượng mảng con chung**, nên khi đọc qua `aliases[1][0]` cũng thấy 99.

**Nhưng gán lại tham chiếu hàng không sửa hàng còn lại:**

~~~java
aliases[0] = new int[] {1};
// aliases[0] trỏ tới mảng con mới [1].
// aliases[1] vẫn trỏ tới mảng con cũ [99, 20].
~~~

Đây là hai kiểu thao tác khác nhau:

| Thao tác | Điều xảy ra |
| --- | --- |
| `aliases[0][0] = 99` | Thay **phần tử của mảng con** mà hàng 0 đang tham chiếu |
| `aliases[0] = new int[] {1}` | Thay **tham chiếu ở hàng 0** bằng tham chiếu tới mảng mới |
| `aliases[1] = aliases[0]` | Để hai hàng tham chiếu chung một mảng con hiện tại |

Không thể coi hàng của `int[][]` là “bản sao giá trị” mỗi lần gán. Đây là **tham chiếu**, không phải phép sao chép sâu (*deep copy*).

## 6. Sao chép mảng ngoài vẫn chia sẻ các hàng

Phép `matrix.clone()` tạo một mảng ngoài mới, nhưng các phần tử của nó vẫn là **tham chiếu đến những hàng cũ**. Vì thế thay `copy[0][1]` có thể làm `matrix[0][1]` đổi theo; còn gán `copy[0] = new int[]{8}` chỉ đổi tham chiếu hàng của bản sao. Hai thao tác này nối trực tiếp với mô hình ở mục 5.

Nếu cần dữ liệu `int` độc lập, hãy sao chép từng mảng con không `null` vào mảng ngoài mới. Bài **Sao chép nông và chia sẻ các mảng con** phân tích đầy đủ `clone()`, `Arrays.copyOf()`, mảng có nhiều alias và phần tử đối tượng mutable.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **ArrayOfArraysDemo.java**:

~~~java
import java.util.Arrays;

public final class ArrayOfArraysDemo {
    private static void printRows(String label, int[][] rows) {
        System.out.println(label + "Length=" + rows.length);
        for (int r = 0; r < rows.length; r++) {
            int[] subarray = rows[r];
            String value = subarray == null
                    ? "null"
                    : Arrays.toString(subarray);
            System.out.println(label + "[" + r + "]=" + value);
        }
    }

    public static void main(String[] args) {
        int[][] rectangular = new int[2][3];
        System.out.println("rectRows=" + rectangular.length);
        System.out.println("rectColumns0=" + rectangular[0].length);
        System.out.println("rectColumns1=" + rectangular[1].length);
        System.out.println("rectDistinct="
                + (rectangular[0] != rectangular[1]));
        rectangular[0][1] = 7;
        System.out.println("rectRow0=" + Arrays.toString(rectangular[0]));
        System.out.println("rectRow1=" + Arrays.toString(rectangular[1]));

        int[][] partial = new int[3][];
        printRows("partial", partial);
        partial[0] = new int[] {2, 4};
        partial[1] = new int[0];
        partial[2] = new int[] {9};
        printRows("ragged", partial);
        System.out.println("lastLength=" + partial[2].length);

        int[] shared = {10, 20};
        int[][] aliases = {shared, shared};
        System.out.println("sharedRowsSame=" + (aliases[0] == aliases[1]));
        aliases[0][0] = 99;
        System.out.println("sameRowSeen=" + aliases[1][0]);

        int[] oldRow = aliases[0];
        aliases[0] = new int[] {1};
        System.out.println("afterReplaceRowsSame="
                + (aliases[0] == aliases[1]));
        System.out.println("oldRow=" + Arrays.toString(oldRow));
        System.out.println("newRow=" + Arrays.toString(aliases[0]));
        System.out.println("remainingRow=" + Arrays.toString(aliases[1]));

        int[][] copiedOuter = partial.clone();
        System.out.println("cloneOuterSame=" + (copiedOuter == partial));
        System.out.println("cloneRowSame="
                + (copiedOuter[0] == partial[0]));
        copiedOuter[0][1] = 55;
        System.out.println("mutatedSource=" + partial[0][1]);
        copiedOuter[0] = new int[] {8};
        System.out.println("sourceRow=" + Arrays.toString(partial[0]));
        System.out.println("copiedRow=" + Arrays.toString(copiedOuter[0]));

        int[][] noRows = new int[0][3];
        int[][] zeroWidth = new int[2][0];
        System.out.println("noRows=" + noRows.length);
        System.out.println("zeroWidthRow=" + zeroWidth[0].length);
        System.out.println("zeroWidthNonNull=" + (zeroWidth[0] != null));

        System.out.println("outerType=" + partial.getClass().getTypeName());
        System.out.println("innerType=" + partial[0].getClass().getTypeName());

        try {
            int[][] missingRow = new int[1][];
            System.out.println(missingRow[0].length);
        } catch (NullPointerException ex) {
            System.out.println("nullRowError=NullPointerException");
        }
        System.out.println("deepRagged=" + Arrays.deepToString(partial));
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ArrayOfArraysDemo.java
java ArrayOfArraysDemo
~~~

**Expected stdout chính xác:**

~~~text
rectRows=2
rectColumns0=3
rectColumns1=3
rectDistinct=true
rectRow0=[0, 7, 0]
rectRow1=[0, 0, 0]
partialLength=3
partial[0]=null
partial[1]=null
partial[2]=null
raggedLength=3
ragged[0]=[2, 4]
ragged[1]=[]
ragged[2]=[9]
lastLength=1
sharedRowsSame=true
sameRowSeen=99
afterReplaceRowsSame=false
oldRow=[99, 20]
newRow=[1]
remainingRow=[99, 20]
cloneOuterSame=false
cloneRowSame=true
mutatedSource=55
sourceRow=[2, 55]
copiedRow=[8]
noRows=0
zeroWidthRow=0
zeroWidthNonNull=true
outerType=int[][]
innerType=int[]
nullRowError=NullPointerException
deepRagged=[[2, 55], [], [9]]
~~~

Phần `rectDistinct=true` cho thấy mỗi hàng của `new int[2][3]` là một mảng con độc lập. `partial[r]=null` thể hiện mảng con chưa được tạo. `sharedRowsSame=true` và `sameRowSeen=99` chứng minh hai hàng có thể cùng tham chiếu một mảng con; `cloneRowSame=true` cho thấy sao chép mảng ngoài vẫn chia sẻ các hàng.

Ở dòng cuối, `Arrays.deepToString(partial)` in cấu trúc lồng nhau theo nội dung, khác với `Arrays.toString(partial)` có thể hiển thị chuỗi định danh mảng con khó đọc.

## 8. Lỗi thường gặp, checklist và bài tập

| Nhầm lẫn hoặc lỗi | Cách hiểu đúng |
| --- | --- |
| `int[][]` là một khối ô `int` liên tục duy nhất | Là mảng ngoài chứa **tham chiếu tới `int[]`** |
| `new int[2][]` đã tạo hai `int[]` | Sai: hai hàng đang là `null` |
| `new int[2][3]` dùng một đối tượng hàng chung | Sai: mỗi hàng là mảng con riêng |
| Mọi hàng đều phải có cùng độ dài | Sai: mỗi mảng con có thể có độ dài riêng |
| `matrix.length` là số cột | Sai: là số phần tử của mảng ngoài (số hàng nếu tổ chức theo hàng) |
| `matrix[r].length` luôn đọc được | Cần `r` hợp lệ và `matrix[r] != null` |
| Gán `a[1] = a[0]` sao chép giá trị hàng | Sai: gán **tham chiếu**, khiến hai hàng chia sẻ cùng mảng |
| `a.clone()` sao chép toàn bộ các mảng con | Sai: chỉ tạo mảng ngoài mới, các tham chiếu hàng được sao chép |
| `new int[2][0]` và `new int[2][]` giống nhau | Sai: hàng rỗng không phải hàng `null` |
| Dùng `Arrays.toString` trên `int[][]` để xem tất cả số | Ưu tiên `Arrays.deepToString` cho hiển thị nhiều tầng |

**Bài 1.** Tạo `int[][] data = new int[3][];`. Kiểm tra `data[0] == null`, rồi lần lượt gán `new int[2]`, `new int[0]` và `new int[] {5}` cho ba hàng. In độ dài mỗi hàng.

**Bài 2.** Khởi tạo `int[][] jagged = {{1}, {2, 3}, {4, 5, 6}};`, viết hai vòng lặp để tính tổng và giải thích tại sao vòng trong phải dựa vào `jagged[row].length`.

**Bài 3.** Tạo hai hàng cùng tham chiếu `int[] shared`, cập nhật một phần tử qua hàng đầu rồi đọc qua hàng còn lại. Sau đó gán lại hàng đầu thành một mảng con mới và dự đoán kết quả.

**Bài 4.** So sánh `new int[0][3]`, `new int[2][0]` và `new int[2][]` về số hàng, `null` và độ dài mảng con hợp lệ.

**Bài 5.** Dùng `clone()` cho mảng ngoài, chứng minh `outerCopy[0] == original[0]`. Sau đó viết hàm sao chép từng `int[]` để thay đổi bản sao mà không ảnh hưởng bản gốc.

### Ghi nhớ

**`int[][]` là mảng mà mỗi phần tử có kiểu `int[]`, tức một tham chiếu tới mảng con.** Dạng `new int[rows][columns]` tạo cả mảng ngoài và các hàng; `new int[rows][]` chỉ tạo mảng ngoài, hàng ban đầu là `null`. Các hàng có thể khác độ dài, chia sẻ tham chiếu hoặc được thay bằng mảng khác; vì vậy hãy kiểm tra từng hàng và hiểu rõ việc sao chép tham chiếu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multidimensional-arrays-002">Tạo ma trận chữ nhật và mảng có hàng dài khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo ma trận chữ nhật và mảng có hàng dài khác nhau — Java 17+

## 1. Mảng hai chiều trong Java thực chất là gì?

Trong Java, một mảng hai chiều như `int[][] matrix` là **mảng chứa các tham chiếu tới mảng `int[]`**. Mỗi phần tử ở mảng ngoài là **một hàng**; mỗi hàng tự có trường `length`.

Vì vậy Java cho phép cả:

- **Ma trận chữ nhật (rectangular array):** các hàng đều có cùng số cột.
- **Mảng răng cưa (jagged/ragged array):** các hàng có thể dài khác nhau, có hàng rỗng, thậm chí hàng chưa được gán (giá trị `null`).

Đây không phải hai kiểu dữ liệu Java riêng biệt: **cả hai đều khai báo `int[][]`**. Khác biệt nằm ở cách tạo và độ dài thực tế của từng `int[]` con.

~~~java
int[][] rectangle = new int[2][3];
int[][] jagged = {{1}, {2, 3}, {4, 5, 6}};
~~~

- `rectangle` gồm **2 hàng × 3 cột**; mọi phần tử mặc định bằng 0.
- `jagged` gồm **3 hàng**, lần lượt dài **1, 2, 3** phần tử.

**Quy tắc bắt buộc:** duyệt theo `matrix.length` cho số hàng, và `matrix[row].length` cho số phần tử trong **chính hàng đó**.

## 2. Tạo ma trận chữ nhật với `new int[rows][cols]`

~~~java
int[][] table = new int[2][3];
table[0][0] = 7;
table[0][2] = 9;
table[1][1] = 5;

System.out.println(java.util.Arrays.deepToString(table));
~~~

**Exact expected stdout:**

~~~text
[[7, 0, 9], [0, 5, 0]]
~~~

Java cấp phát mảng ngoài dài 2 và **hai mảng con riêng biệt**, mỗi mảng con dài 3:

~~~text
table (int[][]) ───> mảng ngoài length = 2
                       [0] ──> int[] {7, 0, 9} length = 3
                       [1] ──> int[] {0, 5, 0} length = 3

Chỉ số hàng: 0, 1
Chỉ số cột trong mỗi hàng: 0, 1, 2
~~~

| Biểu thức | Kết quả |
|---|---:|
| `table.length` | 2 (số hàng) |
| `table[0].length` | 3 |
| `table[1].length` | 3 |
| `table[0][2]` | 9 |
| `table[1][1]` | 5 |

Các phần tử `int` mặc định bằng 0; với `boolean[][]` mặc định là false, còn các ô phần tử kiểu đối tượng mặc định là null.

**Quan trọng:** Mảng chữ nhật được tạo như trên có các hàng dài bằng nhau **ban đầu**, nhưng Java không khóa vĩnh viễn hình dạng đó. Vẫn có thể thay `table[1]` bằng mảng khác độ dài; khi ấy dữ liệu trở thành mảng răng cưa.

## 3. Tạo mảng có hàng dài khác nhau bằng `new int[rows][]`

~~~java
int[][] jagged = new int[3][];
jagged[0] = new int[]{4, 5};
jagged[1] = new int[0];
jagged[2] = new int[]{6, 7, 8};

System.out.println(java.util.Arrays.deepToString(jagged));
~~~

**Exact expected stdout:**

~~~text
[[4, 5], [], [6, 7, 8]]
~~~

Cách này chia thành **hai giai đoạn**:

1. `new int[3][]` tạo mảng ngoài ba phần tử, **mỗi phần tử ban đầu là null**.
2. Gán từng hàng bằng một `int[]` riêng có độ dài tùy nhu cầu.

~~~text
jagged (int[][]) ──> mảng ngoài length = 3
                      [0] ──> {4, 5}     length = 2
                      [1] ──> {}         length = 0
                      [2] ──> {6, 7, 8}  length = 3
~~~

**Không nhầm:**

~~~java
int[][] a = new int[3][];  // Hàng a[0], a[1], a[2] ban đầu null.
int[][] b = new int[3][0]; // Có ba mảng con thật, mỗi mảng dài 0.
~~~

Với `a[0].length` trước khi gán hàng, Java ném `NullPointerException`. Với `b[0].length`, kết quả là `0`.

## 4. Khởi tạo trực tiếp bằng dấu `{ }`

Nếu biết sẵn các giá trị, có thể khai báo:

~~~java
int[][] rectangular = {
    {1, 2, 3},
    {4, 5, 6}
};

int[][] ragged = {
    {10},
    {20, 30},
    {},
    {40, 50, 60}
};
~~~

Hoặc tạo và gán bằng cú pháp array creation:

~~~java
int[][] values = new int[][]{
    {1, 2},
    {3},
    {}
};
~~~

**Chú ý:** `int[][] x = {{1}, {2, 3}};` hợp lệ tại thời điểm khai báo; nhưng để gán nguyên một mảng mới cho biến đã khai báo, phải dùng `new int[][] { ... }` thay vì chỉ viết `{{...}}`.

Có thể chứa hàng null một cách tường minh:

~~~java
int[][] partial = {
    {1, 2},
    null,
    {}
};
~~~

Kiểu của cả `rectangular`, `ragged` và `partial` vẫn là `int[][]`.

## 5. `length` của mảng ngoài và từng hàng

~~~java
int[][] matrix = {{10, 20}, {30}, {}};

System.out.println("rows=" + matrix.length);
for (int row = 0; row < matrix.length; row++) {
    System.out.println("row " + row + " length=" + matrix[row].length);
}
~~~

**Exact expected stdout:**

~~~text
rows=3
row 0 length=2
row 1 length=1
row 2 length=0
~~~

| Biểu thức | Ý nghĩa |
|---|---|
| `matrix.length` | Số phần tử ở mảng ngoài, thường được gọi là **số hàng** |
| `matrix[row]` | Tham chiếu đến mảng con ở hàng `row` |
| `matrix[row].length` | Độ dài **riêng** của hàng đó (nếu khác null) |
| `matrix[row][col]` | Giá trị tại một ô có hàng và cột hợp lệ |

**Không có** một trường chung `matrix.columns` hay `matrix[0].length` được Java bảo đảm là số cột cho mọi hàng. Chỉ khi bạn **đã xác lập và duy trì** bất biến ma trận chữ nhật thì một độ dài cột chung mới có nghĩa.

## 6. Giữ đúng hình dạng khi sử dụng mảng

Việc tạo mảng chữ nhật hay răng cưa quyết định cách thuật toán đọc nó. Dùng `matrix.length` cho số hàng, `matrix[row].length` cho độ dài **hàng hiện tại**, và kiểm tra `matrix[row] != null` trước khi dùng `.length` nếu hàng được phép chưa cấp phát. Cần xử lý `new int[0][3]` (không có hàng), `new int[2][0]` (hai hàng rỗng) và `new int[2][]` (hai hàng ban đầu `null`) theo đúng hợp đồng dữ liệu.

Vòng `for` và enhanced `for` sẽ được phân tích theo mục đích sửa/đọc ở bài **Duyệt bằng chỉ số hoặc enhanced for lồng nhau**. Phần này tập trung vào **tạo dữ liệu và duy trì bất biến hình dạng**.

## 7. Thay hàng có thể biến ma trận chữ nhật thành mảng răng cưa

~~~java
int[][] matrix = new int[2][3];
matrix[1] = new int[]{7};

System.out.println(java.util.Arrays.deepToString(matrix));
System.out.println(matrix[0].length + "," + matrix[1].length);
~~~

**Exact expected stdout:**

~~~text
[[0, 0, 0], [7]]
3,1
~~~

Phần tử `matrix[1]` là một **biến chứa tham chiếu mảng con**, nên có thể được gán sang mảng mới. Trường `length` của từng mảng con vẫn cố định, nhưng **thay tham chiếu hàng** có thể làm các độ dài khác nhau.

Nếu bài toán bắt buộc là ma trận chữ nhật, chương trình phải **duy trì hợp đồng** rằng tất cả hàng tồn tại và cùng dài; khai báo `int[][]` không tự bảo đảm điều đó mãi mãi.

## 8. Kiểm tra dữ liệu có hình chữ nhật hay không

Một quy ước hữu ích: mảng ngoài rỗng được coi là chữ nhật rỗng; mảng ngoài null hoặc có hàng null không đạt điều kiện.

~~~java
static boolean isRectangular(int[][] data) {
    if (data == null) return false;
    if (data.length == 0) return true;
    if (data[0] == null) return false;

    int columns = data[0].length;
    for (int[] row : data) {
        if (row == null || row.length != columns) {
            return false;
        }
    }
    return true;
}
~~~

| Đầu vào | `isRectangular` |
|---|---|
| `new int[2][3]` | true |
| `new int[2][0]` | true |
| `new int[0][3]` | true theo quy ước trong hàm |
| `{{1, 2}, {3}}` | false |
| `new int[2][]` chưa gán hàng | false |
| `null` | false |

Phép kiểm tra có chi phí `O(rows)` vì chỉ đọc độ dài mỗi hàng; không cần duyệt toàn bộ giá trị. Tuy nhiên nó chỉ kiểm tra **trạng thái hiện tại** — nếu sau đó thay một hàng, kết quả có thể khác.



## 9. Chương trình Java 17+ hoàn chỉnh, chạy bằng CLI

Tạo file đúng tên **`RectangularJaggedArrayDemo.java`**:

~~~java
import java.util.Arrays;

public final class RectangularJaggedArrayDemo {
    private RectangularJaggedArrayDemo() {}

    static void describe(String name, int[][] matrix) {
        System.out.println(name + " rows=" + matrix.length);
        for (int row = 0; row < matrix.length; row++) {
            if (matrix[row] == null) {
                System.out.println(name + " row " + row + "=null");
            } else {
                System.out.println(name + " row " + row
                        + " length=" + matrix[row].length
                        + " values=" + Arrays.toString(matrix[row]));
            }
        }
    }

    static int sum(int[][] matrix) {
        int total = 0;
        for (int[] row : matrix) {
            if (row == null) continue;
            for (int value : row) {
                total += value;
            }
        }
        return total;
    }

    static boolean validCell(int[][] matrix, int row, int col) {
        return matrix != null
                && row >= 0 && row < matrix.length
                && matrix[row] != null
                && col >= 0 && col < matrix[row].length;
    }

    static boolean isRectangular(int[][] matrix) {
        if (matrix == null) return false;
        if (matrix.length == 0) return true;
        if (matrix[0] == null) return false;
        int columns = matrix[0].length;
        for (int[] row : matrix) {
            if (row == null || row.length != columns) {
                return false;
            }
        }
        return true;
    }

    static int[][] copyRows(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int row = 0; row < source.length; row++) {
            copy[row] = source[row] == null
                    ? null
                    : Arrays.copyOf(source[row], source[row].length);
        }
        return copy;
    }

    public static void main(String[] args) {
        int[][] rectangle = new int[2][3];
        rectangle[0][0] = 1;
        rectangle[0][2] = 3;
        rectangle[1][1] = 5;
        describe("rectangle", rectangle);
        System.out.println("rectangle deep=" + Arrays.deepToString(rectangle));
        System.out.println("rectangle sum=" + sum(rectangle));
        System.out.println("rectangle shape=" + isRectangular(rectangle));

        int[][] jagged = new int[3][];
        jagged[0] = new int[]{4, 5};
        jagged[1] = new int[0];
        jagged[2] = new int[]{6, 7, 8};
        describe("jagged", jagged);
        System.out.println("jagged deep=" + Arrays.deepToString(jagged));
        System.out.println("jagged sum=" + sum(jagged));
        System.out.println("jagged shape=" + isRectangular(jagged));

        System.out.println("jagged valid 0,1=" + validCell(jagged, 0, 1));
        System.out.println("jagged valid 1,0=" + validCell(jagged, 1, 0));
        System.out.println("jagged valid 2,2=" + validCell(jagged, 2, 2));
        System.out.println("jagged valid 3,0=" + validCell(jagged, 3, 0));

        int[][] zeroRows = new int[0][3];
        int[][] zeroColumns = new int[2][0];
        System.out.println("zero rows=" + zeroRows.length);
        System.out.println("zero cols rows=" + zeroColumns.length
                + ",row0=" + zeroColumns[0].length);
        System.out.println("zero rows rectangular=" + isRectangular(zeroRows));
        System.out.println("zero cols rectangular=" + isRectangular(zeroColumns));

        int[][] partial = new int[2][];
        partial[1] = new int[]{9};
        describe("partial", partial);
        System.out.println("partial sum=" + sum(partial));
        System.out.println("partial shape=" + isRectangular(partial));

        try {
            System.out.println(jagged[1][0]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("empty row index=OUT_OF_BOUNDS");
        }
        try {
            System.out.println(partial[0].length);
        } catch (NullPointerException ex) {
            System.out.println("null row length=NULL_POINTER");
        }

        int[][] shallow = rectangle.clone();
        shallow[0][0] = 99;
        System.out.println("shallow original=" + rectangle[0][0]);

        int[][] separate = copyRows(rectangle);
        separate[0][0] = -1;
        System.out.println("separate original=" + rectangle[0][0]
                + ",copy=" + separate[0][0]);

        jagged[0] = new int[]{1};
        System.out.println("replaced row length=" + jagged[0].length);
        System.out.println("jagged after replacement=" + Arrays.deepToString(jagged));
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; terminal ở thư mục chứa file. Không cần thư viện ngoài JDK, Maven, Gradle hay tính năng preview.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 RectangularJaggedArrayDemo.java
java -cp . RectangularJaggedArrayDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\RectangularJaggedArrayDemo.java
java -cp . RectangularJaggedArrayDemo
~~~

### Exact expected stdout

~~~text
rectangle rows=2
rectangle row 0 length=3 values=[1, 0, 3]
rectangle row 1 length=3 values=[0, 5, 0]
rectangle deep=[[1, 0, 3], [0, 5, 0]]
rectangle sum=9
rectangle shape=true
jagged rows=3
jagged row 0 length=2 values=[4, 5]
jagged row 1 length=0 values=[]
jagged row 2 length=3 values=[6, 7, 8]
jagged deep=[[4, 5], [], [6, 7, 8]]
jagged sum=30
jagged shape=false
jagged valid 0,1=true
jagged valid 1,0=false
jagged valid 2,2=true
jagged valid 3,0=false
zero rows=0
zero cols rows=2,row0=0
zero rows rectangular=true
zero cols rectangular=true
partial rows=2
partial row 0=null
partial row 1 length=1 values=[9]
partial sum=9
partial shape=false
empty row index=OUT_OF_BOUNDS
null row length=NULL_POINTER
shallow original=99
separate original=99,copy=-1
replaced row length=1
jagged after replacement=[[1], [], [6, 7, 8]]
~~~

**Đối chiếu:**

1. `rectangle`: hai hàng, mỗi hàng ba phần tử; tổng `1+3+5=9`; `isRectangular` true.
2. `jagged`: ba hàng lần lượt dài 2, 0, 3; tổng `4+5+6+7+8=30`; không chữ nhật.
3. `validCell` kiểm tra cả chỉ số hàng, hàng null, chỉ số cột và độ dài hàng.
4. `new int[0][3]` tạo **0 hàng**; `new int[2][0]` tạo **2 hàng rỗng**.
5. `partial` có một hàng null và một hàng dài 1; hàm `sum` trong ví dụ chủ động bỏ qua hàng null.
6. Truy cập `jagged[1][0]` gây vượt biên vì hàng 1 rỗng; `partial[0].length` gây null-pointer vì hàng 0 null.
7. `rectangle.clone()` sao chép mảng ngoài nhưng chia sẻ các hàng: sửa `shallow[0][0]` làm `rectangle[0][0]` thành 99.
8. `copyRows` tạo các hàng `int[]` riêng; sửa bản sao thành -1 không làm mảng gốc đổi.
9. Thay `jagged[0]` bằng mảng dài 1 vẫn hợp lệ vì đây là phép gán **tham chiếu hàng**.

## 10. Lỗi thường gặp và cách sửa

| Sai lầm | Hậu quả | Cách đúng |
|---|---|---|
| Nghĩ Java có kiểu ma trận chữ nhật riêng | Hiểu sai cấu trúc `int[][]` | Mảng ngoài chứa các mảng con |
| `new int[3][]` rồi truy cập `a[0][0]` | `NullPointerException` vì chưa tạo hàng | Gán `a[0] = new int[...]` trước |
| Dùng `matrix[0].length` cho mọi hàng | Vượt biên hoặc bỏ sót phần tử | Dùng `matrix[row].length` |
| Nghĩ `matrix.length` là tổng số ô | Tính sai kích thước | `matrix.length` chỉ là số hàng |
| Dùng `matrix[row].length` trên hàng null | `NullPointerException` | Kiểm tra hàng null |
| Dùng `<= length` khi duyệt | `ArrayIndexOutOfBoundsException` | Dùng `< length` |
| Cho rằng `new int[0][3]` có thể truy cập `[0]` | Mảng ngoài không có hàng | Kiểm tra `matrix.length > 0` |
| Cho rằng `new int[2][0]` có các ô cột | Hàng có độ dài 0 | Không truy cập `[row][0]` |
| Nghĩ từng `length` có thể sửa | Trường mảng là final | Thay cả tham chiếu hàng bằng mảng mới |
| Nghĩ `matrix.clone()` sao chép mọi hàng | Hai cấu trúc vẫn chia sẻ mảng con | `Arrays.copyOf` từng hàng |
| Giả định không có hàng null chỉ vì kiểu là `int[][]` | Crash ở `.length` | Xác lập hợp đồng dữ liệu và kiểm tra |
| Cho rằng các hàng có cùng kích thước mãi mãi | Thay hàng làm mất hình chữ nhật | Kiểm tra/duy trì bất biến kích thước |

## 11. Bài tập có đáp án

### A — phân biệt chữ nhật và răng cưa

~~~java
int[][] a = new int[2][3];
int[][] b = {{1}, {2, 3}};
~~~

**Đáp án:** `a` ban đầu là ma trận chữ nhật 2×3; `b` là mảng răng cưa có hai hàng dài 1 và 2. Cả hai đều có kiểu `int[][]`.

### B — mảng ngoài đã tạo, nhưng hàng chưa tạo

~~~java
int[][] a = new int[2][];
// System.out.println(a[0].length);
~~~

**Đáp án:** `a.length == 2`, nhưng `a[0] == null`. Nếu bật dòng truy cập `.length`, chương trình ném `NullPointerException`. Sửa:

~~~java
a[0] = new int[3];
System.out.println(a[0].length);
~~~

**Exact stdout:**

~~~text
3
~~~

### C — tổng các hàng khác độ dài

~~~java
int[][] a = {{1, 2}, {}, {3, 4}};
int total = 0;
for (int[] row : a) {
    for (int value : row) {
        total += value;
    }
}
System.out.println(total);
~~~

**Đáp án:** in `10`. Hàng rỗng có 0 lượt duyệt.

### D — truy cập chỉ số

~~~java
int[][] a = {{5}, {6, 7}};
System.out.println(a[1][1]);
// System.out.println(a[0][1]);
~~~

**Đáp án:** in `7`; `a[0][1]` vượt biên vì hàng đầu chỉ dài 1.

### E — sao chép mảng hai chiều

~~~java
int[][] a = {{1}, {2}};
int[][] b = a.clone();
b[0][0] = 9;
System.out.println(a[0][0]);
~~~

**Đáp án:** in `9`, vì bản clone chỉ sao chép mảng ngoài, còn hàng `a[0]` và `b[0]` cùng tham chiếu mảng con.

### F — thay hàng làm đổi hình dạng

~~~java
int[][] a = new int[2][2];
a[1] = new int[4];
System.out.println(a[0].length + "," + a[1].length);
~~~

**Đáp án:** in `2,4`. Java cho phép thay một hàng bằng mảng có độ dài khác.

## 12. Checklist và kết luận

- [ ] Biết `int[][]` là mảng chứa các mảng `int[]`.
- [ ] Tạo ma trận chữ nhật bằng `new int[rows][cols]`.
- [ ] Tạo mảng răng cưa bằng `new int[rows][]` rồi tạo từng hàng.
- [ ] Phân biệt hàng `null` với hàng rỗng `new int[0]`.
- [ ] Đọc số hàng bằng `matrix.length`, số cột thực tế từng hàng bằng `matrix[row].length`.
- [ ] Duyệt đúng cả hàng dài ngắn khác nhau, không mặc định số cột chung.
- [ ] Kiểm tra đủ biên hàng/cột và trạng thái null trước khi truy cập.
- [ ] Hiểu `new int[0][3]` và `new int[2][0]` là các cấu trúc khác nhau.
- [ ] Biết thay tham chiếu hàng có thể làm dữ liệu không còn chữ nhật.
- [ ] Phân biệt sao chép một cấp `clone()` với sao chép từng hàng `Arrays.copyOf`.
- [ ] Biết cách xác minh hình chữ nhật khi bài toán yêu cầu các hàng cùng độ dài.
- [ ] Biên dịch và chạy `RectangularJaggedArrayDemo.java` trên Java 17+ và đối chiếu stdout.

**Kết luận:** Trong Java 17+, `int[][]` **không bắt buộc là một khối chữ nhật**; mỗi hàng là một mảng riêng có độ dài và tham chiếu riêng. `new int[r][c]` tạo các hàng ban đầu cùng dài, còn `new int[r][]` cho phép cấp phát từng hàng khác nhau. Muốn duyệt đúng và an toàn, hãy luôn dựa vào **`matrix.length` cho số hàng, `matrix[row].length` cho độ dài hàng hiện tại**, đồng thời xử lý hàng rỗng/null theo hợp đồng dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multidimensional-arrays-003">Khởi tạo từng mảng con và hàng chưa được tạo</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo từng mảng con và hàng chưa được tạo trong Java

> **Java core — language-basics / Mảng nhiều chiều**  
> **Môi trường:** JDK 17+, không dùng preview hoặc thư viện ngoài.  
> **Mục tiêu:** Phân biệt việc tạo **mảng ngoài** với việc tạo **mỗi hàng**, xử lý hàng còn `null`, cấp phát các hàng có độ dài khác nhau, và duyệt an toàn một mảng hai chiều không đều.

## 1. Mảng hai chiều là mảng chứa các tham chiếu tới mảng con

Trong Java, `int[][]` không phải một khối hình chữ nhật bắt buộc. Nó là **mảng ngoài có kiểu thành phần `int[]`**: mỗi phần tử ở mảng ngoài lưu một tham chiếu tới một mảng `int[]`, hoặc `null`.

~~~java
int[][] rows = new int[3][];
System.out.println(rows.length); // 3: mảng ngoài có ba vị trí.
System.out.println(java.util.Arrays.deepToString(rows));
// [null, null, null]
~~~

`new int[3][]` chỉ tạo **mảng ngoài** có ba vị trí. Vì kiểu thành phần của nó là `int[]` (kiểu tham chiếu), cả ba vị trí mặc định đều `null`: **chưa có hàng nào được tạo**.

Điều này khác với `new int[3][2]`: khi có kích thước chiều thứ hai, Java tạo luôn ba mảng con `int[2]` và khởi tạo các phần tử số của mỗi hàng bằng 0.

| Biểu thức tạo mảng | Mảng ngoài | Trạng thái hàng ngay sau tạo |
| --- | --- | --- |
| `new int[3][]` | 3 vị trí | `null`, `null`, `null` |
| `new int[3][2]` | 3 vị trí | Mỗi hàng là `int[2]`, chứa `[0, 0]` |
| `new int[0][]` | 0 vị trí | Không có hàng nào để truy cập |
| `new int[][] {{1, 2}, null, {}}` | 3 vị trí | Hàng dài 2, hàng null, hàng rỗng |

Vì hàng là mảng riêng, Java cho phép **các hàng dài khác nhau** (*ragged/jagged array*).

## 2. Cấp phát từng mảng con sau khi tạo mảng ngoài

~~~java
int[][] data = new int[4][];

data[0] = new int[2];           // [0, 0]
data[1] = new int[] {3, 4, 5};  // [3, 4, 5]
data[2] = new int[0];           // []
data[3] = new int[1];           // [0]

System.out.println(java.util.Arrays.deepToString(data));
// [[0, 0], [3, 4, 5], [], [0]]
~~~

Ở trên, `data[0]` và `data[3]` mới được cấp phát với `new int[n]` nên có giá trị mặc định 0. `data[1]` dùng danh sách phần tử cụ thể. `data[2]` là **một đối tượng mảng rỗng hợp lệ**, không phải `null`.

Có thể gán hàng bằng cú pháp ngắn `{...}` **khi khởi tạo trực tiếp biến mảng**, nhưng **không** viết `data[1] = {3,4,5};`: đây là phép gán cho vị trí của mảng đã tồn tại, nên cần `new int[] {3,4,5}`.

Số hàng của mảng ngoài được cố định lúc tạo (`data.length == 4`), nhưng có thể **gán lại các tham chiếu hàng** trong từng vị trí, hoặc dùng mảng ngoài mới nếu cần số hàng khác.

## 3. Hàng `null` khác hàng rỗng `new int[0]`

~~~java
int[][] sample = new int[2][];
sample[1] = new int[0];

System.out.println(sample[0] == null); // true: chưa tạo hàng.
System.out.println(sample[1] == null); // false: có hàng rỗng.
System.out.println(sample[1].length);  // 0.
~~~

| Biểu thức | Hàng chưa tạo `sample[0]` | Hàng rỗng `sample[1]` |
| --- | --- | --- |
| Kiểm tra `row == null` | true | false |
| `row.length` | `NullPointerException` | 0 |
| `row[0]` | `NullPointerException` | `ArrayIndexOutOfBoundsException` |
| Duyệt `for (int x : row)` | `NullPointerException` | 0 lượt |
| `Arrays.toString(row)` | `"null"` | `"[]"` |

`sample.length` vẫn là 2 vì mảng ngoài có hai vị trí; **không phản ánh số hàng đã cấp phát**. Chỉ số của mảng ngoài phải hợp lệ trước khi kiểm tra trạng thái của hàng: `sample[2]` vẫn vượt chỉ số.

Nếu cần phân biệt “chưa tải dữ liệu hàng” và “đã tải nhưng không có phần tử”, hãy giữ khác biệt `null`/rỗng. Nếu quy ước nghiệp vụ không cho phép hàng null, hãy **khởi tạo đủ hàng** hoặc kiểm tra rõ ràng tại ranh giới đầu vào.

## 4. Cấp phát trong vòng lặp và mảng không đều

Có thể tạo các hàng theo quy tắc:

~~~java
int[][] triangle = new int[4][];

for (int row = 0; row < triangle.length; row++) {
    triangle[row] = new int[row + 1];
    for (int col = 0; col < triangle[row].length; col++) {
        triangle[row][col] = row + col;
    }
}
// [[0], [1, 2], [2, 3, 4], [3, 4, 5, 6]]
~~~

Mỗi lần gán `triangle[row] = new int[row + 1]` tạo **một mảng con riêng**. Vòng trong dùng `triangle[row].length` thay vì lấy độ dài của một hàng cố định: với mảng không đều, số cột hợp lệ có thể khác nhau theo hàng.

Mảng hai chiều thông thường vẫn có thể tạo gọn:

~~~java
int[][] rectangular = new int[2][3];
// [[0, 0, 0], [0, 0, 0]]
~~~

Với kiểu mảng nhiều chiều hơn, Java cũng có thể chỉ cấp phát một số chiều đầu; các chiều chưa cấp phát ở cấp sâu hơn chứa null. Chỉ định kích thước sau một chiều đã bỏ trống là không hợp lệ: `new int[][3]` **không biên dịch**.

## 5. Duyệt an toàn khi có hàng chưa tạo

Một vòng lặp nested `for-each` đơn giản có thể ném NPE nếu gặp hàng null:

~~~java
// Không an toàn nếu data có hàng null:
// for (int[] row : data) {
//     for (int number : row) {
//         System.out.println(number);
//     }
// }
~~~

Hãy kiểm tra trước khi duyệt mảng con:

~~~java
static long sumNonNullRows(int[][] values) {
    if (values == null) {
        return 0; // Chỉ khi hợp đồng cho phép coi outer null như rỗng.
    }

    long sum = 0;
    for (int[] row : values) {
        if (row == null) {
            continue; // Hàng chưa tạo.
        }
        for (int number : row) {
            sum += number;
        }
    }
    return sum;
}
~~~

Nếu cần **đếm số phần tử**, chỉ tăng bộ đếm khi thực sự duyệt một giá trị `int`, không dùng `values.length` như số phần tử. Với một mảng không đều, số phần tử là **tổng `row.length` của các hàng khác null**, không phải “số hàng × số cột”.

Một chính sách khác là ném `IllegalArgumentException` khi gặp hàng null, nhằm bảo đảm dữ liệu phải đầy đủ. Chọn và ghi rõ **một hợp đồng**; đừng âm thầm bỏ qua hàng thiếu nếu bài toán coi đó là lỗi.

## 6. Phân biệt sửa ô với gán lại tham chiếu hàng

Sau khi đã cấp phát một hàng, `data[0][1] = 9` cập nhật phần tử của hàng đang tồn tại; `data[0] = new int[]{9}` thay cả tham chiếu ở mảng ngoài. Nếu biến khác đang giữ hàng cũ, biến ấy vẫn thấy đối tượng cũ. Tương tự, `data.clone()` chỉ sao chép **mảng ngoài**, nên hai bản vẫn có thể dùng chung các hàng. Bài **Sao chép nông và chia sẻ các mảng con** đi sâu vào các cách tạo bản sao độc lập; phần demo dưới đây cũng cung cấp một hàm sao chép từng hàng có kiểm tra `null` để người đọc tự chạy thử.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **PartialRowsDemo.java**:

~~~java
import java.util.Arrays;

public final class PartialRowsDemo {
    private static long sumNonNullRows(int[][] values) {
        if (values == null) {
            return 0;
        }
        long sum = 0;
        for (int[] row : values) {
            if (row == null) {
                continue;
            }
            for (int number : row) {
                sum += number;
            }
        }
        return sum;
    }

    private static int countElements(int[][] values) {
        if (values == null) {
            return 0;
        }
        int count = 0;
        for (int[] row : values) {
            if (row != null) {
                count += row.length;
            }
        }
        return count;
    }

    private static int[][] deepCopy(int[][] source) {
        if (source == null) {
            return null;
        }
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    public static void main(String[] args) {
        int[][] data = new int[4][];
        System.out.println("outerLength=" + data.length);
        System.out.println("initial=" + Arrays.deepToString(data));
        System.out.println("row1Null=" + (data[1] == null));

        data[0] = new int[2];
        data[1] = new int[] {3, 4, 5};
        data[2] = new int[0];
        data[3] = new int[1];
        System.out.println("allocated=" + Arrays.deepToString(data));
        System.out.println("row0Length=" + data[0].length);
        System.out.println("emptyRowLength=" + data[2].length);
        System.out.println("emptyRowIsNull=" + (data[2] == null));

        data[0][1] = 9;
        data[3][0] = 7;
        System.out.println("edited=" + Arrays.deepToString(data));

        int[] savedRow = data[1];
        data[1][0] = 8;
        System.out.println("savedAfterMutation=" + Arrays.toString(savedRow));

        data[1] = new int[] {6};
        System.out.println("afterReplace=" + Arrays.deepToString(data));
        System.out.println("oldSavedRow=" + Arrays.toString(savedRow));
        System.out.println("sum=" + sumNonNullRows(data));
        System.out.println("elementCount=" + countElements(data));

        int[][] copy = deepCopy(data);
        copy[0][0] = 99;
        System.out.println("afterCopySource=" + Arrays.deepToString(data));
        System.out.println("afterCopyNew=" + Arrays.deepToString(copy));

        int[][] rectangular = new int[2][3];
        System.out.println("rectangular=" + Arrays.deepToString(rectangular));

        int[][] incomplete = new int[1][];
        try {
            System.out.println(incomplete[0].length);
        } catch (NullPointerException ex) {
            System.out.println("uncreatedRow=NullPointerException");
        }
        try {
            System.out.println(data[2][0]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("emptyRow=ArrayIndexOutOfBoundsException");
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 PartialRowsDemo.java
java PartialRowsDemo
~~~

**Expected stdout chính xác:**

~~~text
outerLength=4
initial=[null, null, null, null]
row1Null=true
allocated=[[0, 0], [3, 4, 5], [], [0]]
row0Length=2
emptyRowLength=0
emptyRowIsNull=false
edited=[[0, 9], [3, 4, 5], [], [7]]
savedAfterMutation=[8, 4, 5]
afterReplace=[[0, 9], [6], [], [7]]
oldSavedRow=[8, 4, 5]
sum=22
elementCount=4
afterCopySource=[[0, 9], [6], [], [7]]
afterCopyNew=[[99, 9], [6], [], [7]]
rectangular=[[0, 0, 0], [0, 0, 0]]
uncreatedRow=NullPointerException
emptyRow=ArrayIndexOutOfBoundsException
~~~

`initial` thể hiện chỉ tạo mảng ngoài; `allocated` thể hiện cấp phát từng hàng và độ dài không đều. `uncreatedRow` khác `emptyRow`: một bên là null, bên kia là đối tượng dài 0. `afterCopySource` và `afterCopyNew` cho thấy sao chép từng hàng tạo dữ liệu số độc lập.

## 8. Lỗi thường gặp và bài tập

| Lỗi / hiểu lầm | Cách xử lý |
| --- | --- |
| `new int[3][]` tạo sẵn ba hàng `int[]` | Sai: ban đầu cả ba tham chiếu hàng đều null |
| `matrix[i].length` khi `matrix[i] == null` | Guard hoặc khởi tạo hàng trước; nếu không sẽ NPE |
| Cho rằng `new int[0]` là null | Sai: đó là một đối tượng mảng rỗng |
| Lấy độ dài một hàng dùng cho mọi hàng | Dùng `matrix[i].length`, vì mỗi hàng có thể dài khác nhau |
| `matrix[i] = {1, 2};` sau khi tạo outer | Dùng `matrix[i] = new int[]{1, 2};` |
| `new int[][2]` | Sai: không được bỏ kích thước chiều trước rồi khai báo kích thước chiều sau |
| `matrix.clone()` sao chép luôn từng hàng | Chỉ sao chép outer; muốn độc lập thì clone từng hàng |
| Duyệt tất cả hàng mà không kiểm tra null | Có thể NPE; xử lý theo hợp đồng dữ liệu |

**Bài 1.** Tạo `int[][] rows = new int[3][];`, in `Arrays.deepToString(rows)`, sau đó cấp phát lần lượt các hàng có độ dài 2, 0 và 4.

**Bài 2.** Phân biệt đầu ra khi truy cập `rows[1].length` lúc hàng chưa cấp phát và sau khi gán `rows[1] = new int[0]`.

**Bài 3.** Dùng hai vòng `for` để tạo mảng tam giác bốn hàng, trong đó hàng `i` có độ dài `i + 1` và phần tử `[i][j] = i + j`.

**Bài 4.** Viết hàm đếm số phần tử thật của mảng không đều có thể chứa hàng null; thử với `{{1, 2}, null, {}, {3}}`.

**Bài 5.** So sánh `Arrays.copyOf(matrix, matrix.length)` với việc sao chép từng hàng `matrix[i].clone()`. Khi sửa một ô số ở bản sao, bản gốc có đổi không?

### Ghi nhớ

**`new int[rows][]` chỉ tạo mảng ngoài; các hàng ban đầu là `null` cho tới khi được gán mảng con.** Mỗi hàng có thể có độ dài riêng, kể cả 0. **Hàng null không phải hàng rỗng**: phải cấp phát hoặc guard trước khi dùng `length`, truy cập chỉ số hay duyệt. Sao chép mảng ngoài không tự sao chép các hàng; muốn dữ liệu độc lập, sao chép từng mảng con.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multidimensional-arrays-004">Lấy số hàng và độ dài của từng hàng</a>

<details>
<summary>Xem chi tiết</summary>

# Lấy số hàng và độ dài của từng hàng trong mảng nhiều chiều Java

## 1. Mảng hai chiều thực chất là mảng chứa các mảng

Trong Java, `int[][]` là **mảng của các mảng `int[]`**. Mảng ngoài lưu tham chiếu đến từng mảng con (từng hàng). Vì vậy, các hàng **không bắt buộc có cùng số phần tử**.

```java
int[][] data = {
    {10, 20, 30}, // hàng 0: 3 phần tử
    {40},         // hàng 1: 1 phần tử
    {},           // hàng 2: 0 phần tử
    {50, 60}      // hàng 3: 2 phần tử
};
```

Có hai phép lấy độ dài khác nhau:

- **`data.length`** là số phần tử của mảng ngoài, tức **số hàng**: ở ví dụ trên là `4`.
- **`data[i].length`** là số phần tử của mảng con ở hàng `i`: lần lượt là `3`, `1`, `0`, `2`.
- Chỉ số hàng hợp lệ: `0 <= i && i < data.length`. Chỉ số cột của hàng `i` hợp lệ: `0 <= j && j < data[i].length`, nếu `data[i] != null`.

`length` là **trường** của mảng, không viết `length()`. Không có thuộc tính `data.columns` hay một “số cột” cố định áp dụng cho tất cả mảng hai chiều Java.

## 2. Phân biệt mảng chữ nhật và mảng răng cưa

| Cách tạo | `length` của mảng ngoài | Độ dài từng hàng |
| --- | ---: | --- |
| `new int[2][3]` | `2` | `3, 3` |
| `new int[3][0]` | `3` | `0, 0, 0` |
| `new int[0][3]` | `0` | Không có hàng để truy cập |
| `new int[][] {{1, 2}, {3}, {}}` | `3` | `2, 1, 0` |
| `new int[3][]` | `3` | Ba hàng ban đầu đều `null`; chưa thể gọi `.length` trên từng hàng |

**Mảng chữ nhật** có tất cả hàng khác `null` và cùng độ dài, ví dụ `new int[2][3]`. **Mảng răng cưa** (jagged/ragged array) chứa các hàng có độ dài khác nhau. Trong Java, ngay cả những hàng ban đầu cùng kích thước cũng có thể được gán lại bằng các mảng con khác độ dài:

```java
int[][] grid = new int[2][3];
grid[1] = new int[5];
// grid.length == 2
// grid[0].length == 3
// grid[1].length == 5
```

Phép gán trên **thay mảng con ở hàng 1**, không làm thay đổi `length` của mảng con ban đầu. Độ dài mỗi đối tượng mảng là cố định sau khi cấp phát; số hàng cũng cố định nếu vẫn dùng cùng mảng ngoài.

## 3. Ví dụ Java 17+ có thể biên dịch và chạy

Tạo tệp **`RowLengthsDemo.java`**:

```java
public class RowLengthsDemo {
    public static void main(String[] args) {
        int[][] data = {
            {10, 20, 30},
            {40},
            {},
            {50, 60}
        };

        System.out.println("So hang: " + data.length);

        int total = 0;
        for (int i = 0; i < data.length; i++) {
            int count = data[i].length;
            System.out.println("Hang " + i + ": " + count + " phan tu");
            total += count;
        }
        System.out.println("Tong so phan tu: " + total);

        // Mảng chữ nhật: mỗi hàng có độ dài 3.
        int[][] rectangle = new int[2][3];
        System.out.println("So hang chu nhat: " + rectangle.length);
        for (int i = 0; i < rectangle.length; i++) {
            System.out.println("Chu nhat hang " + i + ": " + rectangle[i].length);
        }

        // Mảng ngoài đã tạo, nhưng từng hàng có thể chưa được tạo.
        int[][] pending = new int[3][];
        pending[0] = new int[2];
        pending[1] = new int[0];
        for (int i = 0; i < pending.length; i++) {
            if (pending[i] == null) {
                System.out.println("Chua tao hang " + i + ": null");
            } else {
                System.out.println("Chua tao hang " + i + ": " + pending[i].length);
            }
        }
    }
}
```

Mở terminal trong thư mục chứa tệp với JDK 17 trở lên. Chạy trên Windows (CMD/PowerShell) hoặc Linux/macOS:

```bash
javac -encoding UTF-8 RowLengthsDemo.java
java RowLengthsDemo
```

Kết quả stdout (lệnh `javac` thành công không in kết quả):

```text
So hang: 4
Hang 0: 3 phan tu
Hang 1: 1 phan tu
Hang 2: 0 phan tu
Hang 3: 2 phan tu
Tong so phan tu: 6
So hang chu nhat: 2
Chu nhat hang 0: 3
Chu nhat hang 1: 3
Chua tao hang 0: 2
Chua tao hang 1: 0
Chua tao hang 2: null
```

Vòng `for` đầu duyệt số hàng bằng `data.length`, sau đó lấy số phần tử tại **từng hàng** bằng `data[i].length`. Tổng số phần tử thực tế bằng tổng các độ dài hàng đã được cấp phát. `new int[0]` là hàng **rỗng nhưng tồn tại**, khác với hàng `null` là **chưa tham chiếu đến mảng con**.

## 4. Duyệt an toàn và tính toán dựa trên độ dài từng hàng

Không dùng `data[0].length` để duyệt tất cả hàng nếu chưa biết chắc đây là mảng chữ nhật, có hàng đầu tiên và hàng đó khác `null`. Với mảng răng cưa, cần lấy `data[i].length` ngay tại hàng `i`:

```java
int[][] values = {{1, 2, 3}, {}, {4, 5}};
for (int row = 0; row < values.length; row++) {
    for (int col = 0; col < values[row].length; col++) {
        System.out.println(values[row][col]);
    }
}
// In lần lượt: 1, 2, 3, 4, 5 (mỗi số trên một dòng)
```

Với dữ liệu có thể chứa hàng `null`, kiểm tra trước khi lấy độ dài:

```java
int total = 0;
int[][] values = {new int[2], null, new int[3]};
for (int[] row : values) {
    if (row != null) {
        total += row.length;
    }
}
// total == 5
```

Nếu chính mảng ngoài cũng có thể `null`, kiểm tra `values != null` trước khi đọc `values.length`. Việc chọn coi hàng `null` là “không có phần tử” chỉ là quy ước để tính tổng; về mặt dữ liệu, hàng `null` khác hàng rỗng.

Để kiểm tra các hàng có cùng kích thước (mảng chữ nhật), cần so sánh **tất cả hàng** với độ dài kỳ vọng, đồng thời loại trường hợp `null`:

```java
int[][] values = {{1, 2}, {3, 4}, {5, 6}};
boolean rectangular = true;
int width = values.length == 0 ? 0 : (
        values[0] == null ? -1 : values[0].length);

for (int[] row : values) {
    if (row == null || row.length != width) {
        rectangular = false;
        break;
    }
}
// rectangular == true; width == 2
```

Đối với mảng ngoài rỗng, ở đây quy ước `width = 0` và `rectangular = true` vì không có hàng nào vi phạm. Tùy yêu cầu nghiệp vụ, có thể chọn cách khác để xử lý trường hợp không có hàng.

## 5. Các lỗi dễ gặp

1. **Nhầm `a.length` với `a[0].length`:** biểu thức thứ nhất đếm số hàng; biểu thức thứ hai chỉ đếm phần tử của hàng 0, không phải số hàng.
2. **Giả định mọi hàng có cùng độ dài:** vòng `for (int j = 0; j < a[0].length; j++)` có thể bỏ sót phần tử ở hàng dài hoặc gây lỗi ở hàng ngắn. Dùng `a[i].length` khi duyệt hàng `i`.
3. **Không kiểm tra mảng ngoài rỗng:** với `new int[0][3]`, `a.length == 0`, nhưng `a[0]` gây `ArrayIndexOutOfBoundsException`.
4. **Không kiểm tra hàng `null`:** với `new int[3][]`, `a[0].length` gây `NullPointerException`; cần cấp phát hàng hoặc kiểm tra `null`.
5. **Lấy `length()` thay `length`:** trên mảng dùng thuộc tính `length`; `String` mới dùng phương thức `length()`.
6. **Nhầm độ dài với chỉ số cuối:** nếu hàng `i` có `k` phần tử và `k > 0` thì phần tử cuối là `a[i][k - 1]`, không phải `a[i][k]`.

**Ghi nhớ:** `a.length` là số hàng; `a[i].length` là độ dài riêng của hàng `i` (chỉ khi hàng tồn tại). Mọi thao tác duyệt và truy cập cần tôn trọng ranh giới của từng hàng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multidimensional-arrays-005">Duyệt bằng chỉ số hoặc enhanced for lồng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Duyệt bằng chỉ số hoặc enhanced `for` lồng nhau — Java 17+

## 1. Hiểu mảng hai chiều trước khi duyệt

Trong Java, mảng hai chiều như `int[][] matrix` là **mảng các tham chiếu tới mảng `int[]`**. Có thể hình dung mỗi mảng con là một hàng:

~~~java
int[][] matrix = {
    {3, 1, 4},
    {1, 5},
    {},
    {9}
};
~~~

~~~text
Hàng 0: [3, 1, 4]  -> length = 3
Hàng 1: [1, 5]     -> length = 2
Hàng 2: []         -> length = 0
Hàng 3: [9]        -> length = 1
~~~

- `matrix.length` là số **hàng**: ở đây là `4`.
- `matrix[i]` là một **mảng một chiều** `int[]`.
- `matrix[i].length` là số phần tử của **hàng i**, không nhất thiết bằng số phần tử hàng khác.
- `matrix[i][j]` là phần tử ở hàng `i`, cột/chỉ số trong hàng `j`.
- Chỉ số bắt đầu từ `0`; hàng `i` có chỉ số phần tử hợp lệ từ `0` đến `matrix[i].length - 1`.

Ví dụ trên là mảng **có các hàng dài khác nhau** (*jagged/ragged array*), một cấu trúc Java hoàn toàn hợp lệ. Vì vậy, khi duyệt phải xét `matrix[i].length`, không tự giả định mọi hàng dài bằng `matrix[0].length`.

## 2. Duyệt bằng hai vòng `for` có chỉ số

Dạng chuẩn khi cần biết vị trí phần tử:

~~~java
for (int i = 0; i < matrix.length; i++) {
    for (int j = 0; j < matrix[i].length; j++) {
        System.out.println("[" + i + "][" + j + "]="
                + matrix[i][j]);
    }
}
~~~

Vòng ngoài chọn **hàng i**. Vòng trong duyệt các phần tử **j của chính hàng i**. Với `matrix` ở mục 1, stdout là:

~~~text
[0][0]=3
[0][1]=1
[0][2]=4
[1][0]=1
[1][1]=5
[3][0]=9
~~~

Hàng 2 rỗng nên vòng trong không chạy; không có phần tử `matrix[2][0]` để in. Thứ tự lặp trên đây là **theo hàng** (*row-major traversal*): xử lý hết hàng hiện tại trước khi sang hàng kế.

Có thể đặt tên biến rõ nghĩa hơn:

~~~java
for (int rowIndex = 0; rowIndex < matrix.length; rowIndex++) {
    for (int colIndex = 0;
         colIndex < matrix[rowIndex].length;
         colIndex++) {
        int value = matrix[rowIndex][colIndex];
        // Sử dụng value và cả hai chỉ số khi cần.
    }
}
~~~

**Ưu điểm:** biết được tọa độ, có thể gán `matrix[i][j] = ...`, truy cập hàng xóm hoặc chỉ duyệt một khoảng chỉ số. **Nhược điểm:** cú pháp dài hơn và phải viết điều kiện biên cẩn thận.

## 3. Duyệt bằng enhanced `for` lồng nhau

Enhanced `for` (hay *for-each*) đưa ra lần lượt **giá trị phần tử**, không yêu cầu người viết chỉ số:

~~~java
for (int[] row : matrix) {
    for (int value : row) {
        System.out.println(value);
    }
}
~~~

**Exact stdout:**

~~~text
3
1
4
1
5
9
~~~

Cách đọc cú pháp:

- `for (int[] row : matrix)`: vì `matrix` có kiểu `int[][]`, mỗi phần tử `row` có kiểu **`int[]`**.
- `for (int value : row)`: vì `row` có kiểu `int[]`, mỗi `value` là một **`int`**.
- Hàng rỗng không sinh ra lượt lặp bên trong.
- Thứ tự vẫn là duyệt lần lượt từng hàng, rồi từng giá trị trong hàng.

Điểm mạnh là code ngắn, ít rủi ro viết sai điều kiện `i < length`/`j < length`, phù hợp cho **đọc, tính tổng, đếm, tìm kiếm tuyến tính** khi không cần tọa độ.

Đừng viết `for (int value : matrix)`: phần tử của `int[][]` là `int[]`, không phải `int`; đoạn này không biên dịch.

## 4. Chọn kiểu duyệt theo mục đích

| Nhu cầu | Nên dùng | Lý do |
|---|---|---|
| In, cộng hoặc đếm mọi giá trị | Enhanced `for` lồng nhau | Ngắn và rõ |
| In `(row, column)` của phần tử | Hai `for` có chỉ số | Có tọa độ trực tiếp |
| Gán lại phần tử kiểu nguyên thủy | `matrix[i][j] = ...` | Ghi vào đúng ô trong mảng |
| Thay cả mảng con ở hàng `i` | `matrix[i] = new int[]{...}` | Cần chỉ số hàng |
| Tính tổng từng hàng, trả `int[]` | Kết hợp vòng ngoài có chỉ số, vòng trong enhanced `for` | Cần vị trí kết quả, không cần chỉ số từng giá trị |
| So sánh hàng xóm `[i][j-1]`, `[i][j+1]` | `for` có chỉ số và kiểm tra biên | Cần truy cập có vị trí |
| Có hàng dài khác nhau | Cả hai dạng đều được | Luôn dùng độ dài của từng hàng |

Không có kiểu vòng lặp “tốt nhất” trong mọi trường hợp. Chọn cách diễn đạt đúng điều cần làm; hai dạng thường cho cùng thứ tự đọc nếu cùng duyệt từ đầu đến cuối.

## 5. Sửa giá trị: bẫy của biến lặp enhanced `for`

Xét mảng:

~~~java
int[][] data = {{1, 2}, {3}};
for (int[] row : data) {
    for (int value : row) {
        value = value * 10;
    }
}
System.out.println(java.util.Arrays.deepToString(data));
~~~

**Exact stdout:**

~~~text
[[1, 2], [3]]
~~~

`value` chỉ là **biến cục bộ** nhận bản sao giá trị `int` của từng phần tử. Gán lại `value` **không ghi ngược** vào `row[j]`.

Muốn thay các số nguyên trong mảng, hãy dùng chỉ số:

~~~java
for (int i = 0; i < data.length; i++) {
    for (int j = 0; j < data[i].length; j++) {
        data[i][j] *= 10;
    }
}
System.out.println(java.util.Arrays.deepToString(data));
~~~

**Exact stdout (tiếp theo ví dụ):**

~~~text
[[10, 20], [30]]
~~~

Nhưng biến `row` trong `for (int[] row : data)` **chứa tham chiếu tới mảng con đang được duyệt**, nên thao tác **sửa phần tử qua row** vẫn tác động mảng:

~~~java
for (int[] row : data) {
    if (row.length > 0) {
        row[0] = 0;
    }
}
System.out.println(java.util.Arrays.deepToString(data));
~~~

**Exact stdout (tiếp theo ví dụ):**

~~~text
[[0, 20], [0]]
~~~

Phân biệt:

- `value = 99;`: chỉ gán lại biến `int` cục bộ.
- `row[0] = 99;`: gán vào phần tử trong **mảng con hiện có**.
- `row = new int[]{99};`: chỉ gán lại biến tham chiếu `row` của vòng lặp; **không thay** `data[i]`.
- `data[i] = new int[]{99};`: thay tham chiếu mảng con thực tế ở hàng `i`.

## 6. Kết hợp hai cách duyệt: tổng, số phần tử và tổng mỗi hàng

Tính tổng/số phần tử bằng enhanced `for`:

~~~java
int sum = 0;
int count = 0;
for (int[] row : matrix) {
    for (int value : row) {
        sum += value;
        count++;
    }
}
System.out.println(sum);
System.out.println(count);
~~~

Với `matrix = {{3,1,4},{1,5},{},{9}}`, **exact stdout**:

~~~text
23
6
~~~

Hàng rỗng góp **0 giá trị** vào tổng và số lượng. Số phần tử của mảng có hàng khác độ dài phải tính `count` (hoặc tổng `row.length`), **không** lấy `matrix.length * matrix[0].length`.

Khi cần tổng **theo từng hàng** cùng thứ tự với hàng gốc, kết hợp vòng ngoài có chỉ số và vòng trong enhanced `for`:

~~~java
int[] rowSums = new int[matrix.length];
for (int i = 0; i < matrix.length; i++) {
    for (int value : matrix[i]) {
        rowSums[i] += value;
    }
}
System.out.println(java.util.Arrays.toString(rowSums));
~~~

**Exact stdout:**

~~~text
[8, 6, 0, 9]
~~~

Cả hai cách đều không sửa `matrix` khi chỉ đọc các phần tử.

## 7. Hàng rỗng, hàng `null` và mảng ngoài `null`

Java có thể tạo mảng hai chiều với một số mảng con **chưa được tạo**:

~~~java
int[][] partial = new int[3][];
partial[0] = new int[]{2, 3};
partial[1] = null;
partial[2] = new int[0];
~~~

`partial.length == 3`, nhưng:

| Hàng | Giá trị | Có thể duyệt phần tử ngay? |
|---|---|---|
| `partial[0]` | `[2,3]` | Có, 2 phần tử |
| `partial[1]` | `null` | **Không**; `.length` hoặc enhanced `for` trên hàng này ném `NullPointerException` |
| `partial[2]` | Mảng rỗng | Có, vòng trong chạy 0 lượt |

Cách duyệt phòng vệ khi dữ liệu có thể chứa hàng `null`:

~~~java
int total = 0;
if (partial != null) {
    for (int[] row : partial) {
        if (row == null) {
            continue;
        }
        for (int value : row) {
            total += value;
        }
    }
}
System.out.println(total);
~~~

**Exact stdout:**

~~~text
5
~~~

Kiểm tra `row == null` cần làm **trước** khi truy cập `row.length` hoặc duyệt `for (int value : row)`. Nếu ngay cả mảng ngoài có thể null, cũng phải kiểm tra `matrix != null` trước khi đọc `matrix.length` hoặc lặp qua nó.

Không phải chương trình nào cũng nên **bỏ qua null**: đôi khi null là lỗi dữ liệu và cần ném `IllegalArgumentException`. Chính sách xử lý do bài toán quyết định; điều quan trọng là **không nhầm null với mảng rỗng**.

## 8. In mảng nhiều chiều và lỗi thường gặp

~~~java
int[][] matrix = {{1, 2}, {3}};
System.out.println(java.util.Arrays.deepToString(matrix));
~~~

**Exact stdout:**

~~~text
[[1, 2], [3]]
~~~

`Arrays.deepToString(matrix)` hiển thị **các mảng con lồng nhau**. `Arrays.toString(matrix)` chỉ áp dụng trực tiếp lên **mảng ngoài**, nên có thể in dạng tham chiếu của các mảng con thay vì toàn bộ giá trị số; không dùng đầu ra ấy để so sánh dữ liệu.

Các bẫy cần tránh:

| Lỗi | Hậu quả | Cách sửa |
|---|---|---|
| `j < matrix.length` ở vòng trong | Số hàng không nhất thiết là số phần tử hàng `i` | `j < matrix[i].length` |
| `j < matrix[0].length` cho mọi hàng | Có thể vượt biên hoặc bỏ sót phần tử | Dùng độ dài của từng hàng |
| `j <= matrix[i].length` | Đụng chỉ số ngoài mảng | Dùng `<`, không dùng `<=` |
| Duyệt `for (int v : matrix)` | Sai kiểu: mỗi phần tử mảng ngoài là `int[]` | `for (int[] row : matrix)` |
| Gán `v = ...` và mong mảng đổi | Chỉ đổi biến vòng lặp | Dùng `matrix[i][j] = ...` |
| Không kiểm tra hàng `null` khi dữ liệu cho phép | `NullPointerException` | Kiểm tra hàng trước vòng trong |
| Dùng `matrix.length * matrix[0].length` để đếm mọi phần tử | Sai với hàng khác độ dài, lỗi nếu không có hàng | Cộng `row.length` hoặc đếm trong vòng lặp |
| In `Arrays.toString(matrix)` | Không cho dạng giá trị lồng nhau dễ đọc | `Arrays.deepToString(matrix)` |

## 9. Chương trình Java 17+ hoàn chỉnh

Tạo file đúng tên **`NestedArrayIterationDemo.java`**:

~~~java
import java.util.Arrays;

public final class NestedArrayIterationDemo {
    private NestedArrayIterationDemo() {}

    static void showIndexed(int[][] matrix) {
        for (int rowIndex = 0; rowIndex < matrix.length; rowIndex++) {
            int[] row = matrix[rowIndex];
            System.out.println("row " + rowIndex
                    + " length=" + row.length);

            for (int colIndex = 0; colIndex < row.length; colIndex++) {
                System.out.println("cell[" + rowIndex + "]["
                        + colIndex + "]=" + row[colIndex]);
            }
        }
    }

    static int[] sumAndCount(int[][] matrix) {
        int sum = 0;
        int count = 0;
        for (int[] row : matrix) {
            for (int value : row) {
                sum += value;
                count++;
            }
        }
        return new int[]{sum, count};
    }

    static int[] rowSums(int[][] matrix) {
        int[] sums = new int[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            for (int value : matrix[i]) {
                sums[i] += value;
            }
        }
        return sums;
    }

    static int[][] doubledCopy(int[][] matrix) {
        int[][] result = new int[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            result[i] = Arrays.copyOf(matrix[i], matrix[i].length);
            for (int j = 0; j < result[i].length; j++) {
                result[i][j] *= 2;
            }
        }
        return result;
    }

    static int sumSkippingNullRows(int[][] matrix) {
        if (matrix == null) return 0;
        int sum = 0;
        for (int[] row : matrix) {
            if (row == null) continue;
            for (int value : row) {
                sum += value;
            }
        }
        return sum;
    }

    public static void main(String[] args) {
        int[][] grid = {{3, 1, 4}, {1, 5}, {}, {9}};

        System.out.println("matrix=" + Arrays.deepToString(grid));
        System.out.println("rows=" + grid.length);
        showIndexed(grid);

        int[] stats = sumAndCount(grid);
        System.out.println("enhanced sum=" + stats[0]);
        System.out.println("enhanced count=" + stats[1]);
        System.out.println("row sums=" + Arrays.toString(rowSums(grid)));

        int[][] doubled = doubledCopy(grid);
        System.out.println("doubled=" + Arrays.deepToString(doubled));
        System.out.println("original=" + Arrays.deepToString(grid));

        for (int[] row : grid) {
            for (int value : row) {
                value += 100; // Không ghi vào phần tử int gốc.
            }
        }
        System.out.println("after local value change="
                + Arrays.deepToString(grid));

        grid[0][1] = 7; // Cần tọa độ thì dùng chỉ số.
        System.out.println("after indexed change="
                + Arrays.deepToString(grid));

        for (int[] row : grid) {
            if (row.length > 0) {
                row[0] *= 10; // Sửa qua tham chiếu mảng con.
            }
        }
        System.out.println("after row reference change="
                + Arrays.deepToString(grid));

        int[][] withNullRow = {{2, 3}, null, {}, {5}};
        System.out.println("nullable sum="
                + sumSkippingNullRows(withNullRow));
    }
}
~~~

**Yêu cầu:** JDK 17 hoặc mới hơn, không cần thư viện ngoài hay preview features. Chạy terminal trong thư mục chứa file.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 NestedArrayIterationDemo.java
java -cp . NestedArrayIterationDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\NestedArrayIterationDemo.java
java -cp . NestedArrayIterationDemo
~~~

### Exact expected stdout

~~~text
matrix=[[3, 1, 4], [1, 5], [], [9]]
rows=4
row 0 length=3
cell[0][0]=3
cell[0][1]=1
cell[0][2]=4
row 1 length=2
cell[1][0]=1
cell[1][1]=5
row 2 length=0
row 3 length=1
cell[3][0]=9
enhanced sum=23
enhanced count=6
row sums=[8, 6, 0, 9]
doubled=[[6, 2, 8], [2, 10], [], [18]]
original=[[3, 1, 4], [1, 5], [], [9]]
after local value change=[[3, 1, 4], [1, 5], [], [9]]
after indexed change=[[3, 7, 4], [1, 5], [], [9]]
after row reference change=[[30, 7, 4], [10, 5], [], [90]]
nullable sum=10
~~~

**Giải thích đầu ra:** có 4 hàng nhưng tổng cộng chỉ 6 phần tử; tổng `3+1+4+1+5+9=23`. Dùng enhanced `for` để gán lại biến `value` không sửa các số trong `grid`. Ghi `grid[0][1] = 7` đổi một vị trí; sửa `row[0]` thông qua biến hàng kiểu `int[]` cũng đổi dữ liệu của mảng. `withNullRow` được xử lý bằng cách bỏ qua hàng null, do đó `2+3+5=10`.

## 10. Luyện tập và đáp án

**A.** Với `int[][] a = {{2, 4}, {}, {6}}`, có bao nhiêu hàng và phần tử?  
**Đáp án:** `a.length == 3`; tổng số phần tử là `2+0+1=3`, không phải `3*2`.

**B.** Thay vòng trong có chỉ số bằng enhanced `for` khi chỉ cần in giá trị:

~~~java
for (int i = 0; i < a.length; i++) {
    for (int j = 0; j < a[i].length; j++) {
        System.out.println(a[i][j]);
    }
}
~~~

**Đáp án:**

~~~java
for (int[] row : a) {
    for (int value : row) {
        System.out.println(value);
    }
}
~~~

**C.** Vì sao `for (int value : a)` lỗi khi `a` có kiểu `int[][]`?  
**Đáp án:** phần tử của `a` có kiểu `int[]`; vòng ngoài phải khai báo `int[] row`.

**D.** Với `int[][] a = {{1}, {2,3}}`, đoạn `for(int[] row:a) for(int v:row) v=0;` có đưa mảng về 0 không?  
**Đáp án:** **không**; chỉ gán lại biến `v`, không sửa mảng. Cần `a[i][j]=0`.

**E.** Khi nào dùng `matrix[i][j]` thay vì enhanced `for`?  
**Đáp án:** khi cần **chỉ số**, cần sửa trực tiếp ô có tọa độ, hoặc cần truy cập hàng xóm và giới hạn một khoảng vị trí.

**F.** Nếu `matrix[1] == null`, có được viết `matrix[1].length` không?  
**Đáp án:** không; sẽ ném `NullPointerException` nếu thực thi. Phải kiểm tra `matrix[1] != null` hoặc xác định chính sách từ chối null.

## 11. Checklist và kết luận

- [ ] Hiểu `int[][]` là mảng chứa các mảng `int[]`.
- [ ] Biết số hàng là `matrix.length`, độ dài hàng i là `matrix[i].length`.
- [ ] Duyệt bằng chỉ số với `i < matrix.length`, `j < matrix[i].length`.
- [ ] Duyệt bằng enhanced `for` với `int[] row` ngoài, `int value` trong.
- [ ] Không giả định mọi hàng có cùng độ dài hoặc đều khác `null`.
- [ ] Phân biệt gán lại biến enhanced `for` với sửa phần tử qua chỉ số/tham chiếu hàng.
- [ ] Biết kết hợp outer index với inner enhanced `for` khi cần tổng mỗi hàng.
- [ ] Dùng `Arrays.deepToString` để in nội dung mảng lồng.
- [ ] Chạy `NestedArrayIterationDemo.java` trên JDK 17+ và so sánh stdout.

**Kết luận:** Hai vòng `for` có chỉ số phù hợp khi cần **tọa độ hoặc chỉnh sửa phần tử**, còn enhanced `for` lồng nhau phù hợp khi chỉ cần **duyệt giá trị**. Cả hai đều phải tôn trọng độ dài của **từng hàng** trong mảng Java nhiều chiều; kiểm tra `null` khi mảng con có thể chưa được tạo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multidimensional-arrays-006">Sao chép nông và chia sẻ các mảng con</a>

<details>
<summary>Xem chi tiết</summary>

# Sao chép nông và chia sẻ các mảng con — Java 17+

## 1. Bản chất

`int[][]` trong Java là **mảng ngoài chứa các tham chiếu tới mảng con `int[]`**. Vì vậy **sao chép mảng ngoài không đồng nghĩa sao chép các hàng**.

~~~java
int[][] a = {{1, 2}, {3, 4, 5}};
~~~

Ở đây `a.length == 2`, `a[0].length == 2`, `a[1].length == 3`. Mỗi hàng là một đối tượng mảng riêng. Một **tham chiếu hàng** có thể được lưu ở nhiều vị trí, thậm chí trong nhiều mảng ngoài khác nhau.

~~~text
a (mảng ngoài) ---> [tham chiếu 0 | tham chiếu 1]
                          |               |
                          v               v
                      [1, 2]         [3, 4, 5]
                       int[]             int[]
~~~

**Shallow copy — sao chép nông:** chỉ tạo bản sao của **một tầng mảng**, còn các phần tử tham chiếu ở tầng ấy vẫn trỏ đến các đối tượng cũ.

## 2. So sánh các cách sao chép

| Thao tác với `int[][] a` | Mảng ngoài mới? | Các hàng `int[]` mới? |
|---|---|---|
| `int[][] b = a;` | Không | Không |
| `int[][] b = a.clone();` | **Có** | **Không** |
| `Arrays.copyOf(a, a.length)` | **Có** | **Không** |
| `System.arraycopy(a, 0, b, 0, a.length)` với `b` là mảng ngoài mới | Đích có mảng ngoài riêng | **Không** |
| Tạo mảng ngoài mới rồi `clone()` từng hàng | **Có** | **Có**, với hàng khác null |

Gán `b = a` không tạo bản sao: `a == b` là `true`. Ngược lại, `a.clone()` tạo mảng ngoài khác (`a == b` là `false`), nhưng thường `a[0] == b[0]` vẫn là `true`.

## 3. `clone()` tạo mảng ngoài mới, nhưng chia sẻ hàng

~~~java
int[][] original = {{1, 2}, {3, 4}};
int[][] copy = original.clone();

copy[0][0] = 9;
System.out.println(java.util.Arrays.deepToString(original));
System.out.println(original == copy);
System.out.println(original[0] == copy[0]);
~~~

**Exact expected stdout:**

~~~text
[[9, 2], [3, 4]]
false
true
~~~

`copy[0][0] = 9` sửa **mảng con chung**. Cả hai mảng ngoài đều quan sát được giá trị mới.

Nhưng thay một **tham chiếu hàng** chỉ sửa mảng ngoài đang giữ nó:

~~~java
copy[1] = new int[]{8};
System.out.println(java.util.Arrays.deepToString(original));
System.out.println(java.util.Arrays.deepToString(copy));
~~~

**Exact expected stdout (tiếp theo):**

~~~text
[[9, 2], [3, 4]]
[[9, 2], [8]]
~~~

Phân biệt: `copy[row][col] = value` sửa **phần tử thuộc hàng**; `copy[row] = new int[]{...}` sửa **ô tham chiếu ở mảng ngoài**.

## 4. `Arrays.copyOf` và `System.arraycopy` cũng là sao chép nông ở tầng ngoài

~~~java
int[][] source = {{1, 2}, {3}};
int[][] copied = java.util.Arrays.copyOf(source, source.length);
int[][] target = new int[source.length][];
System.arraycopy(source, 0, target, 0, source.length);

System.out.println(source == copied);
System.out.println(source[0] == copied[0]);
System.out.println(source[0] == target[0]);
~~~

**Exact expected stdout:**

~~~text
false
true
true
~~~

Vì phần tử của `int[][]` là tham chiếu tới `int[]`, sao chép phần tử mảng ngoài chỉ sao chép **giá trị tham chiếu**, không tự gọi `clone()` trên các hàng.

Nếu `Arrays.copyOf(source, source.length + 1)`, ô hàng mới được thêm mặc định là `null`, không phải `new int[0]`.

## 5. Sao chép độc lập từng hàng `int[]`

Khi muốn sửa bản sao **không ảnh hưởng dữ liệu `int` trong mảng gốc**, cần tạo mảng ngoài và từng mảng con riêng:

~~~java
static int[][] detachRows(int[][] source) {
    if (source == null) return null;

    int[][] copy = new int[source.length][];
    for (int i = 0; i < source.length; i++) {
        copy[i] = source[i] == null ? null : source[i].clone();
    }
    return copy;
}
~~~

- Mỗi hàng không null được `clone()` thành `int[]` mới.
- Hàng rỗng vẫn là một `int[]` dài 0 **mới**.
- Hàng null tiếp tục là null, không gọi `.clone()` lên null.
- Mỗi hàng giữ độ dài riêng, nên cách này dùng được với **mảng răng cưa** (*jagged array*).

~~~java
int[][] a = {{1, 2}, {3}};
int[][] b = detachRows(a);
b[0][0] = 99;

System.out.println(java.util.Arrays.deepToString(a));
System.out.println(java.util.Arrays.deepToString(b));
~~~

**Exact expected stdout:**

~~~text
[[1, 2], [3]]
[[99, 2], [3]]
~~~

Với **`int[][]`**, cách này đủ để tách biệt các giá trị nguyên thủy. Với mảng chứa đối tượng có thể thay đổi trạng thái, sao chép từng hàng vẫn có thể chưa đủ.

## 6. Hai hàng có thể cùng tham chiếu một mảng con

~~~java
int[] shared = {1, 2};
int[][] a = {shared, shared};

a[0][0] = 9;
System.out.println(java.util.Arrays.deepToString(a));
System.out.println(a[0] == a[1]);
~~~

**Exact expected stdout:**

~~~text
[[9, 2], [9, 2]]
true
~~~

Đây là **chia sẻ ngay bên trong cùng một mảng ngoài**. Khi dùng `detachRows(a)`, mỗi vị trí sẽ tạo một hàng mới riêng, tức **phá quan hệ cùng tham chiếu** dù giá trị ở mỗi hàng vẫn được sao chép đúng.

Nếu bài toán cần một bản sao có các hàng độc lập với nguồn **nhưng vẫn giữ quan hệ alias giữa các vị trí**, cần thuật toán khác (ví dụ ánh xạ identity từ hàng gốc sang hàng đã sao chép). Không mặc định mọi “deep copy” đều bảo toàn cấu trúc chia sẻ đối tượng.

## 7. Kiểu đối tượng mutable: clone hàng vẫn chia sẻ phần tử

~~~java
StringBuilder[][] a = {
    {new StringBuilder("A")}
};
StringBuilder[][] b = {a[0].clone()};

b[0][0].append("!");
System.out.println(a[0][0]); // A!
~~~

Mặc dù `a[0] != b[0]`, hai ô `a[0][0]` và `b[0][0]` vẫn cùng tham chiếu đối tượng `StringBuilder`, nên `append` làm bản gốc thay đổi.

Ngược lại, `b[0][0] = new StringBuilder("B")` chỉ thay tham chiếu tại **ô của hàng mới**, không đổi `a[0][0]`.

| Kiểu dữ liệu | Muốn tách biệt trạng thái, cần sao chép đến đâu? |
|---|---|
| `int[][]` | Mảng ngoài + từng hàng `int[]` |
| `String[][]` | Mảng ngoài + từng hàng thường đủ, vì `String` bất biến |
| `StringBuilder[][]` | Mảng ngoài + từng hàng + **từng `StringBuilder` mutable** |
| `int[][][]` | Phải xét cả **ba tầng mảng** |

`clone()` không phải lệnh “sao chép sâu tất cả các object bên trong”.

## 8. So sánh identity và nội dung

~~~java
int[][] a = {{1, 2}, {3}};
int[][] b = detachRows(a);

System.out.println(a == b);
System.out.println(java.util.Arrays.equals(a, b));
System.out.println(java.util.Arrays.deepEquals(a, b));
System.out.println(java.util.Arrays.deepToString(b));
~~~

**Exact expected stdout:**

~~~text
false
false
true
[[1, 2], [3]]
~~~

- `==` so **cùng object mảng ngoài** hay không.
- `Arrays.equals(a,b)` so **phần tử tầng ngoài**; với `int[][]`, các phần tử là tham chiếu `int[]`, nên hai hàng mới khác identity thì không bằng nhau theo cách này.
- `Arrays.deepEquals(a,b)` so sánh **nội dung lồng nhau**.
- `Arrays.deepToString(a)` in nội dung các hàng dễ đọc.

**Nội dung bằng nhau không chứng minh hai cấu trúc độc lập**; phải xét tham chiếu chia sẻ theo từng tầng.

## 9. Chương trình Java 17 hoàn chỉnh

Lưu tên **`ShallowArrayDemo.java`**:

~~~java
import java.util.Arrays;

public final class ShallowArrayDemo {
    private ShallowArrayDemo() {}

    static int[][] detachRows(int[][] source) {
        if (source == null) return null;
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    public static void main(String[] args) {
        int[][] source = {{1, 2}, {3, 4, 5}};
        int[][] alias = source;
        int[][] shallow = source.clone();
        int[][] copied = Arrays.copyOf(source, source.length);
        int[][] arrayCopied = new int[source.length][];
        System.arraycopy(source, 0, arrayCopied, 0, source.length);

        System.out.println("alias outer=" + (source == alias));
        System.out.println("clone outer=" + (source == shallow));
        System.out.println("clone row0=" + (source[0] == shallow[0]));
        System.out.println("copyOf row1=" + (source[1] == copied[1]));
        System.out.println("arraycopy row0=" + (source[0] == arrayCopied[0]));

        shallow[0][0] = 9;
        System.out.println("source after shallow=" + Arrays.deepToString(source));

        copied[1] = new int[]{8};
        System.out.println("source row1=" + Arrays.toString(source[1]));
        System.out.println("copied row1=" + Arrays.toString(copied[1]));

        arrayCopied[1][1] = 7;
        System.out.println("source after arraycopy="
                + Arrays.deepToString(source));

        int[][] independent = detachRows(source);
        independent[0][0] = 100;
        System.out.println("original=" + Arrays.deepToString(source));
        System.out.println("independent=" + Arrays.deepToString(independent));
        System.out.println("independent shares row0="
                + (source[0] == independent[0]));
        System.out.println("deep equals changed="
                + Arrays.deepEquals(source, independent));
        System.out.println("deep equals fresh="
                + Arrays.deepEquals(source, detachRows(source)));

        int[][] ragged = {{4}, null, {}};
        int[][] raggedCopy = detachRows(ragged);
        System.out.println("ragged copy=" + Arrays.deepToString(raggedCopy));
        System.out.println("null preserved=" + (raggedCopy[1] == null));
        System.out.println("row detached=" + (ragged[0] != raggedCopy[0]));

        int[] same = {1, 2};
        int[][] repeated = {same, same};
        int[][] repeatedShallow = repeated.clone();
        int[][] repeatedDetached = detachRows(repeated);
        System.out.println("same source rows=" + (repeated[0] == repeated[1]));
        System.out.println("same shallow rows="
                + (repeatedShallow[0] == repeatedShallow[1]));
        System.out.println("same detached rows="
                + (repeatedDetached[0] == repeatedDetached[1]));

        repeated[0][0] = 6;
        System.out.println("repeated=" + Arrays.deepToString(repeated));
        System.out.println("repeated detached="
                + Arrays.deepToString(repeatedDetached));

        StringBuilder[][] objects = {{new StringBuilder("A")}};
        StringBuilder[][] rowCopy = {objects[0].clone()};
        rowCopy[0][0].append("!");
        System.out.println("mutable original=" + objects[0][0]);
        rowCopy[0][0] = new StringBuilder("B");
        System.out.println("mutable original after replace=" + objects[0][0]);
        System.out.println("mutable copy=" + rowCopy[0][0]);

        int[][][] cube = {{{1}}};
        int[][][] cubeCopy = cube.clone();
        System.out.println("3d level2 shared=" + (cube[0] == cubeCopy[0]));
    }
}
~~~

**Bash (Linux/macOS), JDK 17+:**

~~~bash
javac --release 17 -encoding UTF-8 ShallowArrayDemo.java
java -cp . ShallowArrayDemo
~~~

**PowerShell (Windows), JDK 17+:**

~~~powershell
javac --release 17 -encoding UTF-8 .\ShallowArrayDemo.java
java -cp . ShallowArrayDemo
~~~

### Exact expected stdout

~~~text
alias outer=true
clone outer=false
clone row0=true
copyOf row1=true
arraycopy row0=true
source after shallow=[[9, 2], [3, 4, 5]]
source row1=[3, 4, 5]
copied row1=[8]
source after arraycopy=[[9, 2], [3, 7, 5]]
original=[[9, 2], [3, 7, 5]]
independent=[[100, 2], [3, 7, 5]]
independent shares row0=false
deep equals changed=false
deep equals fresh=true
ragged copy=[[4], null, []]
null preserved=true
row detached=true
same source rows=true
same shallow rows=true
same detached rows=false
repeated=[[6, 2], [6, 2]]
repeated detached=[[1, 2], [1, 2]]
mutable original=A!
mutable original after replace=A!
mutable copy=B
3d level2 shared=true
~~~

**Giải thích:** `clone()`, `Arrays.copyOf` và `System.arraycopy` trên `int[][]` vẫn chia sẻ hàng; `detachRows` tạo hàng `int[]` mới. Thay phần tử của hàng chia sẻ tác động bản gốc, còn thay cả tham chiếu hàng trong mảng ngoài riêng thì không. Với `StringBuilder[][]`, sao chép hàng không sao chép các object mutable.

## 10. Các lỗi thường gặp

| Sai lầm | Vì sao sai? | Sửa |
|---|---|---|
| `b = a` là bản sao | Chỉ thêm tên tham chiếu | Dùng clone hoặc sao chép hàng tùy nhu cầu |
| `a.clone()` là deep copy | Chỉ sao chép mảng ngoài | Clone từng hàng `int[]` |
| `Arrays.copyOf(a, a.length)` sao chép mọi `int[]` | Chỉ sao chép các tham chiếu hàng | Tạo mảng con mới |
| `System.arraycopy` tự clone các object con | Chỉ sao chép phần tử của mảng nguồn | Sao chép sâu theo dữ liệu |
| `b[0][0]` và `b[0]` là cùng loại thao tác | Một lệnh sửa ô, một lệnh thay hàng | Xác định đúng tầng tham chiếu |
| `source[i].clone()` khi hàng null | Ném `NullPointerException` | Kiểm tra null trước |
| `Arrays.equals` đủ để so ma trận theo giá trị | Nó so phần tử tầng ngoài | Dùng `Arrays.deepEquals` |
| Chỉ kiểm tra `a != b` để khẳng định độc lập | Các hàng vẫn có thể chung | So identity từng cấp |
| Clone hàng `StringBuilder[]` sẽ sao chép từng StringBuilder | Các object vẫn dùng chung | Tạo object mới cho từng phần tử mutable |
| Hai hàng nguồn dùng chung một object nhưng bản sao hàng tự giữ alias | Clone từng hàng làm tách alias | Nếu cần giữ alias nội bộ, dùng ánh xạ identity |

## 11. Bài tập có đáp án

**A — Dự đoán kết quả:**

~~~java
int[][] a = {{1}, {2}};
int[][] b = a.clone();
b[0][0] = 7;
b[1] = new int[]{8};
System.out.println(Arrays.deepToString(a));
System.out.println(Arrays.deepToString(b));
~~~

**Đáp án:**

~~~text
[[7], [2]]
[[7], [8]]
~~~

**B — Vì sao `a == b` false nhưng sửa hàng vẫn thấy ở `a`?** Vì sao chép nông tạo mảng ngoài mới, còn `a[i] == b[i]` vẫn có thể true.

**C — Sao chép an toàn mảng răng cưa có hàng null:** dùng `detachRows` ở mục 5; `{{1}, null, {}}` được sao chép thành cấu trúc nội dung `[[1], null, []]` với các hàng không null độc lập.

**D — Muốn hai hàng cùng tham chiếu sau khi sao chép?** Không dùng trực tiếp hàm clone từng hàng độc lập; cần ánh xạ hàng gốc → hàng sao chép để tái sử dụng cùng hàng mới tại các vị trí tương ứng.

**E — Với `StringBuilder[][]` sao chép từng hàng đã đủ chưa?** Chưa nếu yêu cầu không chia sẻ trạng thái object. Cần tạo bản sao `StringBuilder` (hoặc object mutable tương ứng) trong từng ô.

## 12. Checklist và kết luận

- [ ] Hiểu `int[][]` chứa **tham chiếu hàng**.
- [ ] Phân biệt phép gán tham chiếu với bản sao nông.
- [ ] Biết `clone()`, `Arrays.copyOf`, `System.arraycopy` ở tầng ngoài vẫn chia sẻ mảng con.
- [ ] Hiểu `b[row][col] = x` khác `b[row] = new int[]{...}`.
- [ ] Sao chép được các hàng `int[]` độc lập, kể cả hàng rỗng và null.
- [ ] Nhận ra trường hợp nhiều hàng trỏ tới cùng một đối tượng `int[]`.
- [ ] Phân biệt mảng con và object mutable nằm trong phần tử mảng con.
- [ ] Dùng `Arrays.deepEquals` và `Arrays.deepToString` đúng mục đích.
- [ ] Chạy `ShallowArrayDemo.java` bằng Java 17+, đối chiếu stdout.

**Kết luận:** Sao chép nông của mảng nhiều chiều trong Java tạo **mảng ngoài mới nhưng vẫn chia sẻ các mảng con**. Thay nội dung hàng chung ảnh hưởng mọi tham chiếu tới hàng đó; thay tham chiếu hàng trong một mảng ngoài riêng thì không. Với `int[][]`, muốn độc lập dữ liệu hãy sao chép **cả mảng ngoài và từng `int[]` con**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multidimensional-arrays-007">Arrays.deepToString() và Arrays.deepEquals()</a>

<details>
<summary>Xem chi tiết</summary>

# `Arrays.deepToString()` và `Arrays.deepEquals()` trong Java

> **Java core — language-basics / Mảng nhiều chiều**  
> **Môi trường:** JDK 17+, chương trình một file, không cần thư viện ngoài.  
> **Mục tiêu:** Hiển thị và so sánh **nội dung lồng nhau** của mảng nhiều chiều; phân biệt `toString`/`equals` thông thường với hai hàm `deep...`, hiểu hàng `null`, mảng không đều và giới hạn của phép so sánh sâu.

## 1. Vì sao mảng nhiều chiều cần các hàm `deep...`?

Trong Java, mảng hai chiều thực chất là **mảng chứa tham chiếu đến các mảng con**:

~~~java
int[][] grid = {{1, 2}, {3}};
System.out.println(java.util.Arrays.deepToString(grid));
// [[1, 2], [3]]
~~~

Nếu gọi `Arrays.toString(grid)`, Java xử lý từng **phần tử của mảng ngoài** là một đối tượng `int[]`. Phần tử mảng con thường được biểu diễn bằng chuỗi kiểu `[I@...` chứa mã danh tính, **không phải** danh sách các số. Chuỗi danh tính đó phụ thuộc runtime và không nên dùng làm expected output.

`Arrays.deepToString(grid)` đi qua các cấp mảng và in nội dung bên trong. Tương tự, `Arrays.deepEquals(a, b)` so sánh nội dung mảng con thay vì chỉ so sánh tham chiếu của từng hàng.

| Nhu cầu | Mảng một chiều | Mảng nhiều chiều / lồng nhau |
| --- | --- | --- |
| In các phần tử | `Arrays.toString(array)` | `Arrays.deepToString(outer)` |
| So sánh phần tử | `Arrays.equals(a, b)` | `Arrays.deepEquals(a, b)` |
| So cùng đối tượng mảng? | `a == b` | `a == b` chỉ so tham chiếu outer |
| Tạo bản sao? | `Arrays.copyOf` / `clone` | Cần sao chép thêm từng hàng để độc lập |

**Quan trọng về kiểu:** `deepToString` và `deepEquals` nhận tham số kiểu `Object[]`. `int[][]` dùng được vì các phần tử của nó là `int[]` (tham chiếu); **`int[]` không phải `Object[]`**. Muốn in/so sánh `int[]` trực tiếp thì dùng `Arrays.toString(int[])` và `Arrays.equals(int[], int[])`.

## 2. `Arrays.deepToString()`: xem nội dung qua nhiều tầng

~~~java
import java.util.Arrays;

int[][] matrix = {{1, 2}, {3, 4}};
System.out.println(Arrays.deepToString(matrix));
// [[1, 2], [3, 4]]
~~~

`deepToString` tự nhận biết phần tử là một mảng và tiếp tục định dạng các phần tử con. Nó cũng xử lý **mảng răng cưa** (*ragged array*) — các hàng không bắt buộc dài bằng nhau:

~~~java
int[][] jagged = {{1, 2}, null, {}, {7}};
System.out.println(Arrays.deepToString(jagged));
// [[1, 2], null, [], [7]]
~~~

Ở đây `null` biểu thị **một hàng chưa có đối tượng mảng**, còn `[]` biểu thị **một hàng đã được tạo với độ dài 0**. Hai trạng thái này khác nhau và được hiển thị khác nhau.

Với mảng lồng nhiều tầng hơn, `deepToString` tiếp tục xuống các tầng đó. Nếu phần tử cuối là đối tượng thông thường (không phải mảng), cách biểu diễn phụ thuộc `String.valueOf(element)`/`toString()` của đối tượng; không phải mọi lớp đều có `toString()` biểu diễn giá trị nghiệp vụ.

## 3. `Arrays.deepEquals()`: so sánh từng hàng theo nội dung

Hai mảng có giá trị giống nhau nhưng các hàng được tạo **độc lập**:

~~~java
int[][] first = {{1, 2}, {3}};
int[][] second = {{1, 2}, {3}};

System.out.println(first == second);                   // false
System.out.println(Arrays.equals(first, second));      // false
System.out.println(Arrays.deepEquals(first, second));  // true
~~~

Vì sao?

1. `first == second` là false: hai **mảng ngoài** khác đối tượng.
2. `Arrays.equals(first, second)` so sánh các phần tử mảng ngoài. Mỗi phần tử là một `int[]`, và mảng con không override `equals` để so theo giá trị: các tham chiếu hàng khác nhau nên false.
3. `Arrays.deepEquals(first, second)` đi xuống từng hàng và so sánh từng giá trị số; cùng kích thước, thứ tự và nội dung nên true.

Nếu một hàng chứa phần tử khác, số hàng khác hoặc độ dài một hàng khác thì `deepEquals` trả false. Phép so sánh **có xét thứ tự**: `{{1,2},{3}}` không bằng `{{2,1},{3}}` theo `deepEquals`.

Hai mảng ngoài cùng tham chiếu sẽ so bằng nhau, nhưng đây không phải mục tiêu chính của deep comparison: nó hữu ích khi hai cấu trúc là **những đối tượng riêng biệt**.

## 4. Mảng lồng không đều, hàng `null` và mảng rỗng

~~~java
int[][] a = {{1, 2}, null, {}, {7}};
int[][] b = {{1, 2}, null, {}, {7}};
int[][] c = {{1, 2}, {}, null, {7}};

System.out.println(Arrays.deepEquals(a, b)); // true
System.out.println(Arrays.deepEquals(a, c)); // false
~~~

`a` và `b` cùng cấu trúc, cùng các hàng `null`/rỗng và cùng các phần tử. `c` đã đổi chỗ hai trạng thái nên không bằng `a`.

| Cấu trúc | `Arrays.deepToString(...)` |
| --- | --- |
| `new int[][] {{1}, {2, 3}}` | `"[[1], [2, 3]]"` |
| `new int[2][]` | `"[null, null]"` |
| `new int[][] {{}, {}}` | `"[[], []]"` |
| `new int[0][]` | `"[]"` |

`deepToString((Object[]) null)` trả chuỗi `"null"`. `deepEquals((Object[]) null, (Object[]) null)` trả true, còn so một outer null với một outer rỗng trả false. Cần phân biệt **outer null**, **outer có hàng null**, và **outer rỗng**.

Các kiểu primitive như `int[]`, `long[]` bên trong `Object[]` vẫn được xử lý theo nội dung; không cần boxing từng số thành `Integer`.

## 5. Mảng hỗn hợp và `equals` của phần tử là đối tượng

Có thể đặt nhiều loại mảng con cùng các đối tượng thông thường trong một `Object[]`:

~~~java
Object[] values = {
    "Java",
    new int[] {4, 5},
    new String[] {"A", "B"},
    null
};
System.out.println(Arrays.deepToString(values));
// [Java, [4, 5], [A, B], null]
~~~

`deepEquals` so sánh đệ quy nếu cả hai phần tử tương ứng là mảng. Nếu chúng là **đối tượng không phải mảng**, nó dựa vào quy tắc `equals` của đối tượng đó:

~~~java
Object[] textA = {new String("OK")};
Object[] textB = {"OK"};
System.out.println(Arrays.deepEquals(textA, textB)); // true

Object[] builderA = {new StringBuilder("OK")};
Object[] builderB = {new StringBuilder("OK")};
System.out.println(Arrays.deepEquals(builderA, builderB)); // false
~~~

`String` override `equals` theo nội dung; `StringBuilder` không override `equals` theo nội dung, nên hai builder riêng biệt có cùng chữ vẫn không bằng nhau. **Deep** trong `deepEquals` có nghĩa là **đi sâu vào phần tử nào thực sự là mảng**, không phải tự so sánh mọi thuộc tính bên trong bất kỳ đối tượng nào.

Hai mảng con khác loại (`int[]` và `long[]`) không trở thành bằng nhau chỉ vì cùng các giá trị số; quy tắc so sánh vẫn xét loại mảng phù hợp.

## 6. So sánh sâu KHÔNG phải sao chép sâu

`Arrays.deepEquals` trả kết quả `boolean` và **không sao chép bất kỳ đối tượng nào**. `Arrays.deepToString` tạo chuỗi hiển thị, cũng **không sao chép dữ liệu**.

Trong khi đó, sao chép mảng ngoài bằng `clone()`/`Arrays.copyOf` vẫn là **shallow copy**: các hàng con tiếp tục được chia sẻ.

~~~java
int[][] source = {{1, 2}, {3}};
int[][] shallow = source.clone();

shallow[0][0] = 9;
// source[0][0] == 9 vì hai outer vẫn trỏ cùng hàng 0.
~~~

Muốn sao chép độc lập tất cả các giá trị `int`, phải tạo hàng mới cho từng hàng tồn tại:

~~~java
static int[][] copyRows(int[][] source) {
    if (source == null) return null;
    int[][] copy = new int[source.length][];
    for (int i = 0; i < source.length; i++) {
        copy[i] = source[i] == null ? null : source[i].clone();
    }
    return copy;
}
~~~

Cách này giữ nguyên hàng null nhưng tạo **đối tượng hàng mới** cho mỗi hàng không null. Với mảng chứa các đối tượng mutable, ngay cả khi clone từng hàng, các đối tượng phần tử vẫn có thể được chia sẻ và cần chiến lược sao chép theo hợp đồng nghiệp vụ.

## 7. Lưu ý hiệu năng, mảng tự tham chiếu và phiên bản

- Cả hai hàm đều duyệt qua các phần tử cần hiển thị/so sánh; chi phí tăng theo tổng số phần tử lồng nhau. `deepEquals` có thể dừng sớm ở vị trí khác biệt đầu tiên, nhưng trường hợp cần so hết vẫn tốn thời gian theo số phần tử.
- Với mảng lồng nhau có **chu trình tham chiếu** như một `Object[]` chứa chính nó, `deepToString` có cơ chế đánh dấu vòng tham chiếu trong chuỗi hiển thị. Đừng thiết kế thuật toán nghiệp vụ phụ thuộc vào định dạng tham chiếu vòng; nên tránh cấu trúc vòng nếu không cần.
- `Arrays.deepEquals` **không nên dùng để so sánh các cấu trúc mảng tự tham chiếu theo chu trình**: quy tắc API không định nghĩa kết quả đáng tin cậy cho mảng chứa chính nó trực tiếp hoặc gián tiếp. Hãy xác định cách xử lý chu trình riêng nếu bài toán yêu cầu.
- Nếu mảng chứa phần tử là `double[]` hoặc `float[]`, `deepEquals` dùng quy tắc so sánh mảng primitive tương ứng của `Arrays.equals`, không đơn giản là phép `==` cho từng số. Nếu cần so số thực theo ngưỡng sai số, phải xây dựng kiểm tra riêng theo yêu cầu.

Cú pháp và các API trong bài hoạt động trên Java 17+ không cần cờ `--enable-preview`.

## 8. Chương trình Java 17+ chạy độc lập

Tạo file **DeepArraysDemo.java**:

~~~java
import java.util.Arrays;

public final class DeepArraysDemo {
    private static int[][] copyRows(int[][] source) {
        if (source == null) {
            return null;
        }
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    public static void main(String[] args) {
        int[][] first = {{1, 2}, {3}};
        int[][] second = {{1, 2}, {3}};

        System.out.println("outerSame=" + (first == second));
        System.out.println("shallowEqual=" + Arrays.equals(first, second));
        System.out.println("deepEqual=" + Arrays.deepEquals(first, second));
        System.out.println("deepText=" + Arrays.deepToString(first));
        System.out.println("flatText=" + Arrays.toString(first[0]));
        System.out.println("flatEqual=" + Arrays.equals(first[0], second[0]));

        int[][] jagged = {{1, 2}, null, {}, {7}};
        int[][] jaggedTwin = {{1, 2}, null, {}, {7}};
        System.out.println("jaggedText=" + Arrays.deepToString(jagged));
        System.out.println("jaggedEqual=" + Arrays.deepEquals(jagged, jaggedTwin));

        Object[] mixed = {
            "Java", new int[] {4, 5}, new String[] {"A", "B"}, null
        };
        Object[] mixedTwin = {
            "Java", new int[] {4, 5}, new String[] {"A", "B"}, null
        };
        System.out.println("mixedText=" + Arrays.deepToString(mixed));
        System.out.println("mixedEqual=" + Arrays.deepEquals(mixed, mixedTwin));

        Object[] stringsA = {new String("OK")};
        Object[] stringsB = {"OK"};
        System.out.println("stringEqual="
                + Arrays.deepEquals(stringsA, stringsB));

        Object[] buildersA = {new StringBuilder("OK")};
        Object[] buildersB = {new StringBuilder("OK")};
        System.out.println("builderEqual="
                + Arrays.deepEquals(buildersA, buildersB));

        Object[] absent = null;
        System.out.println("nullText=" + Arrays.deepToString(absent));
        System.out.println("nullEqualNull="
                + Arrays.deepEquals(absent, (Object[]) null));
        System.out.println("nullEqualEmpty="
                + Arrays.deepEquals(absent, new Object[0]));

        int[][] shallow = first.clone();
        shallow[0][0] = 9;
        System.out.println("outerCopySame=" + (first == shallow));
        System.out.println("sharedRow=" + (first[0] == shallow[0]));
        System.out.println("sourceAfterShallow=" + Arrays.deepToString(first));
        System.out.println("deepEqualAfterChange="
                + Arrays.deepEquals(first, second));

        int[][] independent = copyRows(first);
        independent[0][1] = 77;
        System.out.println("independentRowSame="
                + (first[0] == independent[0]));
        System.out.println("sourceAfterDeepCopy=" + Arrays.deepToString(first));
        System.out.println("newDeepCopy=" + Arrays.deepToString(independent));
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (PowerShell, Command Prompt hoặc Bash):

~~~bash
javac -encoding UTF-8 DeepArraysDemo.java
java DeepArraysDemo
~~~

**Expected stdout chính xác:**

~~~text
outerSame=false
shallowEqual=false
deepEqual=true
deepText=[[1, 2], [3]]
flatText=[1, 2]
flatEqual=true
jaggedText=[[1, 2], null, [], [7]]
jaggedEqual=true
mixedText=[Java, [4, 5], [A, B], null]
mixedEqual=true
stringEqual=true
builderEqual=false
nullText=null
nullEqualNull=true
nullEqualEmpty=false
outerCopySame=false
sharedRow=true
sourceAfterShallow=[[9, 2], [3]]
deepEqualAfterChange=false
independentRowSame=false
sourceAfterDeepCopy=[[9, 2], [3]]
newDeepCopy=[[9, 77], [3]]
~~~

Dòng `shallowEqual=false` nhưng `deepEqual=true` cho thấy hai outer độc lập vẫn có thể có nội dung sâu bằng nhau. Dòng `sharedRow=true` chứng minh bản clone outer dùng lại các hàng cũ, còn `independentRowSame=false` chứng minh `copyRows` đã tạo hàng riêng.

### Bài tập và checklist

**Bài 1.** Tạo hai `int[][]` có các hàng độc lập nhưng chứa cùng giá trị; dự đoán `==`, `Arrays.equals` và `Arrays.deepEquals`.

**Bài 2.** Với `{{1, 2}, null, {}}`, dự đoán đầu ra của `Arrays.deepToString` và giải thích khác biệt giữa hàng null và hàng rỗng.

**Bài 3.** Dùng `Object[]` chứa một `int[]`, một `String[]` và một `null`; so sánh bằng `deepEquals` với cấu trúc mới có cùng nội dung.

**Bài 4.** Gọi `Arrays.deepEquals` cho hai `Object[]` mỗi bên chứa một `StringBuilder("A")` riêng biệt. Vì sao kết quả không giống hai `String("A")`?

**Bài 5.** `clone()` mảng ngoài, sửa phần tử hàng 0 và quan sát bản gốc. Sau đó dùng `copyRows` để chứng minh bản sao hàng độc lập.

**Checklist:** Dữ liệu có thật sự là mảng lồng nhau không? Có hàng null hay độ dài không đều không? Cần so sánh **nội dung sâu** hay **cùng tham chiếu**? Phần tử có override `equals` không? Có chu trình tham chiếu hoặc nhu cầu sao chép sâu không?

### Ghi nhớ

**`Arrays.deepToString` hiển thị mảng lồng nhau; `Arrays.deepEquals` so sánh nội dung các mảng con.** `Arrays.toString` và `Arrays.equals` chỉ xử lý một tầng của mảng ngoài. Deep comparison **không phải** deep copy; sao chép mảng ngoài vẫn chia sẻ mảng con nếu không tạo bản sao từng hàng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multidimensional-arrays-008">Mảng tham chiếu hiệp biến và nguy cơ ArrayStoreException</a>

<details>
<summary>Xem chi tiết</summary>

# Mảng tham chiếu hiệp biến và nguy cơ `ArrayStoreException` trong Java

> **Java core — language-basics / Mảng nhiều chiều**  
> **Yêu cầu:** Java 17+, JDK, không cần thư viện ngoài.  
> **Mục tiêu:** Giải thích vì sao `String[]` gán được cho `Object[]`, tại sao phép gán một phần tử vẫn có thể thất bại khi chạy, cách kiểm tra kiểu phần tử thực của mảng, và vận dụng đúng với `String[][]`, `Object[][]` và `int[][]`.

## 1. Hiệp biến (*covariance*) của mảng tham chiếu

Giả sử `String` là lớp con của `Object`. Java cho phép coi **mảng tham chiếu** `String[]` như một `Object[]`:

~~~java
String[] names = {"An", "Binh"};
Object[] view = names; // Hợp lệ: String[] là subtype của Object[].
System.out.println(view == names); // true
~~~

Đây gọi là **hiệp biến của mảng tham chiếu**: nếu `Sub` là một subtype của `Super` và cả hai là kiểu tham chiếu phù hợp, `Sub[]` có thể gán cho biến `Super[]`. Các ví dụ:

| Quan hệ | Có gán trực tiếp? | Lý do |
| --- | --- | --- |
| `String[]` → `Object[]` | Có | `String` là subtype của `Object` |
| `Integer[]` → `Number[]` | Có | `Integer` là subtype của `Number` |
| `Dog[]` → `Animal[]` nếu `Dog extends Animal` | Có | Quan hệ subtype của thành phần |
| `Object[]` → `String[]` | Không, trừ ép kiểu có kiểm tra runtime | Hướng ngược không an toàn |
| `int[]` → `Object[]` | **Không** | `int` là nguyên thủy, không phải subtype của `Object` |
| `int[]` → `Object` | Có | Bản thân mảng là đối tượng |
| `String[][]` → `Object[][]` | Có | Các kiểu mảng ở từng tầng có quan hệ subtype phù hợp |

**Cần phân biệt biến tham chiếu với đối tượng thực:** Trong `Object[] view = names;`, Java **không tạo một mảng `Object[]` mới**. Hai biến chỉ cùng giữ tham chiếu đến đối tượng có **kiểu thực khi chạy là `String[]`**.

## 2. Vì sao kiểm tra lúc biên dịch chưa đủ an toàn?

Xét đoạn mã:

~~~java
String[] names = {"An", "Binh"};
Object[] items = names;

items[1] = "Chi";            // Hợp lệ: String.
System.out.println(names[1]); // Chi.

// items[0] = Integer.valueOf(7); // Biên dịch được,
// nhưng khi chạy ném ArrayStoreException.
~~~

Nhìn vào **kiểu khai báo** `Object[]`, trình biên dịch cho phép đặt một tham chiếu `Integer` vào một phần tử `Object`. Nhưng đối tượng mảng thật sự vẫn là `String[]`, chỉ cho phép mỗi phần tử nhận `String` (hoặc `null`). JVM kiểm tra tại lúc thực hiện thao tác ghi và ném `ArrayStoreException` nếu giá trị không tương thích với **kiểu thành phần runtime**.

Nói cách khác, có **hai lớp kiểm tra**:

1. **Compile time:** biểu thức ở bên phải phải gán được cho kiểu phần tử theo **kiểu tĩnh** của biến mảng (`Object` đối với `Object[]`).
2. **Runtime:** nếu phần tử là tham chiếu không null, JVM kiểm tra đối tượng được ghi có phù hợp với **kiểu thành phần thực của đối tượng mảng** không (`String` đối với `new String[n]`).

Do đó, `Object[]` là tên kiểu mà mã nhìn thấy, không phải lời hứa rằng đối tượng mảng hiện tại chứa được mọi loại `Object`.

Một phương thức nhận `Object[]` cũng có rủi ro tương tự:

~~~java
static void replaceFirst(Object[] array, Object value) {
    array[0] = value; // Có thể ném ArrayStoreException.
}

// replaceFirst(new String[1], Integer.valueOf(10));
// Lời gọi hợp lệ khi biên dịch, thất bại lúc ghi.
~~~

## 3. `ArrayStoreException` xảy ra khi nào?

`ArrayStoreException` là ngoại lệ **runtime** khi cố ghi một giá trị tham chiếu không tương thích vào một mảng có kiểu thành phần tham chiếu cụ thể. Nó kế thừa `RuntimeException`, vì vậy không cần khai báo `throws` để biên dịch lời gọi.

~~~java
Object[] values = new String[2];
values[0] = "Java";       // Đúng.
values[1] = null;         // Đúng: null được phép với kiểu tham chiếu.

try {
    values[1] = Integer.valueOf(42); // Sai kiểu thực String[].
} catch (ArrayStoreException ex) {
    System.out.println("Rejected");
}
~~~

**Cách phân biệt lỗi:**

| Tình huống | Hành vi |
| --- | --- |
| `Object[] a = new String[2]; a[0] = "x";` | Ghi thành công |
| `a[0] = null;` | Ghi thành công; `null` hợp lệ |
| `a[0] = 42;` | `ArrayStoreException` (autobox số 42 thành `Integer`) |
| `Object[] b = new Object[2]; b[0] = 42;` | Ghi thành công vì mảng thực là `Object[]` |
| `String[] s = (String[]) new Object[2];` | `ClassCastException` khi ép kiểu mảng, **không** phải lỗi ghi phần tử |
| `String[] s = null; s[0] = "x";` | `NullPointerException` |
| `new String[0][0];` rồi truy cập phần tử `[0]` | `ArrayIndexOutOfBoundsException` |

**Đừng kiểm tra ngoại lệ bằng thông báo JVM:** nội dung `ex.getMessage()` có thể khác giữa các môi trường; khi demo, in loại ngoại lệ để expected stdout ổn định.

## 4. Kiểu thành phần thật được giữ tại runtime

Khác với thông tin generic thường bị xóa kiểu (*type erasure*), đối tượng mảng Java **biết kiểu thành phần runtime**:

~~~java
Object[] strings = new String[2];
Object[] objects = new Object[2];

System.out.println(strings.getClass().getComponentType().getTypeName());
// java.lang.String
System.out.println(objects.getClass().getComponentType().getTypeName());
// java.lang.Object
~~~

Bởi vậy, `Object[] strings` và `Object[] objects` có **cùng kiểu biến được khai báo** nhưng kiểm tra ghi phần tử khác nhau.

Khi gán `Object[] alias = strings;`, kiểu runtime vẫn không đổi. `strings.clone()` cũng tạo mảng mới **cùng kiểu runtime `String[]`** và sao chép các phần tử hiện tại; mảng clone vẫn không cho chứa `Integer` chỉ vì được gán sang biến `Object[]`.

Nếu muốn một mảng thật sự có thể lưu nhiều loại đối tượng, cần **tạo mảng `Object[]` mới** và sao chép các phần tử phù hợp vào đó, chẳng hạn `Object[] copy = java.util.Arrays.copyOf(strings, strings.length, Object[].class);`.

## 5. Hiệp biến trong mảng nhiều chiều

Một `String[][]` là **mảng ngoài chứa các tham chiếu tới `String[]`**. Nó có thể được nhìn qua biến `Object[][]`:

~~~java
String[][] names = {{"A", "B"}, {"C"}};
Object[][] grid = names;

Object[] firstRow = grid[0]; // Thực tế là String[].
firstRow[1] = "BB";           // Đúng.
System.out.println(names[0][1]); // BB.
~~~

Có **hai vị trí ghi** cần xem xét, mỗi vị trí kiểm tra kiểu của mảng nơi ghi:

| Lệnh qua `Object[][] grid` | Mảng thực được ghi | Kết quả |
| --- | --- | --- |
| `grid[0][0] = "X";` | Mảng con thực `String[]` | Đúng |
| `grid[0][0] = 5;` | Mảng con thực `String[]` | `ArrayStoreException` |
| `grid[1] = new String[] {"Y"};` | Mảng ngoài thực `String[][]`, phần tử kiểu `String[]` | Đúng |
| `grid[1] = new Object[] {"Y"};` | Mảng ngoài thực `String[][]` | `ArrayStoreException` |
| `grid[0] = null;` | Mảng ngoài thực `String[][]` | Đúng; hàng trở thành `null` |

Như vậy, lỗi có thể xảy ra cả **khi thay một mảng con** (`grid[row] = ...`) lẫn **khi thay một phần tử bên trong mảng con** (`grid[row][column] = ...`).

`String[][]` còn có thể được gán cho biến `Object[]` vì phần tử của nó (kiểu `String[]`) cũng là một đối tượng. Tuy nhiên kiểu runtime của mảng ngoài vẫn là `String[][]`; lưu một `Object[]` không phải `String[]` vào một ô hàng cũng sẽ bị từ chối.

**Mảng răng cưa** không thay đổi nguyên tắc: các hàng có thể dài ngắn khác nhau, nhưng mỗi hàng không null vẫn phải là mảng con có kiểu phù hợp với mảng ngoài.

## 6. Ngoại lệ quan trọng: `int[]` và `int[][]`

Với mảng kiểu nguyên thủy:

~~~java
int[] numbers = {1, 2};
Object asObject = numbers; // Đúng: mọi mảng là Object.
// Object[] invalid = numbers; // LỖI biên dịch: int[] không là Object[].
~~~

`int[]` chứa phần tử kiểu nguyên thủy `int`, không phải tham chiếu `Object`. Nhưng `int[][]` là **mảng ngoài chứa các phần tử có kiểu tham chiếu `int[]`**, vì bản thân mỗi `int[]` là một đối tượng. Do đó:

~~~java
int[][] matrix = new int[2][];
Object[] rows = matrix;              // Hợp lệ.
rows[0] = new int[] {1, 2};         // Hợp lệ.
System.out.println(matrix[0][0]);  // 1.

// rows[1] = new long[] {3L};
// Biên dịch được qua Object[], nhưng ném ArrayStoreException:
// mảng ngoài thực tế yêu cầu mỗi hàng là int[].
~~~

Đây không phải “mảng nguyên thủy hiệp biến” theo nghĩa `int[]` gán thành `long[]` hoặc `Object[]`. Sự hiệp biến ở đây diễn ra ở **tầng ngoài**, nơi kiểu phần tử `int[]` là một kiểu tham chiếu.

## 7. So sánh với generic `List` và cách phòng tránh

Java generic thông thường **bất biến** (*invariant*):

~~~java
java.util.List<String> names = new java.util.ArrayList<>();
names.add("Java");

// java.util.List<Object> objects = names;
// Không biên dịch: List<String> không là subtype của List<Object>.

java.util.List<? extends Object> readable = names;
System.out.println(readable.getClass().getSimpleName()); // ArrayList.
~~~

Một tham chiếu `List<? extends Object>` cho phép **đọc** phần tử như `Object` nhưng không tùy tiện `add(new Object())` vào danh sách có kiểu phần tử chưa biết (ngoài `null`). Điều này chặn sớm loại thao tác vốn có thể gây lỗi runtime khi làm với mảng hiệp biến.

**Gợi ý thiết kế:**

- Nếu API chỉ **đọc dữ liệu**, tránh sửa phần tử trong phương thức nhận `Object[]`; dùng kiểu tham số cụ thể hoặc abstraction chỉ đọc phù hợp.
- Nếu API cần **ghi**, dùng mảng với kiểu thành phần đúng, hoặc dùng `List<T>` cùng hợp đồng generic rõ ràng.
- Khi đã có `Object[] view` nhưng cần biết mảng thật sự nhận được kiểu gì, kiểm tra nguồn và `view.getClass().getComponentType()` trong lúc gỡ lỗi; **không** dùng kiểm tra để thay thế thiết kế kiểu đúng.
- Một đối tượng mảng nhận được qua cast, method parameter hoặc `clone` không tự chuyển kiểu thành phần runtime.
- `final Object[] view` chỉ cấm **gán lại biến view**, không cấm thay phần tử của đối tượng mảng; lỗi `ArrayStoreException` vẫn có thể xảy ra.

Nếu cần sao chép từ `String[]` vào một mảng có kiểu runtime `Object[]`, tạo mảng thật sự mới:

~~~java
String[] source = {"A", "B"};
Object[] flexible = java.util.Arrays.copyOf(
        source, source.length, Object[].class);
flexible[0] = Integer.valueOf(99); // Hợp lệ: mảng runtime là Object[].
~~~

`Arrays.copyOf(..., Object[].class)` trả mảng mới kiểu `Object[]`, khác `source.clone()` vốn duy trì kiểu runtime `String[]`.

## 8. Ví dụ Java 17+ chạy độc lập

Tạo file **CovariantArrayDemo.java**:

~~~java
import java.util.Arrays;

public final class CovariantArrayDemo {
    private static void replaceFirst(Object[] target, Object value) {
        target[0] = value; // Có thể ném ArrayStoreException tại runtime.
    }

    public static void main(String[] args) {
        String[] words = {"A", "B"};
        Object[] view = words;
        System.out.println("sameArray=" + (view == words));
        System.out.println("actualComponent="
                + view.getClass().getComponentType().getTypeName());

        view[1] = "BB";
        view[0] = null; // null luôn hợp lệ cho phần tử tham chiếu.
        System.out.println("validWrites=" + Arrays.toString(words));

        try {
            replaceFirst(view, Integer.valueOf(7));
        } catch (ArrayStoreException ex) {
            System.out.println("stringReject=ArrayStoreException");
        }
        System.out.println("afterReject=" + Arrays.toString(words));

        Object[] general = new Object[1];
        general[0] = Integer.valueOf(42);
        System.out.println("objectAccept=" + general[0]);

        Number[] numbers = new Integer[2];
        numbers[0] = Integer.valueOf(5);
        try {
            numbers[1] = Double.valueOf(1.5);
        } catch (ArrayStoreException ex) {
            System.out.println("numberReject=ArrayStoreException");
        }
        System.out.println("numbersAfter=" + Arrays.toString(numbers));

        String[] cloned = words.clone();
        System.out.println("cloneDifferent=" + (cloned != words));
        System.out.println("cloneComponent="
                + cloned.getClass().getComponentType().getTypeName());
        try {
            ((Object[]) cloned)[0] = Integer.valueOf(8);
        } catch (ArrayStoreException ex) {
            System.out.println("cloneReject=ArrayStoreException");
        }

        String[][] names = {{"A", "B"}, {"C"}};
        Object[][] grid = names;
        Object[] firstRow = grid[0];
        firstRow[1] = "BB";
        System.out.println("innerValid=" + Arrays.toString(names[0]));
        try {
            firstRow[0] = Integer.valueOf(9);
        } catch (ArrayStoreException ex) {
            System.out.println("innerReject=ArrayStoreException");
        }
        try {
            grid[1] = new Object[] {"X"};
        } catch (ArrayStoreException ex) {
            System.out.println("outerReject=ArrayStoreException");
        }
        System.out.println("afterOuterReject=" + Arrays.deepToString(names));

        grid[1] = new String[] {"OK"};
        System.out.println("outerValid=" + Arrays.deepToString(names));

        int[][] primitiveRows = new int[2][];
        Object[] rowView = primitiveRows;
        rowView[0] = new int[] {1, 2};
        System.out.println("intRow=" + Arrays.toString(primitiveRows[0]));
        try {
            rowView[1] = new long[] {3L};
        } catch (ArrayStoreException ex) {
            System.out.println("primitiveRowReject=ArrayStoreException");
        }
        System.out.println("secondRowNull=" + (primitiveRows[1] == null));

        Object primitiveAsObject = new int[] {5};
        System.out.println("primitiveIsObjectArray="
                + (primitiveAsObject instanceof Object[]));

        Object[] flexible = Arrays.copyOf(
                words, words.length, Object[].class);
        flexible[0] = Integer.valueOf(99);
        System.out.println("flexible=" + Arrays.toString(flexible));
        System.out.println("sourceUnchanged=" + Arrays.toString(words));

        try {
            String[] badCast = (String[]) new Object[1];
            System.out.println(badCast.length);
        } catch (ClassCastException ex) {
            System.out.println("badCast=ClassCastException");
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 CovariantArrayDemo.java
java CovariantArrayDemo
~~~

**Expected stdout chính xác:**

~~~text
sameArray=true
actualComponent=java.lang.String
validWrites=[null, BB]
stringReject=ArrayStoreException
afterReject=[null, BB]
objectAccept=42
numberReject=ArrayStoreException
numbersAfter=[5, null]
cloneDifferent=true
cloneComponent=java.lang.String
cloneReject=ArrayStoreException
innerValid=[A, BB]
innerReject=ArrayStoreException
outerReject=ArrayStoreException
afterOuterReject=[[A, BB], [C]]
outerValid=[[A, BB], [OK]]
intRow=[1, 2]
primitiveRowReject=ArrayStoreException
secondRowNull=true
primitiveIsObjectArray=false
flexible=[99, BB]
sourceUnchanged=[null, BB]
badCast=ClassCastException
~~~

Ví dụ bắt riêng các ngoại lệ để kết quả chạy ổn định. Dòng `stringReject` cho thấy lỗi sau khi gán `String[]` vào biến `Object[]`; `innerReject` và `outerReject` thể hiện hai cấp kiểm tra kiểu trong `String[][]`; `primitiveRowReject` minh họa mảng ngoài của `int[][]` cũng là mảng **tham chiếu**; `flexible` chứng minh cách tạo mảng thực sự có kiểu `Object[]`.

## 9. Lỗi thường gặp và bài tập

| Lỗi/hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| `Object[] view = new String[2]` tạo `Object[]` mới | Không: đối tượng thật vẫn là `String[]` |
| Biên dịch được `view[0] = 7` thì chắc chạy được | Không: JVM kiểm tra kiểu phần tử thực lúc ghi |
| `ArrayStoreException` xảy ra lúc khai báo alias | Không: xảy ra khi cố **ghi** phần tử không tương thích |
| Gán `null` vào `String[]` gây `ArrayStoreException` | Không: `null` hợp lệ với mọi kiểu tham chiếu |
| `int[]` là subtype của `Object[]` | Không; chỉ `int[]` là một `Object` |
| `int[][]` không thể gán thành `Object[]` | Sai: thành phần của mảng ngoài là `int[]`, một kiểu tham chiếu |
| `String[][]` gán thành `Object[][]` là tạo bản sao | Không; vẫn dùng cùng đối tượng mảng nhiều chiều |
| `clone()` của `String[]` tạo mảng `Object[]` | Không: kiểu mảng runtime được giữ nguyên |
| `ArrayStoreException` và `ClassCastException` giống nhau | Không: một lỗi lúc **ghi mảng**, một lỗi **ép kiểu** không hợp lệ |
| `List<String>` gán được cho `List<Object>` | Không: generic thông thường bất biến |

**Bài 1.** Tạo `String[] source = {"a"}; Object[] alias = source;`. Thử ghi `"b"`, `null` và `Integer.valueOf(2)`. Ghi lại khi nào `ArrayStoreException` xảy ra.

**Bài 2.** Tạo `Number[] values = new Integer[2];`. Thử lần lượt lưu `Integer.valueOf(1)` và `Double.valueOf(2.0)`. Vì sao compiler chấp nhận nhưng runtime từ chối giá trị thứ hai?

**Bài 3.** Tạo `String[][] grid` có hai hàng, gán nó cho `Object[][] alias`. So sánh hành vi `alias[0][0] = 5` và `alias[0] = new Object[] {"x"}`.

**Bài 4.** Giải thích vì sao `Object[] rows = new int[2][];` hợp lệ nhưng `Object[] oneRow = new int[2];` không biên dịch.

**Bài 5.** Viết ví dụ sao chép `String[]` thành `Object[]` thực qua `Arrays.copyOf(..., Object[].class)`; chứng minh bản mới lưu được `Integer` mà không thay đổi mảng nguồn.

### Ghi nhớ

**Mảng tham chiếu trong Java hiệp biến:** `String[]` có thể được nhìn như `Object[]`, nhưng **kiểu thành phần của đối tượng mảng không đổi**. Khi ghi vào mảng, JVM kiểm tra kiểu thực và có thể ném `ArrayStoreException`. Với mảng nhiều chiều, kiểm tra xảy ra cả lúc thay **hàng** và thay **phần tử trong hàng**. Tránh API ghi vào mảng quá rộng kiểu; chọn kiểu cụ thể, tạo mảng thật sự đúng kiểu hoặc sử dụng generic `List<T>` phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multidimensional-arrays-009">Phân biệt int&#91;&#93; với Integer&#91;&#93; và khả năng gán qua Object</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `int[]` với `Integer[]` và khả năng gán qua `Object` trong Java

## 1. Hai kiểu mảng khác nhau

Trong Java, `int[]` và `Integer[]` đều là **kiểu tham chiếu trỏ đến đối tượng mảng**, nhưng **kiểu phần tử** khác nhau:

| Tiêu chí | `int[]` | `Integer[]` |
| --- | --- | --- |
| Kiểu phần tử | Nguyên thủy `int` | Tham chiếu đến đối tượng `java.lang.Integer` |
| Giá trị mặc định của phần tử trong mảng mới | `0` | `null` |
| Một phần tử có thể là `null`? | Không | Có |
| Gán phần tử từ giá trị `int` | Trực tiếp | Autoboxing `int` thành `Integer` |
| Đọc một phần tử vào biến `int` | Trực tiếp | Unboxing; có thể gây `NullPointerException` khi phần tử `null` |
| Gán cả mảng cho `Object` | Được | Được |
| Gán cả mảng cho `Object[]` | **Không được** | **Được** |
| Đổi trực tiếp `int[]` thành `Integer[]` bằng cast/autoboxing | Không | Không áp dụng |

Mỗi đối tượng mảng có `length` cố định sau khi khởi tạo. `int[]` thường tiết kiệm bộ nhớ và tránh boxing khi xử lý dữ liệu số; `Integer[]` phù hợp khi cần phần tử `null` hoặc một API yêu cầu tham chiếu `Integer`. Không thể coi hai kiểu mảng này là hoán đổi cho nhau.

```java
int[] primitive = new int[3];     // [0, 0, 0]
Integer[] wrappers = new Integer[3]; // [null, null, null]

primitive[0] = 10;
wrappers[0] = 10; // autoboxing: Integer.valueOf(10)

int a = primitive[0]; // 10
int b = wrappers[0];  // unboxing: intValue(), kết quả 10
```

Các lệnh trên là **đoạn minh họa đặt bên trong phương thức**, không phải chương trình độc lập. Nếu `wrappers[1]` đang là `null`, phép gán `int x = wrappers[1]` gây `NullPointerException`.

## 2. Vì sao cả hai đều gán được cho `Object`, nhưng chỉ một loại gán được cho `Object[]`?

Trong Java, **bản thân mảng luôn là đối tượng**: mọi kiểu mảng, kể cả mảng chứa phần tử nguyên thủy, đều có thể gán cho `Object`. Chúng còn triển khai `Cloneable` và `java.io.Serializable`.

```java
int[] a = {1, 2};
Integer[] b = {1, 2};

Object oa = a; // Hợp lệ: Object giữ tham chiếu tới int[]
Object ob = b; // Hợp lệ: Object giữ tham chiếu tới Integer[]

Object[] refs = b; // Hợp lệ: Integer là kiểu tham chiếu
// Object[] invalid = a; // LỖI BIÊN DỊCH: int[] không phải Object[]
```

`Object[]` là mảng có **các phần tử là tham chiếu đối tượng**. `Integer[]` là mảng của các tham chiếu `Integer` (một kiểu con của `Object`), nên có thể gán cho `Object[]`. Ngược lại, phần tử của `int[]` là giá trị nguyên thủy; `int` **không phải kiểu con** của `Object`, vì vậy `int[]` không thể gán cho `Object[]`.

Một phép gán mảng sang `Object` **không biến đổi các phần tử**:

- `Object o = new int[]{1, 2};` chỉ lưu tham chiếu đến **chính mảng `int[]`**.
- `(int[]) o` trả lại tham chiếu theo kiểu `int[]` khi đối tượng thực sự có kiểu đó.
- `(Integer[]) o` không chuyển kiểu từng phần tử; nếu `o` thực sự trỏ tới `int[]`, phép cast này gây `ClassCastException`.
- Autoboxing `int` → `Integer` và unboxing `Integer` → `int` áp dụng cho **giá trị riêng lẻ**, không tự chuyển toàn bộ `int[]` ↔ `Integer[]`.

## 3. Mảng nhiều chiều: `int[][]` lại có thể gán cho `Object[]`

Đây là điểm đặc biệt dễ nhầm trong chủ đề mảng nhiều chiều. Trong Java, `int[][]` là một **mảng ngoài**, mỗi phần tử của nó là **một tham chiếu tới `int[]`**. Vì `int[]` là một đối tượng nên phần tử của mảng ngoài là kiểu tham chiếu.

```java
int[][] matrix = {{10, 20}, {30}};
Object asObject = matrix;   // Hợp lệ: mọi mảng đều là Object
Object[] rows = matrix;     // Hợp lệ: mỗi phần tử của matrix là int[]
Object first = rows[0];     // Đối tượng thật là int[]
int[] firstRow = (int[]) first;
int item = firstRow[1];     // 20

// Object[][] invalid = matrix; // LỖI: int[][] không phải Object[][]
```

So sánh ba tầng:

| Kiểu biểu thức | Bản chất đối tượng mảng | Gán được cho `Object[]`? |
| --- | --- | --- |
| `int[]` | Các phần tử là `int` nguyên thủy | Không |
| `Integer[]` | Các phần tử là tham chiếu `Integer` | Có |
| `int[][]` | Các phần tử là tham chiếu `int[]` | Có |

Độ sâu quan trọng: `int[][]` là `Object[]`, nhưng mỗi hàng `matrix[i]` vẫn có kiểu `int[]` và **không thể** gán trực tiếp cho `Object[]`. Tương tự, `Integer[][]` có thể gán cho `Object[][]`, vì mỗi hàng `Integer[]` là một `Object[]`.

## 4. Tính hiệp biến và kiểm tra kiểu khi ghi qua `Object[]`

Mảng các kiểu tham chiếu trong Java **hiệp biến** (*covariant*): nếu `Integer` là kiểu con của `Number` và `Object`, thì `Integer[]` gán được cho `Number[]` và `Object[]`.

```java
Integer[] numbers = {1, 2};
Number[] asNumbers = numbers;
Object[] asObjects = numbers;
asObjects[0] = Integer.valueOf(10); // Hợp lệ
// asObjects[1] = "hello";          // Biên dịch được, nhưng lỗi khi chạy
```

Do `asObjects` và `numbers` cùng trỏ tới **mảng thực có kiểu `Integer[]`**, JVM kiểm tra kiểu đối tượng lúc gán phần tử. Dù biến có kiểu `Object[]`, gán `String` vào mảng thực `Integer[]` sẽ ném **`ArrayStoreException`**. Điều này cũng áp dụng cho `Object[] rows = new int[2][]`: chỉ có thể lưu `int[]` hoặc `null` vào từng phần tử, không thể lưu `Integer[]`.

Đừng nhầm `ArrayStoreException` khi gán sai kiểu cho **phần tử mảng tham chiếu** với `ClassCastException` khi **ép kiểu chính đối tượng mảng**.

## 5. Chương trình Java 17+ có thể chạy độc lập

Tạo tệp **`ArrayTypeDemo.java`**:

```java
import java.util.Arrays;

public class ArrayTypeDemo {
    public static void main(String[] args) {
        int[] raw = {1, 2, 3};
        Integer[] wrapped = {1, null, 3};

        Object rawAsObject = raw;
        Object wrappedAsObject = wrapped;
        System.out.println("raw la Object: " + (rawAsObject instanceof Object));
        System.out.println("raw la Object[]: " + (rawAsObject instanceof Object[]));
        System.out.println("wrapped la Object[]: " + (wrappedAsObject instanceof Object[]));

        Object[] elements = wrapped;
        elements[0] = 99;  // Autoboxing; thay đổi mảng wrapped gốc
        System.out.println("wrapped: " + Arrays.toString(wrapped));

        try {
            elements[1] = "wrong"; // JVM chặn do mảng thực là Integer[]
        } catch (ArrayStoreException ex) {
            System.out.println("Gan sai kieu: ArrayStoreException");
        }

        int[][] matrix = {{10, 20}, {30}};
        Object[] rows = matrix;
        System.out.println("matrix la Object[]: " + (matrix instanceof Object[]));
        System.out.println("rows[0] la int[]: " + (rows[0] instanceof int[]));
        rows[1] = new int[]{40, 50};
        System.out.println("Hang thu 2: " + Arrays.toString(matrix[1]));

        try {
            rows[0] = new Integer[]{7}; // Phần tử thực phải là int[]
        } catch (ArrayStoreException ex) {
            System.out.println("Gan sai hang: ArrayStoreException");
        }

        System.out.println("asList(int[]): " + Arrays.asList(raw).size());
        System.out.println("asList(Integer[]): " + Arrays.asList(wrapped).size());

        Integer[] boxedCopy = Arrays.stream(raw).boxed().toArray(Integer[]::new);
        int[] primitiveCopy = Arrays.stream(boxedCopy)
                                    .mapToInt(Integer::intValue).toArray();
        System.out.println("Boxed: " + Arrays.toString(boxedCopy));
        System.out.println("Unboxed: " + Arrays.toString(primitiveCopy));
    }
}
```

Trong thư mục chứa tệp, sử dụng JDK 17 trở lên:

```bash
javac -encoding UTF-8 ArrayTypeDemo.java
java ArrayTypeDemo
```

Kết quả stdout chính xác (biên dịch thành công không in thông báo):

```text
raw la Object: true
raw la Object[]: false
wrapped la Object[]: true
wrapped: [99, null, 3]
Gan sai kieu: ArrayStoreException
matrix la Object[]: true
rows[0] la int[]: true
Hang thu 2: [40, 50]
Gan sai hang: ArrayStoreException
asList(int[]): 1
asList(Integer[]): 3
Boxed: [1, 2, 3]
Unboxed: [1, 2, 3]
```

Giải thích: `elements` là tham chiếu kiểu `Object[]` đến cùng đối tượng `Integer[]` với `wrapped`, nên sửa `elements[0]` ảnh hưởng `wrapped[0]`. `rows` trỏ tới cùng mảng ngoài `matrix`, vì vậy thay `rows[1]` làm đổi hàng thứ hai của `matrix`. Cả hai phép ghi sai kiểu đều bị JVM chặn tại thời điểm chạy.

## 6. Chuyển đổi nội dung mảng khi thật sự cần

Vì không có phép chuyển toàn mảng tự động, hãy **tạo mảng mới** và chuyển từng phần tử.

**Từ `int[]` sang `Integer[]`:**

```java
int[] source = {5, 6};
Integer[] boxed = java.util.Arrays.stream(source)
                                  .boxed()
                                  .toArray(Integer[]::new);
// boxed có nội dung [5, 6]
```

**Từ `Integer[]` sang `int[]`:**

```java
Integer[] source = {5, 6};
int[] plain = java.util.Arrays.stream(source)
                              .mapToInt(Integer::intValue)
                              .toArray();
// plain có nội dung [5, 6]
```

Hai đoạn là mã minh họa bên trong phương thức; ví dụ ở mục 5 là chương trình chạy hoàn chỉnh. Nếu `Integer[]` chứa `null`, phép unboxing tại `Integer::intValue` gây `NullPointerException`. Phải quyết định cách xử lý `null` tùy bài toán trước khi chuyển (ví dụ từ chối dữ liệu, bỏ qua, hoặc thay bằng giá trị mặc định có chủ đích).

**Một lỗi API liên quan:** `Arrays.asList(raw)` với `raw` là `int[]` tạo danh sách có **một phần tử là cả mảng `int[]`**; `Arrays.asList(wrapped)` với `wrapped` là `Integer[]` tạo danh sách các `Integer` theo từng phần tử. Trong ví dụ chạy, kích thước lần lượt là `1` và `3`. Để lấy `List<Integer>` từ `int[]`, dùng `Arrays.stream(raw).boxed().toList()` (Java 16+); kết quả này là danh sách các giá trị đã boxing.

## 7. Lỗi phổ biến và cách ghi nhớ

- `int[]` là mảng **chứa nguyên thủy**, nhưng bản thân **vẫn là đối tượng**, do đó gán được cho `Object`.
- `Object[]` yêu cầu **kiểu phần tử là tham chiếu**: nhận `Integer[]`, `String[]`, `int[][]`; **không nhận** `int[]`.
- Không dùng `(Integer[]) someIntArray` để boxing cả mảng; phép cast không chuyển phần tử, cần tạo mảng mới.
- Tránh unboxing `Integer` chứa `null` mà không kiểm tra.
- Qua biến `Object[]`, phép ghi vẫn bị kiểm tra theo kiểu mảng thật ở runtime; `ArrayStoreException` bảo vệ tính đúng đắn của mảng.
- Với mảng nhiều chiều, luôn xem **kiểu phần tử của tầng đang xét**: `int[][]` chứa phần tử `int[]` (tham chiếu), còn `int[]` chứa phần tử `int` (nguyên thủy).

**Quy tắc cốt lõi:** loại dữ liệu của **phần tử mảng** quyết định mảng đó có thể là `Object[]` hay không; sự thật “mảng là đối tượng” chỉ đủ để gán **cả mảng** cho `Object`.

</details>

- [Quay lại đầu trang](#back-to-top)
