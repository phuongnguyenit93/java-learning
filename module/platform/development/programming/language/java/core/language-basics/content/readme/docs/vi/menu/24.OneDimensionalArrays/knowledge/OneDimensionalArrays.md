<a id="back-to-top"></a>

# Mảng một chiều

## Menu
- [Mảng là đối tượng chứa số phần tử cố định cùng kiểu thành phần](#one-dimensional-arrays-001)
- [Khai báo biến mảng và tạo mảng bằng new](#one-dimensional-arrays-002)
- [Khởi tạo mảng bằng danh sách giá trị](#one-dimensional-arrays-003)
- [Chỉ số bắt đầu từ 0 và trường length](#one-dimensional-arrays-004)
- [Giá trị mặc định của phần tử nguyên thủy và tham chiếu](#one-dimensional-arrays-005)
- [Mảng rỗng khác tham chiếu mảng null](#one-dimensional-arrays-006)
- [Đọc, cập nhật và duyệt phần tử](#one-dimensional-arrays-007)
- [ArrayIndexOutOfBoundsException và NegativeArraySizeException](#one-dimensional-arrays-008)
- [Gán mảng chỉ sao chép tham chiếu](#one-dimensional-arrays-009)
- [Giới thiệu Arrays.toString(), Arrays.equals() và Arrays.copyOf()](#one-dimensional-arrays-010)

## <a id="one-dimensional-arrays-001">Mảng là đối tượng chứa số phần tử cố định cùng kiểu thành phần</a>

<details>
<summary>Xem chi tiết</summary>

# Mảng là đối tượng chứa số phần tử cố định cùng kiểu thành phần trong Java

> **Java core — language-basics / Mảng một chiều**  
> **Phiên bản:** Java 17+, chương trình một file, không cần thư viện ngoài JDK.  
> **Mục tiêu:** Hiểu vì sao mảng là **đối tượng** có độ dài cố định và **kiểu thành phần runtime** xác định; phân biệt mảng nguyên thủy với mảng tham chiếu, quan sát cơ chế covariance và `ArrayStoreException`.

## 1. Mảng một chiều là một đối tượng

Trong Java, mảng (`array`) là đối tượng dùng để lưu **một số lượng vị trí cố định**, mỗi vị trí có cùng **kiểu thành phần** (*component type*).

~~~java
int[] scores = new int[3]; // Đối tượng mảng int gồm 3 vị trí.
scores[0] = 7;
scores[1] = 8;
scores[2] = 9;
~~~

- `int[]` là **kiểu mảng**, `int` là **kiểu thành phần**. `String[]` tương tự nhưng các phần tử giữ tham chiếu `String`.
- `new int[3]` tạo **một đối tượng mảng** có `length == 3`. Các vị trí được đánh số **0, 1, 2**.
- Biến `scores` giữ **tham chiếu** tới đối tượng mảng. Có thể gán tham chiếu đó cho biến khác, truyền vào phương thức hoặc đặt bằng `null`.
- Mảng là đối tượng nên có thể gán cho biến kiểu `Object`. Mảng cũng thực hiện `Cloneable` và `java.io.Serializable`.
- Số vị trí của **chính đối tượng mảng** không thay đổi sau khi tạo; nhưng giá trị lưu trong các vị trí có thể thay đổi.

Một `int[]` chỉ giữ các giá trị `int`, còn `String[]` giữ `null` hoặc tham chiếu tới các đối tượng thích hợp với kiểu thành phần `String`. Một mảng tham chiếu có kiểu thành phần là lớp/interface cha có thể chứa đối tượng thuộc các lớp con khác nhau; không bắt buộc mọi phần tử có **cùng lớp runtime**.

Phần tiếp theo tập trung vào **kiểu thành phần và ràng buộc kiểu lúc chạy**. Việc khai báo bằng `new`, duyệt chỉ số, giá trị mặc định, mảng rỗng và sao chép sẽ được thực hành ở các bài tương ứng trong chương.

## 2. Kiểu thành phần và kiểm tra kiểu tại runtime

Mảng có kiểu runtime thực, ngay cả khi được tham chiếu qua `Object`:

~~~java
int[] values = {1, 2};
Object obj = values;

System.out.println(obj instanceof int[]);     // true
System.out.println(obj instanceof Object);    // true
System.out.println(obj instanceof Object[]);  // false
System.out.println(values.getClass().getComponentType().getTypeName()); // int
~~~

`int[]` là một đối tượng nhưng **không phải `Object[]`**, vì phần tử của mảng là giá trị primitive `int`, không phải tham chiếu đối tượng.

Mảng tham chiếu có quy tắc **covariance**: `String[]` có thể dùng ở nơi cần `Object[]`, nhưng Java vẫn bảo vệ kiểu thành phần **thật** của đối tượng mảng:

~~~java
String[] words = {"a", "b"};
Object[] objects = words; // Hợp lệ: covariance.
objects[0] = "X";         // Hợp lệ: String.
try {
    objects[1] = Integer.valueOf(42); // Không phải String!
} catch (ArrayStoreException ex) {
    System.out.println("ArrayStoreException");
}
~~~

`objects` có kiểu tĩnh `Object[]` nhưng mảng runtime vẫn là `String[]`. Gán `Integer` vào vị trí này ném `ArrayStoreException` — đây là lỗi khác với `ArrayIndexOutOfBoundsException`.

**Cùng kiểu thành phần** nghĩa là các vị trí tuân theo **kiểu thành phần của đối tượng mảng**, không phải mọi phần tử đều có cùng kiểu runtime. Ví dụ `Number[]` có thể giữ `Integer` và `Double`, vì cả hai đều là subtype của `Number`.

## 3. Chương trình Java 17+ chạy độc lập

Tạo file **OneDimensionalArrayDemo.java**:

~~~java
import java.util.Arrays;

public final class OneDimensionalArrayDemo {
    public static void main(String[] args) {
        int[] numbers = new int[3];
        String[] names = new String[2];

        System.out.println("intDefaults=" + Arrays.toString(numbers));
        System.out.println("referenceDefaults=" + Arrays.toString(names));
        System.out.println("initialLength=" + numbers.length);

        numbers[0] = 7;
        numbers[2] = 9;
        System.out.println("changed=" + Arrays.toString(numbers));

        int[] empty = new int[0];
        System.out.println("emptyLength=" + empty.length);

        Object asObject = numbers;
        System.out.println("isObject=" + (asObject instanceof Object));
        System.out.println("componentType="
                + numbers.getClass().getComponentType().getTypeName());

        int[] alias = numbers;
        alias[1] = 5;
        System.out.println("aliasShares=" + (alias == numbers));
        System.out.println("afterAlias=" + Arrays.toString(numbers));

        int[] copied = numbers.clone();
        copied[0] = 100;
        System.out.println("afterCopyOriginal=" + Arrays.toString(numbers));
        System.out.println("afterCopyNew=" + Arrays.toString(copied));

        numbers = Arrays.copyOf(numbers, 5);
        System.out.println("resizedNewLength=" + numbers.length);
        System.out.println("resizedValues=" + Arrays.toString(numbers));
        System.out.println("oldAliasLength=" + alias.length);

        String[] words = {"a", "b"};
        Object[] refArray = words;
        refArray[0] = "X";
        System.out.println("referenceChanged=" + Arrays.toString(words));
        try {
            refArray[1] = Integer.valueOf(42);
        } catch (ArrayStoreException ex) {
            System.out.println("covariantStore=ArrayStoreException");
        }

        Object primitiveArray = new int[] {1, 2};
        System.out.println("primitiveObjectArray="
                + (primitiveArray instanceof Object[]));

        try {
            int invalid = numbers[5];
            System.out.println(invalid);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("badIndex=ArrayIndexOutOfBoundsException");
        }

        try {
            int[] negative = new int[-1];
            System.out.println(negative.length);
        } catch (NegativeArraySizeException ex) {
            System.out.println("negativeLength=NegativeArraySizeException");
        }

        int[] missing = null;
        try {
            System.out.println(missing.length);
        } catch (NullPointerException ex) {
            System.out.println("nullArray=NullPointerException");
        }
    }
}
~~~

Trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 OneDimensionalArrayDemo.java
java OneDimensionalArrayDemo
~~~

**Expected stdout chính xác:**

~~~text
intDefaults=[0, 0, 0]
referenceDefaults=[null, null]
initialLength=3
changed=[7, 0, 9]
emptyLength=0
isObject=true
componentType=int
aliasShares=true
afterAlias=[7, 5, 9]
afterCopyOriginal=[7, 5, 9]
afterCopyNew=[100, 5, 9]
resizedNewLength=5
resizedValues=[7, 5, 9, 0, 0]
oldAliasLength=3
referenceChanged=[X, b]
covariantStore=ArrayStoreException
primitiveObjectArray=false
badIndex=ArrayIndexOutOfBoundsException
negativeLength=NegativeArraySizeException
nullArray=NullPointerException
~~~

Những dòng `oldAliasLength=3` và `resizedNewLength=5` chứng minh tạo mảng mới chứ không đổi chiều dài mảng cũ. `covariantStore` thể hiện ràng buộc kiểu thành phần được kiểm tra tại runtime; `badIndex` và `negativeLength` là lỗi khác nhau.

## 4. Lỗi phổ biến và bài tập

| Hiểu lầm / lỗi | Cách hiểu hoặc xử lý đúng |
| --- | --- |
| Gọi `array.length()` | Dùng trường `array.length` |
| Cho rằng chỉ số bắt đầu từ 1 | Mảng bắt đầu từ chỉ số 0 |
| Gán `array.length = n` | Không hợp lệ; tạo mảng mới nếu cần số vị trí khác |
| Dùng `array[array.length]` | Vượt phạm vi; chỉ số cuối là `array.length - 1` khi không rỗng |
| Đồng nhất mảng rỗng và `null` | Rỗng có `length == 0`; null không có đối tượng |
| Gán mảng cho biến khác rồi nghĩ đã sao chép | Đó chỉ là alias; dùng `clone()` hoặc `Arrays.copyOf` khi cần mảng mới |
| Cho rằng `int[]` là `Object[]` | `int[]` là Object nhưng các phần tử là primitive |
| Gán sai kiểu qua alias `Object[]` trỏ tới `String[]` | Runtime ném `ArrayStoreException` |

**Bài 1.** Tạo mảng `boolean[3]` và `String[2]`, dự đoán giá trị mặc định, sau đó in bằng `Arrays.toString`.

**Bài 2.** Với `int[] a = {2, 4}; int[] b = a;`, thay `b[0]` và giải thích vì sao `a[0]` cũng đổi. Sau đó dùng `clone()` để tạo mảng độc lập.

**Bài 3.** Dùng `Arrays.copyOf` mở rộng mảng từ 2 lên 4 phần tử, giải thích các giá trị thêm vào và trạng thái tham chiếu cũ.

**Bài 4.** Thử `Number[] values = {Integer.valueOf(1), Double.valueOf(2.5)};`; vì sao hai đối tượng có lớp runtime khác nhau vẫn có thể nằm trong cùng mảng?

**Bài 5.** Tạo `String[]`, gán vào `Object[]` rồi thử lưu `Integer`; dự đoán lỗi. Nêu khác biệt với lỗi chỉ số và kích thước âm.

### Ghi nhớ

**Mảng là đối tượng có kiểu thành phần và `length` được ấn định khi tạo.** Ta có thể sửa phần tử nhưng không sửa số vị trí của đối tượng mảng. Mảng primitive giữ giá trị primitive; mảng tham chiếu giữ `null` hoặc đối tượng tương thích với kiểu thành phần runtime. Khi cần mở rộng, tạo mảng mới; khi chia sẻ tham chiếu, thay đổi phần tử có thể được nhìn thấy qua mọi alias.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="one-dimensional-arrays-002">Khai báo biến mảng và tạo mảng bằng new</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo biến mảng và tạo mảng bằng `new` — Java 17+

## 1. Phân biệt khai báo biến và tạo đối tượng mảng

Trong Java, **mảng (array)** là một đối tượng có độ dài cố định, chứa các phần tử cùng kiểu thành phần. Biến kiểu mảng giữ **tham chiếu** đến đối tượng mảng, chứ không tự chứa các phần tử chỉ vì đã được khai báo.

~~~java
int[] scores;           // Chỉ khai báo biến tham chiếu mảng.
scores = new int[3];    // Tạo đối tượng mảng ba phần tử và gán tham chiếu.
scores[0] = 10;         // Truy cập phần tử đầu tiên.
~~~

Có thể gộp khai báo và cấp phát:

~~~java
int[] scores = new int[3];
~~~

| Cú pháp | Điều gì xảy ra? |
|---|---|
| `int[] scores;` | Khai báo biến cục bộ có kiểu `int[]`; **chưa có mảng được tạo** |
| `new int[3]` | Cấp phát đối tượng mảng `int` có **3 phần tử**; trả tham chiếu |
| `scores = new int[3];` | Gán tham chiếu tới mảng mới cho biến `scores` |
| `scores[0] = 10;` | Thay giá trị ở chỉ số 0 trong mảng mà `scores` trỏ tới |
| `scores.length` | Đọc độ dài mảng, ở đây là `3` |

**Ghi nhớ:** *khai báo biến* và *tạo mảng* là hai thao tác khác nhau. Từ khóa `new` thực hiện việc tạo đối tượng mảng khi chạy.

## 2. Cú pháp khai báo được khuyến nghị

Java ưu tiên cách viết **ngoặc vuông ở kiểu**:

~~~java
int[] numbers;
String[] names;
double[] temperatures;
boolean[] flags;
~~~

Java cũng chấp nhận phong cách cũ:

~~~java
int numbers[];
~~~

Tuy nhiên, `int[] numbers` dễ đọc hơn vì thể hiện rõ kiểu biến là **mảng của int**.

Một bẫy khi khai báo nhiều biến:

~~~java
int[] first, second; // Cả first lẫn second đều là int[].

int third[], count;  // third là int[]; count là int.
~~~

Vì vậy, thường nên dùng **một khai báo trên một dòng** để tránh hiểu nhầm kiểu.

Dấu `[]` biểu thị **kiểu mảng**, không phải kích thước. Độ dài được chỉ định ở biểu thức cấp phát `new int[3]`, không phải trong kiểu khai báo:

~~~java
int[] data = new int[3]; // Đúng.
// int[3] data;           // Sai cú pháp Java.
~~~

## 3. Tạo mảng bằng `new ElementType[size]`

~~~java
int[] scores = new int[3];
String[] labels = new String[2];
boolean[] enabled = new boolean[2];
double[] prices = new double[2];
~~~

`size` là biểu thức kiểu số nguyên phù hợp và được đánh giá **khi chạy**. Giá trị phải không âm:

~~~java
int size = 2 + 1;
int[] values = new int[size];   // length = 3.
int[] empty = new int[0];       // Hợp lệ: mảng rỗng.
~~~

`new int[-1]` là biểu thức có thể biên dịch, nhưng khi thực thi sẽ ném `NegativeArraySizeException`. Kích thước không âm vẫn không bảo đảm đủ bộ nhớ; yêu cầu cấp phát quá lớn có thể gặp `OutOfMemoryError`.

Mảng có **độ dài cố định sau khi tạo**. Không gán được `values.length = 5`; muốn độ dài khác, phải tạo một mảng mới (chẳng hạn qua `Arrays.copyOf`) rồi dùng tham chiếu tới mảng mới.

## 4. Sau `new`, phần tử đã có giá trị mặc định

`new int[3]` tạo **một mảng** có ba vị trí; giá trị ở từng vị trí ban đầu là `0`. Với `boolean[]`, mặc định là `false`; với `char[]`, là NUL (mã 0); với `String[]`, `Student[]` hoặc các mảng tham chiếu khác, mặc định là **`null`**, không tự gọi constructor từng đối tượng.

~~~java
int[] numbers = new int[2];         // [0, 0]
String[] names = new String[2];      // [null, null]
System.out.println(java.util.Arrays.toString(numbers));
System.out.println(java.util.Arrays.toString(names));
~~~

Biến cục bộ `int[] ref;` **chưa được gán** nên không được đọc ngay; khác với phần tử của mảng đã cấp phát. Quy tắc chi tiết của cả tám kiểu nguyên thủy và các kiểu tham chiếu được giải thích riêng ở bài về giá trị mặc định.

## 5. Chỉ số và `length` của đối tượng vừa tạo

Với `new int[3]`, `length == 3` nhưng chỉ số hợp lệ là `0`, `1`, `2`. `length` là **trường**, không có dấu `()`; không thể sửa để nới dài chính mảng ấy.

~~~java
int[] scores = new int[3];
scores[0] = 10;
scores[2] = 30;
System.out.println(scores.length); // 3
// scores[3] = 40; // Nếu thực thi: ArrayIndexOutOfBoundsException.
~~~

Kiểm tra `0 <= i && i < scores.length` trước khi đọc/ghi theo chỉ số do người dùng cung cấp. Đây là giới hạn của mảng đã tồn tại, khác với lỗi `NegativeArraySizeException` khi **tạo** mảng với kích thước âm.

Sau khi phân biệt khai báo và cấp phát, chương trình sau áp dụng thêm các tình huống liên quan để bạn tự quan sát trạng thái của mảng đã tạo.

## 6. Chương trình Java 17+ chạy độc lập

Lưu file tên **`ArrayDeclarationNewDemo.java`**:

~~~java
import java.util.Arrays;

public final class ArrayDeclarationNewDemo {
    private ArrayDeclarationNewDemo() {}

    static void show(String name, int[] array) {
        System.out.println(name + " length=" + array.length
                + " values=" + Arrays.toString(array));
    }

    public static void main(String[] args) {
        int[] numbers;             // Khai báo, chưa tạo mảng.
        numbers = new int[3];      // Tạo ba phần tử mặc định bằng 0.
        show("new", numbers);

        numbers[0] = 10;
        numbers[2] = 30;
        show("updated", numbers);

        int[] empty = new int[0];
        show("empty", empty);

        int[] initializer = {2, 4, 6};
        show("initializer", initializer);

        int[] explicit = new int[]{7, 8};
        show("explicit", explicit);

        int size = 2 + 1;
        int[] computed = new int[size];
        show("computed", computed);

        String[] labels = new String[2];
        boolean[] flags = new boolean[2];
        double[] prices = new double[2];
        System.out.println("labels=" + Arrays.toString(labels));
        System.out.println("flags=" + Arrays.toString(flags));
        System.out.println("prices=" + Arrays.toString(prices));

        int[] alias = numbers;
        alias[1] = 20;
        show("shared", numbers);
        System.out.println("same object=" + (numbers == alias));

        int[] independent = numbers.clone();
        independent[0] = 99;
        show("original", numbers);
        show("clone", independent);
        System.out.println("clone distinct=" + (numbers != independent));

        int[] extended = Arrays.copyOf(numbers, 5);
        show("extended", extended);
        show("original again", numbers);

        int[] missing = null;
        System.out.println("null ref=" + (missing == null));
        try {
            System.out.println(missing.length);
        } catch (NullPointerException ex) {
            System.out.println("null length=NullPointerException");
        }

        try {
            int[] bad = new int[-1];
            System.out.println(bad.length);
        } catch (NegativeArraySizeException ex) {
            System.out.println("negative size=NegativeArraySizeException");
        }

        try {
            System.out.println(empty[0]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("empty index=ArrayIndexOutOfBoundsException");
        }
    }
}
~~~

**Bash (Linux/macOS), yêu cầu JDK 17+:**

~~~bash
javac --release 17 -encoding UTF-8 ArrayDeclarationNewDemo.java
java -cp . ArrayDeclarationNewDemo
~~~

**PowerShell (Windows), yêu cầu JDK 17+:**

~~~powershell
javac --release 17 -encoding UTF-8 .\ArrayDeclarationNewDemo.java
java -cp . ArrayDeclarationNewDemo
~~~

### Exact expected stdout

~~~text
new length=3 values=[0, 0, 0]
updated length=3 values=[10, 0, 30]
empty length=0 values=[]
initializer length=3 values=[2, 4, 6]
explicit length=2 values=[7, 8]
computed length=3 values=[0, 0, 0]
labels=[null, null]
flags=[false, false]
prices=[0.0, 0.0]
shared length=3 values=[10, 20, 30]
same object=true
original length=3 values=[10, 20, 30]
clone length=3 values=[99, 20, 30]
clone distinct=true
extended length=5 values=[10, 20, 30, 0, 0]
original again length=3 values=[10, 20, 30]
null ref=true
null length=NullPointerException
negative size=NegativeArraySizeException
empty index=ArrayIndexOutOfBoundsException
~~~

**Đối chiếu:** `new int[3]` tạo các số 0; gán giá trị cập nhật phần tử nhưng không đổi `length`. Alias tác động cùng mảng; clone tạo mảng `int[]` khác; `copyOf` tạo mảng dài 5, để mảng gốc dài 3. Ba lỗi/trường hợp biên `null`, kích thước âm, đọc mảng rỗng được bắt riêng và in nhãn ổn định, không dựa vào thông điệp ngoại lệ của JDK.

## 7. Lỗi phổ biến khi mới học mảng

| Sai lầm | Vấn đề | Cách đúng |
|---|---|---|
| `int[] a;` rồi đọc `a.length` | Local chưa gán | `a = new int[3];` trước khi đọc |
| Cho rằng `int[] a = null;` tạo mảng rỗng | `null` không có object | `new int[0]` để tạo mảng rỗng |
| Viết `int[3] a;` | Sai vị trí kích thước | `int[] a = new int[3];` |
| `a.length()` | `length` không phải method | `a.length` |
| Truy cập `a[a.length]` | Chỉ số cuối là `length-1` | Kiểm tra `0 <= i && i < a.length` |
| Tạo `new int[-1]` | `NegativeArraySizeException` | Kiểm tra kích thước không âm |
| `new int[2]{1, 2}` | Không được ghi kích thước với initializer | `new int[]{1, 2}` |
| `a = {1,2};` sau khi khai báo | Initializer rút gọn không dùng ở đây | `a = new int[]{1,2};` |
| Nghĩ `int[] b = a;` sao chép phần tử | Hai biến chia sẻ cùng mảng | `a.clone()` hoặc `Arrays.copyOf` |
| Nghĩ `new Student[2]` tạo hai Student | Chỉ tạo mảng hai tham chiếu null | Tạo riêng từng object `Student` |
| Nghĩ có thể đổi `a.length` | Mảng có độ dài cố định | Tạo mảng mới độ dài mong muốn |

## 8. Bài tập có đáp án

**A.** Khai báo một biến kiểu mảng `double[]` và cấp phát 4 phần tử.  
**Đáp án:**

~~~java
double[] temperatures = new double[4];
~~~

Giá trị mặc định mỗi phần tử là `0.0`.

**B.** Vì sao `int[] a;` không thể gọi ngay `a.length`?  
**Đáp án:** mới chỉ khai báo biến cục bộ, chưa được gán tham chiếu tới mảng; compiler áp dụng quy tắc *definite assignment*.

**C.** `int[] a = new int[0];` có hợp lệ không và `a.length` bằng bao nhiêu?  
**Đáp án:** hợp lệ, `a.length == 0`; nhưng không có phần tử `a[0]`.

**D.** Sau đoạn mã dưới, `a[1]` là bao nhiêu?

~~~java
int[] a = {3, 4};
int[] b = a;
b[1] = 8;
~~~

**Đáp án:** `8`, vì `a` và `b` cùng trỏ một mảng.

**E.** Viết lại cho đúng phép gán mảng có sẵn biến:

~~~java
int[] a;
a = new int[]{5, 6};
~~~

**Đáp án:** đoạn trên đã đúng. `a = {5,6};` mới là cú pháp sai.

**F.** Với `String[] names = new String[2];`, có hai đối tượng String mới được tạo sẵn không?  
**Đáp án:** không. Mảng có hai phần tử tham chiếu, mỗi phần tử mặc định `null`.

## 9. Checklist và kết luận

- [ ] Biết `int[] x;` **chỉ khai báo tham chiếu mảng**, không tạo object.
- [ ] Biết `new int[n]` tạo một đối tượng mảng dài `n`.
- [ ] Dùng `int[]` thay cho cú pháp gây hiểu nhầm `int a[]`.
- [ ] Hiểu giá trị mặc định của phần tử mảng mới.
- [ ] Phân biệt biến local chưa gán, tham chiếu `null` và mảng rỗng.
- [ ] Đọc `length` và truy cập chỉ số hợp lệ từ 0 đến `length-1`.
- [ ] Tạo mảng bằng initializer `{...}` và `new int[]{...}` đúng ngữ cảnh.
- [ ] Biết việc gán `b = a` chia sẻ tham chiếu, không sao chép phần tử.
- [ ] Biết mảng cố định độ dài, muốn đổi độ dài phải tạo mảng mới.
- [ ] Phân biệt lỗi kích thước âm với lỗi truy cập chỉ số.
- [ ] Chạy `ArrayDeclarationNewDemo.java` trên JDK 17+ và đối chiếu exact stdout.

**Kết luận:** **Khai báo mảng** tạo một biến kiểu tham chiếu; **`new ElementType[size]`** mới tạo đối tượng mảng với độ dài cố định và các phần tử có giá trị mặc định. Hiểu rõ hai bước này là nền tảng để xử lý `null`, mảng rỗng, chỉ số và sao chép mảng trong Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="one-dimensional-arrays-003">Khởi tạo mảng bằng danh sách giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo mảng bằng danh sách giá trị trong Java

> **Java core — language-basics / Mảng một chiều**  
> **Môi trường:** JDK 17+; ví dụ một file tự chứa, không dùng thư viện ngoài.  
> **Mục tiêu:** Phân biệt cú pháp `{...}` khi khai báo với `new T[]{...}` trong biểu thức, hiểu độ dài được suy ra từ số phần tử, kiểu thành phần, danh sách rỗng, thứ tự đánh giá và các lỗi biên dịch thường gặp.

## 1. Cú pháp ngắn `{...}` ngay tại khai báo

Khi **khai báo biến mảng và khởi tạo ngay trong cùng câu lệnh**, ta có thể viết các phần tử trong dấu ngoặc nhọn:

~~~java
int[] scores = {8, 10, 6};
String[] names = {"An", "Binh", "Chi"};
boolean[] flags = {true, false, true};
~~~

Java tạo đối tượng mảng mới, gán lần lượt các giá trị vào vị trí có chỉ số bắt đầu từ 0. `scores` có kiểu `int[]`, kiểu thành phần `int` và `scores.length == 3`.

~~~java
System.out.println(scores[0]); // 8
System.out.println(scores[2]); // 6
System.out.println(scores.length); // 3
~~~

Cú pháp đầy đủ tương đương:

~~~java
int[] scores = new int[] {8, 10, 6};
~~~

**Lưu ý:** Không khai báo kích thước trong `new int[] { ... }`, vì độ dài được xác định từ số biểu thức khởi tạo. Mảng đã tạo có `length` cố định; thay phần tử bằng `scores[i] = value` là hợp lệ, còn kéo dài chính đối tượng mảng là không thể.

## 2. Khi nào phải viết `new T[] {...}`?

Dạng `{...}` **đơn độc** chỉ hợp lệ trong *array initializer* gắn trực tiếp với khai báo hoặc các ngữ cảnh khởi tạo thành phần mảng thích hợp. Khi biểu thức cần tạo mảng mới trong phép gán sau khai báo, đối số phương thức hoặc giá trị `return`, hãy ghi rõ `new T[] { ... }`.

~~~java
int[] values;
values = new int[] {2, 4, 6};      // ĐÚNG.

static int total(int[] numbers) {
    int sum = 0;
    for (int number : numbers) {
        sum += number;
    }
    return sum;
}

// Gọi:
// int result = total(new int[] {1, 2, 3});
// result == 6.
~~~

Phương thức trả về mảng:

~~~java
static int[] createPair() {
    return new int[] {4, 6};
}
~~~

Các dạng sau **không biên dịch** (chỉ minh họa, không đưa vào chương trình chạy):

~~~java
// int[] a;
// a = {1, 2};                   // Thiếu "new int[]" ở phép gán.
// total({1, 2});                // Thiếu "new int[]" ở đối số.
// int[] wrong = new int[2] {1, 2}; // Vừa chỉ kích thước vừa khởi tạo.
~~~

Bảng ghi nhớ:

| Ngữ cảnh | Cú pháp hợp lệ |
| --- | --- |
| Khai báo + khởi tạo | `int[] a = {1, 2};` |
| Khai báo + khởi tạo tường minh | `int[] a = new int[] {1, 2};` |
| Gán lại sau khai báo | `a = new int[] {3, 4};` |
| Đối số phương thức nhận `int[]` | `method(new int[] {1, 2});` |
| Trả về `int[]` | `return new int[] {1, 2};` |
| Khai báo có số phần tử, chưa ghi giá trị | `new int[2]` (nhận hai số 0 mặc định) |

## 3. Số phần tử, mảng rỗng và dấu phẩy cuối

**Số biểu thức trong danh sách quyết định chiều dài mảng**, không phụ thuộc vào giá trị của phần tử.

~~~java
int[] three = {5, 5, 5}; // length = 3.
int[] empty = {};        // length = 0.
int[] two = {7, 9,};     // Dấu phẩy sau phần tử cuối là hợp lệ.
~~~

Dấu phẩy cuối có thể hữu ích khi chỉnh sửa danh sách nhiều dòng. Nó **không tạo thêm phần tử**.

`new int[] {}` và `new int[0]` đều tạo một đối tượng mảng dài 0; chúng không phải `null`:

~~~java
int[] nothing = {};
System.out.println(nothing.length); // 0
// nothing[0]; // Nếu truy cập: ArrayIndexOutOfBoundsException.
~~~

Giá trị mặc định khác với giá trị khai báo rõ ràng:

~~~java
int[] defaults = new int[3]; // [0, 0, 0]
int[] given = {0, 0, 0};     // [0, 0, 0]
~~~

Hai mảng trên có cùng **nội dung khởi đầu**, nhưng là **hai đối tượng khác nhau**. Không dùng `==` để so sánh nội dung mảng; khi cần có thể dùng `java.util.Arrays.equals(defaults, given)`.

## 4. Quy tắc kiểu thành phần

Mỗi phần tử phải **tương thích với kiểu thành phần** theo quy tắc gán của Java:

~~~java
long[] distances = {1, 2, 3};    // Hợp lệ: int -> long.
double[] rates = {1, 2.5, 3};    // Hợp lệ: giá trị số phù hợp.
String[] words = {"A", null, "C"}; // Hợp lệ: tham chiếu String hoặc null.
Object[] items = {"text", Integer.valueOf(7), null};
~~~

Một mảng tham chiếu vẫn có **một kiểu thành phần**, nhưng có thể chứa nhiều lớp đối tượng runtime nếu đều gán được cho kiểu đó: `Object[]` ở trên chứa `String`, `Integer` và `null`.

Ví dụ sai:

~~~java
// int[] wrong = {1, "two"}; // String không gán được cho int.
// String[] wrong2 = {"A", Integer.valueOf(2)}; // Integer không là String.
~~~

Một số hằng nguyên `int` nằm trong phạm vi của kiểu nhỏ hơn có thể được gán qua *constant narrowing*; nhưng hằng vượt miền thì không:

~~~java
byte[] bytes = {0, 127}; // Hợp lệ.
// byte[] wrong = {128}; // Không biên dịch: vượt miền byte.
~~~

Với mảng kiểu tham chiếu, kiểu thành phần thực còn được kiểm tra khi lưu giá trị ở **runtime** (ví dụ `String[]` gán qua `Object[]` có thể ném `ArrayStoreException` nếu cố lưu một `Integer`). Danh sách khởi tạo không bỏ qua cơ chế kiểm tra kiểu này.

## 5. Danh sách chứa biểu thức và thứ tự đánh giá

Mỗi phần tử không bắt buộc là literal. Java có thể đánh giá các biến, biểu thức số học và lời gọi phương thức **từ trái sang phải** trong danh sách:

~~~java
int base = 4;
int[] computed = {base, base * 2, base + 5};
// computed = [4, 8, 9].
~~~

Ví dụ có hiệu ứng phụ để thấy rõ thứ tự:

~~~java
static int counter = 0;

static int next() {
    counter++;
    return counter * 10;
}

// Trong một phương thức:
// int[] values = {next(), next(), next()};
// values = [10, 20, 30]; counter = 3.
~~~

Lưu ý rằng nếu một biểu thức ném ngoại lệ, các phần tử còn lại chưa được đánh giá sẽ không được khởi tạo qua các biểu thức đó. Trong mã ứng dụng, hạn chế side effects phức tạp bên trong danh sách khởi tạo để dễ đọc và dễ kiểm thử.

## 6. `var`, tham chiếu và thay đổi mảng sau khi khởi tạo

Java 17 hỗ trợ suy luận kiểu của biến cục bộ bằng `var` khi biểu thức bên phải có kiểu rõ ràng:

~~~java
var numbers = new int[] {2, 4, 6}; // Suy ra int[].
// var wrong = {2, 4, 6};          // Không biên dịch.
~~~

Không có kiểu đích đủ rõ từ riêng `{...}`, nên không viết `var numbers = { ... };`. Với mã nhập môn, `int[] numbers = { ... };` thường ngắn và rõ nhất.

Danh sách khởi tạo **tạo đối tượng mảng mới**, nhưng giá trị của mảng vẫn có thể được cập nhật:

~~~java
int[] a = {2, 4};
a[0] = 9; // a thành [9, 4].

int[] alias = a;
alias[1] = 7; // a thành [9, 7], vì alias trỏ cùng mảng.

a = new int[] {1, 3, 5}; // a trỏ mảng mới, dài 3.
// alias vẫn trỏ mảng cũ [9, 7], dài 2.
~~~

Để sao chép thành một mảng khác, dùng `a.clone()` hoặc `Arrays.copyOf(...)`. Với mảng tham chiếu chứa đối tượng mutable, cách sao chép này chỉ sao chép **tham chiếu phần tử**, không sao chép sâu các đối tượng.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **ArrayInitializerDemo.java**:

~~~java
import java.util.Arrays;

public final class ArrayInitializerDemo {
    private static int counter = 0;

    private static int next() {
        counter++;
        return counter * 10;
    }

    private static int total(int[] values) {
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }

    private static int[] createPair() {
        return new int[] {4, 6};
    }

    public static void main(String[] args) {
        int[] numbers = {2, 4, 6};
        System.out.println("declared=" + Arrays.toString(numbers));
        System.out.println("length=" + numbers.length);

        int[] empty = {};
        System.out.println("emptyLength=" + empty.length);

        int[] trailing = {8, 9,};
        System.out.println("trailing=" + Arrays.toString(trailing));

        int[] assigned;
        assigned = new int[] {3, 5};
        System.out.println("assigned=" + Arrays.toString(assigned));

        System.out.println("argumentSum="
                + total(new int[] {1, 2, 3}));
        System.out.println("returned=" + Arrays.toString(createPair()));

        int[] defaults = new int[3];
        int[] explicitZeros = {0, 0, 0};
        System.out.println("defaults=" + Arrays.toString(defaults));
        System.out.println("contentsEqual="
                + Arrays.equals(defaults, explicitZeros));
        System.out.println("sameObject=" + (defaults == explicitZeros));

        long[] widened = {1, 2, 3};
        System.out.println("longValues=" + Arrays.toString(widened));

        String[] words = {"A", null, "C"};
        System.out.println("words=" + Arrays.toString(words));

        Object[] mixed = {"x", Integer.valueOf(7), null};
        System.out.println("mixed=" + Arrays.toString(mixed));

        var inferred = new int[] {7, 8};
        System.out.println("inferredComponent="
                + inferred.getClass().getComponentType().getTypeName());

        int[] evaluated = {next(), next(), next()};
        System.out.println("evaluation=" + Arrays.toString(evaluated));
        System.out.println("calls=" + counter);

        numbers[0] = 99;
        int[] alias = numbers;
        alias[1] = 42;
        System.out.println("mutated=" + Arrays.toString(numbers));

        int[] copy = numbers.clone();
        copy[0] = 123;
        System.out.println("originalAfterCopy=" + Arrays.toString(numbers));
        System.out.println("copy=" + Arrays.toString(copy));

        numbers = new int[] {1, 3, 5};
        System.out.println("reassigned=" + Arrays.toString(numbers));
        System.out.println("aliasOld=" + Arrays.toString(alias));
    }
}
~~~

Tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ArrayInitializerDemo.java
java ArrayInitializerDemo
~~~

**Expected stdout chính xác:**

~~~text
declared=[2, 4, 6]
length=3
emptyLength=0
trailing=[8, 9]
assigned=[3, 5]
argumentSum=6
returned=[4, 6]
defaults=[0, 0, 0]
contentsEqual=true
sameObject=false
longValues=[1, 2, 3]
words=[A, null, C]
mixed=[x, 7, null]
inferredComponent=int
evaluation=[10, 20, 30]
calls=3
mutated=[99, 42, 6]
originalAfterCopy=[99, 42, 6]
copy=[123, 42, 6]
reassigned=[1, 3, 5]
aliasOld=[99, 42, 6]
~~~

Chú ý `trailing=[8, 9]` (dấu phẩy cuối không tăng độ dài), `sameObject=false` (cùng nội dung không đồng nghĩa cùng đối tượng), `calls=3` (biểu thức được đánh giá theo thứ tự) và `aliasOld` (gán lại biến không làm mảng cũ đổi chiều dài).

## 8. Lỗi thường gặp và bài tập

| Trường hợp | Cách xử lý |
| --- | --- |
| Viết `a = {1, 2};` sau khi khai báo | Dùng `a = new int[] {1, 2};` |
| Viết `method({1, 2})` | Truyền `new int[] {1, 2}` |
| Viết `new int[2] {1, 2}` | Bỏ kích thước: `new int[] {1, 2}` |
| Cho rằng `{1, 2,}` dài 3 | Dấu phẩy cuối không tạo vị trí |
| Dùng `var a = {1, 2};` | Dùng `var a = new int[] {1, 2};` |
| Trộn phần tử không tương thích | Chọn kiểu thành phần phù hợp hoặc sửa danh sách |
| Nhầm `new int[3]` với `{3}` | Một bên là ba phần tử mặc định 0; bên kia là một phần tử giá trị 3 |

**Bài 1.** Khai báo mảng `double[]` bằng `{1.5, 2.0, 3.5}` và tính tổng; kiểm tra chiều dài được suy ra.

**Bài 2.** Tạo mảng rỗng bằng hai cách `{}` và `new int[0]`; so sánh độ dài và tham chiếu.

**Bài 3.** Viết phương thức nhận `String[]`, gọi bằng `new String[] {"A", "B"}` và thử viết trực tiếp `{"A", "B"}` để nhận ra lỗi cú pháp.

**Bài 4.** Khởi tạo bằng các lời gọi có side effects; xác định thứ tự và trạng thái biến sau các phần tử.

**Bài 5.** Khai báo `int[] a = {1, 2};`, lấy `alias = a` rồi gán `a = new int[] {3, 4, 5};`. Dự đoán các giá trị và `length` của hai tham chiếu.

### Ghi nhớ

**`T[] a = {v1, v2};`** là dạng khởi tạo ngắn **ngay khi khai báo**. Khi cần tạo mảng ở ngữ cảnh biểu thức, dùng **`new T[] {v1, v2}`**. Độ dài bằng **số phần tử được liệt kê**, kiểu từng phần tử phải gán được cho kiểu thành phần, và danh sách có thể rỗng hoặc có dấu phẩy cuối. Sau khi khởi tạo, đối tượng mảng có chiều dài cố định, nhưng nội dung có thể thay đổi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="one-dimensional-arrays-004">Chỉ số bắt đầu từ 0 và trường length</a>

<details>
<summary>Xem chi tiết</summary>

# Chỉ số bắt đầu từ 0 và trường `length` — Mảng một chiều Java 17+

## 1. Mục tiêu

Trong Java, **mảng (*array*) đánh số phần tử từ `0`**, không phải từ `1`. Mảng có `n` phần tử thì:

- Phần tử đầu tiên nằm ở `array[0]`, nếu `n > 0`.
- Phần tử cuối cùng nằm ở `array[n - 1]`, nếu `n > 0`.
- Chỉ số hợp lệ là các số nguyên trong khoảng **`0 <= index && index < array.length`**.
- `array.length` là **trường** chứa số phần tử của mảng, **không phải phương thức**; không có cặp ngoặc `()`.
- Nếu `array.length == 0`, **không tồn tại chỉ số hợp lệ**.

Ví dụ:

~~~java
int[] numbers = {7, 0, -3, 5};
System.out.println(numbers.length); // 4
System.out.println(numbers[0]);      // 7
System.out.println(numbers[3]);      // 5
~~~

Mảng này có bốn phần tử nhưng phần tử cuối có chỉ số **3**, không phải 4.

## 2. Hình dung cấu trúc chỉ số

~~~text
int[] numbers = { 7, 0, -3, 5 };

             +----+----+----+----+
Giá trị      |  7 |  0 | -3 |  5 |
             +----+----+----+----+
Chỉ số       |  0 |  1 |  2 |  3 |
             +----+----+----+----+

numbers.length = 4
Chỉ số đầu     = 0
Chỉ số cuối    = length - 1 = 3
~~~

| Biểu thức | Kết quả | Giải thích |
|---|---:|---|
| `numbers.length` | `4` | Mảng chứa bốn phần tử |
| `numbers[0]` | `7` | Giá trị đầu tiên |
| `numbers[1]` | `0` | Giá trị thứ hai |
| `numbers[2]` | `-3` | Giá trị thứ ba |
| `numbers[3]` | `5` | Giá trị cuối |
| `numbers[-1]` | Lỗi lúc chạy | Chỉ số âm |
| `numbers[4]` | Lỗi lúc chạy | Chỉ số bằng `length` không hợp lệ |

**Ghi nhớ:** số phần tử và chỉ số cuối là hai đại lượng khác nhau. Với mảng không rỗng, **`lastIndex = length - 1`**.

## 3. Trường `length` có đặc điểm gì?

Với mọi mảng trong Java:

~~~java
int[] values = {4, 8};
int size = values.length; // Hợp lệ.
~~~

Trường `length` là một thành viên **`public final int` của kiểu mảng**. Vì là trường `final`, không được gán lại:

~~~java
// values.length = 10; // LỖI BIÊN DỊCH: không gán được final field.
// int x = values.length(); // LỖI BIÊN DỊCH: mảng không có method length().
~~~

**Độ dài cố định sau khi tạo mảng.** Muốn có mảng dài hơn hoặc ngắn hơn, phải tạo **mảng mới**, có thể dùng `Arrays.copyOf`:

~~~java
int[] oldArray = {1, 2, 3};
int[] larger = java.util.Arrays.copyOf(oldArray, 5);
System.out.println(java.util.Arrays.toString(larger));
~~~

**Exact expected stdout:**

~~~text
[1, 2, 3, 0, 0]
~~~

`larger` là mảng mới dài 5; `oldArray` vẫn dài 3. Trường `length` không đồng nghĩa với số lượng phần tử “đã được điền dữ liệu hữu ích”. Một mảng `new int[100]` luôn có `length == 100`, dù tất cả phần tử còn là 0 mặc định.

## 4. Điều kiện kiểm tra chỉ số an toàn

Trước khi dùng `array[index]` với `index` đến từ người dùng hoặc từ phép tính, hãy bảo đảm:

~~~java
static boolean validIndex(int[] array, int index) {
    return array != null
            && index >= 0
            && index < array.length;
}
~~~

Thứ tự các điều kiện có ý nghĩa:

1. Kiểm tra mảng **khác null** trước.
2. Chặn chỉ số âm.
3. Chặn chỉ số **bằng hoặc lớn hơn `length`**.

Toán tử `&&` **ngắn mạch**: nếu `array == null` thì không truy cập `array.length`. Đừng chỉ kiểm tra `index < array.length` vì `index = -1` vẫn thỏa điều kiện đó đối với mảng không rỗng.

**Ví dụ kết quả khi mảng dài 4:**

| `index` | `index >= 0` | `index < 4` | Hợp lệ |
|---:|---|---|---|
| `-1` | false | true | Không |
| `0` | true | true | Có |
| `3` | true | true | Có |
| `4` | true | false | Không |
| `5` | true | false | Không |

Có thể để runtime ném ngoại lệ tự nhiên nếu truy cập chỉ số không hợp lệ là lỗi lập trình; nhưng với chỉ số do người dùng cung cấp, xác thực đầu vào thường giúp báo lỗi rõ ràng hơn.

## 5. Lỗi vượt chỉ số `ArrayIndexOutOfBoundsException`

Các biểu thức sau **biên dịch được**, nhưng gây ngoại lệ lúc chạy:

~~~java
int[] numbers = {7, 0, -3, 5};

// System.out.println(numbers[-1]); // ArrayIndexOutOfBoundsException.
// System.out.println(numbers[4]);  // ArrayIndexOutOfBoundsException.
~~~

`ArrayIndexOutOfBoundsException` là ngoại lệ **runtime** do chỉ số âm hoặc chỉ số từ `length` trở lên. Java thực hiện kiểm tra biên để bảo vệ bộ nhớ của mảng.

Ví dụ bắt lỗi minh họa:

~~~java
try {
    System.out.println(numbers[numbers.length]);
} catch (ArrayIndexOutOfBoundsException ex) {
    System.out.println("index == length is invalid");
}
~~~

**Exact expected stdout:**

~~~text
index == length is invalid
~~~

**Không dùng bắt ngoại lệ để thay vòng lặp đúng biên.** Trong code thông thường, sửa `i <= numbers.length` thành `i < numbers.length`; điều đó rõ ràng hơn và tránh phát sinh ngoại lệ lặp đi lặp lại.

## 6. Duyệt từ đầu đến cuối bằng `for`

Cách phổ biến:

~~~java
int[] data = {7, 0, -3, 5};
for (int i = 0; i < data.length; i++) {
    System.out.println("index=" + i + ",value=" + data[i]);
}
~~~

**Exact expected stdout:**

~~~text
index=0,value=7
index=1,value=0
index=2,value=-3
index=3,value=5
~~~

Phân tích ba thành phần:

- **Khởi tạo:** `int i = 0`, bắt đầu từ chỉ số đầu.
- **Điều kiện:** `i < data.length`, chỉ truy cập khi chỉ số còn hợp lệ.
- **Cập nhật:** `i++`, chuyển sang phần tử kế tiếp.

Nếu dùng `i <= data.length`, body sẽ chạy khi `i == 4`; biểu thức `data[4]` vượt biên. Đây là lỗi **off-by-one** điển hình.

### Duyệt ngược

~~~java
for (int i = data.length - 1; i >= 0; i--) {
    System.out.println(data[i]);
}
~~~

**Exact expected stdout:**

~~~text
5
-3
0
7
~~~

Với mảng rỗng, `data.length - 1` là `-1`, điều kiện `i >= 0` false ngay nên body không chạy.

## 7. Mảng rỗng: `length == 0`

~~~java
int[] empty = new int[0];
System.out.println(empty.length);
~~~

**Exact expected stdout:**

~~~text
0
~~~

Mảng rỗng là một **mảng hợp lệ**, khác với `null`:

- `empty.length == 0`.
- Không có `empty[0]`; truy cập sẽ ném `ArrayIndexOutOfBoundsException`.
- `for (int i = 0; i < empty.length; i++)` có **0 lượt**.
- `for (int value : empty)` cũng có **0 lượt**.
- Muốn lấy phần tử đầu hoặc cuối, phải kiểm tra `empty.length > 0` trước.

~~~java
if (empty.length == 0) {
    System.out.println("EMPTY");
} else {
    System.out.println(empty[0]);
}
~~~

**Exact expected stdout:**

~~~text
EMPTY
~~~

**Không giả định** mảng luôn có ít nhất một phần tử chỉ vì biến được khai báo `int[]`.

## 8. `array.length`, `String.length()` và `List.size()` khác nhau

Dễ nhầm ba cách lấy kích thước:

| Đối tượng | Cách viết | Bản chất |
|---|---|---|
| Mảng `int[]`, `String[]`, `Object[]` | `array.length` | **Trường** `int` của mảng |
| Chuỗi `String` | `text.length()` | **Phương thức** của String |
| `List` | `list.size()` | **Phương thức** của List |

~~~java
int[] numbers = {1, 2, 3};
String text = "Java";
java.util.List<String> words = java.util.List.of("a", "b");

System.out.println(numbers.length);
System.out.println(text.length());
System.out.println(words.size());
~~~

**Exact expected stdout:**

~~~text
3
4
2
~~~

**Lưu ý về `String`:** `String.length()` đo số **đơn vị mã UTF-16**, không nhất thiết là số ký tự người dùng nhìn thấy. Đó là quy tắc của String, **khác hoàn toàn** việc đếm số phần tử mảng qua `array.length`.

Dù các phép truy cập kích thước khác nhau về cú pháp, khi duyệt bằng chỉ số vẫn cần bảo đảm cận dưới 0 và cận trên phù hợp với cấu trúc dữ liệu.

## 9. Chương trình Java 17+ tự chứa, chạy bằng CLI

Tạo file đúng tên **`ArrayIndexLengthDemo.java`**:

~~~java
import java.util.Arrays;
import java.util.List;

public final class ArrayIndexLengthDemo {
    private ArrayIndexLengthDemo() {}

    static boolean validIndex(int[] array, int index) {
        return array != null && index >= 0 && index < array.length;
    }

    static int sum(int[] array) {
        int total = 0;
        for (int i = 0; i < array.length; i++) {
            total += array[i];
        }
        return total;
    }

    public static void main(String[] args) {
        int[] numbers = {7, 0, -3, 5};

        System.out.println("length=" + numbers.length);
        System.out.println("first=" + numbers[0]);
        System.out.println("last=" + numbers[numbers.length - 1]);

        for (int i = 0; i < numbers.length; i++) {
            System.out.println("index=" + i + ",value=" + numbers[i]);
        }
        System.out.println("sum=" + sum(numbers));

        System.out.println("valid -1=" + validIndex(numbers, -1));
        System.out.println("valid 0=" + validIndex(numbers, 0));
        System.out.println("valid 3=" + validIndex(numbers, 3));
        System.out.println("valid 4=" + validIndex(numbers, 4));
        System.out.println("valid null=" + validIndex(null, 0));

        try {
            System.out.println(numbers[-1]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("negative index=OUT_OF_BOUNDS");
        }
        try {
            System.out.println(numbers[numbers.length]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("length index=OUT_OF_BOUNDS");
        }

        int[] zeros = new int[3];
        System.out.println("new array=" + Arrays.toString(zeros));
        int[] larger = Arrays.copyOf(numbers, 6);
        System.out.println("copied length=" + larger.length);
        System.out.println("copied=" + Arrays.toString(larger));
        System.out.println("original length=" + numbers.length);

        int[] empty = new int[0];
        System.out.println("empty length=" + empty.length);
        int emptyVisits = 0;
        for (int i = 0; i < empty.length; i++) {
            emptyVisits++;
        }
        System.out.println("empty visits=" + emptyVisits);

        int[] absent = null;
        System.out.println("null reference=" + (absent == null));

        String text = "Java";
        List<String> words = List.of("A", "B");
        System.out.println("string length()=" + text.length());
        System.out.println("list size()=" + words.size());

        int[][] rows = {{1, 2}, {3}, {}};
        System.out.println("row count=" + rows.length);
        for (int row = 0; row < rows.length; row++) {
            System.out.println("row " + row + " length=" + rows[row].length);
        }

        for (int i = numbers.length - 1; i >= 0; i--) {
            System.out.println("reverse index=" + i + ",value=" + numbers[i]);
        }
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal ở thư mục có file. Không dùng Maven, Gradle hay preview feature.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ArrayIndexLengthDemo.java
java -cp . ArrayIndexLengthDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ArrayIndexLengthDemo.java
java -cp . ArrayIndexLengthDemo
~~~

### Exact expected stdout

~~~text
length=4
first=7
last=5
index=0,value=7
index=1,value=0
index=2,value=-3
index=3,value=5
sum=9
valid -1=false
valid 0=true
valid 3=true
valid 4=false
valid null=false
negative index=OUT_OF_BOUNDS
length index=OUT_OF_BOUNDS
new array=[0, 0, 0]
copied length=6
copied=[7, 0, -3, 5, 0, 0]
original length=4
empty length=0
empty visits=0
null reference=true
string length()=4
list size()=2
row count=3
row 0 length=2
row 1 length=1
row 2 length=0
reverse index=3,value=5
reverse index=2,value=-3
reverse index=1,value=0
reverse index=0,value=7
~~~

**Đối chiếu:** mảng `numbers` có `length == 4`, nhưng chỉ số cuối là 3; tổng bằng 9. Chỉ số -1 và 4 không hợp lệ; `new int[3]` có ba số 0 mặc định. `Arrays.copyOf` tạo mảng mới dài 6 và giữ nguyên độ dài mảng gốc. Mảng rỗng không có lượt duyệt; tham chiếu `null` khác mảng rỗng. Các hàng mảng hai chiều có `length` riêng.

## 10. Sai lầm thường gặp

| Sai lầm | Hậu quả | Cách sửa |
|---|---|---|
| Cho chỉ số bắt đầu từ 1 | Bỏ qua phần tử đầu | Bắt đầu `i = 0` |
| Cho chỉ số cuối bằng `length` | `ArrayIndexOutOfBoundsException` | Cuối là `length - 1` khi mảng không rỗng |
| `i <= array.length` khi truy cập `array[i]` | Vượt biên tại `i == length` | `i < array.length` |
| Chỉ kiểm tra `index < length` | Chỉ số âm vẫn lọt | Thêm `index >= 0` |
| Dùng `array.length()` | Lỗi biên dịch | `array.length` |
| Dùng `array.length = n` | Lỗi vì trường final | Tạo mảng mới |
| Lấy `array[0]` khi `length == 0` | Vượt biên | Kiểm tra mảng không rỗng |
| Dùng `array.length` khi `array == null` | `NullPointerException` | Kiểm tra null trước |
| Đồng nhất `length` với số phần tử không rỗng | Sai nghĩa độ dài | `length` là số vị trí được cấp phát |
| Nhầm `String.length()` với `array.length` | Lỗi cú pháp/kiểu | Nhớ trường của array và method của String |
| Dùng `rows.length` làm số cột mọi hàng | Sai với jagged array | `rows[row].length` |
| Bắt ngoại lệ như cách duyệt mảng | Code khó đọc, tốn chi phí lỗi | Kiểm tra cận trước |

## 11. Bài tập có đáp án

### Bài A — Chỉ số hợp lệ

Cho `int[] a = {3, 6, 9, 12, 15};`. Hỏi `a.length`, chỉ số đầu, chỉ số cuối và `a[4]`?

**Đáp án:** `length = 5`, chỉ số đầu `0`, cuối `4`, `a[4] = 15`. `a[5]` không hợp lệ.

### Bài B — Sửa lỗi off-by-one

~~~java
int[] a = {2, 4, 6};
// Sai: for (int i = 0; i <= a.length; i++) System.out.println(a[i]);
~~~

**Đáp án:**

~~~java
for (int i = 0; i < a.length; i++) {
    System.out.println(a[i]);
}
~~~

**Expected stdout:**

~~~text
2
4
6
~~~

### Bài C — Mảng rỗng

~~~java
int[] a = {};
System.out.println(a.length);
~~~

**Đáp án:** in `0`. Không thể đọc `a[0]`, vì mảng không có phần tử.

### Bài D — Kiểm tra chỉ số âm

Vì sao `if (index < a.length) ...` chưa đủ?

**Đáp án:** khi `index = -1` và `a.length = 3`, điều kiện vẫn true nhưng `a[-1]` không hợp lệ. Cần `index >= 0 && index < a.length`.

### Bài E — Phân biệt ba cách đọc kích thước

Hoàn thành các lệnh lấy kích thước từ `int[] a`, `String s`, `List<String> list`.

**Đáp án:**

~~~java
int arraySize = a.length;
int stringUnits = s.length();
int listSize = list.size();
~~~

### Bài F — Tạo mảng mới dài hơn

~~~java
int[] a = {1, 2};
int[] b = Arrays.copyOf(a, 4);
System.out.println(Arrays.toString(a));
System.out.println(Arrays.toString(b));
~~~

**Đáp án:**

~~~text
[1, 2]
[1, 2, 0, 0]
~~~

Mảng `a` không tăng độ dài; `b` là mảng mới.

## 12. Checklist và kết luận

- [ ] Biết `array[0]` là phần tử đầu tiên.
- [ ] Nhớ chỉ số hợp lệ là `0 <= i && i < array.length`.
- [ ] Biết `length - 1` là chỉ số cuối **chỉ khi mảng không rỗng**.
- [ ] Phân biệt số phần tử `length` và giá trị tại chỉ số.
- [ ] Sử dụng `array.length` như một **trường**, không gọi `length()`.
- [ ] Biết không thể đổi `length` sau khi tạo mảng; phải tạo mảng mới.
- [ ] Duyệt đúng biên bằng `for (int i=0; i<a.length; i++)`.
- [ ] Phân biệt `ArrayIndexOutOfBoundsException` với `NullPointerException`.
- [ ] Biết mảng rỗng có `length == 0` và không có chỉ số hợp lệ.
- [ ] Phân biệt `array.length`, `String.length()` và `List.size()`.
- [ ] Chạy `ArrayIndexLengthDemo.java` bằng Java 17+, đối chiếu từng dòng stdout.

**Kết luận:** Trong mảng Java, `length` cho biết **bao nhiêu phần tử**, còn chỉ số bắt đầu từ **0**. Vì thế cận trên hợp lệ là `length - 1`, không phải `length`. Hãy dùng điều kiện `0 <= index && index < array.length`, xử lý mảng rỗng/null đúng cách và nhớ `length` là trường cố định của mảng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="one-dimensional-arrays-005">Giá trị mặc định của phần tử nguyên thủy và tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị mặc định của phần tử nguyên thủy và tham chiếu trong mảng Java

> **Java core — language-basics / Mảng một chiều**  
> **Áp dụng:** Java 17+ (JDK); một chương trình chạy độc lập bằng `javac`/`java`.  
> **Mục tiêu:** Biết chính xác giá trị mà mỗi phần tử nhận được sau `new Type[length]`; phân biệt mảng của kiểu nguyên thủy với mảng của kiểu tham chiếu; nhận ra `null`, phần tử chưa gán tường minh và biến cục bộ chưa khởi tạo.

## 1. Khi tạo mảng, các phần tử được khởi tạo tự động

Một mảng Java là **đối tượng có độ dài cố định** và chứa các phần tử cùng kiểu thành phần. Khi tạo mảng bằng `new` có độ dài, mọi phần tử đều được khởi tạo về **giá trị mặc định của kiểu thành phần** trước khi chương trình đọc chúng:

~~~java
int[] numbers = new int[3];
System.out.println(numbers.length); // 3
System.out.println(numbers[0]);     // 0
System.out.println(numbers[1]);     // 0
System.out.println(numbers[2]);     // 0
~~~

Câu lệnh `new int[3]` tạo ba phần tử được đánh chỉ số `0`, `1` và `2`. Không cần viết vòng lặp để gán từng phần tử bằng 0. Đây là **bảo đảm của ngôn ngữ Java**, không phải hành vi may rủi của bộ nhớ.

**Lưu ý:** Giá trị mặc định phụ thuộc vào **kiểu của phần tử**, không phụ thuộc vào tên mảng, tên biến hoặc kiểu dữ liệu mà bạn dự định gán vào sau này. Việc dùng `new` ở đây tạo mảng mới; việc khai báo một biến tham chiếu mảng **chưa tạo mảng** là tình huống khác.

## 2. Bảng giá trị mặc định của tám kiểu nguyên thủy

Nếu kiểu thành phần của mảng là kiểu nguyên thủy (*primitive*), phần tử ban đầu có giá trị sau:

| Kiểu phần tử | Ví dụ tạo mảng | Giá trị mặc định mỗi phần tử |
| --- | --- | --- |
| `byte` | `new byte[2]` | `0` |
| `short` | `new short[2]` | `0` |
| `int` | `new int[2]` | `0` |
| `long` | `new long[2]` | `0L` (số 0 kiểu `long`) |
| `float` | `new float[2]` | `0.0f` (dương không) |
| `double` | `new double[2]` | `0.0d` (dương không) |
| `char` | `new char[2]` | ký tự có mã Unicode **0**, thường biểu diễn bằng `'\0'` |
| `boolean` | `new boolean[2]` | `false` |

Những ký hiệu `0L`, `0.0f`, `0.0d` mô tả **giá trị và kiểu**, không phải chuỗi văn bản tự chứa trong mảng. Các giá trị số 0.0 mặc định là **dương không** (`+0.0`), không phải `NaN`.

~~~java
long[] counts = new long[2];
boolean[] flags = new boolean[2];
char[] letters = new char[2];

System.out.println(counts[0] == 0L);   // true
System.out.println(flags[0]);          // false
System.out.println((int) letters[0]);  // 0
System.out.println(letters[0] == '\0'); // true
~~~

**Đặc biệt với `char`:** mặc định là ký tự NUL mang mã 0, **không** phải ký tự số '0' (mã Unicode 48), **không** phải dấu cách (mã 32) và **không** phải chữ `null`. Nếu in trực tiếp ký tự NUL, mắt người có thể không thấy gì; hãy dùng ép kiểu `(int) letters[0]` để quan sát giá trị mã.

`boolean[]` nhận `false`; `false` không đồng nghĩa số nguyên 0 và Java không cho đổi qua lại trực tiếp giữa `boolean` và `int` như một số ngôn ngữ khác.

## 3. Mảng kiểu tham chiếu: mỗi phần tử mặc định là `null`

Với kiểu thành phần là **tham chiếu** như `String`, `Integer` hoặc lớp tự định nghĩa, phần tử mặc định là `null`:

~~~java
String[] names = new String[3];
Integer[] scores = new Integer[2];

System.out.println(names[0] == null);  // true
System.out.println(scores[0] == null); // true
~~~

Điều này có nghĩa:

- **Bản thân mảng** `names` đã tồn tại vì đã thực hiện `new String[3]`, nên `names.length` bằng 3.
- Nhưng từng **phần tử tham chiếu** như `names[0]` vẫn là `null` cho đến khi được gán tham chiếu phù hợp.
- `new String[3]` **không tự tạo ba đối tượng `String` rỗng**, cũng không đặt các phần tử thành `""`.
- `new Student[2]` **không tự gọi constructor `Student` hai lần**; nó tạo mảng chứa hai tham chiếu `null`.

Ví dụ khác biệt giữa kiểu nguyên thủy và *wrapper*:

~~~java
int[] primitive = new int[2];
Integer[] boxed = new Integer[2];
boolean[] primitiveFlags = new boolean[2];
Boolean[] boxedFlags = new Boolean[2];

System.out.println(primitive[0]);        // 0
System.out.println(boxed[0]);            // null
System.out.println(primitiveFlags[0]);   // false
System.out.println(boxedFlags[0]);       // null
~~~

`Integer` và `Boolean` là **lớp tham chiếu**, không phải `int` và `boolean`. Vì vậy không được giả định phần tử `Integer[]` mặc định bằng 0 hoặc phần tử `Boolean[]` mặc định bằng `false`.

Nếu cố *unbox* một phần tử `Integer[]` đang `null` thành `int`, Java sẽ ném `NullPointerException`.

## 4. Đọc, gán phần tử và xử lý `null` đúng cách

Sau khi tạo mảng, phần tử đã có giá trị mặc định và có thể **đọc** ngay nếu chỉ số hợp lệ:

~~~java
String[] words = new String[3];
System.out.println(words[0]); // In dòng chữ null.

words[1] = "Java";
words[2] = "";
System.out.println(words[1].length()); // 4
System.out.println(words[2].length()); // 0
~~~

`null` và chuỗi rỗng `""` khác nhau: chuỗi rỗng là **một đối tượng `String` có độ dài 0**, còn `null` biểu thị không có đối tượng được tham chiếu.

Đọc trực tiếp `words[0]` hoặc so sánh `words[0] == null` là hợp lệ. Nhưng gọi phương thức instance như `words[0].length()` khi phần tử đó `null` sẽ gây `NullPointerException`.

~~~java
static int safeLength(String[] words, int index) {
    String value = words[index]; // Yêu cầu words khác null và index hợp lệ.
    return value == null ? -1 : value.length();
}
~~~

Hàm `safeLength` trả `-1` để biểu thị phần tử `null` theo hợp đồng ví dụ; nó **không** xử lý mảng `words == null` hoặc chỉ số ngoài phạm vi. Trong ứng dụng, hãy xác định rõ cần phân biệt null, chuỗi rỗng và lỗi chỉ số như thế nào.

**Gán giá trị tường minh** thay thế mặc định tại vị trí đó:

~~~java
int[] numbers = new int[3];       // [0, 0, 0]
numbers[1] = 7;                   // [0, 7, 0]

String[] words = new String[3];   // [null, null, null]
words[1] = "Java";                // [null, Java, null]
~~~

Việc gán `numbers[1]` không thay đổi `numbers[0]` hay `numbers[2]`. Những phần tử chưa được gán tường minh tiếp tục giữ giá trị mặc định cho đến khi có thao tác thay đổi.

## 5. Khởi tạo theo danh sách, `Arrays.fill` và mảng rỗng

Nếu đã biết toàn bộ giá trị ngay lúc tạo, có thể khởi tạo bằng **danh sách**:

~~~java
int[] points = {4, 5, 6};
String[] labels = {"A", null, "C"};
~~~

Ở đây các phần tử lần lượt nhận **giá trị từ danh sách**, không phải chỉ còn giá trị mặc định. Cú pháp thay thế hợp lệ trong biểu thức là `new int[] {4, 5, 6}`.

Nếu muốn mọi phần tử có **cùng giá trị khác mặc định**, có thể dùng `java.util.Arrays.fill`:

~~~java
int[] offsets = new int[3]; // Ban đầu [0, 0, 0].
java.util.Arrays.fill(offsets, -1);
// Bây giờ [-1, -1, -1].
~~~

`Arrays.fill` là thao tác gán **sau khi mảng được tạo**, không làm thay đổi quy tắc giá trị mặc định của Java.

**Mảng độ dài 0** cũng là một đối tượng mảng hợp lệ:

~~~java
int[] empty = new int[0];
System.out.println(empty.length);  // 0
System.out.println(empty == null); // false
// System.out.println(empty[0]);    // Nếu chạy: ArrayIndexOutOfBoundsException.
~~~

Mảng độ dài 0 **không có phần tử**, nên không có giá trị mặc định nào để đọc ở chỉ số 0; khác với một biến tham chiếu mảng bằng `null`.

## 6. Không nhầm phần tử mảng với biến cục bộ chưa khởi tạo

Đây là khác biệt quan trọng trong Java:

~~~java
static void valid() {
    int[] data = new int[2]; // Biến data được gán một tham chiếu mảng mới.
    System.out.println(data[0]); // 0: phần tử mảng đã có mặc định.
}
~~~

Trong khi đó, một **biến cục bộ** không tự được gán giá trị mặc định để có thể đọc ngay:

~~~java
// KHÔNG BIÊN DỊCH nếu bỏ comment:
// static void invalid() {
//     int number;
//     System.out.println(number); // Biến local chưa được gán.
//
//     String[] names;
//     System.out.println(names);  // Biến local names chưa được gán.
// }
~~~

Chú ý hai tầng:

| Tình huống | Có giá trị đọc ngay? | Giải thích |
| --- | --- | --- |
| `int[] data = new int[2]; data[0]` | Có: `0` | Phần tử mảng được mặc định hóa |
| `String[] data = new String[2]; data[0]` | Có: `null` | Phần tử là tham chiếu |
| `int n;` trong phương thức, đọc `n` ngay | Không | Local chưa đạt *definite assignment* |
| `int[] data;` trong phương thức, đọc `data` ngay | Không | Biến tham chiếu local chưa được gán |
| `int[] data = null; data.length` | Không chạy thành công | `data` được gán `null`, gọi `length` gây `NullPointerException` |
| Trường `int number;` trong đối tượng | Có: `0` | Field cũng có giá trị mặc định |
| Trường `String text;` trong đối tượng | Có: `null` | Field tham chiếu mặc định `null` |

**Scope** chỉ xác định tên dùng được ở đâu; **definite assignment** mới xác định biến cục bộ đã được gán chắc chắn trước khi đọc chưa. Giá trị mặc định của **phần tử trong một mảng đã tạo** không thể được dùng để biện minh cho việc đọc một **biến cục bộ chưa khởi tạo**.

## 7. Chương trình Java 17+ độc lập và stdout chính xác

Tạo file **ArrayDefaultValuesDemo.java**:

~~~java
import java.util.Arrays;

public final class ArrayDefaultValuesDemo {
    private static final class Student {
        private final String name;

        Student(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }
    }

    public static void main(String[] args) {
        byte[] bytes = new byte[2];
        short[] shorts = new short[2];
        int[] integers = new int[3];
        long[] longs = new long[2];
        float[] floats = new float[2];
        double[] doubles = new double[2];
        char[] chars = new char[2];
        boolean[] flags = new boolean[2];

        System.out.println("byte=" + Arrays.toString(bytes));
        System.out.println("short=" + Arrays.toString(shorts));
        System.out.println("int=" + Arrays.toString(integers));
        System.out.println("long=" + Arrays.toString(longs));
        System.out.println("float=" + Arrays.toString(floats));
        System.out.println("double=" + Arrays.toString(doubles));
        System.out.println("boolean=" + Arrays.toString(flags));
        System.out.println("charCodes=" + (int) chars[0] + "," + (int) chars[1]);
        System.out.println("charIsNul=" + (chars[0] == '\0'));

        String[] words = new String[3];
        Integer[] boxed = new Integer[2];
        Boolean[] boxedFlags = new Boolean[2];
        Student[] students = new Student[2];

        System.out.println("wordsInitial=" + Arrays.toString(words));
        System.out.println("boxedInitial=" + Arrays.toString(boxed));
        System.out.println("boxedFlagsInitial=" + Arrays.toString(boxedFlags));
        System.out.println("studentsInitial=" + Arrays.toString(students));
        System.out.println("arrayExists=" + (words != null));
        System.out.println("firstWordNull=" + (words[0] == null));

        integers[1] = 7;
        words[1] = "Java";
        boxed[0] = 42;
        students[0] = new Student("Lan");

        System.out.println("intAfter=" + Arrays.toString(integers));
        System.out.println("wordsAfter=" + Arrays.toString(words));
        System.out.println("boxedAfter=" + Arrays.toString(boxed));
        System.out.println("firstStudent=" + students[0].name());
        System.out.println("secondStudentNull=" + (students[1] == null));

        int[] filled = new int[3];
        Arrays.fill(filled, -1);
        System.out.println("filled=" + Arrays.toString(filled));

        int[] explicit = {4, 5};
        System.out.println("explicit=" + Arrays.toString(explicit));

        int[] empty = new int[0];
        System.out.println("emptyLength=" + empty.length);
        System.out.println("emptyIsNull=" + (empty == null));

        try {
            System.out.println("badLength=" + words[0].length());
        } catch (NullPointerException ex) {
            System.out.println("nullElement=NullPointerException");
        }

        try {
            System.out.println("badIndex=" + empty[0]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("emptyIndex=ArrayIndexOutOfBoundsException");
        }

        int[] missing = null;
        System.out.println("missingArrayNull=" + (missing == null));
    }
}
~~~

Biên dịch rồi chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ArrayDefaultValuesDemo.java
java ArrayDefaultValuesDemo
~~~

**Expected stdout chính xác:**

~~~text
byte=[0, 0]
short=[0, 0]
int=[0, 0, 0]
long=[0, 0]
float=[0.0, 0.0]
double=[0.0, 0.0]
boolean=[false, false]
charCodes=0,0
charIsNul=true
wordsInitial=[null, null, null]
boxedInitial=[null, null]
boxedFlagsInitial=[null, null]
studentsInitial=[null, null]
arrayExists=true
firstWordNull=true
intAfter=[0, 7, 0]
wordsAfter=[null, Java, null]
boxedAfter=[42, null]
firstStudent=Lan
secondStudentNull=true
filled=[-1, -1, -1]
explicit=[4, 5]
emptyLength=0
emptyIsNull=false
nullElement=NullPointerException
emptyIndex=ArrayIndexOutOfBoundsException
missingArrayNull=true
~~~

Ví dụ dùng `Arrays.toString(...)` để nhìn rõ các phần tử. Riêng `char[]` được in dưới dạng **mã số** để ký tự NUL không bị nhầm là chuỗi rỗng. `Student[]` bắt đầu bằng hai `null`; chỉ khi gán `new Student("Lan")` vào vị trí 0 thì một đối tượng `Student` mới xuất hiện.

Hai `catch` có đầu ra cố định minh họa **lỗi truy cập phần tử null** và **lỗi chỉ số trên mảng rỗng**, không phụ thuộc vào nội dung thông báo ngoại lệ của JVM.

## 8. Lỗi thường gặp, cách kiểm tra và bài tập

| Hiểu nhầm hoặc lỗi | Cách hiểu đúng |
| --- | --- |
| `new String[3]` tạo 3 chuỗi rỗng | Sai: tạo 3 phần tử tham chiếu `null` |
| `new Integer[3]` tạo 3 số nguyên bằng 0 | Sai: `Integer` là lớp, phần tử là `null` |
| `new boolean[2]` có `null` | Sai: `boolean` nguyên thủy, mặc định `false` |
| `new char[2]` có ký tự `'0'` | Sai: mặc định ký tự NUL có mã 0 |
| Truy cập phần tử `null` qua `.length()` | Có thể gây `NullPointerException` |
| Mảng mới nhưng có độ dài 0 thì bằng `null` | Sai: vẫn là đối tượng, nhưng không có phần tử |
| Local `int number;` tự mặc định thành 0 | Sai: phải gán trước khi đọc |
| `new Student[2]` gọi constructor cho từng `Student` | Sai: chỉ tạo mảng tham chiếu, chưa tạo từng đối tượng |
| Cần giá trị ban đầu khác mặc định cho mọi phần tử | Dùng vòng lặp, danh sách khởi tạo hoặc `Arrays.fill` phù hợp |
| Đọc `data[0]` khi `data == null` | Không có mảng để đánh chỉ số; gây `NullPointerException` |

**Bài 1.** Tạo `double[] temperatures = new double[4]`, in từng phần tử và giải thích vì sao chúng là `0.0`.

**Bài 2.** Tạo `char[] symbols = new char[3]`, in `(int) symbols[0]` và so sánh `symbols[0] == '0'` với `symbols[0] == '\0'`.

**Bài 3.** Tạo `Integer[] ids = new Integer[2]` và `int[] indexes = new int[2]`. Dự đoán `ids[0]` và `indexes[0]`; thử gán `ids[0] = 9` rồi in lại.

**Bài 4.** Tạo lớp `Book` có constructor nhận tiêu đề; khai báo `Book[] books = new Book[2]`. Tại sao `books[0].title()` chưa gọi được trước khi `books[0] = new Book("Java")`?

**Bài 5.** So sánh `int[] a = new int[0]`, `int[] b = null` và `int[] c = new int[1]` về `length`, `== null` và khả năng đọc `[0]`.

**Bài 6.** Tạo `int[] values = new int[5]`, dùng `Arrays.fill(values, -1)` rồi cập nhật riêng `values[2] = 10`. Dự đoán toàn bộ mảng.

### Ghi nhớ

**Mỗi phần tử của mảng vừa tạo bằng `new Type[length]` đều có giá trị mặc định theo kiểu thành phần:** số nguyên là 0, số thực là +0.0, `char` là NUL (mã 0), `boolean` là `false` và **mọi kiểu tham chiếu là `null`**. Mặc định của **phần tử mảng** không phải quy tắc tự gán cho **biến cục bộ chưa khởi tạo**. Muốn đối tượng ở một phần tử tham chiếu, phải gán một tham chiếu đối tượng thích hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="one-dimensional-arrays-006">Mảng rỗng khác tham chiếu mảng null</a>

<details>
<summary>Xem chi tiết</summary>

# Mảng rỗng khác tham chiếu mảng `null` trong Java

> **Java core — language-basics / Mảng một chiều**  
> **Môi trường:** JDK 17+, một file Java tự chứa, chỉ sử dụng thư viện chuẩn.  
> **Mục tiêu:** Phân biệt mảng có `length == 0` với tham chiếu không trỏ tới mảng (`null`), dự đoán hành vi khi đọc `length`, truy cập phần tử hay duyệt bằng `for-each`, và thiết kế cách xử lý null/rỗng rõ ràng.

## 1. Hai trạng thái hoàn toàn khác nhau

~~~java
int[] empty = new int[0]; // Có đối tượng mảng; length = 0.
int[] alsoEmpty = {};     // Một đối tượng mảng rỗng khác.
int[] missing = null;     // Không trỏ tới đối tượng mảng nào.

System.out.println(empty.length);    // 0
System.out.println(missing == null);  // true
~~~

**Mảng rỗng** tồn tại trong bộ nhớ, có kiểu mảng cụ thể và có thuộc tính `length` bằng 0. **Tham chiếu mảng null** không trỏ tới bất kỳ đối tượng nào; nó không có thuộc tính `length` để truy cập.

Đây không phải khác biệt về **giá trị các phần tử**: mảng rỗng hoàn toàn **không có phần tử**. Mảng `new int[2]` có hai phần tử bằng 0, còn mảng `new String[2]` có hai phần tử bằng `null` — cả hai đều **không rỗng**.

| Khía cạnh | Mảng rỗng `new int[0]` | Tham chiếu `int[] a = null` |
| --- | --- | --- |
| Có đối tượng mảng? | Có | Không |
| `a == null` | `false` | `true` |
| `a.length` | `0` | Ném `NullPointerException` |
| Duyệt `for (int x : a)` | 0 lượt, không lỗi | Ném `NullPointerException` |
| Đọc `a[0]` | `ArrayIndexOutOfBoundsException` | `NullPointerException` |
| `a instanceof int[]` | `true` | `false` |

Khai báo `int[] data;` làm biến cục bộ **chưa được gán**, khác với `int[] data = null;`. Java không cho đọc một biến cục bộ chưa được khởi tạo.

## 2. Cách tạo mảng rỗng và độ dài cố định

Có thể tạo một mảng rỗng theo nhiều cách:

~~~java
int[] first = new int[0];
int[] second = {};
int[] third = new int[] {};
String[] names = {};
~~~

Mỗi lần thực hiện biểu thức tạo mảng là tạo **một đối tượng mảng mới**. Các đối tượng có thể cùng độ dài và cùng nội dung nhưng không nhất thiết cùng tham chiếu:

~~~java
int[] a = {};
int[] b = {};
System.out.println(a == b);                     // false
System.out.println(java.util.Arrays.equals(a,b)); // true
~~~

`a == b` so sánh **tham chiếu**; `Arrays.equals(a, b)` so sánh **phần tử** (hai mảng rỗng cùng kiểu thành phần có nội dung bằng nhau theo phép so sánh phù hợp).

Độ dài mảng là cố định. Muốn chuyển từ mảng rỗng sang một mảng có hai phần tử, ta phải tạo mảng mới và gán lại biến tham chiếu:

~~~java
int[] data = {};
data = new int[] {5, 7}; // data trỏ đối tượng khác, length = 2.
~~~

Điều này không làm “mở rộng” đối tượng rỗng ban đầu. Nếu có một biến khác đang tham chiếu đối tượng rỗng cũ, nó vẫn trỏ tới mảng dài 0.

## 3. `length`, chỉ số và hai kiểu ngoại lệ

Với một mảng hợp lệ có `n` phần tử, chỉ số hợp lệ là từ `0` đến `n - 1`. Với `n = 0`, **không có chỉ số hợp lệ**:

~~~java
int[] empty = new int[0];
System.out.println(empty.length); // 0
// int x = empty[0]; // Nếu chạy: ArrayIndexOutOfBoundsException.
~~~

Còn với tham chiếu `null`, thao tác cần truy cập đối tượng sẽ không thành công:

~~~java
int[] missing = null;
// int size = missing.length; // Nếu chạy: NullPointerException.
// int x = missing[0];        // Nếu chạy: NullPointerException.
~~~

Đọc `empty.length` hợp lệ vì đối tượng tồn tại; đọc `missing.length` không thể. `ArrayIndexOutOfBoundsException` xảy ra khi **đã có mảng** nhưng chỉ số nằm ngoài phạm vi; `NullPointerException` xảy ra khi cố truy cập qua **tham chiếu null**.

Java không tự chuyển `null` thành mảng rỗng và không tự cho `a[0]` bằng 0 khi mảng không có phần tử.

## 4. Duyệt `for-each` và tích lũy an toàn

~~~java
static long sum(int[] values) {
    long total = 0;
    for (int value : values) {
        total += value;
    }
    return total;
}
~~~

Nếu truyền một mảng rỗng, thân `for-each` chạy **0 lần** và phương thức trả `0`. Nếu truyền `null`, vòng `for-each` sẽ cố lấy nguồn dữ liệu để duyệt và ném `NullPointerException`.

Với dữ liệu có thể null, cần quyết định **hợp đồng phương thức**:

**Cách A — null là lỗi đầu vào (strict):**

~~~java
static long strictSum(int[] values) {
    java.util.Objects.requireNonNull(values, "values");
    long sum = 0;
    for (int value : values) {
        sum += value;
    }
    return sum;
}
~~~

**Cách B — null được coi như không có phần tử (có chủ đích):**

~~~java
static long sumOrZero(int[] values) {
    if (values == null) {
        return 0;
    }
    long sum = 0;
    for (int value : values) {
        sum += value;
    }
    return sum;
}
~~~

Hai lựa chọn có ngữ nghĩa khác nhau: *strict* giúp phát hiện sai hợp đồng sớm; *null-as-empty* có thể tiện cho dữ liệu tùy chọn nhưng cũng có thể che lỗi nguồn. **Đừng tự ý biến null thành mảng rỗng** nếu nghiệp vụ phân biệt “chưa có dữ liệu” và “có dữ liệu nhưng rỗng”.

## 5. Phân biệt ba trạng thái: null, rỗng, có phần tử

Khi cần trả thông báo khác nhau, nên kiểm tra null **trước**:

~~~java
static String classify(int[] values) {
    if (values == null) {
        return "NULL";
    }
    if (values.length == 0) {
        return "EMPTY";
    }
    return "NON_EMPTY:" + values.length;
}
~~~

Thứ tự trên vừa tránh `NullPointerException`, vừa giữ rõ ý nghĩa trạng thái:

| Đầu vào | Giá trị trả về |
| --- | --- |
| `null` | `"NULL"` |
| `new int[0]` | `"EMPTY"` |
| `new int[] {0}` | `"NON_EMPTY:1"` |
| `new int[] {4, 5}` | `"NON_EMPTY:2"` |

Nếu chỉ cần tính tổng hay đếm, có thể dùng chính sách null-as-empty. Nếu ứng dụng cần phân biệt dữ liệu chưa được tải với dữ liệu đã tải nhưng không có phần tử, nên giữ hai trạng thái riêng.

## 6. `Arrays.toString`, sao chép và mảng chứa phần tử `null`

`java.util.Arrays.toString(...)` hữu ích khi in nội dung mảng và **xử lý được tham chiếu mảng null**:

~~~java
int[] empty = {};
int[] missing = null;
System.out.println(java.util.Arrays.toString(empty));   // []
System.out.println(java.util.Arrays.toString(missing)); // null
~~~

Dòng thứ hai in chuỗi chữ `null`, **không có nghĩa mảng đã được tạo**. Đó là hành vi của phương thức định dạng, không phải của `missing.length`.

Một mảng tham chiếu có thể chứa giá trị `null` trong các vị trí tồn tại:

~~~java
String[] slots = new String[2];
System.out.println(java.util.Arrays.toString(slots)); // [null, null]
System.out.println(slots.length); // 2.
~~~

`slots` không rỗng dù mỗi phần tử đang null. `slots[0] == null` kiểm tra phần tử đầu tiên; `slots == null` kiểm tra chính **tham chiếu mảng**.

Có thể `clone()` một mảng rỗng để tạo một mảng rỗng **khác**. Nếu cố gọi `missing.clone()` khi `missing == null`, Java ném `NullPointerException`.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **EmptyVsNullArrayDemo.java**:

~~~java
import java.util.Arrays;
import java.util.Objects;

public final class EmptyVsNullArrayDemo {
    private static String classify(int[] values) {
        if (values == null) {
            return "NULL";
        }
        if (values.length == 0) {
            return "EMPTY";
        }
        return "NON_EMPTY:" + values.length;
    }

    private static long strictSum(int[] values) {
        Objects.requireNonNull(values, "values");
        long sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }

    private static long sumOrZero(int[] values) {
        if (values == null) {
            return 0;
        }
        long sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }

    public static void main(String[] args) {
        int[] empty = new int[0];
        int[] anotherEmpty = {};
        int[] missing = null;
        int[] oneZero = {0};
        String[] references = new String[2];

        System.out.println("emptyNull=" + (empty == null));
        System.out.println("missingNull=" + (missing == null));
        System.out.println("emptyLength=" + empty.length);
        System.out.println("emptyIsArray=" + (empty instanceof int[]));
        System.out.println("missingIsArray=" + (missing instanceof int[]));
        System.out.println("differentObjects=" + (empty != anotherEmpty));
        System.out.println("equalContents="
                + Arrays.equals(empty, anotherEmpty));

        System.out.println("classNull=" + classify(missing));
        System.out.println("classEmpty=" + classify(empty));
        System.out.println("classZero=" + classify(oneZero));
        System.out.println("classTwo="
                + classify(new int[] {4, 5}));

        System.out.println("emptyText=" + Arrays.toString(empty));
        System.out.println("missingText=" + Arrays.toString(missing));
        System.out.println("nullElements="
                + Arrays.toString(references));
        System.out.println("referenceLength=" + references.length);

        System.out.println("strictEmpty=" + strictSum(empty));
        System.out.println("strictValues="
                + strictSum(new int[] {3, 5}));
        System.out.println("nullAsEmpty=" + sumOrZero(missing));

        int visits = 0;
        for (int value : empty) {
            visits++;
        }
        System.out.println("emptyVisits=" + visits);

        try {
            int ignored = empty[0];
            System.out.println(ignored);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("emptyIndex=ArrayIndexOutOfBoundsException");
        }

        try {
            System.out.println(missing.length);
        } catch (NullPointerException ex) {
            System.out.println("missingLength=NullPointerException");
        }

        try {
            strictSum(missing);
        } catch (NullPointerException ex) {
            System.out.println("strictMissing=NullPointerException");
        }

        int[] alias = empty;
        empty = new int[] {9, 10};
        System.out.println("newLength=" + empty.length);
        System.out.println("oldAliasLength=" + alias.length);
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 EmptyVsNullArrayDemo.java
java EmptyVsNullArrayDemo
~~~

**Expected stdout chính xác:**

~~~text
emptyNull=false
missingNull=true
emptyLength=0
emptyIsArray=true
missingIsArray=false
differentObjects=true
equalContents=true
classNull=NULL
classEmpty=EMPTY
classZero=NON_EMPTY:1
classTwo=NON_EMPTY:2
emptyText=[]
missingText=null
nullElements=[null, null]
referenceLength=2
strictEmpty=0
strictValues=8
nullAsEmpty=0
emptyVisits=0
emptyIndex=ArrayIndexOutOfBoundsException
missingLength=NullPointerException
strictMissing=NullPointerException
newLength=2
oldAliasLength=0
~~~

`differentObjects=true` minh họa hai mảng rỗng có thể khác tham chiếu; `nullElements` cho thấy mảng dài 2 vẫn có thể chứa các phần tử null. `emptyIndex` và `missingLength` phân biệt rõ hai ngoại lệ; `oldAliasLength=0` chứng minh gán biến sang một mảng khác không biến đổi mảng cũ.

## 8. Sai lầm phổ biến và bài tập

| Hiểu lầm / lỗi | Cách hiểu đúng |
| --- | --- |
| `new int[0] == null` | Sai: mảng rỗng vẫn là một đối tượng |
| Có thể gọi `missing.length` | Sai: tham chiếu null gây `NullPointerException` |
| `empty[0]` có giá trị mặc định 0 | Sai: không tồn tại phần tử số 0; truy cập vượt chỉ số |
| `new String[2]` là mảng rỗng vì phần tử đều null | Sai: có hai phần tử, `length == 2` |
| `for-each` trên null bỏ qua như trên mảng rỗng | Sai: null gây `NullPointerException` |
| `Arrays.toString(nullArray)` in `null` tức là đã tạo mảng | Sai: đó chỉ là chuỗi định dạng tham chiếu null |
| Luôn thay null bằng rỗng | Chỉ khi hợp đồng nghiệp vụ cho phép |

**Bài 1.** Tạo `int[] a = new int[0]` và `int[] b = null`. Dự đoán `a == null`, `b == null`, `a.length` và `b.length`.

**Bài 2.** So sánh `new int[0]`, `new int[1]`, `new String[2]` về `length` và các giá trị mặc định.

**Bài 3.** Viết hàm trả `"NULL"`, `"EMPTY"` hoặc `"NON_EMPTY"` để phân biệt ba trạng thái mà không gây NPE.

**Bài 4.** Chọn hợp đồng cho hàm tính tổng: từ chối null hay coi null như không có phần tử? Viết cả hai, giải thích vì sao kết quả rỗng là 0.

**Bài 5.** Gán tham chiếu mảng rỗng vào biến `alias`, rồi gán biến ban đầu sang `new int[] {1, 2, 3}`; giải thích vì sao `alias.length` vẫn bằng 0.

### Ghi nhớ

**Mảng rỗng là một đối tượng hợp lệ với `length == 0`; tham chiếu `null` không trỏ tới đối tượng mảng nào.** Vòng `for-each` trên mảng rỗng thực hiện 0 lượt, còn trên null gây NPE. Mảng rỗng không có chỉ số 0. Trong API, hãy quyết định rõ lúc nào null được phép, thay vì vô tình đánh đồng “không có dữ liệu” với “dữ liệu đã có nhưng rỗng”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="one-dimensional-arrays-007">Đọc, cập nhật và duyệt phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc, cập nhật và duyệt phần tử của mảng một chiều trong Java

## 1. Chỉ số, độ dài và giá trị phần tử

Mảng một chiều (`T[]`) chứa một số lượng phần tử **cố định** sau khi được tạo. Các phần tử có cùng kiểu `T`, được truy cập bằng **chỉ số** (`index`) bắt đầu từ **0**. Với mảng `a` dài `n`, các chỉ số hợp lệ là `0` đến `n - 1`.

```java
int[] a = {10, 20, 30};
int first = a[0];          // Đọc: 10
int second = a[1];         // Đọc: 20
a[1] = 25;                 // Cập nhật phần tử thứ hai
int last = a[a.length - 1];// Đọc: 30
int count = a.length;      // 3
```

- `a[i]` **đọc** giá trị tại chỉ số `i` trong một biểu thức; `a[i] = value` **ghi đè** giá trị ở chỉ số đó. Phép gán phải tương thích với kiểu phần tử.
- `a.length` là **trường** của mảng, không phải phương thức `length()` như `String`, và không tự thay đổi khi gán phần tử.
- Phần tử đầu là `a[0]`; phần tử cuối là `a[a.length - 1]` **chỉ khi mảng không rỗng**.
- Với `new int[3]`, ba phần tử mặc định đều là `0`; `new boolean[3]` là `false`; phần tử kiểu tham chiếu như `String[]` mặc định là `null`. Biến cục bộ chưa được gán giá trị không có quyền đọc, dù phần tử trong mảng vừa cấp phát đã có giá trị mặc định.

Đọc hoặc cập nhật một phần tử theo chỉ số hợp lệ có độ phức tạp thời gian **O(1)**. Mảng không tự tăng/giảm số phần tử; để có kích thước mới cần tạo mảng mới (chẳng hạn `Arrays.copyOf`).

## 2. Các cách duyệt mảng

| Cách duyệt | Dùng khi nào? | Có thể cập nhật phần tử ngay trong vòng lặp? |
| --- | --- | --- |
| `for (int i = 0; i < a.length; i++)` | Cần chỉ số, duyệt xuôi, thay giá trị, lọc theo vị trí | Có: `a[i] = ...` |
| `for (int i = a.length - 1; i >= 0; i--)` | Duyệt ngược từ cuối về đầu | Có: `a[i] = ...` |
| `while (i < a.length)` | Điều kiện lặp/phạm vi bước nhảy linh hoạt | Có, nếu dùng chỉ số hợp lệ |
| `for (int value : a)` (*enhanced for*, *for-each*) | Chỉ cần **đọc** từng giá trị theo thứ tự | Gán vào biến `value` không làm đổi phần tử mảng |

Các vòng duyệt đầy đủ thường tốn **O(n)** thời gian với `n = a.length`. Khi chỉ đọc/tổng hợp, `for-each` dễ đọc và ít mắc lỗi chỉ số; khi sửa các phần tử kiểu nguyên thủy, ưu tiên vòng lặp có chỉ số.

Cần chú ý: `for (int value : a) { value *= 2; }` chỉ thay đổi **biến cục bộ** `value`. Mảng `a` giữ nguyên. Tương tự, với mảng đối tượng, gán lại biến tham chiếu trong `for-each` không thay tham chiếu tại vị trí mảng; tuy vậy gọi phương thức làm đổi trạng thái của chính đối tượng được tham chiếu vẫn có thể ảnh hưởng đến đối tượng.

## 3. Ví dụ Java 17+ chạy độc lập

Lưu vào tệp **`DemoMang.java`**:

```java
import java.util.Arrays;

public class DemoMang {
    public static void main(String[] args) {
        int[] diem = {4, 7, 9};
        System.out.println("Ban dau: " + Arrays.toString(diem));

        // Đọc rồi cập nhật một phần tử bằng chỉ số.
        int giaTriCu = diem[1];
        diem[1] = 8;
        System.out.println("Gia tri cu: " + giaTriCu);
        System.out.println("Sau cap nhat: " + Arrays.toString(diem));

        // Duyệt xuôi bằng chỉ số.
        System.out.print("For:");
        for (int i = 0; i < diem.length; i++) {
            System.out.print(" " + diem[i]);
        }
        System.out.println();

        // Duyệt chỉ để đọc: for-each.
        System.out.print("For-each:");
        for (int value : diem) {
            System.out.print(" " + value);
        }
        System.out.println();

        // Cập nhật tất cả phần tử, bắt buộc gán về diem[i].
        for (int i = 0; i < diem.length; i++) {
            diem[i] += 1;
        }
        System.out.println("Tang moi phan tu: " + Arrays.toString(diem));

        // Tổng hợp dữ liệu bằng for-each.
        int tong = 0;
        for (int value : diem) {
            tong += value;
        }
        System.out.println("Tong: " + tong);

        // Duyệt ngược theo chỉ số.
        System.out.print("Nguoc:");
        for (int i = diem.length - 1; i >= 0; i--) {
            System.out.print(" " + diem[i]);
        }
        System.out.println();
    }
}
```

Mở terminal tại thư mục chứa tệp, dùng **JDK 17 trở lên** (PowerShell, CMD, bash đều dùng được):

```bash
javac -encoding UTF-8 DemoMang.java
java DemoMang
```

Kết quả trên stdout (**lệnh `javac` biên dịch thành công không in gì**):

```text
Ban dau: [4, 7, 9]
Gia tri cu: 7
Sau cap nhat: [4, 8, 9]
For: 4 8 9
For-each: 4 8 9
Tang moi phan tu: [5, 9, 10]
Tong: 24
Nguoc: 10 9 5
```

Trong chương trình: giá trị `7` đọc vào `giaTriCu` không tự đổi khi `diem[1]` được gán `8`; sau vòng tăng mỗi phần tử, mảng trở thành `[5, 9, 10]`. Lệnh `Arrays.toString(diem)` giúp in nội dung mảng một chiều thay vì kết quả mặc định của `diem.toString()`.

## 4. Kiểm tra giới hạn và mảng rỗng

Java kiểm tra chỉ số tại thời điểm truy cập. `a[-1]` và `a[a.length]` gây **`ArrayIndexOutOfBoundsException`**; gọi `a.length` hoặc `a[i]` khi `a == null` gây **`NullPointerException`**. Không nên dựa vào ngoại lệ để điều khiển luồng xử lý thông thường.

```java
int[] a = {};
if (a != null && a.length > 0) {
    System.out.println(a[a.length - 1]);
} else {
    System.out.println("Mang rong");
}

int index = 2;
if (a != null && index >= 0 && index < a.length) {
    System.out.println(a[index]);
}
```

Mảng rỗng (`new int[0]` hoặc `{}`) có `length == 0`; các vòng `for` và `for-each` duyệt từ đầu không chạy lần nào, nên an toàn. Tuy nhiên truy cập `a[0]` hoặc phần tử cuối là không hợp lệ. Đối với chỉ số nhập từ người dùng, kiểm tra **`0 <= index && index < a.length`** trước cả khi đọc và ghi.

## 5. Một số thao tác thường gặp

**Tìm phần tử đầu tiên thỏa điều kiện**: vòng `for` có chỉ số trả về vị trí, hoặc `-1` khi không tìm thấy:

```java
int[] a = {4, 8, 9};
int foundIndex = -1;
for (int i = 0; i < a.length; i++) {
    if (a[i] == 8) {
        foundIndex = i;
        break;
    }
}
// foundIndex == 1
```

**Cập nhật có điều kiện**: duyệt bằng chỉ số rồi ghi lại phần tử mong muốn:

```java
for (int i = 0; i < a.length; i++) {
    if (a[i] % 2 == 0) {
        a[i] *= 2;
    }
}
// a = [8, 16, 9]
```

**Tránh ảnh hưởng ngoài ý muốn khi dùng chung tham chiếu**:

```java
int[] original = {1, 2};
int[] alias = original;
alias[0] = 99;               // original[0] cũng thành 99
int[] copy = original.clone(); // Mảng int[] riêng
copy[0] = 5;                  // original[0] vẫn là 99
```

Với mảng chứa đối tượng, `clone()` chỉ sao chép cấu trúc mảng và **các tham chiếu**, không sao chép sâu từng đối tượng. Nếu cần đặt cùng một giá trị cho nhiều phần tử, có thể dùng `Arrays.fill(a, value)`; để tính giá trị mới từ chỉ số, có thể dùng `Arrays.setAll(a, i -> ...)` đúng với kiểu mảng tương ứng.

## 6. Lỗi phổ biến và nguyên tắc lựa chọn

- **Lệch một chỉ số**: `for (int i = 0; i <= a.length; i++)` sẽ cố truy cập `a[a.length]`. Khi đi xuôi toàn mảng dùng điều kiện `i < a.length`.
- **Nhầm thứ tự**: phần tử thứ `k` (đếm từ 1) có chỉ số `k - 1`; phần tử cuối là `length - 1`.
- **Nhầm `length` với `length()`**: `array.length` khác `string.length()`.
- **Gán biến `for-each` rồi tưởng mảng đổi**: dùng `a[i] = newValue` nếu thực sự cần thay phần tử.
- **Quên mảng rỗng hoặc `null`**: kiểm tra trước khi lấy phần tử đầu/cuối và khi xử lý đầu vào không bảo đảm.
- **Nhầm thay giá trị với thêm phần tử**: `a[i] = ...` chỉ thay thế tại vị trí hiện có; số lượng phần tử không tăng.
- **Nhầm giá trị và tham chiếu**: `int x = a[i]` sao chép giá trị `int`; khi phần tử là đối tượng, phép đọc sao chép tham chiếu tới đối tượng đó.

**Ghi nhớ:** dùng `a[i]` để đọc/ghi đúng vị trí, `a.length` để khống chế biên, `for` có chỉ số để thay đổi phần tử và `for-each` cho duyệt chỉ đọc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="one-dimensional-arrays-008">ArrayIndexOutOfBoundsException và NegativeArraySizeException</a>

<details>
<summary>Xem chi tiết</summary>

# `ArrayIndexOutOfBoundsException` và `NegativeArraySizeException` — Java 17+

## 1. Hai lỗi mảng khác nhau

Khi làm việc với mảng một chiều trong Java, cần phân biệt **lỗi truy cập phần tử** và **lỗi cấp phát mảng**:

| Ngoại lệ | Phát sinh khi nào? | Ví dụ |
|---|---|---|
| `ArrayIndexOutOfBoundsException` | Đọc/ghi phần tử tại chỉ số ngoài miền `0..length-1` | `a[-1]`, `a[a.length]` |
| `NegativeArraySizeException` | Cấp phát mảng với kích thước âm | `new int[-2]` |
| `NullPointerException` (liên quan) | Truy cập mảng qua tham chiếu `null` | `a.length` với `a == null` |

Cả hai ngoại lệ chính đều là **unchecked exceptions** (các lớp con của `RuntimeException`), không bắt buộc khai báo `throws` hoặc `catch`. Chúng phát sinh **khi chạy**; nhiều biểu thức liên quan vẫn biên dịch hợp lệ.

Ví dụ độc lập:

~~~java
int[] a = {10, 20, 30};

System.out.println(a.length); // 3
System.out.println(a[0]);     // 10
System.out.println(a[2]);     // 30

// a[3] hoặc a[-1]: chỉ số không hợp lệ.
// new int[-2]: kích thước cấp phát không hợp lệ.
~~~

**Quy tắc nhớ:** *Đã có mảng, truy cập sai vị trí → index out of bounds; đang tạo mảng, kích thước âm → negative array size.*

## 2. `ArrayIndexOutOfBoundsException` khi đọc

~~~java
int[] numbers = {4, 8, 12};

// System.out.println(numbers[3]);
// Khi chạy: ArrayIndexOutOfBoundsException.
~~~

Java kiểm tra miền chỉ số lúc truy cập. Việc ghi `numbers[3]` có thể **qua biên dịch** vì chỉ số là `int`, nhưng thất bại lúc thực thi do mảng chỉ có ba phần tử.

Minh họa có bắt ngoại lệ, không phụ thuộc thông điệp lỗi của JDK:

~~~java
try {
    System.out.println(numbers[3]);
} catch (ArrayIndexOutOfBoundsException ex) {
    System.out.println("READ_OUT_OF_BOUNDS");
}
~~~

**Exact stdout:**

~~~text
READ_OUT_OF_BOUNDS
~~~

Thông điệp `ex.getMessage()` có thể phụ thuộc phiên bản và ngữ cảnh; ví dụ dùng một thông báo tự kiểm soát để bảo đảm đầu ra dự kiến chính xác.

## 3. `ArrayIndexOutOfBoundsException` khi ghi

Không chỉ đọc, **ghi** vào chỉ số sai cũng vượt biên:

~~~java
int[] numbers = {4, 8, 12};

try {
    numbers[-1] = 99;
} catch (ArrayIndexOutOfBoundsException ex) {
    System.out.println("WRITE_OUT_OF_BOUNDS");
}
~~~

**Exact stdout:**

~~~text
WRITE_OUT_OF_BOUNDS
~~~

Lệnh gán không tạo vị trí mới. Mảng Java có **độ dài cố định**: `numbers[numbers.length] = 99` không có nghĩa là “thêm phần tử vào cuối”.

Muốn bổ sung phần tử, hãy tạo mảng mới với kích thước phù hợp (ví dụ `Arrays.copyOf`) hoặc chọn `ArrayList` nếu nghiệp vụ cần danh sách có kích thước linh hoạt.

## 4. Lỗi `NegativeArraySizeException` khi tạo mảng

Khi cấp phát bằng `new`, biểu thức kích thước phải là số nguyên **không âm**:

~~~java
int[] empty = new int[0]; // Hợp lệ: length = 0.
int[] data = new int[3];  // Hợp lệ: length = 3.

// int[] bad = new int[-2]; // Biên dịch được, ném NegativeArraySizeException khi chạy.
~~~

Minh họa:

~~~java
try {
    int requestedLength = -2;
    int[] bad = new int[requestedLength];
    System.out.println(bad.length);
} catch (NegativeArraySizeException ex) {
    System.out.println("NEGATIVE_ARRAY_SIZE");
}
~~~

**Exact stdout:**

~~~text
NEGATIVE_ARRAY_SIZE
~~~

Đây là lỗi của **tham số kích thước khi cấp phát**, không phải lỗi đọc chỉ số. Mảng dài 0 được phép tạo; chỉ số âm **không bao giờ** hợp lệ khi truy cập.

## 5. Kiểm tra trước khi cấp phát mảng

Một API nhận kích thước từ bên ngoài nên quyết định chính sách dữ liệu âm:

~~~java
static int[] createChecked(int size) {
    if (size < 0) {
        throw new IllegalArgumentException("size must be >= 0");
    }
    return new int[size];
}
~~~

Ưu điểm: thay vì để `NegativeArraySizeException` phát sinh từ nơi cấp phát, API nêu rõ yêu cầu đầu vào bằng `IllegalArgumentException`. Đây là **quyết định thiết kế API**, không phải luật bắt buộc phải chuyển mọi lỗi sang `IllegalArgumentException`.

Ví dụ:

~~~java
System.out.println(createChecked(0).length); // 0
System.out.println(createChecked(3).length); // 3
~~~

Khi gọi `createChecked(-1)`, phương thức ném `IllegalArgumentException` với thông điệp đã định.

**Quan trọng:** kích thước không âm chưa bảo đảm cấp phát thành công. Yêu cầu mảng quá lớn có thể dẫn tới `OutOfMemoryError`; đó là vấn đề khác, không phải `NegativeArraySizeException`. Không cấp phát thử những mảng cực lớn chỉ để kiểm tra trên máy học.

Khi đã xác định được hai điểm phát sinh lỗi, kiểm tra dữ liệu đầu vào là cách phòng ngừa lỗi thường dùng:

## 6. Kiểm tra biên trước khi đọc/ghi

Hàm đọc trả nhãn thay vì truy cập sai:

~~~java
static String readSafely(int[] array, int index) {
    if (array == null) {
        return "NULL_ARRAY";
    }
    if (index < 0 || index >= array.length) {
        return "BAD_INDEX";
    }
    return "VALUE:" + array[index];
}
~~~

Với mảng `{10, 20, 30}`:

| Thao tác | Giá trị trả |
|---|---|
| `readSafely(a, -1)` | `BAD_INDEX` |
| `readSafely(a, 0)` | `VALUE:10` |
| `readSafely(a, 2)` | `VALUE:30` |
| `readSafely(a, 3)` | `BAD_INDEX` |
| `readSafely(null, 0)` | `NULL_ARRAY` |

Trong ứng dụng thực tế, bạn có thể trả `OptionalInt`, một kết quả nghiệp vụ có cấu trúc hoặc ném `IllegalArgumentException` tùy hợp đồng API. Chuỗi trong ví dụ chỉ nhằm quan sát luồng dễ dàng.

**Không đủ:** `index < array.length`, vì `-1 < length` vẫn true với mảng không rỗng. Cần cả **cận dưới** `index >= 0` lẫn **cận trên** `index < length`.

## 7. Hai ngoại lệ phát sinh ở hai bước khác nhau

~~~mermaid
flowchart TD
    A["Thao tác với mảng"] --> B{"Đang tạo mảng mới?"}
    B -- "Có" --> C{"size < 0?"}
    C -- "Có" --> D["NegativeArraySizeException"]
    C -- "Không" --> E["Tạo mảng; cần đủ bộ nhớ"]
    B -- "Không, đang đọc/ghi" --> F{"array == null?"}
    F -- "Có" --> G["NullPointerException"]
    F -- "Không" --> H{"index < 0 hoặc index >= length?"}
    H -- "Có" --> I["ArrayIndexOutOfBoundsException"]
    H -- "Không" --> J["Truy cập hợp lệ"]
~~~

Sơ đồ giả định thao tác cấp phát và đọc/ghi mảng một chiều với kích thước/chỉ số kiểu số nguyên. Nó chỉ làm rõ **kiểu lỗi thường gặp**, không biểu diễn hết mọi trường hợp lỗi cấp phát hoặc các biểu thức có tác dụng phụ.

Quy tắc debug: nếu thông báo chứa `NegativeArraySizeException`, hãy kiểm tra biểu thức kích thước ở vị trí `new T[size]`; nếu chứa `ArrayIndexOutOfBoundsException`, hãy xem độ dài mảng và chỉ số tại phép truy cập `array[index]`.

## 8. Chương trình Java 17+ độc lập, chạy được

Tạo đúng file **`ArrayExceptionsDemo.java`**:

~~~java
import java.util.Arrays;

public final class ArrayExceptionsDemo {
    private ArrayExceptionsDemo() {}

    static boolean validIndex(int[] array, int index) {
        return array != null && index >= 0 && index < array.length;
    }

    static String readSafely(int[] array, int index) {
        if (array == null) return "NULL_ARRAY";
        if (index < 0 || index >= array.length) return "BAD_INDEX";
        return "VALUE:" + array[index];
    }

    static int[] createChecked(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0");
        }
        return new int[size];
    }

    static int sum(int[] array) {
        int total = 0;
        for (int i = 0; i < array.length; i++) {
            total += array[i];
        }
        return total;
    }

    public static void main(String[] args) {
        int[] values = {10, 20, 30};

        System.out.println("length=" + values.length);
        System.out.println("first=" + values[0]);
        System.out.println("last=" + values[values.length - 1]);
        System.out.println("sum=" + sum(values));

        System.out.println("valid -1=" + validIndex(values, -1));
        System.out.println("valid 0=" + validIndex(values, 0));
        System.out.println("valid 2=" + validIndex(values, 2));
        System.out.println("valid 3=" + validIndex(values, 3));
        System.out.println("valid null=" + validIndex(null, 0));

        System.out.println("safe -1=" + readSafely(values, -1));
        System.out.println("safe 0=" + readSafely(values, 0));
        System.out.println("safe 2=" + readSafely(values, 2));
        System.out.println("safe 3=" + readSafely(values, 3));
        System.out.println("safe null=" + readSafely(null, 0));

        try {
            System.out.println(values[-1]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("read -1=ArrayIndexOutOfBoundsException");
        }

        try {
            values[values.length] = 99;
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("write length=ArrayIndexOutOfBoundsException");
        }

        try {
            int requestedLength = -2;
            int[] bad = new int[requestedLength];
            System.out.println(bad.length);
        } catch (NegativeArraySizeException ex) {
            System.out.println("size -2=NegativeArraySizeException");
        }

        int[] empty = new int[0];
        System.out.println("empty length=" + empty.length);
        try {
            System.out.println(empty[0]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("empty[0]=ArrayIndexOutOfBoundsException");
        }

        int[] missing = null;
        try {
            System.out.println(missing.length);
        } catch (NullPointerException ex) {
            System.out.println("null.length=NullPointerException");
        }

        System.out.println("checked size 0=" + createChecked(0).length);
        System.out.println("checked size 3=" + createChecked(3).length);
        try {
            createChecked(-1);
        } catch (IllegalArgumentException ex) {
            System.out.println("guard -1=" + ex.getMessage());
        }

        int[] extended = Arrays.copyOf(values, 5);
        System.out.println("original=" + Arrays.toString(values));
        System.out.println("extended=" + Arrays.toString(extended));

        for (int i = values.length - 1; i >= 0; i--) {
            System.out.println("reverse=" + values[i]);
        }
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal ở thư mục chứa file. Chương trình không cần thư viện ngoài JDK, Maven, Gradle hoặc preview feature.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ArrayExceptionsDemo.java
java -cp . ArrayExceptionsDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ArrayExceptionsDemo.java
java -cp . ArrayExceptionsDemo
~~~

### Exact expected stdout

~~~text
length=3
first=10
last=30
sum=60
valid -1=false
valid 0=true
valid 2=true
valid 3=false
valid null=false
safe -1=BAD_INDEX
safe 0=VALUE:10
safe 2=VALUE:30
safe 3=BAD_INDEX
safe null=NULL_ARRAY
read -1=ArrayIndexOutOfBoundsException
write length=ArrayIndexOutOfBoundsException
size -2=NegativeArraySizeException
empty length=0
empty[0]=ArrayIndexOutOfBoundsException
null.length=NullPointerException
checked size 0=0
checked size 3=3
guard -1=size must be >= 0
original=[10, 20, 30]
extended=[10, 20, 30, 0, 0]
reverse=30
reverse=20
reverse=10
~~~

**Giải thích nhanh:**

1. `values.length = 3`; chỉ số 0 và 2 hợp lệ, -1 và 3 không hợp lệ.
2. `readSafely` phát hiện null hoặc chỉ số ngoài biên trước khi đọc mảng.
3. Hai trường hợp đọc -1, ghi vào chỉ số bằng `length` ném `ArrayIndexOutOfBoundsException`.
4. `new int[-2]` ném `NegativeArraySizeException`; `new int[0]` hợp lệ, nhưng `empty[0]` vẫn vượt biên.
5. `missing.length` ném `NullPointerException`, không phải lỗi chỉ số.
6. `createChecked` chuyển đầu vào âm thành lỗi `IllegalArgumentException` theo hợp đồng API.
7. `Arrays.copyOf` tạo mảng mới dài 5, giữ mảng gốc nguyên độ dài 3.

## 9. Lỗi thường gặp và cách sửa

| Sai lầm | Hậu quả | Cách đúng |
|---|---|---|
| `a[a.length]` để lấy phần tử cuối | `ArrayIndexOutOfBoundsException` | `a[a.length - 1]` khi không rỗng |
| Dùng `i <= a.length` để duyệt | Vượt biên ở `i == length` | `i < a.length` |
| Chỉ kiểm tra `i < a.length` | Chỉ số âm lọt | `0 <= i && i < a.length` |
| Tạo `new int[-1]` | `NegativeArraySizeException` | Kiểm tra `size >= 0` |
| Nghĩ `new int[0]` là lỗi | Hiểu sai điều kiện cấp phát | Mảng rỗng là hợp lệ |
| Đọc `a[0]` khi `length == 0` | Vượt biên | Kiểm tra `length > 0` |
| Dùng `a.length` khi `a == null` | `NullPointerException` | Kiểm tra null trước |
| Gán `a[a.length] = item` để thêm phần tử | Vượt biên; độ dài cố định | Tạo mảng mới/List phù hợp |
| Trộn hai ngoại lệ trong một `catch` rồi đoán nguyên nhân | Chẩn đoán khó hơn | Xác định lỗi cấp phát hay truy cập |
| Dựa vào `getMessage()` của exception làm exact stdout | Dễ khác môi trường/JDK | In thông điệp ổn định do code định nghĩa |
| Bắt `RuntimeException` quanh cả thuật toán để che mọi lỗi | Có thể giấu bug thật | Sửa cận/kích thước tại nơi gây lỗi |
| Dùng kích thước không âm nhưng quá lớn | Có thể thiếu bộ nhớ | Hạn chế kích thước đầu vào theo yêu cầu |

**Không dùng `try/catch` để thay thế thuật toán duyệt đúng biên.** Trong ví dụ, catch chỉ giúp thấy rõ loại ngoại lệ, còn cách phòng ngừa là xác thực trước khi đọc/ghi/cấp phát.

## 10. Bài tập có đáp án

### A — Hai biểu thức có cùng loại lỗi không?

~~~java
int[] a = {1, 2};
// a[2];
// new int[-2];
~~~

**Đáp án:** không. `a[2]` gây `ArrayIndexOutOfBoundsException` vì mảng dài 2, còn `new int[-2]` gây `NegativeArraySizeException`.

### B — Mảng rỗng

~~~java
int[] a = new int[0];
System.out.println(a.length);
~~~

**Đáp án:** in `0`. Nếu tiếp tục truy cập `a[0]`, runtime ném `ArrayIndexOutOfBoundsException`.

### C — Sửa vòng `for` sai

~~~java
int[] a = {2, 4, 6};
// for (int i = 0; i <= a.length; i++) {
//     System.out.println(a[i]);
// }
~~~

**Đáp án:**

~~~java
for (int i = 0; i < a.length; i++) {
    System.out.println(a[i]);
}
~~~

**Exact stdout:**

~~~text
2
4
6
~~~

### D — Kiểm tra null và biên

Viết hàm cho biết `index` có hợp lệ với `int[] a` hay không.

**Đáp án:**

~~~java
static boolean isValid(int[] a, int index) {
    return a != null && index >= 0 && index < a.length;
}
~~~

Không gọi `a.length` khi `a == null` và không bỏ sót chỉ số âm.

### E — Chính sách kích thước âm

Yêu cầu từ chối kích thước âm bằng ngoại lệ có thông điệp rõ ràng.

**Đáp án:**

~~~java
static int[] allocate(int requested) {
    if (requested < 0) {
        throw new IllegalArgumentException("negative size");
    }
    return new int[requested];
}
~~~

Trường hợp `requested = 0` vẫn hợp lệ, còn `-1` tạo lỗi `IllegalArgumentException` theo chính sách API.

## 11. Checklist và kết luận

- [ ] Biết `ArrayIndexOutOfBoundsException` phát sinh khi đọc **hoặc ghi** vào chỉ số không hợp lệ.
- [ ] Nhớ miền chỉ số `0 <= index < array.length`.
- [ ] Biết `NegativeArraySizeException` phát sinh lúc tạo mảng bằng kích thước âm.
- [ ] Hiểu `new int[0]` hợp lệ nhưng `empty[0]` không hợp lệ.
- [ ] Không nhầm `null` với mảng rỗng; `null.length` gây `NullPointerException`.
- [ ] Sử dụng vòng lặp `i < array.length` thay vì `i <= array.length`.
- [ ] Kiểm tra kích thước không âm ở ranh giới API nếu cần thông báo rõ ràng.
- [ ] Không cho rằng kích thước không âm luôn có đủ bộ nhớ để cấp phát.
- [ ] Phân biệt thời điểm cấp phát mảng với thời điểm truy cập phần tử.
- [ ] Không dựa vào thông điệp exception mặc định để tạo stdout có thể tái lập.
- [ ] Chạy `ArrayExceptionsDemo.java` trên JDK 17+, so sánh exact stdout và xác nhận các biên `-1`, `0`, `length-1`, `length`.

**Kết luận:** `ArrayIndexOutOfBoundsException` báo lỗi **vị trí truy cập** trong mảng đã tạo; `NegativeArraySizeException` báo lỗi **kích thước âm khi cấp phát**. Cả hai đều có thể tránh được trong luồng xử lý thông thường bằng cách kiểm tra đúng dữ liệu đầu vào, luôn duyệt theo cận `0 <= i < length`, và xử lý rõ ràng hai trường hợp mảng rỗng và mảng null.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="one-dimensional-arrays-009">Gán mảng chỉ sao chép tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Gán mảng chỉ sao chép tham chiếu — Java 17+

## 1. Bản chất của phép gán mảng

Mảng trong Java là **đối tượng (object)**. Biến có kiểu mảng như `int[]` giữ **giá trị tham chiếu** tới đối tượng ấy. Phép gán từ biến mảng này sang biến mảng khác **sao chép giá trị tham chiếu**, không tạo mảng mới và không sao chép từng phần tử.

~~~java
int[] a = {10, 20, 30};
int[] b = a;             // Cùng tham chiếu một mảng.
b[0] = 99;               // Sửa phần tử trong mảng chung.
System.out.println(a[0]);
~~~

**Exact stdout:**

~~~text
99
~~~

Mô hình:

~~~text
Biến a ───────┐
              ▼
          int[] {99, 20, 30}
              ▲
Biến b ───────┘
~~~

`a` và `b` là **hai biến khác nhau**, nhưng tại thời điểm đó trỏ **cùng đối tượng mảng**. Thuật ngữ thường dùng là **alias** (bí danh/tham chiếu cùng đối tượng).

**Quy tắc:** `b = a;` không tương đương `b = a.clone();` hoặc `b = Arrays.copyOf(a, a.length);`.

## 2. Phân biệt sửa phần tử và gán lại tham chiếu

~~~java
int[] a = {1, 2};
int[] b = a;

b[0] = 7;            // Sửa object mà cả a và b cùng trỏ.
System.out.println(java.util.Arrays.toString(a));

b = new int[]{8, 9}; // Chỉ thay tham chiếu được giữ trong biến b.
System.out.println(java.util.Arrays.toString(a));
System.out.println(java.util.Arrays.toString(b));
~~~

**Exact stdout:**

~~~text
[7, 2]
[7, 2]
[8, 9]
~~~

Hai loại thao tác:

| Thao tác | Tác động lên |
|---|---|
| `b = a;` | Tham chiếu trong **biến b**; không tạo mảng |
| `b[0] = 7;` | **Phần tử** ở mảng mà b đang chỉ tới |
| `b = new int[]{8, 9};` | Chuyển **biến b** sang đối tượng mảng mới |
| `a[0]` sau khi b chuyển sang mảng khác | Vẫn đọc mảng cũ mà a chỉ tới |

Một lần gán lại `b` không “kéo theo” `a`, dù trước đó `a == b`. **Đừng nhầm việc thay nội dung object với việc thay biến tham chiếu.**

## 3. `==` kiểm tra có phải cùng đối tượng mảng

~~~java
int[] first = {1, 2};
int[] alias = first;
int[] other = new int[]{1, 2};

System.out.println(first == alias);
System.out.println(first == other);
System.out.println(java.util.Arrays.equals(first, other));
~~~

**Exact stdout:**

~~~text
true
false
true
~~~

- Với hai tham chiếu mảng, `==` kiểm tra chúng trỏ đến **cùng object** hay không.
- Hai đối tượng mảng khác nhau vẫn có thể chứa các phần tử **bằng nhau**.
- `Arrays.equals(int[], int[])` so nội dung lần lượt từng phần tử của hai mảng một chiều `int[]`.

| Muốn kiểm tra | Dùng |
|---|---|
| Hai biến có trỏ cùng mảng? | `a == b` |
| Hai mảng `int[]` có cùng dãy giá trị? | `Arrays.equals(a, b)` |
| Muốn in các phần tử mảng `int[]` | `Arrays.toString(a)` |

`a.equals(b)` với mảng Java thông thường **không tự so sánh từng phần tử** như `Arrays.equals`. Không dùng `==` để kết luận hai mảng có nội dung bằng nhau.

## 4. Vì sao truyền mảng vào phương thức cũng có thể làm đổi dữ liệu?

Java **truyền đối số theo giá trị** (*pass-by-value*). Khi đối số là biến tham chiếu mảng, **giá trị được sao chép chính là tham chiếu**.

~~~java
static void changeElement(int[] values) {
    values[0] = 50;
}

static void rebindParameter(int[] values) {
    values = new int[]{7, 8}; // Chỉ gán lại biến tham số cục bộ.
    values[0] = 70;
}
~~~

~~~java
int[] original = {1, 2, 3};
changeElement(original);
System.out.println(java.util.Arrays.toString(original));

rebindParameter(original);
System.out.println(java.util.Arrays.toString(original));
~~~

**Exact stdout:**

~~~text
[50, 2, 3]
[50, 2, 3]
~~~

Trong `changeElement`, tham số `values` là **bản sao tham chiếu** nhưng trỏ đến **cùng mảng** như `original`; sửa `values[0]` làm thay dữ liệu caller. Trong `rebindParameter`, phép gán `values = new int[]{...}` chỉ đổi **tham số trong lần gọi**; biến `original` ở caller vẫn trỏ tới mảng cũ.

Đây **không phải pass-by-reference** theo nghĩa phương thức có thể gán lại biến tham chiếu của caller. Nếu cần caller sử dụng mảng mới, phương thức có thể **trả mảng mới**, rồi caller tự gán: `original = createNewArray(original);`.

## 5. Sao chép nội dung để tránh chia sẻ mảng `int[]`

Các cách thông dụng:

~~~java
import java.util.Arrays;

int[] original = {1, 2, 3};
int[] clone = original.clone();
int[] copy = Arrays.copyOf(original, original.length);

System.out.println(original == clone);
System.out.println(Arrays.equals(original, clone));
~~~

**Exact stdout:**

~~~text
false
true
~~~

`clone()` trên `int[]` tạo **mảng mới cùng kiểu, cùng độ dài và cùng giá trị phần tử**. `Arrays.copyOf(original, original.length)` cũng tạo mảng mới. Sau khi có bản sao:

~~~java
clone[0] = 99;
System.out.println(Arrays.toString(original));
System.out.println(Arrays.toString(clone));
~~~

**Exact stdout:**

~~~text
[1, 2, 3]
[99, 2, 3]
~~~

Vì hai mảng `int[]` giờ độc lập, sửa giá trị nguyên thủy ở bản sao không ảnh hưởng bản gốc.

| Kỹ thuật | Tạo mảng đích mới? | Độ dài đích |
|---|---|---|
| `int[] b = a;` | **Không** | Không có mảng mới |
| `int[] b = a.clone();` | **Có** | Bằng `a.length` |
| `int[] b = Arrays.copyOf(a, a.length);` | **Có** | Bằng `a.length` |
| `int[] b = Arrays.copyOf(a, 5);` | **Có** | Bằng `5` |
| `System.arraycopy(a, 0, b, 0, n)` | **Không tự tạo b** | B phải được cấp phát sẵn đủ chỗ |

Với `Arrays.copyOf(a, 5)`, nếu mảng gốc dài 3, hai vị trí thêm vào của `int[]` có giá trị mặc định `0`. Nếu độ dài mới nhỏ hơn, các phần tử vượt quá sẽ không được sao chép.

**Cẩn thận:** `clone()` hay `copyOf` vẫn là **shallow copy** khi các phần tử mảng là tham chiếu tới đối tượng có thể thay đổi. Điều này sẽ được phân biệt ngay sau đây.

## 6. Mảng chứa đối tượng: sao chép mảng không sao chép đối tượng phần tử

~~~java
StringBuilder[] first = {
    new StringBuilder("A"),
    new StringBuilder("B")
};
StringBuilder[] second = first.clone(); // Mảng ngoài khác, item tham chiếu chung.

second[0].append("!");
System.out.println(first[0]);

second[1] = new StringBuilder("C");
System.out.println(first[1]);
System.out.println(second[1]);
~~~

**Exact stdout:**

~~~text
A!
B
C
~~~

`second[0].append("!")` sửa **object `StringBuilder` được hai mảng cùng tham chiếu**, nên `first[0]` cũng hiển thị `A!`. Trái lại, `second[1] = new StringBuilder("C")` chỉ thay **tham chiếu phần tử** tại vị trí 1 của mảng `second`; `first[1]` vẫn trỏ tới `"B"`.

Vì vậy cần phân biệt hai tầng:

1. **Mảng**: `first` và `second` là hai đối tượng mảng khác nhau sau `clone()`.
2. **Object phần tử**: các phần tử tương ứng vẫn có thể là cùng tham chiếu tới một object mutable.

Muốn tách biệt cả nội dung mutable, cần tạo **bản sao của từng object phần tử**, theo hợp đồng của kiểu ấy. Với `String` (bất biến), chia sẻ tham chiếu thường không gây vấn đề thay đổi nội dung. Còn với `StringBuilder`, sửa nội dung sẽ tác động lên mọi tham chiếu tới object đó.

## 7. Khi gán `null` hoặc trả về mảng mới

~~~java
int[] a = {3, 4};
int[] b = a;
b = null;

System.out.println(b == null);
System.out.println(java.util.Arrays.toString(a));
~~~

**Exact stdout:**

~~~text
true
[3, 4]
~~~

Gán `b = null` chỉ khiến **biến b** không còn trỏ tới mảng; `a` vẫn giữ mảng đó. Nếu sau đó truy cập `b.length` hoặc `b[0]`, Java ném `NullPointerException`.

Một phương thức có thể trả một tham chiếu **mới** để caller chủ động thay biến:

~~~java
static int[] appendZero(int[] source) {
    return java.util.Arrays.copyOf(source, source.length + 1);
}
~~~

~~~java
int[] a = {3, 4};
int[] longer = appendZero(a);
System.out.println(java.util.Arrays.toString(a));
System.out.println(java.util.Arrays.toString(longer));
~~~

**Exact stdout:**

~~~text
[3, 4]
[3, 4, 0]
~~~

Mảng có độ dài cố định. `appendZero` không “nới dài” object cũ mà **cấp phát mảng khác**. Nếu caller muốn dùng mảng dài hơn cho biến `a`, cần `a = appendZero(a);`.

## 8. Chương trình Java 17+ hoàn chỉnh

Lưu đúng file **`ArrayReferenceAssignmentDemo.java`**:

~~~java
import java.util.Arrays;

public final class ArrayReferenceAssignmentDemo {
    private ArrayReferenceAssignmentDemo() {}

    static void mutateElement(int[] values) {
        values[0] = 99;
    }

    static void rebindParameter(int[] values) {
        values = new int[]{7, 8};
        values[0] = 70;
    }

    public static void main(String[] args) {
        int[] original = {10, 20, 30};
        int[] alias = original;

        System.out.println("original start=" + Arrays.toString(original));
        System.out.println("alias same array=" + (original == alias));

        alias[1] = 77;
        System.out.println("original after alias write=" + Arrays.toString(original));

        mutateElement(alias);
        System.out.println("original after mutate=" + Arrays.toString(original));

        rebindParameter(alias);
        System.out.println("original after reassign parameter="
                + Arrays.toString(original));

        int[] copy = original.clone();
        System.out.println("clone same array=" + (original == copy));
        System.out.println("clone contents equal=" + Arrays.equals(original, copy));
        copy[0] = 1;
        System.out.println("original after clone write=" + Arrays.toString(original));
        System.out.println("clone=" + Arrays.toString(copy));

        int[] extended = Arrays.copyOf(original, 5);
        System.out.println("extended=" + Arrays.toString(extended));
        System.out.println("original length=" + original.length);

        int[] destination = new int[3];
        System.arraycopy(original, 0, destination, 0, original.length);
        destination[2] = -5;
        System.out.println("arraycopy dest=" + Arrays.toString(destination));
        System.out.println("original after arraycopy dest write="
                + Arrays.toString(original));

        alias = new int[]{4, 5};
        System.out.println("alias after rebinding=" + Arrays.toString(alias));
        System.out.println("original after rebinding=" + Arrays.toString(original));
        System.out.println("same after rebinding=" + (original == alias));

        int[] missing = original;
        missing = null;
        System.out.println("null reference=" + (missing == null));
        System.out.println("original survives null=" + Arrays.toString(original));

        StringBuilder[] objects = {
            new StringBuilder("A"), new StringBuilder("B")
        };
        StringBuilder[] shallow = objects.clone();
        System.out.println("object array shared item=" + (objects[0] == shallow[0]));
        shallow[0].append("!");
        System.out.println("object after item edit=" + objects[0]);
        shallow[1] = new StringBuilder("C");
        System.out.println("object original second=" + objects[1]);
        System.out.println("object new second=" + shallow[1]);
        System.out.println("object shared array=" + (objects == shallow));
    }
}
~~~

Yêu cầu **JDK 17 trở lên**; không dùng thư viện ngoài JDK hoặc preview feature.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ArrayReferenceAssignmentDemo.java
java -cp . ArrayReferenceAssignmentDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ArrayReferenceAssignmentDemo.java
java -cp . ArrayReferenceAssignmentDemo
~~~

### Exact expected stdout

~~~text
original start=[10, 20, 30]
alias same array=true
original after alias write=[10, 77, 30]
original after mutate=[99, 77, 30]
original after reassign parameter=[99, 77, 30]
clone same array=false
clone contents equal=true
original after clone write=[99, 77, 30]
clone=[1, 77, 30]
extended=[99, 77, 30, 0, 0]
original length=3
arraycopy dest=[99, 77, -5]
original after arraycopy dest write=[99, 77, 30]
alias after rebinding=[4, 5]
original after rebinding=[99, 77, 30]
same after rebinding=false
null reference=true
original survives null=[99, 77, 30]
object array shared item=true
object after item edit=A!
object original second=B
object new second=C
object shared array=false
~~~

Phân tích theo trình tự: `alias` ban đầu cùng tham chiếu với `original`, nên ghi qua alias và qua tham số `mutateElement` làm mảng gốc đổi. Gán lại tham số `values` trong `rebindParameter` không làm caller trỏ mảng mới. `clone()` và `copyOf` tạo các mảng `int[]` độc lập. `System.arraycopy` sao chép phần tử vào mảng đích có sẵn. Ở phần `StringBuilder[]`, clone chỉ tách **mảng**, không tạo bản sao các đối tượng phần tử mutable.

## 9. Sai lầm thường gặp

| Sai lầm | Hậu quả / cách hiểu sai | Cách đúng |
|---|---|---|
| `int[] b = a;` là copy dữ liệu | Hai biến vẫn cùng object | Dùng `a.clone()` hoặc `Arrays.copyOf` |
| `b[0] = 5` chỉ ảnh hưởng b | Khi alias, a cũng thấy thay đổi | Phân biệt biến tham chiếu với mảng |
| `b = new int[2]` khiến a đổi theo | Chỉ b trỏ sang object mới | Phép gán biến không sửa biến khác |
| `a == b` kiểm tra phần tử bằng nhau | Nó so identity | Dùng `Arrays.equals` cho nội dung `int[]` |
| `values = new int[]{...}` trong method gán lại array của caller | Tham số là bản sao tham chiếu | Trả mảng mới, caller tự gán |
| `clone()` trên `StringBuilder[]` clone từng StringBuilder | Các object phần tử vẫn có thể dùng chung | Sao chép từng object khi cần |
| `System.arraycopy` tự tạo mảng đích | Đích phải tồn tại, đủ kích thước | Khởi tạo đích trước |
| `b = null` làm a cũng null | Chỉ b bị gán lại | a vẫn có thể trỏ tới mảng cũ |

## 10. Bài tập và đáp án

**A — Dự đoán giá trị:**

~~~java
int[] a = {2, 4};
int[] b = a;
b[1] = 9;
System.out.println(a[1]);
~~~

**Đáp án:** `9`, vì hai biến trỏ cùng mảng.

**B — Gán lại biến:**

~~~java
int[] a = {2, 4};
int[] b = a;
b = new int[]{7};
System.out.println(a.length + "," + b.length);
~~~

**Đáp án:** `2,1`. Việc chuyển b sang mảng mới không đổi a.

**C — So sánh tham chiếu và nội dung:**

~~~java
int[] a = {1, 2};
int[] b = {1, 2};
System.out.println(a == b);
System.out.println(Arrays.equals(a, b));
~~~

**Đáp án:**

~~~text
false
true
~~~

**D — Phương thức nhận mảng:**

~~~java
static void edit(int[] a) {
    a[0] = 5;
    a = new int[]{8};
}
~~~

Sau `int[] x = {1}; edit(x);`, giá trị `x[0]` là **5**. Gán `a = new int[]{8}` không đổi tham chiếu biến `x` ở caller.

**E — Khi clone mảng object:** `StringBuilder[] copy = original.clone();` làm `copy == original` thành **false**, nhưng `copy[0] == original[0]` có thể **true**. Muốn thay đổi trạng thái `StringBuilder` độc lập, cần sao chép chính object đó.

## 11. Checklist và kết luận

- [ ] Hiểu biến kiểu `T[]` chứa tham chiếu tới đối tượng mảng.
- [ ] Biết phép gán `b = a` chỉ sao chép tham chiếu, không copy phần tử.
- [ ] Phân biệt sửa `b[i]` với gán lại `b`.
- [ ] Dùng `==` kiểm tra cùng object, `Arrays.equals` kiểm tra nội dung `int[]`.
- [ ] Hiểu Java truyền tham chiếu mảng **theo giá trị** khi gọi phương thức.
- [ ] Dùng `clone()`/`Arrays.copyOf` để tạo mảng nguyên thủy mới độc lập.
- [ ] Biết `System.arraycopy` sao chép vào mảng đích đã được tạo.
- [ ] Nhận diện shallow copy khi mảng chứa các object mutable.
- [ ] Không nhầm `null` của một biến với trạng thái biến khác.
- [ ] Chạy `ArrayReferenceAssignmentDemo.java` với Java 17+ và đối chiếu exact stdout.

**Kết luận:** Với mảng một chiều trong Java, `b = a` chỉ **sao chép tham chiếu**; cả hai tên biến cùng nhìn một đối tượng mảng cho tới khi một biến được gán lại. Sửa phần tử qua alias làm thay đổi dữ liệu chung. Muốn có mảng `int[]` độc lập, cần **tạo bản sao đối tượng mảng**; nếu phần tử là object mutable, có thể phải sao chép thêm chính các object phần tử.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="one-dimensional-arrays-010">Giới thiệu Arrays.toString(), Arrays.equals() và Arrays.copyOf()</a>

<details>
<summary>Xem chi tiết</summary>

# Giới thiệu `Arrays.toString()`, `Arrays.equals()` và `Arrays.copyOf()`

> **Java core — language-basics / Mảng một chiều**  
> **Môi trường:** Java 17+, chỉ dùng JDK.  
> **Mục tiêu:** Biết cách **in nội dung**, **so sánh nội dung** và **tạo bản sao mảng với độ dài mong muốn**; tránh nhầm tham chiếu `==` với nội dung, mảng rỗng với `null` và sao chép nông với sao chép sâu.

## 1. Ba công cụ trong `java.util.Arrays`

~~~java
import java.util.Arrays;

int[] data = {2, 4, 6};

System.out.println(Arrays.toString(data));          // [2, 4, 6]
System.out.println(Arrays.equals(data, new int[] {2, 4, 6})); // true
int[] copy = Arrays.copyOf(data, 5);                 // [2, 4, 6, 0, 0]
~~~

| Phương thức | Vai trò | Giá trị trả về |
| --- | --- | --- |
| `Arrays.toString(array)` | Biểu diễn phần tử của mảng một chiều thành chuỗi | `String` |
| `Arrays.equals(a, b)` | So sánh nội dung của hai mảng theo thứ tự | `boolean` |
| `Arrays.copyOf(original, newLength)` | Tạo **mảng mới**, sao chép phần đầu và cắt/bổ sung nếu cần | Mảng mới |

Các phương thức đều là `static`, gọi qua lớp `Arrays`. Chúng không tự thay đổi đối tượng mảng nguồn. Dùng `import java.util.Arrays;` hoặc gọi đầy đủ `java.util.Arrays.toString(...)`.

## 2. `Arrays.toString()`: nhìn thấy các phần tử

~~~java
int[] values = {3, -1, 0};
String[] names = {"An", null, "Binh"};

System.out.println(Arrays.toString(values)); // [3, -1, 0]
System.out.println(Arrays.toString(names));  // [An, null, Binh]
System.out.println(Arrays.toString(new int[0])); // []
~~~

`System.out.println(values)` gọi biểu diễn đối tượng mặc định của mảng (thường giống `[I@...`), **không** tự in các số dưới dạng danh sách. `Arrays.toString(values)` thuận tiện khi debug và viết output có thể kiểm chứng.

- Với mảng primitive, phần tử được định dạng theo kiểu tương ứng.
- Với mảng tham chiếu, nội dung hiển thị phụ thuộc vào `toString()` của đối tượng phần tử; phần tử null hiện `null`.
- Với **mảng nhiều chiều**, `Arrays.toString(outer)` không in sâu các mảng con; dùng `Arrays.deepToString(outer)` khi muốn xem cấu trúc lồng nhau.

`Arrays.toString((int[]) null)` trả **chuỗi `"null"`**, không ném `NullPointerException`. Đây chỉ là cách định dạng tham chiếu null, không có nghĩa mảng được tạo ra.

## 3. `Arrays.equals()` khác toán tử `==`

~~~java
int[] a = {1, 2};
int[] b = {1, 2};
int[] alias = a;

System.out.println(a == b);            // false: hai đối tượng khác nhau.
System.out.println(a == alias);        // true: cùng đối tượng.
System.out.println(Arrays.equals(a, b)); // true: hai phần tử bằng nhau.
~~~

`Arrays.equals(a, b)` với mảng primitive so sánh các phần tử tương ứng cùng thứ tự; chiều dài khác nhau thì false. Với mảng tham chiếu, phương thức dùng quy tắc bằng nhau của **phần tử**, bao gồm `equals`, không nhất thiết so bằng tham chiếu.

~~~java
String[] x = {new String("Java")};
String[] y = {"Java"};
System.out.println(Arrays.equals(x, y)); // true: String.equals xét nội dung.
~~~

Ngược lại, các đối tượng chỉ giữ `Object.equals` mặc định thường so theo danh tính:

~~~java
StringBuilder[] x = {new StringBuilder("A")};
StringBuilder[] y = {new StringBuilder("A")};
// Arrays.equals(x, y) là false: StringBuilder không override equals theo nội dung.
~~~

Điều này khác với **so sánh mảng lồng nhau**: `Arrays.equals(int[][], int[][])` chỉ so sánh các tham chiếu mảng con theo `equals` của từng mảng; muốn so từng số trong mảng con, dùng `Arrays.deepEquals(...)`.

## 4. `Arrays.copyOf()`: mảng mới, dài hơn hoặc ngắn hơn

~~~java
int[] original = {5, 7, 9};

int[] sameLength = Arrays.copyOf(original, original.length);
int[] longer = Arrays.copyOf(original, 5);   // [5, 7, 9, 0, 0]
int[] shorter = Arrays.copyOf(original, 2);  // [5, 7]
~~~

Quy tắc:

1. Java tạo **mảng mới** với độ dài bằng `newLength`.
2. Sao chép `min(original.length, newLength)` phần tử đầu, giữ thứ tự.
3. Nếu cần thêm vị trí, mỗi vị trí được điền **giá trị mặc định của kiểu thành phần**: số là 0, boolean là false, tham chiếu là null.
4. Nếu độ dài mới nhỏ hơn, những phần tử ở cuối mảng nguồn không có trong mảng mới.

~~~java
String[] colors = {"red", "blue"};
String[] padded = Arrays.copyOf(colors, 4);
// padded = [red, blue, null, null].
~~~

`Arrays.copyOf` không thay đổi `original.length`. Nếu viết `original = Arrays.copyOf(original, 5)`, biến `original` sẽ **trỏ sang đối tượng mảng mới**; mọi alias trỏ tới mảng cũ vẫn nhìn thấy độ dài cũ.

Độ dài mới âm gây `NegativeArraySizeException`. Mảng nguồn null gây `NullPointerException` khi gọi `copyOf`; cần kiểm tra đầu vào nếu API cho phép null.

## 5. Bản sao độc lập ở cấp mảng, nhưng có thể chia sẻ đối tượng phần tử

Với mảng `int[]`, sao chép tạo các vị trí số độc lập:

~~~java
int[] a = {2, 4};
int[] copy = Arrays.copyOf(a, a.length);
copy[0] = 99;
// a = [2, 4]; copy = [99, 4]; a == copy là false.
~~~

Với mảng các đối tượng mutable, `copyOf` chỉ **sao chép giá trị tham chiếu** (*shallow copy*):

~~~java
StringBuilder[] source = {new StringBuilder("A")};
StringBuilder[] copy = Arrays.copyOf(source, source.length);

copy[0].append("!");
// source[0].toString() cũng là "A!".
copy[0] = new StringBuilder("B");
// source[0] vẫn trỏ đối tượng ban đầu "A!".
~~~

Mảng nhiều chiều trong Java là **mảng chứa các tham chiếu tới mảng con**. Vì vậy khi copy mảng ngoài, các mảng con vẫn được chia sẻ:

~~~java
int[][] rows = {{1, 2}, {3}};
int[][] outerCopy = Arrays.copyOf(rows, rows.length);

outerCopy[0][0] = 99;
// rows[0][0] cũng bằng 99.
~~~

Muốn sao chép sâu dữ liệu hai chiều, phải tạo bản sao cho từng mảng con hoặc dùng cách sao chép đối tượng thích hợp. `Arrays.deepEquals` và `Arrays.deepToString` dùng để **so sánh/hiển thị sâu**, không tự tạo bản sao sâu.

## 6. Mảng rỗng, `null` và độ phức tạp

| Lệnh | Kết quả |
| --- | --- |
| `Arrays.toString(new int[0])` | `"[]"` |
| `Arrays.toString((int[]) null)` | `"null"` |
| `Arrays.equals(new int[0], new int[0])` | `true` |
| `Arrays.equals((int[]) null, (int[]) null)` | `true` |
| `Arrays.equals((int[]) null, new int[0])` | `false` |
| `Arrays.copyOf(new int[0], 3)` | Mảng mới `[0, 0, 0]` |
| `Arrays.copyOf((int[]) null, 3)` | `NullPointerException` |

`Arrays.equals(null, null)` chỉ có nghĩa hai tham chiếu cùng null theo quy ước của API; **không** có hai đối tượng mảng được tạo. Khi tự viết hàm nghiệp vụ, vẫn phải quyết định null có hợp lệ không.

Với mảng dài `n`, `toString` đọc các phần tử để tạo chuỗi và `equals` có thể đọc tới `n` phần tử (hoặc dừng sớm khi khác). `copyOf` dùng bộ nhớ cho **mảng mới dài `newLength`** và sao chép phần phù hợp. Đây là các thao tác hữu ích nhưng không miễn phí nếu dữ liệu rất lớn.

## 7. Ví dụ Java 17+ chạy độc lập

Tạo file **ArraysBasicsDemo.java**:

~~~java
import java.util.Arrays;

public final class ArraysBasicsDemo {
    public static void main(String[] args) {
        int[] numbers = {2, 4, 6};
        int[] sameValues = {2, 4, 6};
        System.out.println("initial=" + Arrays.toString(numbers));
        System.out.println("sameReference=" + (numbers == sameValues));
        System.out.println("sameContent=" + Arrays.equals(numbers, sameValues));

        int[] alias = numbers;
        alias[0] = 9;
        System.out.println("afterAlias=" + Arrays.toString(numbers));
        System.out.println("equalAfterAlias=" + Arrays.equals(numbers, sameValues));

        int[] copy = Arrays.copyOf(numbers, numbers.length);
        copy[1] = 40;
        System.out.println("originalAfterCopy=" + Arrays.toString(numbers));
        System.out.println("changedCopy=" + Arrays.toString(copy));
        System.out.println("copySameReference=" + (numbers == copy));

        System.out.println("longer="
                + Arrays.toString(Arrays.copyOf(numbers, 5)));
        System.out.println("shorter="
                + Arrays.toString(Arrays.copyOf(numbers, 2)));
        String[] words = {"red", "blue"};
        System.out.println("paddedReferences="
                + Arrays.toString(Arrays.copyOf(words, 4)));

        String[] firstWords = {new String("Java")};
        String[] secondWords = {"Java"};
        System.out.println("stringContentEqual="
                + Arrays.equals(firstWords, secondWords));

        StringBuilder[] buildersA = {new StringBuilder("A")};
        StringBuilder[] buildersB = {new StringBuilder("A")};
        System.out.println("builderContentEqual="
                + Arrays.equals(buildersA, buildersB));

        int[][] nestedA = {{1, 2}, {3}};
        int[][] nestedB = {{1, 2}, {3}};
        System.out.println("nestedShallowEqual="
                + Arrays.equals(nestedA, nestedB));
        System.out.println("nestedDeepEqual="
                + Arrays.deepEquals(nestedA, nestedB));
        System.out.println("nestedDeepText="
                + Arrays.deepToString(nestedA));

        int[][] outerCopy = Arrays.copyOf(nestedA, nestedA.length);
        outerCopy[0][0] = 99;
        System.out.println("nestedAfterCopy="
                + Arrays.deepToString(nestedA));
        System.out.println("outerSame=" + (nestedA == outerCopy));
        System.out.println("innerSame=" + (nestedA[0] == outerCopy[0]));

        String[] missing = null;
        System.out.println("nullText=" + Arrays.toString(missing));
        System.out.println("nullEqualsNull="
                + Arrays.equals(missing, (String[]) null));
        System.out.println("nullEqualsEmpty="
                + Arrays.equals(missing, new String[0]));
        System.out.println("emptyToThree="
                + Arrays.toString(Arrays.copyOf(new int[0], 3)));

        try {
            Arrays.copyOf(missing, 2);
        } catch (NullPointerException ex) {
            System.out.println("copyNull=NullPointerException");
        }
        try {
            Arrays.copyOf(numbers, -1);
        } catch (NegativeArraySizeException ex) {
            System.out.println("copyNegative=NegativeArraySizeException");
        }
    }
}
~~~

Biên dịch/chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ArraysBasicsDemo.java
java ArraysBasicsDemo
~~~

**Expected stdout chính xác:**

~~~text
initial=[2, 4, 6]
sameReference=false
sameContent=true
afterAlias=[9, 4, 6]
equalAfterAlias=false
originalAfterCopy=[9, 4, 6]
changedCopy=[9, 40, 6]
copySameReference=false
longer=[9, 4, 6, 0, 0]
shorter=[9, 4]
paddedReferences=[red, blue, null, null]
stringContentEqual=true
builderContentEqual=false
nestedShallowEqual=false
nestedDeepEqual=true
nestedDeepText=[[1, 2], [3]]
nestedAfterCopy=[[99, 2], [3]]
outerSame=false
innerSame=true
nullText=null
nullEqualsNull=true
nullEqualsEmpty=false
emptyToThree=[0, 0, 0]
copyNull=NullPointerException
copyNegative=NegativeArraySizeException
~~~

Dòng `sameReference=false` nhưng `sameContent=true` chứng minh `==` khác `Arrays.equals`. Hai dòng `nestedDeepEqual` và `nestedAfterCopy` cho thấy cách so sánh sâu **khác** việc sao chép nông. Các dòng `copyNull` và `copyNegative` minh họa lỗi cần xử lý.

## 8. Lỗi thường gặp và bài tập

| Lỗi / hiểu lầm | Cách xử lý |
| --- | --- |
| In mảng trực tiếp rồi nghĩ đã in phần tử | Dùng `Arrays.toString(array)` |
| So hai mảng với `==` để so nội dung | Dùng `Arrays.equals(a, b)` |
| Dùng `Arrays.equals` cho mảng nhiều chiều và mong so mọi số | Dùng `Arrays.deepEquals` |
| Dùng `Arrays.toString` để in sâu mảng lồng nhau | Dùng `Arrays.deepToString` |
| Cho rằng `copyOf` đổi `length` của mảng gốc | Nó tạo **mảng mới** |
| Cho rằng `copyOf` sao chép sâu mọi đối tượng | Nó chỉ sao chép phần tử/tham chiếu |
| Gọi `copyOf(null, n)` | Kiểm tra null trước nếu có khả năng này |
| Dùng `newLength` âm | Kiểm tra độ dài, vì runtime ném `NegativeArraySizeException` |

**Bài 1.** Tạo hai mảng `int[] {1, 2, 3}` độc lập. So sánh bằng `==` và `Arrays.equals`, rồi sửa một phần tử.

**Bài 2.** Từ `int[] {4, 5}`, tạo một bản copy dài 4 và một bản dài 1. Dự đoán giá trị mặc định được thêm và phần tử bị cắt.

**Bài 3.** Dùng `String[] {"A", "B"}`, gọi `Arrays.copyOf` với độ dài 4 và giải thích vì sao hai vị trí mới là null chứ không phải chuỗi rỗng.

**Bài 4.** Viết mảng hai chiều có các mảng con độc lập nhưng nội dung bằng nhau; so sánh kết quả `Arrays.equals` và `Arrays.deepEquals`.

**Bài 5.** Sao chép mảng `StringBuilder[]` bằng `copyOf`, sau đó gọi `append` trên phần tử của bản sao. Vì sao nguồn cũng thấy nội dung thay đổi?

### Ghi nhớ

**`Arrays.toString` để xem nội dung; `Arrays.equals` để so nội dung; `Arrays.copyOf` để tạo mảng mới.** `==` trên hai biến mảng chỉ hỏi có cùng đối tượng không. `copyOf` có thể cắt hoặc bổ sung các giá trị mặc định, nhưng là **shallow copy** với tham chiếu đối tượng. Với mảng lồng nhau, dùng `deepToString`/`deepEquals` nếu cần xem hoặc so sánh phần tử sâu.


</details>

- [Quay lại đầu trang](#back-to-top)
