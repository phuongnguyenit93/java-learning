<a id="back-to-top"></a>

# Lambda và API chuẩn

## Menu
- [Supplier&lt;T&gt;, Consumer&lt;T&gt;, Predicate&lt;T&gt; và Function&lt;T,R&gt;](#lambda-standard-ap-is-001)
- [Phương thức của API nhận ? super T hoặc ? extends R](#lambda-standard-ap-is-002)
- [Generic với Comparator và phép nối tiêu chí](#lambda-standard-ap-is-003)
- [Suy luận kiểu của lambda và tham chiếu phương thức](#lambda-standard-ap-is-004)
- [Phương thức generic và lựa chọn phương thức overload](#lambda-standard-ap-is-005)
- [Giới hạn dùng lambda cho hợp đồng phương thức tự khai báo generic](#lambda-standard-ap-is-006)
- [Đọc chữ ký map, flatMap và collect trong Stream](#lambda-standard-ap-is-007)
- [Generic trong Optional và quan hệ kiểu khi biến đổi dữ liệu](#lambda-standard-ap-is-008)

## <a id="lambda-standard-ap-is-001">Supplier&lt;T&gt;, Consumer&lt;T&gt;, Predicate&lt;T&gt; và Function&lt;T,R&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Supplier<T>, Consumer<T>, Predicate<T> và Function<T,R> trong Java Generics

## 1. Tại sao Java cần bốn functional interfaces này?

Trong `java.util.function`, Java cung cấp các **functional interfaces** để đưa một hành vi thành giá trị: có thể lưu trong biến, truyền vào method, trả về từ method và kết hợp với lambda hoặc method reference.

Bốn interface quan trọng nhất có các vai trò khác nhau:

- **`Supplier<T>`**: cung cấp một giá trị T **không cần đối số**.
- **`Consumer<T>`**: nhận T và thực hiện hành động, **không trả kết quả**.
- **`Predicate<T>`**: nhận T rồi trả lời **đúng/sai** bằng primitive boolean.
- **`Function<T,R>`**: nhận T rồi **biến đổi thành R**.

Sơ đồ trực quan:

~~~text
Supplier<T>         () ───────→ T
Consumer<T>          T ───────→ void
Predicate<T>         T ───────→ boolean
Function<T,R>        T ───────→ R
~~~

Generic parameters cho phép compiler kiểm tra tính tương thích giữa dữ liệu và hành vi. Chẳng hạn, `Function<String,Integer>` nhận String và trả Integer; `Predicate<User>` kiểm tra User, chứ không phải một kiểu bất kỳ bị bỏ qua bằng raw type.

Ví dụ nhanh:

~~~java
Supplier<String> supplier = () -> "Java";
Consumer<String> consumer = System.out::println;
Predicate<String> predicate = s -> !s.isBlank();
Function<String, Integer> mapper = String::length;

String s = supplier.get();          // "Java"
boolean ok = predicate.test(s);    // true
int size = mapper.apply(s);        // 4
consumer.accept(s);                // In Java
~~~

Mảnh mã trên giả định import `java.util.function.*` và đặt trong method. Chương trình Java 17+ đầy đủ, lệnh biên dịch và stdout chính xác có ở phần 9.

## 2. Functional interface, SAM và target typing

**Functional interface** là interface có đúng một abstract method thích hợp theo quy tắc Java; nó có thể có nhiều default/static methods. Một lambda được chuyển thành implementation theo *target type* của functional interface.

Ví dụ tự định nghĩa:

~~~java
@FunctionalInterface
interface TextRule<T> {
    boolean matches(T value);
}
~~~

Cách dùng:

~~~java
TextRule<String> longText = value -> value.length() >= 5;
boolean result = longText.matches("Java"); // false
~~~

`@FunctionalInterface` giúp compiler kiểm tra declaration vẫn đáp ứng quy tắc SAM; annotation không bắt buộc để interface được dùng với lambda.

Các interface `Supplier`, `Consumer`, `Predicate`, `Function` có SAM khác nhau. Compiler suy ra kiểu tham số lambda từ phép gán hoặc tham số method:

~~~java
Predicate<String> a = s -> s.length() > 2;
Function<String, Boolean> b = s -> s.length() > 2;
~~~

Cùng một biểu thức lambda có thể được gán cho hai target types khác nhau, nhưng **Predicate không thay thế trực tiếp Function**: method contract và các phép kết hợp sẵn có khác nhau. `Predicate.test` trả primitive boolean; `Function.apply` ở ví dụ thứ hai trả boxed Boolean.

## 3. Bảng API nền tảng

| Interface | Abstract method | Hướng dữ liệu | Phép kết hợp tiêu biểu |
| --- | --- | --- | --- |
| `Supplier<T>` | `T get()` | Không có đầu vào → T | Không có `andThen` được định nghĩa sẵn. |
| `Consumer<T>` | `void accept(T t)` | T → hành động | `andThen` |
| `Predicate<T>` | `boolean test(T t)` | T → boolean | `and`, `or`, `negate`, `isEqual`, `not` |
| `Function<T,R>` | `R apply(T t)` | T → R | `compose`, `andThen`, `identity` |

Từ mục tiêu nghiệp vụ, hãy chọn:
- Tạo đối tượng/default value theo yêu cầu: Supplier.
- Ghi log, in, gửi notification, thao tác mang side effect: Consumer.
- Lọc hoặc xác thực điều kiện: Predicate.
- Trích xuất trường, chuẩn hóa và chuyển đổi object: Function.

Chúng là **hợp đồng của hành vi**, không phải lớp tự động đảm bảo purity, caching, concurrency hoặc tính hợp lệ của dữ liệu.

## 4. Supplier<T>: cung cấp giá trị đúng lúc get() được gọi

Phương thức:

~~~java
T get();
~~~

Ví dụ:

~~~java
Supplier<String> greeting = () -> "Xin chào";
String text = greeting.get();
~~~

Lambda `() -> ...` không có tham số. Việc tạo supplier **không thực thi ngay** thân lambda; computation xảy ra khi caller gọi `get()`.

### 4.1. Lazy evaluation không phải memoization

~~~java
AtomicInteger calls = new AtomicInteger();
Supplier<Integer> next = calls::incrementAndGet;

System.out.println(calls.get()); // 0
System.out.println(next.get());  // 1
System.out.println(next.get());  // 2
~~~

Mảnh mã cần `java.util.concurrent.atomic.AtomicInteger`. Hai lần `get()` tạo hai kết quả khác nhau. Supplier **không tự cache** kết quả hay chỉ chạy đúng một lần.

Nếu muốn lazy initialization có cache, phải tự thiết kế lưu kết quả và đồng bộ phù hợp nếu dùng nhiều thread. Supplier cũng không tự chạy trên thread riêng: mặc định `get()` là lời gọi đồng bộ.

### 4.2. Supplier giúp tránh phép tính không cần thiết

~~~java
static String choose(boolean fallbackNeeded,
                     Supplier<String> fallback) {
    return fallbackNeeded ? fallback.get() : "primary";
}
~~~

Nếu fallbackNeeded false, supplier không được gọi. Ngược lại, truyền sẵn một giá trị đã được tính thì phép tính đó xảy ra trước lời gọi:

~~~java
// Giả sử expensiveOperation() là method do ứng dụng định nghĩa.
Supplier<String> deferred = () -> expensiveOperation();
// String immediate = expensiveOperation();
~~~

Sự khác biệt nằm ở **thời điểm gọi** chứ không phải ở tốc độ tự nhiên của lambda.

### 4.3. Những API chuẩn dùng Supplier

`Optional.orElseGet(Supplier<? extends T>)` chỉ gọi supplier tạo fallback khi Optional không có giá trị:

~~~java
Optional<String> language = Optional.of("Java");
String name = language.orElseGet(() -> "fallback"); // "Java"
~~~

`Stream.generate(Supplier<T>)` dùng supplier để cung cấp các phần tử stream; thường nên dùng `limit` khi muốn lượng hữu hạn:

~~~java
List<String> values = Stream.generate(() -> "A")
        .limit(3)
        .toList(); // [A, A, A]
~~~

Các ví dụ cần import `java.util.Optional`, `java.util.stream.Stream` và `java.util.List` tương ứng. Stream có cơ chế lazy pipeline của riêng nó; không nên nhầm với thời điểm thực thi `Supplier.get()`.

## 5. Consumer<T>: hành động nhận đầu vào nhưng không trả giá trị

SAM:

~~~java
void accept(T value);
~~~

Ví dụ:

~~~java
Consumer<String> print = System.out::println;
print.accept("Java");
~~~

`Consumer` phù hợp với ghi log, gửi sự kiện, cập nhật trạng thái và terminal operation của collection. Lời gọi `accept` trả `void`, do đó:

~~~java
// String result = print.accept("Java"); // Không biên dịch.
~~~

Nếu cần kết quả String được tạo ra, hãy dùng `Function<T,R>`.

### 5.1. Consumer.andThen chạy theo thứ tự

~~~java
Consumer<String> audit =
        s -> System.out.println("audit=" + s);
Consumer<String> send =
        s -> System.out.println("send=" + s);

audit.andThen(send).accept("done");
~~~

Đầu ra:

~~~text
audit=done
send=done
~~~

`andThen` chuyển **cùng đầu vào** qua cả hai consumer. Consumer thứ hai chỉ được gọi khi consumer đầu hoàn thành bình thường; nếu consumer đầu ném exception, bước sau không được thực hiện. API yêu cầu `after` không null.

### 5.2. Consumer trong collection và stream

~~~java
List.of("A", "B").forEach(System.out::println);

Stream.of("A", "B")
        .forEachOrdered(System.out::println);
~~~

`Iterable.forEach` và `Stream.forEach` nhận Consumer. `forEachOrdered` giúp duy trì encounter order của stream khi điều đó có ý nghĩa; `parallelStream().forEach` có thể không thực hiện các side effects theo thứ tự mong đợi.

Một Consumer **có thể** thay đổi state, nên tránh dùng nó để thêm đồng thời vào một `ArrayList` chia sẻ không đồng bộ trong parallel stream. Nếu muốn thu thập kết quả, ưu tiên `collect` hoặc `toList` thay vì lạm dụng side effects.

## 6. Predicate<T>: điều kiện trả boolean

SAM:

~~~java
boolean test(T value);
~~~

Ví dụ:

~~~java
Predicate<Integer> positive = n -> n > 0;
Predicate<Integer> even = n -> n % 2 == 0;

boolean a = positive.test(4); // true
boolean b = even.test(5);     // false
~~~

Predicate có **boolean primitive return** và các default methods giúp kết hợp logic mà không cần tự viết một lambda dài cho mọi điều kiện.

### 6.1. and và or có short-circuit

~~~java
Predicate<String> present = s -> s != null;
Predicate<String> longEnough = s -> s.length() >= 3;

Predicate<String> valid = present.and(longEnough);
boolean result = valid.test(null); // false; không gọi longEnough.
~~~

`and` chỉ gọi predicate thứ hai khi predicate thứ nhất true. Với `or`, bước thứ hai chỉ chạy khi bước đầu false:

~~~java
Predicate<Integer> negative = n -> n < 0;
Predicate<Integer> zero = n -> n == 0;

Predicate<Integer> nonPositive = negative.or(zero);
boolean yes = nonPositive.test(0); // true
~~~

Thứ tự quan trọng nếu điều kiện sau có thể ném exception hoặc có side effects. Khi kiểm tra null, nên đặt điều kiện `s != null` trước `s.length()`.

### 6.2. negate, isEqual và not

~~~java
Predicate<String> blank = String::isBlank;
Predicate<String> notBlank = blank.negate();

boolean a = notBlank.test("Java"); // true

Predicate<String> isJava = Predicate.isEqual("Java");
boolean b = isJava.test("Java");    // true
~~~

`isEqual` sử dụng quy tắc so sánh giá trị thích hợp, bao gồm xử lý null theo hợp đồng.

Java 11+ còn có `Predicate.not`:

~~~java
List<String> nonBlanks = Stream.of("A", "", "B")
        .filter(Predicate.not(String::isBlank))
        .toList(); // [A, B]
~~~

Nếu nguồn chứa null, method reference `String::isBlank` vẫn có thể ném NullPointerException; `Predicate.not` không phải null validator.

### 6.3. Predicate phù hợp với filter và validation

~~~java
List<Integer> adults = List.of(16, 22, 30)
        .stream()
        .filter(age -> age >= 18)
        .toList(); // [22, 30]
~~~

Predicate thường nên biểu diễn một điều kiện, không tạo side effects. Tránh dựa vào số lần predicate được gọi hoặc thứ tự thực thi khi stream implementation có thể tối ưu pipeline.

## 7. Function<T,R>: nhận T và tạo R

SAM:

~~~java
R apply(T value);
~~~

Ví dụ:

~~~java
Function<String, Integer> length = String::length;
Integer size = length.apply("Java"); // 4
~~~

Trong `Function<String,Integer>`, đầu vào là String và đầu ra là Integer. `String.length()` trả int, được boxing thành Integer để đáp ứng generic return type.

Sử dụng Function khi:
- Biến entity thành DTO.
- Trích thuộc tính object.
- Chuẩn hóa chuỗi.
- Chuyển kiểu chuỗi thành số, ngày hay enum.
- Xử lý `Stream.map`.

~~~java
List<Integer> lengths = List.of("Java", "Go")
        .stream()
        .map(String::length)
        .toList(); // [4, 2]
~~~

### 7.1. andThen: phép sau chạy sau

~~~java
Function<String, Integer> length = String::length;
Function<Integer, Integer> square = n -> n * n;

Function<String, Integer> f = length.andThen(square);
int result = f.apply("java"); // 16
~~~

Thứ tự là:

~~~text
"java" → length → 4 → square → 16
~~~

### 7.2. compose: phép trước chạy trước

~~~java
Function<String, Integer> length = String::length;
Function<Integer, Integer> square = n -> n * n;

Function<String, Integer> f = square.compose(length);
int result = f.apply("abc"); // 9
~~~

Thứ tự:

~~~text
"abc" → length → 3 → square → 9
~~~

Đọc `A.andThen(B)` là A rồi B, còn `B.compose(A)` cũng là A rồi B. Khi các kiểu khác nhau, compiler giúp ngăn ghép sai; khi các function cùng kiểu, một lỗi đảo thứ tự đôi khi vẫn biên dịch được nhưng cho kết quả khác.

### 7.3. identity

~~~java
Function<String, String> same = Function.identity();
String value = same.apply("ok"); // "ok"
~~~

`identity()` trả lại đầu vào mà không biến đổi; có ích trong API cần Function nhưng không có bước chuyển đổi thực tế.

### 7.4. Function không tự bảo đảm purity

Một Function có thể đọc/chỉnh sửa state hoặc ném unchecked exception. Interface chỉ xác định hình dạng `T→R`, không hứa kết quả bất biến, không cache hay tự xử lý lỗi. Trong streams, ưu tiên lambda dễ hiểu và không phụ thuộc mutable shared state.

## 8. Ghép bốn interface thành pipeline Java

Một luồng xử lý có thể là:

~~~text
Supplier<List<User>>
        │ get()
        ▼
List<User> → stream()
                  │ filter(Predicate<User>)
                  ▼
                 User
                  │ map(Function<User,String>)
                  ▼
                String
                  │ accept(Consumer<String>)
                  ▼
             Tác vụ cuối
~~~

Ví dụ: Supplier tạo danh sách User; Predicate giữ người trưởng thành; Function chuẩn hóa tên; Consumer in từng tên.

Trong Java 17, có thể định nghĩa:

~~~java
record User(String name, int age) {}
~~~

Compiler kiểm tra từng bước của pipeline. `filter` phải nhận Predicate dùng được với User; `map` phải chấp nhận User và có kết quả String; `forEachOrdered` truyền String tới Consumer<String>.

Đây là lợi ích của generics: **ràng buộc kiểu đầu vào/đầu ra giữa các hành vi được ghép** mà không cần cast thủ công.

## 9. Chương trình Java 17+ hoàn chỉnh và kiểm tra output

Lưu nội dung sau thành tệp **FunctionalInterfacesDemo.java**:

~~~java
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class FunctionalInterfacesDemo {
    record User(String name, int age) {}

    public static void main(String[] args) {
        Supplier<List<User>> source = () -> List.of(
                new User(" Lan ", 22),
                new User("Minh", 16),
                new User("An", 30)
        );

        Predicate<User> adult = user -> user.age() >= 18;
        Predicate<User> named = user -> !user.name().isBlank();

        Function<User, String> cleanName =
                user -> user.name().trim();
        Function<String, String> upper = String::toUpperCase;

        Consumer<String> print =
                name -> System.out.println("user=" + name);

        source.get().stream()
                .filter(adult.and(named))
                .map(cleanName.andThen(upper))
                .forEachOrdered(print);

        System.out.println(
                "minor=" +
                adult.negate().test(new User("Minh", 16))
        );

        Function<String, Integer> length = String::length;
        Function<Integer, Integer> square = n -> n * n;

        System.out.println(
                "compose=" +
                square.compose(length).apply("abc")
        );
        System.out.println(
                "andThen=" +
                length.andThen(square).apply("java")
        );
        System.out.println(
                "identity=" +
                Function.<String>identity().apply("ok")
        );
        System.out.println(
                "isEqual=" +
                Predicate.isEqual("An").test("An")
        );

        Consumer<String> audit =
                s -> System.out.println("audit=" + s);
        Consumer<String> send =
                s -> System.out.println("send=" + s);

        audit.andThen(send).accept("done");

        AtomicInteger calls = new AtomicInteger();
        Supplier<Integer> next = calls::incrementAndGet;

        System.out.println("before=" + calls.get());
        System.out.println("next=" + next.get());
        System.out.println("next=" + next.get());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -Xlint:all FunctionalInterfacesDemo.java
java FunctionalInterfacesDemo
~~~

Stdout **chính xác**:

~~~text
user=LAN
user=AN
minor=true
compose=9
andThen=16
identity=ok
isEqual=true
audit=done
send=done
before=0
next=1
next=2
~~~

### Giải thích output

1. `source.get()` đưa ba User. `adult.and(named)` giữ Lan và An vì họ từ 18 tuổi trở lên; Minh 16 tuổi bị lọc.
2. `cleanName.andThen(upper)` bỏ khoảng trắng rồi viết hoa; `forEachOrdered` in LAN trước AN theo encounter order.
3. `adult.negate()` cho biết Minh là người chưa trưởng thành: true.
4. `square.compose(length)` tính 3 rồi bình phương thành 9; `length.andThen(square)` tính 4 rồi bình phương thành 16.
5. `Function.identity()` giữ nguyên "ok"; `Predicate.isEqual("An")` nhận diện chuỗi "An".
6. `audit.andThen(send)` thực hiện hai hành động theo thứ tự.
7. `before=0` chứng minh tạo Supplier chưa tăng counter; hai lần `get()` tiếp theo lần lượt trả 1 và 2.

Chương trình dùng tên không dấu để ví dụ viết hoa đơn giản và kết quả ổn định trong bối cảnh chạy thông thường. Trong hệ thống quốc tế hóa, nên xét rõ `Locale` cho các phép chuyển chữ hoa/thường.

## 10. Method references và suy luận kiểu

Các cách viết:

~~~java
Function<String, Integer> a = s -> s.length();
Function<String, Integer> b = String::length;

Consumer<String> printer = System.out::println;
Predicate<String> blank = String::isBlank;
Supplier<List<String>> listFactory = ArrayList::new;
~~~

đều hợp lệ nếu import những class cần thiết. Target functional interface quyết định kiểu phù hợp của method reference.

Các nhóm method references thường gặp:

| Dạng | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| `Class::staticMethod` | `Integer::parseInt` | Gọi static method. |
| `object::instanceMethod` | `System.out::println` | Gọi trên object cụ thể. |
| `Class::instanceMethod` | `String::length` | Tham số đầu là receiver. |
| `Class::new` | `ArrayList::new` | Tạo object bằng constructor. |

Lambda thường dễ đọc hơn khi logic cần nhiều bước, biến trung gian hoặc kiểm tra lỗi. Method reference thường gọn hơn khi chỉ chuyển tiếp đến một method rõ nghĩa.

### Truyền một Function vào generic method

~~~java
static <T, R> R transform(
        T value, Function<? super T, ? extends R> function) {
    return function.apply(value);
}
~~~

Sử dụng:

~~~java
Integer result = transform("Java", String::length); // 4
~~~

Compiler suy luận kiểu T/R từ value, target và method reference. `? super T` giúp chấp nhận function tiêu thụ supertype của T; `? extends R` giúp chấp nhận function trả subtype của R.

## 11. Wildcards, variance và quy tắc PECS

Các functional interfaces chuẩn cũng cần **đúng chiều của dữ liệu**. Java generic invariance khiến `Consumer<Number>` không tự là `Consumer<Integer>`, nhưng wildcard trong API có thể biểu đạt quan hệ sử dụng an toàn:

~~~java
Consumer<Number> printNumber = System.out::println;
Consumer<? super Integer> acceptInteger = printNumber;
acceptInteger.accept(42);

Supplier<Integer> integerSource = () -> 7;
Supplier<? extends Number> numberSource = integerSource;
Number value = numberSource.get();
~~~

- `Consumer` nhận **đầu vào**: `? super T` cho phép callback nhận cả supertype của `T`.
- `Supplier` tạo **đầu ra**: `? extends R` cho phép callback sản xuất subtype của `R`.
- `Function` vừa nhận `T` vừa trả `R`, vì vậy những API biến đổi có thể nhận `Function<? super T, ? extends R>`.

Đây là PECS (*Producer Extends, Consumer Super*), một gợi ý về hướng dữ liệu chứ không biến generic types thành covariant. Bài kế tiếp phân tích **chữ ký thực** của `Stream.map`, `Comparator`, `Predicate` và việc dùng callback với kiểu cha/con; ở đây chỉ cần nhận diện chiều consumer/producer của bốn interface.

## 12. Những biến thể dành cho primitive

Generics dùng reference types, nên `Function<String,Integer>` và `Predicate<Integer>` có thể phát sinh boxing/unboxing. Java có các functional interfaces chuyên biệt:

| Mục tiêu | Interface | Method |
| --- | --- | --- |
| Cung cấp int | `IntSupplier` | `getAsInt()` |
| Xử lý int | `IntConsumer` | `accept(int)` |
| Kiểm tra int | `IntPredicate` | `test(int)` |
| int → int | `IntUnaryOperator` | `applyAsInt(int)` |
| int → long | `IntToLongFunction` | `applyAsLong(int)` |
| T → int | `ToIntFunction<T>` | `applyAsInt(T)` |
| int → R | `IntFunction<R>` | `apply(int)` |

Ví dụ:

~~~java
IntSupplier answer = () -> 21;
IntUnaryOperator doubleIt = n -> n * 2;
IntPredicate even = n -> n % 2 == 0;

int value = doubleIt.applyAsInt(answer.getAsInt()); // 42
boolean yes = even.test(value); // true
~~~

Các class ở `java.util.function` và thích hợp khi làm việc với `IntStream`, `LongStream`, `DoubleStream`. Điều đó không có nghĩa boxing luôn là vấn đề hiệu năng nghiêm trọng: nên đo đạc trước khi tối ưu đường xử lý thực tế.

Cú pháp `Supplier<int>` hay `Function<int,int>` không hợp lệ: type arguments của generics thông thường phải là reference types.

## 13. Các lỗi thường gặp

### 13.1. Dùng Supplier khi cần nhận tham số

~~~java
// Supplier<String> wrong = name -> "Hello " + name;
Function<String, String> correct = name -> "Hello " + name;
~~~

Supplier không có tham số; Function có một đầu vào.

### 13.2. Dùng Consumer rồi mong lấy kết quả

~~~java
Consumer<String> print = System.out::println;
// String result = print.accept("Java"); // Không biên dịch.
~~~

`accept` trả void. Dùng Function nếu cần giá trị mới.

### 13.3. Lẫn Predicate với Function<T,Boolean>

~~~java
Predicate<String> nonBlank = s -> !s.isBlank();
Function<String, Boolean> sameCondition = s -> !s.isBlank();
~~~

Chúng biểu diễn điều kiện tương tự nhưng API khác: Predicate có `and/or/negate` và trả primitive boolean; Function có `compose/andThen` và trả boxed Boolean.

### 13.4. Đảo chiều compose và andThen

Nên viết hướng dữ liệu:

~~~text
"A" -> normalize -> parse -> calculate
~~~

rồi ghép từ đầu ra của bước trước vào đầu vào của bước sau. Với các Function đồng kiểu, thứ tự sai đôi khi vẫn compile nhưng cho sai kết quả.

### 13.5. Predicate truy cập giá trị null

~~~java
Predicate<String> longer = s -> s.length() >= 3;
// longer.test(null); // NullPointerException.
~~~

Một cách an toàn:

~~~java
Predicate<String> present = s -> s != null;
Predicate<String> safe = present.and(s -> s.length() >= 3);
~~~

Bước thứ hai không được gọi nếu bước đầu false.

### 13.6. Cho rằng Supplier tự cache hoặc chạy async

Supplier chỉ cung cấp `get`. Implementation có thể tính lại, lưu cache hoặc gọi một hệ thống ngoài, nhưng không có hành vi nào trong số đó được interface tự cung cấp.

### 13.7. Side effects trong parallel streams

~~~java
List<String> output = new ArrayList<>();
// source.parallelStream().forEach(output::add);
~~~

Đây là ý tưởng không an toàn nếu output là ArrayList chia sẻ không đồng bộ. Dùng collectors hoặc một thiết kế concurrency phù hợp. Predicate/Function/Consumer không tự làm mutable state trở thành thread-safe.

### 13.8. Checked exception trong lambda

SAM của bốn interface chuẩn không khai báo checked exceptions. Một lambda ném IOException chưa xử lý thường không thể gán trực tiếp cho `Function<T,R>` hoặc `Consumer<T>` tương ứng. Có thể catch/wrap phù hợp hoặc định nghĩa functional interface riêng với `throws E` nếu hợp đồng cần giữ checked exceptions.

### 13.9. Tái sử dụng stream đã kết thúc

Stream không phải collection có thể thực hiện terminal operations lặp đi lặp lại. Sau `forEach`, `toList` hoặc terminal operation khác, không nên sử dụng lại cùng stream. Nếu cần tạo stream mới mỗi lần, một `Supplier<Stream<T>>` có thể phù hợp:

~~~java
Supplier<Stream<String>> fresh =
        () -> Stream.of("Java", "Go");

long first = fresh.get().count();   // 2
long second = fresh.get().count();  // 2
~~~

Ở đây supplier tạo **stream mới cho mỗi lần get**, tránh tái sử dụng stream đã tiêu thụ.

## 14. Thiết kế API nhỏ sử dụng cả bốn interface

Một method generic có thể nhận bốn hành vi tách biệt:

~~~java
static <T, R> void process(
        Supplier<? extends T> loader,
        Predicate<? super T> accepted,
        Function<? super T, ? extends R> mapper,
        Consumer<? super R> sink) {

    T value = loader.get();

    if (accepted.test(value)) {
        sink.accept(mapper.apply(value));
    }
}
~~~

Ví dụ:

~~~java
process(
        () -> " Java ",
        text -> !text.isBlank(),
        String::trim,
        System.out::println
);
~~~

Đầu ra là `Java`. Mỗi bước có trách nhiệm rõ ràng: cung cấp, kiểm tra, biến đổi, xử lý cuối. Có thể thay từng lambda bằng implementation thử nghiệm để kiểm tra nhánh đúng/sai hoặc số lần gọi mà không cần thay body của `process`.

### Kiểm thử nhánh bị từ chối

~~~java
AtomicInteger sent = new AtomicInteger();

process(
        () -> "A",
        text -> false,
        String::trim,
        text -> sent.incrementAndGet()
);

System.out.println(sent.get()); // 0
~~~

Predicate trả false nên mapper/sink không chạy. Method này đồng bộ và chỉ gọi Supplier một lần; nó không tự có retry, async execution hoặc transaction semantics.

## 15. Khi nào không nên dùng bốn interface này?

Chúng đủ cho nhiều API nhỏ, nhưng có trường hợp cần loại khác:

- Hai đầu vào, một kết quả: `BiFunction<T,U,R>`.
- Hai đầu vào, không kết quả: `BiConsumer<T,U>`.
- Hai đầu vào, trả boolean: `BiPredicate<T,U>`.
- T → T: `UnaryOperator<T>`.
- Hai T → T: `BinaryOperator<T>`.
- Callback ném checked exception cụ thể: functional interface tự định nghĩa với `throws E`.
- Hợp đồng miền nghiệp vụ có tên rõ như `AuthorizationRule` hoặc `PricePolicy`, đặc biệt khi một tên có ý nghĩa giúp API dễ đọc hơn `Function` tổng quát.

Không cần tự viết interface thay thế `Supplier<T>` nếu nó không mang thêm hợp đồng. Ngược lại, cũng đừng ép logic miền nghiệp vụ quan trọng thành lambda vô danh khó kiểm thử và khó tìm khi debug.

## 16. Checklist áp dụng trong Java thực tế

1. **Xác định số đầu vào và loại đầu ra** trước khi chọn interface.
2. **Giữ generic type arguments** thay vì dùng raw `Function` hay raw `Predicate`.
3. **Dùng method reference hợp lý** để làm rõ ý định, nhưng lambda nhiều bước vẫn thích hợp khi cần logic cụ thể.
4. **Kiểm tra hướng compose/andThen** bằng sơ đồ T→R.
5. **Lưu ý short-circuit** của Predicate.and/or; điều kiện thứ hai có thể không chạy.
6. **Quy định null policy** của từng bước, đặc biệt khi dùng method reference trên object.
7. **Không giả định purity**; mặc dù nên giảm side effects ở Predicate/Function, interface không bắt buộc làm vậy.
8. **Không giả định laziness/caching/async** ngoài những gì Supplier và stream API thực sự cam kết.
9. **Xét wildcard variance** khi thiết kế method generic nhận function của supertype hoặc supplier trả subtype.
10. **Không dùng shared mutable state thiếu đồng bộ** trong consumer của parallel stream.
11. **Chọn primitive specializations** trong đường xử lý primitive khi API và performance yêu cầu.
12. **Dùng compiler với source release mục tiêu**, ví dụ `javac --release 17`, và kiểm tra stdout xác định thay vì dựa vào thứ tự không được API hứa.

## 17. Câu hỏi tự kiểm tra

**1.** Nếu một lambda không có tham số, trả String, chọn interface nào?

**Đáp án:** `Supplier<String>` với `String get()`.

**2.** Muốn log một String mà không tạo kết quả, dùng gì?

**Đáp án:** `Consumer<String>` với `void accept(String)`.

**3.** Muốn giữ User có tuổi từ 18 trở lên bằng `Stream.filter`, dùng gì?

**Đáp án:** `Predicate<User>`, ví dụ `u -> u.age() >= 18`.

**4.** Muốn đổi một User thành String, dùng gì?

**Đáp án:** `Function<User,String>`.

**5.** `Predicate.and` có luôn thực hiện cả hai điều kiện không?

**Đáp án:** Không. Nó short-circuit khi điều kiện đầu false.

**6.** `Consumer.andThen` thực hiện gì khi consumer đầu ném exception?

**Đáp án:** Exception được chuyển tiếp; consumer sau không được thực hiện trên lời gọi đó.

**7.** `length.andThen(square)` và `square.compose(length)` có cùng chiều xử lý không?

**Đáp án:** Có. Cả hai thực hiện length trước rồi square.

**8.** Supplier có tự memoize không?

**Đáp án:** Không. `get()` có thể trả kết quả mới ở mỗi lần gọi.

**9.** `Function.identity()` có ý nghĩa gì?

**Đáp án:** Trả lại giá trị đầu vào, không chuyển đổi.

**10.** Vì sao `Supplier<Integer>` và `IntSupplier` không hoàn toàn giống nhau?

**Đáp án:** Supplier<Integer> trả boxed Integer qua get; IntSupplier trả primitive int qua getAsInt.

**11.** Vì sao một `Consumer<Number>` có thể dùng ở vị trí `Consumer<? super Integer>`?

**Đáp án:** Consumer<Number> xử lý được Integer vì Integer là subtype của Number; `? super Integer` biểu diễn đúng hướng nhận đầu vào.

**12.** Vì sao không nên gọi `parallelStream().forEach(list::add)` nếu list là ArrayList dùng chung?

**Đáp án:** Consumer không cung cấp thread safety và ArrayList không an toàn cho nhiều lần thêm đồng thời không đồng bộ.

## 18. Tổng kết

Bốn interface chuẩn có thể ghi nhớ theo **chiều dữ liệu**:

~~~text
Supplier<T>   = lấy T             → get()
Consumer<T>   = làm việc với T    → accept(T)
Predicate<T>  = hỏi T đúng không  → test(T)
Function<T,R> = chuyển T thành R  → apply(T)
~~~

Chúng cung cấp hợp đồng hành vi có kiểm tra kiểu, làm nền cho lambda, method reference, collection, stream, Optional và nhiều APIs hiện đại.

Điều quan trọng là hiểu **SAM và type inference**, **sự khác nhau giữa hành động và kết quả**, **short-circuit**, **thứ tự compose/andThen**, **hướng wildcards**, và giới hạn thực tế: Supplier không tự cache; Consumer có side effects cần kiểm soát; Predicate/Function không bắt buộc pure; không interface nào tự cung cấp thread safety hoặc xử lý checked exceptions.

Chọn đúng interface giúp code Java dễ đọc, dễ test, linh hoạt khi ghép chức năng và an toàn kiểu hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-standard-ap-is-002">Phương thức của API nhận ? super T hoặc ? extends R</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức của API nhận `? super T` hoặc `? extends R`: Lambda và Java Generics

## Mục tiêu bài học

Khi đọc Java Standard API, bạn thường gặp các chữ ký dạng:

```java
<R> Stream<R> map(Function<? super T, ? extends R> mapper)
```

Đây không phải cách viết rườm rà ngẫu nhiên. Hai wildcard **`? super T`** và **`? extends R`** khiến phương thức chấp nhận nhiều callback hợp lệ hơn nhưng vẫn giữ tính an toàn kiểu.

Sau bài học, bạn có thể:

- Đọc một cách chính xác `Function<? super T, ? extends R>` theo **hướng tiêu thụ đầu vào** và **hướng tạo đầu ra**.
- Giải thích vì sao `Function<Animal, StringBuilder>` có thể truyền vào một hàm đang biến đổi `Dog` thành `CharSequence`.
- Hiểu **contravariance** ở đầu vào, **covariance** ở đầu ra và phân biệt với **invariance** của `Function<A,B>`/generics Java thông thường.
- Đọc các chữ ký phổ biến của `Stream.map`, `Stream.flatMap`, `Stream.filter`, `Iterable.forEach`, `List.sort`, `Function.compose`, `Function.andThen`, `Consumer.andThen` và `Predicate.and/or`.
- Biết khi nào wildcard thuộc tham số callback, khi nào thuộc collection, và tại sao không nên thay chúng bằng raw types.
- Viết một generic API riêng kết hợp `List<? extends T>` và `Function<? super T, ? extends R>`.
- Đọc lỗi compiler, khắc phục vấn đề type inference, chọn lambda hoặc method reference phù hợp.
- Nhận biết giới hạn: wildcard **mở rộng loại callback được nhận**, không tự đổi `Consumer<T>` thành một loại generic khác, cũng không thêm runtime subtype checks mới.

Các ví dụ hoàn chỉnh ở đây sử dụng **Java 17 trở lên**, đã được biên dịch bằng `javac -Xlint:all` và chạy để kiểm chứng stdout. Các đoạn mã được ghi là “minh họa” cần đặt trong lớp/method phù hợp, không phải chương trình độc lập.

## 1. Đầu tiên, hiểu `Function<T,R>`

`Function<T,R>` biểu diễn một phép biến đổi với **một đầu vào `T` và một kết quả `R`**; abstract method là `R apply(T value)`. Ví dụ:

```java
Function<String, Integer> length = String::length;
Integer count = length.apply("Java"); // 4
```

Điều cần cho **chữ ký API callback** là phân biệt hướng dùng: caller đưa vào một `T`, còn callback có thể chấp nhận kiểu **cha của `T`**; caller lấy ra một `R`, còn callback có thể tạo kiểu **con của `R`**. Vì thế mẫu rộng hơn là:

```java
Function<? super T, ? extends R>
```

Tình huống `Dog extends Animal` và `StringBuilder implements CharSequence` ở phần tiếp theo chứng minh cả hai phía đồng thời; không chỉ nhắc lại bảng bốn interfaces của bài mở đầu.

## 2. Vì sao `Function<Animal, StringBuilder>` dùng được cho `Dog -> CharSequence`?

Giả sử có quan hệ kế thừa:

```text
    Object                  CharSequence
      |                          ^
    Animal                        |
      ^                      StringBuilder
      |
     Dog
```

`Dog extends Animal`, và `StringBuilder implements CharSequence`.

Bây giờ callback:

```java
Function<Animal, StringBuilder> label =
        animal -> new StringBuilder(animal.name());
```

Hàm này nhận **mọi `Animal`**, nên chắc chắn nhận được `Dog`. Nó trả `StringBuilder`, và một `StringBuilder` có thể dùng như `CharSequence`. Vì vậy, trong ngữ cảnh `T = Dog`, `R = CharSequence`, callback ấy thỏa:

```java
Function<? super Dog, ? extends CharSequence>
```

Không cần viết lại thành một `Function<Dog, CharSequence>` chỉ để chiều kiểu “vừa khít”.

Bảng:

| Thành phần của callback | Loại thực tế | Kiểu API yêu cầu | Vì sao hợp lệ? |
| --- | --- | --- | --- |
| Đầu vào | `Animal` | `? super Dog` | `Animal` là supertype của `Dog` |
| Đầu ra | `StringBuilder` | `? extends CharSequence` | `StringBuilder` triển khai `CharSequence` |
| Callback đầy đủ | `Function<Animal,StringBuilder>` | `Function<? super Dog,? extends CharSequence>` | Cả hai hướng an toàn |

**Không được đảo hướng:**

- Callback chỉ nhận `Dog` **không** dùng được để xử lý tất cả `Animal`, vì có thể gặp `Cat`.
- Callback chỉ cam kết trả `Object` **không** tự dùng được ở nơi cần `String`, vì `Object` có thể không phải `String`.

Wildcard không “ép” một đối tượng thành subtype thích hợp. Nó mô tả các **quan hệ kiểu an toàn** đã được compiler chứng minh.

## 3. Nguyên tắc PECS cho một functional interface hai tham số kiểu

Quy tắc học nhanh **PECS — Producer Extends, Consumer Super**:

- **`? super T`**: một thành phần **tiêu thụ** các giá trị `T` (consumer) được phép nhận một supertype của `T`.
- **`? extends R`**: một thành phần **cung cấp** các giá trị dùng như `R` (producer) có thể trả một subtype của `R`.

Với `Function<A,B>`, `A` ở vị trí tham số của `apply` nên nó là đầu vào, còn `B` là kết quả của `apply` nên là đầu ra.

```text
T của API  -----(đưa vào)----->  Function<? super T, ? extends R>
                                             |
                                             | apply(T)
                                             v
R của API  <----(nhận về)------  giá trị là R hoặc subtype R
```

Lưu ý: “consumer” và “producer” ở đây mô tả **vai trò của một vị trí kiểu trong lời gọi**, không có nghĩa một `Function` chỉ là `Consumer` hoặc chỉ là `Supplier`. Function vừa nhận đầu vào vừa trả đầu ra, do đó **hai wildcard có thể đi theo hai hướng khác nhau**.

Một lỗi diễn giải thường gặp là chỉ nhìn từ ngữ pháp `extends`/ `super` mà bỏ qua vị trí sử dụng. Hãy nhìn method `R apply(T value)`: đầu vào đi vào hàm, kết quả đi ra khỏi hàm. Hướng dữ liệu quyết định hướng wildcard.

## 4. Vì sao không viết `Function<T,R>` đơn giản?

Chữ ký sau hợp lệ nhưng **quá hạn chế** nếu API muốn nhận callback có đầu vào hoặc đầu ra khác ở mức kế thừa:

```java
static <T, R> R strictApply(T item, Function<T, R> mapper) {
    return mapper.apply(item);
}
```

Đây là đoạn method minh họa và cần import `Function`.

Mặc định Java generics **invariant**. Điều đó có nghĩa dù `Dog extends Animal` thì:

```java
// Function<Animal, StringBuilder> callback = ...;
// Function<Dog, CharSequence> exact = callback; // Không biên dịch.
```

Kiểu `Function<Animal,StringBuilder>` không phải subtype trực tiếp của `Function<Dog,CharSequence>`. Java không khai báo variance ngay trên mọi generic interface theo cách làm `Function` tự đổi hai type arguments trong phép gán đó.

Thay vì dựa vào một phép gán không hợp lệ, API thiết kế tham số:

```java
static <T, R> R flexibleApply(
        T item, Function<? super T, ? extends R> mapper) {
    return mapper.apply(item);
}
```

Ở đây một callback nhận kiểu cha của `T` và trả kiểu con của `R` có thể truyền vào. Quan hệ wildcard này thường xuất hiện trong **method parameters**, nơi compiler biết hàm sẽ đưa `T` vào và nhận `R` ra.

Không nên hiểu `Function<? super T,? extends R>` làm thay đổi định nghĩa interface `Function<T,R>` ở runtime. Đây là ràng buộc compile-time tại vị trí sử dụng một generic type.

## 5. API chuẩn `Stream.map`: mẫu quan trọng nhất

Chữ ký trong `java.util.stream.Stream<T>`:

```java
<R> Stream<R> map(Function<? super T, ? extends R> mapper);
```

Đọc theo ngữ nghĩa:

1. Stream hiện tại cung cấp từng phần tử kiểu `T`.
2. Mapper được gọi với `T`; nếu mapper nhận `Object`, `Animal` hoặc một supertype phù hợp của `T`, nó vẫn làm việc được.
3. Mỗi kết quả mapper được xem là `R`; mapper có thể trả subtype của `R`.
4. API trả `Stream<R>`. Đó là **kiểu kết quả do compiler suy luận hoặc caller yêu cầu**, không nhất thiết trùng chính xác với kiểu biểu thức bên trong lambda.

Với stream `Dog`:

```java
Function<Animal, StringBuilder> label = ...;
List<CharSequence> labels = dogs.stream()
        .<CharSequence>map(label)
        .toList();
```

Dấu `.<CharSequence>map(...)` là **explicit method type argument** để chọn `R = CharSequence`. Nó không bắt buộc trong mọi lời gọi: Java thường suy luận `R` từ callback và ngữ cảnh gán. Trong ví dụ có chủ đích, nó giúp học viên thấy rõ phần `? extends R` ở phía kết quả.

Chú ý: `Stream.toList()` trả `List<CharSequence>` theo `Stream<CharSequence>`, và trong Java 17 danh sách kết quả không hỗ trợ các thao tác sửa đổi thông thường. Dù vậy, wildcard của `map` liên quan đến **quan hệ kiểu callback**, không phải tính bất biến của danh sách.

## 6. Chương trình Java 17 A: Stream, Comparator, Predicate và Consumer

Tạo tệp **`VarianceApiDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class VarianceApiDemo {
    static class Animal {
        private final String name;

        Animal(String name) { this.name = name; }
        String name() { return name; }
    }

    static final class Dog extends Animal {
        Dog(String name) { super(name); }
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>(
                List.of(new Dog("Luna"), new Dog("Milo")));

        Function<Animal, StringBuilder> label =
                animal -> new StringBuilder(
                        animal.name().toUpperCase(Locale.ROOT));

        List<String> labels = dogs.stream()
                .<CharSequence>map(label)
                .map(CharSequence::toString)
                .toList();
        System.out.println("labels: " + labels);

        Comparator<Animal> byName =
                Comparator.comparing(Animal::name);
        dogs.sort(byName);
        System.out.println("sorted: "
                + dogs.stream().map(Dog::name).toList());

        Predicate<Animal> startsWithM =
                animal -> animal.name().startsWith("M");
        System.out.println("predicate: "
                + dogs.stream()
                        .filter(startsWithM)
                        .map(Dog::name)
                        .toList());

        Consumer<Animal> anyAnimal = animal ->
                System.out.println("consume: " + animal.name());
        Consumer<Dog> dogOnly = d ->
                System.out.println("dog: " + d.name());

        Consumer<Dog> combined = dogOnly.andThen(anyAnimal);
        combined.accept(dogs.get(0));
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:all VarianceApiDemo.java
java VarianceApiDemo
```

**Stdout đã kiểm chứng:**

```text
labels: [LUNA, MILO]
sorted: [Luna, Milo]
predicate: [Milo]
dog: Luna
consume: Luna
```

Chương trình đã được kiểm chứng với `-Xlint:all` mà không có compiler warning.

### Đọc đúng phần `map`

`dogs.stream()` có kiểu `Stream<Dog>`. Callback `label` nhận `Animal`, và `Animal` là supertype của `Dog`, nên nó thỏa phần `? super Dog`.

Callback trả `StringBuilder`, là subtype của `CharSequence`, nên nó thỏa `? extends CharSequence`. Dòng `.<CharSequence>map(label)` yêu cầu stream kết quả có phần tử `CharSequence`. Tiếp đó `map(CharSequence::toString)` tạo stream `String`, và `toList()` cho danh sách `String`.

Đây là ví dụ thực tế cho **hai hướng variance trong cùng một callback**, không phải một phép cast nguy hiểm.

### Đọc đúng phần `sort`

`List<E>.sort` nhận:

```java
void sort(Comparator<? super E> c);
```

Danh sách `dogs` có `E = Dog`, còn `byName` có kiểu `Comparator<Animal>`. Một comparator biết so sánh mọi `Animal` chắc chắn so sánh được mọi `Dog`.

`? super E` trong trường hợp này xuất hiện bởi comparator **tiêu thụ** hai giá trị `E` để so sánh. `List<Dog>.sort(Comparator<Animal>)` là hợp lệ; không cần viết lại comparator kiểu `Dog`.

### Đọc đúng phần `filter`

`Stream<T>.filter` nhận:

```java
Stream<T> filter(Predicate<? super T> predicate);
```

Một `Predicate<Animal>` có thể kiểm tra mọi `Dog`, nên dùng được với `Stream<Dog>`. Kết quả vẫn là `Stream<Dog>`: `filter` chỉ chọn phần tử, không tạo một kiểu kết quả mới `R`.

Đây là lý do `filter` chỉ cần `? super T` ở tham số predicate chứ không có `? extends R` trong chữ ký callback.

### Đọc đúng phần `Consumer.andThen`

`Consumer<T>.andThen` nhận `Consumer<? super T>` nhưng **trả `Consumer<T>`**, không tự thu hẹp hoặc mở rộng type parameter của receiver.

Với `dogOnly: Consumer<Dog>`, lời gọi:

```java
Consumer<Dog> combined = dogOnly.andThen(anyAnimal);
```

hợp lệ bởi `anyAnimal: Consumer<Animal>` có thể nhận mọi `Dog`. Hai consumer được gọi theo thứ tự: trước hết `dogOnly`, tiếp đó `anyAnimal`.

Đừng lẫn với:

```java
// KHÔNG HỢP LỆ:
// Consumer<Dog> wrong = anyAnimal.andThen(dogOnly);
```

Receiver `anyAnimal` là `Consumer<Animal>`, nên `andThen` ở đó yêu cầu một `Consumer<? super Animal>`. `Consumer<Dog>` chỉ biết nhận `Dog`, không chắc xử lý được một `Animal` bất kỳ. Compiler bác bỏ. Chữ ký `andThen` **không tự đổi `Consumer<Animal>` thành `Consumer<Dog>`** chỉ vì bạn gán vào biến đó.

## 7. Các chữ ký chuẩn khác cần đọc chính xác

Bảng này dựa trên chữ ký đã đối chiếu bằng `javap` của JDK Java 17:

| API | Chữ ký trọng tâm | Vai trò của wildcard |
| --- | --- | --- |
| `Stream<T>.map` | `<R> Stream<R> map(Function<? super T, ? extends R>)` | Callback nhận T, tạo R |
| `Stream<T>.flatMap` | `<R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>>)` | Callback nhận T, tạo stream phần tử dùng như R |
| `Stream<T>.filter` | `Stream<T> filter(Predicate<? super T>)` | Predicate nhận T |
| `Iterable<T>.forEach` | `void forEach(Consumer<? super T>)` | Consumer nhận T |
| `List<E>.sort` | `void sort(Comparator<? super E>)` | Comparator so sánh các E |
| `Consumer<T>.andThen` | `Consumer<T> andThen(Consumer<? super T>)` | Consumer tiếp theo nhận T |
| `Predicate<T>.and` | `Predicate<T> and(Predicate<? super T>)` | Predicate còn lại nhận T |
| `Predicate<T>.or` | `Predicate<T> or(Predicate<? super T>)` | Predicate còn lại nhận T |
| `Function<T,R>.compose` | `<V> Function<V,R> compose(Function<? super V, ? extends T>)` | Hàm chạy trước nhận V và tạo T |
| `Function<T,R>.andThen` | `<V> Function<T,V> andThen(Function<? super R, ? extends V>)` | Hàm chạy sau nhận R và tạo V |

**Đọc các chữ ký như hợp đồng.** Một `Predicate<? super T>` hoặc `Consumer<? super T>` chỉ tiêu thụ `T`; không có kiểu trả về `R` cần biểu diễn trong generic parameters của `Predicate`/`Consumer`.

Một `Function` vừa nhận vừa trả kết quả nên có cả đầu vào super và đầu ra extends.

Một `Comparator` được truyền vào `sort` cũng tiêu thụ giá trị `E`, nhưng cho ra `int` cố định theo contract `compare`, nên không cần `? extends R`.

## 8. `Function.compose` và `Function.andThen`: quan sát hướng dữ liệu

Lấy `Function<T,R> f`. Về trực giác:

```text
compose(before):
    V ---> before ---> T ---> f ---> R

andThen(after):
    T ---> f ---> R ---> after ---> V
```

Do đó chữ ký của `compose`:

```java
<V> Function<V, R> compose(
        Function<? super V, ? extends T> before)
```

Hàm `before` nhận được `V`, trả về một giá trị hợp lệ làm đầu vào `T` cho hàm hiện tại. Đây là nguyên nhân cặp wildcard gắn với **V** và **T**, không phải với **R**.

Còn `andThen`:

```java
<V> Function<T, V> andThen(
        Function<? super R, ? extends V> after)
```

Hàm `after` tiêu thụ kết quả `R` của hàm hiện tại và tạo giá trị phù hợp với `V`. Cặp wildcard vì thế nằm ở **R** và **V**.

Sự đối xứng này giúp bạn nhớ API mà không cần học thuộc mọi dấu `<` `>`. Hãy vẽ **đường đi của giá trị** theo thứ tự thực thi.

## 9. Chương trình Java 17 B: Compose, andThen và flatMap

Tạo tệp **`FunctionCompositionDemo.java`**:

```java
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class FunctionCompositionDemo {
    public static void main(String[] args) {
        Function<Number, String> stringify =
                n -> "n=" + n.intValue();
        Function<CharSequence, Integer> length =
                CharSequence::length;

        Function<String, String> compose =
                stringify.compose(length);
        System.out.println("compose: " + compose.apply("Java"));

        Function<CharSequence, Integer> count =
                CharSequence::length;
        Function<Number, Integer> after =
                stringify.andThen(count);
        System.out.println("andThen: " + after.apply(17));

        Function<CharSequence, Stream<String>> split =
                input -> Stream.of(input.toString().split(" "));
        List<CharSequence> tokens =
                List.of("red blue", "green")
                        .stream()
                        .<CharSequence>flatMap(split)
                        .toList();
        System.out.println("flatMap: " + tokens);
    }
}
```

Lệnh:

```bash
javac -Xlint:all FunctionCompositionDemo.java
java FunctionCompositionDemo
```

**Stdout đã kiểm chứng:**

```text
compose: n=4
andThen: 4
flatMap: [red, blue, green]
```

Chương trình đã biên dịch không warning với `-Xlint:all`.

### `compose` từng bước

Receiver `stringify` có kiểu `Function<Number,String>`, tức `T=Number`, `R=String`. Callback `before` là `length: Function<CharSequence,Integer>`.

Ta muốn hàm tổng hợp nhận `String`, nên `V=String`.

- `length` nhận `CharSequence`, một supertype của `String`, vì vậy đáp ứng `? super V`.
- `length` trả `Integer`, một subtype của `Number`, vì vậy đáp ứng `? extends T`.
- Kết quả `Function<String,String>` nhận `"Java"`, tính độ dài `4`, rồi tạo `"n=4"`.

Không hề có cast kiểu `CharSequence -> String` nguy hiểm; callback nhận kiểu rộng hơn nên an toàn.

### `andThen` từng bước

Receiver vẫn là `stringify: Function<Number,String>`. Callback sau là `count: Function<CharSequence,Integer>`. Ta chọn `V=Integer`.

- `count` nhận `CharSequence`, supertype của `String`, nên thỏa `? super R`.
- `count` trả `Integer`, phù hợp với `? extends V`.
- `stringify.apply(17)` cho `"n=17"`, gồm bốn ký tự.
- `count.apply("n=17")` cho `4`.

Kết quả `Function<Number,Integer>` không thay đổi kiểu đầu vào `Number` của receiver `stringify`.

### `flatMap` và lớp wildcard lồng nhau

`Stream<T>.flatMap` nhận:

```java
Function<? super T, ? extends Stream<? extends R>>
```

Đọc từ trong ra ngoài:

1. `Stream<? extends R>`: một stream chứa các giá trị có thể dùng như `R`.
2. `? extends Stream<? extends R>`: callback có thể trả một implementation/subtype thích hợp của stream đó.
3. `? super T`: callback có thể nhận `T` hoặc một supertype.

Trong ví dụ `T=String`, `R=CharSequence`:

- `Function<CharSequence,Stream<String>> split` nhận `CharSequence`, tức supertype của `String`.
- Kết quả là `Stream<String>`, những phần tử `String` dùng được như `CharSequence`.
- Phương thức `.<CharSequence>flatMap(split)` tạo `Stream<CharSequence>`, sau `toList()` là `List<CharSequence>`.

Mức wildcard lồng nhau **không phải phép biên dịch độc lập cho từng lớp thực**; nó là cơ chế kiểu để giữ an toàn khi flatten các stream tạo ra từ callback.

## 10. Phân biệt wildcard ở callback với wildcard ở collection

Hai biểu thức này liên quan nhưng nói về **hai đối tượng khác nhau**:

```java
List<? extends Animal> source;
Function<? super Animal, ? extends CharSequence> mapper;
```

Ở **collection nguồn**, `? extends Animal` nghĩa là các phần tử đọc ra có thể dùng như `Animal`; nhưng không thể tự ý thêm `Animal` vào danh sách vì kiểu phần tử thật có thể là `Dog`.

Ở **callback**, `? super Animal` nghĩa là mapper có thể nhận một `Animal` (hoặc một kiểu cha của `Animal`). Còn `? extends CharSequence` là lời hứa kết quả mapper dùng được như `CharSequence`.

Khi tự thiết kế method, bạn thường cần cả hai:

```java
static <T, R> List<R> mapInto(
        List<? extends T> source,
        Function<? super T, ? extends R> mapper) {
    List<R> result = new ArrayList<>();
    for (T item : source) {
        result.add(mapper.apply(item));
    }
    return result;
}
```

Đây là method minh họa và cần import các kiểu thích hợp. Vì `source` là **producer** của `T`, ta nhận `List<? extends T>`; vì `mapper` là **consumer của T** và **producer của R**, ta dùng `Function<? super T,? extends R>`.

Không phải lúc nào cũng cần mọi wildcard như trên. Một API chỉ nhận `List<T>` cũng có thể hợp lệ và đủ tốt theo nhu cầu. Nhưng việc hiểu vai trò của các kiểu giúp bạn chọn chữ ký linh hoạt mà vẫn không tạo unchecked cast.

## 11. Chương trình Java 17 C: tự viết API có cả `extends` và `super`

Tạo tệp **`OwnFlexibleApiDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class OwnFlexibleApiDemo {
    static class Base {
        private final String code;

        Base(String code) { this.code = code; }
        String code() { return code; }
    }

    static final class Entry extends Base {
        Entry(String code) { super(code); }
    }

    static <T, R> List<R> mapInto(
            List<? extends T> source,
            Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(mapper);

        List<R> result = new ArrayList<>();
        for (T item : source) {
            result.add(mapper.apply(item));
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        List<Entry> entries =
                List.of(new Entry("A"), new Entry("BC"));
        Function<Base, StringBuilder> asLabel =
                b -> new StringBuilder(b.code());

        List<CharSequence> labels = OwnFlexibleApiDemo
                .<Base, CharSequence>mapInto(entries, asLabel);

        System.out.println("labels: " + labels);
        System.out.println("types: "
                + labels.get(0).getClass().getSimpleName());
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:all OwnFlexibleApiDemo.java
java OwnFlexibleApiDemo
```

**Stdout đã kiểm chứng:**

```text
labels: [A, BC]
types: StringBuilder
```

### Phân tích đầy đủ ba tham số kiểu

Lời gọi:

```java
OwnFlexibleApiDemo.<Base, CharSequence>mapInto(entries, asLabel)
```

chỉ định `T=Base`, `R=CharSequence`.

**Tham số 1: `List<? extends T> source`.**

`entries` có `List<Entry>`; vì `Entry extends Base`, danh sách này là tham số hợp lệ của `List<? extends Base>`. Method **chỉ duyệt và đọc** ra các `Base`, không tự chèn `Base` vào một list có thể thật ra chỉ chứa `Entry`.

**Tham số 2: `Function<? super T, ? extends R> mapper`.**

`asLabel` có `Function<Base,StringBuilder>`. Phần đầu vào `Base` phù hợp với `? super Base`, phần đầu ra `StringBuilder` là subtype của `CharSequence`, phù hợp `? extends CharSequence`.

**Kiểu trả về: `List<R>`.**

Method thêm các giá trị `StringBuilder` vào `List<CharSequence>`; điều này an toàn do mỗi `StringBuilder` là `CharSequence`. Runtime object vẫn là `StringBuilder` như dòng `types`, không bị chuyển hóa thành một lớp mới.

**Tại sao dùng `.<Base,CharSequence>` tường minh?**

Để thể hiện lựa chọn `T`/`R` rõ ràng trong bài học. Java có type inference cho generic methods nên nhiều ngữ cảnh có thể bỏ type arguments tường minh, nhưng khi compiler suy luận một kiểu hẹp hơn hoặc có overload phức tạp, explicit type witness giúp đọc rõ hợp đồng mong muốn.

Không nên dùng explicit type witness ở mọi lời gọi nếu compiler đã suy luận rõ và code trở nên rườm rà; hãy ưu tiên cách dễ đọc, chỉ thêm khi cần kiểm soát kiểu đích.

## 12. Sự khác nhau giữa `? super` trong `Function` và wildcard trên kiểu trả về

Một câu dễ gây hiểu nhầm: “`map` trả `Stream<? extends R>` bởi mapper có `? extends R`”. **Sai.**

Chữ ký thật của `Stream.map` là:

```java
<R> Stream<R> map(Function<? super T, ? extends R> mapper);
```

Phương thức trả **`Stream<R>`**. Wildcard `? extends R` nằm **ở kiểu của callback được truyền vào**. Nó chỉ cho phép callback trả một subtype của `R` khi method xây stream kết quả kiểu `R`.

Tương tự:

```java
<V> Function<T, V> andThen(Function<? super R, ? extends V> after)
```

Trả `Function<T,V>`, **không** `Function<? super T,? extends V>`.

Khi đọc API Java, hãy phân biệt ba tầng:

1. **Kiểu của tham số method** — ví dụ `Function<? super T,? extends R>`.
2. **Kiểu trả về của method** — ví dụ `Stream<R>`.
3. **Kiểu khai báo của biến nhận kết quả** — ví dụ `List<CharSequence>`.

Wildcard không tự “lan” từ một tham số method sang mọi type argument của giá trị được trả về.

## 13. `? super T` không có nghĩa được đọc một giá trị kiểu `T` tùy ý

Trong `List<? super T>`:

```java
List<? super Dog> sink = new ArrayList<Animal>();
sink.add(new Dog("Milo"));  // Hợp lệ.
Object value = sink.get(0); // Chỉ bảo đảm Object.
```

Đây là đoạn minh họa giả định có lớp `Dog` và `Animal` tương ứng như chương trình A.

Lý do: danh sách thật có thể là `List<Animal>` hoặc `List<Object>`. Khi bạn đọc từ `List<? super Dog>`, compiler chỉ biết phần tử lấy ra là `Object` (ngoài `null`), không thể chắc là `Dog`.

Trong `Function<? super Dog, ? extends CharSequence>` cũng có quy tắc tương tự về **đầu vào của Function**: bạn có thể gọi `apply` với một `Dog`, vì mọi kiểu `? super Dog` đều nhận được `Dog`. Nhưng không thể suy ra loại callback thực sự là `Function<Dog, ...>`; nó có thể là `Function<Animal,...>` hoặc `Function<Object,...>`.

Chính vì giữ đúng giới hạn thông tin như vậy, wildcard vẫn **an toàn kiểu**, khác với raw `Function` hoặc unchecked casts.

## 14. Type inference và lambda: đọc ngữ cảnh trước khi đoán kiểu

Trong `Stream<String>`, method `map` cho compiler một `T` đã biết là `String`, rồi suy luận `R` từ mapper và ngữ cảnh kết quả:

```java
List<String> words = List.of("a", "bb");
List<Integer> lengths = words.stream()
        .map(String::length)
        .toList();
```

Một function nhận **supertype** cũng được:

```java
Function<CharSequence, Integer> count = CharSequence::length;
List<Integer> lengths = words.stream().map(count).toList();
```

Hai đoạn là ví dụ tách biệt và được đặt trong method với imports phù hợp; không khai báo đồng thời hai biến `lengths` trong cùng block. Khi cần cố định output ở một kiểu cha, hãy dùng type witness:

```java
List<CharSequence> names = words.stream()
        .<CharSequence>map(s -> new StringBuilder(s))
        .toList();
```

Đừng cast bừa khi inference khó hiểu. Hãy xác định `T`/`R` từ **chữ ký `Stream.map` và mapper cụ thể**. Bài về inference ở sau phân tích toàn bộ cơ chế target typing, generic method và overload.

## 15. Lambda và method reference: cùng nguyên lý target typing

Callback không tự mang một kiểu functional interface duy nhất nếu tách khỏi ngữ cảnh. Ví dụ `Animal::name` có thể dùng khi target descriptor nhận `Animal` hoặc các trường hợp subtype tương thích.

Trong chương trình `VarianceApiDemo` ở trên:

```java
Comparator<Animal> byName = Comparator.comparing(Animal::name);
dogs.sort(byName);
```

`Comparator.comparing` suy luận kiểu từ method reference; `List<Dog>.sort` chấp nhận `Comparator<? super Dog>` nên một `Comparator<Animal>` là hợp lệ. **Wildcard này nằm ở chữ ký API**, không phải do `Comparator<Animal>` trở thành `Comparator<Dog>`.

Nếu một callback dùng nhiều lần, khai báo `Function<Animal,StringBuilder>` hoặc `Comparator<Animal>` tường minh giúp kiểm tra quan hệ kiểu. Khi có overload/target typing phức tạp, đọc tiếp bài chuyên về inference và overload thay vì coi lambda là một “kiểu đặc biệt” bỏ qua compiler.

## 16. Khi nào `? extends R` là không đủ?

Một callback được yêu cầu trả `String`:

```java
Function<? super Dog, ? extends String> callback
```

**không** nhận tùy ý `Function<Dog,Object>`: một `Object` có thể là `Integer`, `Dog`, `List`, v.v., không bảo đảm là `String`.

Với một method cần `R=String`, compiler có trách nhiệm chặn callback chỉ hứa trả `Object`:

```java
// Minh họa lỗi compile-time:
Function<Dog, Object> tooBroad = dog -> dog.name();
// Stream<String> out = dogs.stream().<String>map(tooBroad); // Lỗi.
```

Mặc dù **lúc chạy** lambda trong ví dụ trả `String` thật, **kiểu khai báo của callback** chỉ là `Function<Dog,Object>`. Method không được dựa vào việc đoán nội dung triển khai để suy ra generic output `String`.

Nếu callback thật sự luôn trả chuỗi, hãy khai báo `Function<Dog,String>` hoặc `Function<Animal,String>` phù hợp. Đừng dùng unchecked cast từ `Function<Dog,Object>` sang `Function<Dog,String>` vì có thể che rủi ro và đẩy lỗi về runtime.

Tương tự, một `Function<Dog,R>` không thể được dùng tại nơi phải xử lý **mọi `Animal`**, vì có thể đầu vào là một loại `Animal` khác. Đây là biểu hiện của hướng **`? super T` ở đầu vào**.

## 17. Một số lỗi phổ biến khi đọc chữ ký API

**Lỗi 1 — Đảo hướng `super`/`extends`.** Đầu vào đi **vào** callback: `? super T`; kết quả đi **ra** callback: `? extends R`. Không dùng `? extends T` cho callback tiêu thụ `T` nếu không có lý do khác.

**Lỗi 2 — Nghĩ `Function<Animal,StringBuilder>` là subtype trực tiếp của `Function<Dog,CharSequence>`.** Generics invariant. Chính **wildcard ở phương thức nhận callback** làm cho lời gọi hợp lệ.

**Lỗi 3 — Tưởng `? super T` “chuyển” kiểu generic của function.** Wildcard cho phép **sử dụng** callback có input rộng hơn, nhưng không thay đổi declared type hoặc runtime class của chính callback.

**Lỗi 4 — Tưởng kết quả mapper buộc phải là đúng `R` theo runtime class.** `StringBuilder` có thể dùng như `CharSequence`; output `? extends R` chấp nhận subtype.

**Lỗi 5 — Nghĩ wildcard của mapper là wildcard của `Stream<R>` kết quả.** `Stream.map` trả `Stream<R>`, không `Stream<? extends R>` như một hiệu ứng tự động.

**Lỗi 6 — Nghĩ `Consumer.andThen` có thể đổi type parameter receiver tùy ý.** Method trả `Consumer<T>`; ví dụ `Consumer<Animal>.andThen(Consumer<Dog>)` không hợp lệ.

**Lỗi 7 — Dùng `List<Object>` khi cần chấp nhận `List<Dog>`.** `List<Dog>` không phải `List<Object>`. Với nguồn chỉ đọc, xem xét `List<? extends Animal>`, `List<?>` hoặc generic method có type variable.

**Lỗi 8 — Dùng raw `Function` hay raw `List` để né lỗi compiler.** Raw types làm mất bảo đảm generic, có thể dẫn đến unchecked warning và lỗi runtime. Wildcard/type inference đúng mới là cách xử lý.

**Lỗi 9 — Viết wildcard trong mọi type argument không cần thiết.** Một API chỉ dùng chính xác `Function<T,R>` trong bối cảnh nội bộ có thể không cần mở rộng. Dùng wildcard khi tăng khả năng tái sử dụng có ý nghĩa và vẫn mô tả đúng vai trò.

**Lỗi 10 — Cho rằng `? super T` luôn trả về `T` khi đọc collection.** Với `List<? super T>`, kết quả đọc chỉ được bảo đảm dưới dạng `Object`; wildcard super thích hợp cho tiêu thụ/ghi `T`, không phải một bảo đảm để đọc `T`.

**Lỗi 11 — Đánh đồng variance ở source với runtime type specialization.** `Function`, `Stream` và `List` vẫn dùng type erasure; wildcard không tạo class mới cho từng tham số hóa.

**Lỗi 12 — Suy luận chỉ từ tên generic parameter.** `T`, `R`, `V` chỉ là tên. Hãy đọc method thực sự **gọi tham số** hay **trả kết quả**, rồi xác định hướng dữ liệu.

## 18. Cách tự xác minh các chữ ký qua JDK

Các lệnh sau giúp bạn đối chiếu API đang dùng từ chính JDK:

```bash
javap -p java.util.stream.Stream
javap -p java.util.function.Function
javap -p java.util.function.Consumer
javap -p java.util.function.Predicate
javap -p java.util.List
javap -p java.lang.Iterable
```

Tìm các tên `map`, `flatMap`, `filter`, `compose`, `andThen`, `sort`, `forEach`. `javap` có thể trình bày generic signatures để bạn xem wildcard cùng type variables.

Đối với chương trình `OwnFlexibleApiDemo.java`, chạy:

```bash
javap -p -s OwnFlexibleApiDemo
```

Trích kết quả đã kiểm chứng:

```text
static <T, R> java.util.List<R> mapInto(
    java.util.List<? extends T>,
    java.util.function.Function<? super T, ? extends R>);
  descriptor: (Ljava/util/List;Ljava/util/function/Function;)Ljava/util/List;
```

Sự khác nhau ở đây quan trọng:

- **Generic signature** mà `javap` hiển thị ở dòng khai báo giữ wildcard `? extends T`, `? super T`, `? extends R` như metadata.
- **JVM descriptor** dùng các lớp bị xóa kiểu `List` và `Function`. Nó không có một runtime class riêng cho `Function<Animal,StringBuilder>` so với `Function<Dog,CharSequence>`.
- Tính hợp lệ của việc truyền callback được compiler kiểm tra dựa trên Java generic type system, không phải nhờ runtime cast tùy ý giữa các dạng `Function`.

Nếu muốn quan sát bytecode cụ thể của lời gọi `apply`, dùng `javap -p -c OwnFlexibleApiDemo`. Nhưng để xác minh câu hỏi trọng tâm về **chữ ký wildcard**, `javap -p` và source JDK đã là bằng chứng trực tiếp và dễ đọc nhất.

## 19. Quy trình chọn wildcard khi tự thiết kế API

Đối với một method nhận callback generic, hãy theo các bước:

1. **Xác định loại dữ liệu mà API chủ động đưa vào callback.** Nếu API gọi `mapper.apply(item)` với `item: T`, callback là **consumer của `T`**, nên có thể nhận `? super T`.
2. **Xác định loại kết quả mà API cần nhận từ callback.** Nếu API muốn một kết quả dùng như `R`, callback là **producer của `R`**, nên có thể trả `? extends R`.
3. **Kiểm tra chữ ký return của method.** Nếu method trả `List<R>` hay `Stream<R>`, đừng tự chuyển thành wildcard output nếu không cần.
4. **Đánh giá nguồn collection.** Nếu chỉ đọc các `T` từ list, `List<? extends T>` có thể phù hợp. Nếu ghi `T` vào destination, `Collection<? super T>` có thể phù hợp.
5. **Kiểm tra bằng các subtype/supertype thực tế.** Tạo một callback nhận supertype và trả subtype; thử với một nguồn list phần tử subtype.
6. **Chạy `javac -Xlint:all` với Java version mục tiêu.** Xử lý lỗi generic bằng quan hệ kiểu đúng, không dùng raw types để “cho biên dịch”.
7. **Đối chiếu `javap` nếu cần:** phân biệt generic metadata với JVM descriptor sau erasure.
8. **Giữ thiết kế dễ đọc.** Wildcard phục vụ tính linh hoạt khi đúng hướng dữ liệu; không cần làm mọi tham số phức tạp nếu lợi ích nhỏ.

Đối với callback, một câu hỏi nhanh rất hiệu quả là: *“Tôi có thể đưa một T cho hàm này mà không cast không? Và kết quả hàm trả có được phép xem như R không?”* Nếu cả hai đều có, chữ ký `Function<? super T,? extends R>` đang làm đúng nhiệm vụ.

## 20. Bài tập có đáp án

### Bài 1 — Chọn callback cho stream

Có `Stream<Dog>`, `Dog extends Animal`, và cần stream kết quả kiểu `CharSequence`. Những callback nào có thể dùng qua `map` với `R=CharSequence`?

```java
Function<Animal, String> a = Animal::name;
Function<Dog, StringBuilder> b = d -> new StringBuilder(d.name());
Function<Object, CharSequence> c = Object::toString;
```

**Đáp án:** cả ba đều thỏa `Function<? super Dog,? extends CharSequence>` (giả định các class `Animal`/`Dog` trong ví dụ). `Animal`, `Dog`, `Object` đều nhận được `Dog` theo quan hệ kiểu; `String`, `StringBuilder`, `CharSequence` đều có thể dùng như `CharSequence`.

### Bài 2 — Vì sao callback này sai?

```java
Function<Animal, Object> broadResult = animal -> animal.name();
// Stream<String> out = dogs.stream().<String>map(broadResult);
```

**Đáp án:** `Object` không phải subtype của `String`. Mặc dù lambda thực tế trả chuỗi, kiểu khai báo callback chỉ cam kết `Object`; `? extends String` không chấp nhận nó.

### Bài 3 — `Consumer.andThen` giữ kiểu nào?

Với `Consumer<Dog> dogOnly` và `Consumer<Animal> allAnimals`, kết quả `dogOnly.andThen(allAnimals)` có kiểu gì?

**Đáp án:** `Consumer<Dog>`. Consumer tiếp theo nhận `Animal`, phù hợp `? super Dog`, còn return type của `andThen` vẫn là `Consumer<T>` với `T=Dog`.

### Bài 4 — Comparator theo supertype

Tại sao `List<Dog>.sort(Comparator<Animal>)` hợp lệ?

**Đáp án:** `List<E>.sort` nhận `Comparator<? super E>`; `Comparator<Animal>` so sánh được hai `Dog` vì mỗi `Dog` là một `Animal`.

### Bài 5 — `compose` chạy theo thứ tự nào?

`Function<Number,String> f` và `Function<CharSequence,Integer> before`; gọi `f.compose(before)` để tạo `Function<String,String>` hợp lệ không?

**Đáp án:** hợp lệ. Hàm `before` nhận `String` vì `CharSequence` là supertype, trả `Integer` vì `Integer` là subtype của `Number`; sau đó `f` nhận kết quả kiểu `Number`.

### Bài 6 — `flatMap` có wildcard nào?

Vì sao `Stream.flatMap` nhận `Function<? super T,? extends Stream<? extends R>>` thay vì chỉ `Function<T,Stream<R>>`?

**Đáp án:** để callback có thể nhận một supertype của `T` và trả stream của những phần tử subtype của `R`, trong khi API vẫn flatten chúng thành `Stream<R>` an toàn.

### Bài 7 — Đọc/ghi wildcard collection

Nếu method chỉ cần duyệt `List<Dog>` để lấy các `Animal`, `List<? extends Animal>` có giúp gì? Nó có cho gọi `list.add(new Animal(...))` không?

**Đáp án:** nhận được nhiều loại list của các subtype `Animal` để **đọc** an toàn. Không được thêm một `Animal` tùy ý, vì list thực có thể chỉ cho phép `Dog` hoặc subtype khác.

### Bài 8 — Generic output type

Với mapper `Function<Animal,StringBuilder>` và source `Stream<Dog>`, nếu để compiler suy luận mặc định, kiểu phần tử kết quả có thể là `StringBuilder`. Làm sao chủ động chọn `R=CharSequence`?

**Đáp án:** dùng `.<CharSequence>map(mapper)` hoặc một ngữ cảnh generic target typing tương ứng khi compiler có thể suy luận. Cách viết tường minh là dễ kiểm chứng nhất ở ví dụ này.

### Bài 9 — Phân biệt generic method với runtime

Chạy `javap -s` thấy descriptor `(List,Function)List` cho `mapInto`, dù generic signature có nhiều wildcard. Có nghĩa compiler bỏ qua kiểm tra kiểu `? super`/`? extends` không?

**Đáp án:** không. Compiler kiểm tra các ràng buộc generic ở source và ghi descriptor erased cho JVM; generic signature metadata và kiểm tra compile-time là hai tầng khác với erased runtime descriptor.

## 21. Checklist trước khi dùng wildcard trong API

- [ ] Xác định **`T` nào được đưa vào** callback và **`R` nào được nhận lại**.
- [ ] Nếu callback nhận `T`, cân nhắc `? super T`.
- [ ] Nếu callback trả một subtype phù hợp với `R`, cân nhắc `? extends R`.
- [ ] Phân biệt variance của **tham số callback** với kiểu **trả về của API**.
- [ ] Đọc đúng chữ ký `Stream.map`, `flatMap`, `filter`, `Consumer.andThen`, `Function.compose/andThen`.
- [ ] Không lẫn generic invariance với khả năng dùng wildcard ở use-site.
- [ ] Không dùng raw type hoặc unchecked cast để né một lỗi về hướng variance.
- [ ] Nếu generic inference phức tạp, thử explicit method type arguments có ý nghĩa.
- [ ] Kiểm tra trường hợp subtype đầu vào và subtype đầu ra bằng một ví dụ runnable.
- [ ] Nhớ `getClass()`/erasure không làm phát sinh những runtime classes riêng cho từng callback generic.

## Kết luận

**`Function<? super T, ? extends R>`** là cách Java Standard API diễn đạt một hợp đồng callback linh hoạt nhưng an toàn: hàm có thể **tiêu thụ T qua một supertype** và **sản xuất R qua một subtype**. Cùng tư duy đó xuất hiện trong `Stream.map`, `flatMap`, `Predicate<? super T>`, `Consumer<? super T>`, `Comparator<? super T>`, `Function.compose` và `Function.andThen`.

Hãy đọc **hướng chảy dữ liệu**, không chỉ học thuộc dấu wildcard. Khi callback nhận giá trị, nghĩ `super`; khi callback trả giá trị dùng như kiểu đích, nghĩ `extends`. Đó là nền tảng để viết lambda, method references và API generic Java dễ tái sử dụng mà không đánh đổi type safety.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-standard-ap-is-003">Generic với Comparator và phép nối tiêu chí</a>

<details>
<summary>Xem chi tiết</summary>

# Generic với `Comparator<T>` và phép nối tiêu chí trong Java

## 1. Mục tiêu: sắp xếp đối tượng theo nhiều tiêu chí có kiểm tra kiểu

Trong ứng dụng Java, ta thường cần sắp xếp danh sách các đối tượng theo nhiều thuộc tính: nhân viên theo phòng ban rồi tên; sinh viên theo điểm giảm dần rồi họ tên; sản phẩm theo giá, ngày cập nhật rồi mã định danh. Việc chỉ viết một phép so sánh đơn giản thường chưa đủ.

Java cung cấp interface generic **`Comparator<T>`** và các factory/chaining methods để xây dựng thứ tự một cách rõ ràng:

```java
Comparator<Student> order =
        Comparator.<Student>comparingInt(Student::score)
                .reversed()
                .thenComparing(Student::name)
                .thenComparingInt(Student::age);
```

Đoạn trên là một mảnh code minh họa; giả định có record hoặc class `Student` với các accessor `score()`, `name()`, `age()`, và import `java.util.Comparator`.

Nó biểu diễn đúng một hợp đồng sắp xếp:

1. **Điểm giảm dần**: sinh viên có điểm cao đứng trước.
2. Nếu **điểm bằng nhau**, so sánh **tên tăng dần**.
3. Nếu **cả điểm và tên bằng nhau**, so sánh **tuổi tăng dần**.

Dấu `.reversed()` đặt **ngay sau comparator điểm** rất quan trọng: nó đảo chiều điểm, không tự đảo hai tiêu chí nối tiếp.

**Nội dung trọng tâm của bài:** Hiểu `Comparator<T>` là một *hợp đồng so sánh có kiểu*, biết sử dụng lambda/method reference với `Comparator.comparing`, `comparingInt` và `thenComparing`, đặt `reversed()` chính xác, xử lý `null`, và giữ tính đúng đắn của thứ tự trong `List.sort`, `Stream.sorted` và các sorted collections.

## 2. `Comparator<T>` là gì?

`java.util.Comparator<T>` là một **functional interface**, trong đó thao tác chính là:

```java
int compare(T left, T right);
```

Ý nghĩa dấu của giá trị trả về:

| Kết quả của `compare(a, b)` | Ý nghĩa |
|---|---|
| Nhỏ hơn 0 | `a` đứng trước `b` theo comparator này |
| Bằng 0 | `a` và `b` bằng nhau **về thứ tự so sánh** |
| Lớn hơn 0 | `a` đứng sau `b` |

Không có yêu cầu phải trả đúng `-1`, `0` hoặc `1`; Java chỉ dựa vào **dấu** của kết quả.

Ví dụ với số nguyên:

```java
Comparator<Integer> ascending = (a, b) ->
        Integer.compare(a, b);
```

Đây là một lambda hợp lệ khi có import `java.util.Comparator`. Nếu `a = 2`, `b = 8`, `compare(a,b)` có giá trị âm; nếu đảo vị trí thì dương.

Generic type argument `Integer` trong `Comparator<Integer>` khiến compiler xác nhận lambda làm việc với hai `Integer`. Việc viết `(a, b)` không khai báo kiểu tường minh vẫn an toàn vì **target type** cung cấp thông tin cho lambda.

### Hợp đồng so sánh phải nhất quán

Comparator không chỉ là một phép tính trả số tùy ý. Nó cần thỏa các thuộc tính quan trọng:

- **Phản xứng về dấu**: dấu của `compare(a,b)` phải ngược dấu của `compare(b,a)` với những giá trị mà phép so sánh xác định.
- **Bắc cầu**: nếu `a > b` và `b > c` theo comparator, phải có `a > c`.
- **Nhất quán khi bằng 0**: nếu `compare(a,b) == 0`, việc so `a` hay `b` với một `z` khác phải cho cùng dấu.
- Comparator nên cho kết quả ổn định khi trạng thái liên quan tới thứ tự không thay đổi.

Vi phạm hợp đồng có thể làm thuật toán sort hoạt động sai, kết quả bất ngờ hoặc trong một số tình huống gây ngoại lệ về vi phạm general contract.

## 3. `Comparator<T>` và `Comparable<T>` khác nhau thế nào?

`Comparable<T>` mô tả một **thứ tự tự nhiên** do chính kiểu dữ liệu định nghĩa:

```java
class Version implements Comparable<Version> {
    private final int number;

    Version(int number) {
        this.number = number;
    }

    @Override
    public int compareTo(Version other) {
        return Integer.compare(number, other.number);
    }
}
```

Đoạn class trên hợp lệ nhưng không có `main`.

`Comparator<T>` là **quy tắc so sánh ở bên ngoài kiểu**, có thể thay đổi tùy ngữ cảnh:

```java
Comparator<Version> descending = (a, b) ->
        b.compareTo(a);
```

Những khác biệt chính:

| `Comparable<T>` | `Comparator<T>` |
|---|---|
| Kiểu thường tự triển khai `compareTo` | Có thể đặt ngoài class |
| Một thứ tự tự nhiên do class lựa chọn | Có thể có nhiều thứ tự khác nhau |
| `Collections.sort(list)`/`sorted()` không comparator dựa vào nó | `sort(comparator)`/`sorted(comparator)` dùng comparator được truyền |
| Hữu ích khi bản thân kiểu có natural order rõ ràng | Hữu ích khi yêu cầu nghiệp vụ thay đổi hoặc có nhiều tiêu chí |

Khi một bản ghi có nhiều cách sắp xếp hợp lý (theo điểm, tên, tuổi…), một hoặc nhiều `Comparator<Student>` thường rõ ràng hơn việc cố nhồi tất cả vào `compareTo`.

## 4. Lambda và method reference đi vào `Comparator<T>`

Vì `Comparator<T>` là functional interface, ta có thể tạo comparator từ lambda:

```java
Comparator<String> byLength = (left, right) ->
        Integer.compare(left.length(), right.length());
```

Hoặc dùng một static factory method với **method reference**:

```java
Comparator<String> byLength =
        Comparator.comparingInt(String::length);
```

`String::length` đại diện cho một hàm nhận `String` và trả `int`; nó phù hợp với `ToIntFunction<? super String>` mà `comparingInt` yêu cầu.

Về ý nghĩa, hai comparator đều so chuỗi theo độ dài tăng dần. Chúng **không tự thêm tiêu chí theo thứ tự từ điển**; hai chuỗi có cùng độ dài sẽ cho kết quả `0`.

Để có thứ tự phụ:

```java
Comparator<String> byLengthThenText =
        Comparator.comparingInt(String::length)
                .thenComparing(Comparator.naturalOrder());
```

Đây là ví dụ dùng lambda/method reference **và nối tiêu chí**; compiler suy luận các generic types liên quan từ target type `Comparator<String>`.

Khi compiler khó suy luận từ một method chain dài, ta có thể nêu rõ type ở biến, hoặc dùng **explicit type witness** như `Comparator.<Student>comparingInt(Student::score)`.

## 5. Ví dụ đầy đủ: điểm giảm dần, tên tăng dần, tuổi tăng dần

Tạo tệp **`StudentComparatorDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class StudentComparatorDemo {

    record Student(String name, int score, int age) {
    }

    public static void main(String[] args) {
        List<Student> students = new ArrayList<>(List.of(
                new Student("Binh", 90, 22),
                new Student("An", 90, 21),
                new Student("Chi", 80, 20),
                new Student("An", 90, 19)
        ));

        Comparator<Student> byScoreNameAge =
                Comparator.<Student>comparingInt(Student::score)
                        .reversed()
                        .thenComparing(Student::name)
                        .thenComparingInt(Student::age);

        students.sort(byScoreNameAge);

        for (Student s : students) {
            System.out.println(
                    s.name() + "|" + s.score() + "|" + s.age());
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac StudentComparatorDemo.java
java StudentComparatorDemo
```

Stdout:

```text
An|90|19
An|90|21
Binh|90|22
Chi|80|20
```

Phân tích:

1. `Comparator.<Student>comparingInt(Student::score)` tạo comparator kiểu `Comparator<Student>` và trích điểm bằng hàm `Student -> int`.
2. `.reversed()` đảo thứ tự của **comparator điểm đã tạo**, đưa `90` trước `80`.
3. `.thenComparing(Student::name)` chỉ so tên khi điểm được coi là bằng nhau. Ở đây `"An"` đứng trước `"Binh"` theo `String` natural order.
4. `.thenComparingInt(Student::age)` chỉ chạy khi điểm **và** tên đều bằng nhau. `"An"` tuổi `19` đứng trước `"An"` tuổi `21`.
5. `students.sort(...)` thay đổi thứ tự phần tử của list có thể chỉnh sửa.

Nếu muốn **tên giảm dần** thay vì tăng dần, cần đặt comparator riêng cho tên ở đoạn `thenComparing`, không nhất thiết đảo toàn bộ comparator.

## 6. Cơ chế short-circuit của `thenComparing`

`thenComparing(other)` tạo một comparator hợp thành. Ý tưởng logic là:

```java
int first = primary.compare(a, b);
if (first != 0) {
    return first;
}
return secondary.compare(a, b);
```

Đây là **mô hình minh họa**, không phải nội dung bytecode hoặc triển khai duy nhất của thư viện JDK.

Điều quan trọng:

- Khi tiêu chí chính đã phân biệt được hai object, tiêu chí phụ **không cần** được gọi.
- Chỉ khi comparator chính trả `0`, comparator phụ mới quyết định.
- Có thể nối nhiều cấp. Mỗi cấp sau chỉ có ý nghĩa khi mọi cấp trước bằng nhau.

Với phép nối ba cấp, có thể hình dung:

```text
So điểm → khác? dùng kết quả đó
       → bằng → so tên → khác? dùng kết quả đó
                     → bằng → so tuổi
```

Điều này thuận lợi cả về cấu trúc logic lẫn việc tránh tính key phụ không cần thiết. Tuy nhiên không nên đưa side effects vào comparator/key extractor: thuật toán sort có thể gọi comparator nhiều lần và theo thứ tự không đơn giản.

## 7. `comparing`, `comparingInt`, `comparingLong`, `comparingDouble`

Java cung cấp nhiều cách tạo comparator từ một **khóa sắp xếp** (*sort key*).

### `Comparator.comparing(Function)`

Khi khóa là một kiểu có thứ tự tự nhiên (*Comparable*), chẳng hạn `String`, `LocalDate`, `BigDecimal`:

```java
Comparator<Student> byName =
        Comparator.comparing(Student::name);
```

Compiler biết `Student::name` trả `String`, và `String` có natural order. Đây là comparator theo tên tăng dần.

### `Comparator.comparingInt(ToIntFunction)`

Khi khóa là `int`:

```java
Comparator<Student> byScore =
        Comparator.comparingInt(Student::score);
```

`comparingInt` dùng `ToIntFunction<? super T>` và so sánh `int` theo ngữ nghĩa `Integer.compare`, không yêu cầu tạo khóa dạng wrapper trong hợp đồng API.

### Các dạng primitive khác

```java
Comparator<Order> byTimestamp =
        Comparator.comparingLong(Order::timestamp);

Comparator<Order> byAmount =
        Comparator.comparingDouble(Order::amount);
```

Đây là những đoạn **minh họa**, giả sử class `Order` có các accessor `timestamp(): long` và `amount(): double`.

Các bản primitive-specific đặc biệt hữu ích khi khóa vốn là primitive, làm rõ ý định và tránh chuyển khóa sang wrapper chỉ để so sánh.

### Khi nào cần overload `comparing(extractor, keyComparator)`?

Nếu khóa không có natural order, hoặc cần một quy tắc khác, truyền comparator của **kiểu khóa**:

```java
Comparator<Student> byNameIgnoreCase =
        Comparator.comparing(
                Student::name,
                String.CASE_INSENSITIVE_ORDER);
```

Trong trường hợp tên có thể là `null`, comparator cho khóa cũng cần xử lý `null`; phần sau có ví dụ.

## 8. Generic signatures và ý nghĩa của `T`, `U`

Phần lớn sức mạnh của API `Comparator` nằm ở generic type parameters. Các chữ ký tiêu biểu (rút gọn từ Java API) là:

```java
public interface Comparator<T> {

    int compare(T first, T second);

    Comparator<T> thenComparing(
            Comparator<? super T> other);

    <U extends Comparable<? super U>>
    Comparator<T> thenComparing(
            Function<? super T, ? extends U> keyExtractor);

    <U> Comparator<T> thenComparing(
            Function<? super T, ? extends U> keyExtractor,
            Comparator<? super U> keyComparator);
}
```

Đây là **chữ ký rút gọn để học**, không phải một interface có thể copy nguyên và thay thế `java.util.Comparator`.

Phân biệt hai type parameters:

- `T`: kiểu đối tượng **được sắp xếp**, ví dụ `Student`.
- `U`: kiểu **khóa trích xuất**, ví dụ `String`, `LocalDate`, `BigDecimal`.

Vì vậy:

```java
Comparator<Student> byName =
        Comparator.comparing(Student::name);
```

có đối tượng so sánh kiểu `Student`, nhưng khóa `name()` là `String`. Hai kiểu không cần trùng nhau.

Trong bản `comparing` không truyền comparator cho key, khóa `U` cần thực hiện `Comparable<? super U>` để có thứ tự tự nhiên. Khi `U` không có natural order, hãy dùng overload có `Comparator<? super U>`.

### `? super T` bảo vệ tính linh hoạt

Một `Comparator<Object>` có thể so hai giá trị theo quy tắc dùng chung, vì nó nhận được mọi `Student` vốn là `Object`. Do đó các API sắp xếp dùng `Comparator<? super T>` để cho phép so sánh bằng comparator của chính `T` hoặc một **supertype** phù hợp.

Tương tự, `Function<? super T, ? extends U>` chấp nhận một key extractor đọc được `T` hoặc một kiểu cha, và cho ra một giá trị phù hợp với `U`.

Đây là ứng dụng quy tắc thường được gọi là **PECS**: consumer nhận `? super`, producer trả `? extends`. Không phải wildcard tùy ý; chúng mô tả hướng dữ liệu đi qua functional interfaces.

## 9. Vì sao cần explicit type witness ở một số chuỗi gọi?

Với biểu thức:

```java
Comparator.comparingInt(Student::score)
        .reversed()
        .thenComparing(Student::name)
```

compiler đôi khi không có đủ thông tin target type sớm để suy luận đúng `Student` trong một chuỗi phương thức, đặc biệt khi một method trung gian như `.reversed()` buộc kiểm tra kiểu kết quả trước khi toàn chuỗi hoàn tất.

Một cách chắc chắn là đặt rõ generic argument cho **factory method**:

```java
Comparator<Student> order =
        Comparator.<Student>comparingInt(Student::score)
                .reversed()
                .thenComparing(Student::name);
```

`<Student>` ngay sau `Comparator.` là **explicit method type argument** cho `comparingInt`, không phải cast và không tạo thêm class runtime.

Một cách khác là tách comparator ban đầu:

```java
Comparator<Student> byScore =
        Comparator.comparingInt(Student::score);

Comparator<Student> order =
        byScore.reversed().thenComparing(Student::name);
```

Khi đọc code, **kiểu được đặt ở biến trung gian** cũng giúp compiler và người bảo trì thấy rõ khóa áp dụng cho `Student`, chứ không phải một `Object` chưa biết.

### Với `Comparator.comparing`, có hai type parameters

Chữ ký `comparing` tổng quát có hai kiểu: đối tượng `T` và khóa `U`. Nếu cần type witness đầy đủ:

```java
Comparator<Student> byName =
        Comparator.<Student, String>comparing(Student::name);
```

Trong code đơn giản, target typing thông thường đã đủ; không cần lạm dụng việc viết explicit type arguments ở mọi nơi.

## 10. `reversed()` đảo đúng phạm vi nào?

`reversed()` trả một comparator có thứ tự ngược với comparator nhận lời gọi.

Điểm bẫy: khi gọi `.reversed()` **sau khi** đã nối nhiều tiêu chí, nó đảo **toàn bộ chuỗi**, không chỉ tiêu chí gần nhất.

Xét:

```java
Comparator<Student> bothAsc =
        Comparator.comparingInt(Student::score)
                .thenComparing(Student::name);

Comparator<Student> bothDesc = bothAsc.reversed();
```

`bothDesc` ưu tiên **điểm giảm dần**, và nếu điểm bằng nhau thì **tên giảm dần**.

Ngược lại, nếu muốn điểm giảm dần **nhưng tên tăng dần**:

```java
Comparator<Student> scoreDescNameAsc =
        Comparator.<Student>comparingInt(Student::score)
                .reversed()
                .thenComparing(Student::name);
```

Đặt `reversed()` **trước** `thenComparing(...)`.

### Ví dụ so ba kết quả

Tạo tệp **`ReversedScopeDemo.java`**:

```java
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ReversedScopeDemo {

    record Item(String label, int quantity) {
    }

    static String render(List<Item> items, Comparator<Item> order) {
        return items.stream()
                .sorted(order)
                .map(i -> i.label() + ":" + i.quantity())
                .collect(Collectors.joining(", "));
    }

    public static void main(String[] args) {
        List<Item> items = List.of(
                new Item("B", 2),
                new Item("A", 2),
                new Item("C", 1));

        Comparator<Item> ascending =
                Comparator.<Item>comparingInt(Item::quantity)
                        .thenComparing(Item::label);

        Comparator<Item> reversedAll = ascending.reversed();

        Comparator<Item> quantityDescLabelAsc =
                Comparator.<Item>comparingInt(Item::quantity)
                        .reversed()
                        .thenComparing(Item::label);

        System.out.println(render(items, ascending));
        System.out.println(render(items, reversedAll));
        System.out.println(render(items, quantityDescLabelAsc));
    }
}
```

Chạy:

```bash
javac ReversedScopeDemo.java
java ReversedScopeDemo
```

Stdout:

```text
C:1, A:2, B:2
B:2, A:2, C:1
A:2, B:2, C:1
```

Dòng cuối là yêu cầu thường gặp: **số lượng giảm dần**, **nhãn tăng dần**. Dòng giữa chứng minh việc `.reversed()` đặt ở cuối đảo cả tiêu chí phụ.

## 11. Đảo riêng một tiêu chí phụ

Nếu muốn **điểm tăng dần, tên giảm dần**, ta không đảo comparator sau khi đã nối toàn bộ. Thay vào đó, xây một comparator tên đảo chiều rồi truyền cho `thenComparing`:

```java
Comparator<Student> scoreAscNameDesc =
        Comparator.<Student>comparingInt(Student::score)
                .thenComparing(
                        Comparator.comparing(
                                Student::name,
                                Comparator.reverseOrder()));
```

Trong ví dụ, `Comparator.reverseOrder()` cung cấp comparator cho khóa `String` theo natural order đảo chiều. Đây là đoạn minh họa với `Student` như đã định nghĩa.

Một cách thể hiện trực tiếp hơn:

```java
Comparator<Student> byNameDescending =
        Comparator.comparing(
                Student::name,
                Comparator.reverseOrder());

Comparator<Student> scoreAscNameDesc =
        Comparator.comparingInt(Student::score)
                .thenComparing(byNameDescending);
```

Khi có nhiều chiều tăng/giảm đan xen, **tạo comparator riêng cho từng khóa** hoặc tách thành biến có tên thường dễ đọc hơn một chain dài với nhiều lần `.reversed()`.

## 12. `thenComparing` và các bản chuyên primitive

API có nhiều overload để nối tiêu chí:

```java
primary.thenComparing(secondaryComparator);
primary.thenComparing(keyExtractor);
primary.thenComparing(keyExtractor, keyComparator);
primary.thenComparingInt(intKeyExtractor);
primary.thenComparingLong(longKeyExtractor);
primary.thenComparingDouble(doubleKeyExtractor);
```

- `thenComparing(Comparator<? super T>)`: nhận comparator hoàn chỉnh của cùng kiểu đối tượng `T`.
- `thenComparing(Function...)`: trích một khóa có natural order rồi so khóa đó.
- `thenComparing(Function..., Comparator...)`: trích khóa và dùng comparator riêng cho khóa.
- `thenComparingInt/Long/Double`: trích primitive keys và so theo cách tương ứng.

Ví dụ:

```java
Comparator<Student> byNameThenScore =
        Comparator.comparing(Student::name)
                .thenComparingInt(Student::score);
```

Nếu `Student::score` trả `int`, `thenComparingInt` biểu đạt đúng kiểu khóa. Nếu dữ liệu có `double` và giá trị `NaN`, comparator dùng `Double.compare` với quy tắc nhất quán của Java; không nên tự so bằng phép trừ rồi ép sang `int`.

## 13. Null: cần phân biệt null **đối tượng** và null **khóa**

Một trong các lỗi phổ biến là:

```java
Comparator<Student> byName =
        Comparator.comparing(Student::name);
```

Nếu `name()` có thể trả `null`, natural order của `String` không xử lý giá trị `null`; lời gọi comparator có thể ném `NullPointerException`.

Có hai tình huống khác nhau:

1. **Student object không null, nhưng `name` null**: đặt `Comparator.nullsFirst` hoặc `nullsLast` lên **comparator của khóa**.
2. **Chính Student object có thể null trong list**: bọc **toàn bộ comparator của Student** bằng `Comparator.nullsFirst` hoặc `nullsLast`.

### Null ở khóa

```java
Comparator<Student> byName =
        Comparator.comparing(
                Student::name,
                Comparator.nullsLast(
                        Comparator.naturalOrder()));
```

Biểu thức này cho `null` **ở giá trị `name`** đứng sau những tên không null. Nó không tự bảo vệ trường hợp bản thân `Student` là `null`, vì key extractor `Student::name` vẫn phải được gọi trên object.

### Null ở object

```java
Comparator<Student> nullStudentsLast =
        Comparator.nullsLast(
                Comparator.comparingInt(Student::score));
```

Lúc này, `null` ở cấp **Student** được xếp sau object khác. Nếu một Student không null lại có khóa nullable, bên trong comparator vẫn cần xử lý khóa riêng.

**Đừng dùng một `nullsLast` ở cấp khóa rồi tin rằng nó tự bao mọi cấp null.**

## 14. Ví dụ chạy được: null trong tên và so sánh không phân biệt hoa thường

Tạo tệp **`NullableKeyComparatorDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NullableKeyComparatorDemo {

    record Person(String name, int id) {
    }

    public static void main(String[] args) {
        List<Person> people = new ArrayList<>(List.of(
                new Person("b", 3),
                new Person(null, 2),
                new Person("A", 2),
                new Person("a", 1)));

        Comparator<Person> byNameThenId =
                Comparator.comparing(
                        Person::name,
                        Comparator.nullsLast(
                                String.CASE_INSENSITIVE_ORDER))
                        .thenComparingInt(Person::id);

        people.sort(byNameThenId);

        for (Person p : people) {
            System.out.println(
                    String.valueOf(p.name()) + "#" + p.id());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac NullableKeyComparatorDemo.java
java NullableKeyComparatorDemo
```

Stdout:

```text
a#1
A#2
b#3
null#2
```

Giải thích:

- `String.CASE_INSENSITIVE_ORDER` xem `"a"` và `"A"` bằng nhau về khóa tên.
- `thenComparingInt(Person::id)` phân biệt hai bản ghi đó, đưa id `1` trước id `2`.
- `Comparator.nullsLast(...)` áp dụng ở **giá trị `name`** nên người có tên `null` đứng cuối.
- Tất cả **Person object** trong ví dụ đều không null. Nếu list chứa một phần tử `null` thật sự, cần bọc comparator ngoài cùng bằng `Comparator.nullsLast(byNameThenId)`.

`String.CASE_INSENSITIVE_ORDER` không đồng nghĩa một thứ tự theo ngôn ngữ tự nhiên cho mọi locale; việc sắp xếp tiếng Việt theo quy tắc ngôn ngữ là câu hỏi khác, xem phần `Collator`.

## 15. Null ở toàn bộ phần tử list

Tạo tệp **`NullElementComparatorDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class NullElementComparatorDemo {

    record Entry(int id) {
    }

    public static void main(String[] args) {
        List<Entry> entries = new ArrayList<>(
                Arrays.asList(new Entry(3), null, new Entry(1)));

        Comparator<Entry> byId =
                Comparator.comparingInt(Entry::id);

        entries.sort(Comparator.nullsLast(byId));

        for (Entry e : entries) {
            System.out.println(e == null ? "null" : e.id());
        }
    }
}
```

Chạy:

```bash
javac NullElementComparatorDemo.java
java NullElementComparatorDemo
```

Stdout:

```text
1
3
null
```

`Arrays.asList` ở đây được dùng để tạo đầu vào **có chứa `null`**, sau đó sao chép sang `ArrayList`. Không dùng `List.of(new Entry(3), null, ...)` vì `List.of` từ chối `null`.

`Comparator.nullsLast(byId)` kiểm tra cấp object trước khi gọi `byId`, nên object `null` được xếp sau object không null mà không truy cập accessor trên `null`.

## 16. `List.sort`, `Collections.sort` và `Stream.sorted`

Có nhiều API dùng chung `Comparator<T>` nhưng **khác cách trả kết quả và tác động lên input**.

### `List.sort(comparator)`

```java
List<Student> mutable = new ArrayList<>(source);
mutable.sort(order);
```

`List.sort` sắp xếp **trên chính list nhận lời gọi**. Nếu list không hỗ trợ thay đổi phần tử, thao tác có thể ném `UnsupportedOperationException`.

### `Collections.sort(list, comparator)`

```java
Collections.sort(mutable, order);
```

Là tiện ích sắp xếp list với comparator; trong Java hiện đại, nó dùng hợp đồng sắp xếp của list tương ứng. Không cần chọn `Collections.sort` chỉ vì comparator generic.

### `Stream.sorted(comparator)`

```java
List<Student> sorted =
        source.stream().sorted(order).toList();
```

Tạo một pipeline rồi thu kết quả thành list mới. `Stream.toList()` trong Java 17 trả một list **unmodifiable**; đây không phải mảng/list có thể tự do thêm phần tử.

Tính ổn định (*stability*) của sorting cũng cần xét:

- `List.sort` được quy định là **stable**: những phần tử có comparator result `0` giữ thứ tự tương đối.
- `Stream.sorted` được bảo đảm stable đối với **ordered streams**; với **unordered streams**, không có bảo đảm giống vậy.
- Trong mọi trường hợp, tính stable không thay thế việc định nghĩa các tie-breakers thực sự quan trọng cho nghiệp vụ.

## 17. Ví dụ chạy được: sort tại chỗ và sorted stream

Tạo tệp **`SortApisDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SortApisDemo {

    record Product(String code, int price) {
    }

    static String codes(List<Product> products) {
        return products.stream()
                .map(Product::code)
                .toList()
                .toString();
    }

    public static void main(String[] args) {
        List<Product> original = List.of(
                new Product("B", 200),
                new Product("A", 100),
                new Product("C", 200));

        Comparator<Product> byPriceThenCode =
                Comparator.<Product>comparingInt(Product::price)
                        .thenComparing(Product::code);

        List<Product> mutable = new ArrayList<>(original);
        mutable.sort(byPriceThenCode);

        List<Product> fromStream = original.stream()
                .sorted(byPriceThenCode)
                .toList();

        System.out.println(codes(original));
        System.out.println(codes(mutable));
        System.out.println(codes(fromStream));
    }
}
```

Lệnh:

```bash
javac SortApisDemo.java
java SortApisDemo
```

Stdout:

```text
[B, A, C]
[A, B, C]
[A, B, C]
```

`original` giữ nguyên thứ tự trong ví dụ vì stream pipeline chỉ **đọc** nguồn và không gọi `sort` trên chính `original`. `mutable` là một bản sao `ArrayList` có thể sửa và được sắp xếp tại chỗ.

`fromStream` là danh sách kết quả theo comparator nhưng không cho thêm phần tử bằng `add` trong Java 17. Nếu cần tiếp tục sửa, tạo `new ArrayList<>(fromStream)`.

## 18. Lỗi dễ gặp: dùng phép trừ để làm comparator số

Nhiều đoạn code viết:

```java
Comparator<Integer> wrong = (a, b) -> a - b;
```

Dù có vẻ ngắn, nó **không an toàn với integer overflow**. Phép trừ có thể tràn vòng theo số nguyên Java, tạo sai dấu và vi phạm hợp đồng comparator.

Hãy dùng:

```java
Comparator<Integer> safe = Integer::compare;
```

Hoặc khi so khóa của object:

```java
Comparator<Student> byScore =
        Comparator.comparingInt(Student::score);
```

### Ví dụ minh họa overflow

Tạo tệp **`ComparatorOverflowDemo.java`**:

```java
public class ComparatorOverflowDemo {
    public static void main(String[] args) {
        int small = Integer.MIN_VALUE;
        int large = Integer.MAX_VALUE;

        System.out.println(small - large);
        System.out.println(Integer.compare(small, large));
    }
}
```

Biên dịch/chạy:

```bash
javac ComparatorOverflowDemo.java
java ComparatorOverflowDemo
```

Stdout:

```text
1
-1
```

Giá trị `Integer.MIN_VALUE - Integer.MAX_VALUE` tràn số nguyên và cho `1`, trong khi thứ tự đúng là số nhỏ đứng trước số lớn. `Integer.compare` trả một giá trị âm theo hợp đồng.

**Không dùng `a.score() - b.score()` làm comparator** chỉ vì test dữ liệu nhỏ chưa phát hiện tràn. Các factory `comparingInt/Long/Double` giúp tránh loại lỗi này.

## 19. `Comparator` và `TreeSet`/`TreeMap`: so bằng 0 là “cùng khóa thứ tự”

Đối với `List.sort`, hai phần tử có comparator result `0` vẫn có thể cùng xuất hiện trong list. Nhưng các cấu trúc **sorted set/map** dùng comparator để xác định **thứ tự và tính tương đương khóa theo so sánh**.

Ví dụ, một `TreeSet<Person>` chỉ so theo `age` sẽ xem hai người cùng tuổi là **tương đương đối với set** ngay cả khi tên/id khác nhau. Kết quả là một phần tử có thể không được thêm.

Tạo tệp **`TreeSetComparatorDemo.java`**:

```java
import java.util.Comparator;
import java.util.TreeSet;

public class TreeSetComparatorDemo {

    record Person(String name, int age) {
    }

    public static void main(String[] args) {
        TreeSet<Person> byAge = new TreeSet<>(
                Comparator.comparingInt(Person::age));

        byAge.add(new Person("An", 20));
        byAge.add(new Person("Binh", 20));

        System.out.println(byAge.size());

        TreeSet<Person> byAgeThenName = new TreeSet<>(
                Comparator.comparingInt(Person::age)
                        .thenComparing(Person::name));

        byAgeThenName.add(new Person("An", 20));
        byAgeThenName.add(new Person("Binh", 20));

        System.out.println(byAgeThenName.size());
    }
}
```

Chạy:

```bash
javac TreeSetComparatorDemo.java
java TreeSetComparatorDemo
```

Stdout:

```text
1
2
```

Vì sao?

- Comparator chỉ theo tuổi trả `0` cho hai người đều `20`, nên `TreeSet` coi phần tử thứ hai là trùng trong quan hệ thứ tự.
- Comparator nối thêm tên giúp phân biệt `"An"` và `"Binh"`, do đó giữ được hai bản ghi.

### `Comparator` có cần nhất quán với `equals` không?

Một comparator có thể hoạt động dù không nhất quán với `equals`, nhưng dùng nó trong `TreeSet`/`TreeMap` có thể tạo hành vi khác với kỳ vọng của hợp đồng `Set`/`Map` dựa trên `equals`. Với loại dữ liệu có identity nghiệp vụ, hãy cân nhắc thêm **tie-breaker duy nhất** như mã id.

Không nên tự động nối `hashCode()` để phân biệt các object: hash có thể va chạm, không phải khóa định danh bền vững.

## 20. Kết quả so bằng 0 và tie-breakers ổn định

Một comparator sắp xếp theo **điểm**:

```java
Comparator<Student> byScore =
        Comparator.comparingInt(Student::score);
```

xem mọi sinh viên cùng điểm tương đương về thứ tự. Điều này hoàn toàn hợp lệ nếu yêu cầu chỉ sắp xếp theo điểm. Nhưng nếu kết quả phải có thứ tự hoàn toàn xác định độc lập với thứ tự input, cần xác định thêm tiêu chí.

Ví dụ một bản ghi có id duy nhất:

```java
record Employee(long id, String name, int score) {
}

Comparator<Employee> order =
        Comparator.<Employee>comparingInt(Employee::score)
                .reversed()
                .thenComparing(Employee::name)
                .thenComparingLong(Employee::id);
```

Đây là đoạn minh họa cần đặt trong class/method thích hợp và import `Comparator`.

Nếu hai object có điểm và tên giống nhau nhưng id khác nhau, tiêu chí id sẽ phân biệt. Điều này hữu ích với báo cáo, phân trang hoặc dữ liệu từ nhiều nguồn vì ta không muốn kết quả phụ thuộc vào thứ tự ngẫu nhiên của input.

**Nhưng** nếu id cũng có thể trùng, comparator vẫn có thể trả `0`. Muốn thứ tự toàn phần tương thích nghiệp vụ, cần bảo đảm tập key cuối thật sự phân biệt những object cần phân biệt.

## 21. Key extractor có thể là một phép tính, nhưng nên thuần

`Comparator.comparing` không yêu cầu key extractor chỉ trả một field. Nó có thể là một function tính toán:

```java
record Ticket(String category, int basePrice, int fee) {
}

Comparator<Ticket> byTotal =
        Comparator.comparingInt(
                t -> t.basePrice() + t.fee());
```

Đoạn ví dụ trên giả định giá trị đủ nhỏ để phép cộng `int` không tràn. Nếu tiền có thể lớn, dùng `long`, `BigDecimal` hoặc kiểu tiền tệ theo thiết kế ứng dụng.

Những nguyên tắc tốt:

- Key extractor nên **deterministic** với cùng object khi dữ liệu không đổi.
- Tránh biến trạng thái hay side effects trong extractor/comparator.
- Không gọi mạng, đọc thời gian hiện tại hoặc lấy số ngẫu nhiên trong so sánh.
- Nếu tính key rất tốn kém, có thể **tính trước và lưu key** hoặc dùng dạng decorate-sort-undecorate; thuật toán sort có thể gọi comparator nhiều lần.
- Khi trường dùng để so sánh có thể thay đổi, tránh sửa nó trong lúc cấu trúc sorted set/map đang giữ object như một key. Thứ tự nội bộ có thể không còn phù hợp.

Một phép nối tiêu chí chỉ đúng khi từng comparator con thỏa hợp đồng so sánh.

## 22. `Comparator.naturalOrder`, `reverseOrder` và `reversed`

Ba thao tác dễ bị nhầm:

- `Comparator.naturalOrder()`: tạo comparator theo thứ tự tự nhiên của các giá trị `Comparable`.
- `Comparator.reverseOrder()`: tạo comparator theo thứ tự tự nhiên đảo chiều.
- `someComparator.reversed()`: đảo chiều **một comparator đã có**.

Ví dụ:

```java
Comparator<String> normal = Comparator.naturalOrder();
Comparator<String> reverse = Comparator.reverseOrder();

Comparator<String> lengthDesc =
        Comparator.comparingInt(String::length)
                .reversed();
```

Hai comparator đầu so chuỗi theo `String.compareTo`; comparator cuối so theo **độ dài giảm dần** chứ không theo bảng chữ cái.

Một tiêu chí nâng cao: generic signature của `naturalOrder()` yêu cầu `T` hỗ trợ `Comparable<? super T>`. Không thể dùng `naturalOrder()` trực tiếp cho một class không có natural order, chẳng hạn một record tự viết không implement `Comparable`.

Nếu muốn sắp xếp những object như vậy, truyền comparator riêng qua `sorted(comparator)` hoặc `List.sort(comparator)`.

## 23. Phân biệt `Comparator<? super T>` với `Comparator<T>`

Một helper generic nên nhận comparator có thể so được cả đối tượng kiểu `T`:

```java
static <T> void sortInPlace(
        java.util.List<T> values,
        java.util.Comparator<? super T> comparator) {
    values.sort(comparator);
}
```

Đây là method hợp lệ khi đặt trong class.

`Comparator<? super T>` cho phép:

- `Comparator<T>`: comparator trực tiếp của T.
- `Comparator<Object>`: comparator của supertype dùng được để so T nếu thực sự triển khai hợp đồng phù hợp.
- Các comparator của superclass/interface khác khi generic compatibility cho phép.

Cách khai báo linh hoạt này không có nghĩa Java tự coi `Comparator<Object>` là một thứ tự hợp lý về nghiệp vụ. Một comparator chỉ so `toString()` của mọi object chẳng hạn có thể chưa phải thứ tự bạn thực sự cần.

Đừng cast `Comparator<Object>` sang `Comparator<Student>` để lách compiler nếu API đã chấp nhận `? super T`: kiểu wildcard mô tả đúng quan hệ hướng tiêu thụ đối tượng.

## 24. Utility generic: sorted copy cho Collection bất kỳ

Tạo tệp **`GenericSortedCopyDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class GenericSortedCopyDemo {

    static <T> List<T> sortedCopy(
            Collection<? extends T> source,
            Comparator<? super T> order) {

        List<T> copy = new ArrayList<>(source);
        copy.sort(order);
        return copy;
    }

    public static void main(String[] args) {
        List<String> words = sortedCopy(
                List.of("zzz", "a", "bb"),
                Comparator.comparingInt(String::length));

        List<Integer> numbers = sortedCopy(
                List.of(5, 1, 3),
                Comparator.reverseOrder());

        System.out.println(words);
        System.out.println(numbers);
    }
}
```

Biên dịch/chạy:

```bash
javac GenericSortedCopyDemo.java
java GenericSortedCopyDemo
```

Stdout:

```text
[a, bb, zzz]
[5, 3, 1]
```

Giải thích chữ ký:

- `Collection<? extends T> source`: nguồn có thể cung cấp các phần tử phù hợp với T.
- `Comparator<? super T> order`: comparator có thể **nhận** T để so.
- `new ArrayList<>(source)`: tạo list riêng để không sắp xếp tại chỗ collection gốc.
- Trả `List<T>`: caller tiếp tục có kiểu tĩnh rõ ràng, không dùng raw `List`.

Đây là ví dụ nhỏ về sự kết hợp **Generic + Lambda/Method Reference + Comparator + PECS** trong một API chuẩn.

## 25. Sort tiếng Việt và locale: natural order không luôn đúng nghiệp vụ

`String.compareTo` so theo thứ tự được định nghĩa cho `String` trong Java, không phải trình tự chữ cái tiếng Việt mà người sử dụng mong muốn. Nếu dữ liệu là tên người/địa danh tiếng Việt, yêu cầu sắp xếp theo ngôn ngữ thường cần một **collator** phù hợp locale.

Ví dụ minh họa:

```java
java.text.Collator collator = java.text.Collator.getInstance(
        java.util.Locale.forLanguageTag("vi-VN"));

Comparator<String> vietnameseOrder =
        (a, b) -> collator.compare(a, b);
```

Đặt đoạn trên trong một method và import `java.util.Comparator` nếu biên dịch. `Collator.compare` cung cấp một quy tắc so sánh văn bản dựa vào locale. Có thể nối tiêu chí với một tie-breaker:

```java
Comparator<String> order =
        vietnameseOrder.thenComparing(
                Comparator.naturalOrder());
```

Đây là một ví dụ về **comparator của key tùy chỉnh**. Không nên giả định một kết quả sắp xếp Unicode cụ thể cho mọi JDK/locale provider nếu chưa xác định yêu cầu kiểm thử. Trường hợp hệ thống cần thứ tự nghiệp vụ chính xác phải kiểm tra bộ dữ liệu tiếng Việt đại diện, chính sách dấu/hoa-thường và môi trường runtime.

Đối với code đồng thời, không nên chia sẻ và thay đổi cấu hình một đối tượng `Collator` mutable giữa các thread mà không có chiến lược đồng bộ phù hợp.

## 26. Những lỗi phổ biến khi nối nhiều tiêu chí

**Lỗi 1 — Dùng phép trừ `a.score() - b.score()` làm comparator.** Có nguy cơ overflow và sai dấu. Dùng `comparingInt` hoặc `Integer.compare`.

**Lỗi 2 — Đặt `.reversed()` ở cuối dù chỉ muốn đảo tiêu chí chính.** Nó đảo **toàn bộ chuỗi** đã ghép. Muốn điểm giảm/tên tăng, đảo comparator điểm trước khi `thenComparing`.

**Lỗi 3 — Quên rằng `thenComparing` chỉ chạy khi tiêu chí trước trả 0.** Nếu comparator chính luôn trả số khác 0 cho các object cần buộc hòa, tiêu chí phụ sẽ không được xét.

**Lỗi 4 — Cho rằng `comparing(Student::name)` tự hỗ trợ null.** Không. Thêm `nullsFirst`/`nullsLast` ở đúng cấp khóa hoặc object.

**Lỗi 5 — Nhầm `nullsLast` ở khóa với ở bản thân phần tử.** `Comparator.comparing(Person::name, nullsLast(...))` vẫn không chấp nhận một `Person` null; cần bọc comparator bên ngoài.

**Lỗi 6 — Nghĩ `List.sort` tạo list mới.** Nó sửa list nhận lời gọi. Muốn bản sao, tạo `ArrayList` mới hoặc dùng stream rồi thu kết quả.

**Lỗi 7 — Gọi `sort` trên `List.of(...)`.** Những list này không hỗ trợ thay đổi; hãy copy trước khi sort.

**Lỗi 8 — Cho rằng `Stream.sorted` làm thay đổi nguồn.** Nó tạo một stage của stream; nguồn list không bị sort tại chỗ chỉ vì pipeline dùng `sorted`.

**Lỗi 9 — So theo một key rồi dùng `TreeSet` nhưng vẫn mong giữ mọi object khác nhau.** Trong `TreeSet`, `compare == 0` làm các object tương đương theo thứ tự; thêm tie-breakers nếu cần phân biệt.

**Lỗi 10 — Dùng comparator không nhất quán hoặc phụ thuộc trạng thái thay đổi.** Có thể phá hợp đồng transitivity/ordering. Tránh side effects và key mutable không được quản lý.

**Lỗi 11 — Dùng `Comparator.comparing` trên một key không implement `Comparable` mà không cung cấp key comparator.** Cần overload `comparing(extractor, keyComparator)`.

**Lỗi 12 — Nghĩ `Comparator<T>` phải so các field cũng có kiểu T.** Không. Key extractor có thể trả `U` khác, ví dụ `String` hoặc `LocalDate`.

**Lỗi 13 — Dùng raw `Comparator` để xử lý lỗi suy luận kiểu.** Làm mất kiểm tra generic và có thể phát sinh `ClassCastException`; dùng target type, explicit witness hoặc biến trung gian.

**Lỗi 14 — Cho rằng mọi natural string ordering là thứ tự tiếng Việt.** Nếu có yêu cầu locale, dùng `Collator` hoặc một rule cụ thể được thử nghiệm.

**Lỗi 15 — Dựa vào tính stable sort để không khai báo tie-breakers.** Stable sort bảo toàn thứ tự input giữa các phần tử bằng nhau theo comparator, nhưng không tạo một thứ tự toàn phần độc lập với input.

**Lỗi 16 — Không phân biệt `Comparator.reverseOrder()` và `existingComparator.reversed()`.** Dạng đầu là natural order đảo chiều; dạng sau đảo một comparator bất kỳ, kể cả comparator nhiều cấp.

## 27. Bài tập tự kiểm tra có đáp án

### Câu 1 — Phân biệt dấu của `compare`

`compare(a,b)` trả `-7` có hợp lệ không?

**Đáp án:** Có, nếu phù hợp hợp đồng comparator. Giá trị âm cho biết `a` đứng trước `b`; không cần đúng `-1`.

### Câu 2 — Chỉ đảo tiêu chí điểm

Muốn điểm giảm, tên tăng trong `Comparator<Student>`, đặt `.reversed()` ở đâu?

**Đáp án:** Ngay sau comparator điểm, **trước** `.thenComparing(Student::name)`.

### Câu 3 — Đảo cả chuỗi

Nếu có `byScore.thenComparing(byName).reversed()`, thứ tự của tiêu chí tên có đảo không?

**Đáp án:** Có; `.reversed()` đảo comparator hợp thành, nghĩa là đảo toàn bộ trật tự.

### Câu 4 — Null ở key

Một `Person` không null có `name = null`. Nên xử lý thế nào?

**Đáp án:** Truyền `Comparator.nullsFirst(...)` hoặc `.nullsLast(...)` như comparator **của khóa `name`** trong `Comparator.comparing(Person::name, keyComparator)`.

### Câu 5 — Null ở object

Một list chứa phần tử `Person` thực sự `null`. Nên xử lý thế nào?

**Đáp án:** Bao toàn bộ comparator `Person` bằng `Comparator.nullsFirst(personComparator)` hoặc `nullsLast(...)`.

### Câu 6 — Key extractor

`Comparator.comparingInt(Student::score)` dùng kiểu gì làm key?

**Đáp án:** Primitive `int`; không yêu cầu dùng wrapper `Integer` cho chữ ký extractor.

### Câu 7 — `TreeSet`

Hai `Student` khác id nhưng cùng điểm được thêm vào `TreeSet` dùng comparator **chỉ theo điểm**. Set có bảo đảm giữ cả hai không?

**Đáp án:** Không. Comparator có thể trả `0`, khiến `TreeSet` coi chúng là tương đương theo ordering.

### Câu 8 — `List.of`

`List.of(3,1,2).sort(Comparator.naturalOrder())` có sắp xếp được tại chỗ không?

**Đáp án:** Không theo hợp đồng unmodifiable list; nên tạo `new ArrayList<>(List.of(3,1,2))` rồi gọi `.sort(...)`.

### Câu 9 — `Comparator<? super T>`

Vì sao `List<T>.sort` nhận comparator có `? super T`?

**Đáp án:** Comparator là consumer của các giá trị T; comparator dành cho một supertype phù hợp vẫn so sánh được những đối tượng T.

### Câu 10 — Method reference

`Student::name` có loại chức năng gì trong `Comparator.comparing(Student::name)`?

**Đáp án:** Key extractor: nhận `Student`, trả một `String` để comparator so khóa tên.

### Câu 11 — Overflow

Vì sao không dùng `(a,b) -> a-b` trên toàn miền `int`?

**Đáp án:** Phép trừ có thể overflow và cho dấu sai, vi phạm thứ tự. Dùng `Integer.compare`.

### Câu 12 — Sort và stream

`list.stream().sorted(cmp).toList()` có tự thay đổi `list` không?

**Đáp án:** Không. Nó tạo một list kết quả theo thứ tự; `list.sort(cmp)` mới sửa list nhận lời gọi.

### Câu 13 — So theo ngày rồi id

Có `record Event(LocalDate at, long id)`. Cách nối tiêu chí?

```java
Comparator<Event> byDateThenId =
        Comparator.comparing(Event::at)
                .thenComparingLong(Event::id);
```

**Đáp án:** Hợp lệ khi import `Comparator`/`LocalDate` và `at` không null. `LocalDate` có natural order; id là khóa long dự phòng.

### Câu 14 — Giữ tính ổn định

Một ordered stream có hai phần tử được comparator xem là bằng nhau (`compare == 0`). `sorted(cmp)` có bảo đảm ổn định không?

**Đáp án:** Có đối với **ordered streams**. Với unordered streams, không có cam kết tương tự.

## 28. Thực hành tổng hợp: thiết kế comparator sẵn dùng cho nghiệp vụ

Một ứng dụng quản lý sản phẩm muốn:

1. Sản phẩm còn hàng (`inStock`) đứng trước hết hàng.
2. Trong mỗi nhóm trạng thái, giá tăng dần.
3. Nếu giá bằng nhau, tên tăng dần (không phân biệt hoa thường).
4. Nếu vẫn bằng, so tiếp mã sản phẩm để thứ tự ổn định.

Tạo tệp **`BusinessOrderDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BusinessOrderDemo {

    record Product(
            String code,
            String name,
            int price,
            boolean inStock) {
    }

    public static void main(String[] args) {
        List<Product> products = new ArrayList<>(List.of(
                new Product("P3", "Beta", 120, true),
                new Product("P2", "Alpha", 100, false),
                new Product("P4", "alpha", 100, true),
                new Product("P1", "Alpha", 100, true)
        ));

        Comparator<Product> order =
                Comparator.<Product, Boolean>comparing(
                                Product::inStock,
                                Comparator.reverseOrder())
                        .thenComparingInt(Product::price)
                        .thenComparing(
                                Product::name,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Product::code);

        products.sort(order);

        for (Product p : products) {
            System.out.println(
                    p.code() + "|" + p.inStock()
                    + "|" + p.price() + "|" + p.name());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac BusinessOrderDemo.java
java BusinessOrderDemo
```

Stdout:

```text
P1|true|100|Alpha
P4|true|100|alpha
P3|true|120|Beta
P2|false|100|Alpha
```

Giải thích:

- `Product::inStock` trả primitive `boolean`. Trong overload `comparing` với key type `Boolean`, compiler dùng boxing để so khóa; `Comparator.reverseOrder()` đặt `true` trước `false` vì natural order của `Boolean` là `false < true`.
- `thenComparingInt(Product::price)` xếp giá từ thấp lên cao bên trong từng nhóm trạng thái.
- `thenComparing(Product::name, String.CASE_INSENSITIVE_ORDER)` so tên mà không phân biệt chữ hoa/thường.
- Với `"Alpha"` và `"alpha"` cùng giá và cùng trạng thái, comparator tên trả `0`, nên mã `P1` đứng trước `P4`.
- Không cần raw type, unchecked cast hoặc viết một `compare` dài có nhiều nhánh.

Nếu tên sản phẩm cho phép `null`, phải thay comparator tên bằng `Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)` hoặc chính sách khác trước khi dùng trên dữ liệu thực.

## 29. Quy trình thực tế khi xây chuỗi comparator

Khi nhận một yêu cầu sắp xếp nhiều khóa:

1. **Ghi rõ thứ tự ưu tiên** của từng tiêu chí. Ví dụ: trạng thái → điểm → tên → id.
2. **Ghi chiều tăng/giảm và chính sách null** cho từng tiêu chí.
3. **Chọn key extractor phù hợp:** `comparingInt` cho `int`, `comparingLong` cho `long`, `comparing` cho khóa đối tượng có natural order.
4. **Dùng key comparator riêng** nếu cần thứ tự tùy chỉnh, case-insensitive, locale hoặc null placement.
5. **Đặt `.reversed()` ở phạm vi chính xác**, hoặc xây comparator tiêu chí riêng khi có nhiều chiều sắp xếp.
6. **Nối `thenComparing` theo đúng mức ưu tiên**. Các tiêu chí sau chỉ được xét khi trước đó hòa.
7. **Thêm tie-breaker đủ mạnh** nếu API cần thứ tự kết quả xác định hoặc dùng trong `TreeSet`/`TreeMap`.
8. **Kiểm tra contract** với các giá trị biên: `Integer.MIN_VALUE`, `Integer.MAX_VALUE`, `null`, khóa trùng, chữ hoa/thường và các khóa mutable.
9. **Chọn API sử dụng:** `List.sort` khi muốn sửa list, `Stream.sorted` khi muốn kết quả mới, `TreeSet`/`TreeMap` khi ordering là cấu trúc dữ liệu.
10. **Viết test có kết quả cụ thể**, không chỉ thử một danh sách đã có thứ tự sẵn.
11. **Giữ comparator thuần**, tránh side effects; nếu extract key tốn kém, cân nhắc caching/precomputation.
12. **Giữ kiểu generic đầy đủ** và dùng target typing/explicit type witness nếu compiler không suy luận được method chain.

Một chuỗi comparator đúng không chỉ sắp xếp được danh sách demo, mà còn giữ đúng hành vi khi dữ liệu nhiều, khóa trùng và biên giá trị xuất hiện.

## 30. Tổng kết và nguồn tra cứu

**`Comparator<T>` là một functional interface generic định nghĩa cách so hai đối tượng cùng kiểu T.** Nó có thể được viết bằng lambda, method reference hoặc các factory như `comparing`, `comparingInt`, `comparingLong`, `comparingDouble`. Khi cần nhiều tiêu chí, nối chúng bằng `thenComparing` và các overload theo key extractor.

Ba điểm đặc biệt quan trọng:

- **Thứ tự ưu tiên** được xác định bằng thứ tự nối comparator. Một key phụ chỉ dùng khi các key trước trả `0`.
- **Chiều sắp xếp** phụ thuộc vị trí `.reversed()`: gọi ở cuối sẽ đảo toàn bộ comparator, còn gọi ngay sau comparator chính chỉ đảo phần chính trước khi nối.
- **An toàn kiểu và đúng hợp đồng** phụ thuộc generic signatures, null handling, tránh overflow và bảo đảm comparator hợp lệ. Dùng `Comparator<? super T>` trong API generic phù hợp và nhớ `TreeSet`/`TreeMap` dựa vào `compare == 0` để xác định khóa tương đương.

Đối với Java 17+, cách diễn đạt tiêu chí thường rõ ràng hơn khi tách từng khóa, dùng primitive comparators khi phù hợp, đặt tên các comparators phức tạp và viết test có tie-breakers. Không cần lạm dụng raw types hoặc unchecked casts.

Tài liệu chuẩn:

- [Java SE 17 — `java.util.Comparator`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html): `compare`, `comparing`, `thenComparing`, `reversed`, `nullsFirst/nullsLast`.
- [Java SE 17 — `java.util.List.sort`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator)): sắp xếp list tại chỗ và hợp đồng stability.
- [Java SE 17 — `java.util.stream.Stream.sorted`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#sorted(java.util.Comparator)): sắp xếp stream với comparator.
- [Java SE 17 — `java.util.TreeSet`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html): comparator quyết định ordering và equivalence của phần tử trong sorted set.
- [Java Language Specification §15.27 — Lambda Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.27): target typing của lambda.
- [Java Language Specification §15.13 — Method Reference Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.13): method references trong functional interfaces.
- [Java Language Specification §18 — Type Inference](https://docs.oracle.com/javase/specs/jls/se17/html/jls-18.html): suy luận generic type arguments.
- [Java Language Specification §4.5 — Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5): kiểu tham số hóa và quan hệ type arguments.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-standard-ap-is-004">Suy luận kiểu của lambda và tham chiếu phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Suy luận kiểu của lambda và tham chiếu phương thức trong Java Generics

## Mục tiêu học tập

Lambda expression và method reference giúp truyền hành vi vào một phương thức mà không cần khai báo lớp ẩn danh dài dòng. Nhưng khi kết hợp với Java Generics, kiểu của một lambda không thể được hiểu chỉ bằng cách nhìn vào biểu thức `s -> s.length()`. Compiler cần một **kiểu đích** (*target type*), thường là một *functional interface*, để xác định kiểu tham số, kiểu kết quả, overload phù hợp và những ràng buộc liên quan.

Sau bài này, bạn có thể:

- Giải thích **target typing**, **functional interface**, phương thức trừu tượng duy nhất (*SAM — single abstract method*) và **poly expression**.
- Phân biệt kiểu được suy luận cho tham số lambda với kiểu generic được suy luận khi gọi phương thức nhận lambda.
- Sử dụng `Function<T,R>`, `Predicate<T>`, `Supplier<T>`, `Consumer<T>`, `Comparator<T>`, `UnaryOperator<T>` và `BinaryOperator<T>` đúng ngữ cảnh.
- Viết method reference kiểu static, instance có receiver đã biết, instance với receiver là tham số đầu và constructor reference.
- Hiểu vì sao cùng một method reference có thể trỏ tới overload khác nhau khi target type khác nhau.
- Xử lý lỗi `lambda expression needs an explicit target-type`, `reference to ... is ambiguous`, suy luận kiểu không tương thích và việc sử dụng `var` trong lambda.
- Đọc và kiểm chứng các ví dụ Java 17+ bằng `javac`/`java`, không đoán đầu ra.

## 1. Nền tảng: functional interface quyết định hợp đồng của lambda

Một lambda cần **functional interface làm target**. Chẳng hạn:

```java
@FunctionalInterface
interface Converter<S, R> {
    R convert(S input);
}
```

Với `Converter<String,Integer>`, compiler biết tham số lambda là `String` và giá trị trả về phải tương thích `Integer`:

```java
Converter<String, Integer> length = text -> text.length();
Integer count = length.convert("Java"); // 4
```

`String.length()` trả `int`, được boxing sang `Integer`. Nếu target là `Converter<Integer,Integer>`, thân `text.length()` sẽ không biên dịch vì `Integer` không có method đó.

**Trọng tâm của bài này là target typing/inference**: compiler suy ra kiểu lambda từ **nơi biểu thức được dùng**, không phải bằng cách đoán một kiểu lambda độc lập. Những interface chuẩn `Function`, `Predicate`, `Supplier`, `Consumer`, `Comparator`, `Callable` là các nguồn target khác nhau; chúng có SAM với số tham số/return/checked exceptions riêng. Functional interface vẫn có thể có method default/static, và annotation `@FunctionalInterface` giúp compiler kiểm tra tính hợp lệ của declaration.

Ở phần sau, ta phân tích suy luận kiểu khi lambda/method reference tham gia **generic method calls** và cách giải overload không rõ ràng; đó là năng lực riêng vượt ngoài việc thuộc bảng các interface chuẩn.

## 2. Target typing: từ nơi dùng quay ngược lại biểu thức

Java coi lambda và method reference là các **poly expression** trong những ngữ cảnh thích hợp: kiểu của chúng phụ thuộc vào vị trí sử dụng.

Ví dụ:

```java
Function<String, Integer> length = s -> s.length();
```

Compiler biết:

1. Biểu thức nằm ở vế phải của phép gán vào `Function<String,Integer>`.
2. SAM `Function.apply` nhận String và trả Integer.
3. Vì vậy `s` được suy luận là `String`.
4. `s.length()` trả `int`, có thể boxing thành `Integer`.
5. Lambda hợp lệ theo target type.

Cùng biểu thức nhưng kiểu đích không tương thích:

```java
// KHÔNG BIÊN DỊCH:
// Function<Integer, Integer> length = s -> s.length();
```

Lý do là `s` lúc này có kiểu Integer, không có method `length()`.

**Target type thường đến từ:** khai báo và phép gán, tham số của lời gọi method, return statement bên trong method có kiểu trả về functional interface, cast sang functional interface, biểu thức điều kiện trong ngữ cảnh có đích và các tình huống khác được JLS quy định.

Ví dụ target từ lời gọi method:

```java
List<Integer> lengths =
        List.of("A", "Java").stream()
            .map(String::length)
            .toList();
```

Compiler dùng chữ ký generic của `Stream.map` và bối cảnh gọi để suy ra method reference `String::length` chuyển String thành Integer (kết quả `int` được boxing). Java 17 hỗ trợ `Stream.toList()`; kết quả không sửa được theo hợp đồng của API này.

**Không có target type, lambda và method reference không thể tự mang một kiểu độc lập như một object thông thường.**

## 3. Tại sao `var fn = s -> s.length()` không hợp lệ?

Từ Java 10, `var` cho phép compiler suy luận kiểu của biến cục bộ từ biểu thức khởi tạo:

```java
var number = 42;              // int
var text = "hello";           // String
var names = List.of("A");     // List<String>
```

Nhưng một lambda không cung cấp target functional interface khi khởi tạo biến bằng `var`:

**Tệp `UntypedLambda.java` — cố ý không biên dịch:**

```java
class UntypedLambda {
    void test() {
        var operation = s -> s.length();
    }
}
```

Lệnh:

```bash
javac --release 17 UntypedLambda.java
```

Thông báo trọng tâm đã kiểm chứng:

```text
cannot infer type for local variable operation
(lambda expression needs an explicit target-type)
```

Tương tự method reference:

**Tệp `UntypedMethodReference.java` — cố ý không biên dịch:**

```java
class UntypedMethodReference {
    void test() {
        var operation = String::trim;
    }
}
```

Lệnh biên dịch cho lỗi:

```text
cannot infer type for local variable operation
(method reference needs an explicit target-type)
```

Cách sửa: cung cấp functional interface ở phía khai báo hoặc cast rõ ràng.

```java
Function<String, Integer> length = s -> s.length();
Function<String, String> trim = String::trim;
```

Nếu rất muốn dùng `var` cho biến lưu lambda, phải tạo target type ở biểu thức khởi tạo bằng một cast hay một lời gọi method có kiểu rõ:

```java
var operation =
        (java.util.function.Function<String, Integer>) (s -> s.length());
```

Cách này dài hơn và thường kém dễ đọc so với khai báo `Function<String,Integer>` trực tiếp. `var` hữu ích ở nhiều chỗ khác, nhưng **không giải quyết chuyện thiếu target type cho lambda/method reference**.

## 4. `var` trong danh sách tham số lambda là chuyện khác

Java 11 cho phép viết `var` ở **tham số** của lambda:

```java
Function<String, Integer> a = (var s) -> s.length();
Function<String, Integer> b = (s) -> s.length();
Function<String, Integer> c = s -> s.length();
```

Cả ba biểu thức đều nhận String và trả Integer vì target type là `Function<String,Integer>`. Việc viết `var s` trong lambda **không** làm biến lambda thành không cần target.

Một ứng dụng của `var` trên tham số lambda là cho phép annotation trên tham số khi cần:

```java
// Dạng minh họa cú pháp nếu @MyAnnotation phù hợp với PARAMETER:
Function<String, Integer> f =
        (@MyAnnotation var s) -> s.length();
```

Không thể chép riêng ví dụ annotation này để biên dịch nếu chưa định nghĩa `MyAnnotation`. Đây chỉ là minh họa hình thức.

### Không trộn `var` và tham số implicit

**Tệp `MixedVarLambdaParameters.java` — cố ý không biên dịch:**

```java
import java.util.function.BiFunction;

class MixedVarLambdaParameters {
    void test() {
        BiFunction<String, String, String> f =
                (var a, b) -> a + b;
    }
}
```

`javac --release 17` báo:

```text
invalid lambda parameter declaration
(cannot mix 'var' and implicitly-typed parameters)
```

Cách sửa:

```java
BiFunction<String, String, String> f =
        (var a, var b) -> a + b;
```

Hoặc:

```java
BiFunction<String, String, String> f =
        (a, b) -> a + b;
```

Cả hai hợp lệ khi được đặt trong method với import `BiFunction`. Quy tắc thống nhất cách khai báo tham số trong cùng một lambda: không trộn khai báo có `var` với tham số implicit hoặc explicit kiểu khác một cách tùy tiện.

## 5. Suy luận kiểu generic khi lambda là đối số method

Phần quan trọng hơn là suy luận kiểu không chỉ xảy ra bên trong lambda. Xét API generic:

```java
static <T, R> List<R> transform(
        List<? extends T> source,
        Function<? super T, ? extends R> mapper) {
    List<R> result = new ArrayList<>();
    for (T item : source) {
        result.add(mapper.apply(item));
    }
    return result;
}
```

Đây là hàm xuất hiện trong chương trình hoàn chỉnh phần 7 và cần import `List`, `ArrayList`, `Function`.

Lời gọi:

```java
List<Integer> sizes =
        transform(List.of("A", "Java"), String::length);
```

Compiler phối hợp nhiều ràng buộc:

1. `source` có các phần tử String, nên T phù hợp với String theo quan hệ subtype/wildcard.
2. `String::length` là method reference cần target `Function<? super T,? extends R>`.
3. Phương thức `String.length()` trả `int`, có thể boxing thành Integer.
4. Biểu thức kết quả được gán cho `List<Integer>`, thêm ngữ cảnh để suy luận R.
5. Do đó chương trình có một danh sách Integer với các giá trị `1` và `4`.

Đây là ví dụ về **inference kết hợp**: suy luận type argument của method generic và kiểm tra lambda/reference tương tác qua các *constraint* của compiler. Bạn không cần viết tường minh toàn bộ type argument trong lời gọi thông thường.

Nếu cần thể hiện ý định rõ ràng hoặc compiler không suy luận như mong muốn, có thể viết type arguments:

```java
List<Integer> sizes =
        LambdaInferenceDemo.<String, Integer>transform(
                List.of("A", "Java"), String::length);
```

Đoạn này nằm trong ngữ cảnh class `LambdaInferenceDemo` ở phần 7. Việc chỉ rõ type argument nên dùng khi thực sự cải thiện tính đúng đắn/khả năng đọc; không phải cách bắt buộc cho mọi generic method.

### Vì sao dùng `? super T` ở mapper?

`Function<? super T, ? extends R>` linh hoạt hơn `Function<T,R>` theo nhu cầu producer/consumer:

- Mapper **tiêu thụ** một đối tượng T, nên có thể chấp nhận một supertype của T.
- Mapper **sản xuất** kết quả tương thích với R, nên có thể trả một subtype của R.

Đây là ứng dụng thực tế của **PECS (Producer Extends, Consumer Super)**. Nó giúp API nhận nhiều dạng Function mà vẫn giữ quan hệ kiểu an toàn.

Ví dụ về chuỗi:

```java
List<String> upper = transform(
        List.of(" a ", " b "),
        LambdaInferenceDemo::normalize);
```

`normalize(String)` trả String, và kiểu R được suy luận phù hợp với `List<String>`. Mã chạy ở phần 7 in `upper=[A, B]`.

### Suy luận kết quả lambda theo target

Một method generic khác:

```java
static <T> T choose(T first, T second, BinaryOperator<T> chooser) {
    return chooser.apply(first, second);
}
```

Gọi:

```java
String longest = choose("AB", "C",
        (a, b) -> a.length() >= b.length() ? a : b);
```

Compiler xác định a và b là String qua T, rồi kiểm tra biểu thức điều kiện trả String, tương thích `BinaryOperator<String>`. Không cần khai báo `(String a, String b)` nếu target đã rõ.

## 6. Các loại method reference và cách suy luận

Method reference không gọi phương thức ngay lập tức. Nó tạo một biểu thức chỉ có nghĩa cụ thể khi compiler biết functional interface đích.

| Dạng | Ví dụ | Ý nghĩa trong target phù hợp |
| --- | --- | --- |
| Static method | `Integer::parseInt` | Nhận String, trả int/Integer theo target. |
| Unbound instance method | `String::length` | Receiver là đối số đầu; nhận String rồi gọi `length()`. |
| Bound instance method | `"An"::equals` | Receiver `"An"` có sẵn; nhận một đối số để so sánh. |
| Bound instance method khác | `"pre-"::concat` | Nhận suffix rồi nối vào receiver cố định. |
| Constructor | `ArrayList::new` | Tạo ArrayList theo signature constructor tương thích. |
| Constructor với đối số | `Member::new` | Nhận String rồi tạo Member. |
| Generic static method | `LambdaInferenceDemo::<String>identity` | Chọn explicit type argument cho method generic. |

### 6.1. Static method reference

`Integer::parseInt` là một method reference tới các overload static. Khi gán:

```java
Function<String, Integer> parse = Integer::parseInt;
```

target có một input String và result Integer, nên compiler chọn method tương thích (ví dụ `parseInt(String)` trả int và kết quả được boxing).

Một reference tới method overloaded như `String::valueOf` cũng cần target để chọn cách gọi:

```java
Function<Integer, String> toStringValue = String::valueOf;
String text = toStringValue.apply(9);
```

`String` có nhiều overload `valueOf`. Target type đóng vai trò ràng buộc số lượng và kiểu tham số. Không nên khẳng định rằng mọi method reference overloaded luôn chọn duy nhất một overload chỉ từ tên method; compiler xét cả chữ ký và ngữ cảnh, và đôi khi báo ambiguous.

### 6.2. Unbound instance method

`String::trim` trong:

```java
Function<String, String> trim = String::trim;
```

có nghĩa là nhận một String làm **receiver** rồi gọi `receiver.trim()`. Đây là method reference loại `ReferenceType::instanceMethod`. Với `String::length`, `Function<String,Integer>` cũng hoạt động dù method `length()` không có tham số tường minh; String đầu vào chính là receiver.

Cũng vì vậy:

```java
Function<Member, String> readName = Member::name;
```

dùng method truy cập record `Member.name()`; đối số duy nhất là instance Member.

### 6.3. Bound instance method

Với:

```java
Predicate<String> isAn = "An"::equals;
Function<String, String> addPrefix = "pre-"::concat;
```

receiver đã được xác định bằng một biểu thức đứng trước dấu `::`. Functional interface chỉ cần cung cấp những đối số còn lại. `isAn.test("An")` trả true; `addPrefix.apply("fix")` trả `"pre-fix"`.

Đừng nhầm hai dạng:

- `String::trim`: receiver được truyền vào như tham số của Function.
- `someString::trim`: receiver đã có; phù hợp với target không tham số như `Supplier<String>`.

Một lưu ý nâng cao: biểu thức receiver ở một bound method reference được đánh giá khi tạo reference, nên nếu việc lấy receiver có side effect thì thời điểm diễn ra side effect có thể khác với lambda trì hoãn việc tính receiver tới lúc gọi. Vì vậy không phải mọi method reference đều có thể thay thế máy móc bằng lambda mà không xét ngữ nghĩa.

### 6.4. Constructor reference

`ArrayList::new` cũng là một poly expression cần target:

```java
Supplier<ArrayList<String>> listFactory = ArrayList::new;
ArrayList<String> result = listFactory.get();
```

Target `Supplier` yêu cầu constructor không đối số. Với `Function<Integer,ArrayList<String>>`, compiler có thể lựa chọn constructor nhận dung lượng ban đầu tùy chữ ký phù hợp; cả hai cùng viết `ArrayList::new` nhưng ngữ cảnh khác nhau.

Trong chương trình phần 7, `Member::new` là constructor reference tạo record Member từ String:

```java
Function<String, Member> memberFactory = Member::new;
Member person = memberFactory.apply("An");
```

Method reference không phải một phép “ép class thành Function” ở runtime; compiler dựng implementation tương thích với target functional interface.

### 6.5. Generic method reference và explicit type argument

Một phương thức generic:

```java
static <T> T identity(T value) {
    return value;
}
```

có thể được dùng:

```java
Function<String,String> id = LambdaInferenceDemo::identity;
```

hoặc chỉ rõ type argument:

```java
Function<String,String> id =
        LambdaInferenceDemo::<String>identity;
```

Cả hai nằm trong ngữ cảnh lớp `LambdaInferenceDemo`. Với phương thức không quá phức tạp, compiler suy luận T từ target. Explicit type argument dùng khi muốn khóa T thành kiểu cụ thể, khi suy luận mơ hồ hoặc để làm rõ ý định.

## 7. Chương trình Java 17 hoàn chỉnh

**Tệp `LambdaInferenceDemo.java`:**

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class LambdaInferenceDemo {
    record Member(String name) {}

    static <T, R> List<R> transform(
            List<? extends T> source,
            Function<? super T, ? extends R> mapper) {
        List<R> result = new ArrayList<>();
        for (T item : source) {
            result.add(mapper.apply(item));
        }
        return result;
    }

    static <T> T choose(T first, T second, BinaryOperator<T> chooser) {
        return chooser.apply(first, second);
    }

    static <T> T identity(T value) {
        return value;
    }

    static String normalize(String s) {
        return s.trim().toUpperCase(Locale.ROOT);
    }

    static String format(Object o) {
        return "Object:" + o;
    }

    static String format(String s) {
        return "String:" + s;
    }

    static void dispatch(Supplier<String> value) {
        System.out.println("dispatch=" + value.get());
    }

    static void dispatch(Callable<String> value) throws Exception {
        System.out.println("callable=" + value.call());
    }

    public static void main(String[] args) {
        Function<String, Integer> length = s -> s.length();
        Function<String, Integer> lengthVar = (var s) -> s.length();
        Function<String, Integer> lengthRef = String::length;
        System.out.println("lengths="
                + length.apply("Java") + ","
                + lengthVar.apply("Hi") + ","
                + lengthRef.apply("A"));

        List<Integer> sizes = transform(List.of("A", "Java"), String::length);
        System.out.println("sizes=" + sizes);

        List<String> upper = transform(
                List.of(" a ", " b "), LambdaInferenceDemo::normalize);
        System.out.println("upper=" + upper);

        Function<String, Integer> parse = Integer::parseInt;
        Function<Integer, String> stringify = String::valueOf;
        System.out.println("parse=" + parse.apply("42")
                + ",valueOf=" + stringify.apply(9));

        Predicate<String> isAn = "An"::equals;
        Function<String, String> trim = String::trim;
        Function<String, String> prefix = "pre-"::concat;
        System.out.println("refs=" + isAn.test("An") + ","
                + trim.apply(" x ") + "," + prefix.apply("fix"));

        Supplier<ArrayList<String>> listFactory = ArrayList::new;
        Function<String, Member> memberFactory = Member::new;
        Function<Member, String> nameOf = Member::name;
        List<Member> members = listFactory.get().isEmpty()
                ? List.of(memberFactory.apply("Binh"), memberFactory.apply("An"))
                : List.of();
        List<Member> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparing(Member::name));
        System.out.println("sorted=" + transform(sorted, nameOf));

        Function<String, String> selectOverload = LambdaInferenceDemo::format;
        System.out.println("overload=" + selectOverload.apply("x"));

        String longest = choose("AB", "C", (a, b) ->
                a.length() >= b.length() ? a : b);
        System.out.println("chosen=" + longest);

        System.out.println("identity="
                + transform(List.of("ok"),
                        LambdaInferenceDemo::<String>identity));

        dispatch((Supplier<String>) () -> "supplier");
    }
}
```

Biên dịch và chạy trên JDK 17 trở lên:

```bash
javac --release 17 -Xlint:all LambdaInferenceDemo.java
java LambdaInferenceDemo
```

**Stdout đã được kiểm chứng:**

```text
lengths=4,2,1
sizes=[1, 4]
upper=[A, B]
parse=42,valueOf=9
refs=true,x,pre-fix
sorted=[An, Binh]
overload=String:x
chosen=AB
identity=[ok]
dispatch=supplier
```

`javac --release 17 -Xlint:all` đã biên dịch chương trình không phát sinh warning trong kiểm thử.

### 7.1. Lambda với tham số ngầm và `var`

Ba biến `length`, `lengthVar`, `lengthRef` cùng thỏa `Function<String,Integer>` nhưng được diễn đạt bằng lambda ngầm, lambda có `var` và method reference. Dòng kết quả `lengths=4,2,1` giúp đối chiếu ba cách gọi.

### 7.2. Inference giữa `transform` và mapper

`sizes` chứa Integer từ `String::length`. `upper` chứa String từ `normalize`. Đây là trường hợp compiler kết hợp generics của method với target type của method reference.

### 7.3. Static/bound/unbound references

`Integer::parseInt`, `String::valueOf` là static; `String::trim` là unbound instance; `"An"::equals` và `"pre-"::concat` là bound instance. Kết quả `refs=true,x,pre-fix` cho thấy chúng hoạt động theo kiểu đầu vào/đầu ra target.

### 7.4. Constructor reference và comparator

`ArrayList::new` cung cấp list mới. `Member::new` tạo record. `Member::name` đọc tên. `Comparator.comparing(Member::name)` cần suy luận kiểu key và so sánh chuỗi; chương trình sắp tên thành `[An, Binh]`.

### 7.5. Overloaded method reference được target xác định

Trong lớp có cả `format(Object)` và `format(String)`. Khi gán `LambdaInferenceDemo::format` cho `Function<String,String>`, compiler chọn overload cụ thể tương thích, và ví dụ đã chạy cho `String:x`. Method reference chỉ có tên giống nhau nhưng không hoàn toàn thiếu thông tin về overload: **target signature** hướng dẫn lựa chọn.

### 7.6. Generic method reference explicit type argument

`LambdaInferenceDemo::<String>identity` cho biết rõ phương thức generic `identity` được sử dụng với T=String. Lời gọi `transform` nhận method reference này và in `identity=[ok]`.

## 8. Overload nhận lambda: tại sao dễ mơ hồ?

Một lambda có thể phù hợp nhiều functional interface không có quan hệ subtype. Ví dụ:

**Tệp `AmbiguousLambdaOverload.java` — cố ý không biên dịch:**

```java
import java.util.function.Supplier;
import java.util.concurrent.Callable;

class AmbiguousLambdaOverload {
    static void choose(Supplier<String> callback) {}
    static void choose(Callable<String> callback) {}

    static void test() {
        choose(() -> "ok");
    }
}
```

`Supplier<String>` và `Callable<String>` đều nhận 0 tham số và trả String. Lambda `() -> "ok"` phù hợp về hình dạng và result cho cả hai, compiler không thể chọn một overload duy nhất.

Lệnh:

```bash
javac --release 17 AmbiguousLambdaOverload.java
```

Lỗi chính đã kiểm chứng:

```text
reference to choose is ambiguous
both method choose(Supplier<String>) and method choose(Callable<String>) match
```

### Cách giải quyết 1: biến có kiểu tường minh

```java
Supplier<String> message = () -> "ok";
choose(message);
```

Trong ngữ cảnh lớp `AmbiguousLambdaOverload`, kiểu `message` làm compiler chọn overload nhận Supplier.

### Cách giải quyết 2: cast vào functional interface đích

```java
choose((Supplier<String>) () -> "ok");
```

Đây là cách chương trình chạy phần 7 phân giải overload cho `dispatch`. Dùng cast cục bộ có thể hợp lý nếu API bắt buộc giữ hai overload, nhưng cũng có thể làm lời gọi khó đọc.

### Cách giải quyết 3: thiết kế lại tên hoặc chữ ký API

Nếu hai overload làm các nghiệp vụ khác nhau, đặt tên `chooseSupplier` và `chooseCallable` rõ hơn. Đặc biệt không nên tạo overload chỉ khác `Function<String,Integer>` và `Function<String,String>`: hai kiểu này có **cùng erasure `Function`**, nên tạo `name clash` ở cấp khai báo trước cả bước giải quyết lambda.

**Lưu ý:** không phải mọi overload có tham số functional interface đều mơ hồ. JLS có quy tắc xác định phương thức cụ thể hơn dựa trên tham số và dạng lambda/method reference. Chỉ kết luận “ambiguous” khi compiler thực tế không thể chọn duy nhất một ứng viên theo quy tắc overload resolution. Ví dụ Supplier/Callable ở trên là trường hợp đã được biên dịch và xác nhận lỗi.

## 9. Những ví dụ không biên dịch: hiểu đúng nguyên nhân

### 9.1. Sai kiểu tham số explicit trong lambda

**Tệp `WrongLambdaParam.java`:**

```java
import java.util.function.Function;

class WrongLambdaParam {
    Function<String, Integer> f = (Integer x) -> x + 1;
}
```

Lỗi:

```text
incompatible types: incompatible parameter types in lambda expression
```

Target `Function<String,Integer>` yêu cầu tham số String, nhưng lambda lại khai báo tường minh Integer. Chỉ sửa thân lambda không đủ: phải làm cho chữ ký tham số thực sự tương thích với target.

### 9.2. Không có target type cho method reference

`var operation = String::trim;` bị từ chối như phần 3. Sửa thành `Function<String,String> operation = String::trim;`.

### 9.3. Không có target type cho lambda

`var operation = s -> s.length();` bị từ chối. Sửa bằng functional interface đích có kiểu phù hợp.

### 9.4. Trộn `var` và implicit parameters

`(var a, b) -> a + b` không hợp lệ. Viết cả hai tham số bằng `var` hoặc không dùng `var` cho cả hai.

### 9.5. Mơ hồ do overload

`choose(() -> "ok")` khi có cả `choose(Supplier<String>)` và `choose(Callable<String>)` bị ambiguous. Dùng biến typed, cast hoặc thiết kế API tách tên.

Những tệp lỗi ở các phần trên phải biên dịch **riêng**, không đặt vào tệp `LambdaInferenceDemo.java` chạy thành công rồi mong cả project biên dịch bình thường.

## 10. Những giới hạn khác của inference cần nhớ

### 10.1. Biểu thức và block lambda có quy tắc kết quả

Với `Function<String,Integer>`, lambda phải cung cấp một giá trị tương thích Integer trên mọi đường hoàn tất bình thường theo hợp đồng:

```java
Function<String, Integer> size = text -> {
    return text.length();
};
```

Với `Consumer<String>`, method chức năng trả void:

```java
Consumer<String> printer = text -> System.out.println(text);
```

`System.out.println(text)` là method invocation phù hợp với void-compatible expression lambda. Khi dùng block body, cần phân biệt khối thực hiện tác vụ không trả giá trị và khối trả giá trị đúng yêu cầu. Không phải biểu thức nào có thể dùng trong body Function cũng có thể dùng giống hệt trong Runnable/Consumer.

### 10.2. Biến bên ngoài lambda phải final hoặc effectively final

Một lambda có thể đọc biến cục bộ từ phạm vi ngoài khi biến ấy không bị gán lại sau khi khởi tạo:

```java
String prefix = "ID-";
Function<String, String> addPrefix = value -> prefix + value;
System.out.println(addPrefix.apply("7")); // ID-7
```

Nếu sau đó gán `prefix = "NEW-";` trong cùng phạm vi, compiler không cho capture biến như thế nữa. Đây là giới hạn của biến cục bộ bị capture; không phải “type inference đã sai” hay “generic không hỗ trợ thay đổi”.

Đối với field của object, quy tắc khác với capture local variable, nhưng thay đổi trạng thái chia sẻ cần thiết kế và đồng bộ phù hợp.

### 10.3. Checked exceptions phải tương thích SAM

`Function.apply` không khai báo checked exception. Một lambda cho Function không thể tự do ném `IOException` chưa bắt và chưa khai báo đúng. `Callable.call() throws Exception` lại cho phép checked exception theo hợp đồng.

Không nên giải quyết bằng cách ném unchecked exception tùy tiện nếu có thể thiết kế interface đúng nhu cầu hoặc xử lý checked exception tại boundary.

### 10.4. Overload resolution là quá trình liên quan tới target typing

Khi một lambda được truyền làm argument của lời gọi overloaded, compiler phải xem xét các method ứng viên, kiểu tham số của chúng và tính phù hợp của lambda. Vì thế quá trình không đơn giản là “tính xong kiểu lambda trước rồi mới chọn method” hay “chọn method bằng tên trước rồi mới xem lambda”.

Trong thực tế, khi một lời gọi trở nên khó suy luận, khai báo một biến functional interface typed hoặc tách một generic call thành hai câu lệnh thường cải thiện cả thông báo compiler lẫn tính dễ đọc.

### 10.5. Primitive specialization có thể giúp tránh boxing

`Function<String,Integer>` trả Integer, nhưng `String.length()` trả int. Compiler có thể boxing int thành Integer. Nếu đường xử lý cường độ cao cần tối ưu hóa, có thể dùng `ToIntFunction<String>`:

```java
java.util.function.ToIntFunction<String> length = String::length;
int n = length.applyAsInt("Java");
```

Tương tự có `IntUnaryOperator`, `IntPredicate` và các interface chuyên biệt. Trước khi tối ưu hóa, kiểm tra yêu cầu hiệu năng thực tế; trong phần lớn code nghiệp vụ, dạng rõ ràng nhất là ưu tiên.

## 11. Checklist khi debug lỗi inference

Nếu compiler báo khó suy luận kiểu cho lambda hay method reference:

1. **Tìm target type.** Biến đích, kiểu tham số method, return type hay cast có cung cấp một functional interface không?
2. **Kiểm tra SAM.** Có đúng một phương thức chức năng với arity, parameter types, result và throws phù hợp không?
3. **Kiểm tra kiểu tham số lambda.** Nếu implicit, compiler đang suy luận gì từ SAM? Nếu explicit, khai báo có khớp SAM không?
4. **Tìm generic constraints.** Method nhận `Function<? super T,? extends R>` có những ràng buộc nào từ source, mapper và target kết quả?
5. **Xác định overload candidates.** Có nhiều SAM cùng hình dạng như Supplier/Callable không? Có method overloaded trùng tên trong method reference không?
6. **Thử biến trung gian typed.** Gán lambda/reference cho `Function<String,Integer>` hoặc `Supplier<String>` rồi truyền biến để giảm mơ hồ.
7. **Kiểm tra `var`.** Đừng dùng `var x = lambda` khi chưa có target. `(var x) -> ...` vẫn cần functional interface target.
8. **Đọc kỹ thông điệp và vị trí lỗi.** `cannot infer`, `incompatible parameter types`, `ambiguous reference` là các vấn đề khác nhau.
9. **Xem boxing và checked exceptions.** Kết quả int/Integer hoặc exception khai báo trong SAM có ảnh hưởng khả năng tương thích.
10. **Đơn giản hóa API trước khi cast hàng loạt.** Nếu người gọi phải dùng nhiều cast để chọn overload, cân nhắc đổi tên/thiết kế API.

## 12. Bảng tổng hợp: lambda hay method reference?

| Nhu cầu | Cách viết | Điều kiện cần hiểu |
| --- | --- | --- |
| Chuyển String thành độ dài | `s -> s.length()` | Cần target như Function<String,Integer>. |
| Viết gọn phép gọi receiver | `String::length` | Unbound instance; String làm đối số đầu. |
| Trim một chuỗi được truyền vào | `String::trim` | Target Function<String,String>. |
| So sánh với một giá trị cố định | `"An"::equals` | Bound receiver; target Predicate<String> chẳng hạn. |
| Parse số | `Integer::parseInt` | Static overloaded; target quyết định signature phù hợp. |
| Tạo danh sách mới | `ArrayList::new` | Constructor target Supplier hoặc Function tương ứng. |
| Tạo Member từ tên | `Member::new` | Constructor signature khớp String→Member. |
| Đọc tên của Member | `Member::name` | Unbound instance, receiver Member là tham số. |
| Chuyển generic T thành T | `ClassName::identity` | T suy luận theo target; có thể chỉ rõ type arguments. |
| Logic nhiều bước | `x -> { ...; return result; }` | Lambda có block body thường rõ hơn một method reference phức tạp. |
| Chọn overload bằng SAM rõ ràng | `(Supplier<String>) () -> "A"` | Cast chỉ để phân giải ambiguity, tránh lạm dụng. |

Method reference thường ngắn gọn khi hành vi **chính xác là một lời gọi có sẵn**. Lambda dễ đọc hơn khi có điều kiện, xử lý lỗi, nhiều bước, hoặc cần đổi dữ liệu đầu vào/đầu ra. Không có quy tắc rằng method reference luôn tốt hơn lambda.

## 13. Bài tập tự kiểm tra

### Bài 1

Vì sao dòng này không biên dịch?

```java
var length = s -> s.length();
```

**Đáp án:** Lambda cần target functional interface. `var` yêu cầu initializer có kiểu mà compiler suy luận trực tiếp được, nhưng lambda không có kiểu standalone phù hợp. Dùng `Function<String,Integer> length = s -> s.length();`.

### Bài 2

`Function<String,Integer> f = String::length;` có nghĩa gì?

**Đáp án:** Khi `f.apply(text)` được gọi, String đầu vào là receiver của `length()`. Kết quả int được boxing thành Integer theo target.

### Bài 3

`"pre-"::concat` là bound hay unbound instance reference?

**Đáp án:** Bound; receiver `"pre-"` đã biết, functional interface nhận phần chuỗi thêm vào. `Function<String,String>` là target thích hợp.

### Bài 4

Vì sao `ArrayList::new` có thể gán vào `Supplier<ArrayList<String>>`?

**Đáp án:** Supplier không nhận tham số và cần trả ArrayList<String>. Compiler chọn constructor không tham số của ArrayList phù hợp target.

### Bài 5

Vì sao `choose(() -> "ok")` mơ hồ nếu có hai overload nhận `Supplier<String>` và `Callable<String>`?

**Đáp án:** Cả hai interface có SAM không tham số trả String (Callable còn cho phép checked exception). Lambda phù hợp cả hai và không có ứng viên cụ thể hơn đủ để compiler chọn. Dùng typed Supplier variable hoặc cast.

### Bài 6

`BiFunction<String,String,String> f = (var a,b) -> a+b` có đúng không?

**Đáp án:** Không. Không được trộn tham số `var` với tham số implicit. Đổi thành `(var a,var b)` hoặc `(a,b)`.

### Bài 7

Vì sao `transform(List.of("A","Java"),String::length)` có thể tạo List Integer?

**Đáp án:** Compiler dùng type argument của source, target functional interface của mapper, kiểu trả về int của `length` và ngữ cảnh kết quả để suy luận T/R; int được boxing thành Integer.

### Bài 8

`Function<String,Integer> f = (Integer x) -> x + 1` bị lỗi ở đâu?

**Đáp án:** Kiểu tham số explicit Integer không tương thích String mà SAM yêu cầu. Compiler báo incompatible parameter types in lambda expression.

### Bài 9

Một static method overloaded `format(Object)` và `format(String)` có thể được tham chiếu qua `Function<String,String>` không?

**Đáp án:** Có trong ví dụ phần 7. Compiler chọn overload String phù hợp và cụ thể hơn; output `String:x`.

### Bài 10

Một lambda trong `Function<String,String>` có thể gọi method ném checked IOException chưa xử lý không?

**Đáp án:** Không nếu checked exception không được SAM khai báo/cho phép. Cần catch, chuyển đổi có kiểm soát, hoặc dùng functional interface khai báo throws tương thích.

### Bài 11

Vì sao `String::trim` không thể gán trực tiếp cho `var operation` nhưng có thể làm argument `transform(..., String::trim)`?

**Đáp án:** Ở phép gán `var` không có functional target type. Ở lời gọi `transform`, chữ ký generic của tham số mapper tạo target type cho method reference.

### Bài 12

Khi nào nên dùng `ToIntFunction<String>` thay `Function<String,Integer>`?

**Đáp án:** Khi cần xử lý int chuyên biệt, tránh boxing trong API, và có lý do về performance hay sự rõ ràng của kiểu primitive. Cả hai là hợp lệ cho `String::length` khi target tương ứng.

## 14. Tổng kết

**Suy luận kiểu của lambda và method reference là quá trình dựa vào ngữ cảnh đích.** Functional interface xác định kiểu tham số và kết quả; generic method thêm các quan hệ T/R; overload resolution quyết định phương thức nào có thể nhận biểu thức. Vì vậy cùng một lambda có thể hợp lệ hoặc không hợp lệ tùy nơi dùng, và cùng một method reference overloaded có thể được ràng buộc khác nhau theo target.

Hãy ưu tiên functional interface được khai báo rõ, generic signatures diễn đạt đúng quan hệ producer/consumer và các biến trung gian typed khi inference mơ hồ. Dùng `var` ở lambda parameters đúng cách nhưng đừng nhầm nó với việc không cần target. Khi method reference chỉ làm câu lệnh khó đọc hơn, viết một lambda rõ ràng là lựa chọn tốt.

**Ghi nhớ:** Trước khi hỏi “lambda này có kiểu gì?”, hãy hỏi **“compiler đang kiểm tra lambda này theo functional interface nào?”**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-standard-ap-is-005">Phương thức generic và lựa chọn phương thức overload</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức generic và lựa chọn phương thức overload trong Java

## 1. Bài toán: Khi có nhiều phương thức cùng tên, Java chọn phương thức nào?

Trong Java, **method overloading** (nạp chồng phương thức) cho phép khai báo nhiều phương thức cùng tên nhưng khác danh sách tham số. **Generic method** là phương thức tự khai báo type parameters như `<T>` và cho phép trình biên dịch suy luận kiểu tại mỗi lời gọi. Hai cơ chế này kết hợp với boxing, primitive widening, varargs, lambda và method references tạo ra những trường hợp lựa chọn overload khó đoán.

Ví dụ:

```java
static String choose(Object value) { return "Object"; }
static String choose(String value) { return "String"; }
```

Với `choose("Java")`, compiler chọn `choose(String)`. Nhưng khi có một generic overload:

```java
static <T> String option(T value) {
    return "generic";
}
static String option(CharSequence value) {
    return "CharSequence";
}
```

thì `option("Java")` trên Java 17 chọn phương thức nhận `CharSequence`, dù generic method có thể suy luận `T = String`.

Bạn cần hiểu ba lớp vấn đề độc lập:

1. **Tính hợp lệ của overload declarations**: hai signatures sau type erasure có xung đột không?
2. **Compile-time overload resolution**: ứng viên nào áp dụng được, tại invocation phase nào, và ai là most-specific?
3. **Runtime behavior**: sau khi compiler đã chọn method signature, instance overriding có thể dispatch theo object runtime, nhưng runtime không tự chọn lại overload chỉ vì object thực thuộc class con.

Bài học dùng Java 17, bao gồm ví dụ chạy được, ví dụ cố ý không biên dịch và cách sửa lỗi `ambiguous`, `name clash` hoặc `inference variable ... has incompatible bounds`.

## 2. Overloading khác overriding

**Overloading** chọn một phương thức dựa trên tên và kiểu tĩnh của danh sách đối số tại compile time.

```java
static String render(Object x) { return "Object"; }
static String render(String x) { return "String"; }

Object text = "Java";
System.out.println(render(text)); // Object
```

Object thật là `String`, nhưng biến `text` có kiểu tĩnh `Object`, nên compiler không chọn overload `(String)`. Nếu dùng pattern matching:

```java
if (text instanceof String s) {
    System.out.println(render(s)); // String
}
```

thì `s` có kiểu tĩnh `String`, dẫn đến lựa chọn khác.

**Overriding** lại là việc subclass cung cấp implementation cho method được kế thừa với signature phù hợp. Sau khi overload đã được resolve, lời gọi instance method có thể được runtime dispatch tới override đúng dựa trên class object. Không đánh đồng hai cơ chế.

### 2.1. Return type không tạo overload độc lập

Hai khai báo chỉ khác kiểu trả về không hợp lệ:

```java
// Cố tình không biên dịch:
// static String load(int id) { return "A"; }
// static Integer load(int id) { return id; }
```

Java không cho cùng tên và cùng danh sách tham số chỉ khác return type. Điều này không phủ nhận việc **target type** có thể ảnh hưởng generic type inference trong một biểu thức; đây là hai quy tắc khác nhau.

### 2.2. Candidate phải nhìn thấy và truy cập được

Compiler chỉ xét các candidate phù hợp với scope/accessibility tại lời gọi. Một overload `private` không truy cập được từ code bên ngoài không thể tự dưng trở thành ứng viên. Khi phân tích API, bắt đầu từ những method thực sự khả dụng trong ngữ cảnh caller.

## 3. Generic method tự khai báo type variable

```java
static <T> T identity(T value) {
    return value;
}
```

Type variable `T` là của **method**, không nhất thiết của class chứa method. Caller:

```java
String s = identity("Java");
Integer n = identity(10);
```

Compiler suy luận `T` từ arguments, bounds và khi phù hợp từ **target type** của biểu thức. Generic inference không đồng nghĩa `T` hiện hữu như một class runtime riêng sau erasure.

Một method có bound:

```java
static <T extends Number> double asDouble(T value) {
    return value.doubleValue();
}
```

có thể nhận `Integer` hoặc `Double`, nhưng không thể nhận `String`. Khi generic candidate có bound không thỏa, compiler phải loại candidate đó khỏi tập applicable methods.

### 3.1. `<T>` không phải wildcard `?`

`<T>` liên kết cùng type variable giữa nhiều vị trí:

```java
static <T> T first(List<T> items) {
    return items.get(0);
}
```

Trong khi:

```java
static int size(List<?> items) {
    return items.size();
}
```

không cần liên kết kiểu phần tử với return type nên wildcard là phù hợp.

Overload resolution phải xét generic method theo những ràng buộc kiểu thật; không có quy tắc “method có `<T>` luôn được chọn vì nó linh hoạt hơn”.

## 4. Ba giai đoạn kiểm tra applicability

Để hiểu các lựa chọn tiêu biểu, dùng mô hình ba giai đoạn của Java method invocation:

1. **Strict fixed-arity invocation**: xét những method áp dụng với số tham số cố định mà không cần boxing/unboxing theo loose invocation.
2. **Loose fixed-arity invocation**: nếu giai đoạn đầu không có candidate phù hợp, xem xét thêm các conversion như boxing/unboxing.
3. **Variable-arity invocation**: nếu vẫn chưa tìm được candidate, xét những method varargs theo hình thức số đối số thay đổi.

Trong giai đoạn đầu tiên có phương thức applicable, compiler tiếp tục chọn **most-specific method** theo các quy tắc của Java. Không quay sang giai đoạn sau chỉ vì một candidate ở đó có vẻ tiện hơn.

Chi tiết chính xác còn có generic inference, *pertinence to applicability*, wildcard, functional interface specificity và access rules. Mô hình ba giai đoạn là cách tổ chức suy nghĩ, không phải toàn bộ thuật toán JLS.

## 5. Primitive widening, boxing và generic method

Cho:

```java
static String widen(long x) {
    return "long";
}

static <T> String widen(T value) {
    return "generic";
}
```

Với `widen(7)`, argument `7` là primitive `int`, widening `int -> long` được chấp nhận ở strict phase. Generic method nhận `T` là reference type cần boxing `int -> Integer`, vì vậy overload `(long)` được chọn.

Với `widen(Integer.valueOf(7))`, argument đã là `Integer` reference. Generic method có thể nhận nó mà không cần unboxing, còn overload `(long)` cần unboxing/widening. Trong ví dụ đã kiểm thử, compiler chọn generic overload.

Kết quả:

```text
widen=long,generic
```

**Bài học:** không tồn tại quy tắc “generic method luôn thua non-generic method”. Kiểu tĩnh và invocation phase quyết định kết quả.

### 5.1. Đừng tự giả định wrapper conversions

`int -> long` là primitive widening. `int -> Integer` là boxing. Nhưng không phải mọi chuyển đổi giữa primitive và wrapper đều được compiler tự ghép thành một conversion tùy ý. Khi báo `no suitable method found`, hãy kiểm tra **chính xác conversion được phép** cho mỗi overload.

## 6. Most-specific: generic method so với tham số cụ thể

Xét:

```java
static <T> String option(T value) {
    return "generic:" + value.getClass().getSimpleName();
}

static String option(CharSequence value) {
    return "CharSequence";
}
```

`option("Java")` chọn `(CharSequence)` trong trường hợp này. `option(7)` không thể truyền một `Integer` tới `CharSequence` nên chọn generic.

Output đã kiểm chứng:

```text
option=CharSequence,generic:Integer
```

Compiler không chỉ suy luận `T = String` rồi tuyên bố đó là tham số “gần” nhất. Các quy tắc applicable methods và most-specific với generic/non-generic candidates cần được áp dụng theo JLS.

Một ví dụ khác:

```java
static String choose(Object x) { return "Object"; }
static String choose(String x) { return "String"; }
```

`choose("Java")` chọn `(String)` cụ thể hơn `(Object)`. `choose(7)` chọn `(Object)` sau boxing, vì không có overload `(String)` phù hợp.

### 6.1. Khi nào nên đặt overload cụ thể cạnh generic fallback?

Thường chỉ nên làm khi semantics thực sự khác và caller hiểu được. Generic fallback có thể khiến API khó đọc nếu việc chọn overload đổi theo kiểu tĩnh của biến:

```java
CharSequence text = "Java";
option(text); // CharSequence
```

Nếu muốn xử lý theo kiểu object runtime, overloading không phải polymorphism runtime. Cân nhắc visitor, strategy, pattern matching hoặc instance override đúng mục đích.

## 7. Fixed arity và generic varargs

```java
static String shape(String value) {
    return "fixed";
}

@SafeVarargs
static <T> String shape(T... values) {
    return "varargs:" + values.length;
}
```

Khi gọi `shape("x")`, fixed method `(String)` được chọn. Với `shape("x", "y")`, fixed method không nhận hai arguments, nên generic varargs áp dụng.

Kết quả:

```text
shape=fixed,varargs:2
```

**Nhưng:** varargs method vẫn có thể tham gia fixed-arity invocation khi caller truyền một array phù hợp:

```java
static void send(String... values) {
    System.out.println(values.length);
}

String[] existing = {"A", "B"};
send(existing); // Truyền array có sẵn.
```

Vì vậy, không nên rút gọn luật thành “method có `...` luôn bị bỏ qua nếu tồn tại method không có `...`”. Cần kiểm tra giai đoạn áp dụng và số arguments cụ thể.

### 7.1. Generic varargs và cảnh báo

`T...` là một array parameter ở source; `T` non-reifiable. Nếu body hoặc nơi gọi có nguy cơ heap pollution, `javac` có thể phát cảnh báo. `@SafeVarargs` có thể dùng với những method/constructor đủ điều kiện và an toàn thật sự; annotation không làm mọi generic varargs trở thành reifiable.

Trong ví dụ `shape` ở trên, method tĩnh chỉ đọc độ dài và không làm array escape hoặc ghi sai kiểu, nên có cơ sở dùng `@SafeVarargs`.

## 8. Lambda không có type độc lập khỏi target

Một lambda được kiểm tra theo **target functional interface type**, không tự có một lớp function độc lập trong source Java:

```java
UnaryOperator<String> clean = s -> s.strip();
```

Khi lời gọi bị overload:

```java
target(s -> s.strip());
```

compiler phải kiểm tra **từng candidate method** và target functional interface tương ứng trước khi quyết định overload thích hợp. Nếu hai candidate đều có thể nhận lambda nhưng không có một lựa chọn *most specific*, lời gọi có thể ambiguous. `var clean = s -> s.strip();` không hợp lệ vì initializer không cung cấp target functional interface.

Ở chương này, trọng tâm của phần này là **hệ quả đối với overload resolution**; quy trình target typing đầy đủ thuộc bài trước. Bước tiếp theo so sánh những candidate như `Function` và `UnaryOperator` để xem compiler thực sự chọn gì.

## 9. `Function` và `UnaryOperator` trong overload resolution

`UnaryOperator<T>` kế thừa `Function<T,T>`. Xét:

```java
static String target(Function<String, String> action) {
    return "Function:" + action.apply(" hi ");
}

static String target(UnaryOperator<String> action) {
    return "UnaryOperator:" + action.apply(" hi ");
}
```

Với:

```java
target(s -> s.strip());
```

Java chọn `UnaryOperator<String>` trong ví dụ đã kiểm thử vì quan hệ subtype/specificity áp dụng. Output:

```text
target=UnaryOperator:hi
```

Điều này không có nghĩa một tên functional interface cụ thể luôn được ưu tiên; thay đổi quan hệ kiểu giữa các interfaces hoặc lambda body có thể tạo kết quả khác.

### 9.1. Lambda + generic type inference

```java
static <T> T convert(T input, UnaryOperator<T> action) {
    return action.apply(input);
}
```

Call:

```java
String output = convert(" java ", String::strip);
```

Compiler suy luận `T = String`; method reference có target `UnaryOperator<String>` và kết quả là `"java"`.

Nguyên tắc quan trọng: khi compiler phân tích lambda/method reference trong generic invocation, type variables và expected functional interface types ràng buộc nhau. Lỗi inference đôi khi phát sinh ở lời gọi chứa lambda chứ không ở method thân bên trong.

## 10. `Supplier` và `Callable` có thể tạo ambiguity

`Supplier<String>` có abstract method `String get()`. `Callable<String>` có abstract method `String call() throws Exception`. Cả hai đều không nhận tham số và trả giá trị kiểu String.

```java
static String decide(Supplier<String> s) {
    return "Supplier:" + s.get();
}

static String decide(Callable<String> c) throws Exception {
    return "Callable:" + c.call();
}
```

Lời gọi:

```java
// Cố tình không biên dịch:
decide(() -> "ok");
```

trở nên ambiguous, vì lambda phù hợp cả hai target types, không có most-specific winner theo chữ ký này. Compiler báo:

```text
error: reference to decide is ambiguous
```

Cách sửa:

```java
decide((Supplier<String>) () -> "ok");
```

Hoặc:

```java
Supplier<String> supplier = () -> "ok";
decide(supplier);
```

Hai cách đều chỉ rõ target functional interface. Cách đặt biến có tên thường dễ đọc hơn nếu lambda phức tạp hoặc tái sử dụng nhiều lần.

### 10.1. Không ưu tiên `Supplier` chỉ vì không throws checked exception

Việc `Callable.call()` khai báo `throws Exception` không tạo một luật “ưu tiên Supplier” cho mọi lambda trả `String`. Không dùng checked exception signatures như một mẹo phân giải overload.

### 10.2. Dùng tên phương thức khác nếu kiểm soát API

Hai overload nhận unrelated interfaces nhưng có cùng lambda shape thường tạo API khó sử dụng. Nếu một method có nghĩa "cung cấp giá trị" và method kia có nghĩa "thực thi tác vụ có exception", tên như `supply(...)` và `callChecked(...)` có thể tốt hơn hai overload `decide(...)`.

## 11. `Consumer` và `Function`: statement-expression lambda

Một lambda có body là method invocation có thể vừa **void-compatible**, vừa **value-compatible**, vì method invocation là *statement expression*.

Cho:

```java
static void use(Consumer<String> c) { }
static void use(Function<String,String> f) { }
```

Lời gọi:

```java
// Không biên dịch:
use(s -> s.trim());
```

bị `javac --release 17` báo ambiguous. Dù `trim()` trả `String`, biểu thức method invocation vẫn có thể được dùng như statement bỏ kết quả, nên lambda tương thích với `Consumer<String>` lẫn `Function<String,String>`.

### 11.1. Block lambda giúp chỉ rõ ý định

Muốn `Consumer<String>`:

```java
use(s -> {
    s.trim();
});
```

Body không return value.

Muốn `Function<String,String>`:

```java
use(s -> {
    return s.trim();
});
```

Body return `String`.

Trong chương trình kiểm chứng:

```text
function=Function:abc
consumer=Consumer
```

Khi cần tính dễ hiểu lâu dài, nên tránh định nghĩa hai overload nhận `Consumer` và `Function` với cùng input type nếu chúng thường nhận lambda dạng statement expression.

### 11.2. `-Xlint:overloads` cảnh báo API có rủi ro

Với cặp overload trên, `javac --release 17 -Xlint:all` phát warning:

```text
[overloads] ... is potentially ambiguous with ...
```

Ngay cả khi các lời gọi hiện tại đã được viết bằng block rõ ràng, warning này nói về **khả năng API gây ambiguity ở các call sites khác**. Đây không phải lỗi runtime.

## 12. Method reference cũng cần target type

Method reference không phải một method object có sẵn functional interface cố định:

```java
String::strip
```

nó phải được hiểu theo target như `UnaryOperator<String>`.

Nếu class có overloads:

```java
static <T> T echo(T value) {
    return value;
}

static String echo(String value) {
    return "special:" + value;
}
```

thì:

```java
UnaryOperator<String> stringEcho =
        GenericOverloadDemo::echo;

Function<Integer, Integer> intEcho =
        GenericOverloadDemo::echo;
```

có thể resolve cùng tên `echo` sang các phương thức khác nhau. Với target `UnaryOperator<String>`, overload `(String)` được chọn; với target `Function<Integer,Integer>`, generic method phù hợp.

Output đã kiểm chứng:

```text
reference=special:z,5
```

### 12.1. Method reference cũng có thể ambiguous

Nếu có:

```java
static String text() { return "x"; }
static void execute(Supplier<String> s) { }
static void execute(Callable<String> c) { }
```

thì:

```java
// Khi đặt trong cùng class:
// execute(MyClass::text);
```

có thể ambiguous vì cả hai functional interfaces đều phù hợp với static method không tham số trả `String`. Phải chỉ rõ target:

```java
Supplier<String> textSupplier = MyClass::text;
execute(textSupplier);
```

Hoặc dùng cast thích hợp. Đừng kỳ vọng đổi lambda thành method reference sẽ tự loại ambiguity.

## 13. `null` và reference overload

```java
static String select(String x) { return "String"; }
static String select(Integer x) { return "Integer"; }
```

Lời gọi:

```java
// Không biên dịch:
select(null);
```

có hai ứng viên áp dụng (`String` và `Integer`) mà không có quan hệ subtype để chọn ứng viên duy nhất. `javac` báo:

```text
error: reference to select is ambiguous
```

Muốn chọn overload `(String)`:

```java
select((String) null);
```

Hoặc tạo biến có kiểu tĩnh String:

```java
String value = null;
select(value);
```

Lưu ý việc chọn được overload không bảo đảm body sẽ chấp nhận null. Nếu method dereference giá trị null, chương trình vẫn có thể ném `NullPointerException`.

## 14. Type erasure và `name clash`

Generic method:

```java
static <T> void inspect(List<T> items) { }
```

có method parameter type sau erasure về raw `List`. Nếu trong cùng class bạn thêm:

```java
static void inspect(List<String> items) { }
```

compiler báo:

```text
error: name clash: inspect(List<String>) and
<T>inspect(List<T>) have the same erasure
```

Tương tự:

```java
// Cũng không hợp lệ nếu cùng class:
static void read(List<String> strings) { }
static void read(List<Integer> integers) { }
```

Hai phương thức chỉ khác type arguments `String` và `Integer`, nhưng sau erasure đều có tham số `List`. Java không cho định nghĩa hai overload như vậy.

**Khác biệt:** `ambiguous` thường nói về **lời gọi** mà nhiều overload đã hợp lệ có thể nhận; `name clash` nói về **khai báo** không thể cùng tồn tại.

### 14.1. Cách sửa name clash

Không có cast tại caller nào giải quyết được lỗi declaration. Cân nhắc:

- Một generic method dùng chung.
- Các tên method rõ semantics: `inspectStrings`, `inspectNumbers`.
- Một tham số `Class<T>` hoặc strategy/handler nếu thực sự cần chỉ rõ cách xử lý.
- Tách trách nhiệm sang các concrete classes có generic interface.

Bridge methods do compiler sinh phục vụ overriding sau erasure không cho phép bạn viết hai overload cùng-erasure một cách thủ công.

## 15. Ví dụ Java 17 hoàn chỉnh

Tạo file **`GenericOverloadDemo.java`**:

```java
import java.util.concurrent.Callable;
import java.util.function.*;

public class GenericOverloadDemo {
    static String choose(Object value) {
        return "Object";
    }

    static String choose(String value) {
        return "String";
    }

    static <T> String option(T value) {
        return "generic:" + value.getClass().getSimpleName();
    }

    static String option(CharSequence value) {
        return "CharSequence";
    }

    static String widen(long value) {
        return "long";
    }

    static <T> String widen(T value) {
        return "generic";
    }

    static String shape(String value) {
        return "fixed";
    }

    @SafeVarargs
    static <T> String shape(T... values) {
        return "varargs:" + values.length;
    }

    static <T> T convert(T input, UnaryOperator<T> action) {
        return action.apply(input);
    }

    static String target(Function<String,String> fn) {
        return "Function:" + fn.apply(" hi ");
    }

    static String target(UnaryOperator<String> fn) {
        return "UnaryOperator:" + fn.apply(" hi ");
    }

    static String task(Runnable action) {
        action.run();
        return "Runnable";
    }

    static String task(Callable<String> action) throws Exception {
        return "Callable:" + action.call();
    }

    static String decide(Supplier<String> source) {
        return "Supplier:" + source.get();
    }

    static String decide(Callable<String> source) throws Exception {
        return "Callable:" + source.call();
    }

    static <T> T echo(T value) {
        return value;
    }

    static String echo(String value) {
        return "special:" + value;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("choose="
                + choose("Java") + "," + choose(7));
        System.out.println("option="
                + option("Java") + "," + option(7));
        System.out.println("widen="
                + widen(7) + "," + widen(Integer.valueOf(7)));
        System.out.println("shape="
                + shape("x") + "," + shape("x", "y"));
        System.out.println("convert="
                + convert(" java ", String::strip));
        System.out.println("target="
                + target(s -> s.strip()));
        System.out.println("task="
                + task(() -> "fromCallable"));
        System.out.println("decision="
                + decide((Supplier<String>) () -> "ok"));
        System.out.println("echo="
                + echo("hello") + "," + echo(42));

        UnaryOperator<String> f1 = GenericOverloadDemo::echo;
        Function<Integer,Integer> f2 = GenericOverloadDemo::echo;
        System.out.println("reference="
                + f1.apply("z") + "," + f2.apply(5));
    }
}
```

Lệnh:

```bash
javac --release 17 -Xlint:all GenericOverloadDemo.java
java GenericOverloadDemo
```

Chương trình đã biên dịch thành công trên Java 17 không có warning với `-Xlint:all`. Stdout chính xác:

```text
choose=String,Object
option=CharSequence,generic:Integer
widen=long,generic
shape=fixed,varargs:2
convert=java
target=UnaryOperator:hi
task=Callable:fromCallable
decision=Supplier:ok
echo=special:hello,42
reference=special:z,5
```

### 15.1. Cách diễn giải từng nhóm output

`choose`: overload String cụ thể hơn Object; khi argument là primitive `7`, boxing đưa nó thành `Integer` thích hợp với Object.

`option`: một generic fallback và một overload `CharSequence` có cách chọn khác nhau cho String và Integer; tính generic không tự tạo ưu tiên tuyệt đối.

`widen`: primitive widening thắng trong strict phase với `7`, còn `Integer` reference chọn generic khi không cần unboxing.

`shape`: method fixed nhận một String; generic varargs nhận hai String.

`convert`: type inference liên kết `String::strip` với `UnaryOperator<String>` và input String.

`target`: `UnaryOperator<String>` cụ thể hơn `Function<String,String>` trong overload set này.

`task`: lambda trả string literal không phải statement expression void-compatible, nên chỉ phù hợp với `Callable<String>` thay vì `Runnable`.

`decision`: cast lambda sang `Supplier<String>` làm rõ overload cần gọi.

`echo`/`reference`: một tên method có generic và non-generic overload được giải quyết theo kiểu argument hoặc target functional interface.

## 16. Năm ví dụ cố ý không biên dịch

Mỗi đoạn dưới là **một tệp riêng**; không đặt chung với chương trình chạy được.

### 16.1. `AmbiguousNull.java`

```java
class AmbiguousNull {
    static String select(String x) { return "s"; }
    static String select(Integer x) { return "i"; }

    static void check() {
        System.out.println(select(null));
    }
}
```

Lệnh `javac --release 17 AmbiguousNull.java` báo:

```text
error: reference to select is ambiguous
```

Sửa bằng `select((String) null)` hoặc một biến có kiểu tĩnh String nếu nghiệp vụ cho phép null.

### 16.2. `AmbiguousLambda.java`

```java
import java.util.concurrent.Callable;
import java.util.function.Supplier;

class AmbiguousLambda {
    static void choose(Supplier<String> s) { }
    static void choose(Callable<String> c) { }

    static void check() {
        choose(() -> "hello");
    }
}
```

Lệnh `javac --release 17 AmbiguousLambda.java` báo:

```text
error: reference to choose is ambiguous
```

Sửa bằng `Supplier<String> s = () -> "hello"; choose(s);` hoặc một cast functional interface phù hợp.

### 16.3. `AmbiguousVoidValue.java`

```java
import java.util.function.Consumer;
import java.util.function.Function;

class AmbiguousVoidValue {
    static void use(Consumer<String> fn) { }
    static void use(Function<String, String> fn) { }

    static void test() {
        use(s -> s.trim());
    }
}
```

Lệnh `javac --release 17 AmbiguousVoidValue.java` báo:

```text
error: reference to use is ambiguous
```

Sửa thành `use(s -> { s.trim(); });` nếu muốn Consumer, hoặc `use(s -> { return s.trim(); });` nếu muốn Function.

### 16.4. `NameClash.java`

```java
import java.util.List;

class NameClash {
    static <T> void inspect(List<T> items) { }
    static void inspect(List<String> items) { }
}
```

Lệnh `javac --release 17 NameClash.java` báo:

```text
error: name clash: inspect(List<String>) and
<T>inspect(List<T>) have the same erasure
```

Đây là lỗi declaration; phải sửa chữ ký hoặc tên method.

### 16.5. `MethodRefAmbiguous.java`

```java
import java.util.concurrent.Callable;
import java.util.function.Supplier;

class MethodRefAmbiguous {
    static String text() { return "x"; }
    static void run(Supplier<String> s) { }
    static void run(Callable<String> c) { }

    static void check() {
        run(MethodRefAmbiguous::text);
    }
}
```

Lệnh `javac --release 17 MethodRefAmbiguous.java` báo:

```text
error: reference to run is ambiguous
```

Method reference cũng cần target functional interface rõ ràng; có thể gán nó cho `Supplier<String>` trước khi gọi.

Năm trường hợp trên đã được kiểm tra trực tiếp bằng compiler Java 17, đều trả trạng thái biên dịch khác 0 với diagnostics tương ứng.

## 17. Chương trình giải ambiguity có kết quả rõ ràng

Tạo file **`OverloadDisambiguationProof.java`**:

```java
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

class OverloadDisambiguationProof {
    static String select(String x) {
        return "String";
    }

    static String select(Integer x) {
        return "Integer";
    }

    static String call(Supplier<String> f) {
        return "Supplier:" + f.get();
    }

    static String call(Callable<String> f) throws Exception {
        return "Callable:" + f.call();
    }

    static String apply(Consumer<String> f) {
        f.accept(" abc ");
        return "Consumer";
    }

    static String apply(Function<String,String> f) {
        return "Function:" + f.apply(" abc ");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("null=" + select((String) null));
        System.out.println("lambda="
                + call((Supplier<String>) () -> "done"));
        System.out.println("function="
                + apply(s -> { return s.trim(); }));
        System.out.println("consumer="
                + apply(s -> { s.trim(); }));

        Supplier<String> ref = () -> "value";
        System.out.println("typed=" + call(ref));
    }
}
```

Chạy:

```bash
javac --release 17 -Xlint:all OverloadDisambiguationProof.java
java OverloadDisambiguationProof
```

Stdout đã xác nhận:

```text
null=String
lambda=Supplier:done
function=Function:abc
consumer=Consumer
typed=Supplier:value
```

Compiler **vẫn phát một warning** `[overloads]` tại khai báo `apply(Function<String,String>)` với tùy chọn `-Xlint:all` vì cặp overload `Consumer`/`Function` tiềm ẩn ambiguity. Điều này chứng minh: việc call sites hiện tại được viết rõ ràng không xóa hết rủi ro về **thiết kế API**.

## 18. Phân biệt lỗi inference và ambiguous overload

Một số lỗi dễ gặp:

| Compiler diagnostic | Ý nghĩa chủ đạo | Cách xử lý |
| --- | --- | --- |
| `reference to ... is ambiguous` | Có nhiều applicable candidates, không có winner duy nhất. | Làm rõ kiểu tĩnh/functional target, thay tên API hoặc bớt overload. |
| `no suitable method found` | Không candidate nào áp dụng cho arguments/conversions/bounds. | Kiểm tra overload signatures và kiểu tĩnh thực tế. |
| `inference variable T has incompatible bounds` | Các ràng buộc lên type variables không giải được đồng thời. | Xem generic bounds, target type và các đối số/lambda. |
| `name clash ... same erasure` | Khai báo overload va chạm sau erasure. | Sửa method declarations, không chỉ cast tại call site. |
| `[overloads] ... potentially ambiguous` | API có overloads nhận functional interfaces khó phân biệt. | Cân nhắc tên phương thức khác hoặc thiết kế kiểu target rõ. |
| `ClassCastException` ở runtime | Giá trị bị dùng/cast thành kiểu runtime không tương thích. | Kiểm tra raw aliases, unchecked casts hoặc dữ liệu từ nguồn ngoài. |

**Phân biệt:** lỗi inference và ambiguous thường được phát hiện khi compile một lời gọi; name clash được phát hiện ngay tại declarations. Không chờ runtime “thử” một overload khác sau khi compiler đã báo ambiguous.

### 18.1. Target type có thể ảnh hưởng generic inference

Ví dụ:

```java
static <T> T empty() {
    return null;
}

String s = empty();
Integer i = empty();
```

Compiler có thể dùng target type của assignment để suy luận kiểu kết quả cho invocation. Với nested generic method calls, constraint từ surrounding expression có thể ảnh hưởng inference.

Điều này **không** có nghĩa bạn được định nghĩa hai methods chỉ khác return type. Target-type inference và luật overload declaration là các cơ chế riêng.

### 18.2. Explicit type witness không phải nút chọn overload tuyệt đối

Java cho phép chỉ rõ type arguments trong một số generic invocations:

```java
String value = GenericOverloadDemo.<String>echo("Java");
```

Nhưng đừng suy rằng cách viết `<String>` luôn buộc compiler chọn một implementation generic bất kể overload set. Applicability/specificity vẫn quyết định theo luật ngôn ngữ. Khi một API overload khó hiểu, giải pháp tốt hơn thường là đổi tên hoặc thiết kế chữ ký minh bạch.

## 19. Checklist xác định overload trong code thực

1. **Ghi chính xác kiểu tĩnh** của mọi đối số, kể cả generic type argument và primitive/reference.
2. **Liệt kê các overload khả dụng** tại nơi gọi, xét access/scope.
3. Với generic methods, xem **type variables, bounds và inference constraints** từ arguments/target.
4. Tìm applicable candidates trong **strict fixed-arity phase**.
5. Nếu không có, xét **loose fixed-arity phase** (boxing/unboxing).
6. Nếu vẫn không có, xét **variable-arity phase** cho varargs.
7. Trong phase đầu tiên thành công, so **most-specific** giữa candidates.
8. Với lambda/method reference, xác định **target functional interface**, descriptor parameters/return, void/value compatibility.
9. Với `null`, xác định reference overloads có quan hệ subtype chọn được winner không.
10. Nếu declarations dùng generic arguments khác nhau, kiểm tra **erasure/name clash**.
11. Nếu ambiguous, làm rõ kiểu bằng biến, cast có chủ đích hoặc thiết kế API mới; không dùng raw type để che lỗi.
12. Biên dịch Java 17 với `-Xlint:all`, kiểm tra stdout/diagnostics trên ví dụ tối thiểu.

Bảng tra nhanh:

| Lời gọi hoặc khai báo | Kết quả |
| --- | --- |
| `choose("x")` với `(Object)` và `(String)` | `(String)` cụ thể hơn. |
| `choose(x)` nếu biến x có kiểu `Object` nhưng runtime String | `(Object)` do compile-time type. |
| `widen(7)` với `(long)` và `<T>(T)` | `(long)` trong ví dụ. |
| `widen(Integer.valueOf(7))` | Generic overload trong ví dụ. |
| `shape("x")` với `(String)` và `<T>(T...)` | Fixed `(String)`. |
| `shape("x","y")` | Generic varargs. |
| `target(s -> s.strip())` với `Function` và `UnaryOperator` | `UnaryOperator`. |
| `decide(() -> "x")` với `Supplier<String>` và `Callable<String>` | Ambiguous. |
| `use(s -> s.trim())` với `Consumer` và `Function` | Ambiguous. |
| `select(null)` với `(String)` và `(Integer)` | Ambiguous. |
| `inspect(List<String>)` và `<T>inspect(List<T>)` | Name clash after erasure. |

## 20. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao biến `Object x = "Java"` gọi overload `(Object)` thay vì `(String)`?

<details>
<summary>Đáp án</summary>

Overload resolution dùng kiểu tĩnh `Object` của biểu thức tại compile time. Class runtime `String` không khiến Java tự chọn lại overload.

</details>

**Câu 2.** Vì sao `widen(7)` có thể chọn `(long)` thay vì generic `<T>(T)`?

<details>
<summary>Đáp án</summary>

Primitive widening `int -> long` áp dụng ở strict fixed-arity phase; generic candidate cần boxing cho argument primitive và được xét ở phase sau.

</details>

**Câu 3.** `List<String>` và `List<Integer>` có đủ khác nhau để định nghĩa hai overload `(List<String>)`, `(List<Integer>)` trong cùng class không?

<details>
<summary>Đáp án</summary>

Không. Erasure của hai parameterized types đều là raw `List`, gây name clash.

</details>

**Câu 4.** Vì sao `() -> "ok"` có thể ambiguous khi có overloads nhận `Supplier<String>` và `Callable<String>`?

<details>
<summary>Đáp án</summary>

Cả hai functional interfaces đều có abstract method không tham số trả `String`; lambda chưa có target type cụ thể, nên hai ứng viên đều phù hợp mà không có winner duy nhất. Dùng typed variable, cast hoặc tên API khác.

</details>

**Câu 5.** Vì sao `s -> s.trim()` có thể khớp cả Consumer và Function?

<details>
<summary>Đáp án</summary>

`trim()` là method invocation, thuộc nhóm statement expression. Expression lambda có thể vừa void-compatible vừa value-compatible. Dùng block không return cho Consumer, block có return cho Function.

</details>

**Câu 6.** Tại sao `UnaryOperator<String>` có thể thắng `Function<String,String>`?

<details>
<summary>Đáp án</summary>

`UnaryOperator<T>` mở rộng `Function<T,T>`, nên có quan hệ subtype/specificity được Java xét khi hai target types đều phù hợp.

</details>

**Câu 7.** Có thể dùng cast ở caller để sửa `name clash` giữa hai generic overload declarations không?

<details>
<summary>Đáp án</summary>

Không. Name clash là lỗi của các method declarations có cùng erasure. Phải sửa tên/signature hoặc thay thiết kế API.

</details>

**Câu 8.** Method reference `SomeClass::echo` có tự quyết định overload mà không cần target type không?

<details>
<summary>Đáp án</summary>

Không. Method reference dùng target functional interface; overload resolution của method được tham chiếu phụ thuộc kiểu tham số/return mà target yêu cầu.

</details>

## 21. Tổng kết

**Overload resolution diễn ra tại compile time**, với kiểu tĩnh, generic inference, applicable invocation phases và most-specific selection. Lambda/method reference còn cần target functional interface nên có thể khiến API overload trở nên ambiguous, dù từng method riêng lẻ hợp lệ.

Khi gặp lỗi, hãy phân biệt ba loại: `ambiguous` ở lời gọi; `name clash` ở declarations sau erasure; `incompatible bounds` ở generic inference. Không sửa bằng cách tùy tiện bỏ `<T>` hoặc dùng raw type. Dùng rõ kiểu tĩnh, lambda block, biến functional interface có tên, hoặc thiết kế lại chữ ký/naming để API dễ hiểu và type-safe.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-standard-ap-is-006">Giới hạn dùng lambda cho hợp đồng phương thức tự khai báo generic</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn dùng lambda cho hợp đồng phương thức tự khai báo generic trong Java

Một **functional interface** của Java là interface có đúng một phương thức trừu tượng theo các quy tắc đếm phương thức của Java (các khai báo tương ứng phương thức `Object`, phương thức `default`, `static` có quy tắc riêng). Lambda biểu diễn hành vi để triển khai **functional descriptor** của một kiểu đích (*target type*). Tuy nhiên, có một giới hạn tinh tế khi phương thức trừu tượng đó **tự khai báo type parameter**:

```java
@FunctionalInterface
interface PolyIdentity {
    <T> T apply(T value);
}
```

Interface trên **vẫn hợp lệ và vẫn là functional interface**, nhưng bạn **không thể** hiện thực trực tiếp phương thức generic `<T> T apply(T)` bằng lambda thông thường:

```java
// KHÔNG BIÊN DỊCH:
PolyIdentity identity = x -> x;
```

Trong Java, **functional descriptor của một lambda không thể là một phương thức tự khai báo type parameter (generic method)** theo cách này. Một lambda không có cú pháp khai báo `<T>` cho chính phương thức mà nó triển khai. `javac` có thể báo: `invalid functional descriptor for lambda expression`, kèm thông tin rằng phương thức đích là generic.

Điều này **khác hoàn toàn** với hai tình huống hợp lệ và rất phổ biến:

1. **Functional interface tự nó generic**, nhưng *single abstract method* không khai báo type parameter riêng, như `Function<T,R>`, `Predicate<T>`, `Consumer<T>`, `UnaryOperator<T>`.
2. **Phương thức bên ngoài nhận lambda là generic**, ví dụ `static <T> T transform(T value, UnaryOperator<T> action)`. Compiler suy luận `T` khi gọi, rồi kiểm tra lambda theo một functional descriptor cụ thể.

Ngoài ra, **method reference tới một phương thức generic phù hợp** và **anonymous class tự hiện thực generic method** có thể thực hiện hợp đồng `PolyIdentity`. Vì vậy, giới hạn này thuộc về **biểu thức lambda và cách nó được kiểm tra theo functional descriptor**, không phải lệnh cấm mọi generic method trong functional interface.

## 1. Phân biệt hai vị trí khai báo type parameter

Hãy so sánh hai interface:

```java
// A: T là type parameter của INTERFACE.
@FunctionalInterface
interface Identity<T> {
    T apply(T value);
}

// B: T là type parameter của METHOD apply.
@FunctionalInterface
interface UniversalIdentity {
    <T> T apply(T value);
}
```

Ở A, khi chọn kiểu đích `Identity<String>`, kiểu của phương thức duy nhất là `String apply(String)`. Compiler có thể kiểm tra lambda `s -> s.toUpperCase()` với tham số và kết quả `String`:

```java
Identity<String> upper = s -> s.toUpperCase();
String result = upper.apply("java"); // "JAVA"
```

Ở B, hợp đồng **một đối tượng UniversalIdentity phải chấp nhận mọi cách chọn T trong từng lời gọi**:

```java
UniversalIdentity value = /* một implementation hợp lệ */;
String text = value.apply("An");
Integer number = value.apply(42);
```

Nghĩa vụ của implementation không phải “chọn một `T` khi tạo object”, mà là cung cấp một phương thức generic có thể dùng nhiều kiểu tham chiếu phù hợp trong các lời gọi khác nhau. Java lambda thông thường không thể tự khai báo một phương thức mới mang type parameter `<T>` để thỏa hợp đồng đó.

| Khai báo | Type parameter thuộc về | Đặt lambda làm implementation? |
| --- | --- | --- |
| `interface Identity<T> { T apply(T x); }` với `Identity<String>` | Interface; được chọn ở kiểu đích | Có |
| `interface Mapper<T,R> { R map(T x); }` với `Mapper<String,Integer>` | Interface; hai kiểu được chọn ở kiểu đích | Có |
| `interface Universal { <T> T apply(T x); }` | Chính abstract method; một implementation phải hỗ trợ mọi `T` | Không bằng lambda trực tiếp |
| `static <T> T transform(T x, UnaryOperator<T> f)` | Generic method **nhận** lambda; descriptor của `UnaryOperator<T>` không tự khai báo generic method | Có thể truyền lambda |
| `static <T> T echo(T x)` gán qua `Universal u = ClassName::echo` | Generic method được tham chiếu | Có thể bằng method reference phù hợp |

Không đánh đồng “trong chữ ký có chữ `T`” với “phương thức chức năng tự khai báo `<T>`”. **Dấu `<T>` nằm ở đâu** quyết định rất nhiều.

## 2. Functional interface có generic abstract method vẫn hợp lệ

Interface sau là ví dụ tối thiểu:

```java
@FunctionalInterface
interface Polymorphic {
    <T> T convert(T input);
}
```

Annotation `@FunctionalInterface` được compiler chấp nhận khi interface thỏa định nghĩa functional interface. Tức là quy tắc **đếm một phương thức trừu tượng** và quy tắc **lambda-compatible descriptor** không đồng nhất ở điểm liên quan generic method.

Nếu thử:

```java
// KHÔNG BIÊN DỊCH:
Polymorphic p = input -> input;
```

thì vấn đề **không phải** vì interface có hai abstract methods. Nó có một abstract method, nhưng method đó là **generic method**. Compiler không thể gán lambda vào loại functional descriptor ấy.

Điểm cần giữ chính xác:

- `@FunctionalInterface` kiểm tra hình dạng interface theo quy tắc ngôn ngữ.
- Lambda expression cần một **target functional interface type** có functional descriptor phù hợp với lambda.
- Với đích như `<T> T convert(T)`, không có cách dùng lambda thông thường để khai báo `T` của method và đảm bảo implementation dùng được cho mọi phép thế kiểu.
- Các quy tắc này tồn tại ngay cả khi phần thân dự kiến “chỉ trả input” và trông rất tổng quát.

Không thể sửa bằng cách đổi `input -> input` thành `(Object input) -> input`: lúc đó bạn chỉ mô tả một lời gọi với `Object`, không hiện thực chính xác chữ ký generic `<T> T convert(T)`.

## 3. Vì sao `Function<T,R>` và `UnaryOperator<T>` dùng lambda được?

Xét cách khai báo ý tưởng của API chuẩn:

```java
@FunctionalInterface
interface FunctionLike<T, R> {
    R apply(T value);
}

@FunctionalInterface
interface UnaryLike<T> {
    T apply(T value);
}
```

`T` và `R` thuộc về **interface**, không phải được khai báo lại trên chính abstract method `apply`. Khi target type là `FunctionLike<String,Integer>`, functional descriptor sau khi thay thế type parameters có tham số `String`, trả về `Integer`.

```java
FunctionLike<String, Integer> length = s -> s.length();
System.out.println(length.apply("Java")); // 4
```

Tương tự, các functional interfaces của Java chuẩn:

- `Function<T,R>`: chuyển `T` thành `R`, có thể dùng lambda `x -> ...`.
- `UnaryOperator<T>`: `Function<T,T>`, đầu vào và đầu ra đều kiểu `T`.
- `Predicate<T>`: kiểm tra `T`, trả `boolean`.
- `Consumer<T>`: nhận một `T`, trả `void`.
- `Supplier<T>`: không nhận tham số, trả `T`.
- `BiFunction<T,U,R>`: nhận hai giá trị có kiểu `T` và `U`, trả `R`.

Các interface **generic ở cấp interface** này là những loại đích thường được dùng với lambda. Với Java 17, bạn có thể viết:

```java
java.util.function.Function<String, Integer> length = s -> s.length();
java.util.function.Predicate<String> nonBlank = s -> !s.isBlank();
java.util.function.UnaryOperator<Integer> increment = n -> n + 1;
```

Từng lambda ở đây chỉ phải thỏa kiểu phương thức đã xác định bởi target type của nó. Không cần một lambda duy nhất tự khai báo một generic method có thể gọi với `String` rồi `Integer` như hợp đồng `<T> T apply(T)`.

### Không phải mọi functional interface tự khai báo generic đều bị cấm

Phát biểu “lambda không dùng được với interface generic” là **sai**. Interface `Identity<T>` chính là phản ví dụ. Giới hạn đang nghiên cứu là **generic abstract method descriptor**, không phải sự hiện diện của dấu `<...>` trên interface hoặc trên các kiểu tham số của nó.

## 4. Generic method nhận lambda: hợp lệ vì mỗi lời gọi suy luận kiểu riêng

Một API có thể khai báo type parameter tại **phương thức gọi lambda**, trong khi lambda vẫn có một functional descriptor hợp lệ:

```java
static <T> T transform(
        T value,
        java.util.function.UnaryOperator<T> operation) {
    return operation.apply(value);
}
```

Cách gọi:

```java
String text = transform("hello", s -> s + "!");
Integer count = transform(10, n -> n + 5);
```

Ở lần gọi đầu, compiler có thể suy luận `T = String`; lambda được kiểm tra như `UnaryOperator<String>`. Ở lần gọi thứ hai, nó có thể suy luận `T = Integer`; lambda được kiểm tra như `UnaryOperator<Integer>`.

Điều này **không biến bản thân lambda** thành một method generic `<T> T apply(T)`. Mỗi biểu thức lambda là một implementation theo **một kiểu đích** được xác định trong quá trình type inference của lời gọi.

Ví dụ khác:

```java
static <T, R> R convert(
        T value,
        java.util.function.Function<? super T, ? extends R> mapper) {
    return mapper.apply(value);
}

String result = convert(42, x -> "No." + x); // "No.42"
```

Generic method `convert` tự khai báo `T`, `R`. Type parameters ở **lớp phương thức bao ngoài**, và `Function<? super T, ? extends R>` vẫn có một phương thức `apply` không tự khai báo type parameter. Compiler đủ thông tin để kiểm tra lambda.

### Dấu `<T>` trong method không áp vào lambda theo cách tương tự

Cần phân biệt:

```java
// Hợp lệ:
static <T> void run(T input, java.util.function.Consumer<T> action) {
    action.accept(input);
}
```

với:

```java
// Interface hợp lệ, nhưng không lambda-implementable trực tiếp:
interface UniversalConsumer {
    <T> void accept(T input);
}
```

Với `run`, `T` được suy luận **khi gọi run** và ràng buộc kiểu của callback. Với `UniversalConsumer`, `accept` là generic abstract method của chính đối tượng callback, đòi implementation có tham số kiểu riêng trong mọi lời gọi.

## 5. Dùng method reference trỏ tới generic method

Một điều thú vị: **generic method reference** có thể phù hợp với functional interface có generic abstract method trong những trường hợp được kiểm tra hợp lệ.

```java
@FunctionalInterface
interface PolyIdentity {
    <T> T apply(T value);
}

static <U> U echo(U value) {
    return value;
}

// Đặt trong ngữ cảnh class hợp lệ:
PolyIdentity identity = MyClass::echo;
```

`MyClass::echo` tham chiếu một method **thật sự tự khai báo `<U>`**. Java có cơ chế kiểm tra *method reference* với functional descriptor khác lambda, nên một method reference generic phù hợp có thể đáp ứng hợp đồng mà `x -> x` không đáp ứng.

Sau khi gán:

```java
String text = identity.apply("An");
Integer number = identity.apply(123);
```

Cả hai lời gọi hợp lệ; implementation là một generic method có sẵn, chứ không phải compiler tự “nhân” một lambda thành generic method mới.

**Không phải mọi method reference đều được chấp nhận.** Phương thức được tham chiếu phải có chữ ký, khả năng truy cập, tham số kiểu, kết quả và các ràng buộc phù hợp với functional descriptor. Ví dụ, một method chỉ nhận `String` không tự nhiên hiện thực `<T> T apply(T)` cho mọi `T`.

Một method reference dạng bound tới instance method generic cũng có thể hợp lệ:

```java
class Echoer {
    <T> T echo(T value) {
        return value;
    }
}

PolyIdentity p = new Echoer()::echo;
```

Khác biệt với lambda đến từ **quy tắc riêng của Java cho method references**, không phải từ việc type erasure biến `T` thành `Object` rồi ngầm chấp nhận mọi phép cast.

## 6. Anonymous class: giải pháp trực tiếp khi thực sự cần generic SAM

Nếu logic không có sẵn dưới dạng generic method thích hợp, hãy tạo anonymous class và **khai báo rõ method generic**:

```java
@FunctionalInterface
interface PolyIdentity {
    <T> T apply(T value);
}

PolyIdentity identity = new PolyIdentity() {
    @Override
    public <T> T apply(T value) {
        return value;
    }
};
```

Tại đây, phần thân `apply` được khai báo là một method Java đầy đủ với `<T>`, khác cú pháp lambda. Compiler có thể kiểm tra rằng nó thực sự override generic abstract method `<T> T apply(T)`.

Cách này có thể dài hơn lambda nhưng diễn đạt đúng hợp đồng **đa hình theo từng lời gọi** của method.

Ví dụ nếu muốn giới hạn bound:

```java
@FunctionalInterface
interface NumericIdentity {
    <T extends Number> T apply(T value);
}

NumericIdentity identity = new NumericIdentity() {
    @Override
    public <T extends Number> T apply(T value) {
        return value;
    }
};
```

Lời gọi `identity.apply(42)` hoặc `identity.apply(3.5)` có thể hợp lệ, còn `identity.apply("Java")` không thỏa bound `Number`.

Phần viết anonymous class không làm `T` thành reifiable. Nếu bên trong muốn viết `new T()`, `T.class` hoặc `new T[10]`, các giới hạn khác của generic type variables vẫn tồn tại.

## 7. Chương trình Java 17+ kiểm chứng những trường hợp hợp lệ

Lưu toàn bộ vào file **`GenericLambdaDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class GenericLambdaDemo {
    @FunctionalInterface
    interface PolyIdentity {
        <T> T apply(T value);
    }

    @FunctionalInterface
    interface Identity<T> {
        T apply(T value);
    }

    static <T> T echo(T value) {
        return value;
    }

    static <T> T transform(T value, UnaryOperator<T> operation) {
        return operation.apply(value);
    }

    static <T, R> R convert(
            T value, Function<? super T, ? extends R> mapper) {
        return mapper.apply(value);
    }

    static <T> List<T> select(
            List<T> source, Predicate<? super T> test) {
        List<T> result = new ArrayList<>();
        for (T item : source) {
            if (test.test(item)) {
                result.add(item);
            }
        }
        return result;
    }

    public static void main(String[] args) {
        Identity<String> shout =
                s -> s.toUpperCase(Locale.ROOT);
        Identity<Integer> doubleValue = n -> n * 2;

        System.out.println(
                "Lambda String: " + shout.apply("java"));
        System.out.println(
                "Lambda Integer: " + doubleValue.apply(7));

        System.out.println("Generic API String: "
                + transform("hello", s -> s + "!"));
        System.out.println("Generic API Integer: "
                + transform(10, x -> x + 5));

        System.out.println("Generic conversion: "
                + convert(42, x -> "No." + x));

        System.out.println("Filtered: "
                + select(List.of("a", "java", "api"),
                         s -> s.length() > 2));

        // Generic method reference có thể thỏa generic SAM.
        PolyIdentity methodReference = GenericLambdaDemo::echo;
        System.out.println("Reference String: "
                + methodReference.apply("An"));
        System.out.println("Reference Integer: "
                + methodReference.apply(123));

        // Anonymous class trực tiếp khai báo generic method.
        PolyIdentity anonymous = new PolyIdentity() {
            @Override
            public <T> T apply(T value) {
                return value;
            }
        };
        System.out.println("Anonymous Boolean: "
                + anonymous.apply(true));

        int threshold = 3; // Effectively final
        Predicate<String> longWord =
                text -> text.length() > threshold;
        System.out.println("Captured value: "
                + longWord.test("java"));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 GenericLambdaDemo.java
java GenericLambdaDemo
```

**Standard output chính xác**:

```text
Lambda String: JAVA
Lambda Integer: 14
Generic API String: hello!
Generic API Integer: 15
Generic conversion: No.42
Filtered: [java, api]
Reference String: An
Reference Integer: 123
Anonymous Boolean: true
Captured value: true
```

Giải thích:

1. Hai biến `Identity<String>` và `Identity<Integer>` minh họa interface generic **có lambda hợp lệ** vì `apply(T)` của chúng không tự khai báo `<U>`.
2. `transform`, `convert`, `select` là các **generic methods nhận callback**. Mỗi lần gọi suy luận type arguments rồi kiểm tra lambda theo `UnaryOperator<T>`, `Function<T,R>` hoặc `Predicate<T>`.
3. `GenericLambdaDemo::echo` là method reference đến method generic `<T> T echo(T)`, có thể hiện thực `PolyIdentity` với cả lời gọi `String` và `Integer`.
4. Anonymous class khai báo chính xác `public <T> T apply(T)`; ở ví dụ, Boolean được boxing thành `Boolean` và trả về.
5. Lambda cuối đọc `threshold` từ scope bên ngoài vì biến local đó **effectively final**. Đây là quy tắc capture riêng của lambda, **không** phải việc lambda tự khai báo generic method.

Phần **không biên dịch** `PolyIdentity p = x -> x;` không xuất hiện trong chương trình chạy để tránh làm hỏng bản demo.

## 8. Ví dụ cố ý thất bại và cách đọc compiler diagnostic

Đặt vào file độc lập **`GenericLambdaError.java`** nếu muốn tự kiểm tra lỗi:

```java
public class GenericLambdaError {
    @FunctionalInterface
    interface UniversalIdentity {
        <T> T apply(T value);
    }

    public static void main(String[] args) {
        UniversalIdentity bad = x -> x; // CỐ Ý SAI
        System.out.println(bad.apply("x"));
    }
}
```

Chạy:

```bash
javac GenericLambdaError.java
```

Compiler có thể báo dạng:

```text
incompatible types: invalid functional descriptor for lambda expression
    method <T>(T)T in interface UniversalIdentity is generic
```

Vị trí/dấu câu của thông báo phụ thuộc phiên bản javac, nhưng trọng tâm là **functional descriptor của lambda trỏ tới một generic method**.

Đừng sửa bằng các cách thiếu an toàn:

```java
// KHÔNG phải giải pháp:
// UniversalIdentity p = (Object x) -> (Object) x;
// UniversalIdentity p = x -> (Object) x;
```

Các biểu thức này không khai báo hay thực hiện được hợp đồng `<T> T apply(T)`. Cast sang `Object` không biến một lambda thành polymorphic generic method; compiler cũng không tự tạo overload/bridge cần thiết từ những cast ấy để thỏa interface.

Chọn cách sửa theo **ý nghĩa của API**:

- Nếu một object thật sự phải hỗ trợ nhiều type arguments ở các lần gọi khác nhau: method reference đến generic method phù hợp hoặc anonymous class có method `<T>` rõ ràng.
- Nếu callback chỉ cần một kiểu `T` tại một vị trí dùng: chuyển sang `Identity<T>` hoặc `Function<T,T>` để lambda hoạt động.
- Nếu một thuật toán generic nhận callback ở nhiều lời gọi độc lập: đưa `<T>` lên generic method chứa tham số kiểu `Function`/`UnaryOperator`, thay vì đặt nó trên abstract method của một callback cố định.

## 9. Chuyển generic method trong interface thành generic interface khi phù hợp

Giả sử thiết kế ban đầu là:

```java
@FunctionalInterface
interface UniversalValidator {
    <T> boolean valid(T item);
}
```

Hợp đồng này yêu cầu **cùng một đối tượng validator có thể nhận mọi loại `T` trong mọi lần gọi**. Nếu công việc thực tế chỉ cần kiểm tra `String` ở chỗ này và `Integer` ở chỗ khác, điều đó có thể phức tạp không cần thiết.

Thiết kế thường phù hợp hơn:

```java
@FunctionalInterface
interface Validator<T> {
    boolean valid(T item);
}

Validator<String> notBlank = s -> !s.isBlank();
Validator<Integer> positive = n -> n > 0;
```

Hai instance validator có contract tĩnh rõ ràng. Compiler kiểm tra input, và mỗi lambda có một descriptor không generic.

Dùng interface chuẩn cũng được:

```java
java.util.function.Predicate<String> notBlank = s -> !s.isBlank();
java.util.function.Predicate<Integer> positive = n -> n > 0;
```

**Không phải lúc nào đổi `UniversalValidator` thành `Validator<T>` cũng bảo toàn ý nghĩa.** Trong thiết kế đầu, một implementation phải nhận mọi `T`; trong thiết kế mới, một instance được tham số hóa bởi một `T` xác định. Chỉ refactor khi hợp đồng nghiệp vụ thật sự không cần kiểu đa hình qua các lần gọi cùng object.

## 10. Generic API dùng callback tại từng lần gọi: mẫu thiết kế khuyến nghị

Trong nhiều tình huống, generic method bên ngoài callback là lựa chọn dễ đọc và dễ type-check nhất:

```java
static <T> java.util.List<T> select(
        java.util.List<T> values,
        java.util.function.Predicate<? super T> predicate) {
    java.util.List<T> selected = new java.util.ArrayList<>();
    for (T item : values) {
        if (predicate.test(item)) {
            selected.add(item);
        }
    }
    return selected;
}
```

Với:

```java
java.util.List<String> words =
        select(java.util.List.of("a", "java"), s -> s.length() > 1);

java.util.List<Integer> even =
        select(java.util.List.of(1, 2, 3), n -> n % 2 == 0);
```

`T` của `select` được suy luận riêng cho mỗi lời gọi; lambda bên trong mỗi lời gọi có một target type `Predicate<? super T>` tương ứng. Wildcard `? super T` cho phép chấp nhận các predicate có thể tiêu thụ phần tử kiểu `T` một cách an toàn.

Nếu muốn biến đổi, dùng `Function<? super T, ? extends R>`; nếu muốn hành động không trả kết quả, dùng `Consumer<? super T>`. Đây là cách thiết kế có type inference tốt, tránh tạo một interface mà abstract method của chính nó phải generic chỉ để callback dùng với nhiều kiểu khác nhau tại **các vị trí gọi khác nhau**.

### Generic methods có thể suy luận qua lambda body và target type

Compiler Java thực hiện **type inference** và kiểm tra *poly expressions* theo ngữ cảnh. Ví dụ:

```java
static <T, R> R map(T input, java.util.function.Function<T, R> mapper) {
    return mapper.apply(input);
}

String text = map(42, n -> "number=" + n);
```

Ở đây kiểu `T` được suy luận từ `42`, còn `R` liên quan kiểu kết quả lambda và kiểu đích `String`. Tuy vậy, body lambda không tự khai báo một generic method; nó chỉ được kiểm tra theo descriptor `Function<T,R>` đã được suy luận phù hợp.

Nếu javac không suy luận được kiểu mong muốn, có thể làm rõ kiểu ở target assignment, parameter declaration hoặc thêm type witness ở lời gọi generic method. Không cần — và không thể — “đưa `<T>` vào trước lambda” để biến lambda thành generic method.

## 11. Những giới hạn khác của lambda: liên quan nhưng không đồng nhất

Ngoài generic SAM, Java lambda còn chịu những quy tắc khác:

### 11.1. Lambda cần target type là functional interface phù hợp

```java
// KHÔNG BIÊN DỊCH: không thể gán trực tiếp lambda vào Object.
// Object action = x -> x;

// Hợp lệ:
java.util.function.UnaryOperator<String> action = x -> x;
Object stored = action;
```

Target typing là cơ chế chính của lambda. Bản thân cú pháp `x -> x` không tự cung cấp type parameter hay một function object với mọi type signature có thể tưởng tượng.

### 11.2. Kiểu trả về và tham số phải phù hợp với descriptor

```java
java.util.function.Function<String, Integer> length = s -> s.length();
// java.util.function.Function<String,Integer> invalid = s -> s; // Sai kiểu trả về
```

Đây là giới hạn bình thường do **chữ ký API** đã chọn (`String` sang `Integer`), không phải giới hạn đặc thù của generic abstract method.

### 11.3. Capture biến local cần final hoặc effectively final

```java
int threshold = 3;
java.util.function.Predicate<String> longer =
        text -> text.length() > threshold;

// threshold++; // Nếu bật dòng này, biến không còn effectively final
```

Rule *effectively final* của Java áp dụng khi lambda capture local variable từ scope bao ngoài. Không được nhầm nó với việc generic type parameter có scope riêng.

### 11.4. Checked exceptions cần phù hợp với hợp đồng callback

`Function.apply` chuẩn không khai báo checked exception. Vì vậy lambda truyền vào `Function` không thể ném một checked exception chưa xử lý như `IOException` nếu descriptor không cho phép. Có thể chọn API khác có functional interface khai báo `throws`, hoặc xử lý ngoại lệ trong lambda. Đây là **một giới hạn của descriptor/hợp đồng checked exception**, không đồng nghĩa generic lambda bị cấm.

### 11.5. Overload có thể làm target type của lambda không rõ

Nếu có nhiều overload nhận các functional interface khác nhau mà cùng phù hợp với một lambda, compiler có thể báo *ambiguous method call*. Cần làm rõ đích qua biến có kiểu, cast hợp lệ đến functional interface hoặc đổi thiết kế overload. Đừng coi mọi lỗi lambda đều do abstract method generic.

## 12. Không dùng raw type hoặc unchecked cast để giả lập generic callback

Một giải pháp thiếu an toàn thường xuất phát từ suy nghĩ “nếu compiler không cho lambda gắn vào `<T> T apply(T)`, cứ cast nó qua `Object` hay raw `Function` rồi dùng”.

Vấn đề: một `Function<Object,Object>` có hợp đồng **một kiểu tham số và kết quả Object**, còn `<T> T apply(T)` yêu cầu quan hệ input/output cùng `T` **đối với mọi lần chọn `T`**. Hai hợp đồng này không đồng nhất chỉ vì cùng có erasure liên quan `Object`.

```java
// Cách tiếp cận không nên dùng để "chứng minh" universal contract:
java.util.function.Function<Object, Object> f = input -> input;
```

Dù `f` có thể trả lại input, bản thân nó không cung cấp chữ ký method generic `<T> T apply(T)`. Nếu thêm unchecked casts và raw types để né compiler, bạn có thể chuyển lỗi thành `ClassCastException` ở caller và làm mất hợp đồng kiểu tĩnh.

**Giải pháp đúng** là chọn functional interface thích hợp, hoặc thực hiện generic method rõ ràng bằng anonymous class/method reference. Không dùng `@SuppressWarnings("unchecked")` để thay thế một bằng chứng về tương thích phương thức.

## 13. Khi nào thật sự cần generic abstract method của functional interface?

Nhu cầu thực tế tồn tại khi một object phải cung cấp **hành vi đa hình với mỗi lần gọi**. Ví dụ, một phép identity chung cần giữ kiểu kết quả đúng theo kiểu đầu vào mà không cố định một type argument tại lúc tạo object:

```java
interface PolyIdentity {
    <T> T apply(T value);
}
```

Một implementation có thể nhận cùng lần lượt `String`, `Integer`, `Boolean`, và trả đúng kiểu tương ứng. Method reference `GenericLambdaDemo::echo` đáp ứng khi `echo` là generic method phù hợp.

Nhưng với các phép xử lý **đặc thù cho một kiểu**, ví dụ viết hoa chuỗi hoặc nhân đôi số nguyên, không cần hợp đồng “mọi `T`”:

```java
java.util.function.UnaryOperator<String> upper =
        s -> s.toUpperCase(java.util.Locale.ROOT);

java.util.function.UnaryOperator<Integer> twice =
        n -> n * 2;
```

Nếu bắt chúng phải hiện thực `<T> T apply(T)`, nghĩa vụ phải hoạt động với **bất kỳ `T` nào** sẽ không phù hợp với thuật toán chỉ làm việc với `String` hoặc `Integer`. Thường đây là dấu hiệu **thiết kế interface sai mức tổng quát**, không đơn thuần là thiếu một mẹo cú pháp lambda.

### Bounded generic method không trở thành lambda-compatible

Kể cả khi type parameter có bound:

```java
@FunctionalInterface
interface NumberIdentity {
    <N extends Number> N apply(N value);
}
```

Abstract method vẫn **tự khai báo generic parameter `N`**. Không thể giả định lambda `n -> n` được chấp nhận chỉ vì erasure của `N` là `Number`; giới hạn descriptor vẫn áp dụng.

Có thể dùng anonymous class khai báo `<N extends Number>`, hoặc method reference tới một generic method có bound tương thích. Một giải pháp thay thiết kế khác là `UnaryOperator<Number>`, nhưng nó có hợp đồng **Number → Number**, không bảo toàn chính xác subtype ở mỗi lời gọi như `<N extends Number> N apply(N)`.

## 14. Lỗi thường gặp và lời giải đúng

**Sai lầm 1 — “Interface có `@FunctionalInterface` thì mọi lambda đều dùng được.”** Không. Generic abstract method vẫn có thể là SAM, nhưng không có functional descriptor mà lambda trực tiếp triển khai theo cách này.

**Sai lầm 2 — “Có dấu `<T>` ở đâu cũng cấm lambda.”** Không. `Function<T,R>` và các generic interface chuẩn được dùng với lambda mỗi ngày. Cần xác định `T` thuộc interface hay thuộc chính abstract method.

**Sai lầm 3 — “Một lambda `x -> x` tự động là generic cho mọi kiểu.”** Không. Lambda có target type cụ thể. Nó không khai báo method-level type parameters.

**Sai lầm 4 — “Generic method nhận lambda cũng bị cấm.”** Không. `static <T> T transform(T, UnaryOperator<T>)` là mẫu hợp lệ: `T` được suy luận tại lời gọi, còn descriptor `UnaryOperator<T>` không tự generic.

**Sai lầm 5 — “Method reference và lambda luôn thay thế nhau hoàn toàn.”** Không. Generic method reference có thể hợp lệ với generic SAM khi lambda không hợp lệ, do quy tắc kiểm tra khác nhau.

**Sai lầm 6 — “Dùng anonymous class cũng không được vì interface có generic abstract method.”** Không. Anonymous class khai báo rõ `public <T> T apply(T)` là cách hiện thực hợp lệ.

**Sai lầm 7 — “Erasure của `T` thành `Object` cho phép ép `Function<Object,Object>` thành universal callback.”** Không. Hợp đồng generic về quan hệ kiểu giữa input/output ở từng lời gọi mạnh hơn việc dùng `Object`; cast không thay thế kiểm tra kiểu của compiler.

**Sai lầm 8 — “Lambda nhận `Predicate<? super T>` là một generic method.”** Không. `Predicate` có type parameter ở interface; `test(T)` không tự khai báo generic method. Wildcard là một phần quy tắc tương thích kiểu ở target.

**Sai lầm 9 — “Mọi lỗi biên dịch của lambda đều do generics.”** Không. Scope/capture, overload ambiguity, checked exception, mismatch kiểu trả về hay thiếu functional target type đều có thể là nguyên nhân khác.

## 15. Bài tập tự kiểm tra

**Câu 1.** Interface này có là functional interface không?

```java
interface Universal {
    <T> T apply(T value);
}
```

**Có.** Nó có một abstract method hợp lệ. Nhưng tính functional interface **không đồng nghĩa lambda trực tiếp tương thích** với method descriptor generic đó.

**Câu 2.** `Universal v = x -> x;` có hợp lệ không?

**Không.** `apply` tự khai báo `<T>`. Lambda không thể trực tiếp hiện thực functional descriptor generic đó.

**Câu 3.** `interface Id<T> { T apply(T x); }` và `Id<String> id = x -> x;` có hợp lệ không?

**Có.** `T` thuộc interface; target `Id<String>` có descriptor `String apply(String)`.

**Câu 4.** `static <T> T echo(T x)` có thể được tham chiếu bằng `Universal v = ClassName::echo` không?

**Có**, nếu chữ ký và các điều kiện truy cập/tương thích phù hợp. Đây là method reference tới generic method thật.

**Câu 5.** Phương thức `static <T> T run(T v, UnaryOperator<T> op)` có nhận được `run("a", x -> x + "!")` không?

**Có.** Caller suy luận `T = String`, còn lambda thực hiện `UnaryOperator<String>` theo target type được xác định.

**Câu 6.** Nếu một interface khai báo `<N extends Number> N apply(N n)`, có thể dùng lambda `n -> n` không?

**Không trực tiếp.** Bound không xóa bản chất generic của abstract method. Dùng generic method reference phù hợp hoặc anonymous class với `<N extends Number>`.

**Câu 7.** Tại sao không dùng `Function<Object,Object>` để thay hoàn toàn `<T> T apply(T)`?

Vì `Function<Object,Object>` không bảo đảm compiler biết đầu ra cùng type argument `T` với đầu vào **tại từng lời gọi**; nó có chữ ký `Object apply(Object)`.

**Câu 8.** Cần callback viết hoa `String` và callback nhân đôi `Integer`. Chọn universal generic SAM hay hai `UnaryOperator<...>`?

Thông thường chọn **`UnaryOperator<String>`** và **`UnaryOperator<Integer>`**. Hai hành vi đặc thù không cần và không thỏa hợp đồng phải hỗ trợ mọi `T`.

## 16. Checklist thiết kế API lambda có generics

Trước khi tạo một functional interface hoặc generic callback, hãy kiểm tra:

1. **Abstract method có tự viết `<T>` không?** Nếu có, đừng mặc định lambda trực tiếp tương thích.
2. **`T` thuộc interface hay method?** `interface F<T> { T run(T); }` khác `interface F { <T> T run(T); }`.
3. **Một object phải hỗ trợ nhiều kiểu tại các lần gọi khác nhau không?** Nếu thực sự cần, có thể cần generic method reference hoặc anonymous class.
4. **Hay mỗi callback được dùng với một kiểu ở một vị trí?** Nếu vậy, `Function<T,R>`, `Predicate<T>`, `Consumer<T>` và `UnaryOperator<T>` thường phù hợp hơn.
5. **Type parameter có thể chuyển lên method API bên ngoài không?** Ví dụ `static <T> void process(T input, Consumer<T> consumer)`.
6. **Có thể dùng method reference tới một generic helper đã có không?** Kiểm tra cả bound, tham số, kết quả và overload resolution.
7. **Có cần wildcard PECS?** Sử dụng `? super T` cho consumer, `? extends R` cho producer khi đúng mục tiêu.
8. **Lỗi compiler thuộc loại gì?** Phân biệt generic descriptor không lambda-compatible, lambda body sai kiểu, biến capture không effectively final, checked exceptions và ambiguous overload.
9. **Có đang định dùng raw/cast để lách không?** Dừng lại và soát contract trước khi gây heap pollution hoặc lỗi cast muộn.
10. **Ví dụ đã biên dịch theo đúng Java target chưa?** Chạy `javac` với `--release` phù hợp khi hỗ trợ nhiều phiên bản Java, và kiểm tra cả chương trình hợp lệ lẫn một phản ví dụ bị từ chối riêng.

**Kết luận:** Java hỗ trợ rất tốt **generic interface dùng với lambda** và **generic method nhận lambda**, nhưng một lambda thông thường **không thể trực tiếp triển khai phương thức trừu tượng tự khai báo type parameter** như `<T> T apply(T)`. Interface đó vẫn có thể là functional interface; cách triển khai phù hợp có thể là **generic method reference** hoặc **anonymous class khai báo rõ generic method**. Nếu không thật sự cần một callback đa hình ở mọi lần gọi, thiết kế đơn giản hơn bằng `Function<T,R>`, `UnaryOperator<T>` hoặc một generic API nhận functional interface chuẩn sẽ dễ dùng và an toàn hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-standard-ap-is-007">Đọc chữ ký map, flatMap và collect trong Stream</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc chữ ký map, flatMap và collect trong Stream của Java Generics

## 1. Tại sao cần biết đọc chữ ký generic thay vì chỉ nhớ ví dụ?

`java.util.stream.Stream<T>` mô tả một luồng xử lý các phần tử có kiểu **T**. Ba nhóm API thường xuất hiện cùng nhau:

- **`map`**: biến mỗi T thành một R; tạo một **Stream<R>**.
- **`flatMap`**: biến mỗi T thành một **Stream con**, rồi nối các stream con thành một **Stream<R>**.
- **`collect`**: **terminal operation** gom các phần tử hiện tại của stream vào một kết quả R thông qua một Collector hoặc ba hành vi xây dựng/gộp.

Điểm khó nằm ở các tham số kiểu `T`, `R`, `A` cùng wildcard bounds `? super` và `? extends`. Khi nắm cách đọc, ta có thể hiểu vì sao pipeline biên dịch được hoặc lỗi, compiler suy luận kiểu ở mỗi bước ra sao, và vì sao `map` không tương đương `flatMap`.

Một pipeline điển hình:

~~~java
List<String> names = orders.stream()
        .filter(order -> order.total() > 0)
        .map(Order::customer)
        .collect(Collectors.toList());
~~~

Đây là **mảnh minh họa** giả định đã định nghĩa record `Order` và biến `orders`; chương trình đầy đủ xuất hiện ở phần 9. Kiểu của dòng dữ liệu biến đổi theo từng bước:

~~~text
List<Order>
    .stream()                  Stream<Order>
    .filter(Predicate<Order>)  Stream<Order>
    .map(Function<Order,String>)
                               Stream<String>
    .collect(Collector<String,?,List<String>>)
                               List<String>
~~~

`map` và `flatMap` là **intermediate operations**, thường lazy; `collect` là **terminal operation**, yêu cầu pipeline tạo kết quả. Luồng đã sử dụng terminal operation không được dùng lại như một stream mới.

## 2. Đọc đúng chữ ký thật trong Java 17

Rút gọn từ `java.util.stream.Stream<T>`:

~~~java
<R> Stream<R> map(
        Function<? super T, ? extends R> mapper);

<R> Stream<R> flatMap(
        Function<? super T, ? extends Stream<? extends R>> mapper);

<R, A> R collect(
        Collector<? super T, A, R> collector);

<R> R collect(
        Supplier<R> supplier,
        BiConsumer<R, ? super T> accumulator,
        BiConsumer<R, R> combiner);
~~~

Các chữ ký trên đã bỏ `public abstract` và thông tin khai báo bao quanh để dễ đọc; chúng không phải một class Java độc lập cần copy chạy. Đây là **hai overload khác nhau** của `collect`, cùng tên nhưng nhận loại đối số khác nhau.

Bảng giải mã ký hiệu:

| Ký hiệu | Vai trò | Ví dụ |
| --- | --- | --- |
| `T` | Kiểu phần tử hiện tại của `Stream<T>` | `Order` với `Stream<Order>` |
| `R` trong `map/flatMap` | Kiểu phần tử của stream sau biến đổi | `String` trong `Stream<String>` |
| `R` trong `collect` | Kiểu **kết quả cuối** | `List<String>`, `Map<String,Integer>` |
| `A` | Kiểu tích lũy **trung gian** bên trong Collector | Một mutable container, có thể khác R |
| `Function<? super T, ? extends R>` | Mapper nhận được T và tạo một giá trị tương thích R | `Order::customer` |
| `Stream<? extends R>` | Một stream con với phần tử là R hoặc subtype của R | `List<Integer>.stream()` khi R phù hợp |
| `Collector<? super T,A,R>` | Collector tiêu thụ được các T và tạo R | `Collectors.toList()` |
| `Supplier<R>`, `BiConsumer<...>` | Ba hành vi cấp phát, tích lũy, gộp kết quả | `ArrayList::new`, `List::add`, `List::addAll` |

Chữ `<R>` đặt **trước kiểu trả về** khai báo type parameter của chính generic method. Nó không có nghĩa method trả về một object có class tên R. Compiler suy luận R bằng target context, kiểu của mapper và các generic constraints.

## 3. Quy tắc wildcard: ? super khi nhận vào, ? extends khi tạo ra

Trong chữ ký `Stream<T>.map`:

~~~java
<R> Stream<R> map(Function<? super T, ? extends R> mapper);
~~~

`? super T` cho mapper **nhận** được giá trị `T` qua một supertype, còn `? extends R` cho mapper **trả** kết quả thuộc subtype thích hợp của `R`. Đây là PECS theo hướng input/output của callback, **không** có nghĩa `Stream<Integer>` là subtype của `Stream<Number>`.

Ví dụ trong ngữ cảnh có imports cần thiết:

~~~java
Stream<Integer> integers = Stream.of(1, 2, 3);
Function<Number, String> asText = Object::toString;
List<String> result = integers.map(asText).toList();
~~~

Function nhận `Number` vẫn xử lý được từng `Integer`. Khi tiếp tục đọc `flatMap` và `collect`, hãy giữ đúng hướng này nhưng **phân biệt thêm** mapper trả `Stream<? extends R>` và collector có kiểu tích lũy `A` khác kiểu trả cuối cùng `R`. Đó là trọng tâm riêng của bài Stream, không chỉ lặp lại quy tắc wildcard tổng quát.

## 4. map: một phần tử đầu vào tạo một phần tử đầu ra

Chữ ký:

~~~java
<R> Stream<R> map(Function<? super T, ? extends R> mapper);
~~~

Với mỗi phần tử T mà pipeline cần xử lý, mapper tạo một phần tử kết quả kiểu R. Những phần tử ấy tạo nên một `Stream<R>`.

Ví dụ:

~~~java
List<String> languages = List.of("Java", "Go", "Kotlin");

List<Integer> lengths = languages.stream()
        .map(String::length)
        .toList();
~~~

Kết quả:

~~~text
[4, 2, 6]
~~~

Phân tích kiểu:

~~~text
Stream<String>
    map(String::length)           Function<String,Integer>
                                   (int được boxing thành Integer)
    ↓
Stream<Integer>
    toList()
    ↓
List<Integer>
~~~

`map` giữ lại quan hệ **mỗi phần tử nguồn được biến đổi bằng một lần áp dụng mapper khi phần tử đó được xử lý**; nhưng không nên kết luận mapper luôn được gọi đúng số phần tử nguồn trong mọi pipeline phức tạp. Stream có thể short-circuit hoặc tối ưu một số thao tác theo ngữ nghĩa API.

### 4.1. map khi mapper trả một collection

Ví dụ mỗi đơn hàng giữ một danh sách giá:

~~~java
record Order(String customer, List<Integer> prices) {}

List<Order> orders = List.of(
        new Order("Lan", List.of(20, 30)),
        new Order("An", List.of(10))
);

List<List<Integer>> nested = orders.stream()
        .map(Order::prices)
        .toList();
~~~

Kết quả:

~~~text
[[20, 30], [10]]
~~~

Đầu vào là `Stream<Order>`. `Order::prices` trả `List<Integer>`, nên `R = List<Integer>`; sau map, ta có `Stream<List<Integer>>`, **không phải Stream<Integer>**.

Đây là mấu chốt: **map không tự làm phẳng collection hoặc stream được mapper trả về**. Nếu mapper trả một List, mỗi phần tử kết quả của Stream vẫn là **một List**.

### 4.2. map và mapToInt

Nếu mapper trả một giá trị số nguyên primitive, `map` thường đưa tới `Stream<Integer>` vì type arguments generic không thể là primitive int:

~~~java
Stream<Integer> boxed = Stream.of("A", "BB")
        .map(String::length);
~~~

Trong khi:

~~~java
IntStream lengths = Stream.of("A", "BB")
        .mapToInt(String::length);

int sum = lengths.sum(); // 3
~~~

`mapToInt(ToIntFunction<? super T>)` trả `IntStream`, một primitive stream chuyên biệt. Không nên dùng `map(String::length)` rồi mong gọi trực tiếp mọi API primitive giống hệt IntStream.

## 5. flatMap: mỗi T tạo một stream con, sau đó làm phẳng một mức

Chữ ký:

~~~java
<R> Stream<R> flatMap(
        Function<? super T, ? extends Stream<? extends R>> mapper);
~~~

Khác với map, mapper của flatMap phải trả về một **Stream** (hoặc subtype phù hợp), không phải một R đơn lẻ.

Ví dụ:

~~~java
List<List<Integer>> batches = List.of(
        List.of(1, 2),
        List.of(3),
        List.of(4, 5)
);

List<Integer> numbers = batches.stream()
        .flatMap(List::stream)
        .toList();
~~~

Kết quả:

~~~text
[1, 2, 3, 4, 5]
~~~

Kiểu tại từng bước:

~~~text
Stream<List<Integer>>
    flatMap(List::stream)
        List<Integer> → Stream<Integer>
    ↓
Stream<Integer>
    toList()
    ↓
List<Integer>
~~~

`flatMap` có thể trả **0, 1 hoặc nhiều phần tử đầu ra cho một phần tử đầu vào**, tùy stream con. Điều này giúp dùng khi xử lý nhiều danh sách, chuỗi từ vựng, các bản ghi con hoặc kết quả tìm kiếm chứa nhiều items.

### 5.1. Không phải flatten đệ quy vô hạn

Nếu đầu vào là `Stream<List<List<String>>>`, chỉ gọi một lần `flatMap(List::stream)` sẽ tạo `Stream<List<String>>`. Muốn thành `Stream<String>` cần làm phẳng thêm một mức:

~~~java
List<List<List<String>>> levels = List.of(
        List.of(List.of("A", "B")),
        List.of(List.of("C"))
);

List<String> words = levels.stream()
        .flatMap(List::stream)
        .flatMap(List::stream)
        .toList();
~~~

Kết quả `[A, B, C]`. Hai lần flatMap xử lý hai mức cấu trúc lồng nhau; không có quy tắc tự động “làm phẳng bao nhiêu lớp cũng được”.

### 5.2. flatMap có thể loại phần tử bằng Stream.empty()

~~~java
List<String> source = List.of("A", "", "B");

List<String> nonBlank = source.stream()
        .flatMap(s -> s.isBlank()
                ? Stream.empty()
                : Stream.of(s))
        .toList();
~~~

Kết quả `[A, B]`. Với mỗi chuỗi trống, mapper trả stream rỗng nên không tạo phần tử đầu ra. Nếu bài toán đơn thuần là loại bỏ, `filter` thường dễ đọc hơn; ví dụ này giúp thấy khả năng một đầu vào tạo **0 phần tử** của flatMap.

### 5.3. Tại sao không trả trực tiếp List?

Sai kiểu:

~~~java
// orders.stream().flatMap(Order::prices);
// Không biên dịch: Order::prices trả List<Integer>, không phải Stream<Integer>.
~~~

Cách sửa:

~~~java
orders.stream().flatMap(order -> order.prices().stream());
~~~

`List.stream()` tạo Stream<Integer>, nên đáp ứng chữ ký Function nhận Order và trả Stream<? extends R>.

### 5.4. flatMap xử lý stream con thế nào?

Khi một mapped stream được sử dụng trong flatMap, Java sẽ xử lý phần tử của stream con và **đóng stream con sau khi sử dụng** theo hợp đồng API. Nếu mapper trả null stream, hợp đồng Stream.flatMap xử lý như một stream rỗng; tuy vậy trong code ứng dụng, trả rõ `Stream.empty()` giúp ý định sáng hơn và tránh phụ thuộc vào null.

Một stream không được tái sử dụng sau terminal operation; tương tự, đừng tái sử dụng một stream con đã bị tiêu thụ để map một phần tử khác. Với dữ liệu từ collection, gọi `list.stream()` mới mỗi lần là tự nhiên.

## 6. Bảng so sánh map và flatMap bằng kiểu

| Đầu vào | Mapper | API | Sau phép biến đổi |
| --- | --- | --- | --- |
| `Stream<String>` | `String::length` trả int/Integer | `map` | `Stream<Integer>` |
| `Stream<Order>` | `Order::customer` trả String | `map` | `Stream<String>` |
| `Stream<Order>` | `Order::prices` trả List<Integer> | `map` | `Stream<List<Integer>>` |
| `Stream<Order>` | `o -> o.prices().stream()` trả Stream<Integer> | `flatMap` | `Stream<Integer>` |
| `Stream<List<String>>` | `List::stream` trả Stream<String> | `flatMap` | `Stream<String>` |
| `Stream<String>` | `s -> Stream.of(s, s)` | `flatMap` | `Stream<String>` với hai kết quả cho mỗi đầu vào |

Câu hỏi chẩn đoán trước khi chọn API: “Mapper của mình trả **một R** hay trả **một Stream<R>**?” Nếu trả một kết quả, dùng map; nếu trả stream con và muốn gộp các phần tử của chúng, dùng flatMap.

Một phương thức trả Optional cũng có thể cung cấp stream con qua `Optional.stream()` (Java 9+):

~~~java
List<String> present = Stream.of(
        Optional.of("A"),
        Optional.<String>empty(),
        Optional.of("B"))
        .flatMap(Optional::stream)
        .toList(); // [A, B]
~~~

Cách này làm phẳng Stream<Optional<String>> thành Stream<String>, bỏ qua các Optional rỗng.

## 7. collect là terminal operation và khác map/flatMap

`map`, `flatMap` chỉ biến đổi một `Stream<T>` thành stream khác; chúng không tự gom các phần tử vào danh sách hoặc map.

`collect` là một **terminal reduction**: dùng các phần tử của stream để tạo kết quả. Có **hai overload** chính trong `Stream<T>`:

~~~java
<R, A> R collect(Collector<? super T, A, R> collector);

<R> R collect(
        Supplier<R> supplier,
        BiConsumer<R, ? super T> accumulator,
        BiConsumer<R, R> combiner);
~~~

Ở overload đầu, một **Collector** đóng gói logic tích lũy, kết hợp, hoàn thiện kết quả và characteristics. Overload thứ hai nhận trực tiếp **ba hành vi** dùng để tạo và gộp kết quả mutable.

Điểm thường bị đọc sai: **R của collect không nhất thiết là kiểu phần tử đầu vào**. Từ `Stream<Order>`, một collector có thể tạo:
- `List<Order>`;
- `Map<String,List<Order>>`;
- `Map<String,Integer>`;
- `Long`;
- `String`;
- một kiểu kết quả riêng của ứng dụng.

Hãy theo dõi **T của stream hiện tại** và **R được Collector tạo ra** thay vì suy ra R luôn là List<T>.

## 8. collect(Collector<? super T,A,R>): ý nghĩa của ba tham số kiểu

Chữ ký:

~~~java
<R, A> R collect(Collector<? super T, A, R> collector);
~~~

Giải mã:

- `T`: phần tử đang được xử lý trong stream.
- `? super T`: collector có thể tiêu thụ T **hoặc một supertype của T**.
- `A`: kiểu **container tích lũy trung gian** (*mutable accumulation type*) mà Collector quản lý. Người gọi nhiều khi không cần biết nó và API có thể biểu diễn bằng `?`.
- `R`: kiểu **kết quả cuối** mà `collect` trả về.

Ví dụ:

~~~java
List<String> names = Stream.of("Java", "Go")
        .collect(Collectors.toList());
~~~

`Collectors.toList()` có chữ ký rút gọn:

~~~java
<T> Collector<T, ?, List<T>> toList();
~~~

Dấu `?` ở vị trí A có nghĩa implementation che loại accumulator cụ thể; không phải Java không biết gì về dữ liệu. Kết quả R là `List<T>`, ở ví dụ này là `List<String>`.

### 8.1. A có thể khác R

Với collector có bước *finisher*, container tích lũy bên trong và kết quả công khai có thể thuộc các kiểu khác nhau. Không nên khẳng định `Collector<T,A,R>` luôn cần `A=R`.

Một collector có thể dùng một mutable builder như `StringBuilder` để tích lũy rồi trả một `String` sau khi hoàn tất. Cơ chế Collector gồm:
- supplier: tạo A;
- accumulator: nhận A và T để tích lũy;
- combiner: ghép hai A;
- finisher: chuyển A sang R;
- characteristics: mô tả các tính chất hỗ trợ tối ưu hoặc thực thi song song theo hợp đồng.

`Collectors.joining()` là một ví dụ dễ hình dung: gom các CharSequence để cho String kết quả mà không bắt caller quản lý builder.

### 8.2. Tại sao collector nhận ? super T?

Giống `map`, collector có thể tiêu thụ một supertype của stream element type. Khi API viết `Collector<? super T,A,R>`, nó tránh yêu cầu mọi collector phải khai báo đúng T tuyệt đối. Đây là một biểu hiện nữa của quy tắc **consumer super**.

Đừng suy luận dấu wildcard `?` trong `Collector<T,?,List<T>>` mang nghĩa “List đầu ra chứa Object”. Dấu hỏi đó đứng ở **A — accumulator nội bộ**, còn kết quả List vẫn giữ T.

## 9. Chương trình Java 17+ đầy đủ minh họa map, flatMap và collect

Lưu tệp **StreamSignaturesDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamSignaturesDemo {
    record Order(String customer, List<Integer> prices) {
        int total() {
            return prices.stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }
    }

    public static void main(String[] args) {
        List<Order> orders = List.of(
                new Order("Lan", List.of(20, 30)),
                new Order("An", List.of(10)),
                new Order("Lan", List.of(5, 15))
        );

        List<String> labels = orders.stream()
                .map(o -> o.customer() + ":" + o.prices().size())
                .toList();
        System.out.println("map=" + labels);

        List<List<Integer>> nested = orders.stream()
                .map(Order::prices)
                .toList();
        System.out.println("nested=" + nested);

        List<Integer> flattened = orders.stream()
                .flatMap(o -> o.prices().stream())
                .toList();
        System.out.println("flatMap=" + flattened);

        // Overload collect(supplier, accumulator, combiner).
        List<String> threeArg = orders.stream()
                .map(Order::customer)
                .collect(ArrayList::new, List::add, List::addAll);
        System.out.println("collect3=" + threeArg);

        // Collector mapping: biến Order thành Integer bên trong collect.
        List<Integer> totals = orders.stream()
                .collect(Collectors.mapping(
                        Order::total, Collectors.toList()
                ));
        System.out.println("mapping=" + totals);

        // Collector flatMapping: làm phẳng phần tử trước downstream.
        List<Integer> withFlatMapping = orders.stream()
                .collect(Collectors.flatMapping(
                        o -> o.prices().stream(),
                        Collectors.toList()
                ));
        System.out.println("flatMapping=" + withFlatMapping);

        // TreeMap để thứ tự key trong output được xác định.
        Map<String, Integer> grouped = orders.stream()
                .collect(Collectors.groupingBy(
                        Order::customer,
                        TreeMap::new,
                        Collectors.summingInt(Order::total)
                ));
        System.out.println("grouped=" + grouped);

        Map<String, Integer> merged = orders.stream()
                .collect(Collectors.toMap(
                        Order::customer,
                        Order::total,
                        Integer::sum,
                        TreeMap::new
                ));
        System.out.println("toMap=" + merged);

        try {
            // Trùng key "Lan" mà không cung cấp merge function.
            orders.stream().collect(Collectors.toMap(
                    Order::customer, Order::total
            ));
        } catch (IllegalStateException error) {
            System.out.println("duplicate=IllegalStateException");
        }

        Function<CharSequence, String> trim =
                s -> s.toString().trim();

        List<String> cleaned = Stream.of(" Lan ", " An ")
                .map(trim)
                .toList();
        System.out.println("wildcard=" + cleaned);

        try {
            labels.add("new");
        } catch (UnsupportedOperationException error) {
            System.out.println("toList=unmodifiable");
        }
    }
}
~~~

Biên dịch với JDK hỗ trợ Java 17:

~~~bash
javac --release 17 -Xlint:all StreamSignaturesDemo.java
java StreamSignaturesDemo
~~~

**Stdout đã được kiểm chứng:**

~~~text
map=[Lan:2, An:1, Lan:2]
nested=[[20, 30], [10], [5, 15]]
flatMap=[20, 30, 10, 5, 15]
collect3=[Lan, An, Lan]
mapping=[50, 10, 20]
flatMapping=[20, 30, 10, 5, 15]
grouped={An=10, Lan=70}
toMap={An=10, Lan=70}
duplicate=IllegalStateException
wildcard=[Lan, An]
toList=unmodifiable
~~~

### 9.1. Phân tích kiểu từng dòng kết quả

`map=[Lan:2, An:1, Lan:2]`: biến `Order` thành `String`; số phần tử giữ nguyên ở tình huống này.

`nested=[[20, 30], [10], [5, 15]]`: `Order::prices` trả `List<Integer>` nên `map` cho `Stream<List<Integer>>`; thu thành `List<List<Integer>>`.

`flatMap=[20, 30, 10, 5, 15]`: mapper `o -> o.prices().stream()` trả `Stream<Integer>`, nên `flatMap` nối các stream con thành `Stream<Integer>`.

`collect3=[Lan, An, Lan]`: sau `map(Order::customer)`, stream hiện tại chứa String; overload ba tham số tạo ArrayList<String>, thêm từng String rồi gộp các container khi cần.

`mapping=[50, 10, 20]`: mỗi Order được biến thành tổng tiền Integer bằng `Order::total` trong `Collectors.mapping`, sau đó downstream `toList` gom kết quả.

`flatMapping=[20, 30, 10, 5, 15]`: `Collectors.flatMapping` dùng stream con của từng order như bước chuẩn bị dữ liệu trước collector downstream.

`grouped={An=10, Lan=70}`: `groupingBy` phân nhóm theo customer, `summingInt` cộng tổng của các order cùng key, TreeMap sắp key theo thứ tự tự nhiên.

`toMap={An=10, Lan=70}`: `toMap` dùng customer làm key, total làm value và `Integer::sum` gộp các value khi trùng key; TreeMap giữ thứ tự key xác định.

`duplicate=IllegalStateException`: overload hai mapper của `toMap` không có merge function được cung cấp, nên dữ liệu có key "Lan" lặp gây ngoại lệ khi collect.

`wildcard=[Lan, An]`: `Function<CharSequence,String>` có thể được dùng làm mapper cho `Stream<String>` nhờ tham số `? super T` trong chữ ký map.

`toList=unmodifiable`: `Stream.toList()` từ Java 16 trả danh sách không cho phép sửa đổi; gọi add ném UnsupportedOperationException.

## 10. Overload collect(supplier, accumulator, combiner)

Chữ ký:

~~~java
<R> R collect(
        Supplier<R> supplier,
        BiConsumer<R, ? super T> accumulator,
        BiConsumer<R, R> combiner);
~~~

Ở overload này **không có A riêng**: kiểu container tích lũy được khai báo chính là R, cũng là kiểu kết quả trả về.

Đọc từng thành phần:
- `supplier`: tạo **container R mới** để tích lũy.
- `accumulator`: nhận container R và một phần tử T; thay đổi container để thêm dữ liệu.
- `combiner`: kết hợp hai container R; đặc biệt quan trọng khi engine cần gộp các kết quả từng nhánh của parallel stream.

Ví dụ:

~~~java
List<String> output = Stream.of("A", "B", "C")
        .collect(
                ArrayList::new,
                List::add,
                List::addAll
        );
~~~

Kiểu suy luận:
- Input `Stream<String>`: T = String.
- `ArrayList::new` cấp phát một ArrayList<String> có thể được nhìn dưới dạng List<String>.
- `List::add` chấp nhận container List<String> và phần tử String (giá trị trả boolean bị bỏ qua khi dùng làm BiConsumer).
- `List::addAll` kết hợp hai List<String>, thêm phần tử của container thứ hai vào container thứ nhất.
- Result R = `List<String>`.

**Điều kiện correctness:** combiner phải kết hợp đúng, và supplier/accumulator/combiner cần tuân thủ các ràng buộc của mutable reduction. Nếu combiner chỉ bỏ qua container thứ hai, kết quả có thể trông đúng ở tuần tự nhưng thiếu phần tử khi chạy parallel.

### 10.1. Vì sao không chỉ dùng một ArrayList global?

Sai lầm thường gặp:

~~~java
List<String> shared = new ArrayList<>();
// stream.parallel().forEach(shared::add); // Không an toàn.
~~~

`ArrayList` không thread-safe để nhiều thread cùng thay đổi qua `forEach` không đồng bộ. Trong overload collect ba tham số, framework có thể tạo các container riêng bằng supplier và dùng combiner để kết hợp; đây là cơ chế reduction được thiết kế cho môi trường parallel khi các ràng buộc về đồng nhất và liên kết được tuân thủ.

Không nên truyền một supplier luôn trả cùng một mutable global list, vì như vậy phá ý tưởng các container tích lũy độc lập.

### 10.2. Vì sao combiner không dùng để biến T sang R?

Trong chữ ký, `BiConsumer<R,R> combiner` nhận **hai container R**, không nhận phần tử T. Việc xử lý từng T thuộc `BiConsumer<R,? super T> accumulator`. Nếu cần biến đổi T thành R theo từng phần tử, dùng map trước hoặc xử lý trong accumulator có kiểm soát.

## 11. Collectors.toList và Stream.toList khác nhau

Hai cách thu về danh sách:

~~~java
List<String> a = Stream.of("A", "B")
        .collect(Collectors.toList());

List<String> b = Stream.of("A", "B")
        .toList();
~~~

Chúng có cùng **kiểu kết quả** `List<String>` và cùng phần tử trên stream có encounter order tương ứng, nhưng không có cùng mọi cam kết API.

- `Stream.toList()` có từ Java 16; trả **unmodifiable List**. Cố gắng thêm, xóa hoặc thay phần tử qua các thao tác sửa đổi sẽ ném UnsupportedOperationException.
- `Collectors.toList()` không bảo đảm cụ thể về kiểu implementation, mutability, serializability hoặc thread safety của List kết quả. Trên một số JDK có thể thấy ArrayList mutable, nhưng **không được xem đây là hợp đồng chuẩn**.
- `Collectors.toCollection(ArrayList::new)` dùng khi cần collection cụ thể là ArrayList do factory chỉ định.
- `Collectors.toUnmodifiableList()` cung cấp unmodifiable List qua collector; phương thức này không chấp nhận phần tử null theo hợp đồng.

Ví dụ:

~~~java
List<String> mutable = Stream.of("A", "B")
        .collect(Collectors.toCollection(ArrayList::new));

mutable.add("C"); // Hợp lệ với ArrayList.
~~~

Đừng chọn `Collectors.toList()` chỉ vì muốn sửa kết quả nếu API thực sự đòi một ArrayList mutable; hãy thể hiện yêu cầu bằng `toCollection`.

## 12. groupingBy: đọc classifier, mapFactory và downstream

Overload cơ bản của `Collectors.groupingBy` có thể được mô tả như sau:

~~~java
<T, K> Collector<T, ?, Map<K, List<T>>> groupingBy(
        Function<? super T, ? extends K> classifier);
~~~

Nó nhóm các phần tử T theo **khóa K** do classifier tạo, và trả Map<K,List<T>>.

Ví dụ:

~~~java
Map<Integer, List<String>> byLength = Stream.of("A", "BB", "C")
        .collect(Collectors.groupingBy(String::length));
~~~

Kiểu:
- T = String.
- K = Integer.
- classifier: String → Integer.
- R = Map<Integer,List<String>>.

Nếu cần một kết quả downstream không phải List<T>, dùng overload có Collector:

~~~java
<T, K, A, D> Collector<T, ?, Map<K, D>> groupingBy(
        Function<? super T, ? extends K> classifier,
        Collector<? super T, A, D> downstream);
~~~

Ở đây `D` là kết quả của **mỗi nhóm**, có thể là Long, Integer, Set<String>, hoặc một cấu trúc khác. Vì thế toàn bộ kết quả là `Map<K,D>`.

Ví dụ đếm mỗi nhóm:

~~~java
Map<Integer, Long> counts = Stream.of("A", "BB", "C")
        .collect(Collectors.groupingBy(
                String::length,
                Collectors.counting()
        ));
~~~

Kết quả theo nội dung: key 1 có count 2, key 2 có count 1. Không dựa vào thứ tự in của HashMap ở ví dụ này.

Chương trình đầy đủ dùng overload ba đối số:

~~~java
Collectors.groupingBy(
        Order::customer,
        TreeMap::new,
        Collectors.summingInt(Order::total)
)
~~~

Ý nghĩa:
- classifier lấy customer:String → K = String;
- mapFactory tạo TreeMap<String,Integer>;
- downstream cộng total của từng nhóm → D = Integer;
- result `Map<String,Integer>` được in ổn định theo key TreeMap.

Điểm rất hay bị hiểu sai: `groupingBy(classifier, downstream)` không đòi downstream nhận **một List<T> đã nhóm sẵn**; collector downstream được dùng để tích lũy những phần tử T thuộc từng nhóm.

## 13. Collectors.mapping và Collectors.flatMapping không phải Stream.map/flatMap

Hai API khác nhau:

~~~java
orders.stream()
      .map(Order::total)
      .collect(Collectors.toList());
~~~

và:

~~~java
orders.stream()
      .collect(Collectors.mapping(
              Order::total,
              Collectors.toList()
      ));
~~~

đều có thể tạo danh sách các tổng của từng order. Nhưng:
- `Stream.map` biến **stream trung gian** từ Stream<Order> thành Stream<Integer>.
- `Collectors.mapping` là **adapter collector**: nó biến từng phần tử Order thành Integer khi collector downstream nhận dữ liệu.

Một chữ ký rút gọn:

~~~java
<T, U, A, R> Collector<T, ?, R> mapping(
        Function<? super T, ? extends U> mapper,
        Collector<? super U, A, R> downstream);
~~~

Mapper nhận T và tạo U; downstream thu U để trả R.

Tương tự:

~~~java
<T, U, A, R> Collector<T, ?, R> flatMapping(
        Function<? super T, ? extends Stream<? extends U>> mapper,
        Collector<? super U, A, R> downstream);
~~~

`Collectors.flatMapping` làm phẳng stream con **ngay trong quá trình collect**, hữu ích khi nó là một downstream collector của groupingBy hoặc partitioningBy.

Ví dụ muốn lấy set các số nguyên trong mỗi nhóm customer:

~~~java
Map<String, Set<Integer>> valuesByCustomer = orders.stream()
        .collect(Collectors.groupingBy(
                Order::customer,
                Collectors.flatMapping(
                        o -> o.prices().stream(),
                        Collectors.toSet()
                )
        ));
~~~

Phần này giả định import `java.util.Set` và kiểu Order của chương trình đầy đủ. Mỗi nhóm customer nhận các Order, flatMapping tạo Integer từ các list giá, và downstream `toSet` gom các Integer, không cần tạo Stream trung gian để làm phẳng toàn bộ rồi cố dựng lại mối quan hệ customer.

**Lưu ý:** đây là hai mức API khác nhau; đừng cố gọi `.flatMap(Collectors.flatMapping(...))` vì `Stream.flatMap` cần Function trả Stream, còn `Collectors.flatMapping` trả một Collector.

## 14. Collectors.toMap: hai mapper và bài toán trùng khóa

Overload cơ bản:

~~~java
<T, K, U> Collector<T, ?, Map<K, U>> toMap(
        Function<? super T, ? extends K> keyMapper,
        Function<? super T, ? extends U> valueMapper);
~~~

Tức là với mỗi phần tử T:
- keyMapper sinh K;
- valueMapper sinh U;
- kết quả gom thành Map<K,U>.

Ví dụ:

~~~java
Map<String, Integer> values = Stream.of("A", "BB")
        .collect(Collectors.toMap(
                word -> word,
                String::length
        ));
~~~

Đầu vào `Stream<String>`, khóa String, giá trị Integer. Nhưng nếu có **hai phần tử tạo cùng key**, overload này ném IllegalStateException.

Trong chương trình ở phần 9, customer "Lan" xuất hiện hai lần. Vì vậy:

~~~java
orders.stream().collect(Collectors.toMap(
        Order::customer, Order::total
));
~~~

bị IllegalStateException do trùng khóa.

Muốn quyết định cách gộp value khi trùng khóa:

~~~java
Map<String, Integer> merged = orders.stream()
        .collect(Collectors.toMap(
                Order::customer,
                Order::total,
                Integer::sum,
                TreeMap::new
        ));
~~~

Ở overload này:
- `BinaryOperator<U> mergeFunction` kết hợp **hai giá trị U** của cùng key, không kết hợp hai đối tượng Order.
- `Supplier<M> mapFactory` chọn loại Map cần tạo; TreeMap giúp thứ tự key xác định.
- Kết quả có `Lan = 50 + 20 = 70`, `An = 10`.

Nếu dữ liệu lớn và parallel, merge function nên phù hợp các yêu cầu associativity và không gây các side effects khiến kết quả thiếu nhất quán. Chọn merge policy thể hiện nghiệp vụ: lấy đầu, lấy cuối, cộng, max, min, hoặc báo lỗi — không tùy tiện ghép string nếu thứ tự không được bảo đảm.

### 14.1. groupingBy so với toMap

`groupingBy` thường hợp khi muốn **nhóm nhiều phần tử theo khóa** và dùng downstream reductions. `toMap` thường hợp khi muốn **một giá trị U cho mỗi khóa K** và có chính sách rõ ràng khi trùng khóa.

Cả hai có thể tạo Map<String,Integer> tính tổng như chương trình demo, nhưng cấu trúc code khác:
- groupingBy + summingInt mô tả “nhóm rồi cộng mỗi nhóm”.
- toMap + Integer::sum mô tả “đưa từng key/value vào map và merge giá trị trùng key”.

Không có một cách luôn tốt hơn; hãy chọn cách diễn đạt đúng mục đích và ràng buộc về key, null, ordering, parallelism.

## 15. Biến kiểu trong một pipeline thay đổi sau mỗi phép map/flatMap

Xét:

~~~java
Stream<Order> first = orders.stream();

Stream<String> second = first.map(Order::customer);
~~~

Sau phép map, stream mới chứa String. Nếu muốn `map(Order::total)` trên stream `second`, compiler sẽ từ chối vì mapper đó cần Order chứ không phải String.

**Đừng tái sử dụng `first` sau intermediate/terminal chain** như thể mọi stream có thể chạy nhiều lần. Java streams là các pipeline tiêu thụ một lần; cách viết trên chỉ giúp nhìn kiểu. Trong code thật nên tạo pipeline mới từ collection nguồn cho một terminal operation mới.

Một ví dụ chuỗi đúng kiểu:

~~~java
List<Integer> lengths = orders.stream()
        .map(Order::customer)  // Stream<String>
        .map(String::length)   // Stream<Integer>
        .toList();             // List<Integer>
~~~

Cách đọc lỗi kiểu:
1. Ghi T của stream trước phép biến đổi.
2. Xem mapper nhận gì, trả gì.
3. Suy ra R và kiểu stream sau biến đổi.
4. Đối chiếu kiểu R đó với phép tiếp theo.
5. Ở collect, xem collector tiêu thụ kiểu nào và trả kiểu R tổng kết nào.

Compiler đôi lúc đưa lỗi dài về inference variable hoặc incompatible bounds; việc đặt biến trung gian với kiểu tường minh có thể giúp xác định bước nào sai.

## 16. Compiler inference và type witness khi generics trở nên khó đọc

Ví dụ từ `Stream.of`:

~~~java
Stream<String> names = Stream.of("A", "B");
List<Integer> sizes = names.map(String::length).toList();
~~~

Compiler suy luận T=String từ các literal và R=Integer từ mapper. Đa số code không cần tự viết type witness.

Nhưng với các mapper trả generic collection, những bước suy luận phức tạp có thể khiến compiler suy ra một supertype rộng hoặc báo lỗi nếu hai nhánh lambda không thống nhất:

~~~java
// Nếu muốn mọi phần tử đầu ra có kiểu CharSequence:
List<CharSequence> output = Stream.of("A", "B")
        .<CharSequence>map(s -> s)
        .toList();
~~~

`.<CharSequence>map(...)` chỉ rõ type argument R của generic method map. Đây là **explicit type witness**; dùng khi cần kiểm soát kiểu đầu ra hoặc hỗ trợ compiler trong tình huống khó suy luận.

Tuy nhiên, không dùng type witness để che một mapper sai thật. Ví dụ `flatMap` vẫn cần function trả Stream; khai báo R không thể biến một List tự động thành Stream.

Một lời nhắc khác: `var` chỉ yêu cầu compiler suy ra kiểu biểu thức, không biến một type thành raw. Nếu viết:

~~~java
var nested = orders.stream().map(Order::prices).toList();
~~~

kiểu được suy luận tương ứng `List<List<Integer>>` trong ví dụ; nó **không tự làm phẳng** chỉ vì không ghi kiểu tường minh.

## 17. Laziness, terminal operation và không được dùng lại Stream

`map` và `flatMap` thường không chạy mapper ngay khi khai báo pipeline:

~~~java
Stream<String> pipeline = Stream.of("A", "B")
        .map(s -> {
            System.out.println("mapped=" + s);
            return s.toLowerCase();
        });
~~~

Khi chỉ tạo `pipeline`, không có yêu cầu tính toàn bộ kết quả. Khi gọi:

~~~java
List<String> result = pipeline.toList();
~~~

pipeline được thực thi để tạo danh sách. Đừng bắt đầu một terminal operation thứ hai trên `pipeline` sau khi đã tiêu thụ nó.

Cần phân biệt:
- **Intermediate:** `map`, `flatMap`, `filter`, `peek`, `sorted`...
- **Terminal:** `collect`, `toList`, `forEach`, `count`, `reduce`, `findFirst`...

Các thao tác có thể được stream engine fuse hoặc tối ưu; đừng dựa vào side effects của mapper để đếm số lần xử lý trong mọi pipeline. Đặc biệt với short-circuit operations, có thể không cần chạy mapper trên mọi phần tử.

### flatMap và tài nguyên stream con

Nếu một mapper trả stream được xây từ nguồn tài nguyên cần đóng, cần hiểu trách nhiệm đóng tài nguyên của stream cha và mapped streams. `flatMap` đóng từng mapped stream sau khi sử dụng, nhưng quản lý nguồn như file lines, IO hoặc connection vẫn đòi thiết kế đúng và xử lý lỗi theo API nguồn. Không nên mở tài nguyên rồi giả định mọi pipeline chưa terminal sẽ tự giải phóng chúng đúng lúc.

## 18. Parallel Stream và Collector: đọc chữ ký chưa đủ cho correctness

Với `parallelStream`, collector có thể thực hiện accumulation theo nhiều nhánh và combine kết quả. Các ràng buộc quan trọng:

- Mapper/flatMapper nên **non-interfering**: không thay đổi nguồn stream trong khi xử lý.
- Lambda có state chia sẻ phải được đồng bộ phù hợp; nếu không, kết quả có thể race hoặc sai.
- `collect(supplier,accumulator,combiner)` đòi hành vi phù hợp với **identity/associativity** của mutable reduction.
- `groupingBy` thường không phải concurrent collector; khi cần shared concurrent accumulation, cân nhắc API như `groupingByConcurrent` với đúng đặc tính và yêu cầu thứ tự.
- Không kết luận rằng một Collector trả ArrayList nghĩa là framework cho nhiều thread đồng thời ghi vào **cùng một ArrayList không đồng bộ**. Reduction có thể dùng container riêng và kết hợp, theo hợp đồng collector.

Ví dụ thiết kế sai:

~~~java
List<String> global = new ArrayList<>();

// Dùng như sau là ý tưởng không an toàn cho parallel stream:
// orders.parallelStream().map(Order::customer).forEach(global::add);
~~~

Thay bằng terminal collection phù hợp:

~~~java
List<String> customers = orders.parallelStream()
        .map(Order::customer)
        .toList();
~~~

Khi cần thứ tự, xem rõ encounter order và semantics của collector. `TreeMap` trong chương trình demo chọn thứ tự key được xác định, không có nghĩa mọi stream parallel sẽ có thứ tự mọi side effect được bảo đảm.

## 19. Null và duplicate trong các Collector phổ biến

**Với `toMap`:** trong code mới, nên coi null keys/values là vấn đề cần hợp đồng rõ. Các collector/map implementation cụ thể có giới hạn riêng; tránh dựa vào việc một overload chấp nhận null trên một bản JDK rồi coi đó là API chung. Đặc biệt, các bản Java chuẩn có thể từ chối null values trong `Collectors.toMap` với NullPointerException.

**Với `groupingBy`:** classifier trả null key có thể bị implementation chuẩn của groupingBy từ chối; tốt hơn hãy chuẩn hóa key sang một giá trị cụ thể được nghiệp vụ chấp nhận.

**Với `toUnmodifiableList`:** phần tử null bị từ chối. `Stream.toList()` có hợp đồng List không cho sửa đổi; đừng nhầm điều đó với một validator nội dung tổng quát.

**Với duplicate keys:** `toMap(keyMapper,valueMapper)` không tự quyết định cộng hoặc lấy value đầu/last. Nó ném IllegalStateException cho duplicate, nên nếu key có thể lặp phải chọn overload có mergeFunction hoặc dùng groupingBy.

**Với `Collectors.toList`:** không hứa List kết quả mutable hay có lớp triển khai cụ thể. Cần ArrayList mutable thì dùng `toCollection(ArrayList::new)`.

Chính sách cụ thể cho null, duplicates, ordering và mutability là một phần của **thiết kế API**, không phải chuyện có thể giải quyết chỉ bằng generic type arguments.

## 20. Lỗi thường gặp khi đọc ba chữ ký

### 20.1. Dùng map nhưng tưởng trả stream phẳng

~~~java
Stream<List<Integer>> nested = orders.stream().map(Order::prices);

// Stream<Integer> wrong = orders.stream().map(Order::prices);
// Không biên dịch: mỗi mapper trả List<Integer>.
~~~

Sửa bằng `flatMap(o -> o.prices().stream())`.

### 20.2. Dùng flatMap với mapper chỉ trả List

~~~java
// orders.stream().flatMap(Order::prices); // Không hợp lệ.
~~~

Cần trả `Stream<? extends R>`. Sửa bằng `o -> o.prices().stream()`.

### 20.3. Coi A trong Collector<T,A,R> là kiểu output

`A` là loại container tích lũy trung gian, `R` mới là kiểu kết quả. `Collector<T,?,List<T>>` có dấu `?` ở A không làm đầu ra trở thành List<?>.

### 20.4. Coi R của map và R của collect là một biến cố định

Chúng là các type parameters **riêng trong mỗi generic method invocation**. `map` có thể cho `Stream<Integer>`; sau đó `collect` có thể trả `Map<String,Long>` hoặc `List<Integer>` tùy collector và pipeline. Không có một R chung tồn tại xuyên tất cả methods.

### 20.5. Dùng BiConsumer trong collect rồi mong có return value

Trong overload ba tham số, accumulator/combiner là **BiConsumer** và có return type void. Các method references `List::add`, `List::addAll` vốn trả boolean vẫn có thể dùng theo target void-compatible trong ngữ cảnh này; giá trị boolean bị bỏ qua. Logic phải sửa container, không phải trả container khác rồi mong framework tự dùng giá trị trả về.

### 20.6. Thiếu combiner đúng cho parallel stream

Một combiner rỗng có thể trông không sai khi code chạy tuần tự nhưng cho kết quả sai ở parallel stream. Chọn những phép gộp thỏa luật kết hợp và identity, hoặc dùng collector chuẩn.

### 20.7. Dùng toMap với key trùng

~~~java
// orders.stream().collect(Collectors.toMap(
//         Order::customer, Order::total));
// Có thể ném IllegalStateException khi customer lặp.
~~~

Chọn mergeFunction rõ ràng như `Integer::sum` hoặc groupingBy.

### 20.8. Mặc định Collectors.toList trả ArrayList mutable

Đây không phải bảo đảm của API. Dùng `toCollection(ArrayList::new)` khi cần loại collection cụ thể.

### 20.9. Tái dùng stream sau terminal operation

~~~java
Stream<String> names = Stream.of("A", "B");
names.collect(Collectors.toList());
// names.count(); // Không dùng lại stream đã tiêu thụ.
~~~

Tạo stream mới từ nguồn khi cần phép xử lý tiếp theo.

### 20.10. Quên primitive streams

`mapToInt`, `flatMapToInt`, `mapToLong`... khác chữ ký với `map/flatMap` thông thường. Nếu mục tiêu là phép cộng số nguyên, primitive streams có thể đơn giản và tránh boxing; nhưng không phải cách chọn mặc định cho mọi loại dữ liệu.

## 21. Quy trình tự đọc một pipeline phức tạp

Khi nhìn thấy một chuỗi `stream().filter(...).flatMap(...).map(...).collect(...)`, thực hiện theo thứ tự:

1. **Ghi T ban đầu**: chẳng hạn `Stream<Order>`.
2. **Đọc filter**: Predicate nhận được Order, stream vẫn là Stream<Order>.
3. **Đọc flatMap**: function nhận Order và trả Stream<Integer>; sau đó stream là Stream<Integer>.
4. **Đọc map**: nếu mapping Integer → String, stream là Stream<String>.
5. **Đọc collect**: collector nhận được String và trả R, ví dụ List<String> hoặc Map<String,Long>.
6. **Chú ý wildcards**: mapper có thể nhận supertype; đầu ra là subtype của R; đừng thay nó bằng cast/raw type.
7. **Nếu gặp lỗi compiler**: đặt biến intermediate có kiểu tường minh để cô lập bước inference gây lỗi.
8. **Nếu collector phức tạp**: ghi T, A, R ở mỗi collector và downstream; A thường bị ẩn bằng `?`.
9. **Kiểm tra tính đúng đắn khác type safety**: null, duplicate keys, ordering, parallel, side effects, mutability.
10. **Xác nhận bằng javac** với source release dự án. Một ví dụ Java 17 có thể dùng `Stream.toList()`, nhưng dự án Java 8 cần `collect(Collectors.toList())` hoặc cách phù hợp khác.

Đọc chữ ký method là bước đầu; hiểu hợp đồng của terminal operation và collector mới giúp biết **kết quả thực sự được tạo ra như thế nào**.

## 22. Câu hỏi tự kiểm tra

**1.** Với `Stream<String>` và `map(String::length)`, stream sau map có kiểu gì?

**Đáp án:** `Stream<Integer>`, vì `String.length()` trả int và được boxing thành Integer khi cần generic type argument R.

**2.** Vì sao `map(Order::prices)` cho `Stream<List<Integer>>`?

**Đáp án:** `Order::prices` trả đúng một List<Integer> cho mỗi Order, nên R là List<Integer>. map không tự lấy phần tử bên trong List.

**3.** Đổi kết quả đó thành `Stream<Integer>` bằng cách nào?

**Đáp án:** `flatMap(o -> o.prices().stream())`; mapper phải trả Stream<Integer>.

**4.** Chữ ký `Function<? super T, ? extends R>` cho phép điều gì?

**Đáp án:** Function nhận T hoặc supertype của T, và trả R hoặc subtype của R; thể hiện PECS cho đầu vào/đầu ra.

**5.** `flatMap` có nhất thiết trả đúng một kết quả cho mỗi T không?

**Đáp án:** Không. Stream con có thể rỗng, một phần tử hoặc nhiều phần tử, tạo 0..n kết quả.

**6.** `A` trong `Collector<? super T,A,R>` là gì?

**Đáp án:** Kiểu container tích lũy trung gian; không nhất thiết trùng R, là kiểu trả ra sau khi collector hoàn tất.

**7.** `collect(ArrayList::new,List::add,List::addAll)` dùng hai BiConsumer cho việc gì?

**Đáp án:** Accumulator thêm từng T vào container R; combiner ghép hai container R, cần thiết khi framework gộp kết quả.

**8.** Từ Stream<Order>, dùng `Collectors.groupingBy(Order::customer)` thì R là gì?

**Đáp án:** `Map<String,List<Order>>`, bởi classifier trả String và downstream mặc định nhóm các Order vào List<Order>.

**9.** Khi dùng `groupingBy(Order::customer, Collectors.summingInt(Order::total))`, kiểu value của Map là gì?

**Đáp án:** Integer, nên kết quả là Map<String,Integer> về mặt generic type.

**10.** Vì sao `Collectors.toMap(Order::customer,Order::total)` có thể ném IllegalStateException?

**Đáp án:** Hai order có cùng customer tạo duplicate key; overload hai mapper không có merge policy do caller cung cấp.

**11.** Muốn tổng giá trị cho từng customer bằng toMap, thêm gì?

**Đáp án:** MergeFunction như `Integer::sum`; có thể thêm mapFactory `TreeMap::new` để thứ tự key xác định.

**12.** `Collectors.mapping` khác `Stream.map` ở đâu?

**Đáp án:** Stream.map biến một Stream trung gian; Collectors.mapping là adapter Collector biến từng T thành U trước khi downstream accumulator nhận dữ liệu.

**13.** `Stream.toList()` khác `collect(Collectors.toList())` ra sao?

**Đáp án:** `Stream.toList()` bảo đảm List kết quả không cho sửa đổi (Java 16+), còn `Collectors.toList()` không cam kết cụ thể mutability hoặc implementation type.

**14.** Vì sao không nên gọi `forEach(sharedArrayList::add)` trên parallel stream?

**Đáp án:** Shared ArrayList không thread-safe cho concurrent additions; nên dùng collection terminal phù hợp và không có interfering side effects.

**15.** Compiler báo incompatible bounds trong một pipeline; cách kiểm tra đầu tiên nên là gì?

**Đáp án:** Ghi lại kiểu T trước mỗi intermediate operation, kiểu R do mapper trả, sau đó đối chiếu collector tiêu thụ kiểu nào. Dùng intermediate variables rõ kiểu để tìm đúng bước.

## 23. Ghi nhớ

Ba API làm việc ở **ba mức khác nhau**:

~~~text
Stream<T>
    ├─ map(Function<T,R>)                 → Stream<R>
    ├─ flatMap(Function<T,Stream<R>>)     → Stream<R>, nối các stream con
    └─ collect(Collector<T,A,R>)          → R, kết thúc pipeline
~~~

Những chữ ký ở sơ đồ đã lược bỏ wildcard để dễ nhớ; **chữ ký thực** dùng `? super` cho đầu vào tiêu thụ và `? extends` cho đầu ra được tạo.

- `map`: **một T → một R** theo mapping, không tự flatten.
- `flatMap`: **một T → Stream<R> → 0..n R**, làm phẳng một mức.
- `collect`: **Stream<T> → R**, dùng Collector hoặc supplier/accumulator/combiner; A là container trung gian khi dùng Collector.
- `groupingBy`, `mapping`, `flatMapping`, `toMap` là **collector factories/adapters**, không phải phương thức cùng loại với Stream.map/flatMap.
- **Type safety không thay thế behavioral correctness**: cần chú ý duplicate keys, null, ordering, mutability, short-circuit, parallel reduction và nguồn side effects.

Cách học hữu ích nhất là **đọc loại phần tử T tại mỗi bước**, theo dõi R của mapper, rồi đọc kết quả R của collector. Khi làm được điều này, các chữ ký generics dài trở thành sơ đồ luồng dữ liệu rõ ràng thay vì một chuỗi ký tự khó nhớ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lambda-standard-ap-is-008">Generic trong Optional và quan hệ kiểu khi biến đổi dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Generic trong `Optional` và quan hệ kiểu khi biến đổi dữ liệu (Java 17)

## 1. Vấn đề: một giá trị có thể không tồn tại, nhưng vẫn cần giữ kiểu

Trong Java, một phép tra cứu có thể **tìm thấy** hoặc **không tìm thấy** dữ liệu. Trả `null` trực tiếp khiến caller phải nhớ kiểm tra và dễ tạo `NullPointerException`:

```java
String name = repository.findName(id); // Có thể là null.
System.out.println(name.length());      // Có thể ném NullPointerException.
```

`java.util.Optional<T>` biểu đạt rõ hơn rằng kết quả có thể vắng mặt:

```java
Optional<String> name = repository.findName(id);
```

Ở đây:

- `Optional` là một generic class.
- `T` là kiểu **giá trị bên trong**, ví dụ `String`, `User`, `Integer`.
- Một `Optional<T>` có thể chứa một giá trị khác `null` thuộc `T`, hoặc **empty**.
- Caller xử lý sự có mặt/vắng mặt bằng các API như `map`, `flatMap`, `filter`, `orElseGet`, `orElseThrow`, `stream`.

Vấn đề Generics xuất hiện khi **biến đổi** dữ liệu: một `Optional<User>` có thể phải thành `Optional<String>`, hay một phép biến đổi có thể đã trả `Optional<Profile>` và cần tránh sinh `Optional<Optional<Profile>>`. Để làm tốt, cần hiểu **quan hệ kiểu giữa input, mapper và output**, cùng sự khác nhau giữa `map` và `flatMap`.

Bài này trình bày cơ chế Generics trong Optional, cú pháp Java 17, type inference, wildcard variance, tập hợp Optional, xử lý null và lỗi phổ biến. Ví dụ chính là một chương trình độc lập đã được biên dịch/chạy với `javac --release 17 -Xlint:all` và đối chiếu stdout.

## 2. Nhận diện đúng hợp đồng của `Optional<T>`

Một `Optional<String>` **không phải** một `String`. Nó là một container có khả năng chứa `String`.

```java
Optional<String> present = Optional.of("Java");
Optional<String> absent = Optional.empty();
```

Để đọc, bạn không thể gán trực tiếp:

```java
// Không biên dịch:
// String text = present;
```

Bạn phải chọn rõ chính sách khi có giá trị hoặc khi empty, chẳng hạn:

```java
String text = present.orElse("N/A");
```

Nhờ vậy, API không còn ngầm yêu cầu caller đoán xem return value có thể là `null` hay không.

### 2.1. Các factory methods

| API | Input | Nếu giá trị là `null` | Kết quả |
| --- | --- | --- | --- |
| `Optional.of(value)` | Một tham chiếu **bắt buộc khác null** | Ném `NullPointerException` | Optional có giá trị |
| `Optional.ofNullable(value)` | Tham chiếu có thể null | Trả `Optional.empty()` | Có giá trị hoặc empty |
| `Optional.empty()` | Không nhận input | Không áp dụng | Optional empty |

Ví dụ:

```java
Optional<String> a = Optional.of("x");
Optional<String> b = Optional.ofNullable(null);
Optional<String> c = Optional.empty();
```

`b` và `c` đều là empty. Không nên so sánh danh tính bằng `==` để phát hiện empty; dùng `isEmpty()`, `isPresent()`, hoặc các thao tác phù hợp.

### 2.2. Optional là value-based class

Tài liệu Java mô tả `Optional` là một **value-based class**. Đừng dựa vào identity, đồng bộ hóa trên Optional instance hoặc suy đoán rằng các empty instances luôn cùng tham chiếu. Quan tâm tới **giá trị/ngữ nghĩa** thông qua API của Optional, không phải việc wrapper object có identity nào.

### 2.3. `Optional` không thay thế mọi đối tượng và collection

Nếu kết quả tự nhiên là “không có phần tử nào”, một `List<T>` rỗng thường đủ rõ. `Optional<List<T>>` lại có **hai trạng thái khác nhau**:

- `Optional.empty()` — không có kết quả danh sách.
- `Optional.of(List.of())` — có kết quả danh sách, nhưng danh sách rỗng.

Chỉ dùng hai tầng này nếu sự phân biệt có ý nghĩa nghiệp vụ. Đừng biến mọi giá trị của codebase thành `Optional<T>` một cách máy móc.

## 3. `Optional<T>` là generic bất biến (invariant)

Java Generics không tự động covariant chỉ vì các type arguments có quan hệ subtype.

```java
Integer number = 7;
Number parent = number; // Hợp lệ.
```

Nhưng một biến đã xác định kiểu:

```java
Optional<Integer> integers = Optional.of(7);

// Không biên dịch:
// Optional<Number> numbers = integers;
```

`Optional<Integer>` **không phải subtype** của `Optional<Number>`. Điều này cũng tương tự `List<Integer>` không tự là `List<Number>`.

### 3.1. Vì sao `Optional<Number> x = Optional.of(7)` lại biên dịch?

Đây là chi tiết quan trọng về **generic method inference**:

```java
Optional<Number> numbers = Optional.of(7);
```

Đoạn này **hợp lệ** trên Java 17. `Optional.of` là generic factory method:

```java
public static <T> Optional<T> of(T value)
```

Khi suy luận `T`, compiler có thể cân nhắc **kiểu đích `Optional<Number>`** bên cạnh `Integer` value đã boxing. Nó chọn type argument đáp ứng các ràng buộc của lời gọi và assignment.

Điều đó **không làm Generic Java covariant**. Nếu kết quả đã được lưu vào một biến `Optional<Integer>`, bạn không thể gán biến đó thẳng sang `Optional<Number>`.

So sánh rõ:

```java
Optional<Number> direct = Optional.of(7); // Hợp lệ.

Optional<Integer> integers = Optional.of(7);
// Optional<Number> reassigned = integers; // Không hợp lệ.
```

Đây là khác biệt giữa **suy luận kiểu khi tạo một giá trị generic mới** và **quan hệ subtype giữa hai parameterized types đã xác định**.

### 3.2. Dùng wildcard khi chỉ cần đọc kiểu cha

Nếu một method chỉ cần đọc Number:

```java
static double doubled(Optional<? extends Number> input) {
    return input.map(n -> n.doubleValue() * 2).orElse(0.0);
}
```

Caller có thể truyền `Optional<Integer>` hoặc `Optional<Double>`:

```java
double a = doubled(Optional.of(7));
double b = doubled(Optional.of(1.5));
```

`? extends Number` biểu thị phần tử được đảm bảo là một subtype nào đó của `Number`, phù hợp với mục đích **producer**: ta đọc một `Number` và gọi `doubleValue()`.

Không viết `Optional<Number>` nếu thực tế method muốn nhận nhiều loại Optional chứa các subtype mà không có nhu cầu thay đổi wrapper/giá trị đầu vào.

### 3.3. Khi cần `Optional<Number>` thật sự

Bạn có thể biến đổi tường minh:

```java
Optional<Integer> ints = Optional.of(7);
Optional<Number> numbers = ints.map(n -> (Number) n);
```

Đây là **tạo một Optional mới về mặt kết quả API**, không phải một phép gán covariant giữa hai biến generic đã xác định. Lambda cho compiler biết kiểu trả về mapper là `Number`, dẫn tới `U = Number` cho `map`.

Một phương án khác trong ngữ cảnh phù hợp là type witness/cung cấp target type khi tạo Optional mới. Nhưng đừng xem type witness là cách bỏ qua mọi quan hệ generic bị cấm.

## 4. Chữ ký generic của `map`: từ `T` sang `U`

Trên Java 17, chữ ký quan trọng của `Optional<T>` là:

```java
public <U> Optional<U> map(
        Function<? super T, ? extends U> mapper)
```

Tách thành bốn phần:

1. `T` là **kiểu hiện tại** của Optional.
2. `<U>` là **type parameter mới của method `map`**: kiểu kết quả sau biến đổi.
3. `Function<? super T, ? extends U>` nhận một giá trị thích hợp cho `T`, trả một giá trị tương thích `U`.
4. `Optional<U>` là wrapper của **kết quả đã biến đổi**.

Ví dụ:

```java
Optional<String> name = Optional.of("Java");
Optional<Integer> length = name.map(String::length);
```

Trước `map`: `T = String`.

Mapper: `String -> Integer` (kết quả primitive `int` được boxing).

Sau `map`: `U = Integer`, kết quả là `Optional<Integer>`.

Không có cast trong mã caller. Compiler bảo toàn quan hệ kiểu qua functional interface và generic type inference.

### 4.1. Vì sao input của Function là `? super T`?

`? super T` cho phép mapper có thể nhận **một kiểu cha** của `T`, vì mapper đóng vai trò **consumer** của giá trị được Optional đưa vào.

Ví dụ:

```java
Function<Object, String> stringify = Object::toString;
Optional<String> input = Optional.of("Java");

Optional<String> output = input.map(stringify);
```

Dù mapper nhận `Object`, nó vẫn có thể xử lý một `String`. Không cần buộc `Function<String,String>` nếu một `Function<Object,String>` đã đủ năng lực.

### 4.2. Vì sao output của Function là `? extends U`?

Kết quả mapper là một **producer** của `U`: có thể trả một subtype phù hợp, chứ không nhất thiết phải có chữ ký trả đúng `U` ở mọi declaration.

Đây là cách dùng quy tắc **PECS**: *Producer Extends, Consumer Super*. Tuy nhiên PECS là mẹo nhớ hướng wildcard, không thay thế việc hiểu ngữ nghĩa của `Function` và ràng buộc inference.

### 4.3. Một generic helper dùng `map`

```java
static <T, U> Optional<U> transform(
        Optional<T> input,
        Function<? super T, ? extends U> mapper) {
    return input.map(mapper);
}
```

Caller:

```java
Optional<Integer> length =
        transform(Optional.of("abc"), CharSequence::length);
```

Ở đây `CharSequence::length` có thể nhận String vì String triển khai CharSequence. Kết quả được boxing thành `Integer`. Java 17 đã biên dịch ví dụ này và chương trình in:

```text
transform=3
```

Một thiết kế generic helper vẫn nên kiểm tra input hoặc mapper null theo contract riêng, thay vì cho rằng annotation type parameters tự động ngăn tham chiếu null.

## 5. `map` chỉ thực thi mapper khi có giá trị

Với:

```java
Optional<String> name = Optional.of(" Java ");
Optional<String> normalized = name.map(String::strip);
```

Mapper nhận `" Java "` và trả `"Java"`.

Nếu đầu vào empty:

```java
Optional<String> empty = Optional.<String>empty();
Optional<Integer> result = empty.map(String::length);
```

`result` vẫn empty; lambda/method reference không được gọi.

Đây là cách viết pipeline mà mỗi bước chỉ chạy khi các bước trước thực sự có dữ liệu. Không cần viết nhiều tầng `if (value != null)` chỉ để truyền kết quả qua các phép biến đổi tuần tự.

### 5.1. `map` nhận kết quả null thì sao?

Một khác biệt tinh tế:

```java
Optional<String> result =
        Optional.of("x").map(x -> (String) null);

System.out.println(result.isEmpty()); // true
```

`map` dùng ngữ nghĩa tương đương `ofNullable` trên **kết quả mapper**: nếu mapper trả `null`, Optional kết quả trở thành empty. Nó không tạo `Optional` “có giá trị null”.

Nhưng nếu **mapper argument là null**:

```java
// Không hợp lệ tại runtime:
// Optional.of("x").map(null);
```

method từ chối mapper null bằng `NullPointerException`. Hãy phân biệt “mapper trả null” và “mapper là null”.

### 5.2. `map` không tự nuốt exception

Nếu mapper ném `NumberFormatException`:

```java
Optional<Integer> number =
        Optional.of("not-an-int").map(Integer::parseInt);
```

Exception vẫn lan ra caller. Optional **không** chuyển mọi exception thành empty.

Nếu “không parse được” là trạng thái nghiệp vụ bình thường, hãy viết mapper hoặc helper chuyển đổi riêng có xử lý lỗi rõ ràng, ví dụ trả `Optional<Integer>` và dùng `flatMap`.

## 6. `filter`: lọc nhưng không thay kiểu `T`

Chữ ký tương ứng:

```java
public Optional<T> filter(Predicate<? super T> predicate)
```

`filter` chỉ kiểm tra điều kiện trên giá trị hiện có:

```java
Optional<String> candidate = Optional.of("  Java  ");

Optional<String> nonBlank = candidate
        .map(String::strip)
        .filter(s -> !s.isEmpty());
```

Nếu predicate trả true, giữ giá trị; nếu false, chuyển thành empty. Nếu Optional đã empty, predicate không chạy.

**Kiểu kết quả vẫn là `Optional<T>`**, khác với `map` có thể đổi `T` thành `U`.

Ví dụ:

```java
Optional<Integer> positive = Optional.of(7)
        .filter(n -> n > 0);
```

`positive` là `Optional<Integer>`, không có `U` mới được tạo bởi `filter`.

### 6.1. Thứ tự thao tác có ý nghĩa

```java
Optional<String> cleaned = Optional.of("  ")
        .map(String::strip)
        .filter(s -> !s.isBlank());
```

Chuỗi `"  "` được strip thành `""`, rồi filter loại bỏ. Với một pipeline khác hoặc điều kiện khác, dữ liệu có thể được xử lý khác. `map` và `filter` không phải phép hoán đổi thứ tự tùy ý.

## 7. `map` tạo Optional lồng nhau khi mapper đã trả Optional

Giả sử tìm thông tin User:

```java
record Profile(String city) {}
record User(String name, Optional<Profile> profile) {}

static Optional<User> findUser(int id) {
    if (id == 1) {
        return Optional.of(new User(
                "Lan", Optional.of(new Profile("Hanoi"))));
    }
    return Optional.empty();
}
```

Bạn muốn tìm `Profile` từ `Optional<User>`.

Nếu dùng `map`:

```java
Optional<Optional<Profile>> nested =
        findUser(1).map(User::profile);
```

Vì method `User.profile()` **đã trả `Optional<Profile>`**, generic inference của `map` xác định:

- Input `T = User`.
- Mapper result `U = Optional<Profile>`.
- Return `Optional<U> = Optional<Optional<Profile>>`.

Hai tầng Optional có nghĩa hai cấu trúc vắng mặt được lồng nhau. Trường hợp này thường **không cần thiết** trong pipeline tra cứu dữ liệu.

### 7.1. Phân biệt hai `map` có chữ ký giống tên

`Optional<User>.map(User::name)` hợp lý nếu `User.name()` trả một `String`.

Nhưng `Optional<User>.map(User::profile)` sinh nested Optional nếu `User.profile()` trả `Optional<Profile>`.

Sự khác biệt đến từ **kiểu trả về thật của mapper**; không phải compiler ngẫu nhiên thêm một lớp Optional.

## 8. `flatMap`: phép biến đổi đã trả Optional

Chữ ký `flatMap` trong Java 17:

```java
public <U> Optional<U> flatMap(
    Function<? super T, ? extends Optional<? extends U>> mapper)
```

Điều quan trọng nhất:

- Mapper nhận một giá trị tương thích `T`.
- Mapper **phải trả `Optional`** chứa kiểu tương thích với `U`.
- `flatMap` không bọc Optional thêm một tầng nữa; kết quả cuối là `Optional<U>`.

Ví dụ:

```java
Optional<Profile> profile =
        findUser(1).flatMap(User::profile);
```

Thay vì `Optional<Optional<Profile>>`, ta nhận trực tiếp `Optional<Profile>`.

Muốn lấy city:

```java
Optional<String> city = findUser(1)
        .flatMap(User::profile)
        .map(Profile::city);
```

Pipeline làm ba việc theo kiểu tĩnh:

1. `findUser(1)` có kiểu `Optional<User>`.
2. `.flatMap(User::profile)` thành `Optional<Profile>`.
3. `.map(Profile::city)` thành `Optional<String>`.

Nếu một bước empty, các bước sau không nhận giá trị để chạy và toàn pipeline trả empty.

### 8.1. Tại sao chữ ký của `flatMap` có hai dấu `extends`?

`? super T` biểu đạt mapper có thể chấp nhận một kiểu rộng hơn `T`.

`? extends Optional<? extends U>` biểu đạt mapper có thể tạo một subtype hợp lệ của kiểu Optional container, bên trong chứa subtype tương thích với `U`. Signature giúp nhiều cách viết mapper và quan hệ subtype không phải cast thủ công.

Để dùng hiệu quả, thường không cần học thuộc nguyên văn cả signature; chỉ cần ghi nhớ **Function phải trả Optional** và compiler có thể suy luận `U` từ kiểu của giá trị bên trong.

### 8.2. `flatMap` từ chối mapper trả null

Khác với `map`:

```java
Optional.of("x")
        .flatMap(s -> (Optional<String>) null);
```

với Optional có giá trị sẽ ném `NullPointerException`. Mapper của `flatMap` phải trả một **Optional object** khác null: có giá trị hoặc `Optional.empty()`.

Nếu muốn biểu thị vắng mặt, trả:

```java
Optional.<String>empty()
```

Không trả `null`.

### 8.3. `flatMap` không phải `map` có tên khác

Nếu mapper trả một giá trị thường, ví dụ `Integer.parseInt(s)` có kiểu `int`/`Integer`, thì dùng:

```java
Optional<Integer> number =
        Optional.of("7").map(Integer::parseInt);
```

Không dùng:

```java
// Không biên dịch:
// Optional.of("7").flatMap(Integer::parseInt);
```

Compiler báo `int cannot be converted to Optional<? extends U>` vì chữ ký `flatMap` yêu cầu mapper trả Optional.

## 9. Chọn `map` hay `flatMap` theo kiểu trả về mapper

| Kiểu ban đầu | Mapper | Dùng | Kiểu kết quả |
| --- | --- | --- | --- |
| `Optional<String>` | `String -> Integer` | `map` | `Optional<Integer>` |
| `Optional<User>` | `User -> String` | `map` | `Optional<String>` |
| `Optional<User>` | `User -> Optional<Profile>` | `map` | `Optional<Optional<Profile>>` |
| `Optional<User>` | `User -> Optional<Profile>` | `flatMap` | `Optional<Profile>` |
| `Optional<Profile>` | `Profile -> String` | `map` | `Optional<String>` |
| `Optional<T>` | `T -> boolean` | `filter` dùng `Predicate` | `Optional<T>` giữ hoặc empty |

Một mẹo kiểm tra nhanh:

- Mapper trả **`U`** → `map`, Optional sẽ bọc `U`.
- Mapper trả **`Optional<U>`** → `flatMap`, tránh wrapper lồng nhau.
- Chỉ kiểm tra đúng/sai trên `T` → `filter`.

Không thay `flatMap` bằng `.map(...).get()` để “bóc” Optional. `.get()` trên empty ném `NoSuchElementException`, làm mất lợi ích của pipeline an toàn.

## 10. Biến đổi dữ liệu qua nhiều lớp Optional

Ví dụ tìm thành phố của User:

```java
static Optional<String> cityFor(int id) {
    return findUser(id)
            .flatMap(User::profile)
            .map(Profile::city);
}
```

Nếu `findUser(1)` có User, Profile và city, kết quả là `Optional.of("Hanoi")`.

Nếu `findUser(9)` empty, kết quả empty. Caller chọn giá trị fallback:

```java
String city = cityFor(9).orElse("?");
```

Nếu User tồn tại nhưng `profile()` empty, `flatMap` cũng cho empty. Đây là chain biến đổi **không cần** nhiều if/else lồng nhau.

### 10.1. Có thể mất thông tin về lý do vắng mặt

Một pipeline Optional thường chỉ ghi nhận “có giá trị” hoặc “empty”; nó không giải thích:

- User không tồn tại.
- User có nhưng chưa có Profile.
- Profile có nhưng city chưa được cung cấp.

Nếu ứng dụng cần phân biệt những trạng thái này cho log/API errors, `Optional<T>` có thể không đủ. Cân nhắc một kết quả domain có mã trạng thái, sealed hierarchy hoặc kiểu Result/Either do ứng dụng/thư viện cung cấp.

### 10.2. Đừng gọi Optional một “effect/exception monad” tùy tiện

`map`/`flatMap` mang mô hình biến đổi container hữu ích, nhưng `Optional` của Java không tự gói checked exceptions, không tích lũy lỗi validation và không có logging/transaction semantics. Khi cần những yêu cầu đó, bổ sung abstraction thích hợp thay vì lạm dụng `.orElse(null)`.

## 11. `Optional.or`: fallback cũng là Optional

Java 9+ có method:

```java
public Optional<T> or(
    Supplier<? extends Optional<? extends T>> supplier)
```

Nếu Optional hiện có giá trị, trả lại Optional hiện tại. Nếu empty, gọi Supplier để lấy một **Optional fallback**, không chỉ một giá trị `T`.

```java
Optional<String> result = Optional.<String>empty()
        .or(() -> Optional.of("alternate"));
```

`result` là `Optional<String>` có giá trị `"alternate"`.

Phân biệt:

- `orElse(T other)`: trả **T**; fallback được đánh giá trước lời gọi theo quy tắc Java.
- `orElseGet(Supplier<? extends T>)`: trả **T**; Supplier chỉ được gọi khi empty.
- `or(Supplier<? extends Optional<? extends T>>)`: trả **Optional<T>**; Supplier phải trả một Optional khác null khi được gọi.

`or` hữu ích cho chuỗi tìm nguồn khác:

```java
Optional<String> value = fromCache()
        .or(() -> fromDatabase());
```

Đây là đoạn minh họa khi hai helper `fromCache()` và `fromDatabase()` trả Optional tương thích. Nếu cache có giá trị, supplier thứ hai không chạy.

## 12. `orElse` và `orElseGet`: eager so với lazy

Giả sử:

```java
Optional<String> existing = Optional.of("ready");
```

Hai lệnh trông giống nhau về kết quả:

```java
String a = existing.orElse(computeFallback());
String b = existing.orElseGet(() -> computeFallback());
```

Nhưng cách đánh giá khác:

- `orElse(computeFallback())`: Java phải **đánh giá đối số `computeFallback()` trước khi gọi** `orElse`, kể cả khi Optional đã có giá trị.
- `orElseGet(() -> computeFallback())`: truyền Supplier; `computeFallback()` chỉ chạy nếu Optional empty.

Ví dụ dùng counter:

```java
AtomicInteger calls = new AtomicInteger();

String eager =
        existing.orElse(expensiveFallback(calls));
String lazy =
        existing.orElseGet(() -> expensiveFallback(calls));
```

Nếu `expensiveFallback` tăng counter, sau hai lệnh counter là `1`. Cả `eager` và `lazy` đều bằng `"ready"`, nhưng eager fallback đã chạy một lần.

Khi:

```java
Optional.<String>empty()
        .orElseGet(() -> expensiveFallback(calls));
```

supplier chạy và counter tăng thành `2`.

### 12.1. Quy tắc chọn đơn giản

Dùng `orElse("N/A")` cho constant hoặc giá trị rẻ, đã sẵn có.

Dùng `orElseGet(this::expensiveLookup)` khi fallback cần tính toán, truy cập hệ thống khác, ghi log, tạo object tốn kém hoặc có side effect chỉ nên thực thi lúc empty.

Không phải `orElseGet` “nhanh hơn tuyệt đối” trong mọi trường hợp; khác biệt cốt lõi là **thời điểm thực thi fallback** và semantic side effects.

### 12.2. `orElseThrow` khi thiếu là lỗi

Nếu giá trị bắt buộc phải tồn tại:

```java
String name = optionalName.orElseThrow(
        () -> new IllegalStateException("Name missing"));
```

Còn `optionalName.orElseThrow()` (Java 10+) ném `NoSuchElementException` nếu empty.

Dùng exception chỉ khi thiếu giá trị là trường hợp thật sự bất thường theo contract; không ném rồi catch ngay chỉ để mô phỏng điều kiện thường gặp.

## 13. `ifPresent`, `ifPresentOrElse` và `get()`

Đôi khi thay vì biến đổi Optional thành Optional khác, bạn muốn thực hiện tác vụ:

```java
optionalName.ifPresent(System.out::println);
```

`ifPresent` nhận `Consumer<? super T>`, trả `void`. Không nên dùng nó thay `map` khi bạn muốn giá trị biến đổi tiếp.

Java 9+ có:

```java
optionalName.ifPresentOrElse(
        value -> System.out.println(value),
        () -> System.out.println("missing"));
```

Một action chạy khi có giá trị và một Runnable chạy khi empty.

`Optional.get()` có thể trả giá trị khi present nhưng ném `NoSuchElementException` khi empty. Với code mới, thường rõ hơn nếu dùng `orElseThrow`, hoặc biến đổi bằng `map`/`flatMap` và xử lý fallback một lần ở ranh giới.

### 13.1. Anti-pattern: dùng `isPresent()` rồi `get()` khắp nơi

```java
if (maybeUser.isPresent()) {
    User user = maybeUser.get();
    // ...
}
```

Không phải lúc nào cũng sai, nhưng với một phép biến đổi đơn giản có thể rõ và nhất quán hơn:

```java
Optional<String> name = maybeUser.map(User::name);
```

`map` giữ logic trong pipeline, không phải viết kiểm tra empty ở mỗi bước. Dùng `ifPresent` khi thật sự muốn side effect thay vì biến đổi return value.

## 14. `Optional.stream()` giúp phối hợp với `Stream<T>`

Java 9+ bổ sung:

```java
Stream<T> stream()
```

- Optional có giá trị → Stream có đúng **một** phần tử.
- Optional empty → Stream rỗng.

Điều này giúp flatten một stream các Optional:

```java
List<Integer> found = Stream.of(
        Optional.of(2),
        Optional.<Integer>empty(),
        Optional.of(5))
    .flatMap(Optional::stream)
    .toList();
```

Java 17 in:

```text
stream=[2, 5]
```

Ở đây `Stream.flatMap` khác `Optional.flatMap` nhưng có ý tưởng tương tự: mapper tạo một structure phù hợp, còn `flatMap` gộp các structures kết quả để tránh các tầng lồng không cần thiết.

`Stream.toList()` có từ Java 16 và trả danh sách unmodifiable theo hợp đồng API. Nếu cần mutable list, chọn collector hoặc cấu trúc copy có chủ đích.

### 14.1. `List<Optional<T>>` so với `Optional<List<T>>`

`List<Optional<String>>` nghĩa là **nhiều vị trí**, mỗi vị trí có thể có hoặc thiếu string.

`Optional<List<String>>` nghĩa là **toàn bộ kết quả list có thể có hoặc thiếu**, nhưng nếu present thì phần tử của list được quy định bởi generic `String`.

Để bỏ vị trí empty trong `List<Optional<T>>`:

```java
List<String> names = optionals.stream()
        .flatMap(Optional::stream)
        .toList();
```

Đoạn này giả định `optionals` là `List<Optional<String>>`. Nó giữ các present values và bỏ các empty entries; do đó **mất thông tin vị trí nào ban đầu thiếu**. Nếu vị trí có ý nghĩa nghiệp vụ, không nên flatten kiểu này mà không lưu index hoặc trạng thái riêng.

## 15. Ví dụ Java 17 chạy được: biến đổi Optional và Generics

Tạo tệp **`OptionalGenericsDemo.java`**:

```java
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

public class OptionalGenericsDemo {
    record Profile(String city) {}
    record User(String name, Optional<Profile> profile) {}

    static Optional<User> findUser(int id) {
        if (id == 1) {
            return Optional.of(new User(
                    "  Lan  ", Optional.of(new Profile("Hanoi"))));
        }
        return Optional.empty();
    }

    static Optional<String> cityFor(int id) {
        return findUser(id).flatMap(User::profile)
                .map(Profile::city);
    }

    static <T, U> Optional<U> transform(
            Optional<T> input,
            Function<? super T, ? extends U> mapper) {
        return input.map(mapper);
    }

    static Optional<Number> toNumber(Optional<Integer> input) {
        return input.map(n -> (Number) n);
    }

    static double doubled(Optional<? extends Number> input) {
        return input.map(n -> n.doubleValue() * 2)
                .orElse(0.0);
    }

    static String expensiveFallback(AtomicInteger calls) {
        calls.incrementAndGet();
        return "fallback";
    }

    public static void main(String[] args) {
        Optional<String> raw = Optional.ofNullable("  Java  ");
        Optional<Integer> length = raw.map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(String::length);
        System.out.println("length=" + length.orElse(-1));

        System.out.println("map-null-empty="
                + Optional.of("x")
                .map(x -> (String) null).isEmpty());

        Optional<Optional<Profile>> nested =
                findUser(1).map(User::profile);
        System.out.println("nested=" + nested.isPresent());
        System.out.println("city=" + cityFor(1).orElse("?"));
        System.out.println("missing-city=" + cityFor(9).orElse("?"));

        System.out.println("transform="
                + transform(Optional.of("abc"),
                        CharSequence::length).orElse(-1));

        System.out.println("number="
                + toNumber(Optional.of(7)).orElse(-1));
        System.out.println("covariant="
                + doubled(Optional.of(7)));

        List<Integer> values = Stream.of(
                Optional.of(2),
                Optional.<Integer>empty(),
                Optional.of(5))
                .flatMap(Optional::stream).toList();
        System.out.println("stream=" + values);

        Optional<String> restored = Optional.<String>empty()
                .or(() -> Optional.of("alternate"));
        System.out.println("or=" + restored.orElse("?"));

        AtomicInteger calls = new AtomicInteger();
        Optional<String> existing = Optional.of("ready");
        String eager = existing.orElse(expensiveFallback(calls));
        String lazy = existing.orElseGet(
                () -> expensiveFallback(calls));

        System.out.println("fallback=" + eager + ","
                + lazy + "," + calls.get());

        System.out.println("empty-lazy="
                + Optional.<String>empty()
                .orElseGet(() -> expensiveFallback(calls))
                + "," + calls.get());

        List<String> names = Stream.of("a", "", "bc")
                .map(OptionalGenericsDemo::nonBlank)
                .flatMap(Optional::stream).toList();
        System.out.println("names=" + names);

        try {
            Optional.of("x")
                    .flatMap(x -> (Optional<String>) null);
        } catch (NullPointerException e) {
            System.out.println("flatmap-null="
                    + e.getClass().getSimpleName());
        }

        try {
            Optional.ofNullable(null).orElseThrow();
        } catch (java.util.NoSuchElementException e) {
            System.out.println("empty-get="
                    + e.getClass().getSimpleName());
        }
    }

    static Optional<String> nonBlank(String text) {
        return Optional.ofNullable(text)
                .map(String::strip)
                .filter(x -> !x.isEmpty());
    }
}
```

Chạy từ thư mục chứa file:

```bash
javac --release 17 -Xlint:all OptionalGenericsDemo.java
java OptionalGenericsDemo
```

Chương trình đã được biên dịch thành công trên Java 17, không có warning với `-Xlint:all`. **Stdout chính xác**:

```text
length=4
map-null-empty=true
nested=true
city=Hanoi
missing-city=?
transform=3
number=7
covariant=14.0
stream=[2, 5]
or=alternate
fallback=ready,ready,1
empty-lazy=fallback,2
names=[a, bc]
flatmap-null=NullPointerException
empty-get=NoSuchElementException
```

### 15.1. Đọc `length=4`

`Optional.ofNullable("  Java  ")` có giá trị, nên `map(String::strip)` tạo `"Java"`, `filter` giữ giá trị khác rỗng, và `map(String::length)` đổi sang `Optional<Integer>` có giá trị `4`.

### 15.2. Đọc `map-null-empty=true`

Khi mapper trong `map` trả null, Optional kết quả empty. Đây là hành vi có chủ đích của `map`, không phải một Optional giữ một giá trị null.

### 15.3. Đọc `nested=true`

`findUser(1).map(User::profile)` có kiểu `Optional<Optional<Profile>>` vì `profile()` đã trả Optional. `nested.isPresent()` true cho biết outer Optional có giá trị, **không chứng minh inner Optional luôn present**. Nếu muốn Profile trực tiếp, dùng `flatMap`.

### 15.4. Đọc `city` và `missing-city`

`cityFor(1)` dùng `flatMap` rồi `map`, trả `Hanoi`. `cityFor(9)` empty ngay từ phép tìm User, nên fallback `"?"` được sử dụng.

### 15.5. Đọc `transform`, `number`, `covariant`

`transform` chấp nhận `CharSequence::length` trên String vì mapper có input wildcard `? super T`. `toNumber` đổi kết quả `Optional<Integer>` sang `Optional<Number>` bằng `map`, không cần cast wrapper. `doubled(Optional<? extends Number>)` nhận Optional chứa Integer và trả `14.0`.

### 15.6. Đọc `stream` và `names`

`Optional.stream()` biến mỗi Optional thành stream 0 hoặc 1 phần tử. `flatMap(Optional::stream)` bỏ các empty entries, tạo `[2, 5]`. Pipeline `nonBlank` tương tự bỏ chuỗi rỗng, giữ `[a, bc]`.

### 15.7. Đọc `fallback` và `empty-lazy`

Với Optional có giá trị `"ready"`, đối số của `orElse` được thực thi eagerly, làm counter tăng 1 dù không dùng fallback. `orElseGet` trên Optional present không gọi Supplier. Trường hợp empty tiếp theo mới gọi Supplier, khiến counter tăng thành 2.

### 15.8. Đọc hai exception cuối

`flatMap` không chấp nhận mapper trả null, ném `NullPointerException`. `orElseThrow()` trên Optional empty ném `NoSuchElementException`. Hai lỗi có nguyên nhân khác nhau và không nên biến mọi exception thành “Optional sai”.

## 16. Các ví dụ cố ý không biên dịch: hiểu lỗi inference và quan hệ kiểu

Các đoạn sau là **các tệp riêng**, không chép chung vào file chương trình chính.

### 16.1. `BadInvariant.java`: biến đã có kiểu cố định

```java
import java.util.Optional;

class BadInvariant {
    static void check() {
        Optional<Integer> integers = Optional.of(7);
        Optional<Number> numbers = integers;
    }
}
```

Chạy:

```bash
javac --release 17 BadInvariant.java
```

Compiler báo:

```text
error: incompatible types:
Optional<Integer> cannot be converted to Optional<Number>
```

Đây là **generic invariance**. Dùng `Optional<? extends Number>` cho code chỉ đọc Number, hoặc dùng `map` nếu thực sự cần biến đổi kết quả sang Optional<Number>.

Nhớ phân biệt ví dụ này với `Optional<Number> numbers = Optional.of(7)`, vốn **có thể biên dịch** nhờ inference từ kiểu đích.

### 16.2. `BadMapNested.java`: dùng `map` khi mapper trả Optional

```java
import java.util.Optional;

class BadMapNested {
    static Optional<Integer> make() {
        return Optional.of("7")
                .map(s -> Optional.of(Integer.parseInt(s)));
    }
}
```

Biên dịch:

```bash
javac --release 17 BadMapNested.java
```

Compiler báo lỗi về **incompatible bounds** cho inference variable `U`. Mapper trả `Optional<Integer>`, vì vậy `.map(...)` tạo `Optional<Optional<Integer>>`, không phải `Optional<Integer>` mà return type yêu cầu.

Sửa thành:

```java
return Optional.of("7")
        .flatMap(s -> Optional.of(Integer.parseInt(s)));
```

Nếu mapper chỉ trả `Integer`, dùng `map(Integer::parseInt)` còn đơn giản hơn.

### 16.3. `BadFlatMap.java`: mapper không trả Optional

```java
import java.util.Optional;

class BadFlatMap {
    static Optional<Integer> make() {
        return Optional.of("7")
                .flatMap(Integer::parseInt);
    }
}
```

Biên dịch:

```bash
javac --release 17 BadFlatMap.java
```

Compiler từ chối vì method reference có kết quả `int`, **không thể chuyển thành** `Optional<? extends U>` mà `flatMap` yêu cầu.

Sửa bằng `map(Integer::parseInt)` vì mapper trả một giá trị thường.

### 16.4. `BadMapType.java`: kiểu kết quả không khớp

```java
import java.util.Optional;

class BadMapType {
    static Optional<Integer> make() {
        return Optional.of("java")
                .map(String::toUpperCase);
    }
}
```

Chạy:

```bash
javac --release 17 BadMapType.java
```

`String::toUpperCase` trả `String`; method lại khai báo `Optional<Integer>`. Compiler báo incompatible bounds của inference variable `U` với các ràng buộc `Integer` và `String`.

Cần thay mapper để thật sự tạo `Integer`, hoặc đổi return type thành `Optional<String>` theo mục tiêu thiết kế.

### 16.5. Các lỗi này khác exception runtime

Bốn tệp trên **không biên dịch**. Chúng là lỗi hệ thống kiểu/inference. Trái lại, một mapper hợp lệ về kiểu nhưng ném `NumberFormatException`, hoặc `flatMap` trả null, là lỗi khi **chạy**.

Vì vậy khi debug cần phân biệt “compiler không suy luận được một `U` thỏa constraints” với “mapper chạy và thất bại vì dữ liệu”.

## 17. Tính liên kết kiểu trong các pipeline dài

Một pipeline:

```java
Optional<String> city =
        findUser(id)
            .flatMap(User::profile)
            .map(Profile::city)
            .filter(s -> !s.isBlank());
```

có thể viết bảng kiểu sau mỗi bước:

| Bước | Kiểu tĩnh | Ý nghĩa |
| --- | --- | --- |
| `findUser(id)` | `Optional<User>` | User có thể không tồn tại |
| `.flatMap(User::profile)` | `Optional<Profile>` | User có thể không có Profile |
| `.map(Profile::city)` | `Optional<String>` | Có city string nếu các bước trước có giá trị |
| `.filter(s -> !s.isBlank())` | `Optional<String>` | Loại city chỉ gồm whitespace |
| `.orElse("Unknown")` | `String` | Biến Optional thành giá trị thường với fallback |

Viết kiểu tĩnh sau từng bước là cách chẩn đoán rất hiệu quả khi `javac` báo inference bounds không tương thích.

### 17.1. Refactor bằng local variables để đọc lỗi dễ hơn

Nếu chain dài:

```java
var result = a.flatMap(...).map(...).flatMap(...).filter(...);
```

gặp diagnostic khó hiểu, tách thành:

```java
Optional<User> user = findUser(id);
Optional<Profile> profile = user.flatMap(User::profile);
Optional<String> city = profile.map(Profile::city);
```

Compiler sẽ xác định lỗi gần bước tạo type mismatch hơn, đồng thời tăng tính dễ đọc và test.

### 17.2. Không ép mọi bước vào một Optional

Nếu một bước cần log, thu thập nhiều lỗi hoặc xử lý bất đồng bộ, một chain Optional thuần có thể không biểu diễn đủ ngữ nghĩa. Bạn có thể tách xử lý hoặc chuyển abstraction. Mục tiêu là quan hệ kiểu rõ, không phải có một dòng code duy nhất.

## 18. Generic methods dùng `Optional<? extends T>` ở API boundary

Giả sử một hàm chỉ muốn đọc một số:

```java
static double doubled(Optional<? extends Number> input) {
    return input.map(n -> n.doubleValue() * 2)
            .orElse(0.0);
}
```

Nó nhận `Optional<Integer>`, `Optional<Long>`, `Optional<Double>` nhưng không tự biến input thành `Optional<Number>` cho việc gán.

Một generic helper khác:

```java
static <T, U> Optional<U> transform(
        Optional<T> input,
        Function<? super T, ? extends U> mapper) {
    return input.map(mapper);
}
```

có thể dùng để giữ quan hệ typed giữa input và output. Tuy nhiên đôi khi chỉ viết `input.map(mapper)` trực tiếp còn dễ hiểu hơn; không tạo generic abstraction mới nếu nó không đem lại giá trị thiết kế.

### 18.1. PECS trong Optional

PECS giúp nhớ:

- `? extends Number` trong `Optional<? extends Number>`: producer của Number.
- `? super T` trong `Function<? super T,...>`: mapper có thể tiêu thụ `T`.
- `? extends U` trong `Function<...,? extends U>`: mapper sản xuất `U` hoặc subtype.
- `? extends Optional<? extends U>` trong `flatMap`: mapper sản xuất một Optional chứa kiểu tương thích với `U`.

PECS không cho phép bạn thêm/mutate một giá trị tùy ý vào Optional; `Optional<T>` không có `set(T)`. Lý do dùng wildcard ở đây là **khả năng chấp nhận kiểu và suy luận**, không phải sửa nội dung container.

## 19. Tránh lạm dụng `Optional<T>` trong dữ liệu và API

### 19.1. Optional thường phù hợp cho kết quả có thể vắng mặt

Một ví dụ API rõ ý nghĩa:

```java
Optional<User> findById(long id);
```

Caller được nhắc phải xử lý trường hợp không tìm thấy. Điều đó thường rõ hơn việc trả `User` và tài liệu hóa bằng câu mơ hồ “có thể null”.

### 19.2. Cân nhắc khi dùng Optional làm field hoặc parameter

Nhiều codebase ưu tiên dùng Optional chủ yếu làm **return type** và dùng kiểu domain phù hợp cho fields/parameters, đặc biệt khi có các framework serialization/ORM không hỗ trợ Optional theo mong muốn. Đây là lời khuyên thiết kế theo ngữ cảnh, không phải lệnh cấm tuyệt đối của Java.

Một constructor param `Optional<String>` có thể buộc caller luôn bọc giá trị, kể cả khi chính sách có thể được diễn đạt gọn hơn qua overload/config object. Với field persisted, kiểm tra hỗ trợ của framework trước khi dùng Optional.

### 19.3. Không truyền `null` thay Optional empty

Một method trả `Optional<T>` không nên trả `null`:

```java
// Không nên:
// return null;
```

Nếu không có giá trị, trả `Optional.empty()`. Trả null ở chính wrapper Optional làm caller không còn bảo đảm được API xử lý vắng mặt bằng `map`/`flatMap`.

### 19.4. Không so sánh Optional với `==`

Optional là value-based, dùng equals/kiểm tra trạng thái chứ không dựa trên identity. Cũng không đồng bộ hóa trên Optional object.

## 20. Debug các lỗi và hiện tượng phổ biến

| Hiện tượng | Nguyên nhân hay gặp | Hướng xử lý |
| --- | --- | --- |
| `Optional<Integer> cannot be converted to Optional<Number>` | Generic invariance với biến đã xác định type argument | Dùng `Optional<? extends Number>` khi chỉ đọc, hoặc `map` để tạo Optional<Number> |
| `inference variable U has incompatible bounds` sau `map` | Mapper return type không khớp output mong đợi, hoặc trả Optional nested | Ghi rõ type sau mỗi bước; chọn `flatMap` hoặc sửa mapper/return |
| `int cannot be converted to Optional<? extends U>` sau `flatMap` | Mapper trả giá trị thường thay vì Optional | Chuyển sang `map`, hoặc trả Optional thật |
| `NoSuchElementException` | Gọi `get()`/`orElseThrow()` trên empty | Chọn fallback đúng hợp đồng hoặc xử lý empty trước |
| `NullPointerException` tại `Optional.of(null)` | `of` cấm null | Dùng `ofNullable` nếu null biểu thị vắng mặt |
| `NullPointerException` tại `flatMap` | Mapper/null argument hoặc mapper trả null | Không trả null; trả `Optional.empty()` |
| `map` trả empty dù đầu vào present | Mapper trả null | Xem logic mapper; `map` wrap bằng ofNullable |
| Fallback có side effect dù Optional present | Dùng `orElse(expensiveCall())` | Dùng `orElseGet(() -> expensiveCall())` |
| `Optional<Optional<T>>` ngoài mong muốn | Dùng `map` với mapper trả Optional | Dùng `flatMap` |
| `List<Optional<T>>` xử lý khó | Mỗi phần tử có trạng thái vắng mặt | Dùng `Optional.stream()` để lọc present nếu có thể bỏ vị trí |
| Chain che lý do không có dữ liệu | Optional chỉ giữ present/empty | Dùng domain Result/error type nếu cần nhiều trạng thái lỗi |

### 20.1. Dùng compiler để tìm sai quan hệ kiểu

Một quy trình thực tế:

1. Xác định `T` của Optional trước biến đổi.
2. Xác định return type **chính xác** của mapper.
3. Nếu mapper trả `U`, dùng `map`; nếu trả `Optional<U>`, dùng `flatMap`.
4. Viết type sau biến đổi: `Optional<U>` hay `Optional<Optional<U>>`?
5. So sánh với method return type hoặc assignment target.
6. Nếu dùng wildcard, phân tích nó là input consumer hay output producer.
7. Tách pipeline dài thành variables có kiểu tường minh.
8. Biên dịch bằng `javac --release 17 -Xlint:all` và test cả present, empty, null input, mapper sai và exception.

## 21. Bài tập có đáp án

### Bài 1. `map` đổi kiểu ra sao?

```java
Optional<String> name = Optional.of("Java");
var result = name.map(String::length);
```

**Hỏi:** kiểu tĩnh của `result` là gì?

<details>
<summary>Đáp án</summary>

`Optional<Integer>`. Input là String, mapper trả int (boxing thành Integer), nên generic method `<U> map(...)` suy luận `U = Integer`.

</details>

### Bài 2. Hai dạng assignment nào hợp lệ?

```java
Optional<Number> a = Optional.of(7);

Optional<Integer> ints = Optional.of(7);
// Optional<Number> b = ints;
```

<details>
<summary>Đáp án</summary>

Dòng đầu hợp lệ nhờ type inference có target type `Optional<Number>`. Dòng gán `ints` sang `b` không hợp lệ vì Java Generics invariant: `Optional<Integer>` không phải subtype của `Optional<Number>`.

</details>

### Bài 3. Phép biến đổi nào tránh Optional lồng nhau?

```java
Optional<User> user = findUser(1);
```

Nếu `User.profile()` trả `Optional<Profile>`, dùng method nào?

<details>
<summary>Đáp án</summary>

Dùng `user.flatMap(User::profile)` để nhận `Optional<Profile>`. Dùng `map` sẽ tạo `Optional<Optional<Profile>>`.

</details>

### Bài 4. Tại sao `flatMap(Integer::parseInt)` không hợp lệ?

<details>
<summary>Đáp án</summary>

`parseInt` trả `int`, trong khi `flatMap` yêu cầu mapper trả `Optional<? extends U>`. Dùng `map(Integer::parseInt)` nếu muốn `Optional<Integer>`.

</details>

### Bài 5. `map` và `flatMap` xử lý mapper trả null có giống nhau?

<details>
<summary>Đáp án</summary>

Không. Nếu mapper của `map` trả null trên Optional present, kết quả thành empty. `flatMap` yêu cầu mapper trả một Optional object khác null; nếu trả null, `NullPointerException`.

</details>

### Bài 6. Khi Optional đã có giá trị, fallback nào vẫn được đánh giá?

```java
present.orElse(costly());
present.orElseGet(() -> costly());
```

<details>
<summary>Đáp án</summary>

`costly()` trong `orElse(costly())` được đánh giá trước lời gọi dù Optional present. Supplier ở `orElseGet` chỉ được gọi nếu Optional empty.

</details>

### Bài 7. `List<Optional<T>>` và `Optional<List<T>>` khác nhau ở đâu?

<details>
<summary>Đáp án</summary>

`List<Optional<T>>` là nhiều vị trí, mỗi vị trí có thể thiếu. `Optional<List<T>>` là toàn bộ danh sách có thể thiếu; khi present, danh sách có thể rỗng. Hai mô hình dữ liệu không tương đương.

</details>

### Bài 8. Vì sao `Optional<? extends Number>` giúp nhận nhiều kiểu Optional khác nhau?

<details>
<summary>Đáp án</summary>

Wildcard `? extends Number` cho phép một Optional có type argument là subtype của Number như Integer/Double; code có thể đọc giá trị như Number. Không có nghĩa có thể gán một biến `Optional<Integer>` trực tiếp sang `Optional<Number>`.

</details>

### Bài 9. `Optional.stream()` làm gì với empty?

<details>
<summary>Đáp án</summary>

Nó trả một stream không có phần tử; Optional present tạo stream đúng một phần tử. Vì vậy `streamOfOptionals.flatMap(Optional::stream)` bỏ các empty values.

</details>

### Bài 10. Optional có tự bắt lỗi parse không?

<details>
<summary>Đáp án</summary>

Không. `Optional.of("bad").map(Integer::parseInt)` vẫn ném `NumberFormatException`. Nếu lỗi parse cần biểu diễn thành empty hoặc kết quả có lỗi rõ ràng, viết logic xử lý ngoại lệ riêng.

</details>

## 22. Kết luận

`Optional<T>` không chỉ là cách tránh `null`; khi kết hợp Generic, nó tạo một hợp đồng **có hoặc không có giá trị thuộc kiểu T**, cùng những phép biến đổi được compiler kiểm tra:

- `map(Function<? super T, ? extends U>)` đổi `Optional<T>` thành `Optional<U>`.
- `flatMap(Function<? super T, ? extends Optional<? extends U>>)` dùng khi mapper **đã trả Optional**, tránh Optional lồng nhau.
- `filter(Predicate<? super T>)` kiểm tra điều kiện nhưng giữ kiểu `Optional<T>`.
- `orElse` và `orElseGet` trả `T` với cách thực thi fallback khác nhau; `or` trả lại Optional.
- `Optional.stream()` kết hợp với Stream API để lấy các present values.
- `Optional<Integer>` không subtype của `Optional<Number>`; wildcard và inference ở factory methods giải quyết những nhu cầu khác nhau.

Hãy xác định **kiểu của dữ liệu trước bước biến đổi, kiểu kết quả mapper, kiểu Optional sau bước biến đổi**. Với pipeline dài, tách thành variables có kiểu tường minh để compiler báo lỗi đúng vị trí. Tránh lạm dụng `get()`, cast unchecked và `orElse(null)`; mục tiêu của Optional là để trạng thái vắng mặt và quan hệ kiểu được biểu đạt rõ ngay trong API.

</details>

- [Quay lại đầu trang](#back-to-top)
