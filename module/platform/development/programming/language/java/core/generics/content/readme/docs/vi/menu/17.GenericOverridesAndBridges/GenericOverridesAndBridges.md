<a id="back-to-top"></a>

# Ghi đè và cầu nối

## Menu
- [Thay thế tham số kiểu khi ghi đè phương thức](#generic-overrides-and-bridges-001)
- [Chữ ký sau xóa kiểu và khái niệm subsignature](#generic-overrides-and-bridges-002)
- [Phương thức cầu nối (bridge method) bảo toàn đa hình](#generic-overrides-and-bridges-003)
- [Đối chiếu mã Java với bytecode bằng javap](#generic-overrides-and-bridges-004)
- [Nhận diện phương thức bridge và synthetic qua reflection](#generic-overrides-and-bridges-005)
- [Xung đột tên (name clash) do cùng chữ ký sau xóa kiểu](#generic-overrides-and-bridges-006)
- [Vì sao không thể overload chỉ bằng List&lt;String&gt; và List&lt;Integer&gt;](#generic-overrides-and-bridges-007)
- [Ảnh hưởng của bridge đến dấu vết lỗi ép kiểu lúc chạy](#generic-overrides-and-bridges-008)

## <a id="generic-overrides-and-bridges-001">Thay thế tham số kiểu khi ghi đè phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Thay thế tham số kiểu khi ghi đè phương thức trong Java Generics

## 1. Mục tiêu và tình huống

Khi một lớp con kế thừa lớp hoặc triển khai interface có tham số kiểu, Java cần xác định **phương thức sau khi thay thế tham số kiểu** có thực sự ghi đè (*override*) phương thức của lớp cha hay không.

Ví dụ:

```java
class Box<T> {
    T get() {
        return null;
    }

    void put(T value) {
    }
}

class StringBox extends Box<String> {
    @Override
    String get() {
        return "Java";
    }

    @Override
    void put(String value) {
        System.out.println(value);
    }
}
```

Trong `Box<T>`, `get()` trả `T` và `put(...)` nhận `T`. Với kiểu cha trực tiếp `Box<String>`, trình biên dịch xem các kiểu trong **hợp đồng thừa kế** như đã được thay `T` bằng `String`:

```text
Box<T> khai báo              Khi nhìn từ Box<String>
--------------------------  ---------------------------
T get()                     String get()
void put(T value)           void put(String value)
```

Vì vậy `StringBox.get()` và `StringBox.put(String)` là các phương thức ghi đè hợp lệ. `@Override` giúp compiler xác nhận điều đó.

Tuy nhiên, sau **type erasure**, bytecode của `Box<T>` vẫn có thể mang chữ ký `Object get()` và `put(Object)`. Để bảo toàn đa hình, compiler có thể sinh **bridge method** trong lớp con. Cần phân biệt ba bước:

1. **Thay thế tham số kiểu khi kiểm tra source:** từ `Box<T>` đến `Box<String>`, các chỗ dùng `T` trong hợp đồng được chuyên biệt hóa thành `String`.
2. **Xác định quan hệ override:** tên, tham số, phạm vi truy cập, kiểu trả về và giới hạn generic phải tuân thủ quy tắc ngôn ngữ.
3. **Phát sinh bytecode:** Java xóa nhiều thông tin generic khỏi descriptor và thêm bridge khi cần duy trì dispatch.

Đây là bài về **thay thế tham số kiểu trong override**. Giới hạn đệ quy, wildcard và erasure được nhắc tới ở mức cần thiết để đọc đúng hợp đồng ghi đè.

## 2. Hai loại tham số kiểu cần phân biệt

### 2.1. Type parameter thuộc lớp hoặc interface

```java
class Repository<T> {
    T save(T value) {
        return value;
    }
}

class StringRepository extends Repository<String> {
    @Override
    String save(String value) {
        return value.strip();
    }
}
```

Ở lớp cha, `T` được khai báo ngay tại `Repository<T>`. Lớp con chọn `String` qua `extends Repository<String>`, nên tham số và kết quả `save` trong hợp đồng kế thừa đều được thay tương ứng.

Điều này **không tạo một bản sao `Repository<String>` có bytecode riêng**. Đây là chuyên biệt hóa ở mức kiểu source, còn `Repository` vẫn là một lớp generic được biên dịch theo mô hình erasure.

### 2.2. Type parameter thuộc chính phương thức

```java
class Repository<T> {
    <N extends Number> N number(N value) {
        return value;
    }
}
```

`N` được khai báo tại **phương thức** `number`; nó độc lập với `T` của lớp. Nếu kế thừa `Repository<String>`, ta chỉ thay các lần xuất hiện của `T` thuộc lớp. Không được tự ý suy diễn rằng `N` cũng trở thành `String`.

Lớp con có thể ghi đè bằng tham số kiểu phương thức **đổi tên**:

```java
class StringRepository extends Repository<String> {
    @Override
    <X extends Number> X number(X value) {
        return value;
    }
}
```

`X` và `N` là tên khác nhau của cùng vị trí tham số kiểu phương thức và có giới hạn tương ứng. Sự đổi tên này tương tự đổi tên biến ràng buộc trong một biểu thức generic; nó không thay đổi hợp đồng.

Ngược lại:

```java
// Minh họa lỗi khi dùng @Override:
class Base {
    <N> void accept(N value) {
    }
}

class Child extends Base {
    @Override
    void accept(String value) {
    }
}
```

`accept(String)` **không ghi đè** phương thức generic `<N> accept(N)`: phương thức cha phải chấp nhận mọi kiểu `N` hợp lệ, còn phương thức con chỉ chấp nhận `String`. Khi có `@Override`, compiler báo phương thức không override một phương thức của supertype. Nếu bỏ `@Override`, đây là một overload với tham số cụ thể, còn phương thức generic được kế thừa vẫn tồn tại.

**Quy tắc:** tham số kiểu **của lớp** được thay theo phép kế thừa; tham số kiểu **của phương thức** vẫn được khai báo độc lập tại từng phương thức generic. Không thể biến phương thức generic của cha thành một bản chỉ xử lý `String` chỉ bằng cách ghi đè có tham số `String`.

## 3. Trình tự suy ra chữ ký override

Xét:

```java
import java.util.List;

class Repository<T> {
    T save(T value) {
        return value;
    }

    List<T> items(T value) {
        return List.of(value);
    }

    <N extends Number> N number(N value) {
        return value;
    }
}

class StringRepository extends Repository<String> {
    @Override
    String save(String value) {
        return value.strip();
    }

    @Override
    List<String> items(String value) {
        return List.of(save(value));
    }

    @Override
    <X extends Number> X number(X value) {
        return value;
    }
}
```

Hãy đọc theo trình tự:

1. **Tìm khai báo generic của lớp cha**: `Repository<T>`.
2. **Tìm phép kế thừa cụ thể**: `extends Repository<String>`, tức phép thay `T := String`.
3. **Áp dụng thay thế vào các lần dùng `T` thuộc lớp**: `save(T)` thành `save(String)`, `List<T> items(T)` thành `List<String> items(String)`.
4. **Giữ riêng type parameter của phương thức**: `<N extends Number> N number(N)` vẫn là một phương thức generic. Có thể dùng tên mới `<X extends Number> X number(X)` với bound phù hợp.
5. **Kiểm tra `@Override`** và các quy tắc Java: kiểu tham số, return-type substitutability, access, `final`, `static`, checked exceptions…
6. Sau khi override hợp lệ, **xem erasure** để hiểu vì sao compiler có thể phát sinh phương thức bridge.

Bảng đối chiếu:

| Phương thức khai báo ở `Repository<T>` | Hợp đồng nhìn từ `Repository<String>` | Khai báo ở lớp con |
| --- | --- | --- |
| `T save(T)` | `String save(String)` | `String save(String)` |
| `List<T> items(T)` | `List<String> items(String)` | `List<String> items(String)` |
| `<N extends Number> N number(N)` | Vẫn generic với `N extends Number` | `<X extends Number> X number(X)` |

Điểm quyết định ở đây là **cùng một `T` thuộc lớp** xuất hiện trong nhiều vị trí đều được thay nhất quán. Không được chỉ thay kiểu tham số mà giữ nhầm kiểu trả về, hoặc thay type variable thuộc một phạm vi khai báo khác.

## 4. Kiểu tham số phải khớp theo hợp đồng kế thừa

### 4.1. `put(Object)` không tự ghi đè `put(String)`

```java
class Base<T> {
    void put(T value) {
    }
}

class Wrong extends Base<String> {
    // Không hợp lệ; javac có thể báo "name clash":
    void put(Object value) {
    }
}
```

Sau khi thay `T := String`, phương thức cha được nhìn theo hợp đồng source là `put(String)`. `put(Object)` của lớp con không trở thành `put(String)` chỉ vì `String` là subtype của `Object`.

Ví dụ trên đặc biệt nguy hiểm: `put(Object)` và phương thức cha có **cùng erasure** là `put(Object)` nhưng không hình thành một override hợp lệ. `javac` từ chối với lỗi dạng:

```text
name clash: put(Object) in Wrong and put(String) in Base
have the same erasure, yet neither overrides the other
```

Đây là lỗi khác với một overload thông thường. Khi thấy “same erasure, yet neither overrides the other”, hãy đối chiếu **chữ ký kế thừa sau substitution** và **chữ ký JVM sau erasure**, thay vì thử thêm cast vào body.

Sửa đúng:

```java
class Correct extends Base<String> {
    @Override
    void put(String value) {
        System.out.println(value);
    }
}
```

### 4.2. Khác type argument trong tham số không đồng nghĩa override

```java
import java.util.List;

class Base {
    void publish(List<String> values) {
    }
}

// Không phải bản override hợp lệ:
class Wrong extends Base {
    // void publish(List<Integer> values) {} // Name clash
}
```

`List<String>` và `List<Integer>` bị xóa về cùng kiểu `List`. Java không cho tạo một phương thức mới cùng tên/cùng erased parameter list nhưng khác type argument như vậy trong quan hệ kế thừa không tương thích. Nếu ý định là xử lý khác loại dữ liệu, hãy chọn chữ ký hoặc tên API khác phù hợp.

**Overload** chọn phương thức dựa trên danh sách tham số và luật overload; **override** thay implementation của một phương thức đã được kế thừa. Java không cho phép “overload bằng generic type argument” theo cách chỉ thay `List<String>` thành `List<Integer>`.

### 4.3. Đổi tên type parameter của phương thức thì khác

Đây là trường hợp hợp lệ:

```java
class Base {
    <T extends Number> T echo(T input) {
        return input;
    }
}

class Child extends Base {
    @Override
    <U extends Number> U echo(U input) {
        return input;
    }
}
```

Tên `T` và `U` khác nhau nhưng cấu trúc generic tương ứng. Sửa thành `<U extends CharSequence>` không còn cùng hợp đồng; không thể đổi tùy ý bound của phương thức đang override.

Đừng nhầm việc **đổi tên biến kiểu** (`T` → `U`) với việc **chuyên biệt hóa biến kiểu** (`T` → `String`). Việc đầu thường hợp lệ khi bound khớp; việc sau phụ thuộc biến đó được khai báo tại lớp hay tại phương thức.

## 5. Kiểu trả về: covariant, invariant và return-type substitutability

Một phương thức override phải có kiểu trả về hợp lệ theo quy tắc *return-type substitutability*. Trong các trường hợp tham chiếu thông dụng, kiểu trả về có thể là subtype thích hợp của kiểu cha sau khi thay type parameter.

Ví dụ hợp lệ:

```java
class Base<T> {
    T load() {
        return null;
    }
}

class Child extends Base<Number> {
    @Override
    Integer load() {
        return 10;
    }
}
```

Sau phép thay `T := Number`, `Base<Number>.load()` có hợp đồng trả `Number`; `Integer` là subtype của `Number`, nên việc thu hẹp kiểu trả về được cho phép. Đây là **covariant return**.

Một trường hợp dễ nhầm với generic parameterized return:

```java
import java.util.List;

class Base {
    List<Number> values() {
        return List.of(1);
    }
}

class Wrong extends Base {
    // @Override List<Integer> values() { return List.of(1); } // Sai
}
```

Dù `Integer extends Number`, `List<Integer>` **không phải subtype** của `List<Number>`: generic Java bất biến. Compiler không cho override bằng kiểu trả về này.

Nếu API thực sự chỉ hứa trả các phần tử kiểu số bất kỳ, một hợp đồng linh hoạt hơn có thể là:

```java
import java.util.List;

class Base {
    List<? extends Number> values() {
        return List.of(1, 2);
    }
}

class Child extends Base {
    @Override
    List<Integer> values() {
        return List.of(4, 5);
    }
}
```

`List<Integer>` tương thích với `List<? extends Number>` ở vai trò **kết quả để đọc các giá trị Number**. Nhưng đó là một **quyết định thiết kế của API cha**, không phải mẹo có thể tự áp dụng từ lớp con khi chữ ký của cha đã cố định.

### 5.1. Kiểu trả về `void` và primitive

Bạn không thể tùy ý đổi `void` thành một kiểu khác khi override. Với kiểu primitive, việc widening như `int` thành `long` không tạo ra covariant return như đối với subtype tham chiếu:

```java
class Base {
    int size() { return 0; }
}

class Wrong extends Base {
    // @Override long size() { return 0L; } // Sai
}
```

Khi debug lỗi override, đừng chỉ nhìn danh sách tham số; kiểu trả về vẫn phải hợp lệ dù nó không tham gia cách lập overload bằng source code.

## 6. Sau substitution vẫn có type erasure và bridge

Trở lại:

```java
class Repository<T> {
    T save(T value) {
        return value;
    }
}

class StringRepository extends Repository<String> {
    @Override
    String save(String value) {
        return value.strip();
    }
}
```

Compiler biết `StringRepository.save(String)` là một override hợp lệ. Nhưng descriptor của `Repository.save` sau xóa `T` không có bound là:

```text
(Ljava/lang/Object;)Ljava/lang/Object;
```

Trong khi phương thức được viết ở lớp con có descriptor:

```text
(Ljava/lang/String;)Ljava/lang/String;
```

Để đảm bảo lời gọi `save` đi qua một tham chiếu kiểu `Repository<String>` vẫn dispatch đến phương thức override của lớp con, compiler có thể sinh **bridge** trong `StringRepository`, có dạng mô tả:

```java
// MÃ KHÁI NIỆM DO COMPILER SINH, KHÔNG THÊM VÀO SOURCE:
Object save(Object input) {
    return save((String) input);
}
```

Với `@Override List<String> items(String)`, erasure của kiểu trả về `List<String>` thành `List`, và vì kiểu tham số `T` thành `Object` trong lớp cha, compiler cũng có thể sinh:

```text
items(Object) -> List
```

Bridge thông thường được đánh dấu `ACC_BRIDGE` và `ACC_SYNTHETIC` trong class file. Nó là phần hỗ trợ đa hình sau erasure; **không phải** một overload mà lập trình viên tự viết.

Không phải phương thức nào sau substitution cũng cần bridge. Trong ví dụ:

```java
<N extends Number> N number(N value)
```

phương thức lớp cha và phương thức lớp con (đổi tên type variable thành `X`) đều có descriptor sau erasure:

```text
(Ljava/lang/Number;)Ljava/lang/Number;
```

Nếu không có khác biệt descriptor cần nối, compiler không cần tạo bridge riêng cho phương thức đó.

### 6.1. Bridge có thể gây `ClassCastException` nếu phá hợp đồng qua raw type

Nếu code legacy dùng raw type:

```java
// Không khuyến nghị:
Repository raw = new StringRepository();
raw.save(123);
```

Lời gọi compile được với raw type (thường có cảnh báo unchecked). Bridge `save(Object)` của lớp con cố chuyển `123` thành `String`, nên phát sinh `ClassCastException` tại runtime. Đây không chứng minh generics “chỉ là kiểm tra giả”: lỗi xuất hiện vì code raw/unchecked đã bỏ qua hợp đồng `Repository<String>` mà compiler cần bảo vệ.

Trong code mới, dùng tham chiếu tham số hóa:

```java
Repository<String> safe = new StringRepository();
safe.save("Java");
```

Lời gọi `safe.save(123)` sẽ bị từ chối ở compile time.

## 7. Ví dụ Java 17 hoàn chỉnh

Tạo file **`OverrideSubstitutionDemo.java`**:

```java
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class OverrideSubstitutionDemo {
    static class Repository<T> {
        public T save(T value) {
            return value;
        }

        public List<T> items(T value) {
            return List.of(value);
        }

        public <N extends Number> N number(N value) {
            return value;
        }
    }

    static final class StringRepository extends Repository<String> {
        @Override
        public String save(String value) {
            return value.strip();
        }

        @Override
        public List<String> items(String value) {
            return List.of(save(value));
        }

        @Override
        public <X extends Number> X number(X value) {
            return value;
        }
    }

    static class NumberSource {
        public List<? extends Number> values() {
            return List.of(1, 2);
        }
    }

    static final class IntegerSource extends NumberSource {
        @Override
        public List<Integer> values() {
            return List.of(4, 5);
        }
    }

    public static void main(String[] args) {
        Repository<String> repository = new StringRepository();

        System.out.println(repository.save("  Java  "));
        System.out.println(repository.items("  Generics "));
        System.out.println(repository.number(42));
        System.out.println(new IntegerSource().values());

        Arrays.stream(StringRepository.class.getDeclaredMethods())
                .filter(Method::isBridge)
                .map(m -> m.getName() + "("
                        + Arrays.stream(m.getParameterTypes())
                                .map(Class::getSimpleName)
                                .collect(Collectors.joining(", "))
                        + ") -> " + m.getReturnType().getSimpleName())
                .sorted()
                .forEach(System.out::println);
    }
}
```

Biên dịch bằng Java 17, hoặc JDK mới hơn có hỗ trợ `--release 17`:

```bash
javac --release 17 OverrideSubstitutionDemo.java
java OverrideSubstitutionDemo
```

Stdout:

```text
Java
[Generics]
42
[4, 5]
items(Object) -> List
save(Object) -> Object
```

Phần đầu xác nhận các giá trị trả về giữ hợp đồng generic của `Repository<String>`. Hai dòng cuối được tạo bằng reflection, lọc `Method.isBridge()`, format tên/tham số/kiểu trả về và sắp xếp tên: compiler đã sinh bridge cho `items` và `save`. Phương thức generic `number` chỉ đổi tên biến kiểu mà không cần bridge vì descriptor cơ bản không thay đổi.

### 7.1. Xem signature bytecode

Sau `javac`, chạy:

```bash
javap -p -s 'OverrideSubstitutionDemo$StringRepository'
```

Dấu nháy đơn giữ nguyên ký tự `$` trong các shell có khai triển biến. Trong phần kết quả tương ứng, bạn có thể nhìn thấy:

```text
public java.lang.String save(java.lang.String);
  descriptor: (Ljava/lang/String;)Ljava/lang/String;

public java.util.List<java.lang.String> items(java.lang.String);
  descriptor: (Ljava/lang/String;)Ljava/util/List;

public <X extends java.lang.Number> X number(X);
  descriptor: (Ljava/lang/Number;)Ljava/lang/Number;

public java.util.List items(java.lang.Object);
  descriptor: (Ljava/lang/Object;)Ljava/util/List;

public java.lang.Object save(java.lang.Object);
  descriptor: (Ljava/lang/Object;)Ljava/lang/Object;
```

Để nhìn cờ của bridge:

```bash
javap -p -v 'OverrideSubstitutionDemo$StringRepository'
```

Tìm `ACC_BRIDGE` và `ACC_SYNTHETIC` ở hai phương thức chuyển tiếp. `javap -s` hiển thị descriptor; `javap -v` cho phép kiểm tra flags, Signature attribute và hướng gọi trong bytecode.

### 7.2. Kiểu tĩnh và kiểu runtime của cùng một đối tượng

```java
Repository<String> repository = new StringRepository();
String name = repository.save("  Java  ");
```

- Compiler nhìn `repository` theo `Repository<String>`; `save` nhận và trả `String`.
- Đối tượng thực là `StringRepository`.
- Khi cần, bridge của lớp con đảm bảo lời gọi tới descriptor đã xóa được chuyển tiếp đến `save(String)`.
- Giá trị kết quả là `"Java"` nhờ `.strip()`.
- Sự thay `T := String` **không tạo** một instance chuyên biệt có trường runtime để chứa tham số kiểu `String`.

Các cấp này phối hợp để code vẫn có type safety ở source và đa hình ở runtime.

## 8. Interface generic: thay tham số kiểu qua `implements`

Cơ chế tương tự diễn ra với interface:

```java
interface Transformer<I, O> {
    O transform(I input);
}

final class LengthTransformer implements Transformer<String, Integer> {
    @Override
    public Integer transform(String input) {
        return input.length();
    }
}
```

Với phép thay `I := String`, `O := Integer`, hợp đồng kế thừa là `Integer transform(String)`. Nếu khai báo `Object transform(Object)` ở lớp triển khai, nó không trở thành override đúng ý định chỉ vì các kiểu sau erasure của interface gốc cũng là `Object`. Kiểm tra source vẫn dựa trên **hợp đồng đã thay tham số kiểu**.

Một lớp có thể kế thừa tham số kiểu qua nhiều tầng:

```java
interface Sink<T> {
    void accept(T value);
}

abstract class BaseSink<U> implements Sink<U> {
}

final class StringSink extends BaseSink<String> {
    @Override
    public void accept(String value) {
        System.out.println(value);
    }
}
```

Lần theo `StringSink -> BaseSink<String> -> Sink<String>` là có thể xác định được `accept(String)`. Phép thay có thể diễn ra liên tiếp qua nhiều lớp mà không cần `StringSink` trực tiếp khai báo `implements Sink<String>`.

Khi phải debug một hierarchy phức tạp, hãy vẽ đường kế thừa và **ghi lại type argument tại mỗi cạnh**, trước khi đọc body hoặc thử ép kiểu.

### 8.1. Không thể triển khai cùng generic interface với hai type argument khác nhau

Một class không thể đồng thời nhận hai specialization không tương thích của cùng một generic interface theo các nhánh thừa kế khác nhau, ví dụ một nhánh `Sink<String>`, nhánh kia `Sink<Integer>`:

```java
interface Sink<T> {
    void accept(T value);
}

interface StringSink extends Sink<String> {
}

interface IntegerSink extends Sink<Integer> {
}

// Không hợp lệ:
// class Both implements StringSink, IntegerSink { ... }
```

Compiler ngăn lớp `Both` vì cùng một generic superinterface không thể được kế thừa với các tham số kiểu khác nhau trong một hierarchy. Đây là ràng buộc cần tính đến khi tổ chức interface dùng đa kế thừa.

## 9. Kiểu trả về generic và wildcard trong override

Wildcard không phải công cụ để tự do thay đổi kiểu tham số ở phương thức override. Nó hữu ích nhất khi **API cha được thiết kế có chủ đích** để cho phép kiểu trả về hẹp hơn:

```java
import java.util.List;

interface NumberProvider {
    List<? extends Number> get();
}

final class IntProvider implements NumberProvider {
    @Override
    public List<Integer> get() {
        return List.of(1, 2);
    }
}
```

Ở đây `List<Integer>` là subtype phù hợp của `List<? extends Number>` theo quan hệ kiểu generic có wildcard. Nhưng với phương thức nhận `List<Number>` làm tham số, lớp con không được ghi đè bằng `List<Integer>`: một `NumberProvider` được phép tiếp nhận danh sách `Number` nếu hợp đồng cha yêu cầu như vậy; thu hẹp khả năng chấp nhận đối số sẽ phá nguyên tắc thay thế.

Vì thế hãy phân biệt **covariant return** (có thể trả kiểu hẹp hơn khi hợp lệ) với việc **narrow parameter** (không tự tạo override chỉ bằng một subtype của tham số cha).

## 10. Quy tắc override khác vẫn có hiệu lực

Thay `T` bằng `String` chưa đủ để hợp đồng override đúng trong mọi tình huống. Các quy tắc Java khác vẫn áp dụng:

- **`static`**: static method không phải instance override; lớp con có thể *hide* static method trong những điều kiện ngôn ngữ cho phép.
- **`final`**: instance method `final` không được override dù chữ ký sau substitution khớp.
- **`private`**: phương thức `private` của lớp cha không được kế thừa để override.
- **Phạm vi truy cập**: không giảm visibility (ví dụ ghi đè `public` thành `protected`).
- **Checked exceptions**: phương thức override không được bổ sung checked exception rộng hơn trái hợp đồng đã kế thừa; việc substitution với generic type variable khai báo `throws` cũng phải thỏa quy tắc này.
- **Kiểu trả về**: phải return-type-substitutable; không được thay tùy tiện `List<Number>` thành `List<Integer>` hoặc đổi primitive return.
- **`@Override`**: giúp phát hiện ngay rằng phương thức dự định ghi đè thực ra chỉ là overload, name clash hoặc sai return type.

Điều kiện kiểm tra override được nêu chính xác trong Java Language Specification, mục **8.4.2 Method Signature**, **8.4.8 Inheritance, Overriding, and Hiding**, **8.4.5 Method Result** và các quy tắc generic tương ứng. Quy tắc đầy đủ còn xét *subsignature* và *override-equivalent*, vì vậy không nên cố đưa ra kết luận từ duy nhất một phép so sánh tên Java viết trong source.

### 10.1. Góc cạnh dành cho mã legacy: erasure của chữ ký phương thức generic

Java cho phép một số trường hợp *subsignature* dùng để giữ tương thích với API được generify, trong đó một chữ ký trùng với **erasure** của chữ ký kia. Điều này không đồng nghĩa mọi phương thức có “cùng erasure” đều ghi đè nhau. Ví dụ `put(Object)` ở `Base<String>` phía trên đã chứng minh ngược lại: cần xét quan hệ subsignature **sau khi thay tham số kiểu trong supertype**, không chỉ so sánh descriptor bytecode.

Nếu gặp code legacy có raw type, hãy làm rõ API gốc và chạy compiler với cảnh báo liên quan:

```bash
javac --release 17 -Xlint:unchecked -Xlint:rawtypes YourFile.java
```

Đây là lệnh minh họa để dùng với tệp thực trong dự án; thay `YourFile.java` bằng đúng tên source. Cảnh báo về raw type/unchecked là dấu hiệu nên kiểm tra hợp đồng generic, không phải lời khuyên mặc định thêm `@SuppressWarnings`.

## 11. Những lỗi biên dịch đặc trưng

### 11.1. `method does not override or implement a method from a supertype`

Nguyên nhân phổ biến:

```java
class Base {
    <T> void accept(T value) {
    }
}

class Child extends Base {
    @Override
    void accept(String value) {
    }
}
```

Phương thức của cha là **method generic**, không có tham số lớp để tự động thay thành `String`. Giải pháp phụ thuộc ý định:

- Muốn override phương thức generic: triển khai phương thức generic tương ứng, như `<U> void accept(U value)`.
- Muốn lớp con chỉ phục vụ chuỗi: chuyển hợp đồng tổng quát lên **type parameter của lớp cha** (`Base<T>`) rồi để con `extends Base<String>`.
- Muốn thêm hành vi riêng cho `String` và giữ API tổng quát: có thể viết overload `accept(String)` khi các chữ ký không clash, đồng thời nhận thức rằng nó **không override** method generic.

### 11.2. `name clash ... same erasure, yet neither overrides the other`

Nguyên nhân điển hình:

```java
class Base<T> {
    void accept(T value) {
    }
}

class Child extends Base<String> {
    void accept(Object value) {
    }
}
```

`accept(Object)` ở con không khớp `accept(String)` sau khi thay `T` trong supertype; nhưng descriptor erased của phương thức cha cũng dùng `Object`, gây clash. Cách sửa thông thường là `accept(String)` hoặc đổi tên/thiết kế API nếu thực sự cần hành vi xử lý mọi `Object`.

### 11.3. `return type ... is not compatible with ...`

Nguyên nhân:

```java
import java.util.List;

class Base {
    List<Number> read() {
        return List.of(1);
    }
}

class Child extends Base {
    @Override
    List<Integer> read() {
        return List.of(2);
    }
}
```

Phương thức con có danh sách tham số phù hợp, nhưng `List<Integer>` không return-type-substitutable cho `List<Number>`. Cách sửa là giữ `List<Number>` ở override; hoặc, nếu sở hữu API cha và thực sự cần tính linh hoạt, cân nhắc kiểu trả về `List<? extends Number>` trong hợp đồng cha.

### 11.4. `ClassCastException` trong bridge sau khi dùng raw type

Hãy tìm luồng dữ liệu đi qua tham chiếu raw hoặc unchecked cast. Nếu một `Repository<String>` thật bị dùng qua `Repository` raw để truyền `Integer`, bridge có thể phải cast đầu vào thành `String` và ném lỗi. Giải pháp là sửa **đầu vào vi phạm hợp đồng**, không xóa bridge.

## 12. Quy trình rà soát khi đọc code dự án

Với một phương thức con bị nghi không override được:

1. **Xác định chính xác supertype đã tham số hóa**: chẳng hạn `extends Repo<String>` hoặc `implements Handler<Long>`.
2. **Lần theo các cấp kế thừa** và lập phép thay cho type parameter **của class/interface** tại từng cấp.
3. **Viết lại chữ ký của phương thức cha theo góc nhìn lớp con**, gồm tham số và return. Phân biệt type parameter của phương thức với type parameter của class.
4. **Kiểm tra method subsignature và return-type substitutability**; đừng chỉ dựa vào quan hệ subtype của một đối số.
5. **Giữ `@Override`** trên implementation dự định ghi đè để compiler báo sớm khi sai.
6. Nếu có lỗi `name clash`, **tính erasure của hai chữ ký** và xác minh chúng có cùng JVM descriptor phần tham số hay không.
7. Nếu việc ghi đè compile được nhưng bytecode có phương thức bất ngờ, **dùng `javap -p -s` và `javap -v`**, hoặc `Method.isBridge()`.
8. Nếu runtime lỗi kiểu, tìm raw types, unchecked casts, heap pollution hoặc dữ liệu vi phạm hợp đồng, thay vì coi bridge tự phát sinh là nguyên nhân gốc.

Bảng nhanh:

| Dấu hiệu | Điều cần phân biệt | Hướng xử lý |
| --- | --- | --- |
| `extends Base<String>` và `Base<T>.set(T)` | `T` của class | Override bằng `set(String)`. |
| `Base.<T> set(T)` | `T` của method | Override bằng method generic tương ứng; `set(String)` là overload (khi hợp lệ). |
| `List<Integer>` trả thay `List<Number>` | Generic invariant | Giữ đúng kiểu hợp đồng hoặc thiết kế wildcard ở API cha nếu được phép. |
| `name clash` với `set(Object)` | Source subsignature khác erasure | Chỉnh đúng kiểu tham số sau phép thay hoặc đổi thiết kế API. |
| `javap` cho thấy `save(Object)` trong lớp con | Bridge do compiler tạo | Kiểm tra bridge flag và đường dispatch; không xóa phương thức synthetic. |
| Raw reference gọi subclass qua `Object` | Bỏ qua generic contract | Loại raw/unchecked API và sửa giá trị truyền vào. |

## 13. Bài luyện tập

**Bài 1.** Với:

```java
interface Store<T> {
    T add(T value);
}

class IntStore implements Store<Integer> {
    // Khai báo override nào?
}
```

<details>
<summary>Đáp án</summary>

```java
@Override
public Integer add(Integer value) {
    return value;
}
```

`T` thuộc interface `Store<T>` nên thay thành `Integer` khi `implements Store<Integer>`. Do `Store.add(T)` bị erasure về `add(Object):Object`, compiler có thể cần bridge trong implementation.

</details>

**Bài 2.** Phương thức `<X extends Number> X copy(X x)` của cha có được override bằng `<Y extends Number> Y copy(Y y)` không? Có được đổi thành `String copy(String)` không?

<details>
<summary>Đáp án</summary>

Đổi tên `X` thành `Y` với bound tương ứng là hợp lệ. `String copy(String)` không chuyên biệt hóa method generic này thành override; nếu không gây xung đột với phương thức được kế thừa, nó là overload và vẫn cần implementation generic khi lớp cha/interface yêu cầu.

</details>

**Bài 3.** Vì sao `StringRepository` có thể có `save(String)` và phương thức synthetic `save(Object)` trong class file, trong khi code Java chỉ khai báo một phương thức `save`?

<details>
<summary>Đáp án</summary>

`Repository<T>.save(T):T` sau erasure có descriptor `(Object):Object`, còn override cụ thể của con có `(String):String`. Compiler tạo bridge `(Object):Object` chuyển lời gọi đến `(String):String` để bảo toàn đa hình sau erasure.

</details>

**Bài 4.** Bạn muốn `List<Integer>` là kiểu trả về của override. API cha trả `List<Number>` hiện tại có cho phép không? Nếu đang thiết kế API mới thì lựa chọn nào diễn tả ý “danh sách những phần tử thuộc Number”?

<details>
<summary>Đáp án</summary>

Không thể trực tiếp override `List<Number>` bằng `List<Integer>` vì invariance. Nếu muốn chỉ đọc giá trị Number và chấp nhận nhiều loại phần tử số, API cha có thể công bố `List<? extends Number>`; `List<Integer>` khi đó là return type tương thích.

</details>

## 14. Ghi nhớ

**Thay thế tham số kiểu khi ghi đè** bắt đầu ở quan hệ `extends`/`implements`: `Base<T>` trở thành `Base<String>` thì những phương thức dùng **`T` của class** phải được nhìn theo `String` khi kiểm tra override. **Type parameter của method** được khai báo ở cấp phương thức không bị chuyên biệt hóa theo cách đó; nó phải tiếp tục giữ hợp đồng generic của mình, dù có thể được đổi tên biến kiểu.

Sau khi xác nhận override đúng ở source, **erasure** có thể khiến descriptor JVM của lớp cha và lớp con khác nhau; Java compiler tạo **bridge method** để giữ đa hình. Vì vậy đọc một lỗi về ghi đè generic cần phân biệt rõ **substitution ở source**, **quy tắc override**, và **descriptor/bridge ở bytecode**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-overrides-and-bridges-002">Chữ ký sau xóa kiểu và khái niệm subsignature</a>

<details>
<summary>Xem chi tiết</summary>

# Chữ ký sau xóa kiểu và khái niệm subsignature trong Java Generics

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt **chữ ký phương thức trong ngôn ngữ Java**, **chữ ký sau xóa kiểu** (*erased signature*) và **method descriptor** ở bytecode.
2. Áp dụng định nghĩa **subsignature** để xác định chiều ghi đè hợp lệ giữa một phương thức generic/parameterized và phiên bản có chữ ký đã xóa kiểu.
3. Giải thích vì sao hai phương thức có cùng erasure đôi khi ghi đè hợp lệ, đôi khi gây lỗi `name clash`.
4. Hiểu mối liên hệ giữa ghi đè ở mã nguồn, *type erasure*, *bridge method* và dispatch động ở JVM.
5. Chẩn đoán lỗi bằng `javac`, `@Override` và `javap`, rồi chọn cách sửa tránh raw type trong thiết kế API mới.

Bối cảnh: Java generics chủ yếu được hiện thực bằng **type erasure**. Trong khi đó, ghi đè vẫn phải đúng về mặt hệ thống kiểu của mã nguồn. Hai cơ chế gặp nhau ở quy tắc **subsignature**, vốn hỗ trợ sự tương thích khi thư viện tiến hóa từ API cũ sang API có generics.

## 1. “Chữ ký phương thức” nghĩa là gì?

Theo quy tắc chữ ký phương thức Java (JLS `8.4.2), chữ ký xét tới **tên**, **các tham số kiểu của chính phương thức khi có**, và **kiểu của danh sách tham số hình thức** sau khi áp dụng các quy tắc tương ứng về tương đương kiểu và đổi tên tham số kiểu.

Ví dụ:

```java
// Các ví dụ minh họa chữ ký, không đặt cùng phạm vi vì chúng đụng chữ ký:
void find(java.util.List<String> values);
void find(java.util.List<Integer> values);

<T> void transfer(T value);
<U> void transfer(U value);
```

Hai khai báo `find` không có cùng chữ ký nguồn vì tham số hóa `List<String>` và `List<Integer>` khác nhau. Tuy nhiên, chúng sẽ **trùng erasure**, nên không thể khai báo chúng như hai overload trong cùng một lớp. Hai khai báo `transfer` thực chất có **cùng chữ ký** sau khi đổi tên tham số kiểu (`T` ↔ `U`), nên cũng không thể đặt cạnh nhau trong một lớp.

Các yếu tố **không thuộc chữ ký phương thức Java** gồm:

- **Kiểu trả về**: không thể overload chỉ bằng cách đổi `String output()` thành `Object output()`.
- Tên biến tham số: `void log(String name)` và `void log(String text)` cùng chữ ký.
- `throws`: `void read() throws IOException` và `void read()` không tạo hai overload.
- Các modifier như `public`, `private`, `static`, `final`: chúng tác động tính hợp lệ của kế thừa/ghi đè, không làm hai phương thức khác chữ ký.
- Annotation và phần thân phương thức.

Dạng varargs `String...` và dạng mảng `String[]` cho cùng loại tham số mảng trong việc xác định chữ ký; không thể dùng chúng làm hai overload độc lập. Nhưng vì varargs còn quy định cách truyền đối số tại vị trí gọi, hai khai báo có thể khác trải nghiệm khi sử dụng dù chữ ký tương đương.

Đừng nhầm **chữ ký nguồn** với **descriptor của JVM**: descriptor mã hóa kiểu tham số **và kiểu trả về** ở bytecode. Chúng phục vụ hai cấp độ khác nhau; phần 6 sẽ giải thích.

## 2. Type erasure biến chữ ký như thế nào?

Ở bước biên dịch, type erasure loại bỏ hoặc quy về dạng thích hợp các phần tham số hóa trong kiểu. Một số phép biến đổi quan trọng:

| Kiểu được viết trong Java | Kiểu sau erasure | Ý nghĩa |
| --- | --- | --- |
| `List<String>` | `List` | Đối số generic bị loại khỏi kiểu tham chiếu. |
| `Map<String, Integer>` | `Map` | Không còn hai đối số generic trong mô tả kiểu đã xóa. |
| `T`, với `T` không có bound tường minh | `Object` | Giới hạn ngầm là `Object`. |
| `T extends Number` | `Number` | Lấy erasure của giới hạn bên trái. |
| `T extends Number & Comparable<T>` | `Number` | Erasure của type variable dựa trên giới hạn đầu tiên. |
| `List<String>[]` | `List[]` | Erasure áp dụng tới thành phần mảng. |
| `int`, `String`, `String[]` | Không đổi | Các kiểu không cần loại tham số generic vẫn giữ kiểu tương ứng. |

Ví dụ minh họa:

```java
// Trước xóa kiểu:
<T> T identity(T value);
<T extends Number> T twice(T value);
void display(java.util.List<String> values);

// Hình dung dạng mô tả sau xóa kiểu:
// Object identity(Object value);
// Number twice(Number value);
// void display(java.util.List values);
```

Đây là **sơ đồ khái niệm**: đừng sao chép cả hai nhóm vào một lớp như những phương thức riêng. Java compiler có thể giữ thông tin generic trong metadata `Signature` để hỗ trợ reflection/tooling, nhưng descriptor và lời gọi JVM sử dụng kiểu đã xóa khi thích hợp.

**Chữ ký sau xóa kiểu của một phương thức** là chữ ký có cùng tên, với kiểu của từng tham số hình thức được thay bằng erasure tương ứng. Để xác định nó, bạn nên nhìn vào **tham số** và kiểu generic; kiểu trả về được xóa kiểu trong quá trình dịch mã nhưng **không tham gia định nghĩa chữ ký phương thức nguồn**.

Ví dụ:

- `void submit(List<String> values)` → erasure của chữ ký: `submit(List)`.
- `void submit(List<Integer> values)` → erasure của chữ ký: `submit(List)`.
- `<T> void inspect(T value)` → erasure của chữ ký: `inspect(Object)`.
- `<T extends Number> void inspect(T value)` → erasure của chữ ký: `inspect(Number)`.

Vì vậy, hai phương thức khác biệt ở kiểu đối số generic có thể trở thành cùng một điểm gọi sau erasure.

## 3. Định nghĩa subsignature và chiều quan hệ

Gọi `sig(m)` là chữ ký phương thức `m` trong Java và `erasure(sig(m))` là chữ ký sau xóa kiểu.

**Chữ ký của `m1` là subsignature của `m2` nếu và chỉ nếu xảy ra một trong hai trường hợp:**

1. `sig(m1)` **giống** `sig(m2)` theo quy tắc chữ ký Java; hoặc
2. `sig(m1)` **giống chữ ký đã xóa kiểu của `m2`**.

Nói gọn:

```text
m1 subsignature m2
⇔ sig(m1) == sig(m2)
   hoặc sig(m1) == erasure(sig(m2))
```

Dấu `==` ở sơ đồ trên biểu thị **tương đương chữ ký theo quy tắc Java**, không phải toán tử so sánh đối tượng hay phép so sánh chuỗi ký tự thông thường.

Điểm dễ bỏ sót: **subsignature có hướng**. Nếu `m1` là subsignature của `m2`, không thể tự động kết luận chiều ngược lại cũng đúng. Trường hợp cùng chữ ký thì hai chiều đều đúng; trường hợp chỉ bằng erasure thì có thể đúng duy nhất một chiều.

Ví dụ:

```java
// Phương thức m1:
void accept(java.util.List items);

// Phương thức m2:
void accept(java.util.List<String> items);
```

- `sig(m1) = accept(List)`.
- `sig(m2) = accept(List<String>)`.
- `erasure(sig(m2)) = accept(List)`.
- **m1 là subsignature của m2** vì chữ ký của m1 bằng erasure của m2.
- **m2 không là subsignature của m1**: `accept(List<String>)` không giống `accept(List)`, cũng không giống erasure của `accept(List)`.

Hai phương thức minh họa này **không được đặt cạnh nhau trong cùng một lớp**. Quan hệ subsignature hữu ích khi xét ghi đè giữa các khai báo ở những lớp/interface có quan hệ kế thừa.

### Override-equivalent khác subsignature như thế nào?

Hai chữ ký là **override-equivalent** nếu chữ ký thứ nhất là subsignature của chữ ký thứ hai **hoặc ngược lại**. Khái niệm này có tính đối xứng, khác với việc kiểm tra một chiều của subsignature.

Tuy nhiên, việc hai chữ ký override-equivalent **không tự động đảm bảo một phương thức thực sự ghi đè phương thức kia**. Java vẫn xét quan hệ kế thừa, phương thức có được kế thừa/hiện diện phù hợp, `static` hay instance, quyền truy cập, kiểu trả về tương thích, ngoại lệ checked và các quy tắc liên quan.

Ví dụ `static` có thể ẩn một phương thức `static` khác (*hiding*) thay vì override. Phương thức `private` ở lớp cha không được ghi đè theo cách phương thức instance được kế thừa. Một lớp cũng không thể override một phương thức `final`.

## 4. Vì sao Java có quy tắc subsignature?

Xét API cũ trước generics:

```java
// Phiên bản API cũ, minh họa:
class Library {
    void consume(java.util.List items) { }
}

class Client extends Library {
    @Override
    void consume(java.util.List items) { }
}
```

Sau này, tác giả thư viện muốn mô tả phần tử rõ ràng hơn:

```java
// Phiên bản Library đã được generic hóa:
class Library {
    void consume(java.util.List<String> items) { }
}
```

Chữ ký `Client.consume(List)` là subsignature của `Library.consume(List<String>)`, nên phương thức cũ ở `Client` vẫn có thể override phương thức được generic hóa. Đây là một khả năng hỗ trợ tương thích trong quá trình chuyển API cũ sang generics.

Chiều ngược lại **không tương đương**. Nếu lớp cha có `consume(List)`, còn lớp con cố khai báo `consume(List<String>)` thì chữ ký ở lớp con **không phải subsignature** của chữ ký lớp cha. Vì hai chữ ký có cùng erasure nhưng không ghi đè đúng, trình biên dịch thường báo `name clash`.

Có thể nhớ bằng bảng:

| Khai báo ở lớp cha | Khai báo ở lớp con | Kết quả |
| --- | --- | --- |
| `void f(List<String> x)` | `void f(List x)` | Có thể override nhờ subsignature theo erasure; raw type có hạn chế. |
| `void f(List x)` | `void f(List<String> x)` | Không phải override hợp lệ; `name clash`. |
| `<T> void f(T x)` | `void f(Object x)` | Có thể override vì `f(Object)` là erasure của `f(T)`. |
| `void f(Object x)` | `<T> void f(T x)` | Không phải chiều override hợp lệ; có thể `name clash`. |
| `<T> void f(T x)` | `<U> void f(U x)` | Cùng chữ ký sau điều chỉnh tên tham số kiểu; có thể override. |

Bảng xét tới quy tắc chữ ký, với giả định phương thức là instance và các yêu cầu override khác đều đáp ứng. Nó không biến raw type thành lựa chọn mặc định: bản thân `List` không có tham số kiểu nên giảm độ an toàn và có thể tạo cảnh báo `rawtypes`.

## 5. Chương trình Java 17: ghi đè hợp lệ và cầu nối

Đây là chương trình độc lập, thể hiện ba tình huống liên quan:

- Override phương thức generic bằng chữ ký đã xóa kiểu.
- Override phương thức nhận `List<String>` bằng `List` ở phía lớp triển khai.
- Ghi đè khi một lớp generic được chuyên biệt hóa, dẫn đến bridge method; đồng thời quan sát cầu nối cho kiểu trả về hiệp biến.

**Tệp `ErasureSignatureDemo.java`:**

```java
import java.lang.reflect.Method;
import java.util.List;

public class ErasureSignatureDemo {
    static class Base {
        public <T> String render(T value) {
            return "base:" + value;
        }
    }

    static final class LegacyOverride extends Base {
        @Override
        public String render(Object value) {
            return "legacy:" + value;
        }
    }

    interface Catalog {
        void add(List<String> names);
    }

    static final class RawCatalog implements Catalog {
        @Override
        public void add(List names) {
            System.out.println("added:" + names.size());
        }
    }

    static class Box<T> {
        T echo(T input) {
            return input;
        }
    }

    static final class StringBox extends Box<String> {
        @Override
        String echo(String input) {
            return "str:" + input;
        }
    }

    static class Animal {
        Animal copy() {
            return this;
        }
    }

    static final class Dog extends Animal {
        @Override
        Dog copy() {
            return this;
        }
    }

    public static void main(String[] args) {
        Base base = new LegacyOverride();
        System.out.println(base.render(42));

        new RawCatalog().add(List.of("A", "B"));

        Box<String> box = new StringBox();
        System.out.println(box.echo("Java"));

        for (Method method : StringBox.class.getDeclaredMethods()) {
            if (method.isBridge()) {
                System.out.println("bridge:" + method.getName()
                    + "(" + method.getParameterTypes()[0].getSimpleName()
                    + ")->" + method.getReturnType().getSimpleName());
            }
        }

        Animal animal = new Dog();
        System.out.println(animal.copy().getClass().getSimpleName());

        for (Method method : Dog.class.getDeclaredMethods()) {
            if (method.isBridge()) {
                System.out.println("covariant bridge:" + method.getName()
                    + "()->" + method.getReturnType().getSimpleName());
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 ErasureSignatureDemo.java
java ErasureSignatureDemo
```

**Stdout đã kiểm chứng:**

```text
legacy:42
added:2
str:Java
bridge:echo(Object)->Object
Dog
covariant bridge:copy()->Animal
```

Chương trình dùng Java 17 trở lên. Với `javac --release 17 -Xlint:rawtypes ErasureSignatureDemo.java`, trình biên dịch cảnh báo một raw type tại tham số `List names` ở `RawCatalog`; đó là điều **có chủ đích để minh họa tương thích API cũ**. Trong ứng dụng mới, ưu tiên viết `void add(List<String> names)` đúng với interface nếu bạn không cần chứng minh cơ chế subsignature.

### 5.1. Vì sao `LegacyOverride.render(Object)` ghi đè được?

Ở `Base`, `render` là **phương thức generic**:

```java
public <T> String render(T value)
```

Type variable `T` không có bound tường minh, nên erasure của tham số `T` là `Object`. `LegacyOverride.render(Object)` có chữ ký bằng erasure của phương thức lớp cha, tức là subsignature của nó. Vì thế `@Override` hợp lệ, và lời gọi qua biến `Base` vẫn thực thi mã lớp con:

```text
legacy:42
```

Không cần tạo bridge chỉ để kết nối hai chữ ký này: chữ ký mã máy liên quan đã sử dụng tham số `Object`. Các giá trị trả về ở đây đều là `String`.

### 5.2. Vì sao `RawCatalog.add(List)` được chấp nhận?

`Catalog.add(List<String>)` có erasure tham số là `List`. Chữ ký `RawCatalog.add(List)` đúng bằng erasure đó, nên được phép override. Khi chạy, method in kích thước danh sách là `2`.

`RawCatalog` minh họa việc một mã cũ dùng raw `List` có thể tiếp tục đáp ứng hợp đồng được bổ sung generic. Raw type có thể cho phép các thao tác phần tử không an toàn ở mã triển khai; subsignature **không tự xác thực dữ liệu bên trong List** và không loại bỏ cảnh báo compiler.

### 5.3. Vì sao `StringBox.echo(String)` cần bridge?

`Box<T>` có phương thức `T echo(T)`. Trong ngữ cảnh `StringBox extends Box<String>`, kiểu `T` được thay bằng `String` để kiểm tra override ở mã nguồn, nên `String echo(String)` ghi đè hợp lệ.

Nhưng khi dịch `Box<T>`, erasure của `T` là `Object`. JVM vẫn có thể gọi `Box.echo(Object)` thông qua biến kiểu `Box<String>`. Trình biên dịch vì thế tạo một phương thức **bridge**, thường có dạng logic tương đương:

```java
// Minh họa cơ chế cầu nối do javac tự sinh, KHÔNG tự chép vào StringBox:
Object echo(Object input) {
    return echo((String) input);
}
```

Bridge mang descriptor đã xóa kiểu, ép đối số về `String` rồi chuyển lời gọi tới `StringBox.echo(String)`. Kết quả chương trình in:

```text
str:Java
bridge:echo(Object)->Object
```

`Method.isBridge()` cho phép nhận diện bridge qua reflection. Phương thức bridge thường cũng được đánh dấu synthetic (`Method.isSynthetic()`); trong ví dụ này, `javap` xác nhận cờ `ACC_BRIDGE` và `ACC_SYNTHETIC`.

Quan trọng: **subsignature là một quy tắc trong quá trình xác định ghi đè ở cấp nguồn; bridge là mã do compiler sinh để giữ đúng hành vi sau erasure.** Không phải hễ có subsignature thì phải sinh bridge: hai ví dụ `render` và `add` phía trên ghi đè hợp lệ mà không cần một bridge tương ứng.

### 5.4. Bridge do kiểu trả về hiệp biến

`Dog.copy()` trả về `Dog` trong khi `Animal.copy()` trả về `Animal`. Kiểu trả về con hợp lệ theo quy tắc *covariant return*. Khi nhìn ở bytecode, descriptor có chứa kiểu trả về, nên compiler có thể tạo cầu nối:

```text
Dog.copy()    -> Dog
Dog.copy()    -> Animal  // bridge, mô tả dạng logic
```

Lời gọi qua biến `Animal animal = new Dog()` vẫn trả lại đối tượng Dog; chương trình in `Dog`. Đây là ví dụ quan trọng vì **bridge method không chỉ có thể xuất hiện từ generics**; kiểu trả về hiệp biến cũng có thể dẫn tới bridge.

## 6. Chữ ký Java và JVM descriptor khác nhau ở đâu?

Đặt hai khai báo:

```java
// Minh họa:
class Parent<T> {
    T get(T value) {
        return value;
    }
}

class Child extends Parent<String> {
    @Override
    String get(String value) {
        return value;
    }
}
```

Chúng hợp lệ nếu đặt trong cùng tệp theo dạng các lớp phù hợp. Ở mã nguồn, compiler biết `Parent<String>` nên thấy `Child.get(String)` override phương thức thừa kế chuyên biệt thành `String get(String)`.

Nhưng class file của `Parent` vẫn dùng kiểu đã xóa:

```text
Parent.get(T)
  descriptor: (Ljava/lang/Object;)Ljava/lang/Object;

Child.get(String)
  descriptor: (Ljava/lang/String;)Ljava/lang/String;

Child.get(Object)  // bridge thường được javac sinh
  descriptor: (Ljava/lang/Object;)Ljava/lang/Object;
```

Descriptor JVM thể hiện các kiểu với ký hiệu nội bộ: `Ljava/lang/String;` là tham chiếu `String`, `Ljava/lang/Object;` là tham chiếu `Object`, chữ `V` cho kết quả `void`. JVM descriptor **gồm cả kiểu trả về**. Java source signature thì **không** dùng kiểu trả về để quyết định một overload khác.

Để kiểm tra chương trình phần 5 sau khi biên dịch:

```bash
javap -p -v ErasureSignatureDemo$StringBox
javap -p -v ErasureSignatureDemo$Dog
```

Trên shell Unix-like, hãy trích dẫn tên chứa dấu `$` để shell không coi đó là biến, ví dụ:

```bash
javap -p -v 'ErasureSignatureDemo$StringBox'
javap -p -v 'ErasureSignatureDemo$Dog'
```

Trên PowerShell, đặt tên này trong dấu nháy đơn như trên cũng an toàn. Nếu chỉ cần danh sách phương thức ở cấp source-like, có thể dùng `javap -p ...`; cờ `-v` mới hiển thị chi tiết descriptor, flags và metadata.

Đầu ra `javap -v` cho các phương thức liên quan trong `StringBox` (lược riêng những phần cần quan sát) bao gồm:

```text
java.lang.String echo(java.lang.String);
  descriptor: (Ljava/lang/String;)Ljava/lang/String;

java.lang.Object echo(java.lang.Object);
  descriptor: (Ljava/lang/Object;)Ljava/lang/Object;
  flags: (0x1040) ACC_BRIDGE, ACC_SYNTHETIC
```

Ở `Dog`, cũng có hai dạng `copy()`: một trả về `Dog`, một bridge trả về `Animal`. Đây là lý do không nên nhìn mỗi phương thức do người lập trình viết để suy đoán toàn bộ phương thức có thể xuất hiện qua reflection ở runtime.

**Lưu ý:** Thông tin generic được lưu bằng metadata `Signature` khi cần thiết, nhưng `Signature` không biến `List<String>` và `List<Integer>` thành hai descriptor JVM khác nhau. Java không hỗ trợ overload chỉ dựa trên đối số kiểu generic của cùng một lớp tham số sau erasure.

## 7. Name clash: cùng erasure nhưng không thể override

### Trường hợp A: hai overload chỉ khác đối số generic

**Tệp `ErasureOverloads.java` — cố ý không biên dịch:**

```java
import java.util.List;

public class ErasureOverloads {
    void log(List<String> a) {}
    void log(List<Integer> a) {} // Lỗi: trùng erasure
}
```

Lệnh:

```bash
javac --release 17 ErasureOverloads.java
```

Thông báo lỗi trọng tâm mà `javac` đưa ra:

```text
name clash: log(List<Integer>) and log(List<String>) have the same erasure
```

Cả hai bị xóa thành `log(List)`. Không có đủ khác biệt về kiểu tham số ở cấp đã xóa để biểu diễn chúng như hai overload Java độc lập. Cách giải quyết phù hợp tùy nhu cầu: đặt tên khác như `logStrings` và `logIntegers`, hoặc dùng **một** phương thức `log(List<T>)` có hợp đồng chung. Thêm một tham số thực sự khác kiểu đã xóa cũng làm chữ ký khác, nhưng đừng thêm tham số giả chỉ để lách lỗi nếu nó không có ý nghĩa thiết kế.

### Trường hợp B: đặt `List<String>` ở lớp con của API raw

**Tệp `WrongDirection.java` — cố ý không biên dịch:**

```java
import java.util.List;

class LegacyBase {
    void save(List items) {}
}

public class WrongDirection extends LegacyBase {
    @Override
    void save(List<String> items) {}
}
```

Lệnh:

```bash
javac --release 17 WrongDirection.java
```

Hai thông báo quan trọng:

```text
name clash: save(List<String>) in WrongDirection and save(List) in LegacyBase
have the same erasure, yet neither overrides the other
method does not override or implement a method from a supertype
```

Chiều subsignature bị đảo: `save(List<String>)` của lớp con **không** là subsignature của `save(List)` ở lớp cha. Nếu cần override API cũ, dùng `save(List)` đúng chữ ký kế thừa; nếu kiểm soát API cha và muốn tăng tính an toàn, hãy cân nhắc generic hóa chính hợp đồng cha sau khi đánh giá tác động tương thích.

### Trường hợp C: chuyên biệt hóa một phương thức generic tùy ý

**Tệp `GenericMethodClash.java` — cố ý không biên dịch:**

```java
import java.util.List;

class GenericBase {
    <T> void use(List<T> items) {}
}

public class GenericMethodClash extends GenericBase {
    void use(List<String> items) {}
}
```

Lệnh:

```bash
javac --release 17 GenericMethodClash.java
```

Thông báo lỗi trọng tâm:

```text
name clash: use(List<String>) in GenericMethodClash and
use(List<T>) in GenericBase have the same erasure,
yet neither overrides the other
```

`GenericBase.use` là **phương thức generic**: mỗi lời gọi của nó có thể lựa chọn T. Một phương thức con chỉ nhận `List<String>` không thể được xem là triển khai hợp đồng hoạt động với mọi T. Erasure của hai tham số đều là `List`, khiến xung đột trở thành lỗi biên dịch thay vì một overload độc lập.

**Phân biệt với generic ở cấp lớp:** trong `class Box<T> { T echo(T v); }`, `Box<String>` cố định T thành String **cho việc nhìn nhận phương thức kế thừa trong kiểu đó**. Vì vậy `StringBox.echo(String)` override hợp lệ, với bridge giải quyết sự khác nhau ở bytecode. Còn `<T> void use(List<T>)` ở `GenericBase` có T thuộc **riêng phương thức**, lớp con không được biến giao kèo “mọi T” thành “chỉ String”.

## 8. Cách nhận biết overload, override và name clash

Khi gặp hai phương thức cùng tên, hãy phân tích theo thứ tự:

1. **Đặt chúng vào đúng ngữ cảnh kiểu.** Xác định phương thức ở cùng lớp, ở lớp cha/lớp con, hay ở hai interface. Với generic cấp lớp, thay tham số kiểu theo quan hệ kế thừa trước khi so các phương thức ở cấp mã nguồn.
2. **So chữ ký nguồn.** Nếu cùng chữ ký, không thể là hai overload trong cùng lớp; ở quan hệ kế thừa có thể là override nếu những điều kiện khác thỏa mãn.
3. **Kiểm tra subsignature đúng chiều.** Muốn xét `mChild` override `mParent`, hỏi liệu chữ ký của **child** có giống chữ ký **parent** hoặc erasure của parent hay không.
4. **So erasure của từng tham số.** Nếu hai phương thức có cùng erasure nhưng không thể override theo luật Java, bạn có nguy cơ `name clash`.
5. **Kiểm tra điều kiện ghi đè bổ sung.** `static`/instance, `private`, `final`, khả năng truy cập, kiểu trả về tương thích và checked exceptions đều có thể làm khai báo không hợp lệ dù chữ ký đã phù hợp.
6. **Kiểm tra cách Java dịch sang bytecode khi cần.** Nếu phương thức chuyên biệt có descriptor khác phương thức được thừa kế sau erasure, compiler có thể thêm bridge để giữ dispatch đúng.

Bảng đối chiếu nhanh:

| Hai phương thức | Chữ ký nguồn | Erasure | Kết luận trong ngữ cảnh chỉ rõ |
| --- | --- | --- | --- |
| Cùng lớp: `f(List<String>)` và `f(List<Integer>)` | Khác | Trùng `f(List)` | `name clash`, không overload được. |
| Cùng lớp: `f(String)` và `f(Integer)` | Khác | Khác | Có thể overload. |
| Cha `f(List<String>)`, con `f(List)` | Khác | Trùng | Con có thể override nhờ subsignature một chiều. |
| Cha `f(List)`, con `f(List<String>)` | Khác | Trùng | `name clash`; không override. |
| Cha `<T> f(T)`, con `f(Object)` | Khác | Trùng | Con có thể override; `Object` là erasure của T không bound. |
| Cha `<T> f(T)`, con `<U> f(U)` | Cùng sau đổi tên type variable | Trùng | Có thể override nếu các quy tắc khác hợp lệ. |
| `Box<T>.echo(T)`, `Box<String>` có `echo(String)` | Phù hợp sau chuyên biệt hóa T | Descriptor có thể khác | Override hợp lệ; compiler có thể tạo bridge. |

Trong bảng, các chữ ký rút gọn như `f(List)` chỉ minh họa phần tham số; các điều kiện khác của method override vẫn áp dụng.

## 9. Các hiểu nhầm quan trọng

**Nhầm 1 — “Cứ có cùng erasure là override”.** Sai. Cùng erasure có thể là điều kiện dẫn tới `name clash`. Phải kiểm tra subsignature và các quy tắc override khác.

**Nhầm 2 — “Subsignature là quan hệ hai chiều”.** Sai. Với một khai báo raw và một khai báo parameterized, chiều raw → parameterized có thể đáp ứng tiêu chí bằng erasure; chiều ngược lại không mặc nhiên đúng.

**Nhầm 3 — “Hai phương thức khác kiểu trả về thì có chữ ký khác”.** Sai ở cấp nguồn Java. Kiểu trả về không tham gia chữ ký phương thức nguồn; quy tắc return-type-substitutability mới quyết định tính hợp lệ của override. Tuy vậy, descriptor JVM có chứa kiểu trả về, tạo điều kiện để compiler sinh bridge cho covariant return.

**Nhầm 4 — “Bridge là overload do người lập trình thêm”.** Không đúng. Bridge là phương thức do compiler sinh để duy trì nghĩa ghi đè ở bytecode. Không nên tự viết thủ công một phương thức có chữ ký erasure trùng với bridge dự kiến; điều này có thể gây xung đột hoặc phá vỡ hợp đồng.

**Nhầm 5 — “Đổi từ `List<String>` sang `List<Integer>` là overload hợp lệ”.** Không đúng: hai kiểu tham số hóa có cùng erasure. Có thể cần tên API khác hoặc một phương thức generic chung.

**Nhầm 6 — “Muốn sửa `name clash` thì cast sang raw List”.** Cast ở vị trí gọi không thay đổi chữ ký của hai khai báo đang xung đột. Phải sửa chữ ký/thiết kế phương thức.

**Nhầm 7 — “Không thấy bridge qua source nên JVM không có”.** Compiler có thể sinh bridge trong class file. Dùng `Method.isBridge()` hoặc `javap -p -v` để quan sát khi cần chẩn đoán.

## 10. Thực hành chẩn đoán bằng công cụ

Với trường hợp nghi ngờ sai override:

1. **Đặt `@Override`** ở phương thức instance lớp con. Nếu compiler báo không ghi đè, đừng bỏ annotation ngay; xem lại chữ ký, chiều subsignature và kiểu tham số sau khi thay generic của superclass.
2. Biên dịch với cảnh báo khi đụng API cũ: `javac --release 17 -Xlint:rawtypes -Xlint:unchecked File.java`. `rawtypes` cảnh báo raw type, `unchecked` cảnh báo các phép toán không được compiler chứng minh hoàn toàn an toàn.
3. Với `name clash`, ghi riêng hai chữ ký nguồn và erasure vào giấy hoặc bảng rồi kiểm tra chiều `child → parent`. Một thay đổi type argument như `List<String>` sang `List<Object>` thường **không** giải quyết đụng erasure.
4. Nếu override được chấp nhận nhưng hành vi dispatch khó hiểu, dùng `javap -p -v 'TênLớp$LớpCon'`, tìm `descriptor:` và `ACC_BRIDGE`/`ACC_SYNTHETIC`.
5. Với reflection, `getDeclaredMethods()` có thể trả về cả bridge. Nếu muốn xử lý **phương thức do tác giả khai báo**, bạn cần cân nhắc lọc `isBridge()` và `isSynthetic()` theo mục đích tool; đừng vô điều kiện lọc nếu đang phân tích ABI hoặc runtime dispatch.

Khi thiết kế API mới, ưu tiên kiểu tham số hóa đầy đủ như `List<String>`. Raw method override là một cơ chế tương thích cần hiểu, nhưng thường không phải cách viết mới mong muốn.

## 11. Tự kiểm tra

### Câu 1

`Class A` có `void put(List<String> xs)`; `Class B extends A` có `void put(List xs)`. Chữ ký nào là subsignature của chữ ký nào?

**Đáp án:** `B.put(List)` là subsignature của `A.put(List<String>)` vì bằng erasure của khai báo ở A. Với điều kiện override khác phù hợp, B override được A.

### Câu 2

Đảo lại: A có `void put(List xs)`, B có `void put(List<String> xs)`. Điều gì xảy ra?

**Đáp án:** B không override được A vì chữ ký parameterized không bằng chữ ký raw của A hay erasure của nó. Hai khai báo có cùng erasure; compiler báo `name clash`.

### Câu 3

Có thể viết `void show(List<String> xs)` và `void show(List<Long> xs)` làm hai overload trong cùng lớp không?

**Đáp án:** Không. Cả hai có erasure `show(List)`.

### Câu 4

`class Parent<T>` có `T id(T x)`. `class Child extends Parent<String>` khai báo `@Override String id(String x)`. Vì sao override hợp lệ nhưng có thể xuất hiện phương thức `id(Object)` khi xem bytecode?

**Đáp án:** Ở cấp nguồn, kiểu T của Parent được chuyên biệt hóa thành String khi xét Child. Ở cấp bytecode, Parent được dịch với erasure Object; compiler thêm bridge `id(Object)` để chuyển đến `id(String)` và duy trì dispatch.

### Câu 5

Phương thức trả về `String` và phương thức cùng tên, cùng tham số nhưng trả về `Object` có phải hai overload được không?

**Đáp án:** Không. Kiểu trả về không thuộc chữ ký nguồn Java; sự thay đổi kiểu trả về chỉ có thể hợp lệ trong quan hệ ghi đè tương ứng nếu đáp ứng quy tắc return-type-substitutability, hoặc do compiler sinh bridge ở bytecode.

### Câu 6

Có phải phương thức nào là subsignature của phương thức khác đều cần một bridge?

**Đáp án:** Không. `render(Object)` override `render(T)` trong ví dụ vẫn cùng descriptor sau erasure, nên không cần bridge riêng cho trường hợp đó. Bridge cần thiết ở những tình huống compiler phải duy trì phân phối phương thức giữa các descriptor JVM khác nhau.

## 12. Ghi nhớ

- **Signature Java**: tên và kiểu tham số (kèm quy tắc tham số kiểu phương thức); không dùng kiểu trả về hay `throws` để phân biệt overload.
- **Erased signature**: tên và kiểu tham số sau khi áp dụng erasure; `List<String>` và `List<Integer>` cùng trở thành `List`.
- **Subsignature**: chữ ký m1 bằng chữ ký m2 **hoặc** bằng erasure chữ ký m2; đây là quan hệ **có hướng**.
- **Override-equivalent**: subsignature đúng ở ít nhất một chiều; việc override thực tế còn phải đáp ứng luật kế thừa và kiểu trả về.
- **Name clash**: chữ ký trùng sau erasure nhưng không thể cùng tồn tại/ghi đè theo quy tắc Java.
- **Bridge method**: phương thức do compiler sinh để giữ dispatch đúng sau erasure hoặc khi có kiểu trả về hiệp biến; quan sát bằng `javap` và `Method.isBridge()`.

Hiểu đồng thời các quy tắc này giúp bạn giải thích chính xác vì sao compiler chấp nhận một override dùng chữ ký đã xóa kiểu, từ chối một phương thức trông có vẻ “chuyên biệt hơn”, và tạo bridge dù mã nguồn chỉ khai báo một phương thức ghi đè.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-overrides-and-bridges-003">Phương thức cầu nối (bridge method) bảo toàn đa hình</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức cầu nối (bridge method) bảo toàn đa hình trong Java Generics

## 1. Vấn đề: sau xóa kiểu, JVM vẫn phải gọi đúng phương thức ghi đè

Trong Java, **đa hình** có nghĩa là khi gọi một phương thức có thể ghi đè qua tham chiếu lớp cha hoặc interface, việc thực thi phải chọn phần cài đặt phù hợp với lớp thực tế của đối tượng.

~~~java
Box<String> box = new StringBox();
box.set("java");
String result = box.get();
~~~

Với một lớp cơ sở generic `Box<T>` và lớp con `StringBox extends Box<String>`, lập trình viên mong đợi `set` và `get` ghi đè ở `StringBox` hoạt động khi gọi qua `Box<String>`.

Nhưng **type erasure (xóa kiểu)** tạo ra khác biệt giữa chữ ký kiểu ở source Java và descriptor mà JVM sử dụng:

- Ở `Box<T>`, tham số `T` không có giới hạn tường minh nên bị xóa thành **Object**.
- Ở `StringBox`, phương thức chuyên biệt có tham số hoặc kiểu trả về **String**.
- JVM phân giải phương thức dựa trên tên và descriptor; chữ ký `set(Object)` khác `set(String)`, còn `get():Object` khác `get():String` ở mức descriptor.

Để bảo toàn quy tắc ghi đè đã được compiler xác định từ source Java, **javac có thể tự sinh phương thức cầu nối (bridge method)**. Đây là một phương thức có bytecode thật, chuyển lời gọi từ chữ ký sau erasure sang phần cài đặt chuyên biệt.

Với `set`, bridge cần ép kiểu đối số Object về String rồi chuyển tiếp đến `set(String)`. Với `get`, bridge có thể chỉ cần gọi `get():String` và trả kết quả ở dạng Object.

Bridge method là một chi tiết do compiler tạo để bảo toàn **tính đa hình và tương thích nhị phân**, không phải phương thức người lập trình bắt buộc phải viết thủ công.

## 2. Hình dung trường hợp điển hình

Giả sử hai lớp:

~~~java
class Box<T> {
    private T value;

    public void set(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }
}

class StringBox extends Box<String> {
    @Override
    public void set(String value) {
        super.set(value.toUpperCase());
    }

    @Override
    public String get() {
        return super.get();
    }
}
~~~

Ở **source Java**, `set(String)` và `get():String` của `StringBox` ghi đè hợp lệ hai phương thức generic của `Box<String>`.

Sau xóa kiểu, các phương thức cơ sở về mặt descriptor tương ứng:

~~~text
Box.set(java.lang.Object): void
Box.get(): java.lang.Object
~~~

Còn các phương thức khai báo trực tiếp trong `StringBox` tương ứng:

~~~text
StringBox.set(java.lang.String): void
StringBox.get(): java.lang.String
~~~

Không có các bridge, lời gọi dùng descriptor `set(Object)` từ lớp cơ sở có thể bỏ qua chuyên biệt hóa `set(String)` ở lớp con. Javac vì vậy sinh thêm các điểm vào có descriptor tương thích với bản đã xóa của lớp cha.

Có thể hình dung `StringBox` **gần tương đương** với các phương thức bổ sung sau:

~~~java
// PSEUDOCODE: không được chép trực tiếp vào StringBox,
// vì chữ ký và ràng buộc nguồn Java có thể gây name clash.
public void set(Object value) {
    set((String) value);
}

public Object get() {
    return get(); // Chuyển đến get() trả String theo descriptor riêng.
}
~~~

Hai phương thức trên là **mã minh họa bytecode ở dạng Java giả lập**, không phải source hợp lệ cần viết thêm. Trong bytecode, JVM phân biệt các descriptor khác nhau; ở Java source, overload không thể chỉ khác kiểu trả về và cố viết `set(Object)` có thể xung đột erasure.

**Điểm mấu chốt:** `@Override` và kiểm tra kiểu generic diễn ra trên chương trình Java ban đầu. Compiler chịu trách nhiệm tạo cầu nối cần thiết để quan hệ ghi đè ấy vẫn đúng sau khi loại bỏ phần lớn thông tin type parameter.

## 3. Chương trình Java 17+ hoàn chỉnh

Lưu nội dung sau trong tệp **BridgeMethodDemo.java**:

~~~java
import java.lang.reflect.Method;
import java.util.Arrays;

public class BridgeMethodDemo {
    static class Box<T> {
        private T value;

        public void set(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }
    }

    static final class StringBox extends Box<String> {
        @Override
        public void set(String value) {
            super.set(value.toUpperCase());
        }

        @Override
        public String get() {
            return super.get();
        }
    }

    public static void main(String[] args) {
        // Lời gọi qua kiểu cha generic phải chọn override của StringBox.
        Box<String> box = new StringBox();
        box.set("java");
        System.out.println("value=" + box.get());

        // Reflection phát hiện các phương thức cầu nối compiler tạo.
        System.out.println(
                "bridge names=" +
                Arrays.stream(StringBox.class.getDeclaredMethods())
                        .filter(Method::isBridge)
                        .map(Method::getName)
                        .sorted()
                        .toList()
        );

        // Raw type bỏ kiểm tra generic ở phía gọi và có cảnh báo.
        Box raw = box;
        try {
            raw.set(123);
        } catch (ClassCastException ex) {
            System.out.println("raw call=ClassCastException");
        }

        System.out.println("value after=" + box.get());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -Xlint:unchecked BridgeMethodDemo.java
java BridgeMethodDemo
~~~

Do gọi `raw.set(123)` trên raw `Box`, trình biên dịch sẽ đưa ra **unchecked warning**. Cảnh báo nằm ở đầu ra biên dịch, không phải stdout của chương trình.

Stdout khi chạy:

~~~text
value=JAVA
bridge names=[get, set]
raw call=ClassCastException
value after=JAVA
~~~

Giải thích:

1. `box` có kiểu tĩnh `Box<String>` nhưng chứa đối tượng `StringBox`. Gọi `box.set("java")` phải thực hiện override tại `StringBox`, đổi dữ liệu thành chữ hoa. Kết quả `box.get()` là `JAVA`.
2. `StringBox.class.getDeclaredMethods()` bao gồm các phương thức do compiler tạo ngay trên `StringBox`. `Method.isBridge()` nhận diện bridge methods. Ví dụ này có bridge cho cả `set` và `get`; danh sách được sắp xếp để thứ tự in ổn định.
3. Tham chiếu raw `Box` cho phép gọi `set(123)` ở compile time kèm cảnh báo. Tại runtime, lời gọi `set(Object)` được phân phối tới bridge của `StringBox`, nơi compiler chèn **checkcast String** trước khi chuyển tiếp đến `set(String)`.
4. Đối số là Integer, không phù hợp String nên bridge ném `ClassCastException`. Giá trị cũ trong Box không bị thay đổi; lời gọi cuối cùng vẫn in `JAVA`.

**Lưu ý:** Raw type ở đây chỉ để quan sát kiểm tra runtime bên trong bridge. API thực tế nên giữ `Box<String>`; câu lệnh `box.set(123)` bình thường sẽ bị javac từ chối ngay tại compile time.

## 4. Kiểm tra bridge method trong bytecode bằng javap

Sau khi biên dịch, chạy:

~~~bash
javap -c -p 'BridgeMethodDemo$StringBox'
~~~

Tùy shell, cần quote tên nested class để ký tự `$` không bị diễn giải; trên Windows CMD có thể dùng dấu nháy kép. Có thể xem thêm cờ synthetic và bridge ở class file:

~~~bash
javap -v -p 'BridgeMethodDemo$StringBox'
~~~

Bạn sẽ thấy **cả các phương thức String chuyên biệt lẫn các phương thức do compiler tạo**. Dạng mô tả bytecode quan trọng với `set`:

~~~text
public void set(java.lang.Object);
  ... aload_0
  ... aload_1
  ... checkcast java/lang/String
  ... invokevirtual set:(Ljava/lang/String;)V
  ... return
~~~

Bridge của `get` có dạng khái niệm:

~~~text
public java.lang.Object get();
  ... aload_0
  ... invokevirtual get:()Ljava/lang/String;
  ... areturn
~~~

Offset, số constant-pool và một vài chi tiết opcode có thể khác nhau giữa các bản javac. Điều cần kiểm tra là **descriptor tương thích với lớp cơ sở, cờ bridge/synthetic và lời gọi chuyển tiếp tới phương thức chuyên biệt**.

Nếu dùng `javap -v`, bridge do javac tạo thường có cờ **ACC_BRIDGE** và **ACC_SYNTHETIC**. Reflection có các hàm tương ứng:

~~~java
method.isBridge();
method.isSynthetic();
~~~

Trong Java, hai thuộc tính có ý nghĩa khác nhau: `isBridge` cho biết method là cầu nối, còn `isSynthetic` chỉ method không khai báo trực tiếp trong mã nguồn; không phải mọi synthetic method đều là bridge.

### JVM descriptor cần hiểu đúng

Với `Box<T>` không có bound tường minh:

| Phương thức | Descriptor ở JVM |
| --- | --- |
| `Box.set(T)` sau erasure | `(Ljava/lang/Object;)V` |
| `StringBox.set(String)` | `(Ljava/lang/String;)V` |
| Bridge `StringBox.set(Object)` | `(Ljava/lang/Object;)V` |
| `Box.get()` sau erasure | `()Ljava/lang/Object;` |
| `StringBox.get():String` | `()Ljava/lang/String;` |
| Bridge `StringBox.get():Object` | `()Ljava/lang/Object;` |

Ký tự `V` đại diện cho void, còn `Ljava/lang/String;` đại diện cho reference type java.lang.String. JVM có thể phân biệt các method descriptor bao gồm cả kiểu trả về; Java source không cho overload chỉ dựa trên kiểu trả về.

## 5. Phương thức cầu nối cho generic interface

Bridge method không chỉ xuất hiện khi một lớp extends lớp cha generic. Interface generic cũng có vấn đề tương tự:

~~~java
interface Converter<T> {
    T convert(T input);
}

final class TrimConverter implements Converter<String> {
    @Override
    public String convert(String input) {
        return input.trim();
    }
}
~~~

Ở source, `convert(String)` ghi đè hợp lệ `convert(T)` sau khi `T` được thay bằng String.

Sau erasure, method trong interface có descriptor gần như:

~~~text
Converter.convert(Object) -> Object
~~~

Trong `TrimConverter`, phương thức chuyên biệt:

~~~text
TrimConverter.convert(String) -> String
~~~

Javac thêm bridge tương thích `convert(Object) -> Object`. Bridge kiểm tra Object thành String trước khi gọi phương thức chuyên biệt và trả kết quả như Object.

Ví dụ áp dụng:

~~~java
Converter<String> converter = new TrimConverter();
String result = converter.convert("  Java  "); // "Java"
~~~

Nếu thiếu cầu nối, cơ chế gọi qua interface generic sau erasure có thể không tìm đúng phần cài đặt chuyên biệt. Cấu trúc bridge giúp lời gọi interface vẫn hoạt động như hợp đồng override trong Java source.

**Đây là đoạn minh họa để ghép vào chương trình có chứa các định nghĩa cùng đoạn**, không phải một tệp Java độc lập vì chưa có hàm main hoặc public class.

## 6. Phương thức có bound: erasure không phải lúc nào cũng thành Object

Cận kiểu có thể làm kiểu sau erasure khác Object.

~~~java
class NumberHolder<T extends Number> {
    public void accept(T input) {
        System.out.println(input.intValue());
    }
}

final class IntegerHolder extends NumberHolder<Integer> {
    @Override
    public void accept(Integer input) {
        System.out.println(input + 1);
    }
}
~~~

Trong `NumberHolder`, erasure của T là **Number** vì `T extends Number`. Vì thế descriptor của phương thức cơ sở nhận Number, còn override nhận Integer:

~~~text
NumberHolder.accept(Number)
IntegerHolder.accept(Integer)
bridge IntegerHolder.accept(Number) → cast Integer → accept(Integer)
~~~

Bridge **không bắt buộc dùng Object**. Nó phải khớp chữ ký ở phía lớp cơ sở/interface sau erasure. Quy tắc erasure của type variable lấy cận trái nhất, nên nếu có giới hạn class/interface phức tạp hơn, cách phát sinh cầu nối cũng dựa trên chữ ký runtime thực tế.

Nếu dùng raw NumberHolder và truyền một đối tượng Number không phải Integer tới một IntegerHolder, bridge có thể ném ClassCastException khi checkcast Integer. API có kiểu `NumberHolder<Integer>` bình thường sẽ từ chối đối số không phù hợp ngay khi biên dịch.

## 7. Bridge cũng xuất hiện khi trả về kiểu hiệp biến

**Covariant return type** cho phép override trả về subtype của kiểu trả về gốc:

~~~java
class Animal {
    Animal copy() {
        return new Animal();
    }
}

final class Dog extends Animal {
    @Override
    Dog copy() {
        return new Dog();
    }
}
~~~

Ngay cả khi **không có generics**, kiểu trả về của `Animal.copy()` và `Dog.copy()` khác nhau ở JVM descriptor. Compiler có thể sinh cầu nối với descriptor trả Animal trong Dog để duy trì dispatch đúng qua tham chiếu Animal.

Từ đó, quy tắc “bridge method luôn do generics tạo ra” là không đúng. Có ít nhất hai tình huống thường gặp: **xóa kiểu làm thay đổi chữ ký ghi đè** và **override có kiểu trả về hiệp biến**.

Ví dụ:

~~~java
Animal animal = new Dog();
Animal duplicate = animal.copy();
System.out.println(duplicate instanceof Dog); // true
~~~

Đa hình được giữ: phương thức thực hiện thuộc Dog và trả về Dog, mặc dù biến ngoài có kiểu Animal.

## 8. Cách bridge bảo toàn đa hình, từng bước

Với ví dụ Box/StringBox, khi gọi:

~~~java
Box<String> box = new StringBox();
box.set("java");
~~~

một cách hình dung logic của quá trình là:

1. Javac kiểm tra `StringBox.set(String)` ghi đè `Box<String>.set(String)` hợp lệ ở mức source.
2. Sau erasure, lời gọi qua `Box` mang descriptor `set(Object)`.
3. StringBox có bridge `set(Object)`, cùng descriptor cần thiết để override ở bytecode.
4. Dispatch động chọn bridge tại đối tượng runtime StringBox.
5. Bridge ép đối số thành String, rồi gọi `StringBox.set(String)`.
6. Phương thức chuyên biệt thực hiện chuyển chữ hoa; kết quả nhận được tuân thủ ý nghĩa đa hình của source.

Tương tự với get:

~~~java
String value = box.get();
~~~

Lời gọi qua `Box` có descriptor trả Object sau erasure; JVM có thể dispatch vào bridge get():Object trong StringBox, bridge gọi get():String thực sự, rồi caller sử dụng kết quả như String. Javac có thể chèn **checkcast String tại caller** vì caller nhận Object từ lời gọi đã xóa kiểu.

Vì thế có thể gặp **hai kiểu kiểm tra khác vị trí** trong cùng ví dụ:

- Cast trong bridge **set(Object)** kiểm tra đối số trước khi chuyển sang set(String).
- Cast tại phía caller **get()** kiểm tra kết quả trả Object trước khi dùng như String.

Chúng phục vụ những thao tác khác nhau. Bridge method là **một phương thức sinh ra**, còn checkcast là **một opcode**; không nên coi chúng là cùng một khái niệm.

## 9. Vì sao không nên tự viết một bridge method?

Cố tự viết phương thức `set(Object)` cạnh `set(String)` trong lớp con generic có thể gặp **name clash** vì erasure hoặc làm thay đổi hợp đồng overload/override, tùy chữ ký cụ thể.

Ví dụ **cố ý không biên dịch**:

~~~java
class Base<T> {
    void set(T value) {}
}

class BadChild extends Base<String> {
    void set(Object value) {} // Name clash với phương thức được kế thừa.
}
~~~

Vì `set(T)` bị xóa thành `set(Object)`, khai báo ở lớp con không thể dùng tùy ý để “thay bridge” trong source Java. Nếu lớp con muốn ghi đè đúng phương thức đã được chuyên biệt hóa, hãy viết:

~~~java
class GoodChild extends Base<String> {
    @Override
    void set(String value) {}
}
~~~

Sau đó để compiler phát sinh bridge khi cần. Với phương thức generic đúng chuẩn, `@Override` là công cụ quan trọng: nó yêu cầu compiler xác minh rằng chữ ký thực sự ghi đè một phương thức đã tồn tại.

## 10. Phân biệt bridge, overload, override và method hiding

| Cơ chế | Ý nghĩa | Khi nào dispatch tới phần cài đặt lớp con? |
| --- | --- | --- |
| **Override** | Lớp con thay thế implementation của một instance method được kế thừa theo quy tắc Java. | Gọi instance method qua lớp cha/interface vẫn chọn override theo runtime object. |
| **Overload** | Cùng tên phương thức nhưng khác danh sách tham số ở source. | Chọn overload chủ yếu tại compile time theo kiểu biểu thức và tham số. |
| **Bridge method** | Compiler sinh thêm phương thức với descriptor phù hợp, chuyển tiếp đến implementation chuyên biệt. | Hỗ trợ override vẫn hoạt động khi erasure/covariant return làm descriptor khác nhau. |
| **Method hiding** | Phương thức static trong subclass che phương thức static cùng chữ ký của parent. | Static method không tham gia dynamic dispatch như instance override; bridge không làm static hiding thành override. |

Ví dụ overload:

~~~java
class Printer {
    void print(Object value) {
        System.out.println("Object");
    }
    void print(String value) {
        System.out.println("String");
    }
}
~~~

Lựa chọn giữa `print(Object)` và `print(String)` dựa vào kiểu tĩnh của đối số khi biên dịch. Điều này khác với vấn đề bridge: bridge giúp **một override đã được xác định** tiếp tục tham gia dynamic dispatch đúng sau erasure.

## 11. Kiểm tra bridge bằng Reflection

JDK cung cấp:

~~~java
Method.isBridge()
Method.isSynthetic()
Method.getParameterTypes()
Method.getReturnType()
Method.getDeclaredMethods()
~~~

Ví dụ kiểm tra có phân biệt:

~~~java
for (Method method : StringBox.class.getDeclaredMethods()) {
    if (method.isBridge()) {
        System.out.println(method.getName() + ": " + method.isSynthetic());
    }
}
~~~

Mảnh code trên giả định StringBox đã được khai báo cùng scope. Thứ tự từ `getDeclaredMethods()` **không được API bảo đảm**, nên nếu muốn có output lặp lại ổn định thì cần `sorted()` theo tên hoặc descriptor.

Một phương thức bridge do javac sinh thông thường cũng synthetic, nhưng bạn nên dùng `isBridge()` để xác định đúng bridge thay vì suy luận từ `isSynthetic()` một mình. Reflection có thể nhìn thấy những method compiler-generated mà source code không có khai báo tường minh, điều này bình thường.

### Khi reflection tạo nhiều phương thức “trùng tên”

Công cụ quét API có thể thấy nhiều `get()`, `set(...)` hoặc `convert(...)` mà tên có vẻ trùng nhau. Đừng vội kết luận class định nghĩa API thừa. Hãy kiểm tra:

- `isBridge()` và `isSynthetic()`.
- Danh sách tham số từ `getParameterTypes()`.
- Kiểu trả về từ `getReturnType()`.
- Quan hệ lớp cha/interface generic và những override chuyên biệt.

Khi tạo tài liệu API, nhiều công cụ chọn loại bỏ hoặc ẩn bridge method khỏi danh sách API dành cho người lập trình, nhưng vẫn giữ chúng trong bytecode để đảm bảo hành vi thực thi.

## 12. Những lỗi và ngộ nhận thường gặp

### Nhầm “erasure phá đa hình”

Type erasure có thể thay đổi chữ ký ở bytecode, nhưng javac hỗ trợ bridge để giữ hợp đồng overriding hợp lệ đã xác định ở source. Một lời gọi qua kiểu cha sẽ vẫn chọn phần cài đặt lớp con như mong đợi.

### Nhầm “StringBox được phép nhận Integer vì set sau erasure nhận Object”

Chữ ký source của `Box<String>.set` nhận String. Nếu dùng reference được tham số hóa đúng, compiler chặn Integer. Đối số không đúng kiểu chỉ có thể lọt qua đường raw/unchecked hay lối truy cập không an toàn khác; bridge có thể phát hiện lỗi runtime.

### Nhầm “bridge = cast”

Bridge là method có thể chứa checkcast, invoke và return. Còn checkcast là chỉ thị kiểm tra tham chiếu. Bridge trả về Object từ get():String thường không cần cast String ngay bên trong bridge; cast của caller là chuyện khác.

### Nhầm “mọi phương thức generic đều sinh bridge”

Generic class không tự động cần bridge chỉ vì có T. Bridge xuất hiện khi phải bắc cầu chữ ký/descriptor để quan hệ override hoạt động đúng. Nếu lớp con không chuyên biệt hóa override hoặc descriptor vẫn tương thích, có thể không có bridge.

### Nhầm “bridge luôn trả Object”

Descriptor phải phù hợp với phương thức ở phía lớp cha/interface sau erasure. Nếu type variable có bound, descriptor có thể sử dụng Number, Serializable hoặc một class/interface tương ứng. Trường hợp covariant return không generic cũng có thể trả một lớp cha khác Object.

### Nhầm “tên và số lượng bridge là hợp đồng public API”

Hình dạng bridge phụ thuộc các phương thức ghi đè, hierarchy, compiler và target bytecode. Không nên viết logic nghiệp vụ dựa trên số bridge hoặc offset bytecode của một bản javac cụ thể. Kiểm tra những thuộc tính đó chỉ phù hợp cho học tập, phân tích và công cụ chuyên biệt.

## 13. Khi nào cần quan tâm bridge method trong công việc thực tế?

**Khi debug ClassCastException:** nếu stack trace dính đến một method synthetic/bridge hoặc một lời gọi raw generic, xem tham số runtime có phù hợp subtype mà lớp con thực sự chấp nhận hay không. Một lời gọi raw `set(Object)` có thể bị bridge ép thành String và thất bại.

**Khi dùng reflection hoặc tạo framework:** bridge có thể xuất hiện thêm trong `getDeclaredMethods()`. Nếu quét annotation, xây routing hoặc đăng ký handler, cần xác định method thực sự do người dùng viết và phương thức bridge chuyển tiếp, tránh xử lý nhầm một phương thức hai lần. Quy tắc mapping annotation/bridge còn phụ thuộc framework đang dùng.

**Khi phân tích bytecode:** so sánh `javap -c -p` của superclass generic và subclass chuyên biệt, xác định descriptor, ACC_BRIDGE/ACC_SYNTHETIC, checkcast và lệnh invoke chuyển tiếp.

**Khi thiết kế class generic:** viết override đúng kiểu và thêm `@Override`. Không can thiệp vào bridge do compiler tạo và không phụ thuộc giả định erasure khiến JVM tự đoán kiểu generic ở runtime.

**Khi đánh giá binary compatibility:** bridge là một trong các cơ chế giúp class file và lời gọi biên dịch với descriptor đã xóa tương thích. Tuy nhiên, thay đổi API public, generic bounds hoặc quan hệ kế thừa vẫn có thể tạo lỗi tương thích; bridge không bảo đảm mọi kiểu thay đổi thư viện đều an toàn.

## 14. Câu hỏi tự kiểm tra

**1.** Vì sao `StringBox.set(String)` cần bridge tương thích `set(Object)`?

**Đáp án:** Sau erasure, chữ ký phương thức `Box.set(T)` là `set(Object)`. Bridge có descriptor ấy để dynamic dispatch qua Box vẫn đi tới override set(String).

**2.** Khi tham chiếu raw Box gọi `set(123)` trên StringBox, tại sao có thể ném ClassCastException trước khi vào set(String)?

**Đáp án:** Bridge set(Object) kiểm tra đối số bằng checkcast String rồi mới chuyển tiếp. Integer không tương thích String nên phép kiểm tra thất bại.

**3.** Bridge get():Object có bắt buộc ép kiểu String ở trong bridge không?

**Đáp án:** Không. Nó có thể gọi get():String và trả tham chiếu đó như Object. Caller dùng kết quả đã xóa kiểu như String có thể thực hiện checkcast String riêng.

**4.** `method.isSynthetic()` có đủ để kết luận method là bridge không?

**Đáp án:** Không. Synthetic bao gồm các thành viên do compiler tạo vì nhiều lý do; cần kiểm tra `isBridge()` để nhận diện bridge.

**5.** Bridge method có tồn tại khi không sử dụng generics không?

**Đáp án:** Có thể có khi ghi đè dùng kiểu trả về hiệp biến, chẳng hạn Animal.copy():Animal và Dog.copy():Dog.

**6.** Nếu generic type parameter có bound `T extends Number`, bridge nhận Object hay Number?

**Đáp án:** Chữ ký sau erasure của T dùng Number, nên bridge cho override chuyên biệt nhận Number khi cần.

**Ghi nhớ:** **Bridge method là cầu nối bytecode do compiler tạo để quan hệ override trong Java source vẫn đúng sau erasure hoặc thay đổi kiểu trả về hiệp biến.** Khi gọi qua superclass/interface, JVM vẫn phân phối lời gọi đến phần cài đặt lớp con; bridge chuyển descriptor và có thể thực hiện cast cần thiết. Hiểu bridge giúp giải thích chính xác đa hình, unchecked calls, reflection và lỗi cast trong chương trình generic.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-overrides-and-bridges-004">Đối chiếu mã Java với bytecode bằng javap</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu mã Java với bytecode bằng `javap`: ghi đè generic và phương thức cầu nối

## Mục tiêu học tập

Bài này thuộc chủ đề **Ghi đè và cầu nối (overriding and bridge methods)** trong Java generics. Sau khi hoàn thành, bạn có thể:

- Đọc một phương thức `@Override` ở Java source và tìm phương thức tương ứng trong tệp `.class`.
- Giải thích vì sao trình biên dịch có thể tạo **bridge method** ngoài những phương thức do lập trình viên khai báo.
- Phân biệt `Signature` mô tả thông tin generic với **JVM descriptor** dùng để gọi phương thức.
- Dùng `javap -s`, `javap -c`, `javap -v` để kiểm tra descriptor, bytecode, cờ `ACC_BRIDGE` và `ACC_SYNTHETIC`.
- Theo dõi đúng luồng gọi phương thức qua biến kiểu cha và dự đoán nơi phát sinh `ClassCastException` khi dùng raw type sai.
- Nhận ra cầu nối do erasure, cầu nối liên quan đến kiểu trả về hiệp biến và ảnh hưởng của upper bound không phải `Object`.

Mọi phần kết quả bên dưới được đối chiếu với ví dụ Java 17 chạy thực tế. Các đoạn trích `javap` chỉ giữ những dòng liên quan; các vị trí bytecode/constant-pool có thể khác tùy JDK.

## 1. Vấn đề: mã Java nói “ghi đè”, descriptor lại khác nhau

Xét hai kiểu:

```java
class Processor<T> {
    public T transform(T value) {
        return value;
    }
}

class UppercaseProcessor extends Processor<String> {
    @Override
    public String transform(String value) {
        return value.toUpperCase();
    }
}
```

Nhìn từ Java source, `UppercaseProcessor` kế thừa `Processor<String>`. Sau khi thay `T = String` để kiểm tra kiểu, phương thức `transform(String)` trong lớp con **ghi đè** phương thức `transform(T)` của lớp cha. Chú thích `@Override` được trình biên dịch chấp nhận.

Nhưng `Processor<T>` khai báo `T` **không có giới hạn tường minh** nên type erasure thay `T` bằng `Object`. Sau biên dịch:

| Nơi khai báo | Nhìn từ Java source | JVM descriptor |
| --- | --- | --- |
| `Processor<T>` | `T transform(T)` | `(Ljava/lang/Object;)Ljava/lang/Object;` |
| `UppercaseProcessor` | `String transform(String)` | `(Ljava/lang/String;)Ljava/lang/String;` |

Nếu runtime chỉ thấy phương thức `transform(String)` ở lớp con, lời gọi qua `Processor<String>` vẫn có thể tham chiếu đến chữ ký đã xóa `transform(Object)`, dẫn tới vấn đề giữ đúng ngữ nghĩa ghi đè. `javac` giải quyết bằng một **phương thức cầu nối** có descriptor tương thích với phương thức của lớp cha. Cầu nối nhận `Object`, ép sang `String`, rồi ủy quyền cho phương thức `transform(String)` thật.

Phương thức cầu nối này **không xuất hiện trong source**. Nó được trình biên dịch tạo tự động vào tệp `.class` để bảo đảm tính đa hình sau type erasure.

## 2. Chương trình Java 17 có thể chạy và kiểm tra

Tạo tệp **`BridgeInspection.java`** với nguyên văn chương trình:

```java
import java.lang.reflect.Method;

public class BridgeInspection {
    static class Processor<T> {
        public T transform(T value) {
            return value;
        }
    }

    static final class UppercaseProcessor extends Processor<String> {
        @Override
        public String transform(String value) {
            return value.toUpperCase();
        }
    }

    static class Provider<T> {
        public T produce() {
            return null;
        }
    }

    static final class StringProvider extends Provider<String> {
        @Override
        public String produce() {
            return "ready";
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Processor<String> processor = new UppercaseProcessor();
        System.out.println("generic call: " + processor.transform("java"));

        Method concrete = UppercaseProcessor.class
                .getDeclaredMethod("transform", String.class);
        Method bridge = UppercaseProcessor.class
                .getDeclaredMethod("transform", Object.class);

        System.out.println("concrete: "
                + concrete.getReturnType().getSimpleName()
                + ", bridge=" + concrete.isBridge()
                + ", synthetic=" + concrete.isSynthetic());
        System.out.println("bridge: "
                + bridge.getReturnType().getSimpleName()
                + ", bridge=" + bridge.isBridge()
                + ", synthetic=" + bridge.isSynthetic());

        Provider<String> provider = new StringProvider();
        System.out.println("provider: " + provider.produce());

        Processor raw = new UppercaseProcessor();
        try {
            raw.transform(100);
        } catch (ClassCastException ex) {
            System.out.println("raw call: "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Từ thư mục chứa tệp, chạy:

```bash
javac -Xlint:unchecked BridgeInspection.java
java BridgeInspection
```

**Stdout đã kiểm chứng:**

```text
generic call: JAVA
concrete: String, bridge=false, synthetic=false
bridge: Object, bridge=true, synthetic=true
provider: ready
raw call: ClassCastException
```

`javac -Xlint:unchecked` còn phát cảnh báo cho lời gọi `raw.transform(100)` trên raw type `Processor` (cảnh báo là đầu ra của compiler, không nằm trong stdout của ứng dụng). Cách gọi raw được giữ **cố ý** để quan sát rủi ro khi bỏ qua kiểm tra kiểu. Trong mã ứng dụng thông thường, hãy dùng `Processor<String>` thay vì raw `Processor`.

Tại sao có ba dòng cần chú ý?

- `generic call: JAVA`: khi gọi qua tham chiếu kiểu cha `Processor<String>`, chương trình vẫn thực thi phần thân ghi đè của `UppercaseProcessor`.
- Reflection nhìn thấy **hai** phương thức trong lớp con: phương thức người lập trình khai báo có tham số `String`, và phương thức cầu nối do compiler sinh có tham số `Object`.
- `raw call: ClassCastException`: raw type cho phép qua bước kiểm tra source với cảnh báo, nhưng bridge phải ép đối số `Integer` sang `String`; phép ép đó thất bại.

## 3. Lệnh `javap` cần dùng và cách đọc

Sau khi `javac` thành công, chương trình trên tạo các tệp class cho lớp ngoài cùng và những lớp lồng, ví dụ `BridgeInspection$UppercaseProcessor.class`.

Dùng ba mức độ quan sát, mỗi lệnh phục vụ mục tiêu khác nhau:

```bash
# 1. Xem descriptor của các method, kể cả private.
javap -p -s 'BridgeInspection$UppercaseProcessor'

# 2. Xem mã lệnh trong phần thân từng method.
javap -p -s -c 'BridgeInspection$UppercaseProcessor'

# 3. Xem flags, thuộc tính Signature và chi tiết class file.
javap -p -v 'BridgeInspection$UppercaseProcessor'
```

Dấu nháy đơn quanh tên lớp lồng đặc biệt hữu ích trên Bash và PowerShell vì ký tự `$` có ý nghĩa với shell. Trên Windows PowerShell, những lệnh trên cũng có thể dùng dấu nháy đơn như ví dụ. Nếu đã lưu các tệp vào một thư mục khác, cung cấp `-classpath <thư-mục-chứa-class>`, hoặc chạy ngay từ thư mục ấy.

Ý nghĩa các tùy chọn:

| Tùy chọn | Dữ liệu chính | Dùng khi |
| --- | --- | --- |
| `-p` | Hiển thị mọi thành viên, kể cả private | Không bỏ sót phương thức được sinh thêm |
| `-s` | Hiển thị JVM descriptor | Xác định erasure của kiểu tham số và kết quả |
| `-c` | Disassemble mã lệnh JVM của phương thức | Kiểm tra `checkcast`, `invokevirtual`, `areturn` |
| `-v` | Chi tiết class file, access flags, constant pool, thuộc tính | Xác định chính xác `ACC_BRIDGE`, `ACC_SYNTHETIC`, `Signature` |

Chỉ chạy `javap BridgeInspection` trên lớp ngoài cùng có thể khiến bạn không thấy bridge bên trong lớp lồng. Hãy trỏ đúng **class chứa phương thức ghi đè**: `BridgeInspection$UppercaseProcessor`.

## 4. Đối chiếu trực tiếp `UppercaseProcessor`: hai descriptor

Đoạn liên quan từ kết quả thực tế của:

```bash
javap -p -s -c 'BridgeInspection$UppercaseProcessor'
```

```text
public java.lang.String transform(java.lang.String);
  descriptor: (Ljava/lang/String;)Ljava/lang/String;
  Code:
     0: aload_1
     1: invokevirtual #7  // Method java/lang/String.toUpperCase:()Ljava/lang/String;
     4: areturn

public java.lang.Object transform(java.lang.Object);
  descriptor: (Ljava/lang/Object;)Ljava/lang/Object;
  Code:
     0: aload_0
     1: aload_1
     2: checkcast     #8  // class java/lang/String
     5: invokevirtual #13 // Method transform:(Ljava/lang/String;)Ljava/lang/String;
     8: areturn
```

Các số `#7`, `#8`, `#13` là index trong constant pool của lần biên dịch kiểm tra, không phải giá trị chuẩn bắt buộc ở mọi JDK. Nếu compiler thay đổi thứ tự constant pool, nội dung cần quan sát vẫn là các cặp **descriptor và bytecode**.

Tách thành hai phương thức:

**Phương thức thực do lập trình viên viết:**

```java
public String transform(String value) {
    return value.toUpperCase();
}
```

Nó có JVM descriptor `(Ljava/lang/String;)Ljava/lang/String;`.

**Phương thức cầu nối được compiler tạo (mã giả gần tương đương):**

```java
// PSEUDOCODE: minh họa compiler tạo bridge trong bytecode.
// Không tự viết thêm phương thức này vào UppercaseProcessor.
public Object transform(Object value) {
    return transform((String) value);
}
```

Cầu nối có JVM descriptor `(Ljava/lang/Object;)Ljava/lang/Object;`, chính là dạng erasure của phương thức trong `Processor<T>`. Ví dụ mã giả minh họa semantics của method sinh, không phải đoạn cần sao chép vào lớp con: tự viết `transform(Object)` cạnh override generic sẽ gây xung đột/name clash trong Java source.

### Đọc bytecode của cầu nối theo từng bước

1. `aload_0`: đưa `this` lên operand stack.
2. `aload_1`: đưa tham số `Object value` lên stack.
3. `checkcast java/lang/String`: kiểm tra đối tượng nhận được có thể ép sang `String`; với `Integer` sẽ ném `ClassCastException`.
4. `invokevirtual ... transform(String):String`: gọi method chuyên biệt trong lớp con (được người lập trình viết).
5. `areturn`: trả về tham chiếu kết quả. `String` cũng là `Object`, nên ở bước trả về không cần tạo một đối tượng khác.

Với `Processor<String> p = new UppercaseProcessor(); p.transform("java")`, bytecode lời gọi có thể tham chiếu descriptor erased của lớp cha. Cơ chế dispatch chọn triển khai tương ứng trên đối tượng lớp con thông qua bridge, rồi bridge chuyển tới `transform(String)`. Nhờ vậy, ngữ nghĩa ghi đè mà Java source hứa vẫn được thực thi tại runtime.

Đây là điểm quan trọng: **bridge bảo toàn đa hình (polymorphism)** khi descriptor của phương thức được dùng để gọi qua lớp cha không trùng trực tiếp với descriptor của phương thức chuyên biệt trong lớp con.

## 5. Xác nhận cờ `ACC_BRIDGE` và `ACC_SYNTHETIC`

Chạy:

```bash
javap -p -v 'BridgeInspection$UppercaseProcessor'
```

Trong đầu ra dài, tìm phương thức có descriptor `(Ljava/lang/Object;)Ljava/lang/Object;`. Trích đoạn đã kiểm tra:

```text
public java.lang.String transform(java.lang.String);
  descriptor: (Ljava/lang/String;)Ljava/lang/String;
  flags: (0x0001) ACC_PUBLIC

public java.lang.Object transform(java.lang.Object);
  descriptor: (Ljava/lang/Object;)Ljava/lang/Object;
  flags: (0x1041) ACC_PUBLIC, ACC_BRIDGE, ACC_SYNTHETIC
```

Ý nghĩa:

- `ACC_PUBLIC`: bridge trong ví dụ vẫn công khai, tương thích với phương thức ghi đè công khai của lớp cha.
- `ACC_BRIDGE`: class file đánh dấu phương thức này là **bridge** do compiler tạo nhằm duy trì quan hệ ghi đè/kiểu.
- `ACC_SYNTHETIC`: thành viên được compiler sinh ra; không phải một phương thức thông thường do lập trình viên trực tiếp khai báo trong source.

Không suy ra rằng mọi phương thức `synthetic` đều là bridge: `synthetic` là khái niệm rộng hơn. Với method ở đây, cả hai cờ cùng xuất hiện.

Trình biên dịch có thể chọn những chi tiết bytecode khác khi sinh bridge; để kiểm tra một class file cụ thể, hãy tin descriptor và flags của chính class file đó, thay vì học thuộc bytecode của ví dụ.

## 6. Reflection kiểm chứng bytecode từ chương trình Java

Trong `main` của chương trình hoàn chỉnh, hai phương thức được truy xuất bằng:

```java
Method concrete = UppercaseProcessor.class
        .getDeclaredMethod("transform", String.class);
Method bridge = UppercaseProcessor.class
        .getDeclaredMethod("transform", Object.class);
```

Đây là đoạn trích từ `BridgeInspection.java`; các import `java.lang.reflect.Method`, class và method `main` đã có trong chương trình chạy ở mục 2.

Đầu ra xác nhận:

```text
concrete: String, bridge=false, synthetic=false
bridge: Object, bridge=true, synthetic=true
```

`Class.getDeclaredMethod(name, parameterTypes...)` tìm theo tên và kiểu tham số; cầu nối có tham số `Object` nên có thể được truy xuất riêng với phương thức thật có tham số `String`. `Method.isBridge()` và `Method.isSynthetic()` phản ánh vai trò của các method trong class file.

Khi xây công cụ reflection, serializer, mapper hoặc framework nhận diện API của người dùng, bạn cần biết cầu nối có thể hiện diện trong `getDeclaredMethods()`. Nếu mục tiêu là lấy phương thức do người dùng khai báo để xử lý metadata, phải xem xét `isBridge()`/`isSynthetic()` và quan hệ ghi đè cho phù hợp; không đếm máy móc tất cả method cùng tên thành API độc lập. Trong một số trường hợp framework vẫn **cần** resolve bridge về method thực để tìm annotation hoặc generic metadata đúng ngữ cảnh.

Tránh giả định thứ tự của mảng do `getDeclaredMethods()` trả về. Trong ví dụ, chúng ta dùng `getDeclaredMethod` với tham số cụ thể để kết quả không phụ thuộc thứ tự liệt kê.

## 7. Một biến thể quan trọng: tham số không đổi, kiểu trả về đổi

Bridge không chỉ liên quan đến tình huống `transform(T)`. Trong cùng tệp chạy được ở mục 2 còn có:

```java
static class Provider<T> {
    public T produce() {
        return null;
    }
}

static final class StringProvider extends Provider<String> {
    @Override
    public String produce() {
        return "ready";
    }
}
```

Từ Java source, `StringProvider.produce()` ghi đè phương thức ở `Provider<String>`. Phương thức cha sau erasure trả về `Object`; phương thức lớp con trả về `String`. Kiểu tham số đều rỗng.

Dùng:

```bash
javap -p -s -c 'BridgeInspection$StringProvider'
```

Hai phương thức quan trọng trong kết quả thực tế:

```text
public java.lang.String produce();
  descriptor: ()Ljava/lang/String;
  Code:
     0: ldc           #7  // String ready
     2: areturn

public java.lang.Object produce();
  descriptor: ()Ljava/lang/Object;
  Code:
     0: aload_0
     1: invokevirtual #9  // Method produce:()Ljava/lang/String;
     4: areturn
```

**Khác biệt so với cầu nối ở `transform(Object)`:** phương thức `produce()` không có đối số cần `checkcast`. Cầu nối đơn giản gọi bản trả về `String` rồi trả dưới dạng `Object`.

Cần nhớ rằng JVM method descriptor **có chứa kiểu trả về**. Việc compiler tạo cầu nối có cùng danh sách tham số nhưng descriptor kiểu trả về khác nhau là hợp lệ ở cấp class file. Trong Java source, bạn không được tự khai báo hai overload chỉ khác kiểu trả về:

```java
// Không thể tự khai báo trong cùng lớp Java source:
// String produce() { ... }
// Object produce() { ... }
```

Các bridge còn có thể phát sinh với ghi đè trả về kiểu hiệp biến (*covariant return*) ngay cả khi không có generic. Khi kiểm tra bằng `javap`, hãy kết luận từ quan hệ override thực tế và descriptor chứ đừng mặc định mọi bridge chỉ do `<T>`.

## 8. Upper bound ảnh hưởng descriptor cầu nối thế nào?

Với `Processor<T>`, giới hạn trên của `T` là `Object` vì không khai báo `extends`. Nếu thay bằng `<T extends Number>`, erasure của `T` sẽ là `Number`.

Một ví dụ độc lập khác, tệp **`BoundedBridge.java`**:

```java
public class BoundedBridge {
    static class Receiver<T extends Number> {
        public void accept(T value) {
            System.out.println("base: " + value);
        }
    }

    static final class IntegerReceiver extends Receiver<Integer> {
        @Override
        public void accept(Integer value) {
            System.out.println("integer: " + value);
        }
    }

    public static void main(String[] args) {
        Receiver<Integer> receiver = new IntegerReceiver();
        receiver.accept(7);
    }
}
```

Biên dịch/chạy/kiểm tra:

```bash
javac BoundedBridge.java
java BoundedBridge
javap -p -s 'BoundedBridge$IntegerReceiver'
```

Stdout đã kiểm chứng:

```text
integer: 7
```

Đoạn trích descriptor thực tế:

```text
public void accept(java.lang.Integer);
  descriptor: (Ljava/lang/Integer;)V

public void accept(java.lang.Number);
  descriptor: (Ljava/lang/Number;)V
```

Phương thức `accept(Number)` được tạo làm cầu nối của phương thức trong `Receiver<T extends Number>`; erasure dùng `Number`, **không phải** `Object`. Phương thức cầu nối chuyển đối số `Number` về `Integer` trước khi gọi phương thức thật. Vì thế câu khẳng định “mọi bridge generic đều có tham số `Object`” là sai.

Khi một type variable có nhiều giới hạn, erasure dựa trên giới hạn đầu tiên. Quy tắc xóa kiểu áp dụng vào **chữ ký JVM thực sự đang được bridge kết nối**; không nên tự suy ra descriptor chỉ từ kiểu giá trị truyền tại một điểm gọi.

## 9. Bridge và overload: đừng lẫn khái niệm

**Override** là quan hệ giữa phương thức lớp con với phương thức lớp cha/interface theo các quy tắc của ngôn ngữ, bao gồm trường hợp sau thay thế tham số kiểu generic. `@Override` giúp compiler xác nhận ý định đó.

**Overload** là nhiều phương thức có tên giống nhau nhưng khác danh sách tham số trong ngữ cảnh cho phép. Ví dụ `void print(String)` và `void print(Integer)` là overload thông thường. Compiler không tự biến mọi overload thành bridge.

**Bridge** là phương thức bổ sung trong bytecode do compiler tạo để giữ ngữ nghĩa override khi descriptor cụ thể khác descriptor được dùng qua kiểu cha. Bridge có thể nhìn giống một overload nếu chỉ xét `javap -p`, nhưng nó mang cờ `ACC_BRIDGE` và có vai trò ủy quyền.

Ví dụ lỗi sai phổ biến:

```java
class Parent<T> {
    public void handle(T value) {}
}

class Child extends Parent<String> {
    // Sai nếu mong đây là override của handle(String):
    // public void handle(Object value) {}
}
```

Trường hợp này không phải giải pháp tự viết bridge; giữa `handle(Object)` ở lớp con và phương thức cha sau thay kiểu có xung đột erasure/name clash, và không thể dùng nó thay cho `handle(String)` một cách hợp lệ.

Cách viết đúng:

```java
class Child extends Parent<String> {
    @Override
    public void handle(String value) {
        System.out.println(value);
    }
}
```

Hai đoạn này minh họa độc lập với các chương trình hoàn chỉnh phía trên; đặt mỗi cặp lớp trong một tệp/ngữ cảnh riêng khi thử nghiệm.

**Giữ `@Override`** để phát hiện sớm tên sai, kiểu tham số sai hoặc việc vô tình tạo overload khác với phương thức định ghi đè. Việc sinh bridge là trách nhiệm compiler; không thêm bridge thủ công vào Java source.

## 10. Phân biệt `descriptor`, `Signature` và mã lệnh

Khi dùng `javap -v`, ba loại bằng chứng trả lời ba câu hỏi khác nhau:

| Thứ kiểm tra | Câu hỏi | Quan sát trong ví dụ |
| --- | --- | --- |
| **Descriptor** | JVM nhận/trả kiểu gì ở mức binary? | `transform(Object):Object` hoặc `transform(String):String` |
| **Signature metadata** | Class/method có khai báo generic ra sao? | `Processor<T>`, `extends Processor<String>` có thể hiện trong metadata |
| **Code** | Khi chạy thì method làm gì? | Bridge ép `Object` thành `String` rồi gọi method thật |
| **Access flags** | Method có phải do compiler sinh làm cầu nối? | `ACC_BRIDGE, ACC_SYNTHETIC` |
| **Reflection** | Chương trình có thể nhận diện method cầu nối không? | `Method.isBridge() == true` |

Một dòng `Signature` nói lớp con mở rộng `Processor<String>` **không** làm biến mất descriptor `Processor.transform(Object):Object`. Metadata generic giúp mô tả khai báo cho công cụ, còn method bridge giúp thực thi đúng dispatch ở binary level.

Không đồng nhất `Signature` với “một phương thức được gọi tại runtime” và không đồng nhất `checkcast` với việc sinh một instance mới. `checkcast` chỉ kiểm tra khả năng ép một tham chiếu sang kiểu mong muốn.

## 11. Lỗi và bẫy thường gặp khi đọc `javap`

**Chỉ nhìn source, bỏ qua bridge.** Nếu lớp con ghi đè thành công nhưng descriptor của lớp cha và lớp con khác nhau sau erasure, hãy kiểm tra class file lớp con trước khi kết luận JVM không dispatch đúng.

**Nhầm `synthetic` và `bridge`.** Một method `synthetic` không nhất thiết là bridge. Dùng `ACC_BRIDGE` hoặc `Method.isBridge()` để nhận diện chính xác hơn.

**Dùng sai tên lớp lồng.** Với `static class UppercaseProcessor` trong `BridgeInspection`, class file có tên binary `BridgeInspection$UppercaseProcessor`. Đừng chỉ chạy `javap BridgeInspection` rồi tìm method lớp con không có trong lớp ngoài.

**Lấy số constant-pool làm kết luận.** Các mã như `#8` và offset `2` là đặc thù của bản biên dịch. Khi giải thích, ưu tiên tên method, descriptor, `checkcast` và opcode `invokevirtual`.

**Nghĩ bridge làm mọi raw call an toàn.** Ví dụ `raw.transform(100)` chứng minh điều ngược lại: bridge ném `ClassCastException` nếu giá trị không phù hợp với kiểu chuyên biệt.

**Cho rằng mỗi method tên trùng là overload do tác giả viết.** Một số method có thể là bridge ẩn trong source. Dùng `javap -v`, `isBridge()` và quan hệ giữa các lớp.

**Suy luận cứng rằng return type không liên quan descriptor.** JVM descriptor có kiểu trả về; ví dụ `StringProvider.produce()` có một bridge trả `Object` để phù hợp với dạng erased của phương thức cha.

**Xóa thủ công bridge khỏi bytecode.** Làm như vậy có thể phá vỡ dispatch/khả năng tương thích binary của lớp. Sửa mã nguồn đúng quan hệ ghi đè và để compiler sinh bridge cần thiết.

## 12. Quy trình đối chiếu nhanh trên dự án thực

Khi gặp một phương thức generic `@Override` khó hiểu:

1. **Nhìn source:** xác định lớp cha/interface, `T`, giới hạn của `T`, lớp con thay `T` bằng kiểu nào và chữ ký override cụ thể.
2. **Dự đoán erasure:** nếu `T` không giới hạn, dùng `Object`; nếu bị giới hạn như `T extends Number`, dùng `Number`. Phân biệt kiểu tham số và kiểu trả về.
3. **Biên dịch đúng Java version:** dùng `javac`; xem cảnh báo `-Xlint:unchecked` nếu dùng raw type.
4. **Xem `javap -p -s`:** tìm descriptor của phương thức được viết ở source và method bổ sung tương thích với dạng erased.
5. **Xem `javap -p -c`:** tìm `checkcast`, method đích và cách trả về.
6. **Xem `javap -p -v`:** xác minh bridge có `ACC_BRIDGE` và `ACC_SYNTHETIC`.
7. **Thử qua tham chiếu kiểu cha:** đảm bảo dispatch đi đến override và kiểm tra một tình huống raw type sai khi cần chứng minh rủi ro.
8. **Đối chiếu reflection:** nếu framework xử lý method descriptors, quan sát `isBridge()` và cân nhắc resolve method thật.

Thứ tự này đưa bạn từ **ý định trong Java source** đến **bằng chứng trong bytecode** rồi trở lại **hành vi runtime**, giúp phân biệt lỗi suy luận về generics với lỗi trong thuật toán.

## 13. Bài tập có đáp án

### Bài 1. Dự đoán bridge cho `transform`

Cho `Processor<T>` và `UppercaseProcessor extends Processor<String>`, hỏi trình biên dịch có tạo `transform(Object)` trong lớp con không? Descriptor và vai trò của nó là gì?

**Đáp án:** có. `(Ljava/lang/Object;)Ljava/lang/Object;` phù hợp với phương thức `Processor<T>` sau erasure. Nó nhận `Object`, ép sang `String`, gọi `transform(String)` và trả về kết quả như `Object`.

### Bài 2. Nhận diện lỗi lúc chạy

Biến `Processor raw = new UppercaseProcessor();` được dùng để gọi `raw.transform(100)`. Lỗi nào có thể xảy ra và ở đâu?

**Đáp án:** compiler cảnh báo unchecked raw call; khi chạy bridge thực hiện `checkcast String` trên `Integer` và ném `ClassCastException`. Lỗi xuất hiện trước khi thân `transform(String)` xử lý chuỗi.

### Bài 3. Bridge không có đối số

Với `Provider<T>.produce()` và `StringProvider.produce()`, có cần `checkcast` trong bridge không?

**Đáp án:** không cần vì bridge không nhận tham số; nó gọi `produce():String` rồi trả kết quả dưới dạng `Object`.

### Bài 4. Thay upper bound

Với `Receiver<T extends Number>` và `IntegerReceiver`, descriptor bridge nhận `Object` hay `Number`?

**Đáp án:** `(Ljava/lang/Number;)V`, vì erasure của `T` là `Number`. Method thật nhận `Integer`.

### Bài 5. Nhận diện compiler-generated method

Một lớp có hai `transform` trong `javap`: một có `ACC_PUBLIC`; phương thức kia có `ACC_PUBLIC, ACC_BRIDGE, ACC_SYNTHETIC`. Có thể kết luận cả hai đều được viết thủ công trong source không?

**Đáp án:** không. Method có `ACC_BRIDGE` là phương thức cầu nối. Trong ví dụ, compiler tự tạo thêm phương thức đó để duy trì override sau erasure.

### Bài 6. Phân tích `@Override`

Vì sao nên giữ `@Override` cho `String transform(String)` trong `UppercaseProcessor`, mặc dù bytecode có thêm `transform(Object)`?

**Đáp án:** `@Override` yêu cầu compiler xác nhận mối quan hệ ghi đè ở cấp Java source; compiler mới là thành phần quyết định cách sinh bridge. Lập trình viên viết và bảo trì phương thức chuyên biệt, không phải cầu nối do compiler sinh.

## Kết luận cần nhớ

`javap` giúp thấy sự khác biệt giữa **mã Java source**, **kiểu bị xóa trong descriptor** và **method cầu nối được thêm vào class file**. Khi một phương thức generic ở lớp cha được lớp con ghi đè với kiểu cụ thể, `javac` có thể sinh method mang cờ `ACC_BRIDGE, ACC_SYNTHETIC` để giữ đúng đa hình. Hãy đối chiếu `-s` để đọc descriptor, `-c` để theo luồng ép kiểu/ủy quyền và `-v` để xác nhận flags. Bridge giải thích vì sao lời gọi qua kiểu cha vẫn đến đúng override sau type erasure, đồng thời cho thấy tại sao raw type sai có thể gây `ClassCastException`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-overrides-and-bridges-005">Nhận diện phương thức bridge và synthetic qua reflection</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện phương thức `bridge` và `synthetic` qua Reflection trong Java Generics

## 1. Mục tiêu

Khi đọc các phương thức của một class bằng Java Reflection, đôi khi bạn thấy **nhiều phương thức hơn số phương thức đã viết trong source**. Chẳng hạn class chỉ khai báo `String map(String)`, nhưng Reflection còn liệt kê `Object map(Object)`. Điều này có thể xảy ra vì trình biên dịch tạo **bridge method** để giữ hành vi ghi đè sau khi generic bị xóa kiểu (*type erasure*).

Java cung cấp hai phép kiểm tra trực tiếp trong `java.lang.reflect.Method`:

- `method.isBridge()` — phương thức có cờ bridge của JVM hay không.
- `method.isSynthetic()` — phương thức có cờ synthetic của JVM hay không.

Bài này tập trung vào cách đọc hai cờ đó một cách chính xác, kết nối chúng với **chữ ký phương thức, quy tắc override và output của `javap`**, phân biệt một bridge với phương thức phụ trợ do compiler tạo và sử dụng kết quả Reflection trong công cụ quét API.

Sau bài học, bạn sẽ:

1. Giải thích được tại sao class con generic có thể xuất hiện thêm method trong Reflection.
2. Viết được chương trình liệt kê các phương thức declared kèm kiểu tham số, kiểu trả về, `isBridge()` và `isSynthetic()`.
3. Nhìn ra sự khác biệt giữa `bridge=true, synthetic=true`, `bridge=false, synthetic=true` và một phương thức thông thường.
4. Chọn được `getDeclaredMethods()` hay `getMethods()` khi kiểm tra một class.
5. Tránh đếm trùng phương thức public API khi xây dựng framework, bộ quét annotation, tài liệu API hoặc công cụ kiểm thử.

## 2. Vì sao generic override có thể cần bridge?

Xét một interface generic:

```java
interface Mapper<T> {
    T map(T value);
}
```

Lớp triển khai gắn `T` thành `String`:

```java
final class StringMapper implements Mapper<String> {
    @Override
    public String map(String value) {
        return value.strip();
    }
}
```

Tại **source**, phương thức `String map(String)` đáp ứng hợp đồng của `Mapper<String>`. Tuy nhiên, descriptor của `Mapper.map(T)` ở JVM, do `T` không có bound rõ ràng, có dạng:

```text
(Ljava/lang/Object;)Ljava/lang/Object;
```

Phương thức được người lập trình khai báo trong `StringMapper` có descriptor:

```text
(Ljava/lang/String;)Ljava/lang/String;
```

Nếu chỉ có `map(String)`, một lời gọi đã biên dịch nhằm tới phương thức trên interface có descriptor `map(Object):Object` sẽ không trực tiếp khớp với descriptor `map(String):String` ở class con.

Để bảo đảm tính đa hình, compiler có thể tạo phương thức **bridge** trong `StringMapper`. Xét về hành vi, bridge tương tự:

```java
// MINH HỌA BYTECODE DO COMPILER SINH, KHÔNG TỰ THÊM VÀO SOURCE:
public Object map(Object input) {
    return map((String) input);
}
```

Bridge nhận kiểu sau erasure, chuyển tiếp tới implementation thật và có thể chèn cast cần thiết.

Sơ đồ:

```text
Mapper<String> variable
        |
        | lời gọi map(...), descriptor gốc map(Object):Object
        v
StringMapper.map(Object):Object       [bridge + synthetic]
        |
        | ép Object thành String, gọi implementation
        v
StringMapper.map(String):String       [phương thức do lập trình viên viết]
```

Điều quan trọng: class file có thể chứa **hai** method cùng tên `map` vì danh sách tham số/kiểu trả về trong descriptor bytecode khác nhau. Người lập trình không nên cố khai báo thủ công một overload có erasure trùng như vậy.

### 2.1. Bridge bảo vệ hợp đồng generic như thế nào?

Với tham chiếu có kiểu đúng:

```java
Mapper<String> mapper = new StringMapper();
String text = mapper.map("  Java  ");
```

Java kiểm tra rằng đối số là `String` ngay lúc biên dịch; bridge cho phép lời gọi đi qua giao diện với descriptor đã xóa rồi dispatch về implementation ở class con.

Nếu cố dùng raw type:

```java
// Minh họa hành vi rủi ro; có thể sinh cảnh báo raw/unchecked:
Mapper raw = new StringMapper();
raw.map(123);
```

Bridge có thể ném `ClassCastException` khi ép `Integer` thành `String`. Vấn đề gốc là lời gọi đã phá hợp đồng `Mapper<String>` qua raw type. Bridge không phải một cơ chế chuyển đổi dữ liệu tùy ý.

## 3. `Method.isBridge()` và `Method.isSynthetic()` khác nhau

Đối tượng `Method` biểu diễn một phương thức mà Reflection nhìn thấy. Các phương thức kiểm tra:

```java
boolean bridge = method.isBridge();
boolean synthetic = method.isSynthetic();
```

có ý nghĩa độc lập:

| Trạng thái | Diễn giải thực tế |
| --- | --- |
| `bridge=false`, `synthetic=false` | Phương thức thông thường không mang hai cờ đó. |
| `bridge=true`, `synthetic=true` | Trường hợp bridge do `javac` thường sinh khi cần giữ override/đa hình sau erasure hoặc với covariant return. |
| `bridge=false`, `synthetic=true` | Phương thức do compiler tạo vì mục đích khác, chẳng hạn helper triển khai lambda ở một cách biên dịch cụ thể. |
| `bridge=true`, `synthetic=false` | Hai cờ trên JVM là khái niệm riêng; đừng tự định nghĩa logic của công cụ với giả định `isBridge()` và `isSynthetic()` luôn đồng nhất trong mọi class file và mọi công cụ sinh bytecode. |

`isBridge()` hỏi phương thức có cờ `ACC_BRIDGE` theo class file hay không. `isSynthetic()` hỏi phương thức có cờ `ACC_SYNTHETIC` theo class file hay không. Thông thường bridge do Java compiler sinh là synthetic, nhưng **synthetic không đồng nghĩa bridge**.

`synthetic` ở đây là thuật ngữ của JVM cho thành phần không xuất hiện như một khai báo thông thường trong source. Nó **không phải** thuộc tính generic, không liên quan trực tiếp đến từ khóa `synchronized`, và cũng không có nghĩa rằng phương thức không thể được Reflection tìm thấy hoặc gọi.

### 3.1. Các API Reflection liên quan

| API | Để làm gì |
| --- | --- |
| `Method.isBridge()` | Kiểm tra bridge flag. |
| `Method.isSynthetic()` | Kiểm tra synthetic flag của method. |
| `Method.getName()` | Tên phương thức. |
| `Method.getParameterTypes()` | Kiểu tham số raw/erased ở runtime. |
| `Method.getReturnType()` | Kiểu trả về runtime của method. |
| `Method.getGenericParameterTypes()` | Đọc thông tin tham số generic từ khai báo/metadata nếu có. |
| `Method.getGenericReturnType()` | Đọc khai báo kiểu trả về generic nếu có. |
| `Method.getDeclaringClass()` | Class/interface thực sự khai báo method này trong class file. |
| `Class.getDeclaredMethods()` | Đọc các phương thức được khai báo trong class/interface đó, bao gồm non-public và compiler-generated methods; không gồm phương thức chỉ được kế thừa. |
| `Class.getMethods()` | Đọc các phương thức `public` có thể nhìn thấy của class/interface, bao gồm các phương thức public được kế thừa. |
| `java.lang.reflect.Modifier` | Kiểm tra các modifier thông thường như `public`, `private`, `static`. |

**Đừng tìm `isBridge()` ở đối tượng `Class<?>` hoặc ở `Field`.** Đây là API của `Method`. `isSynthetic()` còn xuất hiện ở một số đối tượng Reflection khác nhưng ý nghĩa phụ thuộc thành phần đang được kiểm tra.

## 4. Ví dụ Java 17 hoàn chỉnh: bridge, covariant return và lambda synthetic

Tạo tệp **`BridgeReflectionDemo.java`**:

```java
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

public class BridgeReflectionDemo {
    interface Mapper<T> {
        T map(T input);
    }

    static final class StringMapper implements Mapper<String> {
        @Override
        public String map(String input) {
            return input.strip();
        }
    }

    static class Base {
        Number score() {
            return 1;
        }
    }

    static final class Derived extends Base {
        @Override
        Integer score() {
            return 2;
        }
    }

    static final class LambdaHolder {
        Runnable callback() {
            return () -> { };
        }
    }

    static void inspect(Class<?> type) {
        System.out.println("[" + type.getSimpleName() + "]");
        Arrays.stream(type.getDeclaredMethods())
                .sorted(Comparator.comparing(Method::getName)
                        .thenComparing(m -> Arrays.toString(m.getParameterTypes())))
                .forEach(m -> System.out.println(format(m)));
    }

    static String format(Method m) {
        String params = Arrays.stream(m.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
        return m.getName() + "(" + params + ")->"
                + m.getReturnType().getSimpleName()
                + " | bridge=" + m.isBridge()
                + " synthetic=" + m.isSynthetic();
    }

    public static void main(String[] args) throws Exception {
        Mapper<String> mapper = new StringMapper();
        System.out.println("result=" + mapper.map("  Java  "));
        System.out.println("score=" + new Derived().score());
        inspect(StringMapper.class);
        inspect(Derived.class);
        inspect(LambdaHolder.class);
        Method bridge = StringMapper.class.getDeclaredMethod(
                "map", Object.class);
        System.out.println("erasedParam="
                + bridge.getParameterTypes()[0].getSimpleName());
    }
}
```

Biên dịch bằng Java 17 hoặc JDK mới hơn có hỗ trợ `--release 17`:

```bash
javac --release 17 BridgeReflectionDemo.java
java BridgeReflectionDemo
```

Kết quả đã kiểm tra bằng `javac --release 17` và `java`:

```text
result=Java
score=2
[StringMapper]
map(Object)->Object | bridge=true synthetic=true
map(String)->String | bridge=false synthetic=false
[Derived]
score()->Integer | bridge=false synthetic=false
score()->Number | bridge=true synthetic=true
[LambdaHolder]
callback()->Runnable | bridge=false synthetic=false
lambda$callback$0()->void | bridge=false synthetic=true
erasedParam=Object
```

Các dòng trên là output của cách biên dịch bằng **`javac`** đã kiểm tra. Tên chính xác như `lambda$callback$0`, việc có/không có helper lambda và thứ tự những phương thức do compiler sinh có thể khác ở compiler/JDK/chiến lược chuyển mã khác; đừng viết business logic phụ thuộc trực tiếp vào tên helper lambda đó. Trong ví dụ, `sorted(...)` được đưa vào để kết quả liệt kê deterministic với bộ method được compiler này sinh ra.

### 4.1. Đọc nhóm `StringMapper`

```text
map(Object)->Object | bridge=true synthetic=true
map(String)->String | bridge=false synthetic=false
```

`map(String)` chính là phương thức tác giả viết. `map(Object)` được sinh để phù hợp với erasure của `Mapper<T>.map(T)`; vì vậy nó mang bridge flag và synthetic flag.

Hai phương thức có cùng tên nhưng **tham số runtime khác nhau**, do đó:

```java
StringMapper.class.getDeclaredMethod("map", String.class);
StringMapper.class.getDeclaredMethod("map", Object.class);
```

đều có thể tìm được method ở class file sau khi Java compiler đã phát sinh bridge. Kết quả thứ hai biểu diễn bridge; cách dùng Reflection đó không khiến source `StringMapper` thực sự công bố một overload `map(Object)` mới cho lập trình viên viết Java thông thường.

### 4.2. Đọc nhóm `Derived`: bridge không chỉ gắn với generic

```text
score()->Integer | bridge=false synthetic=false
score()->Number | bridge=true synthetic=true
```

Ở đây `Base.score()` trả `Number`, còn `Derived.score()` ghi đè hợp lệ với kiểu trả về hẹp hơn `Integer` (*covariant return*). Vì descriptor JVM gồm cả return type, `javac` có thể tạo bridge `Number score()` để giữ dispatch tương thích với những lời gọi đã biên dịch theo kiểu trả về `Number`.

Điều này cho thấy: **thấy `isBridge() == true` không đủ để kết luận phương thức cha có type parameter generic.** Bridge cũng có thể xuất hiện từ covariant return của phương thức không generic.

Nếu chỉ muốn biết lớp con có phương thức do compiler sinh để bảo toàn override hay không, hãy xem cả quan hệ kế thừa và chữ ký raw. Nếu muốn biết trường hợp nào là generic erasure, cần đọc khai báo generic của supertype.

### 4.3. Đọc nhóm `LambdaHolder`: synthetic chưa chắc bridge

```text
callback()->Runnable | bridge=false synthetic=false
lambda$callback$0()->void | bridge=false synthetic=true
```

Trong bytecode do `javac` sử dụng cho lambda ở ví dụ, compiler tạo một helper `lambda$callback$0()` và đánh dấu là synthetic. Helper này **không thực hiện nhiệm vụ tương thích chữ ký override**, nên `isBridge() == false`.

Một lambda ở source được triển khai bằng cơ chế liên quan `invokedynamic` và runtime của Java. Hãy phân biệt helper method xuất hiện trong class khai báo lambda với đối tượng thực thi `Runnable` được tạo khi chạy. Reflection trên `LambdaHolder.class` đang soi các phương thức được khai báo trong class ấy, không phải liệt kê mọi phương thức có thể có trong implementation object của lambda.

## 5. Xác nhận bằng `javap`: nhìn trực tiếp descriptor và cờ bytecode

Sau khi biên dịch ví dụ, dùng `javap` để đọc class file của các lớp lồng:

```bash
javap -p -s 'BridgeReflectionDemo$StringMapper'
javap -p -s 'BridgeReflectionDemo$Derived'
javap -p -s 'BridgeReflectionDemo$LambdaHolder'
```

Những đoạn descriptor chính:

```text
public java.lang.String map(java.lang.String);
  descriptor: (Ljava/lang/String;)Ljava/lang/String;

public java.lang.Object map(java.lang.Object);
  descriptor: (Ljava/lang/Object;)Ljava/lang/Object;
```

```text
java.lang.Integer score();
  descriptor: ()Ljava/lang/Integer;

java.lang.Number score();
  descriptor: ()Ljava/lang/Number;
```

```text
java.lang.Runnable callback();
  descriptor: ()Ljava/lang/Runnable;

private static void lambda$callback$0();
  descriptor: ()V
```

Chuyển sang chế độ chi tiết:

```bash
javap -p -v 'BridgeReflectionDemo$StringMapper'
javap -p -v 'BridgeReflectionDemo$Derived'
javap -p -v 'BridgeReflectionDemo$LambdaHolder'
```

Tìm dòng `flags` của các phương thức. Ví dụ kiểu hiển thị:

```text
flags: (...) ACC_PUBLIC, ACC_BRIDGE, ACC_SYNTHETIC
```

Với helper lambda:

```text
flags: (...) ACC_PRIVATE, ACC_STATIC, ACC_SYNTHETIC
```

Các cờ này giải thích trực tiếp giá trị mà `isBridge()` và `isSynthetic()` phản ánh. Dùng `javap -c -p` nếu cần xem thân của bridge: nó thường đọc tham số `Object`, chuyển kiểu và gọi implementation `String`. Chi tiết opcode có thể thay đổi tùy compiler; hãy theo **hợp đồng chuyển tiếp** thay vì cố ghi nhớ từng opcode.

Trong PowerShell và các shell Unix, dấu nháy đơn quanh tên lớp lồng giúp `$StringMapper` không bị xử lý như tên biến shell.

## 6. `getDeclaredMethods()` hay `getMethods()`?

Đây là một lựa chọn quan trọng khi viết công cụ dùng Reflection.

### 6.1. `getDeclaredMethods()`: tập method khai báo trực tiếp

```java
Method[] methods = StringMapper.class.getDeclaredMethods();
```

Kết quả gồm phương thức mà class file của `StringMapper` trực tiếp khai báo: `map(String)` và bridge `map(Object)`. Nó cũng có thể bao gồm các phương thức `private`, `protected`, `package-private` hay synthetic nếu chúng được khai báo tại đó.

Nó **không bao gồm** các phương thức chỉ được thừa kế từ superclass hoặc interface mà class chưa khai báo trực tiếp. Nó không trả constructors; dùng `getDeclaredConstructors()` để đọc constructor.

Dùng khi muốn:

- Kiểm tra chính xác compiler đã đặt những method nào vào **class này**.
- Tìm bridge hoặc helper synthetic của class.
- Debug sự thay đổi bytecode của một lớp cụ thể.
- Quét annotation chỉ do class hiện tại khai báo, nếu chính sách scanner là như vậy.

### 6.2. `getMethods()`: tập phương thức public có thể nhìn thấy

```java
Method[] methods = StringMapper.class.getMethods();
```

Kết quả gồm public methods được khai báo và public methods được thừa kế theo quy tắc Reflection. Nó có thể có bridge public và các phương thức từ `Object`, interface, hoặc superclass.

Dùng khi cần thấy **public surface** có thể gọi được trên class. Nhưng nếu mục tiêu là “đếm số method lập trình viên đã viết”, `getMethods().length` sẽ cho kết quả sai vì inherited methods và compiler-generated methods.

Lưu ý: `getDeclaredMethods()` và `getMethods()` **không bảo đảm thứ tự**. Hãy sắp xếp bằng tiêu chí cụ thể trước khi hiển thị, so sánh snapshot hoặc kiểm thử.

### 6.3. Vì sao `getDeclaredMethod` có thể gây hiểu nhầm?

```java
Method real = StringMapper.class.getDeclaredMethod(
        "map", String.class);

Method bridge = StringMapper.class.getDeclaredMethod(
        "map", Object.class);
```

Tham số truyền vào `getDeclaredMethod` là **runtime parameter types**, không phải type arguments ở source. Sau erasure, bridge nhận `Object`, nên kết quả thứ hai là một phương thức có thật trong class file.

Nếu có hai phương thức JVM cùng tên và cùng danh sách parameter types nhưng khác return type (có thể do covariant return và bridge), Reflection cung cấp quy tắc chọn method khi dùng `getDeclaredMethod`; đừng dùng một phép tra cứu theo tên/tham số để **điều tra đầy đủ tất cả method**. Hãy dùng `getDeclaredMethods()` và kiểm tra return type, `isBridge`, `isSynthetic` của từng phần tử.

## 7. Chữ ký generic và raw descriptor: không nên gộp làm một

`Method.getReturnType()` và `Method.getGenericReturnType()` trả thông tin liên quan nhưng không trùng nhau:

```java
Method m = Mapper.class.getDeclaredMethod("map", Object.class);

System.out.println(m.getReturnType());        // class java.lang.Object
System.out.println(m.getGenericReturnType()); // T
```

`Mapper<T>.map(T)` dùng một type variable `T` trong source. Sau erasure, kiểu trả về runtime là `Object`, nhưng metadata generic của method khai báo trong interface có thể cho Reflection biết đây là `T`.

Đối với bridge `StringMapper.map(Object)`, bạn thường thấy thông tin raw/erased (như `Object`) bởi phương thức bridge được compiler sinh ra để nối descriptor, trong khi implementation `map(String)` có kiểu trả về `String`.

Để xem quan hệ giữa lớp con và generic interface, kiểm tra:

```java
StringMapper.class.getGenericInterfaces();
```

Đầu ra có thể chứa một `ParameterizedType` biểu diễn `Mapper<String>`. Đây là **metadata của quan hệ supertype**; không có nghĩa mọi instance `Mapper<String>` mang một tham số type runtime có thể kiểm tra bằng `instanceof Mapper<String>`.

### 7.1. Tham số có bound khác `Object`

Nếu interface được khai báo:

```java
interface NumberMapper<T extends Number> {
    T map(T value);
}
```

thì erasure của type variable `T` là `Number`, nên descriptor tương ứng dùng `Number`, không phải `Object`. Khi thực hiện Reflection, hãy tìm method bridge với tham số raw phù hợp (`Number.class`) và đừng mặc định mọi bridge generic đều nhận `Object`.

Ví dụ chữ ký:

```text
map(Number):Number
```

có thể là đường chuyển tiếp đến implementation cụ thể có tham số `Integer`. `Object` xuất hiện ở ví dụ chính vì type variable của interface **không có bound tường minh**.

### 7.2. Synthetic và bridge không phải là “type erasure itself”

Erasure là quy tắc dịch kiểu generic sang dạng tương thích bytecode. Một method generic có thể bị erasure mà **không sinh bridge**:

```java
static <T> T identity(T value) {
    return value;
}
```

Nếu không có quan hệ override cần nối hai descriptor khác nhau, method `identity` không cần bridge. Ngược lại, phương thức không generic với covariant return có thể cần bridge. Vì vậy:

- Có generic **không suy ra** `isBridge() == true`.
- Có bridge **không suy ra** phương thức cha generic.
- Có synthetic **không suy ra** có bridge.
- Không có bridge **không suy ra** không hề có type erasure.

## 8. Cách lọc method khi xây công cụ quét API

Một framework có thể quét method để tạo tuyến REST, ghi thông tin audit, xây metadata hoặc đăng ký handler. Nếu không phân biệt bridge, một phương thức source có thể được xử lý hai lần khi class file chứa cả implementation và bridge.

### 8.1. Lọc các phương thức compiler-generated trong một lượt quét

Một chính sách hợp lý **khi chỉ muốn những phương thức người lập trình công bố trực tiếp trên class**:

```java
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

static List<Method> sourceLikeDeclaredMethods(Class<?> type) {
    return Arrays.stream(type.getDeclaredMethods())
            .filter(m -> !m.isBridge())
            .filter(m -> !m.isSynthetic())
            .toList();
}
```

Đoạn trên là **một utility method minh họa**, cần đặt trong class và có import phù hợp khi dùng. Nó giữ các method không có hai cờ, nhưng không đảm bảo tìm được “toàn bộ API ở source” trong mọi ngữ cảnh:

- Phương thức **thừa kế** không nằm trong `getDeclaredMethods()`.
- Interface default methods, annotation, override và generic hierarchy vẫn cần chính sách riêng.
- Những cấu trúc class file do các compiler hoặc bytecode generator khác sinh có thể có cờ/metadata khác nhau.
- Không phải mọi method không synthetic đều công khai ra bên ngoài; vẫn phải kiểm tra `Modifier.isPublic(...)` hoặc các access rules khác.

Nếu bạn chỉ muốn loại method bridge nhưng **vẫn cần quan sát synthetic helper**, chỉ dùng:

```java
.filter(m -> !m.isBridge())
```

Không có một bộ lọc duy nhất đúng cho mọi nhiệm vụ. Chính sách “chỉ source-like methods” hợp lý cho tài liệu nội bộ đơn giản, nhưng sẽ bỏ qua dữ liệu hữu ích khi debug bytecode.

### 8.2. Tránh đếm trùng nhưng vẫn bảo toàn thông tin annotation

Giả sử một scanner tạo một mô tả API cho mỗi phương thức triển khai. Bridge có thể xuất hiện cạnh phương thức cùng nhiệm vụ nghiệp vụ. Một cách tiếp cận:

1. Quét `getDeclaredMethods()` hoặc `getMethods()` tùy phạm vi scanner.
2. Xác định trước liệu scanner cần **implementation method**, **interface contract**, hay cả hai.
3. Nhận diện bridge bằng `isBridge()`, không suy luận chỉ từ tên hay kiểu trả về.
4. Khi bridge đại diện cho một implementation, cố tìm method đích qua **tên, quan hệ kiểu tham số, quan hệ kiểu trả về và hierarchy**; đừng ghép bừa method đầu tiên cùng tên.
5. Với annotation, cần xác định nơi annotation được khai báo và quy tắc framework có hợp nhất metadata từ interface, superclass, method implementation hay bridge không.
6. Dùng test với generic interface, covariant return, overload, inherited method và raw type để kiểm chứng scanner thực sự không bỏ sót hoặc đăng ký trùng.

Đừng mặc định rằng annotation trên method source luôn hoặc không bao giờ được compiler chép sang bridge trong mọi công cụ/JDK. Nếu thông tin annotation có tác dụng pháp lý/nghiệp vụ, hãy kiểm tra bytecode thực và chính sách của framework. Không nên xóa bridge khỏi kết quả **trước khi** phân giải quan hệ override nếu scanner cần nó để hiểu một method gốc.

### 8.3. Đừng nhận diện bridge qua tên method

Trong ví dụ, bridge vẫn có tên `map`, giống implementation. Trong ví dụ covariant return, bridge có tên `score`, giống method thật. Nó **không có một tiền tố tiêu chuẩn** như `bridge$`.

Ngược lại, tên `lambda$callback$0` ở ví dụ là một chi tiết sinh mã của `javac`; tên khác hoặc cách triển khai khác hoàn toàn có thể xảy ra.

Tiêu chí chuẩn để xác định bridge ở Reflection là `Method.isBridge()`. Dùng `isSynthetic()` để nhận biết synthetic method nói chung, không suy đoán từ tiền tố tên.

## 9. Các tình huống kiểm tra điển hình

### Tình huống A: Reflection thấy `map(Object)` dù source chỉ có `map(String)`

Kiểm tra:

```java
for (Method m : StringMapper.class.getDeclaredMethods()) {
    System.out.println(m.getName() + ": " + m.isBridge());
}
```

Nếu `map(Object)` là bridge, bạn sẽ nhìn thấy `true`. Dùng `javap -v` để kiểm tra `ACC_BRIDGE` và `ACC_SYNTHETIC`. Đừng sửa class source để “xóa” một phương thức compiler đã tạo: nó cần thiết cho dispatch đúng.

### Tình huống B: Quét handler khiến cùng một chức năng xuất hiện hai lần

Nếu hai `Method` có cùng tên nhưng một có `isBridge() == true`, cần xem chúng có cùng implementation nghiệp vụ không. Với scanner hướng tới source-level API, lọc hoặc gộp bridge theo đúng quy tắc resolution. Kiểm thử với annotation ở interface và implementation trước khi coi filtering là đủ.

### Tình huống C: Synthetic method xuất hiện dù class không dùng generics

Ví dụ method helper của lambda. Một method có `isSynthetic() == true`, `isBridge() == false` không chứng minh class có generic. Dùng `javap` và khai báo source để hiểu compiler tạo method đó vì lý do gì.

### Tình huống D: Override covariant return dẫn tới hai kết quả cùng tên, cùng tham số

Với `Number score()` ở cha và `Integer score()` ở con, JVM có thể có bridge trả `Number` và implementation trả `Integer`. Sử dụng **toàn bộ `getDeclaredMethods()`** để nghiên cứu cặp method; tra cứu một method theo tên và danh sách tham số chưa chắc giúp phân tích đầy đủ khác biệt return type.

### Tình huống E: Kết quả quét không ổn định giữa các lần chạy

Theo đặc tả Reflection, thứ tự phần tử trong mảng trả về từ `getDeclaredMethods()` không được bảo đảm. Hãy sort theo tiêu chí ổn định như tên, danh sách tham số và kiểu trả về khi xuất báo cáo. Không dựa vào vị trí `[0]` để quyết định phương thức nào “thật” hay “bridge”.

### Tình huống F: Thư viện bytecode hoặc compiler khác javac

Đừng giả định class file nào cũng giống bytecode do `javac --release 17` tạo. Một số class file được sinh bởi công cụ runtime, weaving hoặc compiler ngôn ngữ JVM khác. `isBridge()`/`isSynthetic()` phản ánh cờ thực trong method hiện tại; ý nghĩa nghiệp vụ hoặc nguồn sinh method cần xác minh theo toolchain cụ thể.

## 10. Quy trình debug thực tế

Khi công cụ Reflection báo có phương thức lạ:

1. **Chốt class cần kiểm tra** và gọi `getDeclaredMethods()` để xem chính phương thức nằm trong class file đó.
2. **In tối thiểu**: tên, parameter types, return type, `isBridge()`, `isSynthetic()`, `getDeclaringClass()`.
3. **Sort kết quả**, bởi Reflection không đảm bảo thứ tự.
4. Nếu là bridge, **tìm supertype generic hoặc method covariant return** và so sánh descriptor trước/sau erasure.
5. Nếu chỉ synthetic, **kiểm tra lambda/helper hoặc cơ chế sinh mã khác**; không tự coi đó là bridge.
6. Dùng `javap -p -s` để xem descriptor và `javap -p -v` để kiểm tra flags. Khi cần xác minh hướng gọi, dùng `javap -p -c`.
7. **Quyết định theo mục đích scanner**: giữ method để debug bytecode, hoặc lọc/gộp nó khi hiển thị logical API.
8. **Thử lại** với interface generic, class kế thừa generic, covariant return, overload và code có lambda để kiểm tra logic phân loại.

Một bảng tra cứu nhanh:

| Kết quả quan sát | Ý nghĩa nên điều tra |
| --- | --- |
| `bridge=true, synthetic=true`, tham số `Object` | Bridge do erasure kiểu generic không bound. |
| `bridge=true, synthetic=true`, khác return type | Có thể là bridge hỗ trợ covariant return. |
| `bridge=false, synthetic=true`, tên giống helper lambda | Compiler-generated helper, không làm nhiệm vụ bridge override. |
| `bridge=false, synthetic=false` | Method thường, nhưng vẫn cần kiểm tra access/public/inherited nếu mô tả API. |
| `getMethods()` cho nhiều method hơn `getDeclaredMethods()` | Có thể vì tập public inherited, không nhất thiết đều là bridge. |
| Compiler báo `name clash` | Chữ ký source và erasure xung đột; không giải quyết bằng cách thêm phương thức bridge thủ công. |

## 11. Các lỗi tư duy cần tránh

**Sai lầm 1 — `isSynthetic()` nghĩa là `isBridge()`.** Thực tế synthetic bao gồm nhiều loại method do compiler sinh. Ví dụ helper lambda ở chương trình chạy được là synthetic nhưng không bridge.

**Sai lầm 2 — mọi generic method đều có bridge.** Một phương thức generic độc lập không tham gia override thường không cần bridge. Bridge được sinh khi cần giữ tương thích lời gọi/override sau biên dịch.

**Sai lầm 3 — bridge là overload source mà người dùng phải gọi.** Bridge là chi tiết biên dịch để đảm bảo dispatch. Framework và reflection có thể thấy nó; lời gọi Java source nên tuân thủ hợp đồng generic hoặc covariant return đã công bố.

**Sai lầm 4 — `getDeclaredMethods()` chỉ trả phương thức do programmer viết.** Nó còn có thể trả bridge và helper synthetic được khai báo trong class file.

**Sai lầm 5 — có thể lọc hết synthetic mà không đánh giá nhu cầu.** Đối với tài liệu source API, lọc synthetic có thể hợp lý. Nhưng trong công cụ debug, coverage hoặc instrumenting bytecode, bỏ hết synthetic có thể làm mất dữ liệu cần thiết.

**Sai lầm 6 — reflection method order là cố định.** Java không hứa thứ tự; mọi báo cáo và test so sánh nên tự sắp xếp hoặc so theo tập hợp.

**Sai lầm 7 — `getGenericReturnType()` luôn cho biết type argument runtime của đối tượng.** API này mô tả khai báo/metadata generic của method, không tự tái tạo specialization runtime của mọi instance `List<T>`.

**Sai lầm 8 — `isBridge()` cho biết chính xác method đích được gọi.** Cờ chỉ nhận diện bridge. Muốn biết bridge chuyển tiếp tới đâu, cần xem quan hệ kiểu hoặc inspect bytecode (`javap -c`) và tránh heuristic chỉ dựa vào tên.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Tại sao `StringMapper.class.getDeclaredMethods()` có thể chứa cả `map(String)` và `map(Object)` trong khi source chỉ định nghĩa `map(String)`?

<details>
<summary>Đáp án</summary>

`Mapper<T>.map(T)` erases thành `map(Object):Object`; implementation sau khi gắn `T = String` là `map(String):String`. Compiler sinh bridge `map(Object):Object` để giữ dispatch từ interface/supertype. Bridge được Reflection nhìn thấy với `isBridge() == true`.

</details>

**Câu 2.** `isSynthetic() == true` có chứng minh một method là bridge không?

<details>
<summary>Đáp án</summary>

Không. Synthetic method có thể là helper do compiler tạo để thực hiện lambda hoặc mục đích khác. Kiểm tra `isBridge()` riêng; nếu cần, đọc `ACC_BRIDGE`/`ACC_SYNTHETIC` bằng `javap -v`.

</details>

**Câu 3.** Khi quét một class để tìm method do class đó khai báo, nên bắt đầu bằng `getMethods()` hay `getDeclaredMethods()`?

<details>
<summary>Đáp án</summary>

`getDeclaredMethods()` vì nó truy xuất method khai báo tại class hiện tại, gồm cả non-public và synthetic. Nếu mục tiêu là toàn bộ public surface kể cả inherited methods thì cân nhắc `getMethods()`. Cả hai đều có thể trả compiler-generated methods; cần chính sách filtering phù hợp.

</details>

**Câu 4.** Vì sao `Derived.score()` cũng có thể sinh bridge dù không sử dụng generics?

<details>
<summary>Đáp án</summary>

`Base.score()` trả `Number`, `Derived.score()` override bằng `Integer` (covariant return). Descriptor JVM khác return type, nên compiler có thể tạo bridge `Number score()` để đáp ứng lời gọi theo descriptor của superclass.

</details>

**Câu 5.** Tại sao không nên nhận diện method synthetic bằng tên `lambda$...`?

<details>
<summary>Đáp án</summary>

Tên helper là chi tiết compiler, không phải định danh chuẩn của mọi synthetic method. Reflection có `isSynthetic()`, và `javap -v` cho thấy `ACC_SYNTHETIC`. Một synthetic method có thể không có tiền tố `lambda$` và một method bridge thường có cùng tên với implementation.

</details>

## 13. Tóm lược kỹ thuật

**`Method.isBridge()` kiểm tra bridge method; `Method.isSynthetic()` kiểm tra synthetic method.** Bridge thường được compiler tạo để điều hòa sự khác biệt descriptor sau generic erasure hoặc covariant return, trong khi synthetic là nhóm rộng hơn, bao gồm cả helper lambda do compiler sinh.

Khi cần phân tích chính xác, sử dụng **`getDeclaredMethods()` + kiểu tham số/return + `isBridge()` + `isSynthetic()`**, kết hợp `javap -s/-v` để xác minh descriptor và flags. Khi xây công cụ quét API, xác định rõ bạn muốn **method hiện diện trong bytecode** hay **method thể hiện hợp đồng source**, rồi lọc/gộp theo mục đích. Không phụ thuộc vào tên helper hoặc thứ tự phương thức mà Reflection trả về.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-overrides-and-bridges-006">Xung đột tên (name clash) do cùng chữ ký sau xóa kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Xung đột tên (name clash) do cùng chữ ký sau xóa kiểu trong Java Generics

Một lớp Java có thể khai báo các phương thức cùng tên nhưng nhận kiểu tham số khác nhau. Tuy nhiên, khi tham số có generic, **hai chữ ký trông khác nhau ở mã nguồn vẫn có thể xóa kiểu (type erasure) thành cùng dạng tham số ở bytecode**. Nếu các phương thức này không tạo thành quan hệ ghi đè hợp lệ, trình biên dịch báo **name clash** và từ chối chương trình.

Ví dụ điển hình:

```java
// KHÔNG BIÊN DỊCH: ví dụ cố ý gây name clash.
import java.util.List;

class DuplicateProcessor {
    void process(List<String> names) { }
    void process(List<Integer> ids) { }
}
```

Cả `List<String>` và `List<Integer>` đều có erasure là `List`. Với Java, việc đổi **đối số kiểu** của một kiểu tham số hóa không đủ để tạo hai overload có chữ ký khác nhau sau erasure.

Bài này giải thích cách tự tính erasure, vì sao xung đột xuất hiện khi overload hoặc kế thừa, khi nào ghi đè là hợp lệ, vai trò của bridge method và cách thiết kế API để tránh name clash.

## 1. Cần phân biệt ba khái niệm: overload, override và name clash

**Overloading (nạp chồng):** có nhiều phương thức cùng tên nhưng **danh sách kiểu tham số** khác nhau để trình biên dịch phân giải lời gọi. Ví dụ `send(String)` và `send(Integer)` là hai overload hợp lệ.

**Overriding (ghi đè):** phương thức của lớp con hoặc lớp triển khai thay thế hành vi của một phương thức được kế thừa theo các quy tắc chữ ký, khả năng truy cập, kiểu trả về và các điều kiện khác của Java. Ghi đè có thể liên quan generics và bridge method.

**Name clash:** hai phương thức dẫn đến chữ ký bị xung đột theo quy tắc ngôn ngữ, chẳng hạn có **cùng erasure nhưng không ghi đè nhau hợp lệ**. Trong trường hợp này, compiler không thể duy trì cả hai như hai overload hợp lệ.

Không phải cứ hai phương thức cùng erasure là sẽ có lỗi: **một override hợp lệ chính là trường hợp cần giữ cùng lời gọi động sau erasure**. Muốn chẩn đoán đúng cần xét cả erasure **và quan hệ kế thừa/ghi đè**.

### Ví dụ nhanh

| Khai báo | Kết quả | Lý do |
| --- | --- | --- |
| `f(List<String>)` cùng `f(List<Integer>)` trong một lớp | Name clash | Cả hai xóa thành `f(List)` |
| `f(List<String>)` cùng `f(Set<Integer>)` | Hợp lệ | Erasure là `f(List)` và `f(Set)` |
| `f(List<String>, int)` cùng `f(List<Integer>, String)` | Hợp lệ | Tham số thứ hai phân biệt chữ ký sau erasure |
| `<T> f(T)` cùng `f(Object)` trong một lớp | Name clash | `T` không bound xóa thành `Object` |
| `<T extends Number> f(T)` cùng `f(Number)` | Name clash | `T` xóa thành `Number` |
| `<T> f(T)` cùng `f(String)` | Hợp lệ | Erasure của tham số lần lượt là `Object` và `String` |
| Lớp con ghi đè `Parent<String>.save(String)` | Có thể hợp lệ | Ghi đè generic đúng quy tắc; compiler tạo bridge nếu cần |

Những dòng minh họa cú pháp trong bảng được hiểu là khai báo trong ngữ cảnh lớp hợp lệ, và các cặp được xét riêng biệt.

## 2. Phương thức có “chữ ký” gì ở mã nguồn và sau erasure?

Trong Java, **không thể overload chỉ bằng kiểu trả về**. Khi phân biệt overload, tên phương thức và kiểu/thứ tự tham số có ý nghĩa; generic type arguments có thể khiến hai danh sách tham số trông khác ở nguồn nhưng không khác sau erasure.

Ví dụ:

```java
// Hai dòng này có vẻ khác nhau ở nguồn:
void save(java.util.List<String> names) { }
void save(java.util.List<Integer> ids) { }
```

Cách tính erasure của cả hai:

```text
save(List<String>)   -> save(List)
save(List<Integer>)  -> save(List)
```

Ở tầng JVM, đối với các phương thức `void`, cả hai sẽ cần descriptor:

```text
(Ljava/util/List;)V
```

Ký hiệu `Ljava/util/List;` đại diện tham số kiểu `java.util.List`, còn `V` biểu thị kết quả `void`. Đây là **cùng descriptor** trong ví dụ trên; JVM không có một descriptor khác nhau mang trực tiếp `String` hay `Integer` của tham số `List<E>`.

Vì vậy, compiler không tạo hai bản phương thức khác nhau chỉ vì hai type argument khác nhau. Vấn đề phải được chặn ngay lúc biên dịch.

### Các quy tắc erasure đủ để phát hiện đa số trường hợp

1. `List<String>`, `List<Integer>`, `List<?>` đều xóa về `List`.
2. `Map<String, Integer>` và `Map<Long, Long>` đều xóa về `Map`.
3. Type variable `T` **không bound** xóa thành `Object`.
4. `T extends Number` xóa thành `Number`.
5. Với nhiều bound, chẳng hạn `T extends Number & Comparable<T>`, erasure của `T` là **bound đầu tiên**, tức `Number`.
6. Kiểu mảng có thành phần generic parameterized cũng bị erasure ở phần tử: `List<String>[]` và `List<Integer>[]` đều dẫn tới dạng mảng của raw `List` về mặt erasure. (Không được tự suy ra rằng mọi phép tạo array generic đều hợp lệ.)

Trong ngữ cảnh chữ ký phương thức, hãy xóa từng loại tham số rồi so sánh toàn bộ **danh sách tham số** theo đúng thứ tự.

## 3. Xung đột overload: đổi type argument chưa đủ

Đây là lỗi phổ biến nhất.

```java
// KHÔNG BIÊN DỊCH. Đây là một file minh họa lỗi, không phải bài tập chạy.
import java.util.List;

class Service {
    void handle(List<String> names) {
        System.out.println("names");
    }

    void handle(List<Integer> ids) {
        System.out.println("ids");
    }
}
```

Compiler báo lỗi kiểu **name clash** vì hai phương thức có cùng erasure. Tên biến `names` và `ids`, nội dung thân phương thức hay việc danh sách thực tế chứa phần tử loại nào đều không làm đổi chữ ký.

Không thể sửa bằng cách đổi tên tham số:

```java
// Vẫn xung đột nếu khai báo trong cùng lớp:
void handle(List<String> a) { }
void handle(List<Integer> b) { }
```

Cũng không thể sửa bằng cách thay kiểu trả về:

```java
// KHÔNG BIÊN DỊCH, chỉ minh họa:
// int read(List<String> a) { return 1; }
// long read(List<Integer> b) { return 2L; }
```

Kiểu trả về không phải tiêu chí phân biệt overload của Java. Hơn nữa, Java còn từ chối hai phương thức chỉ khác kiểu trả về **ngay cả khi không dùng generics**.

### Không chỉ `List`: mọi generic class/interface đều có thể gặp vấn đề

```java
// KHÔNG BIÊN DỊCH nếu cặp phương thức cùng ở một lớp:
void index(java.util.Map<String, Integer> mapping) { }
void index(java.util.Map<Long, Double> mapping) { }
```

Hai tham số đều xóa về `Map`. Tương tự, `Comparable<String>` và `Comparable<Integer>` cùng xóa thành `Comparable`; một API muốn overload dựa riêng vào type argument của chúng sẽ gặp vấn đề tương tự.

## 4. Trường hợp type variable làm mất sự khác biệt

Type variable có thể khiến hai overload viết rất khác nhưng cùng erasure.

```java
// KHÔNG BIÊN DỊCH:
class Operation {
    <T> void put(T value) { }
    void put(Object value) { }
}
```

Vì `T` không giới hạn, `T` xóa thành `Object`:

```text
<T> put(T)     -> put(Object)
put(Object)    -> put(Object)
```

Tương tự:

```java
// KHÔNG BIÊN DỊCH:
class NumberOperation {
    <T extends Number> void put(T value) { }
    void put(Number value) { }
}
```

Cả hai thành `put(Number)`. Lưu ý: erasure của `T extends Number` là **Number**, không phải kiểu concrete mà một lời gọi suy luận ra, ví dụ `Integer`.

Ngược lại, đây là **overload hợp lệ**:

```java
// Snippet: có thể đặt trong cùng lớp.
static <T> void print(T value) {
    System.out.println("Generic: " + value);
}

static void print(String value) {
    System.out.println("String: " + value);
}
```

Sau erasure, phương thức generic nhận `Object`; phương thức còn lại nhận `String`. Hai descriptor khác nhau, nên không có name clash. Khi gọi `print("hello")`, compiler thường chọn overload `String` cụ thể hơn, còn `print(123)` có thể dùng overload generic. **Overload resolution xảy ra tại thời điểm biên dịch dựa trên kiểu tĩnh**, không dựa vào việc JVM thấy một `String` ở runtime rồi tự chọn lại overload.

## 5. Name clash do phương thức kế thừa

Xung đột không chỉ xảy ra khi viết hai phương thức trong cùng lớp. Một lớp con cũng có thể viết một phương thức trông khác nhưng xóa thành chữ ký của phương thức được kế thừa mà **không ghi đè hợp lệ**.

```java
// KHÔNG BIÊN DỊCH:
import java.util.List;

class Base {
    void process(List<String> names) { }
}

class Derived extends Base {
    void process(List<Integer> ids) { }
}
```

Trong `Base`, erasure là `process(List)`; trong `Derived` cũng là `process(List)`. Nhưng `process(List<Integer>)` không phải một override hợp lệ của `process(List<String>)`. Vì vậy compiler không thể vừa xem đây là overload độc lập, vừa xem đây là một ghi đè hợp lệ.

**Bài học:** khi thấy lỗi `name clash` ở lớp con chỉ khai báo **một** phương thức, đừng vội kết luận compiler báo sai. Hãy tra cả các phương thức cùng tên của superclass và interface mà lớp đó kế thừa.

### Hai interface đòi hỏi hai phiên bản generic không tương thích

```java
// Ví dụ KHÔNG HỢP LỆ nếu một lớp cố thực hiện cả hai yêu cầu
// bằng cùng tên và danh sách tham số xóa thành một chữ ký.
interface NameReceiver {
    void receive(java.util.List<String> names);
}

interface IdReceiver {
    void receive(java.util.List<Integer> ids);
}

// Một lớp cố implements cả hai sẽ không thể khai báo hai
// overload receive(List<String>) và receive(List<Integer>) hợp lệ.
```

Điều này cho thấy xung đột có thể bắt nguồn từ **hợp đồng kế thừa nhiều interface**, ngay cả khi mỗi interface riêng lẻ đều biên dịch bình thường. Để thiết kế lại API, có thể thống nhất một hợp đồng generic, dùng tên khác nhau, hoặc chuyển chiến lược xử lý qua một kiểu tham số thực sự phân biệt.

### Vì sao “hai kiểu generic khác nhau” không phải lý do đủ?

Về lý thuyết `List<String>` và `List<Integer>` là **hai kiểu tĩnh bất biến**, không thể gán trực tiếp cho nhau. Nhưng quy tắc overload và override của Java còn cần đáp ứng **sự tương thích của phương thức sau xóa kiểu**. Vì thế hai type argument khác nhau không đảm bảo chữ ký đủ khác để cùng tồn tại.

## 6. Tại sao ghi đè generic hợp lệ không bị coi là name clash?

Xét một lớp cha generic:

```java
class Parent<T> {
    void save(T value) {
        System.out.println("Parent: " + value);
    }
}

class Child extends Parent<String> {
    @Override
    void save(String value) {
        System.out.println("Child: " + value);
    }
}
```

Khi gắn `T = String`, việc `Child.save(String)` ghi đè `Parent<T>.save(T)` là hợp lệ ở mức ngôn ngữ. Nhưng sau erasure, descriptor phương thức của lớp cha nhận `Object`, còn phương thức hiện thực của lớp con nhận `String`.

Compiler giải quyết bằng **bridge method** trong bytecode của lớp con, có hành vi đại ý:

```java
// Pseudocode để hiểu bridge, KHÔNG tự thêm vào Child:
void save(Object value) {
    save((String) value);
}
```

Bridge giúp lời gọi qua `Parent<String> parent = new Child(); parent.save("abc");` vẫn vào phương thức ghi đè `Child.save(String)`. Bridge này là do compiler sinh, có thể được đánh dấu `ACC_BRIDGE` và `ACC_SYNTHETIC` trong bytecode, và được reflection nhận biết bởi `Method.isBridge()`.

Đây **không phải** xung đột overload của hai phương thức người lập trình cố khai báo. Compiler biết đây là quan hệ override hợp lệ, nên nó có thể tạo mã cầu nối mà vẫn bảo toàn tính đa hình. Khi gặp lỗi name clash, phải phân biệt:

- **Hai phương thức không có quan hệ override phù hợp** nhưng trùng erasure: bị từ chối.
- **Một override generic hợp lệ** có descriptor khác sau erasure: compiler có thể chèn bridge để bảo toàn dispatch.

Trong một lớp con, nếu bạn cố tự khai báo một phương thức có chữ ký sau erasure cạnh tranh với bridge cần được sinh, compiler cũng có thể báo lỗi name clash. Đừng viết tay phương thức `save(Object)` để “thay” bridge của `Child.save(String)`; hãy giữ override theo kiểu chuyên biệt và để compiler thực hiện nhiệm vụ này.

## 7. Subsignature và khả năng tương thích với mã cũ

Một điểm tinh tế của Java là quan hệ **subsignature**: Java cho phép một số trường hợp ghi đè khi chữ ký phương thức ở lớp con tương đương với dạng đã xóa của phương thức lớp cha. Quy tắc này phục vụ việc chuyển các thư viện cũ sang generics mà không phá vỡ mọi lớp con hiện có.

Ví dụ ý tưởng:

```java
// Snippet minh họa cơ chế tương thích nguồn khi kế thừa:
class GenericParent {
    <T> void accept(T value) { }
}

class LegacyChild extends GenericParent {
    @Override
    void accept(Object value) { }
}
```

Trong ngữ cảnh này, phương thức `accept(Object)` ở lớp con có thể override phương thức `<T> accept(T)` của lớp cha nhờ chữ ký tương thích theo erasure. Mặc dù cả hai có tham số `Object` trong descriptor, đây là một **ghi đè hợp lệ**, không phải một overload cần phân biệt.

Điều này khác với việc hai phương thức `accept(List<String>)` và `accept(List<Integer>)` cùng có erasure `accept(List)`: chúng không tự tạo thành quan hệ override chỉ vì erasure trùng nhau.

Đừng dùng mẹo “thay hết generic bằng raw type” một cách máy móc để sửa name clash. Raw types và unchecked conversions có thể làm suy yếu kiểm tra kiểu, thay đổi cách chọn overload và che giấu lỗi dữ liệu. Giải pháp đúng phải bảo toàn hợp đồng API và quan hệ kế thừa thực sự.

## 8. Chương trình Java 17+ chứng minh các trường hợp hợp lệ

Lưu toàn bộ mã sau vào tệp **`NameClashDemo.java`**:

```java
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NameClashDemo {
    // Hợp lệ: List và Set có erasure khác nhau.
    static void inspect(List<String> values) {
        System.out.println("List: " + values.get(0));
    }

    static void inspect(Set<Integer> values) {
        System.out.println("Set: " + values.iterator().next());
    }

    // Hợp lệ: T extends Number bị xóa thành Number, khác String.
    static <T extends Number> void describe(T value) {
        System.out.println("Number: " + value);
    }

    static void describe(String value) {
        System.out.println("String: " + value);
    }

    // Hợp lệ: T không bound bị xóa thành Object, khác String.
    static <T> void log(T value) {
        System.out.println("Generic: " + value);
    }

    static void log(String value) {
        System.out.println("Specific: " + value);
    }

    static class Parent<T> {
        void save(T value) {
            System.out.println("Parent: " + value);
        }
    }

    static final class Child extends Parent<String> {
        @Override
        void save(String value) {
            System.out.println("Child: " + value.toUpperCase(Locale.ROOT));
        }
    }

    public static void main(String[] args) {
        inspect(List.of("Java"));
        inspect(Set.of(7));
        describe(42);
        describe("erasure");
        log("hello");
        log(100);

        Parent<String> parent = new Child();
        parent.save("bridge");

        boolean hasBridge = Arrays.stream(Child.class.getDeclaredMethods())
                .anyMatch(Method::isBridge);
        System.out.println("Bridge method: " + hasBridge);
    }
}
```

Dùng JDK 17 trở lên:

```bash
javac -encoding UTF-8 NameClashDemo.java
java NameClashDemo
```

Kết quả:

```text
List: Java
Set: 7
Number: 42
String: erasure
Specific: hello
Generic: 100
Child: BRIDGE
Bridge method: true
```

Diễn giải:

1. `inspect(List<String>)` và `inspect(Set<Integer>)` cùng tên nhưng có **erasure khác nhau**: `List` và `Set`. Vì vậy cả hai tồn tại trong một lớp.
2. `describe(T extends Number)` xóa thành `describe(Number)`, đủ khác `describe(String)`.
3. `log(T)` xóa thành `log(Object)`, đủ khác `log(String)`. Lời gọi `log("hello")` dùng overload `String`; `log(100)` dùng overload generic.
4. `parent.save("bridge")` gọi được logic ghi đè của `Child` thông qua tham chiếu lớp cha đã gắn kiểu `String`.
5. Reflection tìm thấy **bridge method** do compiler tạo ở `Child`. Việc bridge tồn tại chứng minh compiler đã bảo toàn override sau erasure, chứ không phải sinh thêm một lớp `Parent<String>` cho đối số kiểu.

Ví dụ này là chương trình độc lập; các ví dụ **KHÔNG BIÊN DỊCH** trong những mục khác phải được thử riêng, không chép nguyên chúng vào tệp `NameClashDemo.java`.

## 9. Ba cách sửa name clash theo nhu cầu API

### Cách 1: đổi tên phương thức theo ý nghĩa nghiệp vụ

Từ hai overload xung đột:

```java
// Không hợp lệ nếu đặt cùng lớp:
// void process(List<String> names) { }
// void process(List<Integer> ids) { }
```

Chuyển thành:

```java
// Snippet hợp lệ:
void processNames(List<String> names) { /* ... */ }
void processIds(List<Integer> ids) { /* ... */ }
```

Đây thường là cách rõ nghĩa nhất khi mỗi danh sách đòi hỏi nghiệp vụ khác nhau. Nó giữ được kiểm tra kiểu tĩnh và dễ tìm API bằng tên.

### Cách 2: chọn hai kiểu tham số thực sự khác sau erasure

Nếu hai thao tác **vốn có cấu trúc đầu vào khác nhau**, dùng các abstraction phù hợp để erasure vẫn khác:

```java
// Snippet hợp lệ nếu phù hợp nhu cầu thiết kế:
void process(List<String> names) { /* ... */ }
void process(Set<Integer> ids) { /* ... */ }
```

Nhưng đừng đổi `List` thành `Set` chỉ để “lách compiler”: `Set` có ngữ nghĩa khác về phần tử trùng lặp và thứ tự, ảnh hưởng hành vi chương trình. Kiểu tham số phải phản ánh hợp đồng dữ liệu thật.

### Cách 3: dùng một phương thức generic thống nhất

Nếu cả hai kiểu phần tử được xử lý **cùng một thuật toán**, không cần hai overload:

```java
// Snippet hợp lệ: một API cho mọi kiểu T.
static <T> int count(List<T> items) {
    return items.size();
}
```

Có thể gọi `count(List<String>)` hoặc `count(List<Integer>)` mà vẫn được kiểm tra kiểu nguồn; bytecode chỉ cần một phương thức nhận `List`.

Nếu thuật toán khác nhau tùy kiểu phần tử, hãy đưa sự khác biệt vào một **strategy** hoặc callback có kiểu rõ ràng:

```java
// Snippet hợp lệ; chỉ minh họa chữ ký generic dùng callback:
static <T> void processAll(
        List<T> items,
        java.util.function.Consumer<? super T> operation) {
    for (T item : items) {
        operation.accept(item);
    }
}
```

Lúc gọi có thể truyền `processAll(names, System.out::println)`. Như vậy sự khác biệt trong xử lý được thể hiện qua đối số `Consumer`; không cần nhân đôi chữ ký overload dựa trên `List<T>`.

### Cách 4: dùng các kiểu miền riêng khi hợp đồng thực sự khác

Nếu hai tập dữ liệu tương ứng hai loại nghiệp vụ, có thể tạo kiểu wrapper:

```java
// Snippet minh họa Java 17:
record NameBatch(List<String> values) { }
record IdBatch(List<Integer> values) { }

void process(NameBatch names) { /* ... */ }
void process(IdBatch ids) { /* ... */ }
```

Hai kiểu bản ghi `NameBatch` và `IdBatch` là **hai class runtime khác nhau**, do đó phân biệt được overload. Cách này hữu ích khi cần biểu diễn các khái niệm miền thật, kèm validation hay metadata riêng. Không nên tạo wrapper vô cớ chỉ để che một API chưa rõ mục đích.

## 10. Những trường hợp dễ bị hiểu nhầm

**Nhầm 1 — “`List<String>` khác `List<Integer>` nên overload chắc chắn được.”** Sai. Khác nhau ở kiểu tĩnh không đồng nghĩa khác nhau ở chữ ký erasure.

**Nhầm 2 — “Cùng erasure thì mọi override đều lỗi.”** Sai. Override generic hợp lệ có thể cần bridge method; compiler không xử lý nó như hai overload cạnh tranh.

**Nhầm 3 — “Đổi kiểu trả về giải quyết clash.”** Sai. Kiểu trả về không dùng để phân biệt hai overload Java.

**Nhầm 4 — “Tên biến khác nhau tạo thành chữ ký khác.”** Sai. `process(List<String> names)` và `process(List<Integer> ids)` vẫn bị name clash.

**Nhầm 5 — “Giới hạn `T extends Number` vẫn xóa thành `Object`.”** Sai. Erasure của `T` là giới hạn đầu tiên, tức `Number`.

**Nhầm 6 — “Raw type là lối sửa phổ quát.”** Sai. Chuyển sang raw `List` thường giữ nguyên erasure `List`, đồng thời làm mất an toàn kiểu.

**Nhầm 7 — “Compiler nhìn dữ liệu thực bên trong list để chọn overload.”** Sai. Chọn overload dựa trên thông tin kiểu lúc biên dịch, còn `List<String>` không mang một runtime class riêng với `List<Integer>`.

**Nhầm 8 — “Một lớp có hai phương thức cùng tên chỉ khác generic là lỗi do reflection.”** Sai. Name clash được chẩn đoán lúc biên dịch Java, trước khi đến runtime reflection.

**Nhầm 9 — “Bridge method là phương thức overload do lập trình viên phải viết.”** Sai. Bridge là bytecode compiler sinh trong các trường hợp cần thiết để giữ tính đa hình.

## 11. Cách chẩn đoán name clash từng bước

Khi javac báo `name clash: ... have the same erasure` hoặc một thông báo tương tự:

1. **Liệt kê tất cả phương thức cùng tên**, kể cả phương thức của superclass và interface; không chỉ xem phương thức vừa sửa.
2. **Ghi toàn bộ danh sách tham số** của từng phương thức theo thứ tự. Đừng dựa vào tên biến hay kiểu trả về.
3. **Xóa các đối số kiểu:** `List<String>` → `List`; `Map<K,V>` → `Map`.
4. **Xóa type variable theo bound đầu tiên:** `<T>` → `Object`; `<T extends Number>` → `Number`.
5. **So sánh các danh sách tham số đã xóa.** Nếu khác nhau, có thể vẫn là overload hợp lệ (còn phụ thuộc các quy tắc Java khác).
6. **Nếu trùng nhau, kiểm tra có override/subsignature hợp lệ không.** Quan hệ override hợp lệ có thể dùng bridge; hai phương thức không override nhau thì nguy cơ name clash.
7. **Chọn sửa ở mức hợp đồng API:** tên khác, tham số có ý nghĩa khác, một method generic, strategy hoặc wrapper miền; kiểm tra lại các lời gọi và các lớp con liên quan.

Không nên dùng thông báo lỗi như một “gợi ý bỏ generic cho xong”. Compiler đang chỉ ra sự bất nhất trong tập hợp phương thức sau erasure. Việc giải quyết cần nhìn cả thiết kế API và cấu trúc kế thừa.

## 12. Bài tập tự kiểm tra

**Câu 1.** Hai phương thức `void run(List<String> a)` và `void run(List<Integer> b)` cùng thuộc một lớp. Hợp lệ không?

Không. Cả hai thành `run(List)`; không thể tạo hai overload chỉ bằng type argument.

**Câu 2.** Hai phương thức `<T extends Number> void run(T x)` và `void run(String s)` cùng một lớp. Hợp lệ không?

Có. Erasure là `run(Number)` và `run(String)`.

**Câu 3.** `<T> void run(T x)` và `void run(Object x)` cùng một lớp. Hợp lệ không?

Không. Type variable `T` không bound xóa thành `Object`, gây trùng chữ ký.

**Câu 4.** `Child extends Parent<String>` và ghi đè đúng `void save(String)`. Vì sao compiler có thể sinh `save(Object)` trong bytecode mà không báo name clash?

Vì đây là **quan hệ override hợp lệ**, và `save(Object)` là bridge compiler tạo nhằm thực hiện lời gọi động tương thích với erasure của phương thức lớp cha, không phải một overload tự khai báo đang cạnh tranh.

**Câu 5.** Hai method `sum(List<String>, int)` và `sum(List<Integer>, long)` có name clash chỉ do generic không?

Không. Sau erasure chúng là `sum(List,int)` và `sum(List,long)`, nên danh sách tham số vẫn khác nhau. Các điều kiện khác của Java vẫn phải được đáp ứng.

**Câu 6.** API cần xử lý danh sách `String` và `Integer` bằng đúng cùng một thuật toán. Có nhất thiết cần hai overload không?

Không. Dùng một phương thức generic như `<T> void process(List<T> values)` là cách tự nhiên hơn.

## 13. Kết luận và quy tắc thực hành

**Name clash do erasure** xuất hiện khi những phương thức generic có các chữ ký tham số bị xóa về cùng dạng mà **không thể cùng tồn tại theo quy tắc overload/override của Java**. Điểm mấu chốt là so sánh **kiểu tham số sau xóa** và **quan hệ ghi đè**, chứ không chỉ nhìn sự khác nhau giữa các đối số `<String>`, `<Integer>` ở mã nguồn.

Khi thiết kế API generics, đừng overload các phương thức chỉ bằng cách đổi type argument của cùng một generic class. Hãy dùng tên nghiệp vụ rõ ràng, chọn kiểu tham số thực sự khác nhau khi có lý do, hoặc gộp hành vi chung vào một phương thức generic. Với subclass, kiểm tra chữ ký kế thừa và để compiler sinh bridge method cho những override generic hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-overrides-and-bridges-007">Vì sao không thể overload chỉ bằng List&lt;String&gt; và List&lt;Integer&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao không thể overload chỉ bằng `List<String>` và `List<Integer>`? Chọn thiết kế API thay thế

Giả sử một dịch vụ phải xử lý hai loại danh sách:

- `List<String>` chứa tên nhân viên; cần nối các tên thành báo cáo.
- `List<Integer>` chứa điểm số; cần cộng các điểm thành tổng.

Ý tưởng ban đầu là viết hai phương thức cùng tên:

~~~java
import java.util.List;

class InvalidProcessor {
    void process(List<String> names) {
        System.out.println(String.join(", ", names));
    }

    void process(List<Integer> scores) {
        System.out.println(scores.stream().mapToInt(Integer::intValue).sum());
    }
}
~~~

**Class này cố ý không biên dịch.** Trong Java Generics, `List<String>` và `List<Integer>` khác nhau về quy tắc kiểm tra kiểu ở source, nhưng chúng đều bị **type erasure** thành `java.util.List` ở bytecode. Cả hai phương thức muốn có chữ ký nguồn phân biệt được bằng cùng một danh sách tham số đã xóa:

~~~text
process(List<String>)    -> process(List)
process(List<Integer>)   -> process(List)
~~~

Khi hai khai báo cùng nằm trong một class và không tạo quan hệ override hợp lệ, compiler báo **name clash**. Bài này tập trung vào bước tiếp theo của lập trình viên: **chọn API đúng với mục đích nghiệp vụ thay vì tìm cách vượt qua compiler**.

## 1. Điều compiler thực sự cần để overload

Overload được phân giải ở compile time bằng tên method và các kiểu tham số theo quy tắc Java, không phải bằng cách JVM nhìn các phần tử trong List rồi chọn nhánh ở runtime.

Nếu hai phương thức đều có kiểu trả về `void`, descriptor giả định của chúng sẽ cùng là:

~~~text
(Ljava/util/List;)V
~~~

`(Ljava/util/List;)` mô tả một tham số `java.util.List`, còn `V` là `void`. Các type arguments `String` và `Integer` không tạo hai erased parameter types riêng. Trong class file, generic `Signature` metadata có thể ghi type arguments của những **phương thức hợp lệ**; nó không cho phép Java tuyên bố hai overload đụng nhau chỉ bằng cách thêm Generic Signature khác nhau.

Cần phân biệt ba khái niệm:

| Khái niệm | Ví dụ | Ảnh hưởng tới vấn đề |
| --- | --- | --- |
| Generic type argument | `List<String>` khác `List<Integer>` | Compiler dùng để kiểm tra phần tử; hai kiểu đều erasure thành List. |
| Overload parameter type | `process(String)` và `process(Integer)` | Hai erased parameter types khác nhau, overload hợp lệ. |
| Override | `Parent<T>.save(T)` được `Child extends Parent<String>` ghi đè bằng `save(String)` | Compiler có thể tạo bridge để giữ dispatch, **không phải** hai overload khác type arguments. |

**Không sửa** bằng cách thay `void` thành kiểu trả về khác, đổi tên biến `names`/`scores`, thêm type parameter `<T>` vào một method, hoặc viết `@SuppressWarnings("unchecked")`. Những thao tác đó không tạo một danh sách tham số phân biệt được sau erasure.

### Kiểm chứng lỗi compile riêng

Lưu code `InvalidProcessor` ở trên thành **InvalidProcessor.java**, chạy:

~~~bash
javac --release 17 InvalidProcessor.java
~~~

Kết quả mong đợi: **javac thất bại**, với chẩn đoán chứa khái niệm name clash hoặc same erasure. Đừng cố ghép file này vào chương trình runnable của phần 6: nó cố ý dùng làm **negative compilation test**, không phải ví dụ runtime.

## 2. Chọn một trong bốn chiến lược theo nhu cầu

| Nhu cầu thật | Thiết kế ưu tiên | Vì sao |
| --- | --- | --- |
| Hai hành vi **khác nhau hoàn toàn** | Hai tên method có nghĩa, như `joinNames` và `sumScores` | Tên đã mô tả hợp đồng và tránh overload gây bất ngờ. |
| **Một thuật toán** dùng được cho nhiều phần tử | Một method `<T>` hoặc method nhận `List<?>` | Không nhân đôi logic chỉ để phân biệt kiểu phần tử. |
| Một pipeline giống nhau, **hành vi mỗi phần tử khác nhau** | Generic method nhận `Function`/`Consumer` | Compiler type-check callback tại từng lời gọi. |
| Phải giữ **cùng tên process**, nhưng đầu vào là hai khái niệm miền khác nhau | Hai wrapper types riêng, ví dụ records `Names` và `Scores` | JVM có hai erased parameter classes khác nhau. |
| Muốn **xác minh** kiểu phần tử khi nhận dữ liệu không tin cậy | Một method kèm `Class<?>`/validator | Runtime token kiểm tra dữ liệu, không biến two List overloads thành hợp lệ. |

Đây là các lựa chọn về **hợp đồng API**, không chỉ cú pháp để vượt lỗi javac. Đổi từ chữ ký công khai `process(List...)` đã phát hành sang `process(Names)` có thể là **breaking change** với callers/binaries cũ; muốn tiến hóa thư viện phải có kế hoạch chuyển đổi, overload adapter được phép bởi Java, versioning và kiểm tra tương thích.

## 3. Phương án A: đặt tên theo nghiệp vụ

Khi các thuật toán bản chất khác nhau, code dễ hiểu nhất thường có **hai tên khác nhau**:

~~~java
static String joinNames(List<String> names) {
    return String.join(", ", names);
}

static int sumScores(List<Integer> scores) {
    return scores.stream().mapToInt(Integer::intValue).sum();
}
~~~

Ý nghĩa của `joinNames` và `sumScores` hiển thị ngay trong API. Không cần ép kiểu và không tạo tên method chung mơ hồ với yêu cầu xử lý khác nhau.

Khi sử dụng:

~~~java
String report = joinNames(List.of("Lan", "Mai"));
int total = sumScores(List.of(5, 7));
~~~

`report` là `"Lan, Mai"`, `total` là `12`. Hai method có tên khác nhau nên không có nguy cơ erased-signature collision.

**Điểm đánh đổi:** Nếu nhiều phiên bản thực sự làm **cùng một thao tác duyệt** và chỉ khác callback, tách nhiều method có thể làm trùng code. Khi đó dùng generic method chung thường tốt hơn.

## 4. Phương án B: một generic method với hành vi được truyền vào

Một method **không cần biết cụ thể T** có thể xử lý List<T> theo cùng một thuật toán:

~~~java
static <T, R> List<R> mapAll(
        List<? extends T> source,
        java.util.function.Function<? super T, ? extends R> transform) {

    List<R> output = new ArrayList<>();

    for (T item : source) {
        output.add(transform.apply(item));
    }

    return output;
}
~~~

Nó **không** tạo hai overload `mapAll(List<String>)` và `mapAll(List<Integer>)`; đây là **một** generic method được compiler suy luận type arguments cho mỗi lời gọi.

~~~java
List<Integer> lengths = mapAll(
        List.of("a", "abcd"), String::length
);

List<Integer> squares = mapAll(
        List.of(2, 3), n -> n * n
);
~~~

Kết quả:

~~~text
lengths=[1, 4]
squares=[4, 9]
~~~

Ở lời gọi đầu, compiler biết phần tử của nguồn là String và callback trả Integer. Ở lời gọi thứ hai, phần tử là Integer và lambda tạo Integer. Callback có các tham số wildcard `? super T`, `? extends R` để dùng đúng vai trò nhận T/trả R theo nhu cầu.

### Vì sao không đặt generic method ở mọi nơi?

Nếu body chỉ `System.out.println(value)` và không liên hệ kiểu đầu vào với kết quả/đối số khác, một tham số `List<?>` có thể đủ. Nếu nghiệp vụ thật sự cần gọi API chỉ có ở String hoặc Integer, một `<T>` không giới hạn **không tự cho phép gọi String.length() hoặc Number.intValue() trên mọi T**. Khi đó chọn callback, bound phù hợp hoặc tách phương thức theo nghiệp vụ.

**Kết luận:** một generic method chỉ tốt khi nó mô tả đúng **cấu trúc thuật toán chung**, không phải thủ thuật đánh lừa name clash.

## 5. Phương án C: wrapper types giúp overload hợp lệ và rõ miền dữ liệu

Đôi khi một entry point tên `process` được thiết kế để nhận **hai loại yêu cầu có ý nghĩa miền nghiệp vụ khác nhau**. Ta có thể dùng records riêng:

~~~java
record Names(List<String> items) {}
record Scores(List<Integer> items) {}

static String process(Names request) {
    return String.join("|", request.items());
}

static String process(Scores request) {
    int total = 0;
    for (int value : request.items()) {
        total += value;
    }
    return "scores=" + total;
}
~~~

Hai phương thức có parameter types `Names` và `Scores`. Đây là **hai runtime classes riêng**, vì vậy erased descriptors cũng khác nhau. Overload hợp lệ mà không cần dựa vào runtime generic arguments.

Tại điểm gọi:

~~~java
process(new Names(List.of("Lan", "Mai")));
process(new Scores(List.of(5, 7)));
~~~

Compiler xác định overload nhờ class thực sự của đối số. Các record không có mối quan hệ subtype với nhau, nên lời gọi trần `process(null)` **có thể mơ hồ**: cả hai overload nhận null mà không có một overload cụ thể hơn. Đây là vấn đề API khi overload bằng hai reference types ngang hàng, **không** phải lỗi erasure.

### Record không tự deep-immutable

`record Names(List<String> items)` có field tham chiếu cuối cùng (*final component*), nhưng một List mutable vẫn có thể bị sửa qua alias. Nếu yêu cầu API nhận một snapshot độc lập và không sửa được, có thể dùng **compact constructor**:

~~~java
record Names(List<String> items) {
    Names {
        items = List.copyOf(items);
    }
}
~~~

`List.copyOf` tạo list không cho sửa và từ chối null elements; đây là *shallow copy* đối với các element references. Chữ ký record bảo vệ cấu trúc collection nhưng không tự làm mọi element object bất biến.

**Khi nào chọn wrappers:** input type chứa các quy tắc riêng, metadata hay validation thực sự có giá trị. Đừng tạo thêm hai class chỉ để đổi tên một tiện ích nhỏ mà có thể gọi `joinNames`/`sumScores` một cách rõ ràng hơn.

## 6. Chương trình Java 17 hoàn chỉnh: so sánh wrapper, callback và runtime validation

Lưu thành **ListOverloadApiDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ListOverloadApiDemo {
    record Names(List<String> items) {
        Names {
            items = List.copyOf(items);
        }
    }

    record Scores(List<Integer> items) {
        Scores {
            items = List.copyOf(items);
        }
    }

    static String process(Names batch) {
        return "names=" + String.join("|", batch.items());
    }

    static String process(Scores batch) {
        int total = 0;
        for (int score : batch.items()) {
            total += score;
        }
        return "scores=" + total;
    }

    static <T, R> List<R> mapAll(
            List<? extends T> source,
            Function<? super T, ? extends R> transform) {

        List<R> output = new ArrayList<>();
        for (T value : source) {
            output.add(transform.apply(value));
        }
        return output;
    }

    static boolean allElements(
            List<?> values, Class<?> elementType) {

        for (Object value : values) {
            if (!elementType.isInstance(value)) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println(
                process(new Names(List.of("Lan", "Mai")))
        );

        System.out.println(
                process(new Scores(List.of(5, 7)))
        );

        System.out.println(
                "lengths=" + mapAll(
                        List.of("a", "abcd"),
                        String::length
                )
        );

        System.out.println(
                "squares=" + mapAll(
                        List.of(2, 3),
                        number -> number * number
                )
        );

        System.out.println(
                "strings=" + allElements(
                        List.of("a", "b"), String.class
                )
        );

        System.out.println(
                "mixed=" + allElements(
                        List.of(1, "b"), String.class
                )
        );

        System.out.println(
                "sameRuntimeClass=" +
                (new ArrayList<String>().getClass()
                        == new ArrayList<Integer>().getClass())
        );
    }
}
~~~

Biên dịch/chạy bằng Java 17:

~~~bash
javac --release 17 -Xlint:all ListOverloadApiDemo.java
java ListOverloadApiDemo
~~~

**Stdout đã kiểm chứng:**

~~~text
names=Lan|Mai
scores=12
lengths=[1, 4]
squares=[4, 9]
strings=true
mixed=false
sameRuntimeClass=true
~~~

Đọc kết quả theo hợp đồng:

1. **`names` và `scores`:** cùng tên `process` nhưng hai tham số **thực sự là Names/Scores**, khác runtime class và erasure. Record compact constructors phòng ngừa list bị thay đổi qua chính reference đầu vào sau khi xây bản snapshot.
2. **`lengths` và `squares`:** **một method `mapAll`** dùng hai lần; callback quyết định phép biến đổi cụ thể trong từng lần gọi.
3. **`strings` và `mixed`:** `Class<?>.isInstance` kiểm tra **từng phần tử thực** của List<?>. Nó không phải cách tạo hai overload bằng List<String> và List<Integer>.
4. **`sameRuntimeClass`:** đối tượng ArrayList của hai parameterizations sử dụng cùng runtime class. Không được suy từ class equality thành quan hệ subtype giữa List<String> và List<Integer>.

### Giới hạn của validation bằng Class

`allElements` trả `false` khi gặp bất kỳ object nào không phải instance của class token (kể cả null). Đây là **chính sách kiểm tra minh họa**; nghiệp vụ khác có thể cho phép null, hoặc yêu cầu thu thập tất cả lỗi thay vì boolean.

`Class<String>` cho phép kiểm tra từng object là String, nhưng `Class<List<String>>` không có class literal tương đương `List<String>.class` để chứng minh toàn bộ danh sách lồng nhau đã đúng type arguments. Việc kiểm tra cấu trúc generic lồng nhau đòi validator qua từng tầng hoặc schema phù hợp.

## 7. Phương án D: truyền type token hoặc strategy có ý nghĩa

Nếu API của bạn cần **một method duy nhất** và muốn người gọi công bố loại phần tử dự kiến, có thể chọn:

~~~java
static <T> boolean validate(
        List<T> values, Class<T> elementType) {

    for (T value : values) {
        if (!elementType.isInstance(value)) {
            return false;
        }
    }
    return true;
}
~~~

Chữ ký này có một generic method; compiler cố gắng liên hệ type argument của List và Class theo cùng T tại call site. Một lời gọi có source typed đúng cũng phải hiểu rằng **raw/unchecked aliases có thể phá contract**, vì vậy kiểm tra runtime elements vẫn hữu ích khi dữ liệu không tin cậy.

Cần tránh hiểu nhầm:

~~~java
// VẪN KHÔNG HỢP LỆ: hai phương thức cùng erased tham số (List, Class).
// void process(List<String> items, Class<String> token) {}
// void process(List<Integer> items, Class<Integer> token) {}
~~~

Đổi `Class<String>` thành `Class<Integer>` chỉ đổi type argument của một generic class nữa; erasure vẫn là `Class`. Token hữu ích **làm dữ liệu/validation cho một method**, không phải như một trục overload mới nếu raw parameter types không đổi.

Một lựa chọn khác là **strategy interface**: caller truyền đối tượng xử lý mà API gọi tới qua một contract. Strategy phù hợp hơn khi hành vi có trạng thái, cấu hình, nhiều bước hoặc cần tái sử dụng, trong khi một Function/Consumer gọn hơn cho biến đổi đơn giản.

## 8. Overload được phép nhưng dễ gây hiểu nhầm

Hai method dưới đây có erased parameter types khác nhau và có thể cùng tồn tại:

~~~java
static void process(java.util.Collection<String> names) {}
static void process(java.util.List<Integer> scores) {}
~~~

Erasure lần lượt là Collection và List; chúng không va chạm như hai tham số cùng List. Tuy nhiên, đây là một thiết kế **dễ đánh lừa người đọc**:

- Method selection dựa vào kiểu tĩnh của đối số và overload resolution; nó không tự nhìn kiểu phần tử ở runtime để quyết định nhánh.
- Danh sách String có thể được nhìn qua Collection<String> hoặc một kiểu trung gian; các loại collection và wildcard khiến call sites khó suy luận.
- Một số lời gọi có inference hoặc null có thể gặp lựa chọn overload bất ngờ/không áp dụng được.

Vì thế nếu ý định nghiệp vụ thực sự là **tách tên và điểm số**, hai tên rõ ràng hoặc hai wrapper classes thường bền vững hơn cách tạo khác biệt raw container type một cách gượng ép.

Thêm một tham số giả `boolean isString` để làm khác arity chỉ vì compiler cấm hai List overload cũng khiến callers khó hiểu. Chỉ thêm discriminator nếu nó biểu thị **một thuộc tính nghiệp vụ cần thiết** và được validate/diễn đạt rõ.

## 9. Không nhầm name clash với bridge của override

Một method trong superclass generic:

~~~java
class Box<T> {
    T echo(T value) {
        return value;
    }
}

class StringBox extends Box<String> {
    @Override
    String echo(String value) {
        return value.toUpperCase();
    }
}
~~~

`StringBox.echo(String)` **ghi đè hợp lệ** phương thức chuyên biệt hóa từ Box<String>. Compiler có thể sinh bridge `echo(Object)` để giữ dynamic dispatch sau erasure.

Đây là tình huống **khác** với hai khai báo cạnh nhau:

~~~java
// CỐ Ý KHÔNG HỢP LỆ:
// void echo(List<String> names) {}
// void echo(List<Integer> numbers) {}
~~~

Bridge method chỉ giúp duy trì một **override đã hợp lệ** từ source Java khi descriptors cần được nối. Nó không cứu một cặp overload không hợp lệ vì cùng erasure. Đừng thêm bridge thủ công hoặc dựa vào runtime generic argument để dispatch.

## 10. API evolution: chọn lời gọi dễ dùng, không chỉ chữ ký biên dịch được

Trước khi chốt phương án, cần hỏi:

1. **Hai hành vi có thật sự khác nhau?** Nếu có, tên `joinNames` và `sumScores` thể hiện ý nghĩa tốt hơn `process` chung chung.
2. **Có chung một pipeline không?** Nếu có, một generic method với callback tránh duplicate logic.
3. **Input có hai miền dữ liệu rõ ràng không?** Nếu có, records/classes wrapper khác nhau có thể cho overload hợp lệ và chứa validation phù hợp.
4. **Dữ liệu đến từ raw/JSON/Reflection không?** Nếu có, cần xác thực runtime thực sự; đừng xem unchecked cast hoặc List<?> như một validator.
5. **Code đã phát hành hay mới thiết kế?** Với API cũ, thay method descriptors có thể làm binary clients cũ gặp `NoSuchMethodError`. Chọn migration path phù hợp thay vì phá ngay hợp đồng.
6. **Sẽ thêm kiểu phần tử thứ ba sau này không?** Nếu sẽ mở rộng nhiều kiểu, callback/strategy hoặc domain interfaces có thể thuận tiện hơn việc nhân đôi overloads và wrappers.

Khi buộc phải duy trì binary compatibility, một adapter có **tên và descriptor cũ** có thể giữ các callers đã biên dịch; điều này đòi kiểm tra thực tế với class files cũ và mới. Không tự cho rằng thêm một overload mới lúc nào cũng không thay đổi source overload resolution hoặc hành vi callers sau recompile.

## 11. Tự kiểm tra

**Câu 1.** Vì sao `process(List<String>)` và `process(List<Integer>)` cùng class không hợp lệ?

**Đáp án:** Erasure của hai tham số đều là List, dẫn đến name clash. Type arguments chỉ giúp compiler kiểm tra nguồn, không tạo runtime classes riêng cho mỗi List<E>.

**Câu 2.** Hai records `Names` và `Scores` khác nhau có thể làm tham số hai overload `process` không?

**Đáp án:** Có. Hai erased parameter types thực sự khác nhau; null không có target cụ thể có thể gây overload ambiguity.

**Câu 3.** Nếu muốn một thuật toán xử lý nhiều loại danh sách với callback khác nhau, nên dùng gì?

**Đáp án:** Một generic method nhận `Function<? super T,? extends R>` hay `Consumer<? super T>` theo dòng dữ liệu, không tạo hai List<E> overloads.

**Câu 4.** Thêm `Class<String>` và `Class<Integer>` làm tham số thứ hai có tự giải quyết erased collision không?

**Đáp án:** Không. `Class<String>` và `Class<Integer>` cùng erasure là Class, nên cả hai method vẫn có erased parameter list (List, Class).

**Câu 5.** Hai ArrayList có cùng runtime class chứng minh generic type arguments bằng nhau không?

**Đáp án:** Không. Erasure làm runtime classes thông thường không chứa type arguments theo cách đó. Không dùng `getClass()` để suy List<String> từ data.

**Câu 6.** `process(Collection<String>)` và `process(List<Integer>)` có hợp lệ về kiểu tham số không?

**Đáp án:** Có thể có, vì erased parameter types là Collection và List. Nhưng về thiết kế API dễ gây nhầm; phải xét overload resolution và type tĩnh thực sự.

**Câu 7.** Bridge methods có giúp khai báo hai overload chỉ khác List<String>/List<Integer> không?

**Đáp án:** Không. Bridge phục vụ dynamic dispatch của override hợp lệ; còn name clash khiến source không biên dịch được từ đầu.

**Câu 8.** Nếu library trước đây phát hành một public method nhận List và nay đổi sang wrapper Names, clients .class cũ tự chạy được không?

**Đáp án:** Không thể mặc định. Method descriptor đã đổi, nên cần adapter/compatibility plan hoặc rebuild clients; kiểm thử binary clients cũ với library mới.

**Ghi nhớ:** **đừng cố phân biệt hai overload bằng generic arguments vốn bị erasure**. Hãy quyết định xem API cần hai **tên nghiệp vụ**, **một generic pipeline**, hay **hai domain types thật**; chỉ dùng runtime token khi nó phục vụ validation hoặc logic đã được thiết kế rõ, không dùng như thủ thuật tạo overload. 


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-overrides-and-bridges-008">Ảnh hưởng của bridge đến dấu vết lỗi ép kiểu lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của bridge method đến dấu vết lỗi ép kiểu lúc chạy trong Java Generics

## Mục tiêu học tập

Khi sử dụng generics và ghi đè phương thức, đôi khi một `ClassCastException` xuất hiện ở nơi không thấy phép ép kiểu nào trong mã nguồn. Stack trace có thể chỉ đến phương thức của lớp con, dù phương thức đó khai báo tham số `String` và lỗi lại nói một `Integer` không thể chuyển thành `String`. Một nguyên nhân thường gặp là **bridge method** do trình biên dịch tự sinh để giữ đúng cơ chế ghi đè sau *type erasure*.

Sau bài này, bạn có thể:

1. Giải thích vì sao phương thức cầu nối được tạo ra khi lớp con chuyên biệt hóa tham số kiểu generic.
2. Phân biệt `ClassCastException` phát sinh **trong bridge** với lỗi ép kiểu xảy ra ở **vị trí người gọi**.
3. Đọc stack trace mà không nhầm bridge do compiler sinh với phương thức do lập trình viên viết.
4. Sử dụng `javap -c -p`, `javap -v` và reflection (`Method.isBridge()`, `isSynthetic()`) để xác minh vị trí ép kiểu.
5. Truy tìm nguồn dữ liệu sai kiểu từ raw type, unchecked invocation hoặc đoạn mã vượt qua kiểm tra generic.

## 1. Quan hệ giữa erasure, ghi đè và bridge

Generics của Java được triển khai chủ yếu bằng **type erasure**: tham số kiểu không được biểu diễn trong method descriptor của JVM giống như thông tin generic của mã nguồn. Ví dụ:

```java
interface Handler<T> {
    void handle(T value);
}

final class TextHandler implements Handler<String> {
    @Override
    public void handle(String value) {
        System.out.println(value);
    }
}
```

Đây là đoạn minh họa khai báo các kiểu; nếu muốn chạy một chương trình hoàn chỉnh, dùng ví dụ `BridgeTraceDemo.java` ở phần 3.

Ở mã nguồn, `Handler<String>` yêu cầu nhận `String` và `TextHandler.handle(String)` là một phương thức ghi đè hợp lệ. Nhưng khi dịch:

- Phương thức interface `Handler<T>.handle(T)` có erasure tương ứng với tham số `Object` vì `T` không có bound tường minh.
- Phương thức thực thi do người lập trình viết có tham số `String`.
- Để lời gọi tới `Handler.handle(Object)` vẫn dispatch tới implementation `TextHandler.handle(String)`, `javac` tạo thêm một **bridge method** mang chữ ký bytecode tương ứng.

Về mặt ý tưởng, compiler sinh logic như sau:

```java
// SƠ ĐỒ Ý TƯỞNG: không được tự khai báo thêm vào TextHandler.
public void handle(Object value) {
    handle((String) value);
}
```

Bridge có phép ép kiểu tại runtime: `(String) value`. Nếu đối số thực tế là `Integer`, phép ép kiểu này ném `ClassCastException` **trước khi phần thân `handle(String)` của người lập trình được gọi**.

Sơ đồ luồng:

```text
Handler<String> reference
          |
          | gọi handle("hello") — an toàn, đối số String
          v
  invokeinterface Handler.handle(Object)
          |
          v
  TextHandler.handle(Object) [bridge]
          |
          | checkcast String (PASS)
          v
  TextHandler.handle(String) [mã nguồn]
          |
          v
  Xử lý bình thường


Handler raw reference
          |
          | gọi handle(Integer) — unchecked invocation
          v
  invokeinterface Handler.handle(Object)
          |
          v
  TextHandler.handle(Object) [bridge]
          |
          | checkcast String (FAIL)
          v
  ClassCastException
  (chưa đi tới handle(String))
```

**Điểm quyết định:** bridge không phải một bước “Java tự kiểm tra generic ở mọi thời điểm chạy”. Nó là phương thức chuyển tiếp có các thao tác cụ thể do compiler tạo ra để giữ đúng quy tắc override. Một số bridge có `checkcast`, một số chỉ ủy quyền lời gọi và không cần ép kiểu đối số.

## 2. Vì sao stack trace có thể gây hiểu nhầm?

Stack trace cho biết phương thức đang thực thi khi ngoại lệ được tạo ra và chuỗi lời gọi dẫn đến đó. Nếu lệnh `checkcast` nằm trong bridge, top frame có thể hiển thị tên phương thức ở lớp con, ví dụ:

```text
BridgeTraceDemo$TextHandler.handle(BridgeTraceDemo.java:<line>)
```

`<line>` ở trên chỉ là ký hiệu minh họa: số dòng thực tế phụ thuộc nội dung tệp, thông tin debug khi biên dịch và compiler. Đây không phải nguyên văn stdout của chương trình ở phần sau.

Người đọc mã nguồn tìm `TextHandler.handle(String)` sẽ thấy không có cast nào:

```java
@Override
public void handle(String value) {
    System.out.println("accepted:" + value);
}
```

Tuy nhiên, JVM đang chạy **một phương thức khác cũng tên `handle`**: bridge `handle(Object)`. Trong stack trace thông thường, `StackTraceElement` cung cấp tên lớp, tên phương thức, tệp và dòng, **không cung cấp danh sách kiểu tham số hay cờ bridge**. Hai phương thức cùng tên có thể nhìn giống nhau khi chỉ quan sát stack trace.

Ngay cả vị trí dòng được gán cho bridge cũng không đủ để kết luận đó là phương thức nguồn. Trình biên dịch có thể ánh xạ bridge tới dòng khai báo phương thức hoặc thông tin dòng tương ứng. Do đó, **đừng suy ra rằng một lỗi ở dòng `handle(String)` nhất thiết do thân phương thức đó trực tiếp ném**.

Muốn kiểm chứng, cần kết hợp stack trace với bytecode hoặc reflection của lớp đã biên dịch.

## 3. Chương trình Java 17 hoàn chỉnh: lỗi ở bridge và lỗi ở caller

Chương trình dưới đây trình bày hai tình huống **cố ý** dùng raw type để vượt qua kiểm tra generic:

- Gọi handler chuyên biệt cho `String` qua một tham chiếu raw để đưa `Integer` vào bridge.
- Ghi một `Integer` vào `Box<String>` qua raw `Box`. `Box` không có override tạo bridge, nên lỗi xảy ra khi người gọi nhận kết quả thành `String`.

**Tệp `BridgeTraceDemo.java`:**

```java
import java.lang.reflect.Method;

public class BridgeTraceDemo {
    interface Handler<T> {
        void handle(T value);
    }

    static final class TextHandler implements Handler<String> {
        @Override
        public void handle(String value) {
            System.out.println("accepted:" + value);
        }
    }

    static final class Box<T> {
        private T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        void set(T value) {
            this.value = value;
        }
    }

    public static void main(String[] args) {
        Handler<String> typedHandler = new TextHandler();
        typedHandler.handle("safe");

        Handler rawHandler = typedHandler;
        try {
            rawHandler.handle(123);
        } catch (ClassCastException exception) {
            StackTraceElement frame = exception.getStackTrace()[0];
            System.out.println("bridge error:" + exception.getClass().getSimpleName());
            System.out.println("bridge top:" + frame.getClassName()
                    .replace("BridgeTraceDemo$", "") + "." + frame.getMethodName());
        }

        for (Method method : TextHandler.class.getDeclaredMethods()) {
            if (method.isBridge()) {
                System.out.println("bridge reflection:"
                        + method.getName()
                        + "(" + method.getParameterTypes()[0].getSimpleName()
                        + "), synthetic=" + method.isSynthetic());
            }
        }

        Box<String> typedBox = new Box<>("initial");
        Box rawBox = typedBox;
        rawBox.set(123);
        try {
            String value = typedBox.get();
            System.out.println(value);
        } catch (ClassCastException exception) {
            StackTraceElement frame = exception.getStackTrace()[0];
            System.out.println("caller error:" + exception.getClass().getSimpleName());
            System.out.println("caller top:" + frame.getClassName()
                    + "." + frame.getMethodName());
        }
    }
}
```

**Biên dịch và chạy bằng JDK 17+:**

```bash
javac --release 17 BridgeTraceDemo.java
java BridgeTraceDemo
```

`javac` có thể in ghi chú về thao tác unchecked/unsafe ở stderr trong bước biên dịch, vì hai lời gọi dùng raw type là cố ý. Phần dưới là **stdout của lệnh `java`**, đã kiểm chứng trên chương trình này:

```text
accepted:safe
bridge error:ClassCastException
bridge top:TextHandler.handle
bridge reflection:handle(Object), synthetic=true
caller error:ClassCastException
caller top:BridgeTraceDemo.main
```

Chương trình bắt ngoại lệ để trình bày phần tên phương thức ổn định hơn trong stack trace. Nó **không** in số dòng, class-loader hay toàn văn `ClassCastException.getMessage()`, vì các chi tiết đó có thể khác giữa các JDK hoặc cấu hình biên dịch.

### 3.1. Nhánh 1: `ClassCastException` từ bridge

Các câu lệnh:

```java
Handler<String> typedHandler = new TextHandler();
typedHandler.handle("safe");

Handler rawHandler = typedHandler;
rawHandler.handle(123);
```

Đoạn này đã có trong chương trình hoàn chỉnh; dòng cuối ở ví dụ thực tế được bọc trong `try/catch`.

Lời gọi `typedHandler.handle("safe")` hợp lệ vì đối số thuộc `String`. Ngược lại, nếu viết:

```java
// typedHandler.handle(123); // Không biên dịch: cần String, nhận int/Integer.
```

thì trình biên dịch sẽ chặn lỗi. Chỉ khi chủ động dùng raw `Handler`, lời gọi `rawHandler.handle(123)` mới có thể được biên dịch với cảnh báo unchecked và truyền `Integer` qua chữ ký đã xóa `handle(Object)`.

Thứ tự lúc runtime:

1. `rawHandler` vẫn trỏ đến một `TextHandler` thật; raw type chỉ thay đổi cách compiler kiểm tra ở nguồn, không tạo đối tượng handler khác.
2. Lời gọi theo interface được dispatch tới bridge `TextHandler.handle(Object)`.
3. Bridge thực thi `checkcast java/lang/String`.
4. `Integer` không phải `String`, nên JVM tạo `ClassCastException`.
5. Chương trình bắt lỗi và in `bridge top:TextHandler.handle`. Dòng `System.out.println("accepted:" + value)` trong `handle(String)` không được chạy ở lượt lỗi.

Ở đây, bridge chính là **vị trí ngoại lệ phát sinh**. Nhưng **nguyên nhân tạo dữ liệu sai kiểu** nằm ở lời gọi qua raw reference. Đọc top frame là bước xác định nơi lỗi bị phát hiện, chưa đủ để xác định vị trí gây ra vi phạm kiểu ban đầu.

### 3.2. Nhánh 2: lỗi ép kiểu ở caller mà không có bridge

`Box<T>` là một lớp generic thông thường:

```java
static final class Box<T> {
    private T value;

    Box(T value) { this.value = value; }

    T get() { return value; }

    void set(T value) { this.value = value; }
}
```

Trong class file của `Box`, `T` không bị ràng buộc nên các vị trí `T` được xóa thành `Object`. Không có subclass ghi đè phương thức generic ở trường hợp này nên **không cần sinh bridge để xử lý `Box.get()`/`set()`**.

Các câu lệnh:

```java
Box<String> typedBox = new Box<>("initial");
Box rawBox = typedBox;
rawBox.set(123);
String value = typedBox.get(); // ClassCastException tại caller.
```

được dùng bên trong chương trình chính; dòng đọc `value` được bọc `try/catch` để quan sát lỗi.

Sau lời gọi raw `set(123)`, trường trong cùng một đối tượng `Box<String>` thực tế chứa `Integer`. Đây là một dạng **heap pollution**: một vị trí được chương trình xem là mang giá trị của kiểu đã tham số hóa chứa đối tượng không thỏa kỳ vọng đó.

Khi `typedBox.get()` trả về giá trị qua descriptor có kiểu `Object`, compiler chèn phép cast sang `String` **ở chỗ dùng kết quả** để đáp ứng khai báo `String value`. Vì vậy lỗi hiển thị:

```text
caller error:ClassCastException
caller top:BridgeTraceDemo.main
```

Đây là chứng cứ rằng **không phải mọi ClassCastException liên quan đến generics đều có bridge**. Cùng một nguyên nhân gốc là vượt kiểm tra kiểu, nhưng điểm phát hiện sai kiểu có thể nằm ở caller thay vì trong bridge.

## 4. Kiểm tra bridge bằng `javap`

Biên dịch tệp ở phần 3, sau đó xem mã JVM của lớp lồng `TextHandler`:

```bash
javap -c -p 'BridgeTraceDemo$TextHandler'
```

Dùng dấu nháy đơn quanh tên có ký tự `$` trên Bash, zsh hoặc PowerShell để tránh shell coi đó là biến. Với Windows CMD, quy tắc trích dẫn tên lớp có thể khác. Các lệnh `javap` dưới đây đọc class file từ thư mục đầu ra của `javac`; nếu dịch sang thư mục khác, thêm `-classpath` phù hợp.

Phần kết quả đáng chú ý (lược bớt constructor và các phương thức không liên quan):

```text
public void handle(java.lang.String);
  Code:
     ...

public void handle(java.lang.Object);
  Code:
     0: aload_0
     1: aload_1
     2: checkcast     #23 // class java/lang/String
     5: invokevirtual #25 // Method handle:(Ljava/lang/String;)V
     8: return
```

Chỉ số constant pool `#23`, `#25` là giá trị của bản build minh họa; chúng có thể thay đổi. Điều cần quan sát là:

- Có thêm `handle(java.lang.Object)` dù mã nguồn chỉ khai báo `handle(String)`.
- Lệnh `checkcast java/lang/String` nằm trong `handle(Object)`.
- Sau cast, `invokevirtual` chuyển tiếp tới phương thức nhận `String`.
- Chính `checkcast` là nơi ném `ClassCastException` nếu đối số thực không phù hợp.

Xem cờ của phương thức:

```bash
javap -p -v 'BridgeTraceDemo$TextHandler'
```

Trong kết quả đã kiểm chứng có dạng:

```text
public void handle(java.lang.Object);
  descriptor: (Ljava/lang/Object;)V
  flags: (0x1041) ACC_PUBLIC, ACC_BRIDGE, ACC_SYNTHETIC
```

`ACC_BRIDGE` biểu thị phương thức cầu nối do compiler tạo để hỗ trợ ngữ nghĩa của Java; `ACC_SYNTHETIC` cho biết nó không tương ứng với một khai báo phương thức thông thường do người lập trình viết trong nguồn. `Method.isBridge()` và `Method.isSynthetic()` qua reflection xác nhận tương ứng.

Muốn kiểm tra việc cast ở phía caller trong tình huống `Box<String>`, hãy xem bytecode của lớp chính:

```bash
javap -c -p BridgeTraceDemo
```

Tìm đoạn gọi `Box.get` theo descriptor trả về `Object` và lệnh `checkcast java/lang/String` xuất hiện **sau lời gọi này trong `main`**. Thay vì đoán vị trí từ stack trace, đối chiếu vị trí lệnh `checkcast` và đường gọi đã thực thi.

### So sánh hai loại vị trí lỗi

| Tình huống | Có bridge ở đường lỗi? | Nơi chứa `checkcast String` | Top frame quan sát |
| --- | --- | --- | --- |
| `TextHandler` nhận `Integer` qua raw `Handler` | Có | Bridge `TextHandler.handle(Object)` | `TextHandler.handle` |
| `Box<String>` bị raw `Box` ghi `Integer` rồi đọc ra `String` | Không cần bridge | Mã gọi `Box.get()` trong `main` | `BridgeTraceDemo.main` |

Hai hàng cùng ném `ClassCastException`, nhưng muốn kết luận “lỗi nằm ở bridge”, cần bằng chứng ở bytecode/reflection, **không chỉ dựa vào tên phương thức trong trace**.

## 5. Đọc stack trace của bridge như thế nào?

Một stack trace đầy đủ có thể bắt đầu đại loại như sau trong tình huống đầu tiên:

```text
java.lang.ClassCastException: ...
    at BridgeTraceDemo$TextHandler.handle(BridgeTraceDemo.java:<line>)
    at BridgeTraceDemo.main(BridgeTraceDemo.java:<line>)
```

Đây là **sơ đồ**, không phải bản chép nguyên văn: dấu `...` và `<line>` thay cho thông tin phụ thuộc runtime. Không dùng số dòng hay toàn văn thông báo để xác định kiểu dữ liệu một cách máy móc.

Đọc theo thứ tự:

1. **Loại ngoại lệ**: `ClassCastException` là dấu hiệu một phép cast tham chiếu thất bại ở runtime.
2. **Top frame**: xác định phương thức chứa lệnh đang ném lỗi. Nếu phương thức đó trông như không có cast, nghi ngờ bridge hoặc cast compiler chèn.
3. **Frame ngay dưới**: xác định đường gọi dẫn đến điểm thất bại; ở ví dụ, `main` gửi một giá trị sai qua raw `Handler`.
4. **Kiểu runtime thực**: tìm nguồn của đối số. Với Java hiện đại, message của CCE thường có tên kiểu nguồn/đích; cách biểu diễn module/class loader có thể thay đổi theo phiên bản Java.
5. **Bytecode**: nếu nghi bridge, dùng `javap -p -v` để tìm `ACC_BRIDGE`, và `javap -c -p` để tìm `checkcast` hoặc lời gọi ủy quyền.
6. **Luồng dữ liệu**: lần ngược nơi raw type, cast unchecked, reflection hoặc thư viện cũ đã phá vỡ hợp đồng generic.

**Cẩn trọng:** stack trace Java thông thường không in descriptor như `(Ljava/lang/Object;)V`. Ngay cả khi có hai `handle` trong bytecode, chúng đều được hiển thị dưới tên `handle`. Không thể khẳng định frame là bridge chỉ vì nó có tên giống phương thức mã nguồn hoặc số dòng trùng với khai báo.

Một kỹ thuật hữu ích khi cần chứng minh bridge mà không phụ thuộc cách debugger đặt breakpoint:

```java
for (java.lang.reflect.Method method
        : TextHandler.class.getDeclaredMethods()) {
    if (method.isBridge()) {
        System.out.println(method);
    }
}
```

Đây là đoạn minh họa đặt **bên trong `BridgeTraceDemo`**, nơi `TextHandler` đã được định nghĩa. Reflection có thể hiển thị phương thức bridge `handle(Object)`. Khi viết công cụ phân tích reflection, cân nhắc lọc `isBridge()` cho mục đích hiển thị API người viết; khi phân tích **runtime dispatch/ABI**, cần giữ thông tin bridge để hiểu đầy đủ class file.

## 6. Vì sao warning unchecked liên quan trực tiếp đến lỗi?

Trong chương trình, hai dòng:

```java
Handler rawHandler = typedHandler;
rawHandler.handle(123);

Box rawBox = typedBox;
rawBox.set(123);
```

là nguyên nhân tạo đường truyền dữ liệu không an toàn. `rawHandler` và `rawBox` là **raw type**, tức thiếu tham số generic. Chúng làm mất kiểm tra đối số ở mức mà chương trình thông thường được compiler bảo đảm.

Để xem cảnh báo rõ hơn:

```bash
javac --release 17 -Xlint:rawtypes -Xlint:unchecked BridgeTraceDemo.java
```

Bạn sẽ gặp cảnh báo liên quan tới khai báo raw `Handler`/`Box` và các lời gọi unchecked `handle`/`set`. Cảnh báo này không phải “nhiễu” có thể bỏ qua vô điều kiện: nó chỉ ra chỗ hợp đồng generic không còn được xác minh đủ tại thời điểm biên dịch.

**Không có `rawHandler.handle(123)` tương đương khi giữ kiểu chính xác:**

```java
Handler<String> typedHandler = new TextHandler();
// typedHandler.handle(123); // Compiler từ chối.
typedHandler.handle("safe");  // Hợp lệ.
```

Điều tương tự áp dụng cho `Box<String>`: lời gọi `typedBox.set(123)` không biên dịch. Sự khác biệt là raw type đã cho phép điều bất hợp lệ đi qua và khiến lỗi chỉ xuất hiện muộn khi bridge hoặc caller buộc phải cast.

Ngoài raw type, cần rà soát các nguồn dữ liệu sai kiểu khác:

- Cast unchecked, nhất là `(List<String>) someObject` hoặc ép kiểu generic không thể được kiểm tra đầy đủ vì erasure.
- API phản chiếu (`Method.invoke`, field access) hoặc thư viện sinh bytecode không giữ đúng hợp đồng kiểu.
- Thư viện legacy trả về collection không tham số hóa.
- Các đường đi qua varargs generic hoặc cấu trúc có heap pollution, nếu có cảnh báo unchecked thực sự phù hợp.

Không nên quy lỗi cho bridge nếu không thấy raw type ở **chính frame gần lỗi**. Dữ liệu sai kiểu có thể được đưa vào một đối tượng từ sớm và bị phát hiện tại một lời gọi khác nhiều tầng sau đó.

## 7. Khi nào bridge không phải nơi phát sinh `ClassCastException`?

Bridge xuất hiện trong nhiều trường hợp ngoài việc chuyển `Object` thành kiểu cụ thể của tham số.

### 7.1. Covariant return

Ví dụ:

```java
class Animal {
    Animal copy() {
        return this;
    }
}

final class Dog extends Animal {
    @Override
    Dog copy() {
        return this;
    }
}
```

Trong trường hợp này, Java cho phép phương thức ở lớp con trả về `Dog` thay vì `Animal` (kiểu trả về hiệp biến). JVM descriptor có bao gồm kiểu trả về, vì vậy compiler có thể tạo một bridge mang descriptor trả về `Animal` và chuyển tiếp tới `Dog.copy()`.

Bridge kiểu này có thể **không cần `checkcast` đối số**. Chỉ thấy `ACC_BRIDGE` không đủ để kết luận nó là nơi xảy ra ClassCastException. Cần xem bytecode cụ thể.

### 7.2. Getter trả về kiểu generic

`Box<T>.get()` trả về `T`, nhưng erasure của T là `Object`. Với `Box<String>`, khi giá trị trả về được đưa vào một biến `String`, cast thường nằm tại vị trí caller như phần 3.2.

Với phương thức trả về `T` được override bởi lớp con, có thể xuất hiện **bridge mang descriptor khác** để đảm bảo override; lúc đó cần kiểm tra riêng body của bridge và chỗ dùng kết quả. Đừng áp dụng quy tắc “tất cả getter generic đều cast trong bridge”.

### 7.3. Cast được viết trực tiếp trong mã nguồn

Nếu lập trình viên viết:

```java
Object value = Integer.valueOf(12);
String text = (String) value; // Lỗi ngay tại phép cast do người viết.
```

đây là ví dụ minh họa một phép cast trực tiếp trong phương thức, không cần generics hay bridge. Lỗi do phép cast ở dòng mã nguồn tương ứng; `javap` cũng có `checkcast java/lang/String`, nhưng method chứa lệnh đó không phải bridge.

Vì vậy, ba vị trí cần phân biệt là **bridge**, **caller do compiler chèn cast**, và **cast tường minh của người lập trình**.

## 8. Bridge và stack trace khi debug thư viện/framework

Trong ứng dụng lớn, ngăn xếp có thể chứa proxy, interceptor, reflection hoặc các adapter gọi vào một interface generic. Khi nhìn thấy một lỗi ép kiểu, hãy tách ba trách nhiệm:

- **Nơi gửi dữ liệu:** component đã truyền đối số sai kiểu hoặc làm ô nhiễm trạng thái generic.
- **Nơi chuyển giao:** interface, proxy hay bridge thực hiện dispatch và có thể chứa cast.
- **Nơi phát hiện lỗi:** instruction `checkcast` cụ thể ném `ClassCastException`.

Ví dụ, một framework lưu handler qua raw hoặc không giữ metadata generic chính xác có thể khiến nó gửi đối tượng không phù hợp. Stack trace hiện ra ở bridge của implementation, nhưng sửa bằng cách xóa bridge hoặc sửa phương thức `handle(String)` sẽ không giải quyết nguyên nhân. Cần thay đổi nơi framework chọn handler, kiểm tra kiểu đối số hoặc khôi phục hợp đồng generic ở ranh giới.

Khi một stack trace có phương thức do compiler sinh, một số công cụ instrumentation/profiling có thể hiển thị thêm thông tin synthetic/bridge. Tuy vậy, cách hiển thị phụ thuộc công cụ. Đối với chẩn đoán có thể kiểm chứng, `javap` trên **đúng class file đang chạy** đáng tin hơn suy đoán từ giao diện IDE.

Nếu có nhiều phiên bản JAR trong classpath hoặc class loader khác nhau, hãy xác minh lớp thực sự được tải từ đâu trước khi phân tích bản .class trong source tree. Trace cũng có thể có các frame reflection/proxy, nên không nên cho rằng hai frame liền kề trong mọi ứng dụng luôn là caller và bridge.

## 9. Quy trình điều tra một lỗi ép kiểu nghi do bridge

Sử dụng các bước sau theo thứ tự:

1. **Giữ stack trace đầy đủ.** Lưu loại ngoại lệ, message, top frame và các frame ngay dưới; đừng chỉ trích dòng báo lỗi ngắn.
2. **Xác định kiểu nhận ở nguồn.** Tìm interface hoặc superclass generic mà lớp đang thực thi đã chuyên biệt hóa, ví dụ `Handler<String>`.
3. **So sánh với erasure.** Với `Handler<T>` không có bound, tham số T sau erasure là `Object`; implementation nhận `String` có khả năng cần bridge.
4. **Kiểm tra class file.** Chạy `javap -p -v` tìm phương thức `ACC_BRIDGE`; chạy `javap -c -p` tìm `checkcast` và lời gọi ủy quyền.
5. **Phân biệt vị trí phát hiện.** Nếu cast trong bridge, top frame thường là method của bridge. Nếu cast được chèn ở caller sau khi lấy kết quả từ generic API, top frame thường là caller.
6. **Lần ngược nguồn sai kiểu.** Tìm raw type, unchecked warning, cast không được kiểm tra, serialization, reflection hay adapter chuyển đổi dữ liệu.
7. **Chỉnh sửa ranh giới dữ liệu.** Tham số hóa API đúng kiểu, tránh bỏ generic, xác thực dữ liệu runtime nếu nguồn không đáng tin và không dùng cast để “dập” lỗi compile.
8. **Kiểm chứng lại.** Bật `-Xlint:rawtypes,unchecked`, viết ca kiểm thử đưa đúng/sai kiểu qua ranh giới liên quan; chỉ gọi là đã sửa khi đường truyền sai kiểu không còn.

Một ghi chú hữu ích khi báo lỗi: “Bridge là nơi exception được ném” **khác** với “bridge là nguyên nhân bug”. Phương thức do compiler sinh đang thực thi một kiểm tra kiểu cần thiết; nguồn vi phạm có thể nằm ở code gọi từ trước.

## 10. Các sai lầm thường gặp

| Nhận định sai | Vấn đề | Cách hiểu đúng |
| --- | --- | --- |
| “Lỗi tại `handle(String)` thì chắc chắn do code trong body `handle(String)`.” | Stack frame không ghi kiểu tham số; bridge `handle(Object)` có cùng tên. | Kiểm tra `ACC_BRIDGE` và `checkcast` bằng `javap`. |
| “Bridge tự tạo dữ liệu sai kiểu.” | Bridge thường chỉ kiểm tra/ủy quyền đối số đã nhận. | Truy nguồn raw/unchecked invocation hoặc dữ liệu sai được truyền vào. |
| “Bất kỳ `ClassCastException` nào của generics đều ở bridge.” | Caller cũng có thể chứa cast do compiler chèn. | Xem bytecode của cả method được gọi và caller. |
| “`isSynthetic()` đồng nghĩa với `isBridge()`.” | Synthetic có nhiều loại thành phần do compiler/tool tạo. | Dùng `isBridge()` để nhận diện bridge; xem cả flags khi cần. |
| “Có bridge thì chắc chắn có `checkcast`.” | Bridge cho covariant return hoặc trường hợp khác có thể chỉ delegate. | Đọc phần Code của bridge cụ thể. |
| “Bỏ `@Override`, thêm cast, hay bỏ qua warning sẽ sửa lỗi.” | Thay đổi bề mặt, mất kiểm tra kiểu hoặc che dấu triệu chứng. | Sửa hợp đồng generic và nguồn dữ liệu sai kiểu. |
| “Số dòng ở trace xác định duy nhất một method overload.” | Stack trace không mang descriptor và dòng bridge do compiler gán. | So class, method, descriptor và bytecode. |
| “Vì compile không lỗi nên không thể có CCE.” | Raw/unchecked là vùng compiler không chứng minh được an toàn. | Bật lint, kiểm tra chỗ phá generic và test runtime có chủ đích. |

## 11. Bài tập tự kiểm tra

**Bài 1.** Một lớp triển khai `Consumer<String>` có `accept(String)`. Một raw `Consumer` gọi `accept(Integer.valueOf(1))`. Nếu bridge `accept(Object)` chứa `checkcast String`, ngoại lệ xuất hiện ở đâu?

**Đáp án:** Trong bridge `accept(Object)` của lớp implementation, trước khi phần thân `accept(String)` chạy.

**Bài 2.** Tại sao stack trace chỉ hiện `SomeConsumer.accept` không đủ xác định có phải bridge?

**Đáp án:** Stack trace mặc định chỉ hiện tên lớp và tên phương thức, không in kiểu tham số hay cờ bridge. Phải kiểm tra bytecode/flags hoặc reflection.

**Bài 3.** Một `List<String>` thực tế chứa `Integer` do raw list thêm vào. Khi vòng lặp đọc `String s` rồi ném CCE, có chắc bridge đã ném lỗi?

**Đáp án:** Không. Compiler có thể chèn `checkcast String` ở đoạn lấy phần tử của caller. Cần kiểm tra bytecode tại frame tương ứng.

**Bài 4.** Trong `BridgeTraceDemo`, vì sao output có `accepted:safe` nhưng không có `accepted:123`?

**Đáp án:** Lần gọi an toàn qua `Handler<String>` đưa String vào thành công. Lần gọi raw đưa Integer tới bridge, cast thất bại trước khi gọi `handle(String)`.

**Bài 5.** `javap` cho thấy `ACC_BRIDGE` nhưng không có `checkcast`. Có thể kết luận bridge này gây CCE không?

**Đáp án:** Không. Cờ bridge xác nhận vai trò chuyển tiếp, không khẳng định nó chứa phép cast thất bại. Cần xem code và stack thực tế.

**Bài 6.** Cần sửa bug ở raw handler bằng cách nào?

**Đáp án:** Giữ `Handler<String>` xuyên suốt các nơi lưu trữ và gọi; loại bỏ raw invocation, không truyền Integer tới handler chỉ chấp nhận String. Nếu đầu vào là `Object` từ nguồn ngoài, xác thực/chuyển đổi nó trước khi gọi handler.

## 12. Ghi nhớ

Bridge method giúp ngữ nghĩa ghi đè ở mã nguồn Java tiếp tục đúng khi các chữ ký generic bị xóa ở bytecode. Nếu bridge chứa lệnh `checkcast`, nó có thể là frame đầu của `ClassCastException` dù tác giả không viết phép cast nào trong method gốc. Tuy nhiên, lỗi ép kiểu cũng có thể xảy ra tại caller, chẳng hạn khi một generic getter trả về `Object` sau erasure và giá trị được gán sang `String`.

Để chẩn đoán đúng, hãy kết hợp **stack trace → method descriptor → bytecode `checkcast` → đường truyền dữ liệu sai kiểu**. Dấu vết cho biết nơi lỗi bị phát hiện; nguyên nhân thường là đoạn mã vượt qua kiểm tra kiểu ở ranh giới generic, raw type hoặc unchecked operation.


</details>

- [Quay lại đầu trang](#back-to-top)
