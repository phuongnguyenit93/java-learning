<a id="back-to-top"></a>

# Lớp và đối tượng

## Menu
- [Lớp mô tả kiểu và đối tượng là thể hiện cụ thể](#classes-and-objects-001)
- [Trường biểu diễn dữ liệu và phương thức biểu diễn hành vi](#classes-and-objects-002)
- [Tạo đối tượng và gọi phương thức bằng dấu chấm](#classes-and-objects-003)
- [Hàm khởi tạo thiết lập giá trị ban đầu](#classes-and-objects-004)
- [this biểu thị đối tượng hiện tại](#classes-and-objects-005)
- [Phân biệt thành viên static với thành viên đối tượng](#classes-and-objects-006)
- [Nhận diện public, private và truy cập trong gói](#classes-and-objects-007)
- [Giới thiệu enum, record và interface ở mức đọc hiểu cú pháp](#classes-and-objects-008)
- [Từ chương trình chỉ có main tới chương trình có nhiều lớp](#classes-and-objects-009)
- [Đọc cú pháp generic như List&lt;String&gt; và ý nghĩa kiểm tra kiểu](#classes-and-objects-010)
- [Nhận diện lambda và tham chiếu phương thức trong lời gọi API](#classes-and-objects-011)
- [Nhận diện annotation @Override và @Deprecated](#classes-and-objects-012)

## <a id="classes-and-objects-001">Lớp mô tả kiểu và đối tượng là thể hiện cụ thể</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp mô tả kiểu và đối tượng là thể hiện cụ thể — Java 17+

## 1. Lớp khác đối tượng như thế nào?

Trong Java, **lớp (class)** định nghĩa một **kiểu dữ liệu**: tên kiểu, các thuộc tính (*fields*), hành vi (*methods*) và cách khởi tạo (*constructors*). **Đối tượng (object)** là một **thể hiện cụ thể (instance)** của kiểu đó, được tạo khi chương trình chạy.

~~~java
class Student {                          // Định nghĩa một lớp.
    String name;                         // Trường instance.
    int score;

    Student(String name, int score) {    // Constructor.
        this.name = name;
        this.score = score;
    }

    void printInfo() {                   // Phương thức instance.
        System.out.println(name + ":" + score);
    }
}
~~~

~~~java
Student an = new Student("An", 70);       // Một đối tượng.
Student binh = new Student("Binh", 85);   // Đối tượng thứ hai.
an.printInfo();
binh.printInfo();
~~~

**Exact expected stdout** (khi đặt hai câu gọi trong `main`):

~~~text
An:70
Binh:85
~~~

`Student` là **kiểu chung** mô tả các đặc điểm và thao tác; `an` và `binh` là **hai biến tham chiếu đến hai đối tượng Student khác nhau**. Chúng dùng cùng định nghĩa lớp nhưng có dữ liệu `name`, `score` riêng.

## 2. Phân biệt class, object, reference và constructor

| Khái niệm | Ví dụ | Vai trò |
|---|---|---|
| Class | `class Student { ... }` | Khai báo kiểu và các thành viên |
| Object/instance | Đối tượng do `new Student(...)` tạo | Thể hiện có trạng thái riêng |
| Reference variable | `Student an` | Biến có kiểu `Student`, giữ giá trị tham chiếu tới object (hoặc `null`) |
| Constructor | `Student(String name, int score)` | Khởi tạo object khi được gọi qua `new` |
| Instance field | `name`, `score` | Dữ liệu thuộc từng instance |
| Instance method | `printInfo()` | Hành vi gọi trên một instance |
| Static member | `static int created` | Thành viên gắn với lớp, không tồn tại một bản riêng ở mỗi object |

Câu lệnh:

~~~java
Student student = new Student("Lan", 90);
~~~

có hai phía:

1. `Student student` khai báo **biến tham chiếu** tên `student`.
2. `new Student("Lan", 90)` tạo **đối tượng**, gọi constructor và cung cấp giá trị tham chiếu để lưu vào `student`.

Chỉ viết `Student student;` **không tạo object**, và vì là biến cục bộ chưa được gán, không được đọc nó trước khi gán. Nếu chủ động viết `Student student = null;`, biến đã được gán nhưng **chưa trỏ đến đối tượng nào**.

## 3. Mỗi đối tượng có trạng thái instance độc lập

~~~java
Student an = new Student("An", 70);
Student binh = new Student("Binh", 85);

an.score = 75; // Minh họa khi field có thể truy cập.
System.out.println(an.score);
System.out.println(binh.score);
~~~

**Exact expected stdout:**

~~~text
75
85
~~~

Thay đổi trường `score` của `an` không thay trường `score` của `binh`, vì hai object được tạo bằng **hai lần `new`**.

Một mô hình tham chiếu:

~~~text
Student an   ──────> Object #1: {name="An",   score=75}
Student binh ──────> Object #2: {name="Binh", score=85}

              Cùng kiểu Student, khác đối tượng và trạng thái.
~~~

Tên `Object #1` và `Object #2` chỉ là chú giải; không phải số nhận dạng Java tự in ra. Java không bảo đảm các object nằm ở vị trí bộ nhớ cụ thể mà chương trình được phép dựa vào.

Trong code thực tế, thường khai báo field `private` và cung cấp phương thức truy cập/cập nhật có kiểm tra, thay vì cho phép gán tùy ý vào `student.score`.

## 4. Hai biến có thể trỏ cùng một đối tượng

~~~java
Student an = new Student("An", 70);
Student alias = an;                 // Không gọi new, không tạo Student mới.
alias.score = 80;
System.out.println(an.score);
System.out.println(an == alias);
~~~

**Exact expected stdout:**

~~~text
80
true
~~~

`alias = an` **sao chép giá trị tham chiếu**. Vì `an` và `alias` đang chỉ đến cùng object, sửa trạng thái qua biến nào cũng thấy từ biến kia.

Nếu sau đó:

~~~java
alias = new Student("Chi", 90);
System.out.println(an == alias);
~~~

**Exact expected stdout (tiếp nối):**

~~~text
false
~~~

`alias` chuyển sang object mới; `an` vẫn giữ tham chiếu tới object cũ. **Gán lại biến tham chiếu** không tự thay một biến tham chiếu khác.

Với hai object được tạo riêng bằng `new`, toán tử `==` so sánh xem các tham chiếu có chỉ đến **cùng object** hay không. Nó không tự so sánh mọi field bên trong. Nếu cần kiểm tra giá trị nghiệp vụ tương đương, có thể thiết kế `equals`/`hashCode` phù hợp ở bài học nâng cao.


## 5. Constructor giúp đối tượng có trạng thái ban đầu

Khi gọi `new Student("Lan", 90)`, Java chọn constructor tương ứng để khởi tạo **chính instance mới**. Constructor mang tên lớp, không khai báo kiểu trả về và thường kiểm tra đầu vào trước khi hoàn tất. Ví dụ:

~~~java
class Student {
    private final String name;
    private int score;

    Student(String name, int score) {
        if (name == null || name.isBlank() || score < 0 || score > 100) {
            throw new IllegalArgumentException("invalid student");
        }
        this.name = name.strip();
        this.score = score;
    }
}
~~~

`this.name` là field của object đang tạo, còn `name` là tham số. `final` ngăn gán lại field `name` sau khởi tạo, không khiến toàn bộ object bất biến. Nếu constructor ném ngoại lệ, biểu thức `new` không trả tham chiếu cho caller. Các quy tắc về constructor mặc định, overload, `this(...)` và `super(...)` được thực hành ở bài **Hàm khởi tạo thiết lập giá trị ban đầu**.

## 6. Đối tượng có hành vi riêng, lớp có thành viên chung

~~~java
// Với instance method:
an.increase(5); // Thay trạng thái của đối tượng an.

// Với static method do lớp định nghĩa:
Student.getCreatedCount(); // Đọc bộ đếm gắn với lớp Student.
~~~

Phương thức **instance** chạy với một đối tượng nhận lời gọi; trong thân nó có thể dùng `this` để chỉ object ấy. Thành viên **static** gắn với lớp, không có một bản riêng trên từng object và static method không có `this`. Ví dụ hoàn chỉnh bên dưới minh họa cả hai, trong khi bài **Phân biệt thành viên static với thành viên đối tượng** giải thích phạm vi và rủi ro của trạng thái chung.

## 7. Chương trình Java 17+ hoàn chỉnh

Lưu đúng file **`ClassObjectBasicsDemo.java`**:

~~~java
public final class ClassObjectBasicsDemo {
    private ClassObjectBasicsDemo() {}

    // Lớp mô tả kiểu đối tượng Student.
    static final class Student {
        private static int createdCount;

        private final String name;
        private int score;

        Student(String name, int score) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name is blank");
            }
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("score out of range");
            }
            this.name = name.strip();
            this.score = score;
            createdCount++;
        }

        String getName() {
            return name;
        }

        int getScore() {
            return score;
        }

        void increase(int points) {
            if (points < 0 || points > 100 - score) {
                throw new IllegalArgumentException("invalid increase");
            }
            score += points;
        }

        String describe() {
            return name + ":" + score;
        }

        static int getCreatedCount() {
            return createdCount;
        }
    }

    public static void main(String[] args) {
        System.out.println("created initially=" + Student.getCreatedCount());

        Student an = new Student(" An ", 70);
        Student binh = new Student("Binh", 85);
        System.out.println("created after two=" + Student.getCreatedCount());

        System.out.println("an=" + an.describe());
        System.out.println("binh=" + binh.describe());
        System.out.println("an name=" + an.getName());

        an.increase(5);
        System.out.println("an after increase=" + an.getScore());
        System.out.println("binh unchanged=" + binh.getScore());
        System.out.println("two objects same=" + (an == binh));

        Student alias = an;
        alias.increase(5);
        System.out.println("alias same=" + (an == alias));
        System.out.println("an via alias=" + an.describe());
        System.out.println("created after alias=" + Student.getCreatedCount());

        alias = new Student("Chi", 90);
        System.out.println("alias replaced same=" + (an == alias));
        System.out.println("an still=" + an.describe());
        System.out.println("alias now=" + alias.describe());
        System.out.println("created after third=" + Student.getCreatedCount());

        Student missing = null;
        System.out.println("missing null=" + (missing == null));

        try {
            new Student("", 10);
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid name=" + ex.getMessage());
        }
        try {
            an.increase(30);
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid increase=" + ex.getMessage());
        }
        System.out.println("created at end=" + Student.getCreatedCount());
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; mở terminal tại thư mục chứa file. Không cần Maven, Gradle, thư viện ngoài hay preview features.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ClassObjectBasicsDemo.java
java -cp . ClassObjectBasicsDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ClassObjectBasicsDemo.java
java -cp . ClassObjectBasicsDemo
~~~

### Exact expected stdout

~~~text
created initially=0
created after two=2
an=An:70
binh=Binh:85
an name=An
an after increase=75
binh unchanged=85
two objects same=false
alias same=true
an via alias=An:80
created after alias=2
alias replaced same=false
an still=An:80
alias now=Chi:90
created after third=3
missing null=true
invalid name=name is blank
invalid increase=invalid increase
created at end=3
~~~

**Lưu ý tràn số:** Vì `score` luôn nằm trong `0..100`, so sánh `points > 100 - score` sẽ từ chối cả đối số rất lớn như `Integer.MAX_VALUE`; kiểm tra `score + points > 100` có thể sai do phép cộng `int` tràn trước khi so sánh.

**Đối chiếu:** hai lần `new` đầu tạo hai object nên `createdCount == 2`. `alias = an` không tạo object; gọi `alias.increase(5)` thay đúng object `an` đang chỉ đến. Sau `alias = new Student("Chi", 90)`, có object thứ ba, còn `an` giữ nguyên trạng thái 80. Constructor từ chối tên rỗng, nên lần tạo thất bại không tăng bộ đếm trong mã này.

## 8. Những lỗi thường gặp

| Sai lầm | Vì sao sai? | Cách hiểu đúng |
|---|---|---|
| Nghĩ `class Student` tự tạo học viên cụ thể | Chỉ định nghĩa kiểu | Cần `new Student(...)` để tạo instance |
| Nghĩ `Student s;` tạo object | Chỉ khai báo biến tham chiếu local | Gán tham chiếu object trước khi dùng |
| Nghĩ `Student s = null;` là object rỗng | `null` là không trỏ đến object | Không gọi `s.method()` khi null |
| Nghĩ `Student b = a;` tạo bản sao | Chỉ sao chép tham chiếu | `a` và `b` cùng object |
| Nghĩ thay `alias` sang `new` làm `an` đổi theo | Hai biến tham chiếu được gán độc lập | Gán lại alias không gán lại an |
| Nghĩ các object chia sẻ toàn bộ instance fields | Instance có trạng thái riêng | Chỉ thành viên static chung theo lớp |
| Viết constructor `void Student(...)` | Đó là method, không phải constructor | Constructor không có kiểu trả về |
| Truy cập `private` field từ bên ngoài lớp | Vi phạm truy cập | Dùng phương thức phù hợp |
| Dùng `==` để so nội dung tất cả fields | `==` với object reference so identity | Dùng chính sách equals khi cần |
| In object trực tiếp rồi mong format ổn định | `toString` mặc định không mô tả giá trị nghiệp vụ | Viết `describe`/`toString` tùy nhu cầu |

## 9. Bài tập có đáp án

**A — Tạo bao nhiêu object?**

~~~java
Student first = new Student("Lan", 50);
Student second = first;
Student third = new Student("Mai", 60);
~~~

**Đáp án:** **2 Student objects** do có hai lần `new Student`. `second` chỉ là alias của `first`.

**B — Sau phép cập nhật qua alias thì sao?**

~~~java
Student first = new Student("Lan", 50);
Student alias = first;
alias.increase(10);
System.out.println(first.getScore());
~~~

**Đáp án:** `60`, vì hai biến trỏ tới cùng object.

**C — Hai object cùng dữ liệu có chắc `==` true không?**

~~~java
Student a = new Student("An", 70);
Student b = new Student("An", 70);
System.out.println(a == b);
~~~

**Đáp án:** `false`. Hai lần `new` tạo hai object riêng, dù field có giá trị giống nhau.

**D — Khi nào dùng `static`?** Khi dữ liệu/hành vi gắn với **lớp**, chẳng hạn bộ đếm số object tạo thành công trong ví dụ. Với dữ liệu của từng học viên, dùng instance field.

**E — Field nào thuộc từng object?** Trong `Student` của chương trình, `name` và `score` là instance fields; `createdCount` là static field.

## 10. Checklist và kết luận

- [ ] Hiểu **class định nghĩa kiểu**, object là instance cụ thể.
- [ ] Phân biệt khai báo biến tham chiếu với tạo object bằng `new`.
- [ ] Biết constructor thiết lập trạng thái ban đầu.
- [ ] Nhận ra mỗi object có bộ instance fields riêng.
- [ ] Hiểu `b = a` chia sẻ object, không tự sao chép.
- [ ] Biết `==` so identity của tham chiếu object.
- [ ] Phân biệt instance method và static member.
- [ ] Nhận diện `null` và tránh dereference khi không có object.
- [ ] Biết dùng `private` và method để quản lý trạng thái.
- [ ] Biên dịch và chạy `ClassObjectBasicsDemo.java` với JDK 17+, đối chiếu exact stdout.

**Kết luận:** **Lớp là định nghĩa kiểu và hành vi; đối tượng là một thể hiện cụ thể được tạo khi chạy**. Nhiều object có thể cùng kiểu nhưng giữ trạng thái riêng; nhiều biến tham chiếu cũng có thể cùng trỏ đến **một** object. Đó là nền tảng để hiểu constructor, instance fields, instance methods và `static` trong Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classes-and-objects-002">Trường biểu diễn dữ liệu và phương thức biểu diễn hành vi</a>

<details>
<summary>Xem chi tiết</summary>

# Trường biểu diễn dữ liệu và phương thức biểu diễn hành vi — Java 17+

## 1. Hai thành phần cơ bản của một lớp

Trong Java, **lớp (class)** mô tả kiểu đối tượng. Một lớp thường tập hợp:

- **Trường (field)** để lưu **dữ liệu/trạng thái** của đối tượng, chẳng hạn tên sách và tình trạng đang được mượn.
- **Phương thức (method)** để mô tả **hành vi/thao tác**, chẳng hạn mượn sách, trả sách hoặc hiển thị tình trạng.

~~~java
class Book {
    String title;       // Field: dữ liệu về cuốn sách.
    boolean borrowed;   // Field: trạng thái hiện tại.

    void borrow() {     // Method: một hành vi.
        borrowed = true;
    }

    void giveBack() {   // Method: một hành vi khác.
        borrowed = false;
    }
}
~~~

Ví dụ này chỉ giới thiệu cấu trúc. Code hoàn chỉnh bên dưới sẽ dùng `private` và kiểm tra điều kiện để ngăn mượn một cuốn sách đang được mượn.

**Phân biệt quan trọng:** một trường mô tả “đối tượng **đang có gì/ở trạng thái nào**”; một phương thức mô tả “đối tượng **có thể làm gì**”. Hai thành phần không tách rời: hành vi có thể **đọc hoặc cập nhật** trạng thái nhưng cần tuân thủ quy tắc của lớp.

## 2. Khai báo và truy cập field

Cú pháp khái quát:

~~~java
[accessModifier] [static] [final] Type fieldName;
~~~

Ví dụ:

~~~java
class Student {
    private String name;
    private int score;
    private boolean active;
}
~~~

`name`, `score`, `active` là ba **instance fields** — mỗi đối tượng `Student` có phần trạng thái riêng. Khi tạo hai đối tượng bằng hai lần `new`, thay đổi field của đối tượng thứ nhất không tự làm thay đổi đối tượng thứ hai.

Với field không được khởi tạo tường minh, Java thiết lập **giá trị mặc định** khi đối tượng được tạo:

| Kiểu field | Giá trị mặc định |
|---|---|
| `int`, `long`, các kiểu số nguyên | `0` tương ứng |
| `double`, `float` | `0.0` tương ứng |
| `boolean` | `false` |
| `char` | `'\\u0000'` |
| Kiểu tham chiếu như `String`, `int[]` | `null` |

**Khác biệt:** biến cục bộ khai báo `int n;` **không có giá trị mặc định để đọc ngay**. Với field, Java khởi tạo mặc định trước khi constructor hoàn tất.

Trong code dùng `private`, mã bên ngoài lớp **không được viết trực tiếp** `book.borrowed = true;`. Lớp nên công khai một hành vi hợp lệ như `book.borrow()` nếu việc chuyển trạng thái cần quy tắc kiểm tra.

## 3. Khai báo method và gọi hành vi

Cú pháp khái quát của một phương thức:

~~~java
[accessModifier] [static] ReturnType methodName(ParameterList) {
    // Thân phương thức.
}
~~~

Ví dụ:

~~~java
class Counter {
    private int value;

    void increase() {
        value++;
    }

    void add(int amount) {
        value += amount;
    }

    int getValue() {
        return value;
    }
}
~~~

- `void increase()`: hành vi không nhận đối số, không trả một giá trị.
- `void add(int amount)`: nhận **đối số** khi gọi (ví dụ `add(3)`) qua tham số `amount`.
- `int getValue()`: trả về `int` bằng lệnh `return value;`.
- `getValue()` **đọc** trạng thái; `increase()`, `add(...)` **thay đổi** trạng thái.

~~~java
Counter counter = new Counter();
counter.increase();
counter.add(3);
System.out.println(counter.getValue());
~~~

**Exact expected stdout:**

~~~text
4
~~~

Ví dụ trên sử dụng lớp `Counter` ngay phía trên; không phải chương trình độc lập nếu chỉ sao chép riêng bốn dòng gọi. Chương trình hoàn chỉnh để chạy bằng CLI được cung cấp ở mục 9.


## 4. Constructor cung cấp trạng thái bắt đầu cho field

~~~java
class Student {
    private final String name;
    private int score;

    Student(String name, int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score out of range");
        }
        this.name = name;
        this.score = score;
    }
}
~~~

Constructor kiểm tra và gán field của instance mới. Một field `final` không được gán lại sau khi hoàn tất khởi tạo theo quy tắc Java; field khác vẫn có thể đổi qua hành vi có kiểm tra. Từ khóa `this` chọn **field của object hiện tại** khi tên field trùng tham số. Ở bài này, quan trọng là **dữ liệu ban đầu và các method cập nhật sau đó tuân thủ cùng hợp đồng trạng thái**. Các biến thể constructor và `this(...)` có bài riêng.

## 5. Method thao tác trên trạng thái của receiver

Nếu `student.addScore(5)` là một lời gọi instance, `addScore` sẽ đọc/cập nhật field của object mà `student` đang trỏ đến. Có thể viết `this.score += points;` hoặc `score += points;` khi tên không bị che khuất. Để phân biệt field với tham số cùng tên, viết rõ `this.score = score;`.

Hai object được tạo riêng có field instance riêng; hai biến cùng tham chiếu một object sẽ quan sát cùng trạng thái. Phần này giúp đọc hành vi `Book.borrow()` ở dưới, không yêu cầu học lại toàn bộ phép gán tham chiếu.

## 6. Tại sao nên để field `private`?

Ví dụ phản diện:

~~~java
class Book {
    public boolean borrowed;
}
~~~

Code khác có thể đặt `book.borrowed = true;` hoặc `false` từ bất kỳ đâu cho phép truy cập, ngay cả khi quy trình mượn/trả còn phải cập nhật thống kê hoặc kiểm tra điều kiện. Trạng thái dễ trở nên **không nhất quán**.

Đóng gói (*encapsulation*) giúp lớp tự quản lý dữ liệu:

~~~java
class Book {
    private boolean borrowed;
    private int borrowCount;

    boolean borrow() {
        if (borrowed) return false;
        borrowed = true;
        borrowCount++;
        return true;
    }

    boolean returnBook() {
        if (!borrowed) return false;
        borrowed = false;
        return true;
    }

    boolean isBorrowed() {
        return borrowed;
    }
}
~~~

Hợp đồng:

- `borrow()` trả `true` nếu mượn thành công và **chuyển trạng thái** sang đang mượn; trả `false` nếu đã được mượn, không tăng `borrowCount`.
- `returnBook()` trả `true` khi cuốn sách đang được mượn và được trả thành công; nếu đã có sẵn thì trả `false`.
- `isBorrowed()` chỉ **đọc trạng thái**, không sửa dữ liệu.

`private` không tự đảm bảo mọi thiết kế đều đúng; lợi ích là các thay đổi trạng thái có thể tập trung trong những phương thức kiểm soát quy tắc. Không nhất thiết phải tạo setter cho mọi field: một setter công khai không có kiểm tra có thể làm mất ý nghĩa đóng gói.


## 7. Field và method instance là trọng tâm của mô hình Book

Trong ví dụ `Book`, `borrowed` và `borrowCount` là **instance fields**: mỗi sách giữ tình trạng và số lượt mượn của mình. `borrow()`, `returnBook()`, `summary()` là **instance methods** đọc hoặc thay đổi những field ấy.

`createdCount` và `getCreatedCount()` là thành viên **static**, dùng chung ở cấp lớp để minh họa bộ đếm số đối tượng tạo thành công. Gọi `Book.getCreatedCount()` qua tên lớp. Static method không có `this` để truy cập trạng thái của một sách cụ thể; bài riêng về `static` sẽ giải thích sâu hơn thời điểm khởi tạo, chia sẻ trạng thái và đa luồng.

## 8. Dữ liệu, hành vi và tính nhất quán của đối tượng

Một mô hình đơn giản:

~~~text
                  class Book
         +--------------------------+
FIELDS   | title: String            |  Trạng thái
         | borrowed: boolean        |
         | borrowCount: int         |
         +--------------------------+
METHODS  | borrow(): boolean        |  Hành vi
         | returnBook(): boolean    |
         | summary(): String        |
         +--------------------------+

bookA ──> {title="Java", borrowed=true,  borrowCount=2}
bookB ──> {title="OOP",  borrowed=false, borrowCount=0}
~~~

Hai đối tượng cùng kiểu `Book` có thể ở **hai trạng thái khác nhau**. Các phương thức được khai báo trong một định nghĩa lớp và hoạt động trên đối tượng nhận lời gọi.

**Bất biến nghiệp vụ** (*invariant*) của ví dụ: `borrowCount` không âm; mỗi lần mượn thành công mới tăng bộ đếm; trả sách không làm giảm tổng số lượt đã mượn. Constructor kiểm tra `title` không rỗng để tránh tạo object thiếu tên hợp lệ.

Một getter thường **trả về** thông tin, còn một phương thức hành vi như `borrow()` truyền đạt rõ hơn mục đích nghiệp vụ so với cho phép bên ngoài sửa trực tiếp field `borrowed`.

## 9. Chương trình Java 17+ hoàn chỉnh

Lưu file đúng tên **`FieldsMethodsDemo.java`**:

~~~java
public final class FieldsMethodsDemo {
    private FieldsMethodsDemo() {}

    static final class Book {
        private static int createdCount;

        private final String title;
        private boolean borrowed;
        private int borrowCount;

        Book(String title) {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title must not be blank");
            }
            this.title = title.strip();
            // borrowed mặc định false, borrowCount mặc định 0.
            createdCount++;
        }

        boolean borrow() {
            if (borrowed) {
                return false;
            }
            borrowed = true;
            borrowCount++;
            return true;
        }

        boolean returnBook() {
            if (!borrowed) {
                return false;
            }
            borrowed = false;
            return true;
        }

        boolean isBorrowed() {
            return borrowed;
        }

        int getBorrowCount() {
            return borrowCount;
        }

        String summary() {
            return title + " | borrowed=" + borrowed
                    + " | times=" + borrowCount;
        }

        static int getCreatedCount() {
            return createdCount;
        }
    }

    public static void main(String[] args) {
        System.out.println("initial created=" + Book.getCreatedCount());

        Book javaBook = new Book(" Java ");
        Book algoBook = new Book("Algorithms");
        System.out.println("created after new=" + Book.getCreatedCount());

        System.out.println("java start=" + javaBook.summary());
        System.out.println("algorithms start=" + algoBook.summary());
        System.out.println("borrow java=" + javaBook.borrow());
        System.out.println("borrow java again=" + javaBook.borrow());
        System.out.println("java after borrow=" + javaBook.summary());
        System.out.println("algorithms unchanged=" + algoBook.summary());

        System.out.println("return java=" + javaBook.returnBook());
        System.out.println("return java again=" + javaBook.returnBook());

        Book alias = javaBook;
        System.out.println("borrow alias=" + alias.borrow());
        System.out.println("alias same object=" + (alias == javaBook));
        System.out.println("java final=" + javaBook.summary());
        System.out.println("algorithms final=" + algoBook.summary());
        System.out.println("total created=" + Book.getCreatedCount());

        try {
            new Book(" ");
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid title=" + ex.getMessage());
        }
        System.out.println("total after invalid=" + Book.getCreatedCount());
    }
}
~~~

**Yêu cầu:** JDK 17+; không cần thư viện ngoài, Maven, Gradle hoặc preview features. Mã dùng một `public class` có tên trùng tên file và một lớp `Book` lồng nhau để mọi thứ có thể chạy từ một file.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 FieldsMethodsDemo.java
java -cp . FieldsMethodsDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\FieldsMethodsDemo.java
java -cp . FieldsMethodsDemo
~~~

### Exact expected stdout

~~~text
initial created=0
created after new=2
java start=Java | borrowed=false | times=0
algorithms start=Algorithms | borrowed=false | times=0
borrow java=true
borrow java again=false
java after borrow=Java | borrowed=true | times=1
algorithms unchanged=Algorithms | borrowed=false | times=0
return java=true
return java again=false
borrow alias=true
alias same object=true
java final=Java | borrowed=true | times=2
algorithms final=Algorithms | borrowed=false | times=0
total created=2
invalid title=title must not be blank
total after invalid=2
~~~

**Giải thích từng pha:**

1. Khi mới chạy, `createdCount` mặc định bằng 0. Hai `new Book(...)` thành công khiến số đó thành 2.
2. `borrowed` và `borrowCount` là **instance fields** mặc định `false` và `0` cho **mỗi** đối tượng.
3. Mượn sách `Java` lần thứ nhất thành công; lần thứ hai bị từ chối, nên `times` vẫn bằng 1. Sách `Algorithms` không đổi.
4. Trả sách lần thứ nhất thành công, lần thứ hai trả `false` vì sách không còn được mượn.
5. `alias` và `javaBook` cùng chỉ đến một object; mượn qua `alias` khiến `javaBook.summary()` hiển thị `times=2`.
6. Constructor bị lỗi với tiêu đề toàn khoảng trắng **trước** dòng tăng bộ đếm, nên `createdCount` vẫn là 2.

## 10. Lỗi phổ biến

| Nhầm lẫn | Điều cần hiểu |
|---|---|
| Nghĩ field và method đều là “biến” | Field lưu dữ liệu; method là khai báo hành vi có thể được gọi |
| Tưởng `Book a;` đã tạo object | Đây chỉ là khai báo biến tham chiếu; cần `new Book(...)` |
| Nghĩ hai đối tượng cùng lớp chia sẻ mọi field | **Instance fields** là trạng thái từng object; **static fields** mới gắn với lớp |
| Viết `score = score;` khi tên tham số trùng field | Dùng `this.score = score;` để gán cho field |
| Cho mọi field thành `public` để sửa tùy ý | Có thể phá vỡ quy tắc của lớp; cân nhắc `private` và phương thức kiểm soát |
| Gọi method `borrow()` rồi coi giá trị trả về là field | Giá trị `boolean` được method tính/trả, không phải khai báo field mới |
| Gọi instance method từ `static main` mà không có object | Tạo/lấy tham chiếu object rồi gọi `book.borrow()` |
| Dùng `this` trực tiếp trong static method | Static context không có receiver instance |
| Nghĩ `final String title` khiến cả object bất biến | Chỉ field `title` không được gán lại; `borrowed` và `borrowCount` vẫn có thể thay đổi |
| Gán `Book alias = book;` rồi nghĩ có bản sao object | Chỉ sao chép giá trị tham chiếu, hai biến chỉ cùng object |

## 11. Bài tập có đáp án

**A.** Phân loại các thành viên sau:

~~~java
private int balance;
void deposit(int amount) { balance += amount; }
int getBalance() { return balance; }
~~~

**Đáp án:** `balance` là **instance field**; `deposit` là method **thay đổi trạng thái**; `getBalance` là method **đọc/trả trạng thái**.

**B.** Nếu tạo `Book first = new Book("A")` và `Book second = new Book("B")`, rồi chỉ gọi `first.borrow()`, trường `borrowed` của `second` là gì?  
**Đáp án:** `false`, vì đó là instance field riêng của `second`.

**C.** Vì sao `private boolean borrowed` giúp thiết kế hợp lý hơn `public boolean borrowed`?  
**Đáp án:** caller không thể tùy tiện gán field từ ngoài lớp, nên lớp có thể đảm bảo chuyển trạng thái qua `borrow()`/`returnBook()` đúng quy tắc.

**D.** `Book alias = first;` rồi `alias.borrow()` có tác động đối tượng `first` không?  
**Đáp án:** **Có**, vì hai tham chiếu cùng trỏ tới một đối tượng.

**E.** Một static method có gọi trực tiếp `this.summary()` được không?  
**Đáp án:** **Không**. Static method không có receiver instance mặc định; cần tham chiếu object cụ thể.

**F.** Tại sao lần tạo `new Book(" ")` của chương trình hoàn chỉnh không tăng `createdCount`?  
**Đáp án:** constructor ném `IllegalArgumentException` trong bước kiểm tra, trước khi thực hiện `createdCount++`.

## 12. Checklist và kết luận

- [ ] Biết **field lưu dữ liệu/trạng thái** và **method thể hiện hành vi**.
- [ ] Phân biệt khai báo lớp với tạo đối tượng bằng `new`.
- [ ] Nhận diện instance fields, instance methods và các thành viên `static`.
- [ ] Hiểu constructor thiết lập trạng thái ban đầu.
- [ ] Dùng `this.field` phân biệt field và tham số cùng tên.
- [ ] Hiểu method có thể đọc, cập nhật field và trả kết quả.
- [ ] Dùng `private` và hành vi có kiểm tra để giữ trạng thái hợp lệ.
- [ ] Phân biệt thay đổi trạng thái một object và thay đổi object khác.
- [ ] Biết alias là nhiều tham chiếu cùng một object, không phải bản sao mới.
- [ ] Đọc và đối chiếu exact stdout của `FieldsMethodsDemo.java` trên JDK 17+.

**Kết luận:** Lớp Java gom **dữ liệu** trong các field và **hành vi** trong các method. Field thể hiện trạng thái của từng object; method sử dụng và thay đổi trạng thái theo quy tắc của lớp. Thiết kế field `private`, constructor hợp lệ và những method có mục đích rõ ràng giúp chương trình dễ hiểu, an toàn và dễ mở rộng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classes-and-objects-003">Tạo đối tượng và gọi phương thức bằng dấu chấm</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo đối tượng và gọi phương thức bằng dấu chấm trong Java

> **Java core — language-basics / Lớp và đối tượng**  
> **Môi trường:** JDK 17+, một file Java tự chứa.  
> **Mục tiêu:** Tạo đối tượng bằng `new`, giữ tham chiếu trong biến, gọi phương thức bằng cú pháp `object.method(arguments)`, hiểu kết quả/`void`, trạng thái riêng từng đối tượng, alias, `null` và khác biệt giữa phương thức instance với `static`.

## 1. Từ lớp đến đối tượng

**Lớp** (*class*) khai báo trạng thái (trường) và hành vi (phương thức). **Đối tượng** (*object*) là một thực thể được tạo từ lớp đó. Câu lệnh `new Type(...)` thường cấp phát đối tượng và gọi **constructor** phù hợp để khởi tạo nó:

~~~java
class Lamp {
    private boolean on;

    Lamp(boolean initiallyOn) { // Constructor: tên giống lớp, không có kiểu trả về.
        this.on = initiallyOn;
    }

    void turnOn() {              // Phương thức instance.
        this.on = true;
    }

    boolean isOn() {             // Trả về trạng thái hiện tại.
        return this.on;
    }
}

// Sử dụng từ một phương thức phù hợp:
// Lamp deskLamp = new Lamp(false);
// deskLamp.turnOn();
// System.out.println(deskLamp.isOn()); // true.
~~~

Trong `Lamp deskLamp = new Lamp(false);`:

| Phần | Ý nghĩa |
| --- | --- |
| `Lamp` bên trái | Kiểu của biến tham chiếu |
| `deskLamp` | Tên biến giữ tham chiếu đến đối tượng |
| `new Lamp(false)` | Tạo đối tượng và gọi constructor nhận `boolean` |
| `deskLamp.turnOn()` | Gọi phương thức instance qua tham chiếu và dấu chấm |
| `deskLamp.isOn()` | Gọi phương thức rồi nhận giá trị `boolean` |

Constructor **không phải phương thức `void`**. Tên constructor trùng tên lớp và không khai báo kiểu trả về; không thể gọi lại nó bằng cú pháp `deskLamp.Lamp(...)`.

## 2. Dấu chấm `.` để gọi phương thức

Cú pháp thường gặp:

~~~java
receiver.methodName(argument1, argument2);
~~~

`receiver` là biểu thức tham chiếu tới đối tượng (như tên biến `deskLamp`). Dấu `.` chọn thành viên có thể truy cập; dấu ngoặc `(...)` và đối số thể hiện **lời gọi phương thức**.

~~~java
String text = " java ";
String clean = text.trim(); // "java"
int size = clean.length();  // 4.
boolean empty = clean.isEmpty(); // false.
~~~

Các phương thức này là instance method của `String`: mỗi lời gọi xác định đối tượng nhận lời gọi qua `text` hoặc `clean`. Trong khi đó, `text.length` (thiếu ngoặc) **không phải lời gọi hợp lệ** vì `String` cung cấp phương thức `length()` chứ không phải trường công khai `length`.

- `object.field`: truy cập **trường** nếu quyền truy cập cho phép.
- `object.method()`: gọi **phương thức không tham số**.
- `object.method(arg)`: gọi phương thức có tham số tương ứng.
- `method()` trong cùng lớp có thể là cách viết rút gọn trong ngữ cảnh phù hợp, nhưng viết `object.method()` giúp chỉ rõ **đối tượng nhận lời gọi**.

Nếu trường là `private`, mã ở một lớp không được cấp quyền không thể đọc/ghi trực tiếp `object.field`; hãy dùng phương thức công khai thích hợp thay vì tùy tiện làm trường `public`.

## 3. Phương thức có tham số, kiểu trả về và `void`

~~~java
class Counter {
    private int value;

    Counter(int start) {
        this.value = start;
    }

    void add(int amount) {
        this.value += amount;
    }

    int current() {
        return this.value;
    }
}

// Counter counter = new Counter(2);
// counter.add(3);          // Cập nhật giá trị, không trả giá trị.
// int now = counter.current(); // now == 5.
~~~

Khi gọi `counter.add(3)`, `3` là **đối số**, còn `amount` là **tham số** của phương thức `add`. `counter.current()` trả một giá trị `int` để gán, in hoặc dùng trong biểu thức:

~~~java
// System.out.println("now=" + counter.current());
// int doubleValue = counter.current() * 2;
~~~

`void` nghĩa là phương thức **không cung cấp giá trị trả về**; không thể viết `int x = counter.add(3);`. Ngược lại, kiểu trả về `int` cho phép lấy giá trị từ `current()`.

Trong thân phương thức instance, `this` chỉ **đối tượng hiện tại** đang nhận lời gọi. `this.value` rõ ràng là trường của đối tượng, không phải biến cục bộ cùng tên. Các đối số Java được truyền **theo giá trị**, kể cả khi giá trị là tham chiếu tới đối tượng.


## 4. Lời gọi qua dấu chấm chọn đúng receiver

~~~java
Counter first = new Counter(2);
Counter second = new Counter(5);
first.add(3);
second.add(-2);
// first.current() == 5; second.current() == 3.
~~~

Dấu chấm trong `first.add(3)` chọn object do `first` tham chiếu, còn `second.add(-2)` tác động trên object thứ hai. Hai lời gọi cùng method nhưng có **receiver** khác nhau nên thay đổi hai field `value` độc lập.

## 5. Alias và null ảnh hưởng thế nào đến lời gọi?

~~~java
Counter alias = first;
alias.add(1);                 // first.current() == 6.
alias = new Counter(1);       // Chỉ thay tham chiếu alias.
Counter missing = null;
// missing.add(1);            // NullPointerException nếu chạy.
~~~

`alias = first` sao chép giá trị tham chiếu, **không** gọi constructor. Khi hai tên chỉ cùng object, lời gọi qua tên nào cũng đến cùng trạng thái; gán lại `alias` không làm `first` đổi tham chiếu. Với `null`, không có object làm receiver để gọi instance method, nên phải kiểm tra hoặc bảo đảm tham chiếu khác null.

Để so xem hai tham chiếu có cùng object, dùng `==`; so sánh **nội dung nghiệp vụ** của hai object cần hợp đồng `equals` thích hợp. Bài đầu chương giải thích sâu hơn về object identity và alias; ở đây hãy tập trung vào receiver của từng lời gọi.

## 6. Gọi nối tiếp và biểu thức tạo đối tượng tạm

Vì `new Counter(...)` là biểu thức cho ra tham chiếu, có thể gọi phương thức ngay trên đối tượng mới:

~~~java
// new Counter(4).current()  // Trả 4.
~~~

Nếu một phương thức trả lại đối tượng hoặc một tham chiếu thích hợp, có thể **gọi nối tiếp** (*method chaining*):

~~~java
class Accumulator {
    private int total;

    Accumulator add(int number) {
        total += number;
        return this;
    }

    int value() {
        return total;
    }
}

// int result = new Accumulator().add(2).add(3).value();
// result == 5.
~~~

Phân giải từ trái sang phải theo chuỗi nhận kết quả: `new Accumulator()` tạo đối tượng, `add(2)` trả về `this`, `add(3)` cập nhật cùng đối tượng, rồi `value()` trả kết quả.

Nếu phương thức trả `void`, không thể nối tiếp bằng cách gọi một phương thức khác **trên giá trị trả về** của lời gọi `void`. Dùng tên biến đối tượng và gọi các lệnh riêng để rõ ràng khi cần.


## 7. Dấu chấm cũng xuất hiện ở lời gọi static

~~~java
Counter counter = new Counter(2);
int current = counter.current();       // Instance: có receiver Counter.
int maximum = Math.max(4, 9);        // Static: Math là tên lớp.
~~~

Cùng dấu `.`, nhưng `counter.current()` gọi hành vi trên instance, còn `Math.max(...)` gọi static method qua **tên lớp**, không cần object `Math`. Hãy ưu tiên `Type.staticMethod(...)` để tránh hiểu nhầm một biến object là receiver của phương thức static; bài chuyên đề `static` phân tích đầy đủ sự khác biệt.

## 8. Chương trình Java 17+ chạy độc lập

Tạo file **DotInvocationDemo.java**:

~~~java
public final class DotInvocationDemo {
    private static final class Counter {
        private final String label;
        private int value;

        Counter(String label, int start) {
            this.label = label;
            this.value = start;
        }

        void increment() {
            this.value++;
        }

        void add(int amount) {
            this.value += amount;
        }

        int current() {
            return this.value;
        }

        boolean isPositive() {
            return this.value > 0;
        }

        String summary() {
            return label + ":" + value;
        }

        Counter addAndReturn(int amount) {
            add(amount);
            return this;
        }

        static int recommendedStart() {
            return 1;
        }
    }

    public static void main(String[] args) {
        Counter first = new Counter("A", 2);
        Counter second = new Counter("B", 5);
        System.out.println("createdA=" + first.summary());
        System.out.println("createdB=" + second.summary());

        first.increment();
        first.add(3);
        second.add(-2);
        System.out.println("afterCallsA=" + first.summary());
        System.out.println("afterCallsB=" + second.summary());
        System.out.println("returnedValue=" + first.current());
        System.out.println("isPositive=" + first.isPositive());

        Counter alias = first;
        alias.increment();
        System.out.println("aliasSame=" + (alias == first));
        System.out.println("afterAlias=" + first.summary());

        Counter another = new Counter("A", 7);
        System.out.println("sameStateDifferentObject=" + (first == another));
        System.out.println("anotherSummary=" + another.summary());

        System.out.println("afterChain="
                + first.addAndReturn(2).addAndReturn(1).summary());
        System.out.println("temporary="
                + new Counter("T", 4).summary());

        alias = new Counter("X", 1);
        System.out.println("aliasAfterReassign=" + alias.summary());
        System.out.println("firstAfterReassign=" + first.summary());

        System.out.println("staticDefault=" + Counter.recommendedStart());
        System.out.println("mathMax=" + Math.max(4, 9));

        Counter missing = null;
        System.out.println("missingIsNull=" + (missing == null));
        try {
            missing.increment();
        } catch (NullPointerException ex) {
            System.out.println("nullCall=NullPointerException");
        }
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 DotInvocationDemo.java
java DotInvocationDemo
~~~

**Expected stdout chính xác:**

~~~text
createdA=A:2
createdB=B:5
afterCallsA=A:6
afterCallsB=B:3
returnedValue=6
isPositive=true
aliasSame=true
afterAlias=A:7
sameStateDifferentObject=false
anotherSummary=A:7
afterChain=A:10
temporary=T:4
aliasAfterReassign=X:1
firstAfterReassign=A:10
staticDefault=1
mathMax=9
missingIsNull=true
nullCall=NullPointerException
~~~

Hai đối tượng `first`/`second` có trạng thái độc lập. `aliasSame=true` nhưng `sameStateDifferentObject=false` chứng minh **alias khác với đối tượng mới có cùng trạng thái**. `afterChain` minh họa các phương thức trả về `this` cho phép gọi nối tiếp; `nullCall` chỉ lỗi khi gọi instance method qua tham chiếu `null`.

## 9. Lỗi thường gặp và bài tập

| Lỗi / hiểu nhầm | Cách hiểu hoặc sửa |
| --- | --- |
| Viết `Counter c; c.increment();` với local chưa khởi tạo | Tạo `new Counter(...)` hoặc gán tham chiếu hợp lệ trước khi dùng |
| Viết `Counter c = null; c.increment();` | Đối tượng chưa tồn tại; có thể gây `NullPointerException` |
| Gọi `c.current` thay vì `c.current()` | Phương thức phải có `(...)` kể cả khi không có đối số |
| Viết `int n = c.increment();` khi phương thức trả `void` | Không thể dùng lời gọi `void` làm giá trị |
| Tưởng `Counter alias = c` tạo object mới | Chỉ sao chép tham chiếu; dùng `new` khi cần đối tượng mới |
| Tưởng hai đối tượng cùng lớp luôn có chung trường instance | Mỗi đối tượng có giá trị trường instance riêng |
| Tưởng constructor được gọi bằng `c.Counter()` | Constructor chạy khi `new Counter(...)`, không phải method bình thường |
| Dùng `Math.max` như instance method | Gọi static bằng `Math.max(...)` |
| Truy cập trường `private` từ lớp khác | Dùng phương thức được cấp quyền, thiết kế getter/setter nếu cần |

**Bài 1.** Tạo lớp `Rectangle` có constructor nhận chiều rộng và chiều cao, phương thức `area()` trả diện tích; gọi qua `rectangle.area()`.

**Bài 2.** Tạo hai đối tượng `Counter` khác nhau, gọi `add` với các đối số khác nhau và giải thích vì sao trạng thái không tự đồng bộ.

**Bài 3.** Tạo `Counter alias = first` rồi gọi `alias.increment()`. Sau đó gán `alias = new Counter(...)` và dự đoán `first.current()`.

**Bài 4.** Viết phương thức trả `this` để hỗ trợ `counter.addAndReturn(2).addAndReturn(3)`. Thử thay kiểu trả về thành `void` rồi quan sát lỗi khi chaining.

**Bài 5.** Phân biệt lời gọi `object.instanceMethod()` và `ClassName.staticMethod()`. Nêu lý do gọi instance method qua null thất bại.

### Ghi nhớ

**`new Type(args)` tạo đối tượng và gọi constructor; biến kiểu lớp thường giữ tham chiếu tới đối tượng.** Dùng `object.method(args)` để gọi hành vi của **đối tượng nhận lời gọi**, kèm `(...)` cho phương thức. Hai biến có thể dùng chung đối tượng qua alias; hai lần `new` tạo đối tượng riêng. `static` thuộc lớp và nên gọi theo `Type.method(...)`; tham chiếu `null` không thể nhận lời gọi instance method.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classes-and-objects-004">Hàm khởi tạo thiết lập giá trị ban đầu</a>

<details>
<summary>Xem chi tiết</summary>

# Hàm khởi tạo thiết lập giá trị ban đầu trong Java

## 1. Constructor là gì?

**Hàm khởi tạo** (*constructor*) là cơ chế khởi tạo đối tượng khi dùng biểu thức `new`. Constructor có **cùng tên với lớp**, **không khai báo kiểu trả về** (kể cả `void`) và được dùng để thiết lập trạng thái ban đầu hợp lệ cho mỗi instance.

```java
class Student {
    String name;
    int age;

    Student(String name, int age) {
        this.name = name;
        this.age = age;
    }
}

// Đặt trong phương thức:
// Student s = new Student("Lan", 18);
// s.name == "Lan" và s.age == 18
```

Biểu thức `new Student("Lan", 18)` tạo một đối tượng `Student` rồi thực thi quá trình khởi tạo, trong đó có constructor được chọn theo danh sách đối số. `this.name` và `this.age` là các **trường của đối tượng hiện tại**; `name` và `age` không có `this` trong constructor là **tham số**. Nếu viết `name = name;`, đó chỉ là tự gán tham số, không lưu giá trị vào trường.

Constructor chủ yếu để thiết lập **bất biến của đối tượng** (*invariant*): những điều kiện cần đúng ngay sau khi tạo, ví dụ tên không rỗng, tuổi không âm. Tùy yêu cầu, không phải trường nào cũng bắt buộc đặt qua constructor; trường có thể có giá trị mặc định hoặc giá trị khởi tạo tại khai báo.

## 2. Giá trị mặc định và khởi tạo trường

Ngay khi bộ nhớ đối tượng được cấp phát, các **trường instance** có giá trị mặc định của kiểu, trước khi các bộ khởi tạo trường và thân constructor thực hiện những phép gán có liên quan.

| Kiểu trường | Giá trị mặc định trước khi được gán rõ ràng |
| --- | --- |
| `byte`, `short`, `int`, `long` | `0` tương ứng với kiểu |
| `float`, `double` | `0.0` tương ứng với kiểu |
| `char` | `'\u0000'` |
| `boolean` | `false` |
| Mọi kiểu tham chiếu, kể cả `String`, mảng và đối tượng | `null` |

```java
class Settings {
    int retries;               // Ban đầu 0
    boolean enabled;           // Ban đầu false
    String mode = "standard";   // Field initializer: đặt giá trị rõ ràng

    Settings(int retries) {
        this.retries = retries;
        this.enabled = true;
    }
}
```

Sau khi `new Settings(3)` hoàn tất, đối tượng có `retries == 3`, `enabled == true`, `mode.equals("standard")`.

**Khác biệt với biến cục bộ:** Java không cho phép đọc một biến cục bộ nếu nó chưa được **definitely assigned** (chắc chắn đã được gán) trước điểm sử dụng; biến cục bộ không tự có giá trị mặc định để đọc như trường đối tượng.

```java
void example() {
    int count;
    // System.out.println(count); // LỖI biên dịch: count chưa được gán
    count = 2;
    System.out.println(count); // Hợp lệ
}
```

Đoạn `example()` là một phương thức minh họa cần được đặt trong lớp.

## 3. Cú pháp và điểm khác phương thức thông thường

```java
class Rectangle {
    private final int width;
    private final int height;

    public Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int area() {
        return width * height;
    }
}
```

- `Rectangle(...)` là **constructor**, không có kiểu trả về.
- `area()` là **phương thức** có kiểu trả về `int`; phương thức có thể được gọi trên đối tượng nhiều lần.
- Constructor chạy trong quá trình tạo instance. Gọi lại constructor như phương thức thông thường, ví dụ `object.Rectangle(2, 3)`, **không hợp lệ**.
- Constructor có thể có các mức truy cập `public`, `protected`, package-private (không ghi modifier) hoặc `private`. Quyền truy cập quyết định mã bên ngoài có được phép dùng `new` với constructor đó hay không.
- Constructor **không được kế thừa** và không bị override. Constructor không khai báo `static`, `abstract` hay `final`.
- `return;` có thể dùng để kết thúc sớm constructor trong một số trường hợp, nhưng constructor **không được trả về giá trị** bằng `return expression;`. Trong thiết kế tốt, nên đảm bảo kiểm tra đầu vào và khởi tạo đầy đủ trước khi hoàn tất bình thường.

Tên constructor phải đúng tên lớp, phân biệt hoa thường. Nếu viết `void Rectangle(...)`, đó là **phương thức tên `Rectangle`**, không phải constructor, và trình biên dịch không xem đó là constructor được khai báo.

## 4. Constructor mặc định, không tham số và có tham số

Cần phân biệt **constructor mặc định do trình biên dịch tự sinh** với **constructor không tham số do lập trình viên viết**:

```java
class Empty {
    // Không khai báo constructor nào.
    // Trình biên dịch cung cấp default constructor không tham số.
}

class Custom {
    Custom() {
        System.out.println("Custom()");
    }
}
```

Khi một lớp **không khai báo constructor nào**, trình biên dịch cấp một constructor mặc định không tham số. Constructor này gọi constructor lớp cha theo quy tắc ngôn ngữ và không tự suy ra các giá trị đầu vào đặc biệt. Mức truy cập của constructor mặc định phụ thuộc mức truy cập của lớp theo quy tắc Java; không nên mặc định cho rằng constructor tự sinh lúc nào cũng là `public`.

Nếu đã khai báo **bất kỳ constructor nào**, trình biên dịch **không còn tự thêm constructor mặc định không tham số**:

```java
class User {
    String name;

    User(String name) {
        this.name = name;
    }
}

// new User("Mai"); // Hợp lệ
// new User();      // LỖI biên dịch: không có constructor User()
```

Muốn cả hai cách tạo đối tượng, phải tự khai báo overload không tham số. Nếu không khai báo constructor trong lớp con, trình biên dịch sẽ sinh constructor mặc định cố gọi `super()`; nếu lớp cha không có constructor không tham số truy cập được, việc biên dịch thất bại. Khi đó lớp con cần tự khai báo constructor và gọi đúng `super(arguments)`.

## 5. Nạp chồng constructor và `this(...)`

**Nạp chồng** (*overloading*) cho phép nhiều constructor có danh sách tham số khác nhau; sử dụng để cung cấp cách tạo đối tượng linh hoạt nhưng vẫn giữ chung quy tắc khởi tạo.

```java
class Ticket {
    private final String title;
    private final int quantity;

    Ticket(String title) {
        this(title, 1); // Ủy quyền cho constructor khác cùng lớp
    }

    Ticket(String title, int quantity) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.title = title;
        this.quantity = quantity;
    }
}
```

Ở **Java 17**, lời gọi constructor khác `this(...)` hoặc lời gọi constructor lớp cha `super(...)`, nếu có, phải là **câu lệnh đầu tiên** của thân constructor. Vì vậy:

- `this(title, 1);` chuyển sang constructor cùng lớp nhận hai đối số.
- Constructor được ủy quyền thực hiện kiểm tra và gán trạng thái, tránh lặp logic ở nhiều nơi.
- Không thể gọi cả `this(...)` và `super(...)` trong cùng thân constructor: mỗi đường khởi tạo cuối cùng phải dẫn tới việc khởi tạo lớp cha đúng một lần.
- Không tạo vòng lặp `this()` lẫn nhau; trình biên dịch từ chối lời gọi constructor đệ quy.
- `this(...)` gọi constructor; `this.field` truy cập trường instance: hai dạng `this` có tác dụng khác nhau.
- Nếu bỏ cả `this(...)` và `super(...)`, trình biên dịch ngầm gọi `super()` khi hợp lệ.

Lưu ý ranh giới phiên bản: một số phiên bản Java mới hơn thay đổi chi tiết cho phép câu lệnh trước lời gọi constructor tường minh; ví dụ bài này giữ quy tắc **Java 17** và tương thích với `javac` 17.

## 6. Lớp cha được khởi tạo trước thân constructor lớp con

Java thực hiện chuỗi khởi tạo đối tượng theo quy tắc. Với các lớp thông thường trong ví dụ:

1. Các trường được đặt giá trị mặc định khi đối tượng được cấp phát.
2. Các constructor cha được gọi theo chuỗi kế thừa cho tới `Object`.
3. Trên đường hoàn tất các lớp trong chuỗi, các **field initializer** và **instance initializer block** của mỗi lớp chạy theo thứ tự xuất hiện trong lớp ấy trước phần thân constructor tương ứng (với constructor ủy quyền qua `this(...)`, các bộ khởi tạo instance của lớp chỉ chạy một lần).
4. Phần thân constructor của lớp cha hoàn tất trước phần thân constructor của lớp con.

Ví dụ độc lập của ý tưởng này khi đặt vào lớp:

```java
class Parent {
    Parent() {
        System.out.println("Parent constructor");
    }
}

class Child extends Parent {
    private int id = 7;

    Child() {
        super(); // Ngầm định nếu bỏ, khi Parent() truy cập được
        System.out.println("Child constructor: " + id);
    }
}
```

Tạo `new Child()` sẽ in `Parent constructor` trước `Child constructor: 7`. Không nên gọi phương thức instance có thể bị override từ constructor của lớp cha: khi override chạy trong lớp con, các trường của lớp con có thể chưa hoàn tất quá trình khởi tạo của nó.

## 7. Ví dụ Java 17+ chạy độc lập

Tạo tệp **`ConstructorDemo.java`**:

```java
public class ConstructorDemo {
    static class Order {
        private final String item;
        private final int quantity;

        Order(String item) {
            this(item, 1); // Constructor overload
        }

        Order(String item, int quantity) {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("Invalid item");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Invalid quantity");
            }
            this.item = item;
            this.quantity = quantity;
        }

        void print() {
            System.out.println(item + ": " + quantity);
        }
    }

    static class Defaults {
        int count;
        String description;

        Defaults() {
            System.out.println("Before: " + count + ", " + description);
            count = 5;
            description = "ready";
        }

        void print() {
            System.out.println("After: " + count + ", " + description);
        }
    }

    static class Parent {
        Parent() {
            System.out.println("Parent()");
        }
    }

    static class Child extends Parent {
        private final int id = 42;

        Child() {
            super();
            System.out.println("Child(): " + id);
        }
    }

    public static void main(String[] args) {
        Order first = new Order("Pen");
        Order second = new Order("Book", 3);
        first.print();
        second.print();

        try {
            new Order("Paper", 0);
        } catch (IllegalArgumentException ex) {
            System.out.println("Rejected: " + ex.getMessage());
        }

        Defaults defaults = new Defaults();
        defaults.print();

        new Child();
    }
}
```

Mở terminal tại thư mục chứa tệp, sử dụng **JDK 17 trở lên** trên Windows PowerShell/CMD hoặc Linux/macOS:

```bash
javac -encoding UTF-8 ConstructorDemo.java
java ConstructorDemo
```

Kết quả **stdout chính xác** (lệnh biên dịch thành công không in gì):

```text
Pen: 1
Book: 3
Rejected: Invalid quantity
Before: 0, null
After: 5, ready
Parent()
Child(): 42
```

Giải thích kết quả:

- `new Order("Pen")` gọi constructor một tham số và ủy quyền qua `this(item, 1)`; `quantity` được khởi tạo thành `1`.
- `new Order("Book", 3)` dùng overload hai tham số và khởi tạo đủ hai trường `final`.
- Constructor từ chối `quantity == 0` bằng `IllegalArgumentException`; không có đối tượng `Order` được trả về cho lời gọi `new` thất bại.
- Trong `Defaults()`, hai trường chưa được gán tường minh có giá trị mặc định `0` và `null`; sau phép gán trong constructor, chúng thành `5` và `"ready"`.
- Với `new Child()`, constructor lớp cha in trước, rồi constructor lớp con đọc field `id` đã được khởi tạo thành `42`.

## 8. Khởi tạo `final`, validation và tham chiếu mutable

### Trường `final` khởi tạo một lần

Một **trường instance `final` chưa được khởi tạo tại khai báo** (*blank final field*) phải được gán chính xác theo các quy tắc *definite assignment* trước khi constructor hoàn tất bình thường:

```java
class Point {
    final int x;
    final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
```

Nếu một nhánh constructor hoàn tất bình thường mà bỏ qua việc gán `x` hoặc `y`, mã không biên dịch được. Nếu field `final` đã được gán tại khai báo, không thể gán lại trường đó trong constructor.

Từ khóa `final` trên một **tham chiếu** ngăn gán lại tham chiếu sau khởi tạo, nhưng không tự biến trạng thái của đối tượng được tham chiếu thành bất biến:

```java
class Holder {
    private final int[] values;

    Holder(int[] input) {
        this.values = input.clone();
    }
}
```

`input.clone()` giúp không dùng chung mảng `int[]` ban đầu khi tạo đối tượng. Nếu getter trả thẳng `values`, mã bên ngoài vẫn có thể sửa phần tử; khi cần bất biến thực tế, phải kiểm soát cả cách công khai và cập nhật trạng thái.

### Kiểm tra dữ liệu ngay lúc tạo

Constructor là nơi phù hợp để từ chối đối số không hợp lệ, ví dụ `name == null`, chuỗi trống hoặc số lượng âm. Cần chọn rõ điều kiện miền dữ liệu; `quantity <= 0` hợp lý cho đơn hàng yêu cầu ít nhất một đơn vị, nhưng không phù hợp với mọi bài toán (ví dụ một bộ đếm hợp lệ có thể bằng 0).

Đừng để lộ `this` trong lúc constructor chưa hoàn tất (ví dụ đăng ký đối tượng vào cấu trúc công khai hoặc khởi chạy luồng dùng `this` quá sớm): mã khác có thể quan sát trạng thái chưa được khởi tạo xong.

## 9. Lỗi phổ biến và cách kiểm tra

| Lỗi | Hậu quả/cách sửa |
| --- | --- |
| Viết `name = name;` khi tham số trùng tên trường | Tham số tự gán cho chính nó; dùng `this.name = name;` |
| Thêm `void` trước tên constructor | Biến thành một phương thức thông thường, không còn là constructor |
| Gọi `new User()` sau khi chỉ khai báo `User(String name)` | Không có constructor không tham số tự sinh; viết overload `User()` nếu cần |
| Dùng `this(...)` sau câu lệnh khác ở Java 17 | Lỗi biên dịch: lời gọi constructor tường minh phải đứng đầu |
| Một constructor chỉ gán vài trường `final` rồi kết thúc | Lỗi *definite assignment*; mọi đường kết thúc bình thường phải gán đủ |
| Tưởng field và biến local đều có giá trị mặc định để đọc | Field có giá trị mặc định; local phải được gán chắc chắn trước khi đọc |
| Gọi phương thức có thể bị override từ constructor cha | Có thể chạy logic của lớp con trước khi trạng thái lớp con khởi tạo xong |
| Lưu trực tiếp mảng mutable được truyền vào rồi coi đối tượng là bất biến | Dữ liệu có thể bị sửa từ ngoài qua cùng tham chiếu; cân nhắc sao chép bảo vệ |

**Ghi nhớ:** constructor định nghĩa cách tạo một instance ở trạng thái ban đầu hợp lệ. Dùng `this.field` để gán đúng field, dùng `this(...)` để chia sẻ logic giữa các constructor, dùng `super(...)` khi cần khởi tạo lớp cha, và kiểm tra đối số trước khi công khai đối tượng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classes-and-objects-005">this biểu thị đối tượng hiện tại</a>

<details>
<summary>Xem chi tiết</summary>

# `this` biểu thị đối tượng hiện tại — Java 17+

## 1. `this` là gì?

Trong **constructor, phương thức instance và ngữ cảnh instance** của một lớp Java, `this` là biểu thức tham chiếu tới **chính đối tượng hiện tại**. Đó là đối tượng được khởi tạo hoặc đối tượng nhận lời gọi phương thức.

~~~java
class Box {
    private int value;

    Box(int value) {
        this.value = value;
    }

    void increase() {
        this.value++;
    }

    int getValue() {
        return this.value;
    }
}
~~~

Nếu tạo hai hộp riêng bằng `new Box(1)` và `new Box(10)`, rồi gọi `increase()` trên hộp đầu, `this` trong lượt gọi ấy chỉ **hộp đầu**. Trong lượt gọi `increase()` trên hộp thứ hai, `this` lại chỉ **hộp thứ hai**.

**Nhớ:** `this` không phải tên lớp, không tạo object, và không phải biến có thể gán lại; nó biểu thị **instance hiện tại**. `this` không được dùng trực tiếp trong ngữ cảnh `static`.

## 2. `this.field`: phân biệt field với tham số hoặc local

~~~java
class Student {
    private String name;

    Student(String name) {
        this.name = name;
    }
}
~~~

Trong `Student(String name)`:

- `name` ở vế phải là **tham số constructor**.
- `this.name` ở vế trái là **field của đối tượng đang tạo**.
- Không có `this`, lệnh `name = name;` chỉ gán tham số cho chính nó; field chưa được cập nhật như mong muốn.

| Biểu thức | Ý nghĩa |
|---|---|
| `name` | Tên đơn; nếu có tham số/local cùng tên, Java dùng tham số/local đó |
| `this.name` | Field `name` của instance hiện tại |
| `this.score` | Field `score` của instance hiện tại |
| `this.getScore()` | Gọi phương thức instance trên instance hiện tại |

Nếu không có biến local/tham số nào che tên field, `score` và `this.score` thường cùng chỉ field của đối tượng đó. Dùng `this` khi cần phân biệt, hoặc khi muốn thể hiện rõ đây là thành viên instance.

## 3. `this.method()` và lời gọi ngầm

Trong một instance method:

~~~java
void addPoints(int amount) {
    this.points += amount;
}

void addAndPrint(int amount) {
    this.addPoints(amount);
    System.out.println(this.points);
}
~~~

Nếu `addPoints` là instance method cùng lớp, có thể viết ngắn `addPoints(amount);`; lời gọi không định danh đối tượng vẫn được thực hiện trên instance hiện tại (trong các trường hợp thông thường).

`this.addPoints(amount)` **không gọi phương thức của một instance mới**. Nó gọi phương thức với cùng receiver `this` hiện có. Khi gọi `an.addAndPrint(5)`, các lần truy cập `this.points` ở hai phương thức đều thuộc object mà `an` trỏ tới.

Không dùng `this` làm cách thay thế cho việc chọn object khác. Muốn thay đổi `binh`, phải gọi trên object `binh` hoặc có tham chiếu rõ ràng tới nó.

## 4. `this(...)` là lời gọi constructor khác

Phân biệt hai cú pháp:

- `this.field`, `this.method()`: dùng **đối tượng hiện tại**.
- `this(arguments);`: gọi **constructor khác trong cùng lớp** để dùng lại logic khởi tạo.

~~~java
class Student {
    private String name;
    private int score;

    Student(String name) {
        this(name, 0);  // Constructor chaining.
    }

    Student(String name, int score) {
        this.name = name;
        this.score = score;
    }
}
~~~

Trên Java 17, một lời gọi constructor tường minh `this(...)` phải là **câu lệnh đầu tiên trong thân constructor**. Không đặt lệnh in, phép gán field hoặc `this.field` trước lời gọi ấy. Một constructor không thể vừa gọi `this(...)` vừa gọi `super(...)` tường minh; chuỗi gọi constructor cũng không được tạo vòng lặp vô hạn giữa các constructor.

`this(name, 0)` **không tạo thêm một Student thứ hai**. Nó thực hiện constructor hai tham số để khởi tạo **cùng object đang được tạo**. Việc tạo object xuất phát từ `new Student(...)`.

## 5. `return this`: trả tham chiếu của chính object

~~~java
class Counter {
    private int count;

    Counter add(int amount) {
        this.count += amount;
        return this;
    }

    int getCount() {
        return this.count;
    }
}
~~~

Có thể viết:

~~~java
Counter counter = new Counter();
Counter returned = counter.add(2).add(3);
System.out.println(counter.getCount());
System.out.println(counter == returned);
~~~

**Exact expected stdout:**

~~~text
5
true
~~~

`return this;` trả **tham chiếu tới chính object đang nhận lời gọi**, không tạo bản sao. Kiểu trả về ở ví dụ là `Counter`, không phải `void`; vì thế mới trả được `this`. Cách trả về này thường dùng trong *fluent API* hoặc *method chaining*, nhưng chỉ nên áp dụng khi làm API rõ ràng.


## 6. Cùng instance thì `this` cũng là cùng receiver

~~~java
Counter first = new Counter();
Counter alias = first;
first.add(1);
alias.add(2);
System.out.println(first.getCount()); // 3
System.out.println(first == alias);    // true
~~~

Hai biến `first` và `alias` cùng tham chiếu một object. Vì thế trong cả hai lần gọi `add`, `this` vẫn chỉ **object Counter ấy**. Khi `alias = new Counter()`, lời gọi tiếp theo qua `alias` sẽ có receiver khác. `this` không phải biến có thể gán lại, và `return this` không sao chép đối tượng. Trường hợp nhiều object và alias được minh họa đầy đủ ở bài đầu chương.

## 7. Khi nào `this` không hợp lệ?

`this` chỉ có ý nghĩa khi có **instance hiện tại**. Phương thức `static` gắn với lớp, không có một receiver instance mặc định:

~~~java
class Student {
    private int score;

    static void invalid() {
        // System.out.println(this.score);
        // LỖI BIÊN DỊCH: không dùng this trong static context.
    }
}
~~~

Cách đúng là nhận hoặc tạo một object rồi truy cập qua tham chiếu đó (tuân thủ phạm vi và quyền truy cập):

~~~java
static int readScore(Student student) {
    return student.score;  // Đọc qua tham chiếu student, không phải this.
}
~~~

Ngoài ra:

- Không thể `this = anotherObject;` vì `this` không phải biến gán được.
- Trong `main` là phương thức static, dùng `student.method()` hoặc `new Student(...)`, không dùng `this.method()`.
- `this` trong một instance method luôn tham chiếu **receiver hiện tại**. Khi có kế thừa, receiver thực tế có thể thuộc lớp con; `super` là cách chọn phiên bản thành viên của lớp cha trong ngữ cảnh thích hợp, khác với `this`.

## 8. Chương trình Java 17+ tự chạy bằng CLI

Lưu file **`ThisCurrentObjectDemo.java`**:

~~~java
public final class ThisCurrentObjectDemo {
    private ThisCurrentObjectDemo() {}

    static final class Student {
        private final String name;
        private int points;

        Student(String name) {
            this(name, 0); // Phải đứng đầu constructor trong Java 17.
        }

        Student(String name, int points) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("invalid name");
            }
            if (points < 0 || points > 100) {
                throw new IllegalArgumentException("invalid points");
            }
            this.name = name.strip();
            this.points = points;
        }

        void addPoints(int delta) {
            if (delta <= 0 || delta > 100 - this.points) {
                throw new IllegalArgumentException("points out of range");
            }
            this.points += delta;
        }

        Student addAndReturnThis(int delta) {
            this.addPoints(delta);
            return this;
        }

        boolean sameObject(Student other) {
            return this == other;
        }

        String describe() {
            return this.name + ":" + this.points;
        }

        static String classLabel() {
            return "Student";
        }
    }

    public static void main(String[] args) {
        Student an = new Student(" An ", 70);
        Student binh = new Student("Binh");

        System.out.println("an=" + an.describe());
        System.out.println("binh=" + binh.describe());

        an.addPoints(5);
        System.out.println("an after direct=" + an.describe());
        System.out.println("binh unchanged=" + binh.describe());

        Student alias = an;
        alias.addPoints(5);
        System.out.println("an via alias=" + an.describe());
        System.out.println("alias same an=" + alias.sameObject(an));
        System.out.println("an same binh=" + an.sameObject(binh));

        Student returned = an.addAndReturnThis(10);
        System.out.println("returned same an=" + (returned == an));
        System.out.println("an after return this=" + an.describe());

        binh.addPoints(20);
        System.out.println("binh after add=" + binh.describe());

        Student sameData = new Student("An", 90);
        System.out.println("same data same object=" + (an == sameData));
        System.out.println("static label=" + Student.classLabel());

        try {
            an.addPoints(11);
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid add=" + ex.getMessage());
        }
        System.out.println("an final=" + an.describe());
    }
}
~~~

**Bash (Linux/macOS), JDK 17+:**

~~~bash
javac --release 17 -encoding UTF-8 ThisCurrentObjectDemo.java
java -cp . ThisCurrentObjectDemo
~~~

**PowerShell (Windows), JDK 17+:**

~~~powershell
javac --release 17 -encoding UTF-8 .\ThisCurrentObjectDemo.java
java -cp . ThisCurrentObjectDemo
~~~

### Exact expected stdout

~~~text
an=An:70
binh=Binh:0
an after direct=An:75
binh unchanged=Binh:0
an via alias=An:80
alias same an=true
an same binh=false
returned same an=true
an after return this=An:90
binh after add=Binh:20
same data same object=false
static label=Student
invalid add=points out of range
an final=An:90
~~~

**Lưu ý tràn số:** Vì `this.points` được giữ trong `0..100`, dùng `delta > 100 - this.points` thay vì cộng trước khi so sánh; nhờ đó `Integer.MAX_VALUE` không thể làm `int` tràn rồi vượt qua kiểm tra.

**Đọc kết quả:** `this` trong `an.addPoints` là object của `an`, không phải `binh`; qua biến `alias` vẫn là object ấy. `this(...)` trong constructor một tham số thiết lập điểm mặc định bằng 0 trên cùng instance `binh`. `return this` trả đúng tham chiếu `an`, không cấp phát object mới.

## 9. Bài tập, lỗi hay gặp và checklist

| Sai lầm | Vì sao sai? | Cách sửa |
|---|---|---|
| `name = name;` khi tham số trùng tên field | Chỉ tác động tham số | `this.name = name;` |
| Nghĩ `this` tạo thêm instance | `this` chỉ instance sẵn có | `new` mới tạo object |
| Dùng `this.field` trong `static` | Không có receiver instance | Dùng object cụ thể |
| Viết lệnh trước `this(...)` trong constructor Java 17 | Vi phạm quy tắc lời gọi constructor đầu tiên | Đặt `this(...)` đầu thân constructor |
| Cho rằng `return this` tạo bản sao | Trả chính tham chiếu object | Tạo bản sao rõ ràng nếu cần |
| Cho rằng `alias = a` tạo object mới | Chỉ sao chép tham chiếu | Phân biệt alias với `new` |

**Bài A:** Với `Student a = new Student("An", 10);` và `Student b = a;`, lời gọi `b.addPoints(5)` làm `a.describe()` trả gì? **`An:15`**: `this` của lời gọi trỏ cùng object.

**Bài B:** Có thể dùng `this` trong `public static void main` không? **Không**. Phải có một object, ví dụ `Student a = new Student("An"); a.describe();`.

**Bài C:** `Student(String name) { this(name, 0); }` tạo mấy Student khi gọi `new Student("An")`? **Một**. Nó gọi constructor khác cho cùng object, không gọi `new` lần thứ hai.

**Bài D:** `return this;` có giống `return new Student(...);` không? **Không**. Một bên trả object đang có; bên còn lại tạo object khác.

### Checklist

- [ ] Hiểu `this` là object hiện tại trong ngữ cảnh instance.
- [ ] Biết `this.field` phân biệt field với tham số/local cùng tên.
- [ ] Biết `this.method()` gọi method trên chính instance ấy.
- [ ] Phân biệt `this(...)` (constructor chaining) với `this.field`.
- [ ] Nhớ trong Java 17, `this(...)` phải là lệnh đầu tiên của constructor.
- [ ] Hiểu `return this` trả cùng tham chiếu, không copy object.
- [ ] Hiểu hai biến alias có thể có cùng receiver `this`.
- [ ] Không dùng `this` trong static context.
- [ ] Biên dịch và chạy `ThisCurrentObjectDemo.java`, đối chiếu exact stdout.

**Kết luận:** `this` luôn giúp xác định **đối tượng hiện tại** trong code instance. Dùng `this.field` để truy cập field, `this.method()` để gọi hành vi trên receiver, `this(...)` để tái sử dụng constructor và `return this` để trả chính instance ấy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classes-and-objects-006">Phân biệt thành viên static với thành viên đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt thành viên `static` với thành viên đối tượng — Java 17+

## 1. `static` thuộc lớp, instance thuộc từng đối tượng

Trong một lớp Java, **thành viên** thường là trường (*field*) hoặc phương thức (*method*). Từ khóa `static` quyết định thành viên được gắn với **lớp** thay vì gắn với từng **đối tượng (instance)**.

~~~java
class Counter {
    static int createdCount; // Static field: gắn với lớp Counter.
    int value;               // Instance field: thuộc từng Counter.

    Counter(int value) {
        this.value = value;
        createdCount++;
    }

    void increase() {        // Instance method: làm việc với đối tượng hiện tại.
        value++;
    }

    static int count() {     // Static method: làm việc không cần receiver instance.
        return createdCount;
    }
}
~~~

~~~java
Counter a = new Counter(2);
Counter b = new Counter(10);
a.increase();
System.out.println(a.value);
System.out.println(b.value);
System.out.println(Counter.count());
~~~

**Exact stdout**, khi các dòng gọi được đặt trong `main` thích hợp của một lần chạy mới:

~~~text
3
10
2
~~~

Hai lần `new Counter(...)` tạo **hai đối tượng** có `value` riêng. Ngược lại, `createdCount` là **một trường static của lớp `Counter`** và tăng ở cả hai lần gọi constructor.

**Quy tắc nhớ:** `static` gắn với **kiểu/lớp**; **instance** gắn với **đối tượng cụ thể**. Không nên hiểu static là “một bản riêng cho mỗi đối tượng”, cũng không hiểu instance là “biến chung của mọi đối tượng”.

## 2. Bảng phân biệt nhanh

| Tiêu chí | Thành viên `static` | Thành viên instance |
|---|---|---|
| Có từ khóa `static`? | **Có** | **Không** |
| Gắn với | Lớp khai báo | Mỗi đối tượng của lớp |
| Field lưu gì? | Trạng thái chung ở cấp lớp | Trạng thái riêng của object |
| Cách đọc/gọi khuyến nghị | `Counter.count()` | `a.increase()` |
| Cần `new Counter(...)` trước khi gọi? | Không, nếu có quyền truy cập phù hợp | Cần một đối tượng/receiver |
| Có `this` trong phương thức? | Không | Có, trỏ đến receiver hiện tại |
| Có truy cập trực tiếp instance field? | Không, nếu không có receiver cụ thể | Có |
| Có truy cập static field? | Có | Có, nhưng thường dùng tên lớp cho rõ |
| Ví dụ | `static int createdCount` | `int value` |

Chữ `static` không đồng nghĩa với **hằng số**. Một `static int` vẫn có thể thay đổi. Chỉ khi thêm `final` mới có ràng buộc không thể gán lại **biến field** sau khi được khởi tạo theo quy tắc Java.

## 3. Instance fields: trạng thái riêng của từng object

~~~java
class Box {
    private int size;

    Box(int size) {
        this.size = size;
    }

    void grow(int amount) {
        size += amount;
    }

    int getSize() {
        return size;
    }
}
~~~

~~~java
Box small = new Box(2);
Box large = new Box(10);
small.grow(3);
System.out.println(small.getSize());
System.out.println(large.getSize());
~~~

**Exact stdout:**

~~~text
5
10
~~~

`size` là **instance field**. Hai đối tượng cùng kiểu `Box` có các giá trị `size` **độc lập**. Gọi `small.grow(3)` cập nhật đối tượng mà `small` trỏ tới, không cập nhật `large`.

Một biến tham chiếu được gán `Box alias = small;` **không tạo Box mới**: `alias` và `small` trỏ cùng object. Khi đó, `alias.grow(1)` làm `small.getSize()` thay đổi. Đây là **chia sẻ đối tượng qua hai tham chiếu**, không phải do field trở thành static.

## 4. Static fields: dữ liệu chung ở cấp lớp

~~~java
class Ticket {
    private static int nextNumber = 1;
    private final int number;

    Ticket() {
        number = nextNumber++;
    }

    int getNumber() {
        return number;
    }

    static int nextAvailableNumber() {
        return nextNumber;
    }
}
~~~

~~~java
Ticket a = new Ticket();
Ticket b = new Ticket();
System.out.println(a.getNumber());
System.out.println(b.getNumber());
System.out.println(Ticket.nextAvailableNumber());
~~~

**Exact stdout**, với một lần chạy mới:

~~~text
1
2
3
~~~

- `nextNumber` là **static field**, giúp các constructor chia sẻ bộ đếm tiếp theo.
- `number` là **instance field** của từng vé; vé `a` nhận `1`, vé `b` nhận `2`.
- `Ticket.nextAvailableNumber()` là **static method** nên gọi bằng tên lớp, không cần tạo thêm `Ticket`.

Một static field bình thường được Java cấp phát và khởi tạo khi lớp được khởi tạo theo quy tắc của JVM; đây không phải “một field nằm trong từng đối tượng”. Trong môi trường có nhiều class loader, cần phân biệt phạm vi định danh lớp; không nên coi mọi static field là **một biến toàn cục duy nhất trong mọi JVM**.

## 5. Gọi phương thức static và instance

~~~java
class MathBox {
    private int value;

    MathBox(int value) {
        this.value = value;
    }

    int doubleValue() {
        return this.value * 2;
    }

    static int square(int n) {
        return n * n;
    }
}
~~~

~~~java
MathBox box = new MathBox(5);
System.out.println(box.doubleValue());   // Instance method.
System.out.println(MathBox.square(5));   // Static method.
~~~

**Exact stdout:**

~~~text
10
25
~~~

**Phương thức instance** có receiver, tức một object cụ thể. Trong `box.doubleValue()`, `this` chính là object mà `box` đang tham chiếu.

**Phương thức static** không có receiver instance ngầm định. `MathBox.square(5)` hoạt động với đối số `5` và không cần tham chiếu tới object `MathBox` nào.

Java cho phép một số cú pháp gọi static method qua biến instance, ví dụ `box.square(5)`; nhưng cách này dễ tạo ấn tượng sai về receiver. **Nên gọi bằng tên lớp: `MathBox.square(5)`.** Nếu biết tên lớp và phương thức static được phép truy cập, không cần `new` object để gọi.

## 6. `this` không dùng trong static context

~~~java
class User {
    private String name;
    private static int count;

    String describe() {
        return this.name;    // Hợp lệ: đang ở instance method.
    }

    static int getCount() {
        return count;        // Hợp lệ: đọc static field.
    }

    // static String wrong() {
    //     return this.name; // LỖI: static context không có this.
    // }
}
~~~

Một static method **không thể trực tiếp** đọc instance field `name` mà không có object cụ thể. Muốn đọc, nhận object làm đối số hoặc tạo một object hợp lệ:

~~~java
static String describeUser(User user) {
    return user.name;  // Truy cập thông qua tham chiếu user.
}
~~~

Trong ví dụ này, `describeUser` thuộc **cùng lớp `User`**, nên có thể truy cập field `private name` của một đối tượng `User`. Nếu đặt trong lớp khác, cần quyền truy cập thích hợp (ví dụ gọi getter).

Ngược lại, một instance method có thể dùng static field/method cùng lớp; dù viết được `count` không kèm tên lớp, cách `User.count` thường làm rõ đây là dữ liệu chung.

**Lưu ý:** `main(String[] args)` cũng là static method, nên không có `this`. Để gọi một instance method từ `main`, hãy tạo hoặc nhận một object trước.

## 7. Static initializer và thời điểm khởi tạo

~~~java
class Settings {
    static int limit = 10;

    static {
        limit += 5;
    }

    static int getLimit() {
        return limit;
    }
}
~~~

Khi lớp `Settings` được khởi tạo, các static fields và static initializer blocks được xử lý theo thứ tự văn bản khai báo. Trong ví dụ trên, sau khi khởi tạo, `Settings.getLimit()` trả **`15`**.

Instance initializer/constructor là cơ chế khác, xảy ra khi tạo từng đối tượng. **Việc import một lớp** không phải lệnh “chạy constructor” hoặc khởi tạo tất cả object của lớp đó.

Static initialization được JVM quản lý (thông thường trước lần sử dụng chủ động cần khởi tạo lớp). Không nên tự suy đoán mọi lớp được khởi tạo chính xác tại thời điểm biên dịch hoặc ngay khi file nguồn được import.

## 8. `static final`: hằng cấp lớp và giới hạn bất biến

~~~java
class Limits {
    static final int MAX_ITEMS = 100;
    static final String UNIT = "items";
}
~~~

Có thể đọc bằng `Limits.MAX_ITEMS` và `Limits.UNIT` mà không cần tạo đối tượng. Những field kiểu primitive/String dùng `static final` thường thích hợp cho các hằng không đổi như giới hạn, nhãn hoặc đơn vị.

Tuy nhiên, `final` chỉ ngăn **gán lại field**, không tự “đóng băng” nội dung object mà field tham chiếu:

~~~java
class Holder {
    static final int[] VALUES = {1, 2};
}
~~~

`Holder.VALUES = new int[]{3};` không hợp lệ, nhưng `Holder.VALUES[0] = 9;` **vẫn hợp lệ** nếu mảng còn có thể truy cập và không có kiểm soát khác. Vì thế, tránh công khai trực tiếp những static arrays/collections mutable khi cần bảo vệ dữ liệu.

**Thành viên interface** còn có một số quy tắc riêng: field khai báo trong interface là `public static final` theo ngầm định; bài này tập trung vào so sánh static/instance trong lớp thông thường.

## 9. Static mutable state: dùng đúng phạm vi

Static field có thể hữu ích để giữ:

- Hằng cấu hình không đổi: `static final`.
- Bộ đếm số object được tạo hoặc số lần một thao tác diễn ra.
- Hàm tiện ích không cần trạng thái instance, ví dụ `Math.max(...)`.
- Dữ liệu chung có chủ đích, có quy tắc cập nhật rõ ràng.

Không phải dữ liệu nào cũng nên để static. Nếu đặt `balance` của tài khoản ngân hàng thành static thì **mọi object tài khoản cùng dùng một số dư**, sai yêu cầu “mỗi tài khoản một số dư riêng”.

Một static field mutable cũng làm các phần khác của chương trình **chia sẻ trạng thái**. Trong chương trình nhiều luồng, `counter++` không phải phép tăng nguyên tử; có thể cần đồng bộ hoặc các công cụ như `AtomicInteger`, tùy bài toán. Trong test, static mutable state còn có thể tồn tại xuyên các test nếu không được reset. Những vấn đề này không phát sinh chỉ vì từ khóa `static` “xấu”; chúng là hệ quả của **trạng thái chung và cách quản lý vòng đời**.

Với ví dụ giáo dục bên dưới, bộ đếm và tổng số dư dùng static để biểu diễn thống kê chung. Đây là bản minh họa **một luồng**, không phải cài đặt giao dịch tài chính an toàn cho production.

## 10. Chương trình Java 17+ độc lập

Lưu đúng tên file **`StaticInstanceMembersDemo.java`**:

~~~java
public final class StaticInstanceMembersDemo {
    private StaticInstanceMembersDemo() {}

    // Lớp lồng static để ví dụ chạy từ một file.
    static final class Account {
        static final String CURRENCY = "VND";   // Hằng cấp lớp.

        private static int createdCount;        // Field chung.
        private static int nextId = 1;          // Field chung.
        private static int totalBalance;        // Tổng số dư chung.

        private final int id;                   // Field instance.
        private final String owner;             // Field instance.
        private int balance;                    // Field instance.

        Account(String owner, int openingBalance) {
            if (owner == null || owner.isBlank()) {
                throw new IllegalArgumentException("owner must not be blank");
            }
            if (openingBalance < 0) {
                throw new IllegalArgumentException("negative opening balance");
            }

            this.owner = owner.strip();
            this.balance = openingBalance;
            this.id = nextId++;
            createdCount++;
            totalBalance += openingBalance;
        }

        void deposit(int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            this.balance += amount;
            totalBalance += amount;
        }

        boolean withdraw(int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            if (amount > this.balance) {
                return false;
            }
            this.balance -= amount;
            totalBalance -= amount;
            return true;
        }

        String summary() {
            return id + ":" + owner + ":" + balance;
        }

        static int getCreatedCount() {
            return createdCount;
        }

        static int getTotalBalance() {
            return totalBalance;
        }
    }

    public static void main(String[] args) {
        System.out.println("currency=" + Account.CURRENCY);
        System.out.println("created initially=" + Account.getCreatedCount());
        System.out.println("total initially=" + Account.getTotalBalance());

        Account an = new Account(" An ", 100);
        Account binh = new Account("Binh", 50);
        System.out.println("created after new=" + Account.getCreatedCount());
        System.out.println("total after new=" + Account.getTotalBalance());
        System.out.println("an initial=" + an.summary());
        System.out.println("binh initial=" + binh.summary());

        an.deposit(20);
        System.out.println("withdraw Binh 10=" + binh.withdraw(10));
        System.out.println("withdraw Binh 100=" + binh.withdraw(100));
        System.out.println("an after operations=" + an.summary());
        System.out.println("binh after operations=" + binh.summary());
        System.out.println("shared total=" + Account.getTotalBalance());

        Account alias = an;
        alias.deposit(5);
        System.out.println("alias same An=" + (alias == an));
        System.out.println("an via alias=" + an.summary());
        System.out.println("created after alias=" + Account.getCreatedCount());
        System.out.println("shared total after alias=" + Account.getTotalBalance());

        try {
            new Account(" ", 30);
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid owner=" + ex.getMessage());
        }
        System.out.println("created final=" + Account.getCreatedCount());
        System.out.println("shared total final=" + Account.getTotalBalance());
    }
}
~~~

Yêu cầu **JDK 17 trở lên**; không cần Maven, Gradle, thư viện ngoài JDK hoặc preview features. File có một public top-level class khớp tên file. `Account` là **static nested class** nhằm gom ví dụ vào một file; từ khóa `static` ở kiểu lồng nghĩa là không cần một instance của lớp bao ngoài để tạo `Account`, khác với các static fields bên trong `Account`.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 StaticInstanceMembersDemo.java
java -cp . StaticInstanceMembersDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\StaticInstanceMembersDemo.java
java -cp . StaticInstanceMembersDemo
~~~

### Exact expected stdout

~~~text
currency=VND
created initially=0
total initially=0
created after new=2
total after new=150
an initial=1:An:100
binh initial=2:Binh:50
withdraw Binh 10=true
withdraw Binh 100=false
an after operations=1:An:120
binh after operations=2:Binh:40
shared total=160
alias same An=true
an via alias=1:An:125
created after alias=2
shared total after alias=165
invalid owner=owner must not be blank
created final=2
shared total final=165
~~~

**Phân tích:**

1. `Account.CURRENCY` là static final constant. `createdCount` và `totalBalance` là static fields, khởi đầu với giá trị mặc định `0`; `nextId` bắt đầu bằng `1`.
2. Hai constructor thành công tạo `an` và `binh`: số tài khoản là `2`, tổng ban đầu `100 + 50 = 150`. Mỗi object có `id`, `owner`, `balance` riêng.
3. `an.deposit(20)` làm số dư An thành `120`. `binh.withdraw(10)` làm số dư Binh thành `40`. Rút `100` bị từ chối nên không đổi dữ liệu. Tổng static lúc này là `120+40=160`.
4. `Account alias = an;` **không tạo tài khoản mới**, mà chia sẻ một object. Nạp `5` qua alias làm An thành `125`, tổng chung thành `165`, nhưng số account được tạo vẫn là `2`.
5. Constructor với `" "` ném ngoại lệ **trước** các dòng cập nhật static, vì thế đếm/tổng không đổi.

## 11. Những lỗi thường gặp

| Sai lầm | Vì sao sai? | Cách sửa |
|---|---|---|
| Nghĩ `static int count` có một bản trong mỗi object | Static field gắn với lớp | Chỉ dùng static cho dữ liệu chung thực sự |
| Nghĩ `int balance` trong lớp tự là static | Thiếu `static` nên là instance field | Phân biệt từng khai báo |
| Gọi `Account.summary()` như static | `summary()` là instance method | Dùng `an.summary()` |
| Gọi `this.balance` trong static method | Static context không có `this` | Truy cập qua object cụ thể |
| Viết `new Account(...)` chỉ để gọi static method | Tạo object không cần thiết | Dùng `Account.getCreatedCount()` |
| Gọi `an.getCreatedCount()` khiến tưởng count thuộc An | Static member được gọi qua class mới rõ | `Account.getCreatedCount()` |
| Nghĩ `static final int[]` là mảng bất biến | `final` chỉ cố định tham chiếu field | Tránh lộ array mutable hoặc trả bản sao |
| Dùng static cho mọi field để tiện truy cập | Trộn trạng thái các object, khó test | Dùng instance field khi dữ liệu thuộc từng object |
| Tin `counter++` static luôn an toàn giữa các luồng | Read-modify-write không nguyên tử | Cần cơ chế thread safety nếu chia sẻ đa luồng |
| Nhầm gán `alias = an` với sao chép account | Đây là sao chép tham chiếu | Tạo object mới nếu cần trạng thái độc lập |

## 12. Bài tập và đáp án

**A — Phân loại thành viên:**

~~~java
class Sensor {
    static int sensorCount;
    int temperature;
    static int getSensorCount() { return sensorCount; }
    int read() { return temperature; }
}
~~~

**Đáp án:** `sensorCount` và `getSensorCount()` là **static**; `temperature` và `read()` là **instance**.

**B — Hai object chia sẻ gì?**

~~~java
class Marker {
    static int created;
    int color;

    Marker(int color) {
        this.color = color;
        created++;
    }
}
~~~

Sau `new Marker(1)` và `new Marker(2)`, có **hai giá trị instance `color` riêng**, còn `Marker.created == 2` là trạng thái static chung.

**C — Static method có được đọc instance field?**  
**Đáp án:** không thể đọc **trực tiếp** khi không có receiver. Có thể nhận object làm đối số rồi đọc qua tham chiếu đó nếu có quyền truy cập.

**D — Hai biến cùng object có giống static không?**  
**Đáp án:** không. `Account b = a;` khiến hai biến trỏ cùng **một** object; đó là aliasing. Một instance field vẫn thuộc đúng object ấy chứ không trở thành static.

**E — `static final int[] VALUES` có cấm `VALUES[0] = 5` không?**  
**Đáp án:** không; `final` cấm gán lại field `VALUES`, nhưng không tự khiến phần tử mảng bất biến.

**F — Tại sao `createdCount` không nên là instance field nếu mục đích là đếm mọi account được tạo?**  
**Đáp án:** mỗi object sẽ có bản đếm riêng. Static field biểu diễn một bộ đếm dùng chung; cần cập nhật nhất quán và lưu ý vấn đề đa luồng nếu có.

## 13. Checklist và kết luận

- [ ] Nhận diện trường/phương thức nào có từ khóa `static`.
- [ ] Phân biệt field **chung ở cấp lớp** và field **riêng của object**.
- [ ] Biết gọi static method bằng tên lớp, instance method qua receiver.
- [ ] Không dùng `this` trong static context.
- [ ] Hiểu instance method có thể truy cập cả instance lẫn static member.
- [ ] Hiểu constructor tạo object riêng nhưng có thể cập nhật static counter chung.
- [ ] Phân biệt `static` với `final`; biết `static final` không làm object mutable thành bất biến.
- [ ] Biết static initializer liên quan đến khởi tạo lớp, không phải tạo từng object.
- [ ] Hiểu aliasing là chia sẻ object, không phải thành viên static.
- [ ] Biết rủi ro static mutable state trong test/đa luồng.
- [ ] Biên dịch và chạy `StaticInstanceMembersDemo.java` với Java 17+, đối chiếu exact stdout.

**Kết luận:** **Thành viên static thuộc về lớp**, dùng cho dữ liệu và hành vi chung không gắn với một instance cụ thể. **Thành viên đối tượng thuộc về từng instance**, dùng để biểu diễn trạng thái và hành vi riêng. Chọn đúng phạm vi thành viên giúp tránh trộn dữ liệu các object, làm rõ nơi được phép dùng `this` và kiểm soát những trạng thái chia sẻ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classes-and-objects-007">Nhận diện public, private và truy cập trong gói</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện `public`, `private` và truy cập trong gói trong Java

> **Java core — language-basics / Lớp và đối tượng**  
> **Môi trường:** JDK 17+, không dùng thư viện ngoài.  
> **Mục tiêu:** Đọc được modifier truy cập của lớp, constructor, trường và phương thức; phân biệt `public`, `private` và **package-private** (không viết modifier); sử dụng API công khai để bảo vệ trạng thái; biết khi nào mã cùng hoặc khác package được truy cập thành viên.

## 1. Ba mức truy cập cần nhận diện đầu tiên

Trong Java, *access modifier* quy định vị trí mã được phép sử dụng một lớp hoặc thành viên. Với các thành viên thông thường:

| Khai báo | Trong lớp khai báo | Lớp khác cùng package | Lớp không liên quan khác package |
| --- | --- | --- | --- |
| `public` | Có | Có | Có, nếu lớp khai báo cũng truy cập được |
| `private` | Có | Không | Không |
| **Không có modifier** (*package-private*) | Có | Có | Không |

Ví dụ nhận diện ba mức:

~~~java
class Sample {
    public int shown = 10;
    private int hidden = 20;
    int sharedWithinPackage = 30; // KHÔNG có access modifier.
}
~~~

- `public` cho phép sử dụng từ mã có quyền nhìn thấy lớp/kiểu chứa thành viên.
- `private` giữ thành viên trong phạm vi truy cập của lớp khai báo và những cấu trúc lồng nhau được ngôn ngữ cho phép; **một top-level class khác** dù cùng package không được đọc trực tiếp.
- Bỏ trống modifier được gọi là **package-private** hoặc *package access*: các lớp cùng package có thể truy cập, kể cả nằm ở file `.java` khác nhau.
- Không tồn tại từ khóa Java `package-private` hoặc `default` để viết trước khai báo; chỉ **bỏ trống** modifier.

Java còn có `protected`: mã cùng package được truy cập và lớp con ở package khác có quyền theo các quy tắc kế thừa đặc biệt. Bài này chỉ tập trung nhận diện ba trường hợp cơ bản; **không** hiểu `protected` đơn thuần là `public` cho mọi subclass trong mọi tình huống.

## 2. Package là gì, và không đồng nghĩa cùng một file

Một file Java có thể mở đầu bằng khai báo:

~~~java
package school.model;
~~~

Các lớp có cùng tên package như `school.model` được xem là cùng package dù nằm ở **nhiều file mã nguồn**. Ngược lại, hai lớp khác package không được phép dùng member package-private của nhau, dù chúng nằm gần nhau trong cấu trúc thư mục.

Java dùng dấu `.` để tách các phần trong tên package, nhưng tính cùng package dựa vào **tên package đầy đủ**, không phải tiền tố tên:

- `school.model` và `school.model`: cùng package.
- `school.model` và `school.app`: khác package.
- `school.model` và `school.model.extra`: **cũng khác package**; package con không tự kế thừa quyền package-private của package cha.

Một lớp không khai báo `package` thuộc **unnamed package** (thường gọi là *default package*) trong ví dụ nhập môn. Khi viết ứng dụng thật, nên dùng package có tên để tổ chức mã và tránh hạn chế khi tích hợp.

**Không trộn lẫn:** Cùng **project**, cùng **thư mục**, hoặc cùng **package cha** không mặc nhiên đồng nghĩa có quyền truy cập package-private. `import` cũng chỉ hỗ trợ tham chiếu theo tên; nó **không mở thêm quyền truy cập**.

## 3. Modifier trên lớp và trên thành viên là hai tầng khác nhau

Với **top-level class** (lớp không lồng trong lớp khác), hai mức cần nhớ:

~~~java
public class PublicType {
    // Được nhìn thấy bên ngoài package khi môi trường cho phép.
}

class PackageType {
    // Chỉ truy cập trực tiếp từ cùng package.
}
~~~

Khi viết thành file riêng, lớp top-level `public` phải nằm trong file có tên phù hợp, ví dụ `PublicType.java`. Một file nguồn thông thường không thể có hai lớp top-level `public` cùng được khai báo và biên dịch hợp lệ. Không dùng `private class Hidden { ... }` cho **top-level class**: modifier `private` không hợp lệ ở đây. Với **lớp lồng nhau** (`nested class`), `private` có thể hợp lệ.

Một member `public` nằm trong lớp top-level package-private **không biến lớp chứa nó thành public**:

~~~java
class PackageType {
    public void run() {
        System.out.println("running");
    }
}
~~~

Mã ở package khác vẫn không thể đặt tên và sử dụng `PackageType` trực tiếp chỉ vì `run()` được khai báo public. Muốn cung cấp API xuyên package, lớp/kiểu lộ ra cũng phải đủ mức truy cập, ngoài những điều kiện của module và ngữ cảnh biên dịch.

## 4. `private` giúp đóng gói trạng thái

Thông lệ nhập môn: giữ các trường có thể thay đổi ở `private`, rồi cung cấp phương thức công khai có kiểm tra điều kiện khi cần:

~~~java
class Wallet {
    private int balance;

    public Wallet(int initial) {
        if (initial < 0) {
            throw new IllegalArgumentException("initial must be non-negative");
        }
        this.balance = initial;
    }

    public int balance() {
        return balance;
    }

    public void deposit(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        balance += amount;
    }

    private boolean hasEnough(int amount) {
        return balance >= amount;
    }

    public boolean canPay(int amount) {
        return amount >= 0 && hasEnough(amount);
    }
}
~~~

`balance` và `hasEnough` là các thành viên private; mã ở lớp khác không thể gọi `wallet.hasEnough(...)` hay đọc `wallet.balance` trực tiếp. Phương thức `balance()`, `deposit(...)` và `canPay(...)` tạo API công khai và có thể gọi qua tham chiếu tới đối tượng `Wallet` tại nơi có quyền nhìn thấy `Wallet`.

**Lưu ý:** `private` là kiểm soát truy cập ở mức ngôn ngữ, không phải cơ chế mã hóa dữ liệu hoặc ranh giới bảo mật giữa các tiến trình. Đừng cho rằng một field private tự bảo vệ được mật khẩu hay dữ liệu nhạy cảm trước mọi hình thức quan sát.

## 5. Truy cập package-private: dùng có chủ đích

Nếu không viết modifier trên **trường**, **phương thức** hoặc **constructor**, mặc định đó là package-private:

~~~java
class Task {
    String phase = "NEW";             // Field package-private.

    Task() {                          // Constructor package-private.
    }

    void markStarted() {              // Method package-private.
        phase = "STARTED";
    }

    public String status() {          // Method public.
        return phase;
    }

    private void resetInternally() {  // Method private.
        phase = "NEW";
    }
}
~~~

Một lớp **khác trong cùng package** có thể gọi `new Task()`, đọc `task.phase`, gọi `task.markStarted()` và `task.status()`, nhưng không gọi `task.resetInternally()` vì private.

Package-private thường phù hợp cho mã **hỗ trợ nội bộ trong cùng package**. Tuy nhiên, gán trực tiếp trường package-private có thể bỏ qua kiểm tra bất biến trạng thái; không nên mở trường chỉ để “tiện” nếu getter/phương thức nghiệp vụ sẽ rõ ràng hơn.

Khai báo access modifier cho constructor độc lập với các phương thức khác: `public class Type` có thể có constructor private hoặc package-private. Vì vậy nhìn thấy **lớp public** chưa chắc đã gọi được `new Type()` từ mọi package.

## 6. Ví dụ một file: phân biệt public, private và trong gói

Chương trình sau cố ý chứa một lớp top-level `public` và hai lớp top-level **không ghi modifier**, nên cả ba lớp ở **cùng một package**. Nó minh họa lớp khác gọi được method/field package-private nhưng không đọc được field private.

Tạo file **AccessLevelsDemo.java**:

~~~java
public final class AccessLevelsDemo {
    public static void main(String[] args) {
        Account account = new Account("Lan", 100, "A7");
        System.out.println("initial=" + account.summary());
        System.out.println("publicName=" + account.owner());
        System.out.println("packageField=" + account.category);
        System.out.println("packageMethod=" + account.internalCode());
        System.out.println("readerCode="
                + SamePackageReader.readCode(account));

        account.deposit(25);
        System.out.println("afterDeposit=" + account.balance());
        account.adjustInsidePackage(5);
        System.out.println("afterPackageAdjustment=" + account.balance());
        System.out.println("privateBridge="
                + account.privateInfoThroughClass());

        Account other = new Account("Bao", 40, "A7");
        System.out.println("sameInternalId="
                + account.sameInternalId(other));
        System.out.println("sameReference=" + (account == other));

        account.category = "VIP"; // Được phép vì cùng package.
        System.out.println("afterPackageField=" + account.summary());
        System.out.println("readerSummary="
                + SamePackageReader.readSummary(account));

        // KHÔNG BIÊN DỊCH nếu bỏ comment:
        // System.out.println(account.internalId); // private.
        // account.privateTag();                    // private.
    }
}

// Top-level class package-private: không ghi public/private.
final class Account {
    private final String name;
    private int balance;
    private final String internalId;
    String category = "BASIC"; // package-private field.

    Account(String name, int balance, String internalId) {
        this.name = name;
        this.balance = balance;
        this.internalId = internalId;
    }

    public String owner() {
        return name;
    }

    public int balance() {
        return balance;
    }

    public void deposit(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount > 0");
        }
        balance += amount;
    }

    String internalCode() { // package-private method.
        return name.substring(0, 1) + "-" + category;
    }

    void adjustInsidePackage(int delta) { // package-private.
        balance += delta;
    }

    private String privateTag() {
        return "tag:" + internalId;
    }

    String privateInfoThroughClass() {
        return privateTag(); // Chính lớp này được gọi private.
    }

    public boolean sameInternalId(Account other) {
        // Trong lớp Account có thể đọc private field
        // của một đối tượng Account KHÁC.
        return internalId.equals(other.internalId);
    }

    public String summary() {
        return name + ":" + balance + ":" + category;
    }
}

// Lớp thứ ba cùng package, truy cập được thành viên package-private.
final class SamePackageReader {
    static String readCode(Account account) {
        return account.internalCode();
    }

    static String readSummary(Account account) {
        return account.summary();
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 AccessLevelsDemo.java
java AccessLevelsDemo
~~~

**Expected stdout chính xác:**

~~~text
initial=Lan:100:BASIC
publicName=Lan
packageField=BASIC
packageMethod=L-BASIC
readerCode=L-BASIC
afterDeposit=125
afterPackageAdjustment=130
privateBridge=tag:A7
sameInternalId=true
sameReference=false
afterPackageField=Lan:130:VIP
readerSummary=Lan:130:VIP
~~~

**Quan sát:** `SamePackageReader` gọi được `Account.internalCode()` vì các top-level classes nằm trong cùng package, dù là lớp khác. `internalId` và `privateTag()` vẫn không thể truy cập trực tiếp từ `AccessLevelsDemo` vì `private`. Bên trong chính `Account`, phương thức `sameInternalId` được đọc private field của **đối tượng `Account` khác**; private giới hạn mã truy cập theo lớp, **không** chỉ cho riêng `this`.

## 7. Qua hai package: điều gì còn truy cập được?

Khi đi sang package khác, điều kiện truy cập trở nên rõ hơn. Có thể thử hai file độc lập như sau.

**File `src/school/model/Student.java`:**

~~~java
package school.model;

public final class Student {
    private final int score = 8;
    String note = "class-only"; // package-private.

    public int score() {
        return score;
    }
}
~~~

**File `src/school/app/Main.java`:**

~~~java
package school.app;

import school.model.Student;

public final class Main {
    public static void main(String[] args) {
        Student student = new Student();
        System.out.println("outside=" + student.score());

        // KHÔNG BIÊN DỊCH nếu bỏ comment:
        // System.out.println(student.score); // private.
        // System.out.println(student.note);  // package-private.
    }
}
~~~

Biên dịch và chạy tại thư mục chứa `src`:

~~~bash
javac -encoding UTF-8 -d out src/school/model/Student.java src/school/app/Main.java
java -cp out school.app.Main
~~~

**Expected stdout:**

~~~text
outside=8
~~~

`import school.model.Student;` làm tên lớp dễ dùng hơn nhưng không cho phép truy cập `note` hoặc `score` trực tiếp. Gọi `student.score()` hợp lệ vì **cả lớp `Student` và phương thức `score()` đều public**. Nếu đổi `public final class Student` thành `final class Student`, class trở thành package-private và `Main` ở package khác không thể sử dụng kiểu này trực tiếp.

## 8. Phân biệt lỗi truy cập với các lỗi khác

| Tình huống | Nguyên nhân chính | Cách xử lý |
| --- | --- | --- |
| `account.internalId` từ top-level class khác | Field `private` | Dùng API hợp lệ hoặc đưa logic vào lớp sở hữu |
| `student.note` từ `school.app` | Field package-private, khác package | Cân nhắc public API; không lách quyền bằng `import` |
| `new HiddenType()` từ package khác | Top-level class package-private | Thiết kế kiểu public cần thiết hoặc giữ mã trong package |
| `new PublicType()` không hợp lệ vì constructor ẩn | Class public nhưng constructor không public | Dùng factory hợp lệ hoặc điều chỉnh API |
| `obj.method()` không truy cập được | Method private/package-private hoặc lớp khai báo không nhìn thấy | Kiểm tra cả class và method |
| `obj == null` rồi gọi instance method | `NullPointerException` lúc chạy; không phải access modifier | Tạo đối tượng/kiểm tra null |
| `private class X` ở cấp top-level | Modifier private không hợp lệ cho top-level class | Bỏ modifier hoặc biến thành nested class |
| `cannot find symbol` tên class | Có thể thiếu import, classpath hoặc tên | Kiểm tra tên/package trước khi kết luận lỗi access |

Một API nhỏ, rõ ràng thường dùng **field private** và **method public cần thiết**, còn package-private dành cho thành viên thực sự chỉ sử dụng nội bộ cùng package. Tránh đặt mọi field public chỉ để làm ví dụ chạy nhanh.

### Bài tập tự kiểm tra

1. Trong cùng package, tạo hai file `Book.java` và `BookDemo.java`. Cho `Book` có field package-private và field private; thử truy cập cả hai từ `BookDemo`.
2. Tách `BookDemo` sang package khác, thay `Book` thành `public class Book`. Những truy cập nào không còn biên dịch?
3. Định nghĩa class top-level không modifier nhưng có một phương thức public. Vì sao mã khác package vẫn không thể dùng class đó trực tiếp?
4. Viết constructor package-private và một static factory public trong lớp public. Khi nào mã khác package phải dùng factory?
5. Giải thích vì sao phương thức của `Account` có thể đọc `other.internalId` trong khi `SamePackageReader` không thể đọc cùng field.

### Ghi nhớ

**`public` mở truy cập theo API; `private` giữ thành viên trong phạm vi lớp/nested structure phù hợp; bỏ access modifier nghĩa là package-private — chỉ cùng package.** Quyền truy cập cần xét **cả kiểu khai báo và thành viên**. Cùng package không bắt buộc cùng file; package con vẫn là package khác; `import` không vượt qua access control. Hãy ưu tiên đóng gói trường private và cung cấp phương thức công khai có chủ đích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classes-and-objects-008">Giới thiệu enum, record và interface ở mức đọc hiểu cú pháp</a>

<details>
<summary>Xem chi tiết</summary>

# Giới thiệu `enum`, `record` và `interface` — đọc hiểu cú pháp Java 17+

## 1. Nhận diện nhanh ba kiểu khai báo

Java không chỉ có `class`. Khi đọc mã nguồn, bạn thường gặp:

~~~java
enum Priority { LOW, MEDIUM, HIGH }

record Point(int x, int y) {}

interface Printable {
    String label();
}
~~~

| Từ khóa | Mục đích chính | Đọc hiểu nhanh |
|---|---|---|
| `enum` | Biểu diễn tập giá trị cố định có tên | `Priority.MEDIUM` là một hằng kiểu `Priority` |
| `record` | Khai báo kiểu dữ liệu mang các thành phần (*components*) | `new Point(2, 3)` có các accessor `x()`, `y()` |
| `interface` | Định nghĩa hợp đồng mà các kiểu khác có thể thực hiện | `class/record X implements Printable` cam kết cung cấp `label()` |

Cả ba tạo ra **kiểu tham chiếu**, không phải kiểu nguyên thủy như `int`. Bài này tập trung **đọc đúng cú pháp và sử dụng cơ bản** trên Java 17, chưa đi sâu thiết kế OOP.

## 2. `enum`: tập hằng có tên và kiểu rõ ràng

~~~java
enum Priority {
    LOW, MEDIUM, HIGH
}

Priority current = Priority.MEDIUM;
System.out.println(current);
~~~

**Exact stdout:**

~~~text
MEDIUM
~~~

- `Priority` là tên kiểu `enum`, còn `LOW`, `MEDIUM`, `HIGH` là các hằng của kiểu đó.
- `Priority.MEDIUM` không phải chuỗi `"MEDIUM"`; nó là một giá trị kiểu `Priority`.
- `Priority.values()` trả mảng chứa các hằng theo thứ tự khai báo; `Priority.valueOf("MEDIUM")` tìm đúng hằng có tên này.
- `current.name()` trả tên hằng; `current.ordinal()` là vị trí bắt đầu từ 0, nhưng **không nên lưu ordinal làm mã nghiệp vụ bền vững** vì đổi thứ tự hằng sẽ đổi số.
- `enum` có thể có field, method và constructor, nhưng ở mức cơ bản có thể chỉ cần liệt kê các hằng.

~~~java
String action = switch (current) {
    case LOW -> "read";
    case MEDIUM -> "review";
    case HIGH -> "urgent";
};
System.out.println(action);
~~~

**Exact stdout:**

~~~text
review
~~~

Đây là **switch expression** hợp lệ trong Java 17: các nhánh bao phủ mọi hằng hiện có của `Priority`. Không thể viết `new Priority()` để tùy ý tạo thêm hằng enum.

## 3. `record`: cách khai báo gọn một kiểu dữ liệu

~~~java
record Point(int x, int y) {}

Point p = new Point(2, 3);
System.out.println(p.x());
System.out.println(p.y());
System.out.println(p);
~~~

**Exact stdout:**

~~~text
2
3
Point[x=2, y=3]
~~~

`record Point(int x, int y)` khai báo hai **record components** `x` và `y`. Java tự cung cấp nhiều thành phần thường phải viết thủ công:

- Constructor chuẩn theo các components, ví dụ `new Point(2, 3)`.
- Các accessor tên `x()` và `y()`, **không mặc định là** `getX()`/`getY()`.
- `equals`, `hashCode`, `toString` dựa trên các components.
- Các trường component là `private final` trong record, không được gán lại sau khi khởi tạo.

Record vẫn có thể có method riêng và **kiểm tra dữ liệu lúc tạo** bằng compact constructor:

~~~java
record Lesson(String title, int minutes) {
    Lesson {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("blank title");
        }
        if (minutes < 0) {
            throw new IllegalArgumentException("negative minutes");
        }
        title = title.strip();
    }
}
~~~

`Lesson { ... }` là **compact canonical constructor**: viết logic kiểm tra/chuẩn hóa, không phải khai báo một method tên `Lesson`. Sau phần thân này, Java gán các giá trị tham số component đã xử lý vào các trường tương ứng.

**Quan trọng:** record không đồng nghĩa *deep immutable*. Nếu component là một mảng hoặc object mutable, bản ghi vẫn có thể giữ tham chiếu tới dữ liệu có thể bị sửa. Record là `final` (không thể được kế thừa bằng `extends`); nó có thể `implements interface`.

## 4. `interface`: hợp đồng hành vi

~~~java
interface Describable {
    String label();

    default String summary() {
        return "Named:" + label();
    }

    static String kind() {
        return "describable";
    }
}
~~~

Đọc cú pháp:

- `String label();` khai báo một phương thức trừu tượng (implicit `public abstract`); không có thân ở đây.
- `default String summary() { ... }` có **thân mặc định** để các lớp thực hiện interface có thể sử dụng hoặc override.
- `static String kind() { ... }` là phương thức static của interface, gọi bằng `Describable.kind()`.
- Một class hoặc record dùng `implements Describable` và phải cung cấp cách cài đặt các phương thức abstract chưa được thực hiện.
- Phương thức cài đặt `label()` từ interface phải là `public`; không được giảm quyền truy cập thành package-private hay `private`.

~~~java
record Lesson(String title, int minutes) implements Describable {
    @Override
    public String label() {
        return title;
    }
}
~~~

Ví dụ trên chỉ minh họa hình dạng khai báo; chương trình hoàn chỉnh ở mục 6 có thêm compact constructor để xác thực dữ liệu.

**Không viết** `new Describable()`: interface không phải class cụ thể để khởi tạo trực tiếp. Nhưng biến kiểu interface có thể **tham chiếu tới object của lớp/record cài đặt nó**:

~~~java
Describable item = new Lesson("Java", 30);
System.out.println(item.label());
System.out.println(item.summary());
~~~

**Exact stdout:**

~~~text
Java
Named:Java
~~~

Java cũng hỗ trợ class `implements` nhiều interface, và interface `extends` một hoặc nhiều interface khác. Đừng nhầm từ khóa `implements` (thực hiện hợp đồng) với `extends` (quan hệ kế thừa được phép giữa các kiểu).

## 5. Đặt ba kiểu cạnh nhau

| Điểm khác biệt | `enum` | `record` | `interface` |
|---|---|---|---|
| Thường dùng để | Trạng thái/tùy chọn hữu hạn | Gói dữ liệu có tên trường | Hợp đồng hành vi |
| Tạo giá trị | Dùng hằng khai báo sẵn | Thường `new RecordName(...)` | Không tạo object trực tiếp |
| Ví dụ truy cập | `Priority.HIGH` | `lesson.title()` | `item.label()` |
| Phương thức có thân | Có thể khai báo | Có thể khai báo | `default`, `static` (và helper `private` trên Java hiện đại) |
| Có thể thực hiện interface | Có | Có | Interface khác có thể mở rộng nó |

Khi đọc chữ ký `record Lesson(...) implements Describable`, hiểu theo thứ tự: định nghĩa **kiểu record** `Lesson`, khai báo components trong ngoặc, rồi xác nhận nó **cung cấp hành vi** theo `Describable`.

Trong Java 17, cả ba cú pháp trên đều **ổn định, không cần bật preview features**. Record trở thành tính năng chính thức từ Java 16; enum và interface đã có từ các phiên bản Java trước đó.

## 6. Chương trình Java 17+ hoàn chỉnh

Lưu đúng file **`EnumRecordInterfaceDemo.java`**:

~~~java
public final class EnumRecordInterfaceDemo {
    private EnumRecordInterfaceDemo() {}

    enum Priority {
        LOW, MEDIUM, HIGH
    }

    interface Describable {
        String label();

        default String summary() {
            return "Named:" + label();
        }

        static String kind() {
            return "describable";
        }
    }

    record Lesson(String title, int minutes) implements Describable {
        Lesson {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("blank title");
            }
            if (minutes < 0) {
                throw new IllegalArgumentException("negative minutes");
            }
            title = title.strip();
        }

        @Override
        public String label() {
            return title;
        }
    }

    static final class Folder implements Describable {
        private final String name;

        Folder(String name) {
            this.name = name;
        }

        @Override
        public String label() {
            return name;
        }
    }

    public static void main(String[] args) {
        Priority priority = Priority.MEDIUM;
        System.out.println("enum value=" + priority);
        System.out.println("enum name=" + priority.name());
        System.out.println("enum count=" + Priority.values().length);
        System.out.println("enum lookup=" + (Priority.valueOf("MEDIUM") == priority));

        String action = switch (priority) {
            case LOW -> "read";
            case MEDIUM -> "review";
            case HIGH -> "urgent";
        };
        System.out.println("enum switch=" + action);

        Lesson first = new Lesson(" Java ", 30);
        Lesson second = new Lesson("Java", 30);
        System.out.println("record=" + first);
        System.out.println("record title=" + first.title());
        System.out.println("record minutes=" + first.minutes());
        System.out.println("record equals=" + first.equals(second));
        System.out.println("record identity=" + (first == second));

        Describable item = first;
        System.out.println("interface label=" + item.label());
        System.out.println("interface default=" + item.summary());
        System.out.println("interface static=" + Describable.kind());

        Describable folder = new Folder("Basics");
        System.out.println("class implements=" + folder.summary());

        try {
            new Lesson(" ", 5);
        } catch (IllegalArgumentException ex) {
            System.out.println("record invalid=" + ex.getMessage());
        }
    }
}
~~~

Yêu cầu **JDK 17 trở lên**, không cần Maven/Gradle, thư viện bên ngoài hay `--enable-preview`.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 EnumRecordInterfaceDemo.java
java -cp . EnumRecordInterfaceDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\EnumRecordInterfaceDemo.java
java -cp . EnumRecordInterfaceDemo
~~~

### Exact expected stdout

~~~text
enum value=MEDIUM
enum name=MEDIUM
enum count=3
enum lookup=true
enum switch=review
record=Lesson[title=Java, minutes=30]
record title=Java
record minutes=30
record equals=true
record identity=false
interface label=Java
interface default=Named:Java
interface static=describable
class implements=Named:Basics
record invalid=blank title
~~~

Đọc luồng kết quả: biến `priority` giữ một hằng enum; `Lesson` tạo hai object có cùng nội dung (`equals=true`) nhưng khác tham chiếu (`==false`); object record có thể được gán cho biến interface `Describable` vì đã `implements`; lớp `Folder` cũng có thể thực hiện cùng interface.

## 7. Lỗi đọc cú pháp thường gặp

| Nhầm lẫn | Cách hiểu đúng |
|---|---|
| Coi `Priority.HIGH` là `String` | Nó là hằng kiểu `Priority`; dùng `.name()` nếu cần tên |
| Dùng `new Priority()` | Không thể tự tạo enum constant bằng `new` |
| Dùng `new Describable()` | Interface không được khởi tạo trực tiếp |
| Gọi `lesson.getTitle()` theo mặc định | Record component có accessor `title()` |
| Nghĩ record luôn deep immutable | Component tham chiếu mutable vẫn có thể bị thay đổi |
| Gọi `Describable.summary()` như static | `summary()` là instance default method; chỉ `kind()` mới static |
| Cài đặt `label()` không `public` | Không được giảm quyền truy cập của phương thức interface |
| Dùng `extends` thay `implements` khi record cài đặt interface | Viết `record Lesson(...) implements Describable` |
| Coi `record` là class có thể kế thừa | Record ngầm `final`, không được lớp khác `extends` |
| Lưu `enum.ordinal()` như mã cố định | Thứ tự hằng thay đổi sẽ làm ordinal đổi |

## 8. Bài tập có đáp án

**A.** Với `enum Traffic { RED, YELLOW, GREEN }`, biến `Traffic light = Traffic.RED;` giữ kiểu gì? **`Traffic`**; `RED` là một hằng của enum, không phải chuỗi.

**B.** Với `record Size(int width, int height) {}`, tạo `new Size(2, 5)` rồi đọc chiều rộng thế nào? **`size.width()`** với biến `Size size`; Java không tự tạo `getWidth()`.

**C.** Interface khai báo `void run();`, class thực hiện nó phải viết phương thức với quyền truy cập nào? **`public void run() { ... }`** (nếu class cụ thể cung cấp cài đặt trực tiếp).

**D.** Hai record cùng giá trị components có thể `equals` true nhưng `==` false không? **Có**: `equals` dựa vào nội dung components, `==` so object identity.

**E.** `record Lesson(String name) implements Describable` có thể có thêm method riêng không? **Có**; record vẫn là kiểu lớp đặc biệt có thể khai báo method và thực hiện interface.

## 9. Checklist và kết luận

- [ ] Nhận diện `enum`, danh sách hằng và cách viết `Type.CONSTANT`.
- [ ] Hiểu `record X(T component)` khai báo dữ liệu và accessor `component()`.
- [ ] Biết `equals` và `==` của record mang ý nghĩa khác nhau.
- [ ] Hiểu interface khai báo hợp đồng; `implements` là thực hiện hợp đồng.
- [ ] Phân biệt abstract, default và static method trong interface.
- [ ] Biết record có thể `implements interface`.
- [ ] Không nhầm record là deep immutable hay enum là `String`.
- [ ] Biên dịch, chạy `EnumRecordInterfaceDemo.java` bằng JDK 17+ và đối chiếu exact stdout.

**Kết luận:** `enum` phù hợp với **tập lựa chọn có tên**, `record` thuận tiện để biểu diễn **dữ liệu có cấu trúc**, còn `interface` biểu diễn **hợp đồng hành vi**. Đọc đúng ba cú pháp giúp hiểu cách các kiểu Java kết hợp với nhau mà chưa cần học sâu các kỹ thuật OOP nâng cao.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classes-and-objects-009">Từ chương trình chỉ có main tới chương trình có nhiều lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Từ chương trình chỉ có `main` tới chương trình có nhiều lớp — Java 17+

> **Java core — language-basics / Lớp và đối tượng**  
> **Yêu cầu:** JDK 17+, không dùng thư viện ngoài hoặc công cụ build.  
> **Mục tiêu:** Biết khi nào chương trình một `main` bắt đầu khó bảo trì; chuyển dữ liệu và hành vi liên quan vào lớp riêng; tạo đối tượng bằng `new`, gọi phương thức qua dấu chấm, biên dịch nhiều file `.java` và chạy đúng lớp có `main`.

## 1. Vì sao ban đầu chỉ cần một `main`?

Một chương trình Java rất nhỏ có thể đặt tất cả công việc trong `main`:

~~~java
public final class Hello {
    public static void main(String[] args) {
        String name = "Lan";
        System.out.println("Xin chào " + name);
    }
}
~~~

`main(String[] args)` là điểm bắt đầu quen thuộc khi chạy một lớp từ lệnh `java Hello`. Nó là phương thức `static`, vì Java launcher không cần tạo đối tượng `Hello` để gọi vào điểm bắt đầu này.

Khi ứng dụng chỉ thực hiện vài lệnh tuần tự, một `main` ngắn, rõ ràng là hợp lý. Nhưng nếu `main` vừa lưu dữ liệu, vừa kiểm tra hợp lệ, vừa tính toán, vừa in ra màn hình, các thay đổi sau này sẽ dễ làm ảnh hưởng nhiều phần khác nhau.

Bài này dùng cùng một yêu cầu đơn giản cho hai phiên bản:

- Có hai học viên: **Lan** đạt 7 điểm, **Minh** đạt 9 điểm.
- Cộng thêm 1 điểm cho Lan.
- In điểm từng người và điểm trung bình của lớp.

Kết quả của cả hai phiên bản phải **giống nhau**; mục tiêu của việc tách lớp là làm cấu trúc tốt hơn, không thay đổi nghiệp vụ.

## 2. Phiên bản đầu: tất cả logic nằm trong `main`

Tạo file **MainOnly.java**:

~~~java
public final class MainOnly {
    public static void main(String[] args) {
        String[] names = {"Lan", "Minh"};
        int[] scores = {7, 9};

        // Thay đổi điểm Lan.
        scores[0] += 1;

        // In điểm của từng học viên.
        for (int i = 0; i < names.length; i++) {
            System.out.println(names[i] + "=" + scores[i]);
        }

        // Tính và in điểm trung bình.
        int total = 0;
        for (int score : scores) {
            total += score;
        }
        System.out.println("average=" + (double) total / scores.length);
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 MainOnly.java
java MainOnly
~~~

**Expected stdout:**

~~~text
Lan=8
Minh=9
average=8.5
~~~

Cú pháp `(double) total / scores.length` bảo đảm phép chia ra kết quả thập phân; nếu dùng `int / int`, phép chia nguyên sẽ loại bỏ phần thập phân.

Phiên bản một lớp **không sai**. Nhưng khi cần thêm học viên, kiểm tra điểm từ 0 đến 10, lưu thông tin khác hoặc tái sử dụng cách tính trung bình ở nhiều nơi, những dòng mã này bắt đầu có vấn đề:

| Phần hiện tại | Vấn đề khi mở rộng |
| --- | --- |
| `names[i]` và `scores[i]` | Hai mảng song song phải luôn cùng độ dài, cùng vị trí |
| `scores[0] += 1` | Bất kỳ đoạn mã nào cũng có thể gán điểm ngoài giới hạn |
| Vòng lặp in điểm | Logic trình bày và xử lý dữ liệu nằm lẫn trong `main` |
| Phép tính trung bình | Dễ phải sao chép khi màn hình/chức năng khác cũng cần |
| Chỉ số `0` gắn với Lan | Thay thứ tự học viên có thể khiến thao tác đổi nhầm người |

Hướng cải thiện: gom **tên + điểm + quy tắc thay đổi điểm** thành một đối tượng `Student`; gom **danh sách học viên + tính trung bình + in danh sách** thành một đối tượng `Classroom`; giữ `main` ở lớp điều phối `SchoolApp`.

## 3. Nhận diện trách nhiệm của từng lớp

| Lớp | Trạng thái / vai trò | Hành vi chính |
| --- | --- | --- |
| `Student` | Tên và điểm của **một học viên** | Tạo học viên, đọc tên/điểm, tăng điểm có kiểm tra |
| `Classroom` | Các tham chiếu học viên trong **một lớp học** | In danh sách và tính điểm trung bình |
| `SchoolApp` | Điểm bắt đầu chạy chương trình | Tạo đối tượng, phối hợp lời gọi, in kết quả tổng hợp |

Cấu trúc thư mục đơn giản, mọi file nằm cùng một thư mục và **không khai báo `package`** trong ví dụ:

~~~text
study/
├── MainOnly.java      # Phiên bản một main để so sánh.
├── Student.java       # Đối tượng học viên.
├── Classroom.java     # Danh sách học viên và phép tính.
└── SchoolApp.java     # Main điều phối phiên bản nhiều lớp.
~~~

Có thể giữ `MainOnly.java` để học đối chiếu; khi chạy `SchoolApp`, JVM không tự chạy `MainOnly.main`.

Không phải cứ có nhiều lớp là chương trình “hướng đối tượng tốt”. Chỉ tách lớp khi lớp có **trách nhiệm rõ ràng**; tránh tạo những lớp chỉ chuyển tiếp một dòng lệnh mà không giúp mã dễ hiểu.

## 4. Tách một học viên thành `Student.java`

Tạo file **Student.java**:

~~~java
public final class Student {
    private final String name;
    private int score;

    public Student(String name, int score) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (score < 0 || score > 10) {
            throw new IllegalArgumentException("score must be 0..10");
        }
        this.name = name;
        this.score = score;
    }

    public String name() {
        return name;
    }

    public int score() {
        return score;
    }

    public void improve(int points) {
        if (points < 0 || points > 10 - score) {
            throw new IllegalArgumentException("invalid improvement");
        }
        score += points;
    }
}
~~~

Giải thích:

1. `class Student` tạo một **kiểu tham chiếu mới** tên `Student`. Mỗi lần `new Student("Lan", 7)` tạo một **đối tượng học viên** mới.
2. `private final String name` và `private int score` là **trường instance**. Mỗi đối tượng giữ giá trị riêng; mã lớp khác không truy cập trường `private` trực tiếp.
3. Constructor `Student(String name, int score)` kiểm tra đầu vào rồi khởi tạo trường qua `this.name = name` và `this.score = score`.
4. `name()`, `score()` và `improve(int points)` là **phương thức instance**; cần đối tượng nhận lời gọi, ví dụ `lan.improve(1)`.
5. `improve` bảo vệ quy tắc điểm không vượt 10. Dùng `points > 10 - score` thay vì `score + points > 10` để tránh phép cộng số nguyên lớn tràn trước khi kiểm tra.
6. `final` trên tên cấm thay **tham chiếu tên** sau khi khởi tạo. `score` không final vì nghiệp vụ cho phép tăng điểm. `final class Student` ở đầu cấm kế thừa lớp này; nó không tự làm mọi đối tượng Student bất biến.

Đặt logic kiểm tra trong lớp `Student` giúp các nơi sử dụng không phải lặp lại cùng quy tắc khi đổi điểm.

**Ví dụ lời gọi:** `Student lan = new Student("Lan", 7);` rồi `lan.improve(1);` và `lan.score()` trả `8`. Việc tạo `Student minh = new Student("Minh", 9);` không ảnh hưởng đối tượng `lan`.

## 5. Tạo `Classroom.java` để quản lý nhiều học viên

Tạo file **Classroom.java**:

~~~java
public final class Classroom {
    private final Student[] students;

    public Classroom(Student[] students) {
        if (students == null || students.length == 0) {
            throw new IllegalArgumentException("students must not be empty");
        }

        this.students = students.clone();

        for (Student student : this.students) {
            if (student == null) {
                throw new IllegalArgumentException("student must not be null");
            }
        }
    }

    public void printScores() {
        for (Student student : students) {
            System.out.println(student.name() + "=" + student.score());
        }
    }

    public double average() {
        int total = 0;
        for (Student student : students) {
            total += student.score();
        }
        return (double) total / students.length;
    }
}
~~~

`Classroom` dùng kiểu `Student[]` vì đã có lớp mô tả **một học viên**, không còn cần giữ hai mảng song song `String[]` và `int[]`. Nó tập trung vào hoạt động liên quan tới **cả lớp**.

- Constructor nhận một mảng `Student[]` và dùng `students.clone()` để **sao chép mảng ngoài**, tránh người gọi đổi vị trí các phần tử trong mảng đầu vào làm thay đổi danh sách bên trong.
- Các **đối tượng Student không được nhân bản** bởi `clone()` của mảng: hai mảng vẫn giữ tham chiếu tới cùng các đối tượng. Vì thế nếu `lan.improve(1)`, lớp học sẽ quan sát được điểm cập nhật.
- Vòng lặp kiểm tra không có phần tử `null` giúp `printScores()` và `average()` có thể gọi phương thức trên từng `Student`.
- `average()` không chia cho 0 vì constructor đã từ chối mảng rỗng.
- `printScores()` minh họa chức năng in cho bài học; trong ứng dụng có giao diện phức tạp, có thể tách phần hiển thị riêng và để `Classroom` trả dữ liệu.

`students` được khai báo `final` nghĩa là biến trường này không thể bị gán sang mảng khác sau khởi tạo, **không** có nghĩa mọi phần tử hoặc trạng thái `Student` đều bất biến.

## 6. `SchoolApp.java`: `main` chỉ điều phối các đối tượng

Tạo file **SchoolApp.java**:

~~~java
public final class SchoolApp {
    public static void main(String[] args) {
        Student lan = new Student("Lan", 7);
        Student minh = new Student("Minh", 9);

        Classroom classroom =
                new Classroom(new Student[] {lan, minh});

        lan.improve(1);
        classroom.printScores();

        System.out.println("average=" + classroom.average());
    }
}
~~~

Phiên bản này thực hiện đúng các bước của `MainOnly`, nhưng `main` không cần biết cách lưu điểm hay cách cộng điểm trung bình:

~~~text
SchoolApp.main(...)
   |
   +--> new Student("Lan", 7)
   +--> new Student("Minh", 9)
   +--> new Classroom(Student[] {lan, minh})
   |
   +--> lan.improve(1)
   +--> classroom.printScores()
   +--> classroom.average()
~~~

Trong `classroom.printScores()`, dấu chấm `.` biểu thị gọi **phương thức của đối tượng classroom**; tương tự `lan.improve(1)` gọi phương thức của đối tượng `Student` mà biến `lan` đang tham chiếu. `Student[]` là mảng chứa **các tham chiếu Student**, không phải một loại lớp học có tính năng in/tính trung bình tự động.

`SchoolApp.main` là `static` vì nó là điểm vào; nó **được phép tạo đối tượng và gọi instance method thông qua các tham chiếu**, chứ không phải vì `static` thì bị cấm mọi lời gọi phương thức instance.

## 7. Biên dịch và chạy chương trình nhiều lớp

Đặt `Student.java`, `Classroom.java` và `SchoolApp.java` cùng thư mục. Trong terminal đang đứng tại thư mục đó, dùng lệnh sau; tên file **phải** khớp tên lớp top-level `public`:

~~~bash
javac -encoding UTF-8 Student.java Classroom.java SchoolApp.java
java SchoolApp
~~~

**Expected stdout chính xác của `java SchoolApp`:**

~~~text
Lan=8
Minh=9
average=8.5
~~~

Kết quả **giống hệt `MainOnly`**. Biên dịch nhiều tệp tạo ra `Student.class`, `Classroom.class`, `SchoolApp.class` ở thư mục hiện hành. `javac` cần biết các lớp tham chiếu nhau; liệt kê đủ ba file như trên là cách đơn giản và rõ ràng cho người mới.

Có thể biên dịch vào thư mục riêng; tạo `out` trước và chạy tên lớp từ classpath root:

~~~bash
javac -encoding UTF-8 -d out Student.java Classroom.java SchoolApp.java
java -cp out SchoolApp
~~~

Ở lệnh này, **thư mục `out` phải tồn tại trước** khi truyền cho `javac -d`. Với Bash/Linux/macOS có thể tạo bằng `mkdir -p out`; với PowerShell: `New-Item -ItemType Directory -Force out | Out-Null`. Đây là lựa chọn để giữ tệp `.class` tách khỏi nguồn, không bắt buộc nếu đang dùng cặp lệnh đầu tiên.

**Lệnh `java SchoolApp` chọn đúng một điểm vào:** JVM không lần lượt chạy tất cả phương thức `main` của các lớp hiện diện. Nếu muốn chạy phiên bản cũ, dùng `java MainOnly` riêng (sau khi biên dịch `MainOnly.java`).

Ví dụ trên nằm trong **unnamed package** để tập trung vào sự chuyển đổi từ một sang nhiều lớp. Khi dự án lớn, tổ chức theo `package` và dùng `javac -d`/`java -cp` với **tên lớp đầy đủ**; việc chuyển sang package không làm thay đổi trách nhiệm các đối tượng.

## 8. Quan hệ đối tượng và biến tham chiếu sau khi tách lớp

Đoạn mã:

~~~java
Student lan = new Student("Lan", 7);
Student minh = new Student("Minh", 9);
Student[] list = {lan, minh};
Classroom room = new Classroom(list);
~~~

Có thể hình dung:

~~~text
lan -------> Student(name="Lan", score=7) <------ list[0]
                           ^
                           |
                      room.students[0]

minh ------> Student(name="Minh", score=9) <----- list[1]
                           ^
                           |
                      room.students[1]
~~~

`room.students` là mảng riêng do `clone()` tạo, nhưng các phần tử tham chiếu tới **cùng hai đối tượng Student**. Vì vậy:

- `lan.improve(1)` khiến `room.average()` dùng điểm Lan mới bằng 8.
- `list[0] = new Student("Hoa", 10)` **không tự thay** `room.students[0]` vì lớp học đã sao chép các vị trí của mảng đầu vào.
- `Student alias = lan; alias.improve(1)` tiếp tục sửa **cùng đối tượng Lan**; gán `alias = new Student(...)` chỉ đổi biến alias.
- Nếu truyền `null` thay mảng cho constructor hoặc để một phần tử null, constructor từ chối với `IllegalArgumentException` trước khi phương thức khác dùng dữ liệu.

Nhờ phân biệt **biến tham chiếu**, **đối tượng** và **mảng tham chiếu**, ta tránh hiểu nhầm rằng truyền đối tượng vào constructor sẽ tạo bản sao sâu của toàn bộ dữ liệu.

## 9. Những bước refactor an toàn và lỗi thường gặp

Khi chuyển từ một `main` dài sang nhiều lớp, có thể đi theo trình tự:

1. **Giữ hành vi đầu ra làm mốc.** Biên dịch/chạy bản ban đầu và lưu những trường hợp cần kiểm tra, gồm cả dữ liệu không hợp lệ nếu có.
2. **Nhóm dữ liệu luôn đi cùng nhau.** Ở đây `name` và `score` thuộc cùng một học viên, nên được giữ trong `Student` thay vì hai mảng song song.
3. **Đưa hành vi gắn với dữ liệu vào lớp đó.** Việc đổi và kiểm tra điểm thuộc `Student.improve`; việc tính trung bình thuộc `Classroom.average`.
4. **Giữ `main` làm nơi khởi tạo và điều phối.** Tạo `new Student(...)` rồi truyền cho `Classroom`; gọi các phương thức có trách nhiệm rõ ràng.
5. **Biên dịch tất cả lớp liên quan và so sánh kết quả.** Phiên bản mới phải cho kết quả nghiệp vụ tương đương và không thay đổi ngoài ý muốn.
6. **Chỉ tách thêm khi có lý do.** Không bắt buộc mỗi phương thức, biến hay phép tính nhỏ phải có một lớp riêng.

| Sai sót | Cách hiểu / cách sửa |
| --- | --- |
| Đặt tất cả các trường thành `public` để `main` tự sửa | Ưu tiên trường `private` và API có hợp đồng rõ |
| Cố gọi `Student.improve(1)` | `improve` là instance method; gọi qua đối tượng như `lan.improve(1)` |
| Viết `new Student(...)` nhưng gọi `new Classroom()` khi không có constructor không tham số | Phải dùng đúng constructor đã khai báo |
| Có hai lớp top-level `public` trong cùng một file `.java` | Tách thành file đúng tên lớp, hoặc dùng cách tổ chức hợp lệ khác |
| Chỉ biên dịch `SchoolApp.java` khi mã nguồn phụ thuộc chưa được tìm thấy | Chỉ rõ đủ file nguồn hoặc cấu hình sourcepath/classpath phù hợp |
| `java Student` dù `Student` không có `main` | Chạy `java SchoolApp` là lớp chứa điểm vào |
| Giả định `clone()` trên `Student[]` sao chép luôn từng `Student` | `clone()` chỉ sao chép mảng, các tham chiếu phần tử vẫn chung |
| Một lớp có cả giao diện, dữ liệu, lưu file và mọi phép tính | Xem xét tách trách nhiệm, nhưng không chia nhỏ một cách máy móc |
| Kết quả khác phiên bản `main` ban đầu | So lại dữ liệu đầu vào, thứ tự thay đổi trạng thái và phép tính |

### Bài tập

**Bài 1.** Thêm một học viên `Student("Hoa", 10)` vào đầu vào của `Classroom`, dự đoán điểm trung bình và kiểm tra bằng chạy chương trình.

**Bài 2.** Gọi `lan.improve(3)` khi Lan đang đạt 8. Vì sao bị từ chối? Phân tích trách nhiệm kiểm tra thuộc lớp nào.

**Bài 3.** Viết phương thức `isPassing()` trong `Student` với điều kiện điểm từ 5 trở lên. Có cần thay `SchoolApp` để biết cách so sánh điểm không?

**Bài 4.** Thêm `highestScore()` vào `Classroom`. Giải thích vì sao phép tính toàn lớp thuộc `Classroom` chứ không thuộc riêng từng `Student`.

**Bài 5.** Tạo `Student[] input = {lan, minh}`; đưa nó vào `Classroom`, rồi gán `input[0] = new Student("Hoa", 10)`. Vì sao danh sách trong lớp học không thay theo thao tác này, trong khi `lan.improve(1)` vẫn có thể được lớp học quan sát?

### Ghi nhớ

**`main` là điểm bắt đầu, không cần chứa toàn bộ logic của chương trình.** Bắt đầu bằng một `main` đơn giản là hợp lý; khi dữ liệu và hành vi phát triển, hãy tách thành các lớp có trách nhiệm rõ ràng, giữ trạng thái sau trường `private`, tạo đối tượng bằng `new` và gọi instance method qua biến tham chiếu. Các lớp có thể nằm ở file `.java` khác nhau, được biên dịch cùng nhau; khi chạy hãy chỉ rõ lớp có `main`. Mục tiêu tách lớp là **bảo toàn hành vi, giảm phụ thuộc và giúp mã dễ mở rộng/kiểm tra**, không phải tăng số lượng lớp cho đẹp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classes-and-objects-010">Đọc cú pháp generic như List&lt;String&gt; và ý nghĩa kiểm tra kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc cú pháp generic như `List<String>` và ý nghĩa kiểm tra kiểu trong Java

## 1. `List<String>` được đọc như thế nào?

Trong Java, **generic** (*kiểu tổng quát*) cho phép lớp, interface và phương thức mô tả kiểu dữ liệu mà chúng xử lý thông qua **tham số kiểu** (*type parameter*). Khi viết:

```java
List<String> names;
```

ta có thể đọc là **“một danh sách có phần tử kiểu `String`”**.

- `List` là interface `java.util.List<E>`, dùng để biểu diễn một dãy phần tử có thứ tự và cho phép truy cập theo vị trí.
- `<String>` là **đối số kiểu** (*type argument*) cụ thể, thay cho tham số kiểu `E` trong khai báo `List<E>`. Dấu `< >` ở đây chỉ thông tin kiểu, không phải các toán tử so sánh nhỏ hơn/lớn hơn.
- `names` là **biến tham chiếu** có kiểu `List<String>`. Nếu chỉ khai báo mà chưa gán, biến cục bộ này chưa sử dụng được để đọc; nếu là một field thì giá trị mặc định là `null`.
- `List` là **interface**, nên thường khởi tạo bằng một lớp cài đặt, ví dụ `ArrayList`. Cần `import java.util.List;` và `import java.util.ArrayList;` hoặc viết tên đầy đủ.

```java
import java.util.ArrayList;
import java.util.List;

// Các dòng sau đặt trong phương thức hoặc khối khởi tạo:
List<String> names = new ArrayList<String>();
names.add("An");
names.add("Binh");
String first = names.get(0);  // "An"
```

Trên Java 7+, có thể dùng **diamond operator** `<>` ở vế phải để trình biên dịch suy luận đối số kiểu từ ngữ cảnh:

```java
List<String> names = new ArrayList<>();
```

Cú pháp này vẫn giữ kiểu phần tử là `String`; `<>` không có nghĩa danh sách nhận mọi kiểu. Trong Java 17, dùng `var names = new ArrayList<String>();` thì kiểu được suy luận là `ArrayList<String>`; còn `var names = new ArrayList<>();` thiếu ngữ cảnh kiểu mong muốn sẽ suy luận kiểu thành phần là `Object`. Không nên mặc định hai cách khai báo `var` này cho kết quả giống `List<String>`.

## 2. Kiểm tra kiểu khi biên dịch mang lại điều gì?

Với `List<String>`, các phương thức do `List<E>` khai báo được nhìn qua tham số kiểu `E = String`:

| Biểu thức | Kiểu hoặc kết quả | Ý nghĩa |
| --- | --- | --- |
| `names.add("Java")` | Hợp lệ, trả về `boolean` | Thêm một `String` |
| `names.get(0)` | `String` | Không cần ép kiểu thủ công tại mã nguồn |
| `names.set(0, "Kotlin")` | `String` | Thay phần tử và trả về phần tử cũ |
| `for (String s : names)` | Hợp lệ | Duyệt từng `String` |
| `names.add(123)` | **Lỗi biên dịch** | `Integer` không phải `String` |
| `Integer n = names.get(0)` | **Lỗi biên dịch** | Không thể gán giá trị kiểu `String` cho `Integer` |

Ví dụ độc lập trong phương thức:

```java
List<String> names = new ArrayList<>();
names.add("Java");
String name = names.get(0); // Hợp lệ

// names.add(123);          // LỖI: int autobox thành Integer, không phải String
// Integer x = names.get(0); // LỖI: get trả về String theo khai báo generic
```

Điểm quan trọng: **trình biên dịch phát hiện nhiều phép gán/thêm sai kiểu trước khi chương trình chạy**. Điều này giúp API tự diễn đạt kiểu dữ liệu mong đợi, giảm lỗi ép kiểu thủ công và làm mã dễ đọc.

Generics **không bảo đảm** nội dung trong một danh sách luôn hợp lệ nếu đã cố tình bỏ qua cảnh báo dùng *raw type*, thực hiện ép kiểu không được kiểm chứng, hoặc có mã tương tác qua API cũ. Vì vậy cần hiểu cả giới hạn ở runtime.

## 3. Cấu trúc khai báo generic: tham số kiểu và đối số kiểu

Có thể tự định nghĩa một lớp generic:

```java
class Box<T> {
    private T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

Ở đây `T` là **tham số kiểu** của lớp `Box<T>`. Khi sử dụng:

```java
Box<String> textBox = new Box<>("Hello");
String message = textBox.get(); // message == "Hello"

Box<Integer> intBox = new Box<>(42);
Integer number = intBox.get();  // number == 42
```

`String` và `Integer` ở hai khai báo là **đối số kiểu** cụ thể. Trình biên dịch kiểm tra `Box<String>` theo `String`, `Box<Integer>` theo `Integer`.

Generic method khai báo tham số kiểu trước kiểu trả về:

```java
static <T> T first(List<T> values) {
    return values.get(0);
}

// String s = first(List.of("A", "B"));
// Integer n = first(List.of(10, 20));
```

Đây là đoạn minh họa nằm trong một lớp. Khi dùng, kiểu `T` có thể được suy luận từ đối số. `List.of(...)` là tiện ích tạo danh sách **không cho phép thay đổi cấu trúc/phần tử** (unmodifiable) trong các phiên bản Java hiện đại; nếu cần `add`, dùng `ArrayList`.

### Vì sao dùng `Integer` thay vì `int`?

Đối số kiểu của generic **phải là kiểu tham chiếu**, không phải kiểu nguyên thủy:

```java
List<Integer> numbers = new ArrayList<>();
numbers.add(10); // autoboxing: int -> Integer
int value = numbers.get(0); // unboxing: Integer -> int

// List<int> invalid = new ArrayList<>(); // LỖI cú pháp/kiểu
```

Nếu phần tử `Integer` là `null`, unboxing sang `int` có thể gây `NullPointerException`. Với dữ liệu số lớn và hiệu năng nhạy cảm, các mảng nguyên thủy hoặc `IntStream` đôi khi tránh được chi phí boxing, tùy bài toán.

## 4. `List<String>` không phải `List<Object>`

Dù `String` là kiểu con của `Object`, **`List<String>` không phải kiểu con của `List<Object>`**. Generic type argument mặc định có tính **bất biến** (*invariant*).

```java
List<String> words = new ArrayList<>();
words.add("Java");

// List<Object> objects = words; // LỖI BIÊN DỊCH
```

Nếu cho phép phép gán trên, biến `objects` có thể gọi `objects.add(123)` và đưa `Integer` vào một danh sách vốn được khai báo chứa `String`, phá vỡ an toàn kiểu của `words`. Trình biên dịch từ chối phép gán để tránh điều đó.

Tương tự, `List<Integer>` không tự chuyển thành `List<Number>`. Để mô tả một danh sách có kiểu phần tử chưa biết chính xác hoặc theo một giới hạn, Java dùng **wildcard**.

## 5. Đọc các cú pháp wildcard thông dụng

| Cú pháp | Đọc/diễn giải | Lưu ý |
| --- | --- | --- |
| `List<?>` | Danh sách có **kiểu phần tử chưa biết** | Đọc phần tử như `Object`; không thêm giá trị khác `null` |
| `List<? extends Number>` | Danh sách có kiểu phần tử nào đó là `Number` hoặc kiểu con | Phù hợp cho thao tác **đọc** số; không được thêm tùy ý `Integer` |
| `List<? super Integer>` | Danh sách có kiểu phần tử nào đó là `Integer` hoặc một kiểu cha của nó | Có thể **thêm** `Integer`; khi đọc an toàn nhất chỉ biết là `Object` |

Ví dụ:

```java
static double sum(List<? extends Number> values) {
    double total = 0;
    for (Number value : values) {
        total += value.doubleValue();
    }
    return total;
}

static void addDefault(List<? super Integer> values) {
    values.add(0); // Integer được chấp nhận
}
```

`sum` nhận `List<Integer>`, `List<Double>` hoặc các danh sách có phần tử là kiểu con thích hợp của `Number` mà không đánh mất an toàn kiểu. Tuy nhiên `List<? extends Number>` không cho phép tùy ý thêm một `Number`: danh sách thực tế có thể là `List<Integer>`. Với `List<? super Integer>`, việc thêm `Integer` là an toàn vì kiểu thực tế có thể là `Integer`, `Number` hoặc `Object`.

Quy tắc nhớ thường dùng là **PECS**: *Producer Extends, Consumer Super*. Dùng `extends` khi chủ yếu đọc dữ liệu kiểu `T` từ cấu trúc; dùng `super` khi cần ghi các giá trị kiểu `T` vào cấu trúc.

Các từ `extends`/`super` ở **wildcard bound** mô tả quan hệ giới hạn kiểu, không có nghĩa `List` đã kế thừa trực tiếp từ một lớp danh sách khác.

## 6. Ví dụ hoàn chỉnh Java 17+

Tạo tệp **`GenericListDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class GenericListDemo {
    static void printUppercase(List<String> values) {
        for (String value : values) {
            System.out.println("upper: " + value.toUpperCase());
        }
    }

    static int countNonEmpty(List<? extends CharSequence> values) {
        int count = 0;
        for (CharSequence value : values) {
            if (value.length() > 0) {
                count++;
            }
        }
        return count;
    }

    static void addLabel(List<? super String> destination) {
        destination.add("new");
    }

    static <T> T first(List<T> values) {
        return values.get(0);
    }

    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        names.add("java");
        names.add("core");

        String firstName = names.get(0);
        System.out.println("first: " + firstName);
        System.out.println("size: " + names.size());
        printUppercase(names);

        names.set(1, "generics");
        System.out.println("updated: " + names);
        System.out.println("first generic: " + first(names));

        List<Integer> numbers = List.of(10, 20);
        int firstNumber = first(numbers);
        System.out.println("numbers: " + numbers);
        System.out.println("number first: " + firstNumber);
        System.out.println("nonempty: " + countNonEmpty(names));

        List<Object> destination = new ArrayList<>();
        addLabel(destination);
        System.out.println("destination: " + destination);

        List<?> unknown = names;
        Object value = unknown.get(0);
        System.out.println("unknown item: " + value);

        // names.add(42);               // Lỗi biên dịch nếu bỏ chú thích
        // List<Object> bad = names;     // Lỗi vì generic invariant
    }
}
```

Trong thư mục chứa tệp với **JDK 17 trở lên**, chạy trên PowerShell/CMD (Windows) hoặc bash/zsh (Linux/macOS):

```bash
javac -encoding UTF-8 GenericListDemo.java
java GenericListDemo
```

Kết quả stdout chính xác (lệnh `javac` thành công không in thông báo):

```text
first: java
size: 2
upper: JAVA
upper: CORE
updated: [java, generics]
first generic: java
numbers: [10, 20]
number first: 10
nonempty: 2
destination: [new]
unknown item: java
```

Giải thích: `names` chỉ nhận `String`; `first(names)` suy luận `T` là `String`, còn `first(numbers)` suy luận `T` là `Integer` (khi gán sang `int` có unboxing). `countNonEmpty` chấp nhận `List<String>` vì `String` thực hiện `CharSequence`. `List<?>` cho phép lấy phần tử dạng `Object`, dù không biết chính xác kiểu phần tử qua biểu thức có wildcard.

## 7. Type erasure: kiểm tra lúc biên dịch khác runtime

Java thực hiện generics chủ yếu qua **type erasure**: ở cấp bytecode/runtime, nhiều thông tin về đối số kiểu của các tham chiếu generic không được giữ dưới dạng một loại đối tượng riêng cho mỗi `T`. Trình biên dịch kiểm tra kiểu và có thể thêm những phép ép kiểu cần thiết vào mã sinh ra.

Vì vậy:

```java
List<String> a = new ArrayList<>();
List<Integer> b = new ArrayList<>();
System.out.println(a.getClass() == b.getClass()); // true
```

Hai đối tượng đều được tạo bởi lớp runtime `ArrayList`; không có một lớp runtime riêng `ArrayList<String>` và `ArrayList<Integer>` cho hai đối tượng này.

Các ví dụ sau **không hợp lệ**:

```java
Object value = new ArrayList<String>();
// if (value instanceof List<String>) { } // LỖI biên dịch
// List<String>[] arrays = new List<String>[2]; // LỖI tạo mảng generic
```

Có thể kiểm tra `value instanceof List<?>` ở nơi phù hợp, nhưng **không thể từ phép kiểm tra đó kết luận mọi phần tử đều là `String`**. Đừng nhầm generics là cơ chế JVM liên tục kiểm tra đối số kiểu `String` khi thêm vào mọi `List`: các kiểm tra thường diễn ra **ở biên dịch**, còn tính hợp lệ ở runtime phụ thuộc thao tác cụ thể và hệ thống kiểu đã bị xóa thông tin generic.

**Raw type** như `List list = new ArrayList();` bỏ phần kiểm tra kiểu generic và dễ làm mất an toàn:

```java
List raw = new ArrayList(); // raw type: javac có thể cảnh báo
raw.add(123);
List<String> strings = raw; // unchecked conversion: cảnh báo
// String text = strings.get(0); // ClassCastException khi chạy
```

Các đoạn dùng raw type ở trên chỉ để phân tích hành vi; **không nên áp dụng** trong code mới. Cảnh báo có thể quan sát khi biên dịch với `javac -Xlint:rawtypes -Xlint:unchecked`. Khi sử dụng API cũ, hạn chế phạm vi chuyển đổi không được kiểm tra và xác minh dữ liệu trước khi tin tưởng kiểu phần tử.

## 8. So sánh nhanh với mảng và các lỗi thường gặp

Mảng và generic list có các đặc điểm khác nhau:

- `String[]` có kích thước cố định và cho phép truy cập `array[i]`; `List<String>` là interface của danh sách, truy cập qua `get(i)` và có thể thay đổi kích thước tùy implementation.
- Mảng các kiểu tham chiếu có tính **hiệp biến**: `Object[] a = new String[2];` biên dịch được nhưng gán `a[0] = Integer.valueOf(1)` gây `ArrayStoreException` khi chạy.
- Generic `List<String>` có tính **bất biến** đối với đối số kiểu: `List<Object> a = new ArrayList<String>();` bị từ chối khi biên dịch, giúp tránh kiểu lỗi tương tự ngay từ đầu.
- `List<String>` là danh sách chứa tham chiếu `String`; bản thân đối tượng danh sách có thể là `ArrayList`, `LinkedList` hoặc implementation tương thích, tùy ngữ cảnh.
- **Không viết** `List<int>`: generic không nhận kiểu nguyên thủy, dùng `List<Integer>`.
- **Không viết** `List<String> names = new List<>();`: `List` là interface, cần implementation như `new ArrayList<>()`.
- **Không dùng** `List<Object>` để đại diện chung cho mọi danh sách generic: nếu chỉ cần đọc phần tử không biết kiểu, dùng `List<?>`; nếu có ràng buộc kiểu, chọn wildcard phù hợp.
- **Không hiểu nhầm** `List.of("a", "b")` là danh sách cho phép `add`: danh sách từ `List.of` không hỗ trợ chỉnh sửa; `add` sẽ gây `UnsupportedOperationException`.
- `List<String>` **không tự đảm bảo phần tử khác `null`**: với `ArrayList<String>`, `null` có thể được thêm vào; gọi phương thức trên phần tử ấy cần kiểm tra. Ngoại lệ: một số factory/implementation như `List.of` không chấp nhận `null`.

## 9. Checklist đọc một kiểu generic

Khi gặp cú pháp như `Map<String, Integer>` hoặc `List<? extends Number>`, đọc lần lượt:

1. **Tên lớp/interface generic**: `Map`, `List`, `Box` xác định các thao tác có thể sử dụng.
2. **Đối số kiểu đặt trong `<...>`**: chẳng hạn `String`, `Integer` xác định các kiểu được kiểm tra khi biên dịch.
3. **Giới hạn kiểu nếu có**: `?`, `? extends T`, `? super T` diễn tả mức độ chắc chắn khi đọc/ghi dữ liệu.
4. **Lớp runtime thực tế**: `new ArrayList<>()` tạo đối tượng `ArrayList`; generic không tự tạo một lớp runtime chuyên biệt theo mỗi kiểu phần tử.
5. **Hoạt động hợp lệ**: `add`, `get`, `set` phải theo đúng kiểu và theo khả năng sửa đổi của implementation.

**Ghi nhớ:** `List<String>` nghĩa là danh sách có **hợp đồng kiểu phần tử `String` được trình biên dịch kiểm tra**. Generics giúp bắt lỗi sớm và giảm ép kiểu, trong khi wildcard mô tả cách dùng linh hoạt hơn và type erasure giải thích các giới hạn tại runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classes-and-objects-011">Nhận diện lambda và tham chiếu phương thức trong lời gọi API</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện lambda và tham chiếu phương thức trong lời gọi API — Java 17+

## 1. Nhìn cú pháp để biết API đang nhận một hành vi

Khi đọc mã Java, hai dạng phổ biến là:

~~~java
words.forEach(word -> System.out.println(word));
words.forEach(System.out::println);
~~~

Cả hai truyền một **hành vi** cho API `forEach`: với mỗi phần tử, gọi `println`. Dạng đầu là **lambda expression** có ký hiệu `->`; dạng sau là **method reference** có ký hiệu `::`.

Không cần hiểu trước mọi kỹ thuật lập trình hàm để đọc chúng. Hãy đặt ba câu hỏi:

1. API đang gọi phương thức nào (`forEach`, `map`, `removeIf`, `sort`...)?
2. API yêu cầu loại hành vi nào (nhận gì, trả gì)?
3. Lambda hoặc method reference cung cấp hành vi đó theo cách nào?

**Điểm quan trọng:** một lambda hoặc method reference tạo ra giá trị có kiểu **functional interface** phù hợp; bản thân việc tạo nó thường **chưa thực thi thân hành vi**. API hay lời gọi phương thức trừu tượng tương ứng mới kích hoạt hành vi khi thích hợp.

## 2. Lambda: `thamSo -> bieuThuc` hoặc `thamSo -> { ... }`

~~~java
x -> x * 2

(String text) -> text.length()

name -> {
    System.out.println("Hello " + name);
}
~~~

- Bên trái `->` là danh sách **tham số**. Một tham số có kiểu suy luận có thể bỏ dấu ngoặc: `x -> ...`; với nhiều tham số phải viết `(a, b) -> ...`.
- Bên phải là **biểu thức** hoặc **khối lệnh**. Nếu cần trả giá trị từ khối lệnh, dùng `return expression;` khi kiểu đích yêu cầu kết quả.
- Kiểu của các tham số thường được suy luận từ **ngữ cảnh kiểu đích** (*target typing*).

Ví dụ:

~~~java
java.util.function.IntUnaryOperator doubleIt = x -> x * 2;
System.out.println(doubleIt.applyAsInt(3));
~~~

**Exact stdout:**

~~~text
6
~~~

`IntUnaryOperator` có một phương thức trừu tượng `applyAsInt(int)`: nhận một số nguyên và trả một số nguyên. Lambda trên chính là phần cài đặt cho hành vi đó.

## 3. Functional interface là kiểu đích

Một **functional interface** có đúng **một phương thức trừu tượng cần triển khai** (*single abstract method / SAM*), không tính các phương thức của `Object`. Nó có thể còn có `default` hoặc `static` methods.

| Functional interface | Phương thức chính | Đọc như |
|---|---|---|
| `Consumer<T>` | `accept(T)` → `void` | Nhận giá trị để xử lý, không trả kết quả |
| `Predicate<T>` | `test(T)` → `boolean` | Kiểm tra điều kiện đúng/sai |
| `Function<T,R>` | `apply(T)` → `R` | Biến đổi một giá trị thành giá trị khác |
| `Supplier<T>` | `get()` → `T` | Cung cấp giá trị không cần đối số |
| `Comparator<T>` | `compare(T,T)` → `int` | Xác định thứ tự hai phần tử |
| `Runnable` | `run()` → `void` | Hành động không đối số và không trả giá trị |

Khi thấy `items.removeIf(s -> s.isBlank())`, có thể suy luận `removeIf` nhận `Predicate` nên lambda cần cho ra `boolean`. Khi thấy `items.forEach(s -> System.out.println(s))`, API nhận `Consumer`, nên hành vi không cần trả giá trị.

**Không phải mọi interface đều có thể làm kiểu đích cho lambda**: interface có nhiều phương thức trừu tượng độc lập không phải SAM. Annotation `@FunctionalInterface` giúp compiler kiểm tra ý định khai báo, nhưng lambda không yêu cầu interface bắt buộc có annotation ấy.

## 4. Method reference: `::` thay cho lambda chuyển tiếp

Method reference dùng một phương thức hoặc constructor **đã tồn tại** để thỏa hợp đồng functional interface.

| Dạng | Ví dụ | Lambda tương đương về ý nghĩa |
|---|---|---|
| Static method | `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| Instance method của object có sẵn | `System.out::println` | `s -> System.out.println(s)` |
| Instance method của object được truyền vào | `String::length` | `s -> s.length()` |
| Constructor | `StringBuilder::new` | `() -> new StringBuilder()` |

~~~java
java.util.function.Function<String, Integer> length = String::length;
System.out.println(length.apply("Java"));
~~~

**Exact stdout:**

~~~text
4
~~~

Ở `String::length`, **đối tượng nhận lời gọi** chính là đối số `"Java"`, nên API tương đương `s -> s.length()`.

Với method reference của một object cụ thể:

~~~java
String prefix = "Hi ";
java.util.function.Function<String, String> greet = prefix::concat;
System.out.println(greet.apply("Java"));
~~~

**Exact stdout:**

~~~text
Hi Java
~~~

`prefix::concat` đã gắn receiver `prefix` tại lúc tạo tham chiếu phương thức. Nó không tạo bản sao String hay gọi `concat` ngay. **Không viết `String::length()`**: method reference cần tên phương thức sau `::`, không có dấu ngoặc gọi.

## 5. Đọc `forEach`, `map`, `removeIf` và `sort`

~~~java
List<String> names = List.of("An", "Binh");
names.forEach(System.out::println);
~~~

`forEach` nhận `Consumer<String>` và thực hiện một hành động cho mỗi phần tử. Với danh sách này, stdout là:

~~~text
An
Binh
~~~

Các API thường gặp:

| Lời gọi | Đọc hiểu |
|---|---|
| `names.forEach(System.out::println)` | Gọi `println` trên mỗi phần tử |
| `names.stream().map(String::length)` | Tạo stream kết quả độ dài từng String |
| `mutable.removeIf(String::isBlank)` | Xóa những phần tử thỏa predicate “chuỗi chỉ chứa khoảng trắng hoặc rỗng” |
| `mutable.replaceAll(s -> s.toUpperCase())` | Thay từng phần tử bằng dạng chữ hoa |
| `mutable.sort(Comparator.comparingInt(String::length))` | Sắp xếp theo độ dài tăng dần |

**Phân biệt thời điểm chạy:** `Collection.forEach` thực hiện hành động khi được gọi. Với `Stream.map`, việc tạo pipeline thường **lazy**; hàm biến đổi được áp dụng khi có thao tác terminal, ví dụ `toList()` hoặc `forEach()`.

**Phân biệt tính thay đổi dữ liệu:** `List.of(...)` trả danh sách không thể sửa trực tiếp; gọi `removeIf`, `replaceAll` hay `sort` trên nó có thể ném `UnsupportedOperationException`. Nếu cần sửa danh sách, dùng `new ArrayList<>(List.of(...))`. Kết quả `Stream.toList()` từ Java 16 trở lên cũng không cho phép sửa cấu trúc danh sách kết quả.

## 6. Vì sao không phải lúc nào cũng thay lambda bằng method reference?

Hai cách viết thường tương đương khi lambda **chỉ chuyển tiếp đối số** đến một phương thức:

~~~java
words.stream().map(word -> word.length()).toList();
words.stream().map(String::length).toList();
~~~

Nhưng lambda rất hữu ích khi cần tính toán thêm hoặc nhiều bước:

~~~java
words.stream().map(word -> word.trim().toUpperCase()).toList();
~~~

Đoạn này không tương đương với một method reference duy nhất kiểu `String::trim` vì còn bước chữ hoa. Có thể đặt logic vào một phương thức có tên `normalize` và viết `ClassName::normalize` khi thao tác được tái sử dụng.

**Cẩn thận với overload:** `System.out::println` có nhiều overload, Java chọn phiên bản tương ứng với kiểu đích, ví dụ `Consumer<String>`. Không thể xem method reference như một giá trị “không có kiểu” trong mọi ngữ cảnh; nó cần target functional interface thích hợp.

## 7. Capture biến ngoài và những lỗi cơ bản

Lambda có thể sử dụng local bên ngoài nếu biến đó là `final` hoặc **effectively final** (không bị gán lại):

~~~java
int threshold = 3;
java.util.function.Predicate<String> longEnough =
        text -> text.length() >= threshold;
System.out.println(longEnough.test("Java"));
~~~

**Exact stdout:**

~~~text
true
~~~

Nếu sau đó viết `threshold = 5;` trong cùng phương thức, lambda capture `threshold` như trên sẽ **không biên dịch**, do biến không còn effectively final.

Phân biệt **gán lại tham chiếu** và **thay đổi nội dung object**: một tham chiếu local effectively final vẫn có thể trỏ tới một object mutable, nên callback có thể thấy thay đổi trạng thái object đó. Điều này có thể làm code khó hiểu, nhất là stream song song; ở mức nhập môn, ưu tiên các hàm không gây tác dụng phụ bất ngờ.

Lambda `x -> x + 1` không phải là phương thức có tên để gọi qua `x(...)`; nó được dùng thông qua functional interface, chẳng hạn `op.applyAsInt(5)`.

## 8. Chương trình Java 17+ hoàn chỉnh

Tạo file đúng tên **`LambdaMethodReferenceDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class LambdaMethodReferenceDemo {
    private LambdaMethodReferenceDemo() {}

    static String normalize(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    static void printTagged(String value) {
        System.out.println("word=" + value);
    }

    public static void main(String[] args) {
        List<String> raw = List.of(" java ", "go", " rust ");
        List<String> words = raw.stream()
                .map(LambdaMethodReferenceDemo::normalize)
                .toList();
        System.out.println("normalized=" + words);

        List<Integer> lengths = words.stream()
                .map(String::length)
                .toList();
        System.out.println("lengths=" + lengths);

        words.forEach(LambdaMethodReferenceDemo::printTagged);

        List<String> editable = new ArrayList<>(
                List.of("a", "", "bbb", "cc"));
        editable.removeIf(String::isBlank);
        System.out.println("filtered=" + editable);

        editable.replaceAll(s -> s.toUpperCase(Locale.ROOT));
        System.out.println("upper=" + editable);

        editable.sort(
                Comparator.<String>comparingInt(String::length)
                        .thenComparing(Comparator.naturalOrder()));
        System.out.println("sorted=" + editable);

        Predicate<String> longEnough = s -> s.length() >= 3;
        System.out.println("predicate=" + longEnough.test("JAVA"));

        Function<String, Integer> measure = String::length;
        System.out.println("function=" + measure.apply("RUST"));

        Consumer<String> printer = System.out::println;
        printer.accept("consumer=hello");

        Supplier<StringBuilder> supplier = StringBuilder::new;
        System.out.println("constructor="
                + supplier.get().append("OK"));

        String prefix = "Hi ";
        Function<String, String> bound = prefix::concat;
        System.out.println("bound=" + bound.apply("Java"));

        IntUnaryOperator doubleIt = n -> n * 2;
        System.out.println("lambda=" + doubleIt.applyAsInt(3));

        List<Integer> integers = List.of("2", "4").stream()
                .map(Integer::parseInt)
                .toList();
        System.out.println("parsed=" + integers);

        Runnable action =
                () -> System.out.println("runnable=executed");
        action.run();
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, không cần Maven, Gradle hoặc thư viện ngoài JDK, không dùng preview features.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 LambdaMethodReferenceDemo.java
java -cp . LambdaMethodReferenceDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\LambdaMethodReferenceDemo.java
java -cp . LambdaMethodReferenceDemo
~~~

### Exact expected stdout

~~~text
normalized=[JAVA, GO, RUST]
lengths=[4, 2, 4]
word=JAVA
word=GO
word=RUST
filtered=[a, bbb, cc]
upper=[A, BBB, CC]
sorted=[A, CC, BBB]
predicate=true
function=4
consumer=hello
constructor=OK
bound=Hi Java
lambda=6
parsed=[2, 4]
runnable=executed
~~~

**Giải thích:** `map` biến đổi chuỗi và sau đó lấy độ dài bằng method reference; `forEach` dùng static method reference tới `printTagged`. `removeIf` sử dụng predicate `String::isBlank`, `replaceAll` dùng lambda, `sort` dùng comparator xây từ `String::length`. Các ví dụ `Supplier`, `Function` và `Runnable` minh họa hành vi được thực thi khi gọi phương thức SAM hoặc API tương ứng.

## 9. Bài tập, lỗi thường gặp và checklist

| Sai lầm | Nguyên nhân | Cách hiểu đúng |
|---|---|---|
| Nghĩ `x -> x * 2` là phép tính chạy ngay | Lambda tạo hành vi để thực thi sau | Cần lời gọi SAM/API thích hợp |
| Nghĩ `String::length` là gọi `length()` tức thì | Đây là method reference | Dùng làm đối số cho API nhận functional interface |
| Viết `String::length()` | Sai cú pháp method reference | `String::length` |
| Dùng `map` mà không có terminal operation rồi chờ tác dụng | Pipeline stream thường lazy | Gọi `toList`, `forEach`, ... |
| Dùng `removeIf` trên `List.of(...)` | Danh sách không hỗ trợ thay đổi | Dùng `ArrayList` khi cần sửa |
| Dùng lambda mà không có kiểu đích hợp lệ | Lambda cần functional interface | Chọn `Predicate`, `Function`, `Consumer`... |
| Capture một local rồi gán lại local đó | Không effectively final | Tránh gán lại hoặc thiết kế lại |
| Nhầm `Consumer` với `Function` | Consumer không trả kết quả | Chọn functional interface đúng hợp đồng |

**Bài A:** `List.of("A","BB").stream().map(String::length).toList()` cho kết quả gì? **`[1, 2]`**.

**Bài B:** `names.forEach(System.out::println)` tương đương lambda nào? **`names.forEach(s -> System.out.println(s))`** trong ngữ cảnh `Consumer<String>`.

**Bài C:** API `removeIf` yêu cầu callback kiểm tra kiểu gì? **`Predicate<E>`**, có phương thức `test(E)` trả `boolean`.

**Bài D:** `StringBuilder::new` trong `Supplier<StringBuilder>` nghĩa là gì? **Một hành vi không tham số tạo `StringBuilder` mới khi `get()` được gọi**, không tạo object ngay lúc chỉ viết method reference.

### Checklist

- [ ] Nhận ra `->` là lambda và `::` là method reference.
- [ ] Biết functional interface là kiểu đích cho lambda/reference.
- [ ] Phân biệt `Consumer`, `Predicate`, `Function`, `Supplier`, `Comparator`, `Runnable`.
- [ ] Đọc được static, instance-bound, instance-unbound và constructor references.
- [ ] Hiểu callback thực thi khi API/lời gọi SAM kích hoạt, không phải khi viết cú pháp.
- [ ] Phân biệt stream trung gian `map` với terminal `toList`.
- [ ] Nhận ra API nào sửa danh sách, API nào tạo kết quả mới.
- [ ] Tránh capture local không effectively final.
- [ ] Chạy `LambdaMethodReferenceDemo.java` bằng JDK 17+ và đối chiếu exact stdout.

**Kết luận:** Khi gặp `list.forEach(x -> ...)` hoặc `stream.map(Type::method)`, hãy đọc đó là **truyền một hành vi cho API**. Lambda viết trực tiếp hành vi; method reference tái sử dụng phương thức/constructor đã có. Kiểu functional interface của API quyết định hành vi nhận đối số và trả kết quả như thế nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classes-and-objects-012">Nhận diện annotation @Override và @Deprecated</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện annotation `@Override` và `@Deprecated` trong Java

> **Java core — language-basics / Lớp và đối tượng**  
> **Môi trường:** Java 17+, chỉ cần JDK.  
> **Mục tiêu:** Biết mục đích, vị trí và tác dụng của `@Override`, `@Deprecated`; phân biệt ghi đè với nạp chồng/che khuất phương thức; nhận diện API cũ và cách thay thế an toàn.

## 1. Annotation là gì?

*Annotation* là thông tin bổ sung được đặt trên khai báo (và một số vị trí khác tùy loại annotation), thường bắt đầu bằng dấu `@`. Nó giúp trình biên dịch, công cụ hoặc thư viện nhận biết ý định của mã:

~~~java
@Override
public String toString() {
    return "example";
}
~~~

Annotation **không phải** lời gọi phương thức. Đặt `@Override` trước khai báo phương thức không làm thân tự chạy; phương thức vẫn chạy khi được gọi theo quy tắc Java.

| Annotation | Ý nghĩa chính | Tác dụng dễ quan sát |
| --- | --- | --- |
| `@Override` | Khai báo này dự định ghi đè/triển khai một phương thức hợp lệ từ lớp cha/interface | Trình biên dịch báo lỗi nếu thực tế không ghi đè/triển khai đúng |
| `@Deprecated` | API đã không còn được khuyến nghị sử dụng | Công cụ/trình biên dịch có thể cảnh báo khi mã khác dùng API cũ |

Hai annotation này nằm trong `java.lang` nên không cần `import`. Không nhầm `@Deprecated` với một lệnh xóa mã và không nhầm `@Override` với từ khóa bắt buộc để Java có đa hình.

## 2. `@Override` trên phương thức ghi đè từ lớp cha

~~~java
class Animal {
    public String speak() {
        return "generic";
    }
}

class Dog extends Animal {
    @Override
    public String speak() {
        return "woof";
    }
}
~~~

`Dog.speak()` ghi đè `Animal.speak()`: cùng tên và danh sách kiểu tham số, kiểu trả về tương thích và đáp ứng các quy tắc về quyền truy cập/ngoại lệ. Gọi qua tham chiếu kiểu cha vẫn thực thi phiên bản ở đối tượng thực:

~~~java
Animal pet = new Dog();
System.out.println(pet.speak()); // woof
~~~

`@Override` yêu cầu compiler kiểm tra ý định này. Nếu vô tình viết `speek()` thay `speak()` rồi vẫn gắn `@Override`, trình biên dịch báo lỗi thay vì âm thầm tạo một phương thức **mới**.

**Không có `@Override` thì ghi đè hợp lệ vẫn xảy ra**, nhưng thêm annotation giúp phát hiện sai chữ ký sớm và làm mã rõ hơn.

Có thể ghi đè các phương thức instance phù hợp của `Object`, chẳng hạn `toString()`:

~~~java
@Override
public String toString() {
    return "Dog";
}
~~~

## 3. `@Override` khi triển khai interface

Một lớp triển khai interface thường ghi `@Override` trước phương thức hiện thực hóa hợp đồng:

~~~java
interface Audible {
    String sound();
}

class Bird implements Audible {
    @Override
    public String sound() {
        return "chirp";
    }
}
~~~

`Bird.sound()` phải đáp ứng chữ ký và kiểu trả về của `Audible.sound()`. Vì phương thức trong interface ở ví dụ này là `public`, phương thức triển khai cũng phải `public`; không được làm giảm quyền truy cập.

Trong Java 17, `@Override` cũng được dùng khi ghi đè một `default method` phù hợp của interface. Annotation không tự thay thế từ khóa `implements`/`extends`: quan hệ kiểu và định nghĩa phương thức phải có sẵn.

## 4. Những trường hợp `@Override` không hợp lệ

| Tình huống | Có phải ghi đè hợp lệ để dùng `@Override`? | Lý do |
| --- | --- | --- |
| Viết sai tên `speek()` thay `speak()` | Không | Không khớp phương thức cha |
| `speak(int n)` trong khi cha chỉ có `speak()` | Không | Đây là **overload**, danh sách tham số khác |
| Định nghĩa một phương thức mới chỉ có trong lớp con | Không | Không có phương thức phù hợp để ghi đè |
| Viết constructor `Dog()` | Không | Constructor không phải phương thức ghi đè |
| Khai báo `static` trùng với static method cha | Không | Đây là **method hiding**, không phải overriding |
| Cố ghi đè phương thức `private` của cha | Không | Phương thức private không được kế thừa để ghi đè |
| Cố ghi đè phương thức `final` của cha | Không | `final` chặn việc ghi đè |

Ví dụ dưới đây **cố ý không biên dịch**, chỉ dùng để nhận diện lỗi:

~~~java
class Parent {
    public void run() {}
}

class Child extends Parent {
    // @Override
    // public void run(int times) {} // Lỗi: chỉ nạp chồng, không ghi đè.

    // @Override
    // public void rnu() {} // Lỗi: gõ sai tên phương thức.
}
~~~

Bỏ comment `@Override` trong một ví dụ sai sẽ khiến lỗi được phát hiện ngay khi biên dịch. Đừng bỏ annotation chỉ để che lỗi: hãy xem tên, kiểu tham số, kiểu trả về và quan hệ kế thừa/triển khai.

`@Override` cũng không biến lời gọi static thành đa hình. Sự phân giải **overload** dựa vào kiểu biểu thức tại thời điểm biên dịch, trong khi **overriding** của phương thức instance hỗ trợ dispatch theo đối tượng thực lúc chạy.

## 5. `@Deprecated`: đánh dấu API cũ, chưa phải xóa API

~~~java
class LabelService {
    /**
     * @deprecated Use modernLabel(String) instead.
     */
    @Deprecated(since = "17", forRemoval = false)
    public String oldLabel(String value) {
        return "old:" + value;
    }

    public String modernLabel(String value) {
        return "new:" + value;
    }
}
~~~

Dù phương thức `oldLabel` đã được gắn `@Deprecated`, lời gọi vẫn có thể biên dịch và chạy khi API đó còn tồn tại:

~~~java
LabelService service = new LabelService();
System.out.println(service.modernLabel("Java")); // new:Java
System.out.println(service.oldLabel("Java"));    // old:Java
~~~

Trình biên dịch/công cụ có thể báo cảnh báo về cách dùng API deprecated; cảnh báo **không đồng nghĩa lỗi biên dịch**, và annotation không tự làm phương thức bị gỡ khỏi class.

Có thể đánh dấu deprecated cho **class**, **constructor**, **method**, **field** và các loại khai báo mà annotation cho phép. Vì người dùng API cần biết đường thay thế, nên viết tài liệu migration rõ ràng.

**Hai phần khác nhau:**

- `@Deprecated` là **annotation** có ý nghĩa được trình biên dịch/công cụ nhận biết.
- `@deprecated` viết thường trong `/** ... */` là **Javadoc tag**, để giải thích lý do và gợi ý API thay thế cho người đọc tài liệu.

Nên dùng **cả hai** khi xuất bản API cũ. Chỉ viết Javadoc `@deprecated` mà thiếu annotation sẽ không cung cấp đầy đủ tín hiệu deprecated chuẩn cho công cụ.

## 6. `since`, `forRemoval` và xử lý cảnh báo

Từ Java 9, `@Deprecated` có hai thành phần tùy chọn, đều dùng được trên Java 17:

| Cú pháp | Diễn giải |
| --- | --- |
| `@Deprecated` | Đánh dấu deprecated, không khai báo thêm thông tin |
| `@Deprecated(since = "17")` | Cho biết từ phiên bản được tác giả ghi là `17`; chuỗi này không tự xác minh version |
| `@Deprecated(forRemoval = true)` | API được đánh dấu là **dự định loại bỏ** trong tương lai |
| `@Deprecated(since = "17", forRemoval = false)` | Deprecated từ mốc mô tả `17`, chưa đánh dấu dự định loại bỏ |

`forRemoval = true` **không** cam kết API sẽ biến mất vào một ngày cố định. Nếu ứng dụng đang dùng API này, hãy xem tài liệu và kế hoạch migration của nhà cung cấp; tránh tự suy ra thời hạn loại bỏ.

Để xem thông tin cảnh báo deprecated khi tự biên dịch dự án:

~~~bash
javac -Xlint:deprecation -encoding UTF-8 AnnotationBasicsDemo.java
~~~

Cảnh báo của `javac` thuộc luồng thông báo biên dịch, **không** phải stdout của chương trình chạy, và nội dung có thể tùy vị trí file/JDK. `@SuppressWarnings("deprecation")` có thể tắt cảnh báo tương ứng trong phạm vi được gắn, nhưng nên dành cho trường hợp có lý do rõ ràng thay vì dùng để che việc đang phụ thuộc API cũ. `forRemoval=true` có thể tạo cảnh báo thuộc nhóm `removal` riêng.

## 7. Ví dụ Java 17+ tự chứa, chạy được

Tạo file **AnnotationBasicsDemo.java**. Những khai báo trong một file sau đều biên dịch hợp lệ:

~~~java
public final class AnnotationBasicsDemo {
    private interface Audible {
        String sound();
    }

    private static class Animal {
        public String speak() {
            return "generic";
        }

        public String kind() {
            return "ANIMAL";
        }
    }

    private static final class Dog extends Animal {
        private final String name;

        Dog(String name) {
            this.name = name;
        }

        @Override
        public String speak() {
            return "woof";
        }

        @Override
        public String kind() {
            return "DOG";
        }

        @Override
        public String toString() {
            return "Dog(" + name + ")";
        }

        // Cùng tên nhưng tham số khác: overload, không phải override.
        public String speak(int times) {
            return "woof x" + times;
        }
    }

    private static final class Bird implements Audible {
        @Override
        public String sound() {
            return "chirp";
        }
    }

    private static final class LegacyApi {
        /**
         * @deprecated Use modernLabel(String) instead.
         */
        @Deprecated(since = "17", forRemoval = false)
        public String oldLabel(String value) {
            return "old:" + value;
        }

        public String modernLabel(String value) {
            return "new:" + value;
        }
    }

    public static void main(String[] args)
            throws NoSuchMethodException {
        Animal base = new Animal();
        Animal pet = new Dog("Rex");
        Dog dog = new Dog("Rex");
        Audible bird = new Bird();
        LegacyApi labels = new LegacyApi();

        System.out.println("parent=" + base.speak());
        System.out.println("override=" + dog.speak());
        System.out.println("dynamic=" + pet.speak());
        System.out.println("kind=" + pet.kind());
        System.out.println("interface=" + bird.sound());
        System.out.println("toString=" + dog.toString());
        System.out.println("overload=" + dog.speak(2));

        System.out.println("modern=" + labels.modernLabel("Java"));
        System.out.println("legacy=" + labels.oldLabel("Java"));

        // Minh họa annotation Deprecated có thể đọc bằng reflection.
        Deprecated info = LegacyApi.class
                .getDeclaredMethod("oldLabel", String.class)
                .getAnnotation(Deprecated.class);
        System.out.println("hasDeprecated=" + (info != null));
        System.out.println("since=" + info.since());
        System.out.println("forRemoval=" + info.forRemoval());
    }
}
~~~

Biên dịch/chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 AnnotationBasicsDemo.java
java AnnotationBasicsDemo
~~~

**Expected stdout chính xác khi chạy `java AnnotationBasicsDemo`:**

~~~text
parent=generic
override=woof
dynamic=woof
kind=DOG
interface=chirp
toString=Dog(Rex)
overload=woof x2
modern=new:Java
legacy=old:Java
hasDeprecated=true
since=17
forRemoval=false
~~~

Giải thích: `dynamic=woof` và `kind=DOG` là ghi đè động qua biến kiểu `Animal`; `interface=chirp` minh họa triển khai interface; `overload=woof x2` gọi hàm khác danh sách tham số, không phải một override. `legacy` vẫn hoạt động mặc dù API cũ bị đánh dấu deprecated.

Ba dòng cuối dùng **reflection** của JDK để đọc `@Deprecated`; đây là phần quan sát bổ sung, không bắt buộc để hiểu API. `@Override` chủ yếu hỗ trợ kiểm tra lúc biên dịch và không dùng theo cách quan sát reflection này.

## 8. Lỗi thường gặp, checklist và bài tập

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| Phải viết `@Override` thì đa hình mới hoạt động | Không: annotation giúp kiểm tra ý định ghi đè |
| `@Override` được đặt trên mọi overload | Sai: overload khác danh sách tham số không phải override |
| Ghi đè static method như instance method | Sai: static method có thể bị che khuất, không override |
| Có thể `@Override` trên constructor | Không: constructor không phải phương thức ghi đè |
| `@Deprecated` khiến code không chạy | Không: vẫn chạy nếu API còn và lời gọi hợp lệ |
| `@Deprecated` tự cung cấp API thay thế | Không: cần đọc Javadoc/changelog để migration |
| Javadoc `@deprecated` và annotation `@Deprecated` là một | Khác nhau: tài liệu giải thích vs tín hiệu compiler/tool |
| `forRemoval=true` nghĩa là đã bị xóa | Chỉ là ý định loại bỏ; chưa phải xóa tức thời |
| Tắt mọi cảnh báo deprecated để code “sạch” | Nên xác định API thay thế và lộ trình migration |

**Bài 1.** Tạo lớp `Vehicle` có `move()`, lớp `Bike` ghi đè với `@Override`; gọi qua biến `Vehicle` để thấy dispatch theo đối tượng thực.

**Bài 2.** Cố ý viết sai `move` thành `mvoe` và giữ `@Override`. Ghi lại lỗi biên dịch; sửa tên để thành override hợp lệ.

**Bài 3.** Tạo interface `Greeting` có `hello()`, lớp triển khai ghi `@Override`. Vì sao phương thức triển khai phải có quyền truy cập phù hợp?

**Bài 4.** Đánh dấu phương thức `oldSum(int,int)` bằng `@Deprecated(since = "17", forRemoval = false)`, viết `sum(int,int)` thay thế và thêm Javadoc hướng dẫn chuyển đổi.

**Bài 5.** Thử `javac -Xlint:deprecation`, xác định nguồn cảnh báo. Giải thích tại sao dù có cảnh báo, `java` vẫn có thể gọi phương thức cũ.

### Ghi nhớ

**`@Override` cho biết “tôi định ghi đè một phương thức hợp lệ” và nhờ compiler bắt sai chữ ký. `@Deprecated` cho biết “API này không còn được khuyến nghị” nhưng không tự xóa hay ngăn runtime gọi nó.** Dùng Javadoc `@deprecated` để hướng dẫn thay thế; phân biệt overriding, overloading và static hiding; xử lý cảnh báo bằng hiểu rõ API thay vì chỉ che chúng.


</details>

- [Quay lại đầu trang](#back-to-top)
