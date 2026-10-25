<a id="back-to-top"></a>

# Tham chiếu và null

## Menu
- [Biến tham chiếu và đối tượng mà biến tham chiếu tới](#references-and-null-001)
- [Khai báo biến không đồng nghĩa với tạo đối tượng](#references-and-null-002)
- [new tạo đối tượng và trả về tham chiếu](#references-and-null-003)
- [Gán tham chiếu làm nhiều biến cùng dùng một đối tượng](#references-and-null-004)
- [Thay đổi trạng thái khác với gán lại biến tham chiếu](#references-and-null-005)
- [== trên tham chiếu kiểm tra cùng đối tượng](#references-and-null-006)
- [null biểu thị không tham chiếu tới đối tượng](#references-and-null-007)
- [Nhận diện NullPointerException và đặt kiểm tra tại điểm phù hợp](#references-and-null-008)

## <a id="references-and-null-001">Biến tham chiếu và đối tượng mà biến tham chiếu tới</a>

<details>
<summary>Xem chi tiết</summary>

# Biến tham chiếu và đối tượng mà biến tham chiếu tới — Java 17+

Trong Java, khi khai báo `Box item = new Box(10);`, ta cần phân biệt **biến tham chiếu `item`** với **đối tượng `Box` được tạo bởi `new`**.

- **Biến tham chiếu** có một kiểu khai báo (ví dụ `Box`), có thể chứa giá trị tham chiếu tới đối tượng tương thích hoặc `null`. Biến này có thể được gán lại nếu không bị ràng buộc bởi `final`.
- **Đối tượng** mang trạng thái và hành vi riêng theo lớp của nó. Hai biến có thể cùng tham chiếu đến **một đối tượng**; khi đó thao tác thay đổi trạng thái đối tượng qua một biến có thể quan sát qua biến còn lại.
- **Phép gán tham chiếu không nhân bản đối tượng.** Trong `Box other = item;`, giá trị tham chiếu được sao chép, chứ không tự có thêm một `Box`.
- `null` có nghĩa là **không tham chiếu đến đối tượng nào**, không phải một đối tượng đặc biệt có trường hoặc phương thức.

Đây là **ngữ nghĩa** của Java, không cần giả định địa chỉ bộ nhớ, vị trí stack/heap hay cách JVM tối ưu đối tượng.

## 1. Bốn thao tác trông giống nhau nhưng khác bản chất

~~~java
Box first = new Box("Main", 10); // Tao mot doi tuong, first tham chieu toi no.
Box alias = first;        // Copy tham chieu, KHONG tao Box moi.
Box another = new Box("Other", 10); // Tao Box khac, du co cung gia tri.
alias = null;             // Chi thay doi bien alias.
~~~


| Thao tác | Biến thay đổi? | Đối tượng thay đổi? |
| --- | --- | --- |
| `Box first = new Box("Main", 10);` | `first` nhận tham chiếu | Có đối tượng mới được tạo |
| `Box alias = first;` | `alias` nhận bản sao tham chiếu | Không tự tạo hoặc sửa đối tượng |
| `alias.points = 20;` | Tham chiếu trong `alias` không đổi | Đối tượng chung được sửa nếu field cho phép |
| `alias = new Box("Fresh", 5);` | `alias` trỏ sang đối tượng khác | Đối tượng cũ không bị thay bằng đối tượng mới |
| `alias = null;` | `alias` không còn trỏ tới đối tượng | Bản thân đối tượng cũ không tự bị xóa ngay |
| `Box independent = new Box("Independent", 20);` | `independent` giữ tham chiếu riêng | Có đối tượng mới, dù dữ liệu trùng nhau |

Các đoạn ở mục này dùng cùng constructor `Box(String label, int points)` và field `points` trong chương trình ở mục 2; những đoạn ví dụ này chỉ cần đặt vào phạm vi thích hợp sau định nghĩa lớp `Box` để thử lại.

Hãy hình dung:

~~~text
first ───┐
         ├──────> Box(points=10)
alias ───┘

another ────────> Box(points=10)   // Doi tuong khac

alias = null;
first ──────────> Box(points=10)
alias ──────────> null
~~~

Sơ đồ chỉ mô tả **quan hệ tham chiếu**, không mô tả địa chỉ RAM hay cơ chế quản lý bộ nhớ vật lý.

## 2. Ví dụ Java 17+ chạy độc lập

Lưu tệp UTF-8 tên **`ReferenceObjectDemo.java`**:

~~~java
public class ReferenceObjectDemo {
    static class Box {
        final String label;
        int points;

        Box(String label, int points) {
            this.label = label;
            this.points = points;
        }
    }

    static void increase(Box parameter) {
        parameter.points += 5; // Sua doi tuong dang duoc tham chieu.
    }

    static void replace(Box parameter) {
        parameter = new Box("Replacement", 99);
        System.out.println("insideReplace=" + parameter.points);
    }

    public static void main(String[] args) {
        Box first = new Box("Main", 10);
        Box alias = first;
        Box separate = new Box("Main", 10);

        System.out.println("aliasSameObject=" + (first == alias));
        System.out.println("separateSameObject=" + (first == separate));

        alias.points = 20;
        System.out.println("firstAfterAlias=" + first.points);
        System.out.println("separatePoints=" + separate.points);

        increase(first);
        System.out.println("afterIncrease=" + first.points);

        replace(first);
        System.out.println("afterReplace=" + first.points);

        alias = null;
        System.out.println("aliasIsNull=" + (alias == null));
        System.out.println("firstStillWorks=" + first.points);

        Box old = first;
        first = new Box("Fresh", 7);
        System.out.println("newObject=" + first.points);
        System.out.println("oldObject=" + old.points);

        final Box fixedReference = old;
        fixedReference.points = 30;
        System.out.println("finalReferencePoints=" + old.points);

        Box absent = null;
        System.out.println("absentIsNull=" + (absent == null));
        try {
            System.out.println(absent.points);
        } catch (NullPointerException ex) {
            System.out.println("dereference=NullPointerException");
        }

        Box[] slots = new Box[2];
        System.out.println("defaultArrayElementNull=" + (slots[0] == null));
        slots[0] = old;
        System.out.println("arraySharesObject=" + (slots[0] == old));
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) ở thư mục chứa tệp, dùng JDK 17+:

~~~bash
java -version
javac -version
javac -encoding UTF-8 ReferenceObjectDemo.java
java ReferenceObjectDemo
~~~

**Expected output của chương trình** (không tính các dòng phiên bản do `java -version` và `javac -version` in ra):

~~~text
aliasSameObject=true
separateSameObject=false
firstAfterAlias=20
separatePoints=10
afterIncrease=25
insideReplace=99
afterReplace=25
aliasIsNull=true
firstStillWorks=25
newObject=7
oldObject=25
finalReferencePoints=30
absentIsNull=true
dereference=NullPointerException
defaultArrayElementNull=true
arraySharesObject=true
~~~

### Phân tích kết quả

1. `alias = first` làm hai biến trỏ cùng đối tượng, vì thế `first == alias` là `true`. `separate` được tạo bằng lần `new` khác; dữ liệu giống nhau không biến nó thành cùng một đối tượng.
2. `alias.points = 20` làm `first.points` thành 20. Không phải “biến `first` bị sửa tham chiếu”; **trạng thái của đối tượng chung** đã thay đổi.
3. Phương thức `increase` nhận bản sao tham chiếu. Nó vẫn truy cập cùng đối tượng và tăng `points` lên 25.
4. `replace` chỉ gán lại **tham số cục bộ** sang `Box` mới. Biến `first` của bên gọi vẫn trỏ tới đối tượng có `points = 25`.
5. Gán `alias = null` không làm đối tượng biến mất vì `first` vẫn giữ tham chiếu. Khi gán `first = new Box("Fresh", 7)`, `old` tiếp tục giữ tham chiếu đối tượng cũ.
6. `final Box fixedReference = old` ngăn gán lại biến `fixedReference`; nó **không** đóng băng `points` bên trong đối tượng. Thay field qua `fixedReference` làm `old.points` thành 30.
7. `absent.points` gây `NullPointerException` vì `absent` là `null`. Mảng `Box[]` cũng là đối tượng; mỗi phần tử tham chiếu ban đầu là `null`. Gán `slots[0] = old` tạo thêm một alias.

## 3. Java luôn truyền tham số **theo giá trị**

Với tham số kiểu tham chiếu, Java **sao chép giá trị tham chiếu** vào biến tham số. Vì vậy cần tách hai việc:

~~~java
static void mutate(Box b) {
    b.points = 42; // Sua trang thai doi tuong chung.
}

static void reassign(Box b) {
    b = new Box("Other", 42); // Chi gan lai tham so b.
}
~~~

- Gọi `mutate(first)` có thể khiến `first.points` thay đổi, vì bên gọi và tham số đều dẫn tới cùng đối tượng.
- Gọi `reassign(first)` **không** làm biến `first` trỏ tới một đối tượng mới.
- Nếu muốn bên gọi dùng một đối tượng thay thế, phương thức có thể **trả về tham chiếu mới**, rồi bên gọi viết `first = createReplacement(...);`.

Không gọi cơ chế này là “Java truyền tham chiếu theo tham chiếu”: **Java không cho phương thức gán lại biến tham chiếu của bên gọi bằng cách gán lại tham số**.


## 4. Nhận diện cùng đối tượng

Hai lần `new Box("A", 10)` tạo hai đối tượng có thể cùng dữ liệu, nhưng `x == y` sẽ là `false` nếu `x` và `y` trỏ tới hai đối tượng ấy. Ngược lại, `Box z = x;` khiến `x == z` là `true` vì chỉ sao chép tham chiếu. `==` không tự so các field; với `String`, dùng `equals` khi muốn so nội dung. Kiểu tự định nghĩa chỉ so giá trị bằng `equals` khi có hợp đồng phù hợp.


## 5. Giá trị `null` và biến chưa gán

`Box absent = null;` là biến đã có giá trị nhưng chưa chỉ tới đối tượng nào; `absent.points` sẽ ném `NullPointerException`. Ngược lại, biến cục bộ chỉ mới khai báo `Box pending;` chưa được *definite assignment* nên không được đọc. Field và phần tử mảng kiểu tham chiếu có giá trị mặc định `null`.

`alias = null` chỉ thay giá trị một biến, không hủy đối tượng đang được các alias khác giữ; thời điểm garbage collector thu hồi không được bảo đảm. Đóng tài nguyên như file hoặc socket theo `close()`/`try-with-resources` là trách nhiệm riêng.

## 6. Những nhận định dễ sai

| Nhận định | Đúng/sai | Giải thích |
| --- | --- | --- |
| “`Box b = a;` tạo Box mới.” | **Sai** | Chỉ sao chép giá trị tham chiếu |
| “Hai biến trỏ cùng đối tượng, sửa field qua biến này sẽ thấy qua biến kia.” | **Đúng** với field có thể sửa | Cả hai truy cập cùng trạng thái |
| “Gán `b = null` sẽ xóa đối tượng ngay.” | **Sai** | Chỉ đổi tham chiếu; có thể còn alias khác |
| “`new Box(1)` và `new Box(1)` là cùng đối tượng.” | **Sai** | Hai lần tạo đối tượng riêng |
| “Gán lại tham số kiểu `Box` sẽ đổi biến bên gọi.” | **Sai** | Tham số là bản sao của giá trị tham chiếu |
| “`final Box b` làm mọi field bất biến.” | **Sai** | `final` ràng buộc gán lại biến, không tự ràng buộc trạng thái đối tượng |
| “`null` là một đối tượng rỗng.” | **Sai** | Không có đối tượng nào được tham chiếu |
| “Mảng `Box[]` chứa sẵn các `Box` được `new` riêng.” | **Sai** | Các phần tử kiểu tham chiếu ban đầu là `null` |

## 7. Bài tập tự kiểm tra

**Bài 1.** Dự đoán kết quả sau:

~~~java
Box a = new Box("One", 1);
Box b = a;
b.points = 4;
System.out.println(a.points);
~~~

*Đáp án:* `4`. Hai biến cùng tham chiếu một đối tượng.

**Bài 2.** Nếu tiếp tục viết `b = new Box("Two", 9);`, `a.points` thành bao nhiêu?

*Đáp án:* Vẫn là `4`. Chỉ `b` trỏ sang đối tượng mới.

**Bài 3.** Với `Box x = null;`, kiểm tra `x == null` có hợp lệ không? Còn `x.points`?

*Đáp án:* Phép so sánh hợp lệ và trả `true`. Truy cập field ném `NullPointerException`.

**Bài 4.** Tại sao một phương thức nhận `Box parameter` rồi viết `parameter = new Box("New", 3);` không sửa tham chiếu trong biến của bên gọi?

*Đáp án:* Java truyền **bản sao giá trị tham chiếu** vào tham số; phép gán chỉ cập nhật tham số cục bộ.

**Bài 5.** `final Box b = new Box("Fixed", 5);` có cho phép `b.points = 6;` không? Có cho phép `b = new Box("Other", 6);` không?

*Đáp án:* Cho phép thay đổi `points`; không cho phép gán lại biến `final`.

## Checklist

- [ ] Phân biệt **biến tham chiếu** với **đối tượng được tạo**.
- [ ] Hiểu phép gán giữa biến tham chiếu là **sao chép giá trị tham chiếu**.
- [ ] Phân biệt sửa field của đối tượng với gán lại biến trỏ sang đối tượng khác.
- [ ] Biết `==` so sánh đồng nhất đối tượng, không mặc định so nội dung.
- [ ] Biết Java truyền mọi tham số theo giá trị, kể cả tham số là tham chiếu.
- [ ] Hiểu `null`, truy cập qua `null` và điều kiện thu gom bộ nhớ.
- [ ] Không hiểu nhầm `final` là làm đối tượng bất biến.

**Ghi nhớ:** Một biến tham chiếu là **nơi giữ đường dẫn logic tới đối tượng**, còn đối tượng là **thực thể chứa trạng thái**. Sao chép đường dẫn không sao chép thực thể; sửa thực thể không tự thay biến; thay biến không tự sửa hay xóa thực thể.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="references-and-null-002">Khai báo biến không đồng nghĩa với tạo đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo biến không đồng nghĩa với tạo đối tượng — Java 17+

Trong Java, **khai báo một biến tham chiếu** không đồng nghĩa với **tạo một đối tượng**. Đây là hai hành động độc lập: biến giữ một **giá trị tham chiếu** (hoặc `null`), còn đối tượng là thực thể được tạo ra khi một thao tác thực sự khởi tạo đối tượng.

~~~java
Book book;                    // Chi khai bao bien cuc bo, chua tao Book.
book = null;                 // Bien khong tham chieu den doi tuong nao.
book = new Book("Java");     // Tao Book, gan tham chieu cho book.
Book alias = book;           // Sao chep tham chieu, KHONG tao Book moi.
~~~

Không thể đọc `book` ngay sau dòng `Book book;` nếu đó là biến cục bộ: nó chưa được gán chắc chắn (*definite assignment*). Ngược lại, field kiểu tham chiếu của lớp/đối tượng chưa được khởi tạo tường minh sẽ có giá trị mặc định `null`.

## 1. Phân biệt các thao tác

| Đoạn mã | Điều gì xảy ra? | Có tạo `Book` mới? |
| --- | --- | --- |
| `Book a;` | Khai báo biến cục bộ chưa gán | **Không** |
| `Book a = null;` | Khai báo và gán tham chiếu null | **Không** |
| `Book a = new Book("A");` | Gọi constructor của một Book mới | **Có** |
| `Book b = a;` | Sao chép tham chiếu, tạo alias | **Không** |
| `a = new Book("B");` | Gán lại biến `a` để trỏ tới Book mới | **Có** |
| `Book[] books = new Book[2];` | Tạo **một đối tượng mảng**, hai phần tử ban đầu là null | **Không tạo Book** |
| `books[0] = a;` | Đặt tham chiếu sẵn có vào phần tử mảng | **Không** |

**Cần phân biệt loại đối tượng được tạo:** `new Book[2]` vẫn tạo **đối tượng mảng `Book[]`**, nhưng không gọi constructor `Book` hai lần. Mỗi phần tử của mảng tham chiếu ban đầu bằng `null`.

Một đối tượng cũng có thể được truy cập qua giá trị mà một phương thức trả về, một field, hoặc một biến khác. Đừng hiểu nhầm rằng **mỗi tên biến** đều phải đi với một **đối tượng riêng**.

## 2. Chương trình Java 17+ runnable

Tạo file UTF-8 tên **`DeclarationAndCreationDemo.java`**:

~~~java
public class DeclarationAndCreationDemo {
    static class Book {
        static int constructed;
        final String title;

        Book(String title) {
            this.title = title;
            constructed++;
        }
    }

    static class Shelf {
        Book current; // Reference field mac dinh null.
    }

    static Book shared; // Static reference field mac dinh null.

    public static void main(String[] args) {
        Book pending;
        pending = null;
        System.out.println("pendingNull=" + (pending == null));
        System.out.println("constructedStart=" + Book.constructed);

        Shelf shelf = new Shelf(); // Tao Shelf, KHONG tao Book.
        System.out.println("shelfBookNull=" + (shelf.current == null));
        System.out.println("sharedNull=" + (shared == null));

        Book first = new Book("Java");
        System.out.println("afterFirst=" + Book.constructed);

        Book alias = first; // Khong new, khong goi constructor.
        System.out.println("afterAlias=" + Book.constructed);
        System.out.println("aliasSame=" + (alias == first));

        Book second = new Book("Java");
        System.out.println("afterSecond=" + Book.constructed);
        System.out.println("sameObject=" + (first == second));
        System.out.println("sameTitle=" + first.title.equals(second.title));

        Book[] slots = new Book[2];
        System.out.println("afterArray=" + Book.constructed);
        System.out.println("firstSlotNull=" + (slots[0] == null));

        slots[0] = first;
        shelf.current = first;
        shared = second;
        System.out.println("slotAlias=" + (slots[0] == first));
        System.out.println("shelfAlias=" + (shelf.current == first));
        System.out.println("sharedSecond=" + (shared == second));

        first = second;
        System.out.println("firstNowSecond=" + (first == second));
        System.out.println("aliasStillOld=" + (alias == slots[0]));
        System.out.println("constructedEnd=" + Book.constructed);

        try {
            System.out.println(pending.title);
        } catch (NullPointerException ex) {
            System.out.println("nullAccess=NullPointerException");
        }
    }
}
~~~

Tại thư mục chứa file, mở PowerShell hoặc Bash và dùng **JDK 17+**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 DeclarationAndCreationDemo.java
java DeclarationAndCreationDemo
~~~

**Expected output** của lệnh `java DeclarationAndCreationDemo` (không tính thông tin phiên bản JDK):

~~~text
pendingNull=true
constructedStart=0
shelfBookNull=true
sharedNull=true
afterFirst=1
afterAlias=1
aliasSame=true
afterSecond=2
sameObject=false
sameTitle=true
afterArray=2
firstSlotNull=true
slotAlias=true
shelfAlias=true
sharedSecond=true
firstNowSecond=true
aliasStillOld=true
constructedEnd=2
nullAccess=NullPointerException
~~~

### Giải thích kết quả

- `Book pending;` không tạo `Book`. Sau khi gán `pending = null`, biến đã được khởi tạo hợp lệ, nhưng vẫn không tham chiếu đối tượng.
- `new Shelf()` **tạo một Shelf** chứ không tạo sẵn Book. Field `current` trong Shelf và field `shared` mặc định là `null`.
- Mỗi biểu thức `new Book("Java")` gọi constructor `Book` một lần. Trong chương trình, `Book.constructed` tăng từ 0 lên 1 rồi 2.
- `Book alias = first;` **không gọi constructor**. Hai biến cùng tham chiếu một Book; `alias == first` là `true`.
- `first` và `second` là hai Book riêng biệt dù `title.equals` cho `true`. `==` với hai tham chiếu ở đây so sánh **đồng nhất đối tượng**, không so sánh nội dung.
- `new Book[2]` chỉ tạo **một mảng**. Hai ô của mảng nhận giá trị null, không tự nhận Book. Vì vậy `afterArray=2`.
- Gán `slots[0] = first`, `shelf.current = first` và `shared = second` chỉ lưu **các tham chiếu sẵn có**.
- `first = second` không xóa Book cũ. `alias` và `slots[0]` vẫn tham chiếu Book cũ; không có constructor được gọi thêm.
- `pending.title` cố truy cập field qua `null`, gây `NullPointerException` và được chương trình bắt để in thông báo xác định.

**Lưu ý:** `constructed` chỉ là bộ đếm *lần gọi constructor Book trong ví dụ một luồng*. Nó không phải bộ đếm tất cả object trong JVM: còn có đối tượng Shelf, đối tượng mảng và các đối tượng khác.

## 3. Biến chưa gán, biến `null` và đối tượng thật

~~~java
Book local;             // Khai bao local; CHUA duoc gan.
// System.out.println(local); // Loi bien dich: chua definite assignment.

local = null;           // Da gan, nhung khong co doi tuong.
System.out.println(local == null); // true.

local = new Book("A");  // Bay gio co doi tuong Book.
System.out.println(local.title);   // A.
~~~

Có ba trạng thái cần tách biệt:

1. **Biến cục bộ chưa được gán chắc chắn:** compiler **không cho phép đọc** giá trị.
2. **Biến đã gán `null`:** đọc chính biến hợp lệ, nhưng gọi phương thức hoặc đọc field qua nó sẽ ném `NullPointerException`.
3. **Biến trỏ tới một đối tượng:** có thể truy cập thành viên của đối tượng đó theo quyền truy cập và trạng thái thực tế.

Field của lớp/đối tượng khác local variable: `Book field;` ở cấp lớp nhận giá trị mặc định `null`. Nhưng **null không phải là một đối tượng Book rỗng**. `new Book("")` tạo Book thật, còn `Book b = null;` thì không.


## 4. Biến có thể lấy tham chiếu từ nhiều nguồn

Sau `Book alias = first;`, cả hai biến giữ tham chiếu tới cùng một `Book`; gán `slots[0] = first` cũng không tạo `Book` mới. `Book shared = getBook();` nhận giá trị trả về của phương thức, còn field và tham số cũng có thể giữ tham chiếu đã có. Vì vậy **đếm biến hoặc phép gán không cho biết số đối tượng được tạo**; trong ví dụ chạy, bộ đếm constructor mới giúp theo dõi riêng các instance `Book`.


## 5. `final` có tạo hoặc bảo toàn đối tượng không?

`final Book b;` chỉ khai báo một biến chưa được gán ở phạm vi cục bộ. Nó chưa tạo `Book` và vẫn phải được gán chắc chắn trước khi đọc; sau lần gán hợp lệ, biến `final` không thể được gán lại. Bỏ tham chiếu bằng cách gán `null` ở biến khác cũng không hủy đối tượng ngay: một alias vẫn có thể dùng nó. Các điều này đều độc lập với việc constructor đã được gọi bao nhiêu lần.


## 6. Tự phân biệt các trường hợp dễ nhầm

| Mã | Có tạo `Book` mới? | Vì sao? |
| --- | --- | --- |
| `Book a;` hoặc `Book a = null;` | Không | Chỉ khai báo/gán biến |
| `Book b = a;` | Không | Sao chép tham chiếu |
| `new Book[2]` | Không | Tạo một **mảng** chứ không tạo `Book` |
| `Book b = new Book("X");` | Có | Một lần khởi tạo `Book` |
| `b = new Book("Y");` | Có | Khởi tạo thêm `Book` rồi gán lại `b` |

Đối tượng còn có thể đến từ lời gọi phương thức hoặc literal; không phải mọi tham chiếu đều được tạo tại dòng khai báo biến.

## 7. Bài tập tự kiểm tra

**Bài 1.** Bao nhiêu đối tượng `Book` được tạo?

~~~java
Book a = new Book("X");
Book b = a;
Book c = null;
~~~

*Đáp án:* **Một Book**. `b` chỉ là alias, `c` là null.

**Bài 2.** `Book[] a = new Book[3];` tạo bao nhiêu mảng và bao nhiêu Book?

*Đáp án:* Một **mảng Book[]**, **không có Book** mới; các phần tử đều null.

**Bài 3.** Tại sao `Book x; System.out.println(x);` ở trong phương thức không biên dịch?

*Đáp án:* `x` là biến cục bộ chưa được gán chắc chắn (*definite assignment*).

**Bài 4.** `Book x = new Book("One"); Book y = x; x = new Book("Two");`. `y` trỏ đối tượng nào?

*Đáp án:* Vẫn là Book “One”, còn `x` trỏ Book “Two”.

**Bài 5.** `Book x = null;` có tương đương `Book x = new Book("");` không?

*Đáp án:* Không. Câu đầu không tạo object; câu sau tạo một Book với `title` là chuỗi rỗng.

## Checklist

- [ ] Phân biệt **khai báo biến** với **tạo đối tượng**.
- [ ] Phân biệt local variable chưa gán với reference có giá trị null.
- [ ] Biết phép gán tham chiếu tạo **alias**, không nhân bản object.
- [ ] Biết mảng tham chiếu là **đối tượng mảng**, các phần tử ban đầu null.
- [ ] Biết gán lại biến không thay đổi các tham chiếu khác đến object cũ.
- [ ] Không dùng `==` để suy ra hai đối tượng có cùng nội dung.
- [ ] Không nghĩ `final` hay gán `null` sẽ tự tạo/xóa object.

**Ghi nhớ:** Một biến là **nơi giữ giá trị tham chiếu**; một đối tượng là **thực thể được tạo và có trạng thái**. Đếm số lần tạo đối tượng phù hợp, **không đếm tên biến**, khi phân tích chương trình Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="references-and-null-003">new tạo đối tượng và trả về tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# `new` tạo đối tượng và trả về tham chiếu — Java 17+

## 1. Mục tiêu: phân biệt đối tượng với biến tham chiếu

Trong Java, biểu thức `new` dùng để **tạo một đối tượng mới** của lớp có thể khởi tạo (hoặc một mảng mới). Với việc tạo instance của class, Java cấp phát và khởi tạo đối tượng, thực thi constructor tương ứng; nếu quá trình thành công, **giá trị của biểu thức `new` là một tham chiếu tới đối tượng vừa tạo**.

~~~java
Person person = new Person("Lan");
~~~

Hãy tách câu lệnh trên thành ba ý:

1. `Person` là **kiểu khai báo của biến** `person`.
2. `new Person("Lan")` **tạo một instance** của `Person`, gọi constructor với đối số `"Lan"` và tạo ra giá trị tham chiếu đến instance ấy nếu thành công.
3. `=` **gán giá trị tham chiếu** này vào biến `person`.

Biến `person` **không phải bản thân đối tượng**. Nó giữ một giá trị tham chiếu giúp chương trình truy cập đối tượng.

~~~java
final class Person {
    private final String name;

    Person(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }
}

// Trong method:
Person person = new Person("Lan");
System.out.println(person.getName()); // Lan
~~~

Một biến tham chiếu có thể được **gán lại** để trỏ đến đối tượng khác; còn đối tượng đã tạo có thể có nhiều biến cùng tham chiếu tới nó.

## 2. `new` và constructor: ai làm việc gì?

Cú pháp cơ bản khi tạo đối tượng:

~~~java
ClassName variable = new ClassName(arguments);
~~~

- `new` mở đầu biểu thức tạo instance.
- `ClassName` xác định lớp được khởi tạo.
- Dấu `(...)` truyền đối số cho **constructor** phù hợp.
- Toàn bộ biểu thức `new ClassName(...)` có kiểu tham chiếu của instance vừa tạo (có thể gán vào biến có kiểu tương thích).

Ví dụ một lớp có hai constructor:

~~~java
final class Book {
    private final String title;
    private final int pages;

    Book(String title) {
        this(title, 0); // Gọi constructor khác cùng lớp
    }

    Book(String title, int pages) {
        this.title = title;
        this.pages = pages;
    }

    String describe() {
        return title + " (" + pages + " pages)";
    }
}

// Trong method:
Book first = new Book("Java");
Book second = new Book("Java", 120);
System.out.println(first.describe());  // Java (0 pages)
System.out.println(second.describe()); // Java (120 pages)
~~~

Constructor có **cùng tên lớp** và **không có kiểu trả về**, kể cả `void`. Không viết `new` trong định nghĩa constructor.

**Phân biệt:** constructor không phải một method thông thường “trả về đối tượng”; **biểu thức tạo instance `new`** mang giá trị tham chiếu sau khi constructor hoàn tất thành công.

### Có bắt buộc phải lưu vào biến không?

Không. Tham chiếu từ `new` là một giá trị biểu thức, nên có thể dùng trực tiếp:

~~~java
System.out.println(new Book("Direct", 20).describe());
// Direct (20 pages)
~~~

Với object cần dùng lại, nên giữ vào biến có tên rõ nghĩa để code dễ đọc.

## 3. Mỗi lần `new` riêng tạo một instance riêng biệt

~~~java
Person a = new Person("Lan");
Person b = new Person("Lan");

System.out.println(a == b); // false
~~~

Dù cả hai có nội dung `name` giống nhau, đó là **hai đối tượng khác nhau** vì chúng đến từ hai lần tạo instance bằng `new` riêng biệt.

So sánh biến tham chiếu bằng `==` kiểm tra **đồng nhất đối tượng** (*reference identity*): hai tham chiếu có chỉ cùng một object hay không. Nó **không so sánh tự động các field**.

| Câu lệnh | Số instance `Person` mới | Giá trị tham chiếu |
|---|---:|---|
| `Person a = new Person("Lan");` | 1 | `a` trỏ instance mới |
| `Person b = a;` | 0 | `b` sao chép tham chiếu từ `a` |
| `Person c = new Person("Lan");` | 1 | `c` trỏ instance khác |
| `b = c;` | 0 | `b` đổi sang tham chiếu của `c` |

**Quy tắc:** **phép gán tham chiếu không tạo bản sao đối tượng**. Muốn có đối tượng khác, cần tạo riêng hoặc dùng cơ chế sao chép phù hợp do API cung cấp.


## 4. Dùng kết quả của `new`

Kết quả `new Book("Java")` có thể gán ngay cho biến, trả về từ phương thức hoặc truyền trực tiếp làm đối số. Sau đó `Book alias = book;` chỉ sao chép tham chiếu, trong khi `alias = new Book("Khác");` mới thực hiện thêm một lần khởi tạo. Tham số phương thức nhận **bản sao giá trị tham chiếu**; gán lại tham số không gán lại biến của bên gọi. Muốn thay object ở phía gọi, hãy trả tham chiếu mới và để bên gọi chủ động gán.

## 5. `new` cho mảng: array object khác phần tử object

`new` cũng được dùng để tạo **mảng**:

~~~java
int[] numbers = new int[3];
System.out.println(numbers.length); // 3
System.out.println(numbers[0]);     // 0
~~~

`new int[3]` tạo một array object có 3 phần tử `int`, được khởi tạo mặc định bằng `0`.

Nhưng mảng tham chiếu chỉ tạo **mảng chứa các tham chiếu**, không tự tạo các đối tượng ở từng phần tử:

~~~java
Box[] slots = new Box[2];

System.out.println(slots.length); // 2
System.out.println(slots[0] == null); // true

slots[0] = new Box(50);
System.out.println(slots[0].value); // 50
~~~

Ở đây có **hai lần tạo** thuộc hai loại khác nhau:

- `new Box[2]` tạo **một mảng**, ban đầu hai phần tử tham chiếu đều `null`.
- `new Box(50)` tạo **một instance Box** riêng và gán tham chiếu cho `slots[0]`.

**Đừng nhầm** `new Box[2]` với “tự chạy constructor Box hai lần”.

## 6. Constructor và quá trình khởi tạo đối tượng

Khi thực thi `new ClassName(...)` hợp lệ, Java chuẩn bị một instance, áp dụng giá trị mặc định cho các field, thực hiện quy trình khởi tạo lớp/instance và lời gọi constructor theo quy tắc kế thừa/khởi tạo của ngôn ngữ.

Ví dụ ngắn:

~~~java
final class Ticket {
    int id; // Field ban đầu có giá trị mặc định 0

    Ticket(int id) {
        this.id = id;
    }
}

// Trong method:
Ticket ticket = new Ticket(42);
System.out.println(ticket.id); // 42
~~~

Những ý quan trọng:

- Field được khởi tạo theo quy tắc Java; constructor có thể gán giá trị riêng.
- Constructor có thể **ném exception**. Nếu quá trình khởi tạo thất bại, biểu thức `new` không hoàn tất bình thường để cung cấp một tham chiếu kết quả cho phép gán đó.
- `new` có thể gặp lỗi tài nguyên, ví dụ `OutOfMemoryError`; **không được coi mọi lần gọi new luôn thành công**.
- Constructor không bắt buộc chỉ gán field; nó có thể thực hiện validation và các logic khởi tạo khác. Tuy nhiên, nên giữ trạng thái đối tượng nhất quán và không phát tán `this` đang khởi tạo dở một cách tùy tiện.

Về mô hình bộ nhớ, object thường được nhắc tới là “nằm trên heap”, nhưng JVM/JIT có thể tối ưu cấp phát trong một số trường hợp. Điều quan trọng đối với logic nguồn Java là **ngữ nghĩa instance và identity**, không phải đoán một địa chỉ vật lý.


## 7. Quan sát object vừa tạo

`new Box(10) == new Box(10)` là `false` vì mỗi vế tạo instance riêng. `equals` chỉ so giá trị nếu class có định nghĩa hợp đồng tương ứng; mặc định `Object.equals` so identity. Không lấy chuỗi từ `Object.toString()` làm địa chỉ bộ nhớ: dạng `ClassName@hash` không bảo đảm là địa chỉ thực. Nếu cần in trạng thái dễ đọc, hãy tự định nghĩa `toString()` như ví dụ `Counter` bên dưới.

## 8. Chương trình Java 17 chạy độc lập

Tạo tệp chính xác **`NewReferenceDemo.java`** với nội dung:

~~~java
public final class NewReferenceDemo {
    private NewReferenceDemo() {}

    static final class Counter {
        static int created;

        private final String name;
        private int value;

        Counter(String name, int value) {
            this.name = name;
            this.value = value;
            created++;
        }

        void add(int amount) {
            value += amount;
        }

        @Override
        public String toString() {
            return name + "=" + value;
        }
    }

    static void mutate(Counter counter) {
        counter.add(2);
    }

    static void rebindParameter(Counter counter) {
        counter = new Counter("Local", 99);
        System.out.println("inside rebind=" + counter);
    }

    public static void main(String[] args) {
        Counter first = new Counter("A", 10);
        Counter alias = first;
        Counter other = new Counter("A", 10);

        System.out.println("first=" + first);
        System.out.println("alias same=" + (first == alias));
        System.out.println("other same=" + (first == other));

        alias.add(5);
        System.out.println("after alias first=" + first);
        System.out.println("other unaffected=" + other);

        alias = new Counter("B", 1);
        System.out.println("alias rebound=" + alias);
        System.out.println("first still=" + first);

        mutate(first);
        System.out.println("after method mutate=" + first);

        rebindParameter(first);
        System.out.println("after method rebind=" + first);

        Counter missing = null;
        System.out.println("missing is null=" + (missing == null));
        try {
            missing.add(1);
        } catch (NullPointerException ex) {
            System.out.println("null access=NullPointerException");
        }

        Counter[] slots = new Counter[2];
        System.out.println("slots length=" + slots.length);
        System.out.println("default slot null=" + (slots[0] == null));
        slots[0] = new Counter("Slot", 3);
        System.out.println("slot object=" + slots[0]);

        int[] numbers = new int[2];
        System.out.println("int defaults=" + numbers[0]
                + "," + numbers[1]);

        System.out.println("Counter instances created=" + Counter.created);
    }
}
~~~

Dùng **JDK 17+** và mở terminal tại thư mục chứa source.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 NewReferenceDemo.java
java -cp . NewReferenceDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\NewReferenceDemo.java
java -cp . NewReferenceDemo
~~~

**Expected output (không phụ thuộc địa chỉ/hash object):**

~~~text
first=A=10
alias same=true
other same=false
after alias first=A=15
other unaffected=A=10
alias rebound=B=1
first still=A=15
after method mutate=A=17
inside rebind=Local=99
after method rebind=A=17
missing is null=true
null access=NullPointerException
slots length=2
default slot null=true
slot object=Slot=3
int defaults=0,0
Counter instances created=5
~~~

### Giải thích cách đếm `new`

Biến `Counter.created` chỉ tăng ở **constructor `Counter(...)`**. Trong chương trình có 5 lần tạo instance `Counter`:

1. `first = new Counter("A", 10)`.
2. `other = new Counter("A", 10)`.
3. `alias = new Counter("B", 1)`.
4. `counter = new Counter("Local", 99)` bên trong method.
5. `slots[0] = new Counter("Slot", 3)`.

Các lệnh `alias = first`, `mutate(first)` và `new Counter[2]` **không tạo thêm instance Counter**. `new Counter[2]` chỉ tạo một **array object**. `new int[2]` cũng tạo một array object và các phần tử số có giá trị mặc định 0.

Phép `alias = new Counter(...)` và phép gán lại tham số trong `rebindParameter` **không làm biến `first` đổi tham chiếu**. Chỉ `mutate(first)` sửa trạng thái đối tượng mà `first` đang trỏ tới.


## 9. Kiểm tra đúng ý nghĩa biểu thức tạo đối tượng

- `new SomeType[n]` luôn tạo mảng nếu thành công; với kiểu phần tử tham chiếu, các phần tử ban đầu bằng `null` và **chưa tạo** `SomeType`.
- Nếu constructor ném exception, biểu thức tạo instance không hoàn tất bình thường nên phép gán kết quả không xảy ra.
- Biểu thức `new` cũng có thể thất bại do tài nguyên; không dựa trên giả định mọi lần khởi tạo đều thành công.
- `new` trả giá trị tham chiếu, còn constructor không khai báo kiểu trả về; phân biệt hai ý này khi đọc mã.


## 10. Bài tập tập trung vào `new`

**Bài A.** `Book a = new Book("A"); Book b = a; Book c = new Book("A");` thực hiện bao nhiêu lần tạo instance `Book`?

*Đáp án:* Hai. Phép `b = a` không gọi constructor.

**Bài B.** `Book[] shelf = new Book[3]; shelf[0] = new Book("B");` tạo bao nhiêu đối tượng mảng và bao nhiêu `Book`?

*Đáp án:* Một mảng và một `Book`. Hai phần tử còn lại vẫn bằng `null`.

**Bài C.** Nếu `new Book(null)` gọi constructor có `Objects.requireNonNull(title)` rồi ném `NullPointerException`, biến nhận kết quả của phép gán có được cập nhật không?

*Đáp án:* Không. Khi constructor thất bại, phép gán không được thực hiện. Nếu là biến cục bộ trước đó chưa được gán, sau nhánh thất bại vẫn phải tuân thủ quy tắc *definite assignment*.

## 11. Checklist và kết luận

- [ ] Phân biệt **instance object** với **biến tham chiếu**.
- [ ] Hiểu `new ClassName(...)` tạo instance, chạy constructor và tạo giá trị tham chiếu nếu thành công.
- [ ] Phân biệt `new ClassName(...)` với `ClassName other = existing;`.
- [ ] Biết `==` so **cùng object** khi áp dụng cho hai tham chiếu.
- [ ] Nhận ra hai biến alias có thể sửa cùng một object.
- [ ] Hiểu phép gán lại một biến không tự sửa object mà biến đó từng trỏ.
- [ ] Phân biệt biến cục bộ chưa gán, tham chiếu `null` và tham chiếu tới object.
- [ ] Biết `new SomeClass[n]` tạo **mảng**, không tự tạo `n` instance của class.
- [ ] Hiểu Java truyền tham chiếu theo **giá trị**, không pass-by-reference.
- [ ] Không suy ra địa chỉ vật lý hoặc thời điểm GC từ biểu thức `new`.
- [ ] Biên dịch và chạy `NewReferenceDemo.java`, đối chiếu từng dòng output.

**Kết luận:** `new` là biểu thức **tạo instance và cho kết quả là tham chiếu** khi khởi tạo thành công. Gán tham chiếu không nhân bản object; nhiều biến có thể cùng trỏ một instance, còn mỗi biểu thức tạo instance `new ClassName(...)` thực thi riêng biểu thị một lần tạo instance riêng. Hiểu rõ điều này giúp xử lý đúng identity, alias, tham số method, `null` và vòng đời đối tượng trong Java 17+.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="references-and-null-004">Gán tham chiếu làm nhiều biến cùng dùng một đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Gán tham chiếu làm nhiều biến cùng dùng một đối tượng trong Java

> **Java core — language-basics / Tham chiếu và null**  
> **Yêu cầu:** JDK 17+; dùng thư viện chuẩn, một file Java chạy độc lập.  
> **Mục tiêu:** Hiểu phép gán giữa các biến kiểu tham chiếu **sao chép giá trị tham chiếu**, không sao chép object; phân biệt **sửa trạng thái object** với **gán lại biến** và biết cách hạn chế tác dụng phụ do nhiều biến cùng trỏ tới một object.

## 1. Biến tham chiếu không chứa một bản sao của object

Ví dụ:

~~~java
class Box {
    int value;
    Box(int value) {
        this.value = value;
    }
}

Box first = new Box(10);
Box second = first;
~~~

Dòng `new Box(10)` tạo một object `Box`. Biến `first` giữ tham chiếu đến object ấy. Dòng `second = first` **sao chép giá trị tham chiếu** sang `second` — không gọi `new` và **không tạo bản sao**.

Sau phép gán, hai tên cùng trỏ đến **một object**:

~~~mermaid
flowchart LR
    A["first"] --> O["Box object: value = 10"]
    B["second"] --> O
~~~

Cách hình dung: `first` và `second` là **hai biến riêng**, nhưng lúc này giá trị của hai biến là tham chiếu tới **cùng một đối tượng**. Các biến không trỏ tới nhau.

| Thao tác | Object có được tạo mới? | Điều xảy ra |
| --- | --- | --- |
| `Box first = new Box(10);` | Có | Tạo Box và lưu tham chiếu trong first |
| `Box second = first;` | Không | Hai biến trỏ chung một Box |
| `second.value = 20;` | Không | Sửa Box chung, đọc `first.value` cũng được 20 |
| `second = new Box(30);` | Có | second đổi sang Box mới; first vẫn trỏ Box cũ |
| `second = null;` | Không | Chỉ second không trỏ tới object nào; first không đổi |

**Điểm cốt lõi:** `second = first` không phải *deep copy*, *shallow copy object* hay phép sao chép toàn bộ dữ liệu. Đây là **gán tham chiếu**.


## 2. Theo dõi một alias khi trạng thái thay đổi

Nếu `Box b = a;` rồi `b.value = 20;`, cả hai biến vẫn tham chiếu một object và `a.value` thành `20`. Nếu tiếp tục `b = new Box(30);`, chỉ `b` đổi sang object mới, `a.value` vẫn là `20`. Hai tình huống này khác nhau vì thao tác thứ nhất sửa **object chia sẻ**, thao tác thứ hai sửa **giá trị tham chiếu trong biến**. Trong ví dụ đầy đủ sau, hãy theo dõi các nhánh alias của object gốc thay vì chỉ nhìn tên biến.

## 3. Ví dụ Java 17+ runnable

Tạo tệp **ReferenceAliasDemo.java**:

~~~java
public final class ReferenceAliasDemo {
    private static final class Box {
        private int score;

        Box(int score) {
            this.score = score;
        }

        void add(int amount) {
            score += amount;
        }

        int getScore() {
            return score;
        }
    }

    private static void addPoints(Box box) {
        box.add(5); // Sửa chính object mà bên gọi đang giữ.
    }

    private static void rebind(Box box) {
        box = new Box(999); // Chỉ đổi biến tham số cục bộ.
        System.out.println("insideRebind=" + box.getScore());
    }

    public static void main(String[] args) {
        Box first = new Box(10);
        Box alias = first;

        System.out.println("sameReference=" + (first == alias));

        alias.add(10);
        System.out.println("viaFirst=" + first.getScore());

        addPoints(first);
        System.out.println("viaAlias=" + alias.getScore());

        rebind(alias);
        System.out.println("afterCall=" + alias.getScore());

        alias = new Box(30);
        System.out.println("sharedAfterRebind=" + (first == alias));
        System.out.println("firstScore=" + first.getScore());
        System.out.println("aliasScore=" + alias.getScore());

        Box third = first;
        third = null;
        System.out.println("thirdNull=" + (third == null));
        System.out.println("firstStill=" + first.getScore());

        int[] left = {1, 2};
        int[] right = left;
        right[0] = 9;
        System.out.println("arrayFirst=" + left[0]);

        String textA = "Hi";
        String textB = textA;
        textB = textB.concat("!");
        System.out.println("textA=" + textA);
        System.out.println("textB=" + textB);

        Box independent = new Box(first.getScore());
        independent.add(100);
        System.out.println("separateCopy=" + independent.getScore());
        System.out.println("originalStill=" + first.getScore());
    }
}
~~~

Biên dịch và chạy tại thư mục có file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ReferenceAliasDemo.java
java ReferenceAliasDemo
~~~

**Expected output (chính xác):**

~~~text
sameReference=true
viaFirst=20
viaAlias=25
insideRebind=999
afterCall=25
sharedAfterRebind=false
firstScore=25
aliasScore=30
thirdNull=true
firstStill=25
arrayFirst=9
textA=Hi
textB=Hi!
separateCopy=125
originalStill=25
~~~

**Giải thích các mốc quan trọng:**

1. `alias = first` làm `first == alias` là `true`.
2. Gọi `alias.add(10)` tăng object chung từ 10 lên 20; đọc qua `first` thấy 20. `addPoints(first)` tăng tiếp thành 25; đọc qua `alias` vẫn thấy 25.
3. Bên trong `rebind`, tham số `box` được gán sang Box mới (999). Khi phương thức trở về, `alias` của bên gọi vẫn trỏ Box điểm 25.
4. `alias = new Box(30)` làm `first` và `alias` trỏ **hai Box khác nhau**, có điểm lần lượt 25 và 30.
5. Gán `third = null` chỉ thay đổi biến `third`, không xóa Box được `first` giữ.
6. Mảng cũng là object: `right = left` làm hai biến dùng chung mảng; sửa `right[0]` thì `left[0]` thành 9.
7. `String` bất biến: `concat` tạo kết quả chuỗi `"Hi!"`, còn `textA` vẫn là `"Hi"`.
8. `new Box(first.getScore())` tạo Box riêng có giá trị ban đầu 25; tăng bản riêng thành 125 không đổi Box của `first`.


## 4. Alias được truyền qua tham số

Java truyền **theo giá trị**: method nhận bản sao tham chiếu. Vì thế `box.add(5)` có thể sửa object mà caller cũng giữ, còn `box = new Box(100)` chỉ gán lại tham số của method. Đây cũng là lý do `rebind(alias)` trong chương trình chỉ in `insideRebind=999` mà `alias` của caller vẫn có điểm `25`.


## 5. Aliasing không đồng nghĩa mọi dữ liệu đều sửa được

Mảng cũng là đối tượng có thể sửa: sau `int[] b = a; b[0] = 9;`, đọc `a[0]` được `9`. Trái lại, `String` bất biến; `String b = a; b = b.concat("!");` chỉ đổi tham chiếu của `b` sang kết quả, không sửa chuỗi mà `a` giữ. Gán `b = null` chỉ bỏ alias ở `b`, và biến `final` chỉ cấm gán lại chính nó; hai quy tắc này không làm object gốc biến mất hay bất biến.

## 6. Khi nào cần sao chép đối tượng?

Dùng chung tham chiếu hữu ích khi nhiều nơi **cố ý chia sẻ cùng trạng thái**. Nhưng nếu một nơi cần sửa mà không ảnh hưởng nơi khác, phải tạo **object độc lập**.

~~~java
Box original = new Box(10);
Box shared = original;                  // Cùng object.
Box independent = new Box(original.getScore()); // Object mới.
~~~

`independent.add(5)` không sửa `original` trong ví dụ Box chỉ có trường primitive `score`. Tuy nhiên, với object chứa tham chiếu tới các object mutable lồng bên trong, chỉ sao chép các field là **shallow copy** và có thể vẫn chia sẻ đối tượng con. Khi yêu cầu độc lập hoàn toàn, cần cân nhắc **deep copy** phù hợp với mô hình dữ liệu.

Với mảng primitive, `int[] copy = originalArray.clone();` tạo mảng mới với các giá trị độc lập. Với mảng các tham chiếu, `clone()` tạo **mảng mới**, nhưng các phần tử tham chiếu vẫn có thể trỏ tới **cùng object con**. Không áp dụng “clone = deep copy” cho mọi kiểu.

## 7. Lỗi thường gặp và câu hỏi tự kiểm tra

| Hiểu lầm | Sửa lại cho đúng |
| --- | --- |
| “`b = a` sẽ copy toàn bộ object.” | Sai: chỉ sao chép **giá trị tham chiếu**, không tạo object mới |
| “Sửa `b.score` không ảnh hưởng `a` vì là hai biến.” | Nếu `a == b` thì sửa cùng object, a nhìn thấy thay đổi |
| “`b = new Box(...)` làm `a` cũng đổi object.” | Sai: chỉ biến b được gán lại |
| “Gán `b = null` sẽ xóa object đang dùng chung.” | Sai: alias khác vẫn có thể tham chiếu object ấy |
| “Java pass-by-reference nên method đổi tham số sẽ đổi biến bên gọi.” | Sai: Java pass-by-value, sao chép giá trị tham chiếu |
| “Mảng gán bằng `=` sẽ tự sao chép các phần tử.” | Sai: hai biến cùng tham chiếu đến mảng cũ |
| “`String.concat` sửa nội dung String đang được chia sẻ.” | Sai: String bất biến; kết quả concat không sửa object gốc |

**Bài tập 1:** Cho `Box x = new Box(2); Box y = x;`. Dự đoán kết quả nếu gọi `y.add(3)` rồi đọc `x.getScore()`. `x == y` là true hay false?

**Bài tập 2:** Sau bài 1, thực hiện `y = new Box(10);`. Hỏi `x.getScore()` và `y.getScore()` bằng bao nhiêu; có bao nhiêu object Box đã được tạo?

**Bài tập 3:** Với `int[] a = {1, 2}; int[] b = a;`, hãy sửa `b[1]` thành 99 rồi in `a[1]`. Sau đó thử `b = a.clone(); b[1] = 5;`: kết quả `a[1]` có thay đổi tiếp không?

**Bài tập 4:** Viết phương thức nhận `Box` và thử hai thao tác: `box.add(1)`, `box = new Box(0)`. Giải thích vì sao chỉ một thao tác được bên gọi quan sát trên Box gốc.

**Bài tập 5:** Cho `String a = "Hi"; String b = a; b = b + "!";`. Dự đoán `a` và `b`, phân biệt **gán tham chiếu** với **tính bất biến của String**.

### Ghi nhớ

**Gán `b = a` với kiểu tham chiếu tạo thêm một alias tới cùng object, không sao chép object.** Mọi alias có thể nhìn thấy thay đổi **trạng thái object mutable**. Ngược lại, **gán lại biến** (sang object khác hoặc `null`) chỉ thay đổi biến ấy. Java truyền cả tham chiếu **theo giá trị**, và khi cần dữ liệu độc lập phải **tạo bản sao phù hợp**, không chỉ gán sang tên biến mới.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="references-and-null-005">Thay đổi trạng thái khác với gán lại biến tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Thay đổi trạng thái khác với gán lại biến tham chiếu trong Java

> **Java core — language-basics / Tham chiếu và null**  
> **Yêu cầu:** JDK 17 trở lên; chỉ sử dụng Java standard library.  
> **Mục tiêu:** Phân biệt **sửa trạng thái đối tượng** (*mutation*) với **gán lại một biến tham chiếu** (*reassignment*); dự đoán chính xác tác động qua alias, tham số phương thức, `final`, mảng và `String`.

## 1. Hai thao tác, hai đối tượng tác động khác nhau

Xét một lớp có trạng thái thay đổi được:

~~~java
class Meter {
    int value;
    Meter(int value) { this.value = value; }
}
~~~

Sau đó:

~~~java
Meter first = new Meter(10);
Meter second = first;

second.value = 15;       // Mutation: sửa object đang được cả hai biến tham chiếu.
second = new Meter(7);   // Reassignment: chỉ đổi tham chiếu lưu trong second.
~~~

- **Mutation:** `second.value = 15` thay đổi trường `value` của **object Meter hiện có**. Khi `first` và `second` còn trỏ cùng object, đọc `first.value` thấy **15**.
- **Reassignment:** `second = new Meter(7)` tạo object mới rồi thay **giá trị tham chiếu của biến `second`**. Biến `first` vẫn trỏ object cũ có `value = 15`.
- **Phép gán `second = first`** tự nó chỉ sao chép giá trị tham chiếu, **không tạo bản sao object**.

Trước thao tác gán lại:

~~~text
first  ─┐
        ├──> Meter(value = 15)
second ─┘
~~~

Sau `second = new Meter(7)`:

~~~text
first  ────> Meter(value = 15)
second ────> Meter(value = 7)
~~~

**Điểm mấu chốt:** Mutation đổi **dữ liệu bên trong object**; reassignment đổi **object mà một biến tham chiếu đang trỏ tới**. Một thao tác không tự động làm thao tác còn lại xảy ra.

## 2. Bảng dự đoán hành vi

Giả sử khởi đầu với `Meter a = new Meter(10); Meter b = a;`:

| Câu lệnh tiếp theo | `a.value` | `b.value` / liên hệ | Giải thích |
| --- | ---: | --- | --- |
| `b.value = 12;` | 12 | 12; `a == b` là true | Cùng object bị sửa |
| `b = new Meter(20);` | 12 | 20; `a == b` là false | Chỉ b nhận tham chiếu mới |
| `b.value = 25;` | 12 | 25 | Sửa object mới của b |
| `b = a;` | 12 | 12; `a == b` là true | b trỏ lại object cũ |
| `b = null;` | 12 | b là null | Chỉ b không còn trỏ tới object |

Các hàng này được thực hiện **theo thứ tự** từ trên xuống. `a == b` là phép so sánh **identity tham chiếu**, không phải kiểm tra hai object có dữ liệu giống nhau hay không.

Gán `b = null` cũng **không hủy object**: nếu `a` còn tham chiếu, object vẫn có thể được dùng. Đừng coi `null` là lệnh giải phóng bộ nhớ ngay lập tức.

## 3. Chương trình Java 17+ chạy độc lập

Tạo tệp **MutationVsReassignmentDemo.java**:

~~~java
public final class MutationVsReassignmentDemo {
    private static final class Meter {
        private int value;

        Meter(int value) {
            this.value = value;
        }

        void increase(int amount) {
            value += amount;
        }

        int value() {
            return value;
        }
    }

    private static void mutate(Meter meter) {
        meter.increase(3);
    }

    private static void redirect(Meter meter) {
        meter = new Meter(99);
        System.out.println("insideRedirect=" + meter.value());
    }

    public static void main(String[] args) {
        Meter original = new Meter(10);
        Meter alias = original;

        System.out.println("initialSame=" + (original == alias));
        alias.increase(5);
        System.out.println("afterMutation=" + original.value());

        mutate(original);
        System.out.println("afterMethodMutation=" + alias.value());

        redirect(original);
        System.out.println("afterMethodRedirect=" + original.value());

        alias = new Meter(7);
        System.out.println("sameAfterReassign=" + (original == alias));
        System.out.println("original=" + original.value());
        System.out.println("alias=" + alias.value());

        alias = null;
        System.out.println("aliasNull=" + (alias == null));
        System.out.println("originalStill=" + original.value());

        final Meter fixed = original;
        fixed.increase(2);
        System.out.println("finalObjectValue=" + original.value());

        int[] firstArray = {1, 2};
        int[] secondArray = firstArray;
        secondArray[0] = 9;
        System.out.println("sharedArray=" + firstArray[0]);
        secondArray = new int[] {4, 5};
        System.out.println("afterArrayReassign=" + firstArray[0]
                + "," + secondArray[0]);

        String text1 = "Hi";
        String text2 = text1;
        text2 = text2.concat("!");
        System.out.println("strings=" + text1 + "," + text2);
    }
}
~~~

Biên dịch/chạy tại thư mục chứa tệp (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 MutationVsReassignmentDemo.java
java MutationVsReassignmentDemo
~~~

**Expected output chính xác:**

~~~text
initialSame=true
afterMutation=15
afterMethodMutation=18
insideRedirect=99
afterMethodRedirect=18
sameAfterReassign=false
original=18
alias=7
aliasNull=true
originalStill=18
finalObjectValue=20
sharedArray=9
afterArrayReassign=9,4
strings=Hi,Hi!
~~~

Đọc từng mốc:

1. `alias = original` cho hai biến cùng trỏ Meter 10, nên `initialSame=true`.
2. `alias.increase(5)` sửa object chung thành 15; `mutate(original)` sửa tiếp thành 18. Cả hai biến đều nhìn thấy.
3. `redirect(original)` nhận **bản sao giá trị tham chiếu** qua tham số. Dòng `meter = new Meter(99)` chỉ thay biến **meter** bên trong phương thức; khi quay về, `original.value()` vẫn là 18.
4. `alias = new Meter(7)` đổi object mà alias trỏ đến. `original` không đổi, nên `sameAfterReassign=false`.
5. `alias = null` chỉ bỏ tham chiếu của alias; `original` vẫn đọc được 18.
6. `fixed` là tham chiếu `final` trỏ tới cùng object của `original`. Phép `fixed.increase(2)` được phép nên giá trị object tăng thành 20.
7. Mảng cũng là object: sửa `secondArray[0]` làm `firstArray[0]` thành 9. Nhưng gán `secondArray = new int[] {...}` chỉ đổi biến secondArray; phần tử của firstArray vẫn 9.
8. `String` là immutable: `concat` không sửa chuỗi `"Hi"`. Biến `text2` được gán tham chiếu tới chuỗi kết quả `"Hi!"`; `text1` vẫn cho `"Hi"`.


## 4. Ranh giới giữa method sửa tại chỗ và trả object mới

Một method nhận `Meter` nhận **bản sao giá trị tham chiếu**. Gọi `meter.increase(3)` trong method sẽ sửa object được chia sẻ; gán `meter = new Meter(99)` chỉ đổi biến tham số. Nếu cần tạo một phiên bản độc lập, method có thể trả về `new Meter(old.value() + 1)` và bên gọi tự quyết định `original = nextMeter(original)`. Giao diện method nên làm rõ nó **mutate** đầu vào hay **trả object mới**.


## 5. `final` khóa biến, không khóa object

`final Meter meter = new Meter(1);` cho phép `meter.increase(1)` nếu API hỗ trợ, nhưng không cho phép `meter = new Meter(2)`. Cùng logic, `final int[] numbers = {1, 2};` vẫn cho phép `numbers[0] = 9`. Muốn dữ liệu không bị sửa bởi alias khác, phải dựa vào thiết kế bất biến, quyền truy cập và cách API công bố trạng thái; chỉ viết `final` ở biến là chưa đủ.


## 6. Thiết kế API theo chủ đích thay đổi trạng thái

Một API kiểu `void increase(Meter meter)` thể hiện thao tác sửa trạng thái đang chia sẻ; API kiểu `Meter increased(Meter old)` có thể tạo và trả về giá trị mới để caller chọn có gán lại biến hay không. Với đối tượng mutable, tài liệu API nên nói rõ liệu đầu vào có bị sửa và kết quả có chia sẻ tham chiếu với dữ liệu cũ.

Nếu không muốn người ngoài sửa trạng thái nội bộ, cân nhắc bản sao bảo vệ (*defensive copy*) hoặc kiểu bất biến. Sao chép một mảng tham chiếu bằng `clone()` tạo mảng mới nhưng vẫn có thể chia sẻ các phần tử object mutable, vì vậy phải xét cấu trúc dữ liệu trước khi chọn cách copy.

## 7. Lỗi hay gặp và bài tập

| Ngộ nhận | Cách hiểu đúng |
| --- | --- |
| “`b = a` sao chép cả object.” | Sai: chỉ sao chép **giá trị tham chiếu** |
| “Sửa qua b chỉ ảnh hưởng b.” | Sai nếu a và b cùng tham chiếu object mutable |
| “`b = new Meter(...)` làm a đổi luôn.” | Sai: chỉ biến b nhận tham chiếu mới |
| “Method gán `param = new ...` sẽ đổi biến bên gọi.” | Sai: Java truyền tham số theo giá trị |
| “`b = null` xóa object.” | Sai: chỉ đổi biến b; alias khác còn dùng object |
| “`final` cấm mọi thay đổi object.” | Sai: `final` cấm gán lại biến, không cấm mutation |
| “`String.concat` sửa String cũ.” | Sai: `String` bất biến; kết quả là giá trị chuỗi mới/có thể được tái dùng tùy API |

**Bài tập 1.** Khởi tạo `Meter a = new Meter(3); Meter b = a;`, rồi gọi `b.increase(2)`. Dự đoán `a.value()` và `a == b`.

**Bài tập 2.** Sau bài 1, thực hiện `b = new Meter(10);` và `b.increase(1)`. Những thao tác nào ảnh hưởng a, những thao tác nào chỉ ảnh hưởng b?

**Bài tập 3.** Truyền a vào `changeValue` và `changeVariable`. Thử dự đoán trước khi chạy: object của a có đổi không? Biến a có trỏ object khác không?

**Bài tập 4.** Với `final int[] nums = {1, 2};`, hãy thử `nums[1] = 7;` và `nums = new int[]{3, 4};`. Dòng nào hợp lệ, vì sao?

**Bài tập 5.** Viết `String a = "X"; String b = a; b += "Y";` rồi in a và b. Tại sao đây là **gán lại tham chiếu tới kết quả** chứ không phải sửa object String?

### Tổng kết

**Mutation** đổi trạng thái của object đang tồn tại và có thể quan sát qua **mọi alias** tới object ấy. **Reassignment** đổi tham chiếu được lưu trong **một biến**, không tự sửa object cũ hoặc gán lại các biến khác. Khi đọc mã Java, luôn hỏi: **“Dòng này sửa object hay đổi biến đang trỏ object?”** — đó là chìa khóa để dự đoán đúng hành vi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="references-and-null-006">== trên tham chiếu kiểm tra cùng đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# `==` trên tham chiếu kiểm tra cùng đối tượng — Java 17+

## 1. Mục tiêu: “cùng đối tượng” khác “cùng nội dung”

Trong Java, toán tử `==` có ý nghĩa khác nhau tùy **kiểu toán hạng**:

- Với **giá trị nguyên thủy** (*primitive*), `==` so sánh giá trị theo quy tắc của loại primitive tương ứng.
- Với **hai tham chiếu có thể so sánh được**, `==` trả `true` khi **cả hai tham chiếu cùng chỉ tới một đối tượng**, hoặc **đều là `null`**. Nó không tự so sánh từng field, từng ký tự hoặc từng phần tử mảng.

~~~java
int x = 10;
int y = 10;
System.out.println(x == y); // true: cùng giá trị int

String first = new String("Java");
String second = new String("Java");
System.out.println(first == second);      // false: hai object khác nhau
System.out.println(first.equals(second)); // true: cùng nội dung chuỗi
~~~

**Trọng tâm:** `==` trên tham chiếu trả lời câu hỏi **“hai biến này có đang chỉ đến đúng cùng một object không?”**, chứ không phải **“hai object có dữ liệu giống nhau không?”**.

Để giữ ví dụ dễ kiểm chứng, bài dùng Java 17+, class tự định nghĩa, chuỗi, mảng và `record` để phân biệt **object identity** và **value equality**.

## 2. Mỗi biến tham chiếu giữ một giá trị tham chiếu

Xét class:

~~~java
static final class Box {
    int value;

    Box(int value) {
        this.value = value;
    }
}
~~~

Trong một method:

~~~java
Box a = new Box(10);
Box alias = a;
Box b = new Box(10);

System.out.println(a == alias); // true
System.out.println(a == b);     // false
~~~

Tại sao?

1. `new Box(10)` lần đầu tạo một instance, rồi gán tham chiếu của instance đó cho `a`.
2. `alias = a` **sao chép tham chiếu**, không gọi `new`, nên `alias` và `a` cùng chỉ vào instance ban đầu.
3. `new Box(10)` lần thứ hai tạo **instance khác**, nên `b` không cùng identity với `a`, dù `a.value` và `b.value` đều bằng `10`.

Sơ đồ minh họa quan hệ, **không phải địa chỉ bộ nhớ**:

~~~mermaid
flowchart LR
  A["a"] --> X["Box #1: value = 10"]
  C["alias"] --> X
  B["b"] --> Y["Box #2: value = 10"]
~~~

| Biểu thức | Kết quả | Lý do |
|---|---|---|
| `a == alias` | `true` | Hai tham chiếu cùng object |
| `a == b` | `false` | Hai lần `new` tạo hai Box riêng |
| `a.value == b.value` | `true` | Hai field `int` có cùng giá trị |
| `alias == b` | `false` | Hai object khác nhau |

**Không có quy tắc “field giống nhau thì `==` true”** đối với tham chiếu.


## 3. Khi nào alias ảnh hưởng phép so identity?

`Box alias = a;` khiến `a == alias` là `true`; sửa `alias.value` không đổi kết quả vì cả hai vẫn trỏ một object. Sau `alias = new Box(99);`, phép so thành `false` dù `a` không bị sửa. Đây là quan hệ giữa **giá trị tham chiếu hiện tại** và identity, độc lập với việc hai object có field giống nhau.

## 4. `null`: trường hợp hai tham chiếu đều không trỏ object

`null` là giá trị tham chiếu đặc biệt thể hiện **không trỏ đến object nào**.

~~~java
Box first = null;
Box second = null;
Box actual = new Box(5);

System.out.println(first == second); // true
System.out.println(first == actual); // false
System.out.println(actual != null);  // true
~~~

**Điểm quan trọng:** `null == null` cho `true` theo nghĩa so sánh tham chiếu, **không** có nghĩa Java tạo ra “một object null” để cả hai cùng trỏ tới.

Kiểm tra `null` trước khi dereference là một trường hợp đúng và phổ biến để dùng `==`:

~~~java
static void printBox(Box box) {
    if (box == null) {
        System.out.println("No box");
        return;
    }
    System.out.println(box.value);
}
~~~

Còn:

~~~java
Box box = null;
// System.out.println(box.value); // Nếu thực thi sẽ có NullPointerException
~~~

thì truy cập field qua tham chiếu `null` gây lỗi lúc chạy.

**Phân biệt với biến cục bộ chưa gán**:

~~~java
Box box;
// System.out.println(box == null); // Lỗi compile: box chưa gán giá trị
box = null;
System.out.println(box == null); // true
~~~

Việc khai báo `Box box;` không tự khởi tạo biến cục bộ bằng `null`. Quy tắc **definite assignment** vẫn áp dụng.

## 5. `String`: vì sao nên so nội dung bằng `equals`

Với dữ liệu văn bản, ý định thường là so sánh **nội dung**:

~~~java
String a = new String("Hello");
String b = new String("Hello");

System.out.println(a == b);      // false: hai String instance riêng
System.out.println(a.equals(b)); // true: cùng nội dung ký tự
~~~

**`String.equals`** được class String định nghĩa để so sánh các dãy ký tự tương ứng. Nó khác toán tử `==` trên tham chiếu.

### Bẫy string pool

~~~java
String a = "Java";
String b = "Java";

System.out.println(a == b); // true
~~~

Hai **string literal giống hệt nhau** được *intern* theo quy tắc của Java, vì vậy trong ví dụ này chúng trỏ đến cùng một `String` đã được intern.

Nhưng **không được lấy kết quả `true` đó làm cách kiểm tra nội dung** trong code tổng quát:

~~~java
String literal = "Java";
String created = new String("Java");

System.out.println(literal == created);      // false
System.out.println(literal.equals(created)); // true
~~~

`new String("Java")` tạo object String mới; nội dung vẫn là `Java`, nhưng identity khác với literal được intern.

**Thực hành:** dùng `equals` khi muốn kiểm tra hai chuỗi có cùng nội dung. Khi một biến có thể `null`, cân nhắc cách so sánh an toàn:

~~~java
String input = null;

System.out.println("OK".equals(input));           // false
System.out.println(java.util.Objects.equals(input, "OK")); // false
~~~

Đừng gọi `input.equals(...)` khi `input` có thể `null`; lời gọi method trên null gây `NullPointerException`.

## 6. `equals()` không phải toán tử `==`

`==` là toán tử ngôn ngữ Java. Ngược lại, `equals(Object)` là một **method** khai báo trong `java.lang.Object`, và class có thể **override** để đưa ra tiêu chí so sánh nội dung.

~~~java
static final class NumberBox {
    final int number;

    NumberBox(int number) {
        this.number = number;
    }
}

NumberBox a = new NumberBox(7);
NumberBox b = new NumberBox(7);

System.out.println(a == b);      // false
System.out.println(a.equals(b)); // false: chưa override equals
~~~

Nếu class **không override `equals`**, implementation mặc định từ `Object` chỉ trả true cho cùng object. Không nên mặc định rằng `equals` luôn so các field.

Nếu nghiệp vụ cần so sánh theo giá trị, có thể tự định nghĩa `equals`/`hashCode` cho class, hoặc dùng kiểu đã hỗ trợ value equality phù hợp.

### `record` trong Java 17: ví dụ value equality rõ ràng

~~~java
record Point(int x, int y) {}

Point p = new Point(1, 2);
Point q = new Point(1, 2);
Point alias = p;

System.out.println(p == q);       // false
System.out.println(p.equals(q));  // true
System.out.println(p == alias);   // true
~~~

Một `record` đơn giản tự cung cấp `equals` và `hashCode` dựa trên các thành phần của record. Nhưng **`==` vẫn so identity**, không đổi thành phép so nội dung chỉ vì kiểu là `record`.

> **Lưu ý:** `equals` theo thành phần của record không tự làm các thành phần tham chiếu mutable trở nên bất biến hoặc buộc so sánh mảng theo nội dung. Với một record chứa mảng, cần đặc biệt chú ý vì `equals` của mảng mặc định so identity.

## 7. Mảng: cùng phần tử chưa chắc cùng đối tượng mảng

~~~java
int[] first = {2, 4, 6};
int[] second = {2, 4, 6};
int[] alias = first;

System.out.println(first == second); // false
System.out.println(first == alias);  // true
~~~

Mỗi khởi tạo mảng độc lập tạo một array object. Muốn **so nội dung của mảng một chiều**, dùng `java.util.Arrays.equals`:

~~~java
System.out.println(java.util.Arrays.equals(first, second)); // true
~~~

Với mảng lồng nhau, có thể cần `Arrays.deepEquals` nếu cấu trúc phù hợp.

Tương tự các object khác, `first.equals(second)` của array **không tự so từng phần tử**; `Object.equals` mặc định là so identity.

**Không nhầm** `Arrays.equals` với `==`: API so nội dung sẽ xem từng phần tử theo quy tắc của loại mảng, còn `==` trên tham chiếu chỉ xác định một object duy nhất hay không.

## 8. Wrapper, autoboxing và caching: không dùng `==` để so giá trị

Kiểu wrapper như `Integer` hoặc `Long` là **object**, không phải primitive tương ứng:

~~~java
Integer left = Integer.valueOf(500);
Integer right = Integer.valueOf(500);

System.out.println(left.equals(right)); // true
~~~

Không nên kiểm tra `left == right` với ý định so số 500: `==` trên hai biến có kiểu `Integer` so **tham chiếu** và kết quả có thể chịu ảnh hưởng của caching/triển khai API.

Một số giá trị boxed có caching được bảo đảm hoặc thường gặp nên thỉnh thoảng `==` trả `true`; đó chính là lý do bug này **khó phát hiện** nếu chỉ thử một vài giá trị nhỏ.

Phân biệt hai tình huống:

~~~java
Integer boxed = 500;
int primitive = 500;

System.out.println(boxed.equals(primitive)); // true (boxed tham số)
System.out.println(boxed == primitive);      // true (unboxing rồi so số)
~~~

Ở phép so `boxed == primitive`, một toán hạng kiểu `int` khiến wrapper được **unbox**, rồi Java thực hiện phép so số; không phải reference identity. Nếu `boxed == null`, phép unboxing trong biểu thức này có thể ném `NullPointerException`.

**Quy tắc:** đối với wrapper, nếu muốn so nội dung hãy dùng `equals`, `Objects.equals` hoặc chuyển sang primitive có kiểm soát null; đừng dựa vào identity.


## 9. Kiểm tra identity qua lời gọi method

Tham số tham chiếu được **sao chép theo giá trị**. Method có thể sửa object đang chia sẻ mà không làm thay đổi phép `==` giữa các alias; nếu method gán lại tham số bằng `new Box(...)`, tham chiếu trong biến của caller vẫn giữ nguyên. Phép kiểm tra identity phải dựa vào **tham chiếu ở thời điểm so sánh**, không dựa vào việc method đã nhận một đối số trước đó.

## 10. Bảng chọn phép so sánh

| Dữ liệu / mục tiêu | Cách kiểm tra phù hợp |
|---|---|
| Hai biến primitive `int` | `a == b` |
| Hai tham chiếu có cùng instance không | `a == b` |
| Một tham chiếu có null không | `value == null` hoặc `value != null` |
| Hai `String` có cùng nội dung | `a.equals(b)` nếu `a` chắc chắn không null |
| Hai giá trị String có thể null | `Objects.equals(a, b)` |
| Hai `int[]` có cùng phần tử | `Arrays.equals(a, b)` |
| Hai object của class tự tạo có cùng dữ liệu | Override `equals` đúng hợp đồng hoặc sử dụng kiểu value phù hợp |
| Hai giá trị wrapper `Integer` có cùng số | `Objects.equals(a, b)` hoặc `equals` khi không null |
| Hai `record` có cùng component | `equals` theo hợp đồng record |

Đây là bảng theo **ý định**, không phải cứ thấy biến là sử dụng một phép so sánh chung cho mọi loại. Với số thực `float`/`double`, còn có quy tắc riêng cho NaN, signed zero và sai số xấp xỉ; không nên kéo phép so sánh identity của tham chiếu sang bài toán số thực.

## 11. Ví dụ Java 17 hoàn chỉnh và kết quả chạy

Tạo tệp đúng tên **`ReferenceIdentityDemo.java`**:

~~~java
import java.util.Arrays;
import java.util.Objects;

public final class ReferenceIdentityDemo {
    private ReferenceIdentityDemo() {}

    static final class Box {
        int value;

        Box(int value) {
            this.value = value;
        }
    }

    record Point(int x, int y) {}

    static void rebind(Box parameter) {
        parameter = new Box(999);
        System.out.println("inside parameter=" + parameter.value);
    }

    public static void main(String[] args) {
        int number1 = 10;
        int number2 = 10;
        System.out.println("primitive equal=" + (number1 == number2));

        Box first = new Box(10);
        Box alias = first;
        Box twin = new Box(10);
        System.out.println("alias identity=" + (first == alias));
        System.out.println("twin identity=" + (first == twin));
        System.out.println("field values=" + (first.value == twin.value));

        alias.value = 25;
        System.out.println("first after alias=" + first.value);
        System.out.println("twin unchanged=" + twin.value);

        alias = new Box(30);
        System.out.println("identity after rebind=" + (first == alias));
        System.out.println("first after rebind=" + first.value);

        Box absent = null;
        Box alsoAbsent = null;
        System.out.println("two null references=" + (absent == alsoAbsent));
        System.out.println("null vs object=" + (absent == first));
        System.out.println("null safe equals=" + Objects.equals(absent, alsoAbsent));

        String text1 = new String("Java");
        String text2 = new String("Java");
        String textAlias = text1;
        System.out.println("new Strings identity=" + (text1 == text2));
        System.out.println("new Strings contents=" + text1.equals(text2));
        System.out.println("String alias identity=" + (text1 == textAlias));

        String literal1 = "OK";
        String literal2 = "OK";
        System.out.println("interned literals=" + (literal1 == literal2));

        int[] array1 = {1, 2};
        int[] array2 = {1, 2};
        int[] arrayAlias = array1;
        System.out.println("arrays identity=" + (array1 == array2));
        System.out.println("arrays contents=" + Arrays.equals(array1, array2));
        System.out.println("array alias identity=" + (array1 == arrayAlias));

        Point point1 = new Point(1, 2);
        Point point2 = new Point(1, 2);
        System.out.println("records identity=" + (point1 == point2));
        System.out.println("records contents=" + point1.equals(point2));

        Integer boxed = Integer.valueOf(500);
        int primitive = 500;
        System.out.println("boxed vs primitive=" + (boxed == primitive));

        rebind(first);
        System.out.println("caller unchanged=" + first.value);
    }
}
~~~

Mở terminal trong thư mục chứa file; cần cài **JDK 17+**.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ReferenceIdentityDemo.java
java -cp . ReferenceIdentityDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ReferenceIdentityDemo.java
java -cp . ReferenceIdentityDemo
~~~

**Expected output chính xác:**

~~~text
primitive equal=true
alias identity=true
twin identity=false
field values=true
first after alias=25
twin unchanged=10
identity after rebind=false
first after rebind=25
two null references=true
null vs object=false
null safe equals=true
new Strings identity=false
new Strings contents=true
String alias identity=true
interned literals=true
arrays identity=false
arrays contents=true
array alias identity=true
records identity=false
records contents=true
boxed vs primitive=true
inside parameter=999
caller unchanged=25
~~~

Đầu ra tránh hash code, địa chỉ object hoặc chi tiết triển khai cache không ổn định. Những điểm nên tự đối chiếu:

- **Alias:** `first == alias` ban đầu `true`; sửa `alias.value` làm `first.value` đổi theo.
- **Gán lại:** sau `alias = new Box(30)`, `first == alias` là `false`; `first.value` vẫn `25`.
- **Null:** hai tham chiếu `null` so bằng `==` là `true`, dù không có object `null`.
- **String:** hai lần `new String` độc lập khác identity nhưng `equals` trả true.
- **Mảng:** hai array object khác nhau dù `Arrays.equals` thấy phần tử giống nhau.
- **Record:** `point1 == point2` là false, nhưng `point1.equals(point2)` là true.
- **Wrapper:** `boxed == primitive` thực hiện unboxing để so số, nên true.
- **Method:** tạo Box mới cho tham số trong `rebind` không thay đối tượng mà `first` đang trỏ.

## 12. Lỗi thường gặp

| Đoạn mã / ngộ nhận | Vấn đề | Cách khắc phục |
|---|---|---|
| `new Box(1) == new Box(1)` phải true | Hai lần `new` tạo hai object | Dùng so nội dung theo hợp đồng nếu đó là mục tiêu |
| `Box b = a` tạo bản sao Box | Gán chỉ sao chép giá trị tham chiếu | Cần cơ chế copy/create object riêng |
| Hai object có field `value` giống nhau thì `==` true | `==` không so field | So giá trị hoặc dùng `equals` được thiết kế rõ |
| `"Hi" == new String("Hi")` true vì chữ giống nhau | String literal và String tạo mới khác identity | `equals` |
| Thử hai literal trùng nhau rồi quyết định dùng `==` cho String | String pool làm kết quả gây hiểu nhầm | Dùng `String.equals` |
| `a.equals(b)` với `a == null` | Gọi method trên null ném exception | `Objects.equals(a, b)` |
| `charArray1 == charArray2` để so phần tử | `==` so array object | `Arrays.equals` |
| `Integer a == Integer b` để so số | So identity của boxed objects | Dùng `equals`/`Objects.equals` |
| `Integer boxed = null; boxed == 1` | Unboxing null gây `NullPointerException` | Kiểm tra null trước |
| Override `equals` mà mong toán tử `==` đổi hành vi | `==` không bị override | Gọi `equals` đúng chỗ |
| `rebind(parameter)` làm biến bên gọi trỏ object mới | Java pass-by-value | Trả tham chiếu mới và gán tại phía caller nếu cần |
| Nhìn `Object.toString()` rồi nghĩ đó là địa chỉ | Không có bảo đảm địa chỉ vật lý | Tư duy qua identity, không qua hash/địa chỉ |

**Cẩn thận về kiểu tĩnh:** không phải mọi cặp tham chiếu bất kỳ đều có thể viết `a == b`. Compiler yêu cầu toán hạng có kiểu cho phép kiểm tra reference equality; một số cặp kiểu không liên quan có thể bị từ chối ngay lúc biên dịch. Đừng ép kiểu bừa chỉ để cưỡng ép một so sánh không có ý nghĩa.

## 13. Bài tập tự kiểm tra (có đáp án)

### Bài A — cùng object hay cùng field?

~~~java
Box a = new Box(7);
Box b = a;
Box c = new Box(7);
System.out.println(a == b);
System.out.println(a == c);
System.out.println(a.value == c.value);
~~~

**Đáp án:**

~~~text
true
false
true
~~~

### Bài B — thay đổi alias rồi gán lại

~~~java
Box first = new Box(4);
Box second = first;
second.value = 9;
second = new Box(1);
System.out.println(first.value);
System.out.println(first == second);
~~~

**Đáp án:**

~~~text
9
false
~~~

### Bài C — hai String có nội dung giống nhau

~~~java
String a = new String("ok");
String b = new String("ok");
System.out.println(a == b);
System.out.println(a.equals(b));
~~~

**Đáp án:**

~~~text
false
true
~~~

### Bài D — hai mảng cùng phần tử

~~~java
int[] a = {3, 4};
int[] b = {3, 4};
System.out.println(a == b);
System.out.println(java.util.Arrays.equals(a, b));
~~~

**Đáp án:**

~~~text
false
true
~~~

### Bài E — null

~~~java
Object a = null;
Object b = null;
System.out.println(a == b);
System.out.println(java.util.Objects.equals(a, b));
~~~

**Đáp án:**

~~~text
true
true
~~~

Trong ví dụ này cả hai phương pháp trả true, nhưng **không cùng bản chất**: `==` so hai giá trị tham chiếu null; `Objects.equals` là phép kiểm tra equality an toàn với null.

## 14. Checklist và kết luận

- [ ] Biết `==` trên primitive khác `==` trên tham chiếu.
- [ ] Giải thích được tại sao `new Box(1)` hai lần không tạo cùng identity.
- [ ] Biết `Box alias = first;` chỉ sao chép tham chiếu.
- [ ] Phân biệt sửa object qua alias và gán lại một biến tham chiếu.
- [ ] Hiểu `null == null` là true nhưng `null` không phải object.
- [ ] Dùng `String.equals` thay cho `==` khi so nội dung chuỗi.
- [ ] Biết string pool có thể khiến so literal bằng `==` trả true.
- [ ] Biết `Arrays.equals` cho so nội dung mảng.
- [ ] Hiểu `Object.equals` mặc định so identity khi class không override.
- [ ] Nhớ `record.equals` so component nhưng `record == record` vẫn so identity.
- [ ] Tránh dùng `==` trên hai wrapper để so giá trị, chú ý unboxing.
- [ ] Chạy `ReferenceIdentityDemo.java` bằng `javac`/`java` Java 17+ và đối chiếu toàn bộ expected output.

**Kết luận:** Khi áp dụng cho tham chiếu Java, `==` hỏi **“có cùng một object hay đều null không?”**. Nó **không** tự kiểm tra “dữ liệu bằng nhau”. Muốn so nội dung, hãy chọn API và hợp đồng equality phù hợp: `String.equals`, `Arrays.equals`, `Objects.equals` hoặc `equals` do class cung cấp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="references-and-null-007">null biểu thị không tham chiếu tới đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# `null` biểu thị không tham chiếu tới đối tượng — Java 17+

Trong Java, `null` là **giá trị đặc biệt của kiểu tham chiếu**, có nghĩa **biến không tham chiếu tới bất kỳ đối tượng nào**. `null` không phải một đối tượng “rỗng”, cũng không phải địa chỉ của một đối tượng có dữ liệu mặc định.

~~~java
Ticket missing = null;                  // Khong tro toi Ticket nao.
Ticket present = new Ticket("A1");      // Tro toi mot Ticket duoc tao.
Ticket same = present;                  // Hai bien tro cung Ticket.
~~~

Hệ quả quan trọng:

- Có thể so sánh `missing == null` một cách an toàn.
- Không thể gọi phương thức instance hoặc truy cập field qua tham chiếu `null`: thao tác đó gây `NullPointerException`.
- Gán `present = null` chỉ thay giá trị tham chiếu trong **biến `present`**; nếu `same` vẫn trỏ tới đối tượng, đối tượng vẫn tồn tại và có thể được dùng qua `same`.
- Kiểu nguyên thủy như `int` hoặc `boolean` không được gán trực tiếp `null`.

## 1. Phân biệt `null` với đối tượng và chuỗi rỗng

| Ví dụ | Có đối tượng thực sự được tham chiếu không? | Ý nghĩa |
| --- | --- | --- |
| `Ticket t = null;` | **Không** | Biến `t` không tham chiếu Ticket nào |
| `Ticket t = new Ticket("");` | **Có** | Một Ticket thật, thuộc tính chuỗi có thể rỗng |
| `String s = null;` | **Không** | Không có đối tượng String qua biến `s` |
| `String s = "";` | **Có** | Đối tượng String có `length() == 0` |
| `String s = " ";` | **Có** | Chuỗi chứa một dấu cách, độ dài 1 |
| `Ticket[] a = new Ticket[2];` | **Có, một đối tượng mảng** | Hai **phần tử tham chiếu** ban đầu bằng `null` |
| `int n = 0;` | Không liên quan `null` | Giá trị nguyên thủy số 0 |

`null` **không đồng nghĩa** với số `0`, `false`, chuỗi `""` hoặc một đối tượng có các field bằng giá trị mặc định. Nên quyết định rõ khi thiết kế API: liệu `null` mang nghĩa “chưa cung cấp”, “không tìm thấy” hay “không áp dụng”, thay vì dùng tùy tiện.

## 2. Chương trình Java 17+ chạy được

Lưu tệp UTF-8 tên **`NullReferenceDemo.java`**:

~~~java
public class NullReferenceDemo {
    static class Ticket {
        final String id;

        Ticket(String id) {
            this.id = id;
        }

        String describe() {
            return "Ticket(" + id + ")";
        }
    }

    static Ticket shared; // Static reference field mac dinh null.

    static class Holder {
        Ticket ticket;     // Instance reference field mac dinh null.
    }

    static void reassignParameter(Ticket local) {
        local = null;      // Chi gan lai bien tham so.
        System.out.println("insideParamNull=" + (local == null));
    }

    public static void main(String[] args) {
        Ticket absent = null;
        System.out.println("absentNull=" + (absent == null));
        System.out.println("sharedNull=" + (shared == null));

        Holder holder = new Holder();
        System.out.println("fieldNull=" + (holder.ticket == null));

        Ticket[] tickets = new Ticket[2];
        System.out.println("slotNull=" + (tickets[0] == null));

        Ticket original = new Ticket("A1");
        Ticket alias = original;
        tickets[0] = original;
        System.out.println("aliasSame=" + (alias == original));

        original = null;
        System.out.println("originalNull=" + (original == null));
        System.out.println("aliasExists=" + alias.describe());

        reassignParameter(alias);
        System.out.println("aliasAfterMethod=" + alias.describe());

        Ticket empty = new Ticket("");
        System.out.println("emptyObjectNull=" + (empty == null));

        String emptyText = "";
        String absentText = null;
        System.out.println("emptyStringNull=" + (emptyText == null));
        System.out.println("emptyStringLength=" + emptyText.length());
        System.out.println("safeEquals=" + "Java".equals(absentText));
        System.out.println(
                "guard=" + (absentText == null || absentText.isBlank()));

        try {
            System.out.println(absent.describe());
        } catch (NullPointerException ex) {
            System.out.println("dereference=NullPointerException");
        }

        Integer wrapper = null;
        try {
            int value = wrapper;
            System.out.println("unboxed=" + value);
        } catch (NullPointerException ex) {
            System.out.println("unboxing=NullPointerException");
        }

        System.out.println("arrayAlias=" + (tickets[0] == alias));
    }
}
~~~

Trong PowerShell (Windows) hoặc Bash (Linux/macOS), chuyển tới thư mục chứa tệp và chạy bằng **JDK 17+**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 NullReferenceDemo.java
java NullReferenceDemo
~~~

**Expected output** của lệnh `java NullReferenceDemo`:

~~~text
absentNull=true
sharedNull=true
fieldNull=true
slotNull=true
aliasSame=true
originalNull=true
aliasExists=Ticket(A1)
insideParamNull=true
aliasAfterMethod=Ticket(A1)
emptyObjectNull=false
emptyStringNull=false
emptyStringLength=0
safeEquals=false
guard=true
dereference=NullPointerException
unboxing=NullPointerException
arrayAlias=true
~~~

Các dòng `java -version` và `javac -version` phụ thuộc JDK đã cài, nên không thuộc khối expected output cố định.

### Vì sao kết quả như vậy?

1. `absent = null` không trỏ tới Ticket; kiểm tra bằng `== null` hợp lệ và trả `true`.
2. Field `shared`, `holder.ticket` và phần tử `tickets[0]` mặc định bằng `null` khi chưa được gán đối tượng. **Mảng `tickets` vẫn là một đối tượng đã được tạo**.
3. `alias = original` và `tickets[0] = original` sao chép **giá trị tham chiếu**. Cả ba lối truy cập ban đầu cùng dẫn tới Ticket("A1").
4. `original = null` không xóa Ticket("A1"): `alias` và `tickets[0]` vẫn trỏ tới đối tượng. Vì vậy `alias.describe()` còn chạy được.
5. Phương thức `reassignParameter` nhận bản sao tham chiếu; `local = null` không làm biến `alias` ở bên gọi biến thành null. Java **truyền tham số theo giá trị**.
6. `new Ticket("")` tạo một Ticket thực sự dù `id` là chuỗi rỗng. Chuỗi `""` cũng là một String thật có độ dài 0, khác `String absentText = null`.
7. `"Java".equals(absentText)` an toàn vì đối tượng nhận lời gọi là literal không null; kết quả là `false`. Biểu thức `absentText == null || absentText.isBlank()` dùng *short-circuit*, nên không gọi `isBlank()` trên null.
8. `absent.describe()` gây `NullPointerException`. `Integer wrapper = null` hợp lệ nhưng tự động **unboxing** `wrapper` sang `int` cũng gây `NullPointerException`.

## 3. Vì sao `null` và biến cục bộ chưa khởi tạo khác nhau?

~~~java
Ticket later;
// System.out.println(later); // Sai: bien cuc bo chua duoc gan.

later = null;
System.out.println(later == null); // Hop le: true.
~~~

- **Biến cục bộ chưa được gán chắc chắn:** compiler không cho phép đọc giá trị, theo quy tắc *definite assignment*.
- **Biến đã được gán `null`:** đọc biến và so sánh với `null` đều hợp lệ, nhưng không thể truy cập thành viên instance qua tham chiếu đó.
- **Field kiểu tham chiếu:** nếu không có initializer hoặc phép gán khác, nó nhận giá trị mặc định `null`. Điều này **không** có nghĩa Java tự tạo đối tượng cho field.

Ví dụ một class có field `Ticket ticket;` không tự tạo `Ticket` khi đối tượng chứa field được tạo. Nếu muốn có Ticket thật, phải thực hiện thao tác khởi tạo phù hợp, chẳng hạn `ticket = new Ticket("A1");`.


## 4. Kiểm tra `null` theo mục đích

- `x == null` và `x != null` kiểm tra tham chiếu một cách an toàn. Muốn dùng member, phải có giá trị khác `null`.
- `x != null && x.describe().isEmpty()` tận dụng short-circuit: vế phải chỉ chạy khi `x` có đối tượng.
- `"OK".equals(s)` và `Objects.equals(a, b)` hỗ trợ so sánh an toàn khi dữ liệu có thể vắng mặt; lưu ý hai đối số đều `null` thì `Objects.equals` trả `true`.
- `Objects.requireNonNull(x, "x required")` chủ động báo vi phạm khi hợp đồng yêu cầu có object. Đây là lựa chọn thiết kế, không phải cách chữa mọi lỗi.
- Với wrapper như `Integer`, kiểm tra trước khi tự động unbox để tránh `NullPointerException`.

## 5. `null` không phải “lệnh xóa” đối tượng

~~~java
Ticket a = new Ticket("A1");
Ticket b = a;

a = null; // Chi doi gia tri trong bien a.
System.out.println(b.describe()); // Van goi duoc.
~~~

Sau `a = null`, biến `b` vẫn tham chiếu đối tượng. **Việc đối tượng còn được truy cập hay không** phụ thuộc mọi tham chiếu còn tồn tại, không chỉ một biến. Java dùng garbage collector; không có bảo đảm “gán null là đối tượng bị thu gom ngay”.

Điều này cũng khác với **đóng tài nguyên**: một đối tượng giữ file, kết nối hoặc socket có thể cần `close()`/`try-with-resources` theo hợp đồng API. Chỉ gán tham chiếu thành null không thay cho việc giải phóng các tài nguyên ngoài bộ nhớ.

### `final` không loại bỏ khả năng có null

~~~java
final Ticket chosen = null;

// chosen = new Ticket("A1"); // Sai: chosen da duoc gan va la final.
~~~

`final` chỉ giới hạn **việc gán lại biến**, không tự yêu cầu biến phải chứa đối tượng. Một tham chiếu `final` có thể mang `null` nếu được khởi tạo hợp lệ như trên.

## 6. Những nhận định dễ nhầm

| Nhận định | Đúng hay sai? | Lý do |
| --- | --- | --- |
| “`null` là một đối tượng Ticket có dữ liệu trống.” | **Sai** | Không tham chiếu đối tượng nào |
| “`Ticket t = null;` tạo Ticket.” | **Sai** | Chỉ gán giá trị tham chiếu null |
| “`Ticket t;` trong phương thức mặc định bằng null.” | **Sai** | Local phải được gán chắc chắn trước khi đọc |
| “Field `Ticket t;` chưa gán mặc định là null.” | **Đúng** | Giá trị mặc định của reference field |
| “`new Ticket[2]` tạo hai Ticket.” | **Sai** | Tạo một mảng gồm hai phần tử null |
| “Gán `t = null` chắc chắn xóa đối tượng ngay.” | **Sai** | Các tham chiếu khác có thể còn tồn tại |
| “`t == null` gây NPE khi t là null.” | **Sai** | So sánh null là an toàn |
| “`t.describe()` khi t là null gây NPE.” | **Đúng** | Không có đối tượng để truy cập |
| “`Integer n = null; int x = n;` luôn an toàn.” | **Sai** | Unboxing null gây NPE |
| “`final Ticket t = null;` hợp lệ.” | **Đúng** | `final` không cấm giá trị null |

## 7. Bài tập tự kiểm tra

**Bài 1.** Sau `Ticket a = null;`, `a == null` là gì? `a.describe()` có chạy được không?

*Đáp án:* `true`; gọi `describe()` gây `NullPointerException`.

**Bài 2.** `Ticket a = new Ticket("A"); Ticket b = a; a = null;`. `b` còn dùng được không?

*Đáp án:* Có. `b` vẫn tham chiếu Ticket("A").

**Bài 3.** `String a = null; String b = "";` có tương đương không?

*Đáp án:* Không. `a` không trỏ đối tượng, `b` là đối tượng String rỗng có `length() == 0`.

**Bài 4.** Vì sao `Ticket[] t = new Ticket[3];` có `t[0] == null` dù mảng `t` đã được tạo?

*Đáp án:* Bản thân mảng là đối tượng; các **phần tử kiểu tham chiếu** chưa gán nhận null, không tự tạo Ticket.

**Bài 5.** Tại sao `Integer n = null;` hợp lệ nhưng `int x = n;` có thể ném lỗi?

*Đáp án:* `Integer` là reference type và có thể null; tự động unboxing cần giá trị đối tượng thật nên ném `NullPointerException` khi null.

## Checklist

- [ ] Hiểu `null` nghĩa là **không tham chiếu tới đối tượng**, không phải “đối tượng rỗng”.
- [ ] Phân biệt null với `""`, `0` và đối tượng được khởi tạo.
- [ ] Biết field/array reference defaults khác local variable chưa definite assignment.
- [ ] Kiểm tra `== null` hoặc `!= null` an toàn trước khi gọi thành viên instance.
- [ ] Hiểu gán null chỉ đổi tham chiếu của **biến**, không tự xóa object.
- [ ] Nhận biết lỗi NPE khi dereference hoặc unboxing wrapper null.
- [ ] Dùng short-circuit, null-safe equality, yêu cầu non-null theo hợp đồng API.

**Kết luận:** `null` là **giá trị của tham chiếu không trỏ tới đối tượng nào**. Khi viết Java, hãy tách biệt **sự tồn tại của biến**, **giá trị tham chiếu trong biến** và **sự tồn tại của đối tượng**. Kiểm tra null đúng nơi giúp tránh `NullPointerException` và tránh nhầm null với dữ liệu rỗng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="references-and-null-008">Nhận diện NullPointerException và đặt kiểm tra tại điểm phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện `NullPointerException` và đặt kiểm tra tại điểm phù hợp

> **Java core — language-basics / Tham chiếu và null**  
> **Môi trường:** JDK 17+; một tệp Java độc lập, không có dependency ngoài.  
> **Mục tiêu:** Nhận biết các thao tác có thể phát sinh `NullPointerException` (NPE), lần theo vị trí lỗi, chọn giữa **kiểm tra `null` / giá trị mặc định / từ chối đầu vào**, và đặt kiểm tra **trước thao tác cần đối tượng** thay vì bắt lỗi tràn lan.

## 1. NPE là gì và xảy ra ở đâu?

`null` là giá trị đặc biệt của **kiểu tham chiếu**, nghĩa là biến hiện không tham chiếu tới đối tượng nào. `NullPointerException` thường xảy ra khi chương trình thực hiện thao tác **đòi hỏi có đối tượng** nhưng tham chiếu liên quan đang bằng `null`.

Ví dụ:

~~~java
String text = null;
// int n = text.length(); // Nếu chạy: NullPointerException.
~~~

Các tình huống phổ biến:

| Thao tác | Ví dụ nguy hiểm | Tham chiếu có thể `null` |
| --- | --- | --- |
| Gọi instance method | `name.trim()` | `name` |
| Đọc/ghi instance field | `user.status` | `user` |
| Đọc `length` hay phần tử mảng | `items.length`, `items[0]` | **Mảng `items`**, nếu chưa có object mảng |
| Sử dụng **phần tử** mảng tham chiếu | `items[0].length()` | `items[0]`, ngay cả khi mảng có tồn tại |
| Tự unbox wrapper | `int n = boxed;` | `boxed` kiểu `Integer` |
| Duyệt bằng enhanced `for` trên mảng | `for (String s : items) { ... }` | `items` nếu là mảng `null` |

**Phân biệt lỗi:** `items[10]` trên mảng tồn tại nhưng thiếu phần tử gây `ArrayIndexOutOfBoundsException`, **không** phải NPE. `String` rỗng `""` là object hợp lệ, khác với tham chiếu `null`.

## 2. Xác định chính xác điểm lỗi trước khi thêm kiểm tra

Khi gặp NPE, đọc **stack trace**:

1. Tìm dòng `java.lang.NullPointerException` và frame đầu tiên thuộc mã của bạn (ví dụ `Service.java:42`).
2. Mở đúng dòng được chỉ tới; liệt kê từng tham chiếu có thể `null` trên dòng đó, nhất là chuỗi gọi như `order.getCustomer().getName()`.
3. Lần ngược nguồn tạo/nhận giá trị: tham số method, kết quả API, trường chưa khởi tạo, phần tử mảng, giá trị trả về từ repository, v.v.
4. Kiểm tra **hợp đồng dữ liệu**: `null` có phải một trạng thái hợp lệ hay là đầu vào không hợp lệ?

Java 17 có thể in **thông điệp NPE chi tiết** tùy cấu hình JVM, nhưng đừng hardcode toàn bộ stack trace hoặc thông điệp tự động vào testcase: tên biến và lời giải thích có thể phụ thuộc môi trường/biên dịch. Dùng **line number + nguyên nhân + hợp đồng** để quyết định cách sửa.

Ví dụ một lời gọi nhiều bước:

~~~java
// Nếu customer có thể null, kiểm tra tại đây trước khi gọi method:
// Customer customer = order.getCustomer();
// if (customer == null) { /* xử lý nhánh thiếu customer */ }
// String name = customer.getName();
~~~

Đừng tự động thêm `try { ... } catch (NullPointerException e) { }` để bỏ qua lỗi: cách đó có thể che lỗi lập trình và làm dữ liệu sau đó sai khó phát hiện.

## 3. Chọn cách xử lý theo ý nghĩa `null`

Có **ba chiến lược chính**, không thay thế lẫn nhau:

| Tình huống nghiệp vụ | Vị trí kiểm tra phù hợp | Cách giải quyết |
| --- | --- | --- |
| `null` **hợp lệ**, cần mặc định | Trước khi đọc dữ liệu tùy chọn | `if (x == null) return fallback;` |
| `null` **không hợp lệ** | Ngay ranh giới nhận dữ liệu, constructor hoặc public API | `Objects.requireNonNull(x, "x required")` |
| `null` hợp lệ nhưng chỉ được thao tác khi có object | Ngay trước thao tác dereference | `x != null && x.isReady()` |

Ví dụ gác điều kiện an toàn:

~~~java
String input = null;
boolean hasText = input != null && !input.isBlank();
System.out.println(hasText); // false, không gọi input.isBlank().
~~~

`&&` thực hiện **short-circuit**: khi vế trái false, Java không đánh giá vế phải. **Thứ tự là quan trọng**; đảo thành `!input.isBlank() && input != null` sẽ gây NPE nếu `input` là null. Toán tử boolean `&` **không short-circuit**, nên không phải cách thay thế tương đương khi cần bảo vệ việc gọi method.

Đối với so sánh với literal:

~~~java
String command = null;
System.out.println("START".equals(command)); // false, an toàn với null.
~~~

Có thể dùng `Objects.equals(a, b)` khi **cả hai** biến có thể `null`. Cần biết `Objects.equals(null, null)` cho **true**; nếu dữ liệu cùng vắng mặt không được xem là bằng nhau, phải đặt quy tắc riêng.

## 4. Java 17+ runnable: các tình huống NPE và cách xử lý

Tạo file **NullCheckDemo.java**:

~~~java
import java.util.Objects;

public final class NullCheckDemo {
    private static final class Account {
        private final String label;

        Account(String label) {
            this.label = Objects.requireNonNull(label, "label required");
        }

        String label() {
            return label;
        }
    }

    private static String displayLabel(Account account) {
        if (account == null) {
            return "Guest"; // Null là một trạng thái hợp lệ của API này.
        }
        return account.label();
    }

    private static int requiredLength(Account account) {
        Objects.requireNonNull(account, "account required");
        return account.label().length();
    }

    public static void main(String[] args) {
        Account valid = new Account("Linh");
        Account missing = null;

        System.out.println("valid=" + displayLabel(valid));
        System.out.println("fallback=" + displayLabel(missing));
        System.out.println("guard="
                + (missing != null && missing.label().length() > 0));
        System.out.println("literalEquals=" + "Linh".equals(null));
        System.out.println("bothNullEquals=" + Objects.equals(null, null));
        System.out.println("requiredLength=" + requiredLength(valid));

        try {
            requiredLength(missing);
        } catch (NullPointerException e) {
            System.out.println("requiredError=" + e.getMessage());
        }

        String receiver = null;
        try {
            receiver.length();
        } catch (NullPointerException e) {
            System.out.println("receiverNpe=true");
        }

        String[] words = {null, "ok"};
        try {
            System.out.println(words[0].length());
        } catch (NullPointerException e) {
            System.out.println("elementNpe=true");
        }

        Integer boxed = null;
        try {
            int number = boxed;
            System.out.println("number=" + number);
        } catch (NullPointerException e) {
            System.out.println("unboxingNpe=true");
        }

        try {
            new Account(null);
        } catch (NullPointerException e) {
            System.out.println("constructorError=" + e.getMessage());
        }

        System.out.println("safeLength="
                + (words[1] != null ? words[1].length() : 0));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file bằng Bash, PowerShell hoặc Command Prompt:

~~~bash
javac -encoding UTF-8 NullCheckDemo.java
java NullCheckDemo
~~~

**Expected output chính xác:**

~~~text
valid=Linh
fallback=Guest
guard=false
literalEquals=false
bothNullEquals=true
requiredLength=4
requiredError=account required
receiverNpe=true
elementNpe=true
unboxingNpe=true
constructorError=label required
safeLength=2
~~~

**Giải thích:**

- `displayLabel(null)` trả `"Guest"` vì API **cho phép** tài khoản vắng mặt; `requiredLength(null)` cố ý từ chối bằng thông báo `"account required"`.
- `missing != null && ...` dừng trước thao tác đọc `label()`. Literal `"Linh".equals(null)` trả false. `Objects.equals(null, null)` trả true theo hợp đồng của nó.
- `receiver.length()` gây NPE vì receiver null. Mảng `words` **không null**, nhưng phần tử `words[0]` null nên gọi `length()` vẫn gây NPE.
- Biến wrapper `Integer boxed` có thể null; tự chuyển sang `int` (*auto-unboxing*) gây NPE.
- Constructor `Account` kiểm tra giá trị `label` **ngay tại nơi thiết lập bất biến của object**, khiến một `Account` được tạo thành công luôn có `label` không null.
- Các khối `catch` dùng để minh họa một số lỗi một cách có kiểm soát; trong ứng dụng thật, hãy **sửa hợp đồng dữ liệu/kiểm tra đúng chỗ**, đừng nuốt NPE rồi tiếp tục như chưa có vấn đề.

## 5. Kiểm tra `null` ở đâu cho đúng?

**Nguyên tắc:** Đặt kiểm tra tại nơi **có đủ ngữ cảnh để quyết định cách xử lý**, và trước khi truy cập object. Không phải biến nào cũng cần lặp lại `if (x == null)` ở mọi dòng.

- **Ranh giới public API hoặc constructor:** Nếu giá trị bắt buộc, dùng `Objects.requireNonNull` để phát hiện sai đầu vào **sớm**, ghi rõ tên/thông điệp. Sau đó code nội bộ có thể dựa trên bất biến đã thiết lập.
- **Ranh giới nhập liệu hoặc nguồn có thể thiếu:** Nếu `null` là trạng thái hợp lệ, quyết định trả default, bỏ qua, yêu cầu người dùng bổ sung hoặc báo lỗi nghiệp vụ trước thao tác.
- **Trước chuỗi truy cập nhiều cấp:** Chia thành biến trung gian rồi kiểm tra đúng phần nào có thể null, thay vì gọi nối tiếp và chỉ bắt exception.
- **Từng phần tử collection/mảng:** Kiểm tra cả **container** và **element** nếu nguồn dữ liệu có thể chứa null.
- **Wrapper số:** Trước phép tính cần unbox `Integer`/`Long`/`Boolean`, xác nhận chúng không null hoặc có quy tắc default rõ ràng.

`Objects.requireNonNull(x, "...")` vẫn tạo ra **NPE**, nhưng đó là kiểm tra **chủ động ở ranh giới hợp đồng**, khác với lỗi vô tình dereference null sâu bên trong. Nếu public API cần loại exception khác (ví dụ `IllegalArgumentException` cho giá trị nhập không hợp lệ), hãy thiết kế có chủ đích và thống nhất thay vì đổi tùy tiện.

## 6. Những sai lầm thường gặp

| Sai lầm | Hậu quả / cách sửa |
| --- | --- |
| `name.equals("OK")` khi `name` có thể null | Có thể NPE; dùng `"OK".equals(name)` hoặc kiểm tra trước |
| `input.isBlank() && input != null` | Đảo thứ tự: gọi method trước khi guard |
| `input != null & input.isBlank()` | `&` đánh giá cả hai vế; dùng `&&` |
| Chỉ kiểm tra `array != null` rồi gọi `array[0].length()` | Phần tử `array[0]` vẫn có thể null |
| Tự unbox `Integer count = null` | NPE; định nghĩa fallback hoặc yêu cầu count khác null |
| Bắt và bỏ qua mọi `NullPointerException` | Che lỗi hợp đồng, kết quả về sau có thể sai |
| Mọi giá trị thiếu đều thay bằng `""` hoặc `0` | Có thể sai nghiệp vụ; chỉ dùng default nếu được định nghĩa |
| Gán `x = null` rồi tưởng object đã bị xóa | Chỉ biến x đổi tham chiếu; alias khác có thể còn trỏ object |

**Lưu ý:** `Optional` có thể thể hiện kết quả tùy chọn khi API phù hợp, nhưng không phải công cụ để bọc mọi field và không thay thế việc hiểu liệu `null` là dữ liệu hợp lệ hay lỗi. Tránh gọi `Optional.get()` mà không kiểm tra vì lại sinh lỗi khác.

## 7. Bài tập ngắn

**Bài 1.** Cho `String value = null;`. Dự đoán hành vi của `value.length()`, `"OK".equals(value)` và `value != null && value.length() > 0`. Chỉ ra lệnh nào gây NPE, lệnh nào an toàn.

**Bài 2.** Cho `String[] list = new String[2];`. Tại sao `list.length` hợp lệ nhưng `list[0].length()` gây NPE? Đặt guard ở đâu?

**Bài 3.** Viết hàm nhận `String requiredCode` bắt buộc khác null. Đặt `Objects.requireNonNull(requiredCode, "code required")` ngay đầu hàm, rồi dùng biến an toàn ở phần còn lại.

**Bài 4.** Một API trả `null` khi không tìm thấy tài khoản. Hãy chọn giữa (a) trả nhãn `"Guest"`, (b) thông báo “không tìm thấy”, (c) phát hiện lỗi đầu vào — và giải thích sự khác nhau về hợp đồng.

**Bài 5.** `Integer quantity = null; int total = quantity * 2;` gây lỗi vì sao? Viết hai cách xử lý có chủ đích: bắt buộc quantity hoặc dùng giá trị mặc định đã được nghiệp vụ đồng ý.

### Ghi nhớ

**NPE thường báo rằng code đã cố sử dụng một tham chiếu `null` như object.** Hãy đọc đúng stack trace để tìm thao tác lỗi; quyết định `null` có hợp lệ không, rồi **đặt kiểm tra tại ranh giới dữ liệu hoặc ngay trước dereference**. Dùng `&&` để guard, `Objects.requireNonNull` cho giá trị bắt buộc và so sánh an toàn khi cần. Tránh che giấu NPE bằng `catch` rỗng hay rải default không có ý nghĩa.


</details>

- [Quay lại đầu trang](#back-to-top)
