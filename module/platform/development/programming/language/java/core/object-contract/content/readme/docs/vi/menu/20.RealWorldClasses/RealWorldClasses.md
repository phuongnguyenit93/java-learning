<a id="back-to-top"></a>

# Lớp thực tế

## Menu
- [getClass() trả về đối tượng Class của lớp thực tế](#real-world-classes-001)
- [Phân biệt kiểu khai báo với lớp thực tế](#real-world-classes-002)
- [getClass() là final và không thể ghi đè](#real-world-classes-003)
- [Class literal Type.class và so sánh với getClass()](#real-world-classes-004)
- [So sánh chính xác lớp khác với kiểm tra khả năng là một kiểu con](#real-world-classes-005)
- [Cùng tên lớp không bảo đảm cùng lớp khi có class loader khác nhau](#real-world-classes-006)
- [Vai trò của getClass() trong equals(), clone() và chẩn đoán](#real-world-classes-007)

## <a id="real-world-classes-001">getClass() trả về đối tượng Class của lớp thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# `getClass()` trả về đối tượng `Class` của lớp thực tế

## 1. Khái niệm: lớp thực tế là lớp của đối tượng tại runtime

Trong Java, biến kiểu tham chiếu có **kiểu khai báo** (static/compile-time type), còn đối tượng mà biến trỏ tới có **lớp thực tế khi chạy** (runtime class). Hai thông tin này có thể khác nhau vì tính đa hình và phép gán hướng lên (upcasting).

Phương thức `getClass()` được khai báo trong `java.lang.Object`:

```java
public final native Class<?> getClass();
```

Đây là **chữ ký API**, không phải một phương thức cần tự viết lại. Khi gọi `object.getClass()` trên đối tượng khác `null`, kết quả là đối tượng `java.lang.Class` đại diện cho **lớp runtime chính xác** của đối tượng đó.

Ví dụ ý tưởng:

```java
Animal animal = new Dog();
Class<?> runtimeType = animal.getClass();
```

Nếu `Dog extends Animal`, biến `animal` có kiểu khai báo là `Animal` nhưng đối tượng được tạo là `Dog`. Vì vậy `animal.getClass() == Dog.class` là `true`, còn `animal.getClass() == Animal.class` là `false`. Việc gán đối tượng vào biến kiểu cha **không biến đối tượng Dog thành Animal**.

Các điều cần nhớ:

- `getClass()` là phương thức của **đối tượng**, không phải một cách hỏi trình biên dịch biến đang khai báo kiểu gì.
- Phương thức `getClass()` là `final`: lớp con **không được ghi đè** để nói dối về lớp runtime.
- Kết quả có kiểu `Class<?>` (một đối tượng metadata), **không phải một đối tượng mới của lớp được phản ánh**.
- So sánh `getClass()` với một class literal như `Dog.class` là cách kiểm tra **đúng lớp thực tế**.
- Không thể gọi `null.getClass()`: tham chiếu `null` không đại diện cho đối tượng nên sẽ phát sinh `NullPointerException`.

Bài này tập trung vào nghĩa của `Object.getClass()`, cách đối chiếu với `instanceof` và hệ quả đối với hợp đồng bằng nhau của đối tượng Java.

## 2. Kiểu khai báo khác lớp thực tế thế nào?

Giả sử có quan hệ:

```text
          Animal
          /    \
        Dog    Cat

Biến Animal animal = new Dog()
        |
        v
  Đối tượng Dog ở runtime
```

Một biến kiểu `Animal` có thể tham chiếu tới một `Animal`, một `Dog` hoặc một `Cat` nếu các lớp và điều kiện truy cập cho phép. Kiểu khai báo quyết định **những thành viên có thể gọi trực tiếp trong mã nguồn**, còn lớp runtime quyết định **đối tượng đang tồn tại thực sự thuộc lớp nào** và cách dispatch các phương thức instance có thể ghi đè.

So sánh:

| Biểu thức | Trả lời câu hỏi | Ví dụ với `Animal animal = new Dog()` |
| --- | --- | --- |
| `animal.getClass()` | Đối tượng hiện tại thuộc chính xác lớp runtime nào? | `Dog.class` |
| `animal.getClass() == Dog.class` | Có đúng lớp `Dog` không? | `true` |
| `animal.getClass() == Animal.class` | Có đúng lớp `Animal` không? | `false` |
| `animal instanceof Animal` | Có thể xem đối tượng như một `Animal` không? | `true` |
| `animal instanceof Dog` | Có thể xem đối tượng như một `Dog` không? | `true` |
| `Dog.class.isInstance(animal)` | Kiểm tra quan hệ instance động bằng đối tượng `Class` | `true` |
| `animal.getClass().getSuperclass()` | Lớp cha trực tiếp của lớp runtime là gì? | `Animal.class` |

**Lưu ý:** `getClass()` không tự thực hiện ép kiểu. Biết rằng đối tượng có runtime class là `Dog` không làm biến `animal` tự chuyển kiểu khai báo thành `Dog`. Khi thực sự cần truy cập API riêng của `Dog`, dùng kiểm tra kiểu thích hợp hoặc pattern matching:

```java
if (animal instanceof Dog dog) {
    // dog có kiểu Dog trong phạm vi được phép.
}
```

Đây là đoạn minh họa và cần đặt trong một ngữ cảnh có `Animal`/`Dog` được khai báo. Java 17 hỗ trợ pattern matching cho `instanceof` như trên.

## 3. Một đối tượng `Class` đại diện điều gì?

`java.lang.Class` là kiểu chứa thông tin về một lớp, interface, array class hoặc kiểu đặc biệt được Java biểu diễn bằng class object. `Object.getClass()` trả về metadata của lớp thực tế của một **instance**. Từ `Class<?>`, ta có thể đọc những thông tin phù hợp:

| API | Ý nghĩa |
| --- | --- |
| `getName()` | Tên nhị phân của lớp thông thường; cú pháp riêng cho một số kiểu, ví dụ mảng. |
| `getSimpleName()` | Tên đơn giản phục vụ trình bày; có thể là chuỗi rỗng với một số lớp ẩn danh. |
| `getCanonicalName()` | Tên canonical khi tồn tại; có thể trả về `null`, chẳng hạn một số lớp cục bộ hoặc ẩn danh. |
| `getSuperclass()` | `Class` của lớp cha trực tiếp, hoặc `null` trong các trường hợp không có lớp cha phù hợp. |
| `getInterfaces()` | Mảng `Class<?>` của các interface trực tiếp mà kiểu khai báo thực hiện/kế thừa theo quy tắc API. |
| `isInterface()` | `true` nếu `Class` đại diện cho interface. |
| `isArray()` | `true` nếu kiểu được phản ánh là một array class. |
| `getComponentType()` | Với một array class, trả `Class` của kiểu phần tử; không phải array class thì trả `null`. |
| `isAssignableFrom(otherClass)` | Kiểm tra khả năng gán kiểu tham chiếu từ `otherClass` sang kiểu đại diện bởi `Class` hiện tại. |
| `isInstance(value)` | Kiểm tra động liệu giá trị có là instance của lớp/interface được phản ánh hay không. |

Ví dụ:

```java
Class<?> type = "hello".getClass();

System.out.println(type.getSimpleName());  // String
System.out.println(type == String.class);   // true
System.out.println(type.isInterface());     // false
```

Đoạn trên chỉ minh họa trong một phương thức, không phải một chương trình độc lập. Phần lớn thao tác này thuộc nhóm **reflection** (phản chiếu metadata). Nhưng hiểu `getClass()` không yêu cầu phải tạo đối tượng bằng reflection hoặc thao tác với private field.

### `getClass()` không tạo mới một `Class` cho mỗi lần gọi

Đối với **cùng một lớp đã được định nghĩa trong cùng phạm vi định danh runtime**, các lần gọi `getClass()` trên các instance của lớp đó tham chiếu tới cùng đối tượng `Class`. Vì vậy kiểm tra:

```java
new String("a").getClass() == new String("b").getClass()
```

trả về `true`, mặc dù hai `String` được tạo bằng hai biểu thức `new` là hai đối tượng khác nhau. Đối tượng `Class` đại diện **kiểu**, còn hai chuỗi đại diện **instance**.

Phép so sánh `==` giữa hai biến `Class<?>` là phù hợp để hỏi chúng có biểu diễn **cùng một runtime class** không. Đừng suy từ `==` trên `Class` sang `==` trên các instance của lớp ấy.

## 4. Lớp cha, interface và đa hình

Đối tượng có thể thỏa nhiều quan hệ kiểu, nhưng `getClass()` vẫn trả về **một lớp runtime chính xác**.

Giả sử:

```java
interface Pet {}
class Animal {}
final class Dog extends Animal implements Pet {}
```

Khi `Object value = new Dog()`:

- `value.getClass() == Dog.class` đúng.
- `value.getClass() == Animal.class` sai dù `Dog` kế thừa `Animal`.
- `value.getClass() == Pet.class` sai dù `Dog` triển khai `Pet`; `Pet.class` phản ánh interface, không phải lớp thực tế của instance.
- `value instanceof Animal` và `value instanceof Pet` đều đúng.
- `Animal.class.isInstance(value)` và `Pet.class.isInstance(value)` đều đúng.

Nói cách khác, **“chính xác cùng lớp”** và **“thuộc kiểu hoặc kiểu con”** là hai quan hệ khác nhau. Trong code nghiệp vụ, chọn phép kiểm tra tương ứng với yêu cầu:

- Nếu cho phép mọi đối tượng triển khai một giao diện, dùng `instanceof` hoặc `Class.isInstance`.
- Nếu muốn biết đúng lớp runtime, dùng `getClass() == ExpectedType.class`.
- Nếu cần phương thức có polymorphism, đa số trường hợp chỉ cần gọi phương thức đã khai báo ở interface/lớp cha; không cần hỏi `getClass()` ở từng nhánh.

Lạm dụng `getClass()` rồi viết chuỗi `if/else` cho mọi lớp con có thể làm giảm giá trị của đa hình. Chỉ kiểm tra lớp thực tế khi **lớp cụ thể thực sự là một phần của quy tắc**.

## 5. Chương trình Java 17+ độc lập

Chương trình sau bao quát lớp cha/con, interface, upcast, class literal, record, mảng và `null`. Phần in ra sử dụng `getSimpleName()` và các kết quả boolean ổn định để không phụ thuộc tên package khi chạy.

Lưu trong tệp `RuntimeClassDemo.java`:

```java
public class RuntimeClassDemo {
    interface Pet {}

    static class Animal {}

    static final class Dog extends Animal implements Pet {}

    static final class Cat extends Animal {}

    record Point(int x, int y) {}

    public static void main(String[] args) {
        Animal animal = new Dog();
        Object upcast = animal;

        System.out.println("declared Animal, runtime: "
                + animal.getClass().getSimpleName());
        System.out.println("upcast keeps runtime Class: "
                + (upcast.getClass() == animal.getClass()));
        System.out.println("exactly Dog: "
                + (animal.getClass() == Dog.class));
        System.out.println("exactly Animal: "
                + (animal.getClass() == Animal.class));
        System.out.println("instanceof Animal: "
                + (animal instanceof Animal));
        System.out.println("instanceof Dog: "
                + (animal instanceof Dog));
        System.out.println("implements Pet: "
                + Pet.class.isInstance(animal));
        System.out.println("superclass: "
                + animal.getClass().getSuperclass().getSimpleName());

        Object otherAnimal = new Cat();
        System.out.println("another runtime class: "
                + otherAnimal.getClass().getSimpleName());
        System.out.println("Cat vs Dog Class: "
                + (otherAnimal.getClass() == animal.getClass()));

        Object point = new Point(3, 4);
        System.out.println("record runtime: "
                + point.getClass().getSimpleName());

        int[] numbers = {1, 2};
        System.out.println("int[] exact class: "
                + (numbers.getClass() == int[].class));
        System.out.println("array component is int: "
                + (numbers.getClass().getComponentType() == int.class));
        System.out.println("array Class isArray: "
                + numbers.getClass().isArray());

        Object strings = new String[2];
        System.out.println("String[] vs Object[] exact: "
                + (strings.getClass() == Object[].class));
        System.out.println("String[] is Object[] instance: "
                + (strings instanceof Object[]));

        System.out.println("same Dog Class object: "
                + (new Dog().getClass() == animal.getClass()));
        System.out.println("Class of runtime metadata: "
                + animal.getClass().getClass().getSimpleName());

        Object missing = null;
        try {
            missing.getClass();
        } catch (NullPointerException exception) {
            System.out.println("null getClass: NullPointerException");
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 RuntimeClassDemo.java
java RuntimeClassDemo
```

Đầu ra:

```text
declared Animal, runtime: Dog
upcast keeps runtime Class: true
exactly Dog: true
exactly Animal: false
instanceof Animal: true
instanceof Dog: true
implements Pet: true
superclass: Animal
another runtime class: Cat
Cat vs Dog Class: false
record runtime: Point
int[] exact class: true
array component is int: true
array Class isArray: true
String[] vs Object[] exact: false
String[] is Object[] instance: true
same Dog Class object: true
Class of runtime metadata: Class
null getClass: NullPointerException
```

### Phân tích các nhóm kết quả

**Kiểu khai báo và upcast:** biến `animal` có kiểu `Animal`, nhưng được gán một instance `Dog`. Chuyển tiếp thành `Object upcast` không đổi đối tượng, do đó `upcast.getClass()` và `animal.getClass()` vẫn là cùng `Dog.class`.

**`getClass` so với `instanceof`:** kiểm tra `animal.getClass() == Animal.class` sai do đối tượng không thuộc *chính xác* lớp `Animal`. Nhưng `animal instanceof Animal` đúng do `Dog` là lớp con. Giao diện `Pet` không thay đổi runtime class là `Dog`, dù `Pet.class.isInstance(animal)` đúng.

**Hai lớp con khác nhau:** `otherAnimal` được tạo là `Cat` nên `getClass().getSimpleName()` là `Cat`; dù cả `Cat` và `Dog` đều là `Animal`, hai đối tượng `Class` cụ thể khác nhau.

**Record:** `Point` cũng là một kiểu tham chiếu có `getClass()`, giống class thông thường. Trong trường hợp này `point.getClass() == Point.class` cũng sẽ đúng. Cơ chế `getClass` không khác biệt chỉ vì cú pháp khai báo là `record`.

**Mảng:** `int[]` và `String[]` đều là đối tượng có array class riêng. `int[].class` phản ánh loại mảng `int[]`, còn `int.class` phản ánh kiểu thành phần nguyên thủy `int`. `String[]` không có **lớp chính xác** là `Object[]`, nhưng có thể dùng ở vị trí `Object[]` bởi quy tắc kiểu của mảng tham chiếu Java. Phân biệt khả năng gán với chính xác cùng lớp.

**`Class` object:** hai `Dog` riêng biệt vẫn trả về cùng đối tượng `Class` của `Dog`. Lời gọi thêm `animal.getClass().getClass()` hỏi lớp của **chính đối tượng metadata `Class`**, nên tên đơn giản in ra là `Class`, không phải `Dog`.

**`null`:** `missing.getClass()` ném `NullPointerException` bởi `missing` không trỏ đến đối tượng nào. Đoạn `try/catch` giúp chương trình tiếp tục, không biến `null` thành một lớp runtime.

## 6. `getClass()` với mảng và kiểu nguyên thủy

### Mảng cũng có lớp runtime

Các biểu thức như:

```java
String[] texts = new String[3];
int[][] grid = new int[2][4];

Class<?> textArrayClass = texts.getClass();
Class<?> gridClass = grid.getClass();
```

cho biết hai đối tượng là các array class khác nhau. `textArrayClass.isArray()` và `gridClass.isArray()` đều đúng. `gridClass.getComponentType()` trả `int[].class` vì phần tử trực tiếp của `int[][]` là một `int[]`, không phải `int`.

Với `Object[] mixed = new String[2]`, biến `mixed` khai báo là `Object[]` nhưng runtime class của mảng vẫn là `String[].class`. Điều này giải thích tại sao Java vẫn kiểm tra loại phần tử thực tế của mảng và có thể ném `ArrayStoreException` nếu cố lưu đối tượng không phù hợp vào một mảng tham chiếu được nhìn qua kiểu rộng hơn:

```java
Object[] mixed = new String[1];
mixed[0] = Integer.valueOf(42); // ArrayStoreException khi chạy.
```

Đây là đoạn minh họa không nên chạy mà không bắt ngoại lệ. Trường hợp này cho thấy **runtime type của array là dữ liệu thật**, chứ không bị thay thế bởi kiểu của biến đang giữ mảng.

### Kiểu nguyên thủy không thể gọi phương thức

`int`, `boolean`, `double` là các kiểu nguyên thủy; một giá trị `int` không phải tham chiếu tới instance nên không có cú pháp hợp lệ `number.getClass()` với `number` là primitive.

Nhưng Java vẫn có **class literal** cho kiểu nguyên thủy:

```java
Class<?> primitive = int.class;
Class<?> wrapper = Integer.class;

System.out.println(primitive.isPrimitive()); // true
System.out.println(wrapper.isPrimitive());   // false
```

Đây là đoạn dùng trong một phương thức. Nếu cần một đối tượng bọc giá trị `int`, `Integer.valueOf(5).getClass()` sẽ trả `Integer.class`, không phải `int.class`. Class literal `int.class` là metadata của kiểu nguyên thủy chứ không phải kết quả gọi phương thức trên một giá trị primitive.

### Interface không phải runtime class chính xác của đối tượng triển khai

`Runnable.class`, `Comparable.class` và các class literal giao diện khác đều là đối tượng `Class` hợp lệ. Nhưng với một đối tượng triển khai `Runnable`, `getClass()` cho biết **lớp thực thi cụ thể**, không trả chính `Runnable.class`. Muốn kiểm tra đối tượng có triển khai interface hay không, dùng `instanceof Runnable` hoặc `Runnable.class.isInstance(value)`.

## 7. `getClass()` trong thiết kế `equals(Object)`: so sánh lớp chính xác

Một ứng dụng quan trọng trong chủ đề hợp đồng đối tượng là chọn **miền các kiểu được phép bằng nhau**.

Giả sử lớp giá trị chỉ muốn bằng đối tượng **đúng lớp runtime**. Có thể kiểm tra:

```java
if (other == null || getClass() != other.getClass()) {
    return false;
}
```

Đây là đoạn bên trong một triển khai `equals(Object)`. Kiểm tra trên `other` sau khi xác nhận nó không `null` để tránh NPE. Sau điều kiện này, hai đối tượng có cùng lớp runtime và có thể tiếp tục so các trường ý nghĩa.

### Ví dụ về `equals` dùng kiểm tra lớp chính xác

```java
import java.util.Objects;

final class Coordinate {
    private final int x;
    private final int y;

    Coordinate(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Coordinate that = (Coordinate) other;
        return x == that.x && y == that.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
```

Đây là một lớp hỗ trợ hợp lệ trong cùng package/tệp Java (không phải chương trình có `main`). `Coordinate` là `final` nên không có lớp con; kiểm tra `getClass` thể hiện cách yêu cầu đúng kiểu. `equals` xác nhận hai tọa độ bằng nhau theo `x` và `y`, còn `hashCode` dùng cùng dữ liệu để đáp ứng hợp đồng hash. Cặp `getClass` và `hashCode` không thay thế nhau: câu hỏi “đúng lớp không?” và “giá trị các trường bằng nhau không?” đều cần được trả lời.

### Khi nào `getClass()` khác `instanceof` trong `equals`?

- `getClass() == other.getClass()`: chỉ cho phép **chính xác cùng lớp runtime**. Một instance lớp cha không bao giờ bằng instance lớp con nếu khác lớp runtime, dù các trường chung trùng nhau.
- `other instanceof Base`: cho phép đối tượng thuộc lớp `Base` hoặc lớp con của nó vượt qua kiểm tra kiểu. Đây có thể là lựa chọn đúng nếu quy tắc bằng nhau thực sự được định nghĩa thống nhất cho toàn thứ bậc.
- Một lớp cha cho phép bằng lớp con, nhưng lớp con chỉ cho bằng chính nó, có thể gây **vi phạm tính đối xứng** (`a.equals(b)` khác `b.equals(a)`). Nếu các lớp thêm tiêu chí so sánh, còn có nguy cơ phá bắc cầu.

Vì vậy **không có quy tắc “mọi `equals` đều phải dùng `getClass`”**. Hãy thiết kế quan hệ bằng nhau trước; dùng `getClass` hoặc `instanceof` theo chính sách type-equivalence của domain. `final` value class hoặc record (khi phù hợp) giúp tránh một số xung đột kế thừa, nhưng không tự xử lý mọi component mutable hoặc so sánh sai.

Đặc biệt, dùng `getClass()` trong `equals` không nhất thiết phù hợp với mô hình có proxy/subclass sinh bởi framework: đối tượng proxy có thể có runtime class khác lớp entity gốc. Đừng áp nguyên tắc này máy móc lên các entity của ORM; cần tuân theo chiến lược danh tính và proxy của framework đang dùng.

## 8. Tại sao tên lớp chưa chắc là danh tính lớp?

`Class.getName()`, `getSimpleName()` và `getCanonicalName()` chủ yếu cung cấp **tên và mô tả**. Nhưng định danh class ở runtime không chỉ do chuỗi tên quyết định: trong hệ thống có nhiều class loader, hai lớp với **cùng tên nhị phân** được định nghĩa bởi **hai class loader khác nhau** có thể là hai lớp khác nhau theo JVM.

Hệ quả:

- Không thay `first.getClass() == second.getClass()` bằng `first.getClass().getName().equals(second.getClass().getName())` khi cần kiểm tra **cùng lớp runtime chính xác**.
- `getClass().getSimpleName()` là chuỗi có ích để ghi log/hiển thị nhưng không phải khóa danh tính duy nhất.
- Các lớp lồng thường có tên nhị phân chứa dấu `$` (ví dụ `RuntimeClassDemo$Dog`). Lớp ẩn danh hoặc lớp cục bộ có quy tắc tên khác, và `getSimpleName()` có thể rỗng.
- Proxy được framework sinh động có thể có tên lớp bất ngờ dù đối tượng vẫn triển khai interface/kế thừa kiểu gốc.
- Nếu cần làm việc với metadata thực sự, giữ đối tượng `Class<?>` và dùng API của nó thay vì tự phân tích chuỗi tên bằng thủ thuật cắt ký tự.

Chỉ cần biết giới hạn trên để tránh nhầm lẫn. Việc tự tạo class loader, định nghĩa lớp động hoặc khám phá toàn bộ reflection là chủ đề sâu hơn, không cần cho cách dùng `getClass()` thông thường.

## 9. `getClass()`, `Class.isInstance`, `isAssignableFrom` và `instanceof`

Các công cụ sau dễ bị nhầm tên nhưng trả lời những câu hỏi khác nhau:

| Biểu thức | Cách hiểu |
| --- | --- |
| `obj.getClass() == Target.class` | Instance có **chính xác** lớp `Target` không? |
| `obj instanceof Target` | Instance có thuộc `Target` hoặc kiểu con/kiểu triển khai phù hợp không? |
| `Target.class.isInstance(obj)` | Biến thể động của kiểm tra instance dựa trên `Class` của đích. |
| `Target.class.isAssignableFrom(actualClass)` | Có thể gán một tham chiếu kiểu `actualClass` vào biến kiểu `Target` không? |
| `obj.getClass().getSuperclass() == Parent.class` | Lớp cha trực tiếp của lớp runtime có đúng là `Parent` không? |

Hai cách kiểm tra có thể khác nhau:

```java
// Giả sử Dog extends Animal và Object obj = new Dog();
boolean exact = obj.getClass() == Animal.class; // false
boolean assignable = Animal.class.isInstance(obj); // true
boolean classRelation =
        Animal.class.isAssignableFrom(obj.getClass()); // true
```

Đoạn minh họa đặt trong phương thức có các kiểu `Animal`/`Dog`. Với mục tiêu xử lý mọi loại `Animal`, kiểm tra theo kiểu kế thừa thường phù hợp; với mục tiêu chặn mọi lớp con khác, phép kiểm tra `getClass` mới thể hiện chính sách.

Chú ý hướng của `isAssignableFrom`: `Parent.class.isAssignableFrom(Child.class)` thường là đúng khi `Child extends Parent`, còn đảo chiều không nhất thiết đúng.

## 10. Lỗi thường gặp và cách sửa

| Lỗi | Vì sao sai | Hướng đúng |
| --- | --- | --- |
| Nghĩ `obj.getClass()` trả kiểu khai báo của biến | Upcast chỉ thay cách nhìn của trình biên dịch, không thay đối tượng. | Dùng `getClass` để đọc lớp runtime; xem khai báo biến để biết static type. |
| So `getClass() == Parent.class` rồi cho rằng mọi lớp con cũng khớp | `==` giữa các đối tượng `Class` yêu cầu chính xác một lớp. | Dùng `instanceof Parent` hoặc `Parent.class.isInstance(obj)` nếu cần nhận lớp con. |
| Gọi `getClass()` trên `null` | `null` không có instance để dispatch phương thức. | Kiểm tra `obj != null` trước khi gọi; dùng `obj instanceof Type` nếu phù hợp vì `null instanceof Type` trả `false`. |
| Tìm cách ghi đè `getClass()` ở lớp con | `Object.getClass` là `final`. | Không override; dùng một phương thức domain riêng nếu muốn nhãn/loại nghiệp vụ tùy biến. |
| Lấy `getSimpleName()` làm định danh class duy nhất | Nhiều lớp ở package/loader khác nhau có thể trùng tên đơn giản. | Giữ `Class<?>` khi cần đúng lớp runtime; tên chỉ dùng để hiển thị. |
| Dùng `getClass()` trong mọi `equals` mà không xét domain | Có thể từ chối proxy/lớp con vốn cần bằng nhau theo quy tắc nghiệp vụ. | Quy định miền kiểu của equality trước khi chọn `getClass` hay `instanceof`. |
| So hash code của hai đối tượng `Class` thay vì so chính các `Class` | Hai hash giống nhau không bảo đảm cùng lớp. | So `Class` trực tiếp bằng `==` hoặc `equals`. |
| Nghĩ mảng chỉ có kiểu `Object` tại runtime | Mỗi loại array có runtime class riêng. | Dùng `getClass().isArray()`/`getComponentType()` hoặc class literal của mảng. |
| Nghĩ `int.class` là đối tượng `Integer` hay kết quả của `intValue.getClass()` | Primitive không có phương thức instance; wrapper là một lớp khác. | Phân biệt `int.class`, `Integer.class` và boxing. |
| Dùng chuỗi tên của lớp để dispatch mọi loại hành vi | Dễ hỏng khi đổi tên, thay class loader hoặc gặp proxy. | Ưu tiên interface, polymorphism, hoặc `Class`/`instanceof` khi cần phân nhánh thật sự. |

## 11. Checklist tự kiểm tra

Để chắc chắn hiểu `getClass()`, thử trả lời rồi chạy các tình huống:

1. Với `Object obj = new String("a")`, `obj.getClass()` là `String.class` hay `Object.class`? **`String.class`**.
2. Với `Animal animal = new Dog()`, vì sao `animal.getClass() == Animal.class` sai nhưng `animal instanceof Animal` đúng? **Một phép so chính xác lớp; phép còn lại xét quan hệ subtype**.
3. Hai đối tượng `new Dog()` khác nhau có cùng đối tượng `Class` không? **Có, nếu được tạo từ cùng lớp runtime đã định nghĩa**.
4. `getClass()` có thể trả trực tiếp interface `Pet.class` trên một instance `Dog implements Pet` không? **Không**, nó trả lớp runtime cụ thể `Dog.class`.
5. `new int[3].getClass() == int[].class` có đúng không? **Có**.
6. `int.class.isPrimitive()` khác `Integer.class.isPrimitive()` thế nào? **Lần lượt là `true` và `false`**.
7. `getClass()` có an toàn cho `null` không? **Không**, nó gây `NullPointerException`.
8. Trong `equals`, có khi nào kiểm tra exact class làm thay đổi chính sách bằng nhau giữa cha và con? **Có**, nên phải định nghĩa policy trước.
9. Nếu hai đối tượng trả cùng `getClass().getSimpleName()`, chắc chắn chúng có cùng `Class`? **Không**, tên ngắn không phải định danh duy nhất.
10. Nếu muốn chấp nhận bất kỳ implementer nào của một interface, `getClass()==Interface.class` hay `instanceof Interface` phù hợp? **`instanceof Interface`**.

## 12. Ghi nhớ

`Object.getClass()` trả về **đối tượng `Class` của lớp thực tế tại runtime**, bất kể kiểu khai báo của biến hay việc đối tượng được upcast qua bao nhiêu lớp/giao diện. Phương thức là `final` và yêu cầu tham chiếu khác `null`. Dùng `getClass() == SomeType.class` để kiểm tra **đúng lớp**, dùng `instanceof` hoặc `Class.isInstance` khi cần quan hệ **lớp con hoặc triển khai interface**. Mảng và record đều có lớp runtime; primitive có class literal nhưng không gọi `getClass` được. Khi dùng `getClass` trong `equals` hay reflection, phải hiểu chính sách danh tính kiểu và giới hạn của tên lớp, thay vì nhầm lẫn giữa metadata, quan hệ kế thừa và giá trị của đối tượng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="real-world-classes-002">Phân biệt kiểu khai báo với lớp thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt kiểu khai báo với lớp thực tế của đối tượng trong Java

## 1. Hai loại thông tin trên cùng một biến tham chiếu

Khi đọc mã Java, cần phân biệt **kiểu khai báo** (*declared/static type*) của biến hoặc biểu thức với **lớp thực tế** (*runtime class*) của đối tượng mà tham chiếu đó đang trỏ tới.

```java
Animal pet = new Dog();
```

Đây là **một câu lệnh minh họa** (giả sử `Dog extends Animal`), chưa phải chương trình hoàn chỉnh:

- `Animal` là **kiểu khai báo** của biến `pet`, được trình biên dịch biết trong lúc kiểm tra mã.
- `new Dog()` tạo ra **đối tượng thực tế** thuộc lớp `Dog` lúc chương trình chạy.
- Biến `pet` giữ tham chiếu tới đối tượng `Dog`. Việc biến có kiểu `Animal` không biến đối tượng `Dog` thành đối tượng `Animal` thuần túy.
- Vì `Dog` là lớp con của `Animal`, phép gán này là **upcasting** hợp lệ và không cần ép kiểu tường minh.

Khái niệm tương tự áp dụng khi biến được khai báo bằng một **interface**:

```java
CharSequence text = new StringBuilder("Java");
```

Trong ví dụ này, `CharSequence` là kiểu khai báo, còn `StringBuilder` là lớp thực tế. Interface chỉ quy định hợp đồng mà đối tượng có thể thực hiện; interface tự nó không phải lớp cụ thể được tạo ra bằng `new CharSequence()`.

## 2. Trình biên dịch dùng kiểu khai báo để quyết định điều gì?

Kiểu khai báo chi phối những thao tác có thể viết **hợp lệ tại vị trí mã đang biên dịch**:

- Tên và chữ ký phương thức có thể gọi (phương thức phải được nhìn thấy và phù hợp qua kiểu khai báo).
- Thuộc tính có thể truy cập, bao gồm các quy tắc phạm vi truy cập và cách chọn **field**.
- Cách giải quyết **overloading**: chọn chữ ký khi có nhiều phương thức trùng tên nhưng khác danh sách tham số, dựa trên các kiểu được biết tại thời điểm biên dịch.
- Khả năng gán tham chiếu, các ràng buộc tham số generic và yêu cầu ép kiểu khi cần truy cập API chỉ có ở lớp con.

Ví dụ:

```java
Animal pet = new Dog();
pet.speak();     // Hợp lệ nếu Animal khai báo hoặc kế thừa speak()
// pet.fetch();  // LỖI BIÊN DỊCH nếu chỉ Dog có fetch()
```

Cho dù đối tượng thực tế là `Dog`, trình biên dịch chỉ biết rằng biểu thức `pet` có kiểu `Animal`, nên không cho phép gọi trực tiếp phương thức chỉ tồn tại ở `Dog` mà chưa kiểm tra/ép kiểu.

**Lưu ý về `var`:** `var pet = new Dog()` làm trình biên dịch **suy luận kiểu khai báo là `Dog`** tại chỗ khai báo. `var` không có nghĩa biến trở thành kiểu động hay tự thay đổi kiểu theo đối tượng ở các lần gán sau.

## 3. JVM dùng lớp thực tế để quyết định điều gì?

Đối với các **phương thức thể hiện có thể ghi đè** (overridable instance methods), sau khi chữ ký lời gọi đã được xác định hợp lệ, Java sử dụng **dynamic dispatch**: bản cài đặt được thực thi phụ thuộc vào lớp thực tế của receiver.

```java
class Animal {
    String speak() { return "Animal"; }
}

class Dog extends Animal {
    @Override
    String speak() { return "Dog"; }
}

// Trong một phương thức:
Animal pet = new Dog();
System.out.println(pet.speak()); // Dog
```

Đoạn trên là minh họa nhiều khai báo lớp và lời gọi, chưa phải tệp có `main`. Trình biên dịch chấp nhận `pet.speak()` vì kiểu `Animal` có phương thức `speak()`. Khi chạy, đối tượng receiver là `Dog`, nên bản ghi đè `Dog.speak()` được gọi.

**Không phải mọi thao tác đều dispatch theo lớp thực tế:** truy cập field, chọn overload, gọi phương thức `static` (method hiding) và việc chọn API hợp lệ vẫn phụ thuộc kiểu tại lúc biên dịch. Phương thức `private` không được ghi đè; `final` không thể bị ghi đè; constructor cũng không được override.

## 4. Ví dụ Java 17+ đầy đủ: phương thức, field, overload, cast và `getClass()`

Lưu chương trình sau vào tệp **`DeclaredVsRuntimeTypeDemo.java`**:

```java
public class DeclaredVsRuntimeTypeDemo {
    static class Animal {
        String label = "Animal field";

        String speak() {
            return "Animal.speak";
        }

        String describe(Animal other) {
            return "describe(Animal)";
        }
    }

    static class Dog extends Animal {
        String label = "Dog field";

        @Override
        String speak() {
            return "Dog.speak";
        }

        String describe(Dog other) {
            return "describe(Dog)";
        }

        String fetch() {
            return "fetch";
        }
    }

    static class ServiceDog extends Dog {}

    static class Cat extends Animal {}

    public static void main(String[] args) {
        Animal pet = new Dog();

        System.out.println("declared Animal, speak: " + pet.speak());
        System.out.println("declared Animal, field: " + pet.label);
        System.out.println("runtime class: "
                + pet.getClass().getSimpleName());
        System.out.println("instanceof Dog: " + (pet instanceof Dog));
        System.out.println("overload via Animal: "
                + pet.describe(new Dog()));

        Dog dog = (Dog) pet;
        System.out.println("cast Dog, field: " + dog.label);
        System.out.println("overload via Dog: "
                + dog.describe(new Dog()));

        if (pet instanceof Dog matchedDog) {
            System.out.println("pattern instanceof: " + matchedDog.fetch());
        }

        Animal service = new ServiceDog();
        System.out.println("service instanceof Dog: "
                + (service instanceof Dog));
        System.out.println("service exact Dog class: "
                + (service.getClass() == Dog.class));

        Animal missing = null;
        System.out.println("null instanceof Dog: "
                + (missing instanceof Dog));
        try {
            missing.getClass();
        } catch (NullPointerException ex) {
            System.out.println("null.getClass: NullPointerException");
        }

        Animal cat = new Cat();
        try {
            Dog invalid = (Dog) cat;
            System.out.println(invalid.fetch());
        } catch (ClassCastException ex) {
            System.out.println("Cat cast to Dog: ClassCastException");
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac DeclaredVsRuntimeTypeDemo.java
java DeclaredVsRuntimeTypeDemo
```

Kết quả:

```text
declared Animal, speak: Dog.speak
declared Animal, field: Animal field
runtime class: Dog
instanceof Dog: true
overload via Animal: describe(Animal)
cast Dog, field: Dog field
overload via Dog: describe(Dog)
pattern instanceof: fetch
service instanceof Dog: true
service exact Dog class: false
null instanceof Dog: false
null.getClass: NullPointerException
Cat cast to Dog: ClassCastException
```

Kết quả chỉ phụ thuộc vào quan hệ lớp và lời gọi được định nghĩa trong ví dụ, không phụ thuộc danh tính tham chiếu hay thứ tự collection.

## 5. Giải thích vì sao các dòng kết quả khác nhau

**Ghi đè phương thức — dùng lớp thực tế.** `pet.speak()` chạy bản của `Dog` dù `pet` được khai báo `Animal`, vì `speak()` là phương thức thể hiện được `Dog` override.

**Field hiding — dùng kiểu của biểu thức truy cập.** Cả `Animal` và `Dog` khai báo field cùng tên `label`. `pet.label` nhìn qua biểu thức kiểu `Animal` nên lấy `"Animal field"`; sau khi ép kiểu, `dog.label` nhìn qua kiểu `Dog` nên lấy `"Dog field"`. Field không có cơ chế override động như instance method. Tình huống hai field cùng tên thường nên tránh vì làm mã khó hiểu.

**Overloading — chọn chữ ký lúc biên dịch.** `pet.describe(new Dog())` chỉ thấy `describe(Animal)` qua kiểu khai báo `Animal`, nên chọn chữ ký này. Khi `dog.describe(new Dog())`, kiểu biểu thức là `Dog`, có cả `describe(Animal)` lẫn `describe(Dog)` và chọn chữ ký đặc hiệu hơn `describe(Dog)`. Trong chương trình này không có ghi đè `describe(Animal)` ở `Dog`, nên kết quả đầu tiên là của phương thức kế thừa.

**Cast không thay đổi đối tượng thực tế.** `(Dog) pet` chỉ tạo một biểu thức có kiểu `Dog` sau khi JVM kiểm tra đối tượng thực tế tương thích. Không có đối tượng mới và bản thân lớp của đối tượng không đổi. Nếu `pet` thực tế là `Cat`, ép xuống `Dog` ném `ClassCastException`.

**`instanceof` kiểm tra tương thích kiểu.** Một `ServiceDog` là lớp con của `Dog`, nên `service instanceof Dog` là `true` dù `service.getClass() == Dog.class` là `false` (lớp thực tế chính xác là `ServiceDog`). Trong Java 17, cú pháp `pet instanceof Dog matchedDog` vừa kiểm tra vừa khai báo biến đã được thu hẹp kiểu bên trong nhánh điều kiện.

## 6. `getClass()` và `instanceof` trả lời hai câu hỏi khác nhau

| Biểu thức | Câu hỏi được trả lời | Ví dụ `new ServiceDog()` |
| --- | --- | --- |
| `obj.getClass() == Dog.class` | Đối tượng có **lớp thực tế chính xác** là `Dog` không? | `false` |
| `obj instanceof Dog` | Đối tượng có là instance của `Dog` **hoặc lớp con của `Dog`** không? | `true` |
| `Dog.class.isInstance(obj)` | Kiểm tra tương thích runtime theo class token | `true` |
| `obj.getClass().getSuperclass()` | Lấy superclass trực tiếp của lớp runtime | `Dog.class` đối với `ServiceDog` |

`Object.getClass()` là phương thức `final` trả về đối tượng `Class<?>` đại diện **lớp thực tế** của receiver. Khi `obj == null`, `obj.getClass()` ném `NullPointerException`; `obj instanceof Dog` trả về `false` khi `obj` là `null`.

Trong hệ thống dùng proxy hoặc lớp con được framework tạo lúc chạy, `getClass()` có thể cho một **lớp proxy**. Vì vậy muốn biết một đối tượng có hỗ trợ interface/kiểu cơ sở hay không, thường nên kiểm tra bằng `instanceof` hoặc API framework thích hợp thay vì chỉ so sánh chính xác `getClass() == SomeClass.class`.

## 7. Liên hệ với hợp đồng `equals()` trong `object-contract`

`equals(Object)` được khai báo trong `java.lang.Object` và có thể được lớp con ghi đè. Vì thế:

```java
Object left = new ValueKey(7);
Object right = new ValueKey(7);
System.out.println(left.equals(right));
```

Đoạn trên giả sử một lớp `ValueKey` có `equals(Object)` thích hợp; tuy **kiểu khai báo của `left` là `Object`**, lời gọi `left.equals(right)` vẫn có thể chạy bản override của lớp runtime `ValueKey` và trả `true` theo giá trị. Nếu `ValueKey` không ghi đè `equals`, mặc định `Object.equals` so sánh danh tính và hai đối tượng `new` khác nhau thường không bằng nhau.

Khi cài đặt `equals`, hai cách kiểm tra kiểu thường gặp là:

```java
if (obj == null || getClass() != obj.getClass()) return false;

// Hoặc trong một chính sách cho phép kiểu con phù hợp:
if (!(obj instanceof ValueKey other)) return false;
```

Đây là **hai phương án thiết kế thay thế**, không phải hai lệnh phải dùng đồng thời. Cách `getClass()` đòi cùng lớp runtime chính xác; cách `instanceof` có thể chấp nhận lớp con. Dù chọn cách nào, `equals()` vẫn phải thỏa tính phản xạ, đối xứng, bắc cầu, nhất quán, xử lý `null`, đồng thời `hashCode()` phải đồng bộ. Đặc biệt, cho phép lớp con bằng lớp cha một chiều có thể vi phạm tính đối xứng hoặc bắc cầu nếu mỗi lớp dùng tiêu chí khác nhau.

Ví dụ lớp giá trị cuối cùng không cho kế thừa:

```java
final class ValueKey {
    private final int id;

    ValueKey(int id) { this.id = id; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ValueKey other && id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
```

Đây là **khai báo lớp có thể biên dịch khi đặt vào một tệp Java thích hợp**, không phải chương trình có `main`. Vì `ValueKey` là `final`, kiểm tra `instanceof ValueKey` không tạo vấn đề với các lớp con tự định nghĩa; hai đối tượng `new ValueKey(7)` được coi là bằng nhau theo `id`. Lời gọi `equals` vẫn dispatch về `ValueKey.equals` dù tham chiếu được khai báo là `Object`.

## 8. Ép kiểu an toàn và giới hạn cần nhớ

**Upcast** (lớp con sang lớp cha/interface) là chuyển đổi tham chiếu hợp lệ: `Animal pet = new Dog()`. Nhưng bạn chỉ gọi trực tiếp được các phương thức mà kiểu khai báo nhìn thấy.

**Downcast** (lớp cha/interface sang lớp con) cần kiểm tra runtime: `Dog d = (Dog) pet`. Nếu lớp thực tế không tương thích, JVM ném `ClassCastException`. Java 17 có pattern matching cho `instanceof` để xử lý an toàn và gọn hơn:

```java
if (pet instanceof Dog d) {
    System.out.println(d.fetch());
}
```

Đoạn trên giả định `pet`, `Dog` và `fetch()` như ví dụ lớn. `instanceof` không khiến một đối tượng `Animal` thuần túy trở thành `Dog`; nó chỉ **kiểm tra rồi làm rõ kiểu của tham chiếu** trong nhánh hợp lệ.

**`null` không có lớp thực tế.** Biến `Animal pet = null;` vẫn có kiểu khai báo `Animal`, nhưng không trỏ tới đối tượng nào. Không nên gọi phương thức thể hiện qua `pet` khi nó là `null`. Kiểm tra `pet == null` hoặc `pet instanceof Dog` tùy mục đích (vế sau trả `false` với `null`).

**Generic type không khôi phục trực tiếp từ `getClass()`.** Ví dụ `List<String>` và `List<Integer>` dùng cùng lớp triển khai runtime như `ArrayList` khi tạo bởi `new ArrayList<>()` do cơ chế type erasure. Kiểu tham số `<String>`/`<Integer>` giúp trình biên dịch kiểm tra cách dùng, nhưng `list.getClass()` không cung cấp `Class<List<String>>` riêng để phân biệt chúng ở runtime.

## 9. Các nhầm lẫn thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| `Animal pet = new Dog()` nghĩa là chỉ có đối tượng `Animal` | Đối tượng được tạo là `Dog`; `Animal` là kiểu khai báo của tham chiếu |
| Lời gọi phương thức luôn theo kiểu khai báo | Phương thức thể hiện được override chạy theo lớp receiver thực tế |
| `pet.label` dùng field của `Dog` vì runtime là `Dog` | Truy cập field dùng kiểu của biểu thức (`Animal`) |
| Overload được chọn theo lớp runtime của tham số | Chữ ký overload được quyết định lúc biên dịch theo kiểu áp dụng của biểu thức và tham số |
| `(Dog) pet` tạo một đối tượng `Dog` mới | Ép kiểu chỉ đổi góc nhìn kiểu tham chiếu sau kiểm tra runtime |
| `instanceof Dog` giống `getClass() == Dog.class` | `instanceof` chấp nhận lớp con; `getClass() ==` đòi chính xác lớp runtime |
| Mọi biến tham chiếu đều có đối tượng runtime | Biến có thể `null`; lúc đó không có lớp thực tế của đối tượng |
| Khai báo `Object x = new ValueKey(7)` sẽ luôn gọi `Object.equals` | Nếu `ValueKey` override `equals`, lời gọi chạy bản override theo lớp thực tế |

## 10. Tự kiểm tra

1. `Animal x = new Dog();` — kiểu khai báo của `x` là gì, lớp thực tế là gì? **`Animal` và `Dog`.**
2. `x.speak()` gọi phương thức nào khi `Dog` override `speak`? **`Dog.speak()`** do dynamic dispatch.
3. `x.label` đọc field nào nếu cả hai lớp có field `label`? **Field nhìn thấy qua kiểu `Animal`**.
4. `Animal x = new ServiceDog(); x instanceof Dog` và `x.getClass() == Dog.class` là gì? **`true` và `false`.**
5. `Animal x = null; x.getClass()` có trả `Animal.class` không? **Không**; ném `NullPointerException`.
6. Nếu một phương thức chỉ tồn tại trên `Dog`, có gọi `x.fetch()` trực tiếp khi `x` khai báo là `Animal` được không? **Không**; cần kiểm tra kiểu rồi thu hẹp/ép kiểu tham chiếu.

**Ghi nhớ:** **Kiểu khai báo** quyết định Java cho phép bạn *viết lời gọi nào* và chọn chữ ký/field ở thời điểm biên dịch; **lớp thực tế** quyết định *bản ghi đè nào được thực thi* ở runtime và kết quả của `getClass()`/`instanceof`. Phân biệt hai lớp thông tin này giúp hiểu polymorphism, casting và hợp đồng đối tượng chính xác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="real-world-classes-003">getClass() là final và không thể ghi đè</a>

<details>
<summary>Xem chi tiết</summary>

# `getClass()` là `final` và không thể ghi đè trong Java

## 1. Mục tiêu bài học

Trong Java, mọi đối tượng đều kế thừa phương thức `getClass()` từ `java.lang.Object`. Khi gọi phương thức này trên một tham chiếu **khác `null`**, Java trả về đối tượng `Class<?>` đại diện cho **lớp thực tế lúc chạy** (runtime class) của chính đối tượng ấy.

Bài học tập trung vào hai sự thật gắn liền nhau:

1. `Object.getClass()` là phương thức **`final`**, vì vậy mọi lớp con, kể cả lớp tự định nghĩa, record hay enum, **không được override nó**.
2. Kết quả `getClass()` phản ánh **kiểu thực của đối tượng**, có thể khác kiểu biến tham chiếu được khai báo trong mã nguồn.

Nắm rõ chúng giúp bạn dùng đúng thông tin kiểu trong Java, phân biệt so kiểu chính xác với so theo quan hệ kế thừa, và tránh thiết kế sai các phương thức `equals()`.

## 2. Khai báo của `Object.getClass()`

Trong `java.lang.Object`, chữ ký phương thức có dạng:

```java
public final native Class<?> getClass();
```

Đây là **khai báo minh họa chữ ký từ JDK**, không phải đoạn mã bạn có thể tự thêm vào một lớp để chạy độc lập. Ý nghĩa từng phần:

- **`public`:** có thể gọi trên bất kỳ đối tượng nào mà bạn có tham chiếu hợp lệ.
- **`final`:** lớp kế thừa không thể ghi đè phương thức cùng chữ ký. Quy tắc được trình biên dịch Java kiểm tra.
- **`native`:** phần hiện thực được cung cấp qua cơ chế mức thấp của JVM/runtime, thay vì một thân phương thức Java trong khai báo trên.
- **`Class<?>`:** kiểu kết quả biểu diễn metadata của một lớp/kiểu runtime; nó không phải chuỗi tên lớp. Có thể dùng các API phản chiếu (reflection) như `getName()`, `getSuperclass()`, `isInterface()`, `isAssignableFrom()`.

Đối với một đối tượng tồn tại trong JVM, lớp thực tế của nó đã được xác định khi đối tượng được tạo ra. Việc gán đối tượng đó sang một biến có kiểu cha hoặc interface không thay đổi lớp thực tế ấy, nên `getClass()` vẫn trả metadata của kiểu runtime ban đầu.

**Điểm cần phân biệt:** `getClass()` là phương thức **`final`**, nhưng điều đó không có nghĩa mọi lớp chứa phương thức ấy đều là `final`. Ví dụ, `Animal` có thể không `final` và vẫn được kế thừa, mặc dù phương thức `getClass()` kế thừa từ `Object` luôn không thể bị ghi đè.

## 3. Kiểu khai báo và lớp thực tế khác nhau ra sao?

Ví dụ:

```java
class Animal {}
class Dog extends Animal {}

Animal pet = new Dog();
```

`pet` có **kiểu khai báo** `Animal`. Nhưng đối tượng được tạo bởi `new Dog()` có **runtime class** là `Dog`.

Vì vậy:

```java
pet.getClass() == Dog.class;    // true
pet.getClass() == Animal.class; // false
pet instanceof Animal;          // true
pet instanceof Dog;             // true
```

Đây là các biểu thức minh họa dựa trên biến `pet` khai báo phía trên. Hãy hiểu hai kiểm tra khác nhau:

- `pet.getClass() == Dog.class` hỏi: “Đối tượng này thuộc **chính xác** lớp `Dog` ở runtime không?”
- `pet instanceof Animal` hỏi: “Đối tượng này có tương thích với kiểu `Animal`, bao gồm lớp con không?”

Phương thức `getClass()` không bị “che” bởi kiểu biến `Animal`. Việc gọi trên một biến kiểu cha vẫn cho metadata của lớp thực tế, kể cả khi lớp con không hề định nghĩa lại `getClass()`.

### `SomeType.class` khác `obj.getClass()` thế nào?

`Dog.class` là **class literal**: lấy `Class` của kiểu được nêu trực tiếp trong mã; không cần tạo một thể hiện của `Dog`.

`pet.getClass()` là **truy vấn runtime** trên đối tượng cụ thể đang được tham chiếu. Nếu một ngày biến `pet` giữ `new Cat()` thay vì `new Dog()`, kết quả có thể là `Cat.class`.

Cả hai trả về đối tượng `Class` đại diện cho kiểu tương ứng. Trong cùng phạm vi kiểu đã được JVM nạp, nhiều thể hiện của cùng một lớp chia sẻ đối tượng `Class`:

```java
new Dog().getClass() == new Dog().getClass(); // true
```

Hai thể hiện `Dog` ở biểu thức này **không phải cùng một đối tượng Dog**; chúng chỉ có cùng lớp thực tế. Vì thế `a.getClass() == b.getClass()` không đồng nghĩa với `a == b` hoặc `a.equals(b)`.

## 4. Vì sao không được override `getClass()`?

Nếu một lớp có thể giả mạo metadata kiểu runtime thông qua override, kết quả của `getClass()` sẽ không còn là nguồn thông tin đáng tin cậy về lớp thực sự mà JVM đang quản lý. Java vì vậy khóa phương thức tại `Object` bằng `final`.

Ví dụ **cố ý sai, sẽ không biên dịch**:

```java
class FakeType {
    @Override
    public Class<?> getClass() {
        return String.class;
    }
}
```

Lỗi biên dịch về bản chất là: `getClass()` không thể override phương thức `final` trong `Object`. Bỏ `@Override` cũng không giúp: chữ ký vẫn trùng với phương thức `final` được kế thừa.

Tương tự, ta không thể override `getClass()` trong:

- Một lớp con bình thường, kể cả lớp con sâu nhiều cấp.
- Một record: record kế thừa `Object` (thông qua `java.lang.Record`) và vẫn chịu ràng buộc `final` của `getClass()`.
- Một enum: enum kế thừa `java.lang.Enum` rồi đến `Object`.
- Một lớp vô danh hoặc thân lớp riêng của hằng enum.

Nếu chỉ muốn cung cấp **nhãn loại nghiệp vụ**, hãy đặt một phương thức **khác tên**, ví dụ `kind()`, `typeCode()`, `displayType()`; không cố thay đổi `Object.getClass()`.

## 5. Ví dụ Java 17+ hoàn chỉnh

Tạo tệp **`GetClassFinalDemo.java`**:

```java
import java.util.ArrayList;

public class GetClassFinalDemo {

    static class Animal {}

    static class Dog extends Animal {}

    record Point(int x, int y) {}

    enum Mode {
        SIMPLE,
        CUSTOM {
            @Override
            String label() {
                return "custom";
            }
        };

        String label() {
            return "simple";
        }
    }

    static boolean sameRuntimeClass(Object left, Object right) {
        return left != null
                && right != null
                && left.getClass() == right.getClass();
    }

    public static void main(String[] args) {
        Animal animal = new Animal();
        Animal dogAsAnimal = new Dog();
        Object anonymous = new Animal() {};
        Object point = new Point(1, 2);

        System.out.println("declared Animal, runtime Dog: "
                + (dogAsAnimal.getClass() == Dog.class));
        System.out.println("dog instanceof Animal: "
                + (dogAsAnimal instanceof Animal));
        System.out.println("Animal vs Dog same class: "
                + (animal.getClass() == dogAsAnimal.getClass()));
        System.out.println("two Dog instances same Class: "
                + (new Dog().getClass() == dogAsAnimal.getClass()));
        System.out.println("same runtime class Dog vs Animal: "
                + sameRuntimeClass(dogAsAnimal, animal));
        System.out.println("record runtime class: "
                + (point.getClass() == Point.class));

        System.out.println("anonymous exactly Animal: "
                + (anonymous.getClass() == Animal.class));
        System.out.println("anonymous superclass Animal: "
                + (anonymous.getClass().getSuperclass()
                        == Animal.class));
        System.out.println("anonymous canonical name null: "
                + (anonymous.getClass().getCanonicalName() == null));

        System.out.println("enum constant class is Mode: "
                + (Mode.CUSTOM.getClass() == Mode.class));
        System.out.println("enum declaring class is Mode: "
                + (Mode.CUSTOM.getDeclaringClass() == Mode.class));

        Object strings = new ArrayList<String>();
        Object numbers = new ArrayList<Integer>();
        System.out.println("erased generic class same: "
                + (strings.getClass() == numbers.getClass()));

        Object missing = null;
        System.out.println("missing == null: " + (missing == null));
        try {
            missing.getClass();
        } catch (NullPointerException ex) {
            System.out.println("missing.getClass() throws NPE");
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac GetClassFinalDemo.java
java GetClassFinalDemo
```

**Kết quả chuẩn:**

```text
declared Animal, runtime Dog: true
dog instanceof Animal: true
Animal vs Dog same class: false
two Dog instances same Class: true
same runtime class Dog vs Animal: false
record runtime class: true
anonymous exactly Animal: false
anonymous superclass Animal: true
anonymous canonical name null: true
enum constant class is Mode: false
enum declaring class is Mode: true
erased generic class same: true
missing == null: true
missing.getClass() throws NPE
```

**Giải thích các nhóm kết quả:**

1. `dogAsAnimal` có kiểu khai báo `Animal` nhưng là `new Dog()`, do đó `getClass() == Dog.class` là `true`. `instanceof Animal` cũng là `true` do quan hệ kế thừa.
2. `animal` và `dogAsAnimal` có lớp runtime khác nhau, nên phép so hai đối tượng `Class` là `false`. Hai thể hiện `Dog` độc lập lại có **cùng lớp** và chia sẻ metadata `Class`.
3. `sameRuntimeClass` so chính xác hai lớp và chủ động kiểm tra null ở cả hai phía. Hai đối tượng được tạo khác lớp cho kết quả `false`.
4. `Point` là record, nhưng record vẫn là một kiểu runtime cụ thể. `point.getClass()` trả `Point.class`.
5. `new Animal() {}` tạo **lớp con vô danh** của `Animal`. Đối tượng đó là `Animal` theo `instanceof`, nhưng lớp thực tế **không phải chính** `Animal.class`. `getSuperclass()` cho biết lớp cha trực tiếp là `Animal`, còn lớp vô danh không có tên canonical nên `getCanonicalName() == null`.
6. `Mode.CUSTOM` có **constant-specific class body**, do đó runtime class của hằng này có thể là một lớp con của enum `Mode`, khác `Mode.class`. `getDeclaringClass()` của hằng vẫn trả lớp enum khai báo, tức `Mode.class`.
7. Hai `ArrayList` với đối số kiểu `String` và `Integer` có cùng lớp runtime `ArrayList` sau khi type erasure. `getClass()` của thể hiện không cho phép suy ra đầy đủ generic type argument.
8. Gọi phương thức trên tham chiếu `null` ném `NullPointerException`; kiểm tra `missing == null` được phép và không gọi phương thức.

## 6. `getClass()`, `instanceof`, `Class.isInstance`: chọn đúng câu hỏi

| Cách kiểm tra | Ý nghĩa | Kết quả với `new Dog()` khi so `Animal` |
| --- | --- | --- |
| `obj.getClass() == Animal.class` | Lớp runtime **chính xác** là `Animal` | `false` |
| `obj instanceof Animal` | Đối tượng là `Animal` hoặc subtype | `true` |
| `Animal.class.isInstance(obj)` | Kiểm tra runtime xem đối tượng có thể dùng như `Animal` không | `true` |
| `Animal.class.isAssignableFrom(obj.getClass())` | Xem lớp `Animal` có thể nhận một giá trị của lớp runtime bên phải không | `true` nếu `obj` khác null |
| `obj == other` | Hai tham chiếu trỏ cùng đối tượng hay không | Không suy ra được chỉ từ tên lớp |

`instanceof` và `Class.isInstance` được thiết kế cho bài toán **tương thích kiểu**, chẳng hạn một nhánh xử lý áp dụng cho mọi lớp triển khai interface. `getClass() == SomeClass.class` dành cho bài toán cần đúng **lớp cụ thể**, không chấp nhận subtype.

Ví dụ với interface:

```java
interface Payable {}
final class Invoice implements Payable {}

Object value = new Invoice();

boolean isPayable = value instanceof Payable;          // true
boolean runtimeInvoice = value.getClass() == Invoice.class; // true
```

Đoạn này minh họa hai câu hỏi khác nhau, không phải một chương trình có `main`. Phần lớn mã ứng dụng tận dụng **đa hình** nên nên xử lý qua interface hoặc `instanceof` thay vì rẽ nhánh liên tục theo tên lớp. Chỉ chọn kiểm tra lớp chính xác khi đó thật sự là một yêu cầu của nghiệp vụ hoặc API.

## 7. Liên hệ với `equals()`: vì sao có người dùng `getClass()`?

Khi tự viết quan hệ bằng nhau theo giá trị, có hai chiến lược phổ biến để kiểm tra đối tượng bên phải:

**Chiến lược kiểm tra lớp chính xác:**

```java
@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    PointValue that = (PointValue) other;
    return x == that.x && y == that.y;
}
```

Đây là **thân phương thức minh họa** của một lớp `PointValue` có hai trường `int x`, `int y`; cần định nghĩa `hashCode()` phù hợp để thành một lớp value hoàn chỉnh. Điều kiện `getClass() != other.getClass()` loại mọi subtype khỏi quan hệ bằng nhau. Trong một kiểu có thể bị kế thừa, điều này đôi khi là quyết định có chủ đích để tránh các tình huống phức tạp về đối xứng/bắc cầu của `equals`.

**Chiến lược kiểm tra loại tương thích:**

```java
if (!(other instanceof PointValue value)) {
    return false;
}
```

Cách dùng `instanceof` có thể chấp nhận subtype. Điều này hợp lý khi tất cả subtype được phép tham gia **cùng một quan hệ bằng nhau** và các quy tắc thật sự giữ được hợp đồng `equals`. Nhưng nếu subtype bổ sung các trường tham gia equality, triển khai sơ suất có thể phá đối xứng hoặc bắc cầu.

**Không có một quy tắc “luôn phải dùng `getClass`” cho mọi lớp**. Phụ thuộc vào mô hình dữ liệu và hợp đồng equality:

- Với value class `final` hoặc record, không có subclass ứng dụng; `instanceof` và kiểm tra lớp chính xác thường tương đương cho các thể hiện cùng kiểu được xét.
- Với phân cấp kế thừa có mở rộng dữ liệu, cần thiết kế quan hệ bằng nhau rất cẩn thận; có thể giới hạn đúng lớp bằng `getClass()` hoặc ưu tiên composition/value object `final`.
- Đừng nhầm việc `getClass()` là `final` với việc `equals()` luôn so lớp chính xác. `Object.equals()` mặc định so **danh tính**; nhiều lớp tự override `equals()` theo ngữ nghĩa riêng. `getClass()` không tự được gọi bởi mọi triển khai `equals`.

### Trường hợp proxy hoặc lớp sinh động

Một đối tượng proxy/subclass do framework tạo có thể có lớp runtime khác với lớp domain mà chương trình nghĩ tới. Nếu dùng `getClass() == Domain.class` cứng nhắc, đối tượng ấy có thể bị từ chối dù nó vẫn tương thích với `Domain` theo `instanceof`. Trong các framework, cần đọc hợp đồng proxy và equality tương ứng; không tự giả định một thực thể proxy và thực thể gốc phải bằng nhau.

## 8. Các tình huống đặc biệt thường gặp

### 8.1. Lớp vô danh và lớp lồng

`getClass()` không trả “lớp cha gần nhất” mà trả chính lớp runtime, kể cả khi tên lớp được trình biên dịch sinh ra. Lớp vô danh có thể không có canonical name; không dựa vào `getSimpleName()` hoặc một tên có dấu `$` để xác định loại nghiệp vụ.

`getSuperclass()` là API khác: nó trả superclass trực tiếp của một `Class` (hoặc null cho một số loại như interface, primitive hoặc `Object.class`). Nó không thay thế `getClass()`.

### 8.2. Enum có constant-specific class body

Nếu enum định nghĩa phương thức riêng cho từng hằng bằng thân lớp, một hằng có thể có runtime class khác lớp enum khai báo. Muốn lấy **kiểu enum gốc**, dùng `someEnum.getDeclaringClass()`. Muốn kiểm tra đúng **hằng** nào, dùng `==` với hằng tương ứng.

Không giả định rằng mọi `enumConstant.getClass() == MyEnum.class` đều đúng khi enum có constant-specific class body.

### 8.3. Kiểu nguyên thủy và mảng

Primitive không phải đối tượng để gọi instance method:

```java
int number = 42;
// number.getClass(); // Sai: int là kiểu nguyên thủy, không có getClass().
Class<?> primitiveType = int.class;
Class<?> boxedType = Integer.valueOf(number).getClass();
```

Đoạn trên là minh họa khai báo biến. `primitiveType` biểu diễn kiểu nguyên thủy `int`; `boxedType` biểu diễn lớp `Integer`. Chúng không bằng nhau.

Mảng là đối tượng, nên `new String[0].getClass() == String[].class` là `true`. Đừng suy ra mảng là `String.class`: lớp của `String[]` khác lớp của từng phần tử `String`.

### 8.4. Generic type erasure

Với:

```java
Object first = new java.util.ArrayList<String>();
Object second = new java.util.ArrayList<Integer>();

boolean sameClass = first.getClass() == second.getClass(); // true
```

`getClass()` không đưa ra tham số kiểu generic `String` hoặc `Integer` của từng lần khởi tạo khi thông tin ấy đã bị xóa bởi type erasure. API reflection về generic declarations có thể đọc cấu trúc generic trong **khai báo lớp/trường/phương thức** khi metadata tồn tại, nhưng không biến mỗi đối tượng `ArrayList<T>` thành một lớp runtime khác nhau cho từng `T`.

### 8.5. Lớp nạp bởi các class loader khác nhau

Tên đầy đủ giống nhau không phải lúc nào cũng có nghĩa đó là **cùng một kiểu runtime**. Java tính danh tính lớp theo định danh lớp và class loader xác định lớp đó. Khi cùng tên lớp được nạp độc lập bởi những bộ nạp khác nhau, hai đối tượng `Class` có thể khác nhau. So `Class<?>` bằng `==` phản ánh đúng danh tính runtime; so các chuỗi từ `getName()` có thể chỉ nói chúng **cùng tên**, không chứng minh cùng lớp.

Trong ứng dụng thông thường, điều này ít xuất hiện; trong plugin system, app server, hot deployment hoặc môi trường module phức tạp, đây là nguyên nhân quan trọng của lỗi tương thích kiểu.

## 9. `getClass()` và reflection

Một khi đã có đối tượng `Class<?>`, có thể xem thông tin cấu trúc mà không làm thay đổi bản chất của đối tượng:

```java
Object value = "hello";
Class<?> type = value.getClass();

String binaryName = type.getName();       // "java.lang.String"
String simpleName = type.getSimpleName(); // "String"
boolean isString = type == String.class;  // true
```

Đây là đoạn Java minh họa có thể đặt trong `main`. Reflection còn có các phương thức để truy vấn constructor, field, method hoặc annotation theo quy tắc truy cập của Java. `getClass()` tự nó **không** tự động đọc hết trường dữ liệu, không thay đổi khả năng truy cập thành viên và không biến một đối tượng thành một thể hiện của lớp khác.

Khi cần tạo đối tượng kiểu đã biết ngay tại thời điểm biên dịch, hãy ưu tiên `SomeClass.class`. Khi nhận một `Object` bất kỳ và phải biết kiểu runtime, dùng `getClass()` sau khi đã xử lý khả năng null.

## 10. Lỗi phổ biến và cách tránh

| Nhận định hoặc cách làm | Vấn đề | Cách đúng |
| --- | --- | --- |
| “Biến `Animal` luôn trả `Animal.class`.” | Sai nếu nó trỏ tới `Dog` hoặc lớp con khác. | `getClass()` trả lớp thực tế của đối tượng. |
| “Override `getClass()` để hiển thị loại riêng.” | Phương thức của `Object` là `final`; không biên dịch. | Định nghĩa `kind()` hoặc phương thức tên khác. |
| “`getClass() == Base.class` kiểm tra cả lớp con.” | Nó chỉ nhận **đúng lớp Base**. | Dùng `instanceof Base` nếu muốn nhận subtype. |
| “Hai đối tượng có cùng `getClass()` thì `equals()` bằng nhau.” | Cùng kiểu không chứng minh bằng nhau về danh tính hay giá trị. | Dùng hợp đồng `equals` hoặc `==` theo mục đích. |
| “Có thể gọi `value.getClass()` khi `value` là null.” | Ném `NullPointerException`. | Kiểm tra null trước hoặc dùng `instanceof` khi phù hợp. |
| “So `getClass().getName()` luôn đủ xác định lớp.” | Có thể cùng tên nhưng khác class loader. | So `Class` theo danh tính khi thực sự cần exact runtime class. |
| “`getClass()` phân biệt được `List<String>` và `List<Integer>`.” | Generic args thường bị xóa trong runtime instance type. | Đưa thông tin kiểu vào API/`Class<T>` hoặc type token khi cần. |
| “Mọi hằng enum có `getClass() == MyEnum.class`.” | Constant-specific class body có runtime subclass. | Dùng `getDeclaringClass()` cho kiểu enum khai báo. |
| “`getClass()` final làm mọi lớp con thành final.” | Ràng buộc final chỉ áp dụng phương thức đó. | Phân biệt `final` method với `final` class. |
| “`getClass()` và `getSuperclass()` tương đương.” | Một cái là lớp runtime đối tượng, một cái hỏi lớp cha của metadata `Class`. | Chọn API theo câu hỏi cần trả lời. |

## 11. Checklist lựa chọn nhanh

1. **Muốn biết chính xác lớp của một đối tượng?** Kiểm tra nó khác `null`, sau đó dùng `obj.getClass()`.
2. **Muốn đối tượng có tương thích một interface/lớp cha?** Dùng `instanceof` hoặc `Class.isInstance`.
3. **Muốn metadata của kiểu đã biết khi biên dịch?** Dùng `Type.class`; không cần tạo một đối tượng chỉ để gọi `getClass()`.
4. **Muốn so hai lớp runtime chính xác?** Dùng `a.getClass() == b.getClass()` sau khi xử lý null.
5. **Muốn kiểm tra hai đối tượng giống nhau về dữ liệu?** Dùng quan hệ `equals()` của lớp đã định nghĩa, không suy ra từ `getClass()`.
6. **Muốn kiểu enum khai báo dù hằng có thân lớp riêng?** Dùng `enumValue.getDeclaringClass()`.
7. **Muốn tạo loại nghiệp vụ do người dùng định nghĩa?** Tạo API tên khác; `Object.getClass()` không thể override.

## 12. Tự kiểm tra

**Câu 1.** `Animal x = new Dog();`. `x.getClass() == Animal.class` là `true` hay `false`?

**Đáp án:** `false`, vì runtime class là `Dog`. `x instanceof Animal` vẫn là `true`.

**Câu 2.** Có thể khai báo `public Class<?> getClass()` trong lớp riêng để “thay kết quả” của `Object.getClass()` không?

**Đáp án:** Không. `Object.getClass()` là `final`; phương thức cùng chữ ký không được override.

**Câu 3.** Hai đối tượng khác nhau của `Dog` có cùng `getClass()` không? Có chắc chắn `equals()` bằng nhau không?

**Đáp án:** Chúng có cùng `Class` runtime. `equals()` còn phụ thuộc quy tắc của lớp; không thể suy từ cùng kiểu.

**Câu 4.** Vì sao `new Animal() {}` không có `getClass() == Animal.class`?

**Đáp án:** Đó là một lớp con vô danh thực sự; runtime class là lớp con ấy.

**Câu 5.** Tại sao `getClass()` của hai `ArrayList<String>` và `ArrayList<Integer>` giống nhau?

**Đáp án:** Cả hai là thể hiện runtime của `ArrayList`; thông tin generic type argument không tạo lớp runtime tách biệt sau type erasure.

**Câu 6.** Khi cần kiểm tra một giá trị thuộc subtype của `Base`, dùng `obj.getClass() == Base.class` hay `obj instanceof Base`?

**Đáp án:** `obj instanceof Base`. Cách kiểm tra lớp bằng nhau chính xác sẽ loại subtype.

**Ghi nhớ:** `Object.getClass()` là **`public final native`**, luôn trả metadata của **lớp runtime thực tế** đối với một đối tượng khác null. Không lớp nào được override nó. Hãy dùng `getClass()` khi cần exact type, `instanceof` khi cần quan hệ kiểu, và `equals()` khi cần so ngữ nghĩa giá trị.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="real-world-classes-004">Class literal Type.class và so sánh với getClass()</a>

<details>
<summary>Xem chi tiết</summary>

# Class literal `Type.class` và so sánh với `getClass()` trong Java

## 1. Mục tiêu của bài học

Khi làm việc với một object trong Java, có ít nhất hai khái niệm kiểu cần phân biệt:

- **Kiểu ở khai báo/biểu thức** (*static type*): kiểu mà compiler biết khi phân tích mã, chẳng hạn biến `Animal animal` được khai báo là `Animal`.
- **Lớp thực tế ở runtime** (*runtime class*): lớp cụ thể của object mà biến đang tham chiếu, chẳng hạn object tạo bởi `new Dog()`.

Java cho phép một biến tham chiếu đến instance của lớp con hoặc lớp cài đặt interface. Do đó, kiểu khai báo **không nhất thiết** trùng lớp thực tế. Hai cách lấy đối tượng `Class` thể hiện hai câu hỏi khác nhau:

- `Animal.class`: đối tượng `Class<Animal>` đại diện cho kiểu `Animal` đã được nêu tường minh trong **mã nguồn**.
- `animal.getClass()`: đối tượng `Class<?>` đại diện cho **lớp thực tế** của instance mà `animal` đang trỏ tới, được xác định lúc chạy.

Trong phạm vi Java Core – Object Contract, hiểu khác biệt này giúp kiểm tra kiểu, sử dụng reflection và tránh nhầm lẫn khi thiết kế `equals()` của các lớp có kế thừa, proxy hoặc nhiều implementation.

Sau bài học, bạn có thể giải thích và áp dụng `Type.class`, `obj.getClass()`, `instanceof`, `Class.isInstance`, `Class.isAssignableFrom`; xử lý `null`; hiểu `Class` của primitive/array/interface; và chọn kiểu so sánh thích hợp với ý nghĩa nghiệp vụ.

## 2. `Type.class` thực sự là gì?

**Class literal** là biểu thức có cú pháp `TênKiểu.class`. Ví dụ:

```java
Class<String> stringType = String.class;
Class<Integer> boxedType = Integer.class;
Class<?> arrayType = String[].class;
Class<?> primitiveType = int.class;
Class<?> noValueType = void.class;
```

Đây là các câu lệnh minh họa đặt bên trong phương thức hoặc khối khởi tạo phù hợp, không phải file Java chạy độc lập.

Trong từng trường hợp, biểu thức trả về một object `java.lang.Class` đại diện cho kiểu đã nêu. Nó **không tạo instance** kiểu đó:

- `String.class` không tạo một `String` mới.
- `Dog.class` không gọi constructor `Dog`.
- `SomeInterface.class` đại diện cho interface, dù interface không thể được tạo bằng `new SomeInterface()`.
- `int.class` đại diện cho kiểu primitive `int`, không phải `Integer.class`.
- `String[].class` đại diện cho lớp runtime của mảng phần tử `String`, khác `String.class`.

Với một lớp thông thường được định nghĩa trong một class loader xác định, các lần lấy `T.class` đều trả về cùng một object `Class` cho chính định nghĩa đó. Nhưng **tên lớp giống nhau chưa chắc là cùng lớp runtime** nếu hai class loader khác nhau nạp hai định nghĩa: identity của lớp gắn với cả tên nhị phân và defining class loader.

`Class` là metadata đại diện cho một kiểu, không phải instance nghiệp vụ của kiểu đó. `String.class` là một đối tượng kiểu `Class<String>`, không phải một `String`.

### Class literal có chủ động khởi tạo lớp không?

Việc đánh giá class literal như `T.class` không tự kích hoạt **class initialization** của `T` chỉ vì tham chiếu literal đó. Khác biệt cần chú ý so với một số hành động chủ động khác như gọi static method/đọc static field không phải compile-time constant hay `Class.forName(String)` phiên bản một tham số, vốn thường yêu cầu tải và khởi tạo lớp.

Tuy nhiên, “không tự khởi tạo” không có nghĩa JVM không cần resolve/load thông tin kiểu để có đối tượng `Class`. **Loading, linking, initialization** là các giai đoạn khác nhau trong cơ chế JVM; không dùng class literal như một kỹ thuật cam kết “không đụng tới class loader”.

## 3. `obj.getClass()` lấy lớp thực tế như thế nào?

`getClass()` là phương thức `public final native Class<?> getClass()` của `java.lang.Object` về mặt API; mọi object Java đều có phương thức này và các subclass không thể override nó. Kiểu trả về chính xác trong khai báo generic của Java API có ràng buộc theo kiểu của receiver; khi sử dụng thực tế có thể viết `Class<?>` nếu chỉ cần metadata chung.

Ví dụ:

```java
class Animal {}
class Dog extends Animal {}

Animal animal = new Dog();
System.out.println(Animal.class == animal.getClass()); // false
System.out.println(Dog.class == animal.getClass());    // true
```

Đây là minh họa ở nơi cho phép khai báo class/các lệnh trong phương thức. **Kiểu khai báo** của biến `animal` là `Animal`. **Object đang được giữ** là một `Dog`. Lúc gọi `animal.getClass()`, JVM trả metadata của `Dog` vì đó là lớp thực tế của object.

Có thể dùng:

```java
Object any = "hello";
Class<?> runtimeType = any.getClass(); // String.class
```

Không cần cast `any` thành `String` để biết runtime class của object. Nhưng `getClass()` **cần một object khác null**. Nếu `any == null`, gọi `any.getClass()` sẽ ném `NullPointerException`. Ngược lại, `String.class` không cần bất kỳ biến instance nào nên vẫn đánh giá bình thường.

### So với `getClass().getName()`

- `obj.getClass()` trả đối tượng `Class` để kiểm tra identity/metadata.
- `obj.getClass().getName()` trả **chuỗi tên nhị phân** của lớp (với mảng có ký hiệu runtime đặc biệt).
- `obj.getClass().getSimpleName()` trả tên ngắn, có thể rỗng trong một số trường hợp, ví dụ các lớp vô danh.
- `obj.getClass().getCanonicalName()` có thể trả `null` cho các loại class không có canonical name.
- `obj.toString()` là cách object muốn biểu diễn chính nó, không đồng nghĩa luôn là tên lớp.

Khi muốn kiểm tra có đúng cùng lớp không, dùng **`Class` identity** (ví dụ `obj.getClass() == Dog.class`) thay vì so chuỗi tên simple name. Hai lớp ở package khác nhau có thể cùng `getSimpleName()`; hai class loader cũng có thể nạp các lớp trùng tên mà khác identity.

## 4. Bảng so sánh nhanh

| Tình huống | `Type.class` | `obj.getClass()` |
|---|---|---|
| Nguồn xác định | Tên kiểu ghi trong source | Instance thực tế tại runtime |
| Cần instance? | Không | Có, và khác `null` |
| Với biến kiểu cha chứa object lớp con | `Base.class` vẫn là `Base` | Trả `Derived.class` |
| Với interface | `Interface.class` hợp lệ | Instance trả lớp triển khai thực tế |
| Với primitive `int` | `int.class` hợp lệ | Primitive không phải object nên không gọi trực tiếp `getClass()` |
| Với boxed value `Integer` | `Integer.class` | `Integer.valueOf(1).getClass()` là `Integer.class` |
| Với array | `String[].class` | `new String[0].getClass()` là `String[].class` |
| Với biến `null` | `Type.class` vẫn hợp lệ | Gọi `getClass()` ném `NullPointerException` |
| Thay đổi theo object đang tham chiếu | Không | Có thể khác khi biến được gán instance lớp khác |

Một biến có static type `Object` không trở thành instance lớp `Object` chỉ vì nó được khai báo thế; `new String("x")` gán vào biến đó vẫn có runtime class `String`.

## 5. Khi nào cần `==`, `equals`, `instanceof` và `isAssignableFrom`?

Các phép kiểm tra dễ bị nhầm là đang trả lời cùng một câu hỏi, nhưng thực tế khác nhau.

### 5.1. So sánh chính xác hai đối tượng `Class`

```java
boolean exact = value.getClass() == Dog.class;
```

Điều này hỏi: **lớp runtime của `value` có đúng là `Dog` không?** Một instance của subclass `SpecialDog extends Dog` (nếu `Dog` không `final`) sẽ không vượt qua kiểm tra exact class của `Dog`.

`Class.equals` cũng so identity của object `Class`, nên `Dog.class.equals(value.getClass())` tương đương `Dog.class == value.getClass()` về ngữ nghĩa. `==` là lựa chọn quen thuộc vì đối tượng `Class` biểu diễn cùng một runtime class được tái sử dụng.

### 5.2. `instanceof` và `Class.isInstance` kiểm tra tương thích kiểu

```java
boolean compatible = value instanceof Dog;
boolean reflectiveCheck = Dog.class.isInstance(value);
```

Với `value` khác null, hai kiểm tra này đều đúng nếu object là `Dog` hoặc subclass của `Dog`. Với `null`, cả hai trả `false` (với `isInstance(null)` trả `false`). `instanceof` còn kiểm tra implementation của interface, không cần lấy `getClass()`.

Trong mã Java thông thường với kiểu được biết tĩnh, ưu tiên `instanceof` khi chỉ hỏi khả năng cast/kiểu tương thích. Dùng `Class.isInstance` khi đối tượng `Class<?>` đã được lấy động, chẳng hạn qua cấu hình/plugin.

### 5.3. `Class.isAssignableFrom` so **hai kiểu**, không cần object

```java
boolean canAssignDogToAnimal =
        Animal.class.isAssignableFrom(Dog.class); // true
boolean canAssignAnimalToDog =
        Dog.class.isAssignableFrom(Animal.class); // false
```

Đọc `A.class.isAssignableFrom(B.class)` là “một giá trị kiểu `B` có thể gán vào biến tham chiếu kiểu `A` theo quan hệ gán kiểu do Java quy định không?”. Với các lớp tham chiếu, interface và array, nó mô tả quan hệ kiểu runtime, gồm kế thừa/cài đặt interface.

**Hướng tham số rất dễ bị viết ngược:** `Animal.class.isAssignableFrom(Dog.class)` là `true`, còn ngược lại là `false`. Phương thức này không tự kiểm tra một object cụ thể vì đầu vào là một đối tượng `Class<?>`.

Lưu ý primitive `Class` không áp dụng boxing tự động khi kiểm tra assignability: `int.class.isAssignableFrom(Integer.class)` là `false`. Boxing là cơ chế của ngôn ngữ và lời gọi phương thức, không làm `int.class` và `Integer.class` trở thành cùng kiểu runtime.

### 5.4. Chọn công cụ đúng mục đích

| Câu hỏi | Công cụ |
|---|---|
| “Kiểu `Dog` được nêu trong source là kiểu nào?” | `Dog.class` |
| “Instance này thực chất thuộc lớp nào?” | `obj.getClass()` |
| “Instance này có thể xem/cast thành `Dog` không?” | `obj instanceof Dog` |
| “Kiểu chỉ biết lúc chạy có nhận instance này không?” | `selectedClass.isInstance(obj)` |
| “Định nghĩa lớp `Animal` có bao gồm một `Dog` không?” | `Animal.class.isAssignableFrom(Dog.class)` |
| “Hai instance có cùng lớp runtime chính xác không?” | `a.getClass() == b.getClass()` (sau khi kiểm tra null) |
| “Hai instance bằng nhau theo nghiệp vụ không?” | `a.equals(b)` theo contract của lớp; không thay bằng so `Class` |

## 6. Chương trình Java 17 chạy độc lập

Tạo tệp **`ClassLiteralDemo.java`**. Chương trình cố định dữ liệu và chỉ in những kết quả không phụ thuộc tên class ẩn hay hash runtime.

```java
public class ClassLiteralDemo {
    static class Animal { }

    static class Dog extends Animal { }

    static class Cat extends Animal { }

    interface Tagged { }

    static final class TaggedDog extends Dog implements Tagged { }

    public static void main(String[] args) {
        Animal animal = new Dog();
        Object sameObject = animal;

        System.out.println("declared literal: "
                + Animal.class.getSimpleName());
        System.out.println("runtime class: "
                + animal.getClass().getSimpleName());
        System.out.println("base exact: "
                + (animal.getClass() == Animal.class));
        System.out.println("dog exact: "
                + (animal.getClass() == Dog.class));
        System.out.println("same Class object: "
                + (animal.getClass() == sameObject.getClass()));

        System.out.println("instanceof Animal: "
                + (animal instanceof Animal));
        System.out.println("instanceof Dog: "
                + (animal instanceof Dog));
        System.out.println("Animal.isInstance: "
                + Animal.class.isInstance(animal));
        System.out.println("Animal <- Dog: "
                + Animal.class.isAssignableFrom(Dog.class));
        System.out.println("Dog <- Animal: "
                + Dog.class.isAssignableFrom(Animal.class));

        Animal tagged = new TaggedDog();
        System.out.println("subclass exact Dog: "
                + (tagged.getClass() == Dog.class));
        System.out.println("subclass instanceof Dog: "
                + (tagged instanceof Dog));
        System.out.println("tag interface: "
                + Tagged.class.isInstance(tagged));

        Object absent = null;
        System.out.println("null isInstance: "
                + Animal.class.isInstance(absent));
        System.out.println("null instanceof: "
                + (absent instanceof Animal));

        System.out.println("primitive name: " + int.class.getName());
        System.out.println("wrapper name: " + Integer.class.getName());
        System.out.println("primitive equals wrapper: "
                + int.class.equals(Integer.class));
        System.out.println("array exact: "
                + (new String[0].getClass() == String[].class));
        System.out.println("array as Object: "
                + Object.class.isAssignableFrom(String[].class));
        System.out.println("interface literal: "
                + Tagged.class.isInterface());
    }
}
```

Tại thư mục chứa tệp, dùng JDK 17 hoặc mới hơn:

```bash
javac --release 17 ClassLiteralDemo.java
java ClassLiteralDemo
```

Output:

```text
declared literal: Animal
runtime class: Dog
base exact: false
dog exact: true
same Class object: true
instanceof Animal: true
instanceof Dog: true
Animal.isInstance: true
Animal <- Dog: true
Dog <- Animal: false
subclass exact Dog: false
subclass instanceof Dog: true
tag interface: true
null isInstance: false
null instanceof: false
primitive name: int
wrapper name: java.lang.Integer
primitive equals wrapper: false
array exact: true
array as Object: true
interface literal: true
```

### Giải thích các nhóm kết quả

**Nhóm một — Kiểu khai báo khác lớp runtime.** Biến `animal` có kiểu khai báo `Animal`, còn instance được tạo từ `Dog`. Vì thế `Animal.class` vẫn là `Animal`, nhưng `animal.getClass()` là `Dog.class`. Hai biến `animal` và `sameObject` trỏ cùng object nên lấy về cùng đối tượng `Class`.

**Nhóm hai — Kế thừa và assignability.** `Dog extends Animal` nên Dog là một Animal; `instanceof Animal` đúng và `Animal.class.isAssignableFrom(Dog.class)` đúng. Chiều `Dog.class.isAssignableFrom(Animal.class)` sai: không phải mọi Animal đều là Dog.

**Nhóm ba — Lớp con tiếp theo.** `TaggedDog extends Dog` nên object đó tương thích với `Dog` nhưng runtime class chính xác là `TaggedDog`. Phép `tagged.getClass() == Dog.class` sai trong khi `tagged instanceof Dog` đúng. Nó cũng implements `Tagged` nên `Tagged.class.isInstance(tagged)` đúng.

**Nhóm bốn — Null.** `Class.isInstance(null)` và `null instanceof Animal` trả `false`. Nhưng `absent.getClass()` sẽ ném `NullPointerException` và không được gọi ở đây.

**Nhóm năm — Primitive, wrapper, array, interface.** `int.class` và `Integer.class` biểu diễn hai kiểu khác nhau; primitive không tự boxing khi được biểu diễn bằng đối tượng `Class`. Mảng `String[]` có runtime Class riêng, và một mảng là object nên có thể gán cho `Object`. `Tagged.class` đại diện cho interface, kể cả khi bản thân interface không thể được instantiate trực tiếp.

### Những kết luận không được rút ra

- `animal instanceof Animal` đúng **không** suy ra `animal.getClass() == Animal.class`.
- `animal.getClass() == Dog.class` đúng **không** có nghĩa ta cần ép kiểu trước khi gọi `getClass()`.
- `Dog.class.isAssignableFrom(Animal.class)` sai **không** có nghĩa không thể có một biến `Animal` chứa Dog.
- `Object.class.isAssignableFrom(String[].class)` đúng **không** có nghĩa `String[].class == Object.class`.
- Hai instance cùng runtime class **không tự động bằng nhau** theo `equals`; đó vẫn là trách nhiệm của lớp.

## 7. Hệ quả với `equals()`: class literal không quyết định thay chính sách bình đẳng

`Type.class` mô tả một kiểu nêu **cố định trong source**, còn `getClass()` mô tả lớp runtime của từng instance. Khi viết `equals(Object)`, hai cách kiểm tra sau thể hiện hai chính sách khác nhau:

```java
// Trong equals(Object) của một lớp Base:
if (other == null || getClass() != other.getClass()) return false;

// Phương án khác, chỉ khi domain cho phép lớp con tham gia equality:
if (!(other instanceof Base that)) return false;
```

Đây là **hai đoạn thay thế**, không dùng đồng thời; chúng giả định đang ở trong lớp `Base` và phần còn lại của `equals` phải so các trường xác định giá trị. Cách đầu chỉ cho hai instance **cùng lớp runtime** đi tiếp. Cách thứ hai nhận cả subclass, nên các lớp trong hệ kế thừa phải thống nhất tiêu chí bình đẳng: nếu `base.equals(sub)` đúng nhưng `sub.equals(base)` sai thì đã vi phạm **đối xứng**. Dù chọn cách nào, `equals` vẫn phải phản xạ, đối xứng, bắc cầu, nhất quán và tương thích với `hashCode`.

Không thay máy móc `getClass() == other.getClass()` bằng `other.getClass() == Base.class`: class literal `Base.class` luôn cố định, trong khi `getClass()` của `this` có thể là subclass nếu phương thức được kế thừa. Với value object `final` hoặc record, rủi ro phát sinh từ subclass thường giảm; với entity/proxy runtime, kiểm tra lớp chính xác có thể loại một đối tượng đại diện cùng bản ghi. Hãy chọn chính sách equality theo mô hình và hợp đồng framework đang dùng.

Điểm cần nhớ trong bài **class literal** là `Base.class` và `obj.getClass()` không tự thay thế cho nhau. Chúng chỉ cung cấp thông tin kiểu; bản thân chúng không quyết định hai đối tượng có **cùng giá trị nghiệp vụ** hay không.

## 8. Primitive, mảng, interface, enum và record

### Primitive và `void`

`int.class` là Class của primitive `int`; `Integer.class` là Class của lớp wrapper. Hai object này khác nhau. `void.class` cũng là class literal hợp lệ đại diện kiểu `void`. Không có instance Java thông thường của `int` hoặc `void` để gọi `getClass()` trực tiếp.

Trong các API reflection, có thể dùng `Class.isPrimitive()` để kiểm tra `int.class.isPrimitive()==true`, trong khi `Integer.class.isPrimitive()==false`. `void.class.isPrimitive()` cũng được xem là primitive theo API `Class`.

### Array

`new String[0].getClass() == String[].class` là đúng. Độ dài mảng không làm thay đổi runtime class. Mảng primitive và mảng wrapper là hai kiểu khác nhau: `int[].class != Integer[].class`. Mảng `String[]` và `Object[]` cũng là các đối tượng `Class` khác nhau, dù `Object[].class.isAssignableFrom(String[].class)` đúng do covariance của mảng tham chiếu.

Mảng còn implements `Cloneable` và `java.io.Serializable`. `Object.class.isAssignableFrom(int[].class)` trả đúng, vì mảng primitive vẫn là object. Nhưng `int.class.isAssignableFrom(Integer.class)` vẫn sai.

### Interface

`Runnable.class` đại diện một interface. Một object có thể implements `Runnable` nhưng `obj.getClass()` trả lớp triển khai, không trả `Runnable.class`. Nếu muốn hỏi object có triển khai interface không, dùng `obj instanceof Runnable` hoặc `Runnable.class.isInstance(obj)`.

### Enum

`DayOfWeek.MONDAY.getClass()` trả runtime class của object enum. Với enum có **constant-specific class body** (mỗi hằng có phần thân riêng), runtime class có thể là một subclass đặc biệt, còn `getDeclaringClass()` trả enum type khai báo hằng; vì vậy không mặc định lấy `constant.getClass() == MyEnum.class` trong mọi kiểu khai báo enum. Với enum thông thường không có constant-specific class body, hai kết quả thường trùng.

### Record

Một record là lớp `final`, nên với một instance record được tạo bình thường, `recordInstance.getClass() == RecordType.class`. Nó vẫn là object Java, kế thừa `Object.getClass` và được kiểm tra bằng `instanceof` như các kiểu tham chiếu khác. Việc record được compiler sinh `equals`/`hashCode` dựa trên components **không phải** là do Java tự so một chuỗi tên lớp.

### Anonymous class và lambda

Một anonymous class có runtime class riêng, nên `new Runnable() { public void run() {} }.getClass()` không phải `Runnable.class`. Lambda cũng có runtime implementation class do JVM tạo; không dựa vào tên, số thứ tự, tính ổn định hoặc đường dẫn class này như API công khai. Nếu chỉ cần biết object có thực hiện interface nào, dùng `instanceof` hoặc `Class.isInstance`.

## 9. Class loader và sự khác biệt giữa tên lớp với identity

`Class.getName()` có thể cho cùng một chuỗi từ hai lớp được nạp bằng hai defining class loader khác nhau, nhưng hai runtime class đó **không phải cùng kiểu**. Điều này quan trọng trong plugin system, application server, modular runtime và các hệ thống có class loader cô lập.

Đừng suy luận:

```java
left.getClass().getName().equals(right.getClass().getName())
```

là đủ để hai object có cùng runtime class. Đây là đoạn biểu thức minh họa với `left`/`right` không null, không phải chương trình độc lập. Phép so đúng Class identity khi cần exact class là:

```java
left.getClass() == right.getClass()
```

Lưu ý: equality của entity/value object là chuyện khác; cùng class runtime cũng không đủ để kết luận hai object cùng giá trị hay cùng danh tính nghiệp vụ.

### So với `Class.forName`

`Type.class` sử dụng tên kiểu biết ở compile time. `Class.forName("mypackage.Type")` tra cứu kiểu bằng chuỗi runtime qua cơ chế nạp lớp; phiên bản một tham số có thể khởi tạo lớp. Nếu cần điều khiển, có overload `Class.forName(name, initialize, loader)`, cho phép lựa chọn việc khởi tạo và loader.

Chỉ dùng `Class.forName` khi thực sự cần tên động, chẳng hạn đọc cấu hình, và xử lý `ClassNotFoundException` hoặc vấn đề visibility/security phù hợp. Khi đã biết kiểu tại compile time, class literal rõ ràng và type-safe hơn.

## 10. Generics: `Class<T>` và giới hạn của `getClass()`

`Class<String>` mang generic type parameter biểu thị nó đại diện cho `String`, hỗ trợ các API reflection có typing tốt hơn. `Class<?>` là kiểu an toàn khi chỉ biết một lớp bất kỳ.

```java
Class<String> stringClass = String.class;
Class<?> runtimeClass = "text".getClass();

System.out.println(stringClass == runtimeClass); // true
```

Đoạn này nằm trong phương thức. Một số biểu thức so sánh trực tiếp giữa `Class<...>` có kiểu generic không tương thích có thể gây lỗi biên dịch do kiểm tra type; khi API cần làm việc với các Class không biết trước, lưu chúng dưới dạng `Class<?>` và dùng `Class.equals` hoặc các API phản chiếu được định kiểu.

**Type erasure** vẫn tồn tại: `List<String>` và `List<Integer>` đều có runtime class của cùng một kiểu triển khai như `ArrayList` khi cùng được tạo từ `new ArrayList<>()`. Không có `List<String>.class` vì class literal không nhận một kiểu tham số hóa. Việc gọi `list.getClass()` không thể cho biết chắc từng phần tử của list là String.

Do đó, `getClass()` là nguồn của **runtime class** chứ không phải toàn bộ thông tin generic source type. Nếu cần generic declaration metadata, những API reflection như `getGenericSuperclass()`/`getGenericInterfaces()` có giới hạn và chỉ phản ánh cấu trúc khai báo; chúng không khôi phục mọi generic argument đã bị xóa ở runtime.

## 11. Cạm bẫy và cách khắc phục

| Lỗi thường gặp | Vì sao sai | Cách đúng |
|---|---|---|
| `animal.getClass() == Animal.class` để hỏi có phải Animal không | Subclass bị loại vì runtime class khác | `animal instanceof Animal` hoặc `Animal.class.isInstance(animal)` |
| Gọi `obj.getClass()` khi `obj` có thể null | Ném `NullPointerException` | Kiểm tra null hoặc dùng `X.class.isInstance(obj)` |
| Dùng `getSimpleName()` để kiểm tra identity | Các lớp khác package/loader có thể cùng tên ngắn | So `Class` identity trực tiếp |
| Dùng `int.class == Integer.class` như thử boxing | Primitive và wrapper khác loại | Phân biệt primitive type với wrapper; đừng suy từ boxing |
| Viết `Dog.class.isAssignableFrom(Animal.class)` để kiểm tra Dog là Animal | Đảo chiều quan hệ gán | `Animal.class.isAssignableFrom(Dog.class)` |
| Tưởng `getClass()` trả kiểu khai báo của biến | Nó trả lớp của object runtime | Dùng class literal khi muốn kiểu cố định ở source |
| Dùng `getClass()` quá chặt trong equality của entity dùng ORM proxy | Proxy có thể là subclass runtime | Theo quy tắc equality/proxy của ORM thực tế |
| Chấp nhận subclass bằng `instanceof` nhưng subclass thay đổi equality | Có thể phá đối xứng/bắc cầu | Thiết kế hợp đồng cho hierarchy hoặc dùng value object `final` |
| Dùng `getName()` làm identity lớp | Class loader khác nhau có thể định nghĩa cùng tên | So object `Class`, cân nhắc defining class loader |
| Nghĩ `getClass()` khôi phục `List<String>` | Generic erasure | Thiết kế type token hoặc metadata khác khi cần |
| Gắn tên runtime class của lambda/proxy vào business logic | Tên/lớp tổng hợp là chi tiết triển khai | So interface/hợp đồng API, không suy từ tên tự sinh |

## 12. Bài tập thực hành

### Bài 1 — Kế thừa và runtime class

Tạo `Animal` và `Dog extends Animal`. Gán `Animal x = new Dog()`, rồi dự đoán:
- `x.getClass() == Animal.class` → `false`.
- `x.getClass() == Dog.class` → `true`.
- `x instanceof Animal` → `true`.
- `Animal.class.isAssignableFrom(Dog.class)` → `true`.
- `Dog.class.isAssignableFrom(Animal.class)` → `false`.

Giải thích bằng static type và runtime class, không chỉ học thuộc kết quả.

### Bài 2 — Kiểu interface và nhiều implementation

Tạo `interface Payment`, hai lớp `CardPayment` và `CashPayment` implements `Payment`. Dùng biến `Payment payment` lần lượt trỏ vào từng object. `Payment.class` luôn đại diện interface; `payment.getClass()` lần lượt trả các implementation tương ứng; `payment instanceof Payment` luôn đúng với object khác null.

### Bài 3 — Null và API động

Cho `Object maybeNull = null`. So kết quả `maybeNull instanceof String`, `String.class.isInstance(maybeNull)`, và thử `maybeNull.getClass()` trong một `try/catch`. Hai phép đầu trả `false`, phép cuối ném `NullPointerException`. Giải thích tại sao class literal không cần instance.

### Bài 4 — Equals cho value object

Tạo class `final` có hai trường bất biến `code` và `version`. Viết `equals` kiểm tra `this==other`, kiểu exact và hai trường; `hashCode` dùng cùng trường. Tạo hai instance riêng cùng giá trị, xác nhận chúng bằng nhau và hash bằng nhau. Sau đó giải thích vì sao `getClass()` trong lớp `final` không gặp vấn đề subclass.

### Bài 5 — Array và primitive

So `int.class` với `Integer.class`, `int[].class` với `Integer[].class`, `String[].class` với `Object[].class`. Kiểm tra thêm `Object[].class.isAssignableFrom(String[].class)` và `Object.class.isAssignableFrom(int[].class)`, rồi giải thích array covariance và array là object.

### Bài 6 — So sánh khi dùng proxy

Giả lập `ProxyEntity extends Entity` với `Entity normal = new Entity(...)` và `Entity proxy = new ProxyEntity(...)`. So `normal.getClass() == proxy.getClass()` và `proxy instanceof Entity`. Nêu tại sao phép so exact class có thể không phù hợp cho ORM proxy, và phải thống nhất chính sách equality trước khi dùng entity làm khóa collection.

### Bài 7 — Class loader

Tìm hiểu ứng dụng plugin nạp cùng fully qualified class name qua hai class loader riêng. Giải thích vì sao `getName()` có thể trùng nhưng đối tượng `Class` khác nhau; tại sao cast chéo giữa hai runtime class không nhất thiết thành công dù tên giống nhau. Không cần tự viết custom class loader để hiểu định nghĩa.

## 13. Checklist áp dụng trong dự án

- Đã phân biệt static type trong source và runtime class của instance.
- Dùng `Type.class` khi muốn metadata của kiểu xác định từ mã nguồn.
- Dùng `getClass()` khi thật sự cần lớp cụ thể runtime và object khác null.
- Dùng `instanceof`/`Class.isInstance` khi muốn chấp nhận subclass hoặc interface implementation.
- Dùng `Class.isAssignableFrom` đúng chiều khi so quan hệ giữa hai kiểu.
- Không đánh đồng primitive Class và wrapper Class; biết array/interface có Class riêng.
- Không dựa trên `getSimpleName()` hoặc tên lambda/proxy tự sinh để định danh kiểu.
- Khi dùng `getClass()` trong `equals`, đã rà soát kế thừa, đối xứng, proxy và quan hệ nghiệp vụ.
- Khi dùng reflection/generics, hiểu `Class<?>` và giới hạn của type erasure.
- Đã chạy ví dụ `ClassLiteralDemo.java` và có thể giải thích từng dòng output.

**Điểm ghi nhớ:** `Type.class` mô tả **kiểu được nêu tường minh** trong chương trình, còn `obj.getClass()` mô tả **lớp thực tế của object tại runtime**. Phép so exact class khác phép kiểm tra tương thích theo kế thừa. Chọn đúng phép kiểm tra là một phần quan trọng của việc thiết kế Object Contract rõ ràng, an toàn và dễ duy trì.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="real-world-classes-005">So sánh chính xác lớp khác với kiểm tra khả năng là một kiểu con</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh chính xác lớp và kiểm tra khả năng là kiểu con trong Java

## 1. Hai câu hỏi khác nhau về kiểu đối tượng

Trong Java, **lớp thực tế** (*runtime class*) của một đối tượng là lớp của đối tượng được tạo tại thời điểm chạy, không nhất thiết là kiểu khai báo của biến đang tham chiếu đến nó. Phân biệt hai yêu cầu thường gặp:

1. **Chính xác lớp này?** Đối tượng có đúng lớp runtime `SomeClass`, không chấp nhận lớp con? Dùng `obj.getClass() == SomeClass.class` sau khi bảo đảm `obj != null`.
2. **Là một đối tượng thuộc kiểu này hoặc kiểu con?** Đối tượng có thể được coi là một `SomeClass` theo quan hệ kế thừa/triển khai interface? Dùng `obj instanceof SomeClass`.

Ví dụ:

```java
class Animal {}
class Dog extends Animal {}
class Poodle extends Dog {}

Animal pet = new Poodle();

System.out.println(pet instanceof Animal);       // true
System.out.println(pet instanceof Dog);          // true
System.out.println(pet.getClass() == Animal.class); // false
System.out.println(pet.getClass() == Poodle.class); // true
```

Đây là **đoạn minh họa các khai báo/lệnh**; chương trình có thể biên dịch/chạy độc lập được cung cấp ở mục 5. Biến `pet` được khai báo là `Animal` nhưng đối tượng thực tế là `Poodle`. Vì vậy cùng một tham chiếu có thể thỏa kiểm tra “là một `Animal`” trong khi không có lớp runtime đúng bằng `Animal.class`.

| Biểu thức | Câu hỏi | `new Poodle()` trong hệ `Animal → Dog → Poodle` |
| --- | --- | --- |
| `obj instanceof Animal` | Đối tượng có tương thích với `Animal`? | `true` |
| `obj instanceof Dog` | Đối tượng có tương thích với `Dog`? | `true` |
| `obj instanceof Poodle` | Đối tượng có tương thích với `Poodle`? | `true` |
| `obj.getClass() == Animal.class` | Lớp runtime có đúng `Animal`? | `false` |
| `obj.getClass() == Poodle.class` | Lớp runtime có đúng `Poodle`? | `true` |
| `Animal.class.isInstance(obj)` | Kiểm tra runtime tương tự `instanceof Animal`? | `true` |
| `Animal.class.isAssignableFrom(Poodle.class)` | Có thể gán đối tượng kiểu `Poodle` vào tham chiếu `Animal`? | `true` |

Đây là hai **chính sách về kiểu** khác nhau, không phải hai cú pháp tương đương có thể tùy ý thay thế.

## 2. `getClass() == SomeClass.class`: so sánh lớp chính xác

Mỗi đối tượng Java thừa hưởng phương thức `public final Class<?> getClass()` từ `Object`. Nó trả về một đối tượng `Class` biểu diễn **kiểu runtime**. Để kiểm tra chính xác lớp:

```java
if (obj != null && obj.getClass() == Animal.class) {
    // Chỉ đối tượng có lớp thực tế đúng Animal.
}
```

Đây là **đoạn lệnh nằm trong phương thức đã có biến `obj`**. Kiểm tra `obj != null` rất quan trọng: `obj.getClass()` ném `NullPointerException` khi `obj == null`. So với đó, `null instanceof Animal` luôn là `false` (với vế trái là tham chiếu null).

### Vì sao dùng `==` giữa các đối tượng `Class`?

Trong cùng một lớp được nạp, đối tượng `Class` đại diện cho kiểu runtime là duy nhất, nên so bằng `==` là thích hợp để kiểm tra **cùng một lớp runtime**. Không cần so tên lớp bằng chuỗi:

```java
obj.getClass().getName().equals("com.example.Animal")
```

Đoạn trên là **cách so tên mang tính minh họa**, không phải cách kiểm tra lớp chính xác được khuyến nghị. Hai lớp do hai class loader khác nhau nạp có thể có cùng tên nhị phân nhưng vẫn là hai kiểu runtime khác nhau. `getClass() == Animal.class` phản ánh đúng sự phân biệt này.

### Khi nào kiểm tra chính xác hữu ích?

- Trong định nghĩa bình đẳng theo **kiểu giá trị**, nếu một đối tượng của lớp con không được phép bằng đối tượng lớp cha dù các trường chung trùng nhau.
- Khi phân nhánh xử lý yêu cầu **đúng lớp hiện tại**, không chấp nhận bất kỳ subtype nào.
- Khi kiểm tra bất biến của một mô hình mà loại giá trị phải chính xác, ví dụ một số cách so sánh dữ liệu không có chính sách tương đương xuyên kiểu.

**Giới hạn:** kiểm tra bằng `getClass` loại trừ toàn bộ subclass, kể cả subclass không bổ sung trạng thái. Nếu hệ thống dùng proxy sinh lớp con ở runtime hoặc framework dựa trên subclass, kiểm tra kiểu chính xác có thể không phù hợp với yêu cầu nghiệp vụ. Không nên áp dụng cơ học chỉ vì thấy `instanceof` “quá rộng”.

## 3. `instanceof`: kiểm tra tương thích với kiểu đích

Toán tử `instanceof` trả về `true` nếu một tham chiếu không null trỏ tới đối tượng có thể chuyển đổi kiểm tra kiểu (*casting conversion*) thích hợp sang kiểu đích theo luật Java. Với lớp và interface thường gặp, điều đó bao gồm **đúng lớp** và **kiểu con**; với interface, nó bao gồm đối tượng thuộc lớp triển khai interface.

```java
Object value = "hello";
System.out.println(value instanceof String);       // true
System.out.println(value instanceof CharSequence); // true
System.out.println(value instanceof Object);       // true
```

Đây là **đoạn minh họa chạy trong phương thức**; `String` thực hiện `CharSequence` và cũng là `Object`. Cách kiểm tra này phù hợp khi mã cần sử dụng một **khả năng được biểu diễn bởi kiểu cha hoặc interface**, mà không cần biết lớp triển khai cụ thể.

Từ Java 16 trở đi, `instanceof` hỗ trợ **pattern matching**:

```java
if (value instanceof String text) {
    System.out.println(text.length());
}
```

Đây là **đoạn lệnh nằm trong phương thức đã có biến `value`**. Biến mẫu `text` chỉ khả dụng khi việc kiểm tra phù hợp với phạm vi đã được trình biên dịch xác định; Java thực hiện kiểm tra rồi cung cấp biến kiểu `String` không cần ép kiểu tường minh.

Tuy nhiên, **một kiểm tra `instanceof` không chứng minh tất cả đối tượng thuộc lớp cha có thể ép thành lớp con**. `obj instanceof Dog` đúng thì mới được dùng như `Dog`, còn việc `obj instanceof Animal` đúng không chứng minh nó là `Dog`.

### Liên hệ với `Class.isInstance` và `Class.isAssignableFrom`

Khi kiểu đích được biết trước ở thời điểm viết mã, `instanceof Animal` là cách trực tiếp nhất. Khi kiểu đích nằm trong một biến `Class<?>` (ví dụ được truyền từ cấu hình hoặc một API reflection), có thể dùng:

```java
Class<?> target = Animal.class;
boolean supported = target.isInstance(obj);
```

Đây là **đoạn lệnh trong phương thức**; `Class.isInstance(obj)` nhận một **đối tượng hoặc null** và trả về boolean. Nó không yêu cầu gọi phương thức trên `obj`, vì vậy `target.isInstance(null)` là `false` thay vì ném `NullPointerException`.

Với `Class.isAssignableFrom(otherClass)`, hai bên đều là **đối tượng `Class`**:

```java
boolean animalFromDog = Animal.class.isAssignableFrom(Dog.class); // true
boolean dogFromAnimal = Dog.class.isAssignableFrom(Animal.class); // false
```

Đây là **đoạn lệnh minh họa** dùng khai báo `Animal` và `Dog` ở trên. Chiều của phép kiểm tra rất quan trọng: `A.class.isAssignableFrom(B.class)` hỏi liệu một giá trị kiểu `B` có thể được gán vào nơi cần `A` hay không, không phải chiều ngược lại. Nó hữu ích trong reflection, generic container và framework, khi ta cần kiểm tra tương thích kiểu **trước khi có instance**.

## 4. Hệ quả đặc biệt với hợp đồng `equals()`

Trong module **object-contract**, khác biệt giữa `getClass` và `instanceof` đặc biệt quan trọng ở **`Object.equals(Object)`**. Một phương thức `equals` hợp lệ phải có tính phản xạ, đối xứng, bắc cầu, nhất quán và trả về `false` khi so với `null`.

### 4.1. Dùng `instanceof` có thể khiến lớp cha chấp nhận lớp con

Giả sử lớp `Point` có `x` và `y`, còn `ColoredPoint extends Point` thêm trường `color`. Nếu `Point.equals` dùng:

```java
if (!(other instanceof Point p)) return false;
return x == p.x && y == p.y;
```

Đây là **phần thân minh họa** của `equals` trong lớp `Point`. Phương thức của lớp cha chấp nhận cả `ColoredPoint` vì lớp con cũng là một `Point`. Nhưng nếu `ColoredPoint.equals` kiểm tra thêm màu và không chấp nhận `Point` thuần, có thể xảy ra:

```text
point.equals(coloredPoint)  -> true
coloredPoint.equals(point)  -> false
```

Khi hai phía trả khác nhau, **tính đối xứng bị phá vỡ**. Lỗi không do bản thân `instanceof` sai, mà do **hai lớp định nghĩa quan hệ bằng nhau không tương thích**. Chỉ đổi `instanceof` trong một phương thức mà không kiểm tra toàn hệ phân cấp chưa đủ bảo đảm hợp đồng.

### 4.2. Dùng `getClass` để thống nhất chính sách “chỉ cùng lớp”

Nếu đối tượng của hai lớp runtime khác nhau **không bao giờ nên bằng nhau**, `Point.equals` có thể kiểm tra:

```java
@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    Point that = (Point) other;
    return x == that.x && y == that.y;
}
```

Đây là **phương thức thuộc lớp `Point`**, giả sử các trường `x`, `y` đã khai báo; không phải chương trình độc lập. Khi một `Point` thuần so với một `ColoredPoint`, kiểm tra lớp runtime không khớp ở cả hai hướng và hai phương thức có thể cùng trả `false`.

Ở phía lớp con, cần xây dựng `equals` và `hashCode` tương thích với phần dữ liệu kế thừa lẫn phần dữ liệu mới. Ví dụ tại mục 5 thể hiện đầy đủ, có thể chạy được.

**Lưu ý quan trọng:** `getClass` trong lớp cha không tự khiến mọi override `equals` ở lớp con tuân thủ hợp đồng. Một lớp con có thể viết mã sai, hoặc dùng quan hệ bằng nhau không đối xứng trong phần triển khai của nó. `getClass` chỉ giúp thiết lập chính sách chính xác về kiểu khi các lớp đều tuân thủ.

### 4.3. Dùng `instanceof` vẫn có thể đúng khi mô hình phù hợp

Với một **lớp `final`** không có lớp con, kiểm tra `other instanceof FinalValue` và kiểm tra lớp chính xác thường dẫn đến cùng miền đối tượng hợp lệ. Một hệ phân cấp cũng có thể dùng `instanceof` nếu **mọi lớp liên quan chia sẻ một định nghĩa bình đẳng ổn định**, không có lớp con âm thầm bổ sung tiêu chí khiến quan hệ bị lệch.

Nếu thật sự cần một số lớp con được phép bằng lớp cha, có thể dùng thiết kế hợp tác như `canEqual` hoặc một mô hình danh tính chung; vẫn phải kiểm tra cả đối xứng, bắc cầu, `hashCode` và các giá trị biên. `getClass` không phải “nâng cấp” mặc định của `instanceof` trong mọi bài toán.

## 5. Chương trình Java 17+ đầy đủ: so kiểu và kiểm chứng `equals`

Lưu chương trình sau thành **`RuntimeClassDemo.java`**. Phần đầu minh họa kiểu runtime với ba lớp kế thừa. Phần sau dùng `getClass` trong `Point.equals` và triển khai `ColoredPoint` để kiểm tra bình đẳng giữa cha/con theo cả hai chiều.

```java
import java.util.Objects;

public class RuntimeClassDemo {
    static class Animal {}
    static class Dog extends Animal {}
    static final class Poodle extends Dog {}

    static class Point {
        private final int x, y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            Point that = (Point) other;
            return x == that.x && y == that.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class ColoredPoint extends Point {
        private final String color;

        ColoredPoint(int x, int y, String color) {
            super(x, y);
            this.color = Objects.requireNonNull(color);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!super.equals(other)) return false;
            ColoredPoint that = (ColoredPoint) other;
            return color.equals(that.color);
        }

        @Override
        public int hashCode() {
            return 31 * super.hashCode() + color.hashCode();
        }
    }

    public static void main(String[] args) {
        Object poodle = new Poodle();
        System.out.println("instanceof Animal = "
                + (poodle instanceof Animal));
        System.out.println("instanceof Dog = "
                + (poodle instanceof Dog));
        System.out.println("exact Animal class = "
                + (poodle.getClass() == Animal.class));
        System.out.println("exact Poodle class = "
                + (poodle.getClass() == Poodle.class));
        System.out.println("Animal isInstance = "
                + Animal.class.isInstance(poodle));
        System.out.println("Animal assignable from Poodle = "
                + Animal.class.isAssignableFrom(Poodle.class));
        System.out.println("Poodle assignable from Animal = "
                + Poodle.class.isAssignableFrom(Animal.class));
        Object missing = null;
        System.out.println("null instanceof Animal = "
                + (missing instanceof Animal));

        Point base = new Point(2, 3);
        ColoredPoint red = new ColoredPoint(2, 3, "red");
        ColoredPoint copy = new ColoredPoint(2, 3, "red");
        System.out.println("base.equals(red) = " + base.equals(red));
        System.out.println("red.equals(base) = " + red.equals(base));
        System.out.println("red.equals(copy) = " + red.equals(copy));
        System.out.println("equal colored hash = "
                + (red.hashCode() == copy.hashCode()));
    }
}
```

Chạy trong thư mục chứa tệp bằng JDK 17 trở lên:

```bash
javac RuntimeClassDemo.java
java RuntimeClassDemo
```

Kết quả:

```text
instanceof Animal = true
instanceof Dog = true
exact Animal class = false
exact Poodle class = true
Animal isInstance = true
Animal assignable from Poodle = true
Poodle assignable from Animal = false
null instanceof Animal = false
base.equals(red) = false
red.equals(base) = false
red.equals(copy) = true
equal colored hash = true
```

**Giải thích đầu ra:**

1. Đối tượng `Poodle` cũng là `Dog` và `Animal`, nên hai kiểm tra `instanceof` đầu tiên đều đúng. Nhưng lớp runtime của đối tượng là **`Poodle`**, không phải `Animal`.
2. `Animal.class.isInstance(poodle)` cũng `true`: đây là cách kiểm tra ở runtime khi thông tin kiểu nằm trong một đối tượng `Class`. `isAssignableFrom` cho biết hướng kế thừa `Poodle → Animal` là hướng gán hợp lệ; hướng `Animal → Poodle` thì không.
3. Một tham chiếu `null` cho kết quả `false` khi dùng `instanceof`. Nếu dùng `missing.getClass()` mà không kiểm tra null sẽ ném `NullPointerException`.
4. `base.equals(red)` là `false` vì lớp runtime `Point` khác `ColoredPoint`. Chiều ngược lại cũng `false`, vì `super.equals(other)` phát hiện lớp khác.
5. Hai `ColoredPoint` độc lập nhưng có cùng tọa độ và màu trả `true`. Cả hai tạo hash theo phần điểm kế thừa và màu nên các đối tượng bằng nhau cũng có cùng hash.

Không nên suy rộng rằng bất kỳ hai đối tượng cùng lớp nào cũng bằng nhau. `getClass` chỉ giải quyết câu hỏi **kiểu có tương thích theo chính sách đang chọn không**, còn nội dung thuộc tính vẫn phải so đúng trong `equals`.

## 6. Khi nào nên dùng loại kiểm tra nào?

| Tình huống | Lựa chọn phù hợp | Lý do |
| --- | --- | --- |
| Xử lý mọi đối tượng triển khai một interface | `instanceof InterfaceType` | Chấp nhận các implementation khác nhau |
| Nhận một đối tượng thuộc lớp cơ sở hoặc bất kỳ lớp con | `instanceof BaseType` | Hỗ trợ đa hình |
| Chỉ xử lý đúng lớp runtime `FinalMessage` hoặc `ExactEvent` | `getClass() == ExactEvent.class` | Không nhận lớp con |
| Lớp giá trị muốn `equals` chỉ có thể `true` giữa cùng lớp runtime | `getClass()` trong `equals` cùng kiểm tra thuộc tính | Xác lập chính sách bình đẳng chính xác về kiểu |
| Kiểu được chọn động bằng `Class<?> target` | `target.isInstance(obj)` | Kiểm tra tương thích với instance khi kiểu là dữ liệu runtime |
| Cần xác định liệu hai kiểu có thể gán theo một hướng | `Target.class.isAssignableFrom(Source.class)` | Là câu hỏi về **kiểu**, không cần instance |
| Cần ép kiểu an toàn và khai báo biến mới | `obj instanceof Target t` | Vừa kiểm tra vừa ràng buộc biến pattern trong phạm vi phù hợp |
| Đối tượng do framework tạo proxy kế thừa runtime | Cần thiết kế chính sách domain trước | `getClass` sẽ thấy lớp proxy thay vì luôn thấy lớp mô hình gốc |

### Hai điểm dễ sai với reflection

- `A.class.isAssignableFrom(B.class)` hướng từ **kiểu đích** `A` tới **kiểu nguồn** `B`. Viết đảo sẽ trả kết quả khác; trong quan hệ cha/con, chỉ một hướng có thể gán an toàn.
- `Class.isInstance` không phải `getClass()==...`. `Animal.class.isInstance(new Poodle())` là `true` dù hai đối tượng `Class` `Animal.class` và `Poodle.class` không đồng nhất.

Một biến kiểu `Object` có thể trỏ tới đối tượng mang kiểu runtime bất kỳ. Vì vậy muốn biết đối tượng **hỗ trợ hành vi** nào, thường dùng interface và `instanceof`/đa hình; muốn ràng buộc chính xác **danh tính lớp runtime**, dùng `getClass` hoặc so đối tượng `Class`.

## 7. Trường hợp biên và giới hạn của hai phương pháp

### 7.1. `null`

`obj instanceof SomeType` là `false` nếu `obj == null`. `SomeType.class.isInstance(null)` cũng trả `false`. Trong khi đó `obj.getClass()` ném `NullPointerException`; phép kiểm tra lớp chính xác cần đi sau điều kiện null. Khi viết `equals`, thường bắt đầu bằng:

```java
if (this == other) return true;
if (other == null || getClass() != other.getClass()) return false;
```

Đây là **đoạn logic trong phương thức `equals`**, không phải chương trình độc lập. Nó vừa xử lý so cùng tham chiếu, vừa tránh gọi `getClass` trên đối tượng null.

### 7.2. Interface, abstract class và kiểu đa hình

Một đối tượng triển khai interface `Runnable` có thể thỏa `obj instanceof Runnable`. Nhưng `obj.getClass() == Runnable.class` luôn sai đối với một instance thông thường, vì interface không phải một lớp có thể tạo đối tượng trực tiếp. Tương tự, một instance lớp con có thể thỏa `obj instanceof AbstractBase`, trong khi lớp runtime không thể bằng đúng lớp abstract không thể khởi tạo.

Điều này làm rõ khác biệt: **“thuộc một kiểu” không có nghĩa “lớp runtime trùng chính xác kiểu ấy”**.

### 7.3. Mảng

Mảng `String[]` thỏa `instanceof Object[]` vì các phần tử đều là tham chiếu, nhưng lớp runtime của nó không bằng `Object[].class`; nó bằng `String[].class`. Mảng `int[]` lại **không** là `Object[]` vì phần tử là kiểu nguyên thủy; tuy vậy chính mảng `int[]` vẫn là một `Object`. Các loại mảng khác nhau có lớp runtime khác nhau, và có thể phân biệt bằng `getClass` khi thực sự cần chính xác kiểu mảng.

### 7.4. Proxy và lớp sinh động

Một số framework tạo proxy bằng cách tạo subclass của lớp miền. Khi đó `getClass() == DomainType.class` có thể là `false` dù proxy được thiết kế để đóng vai một `DomainType` trong ứng dụng. Ngược lại, `proxy instanceof DomainType` có thể là `true` trong trường hợp proxy kế thừa `DomainType`.

**Không phải proxy nào cũng là subclass của lớp miền**: proxy dựa trên interface có quan hệ kiểu theo interface thay vì chắc chắn kế thừa lớp cụ thể. Các ORM và framework còn có chính sách riêng về identity, lazy loading và `equals`. Hãy xác định quy tắc identity ở mức miền hoặc theo hướng dẫn framework thay vì tự động chọn `getClass` hay `instanceof` từ một ví dụ đơn giản.

### 7.5. Lớp con mới có thể phá hợp đồng `equals`

Ngay cả khi lớp cha dùng `getClass` đúng, một lớp con triển khai `equals` không đối xứng hoặc quên `hashCode` vẫn có thể làm hỏng hợp đồng. Trong mô hình giá trị, dùng `final class` hoặc `record` (mặc định final) thường giảm rủi ro do subclass thay đổi tiêu chí bình đẳng. Nếu cần kế thừa, phải kiểm tra toàn bộ quan hệ giữa các lớp thực tế có thể xuất hiện.

## 8. Lỗi phổ biến và cách tự kiểm tra

| Lỗi | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Tưởng `obj instanceof Base` chỉ nhận đúng `Base` | Không tính đến subtype | Dùng `getClass() == Base.class` khi muốn lớp runtime chính xác |
| Tưởng `obj.getClass() == Base.class` chấp nhận lớp con | `Class` bình đẳng chính xác, không theo kế thừa | Dùng `instanceof Base` hoặc `Base.class.isInstance(obj)` |
| Gọi `getClass()` trên null | Dereference null | Kiểm tra `obj != null` trước |
| Đảo chiều `isAssignableFrom` | Nhầm lớp nguồn và lớp đích | Nhớ `Target.class.isAssignableFrom(Source.class)` |
| Lớp cha dùng `instanceof` nhưng lớp con thêm tiêu chí bình đẳng riêng | `equals` có thể không đối xứng hoặc không bắc cầu | Thiết kế thống nhất quan hệ equality xuyên phân cấp |
| Thay `instanceof` bằng `getClass` khắp nơi không xét domain | Loại trừ mọi subclass/proxy | Xác định cần tương thích kiểu hay cần lớp chính xác |
| So tên lớp bằng chuỗi thay vì đối tượng `Class` | Class loader có thể tạo kiểu khác cùng tên | So `Class` trực tiếp nếu cần kiểm tra type identity |
| Kiểm tra `instanceof` rồi vẫn ép kiểu sai | Kiểm tra một kiểu nhưng ép sang một kiểu con khác | Dùng pattern variable đúng kiểu đã kiểm tra |
| Chỉ kiểm tra kiểu trong `equals`, quên giá trị và `hashCode` | Hợp đồng bình đẳng không hoàn chỉnh | So các thuộc tính quyết định equality, sinh hash tương thích |

### Câu hỏi tự kiểm tra

**1.** `Animal pet = new Poodle();`. `pet.getClass() == Animal.class` có đúng không? Còn `pet instanceof Animal`?

Kết quả lần lượt là `false` và `true`. Kiểu khai báo của biến không thay đổi lớp runtime của đối tượng.

**2.** `Class<?> target = Animal.class`. Dùng `target.isInstance(new Poodle())` có khác ý nghĩa với `new Poodle().getClass() == Animal.class` không?

Có. Biểu thức đầu kiểm tra quan hệ tương thích kiểu và cho `true`; biểu thức sau kiểm tra chính xác lớp runtime và cho `false`.

**3.** Trong hệ `Point`, `ColoredPoint` có thêm màu quyết định identity. Nếu lớp cha dùng `instanceof Point` để coi lớp con bằng mình theo tọa độ, còn lớp con từ chối lớp cha, tính chất nào bị vi phạm?

Tính **đối xứng** của `equals`. Hai chiều cho hai kết quả khác nhau. Một giải pháp là chính sách lớp runtime chính xác ở cả hai phía, miễn là nó phù hợp miền nghiệp vụ.

**4.** `Runnable.class.isAssignableFrom(MyTask.class)` đúng có nghĩa là gì?

Nếu `MyTask` triển khai `Runnable`, thì một đối tượng `MyTask` có thể được dùng ở nơi cần `Runnable`. Nó không khẳng định lớp runtime của đối tượng bằng chính xác `Runnable.class`.

**Ghi nhớ:** `instanceof` trả lời “đối tượng **có thể được coi là** kiểu này không?”, còn `getClass() == SomeType.class` trả lời “đối tượng có **chính xác lớp runtime** này không?”. Với `equals` và các hợp đồng đối tượng, lựa chọn phụ thuộc cách ứng dụng định nghĩa bình đẳng xuyên hệ phân cấp; sau kiểm tra kiểu vẫn phải so giá trị và bảo đảm `hashCode` tương thích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="real-world-classes-006">Cùng tên lớp không bảo đảm cùng lớp khi có class loader khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Cùng tên lớp không bảo đảm cùng lớp khi có class loader khác nhau

## 1. Vấn đề: tên giống hệt nhưng JVM coi là hai kiểu khác nhau

Trong Java, hai đối tượng có thể in ra **cùng tên lớp đầy đủ** nhưng không thuộc **cùng một lớp lúc chạy**. Điều này xảy ra khi bytecode mang cùng tên nhị phân được **định nghĩa bởi hai class loader khác nhau**.

Ví dụ, một plugin và ứng dụng chủ cùng có tệp `com.example.model.Customer.class`. Nếu mỗi bên định nghĩa một bản sao riêng, hai đối tượng đều có thể báo:

```text
com.example.model.Customer
```

qua `getClass().getName()`. Tuy nhiên:

```java
left.getClass().getName().equals(right.getClass().getName()) // true
left.getClass() == right.getClass()                          // false
```

Đây là **fragment minh họa**; cần có hai đối tượng `left` và `right` tương ứng. Lý do là **tên dạng `String` không phải định danh duy nhất của lớp**.

Quy tắc cốt lõi dành cho kiểu tham chiếu thông thường trong JVM là: một lớp được nhận diện bằng **tên nhị phân (binary name)** kết hợp với **class loader định nghĩa (defining class loader)**. Có cùng tên nhưng có **defining loader khác nhau** thì là hai kiểu runtime khác nhau.

Điều này tác động trực tiếp đến `getClass()`, `instanceof`, ép kiểu (cast), gọi phương thức phản chiếu (reflection), overload nhận kiểu cụ thể, `equals()` và nhiều collection/API phụ thuộc kiểu đối tượng.

## 2. Phân biệt tên lớp, đối tượng `Class` và class loader

### Tên nhị phân và đối tượng `Class<?>`

`Class.getName()` trả một chuỗi mô tả tên nhị phân của class thông thường, ví dụ `java.lang.String` hoặc `com.example.Customer`. Với nested class, tên nhị phân có dấu `$`, chẳng hạn `Example$Value`.

`Class<?>` không chỉ là chuỗi tên. Nó là đại diện runtime cho một kiểu cụ thể đã được JVM nạp/định nghĩa. Hai class có cùng `getName()` vẫn có thể được biểu diễn bởi **hai đối tượng `Class` riêng biệt**.

```java
Class<?> typeA = objectA.getClass();
Class<?> typeB = objectB.getClass();

boolean sameText = typeA.getName().equals(typeB.getName());
boolean sameRuntimeType = typeA == typeB;
```

Các biến `objectA`/`objectB` là giả định của fragment này. Khi chỉ cần biết hai đối tượng có **chính xác cùng kiểu runtime**, so sánh `getClass()` bằng `==` (hoặc `Class.equals`) là đúng. Không dùng `getName().equals(...)` để thay cho kiểm tra kiểu.

### Class loader định nghĩa là gì?

Một `ClassLoader` có thể định vị bytecode và dùng `defineClass(...)` để tạo class trong JVM. **Loader nào thực sự định nghĩa class** là một phần bản sắc kiểu. Một loader khác có thể **yêu cầu nạp** class rồi nhận về chính class do loader cha định nghĩa; trong trường hợp đó, loader yêu cầu không phải defining loader.

```java
ClassLoader definingLoader = typeA.getClassLoader();
```

Với class do bootstrap class loader định nghĩa, `Class.getClassLoader()` thường trả `null`; `null` ở đây không có nghĩa class không được nạp.

JVM cũng có thể có nhiều loader cùng loại/class Java nhưng là **những instance khác nhau**. Hai instance ấy có thể định nghĩa hai bản sao runtime khác nhau của một tên class.

> Quy tắc cần nhớ: **tên đầy đủ + defining loader**, không phải chỉ tên file `.class`, tên package, đường dẫn JAR hoặc tên module.

## 3. Vì sao class loader khác nhau có thể nạp cùng tên?

Java thường dùng mô hình **ủy quyền cho loader cha (parent delegation)**: loader được yêu cầu trước tiên thử thông qua cha; nếu cha đã nạp/định nghĩa class thích hợp, các loader con có thể nhận về cùng một đối tượng `Class`. Vì thế **hai loader khác nhau không tự động đồng nghĩa hai class khác nhau**.

Các hệ thống plugin, máy chủ ứng dụng, container, IDE, công cụ reload hoặc môi trường cô lập có thể có chiến lược nạp tùy chỉnh. Một loader **child-first cho một tập tên được chọn** có thể định nghĩa bản sao riêng thay vì lấy bản đã tồn tại ở cha.

| Tình huống | Kết quả có thể xảy ra |
| --- | --- |
| Hai loader con đều ủy quyền cho cùng loader cha đã định nghĩa `SharedType` | Nhận **cùng** đối tượng `Class`, không có xung đột bản sắc kiểu |
| Hai loader khác nhau mỗi bên tự `defineClass` cùng tên `PluginType` | Có **hai** đối tượng `Class` khác nhau |
| Một loader đã định nghĩa một tên, lại cố định nghĩa cùng tên trong chính loader đó | Không được tạo hai class độc lập có cùng tên ở **cùng defining loader**; có thể phát sinh `LinkageError` |
| Hai bản JAR chứa tên class giống nhau | Chưa kết luận được gì nếu không biết class thực sự được loader nào định nghĩa |

Do đó không nên suy luận bản sắc kiểu chỉ từ việc nhìn thấy hai JAR hay hai `ClassLoader`. Phải quan sát **`Class<?>` thực tế của object**, cùng `getClassLoader()`, trong ngữ cảnh runtime.

## 4. Java 17: minh họa hai loader định nghĩa cùng một bytecode

Ví dụ này cố ý tạo **hai instance loader tùy chỉnh**, mỗi loader định nghĩa riêng nested class `ClassLoaderIdentityDemo$Value` từ cùng bytecode `.class`. Ngoài class `Value`, các tên khác vẫn được ủy quyền cho loader cha. Vì vậy hai `Value` là hai kiểu khác nhau, trong khi cả hai có thể triển khai **một interface dùng chung** `CodeView` do loader cha định nghĩa.

Tạo tệp duy nhất `ClassLoaderIdentityDemo.java` với nội dung sau:

```java
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class ClassLoaderIdentityDemo {

    public interface CodeView {
        String code();
    }

    public static class Value implements CodeView {
        private final String code;

        public Value(String code) {
            this.code = Objects.requireNonNull(code);
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            Value that = (Value) other;
            return code.equals(that.code);
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }
    }

    static class IsolatedValueLoader extends ClassLoader {
        private final String targetName;
        private final byte[] targetBytes;

        IsolatedValueLoader(ClassLoader parent, String targetName,
                            byte[] targetBytes) {
            super(parent);
            this.targetName = targetName;
            this.targetBytes = targetBytes.clone();
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.equals(targetName)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = defineClass(
                            name, targetBytes, 0, targetBytes.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String binaryName = Value.class.getName();
        String resourceName = "/" + binaryName.replace('.', '/')
                + ".class";

        byte[] bytes;
        try (InputStream stream =
                ClassLoaderIdentityDemo.class.getResourceAsStream(
                        resourceName)) {
            if (stream == null) {
                throw new IOException("Missing class resource: "
                        + resourceName);
            }
            bytes = stream.readAllBytes();
        }

        ClassLoader parent =
                ClassLoaderIdentityDemo.class.getClassLoader();
        ClassLoader loaderA =
                new IsolatedValueLoader(parent, binaryName, bytes);
        ClassLoader loaderB =
                new IsolatedValueLoader(parent, binaryName, bytes);

        Class<?> typeA = Class.forName(binaryName, true, loaderA);
        Class<?> typeB = Class.forName(binaryName, true, loaderB);
        Object a = typeA.getConstructor(String.class).newInstance("X");
        Object aAgain = typeA.getConstructor(String.class)
                .newInstance("X");
        Object b = typeB.getConstructor(String.class).newInstance("X");
        Value host = new Value("X");

        System.out.println("same binary name: "
                + typeA.getName().equals(typeB.getName()));
        System.out.println("same Class object: " + (typeA == typeB));
        System.out.println("same defining loader: "
                + (typeA.getClassLoader() == typeB.getClassLoader()));
        System.out.println("loader A owns type A: "
                + (typeA.getClassLoader() == loaderA));

        System.out.println("same-loader objects equal: "
                + a.equals(aAgain));
        System.out.println("cross-loader objects equal: "
                + a.equals(b));
        System.out.println("same hash allowed: "
                + (a.hashCode() == b.hashCode()));
        System.out.println("a instanceof host Value: "
                + (a instanceof Value));
        System.out.println("a implements shared CodeView: "
                + (a instanceof CodeView));
        System.out.println("shared interface call: "
                + ((CodeView) a).code());
        System.out.println("host equals a: " + host.equals(a));

        try {
            Value impossible = (Value) a;
            System.out.println(impossible.code());
        } catch (ClassCastException e) {
            System.out.println("host Value cast failed: true");
        }
    }
}
```

Biên dịch và chạy trong cùng thư mục bằng JDK 17 trở lên:

```bash
javac ClassLoaderIdentityDemo.java
java ClassLoaderIdentityDemo
```

Lưu ý: cần giữ các tệp `ClassLoaderIdentityDemo.class`, `ClassLoaderIdentityDemo$Value.class` và `ClassLoaderIdentityDemo$CodeView.class` mà `javac` tạo trong classpath lúc chạy. Chương trình đọc bytes của nested `Value` qua class resource, **không** phụ thuộc tên tuyệt đối của thư mục trên máy.

Đầu ra:

```text
same binary name: true
same Class object: false
same defining loader: false
loader A owns type A: true
same-loader objects equal: true
cross-loader objects equal: false
same hash allowed: true
a instanceof host Value: false
a implements shared CodeView: true
shared interface call: X
host equals a: false
host Value cast failed: true
```

### Điều gì diễn ra?

1. `Class.forName(binaryName, true, loaderA)` yêu cầu nạp tên thông qua `loaderA`. Vì loader tự xử lý **duy nhất** `Value`, nó gọi `defineClass` cho tên đó. `loaderB` thực hiện tương tự, tạo một định nghĩa khác.
2. Cả `typeA.getName()` và `typeB.getName()` có cùng chuỗi tên `ClassLoaderIdentityDemo$Value`, nên kết quả “same binary name” là `true`.
3. `typeA == typeB` là `false` và defining loader của chúng cũng khác nhau. Dù bytes y hệt, class trong JVM vẫn khác vì defining loader khác.
4. `a` và `aAgain` đều được tạo từ `typeA`. `Value.equals` thấy cùng class và cùng `code = "X"`, nên trả `true`.
5. `a.equals(b)` trả `false`: đối tượng `b` thuộc định nghĩa do `loaderB` tạo. Điều kiện `getClass() != other.getClass()` chặn việc cast sang kiểu `Value` của `a`.
6. `a.hashCode() == b.hashCode()` là `true` vì cả hai cùng hash chuỗi `"X"`; điều này **không chứng minh hai đối tượng bằng nhau**. Hợp đồng hash chỉ bắt buộc chiều `equals() == true => hashCode()` giống nhau.
7. `a instanceof Value` là `false` khi `Value` ở bên phải được biên dịch tham chiếu đến bản của **loader ứng dụng**. Đây là kiểm tra kiểu runtime, không phải so tên chuỗi.
8. `a instanceof CodeView` lại là `true` vì hai bản `Value` đều tham chiếu một `CodeView` được cha định nghĩa. Chuyển qua giao diện chia sẻ được và gọi `code()` trả `"X"`.
9. Ép `a` sang `Value` của ứng dụng ném `ClassCastException`. Chương trình in một dòng cố định trong `catch` thay vì in toàn bộ message ngoại lệ vì cách trình bày loader trong exception có thể phụ thuộc phiên bản JVM.

**Mục tiêu duy nhất của loader tùy chỉnh là minh họa bản sắc kiểu.** Đây không phải một class loader plugin hoàn chỉnh: production còn phải quản lý phạm vi tên, resource, bảo mật, giải phóng tham chiếu, caching và nhiều vấn đề tương thích khác. Không nên thay parent delegation bừa bãi cho mọi lớp.

## 5. Vì sao `getClass()` và `instanceof` phản ứng khác so với so tên?

### `getClass()` cho thấy đúng kiểu runtime

`getClass()` trả chính đối tượng `Class` đại diện cho kiểu thực tế của instance. Khi kiểm tra bằng:

```java
if (other == null || getClass() != other.getClass()) {
    return false;
}
```

một implementation `equals` **theo lớp chính xác** sẽ từ chối đối tượng do loader khác định nghĩa, ngay cả khi trường dữ liệu và tên lớp giống nhau.

Điều đó phù hợp với nhiều **value class không hỗ trợ quan hệ bằng nhau xuyên lớp**. Song `getClass()` không phải điều kiện bắt buộc cho mọi `equals`: những kiểu theo hợp đồng giao diện như `List`/`Set`/`Map` có thể định nghĩa bằng nhau xuyên lớp triển khai. Chọn chiến lược dựa trên **bản sắc logic**, không dùng một công thức chung cho toàn bộ Java.

### `instanceof` dùng quan hệ kiểu, không dùng tên

`obj instanceof SomeType` chỉ đúng khi kiểu runtime của `obj` là `SomeType` hoặc kiểu con/triển khai tương thích của **chính đối tượng Class tương ứng với `SomeType`**. Có cùng `getName()` không tạo quan hệ kế thừa.

```java
boolean exactType = obj.getClass() == SomeType.class;
boolean compatible = obj instanceof SomeType;
```

Fragment giả sử `obj` khác null và có kiểu `SomeType` nằm trong phạm vi biên dịch. Phép kiểm tra thứ nhất đòi **đúng lớp runtime**; thứ hai cho phép subclass/implementation hợp lệ. Cả hai không so khớp bằng tên text.

Trong ví dụ, `Value` của `loaderA` **không phải** subclass của `Value` của loader ứng dụng, nên `instanceof Value` thất bại. Nhưng cả hai có thể triển khai `CodeView` được nạp dùng chung, do đó `instanceof CodeView` thành công.

## 6. `ClassCastException` gây nhầm lẫn: “không cast được X sang X”?

Đây là triệu chứng thường gặp trong các môi trường plugin hoặc application server. Một thông báo lỗi có thể trông như:

```text
class com.example.Customer cannot be cast to class com.example.Customer
```

Phần chi tiết bổ sung của JVM có thể đề cập module và loader thực tế. Điều quan trọng là **hai tên hiển thị giống nhau không nhất thiết mô tả cùng kiểu runtime**.

Khi gặp sự cố này, đừng chỉ so `getName()`. Hãy thu thập theo từng object:

```java
Class<?> c = value.getClass();
System.out.println("type=" + c.getName());
System.out.println("loader=" + c.getClassLoader());
System.out.println("module=" + c.getModule());
```

Đây là fragment kiểm tra, giả định `value` khác null. Cần thận trọng khi đưa thông tin này vào log production (không lộ classpath, cấu trúc triển khai hoặc dữ liệu nhạy cảm qua thông báo công khai).

**Mẹo điều tra:** so sánh chính hai đối tượng `Class`, dùng `getClassLoader()` để quan sát defining loader, rồi xác định đường nạp đã chọn. Chuỗi `toString()` của loader là thông tin phụ trợ; không dùng nó thay bằng chứng danh tính object loader.

## 7. Có bắt buộc cùng loader mới giao tiếp được không?

Không. Hai plugin có class nội bộ riêng vẫn có thể phối hợp qua **API dùng chung** được định nghĩa bởi cùng loader cha. Cách minh họa ở phần 4 là `CodeView`:

- `CodeView` được load/định nghĩa bởi lớp cha dùng chung.
- `Value` của `loaderA` và `Value` của `loaderB` đều implements đúng **cùng `CodeView` runtime**.
- Có thể nhận chúng dưới kiểu `CodeView` và gọi `code()` mà không cần cast sang `Value` của plugin khác.

Một thiết kế hệ thống thường đặt interface API, DTO dùng chung hoặc kiểu thông điệp ở tầng classpath/module cha mà các plugin cùng nhìn thấy. Các kiểu implementation riêng của plugin được giữ nội bộ và không rò rỉ qua ranh giới. Khi muốn truyền dữ liệu mà không chia sẻ lớp implementation, có thể dùng interface ổn định, primitive/`String`, hoặc một định dạng serialize có schema rõ ràng.

**Lưu ý:** bản thân việc hai interface có cùng tên chưa đủ. Nếu mỗi loader cũng định nghĩa riêng `com.example.Api`, chúng lại là **hai interface runtime khác nhau**, và phép cast sang interface ở phía kia vẫn thất bại. Phải thật sự chia sẻ **cùng định nghĩa runtime của API**, không chỉ sao chép mã nguồn/JAR.

## 8. Class loader, `equals()` và `hashCode()`

Có hai thiết kế cần phân biệt:

### Bằng nhau theo kiểu thực tế

Một value class có thể yêu cầu `getClass() == other.getClass()` rồi so các trường. Như ví dụ, các instance cùng tên ở loader khác nhau không được xem bằng nhau; điều này tránh ép kiểu sai và phù hợp bản sắc kiểu.

### Bằng nhau theo một giao diện chia sẻ

Một API có thể định nghĩa quan hệ bằng nhau xuyên implementation, nhưng phải đặc biệt thận trọng: cả hai phía phải cùng tuân thủ **đối xứng, bắc cầu, nhất quán và `hashCode()`**.

Ví dụ, nếu một bên so bằng `getClass()` và bên kia so chỉ theo `CodeView.code()`, có thể xảy ra:

```text
a.equals(b) = false
b.equals(a) = true
```

Đó là vi phạm **tính đối xứng** của `equals`. Nếu cần so dữ liệu logic giữa các plugin, một cách an toàn thường là trích ra **khóa bất biến chung** hoặc so các giá trị từ API chung bằng phương thức so sánh riêng, thay vì yêu cầu hai class implementation viết `equals` tương thích xuyên loader.

Không viết kiểu:

```java
// SAI VỀ KIỂM TRA BẢN SẮC KIỂU:
if (other.getClass().getName().equals(getClass().getName())) {
    // Ép kiểu sang class hiện tại KHÔNG an toàn.
}
```

Đây là **fragment cố ý minh họa lỗi**; nó còn cần kiểm tra null. Kết quả trùng tên không chứng minh việc cast được, không chứng minh cấu trúc trường giống nhau và không cho phép coi object là cùng kiểu.

Về `hashCode`, hai đối tượng khác lớp loader có thể có cùng hash dù `equals` khác nhau. Không có yêu cầu “class khác thì hash bắt buộc khác”. Nếu hai đối tượng **được phép bằng nhau theo contract**, lúc đó mới bắt buộc hash bằng nhau.

## 9. Phân biệt class loader với package, JAR và Java module

Không nên nhầm các khái niệm:

| Khái niệm | Ý nghĩa | Có đủ để xác định `Class` runtime không? |
| --- | --- | --- |
| Tên lớp/package | Chuỗi định danh trong bytecode | **Không**, còn defining loader |
| Tên tệp/JAR | Nơi chứa resource hoặc bytecode | **Không**, một JAR có thể được dùng bởi nhiều loader |
| Class loader | Đối tượng tham gia nạp/định nghĩa class | Kết hợp với tên class để xác định kiểu runtime thông thường |
| Java Platform Module System (JPMS) | Mô tả module, khả năng đọc, export/open package | Có ảnh hưởng đến khả năng truy cập/lookup, nhưng không thay thế việc xem danh tính `Class` và defining loader |

Trong các ứng dụng module, module liên quan đến class có thể được quan sát bằng `Class.getModule()`. Không suy luận rằng cùng tên module hoặc cùng tên package đảm bảo cùng `Class`. Và ngược lại, việc thấy các module khác nhau cũng chưa phải lời giải đầy đủ cho một `ClassCastException` mà chưa kiểm tra loader/type thực tế.

### `Class.forName` không luôn tạo lớp mới

`Class.forName(name)` thường dùng loader liên quan đến caller. `Class.forName(name, initialize, loader)` cho phép chỉ định loader yêu cầu nạp. Nhưng loader được chỉ định **có thể ủy quyền**, khiến ta nhận lại class do cha định nghĩa; nó không tự động tạo một bản sao thứ hai.

Ví dụ trên chỉ tạo hai bản vì `IsolatedValueLoader.loadClass` cố ý **tự gọi `defineClass` cho tên mục tiêu**. Với các tên khác, loader vẫn dùng `super.loadClass` và cơ chế ủy quyền thông thường.

## 10. Hệ quả ở reflection, collection và plugin architecture

### Reflection có cần đúng đối tượng `Class`?

Có. Khi tìm constructor/method với tham số kiểu cụ thể, Java reflection làm việc với **kiểu runtime**, không chỉ chuỗi tên. Nếu API yêu cầu `com.example.Customer` do loader cha định nghĩa mà caller cung cấp một `Customer` trùng tên do loader plugin riêng định nghĩa, việc cast hoặc tìm method với kiểu parameter tương ứng có thể thất bại.

Các thông báo như `NoSuchMethodException`, `IllegalArgumentException` khi truyền argument phản chiếu, hoặc `ClassCastException` có thể là triệu chứng của **không khớp kiểu runtime**. Không được giải quyết bằng cách so tên lớp rồi ép cast.

### Collection có tự hợp nhất hai class cùng tên không?

Không. `HashSet` và `HashMap` dùng `equals()`/`hashCode()` của phần tử hoặc khóa; nếu những phương thức ấy phân biệt class loader, hai giá trị cùng tên/cùng dữ liệu có thể là hai khóa khác nhau.

Nếu muốn dùng khóa xuyên ranh giới class loader, hãy dùng kiểu khóa từ API chung (ví dụ `String`, `UUID` hoặc một immutable DTO được định nghĩa chung). Bảo đảm `equals` và `hashCode` của kiểu khóa thống nhất, thay vì dựa trên tên class implementation.

### Class loader và khả năng giải phóng plugin

Các tham chiếu sống đến đối tượng, `Class<?>`, loader, thread context class loader, static cache hoặc registry có thể giữ class loader và các class của nó còn reachable, làm hệ thống không giải phóng được các phiên bản plugin cũ. Khi thiết kế hot reload/unload, cần quản lý lifecycle và loại bỏ tham chiếu giữ loader. Đây là lưu ý vận hành bổ sung, không phải tiêu chí của tính bằng nhau.

## 11. Quy trình chẩn đoán “cùng tên mà cast thất bại”

1. **Lấy đúng hai object/type:** đối tượng thực tế và kiểu đích của phép cast hoặc tham số method.
2. **In `getName()`:** xác nhận tên nhị phân có giống nhau không.
3. **So đối tượng `Class` trực tiếp:** `actualType == expectedType` và, nếu có kế thừa, `expectedType.isAssignableFrom(actualType)`.
4. **Xem `getClassLoader()` và `getModule()`:** ghi nhận defining loader, module và bối cảnh classpath khi cần.
5. **Kiểm tra đường nạp:** parent delegation hay loader riêng, có bản JAR trùng hoặc plugin đóng gói cả shared API không.
6. **Kiểm tra ranh giới API:** kiểu implementation có bị truyền qua một interface/DTO được nạp riêng hai lần không.
7. **Khắc phục ở kiến trúc nạp lớp:** chia sẻ đúng API ở loader chung, tránh đóng gói bản sao shared class trong từng plugin, hoặc chuyển dữ liệu sang giá trị trung gian có schema rõ ràng.
8. **Kiểm tra lại cả cast lẫn `equals`/`hashCode`:** nếu đổi chính sách nạp hoặc kiểu khóa, collection, reflection và kiểm thử tích hợp phải cùng thống nhất.

Fragment chẩn đoán kiểu tương thích:

```java
Class<?> actual = value.getClass();
Class<?> required = SomeApiType.class;

System.out.println(actual.getName());
System.out.println(required.getName());
System.out.println(actual == required);
System.out.println(required.isAssignableFrom(actual));
```

Đây là đoạn minh họa; `value` và `SomeApiType` phải có thật trong code gọi. `isAssignableFrom` xác định khả năng gán kiểu theo runtime type hierarchy, không chỉ tên class.

## 12. Tự kiểm tra kiến thức

**Câu 1.** Hai `Class<?>` có cùng `getName()` nhưng `getClassLoader()` là hai instance khác nhau. Có thể kết luận `Class` của chúng bằng nhau không?

**Đáp án:** Không. Nếu đó là hai **defining loader** thực tế khác nhau cho cùng tên class, chúng là hai kiểu runtime riêng. Còn nếu chỉ biết hai loader được dùng để **yêu cầu nạp**, cả hai có thể ủy quyền về cùng class do cha định nghĩa, nên phải kiểm tra `Class<?>` trả về.

**Câu 2.** Vì sao hai plugin có thể dùng chung một interface nhưng không thể cast trực tiếp implementation của nhau?

**Đáp án:** Cùng interface **do loader chung định nghĩa** là một type runtime duy nhất; mỗi implementation vẫn có thể là một type riêng do loader plugin định nghĩa. Cast tới interface chung hợp lệ, cast sang implementation bản khác không hợp lệ.

**Câu 3.** `getClass().getName().equals(...)` có thay thế an toàn cho `getClass() == ...` trong `equals()` không?

**Đáp án:** Không. Chuỗi tên có thể trùng trong khi type khác loader; dùng tên có thể khiến nhánh cast không an toàn, làm thay đổi ngữ nghĩa và gây `ClassCastException`.

**Câu 4.** Hai object không bằng nhau có thể có cùng `hashCode()` không?

**Đáp án:** Có. Collision là bình thường và không vi phạm hợp đồng. Chỉ khi `equals()` trả `true` thì hash bằng nhau là bắt buộc.

**Câu 5.** `Class.forName(name, true, loaderA)` và cùng lời gọi với `loaderB` chắc chắn trả hai class khác nhau?

**Đáp án:** Không. Nếu cả hai loader ủy quyền cho cùng một defining loader, chúng có thể trả cùng đối tượng `Class`. Phải xem cách loader thực thi.

## Ghi nhớ

**Bản sắc một lớp trong JVM không được quyết định chỉ bằng tên.** Với các class tham chiếu thông thường, tên nhị phân kết hợp **class loader định nghĩa** mới xác định kiểu runtime. Vì vậy các class cùng tên từ loader khác nhau có thể không cast được cho nhau, `instanceof` thất bại và `getClass()` cho hai đối tượng khác nhau. Để tránh lỗi ở hệ thống plugin/module, hãy chia sẻ API chung qua một định nghĩa runtime thống nhất, kiểm tra `Class<?>` và defining loader thay vì chỉ so tên, và tách quan hệ bằng nhau nghiệp vụ khỏi việc nhận diện implementation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="real-world-classes-007">Vai trò của getClass() trong equals(), clone() và chẩn đoán</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của getClass() trong equals(), clone() và chẩn đoán

## 1. getClass() cho biết điều gì?

**Object.getClass()** là phương thức public final của java.lang.Object, có trên mọi đối tượng Java không null. Nó trả về một đối tượng **Class** biểu diễn **lớp thực tế tại thời điểm chạy (runtime class)** của đối tượng đang nhận lời gọi, kể cả khi biến đang được khai báo bằng kiểu cha hoặc interface.

~~~java
Object value = "Java";
Class<?> actual = value.getClass();
System.out.println(actual == String.class); // true
~~~

Biến value có kiểu khai báo Object nhưng đối tượng đang trỏ tới là String. Vì vậy getClass() trả Class của String. Kết quả **không phụ thuộc vào kiểu khai báo của biến tham chiếu**.

Một số điểm nền tảng:

- Class là đối tượng mô tả một kiểu trong môi trường chạy Java. Có thể dùng để kiểm tra tên, modifier, superclass, interface, annotation hay phục vụ reflection.
- Với một đối tượng đang tồn tại, getClass() trả lớp thực tế của chính đối tượng đó. Các lần gọi không làm thay đổi lớp đối tượng.
- getClass() là final nên không thể ghi đè để giả mạo lớp trả về bằng một phương thức có cùng chữ ký.
- Gọi getClass() trên biến tham chiếu **null** gây NullPointerException, vì không có đối tượng để gọi phương thức.
- Một lớp có thể được nạp bởi các class loader khác nhau. Danh tính Class không chỉ là tên chuỗi của lớp; hai kiểu trùng tên nhưng do class loader khác nhau định nghĩa có thể là những Class khác nhau.

Ba ứng dụng thường gặp trong hợp đồng đối tượng là: **kiểm tra kiểu chính xác trong equals()**, **xác minh kiểu runtime khi sao chép qua clone()**, và **chẩn đoán đối tượng hoặc hệ thống reflection/proxy**. getClass() tự nó không quyết định hai đối tượng bằng nhau, không thực hiện clone và không giải thích nguyên nhân lỗi.

## 2. getClass() trong equals(): so sánh đúng lớp runtime

Khi ghi đè equals(Object), lập trình viên phải xác định liệu hai đối tượng thuộc các kiểu khác nhau có được phép coi là bằng nhau hay không.

Một mẫu phổ biến để chỉ chấp nhận **đúng cùng lớp runtime**:

~~~java
@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
        return false;
    }
    Account other = (Account) obj;
    return id.equals(other.id);
}
~~~

Đây là **trích đoạn** nằm trong lớp Account có trường id khác null và không thay đổi; chưa phải chương trình độc lập.

Cách triển khai trên hoạt động theo thứ tự:

1. Cùng tham chiếu this == obj: trả true (phản xạ, và tránh kiểm tra thừa).
2. obj null: trả false, không gọi obj.getClass() trên null.
3. getClass() khác obj.getClass(): trả false dù hai đối tượng có cùng trường id.
4. Chỉ sau khi xác định lớp runtime giống nhau mới ép kiểu và so sánh các trường quyết định equality.
5. hashCode phải nhất quán với equals: mọi cặp equals true bắt buộc có hash bằng nhau.

Trong mã này, **getClass() != obj.getClass() kiểm tra danh tính lớp**, khác với so sánh chuỗi tên lớp. Hai đối tượng từ cùng lớp có cùng đối tượng Class, còn hai lớp khác nhau sẽ không có cùng Class.

### getClass() so với instanceof

Hai kiểu kiểm tra có ý nghĩa khác nhau:

| Cách kiểm tra | Điều được chấp nhận | Hệ quả |
| --- | --- | --- |
| obj instanceof Account | Account và các đối tượng thuộc lớp con của Account | Hỗ trợ quan hệ kiểu con |
| obj != null && getClass() == obj.getClass() | Chỉ đối tượng có đúng lớp runtime như this | Loại lớp con khác runtime class |
| Account.class == obj.getClass() | Chỉ đúng lớp Account được nhắc trực tiếp | Không tương đương getClass() của this khi chạy trong lớp con |

**Ví dụ:** một đối tượng PremiumAccount kế thừa Account là instanceof Account, nhưng PremiumAccount.class khác Account.class. Vì vậy getClass cho hai đối tượng thuộc hai lớp này khác nhau.

Nếu equals của lớp cha dùng instanceof và chỉ so sánh id, còn lớp con bổ sung trạng thái quan trọng rồi ghi đè equals, rất dễ rơi vào lỗi **mất tính đối xứng**: lớp cha xem đối tượng lớp con là bằng nhau, nhưng lớp con từ chối lớp cha. Kiểm tra đúng lớp bằng getClass() có thể giúp định nghĩa một chính sách “khác lớp thì khác giá trị” rõ ràng và tránh một nhóm lỗi như vậy.

Tuy nhiên **getClass() không tự động làm equals đúng**: vẫn phải kiểm tra tính đối xứng, bắc cầu, nhất quán và quan hệ với hashCode. Đặc biệt khi lớp cha cho phép subclass mở rộng các trường mang ý nghĩa equality, việc để subclass kế thừa nguyên equals mà không bổ sung quy tắc có thể khiến các đối tượng lớp con bị coi bằng nhau dù các trường mới khác nhau.

### Khi nào không nên dùng kiểm tra lớp chính xác?

Nếu mô hình nghiệp vụ chủ ý coi các đối tượng từ các lớp con khác nhau là cùng một giá trị dựa trên giao diện chung, kiểm tra getClass() sẽ **quá chặt**. Một quy tắc dựa trên instanceof, interface, định danh hoặc thiết kế value object thống nhất có thể phù hợp hơn, nhưng phải bảo đảm đối xứng và bắc cầu trong toàn bộ hệ phân cấp.

Ví dụ các lớp proxy runtime (một số ORM hoặc thư viện sinh lớp proxy động) có thể là lớp con của kiểu miền. getClass() trên proxy trả **lớp proxy thực tế**, khác với lớp thực thể ban đầu. Nếu equals dùng strict getClass, entity và proxy của nó có thể so sánh false ngay cả khi biểu diễn cùng một bản ghi. Trường hợp này cần tuân theo hợp đồng equality và API nhận diện lớp thực thể do framework cụ thể cung cấp; không nên tự giả định mọi proxy có cùng hành vi.

**Nguyên tắc:** lựa chọn getClass hay instanceof dựa vào ý nghĩa bình đẳng và chiến lược kế thừa của lớp, không phải vì một câu thần chú “getClass luôn tốt hơn”.

## 3. getClass() và hợp đồng hashCode

Giả sử hai đối tượng phải cùng **lớp runtime và cùng id** mới equals:

~~~java
@Override
public int hashCode() {
    return java.util.Objects.hash(getClass(), id);
}
~~~

Công thức này nhất quán với equals ở phần 2: nếu hai đối tượng có cùng runtime class và id, chúng có cùng giá trị đầu vào để băm.

Tuy nhiên, **không bắt buộc** hashCode phải thêm getClass() để hợp đồng đúng. Dùng id.hashCode() cũng có thể hợp lệ nếu id không null, vì hai đối tượng equals true luôn có id bằng nhau; những đối tượng khác lớp nhưng id giống nhau có thể va chạm hash, và va chạm là hợp lệ.

Vì thế:

- Thêm getClass vào hash có thể giúp phân tán theo kiểu khi có nhiều lớp dùng cùng id.
- Bỏ getClass khỏi hash **không tự động là lỗi** nếu vẫn bảo đảm equals true kéo theo cùng hash.
- Không dùng getClass().getName() chỉ để suy ra danh tính lớp; dùng chính Class trong những thiết kế cần phân biệt runtime type.
- Nếu equals theo chính sách chấp nhận lớp con bằng lớp cha, phải chọn dữ liệu băm tương thích chính sách ấy; thêm runtime class vào hash có thể làm hai đối tượng equals true nhận hash khác nhau.

Với đối tượng dùng làm khóa HashSet/HashMap, các trường tham gia equality nên ổn định trong suốt thời gian lưu khóa. Class runtime không đổi, nhưng các trường mutable như id nếu bị sửa vẫn có thể làm hash thay đổi và gây khó tìm lại khóa.

## 4. getClass() liên quan gì đến clone()?

Object.clone() là phương thức **protected** của Object. Với lớp có triển khai giao diện marker **Cloneable**, lời gọi super.clone() trong một override hợp lệ thường tạo một đối tượng mới có **đúng lớp runtime của đối tượng nguồn**, và sao chép từng trường theo kiểu **shallow copy**.

Đối với một lớp đơn giản:

~~~java
@Override
public MyType clone() {
    try {
        return (MyType) super.clone();
    } catch (CloneNotSupportedException e) {
        throw new AssertionError(e);
    }
}
~~~

Đoạn này giả định lớp MyType implements Cloneable và không cần thêm xử lý sau khi clone. super.clone() không gọi constructor theo cách thông thường để xây dựng lại toàn bộ object graph.

Một số điều quan trọng:

1. **clone không tự do gọi từ bên ngoài**: Object.clone là protected; lớp có thể mở rộng quyền truy cập bằng override public clone.
2. **Cloneable không có phương thức clone**: đó là marker interface làm Object.clone chấp nhận sao chép; chỉ khai báo implements Cloneable không tự biến clone thành public.
3. **super.clone() tạo một thể hiện mới cùng runtime class**: nếu lời gọi đi qua lớp cha đến một đối tượng lớp con hỗ trợ Cloneable, thể hiện được tạo ra vẫn thuộc lớp con thực tế, không bị cắt xuống thành lớp cha.
4. **Sao chép nông**: trường primitive được chép giá trị; trường tham chiếu được chép tham chiếu, nên cả nguồn và bản sao có thể cùng trỏ vào một List, mảng, hoặc đối tượng mutable.
5. Nếu đối tượng không thực hiện Cloneable mà trực tiếp gọi Object.clone, CloneNotSupportedException sẽ được ném. Mảng Java là trường hợp có hỗ trợ clone và việc clone mảng tạo mảng mới cùng kiểu, nhưng phần tử tham chiếu bên trong vẫn được sao chép nông.

getClass giúp **kiểm tra kiểu thực tế của bản sao**:

~~~java
Object copied = original.clone();
boolean sameRuntimeClass = original.getClass() == copied.getClass();
~~~

Đây là đoạn minh họa đặt trong ngữ cảnh original có một phương thức clone truy cập được.

### Điều gì là bảo đảm, điều gì chỉ là kỳ vọng?

Với **super.clone() của Object**, bản sao có runtime class giống nguồn và các trường được sao chép nông. Nếu lớp **ghi đè clone theo cách riêng** (ví dụ tự khởi tạo một kiểu khác và trả về dưới dạng Object), không thể kết luận mọi phương thức tên clone của mọi lớp Java đều giữ đúng runtime class. Tài liệu Object.clone mô tả các kỳ vọng thông thường như bản sao khác tham chiếu, cùng lớp runtime, thường tương đương về giá trị; các kỳ vọng này không được bắt buộc tuyệt đối đối với mọi override tự định nghĩa.

Ngoài ra:

- Hai đối tượng khác tham chiếu không mặc định equals: nếu lớp kế thừa Object.equals, nó so sánh danh tính nên bản sao cũng không equals nguồn dù mọi trường vừa được copy.
- Bản sao cùng Class không có nghĩa là các đối tượng con được copy độc lập.
- Kiểm tra getClass() bằng nhau không chứng minh dữ liệu bằng nhau, copy đầy đủ, hoặc an toàn khi cùng sửa từ hai luồng.

## 5. Ví dụ Java 17+ hoàn chỉnh: equals, clone và chẩn đoán

Tạo tệp **GetClassContractDemo.java**:

~~~java
import java.util.Arrays;
import java.util.Objects;

public class GetClassContractDemo {
    static class Key {
        private final String id;

        Key(String id) {
            this.id = Objects.requireNonNull(id);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Key other = (Key) obj;
            return id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), id);
        }
    }

    // Không thêm trường tham gia equality: lớp con chỉ để minh họa kiểu runtime.
    static final class SpecialKey extends Key {
        SpecialKey(String id) {
            super(id);
        }
    }

    static final class CloneBox implements Cloneable {
        private final String name;
        private final int[] numbers;

        CloneBox(String name, int[] numbers) {
            this.name = Objects.requireNonNull(name);
            this.numbers = Objects.requireNonNull(numbers).clone();
        }

        @Override
        public CloneBox clone() {
            try {
                return (CloneBox) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        CloneBox independentCopy() {
            return new CloneBox(name, numbers);
        }

        void changeFirst(int value) {
            numbers[0] = value;
        }

        int first() {
            return numbers[0];
        }
    }

    public static void main(String[] args) {
        Key first = new Key("A");
        Key second = new Key("A");
        Key subclass = new SpecialKey("A");

        System.out.println("same Class for same type: "
                + (first.getClass() == second.getClass()));
        System.out.println("different Class for subclass: "
                + (first.getClass() != subclass.getClass()));
        System.out.println("first equals second: " + first.equals(second));
        System.out.println("first equals subclass: " + first.equals(subclass));
        System.out.println("subclass equals first: " + subclass.equals(first));
        System.out.println("subclass instanceof Key: "
                + (subclass instanceof Key));
        System.out.println("runtime simple name: "
                + subclass.getClass().getSimpleName());

        CloneBox original = new CloneBox("data", new int[] {10, 20});
        CloneBox shallow = original.clone();
        System.out.println("clone is a new reference: "
                + (original != shallow));
        System.out.println("clone has same Class: "
                + (original.getClass() == shallow.getClass()));
        System.out.println("clone shares array: "
                + (original.numbers == shallow.numbers));

        shallow.changeFirst(99);
        System.out.println("original first after shallow edit: "
                + original.first());

        CloneBox independent = original.independentCopy();
        System.out.println("independent array: "
                + (original.numbers != independent.numbers));
        independent.changeFirst(7);
        System.out.println("original first after independent edit: "
                + original.first());

        System.out.println("class full name: "
                + first.getClass().getName());
        System.out.println("array class: "
                + new int[2].getClass().getName());
        System.out.println("array is array type: "
                + new int[2].getClass().isArray());
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 GetClassContractDemo.java
java GetClassContractDemo
~~~

**Stdout chính xác:**

~~~text
same Class for same type: true
different Class for subclass: true
first equals second: true
first equals subclass: false
subclass equals first: false
subclass instanceof Key: true
runtime simple name: SpecialKey
clone is a new reference: true
clone has same Class: true
clone shares array: true
original first after shallow edit: 99
independent array: true
original first after independent edit: 99
class full name: GetClassContractDemo$Key
array class: [I
array is array type: true
~~~

**Giải thích kết quả:**

- first và second là hai đối tượng Key riêng nhưng cùng runtime class và id, vì vậy equals trả true.
- subclass là SpecialKey: vẫn là instanceof Key, nhưng runtime class khác Key. Phép equals kế thừa từ Key dùng getClass() nên cả hai chiều Key/SpecialKey đều false; đây là một ví dụ về chính sách equality nghiêm ngặt theo lớp runtime.
- original.clone() qua super.clone() tạo CloneBox mới cùng runtime class. Tuy nhiên, trường numbers của hai bản vẫn cùng tham chiếu một mảng: sửa shallow cũng làm original thấy số 99.
- independentCopy tạo một CloneBox mới từ constructor có sao chép mảng đầu vào. Sau khi sửa bản độc lập, original vẫn giữ phần tử đầu tiên là 99.
- getName() cho lớp lồng tĩnh Key có tên nhị phân gồm dấu $. Kiểu mảng int[] có tên nhị phân JVM dạng [I; isArray() giúp nhận biết đó là kiểu mảng. Tên lớp để chẩn đoán phụ thuộc cấu trúc khai báo, package và class loader thực tế.

Ví dụ dùng riêng lớp Key để minh họa nguyên tắc equality, và CloneBox để minh họa clone. Không nên hiểu rằng mọi class đều phải vừa ghi đè equals vừa hỗ trợ Cloneable.

## 6. Dùng getClass() để chẩn đoán và điều tra lỗi

Khi một đối tượng xuất hiện ở vị trí nhận Object hoặc interface, getClass() giúp xác định **runtime type thực sự**:

~~~java
static void inspect(Object value) {
    if (value == null) {
        System.out.println("null reference");
        return;
    }
    Class<?> type = value.getClass();
    System.out.println("type: " + type.getName());
    System.out.println("simple name: " + type.getSimpleName());
    System.out.println("is array: " + type.isArray());
}
~~~

Đây là phương thức minh họa có thể đặt trong một lớp Java; không phải chương trình độc lập vì thiếu lớp chứa và lời gọi.

Các API đáng biết:

| API | Thông tin | Lưu ý |
| --- | --- | --- |
| obj.getClass() | Đối tượng Class của kiểu runtime | obj phải khác null |
| type.getName() | Tên kiểu theo quy ước JVM/Java | Lớp lồng, mảng có tên đặc thù |
| type.getSimpleName() | Tên ngắn của kiểu | Có thể là chuỗi rỗng với lớp ẩn danh |
| type.getCanonicalName() | Tên chính tắc nếu tồn tại | Một số kiểu local/anonymous không có canonical name |
| type.getSuperclass() | Lớp cha trực tiếp (nếu có) | Interface và Object có thể trả null |
| type.getInterfaces() | Các interface trực tiếp được khai báo | Không tự liệt kê mọi interface kế thừa gián tiếp |
| type.isArray() | Kiểu có phải mảng không | Hữu ích vì tên mảng có dạng JVM |
| type.isInterface() | Kiểu có phải interface không | Hỗ trợ phân loại kiểu khi phản chiếu |

Ví dụ khi một biến kiểu List thực tế chứa ArrayList, getClass có thể giúp nhận ra lớp triển khai ở runtime. Với thư viện tạo proxy hoặc bytecode tăng cường, tên runtime có thể chứa mã sinh tự động; kết quả getClass hỗ trợ chẩn đoán nhưng không nên xem tên sinh tự động là API ổn định.

### getClass() không thay thế instanceof và kiểm tra kiểu an toàn

Nếu chỉ cần biết đối tượng có hỗ trợ một giao diện nào đó, kiểm tra instanceof thường phản ánh đúng mục tiêu hơn:

~~~java
if (value instanceof java.util.List<?> list) {
    System.out.println("size: " + list.size());
}
~~~

Kiểm tra bằng chuỗi như getClass().getName().equals("java.util.ArrayList") dễ bị phụ thuộc triển khai, không xét lớp con, và có thể xử lý sai khi xuất hiện proxy. Dùng instanceof, Class.isInstance hoặc Class.isAssignableFrom phù hợp với mục tiêu kiểm tra khả năng gán kiểu.

### Chẩn đoán lớp và ClassLoader

Một kiểu được định danh bởi **tên nhị phân và class loader định nghĩa nó**. Khi gặp ClassCastException gây ngạc nhiên giữa những tên lớp trông giống nhau, thông tin class loader có thể giúp phân tích:

~~~java
Class<?> type = value.getClass();
ClassLoader loader = type.getClassLoader();
System.out.println("class: " + type.getName());
System.out.println("loader: " + loader);
~~~

Đoạn minh họa; loader có thể là null đối với lớp do bootstrap class loader định nghĩa, nên không gọi loader.toString() mà không kiểm tra null. Chỉ log thông tin lớp và loader ở mức phù hợp với chính sách vận hành, vì tên lớp/loader có thể làm lộ chi tiết triển khai trong log công khai.

Thông tin getClass giúp *đặt câu hỏi đúng*: đây là lớp thật hay proxy, đúng phiên bản và loader không, nó có kế thừa/triển khai đúng kiểu không? Nhưng riêng getClass không chứng minh nguyên nhân của mọi ClassCastException hoặc lỗi serialization/clone.

## 7. Những lỗi thường gặp và cách tránh

### Lỗi 1: Gọi obj.getClass() khi obj có thể null

~~~java
// Nguy cơ NullPointerException:
return getClass() == obj.getClass();
~~~

Cần kiểm tra obj == null trước. Khi muốn kiểm tra hai tham chiếu có cùng danh tính, toán tử == an toàn với null. Khi muốn so giá trị có thể null, dùng Objects.equals hoặc logic phù hợp.

### Lỗi 2: Dùng getClass trong equals rồi quên hashCode

Nếu equals được ghi đè, phải bảo đảm hashCode tương thích: hai đối tượng bằng nhau luôn có cùng hash. getClass không tự thay đổi hashCode của Object hay bảo đảm đúng hợp đồng. Các lớp giá trị nên xác định những trường tham gia equality rồi băm phù hợp.

### Lỗi 3: Coi getClass và instanceof là tương đương

getClass kiểm tra đúng kiểu runtime, còn instanceof chấp nhận kiểu con hoặc triển khai interface. Việc thay thế một cách máy móc có thể đổi nghĩa nghiệp vụ, đặc biệt với hệ phân cấp lớp và proxy runtime.

### Lỗi 4: Nghĩ Cloneable tạo bản sao sâu

implements Cloneable chỉ cho phép Object.clone sao chép theo cơ chế của nó. Nếu chứa mảng, List, Map hay đối tượng con mutable, bản sao qua super.clone thường chia sẻ các tham chiếu ấy. Muốn sao chép độc lập phải xử lý rõ từng thành phần mutable, cân nhắc aliasing hoặc dùng copy constructor/factory.

### Lỗi 5: Nghĩ clone() chỉ có thể trả đúng kiểu runtime

super.clone giữ runtime class trong trường hợp chuẩn. Nhưng một override clone tùy chỉnh có thể dùng logic riêng; không được coi bất kỳ phương thức có tên clone nào cũng được JVM bắt buộc sao y kiểu nguồn. Kiểm tra hợp đồng API cụ thể trước khi ép kiểu và dựa vào kết quả.

### Lỗi 6: Dùng getClass().getName() làm danh tính nghiệp vụ

Tên lớp có thể thay đổi khi refactor, shading, proxy generation hoặc class loader khác nhau. Danh tính doanh nghiệp thường nên dựa trên id hoặc dữ liệu miền. Tên lớp phục vụ phản chiếu và chẩn đoán, không phải khóa nghiệp vụ ổn định.

### Lỗi 7: Dùng constructor reflection để thay Object.clone mà không xét khác biệt

Một thao tác tạo đối tượng mới bằng reflection và gọi constructor **không mặc định** tạo bản sao: nó chạy logic constructor, có thể thiếu tham số, không sao chép trường, và bị ràng buộc truy cập/module. getClass().getDeclaredConstructor().newInstance() không thay thế super.clone hoặc một copy constructor được thiết kế đúng.

## 8. Cách quyết định trong thực tế

| Nhu cầu | Cách tiếp cận |
| --- | --- |
| equals chỉ chấp nhận đúng cùng runtime class | So sánh getClass() sau khi kiểm tra null |
| equals cố ý hỗ trợ lớp con/giao diện chung | Xem xét instanceof và thiết kế hợp đồng toàn bộ phân cấp |
| Hash code tương thích với equals theo lớp và trường | Hash các dữ liệu tương đương, có thể gồm getClass nếu cần |
| Xác minh kiểu bản sao từ super.clone | So sánh getClass giữa nguồn và bản sao |
| Muốn bản sao độc lập của dữ liệu mutable | Sao chép phần tử/đối tượng con thích hợp hoặc dùng copy constructor |
| Gỡ lỗi runtime type/proxy | Ghi getClass().getName(), kiểm tra superclass và loader khi cần |
| Kiểm tra một đối tượng có hỗ trợ interface | instanceof hoặc Class.isInstance/isAssignableFrom |
| Kiểm tra chuỗi tên lớp cho logic nghiệp vụ | Tránh, ưu tiên dữ liệu định danh và API kiểu |

**Checklist trước khi hoàn tất:**

1. Với equals, đã định nghĩa rõ cùng lớp hay khác lớp có được coi bằng nhau chưa? Kiểm tra null, phản xạ, đối xứng, bắc cầu và hashCode.
2. Nếu có lớp con, việc thêm trạng thái equality hoặc dùng proxy có làm đổi kết quả không? Có xung đột với yêu cầu nghiệp vụ không?
3. Nếu clone, đã xác định mức sao chép nông/sâu chưa? Bản sao có chia sẻ cấu trúc mutable không? Có cần copy constructor/factory an toàn hơn không?
4. Nếu dùng getClass để log, đã xử lý null, lớp được sinh động và tính ổn định của tên lớp chưa?
5. Nếu thấy hai đối tượng có cùng getClass, nhớ rằng điều này **không** bảo đảm equals true, cấu trúc bên trong giống nhau hoặc hai đối tượng chia sẻ trạng thái.

## 9. Ghi nhớ

**getClass() cho biết chính xác runtime class của một đối tượng, không phải kiểu khai báo của biến.** Trong equals, nó hỗ trợ chính sách “đúng cùng lớp thì mới được so sánh giá trị”, cần được chọn có ý thức so với instanceof. Trong clone, nó giúp quan sát việc super.clone tạo bản sao cùng runtime class, nhưng việc chia sẻ trường tham chiếu có thể khiến bản sao chỉ là shallow copy. Trong chẩn đoán, getClass là điểm xuất phát hữu ích để nhận diện kiểu, lớp proxy, kiểu mảng và class loader — không thay cho việc thiết kế equality, copy hay xử lý lỗi đúng ngữ cảnh.

</details>

- [Quay lại đầu trang](#back-to-top)
