<a id="back-to-top"></a>

# Quy tắc PECS

## Menu
- [Nguồn cung cấp dùng extends, đích nhận dữ liệu dùng super](#pecs-001)
- [Xác định vai trò đọc–ghi của từng tham số](#pecs-002)
- [Thiết kế hàm sao chép từ List&lt;? extends T&gt; sang List&lt;? super T&gt;](#pecs-003)
- [Khi cùng tham số vừa cung cấp vừa nhận dữ liệu](#pecs-004)
- [Khi dùng wildcard và khi phải đặt tên tham số kiểu](#pecs-005)
- [Ràng buộc nhiều đầu vào cùng chia sẻ một kiểu](#pecs-006)
- [Giới hạn khi trả về wildcard từ API](#pecs-007)
- [Đọc chữ ký Collections.copy, addAll và Comparator](#pecs-008)

## <a id="pecs-001">Nguồn cung cấp dùng extends, đích nhận dữ liệu dùng super</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc PECS: nguồn cung cấp dùng `extends`, đích nhận dữ liệu dùng `super`

Khi một phương thức Java Generics làm việc với một tập hợp, điều quan trọng là xác định **tập hợp ấy cung cấp dữ liệu cho phương thức hay nhận dữ liệu từ phương thức**. Đây là ý tưởng của **PECS — Producer Extends, Consumer Super**:

- **Producer (nguồn cung cấp):** phương thức **đọc các giá trị kiểu `T`** từ đối tượng đầu vào. Thường khai báo `Collection<? extends T>`, `List<? extends T>` hoặc một dạng generic tương đương.
- **Consumer (đích nhận):** phương thức **ghi các giá trị kiểu `T`** vào đối tượng đầu vào. Thường khai báo `Collection<? super T>` hoặc `List<? super T>`.

`extends` và `super` được chọn theo **vai trò của tham số trong phương thức đang viết**, không phải theo việc đối tượng có khả năng đọc và ghi nói chung. Một `ArrayList<Integer>` có thể được truyền dưới góc nhìn producer trong phương thức tính tổng và dưới góc nhìn consumer trong phương thức thêm số nguyên.

PECS giúp chấp nhận các kiểu con/kiểu cha thích hợp mà **không phá an toàn kiểu**, đặc biệt khi Java generic bất biến (invariant) theo đối số kiểu.

## 1. Bài toán: vì sao không dùng `List<Number>` cho mọi thứ?

Giả sử có `List<Integer>` và `List<Double>`. Vì `Integer` và `Double` đều là `Number`, một phương thức tính tổng phải đọc được cả hai danh sách. Nhưng phép gán này bị cấm:

~~~java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = integers; // LỖI BIÊN DỊCH.
~~~

Đây là **đoạn minh họa sai kiểu**, cần đặt trong class và import `java.util.*` nếu muốn thử biên dịch. Java không coi `List<Integer>` là subtype của `List<Number>`. Nếu cho phép, tham chiếu `numbers` có thể thêm `Double` vào danh sách vốn chỉ chấp nhận `Integer`.

Mặt khác, nếu một phương thức chỉ muốn **thêm `Integer`**, đích có thể là `List<Integer>`, `List<Number>` hoặc `List<Object>`: cả ba kiểu đều nhận được một `Integer`.

Hai nhu cầu cần hai hợp đồng riêng:

~~~java
static double sum(List<? extends Number> source) {
    double total = 0.0;
    for (Number number : source) {
        total += number.doubleValue();
    }
    return total;
}

static void addDefaults(List<? super Integer> destination) {
    destination.add(10);
    destination.add(20);
}
~~~

Đây là **hai phương thức minh họa**, cần đặt trong class với `import java.util.List;`. `sum` yêu cầu nguồn cung cấp giá trị có thể đọc như `Number`; `addDefaults` yêu cầu đích nhận được các giá trị `Integer`. Chúng cùng khai báo wildcard nhưng theo hai chiều ngược nhau.

## 2. Producer Extends: nguồn cung cấp `? extends T`

Xem chữ ký:

~~~java
static void printNumbers(List<? extends Number> source) {
    for (Number number : source) {
        System.out.println(number);
    }
}
~~~

Nghĩa là: **đối số kiểu cụ thể của danh sách phải là `Number` hoặc subtype của `Number`**. Vì vậy `printNumbers` nhận được `List<Integer>`, `List<Double>`, `List<Number>` và các danh sách số phù hợp khác.

Compiler không biết subtype cụ thể của `source`, nhưng biết **mọi phần tử đọc ra đều sử dụng được như `Number`**:

~~~java
List<Integer> integers = new ArrayList<>(List.of(1, 2));
List<? extends Number> source = integers;

Number first = source.get(0); // Hợp lệ.
Object alsoFirst = source.get(0); // Hợp lệ.

// Integer exact = source.get(0); // LỖI: subtype có thể là Double.
// source.add(3);                // LỖI: subtype thực có thể là Double.
// source.add(3.5);              // LỖI: subtype thực có thể là Integer.
~~~

Đây là **đoạn minh họa cần đặt trong class với `import java.util.*;`**. `source` đang trỏ tới `List<Integer>`, nhưng **hợp đồng kiểu tĩnh** chỉ nói `List<? extends Number>`. Compiler không được lấy thông tin bên ngoài khai báo kiểu này để cho phép thêm `Integer` tùy ý.

Nếu `source.add(3)` được phép, cùng chữ ký ấy có thể đang tham chiếu `List<Double>` và gây sai kiểu. Vì thế, producer thuận tiện để **đọc**, nhưng không thể thêm giá trị không phải `null` thông qua `add`.

### Ví dụ chạy được: đọc nhiều subtype cùng một phương thức

Tạo tệp **`ProducerDemo.java`**:

~~~java
import java.util.List;

public class ProducerDemo {
    private static double sum(List<? extends Number> source) {
        double total = 0.0;
        for (Number n : source) {
            total += n.doubleValue();
        }
        return total;
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(2, 4, 6);
        List<Double> doubles = List.of(1.5, 2.5);
        List<Number> mixed = List.of(3, 4.5);

        System.out.println("integers = " + sum(integers));
        System.out.println("doubles = " + sum(doubles));
        System.out.println("mixed = " + sum(mixed));
    }
}
~~~

Biên dịch và chạy trên Java 17+:

~~~bash
javac ProducerDemo.java
java ProducerDemo
~~~

Stdout:

~~~text
integers = 12.0
doubles = 4.0
mixed = 7.5
~~~

Một phương thức xử lý được cả ba kiểu danh sách vì phép tính tổng chỉ dùng các thao tác được `Number` bảo đảm. Không có ép kiểu unchecked hoặc sao chép collection.

## 3. Consumer Super: đích nhận `? super T`

Xem chữ ký:

~~~java
static void addNumbers(List<? super Integer> destination) {
    destination.add(5);
    destination.add(10);
}
~~~

Hợp đồng này chấp nhận `List<Integer>`, `List<Number>` hoặc `List<Object>` vì **cả ba đều lưu được phần tử kiểu `Integer`**. Nó không nhận `List<Double>`: `Double` là subtype khác của `Number`, không phải supertype của `Integer`.

~~~java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();

addNumbers(integers); // Hợp lệ.
addNumbers(numbers);  // Hợp lệ.
addNumbers(objects);  // Hợp lệ.

// addNumbers(new ArrayList<Double>()); // LỖI: Double không là supertype của Integer.
~~~

Các đoạn trên minh họa lời gọi một phương thức đã khai báo, cần đặt trong class và import `java.util.*`.

Có thể gọi `destination.add(5)` vì `5` được boxing thành `Integer`. Nhưng compiler không cho phép thêm một `Number` tùy ý:

~~~java
List<? super Integer> destination = new ArrayList<Number>();

destination.add(Integer.valueOf(7)); // Hợp lệ.
destination.add(8);                  // Hợp lệ qua autoboxing.
destination.add(null);               // Hợp lệ về kiểu.
// destination.add(Double.valueOf(1.5)); // LỖI.
// destination.add(new Object());       // LỖI.
~~~

Đây là **đoạn minh họa cần import `java.util.*`**. Lý do: tham chiếu wildcard có thể trỏ tới `List<Integer>`, nên compiler không được cho thêm `Double` hay `Object` vào nó. `null` tương thích với mọi kiểu tham chiếu, nhưng collection thật vẫn có thể cấm `null` hoặc không hỗ trợ sửa.

### Vì sao consumer chỉ cho đọc như `Object`?

`List<? super Integer>` có thể trỏ tới `List<Object>` chứa `String`, `Boolean` hoặc nhiều giá trị khác, nên **không bảo đảm mọi phần tử đang có đều là `Integer`**:

~~~java
List<Object> any = new ArrayList<>();
any.add("hello");
any.add(42);

List<? super Integer> sink = any;
sink.add(7);

Object first = sink.get(0); // Hợp lệ: thực tế là String.
// Integer n = sink.get(0); // LỖI BIÊN DỊCH.
// Number n = sink.get(0);  // LỖI BIÊN DỊCH.
~~~

Do đó, `super` **mạnh ở phía ghi `T`** nhưng **yếu hơn ở phía đọc**: trình biên dịch chỉ cam kết kiểu chung là `Object`. Không suy luận kiểu dữ liệu đã tồn tại chỉ từ những phần tử mà một phương thức vừa thêm vào.

## 4. Ví dụ Java 17+: producer và consumer trong một chương trình

Tạo tệp **`PecsBasicsDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class PecsBasicsDemo {
    private static void printAll(List<? extends Number> source) {
        for (Number n : source) {
            System.out.print(n + " ");
        }
        System.out.println();
    }

    private static void appendIds(List<? super Integer> destination) {
        destination.add(10);
        destination.add(20);
    }

    public static void main(String[] args) {
        List<Integer> ids = new ArrayList<>(List.of(1, 2));
        List<Number> numbers = new ArrayList<>(List.of(3.5));
        List<Object> objects = new ArrayList<>();

        printAll(ids);
        printAll(numbers);

        appendIds(ids);
        appendIds(numbers);
        appendIds(objects);

        System.out.println("ids = " + ids);
        System.out.println("numbers = " + numbers);
        System.out.println("objects = " + objects);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PecsBasicsDemo.java
java PecsBasicsDemo
~~~

Stdout chính xác:

~~~text
1 2 
3.5 
ids = [1, 2, 10, 20]
numbers = [3.5, 10, 20]
objects = [10, 20]
~~~

Dấu cách ở cuối hai dòng đầu là do `System.out.print(n + " ")`. Nếu muốn không có dấu cách cuối dòng, có thể đổi vòng lặp sang `StringJoiner`; điều đó không liên quan tới PECS.

Ví dụ dùng cùng `List<Integer> ids` làm **producer** khi `printAll(ids)` và **consumer** khi `appendIds(ids)`. Vai trò phụ thuộc **thao tác của hàm**, không phụ thuộc bản chất cố định của đối tượng `ids`.

Danh sách `numbers` chứa một `Double` sẵn có, nhưng vẫn nhận thêm các `Integer` qua `appendIds`. Điều này an toàn với `List<Number>` và minh họa lý do `? super Integer` có ích.

## 5. Ghép cả hai chiều để chuyển phần tử từ nguồn sang đích

Ứng dụng đặc trưng của PECS là **chuyển/sao chép phần tử có subtype cụ thể vào collection nhận một kiểu rộng hơn**.

Chữ ký tổng quát:

~~~java
static <T> void appendAll(
        Collection<? extends T> source,
        Collection<? super T> destination) {
    for (T item : source) {
        destination.add(item);
    }
}
~~~

Đây là **một phương thức cần đặt trong class với `import java.util.Collection;`**. `T` đại diện cho kiểu mà ta muốn truyền qua ranh giới nguồn–đích.

- `Collection<? extends T>` bảo đảm mọi phần tử đọc từ `source` đều dùng được như `T`.
- `Collection<? super T>` bảo đảm mọi phần tử `T` đều có thể được thêm vào `destination`.
- Phương thức không cần biết subtype cụ thể của nguồn hoặc supertype cụ thể của đích.
- `T` có thể được compiler suy luận trong từng lời gọi hoặc được caller nêu rõ bằng cú pháp `<T>`.

### Chương trình chạy được: chuyển `Integer` và `Double` sang `Number`

Tạo **`PecsTransferDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PecsTransferDemo {
    private static <T> void appendAll(
            Collection<? extends T> source,
            Collection<? super T> destination) {
        for (T value : source) {
            destination.add(value);
        }
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(1, 2);
        List<Double> doubles = List.of(3.5, 4.5);
        List<Number> numbers = new ArrayList<>();
        List<Object> objects = new ArrayList<>();

        appendAll(integers, numbers);
        appendAll(doubles, numbers);
        appendAll(numbers, objects);

        System.out.println("numbers = " + numbers);
        System.out.println("objects = " + objects);
        System.out.println("integers = " + integers);
        System.out.println("doubles = " + doubles);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PecsTransferDemo.java
java PecsTransferDemo
~~~

Stdout:

~~~text
numbers = [1, 2, 3.5, 4.5]
objects = [1, 2, 3.5, 4.5]
integers = [1, 2]
doubles = [3.5, 4.5]
~~~

Các lời gọi thứ nhất và thứ hai nạp những phần tử `Integer`/`Double` vào `List<Number>` mà không bao giờ gán `List<Integer>` trực tiếp sang `List<Number>`. Lời gọi thứ ba nạp các `Number` vào `List<Object>`. Mỗi collection đích có cấu trúc riêng; thêm phần tử không làm thay đổi collection nguồn.

**Lưu ý về aliasing:** bản minh họa giả định nguồn và đích khác nhau. Khi nguồn và đích trỏ tới **cùng một collection mutable**, vòng lặp thêm vào collection trong lúc duyệt chính nó có thể phát sinh `ConcurrentModificationException` hoặc hành vi phụ thuộc implementation. Nếu API cần hỗ trợ trường hợp này, có thể chụp bản sao tạm trước khi thêm, ví dụ `destination.addAll(new ArrayList<T>(source))` trong phương thức generic; nên xác định rõ hợp đồng thay vì ngầm cho rằng luôn an toàn.

## 6. Ví dụ với quan hệ kế thừa nghiệp vụ

PECS không chỉ dành cho kiểu số. Giả sử `Dog` và `Cat` là các subtype của `Animal`. Ta muốn lấy các động vật từ một danh sách loài cụ thể và thêm vào danh sách tổng hợp.

Tạo **`PecsAnimalsDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class PecsAnimalsDemo {
    static class Animal {
        private final String name;

        Animal(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static class Dog extends Animal {
        Dog(String name) {
            super(name);
        }
    }

    static class Cat extends Animal {
        Cat(String name) {
            super(name);
        }
    }

    private static <T> void copyInto(
            List<? extends T> source,
            List<? super T> destination) {
        destination.addAll(source);
    }

    public static void main(String[] args) {
        List<Dog> dogs = List.of(new Dog("Bim"), new Dog("Milo"));
        List<Cat> cats = List.of(new Cat("Miu"));
        List<Animal> animals = new ArrayList<>();
        List<Object> everything = new ArrayList<>();

        PecsAnimalsDemo.<Animal>copyInto(dogs, animals);
        PecsAnimalsDemo.<Animal>copyInto(cats, animals);
        PecsAnimalsDemo.<Animal>copyInto(animals, everything);

        System.out.println("animals = " + animals);
        System.out.println("everything = " + everything);
    }
}
~~~

Chạy:

~~~bash
javac PecsAnimalsDemo.java
java PecsAnimalsDemo
~~~

Stdout chính xác:

~~~text
animals = [Bim, Milo, Miu]
everything = [Bim, Milo, Miu]
~~~

Ở các lời gọi trên, ta chọn rõ `T = Animal`:

- `List<Dog>` và `List<Cat>` phù hợp với tham số nguồn `List<? extends Animal>`.
- `List<Animal>` và `List<Object>` phù hợp với tham số đích `List<? super Animal>`.
- Phương thức `copyInto` sử dụng `List.addAll(Collection<? extends E>)` để thêm các phần tử từ nguồn, không làm thay đổi kiểu danh sách gốc.
- Các collection đích chứa **tham chiếu tới cùng các đối tượng Animal** được sao chép từ nguồn, không tự động tạo bản sao sâu của từng đối tượng.

Nếu muốn truyền `List<Dog>` làm **đích của những giá trị `Animal` bất kỳ**, compiler phải từ chối: danh sách chó không được hứa nhận một `Cat`. Đó chính là giới hạn an toàn kiểu mà PECS giữ lại.

## 7. PECS xuất hiện ngay trong thư viện chuẩn Java

Quy tắc không chỉ là mẹo ghi nhớ. Nhiều chữ ký Java Collections Framework đã áp dụng hai chiều wildcard.

### 7.1. `Collection.addAll(Collection<? extends E>)`

Giả sử có `Collection<Number> destination`. Phương thức `addAll` cần **đọc** các phần tử từ đối số và thêm vào collection đích, nên đối số nguồn có dạng `Collection<? extends E>`:

~~~java
List<Number> numbers = new ArrayList<>();
List<Integer> integers = List.of(1, 2);

numbers.addAll(integers); // Hợp lệ.
numbers.add(3.5);
System.out.println(numbers); // [1, 2, 3.5]
~~~

Đây là **đoạn minh họa cần đặt trong class với `import java.util.*;`**. `numbers` có `E = Number`, do đó nguồn `Collection<? extends Number>` có thể là `List<Integer>`.

### 7.2. `Collections.addAll(Collection<? super T>, T...)`

Phương thức utility `Collections.addAll` thêm nhiều giá trị kiểu `T` vào một collection đích. Vì collection ấy **tiêu thụ** giá trị `T` nên dùng `Collection<? super T>`:

~~~java
Collection<Number> numbers = new ArrayList<>();
Collections.addAll(numbers, 1, 2, 3);
System.out.println(numbers); // [1, 2, 3]
~~~

Đây là **đoạn minh họa cần import `java.util.*`**. Phần `T...` là varargs kiểu `T`, ở ví dụ các `Integer` được đưa vào một `Collection<Number>`.

### 7.3. `Collections.copy`: một consumer được điền theo chỉ số

Chữ ký của `Collections.copy` cũng có nguồn `List<? extends T>` và đích `List<? super T>`, nhưng **đích xuất hiện trước**:

~~~java
// Chữ ký API chuẩn để đọc, không phải phương thức cần khai báo lại:
static <T> void copy(List<? super T> dest, List<? extends T> src)
~~~

Nguồn cung cấp `T`; đích nhận `T` bằng thao tác **`set` trên các vị trí đã có**. Vì vậy `Collections.copy` không nối thêm phần tử, và `dest.size()` phải ít nhất bằng `src.size()`; đích còn phải hỗ trợ ghi đè. Nếu mục tiêu là append, hãy dùng `destination.addAll(source)`. Điều kiện về kích thước và tính mutable là hợp đồng runtime, khác với bảo đảm kiểu của PECS.

### 7.4. `Comparator<? super T>`: consumer không phải collection

Một `Comparator<T>` **nhận hai giá trị `T`** để gọi `compare`, nên `List<Integer>.sort(...)` có thể chấp nhận `Comparator<Number>` thông qua `Comparator<? super Integer>`. Không có phần tử nào được lưu vào comparator: “consumer” ở đây có nghĩa nó **nhận giá trị kiểu `T` làm đầu vào**.

Hai ví dụ API này nối PECS với thư viện chuẩn; bài đọc chữ ký `Collections.copy`, `addAll` và `Comparator` đi sâu vào overload, output, cảnh báo compiler và các ngoại lệ runtime.


## 8. Khi PECS không nên áp dụng máy móc

PECS là nguyên tắc thiết kế **chữ ký API**, không có nghĩa mọi generic parameter phải là wildcard.

### 8.1. Khi vừa đọc và ghi đúng cùng một kiểu `T`

~~~java
static <T> void replaceFirst(List<T> list, T replacement) {
    if (!list.isEmpty()) {
        T previous = list.set(0, replacement);
        System.out.println("old = " + previous);
    }
}
~~~

Đây là **phương thức minh họa cần đặt trong class với `import java.util.List;`**. `List<T>` liên kết kiểu phần tử đọc ra với kiểu giá trị mới được ghi vào. Dùng `List<? extends T>` sẽ cản `set` với giá trị `T` bất kỳ; dùng `List<? super T>` sẽ không cho đọc phần tử ra dưới kiểu `T` như đã viết.

Vì vậy, nếu API thực sự **cả đọc và ghi theo một hợp đồng kiểu xác định**, `List<T>` có thể đúng hơn.

### 8.2. Nếu không cần biết kiểu phần tử

Một phương thức chỉ in kích thước danh sách hoặc kiểm tra rỗng có thể dùng `List<?>`:

~~~java
static boolean hasItems(List<?> values) {
    return !values.isEmpty();
}
~~~

Đây là phương thức minh họa, cần import `java.util.List`. Nó chấp nhận bất kỳ danh sách nào và không cần một cận kiểu `T` cụ thể. Sử dụng `List<? extends Object>` trong trường hợp thông thường có ý nghĩa gần như `List<?>`, nhưng không có lợi ích rõ ràng so với dạng ngắn gọn.

### 8.3. Cẩn thận khi `T` được compiler suy luận

Xem:

~~~java
static <T> void show(List<T> input) {
    System.out.println(input.size());
}
~~~

Phương thức generic này có thể lần lượt nhận `List<Integer>` và `List<String>` ở **các lời gọi riêng biệt**, vì `T` có thể được suy luận khác nhau. Vì thế không nên nói tuyệt đối rằng “chữ ký `List<T>` không bao giờ chấp nhận các kiểu khác nhau”.

Khác biệt cần nắm:

- Khi `T` **đã cố định là `Number`**, `List<T>` chỉ tương thích trực tiếp với `List<Number>`, còn `List<? extends T>` nhận `List<Integer>`, `List<Double>`…
- Khi `T` là type parameter **được suy luận mỗi lần gọi**, `List<T>` vẫn có thể dùng cho những đối số kiểu khác nhau, miễn là ràng buộc của toàn lời gọi phù hợp.
- Wildcard đặc biệt hữu ích khi một phương thức cần **chấp nhận một nguồn subtype và một đích supertype có quan hệ qua cùng `T`**.

Không nên thay mọi `List<T>` bằng `List<? extends T>` chỉ để đạt vẻ “linh hoạt”; hãy nhìn thao tác cụ thể.

## 9. Giới hạn về thay đổi dữ liệu và `null`

`? extends T` thường được mô tả là **read-only view**, nhưng cách diễn đạt này chỉ chính xác nếu hiểu là “không thể tự ý ghi một giá trị kiểu cụ thể mới”.

~~~java
List<Integer> numbers = new ArrayList<>(List.of(1, 2));
List<? extends Number> source = numbers;

source.remove(0); // Hợp lệ về kiểu.
source.clear();    // Hợp lệ về kiểu.
source.add(null);  // Hợp lệ về kiểu; collection thật có thể không chấp nhận.
~~~

Đây là **đoạn minh họa cần import `java.util.*`**. Không có yêu cầu cung cấp một `Number` hay `Integer` để thực hiện `remove`/`clear`, nên compiler cho phép. Với một danh sách bất biến về cấu trúc như `List.of(1, 2)`, các thao tác sửa có thể ném `UnsupportedOperationException`.

`? super T` cũng **không bảo đảm collection thật sửa được**:

~~~java
List<Integer> fixed = List.of(1, 2);
List<? super Integer> sink = fixed;
// sink.add(3); // Biên dịch, nhưng chạy sẽ ném UnsupportedOperationException.
~~~

Đoạn này là minh họa; dòng gọi được chú thích nhằm tránh chương trình lỗi. Java Generics kiểm soát **kiểu dữ liệu**; khả năng sửa, chính sách `null`, lọc trùng và thứ tự phần tử là những đặc tính bổ sung của implementation.

Một chi tiết khác: `? super T` cho phép thêm giá trị thuộc **`T` và các subtype của `T`**, không phải thêm mọi supertype của `T`. Với `? super Integer`, thêm `Integer` hợp lệ nhưng thêm một `Number` có kiểu tĩnh rộng hơn không được bảo đảm.

## 10. Các lỗi hiểu sai PECS thường gặp

**Nhầm lẫn 1: “`extends` chỉ nhận đúng `T`.”** Sai. `List<? extends Number>` nhận cả `List<Integer>` và `List<Double>` vì đó là nguồn có phần tử subtype của `Number`.

**Nhầm lẫn 2: “`super` nhận danh sách của mọi kiểu con.”** Sai. `List<? super Integer>` nhận đối số kiểu `Integer` và các supertype của nó, không nhận `List<Double>` và không nhận `List<Dog>` nếu `Dog` không là supertype của `Integer`.

**Nhầm lẫn 3: “Đọc được thành `T` thì thêm `T` được.”** Sai với `? extends T`. Compiler không biết subtype cụ thể thật sự là gì; việc ghi một `T` tổng quát có thể phá hợp đồng của nguồn.

**Nhầm lẫn 4: “Thêm được `T` thì đọc ra phải là `T`.”** Sai với `? super T`. Collection đích có thể là `List<Object>` chứa những phần tử cũ không phải `T`; đọc qua wildcard chỉ bảo đảm `Object`.

**Nhầm lẫn 5: “`extends` và `super` biến `List<A>` thành `List<B>`.”** Sai. Wildcard cung cấp góc nhìn với phép gán và thao tác được chứng minh an toàn; nó không đổi dữ liệu, không sao chép danh sách và không sửa đối số kiểu của object.

**Nhầm lẫn 6: “Dùng cast unchecked sẽ giải quyết.”** Không an toàn. Ép một `List<Integer>` thành `List<Number>` qua raw hoặc wildcard rồi thêm `Double` có thể gây heap pollution và `ClassCastException` khi đọc kiểu `Integer`. Hãy dùng wildcard đúng vai trò hoặc tạo collection mới nếu cần chứa các loại dữ liệu rộng hơn.

**Nhầm lẫn 7: “`super` luôn cho thêm và `extends` không bao giờ sửa được.”** Đây là sự nhầm lẫn giữa **kiểm tra kiểu** với **khả năng sửa runtime**. Một collection không sửa được vẫn có thể là đối số phù hợp kiểu của `? super T`; `? extends T` vẫn có thể dùng `clear` trên một collection cho phép sửa.

**Nhầm lẫn 8: “`Collections.copy` tự tăng kích thước destination.”** Sai. Nó ghi đè phần tử tại các vị trí hiện có; không tự append. Dùng `addAll` khi muốn tăng số lượng phần tử.

## 11. Bài tập tự kiểm tra kiểu

Giả sử những biến sau được khai báo trong một phương thức với `import java.util.*;`:

~~~java
List<Integer> integers = new ArrayList<>(List.of(1, 2));
List<Double> doubles = new ArrayList<>(List.of(1.5, 2.5));
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();

List<? extends Number> producer = integers;
List<? super Integer> consumer = numbers;
~~~

Xét **từng lệnh độc lập**:

| Biểu thức | Biên dịch? | Vì sao? |
| --- | --- | --- |
| `producer = doubles;` | Có | `Double` là subtype của `Number` |
| `producer = objects;` | Không | `Object` không phải subtype của `Number` |
| `Number n = producer.get(0);` | Có | Producer bảo đảm đọc như `Number` |
| `producer.add(42);` | Không | Không biết subtype thực của producer |
| `producer.add(null);` | Có | `null` tương thích kiểu tham chiếu; runtime có thể từ chối |
| `consumer = objects;` | Có | `Object` là supertype của `Integer` |
| `consumer = doubles;` | Không | `Double` không phải supertype của `Integer` |
| `consumer.add(42);` | Có | Consumer nhận `Integer` |
| `consumer.add(2.5);` | Không | Đích có thể là `List<Integer>` |
| `Object x = consumer.get(0);` | Có | Consumer chỉ bảo đảm `Object` khi đọc |
| `Integer x = consumer.get(0);` | Không | Collection có thể là `List<Object>` |
| `numbers.addAll(integers);` | Có | `addAll` đọc nguồn `<? extends Number>` |
| `Collections.copy(numbers, integers);` | Có | Chữ ký tuân PECS; đích phải đủ phần tử lúc chạy |

Lệnh `Collections.copy(numbers, integers)` trong trạng thái **`numbers` đang rỗng** sẽ biên dịch nhưng ném `IndexOutOfBoundsException` lúc chạy. Câu hỏi “có biên dịch?” và “có chạy thành công?” phải được phân biệt.

Hãy tự trả lời thêm: một phương thức nhận `Collection<? super Animal>` có thể thêm `Dog`, `Cat` không? **Có**, miễn `Dog` và `Cat` kế thừa `Animal`. Nó có thể đọc mọi phần tử như `Animal` không? **Không**, vì đích có thể là `Collection<Object>` chứa các đối tượng khác loại.

## 12. Quy trình thiết kế chữ ký theo luồng dữ liệu

Khi viết một API generic, có thể thực hiện bốn bước:

1. **Xác định giá trị `T` có đi ra khỏi tham số hay đi vào tham số.** Nếu tham số chủ yếu cung cấp giá trị, nó là producer. Nếu tham số chủ yếu nhận giá trị, nó là consumer.
2. **Chọn cận wildcard phù hợp.** Với producer cần đọc như `T`, sử dụng `? extends T`. Với consumer cần chấp nhận `T`, sử dụng `? super T`.
3. **Kiểm tra thao tác thật trong thân hàm.** Nếu `extends` mà cần thêm một `T` tùy ý, hoặc `super` mà cần đọc chắc chắn là `T`, chữ ký hiện tại không đáp ứng nhu cầu; nên điều chỉnh hợp đồng, dùng `List<T>` hoặc một phương thức generic liên kết kiểu.
4. **Kiểm tra yêu cầu runtime.** Collection có sửa được không, có nhận `null` không, có duy trì thứ tự hay loại trùng không, nguồn và đích có thể là cùng một đối tượng không? Các vấn đề này nằm ngoài bảo đảm của PECS.

Bảng ghi nhớ:

| Vai trò của tham số trong hàm | Kiểu điển hình | Điều được bảo đảm |
| --- | --- | --- |
| Đọc các phần tử như `T` | `Collection<? extends T>` | Mọi phần tử lấy ra là `T` hoặc subtype của `T` |
| Ghi các phần tử `T` | `Collection<? super T>` | Collection nhận được giá trị `T` và subtype của nó |
| Cần cả đọc và ghi cùng kiểu `T` | `Collection<T>`, `List<T>` | Một đối số kiểu cụ thể liên kết các thao tác |
| Không cần biết kiểu phần tử | `Collection<?>` | Có thể duyệt phần tử như `Object` và dùng thao tác không phụ thuộc kiểu |

**PECS = Producer Extends, Consumer Super.** Nguồn dùng `extends` để truyền ra `T` an toàn; đích dùng `super` để nhận `T` an toàn. Hai chiều wildcard mở rộng khả năng tái sử dụng của API trong khi giữ nguyên bất biến generic của Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pecs-002">Xác định vai trò đọc–ghi của từng tham số</a>

<details>
<summary>Xem chi tiết</summary>

# Xác định vai trò đọc–ghi của từng tham số theo quy tắc PECS

## 1. Vì sao cần xác định vai trò của tham số?

Khi thiết kế API Java Generics, hai phương thức đều nhận `List<T>` có thể sử dụng danh sách theo cách rất khác nhau: một phương thức chỉ lấy phần tử ra để tính toán, còn phương thức kia chỉ nhận các phần tử mới được ghi vào. Viết tham số là `List<T>` trong mọi trường hợp có thể khiến API từ chối những danh sách hoàn toàn phù hợp với công việc thực tế.

**PECS** là quy tắc lựa chọn wildcard dựa trên vai trò của từng tham số:

- **Producer Extends**: nguồn **cung cấp giá trị kiểu `T` cho thuật toán** → cân nhắc `? extends T`.
- **Consumer Super**: đích **nhận các giá trị kiểu `T` từ thuật toán** → cân nhắc `? super T`.

Đây là quy tắc về **hướng di chuyển của giá trị đối với thuật toán đang viết**, chứ không đơn giản là tham số đứng ở vị trí đầu hay cuối chữ ký, hoặc có được truyền vào phương thức hay không.

Ví dụ:

```java
static <T> void copyValues(
        List<? extends T> source,
        List<? super T> destination) {
    for (T element : source) { // Đọc T từ source
        destination.add(element); // Ghi T vào destination
    }
}
```

Trong ví dụ này, `source` là producer dù nó được **truyền vào** phương thức; `destination` là consumer dù nó cũng được **truyền vào** phương thức. Đọc–ghi phải được nhìn từ **bên trong hoạt động của thuật toán**.

## 2. Quy trình xác định vai trò của từng tham số

Với mỗi tham số generic, hãy thực hiện lần lượt:

1. **Xác định kiểu giá trị `T` đang trao đổi.** Đây có thể là `Number`, `String`, `Animal` hoặc tham số kiểu `<T>` được khai báo trên phương thức.
2. **Đọc thân phương thức theo luồng dữ liệu.** Giá trị `T` được **lấy từ** tham số hay được **đưa vào** tham số?
3. **Tách các thao tác phụ khỏi thao tác chính.** `size()`, `isEmpty()` và `contains(Object)` không cung cấp hoặc nhận một giá trị có kiểu phần tử `T` theo cách PECS quan tâm.
4. **Xét quyền cần thiết:** nếu chỉ lấy `T` ra, chọn `? extends T`; nếu chỉ cung cấp `T` cho đích, chọn `? super T`; nếu cần cả đọc và ghi `T` chính xác, xem xét `List<T>` và một tham số kiểu có tên.
5. **Thử lời gọi với các kiểu con/kiểu cha.** Kiểm tra một `List<Integer>` có thể làm nguồn cho phép đọc `Number`; kiểm tra một `List<Object>` có thể làm đích nhận `Integer`.
6. **Đừng vượt quá cam kết của wildcard.** `extends` không bảo đảm danh sách immutable; `super` không bảo đảm giá trị đọc ra là `T`; cả hai vẫn phụ thuộc khả năng sửa đổi thực tế của implementation.

Một bảng quyết định ban đầu:

| Cách tham số được dùng trong phương thức | Vai trò | Lựa chọn điển hình |
|---|---|---|
| Chỉ đọc phần tử dưới kiểu `T` | Producer | `List<? extends T>` |
| Chỉ đưa giá trị `T` vào danh sách | Consumer | `List<? super T>` |
| Vừa đọc phần tử như `T`, vừa ghi phần tử `T` | Cả hai | `List<T>` hoặc thiết kế `<T>` thích hợp |
| Chỉ kiểm tra số lượng, in thông tin chung, không cần kiểu `T` | Không phụ thuộc kiểu phần tử | `List<?>` |
| Cần bảo toàn một quan hệ kiểu giữa nhiều tham số và kết quả | Phụ thuộc hợp đồng | Kết hợp `<T>` với wildcard ở từng vị trí |

Bảng là hướng dẫn thiết kế, **không phải quy tắc đổi chữ ký máy móc**. Chẳng hạn, `List<? extends T>` vẫn có thể gọi `clear()`, và `List<? super T>` vẫn có thể đọc `Object`. Thuật ngữ producer/consumer nhấn mạnh việc **cung cấp hay nhận giá trị của kiểu `T`**.

## 3. Xác định producer từ các thao tác đọc

Giả sử một phương thức cần tính tổng danh sách số:

```java
static double total(List<? extends Number> values) {
    double result = 0;
    for (Number value : values) {
        result += value.doubleValue();
    }
    return result;
}
```

Phân tích:

- `values` cung cấp từng phần tử bằng `iterator()`/`get()`.
- Phương thức coi mỗi phần tử là `Number` và gọi `doubleValue()`; nó không tạo một phần tử `Number` mới để đưa vào `values`.
- Do đó `values` đóng vai trò **producer của `Number`**. `List<? extends Number>` chấp nhận `List<Number>`, `List<Integer>`, `List<Double>` và các `List<S>` với `S extends Number`.

Nếu khai báo `List<Number>`, lời gọi `total(List<Integer>)` sẽ **không** biên dịch do Generics bất biến (*invariance*), dù `Integer` là lớp con của `Number`.

```java
List<Integer> counts = List.of(2, 3);
List<Double> measurements = List.of(1.5, 2.5);

double a = total(counts);       // 5.0
double b = total(measurements); // 4.0
```

Với `List<? extends Number>`, giá trị đọc từ danh sách có thể dùng như `Number`; nhưng compiler **không cho thêm một `Integer` hoặc `Double` tùy ý vào nó**. Kiểu phần tử ẩn có thể là bất kỳ kiểu con nào của `Number`.

```java
static void inspect(List<? extends Number> values) {
    Number first = values.get(0); // Hợp kiểu, cần danh sách không rỗng
    // values.add(1);              // Không biên dịch
    // values.add(1.5);            // Không biên dịch
}
```

**Lưu ý:** Nếu thuật toán chỉ cần `size()` hoặc in mọi phần tử như `Object`, `List<?>` có thể truyền đạt chính xác mục đích hơn `List<? extends Number>` vì không cần ràng buộc dữ liệu phải là số.

## 4. Xác định consumer từ các thao tác ghi

Xét một phương thức đưa hai số nguyên vào danh sách do bên gọi cung cấp:

```java
static void addDefaults(List<? super Integer> destination) {
    destination.add(10);
    destination.add(20);
}
```

Phân tích:

- Thân phương thức **tạo ra** hai giá trị `Integer`.
- `destination` **nhận** chúng bằng `add`.
- Do đó `destination` là **consumer của `Integer`**, phù hợp `List<? super Integer>`.

```java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();

addDefaults(integers); // Được
addDefaults(numbers);  // Được
addDefaults(objects);  // Được
```

Ba đối số phù hợp vì `Integer` gán được cho `Integer`, `Number` và `Object`. Ngược lại, `List<Double>` không phù hợp với `? super Integer` vì `Double` **không phải kiểu cha của `Integer`**.

Điểm đánh đổi là giá trị được lấy ra từ biến có kiểu `List<? super Integer>` chỉ bảo đảm kiểu tĩnh `Object`:

```java
static void demoReadingConsumer(List<? super Integer> values) {
    Object element = values.get(0); // Hợp kiểu
    // Integer integer = values.get(0); // Không biên dịch
}
```

Điều này hợp lý: một `List<Object>` có thể đã chứa `String` hoặc một đối tượng không phải `Integer` từ trước. Chữ ký consumer chỉ cam kết **đủ rộng để nhận `Integer`**, không cam kết mọi phần tử đang có đều là `Integer`.

`null` có thể được thêm ở mức kiểm tra kiểu với cả `? extends T` và `? super T` vì null tương thích mọi kiểu tham chiếu. Tuy nhiên việc thao tác có thành công lúc chạy hay không phụ thuộc danh sách có cho phép mutation và null. Không dùng khả năng thêm null để kết luận vai trò PECS.

## 5. Khi tham số vừa được đọc vừa được ghi

Một số thao tác **thay thế hoặc biến đổi giá trị cùng kiểu** cần đọc `T` từ danh sách và ghi một giá trị `T` vào chính danh sách ấy. Ví dụ tăng mỗi số nguyên:

```java
static void increment(List<Integer> values) {
    for (int i = 0; i < values.size(); i++) {
        Integer oldValue = values.get(i); // Đọc Integer
        values.set(i, oldValue + 1);      // Ghi Integer
    }
}
```

Ở đây, nếu thay `List<Integer>` thành `List<? extends Integer>` thì có thể đọc dưới kiểu `Integer` nhưng không thể ghi một `Integer` mới. Nếu thay bằng `List<? super Integer>` thì ghi được `Integer` nhưng `get(i)` chỉ an toàn dưới kiểu `Object`. Với đúng hợp đồng “**đọc và ghi lại Integer**”, `List<Integer>` thể hiện quyền cần thiết.

Với thuật toán tổng quát áp dụng một hàm biến đổi giá trị cùng kiểu:

```java
static <T> void transformInPlace(
        List<T> values,
        java.util.function.UnaryOperator<T> transform) {
    for (int i = 0; i < values.size(); i++) {
        T current = values.get(i);
        values.set(i, transform.apply(current));
    }
}
```

Chọn `List<T>` vì phải đọc và ghi **cùng một `T`**. Phương thức không buộc tất cả danh sách phải có tham số kiểu `Object`; `T` được suy luận riêng cho từng lời gọi. Ví dụ `List<String>` và `UnaryOperator<String>` hoàn toàn phù hợp.

Có một ngoại lệ đáng học: **hoán đổi hoặc sao chép trở lại chính các phần tử đã đọc** có thể thực hiện an toàn bằng một helper bắt kiểu wildcard (*wildcard capture*):

```java
static void swapFirstTwo(List<?> values) {
    swapCaptured(values);
}

private static <T> void swapCaptured(List<T> values) {
    if (values.size() < 2) {
        return;
    }
    T first = values.get(0);
    values.set(0, values.get(1));
    values.set(1, first);
}
```

Helper giữ một kiểu `T` ẩn duy nhất cho cả đọc và ghi; nó **không tự tạo một đối tượng `T` mới thuộc kiểu tùy ý**. Đây là tình huống dùng tham số kiểu có tên để kết nối thao tác an toàn, thay vì ép kiểu thô.

## 6. Xác định vai trò riêng của từng tham số trong cùng một phương thức

Một phương thức có thể có nhiều tham số, mỗi tham số thực hiện một vai trò khác nhau. Phân tích **từng vị trí** theo thao tác trên dữ liệu, thay vì áp dụng một wildcard giống nhau cho tất cả.

```java
static <T> void copyValues(
        List<? extends T> source,
        List<? super T> destination) {
    for (T element : source) {
        destination.add(element);
    }
}
```

Luồng dữ liệu là:

```text
source: List<? extends T>
           │ đọc T
           ▼
        element: T
           │ ghi T
           ▼
destination: List<? super T>
```

- Tham số `source` là **producer**. Có thể lấy từng `T` từ danh sách thực sự có phần tử kiểu `T` hoặc kiểu con.
- Tham số `destination` là **consumer**. Có thể thêm `T` vào danh sách thực sự có kiểu phần tử là `T` hoặc kiểu cha.
- Tham số kiểu có tên `<T>` **liên kết cả hai đầu** để compiler kiểm tra quan hệ kiểu chung.

Ví dụ:

```java
List<Integer> integers = List.of(1, 2);
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();

copyValues(integers, numbers); // Integer được nhận qua Number
copyValues(integers, objects); // Integer được nhận qua Object
copyValues(numbers, objects);  // Number được nhận qua Object
```

Dù `Integer` là kiểu con của `Number`, `List<Integer>` không phải kiểu con của `List<Number>`. Chính hai wildcard trong **chữ ký phương thức** giúp những lời gọi này được kiểm tra an toàn, không phải Java đã thay đổi quy tắc invariance.

Một `List<? super T>` dùng làm đích có thể đã chứa giá trị thuộc nhiều kiểu con khác nhau. Không suy luận rằng mọi giá trị đọc ra từ đích là `T`.

## 7. Ví dụ Java 17 hoàn chỉnh: ba vai trò trong một API

Tạo tệp `PecsParameterRolesDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public class PecsParameterRolesDemo {
    static <T> void copyMatching(
            Iterable<? extends T> source,
            Collection<? super T> destination,
            Predicate<? super T> filter) {
        for (T element : source) {
            if (filter.test(element)) {
                destination.add(element);
            }
        }
    }

    static double sum(List<? extends Number> source) {
        double total = 0;
        for (Number element : source) {
            total += element.doubleValue();
        }
        return total;
    }

    static void addDefaults(List<? super Integer> destination) {
        destination.add(10);
        destination.add(20);
    }

    static <T> void replaceFirst(List<T> values, T replacement) {
        if (!values.isEmpty()) {
            values.set(0, replacement);
        }
    }

    static void showCount(List<?> values) {
        System.out.println("Input size: " + values.size());
    }

    public static void main(String[] args) {
        List<Integer> input = List.of(1, 2, 3, 4);
        List<Number> selected = new ArrayList<>();
        Predicate<Number> isEven =
                value -> value.intValue() % 2 == 0;

        copyMatching(input, selected, isEven);
        System.out.println("Selected: " + selected);
        System.out.println("Sum: " + sum(selected));

        List<Object> events = new ArrayList<>();
        addDefaults(events);
        System.out.println("Events: " + events);

        replaceFirst(selected, 99);
        System.out.println("After replace: " + selected);

        showCount(input);
    }
}
```

Chạy trong thư mục chứa tệp (JDK 17 trở lên):

```bash
javac PecsParameterRolesDemo.java
java PecsParameterRolesDemo
```

**Stdout:**

```text
Selected: [2, 4]
Sum: 6.0
Events: [10, 20]
After replace: [99, 4]
Input size: 4
```

Cách phân tích từng tham số:

| Tham số | Thao tác trực tiếp | Vai trò với `T` | Chữ ký |
|---|---|---|---|
| `copyMatching.source` | Đọc `T` từ vòng lặp | Producer | `Iterable<? extends T>` |
| `copyMatching.destination` | `add(element)` | Consumer | `Collection<? super T>` |
| `copyMatching.filter` | `test(element)`: nhận `T` | Consumer | `Predicate<? super T>` |
| `sum.source` | Đọc `Number` và tính `doubleValue()` | Producer | `List<? extends Number>` |
| `addDefaults.destination` | `add(Integer)` | Consumer | `List<? super Integer>` |
| `replaceFirst.values` | `get()` và `set()` với cùng `T` | Đọc và ghi | `List<T>` |
| `showCount.values` | `size()` | Không cần `T` | `List<?>` |

**Giải thích các điểm dễ nhầm:**

1. `filter` cũng là **consumer**, mặc dù `Predicate.test` không ghi một phần tử vào danh sách: nó **nhận một giá trị `T` làm đầu vào** để kiểm tra. Do đó `Predicate<Number>` có thể làm bộ lọc cho các phần tử `Integer`.
2. `source` là `Iterable<? extends T>` vì thuật toán chỉ cần khả năng duyệt phần tử; không cần buộc nguồn cung cấp `get(index)` hoặc `List`. Đây là cách chọn interface vừa đủ cho hợp đồng.
3. `destination` dùng `Collection<? super T>` vì chỉ cần `add`, không cần thao tác theo chỉ số. Đích có thể là `List<Number>` dù nguồn là `List<Integer>`.
4. `replaceFirst` sử dụng `List<T>` vì cần đọc và ghi đúng cùng một kiểu `T`. `selected` là `List<Number>`, nên ghi `Integer 99` hợp lệ qua quan hệ `Integer extends Number`.
5. `input` được tạo bởi `List.of(...)` (không sửa đổi được), nhưng chỉ đóng vai trò nguồn nên chương trình không cố thay đổi nó. `selected` và `events` dùng `ArrayList` để cho phép `add` và `set`.

Cần phân biệt kiểm tra **kiểu tĩnh** với runtime. Nếu đưa một đích không sửa đổi được vào `copyMatching`, lời gọi vẫn có thể biên dịch nhưng `destination.add` có thể ném `UnsupportedOperationException`. PECS giúp xác định kiểu nào **được phép nhận**, không tự cấp khả năng sửa dữ liệu cho implementation.

## 8. Nhìn PECS trong các callback và interface chức năng

PECS không chỉ dành cho `List`. Với một tham số kiểu `T`, hãy xem một đối tượng hàm **nhận `T`** hay **cung cấp `T`**.

### `Predicate<? super T>`: một consumer của `T`

`Predicate<T>.test(T value)` nhận một giá trị kiểu `T` và trả về boolean. Nếu thuật toán có phần tử `Integer`, một `Predicate<Number>` vẫn có thể kiểm tra nó như `Number`:

```java
static <T> long countMatching(
        List<? extends T> source,
        java.util.function.Predicate<? super T> condition) {
    long count = 0;
    for (T item : source) {
        if (condition.test(item)) {
            count++;
        }
    }
    return count;
}
```

Đoạn phương thức này là ví dụ độc lập về chữ ký; để biên dịch cần đặt trong một lớp và import `java.util.List`. `source` cung cấp `T`, còn `condition` nhận `T` nên áp dụng hai hướng wildcard khác nhau.

### `Comparator<? super T>`: nhận hai giá trị `T` để so sánh

`List<E>.sort` có chữ ký nhận `Comparator<? super E>`. Comparator không sinh ra `E`; nó **nhận các `E` làm đối số của `compare`**, nên là consumer.

```java
List<Integer> values = new ArrayList<>(List.of(30, 2, 11));
java.util.Comparator<Number> byValue =
        java.util.Comparator.comparingDouble(Number::doubleValue);

values.sort(byValue);
System.out.println(values); // [2, 11, 30]
```

Đây là đoạn minh họa có thể đặt trong `main` với import `java.util.List` và `java.util.ArrayList`. Việc dùng `Comparator<Number>` để sắp xếp `List<Integer>` là hợp lệ vì `Number` có thể nhận mọi `Integer`.

### `Function<? super T, ? extends R>`: có cả đầu vào và đầu ra

`Function<A,B>` **tiêu thụ** một `A` qua `apply(A)` và **sản xuất** một `B` làm kết quả. Vì vậy, trong một API biến đổi `T` sang `R`, có thể chọn:

```java
static <T, R> List<R> map(
        List<? extends T> source,
        java.util.function.Function<? super T, ? extends R> mapper) {
    List<R> result = new ArrayList<>();
    for (T item : source) {
        result.add(mapper.apply(item));
    }
    return result;
}
```

Ví dụ `source` cung cấp `Integer`; một `Function<Number,String>` nhận `Integer` và trả `String` phù hợp. Ở callback, cận dưới cho **đầu vào**, cận trên cho **đầu ra**; cả hai đều bắt nguồn từ hướng dữ liệu đối với thuật toán.

Lưu ý các đoạn `Comparator` và `map` minh họa API trong một lớp Java phù hợp, không phải chương trình độc lập nếu đứng riêng. Chương trình hoàn chỉnh ở mục 7 đã có tệp và stdout xác định.

## 9. Phân biệt `add`, `set`, `get` với thao tác không xác định vai trò

Khi đọc một phương thức, đừng gắn nhãn “consumer” chỉ vì có thao tác **thay đổi dữ liệu**. PECS quan tâm đến dòng giá trị thuộc kiểu `T`:

| Lời gọi | Liên quan đến `T` thế nào? | Ý nghĩa khi chọn wildcard |
|---|---|---|
| `T x = list.get(i)` | Lấy ra `T` | Producer |
| `for (T x : list)` | Lấy ra `T` | Producer |
| `list.add(valueOfTypeT)` | Đưa `T` vào | Consumer |
| `list.set(i, valueOfTypeT)` | Đưa `T` vào vị trí | Consumer, đồng thời cần xem có đọc `T` hay không |
| `list.size()` | Không trao đổi `T` | Không quyết định PECS |
| `list.isEmpty()` | Không trao đổi `T` | Không quyết định PECS |
| `list.contains(anyObject)` | Nhận `Object`, không đòi `T` | Không đủ để kết luận consumer `T` |
| `list.remove(anyObject)` | Nhận `Object` để tìm/xóa | Không đủ để kết luận consumer `T` |
| `list.remove(index)` | Xóa theo chỉ số, có thể trả phần tử `T` | Nếu dùng kết quả như `T` thì có khía cạnh producer |
| `list.clear()` | Xóa hết, không cần cung cấp `T` | Không biến nó thành consumer `T` |

Ví dụ `List<? extends Number>` **có thể** gọi `remove` hoặc `clear()` về mặt kiểm tra kiểu, dù không thêm được `Integer` cụ thể. Khi gọi trên danh sách không cho sửa, những thao tác đó có thể thất bại tại runtime. Vì thế producer không đồng nghĩa “immutable”.

Tương tự, một `List<? super Integer>` có thể gọi `get`, nhưng chỉ đọc an toàn dưới kiểu `Object`. Consumer không có nghĩa “không thể đọc bất cứ gì”.

### Cẩn thận với overload `remove`

`List` có cả `remove(int index)` và `remove(Object o)`:

```java
List<Integer> values = new ArrayList<>(List.of(10, 20, 30));
values.remove(1);                  // Xóa phần tử tại chỉ số 1
values.remove(Integer.valueOf(30)); // Xóa đối tượng có giá trị 30
```

Nếu đọc phương thức có `remove(1)`, đây là thao tác xóa theo vị trí, không phải “ghi giá trị Integer 1 vào danh sách”. Kiểm tra overload thực tế trước khi phân tích vai trò.

## 10. Khi nào giữ nguyên `List<T>` thay vì wildcard?

PECS là gợi ý tối ưu hợp đồng ở vị trí **chỉ đọc** hoặc **chỉ ghi**. Không nên ép dùng wildcard nếu làm mất một quan hệ kiểu quan trọng.

### Cần đọc và ghi cùng `T`

```java
static <T> void replaceAll(
        List<T> values,
        java.util.function.UnaryOperator<T> op) {
    values.replaceAll(op);
}
```

`List<T>` và `UnaryOperator<T>` biểu thị cùng một kiểu cho đầu vào và kết quả của phép biến đổi. Sử dụng `List<? extends T>` ở vị trí `values` có thể làm việc ghi lại một `T` bất kỳ trở nên không hợp lệ; `List<? super T>` làm mất bảo đảm đọc như `T`.

### Cần một kết quả giữ nguyên kiểu của đầu vào

```java
static <T> T firstOrNull(List<T> values) {
    return values.isEmpty() ? null : values.get(0);
}
```

`firstOrNull(List<String>)` trả về kiểu tĩnh `String`. Nếu dùng `List<?>` và trả về `Object`, bên gọi sẽ mất quan hệ kiểu này. Tuy vậy, có thể dùng một số thiết kế tương đương với `? extends T` tùy nhu cầu suy luận và chữ ký; điểm cần giữ là **type parameter `T` liên hệ đầu vào với đầu ra**.

### Chỉ cần thao tác không phụ thuộc kiểu phần tử

```java
static boolean hasElements(List<?> values) {
    return !values.isEmpty();
}
```

Vì không tạo hay lấy kết quả thuộc một kiểu `T` cần được đặt tên, wildcard không giới hạn `<?>` là hợp đồng đơn giản hơn việc thêm `<T>` không dùng đến.

## 11. Đọc vai trò tham số trong `Collections.copy`

Một ví dụ đáng chú ý khi phân tích **vai trò theo vị trí** là chữ ký của thư viện chuẩn:

```java
static <T> void copy(List<? super T> dest, List<? extends T> src)
```

Trong `Collections.copy(dest, src)`, **tham số đứng trước `dest` là consumer**, còn tham số thứ hai `src` là producer. Điều này ngược với thói quen đặt `source` trước của nhiều helper tự viết: vị trí không quyết định PECS, **thao tác trên giá trị `T` mới quyết định**.

Phương thức dùng `set` để **ghi đè các phần tử đã có**, không `add` để tăng kích thước. Ví dụ với đích `[0, 0, 0]` và nguồn `[7, 8]`, kết quả là `[7, 8, 0]`; nếu đích ngắn hơn nguồn, nó ném `IndexOutOfBoundsException`. Danh sách đích không hỗ trợ thay đổi có thể ném `UnsupportedOperationException`. Các điều kiện này không do wildcard quyết định.


## 12. Một ví dụ sai và cách sửa chữ ký

Giả sử ta muốn gom mọi `Number` từ nguồn vào một danh sách đích:

```java
static void collectWrong(
        List<Number> source,
        List<Number> target) {
    for (Number n : source) {
        target.add(n);
    }
}
```

Phương thức trên biên dịch, nhưng `List<Integer>` không thể truyền vào `source` và `List<Object>` không thể truyền vào `target`. Chữ ký đang giới hạn hơn mức thuật toán yêu cầu.

Quan sát thân hàm:

1. `source` chỉ được đọc qua vòng lặp, cần bảo đảm mỗi giá trị có thể dùng dưới kiểu `Number`.
2. `target` chỉ nhận các giá trị `Number` được lấy từ nguồn, không cần đọc chúng trở lại dưới kiểu `Number`.

Sửa:

```java
static void collectNumbers(
        List<? extends Number> source,
        List<? super Number> target) {
    for (Number n : source) {
        target.add(n);
    }
}
```

Với chữ ký này:

```java
List<Integer> input = List.of(1, 2);
List<Object> output = new ArrayList<>();
collectNumbers(input, output);
System.out.println(output); // [1, 2]
```

Đoạn mẫu cần import `java.util.List` và `java.util.ArrayList`, đặt vào một lớp để biên dịch. Quy tắc sửa là **nới đúng vai trò của từng tham số** nhưng vẫn giữ cận `Number` cho các phép đọc/ghi số.

Không cần chuyển thành raw `List`: raw type bỏ qua kiểm tra tham số kiểu và có thể gây *heap pollution*. Wildcard giữ hợp đồng có thể kiểm tra tại compile time.

## 13. Những bẫy khi phân tích vai trò đọc–ghi

**Bẫy 1 — “Tham số truyền vào là input nên phải dùng `extends`.”** Sai. Trong Java, mọi tham số đều đi vào phương thức, nhưng một tham số có thể đại diện nơi thuật toán ghi kết quả. `destination` thường là consumer `<? super T>`.

**Bẫy 2 — “Producer là danh sách immutable.”** Sai. Producer là vai trò cung cấp dữ liệu cho thuật toán. `List<? extends T>` không ngăn `remove`, `clear` hoặc một số thao tác khác; khả năng sửa đổi do đối tượng thực quyết định.

**Bẫy 3 — “Consumer là không đọc được.”** Sai. `List<? super T>` vẫn đọc được dưới kiểu `Object`, nhưng không bảo đảm mọi phần tử là `T`. Nếu thuật toán cần đọc ra như `T`, hãy xét lại chữ ký.

**Bẫy 4 — “Hễ có `set` là chỉ consumer.”** Chưa đủ. `set` cung cấp một giá trị cho đích, nhưng nếu thuật toán cũng cần đọc giá trị cùng kiểu `T` từ tham số ấy, tham số có cả hai vai trò. Ví dụ `transformInPlace` dùng `List<T>`.

**Bẫy 5 — “`contains` và `remove(Object)` biến tham số thành consumer của `T`.”** Không nhất thiết. Những phương thức này nhận `Object`; cần xem thuật toán có thực sự đưa giá trị kiểu `T` vào vị trí lưu trữ hay không.

**Bẫy 6 — “Mọi interface chức năng chỉ là producer vì trả về một kết quả.”** Sai. `Predicate<T>` và `Comparator<T>` nhận giá trị kiểu `T` nên là consumer của `T`, dù chúng trả về `boolean` hoặc `int`.

**Bẫy 7 — “Một `Function` có cùng vai trò cho cả hai tham số kiểu.”** Sai. `Function<A,B>` nhận `A` và trả `B`; trong ngữ cảnh chuyển đổi, đó thường là `Function<? super A, ? extends B>`.

**Bẫy 8 — “PECS chứng minh chương trình sẽ chạy an toàn trong mọi tình huống.”** Không. Nó kiểm tra **tương thích kiểu**. `IndexOutOfBoundsException`, `UnsupportedOperationException`, `NullPointerException`, dữ liệu không hợp lệ, và các lỗi nghiệp vụ vẫn có thể xảy ra.

## 14. Bài tự kiểm tra vai trò của từng tham số

Với mỗi chữ ký sau, hãy xác định producer, consumer hay cần một kiểu `T` chung:

```java
// A
static double average(List<? extends Number> data);

// B
static void collect(List<? super Integer> target, int value);

// C
static <T> void copy(
        List<? extends T> from,
        List<? super T> to);

// D
static <T> void update(
        List<T> values,
        java.util.function.UnaryOperator<T> change);

// E
static boolean exists(List<?> items);

// F
static <T> long count(
        Iterable<? extends T> input,
        java.util.function.Predicate<? super T> condition);
```

Đây là **chữ ký bài tập**, không phải các phương thức đã có thân và không phải tệp Java biên dịch độc lập.

**Đáp án:**

- **A:** `data` là producer của `Number` vì `average` đọc các giá trị để tính toán; không cần ghi số vào nguồn.
- **B:** `target` là consumer của `Integer` vì nhận giá trị `int` được boxing thành `Integer`. Tham số `value` là một giá trị số, không phải cấu trúc generic cần wildcard.
- **C:** `from` là producer `T`, `to` là consumer `T`; tham số kiểu `<T>` nối hai vai trò.
- **D:** `values` vừa đọc vừa ghi `T`, nên dùng `List<T>`. `change` nhận `T` và trả lại `T`; `UnaryOperator<T>` phản ánh việc biến đổi cùng kiểu.
- **E:** `items` chỉ dùng để hỏi “có phần tử không?”, không cần biết kiểu phần tử; `List<?>` là đủ.
- **F:** `input` là producer của `T`. `condition` nhận từng `T` qua `test` nên là consumer của `T`, dù kết quả `boolean` được dùng để đếm.

Có thể tự kiểm tra thêm bằng cách thay `T = Number` hoặc `T = Integer`, rồi thử đưa `List<Integer>` vào nơi đọc và `List<Object>` vào nơi ghi. Nếu thay `extends` bằng `super` làm hỏng kiểu đọc, hoặc ngược lại, đó là dấu hiệu cần xem lại vai trò.

## 15. Checklist khi thiết kế chữ ký PECS

Trước khi công bố một phương thức generic, hãy trả lời:

1. **Thuật toán đọc những giá trị thuộc kiểu nào?** Nếu nhận giá trị `T` từ tham số, tham số đó thường là producer.
2. **Thuật toán ghi những giá trị thuộc kiểu nào?** Nếu truyền `T` vào tham số, tham số đó thường là consumer.
3. **Tham số nào cần giữ quan hệ cùng kiểu?** Nếu một giá trị đọc phải được ghi lại hoặc trả về với đúng kiểu đó, cân nhắc `<T>` và `List<T>`.
4. **Có tham số nào chỉ gọi thao tác chung?** Nếu không phụ thuộc kiểu phần tử, `<?>` giúp tránh ràng buộc thừa.
5. **Interface tham số đã đủ hẹp chưa?** Nếu chỉ duyệt, `Iterable<? extends T>` có thể đủ; nếu chỉ thêm, `Collection<? super T>` thường đủ; không cần mặc định `List`.
6. **Các kiểu con và cha có được chấp nhận như dự kiến?** Thử các lời gọi `List<Integer>` → `List<Number>` hoặc `List<Object>` mà không dùng raw type.
7. **Các yêu cầu runtime đã rõ chưa?** Với nơi ghi, caller cần truyền collection hỗ trợ mutation; với `Collections.copy`, đích phải có đủ phần tử sẵn có; với dữ liệu null, cần hợp đồng riêng.

Một chữ ký tốt thể hiện **đúng quyền đọc–ghi mà thân phương thức cần**, không trao quyền ghi vào nguồn hay bắt buộc đích phải có đúng kiểu phần tử nếu điều đó không cần thiết.

## 16. Ghi nhớ

PECS là phương pháp **xem mỗi tham số generic đang cung cấp hay nhận giá trị kiểu `T`**. Dùng **`? extends T`** cho tham số cung cấp `T`; dùng **`? super T`** cho tham số nhận `T`. Nếu cả đọc và ghi cùng một kiểu, dùng một tham số kiểu có tên để giữ quan hệ; nếu kiểu phần tử không tham gia thuật toán, `<?>` có thể là lựa chọn rõ ràng nhất.

Mục tiêu của PECS là làm API linh hoạt với các kiểu con/kiểu cha cần thiết **mà vẫn an toàn kiểu tại thời điểm biên dịch**, chứ không chỉ thay vài từ khóa trong chữ ký.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pecs-003">Thiết kế hàm sao chép từ List&lt;? extends T&gt; sang List&lt;? super T&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế hàm sao chép từ `List<? extends T>` sang `List<? super T>`

## Mục tiêu

Trong Java Generics, một hàm sao chép cần nhận phần tử từ một danh sách nguồn rồi đưa chúng vào một danh sách đích **mà không làm mất an toàn kiểu**. Khi nguồn chứa `Integer` và đích chứa `Number` hoặc `Object`, ta muốn chương trình vẫn biên dịch, dù `List<Integer>` không phải subtype của `List<Number>`.

Chữ ký tổng quát giải quyết yêu cầu ấy là:

```java
static <T> void appendCopy(
        List<? super T> destination,
        List<? extends T> source)
```

Đây là **chữ ký minh họa**, phần thân đầy đủ được trình bày ở ví dụ chạy độc lập phía dưới. Bài học giúp bạn:

- Xác định đúng vai trò **nguồn đọc** (producer) và **đích ghi** (consumer) theo quy tắc PECS.
- Giải thích vì sao `List<? extends T>` đọc được phần tử dạng `T`, còn `List<? super T>` nhận được phần tử dạng `T`.
- Tự thiết kế hàm **nối thêm một bản sao các phần tử** theo thứ tự, bảo toàn danh sách nguồn.
- Phân biệt hàm nối thêm với `Collections.copy(destination, source)`, vốn **ghi đè theo chỉ số** và cần dung lượng *hiện có* của đích.
- Biết xử lý trường hợp nguồn và đích chính là một danh sách, các danh sách không sửa được và những giới hạn runtime.

## 1. Bài toán và giới hạn của generics bất biến

Giả sử:

```java
List<Integer> integers = new ArrayList<>(List.of(2, 4));
List<Number> numbers = new ArrayList<>();
```

Ta có `Integer extends Number`, nhưng **không thể gán `List<Integer>` cho `List<Number>`**:

```java
// List<Number> invalid = integers; // lỗi biên dịch
```

Nếu phép gán ấy được chấp nhận, một nơi đang giữ `List<Number>` có thể thêm `Double` vào đối tượng thực là `List<Integer>`. Vì vậy Java giữ `List<Integer>` và `List<Number>` **bất biến theo đối số kiểu** (*invariant*).

Tuy nhiên, đây là nhu cầu hợp lệ:

- **Đọc** số nguyên từ `List<Integer>`.
- **Ghi** từng số nguyên ấy vào `List<Number>`.

Ta không cần coi hai list là cùng kiểu; chỉ cần xác định quan hệ kiểu tại **điểm lấy phần tử** và **điểm đưa phần tử vào**.

## 2. PECS: Producer `extends`, Consumer `super`

Quy tắc **PECS** (*Producer Extends, Consumer Super*) định hướng wildcard theo vai trò tham số:

| Vai trò | Kiểu khai báo | Điều chắc chắn có thể làm |
| --- | --- | --- |
| Nguồn cung cấp `T` | `List<? extends T>` | Đọc phần tử ra biến kiểu `T` |
| Đích nhận `T` | `List<? super T>` | Thêm một giá trị kiểu `T` vào đích |
| Danh sách có kiểu chính xác | `List<T>` | Đọc và thêm giá trị kiểu `T`, nếu implementation cho phép |

### Vì sao `source` dùng `? extends T`?

`List<? extends T>` có kiểu phần tử thực là một subtype **chưa biết chính xác** của `T`. Mọi giá trị lấy ra đều có thể dùng như `T`:

```java
static <T> T firstOf(List<? extends T> source) {
    return source.get(0);
}
```

Đoạn trên là phương thức minh họa, giả định list không rỗng. Trình biên dịch cho phép `source.get(0)` trả về kiểu `T`. Nhưng gọi `source.add(valueOfTypeT)` nhìn chung **không biên dịch**, vì kiểu phần tử thực có thể là một subtype hẹp hơn `T`.

Ví dụ với `T = Number`, nguồn có thể là `List<Integer>` hoặc `List<Double>`. Ghi một `Number` bất kỳ vào danh sách ấy không an toàn.

### Vì sao `destination` dùng `? super T`?

`List<? super T>` có kiểu phần tử thực là `T` hoặc một siêu kiểu chưa biết của `T`. Nếu `T = Integer`, một `Integer` có thể được thêm an toàn vào `List<Integer>`, `List<Number>` hoặc `List<Object>`:

```java
static <T> void addOne(List<? super T> destination, T value) {
    destination.add(value);
}
```

Phương thức này là minh họa riêng, không phải ví dụ Java độc lập. Chiều ngược lại, lấy từ `List<? super T>` chỉ được bảo đảm nhận dưới `Object` vì phần tử đang nằm trong đích có thể được khai báo ở một kiểu rộng hơn `T`.

**Điểm cốt lõi:** hàm sao chép đọc `T` từ producer và ghi chính `T` vào consumer; nó không cần ép kiểu không kiểm tra và không cần `List<Integer>` chuyển thành `List<Number>`.

## 3. Chữ ký hàm sao chép và ý nghĩa từng thành phần

Ta chọn thứ tự tham số **đích trước, nguồn sau**:

```java
static <T> void appendCopy(
        List<? super T> destination,
        List<? extends T> source) {

    for (T value : source) {
        destination.add(value);
    }
}
```

Đoạn trên minh họa ý tưởng cơ bản, **chưa xử lý trường hợp nguồn và đích cùng một danh sách**. Phần sau sẽ dùng một bản chụp tạm để an toàn cho tình huống đó.

Phân tích chữ ký:

- `static <T>`: khai báo **type parameter của phương thức**; đây không phải cú pháp so sánh “bé hơn”.
- `void`: hàm thực hiện thao tác sao chép, không trả kết quả.
- `List<? super T> destination`: đích có thể tiếp nhận giá trị kiểu `T`.
- `List<? extends T> source`: nguồn cung cấp các giá trị có thể coi như `T`.
- `T value : source`: phép đọc an toàn; mỗi giá trị nguồn ít nhất tương thích với `T`.
- `destination.add(value)`: phép ghi an toàn; kiểu phần tử của đích là `T` hoặc một siêu kiểu của `T`.

Một ví dụ suy luận kiểu: gọi `appendCopy(numbers, integers)` với `List<Number>` là đích và `List<Integer>` là nguồn. Compiler có thể chọn `T = Integer`: nguồn `List<Integer>` phù hợp `List<? extends Integer>` và đích `List<Number>` phù hợp `List<? super Integer>`. Không phải bản thân `List<Integer>` trở thành `List<Number>`.

Bạn cũng có thể dùng lời gọi chỉ định đối số kiểu:

```java
// Khi đã có numbers và integers như ở trên:
appendCopy(numbers, integers);             // suy luận T
MyUtility.<Integer>appendCopy(numbers, integers); // cú pháp chỉ rõ T,
                                                   // nếu hàm thuộc lớp MyUtility
```

Hai dòng chỉ **minh họa cách gọi**; dòng có `MyUtility` đòi bạn đặt phương thức vào lớp tương ứng. Trong phần lớn trường hợp, để trình biên dịch tự suy luận `T` sẽ gọn và rõ hơn.

## 4. Phiên bản an toàn khi nguồn và đích cùng một danh sách

Một hàm append viết vòng `for-each` trực tiếp trên nguồn thường chạy tốt khi nguồn/đích độc lập. Nhưng nếu cùng một danh sách vừa bị duyệt vừa bị `add`, nhiều implementation như `ArrayList` có thể phát hiện thay đổi cấu trúc và ném `ConcurrentModificationException`. Không nên dựa vào hành vi của việc đồng thời duyệt và thêm vào cùng list.

Một giải pháp dễ kiểm chứng: **chụp tạm các phần tử nguồn trước, rồi mới thay đổi đích**:

```java
static <T> void appendCopy(
        List<? super T> destination,
        List<? extends T> source) {
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(source, "source");

    List<T> snapshot = new ArrayList<>(source);
    for (T value : snapshot) {
        destination.add(value);
    }
}
```

Lời gọi `new ArrayList<>(source)` tạo một **danh sách mới chứa các tham chiếu phần tử hiện có**. Nó không clone sâu từng đối tượng phần tử. Bản chụp tạm này làm cho vòng lặp chỉ đọc một cấu trúc ổn định trong khi hàm đang thêm vào đích.

Khi `destination == source`, giả sử dữ liệu đầu là `[7, 8]`, bản chụp chứa đúng `[7, 8]`; thêm hai phần tử ấy vào đối tượng nguồn/đích tạo `[7, 8, 7, 8]`. Hàm hoàn tất mà không vừa duyệt vừa mở rộng chính danh sách đang duyệt.

`Objects.requireNonNull` kiểm tra hai tham số trước thao tác sao chép và báo lỗi `NullPointerException` với tên đối số khi đầu vào null. Hai tham số không-null **không có nghĩa từng phần tử bên trong cũng không-null**; danh sách đích có thể có quy định riêng về phần tử null.

Phiên bản này được sử dụng trong chương trình hoàn chỉnh tiếp theo.

## 5. Ví dụ Java 17+ chạy độc lập

Lưu tệp với tên **`PecCopyDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PecCopyDemo {
    static <T> void appendCopy(
            List<? super T> destination,
            List<? extends T> source) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(source, "source");

        List<T> snapshot = new ArrayList<>(source);
        for (T value : snapshot) {
            destination.add(value);
        }
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>(List.of(2, 4));
        List<Number> numbers = new ArrayList<>(List.of(1.5));
        appendCopy(numbers, integers);
        System.out.println("numbers: " + numbers);

        List<Object> objects = new ArrayList<>(List.of("begin"));
        appendCopy(objects, integers);
        System.out.println("objects: " + objects);

        List<Integer> same = new ArrayList<>(List.of(7, 8));
        appendCopy(same, same);
        System.out.println("same: " + same);
        System.out.println("integers unchanged: " + integers);

        List<Number> slots = new ArrayList<>(List.of(-1, -1, -1));
        Collections.copy(slots, integers);
        System.out.println("slots after Collections.copy: " + slots);

        List<Number> tooShort = new ArrayList<>(List.of(-1));
        try {
            Collections.copy(tooShort, integers);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("too short: IndexOutOfBoundsException");
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac PecCopyDemo.java
java PecCopyDemo
```

**Stdout chính xác:**

```text
numbers: [1.5, 2, 4]
objects: [begin, 2, 4]
same: [7, 8, 7, 8]
integers unchanged: [2, 4]
slots after Collections.copy: [2, 4, -1]
too short: IndexOutOfBoundsException
```

**Diễn giải các dòng kết quả:**

1. `List<Integer> integers` làm nguồn và `List<Number> numbers` làm đích. `appendCopy` nối `2`, `4` vào sau `1.5`: kết quả `[1.5, 2, 4]`.
2. `List<Object> objects` cũng là đích hợp lệ cho nguồn `List<Integer>`, nên kết quả `[begin, 2, 4]`.
3. `appendCopy(same, same)` chụp hai phần tử nguồn trước khi thêm: `[7, 8]` thành `[7, 8, 7, 8]`; không có lặp vô hạn.
4. `integers` vẫn là `[2, 4]` vì việc sao chép chỉ **đọc** nó, không xóa hay sửa phần tử.
5. `Collections.copy(slots, integers)` khác `appendCopy`: nó thay hai phần tử đầu của đích `[-1, -1, -1]` và để nguyên phần tử còn lại, được `[2, 4, -1]`.
6. `Collections.copy(tooShort, integers)` ném `IndexOutOfBoundsException` vì danh sách đích chỉ có một vị trí, trong khi nguồn có hai phần tử.

Chương trình dùng đúng các import `java.util` và không phụ thuộc framework bên ngoài. `Number` và `Integer` thuộc `java.lang` nên không cần import riêng.

## 6. “Copy” có hai ý nghĩa: nối thêm và ghi đè

Điểm dễ gây sai lệch trong yêu cầu là từ **“sao chép”**. Hai cơ chế đều đọc nguồn và đưa dữ liệu vào đích, nhưng có ngữ nghĩa khác nhau:

| Đặc điểm | `appendCopy(destination, source)` ở trên | `Collections.copy(destination, source)` |
| --- | --- | --- |
| Thao tác lên đích | `add` từng phần tử vào cuối | `set` đè từng vị trí tương ứng |
| Đích ban đầu | Có thể rỗng, miễn cho phép thêm | Phải có ít nhất `source.size()` phần tử |
| Thay đổi kích thước đích | Tăng theo số phần tử nguồn | Giữ nguyên |
| Ví dụ đích `[9, 9, 9]`, nguồn `[1, 2]` | `[9, 9, 9, 1, 2]` | `[1, 2, 9]` |
| Đích mutable thích hợp | List hỗ trợ thêm | List hỗ trợ `set` |
| Khi hai tham số cùng list | Bản chụp tạm giúp append hữu hạn | Không có nhu cầu tăng kích thước |
| Độ phức tạp cơ bản của triển khai mẫu | O(n) thời gian thêm đối với `ArrayList`, O(n) bộ nhớ snapshot | Ghi đè n vị trí, không cần tăng kích thước; chi phí thực tế phụ thuộc implementation |

Chính API chuẩn có chữ ký gần tương tự:

```java
public static <T> void copy(
        List<? super T> dest,
        List<? extends T> src)
```

Đây là **chữ ký minh họa** của `java.util.Collections.copy`. Phương thức nhận **destination trước, source sau**. Với `Collections.copy`, số phần tử đang có của đích phải **lớn hơn hoặc bằng** số phần tử nguồn. Gọi `new ArrayList<>(capacity)` chỉ cấp *dung lượng nội bộ*, **không tăng `size()`** của list, vì vậy không đáp ứng điều kiện đó.

Ví dụ sai thường gặp:

```java
List<Number> destination = new ArrayList<>(10);
List<Integer> source = List.of(1, 2);

// Collections.copy(destination, source); // runtime IndexOutOfBoundsException
```

Danh sách `destination` ở đây có `size() == 0` mặc dù capacity ban đầu là 10. Muốn nối thêm hai số, dùng `appendCopy(destination, source)`; muốn ghi đè theo `Collections.copy` thì phải khởi tạo số **phần tử thực** đủ lớn và bảo đảm `set` được hỗ trợ.

Ngược lại, `Arrays.asList(...)` tạo danh sách kích thước cố định nhưng vẫn thường hỗ trợ `set` nên có thể thích hợp với `Collections.copy` nếu đủ phần tử; nó không hỗ trợ `add` làm tăng kích thước, nên `appendCopy` không phù hợp.

## 7. Tại sao không cần ép kiểu hoặc raw type?

Một cách làm nguy hiểm là cố biến nguồn `List<Integer>` thành `List<Number>`, hoặc dùng raw `List` để né kiểm tra kiểu. Ví dụ bên dưới **không nên dùng**:

```java
// Sai hướng; đây là ví dụ về ép kiểu không an toàn:
@SuppressWarnings({"rawtypes", "unchecked"})
static void unsafe(List destination, List source) {
    destination.addAll(source);
}
```

Thao tác raw type có thể làm mất thông tin kiểu, cho phép thêm giá trị không phù hợp vào một danh sách đáng lẽ chỉ chứa một subtype nhất định. Lỗi có thể chỉ xuất hiện ở thời điểm đọc/ép kiểu sau này (`ClassCastException`).

PECS giúp biên dịch viên chứng minh quan hệ hợp lệ mà không cần cast:

- Nguồn: `List<? extends T>` cung cấp một `T`.
- Đích: `List<? super T>` nhận được giá trị `T` ấy.
- Tất cả ràng buộc được kiểm tra tại **compile time**.

Nếu chỉ cần nối thêm dữ liệu và **biết chắc nguồn không bị thay đổi khi đang thao tác** cũng như không trùng đối tượng nguồn/đích, có thể viết ngắn bằng `destination.addAll(source)` trong cùng chữ ký PECS. Bản chụp trong ví dụ chính làm rõ ngữ nghĩa khi tự nối và chi phí sao chép, vì vậy phù hợp cho ví dụ giáo dục.

## 8. Suy luận kiểu `T` trong các trường hợp thực tế

Xét những tổ hợp điển hình:

| Kiểu nguồn | Kiểu đích | `appendCopy(dest, src)` | Giải thích |
| --- | --- | --- | --- |
| `List<Integer>` | `List<Integer>` | Hợp lệ | Cùng kiểu phần tử |
| `List<Integer>` | `List<Number>` | Hợp lệ | Đích chứa được `Integer` |
| `List<Integer>` | `List<Object>` | Hợp lệ | `Object` là siêu kiểu của `Integer` |
| `List<Double>` | `List<Number>` | Hợp lệ | Đích chứa được `Double` |
| `List<Number>` | `List<Object>` | Hợp lệ | `Object` chứa được `Number` |
| `List<Number>` | `List<Integer>` | Không hợp lệ | Nguồn có thể chứa `Double`, đích `Integer` không chứa được |
| `List<Object>` | `List<Number>` | Không hợp lệ | Nguồn có thể chứa kiểu không phải `Number` |
| `List<String>` | `List<Object>` | Hợp lệ | Mọi `String` đều là `Object` |
| `List<Integer>` | `List<String>` | Không hợp lệ | Không có `T` thích hợp đảm bảo an toàn ghi |

Bảng xét **khả năng biên dịch** cho lời gọi. Nó không bảo đảm implementation đích có hỗ trợ `add` tại runtime. Ví dụ `List<Number> numbers = List.of(1, 2)` có kiểu đích phù hợp, nhưng không cho phép thêm phần tử.

Một lỗi suy luận khác là tưởng rằng type argument `T` phải luôn bằng chính xác kiểu của nguồn hoặc đích. Trên thực tế, compiler tìm các ràng buộc thỏa cả phía `extends` và `super`. Không nên yêu cầu caller tự cast hai list về một kiểu chung chỉ để gọi một phương thức có wildcard chuẩn.

## 9. Các rủi ro runtime và giới hạn cần ghi rõ

### 9.1. Đích phải hỗ trợ thao tác thêm

`List<? super T>` chứng minh **kiểu phần tử có thể được nhận**, nhưng không bảo đảm `add` thành công. `List.of(...)`, `List.copyOf(...)` hoặc `Collections.unmodifiableList(...)` từ chối thay đổi; `Arrays.asList(...)` có kích thước cố định.

Do đó, API `appendCopy` phải công bố rằng đích cần là **danh sách thay đổi kích thước được**, chẳng hạn `ArrayList`. Nếu nguồn có `n` phần tử và phép thêm thành công, đích tăng `n` phần tử.

### 9.2. “Copy” ở đây là sao chép tham chiếu phần tử

`new ArrayList<>(source)` và `destination.add(value)` không sao chép sâu trạng thái một đối tượng `T` mutable. Nếu nguồn chứa các đối tượng mutable, nguồn và đích có thể cùng trỏ tới những đối tượng phần tử ấy. Đổi thuộc tính của một đối tượng bên nguồn có thể quan sát được qua phần tử tương ứng ở đích.

Để có **deep copy**, cần quy tắc sao chép từng loại phần tử, ví dụ truyền vào một hàm mapper/copy factory. Không nên gọi `appendCopy` là deep copy.

### 9.3. Không có tính nguyên tử hay đồng bộ tự động

Phiên bản minh họa kiểm tra null trước, tạo bản chụp, rồi lần lượt `add`. Nếu một lời gọi `add` về sau thất bại, các phần tử đã thêm trước đó **có thể vẫn nằm ở đích**. Vì vậy phương thức không hứa “hoặc thành công toàn bộ, hoặc không đổi gì”.

Bản chụp tạm cũng **không tự bảo đảm thread-safety** nếu một luồng khác đồng thời sửa nguồn trong lúc tạo snapshot. Cần cơ chế đồng bộ/quyền sở hữu phù hợp hoặc collection bảo đảm tính nhất quán theo yêu cầu.

### 9.4. `null` và kiểm soát dữ liệu

`Objects.requireNonNull` chỉ cấm **chính hai danh sách tham số** là null. Nếu source chứa một phần tử null, snapshot có thể chứa null và `destination.add(null)` sẽ phụ thuộc vào danh sách đích có chấp nhận null hay không. Muốn không cho dữ liệu null, hãy đặt hợp đồng và kiểm tra riêng.

### 9.5. Bảo toàn thứ tự

Hàm dùng `List` và `for-each`, nên các phần tử được thêm theo thứ tự duyệt của nguồn. Nếu không có luồng khác sửa nguồn trong khi sao chép, thứ tự các phần tử mới trong đích giống thứ tự các phần tử của source lúc tạo snapshot. Đích có thể đã có những phần tử trước đó; hàm không xóa chúng.

## 10. Thiết kế API theo mục đích sử dụng

Nếu cần **nối nguồn vào đích**, đặt tên thể hiện append, chẳng hạn `appendCopy`, `appendFrom` hoặc `appendAll`; dùng chữ ký:

```java
static <T> void appendCopy(
        List<? super T> destination,
        List<? extends T> source)
```

Nếu cần **ghi đè phần đầu danh sách đích**, dùng sẵn:

```java
Collections.copy(destination, source);
```

Khi chỉ cần **tạo list mới độc lập với cấu trúc nguồn**, không nhất thiết phải nhận một danh sách đích:

```java
static <T> List<T> mutableCopy(List<? extends T> source) {
    return new ArrayList<>(source);
}
```

Cách này tạo một `ArrayList<T>` mới, thay vì sửa list của người gọi. Đây cũng là **bản sao nông** đối với các phần tử kiểu tham chiếu. Với đầu vào phù hợp và yêu cầu danh sách không cho sửa, `List.copyOf(source)` là lựa chọn khác, nhưng phương thức ấy không chấp nhận phần tử null và không bảo đảm deep copy.

Nên chọn chữ ký/return type theo mục đích thực tế. Không nhất thiết dùng wildcard nếu API chỉ thao tác với đúng một kiểu cụ thể và không cần nhận các danh sách subtype/supertype khác nhau.

## 11. Những lỗi thường gặp

**Lỗi 1 — Dùng `List<T>` cho cả nguồn và đích.** Việc này quá hạn chế: `List<Integer>` và `List<Number>` không có cùng một đối số kiểu. Nếu chỉ đọc nguồn và chỉ thêm vào đích, `? extends T` và `? super T` cho phép API rộng hơn mà vẫn an toàn.

**Lỗi 2 — Đảo chiều wildcard.** `List<? super T> source` chỉ bảo đảm phần tử đọc ra là `Object`, còn `List<? extends T> destination` không chấp nhận thêm giá trị `T` bất kỳ. Vai trò producer/consumer phải xác định theo hướng dữ liệu.

**Lỗi 3 — Hiểu `Collections.copy` là nối thêm.** Nó ghi đè phần tử theo chỉ số và cần đích có ít nhất bằng số phần tử nguồn. Dung lượng `ArrayList` không đồng nghĩa số phần tử hiện có.

**Lỗi 4 — Duyệt và thêm vào cùng danh sách.** Dùng vòng foreach trực tiếp trên list khi list ấy cũng là đích có thể gây lỗi khi sửa cấu trúc trong lúc duyệt. Tạo snapshot trước khi thêm nếu muốn hỗ trợ tự nối.

**Lỗi 5 — Giả định wildcard biến đích thành mutable.** Generics chứng minh kiểu, không bảo đảm thao tác `add` được implementation hỗ trợ.

**Lỗi 6 — Dùng cast/raw type thay vì wildcard.** Cast có thể che lỗi kiểu đến runtime và làm hỏng ràng buộc phần tử.

**Lỗi 7 — Mong phần tử đích là bản sao độc lập hoàn toàn.** `appendCopy` sao chép tham chiếu, không clone sâu các đối tượng phần tử.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Tại sao `appendCopy(List<Number>, List<Integer>)` có thể biên dịch dù `List<Integer>` không phải subtype của `List<Number>`?

Vì compiler suy ra `T` phù hợp (chẳng hạn `Integer`) với hai wildcard. Nó lấy một `T` từ `List<? extends T>` rồi thêm chính `T` vào `List<? super T>`.

**Câu 2.** Khi nguồn là `List<Number>` chứa cả `Integer` và `Double`, có thể sao chép sang `List<Integer>` không?

Không; danh sách đích khai báo `Integer` không chứa an toàn mọi phần tử `Number`.

**Câu 3.** `new ArrayList<Number>(100)` có đủ chỗ cho `Collections.copy(dest, source)` với source 3 phần tử không?

Chưa chắc: constructor đó chỉ cấp capacity, `size()` vẫn bằng 0. Cần ít nhất ba phần tử thực đã tồn tại và đích hỗ trợ `set`.

**Câu 4.** Khi gọi `appendCopy(list, list)` với `list = [7, 8]`, phiên bản có snapshot cho kết quả gì?

`[7, 8, 7, 8]`. Snapshot chỉ chụp hai phần tử nguồn trước khi hàm bắt đầu thêm.

**Câu 5.** `List<? super Integer>` đảm bảo đọc phần tử dưới kiểu `Integer` không?

Không. Bởi đích có thể là `List<Number>` hay `List<Object>`, giá trị đọc ra chỉ bảo đảm dưới kiểu `Object`. Trong bài toán này, hàm **không cần đọc từ đích**.

**Câu 6.** Nếu đích là một `List.of(...)`, chữ ký generic có bảo đảm thêm được không?

Không. Compiler có thể chấp nhận quan hệ kiểu, nhưng runtime ném `UnsupportedOperationException` khi cố thay đổi danh sách không hỗ trợ sửa.

## Ghi nhớ

Một hàm sao chép theo PECS có dạng `<T> void copy(List<? super T> destination, List<? extends T> source)`: **nguồn sản xuất `T` nên dùng `extends`, đích tiêu thụ `T` nên dùng `super`**. Trong triển khai cần xác định rõ ngữ nghĩa **nối thêm** hay **ghi đè**; ví dụ `appendCopy` ở bài này nối thêm, chụp nguồn trước để hỗ trợ tự nối, còn `Collections.copy` ghi đè các vị trí sẵn có. Wildcard bảo đảm an toàn kiểu; quyền sửa danh sách, aliasing, deep copy và đồng bộ vẫn là các vấn đề runtime/thiết kế riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pecs-004">Khi cùng tham số vừa cung cấp vừa nhận dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Khi cùng tham số vừa cung cấp vừa nhận dữ liệu: áp dụng PECS thế nào?

## 1. Bài toán và quy tắc cốt lõi

Quy tắc **PECS** (*Producer Extends, Consumer Super*) giúp chọn wildcard trong Java Generics:

- Một tham số **cung cấp** phần tử để đọc theo kiểu `T` (*producer*) thường thích hợp với `? extends T`.
- Một tham số **nhận** phần tử kiểu `T` để ghi (*consumer*) thường thích hợp với `? super T`.

Nhưng nếu **cùng một tham số**, chẳng hạn cùng một `List`, vừa cung cấp phần tử để đọc vừa nhận phần tử cùng kiểu để ghi, không nên máy móc chọn một trong hai wildcard. Khi cần gắn chặt kiểu phần tử được đọc với kiểu phần tử sẽ ghi, hãy ưu tiên **tham số kiểu được đặt tên**:

```java
static <T> void replaceFirst(List<T> values, T replacement) {
    if (!values.isEmpty()) {
        values.set(0, replacement);
    }
}
```

Trong chữ ký này, `T` xuất hiện ở cả `List<T>` và `T replacement`, buộc kiểu của giá trị thay thế tương thích với kiểu phần tử của danh sách. Với những phép biến đổi lấy phần tử ra rồi ghi trở lại, có thể dùng `List<T>`, `UnaryOperator<T>` hoặc helper generic `<T>` để giữ cùng một kiểu.

**Nguyên tắc cần nhớ:** PECS là chỉ dẫn theo **vai trò đọc/ghi của tham số**, không phải quy tắc bắt buộc mọi tham số có `List` đều phải khai báo bằng wildcard.

## 2. Vì sao `? extends T` không đủ khi muốn ghi T?

Xét nguồn chỉ cần đọc:

```java
static double sum(List<? extends Number> source) {
    double total = 0;
    for (Number number : source) {
        total += number.doubleValue();
    }
    return total;
}
```

`source` có thể là `List<Integer>`, `List<Double>` hoặc `List<Number>`. Mọi phần tử đều đọc được dưới dạng `Number`.

Nhưng nếu cùng biến phải nhận một `Number` bất kỳ:

```java
static void wrong(List<? extends Number> values) {
    Number old = values.get(0); // Đúng về kiểu nếu chỉ số hợp lệ
    values.set(0, old);         // LỖI BIÊN DỊCH
    values.add(5);              // LỖI BIÊN DỊCH
}
```

`? extends Number` đại diện cho **một kiểu phần tử cụ thể nhưng chưa biết**. Một danh sách có thể thực sự là `List<Integer>`; giá trị `Number` nhận từ nơi khác có thể là `Double`. Do đó `set` hoặc `add` bằng `Number` bất kỳ không được bảo đảm an toàn.

Ngay cả khi giá trị `old` vừa được đọc từ chính danh sách, cách viết `values.set(0, old)` vẫn sai vì khi khai báo `Number old`, ta chỉ giữ được thông tin `Number`, không còn quan hệ chặt với kiểu capture chưa biết của `values`.

`values.add(null)` và `values.set(0, null)` là ngoại lệ **có thể biên dịch** vì `null` tương thích với kiểu tham chiếu chưa biết. Tuy nhiên, danh sách thực tế có thể không cho phép sửa đổi hoặc từ chối `null`; ngoại lệ này không giải quyết yêu cầu ghi một giá trị `T` hữu ích.

## 3. Vì sao `? super T` không đủ khi cần đọc đúng T?

Giới hạn dưới phù hợp với đích nhận giá trị:

```java
static void appendInteger(List<? super Integer> destination) {
    destination.add(10); // Đúng về kiểu
}
```

`destination` có thể là `List<Integer>`, `List<Number>` hoặc `List<Object>`. Mọi danh sách như vậy đều nhận được `Integer`.

Nhưng nếu phương thức cần đọc một `Integer` từ chính đích này:

```java
static void wrongRead(List<? super Integer> values) {
    Integer value = values.get(0); // LỖI BIÊN DỊCH
    Object valueAsObject = values.get(0); // Đúng nếu chỉ số hợp lệ
}
```

Danh sách có thể thực sự là `List<Object>` và chứa các giá trị không phải `Integer`. Vì vậy, phần tử đọc ra qua `? super Integer` chỉ được bảo đảm là `Object`.

Đổi từ `? extends T` sang `? super T` có thể giải quyết quyền **ghi T** nhưng đánh mất bảo đảm **đọc T**. Cả hai wildcard được thiết kế để mô tả **hướng sử dụng dữ liệu**; chúng không thay thế được một quan hệ kiểu chính xác cần duy trì ở cả hai chiều.

## 4. Giải pháp chính: dùng `List<T>` khi cần cùng một T

Khi giá trị được đọc và ghi phải thuộc **cùng một kiểu phần tử**, hãy đặt tên kiểu đó trong chữ ký:

```java
static <T> void replaceFirst(List<T> values, T replacement) {
    if (!values.isEmpty()) {
        values.set(0, replacement);
    }
}
```

Ví dụ:

```java
List<String> names = new ArrayList<>(List.of("An", "Binh"));
replaceFirst(names, "Chi"); // T là String

List<Integer> ages = new ArrayList<>(List.of(18, 20));
replaceFirst(ages, 21); // T là Integer
```

Phương thức generic là một **khuôn mẫu kiểu**: mỗi lời gọi suy luận một `T` tương thích với các đối số. Sự linh hoạt này khác với một tham số cố định `List<Object>`, vốn không nhận trực tiếp `List<String>` hoặc `List<Integer>` vì generic của Java mặc định bất biến.

Nếu cần viết phép biến đổi mà **giá trị mới bắt nguồn từ giá trị cũ**, dùng `UnaryOperator<T>`:

```java
import java.util.List;
import java.util.function.UnaryOperator;

static <T> void updateFirst(
        List<T> values,
        UnaryOperator<T> operation) {
    if (!values.isEmpty()) {
        T oldValue = values.get(0);
        T newValue = operation.apply(oldValue);
        values.set(0, newValue);
    }
}
```

`UnaryOperator<T>` có dạng `T -> T`, tức là nhận và trả về **cùng T**. Như vậy kiểu của kết quả được phép ghi trở lại `List<T>`.

Ví dụ:

```java
List<String> words = new ArrayList<>(List.of("java", "generics"));
updateFirst(words, String::toUpperCase); // [JAVA, generics]

List<Integer> scores = new ArrayList<>(List.of(3, 4));
updateFirst(scores, n -> n * 10);       // [30, 4]
```

Lưu ý: một `List<T>` có thể là danh sách không hỗ trợ sửa đổi (ví dụ `List.of(...)`). Chữ ký chỉ bảo đảm **an toàn kiểu ở compile-time**, không bảo đảm `set` hoạt động lúc chạy. Trong trường hợp danh sách không hỗ trợ `set`, phương thức có thể ném `UnsupportedOperationException`.

## 5. Khi chỉ có `List<?>`: dùng wildcard capture helper

Đôi khi API bên ngoài chỉ biết một danh sách chứa kiểu bất kỳ:

```java
List<?> unknown = new ArrayList<>(List.of("A", "B"));
```

Nếu cần đổi chỗ **những phần tử đã có**, ta không cần biết kiểu cụ thể, miễn mọi giá trị di chuyển vẫn thuộc **cùng danh sách** và giữ nguyên kiểu đó. Một cách diễn đạt an toàn là gọi helper generic:

```java
static <T> void swapFirstTwo(List<T> values) {
    if (values.size() < 2) {
        return;
    }

    T first = values.get(0);
    values.set(0, values.get(1));
    values.set(1, first);
}

static void reorderUnknown(List<?> values) {
    swapFirstTwo(values);
}
```

Ở lời gọi `swapFirstTwo(values)`, Java có thể **capture wildcard** thành một tham số kiểu `T` cho toàn bộ một lần gọi. Bên trong helper, `get` và `set` sử dụng đúng cùng `T`, vì vậy không cần ép kiểu và không có nguy cơ đưa một kiểu phần tử mới khác loại vào danh sách.

Một phép viết trực tiếp sau thường không biên dịch:

```java
static void badSwap(List<?> values) {
    Object first = values.get(0);
    values.set(0, first); // LỖI BIÊN DỊCH
}
```

`Object` quá rộng so với kiểu phần tử chưa biết. Ngay cả `values.set(0, values.get(0))` cũng có thể bị từ chối do các lần sử dụng wildcard được capture riêng và không liên hệ theo cách mà `set` yêu cầu.

Cũng có thể dùng tiện ích chuẩn:

```java
import java.util.Collections;
import java.util.List;

static void reverseUnknown(List<?> values) {
    Collections.reverse(values);
}

static void swapUnknown(List<?> values) {
    if (values.size() >= 2) {
        Collections.swap(values, 0, 1);
    }
}
```

`Collections.reverse` và `Collections.swap` đều nhận danh sách kiểu wildcard và triển khai thao tác giữ an toàn kiểu cho các phần tử đang có. Chúng vẫn cần danh sách hỗ trợ các phép thay đổi tương ứng; ví dụ, danh sách `List.of` không cho sửa.

**Khác biệt cần nhận ra:** helper có thể **di chuyển, sao chép lại hoặc biến đổi theo cùng T**; nó không cấp quyền chèn tùy ý một `Number` vào một `List<Integer>`.

## 6. Hai trường hợp hay nhầm: một danh sách hai vai trò và hai danh sách hai vai trò

Cách thiết kế phải dựa trên việc **cùng một đối tượng** vừa đọc/ghi hay có **hai tham số khác nhau**.

### Trường hợp A: cùng một danh sách vừa là nguồn vừa là đích

Ví dụ: thay phần tử đầu, hoán đổi hai phần tử, xoay thứ tự, chuẩn hóa chuỗi, áp dụng phép biến đổi `T -> T`.

```java
static <T> void swapFirstTwo(List<T> list) {
    if (list.size() >= 2) {
        T first = list.get(0);
        list.set(0, list.get(1));
        list.set(1, first);
    }
}
```

**Đề xuất:** `List<T>` cùng tham số kiểu đặt tên `T`. Nếu API chỉ cần đảo thứ tự mà không xử lý cụ thể phần tử, `List<?>` ở API bên ngoài cùng helper/tiện ích thư viện cũng có thể thích hợp.

Lý do: phương thức muốn bảo đảm phần tử lấy từ chính danh sách có thể đặt lại vào danh sách với **cùng kiểu**.

### Trường hợp B: hai danh sách khác nhau, một nguồn đọc và một đích ghi

Ví dụ: sao chép `Integer` từ `List<Integer>` sang `List<Number>` hoặc `List<Object>`.

```java
static <T> void copyAll(
        List<? extends T> source,
        List<? super T> destination) {
    for (T value : source) {
        destination.add(value);
    }
}
```

**Đề xuất:** áp dụng PECS theo từng tham số: `source` dùng `? extends T`, `destination` dùng `? super T`.

```java
List<Integer> integers = List.of(1, 2);
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();

copyAll(integers, numbers); // Đúng
copyAll(integers, objects); // Đúng
```

Nếu viết cứng cả hai bên thành `List<T>`:

```java
static <T> void restrictiveCopy(List<T> src, List<T> dest) {
    dest.addAll(src);
}
```

thì cuộc gọi `restrictiveCopy(List<Integer>, List<Number>)` **không hợp lệ**, vì `List<Integer>` và `List<Number>` không có cùng đối số kiểu `T`. Đây là trường hợp dùng wildcard PECS làm API linh hoạt hơn mà vẫn an toàn.

**Không nên nhầm** phương thức `copyAll` vừa viết với `Collections.copy(destination, source)`: `copyAll` ở đây nối thêm phần tử vào cuối đích, còn `Collections.copy` thay giá trị ở các vị trí đã tồn tại và yêu cầu danh sách đích có đủ phần tử.

Ngoài ra, nếu `source` và `destination` là **cùng một danh sách**, không nên lặp `for-each` qua nó đồng thời `add` vào nó: nhiều triển khai (như `ArrayList`) sẽ phát hiện thay đổi cấu trúc khi đang lặp và ném `ConcurrentModificationException`. Hãy xử lý trường hợp aliasing nếu API cho phép nguồn và đích trùng nhau, chẳng hạn sao chép snapshot trước khi thêm.

## 7. Một tham số vừa đọc và thêm giá trị mới: xác định nghĩa vụ kiểu

Không phải bất cứ trường hợp “đọc rồi ghi” nào cũng chỉ cần `List<T>` mà không cần thiết kế thêm. Hãy xác định **giá trị được ghi đến từ đâu**:

| Tình huống | Kiểu tham số nên dùng | Vì sao |
| --- | --- | --- |
| Chỉ đọc phần tử dưới dạng `T` | `List<? extends T>` | Nguồn có thể có kiểu phần tử hẹp hơn `T` |
| Chỉ nhận giá trị `T` để ghi | `List<? super T>` | Đích có thể có kiểu phần tử rộng hơn `T` |
| Đọc và ghi lại phần tử **cùng kiểu** trong một danh sách | `List<T>` với helper `<T>` khi cần | Giữ liên hệ đúng kiểu giữa `get` và `set` |
| Đọc `T`, áp dụng `T -> T`, rồi ghi lại | `List<T>`, `UnaryOperator<T>` | Kết quả biến đổi luôn đúng `T` |
| Chỉ sắp xếp lại các phần tử hiện có, kiểu thực tế không quan trọng | `List<?>` + `Collections.swap/reverse` hoặc helper `<T>` | Dùng wildcard capture, không thêm kiểu lạ |
| Chuyển phần tử giữa nguồn và đích riêng biệt | `List<? extends T>` và `List<? super T>` | PECS cho đúng mỗi vai trò |
| Thay phần tử bằng giá trị cụ thể kiểu `Number` | Danh sách phải thực sự cho phép nhận `Number`, ví dụ `List<Number>` hoặc `List<? super Number>` | Danh sách `List<Integer>` không nhận được `Double` |

Cần phân biệt **một tham số kiểu `T`** (đại diện cho một kiểu và liên hệ nhiều vị trí) với **wildcard `?`** (kiểu phần tử không biết tại vị trí dùng). Một phương thức generic có thể vừa khai báo `T` vừa dùng wildcard ở những tham số có vai trò riêng biệt.

### 7.1. Giới hạn `T extends Number` có giải quyết được trường hợp vừa đọc vừa ghi?

Có thể giới hạn `T` nếu thuật toán thực sự cần chức năng của `Number`:

```java
static <T extends Number> T first(List<T> values) {
    return values.get(0);
}
```

Nếu muốn **thay** phần tử bằng giá trị `T`, vẫn phải dùng đúng `T`:

```java
static <T extends Number> void replaceFirst(
        List<T> values,
        T replacement) {
    if (!values.isEmpty()) {
        values.set(0, replacement);
    }
}
```

Việc có `<T extends Number>` **không** cho phép thêm một `Double` tùy ý vào `List<T>`, vì `T` có thể là `Integer`. Chữ ký `List<? extends T>` cũng không cho thêm `T` tùy ý vì kiểu phần tử của danh sách có thể hẹp hơn `T`.

### 7.2. Không cần wildcard chỉ để nhận nhiều kiểu List

Một phương thức khai báo `<T> void process(List<T>)` có thể được gọi với cả `List<String>` lẫn `List<Integer>`, mỗi lần suy luận `T` riêng. Do đó không phải dùng `List<?>` chỉ để đạt khả năng nhận nhiều kiểu danh sách.

```java
static <T> void keepSameType(List<T> values) {
    if (!values.isEmpty()) {
        T current = values.get(0);
        values.set(0, current);
    }
}
```

Ví dụ này dùng `set` với chính giá trị cũ để minh họa tính nhất quán kiểu; trong mã thực tế nên thực hiện phép biến đổi có ích hoặc không gọi `set` nếu không cần.

## 8. Ví dụ Java 17+ đầy đủ

Lưu tệp `PecsBothDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public class PecsBothDemo {
    // Một List vừa cung cấp T vừa nhận lại T.
    static <T> void replaceFirst(
            List<T> values,
            UnaryOperator<T> operation) {
        if (!values.isEmpty()) {
            values.set(0, operation.apply(values.get(0)));
        }
    }

    // Helper sử dụng cùng T cho hai thao tác get và set.
    static <T> void swapFirstTwo(List<T> values) {
        if (values.size() >= 2) {
            T first = values.get(0);
            values.set(0, values.get(1));
            values.set(1, first);
        }
    }

    // API ngoài không cần biết kiểu phần tử cụ thể.
    static void reorderUnknown(List<?> values) {
        swapFirstTwo(values);
    }

    // Hai tham số có vai trò tách biệt: producer và consumer.
    static <T> void copyAll(
            List<? extends T> source,
            List<? super T> destination) {
        for (T value : source) {
            destination.add(value);
        }
    }

    public static void main(String[] args) {
        List<String> words =
                new ArrayList<>(List.of("cat", "dog", "bird"));
        replaceFirst(words, String::toUpperCase);
        System.out.println("Replace: " + words);

        List<?> unknownWords = words;
        reorderUnknown(unknownWords);
        System.out.println("Swap: " + words);

        List<Integer> scores = new ArrayList<>(List.of(2, 4));
        replaceFirst(scores, x -> x * 10);
        System.out.println("Numbers: " + scores);

        List<Number> numbers = new ArrayList<>();
        copyAll(scores, numbers);
        System.out.println("Copy to numbers: " + numbers);

        List<Object> objects = new ArrayList<>();
        copyAll(words, objects);
        System.out.println("Copy to objects: " + objects);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 PecsBothDemo.java
java PecsBothDemo
```

Kết quả chính xác:

```text
Replace: [CAT, dog, bird]
Swap: [dog, CAT, bird]
Numbers: [20, 4]
Copy to numbers: [20, 4]
Copy to objects: [dog, CAT, bird]
```

Diễn giải:

1. `replaceFirst` đọc `String` và ghi lại `String` sau chuyển thành chữ hoa.
2. `reorderUnknown` nhận `List<?>` rồi nhờ helper generic capture kiểu thực tế; hoán đổi được mà không ép kiểu.
3. Cùng phương thức `replaceFirst` nhận `List<Integer>` với phép biến đổi `Integer -> Integer`, chứng minh `T` được suy luận theo từng lời gọi.
4. `copyAll(scores, numbers)` dùng `? extends T` cho nguồn `Integer`, `? super T` cho đích `Number`.
5. `copyAll(words, objects)` cũng an toàn bởi `List<Object>` nhận được các phần tử `String`.

Mọi danh sách trong ví dụ đều là `ArrayList` có thể sửa đổi ở nơi thuật toán cần ghi, nên kết quả không phụ thuộc một thao tác ghi bị cấm bởi `List.of`.

## 9. Những lỗi thường gặp

### 9.1. Cố dùng `? extends T` khi cần cập nhật phần tử

```java
static void incorrect(List<? extends Number> values) {
    Number value = values.get(0);
    values.set(0, value); // Không biên dịch
}
```

**Sửa:** đặt cùng một `T` cho toàn bộ quan hệ đọc/ghi, nếu cần thao tác trên kiểu phần tử thực tế:

```java
static <T extends Number> void correct(List<T> values) {
    if (!values.isEmpty()) {
        T value = values.get(0);
        values.set(0, value);
    }
}
```

Ví dụ `correct` thể hiện tính an toàn kiểu; việc gán lại cùng giá trị trong thực tế không tạo thay đổi nghiệp vụ.

### 9.2. Cố đọc T từ `? super T`

```java
static void incorrect(List<? super Integer> values) {
    Integer first = values.get(0); // Không biên dịch
}
```

**Sửa:** dùng `List<Integer>` hoặc `<T> List<T>` nếu cần đúng kiểu khi đọc; hoặc đọc thành `Object` nếu API chỉ cần xử lý ở mức `Object`.

### 9.3. Khai báo hai tham số cùng `List<T>` một cách không cần thiết

```java
static <T> void copy(List<T> source, List<T> target) {
    target.addAll(source);
}
```

Chữ ký này chặt hơn yêu cầu thực tế nếu nguồn và đích có thể khác tham số kiểu theo hướng an toàn (ví dụ `Integer` sang `Number`). Nên tách vai trò bằng `? extends T` và `? super T` khi cần.

### 9.4. Dùng raw type hoặc unchecked cast để đưa giá trị mới vào List<?>

```java
// Mã nguy hiểm, không nên sử dụng:
@SuppressWarnings({"rawtypes", "unchecked"})
static void unsafe(List<?> values) {
    List raw = values;
    raw.add("not necessarily correct");
}
```

Nếu `values` đang trỏ tới `List<Integer>`, lệnh trên có thể đưa `String` vào danh sách. Lỗi chỉ có thể xuất hiện sau đó khi nơi khác đọc và ép phần tử thành `Integer`. Đây là **heap pollution**, không phải cách áp dụng PECS.

### 9.5. Tưởng List<T> đồng nghĩa có thể sửa đổi

```java
List<String> immutable = List.of("A", "B");
swapFirstTwo(immutable); // Biên dịch nhưng có thể ném UnsupportedOperationException
```

Hệ thống kiểu generics không bảo đảm khả năng thay đổi nội dung của một `List`. Nếu phương thức có hợp đồng cần sửa dữ liệu, tài liệu API phải nêu rõ yêu cầu danh sách hỗ trợ `set`, `add` hoặc `remove` tương ứng; có thể tạo một `ArrayList` mới nếu không được phép sửa nguồn.

## 10. Tự kiểm tra

Giả sử đã `import java.util.*;`. Hãy xác định trường hợp nào **biên dịch**, thao tác nào có thể **lỗi runtime**, và sửa chữ ký khi cần.

**A — Cùng danh sách vừa đọc vừa ghi**

```java
static <T> void swap(List<T> list) {
    if (list.size() >= 2) {
        T first = list.get(0);
        list.set(0, list.get(1));
        list.set(1, first);
    }
}
```

**B — Dùng wildcard extends với Number**

```java
static void change(List<? extends Number> list) {
    Number n = list.get(0);
    list.set(0, n);
}
```

**C — Dùng wildcard super với Integer**

```java
static void change(List<? super Integer> list) {
    Integer n = list.get(0);
    list.add(n);
}
```

**D — Copy hai danh sách theo PECS**

```java
static <T> void copy(
        List<? extends T> source,
        List<? super T> target) {
    for (T value : source) {
        target.add(value);
    }
}
List<Integer> source = List.of(1, 2);
List<Number> target = new ArrayList<>();
copy(source, target);
```

**E — List<?> gọi helper có type parameter**

```java
static <T> void swapFirstTwo(List<T> list) {
    if (list.size() >= 2) {
        T first = list.get(0);
        list.set(0, list.get(1));
        list.set(1, first);
    }
}
List<?> unknown = new ArrayList<>(List.of("A", "B"));
swapFirstTwo(unknown);
```

Các ví dụ là **mảnh mã minh họa độc lập**: phải đặt phương thức `static` trong một lớp, và các lệnh gọi/khai báo biến tại vị trí hợp lệ; không ghép tất cả phương thức trùng tên vào cùng một lớp rồi suy diễn rằng đó là một tệp hoàn chỉnh.

**Đáp án:**

- **A:** Biên dịch. `List<T>` giữ cùng kiểu cho `get` và `set`; runtime có thể từ chối `set` nếu danh sách không hỗ trợ sửa đổi.
- **B:** Không biên dịch. `Number` là giới hạn trên, không chắc tương thích với kiểu phần tử ẩn của `List<? extends Number>`.
- **C:** Không biên dịch ở phép gán `get` cho `Integer`: `List<? super Integer>` chỉ bảo đảm đọc ra `Object`. Phép `add` một `Integer` đã biết là hợp lệ nếu tách riêng.
- **D:** Biên dịch và chạy thành công với `ArrayList` đích, kết quả `[1, 2]`.
- **E:** Biên dịch và chạy với `ArrayList`; generic method capture wildcard để bảo đảm cùng kiểu phần tử được đọc và ghi.

## 11. Checklist chọn chữ ký API

Khi đứng trước một tham số generic cần đọc và/hoặc ghi, hãy xác định:

1. **Đọc ra giá trị thuộc kiểu nào?** Nếu chỉ cần giao diện của kiểu cha `T`, `? extends T` có thể đủ.
2. **Ghi vào giá trị từ đâu?** Nếu đưa một `T` từ bên ngoài vào, `? super T` là ứng viên.
3. **Cùng một danh sách phải đọc và ghi đúng kiểu thực tế không?** Dùng `List<T>` với tham số kiểu `<T>`, hoặc helper capture wildcard.
4. **Có hai danh sách nguồn/đích riêng biệt không?** Áp dụng PECS cho từng tham số riêng.
5. **Đối tượng có sửa được không?** Kiểm tra hợp đồng runtime của `List`, không suy từ `T`, `extends` hoặc `super`.
6. **Nguồn và đích có thể cùng tham chiếu một danh sách không?** Cân nhắc snapshot/thuật toán không lặp và thêm đồng thời để tránh vấn đề thay đổi cấu trúc.

### Kết luận cần ghi nhớ

Khi **cùng một tham số vừa cung cấp vừa nhận các phần tử cùng kiểu**, dùng **`List<T>` và một tham số kiểu `<T>` được đặt tên** là cách thông dụng nhất để giữ quan hệ kiểu chính xác. Nếu phía ngoài chỉ có `List<?>` hoặc `List<? extends T>`, có thể chuyển việc thao tác phần tử đang có vào helper generic để **capture** kiểu ẩn một cách an toàn. Nếu **hai tham số có hướng dữ liệu tách biệt**, PECS vẫn thích hợp: **`? extends T` ở nguồn, `? super T` ở đích**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pecs-005">Khi dùng wildcard và khi phải đặt tên tham số kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Khi dùng wildcard và khi phải đặt tên tham số kiểu trong Java Generics

## 1. Bài toán thiết kế kiểu: `?` hay `<T>`?

Trong Java generics, **wildcard** `?` biểu diễn một kiểu chưa biết, còn **type parameter có tên** như `T`, `E`, `K` hoặc `V` cho phép nhiều vị trí trong chữ ký phương thức cùng **tham chiếu đến một quan hệ kiểu**. Cả hai có thể làm API linh hoạt, nhưng chúng diễn đạt những thông tin khác nhau.

Ví dụ, một phương thức chỉ cần đếm số phần tử:

~~~java
static int count(List<?> values) {
    return values.size();
}
~~~

Ở đây `?` là đủ: không có lý do gì để đặt tên cho kiểu phần tử. Một chữ ký `static <T> int count(List<T> values)` cũng có thể dùng, nhưng `T` không mang thêm quan hệ hữu ích nào.

Ngược lại, một phương thức lấy phần tử đầu và **trả về đúng kiểu phần tử mà caller cung cấp** cần `T`:

~~~java
static <T> T first(List<T> values) {
    return values.get(0);
}
~~~

Với `List<String>`, kết quả có kiểu `String`; với `List<Integer>`, kết quả có kiểu `Integer`. Thay `T` bằng `?` sẽ mất mối liên hệ giữa **kiểu phần tử đầu vào** và **kiểu giá trị trả về**.

Phần còn lại của bài sẽ giúp bạn:

- Chọn đúng `?`, `? extends T` và `? super T` theo hướng dữ liệu của quy tắc **PECS** (*Producer Extends, Consumer Super*).
- Nhận biết thời điểm cần khai báo `<T>` hoặc `<T, R>` để diễn tả quan hệ giữa nhiều vị trí.
- Kết hợp wildcard và type parameter trong **cùng một chữ ký**, thay vì xem chúng là hai lựa chọn loại trừ nhau.
- Hiểu vì sao một phương thức public nhận `List<?>` đôi khi phải gọi một helper `<T>` để giữ **wildcard capture**.
- Tránh các chữ ký quá chặt, quá rộng hoặc buộc caller phải dùng raw type/unchecked cast.

## 2. Cơ chế kiểu: “không cần biết tên” và “cần gọi cùng một tên”

Xem ba chữ ký:

~~~java
static void showAll(List<?> values) { /* ... */ }

static double sum(List<? extends Number> values) { /* ... */ }

static <T> T selectFirst(List<T> values) { return values.get(0); }
~~~

Các đoạn này **minh họa chữ ký**, phải đặt trong một lớp và import `java.util.List` nếu cần biên dịch độc lập.

- `List<?>`: **một kiểu phần tử chưa biết**; mọi phần tử có thể đọc như `Object`. Hữu ích khi thuật toán không cần API riêng của phần tử.
- `List<? extends Number>`: **một subtype chưa biết của `Number`**; mọi phần tử đọc được như `Number`, nên có thể gọi `doubleValue()`.
- `<T> T selectFirst(List<T>)`: đặt tên `T` ở cả input và output. Caller truyền `List<String>` thì được `String`, không cần cast.

Có thể đọc `?` như “kiểu cụ thể là gì không quan trọng với lời hứa của API”. Có thể đọc `T` như “kiểu cụ thể có thể khác nhau giữa những lời gọi, nhưng **các vị trí nhắc tới `T` trong cùng một lời gọi phải thỏa những ràng buộc của `T`**”.

**Lưu ý về chữ ký:** `?` là *type argument* bên trong `List<?>`, `Class<?>`…; bạn **không thể** dùng dấu `?` độc lập làm kiểu trả về theo dạng `static ? first(...)`. Muốn trả về giá trị có kiểu liên hệ với input, cần type variable có tên hoặc một kiểu cụ thể.

## 3. Dùng wildcard khi không cần duy trì danh tính kiểu qua các vị trí

### 3.1. Chỉ quan sát dữ liệu bằng API của `Object`: `?`

~~~java
static void printItems(List<?> items) {
    for (Object item : items) {
        System.out.println(item);
    }
}
~~~

`printItems(List.of("A", "B"))` và `printItems(List.of(1, 2))` đều hợp lệ. Phương thức không quan tâm `String` hay `Integer`; mọi object đều có thể in ra thông qua `println(Object)`.

Biến thể có type parameter sau đây không tăng tính an toàn:

~~~java
static <T> void printItemsWithT(List<T> items) {
    for (T item : items) {
        System.out.println(item);
    }
}
~~~

Nếu `T` không xuất hiện ở **vị trí nào khác có ý nghĩa** và thân phương thức không yêu cầu giữ danh tính kiểu, wildcard rõ ý định hơn. Đây là **quy tắc thiết kế**; không phải compiler cấm khai báo type parameter dùng một lần.

### 3.2. Chỉ đọc theo một upper bound: `? extends T`

~~~java
static double average(List<? extends Number> numbers) {
    if (numbers.isEmpty()) {
        throw new IllegalArgumentException("empty list");
    }
    double sum = 0;
    for (Number number : numbers) {
        sum += number.doubleValue();
    }
    return sum / numbers.size();
}
~~~

Ví dụ có thể nhận `List<Integer>`, `List<Long>`, `List<Double>`, v.v. Vì thuật toán chỉ cần phương thức có ở `Number` và **không trả về giá trị dưới kiểu subtype gốc**, ta không cần thêm `<T extends Number>`.

So sánh chữ ký:

~~~java
// Đủ rõ khi chỉ dùng Number để đọc:
static double average(List<? extends Number> numbers) { /* ... */ }

// Hợp lệ nhưng T không phục vụ mối liên hệ nào:
static <T extends Number> double averageWithT(List<T> numbers) { /* ... */ }
~~~

Hai khối trên chỉ so sánh **chữ ký** (thân `/* ... */` không cung cấp implementation hoàn chỉnh của phương thức trả `double`). Khi viết code thực, dùng implementation `average` ở phía trên.

**Cơ chế:** với `List<? extends Number>`, compiler chỉ biết phần tử có một kiểu capture `CAP <: Number`. Đọc được `Number`, nhưng không thể `add(Integer)` hay `add(Double)` tùy ý vì `CAP` có thể là subtype khác. Wildcard `extends` phù hợp với nơi **cung cấp dữ liệu để phương thức đọc**.

### 3.3. Chỉ ghi vào một đích: `? super T`

~~~java
static void addDefaults(List<? super Integer> target) {
    target.add(10);
    target.add(20);
}
~~~

Có thể truyền `List<Integer>`, `List<Number>` hoặc `List<Object>`. Cả ba đều chấp nhận giá trị `Integer`.

Với `List<? super Integer>`, compiler **chỉ bảo đảm** kiểu đọc `get(...)` là `Object`, vì danh sách thật có thể là `List<Object>` đã chứa giá trị khác. Hợp đồng ở đây tập trung vào **ghi dữ liệu**. Không cần khai báo `<T extends Integer>` chỉ để thêm các giá trị `Integer` đã biết.

### 3.4. Áp dụng PECS theo vai trò của tham số

| Vai trò mà phương thức cần | Chữ ký thường phù hợp | Lý do |
| --- | --- | --- |
| Chỉ đếm, in hoặc xử lý dưới dạng `Object` | `List<?>` | Không cần biết loại phần tử. |
| Đọc các `Number` | `List<? extends Number>` | Mỗi phần tử chắc chắn là một `Number`. |
| Thêm `Integer` vào danh sách đích | `List<? super Integer>` | Mọi supertype của `Integer` đều nhận được `Integer`. |
| Đọc và ghi lại phần tử dưới **cùng kiểu ẩn** | `List<?>` + helper `<T>`, hoặc `List<T>` | Cần giữ capture type bên trong thao tác. |
| Trả lại chính kiểu phần tử của input | `<T> T first(List<T>)` | Quan hệ input/output đòi tên chung `T`. |
| Nối nguồn vào đích với kiểu liên hệ | `<T> void append(List<? extends T>, List<? super T>)` | `T` nối hai vị trí, wildcard thể hiện producer/consumer. |

PECS xem xét **luồng dữ liệu phần tử đối với phương thức**: tham số là *producer* khi cung cấp phần tử để đọc, *consumer* khi nhận phần tử để ghi. Nó không có nghĩa `? extends` biến collection thành immutable hay `? super` cấm đọc; những thao tác khác vẫn theo API/implementation cụ thể.

## 4. Đặt tên `T` khi phải bảo toàn quan hệ giữa tham số và kết quả

Một API lấy phần tử đầu:

~~~java
static Object firstObject(List<?> values) {
    return values.get(0);
}
~~~

Nó chấp nhận mọi loại danh sách, nhưng caller chỉ nhận được `Object`. Nếu cần trả về loại gốc, dùng:

~~~java
static <T> T firstTyped(List<T> values) {
    return values.get(0);
}

String s = firstTyped(List.of("Lan", "Minh"));
Integer n = firstTyped(List.of(2, 4, 6));
~~~

Đây là ví dụ có các dòng lời gọi đặt trong **phương thức hoặc khối Java hợp lệ**; cần import `List`. Không có cast không kiểm tra. Compiler suy luận `T = String` hoặc `T = Integer` từ argument và ngữ cảnh.

Vậy có nên viết:

~~~java
static <T> T firstFromExtends(List<? extends T> values) {
    return values.get(0);
}
~~~

Cũng **hợp lệ**. Chữ ký này cho phép list có phần tử subtype của `T`; nó còn cho phép type inference chọn `T` rộng hơn dựa vào ngữ cảnh. Sự khác nhau giữa `List<T>` và `List<? extends T>` phải được cân nhắc theo contract, không theo cảm giác “extends luôn tốt hơn”.

Cần lưu ý trường hợp list rỗng: cả `firstTyped` và `firstFromExtends` ở đây đều ném `IndexOutOfBoundsException` nếu gọi `get(0)` khi không có phần tử. Generics chỉ kiểm soát **kiểu**, không bảo đảm list có ít nhất một phần tử.

### 4.1. Khi kết quả có nhiều kiểu quan hệ

Nếu hàm chuyển `T` sang `R`, hai tên có ý nghĩa độc lập:

~~~java
static <T, R> List<R> mapAll(
        List<? extends T> source,
        java.util.function.Function<? super T, ? extends R> mapper) {
    List<R> output = new ArrayList<>();
    for (T value : source) {
        output.add(mapper.apply(value));
    }
    return output;
}
~~~

Đoạn này là **phương thức minh họa**, đặt trong lớp có import `java.util.List` và `java.util.ArrayList` để biên dịch. Nó cho thấy:

- `T` là kiểu được đọc từ danh sách nguồn.
- `R` là kiểu kết quả tạo ra.
- `? extends T` cho phép source cung cấp subtype của `T`.
- `Function<? super T, ? extends R>` nhận được giá trị `T` và trả về `R` hoặc subtype của `R`.
- `List<R>` thể hiện đầu ra định kiểu chính xác theo `R`.

Sử dụng wildcard **bên trong** khai báo generic `Function<...>` không làm `T`, `R` dư thừa; ngược lại, những tên này giúp nối loại dữ liệu giữa source, mapper và kết quả.

## 5. Đặt tên `T` khi phải diễn đạt quan hệ giữa nhiều tham số

Giả sử ta muốn sao chép từng phần tử **bằng cách thêm vào cuối danh sách đích**. Nguồn có thể chứa `Integer`, trong khi đích được khai báo `List<Number>`.

Dùng một method type parameter để nói rõ rằng nguồn tạo ra `T` và đích nhận được `T`:

~~~java
static <T> void appendAll(
        List<? extends T> source,
        List<? super T> target) {
    for (T item : source) {
        target.add(item);
    }
}
~~~

Ví dụ hợp lệ:

~~~java
List<Integer> source = List.of(1, 2, 3);
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();

appendAll(source, numbers);
appendAll(source, objects);
~~~

**Tại sao không chỉ có wildcard không tên?**

~~~java
// KHÔNG biên dịch: hai dấu ? biểu diễn hai kiểu chưa biết độc lập.
static void badAppend(List<?> source, List<?> target) {
    for (Object element : source) {
        target.add(element); // Không thể xác nhận element có đúng kiểu của target.
    }
}
~~~

`List<?>` không cho compiler biết kiểu của source và target có liên hệ gì. Dùng `<T>` làm **cầu nối** giữa hai tham số. Hai wildcard `? extends T`, `? super T` khiến chữ ký vừa an toàn, vừa không quá hạn chế.

### 5.1. Vì sao `List<T>` ở cả hai phía thường quá chặt?

~~~java
// Có thể quá chặt cho nhu cầu producer -> consumer:
static <T> void appendStrict(List<T> source, List<T> target) {
    target.addAll(source);
}
~~~

Với `List<Integer>` và `List<Number>`, lời gọi `appendStrict(ints, numbers)` không biên dịch: generic `List<T>` là invariant, và một `T` không thể đồng thời bằng `Integer` và `Number` cho hai parameterized types ấy.

Đối với thao tác chuyển phần tử, điều cần chứng minh không phải **hai danh sách có cùng type argument**, mà là **mọi phần tử từ source đều gán được cho target**. `<T> List<? extends T>` và `List<? super T>` diễn đạt đúng sự phụ thuộc đó.

### 5.2. Đối chiếu với API chuẩn

`java.util.Collections.copy` dùng:

~~~java
public static <T> void copy(
        List<? super T> dest,
        List<? extends T> src);
~~~

Đây là **chữ ký API**, không phải định nghĩa phương thức có body để tự biên dịch. `copy` **ghi đè các phần tử ở vị trí tương ứng** của đích và yêu cầu đích có đủ phần tử; không dùng giống thao tác `appendAll` thêm vào cuối. Cả hai vẫn chia sẻ cùng kiểu hợp đồng producer/consumer.

Phương thức chuẩn `Collection<E>.addAll(Collection<? extends E> c)` cũng minh họa quy tắc: `E` đã được **đặt tên ở cấp interface** là kiểu phần tử của đích; vì thế tham số source chỉ cần wildcard `? extends E`, không phải khai báo thêm `<S extends E>` dùng một lần.

## 6. Trường hợp đặc biệt: wildcard capture buộc dùng helper `<T>`

Không phải lúc nào một API nhận `List<?>` cũng có thể hoàn thành thao tác trong cùng thân phương thức. Có những thao tác cần **lấy một phần tử và ghi lại nó vào chính collection đó**.

Ví dụ thử hoán đổi phần tử đầu và cuối:

~~~java
// KHÔNG biên dịch:
static void badSwap(List<?> list) {
    list.set(0, list.get(list.size() - 1));
}
~~~

Thoạt nhìn đây là thao tác an toàn vì đọc và ghi vào **cùng một list**. Tuy nhiên mỗi biểu thức liên quan tới kiểu phần tử không tên có thể được compiler xử lý bằng các capture variables khác nhau, nên compiler không thể bảo đảm phép truyền giá trị như cách code ở trên.

Thiết kế đúng là **public API dùng wildcard để nhận bất kỳ list**, còn helper private đặt tên cho capture type:

~~~java
static void swapEnds(List<?> values) {
    swapEndsCaptured(values);
}

private static <T> void swapEndsCaptured(List<T> values) {
    if (values.size() < 2) {
        return;
    }
    int lastIndex = values.size() - 1;
    T first = values.get(0);
    values.set(0, values.get(lastIndex));
    values.set(lastIndex, first);
}
~~~

Ở lời gọi `swapEndsCaptured(values)`, type inference và capture conversion cho phép Java đặt tên tạm cho kiểu phần tử duy nhất của **cùng list** thành `T` trong helper. Các lệnh `get`/`set` từ đây dùng chung `T` và không cần cast.

Đây là khác biệt giữa:

- **Bề mặt API:** caller không cần biết kiểu phần tử nên dùng `List<?>`.
- **Implementation:** khi thuật toán phải di chuyển những giá trị cùng kiểu phần tử qua nhiều biểu thức, helper `<T>` có ích để compiler biểu diễn quan hệ ấy.

Nếu caller đưa vào `List.of(...)` bất biến thì phép `set` ném `UnsupportedOperationException` lúc chạy. Ví dụ hoán đổi phải nhận list mutable (như `ArrayList`). Type safety và mutability là hai hợp đồng khác nhau.

## 7. Chương trình Java 17+ hoàn chỉnh, biên dịch và chạy được

Tạo tệp **WildcardOrTypeParameterDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class WildcardOrTypeParameterDemo {
    static double average(List<? extends Number> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("empty list");
        }
        double sum = 0.0;
        for (Number value : values) {
            sum += value.doubleValue();
        }
        return sum / values.size();
    }

    static String summary(List<?> values) {
        return "size=" + values.size();
    }

    static <T> T first(List<T> values) {
        return values.get(0);
    }

    static <T> void appendAll(List<? extends T> source, List<? super T> target) {
        for (T item : source) {
            target.add(item);
        }
    }

    static void swapEnds(List<?> values) {
        swapEndsCaptured(values);
    }

    private static <T> void swapEndsCaptured(List<T> values) {
        if (values.size() < 2) {
            return;
        }
        int lastIndex = values.size() - 1;
        T first = values.get(0);
        values.set(0, values.get(lastIndex));
        values.set(lastIndex, first);
    }

    public static void main(String[] args) {
        List<Integer> ids = List.of(2, 4, 6);
        System.out.println("average: " + average(ids));
        System.out.println("summary: " + summary(ids));

        String name = first(List.of("Lan", "Minh"));
        System.out.println("first: " + name);

        List<Number> destination = new ArrayList<>(List.of(1.5));
        appendAll(ids, destination);
        System.out.println("copied: " + destination);

        List<String> names = new ArrayList<>(List.of("A", "B", "C"));
        swapEnds(names);
        System.out.println("swapped: " + names);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -Xlint:all WildcardOrTypeParameterDemo.java
java WildcardOrTypeParameterDemo
~~~

**Stdout chính xác**:

~~~text
average: 4.0
summary: size=3
first: Lan
copied: [1.5, 2, 4, 6]
swapped: [C, B, A]
~~~

Bài này đã được biên dịch với `javac --release 17 -Xlint:all` và chạy thành công, không có unchecked warning.

Từng phần giải thích một lựa chọn thiết kế:

1. `average` chỉ đọc `Number` nên dùng `? extends Number`.
2. `summary` không cần kiểu phần tử, nên dùng `?`.
3. `first` trả về đúng kiểu phần tử, nên cần type parameter `T`.
4. `appendAll` truyền `T` từ source sang target, nên cần `T` nối hai wildcard.
5. `swapEnds` nhận bất cứ `List<?>` nào, nhưng helper dùng `T` để giữ quan hệ giữa hai phần tử của cùng list.

## 8. Những tình huống cần kiểm tra kỹ trước khi chọn

### 8.1. Khi một container vừa cần đọc vừa cần ghi dữ liệu cùng kiểu

Nếu API cần lấy `T` từ một collection, biến đổi hoặc trao đổi rồi ghi **lại các `T` cùng kiểu**, một `List<T>` có tên hoặc helper capture thường rõ ràng hơn `List<? extends T>` / `List<? super T>`. Quy tắc “producer extends, consumer super” không nhằm ép wildcard vào mọi thao tác khi cùng một biến vừa producer vừa consumer.

Ví dụ:

~~~java
static <T> void replaceFirst(List<T> list, T value) {
    list.set(0, value);
}
~~~

`T` bảo đảm `value` phù hợp với phần tử của `list`. Cần lưu ý `set` có thể ném `IndexOutOfBoundsException` nếu list rỗng hoặc `UnsupportedOperationException` nếu không sửa được.

### 8.2. Type parameter một lần không tự động sai

Nguyên tắc “nếu `T` chỉ được nhắc một lần thì dùng wildcard” là heuristic **thiết kế chữ ký**, không phải quy tắc cú pháp bắt buộc. Có những lý do ngoại lệ: type parameter dùng trong bound của type parameter khác, inference phức tạp, interface contract cần giữ tương thích, hoặc triển khai nội bộ cần tên cho capture type.

Ví dụ, `<T extends Number> void observe(List<T>)` có thể được đơn giản hóa thành `void observe(List<? extends Number>)` khi chỉ cần đọc như `Number`. Nhưng không nên đổi chữ ký công khai tùy tiện nếu việc đổi làm ảnh hưởng binary/source compatibility của hệ thống hiện có; phải đánh giá callers, overloads và phương thức override.

### 8.3. Một tham số kiểu có thể nối nhiều vị trí gián tiếp qua bound

Không bắt buộc chữ `T` phải xuất hiện ở đúng hai tham số. Ví dụ:

~~~java
static <T extends Comparable<? super T>> T chooseMax(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
~~~

`T` xuất hiện ở input, output và bound `Comparable<? super T>`. Wildcard `? super T` của `Comparable` nói rằng phép so sánh có thể nhận `T` ngay cả khi implementation so sánh theo một supertype của `T`. Đây là trường hợp type parameter có tên và wildcard **bổ trợ lẫn nhau**.

### 8.4. Vị trí trả về thường nên cung cấp kiểu hữu ích nhất

Một phương thức trả `List<?>` hoặc `List<? extends Number>` có thể là hợp lệ, nhưng caller sẽ phải làm việc qua góc nhìn còn ít thông tin hơn. Nếu method biết chính xác kiểu phần tử hoặc có thể giữ `T` từ tham số đầu vào, trả `List<T>` thường dễ sử dụng hơn.

~~~java
static <T> List<T> mutableCopy(List<? extends T> source) {
    return new ArrayList<>(source);
}
~~~

Caller từ `List<String>` có thể nhận `List<String>`. Khi API chỉ cố ý công bố “một loại Number nào đó” thì kiểu trả về wildcard có thể thích hợp, nhưng cần nhận thức rõ giới hạn với caller.

### 8.5. Named type parameter không đồng nghĩa hai đối số có runtime class giống nhau

Compiler xác minh **khả năng gán/quan hệ kiểu theo chữ ký**, không kiểm tra hai đối tượng có cùng `getClass()`. Ngay cả khi type inference chọn một `T` chung, `T` có thể là interface hoặc superclass.

Ví dụ:

~~~java
static <T extends Number> void acceptPair(T a, T b) {
    System.out.println(a.doubleValue() + b.doubleValue());
}

acceptPair(10, 2.5); // Có thể suy luận T là một supertype phù hợp của Integer và Double.
~~~

Vì vậy “cùng `T`” không nhất thiết có nghĩa **cùng concrete class**. Nó có nghĩa mọi vị trí `T` tuân thủ một hệ ràng buộc kiểu chung mà compiler suy luận được.

## 9. Cách chọn theo một quy trình nhất quán

Khi thiết kế một phương thức generic mới, thực hiện theo thứ tự:

1. **Vẽ đường đi của phần tử:** phương thức lấy dữ liệu từ đâu, đưa dữ liệu đến đâu, có trả lại phần tử hoặc collection mới không?
2. **Tách loại chỉ cần quan sát:** nếu chỉ cần `size()`, `Object` hoặc xử lý không phụ thuộc loại phần tử, thử `<?>`.
3. **Chọn bound cho thao tác một chiều:** chỉ cần đọc `Number` → `? extends Number`; chỉ cần thêm `Integer` → `? super Integer`.
4. **Tìm các quan hệ bắt buộc:** nếu giá trị trả về phải giữ kiểu từ tham số, hoặc nguồn và đích phải tuân thủ quan hệ nào đó, khai báo một tên `<T>` hay `<T,R>`.
5. **Thay các biến kiểu chỉ dùng cho độ linh hoạt:** khi một `S extends T` chỉ có mặt tại một parameterized argument và không góp vào quan hệ khác, cân nhắc viết `? extends T` / `? super T`.
6. **Dùng wildcard capture helper khi cần:** API ngoài có thể nhận `<?>` còn helper bên trong dùng `<T>` để thao tác giữa những phần tử cùng capture.
7. **Biên dịch đúng call site thật:** kiểm tra những lời gọi quan trọng, compiler diagnostics và `javac -Xlint:unchecked`; đừng kết luận chỉ từ một ví dụ lý tưởng.

Bảng quyết định nhanh:

| Tình huống | Lựa chọn | Vì sao |
| --- | --- | --- |
| `print(List<...>)` không quan tâm loại | `List<?>` | Không cần tên kiểu. |
| `sum(List<...>)` chỉ đọc số | `List<? extends Number>` | Đủ bảo đảm `Number`. |
| `fill(List<...>)` chỉ thêm Integer | `List<? super Integer>` | Consumer nhận các `Integer`. |
| `first(...)` trả về loại phần tử đầu vào | `<T> T first(List<T>)` | Nối input và return. |
| `append(source,target)` | `<T> void append(List<? extends T>,List<? super T>)` | Nối source và target. |
| `swap(List<?>)` không muốn lộ kiểu | API `List<?>` + helper `<T>` | Capture cùng kiểu cho `get`/`set`. |
| `map(source,mapper)` trả result kiểu mới | `<T,R>` + wildcard ở source/mapper | Nối source, chuyển đổi, kết quả. |

## 10. Lỗi thường gặp, triệu chứng và hướng sửa

### Lỗi 1 — Dùng `<T>` không mang ý nghĩa

~~~java
static <T> int size(List<T> values) {
    return values.size();
}
~~~

Có thể dùng `static int size(List<?> values)`. `T` ở đây chỉ làm code dài hơn, không bảo toàn quan hệ hữu ích nào.

### Lỗi 2 — Dùng `<?>` rồi mong trả về `T` cụ thể

~~~java
static Object firstUnknown(List<?> values) {
    return values.get(0);
}

// String name = firstUnknown(List.of("Lan")); // KHÔNG biên dịch.
~~~

Nếu cần trả đúng loại, viết `<T> T first(List<T>)`. **Cast xuống subtype** sau khi nhận `Object` không thay thế bằng chứng tĩnh được cung cấp bởi generic method.

### Lỗi 3 — Dùng `extends` tại đích cần ghi

~~~java
static void brokenAdd(List<? extends Number> target) {
    target.add(10); // KHÔNG biên dịch.
}
~~~

Đổi sang `List<? super Integer>` nếu chỉ thêm `Integer`, hoặc dùng `List<Number>` nếu hợp đồng thực sự yêu cầu list phần tử `Number`.

### Lỗi 4 — Dùng `super` tại nguồn cần đọc Number

~~~java
static double brokenSum(List<? super Number> values) {
    double result = 0;
    for (Number n : values) { // KHÔNG biên dịch: phần tử đọc ra chỉ bảo đảm Object.
        result += n.doubleValue();
    }
    return result;
}
~~~

Đổi sang `List<? extends Number>` nếu dùng các phần tử như `Number`.

### Lỗi 5 — Biến mỗi kiểu wildcard thành một `T` duy nhất

Nếu hai tham số **không liên quan**, không nên buộc chúng dùng chung `T`:

~~~java
static void printPairSizes(List<?> left, List<?> right) {
    System.out.println(left.size() + ":" + right.size());
}
~~~

Hàm có thể nhận `List<String>` cùng `List<Integer>`. Không cần ép chúng vào chung một type argument.

### Lỗi 6 — Chữa lỗi kiểu bằng raw `List` hoặc unchecked cast

~~~java
// Cảnh báo/rủi ro: raw type che thông tin kiểu.
List raw = new ArrayList<String>();
List<Integer> ints = raw; // Unchecked conversion.
~~~

Thao tác này không tạo quan hệ subtype đúng và có thể dẫn đến `ClassCastException` khi đọc. Thiết kế lại chữ ký bằng wildcard và type parameter thích hợp, hoặc kiểm tra dữ liệu tại boundary của legacy API.

### Lỗi 7 — Nhầm thông tin của type parameter với giới hạn runtime

Do **type erasure**, `List<String>` và `List<Integer>` không giữ type arguments cụ thể để có thể kiểm tra bằng `instanceof List<String>`. Named `T` giúp **compiler** xác minh quan hệ kiểu; không phải một “bằng chứng runtime” tự động. Nếu cần runtime validation, kiểm tra phần tử thực tế hoặc dùng `Class<T>` token khi phù hợp.

## 11. Ôn tập

1. Muốn in `size()` của mọi list, nên chọn `<T> void f(List<T>)` hay `void f(List<?>)`? **`List<?>`** đơn giản, không đánh mất mối quan hệ cần thiết.
2. Muốn tính tổng `List<Integer>` lẫn `List<Double>` mà chỉ cần `Number.doubleValue()`? **`List<? extends Number>`**.
3. Muốn ghi `Integer` vào `List<Integer>`, `List<Number>` hoặc `List<Object>`? **`List<? super Integer>`**.
4. Vì sao `<T> T first(List<T>)` cần `T`? **Để loại của kết quả liên hệ với loại phần tử đầu vào tại cùng một lời gọi.**
5. Vì sao `appendAll(List<?>, List<?>)` không đủ để chèn phần tử? **Hai kiểu wildcard không được chứng minh có quan hệ producer/consumer; cần type parameter chung để nối hai vị trí.**
6. Vì sao API `swapEnds(List<?>)` vẫn có thể cần helper `<T>`? **Để capture một kiểu phần tử chưa biết và dùng cùng một tên `T` ở cả hai thao tác đọc/ghi.**
7. Có phải `<T>` đảm bảo mọi argument ở runtime cùng một class? **Không.** Type inference có thể chọn một supertype chung.
8. Quy tắc “`T` dùng một lần thì đổi thành `?`” có luôn đúng không? **Không tuyệt đối.** Hãy tìm xem `T` có góp vào bound/return type/quan hệ khác hay ràng buộc của API hiện có không.

## 12. Tài liệu tham khảo

- [JLS Java SE 17 §4.5.1 — Type Arguments of Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1): giải thích wildcard, type-argument containment và ví dụ khi một type parameter dùng một lần nên thay bằng wildcard.
- [JLS Java SE 17 §5.1.10 — Capture Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.10): cơ sở để chuyển kiểu chưa biết của wildcard thành type variable tạm thời.
- [Oracle Java Tutorials — Guidelines for Wildcard Use](https://docs.oracle.com/javase/tutorial/java/generics/wildcardGuidelines.html): lựa chọn `extends`/`super` theo producer/consumer và lưu ý với method return types.
- [Oracle Java Tutorials — Wildcard Capture and Helper Methods](https://docs.oracle.com/javase/tutorial/java/generics/capture.html): lý do dùng helper generic để nắm bắt wildcard.
- [Java SE 17 API — Collections.copy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#copy(java.util.List,java.util.List)): chữ ký chuẩn kết hợp `<T>`, `? super T` và `? extends T`.
- [Java SE 17 API — Collection.addAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html#addAll(java.util.Collection)): `Collection<? extends E>` trong một interface đã đặt tên `E`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pecs-006">Ràng buộc nhiều đầu vào cùng chia sẻ một kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Ràng buộc nhiều đầu vào cùng chia sẻ một kiểu trong Java Generics và PECS

Khi một phương thức Java nhận nhiều danh sách, tập hợp hoặc các giá trị generic, ta thường cần diễn đạt **mối quan hệ giữa các tham số**: phần tử từ nhiều nguồn phải được xử lý theo một kiểu chung, dữ liệu được chuyển sang đích phải an toàn, và kết quả trả về phải giữ được thông tin kiểu.

Một tham số kiểu phương thức, chẳng hạn `<T>`, cho phép đặt **một tên kiểu chung** dùng ở nhiều vị trí trong cùng chữ ký. Kết hợp với quy tắc **PECS** (*Producer Extends, Consumer Super*), ta có thể viết:

```java
static <T> void transferBoth(
        List<? extends T> first,
        List<? extends T> second,
        List<? super T> destination) {
    for (T value : first) {
        destination.add(value);
    }
    for (T value : second) {
        destination.add(value);
    }
}
```

Ở đây, `first` và `second` là **nguồn cung cấp** các phần tử tương thích với `T`; `destination` là **đích nhận** giá trị `T`. Cả ba tham số được kiểm tra thông qua **cùng một `T` của mỗi lần gọi**, nhờ đó compiler có thể xác minh luồng dữ liệu.

Điều quan trọng cần hiểu ngay: dùng chung `T` trong `List<? extends T>` **không buộc hai nguồn có cùng kiểu phần tử chính xác**. Ví dụ nguồn `List<Integer>` và `List<Double>` vẫn có thể cùng tham gia một lời gọi với `T = Number`. Nếu muốn yêu cầu cả hai danh sách có **chính xác cùng đối số kiểu**, chữ ký `List<T>` ở cả hai vị trí cho một hợp đồng khác.

## 1. Thế nào là “nhiều đầu vào cùng chia sẻ một kiểu”?

Xét phương thức xử lý hai danh sách:

```java
static <T> void inspect(List<T> left, List<T> right) {
    T firstLeft = left.get(0);
    T firstRight = right.get(0);
    System.out.println(firstLeft + " | " + firstRight);
}
```

Khai báo `<T>` **một lần** trước kiểu trả về của phương thức; các lần dùng `T` trong chữ ký và thân phương thức chỉ tới **cùng tham số kiểu**. Với các đối số là `List<String>` và `List<String>`, compiler có thể suy luận `T = String`. Với hai `List<Integer>`, compiler có thể suy luận `T = Integer`.

Trong chữ ký này, `List<T>` là bất biến (*invariant*), nên hai tham số danh sách được yêu cầu phù hợp với **cùng một đối số kiểu `T`**. Ví dụ:

```java
List<Integer> integers = List.of(1, 2);
List<Integer> moreIntegers = List.of(3, 4);
List<Number> numbers = List.of(5, 6);

inspect(integers, moreIntegers); // Hợp lệ: T là Integer.
// inspect(integers, numbers);   // Không biên dịch với chữ ký List<T>, List<T>.
```

Đoạn trên là minh họa nằm trong class có phương thức `inspect` và cần `import java.util.List;`.

Lỗi ở lời gọi thứ hai xuất hiện vì `List<Integer>` không phải `List<Number>`. Java không thể chỉ chọn `T = Number` để biến `List<Integer>` thành `List<Number>`; các generic như `List<E>` không tự hiệp biến theo đối số kiểu.

**Khái niệm “chung một `T`” có hai mức thiết kế khác nhau**:

1. `List<T>` ở nhiều vị trí: các tham số danh sách phải dùng cùng một kiểu đối số theo các quy tắc tương thích generic.
2. `List<? extends T>` ở nhiều vị trí: mỗi danh sách có thể dùng kiểu phần tử riêng, miễn đều là `T` hoặc kiểu con của `T`.

Cả hai đều dùng chung tên `T`, nhưng **lời hứa về các đối số đầu vào khác nhau**.

## 2. Tham số kiểu `<T>` và vai trò của suy luận kiểu

Trong một phương thức generic:

```java
static <T> T chooseFirst(T first, T second) {
    return first;
}
```

các vị trí `T` được ràng buộc bởi cùng một biến kiểu của phương thức. Tuy nhiên, đừng hiểu `T` là lời hứa rằng **hai đối tượng runtime phải có cùng class**. Lời gọi:

```java
Number value = chooseFirst(10, 2.5);
```

có thể hợp lệ vì compiler tìm được một kiểu chung tương thích với `Integer` và `Double`, đồng thời phù hợp với ngữ cảnh kết quả, chẳng hạn `Number`. Cả hai tham số `T first` và `T second` nhận giá trị có kiểu con của `T` thông qua chuyển đổi phương thức.

Khác biệt xuất hiện ở generic bất biến:

```java
static <T> void acceptLists(List<T> first, List<T> second) {
    // Hai tham số List<T> cùng đòi hỏi đối số kiểu T.
}
```

Dù `Integer` và `Double` cùng kế thừa `Number`, một `List<Integer>` không thể tự chuyển thành `List<Number>` để khớp `List<T>`. Khi cần cho phép các **nguồn khác kiểu nhưng có kiểu cha chung**, ta phải biểu diễn rõ ràng quan hệ đó bằng `? extends T`.

Không nên lạm dụng `<T>` chỉ để thay cho một kiểu cụ thể mà không có quan hệ nào cần duy trì. Giá trị của tham số kiểu chung là **kết nối nhiều vị trí** trong chữ ký và cho phép compiler kiểm tra chúng nhất quán.

## 3. Hai producer: `List<? extends T>` kết hợp `List<? extends T>`

Một phương thức muốn đọc hai nguồn với kiểu phần tử có thể khác nhau nên khai báo:

```java
static <T> List<T> combine(
        List<? extends T> first,
        List<? extends T> second) {
    List<T> result = new ArrayList<>();
    result.addAll(first);
    result.addAll(second);
    return result;
}
```

Hai đầu vào là *producer*: chúng cung cấp dữ liệu mà ta có thể đọc dưới kiểu `T`. Mỗi wildcard có thể đại diện cho một kiểu con khác nhau của `T`; không phải cùng một wildcard capture và không phải hai danh sách có cùng kiểu phần tử chính xác.

Ví dụ `List<Integer>` và `List<Double>` đều có thể được đọc dưới kiểu `Number`. Nếu biến nhận kết quả có kiểu `List<Number>`, compiler có thể chọn `T = Number`.

### Ví dụ Java 17 chạy được: kết hợp hai nguồn

Lưu thành `SharedProducerDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class SharedProducerDemo {
    static <T> List<T> combine(
            List<? extends T> first,
            List<? extends T> second) {
        List<T> result = new ArrayList<>();
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(1, 2);
        List<Double> doubles = List.of(2.5, 3.5);

        List<Number> allNumbers = combine(integers, doubles);
        System.out.println(allNumbers);

        List<String> allWords = combine(
                List.of("Java"),
                List.of("Generics"));
        System.out.println(allWords);

        // List<Integer> wrong = combine(integers, doubles);
        // Không biên dịch: Double không thể là Integer.
    }
}
```

Biên dịch và chạy:

```bash
javac SharedProducerDemo.java
java SharedProducerDemo
```

Đầu ra:

```text
[1, 2, 2.5, 3.5]
[Java, Generics]
```

Phương thức `combine` tạo ra một danh sách mới nên việc gọi `addAll` trên `result` được hỗ trợ vì `ArrayList` có thể thay đổi. Phần tử lấy từ mỗi nguồn được phép thêm vào `List<T>` vì compiler đã chứng minh kiểu phần tử của các nguồn là `T` hoặc kiểu con của `T`.

Khi cần bảo toàn kiểu `Integer` cho toàn bộ kết quả, nguồn `List<Double>` sẽ không phù hợp. Không có phép ép kiểu an toàn nào biến giá trị `Double` thành `Integer` chỉ nhờ generic.

## 4. Hai producer và một consumer: liên kết bằng chung `T`

Quy tắc PECS áp dụng rõ nhất khi dữ liệu luân chuyển **từ nhiều nguồn vào một đích**:

```java
static <T> void transferBoth(
        List<? extends T> first,
        List<? extends T> second,
        List<? super T> destination) {
    for (T item : first) {
        destination.add(item);
    }
    for (T item : second) {
        destination.add(item);
    }
}
```

Ý nghĩa của từng vị trí:

| Vị trí | Chữ ký | Hợp đồng kiểu |
| --- | --- | --- |
| Nguồn thứ nhất | `List<? extends T>` | Đọc phần tử dưới kiểu `T` |
| Nguồn thứ hai | `List<? extends T>` | Đọc phần tử dưới cùng kiểu chung `T` |
| Đích | `List<? super T>` | Có thể thêm các đối tượng kiểu `T` |
| Khai báo phương thức | `<T>` | Liên kết tất cả điều kiện kiểu trong một lần gọi |

Compiler phải tìm một cách gán `T` sao cho các nguồn phù hợp với `? extends T`, còn đích phù hợp với `? super T`. Nếu không tồn tại kiểu thỏa tất cả, lời gọi bị từ chối trước khi chạy.

### Ví dụ Java 17: chuyển nhiều nguồn vào một đích

Lưu thành `MultiSourcePecsDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class MultiSourcePecsDemo {
    static <T> void transferBoth(
            List<? extends T> first,
            List<? extends T> second,
            List<? super T> destination) {
        for (T item : first) {
            destination.add(item);
        }
        for (T item : second) {
            destination.add(item);
        }
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(1, 2);
        List<Double> doubles = List.of(2.5, 3.5);
        List<Number> numbers = new ArrayList<>();

        transferBoth(integers, doubles, numbers);
        System.out.println(numbers);

        List<Object> objects = new ArrayList<>();
        transferBoth(List.of("A", "B"), List.of(10, 20), objects);
        System.out.println(objects);

        List<Integer> integerDestination = new ArrayList<>();
        transferBoth(integers, List.of(3, 4), integerDestination);
        System.out.println(integerDestination);

        // transferBoth(integers, doubles, integerDestination);
        // Không biên dịch: đích List<Integer> không thể nhận Double.
    }
}
```

```bash
javac MultiSourcePecsDemo.java
java MultiSourcePecsDemo
```

Đầu ra:

```text
[1, 2, 2.5, 3.5]
[A, B, 10, 20]
[1, 2, 3, 4]
```

Phân tích:

1. Với `List<Integer>`, `List<Double>` và `List<Number>`, có thể chọn `T = Number`: hai nguồn đọc được như `Number` và đích nhận được `Number`.
2. Với nguồn `List<String>`, `List<Integer>` và đích `List<Object>`, có thể chọn kiểu chung như `Object`, đủ để truyền cả hai loại phần tử.
3. Với hai nguồn số nguyên và đích `List<Integer>`, có thể chọn `T = Integer`.
4. Với nguồn có `Double` nhưng đích `List<Integer>`, không có `T` an toàn thỏa cả yêu cầu nhận `Double` của nguồn và giới hạn nhận `Integer` của đích.

Ví dụ minh họa **ràng buộc kiểu qua nhiều tham số**. Nó không ép Java chuyển đổi đối tượng `Double` thành `Integer` hay `String` thành `Object` mới; các thao tác chỉ chuyển cùng tham chiếu đối tượng qua interface danh sách.

## 5. “Chia sẻ một `T`” không đồng nghĩa các nguồn cùng kiểu chính xác

Đây là chỗ dễ nhầm nhất.

So sánh hai chữ ký:

```java
static <T> void exactArguments(List<T> left, List<T> right) {
    // Hai List dùng cùng đối số kiểu T.
}

static <T> void commonSupertype(
        List<? extends T> left,
        List<? extends T> right) {
    // Mỗi List có thể dùng kiểu con khác nhau của T.
}
```

Với:

```java
List<Integer> ints = List.of(1, 2);
List<Double> doubles = List.of(2.5, 3.5);

// exactArguments(ints, doubles); // Không biên dịch.
commonSupertype(ints, doubles);   // Có thể suy luận T chung.
```

Lưu ý: các đoạn ở mục này chỉ minh họa cú pháp và điều kiện biên dịch; muốn chạy độc lập, đặt chúng vào class, thêm `import java.util.List;` và phương thức `main`.

**Tại sao `List<T>` chặt hơn?** Tham số kiểu `T` nằm bên trong `List<T>` bất biến. Khi một đối số là `List<Integer>`, nó tạo điều kiện đẳng thức đối số kiểu phù hợp với `Integer`; đối số `List<Double>` tạo điều kiện khác không khớp.

**Tại sao `List<? extends T>` mềm hơn?** Tham số nguồn chỉ cần có đối số kiểu thuộc `T` hoặc kiểu con của `T`. `Integer` và `Double` có kiểu cha chung `Number`, nên điều kiện có thể được thỏa đồng thời.

Cũng đừng đồng nhất `<T> void accept(T a, T b)` với `<T> void accept(List<T> a, List<T> b)`. Ở cách đầu, mỗi giá trị `Integer` hay `Double` có thể tự chuyển sang một kiểu cha chung khi truyền tham số; ở cách thứ hai, hai container bất biến không được chuyển đổi đối số kiểu như vậy.

## 6. Cận trên của `T` giới hạn miền kiểu chung

Nếu mục tiêu chỉ xử lý các số, ta có thể thêm cận:

```java
static <T extends Number> double sumBoth(
        List<? extends T> first,
        List<? extends T> second) {
    double sum = 0;
    for (Number value : first) {
        sum += value.doubleValue();
    }
    for (Number value : second) {
        sum += value.doubleValue();
    }
    return sum;
}
```

Compiler cần tìm một `T` là `Number` hoặc kiểu con của `Number`, đồng thời là kiểu cha chung phù hợp với cả hai nguồn. Hai nguồn `Integer` và `Double` có thể dùng chung `T = Number`. Nhưng nếu một nguồn là `List<String>` thì không có cách suy luận hợp lệ thỏa `T extends Number`.

Lưu thành `BoundedInputsDemo.java`:

```java
import java.util.List;

public class BoundedInputsDemo {
    static <T extends Number> double sumBoth(
            List<? extends T> first,
            List<? extends T> second) {
        double result = 0;
        for (Number number : first) {
            result += number.doubleValue();
        }
        for (Number number : second) {
            result += number.doubleValue();
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println(sumBoth(
                List.of(1, 2),
                List.of(0.5, 1.5)));

        // sumBoth(List.of(1), List.of("hello"));
        // Không biên dịch: String không phải Number.
    }
}
```

```bash
javac BoundedInputsDemo.java
java BoundedInputsDemo
```

Đầu ra:

```text
5.0
```

Cận `T extends Number` giúp code trong thân phương thức dùng được API của `Number`. Tuy nhiên, cận kiểu không ngăn giá trị `null` xuất hiện trong những nguồn cho phép `null`. Đoạn `sumBoth` giả định các phần tử được cung cấp là số khác `null`; nếu không, `number.doubleValue()` có thể gây `NullPointerException`. API thực tế nên nêu rõ hoặc kiểm tra giả định dữ liệu.

## 7. Kết quả trả về cũng có thể chia sẻ `T` với nhiều đầu vào

Ràng buộc kiểu không chỉ áp dụng cho tham số đầu vào. Kiểu trả về `T` hoặc `List<T>` giúp bảo toàn mối quan hệ qua ranh giới lời gọi:

```java
static <T> List<T> combine(
        List<? extends T> first,
        List<? extends T> second) {
    List<T> result = new ArrayList<>();
    result.addAll(first);
    result.addAll(second);
    return result;
}
```

Nhờ kiểu trả về `List<T>`, ngữ cảnh gán cũng có thể tham gia suy luận:

```java
List<Integer> ints = List.of(1, 2);
List<Double> doubles = List.of(2.5, 3.5);

List<Number> combined = combine(ints, doubles); // Hợp lệ.

// List<Integer> wrong = combine(ints, doubles);
// Không biên dịch: yêu cầu đầu ra Integer không dung nạp Double.
```

Nếu đổi chữ ký sang `List<?> combine(List<?> a, List<?> b)`, phương thức vẫn có thể chứa và trả lại các phần tử, nhưng caller chỉ đọc được kết quả dưới dạng `Object` và đánh mất thông tin kiểu chung mà `<T>` có thể duy trì.

Đây là tiêu chí thiết kế quan trọng: **khi có quan hệ kiểu giữa nhiều tham số và kết quả, dùng tên `T` chung để mô tả quan hệ ấy**. Wildcard độc lập `?` thường phù hợp khi caller và phương thức không cần duy trì mối liên hệ đó.

## 8. Nhiều wildcard `?` không tự liên kết với nhau

Xét phương thức:

```java
static void inspectUnknown(List<?> first, List<?> second) {
    Object a = first.get(0);
    Object b = second.get(0);
    System.out.println(a + ", " + b);
}
```

Mỗi `?` biểu thị một đối số kiểu chưa biết **riêng**. Không thể suy ra rằng phần tử của `first` có cùng kiểu với phần tử của `second`.

Tương tự, chữ ký:

```java
static void acceptTwo(
        List<? extends Number> first,
        List<? extends Number> second) {
    // Cả hai nguồn đều đọc được như Number.
}
```

bảo đảm cả hai danh sách chứa số khi đọc qua kiểu tĩnh, nhưng không khẳng định hai danh sách có cùng kiểu phần tử chính xác. `List<Integer>` và `List<Double>` đều hợp lệ.

Với một phương thức generic:

```java
static <T> void acceptLinked(
        List<? extends T> first,
        List<? extends T> second) {
    // Có một T dùng chung, mỗi wildcard có thể capture kiểu con khác.
}
```

cũng không thể thực hiện `first.add(second.get(0))`, vì `first` có kiểu phần tử capture không xác định và có thể là `List<Integer>` trong khi `second` là `List<Double>`. **Kiểu chung `T` chỉ bảo đảm các phần tử có thể được đọc như `T`**, chưa đủ cho phép ghi một phần tử của nguồn này trực tiếp vào nguồn kia.

Nếu cần chuyển phần tử, phải truyền vào một **consumer phù hợp**, hoặc nhận các danh sách chính xác `List<T>` khi thực sự yêu cầu cùng kiểu phần tử và các thao tác ghi phù hợp.

## 9. Tại sao không dùng `List<Object>` hoặc ép kiểu để đơn giản hóa?

Một cách viết kém linh hoạt là:

```java
static void transferOnlyObjects(
        List<Object> source1,
        List<Object> source2,
        List<Object> destination) {
    destination.addAll(source1);
    destination.addAll(source2);
}
```

Cách này không nhận trực tiếp `List<String>` hay `List<Integer>` vì `List<String>` không phải `List<Object>`. Người gọi có thể bị buộc tạo danh sách mới hoặc dùng cast không an toàn.

Nếu chữ ký thật sự chỉ cần **đọc các nguồn và ghi vào đích kiểu cha chung**, `<T>` + PECS diễn đạt ý định chính xác hơn:

```java
static <T> void transferBoth(
        List<? extends T> first,
        List<? extends T> second,
        List<? super T> destination) {
    for (T value : first) destination.add(value);
    for (T value : second) destination.add(value);
}
```

Compiler tự kiểm tra kiểu. Tránh raw type hoặc `(List)` để ép một `List<Integer>` thành `List<Number>`: những cách đó có thể làm hỏng an toàn kiểu và gây `ClassCastException` ở nơi đọc về sau.

Tuy vậy, **không phải mọi phương thức đều cần generic**. Nếu nghiệp vụ thật sự yêu cầu mọi đầu vào đã là `List<Object>` và không chấp nhận các loại danh sách tham số hóa khác, việc khai báo kiểu cụ thể có thể là phù hợp. Quyết định đến từ **hợp đồng dữ liệu mong muốn**, không phải mong muốn dùng nhiều cú pháp generic nhất.

## 10. Liên hệ với API `Collections.copy`

Thư viện chuẩn Java có phương thức với ý tưởng PECS quen thuộc:

```java
public static <T> void copy(
        List<? super T> dest,
        List<? extends T> src)
```

Đây là chữ ký khái niệm của `java.util.Collections.copy`. `src` là producer, `dest` là consumer, cùng chia sẻ `T`; do đó có thể sao chép từ `List<Integer>` vào `List<Number>`.

Chú ý: `Collections.copy` **ghi đè các phần tử hiện có** ở đích và yêu cầu danh sách đích đủ dài; nó **không phải** một phương thức `addAll` tự tăng kích thước đích.

Trong bài toán có **hai nguồn**, chúng ta tổng quát hóa mô hình này bằng cách gắn cả hai nguồn vào một `T`:

```java
static <T> void appendAll(
        List<? extends T> sourceA,
        List<? extends T> sourceB,
        List<? super T> destination) {
    destination.addAll(sourceA);
    destination.addAll(sourceB);
}
```

`appendAll` yêu cầu đích hỗ trợ thêm phần tử; compiler chỉ kiểm tra quan hệ kiểu, không bảo đảm collection đích sửa được. Ví dụ `List.of(...)` có thể thỏa tham số về kiểu nhưng vẫn ném `UnsupportedOperationException` khi `addAll` thực hiện thay đổi.

Nếu dữ liệu từ hai nguồn không có kiểu chung đáp ứng khả năng của đích, compiler từ chối lời gọi thay vì để lỗi kiểu xuất hiện sau khi ghi.

## 11. Trường hợp API cần ràng buộc cùng loại phần tử chính xác

Giả sử hai danh sách là hai phiên bản của **cùng một loại dữ liệu**, và phương thức cần hoán đổi các phần tử giữa chúng. Khi ấy ta có thể viết:

```java
static <T> void exchangeFirst(List<T> first, List<T> second) {
    T temporary = first.get(0);
    first.set(0, second.get(0));
    second.set(0, temporary);
}
```

Ở đây `List<T>` ở cả hai vị trí **cố ý yêu cầu cùng đối số kiểu**. Một `List<Integer>` và một `List<Number>` không được tự xem là cặp cùng kiểu phần tử để gọi trực tiếp phương thức này.

Ví dụ Java 17 đầy đủ, lưu thành `SameTypeSwapDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class SameTypeSwapDemo {
    static <T> void exchangeFirst(List<T> first, List<T> second) {
        T previous = first.get(0);
        first.set(0, second.get(0));
        second.set(0, previous);
    }

    public static void main(String[] args) {
        List<String> first = new ArrayList<>(List.of("left", "keep"));
        List<String> second = new ArrayList<>(List.of("right", "stay"));

        exchangeFirst(first, second);
        System.out.println(first);
        System.out.println(second);

        List<Integer> ints = new ArrayList<>(List.of(1));
        List<Number> numbers = new ArrayList<>(List.of(2));
        // exchangeFirst(ints, numbers); // Không biên dịch.
    }
}
```

```bash
javac SameTypeSwapDemo.java
java SameTypeSwapDemo
```

Đầu ra:

```text
[right, keep]
[left, stay]
```

`exchangeFirst` yêu cầu cả hai danh sách không rỗng và cho phép `set`. Các giả định này được đáp ứng bởi ví dụ `ArrayList`, nhưng không phải do generics tự bảo đảm. Danh sách không sửa đổi hoặc rỗng có thể gây exception khi chạy. Đây là giới hạn về API collection, tách biệt với kiểm tra quan hệ kiểu.

Đừng thêm `? extends T` vào mọi tham số chỉ vì PECS: khi hàm phải **đọc và ghi lại** đúng kiểu phần tử vào cả hai danh sách, `List<T>` thường diễn đạt ràng buộc tốt hơn.

## 12. Lỗi phổ biến và cách sửa

### Lỗi 1: dùng `List<T>` nhưng muốn nhận hai producer có kiểu con khác nhau

```java
static <T> void consumeBoth(List<T> a, List<T> b) {
    // ...
}
```

Lời gọi với `List<Integer>` và `List<Double>` sẽ không phù hợp vì danh sách generic bất biến.

**Cách sửa:** khi phương thức chỉ đọc từ hai danh sách, cân nhắc `List<? extends T>` cho cả hai, với `<T>` dùng chung.

### Lỗi 2: dùng hai wildcard `?` rồi giả định chúng là cùng kiểu

```java
static void wrongAssumption(List<?> a, List<?> b) {
    Object value = b.get(0);
    // a.add(value); // Không biên dịch.
}
```

Không có quan hệ nào buộc kiểu phần tử của `a` và `b` giống nhau. Đổi sang `List<T>` nếu cần cùng đối số kiểu để chuyển phần tử, hoặc chuyển phần tử qua một consumer riêng `List<? super T>` nếu chỉ cần một kiểu cha chung.

### Lỗi 3: nhầm `? extends T` với lời hứa hai nguồn cùng kiểu chính xác

```java
static <T> void accept(List<? extends T> left,
                       List<? extends T> right) {
    // T chung không có nghĩa hai wildcard capture giống nhau.
}
```

Có thể truyền `List<Integer>` và `List<Double>`, nên không được tự động ghi phần tử từ một nguồn vào nguồn kia.

### Lỗi 4: chọn đích quá hẹp cho các nguồn

```java
List<Integer> ints = List.of(1);
List<Double> doubles = List.of(2.5);
List<Integer> destination = new ArrayList<>();

// transferBoth(ints, doubles, destination); // Không biên dịch.
```

**Cách sửa:** nếu đích phải chứa cả hai loại số, dùng `List<Number>` hoặc kiểu cha chung phù hợp làm kiểu phần tử của đích.

### Lỗi 5: dùng `List<Object>` thay cho wildcard phù hợp

`List<Object>` không nhận trực tiếp `List<String>` hay `List<Integer>`. Nếu phương thức chỉ quan sát dữ liệu, dùng `List<?>`; nếu cần giữ quan hệ qua nhiều nguồn/đích, dùng một tham số kiểu `<T>` cùng cận wildcard.

### Lỗi 6: nghĩ một `T` sẽ được suy ra từ đúng một đối số

Compiler xét **toàn bộ điều kiện của lời gọi**, bao gồm các nguồn, đích, cận `T extends ...` và đôi khi cả kiểu kết quả được kỳ vọng. Nếu một đối số buộc `T` không tương thích với đối số khác, lời gọi sẽ bị từ chối. Không chọn `T` riêng rẽ cho từng vị trí rồi coi chúng là cùng kiểu.

### Lỗi 7: nhầm an toàn kiểu với khả năng sửa đổi collection

`List<? super T>` cho phép compiler chấp nhận thao tác thêm `T`, nhưng collection thực tế có thể không hỗ trợ `add` hoặc có quy tắc về `null`. Bảo đảm kiểu của PECS và hợp đồng runtime của collection là các vấn đề khác nhau.

## 13. Các câu hỏi tự kiểm tra

**Câu 1.** Với hai danh sách `List<Integer>` và `List<Double>`, có gọi được phương thức sau không?

```java
static <T> void read(List<? extends T> a,
                     List<? extends T> b) { }
```

**Có.** Compiler có thể chọn kiểu cha chung thích hợp như `Number`. Hai wildcard capture không cần bằng nhau.

**Câu 2.** Hai danh sách ấy có gọi được phương thức `static <T> void read(List<T> a, List<T> b)` không?

**Không**, vì `List<Integer>` và `List<Double>` là các tham số hóa bất biến khác nhau.

**Câu 3.** Nếu hai nguồn là `List<Integer>` và `List<Double>`, đích `List<Number>` có phù hợp với chữ ký `<T> void transferBoth(List<? extends T>, List<? extends T>, List<? super T>)` không?

**Có.** Có thể chọn `T = Number` và thêm tất cả phần tử số vào đích.

**Câu 4.** Thay đích thành `List<Integer>` thì sao?

**Không hợp lệ** vì nguồn `List<Double>` chứa kiểu phần tử không thể ghi an toàn vào `List<Integer>`.

**Câu 5.** Một phương thức cần hoán đổi phần tử qua lại giữa hai danh sách thì nên dùng hai `List<? extends T>`?

**Không nhất thiết; thường nên dùng hai `List<T>`** vì cần đọc và ghi các phần tử cùng kiểu chính xác. `? extends T` chủ yếu thích hợp khi tham số là producer.

**Câu 6.** Tại sao `<T>` xuất hiện một lần trong chữ ký nhưng được dùng ở nhiều chỗ?

Vì một tham số kiểu phải được khai báo một lần và có phạm vi sử dụng trong chữ ký/thân phương thức. Các chỗ dùng cùng `T` cho compiler khả năng kiểm tra một quan hệ nhất quán giữa nguồn, đích và kết quả.

## 14. Hướng dẫn chọn chữ ký

Hãy bắt đầu từ **hướng dữ liệu và quan hệ kiểu mong muốn**:

1. Nếu nhiều tham số phải là danh sách có **cùng đối số kiểu chính xác** và có thể đọc/ghi qua lại, dùng `<T> ... (List<T> a, List<T> b)`.
2. Nếu nhiều nguồn có thể chứa **những kiểu con khác nhau của một kiểu chung**, dùng `<T> ... (List<? extends T> a, List<? extends T> b)`.
3. Nếu dữ liệu từ những nguồn ấy cần được **thêm vào một đích**, dùng `List<? super T>` cho đích để nối quan hệ PECS.
4. Nếu miền dữ liệu phải thuộc một họ nhất định, bổ sung cận cho `T`, ví dụ `<T extends Number>`.
5. Nếu kiểu kết quả cần phản ánh đúng quan hệ với các nguồn, dùng `T` hoặc `List<T>` trong kiểu trả về.
6. Nếu không cần quan hệ giữa các phần tử hoặc giữa các tham số, `List<?>` có thể đủ và dễ đọc hơn một generic method không cần thiết.

**Ghi nhớ:** `<T>` tạo tên cho **một biến kiểu chung**; `? extends T` cho phép mỗi nguồn là producer của `T` hoặc kiểu con; `? super T` cho phép đích nhận `T`. Thiết kế đúng phải phân biệt rõ “cùng `T` để liên kết luồng dữ liệu” với “các danh sách có cùng đối số kiểu chính xác”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pecs-007">Giới hạn khi trả về wildcard từ API</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn khi trả về wildcard từ API trong Java Generics

## 1. Mục tiêu và vấn đề thực tế

Quy tắc **PECS** (*Producer Extends, Consumer Super*) giúp chọn wildcard cho các tham số generic: đối tượng dùng để **cung cấp dữ liệu** thường phù hợp với `? extends T`, còn đối tượng dùng để **nhận dữ liệu** thường phù hợp với `? super T`. Tuy nhiên, áp dụng PECS một cách máy móc cho **kiểu trả về của API** có thể khiến mã gọi API mất thông tin kiểu, bị hạn chế thao tác và phải truyền wildcard sang nhiều tầng mã khác.

Ví dụ:

```java
static List<? extends Number> numbers() {
    return List.of(1, 2, 3);
}
```

Lời gọi sau hợp lệ:

```java
List<? extends Number> result = numbers();
Number number = result.get(0);
```

Nhưng hai thao tác sau không hợp lệ:

```java
// List<Number> result = numbers(); // LỖI: kiểu trả về không bảo đảm List<Number>.
// numbers().add(4);               // LỖI: không biết kiểu phần tử thực tế.
```

Đây là những **giới hạn ở thời điểm biên dịch**, không nhất thiết là lỗi thiết kế trong mọi tình huống. Bài học phân biệt khi wildcard return hợp lý, khi nó khiến API khó sử dụng và cách viết chữ ký trả về rõ ràng hơn.

Sau khi học, bạn có thể:

- Giải thích thông tin kiểu mà `List<?>`, `List<? extends T>` và `List<? super T>` còn giữ lại cho caller.
- Phân biệt việc **trả về một giá trị** với vai trò producer/consumer của **đối tượng được trả về**.
- Hiểu vì sao `List<? extends T>` không đồng nghĩa với `List<T>` hoặc một danh sách bất biến.
- Thiết kế API trả về kiểu cụ thể, kiểu cha phù hợp hoặc phương thức generic `<T>` khi caller cần thông tin kiểu mạnh hơn.
- Nhận diện các trường hợp wildcard return hữu ích và tránh ép kiểu không kiểm tra.

## 2. Wildcard return là gì?

Giả sử có các class:

```java
class Animal {}
class Dog extends Animal {}
class Cat extends Animal {}
```

Và một thư viện cung cấp các phương thức sau:

```java
static List<Dog> dogs() {
    return List.of(new Dog());
}

static List<? extends Animal> animalsUnknownSubtype() {
    return dogs();
}

static List<?> unknownItems() {
    return dogs();
}
```

Chữ ký `List<Dog>` cho caller biết **chính xác đối số kiểu là `Dog`**. Chữ ký `List<? extends Animal>` chỉ cho biết đối số kiểu là một kiểu không xác định nhưng là subtype của `Animal`. Chữ ký `List<?>` không bảo đảm phần tử thuộc lớp cụ thể nào ngoài `Object`.

Một phương thức có thể trả về đối tượng runtime giống nhau ở cả ba trường hợp. Khác biệt chính là **hợp đồng kiểu mà Java cho caller sử dụng**.

### 2.1. Kiểu được công khai quyết định thao tác hợp lệ

Trong mã của thư viện, tác giả có thể biết rằng kết quả là `List<Dog>`. Khi công khai chữ ký `List<? extends Animal>`, thông tin **chính xác là `Dog`** không còn được bảo đảm cho người gọi:

```java
List<? extends Animal> animals = animalsUnknownSubtype();

Animal first = animals.get(0); // Hợp lệ.
// Dog dog = animals.get(0);   // Lỗi: giá trị chỉ được bảo đảm là Animal.
// animals.add(new Dog());     // Lỗi: có thể danh sách thực tế là List<Cat>.
```

Đối tượng runtime có thể chứa `Dog`; tuy nhiên, Java phải kiểm tra chương trình an toàn với **mọi kiểu** mà chữ ký cho phép, không chỉ kết quả ở một lần thực thi.

## 3. Vì sao không nên áp dụng PECS máy móc lên kiểu trả về?

Trong chữ ký:

```java
static void process(List<? extends Animal> source) { ... }
```

`source` là tham số đóng vai **producer** khi `process` cần đọc dữ liệu. Wildcard cận trên cho phép caller truyền `List<Dog>`, `List<Cat>` hay `List<Animal>` mà vẫn bảo đảm phần tử đọc được như `Animal`.

Nhưng trong chữ ký:

```java
static List<? extends Animal> getAnimals() { ... }
```

người gọi **nhận một đối tượng danh sách**. Sau khi nhận, họ có thể muốn đọc, lọc, sao chép, thêm phần tử, sắp xếp hoặc truyền kết quả đến phương thức khác. Nếu khai báo trả về chứa wildcard, mọi thao tác tiếp theo chịu các hạn chế tương ứng với wildcard đó.

Hai góc nhìn cần phân biệt:

1. **Phương thức trả về giá trị**: API sản xuất ra một đối tượng cho caller.
2. **Collection được trả về**: caller có thể dùng đối tượng đó để sản xuất hoặc tiêu thụ phần tử.

Không nên suy ra rằng vì phương thức là producer của đối tượng trả về nên kiểu trả về bắt buộc dùng `? extends T`. Hãy quyết định dựa trên **hợp đồng mà caller cần**.

Khuyến nghị thường gặp từ hướng dẫn Java Generics là **tránh wildcard ở kiểu trả về khi có thể**, vì caller phải tiếp tục xử lý kiểu chưa biết. Đây là nguyên tắc thiết kế API, không phải lệnh cấm cú pháp.

## 4. Trả về `List<?>`: mất thông tin về phần tử

Ví dụ:

```java
static List<?> loadItems() {
    return List.of("Java", "Generics");
}
```

Tại caller:

```java
List<?> values = loadItems();
Object item = values.get(0);       // Hợp lệ.
int count = values.size();         // Hợp lệ.

// String value = values.get(0);   // Lỗi biên dịch: chỉ bảo đảm Object.
// values.add("new element");      // Lỗi biên dịch: không rõ kiểu phần tử.
```

Về mặt kiểu, `List<?>` có thể biểu thị `List<String>`, `List<Integer>` hoặc nhiều kiểu khác. Do đó kết quả của `get` chỉ được bảo đảm là `Object`, và `add` không thể nhận một đối tượng non-null bất kỳ theo cách an toàn tổng quát.

### 4.1. Khi `List<?>` vẫn phù hợp

Kiểu trả về `List<?>` có thể hợp lý khi **ý nghĩa nghiệp vụ thực sự là một tập hợp có phần tử chưa biết hoặc dị thể** và caller chỉ cần các thao tác không phụ thuộc kiểu phần tử:

```java
static List<?> diagnosticValues() {
    return List.of("READY", 200, true);
}
```

Caller có thể in, đếm, hiển thị hoặc kiểm tra từng phần tử:

```java
for (Object value : diagnosticValues()) {
    System.out.println(value);
}
```

Nhưng nếu API thực sự bảo đảm mọi phần tử là `String`, công khai `List<String>` sẽ dễ dùng hơn `List<?>`.

### 4.2. Phân biệt `List<?>` với raw `List`

```java
List<?> safe = List.of("a", "b");
List raw = List.of("a", "b"); // Raw type, không nên dùng cho API mới.
```

`List<?>` vẫn duy trì hệ thống kiểm tra kiểu generic. Raw `List` bỏ qua một phần bảo vệ đó và có thể tạo unchecked warnings. Không nên thay wildcard return bằng raw type chỉ để làm compiler hết phàn nàn.

## 5. Trả về `List<? extends T>`: đọc được T nhưng khó ghi

Giả sử:

```java
static List<? extends Number> readNumbers() {
    return List.of(1, 2, 3);
}
```

Ở caller:

```java
List<? extends Number> result = readNumbers();

Number first = result.get(0); // Hợp lệ.
Object same = result.get(0);  // Hợp lệ.
// result.add(4);             // Lỗi: chưa chắc kiểu phần tử là Integer.
// result.add(2.5);           // Lỗi: chưa chắc kiểu phần tử là Double.
```

`? extends Number` cam kết rằng các phần tử đọc ra **là `Number` hoặc subtype của `Number`**. Nó không cam kết collection có thể tiếp nhận mọi `Number`: danh sách thực tế có thể là `List<Integer>`, `List<Double>`, v.v.

### 5.1. Không thể gán trực tiếp vào `List<T>`

```java
List<? extends Number> result = readNumbers();

// List<Number> numbers = result; // Lỗi biên dịch.
List<? extends Number> view = result; // Hợp lệ.
```

`List<Number>` cho phép thêm một `Double`, trong khi `List<? extends Number>` không bảo đảm điều đó. Java Generics là **invariant**: `List<Integer>` không phải subtype của `List<Number>`.

Đừng dùng ép kiểu không kiểm tra:

```java
// List<Number> unsafe = (List<Number>) (List<?>) result;
```

Cách ép kiểu này có thể làm mất tính an toàn và khiến caller thêm đối tượng sai loại vào danh sách do thư viện sở hữu.

### 5.2. Có thể sao chép sang một `List<T>` mới

Nếu caller cần một danh sách `List<Number>` có khả năng nhận thêm các loại `Number`, hãy **sao chép phần tử**:

```java
List<? extends Number> source = readNumbers();
List<Number> numbers = new ArrayList<>(source);

numbers.add(3.14); // Hợp lệ vì kiểu đích thực sự là List<Number>.
```

Phương thức khởi tạo `ArrayList(Collection<? extends E>)` được thiết kế chính để chấp nhận nguồn có kiểu phần tử con của `E`. Bản sao và danh sách nguồn có cấu trúc riêng; thêm phần tử vào bản sao không thêm vào nguồn.

Tuy nhiên đây là **shallow copy**: phần tử tham chiếu bên trong được chia sẻ, nên việc sửa trạng thái của đối tượng phần tử mutable có thể vẫn ảnh hưởng cả hai phía.

### 5.3. Wildcard cận trên không bảo đảm immutable

```java
List<? extends Number> view = new ArrayList<Integer>();
view.clear(); // Biên dịch và chạy được; danh sách ở đây mutable.
// view.add(123); // Không biên dịch được.
```

Việc compiler ngăn `add(123)` không đồng nghĩa danh sách là bất biến. Tùy implementation, `clear()`, `remove(...)`, `removeIf(...)`, `sort(...)` hoặc các phép thay đổi cấu trúc không cần truyền giá trị phần tử có thể khả thi.

Nếu API muốn cam kết **không được thay đổi** collection, phải dùng hợp đồng và implementation riêng như `List.copyOf(...)`, `List.of(...)` hoặc một wrapper không cho sửa. Wildcard không thay thế cơ chế bất biến.

### 5.4. Mất thông tin subtype cụ thể

Nếu thư viện trả về `List<Dog>` nhưng khai báo công khai `List<? extends Animal>`:

```java
static List<? extends Animal> getAnimals() {
    return List.of(new Dog());
}
```

thì caller không thể mặc định nhận một `Dog`:

```java
Animal animal = getAnimals().get(0); // Hợp lệ.
// Dog dog = getAnimals().get(0);    // Không hợp lệ về kiểu.
```

Ngay cả khi hôm nay implementation chỉ tạo `Dog`, chữ ký cho phép implementation ngày mai trả về `List<Cat>`. Khai báo rộng có thể là mục đích thiết kế, nhưng nếu API **luôn bảo đảm phần tử kiểu `Dog`**, `List<Dog>` thông báo đúng hợp đồng hơn.

## 6. Trả về `List<? super T>`: ghi được T nhưng khó đọc T

Xét:

```java
static List<? super Integer> writableNumbers() {
    return new ArrayList<Number>();
}
```

Ở caller:

```java
List<? super Integer> result = writableNumbers();
result.add(10);                  // Hợp lệ.
result.add(Integer.valueOf(20)); // Hợp lệ.
Object value = result.get(0);    // Hợp lệ.

// Integer x = result.get(0); // Lỗi biên dịch.
// result.add(3.14);          // Lỗi biên dịch.
```

`? super Integer` cho biết kiểu phần tử thực tế có thể là `Integer`, `Number` hoặc `Object`. Nó cho phép thêm `Integer` và subtype của `Integer`, nhưng đọc từ `get` chỉ được bảo đảm là `Object`.

### 6.1. Vì sao `? super T` thường khó hiểu ở API trả về?

Chữ ký:

```java
List<? super Animal> getSink();
```

ngầm nói với caller rằng họ đang nhận một collection có khả năng **tiêu thụ `Animal`**. Điều này có thể đúng nếu API cố ý trả về một vùng đích ghi dữ liệu, nhưng làm mờ các câu hỏi:

- Ai sở hữu danh sách và ai có quyền chỉnh sửa?
- Danh sách có mutable không?
- Caller cần đọc lại các phần tử dưới dạng kiểu nào?
- Tại sao không công khai một hành vi đơn giản hơn như `void addAnimal(Animal item)`?

Ví dụ API ghi thông qua một phương thức thường rõ hơn:

```java
interface AnimalReceiver {
    void addAnimal(Animal animal);
}
```

Đây là đề xuất thiết kế khi API chỉ cung cấp hành động **nhận đối tượng**, không cần công khai cấu trúc `List`. Nó tránh vô tình cho caller quyền gọi những phương thức khác của `List`.

### 6.2. Cận dưới không có nghĩa luôn thêm thành công ở runtime

```java
List<? super Integer> immutable = List.<Number>of(1, 2);
immutable.add(3); // Biên dịch được, nhưng UnsupportedOperationException.
```

`? super T` là lời bảo đảm **an toàn kiểu**, không phải lời bảo đảm implementation hỗ trợ thay đổi. Hợp đồng API cần ghi rõ tính mutable và hành vi khi gọi các thao tác sửa.

## 7. Wildcard ở kiểu trả về có thể lan truyền qua nhiều lớp gọi

Giả sử một thư viện khai báo:

```java
static List<? extends Animal> findAnimals() {
    return List.of(new Dog());
}
```

Ứng dụng có thể chỉ đọc:

```java
static void printAnimals(List<? extends Animal> values) {
    for (Animal value : values) {
        System.out.println(value);
    }
}

printAnimals(findAnimals()); // Hợp lệ.
```

Nhưng nếu một hàm downstream yêu cầu `List<Animal>`:

```java
static void appendCat(List<Animal> values) {
    values.add(new Cat());
}

// appendCat(findAnimals()); // Lỗi biên dịch.
```

Caller phải đổi thiết kế, ví dụ sao chép:

```java
List<Animal> copied = new ArrayList<>(findAnimals());
appendCat(copied);
```

Trong ví dụ này, `appendCat` là một đoạn phương thức minh họa đặt trong cùng class, không phải một chương trình độc lập.

Khi nhiều tầng xử lý liên tục truyền `List<? extends Animal>`, wildcard có thể “lan” sang chữ ký của các phương thức trung gian. Điều đó vẫn **đúng về kiểu** nếu mọi tầng chỉ đọc. Vấn đề xuất hiện khi tầng tiếp theo cần một kiểu chính xác hơn, cần ghi hoặc cần mô tả quan hệ giữa nhiều giá trị.

### 7.1. Không được thu hẹp wildcard dựa trên suy đoán

```java
List<? extends Animal> values = findAnimals();
// List<Dog> dogs = values; // Lỗi biên dịch.
```

Không phải mọi `List<? extends Animal>` đều là `List<Dog>`. Nếu cần `Dog`, phải để API đảm bảo `Dog` ngay từ chữ ký hoặc dùng bộ lọc và kiểm tra runtime thích hợp.

## 8. Thiết kế API thay thế: trả về kiểu cụ thể khi có bảo đảm cụ thể

Nếu API quản lý danh sách `Dog` và hợp đồng bảo đảm kết quả là danh sách các `Dog`, hãy trả về:

```java
static List<Dog> findDogs() {
    return List.of(new Dog());
}
```

Caller nhận đúng thông tin:

```java
List<Dog> dogs = findDogs();
Dog first = dogs.get(0);
```

Đây là lựa chọn đơn giản khi subtype thực sự là một phần của hợp đồng.

**Độ cụ thể không nhất thiết là cấp class triển khai**: bạn vẫn có thể trả về interface `List<Dog>`, `Collection<Dog>` hoặc `Iterable<Dog>`, tùy phương thức mà caller cần. Điều nên rõ là **đối số kiểu phần tử** và hợp đồng sử dụng, không phải bắt caller biết implementation `ArrayList`.

### 8.1. Trả về `List<Animal>` bằng cách tạo bản sao

Nếu API chủ động muốn công khai phần tử dưới dạng `Animal` và cho caller nhận được `List<Animal>`, trong khi dữ liệu gốc là `List<Dog>`, có thể tạo bản sao:

```java
static List<Animal> animalSnapshot(List<Dog> dogs) {
    return new ArrayList<>(dogs);
}
```

`new ArrayList<>(dogs)` xây một `List<Animal>` mới. Nó tách cấu trúc danh sách khỏi danh sách nguồn, nhưng không sao chép sâu các đối tượng `Dog`.

Nếu muốn cung cấp **snapshot không cho sửa**:

```java
static List<Animal> immutableAnimalSnapshot(List<Dog> dogs) {
    return List.copyOf(dogs);
}
```

Việc suy luận type parameter của `List.copyOf` có thể dùng kiểu đích `List<Animal>`. Cần lưu ý `List.copyOf` không chấp nhận phần tử `null`. Không nên quảng bá bản sao là deep copy.

### 8.2. Khi API muốn cho phép thêm, vẫn cần nêu ownership

Trả về `List<Animal>` giúp caller có thể viết `result.add(new Cat())` **về mặt kiểu**; nhưng kết quả có thực sự mutable hay không phụ thuộc implementation. Ví dụ `List.copyOf` trả về danh sách không thể sửa dù kiểu khai báo là `List<Animal>`.

Nên ghi rõ trong hợp đồng API: đây là snapshot bất biến, bản sao mutable do caller sở hữu, hay view dựa trên dữ liệu nội bộ có thể thay đổi theo thời gian. Những lựa chọn đó độc lập với wildcard.

## 9. Thiết kế API thay thế: phương thức generic giữ quan hệ kiểu

Khi người gọi cung cấp thông tin kiểu cần thiết, API có thể dùng một **type parameter có tên** để liên kết đầu vào và đầu ra:

```java
static <T extends Animal> List<T> onlyType(
        List<? extends Animal> source,
        Class<T> requestedType) {

    List<T> result = new ArrayList<>();
    for (Animal animal : source) {
        if (requestedType.isInstance(animal)) {
            result.add(requestedType.cast(animal));
        }
    }
    return result;
}
```

Cách dùng:

```java
List<Animal> source = List.of(new Dog(), new Cat());
List<Dog> dogs = onlyType(source, Dog.class);
Dog firstDog = dogs.get(0);
```

`Class<T>` cung cấp **căn cứ kiểm tra ở runtime** trước khi trả về `List<T>`. Java có thể chứng minh các phần tử trong kết quả đều thuộc `T`, thay vì bắt caller làm unchecked cast.

Đoạn này là mã minh họa đặt trong class với `import java.util.*;`.

### 9.1. Không khai báo type parameter giả tạo để chiều ý caller

Cẩn thận với một API kiểu:

```java
// Ví dụ chữ ký nguy hiểm nếu không có ràng buộc thực:
// static <T extends Animal> List<T> findAllAnimals() { ... }
```

Nếu lời gọi cho phép caller tự ý suy luận `T = Dog` hoặc `T = Cat`, trong khi dữ liệu thực tế có thể chứa cả hai, implementation không có cách an toàn để đáp ứng lời hứa đó chỉ từ tên `T` do caller chọn.

Một generic return type `List<T>` chỉ đúng khi có quan hệ kiểu được bảo đảm: đối số `Class<T>`, tham số đầu vào `List<T>`, một container `Repository<T>` có kiểu cố định, hoặc cơ chế lưu trữ/kiểm tra tương đương.

Không thay wildcard bằng `<T>` chỉ để ép mã biên dịch; phải bảo toàn tính đúng đắn của hợp đồng.

## 10. Thiết kế API thay thế: gắn tham số kiểu với đối tượng sở hữu

Một repository generic có thể xác định loại phần tử ở **thời điểm tạo đối tượng**:

```java
interface Repository<T> {
    List<T> findAll();
}
```

Sau đó:

```java
Repository<Dog> dogRepository = /* một implementation Repository<Dog> */;
List<Dog> dogs = dogRepository.findAll();
```

Ví dụ sử dụng ở trên chỉ minh họa quan hệ kiểu và chưa có implementation cụ thể. Ưu điểm là `T` được liên kết với repository, nên phương thức `findAll()` có thể trả về `List<T>` mà không cần `List<? extends T>`.

Nếu cần không cho caller sửa danh sách, implementation có thể trả `List.copyOf(stored)` hoặc một collection bất biến được mô tả rõ. Kiểu `List<T>` vẫn giữ thông tin kiểu chính xác.

## 11. Khi wildcard return là một chủ ý tốt

Không phải mọi wildcard return đều là “mùi thiết kế”.

### 11.1. Muốn công khai một view chỉ bảo đảm cận trên

Một API tổng hợp dữ liệu từ nhiều nguồn có thể không thể cam kết kiểu phần tử chính xác hơn `Animal`:

```java
static List<? extends Animal> animalsFromSource(boolean dogsOnly) {
    return dogsOnly
            ? List.of(new Dog())
            : List.of(new Cat());
}
```

Ở caller, bảo đảm “đọc được `Animal`” có thể là đủ. `List<? extends Animal>` có thể phản ánh đúng sự không xác định trong contract; nó cũng giữ khả năng implementation trả về collection có kiểu `Dog` hoặc `Cat` mà không sao chép cấu trúc.

Tuy nhiên, nếu **chỉ** muốn caller đọc dưới dạng `Animal`, vẫn có thể cân nhắc `List<Animal>` bằng cách tạo danh sách phù hợp. Lựa chọn phụ thuộc ownership, chi phí sao chép, độ linh hoạt và khả năng thao tác của caller.

### 11.2. Muốn trả về cấu trúc dữ liệu không biết kiểu phần tử

Một API chẩn đoán hoặc khám phá có thể thật sự chỉ bảo đảm `List<?>`:

```java
interface DiagnosticProvider {
    List<?> rawObservations();
}
```

Caller chủ yếu quan tâm số lượng, trình bày và introspection, không kỳ vọng mọi phần tử đều có một kiểu thống nhất ngoài `Object`. Lúc này wildcard nói đúng sự bất định của dữ liệu.

### 11.3. Muốn công khai mục tiêu ghi dữ liệu

Một API rất chuyên biệt có thể trả về `Collection<? super Event>` để người gọi thêm các sự kiện:

```java
interface EventBuffer {
    Collection<? super Event> writableTarget();
}

class Event {}
```

Chữ ký như vậy mô tả **khả năng chấp nhận `Event`**, nhưng cần documentation về mutability, ownership, đồng bộ hóa, tác dụng phụ, thứ tự và cách xử lý lỗi. Nếu nhiệm vụ thật sự chỉ là ghi sự kiện, một phương thức `append(Event event)` thường rõ ràng hơn việc trao ra toàn bộ `Collection`.

## 12. Chương trình Java 17+ hoàn chỉnh

Lưu thành **`WildcardReturnApiDemo.java`**. Chương trình so sánh chữ ký trả về wildcard, kiểu chính xác, snapshot theo kiểu cha và generic method trả kết quả có kiểu được kiểm chứng.

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardReturnApiDemo {
    static class Animal {
        private final String name;

        Animal(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + name + ")";
        }
    }

    static final class Dog extends Animal {
        Dog(String name) {
            super(name);
        }
    }

    static final class Cat extends Animal {
        Cat(String name) {
            super(name);
        }
    }

    static List<Dog> findDogs() {
        return List.of(new Dog("Rex"), new Dog("Bolt"));
    }

    static List<? extends Animal> findAnimalsWildcard() {
        return findDogs();
    }

    static List<Animal> animalSnapshot() {
        return new ArrayList<>(findDogs());
    }

    static <T extends Animal> List<T> filterByClass(
            List<? extends Animal> source,
            Class<T> requestedType) {

        List<T> result = new ArrayList<>();
        for (Animal animal : source) {
            if (requestedType.isInstance(animal)) {
                result.add(requestedType.cast(animal));
            }
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        List<? extends Animal> wildcard = findAnimalsWildcard();
        Animal firstAnimal = wildcard.get(0);
        // wildcard.add(new Dog("Max")); // Lỗi biên dịch.

        List<Dog> typed = findDogs();
        Dog firstDog = typed.get(0);

        List<Animal> snapshot = animalSnapshot();
        snapshot.add(new Cat("Mia"));

        List<Animal> mixed = List.of(
                new Dog("Rex"),
                new Cat("Luna"),
                new Dog("Bolt")
        );
        List<Dog> filtered = filterByClass(mixed, Dog.class);

        List<Object> storage = new ArrayList<>();
        List<? super Animal> writableView = storage;
        writableView.add(new Cat("Coco"));
        Object fromWritable = writableView.get(0);

        System.out.println("wildcard=" + firstAnimal);
        System.out.println("typed=" + firstDog);
        System.out.println("snapshot=" + snapshot);
        System.out.println("filtered=" + filtered);
        System.out.println("super-read=" + fromWritable);
    }
}
```

Biên dịch và chạy:

```bash
javac WildcardReturnApiDemo.java
java WildcardReturnApiDemo
```

Stdout:

```text
wildcard=Dog(Rex)
typed=Dog(Rex)
snapshot=[Dog(Rex), Dog(Bolt), Cat(Mia)]
filtered=[Dog(Rex), Dog(Bolt)]
super-read=Cat(Coco)
```

Giải thích các điểm cần kiểm chứng:

1. `findAnimalsWildcard()` công khai `List<? extends Animal>`; caller đọc được `Animal` nhưng không thể thêm tùy ý.
2. `findDogs()` công khai `List<Dog>`, nên caller nhận trực tiếp `Dog` từ `get`.
3. `animalSnapshot()` sao chép sang `List<Animal>`; caller có thể thêm `Cat` vào **bản sao** vì `ArrayList` mutable.
4. `filterByClass(..., Dog.class)` trả `List<Dog>` nhờ kiểm tra `isInstance` và `cast`, không thực hiện unchecked cast.
5. `List<? super Animal>` hỗ trợ thêm `Cat` vì `Cat <: Animal`, nhưng kết quả `get` chỉ được đảm bảo có kiểu `Object`.

Các `List.of(...)` trong chương trình là danh sách không sửa được. Bản sao `new ArrayList<>(...)` có thể sửa. Cả hai trạng thái tồn tại độc lập với wildcard trong chữ ký.

## 13. Wildcard capture có thể phát sinh ở phía caller

Một hạn chế khác khi trả về `List<?>`: kiểu phần tử tuy không biết cụ thể nhưng **vẫn có một kiểu nhất quán** bên trong từng danh sách.

Ví dụ:

```java
static void swapFirstTwo(List<?> items) {
    swapCaptured(items);
}

private static <T> void swapCaptured(List<T> items) {
    if (items.size() >= 2) {
        T first = items.get(0);
        items.set(0, items.get(1));
        items.set(1, first);
    }
}
```

Phương thức `swapCaptured` dùng một type parameter `T` để giữ quan hệ giữa phần tử đọc ra và phần tử ghi lại. Compiler có thể **capture** wildcard của tham số `List<?>` khi gọi helper. Đây là các đoạn method minh họa đặt bên trong một class với `import java.util.List;`.

Điều cần phân biệt:

- `items.add(new Dog())` trên `List<?>` không an toàn vì `?` có thể là một kiểu không liên quan.
- Hoán đổi hai phần tử **của chính cùng một `List<T>`** là an toàn về kiểu: lấy một `T` ra rồi ghi lại vị trí khác cũng yêu cầu `T`.

Phép `set` có thể ném `UnsupportedOperationException` nếu danh sách không cho phép thay đổi. Nếu API trả về wildcard nhưng caller liên tục phải viết helper chỉ để dùng dữ liệu thông thường, cần cân nhắc lại kiểu trả về.

## 14. Tác động đến suy luận generic và quá tải phương thức

### 14.1. Generic method có thể nhận nguồn wildcard mà không cần ép kiểu

```java
static <T> void copyTo(List<? super T> target, List<? extends T> source) {
    target.addAll(source);
}
```

Caller có thể viết:

```java
List<? extends Number> source = List.of(1, 2);
List<Number> target = new ArrayList<>();

copyTo(target, source);
```

Đoạn này nằm trong class với `import java.util.*;`. `copyTo` cho compiler một type parameter `T` để liên kết hai tham số. Kỹ thuật này hữu ích khi không đổi được API đã trả về wildcard; nó **không** biến nguồn thành `List<Number>`.

### 14.2. Không định nghĩa overload chỉ khác generic wildcard

Không được viết hai overload chỉ khác nhau bởi đối số kiểu bên trong `List` như:

```java
// void inspect(List<? extends Number> values) {}
// void inspect(List<? super Integer> values) {}
```

Hai chữ ký sẽ bị xung đột do **type erasure**: cả hai có cùng dạng tham số raw `List` ở bytecode. Nếu API cần hai hành vi, đặt hai tên có nghĩa riêng hoặc thay đổi tham số sao cho có chữ ký khác thật sự.

Vấn đề erasure áp dụng cho generic parameter nói chung; wildcard return không tạo ra một cơ chế overload đặc biệt.

## 15. Bảng lựa chọn chữ ký API

| Nhu cầu của caller | Chữ ký thường phù hợp | Lý do |
| --- | --- | --- |
| API luôn trả các `Dog` | `List<Dog> findDogs()` | Caller đọc trực tiếp `Dog`, không mất thông tin subtype |
| API công khai các phần tử dưới dạng `Animal` và muốn trả một danh sách có kiểu cố định | `List<Animal> findAnimals()` | Không lan wildcard; phải xây/trả một `List<Animal>` thực sự |
| API chỉ cam kết phần tử là subtype của `Animal` và muốn trả view không phải copy | `List<? extends Animal> findAnimals()` | Giữ độ linh hoạt subtype; caller chủ yếu đọc |
| API không thể cam kết kiểu phần tử cụ thể | `List<?> inspect()` | Caller phải xử lý phần tử như `Object` |
| Caller yêu cầu một subtype và API kiểm chứng được | `<T extends Animal> List<T> find(Class<T> type)` | `T` được ràng buộc qua thông tin đầu vào và kiểm tra thực tế |
| Loại phần tử gắn với repository | `Repository<T>.findAll(): List<T>` | Kiểu `T` được xác định khi tạo repository |
| Caller chỉ cần gửi thêm dữ liệu | `void append(Animal value)` | Công khai hành vi, không lộ collection nội bộ |
| API cố ý đưa ra đích ghi tổng quát | `Collection<? super Animal> writableTarget()` | Cho phép `add(Animal)`, nhưng đọc chỉ bảo đảm `Object` |

Bảng là hướng dẫn lựa chọn, không phải quy tắc một chữ ký cho mọi API. Còn cần xét mutability, null, ownership, hiệu năng sao chép, độ ổn định của hợp đồng và khả năng phát triển API.

## 16. Những hiểu lầm và lỗi thường gặp

### Sai lầm 1: “Trả về wildcard luôn là sai”

Wildcard return là cú pháp hợp lệ và có thể diễn đạt chính xác sự bất định mà API cố ý công khai. Vấn đề là caller có thực sự cần tự xử lý wildcard và các giới hạn kéo theo hay không.

### Sai lầm 2: “`? extends T` làm collection trở thành read-only”

`? extends T` hạn chế **ghi giá trị mới thuộc kiểu không biết** vào collection. Nó không ngăn toàn bộ thao tác sửa như `clear()`; không bảo đảm snapshot, immutable hay thread-safe.

### Sai lầm 3: “Có thể gán kết quả `List<? extends T>` cho `List<T>`”

Generic không tự động biến thiên theo quan hệ subtype của phần tử. Nếu cần `List<T>` riêng, hãy sao chép hoặc thiết kế API trả `List<T>` ngay từ đầu.

### Sai lầm 4: “`List<? super T>` trả về là `List<T>` có thể đọc T”

Không. Kiểu phần tử thực tế có thể là supertype của `T`; kết quả `get` chỉ được bảo đảm là `Object`.

### Sai lầm 5: “Đổi sang `<T> List<T>` là sửa được mọi vấn đề”

Nếu không có nguồn ràng buộc/kiểm chứng `T`, phương thức không thể an toàn hứa rằng dữ liệu thực tế hợp mọi kiểu do caller chọn. Đừng dùng unchecked cast để biến một API sai thành API “trông có vẻ generic”.

### Sai lầm 6: “Trả `List<T>` nghĩa là caller có thể sửa”

Chữ ký `List<T>` cho phép `add(T)` ở compile-time, nhưng implementation có thể trả `List.of`, `List.copyOf` hoặc wrapper unmodifiable. Quyền sửa phải được hợp đồng API xác định.

### Sai lầm 7: “Dùng `Object` hoặc raw `List` để loại wildcard là cải tiến”

`List<Object>` không thể thay thế tùy ý cho `List<String>` bởi invariance. Raw `List` làm suy yếu kiểm tra kiểu. Nếu dữ liệu thật sự không biết kiểu, `List<?>` có thể là cách diễn đạt an toàn nhất.

## 17. Bài tập tự đánh giá

Cho:

```java
class Vehicle {}
class Car extends Vehicle {}
class Bike extends Vehicle {}

static List<? extends Vehicle> loadVehicles() {
    return List.of(new Car(), new Bike());
}
```

### Bài 1. Phép gán nào hợp lệ?

```java
List<? extends Vehicle> a = loadVehicles(); // (1)
// List<Vehicle> b = loadVehicles();         // (2)
Vehicle c = loadVehicles().get(0);          // (3)
// Car d = loadVehicles().get(0);           // (4)
```

**Đáp án:** (1), (3) hợp lệ; (2) sai vì không thể thu hẹp wildcard về `List<Vehicle>`; (4) sai vì kết quả đọc chỉ được bảo đảm là `Vehicle`, có thể là `Bike`.

### Bài 2. Có thể thêm `Car` vào kết quả không?

```java
List<? extends Vehicle> vehicles = loadVehicles();
// vehicles.add(new Car()); // (1)
```

**Đáp án:** Không. Kiểu phần tử thực tế có thể là `Car`, `Bike` hoặc `Vehicle`. Nếu caller cần cấu trúc có thể thêm `Vehicle`, sao chép:

```java
List<Vehicle> editable = new ArrayList<>(vehicles);
editable.add(new Car());
```

### Bài 3. Khi nào nên trả `List<Car>`?

Một phương thức `loadCars()` luôn chỉ trả các `Car`, và caller cần thao tác dựa trên kiểu `Car`. Chọn `List<Car>`, vì nó thể hiện đúng hợp đồng. Nếu API cố ý che subtype nhằm chỉ công khai `Vehicle`, cần đánh giá lý do và những giới hạn phát sinh trước khi dùng `List<? extends Vehicle>`.

### Bài 4. Hãy chỉ ra nguy cơ của chữ ký này

```java
// static <T extends Vehicle> List<T> loadAll() { ... }
```

Nếu dữ liệu thực tế chứa `Car` và `Bike`, caller có thể mong `List<Car>` mà implementation không bảo đảm được. Cần thêm bộ lọc có kiểm chứng như `Class<T>`, hoặc chuyển sang `List<Vehicle>`/mô hình repository có kiểu ràng buộc.

### Bài 5. Tìm chữ ký phù hợp cho nơi nhận dữ liệu

Một API muốn caller gửi `Vehicle` nhưng không yêu cầu caller đọc collection nội bộ. Nên cung cấp:

```java
void addVehicle(Vehicle vehicle);
```

Nó bộc lộ hành vi cần thiết và không ràng buộc caller với cấu trúc lưu trữ `List`. Trong nội bộ implementation vẫn có thể dùng `Collection<? super Vehicle>` làm consumer.

## 18. Checklist khi review chữ ký trả về

1. **Caller cần biết chính xác kiểu phần tử nào?** Nếu có bảo đảm rõ ràng, cân nhắc `List<T>` cụ thể.
2. **Caller chỉ đọc hay cũng cần ghi?** Với `? extends T`, đừng kỳ vọng có thể thêm `T`; với `? super T`, đừng kỳ vọng đọc ra `T`.
3. **Wildcard có truyền tiếp qua nhiều tầng code không?** Nếu người dùng phải liên tục cast/copy/wrapper, xem lại hợp đồng public.
4. **Có cần giữ subtype chính xác không?** Khi có, trả `List<Dog>` thay vì vô cớ làm rộng thành `List<? extends Animal>`.
5. **Có nguồn kiểm chứng cho generic return `<T>` không?** Tham số `Class<T>`, đối số `List<T>` hoặc đối tượng sở hữu `Repository<T>` là các mẫu thông thường.
6. **API có trả view hay snapshot?** Nêu rõ dữ liệu có chia sẻ với nội bộ, có đổi theo thời gian hay không, và nếu sao chép thì sao chép nông hay sâu.
7. **Danh sách có mutable không?** Wildcard và mutability độc lập; document rõ khả năng thêm/xóa/sửa và các ngoại lệ runtime.
8. **Có cần trả cả collection không?** Nếu mục tiêu chỉ là nhận dữ liệu, một hành vi `add(T)` có thể là API phù hợp hơn.
9. **Có đang dựa vào unchecked cast/raw type để xóa lỗi không?** Xem lại kiểu gốc; đừng bỏ bảo vệ của compiler.

## 19. Tài liệu đọc thêm

- Oracle Java Tutorials — [Guidelines for Wildcard Use](https://docs.oracle.com/javase/tutorial/java/generics/wildcardGuidelines.html): producer/consumer và lưu ý về wildcard return type.
- Oracle Java Tutorials — [Wildcards and Subtyping](https://docs.oracle.com/javase/tutorial/java/generics/subtyping.html): quan hệ giữa các kiểu generic chứa wildcard.
- Oracle Java Tutorials — [Wildcard Capture and Helper Methods](https://docs.oracle.com/javase/tutorial/java/generics/capture.html): cách xử lý wildcard chưa biết bằng generic helper.
- Java Language Specification 17 — [§4.5.1 Type Arguments of Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1): cú pháp, containment và ý nghĩa wildcard.
- Java SE 17 API — [List.copyOf(Collection)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)): snapshot unmodifiable, hành vi null và hợp đồng sao chép.

**Kết luận:** PECS hữu ích nhất khi xác định **hướng dữ liệu của tham số generic**. Với kiểu trả về, mục tiêu là truyền cho caller **đúng và đủ thông tin kiểu** để sử dụng API. Dùng `List<T>` khi `T` được bảo đảm; dùng wildcard khi sự không xác định của kiểu phần tử chính là điều API muốn công khai; và luôn mô tả riêng mutability, ownership và khả năng sao chép.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pecs-008">Đọc chữ ký Collections.copy, addAll và Comparator</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc chữ ký `Collections.copy`, `addAll` và `Comparator` qua quy tắc PECS

Khi đọc mã nguồn, tài liệu Javadoc hoặc chữ ký phương thức của Java Collections Framework, chúng ta thường gặp các kiểu như `? extends T` và `? super T`. Thay vì học thuộc từng API, hãy đọc chữ ký theo **luồng dữ liệu**: tham số nào **cung cấp** giá trị thuộc kiểu `T` cho phương thức, và tham số nào **nhận** hoặc **tiêu thụ** các giá trị `T` đó?

Quy tắc **PECS — Producer Extends, Consumer Super** giúp giải mã các chữ ký quan trọng:

~~~java
// Các chữ ký đã giản lược tên package và modifier để dễ đọc.
public static <T> void copy(
        List<? super T> dest,
        List<? extends T> src);

public boolean addAll(Collection<? extends E> c); // Collection<E>

public static <T> boolean addAll(
        Collection<? super T> c,
        T... elements); // Collections

public void sort(Comparator<? super E> c); // List<E>
~~~

Những dòng trên là **chữ ký để phân tích**, không phải một chương trình có thể biên dịch độc lập. `Collections.copy` sao chép vào các vị trí có sẵn; `Collection.addAll` nối các phần tử từ một collection nguồn; `Collections.addAll` nhận những phần tử varargs; `List.sort` tiêu thụ một `Comparator` có khả năng so sánh các phần tử trong danh sách.

Bài học tập trung vào cách **đọc chính xác từng chữ ký**, xác định đối số kiểu có thể truyền, hiểu lỗi compile-time, và phân biệt các điều kiện runtime mà generic không bảo đảm.

## 1. Nhắc lại quy tắc cần để đọc API

Generic class/interface Java thông thường **bất biến** theo đối số kiểu:

~~~java
List<Integer> integers = new ArrayList<>();
// List<Number> numbers = integers; // LỖI BIÊN DỊCH.
~~~

Đây là **đoạn minh họa** (cần import `java.util.*` và đặt trong class). Mặc dù `Integer` là subtype của `Number`, `List<Integer>` không là subtype của `List<Number>`. Nếu phép gán được chấp nhận, mã khác có thể gọi `numbers.add(3.5)` và phá vỡ hợp đồng của `List<Integer>`.

Wildcard giữ an toàn kiểu mà vẫn cho phép sử dụng quan hệ subtype theo **một chiều thao tác phù hợp**:

| Loại | Ý nghĩa với phần tử `T` | Khả năng biên dịch cốt lõi |
| --- | --- | --- |
| `? extends T` | Kiểu thực là `T` hoặc **subtype** của `T` | Đọc phần tử thành `T`; không thêm một `T` bất kỳ |
| `? super T` | Kiểu thực là `T` hoặc **supertype** của `T` | Thêm `T`; đọc ra chỉ bảo đảm `Object` |
| `T` không có wildcard | Đối số kiểu chính xác, khi `T` đã xác định | Có thể đọc/ghi `T` theo hợp đồng của container |

Góc nhìn `extends` không có nghĩa collection thực sự immutable. Nó vẫn có thể cho xóa hoặc xóa sạch; ngược lại, góc nhìn `super` không bảo đảm đối tượng collection có thể sửa ở runtime. PECS chủ yếu là **hợp đồng tương thích kiểu**.

Ta sẽ lần lượt đọc các phương thức theo bốn câu hỏi:

1. **`T` hoặc `E` là gì?** Nó được khai báo ở class hay ở chính phương thức?
2. **Tham số nào là nguồn, tham số nào là đích?**
3. **Các kiểu con/cha nào được chấp nhận?** Suy ra từ `extends` hoặc `super`.
4. **Khi chạy, phương thức làm gì với cấu trúc collection?** Nối thêm, ghi đè, sắp xếp và điều kiện tiền đề khác nhau.

## 2. Giải mã `Collections.copy`: nguồn `extends`, đích `super`

Chữ ký của tiện ích `java.util.Collections`:

~~~java
public static <T> void copy(
        List<? super T> dest,
        List<? extends T> src);
~~~

Đây là **chữ ký phương thức để đọc**, không cần tự khai báo lại API này.

Đọc từ trái sang phải:

- `<T>`: **type parameter của phương thức**. Compiler có thể suy luận `T` từ đối số truyền vào hoặc caller có thể chọn tường minh.
- `dest: List<? super T>`: đích có đối số kiểu là `T` hoặc supertype của `T`, nên nhận được mọi phần tử được dùng như `T`.
- `src: List<? extends T>`: nguồn có đối số kiểu là `T` hoặc subtype của `T`, nên đọc các phần tử nguồn thành `T` an toàn.
- `void`: phương thức thay đổi `dest` và không trả về danh sách mới.
- `static`: gọi bằng `Collections.copy(dest, src)`, không gọi trên instance của `List`.

Từ chữ ký đã suy ra quan hệ hợp lệ: ví dụ có thể chuyển các phần tử `Integer` từ `List<Integer>` sang `List<Number>` hoặc `List<Object>` mà không ép kiểu.

### 2.1. Ví dụ chạy được và đầu ra

Tạo **`CollectionsCopyReadingDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollectionsCopyReadingDemo {
    public static void main(String[] args) {
        List<Integer> source = List.of(4, 5);
        List<Number> destination =
                new ArrayList<>(List.of(0, 0, 0));

        Collections.<Number>copy(destination, source);

        System.out.println("source = " + source);
        System.out.println("destination = " + destination);
        System.out.println("source size = " + source.size());
        System.out.println("destination size = " + destination.size());
    }
}
~~~

Biên dịch/chạy bằng Java 17 trở lên:

~~~bash
javac CollectionsCopyReadingDemo.java
java CollectionsCopyReadingDemo
~~~

Stdout chính xác:

~~~text
source = [4, 5]
destination = [4, 5, 0]
source size = 2
destination size = 3
~~~

`Collections.<Number>copy(...)` xác định rõ `T = Number`. Khi ấy:

- `src` yêu cầu `List<? extends Number>`, nên `List<Integer>` hợp lệ.
- `dest` yêu cầu `List<? super Number>`, nên `List<Number>` hợp lệ.
- Phương thức **ghi đè phần tử tại index 0 và 1** của đích, giữ nguyên phần tử index 2.
- Nó không thay đổi kích thước collection đích và không thay đổi nguồn.

Cũng có thể viết `Collections.copy(destination, source)`; compiler thường suy luận được kiểu phù hợp trong ví dụ này. Cú pháp `<Number>` chỉ nhằm làm lộ rõ cách áp dụng cận `extends`/`super` khi đọc chữ ký.

### 2.2. Điều kiện runtime quan trọng: không tự append

`Collections.copy` yêu cầu `dest.size() >= src.size()` ngay trước khi sao chép. Nếu đích ngắn hơn nguồn, nó ném `IndexOutOfBoundsException`, dù hai kiểu generic tương thích.

Ví dụ **cố ý gây lỗi runtime**:

~~~java
List<Integer> src = List.of(1, 2);
List<Number> dest = new ArrayList<>();

Collections.copy(dest, src); // Biên dịch nhưng ném IndexOutOfBoundsException.
~~~

Đoạn này cần import `java.util.*` và đặt trong class; mục tiêu chỉ để phân biệt hợp đồng runtime với kiểm tra kiểu. Ngoài ra, `dest` phải cho phép cập nhật bằng `set(index, value)`; danh sách không hỗ trợ sửa có thể ném `UnsupportedOperationException`.

**Đừng suy luận rằng `copy` tạo bản sao `List` mới.** Nó ghi vào **danh sách đích đã tồn tại**. Khi cần nối phần tử vào cuối, dùng `addAll`; khi cần một collection mới, dùng constructor như `new ArrayList<Number>(source)`.

### 2.3. Trường hợp gán đích/ngược chiều bị cấm

~~~java
List<Integer> integers = new ArrayList<>(List.of(1, 2));
List<Number> numbers = new ArrayList<>(List.of(0, 0));

Collections.copy(numbers, integers); // Hợp lệ.
// Collections.copy(integers, numbers); // LỖI BIÊN DỊCH.
~~~

Tại chiều bị cấm, nguồn có thể đưa ra một `Double` qua `List<Number>`, trong khi đích `List<Integer>` chỉ nhận `Integer`. Wildcard chặn thao tác không an toàn **ngay tại vị trí gọi**.

## 3. `Collection<E>.addAll(Collection<? extends E>)`: collection nhận dữ liệu, đối số là nguồn

Chữ ký instance method trong giao diện `Collection<E>`:

~~~java
boolean addAll(Collection<? extends E> c);
~~~

Đây là **chữ ký giản lược để phân tích**. Cần xác định `E` là gì: `E` **thuộc kiểu generic của receiver**, tức đối tượng đứng bên trái dấu chấm khi gọi `destination.addAll(source)`.

Ví dụ:

~~~java
List<Number> destination = new ArrayList<>();
List<Integer> source = List.of(10, 20);

boolean changed = destination.addAll(source);
~~~

Ở đây receiver có kiểu `List<Number>`, nên `E = Number`. Tham số `c` trở thành `Collection<? extends Number>` và có thể nhận `List<Integer>`. Ta đọc chữ ký:

- `destination` **nhận thêm** các `Number` (và subtype).
- `source` **cung cấp** các phần tử tương thích `Number` cho receiver.
- Vì tham số `source` là producer, chữ ký dùng `extends`.
- Giá trị trả về `boolean` biểu thị collection **có thay đổi hay không** do lời gọi, không phải số phần tử được thêm.
- Phương thức thêm các phần tử vào receiver theo hợp đồng của implementation; ví dụ `List` thường thêm ở cuối, còn `Set` chỉ giữ phần tử chưa trùng theo quy tắc equality của set.

Cần chú ý: ở chữ ký của `Collection.addAll`, **không có tham số `? super E` cho destination** vì chính receiver đã được khai báo `Collection<E>`. Trong `Collections.copy`, cả nguồn và đích đều được truyền làm tham số, nên cả hai chiều được viết tường minh.

### 3.1. Chương trình chạy được: `List.addAll` và `Set.addAll`

Tạo **`CollectionAddAllDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CollectionAddAllDemo {
    public static void main(String[] args) {
        List<Integer> integers = List.of(2, 4);
        List<Double> doubles = List.of(1.5, 2.5);
        List<Number> numbers = new ArrayList<>();

        boolean firstChange = numbers.addAll(integers);
        boolean secondChange = numbers.addAll(doubles);

        System.out.println("numbers = " + numbers);
        System.out.println("changes = " + firstChange + ", " + secondChange);

        Set<Number> unique = new LinkedHashSet<>();
        unique.add(2);
        boolean unchanged = unique.addAll(integers);

        System.out.println("unique = " + unique);
        System.out.println("unchanged = " + unchanged);
    }
}
~~~

Chạy:

~~~bash
javac CollectionAddAllDemo.java
java CollectionAddAllDemo
~~~

Stdout chính xác:

~~~text
numbers = [2, 4, 1.5, 2.5]
changes = true, true
unique = [2, 4]
unchanged = true
~~~

Ở đây `unique` ban đầu chỉ chứa `2`, nên `addAll(integers)` thêm được `4` và trả về `true`. Tên biến `unchanged` dễ gây hiểu lầm: boolean từ `addAll` báo **collection có thay đổi hay không**, không phải trạng thái 'không đổi'. Nếu `unique` đã chứa cả `2` và `4` thì cùng lời gọi sẽ trả về `false`.

### 3.2. Khi `addAll` không tương thích kiểu

~~~java
List<Integer> integers = new ArrayList<>();
List<Number> numbers = List.of(1, 2.5);

// integers.addAll(numbers); // LỖI BIÊN DỊCH.
~~~

Receiver `integers` có `E = Integer`, nên tham số phải là `Collection<? extends Integer>`. `List<Number>` không bảo đảm mọi phần tử là `Integer`. Điều này giải thích vì sao hướng `numbers.addAll(integers)` hợp lệ, còn hướng ngược lại không.

### 3.3. Các điều kiện ngoài PECS

- `List.addAll` thường nối thêm phần tử vào cuối theo thứ tự iterator của nguồn; `Set.addAll` có thể bỏ phần tử trùng, tùy semantics của `equals`/`hashCode`.
- Receiver có thể không hỗ trợ sửa và ném `UnsupportedOperationException` (ví dụ `List.of(...)`).
- Một collection có thể hạn chế `null` hoặc kiểu phần tử theo đặc thù runtime dù chữ ký generic đã đúng.
- `Collection.addAll` có quy định về trường hợp source cũng chính là collection đang được thêm vào; không nên giả định mọi implementation xử lý self-add an toàn, đặc biệt khi nó không rỗng.

## 4. `Collections.addAll(Collection<? super T>, T...)`: phía đích được diễn đạt bằng `super`

Java có **một API khác trùng tên `addAll`**: phương thức tĩnh trong lớp tiện ích `java.util.Collections`:

~~~java
public static <T> boolean addAll(
        Collection<? super T> c,
        T... elements);
~~~

Đừng nhầm nó với `Collection<E>.addAll(Collection<? extends E>)`. Hai phương thức có **receiver và hướng dữ liệu** khác nhau.

Phân tích:

- `<T>`: một type parameter **của chính phương thức**, được suy luận từ các argument hoặc chọn tường minh.
- `c: Collection<? super T>`: collection là **consumer** của các phần tử `T`; chấp nhận collection của `T` hoặc supertype của `T`.
- `T... elements`: các giá trị `T` sẽ được **cung cấp** để thêm vào collection; đây là varargs, không phải một `Collection<? extends T>`.
- `boolean`: trả true nếu collection thay đổi sau thao tác.

### 4.1. Chương trình chạy được: dùng `Collections.addAll`

Tạo **`CollectionsAddAllDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class CollectionsAddAllDemo {
    public static void main(String[] args) {
        Collection<Number> numbers = new ArrayList<>();
        Collection<Object> objects = new ArrayList<>();

        boolean addedNumbers =
                Collections.<Number>addAll(numbers, 1, 2.5, 3L);
        boolean addedObjects =
                Collections.<Integer>addAll(objects, 7, 8);

        System.out.println("numbers = " + numbers);
        System.out.println("objects = " + objects);
        System.out.println("changed = " + addedNumbers + ", " + addedObjects);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac CollectionsAddAllDemo.java
java CollectionsAddAllDemo
~~~

Stdout:

~~~text
numbers = [1, 2.5, 3]
objects = [7, 8]
changed = true, true
~~~

Từ ký pháp tường minh `Collections.<Number>addAll(...)`:

- Các giá trị `1`, `2.5` và `3L` lần lượt được boxing thành `Integer`, `Double` và `Long`, đều tương thích với `Number`.
- `Collection<Number>` là một `Collection<? super Number>` hợp lệ.
- Ở `Collections.<Integer>addAll(objects, 7, 8)`, đích `Collection<Object>` phù hợp vì `Object` là supertype của `Integer`.
- Các giá trị `Number` khi in bằng `toString()` cho chuỗi `1`, `2.5`, `3` như stdout.

Trong mã ứng dụng thường có thể bỏ type argument tường minh để compiler suy luận, ví dụ `Collections.addAll(numbers, 1, 2.5, 3L)`. Ở đây ta viết `<Number>` để người học nhìn thấy mối liên hệ với `? super T`.

### 4.2. Đặt hai loại `addAll` cạnh nhau

| API | Dạng gọi | Ai là nguồn? | Ai là đích? | Vị trí wildcard | Giá trị trả về |
| --- | --- | --- | --- | --- | --- |
| `Collection<E>.addAll(Collection<? extends E>)` | `dest.addAll(src)` | Tham số collection `src` | Receiver `dest` | `src` có `extends E` | `boolean` |
| `Collections.<T>addAll(Collection<? super T>, T...)` | `Collections.addAll(dest, a, b)` | Các giá trị varargs `a`, `b` | Tham số collection `dest` | `dest` có `super T` | `boolean` |

Điểm chung là **dữ liệu đi vào collection đích với kiểu an toàn**. Điểm khác là một API đọc từ một collection nguồn, còn API kia nhận các phần tử riêng lẻ qua varargs.

Ví dụ nhận diện sai cú pháp:

~~~java
List<Number> numbers = new ArrayList<>();
List<Integer> integers = List.of(1, 2);

numbers.addAll(integers); // Hợp lệ: instance addAll.
// Collections.addAll(numbers, integers); // Không phải cách thêm từng Integer.
~~~

Dòng tĩnh cuối nếu không chú thích sẽ gây **lỗi kiểu**: `Collections.addAll(numbers, integers)` cố đưa **chính đối tượng `List<Integer>` như một phần tử** vào `Collection<Number>`, trong khi `List<Integer>` không phải `Number`. Muốn dùng phiên bản tĩnh, truyền từng phần tử phù hợp: `Collections.addAll(numbers, 3, 4)`.

## 5. Vì sao `Comparator<? super T>` cũng là Consumer Super?

`Comparator<T>` không chứa danh sách. Nó biểu diễn một đối tượng có phương thức so sánh **hai giá trị `T`**:

~~~java
@FunctionalInterface
public interface Comparator<T> {
    int compare(T left, T right);
}
~~~

Đây là **chữ ký minh họa giản lược** của `Comparator`: trong JDK thật, interface còn khai báo và kế thừa các phương thức khác. Đừng tạo lại interface này khi sử dụng Java Collections Framework.

Phương thức so sánh **nhận** hai giá trị `T` từ caller, nên comparator đóng vai trò **consumer của các giá trị `T`**. Nếu một comparator so sánh được `Number` nói chung, nó đương nhiên so sánh được các `Integer`, là subtype của `Number`.

Do đó, `List<E>.sort` khai báo:

~~~java
default void sort(Comparator<? super E> c);
~~~

và `Collections.sort` có overload:

~~~java
public static <T> void sort(
        List<T> list,
        Comparator<? super T> c);
~~~

Đây là **các chữ ký để phân tích**, không phải mã class chạy độc lập. Ta đọc chúng như sau:

- Danh sách lưu chính xác các giá trị kiểu `E`/`T`.
- Comparator được **truyền vào để tiêu thụ** các phần tử từ danh sách, nên chấp nhận comparator có đối số kiểu `E`/`T` hoặc một **supertype**.
- Chỉ dùng `Comparator<E>` sẽ hạn chế không cần thiết: một `Comparator<Number>` xử lý được `Integer` nhưng sẽ không được truyền cho `List<Integer>`.
- Chiều ngược lại không an toàn: một `Comparator<Integer>` không thể được bảo đảm so sánh mọi phần tử của `List<Number>`, vì danh sách có thể chứa `Double`.

### 5.1. Chương trình chạy được: `Comparator<Number>` sắp xếp `List<Integer>`

Tạo **`ComparatorSuperDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ComparatorSuperDemo {
    public static void main(String[] args) {
        Comparator<Number> byNumericValue = (a, b) ->
                Double.compare(a.doubleValue(), b.doubleValue());

        List<Integer> integers = new ArrayList<>(List.of(9, 2, 5));
        integers.sort(byNumericValue);
        System.out.println("integers = " + integers);

        List<Number> mixed = new ArrayList<>(List.of(3.5, 1, 2.0));
        Collections.sort(mixed, byNumericValue);
        System.out.println("mixed = " + mixed);
    }
}
~~~

Chạy:

~~~bash
javac ComparatorSuperDemo.java
java ComparatorSuperDemo
~~~

Stdout chính xác:

~~~text
integers = [2, 5, 9]
mixed = [1, 2.0, 3.5]
~~~

Tại `integers.sort(byNumericValue)`, `E = Integer`, còn tham số của `sort` là `Comparator<? super Integer>`. `Comparator<Number>` phù hợp vì `Number` là supertype của `Integer`. Phương thức `compare(Number, Number)` có thể nhận các `Integer` bất kỳ.

`Collections.sort(mixed, byNumericValue)` cũng hợp lệ vì danh sách của `Number` có comparator chính xác `Comparator<Number>`.

Lưu ý rằng comparator ví dụ biến mỗi số thành `double` để so sánh. Cách đó chỉ để minh họa quan hệ kiểu; với các số nguyên rất lớn, `BigDecimal` có yêu cầu chính xác đặc thù hoặc giá trị `NaN`, không nên dùng cách so sánh này một cách máy móc trong hệ thống thực. Chữ ký `Comparator<? super T>` bảo đảm **tương thích kiểu**, không bảo đảm comparator có thứ tự hợp lý hoặc nhất quán với `equals`.

### 5.2. Ví dụ bất hợp lệ theo hướng ngược lại

~~~java
Comparator<Integer> onlyIntegers = Integer::compare;
List<Number> mixed = new ArrayList<>(List.of(1, 2.5));

// mixed.sort(onlyIntegers); // LỖI BIÊN DỊCH.
~~~

Đoạn này cần import `java.util.*` và đặt trong class. `List<Number>` có thể chứa `Double`, mà `Comparator<Integer>` không hứa xử lý. Compiler từ chối để tránh việc comparator bị gọi với giá trị không phù hợp.

Tương tự, `List<Integer>.sort` được phép nhận một `Comparator<Object>` nếu comparator ấy có logic so sánh mọi `Object` phù hợp. Hợp đồng kiểu không nói rằng cách so sánh đó có nghĩa toán học; đây là trách nhiệm của implementation comparator.

## 6. Một bảng đối chiếu ba API theo luồng dữ liệu

| API/chữ ký | Dữ liệu đi từ đâu? | Dữ liệu đi tới đâu? | Wildcard producer | Wildcard consumer |
| --- | --- | --- | --- | --- |
| `Collections.copy(List<? super T> dest, List<? extends T> src)` | `src` | `dest`, ghi đè các vị trí đã có | `src: ? extends T` | `dest: ? super T` |
| `Collection<E>.addAll(Collection<? extends E> c)` | Tham số `c` | Receiver `this` | `c: ? extends E` | Đích là receiver `Collection<E>` |
| `Collections.addAll(Collection<? super T> c, T... elements)` | Các đối số varargs | Tham số `c` | Giá trị `T...` đã định kiểu | `c: ? super T` |
| `List<E>.sort(Comparator<? super E> c)` | Các phần tử của receiver `List<E>` | `c.compare` tiêu thụ hai `E` | Danh sách phát ra giá trị `E` | `c: ? super E` |

Hãy đặc biệt chú ý rằng **consumer không nhất thiết là một collection**: comparator, callback hoặc hàm có đầu vào kiểu `T` đều có thể đóng vai trò consumer.

Với mỗi chữ ký, hãy tự diễn đạt: “Đối tượng này **đọc/trả** `T` cho ai?” hoặc “Đối tượng này **nhận** `T` từ ai?”. Dấu `extends`/`super` sẽ trở nên tự nhiên.

## 7. Đọc generic type inference đúng cách

Hai loại type parameter rất dễ lẫn:

- **`E` thuộc `Collection<E>`/`List<E>`:** thường được xác định từ kiểu của receiver, ví dụ `List<Number> numbers` → `E = Number` tại `numbers.addAll(...)`.
- **`T` thuộc phương thức tĩnh `Collections.<T>copy` hoặc `Collections.<T>addAll`:** được suy luận ở **từng lời gọi** từ các tham số, hoặc được caller chỉ định rõ.

Ví dụ:

~~~java
List<Integer> integers = List.of(1, 2);
List<Number> numbers = new ArrayList<>(List.of(0, 0));

Collections.<Number>copy(numbers, integers); // Hợp lệ.
Collections.<Integer>copy(numbers, integers); // Cũng hợp lệ.
~~~

Đây là **đoạn minh họa cần import `java.util.*` và đặt trong class**.

Với `T = Number`: nguồn `List<Integer>` phù hợp `List<? extends Number>`; đích `List<Number>` phù hợp `List<? super Number>`.

Với `T = Integer`: nguồn `List<Integer>` phù hợp `List<? extends Integer>`; đích `List<Number>` phù hợp `List<? super Integer>`.

**Không nhất thiết `T` phải bằng đúng đối số kiểu của nguồn hoặc đích.** Chỉ cần tồn tại một `T` thỏa đồng thời **nguồn ở phía dưới** và **đích ở phía trên** quan hệ subtype. Đây là lý do phương thức generic có thể biểu diễn nhiều kết hợp hơn kiểu bất biến cố định.

Ngược lại:

~~~java
List<Integer> integers = new ArrayList<>(List.of(1, 2));
List<Number> numbers = List.of(3, 4.5);

// Collections.<Number>copy(integers, numbers); // LỖI BIÊN DỊCH.
// Collections.<Integer>copy(integers, numbers); // LỖI BIÊN DỊCH.
~~~

Với `T = Number`, `List<Integer>` không là đích `List<? super Number>`. Với `T = Integer`, `List<Number>` không là nguồn `List<? extends Integer>`. Hai hướng đều không thỏa đồng thời cận kiểu.

### Đừng nhầm thứ tự tham số

`Collections.copy(dest, src)` đặt **đích trước, nguồn sau**. Điều này khác với một số hàm tự thiết kế có chữ ký `copy(source, destination)`. Khi sử dụng API thư viện, hãy đọc **tên tham số và Javadoc**, đừng chỉ suy từ tên `copy`.

## 8. Ba chương trình sai kiểu có chủ đích và cách sửa

### Sai kiểu 1 — Dùng `Collections.copy` để nạp một đích rỗng

~~~java
List<Integer> src = List.of(1, 2);
List<Number> dest = new ArrayList<>();

// Collections.copy(dest, src); // Biên dịch nhưng runtime IndexOutOfBoundsException.
dest.addAll(src); // Đúng nếu mục tiêu là nối hai phần tử.
~~~

`Collections.copy` là thao tác ghi đè, còn `addAll` là thao tác thêm vào collection. Thay API theo **hành vi cần thiết**, không chỉ theo cận generic.

### Sai kiểu 2 — Dùng phiên bản `Collections.addAll` để thêm cả collection

~~~java
List<Integer> src = List.of(1, 2);
List<Number> dest = new ArrayList<>();

// Collections.addAll(dest, src); // Sai: List<Integer> không phải Number.
dest.addAll(src);                  // Đúng: collection là nguồn.
Collections.addAll(dest, 3, 4);   // Đúng: mỗi phần tử là Number.
~~~

Nếu muốn gọi `Collections.addAll` với các phần tử từ source, có thể tự duyệt source hoặc biến đổi thành mảng phù hợp, nhưng thường `dest.addAll(src)` đã đúng và đơn giản hơn.

### Sai kiểu 3 — Dùng comparator quá hẹp cho danh sách

~~~java
Comparator<Integer> integerOnly = Integer::compare;
List<Number> mixed = new ArrayList<>(List.of(1, 2.5));

// mixed.sort(integerOnly); // LỖI: comparator không chấp nhận mọi Number.
Comparator<Number> numeric =
        (a, b) -> Double.compare(a.doubleValue(), b.doubleValue());
mixed.sort(numeric); // Hợp lệ.
~~~

Dùng comparator phù hợp **ít nhất với toàn bộ miền kiểu phần tử của list**. Không ép kiểu unchecked để bỏ qua cảnh báo compiler.

## 9. Các điều kiện runtime ngoài chữ ký

Đọc đúng chữ ký generic là bước cần thiết nhưng **chưa đủ** để khẳng định lời gọi chạy thành công.

### 9.1. `Collections.copy` cần danh sách đích đủ dài và có thể thay đổi

- `dest.size() < src.size()`: `IndexOutOfBoundsException`.
- `dest` không hỗ trợ cập nhật phần tử: có thể là `UnsupportedOperationException`.
- `dest.size() > src.size()`: các phần tử còn lại ở đích không bị thay đổi; kích thước giữ nguyên.
- Phương thức **sao chép các tham chiếu phần tử**, không tự động clone sâu các đối tượng.

### 9.2. `addAll` chịu quy tắc của collection đích

- `ArrayList.addAll` thêm phần tử và thường giữ thứ tự nguồn được duyệt.
- `Set.addAll` có thể không làm thay đổi collection nếu các phần tử đã tồn tại.
- Collection không hỗ trợ sửa có thể ném `UnsupportedOperationException`.
- Có collection hạn chế `null` hoặc phần tử không phù hợp theo quy tắc riêng.
- `boolean` cho biết **collection đã thay đổi hay chưa**; không phải một con số đếm.

### 9.3. `sort` cần comparator có hợp đồng nhất quán

`Comparator<? super T>` chỉ khẳng định comparator **nhận được các đối tượng kiểu `T`**. Nó không tự bảo đảm so sánh bắc cầu, nhất quán về thứ tự hoặc xử lý `null`. Comparator vi phạm hợp đồng tổng quát có thể khiến phép sắp xếp cho kết quả sai hoặc ném `IllegalArgumentException` trong một số tình huống. `List.of(...)` không hỗ trợ sắp xếp tại chỗ vì list không cho sửa.

PECS giải quyết **quan hệ kiểu compile-time**; các điều kiện về kích thước, mutability, thứ tự và semantics của comparator thuộc hợp đồng runtime của API.

## 10. Đọc thêm: `Comparator` có thể vừa tiêu thụ vừa tạo kết quả

Một comparator **nhận** hai giá trị `T` vào `compare(T, T)` rồi **trả về** một số nguyên biểu diễn thứ tự. Nó là *consumer của T* dù vẫn có output kiểu `int`.

Khi sử dụng method reference hoặc lambda, kiểu lambda thường được suy ra từ **target type**:

~~~java
Comparator<Number> compareNumbers = (a, b) ->
        Double.compare(a.doubleValue(), b.doubleValue());

List<Integer> integers = new ArrayList<>(List.of(5, 2, 9));
integers.sort(compareNumbers);
~~~

Các tham số `a` và `b` được compiler hiểu là `Number` theo target type `Comparator<Number>`. Khi `List<Integer>.sort` gọi comparator, mỗi `Integer` vẫn là một `Number` hợp lệ. Ta không cần tạo `Comparator<Integer>` mới.

Nếu triển khai một hàm nhận comparator:

~~~java
static <T> void sortItems(List<T> items, Comparator<? super T> ordering) {
    items.sort(ordering);
}
~~~

Đây là **phương thức minh họa cần import `java.util.*`**. `items` giữ đối số kiểu chính xác `T` để có thể sắp xếp các phần tử của nó, còn `ordering` được khai báo consumer super để chấp nhận comparator của `T` hoặc kiểu cha.

Điều này thể hiện PECS có thể dùng cho **interface nhận tham số**, không riêng collection.

## 11. Bảng bài tập: dự đoán khả năng biên dịch

Giả sử các biến khai báo bên trong một phương thức với `import java.util.*;`:

~~~java
List<Integer> integers = new ArrayList<>(List.of(1, 2));
List<Double> doubles = new ArrayList<>(List.of(2.5, 3.5));
List<Number> numbers = new ArrayList<>(List.of(0, 0));
List<Object> objects = new ArrayList<>();
Comparator<Number> numberComparator =
        (a, b) -> Double.compare(a.doubleValue(), b.doubleValue());
Comparator<Integer> integerComparator = Integer::compare;
~~~

Hãy phân tích từng biểu thức **độc lập**, không nối các biểu thức thành một chương trình rồi suy luận trạng thái runtime về sau:

| Biểu thức | Biên dịch? | Lý do |
| --- | --- | --- |
| `Collections.copy(numbers, integers);` | Có | Nguồn `Integer`, đích `Number`; PECS phù hợp |
| `Collections.copy(objects, integers);` | Có | Đích `Object` cũng nhận `Integer` |
| `Collections.copy(integers, numbers);` | Không | Nguồn `Number` không chắc là `Integer` |
| `numbers.addAll(integers);` | Có | Receiver có `E = Number`, nguồn `? extends Number` |
| `integers.addAll(numbers);` | Không | Receiver có `E = Integer`, nguồn không chắc là `Integer` |
| `Collections.addAll(numbers, 3, 4.5);` | Có | Có kiểu `T` tương thích với `Number` và các giá trị |
| `Collections.<Number>addAll(objects, 3, 4.5);` | Có | `Object` là supertype của `Number` |
| `Collections.<Number>addAll(integers, 3);` | Không | `Integer` không phải supertype của `Number` |
| `integers.sort(numberComparator);` | Có | `Comparator<Number>` tiêu thụ được `Integer` |
| `numbers.sort(integerComparator);` | Không | Comparator quá hẹp cho mọi `Number` |
| `Collections.sort(integers, numberComparator);` | Có | `Comparator<? super Integer>` nhận `Comparator<Number>` |
| `Collections.copy(numbers, doubles);` | Có | `Double` là subtype của `Number` |

Lưu ý riêng: với `Collections.copy(objects, integers)`, lời gọi **biên dịch**, nhưng `objects` ban đầu rỗng nên sẽ ném `IndexOutOfBoundsException` nếu chạy trong trạng thái đã khai báo. Đây không phải lỗi generic.

### Tự đặt câu hỏi trước khi xem compiler

1. `Collection.addAll` nhận một collection nguồn hay những phần tử varargs?
2. Vì sao `Collections.copy` dùng `super` cho đích nhưng `extends` cho nguồn?
3. Vì sao `Comparator<Number>` sắp xếp được `List<Integer>`, trong khi `Comparator<Integer>` không sắp xếp được `List<Number>`?
4. Khi nào `Collections.copy` biên dịch đúng nhưng vẫn thất bại lúc chạy?
5. Tại sao một comparator không lưu dữ liệu vẫn được xem là consumer theo PECS?

**Đáp án cốt lõi:** (1) `Collection.addAll` nhận một `Collection<? extends E>`; (2) nguồn đưa `T` ra, đích nhận `T`; (3) comparator kiểu cha nhận được subtype, còn comparator kiểu con không nhận mọi kiểu cha; (4) đích thiếu vị trí hoặc không cho sửa; (5) phương thức `compare` có các tham số đầu vào kiểu `T`.

## 12. Checklist ngắn khi gặp một chữ ký generic lạ

Đừng chỉ nhìn dấu `extends` hoặc `super`. Hãy làm rõ:

1. **Type parameter được khai báo ở đâu?** Trong class `Collection<E>`, hay ở đầu phương thức `static <T>`?
2. **Tham số dùng giá trị kiểu đó theo chiều nào?** Đọc giá trị ra từ source hay đưa giá trị vào consumer?
3. **Cận wildcard có phù hợp luồng dữ liệu không?** Producer `? extends T`, consumer `? super T`.
4. **Có điều kiện runtime nào ngoài kiểu dữ liệu?** `copy` cần kích thước đích, `addAll` cần collection cho phép thêm, `sort` cần comparator có hợp đồng nhất quán.
5. **API là instance hay static?** `dest.addAll(src)` khác hẳn `Collections.addAll(dest, item...)` dù trùng tên phương thức.

Ba nhóm chữ ký cho thấy PECS không phải khẩu hiệu áp dụng máy móc, mà là phương pháp **đọc trách nhiệm dữ liệu từ type signature**. `Collections.copy` minh họa rõ hai phía producer/consumer; `Collection.addAll` thể hiện nguồn `extends` bên cạnh receiver đã có kiểu `E`; `Collections.addAll` đặt đích `super` để nhận varargs; `Comparator<? super T>` cho thấy consumer còn có thể là một đối tượng nhận hai giá trị để so sánh. Đọc đúng các quan hệ này giúp viết API linh hoạt mà vẫn được compiler kiểm tra chặt chẽ.

</details>

- [Quay lại đầu trang](#back-to-top)
