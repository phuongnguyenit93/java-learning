<a id="back-to-top"></a>

# Lớp trừu tượng

## Menu
- [Cú pháp khai báo abstract class](#abstract-classes-001)
- [Vì sao không thể khởi tạo trực tiếp lớp trừu tượng](#abstract-classes-002)
- [Lớp trừu tượng có hoặc không có phương thức abstract](#abstract-classes-003)
- [Thuộc tính và trạng thái dùng chung của các lớp con](#abstract-classes-004)
- [Phương thức có thân và mã triển khai dùng chung](#abstract-classes-005)
- [Thành viên static, final và private trong lớp trừu tượng](#abstract-classes-006)
- [Constructor và lựa chọn phạm vi truy cập](#abstract-classes-007)
- [Chuỗi kế thừa qua nhiều lớp trừu tượng](#abstract-classes-008)
- [Lớp con cụ thể và trách nhiệm hoàn thiện triển khai](#abstract-classes-009)
- [Tổ hợp modifier hợp lệ và xung đột abstract với final](#abstract-classes-010)

## <a id="abstract-classes-001">Cú pháp khai báo abstract class</a>

<details>
<summary>Xem chi tiết</summary>

# Cú pháp khai báo `abstract class` trong Java

## 1. Ý nghĩa của `abstract class`

`abstract class` (lớp trừu tượng) là lớp được đánh dấu bằng từ khóa `abstract`. Lớp này có thể cung cấp dữ liệu và hành vi dùng chung cho các lớp con, đồng thời có thể khai báo những phương thức mà lớp con phải hoàn thiện.

Hai quy tắc cơ bản:

- **Không thể tạo đối tượng trực tiếp** bằng `new` từ lớp `abstract`. Tuy nhiên, có thể khai báo biến, tham số hoặc kiểu trả về là lớp trừu tượng và dùng chúng để tham chiếu đến đối tượng của lớp con cụ thể.
- **Lớp trừu tượng không bắt buộc có phương thức trừu tượng.** Ngược lại, bất kỳ lớp nào trực tiếp khai báo một phương thức `abstract` đều phải được khai báo `abstract`.

Dùng lớp trừu tượng khi các lớp con chia sẻ trạng thái, constructor hoặc cách xử lý chung nhưng một số hành vi vẫn khác nhau theo từng lớp con.

## 2. Mẫu cú pháp khai báo

Mẫu tổng quát (các phần trong dấu `[ ]` là tùy chọn, không phải mã Java để biên dịch nguyên dạng):

~~~text
[access-modifier] abstract class ClassName
        [extends ParentClass]
        [implements Interface1, Interface2, ...] {
    // fields, constructors, concrete methods, abstract methods...
}
~~~

Ví dụ đơn giản nhất:

~~~java
abstract class Shape {
}
~~~

Ví dụ có phương thức trừu tượng:

~~~java
abstract class Shape {
    abstract double area();
}
~~~

Phần `abstract double area();` **không có thân hàm** và kết thúc bằng dấu chấm phẩy `;`. Phương thức đang có phạm vi package-private. Với API cần được lớp con thuộc package khác triển khai, thường dùng `public` hoặc `protected`.

| Thành phần | Ý nghĩa và quy tắc |
|---|---|
| `abstract` | Từ khóa đánh dấu lớp trừu tượng; đứng trước `class`. |
| `class ClassName` | Tên lớp tuân theo cú pháp đặt tên lớp Java. |
| `public` (tùy chọn) | Lớp top-level có thể khai báo `public` hoặc không ghi modifier (package-private). |
| `extends ParentClass` | Kế thừa tối đa **một** lớp cha. |
| `implements Interface1, ...` | Có thể thực hiện **nhiều** interface, tách bằng dấu phẩy. |
| `{ ... }` | Thân lớp có thể chứa field, constructor, phương thức thường, phương thức trừu tượng và các thành viên hợp lệ khác. |

**Quy tắc về modifier và tệp:** lớp top-level không được khai báo `private` hoặc `protected`; hai modifier này chỉ có thể dùng cho lớp lồng trong khi hợp lệ. Nếu lớp top-level khai báo `public abstract class Shape`, mã phải nằm trong `Shape.java`. Viết `public abstract class` là quy ước thường gặp; `abstract public class` cũng hợp lệ, nhưng ít được dùng.

## 3. Ví dụ hoàn chỉnh chạy với Java 17+

Lớp `Vehicle` khai báo trường dữ liệu, constructor, phương thức đã cài đặt và phương thức trừu tượng. `ElectricCar` triển khai phương thức trừu tượng và trở thành lớp cụ thể.

Lưu toàn bộ mã sau vào **`Demo.java`**:

~~~java
abstract class Vehicle {
    private final String brand;

    protected Vehicle(String brand) {
        this.brand = brand;
    }

    public final String getBrand() {
        return brand;
    }

    public abstract String start();

    public void stop() {
        System.out.println("Xe đã dừng.");
    }
}

final class ElectricCar extends Vehicle {
    public ElectricCar(String brand) {
        super(brand);
    }

    @Override
    public String start() {
        return getBrand() + " khởi động bằng điện.";
    }
}

public class Demo {
    public static void main(String[] args) {
        Vehicle vehicle = new ElectricCar("VinFast");
        System.out.println(vehicle.start());
        vehicle.stop();
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac Demo.java
java Demo
~~~

Kết quả stdout chính xác:

~~~text
VinFast khởi động bằng điện.
Xe đã dừng.
~~~

Giải thích:

1. `abstract class Vehicle` là lớp cha trừu tượng, **vẫn có thể** có field `brand` và phương thức đã cài đặt `stop()`.
2. `protected Vehicle(String brand)` là constructor hợp lệ. Lệnh `super(brand)` từ constructor `ElectricCar` gọi constructor lớp cha và khởi tạo trạng thái chung.
3. `public abstract String start();` khai báo phương thức instance chưa có thân. Lớp con cụ thể `ElectricCar` ghi đè phương thức này bằng triển khai hoàn chỉnh.
4. `@Override` giúp trình biên dịch kiểm tra việc ghi đè. Phương thức `getBrand()` được khai báo `final`, nên các lớp con không được ghi đè chính phương thức đó.
5. `final class ElectricCar` vẫn hợp lệ: modifier `final` áp dụng cho **lớp con cụ thể**, không áp dụng chung với `abstract` trên cùng một lớp.
6. Biến `Vehicle vehicle` dùng kiểu tham chiếu trừu tượng nhưng giữ đối tượng `ElectricCar`. Lời gọi `vehicle.start()` chạy phiên bản của `ElectricCar`, thể hiện đa hình.

Thử thay `new ElectricCar("VinFast")` bằng `new Vehicle("VinFast")` sẽ gây lỗi biên dịch, vì không thể khởi tạo lớp trừu tượng trực tiếp.

## 4. Cú pháp phương thức `abstract`

Trong lớp trừu tượng, có thể khai báo các phương thức instance `abstract`:

~~~java
abstract class Task {
    protected abstract void execute();

    public abstract int priority();

    public void printStatus() {
        System.out.println("Ready");
    }
}
~~~

`execute()` và `priority()` chỉ có chữ ký và dấu `;`, trong khi `printStatus()` có thân bình thường. Chú ý các ràng buộc:

- Phương thức `abstract` **không có dấu ngoặc nhọn thân hàm**. Muốn có thân, bỏ từ khóa `abstract` khỏi phương thức.
- Phương thức `abstract` không thể kết hợp với `private`, `static` hoặc `final`: `private` không cho phép ghi đè, `static` không phải phương thức instance được override, và `final` cấm ghi đè. `abstract` cũng không kết hợp với modifier `native` hoặc `synchronized` trên cùng phương thức.
- Khi override, phương thức triển khai không được thu hẹp quyền truy cập. Ví dụ, một phương thức `protected abstract` ở lớp cha có thể được lớp con override thành `protected` hoặc `public`.
- Nếu lớp con không khai báo `abstract`, nó phải hiện thực tất cả phương thức trừu tượng còn thiếu trong chuỗi kế thừa, trừ các phương thức đã có một triển khai cụ thể kế thừa phù hợp.
- Lớp `abstract` có thể chứa phương thức thường, phương thức `static` có thân và phương thức `final` có thân; chỉ riêng **một phương thức** không thể vừa `abstract` vừa `static` hoặc `final`.

## 5. Cú pháp và nghĩa vụ của lớp con

Khai báo `abstract class` cho phép một lớp giữ lại những phương thức trừu tượng được kế thừa trong khi lớp trung gian tiếp tục định nghĩa trạng thái hoặc hành vi dùng chung. Lớp chỉ được bỏ `abstract` khi mọi nghĩa vụ còn thiếu đã có triển khai cụ thể hợp lệ. Phần theo dõi nghĩa vụ qua nhiều tầng được giải thích ở bài về chuỗi kế thừa; ở đây không cần lặp lại một ví dụ kế thừa khác.

## 6. Kết hợp `extends`, `implements` và kiểu generic

`extends` phải nằm **trước** `implements`. Một lớp `abstract` có thể không triển khai hết các phương thức do interface yêu cầu:

~~~java
interface Printable {
    void print();
}

interface Resettable {
    void reset();
}

class Parent {
    void ping() {
        System.out.println("pong");
    }
}

abstract class AbstractPrinter extends Parent
        implements Printable, Resettable {

    @Override
    public void print() {
        System.out.println("print");
    }

    // reset() chưa được triển khai.
}
~~~

`AbstractPrinter` vẫn hợp lệ vì được khai báo `abstract`. Lớp con **cụ thể** phải cài đặt `reset()` nếu không có triển khai phù hợp nào được kế thừa. Lưu ý phương thức cài đặt cho một phương thức `public` trong interface cũng phải khai báo `public`.

Một lớp `abstract` có thể dùng tham số kiểu như lớp Java thông thường:

~~~java
abstract class Repository<T> {
    abstract T findById(long id);
}
~~~

`T` là tham số kiểu; tính chất generic không làm thay đổi cách khai báo `abstract class` hoặc quy tắc phương thức trừu tượng.

## 7. Lỗi cú pháp thường gặp

| Khai báo sai | Vì sao sai | Cách sửa |
|---|---|---|
| `class Shape abstract {}` | Modifier đặt sai vị trí. | `abstract class Shape {}` |
| `abstract class {}` | Thiếu tên lớp. | `abstract class Shape {}` |
| `class Shape { abstract double area(); }` | Lớp trực tiếp khai báo phương thức `abstract` nhưng không phải lớp `abstract`. | Thêm `abstract` vào lớp hoặc cung cấp thân cho phương thức. |
| `abstract class Shape { abstract double area() {} }` | Phương thức `abstract` không được có thân. | Kết thúc khai báo bằng `;` hoặc bỏ `abstract` khỏi phương thức. |
| `private abstract class Shape {}` ở cấp top-level | Lớp top-level không được khai báo `private` hoặc `protected`. | Dùng package-private hoặc `public`. |

Nếu lớp top-level là `public`, tên tệp nguồn phải khớp với tên lớp. Các tổ hợp modifier mâu thuẫn như `abstract final` được phân tích riêng trong bài về modifier; không cần lặp lại toàn bộ ma trận ở bài cú pháp.

## 8. Ghi nhớ và áp dụng

Khi khai báo một lớp cha trừu tượng, hãy xác định phần **dùng chung** (field, constructor, phương thức thường) và phần **mỗi lớp con phải quyết định** (phương thức `abstract`). Sau đó kiểm tra lớp con cụ thể đã triển khai đủ các phương thức còn thiếu.

Mẫu ngắn để ghi nhớ:

~~~java
public abstract class AbstractService {
    protected AbstractService() {
    }

    public abstract void execute();

    public void printStatus() {
        System.out.println("Ready");
    }
}
~~~

Đây là khai báo lớp hợp lệ nếu lưu ở `AbstractService.java`; không có `main()` để chạy riêng. Lớp có constructor, phương thức thường có thân và phương thức trừu tượng kết thúc bằng `;`.

Nếu chỉ muốn quy định hợp đồng cho nhiều kiểu không chung trạng thái hoặc hệ kế thừa, cân nhắc `interface`. Java interface cũng có thể có phương thức `default` và `static` có thân, nên không nên phân biệt hai loại bằng kết luận đơn giản rằng interface “không chứa code”.

**Cốt lõi của cú pháp:** `abstract` đặt trước `class` để khai báo lớp trừu tượng; `abstract` đặt trên phương thức để yêu cầu lớp con hiện thực; phương thức này kết thúc bằng `;`; và `new` chỉ dùng với một lớp cụ thể có thể khởi tạo.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-classes-002">Vì sao không thể khởi tạo trực tiếp lớp trừu tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao không thể khởi tạo trực tiếp lớp trừu tượng trong Java?

## 1. Trả lời trực tiếp

Trong Java, một lớp khai báo bằng từ khóa `abstract` **không được dùng trực tiếp ở vế phải của `new`**. Ví dụ, nếu `Animal` là lớp trừu tượng thì `new Animal()` gây **lỗi biên dịch**, ngay cả khi lớp đó có constructor và ngay cả khi lớp đó không khai báo phương thức `abstract` nào.

Lý do nằm ở **hợp đồng của kiểu dữ liệu**: lớp trừu tượng mô tả một kiểu chung, dành cho các lớp con kế thừa và hoàn thiện hành vi. Một lớp trừu tượng có thể chứa các phương thức chưa có phần thân, nên Java không cho phép tạo trực tiếp một đối tượng thuộc chính lớp đó. Quy tắc này được kiểm tra dựa trên khai báo `abstract`, chứ không phụ thuộc vào việc một lớp cụ thể có bao nhiêu phương thức trừu tượng.

Muốn có đối tượng, hãy tạo **một lớp con cụ thể (concrete class)** đáp ứng toàn bộ các phương thức trừu tượng còn thiếu. Biến tham chiếu vẫn có thể mang kiểu lớp trừu tượng:

```java
Animal pet = new Cat("Mimi"); // Hợp lệ nếu Cat là lớp con cụ thể của Animal
// Animal pet = new Animal("Mimi"); // Không hợp lệ: Animal là abstract
```

Ở dòng hợp lệ, **kiểu khai báo của biến** là `Animal`, nhưng **đối tượng thực sự được tạo** có kiểu `Cat`.

## 2. Điều gì xảy ra khi dùng `new` với lớp trừu tượng?

Giả sử ta có một khuôn mẫu chung cho các loài động vật:

```java
abstract class Animal {
    abstract String sound();
}

class InvalidExample {
    void demo() {
        Animal a = new Animal(); // Lỗi biên dịch
    }
}
```

Nếu lưu đoạn trên thành `InvalidExample.java` rồi chạy `javac InvalidExample.java`, trình biên dịch từ chối biểu thức `new Animal()` và thông báo theo ý nghĩa: `Animal is abstract; cannot be instantiated`.

Tại đây, `sound()` chỉ nêu **hành vi phải có** mà chưa quy định **hành vi cụ thể**. Nếu `new Animal()` được phép, không có phần cài đặt nào của `sound()` dành cho đối tượng thuộc chính lớp `Animal`. Vì vậy, thiết kế yêu cầu chương trình cung cấp một lớp con hoàn chỉnh trước khi tạo đối tượng.

Cần phân biệt hai mức:

- **Nguyên nhân trong ví dụ:** `Animal` chưa cài đặt `sound()`.
- **Quy tắc của ngôn ngữ:** mọi lớp được khai báo `abstract` đều không thể khởi tạo trực tiếp, kể cả khi tất cả phương thức đã có phần thân.

Đây là **lỗi tại thời điểm biên dịch** trong trường hợp viết trực tiếp `new Animal()`; chương trình không cần chạy đến dòng đó mới phát hiện lỗi.

## 3. Lớp trừu tượng không nhất thiết có phương thức trừu tượng

Điều dễ gây nhầm lẫn là từ khóa `abstract` tự nó đã đủ để cấm khởi tạo trực tiếp:

```java
abstract class BaseConfiguration {
    String environment() {
        return "production";
    }
}

class InvalidExample {
    void demo() {
        BaseConfiguration config = new BaseConfiguration(); // Vẫn lỗi biên dịch
    }
}
```

`BaseConfiguration` không có phương thức trừu tượng; tuy nhiên, người thiết kế vẫn muốn nó đóng vai trò **lớp nền để mở rộng**, không phải một kiểu được tạo độc lập. Khai báo `abstract` thể hiện chủ đích này và trình biên dịch thực thi đúng quy tắc.

Ngược lại, một lớp **không khai báo `abstract`** thì không được để lại phương thức trừu tượng chưa được cài đặt (trực tiếp hoặc kế thừa). Nếu còn hành vi bắt buộc chưa hoàn thiện, lớp con cũng phải tiếp tục được khai báo `abstract`.

## 4. Cách đúng: khởi tạo lớp con cụ thể

Ví dụ sau là chương trình Java độc lập, chạy được trên Java 17 trở lên. Lớp `Animal` đặt ra hợp đồng `sound()`; lớp `Cat` cài đặt hợp đồng ấy.

**Tệp `Main.java`:**

```java
abstract class Animal {
    private final String name;

    protected Animal(String name) {
        this.name = name;
        System.out.println("Animal constructor: " + name);
    }

    protected abstract String sound();

    public String introduce() {
        return name + " says " + sound();
    }
}

final class Cat extends Animal {
    Cat(String name) {
        super(name);
    }

    @Override
    protected String sound() {
        return "meow";
    }
}

public class Main {
    public static void main(String[] args) {
        Animal pet = new Cat("Mimi");

        System.out.println(pet.introduce());
        System.out.println(pet instanceof Cat);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 Main.java
java Main
```

**Kết quả chuẩn trên stdout:**

```text
Animal constructor: Mimi
Mimi says meow
true
```

Phân tích từng điểm:

1. `new Cat("Mimi")` tạo **đối tượng `Cat`**, vì `Cat` không trừu tượng và đã cài đặt `sound()`.
2. `Animal pet` hoàn toàn hợp lệ: một tham chiếu kiểu cha có thể trỏ đến đối tượng kiểu con (đa hình).
3. Lệnh `pet.introduce()` gọi phương thức kế thừa từ `Animal`; lời gọi `sound()` bên trong được phân phối đến phiên bản của `Cat`, trả về `"meow"`.
4. Constructor `Animal(String)` vẫn chạy thông qua `super(name)` khi khởi tạo `Cat`. Dòng đầu tiên trên stdout chứng minh việc đó.
5. `pet instanceof Cat` trả về `true` vì kiểu thực tế của đối tượng là `Cat`.

Nếu bỏ phần cài đặt `sound()` trong `Cat` mà vẫn giữ `Cat` là lớp cụ thể, chương trình sẽ không biên dịch. Khi đó, phải cài đặt `sound()` hoặc khai báo `Cat` là `abstract` và tiếp tục để lớp con sau hoàn thiện.

## 5. Tại sao lớp trừu tượng vẫn có constructor?

Constructor của lớp trừu tượng khởi tạo phần trạng thái mà mọi lớp con chia sẻ. Khi tạo `new Cat("Mimi")`, Java gọi constructor `Animal` thông qua `super(name)`; điều đó không có nghĩa là một đối tượng `Animal` riêng được tạo ra, và cũng không làm `new Animal(...)` trở nên hợp lệ.

Constructor không được kế thừa và không thể khai báo `abstract`. Nếu lớp cha chỉ có constructor nhận tham số, constructor lớp con phải gọi một constructor tương ứng bằng `super(...)`; nếu không, lời gọi `super()` ngầm định có thể gây lỗi biên dịch. Quy tắc chọn access modifier, chuỗi `this(...)`/`super(...)` và thứ tự khởi tạo được trình bày đầy đủ trong bài về constructor.

## 6. Vì sao cú pháp `new Animal() { ... }` có thể hợp lệ?

Cú pháp có dấu ngoặc nhọn sau `new` tạo **lớp con vô danh (anonymous class)**. Nó không tạo đối tượng thuộc trực tiếp lớp `abstract`.

**Ví dụ độc lập khác, lưu lại dưới tên `Main.java`:**

```java
abstract class Action {
    abstract String execute();
}

public class Main {
    public static void main(String[] args) {
        Action action = new Action() {
            @Override
            String execute() {
                return "Action completed";
            }
        };

        System.out.println(action.execute());
    }
}
```

Chạy `javac -encoding UTF-8 Main.java` rồi `java Main` sẽ in:

```text
Action completed
```

Biểu thức `new Action() { ... }` khai báo ngay tại chỗ một lớp con vô danh kế thừa `Action`, cài đặt `execute()`, sau đó tạo đối tượng của **lớp con vô danh** ấy. Nếu không cài đặt đủ phương thức trừu tượng còn thiếu, biểu thức này cũng không biên dịch.

Vì vậy, hai biểu thức trông gần giống nhau nhưng có ý nghĩa khác nhau:

| Biểu thức | Kết quả | Giải thích |
| --- | --- | --- |
| `new Action()` | Lỗi biên dịch | Cố tạo trực tiếp lớp trừu tượng |
| `new Action() { /* cài đặt đầy đủ */ }` | Hợp lệ | Tạo lớp con vô danh cụ thể rồi khởi tạo nó |
| `new ConcreteAction()` | Hợp lệ nếu lớp con cụ thể và constructor truy cập được | Tạo đối tượng lớp con có tên |

Lớp con vô danh hữu ích cho triển khai ngắn tại một vị trí. Khi hành vi lớn, cần tái sử dụng hoặc có nhiều phụ thuộc, một lớp con có tên thường dễ đọc và kiểm thử hơn.

## 7. Cơ chế thiết kế và các trường hợp cần phân biệt

**Lớp trừu tượng là kiểu có thể sử dụng trong khai báo, nhưng không là đích khởi tạo trực tiếp.** Java cho phép dùng lớp trừu tượng làm kiểu của biến, tham số, kiểu trả về, trường dữ liệu và phần tử của mảng. Chẳng hạn, một phương thức có thể nhận `Animal` để xử lý mọi lớp con mà không phụ thuộc vào `Cat`, `Dog` hay một triển khai cụ thể nào.

Một số trường hợp thường gây nhầm lẫn:

| Tình huống | Hợp lệ? | Lý do |
| --- | --- | --- |
| `Animal a;` | Có | Chỉ khai báo tham chiếu, chưa tạo đối tượng |
| `Animal a = null;` | Có | `null` không phải đối tượng của lớp trừu tượng |
| `Animal a = new Cat("Mimi");` | Có | Đối tượng được tạo thuộc lớp con cụ thể |
| `Animal[] animals = new Animal[3];` | Có | Tạo một **mảng chứa tham chiếu** kiểu `Animal`, không tạo ba đối tượng `Animal` |
| `new Animal("Mimi")` | Không | Khởi tạo trực tiếp lớp được khai báo `abstract` |
| `new Cat("Mimi")` khi `Cat` chưa cài đặt đủ phương thức trừu tượng | Không, nếu `Cat` bị buộc phải là lớp trừu tượng | Lớp con chưa đủ điều kiện để khởi tạo |

Có thể hình dung lớp trừu tượng như một **hợp đồng kèm phần triển khai dùng chung**. Hợp đồng cho biết mọi lớp con cụ thể phải cung cấp những hành vi nào; phần triển khai dùng chung tránh lặp mã. Các đối tượng thực tế mang kiểu của những lớp con đáp ứng hợp đồng.

Tính trừu tượng cũng có thể dùng để **ngăn tạo lớp nền** ngay cả khi mọi hành vi đã được cài đặt. Vì thế, lời giải thích “vì còn phương thức thiếu phần thân” nêu một động cơ phổ biến, nhưng **không phải điều kiện cần** để một lớp được khai báo `abstract`.

### Những lỗi phổ biến

- **Nhầm “không tạo được đối tượng” với “không khai báo được biến”.** `Animal pet` hợp lệ; chính `new Animal()` mới bị cấm.
- **Tưởng thêm constructor công khai sẽ cho phép `new`.** Quyền truy cập constructor và quy tắc `abstract` là hai kiểm tra khác nhau; constructor `public` không biến lớp trừu tượng thành lớp cụ thể.
- **Tưởng mọi lớp con đều có thể khởi tạo.** Chỉ lớp con cụ thể đã thực hiện đủ hợp đồng trừu tượng và có constructor phù hợp mới được tạo đối tượng.
- **Tưởng lớp cha trừu tượng không có constructor.** Constructor của lớp cha vẫn được gọi trong quá trình tạo đối tượng lớp con.
- **Tưởng `new AbstractType() { ... }` phá quy tắc.** Đây là cách định nghĩa và tạo một lớp con vô danh cụ thể; lớp cha vẫn không được khởi tạo trực tiếp.
- **Tưởng một lớp chỉ trừu tượng khi có phương thức `abstract`.** Người viết có thể đánh dấu lớp `abstract` để quy định cách sử dụng, bất kể lớp ấy có phương thức trừu tượng hay không.

## 8. Cách tự kiểm tra khi gặp lỗi

Khi thấy lỗi gần với `SomeType is abstract; cannot be instantiated`, hãy lần lượt xem:

1. **Kiểu ngay sau `new` là gì?** Nếu là lớp `abstract` hoặc interface, không thể dùng `new SomeType()` trực tiếp.
2. **Có lớp con cụ thể phù hợp chưa?** Nếu có, dùng `new ConcreteSubtype(...)` rồi giữ biến tham chiếu kiểu cha nếu cần tính đa hình.
3. **Lớp con đã cài đặt đủ các phương thức trừu tượng chưa?** Kiểm tra cả phương thức nhận từ cha và các interface, chữ ký phương thức, kiểu trả về và mức truy cập khi override.
4. **Constructor lớp con có gọi được constructor lớp cha không?** Nếu lớp cha yêu cầu tham số, hãy truyền qua `super(...)`.
5. **Chỉ cần một triển khai ngắn tại chỗ?** Cân nhắc lớp con vô danh, nhưng vẫn phải cài đặt đầy đủ hành vi trừu tượng.

**Ghi nhớ:** `abstract` mô tả kiểu cha và quy định rằng không được khởi tạo trực tiếp kiểu ấy. Đối tượng phải thuộc một lớp con cụ thể; constructor và phương thức dùng chung của lớp trừu tượng vẫn có vai trò đầy đủ trong quá trình khởi tạo và thực thi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-classes-003">Lớp trừu tượng có hoặc không có phương thức abstract</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp trừu tượng có hoặc không có phương thức abstract trong Java

Trong Java, từ khóa `abstract` trên **lớp** và trên **phương thức** có hai ý nghĩa liên quan nhưng không đồng nhất:

- **Lớp `abstract`**: là lớp không thể được khởi tạo trực tiếp bằng `new TênLớp(...)`. Nó vẫn có thể chứa trạng thái, constructor, phương thức có thân, phương thức `static` và phương thức `final`.
- **Phương thức `abstract`**: chỉ khai báo chữ ký, không có thân phương thức; lớp con cụ thể phải cung cấp cách triển khai, trừ khi việc triển khai đã được thừa hưởng hợp lệ từ một lớp cha khác.

**Quy tắc cốt lõi:** Một lớp khai báo phương thức `abstract` thì chính lớp đó phải khai báo `abstract`; chiều ngược lại **không bắt buộc**. Nghĩa là lớp `abstract` có thể có **0, 1 hoặc nhiều** phương thức `abstract`.

| Trường hợp | Hợp lệ? | Hệ quả |
| --- | --- | --- |
| `abstract class A` chỉ có phương thức có thân | Có | Không được `new A()`, nhưng lớp con cụ thể có thể kế thừa toàn bộ hành vi |
| `abstract class A` có phương thức `abstract` | Có | Lớp con cụ thể phải hoàn thiện các phương thức trừu tượng còn thiếu |
| `class A` trực tiếp khai báo phương thức `abstract` | Không | Lỗi biên dịch: lớp phải là `abstract` |
| Lớp con không `abstract` còn kế thừa phương thức `abstract` chưa triển khai | Không | Lớp con phải triển khai hoặc được khai báo `abstract` |

## 1. Lớp abstract không khai báo phương thức abstract

Một lớp có thể cung cấp đầy đủ hành vi mặc định nhưng vẫn được thiết kế để **chỉ tồn tại thông qua lớp con**. Ví dụ, mọi chứng từ đều có mã và cách mô tả chung, nhưng ứng dụng chỉ tạo các loại chứng từ cụ thể.

Tạo tệp **NoAbstractMethodDemo.java**:

~~~java
abstract class Document {
    private final String code;

    protected Document(String code) {
        this.code = code;
    }

    public final String code() {
        return code;
    }

    public String describe() {
        return "Document: " + code;
    }
}

final class Receipt extends Document {
    Receipt(String code) {
        super(code);
    }
}

public class NoAbstractMethodDemo {
    public static void main(String[] args) {
        Document doc = new Receipt("R-01");
        System.out.println(doc.code());
        System.out.println(doc.describe());
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac NoAbstractMethodDemo.java
java NoAbstractMethodDemo
~~~

Kết quả chính xác:

~~~text
R-01
Document: R-01
~~~

**Vì sao hợp lệ?** `Document` không khai báo một phương thức `abstract` nào. `Receipt` không phải ghi đè `code()` hay `describe()`, vì hai phương thức ấy đã có thân và được thừa hưởng. Constructor `Document(String)` vẫn được gọi thông qua `super(code)`; bản thân constructor không thể khai báo `abstract`.

Dù không có phương thức trừu tượng, `new Document("R-02")` vẫn **bị lỗi biên dịch** vì `Document` là lớp `abstract`. Một tham chiếu kiểu `Document` vẫn hoàn toàn hợp lệ: `Document doc = new Receipt("R-01");` — kiểu tham chiếu là lớp cha, đối tượng thật là `Receipt`.

Với lớp không chứa phương thức `abstract`, biểu thức `new Document("TMP") {}` cũng có thể hợp lệ: dấu `{}` tạo **lớp con ẩn danh** và khởi tạo lớp con đó, không phải tạo một thể hiện trực tiếp của `Document`. Cách này chỉ dùng được nếu constructor và các ràng buộc kế thừa cho phép.

**Khi nào hữu ích?**

- Muốn cấm việc khởi tạo kiểu nền quá chung chung và buộc ứng dụng dùng kiểu con có ý nghĩa nghiệp vụ.
- Muốn tập trung trường dữ liệu, constructor, kiểm tra bất biến và hành vi dùng chung cho một họ lớp.
- Muốn dành khả năng bổ sung điểm mở rộng ở lớp cha trong tương lai, nhưng hiện tại chưa cần một phương thức `abstract`.

Đừng dùng `abstract` chỉ để đặt các hàm tiện ích `static`: một lớp tiện ích `final` với constructor `private` thường thể hiện ý định rõ hơn.

## 2. Lớp abstract có phương thức abstract

Khi mọi lớp con phải tự định nghĩa một phần hành vi, phương thức `abstract` là **hợp đồng bắt buộc**. Phần hành vi đã biết có thể được viết sẵn trong lớp cha và gọi tới phương thức trừu tượng nhờ đa hình.

Tạo tệp **WithAbstractMethodDemo.java**:

~~~java
abstract class Figure {
    private final String name;

    protected Figure(String name) {
        this.name = name;
    }

    abstract double area();

    String summary() {
        return name + " area = " + area();
    }
}

final class Square extends Figure {
    private final double side;

    Square(double side) {
        super("Square");
        this.side = side;
    }

    @Override
    double area() {
        return side * side;
    }
}

public class WithAbstractMethodDemo {
    public static void main(String[] args) {
        Figure figure = new Square(3);
        System.out.println(figure.summary());
        System.out.println(figure.area());
    }
}
~~~

~~~bash
javac WithAbstractMethodDemo.java
java WithAbstractMethodDemo
~~~

Kết quả chính xác:

~~~text
Square area = 9.0
9.0
~~~

Phương thức `Figure.area()` chỉ có chữ ký và dấu `;`, **không có `{ ... }`**. Lớp `Square` dùng `@Override` và cung cấp thân phương thức. Khi `summary()` gọi `area()` trên một đối tượng `Square`, Java thực thi `Square.area()` theo cơ chế **dynamic dispatch** (điều phối động). Đây cũng là cách lớp cha chia sẻ quy trình nhưng để lớp con quyết định một bước trong quy trình.

Ví dụ trên dùng quyền truy cập package-private cho `area()`; khi ghi đè, không được giảm mức truy cập. Trong API dùng giữa nhiều package, có thể chủ động khai báo `public abstract double area();` và ghi đè bằng `public double area()`.

## 3. Lớp con abstract và lớp con cụ thể

Một lớp con vẫn được khai báo `abstract` nếu chưa triển khai hết phương thức trừu tượng kế thừa. Đoạn sau chỉ minh họa quan hệ kế thừa; không phải chương trình có `main`:

~~~java
abstract class Writer {
    abstract String write();
}

abstract class NamedWriter extends Writer {
    String name() {
        return "named";
    }
    // Vẫn để lại write() chưa triển khai.
}

class PlainWriter extends NamedWriter {
    @Override
    String write() {
        return "ok";
    }
}
~~~

`NamedWriter` **không tự khai báo** phương thức `abstract` mới, nhưng **kế thừa** nghĩa vụ triển khai `write()` từ `Writer`; vì vậy nó vẫn phải là lớp `abstract`. `PlainWriter` là lớp cụ thể và đã triển khai `write()`, nên có thể khởi tạo.

Phân biệt chính xác hai câu sau:

- “Lớp này **không khai báo** phương thức abstract nào” không có nghĩa là toàn bộ cây kế thừa không còn phương thức abstract.
- “Lớp cụ thể có thể được tạo đối tượng” chỉ đúng khi mọi phương thức abstract được kế thừa đã có triển khai hợp lệ.

## 4. Những lỗi cần nhận diện

- Lớp khai báo trực tiếp một phương thức `abstract` thì bản thân lớp phải là `abstract`.
- Phương thức `abstract` chỉ có chữ ký và kết thúc bằng `;`; không được kèm thân `{ ... }`.
- Lớp con không `abstract` phải có triển khai hợp lệ cho mọi phương thức trừu tượng còn thiếu trong toàn bộ hệ phân cấp, kể cả nghĩa vụ kế thừa gián tiếp.
- Lớp trừu tượng có thể có phương thức thường, `static` hoặc `final` có thân; chi tiết về các tổ hợp modifier bị cấm thuộc bài chuyên về modifier.

## 5. Phân biệt với interface và cách chọn thiết kế

- **Abstract class**: phù hợp khi các lớp có quan hệ “là một loại của”, cần chia sẻ **trạng thái**, **constructor**, hành vi chung hoặc một số bước bắt buộc phải viết lại. Mỗi lớp Java chỉ được `extends` trực tiếp **một lớp**.
- **Interface**: phù hợp khi cần công bố một **khả năng/hợp đồng** mà nhiều lớp thuộc các hệ phân cấp khác nhau đều có thể đáp ứng. Một lớp có thể `implements` nhiều interface. Interface có thể chứa phương thức `abstract` (thường được ngầm hiểu là `public abstract`), đồng thời có `default`, `static` và `private` method theo quy tắc Java hiện đại.

Không phải cứ có phương thức abstract là cần chọn lớp cha abstract: nếu không cần chia sẻ trạng thái hay constructor và chỉ cần ràng buộc khả năng, interface thường đơn giản hơn. Nếu quan hệ kế thừa khiến lớp con phụ thuộc quá chặt vào chi tiết lớp cha, cân nhắc **composition** (chứa và ủy quyền cho đối tượng khác).

**Câu hỏi tự kiểm tra:** Một lớp `abstract` chỉ có phương thức có thân có hợp lệ không? **Có.** Lớp con cụ thể của nó có bắt buộc `@Override` phương thức nào không? **Không**, nếu không có nghĩa vụ abstract kế thừa khác. Thêm một phương thức `abstract` vào lớp cha thì sao? **Lớp con cụ thể phải có triển khai hợp lệ**, trực tiếp hoặc kế thừa từ lớp trung gian đã triển khai.

**Điểm cần nhớ:** `abstract class` nói về **khả năng tạo đối tượng trực tiếp** và vai trò lớp nền; `abstract method` nói về **phần hành vi chưa được triển khai**. Hai khái niệm liên quan, nhưng lớp `abstract` không bắt buộc phải sở hữu phương thức `abstract`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-classes-004">Thuộc tính và trạng thái dùng chung của các lớp con</a>

<details>
<summary>Xem chi tiết</summary>

# Thuộc tính và trạng thái dùng chung của các lớp con trong lớp trừu tượng (Java)

## 1. Mục tiêu và ý nghĩa của trạng thái kế thừa

Một lớp trừu tượng (`abstract class`) trong Java có thể định nghĩa các **instance field** mà mọi lớp con đều sử dụng, kèm constructor, quy tắc kiểm tra dữ liệu và phương thức xử lý thống nhất. Ví dụ, `SavingsAccount` và `CheckingAccount` là hai loại tài khoản; cả hai đều có chủ sở hữu và số dư, dù chính sách rút tiền khác nhau.

Sau bài này, bạn cần phân biệt được:

- **Chung về cấu trúc và hành vi:** lớp cha khai báo một lần các trường và phương thức, lớp con thừa hưởng chúng.
- **Riêng theo đối tượng:** mỗi `new SavingsAccount(...)` hay `new CheckingAccount(...)` có bản trạng thái instance của **chính đối tượng đó**. Một tài khoản tăng số dư không làm tăng số dư tài khoản khác.
- **Chung thực sự giữa các đối tượng:** trường `static` thuộc về lớp khai báo, không phải bản riêng của mỗi đối tượng.

`abstract` không cấm lớp cha sở hữu trường, khởi tạo dữ liệu hoặc viết phương thức cụ thể. Nó chỉ cấm tạo trực tiếp instance của lớp abstract và cho phép khai báo phương thức abstract. Một lớp con cụ thể sẽ hoàn thành phần hành vi còn thiếu.

## 2. Khi nào nên đặt thuộc tính ở lớp cha?

Đưa dữ liệu vào lớp cha khi dữ liệu đó tồn tại với **cùng ý nghĩa** trong mọi lớp con thuộc hệ phân cấp, và có thể chia sẻ cùng cách kiểm tra hoặc quản lý. Giữ dữ liệu riêng tại subclass nếu chỉ subclass đó cần.

| Dữ liệu | Nên đặt ở đâu? | Giải thích |
| --- | --- | --- |
| `owner` (chủ tài khoản) | `Account` | Tài khoản nào cũng có chủ sở hữu |
| `balanceCents` (số dư theo cent) | `Account` | Tất cả cùng chịu quy tắc cập nhật số dư |
| `freeWithdrawals` (số lượt rút miễn phí) | `SavingsAccount` | Chỉ tài khoản tiết kiệm dùng |
| Thống kê tổng tài khoản | Thành phần quản lý riêng, khi thích hợp | Không phải trạng thái thuộc một tài khoản đơn lẻ |

Quy tắc đơn giản: **thuộc tính nào lớp con nào cũng phải có**, có cùng hợp đồng và bất biến, mới là ứng viên tốt để đưa lên lớp cha. Nếu thêm một subclass mới mà trường đó không có nghĩa đối với nó, hãy xem lại cấu trúc kế thừa.

## 3. Ví dụ đầy đủ Java 17: lớp cha giữ invariant, lớp con thêm quy tắc riêng

Ví dụ dùng `long` để lưu **cent** thay vì dùng `double` biểu diễn tiền. Cả hai loại tài khoản kế thừa cùng `owner` và `balanceCents`; lớp cha chịu trách nhiệm ngăn số dư âm, lớp con tự quyết định chính sách rút.

Tạo tệp **`Demo.java`**:

```java
public class Demo {
    public static void main(String[] args) {
        SavingsAccount savings = new SavingsAccount("An", 10_000, 2);
        CheckingAccount checking = new CheckingAccount("Bình", 20_000);

        savings.deposit(5_000);
        savings.withdraw(2_000);
        checking.withdraw(2_000);

        Account account = savings; // Cùng đối tượng, tham chiếu kiểu cha.

        System.out.println("Savings: " + savings.balanceCents()
                + " cents; free withdrawals: " + savings.freeWithdrawals());
        System.out.println("Checking: " + checking.balanceCents() + " cents");
        System.out.println("Account reference: " + account.owner()
                + " / " + account.balanceCents() + " cents");
    }
}

abstract class Account {
    private final String owner;
    private long balanceCents;

    protected Account(String owner, long openingCents) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner is required");
        }
        if (openingCents < 0) {
            throw new IllegalArgumentException("negative opening balance");
        }
        this.owner = owner;
        this.balanceCents = openingCents;
    }

    public final String owner() {
        return owner;
    }

    public final long balanceCents() {
        return balanceCents;
    }

    public final void deposit(long cents) {
        if (cents <= 0) {
            throw new IllegalArgumentException("deposit must be positive");
        }
        balanceCents = Math.addExact(balanceCents, cents);
    }

    // Thao tác cho lớp con sử dụng, luôn kiểm tra invariant.
    protected final void debit(long cents) {
        if (cents <= 0 || cents > balanceCents) {
            throw new IllegalArgumentException("invalid debit");
        }
        balanceCents -= cents;
    }

    public abstract void withdraw(long cents);
}

final class SavingsAccount extends Account {
    private int freeWithdrawals;

    SavingsAccount(String owner, long openingCents, int freeWithdrawals) {
        super(owner, openingCents);
        if (freeWithdrawals < 0) {
            throw new IllegalArgumentException("negative withdrawal quota");
        }
        this.freeWithdrawals = freeWithdrawals;
    }

    @Override
    public void withdraw(long cents) {
        if (freeWithdrawals == 0) {
            throw new IllegalStateException("no free withdrawals left");
        }
        debit(cents); // Chỉ giảm lượt sau khi trừ tiền thành công.
        freeWithdrawals--;
    }

    public int freeWithdrawals() {
        return freeWithdrawals;
    }
}

final class CheckingAccount extends Account {
    CheckingAccount(String owner, long openingCents) {
        super(owner, openingCents);
    }

    @Override
    public void withdraw(long cents) {
        if (cents <= 0) {
            throw new IllegalArgumentException("withdrawal must be positive");
        }
        debit(Math.addExact(cents, 100)); // Thu thêm phí 100 cents.
    }
}
```

Biên dịch và chạy (JDK 17+):

```bash
javac Demo.java
java Demo
```

Kết quả chính xác:

```text
Savings: 13000 cents; free withdrawals: 1
Checking: 17900 cents
Account reference: An / 13000 cents
```

**Cách đọc ví dụ:**

- `SavingsAccount` bắt đầu 10.000 cent, gửi 5.000, rút 2.000 ⇒ 13.000; hạn mức 2 lượt giảm còn 1.
- `CheckingAccount` bắt đầu 20.000, rút 2.000 và thêm phí 100 ⇒ 17.900. Hai đối tượng không sử dụng chung một trường số dư.
- `Account account = savings` không tạo tài khoản mới; nó là **tham chiếu khác đến cùng đối tượng** `savings`. Vì vậy `account.balanceCents()` nhìn thấy 13.000.
- Lớp `Account` quản lý trường `private long balanceCents`. Lớp con không gán trực tiếp vào nó, mà gọi `debit()`; logic kiểm tra luôn thống nhất.
- `owner` là trường `final` của mỗi instance; `freeWithdrawals` là trường riêng của subclass, không ép tài khoản thanh toán sở hữu nó.
- `withdraw()` được khai báo abstract ở cha và triển khai tại từng lớp con. Phương thức này là **phần hành vi thay đổi**, còn thao tác bảo vệ số dư là **phần hành vi dùng chung**.

Đây là ví dụ về thiết kế trạng thái và kế thừa. Hệ thống tiền tệ thực tế cần thêm các giao dịch nguyên tử, lưu trữ và kiểm soát đồng thời; mã trên không được xem là giải pháp giao dịch ngân hàng đầy đủ.

## 4. Vì sao ưu tiên trường `private` trong lớp abstract?

Lớp con thừa hưởng **phần dữ liệu nằm trong đối tượng**, ngay cả khi mã subclass không được truy cập trực tiếp một trường `private` của cha. Có thể cấp quyền **thực hiện hành vi** thông qua method, thay vì để lớp con sửa dữ liệu tùy ý.

| Modifier | Ai đọc/sửa trực tiếp được trường? | Khuyến nghị |
| --- | --- | --- |
| `private` | Mã trong lớp khai báo; subclass dùng API lớp cha | Nên dùng mặc định cho trạng thái mutable và bất biến cần bảo vệ |
| Không ghi modifier (*package-private*) | Mã trong cùng package | Dùng khi thật sự muốn chia sẻ trong package |
| `protected` | Cùng package và subclass theo quy tắc `protected` của Java | Dùng có cân nhắc; trường mutable dễ bị sửa sai |
| `public` | Mọi mã được phép truy cập lớp | Hiếm khi thích hợp với trường instance mutable |

**Không hiểu nhầm `protected` là “chỉ subclass mới truy cập”.** Các lớp trong cùng package cũng có quyền; subclass khác package còn chịu những hạn chế về biểu thức nhận truy cập. Cách đặt `private` field và `protected final` method thường dễ giữ invariant hơn.

Trong ví dụ:

- `private final String owner`: tham chiếu chỉ được gán trong quá trình khởi tạo; `String` là kiểu bất biến.
- `private long balanceCents`: dữ liệu có thể thay đổi, nhưng mọi đường cập nhật đều nằm dưới kiểm soát của `Account`.
- `public final void deposit(...)`: lớp con không được override để bỏ qua kiểm tra; `Math.addExact` bắt lỗi tràn `long` khi cộng.
- `protected final void debit(...)`: lớp con có thể yêu cầu thao tác trừ tiền hợp lệ nhưng không thay thuật toán kiểm tra.
- `public abstract void withdraw(...)`: lớp con cụ thể phải cung cấp triển khai, nếu không thừa hưởng được một triển khai cụ thể phù hợp.

**Lưu ý về `final`:** `final` của trường tham chiếu chỉ cấm gán lại tham chiếu, không đóng băng object được tham chiếu. Ví dụ `final List<String> tags` vẫn cho phép `tags.add("new")` khi danh sách là mutable. Với trường chứa `List`, `Map` hoặc object mutable trong lớp cha, cần cân nhắc `List.copyOf(...)`, defensive copy và phương thức cập nhật có kiểm soát. Tránh trả trực tiếp cấu trúc dữ liệu mutable nội bộ qua getter.

## 5. Constructor và thời điểm khởi tạo trạng thái

Constructor của lớp cha trừu tượng chạy trong quá trình tạo đối tượng lớp con, thường qua `super(...)`, để thiết lập phần trạng thái mà lớp cha sở hữu. Trường instance bắt đầu từ giá trị mặc định; phần khởi tạo và constructor của lớp cha hoàn tất trước khi các initializer và thân constructor của lớp con hoàn tất.

Tránh gọi phương thức instance có thể override từ constructor lớp cha: Java vẫn dispatch đến lớp con khi các trường của lớp con có thể chưa được gán giá trị mong muốn. Khi thiết kế lớp giữ invariant, constructor nên kiểm tra và gán dữ liệu do chính lớp đó quản lý; ví dụ và quy tắc constructor chi tiết được tập trung trong bài về constructor.

## 6. Field hiding: trường **không** được override như method

Nếu subclass khai báo trường trùng tên với trường ở cha, trường mới **che khuất** (*hide*) tên trường cũ; nó không thay thế trường cũ. Hai trường vẫn tồn tại. Khi truy cập trường, Java xét **kiểu khai báo của biểu thức tham chiếu**; khi gọi instance method được override, Java dispatch theo **kiểu thực tế của đối tượng**.

Lưu mã độc lập sau thành **`FieldHidingDemo.java`**:

```java
public class FieldHidingDemo {
    public static void main(String[] args) {
        Parent child = new Child();

        System.out.println(child.label);
        System.out.println(child.getLabel());
    }
}

class Parent {
    String label = "parent";

    String getLabel() {
        return label;
    }
}

class Child extends Parent {
    String label = "child";

    @Override
    String getLabel() {
        return label;
    }
}
```

```bash
javac FieldHidingDemo.java
java FieldHidingDemo
```

Kết quả:

```text
parent
child
```

`child.label` chọn trường ở `Parent` vì biến `child` có kiểu `Parent`. `child.getLabel()` gọi phương thức override ở `Child` vì đối tượng thực tế là `Child`.

Không nên viết hai trường có cùng ý nghĩa ở lớp cha và lớp con để mong “override trạng thái”. Dù trường cha là `private` và mã con được phép có trường trùng tên riêng, cách làm đó vẫn tạo hai nơi lưu dữ liệu có thể mâu thuẫn. Định nghĩa một trường tại nơi sở hữu hợp lý, rồi mở rộng hành vi bằng method.

## 7. Trường `static` không đại diện cho trạng thái riêng của mỗi đối tượng

Instance field thông thường thuộc từng đối tượng; hai tài khoản được tạo riêng có hai số dư riêng. Ngược lại, field `static` thuộc lớp khai báo và được chia sẻ giữa các instance. Vì vậy, không dùng `static` cho số dư hoặc trạng thái nghiệp vụ riêng của từng đối tượng.

Phương thức `static` cũng không tham gia dynamic dispatch như instance method, còn trạng thái `static` có thể thay đổi cần được xem xét về đồng thời. Các quy tắc chi tiết của `static`, `final` và `private`, bao gồm hiding và đóng gói, được tập trung trong bài về thành viên và modifier.

## 8. Bảo vệ bất biến chung và khả năng mở rộng

**Invariant** là điều kiện luôn phải đúng đối với trạng thái hợp lệ của đối tượng. Trong `Account`, quy tắc `balanceCents >= 0` được bảo vệ bằng constructor, `deposit()` và `debit()`.

Thiết kế đáng ưu tiên:

- Constructor lớp cha chặn dữ liệu không hợp lệ **ngay lúc tạo object**.
- Phương thức cập nhật kiểm tra đầu vào trước khi sửa trạng thái.
- Lớp con gọi hành vi được lớp cha kiểm soát, tránh tự gán vào mutable field của cha.
- Nếu sửa nhiều trường, xác định thứ tự để không tạo trạng thái dở dang khi có exception. Ví dụ `SavingsAccount` chỉ giảm `freeWithdrawals` sau khi `debit()` thành công.
- Nếu yêu cầu mới làm thay đổi invariant cơ bản (chẳng hạn tài khoản được thấu chi), cần thiết kế lại hợp đồng của lớp cha cho nhất quán, không tạo lối tắt sửa dữ liệu ở subclass.

Với đối tượng mutable dùng bởi nhiều thread, `private`, `final` trên method hoặc getter **không tự bảo đảm thread safety**. Hai luồng có thể đọc cùng giá trị cũ và ghi đè nhau. `volatile` giúp các luồng nhìn thấy cập nhật của trường được đánh dấu, nhưng phép tăng/giảm dạng đọc–tính–ghi không tự trở thành nguyên tử. Tùy mô hình sử dụng, cần đồng bộ hóa, khóa hoặc cách cập nhật nguyên tử phù hợp. Đây là mối quan tâm bổ sung khi chia sẻ object; bản chất kế thừa field không tạo cơ chế đồng bộ.

## 9. Kế thừa, interface và composition

- Dùng **abstract class** khi các subclass thật sự có quan hệ *is-a*, chia sẻ cả **trạng thái instance**, invariant và hành vi, trong khi vẫn có những điểm mở rộng cần subclass triển khai.
- Dùng **interface** để mô tả hợp đồng chung, đặc biệt khi các lớp không cùng hệ phân cấp hoặc cần triển khai nhiều hợp đồng. Một interface Java không có instance field để mỗi implementation kế thừa; trường khai báo trong interface ngầm là `public static final`.
- Dùng **composition** khi phần dùng chung là một khả năng/thành phần có thể lắp ghép, không phải quan hệ is-a ổn định. Ví dụ `FeePolicy` có thể là object được `Account` giữ để thay đổi cách tính phí mà không phải tạo một subclass cho mọi tổ hợp chính sách.

Không nên đẩy một thuộc tính lên lớp cha chỉ vì “các lớp đang trùng code”. Hỏi xem thuộc tính đó có mang cùng ý nghĩa, vòng đời, invariant trong tất cả subclass hay không.

## 10. Các lỗi thường gặp

| Lỗi | Hậu quả | Cách tránh |
| --- | --- | --- |
| Khai báo `static` cho số dư của từng object | Tất cả instance cùng thấy một số dư | Dùng instance field |
| Để `protected` mutable field cho mọi subclass sửa | Lớp con có thể phá invariant | Giữ `private`, mở method kiểm soát |
| Đưa field chỉ một subclass cần lên cha | Những subclass khác gánh trạng thái vô nghĩa | Giữ field riêng ở subclass |
| Che khuất field bằng khai báo trùng tên | Có hai trường độc lập dễ lệch dữ liệu | Một nơi lưu field; dùng method để tùy biến |
| Constructor cha gọi phương thức override | Đọc trạng thái subclass chưa khởi tạo | Không dispatch method mở trong constructor |
| Trả trực tiếp bộ sưu tập mutable qua getter | Mã ngoài sửa trạng thái bỏ qua kiểm tra | Defensive copy, API cập nhật hợp lệ |
| Nghĩ `final` tham chiếu làm object bất biến | Object mutable vẫn bị thay đổi | Bảo vệ object được tham chiếu |
| Chia sẻ object mutable qua nhiều thread không đồng bộ | Lost update, dữ liệu sai | Cơ chế đồng bộ phù hợp với nghiệp vụ |

## 11. Tự kiểm tra và áp dụng

**Bài tập:** Thêm `StudentAccount`, mỗi đối tượng có `schoolId` và một hạn mức giao dịch miễn phí.

1. `schoolId` nên ở đâu? **`StudentAccount`**, vì các tài khoản khác không có mã trường học.
2. `balanceCents` nên ở đâu? **`Account`**, vì đây là dữ liệu và invariant chung.
3. Subclass có nên sửa trực tiếp `balanceCents`? **Không**; gọi hành vi cập nhật được lớp cha bảo vệ.
4. Hai object `StudentAccount` có dùng chung một số dư không? **Không**, trừ khi bạn cố tình thiết kế nguồn trạng thái chia sẻ khác; instance field thông thường thuộc từng object.
5. Nếu `Account a = new StudentAccount(...)` thì `a.owner()` trả trạng thái từ đâu? **Từ chính object `StudentAccount`** mà `a` đang trỏ tới, nơi lưu phần `Account` được kế thừa.
6. Nếu tính phí phụ thuộc nhiều chính sách thay đổi độc lập với loại tài khoản, nên dùng gì? **Cân nhắc composition**, ví dụ một `FeePolicy` gắn với tài khoản.

**Ghi nhớ:** Lớp trừu tượng là nơi định nghĩa **thuộc tính chung và cơ chế bảo vệ trạng thái chung**; mỗi đối tượng lớp con vẫn sở hữu **bản trạng thái instance riêng**. Hãy đặt field theo đúng ý nghĩa nghiệp vụ, ưu tiên đóng gói `private`, khởi tạo qua `super(...)` và cho phép subclass tùy biến **hành vi**, không tùy ý phá invariant.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-classes-005">Phương thức có thân và mã triển khai dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức có thân và mã triển khai dùng chung trong lớp trừu tượng (Java)

## 1. Bản chất: lớp trừu tượng vẫn có thể triển khai phương thức

Trong Java, một `abstract class` không thể được tạo đối tượng trực tiếp bằng `new`, nhưng **không bắt buộc mọi phương thức của nó đều trừu tượng**. Nó có thể kết hợp:

- **Phương thức có thân (concrete method):** có khối lệnh `{ ... }`, cung cấp hành vi mặc định hoặc quy trình dùng chung cho các lớp con.
- **Phương thức trừu tượng (abstract method):** chỉ khai báo hợp đồng, kết thúc bằng dấu `;`, không có thân. Lớp con cụ thể phải triển khai những phương thức trừu tượng chưa được triển khai trên chuỗi kế thừa.
- **Trường dữ liệu và constructor:** lưu trạng thái chung, bảo đảm điều kiện khởi tạo mà các phương thức dùng chung cần.

Ví dụ hình thức:

```java
abstract class BaseTask {
    public void start() {                    // Có thân: dùng chung
        System.out.println("Bắt đầu");
        execute();
    }

    protected abstract void execute();       // Không có thân: lớp con cung cấp
}
```

`start()` là phương thức đã được triển khai trong lớp cha. Mỗi lớp con kế thừa `BaseTask` có thể dùng lại `start()` mà không viết lại câu lệnh in thông báo và gọi `execute()`. Khi `execute()` chạy, Java chọn bản triển khai tương ứng với đối tượng thật ở thời điểm chạy.

Một lớp được khai báo `abstract` **vẫn hợp lệ dù không có phương thức `abstract`**; khi đó từ khóa này chủ yếu ngăn khởi tạo trực tiếp và dành lớp làm nền cho các lớp con.

## 2. Vì sao cần mã triển khai dùng chung?

Giả sử nhiều loại bộ gửi thông báo đều phải kiểm tra đầu vào, chuẩn hóa nội dung và ghi log, nhưng mỗi loại gửi thông báo bằng một cơ chế khác nhau. Nếu mỗi lớp chép lại ba thao tác chung, lỗi sửa chữa dễ bị bỏ sót và hành vi dần không nhất quán.

Đưa các bước giống nhau vào một lớp cha trừu tượng sẽ tạo ra một điểm bảo trì chung:

1. Lớp cha chứa **phần bất biến của quy trình**: kiểm tra, chuẩn hóa, ghi log và trình tự gọi.
2. Lớp cha khai báo **điểm biến thiên** dưới dạng phương thức trừu tượng hoặc phương thức có thân cho phép override.
3. Lớp con chỉ viết phần thực sự khác biệt: cách gửi qua email, SMS hoặc kênh khác.

Đây là cơ sở của mẫu thiết kế **Template Method**: phương thức khung trong lớp cha xác định các bước, còn lớp con cung cấp một số bước cụ thể. Không phải mọi phương thức dùng chung đều phải tham gia Template Method; một tiện ích `protected` có thân để các lớp con gọi lại cũng là tái sử dụng hợp lệ.

## 3. Ví dụ Java 17+: dùng chung luồng gửi thông báo

Lưu toàn bộ chương trình sau vào tệp `Main.java`:

```java
import java.util.Objects;

abstract class NotificationSender {
    private final String channelName;

    protected NotificationSender(String channelName) {
        this.channelName = Objects.requireNonNull(channelName);
    }

    // Quy trình dùng chung: lớp con không được thay đổi thứ tự các bước.
    public final void send(String recipient, String message) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(message, "message");

        if (recipient.isBlank()) {
            throw new IllegalArgumentException("recipient không được trống");
        }

        String normalized = normalize(message);
        log(recipient);
        deliver(recipient, normalized);
    }

    // Có thân mặc định; lớp con được tùy chỉnh nếu cần.
    protected String normalize(String message) {
        return message.strip();
    }

    // Có thân, dùng lại nguyên vẹn cho mọi kênh gửi.
    private void log(String recipient) {
        System.out.println("[" + channelName + "] Chuẩn bị gửi tới " + recipient);
    }

    // Phần khác biệt: mỗi kênh phải tự triển khai.
    protected abstract void deliver(String recipient, String message);
}

final class EmailSender extends NotificationSender {
    EmailSender() {
        super("email");
    }

    @Override
    protected void deliver(String recipient, String message) {
        System.out.println("EMAIL tới " + recipient + ": " + message);
    }
}

final class SmsSender extends NotificationSender {
    SmsSender() {
        super("sms");
    }

    // Chỉ thay đổi bước chuẩn hóa; vẫn tái sử dụng bước của lớp cha.
    @Override
    protected String normalize(String message) {
        return super.normalize(message).replaceAll("\\s+", " ");
    }

    @Override
    protected void deliver(String recipient, String message) {
        System.out.println("SMS tới " + recipient + ": " + message);
    }
}

public class Main {
    public static void main(String[] args) {
        NotificationSender email = new EmailSender();
        NotificationSender sms = new SmsSender();

        email.send("an@example.com", "  Xin chào An  ");
        sms.send("0901234567", "  Hẹn     gặp  ");
    }
}
```

Biên dịch và chạy (JDK 17 trở lên):

```bash
javac -encoding UTF-8 Main.java
java Main
```

Kết quả tiêu chuẩn:

```text
[email] Chuẩn bị gửi tới an@example.com
EMAIL tới an@example.com: Xin chào An
[sms] Chuẩn bị gửi tới 0901234567
SMS tới 0901234567: Hẹn gặp
```

Giải thích từng vai trò:

| Thành phần | Kiểu phương thức | Vai trò |
| --- | --- | --- |
| `send(...)` | `public final`, có thân | Cung cấp toàn bộ luồng xử lý chung; `final` cấm lớp con override để không bỏ qua kiểm tra và ghi log. |
| `normalize(...)` | `protected`, có thân | Cung cấp mặc định `strip()`; `SmsSender` override rồi gọi `super.normalize(...)` để tận dụng phần đã có. |
| `log(...)` | `private`, có thân | Chi tiết nội bộ chỉ lớp cha gọi; mọi lớp con vẫn hưởng hành vi ghi log qua `send(...)`. |
| `deliver(...)` | `protected abstract`, không thân | Bắt buộc mỗi lớp con cụ thể cung cấp thao tác gửi riêng. |
| `NotificationSender(...)` | Constructor `protected` | Khởi tạo `channelName` khi một lớp con được tạo; không tạo trực tiếp `NotificationSender`. |

Điểm then chốt: biến `email` có kiểu khai báo `NotificationSender`, nhưng đối tượng thật là `EmailSender`. Lời gọi `email.send(...)` sử dụng thân `send` kế thừa từ lớp cha; bên trong, lời gọi `deliver(...)` được phân giải động đến `EmailSender.deliver(...)`. Tương tự, đối tượng `SmsSender` sử dụng bản `normalize(...)` đã override. Việc gọi phương thức có thân của lớp cha **không làm mất tính đa hình** của các bước được lớp con ghi đè.

Trong ví dụ, `SmsSender.normalize` gọi `super.normalize(message)` để lấy logic cắt khoảng trắng hai đầu, sau đó thu gọn chuỗi khoảng trắng bên trong. `super` là cách tái sử dụng trực tiếp bản triển khai của lớp cha khi override; nó không tạo đối tượng lớp cha mới.

## 4. Phân biệt kế thừa, override và gọi `super`

Nếu lớp con không override một phương thức có thân có thể kế thừa, nó dùng lại triển khai đó nguyên trạng. Chỉ override khi hành vi cần biến thể; dùng `@Override` để trình biên dịch xác minh chữ ký. Khi cần giữ một phần hành vi cũ, lớp con có thể gọi `super.method(...)`, như `SmsSender.normalize` trong ví dụ.

Override phải giữ chữ ký tương thích và không được thu hẹp quyền truy cập. Phương thức `private` không phải hook để override, phương thức `static` bị hiding thay vì dynamic dispatch, còn phương thức `final` cấm override. Bài chuyên về modifier đi sâu vào ma trận hợp lệ; bài này tập trung vào việc tổ chức các bước dùng chung và điểm biến thiên.

## 5. Quan hệ với trạng thái và constructor

Phương thức có thân có thể đọc trạng thái của lớp cha và gọi các hook khác; constructor cha thiết lập trạng thái đó trước khi quy trình dùng chung được gọi. Tránh gọi hook có thể override trong constructor, bởi lớp con có thể chưa khởi tạo xong các trường của mình. Quy tắc về constructor, access modifier và thứ tự khởi tạo được giải thích riêng trong bài về constructor.

## 6. Chọn phương thức có thân, abstract hay final như thế nào?

| Nhu cầu | Cách khai báo gợi ý | Lý do |
| --- | --- | --- |
| Mọi lớp con phải có cùng một bước xử lý | `final` method có thân | Giữ đúng quy tắc chung, tránh override trái ý. |
| Có hành vi mặc định hợp lý, đôi khi cần biến thể | Method có thân và cho phép override | Có thể dùng nguyên trạng hoặc tùy chỉnh, kể cả gọi `super`. |
| Không có triển khai mặc định đúng cho mọi lớp con | `abstract` method | Buộc lớp con cụ thể đưa ra cách thực hiện. |
| Chi tiết chỉ lớp cha tự sử dụng | `private` method có thân | Đóng gói, giảm bề mặt API kế thừa. |
| Hàm tiện ích dùng chung cho lớp con | `protected` method có thân, cân nhắc `final` | Tái sử dụng mà không buộc sao chép thuật toán. |

Quy trình lựa chọn thực tế: trước tiên xác định bước nào **giống nhau** và bước nào **thực sự biến đổi** giữa các lớp con; đưa phần giống nhau vào method có thân, biểu diễn phần bắt buộc khác nhau bằng method `abstract`. Chỉ mở quyền override tại các điểm mở rộng đã được thiết kế.

## 7. Khi nào nên dùng lớp trừu tượng để chia sẻ code?

Phù hợp khi các lớp con có quan hệ **is-a** tự nhiên và cùng chia sẻ một phần thuật toán hoặc trạng thái, ví dụ các loại bộ xử lý, trình đọc tệp hay bộ gửi thông báo có vòng đời và quy tắc nền giống nhau. Nó đặc biệt hữu ích khi phải bảo vệ **thứ tự các bước** bằng Template Method và vẫn cho phép tùy biến một số bước.

Tuy vậy, kế thừa Java chỉ cho phép một lớp cha trực tiếp (`extends` một class). Nếu mục tiêu chỉ là dùng lại vài tiện ích độc lập, chưa chắc nên tạo cả hệ phân cấp:

- **Composition / dependency injection:** chuyển hành vi tái sử dụng sang một đối tượng dịch vụ được các lớp khác gọi. Cách này linh hoạt nếu nhiều kiểu đối tượng không cùng họ vẫn cần logic ấy.
- **Interface với `default method`:** hợp lý khi nhiều lớp cần chia sẻ hành vi mặc định nhỏ, không cần trạng thái instance hay constructor từ cùng một lớp cha. `default method` không thay thế mọi vai trò của abstract class.
- **Utility method `static`:** hợp lý cho phép biến đổi thuần túy không cần trạng thái và không cần đa hình.

Nên ưu tiên cấu trúc đơn giản nhất diễn tả đúng quan hệ nghiệp vụ; không tạo base class chỉ vì hai lớp tình cờ có vài dòng code giống nhau.

## 8. Lỗi thường gặp và cách tự kiểm tra

1. **Nhầm “abstract class” với “toàn phương thức abstract”.** Lớp trừu tượng được phép có rất nhiều phương thức có thân, thậm chí không có phương thức abstract.
2. **Viết thân cho phương thức `abstract`.** Khai báo `protected abstract void execute();` không được kèm `{ ... }`. Muốn có logic mặc định, bỏ `abstract` và cung cấp thân.
3. **Định nghĩa method abstract trong class thường.** Class chứa method `abstract` phải được đánh dấu `abstract`; class con cụ thể phải triển khai các method abstract còn tồn đọng.
4. **Override nhầm phương thức `private`/`final`/`static`.** `private` không thể override, `final` cấm override, `static` chỉ hiding. Dùng `@Override` và biên dịch để bắt lỗi.
5. **Nhân bản phần kiểm tra trong từng lớp con.** Điều này dễ tạo sai lệch. Đặt validation trong một phương thức khung `final`, để lớp con chỉ xử lý phần chuyên biệt.
6. **Thay đổi logic chung trong override mà không hiểu hợp đồng.** Nếu được phép mở rộng, chỉ override đúng hook; gọi `super` khi cần bảo toàn phần mặc định.
7. **Gọi hook có thể override từ constructor.** Chuyển lời gọi đó sang một phương thức được thực thi sau khi đối tượng khởi tạo hoàn chỉnh.
8. **Lạm dụng kế thừa để chia sẻ vài hàm.** Cân nhắc composition hoặc utility method để giảm phụ thuộc vào một lớp cha cố định.

Để kiểm chứng ví dụ, hãy thử thêm một lớp `PushSender` extends `NotificationSender` chỉ triển khai `deliver(...)`: lớp mới tự động nhận toàn bộ kiểm tra đầu vào, chuẩn hóa mặc định và ghi log. Tiếp đó thử gọi `send("  ", "test")`: chương trình ném `IllegalArgumentException` **trước khi** ghi log và gọi `deliver`. Hai phép thử xác nhận mã chung được dùng nhất quán giữa các lớp con.

**Ghi nhớ:** phương thức có thân trong abstract class tạo ra hành vi kế thừa dùng chung; phương thức abstract xác định việc lớp con phải bổ sung; phương thức `final` bảo vệ những bước hoặc thuật toán không được thay đổi. Kết hợp ba loại này có chủ đích giúp vừa tái sử dụng code vừa giữ quyền tùy biến đúng chỗ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-classes-006">Thành viên static, final và private trong lớp trừu tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Thành viên `static`, `final` và `private` trong lớp trừu tượng (Java 17+)

## 1. Bối cảnh và mục tiêu

Trong Java, `abstract class` là lớp không thể khởi tạo trực tiếp bằng `new`. Lớp này thường giữ trạng thái và hành vi chung, đồng thời có thể khai báo phương thức trừu tượng để lớp con triển khai. **Một lớp trừu tượng vẫn có đầy đủ thành viên thông thường**: thuộc tính, phương thức có thân, constructor, khối khởi tạo và lớp lồng nhau. Ba từ khóa `static`, `final`, `private` vẫn áp dụng theo quy tắc chung của Java, nhưng cách kết hợp của chúng với `abstract` cần hiểu chính xác.

Sau bài này, bạn có thể:
- Xác định thành viên thuộc về lớp hay đối tượng, thành viên nào được phép ghi đè và thành viên nào chỉ dùng nội bộ.
- Giải thích vì sao `static abstract`, `final abstract` và `private abstract` không hợp lệ.
- Thiết kế lớp trừu tượng với trạng thái được đóng gói, các bước xử lý cố định và phần mở rộng cho lớp con.
- Phân biệt **method hiding** ở phương thức `static` với **overriding** ở phương thức instance.

Ví dụ khởi đầu:

~~~java
abstract class Document {
    private final String title;
    private static int created;

    protected Document(String title) {
        this.title = title;
        created++;
    }

    public final String title() {
        return title;
    }

    public static int createdCount() {
        return created;
    }

    public abstract String render();
}
~~~

`Document` chưa định nghĩa `render()` nên là lớp trừu tượng. Tuy vậy, `title`, `created`, constructor, `title()` và `createdCount()` đều hợp lệ. Một lớp con cụ thể phải triển khai `render()` (hoặc vẫn khai báo chính mình là `abstract`).

## 2. Bảng quy tắc nhanh

| Khai báo trong `abstract class` | Hợp lệ? | Ý nghĩa |
| --- | --- | --- |
| `static` field/method | Có | Thành viên gắn với lớp khai báo, không gắn với từng instance. |
| `final` field | Có | Biến chỉ được gán một lần theo quy tắc definite assignment. |
| `final` instance method có thân | Có | Lớp con không thể override phương thức này. |
| `private` field/method có thân | Có | Chỉ truy cập theo phạm vi `private` của Java; không tạo hợp đồng override cho lớp con. |
| `private static final` field | Có | Cách khai báo phổ biến cho hằng số hoặc tham chiếu dùng nội bộ. |
| `private final` method có thân | Có | Hợp lệ nhưng `final` thường thừa vì phương thức `private` vốn không thể override. |
| `abstract` instance method (không có thân) | Có | Lớp con cụ thể phải cung cấp triển khai còn thiếu; các tổ hợp modifier mâu thuẫn được tập trung trong bài về `abstract` và `final`. |

Các cột “Có” và “Không” nói về cú pháp và quy tắc ngôn ngữ; việc **nên** dùng tổ hợp nào còn phụ thuộc thiết kế.

## 3. `static`: thành viên thuộc về lớp trừu tượng

### 3.1. Trường `static` là trạng thái dùng chung

Trường `static` được gắn với lớp khai báo. Nếu `Document` có `private static int created`, mỗi lần constructor của `Document` được gọi thông qua constructor lớp con, biến `created` của `Document` được tăng lên. Các instance của những lớp con khác nhau vẫn dùng chung **một trường `Document.created`**. Trường này không được tạo lại riêng cho từng đối tượng.

Truy cập nên thể hiện rõ lớp sở hữu: `Document.createdCount()` thay vì gọi phương thức `static` qua một tham chiếu đối tượng. Nếu nhiều luồng cùng tăng một biến `static int`, thao tác `++` không tự động an toàn cho đồng thời; có thể cần đồng bộ hoặc `AtomicInteger`, tùy bài toán.

### 3.2. Phương thức `static` không override

Phương thức `static` có thể khai báo trong lớp trừu tượng và được gọi mà không cần tạo instance của chính lớp trừu tượng: `Document.createdCount()`. Phương thức này **không thể mang `abstract`**. Một lớp con có thể khai báo phương thức `static` cùng chữ ký phù hợp để *hide* phương thức của lớp cha; lựa chọn phương thức dựa trên kiểu tại thời điểm biên dịch, không dựa trên kiểu thực của đối tượng.

Không được dùng `@Override` cho phương thức `static`, và phương thức `static` không thể thay thế phương thức instance cùng chữ ký qua kế thừa (hoặc ngược lại).

### 3.3. Lưu ý về khởi tạo

Trường `static` được khởi tạo khi lớp tương ứng được khởi tạo theo quy tắc JVM; constructor của từng instance là chuyện khác. Một trường `static final` có thể nhận giá trị ngay tại khai báo hoặc trong khối khởi tạo `static`, miễn đáp ứng quy tắc gán một lần. Không được mặc định rằng mọi trường `static final` đều là hằng số thời gian biên dịch: `static final int LIMIT = 10` có thể là *constant variable*, còn `static final Object LOCK = new Object()` thì không.

## 4. `final`: kiểm soát gán biến, override và kế thừa

### 4.1. Trường instance `final`

`private final String title` phải được gán đúng quy tắc trước khi constructor hoàn tất; với một *blank final instance field*, đường đi khởi tạo bình thường phải bảo đảm biến được gán đúng một lần. Nhờ vậy lớp con không thể gán lại trường này sau khi khởi tạo.

Tuy nhiên, **`final` khóa tham chiếu, không làm đối tượng được tham chiếu bất biến**:

~~~java
import java.util.ArrayList;
import java.util.List;

abstract class TaggedItem {
    private final List<String> tags = new ArrayList<>();

    public void addTag(String tag) {
        tags.add(tag); // Hợp lệ: thay đổi nội dung List.
    }

    public List<String> tags() {
        return List.copyOf(tags); // Trả về bản sao không sửa được.
    }
}
~~~

`tags = new ArrayList<>()` lần nữa trong một phương thức instance sẽ là lỗi biên dịch, nhưng `tags.add(...)` là hợp lệ. Nếu cần bất biến thực sự, thiết kế phải kiểm soát cả khả năng thay đổi nội dung và khả năng lộ tham chiếu.

### 4.2. Phương thức `final`

Một phương thức instance có thân được đánh dấu `final` không thể bị lớp con override. Đây là công cụ giữ cố định một quy trình chung, ví dụ `execute()` thực hiện kiểm tra đầu vào, ghi log hoặc gọi các bước cho phép mở rộng. Một phương thức `final` vẫn có thể **gọi** phương thức `abstract`: phần khung cố định do lớp cha quyết định; chi tiết biến thiên do lớp con triển khai. Đây là ý tưởng gần với *Template Method*.

Phương thức `abstract` không có thân và cần được lớp con thực hiện, vì vậy `abstract final` không hợp lệ. Phương thức `static final` có thân cũng hợp lệ; `final` trong trường hợp này cấm lớp con hide phương thức `static` cùng chữ ký phù hợp.

### 4.3. `final` trên lớp

`abstract class Base` có thể có lớp con `final class Concrete extends Base`. Lớp con `Concrete` không thể được kế thừa tiếp, nhưng vẫn kế thừa từ `Base` hợp lệ. Ngược lại, **một lớp không thể vừa `abstract` vừa `final`**.

## 5. `private`: đóng gói trong lớp trừu tượng

`private` thường bảo vệ bất biến nội bộ như số lượng đối tượng đã tạo, dữ liệu xác thực hoặc các hàm tiện ích không nên trở thành API cho lớp con.

- **Trường `private`**: lớp con không truy cập trực tiếp bằng tên trường; thay bằng constructor và các phương thức `protected`/`public` thích hợp để duy trì bất biến. Không nên biến trường thành `protected` chỉ để tránh viết API.
- **Phương thức `private` có thân**: dùng nội bộ lớp khai báo, có thể là instance hoặc `static`. Phương thức này không được kế thừa để override; một phương thức cùng tên và tham số ở lớp con là phương thức riêng khác, không phải override.
- **Phương thức `private abstract`**: bất hợp lệ vì lớp con không thể thực hiện một hợp đồng override mà nó không có quyền truy cập.
- **Constructor `private`**: một lớp `abstract` có thể khai báo constructor `private` về mặt cú pháp, nhưng lớp con bình thường ở bên ngoài lớp bao không thể gọi constructor đó. Thông thường dùng constructor `protected` hoặc constructor có quyền truy cập phù hợp để cho phép kế thừa có kiểm soát.

`private` và `final` giải quyết hai câu hỏi khác nhau: “Ai được truy cập?” và “Có được override/gán lại không?”. Ví dụ `private final` field vừa ẩn khỏi mã bên ngoài vừa chỉ được gán theo quy tắc `final`. Với phương thức `private final`, `final` ít mang thêm giá trị vì overriding đã không áp dụng.

## 6. Ví dụ chạy được: kết hợp cả ba từ khóa

Lưu toàn bộ đoạn sau thành **`TaskDemo.java`**. Mã gồm một lớp trừu tượng, một lớp con cụ thể và một lớp chứa `main`; chúng cùng nằm trong một tệp, và chỉ `TaskDemo` là `public`.

~~~java
abstract class Task {
    private static int createdCount = 0;
    private static final String PREFIX = "[task] ";
    private final String name;

    protected Task(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;
        createdCount++;
    }

    public static int totalCreated() {
        return createdCount;
    }

    public final String getName() {
        return name;
    }

    private String displayLabel() {
        return PREFIX + getName();
    }

    public final void execute() {
        System.out.println(displayLabel() + ": " + doWork());
    }

    protected abstract String doWork();
}

final class PrintTask extends Task {
    public PrintTask(String name) {
        super(name);
    }

    @Override
    protected String doWork() {
        return "xong";
    }
}

public class TaskDemo {
    public static void main(String[] args) {
        Task first = new PrintTask("Alpha");
        Task second = new PrintTask("Beta");

        System.out.println(Task.totalCreated());
        first.execute();
        second.execute();
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 TaskDemo.java
java TaskDemo
~~~

Kết quả chuẩn:

~~~text
2
[task] Alpha: xong
[task] Beta: xong
~~~

Giải thích theo dòng chảy:
1. `new PrintTask(...)` gọi `super(name)`. Constructor của `Task` lưu `name` qua trường `private final` và tăng biến `private static createdCount`. Sau hai lần tạo, bộ đếm bằng `2`.
2. `Task.totalCreated()` đọc dữ liệu thuộc lớp mà không cần truy cập trường `private` từ ngoài.
3. `execute()` là instance method `final` nên `PrintTask` không thể thay đổi thứ tự hoạt động của nó. Phương thức này gọi trợ giúp `private displayLabel()` rồi gọi `doWork()`.
4. `doWork()` là `protected abstract`; tại runtime, lời gọi trên `first` và `second` tìm triển khai `PrintTask.doWork()`. Kết quả `"xong"` được in ra.
5. `PrintTask` là `final` nhưng vẫn được phép kế thừa `Task`. Hệ thống có thể thêm **một lớp con khác của `Task`** nếu muốn cách làm việc khác.

Đây là mẫu tổ chức hữu ích khi cần bắt buộc mọi tác vụ tuân theo khung xử lý chung nhưng vẫn cho phép thay đổi bước chuyên biệt.

## 7. Ví dụ chạy được: hiding (`static`) và overriding (`abstract`) khác nhau thế nào?

Lưu thành **`BindingDemo.java`**:

~~~java
abstract class Animal {
    public static String category() {
        return "Animal";
    }

    public abstract String sound();
}

class Cat extends Animal {
    public static String category() {
        return "Cat";
    }

    @Override
    public String sound() {
        return "meow";
    }
}

public class BindingDemo {
    public static void main(String[] args) {
        Animal pet = new Cat();

        System.out.println(Animal.category());
        System.out.println(Cat.category());
        System.out.println(pet.category()); // Hợp lệ nhưng nên tránh cách gọi này.
        System.out.println(pet.sound());
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 BindingDemo.java
java BindingDemo
~~~

Kết quả chuẩn:

~~~text
Animal
Cat
Animal
meow
~~~

Ở `pet.category()`, kiểu **khai báo** của biến `pet` là `Animal` nên trình biên dịch chọn `Animal.category()`. Đây là hiding, không phải đa hình. Ở `pet.sound()`, JVM sử dụng kiểu đối tượng thực `Cat` để chọn phương thức instance override nên kết quả là `meow`. Viết `Animal.category()` hoặc `Cat.category()` sẽ làm ý định rõ hơn và tránh nhầm lẫn.

## 8. Các lỗi biên dịch dễ mắc

Các tổ hợp như `abstract final class`, `static abstract`, `private abstract` và `final abstract` là các lỗi cần nhận diện; ma trận đầy đủ, bao gồm khác biệt giữa lớp top-level và lớp thành viên cùng các modifier `sealed`, được tập trung trong bài về xung đột `abstract`/`final`. Phần còn lại của bài này minh họa các hành vi riêng của `final`, `private` và `static` như override, truy cập thành viên và trạng thái mutable.

## 9. Cách lựa chọn trong thiết kế

| Nhu cầu | Cách dùng gợi ý | Lý do |
| --- | --- | --- |
| Trạng thái riêng của mỗi đối tượng, được gán khi khởi tạo | `private final` field | Giữ bất biến tham chiếu/trị số và hạn chế quyền sửa trực tiếp. |
| Hằng số nội bộ của lớp | `private static final` field | Thể hiện giá trị dùng chung và tránh công khai chi tiết triển khai. |
| Đếm hoặc quản lý trạng thái chung | `private static` field, API truy cập rõ ràng | Tránh lớp con tự thay đổi trạng thái chung; cân nhắc an toàn luồng. |
| Hàm phụ trợ chỉ lớp cha dùng | `private` method | Giảm bề mặt API và ràng buộc không cần thiết với lớp con. |
| Quy trình không cho phép lớp con sửa | `public/protected final` instance method | Giữ khung thực thi nhất quán. |
| Bước phải do lớp con triển khai | `protected/public abstract` instance method | Tạo hợp đồng đa hình hợp lệ. |
| Lớp triển khai cuối cùng, không cho kế thừa tiếp | `final class Concrete extends AbstractBase` | Khóa việc mở rộng từ lớp cụ thể. |

Không đặt `static` cho phương thức khi bạn muốn cách xử lý phụ thuộc kiểu thực của đối tượng. Không dùng `protected` một cách máy móc để lộ dữ liệu; nên cân nhắc API hành vi. Không dùng `final` như lời đảm bảo thread safety: một đối tượng vẫn có thể chứa trạng thái mutable, và biến `static` dùng chung vẫn có thể xảy ra race condition.

## 10. Tự kiểm tra

1. **Có thể gọi phương thức `static` của lớp `abstract` mà không tạo instance không?** Có; ví dụ `Task.totalCreated()`. Việc gọi phương thức không có nghĩa có thể `new Task()`.
2. **Vì sao `private abstract` sai?** Vì lớp con không thể override phương thức `private`; hợp đồng abstract sẽ không thể được thực hiện.
3. **Vì sao `final` instance method vẫn gọi được `abstract` method?** Vì `final` chỉ cấm override chính phương thức `final`; lời gọi nội bộ sang hook trừu tượng vẫn được dispatch động đến lớp con.
4. **`private static final List<?>` có bất biến không?** Chỉ tham chiếu trường không được gán lại; các phần tử hoặc danh sách vẫn có thể thay đổi nếu đối tượng cho phép và mã có đường truy cập thích hợp.
5. **`Animal pet = new Cat(); pet.category()` dùng phương thức nào?** `Animal.category()`, vì `category()` là `static` và lựa chọn dựa trên kiểu biểu thức tại lúc biên dịch. `pet.sound()` thì dùng `Cat.sound()` vì đó là instance method được override.

**Ghi nhớ:** `static` xác định thành viên cấp lớp và không tham gia overriding; `final` giới hạn việc gán lại hoặc override; `private` giới hạn truy cập và loại phương thức khỏi hợp đồng override. Trong lớp trừu tượng, bộ ba này phối hợp tốt với một số phương thức `protected abstract` để tạo thiết kế đóng gói và mở rộng có kiểm soát.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-classes-007">Constructor và lựa chọn phạm vi truy cập</a>

<details>
<summary>Xem chi tiết</summary>

# Constructor và lựa chọn phạm vi truy cập trong lớp trừu tượng (Java)

## 1. Vì sao lớp trừu tượng vẫn có constructor?

Trong Java, một lớp khai báo `abstract` không thể được khởi tạo trực tiếp bằng `new`, nhưng **vẫn có constructor**. Constructor của lớp trừu tượng thực hiện phần khởi tạo chung của mọi đối tượng thuộc các lớp con cụ thể (concrete subclass).

Ví dụ, mọi loại công việc có thể cần một mã định danh không rỗng. Thay vì để từng lớp con lặp lại việc kiểm tra và lưu mã, lớp trừu tượng nhận mã qua constructor, kiểm tra tính hợp lệ và gán vào trường chung.

Phân biệt ba khái niệm:

- **Không thể khởi tạo lớp trừu tượng trực tiếp:** `new AbstractJob(...)` là lỗi biên dịch.
- **Constructor của lớp trừu tượng vẫn chạy:** khi `new ExportJob(...)`, constructor `AbstractJob(...)` được gọi trong chuỗi khởi tạo trước khi constructor `ExportJob(...)` hoàn tất.
- **Constructor không được kế thừa:** lớp con phải khai báo constructor của chính nó nếu cần truyền tham số cho lớp cha, và có thể gọi constructor cha bằng `super(...)`.

Constructor **không có kiểu trả về**, kể cả `void`; tên phải trùng tên lớp; không được khai báo `abstract`, `static` hay `final`. Một lớp trừu tượng có thể có nhiều constructor (constructor overloading) như lớp thông thường.

## 2. Ví dụ chạy được: `protected` constructor và lớp con khác package

Ví dụ Java 17+ dưới đây cho thấy lớp cha trừu tượng giữ dữ liệu chung, lớp con ở package khác gọi `super(jobId)`, và mã phía ngoài khởi tạo lớp con qua constructor `public`.

Cấu trúc tệp:

```text
src/
└── demo/
    ├── base/
    │   └── AbstractJob.java
    └── app/
        ├── ExportJob.java
        └── Main.java
```

**`src/demo/base/AbstractJob.java`**

```java
package demo.base;

public abstract class AbstractJob {
    private final String jobId;

    protected AbstractJob(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId must not be blank");
        }
        this.jobId = jobId;
        System.out.println("AbstractJob: " + jobId);
    }

    protected final String jobId() {
        return jobId;
    }

    public abstract String execute();
}
```

**`src/demo/app/ExportJob.java`**

```java
package demo.app;

import demo.base.AbstractJob;

public final class ExportJob extends AbstractJob {
    public ExportJob(String jobId) {
        super(jobId);
        System.out.println("ExportJob ready");
    }

    @Override
    public String execute() {
        return "Export " + jobId();
    }
}
```

**`src/demo/app/Main.java`**

```java
package demo.app;

public class Main {
    public static void main(String[] args) {
        ExportJob job = new ExportJob("JOB-7");
        System.out.println(job.execute());
    }
}
```

Từ thư mục chứa `src`, biên dịch và chạy:

```bash
mkdir out
javac -d out src/demo/base/AbstractJob.java src/demo/app/ExportJob.java src/demo/app/Main.java
java -cp out demo.app.Main
```

Kết quả chính xác:

```text
AbstractJob: JOB-7
ExportJob ready
Export JOB-7
```

Giải thích:

1. `new ExportJob("JOB-7")` cấp phát một đối tượng `ExportJob`; không tạo một đối tượng `AbstractJob` riêng biệt.
2. Constructor `ExportJob(String)` gọi `super(jobId)`. Constructor `AbstractJob(String)` kiểm tra đầu vào, khởi tạo trường `private final jobId` rồi in dòng đầu tiên.
3. Sau khi constructor cha kết thúc, phần thân constructor con in `ExportJob ready`.
4. `execute()` của lớp con đọc mã qua phương thức `protected final jobId()` và in dòng cuối.

`private final` giữ trường nội bộ và chỉ cho phép gán trong giai đoạn khởi tạo hợp lệ. `protected` cho phép lớp con dùng điểm mở rộng cần thiết; `public` của `ExportJob` là giao diện khởi tạo dành cho mã sử dụng lớp con. Phương thức `execute()` là `public abstract`, nên lớp con triển khai với phạm vi `public` tương ứng.

## 3. Chọn phạm vi truy cập cho constructor của abstract class

Phạm vi truy cập áp dụng cho **lệnh gọi constructor**, độc lập với việc bản thân lớp có được khởi tạo trực tiếp hay không.

| Khai báo | Ai có thể gọi constructor? | Khi nào nên chọn? |
| --- | --- | --- |
| `public AbstractBase(...)` | Mã có quyền truy cập lớp, bao gồm các lớp con và lời gọi constructor hợp lệ | Chỉ khi muốn công khai quyền xây dựng phần base; với abstract class thường không cần rộng đến vậy |
| `protected AbstractBase(...)` | Mã cùng package; lớp con ở package khác có thể gọi thông qua `super(...)` (hoặc `this(...)` dẫn tới `super(...)`) theo quy tắc Java | Lựa chọn thường gặp khi thiết kế abstract class làm nền tảng cho lớp con ở nhiều package |
| `AbstractBase(...)` (package-private, không ghi modifier) | Các lớp trong cùng package | Giới hạn hệ phân cấp và quyền khởi tạo vào một package |
| `private AbstractBase(...)` | Bản thân lớp và các lớp cùng nest có quyền truy cập private, chẳng hạn lớp lồng bên trong phù hợp | Chủ động khóa việc tạo lớp con từ bên ngoài; chỉ các triển khai nội bộ có thể nối chuỗi constructor |

Lưu ý về `protected` ở **khác package**: quyền truy cập constructor dành cho lớp con chủ yếu được sử dụng trong khai báo constructor của lớp con thông qua `super(...)`. Nó **không** làm một lớp khác tự do gọi `new AbstractBase(...)`. Hơn nữa, `new AbstractBase(...)` đã bị cấm bởi `abstract` dù constructor là `public`.

Trước khi chọn modifier, cần phân biệt **khả năng nhìn thấy lớp** (ví dụ lớp top-level có `public` hay chỉ package-private) với **khả năng gọi constructor**. Một constructor `protected` không giúp lớp con ngoài package truy cập được một lớp cha top-level package-private.

### Vì sao `protected` thường phù hợp?

Một abstract class được thiết kế cho bên ngoài kế thừa thường có constructor `protected` để khẳng định: “Đây là thao tác khởi tạo cho các lớp con.” Constructor `public` vẫn hợp lệ, nhưng hiếm khi mang thêm lợi ích nếu người dùng cuối chỉ được tạo các lớp con cụ thể.

Nếu chỉ cho phép các lớp trong cùng package mở rộng và xây dựng đối tượng, constructor package-private có phạm vi hẹp hơn. Khi muốn kiểm soát chính xác mọi lớp triển khai bằng lớp lồng, `private` có thể phù hợp nhưng không nên chọn nếu cần subclass thông thường ở ngoài nest.

## 4. `super(...)`, `this(...)` và constructor mặc định

### 4.1. Không có constructor nào được kế thừa

Giả sử lớp trừu tượng chỉ có constructor nhận tham số:

```java
abstract class Base {
    protected Base(String name) {
        System.out.println(name);
    }
}

class Child extends Base {
    Child() {
        // Không gọi constructor cha tường minh.
    }
}
```

**Đây là ví dụ cố ý không biên dịch được.** Với Java 17, compiler tự chèn lời gọi `super()` không đối số ở đầu constructor `Child()`. Nhưng `Base` không định nghĩa constructor không đối số, nên lời gọi đó không hợp lệ.

Sửa bằng cách chuyển tham số tường minh:

```java
class Child extends Base {
    Child() {
        super("child");
    }
}
```

Đoạn sửa trên minh họa **phần khai báo lớp con**; nó dùng chung định nghĩa `Base` ngay trước đó, không phải một chương trình độc lập. Nếu cần tham số từ người tạo, lớp con có thể định nghĩa `Child(String name) { super(name); }`.

### 4.2. Khi nào Java sinh constructor mặc định?

Nếu một lớp (kể cả lớp trừu tượng) **không khai báo constructor nào**, compiler cung cấp constructor mặc định không đối số. Constructor này sẽ gọi `super()` của lớp cha; vì vậy lớp cha tiếp theo phải có constructor không đối số có thể truy cập. Nếu đã khai báo **bất kỳ constructor nào**, Java không tự thêm constructor không đối số nữa.

Hai trường hợp cần tách biệt:

- `abstract class Base {}`: có constructor mặc định, dù `new Base()` vẫn không hợp lệ.
- `abstract class Base { protected Base(int id) {} }`: chỉ có constructor đã khai báo; lớp con bắt buộc nối chuỗi đến constructor nhận `int`.

### 4.3. `this(...)` và `super(...)`

Trong một constructor Java 17, lời gọi constructor tường minh `this(...)` (constructor khác của **cùng lớp**) hoặc `super(...)` (constructor của **lớp cha**) phải là câu lệnh đầu tiên. Một constructor không được gọi trực tiếp cả hai; khi đi qua chuỗi `this(...)`, cuối cùng phải đến một lời gọi constructor cha. Không được tạo vòng lặp `this(...)`.

Ví dụ ngắn (chỉ minh họa lớp cha):

```java
abstract class Base {
    private final String name;

    protected Base() {
        this("unnamed");
    }

    protected Base(String name) {
        this.name = name;
    }

    protected final String name() {
        return name;
    }
}
```

Lớp con có thể dùng `super()` hoặc `super("custom")` nếu constructor tương ứng truy cập được. Không thể viết một lệnh như `System.out.println("start");` trước `super(...)` trong constructor khi dùng Java 17.

## 5. Thứ tự khởi tạo và bẫy gọi phương thức có thể override

Khi khởi tạo lớp con, chuỗi gọi constructor đi lên đến `Object` rồi xử lý lần lượt các lớp cha về phía lớp con. Phần khởi tạo trường và initializer của lớp cha diễn ra trước phần khởi tạo trường và initializer của lớp con; phần thân constructor cha kết thúc trước phần thân constructor con.

**Không nên gọi phương thức có thể override từ constructor lớp trừu tượng.** Java dùng dynamic dispatch: một lời gọi phương thức bị override trong constructor cha vẫn có thể chạy bản triển khai của lớp con, khi trạng thái khởi tạo riêng của lớp con chưa sẵn sàng.

Ví dụ sau độc lập và chạy được (lưu thành `Main.java`):

```java
abstract class Parent {
    protected Parent() {
        System.out.println("In parent: " + description());
    }

    protected abstract String description();
}

final class Child extends Parent {
    private String label = "ready";

    @Override
    protected String description() {
        return label;
    }
}

public class Main {
    public static void main(String[] args) {
        new Child();
    }
}
```

Chạy:

```bash
javac Main.java
java Main
```

Kết quả:

```text
In parent: null
```

`label` vẫn có giá trị mặc định `null` khi constructor `Parent` gọi `description()`; phép gán `"ready"` của lớp con xảy ra sau đó. Đây là lý do constructor cha nên kiểm tra/gán **trạng thái do chính lớp cha sở hữu**, và chỉ gọi các phương thức không thể override như phương thức `private` hoặc `final` khi phù hợp. Nếu cần hành vi phụ thuộc vào lớp con, thực hiện sau khi khởi tạo xong đối tượng.

## 6. Trường hợp `private`: chỉ các triển khai lồng bên trong

Constructor `private` của abstract class thường làm lớp con top-level không thể gọi `super(...)`. Tuy nhiên, lớp lồng cùng nest có quyền truy cập private, nên có thể dùng để giới hạn họ triển khai. Ví dụ sau là một tệp `Main.java` độc lập:

```java
abstract class Token {
    private Token() {
    }

    public static Token create() {
        return new InternalToken();
    }

    public abstract String value();

    private static final class InternalToken extends Token {
        @Override
        public String value() {
            return "internal";
        }
    }
}

public class Main {
    public static void main(String[] args) {
        Token token = Token.create();
        System.out.println(token.value());
    }
}
```

Biên dịch/chạy bằng `javac Main.java` và `java Main`, kết quả:

```text
internal
```

`Token.create()` là factory tạo lớp con nội bộ. Mã ngoài không thể `new Token()` do lớp trừu tượng, và lớp con top-level thông thường cũng không thể gọi constructor `private Token()`. Đây là kỹ thuật kiểm soát triển khai, không phải cách mặc định cho mọi abstract class.

## 7. Quy tắc chọn constructor trong thiết kế thực tế

- **Cần bảo đảm bất biến chung:** đặt tham số bắt buộc vào constructor của abstract class, kiểm tra dữ liệu ngay lúc tạo, lưu trạng thái chung vào trường `private final` khi phù hợp.
- **Cho phép mở rộng ngoài package:** thường chọn constructor `protected`; lớp con quyết định có khai báo constructor `public` cho mã sử dụng hay không.
- **Chỉ triển khai trong cùng package:** cân nhắc constructor package-private; vẫn phải xem xét phạm vi truy cập của lớp và các thành phần khác.
- **Chỉ cho phép lớp lồng triển khai:** cân nhắc `private` cùng factory tường minh; đảm bảo thiết kế thực sự cần đóng hệ phân cấp.
- **Có nhiều cách khởi tạo hợp lệ:** overload constructor và dùng `this(...)` để gom logic khởi tạo về một đường nhất quán.
- **Muốn giấu cách khởi tạo ở API công khai:** factory method có thể trả về kiểu trừu tượng; constructor của lớp con cụ thể vẫn phải nối chuỗi constructor hợp lệ.
- **Đề phòng đối tượng chưa hoàn tất khởi tạo:** tránh truy cập trạng thái subclass qua phương thức override trong constructor cha.

## 8. Lỗi thường gặp và cách chẩn đoán

| Tình huống | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `Base is abstract; cannot be instantiated` | Dùng `new Base(...)` với abstract class | Tạo lớp con cụ thể hoặc dùng factory trả về kiểu `Base` |
| `constructor Base ... cannot be applied to given types` | Compiler chèn `super()` nhưng lớp cha chỉ có constructor có tham số | Gọi `super(thamSo)` phù hợp |
| `Base(...) has private access` | Subclass không cùng nest cố gọi constructor `private` | Chọn `protected` / package-private theo phạm vi thiết kế, hoặc dùng triển khai nội bộ |
| `Base(...) has protected access` ở mã khác package | Mã sử dụng trực tiếp constructor `protected` ngoài ngữ cảnh được cho phép | Để constructor lớp con cụ thể có phạm vi phù hợp và gọi `super(...)` từ subclass |
| “call to super must be first statement” trên Java 17 | Đặt lệnh khác trước `super(...)` hoặc `this(...)` | Đưa lời gọi constructor tường minh lên đầu |
| Giá trị subclass là `null` / `0` trong constructor cha | Constructor cha gọi phương thức bị override khi subclass chưa khởi tạo trường | Bỏ lời gọi override khỏi constructor cha; hoãn hành vi đến sau khởi tạo |

Thông báo lỗi biên dịch thực tế có thể khác nhau tùy trình biên dịch và ngữ cảnh; bảng tập trung vào nguyên nhân cần kiểm tra.

## 9. Tự kiểm tra

1. Nếu `abstract class A` có constructor `protected A(int x)`, `class B extends A` ở package khác phải làm gì? → Khai báo constructor của `B` và gọi `super(x)`; Java không tự chuyển tham số cho lớp cha.
2. Vì sao `public` constructor không cho phép viết `new A(...)` khi `A` là abstract? → Quy tắc cấm khởi tạo abstract class độc lập với quyền truy cập constructor.
3. Khi nào constructor package-private phù hợp hơn `protected`? → Khi chủ đích chỉ cho phép mã trong cùng package gọi constructor, không hỗ trợ subclass ở package khác qua constructor đó.
4. Tại sao constructor lớp cha không nên gọi phương thức abstract do lớp con triển khai? → Dynamic dispatch có thể chạy mã lớp con trước khi trường lớp con được khởi tạo.
5. Nếu constructor lớp cha có tham số và không có constructor không đối số, lỗi nào có thể xảy ra ở lớp con? → `super()` ngầm định không khớp; cần `super(...)` tường minh.

**Ghi nhớ:** Constructor của abstract class là cơ chế khởi tạo phần trạng thái chung qua chuỗi constructor của lớp con. Hãy chọn modifier theo đối tượng được phép mở rộng lớp, yêu cầu bảo toàn bất biến và phạm vi API, đồng thời bảo đảm mọi đường constructor đi đến một `super(...)` hợp lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-classes-008">Chuỗi kế thừa qua nhiều lớp trừu tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Chuỗi kế thừa qua nhiều lớp trừu tượng trong Java

Khi một lớp trừu tượng kế thừa một lớp trừu tượng khác, Java hình thành **chuỗi kế thừa nhiều tầng** (*multilevel inheritance*). Mỗi tầng có thể kế thừa trạng thái, hành vi đã cài đặt và những phương thức trừu tượng còn phải hoàn thiện. Tầng cuối chỉ trở thành lớp cụ thể khi đáp ứng hết các nghĩa vụ trừu tượng được kế thừa.

Bài này tập trung vào chuỗi **lớp trừu tượng → lớp trừu tượng → lớp cụ thể**, cách Java xác định phương thức phải cài đặt, thứ tự khởi tạo, tính đa hình và cách thiết kế chuỗi dễ bảo trì.

## 1. Mô hình và quy tắc cốt lõi

```java
abstract class A {
    abstract void stepA();
}

abstract class B extends A {
    abstract void stepB();
}

class C extends B {
    @Override
    void stepA() {
        System.out.println("A");
    }

    @Override
    void stepB() {
        System.out.println("B");
    }
}
```

Sơ đồ quan hệ:

```text
A (abstract): stepA() chưa cài đặt
    ↑ extends
B (abstract): kế thừa stepA(), thêm stepB()
    ↑ extends
C (concrete): cài đặt stepA() và stepB()
```

Đọc sơ đồ từ dưới lên: `C extends B`, `B extends A`, vì vậy `C` cũng là một `A` theo quan hệ *is-a*. Java cho phép nhiều **tầng** kế thừa, nhưng mỗi lớp chỉ được `extends` **một lớp cha trực tiếp**; không có kế thừa đa lớp kiểu `class C extends A, B`.

Những điểm cần nhớ:

- Lớp `abstract` có thể kế thừa lớp `abstract` hoặc lớp thường; lớp con của nó cũng có thể tiếp tục là `abstract`.
- Một lớp trừu tượng **không bắt buộc** cài đặt ngay tất cả phương thức trừu tượng kế thừa. Nó có thể cài đặt một phần, thêm nghĩa vụ mới, hoặc không thêm gì.
- Một lớp **không khai báo `abstract`** phải cung cấp cài đặt hợp lệ cho **mọi** phương thức trừu tượng còn tồn đọng trong cả chuỗi. Cài đặt có thể được kế thừa từ lớp cha ở tầng trung gian; không nhất thiết viết lại trong lớp cuối.
- Không thể `new A()` hay `new B()`, dù chúng có constructor. Có thể tạo `new C()` rồi giữ đối tượng bằng biến kiểu `A`, `B` hoặc `C`.
- `abstract` chỉ định một lớp chưa được khởi tạo trực tiếp hoặc một phương thức chưa có phần thân. Một lớp có thể là `abstract` dù **không chứa phương thức trừu tượng nào**.

**Quy tắc xác định nghĩa vụ:** bắt đầu từ lớp cha gốc, theo dõi các phương thức trừu tượng; mỗi cài đặt cụ thể hợp lệ sẽ giải quyết một nghĩa vụ, mỗi khai báo `abstract` mới sẽ thêm nghĩa vụ. Tại lớp cụ thể đầu tiên, danh sách nghĩa vụ chưa giải quyết phải rỗng.

## 2. Mỗi tầng đóng góp điều gì?

| Tầng | Vai trò | Có thể để lại phương thức trừu tượng? | Có thể khởi tạo trực tiếp? |
| --- | --- | --- | --- |
| `A` trừu tượng | Đặt hợp đồng chung, cung cấp dữ liệu/hành vi dùng lại | Có | Không |
| `B extends A` trừu tượng | Chuyên biệt hóa, cài đặt một phần, bổ sung hợp đồng | Có | Không |
| `C extends B` cụ thể | Hoàn tất những phần còn thiếu, tạo đối tượng sử dụng được | Không | Có |

Tầng trung gian không chỉ là một cái tên để kéo dài cây kế thừa. Nó nên gom một tập quy tắc hoặc hành vi chung có ý nghĩa cho **nhiều** lớp cụ thể, ví dụ tài nguyên số kế thừa tài nguyên nói chung rồi được chuyên biệt thành PDF, ảnh hoặc âm thanh.

Một phương thức đã có phần thân ở `A` được `B` và `C` kế thừa bình thường, trừ khi được ghi đè hợp lệ. Một phương thức còn `abstract` ở `A` có thể được `B` cài đặt và khi đó `C` không cần cài đặt lại nó.

## 3. Ví dụ chạy được: ba tầng và cài đặt tăng dần

Lưu toàn bộ chương trình sau vào tệp **`Main.java`**. Các lớp phụ được để cùng tệp và chỉ `Main` là `public` để ví dụ có thể biên dịch độc lập trên Java 17+.

```java
abstract class Resource {
    private final String code;

    protected Resource(String code) {
        this.code = code;
        System.out.println("Resource -> " + code);
    }

    protected String code() {
        return code;
    }

    public abstract String kind();

    public abstract int units();

    public String description() {
        return kind() + ":" + code() + ":" + units();
    }
}

abstract class DigitalResource extends Resource {
    protected DigitalResource(String code) {
        super(code);
        System.out.println("DigitalResource");
    }

    @Override
    public String kind() {
        return "digital";
    }

    public abstract String format();

    public String fullDescription() {
        return description() + ":" + format();
    }
}

final class PdfResource extends DigitalResource {
    private final int pages;

    public PdfResource(String code, int pages) {
        super(code);
        this.pages = pages;
        System.out.println("PdfResource");
    }

    @Override
    public int units() {
        return pages;
    }

    @Override
    public String format() {
        return "PDF";
    }
}

public class Main {
    public static void main(String[] args) {
        DigitalResource digital = new PdfResource("R-07", 12);
        Resource general = digital;

        System.out.println(digital.fullDescription());
        System.out.println(general.description());
    }
}
```

Biên dịch và chạy trong thư mục chứa `Main.java`:

```bash
javac Main.java
java Main
```

Kết quả chuẩn:

```text
Resource -> R-07
DigitalResource
PdfResource
digital:R-07:12:PDF
digital:R-07:12
```

**Phân tích lần lượt các tầng:**

1. `Resource` khai báo hai nghĩa vụ: `kind()` và `units()`. Nó cũng định nghĩa constructor, trường `code` và phương thức cụ thể `description()`. Bên trong `description()`, lời gọi `kind()` và `units()` có thể dẫn đến cài đặt ở lớp con.
2. `DigitalResource` vẫn là `abstract`, nhưng đã cài đặt `kind()`. Nó kế thừa nghĩa vụ `units()` chưa giải quyết và thêm nghĩa vụ `format()`. Phương thức `fullDescription()` tái sử dụng `description()` ở tầng gốc.
3. `PdfResource` giải quyết `units()` và `format()`. Vì `kind()` đã được tầng giữa cài đặt nên nó trở thành lớp cụ thể hợp lệ. `final` ở đây là lựa chọn thiết kế nhằm không cho phép lớp khác kế thừa tiếp `PdfResource`; nó không phải yêu cầu của chuỗi trừu tượng.
4. Dòng `DigitalResource digital = new PdfResource(...)` là đa hình: **kiểu tham chiếu** là `DigitalResource`, **kiểu đối tượng thực tế** là `PdfResource`. Dòng `Resource general = digital` không tạo thêm đối tượng, chỉ tạo thêm tham chiếu tới cùng đối tượng.

Hai lần in mô tả đều dùng `kind()` của `DigitalResource` và `units()` của `PdfResource` thông qua **dynamic dispatch**. Mã ở tầng `Resource` không cần biết tên lớp cụ thể nào sẽ thực hiện các phương thức đó.

## 4. Phương thức trừu tượng được “truyền” qua các tầng như thế nào?

Xét chuỗi nghĩa vụ từ ví dụ:

```text
Resource:
    kind()    -> abstract
    units()   -> abstract

DigitalResource:
    kind()    -> implemented
    units()   -> inherited abstract
    format()  -> new abstract

PdfResource:
    kind()    -> inherited implementation
    units()   -> implemented
    format()  -> implemented
```

Đừng hiểu nhầm rằng chỉ những phương thức `abstract` **được viết trực tiếp** trong lớp cha gần nhất mới cần cài đặt. `PdfResource` còn chịu nghĩa vụ `units()` bắt nguồn từ `Resource`, dù `DigitalResource` không khai báo lại phương thức đó.

Nếu bỏ `units()` khỏi `PdfResource` mà giữ `PdfResource` là lớp cụ thể, chương trình **không biên dịch được**: lớp đó chưa cài đặt đủ hợp đồng trừu tượng. Hai hướng sửa hợp lệ là cung cấp `units()` trong `PdfResource` hoặc giữ lớp này là `abstract` để lớp con khác tiếp tục hoàn thiện; lựa chọn phụ thuộc mục đích thiết kế.

Một tầng trung gian cũng có thể cài đặt **toàn bộ** phương thức trừu tượng được thừa kế nhưng vẫn tự khai báo `abstract`: điều này hợp lệ, chẳng hạn khi muốn ngăn khởi tạo trực tiếp hoặc dự định bổ sung quy tắc ở lớp con. Từ khóa `abstract` trên lớp và số lượng phương thức `abstract` không có quan hệ một-một.

## 5. Override và trừu tượng hóa lại trong chuỗi

Các quy tắc override vẫn áp dụng ở mọi tầng: chữ ký phải tương thích, không được thu hẹp quyền truy cập hoặc mở rộng checked exception trái quy tắc; `@Override` giúp phát hiện sai chữ ký. Phương thức `final` không thể override, `private` không tạo hook kế thừa và `static` dùng hiding thay vì dynamic dispatch. Trách nhiệm của lớp cụ thể và checklist chữ ký được phân tích sâu hơn trong bài về hoàn thiện triển khai.

Một trường hợp đáng chú ý là lớp trung gian có thể **trừu tượng hóa lại** một phương thức có thân ở lớp cha (miễn phương thức đó không `final`) để buộc các lớp cụ thể bên dưới cung cấp cách làm riêng:

```java
abstract class Base {
    String status() {
        return "DEFAULT";
    }
}

abstract class Specialized extends Base {
    @Override
    abstract String status();
}

final class Ready extends Specialized {
    @Override
    String status() {
        return "READY";
    }
}
```

Đoạn mã chỉ minh họa khai báo lớp, không có `main`. `Ready` phải cài đặt `status()` vì `Specialized` đã khai báo lại phương thức đó là `abstract`, dù `Base` từng có phần thân. Dùng kỹ thuật này có chủ đích vì nó làm hợp đồng nghiêm ngặt hơn.

## 6. Constructor vẫn chạy xuyên suốt chuỗi

`abstract` không cấm constructor. Khi tạo `new PdfResource("R-07", 12)`, constructor chạy theo chuỗi từ `Object` đến `Resource`, `DigitalResource`, rồi `PdfResource`; các dòng stdout trong ví dụ mục 3 thể hiện chính thứ tự này. Mỗi tầng phải gọi được constructor cha phù hợp, tường minh bằng `super(...)` hoặc qua `super()` ngầm định khi có constructor không đối số khả dụng.

Không lặp lại ví dụ bẫy khởi tạo ở đây: quy tắc về thứ tự initializer và nguy cơ gọi phương thức override khi trường lớp con chưa sẵn sàng thuộc bài chuyên về constructor. Trong chuỗi kế thừa, hãy để constructor khởi tạo trạng thái và thực hiện hành vi đa hình sau khi đối tượng được xây dựng xong.

## 7. Kiểu tham chiếu, phương thức nhìn thấy và ép kiểu

Với đối tượng được khai báo như sau:

```java
PdfResource pdf = new PdfResource("R-08", 8);
DigitalResource digital = pdf;
Resource resource = pdf;
```

Các dòng trên là đoạn minh họa đặt trong ngữ cảnh các lớp ở mục 3. Cả ba biến cùng trỏ tới **một** đối tượng `PdfResource`, nhưng kiểu tham chiếu quyết định các thành viên mà trình biên dịch cho phép gọi:

| Biến | Gọi được theo kiểu khai báo | Không gọi trực tiếp được |
| --- | --- | --- |
| `pdf` | `description()`, `fullDescription()`, `format()`, `units()` | — |
| `digital` | `description()`, `fullDescription()`, `format()`, `units()` | — |
| `resource` | `description()`, `kind()`, `units()` | `format()`, `fullDescription()` |

`resource.description()` vẫn chạy các cài đặt override ở đối tượng `PdfResource`/`DigitalResource`, mặc dù `resource` không gọi trực tiếp được `format()`. **Quyền gọi tại thời điểm biên dịch** và **cài đặt được chọn tại thời điểm chạy** là hai vấn đề khác nhau.

Nếu thật sự cần thao tác đặc thù với tầng giữa, có thể kiểm tra kiểu trước khi ép:

```java
if (resource instanceof DigitalResource digitalResource) {
    System.out.println(digitalResource.format());
}
```

Đây là cú pháp *pattern matching for instanceof* được hỗ trợ trên Java 17. Trong thiết kế tốt, hãy ưu tiên đặt thao tác chung cần thiết trên hợp đồng của tầng phù hợp, thay vì liên tục `instanceof`/ép kiểu.

## 8. Thiết kế chuỗi trừu tượng hợp lý

Một chuỗi nhiều tầng có ích khi từng tầng đại diện cho **một khái niệm chuyên biệt có nghĩa**:

```text
Resource (tất cả tài nguyên)
    └── DigitalResource (tài nguyên số có định dạng)
            ├── PdfResource
            └── ImageResource
```

Ví dụ, `Resource` sở hữu định danh, `DigitalResource` biết đặc trưng chung của tài nguyên số, còn `PdfResource` và `ImageResource` cài đặt chi tiết riêng. Tầng giữa đáng tồn tại nếu nhiều lớp cùng dùng logic của nó.

Trong khi thiết kế, cần:

1. Xác định bất biến và dữ liệu chung ở lớp gốc; dùng `private` cho trạng thái cần bảo vệ, mở phương thức `protected`/`public` khi thật sự cần.
2. Đặt phương thức trừu tượng ở tầng thấp nhất có thể diễn đạt chính xác **nghĩa vụ chung**, tránh ép các lớp không liên quan triển khai hành vi vô nghĩa.
3. Cài đặt logic dùng chung một lần ở tầng phù hợp; dùng phương thức `final` khi muốn giữ bất biến trong thuật toán và không cho lớp dưới ghi đè bước quan trọng.
4. Đảm bảo các lớp con giữ đúng hợp đồng của cha: mọi nơi kỳ vọng `Resource` vẫn phải làm việc đúng với `PdfResource`. Không thay đổi ý nghĩa `units()` giữa các nhánh đến mức người dùng API hiểu sai.
5. Tránh tạo tầng `abstract` chỉ để đặt tên cho một bước chuyển tiếp hoặc để giảm vài dòng mã. Mỗi tầng thêm độ phụ thuộc, khiến việc đọc và thay đổi cây kế thừa khó hơn.
6. Nếu các loại đối tượng chỉ cần chia sẻ **khả năng** mà không có một quan hệ *is-a* chặt chẽ, cân nhắc `interface`; nếu chỉ muốn tái sử dụng một thành phần, cân nhắc **composition**. Java vẫn cho phép một lớp `extends` một lớp và `implements` nhiều interface.

Một mẫu hữu ích là **Template Method**: tầng trừu tượng triển khai quy trình ổn định và gọi những bước `protected abstract` để lớp con hoàn thiện. Cần tránh gọi quy trình có thể override từ constructor và định nghĩa rõ tiền điều kiện, hậu điều kiện của từng bước.

## 9. Những lỗi và ngộ nhận thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Lớp cuối báo chưa cài đặt phương thức trừu tượng | Nghĩa vụ ở tầng ông/bà vẫn tồn tại | Rà soát toàn chuỗi; cài đặt đủ phương thức hoặc khai báo lớp tiếp tục `abstract` |
| `new` lớp trung gian trừu tượng bị lỗi | Không thể khởi tạo trực tiếp lớp `abstract` | Khởi tạo lớp cụ thể và giữ tham chiếu bằng kiểu trừu tượng |
| Lỗi constructor `super()` | Cha không có constructor không tham số khả dụng | Gọi `super(args)` phù hợp trong constructor con |
| Override bị lỗi biên dịch | Sai chữ ký, giảm quyền truy cập, không phù hợp kiểu trả về/`throws`, hoặc đụng phương thức `final` | So lại khai báo chính xác ở tất cả tầng liên quan, dùng `@Override` |
| Gọi phương thức riêng của lớp con qua biến cha không biên dịch | Kiểu tham chiếu không khai báo phương thức đó | Đưa hành vi chung lên API cha hoặc kiểm tra kiểu rồi ép khi cần |
| Giá trị trường con bất ngờ là `null`/`0` trong constructor cha | Constructor cha gọi phương thức bị override trước khi trường con khởi tạo | Không dispatch tới phương thức override trong constructor |
| Cây kế thừa quá sâu, nhiều lớp chỉ truyền tiếp không thêm ý nghĩa | Mô hình hóa sai trách nhiệm, lạm dụng kế thừa | Giảm tầng, tách khả năng thành interface hoặc thành phần composition |

Một số ranh giới quan trọng: Java **không** cho kế thừa nhiều lớp trực tiếp; `abstract` không có nghĩa mọi phương thức đều trừu tượng; và `super` cho phép truy cập hành vi cha phù hợp nhưng không biến một phương thức `abstract` chưa có phần thân thành thứ có thể thực thi.

## 10. Tự kiểm tra kiến thức

**Bài 1 — xác định nghĩa vụ.** Giả sử `A` khai báo `abstract void x()` và `abstract void y()`. `B extends A` (vẫn `abstract`) chỉ cài đặt `x()` rồi thêm `abstract void z()`. Lớp cụ thể `C extends B` phải cài đặt những phương thức nào?

<details>
<summary>Đáp án và lý do</summary>

`C` phải cài đặt `y()` và `z()`. `x()` đã được `B` cung cấp phần thân nên được `C` kế thừa. Trừ khi `C` muốn thay đổi hành vi `x()`, không cần override lại.
</details>

**Bài 2 — đa hình.** Với chương trình mục 3, thay khai báo bằng `Resource value = new PdfResource("R-09", 5);`. `value.description()` có dùng `Resource.kind()` không? Có thể gọi `value.fullDescription()` trực tiếp không?

<details>
<summary>Đáp án và lý do</summary>

`Resource` chỉ khai báo `kind()` là trừu tượng; khi chạy, `description()` gọi `kind()` của `DigitalResource` và `units()` của `PdfResource`. Không thể gọi trực tiếp `value.fullDescription()` vì kiểu khai báo `Resource` không có phương thức đó. Có thể gọi qua tham chiếu `DigitalResource` hoặc phép ép kiểu hợp lệ.
</details>

**Bài 3 — mở rộng.** Tạo `abstract class DocumentResource extends DigitalResource` bổ sung phương thức `abstract String category()`. Sau đó tạo `final class InvoicePdf extends DocumentResource` để hoàn thành `units()`, `format()` và `category()`. Giải thích vì sao `kind()` không cần viết lại. Hãy tự in thứ tự constructor và kiểm tra `description()` khi giữ đối tượng bằng biến `Resource`.

**Checklist trước khi chốt một chuỗi kế thừa:** mọi tầng có trách nhiệm rõ ràng; nghĩa vụ `abstract` được theo dõi qua toàn chuỗi; lớp cuối thực hiện đủ hợp đồng; constructor gọi `super(...)` hợp lệ; không gọi hành vi override trong constructor; API giữ đúng tính đa hình và các quy tắc override.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-classes-009">Lớp con cụ thể và trách nhiệm hoàn thiện triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp con cụ thể và trách nhiệm hoàn thiện triển khai trong Java

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích vì sao lớp con **cụ thể** (concrete class) phải cung cấp đủ triển khai cho những phương thức trừu tượng còn tồn tại trong hệ phân cấp kế thừa.
- Phân biệt phương thức do lớp con tự triển khai với phương thức cụ thể mà nó **kế thừa** từ lớp cha hoặc nhận được qua phương thức `default` của interface.
- Nhận biết khi nào một lớp con phải tiếp tục khai báo `abstract`, cách kiểm tra chữ ký `@Override` và các lỗi biên dịch thường gặp.
- Thiết kế lớp cha trừu tượng để mô tả phần chung, rồi triển khai hành vi biến thiên tại những lớp con phù hợp.

**Bối cảnh:** Đây là một bài về *lớp trừu tượng* (`abstract class`) của Java. Trọng tâm là trách nhiệm hoàn thiện việc triển khai khi một lớp con chuyển từ trạng thái trừu tượng sang có thể khởi tạo đối tượng.

## 2. Lớp con cụ thể là gì?

Một lớp không khai báo `abstract` là **lớp cụ thể**. Nếu lớp đó kế thừa một lớp trừu tượng, trình biên dịch chỉ chấp nhận khi mọi phương thức trừu tượng mà lớp phải thực hiện đều đã có triển khai cụ thể hợp lệ.

Ví dụ về ý tưởng:

~~~java
abstract class Vehicle {
    abstract void move();
}

class Bicycle extends Vehicle {
    @Override
    void move() {
        System.out.println("Pedaling");
    }
}
~~~

`Vehicle` quy định rằng mọi phương tiện con phải có hành vi `move()`, nhưng chưa chỉ ra cách thực hiện. `Bicycle` hoàn thiện hợp đồng đó bằng một phương thức có thân; do vậy `new Bicycle()` là hợp lệ. Ngược lại, `new Vehicle()` không hợp lệ vì `Vehicle` là lớp trừu tượng.

Trách nhiệm này xét trên **toàn bộ chuỗi kế thừa và các interface có liên quan**, không chỉ những phương thức trừu tượng được khai báo trực tiếp ở lớp cha gần nhất.

### Điều kiện cốt lõi

Một lớp con cụ thể cần:

1. Có triển khai hợp lệ cho tất cả phương thức trừu tượng còn chưa được giải quyết, bao gồm các hợp đồng kế thừa gián tiếp và phương thức trừu tượng từ interface.
2. Tuân thủ quy tắc ghi đè của Java: tên, danh sách tham số, kiểu trả về, phạm vi truy cập và ngoại lệ kiểm tra (`checked exception`).
3. Gọi được constructor phù hợp của lớp cha khi khởi tạo; các trường `final` cần được khởi tạo đúng quy tắc như ở mọi lớp Java.

Điều kiện thứ ba thuộc quy tắc khởi tạo đối tượng nói chung, còn hai điều kiện đầu là phần thiết yếu của việc hoàn thiện hợp đồng trừu tượng.

## 3. Ai có trách nhiệm triển khai và khi nào được hoãn lại?

Lớp trung gian được phép hoãn nghĩa vụ phương thức `abstract` khi chính nó vẫn khai báo `abstract`. Lớp đầu tiên không còn `abstract` phải có triển khai hợp lệ cho mọi nghĩa vụ còn thiếu trong toàn bộ hệ phân cấp; nếu chưa sẵn sàng, hãy giữ lớp đó là `abstract` thay vì để lại một khai báo lớp cụ thể không biên dịch.

Điều cần kiểm tra là **nghĩa vụ còn tồn tại**, không phải số lượng phương thức mà lớp cuối tự viết. Triển khai phù hợp có thể đã được cung cấp ở lớp tổ tiên hoặc qua phương thức `default` của interface; phần kế tiếp minh họa rõ trường hợp kế thừa một triển khai có sẵn.

## 4. Không nhất thiết lớp con cuối cùng tự viết mọi phương thức

Hợp đồng chỉ đòi hỏi một **triển khai cụ thể hợp lệ đã tồn tại trong hệ phân cấp**, không bắt buộc lớp con cuối cùng viết lại mọi phương thức. Ví dụ:

~~~java
interface Printer {
    void print();
}

abstract class BasePrinter implements Printer {
    @Override
    public void print() {
        System.out.println("Printed");
    }
}

class OfficePrinter extends BasePrinter {
    // Kế thừa triển khai print() có sẵn.
}
~~~

`OfficePrinter` là lớp cụ thể hợp lệ mặc dù không khai báo `print()`. Triển khai `public void print()` của `BasePrinter` đã đáp ứng giao diện `Printer`. Việc `BasePrinter` vẫn được khai báo `abstract` không làm triển khai `print()` trở thành trừu tượng.

Tương tự, một phương thức `default` phù hợp từ interface có thể cung cấp hành vi mặc định. Tuy nhiên, khi có xung đột giữa các phương thức `default` không liên quan, lớp thực thi phải giải quyết xung đột theo quy tắc kế thừa của Java.

Vì vậy, khi đọc lỗi thiếu triển khai, hãy lần ngược toàn bộ hệ phân cấp để tìm **phương thức trừu tượng chưa được đáp ứng**, thay vì chỉ đếm số lượng phương thức `@Override` trong lớp cuối.

## 5. Ví dụ hoàn chỉnh: hoàn thiện theo nhiều tầng kế thừa

Lưu toàn bộ đoạn sau vào tệp `Main.java`:

~~~java
interface Exportable {
    String format();

    default String fileExtension() {
        return ".txt";
    }
}

abstract class Document implements Exportable {
    private final String title;

    protected Document(String title) {
        this.title = title;
    }

    public final String title() {
        return title;
    }

    public abstract String body();

    public String preview() {
        return title + " | " + body();
    }
}

abstract class TextDocument extends Document {
    protected TextDocument(String title) {
        super(title);
    }

    @Override
    public final String format() {
        return "text/plain";
    }
}

final class Note extends TextDocument {
    private final String content;

    Note(String title, String content) {
        super(title);
        this.content = content;
    }

    @Override
    public String body() {
        return content;
    }
}

public class Main {
    public static void main(String[] args) {
        Document doc = new Note("Week 1", "Review contracts");
        Exportable export = (Exportable) doc;

        System.out.println(doc.preview());
        System.out.println(export.format());
        System.out.println(export.fileExtension());
    }
}
~~~

Biên dịch/chạy với JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 Main.java
java Main
~~~

Kết quả chuẩn:

~~~text
Week 1 | Review contracts
text/plain
.txt
~~~

**Phân tích từng tầng:**

1. `Exportable` đặt ra phương thức trừu tượng `format()` và cung cấp sẵn phương thức `default fileExtension()`.
2. `Document` là lớp trừu tượng, chưa triển khai `format()` của interface; nó còn bổ sung hợp đồng `body()`. Bên cạnh đó, `title()` và `preview()` đã có thân; `preview()` gọi `body()` qua cơ chế đa hình.
3. `TextDocument` giải quyết `format()` bằng một triển khai `final` nhưng vẫn còn `body()` trừu tượng, vì vậy tiếp tục là lớp `abstract`.
4. `Note` triển khai `body()`. Nó **kế thừa** `format()`, `preview()` và `title()`, đồng thời sử dụng `fileExtension()` mặc định. Như vậy tất cả hợp đồng đều được hoàn thiện, cho phép tạo `new Note(...)`.
5. Biến `doc` có kiểu khai báo `Document` nhưng giữ đối tượng `Note`. Lời gọi `doc.preview()` chạy mã có sẵn ở `Document`, còn lời gọi `body()` bên trong được phân giải động đến triển khai của `Note`.

`final class Note` chỉ thể hiện lựa chọn ngăn kế thừa tiếp, **không phải** điều kiện để một lớp trở thành cụ thể. Viết `class Note extends TextDocument` cũng hợp lệ. Ngược lại, `final` không được dùng trên phương thức `abstract` vì một bên cấm ghi đè, một bên đòi hỏi lớp con cung cấp triển khai.

### Vì sao constructor lớp cha vẫn chạy?

`Document` là lớp trừu tượng nên không tạo trực tiếp qua `new Document(...)`, nhưng vẫn có constructor `protected Document(String)` để khởi tạo phần trạng thái chung. Khi chạy `new Note(...)`, chuỗi gọi constructor là `Note` → `TextDocument` → `Document`, rồi việc khởi tạo hoàn tất theo thứ tự từ lớp cha trở xuống. Lớp con cụ thể phải gọi constructor lớp cha phù hợp bằng `super(...)`, tường minh hoặc ngầm định.

**Lưu ý thiết kế:** Không nên gọi các phương thức có thể bị ghi đè từ constructor của lớp cha trừu tượng. Phương thức có thể dispatch xuống lớp con khi các trường của lớp con chưa được khởi tạo xong.

## 6. Ví dụ trực tiếp: kết hợp hợp đồng từ abstract class và interface

Lưu đoạn sau vào `Example2.java` (độc lập với `Main.java` ở trên):

~~~java
interface Describable {
    String describe();
}

abstract class Shape implements Describable {
    public abstract double area();

    public String kind() {
        return "shape";
    }
}

final class Square extends Shape {
    private final double side;

    Square(double side) {
        this.side = side;
    }

    @Override
    public double area() {
        return side * side;
    }

    @Override
    public String describe() {
        return "square: " + area();
    }
}

public class Example2 {
    public static void main(String[] args) {
        Shape shape = new Square(3.0);
        System.out.println(shape.kind());
        System.out.println(shape.describe());
    }
}
~~~

~~~bash
javac --release 17 Example2.java
java Example2
~~~

~~~text
shape
square: 9.0
~~~

`Shape` chưa thực hiện `Describable.describe()` và còn khai báo `area()` trừu tượng. Vì vậy `Square` phải đáp ứng **cả hai** phương thức. `kind()` có thân trong `Shape` nên được kế thừa trực tiếp; không cần ghi đè.

## 7. Ghi đè đúng chữ ký để thực hiện hợp đồng

Một phương thức chỉ hoàn thiện hợp đồng trừu tượng khi nó thật sự **override** phương thức được kế thừa. Cần kiểm tra:

| Thành phần | Yêu cầu khi override | Ví dụ/ý nghĩa |
| --- | --- | --- |
| Tên và tham số | Cùng chữ ký phù hợp (có xét quy tắc tương đương chữ ký Java) | `run()` khác `run(int)`; khai báo sau chỉ là overload. |
| Kiểu trả về | Cùng kiểu hoặc kiểu trả về hiệp biến hợp lệ đối với kiểu tham chiếu | Cha trả `Number`, con có thể trả `Integer`. |
| Phạm vi truy cập | Không được thu hẹp mức truy cập của phương thức được ghi đè | Cha `public` thì con phải `public`. |
| Checked exception | Không khai báo checked exception mới hoặc rộng hơn quy tắc cho phép | Cha `throws IOException`, con có thể bỏ `throws`. |
| `@Override` | Nên dùng để trình biên dịch kiểm tra ý định ghi đè | Sai tên/tham số sẽ được phát hiện ngay. |

Ví dụ về kiểu trả về hiệp biến:

~~~java
abstract class Provider {
    public abstract Number number();
}

class IntProvider extends Provider {
    @Override
    public Integer number() {
        return 42;
    }
}
~~~

`Integer` là lớp con của `Number`, nên `IntProvider.number()` thỏa hợp đồng và `IntProvider` là lớp cụ thể.

Ngược lại, đoạn **cố ý sai, không biên dịch** sau đây cho thấy lỗi overload:

~~~java
abstract class Base {
    abstract String read();
}

class Wrong extends Base {
    String read(int index) {
        return "item " + index;
    }
}
~~~

`read(int)` là một phương thức khác với `read()`; nó không hoàn thiện hợp đồng của `Base`. Trình biên dịch sẽ báo lỗi rằng `Wrong` chưa triển khai phương thức trừu tượng `read()`. Cách sửa là khai báo `@Override String read() { ... }` đúng chữ ký hoặc giữ `Wrong` là lớp `abstract` nếu muốn tiếp tục trì hoãn việc thực hiện.

Một lỗi khác là dùng mức truy cập yếu hơn:

~~~java
abstract class PublicBase {
    public abstract void execute();
}

// CỐ Ý SAI: không biên dịch.
class InvalidChild extends PublicBase {
    @Override
    protected void execute() {
        // ...
    }
}
~~~

`execute()` của cha là `public`, còn con viết `protected` nên vi phạm quy tắc override. Sửa thành `public void execute()`.

Lưu ý: Phương thức `private`, `static` hay `final` không thể được khai báo `abstract`; việc đặt `static` cùng tên trong lớp con cũng không thay thế một phương thức instance trừu tượng.

## 8. Quy trình kiểm tra một lớp con cụ thể

Khi định nghĩa `class X extends Y`, hãy thực hiện theo thứ tự:

1. **Liệt kê hợp đồng:** Xem các phương thức trừu tượng còn tồn tại từ `Y`, các lớp tổ tiên và các interface mà X phải đáp ứng.
2. **Tìm triển khai kế thừa:** Kiểm tra xem lớp cha đã cung cấp phương thức cụ thể hợp lệ hoặc interface đã có `default` thích hợp hay chưa.
3. **Triển khai phần còn thiếu:** Viết phương thức với chữ ký và quy tắc override chính xác; dùng `@Override` trên từng phương thức cần ghi đè.
4. **Quyết định abstract hay concrete:** Nếu vẫn có hợp đồng chưa được thực hiện, để lớp là `abstract`; nếu muốn khởi tạo được thì phải hoàn thiện toàn bộ.
5. **Biên dịch và kiểm thử hành vi:** Kiểm tra constructor, các lời gọi qua kiểu lớp cha/interface và đầu ra thực tế.

Bảng tóm tắt:

| Trường hợp | Khai báo hợp lệ? | Có tạo trực tiếp bằng `new`? |
| --- | --- | --- |
| Lớp cha trừu tượng còn phương thức chưa có thân | Có, nếu lớp vẫn là `abstract` | Không |
| Lớp con `abstract` chưa triển khai hết | Có | Không |
| Lớp con không `abstract` nhưng còn thiếu triển khai | Không: lỗi biên dịch | Không |
| Lớp con cụ thể tự triển khai đủ | Có | Có, nếu constructor truy cập được |
| Lớp con cụ thể kế thừa đủ triển khai từ tổ tiên | Có | Có, nếu constructor truy cập được |

### Lỗi phổ biến và cách xử lý

- **Chỉ triển khai phương thức ở lớp cha trực tiếp:** Quên phương thức trừu tượng từ tổ tiên hoặc interface. Hãy kiểm tra toàn bộ chuỗi `extends` và `implements`.
- **Nhầm overload với override:** Viết `calculate(int)` khi hợp đồng yêu cầu `calculate()`. Thêm `@Override` để phát hiện sai chữ ký.
- **Bỏ `public` khi thực hiện phương thức interface:** Phương thức trừu tượng thông thường của interface là `public`, vì vậy triển khai cũng cần `public`.
- **Cho rằng kế thừa từ lớp `abstract` thì lớp con cũng bắt buộc `abstract`:** Sai; lớp con có thể là cụ thể nếu mọi nghĩa vụ triển khai đã được đáp ứng.
- **Ghi đè lại phương thức đã đủ dùng:** Không cần thiết; có thể dùng trực tiếp triển khai kế thừa. Hãy override khi hành vi thực sự cần khác và phương thức cho phép ghi đè.
- **Gọi phương thức override trong constructor lớp cha:** Có thể đụng trạng thái lớp con chưa sẵn sàng; ưu tiên hoàn tất khởi tạo trước khi gọi hành vi đa hình.

## 9. Tự kiểm tra

Xét đoạn mã:

~~~java
interface Identified {
    String id();
}

abstract class Entity implements Identified {
    public abstract boolean valid();

    public String label() {
        return id();
    }
}

abstract class StoredEntity extends Entity {
    @Override
    public final boolean valid() {
        return true;
    }
}

class Order extends StoredEntity {
    @Override
    public String id() {
        return "ORD-1";
    }
}
~~~

**Câu hỏi:**

1. `StoredEntity` đã hoàn thiện phương thức nào? Vì sao nó vẫn có thể khai báo `abstract`?
2. `Order` phải triển khai những phương thức nào để là lớp cụ thể?
3. `new Order().label()` trả về gì?
4. Có thể ghi đè `valid()` trong `Order` không?

**Đáp án và giải thích:** `StoredEntity` đã triển khai `valid()`, vẫn là `abstract` vì chưa triển khai `id()` và vì Java cho phép khai báo lớp trừu tượng ngay cả khi không còn phương thức trừu tượng. `Order` chỉ cần triển khai `id()`: nó kế thừa `valid()` từ `StoredEntity` và `label()` từ `Entity`. Biểu thức `new Order().label()` trả `"ORD-1"`. `Order` không thể override `valid()` do phương thức này được khai báo `final`.

## 10. Ghi nhớ

**Lớp con cụ thể chịu trách nhiệm bảo đảm mọi hợp đồng trừu tượng đã có triển khai hợp lệ khi kết thúc chuỗi kế thừa.** Triển khai có thể đến từ chính lớp con, lớp tổ tiên hoặc phương thức mặc định thích hợp của interface; lớp trung gian `abstract` được phép hoãn trách nhiệm. Trình biên dịch kiểm tra nghĩa vụ này bằng quy tắc override, nhưng chất lượng hành vi của triển khai vẫn cần được xác minh bằng kiểm thử và thiết kế hợp đồng rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abstract-classes-010">Tổ hợp modifier hợp lệ và xung đột abstract với final</a>

<details>
<summary>Xem chi tiết</summary>

# Tổ hợp modifier hợp lệ và xung đột `abstract` với `final` trong Java

`abstract` và `final` đều liên quan đến kế thừa nhưng mang ý nghĩa khác nhau. Ở mức **lớp**, `abstract` ngăn khởi tạo trực tiếp và cho phép khai báo các phương thức cần lớp con triển khai; `final` ngăn lớp khác kế thừa. Ở mức **phương thức**, `abstract` khai báo hành vi chưa có thân, còn `final` bảo vệ một triển khai đã có khỏi việc bị override.

Điểm dễ nhầm là **một `abstract class` có thể chứa trường `final`, phương thức cụ thể `final`, và được kế thừa bởi một lớp con `final`**. Xung đột chỉ xuất hiện khi áp dụng `abstract` cùng `final` cho **chính một khai báo lớp hoặc phương thức**.

Bài học sử dụng Java 17+ và tập trung vào modifier của lớp trừu tượng, phương thức trừu tượng, cũng như những ngoại lệ theo vị trí khai báo.

## 1. Đọc modifier theo nơi nó được khai báo

| Chủ thể | `abstract` | `final` |
| --- | --- | --- |
| Lớp | Không thể `new` trực tiếp; có thể có phương thức trừu tượng. | Không thể bị `extends` bởi lớp khác. |
| Phương thức instance | Không có thân phương thức, kết thúc bằng `;`; lớp con cụ thể phải đáp ứng hợp đồng kế thừa. | Có triển khai và không thể bị ghi đè. |
| Trường | Không hợp lệ. | Chỉ được gán theo quy tắc khởi tạo của Java, sau đó không được gán lại. |
| Constructor | Không hợp lệ. | Không hợp lệ. |

Một `abstract class` **có thể không chứa phương thức `abstract` nào**. Lớp có thể trừu tượng chỉ để đóng vai trò lớp cơ sở, chia sẻ triển khai hoặc ngăn người dùng tạo một thể hiện không có ý nghĩa. Chiều ngược lại thì bắt buộc: **lớp khai báo một phương thức `abstract` phải là lớp `abstract`**.

## 2. Modifier trên **lớp trừu tượng**

### 2.1. Khai báo top-level và lớp thành viên

| Khai báo | Hợp lệ? | Lý do |
| --- | --- | --- |
| `abstract class Base {}` | Có | Lớp top-level phạm vi package-private. |
| `public abstract class Base {}` | Có | Lớp top-level công khai; nếu là top-level `public`, tệp nguồn phải đặt tên `Base.java`. |
| `final abstract class Base {}` | **Không** | `final` cấm kế thừa nhưng `abstract` cần để ngỏ lớp con. |
| `private abstract class Base {}` ở top-level | **Không** | `private` không được áp dụng cho lớp top-level. |
| `protected abstract class Base {}` ở top-level | **Không** | `protected` dành cho lớp thành viên phù hợp, không phải top-level. |
| `static abstract class Base {}` ở top-level | **Không** | Lớp top-level không thể khai báo `static`. |
| `private static abstract class Base {}` bên trong một lớp | Có | Lớp **thành viên** có thể đồng thời `private`, `static`, `abstract`. |
| `protected abstract class Base {}` bên trong một lớp | Có | Lớp thành viên có thể `protected abstract`. |
| `abstract sealed class Base permits Child {}` | Có, khi `Child` hợp lệ | Chỉ các lớp được cho phép mới kế thừa trực tiếp. |
| `abstract non-sealed class Child extends Base {}` | Có, nếu `Base` là lớp `sealed` cho phép trực tiếp `Child` | Mở lại khả năng kế thừa từ nhánh `Child`. |

`static abstract class` chỉ hợp lệ ở ngữ cảnh lớp thành viên: `static` mô tả quan hệ giữa **lớp lồng nhau** và lớp bao ngoài, không biến các phương thức `abstract` của nó thành phương thức static.

Từ Java 17, `sealed` quy định các lớp con trực tiếp được phép; mỗi lớp con trực tiếp đó phải là `final`, `sealed` hoặc `non-sealed`. Ba modifier `final`, `sealed`, `non-sealed` loại trừ lẫn nhau **trên cùng một lớp**. `abstract sealed` có ý nghĩa vì một lớp cha vẫn có thể trừu tượng dù chỉ giới hạn những lớp con nào được phép tồn tại. `non-sealed` không được dùng tùy ý cho một lớp không trực tiếp kế thừa lớp/interface `sealed`.

### 2.2. Trường hợp `abstract class` chứa thành viên `final`

Đoạn khai báo sau **hợp lệ**:

```java
abstract class Parent {
    private final int id;

    protected Parent(int id) {
        this.id = id;
    }

    public final int id() {
        return id;
    }

    public abstract String name();
}
```

Ở đây `Parent` là `abstract`, trường `id` là `final`, phương thức **có thân** `id()` là `final`, còn `name()` là `abstract`. Chúng tác động đến **ba chủ thể khác nhau**; không xung đột.

Một lớp con **cụ thể** có thể là `final` và vẫn `extends Parent`, miễn là nó triển khai đầy đủ phương thức trừu tượng kế thừa. `final` của lớp con chỉ ngăn các lớp khác kế thừa tiếp từ lớp con ấy. Lớp trừu tượng cũng có thể chứa constructor, phương thức cụ thể `private`, `static`, `synchronized` hoặc `final` theo đúng quy tắc Java. Không được suy ra rằng mọi phương thức của một lớp `abstract` cũng phải `abstract`.

## 3. Modifier trên **phương thức trừu tượng**

Một phương thức `abstract` trong `abstract class` là phương thức **instance** không có phần thân, kết thúc bằng `;`. Quyền truy cập được phép: `public`, `protected` hoặc package-private khi không viết modifier truy cập.

| Khai báo bên trong `abstract class` | Hợp lệ? | Lý do |
| --- | --- | --- |
| `public abstract void run();` | Có | Có thể được lớp con ghi đè và triển khai. |
| `protected abstract void run();` | Có | Cho phép lớp con truy cập, override. |
| `abstract void run();` | Có | Package-private; cần cân nhắc khi có lớp con ở package khác. |
| `private abstract void run();` | **Không** | Phương thức `private` không thể override. |
| `abstract final void run();` | **Không** | `final` cấm ghi đè, ngược với yêu cầu triển khai của `abstract`. |
| `abstract static void run();` | **Không** | `static` thuộc lớp, thực hiện *method hiding* thay vì override instance. |
| `abstract synchronized void run();` | **Không** | `synchronized` là đặc tính thực thi của phương thức có thân. |
| `abstract native void run();` | **Không** | `native` không được tổ hợp với `abstract`. |
| `abstract strictfp void run();` | **Không** | Hai modifier bị cấm kết hợp cho một phương thức; `strictfp` còn là đặc tính đã trở nên dư thừa trong Java hiện đại. |
| `abstract void run() {}` | **Không** | Phương thức trừu tượng không được có thân, kể cả thân rỗng. |

Các lưu ý liên quan:

- `abstract` method vẫn có thể khai báo tham số, generic type, giá trị trả về và `throws`; điểm khác biệt nằm ở việc không có thân và cần được triển khai qua kế thừa.
- Constructor, trường, initializer và phương thức static không thể được khai báo `abstract`.
- Modifier như `private`, `final`, `static`, `synchronized` có thể hợp lệ với **phương thức cụ thể có thân** trong một lớp `abstract`; chỉ tổ hợp chúng với `abstract` trên cùng phương thức mới gây lỗi.
- Với `interface`, một phương thức trừu tượng thông thường mặc định là `public abstract`. `interface` có thể khai báo phương thức `default`, `static` hoặc `private` **có thân**; điều này không khiến `private abstract`, `static abstract` hay `final abstract` trở nên hợp lệ.

## 4. Ví dụ đầy đủ chạy bằng Java 17+

Tạo tệp **`ModifierDemo.java`**:

```java
public class ModifierDemo {
    static abstract class Shape {
        private final String label;

        protected Shape(String label) {
            this.label = label;
        }

        public final String label() {
            return label;
        }

        public abstract int area();

        public String kind() {
            return "Shape";
        }

        public static String unit() {
            return "square units";
        }
    }

    static final class Rectangle extends Shape {
        private final int width;
        private final int height;

        Rectangle(int width, int height) {
            super("rectangle");
            this.width = width;
            this.height = height;
        }

        @Override
        public int area() {
            return width * height;
        }

        @Override
        public String kind() {
            return "Rectangle";
        }
    }

    public static void main(String[] args) {
        Shape shape = new Rectangle(3, 4);
        System.out.println(shape.label());
        System.out.println(shape.area());
        System.out.println(shape.kind());
        System.out.println(Shape.unit());
    }
}
```

Biên dịch và chạy:

```bash
javac ModifierDemo.java
java ModifierDemo
```

Kết quả stdout chính xác:

```text
rectangle
12
Rectangle
square units
```

Giải thích:

1. `Shape` là lớp **thành viên** nên `static abstract` hợp lệ. `new Shape(...)` trực tiếp không hợp lệ vì `Shape` là `abstract`.
2. `Shape.area()` không có thân, nên lớp cụ thể `Rectangle` phải cung cấp một triển khai.
3. `Shape.label()` là phương thức instance `final` **có thân**; lớp con không thể override `label()`.
4. Trường `private final String label` được khởi tạo trong constructor cha; nó không tác động đến quyền kế thừa.
5. `Shape.unit()` là phương thức `static` **cụ thể**, nên không xung đột với lớp trừu tượng.
6. `Rectangle` là `final` nhưng vẫn kế thừa hợp lệ từ `Shape`: nó chỉ không cho lớp **khác** kế thừa tiếp từ `Rectangle`.
7. `kind()` là phương thức instance cụ thể cho phép override; lời gọi qua tham chiếu kiểu `Shape` thực thi phiên bản của `Rectangle`.

Nếu bỏ `Rectangle.area()` mà vẫn giữ `Rectangle` là lớp cụ thể, `javac` sẽ báo lỗi do chưa triển khai phương thức trừu tượng. Nếu cố thêm `@Override public String label() { return "new"; }` vào `Rectangle`, cũng sẽ lỗi vì đang override phương thức `final`.

## 5. `abstract sealed` hợp lệ từ Java 17

Tạo tệp **`SealedAbstractDemo.java`**:

```java
public class SealedAbstractDemo {
    abstract static sealed class Result permits Success, Failure {
        abstract String describe();
    }

    static final class Success extends Result {
        @Override
        String describe() {
            return "OK";
        }
    }

    static non-sealed class Failure extends Result {
        @Override
        String describe() {
            return "ERROR";
        }
    }

    public static void main(String[] args) {
        Result result = new Success();
        System.out.println(result.describe());
    }
}
```

Lệnh:

```bash
javac SealedAbstractDemo.java
java SealedAbstractDemo
```

Stdout:

```text
OK
```

`Result` trừu tượng nên không thể tạo trực tiếp, nhưng cho phép đúng hai lớp con trực tiếp `Success` và `Failure`. `Success` chọn `final` để kết thúc nhánh kế thừa; `Failure` chọn `non-sealed` để cho phép lớp khác kế thừa nó. Cả hai đã triển khai `describe()`. Một lớp khác trực tiếp `extends Result` mà không nằm trong `permits` sẽ bị trình biên dịch từ chối.

## 6. Các trường hợp **cố ý không biên dịch** và cách khắc phục

Những đoạn dưới đây là **ví dụ lỗi độc lập**, không phải mã để chạy cùng hai chương trình phía trên. Nội dung thông báo lỗi của `javac` có thể khác nhau theo phiên bản.

### 6.1. Xung đột `abstract final` trên lớp

```java
abstract final class WrongBase {}
```

Lớp `abstract` cần có khả năng được lớp khác kế thừa để trở thành lớp cụ thể; lớp `final` lại cấm mọi lớp con. Đổi thành `abstract class WrongBase {}` khi cần lớp cha trừu tượng, hoặc `final class WrongBase {}` khi muốn lớp cụ thể không kế thừa tiếp.

### 6.2. Xung đột `abstract final` trên phương thức

```java
abstract class WrongBase {
    public final abstract void execute();
}
```

`execute()` không có triển khai nhưng lại cấm override. Nếu đây là hợp đồng bắt buộc, chọn `public abstract void execute();`. Nếu phải cố định triển khai, chọn một **phương thức cụ thể** có thân và `final`, ví dụ `public final void execute() { System.out.println("run"); }`.

### 6.3. Phương thức trừu tượng lại `private`, `static`, `synchronized`

```java
abstract class WrongBase {
    private abstract void a();
    static abstract void b();
    abstract synchronized void c();
}
```

`private` không cho ghi đè, `static` không tham gia override instance, còn `synchronized` thuộc logic thực thi một phương thức có thân. Khi cần hợp đồng, chọn `public abstract`, `protected abstract` hoặc package-private `abstract`; khi cần helper private/static hoặc một phương thức đã đồng bộ hóa, cung cấp thân cụ thể và bỏ `abstract`.

Tương tự, `abstract native` và `abstract strictfp` trên cùng phương thức đều không hợp lệ.

### 6.4. Lớp không `abstract` chứa phương thức `abstract`

```java
class WrongBase {
    abstract void execute();
}
```

Sửa bằng `abstract class WrongBase` hoặc cung cấp thân cho `execute()`.

### 6.5. Phương thức `abstract` có thân

```java
abstract class WrongBase {
    abstract void execute() {
        System.out.println("run");
    }
}
```

Dù phần thân rỗng hay có câu lệnh, cú pháp vẫn sai. Hoặc bỏ phần thân và kết thúc bằng `;`, hoặc bỏ `abstract`.

### 6.6. Áp dụng `abstract` cho record

```java
abstract record Point(int x, int y) {}
```

Record là kiểu không thể dùng làm lớp cơ sở để kế thừa như class thông thường; không cho khai báo `abstract record`. Nếu cần hợp đồng đa hình, cân nhắc interface hoặc lớp trừu tượng thay cho record.

## 7. Quy tắc override có liên quan

Lớp con **cụ thể** phải hoàn thành các phương thức trừu tượng còn hiệu lực; lớp con **vẫn trừu tượng** có thể tiếp tục hoãn triển khai. Một override không được làm quyền truy cập hẹp hơn, không được mở rộng checked exception trái quy tắc và phải giữ kiểu trả về tương thích (có thể là kiểu con covariant). Nên dùng `@Override` để compiler xác minh chữ ký.

Ví dụ hợp lệ ở cấp tệp (không có `main`):

```java
abstract class Processor {
    protected abstract String process(String input);
}

final class UpperProcessor extends Processor {
    @Override
    public final String process(String input) {
        return input.toUpperCase(java.util.Locale.ROOT);
    }
}
```

`Processor.process` là `protected abstract` trong cha, còn `UpperProcessor.process` là `public final` **có thân** trong con. Mở rộng quyền truy cập từ `protected` lên `public` là hợp lệ. Phương thức sau khi được triển khai hoàn toàn có thể đánh dấu `final`. Lớp con cũng có thể là `final`.

Ngược lại, **một phương thức cụ thể đã mang `final` ở cha** không thể override ở con, bất kể lớp cha có `abstract` hay không. Cần xem modifier gắn với chính khai báo nào trước khi xác định lỗi.

## 8. Checklist và tự kiểm tra

1. Xác định chủ thể: lớp top-level, lớp thành viên, phương thức instance, phương thức static, trường hay constructor.
2. `abstract final` trên **cùng một lớp** luôn sai, bất kể thứ tự từ khóa.
3. `abstract` cùng `final`, `private`, `static`, `synchronized`, `native` hoặc `strictfp` trên **cùng phương thức** là sai.
4. Phương thức `abstract` kết thúc bằng `;`, không có thân; lớp khai báo nó phải `abstract`.
5. `final` của **trường**, của **phương thức có thân** và của **lớp con đã triển khai đủ hợp đồng** có thể cùng tồn tại với một lớp cha `abstract`.
6. Lớp top-level không nhận `private`, `protected`, `static`; lớp thành viên có quy tắc riêng.
7. Với `sealed`, kiểm tra `permits` và xem từng lớp con trực tiếp đã khai báo `final` / `sealed` / `non-sealed` đúng chưa.
8. Với lỗi kế thừa, kiểm tra cả các phương thức abstract chưa được thực thi và các phương thức `final` không được override.

**Câu hỏi:** `abstract class A { final int id = 1; abstract void run(); }` có hợp lệ không? **Có.** `final` gắn với trường `id`, còn `abstract` gắn với lớp `A` và phương thức `run()`.

**Câu hỏi:** `final class B extends A { @Override void run() {} }` có hợp lệ không (với `A` ở trên)? **Có.** `B` triển khai `run()` rồi cấm các lớp khác kế thừa tiếp từ `B`.

</details>

- [Quay lại đầu trang](#back-to-top)
