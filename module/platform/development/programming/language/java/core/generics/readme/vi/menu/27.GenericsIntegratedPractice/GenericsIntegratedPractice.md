<a id="back-to-top"></a>

# Thực hành tổng hợp

## Menu
- [Xây dựng Box&lt;T&gt;, Pair&lt;K,V&gt; và giao diện lưu trữ generic](#generics-integrated-practice-001)
- [Viết hàm chọn phần tử lớn nhất với giới hạn Comparable](#generics-integrated-practice-002)
- [Thiết kế hàm sao chép đúng PECS](#generics-integrated-practice-003)
- [Sửa lỗi wildcard bằng phương thức bắt giữ](#generics-integrated-practice-004)
- [So sánh suy luận với diamond, var và đối số kiểu tường minh](#generics-integrated-practice-005)
- [Tái hiện raw type và heap pollution rồi sửa tại nguồn](#generics-integrated-practice-006)
- [Quan sát type erasure và bridge bằng javap](#generics-integrated-practice-007)
- [Xây dựng kho nhiều kiểu và type token cho cấu trúc lồng nhau](#generics-integrated-practice-008)
- [Lập bảng lựa chọn T, ?, extends, super và tự đánh giá tình huống](#generics-integrated-practice-009)

## <a id="generics-integrated-practice-001">Xây dựng Box&lt;T&gt;, Pair&lt;K,V&gt; và giao diện lưu trữ generic</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng `Box<T>`, `Pair<K,V>` và giao diện lưu trữ generic trong Java 17+

Bài thực hành này xây dựng một mô hình nhỏ nhưng hoàn chỉnh gồm **`Box<T>`**, **`Pair<K,V>`** và **giao diện `Storage<K,V>`** cùng một implementation lưu trong bộ nhớ. Mục tiêu không chỉ là “thêm dấu `<T>` để code biên dịch”, mà còn thiết kế **quan hệ kiểu chính xác, bất biến dữ liệu, hợp đồng thao tác, ranh giới trách nhiệm và kiểm thử** để có thể mở rộng thành API thực tế.

Sau bài thực hành, bạn có thể:

- Tạo một lớp/record generic mang **một** type parameter `T`, với static generic factory và generic instance method.
- Tạo `Pair<K,V>` gồm **hai** type parameters độc lập, dùng được cho cặp khóa–giá trị.
- Khai báo **generic interface `Storage<K,V>`**, nơi `K` là kiểu khóa và `V` là kiểu giá trị; triển khai nó bằng `LinkedHashMap<K,V>`.
- Chọn `Optional<V>` để biểu thị **không tìm thấy**, phân biệt với giá trị `null`.
- Giữ dữ liệu nội bộ `private`, không phát tán mutable backing map, và cung cấp danh sách snapshot không sửa được về **cấu trúc**.
- Áp dụng `K/V/T/R`, method type parameter, `? extends`/`? super`, type inference và cách thiết kế API không cần raw/unchecked casts.
- Viết chương trình Java 17+ chạy độc lập, có đầu ra kiểm chứng được; phân biệt rõ hợp đồng compile-time với những điều vẫn phải kiểm tra ở runtime.

## 1. Đề bài và sơ đồ thiết kế

Giả sử cần quản lý các giá trị có khóa; ví dụ danh sách người dùng theo mã số hoặc điểm số theo tên. Ta muốn một hệ thống có ba thành phần:

```text
Box<T>
  ├── value(): T
  ├── of(U): Box<U>                 (static generic factory)
  └── map(Function<T,R>): Box<R>   (chuyển kiểu giá trị)

Pair<K,V>
  ├── key(): K
  └── value(): V

Storage<K,V>                        (hợp đồng không gắn với công nghệ lưu)
  ├── put(K, V)
  ├── find(K): Optional<V>
  ├── remove(K): Optional<V>
  ├── containsKey(K): boolean
  ├── size(): int
  ├── snapshot(): List<Pair<K,V>>
  └── isEmpty(): boolean           (default method)

InMemoryStorage<K,V> implements Storage<K,V>
  └── private LinkedHashMap<K,V>   (chi tiết triển khai)
```

Một ứng dụng có thể chọn:

```java
Storage<Integer, Box<String>> users = new InMemoryStorage<>();
Storage<String, Integer> scores = new InMemoryStorage<>();
```

Hai biến dùng **cùng một interface** nhưng gắn kiểu khác nhau. Với biến `users`, compiler biết khóa là `Integer` và giá trị là `Box<String>`; với biến `scores`, khóa là `String` và giá trị là `Integer`.

**Không đánh đồng generic storage với database:** phiên bản này chỉ lưu tạm trong RAM, không tự lưu file/SQL, không bảo đảm giao dịch, không bảo đảm đa luồng và không làm deep copy các object được lưu. Những tính năng đó cần thiết kế/hợp đồng riêng.

## 2. Chọn type parameters có ý nghĩa

Bộ ba tên thường gặp:

| Tên | Ý nghĩa trong bài | Quan hệ kiểu |
| --- | --- | --- |
| `T` | Kiểu một giá trị trong `Box<T>` | `Box<String>` giữ `String`, `Box<Integer>` giữ `Integer` |
| `K` | Kiểu khóa trong `Pair<K,V>` và `Storage<K,V>` | Đầu vào cho `put/find/remove/containsKey` |
| `V` | Kiểu giá trị trong `Pair<K,V>` và `Storage<K,V>` | Đầu vào của `put`, kiểu phần tử trong `Optional<V>` |
| `U` | Kiểu riêng của static factory `Box.of` | Không nhầm với `T` của class |
| `R` | Kiểu kết quả mới khi `Box<T>.map` | Liên kết kết quả callback với `Box<R>` |

Tên `K` và `V` **không buộc hai loại runtime class khác nhau**. `Storage<String,String>` vẫn hợp lệ. Tương tự, `Box<Integer>` và `Box<String>` thường dùng cùng một runtime class `Box` do **type erasure**, dù compiler kiểm tra chúng như các kiểu tham số hóa khác nhau.

Một design tốt chọn type parameter để biểu diễn **quan hệ dữ liệu mà compiler có thể bảo vệ**. Nếu thay toàn bộ bằng `Object`, caller sẽ phải cast và dễ gặp `ClassCastException` muộn hơn.

## 3. Xây dựng `Box<T>`: một wrapper generic bất biến ở cấp container

Trong Java 17, `record` giúp biểu diễn một cấu trúc chỉ giữ một giá trị:

```java
import java.util.Objects;
import java.util.function.Function;

record Box<T>(T value) {
    Box {
        Objects.requireNonNull(value, "value");
    }

    static <U> Box<U> of(U value) {
        return new Box<>(value);
    }

    <R> Box<R> map(Function<? super T, ? extends R> mapper) {
        return Box.of(Objects.requireNonNull(mapper, "mapper")
                             .apply(value));
    }
}
```

Đây là **snippet độc lập về khai báo**; chương trình chạy đầy đủ nằm ở mục 10 với các khai báo lồng bên trong một class chứa `main`.

### Vì sao dùng `record Box<T>`?

Record tự cung cấp constructor theo component, accessor `value()`, `equals`, `hashCode`, `toString` theo component. Ta bổ sung compact canonical constructor để **cấm `null`** vì hợp đồng Box ở ví dụ là “luôn có một giá trị”.

```java
Box<String> name = Box.of("Java");
String text = name.value(); // "Java"
```

Khi dùng record, tham chiếu tới component field được cố định sau khi tạo (không có setter). Nhưng “record bất biến” ở đây có nghĩa **bất biến về các component references**: nếu `T` là một `ArrayList` mutable, nội dung list đó vẫn có thể thay đổi từ alias khác. Record không deep-copy object thành phần.

### Static factory có `<U>` riêng

```java
static <U> Box<U> of(U value)
```

`U` được khai báo bởi **chính method `of`**, không phải `T` của record `Box<T>`. Đây là cách đúng vì static method không thể tham chiếu trực tiếp type variable `T` của lớp trong static context.

Caller có thể dùng type inference:

```java
Box<String> words = Box.of("hello");
Box<Integer> count = Box.of(42);
```

Hoặc type witness tường minh khi cần:

```java
Box<String> words = Box.<String>of("hello");
```

Không cần và không nên dùng raw `Box`.

## 4. `Box<T>.map`: method generic có kiểu kết quả mới

Một wrapper thường hữu ích hơn nếu có thể biến đổi giá trị bên trong:

```java
<R> Box<R> map(Function<? super T, ? extends R> mapper)
```

Khi `T = String`, một callback `String::length` cho kết quả `Integer`, vì vậy:

```java
Box<String> text = Box.of("Binh");
Box<Integer> length = text.map(String::length);
System.out.println(length.value()); // 4
```

Cần phân biệt:

- `T` thuộc record `Box<T>`, tức kiểu input của callback tại instance hiện tại.
- `R` thuộc method `map`, tức kiểu output được method suy luận.
- `Function<? super T, ? extends R>` diễn đạt callback có thể **tiêu thụ một giá trị T** và **sản xuất một giá trị tương thích R**.

`map` **không sửa `Box<T>` ban đầu**; nó tạo một `Box<R>` mới. Với implementation đang dùng, mapper không được `null`, và kết quả mapper cũng không được `null` vì `Box.of` gọi constructor cấm null.

Nếu muốn hỗ trợ “có hoặc không có giá trị”, nên thiết kế một wrapper có semantics khác, hoặc dùng `Optional<T>`. Không nên âm thầm đổi Box hiện tại sang nullable rồi vẫn viết Javadoc “luôn có giá trị”.

### Đọc đúng `? super T` và `? extends R`

`? super T` là quan hệ **consumer** của callback: một hàm nhận `Object` cũng có thể nhận `String`. `? extends R` là quan hệ **producer**: một hàm trả `Integer` có thể được dùng trong ngữ cảnh cần `Number`.

Những wildcard này không ảnh hưởng đến runtime class của `Box`; chúng giúp compile-time API linh hoạt hơn mà không cần unchecked casts.

## 5. Xây dựng `Pair<K,V>`: hai thành phần độc lập

Một pair gắn khóa với giá trị:

```java
import java.util.Objects;

record Pair<K,V>(K key, V value) {
    Pair {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
    }
}
```

Caller:

```java
Pair<Integer, String> ticket = new Pair<>(7, "READY");
Integer id = ticket.key();
String status = ticket.value();
```

Dùng `K` cho key và `V` cho value giúp mã đọc được ngay, đồng thời giữ quan hệ kiểu:

```java
// KHÔNG BIÊN DỊCH:
// Pair<Integer, String> wrong = new Pair<>("seven", 7);
```

Record tự cung cấp `equals`, `hashCode`, `toString` theo các component. Ví dụ:

```text
Pair[key=7, value=READY]
```

**Khác biệt quan trọng:** `Pair<K,V>` là **data carrier**, không phải bản thân storage. Một Pair có thể giữ một cặp bất kỳ; nó không cài đặt việc tìm kiếm, cập nhật hay đảm bảo tính duy nhất của khóa. Những quy tắc này thuộc giao diện `Storage` và implementation.

### Tại sao constructor cấm null?

Bài thực hành chủ động chọn key/value **non-null**, vì `Storage.find` sẽ trả `Optional<V>`: null không được dùng như “một giá trị đã lưu nhưng null”. Điều này khiến việc phân biệt *absent* và *present* rõ ràng.

Đây là lựa chọn hợp đồng của API, **không phải Java Generics mặc định cấm null**. Một kiểu `Pair<String,Integer>` khác hoàn toàn có thể cho phép null nếu tác giả thiết kế điều đó.

## 6. Thiết kế interface `Storage<K,V>`: lập hợp đồng trước implementation

Định nghĩa:

```java
import java.util.List;
import java.util.Optional;

interface Storage<K,V> {
    void put(K key, V value);
    Optional<V> find(K key);
    Optional<V> remove(K key);
    boolean containsKey(K key);
    int size();
    List<Pair<K,V>> snapshot();

    default boolean isEmpty() {
        return size() == 0;
    }
}
```

Đây là generic interface với hai type parameters do **interface** khai báo. `Storage<Integer, Box<String>>` làm cho mọi method sử dụng `K`, `V` tương ứng tại compile time.

| Thành viên | Kiểu đầu vào/kết quả | Hợp đồng chọn cho bài |
| --- | --- | --- |
| `put(K,V)` | Nhận đúng K, V | Thêm hoặc ghi đè giá trị theo key; từ chối null |
| `find(K)` | `Optional<V>` | Có key: trả giá trị; không có: `Optional.empty()` |
| `remove(K)` | `Optional<V>` | Xóa key, trả giá trị cũ nếu có |
| `containsKey(K)` | `boolean` | Cho biết key đang được lưu |
| `size()` | `int` | Đếm số **key phân biệt** đang có |
| `snapshot()` | `List<Pair<K,V>>` | Trả ảnh chụp cấu trúc danh sách entry không cho sửa |
| `isEmpty()` | `boolean` | Default method dựa vào `size()==0` |

### Tại sao interface không tự chọn `HashMap`?

Một interface nên mô tả **khả năng mà client sử dụng**, còn chi tiết lưu trong RAM, database hay file thuộc implementation. Nếu code nghiệp vụ giữ biến:

```java
Storage<String, Integer> scores = new InMemoryStorage<>();
```

thì về sau có thể đổi sang implementation khác, miễn nó tuân thủ **cùng hợp đồng**. Tuy nhiên, generic type safety không tự bảo đảm hai implementation có cùng order, atomicity hay concurrency semantics; các yêu cầu đó cần ghi trong tài liệu công khai.

### Vì sao `Optional<V>`?

`Optional<V>` diễn đạt một kết quả **có thể vắng mặt** mà không phải dùng `null` làm tín hiệu mơ hồ:

```java
Optional<Integer> maybeScore = scores.find("An");
int score = maybeScore.orElse(0);
```

Để semantics này rõ, bài chọn **cấm lưu giá trị null**. Nếu thiết kế cho phép `null` như giá trị hợp lệ, `Optional.ofNullable(data.get(key))` không thể phân biệt “khóa tồn tại với null” và “khóa không tồn tại”. Khi đó phải thiết kế kiểu kết quả khác, hoặc thêm chính sách định nghĩa rõ.

## 7. Triển khai `InMemoryStorage<K,V>` bằng `LinkedHashMap`

Một implementation ban đầu:

```java
import java.util.*;

final class InMemoryStorage<K,V> implements Storage<K,V> {
    private final Map<K,V> data = new LinkedHashMap<>();

    @Override
    public void put(K key, V value) {
        data.put(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(value, "value"));
    }

    @Override
    public Optional<V> find(K key) {
        return Optional.ofNullable(
                data.get(Objects.requireNonNull(key, "key")));
    }

    @Override
    public Optional<V> remove(K key) {
        return Optional.ofNullable(
                data.remove(Objects.requireNonNull(key, "key")));
    }

    @Override
    public boolean containsKey(K key) {
        return data.containsKey(
                Objects.requireNonNull(key, "key"));
    }

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public List<Pair<K,V>> snapshot() {
        List<Pair<K,V>> entries = new ArrayList<>();

        for (Map.Entry<K,V> entry : data.entrySet()) {
            entries.add(
                    new Pair<>(entry.getKey(), entry.getValue()));
        }

        return List.copyOf(entries);
    }
}
```

**Cách hoạt động:**

- `Map<K,V>` giữ quan hệ kiểu giống interface; các `put` không cần cast.
- `LinkedHashMap` mặc định giữ **thứ tự chèn của các key** khi duyệt `entrySet()`. Cập nhật giá trị của key đã có không biến nó thành key mới trong insertion-order mode.
- `private final Map<K,V> data` không cho caller giữ trực tiếp một biến tới backing map qua public API. `final` không làm map bất biến; nó chỉ cố định **tham chiếu field** sau khi khởi tạo.
- `put` gọi `Objects.requireNonNull` cho cả key/value để thực thi chính sách non-null ở runtime.
- `Optional.ofNullable(data.get(key))` trả empty khi không thấy key; vì value null bị cấm, hành vi không bị nhập nhằng trong điều kiện invariant còn đúng.
- `remove` lấy giá trị cũ rồi xóa; nếu không thấy key thì empty.
- `snapshot()` duyệt entry hiện tại, xây **các `Pair<K,V>` mới**, rồi dùng `List.copyOf` để caller không `add/remove/set` trên danh sách kết quả.

### Invariant cần duy trì

Bất biến của implementation:

1. **Mọi key/value được lưu đều non-null**, do các đường ghi công khai được kiểm soát.
2. `data` không bị công khai dưới dạng mutable alias.
3. Mỗi key xuất hiện tối đa một lần, theo semantics của `Map`/key equality và hash.
4. `size()` bằng số entry hiện tại của map.
5. `find/remove` dùng một quy tắc “không tìm thấy” nhất quán.
6. `snapshot()` không phụ thuộc vào các thay đổi cấu trúc sau khi đã tạo.
7. Class này **chưa tuyên bố thread-safe**; thao tác đồng thời không được tự động bảo đảm.

Những invariant này thuộc logic chương trình bên cạnh kiểm tra generic type arguments ở compile time. Nếu code cố tình dùng **raw `Storage`**, unchecked casts hay reflection để phá chúng, một `Map<K,V>` bị ô nhiễm vẫn có thể gây lỗi muộn — Java Generics không ngăn mọi hành vi phá hệ thống kiểu.

## 8. Invariance: `Storage<Integer,String>` không phải `Storage<Number,Object>`

Java Generics **bất biến** (*invariant*) theo type arguments. Ví dụ:

```java
Storage<Integer, String> typed = new InMemoryStorage<>();

// KHÔNG BIÊN DỊCH:
// Storage<Number, Object> wider = typed;
```

Điều này không mâu thuẫn với `Integer extends Number` và `String extends Object`. Nếu được gán như vậy, caller có thể dùng `wider.put(3.14, new Object())` và làm hỏng lời hứa `Storage<Integer,String>`.

Tương tự:

```java
Storage<Integer, Box<String>> users = new InMemoryStorage<>();

// KHÔNG BIÊN DỊCH:
// users.put("101", Box.of("An")); // key phải là Integer
// users.put(101, Box.of(42));     // value phải là Box<String>
```

**Đó là lợi ích của generic API**: compiler loại nhiều thao tác sai trước khi chạy.

Đừng sửa bằng cast raw hoặc `(Storage)`: thao tác đó làm mất kiểm tra type arguments, dẫn tới heap pollution và lỗi xuất hiện xa vị trí vi phạm.

## 9. Helper method generic: làm việc với mọi storage có K và V tương ứng

Có thể viết một method dùng lại cho tất cả storage:

```java
static <K,V> void printAll(Storage<K,V> storage) {
    for (Pair<K,V> pair : storage.snapshot()) {
        System.out.println(pair.key() + " => " + pair.value());
    }
}
```

Khi gọi:

```java
Storage<String,Integer> scores = new InMemoryStorage<>();
scores.put("An", 9);
printAll(scores);
```

compiler suy luận `K=String` và `V=Integer`. Method generic này **không cần hiểu riêng** `Integer` hay `String`; nó chỉ dựa vào những thao tác mà interface bảo đảm.

Với `Box<String>` làm value, `pair.value()` có kiểu `Box<String>` ở compile time. In trực tiếp record sẽ dùng `toString()` theo record component, nhưng print format là **lựa chọn của helper**, không nằm trong hợp đồng lưu trữ.

### Khi nào dùng wildcard?

Nếu helper chỉ đọc entry để in mà không cần liên hệ giữa các vị trí tham số/return, ta cũng có thể viết một biến thể dùng wildcard:

```java
static void showUnknown(Storage<?,?> storage) {
    for (Pair<?,?> pair : storage.snapshot()) {
        System.out.println(pair.key() + " => " + pair.value());
    }
}
```

Ở đây ta chấp nhận một storage có kiểu khóa/giá trị chưa biết và chỉ **đọc**; việc gọi `storage.put` qua `Storage<?,?>` với key/value cụ thể không an toàn do compiler không biết các wildcard captures.

Nếu method cần nối kiểu của storage nguồn và đích, hãy dùng named type parameters và wildcard có chủ đích, thay vì `Object` ở mọi nơi.

## 10. Chương trình Java 17+ hoàn chỉnh

Lưu tệp **`GenericStorageDemo.java`**. Toàn bộ chương trình chỉ dùng Java standard library, **không cần dependency**:

```java
import java.util.*;
import java.util.function.Function;

public class GenericStorageDemo {
    record Box<T>(T value) {
        Box {
            Objects.requireNonNull(value, "value");
        }

        static <U> Box<U> of(U value) {
            return new Box<>(value);
        }

        <R> Box<R> map(Function<? super T, ? extends R> mapper) {
            return Box.of(
                    Objects.requireNonNull(mapper, "mapper")
                            .apply(value));
        }
    }

    record Pair<K,V>(K key, V value) {
        Pair {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
        }
    }

    interface Storage<K,V> {
        void put(K key, V value);
        Optional<V> find(K key);
        Optional<V> remove(K key);
        boolean containsKey(K key);
        int size();
        List<Pair<K,V>> snapshot();

        default boolean isEmpty() {
            return size() == 0;
        }
    }

    static final class InMemoryStorage<K,V>
            implements Storage<K,V> {
        private final Map<K,V> data = new LinkedHashMap<>();

        @Override
        public void put(K key, V value) {
            data.put(
                    Objects.requireNonNull(key, "key"),
                    Objects.requireNonNull(value, "value"));
        }

        @Override
        public Optional<V> find(K key) {
            return Optional.ofNullable(
                    data.get(Objects.requireNonNull(key, "key")));
        }

        @Override
        public Optional<V> remove(K key) {
            return Optional.ofNullable(
                    data.remove(Objects.requireNonNull(key, "key")));
        }

        @Override
        public boolean containsKey(K key) {
            return data.containsKey(
                    Objects.requireNonNull(key, "key"));
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public List<Pair<K,V>> snapshot() {
            List<Pair<K,V>> result = new ArrayList<>();

            for (Map.Entry<K,V> entry : data.entrySet()) {
                result.add(
                        new Pair<>(entry.getKey(), entry.getValue()));
            }

            return List.copyOf(result);
        }
    }

    static <K,V> void printAll(Storage<K,V> storage) {
        for (Pair<K,V> pair : storage.snapshot()) {
            System.out.println(pair.key()
                    + " => " + pair.value());
        }
    }

    static void expectFailure(String title, Runnable operation) {
        try {
            operation.run();
            System.out.println(title + ": unexpected success");
        } catch (NullPointerException
                 | UnsupportedOperationException ex) {
            System.out.println(title + ": "
                    + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        Storage<Integer, Box<String>> users = new InMemoryStorage<>();

        System.out.println("Initially empty: " + users.isEmpty());

        users.put(101, Box.of("An"));
        users.put(102, Box.of("Binh"));

        System.out.println("User 101: "
                + users.find(101)
                       .map(Box::value)
                       .orElse("missing"));

        System.out.println("User 999: "
                + users.find(999)
                       .map(Box::value)
                       .orElse("missing"));

        System.out.println("Mapped length: "
                + users.find(102).orElseThrow()
                       .map(String::length).value());

        Pair<Integer,String> ticket = new Pair<>(7, "READY");
        System.out.println("Pair: " + ticket);

        List<Pair<Integer, Box<String>>> before = users.snapshot();

        users.put(103, Box.of("Chi"));

        System.out.println("Snapshot count: " + before.size());
        System.out.println("Storage count: " + users.size());

        System.out.println("Remove 101: "
                + users.remove(101)
                       .map(Box::value).orElse("missing"));

        System.out.println("Contains 101: " + users.containsKey(101));

        System.out.println("Remaining entries:");
        printAll(users);

        expectFailure("Null value",
                () -> users.put(999, null));
        expectFailure("Null key",
                () -> users.find(null));
        expectFailure("Snapshot mutation",
                () -> before.add(new Pair<>(9, Box.of("X"))));

        Storage<String,Integer> scores = new InMemoryStorage<>();
        scores.put("An", 9);
        System.out.println("Score: "
                + scores.find("An").orElseThrow());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 GenericStorageDemo.java
java GenericStorageDemo
```

**Standard output**:

```text
Initially empty: true
User 101: An
User 999: missing
Mapped length: 4
Pair: Pair[key=7, value=READY]
Snapshot count: 2
Storage count: 3
Remove 101: An
Contains 101: false
Remaining entries:
102 => Box[value=Binh]
103 => Box[value=Chi]
Null value: NullPointerException
Null key: NullPointerException
Snapshot mutation: UnsupportedOperationException
Score: 9
```

Các dòng cần hiểu rõ:

1. Trước khi `put`, `size()==0`, nên default `isEmpty()` trả `true`.
2. `find(101)` tìm thấy `Box<String>`; `Optional.map(Box::value)` lấy chuỗi `"An"`.
3. `find(999)` trả `Optional.empty()`, nên `orElse("missing")`.
4. `Box<String>.map(String::length)` trả `Box<Integer>` có giá trị 4, không sửa Box ban đầu.
5. Pair record có `toString()` được sinh từ hai component.
6. `before` chứa hai entry ở thời điểm `snapshot()`; thêm key 103 sau đó làm `size()` của storage thành 3, nhưng `before.size()` vẫn là 2.
7. `remove(101)` trả giá trị cũ; `containsKey(101)` sau đó là `false`.
8. `LinkedHashMap` duy trì insertion order cho các key còn lại: 102 rồi 103.
9. Hai thao tác null bị chặn bằng `Objects.requireNonNull`; sửa danh sách snapshot gây `UnsupportedOperationException`.
10. Cùng implementation `InMemoryStorage<K,V>` dùng được cho `Storage<String,Integer>` mà không cast.

Hàm `expectFailure` chỉ để demo các exception đã dự kiến; trong bài kiểm thử chuyên nghiệp nên dùng assertion chính xác rằng **đúng exception** được ném và thất bại nếu thao tác **không ném exception**, thay vì chỉ in `unexpected success`.

## 11. Quy tắc null, empty và lỗi: ghi rõ trong tài liệu

Không có quy tắc mặc định của Java Generics rằng `T`, `K` hay `V` phải non-null. Đó là **hợp đồng được chọn** cho các class trong bài.

Với `Box<T>`:

- `new Box<>(null)` hoặc `Box.of(null)` bị constructor từ chối bằng `NullPointerException`.
- `box.map(null)` cũng bị từ chối.
- `box.map(...)` nếu mapper trả null, `Box.of` từ chối kết quả null.
- `Box<T>` không có khái niệm empty; dùng `Optional<T>` nếu cần biểu thị “không có giá trị”.

Với `Pair<K,V>`:

- Key và value đều phải non-null.
- Các component có thể **cùng runtime class**; không bắt buộc khác nhau.
- Pair không tự thực thi quy tắc unique key, sorting hay persistence.

Với `Storage<K,V>`:

- `put` nhận key và value non-null; nếu key đã tồn tại thì cập nhật giá trị.
- `find`/ `remove` trả `Optional.empty()` nếu không tồn tại.
- `find(null)`, `remove(null)`, `containsKey(null)`, `put(null,...) ` đều không hợp lệ theo implementation.
- `snapshot()` trả các cặp đang có tại thời điểm tạo danh sách mới; client không thêm/xóa phần tử vào kết quả được.
- Storage không bảo đảm deep clone value object và không tự thread-safe.

**Nên ghi hợp đồng bằng Javadoc**, không để người dùng phải suy luận từ `Optional` hay từ tên `InMemoryStorage`.

## 12. Javadoc cho một interface generic: tài liệu hóa cả kiểu và hành vi

Một interface public có thể khai báo:

```java
/**
 * Stores non-null values under non-null keys.
 *
 * @param <K> key type, using its equals/hashCode semantics
 * @param <V> stored value type
 */
interface Storage<K,V> {
    /**
     * Inserts or replaces a value associated with a key.
     *
     * @param key non-null key
     * @param value non-null value
     * @throws NullPointerException if key or value is null
     */
    void put(K key, V value);

    /**
     * Looks up a value without removing it.
     *
     * @param key non-null key
     * @return present value, or empty if the key is absent
     * @throws NullPointerException if key is null
     */
    Optional<V> find(K key);

    // Other operations follow the same contract.
}
```

Đây là **đoạn khai báo độc lập minh họa Javadoc**; chương trình runnable ở mục 10 có phiên bản đầy đủ của interface. Với public API, bổ sung Javadoc cho `remove`, `size`, `containsKey`, `snapshot`, thứ tự và concurrency policy.

Lưu ý cú pháp:

- `@param <K>`, `@param <V>` mô tả **type parameters** của interface.
- `@param key`, `@param value` mô tả **tham số giá trị** của method.
- `@return` giải thích ý nghĩa empty/present.
- `@throws` nói về trường hợp lỗi theo hợp đồng.

Tài liệu không thể thay thế việc **thực thi** hợp đồng. Nếu Javadoc nói cấm null, implementation phải kiểm tra hoặc dùng cấu trúc dữ liệu áp đặt cùng chính sách một cách nhất quán.

## 13. Tại sao dùng `Map<K,V>` thay `Object[]` ở implementation đầu tiên?

Một cài đặt `Object[]` hoặc raw `Map` có thể cần unchecked casts và chứng minh bất biến phức tạp. Trong bài này, `LinkedHashMap<K,V>` đã có typed API đủ dùng:

```java
private final Map<K,V> data = new LinkedHashMap<>();
```

Compiler bảo vệ khi gọi:

```java
data.put(key, value);
V stored = data.get(key);
```

Không cần `@SuppressWarnings("unchecked")`, không cần tự tạo generic array và không cần `(V)` ở getter.

**Quy tắc thiết kế:** chọn collection generic chuẩn nếu nó diễn đạt đúng dữ liệu và không gây hạn chế nghiệp vụ không cần thiết. Chỉ tự viết low-level storage trên `Object[]` khi thật sự có lý do về bộ nhớ, thuật toán hoặc layout, và lúc đó phải duy trì invariant của mọi đường ghi/đọc cùng phạm vi cast thật hẹp.

Type erasure vẫn tồn tại: nếu caller cố tình dùng `Storage raw` hoặc reflection, họ có thể bypass một phần compile-time guarantees. Đó không phải lý do để bỏ generics trong API bình thường.

## 14. `Pair<K,V>` với `Map.Entry<K,V>`: nên chọn gì?

Java đã có `Map.Entry<K,V>` và `Map.entry(key,value)` để biểu diễn các entry đơn giản. Vậy có cần `Pair<K,V>` riêng?

**Dùng `Pair<K,V>` hợp lý khi:**

- Bài toán cần một data carrier **độc lập với khái niệm Map**.
- Bạn muốn đặt tên component rõ `key`/`value` hoặc `first`/`second`.
- Muốn một record bất biến về các component references, `equals/hashCode/toString` theo record.
- Muốn mở rộng domain behavior phù hợp và tài liệu hóa điều kiện key/value.

**Dùng `Map.Entry<K,V>` hợp lý khi:**

- Đang làm việc trực tiếp với API của `Map`.
- Không cần một abstraction Pair độc lập.
- Có thể dùng `Map.entry(k,v)` cho một cặp có hợp đồng phù hợp (factory này không nhận null).

**Không được đánh đồng `Map.Entry` lấy từ `map.entrySet()` với một immutable snapshot.** Entry từ view của map có thể phản ánh live data và các điều kiện về `setValue`/mutation phụ thuộc implementation; nếu trả chúng ra ngoài, cần đọc hợp đồng cụ thể. Trong ví dụ, ta tạo các `Pair` mới rồi `List.copyOf` để tránh đưa trực tiếp một live `entrySet()` cho caller.

## 15. Snapshot chỉ bất biến về cấu trúc; object graph có thể mutable

Trả `List.copyOf(entries)` bảo vệ danh sách output khỏi `add`, `remove`, `set`; danh sách cũng không tự thay đổi khi storage thêm/xóa entry sau đó.

Nhưng xét một value mutable:

```java
Storage<Integer, List<String>> store = new InMemoryStorage<>();
List<String> original = new ArrayList<>(List.of("a"));
store.put(1, original);

List<Pair<Integer,List<String>>> snapshot = store.snapshot();
original.add("b");

System.out.println(snapshot.get(0).value()); // [a, b]
```

Code trên cho thấy **snapshot cấu trúc không phải deep copy**. Record `Pair` giữ tham chiếu tới cùng `List<String>`; nội dung list ấy vẫn thay đổi qua `original`.

Nếu mục tiêu của ứng dụng đòi một snapshot sâu:

- Có thể yêu cầu `V` là kiểu immutable theo hợp đồng (nhưng Java generic bound không thể diễn tả “mọi đối tượng của V đều immutable” theo cách tổng quát).
- Có thể nhận copier `Function<? super V,? extends V>` hoặc serializer để tạo bản sao thích hợp, tùy domain.
- Có thể chuyển dữ liệu vào immutable domain records/DTO đã kiểm soát các trường mutable.
- Phải xử lý cả concurrency và tính nhất quán khi đọc nguồn.

Một API không nên hứa “fully immutable snapshot” chỉ vì nó trả `List<Pair<K,V>>` được tạo bằng `List.copyOf`.

## 16. PECS khi muốn sao chép giữa hai storage tương thích

Trong ví dụ chính, `Storage<K,V>` giữ quan hệ kiểu chặt. Với thao tác **chỉ đọc từ nguồn và ghi vào đích**, có thể dùng variance tại vị trí sử dụng:

```java
static <K,V> int copyEntries(
        Storage<K, ? extends V> source,
        Storage<K, ? super V> destination) {
    int count = 0;

    for (Pair<K, ? extends V> pair : source.snapshot()) {
        destination.put(pair.key(), pair.value());
        count++;
    }

    return count;
}
```

Đây là **snippet cho method helper** với `Storage` và `Pair` đã định nghĩa. Quan hệ theo PECS:

- `source` sản xuất giá trị đọc như `V` hoặc subtype của `V`, nên dùng `? extends V`.
- `destination` tiêu thụ giá trị `V`, nên dùng `? super V`.
- `K` được giữ như cùng một type parameter để key từ source có thể truyền sang destination đúng kiểu.

Ví dụ hợp lý:

```java
Storage<String,Integer> small = new InMemoryStorage<>();
Storage<String,Number> wide = new InMemoryStorage<>();
small.put("a", 3);

int copied = copyEntries(small, wide);
```

Lời gọi hợp lệ vì `Integer` là subtype của `Number`. Nhưng **`Storage<String,Integer>` vẫn không phải subtype trực tiếp của `Storage<String,Number>`**. Chính wildcard trong **chữ ký helper** cho phép những đối số cùng tham gia một operation.

Một chú ý nghiệp vụ: `copyEntries` duyệt snapshot nguồn và ghi đè nếu key đã có ở đích. Giá trị trả về `count` ở đây là số entry đã xử lý thành công, **không phải số key mới thực sự thêm**. Nếu destination có cùng key hoặc đang dùng cùng instance với source, cần làm rõ semantics/side effects.

## 17. So sánh `Storage<K,V>` với `Storage<K,Box<V>>`

Bạn có hai kiểu thiết kế khả thi:

```java
Storage<Integer,String> direct = new InMemoryStorage<>();
Storage<Integer,Box<String>> wrapped = new InMemoryStorage<>();
```

Trong `direct`, giá trị là chuỗi. Trong `wrapped`, giá trị là **một wrapper có khả năng map và có invariant non-null**.

Không có lựa chọn nào luôn đúng:

- **Chọn `Storage<K,V>` trực tiếp** nếu abstraction Box không thêm ý nghĩa domain, chỉ khiến caller viết thêm `.value()`.
- **Chọn `Storage<K,Box<V>>`** nếu domain thực sự cần một lớp wrapper: provenance, validation, trạng thái đã chuẩn hóa, hoặc các phép biến đổi có hợp đồng riêng.
- Với `Optional<V>`, không nên tự động tạo `Box<Optional<V>>` trừ khi các tầng wrapper có ý nghĩa khác nhau rõ ràng; quá nhiều wrapper làm API khó hiểu.

Bài thực hành dùng `Storage<Integer,Box<String>>` để minh họa composition của generic types; trong code production, cần đánh giá xem Box có tạo giá trị thiết kế thực sự hay không.

## 18. Kiểm thử các trường hợp biên

Bên cạnh chương trình chạy, một bộ kiểm thử chuyên nghiệp nên kiểm tra:

| Tình huống | Kết quả mong đợi theo hợp đồng |
| --- | --- |
| `new InMemoryStorage<>()` | `isEmpty()==true`, `size()==0` |
| `put(k,v)` lần đầu | `size` tăng một, `find(k)` trả `v` |
| `put(k,v2)` với key đã có | `size` không tăng, `find(k)` trả `v2` |
| `find(missing)` | `Optional.empty()` |
| `remove(existing)` | Trả giá trị cũ, key biến mất |
| `remove(missing)` | `Optional.empty()`, không đổi size |
| `containsKey(existing)` | `true` |
| `null` key ở put/find/remove/containsKey | `NullPointerException` |
| `null` value ở put | `NullPointerException` |
| `Box.of(null)`, `new Pair<>(null,v)` | `NullPointerException` |
| `Box.map` với mapper phù hợp | Tạo `Box<R>` mới, không sửa Box cũ |
| `Box.map` với mapper `null`/trả `null` | `NullPointerException` |
| Snapshot trước/sau mutation | Snapshot trước giữ số entry và key/value references tại thời điểm tạo |
| `snapshot().add(...)` | `UnsupportedOperationException` |
| Giá trị mutable bị sửa qua alias | Snapshot có thể thấy trạng thái mới của chính value object |
| `Storage<K,V>` sai K hoặc V | Lời gọi typed bị compiler từ chối |
| Hai type arguments `K`, `V` giống nhau | Vẫn hợp lệ |
| Thao tác trên nhiều thread | Không có bảo đảm thread-safety từ implementation mẫu |

Đối với JUnit 5, bạn có thể dùng `assertThrows(NullPointerException.class, () -> storage.put(1,null))` và `assertEquals(...)`; nhưng JUnit là dependency ngoài JDK, không cần để chạy chương trình ở mục 10.

### Kiểm thử kiểu compile-time

Một fixture cố ý sai có thể chứa:

```java
// KHÔNG BIÊN DỊCH, chỉ để kiểm chứng compiler:
Storage<Integer,String> text = new InMemoryStorage<>();
text.put("not an Integer", "A");
text.put(1, 123);
```

Nếu API của bạn vô tình chuyển `put(K,V)` thành `put(Object,Object)`, những lời gọi này có thể không còn bị compiler chặn — dấu hiệu **hợp đồng kiểu đã yếu đi** dù functional tests với dữ liệu đúng vẫn pass.

## 19. Sai lầm thường gặp khi tự xây generic API

**Sai lầm 1 — Dùng raw `Storage`:**

```java
Storage raw = new InMemoryStorage();
raw.put("key", 3);
```

Code có thể biên dịch với raw/unchecked warnings nhưng mất quan hệ kiểu K/V. Dùng `Storage<String,Integer>` nếu đó là contract bạn muốn.

**Sai lầm 2 — Tưởng `Pair<K,V>` đòi K khác V:** không. `Pair<String,String>` hoàn toàn hợp lệ.

**Sai lầm 3 — Tưởng `Box<T>` có thể khai báo `static T cache`:** không. Static context không được dùng type variable của class theo cách đó; static factory phải có type parameter riêng, như `static <U> Box<U> of(U)`.

**Sai lầm 4 — Tạo `new T[n]` để lưu generic:** không hợp lệ với type variable non-reifiable. Dùng các collection generic chuẩn khi đủ nhu cầu.

**Sai lầm 5 — Nhầm `Storage<Integer,Box<String>>` với `Storage<Integer,String>`:** value types khác nhau; không thể tự động ép Box ra String ở compile time. Muốn chuyển dữ liệu, viết một phép mapping rõ ràng.

**Sai lầm 6 — Gọi `find` và giả định luôn có value:** `Optional<V>` có thể empty. Dùng `orElse`, `ifPresent`, `map` hoặc `orElseThrow` với chính sách lỗi rõ.

**Sai lầm 7 — Dùng null làm tín hiệu missing trong implementation vốn cho phép null value:** làm `Optional.ofNullable(data.get(k))` không phân biệt missing/present-null. Chọn invariant non-null hoặc kiểu kết quả khác.

**Sai lầm 8 — Công khai backing map:** caller có thể sửa state ngoài các method bảo vệ invariant. Trong bài, map là `private`, snapshot được xây riêng.

**Sai lầm 9 — Tưởng `final Map<K,V>` nghĩa map immutable:** `final` chỉ áp dụng tới tham chiếu field; nội dung map vẫn mutable.

**Sai lầm 10 — Tưởng `List.copyOf` deep-copy mọi element:** không. Các object được Pair/Box tham chiếu vẫn có thể mutable.

**Sai lầm 11 — Tưởng interface luôn quy định thứ tự:** thứ tự insertion là lựa chọn của `LinkedHashMap` ở implementation này. Nếu tất cả implementations phải bảo đảm cùng thứ tự, phải đưa vào **hợp đồng interface** và test.

**Sai lầm 12 — Tưởng thread-safe vì có generics:** generic typing không bảo đảm đồng bộ/visibility. Chọn concurrent implementation hoặc quản lý synchronization nếu cần.

**Sai lầm 13 — Dùng unchecked cast để nối `Storage<K,V>` với `Storage<K,? super V>`:** thường có thể viết helper PECS typed; cast không phải lối tắt an toàn.

**Sai lầm 14 — Không nêu semantics khi `put` ghi đè:** với Map, cùng key thay giá trị cũ; người dùng API cần biết đó là update hay lỗi trùng khóa.

**Sai lầm 15 — Làm Box mutable khi không cần:** nếu chỉ cần một giá trị chuyển qua các bước, Box record immutable về component reference thường dễ kiểm soát hơn một class có `set(T)`.

## 20. Bài tập mở rộng có hướng dẫn

### Bài A — Thêm `putIfAbsent`

Mở rộng `Storage<K,V>`:

```java
Optional<V> putIfAbsent(K key, V value);
```

Chọn và tài liệu hóa hợp đồng: nếu key chưa tồn tại thì thêm và trả empty; nếu tồn tại thì giữ nguyên và trả value cũ. Dùng API `Map.putIfAbsent` sau khi kiểm tra null, đồng thời xác nhận implementation không cho lưu null. Kiểm thử size không đổi khi key đã tồn tại.

### Bài B — Tạo `Storage<K,V>` dạng đọc

Nếu nhiều caller chỉ đọc, tách interface:

```java
interface ReadOnlyStorage<K,V> {
    Optional<V> find(K key);
    boolean containsKey(K key);
    int size();
    List<Pair<K,V>> snapshot();
}
```

Sau đó cho `Storage<K,V>` mở rộng `ReadOnlyStorage<K,V>`. Phương pháp *interface segregation* giúp ngăn code chỉ cần đọc phụ thuộc vào `put/remove`.

**Lưu ý:** interface read-only không tự bảo đảm backing storage immutable — mã giữ writable alias vẫn có thể cập nhật dữ liệu. Đây là phân quyền ở **mặt API**, không nhất thiết là snapshot bất biến.

### Bài C — Thêm `mapValues`

Thiết kế một helper:

```java
static <K,V,R> Storage<K,R> mapValues(
        Storage<K,V> source,
        Function<? super V, ? extends R> mapper)
```

Phương thức nên tạo **storage mới**, giữ key `K`, chuyển value `V` thành `R`. Kiểm tra mapper khác null, kết quả mapper non-null theo contract của `Storage`, và xác định thứ tự/side effects khi mapper ném exception.

### Bài D — Kiểm tra behavior của snapshot

Tạo snapshot khi có hai entry, thêm một entry mới vào storage, xác minh snapshot trước vẫn chứa hai entry. Sau đó thử value là mutable list để nhận thấy snapshot không deep-copy. Tài liệu hóa hai tầng **bất biến cấu trúc** và **bất biến trạng thái object bên trong**.

### Bài E — Đổi implementation sang persistence

Thử định nghĩa `FileStorage<K,V>` hoặc `DatabaseStorage<K,V>` vẫn implements `Storage<K,V>`. Đừng giả định method `put` luôn rẻ, nguyên tử hoặc không lỗi I/O. Cần bổ sung exception strategy, serialization/codec cho K/V, transactions và concurrency policy trong hợp đồng.

## 21. Checklist đánh giá bài thực hành

Trước khi dùng bộ class này trong một module lớn hơn, hãy tự kiểm tra:

1. **Cả ba abstraction có đúng phạm vi?** Box giữ một giá trị; Pair mang hai component; Storage định nghĩa hành vi lưu.
2. **Tên type parameter nhất quán?** `T`, `K`, `V`, `R`, `U` có đúng vai trò không?
3. **Static generic factory được khai báo đúng?** `static <U> Box<U> of(U)`, không dùng `T` của class trong static context.
4. **API typed không có raw casts không cần thiết?** `Map<K,V>`, `Pair<K,V>`, `List<Pair<K,V>>` rõ ràng.
5. **Null policy rõ chưa?** Không lẫn missing với present-null.
6. **Update/remove semantics rõ chưa?** Cùng key ghi đè; remove trả giá trị trước khi xóa.
7. **Snapshot là copy hay view?** Trong bài là copy cấu trúc, không deep copy.
8. **Thứ tự entry có được yêu cầu không?** Nếu cần như một bảo đảm public API, hãy ghi vào interface contract.
9. **Tính đa hình đã được thử chưa?** Cùng interface dùng `Integer/Box<String>` và `String/Integer`.
10. **Có unit tests cho các trường hợp biên không?** Empty, duplicate key, null, wrong generic types, snapshot mutation, nullable mapper result.
11. **Có cần concurrency/persistence không?** Nếu có, thiết kế thêm ngoài generic interface mẫu.
12. **Có tách domain policy khỏi storage logic không?** Đừng ép một policy về người dùng vào `Storage<K,V>` nếu nó chỉ đúng cho một business entity.

## 22. Tổng kết

`Box<T>`, `Pair<K,V>` và `Storage<K,V>` minh họa ba cấp độ dùng Java Generics: **đóng gói một kiểu**, **liên hệ hai kiểu**, và **xây dựng một hợp đồng hành vi độc lập với implementation**. `Box<T>` cho thấy static generic factory và generic `map`; `Pair<K,V>` cho thấy hai component có loại khác nhau; `Storage<K,V>` dùng cùng type parameters xuyên suốt `put`, `find`, `remove`, `snapshot`, còn `InMemoryStorage` hiện thực thông qua `LinkedHashMap<K,V>` không cần unchecked casts.

Điểm cần nhớ không chỉ là syntax `<T>`, `<K,V>`: một API generic tốt phải **giữ mối quan hệ kiểu đúng ở compile time**, **nói rõ điều kiện runtime** (null, missing, update, order, mutation, ownership), **che giấu backing data** và **kiểm thử các ranh giới**. Khi những điều đó nhất quán, các abstraction có thể được dùng lại và mở rộng mà không buộc caller phụ thuộc vào raw types hoặc cast không kiểm tra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-integrated-practice-002">Viết hàm chọn phần tử lớn nhất với giới hạn Comparable</a>

<details>
<summary>Xem chi tiết</summary>

# Viết hàm chọn phần tử lớn nhất với giới hạn Comparable trong Java Generics

## 1. Mục tiêu và bài toán

Giả sử bạn cần chọn phần tử lớn nhất trong một nhóm dữ liệu:

- Số nguyên: từ `[3, 9, 7]` chọn `9`.
- Chuỗi: từ `["An", "Mai", "Binh"]` chọn `"Mai"` theo thứ tự tự nhiên của String.
- Ngày: chọn ngày muộn nhất trong nhiều `LocalDate`.
- Đối tượng nghiệp vụ: chọn `Ticket` có độ ưu tiên cao nhất, nếu class định nghĩa cách so sánh tự nhiên.

Viết riêng `maxInteger`, `maxString` và `maxDate` gây lặp thuật toán. Generics cho phép viết **một hàm chọn lớn nhất**, đồng thời yêu cầu compiler xác minh rằng các phần tử có thể so sánh theo cùng một quy tắc.

Ý tưởng cốt lõi:

```java
public static <T extends Comparable<? super T>> T max(
        Iterable<? extends T> values) {
    // Duyệt và giữ giá trị lớn nhất đã gặp.
}
```

Đây chưa phải thân hàm hoàn chỉnh; chương trình Java 17 có thể chạy ở phần 10 chứa implementation đầy đủ, xử lý cả trường hợp đầu vào rỗng và `null`.

Sau bài, bạn sẽ hiểu:

1. `Comparable<T>` là gì và `compareTo` có ý nghĩa ra sao.
2. Tại sao `<T extends Comparable<? super T>>` cần thiết cho một hàm max generic.
3. Khác biệt giữa `Comparable<T>`, `Comparable<? super T>` và `Comparator<? super T>`.
4. Vì sao cần kiểm tra input rỗng/null, dùng `compareTo > 0` và giữ phần tử thắng khi hòa.
5. Cách chọn kiểu nguồn đầu vào: `List<T>`, `Collection<? extends T>` hoặc `Iterable<? extends T>`.
6. Cách diễn giải lỗi type inference/bounds khi gọi hàm với kiểu không so sánh được hoặc dữ liệu trộn loại.
7. Cách kiểm thử, phân tích độ phức tạp và đối chiếu với `Collections.max`/`Stream.max`.

## 2. Tư duy thuật toán trước khi thêm generic

Một giải pháp đơn giản cho danh sách số nguyên:

```java
static Integer maxInteger(List<Integer> values) {
    if (values.isEmpty()) {
        throw new IllegalArgumentException("empty input");
    }
    Integer best = values.get(0);
    for (Integer value : values) {
        if (value.compareTo(best) > 0) {
            best = value;
        }
    }
    return best;
}
```

Hàm mẫu trên cần import `java.util.List`, và giả định phần tử không null. Một điểm có thể cải thiện: vì vòng lặp bắt đầu từ đầu list, giá trị đầu được so với chính nó ở lượt đầu. Thao tác này không làm sai kết quả nếu quy tắc so sánh bình thường, nhưng không cần thiết. Trong implementation hoàn chỉnh, ta lấy phần tử đầu qua iterator rồi duyệt **các phần tử còn lại**.

Thuật toán:

1. Nếu không có phần tử, không thể xác định max: xử lý trường hợp rỗng theo contract.
2. Dùng phần tử đầu làm `best`.
3. Với mỗi phần tử tiếp theo `candidate`, so `candidate.compareTo(best)`.
4. Nếu kết quả lớn hơn 0, cập nhật `best = candidate`.
5. Kết thúc, trả `best`.

**Bất biến vòng lặp:** sau khi xử lý k phần tử đầu, `best` là một phần tử lớn nhất trong k phần tử ấy theo thứ tự đã được định nghĩa. Mỗi phần tử mới hoặc thắng `best`, hoặc không; do đó sau n phần tử, `best` là một giá trị lớn nhất toàn tập.

Nếu nhiều phần tử được so sánh bằng nhau (kết quả 0), dùng toán tử `> 0` giữ **phần tử lớn nhất xuất hiện đầu tiên**. Đây là chính sách ổn định khi chọn tối đa một giá trị; không có nghĩa mọi implementation của max đều bắt buộc có cùng tie-breaking policy.

## 3. `Comparable<T>`: thứ tự tự nhiên do đối tượng định nghĩa

Java khai báo một interface về khả năng so sánh:

```java
public interface Comparable<T> {
    int compareTo(T other);
}
```

Chữ ký trên là dạng rút gọn để thấy phương thức cốt lõi của interface chuẩn, không phải yêu cầu tự viết lại `java.lang.Comparable` trong project.

Một class triển khai `Comparable<T>` xác định **natural ordering** — thứ tự tự nhiên của đối tượng:

```java
record Ticket(String id, int priority) implements Comparable<Ticket> {
    @Override
    public int compareTo(Ticket other) {
        return Integer.compare(priority, other.priority);
    }
}
```

Với quy tắc này, ticket có `priority` lớn hơn sẽ được xem là lớn hơn. `id` dùng nhận diện ticket, **không tham gia so sánh**. Hai ticket khác id nhưng cùng priority được coi là bằng nhau theo compareTo.

### Quy ước dấu của `compareTo`

| Kết quả `x.compareTo(y)` | Ý nghĩa |
| --- | --- |
| Âm (`< 0`) | x đứng trước y; x nhỏ hơn y theo natural ordering. |
| Bằng 0 | x và y tương đương về thứ tự so sánh. |
| Dương (`> 0`) | x đứng sau y; x lớn hơn y. |

**Đừng giả định `compareTo` chỉ trả -1, 0 hoặc 1.** API chỉ cam kết dấu âm/0/dương. Ví dụ các class chuẩn có thể trả nhiều giá trị âm/dương khác nhau. Vì thế so sánh `candidate.compareTo(best) > 0`, không dùng `== 1`.

### Khi nào `compareTo` không phù hợp?

Một số đối tượng không có thứ tự tự nhiên hợp lý, hoặc có nhiều tiêu chí so sánh tùy nghiệp vụ. Ví dụ một nhân viên có thể được sắp theo lương, ngày bắt đầu hay tên. Trong trường hợp đó, đừng bắt class triển khai một natural ordering tùy tiện; sử dụng `Comparator<T>` được truyền vào, như phần 13.

## 4. Vì sao khai báo bound `T extends Comparable...`?

Hãy thử viết:

```java
// Không hợp lệ nếu body gọi compareTo:
static <T> T max(List<T> values) {
    T candidate = values.get(0);
    T best = values.get(1);
    if (candidate.compareTo(best) > 0) {
        return candidate;
    }
    return best;
}
```

Lỗi nằm ở `candidate.compareTo`: compiler chỉ biết T là một reference type chưa bị giới hạn, gần như Object. `Object` không khai báo `compareTo`, nên không có bằng chứng mọi T đều hỗ trợ method đó.

Cách đưa ràng buộc vào generic method:

```java
static <T extends Comparable<T>> T maxSimple(List<T> values) {
    // T biết cách compareTo(T).
    return values.get(0);
}
```

`extends` trong **type parameter bound** được dùng cho cả class lẫn interface. `T extends Comparable<T>` không bắt buộc T phải là một subclass cụ thể của Comparable: `Comparable` là interface và `extends` chỉ thể hiện giới hạn kiểu.

Bound giúp compiler biết một T có method `int compareTo(T)`. Một chữ ký kiểu an toàn không chỉ trả “bất kỳ T” mà còn yêu cầu **khả năng so sánh**.

### Bound là điều kiện compile-time, không phải runtime cast

Khi caller viết `max(List.of(3, 9, 7))`, compiler có thể suy luận T là Integer; Integer triển khai Comparable<Integer>, nên hợp lệ.

Nếu caller truyền các `new Object()`:

```java
// Không biên dịch:
Object result = max(List.of(new Object(), new Object()));
```

`Object` không triển khai Comparable<Object> hay một Comparable phù hợp. Compiler từ chối tại điểm gọi. Điều đó tốt hơn đợi tới runtime rồi cast sang Comparable một cách không kiểm soát.

## 5. Bound linh hoạt hơn: `Comparable<? super T>`

Một API thường nên viết:

```java
<T extends Comparable<? super T>>
```

thay vì chỉ:

```java
<T extends Comparable<T>>
```

Lý do là một class con vẫn có thể **kế thừa khả năng so sánh với class cha**, không trực tiếp implements Comparable của chính kiểu con.

Ví dụ:

```java
static class Score implements Comparable<Score> {
    final int points;

    Score(int points) {
        this.points = points;
    }

    @Override
    public int compareTo(Score other) {
        return Integer.compare(points, other.points);
    }
}

static final class BonusScore extends Score {
    BonusScore(int points) {
        super(points);
    }
}
```

`Score` implements `Comparable<Score>`. `BonusScore` kế thừa `Score.compareTo(Score)` và do đó có thể so sánh các `BonusScore` (vì BonusScore là Score). Tuy nhiên, `BonusScore` không cần triển khai riêng `Comparable<BonusScore>`.

Nếu hàm max yêu cầu `T extends Comparable<T>`, lời gọi giữ kết quả kiểu `BonusScore` có thể không suy luận được:

```java
// Ví dụ lỗi với chữ ký quá hẹp:
static <T extends Comparable<T>> T maxTooStrict(List<T> list) {
    return list.get(0);
}

// BonusScore chosen = maxTooStrict(List.of(
//         new BonusScore(10), new BonusScore(20)));
```

Phương thức `maxTooStrict` trong ví dụ chỉ trả phần tử đầu để cô lập lỗi **bound**. Trường hợp trên đã được kiểm chứng với JDK 17: compiler báo `inference variable T has incompatible bounds` khi đích là BonusScore.

Trong khi đó, bound `Comparable<? super T>` chấp nhận một Comparable của T **hoặc của supertype mà T kế thừa**. Với T=BonusScore, Comparable<Score> là phù hợp vì Score là supertype của BonusScore. Chữ ký rộng hơn mà vẫn có cơ sở an toàn:

```java
static <T extends Comparable<? super T>> T max(
        Iterable<? extends T> values)
```

Vì method `compareTo` có thể nhận Score, truyền một BonusScore vào đó hoàn toàn hợp lệ.

### Đọc cú pháp từ trong ra ngoài

```text
<T extends Comparable<? super T>>
                 |          |
            khả năng      loại tham số compareTo
            so sánh       có thể là T hoặc supertype của T
```

Đây là **lower-bounded wildcard**. Đối với một nơi **tiêu thụ** T (tham số của compareTo), chấp nhận supertype của T giúp tái sử dụng khả năng so sánh của lớp cha. Nó liên hệ nguyên tắc PECS: **Consumer Super**.

### Không nên nhầm `Comparable<? super T>` với `Comparable<? extends T>`

`compareTo` **nhận** một giá trị làm input. Nếu comparator chỉ nhận một subtype chưa biết của T, compiler không có bảo đảm có thể đưa một T bất kỳ vào đó. Với `? super T`, bất kỳ T nào đều có thể dùng ở vị trí input.

## 6. Chọn tham số đầu vào: List, Collection hay Iterable?

Một version đơn giản có thể nhận `List<T>`:

```java
static <T extends Comparable<? super T>> T max(List<T> list) {
    // ...
}
```

Nhưng thuật toán chỉ cần **duyệt tuần tự**. Nó không cần `get(index)`, `set`, `sort`, `size`, hay khả năng chỉnh sửa list. Vì thế `Iterable<? extends T>` là một hợp đồng tổng quát phù hợp:

```java
public static <T extends Comparable<? super T>> T max(
        Iterable<? extends T> values)
```

Lợi ích:

- Dùng được với List và Set, hoặc các iterable domain-specific.
- Không yêu cầu cấu trúc indexable.
- Không mutating input; method chỉ đọc từng phần tử.
- Chấp nhận source có phần tử subtype của T nhờ `? extends T`.
- Caller vẫn nhận đúng kiểu T được compiler suy luận từ nguồn và ngữ cảnh.

`Iterable<? extends T>` là một producer của T; đây là **Producer Extends**. Kết hợp với `Comparable<? super T>` ta có cả hai ý tưởng PECS trong cùng một chữ ký:

- **Nguồn phần tử:** `? extends T` vì dữ liệu được đọc từ source.
- **Khả năng so sánh:** `? super T` vì compareTo tiêu thụ giá trị T.

**Tại sao không dùng `List<? super T>` làm input?** Một list với element type là supertype của T có thể chứa object không phải T, nên đọc từ đó thành T không được bảo đảm.

### Có luôn cần `Iterable` không?

Không. Nếu API cần `size()` hoặc dùng những tính năng của `Collection`, chọn `Collection<? extends T>`. Nếu cần truy cập ngẫu nhiên bằng index vì mục tiêu thuật toán cụ thể, `List<? extends T>` cũng có lý. Mục tiêu là **giao diện hẹp nhất nhưng đủ sức diễn đạt yêu cầu sử dụng**, không phải dùng Iterable theo thói quen.

## 7. Implementation đúng: duyệt một lần bằng Iterator

Implementation đề xuất:

```java
public static <T extends Comparable<? super T>> T max(
        Iterable<? extends T> values) {
    Objects.requireNonNull(values, "values");

    Iterator<? extends T> it = values.iterator();
    if (!it.hasNext()) {
        throw new IllegalArgumentException("empty input");
    }

    T best = Objects.requireNonNull(it.next(), "element");

    while (it.hasNext()) {
        T candidate = Objects.requireNonNull(it.next(), "element");
        if (candidate.compareTo(best) > 0) {
            best = candidate;
        }
    }
    return best;
}
```

Để đặt đoạn trong class, cần import `java.util.Iterator` và `java.util.Objects`. Chương trình đầy đủ ở phần 10 có import và main.

Phân tích từng bước:

1. `Objects.requireNonNull(values,"values")` bác bỏ tham chiếu iterable null từ đầu.
2. `values.iterator()` lấy trình duyệt; method không cần biết concrete collection.
3. `!it.hasNext()` xử lý input rỗng bằng `IllegalArgumentException` rõ ràng.
4. `it.next()` lấy phần tử đầu làm best; kiểm tra phần tử null.
5. Mỗi phần tử còn lại được kiểm tra null rồi so sánh với best.
6. `candidate.compareTo(best) > 0` thay best khi candidate lớn hơn.
7. Trả trực tiếp **một trong các object đã xuất hiện trong input**, không cần tạo bản sao.

### Tại sao không khởi tạo `T best = null`?

Làm vậy đòi thiết kế thêm quy tắc cho phần tử đầu và có thể che lỗi null. Lấy phần tử đầu làm best sau khi đã kiểm tra tập rỗng giúp invariant rõ ràng: khi bắt đầu vòng lặp, best là phần tử thực thuộc tập và không null.

### Tại sao không so sánh bằng `==` hoặc `>`?

Generics T là kiểu tham chiếu; toán tử `>` không định nghĩa cho mọi object kiểu T. `==` so sánh identity của object (trừ các tình huống primitive/boxing liên quan), không mô tả thứ tự. `compareTo` là hợp đồng so sánh đã được compiler bảo đảm.

### Tại sao không dùng `>= 0`?

Dùng `>= 0` cũng chọn được một phần tử lớn nhất nếu thứ tự hợp lệ, nhưng khi có hai phần tử tương đương (compareTo trả 0), nó sẽ chọn **phần tử đến sau** thay vì giữ phần tử đầu. Mẫu trên dùng `> 0` như một quy tắc tie-breaking cố định và dễ kiểm thử.

### Xử lý null trong contract

Ở đây mọi phần tử đều bắt buộc non-null. Khi gặp phần tử null, method ném `NullPointerException("element")`. Nếu một API muốn có null như giá trị tối thiểu/tối đa, cần định nghĩa ordering cho null và thường nên dùng `Comparator.nullsFirst`/`Comparator.nullsLast` trong phiên bản nhận Comparator. Không để null trôi vào `compareTo` mà không có chính sách rõ.

### Trường hợp nguồn bị thay đổi trong khi duyệt

Method không tự đồng bộ hóa collection. Nếu thread khác sửa collection trong lúc duyệt, kết quả phụ thuộc hợp đồng iterator của collection; có thể có `ConcurrentModificationException` hoặc hành vi khác theo loại collection. Trong code concurrent, cần snapshot hoặc cơ chế đồng bộ phù hợp, không dựa vào generic bound để giải quyết concurrency.

## 8. Độ phức tạp và tính đúng

Với n phần tử:

- Đọc phần tử đầu làm best: O(1).
- So sánh các phần tử còn lại: đúng **n-1 lần gọi compareTo** khi n ≥ 1 (không tính việc kiểm tra null và phép duyệt).
- Thời gian: O(n) nếu mỗi phép compareTo có chi phí O(1). Tổng quát hơn: O(n × chi phí so sánh).
- Bộ nhớ bổ sung của thuật toán: O(1), không tính iterator và các cấu trúc sẵn có trong nguồn. Nó chỉ giữ best, candidate và iterator.

Không thể xác định max trong trường hợp tổng quát bằng cách bỏ qua một phần tử chưa được kiểm tra: một phần tử chưa xét có thể lớn hơn tất cả những phần tử đã xét.

### Chứng minh invariant

**Ban đầu:** Sau khi đọc phần tử đầu, best rõ ràng lớn nhất trong tập gồm đúng phần tử ấy.

**Bước duy trì:** Giả sử best là lớn nhất trong k phần tử đã xử lý. Xét candidate kế tiếp:

- Nếu `candidate.compareTo(best)>0`, candidate lớn hơn best và do đó không thua bất kỳ phần tử trước.
- Nếu điều kiện không đúng, best không nhỏ hơn candidate theo thứ tự so sánh dự kiến; nó tiếp tục là phần tử lớn nhất trong k+1 phần tử.

**Kết thúc:** Sau khi duyệt toàn bộ n phần tử, best là một phần tử lớn nhất.

Chứng minh dựa trên việc `compareTo` tuân thủ **thứ tự nhất quán và hợp đồng Comparable**. Nếu compareTo vi phạm tính bắc cầu hoặc thay đổi kết quả theo trạng thái không ổn định, khái niệm phần tử lớn nhất có thể không rõ ràng và vòng lặp không thể bảo đảm ý nghĩa mong muốn.

## 9. Dạng tiện dụng cho nhiều đối số: `maxOf`

Thay vì tự dựng List, caller có thể muốn:

```java
int best = maxOf(5, 2, 8, 4);
```

Một overload/utility method dạng varargs:

```java
@SafeVarargs
public static <T extends Comparable<? super T>> T maxOf(
        T first, T... rest) {
    T best = Objects.requireNonNull(first, "first");
    for (T candidate : rest) {
        T checked = Objects.requireNonNull(candidate, "element");
        if (checked.compareTo(best) > 0) {
            best = checked;
        }
    }
    return best;
}
```

`maxOf` có một phần tử `first` bắt buộc, nên caller không thể gọi hoàn toàn không đối số. Bên trong không trả mảng varargs, không lưu tham chiếu mảng, không sửa slot bằng kiểu không phù hợp; nó chỉ duyệt và chọn best. Với Java 17, `@SafeVarargs` hợp lệ trên static varargs method và giúp xử lý các cảnh báo generic varargs theo cam kết an toàn của tác giả.

**Quan trọng:** Annotation không tự làm code an toàn. Nếu sau này refactor method để giữ, xuất bản hoặc ghi sai mảng varargs, phải đánh giá lại cam kết `@SafeVarargs`. Đặc biệt khi T non-reifiable, không nên trả `T[] rest` ra ngoài một cách tùy tiện.

### Có thể dùng `maxOf("A", "B")` không?

Có, vì String implements Comparable<String>. Nhưng đừng nhầm một invocation dùng chung T với khả năng so sánh giữa những loại tùy ý. `maxOf("A", 3)` không nhất thiết suy luận được một T phù hợp bound Comparable, dù cả String và Integer riêng lẻ đều Comparable.

### Lưu ý về overload

Nếu API có cả `max(List<T>)` và `maxOf(T, T...)`, đặt tên riêng `maxOf` như trên giúp tránh overload resolution khó hiểu với arrays và generics, đồng thời thể hiện contract không rỗng ở call site.

## 10. Chương trình Java 17 hoàn chỉnh có thể chạy

**Tệp `ComparableMaxDemo.java`**:

```java
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class ComparableMaxDemo {
    public static <T extends Comparable<? super T>> T max(
            Iterable<? extends T> values) {
        Objects.requireNonNull(values, "values");
        Iterator<? extends T> it = values.iterator();
        if (!it.hasNext()) {
            throw new IllegalArgumentException("empty input");
        }
        T best = Objects.requireNonNull(it.next(), "element");
        while (it.hasNext()) {
            T candidate = Objects.requireNonNull(it.next(), "element");
            if (candidate.compareTo(best) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    @SafeVarargs
    public static <T extends Comparable<? super T>> T maxOf(
            T first, T... rest) {
        T best = Objects.requireNonNull(first, "first");
        for (T candidate : rest) {
            T checked = Objects.requireNonNull(candidate, "element");
            if (checked.compareTo(best) > 0) {
                best = checked;
            }
        }
        return best;
    }

    public static <T> T maxBy(
            Iterable<? extends T> values, Comparator<? super T> comparator) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(comparator, "comparator");
        Iterator<? extends T> it = values.iterator();
        if (!it.hasNext()) {
            throw new IllegalArgumentException("empty input");
        }
        T best = Objects.requireNonNull(it.next(), "element");
        while (it.hasNext()) {
            T candidate = Objects.requireNonNull(it.next(), "element");
            if (comparator.compare(candidate, best) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    record Ticket(String id, int priority) implements Comparable<Ticket> {
        @Override
        public int compareTo(Ticket other) {
            return Integer.compare(priority, other.priority);
        }
    }

    static class Score implements Comparable<Score> {
        final String label;
        final int points;

        Score(String label, int points) {
            this.label = label;
            this.points = points;
        }

        @Override
        public int compareTo(Score other) {
            return Integer.compare(points, other.points);
        }

        @Override
        public String toString() {
            return label + ":" + points;
        }
    }

    static final class BonusScore extends Score {
        BonusScore(String label, int points) {
            super(label, points);
        }
    }

    public static void main(String[] args) {
        System.out.println("integers=" + max(List.of(3, 9, 7)));
        System.out.println("strings=" + max(List.of("An", "Mai", "Binh")));
        System.out.println("dates=" + max(List.of(
                LocalDate.of(2026, 1, 3),
                LocalDate.of(2026, 1, 10))));
        System.out.println("varargs=" + maxOf(5, 2, 8, 4));

        List<Ticket> tickets = List.of(
                new Ticket("first", 5),
                new Ticket("second", 5),
                new Ticket("low", 3));
        System.out.println("tie=" + max(tickets).id());

        List<BonusScore> bonuses = List.of(
                new BonusScore("Bronze", 10),
                new BonusScore("Gold", 20));
        BonusScore bestBonus = max(bonuses);
        System.out.println("inheritedComparable=" + bestBonus);

        System.out.println("customComparator=" + maxBy(
                List.of("An", "Huyen", "Lan"),
                Comparator.comparingInt(String::length)));
        System.out.println("jdkMax=" + Collections.max(List.of(3, 9, 7)));

        try {
            max(List.<Integer>of());
        } catch (IllegalArgumentException e) {
            System.out.println("empty=" + e.getMessage());
        }

        try {
            max(Arrays.asList(1, null, 3));
        } catch (NullPointerException e) {
            System.out.println("null=" + e.getMessage());
        }
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac --release 17 -Xlint:all ComparableMaxDemo.java
java ComparableMaxDemo
```

**Stdout chính xác đã kiểm chứng:**

```text
integers=9
strings=Mai
dates=2026-01-10
varargs=8
tie=first
inheritedComparable=Gold:20
customComparator=Huyen
jdkMax=9
empty=empty input
null=element
```

Lệnh `javac --release 17 -Xlint:all` đã biên dịch chương trình này mà không báo warning trong phép thử.

### Diễn giải kết quả

- `integers=9`: Integer implements Comparable<Integer>, chọn 9.
- `strings=Mai`: String có thứ tự từ điển theo quy tắc của `String.compareTo`; trong bộ dữ liệu này Mai đứng sau An và Binh. Điều này không có nghĩa String.compareTo hiểu collation tiếng Việt hay chữ hoa/chữ thường theo locale người dùng.
- `dates=2026-01-10`: LocalDate so sánh theo thứ tự thời gian.
- `varargs=8`: `maxOf` tìm phần tử lớn nhất trong những đối số nguyên.
- `tie=first`: hai Ticket priority=5 được so sánh bằng 0; dùng điều kiện `>0` nên phần tử đầu được giữ.
- `inheritedComparable=Gold:20`: BonusScore kế thừa Comparable<Score> từ Score và được bound `Comparable<? super T>` chấp nhận.
- `customComparator=Huyen`: comparator theo độ dài chuỗi chọn Huyen (5 ký tự) thay vì theo thứ tự tự nhiên của String.
- `jdkMax=9`: kết quả thư viện chuẩn phù hợp với phiên bản tự viết.
- `empty=empty input`: demo chủ động quyết định throw IllegalArgumentException với thông báo nhất quán.
- `null=element`: demo chủ động bác bỏ phần tử null trong nguồn.

## 11. Ví dụ lỗi biên dịch 1: T không Comparable

**Tệp `NonComparableMax.java` — cố ý không biên dịch:**

```java
import java.util.List;

class NonComparableMax {
    static <T extends Comparable<? super T>> T max(List<T> values) {
        return values.get(0);
    }

    static void test() {
        Object result = max(List.of(new Object(), new Object()));
    }
}
```

Lệnh:

```bash
javac --release 17 -Xdiags:verbose NonComparableMax.java
```

Compiler đã báo trọng tâm:

```text
method max in class NonComparableMax cannot be applied to given types
required: List<T>
found:    List<Object>
reason: inference variable E has incompatible bounds
upper bounds: Comparable<? super T>,Object
lower bounds: Object
```

`Object` không cung cấp Comparable cần thiết. Không nên ép `Object` thành Comparable chỉ để im lặng compiler nếu các object thực không thể so sánh. Hãy thiết kế một loại có natural ordering hoặc sử dụng comparator được xác định từ bên ngoài.

## 12. Ví dụ lỗi biên dịch 2: bound quá hẹp

**Tệp `WrongComparableBound.java` — cố ý không biên dịch:**

```java
import java.util.List;

class WrongComparableBound {
    static <T extends Comparable<T>> T max(List<T> values) {
        return values.get(0);
    }

    static class Base implements Comparable<Base> {
        @Override
        public int compareTo(Base other) {
            return 0;
        }
    }

    static class Child extends Base {}

    static void test() {
        Child result = max(List.of(new Child(), new Child()));
    }
}
```

Compiler Java 17 báo:

```text
incompatible types: inference variable T has incompatible bounds
equality constraints: Base,E
upper bounds: Child,Object,Comparable<T>
```

`Child` kế thừa `Comparable<Base>`, không có `Comparable<Child>` theo dạng bound chặt. Chữ ký method khi chỉ cần compareTo có thể sửa thành:

```java
static <T extends Comparable<? super T>> T max(List<T> values) {
    // Dùng thuật toán max thật như phần 7.
    return values.get(0);
}
```

Đoạn trên chỉ minh họa **chữ ký bound sửa đúng**, chưa phải implementation max. Với bound này, caller có thể nhận lại Child khi phần tử nguồn đều là Child.

**Đừng “sửa” bằng cách bắt Child implements Comparable<Child> riêng** nếu điều đó không phù hợp mô hình kế thừa. Dùng `? super T` giúp API tôn trọng Comparable<Base> mà class con đã có.

## 13. Ví dụ lỗi biên dịch 3: trộn kiểu không có ordering chung

**Tệp `MixedTypesMax.java` — cố ý không biên dịch:**

```java
import java.util.List;

class MixedTypesMax {
    static <T extends Comparable<? super T>> T max(List<T> values) {
        return values.get(0);
    }

    static void test() {
        Object result = max(List.of("A", 3));
    }
}
```

Compiler đã báo `method max cannot be applied` và `inference variable E has incompatible bounds`, với các ràng buộc từ String, Integer và Comparable.

Mỗi String hoặc Integer tự so sánh được với đối tượng **cùng họ phù hợp**, nhưng không có nghĩa String và Integer có một natural ordering chung mà hàm `compareTo` này có thể dùng. Điều kiện phải đảm bảo tương thích trên toàn bộ tập, không chỉ từng phần tử riêng rẽ.

Nếu nghiệp vụ thật sự cần so sánh dữ liệu khác loại, phải xác định **một quy tắc chung rõ ràng** và sử dụng Comparator phù hợp. Ví dụ chuyển cả hai thành một kiểu domain có tiêu chí so sánh thống nhất, hoặc chỉ so theo thuộc tính đo lường có nghĩa. Không ép các giá trị khác loại vào Comparable raw và đoán kết quả.

## 14. Ví dụ lỗi biên dịch 4: thêm bound Number không cần thiết

**Tệp `WrongNumberBound.java` — cố ý không biên dịch:**

```java
import java.util.List;

class WrongNumberBound {
    static <T extends Number & Comparable<? super T>> T max(
            List<T> values) {
        return values.get(0);
    }

    static void test() {
        String result = max(List.of("A", "B"));
    }
}
```

Compiler báo `incompatible bounds`, trong đó có:

```text
upper bounds: Number,Comparable<? super T>,Object
lower bounds: String
```

String có Comparable<String> nhưng **không extends Number**. Nếu thuật toán chỉ gọi compareTo, giới hạn Number là thừa và loại bỏ những kiểu hợp lệ như String, LocalDate, enum phù hợp, hoặc domain class Comparable.

Khi nào bound Number có ích? Khi body **thực sự cần method của Number** như `doubleValue()` bên cạnh compareTo. Khi đó:

```java
<T extends Number & Comparable<? super T>>
```

là hợp lý vì diễn đạt **hai yêu cầu**. Với multiple bounds, class bound `Number` phải đứng trước interface bound `Comparable`.

## 15. So sánh với thư viện chuẩn

Java đã cung cấp các API chọn max; bài tập tự viết nhằm hiểu Generics, bounds và invariant, chứ không bắt buộc tạo utility mới trong mọi project.

### `Collections.max`

`java.util.Collections.max(Collection<? extends T>)` sử dụng natural ordering và một bound của type parameter gần với `Comparable<? super T>`; API còn duy trì chi tiết bound vì tương thích nhị phân. Có thể viết:

```java
Integer best = Collections.max(List.of(3, 9, 7));
```

Cần import `java.util.Collections`. `Collections.max` đòi collection không rỗng; với đầu vào rỗng, API chuẩn ném `NoSuchElementException`, **khác với** IllegalArgumentException do hàm `max` tự viết cố ý chọn. Hãy xem exception contract khi thay implementation bằng API chuẩn.

`Collections.max(collection, comparator)` cũng có overload nhận Comparator để quyết định ordering bên ngoài.

### `Stream.max`

Với stream:

```java
Optional<Integer> best =
        List.of(3, 9, 7).stream().max(Integer::compareTo);
```

`Stream.max(Comparator)` trả `Optional<T>`, nên một stream rỗng có thể cho Optional.empty() thay vì buộc ném exception. Nếu ứng dụng muốn người gọi xử lý trường hợp không có phần tử, `Optional<T>` là một contract hữu ích. Đừng gọi `optional.get()` vô điều kiện trên Optional rỗng.

### Tự viết hay dùng thư viện?

Dùng `Collections.max` hoặc `Stream.max` khi hành vi của chúng đáp ứng contract. Tự viết khi cần:

- Dạy/học type bounds, iterator và generic inference.
- Quy định cách xử lý null, empty input và tie-breaking đặc thù.
- Có một Iterable mà API Collection/Stream không được cung cấp trực tiếp.
- Tích hợp logging, kiểm soát so sánh, hoặc xử lý domain-specific.

Không nên viết lại chỉ vì “generic hơn”: hãy so chi phí bảo trì và đảm bảo API chuẩn.

## 16. Comparator: mở rộng khi T không có natural ordering

Bound Comparable yêu cầu **bản thân T có natural ordering**. Nhiều loại dữ liệu không có hoặc không nên có thứ tự mặc định. Khi đó truyền vào một Comparator:

```java
public static <T> T maxBy(
        Iterable<? extends T> values,
        Comparator<? super T> comparator) {
    Objects.requireNonNull(values, "values");
    Objects.requireNonNull(comparator, "comparator");
    Iterator<? extends T> it = values.iterator();
    if (!it.hasNext()) {
        throw new IllegalArgumentException("empty input");
    }
    T best = Objects.requireNonNull(it.next(), "element");
    while (it.hasNext()) {
        T candidate = Objects.requireNonNull(it.next(), "element");
        if (comparator.compare(candidate, best) > 0) {
            best = candidate;
        }
    }
    return best;
}
```

Cần import `Comparator`, `Iterator`, `Objects`. Đoạn này có trong chương trình hoàn chỉnh phần 10.

Ví dụ:

```java
String longest = maxBy(
        List.of("An", "Huyen", "Lan"),
        Comparator.comparingInt(String::length));
System.out.println(longest); // Huyen
```

Ở đây không cần T extends Comparable vì comparator nhận trách nhiệm so sánh. `Comparator<? super T>` dùng super vì comparator **tiêu thụ** hai đối tượng T.

### Natural ordering và custom ordering

- `max(List.of("An","Huyen","Lan"))` dùng `String.compareTo`; kết quả natural order theo Unicode, không phải độ dài.
- `maxBy(..., comparingInt(String::length))` dùng chiều dài chuỗi; Huyen dài nhất trong ví dụ.
- Nếu nhiều chuỗi cùng độ dài, code này giữ chuỗi đầu tiên có độ dài lớn nhất, vì chỉ cập nhật khi so sánh dương.

### `Comparator.reversed()` có ảnh hưởng gì?

Nếu comparator định nghĩa `a < b` theo một tiêu chí, dùng `reversed()` đảo thứ tự. Khi gọi max với comparator đảo ngược, kết quả sẽ tương đương **phần tử nhỏ nhất theo comparator gốc**, theo logic sign của compare.

Đừng đổi chiều so sánh trong body theo cảm tính. Giữ invariant “candidate > best theo comparator được cung cấp” giúp tránh bug khi thêm ascending/descending mode.

## 17. Viết `compareTo` cho class nghiệp vụ đúng cách

Đây là đoạn rút gọn từ `Ticket`:

```java
record Ticket(String id, int priority) implements Comparable<Ticket> {
    @Override
    public int compareTo(Ticket other) {
        return Integer.compare(priority, other.priority);
    }
}
```

### Tránh phép trừ làm comparator

Mẫu dễ gây overflow:

```java
// KHÔNG NÊN:
return this.priority - other.priority;
```

Nếu một số gần `Integer.MAX_VALUE` và số khác gần `Integer.MIN_VALUE`, phép trừ có thể overflow và đảo dấu sai. Dùng `Integer.compare(this.priority, other.priority)` hoặc `Comparator.comparingInt(Ticket::priority)` sẽ rõ và an toàn hơn về dấu.

### Hợp đồng `Comparable`

Một ordering lành mạnh cần tuân thủ các thuộc tính như:

- Dấu của `x.compareTo(y)` phải đối xứng với dấu của `y.compareTo(x)`.
- Nếu x > y và y > z, x phải > z theo tính bắc cầu.
- Nếu x.compareTo(y)==0, quan hệ so sánh của x và y với các z phải tương thích với tính nhất quán của ordering.
- Cần xem chính sách `null`; nhiều implementation Comparable chuẩn không nhận null.

**Nhất quán với `equals` là điều được khuyến nghị, không phải lúc nào bắt buộc**. Ví dụ Ticket là record: equals xét cả id và priority, trong khi compareTo chỉ xét priority. Hai Ticket có id khác nhau nhưng priority bằng nhau được compareTo xem là tương đương. `max` vẫn có thể chọn một phần tử lớn nhất, nhưng khi dùng loại này trong `TreeSet`/`TreeMap`, những cấu trúc dựa trên comparison có thể xem chúng như khóa trùng nhau. Cần cân nhắc business identity và collection semantics.

### Ordering dựa trên trạng thái mutable

Nếu đối tượng bị thay đổi priority trong khi đang so sánh, kết quả max có thể không phản ánh một snapshot nhất quán. Hãy định nghĩa dữ liệu bất biến hoặc điều phối cập nhật trạng thái phù hợp khi dùng comparator trong thuật toán/collection sorted.

## 18. Ngữ nghĩa của String, Number, LocalDate và null

### String

`String.compareTo` so sánh chuỗi theo thứ tự từ điển dựa trên UTF-16 code units. Nó không thay thế một `Collator` cho thứ tự ngôn ngữ cụ thể. Vì vậy kết quả max String là **lớn nhất theo natural ordering của String**, không phải “tên xếp sau trong danh bạ tiếng Việt”.

Nếu cần thứ tự người dùng kỳ vọng theo locale, chọn comparator thích hợp thay vì thay đổi dữ liệu đầu vào tùy tiện.

### Integer, Long và Number

`Integer` và `Long` đều Comparable nhưng không cùng generic contract `Comparable<Number>`. Một List<Number> chứa cả Integer và Long không tự trở thành dãy có natural ordering chung. Giới hạn `T extends Number & Comparable<? super T>` không biến mọi Number thành so sánh được lẫn nhau.

Có thể tạo comparator xử lý Number theo một đại lượng xác định như `doubleValue()`, nhưng cần xét mất chính xác, NaN và các loại Number domain-specific. Đừng biến đổi sang double mà chưa xem yêu cầu tính toán.

### Float/Double

`Float.compareTo`/`Double.compareTo` định nghĩa ordering bao gồm các trường hợp `NaN` và signed zero theo hợp đồng lớp. Đừng dùng trực tiếp toán tử `>` để giả định tương đương với `compareTo` ở mọi giá trị floating-point đặc biệt.

### LocalDate

`LocalDate` có natural ordering dựa trên ngày, nên `max(List<LocalDate>)` chọn ngày muộn nhất. Nếu đang xử lý sự kiện có thời gian và timezone, hãy chọn kiểu domain như `Instant`, `ZonedDateTime` hoặc một comparator cụ thể phù hợp, thay vì so LocalDate rồi hiểu nhầm “thời điểm mới nhất”.

### Null

API demo chủ động không nhận null cho container hoặc các phần tử. `Objects.requireNonNull` làm điểm thất bại rõ ràng thay vì để compareTo ném lỗi ở vị trí không dự đoán được. Nếu null có nghĩa nghiệp vụ (ví dụ “chưa đánh giá”), nên định nghĩa thứ tự rõ bằng Comparator hoặc lọc null trước khi gọi max, và nêu điều này trong contract.

## 19. Edge cases và bảng kiểm thử

| Input | Kết quả kỳ vọng với implementation phần 7 | Giải thích |
| --- | --- | --- |
| `[3,9,7]` | 9 | Phần tử lớn nhất. |
| `[4]` | 4 | Một phần tử cũng là max. |
| `[5,5,3]` | Phần tử 5 đầu tiên | Giữ best khi compareTo bằng 0. |
| `[]` | IllegalArgumentException | Không có phần tử đầu để chọn. |
| `null` làm Iterable | NullPointerException, message `values` | Guard ở đầu method. |
| `[1,null,3]` | NullPointerException, message `element` | Contract từ chối null element. |
| `["An","Mai","Binh"]` | "Mai" | Thứ tự String tự nhiên. |
| `List<BonusScore>` | BonusScore điểm cao nhất | Bound wildcard super chấp nhận Comparable<Score>. |
| `List<Object>` | Lỗi compile tại invocation | Object không bảo đảm Comparable. |
| `List<String,Integer>` hỗn hợp | Không suy luận natural max chung | Các kiểu so sánh khác họ. |

Không phải mọi dữ liệu có thể biểu diễn bằng `List<?>` đều có natural max. Compiler cần một bound tương thích với toàn bộ phần tử theo kiểu tĩnh; cố né bằng raw/unchecked cast làm mất bảo đảm này.

### Kiểm thử tính đúng thay vì chỉ kiểm tra ví dụ thuận lợi

- Max ở đầu/cuối/giữa input.
- Nhiều phần tử trùng thứ tự (tie).
- Giá trị âm hoặc số lớn nếu dùng dữ liệu số.
- Phần tử null và container null.
- Input rỗng và chỉ một phần tử.
- Class con kế thừa Comparable của cha.
- Comparator đảo chiều hoặc comparator không phù hợp.
- Mutation/concurrency nếu source là collection mutable đang được chia sẻ.

Khi bài toán thay đổi quy tắc chọn (ví dụ tất cả các phần tử cùng max), API phải thay đổi kiểu kết quả; một method trả T không thể tự trả đủ toàn bộ các phần tử hòa nếu không đổi contract.

## 20. Các lỗi hiểu nhầm thường gặp

**Nhầm 1: “Generic T có thể gọi compareTo mà không cần bound.”** Không đúng; compiler chỉ biết những method bảo đảm bởi bound của T. Thêm `extends Comparable<? super T>` nếu yêu cầu natural ordering.

**Nhầm 2: “Bound phải là Comparable<T>, không dùng wildcard.”** Không đúng. `Comparable<? super T>` linh hoạt và an toàn hơn với lớp con kế thừa Comparable của cha.

**Nhầm 3: “Comparable<? super T> cho phép so sánh mọi hai loại Object.”** Không đúng. Nó chỉ chứng minh khả năng truyền một T vào compareTo phù hợp; không tạo ordering chung giữa String và Integer.

**Nhầm 4: “compareTo trả +1 nếu lớn hơn.”** Không đúng; chỉ cần dấu dương. So `>0`.

**Nhầm 5: “Phải cast T sang Comparable trong body.”** Không cần khi đã có bound đúng; cast raw làm yếu type safety.

**Nhầm 6: “Dùng List<Number> nhận List<Integer> vì Integer extends Number.”** Generics invariant. Đọc từ List<Integer> qua `List<? extends Number>` hoặc copy vào List<Number> mới.

**Nhầm 7: “Không cần xử lý input rỗng.”** Hàm trả T không có phần tử để trả khi rỗng. Throw hoặc đổi contract thành Optional<T>.

**Nhầm 8: “Kết quả max khi hòa không quan trọng.”** Đối với object khác identity nhưng compareTo=0, tie-breaking có thể ảnh hưởng id được trả, logic nghiệp vụ và test.

**Nhầm 9: “String max là sắp theo tiếng Việt.”** String.compareTo không cung cấp locale-aware collation.

**Nhầm 10: “Có Comparable thì equals phải tương đương compareTo.”** Được khuyến nghị cho nhiều use case, nhưng không bắt buộc. Khi không nhất quán, sorted collections có semantics cần chú ý.

**Nhầm 11: “Dùng comparator chỉ khác cú pháp.”** Comparator cho phép ordering tùy chiến lược, kể cả class không implements Comparable. Nó mở rộng miền ứng dụng của thuật toán.

**Nhầm 12: “Sửa cảnh báo bằng @SuppressWarnings là xong.”** Annotation không biến Object thành Comparable hoặc tạo ordering nhất quán, và không khắc phục compile error về incompatible bounds.

## 21. Bài tập thực hành có đáp án

### Bài 1. Điền bound

Cho body:

```java
static <T /* bound? */> T bigger(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

**Đáp án:** `<T extends Comparable<? super T>>`. Bound bảo đảm `a.compareTo(b)` hợp lệ. Dùng `>=0` thì khi hòa trả a; nếu dùng `>0` và else b thì khi hòa trả b.

### Bài 2. Vì sao không viết `candidate > best`?

**Đáp án:** Toán tử > không áp dụng cho mọi reference type T. Java không overload toán tử > cho arbitrary object; cần compareTo hoặc Comparator.

### Bài 3. Điểm khác nhau giữa `Comparable<T>` và `Comparable<? super T>`?

**Đáp án:** Bound thứ hai cho phép T triển khai Comparable của một supertype T, hữu ích cho subclass như BonusScore kế thừa `Comparable<Score>`. Đây là nới lỏng an toàn tại vị trí nhận tham số compareTo.

### Bài 4. Tại sao `List.of("A", 3)` không hợp lệ cho max tự nhiên?

**Đáp án:** String và Integer có natural ordering riêng, không có guarantee rằng có một T chung thỏa Comparable<? super T> cho cả hai theo chữ ký. Compiler có thể báo incompatible bounds.

### Bài 5. Vì sao List Integer có thể truyền vào Iterable<? extends Number>?

**Đáp án:** Vì Iterable sản xuất phần tử để đọc, và Integer extends Number. Tuy nhiên muốn trả max với T=Number thì cần Number tự có Comparable phù hợp; chỉ có quan hệ Iterable không đủ cho Comparable bound.

### Bài 6. Tại sao cần kiểm tra empty?

**Đáp án:** Không tồn tại phần tử đầu để làm best; method trả T phải chọn một chính sách, như IllegalArgumentException, hoặc đổi sang Optional<T>.

### Bài 7. Hàm max ở phần 7 chọn phần tử nào khi bằng nhau?

**Đáp án:** Giữ phần tử lớn nhất đầu tiên vì chỉ thay best khi compareTo > 0. Nếu đổi >= 0, phần tử về sau sẽ thay phần tử trước khi hòa.

### Bài 8. Có thể dùng max cho một record Ticket không?

**Đáp án:** Có nếu Ticket implements Comparable<Ticket> với compareTo hợp lệ. Ví dụ phần 10 so theo priority và chọn Ticket đầu khi hai priority cùng 5.

### Bài 9. Tại sao không viết `this.priority - other.priority`?

**Đáp án:** Phép trừ int có thể overflow, đảo dấu sai; dùng `Integer.compare`.

### Bài 10. `Collections.max` khác hàm tự viết ở điểm nào?

**Đáp án:** Cùng mục tiêu natural max với collection phù hợp, nhưng ví dụ tự viết nhận Iterable và chọn IllegalArgumentException khi rỗng; Collections.max trên collection rỗng ném NoSuchElementException.

### Bài 11. Nếu class không có Comparable nhưng muốn chọn theo age?

**Đáp án:** Dùng `maxBy(values, Comparator.comparingInt(Person::age))` nếu có một kiểu Person định nghĩa accessor age; comparator cung cấp ordering độc lập với class.

### Bài 12. Hàm có thể trả một `BonusScore` từ List<BonusScore> dù nó chỉ Comparable<Score> không?

**Đáp án:** Có với bound `T extends Comparable<? super T>`; Score là supertype của BonusScore, nên compareTo(Score) nhận được BonusScore.

### Bài 13. Vì sao không coi Number là loại so sánh chung của mọi Integer/Double?

**Đáp án:** Number không định nghĩa compareTo và một List<Number> có thể chứa nhiều loại Number khác nhau. Cần comparator có chính sách chung hoặc convert domain type phù hợp.

### Bài 14. Độ phức tạp?

**Đáp án:** Với n≥1, method duyệt một lần và so sánh n-1 lần. Thời gian O(n) nếu compareTo O(1), bộ nhớ bổ sung thường O(1).

### Bài 15. Nếu người dùng muốn max theo nhiều tiêu chí?

**Đáp án:** Dùng Comparator có cấu trúc như `Comparator.comparingInt(...).thenComparing(...)` để xác định tiêu chí phụ, tránh gắn natural ordering tùy tiện vào class nếu có nhiều thứ tự nghiệp vụ khác nhau.

## 22. Checklist triển khai trong dự án

1. Đầu vào có natural ordering không, hay cần Comparator?
2. T đã có bound `Comparable<? super T>` chưa?
3. Bound có quá hẹp (Comparable<T>) hoặc quá rộng (raw Comparable) không?
4. API chỉ cần Iterable hay cần Collection/List?
5. Có cho null container, null elements và empty input không?
6. Tie-breaking là giữ phần tử đầu hay cuối khi compareTo==0?
7. compareTo có nhất quán, bắc cầu, ổn định với dữ liệu đang xét không?
8. Comparator có dựa trên state mutable hay external service làm ordering thay đổi không?
9. Có bị trộn các kiểu so sánh không tương thích không?
10. Có thể dùng Collections.max hoặc Stream.max thay code tự viết không?
11. Các ví dụ Java đã biên dịch với đúng tên class/file và stdout đã xác minh chưa?
12. Các case lỗi có được biên dịch riêng và chẩn đoán đúng type inference/bounds không?

## 23. Kết luận

Để viết hàm chọn phần tử lớn nhất có an toàn kiểu, hãy bắt đầu từ hợp đồng `Comparable` thay vì ép kiểu hoặc toán tử so sánh không áp dụng cho T. Chữ ký:

```java
public static <T extends Comparable<? super T>> T max(
        Iterable<? extends T> values)
```

thể hiện hai quan hệ then chốt: **T có thể so sánh với một T thông qua Comparable của T hoặc supertype phù hợp**, và **nguồn có thể cung cấp những phần tử là T hoặc subtype của T**. Phần thân chỉ cần duyệt một lần, giữ phần tử lớn nhất đã thấy và xử lý rõ đầu vào rỗng/null.

Với ordering tự nhiên, đây là bài tập điển hình của bounded type parameters trong Java Generics. Với ordering thay đổi theo nghiệp vụ, dùng một phiên bản nhận Comparator. Không làm yếu type safety bằng raw cast khi compiler báo incompatible bounds, và không quên các ràng buộc hành vi của compareTo.

**Ghi nhớ:** Bound không chỉ giúp code gọi được `compareTo`; nó là lời hứa compile-time rằng phép so sánh có ý nghĩa đối với loại phần tử mà API nhận và trả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-integrated-practice-003">Thiết kế hàm sao chép đúng PECS</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế hàm sao chép đúng PECS trong Java Generics

Một thao tác rất phổ biến khi thiết kế API Java là **đọc phần tử từ một nguồn và ghi chúng vào một đích**. Nếu chỉ khai báo `List<T>` ở cả hai phía, API có thể trở nên quá hẹp: bạn muốn đọc `Integer` từ `List<Integer>` rồi ghi vào `List<Number>`, nhưng **Java Generics là bất biến** (*invariant*), nên `List<Integer>` không phải subtype của `List<Number>`.

Giải pháp đúng là chọn wildcard theo **vai trò của từng tham số**:

> **PECS = Producer Extends, Consumer Super.** Bên *sản xuất* phần tử để ta đọc dùng `? extends T`; bên *tiêu thụ* phần tử ta muốn ghi dùng `? super T`.

Một chữ ký điển hình:

```java
static <T> int appendAll(
        Iterable<? extends T> source,
        Collection<? super T> destination)
```

Nó nói rằng **nguồn cung cấp các phần tử có thể đọc như `T`** và **đích chấp nhận các phần tử `T`**. Method có thể làm việc với `List<Integer>` → `List<Number>`, `List<Integer>` → `List<Object>`, đồng thời không cần raw types hoặc unchecked casts.

Tuy nhiên, chữ “sao chép” có thể chỉ **hai hợp đồng khác nhau**:

- **Append:** duyệt nguồn rồi thêm phần tử vào đích, thường dùng `Collection.add`. Đích có thể tăng kích thước hoặc, với `Set`, không tăng vì phần tử đã tồn tại.
- **Overwrite/replace:** ghi đè các vị trí đang có trong một `List`, thường dùng `List.set`. Đích phải có đủ vị trí; thao tác không tự thêm chỗ mới.

Bài thực hành này xây cả hai loại, minh họa bản sao tạm để xử lý vùng dữ liệu chồng lấn, và giải thích giới hạn runtime như `null`, mutation, alias, partial failure và concurrent modification.

## 1. Mục tiêu và kiến thức nền

Sau bài, bạn có thể:

1. Tự giải thích tại sao `List<Integer>` không gán được cho `List<Number>`, dù `Integer extends Number`.
2. Chọn **`Iterable<? extends T>` cho nguồn** và **`Collection<? super T>` cho đích** khi một thao tác đọc nguồn–ghi đích.
3. Xây một method `appendAll` không dùng cast, có số phần tử đã xử lý rõ nghĩa.
4. Xây một method `overwritePrefix` sử dụng `List.set`, kiểm tra kích thước đích và snapshot nguồn để hạn chế lỗi khi các list views chồng lấn.
5. Phân biệt `Collections.copy` với `Collection.addAll`/tự viết method append.
6. Biết khi nào dùng `List<T>`, `List<?>`, `? extends T`, `? super T` hay `<T>` riêng ở method.
7. Mở rộng sang sao chép **kèm biến đổi kiểu** với `Function<? super S,? extends R>`.
8. Viết Javadoc, bài test và tài liệu hóa những điều generic signature **không tự bảo đảm**: mutability, null policy, atomicity, aliasing, thứ tự và đa luồng.

Bài sử dụng Java 17 trở lên, không cần thư viện ngoài JDK.

## 2. Vì sao `List<T>` ở cả hai phía chưa đủ linh hoạt?

Xét cách viết ban đầu:

```java
static <T> void copyTooNarrow(
        List<T> source,
        List<T> destination) {
    for (T value : source) {
        destination.add(value);
    }
}
```

Method có thể hoạt động khi hai tham số có type arguments tương thích với cùng `T`, nhưng lời gọi với `List<Integer>` và `List<Number>` **không được `copyTooNarrow` diễn đạt một cách tự nhiên** khi suy luận trực tiếp theo invariant `List<T>`.

```java
List<Integer> ints = List.of(1, 2, 3);
List<Number> numbers = new ArrayList<>();

// copyTooNarrow(ints, numbers); // Không suy luận được T chung phù hợp
```

`List<Integer>` không phải `List<Number>`. Nếu phép gán ấy được cho phép, code dùng tham chiếu `List<Number>` có thể thêm `Double` vào một list vốn được nhìn dưới kiểu `List<Integer>`:

```java
// Đoạn giả định KHÔNG hợp lệ trong Java:
// List<Number> aliases = ints;
// aliases.add(3.14); // Phá hợp đồng List<Integer>
```

**Giải pháp không phải `(List<Number>)(List<?>)ints`**. Cast unchecked làm mất tính an toàn mà compiler đang cố bảo vệ. Hãy để phương thức nói rõ **chỉ đọc** từ nguồn và **chỉ ghi** vào đích bằng wildcard ở đúng vị trí.

## 3. Đọc PECS từ góc nhìn producer và consumer

Cho `T = Integer`:

```java
List<Integer> integers = List.of(2, 4, 6);

List<? extends Number> producer = integers;
Number first = producer.get(0); // Đọc được như Number.

// producer.add(8); // Không biên dịch với non-null Integer thông thường.

List<? super Integer> consumer = new ArrayList<Number>();
consumer.add(9); // Ghi Integer hợp lệ.
Object item = consumer.get(0); // Đọc an toàn ít nhất như Object.
```

Hai wildcard có ý nghĩa khác nhau:

| Kiểu tham chiếu | Dùng để làm gì? | Đọc | Ghi |
| --- | --- | --- | --- |
| `List<T>` | Biết element type `T` ở tham chiếu | `T` | `T` |
| `List<? extends T>` | **Producer** của các giá trị đọc được như `T` | `T` | Không thêm một giá trị `T` non-null tùy ý |
| `List<? super T>` | **Consumer** có thể nhận `T` | `Object` nếu không biết kiểu đích cụ thể hơn | `T` |
| `List<?>` | Không cần biết element type | `Object` | Không thêm giá trị non-null tùy ý |

Ghi chú kỹ thuật: một số thao tác như thêm `null` có thể được compiler chấp nhận qua wildcard, nhưng **đó không phải lý do chọn kiểu nguồn** và còn phụ thuộc null policy của collection. Khi học PECS, hãy hiểu “không thêm được phần tử `T` tùy ý qua `? extends T`”, tránh phát biểu tuyệt đối rằng bất kỳ thao tác `add` nào cũng bị cấm.

**Wildcard không quy định mutability runtime.** Một tham chiếu `List<? super Integer>` vẫn có thể trỏ tới một list không hỗ trợ `add`, và lời gọi hợp kiểu có thể ném `UnsupportedOperationException`.

## 4. Chữ ký cốt lõi của phép append

Thiết kế:

```java
static <T> int appendAll(
        Iterable<? extends T> source,
        Collection<? super T> destination) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(destination, "destination");

    int processed = 0;
    for (T value : source) {
        destination.add(value);
        processed++;
    }
    return processed;
}
```

Giải thích từng phần:

- `<T>` là **type parameter của method**, liên kết element type được đọc từ nguồn với element type được đích chấp nhận.
- `Iterable<? extends T>` nói rằng nguồn có thể là `List<Integer>`, `Set<Integer>` hoặc một iterable khác tạo ra các giá trị phù hợp với `T`. Ta chỉ cần thao tác lặp nên không bắt nguồn phải là `List`.
- `Collection<? super T>` nói rằng destination có thể nhận các phần tử `T`. Nó không ép destination phải là `List<T>` đúng chính xác.
- `for (T value : source)` đọc an toàn: phần tử của nguồn là một subtype phù hợp của `T`.
- `destination.add(value)` ghi an toàn: destination chấp nhận `T` hoặc supertype tương ứng.
- `processed` **tăng sau mỗi lần `add` trả về bình thường** và không có exception. Nó không phải số phần tử mới thực sự được thêm với mọi loại `Collection`.

Ví dụ:

```java
List<Integer> integers = List.of(2, 4, 6);
List<Number> numbers = new ArrayList<>(List.of(0.5));

int count = appendAll(integers, numbers);
System.out.println(count);   // 3
System.out.println(numbers); // [0.5, 2, 4, 6]
```

Compiler kiểm tra đúng quan hệ `Integer <: Number` thông qua wildcard mà không cần raw type.

### Vì sao chọn `Iterable` và `Collection` thay vì hai `List`?

Hàm này **chỉ đọc tuần tự**, không cần `get(i)` hay `set(i)` ở nguồn. Đích chỉ cần hỗ trợ `add`, không cần truy cập chỉ số. Do đó việc dùng `Iterable` cho nguồn và `Collection` cho đích giảm ràng buộc API không cần thiết.

Nếu nghiệp vụ chỉ cho phép sao chép từ `List` đến `List` vì yêu cầu order, size hoặc random access, chữ ký hẹp hơn có thể hợp lý. Hãy chọn abstraction **theo thao tác thực tế**, không phải theo thói quen viết `List<T>` ở mọi nơi.

## 5. `appendAll` chấp nhận những cặp kiểu nào?

Hãy xem các lời gọi:

```java
List<Integer> ints = List.of(1, 2);
Collection<Number> numbers = new ArrayList<>();
Collection<Object> objects = new ArrayList<>();

appendAll(ints, numbers); // Đúng: Number tiêu thụ được Integer.
appendAll(ints, objects); // Đúng: Object tiêu thụ được Integer.
```

Một bộ lưu subtype cũng có thể làm nguồn khi kết quả được đích cho phép. Ví dụ:

```java
List<Double> doubles = List.of(2.5, 4.5);
Collection<Number> numbers = new ArrayList<>();
appendAll(doubles, numbers); // Đúng.
```

Ngược lại:

```java
List<Number> source = List.of(1, 2.5);
List<Integer> target = new ArrayList<>();

// appendAll(source, target); // Không biên dịch: đích không nhận mọi Number.
```

Nếu lời gọi cuối được cho phép, phần tử `Double` ở nguồn có thể bị ghi vào `List<Integer>`, trái hợp đồng typed của đích.

**Không nên chữa lỗi bằng unchecked cast.** Trường hợp này là dữ liệu hoặc thiết kế hướng sao chép không tương thích; hãy chuyển dữ liệu có kiểm tra/biến đổi (ví dụ làm tròn theo chính sách domain) hoặc chọn kiểu đích rộng hơn.

## 6. `Collection.add` có thể không thêm phần tử mới

Trong `Collection<E>`, `add(E)` trả một `boolean` cho biết collection **đã thay đổi vì lời gọi đó** theo hợp đồng của phương thức. Đối với `List` mutable, thêm một phần tử thường làm size tăng; đối với `Set`, một phần tử đã tồn tại có thể khiến `add` trả `false`.

Ví dụ:

```java
List<Integer> source = List.of(2, 4, 6);
Set<Number> unique = new LinkedHashSet<>(List.of(2, 4));

int processed = appendAll(source, unique);

System.out.println(processed); // 3
System.out.println(unique);    // [2, 4, 6]
```

Dù chỉ phần tử 6 tạo ra một entry mới, `processed == 3` vì method **đã xử lý thành công ba lần gọi `add`**, kể cả những lần `add` trả `false`.

Nếu hợp đồng muốn trả **số lần `add` thực sự thay đổi collection**, hãy viết một method hoặc một tên rõ hơn:

```java
static <T> int countAccepted(
        Iterable<? extends T> source,
        Collection<? super T> destination) {
    int accepted = 0;
    for (T value : source) {
        if (destination.add(value)) {
            accepted++;
        }
    }
    return accepted;
}
```

Hai cách đếm trả về giá trị khác nhau trên `Set` có phần tử trùng. Không nên viết Javadoc “number of inserted elements” cho method `appendAll` đang thực sự đếm **các phần tử đã xử lý**.

## 7. Append khác overwrite: đích `List` với các vị trí có sẵn

Một số bài toán “copy” không muốn **thêm** mà muốn **ghi đè** các vị trí hiện có. Thư viện Java cung cấp:

```java
Collections.copy(destination, source);
```

Chữ ký generic có dạng:

```java
public static <T> void copy(
        List<? super T> dest,
        List<? extends T> src)
```

`src` là producer; `dest` là consumer. Đây chính là PECS. Nhưng **hợp đồng runtime khác `appendAll`**:

- `Collections.copy` thay thế các vị trí đầu của `dest` bằng phần tử từ `src`.
- `dest` phải có **ít nhất** `src.size()` phần tử đang tồn tại.
- `dest` cần hỗ trợ `set` ở các vị trí được ghi; một `List.of` unmodifiable không đáp ứng.
- Nếu `dest` dài hơn nguồn, các phần tử còn lại vẫn được giữ.
- Nếu `dest` quá ngắn, `IndexOutOfBoundsException` có thể xảy ra.
- Một số live views/chồng lấn có thể khiến kết quả copy trực tiếp khác mong đợi; cần xét alias semantics.

Ví dụ:

```java
List<Integer> src = List.of(2, 4, 6);
List<Number> dest = new ArrayList<>(List.of(-1, -1, -1, -1));

Collections.copy(dest, src);
System.out.println(dest); // [2, 4, 6, -1]
```

Cần phân biệt với:

```java
dest.addAll(src); // Thêm ba phần tử vào cuối; không thay ba ô đầu.
```

**Đừng dùng `new ArrayList<>(capacity)` rồi gọi `Collections.copy` ngay.** Capacity chỉ là khả năng cấp phát nội bộ; list mới có `size()==0`. Bạn phải tạo **các phần tử đang tồn tại** đủ số lượng hoặc dùng append thay vì overwrite.

## 8. Xây `overwritePrefix`: kiểm tra độ dài và snapshot nguồn

Tự viết một biến thể an toàn hơn với **source/destination là hai subList views chồng lấn**:

```java
static <T> int overwritePrefix(
        List<? super T> destination,
        List<? extends T> source) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(destination, "destination");

    // Sao chép nguồn trước khi sửa đích, tránh ghi đè dữ liệu
    // còn cần đọc khi hai list views cùng backing storage.
    List<T> snapshot = new ArrayList<>(source);

    if (destination.size() < snapshot.size()) {
        throw new IndexOutOfBoundsException(
                "destination too short: "
                + destination.size() + " < " + snapshot.size());
    }

    for (int i = 0; i < snapshot.size(); i++) {
        destination.set(i, snapshot.get(i));
    }

    return snapshot.size();
}
```

**Hai lựa chọn thiết kế nổi bật:**

1. **Snapshot nguồn trước khi ghi** để nếu source và destination chia sẻ backing list, việc ghi vào destination không làm biến đổi những phần tử còn cần đọc của source trong quá trình copy.
2. **Kiểm tra số lượng vị trí trước khi bắt đầu ghi** để một destination quá ngắn bị từ chối mà không có các lần `set` đã thực hiện từ vòng lặp này.

Ví dụ:

```java
List<Integer> source = List.of(2, 4, 6);
List<Number> slots = new ArrayList<>(List.of(-1, -1, -1, -1));

int n = overwritePrefix(slots, source);
System.out.println(n);     // 3
System.out.println(slots); // [2, 4, 6, -1]
```

**Không phải transaction atomic:** nếu destination không hỗ trợ `set`, một phần `set` từ chối, hoặc có side effects/đồng thời từ caller khác, có thể đã có một số vị trí được ghi trước khi lỗi phát sinh. Kiểm tra size từ đầu chỉ bảo đảm **không thất bại vì chiều dài ban đầu không đủ** trong tình huống không có thay đổi cạnh tranh.

### Kích thước nhỏ hơn nguồn

```java
List<Number> tooSmall = new ArrayList<>(List.of(-1, -1));

try {
    overwritePrefix(tooSmall, List.of(2, 4, 6));
} catch (IndexOutOfBoundsException expected) {
    System.out.println("Destination too short");
}

System.out.println(tooSmall); // [-1, -1]
```

Hàm tạo snapshot, so độ dài **trước mọi lần `set`**, nên trong ví dụ single-threaded này đích giữ nguyên. `Collections.copy` cũng yêu cầu đủ độ dài nhưng không có cùng lời hứa về snapshot/aliasing như biến thể tự viết.

## 9. Lỗi thường gặp khi dùng `Collections.copy`

Một đoạn trông hợp lý nhưng sai:

```java
List<Integer> source = List.of(1, 2, 3);
List<Number> destination = new ArrayList<>(source.size());

// KHÔNG hỗ trợ vì destination hiện vẫn có size = 0:
// Collections.copy(destination, source);
```

Constructor `ArrayList<>(initialCapacity)` chỉ đặt **capacity**, không tạo phần tử. Hai lựa chọn:

```java
// Nếu muốn thêm:
destination.addAll(source);
```

hoặc:

```java
// Nếu muốn ghi đè từng ô đã tồn tại:
List<Number> destination = new ArrayList<>(
        Arrays.asList(0, 0, 0));
Collections.copy(destination, source);
```

Còn một vấn đề: `List.of` không hỗ trợ `set`; `Arrays.asList` có **kích thước cố định nhưng cho phép `set`**. Bởi vậy:

```java
List<Number> fixed = Arrays.<Number>asList(-1, -1, -1);
Collections.copy(fixed, source); // Hợp lệ khi phần tử/size phù hợp.
```

Trong khi:

```java
List<Number> unmodifiable = List.of(-1, -1, -1);
// Collections.copy(unmodifiable, source); // Có thể ném UnsupportedOperationException.
```

Dấu ngoặc chú thích không phải do code không biên dịch: lời gọi có kiểu hợp lệ nhưng implementation từ chối sửa đổi **ở runtime**.

## 10. Self-copy và vùng `subList` chồng lấn

Một lỗi khó thấy trong các thuật toán sao chép là **nguồn và đích tham chiếu cùng cấu trúc dữ liệu**.

### 10.1. Append một list vào chính nó

```java
List<Integer> list = new ArrayList<>(List.of(1, 2, 3));

// Không nên mặc định thao tác duyệt source đồng thời add vào
// chính collection là an toàn:
// appendAll(list, list);
```

Trong một `ArrayList` thông thường, vòng lặp `for-each` sử dụng iterator. Thay đổi cấu trúc list qua `add` trong lúc đang duyệt có thể gây `ConcurrentModificationException`. Với một số iterable/concurrent collection khác, cách duyệt và kết quả có thể khác; không được giả định “mọi self-copy sẽ luôn ném một exception cụ thể”.

Một cách có chủ đích là **snapshot trước khi append**:

```java
static <T> int appendSnapshot(
        Iterable<? extends T> source,
        Collection<? super T> destination) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(destination, "destination");

    List<T> snapshot = new ArrayList<>();
    for (T value : source) {
        snapshot.add(value);
    }

    for (T value : snapshot) {
        destination.add(value);
    }
    return snapshot.size();
}
```

Với:

```java
List<Integer> values = new ArrayList<>(List.of(1, 2, 3));
appendSnapshot(values, values);

System.out.println(values); // [1, 2, 3, 1, 2, 3]
```

Nguồn được duyệt xong **trước khi** đích thay đổi, nên việc self-append này có hành vi dễ dự đoán hơn trong trường hợp single-threaded. Snapshot tiêu tốn bộ nhớ **O(n)** theo số phần tử nguồn, và không tự bảo đảm một bản chụp đồng bộ nếu nguồn bị thread khác sửa trong khi snapshot đang được xây dựng.

### 10.2. Hai `subList` views dùng chung backing list

```java
List<Integer> values = new ArrayList<>(List.of(1, 2, 3, 4));

List<Integer> source = values.subList(0, 3);
List<Integer> target = values.subList(1, 4);

overwritePrefix(target, source);

System.out.println(values); // [1, 1, 2, 3]
```

Nếu một thuật toán đọc–ghi xen kẽ trực tiếp trên hai views, lần ghi vào target có thể thay đổi phần tử mà vòng lặp sắp đọc từ source; với views chồng lấn, đó là một dependency không được tính đến. Trong `overwritePrefix`, `snapshot` chứa `[1,2,3]` trước khi có ghi nào, nên phần được sao chép có nghĩa rõ ràng.

**Đừng tuyên bố đây là snapshot thread-safe/atomic**. Trong multi-threaded code, quá trình tạo snapshot và các lệnh `set` vẫn cần chính sách đồng bộ nếu phải quan sát dữ liệu nhất quán.

## 11. So sánh ba chiến lược sao chép

| Chiến lược | Nguồn | Đích | Thao tác chính | Điều kiện và trade-off |
| --- | --- | --- | --- | --- |
| `appendAll` | `Iterable<? extends T>` | `Collection<? super T>` | `add` theo vòng lặp | Có thể thay kích thước; không tự an toàn cho self-append |
| `appendSnapshot` | `Iterable<? extends T>` | `Collection<? super T>` | Snapshot rồi `add` | Thêm bộ nhớ O(n); tự sao chép dễ dự đoán hơn |
| `overwritePrefix` | `List<? extends T>` | `List<? super T>` | Snapshot rồi `set` | Cần `dest.size >= src.size`, hỗ trợ `set`; tránh lỗi đọc–ghi chồng lấn |
| `Collections.copy` | `List<? extends T>` | `List<? super T>` | Thay phần tử đã tồn tại | Có sẵn trong JDK; cần đủ vị trí, xem contract về views/mutation |
| `destination.addAll(source)` | `Collection<? extends E>` | `Collection<E>` | Thêm hàng loạt theo API collection | Tiện nếu đã có collection phù hợp; hợp đồng riêng về self-add và failure |

**Độ phức tạp** phụ thuộc implementation. Với ArrayList, append tuần tự thường có tổng thời gian O(n) theo mô hình amortized capacity; snapshot cũng mất O(n) bộ nhớ; với LinkedList hoặc custom Collection, chi phí có thể khác. Không bảo đảm `Collection.add`, `List.get`, `List.set` đều O(1) đối với mọi implementation.

Nếu cần **thêm** và nguồn là `Collection<? extends T>`, thường dùng `destination.addAll(source)` là đủ. Nếu API cần nhận bất kỳ `Iterable` nào, đếm phần tử đã xử lý, điều khiển validation/exception hoặc snapshot trước mutation, helper tự viết có giá trị riêng.

## 12. Generic method mở rộng: copy kèm chuyển đổi kiểu `S -> R`

PECS cũng áp dụng khi giá trị đầu vào và đầu ra **không cùng một kiểu**.

```java
static <S,R> int mapAppend(
        Iterable<? extends S> source,
        Collection<? super R> destination,
        Function<? super S, ? extends R> mapper) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(mapper, "mapper");

    int processed = 0;
    for (S value : source) {
        destination.add(mapper.apply(value));
        processed++;
    }
    return processed;
}
```

Ở đây:

- `S` là **source element type**, `R` là **result element type**.
- `Iterable<? extends S>` sản xuất những giá trị có thể đọc như `S`.
- `Function<? super S, ? extends R>` **tiêu thụ `S`** và **sản xuất `R`**.
- `Collection<? super R>` tiêu thụ kết quả `R`.

Ví dụ:

```java
List<Integer> ints = List.of(2, 4, 6);
List<Number> halves = new ArrayList<>();

mapAppend(ints, halves, n -> n / 2.0);

System.out.println(halves); // [1.0, 2.0, 3.0]
```

Vì mapper trả `Double`, kiểu đích `Number` đủ rộng để nhận kết quả. Không cần ép `Integer` thành `Double` bằng unchecked cast; phép chia thực hiện thông qua biểu thức số thông thường.

**Lưu ý contract:** nếu mapper ném exception hoặc destination từ chối `add`, một số phần tử trước đó có thể đã được ghi. Nếu mapper trả null, khả năng ghi null phụ thuộc đích; API có thể chủ động cấm bằng `requireNonNull` nếu yêu cầu nghiệp vụ đòi vậy.

## 13. Bản demo Java 17+ hoàn chỉnh

Lưu tệp **`PecsCopyDemo.java`**. Chương trình có đủ bốn helper và nhiều tình huống kiểm chứng PECS, append, overwrite, overlap, size, Set và destination không sửa được.

```java
import java.util.*;
import java.util.function.Function;

public class PecsCopyDemo {
    static <T> int appendAll(
            Iterable<? extends T> source,
            Collection<? super T> destination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");

        int processed = 0;
        for (T value : source) {
            destination.add(value);
            processed++;
        }
        return processed;
    }

    static <T> int appendSnapshot(
            Iterable<? extends T> source,
            Collection<? super T> destination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");

        List<T> snapshot = new ArrayList<>();
        for (T value : source) {
            snapshot.add(value);
        }
        for (T value : snapshot) {
            destination.add(value);
        }
        return snapshot.size();
    }

    static <T> int overwritePrefix(
            List<? super T> destination,
            List<? extends T> source) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");

        List<T> snapshot = new ArrayList<>(source);
        if (destination.size() < snapshot.size()) {
            throw new IndexOutOfBoundsException(
                    "destination too short: " + destination.size()
                    + " < " + snapshot.size());
        }

        for (int i = 0; i < snapshot.size(); i++) {
            destination.set(i, snapshot.get(i));
        }
        return snapshot.size();
    }

    static <S,R> int mapAppend(
            Iterable<? extends S> source,
            Collection<? super R> destination,
            Function<? super S, ? extends R> mapper) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(mapper, "mapper");

        int processed = 0;
        for (S value : source) {
            destination.add(mapper.apply(value));
            processed++;
        }
        return processed;
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(2, 4, 6);
        List<Number> numbers =
                new ArrayList<>(List.of(0.5));

        System.out.println("Append count: "
                + appendAll(integers, numbers));
        System.out.println("Numbers: " + numbers);

        List<Object> objects = new ArrayList<>();
        appendAll(integers, objects);
        System.out.println("Objects: " + objects);

        List<Number> slots =
                new ArrayList<>(List.of(-1, -1, -1, -1));
        System.out.println("Replaced count: "
                + overwritePrefix(slots, integers));
        System.out.println("Slots: " + slots);

        List<Number> tooSmall =
                new ArrayList<>(List.of(-1, -1));
        try {
            overwritePrefix(tooSmall, integers);
        } catch (IndexOutOfBoundsException ex) {
            System.out.println(
                    "Too small: IndexOutOfBoundsException");
        }
        System.out.println(
                "Too small unchanged: " + tooSmall);

        List<Integer> same =
                new ArrayList<>(List.of(1, 2, 3));
        System.out.println("Self-copy count: "
                + appendSnapshot(same, same));
        System.out.println("Self-copy: " + same);

        List<Integer> overlap =
                new ArrayList<>(List.of(1, 2, 3, 4));
        overwritePrefix(
                overlap.subList(1, 4),
                overlap.subList(0, 3));
        System.out.println("Overlapping views: " + overlap);

        List<Number> mapped = new ArrayList<>();
        System.out.println("Mapped count: "
                + mapAppend(integers, mapped, n -> n / 2.0));
        System.out.println("Mapped: " + mapped);

        Set<Number> unique =
                new LinkedHashSet<>(List.of(2, 4));
        System.out.println("Set processed: "
                + appendAll(integers, unique));
        System.out.println("Set values: " + unique);

        List<Number> immutable = List.of(0);
        try {
            appendAll(integers, immutable);
        } catch (UnsupportedOperationException ex) {
            System.out.println(
                    "Immutable destination: "
                    + "UnsupportedOperationException");
        }

        List<? extends Number> producer = integers;
        System.out.println(
                "Producer read: " + producer.get(0));

        List<? super Integer> consumer =
                new ArrayList<Number>();
        consumer.add(9);
        System.out.println("Consumer read type: "
                + consumer.get(0).getClass().getSimpleName());

        List<Number> standard =
                new ArrayList<>(List.of(-1, -1, -1));
        Collections.copy(standard, integers);
        System.out.println(
                "Collections.copy: " + standard);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 -Xlint:all PecsCopyDemo.java
java PecsCopyDemo
```

**Standard output** (không có dữ liệu tùy thời điểm):

```text
Append count: 3
Numbers: [0.5, 2, 4, 6]
Objects: [2, 4, 6]
Replaced count: 3
Slots: [2, 4, 6, -1]
Too small: IndexOutOfBoundsException
Too small unchanged: [-1, -1]
Self-copy count: 3
Self-copy: [1, 2, 3, 1, 2, 3]
Overlapping views: [1, 1, 2, 3]
Mapped count: 3
Mapped: [1.0, 2.0, 3.0]
Set processed: 3
Set values: [2, 4, 6]
Immutable destination: UnsupportedOperationException
Producer read: 2
Consumer read type: Integer
Collections.copy: [2, 4, 6]
```

Điểm đáng chú ý:

- Dòng đầu dùng `List<Integer>` và `List<Number>`: compiler chấp nhận nhờ PECS.
- `List<Object>` cũng là destination hợp lệ.
- `overwritePrefix` **thay các vị trí đầu** và giữ `-1` cuối; không append.
- Destination thiếu vị trí bị từ chối **trước vòng ghi**, nên trong ví dụ nó vẫn giữ `[-1,-1]`.
- Self-append dùng snapshot nên thành `[1,2,3,1,2,3]`, không duyệt một collection đang bị chính vòng lặp `add` làm biến đổi.
- Hai views chồng lấn cho kết quả `[1,1,2,3]` vì copy vào snapshot trước.
- `Set` minh họa **đếm lần xử lý** khác **đếm giá trị mới được thêm**.
- `List.of` có signature phù hợp nhưng không hỗ trợ sửa đổi, nên lỗi thuộc hợp đồng runtime của collection.
- `Collections.copy` thể hiện cách sao chép có sẵn trong JDK với vùng đích đủ lớn.

## 14. Lựa chọn hợp đồng null

Một chữ ký `<T> void copy(...)` **không có nghĩa `T` luôn non-null**. Khi nguồn chứa `null`, việc destination có chấp nhận không phụ thuộc API cụ thể:

```java
List<String> source = new ArrayList<>();
source.add(null);

List<Object> allowNull = new ArrayList<>();
appendAll(source, allowNull); // Có thể chấp nhận null.

List<Object> noNull = new ArrayList<>(List.of("existing"));
// Không suy ra mọi implementation của List cho phép null.
```

Các collection từ factory `List.of` không cho phép null và không cho sửa, trong khi `ArrayList` cho phép null. `Set.of` cũng cấm null theo hợp đồng của nó.

Nếu API sao chép cần cấm null, kiểm tra rõ ràng:

```java
for (T value : source) {
    destination.add(Objects.requireNonNull(value, "element"));
}
```

Nếu cần **chặn hết dữ liệu xấu trước khi ghi bất cứ thứ gì**, phải xác minh/snapshot toàn bộ nguồn trước khi bắt đầu đụng tới đích. Nhưng điều đó không tự giải quyết exception từ `destination.add` hay mutation từ thread khác.

Nếu dữ liệu nghiệp vụ cho phép null, đừng dùng `List.copyOf(source)` như một bước snapshot mà quên rằng factory này từ chối null; `new ArrayList<>(source)` cho phép giữ null trong cấu trúc copy.

## 15. Mutation, partial failure và atomicity

PECS giải quyết **tương thích kiểu**, không phải tính chất giao dịch của hàm copy.

Những lỗi có thể xảy ra:

1. `destination.add` ném `UnsupportedOperationException` vì collection không hỗ trợ thêm.
2. `destination.set` ném exception vì list unmodifiable hoặc kiểm soát từng vị trí.
3. `destination.add` từ chối một phần tử do null/loại giá trị/hợp đồng đặc biệt.
4. Nguồn bị sửa trong lúc đang lặp, có thể gây `ConcurrentModificationException` hoặc dữ liệu không nhất quán tùy implementation.
5. Callback `mapper` ném exception khi xử lý một phần tử nào đó.
6. Các collection giới hạn dung lượng có thể từ chối thêm sau một số lần.
7. Caller cung cấp nguồn vô hạn/lazy iterable; việc snapshot toàn bộ sẽ không kết thúc.

Với `appendAll`, nếu phần tử thứ ba gây lỗi, phần tử thứ nhất và thứ hai **có thể đã được thêm**. `appendSnapshot` chỉ tách giai đoạn **đọc nguồn** khỏi **ghi đích**, không làm các lần `add` thành một transaction atomic.

Với `overwritePrefix`, kiểm tra đủ độ dài trước giúp loại bỏ lỗi **đích quá ngắn ở thời điểm kiểm tra**, nhưng các `set` vẫn có thể thất bại hoặc bị code khác quan sát trạng thái trung gian.

Nếu hệ thống yêu cầu **all-or-nothing**, cần cách tiếp cận khác: xây dựng một collection kết quả riêng rồi thay tham chiếu/commit theo ranh giới đồng bộ thích hợp, dùng transaction của persistence layer hoặc cơ chế ứng dụng cụ thể. Không hứa atomicity chỉ vì có một snapshot local.

## 16. Độ an toàn khi nhiều thread và dữ liệu mutable

Một input có thể là `Collection` mutable; nếu thread khác sửa nó trong khi đọc, kết quả có thể không nhất quán. `new ArrayList<>(source)` hay vòng `for` đưa phần tử vào snapshot **không phải** bảo đảm snapshot atomic cho mọi nguồn concurrent.

Các cách giải quyết tùy yêu cầu:

- Dùng cấu trúc immutable/snapshot đã tạo trước khi bắt đầu thao tác.
- Dùng `synchronized`/lock chung cho **cả người ghi lẫn người copy**, giữ lock đủ lâu để đọc một trạng thái nhất quán.
- Dùng collection đồng thời với semantics iteration được tài liệu hóa, chấp nhận kiểu consistent view của nó khi phù hợp.
- Nếu đích cũng có shared readers/writers, bảo đảm họ đồng bộ với phase ghi hoặc dùng copy-on-write/publish kết quả.

Tính an toàn generic type arguments không giải quyết race condition. Một method `<T>` hợp lệ vẫn có thể không thread-safe.

### Tham chiếu phần tử và deep copy

`appendSnapshot` và `overwritePrefix` chỉ **sao chép tham chiếu phần tử**, không clone các object mutable.

```java
List<StringBuilder> source =
        List.of(new StringBuilder("A"));
List<Object> target = new ArrayList<>();

appendSnapshot(source, target);
source.get(0).append("B");

System.out.println(target.get(0)); // AB
```

Đó là **shallow copy**. Nếu cần giá trị độc lập sâu, phải có mapper/copier do domain cung cấp và xử lý trạng thái mutable bên trong từng phần tử, không thể trông chờ wildcard `? extends T` làm việc ấy.

## 17. Đặt tên tham số và viết Javadoc phù hợp PECS

Nếu là public API, nên mô tả vai trò **source/destination** và ý nghĩa return:

```java
/**
 * Appends every visited source element to destination.
 *
 * <p>The destination must support add operations. This method
 * may partially modify destination before an exception occurs.
 *
 * @param <T> element type transferred from source to destination
 * @param source producer of elements compatible with T
 * @param destination consumer accepting elements of type T
 * @return number of source elements whose destination.add call completed
 * @throws NullPointerException if source or destination is null
 * @throws UnsupportedOperationException if destination rejects an add
 */
static <T> int appendAll(
        Iterable<? extends T> source,
        Collection<? super T> destination) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(destination, "destination");
    int count = 0;
    for (T value : source) {
        destination.add(value);
        count++;
    }
    return count;
}
```

**Phân biệt `@param <T>` với `@param source`:** tag đầu mô tả type parameter; tag sau mô tả formal value parameter. Khi đổi từ processed-count sang accepted-count, phải sửa cả tên và Javadoc; nếu không API có bug về **semantics được hứa**, dù type checking vẫn đúng.

Một design để tránh nhầm thứ tự hai đối số có thể đặt destination trước như `Collections.copy(dest,src)`; một design khác đặt source trước như `appendAll(source,destination)`. Cả hai được nếu tài liệu và tên biến rõ. **Đừng chỉ nhìn hai wildcard mà đoán thứ tự tham số** khi tên method không rõ ràng.

## 18. Kiểm thử đúng compile-time và runtime

Các **positive compile tests** cần có:

```java
List<Integer> integers = List.of(1, 2);
List<Number> numbers = new ArrayList<>();
List<Object> objects = new ArrayList<>();

appendAll(integers, numbers);
appendAll(integers, objects);
```

Một **negative compile test** (đặt trong file riêng cố ý không compile):

```java
List<Number> source = List.of(1, 2.5);
List<Integer> destination = new ArrayList<>();

// KHÔNG BIÊN DỊCH:
appendAll(source, destination);
```

Các **runtime tests** cần kiểm tra:

| Trường hợp | Kỳ vọng |
| --- | --- |
| Nguồn rỗng, đích mutable | Đếm 0; không đổi đích |
| `List<Integer>` → `List<Number>` | Thành công, đúng thứ tự với list |
| `List<Integer>` → `List<Object>` | Thành công |
| `Set` đã có phần tử trùng | Processed-count gồm cả lời gọi `add` trả false |
| List đích unmodifiable | `UnsupportedOperationException` khi copy cố sửa |
| Overwrite với đích đủ vị trí | `size` đích giữ nguyên, các vị trí đầu đổi |
| Overwrite với đích quá ngắn | Exception, được kiểm tra trước vòng `set` |
| Self-append với snapshot | Nối đúng bản chụp của nguồn |
| Hai `subList` chồng lấn | Kết quả dựa trên snapshot nguồn ban đầu |
| Nguồn có null | Theo chính sách null của method và destination |
| Mapper chuyển `Integer` → `Double` | Kết quả tương thích `Collection<Number>` |
| Mapper ném exception | Không hứa rollback các phần tử đã thêm |
| Concurrent mutation | Không mặc định bảo đảm snapshot nhất quán |

Trong JUnit, dùng các phép kiểm tra như `assertEquals`/ `assertThrows` để test **giá trị và exception thực**. Một bài test chỉ nhìn lời gọi compile thành công không chứng minh destination hỗ trợ mutation; ngược lại, một runtime test không thay thế được negative compilation test về type safety.

## 19. Những lỗi thường gặp

**Lỗi 1 — Viết hai `List<T>` vì “đều chứa T”.** Nếu mục đích chỉ đọc nguồn và ghi đích, `? extends T`/`? super T` cho phép subtype/supertype mà invariant `List<T>` không diễn tả được.

**Lỗi 2 — Đảo ngược PECS.** `Collection<? extends T>` không phải kiểu phù hợp để thêm `T` non-null tùy ý; `Iterable<? super T>` không bảo đảm đọc ra `T`.

**Lỗi 3 — Ép `List<Integer>` sang `List<Number>`.** Unchecked cast không biến collection thành kiểu an toàn rộng hơn; dữ liệu `Double` có thể phá hợp đồng `Integer`.

**Lỗi 4 — Tưởng `Collections.copy` tự tăng size của destination.** Nó ghi đè, yêu cầu số vị trí đang có đủ cho source.

**Lỗi 5 — Dùng `new ArrayList<>(n)` và tưởng list có n phần tử.** Constructor này chỉ đặt initial capacity, không đặt size.

**Lỗi 6 — Dùng `addAll` khi muốn ghi đè.** `addAll` thêm phần tử, không thay các vị trí đầu bằng `set`.

**Lỗi 7 — Tưởng typed destination luôn mutable.** `List.of`, unmodifiable views và các implementation khác có thể từ chối `add`/`set` ở runtime.

**Lỗi 8 — Đếm `processed` như số phần tử mới của `Set`.** `add` trả `false` có thể được tính là một lần xử lý thành công nhưng không làm size tăng.

**Lỗi 9 — Copy trực tiếp giữa các views chồng lấn mà không xét alias.** Cần snapshot hoặc thuật toán có thứ tự đọc–ghi được chứng minh an toàn.

**Lỗi 10 — Cho nguồn và đích là một collection khi vòng for-each vừa đọc vừa thêm.** Có thể lỗi concurrent modification hoặc semantics không mong muốn; snapshot trước nếu muốn hỗ trợ tình huống đó.

**Lỗi 11 — Tưởng snapshot tạo deep copy.** Nó chỉ giữ các references, không clone object phần tử.

**Lỗi 12 — Tưởng snapshot tự tạo atomic transaction.** Exception khi ghi đích vẫn có thể làm đích thay đổi một phần; concurrent writes cũng cần đồng bộ riêng.

**Lỗi 13 — Tưởng generic `T` luôn non-null.** Chính sách null nằm ở method và collection, không được type parameter tự bảo đảm.

**Lỗi 14 — Dùng raw types để cho phép các loại collection khác nhau.** API wildcard typed đã diễn đạt đúng nhu cầu mà không bỏ compiler checks.

**Lỗi 15 — Quên `? super S`/`? extends R` khi viết mapper.** Callback cần nhận source và sản xuất result; PECS áp dụng cả ở type arguments của `Function`.

## 20. Bài tập tự luyện

**Bài 1.** Viết `<T> int appendAll(Iterable<? extends T>, Collection<? super T>)`. Nêu chính xác ý nghĩa `int` nếu đích là `Set`.

**Gợi ý:** đếm sau khi `add` hoàn tất là “số phần tử đã xử lý”, không phải số phần tử mới nếu `add` trả `false`.

**Bài 2.** Cho `List<Integer> source` và `List<Number> target`, hãy gọi method mà không cast; thử `List<Object>` làm target. Giải thích vì sao cùng hợp lệ.

**Đáp án cốt lõi:** target là consumer của những phần tử nguồn; `Number` và `Object` đều nhận được `Integer`.

**Bài 3.** Cho `List<Number> source` chứa `Integer` và `Double`, và `List<Integer> target`. Vì sao không thể copy toàn bộ giữ nguyên kiểu?

**Đáp án:** target không nhận mọi `Number`. Nếu nghiệp vụ yêu cầu chuyển `Double` thành `Integer`, phải viết mapper/validator có chính sách rõ, không dùng unchecked cast.

**Bài 4.** `Collections.copy(new ArrayList<>(10), List.of(1,2))` có chạy thành công không?

**Đáp án:** không, nếu destination lúc gọi vẫn có size 0; capacity 10 không đồng nghĩa có 10 vị trí.

**Bài 5.** `Arrays.asList(0,0)` và `List.of(0,0)` khác nhau ra sao khi dùng làm đích của overwrite?

**Đáp án:** `Arrays.asList` hỗ trợ `set` nhưng không thay đổi kích thước; `List.of` không hỗ trợ mutation, kể cả `set`.

**Bài 6.** Khi source và destination là hai `subList` của cùng một list, vì sao `overwritePrefix` sao chép nguồn trước?

**Đáp án:** tránh các lệnh ghi vào destination thay đổi phần tử trong source mà vòng lặp còn chưa đọc.

**Bài 7.** Nếu `appendSnapshot(source, dest)` gặp lỗi trong `dest.add` ở phần tử cuối, có rollback các phần tử trước không?

**Đáp án:** không. Snapshot chỉ tách đọc nguồn khỏi ghi đích, không biến thao tác thành transaction.

**Bài 8.** `Collection<? super Integer>` đọc ra kiểu gì an toàn nếu không có thông tin thêm?

**Đáp án:** `Object`. Compiler không biết tham chiếu hiện giữ `Collection<Integer>`, `Collection<Number>` hay `Collection<Object>`.

**Bài 9.** Thiết kế helper chuyển giá trị `String` thành `Integer` rồi append vào `Collection<Number>`.

**Gợi ý:** dùng `mapAppend(strings, numbers, Integer::parseInt)`, với `Function<? super S,? extends R>`; cân nhắc `NumberFormatException` và partial writes.

**Bài 10.** Bổ sung một biến thể `tryAppendAll` báo số lần `Collection.add` trả `true`, phân biệt với số lần đã duyệt. Viết test với `LinkedHashSet` có trùng key.

## 21. Checklist trước khi công bố API sao chép

1. **Ý định là append hay overwrite?** Tên method, logic và Javadoc phải thống nhất.
2. **PECS đặt đúng chiều chưa?** Nguồn `? extends T`, đích `? super T`.
3. **Đã chọn abstraction tối thiểu?** Chỉ cần iterator thì `Iterable`; cần `set(i)` thì `List`.
4. **Có thực sự cần một `<T>` được đặt tên không?** Khi phải nối quan hệ nguồn–đích, có.
5. **Có tránh raw casts không?** Nếu cần conversion, dùng mapper rõ ngữ nghĩa.
6. **Đích cần sẵn size hay tự tăng size?** Đây là điểm khác biệt chính giữa `Collections.copy` và append.
7. **Return value đếm gì?** Processed, accepted, inserted, hay old values?
8. **Có kiểm tra null source/dest/mapper?** Chính sách null element nằm ở đâu?
9. **Có hỗ trợ self-copy/overlap không?** Nếu có, snapshot/thuật toán đã được chứng minh chưa?
10. **Có hứa atomicity không?** Nếu không, nêu khả năng partial mutation khi lỗi.
11. **Destination có thể không hỗ trợ modification?** Có nêu `UnsupportedOperationException` theo hợp đồng?
12. **Với `Set`, có phân biệt số phần tử xử lý và size tăng không?**
13. **Có concurrency policy không?** Snapshot O(n) không tự bảo đảm thread safety.
14. **Có kiểm tra nested mutable values không?** Shallow copy không clone trạng thái object.
15. **Có test compile-time sai chiều type không?** Ví dụ `List<Number>` → `List<Integer>` phải bị từ chối.
16. **Có test runtime size, alias, null, unsupported operations và mapper exceptions không?**
17. **Tài liệu có nêu rõ yêu cầu về order, duplicates và hiệu năng theo implementation không?**

## 22. Kết luận

**PECS là cách biểu diễn chiều dịch chuyển dữ liệu ở cấp kiểu.** Khi viết hàm sao chép từ nguồn sang đích, hãy bắt đầu bằng **`Iterable<? extends T>` cho nơi đọc** và **`Collection<? super T>` cho nơi ghi**, hoặc với thao tác theo vị trí, dùng **`List<? extends T>` và `List<? super T>`**. Các wildcard giúp Java cho phép truyền phần tử kiểu con sang container kiểu cha mà vẫn được compiler kiểm tra.

Nhưng một hàm `copy` tốt còn phải nói rõ **nó append hay overwrite**, có **yêu cầu đủ chỗ**, có **xử lý overlapping views**, đếm **số phần tử đã duyệt hay thật sự thêm**, và có các **hợp đồng null, mutation, error, alias, concurrency** ra sao. `Collections.copy` là một ví dụ PECS chuẩn cho việc ghi đè; `appendAll`, `appendSnapshot`, `overwritePrefix` và `mapAppend` minh họa cách thiết kế các biến thể đúng semantics mà không cần raw types hay unchecked casts.

Hãy để **chữ ký thể hiện tính an toàn kiểu**, **tên method thể hiện thao tác**, và **Javadoc/tests chứng minh các hành vi runtime**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-integrated-practice-004">Sửa lỗi wildcard bằng phương thức bắt giữ</a>

<details>
<summary>Xem chi tiết</summary>

# Sửa lỗi wildcard bằng phương thức bắt giữ trong Java Generics

## Mục tiêu học tập

Khi làm việc với một danh sách `List<?>`, Java cho phép đọc phần tử nhưng thường không cho đưa chính phần tử vừa đọc trở lại danh sách bằng lời gọi `set` hoặc `add` trực tiếp. Một thông báo dễ gặp là `incompatible types: Object cannot be converted to CAP#1`. Đây là tình huống thực hành tiêu biểu của **wildcard capture**: compiler biết dấu `?` đại diện cho một kiểu cụ thể nào đó, nhưng một biểu thức thông thường không thể tùy ý gán giá trị trở lại vị trí đòi hỏi đúng kiểu chưa biết ấy.

Bài này tập trung vào cách viết **phương thức helper generic** để bắt giữ cùng một kiểu trong một lần gọi, thực hiện phép toán an toàn và giữ giao diện công khai đơn giản.

Sau bài, bạn có thể:

- Đọc và lý giải `CAP#1`, `capture of ?` và giới hạn của thao tác ghi trên `List<?>`.
- Viết helper dạng `private static <T> void helper(List<T> list)` và gọi nó từ phương thức nhận `List<?>`.
- Thực hiện đổi chỗ, xoay và sao chép phần tử **trong cùng một collection** mà không dùng raw type hay unchecked cast.
- Phân biệt trường hợp **một wildcard được bắt giữ** với **hai wildcard độc lập** không có bằng chứng cùng kiểu.
- Quyết định khi nào dùng capture helper, `? extends T`, `? super T`, generic method có tên T, hoặc API thư viện như `Collections.swap`.

## 1. Vấn đề: vì sao `List<?>` không cho ghi lại phần tử?

Xét một danh sách với loại phần tử không biết trước:

```java
static void rotateWrong(java.util.List<?> values) {
    values.set(0, values.get(1)); // Không biên dịch.
}
```

Đây là **mã lỗi có chủ đích**. Nếu list chứa `String`, nhìn bằng mắt thì lấy một phần tử của chính list rồi ghi vào vị trí khác phải an toàn. Vấn đề nằm ở cách compiler kiểm tra kiểu của từng biểu thức.

- `List<?>` không đồng nghĩa `List<Object>`. Dấu `?` biểu diễn một kiểu phần tử cụ thể **chưa biết**.
- Đọc qua `values.get(1)` có thể được dùng dưới dạng `Object`, bởi mọi kiểu tham chiếu hợp lệ đều là subtype của Object.
- Ghi qua `values.set(0, ...)` lại đòi hỏi giá trị có **đúng kiểu phần tử chưa biết ấy**, chứ không chỉ là một Object.
- Phương thức bên ngoài không đặt được tên cho kiểu capture để giữ quan hệ từ giá trị đọc đến giá trị ghi.

Vì vậy compiler không chứng minh được biểu thức truyền cho `set` thuộc đúng capture type mà `set` yêu cầu. Với `javac --release 17`, một thông báo lỗi có dạng:

```text
error: incompatible types: Object cannot be converted to CAP#1
where CAP#1 is a fresh type-variable:
  CAP#1 extends Object from capture of ?
```

`CAP#1` là **tên chẩn đoán nội bộ compiler** dành cho kiểu chưa biết được tạo khi thực hiện *capture conversion*. Nó không phải tên class runtime, không phải kiểu bạn có thể khai báo bằng Java, và số `#1` không mang ý nghĩa thiết kế cố định.

### Wildcard không có nghĩa mọi phần tử đều là Object

Ví dụ:

```java
java.util.List<String> words = new java.util.ArrayList<>();
java.util.List<?> view = words;

// view.add("A");  // Không biên dịch: chưa biết kiểu phần tử.
// view.add(42);   // Không biên dịch.
// view.add(null); // Hợp lệ về hệ thống kiểu, nếu list hỗ trợ sửa và null.
Object value = view.isEmpty() ? null : view.get(0);
```

Hai tham chiếu cùng chỉ tới một đối tượng, nhưng `view` cố tình giấu kiểu phần tử. Compiler chặn việc thêm giá trị tùy ý để bảo vệ một danh sách có thể thực sự là `List<String>`, `List<Integer>` hay `List<MyType>`.

Một điểm cần giữ riêng: **kiểm tra kiểu và khả năng thay đổi collection là hai vấn đề khác nhau**. Dù một thao tác đúng kiểu, một `List.of(...)` không sửa được vẫn có thể ném `UnsupportedOperationException` khi `set`/`add`.

## 2. Capture conversion làm gì?

Khi phương thức khai báo tham số `List<?>`, compiler có thể diễn giải dấu `?` tạm thời thành một **kiểu capture duy nhất** đối với một biểu thức, ví dụ `CAP#1 extends Object`. Đó là cách compiler theo dõi một kiểu ẩn trong quá trình suy luận và kiểm tra generic.

Giả sử có:

```java
static void consume(java.util.List<?> values) {
    // Tại điểm dùng, có thể nhìn values như List<CAP#1>.
}
```

Dòng chú thích là sơ đồ ý niệm; `CAP#1` không phải cú pháp Java hợp lệ. Từ List có phần tử kiểu CAP#1, compiler cho phép chuyển kết quả đọc ra `Object`. Nhưng khi chỉ có một biểu thức ở kiểu `Object`, compiler không thể dùng nó như `CAP#1`.

Một phương thức generic có **tham số kiểu được đặt tên** sẽ giữ được quan hệ này:

```java
private static <T> void replaceFirstWithSecond(java.util.List<T> values) {
    if (values.size() >= 2) {
        T second = values.get(1);
        values.set(0, second);
    }
}
```

Ở đây:

- `List<T>.get(1)` trả về đúng `T`.
- `List<T>.set(0, T)` nhận lại đúng `T`.
- Suy luận kiểu gắn `T` với một capture của wildcard khi gọi helper.
- Không có cast không an toàn, không có raw type.

Từ bên ngoài có thể viết:

```java
static void replaceFirstWithSecond(java.util.List<?> values) {
    replaceFirstWithSecondCaptured(values);
}

private static <T> void replaceFirstWithSecondCaptured(
        java.util.List<T> values) {
    if (values.size() >= 2) {
        T second = values.get(1);
        values.set(0, second);
    }
}
```

Các phương thức trên là **mẫu khai báo đặt trong cùng một lớp**, tên helper cố ý khác tên public để trình bày. Compiler suy ra `T` bằng kiểu capture của list truyền vào. Phần thân helper chỉ thao tác với phần tử lấy từ **chính list ấy**, nên quan hệ kiểu được bảo toàn.

Không cần tự khai báo kiểu `CAP#1`, không cần cast và không cần biết trước list chứa `String` hay `Integer`.

## 3. Quy trình sửa từng bước

Giả sử bạn bắt đầu từ chức năng “đổi phần tử đầu và cuối của bất kỳ danh sách nào”:

**Bước 1 — Viết chữ ký công khai theo nhu cầu.**

```java
static void swapEnds(java.util.List<?> values) {
    // Chấp nhận List<String>, List<Integer>, List<MyType>, ...
}
```

`List<?>` hợp lý vì caller không cần truyền thêm hay nhận về một giá trị có kiểu T cụ thể.

**Bước 2 — Không cố thực hiện phép ghi khó suy luận trực tiếp.**

```java
// SAI, chỉ minh họa:
// Object first = values.get(0);
// values.set(0, values.get(values.size() - 1));
// values.set(values.size() - 1, first);
```

Biến `Object first` đã đánh mất quan hệ với kiểu phần tử chưa biết. Hai lời gọi `set` đều gặp khó khăn về capture.

**Bước 3 — Chuyển thao tác sang helper generic.**

```java
static void swapEnds(java.util.List<?> values) {
    if (values.size() < 2) {
        return;
    }
    swapCaptured(values, 0, values.size() - 1);
}

private static <T> void swapCaptured(
        java.util.List<T> values, int left, int right) {
    T first = values.get(left);
    values.set(left, values.get(right));
    values.set(right, first);
}
```

Hai phương thức trên là đoạn có thể đặt trong một class để biên dịch. Khi truyền `List<?>` cho `swapCaptured`, compiler thực hiện capture conversion và suy luận một `T` thống nhất cho toàn bộ thao tác bên trong helper.

**Bước 4 — Giữ helper ở phạm vi hẹp.** Nên để helper `private` nếu chỉ phục vụ phương thức công khai. Bề mặt API vẫn dễ đọc: caller chỉ thấy `swapEnds(List<?>)`.

**Bước 5 — Kiểm tra điều kiện runtime.** List rỗng/ít hơn hai phần tử nên bỏ qua; list không sửa được sẽ ném `UnsupportedOperationException`; chỉ số sai có thể ném `IndexOutOfBoundsException`. Capture helper **chỉ giải quyết kiểu generic**, không tự sửa các điều kiện runtime.

## 4. Ví dụ Java 17 hoàn chỉnh: swap, rotate, duplicate và PECS

Chương trình sau minh họa ba thao tác thay đổi dữ liệu **trong cùng một `List<?>`** bằng helper capture, rồi so sánh với sao chép từ một danh sách sang danh sách khác bằng `extends`/`super`.

**Tệp `WildcardCaptureLab.java`:**

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WildcardCaptureLab {
    static void swapEnds(List<?> values) {
        if (values.size() < 2) return;
        swapCaptured(values, 0, values.size() - 1);
    }

    private static <T> void swapCaptured(List<T> values, int left, int right) {
        T previous = values.get(left);
        values.set(left, values.get(right));
        values.set(right, previous);
    }

    static void rotateRight(List<?> values) {
        rotateRightCaptured(values);
    }

    private static <T> void rotateRightCaptured(List<T> values) {
        if (values.size() < 2) return;
        T last = values.get(values.size() - 1);
        for (int i = values.size() - 1; i > 0; i--) {
            values.set(i, values.get(i - 1));
        }
        values.set(0, last);
    }

    static void duplicateFirst(List<?> values) {
        duplicateFirstCaptured(values);
    }

    private static <T> void duplicateFirstCaptured(List<T> values) {
        if (!values.isEmpty()) values.add(values.get(0));
    }

    static <T> void appendAll(
            List<? super T> destination,
            List<? extends T> source) {
        destination.addAll(source);
    }

    public static void main(String[] args) {
        List<String> words = new ArrayList<>(List.of("A", "B", "C"));
        List<?> view = words;
        swapEnds(view);
        System.out.println("swap=" + words);
        rotateRight(view);
        System.out.println("rotate=" + words);
        duplicateFirst(view);
        System.out.println("duplicate=" + words);

        List<Integer> integers = new ArrayList<>(List.of(1, 2));
        List<Number> numbers = new ArrayList<>(List.of(0.5));
        appendAll(numbers, integers);
        System.out.println("append=" + numbers);

        List<?> fixed = List.of("x", "y");
        try {
            swapEnds(fixed);
        } catch (UnsupportedOperationException exception) {
            System.out.println("immutable=" + exception.getClass().getSimpleName());
        }

        Collections.swap(words, 0, 1);
        System.out.println("library=" + words);
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac --release 17 -Xlint:all WildcardCaptureLab.java
java WildcardCaptureLab
```

**Stdout chính xác đã được kiểm chứng:**

```text
swap=[C, B, A]
rotate=[A, C, B]
duplicate=[A, C, B, A]
append=[0.5, 1, 2]
immutable=UnsupportedOperationException
library=[C, A, B, A]
```

Chương trình biên dịch bằng `javac --release 17 -Xlint:all` mà không phát sinh warning trong ví dụ đã kiểm tra. Các helper generic thực hiện thao tác có quan hệ kiểu rõ ràng.

### 4.1. `swapEnds` giữ đúng kiểu phần tử

Với `List<String>`, sau lời gọi `swapEnds(view)`, hai phần tử đầu và cuối đổi chỗ: `[A, B, C]` trở thành `[C, B, A]`.

Với `List<Integer>` cũng vậy: helper có thể nhận một capture khác và suy luận `T` tương ứng trong **lần gọi khác**. Không có chuyện helper lưu một “T toàn cục” bị cố định thành String sau lần gọi đầu tiên.

### 4.2. `rotateRight` cần giữ nhiều lần đọc/ghi cùng T

Trong `rotateRightCaptured`:

- `T last = values.get(size - 1)` giữ phần tử cuối dưới đúng kiểu `T`.
- Vòng lặp lần lượt di chuyển phần tử tại `i - 1` sang `i`.
- `values.set(0, last)` chuyển phần tử cuối về đầu.

Sau khi swap có `[C, B, A]`, xoay phải tạo `[A, C, B]`. Nếu list có ít hơn hai phần tử, phương thức giữ nguyên nội dung và tránh truy cập chỉ số ngoài phạm vi.

Cơ chế quan trọng không nằm ở thuật toán xoay, mà là mọi phép đọc/ghi nội bộ đều dùng **một T nhất quán**.

### 4.3. `duplicateFirst` có thể thêm phần tử đọc từ cùng list

Với `List<?>`, `view.add(view.get(0))` không biên dịch. Nhưng trong helper:

```java
private static <T> void duplicateFirstCaptured(List<T> values) {
    if (!values.isEmpty()) {
        values.add(values.get(0));
    }
}
```

kiểu `T` của phần tử đọc là đúng kiểu mà `add(T)` yêu cầu. Do đó có thể nhân đôi phần tử đầu: `[A, C, B]` thành `[A, C, B, A]`.

Điều này **không cho phép thêm một Object tùy ý**; giá trị phải là T, ở đây được lấy trực tiếp từ list đang được bắt giữ.

### 4.4. `appendAll` dùng PECS giữa hai danh sách có quan hệ kiểu

Trong `appendAll`, nguồn có kiểu `List<? extends T>` nên có thể **đọc T** từ danh sách nguồn. Đích có kiểu `List<? super T>` nên có thể **ghi T** vào danh sách đích.

Vì `Integer extends Number`, có thể truyền `List<Integer>` làm nguồn và `List<Number>` làm đích. Khi đó số `1` và `2` được thêm sau số `0.5`, tạo `[0.5, 1, 2]`.

Điểm khác với capture của một `List<?>` là **quan hệ giữa hai container**: API phải bảo đảm phần tử từ nguồn có thể được thêm an toàn vào đích. `extends`/`super` mô tả trực tiếp quan hệ ấy, trong khi hai wildcard `?` độc lập không tự biểu đạt nó.

### 4.5. Capture không làm collection bất biến trở nên sửa được

`List.of("x", "y")` là danh sách không sửa được. `swapEnds(fixed)` vẫn suy luận kiểu T thành công ở compile time; nhưng tại runtime `set` thất bại và ném `UnsupportedOperationException`.

Đây không phải lỗi capture. Generic type safety và mutability là hai vấn đề tách biệt. Phương thức có thể yêu cầu caller cung cấp danh sách sửa được theo hợp đồng, hoặc tạo một bản sao:

```java
List<String> copy = new ArrayList<>(List.of("x", "y"));
swapEnds(copy);
```

Đoạn ngắn này nằm trong ngữ cảnh đã import `List`/`ArrayList` và có phương thức `swapEnds`.

### 4.6. Thư viện sẵn có có thể thay helper tự viết

`Collections.swap(List<?> list, int i, int j)` đã hỗ trợ wildcard cho thao tác đổi chỗ. Nếu bài toán chỉ là swap, có thể gọi API này thay vì tự duy trì helper riêng.

Tương tự, `Collections.reverse(List<?>)` đảo danh sách mà không yêu cầu caller biết kiểu phần tử. Trước khi tạo capture helper phức tạp, hãy kiểm tra liệu thư viện chuẩn đã có phương thức thực hiện đúng thao tác hay chưa.

## 5. Ví dụ sai: lỗi `CAP#1` và cách đọc

**Tệp `InvalidCapture.java` — cố ý không biên dịch:**

```java
import java.util.List;

class InvalidCapture {
    static void rotate(List<?> values) {
        values.set(0, values.get(1));
    }

    static void move(List<?> source, List<?> target) {
        target.add(source.get(0));
    }
}
```

Lệnh thử:

```bash
javac --release 17 InvalidCapture.java
```

`javac` từ chối hai câu lệnh. Phần thông báo cốt lõi (đã kiểm chứng):

```text
error: incompatible types: Object cannot be converted to CAP#1
where CAP#1 is a fresh type-variable:
  CAP#1 extends Object from capture of ?
```

Thông báo tương tự xuất hiện ở hai vị trí nhưng **ý nghĩa cần phân tích khác nhau**:

- `values.set(0, values.get(1))`: phần tử lấy từ **chính cùng list**; về bản chất có thể an toàn. Đây là ứng viên tốt để dùng helper capture với một T.
- `target.add(source.get(0))`: phần tử từ **source** được đưa vào một **target độc lập**, cả hai chỉ có kiểu `List<?>`. Không có bằng chứng chúng chứa cùng loại phần tử. Việc viết một helper một T không thể tự hợp thức hóa phép chuyển này.

Vì vậy, không nên hiểu mọi lỗi `CAP#1` đều sửa được bằng cùng một thao tác mà không thay đổi hợp đồng. Hãy hỏi: **Hai vị trí đọc/ghi có thực sự thuộc cùng một kiểu?** Nếu có, capture helper giúp trình biên dịch diễn đạt được điều đó. Nếu không, phải bổ sung thông tin kiểu hoặc kiểm tra thực tế.

## 6. Hai wildcard độc lập là hai capture riêng

Xét đoạn minh họa:

```java
static void move(List<?> source, List<?> destination) {
    moveCaptured(source, destination);
}

private static <T> void moveCaptured(
        List<T> source, List<T> destination) {
    if (!source.isEmpty()) {
        destination.add(source.get(0));
    }
}
```

Đoạn này **không biên dịch** khi gọi `moveCaptured` bằng hai `List<?>` độc lập. Với `javac --release 17`, lỗi suy luận có dạng:

```text
required: List<T>,List<T>
found:    List<CAP#1>,List<CAP#2>
reason: inference variable T has incompatible equality constraints CAP#2,CAP#1
```

Giải thích:

- `source` có một kiểu phần tử chưa biết, tạm gọi `CAP#1`.
- `destination` có một kiểu phần tử chưa biết độc lập, tạm gọi `CAP#2`.
- Việc cả hai cùng viết `List<?>` không chứng minh `CAP#1` bằng `CAP#2`.
- Helper yêu cầu **cùng T**, nên lời gọi không được chấp nhận.

Ví dụ runtime hoàn toàn có thể là `source = List<String>` và `destination = List<Integer>`. Nếu compiler cho sao chép tùy ý, dữ liệu của đích sẽ bị trộn sai kiểu.

Điểm tinh tế: ngay cả nếu bạn *biết* hai tham chiếu tình cờ trỏ vào cùng một loại danh sách trong một tình huống cụ thể, chữ ký `move(List<?>, List<?>)` vẫn không diễn đạt quan hệ giữa chúng. Java kiểm tra theo hợp đồng kiểu có thể chứng minh tại compile time, không chấp nhận một suy đoán dựa trên dữ liệu mẫu.

### Ba cách thiết kế đúng cho hai collection

**Cách 1 — Caller biết cùng một T:**

```java
static <T> void moveSameType(List<T> source, List<T> target) {
    if (!source.isEmpty()) {
        target.add(source.get(0));
    }
}
```

Phương thức nên nằm trong một lớp có import `java.util.List`. Caller truyền hai danh sách thực sự có cùng kiểu T.

**Cách 2 — Quan hệ subtype/supertype (PECS):**

```java
static <T> void copyInto(
        List<? extends T> source,
        List<? super T> target) {
    target.addAll(source);
}
```

Mẫu này cho phép chuyển phần tử từ `List<Integer>` sang `List<Number>`, hoặc từ `List<String>` sang `List<Object>`. API đặt ra quan hệ kiểu cần thiết.

**Cách 3 — Dữ liệu không rõ kiểu từ nguồn ngoài:** Nhận `List<?>` và một token `Class<T>` để xác thực từng phần tử trước khi thêm vào `List<T>`.

```java
static <T> void appendValidated(
        List<?> source, List<T> target, Class<T> type) {
    for (Object value : source) {
        target.add(type.cast(value));
    }
}
```

Phương thức này có thể thay đổi target một phần nếu dữ liệu lỗi xuất hiện giữa quá trình. Nếu cần **all-or-nothing**, trước tiên kiểm tra và sao chép vào một danh sách tạm typed rồi mới `target.addAll(validated)`. Class token chỉ kiểm tra các kiểu runtime biểu diễn được, như `String.class`; với phần tử generic lồng nhau cần xác thực sâu hơn.

## 7. Chọn wildcard hay type variable cho API?

Một nguyên tắc thường hữu ích: **dùng wildcard khi kiểu cụ thể không liên quan tới hợp đồng công khai**, còn **dùng type variable có tên khi cần diễn đạt quan hệ giữa nhiều vị trí**.

So sánh:

| Ý định | Chữ ký thích hợp | Vì sao |
| --- | --- | --- |
| Chỉ xem số phần tử | `int size(List<?> values)` | Không cần tên T. |
| Duyệt và in bất kỳ phần tử nào | `void print(List<?> values)` | Đọc dưới dạng Object là đủ. |
| Đổi vị trí phần tử trong cùng một danh sách | `void swap(List<?> values, ...)` cùng helper `<T>` | Public API đơn giản; helper giữ quan hệ đọc/ghi. |
| Trả về chính phần tử đầu dưới kiểu caller biết | `<T> T first(List<T> values)` | T liên hệ kiểu phần tử với kiểu trả về. |
| Sao chép nguồn vào đích có kiểu rộng hơn | `<T> void copy(List<? extends T>, List<? super T>)` | Hai collection có quan hệ kiểu rõ ràng. |
| Thêm Integer vào một sink có thể chứa Number/Object | `void addInt(List<? super Integer> sink)` | Đích nhận Integer, không cần capture helper. |
| Đọc Number từ danh sách các subtype của Number | `void consume(List<? extends Number> source)` | Đọc Number an toàn; không ghi Number tùy ý. |

Nếu bạn có:

```java
static <T> void printAny(List<T> values) {
    for (T element : values) {
        System.out.println(element);
    }
}
```

mà T chỉ xuất hiện một lần trong chữ ký và không cần quan hệ khác, thường có thể đơn giản hóa thành `printAny(List<?> values)`. Tuy nhiên, **không phải cứ T xuất hiện một lần là luôn thừa**: hãy xét toàn bộ thân hàm, overload resolution và kiểu trả về. Khi T cần thiết để giữ dữ liệu qua nhiều thao tác, generic helper vẫn có giá trị.

## 8. Wildcard với bound `extends` và `super`

Capture helper không giới hạn ở wildcard không bound.

### 8.1. `? extends Number`

Nếu hàm nhận `List<? extends Number>`, phần tử đọc được dưới dạng Number:

```java
static double sum(List<? extends Number> values) {
    double total = 0;
    for (Number value : values) {
        total += value.doubleValue();
    }
    return total;
}
```

Đây là phương thức minh họa bên trong lớp, với import `List`. Vì không cần ghi, **không cần helper**.

Nếu nhiệm vụ là lấy một phần tử từ danh sách và thêm lại vào **chính danh sách đó**, có thể viết:

```java
static void duplicateNumber(List<? extends Number> values) {
    duplicateNumberCaptured(values);
}

private static <T extends Number> void duplicateNumberCaptured(
        List<T> values) {
    if (!values.isEmpty()) {
        values.add(values.get(0));
    }
}
```

Chữ ký helper giữ được T trong giới hạn Number; compiler có thể bắt giữ kiểu phần tử thực từ wildcard extends Number. Việc thêm một `Number` bất kỳ vào List có phần tử chưa biết (có thể là Integer) vẫn không được phép.

### 8.2. `? super Integer`

Một danh sách `List<? super Integer>` là **consumer** có thể nhận Integer:

```java
static void addInteger(List<? super Integer> destination) {
    destination.add(10);
}
```

Chữ ký này thích hợp khi caller truyền `List<Integer>`, `List<Number>` hoặc `List<Object>`. Khi đọc từ một danh sách `List<? super Integer>`, không thể tự suy ra kết quả là Integer; kiểu có thể đọc an toàn nói chung là Object.

Bạn không cần capture helper để thêm Integer vì bound `super Integer` đã bảo đảm phép ghi.

### 8.3. Capture helper không thay thế nguyên tắc PECS

**Producer Extends, Consumer Super** là cách nghĩ hữu ích khi API có một phía tạo/đọc phần tử và một phía nhận/ghi phần tử. Capture helper xử lý tình huống khác: **cùng một container có kiểu chưa biết nhưng cần thao tác nội bộ nhất quán**.

Không nên bắt mọi API về `List<?>` rồi cố dùng helper phức tạp. Nếu biết rõ loại phần tử cần ghi từ bên ngoài, hãy biểu đạt trực tiếp bằng `List<T>` hoặc `List<? super T>`.

## 9. Quyết định dùng Collections API hay tự viết helper capture

Trước khi tự viết helper `<T>`, hãy hỏi **thao tác mong muốn đã có trong thư viện chuẩn chưa**. Những phương thức dưới đây nhận một list với element type bất kỳ và thực hiện thay đổi vị trí các giá trị *đã có*, không cần caller đoán element type:

| Ý định | API sẵn có | Rủi ro cần xem xét |
|---|---|---|
| Đổi chỗ hai phần tử | `Collections.swap(List<?>, i, j)` | Chỉ số hợp lệ, list hỗ trợ `set` |
| Đảo thứ tự | `Collections.reverse(List<?>)` | List có cho sửa không |
| Xoay theo khoảng cách | `Collections.rotate(List<?>, distance)` | Có thể thay toàn bộ vị trí |
| Xáo trộn | `Collections.shuffle(List<?>)` | Kết quả ngẫu nhiên, không hứa một stdout cố định |

Ví dụ đặt trong method với `import java.util.*`:

```java
List<?> letters = new ArrayList<>(List.of("A", "B", "C"));
Collections.rotate(letters, 1);
System.out.println(letters); // [C, A, B]
```

`List<?>` không cho thêm một `String` tùy ý qua tham chiếu `letters`, nhưng **không cấm API hợp lệ hoán chuyển các phần tử trong cùng list**. Quan hệ kiểu được bảo toàn vì thao tác chỉ thay vị trí những giá trị vốn thuộc chính list đó.

Nếu thuật toán cần giữ một phần tử qua nhiều lần `get`/`set`, không có sẵn trong thư viện, wrapper `List<?>` kết hợp helper `<T> void operation(List<T>)` là lựa chọn rõ ràng. Nếu cần chuyển dữ liệu **giữa hai collections có element types khác nhau**, cần xác định quan hệ PECS, không giả định hai capture `?` độc lập tương thích.

Chú ý: `Collections.sort` còn yêu cầu yếu tố so sánh thích hợp, **không phải** thao tác có thể áp dụng cho mọi `List<?>` giống `reverse`. Các thao tác thay đổi list vẫn có thể ném `UnsupportedOperationException` nếu caller đưa `List.of(...)`; capture conversion không thay thế hợp đồng mutability.

## 10. Lỗi hay gặp khi sửa `capture of ?`

| Cách sửa sai hoặc thiếu | Vấn đề | Cách sửa tốt hơn |
| --- | --- | --- |
| Đổi `List<?>` thành raw `List` | Mất kiểm tra generic, có thể đưa sai kiểu vào danh sách. | Dùng helper `<T>` hoặc điều chỉnh hợp đồng với PECS. |
| Ép `(List<Object>) values` | Cast generic không chứng minh loại phần tử, có thể là unchecked và làm hỏng dữ liệu. | Giữ capture bằng `List<T>` trong helper. |
| Dùng `Object` để tạm giữ phần tử rồi `set` | `Object` có thể rộng hơn kiểu capture cần nhận. | Biến cục bộ `T first` trong helper generic. |
| Gọi helper với hai `List<?>` bất kỳ | Hai wildcard tạo hai capture riêng, không chứng minh cùng T. | Dùng `<T>` ở chữ ký caller hoặc `extends/super`. |
| Cho rằng capture helper khiến `List.of` sửa được | Generic typing không thay đổi mutability của object. | Truyền list mutable hoặc tạo bản sao. |
| Suy ra `?` luôn là Object | Wildcard đại diện kiểu chưa biết; Object chỉ là kiểu đọc an toàn phổ biến. | Giữ sự khác biệt giữa đọc Object và ghi capture. |
| Cho rằng `CAP#1` là một class thật | Nó là tên compiler dùng khi báo lỗi suy luận. | Đọc ràng buộc capture, xác định các vị trí liên quan. |
| Suppress warning thay vì thay đổi chữ ký | Che dấu rủi ro type safety, không thiết lập quan hệ kiểu. | Thiết kế lại API và kiểm chứng bằng compile. |
| Tạo helper quá công khai | Bề mặt API lộ chi tiết kỹ thuật không cần thiết. | Giữ helper private nếu chỉ dùng nội bộ. |

### Cảnh báo về ép kiểu

Không nên sửa bằng một đoạn có raw cast như:

```java
// KHÔNG NÊN DÙNG:
@SuppressWarnings({"unchecked", "rawtypes"})
static void unsafeRotate(List<?> values) {
    List raw = values;
    raw.set(0, raw.get(1));
}
```

Đoạn này có thể biên dịch, nhưng dùng raw type để loại bỏ kiểm tra kiểu. Dù thao tác cụ thể đang lấy và ghi phần tử trong cùng danh sách, API không còn cơ chế compile-time buộc bạn chỉ ghi giá trị tương thích ở những thay đổi tiếp theo. Helper generic làm rõ bất biến: đầu vào và đầu ra nội bộ đều là T.

## 11. Thực hành với compile error và kiểm chứng

Khi gặp thông báo:

```text
incompatible types: Object cannot be converted to CAP#1
```

hãy làm theo trình tự:

1. **Xem câu lệnh đang ghi vào đâu:** `List<?>.add`, `List<?>.set` hoặc phương thức khác nhận tham số capture.
2. **Xác định đối tượng được lấy từ đâu:** cùng list, list khác hay nguồn dữ liệu bất kỳ.
3. **Nếu cùng list**, thử đặt thao tác trong helper `<T> void helper(List<T> values)`; chuyển cả logic đọc và ghi vào cùng helper.
4. **Nếu khác list**, đừng dùng một T tùy ý chỉ để compiler hết lỗi. Hãy bổ sung quan hệ kiểu: `List<T>` ở cả hai phía, hoặc `<? extends T>`/`<? super T>`.
5. **Chạy `javac` trước** khi viết thêm cast. Nếu helper đúng, nó thường không cần `@SuppressWarnings("unchecked")`.
6. **Chạy thử với nhiều loại phần tử** và với collection không sửa được để phân biệt compile-time type safety với runtime mutability.
7. **Kiểm tra empty list và chỉ số** để tránh `IndexOutOfBoundsException`, đồng thời xác định ý nghĩa của việc thêm phần tử, đổi thứ tự và alias.

Ví dụ test thủ công đáng thực hiện:

```java
// Đặt trong main của một lớp đã định nghĩa swapEnds.
List<Integer> ints = new ArrayList<>(List.of(10, 20, 30));
swapEnds(ints);
System.out.println(ints); // [30, 20, 10]
```

Để tự biên dịch đoạn này độc lập, cần class chứa `swapEnds` cùng import `List`/`ArrayList`. Nếu đã chạy chương trình phần 4, bạn có thể thêm đoạn này vào `main` và kiểm tra đầu ra mới.

### Khi nào không cần helper?

Nếu yêu cầu chỉ đọc:

```java
static void printAll(List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

thì không cần capture helper. Nếu chỉ thêm đối tượng được biết trước là Integer vào consumer:

```java
static void addTen(List<? super Integer> destination) {
    destination.add(10);
}
```

cũng không cần. Chỉ tạo helper khi nó giải quyết **một quan hệ kiểu bị wildcard che đi mà compiler chưa thể duy trì qua các biểu thức**.

## 12. Bài tập tự kiểm tra

**Bài 1.** Vì sao `List<?> list` cho phép `list.get(0)` nhưng từ chối `list.set(0, list.get(1))`?

**Đáp án:** Lúc đọc, có thể nhìn phần tử dưới dạng Object; lúc ghi, `set` cần kiểu capture chưa biết. Một biểu thức đọc được sử dụng dưới dạng Object không bảo đảm tương thích với capture trong chữ ký kiểm tra trực tiếp. Helper generic có tên T giúp biểu đạt quan hệ cùng kiểu.

**Bài 2.** Viết chữ ký helper cho phép chuyển phần tử cuối lên đầu trong cùng một list bất kỳ.

**Đáp án:**

```java
static void rotateRight(List<?> values) {
    rotateRightCaptured(values);
}

private static <T> void rotateRightCaptured(List<T> values) {
    if (values.size() < 2) return;
    T last = values.get(values.size() - 1);
    for (int i = values.size() - 1; i > 0; i--) {
        values.set(i, values.get(i - 1));
    }
    values.set(0, last);
}
```

Hai phương thức này là đoạn mã đặt trong một class đã import `List`.

**Bài 3.** Vì sao không được viết `copy(List<?> from, List<?> to) { to.add(from.get(0)); }`?

**Đáp án:** Kiểu phần tử của from và to chưa biết và có thể khác nhau. Không có bằng chứng rằng giá trị từ from tương thích kiểu phần tử mà to nhận.

**Bài 4.** Nếu muốn sao chép từ `List<Integer>` vào `List<Number>`, chữ ký nào phù hợp?

**Đáp án:** `<T> void appendAll(List<? super T> destination, List<? extends T> source)`; trình biên dịch có thể chọn T phù hợp để cho phép đọc từ source và ghi vào destination.

**Bài 5.** Helper generic có giải quyết `UnsupportedOperationException` khi `set` trên `List.of` không?

**Đáp án:** Không. Helper chỉ hỗ trợ kiểm tra generic. List không sửa được vẫn từ chối `set` hoặc `add`.

**Bài 6.** Tại sao thay `List<?>` bằng raw `List` làm biến mất lỗi compiler nhưng vẫn là lựa chọn kém?

**Đáp án:** Raw List bỏ ràng buộc phần tử, có thể cho ghi đối tượng không tương thích. Nó không chứng minh quan hệ kiểu; về sau dễ dẫn tới heap pollution và `ClassCastException`.

**Bài 7.** Một helper `<T> void change(List<T> values)` chỉ được gọi từ `List<?>` chứa String. Có phải T luôn là String trong mọi lần gọi tương lai không?

**Đáp án:** Không. T được suy luận độc lập cho mỗi lời gọi. Một lần khác, helper có thể nhận list chứa Integer, Number hoặc một lớp tự định nghĩa.

**Bài 8.** Có nên tạo helper chỉ để gọi `Collections.swap` không?

**Đáp án:** Thường không; `Collections.swap` đã nhận `List<?>` và hỗ trợ trực tiếp thao tác đổi chỗ. Dùng helper riêng khi thuật toán cần nhiều phép đọc/ghi liên kết hoặc thư viện không có phương thức đáp ứng yêu cầu.

## 13. Ghi nhớ

- `List<?>` chứa một loại phần tử **chưa biết**, không phải `List<Object>`.
- Compiler có thể gán một tên tạm như `CAP#1` cho kiểu capture; tên này chỉ phục vụ suy luận và chẩn đoán.
- Nếu thao tác **đọc và ghi cùng một danh sách**, phương thức helper `<T> void helper(List<T> list)` thường giữ được quan hệ an toàn giữa các phần tử của danh sách.
- Nếu thao tác chuyển phần tử **giữa hai danh sách độc lập**, hai `?` không tự bảo đảm cùng kiểu. Hãy dùng generic signature thể hiện quan hệ hoặc PECS.
- Capture helper không thay đổi việc list có sửa được hay không, không thay đổi runtime erasure và không phải lý do để thêm unchecked cast.
- Ưu tiên API công khai ngắn gọn và các tiện ích thư viện chuẩn; giữ logic capture trong helper private khi chỉ phục vụ triển khai.

Khi đọc lỗi `capture of ?`, câu hỏi đúng không phải “làm sao ép compiler chấp nhận Object?”, mà là **giá trị được ghi có được chứng minh thuộc cùng kiểu chưa biết mà container yêu cầu hay không?** Nếu câu trả lời là có vì nó đến từ cùng container, một helper generic có tên T là cách diễn đạt quan hệ đó một cách rõ ràng, an toàn và dễ bảo trì.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-integrated-practice-005">So sánh suy luận với diamond, var và đối số kiểu tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh suy luận với diamond, `var` và đối số kiểu tường minh trong Java Generics

## 1. Cùng viết ngắn, nhưng ba cơ chế giải quyết ba câu hỏi khác nhau

Java có nhiều cú pháp giúp bớt lặp lại tên kiểu. Ba dạng thường xuất hiện cạnh nhau:

```java
List<String> names = new ArrayList<>();           // Diamond <>
var scores = new ArrayList<Integer>();            // var
var values = new ArrayList<>();                    // Kết hợp var và diamond
List<String> empty = List.<String>of();            // Đối số kiểu tường minh của method
```

Đây là đoạn minh họa, cần import `java.util.List` và `java.util.ArrayList`, đặt bên trong method nếu biên dịch.

Nhìn qua, chúng đều có vẻ “để Java tự đoán kiểu”. Nhưng vai trò khác nhau:

- **Diamond `<>`** nằm ở lời gọi constructor của một class generic. Compiler suy luận **đối số kiểu của class được khởi tạo**, dựa vào constructor arguments và ngữ cảnh đích nếu có.
- **`var`** nằm ở phần **khai báo biến cục bộ**. Compiler suy luận **kiểu tĩnh của biến** từ biểu thức khởi tạo, sau đó biến vẫn được kiểm tra kiểu tĩnh như bình thường.
- **Đối số kiểu tường minh** như `new ArrayList<String>()` hoặc `Collections.<String>emptyList()` chỉ ra cụ thể tham số kiểu muốn sử dụng ở vị trí class constructor hoặc generic method. Nó có thể giúp làm rõ ý đồ hoặc hướng dẫn suy luận trong các tình huống không có ngữ cảnh đủ mạnh.

Điểm cần ghi nhớ:

> `<>` và `var` không đồng nghĩa. Khi dùng `List<String> list = new ArrayList<>()`, vế trái truyền ngữ cảnh để suy ra `String`. Khi dùng `var list = new ArrayList<>()`, không có vế trái mang `String`; với constructor không đối số này, Java suy luận phần tử là `Object`.

Vì vậy, viết gọn **không phải lúc nào cũng giữ nguyên loại thông tin kiểu** được compiler sử dụng. Muốn đánh giá tính an toàn và khả năng đọc hiểu, hãy xem **nguồn thông tin mà compiler dùng để suy luận**.

## 2. Nền tảng: generic type parameter, type argument và kiểu tĩnh

Khai báo `ArrayList<E>` có một tham số kiểu `E`. Khi sử dụng:

```java
new ArrayList<String>()
```

`String` là **đối số kiểu** được ghi tường minh cho `E`. Với:

```java
new ArrayList<>()
```

compiler tự suy luận đối số thích hợp cho `E`.

Sau đó biểu thức khởi tạo có một kiểu tĩnh. Kiểu tĩnh này có thể được dùng để suy luận tiếp kiểu của biến cục bộ khai báo bằng `var`, nhưng `var` bản thân nó **không phải một type argument** của `ArrayList`.

Có thể mô tả đơn giản:

```text
Khai báo class:     ArrayList<E>
                           |
Constructor:        new ArrayList<>()  -- suy luận E
                           |
Biểu thức:          ArrayList<String>   -- nếu ngữ cảnh xác định String
                           |
Khai báo biến:      var list = ...      -- suy luận kiểu của list từ biểu thức
```

Sơ đồ chỉ cho thấy các quan hệ suy luận; compiler Java có các quy tắc chặt chẽ hơn về *target typing*, applicability và type inference trong Java Language Specification.

Cần phân biệt thêm:

- **Kiểu tĩnh** quyết định những lời gọi/gán nào compiler chấp nhận.
- **Class runtime** do đối tượng thực được khởi tạo quyết định. `ArrayList<String>` và `ArrayList<Integer>` thường cùng runtime class `ArrayList`.
- Java Generics dùng **type erasure**, nhưng điều đó không làm mất ý nghĩa kiểm tra kiểu của `var` và diamond tại compile time.

## 3. Diamond `<>`: suy luận đối số kiểu của constructor

### 3.1. Dạng có target type

Ví dụ điển hình:

```java
List<String> names = new ArrayList<>();
```

Vế trái mang kiểu `List<String>`. Constructor `ArrayList<>()` có thể được suy luận thành một biểu thức phù hợp với việc gán cho `List<String>`; trong trường hợp đơn giản này, tham số phần tử của `ArrayList` là `String`.

Viết đầy đủ hơn:

```java
List<String> names = new ArrayList<String>();
```

Hai cách trên có cùng ý định kiểu tại mã nguồn: `names` là `List<String>`. Diamond giảm lặp lại `<String>` ở vế phải.

Tương tự:

```java
Map<String, Integer> counts = new HashMap<>();
Set<Long> ids = new HashSet<>();
```

Cần import các class từ `java.util`. Với các khai báo trên, target type ở vế trái cung cấp thông tin cho constructor.

### 3.2. Constructor arguments cũng có thể cung cấp thông tin

Nếu constructor nhận dữ liệu có kiểu rõ ràng:

```java
var copied = new ArrayList<>(List.of("An", "Binh"));
```

`List.of("An", "Binh")` có phần tử kiểu `String` theo suy luận generic method. Constructor `ArrayList(Collection<? extends E>)` nhận collection ấy, từ đó compiler có thông tin để suy ra `E` tương ứng.

Điểm khác biệt với `new ArrayList<>()` không đối số: ở ví dụ copy, **constructor argument** cung cấp ràng buộc hữu ích ngay cả khi không có kiểu đích ở vế trái.

### 3.3. Diamond không phải dấu hiệu raw type

```java
List<String> a = new ArrayList<>(); // Generic được kiểm tra.
List b = new ArrayList();           // Raw type.
```

Hai dòng có ý nghĩa khác:

- Dòng đầu dùng generic và diamond; `a` có kiểu `List<String>`.
- Dòng sau dùng raw `List` và raw `ArrayList`, làm mất kiểm tra đối số kiểu ở điểm sử dụng và có thể gây cảnh báo `rawtypes`/`unchecked`.

Đừng bỏ dấu `<...>` ở cả hai vế rồi cho rằng vẫn có cùng mức độ an toàn kiểu.

## 4. `var`: compiler suy luận kiểu biến cục bộ từ initializer

`var` được hỗ trợ cho **local variable type inference** từ Java 10. Ví dụ:

```java
var name = "Mai";
var count = 3;
var list = new ArrayList<String>();
```

Trong ba dòng này, compiler suy ra các kiểu tĩnh tương ứng là `String`, `int` và `ArrayList<String>`. Sau khi khai báo, chúng không biến thành những biến “động” có thể đổi kiểu tùy ý.

```java
var value = "hello";
// value = 10; // Lỗi biên dịch: value có kiểu String.
```

Câu lệnh thứ hai được cố ý đặt thành comment để minh họa lỗi.

### `var` không có nghĩa “không có generic”

Một biến:

```java
var strings = new ArrayList<String>();
```

có kiểu tĩnh `ArrayList<String>`. Do đó:

```java
strings.add("valid");
// strings.add(123); // Lỗi biên dịch.
```

Dùng `var` **không tự làm mất** `<String>` đã xuất hiện trong biểu thức khởi tạo.

### `var` không dùng được ở mọi chỗ khai báo

`var` dành cho những vị trí được Java quy định, đặc biệt là local variables có initializer; nó không được dùng như tên kiểu thay thế cho mọi field, method parameter hoặc return type.

Ví dụ không hợp lệ:

```java
// Các dòng này chỉ là ví dụ lỗi:
// var field;                  // Field không dùng var kiểu này.
// var local;                  // Local variable thiếu initializer.
// var nothing = null;         // Không suy luận kiểu từ null đơn độc.
// var array = {1, 2, 3};      // Array initializer dạng rút gọn thiếu kiểu đích.
```

Một local array hợp lệ có thể viết `var array = new int[] {1, 2, 3};`.

Từ Java 11, `var` cũng được phép trong tham số lambda khi tuân thủ quy tắc liên quan, nhưng đó là một ngữ cảnh khác với phần đang so sánh.

## 5. Bẫy quan trọng nhất: `var` kết hợp `new ArrayList<>()`

Đây là phép thử cần thực hành thật:

Tạo tệp **`VarDiamondDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class VarDiamondDemo {
    public static void main(String[] args) {
        List<String> targeted = new ArrayList<>();
        targeted.add("Java");

        var inferredFromExplicit = new ArrayList<String>();
        inferredFromExplicit.add("Generics");

        var withoutTarget = new ArrayList<>();
        withoutTarget.add("text");
        withoutTarget.add(123);

        System.out.println(targeted.get(0));
        System.out.println(inferredFromExplicit.get(0));
        System.out.println(withoutTarget.get(0));
        System.out.println(withoutTarget.get(1));

        Object value = withoutTarget.get(0);
        System.out.println(value.getClass().getSimpleName());
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac VarDiamondDemo.java
java VarDiamondDemo
```

Stdout:

```text
Java
Generics
text
123
String
```

### Vì sao `withoutTarget` nhận được cả `String` và `Integer`?

Với:

```java
var withoutTarget = new ArrayList<>();
```

compiler không nhận được ngữ cảnh kiểu `List<String>` ở vế trái, vì vế trái là `var`. Constructor `ArrayList()` không đối số cũng không đưa ra dữ liệu để suy ra một phần tử chuyên biệt. Trong tình huống này, type parameter `E` được suy luận thành `Object`, khiến biến có kiểu tĩnh `ArrayList<Object>`.

Vì vậy:

```java
withoutTarget.add("text");  // String là Object.
withoutTarget.add(123);     // Integer cũng là Object.
```

Cả hai lời gọi hợp lệ. Đây không phải “`var` thay đổi kiểu của danh sách sau lần add đầu tiên”. Kiểu đã được quyết định **ngay tại dòng khai báo**.

Cũng vì vậy:

```java
// Cố ý không hợp lệ:
String text = withoutTarget.get(0);
```

là lỗi biên dịch: `get(0)` trên `ArrayList<Object>` có kiểu tĩnh `Object`, không thể tự động gán vào `String`.

**Lưu ý:** `var` không hạ mọi generic thành `Object`. Nó chỉ phản ánh kiểu của **initializer**. Nếu initializer đã có đủ thông tin, ví dụ `new ArrayList<String>()` hoặc `new ArrayList<>(List.of("a"))`, thông tin kiểu tương ứng được giữ.

## 6. So sánh cùng một ý định bằng bốn khai báo

Giả sử mục tiêu là một danh sách chuỗi có thể thêm `"A"` và `"B"` nhưng compiler phải chặn `123`.

| Khai báo | Kiểu tĩnh của biến | Có đáp ứng ý định không? |
|---|---|---|
| `List<String> x = new ArrayList<>();` | `List<String>` | Có |
| `List<String> x = new ArrayList<String>();` | `List<String>` | Có, nhưng lặp `<String>` |
| `var x = new ArrayList<String>();` | `ArrayList<String>` | Có; phụ thuộc kiểu triển khai cụ thể |
| `var x = new ArrayList<>();` | `ArrayList<Object>` | Không, nếu muốn giới hạn phần tử là `String` |

Hai điểm khác biệt cần nhấn mạnh:

1. **Mức độ cụ thể của kiểu biến.** `List<String>` dùng interface `List`; `var x = new ArrayList<String>()` suy ra class cụ thể `ArrayList<String>`. Cả hai là generic an toàn nhưng cách biểu diễn phụ thuộc implementation khác nhau.
2. **Thông tin đưa vào suy luận.** Một lần viết `<String>` ở **vế trái** có thể đủ để diamond suy ra, nhưng với `var` thì thông tin đó phải đến từ **initializer** hoặc constructor arguments.

Khi bạn muốn API dễ thay implementation `ArrayList` bằng `LinkedList`, kiểu biến khai báo `List<String>` thường biểu diễn ý định tốt hơn. Khi phần khởi tạo phức tạp nhưng kiểu hiển nhiên từ initializer, `var` có thể giảm nhiễu.

## 7. `var` + diamond vẫn suy luận đúng khi constructor có dữ liệu

Tạo tệp **`DiamondFromArgumentsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class DiamondFromArgumentsDemo {
    public static void main(String[] args) {
        var first = new ArrayList<>(List.of("A", "B"));
        var second = new ArrayList<>(List.of(10, 20));

        String name = first.get(0);
        Integer number = second.get(0);

        first.add("C");
        second.add(30);

        System.out.println(name);
        System.out.println(number);
        System.out.println(first);
        System.out.println(second);
    }
}
```

Chạy:

```bash
javac DiamondFromArgumentsDemo.java
java DiamondFromArgumentsDemo
```

Stdout:

```text
A
10
[A, B, C]
[10, 20, 30]
```

Trong ví dụ:

- `List.of("A", "B")` được suy luận thành danh sách chuỗi.
- `new ArrayList<>(...)` nhận `Collection<? extends E>` để quyết định `E`.
- Biến `first` được suy luận thành `ArrayList<String>`.
- Biến `second` được suy luận thành `ArrayList<Integer>`.

Compiler vì thế có thể kiểm tra `String name = first.get(0)` và `Integer number = second.get(0)`.

Đừng học mẹo “có diamond thì phần tử luôn là Object”. Kết quả phụ thuộc **ràng buộc suy luận từ target type, constructor arguments và các quy tắc inference**.

## 8. Đối số kiểu tường minh của constructor so với diamond

Trong Java:

```java
new ArrayList<String>()
new ArrayList<>()
```

`<String>` ở ví dụ đầu là **đối số kiểu tường minh cho class generic**. `<>` ở ví dụ sau yêu cầu compiler suy luận chúng.

Với ngữ cảnh:

```java
List<String> names = new ArrayList<>();
```

diamond thường ngắn và rõ. Nhưng ở:

```java
var names = new ArrayList<String>();
```

việc chỉ rõ `String` ở constructor có ý nghĩa thực sự, vì `var` không cung cấp target type ở vế trái.

Có thể viết:

```java
ArrayList<String> names = new ArrayList<String>();
```

mà không sai. Tuy vậy cách viết này lặp `String`; thường chỉ nên dùng khi cần thể hiện kiểu đầy đủ để hướng dẫn người đọc hoặc khi phải tương thích môi trường Java rất cũ.

**Không nhầm** khai báo đối số kiểu của class với cú pháp đối số kiểu của method. `new ArrayList<String>()` khác về vị trí ngữ pháp với `Collections.<String>emptyList()`.

## 9. Đối số kiểu tường minh của generic method

Không chỉ constructor mới có type inference. Một **generic method** có thể khai báo tham số kiểu riêng:

```java
static <T> T identity(T input) {
    return input;
}
```

Khi gọi `identity("Java")`, compiler suy luận `T = String` từ tham số. Có thể chỉ rõ đối số kiểu bằng cú pháp:

```java
GenericMethodWitnessDemo.<String>identity("Java")
```

Ví dụ độc lập, tệp **`GenericMethodWitnessDemo.java`**:

```java
import java.util.Collections;
import java.util.List;

public class GenericMethodWitnessDemo {

    static <T> T identity(T value) {
        return value;
    }

    public static void main(String[] args) {
        String inferred = identity("Java");
        String explicit =
                GenericMethodWitnessDemo.<String>identity("Generics");

        List<String> contextual = Collections.emptyList();
        var withoutContext = Collections.emptyList();
        var withWitness = Collections.<String>emptyList();

        System.out.println(inferred);
        System.out.println(explicit);
        System.out.println(contextual.size());
        System.out.println(withoutContext.size());
        System.out.println(withWitness.size());
    }
}
```

Chạy:

```bash
javac GenericMethodWitnessDemo.java
java GenericMethodWitnessDemo
```

Stdout:

```text
Java
Generics
0
0
0
```

Ba lời gọi `Collections.emptyList()` có kiểu **ở mã nguồn** khác nhau:

- `List<String> contextual = Collections.emptyList();` có target type `List<String>`, nên compiler suy luận `T` phù hợp với `String`.
- `var withoutContext = Collections.emptyList();` không có target type từ khai báo biến, và method không có tham số để cung cấp thêm ràng buộc. Trong ví dụ này `T` được suy luận là `Object`, nên biến có kiểu `List<Object>`.
- `var withWitness = Collections.<String>emptyList();` có **type witness** `<String>` ở lời gọi method, nên biến có kiểu `List<String>`.

Cả ba danh sách do `Collections.emptyList()` cung cấp đều rỗng; kết quả stdout không tự chứng minh generic types. Muốn thấy sự khác nhau, hãy thử gán kết quả vào các biến có yêu cầu kiểu trái nhau và xem compiler phản hồi.

### Suy luận không thay đổi generic declaration

Generic method vẫn chỉ có **một khai báo** `<T> List<T> emptyList()`. Việc compiler suy luận `String`, `Integer` hoặc `Object` ở từng lời gọi không tạo thêm các overload hay runtime class riêng cho từng `T`.

## 10. Lời gọi `List.of()` rỗng: nơi `var` dễ làm mất ý định

`List.of()` tạo danh sách không thể chỉnh sửa qua các thao tác cấu trúc thông thường. Phiên bản **không đối số** không có phần tử nào để giúp suy luận kiểu.

So sánh:

```java
List<String> a = List.of();
var b = List.of();
var c = List.<String>of();
```

Dự đoán:

- `a` có kiểu tĩnh `List<String>` nhờ ngữ cảnh ở vế trái.
- `b` có kiểu tĩnh `List<Object>` vì không có ngữ cảnh hoặc đối số phần tử mang `String`.
- `c` có kiểu tĩnh `List<String>` nhờ type witness `<String>`.

Khác với `new ArrayList<>()`, các danh sách từ `List.of()` không cho phép `add(...)`, vì vậy việc kiểm tra kiểu dễ thực hiện bằng phương thức nhận `List<String>` hoặc bằng cách gán sang biến cùng kiểu.

Ví dụ độc lập **`EmptyListInferenceDemo.java`**:

```java
import java.util.List;

public class EmptyListInferenceDemo {

    static void showStrings(List<String> values) {
        System.out.println("strings=" + values.size());
    }

    public static void main(String[] args) {
        List<String> a = List.of();
        var b = List.of();
        var c = List.<String>of();

        showStrings(a);
        showStrings(c);
        System.out.println(b.size());

        // showStrings(b); // Lỗi: b có kiểu List<Object>.
    }
}
```

Chạy:

```bash
javac EmptyListInferenceDemo.java
java EmptyListInferenceDemo
```

Stdout:

```text
strings=0
strings=0
0
```

Bài học: Với một **generic factory method không có đầu vào mang kiểu**, target type hoặc đối số kiểu tường minh thường là cách nói rõ loại kết quả mong đợi.

## 11. Hai dấu `<...>` nằm ở hai nơi khác nhau

Hãy nhìn hai cách viết:

```java
List<String> first = new ArrayList<>();
List<String> second = Collections.<String>emptyList();
```

Dấu ngoặc nhọn ở hai ví dụ có vai trò khác nhau:

- `new ArrayList<>()`: diamond tại **class instance creation expression**; compiler suy luận tham số kiểu của class `ArrayList<E>`.
- `Collections.<String>emptyList()`: `<String>` là **explicit type argument cho generic method** `emptyList<T>()`.

Một lớp generic có thể đồng thời có generic constructor hoặc generic instance method riêng, nên không thể suy luận chỉ từ số lượng dấu ngoặc rằng chúng luôn điều khiển cùng một tham số kiểu.

Ví dụ dạng đơn giản:

```java
class Pair<L, R> {
    Pair(L left, R right) {
    }

    <U> U echo(U value) {
        return value;
    }
}
```

Ở đây `L` và `R` thuộc **class**, còn `U` thuộc **method**. `new Pair<>("x", 1)` suy luận `L`, `R`; `pair.<Boolean>echo(true)` đặt `U = Boolean`. Hai cơ chế có quan hệ nhưng hoạt động tại hai vị trí khai báo riêng.

## 12. Ví dụ tổng hợp: generic class và generic method độc lập

Tạo tệp **`CombinedInferenceDemo.java`**:

```java
public class CombinedInferenceDemo {

    static class Pair<L, R> {
        private final L left;
        private final R right;

        Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }

        L left() {
            return left;
        }

        R right() {
            return right;
        }

        <U> U echo(U value) {
            return value;
        }
    }

    public static void main(String[] args) {
        Pair<String, Integer> first = new Pair<>("age", 21);

        var second = new Pair<>("score", 99);

        String label = first.left();
        Integer score = second.right();

        String message = second.echo("ok");
        Boolean flag = second.<Boolean>echo(true);

        System.out.println(label);
        System.out.println(score);
        System.out.println(message);
        System.out.println(flag);
    }
}
```

Biên dịch và chạy:

```bash
javac CombinedInferenceDemo.java
java CombinedInferenceDemo
```

Stdout:

```text
age
99
ok
true
```

Phân tích:

1. `first` có target type `Pair<String,Integer>`, nên diamond ở constructor được suy luận phù hợp với target.
2. `second` không có target type từ `var`, nhưng constructor nhận `"score"` và `99`, đủ để suy ra `L = String`, `R = Integer`.
3. `second.right()` vì vậy có kiểu tĩnh `Integer`.
4. `second.echo("ok")` suy luận **`U` của method** từ đối số là `String`.
5. `second.<Boolean>echo(true)` là lời gọi cùng method nhưng chỉ định tường minh `U = Boolean`. Nó không thay đổi `L`, `R` của `Pair`.

Nếu thử `Pair<String,Integer> wrong = new Pair<>("age","twenty");`, compiler phải từ chối vì đối số thứ hai không phù hợp kiểu đích `Integer`. Đây là lợi ích cụ thể của target typing và kiểm tra kiểu.

## 13. Khi nào `var` làm lộ class cụ thể thay vì interface?

Nhìn vào hai khai báo:

```java
List<String> list = new ArrayList<>();
var list2 = new ArrayList<String>();
```

- `list` có kiểu tĩnh là **interface** `List<String>`.
- `list2` có kiểu tĩnh là **class triển khai** `ArrayList<String>`.

Điều này không tự động làm `list2` thiếu an toàn kiểu; cả hai đều chặn phần tử khác `String`. Khác biệt ở khả năng truy cập các thành viên chỉ thuộc class triển khai, ở tính đọc hiểu và mức độ phụ thuộc vào implementation.

Ví dụ:

```java
var concrete = new java.util.ArrayList<String>();
java.util.List<String> abstraction = new java.util.ArrayList<>();
```

Hai biến cùng trỏ tới object `ArrayList` khi chạy, nhưng khai báo thứ hai chủ động biểu đạt “tôi chỉ cần hợp đồng `List`”.

Với API dùng lâu dài hoặc được nhiều nơi sử dụng, khai báo interface ở vế trái thường tạo ranh giới phụ thuộc rõ ràng. Với biến cục bộ ngắn, `var` có thể hợp lý nếu kiểu được thể hiện trực tiếp, không làm người đọc phải đoán.

**Không suy ra rằng** `var` giống `Object`. `var concrete = new ArrayList<String>()` có type đầy đủ `ArrayList<String>`, không phải `Object`, raw `ArrayList`, hay `ArrayList<Object>`.

## 14. Bối cảnh target typing: assignment, return và lời gọi method

Compiler có thể dùng **target type** từ ngữ cảnh bao quanh một biểu thức generic:

```java
List<String> list = new ArrayList<>();
```

Ở đây assignment cung cấp kiểu đích.

Một phương thức có return type rõ ràng cũng tạo ngữ cảnh:

```java
static List<String> createNames() {
    return new ArrayList<>();
}
```

Trong lời gọi method, tham số của method có thể tạo target type:

```java
static void consume(List<String> names) {
    System.out.println(names.size());
}

static void demo() {
    consume(new ArrayList<>());
}
```

Hai đoạn trên là khai báo method để minh họa, không có class chứa và `main`; đặt chúng trong một class để biên dịch.

Bởi vậy, quy tắc “diamond chỉ nhìn vế trái” là thiếu. Diamond có thể dùng nhiều ngữ cảnh phù hợp được hệ thống type inference của Java quy định, cùng thông tin đến từ constructor arguments.

**`var` là ngữ cảnh đặc biệt:** Khi viết `var x = expression;`, compiler phải xác định kiểu của chính `expression` để tạo kiểu của `x`. Không có một khai báo `List<String>` ở vế trái để truyền thông tin `String` vào việc suy luận đối số kiểu của initializer.

## 15. Suy luận từ biểu thức generic method và type witness

Xét method:

```java
static <T> T choose(T first, T second) {
    return first;
}
```

Compiler có thể dùng hai đối số để suy luận `T`, và trong ngữ cảnh phù hợp còn xét ràng buộc từ kết quả.

Ví dụ chương trình độc lập **`ChooseInferenceDemo.java`**:

```java
public class ChooseInferenceDemo {

    static <T> T choose(T first, T second) {
        return first;
    }

    public static void main(String[] args) {
        String selected = choose("red", "blue");

        var inferred = choose(10, 20);

        Number asNumber =
                ChooseInferenceDemo.<Number>choose(10, 2.5);

        System.out.println(selected);
        System.out.println(inferred);
        System.out.println(asNumber);
    }
}
```

Chạy:

```bash
javac ChooseInferenceDemo.java
java ChooseInferenceDemo
```

Stdout:

```text
red
10
10
```

`<Number>` là explicit type argument cho method `choose`, nên cả `Integer` (từ `10`) và `Double` (từ `2.5`) đều có thể được dùng qua kiểu `Number`. Kết quả được biểu diễn ở kiểu tĩnh `Number`, dù object được chọn ở runtime là `Integer`.

Nếu khai báo type witness không phù hợp, compiler sẽ từ chối. Ví dụ:

```java
// Cố ý không hợp lệ:
String s = ChooseInferenceDemo.<String>choose("a", 42);
```

`42` không phù hợp với `String`, nên không thể ép compiler “suy luận” sai chỉ bằng `<String>`. Type witness là **ràng buộc kiểm tra kiểu**, không phải cast hoặc lệnh bỏ qua hệ thống kiểu.

### Khi nào nên chỉ định đối số kiểu?

Thông thường compiler đã suy luận được từ đối số:

```java
String selected = choose("red", "blue");
```

Viết `<String>` tường minh thường không cần thiết. Nhưng với generic factory không có tham số hoặc khi muốn làm rõ một kiểu kết quả/kiểu chung, type witness có thể giúp diễn đạt ý định và loại bỏ chỗ mơ hồ.

## 16. `var` và wildcard: đừng nhầm kiểu không biết với `Object`

Một nhu cầu phổ biến là chỉ **đọc** từ danh sách mà không cần biết chính xác kiểu phần tử. Khi đó dùng `List<?>`:

```java
static void printAll(List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

Đây là phương thức minh họa cần đặt trong class.

`List<?>` khác raw `List`:

- `List<?>` vẫn là kiểu generic với một đối số **chưa biết**, nên compiler giữ các ràng buộc an toàn. Chẳng hạn, không thể thêm tùy tiện `"a"` vào `List<?>`.
- Raw `List` bỏ kiểm tra generic ở nơi dùng và có thể tạo cảnh báo unchecked.
- `List<Object>` có phần tử chính xác là `Object` về mặt type argument; không thể nhận trực tiếp `List<String>` do invariance.
- `var x = new ArrayList<>()` trong trường hợp không có dữ liệu/ngữ cảnh thường cho `ArrayList<Object>`, **không** phải `ArrayList<?>`.

Ví dụ sai dễ gặp:

```java
List<String> strings = new ArrayList<>();
// List<Object> objects = strings; // Không hợp lệ.
List<?> unknown = strings;         // Hợp lệ.
```

Suy luận bằng `var` không thay đổi các quy tắc variance hay wildcard của Java.

## 17. Những trường hợp compiler không thể suy luận từ `var`

Một số cách dùng `var` bị từ chối hoặc dẫn đến kiểu ngoài ý muốn:

### Chỉ có `null`

```java
// var value = null; // Lỗi: không có kiểu thích hợp để suy luận.
String value = null; // Hợp lệ: đã có kiểu đích String.
```

### Không có initializer

```java
// var items; // Không hợp lệ.
java.util.List<String> items;
items = new java.util.ArrayList<>();
```

Một local variable khai báo bằng tên kiểu tường minh có thể gán sau, nhưng phải tuân thủ definite assignment trước khi đọc.

### Lambda/method reference đứng độc lập không có kiểu đích

```java
// var predicate = x -> x.length() > 0; // Không hợp lệ.
java.util.function.Predicate<String> predicate =
        x -> x.length() > 0;
```

Lambda cần một **functional interface target type**. Dù `var` có thể xuất hiện trong phần parameter declaration của lambda theo quy tắc Java 11+, không có nghĩa một lambda expression tự cung cấp được functional interface type cho initializer của `var`.

### Diamond rỗng

```java
var items = new java.util.ArrayList<>();
```

Dòng này hợp lệ, nhưng `items` là `ArrayList<Object>`, không phải `ArrayList<String>` chỉ vì bạn dự định thêm String sau đó.

### Anonymous class hay biểu thức có kiểu suy luận phức tạp

`var` có thể suy luận một kiểu quá cụ thể hoặc khó diễn đạt bằng tên đơn giản. Với anonymous class, local variable inference có thể giữ thông tin về anonymous type đủ để gọi thành viên trực tiếp trong phạm vi cho phép, điều khác với khai báo qua interface ở vế trái.

Khi kiểu trở nên khó hiểu hoặc API cần hợp đồng trừu tượng, viết rõ kiểu ở bên trái thường làm code dễ bảo trì hơn.

## 18. Diamond và anonymous class: lưu ý theo phiên bản Java

Từ Java 9, diamond có thể xuất hiện cùng anonymous class trong những trường hợp được ngôn ngữ cho phép. Tuy vậy, compiler đặt điều kiện đối với các kiểu được suy luận cho anonymous class (trong đó yêu cầu về kiểu có thể biểu diễn được bằng ngôn ngữ nguồn).

Không nên lấy một ví dụ `new SomeGeneric<>() { ... }` bất kỳ và kết luận nó luôn hợp lệ; cần xét constructor, inference và hạn chế liên quan.

Với Java 17+, nếu mục tiêu là minh họa diamond thông thường và `var`, ví dụ `new ArrayList<>()` hoặc `new Pair<>("x", 1)` rõ ràng hơn. Anonymous subclass còn liên quan tới việc giữ generic superclass metadata, một chủ đề riêng với mục tiêu suy luận kiểu của bài này.

## 19. Những lỗi và hiểu sai thường gặp

**Lỗi 1 — “`var` luôn suy luận thành `Object`.”** Sai. `var text = "a"` suy ra `String`; `var list = new ArrayList<String>()` suy ra `ArrayList<String>`.

**Lỗi 2 — “`new ArrayList<>()` luôn tạo `ArrayList<Object>`.”** Sai. Với target type `List<String>` hoặc constructor arguments phù hợp, diamond có thể suy luận `String`. Nó suy ra `Object` trong trường hợp không có ràng buộc đủ cụ thể, ví dụ `var x = new ArrayList<>()`.

**Lỗi 3 — “Compiler xem các lệnh `add(...)` sau đó để xác định `E`.”** Sai. Kiểu của biến được xác định ở dòng khai báo, không thay đổi theo những lời gọi tiếp theo.

**Lỗi 4 — “`var` là kiểu động giống JavaScript.”** Sai. Java vẫn kiểm tra kiểu tĩnh; `var x = "a"; x = 1;` là lỗi.

**Lỗi 5 — “Diamond chính là raw type.”** Sai. Diamond yêu cầu suy luận generic argument; raw type bỏ thông tin generic ở nơi sử dụng.

**Lỗi 6 — “`List<?>` giống `List<Object>`.”** Sai. Wildcard biểu diễn đối số kiểu chưa biết; `List<Object>` có đối số kiểu cụ thể là `Object`. Java Generics mặc định invariant.

**Lỗi 7 — “Đối số kiểu `<String>` ở `Collections.<String>emptyList()` là cast.”** Sai. Nó đặt type argument cho generic method, chịu kiểm tra kiểu bình thường.

**Lỗi 8 — “`new ArrayList<String>()` và `Collections.<String>emptyList()` cùng một loại cú pháp generic.”** Không hoàn toàn. Cái đầu áp dụng đối số kiểu cho class instance creation; cái sau cho lời gọi generic method.

**Lỗi 9 — “Nếu code compile với `var` thì đọc code sẽ luôn rõ kiểu.”** Không hẳn. `var` có thể che đi việc initializer suy luận `Object` hoặc một class cụ thể ngoài ý định thiết kế. Cần chọn cách viết dễ hiểu với người bảo trì.

**Lỗi 10 — “Đối số kiểu tường minh sinh class runtime riêng cho mỗi kiểu.”** Sai. Generics Java thông thường áp dụng erasure. Tường minh hay suy luận đều chủ yếu phục vụ compile-time type checking.

**Lỗi 11 — “Có thể sửa mismatch bằng type witness bất kỳ.”** Sai. `ChooseInferenceDemo.<String>choose("a", 42)` vẫn sai vì đối số thứ hai không đáp ứng `String`.

**Lỗi 12 — “Tất cả generic method đều phụ thuộc target type.”** Sai. Nhiều method đã đủ thông tin từ tham số đầu vào; target type chỉ là một nguồn ràng buộc có thể tham gia suy luận theo quy tắc cụ thể.

## 20. Bảng quyết định khi viết code thực tế

| Mục tiêu | Cách viết gợi ý | Vì sao |
|---|---|---|
| Muốn biến có hợp đồng `List<String>` | `List<String> x = new ArrayList<>();` | Interface rõ ràng; diamond tận dụng target type |
| Muốn `var` nhưng phải giữ `String` | `var x = new ArrayList<String>();` | Initializer mang generic argument |
| Copy một collection có kiểu rõ | `var x = new ArrayList<>(List.of("a"));` | Constructor argument giúp suy luận `String` |
| Muốn list rỗng không sửa được | `List<String> x = List.of();` | Target type gợi `String` |
| Muốn dùng `var` với generic factory rỗng | `var x = List.<String>of();` | Type witness nêu rõ type argument |
| Muốn utility generic cho nhiều loại dữ liệu | `<T> ...` và để compiler suy luận từ input | Một thuật toán, vẫn kiểm tra kiểu |
| Muốn diễn đạt rõ một type argument của method | `Utils.<Number>choose(1, 2.5)` | Hướng dẫn suy luận method, không cast |
| Không biết kiểu phần tử, chủ yếu đọc | `List<?> items` | Giữ an toàn wildcard |
| Muốn công khai API phụ thuộc interface | Khai báo `List<T>`, `Map<K,V>` ở boundary | Giảm phụ thuộc vào implementation |
| Muốn giữ code ngắn khi kiểu đã rõ | `var` cho biến cục bộ phù hợp | Ít lặp, vẫn type-safe nếu initializer đúng |

Đây không phải bảng yêu cầu phải dùng một phong cách duy nhất. Khi quyết định giữa `var` và kiểu tường minh, tiêu chí quan trọng là **người đọc có nhận diện đúng loại dữ liệu và hợp đồng kiểu từ vị trí khai báo hay không**.

## 21. Bài thực hành tổng hợp có kết quả cụ thể

Tạo tệp **`InferencePracticeDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class InferencePracticeDemo {

    static <T> List<T> copy(List<? extends T> source) {
        return new ArrayList<>(source);
    }

    public static void main(String[] args) {
        List<String> one = new ArrayList<>();
        var two = new ArrayList<String>();
        var three = new ArrayList<>();
        var four = new ArrayList<>(List.of("A", "B"));

        one.add("one");
        two.add("two");
        three.add("three");
        three.add(3);
        four.add("C");

        List<String> copied = copy(four);
        var emptyTyped = List.<String>of();

        System.out.println(one);
        System.out.println(two);
        System.out.println(three);
        System.out.println(four);
        System.out.println(copied);
        System.out.println(emptyTyped.size());
    }
}
```

Biên dịch/chạy:

```bash
javac InferencePracticeDemo.java
java InferencePracticeDemo
```

Stdout:

```text
[one]
[two]
[three, 3]
[A, B, C]
[A, B, C]
0
```

Trước khi chạy, hãy dự đoán:

1. `one` có kiểu tĩnh gì? **Đáp án:** `List<String>`.
2. `two` có kiểu gì? **Đáp án:** `ArrayList<String>`.
3. `three` có kiểu gì? **Đáp án:** `ArrayList<Object>`.
4. `four` có kiểu gì? **Đáp án:** `ArrayList<String>`.
5. Tại sao `copy(four)` có thể trả `List<String>`? **Đáp án:** `List<? extends T>` nhận danh sách phần tử phù hợp và compiler suy luận `T` theo lời gọi cùng kiểu đích.
6. Vì sao `List.<String>of()` vẫn rỗng? **Đáp án:** `<String>` chỉ cung cấp type argument, không tạo phần tử.
7. `three.add(3)` có làm kiểu của `three` chuyển thành `ArrayList<Integer>`? **Đáp án:** Không; nó vốn là `ArrayList<Object>` từ initializer.

Một thử nghiệm nhỏ khác là thay dòng:

```java
var three = new ArrayList<>();
```

bằng:

```java
var three = new ArrayList<String>();
```

Khi đó compiler sẽ từ chối `three.add(3)` vì kiểu phần tử đã là `String`. Đây là cách quan sát trực tiếp tác động của việc thêm đối số kiểu tường minh.

## 22. Phân biệt các lỗi tại compile time

Khi gặp thông báo lỗi, hãy xét nó nằm ở giai đoạn nào:

| Mã gây lỗi | Vấn đề | Sửa theo ý định |
|---|---|---|
| `var x = null;` | Không có kiểu initializer để suy luận | Ghi loại biến: `String x = null;` |
| `var f = s -> s.length();` | Lambda thiếu functional interface target type | Khai báo `Function<String,Integer>` |
| `var x = new ArrayList<>(); String s = x.get(0);` | `E` đã suy ra là `Object` | Thêm target `List<String>` hoặc constructor `<String>` |
| `List<Object> x = new ArrayList<String>();` | Generic invariance | Chọn `List<String>` hoặc wildcard phù hợp |
| `var x = new ArrayList<String>(); x.add(1);` | `Integer` không phù hợp `String` | Chỉ thêm chuỗi hoặc sửa kiểu phần tử có chủ đích |
| `List<String> x = List.of(); x.add("a");` | Factory trả danh sách không thể chỉnh sửa | Chọn `new ArrayList<>(List.of())` nếu cần thay đổi |
| `ChooseInferenceDemo.<String>choose("a", 42)` | Type witness mâu thuẫn với đối số | Dùng các đối số `String` hoặc chọn một kiểu chung phù hợp |

Lỗi compile time do type inference có thể trông dài vì compiler phải giải nhiều ràng buộc. Thay vì sửa bằng cast hoặc raw type, hãy hỏi: **kiểu mong đợi là gì, và ngữ cảnh nào đang cung cấp thông tin đó?**

## 23. Ghi nhớ

Ba cơ chế bổ sung nhau:

- **Diamond `<>`** suy luận đối số kiểu khi gọi constructor của class generic.
- **`var`** suy luận kiểu của **biến cục bộ** từ initializer; kiểu sau suy luận vẫn cố định và được compiler kiểm tra.
- **Đối số kiểu tường minh** như `new ArrayList<String>()`, `List.<String>of()` hoặc `Utility.<Number>method(...)` phát biểu rõ loại cần dùng và có thể hướng dẫn generic type inference.

`List<String> x = new ArrayList<>()` và `var x = new ArrayList<>()` rất dễ bị nhầm, nhưng trường hợp đầu có target type `String`, trường hợp sau thường suy ra `ArrayList<Object>` nếu constructor không cung cấp ràng buộc khác. Ngược lại, `var x = new ArrayList<String>()` và `var x = new ArrayList<>(List.of("a"))` vẫn giữ thông tin `String`.

**Hãy chọn cú pháp dựa trên nguồn thông tin kiểu thực sự có mặt tại nơi khởi tạo.** Compiler suy luận từ khai báo, đối số và ngữ cảnh; nó không “học thêm” loại phần tử từ các thao tác `add(...)` về sau.

Tài liệu chuẩn để đối chiếu:

- [Java Language Specification §14.4 — Local Variable Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.4): `var` và quy tắc suy luận kiểu biến cục bộ.
- [Java Language Specification §15.9 — Class Instance Creation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.9): diamond và tạo đối tượng.
- [Java Language Specification §18 — Type Inference](https://docs.oracle.com/javase/specs/jls/se17/html/jls-18.html): hệ thống ràng buộc để suy luận kiểu.
- [Java Language Specification §15.12 — Method Invocation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12): generic method invocation và explicit type arguments.
- [Java Language Specification §4.5 — Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5): generic type arguments và quan hệ kiểu.
- [Java Language Specification §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): vì sao compile-time generic không sinh class runtime riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-integrated-practice-006">Tái hiện raw type và heap pollution rồi sửa tại nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# Tái hiện raw type và heap pollution rồi sửa tại nguồn trong Java Generics

## 1. Mục tiêu của bài thực hành

Java Generics giúp compiler ngăn một `List<String>` nhận nhầm `Integer`, nhưng sự bảo đảm đó có thể bị phá vỡ khi chương trình dùng **raw type**, cast không được kiểm chứng, generic varargs không an toàn, hoặc truyền dữ liệu qua các API legacy không còn thông tin generic đầy đủ.

Hiện tượng **heap pollution** xảy ra khi một biến có kiểu tham số hóa trỏ tới một đối tượng không thỏa kiểu tham số hóa được kỳ vọng. Trong ví dụ trọng tâm, biến được khai báo `List<String>` nhưng chính danh sách ấy lại chứa một `Integer` sau khi có code gọi `add` thông qua tham chiếu raw `List`.

Bài này tập trung vào **thực hành tái hiện → đọc cảnh báo và lỗi → truy ra điểm gây ô nhiễm → sửa tại nguồn → kiểm tra không tái phát**, thay vì chỉ đặt `@SuppressWarnings` quanh các cảnh báo.

Bạn sẽ học cách:

- Phân biệt `List` raw, `List<Object>`, `List<?>` và `List<String>`.
- Tái hiện việc ghi Integer vào một danh sách có hợp đồng String qua raw alias.
- Giải thích tại sao câu lệnh ghi có thể chạy xong nhưng `ClassCastException` lại xảy ra **ở phía đọc**.
- Bật `-Xlint:rawtypes,unchecked` và hiểu các cảnh báo nào cần sửa ở dòng nào.
- Viết lại một API legacy thành generic method với kiểu đúng ngay ở điểm thêm phần tử.
- Xây dựng ranh giới kiểm tra dữ liệu `List<?>` trước khi chuyển sang `List<String>`.
- Dùng `Class<T>.cast` và `Collections.checkedList` đúng phạm vi, biết rõ những giới hạn của chúng.
- Tái hiện một trường hợp heap pollution khác liên quan đến generic varargs.
- Kiểm tra code Java 17 hợp lệ, phân biệt compile error, compiler warning và runtime exception.

## 2. Từ generic safety đến lỗ hổng do raw type

Trong mã typed bình thường:

```java
List<String> names = new ArrayList<>();
names.add("An");
names.add("Binh");
// names.add(42); // Không biên dịch.
```

Compiler biết method `List<String>.add` yêu cầu String. Nếu cố truyền 42, compiler sẽ không cho build. Đây là chỗ generic type checking có giá trị nhất: lỗi bị ngăn **ngay tại điểm ghi**.

Nhưng Java còn hỗ trợ **raw type** để tương thích với mã trước Java 5:

```java
List raw = names;
raw.add(42);
```

Raw `List` không khai báo type argument, làm compiler mất thông tin về kiểu phần tử ở tham chiếu `raw`. Lời gọi `raw.add(42)` có thể được chấp nhận **kèm cảnh báo unchecked**, trong khi object thực vẫn là đúng `ArrayList` mà `names` đang tham chiếu. Sau lời gọi, `names` có thể chứa một Integer không phù hợp với `List<String>`.

Đây không phải do Java tạo hai danh sách khác nhau. Hai biến `names` và `raw` là **hai alias** tới cùng object. Không có bản sao tự sinh khi bạn gán `List raw = names`.

### Những kiểu dễ nhầm

| Kiểu tham chiếu | Ý nghĩa | Khả năng ghi/đọc |
| --- | --- | --- |
| `List<String>` | Hợp đồng phần tử là String. | `add(String)` được; `get` được nhìn nhận là String. |
| `List<Object>` | Hợp đồng cho phép phần tử Object. | Có thể thêm String, Integer và Object theo API; không tự nhận phép gán từ `List<String>`. |
| `List<?>` | Có một kiểu phần tử chưa biết. | Đọc an toàn dưới Object; không thêm một giá trị non-null tùy ý. |
| `List` raw | Bỏ type argument của giao diện generic. | Có thể gọi `add(42)` với cảnh báo unchecked; dễ phá hợp đồng của alias typed khác. |

**`List<Object>` không phải cách viết mới của raw `List`.** `List<Object>` vẫn là một parameterized type được compiler kiểm tra. `List<?>` cũng không phải raw: nó giữ ràng buộc rằng kiểu phần tử tồn tại nhưng chưa biết, và ngăn thao tác ghi thiếu bằng chứng.

Một ví dụ:

```java
List<String> names = new ArrayList<>();
List<?> view = names;
Object item = view.isEmpty() ? null : view.get(0);

// view.add(42); // Không biên dịch.
```

`List<?>` là một view **không thể thêm giá trị non-null tùy ý theo kiểu tĩnh**, nhưng không có nghĩa collection tự trở thành immutable. Nếu một alias typed khác sửa được danh sách, view sẽ quan sát nội dung thay đổi; một số thao tác như `clear()` hoặc chèn `null` cũng tùy API và collection cụ thể. Vì vậy “read-only” ở đây là cách nói về **khả năng ghi phần tử typed**, không phải lời hứa bất biến toàn diện.

## 3. Tái hiện heap pollution do raw alias

Đầu tiên, lưu chương trình `RawHeapPollutionDemo.java` ở phần 7 và chạy đúng lệnh biên dịch. Đây là một đoạn rút gọn để hiểu cốt lõi:

```java
static void legacyAppend(List raw) {
    raw.add(42);
}

List<String> names = new ArrayList<>(List.of("An"));
legacyAppend(names);

System.out.println(names); // [An, 42]
String second = names.get(1); // ClassCastException ở runtime.
```

Đoạn này minh họa method và các lệnh cần đặt trong class/main thích hợp; chương trình hoàn chỉnh ở phần 7 có imports và bắt exception.

Diễn tiến:

1. Caller tạo một `ArrayList` và giữ nó dưới tham chiếu `List<String>`.
2. `legacyAppend(List raw)` nhận **cùng object** thông qua một tham số raw.
3. `raw.add(42)` chèn một Integer. Không có kiểm tra runtime trong ArrayList rằng type argument của alias ban đầu là String.
4. In danh sách cho kết quả `[An, 42]` vì `toString()` có thể trình bày cả String lẫn Integer dưới Object.
5. `String second = names.get(1)` yêu cầu String. Compiler đã chấp nhận phép đọc theo type argument của `names`.
6. Khi kết quả thực là Integer, phép cast sang String do compiler chèn tại vị trí sử dụng thất bại và ném `ClassCastException`.

**Điểm gây lỗi thật sự là dòng `raw.add(42)`, không phải lỗi thiết kế của getter String.** Exception chỉ biểu hiện ở phía đọc. Đó là lý do thông báo stack trace và nơi dữ liệu bị làm sai có thể ở cách nhau rất xa trong codebase.

### Hai cảnh báo compiler điển hình

Khi bật:

```bash
javac --release 17 -Xlint:rawtypes,unchecked RawHeapPollutionDemo.java
```

Compiler đã được kiểm chứng tạo hai cảnh báo từ code raw của ví dụ:

```text
[rawtypes] found raw type: List
[unchecked] unchecked call to add(E) as a member of the raw type List
```

Cảnh báo thứ nhất chỉ ra tham số `List raw` thiếu type arguments. Cảnh báo thứ hai đánh dấu đúng thao tác ghi mà compiler không thể bảo đảm an toàn.

Đừng xem đây là “warning vô hại vì code vẫn chạy”. Code **thực sự đã chèn sai loại phần tử**.

## 4. Vì sao exception nổ lúc đọc thay vì lúc ghi?

Generic type erasure khiến `List<String>` và `List<Integer>` không có hai runtime class riêng. Ở bytecode, lời gọi `List.get(int)` trả về `Object` theo erased signature. Compiler có thể chèn một `checkcast java/lang/String` tại điểm caller sử dụng giá trị dưới kiểu String.

Từ góc nhìn khái niệm:

```java
// Logic nguồn:
String second = names.get(1);

// Minh họa công việc tương đương sau erasure:
Object element = names.get(1);
String second = (String) element;
```

Đây là **minh họa cơ chế**, không phải mã nguồn javac xuất nguyên xi. JVM có thể tối ưu hóa hoặc tạo bytecode khác tùy ngữ cảnh, nhưng về nguyên tắc, kết quả của `get` phải tương thích với String tại điểm sử dụng.

Vì vậy:

```java
System.out.println(names);       // Có thể in [An, 42].
Object value = names.get(1);     // Có thể đọc Integer dưới Object.
String text = names.get(1);      // ClassCastException.
```

Nếu debug chỉ tập trung vào dòng `String text = ...`, bạn có thể sửa nhầm bằng cách thêm cast hoặc đổi text thành Object. Việc ấy **không sửa dữ liệu bị ô nhiễm** và có thể chỉ dời lỗi sang chỗ khác.

### Tại sao không có `ArrayStoreException`?

Array Java là reified: một `String[]` thực giữ thông tin component type String và JVM có thể chặn Object[] alias ghi Integer bằng `ArrayStoreException`.

Một `ArrayList<String>` thông thường không mang runtime component type “String” như mảng String[]; bản thân ArrayList lưu các tham chiếu Object. Vì vậy ghi qua raw API có thể đi qua, và lỗi chỉ bộc lộ khi một thao tác sau đó đòi String.

**Khác biệt:** `ArrayStoreException` gắn với runtime array component checking; `ClassCastException` trong ví dụ gắn với ép kiểu tại phía sử dụng. Cùng là lỗi kiểu runtime, nhưng nguyên nhân và điểm kiểm tra khác nhau.

## 5. Quy trình tìm nguồn ô nhiễm trong dự án

Khi production báo `ClassCastException: Integer cannot be cast to String` ở một getter generic:

1. **Xác định collection bị lỗi**: instance nào, ai tạo, được chia sẻ cho những method nào?
2. **Kiểm tra đường alias**: có biến `List raw`, `Map raw`, `Collection raw`, hoặc Object[] alias không?
3. **Tìm cảnh báo unchecked/rawtypes**: đây thường là nơi compiler đã mất khả năng kiểm tra dữ liệu.
4. **Tìm các thao tác ghi**: `add`, `set`, `put`, `addAll`, reflection hay adapters từ API legacy.
5. **Kiểm tra cast unchecked**: có `(List<String>) unknown` mà không validate phần tử không?
6. **Xem dữ liệu runtime thật**: in `getClass().getName()` của phần tử lỗi dưới tham chiếu Object thay vì ép String lại.
7. **Sửa tại ranh giới ghi**: dùng signature typed hoặc kiểm tra dữ liệu đầu vào, không chỉ bắt `ClassCastException` ở getter.
8. **Thêm test regression**: chứng minh thao tác ghi Integer bị compiler từ chối hoặc boundary trả lỗi chủ động.
9. **Xem các alias cũ có còn giữ mutable reference không**: bản vá signature mới có thể chưa đủ nếu API cũ vẫn chia sẻ collection.
10. **Nâng kiểm tra build**: tập trung loại bỏ `rawtypes` và `unchecked` mới, sau đó siết build khi khả thi.

Nếu ứng dụng lưu dữ liệu từ JSON, JDBC, phản chiếu hoặc kết nối thư viện cũ, hãy xác định cả nơi **convert từ Object sang domain type**. Một phép cast parameterized chỉ xác nhận lớp container ở mức có thể kiểm tra, không chứng minh từng phần tử đúng.

## 6. Sửa tại nguồn: biến API raw thành API typed

Điểm ô nhiễm trong ví dụ:

```java
static void legacyAppend(List raw) {
    raw.add(42);
}
```

Nếu method có nghiệp vụ **thêm tên**, sửa thành:

```java
static void appendName(List<String> target, String name) {
    target.add(name);
}
```

Caller:

```java
List<String> names = new ArrayList<>(List.of("An"));
appendName(names, "Binh");
// appendName(names, 42); // Compile error.
```

Bây giờ compiler có đủ bằng chứng để từ chối một Integer ngay ở boundary. Không cần `@SuppressWarnings` và không cần đợi getter bị crash.

### Một compile error được kiểm chứng

**Tệp `TypedListRejectsWrongValue.java` — cố ý không biên dịch:**

```java
import java.util.List;

class TypedListRejectsWrongValue {
    static void append(List<String> names) {
        names.add(42);
    }
}
```

Chạy:

```bash
javac --release 17 -Xdiags:verbose TypedListRejectsWrongValue.java
```

Compiler báo trọng tâm:

```text
no suitable method found for add(int)
method List.add(String) is not applicable
(argument mismatch; int cannot be converted to String)
```

Đây là hành vi mong muốn: type error bị phát hiện ở lúc biên dịch, trước khi dữ liệu sai được chèn vào collection.

### Nếu nghiệp vụ thực sự nhận nhiều kiểu thì sao?

Không phải mọi method raw nên được đổi thành `List<String>`. Nếu method có trách nhiệm thêm **một giá trị đúng kiểu mà caller chỉ định**, hãy dùng một generic method:

```java
static <T> void appendTyped(List<T> target, T value) {
    target.add(value);
}
```

Tuy nhiên đừng mặc định rằng `<T> void appendTyped(List<T>,T)` luôn bắt caller truyền hai biểu thức có cùng exact runtime class; inference cần được phân tích theo cả các ràng buộc, có thể chọn supertype chung trong những ngữ cảnh thích hợp. Nếu contract bắt buộc danh sách String, signature cụ thể `List<String>` là rõ nhất.

Nếu method **chỉ đọc** collection của mọi kiểu:

```java
static void printElements(List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

`List<?>` phù hợp hơn raw List vì giữ nguyên giới hạn kiểu ở compile time. Nó không cho phép thêm tùy ý một Object vào list vốn có thể là List<String>.

## 7. Chương trình Java 17 hoàn chỉnh: tái hiện và sửa

**Tệp `RawHeapPollutionDemo.java`:**

```java
import java.util.ArrayList;
import java.util.List;

public class RawHeapPollutionDemo {
    static void legacyAppend(List raw) {
        raw.add(42);
    }

    static void appendName(List<String> target, String name) {
        target.add(name);
    }

    static List<String> checkedStringCopy(List<?> input) {
        List<String> result = new ArrayList<>();
        for (Object item : input) {
            if (!(item instanceof String text)) {
                throw new IllegalArgumentException("not String: " + item);
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    static <T> List<T> checkedCopy(List<?> input, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Object item : input) {
            result.add(type.cast(item));
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        List<String> names = new ArrayList<>(List.of("An"));
        legacyAppend(names);
        System.out.println("runtimeContent=" + names);

        try {
            String second = names.get(1);
            System.out.println("read=" + second);
        } catch (ClassCastException error) {
            System.out.println("rawFailure="
                    + error.getClass().getSimpleName());
        }

        List<String> fixed = new ArrayList<>(List.of("An"));
        appendName(fixed, "Binh");
        System.out.println("typed=" + fixed);

        System.out.println("checked="
                + checkedStringCopy(List.of("A", "B")));

        try {
            checkedStringCopy(names);
        } catch (IllegalArgumentException error) {
            System.out.println("rejected=" + error.getMessage());
        }

        System.out.println("tokenCopy="
                + checkedCopy(List.of("C", "D"), String.class));

        try {
            checkedCopy(List.of("A", 42), String.class);
        } catch (ClassCastException error) {
            System.out.println("tokenRejected="
                    + error.getClass().getSimpleName());
        }

        List<?> readOnlyView = names;
        System.out.println("unknownSize=" + readOnlyView.size());
        System.out.println("unknownElement="
                + readOnlyView.get(1).getClass().getSimpleName());
    }
}
```

**Biên dịch với cảnh báo được bật:**

```bash
javac --release 17 -Xlint:rawtypes,unchecked RawHeapPollutionDemo.java
java RawHeapPollutionDemo
```

Lưu ý: Chương trình này **cố tình dùng raw API sai**, nên compiler phát hai warning như phần 3. Những warning là một phần của bài thực hành, không phải lỗi của môi trường JDK.

**Stdout đã chạy và kiểm chứng:**

```text
runtimeContent=[An, 42]
rawFailure=ClassCastException
typed=[An, Binh]
checked=[A, B]
rejected=not String: 42
tokenCopy=[C, D]
tokenRejected=ClassCastException
unknownSize=2
unknownElement=Integer
```

### Đọc từng kết quả

- `runtimeContent=[An, 42]`: raw write đã thay đổi cùng một object mà typed reference đang giữ.
- `rawFailure=ClassCastException`: exception xuất hiện tại lúc lấy phần tử thứ hai dưới kiểu String.
- `typed=[An, Binh]`: sau khi sửa method signature thành `List<String>`, thao tác thêm đúng loại được compiler kiểm soát.
- `checked=[A, B]`: boundary tạo bản sao đã xác thực từng phần tử là String.
- `rejected=not String: 42`: boundary phát hiện input sai và báo lỗi có ý nghĩa nghiệp vụ, thay vì để CCE phát sinh muộn.
- `tokenCopy=[C, D]`: phiên bản có `Class<T>` tạo bản sao typed sau khi gọi `type.cast` cho từng giá trị.
- `tokenRejected=ClassCastException`: type token chặn Integer khi caller yêu cầu String.
- `unknownSize=2` và `unknownElement=Integer`: một `List<?>` vẫn quan sát được object sai dưới Object mà không cần cast sang String.

### Tại sao code có thể compile mà vẫn có warning?

`javac` hỗ trợ raw types vì Java cần tương thích mã cũ. Dù `raw.add(42)` không được kiểm tra generic đầy đủ, javac vẫn cho biên dịch nếu tình huống chỉ gây warning và không vi phạm những ràng buộc bắt buộc khác. Đây là **cảnh báo về an toàn kiểu**, và chương trình chứng minh bằng runtime failure rằng cảnh báo có thể là lỗi thật.

Trong code production không chủ đích minh họa, nên sửa raw method và dần đặt chính sách build giảm/loại bỏ warning.

## 8. Dữ liệu đã đến dưới `List<?>`: kiểm tra và copy an toàn

Đôi khi dữ liệu từ bên ngoài chỉ có thể được biết là một danh sách chưa rõ kiểu phần tử:

```java
List<?> rawInput = someExternalApi();
```

Đây là cú pháp minh họa; `someExternalApi` phải do ứng dụng cung cấp, không có sẵn trong JDK.

**Không thể** chuyển dữ liệu thành List String chỉ bằng:

```java
// KHÔNG AN TOÀN:
@SuppressWarnings("unchecked")
List<String> names = (List<String>) rawInput;
```

Cast từ `List<?>` sang `List<String>` là unchecked, vì JVM không kiểm tra mọi phần tử generic ở runtime. Một cast thành công chỉ cho biết container phù hợp mức runtime có thể xác minh; phần tử bên trong có thể vẫn là Integer.

### Cách sửa: duyệt dưới Object rồi xây collection typed mới

```java
static List<String> checkedStringCopy(List<?> input) {
    List<String> result = new ArrayList<>();
    for (Object item : input) {
        if (!(item instanceof String text)) {
            throw new IllegalArgumentException("not String: " + item);
        }
        result.add(text);
    }
    return List.copyOf(result);
}
```

Ưu điểm:

1. Mỗi phần tử được kiểm tra trước khi đưa vào List String.
2. Chỉ các giá trị thật sự là String mới được thêm vào `result`.
3. Không cần unchecked cast.
4. Trả về một snapshot không sửa được về mặt cấu trúc, giúp tránh một alias mutable bên ngoài sửa collection mới sau khi validation.
5. Nếu gặp một giá trị không phải String, exception xuất hiện **ngay tại ranh giới dữ liệu**, kèm thông điệp dễ xác định.

Ví dụ:

```java
List<String> a = checkedStringCopy(List.of("A", "B"));
// checkedStringCopy(List.of("A", 42)); // IllegalArgumentException.
```

Phải làm rõ chính sách null: `null instanceof String` trả false, nên hàm này chủ động từ chối null. `List.copyOf` cũng không nhận phần tử null. Đây là lựa chọn contract cụ thể, không phải một tính chất tự động của mọi List String.

### Defensive copy không phải deep copy

`List.copyOf(result)` tạo collection không sửa được về mặt cấu trúc, không tự làm immutable các object bên trong. Với String điều đó ít vấn đề vì String immutable; với `List<MutableEntity>`, những MutableEntity vẫn có thể thay đổi trạng thái. Nếu cần deep immutability, phải clone/convert object thành dữ liệu bất biến phù hợp.

## 9. Generic boundary dùng `Class<T>`

Khi một adapter phải hỗ trợ nhiều kiểu runtime đơn giản, có thể nhận type token:

```java
static <T> List<T> checkedCopy(List<?> input, Class<T> type) {
    List<T> result = new ArrayList<>();
    for (Object item : input) {
        result.add(type.cast(item));
    }
    return List.copyOf(result);
}
```

Caller:

```java
List<String> names = checkedCopy(List.of("A", "B"), String.class);
List<Integer> ids = checkedCopy(List.of(1, 2), Integer.class);
```

`Class<T>.cast` là một runtime check dựa trên Class token. Nếu một giá trị không tương thích với String.class, nó ném ClassCastException tại boundary.

**Giới hạn cần hiểu:**

- `String.class` kiểm tra String, `Integer.class` kiểm tra Integer.
- Không tồn tại class literal `List<String>.class` để kiểm tra sâu nested type arguments.
- `List.class` chỉ kiểm tra container List, không kiểm tra từng String bên trong.
- `Class.cast(null)` chấp nhận null; nhưng ví dụ `checkedCopy` dùng `List.copyOf` nên null sẽ bị từ chối ở bước copy. Nếu null là dữ liệu hợp lệ trong nghiệp vụ, cần một contract và implementation khác.

Một framework nhận mô tả `Type`/`ParameterizedType` có thể biết expected generic element type, nhưng vẫn phải **tự thực hiện validation/deserialization**. Metadata không biến JVM thành cơ chế tự kiểm tra mọi kiểu generic lồng nhau.

## 10. `Collections.checkedList`: chặn raw write động tại boundary

Nếu vẫn phải tương tác với một API legacy giữ tham chiếu `List` raw và có thể gọi `add`, `Collections.checkedList` cung cấp **dynamic type-checking view** cho các thao tác ghi đi qua wrapper.

Ví dụ:

```java
List<String> checkedNames =
        Collections.checkedList(new ArrayList<>(), String.class);

checkedNames.add("An");
List raw = checkedNames; // Raw alias tại boundary legacy.
raw.add(42);             // ClassCastException ngay tại thao tác ghi.
```

Đoạn trên cần import `Collections`, `ArrayList`, `List` và cố ý tạo warning khi dùng raw.

Ý nghĩa: wrapper biết String.class, nên kiểm tra phần tử được thêm qua wrapper ở runtime. Đây là một tuyến phòng vệ bổ sung khi không thể sửa toàn bộ code legacy ngay lập tức.

### Chương trình kiểm chứng hành vi wrapper

**Tệp `CheckedBoundaryDemo.java`:**

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CheckedBoundaryDemo {
    static List<String> makeCheckedNames() {
        return Collections.checkedList(new ArrayList<>(), String.class);
    }

    static void legacyWrite(List raw, Object value) {
        raw.add(value);
    }

    public static void main(String[] args) {
        List<String> checkedNames = makeCheckedNames();
        checkedNames.add("An");
        try {
            legacyWrite(checkedNames, 42);
        } catch (ClassCastException e) {
            System.out.println("writeRejected="
                    + e.getClass().getSimpleName());
        }
        System.out.println("checkedContents=" + checkedNames);

        List<String> storage = new ArrayList<>(List.of("An"));
        List<String> wrapper =
                Collections.checkedList(storage, String.class);
        legacyWrite(storage, 42);
        System.out.println("wrapperDidNotRetroactivelyValidate="
                + wrapper);
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 -Xlint:rawtypes,unchecked CheckedBoundaryDemo.java
java CheckedBoundaryDemo
```

Code cố ý có raw `List` và unchecked write ở `legacyWrite`, nên compiler phát hai warnings về raw type/unchecked call trong kiểm thử.

**Stdout đã kiểm chứng:**

```text
writeRejected=ClassCastException
checkedContents=[An]
wrapperDidNotRetroactivelyValidate=[An, 42]
```

### Hai giới hạn quan trọng của checked wrapper

**Thứ nhất:** checked wrapper **không tự quét để sửa hoặc bác bỏ tất cả phần tử đã có** trong collection khi vừa được tạo. Nếu collection gốc đã polluted, việc bọc nó không tự làm dữ liệu sạch.

**Thứ hai:** nếu code vẫn giữ một alias trực tiếp tới collection gốc, thao tác ghi qua alias đó **đi vòng qua wrapper**. Trong ví dụ, biến `storage` vẫn truy cập ArrayList gốc nên `legacyWrite(storage, 42)` thành công và wrapper nhìn thấy `[An,42]` sau đó.

Do đó, checked view là tuyến bảo vệ hữu ích ở boundary đã được kiểm soát, nhưng không thay thế việc bỏ raw aliases và sửa signature API. Hạn chế quyền truy cập collection gốc nếu bạn muốn mọi thao tác đi qua wrapper.

## 11. Heap pollution khác: generic varargs và mảng không reifiable

Heap pollution không chỉ đến từ raw `List`. Một nguồn khác là **generic varargs**, nơi compiler phải kết hợp parameterized types với array ở runtime.

Xem ví dụ cố ý không an toàn:

**Tệp `UnsafeVarargsPollutionDemo.java`:**

```java
import java.util.List;

public class UnsafeVarargsPollutionDemo {
    static void corrupt(List<String>... groups) {
        Object[] alias = groups;
        alias[0] = List.of(42);
        String item = groups[0].get(0);
        System.out.println(item);
    }

    public static void main(String[] args) {
        try {
            corrupt(List.of("A"), List.of("B"));
        } catch (ClassCastException error) {
            System.out.println("varargsFailure="
                    + error.getClass().getSimpleName());
        }
    }
}
```

Biên dịch:

```bash
javac --release 17 -Xlint:all UnsafeVarargsPollutionDemo.java
java UnsafeVarargsPollutionDemo
```

**Đã kiểm chứng:** `javac` phát hai cảnh báo chính:

```text
[unchecked] Possible heap pollution from parameterized vararg type List<String>
[unchecked] unchecked generic array creation for varargs parameter of type List<String>[]
```

Stdout:

```text
varargsFailure=ClassCastException
```

### Diễn tiến trong trường hợp varargs

1. Tham số `List<String>... groups` được biểu diễn qua một mảng mà component runtime không thể phân biệt List String với List Integer.
2. Mảng danh sách được nhìn qua alias `Object[]`.
3. `alias[0] = List.of(42)` vượt được array store check vì phần tử mới vẫn là một List ở cấp runtime.
4. `groups[0].get(0)` trả Integer, nhưng phía sử dụng muốn String.
5. ClassCastException nổ khi kiểu String được yêu cầu.

Đây là **đường gây heap pollution khác**, không phải lỗi raw List ban đầu. Nó cho thấy vì sao một signature nhìn có generic vẫn có thể gặp nguy hiểm nếu code dùng non-reifiable varargs array sai cách.

### Không che bằng `@SafeVarargs` một cách máy móc

`@SafeVarargs` là lời cam kết của tác giả rằng body không thao tác không an toàn với varargs theo những trường hợp được compiler cho phép khai báo annotation. **Method `corrupt` ở trên không an toàn**, vì nó cố ý ghi một List Integer vào vị trí dự kiến List String. Không được gắn `@SafeVarargs` chỉ để tắt warning.

Nếu API thật sự cần nhận nhiều danh sách, hãy cân nhắc một tham số `List<? extends List<String>>` hoặc một collection typed của các group, hoặc viết varargs method không ghi/không công khai mảng, rồi đánh giá an toàn trước khi sử dụng annotation hợp lệ.

## 12. Vì sao `@SuppressWarnings("unchecked")` không sửa được lỗi?

Annotation chỉ yêu cầu compiler không hiển thị một loại cảnh báo ở phạm vi được chỉ định. Nó không:

- Kiểm tra phần tử trong một `List`.
- Chuyển Integer thành String.
- Khôi phục generic type arguments đã bị erasure.
- Chặn raw alias ghi sai dữ liệu.
- Ngăn `ClassCastException`.
- Sửa generic varargs implementation không an toàn.

Ví dụ:

```java
@SuppressWarnings("unchecked")
static List<String> unsafeAssumeStrings(Object obj) {
    return (List<String>) obj;
}
```

Phương thức có thể trả một List thực sự chứa Integer. Caller đọc một phần tử dưới String sẽ gặp lỗi. Việc warning biến mất không có nghĩa dữ liệu đã sạch.

### Khi nào suppress có thể được xem xét?

Một adapter legacy đôi khi cần cast mà compiler không chứng minh được, nhưng lập trình viên có bằng chứng từ protocol/schema/validation riêng. Trong trường hợp đó:

1. Xác minh bằng chứng **trước cast**; tốt nhất là validate dữ liệu khi có thể.
2. Giới hạn phạm vi suppress vào **một biến hoặc một method adapter nhỏ**, không tắt toàn class/package tùy tiện.
3. Ghi rõ invariant mà adapter dựa vào.
4. Viết test có cả input đúng và sai kiểu.
5. Ưu tiên thay đổi API upstream để cast không còn cần thiết.

Tuyệt đối không xem suppress là bước “sửa tại nguồn”. Sửa tại nguồn nghĩa là phục hồi một contract typed và bảo vệ boundary chèn/nhận dữ liệu.

## 13. Phân biệt heap pollution và các lỗi liên quan

| Hiện tượng | Nơi xuất hiện | Dấu hiệu |
| --- | --- | --- |
| Raw type | Mã nguồn Java | `List raw` thay vì `List<String>`/`List<?>`. |
| Unchecked operation | Biên dịch | `[unchecked] unchecked call`, cast hoặc generic array creation. |
| Heap pollution | Trạng thái kiểu của reference/đối tượng trong chương trình | Tham chiếu typed nhìn dữ liệu không thực sự phù hợp với type argument. |
| ClassCastException | Runtime | Code yêu cầu String nhưng gặp Integer. |
| ArrayStoreException | Runtime | Array có component type runtime ngăn ghi object sai loại. |
| Compile-time type error | Biên dịch | `List<String>.add(42)` bị từ chối ngay. |
| UnsupportedOperationException | Runtime | Collection không cho thao tác sửa, dù kiểu phần tử phù hợp. |
| NullPointerException | Runtime | Dữ liệu null vi phạm contract của thao tác, chẳng hạn `List.copyOf`. |

Một collection có thể **vừa heap-polluted vừa có thể in ra bình thường**. Heap pollution là sai phạm về contract kiểu, không nhất thiết làm chương trình crash ngay lập tức.

Một method có generic type signature hợp lệ vẫn có thể không an toàn nếu dùng unchecked cast hoặc alias không kiểm soát. Compiler bảo vệ **những đường đi có đủ thông tin kiểu**, không chứng minh toàn bộ logic runtime của mọi thư viện bên ngoài.

## 14. Sửa tại nguồn: kế hoạch chuyển đổi API legacy thực tế

Trong dự án lớn, xóa raw types có thể phải đi theo từng bước vì có API cũ và consumer chưa cập nhật.

### Giai đoạn A — Cô lập và đo warning

Chạy:

```bash
javac --release 17 -Xlint:rawtypes,unchecked *.java
```

Lệnh wildcard có cách mở rộng khác nhau tùy shell/IDE/build tool; trong Maven/Gradle, cấu hình tương ứng cho JavaCompile/Javac nếu cần. Với ví dụ bài này, hãy compile từng file riêng như hướng dẫn.

Phân loại warnings:

- Raw declarations do chính code đang kiểm soát.
- Unchecked calls/assignments từ API cũ.
- Casts cố ý nhưng chưa có validation.
- Generic varargs và các thao tác array alias.
- Reflection/deserialization boundaries.

Ưu tiên sửa **đường ghi vào collection typed** vì nó có thể làm hỏng dữ liệu được chia sẻ cho nhiều consumer.

### Giai đoạn B — Cung cấp API mới an toàn

Ví dụ API cũ:

```java
void addRecords(List list, Object item);
```

Thiết kế typed tùy domain:

```java
void addNames(List<String> names, String name);
```

hoặc generic method:

```java
<T> void addRecord(List<T> target, T item);
```

Các chữ ký ở trên là declaration mô tả thiết kế; cần class/interface và implementation khi đưa vào project.

Với input bên ngoài chưa xác định type argument, dùng `List<?>` để đọc và validation/copy để tạo data structure typed mới.

### Giai đoạn C — Giữ tương thích có kiểm soát

Nếu còn caller cũ dùng raw API, có thể tạm tạo một **adapter nhỏ**:

- Không để raw list tiếp tục đi sâu vào core business logic.
- Chuyển Object sang domain type ở boundary.
- Từ chối sai loại với thông báo rõ.
- Trả collection typed mới thay vì phát tán alias mutable.
- Dùng dynamic checked view chỉ ở những nơi chưa thể sửa caller, nhớ giới hạn alias và dữ liệu tồn tại.

Không nên chỉ đổi kiểu biến local `List raw` thành `List<String>` bằng unchecked cast mà không kiểm tra phần tử; đó là **đổi nhãn type**, không đổi dữ liệu.

### Giai đoạn D — Siết build sau khi đã sửa

Khi các warning chủ động được xử lý, có thể cấu hình compiler để phát hiện những thao tác raw/unchecked mới:

```bash
javac --release 17 -Xlint:rawtypes,unchecked -Werror SafeSource.java
```

`-Werror` khiến warning làm compile thất bại, nên chỉ bật sau khi codebase có baseline phù hợp. Khi cần tạm migration, khoanh vùng legacy adapter với tài liệu rõ ràng và deadline kỹ thuật, không tắt cảnh báo vô hạn.

### Giai đoạn E — Regression tests

Nên có hai chiều kiểm thử:

- **Tích cực:** API typed nhận String và tạo output đúng.
- **Tiêu cực:** cố thêm Integer vào List<String> không compile hoặc boundary runtime chủ động từ chối nó.

Tách các test compile-negative thành file/tình huống riêng để build chính không bị hỏng có chủ đích.

## 15. Những bẫy thường gặp khi di chuyển từ raw sang generic

**Bẫy 1 — Dùng `List<Object>` để thay raw ở mọi chỗ.** Điều này thay đổi contract, thường không nhận được `List<String>` vì invariance. Nếu chỉ đọc bất kỳ list, `List<?>` thích hợp hơn.

**Bẫy 2 — Cast `List<?>` sang `List<String>` để tiện dùng.** Compiler không xác minh được String trong từng phần tử; cast unchecked có thể che heap pollution.

**Bẫy 3 — Chỉ bắt `ClassCastException` ở nơi đọc.** Bạn mới che triệu chứng; điểm raw write vẫn làm hỏng dữ liệu mà consumer khác có thể đọc.

**Bẫy 4 — Gắn `@SuppressWarnings("unchecked")` lên cả class.** Cảnh báo biến mất nhưng kiểm tra generic vẫn bị bỏ qua tại các nơi nguy hiểm.

**Bẫy 5 — Xem `Collections.checkedList` như phép làm sạch dữ liệu.** Checked view không quét sửa nội dung có sẵn và không chặn thao tác qua alias tới backing list.

**Bẫy 6 — Tin type erasure đồng nghĩa runtime không kiểm tra kiểu nào.** JVM vẫn kiểm tra casts, ArrayStore, Class tokens và các phép kiểm tra đối tượng reifiable. Chỉ **type argument không reified** không được JVM tự xác minh sâu.

**Bẫy 7 — Tưởng `List<?>` immutable.** View wildcard ngăn nhiều kiểu add tại compile time nhưng không khiến backing collection bất biến.

**Bẫy 8 — Đổ lỗi cho compiler vì cảnh báo raw List.** Raw types được giữ để tương thích lịch sử; cảnh báo là tín hiệu cho phép bạn tìm và cải thiện điểm mất type information.

**Bẫy 9 — Tự dùng mảng `List<String>[]` và ép kiểu không an toàn.** Các mảng generic non-reifiable dễ dẫn tới heap pollution, nhất là qua Object[] alias hoặc varargs.

**Bẫy 10 — Nghĩ cast checked với Class token giải quyết nested List<String>.** `Class<List>` chỉ biết List; cần validate các phần tử String riêng.

**Bẫy 11 — Bỏ qua tính mutable của nguồn.** Copy hoặc validate một lần không đảm bảo alias mutable bên ngoài không sửa nguồn sau này. Trả bản sao mới không share backing collection giúp tách ranh giới.

**Bẫy 12 — Quên null contract.** `type.cast(null)` không tự bác bỏ null, nhưng `List.copyOf` không chấp nhận null; thống nhất rõ chính sách của adapter.

## 16. Kỹ thuật kiểm tra dữ liệu bị ô nhiễm mà không gây CCE mới

Khi nghi một `List<String>` đã bị chèn sai loại, **đừng lập tức duyệt dưới biến String**:

```java
// Có thể ClassCastException khi gặp phần tử không phải String:
for (String name : names) {
    System.out.println(name.length());
}
```

Thay vào đó, quan sát qua `List<?>`/Object:

```java
List<?> unknown = names;
for (Object item : unknown) {
    System.out.println(item == null
            ? "null"
            : item.getClass().getName());
}
```

Với dữ liệu chứa String và Integer, bạn sẽ thấy các class runtime thật thay vì buộc cast từng phần tử thành String. Đây là một bước **chẩn đoán**, không làm sạch collection.

Sau khi tìm được giá trị sai:

1. Xác minh đường ghi đưa nó vào.
2. Sửa nơi ghi và signature liên quan.
3. Quyết định có nên loại bỏ/convert data cũ không.
4. Trả về một collection mới đã validate, nếu consumer cần invariant rõ.
5. Thêm test bảo vệ cả dữ liệu cũ và data flow mới.

Lưu ý khi in dữ liệu cá nhân hoặc nhạy cảm để debug: chỉ log class/type và thông tin tối thiểu, tránh tự in toàn bộ nội dung khi không cần.

## 17. So sánh ba cách bảo vệ boundary

| Cách | Kiểm tra gì? | Điểm mạnh | Giới hạn |
| --- | --- | --- | --- |
| `List<String>` trong signature | Compiler kiểm tra những lời gọi typed. | Ngăn lỗi ngay điểm viết code. | Raw/unchecked legacy code vẫn có thể vượt qua. |
| `checkedStringCopy(List<?>)` | Từng phần tử String khi copy. | Chặn sai dữ liệu và tạo snapshot mới. | O(n) để duyệt; tự định nghĩa chính sách null/loại lỗi. |
| `checkedCopy(List<?>,Class<T>)` | `Class.cast` từng phần tử. | Tái dùng cho nhiều runtime classes. | Không xác minh nested generic arguments. |
| `Collections.checkedList(backing,String.class)` | Các thao tác chèn phần tử đi qua wrapper. | Bảo vệ runtime tại đường ghi legacy. | Không validate dữ liệu cũ; alias backing có thể đi vòng. |

Không cần áp dụng **tất cả** mọi nơi. Với ứng dụng đã typed sạch, signature và compiler thường đủ ở phần lớn core logic. Boundary từ dữ liệu ngoài cần validation/copy. Wrapper thích hợp như biện pháp phòng vệ bổ sung khi vẫn phải hỗ trợ writer legacy.

## 18. Luyện tập và đáp án

### Bài 1 — Lỗi xuất hiện ở đâu?

```java
List<String> names = new ArrayList<>();
List raw = names;
raw.add(42);
String s = names.get(0);
```

**Đáp án:** Ghi raw chấp nhận kèm warning; ở bước đọc thành String, `ClassCastException` có thể xảy ra. Điểm phải sửa đầu tiên là `raw.add(42)` hoặc chữ ký API cho phép raw write.

### Bài 2 — Có thể dùng `List<Object>` thay `List<?>` để nhận mọi danh sách typed không?

**Đáp án:** Không. `List<String>` không là subtype của `List<Object>`. `List<?>` phù hợp cho API quan sát mọi List mà không tự cho phép thêm phần tử tùy ý.

### Bài 3 — `List<?>` có xóa phần tử sai không?

**Đáp án:** Không. Nó chỉ thay đổi những điều compiler cho phép qua tham chiếu đó; object phía dưới và mọi alias vẫn giữ nguyên dữ liệu.

### Bài 4 — Vì sao getter generic có thể ném ClassCastException khi setter raw chạy xong?

**Đáp án:** Erasure khiến runtime List không kiểm tra deep generic String ở raw write. Khi kết quả `get` được dùng dưới String, compiler có thể chèn cast và phát hiện Integer.

### Bài 5 — Cảnh báo nào nên xem trọng?

**Đáp án:** `[rawtypes]` ở khai báo raw và `[unchecked] unchecked call` ở thao tác ghi là hai tín hiệu trực tiếp của việc mất kiểm tra kiểu. Chúng có thể là nguyên nhân heap pollution.

### Bài 6 — Sửa `void addName(List raw,Object value)` thế nào?

**Đáp án:** Nếu nghiệp vụ thêm String, đổi thành `void addName(List<String> names,String value)`; compiler sẽ chặn Integer ở caller.

### Bài 7 — Khi chưa biết kiểu phần tử của input?

**Đáp án:** Nhận `List<?>`, duyệt từng Object, kiểm tra `instanceof String` rồi copy vào `List<String>` mới. Không cast nguyên danh sách mà không validate.

### Bài 8 — Class token có kiểm tra được `List<String>` không?

**Đáp án:** `String.class` kiểm tra từng String khi bạn gọi `cast` trên phần tử. `List.class` chỉ kiểm tra container, không có `List<String>.class` để JVM quét toàn bộ list.

### Bài 9 — Vì sao `Collections.checkedList` không đủ nếu còn alias backing?

**Đáp án:** Checked wrapper chỉ chặn thao tác đi qua wrapper. Raw writer dùng alias tới backing list có thể chèn sai loại đi vòng.

### Bài 10 — `@SafeVarargs` có được dùng để che warning cho method cố ý ghi List Integer vào mảng List String không?

**Đáp án:** Không. Annotation chỉ hợp lý khi implementation có cam kết an toàn tương ứng; việc đánh dấu một method không an toàn không sửa heap pollution.

### Bài 11 — Heap pollution và ClassCastException có luôn xảy ra cùng một dòng không?

**Đáp án:** Không. Heap pollution có thể được tạo từ raw write, còn CCE xảy ra muộn ở consumer đọc dưới kiểu String.

### Bài 12 — Cách kiểm tra class thật của phần tử mà không cast String?

**Đáp án:** Dùng `List<?>`, duyệt `Object`, sau đó `item.getClass()` với kiểm tra null. Đây là bước chẩn đoán, không phải thay thế validation/copy.

### Bài 13 — Vì sao `String[]` khác `List<String>`?

**Đáp án:** Mảng String giữ component type runtime và có array store check; List<String> bị type erasure ở runtime class và không tự kiểm tra String qua raw List write.

### Bài 14 — Nên xử lý dữ liệu cũ đã polluted thế nào sau khi sửa API?

**Đáp án:** Không mặc định dữ liệu tự sạch. Kiểm tra hoặc chuyển từng phần tử, loại bỏ giá trị không hợp lệ theo contract, tạo collection mới nếu cần và thêm regression tests.

### Bài 15 — Có thể coi code compile không warning là bảo đảm tuyệt đối mọi dữ liệu runtime hợp lệ không?

**Đáp án:** Không. Dữ liệu bên ngoài, reflection, thư viện native, raw legacy và một số unchecked boundary có thể ảnh hưởng. Nhưng việc loại bỏ raw/unchecked trong core logic là bước quan trọng để compiler kiểm tra tốt nhất có thể.

## 19. Checklist khi sửa bug heap pollution

1. **Tái hiện đúng input** khiến danh sách typed chứa phần tử sai.
2. **Ghi lại nơi tạo object** và các alias cùng trỏ vào nó.
3. **Bật compiler warning** với `-Xlint:rawtypes,unchecked`.
4. **Tìm dòng ghi đầu tiên thiếu kiểm tra kiểu**: raw add/set/put, cast, varargs alias.
5. **Định nghĩa invariant mong muốn**: ví dụ mỗi phần tử bắt buộc String và không null.
6. **Sửa signature ở nguồn**: `List<String>` hoặc `List<T>` theo mục tiêu thực tế.
7. **Dùng `List<?>` nếu chỉ đọc**, không thay bằng raw type.
8. **Kiểm tra dữ liệu đến từ ngoài** trước khi nhận dưới kiểu mạnh hơn.
9. **Tạo defensive copy** khi cần ngăn alias mutable làm hỏng dữ liệu sau validation.
10. **Cân nhắc checked wrappers** nếu còn API legacy ghi dữ liệu, nhớ giới hạn backing alias.
11. **Không suppress warning vô căn cứ**; khoanh vùng adapter cần thiết.
12. **Test biên dịch âm** để chắc ghi sai bị compiler từ chối.
13. **Test runtime âm** cho input không hợp lệ ở boundary.
14. **Kiểm tra null, mutability, nested generics và varargs** nếu liên quan.
15. **Đọc lại stack trace**: sau bản sửa, lỗi phải được chặn tại nguồn/boundary thay vì CCE muộn.
16. **Kiểm tra các consumer khác** đã từng dùng shared collection để phát hiện dư âm dữ liệu polluted.

## 20. Tổng kết

Raw type cho phép code Java legacy bỏ type arguments trên một tham chiếu generic. Nếu raw alias ghi giá trị sai vào collection, biến typed khác vẫn nhìn cùng object nhưng đang giữ dữ liệu vi phạm contract — đó là **heap pollution**. Java có thể cảnh báo ở điểm ghi nhưng ném `ClassCastException` muộn tại vị trí đọc.

Giải pháp đúng không phải che stack trace, thêm cast hoặc `@SuppressWarnings`. Hãy **sửa tại nguồn** bằng signature typed, tránh raw aliases, kiểm tra dữ liệu ngay boundary khi type chưa biết, và tạo snapshot mới nếu cần giữ invariant trước các writer bên ngoài. `Collections.checkedList` hoặc `Class<T>` là các công cụ phụ trợ hữu ích, nhưng mỗi công cụ có phạm vi bảo vệ riêng.

**Nguyên tắc quan trọng nhất:** Nơi dữ liệu đi vào collection typed phải là nơi hợp đồng kiểu được kiểm tra. Khi bạn khôi phục đúng ranh giới đó, compiler có thể giúp ngăn lỗi ngay từ lúc viết code, thay vì để một consumer bất kỳ chịu hậu quả ở runtime.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-integrated-practice-007">Quan sát type erasure và bridge bằng javap</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành tổng hợp: quan sát type erasure và bridge method bằng `javap`

## Mục tiêu của phòng thực hành

Trong Java generics, mã nguồn nhìn thấy các kiểu như `Store<String>` và `NumberWorker<Integer>`, nhưng phần lớn thông tin đối số kiểu được **xóa kiểu** (*type erasure*) khi biên dịch. Khi lớp con ghi đè một phương thức generic, descriptor JVM của phương thức chuyên biệt có thể khác descriptor phương thức cha. Trình biên dịch Java có thể sinh một **bridge method** để lời gọi đa hình vẫn hoạt động.

Bài thực hành này đưa ra một chương trình Java 17 chạy được, sau đó kiểm chứng **từng nhận định bằng tệp `.class`**, không chỉ bằng suy đoán từ mã nguồn. Sau khi hoàn thành, bạn có thể:

- Dự đoán erasure của `T` không giới hạn và `T extends Number`.
- Dùng `javap -p -s` để đối chiếu JVM descriptor của field/phương thức với chữ ký ở Java source.
- Dùng `javap -p -c` để lần theo bytecode: `checkcast`, `invokevirtual`, `invokespecial` và `areturn`.
- Dùng `javap -p -v` xác nhận cờ `ACC_BRIDGE` và `ACC_SYNTHETIC` của phương thức do compiler tạo.
- Đối chiếu hai kiểu bridge: `set(Object)` có cast đầu vào, `get():Object` chuyển tiếp kiểu trả về.
- Phân biệt cầu nối do `Object` erasure với cầu nối dùng `Number` từ upper bound.
- Sử dụng reflection `Method.isBridge()` để xác minh ở thời gian chạy và giải thích tình huống raw type gây `ClassCastException`.

**Yêu cầu:** Java 17 trở lên, có `javac`, `java` và `javap` cùng một bộ JDK. Không cần Gradle, Maven hoặc thư viện ngoài.

## 1. Dự đoán trước khi mở bytecode

Hãy xem ba mối quan hệ kiểu trong một bài toán:

```java
class Store<T> {
    public void set(T item) { /* ... */ }
    public T get() { /* ... */ }
}

class StringStore extends Store<String> {
    @Override public void set(String item) { /* ... */ }
    @Override public String get() { /* ... */ }
}

class NumberWorker<T extends Number> {
    public T next(T value) { return value; }
}

class IntegerWorker extends NumberWorker<Integer> {
    @Override public Integer next(Integer value) { return value + 1; }
}
```

Đây là **trích yếu dùng để phân tích chữ ký**; chương trình đầy đủ có thể biên dịch nằm ở phần tiếp theo.

Trước khi chạy lệnh, hãy tự dự đoán:

1. Sau type erasure, `Store<T>.set(T)` nhận `T`, `Object` hay `String`?
2. `Store<T>.get()` trả về kiểu gì trong JVM descriptor?
3. Trong `StringStore`, compiler cần thêm phương thức nào để kết nối lời gọi qua `Store<String>` với phương thức ghi đè nhận `String`?
4. Nếu cầu nối `set(Object)` nhận thực tế một `Integer`, điều gì xảy ra?
5. Với `NumberWorker<T extends Number>`, upper bound có khiến erasure của `T` khác `Object` không?

Giữ các dự đoán này để đối chiếu với `javap` và kết quả chạy. Đó là bước quan trọng nhất của một bài lab: **mỗi kết luận đều phải có bằng chứng quan sát**.

## 2. Tạo một chương trình có đầy đủ trường hợp kiểm chứng

Tạo tệp đúng tên **`ErasureBridgeLab.java`**:

```java
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class ErasureBridgeLab {
    static class Store<T> {
        private T item;

        public void set(T item) {
            this.item = item;
        }

        public T get() {
            return item;
        }
    }

    static final class StringStore extends Store<String> {
        @Override
        public void set(String item) {
            super.set(item.trim());
        }

        @Override
        public String get() {
            return super.get();
        }
    }

    static class NumberWorker<T extends Number> {
        public T next(T value) {
            return value;
        }
    }

    static final class IntegerWorker extends NumberWorker<Integer> {
        @Override
        public Integer next(Integer value) {
            return value + 1;
        }
    }

    static <T> T echo(T item) {
        return item;
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Store<String> store = new StringStore();
        store.set("  java  ");
        System.out.println("stored: " + store.get());
        System.out.println("runtime: " + store.getClass().getSimpleName());

        Method concrete = StringStore.class
                .getDeclaredMethod("set", String.class);
        Method bridge = StringStore.class
                .getDeclaredMethod("set", Object.class);
        System.out.println("set(String) bridge: " + concrete.isBridge());
        System.out.println("set(Object) bridge: " + bridge.isBridge());
        System.out.println("set(Object) synthetic: " + bridge.isSynthetic());

        List<String> bridges = Arrays
                .stream(StringStore.class.getDeclaredMethods())
                .filter(Method::isBridge)
                .map(Method::getName)
                .sorted()
                .toList();
        System.out.println("StringStore bridges: " + bridges);

        NumberWorker<Integer> worker = new IntegerWorker();
        System.out.println("bounded: " + worker.next(7));
        System.out.println("echo: " + echo("ok"));

        Store raw = new StringStore();
        try {
            raw.set(123);
        } catch (ClassCastException ex) {
            System.out.println("raw failure: "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Các lớp `Store`, `StringStore`, `NumberWorker` và `IntegerWorker` ở đây là **static nested classes**. `javac` sẽ tạo các class file cho từng lớp lồng; tên nhị phân có `$` giữa tên lớp ngoài và lớp trong. Việc dùng cùng một tệp giúp tái tạo tất cả thí nghiệm với một lệnh biên dịch.

**Chi tiết có chủ đích:**

- `Store<T>` chứa field `T item`, phương thức nhận `T` và phương thức trả `T`: quan sát được erasure ở ba vị trí.
- `StringStore` ghi đè cả `set(String)` và `get():String`: ta sẽ thấy **hai bridge** với hành vi khác nhau.
- `NumberWorker<T extends Number>` có upper bound: bài test xác nhận bridge dùng `Number` chứ không mặc định dùng `Object`.
- `echo(T)` là generic method độc lập với các lớp generic: descriptor chứng minh type erasure cũng áp dụng cho tham số kiểu khai báo ở cấp method.
- Đoạn raw `Store` cố tình tạo cảnh báo/lỗi runtime để phơi bày nơi compiler mất khả năng bảo vệ kiểu.
- Reflection dùng `getDeclaredMethod` với tham số cụ thể, còn danh sách bridge được **sort theo tên** để stdout không phụ thuộc thứ tự `getDeclaredMethods()`.

## 3. Biên dịch và chạy để kiểm tra hành vi

Trong thư mục có `ErasureBridgeLab.java`, chạy:

```bash
javac -Xlint:rawtypes -Xlint:unchecked ErasureBridgeLab.java
java ErasureBridgeLab
```

**Stdout đã kiểm chứng trên Java 17+**:

```text
stored: java
runtime: StringStore
set(String) bridge: false
set(Object) bridge: true
set(Object) synthetic: true
StringStore bridges: [get, set]
bounded: 8
echo: ok
raw failure: ClassCastException
```

`javac` còn phát **hai cảnh báo** trong ví dụ: một `[rawtypes]` ở `Store raw` và một `[unchecked]` ở `raw.set(123)`. Đó là thông báo từ compiler, không phải stdout của `java ErasureBridgeLab`. Dòng `raw failure` do khối `catch` trong chương trình in ra.

Giải thích bước đầu:

- `store` được khai báo `Store<String>` nên compiler chỉ cho phép gọi `set` với `String` trên tham chiếu ấy.
- Instance thật được tạo bởi `new StringStore()`, vì vậy lời gọi đa hình đi tới logic của lớp con và xóa khoảng trắng bằng `trim()`.
- `set(String)` là phương thức do người viết khai báo; `set(Object)` mang tính **bridge** do compiler tạo.
- Cả `get` và `set` đều có bridge trong `StringStore`.
- `IntegerWorker.next(7)` tăng số từ `7` lên `8`.
- Raw type làm mất thông tin `String` ở nguồn; bridge cố ép `Integer` sang `String` và ném `ClassCastException`.

**Điều cần quan sát riêng:** Kết quả `runtime: StringStore` phản ánh **lớp thật của instance**, không phải chuỗi `Store<String>` tại compile time. Cùng một đối tượng có thể được giữ qua biến kiểu cha.

## 4. Tìm đúng class file và dùng `javap`

Sau khi biên dịch, các class file liên quan có tên nhị phân như:

```text
ErasureBridgeLab.class
ErasureBridgeLab$Store.class
ErasureBridgeLab$StringStore.class
ErasureBridgeLab$NumberWorker.class
ErasureBridgeLab$IntegerWorker.class
```

Đừng chỉ chạy `javap ErasureBridgeLab` rồi kết luận không có bridge: method được tạo nằm trong **class con** tương ứng.

Các lệnh quan sát:

```bash
javap -p -s 'ErasureBridgeLab$Store'
javap -p -s 'ErasureBridgeLab$StringStore'
javap -p -s 'ErasureBridgeLab$NumberWorker'
javap -p -s 'ErasureBridgeLab$IntegerWorker'
javap -p -s ErasureBridgeLab
```

Dấu nháy đơn quanh tên lớp có `$` hoạt động trong Bash và PowerShell, giúp shell không hiểu `$Store`, `$StringStore` là tên biến. Nếu chạy `javap` từ thư mục khác, thêm `-classpath <thư_mục_chứa_class>` và giữ đúng tên nhị phân.

Ba chế độ quan trọng:

| Lệnh | Mục đích |
| --- | --- |
| `javap -p -s` | Hiển thị mọi method/field và JVM **descriptor** |
| `javap -p -c` | Hiển thị instruction trong thân phương thức |
| `javap -p -v` | Hiển thị access flags, `Signature`, constant pool và metadata |

`javap -p` hiển thị cả thành viên private, giúp quan sát field `item`. `javap -s` cho biết descriptor chính xác; `javap -v` mới là cách rõ ràng để xem cờ cầu nối.

## 5. Thí nghiệm A — Type erasure của `Store<T>` không giới hạn

Source của lớp cha:

```java
static class Store<T> {
    private T item;
    public void set(T item) { this.item = item; }
    public T get() { return item; }
}
```

Đoạn này đã nằm trong chương trình đầy đủ ở phần 2. `T` không có `extends` nên upper bound hiệu dụng là `Object`.

Chạy:

```bash
javap -p -s 'ErasureBridgeLab$Store'
```

Trích kết quả **đã kiểm chứng**:

```text
class ErasureBridgeLab$Store<T> {
  private T item;
    descriptor: Ljava/lang/Object;

  public void set(T);
    descriptor: (Ljava/lang/Object;)V

  public T get();
    descriptor: ()Ljava/lang/Object;
}
```

Trong descriptor JVM:

- `Ljava/lang/Object;` là tham chiếu tới `java.lang.Object`.
- Phần trong dấu ngoặc `(...)` là kiểu tham số của phương thức.
- `V` là kiểu trả về `void`.
- `()Ljava/lang/Object;` nghĩa là method **không nhận tham số** và trả `Object`.

`javap` vẫn có thể in `T` trên dòng khai báo của lớp/phương thức vì class file giữ metadata **generic Signature** để công cụ và reflection đọc lại thông tin generic được khai báo. Nhưng descriptor chứa kiểu xóa đã cho thấy JVM biểu diễn phương thức ra sao.

Như vậy, `Store<String>` và `Store<Integer>` không cần các phiên bản runtime `Store` riêng cho `String` hay `Integer`. Runtime nhận những instance của lớp thật, còn trình biên dịch Java kiểm tra kiểu tham số generic và có thể chèn cast tại điểm dùng giá trị.

**Câu hỏi kiểm tra:** `Store<T>.get()` ở source trả `T`. Nếu một biến `Store<String>` gọi `get()`, biểu thức ở source được hiểu là `String`, nhưng descriptor `get` trong class cha là `()Ljava/lang/Object;`. Compiler có thể chèn `checkcast java/lang/String` tại vị trí cần dùng kết quả.

## 6. Thí nghiệm B — Quan sát hai bridge trong `StringStore`

`StringStore` khai báo:

```java
static final class StringStore extends Store<String> {
    @Override
    public void set(String item) {
        super.set(item.trim());
    }

    @Override
    public String get() {
        return super.get();
    }
}
```

Dòng `extends Store<String>` khiến compiler xem phương thức chuyên biệt của lớp con là override hợp lệ. Tuy nhiên, ở bytecode lớp cha có `set(Object):void` và `get():Object`, còn phương thức con do người dùng viết có `set(String):void` và `get():String`.

Dùng:

```bash
javap -p -s 'ErasureBridgeLab$StringStore'
```

Các descriptor chính được xác nhận:

```text
public void set(java.lang.String);
  descriptor: (Ljava/lang/String;)V

public java.lang.String get();
  descriptor: ()Ljava/lang/String;

public java.lang.Object get();
  descriptor: ()Ljava/lang/Object;

public void set(java.lang.Object);
  descriptor: (Ljava/lang/Object;)V
```

Các method `set(String)`, `get():String` là **override cụ thể** đã viết trong source. Hai method `set(Object)`, `get():Object` được `javac` sinh làm bridge để các lời gọi dùng descriptor erased của lớp cha vẫn được chuyển tới override cụ thể.

Bảng đối chiếu:

| Vai trò | Method ở lớp con | JVM descriptor |
| --- | --- | --- |
| Override được viết trong source | `set(String)` | `(Ljava/lang/String;)V` |
| **Bridge** | `set(Object)` | `(Ljava/lang/Object;)V` |
| Override được viết trong source | `get():String` | `()Ljava/lang/String;` |
| **Bridge** | `get():Object` | `()Ljava/lang/Object;` |

Từ góc nhìn source, Java không cho phép bạn tự khai báo hai method chỉ khác kiểu trả về. Dù vậy, ở **class file**, JVM descriptor có chứa kiểu trả về và compiler có thể tạo bridge `get():Object` bên cạnh method `get():String`. Đừng tự viết bridge bằng tay; nó do compiler tạo, và việc viết một phương thức `set(Object)` bên cạnh `set(String)` trong quan hệ generic này thường dẫn đến **name clash**.

## 7. Thí nghiệm C — Xem thân bridge bằng `javap -c`

Chạy:

```bash
javap -p -c 'ErasureBridgeLab$StringStore'
```

Nhìn phần thân cầu nối `set(Object)`. Đoạn opcode đã kiểm chứng:

```text
public void set(java.lang.Object);
  Code:
     0: aload_0
     1: aload_1
     2: checkcast     #8  // class java/lang/String
     5: invokevirtual #25 // Method set:(Ljava/lang/String;)V
     8: return
```

Đọc theo thứ tự:

1. `aload_0` đẩy tham chiếu `this` lên operand stack.
2. `aload_1` đẩy đối số được truyền vào dưới dạng `Object`.
3. `checkcast String` xác nhận đối số là `String` hoặc `null` (về mặt cast); nếu không tương thích, JVM ném `ClassCastException`.
4. `invokevirtual ... set(String):void` gọi method do lớp con viết.
5. `return` kết thúc bridge.

Mã giả biểu diễn ý tưởng, **không phải đoạn mã nguồn cần thêm vào class**:

```java
// Minh họa compiler-generated bridge, không viết thủ công:
public void set(Object value) {
    set((String) value);
}
```

Chính `checkcast` này giải thích stdout:

```text
raw failure: ClassCastException
```

Raw `Store` gọi `set(123)` với `Integer`; phép cast sang `String` thất bại **trước khi vào phần thân** `set(String)` để trim chuỗi. Với một tham chiếu typed `Store<String>`, compiler từ chối trực tiếp lời gọi `set(123)`, nên sai lầm bị phát hiện sớm hơn.

### Bridge `get():Object` khác gì?

Phần thân từ `javap -c`:

```text
public java.lang.Object get();
  Code:
     0: aload_0
     1: invokevirtual #21 // Method get:()Ljava/lang/String;
     4: areturn
```

Không có đối số cần cast, vì thế cầu nối này chỉ gọi `get():String` rồi trả tham chiếu kết quả dưới dạng `Object`. Một `String` vốn là một `Object`, không cần tạo đối tượng mới.

Chú ý: bản `get():String` mà người dùng viết **gọi** `super.get():Object`; trong bytecode của method thật, compiler chèn **`checkcast String`** khi chuyển kết quả từ lớp cha về `String`. Đừng nhầm `checkcast` nằm trong method thật ấy với phần thân của bridge `get():Object`.

Trong cùng kết quả `javap -c`, phương thức thật có các lệnh điển hình:

```text
public java.lang.String get();
  Code:
     0: aload_0
     1: invokespecial #17 // Method ErasureBridgeLab$Store.get:()Ljava/lang/Object;
     4: checkcast     #8  // class java/lang/String
     7: areturn
```

Sự phân biệt này rất hữu ích khi đọc stack trace hoặc tìm điểm cast sai.

## 8. Thí nghiệm D — Xác nhận bridge qua flags và reflection

Dùng chế độ verbose:

```bash
javap -p -v 'ErasureBridgeLab$StringStore'
```

Ở các method bridge, bạn sẽ thấy một dòng tương tự trích đoạn đã kiểm chứng:

```text
public java.lang.Object get();
  descriptor: ()Ljava/lang/Object;
  flags: (0x1041) ACC_PUBLIC, ACC_BRIDGE, ACC_SYNTHETIC

public void set(java.lang.Object);
  descriptor: (Ljava/lang/Object;)V
  flags: (0x1041) ACC_PUBLIC, ACC_BRIDGE, ACC_SYNTHETIC
```

Các cờ:

- **`ACC_BRIDGE`**: method là cầu nối do compiler sinh phục vụ ngữ nghĩa ghi đè/kiểu.
- **`ACC_SYNTHETIC`**: method do compiler sinh, không phải khai báo thông thường trong mã nguồn của người viết.
- **`ACC_PUBLIC`**: method trong ví dụ có phạm vi public phù hợp với phương thức được ghi đè.

Không phải mọi method synthetic đều là bridge. Muốn khẳng định một method là bridge, cần kiểm tra `ACC_BRIDGE` hoặc API reflection tương ứng.

Phần Java trong `main` xác nhận tương tự:

```java
Method concrete = StringStore.class
        .getDeclaredMethod("set", String.class);
Method bridge = StringStore.class
        .getDeclaredMethod("set", Object.class);
System.out.println(concrete.isBridge()); // false
System.out.println(bridge.isBridge());   // true
System.out.println(bridge.isSynthetic()); // true
```

Đây là trích đoạn từ tệp `ErasureBridgeLab.java`, không phải một chương trình đứng độc lập. `Class.getDeclaredMethod` dùng **kiểu tham số**, nên hai bản `set` có thể được nhận diện riêng. Với bridge `get()` và method `get()` thực cùng danh sách tham số rỗng nhưng khác kiểu trả về trong class file, duyệt `getDeclaredMethods()` rồi lọc `Method.isBridge()` là cách quan sát chắc chắn hơn việc giả định `getDeclaredMethod("get")` chọn một bản nhất định.

Đầu ra đã kiểm chứng của lab:

```text
set(String) bridge: false
set(Object) bridge: true
set(Object) synthetic: true
StringStore bridges: [get, set]
```

Lệnh `sorted()` khiến thứ tự `[get, set]` ổn định, vì Reflection không cam kết thứ tự các method mà `getDeclaredMethods()` trả về.

## 9. Thí nghiệm E — Bound `Number` thay đổi descriptor cầu nối

Bây giờ xem cặp lớp thứ hai:

```java
static class NumberWorker<T extends Number> {
    public T next(T value) {
        return value;
    }
}

static final class IntegerWorker extends NumberWorker<Integer> {
    @Override
    public Integer next(Integer value) {
        return value + 1;
    }
}
```

Ở đây `T` có giới hạn đầu tiên là `Number`, nên type erasure của `T` là **`Number`**, không phải `Object`.

Chạy:

```bash
javap -p -s 'ErasureBridgeLab$NumberWorker'
javap -p -s 'ErasureBridgeLab$IntegerWorker'
```

Kết quả chính từ `NumberWorker`:

```text
public T next(T);
  descriptor: (Ljava/lang/Number;)Ljava/lang/Number;
```

Từ `IntegerWorker`:

```text
public java.lang.Integer next(java.lang.Integer);
  descriptor: (Ljava/lang/Integer;)Ljava/lang/Integer;

public java.lang.Number next(java.lang.Number);
  descriptor: (Ljava/lang/Number;)Ljava/lang/Number;
```

Method `next(Number):Number` là bridge. Chạy:

```bash
javap -p -c 'ErasureBridgeLab$IntegerWorker'
```

Đoạn thân cầu nối đã kiểm chứng:

```text
public java.lang.Number next(java.lang.Number);
  Code:
     0: aload_0
     1: aload_1
     2: checkcast     #8  // class java/lang/Integer
     5: invokevirtual #17 // Method next:(Ljava/lang/Integer;)Ljava/lang/Integer;
     8: areturn
```

Như vậy, lời gọi qua `NumberWorker<Integer>` vẫn liên kết với chữ ký đã xóa của lớp cha `next(Number):Number`, rồi bridge chuyển tới `next(Integer):Integer` của lớp con. Giá trị đầu vào `7` là `Integer` nên phép cast thành công và method trả `8`.

Quy tắc mở rộng: với một type variable như `<T extends Number & Comparable<T>>`, erasure của `T` dựa vào **bound đầu tiên** là `Number`. Hãy kiểm tra trường hợp cụ thể thay vì mặc định mọi method generic bị xóa về `Object`.

## 10. Thí nghiệm F — Generic method cũng bị erasure

Tệp hoàn chỉnh còn có:

```java
static <T> T echo(T item) {
    return item;
}
```

Đây là một **generic method**, không phải phương thức phụ thuộc tham số kiểu của lớp. Chạy:

```bash
javap -p -s ErasureBridgeLab
```

Dòng liên quan trong kết quả đã kiểm tra:

```text
static <T> T echo(T);
  descriptor: (Ljava/lang/Object;)Ljava/lang/Object;
```

Vì `T` không có upper bound tường minh, JVM descriptor dùng `Object` ở cả đầu vào và đầu ra. Lời gọi `echo("ok")` vẫn được Java compiler kiểm tra và suy luận kiểu phù hợp, nên stdout:

```text
echo: ok
```

Bằng chứng này tránh một nhầm lẫn phổ biến: type erasure không chỉ xảy ra trên generic class như `Store<T>`; nó còn áp dụng cho type parameter khai báo ở method `<T> T echo(T)`.

**Phân biệt:** `echo` không tự sinh bridge chỉ vì nó là generic. Bridge trong lab xuất hiện do quan hệ **ghi đè** có descriptor đặc biệt cần duy trì sau erasure.

## 11. Sơ đồ đường đi của lời gọi `set`

Khi viết:

```java
Store<String> store = new StringStore();
store.set("  java  ");
```

Mô hình luồng thực thi phù hợp với bytecode đã quan sát:

```text
Java source: Store<String>.set("  java  ")
                |
                v
Compiler kiểm tra argument là String
                |
                v
JVM call theo descriptor Store.set(Object):void
                |
                v
Đối tượng thực: StringStore
                |
                v
StringStore.set(Object):void     [BRIDGE]
                |
       checkcast String
                |
                v
StringStore.set(String):void     [SOURCE OVERRIDE]
                |
       String.trim()
                |
                v
Store.set(Object):void          [SUPER IMPLEMENTATION]
                |
                v
Field item lưu đối tượng String "java"
```

Đây là sơ đồ hướng dẫn đọc bytecode, không phải cú pháp Java để chạy. Với compiler và kiểu dispatch khác nhau, opcode có thể thay đổi, nhưng bằng chứng của lab gồm hai đường chính: method bridge nhận descriptor erased và method override chuyên biệt xử lý giá trị.

Khi gọi `store.get()`, JVM tương tự dùng descriptor `Store.get():Object`, chọn bridge `StringStore.get():Object`, bridge gọi `StringStore.get():String`, phương thức thật gọi `super.get():Object` và cast kết quả về `String`. Nhờ bridge, tính đa hình ở source không bị phá hỏng bởi erasure.

## 12. Giải thích hiện tượng raw type trong cùng lab

Chủ ý cuối `main`:

```java
Store raw = new StringStore();
try {
    raw.set(123);
} catch (ClassCastException ex) {
    System.out.println("raw failure: "
            + ex.getClass().getSimpleName());
}
```

`Store` ở đây là **raw type** vì bỏ đối số kiểu `<T>`. Trong Java source, lời gọi `raw.set(123)` được cho phép với cảnh báo unchecked vì chữ ký erased của `set` nhận `Object`. Tuy nhiên đối tượng thật vẫn là `StringStore`; cầu nối `set(Object)` của lớp này yêu cầu cast sang `String`. `Integer` không phù hợp, nên runtime ném `ClassCastException`.

Hai điểm cần rút ra:

- **Generic type safety** được compiler áp dụng trong lời gọi typed thông thường. Raw type cố tình đi qua một ranh giới ít kiểm tra hơn và làm lộ nguy cơ.
- **Bridge không phải công cụ chuyển kiểu tùy ý.** Nó chỉ bảo tồn hợp đồng ghi đè; phép `checkcast` vẫn có thể thất bại với dữ liệu vi phạm giả định.

Không kết luận rằng “mọi raw method đều ném exception”: nếu đối số thật là `String`, bridge có thể cast thành công. Lỗi phụ thuộc kiểu dữ liệu thực truyền vào và bytecode của phương thức đích.

## 13. Bộ lệnh kiểm tra nhanh và cách tự đối chiếu

Một trình tự tái lập từ đầu đến cuối:

```bash
# Bước 1: Biên dịch cùng cảnh báo cần quan sát.
javac -Xlint:rawtypes -Xlint:unchecked ErasureBridgeLab.java

# Bước 2: Quan sát output.
java ErasureBridgeLab

# Bước 3: Quan sát erasure ở lớp generic.
javap -p -s 'ErasureBridgeLab$Store'

# Bước 4: Đếm method thật và bridge ở lớp con.
javap -p -s 'ErasureBridgeLab$StringStore'

# Bước 5: Quan sát cast và lệnh gọi trong bridge.
javap -p -c 'ErasureBridgeLab$StringStore'

# Bước 6: Xác minh flags bridge/synthetic.
javap -p -v 'ErasureBridgeLab$StringStore'

# Bước 7: Đối chiếu upper bound Number.
javap -p -s 'ErasureBridgeLab$NumberWorker'
javap -p -s 'ErasureBridgeLab$IntegerWorker'

# Bước 8: Đối chiếu generic method.
javap -p -s ErasureBridgeLab
```

Các dòng bắt đầu bằng `#` là chú thích shell trong Bash; trong PowerShell cũng có thể sử dụng `#` cho chú thích. Nếu Windows `cmd.exe` được sử dụng, hãy chạy các lệnh `javac`/`java`/`javap` riêng, không sao chép các dòng chú thích vào `cmd`.

Các mệnh đề để kiểm tra từ output:

| Mệnh đề | Bằng chứng cần thấy | Kỳ vọng |
| --- | --- | --- |
| Erasure của `Store<T>` là `Object` | Descriptor của field/set/get | `Object` |
| `StringStore` có bridge nhận `Object` | `javap -s` | `set(Object):void` |
| `StringStore` có bridge trả `Object` | `javap -s` | `get():Object` |
| Bridge thực hiện cast khi nhận tham số | `javap -c` | `checkcast String` |
| Bridge là compiler-generated | `javap -v` | `ACC_BRIDGE, ACC_SYNTHETIC` |
| Reflection nhận diện bridge | `Method.isBridge()` | `true` với `set(Object)` |
| Erasure của `T extends Number` | `NumberWorker.next` | `Number` |
| Bridge lớp con giới hạn Number | `IntegerWorker.next` | `next(Number):Number` |
| Generic method `echo(T)` bị erasure | Descriptor của `echo` | `(Object)Object` |
| Raw type phá an toàn kiểu | `raw.set(123)` | `ClassCastException` |

Để kết luận một mục **PASS**, hãy chỉ ra dòng descriptor/flag/bytecode tương ứng, thay vì chỉ dựa vào chữ ký generic mà `javap` in ở dòng trên.

## 14. Những lỗi thường gặp khi thực hành

### Không tìm thấy `ErasureBridgeLab$StringStore`

Nguyên nhân thường gặp là chạy `javap` ở thư mục sai, chưa biên dịch, hoặc shell xử lý ký tự `$`. Kiểm tra class file đã sinh, đặt tên lớp lồng trong dấu nháy đơn, hoặc truyền `-classpath` trỏ đến thư mục output của `javac`.

### Chỉ dùng `javap ErasureBridgeLab` và không thấy method bridge

Bridge được sinh trong **lớp con** `StringStore` và `IntegerWorker`, không nằm ở class `ErasureBridgeLab` bao quanh. Cần hướng `javap` vào chính class con.

### Tưởng `T` hiển thị trong output `javap` nghĩa là chưa bị xóa

`javap` có thể đọc generic `Signature`. Hãy kiểm tra **`descriptor:`** để xác định kiểu JVM dùng. Ví dụ `T get()` có descriptor `()Ljava/lang/Object;`.

### Kết luận `get():Object` là một overload thông thường do tác giả viết

Bridge được đánh dấu `ACC_BRIDGE` và `ACC_SYNTHETIC`, không xuất hiện trong source. JVM descriptor chứa cả kiểu trả về nên compiler có thể đặt bridge `get():Object` bên cạnh `get():String`.

### Cho rằng mọi bridge phải có `checkcast`

Bridge `set(Object)` có cast đầu vào; bridge `get():Object` trong ví dụ chỉ ủy quyền tới `get():String`. Có thể có cast ở **phương thức thật** hoặc **điểm gọi** chứ không nhất thiết ở mọi cầu nối.

### Nhầm `StringStore.get():String` với `StringStore.get():Object`

Cả hai hiện diện trong class file và khác **return descriptor**. Hãy đọc phương thức đang xem ở đầu khối `javap -c` trước khi diễn giải opcode.

### Nghĩ raw type tự động chuyển `Integer` thành `String`

Raw type bỏ bớt kiểm tra lúc biên dịch; bridge kiểm tra cast tại runtime. Khi kiểu không tương thích, chương trình ném `ClassCastException` chứ không tự chuyển giá trị.

### Dùng kết quả `getDeclaredMethods()` theo thứ tự cố định

Reflection không bảo đảm thứ tự danh sách method. Khi cần so khớp output, hãy lọc `isBridge`, chuẩn hóa tên/chữ ký và sắp xếp có chủ đích như chương trình mẫu.

### Thấy `Number` rồi suy luận là sai vì tài liệu nói “generic về Object”

Erasure thay type variable bằng **upper bound hiệu dụng**. Với `T extends Number`, descriptor dùng `Number`; `Object` là trường hợp không có bound (hoặc upper bound hiệu dụng là `Object`).

### Sao chép đoạn mã giả bridge vào source

Compiler tự tạo cầu nối. Nếu tự viết method có cùng erasure với phương thức inherited, bạn có thể tạo name clash. Chỉ triển khai override typed và để compiler xử lý bridge.

## 15. Bài tập biến đổi chương trình và dự đoán kết quả

**Bài 1 — Bỏ `@Override` ở `StringStore.set(String)`.** Dự đoán bridge có biến mất không nếu chữ ký và quan hệ kế thừa vẫn giống cũ. Sau đó biên dịch và quan sát lại `javap`.

*Đáp án định hướng:* `@Override` là bước kiểm tra của compiler đối với ý định ghi đè. Việc bỏ annotation không làm đổi quan hệ override hợp lệ; các cầu nối cần thiết thường vẫn được sinh. Tuy nhiên, giữ annotation để phát hiện sớm sai sót về chữ ký.

**Bài 2 — Đổi giới hạn `NumberWorker<T extends Number>` thành `NumberWorker<T>`.** Cần sửa điều gì để `IntegerWorker` vẫn hợp lệ? Dự đoán descriptor của `next(T)` ở lớp cha và bridge trong lớp con.

*Đáp án định hướng:* `T` mới xóa về `Object`; nếu các phần phương thức vẫn hợp lệ, descriptor cha là `(Object)Object`, và cầu nối lớp con cần `next(Object):Object`. Chạy `javap -s` sau khi sửa để xác minh.

**Bài 3 — Đổi `StringStore.get()` thành trả chuỗi cố định.** Hỏi bridge `get():Object` có còn cần không?

*Đáp án định hướng:* nếu vẫn ghi đè `Store<String>.get()` với kiểu trả `String`, quan hệ bytecode với phương thức cha trả `Object` vẫn tồn tại. Việc thay thân phương thức không tự xóa nhu cầu bridge.

**Bài 4 — Lấy danh sách mọi bridge của `IntegerWorker` bằng reflection.** Viết một đoạn tương tự `Arrays.stream(...).filter(Method::isBridge)` và so sánh với `StringStore`.

*Đáp án định hướng:* `IntegerWorker` có một bridge `next(Number):Number` trong ví dụ hiện tại, trong khi `StringStore` có bridge `get` và `set`. Đếm method trên đúng class, không gộp cả method thừa kế vào kết quả `getDeclaredMethods()`.

**Bài 5 — Phân loại cast.** Trong `javap -c`, tìm `checkcast` trong `StringStore.get():String` và trong `StringStore.set(Object):void`. Cái nào nằm trong bridge?

*Đáp án định hướng:* `set(Object)` là bridge, cast đầu vào từ `Object` thành `String`. `get():String` là method thật, cast kết quả nhận từ `Store.get():Object` để trả `String`.

**Bài 6 — Kiểm tra sai kiểu qua raw.** Thay `raw.set(123)` bằng `raw.set("correct")`, biên dịch và chạy. Cảnh báo và exception thay đổi thế nào?

*Đáp án định hướng:* cảnh báo raw/unchecked vẫn còn vì kiểu tham chiếu raw; nhưng giá trị thực là `String` nên cast của bridge thành công, không còn `ClassCastException` tại lời gọi ấy.

**Bài 7 — Nhìn method generic `echo`.** Vì sao `echo("ok")` không tạo bridge dù descriptor là `(Object)Object`?

*Đáp án định hướng:* generic erasure tự nó không buộc compiler tạo bridge. Bridge phục vụ những quan hệ override/descriptor cần kết nối; `echo(T)` ở đây là phương thức static generic độc lập, không ghi đè method generic ở lớp cha.

## 16. Tự đánh giá bài thực hành

Bạn đã hoàn thành bài khi có thể:

- Biên dịch `ErasureBridgeLab.java` bằng `javac`, giải thích cảnh báo raw/unchecked và đối chiếu stdout chính xác.
- Chỉ ra `Object` trong descriptor `Store<T>`, trong khi `javap` vẫn hiển thị ký hiệu generic `T`.
- Chứng minh `StringStore` chứa hai bridge bằng **descriptor, bytecode và flags**, không chỉ dựa vào `@Override`.
- Phân biệt `checkcast` ở bridge `set(Object)` với cast trong method thật `get():String`.
- Giải thích vì sao `IntegerWorker` có bridge với `Number` thay vì `Object`.
- Đọc được `ACC_BRIDGE`, `ACC_SYNTHETIC`, `Signature` và giải thích vai trò khác nhau của chúng.
- Kết nối bằng chứng runtime `isBridge()`, lỗi raw type và cơ chế dispatch qua lớp cha.

**Kết quả cốt lõi:** type erasure biến những vị trí dùng `T` thành kiểu upper bound phù hợp trong JVM descriptor; bridge method được `javac` sinh để giữ đúng quan hệ ghi đè và đa hình khi descriptor của override chuyên biệt khác dạng erased. `javap -s`, `-c`, `-v` cùng reflection cho phép tự chứng minh cả **hình dạng bytecode** lẫn **hành vi chạy** của cơ chế đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-integrated-practice-008">Xây dựng kho nhiều kiểu và type token cho cấu trúc lồng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng kho nhiều kiểu và type token cho cấu trúc lồng nhau trong Java Generics

## 1. Bài toán và kết quả mong đợi

Thông thường, một collection generic lưu nhiều giá trị **cùng một kiểu phần tử**:

~~~java
List<String> names = List.of("Java", "Kotlin");
Map<String, Integer> points = Map.of("A", 10, "B", 20);
~~~

Nhưng có trường hợp ứng dụng muốn một **kho giá trị nhiều kiểu** (*heterogeneous typed store*), trong đó từng khóa cho biết kiểu của giá trị đi kèm:

- Khóa `String.class` đi kèm một `String`.
- Khóa `Integer.class` đi kèm một `Integer`.
- Khóa đại diện `List<String>` đi kèm danh sách chuỗi.
- Khóa đại diện `List<Integer>` đi kèm danh sách số nguyên.
- Khóa đại diện `Map<String, List<Integer>>` đi kèm cấu trúc lồng nhau.

Ta muốn gọi phương thức có chữ ký generic:

~~~java
<T> void put(TypeRef<T> key, T value);
<T> T get(TypeRef<T> key);
~~~

để compiler kiểm tra mối quan hệ giữa **kiểu khóa** và **kiểu dữ liệu được thêm vào**, trong khi bên trong kho vẫn phải lưu các giá trị khác kiểu cạnh nhau.

Mục tiêu của bài:

1. Xây dựng phiên bản đơn giản dùng **`Class<T>` làm type token** cho kiểu reifiable như String/Integer.
2. Nhận ra hạn chế: `List<String>.class`, `Map<String, List<Integer>>.class` không tồn tại trong Java.
3. Xây dựng **`TypeRef<T>`** lưu `java.lang.reflect.Type` để đại diện cho cấu trúc generic lồng nhau.
4. Xây dựng một kho dùng `Map<Type, Object>` làm nơi lưu nội bộ, với API generic ở bên ngoài.
5. Hiểu chính xác phần an toàn **compile time** và phần **runtime validation** chưa được bảo đảm chỉ bằng một type token.
6. Thử thực tế với Java 17+, kèm trường hợp dữ liệu sai kiểu bị phát hiện.

Không nên gọi `TypeRef<T>` là một “kiểu runtime mới” hay cho rằng nó khiến JVM giữ nguyên toàn bộ thông tin generic của mọi đối tượng. Nó giữ **mô tả kiểu bằng Reflection**, còn dữ liệu thực sự vẫn chịu type erasure.

## 2. Kho nhiều kiểu khác Map<String, Object> thông thường như thế nào?

Một cách thiết kế ban đầu:

~~~java
Map<String, Object> values = new HashMap<>();
values.put("title", "Java");
values.put("count", 3);
~~~

Cách này lưu được nhiều kiểu, nhưng kiểu của giá trị **không được nối với kiểu khóa** bằng hệ thống kiểu:

~~~java
String title = (String) values.get("title");
Integer count = (Integer) values.get("count");
~~~

Các cast do người dùng viết có thể thất bại vì gõ sai khóa hoặc dữ liệu ở khóa tương ứng đã bị thay.

Kho có type token muốn đưa quan hệ đó vào **chữ ký generic**:

~~~java
store.put(String.class, "Java");
store.put(Integer.class, 3);

String title = store.get(String.class);
Integer count = store.get(Integer.class);
~~~

Compiler có thể suy luận `T = String` hoặc `T = Integer` từ `Class<T>`, nên người gọi không phải viết cast khi đọc.

Mặc dù kho nội bộ vẫn dùng Object, một API được thiết kế đúng có thể giữ bất biến:

> Với mỗi type token dùng làm khóa, giá trị lưu dưới khóa đó được chấp nhận theo kiểu gắn với token tại thời điểm ghi.

Đây là **bất biến của API**, không phải phép màu của bộ nhớ runtime. Nếu cho phép truy cập raw type, unchecked cast, reflection, hoặc thay đổi dữ liệu lồng nhau qua alias không an toàn, bất biến vẫn có thể bị phá vỡ.

## 3. Phiên bản đầu tiên: type token Class<T>

`Class<T>` là token quen thuộc và hữu ích cho những kiểu có thể kiểm tra trực tiếp tại runtime:

~~~java
final class ClassStore {
    private final Map<Class<?>, Object> values = new HashMap<>();

    <T> void put(Class<T> type, T value) {
        values.put(type, type.cast(value));
    }

    <T> T get(Class<T> type) {
        return type.cast(values.get(type));
    }
}
~~~

Đây là **một đoạn minh họa**, giả định file có import `java.util.*`. Chương trình hoàn chỉnh được trình bày ở phần sau.

Cơ chế:

- `Map<Class<?>, Object>` lưu các đối tượng token của nhiều loại khác nhau.
- `put(Class<T>, T)` gắn kiểu của token và giá trị trong cùng một lời gọi.
- `Class.cast` kiểm tra được top-level runtime class, ví dụ không thể ép một `Integer` thành `String`.
- `get(Class<T>)` trả về T, suy luận từ token mà người gọi cung cấp.

Ví dụ sai bị compiler chặn:

~~~java
// store.put(String.class, 123); // Không biên dịch: T không phù hợp.
~~~

**Giới hạn:** `Class<T>` không trực tiếp biểu diễn được type argument lồng nhau như `List<String>`. `List.class` là class token của raw `List`; nó không chứa thông tin phần tử String.

## 4. Vì sao List<String>.class không tồn tại?

Java cho phép class literal:

~~~java
String.class
Integer.class
List.class
Map.class
~~~

Nhưng các biểu thức sau **không hợp lệ**:

~~~java
// List<String>.class
// Map<String, List<Integer>>.class
~~~

Bởi generics trong Java phần lớn dựa trên **type erasure**. Một thể hiện của `ArrayList<String>` và `ArrayList<Integer>` về cơ bản có cùng runtime class:

~~~java
var strings = new ArrayList<String>();
var integers = new ArrayList<Integer>();

System.out.println(strings.getClass() == integers.getClass()); // true
~~~

Nếu kho chỉ dùng `List.class`, các danh sách phần tử khác kiểu sẽ trùng khóa:

~~~java
// Cùng một Class object làm khóa:
values.put(List.class, List.of("A"));
values.put(List.class, List.of(1)); // Ghi đè giá trị trước.
~~~

Không thể giải quyết bằng cách thêm cast unchecked từ `Class<List>` sang `Class<List<String>>` rồi tuyên bố đã khôi phục tham số kiểu. JVM không thu thập thông tin String cho từng instance của List theo cách đó.

Ta cần **một đối tượng mô tả kiểu phức hợp**. Java Reflection có hệ thống `java.lang.reflect.Type` dành cho mục đích biểu diễn kiểu, không phải để khiến generic arguments trở nên reifiable.

## 5. Type và ParameterizedType đại diện cấu trúc lồng nhau

`java.lang.reflect.Type` là abstraction mô tả kiểu Java trong API Reflection. Một số dạng quan trọng:

| Reflection API | Đại diện điển hình | Ý nghĩa |
| --- | --- | --- |
| `Class<?>` | `String.class`, `List.class` | Lớp/interface trực tiếp, không phải danh sách type arguments. |
| `ParameterizedType` | `List<String>` | Kiểu có raw type và type arguments. |
| `TypeVariable<?>` | `T` trong `class Holder<T>` | Biến kiểu có bound, chưa chắc có giá trị cụ thể ở runtime. |
| `WildcardType` | `? extends Number` | Wildcard với giới hạn trên/dưới. |
| `GenericArrayType` | `T[]` hoặc mảng thành phần generic phù hợp | Kiểu mảng được mô tả bằng Type phức hợp. |

Với `Map<String, List<Integer>>`, đối tượng mô tả kiểu có cấu trúc **lồng nhau**:

~~~text
ParameterizedType: Map<String, List<Integer>>
├── raw type: Map
├── argument 0: Class<String>
└── argument 1: ParameterizedType List<Integer>
    ├── raw type: List
    └── argument 0: Class<Integer>
~~~

Nhờ vậy, một key cho `List<String>` có thể được phân biệt với key cho `List<Integer>` theo cấu trúc Type, dù runtime class của hai danh sách đều có thể là ArrayList.

`ParameterizedType` thường dùng structural equality: hai Type mô tả cùng khai báo generic với cùng type arguments có thể `equals` nhau. Điều đó cho phép dùng `Type` làm key trong `HashMap`, miễn cách tạo Type nhất quán.

Quan trọng: đây là **metadata kiểu**. Không có phép đảm bảo rằng chỉ cần mang một `ParameterizedType` thì mọi phần tử nằm trong object được gán với metadata đó đều có kiểu chính xác.

## 6. TypeRef<T>: giữ chữ ký generic bằng anonymous subclass

Một kỹ thuật phổ biến là **super type token**:

~~~java
abstract class TypeRef<T> {
    private final Type type;

    protected TypeRef() {
        Type parent = getClass().getGenericSuperclass();

        if (!(parent instanceof ParameterizedType parameterized)) {
            throw new IllegalStateException("Use an anonymous subclass");
        }

        this.type = parameterized.getActualTypeArguments()[0];
    }

    public final Type type() {
        return type;
    }
}
~~~

Đoạn này dùng các import của Reflection và là thành phần sẽ xuất hiện trong chương trình hoàn chỉnh ở phần 8.

Cách tạo token:

~~~java
TypeRef<List<String>> names =
        new TypeRef<List<String>>() {};

TypeRef<Map<String, List<Integer>>> nested =
        new TypeRef<Map<String, List<Integer>>>() {};
~~~

Dấu `{}` tạo **anonymous subclass** kế thừa `TypeRef<List<String>>` hoặc `TypeRef<Map<...>>`. Reflection đọc **generic superclass** của lớp ẩn danh để lấy Type mô tả chính xác type argument được viết ở source.

Tại constructor:

1. `getClass()` là class của anonymous subclass thực tế.
2. `getGenericSuperclass()` thường trả một `ParameterizedType` mô tả `TypeRef<List<String>>`.
3. `getActualTypeArguments()[0]` lấy Type của `List<String>`.
4. Trường `type` giữ Type đó, và kho có thể dùng nó làm khóa.

**Điều kiện:** kỹ thuật này yêu cầu lớp con thực sự có generic superclass đã được ghi lại và type argument phải có đủ thông tin. `new TypeRef<String>() {}` được hỗ trợ, nhưng `new TypeRef() {}` dùng raw superclass sẽ không cung cấp `ParameterizedType` như mong đợi và constructor trên chủ động từ chối.

## 7. Thiết kế kho dùng Map<Type, Object>

Một kho chung có thể lưu cả token đơn giản và token lồng nhau:

~~~java
final class TypedStore {
    private final Map<Type, Object> values = new HashMap<>();

    public <T> void put(Class<T> token, T value) {
        values.put(token, token.cast(Objects.requireNonNull(value)));
    }

    public <T> T get(Class<T> token) {
        return token.cast(values.get(token));
    }

    public <T> void put(TypeRef<T> token, T value) {
        values.put(token.type(), Objects.requireNonNull(value));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(TypeRef<T> token) {
        return (T) values.get(token.type());
    }
}
~~~

Đây là thành phần của chương trình hoàn chỉnh ở phần tiếp theo.

### Vì sao bên trong cần Object?

Không có một `T` duy nhất đại diện cho mọi value: kho vừa có String, Integer, List<String>, Map<String,List<Integer>>, ... Vì vậy `Map<Type,Object>` là tầng lưu trữ hỗn hợp; các API `put/get` dùng generic method riêng cho từng lời gọi.

### Vì sao có unchecked cast trong get(TypeRef<T>)?

Runtime không thể dùng một `ParameterizedType` để thực hiện kiểu cast đầy đủ đến `List<String>` hoặc `Map<String,List<Integer>>`. Trong chương trình, biểu thức `(T) values.get(token.type())` chỉ cung cấp kiểm tra kiểu đã bị xóa. Compiler có thể chèn thêm cast cho kiểu reifiable khi caller nhận dữ liệu.

Lời gọi được dự kiến an toàn **theo bất biến của kho**: mọi entry phải được thêm qua `put(TypeRef<T>, T)` với kiểu phù hợp, và không có raw/unchecked đường khác làm hỏng dữ liệu.

`@SuppressWarnings("unchecked")` cần được đặt **hẹp**, trên phương thức duy nhất sử dụng cast được lý giải, không rải trên toàn bộ class hoặc cả chương trình. Nó không biến một cast vốn không thể kiểm tra đầy đủ thành checked cast.

### Thiết kế giá trị null và khóa không tồn tại

`put` ở trên cố ý **từ chối null** bằng `Objects.requireNonNull` để giảm nhập nhằng khi gọi `get` trả null. Dù vậy, `get` trả null nếu chưa có entry. API sản phẩm có thể dùng `Optional<T>`, `contains`, hoặc ném ngoại lệ khi khóa vắng mặt; đây là lựa chọn hợp đồng, không ảnh hưởng nguyên lý type token.

## 8. Chương trình Java 17+ đầy đủ và chạy được

Lưu đúng tên tệp **HeterogeneousStoreDemo.java**:

~~~java
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HeterogeneousStoreDemo {
    static abstract class TypeRef<T> {
        private final Type type;

        protected TypeRef() {
            Type parent = getClass().getGenericSuperclass();
            if (!(parent instanceof ParameterizedType parameterized)) {
                throw new IllegalStateException("Use an anonymous subclass");
            }
            type = parameterized.getActualTypeArguments()[0];
        }

        public final Type type() {
            return type;
        }
    }

    static final class TypedStore {
        private final Map<Type, Object> values = new HashMap<>();

        public <T> void put(Class<T> token, T value) {
            values.put(token, token.cast(Objects.requireNonNull(value)));
        }

        public <T> T get(Class<T> token) {
            return token.cast(values.get(token));
        }

        public <T> void put(TypeRef<T> token, T value) {
            values.put(token.type(), Objects.requireNonNull(value));
        }

        @SuppressWarnings("unchecked")
        public <T> T get(TypeRef<T> token) {
            return (T) values.get(token.type());
        }
    }

    // Kiểm tra toàn bộ cấu trúc Map<String, List<Integer>> tại runtime
    // trước khi nhận dữ liệu từ một nguồn chưa đáng tin.
    static Map<String, List<Integer>> checkedNested(Object candidate) {
        if (!(candidate instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Map required");
        }

        Map<String, List<Integer>> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.class.cast(entry.getKey());

            if (!(entry.getValue() instanceof List<?> raw)) {
                throw new IllegalArgumentException("List required");
            }

            List<Integer> numbers = new ArrayList<>();
            for (Object element : raw) {
                numbers.add(Integer.class.cast(element));
            }

            result.put(key, List.copyOf(numbers));
        }

        return Map.copyOf(result);
    }

    public static void main(String[] args) {
        TypedStore store = new TypedStore();

        // Class<T> type tokens: kiểm tra được top-level runtime class.
        store.put(String.class, "Hello");
        store.put(Integer.class, 3);

        // TypeRef<T> type tokens: giữ type arguments lồng nhau.
        TypeRef<List<String>> labels =
                new TypeRef<List<String>>() {};
        TypeRef<List<Integer>> scores =
                new TypeRef<List<Integer>>() {};
        TypeRef<Map<String, List<Integer>>> nested =
                new TypeRef<Map<String, List<Integer>>>() {};

        store.put(labels, List.of("Java", "Kotlin"));
        store.put(scores, List.of(10, 20));

        store.put(
                nested,
                checkedNested(
                        Map.of(
                                "odd", List.of(1, 3),
                                "even", List.of(2, 4)
                        )
                )
        );

        System.out.println("greeting=" + store.get(String.class));
        System.out.println("count=" + store.get(Integer.class));
        System.out.println("labels=" + store.get(labels));
        System.out.println("scores=" + store.get(scores));
        System.out.println("odd=" + store.get(nested).get("odd"));

        // Type làm khóa theo cấu trúc, không theo identity của token wrapper.
        System.out.println(
                "sameType=" +
                labels.type().equals(
                        new TypeRef<List<String>>() {}.type()
                )
        );
        System.out.println(
                "differentType=" + labels.type().equals(scores.type())
        );
        System.out.println(
                "nestedCaptured=" +
                (nested.type() instanceof ParameterizedType)
        );

        // Một phần tử String sai vị trí số nguyên bị từ chối khi validate.
        try {
            checkedNested(Map.of("bad", List.of("wrong")));
        } catch (ClassCastException error) {
            System.out.println("invalid=ClassCastException");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -Xlint:unchecked HeterogeneousStoreDemo.java
java HeterogeneousStoreDemo
~~~

Stdout chính xác:

~~~text
greeting=Hello
count=3
labels=[Java, Kotlin]
scores=[10, 20]
odd=[1, 3]
sameType=true
differentType=false
nestedCaptured=true
invalid=ClassCastException
~~~

Giải thích kết quả:

- Hai giá trị đơn giản được lưu bằng `String.class` và `Integer.class`, không cần ép kiểu thủ công khi đọc.
- Hai key `List<String>` và `List<Integer>` **khác nhau** dù đều có raw type List. Vì vậy danh sách chuỗi và số không ghi đè lẫn nhau.
- Token lồng nhau ghi nhận `Map<String, List<Integer>>`. Truy xuất khóa `"odd"` trả `List<Integer>` có giá trị `[1, 3]`.
- `sameType=true` cho thấy hai anonymous TypeRef khác nhau có thể giữ Type mô tả cùng `List<String>`.
- `differentType=false` vì `List<String>` không phải `List<Integer>`.
- `nestedCaptured=true` xác nhận token của Map lồng nhau được mô tả bởi `ParameterizedType`.
- `invalid=ClassCastException` xuất hiện trong **checkedNested**, khi `Integer.class.cast` gặp một String là `"wrong"`. Đây là kiểm tra runtime chủ động, **không phải kiểm tra tự động từ TypeRef**.

Ví dụ sử dụng `List.of` và `Map.copyOf` để các collection đầu ra không hỗ trợ sửa đổi qua những tham chiếu trả về thông thường. Điều này giảm nguy cơ dữ liệu bị biến đổi về sau, nhưng nếu dùng giá trị mutable được tạo từ nguồn khác, vẫn phải chú ý alias và các thao tác unchecked.

## 9. Kiểm tra sai kiểu ở compile time

Với token:

~~~java
TypeRef<List<String>> words =
        new TypeRef<List<String>>() {};
~~~

Lời gọi hợp lệ:

~~~java
store.put(words, List.of("Java", "Go"));
~~~

Lời gọi cố tình sai:

~~~java
// store.put(words, List.of(1, 2)); // Không thỏa List<String>.
~~~

Trong một bối cảnh method invocation thông thường với token có kiểu `TypeRef<List<String>>`, compiler sẽ kiểm tra argument danh sách có thể dùng như `List<String>` hay không. Không được giả định rằng kho tự động chuyển Integer thành String hoặc lọc phần tử sai.

Với nested token:

~~~java
TypeRef<Map<String, List<Integer>>> nested =
        new TypeRef<Map<String, List<Integer>>>() {};
~~~

Cách dùng đúng:

~~~java
store.put(nested, Map.of("scores", List.of(1, 2, 3)));
~~~

Dùng sai sẽ bị kiểm tra theo ràng buộc type parameter:

~~~java
// store.put(nested, Map.of("scores", List.of("bad")));
~~~

Các dòng có comment là **đoạn minh họa lỗi biên dịch**, không nằm trong chương trình chạy độc lập.

### Lỗ hổng không thể giải quyết chỉ bằng chữ ký generic

Một caller vẫn có thể phá kiểm tra kiểu bằng raw types hoặc unchecked casts:

~~~java
@SuppressWarnings({"rawtypes", "unchecked"})
static void unsafeInsert(TypedStore store, TypeRef<List<String>> key) {
    TypeRef raw = key;
    store.put(raw, List.of(123));
}
~~~

Ví dụ trên minh họa code cố ý không an toàn và chỉ biên dịch khi các class liên quan có thể truy cập tại vị trí đặt phương thức. Khi khôi phục thành `List<String>`, việc đọc phần tử như String có thể ném `ClassCastException`. Lý do: raw token đã bỏ ràng buộc generic, còn TypeRef chỉ giữ mô tả kiểu chứ không duyệt mọi phần tử của value.

Không sử dụng cách này trong ứng dụng thật. Khi tích hợp mã cũ, giữ unchecked cast trong một adapter được kiểm soát và validate dữ liệu trước khi đưa vào kho.

## 10. Runtime type validation cho cấu trúc lồng nhau

`Class.cast` có thể kiểm tra được một đối tượng top-level có phải String hay Integer. Nhưng `Map<String, List<Integer>>` chứa nhiều lớp kiểm tra:

1. Đối tượng ngoài cùng là Map.
2. Mọi **khóa** phải là String.
3. Mọi **giá trị** phải là List.
4. Mọi phần tử của mỗi List phải là Integer.
5. Nếu hệ thống từ chối null, phải áp chính sách cho map, key, list và từng phần tử.
6. Nếu muốn tránh thay đổi sau kiểm tra, nên tạo bản sao độc lập và hạn chế mutation.

Đó là lý do chương trình có `checkedNested(Object)` với `instanceof Map<?, ?>`, `instanceof List<?>`, `String.class.cast` và `Integer.class.cast`. Sau khi từng phần tử đã được kiểm tra, nó xây một `Map<String, List<Integer>>` mới.

Phương thức:

~~~java
static Map<String, List<Integer>> checkedNested(Object candidate)
~~~

có thể xem như một **ranh giới dữ liệu đáng tin**: nhận Object, kiểm tra cấu trúc, tạo giá trị có kiểu đã được xác minh, rồi mới đưa vào TypedStore.

**Chú ý:** ví dụ dùng `List.copyOf` và `Map.copyOf` nên **không chấp nhận null**; nếu nghiệp vụ có null hợp lệ, hãy thiết kế một bản kiểm tra và sao chép khác có chính sách null rõ ràng. Ngoài ra, validator này được viết cụ thể cho một cấu trúc; TypeRef không tự cung cấp một bộ validator tổng quát.

### Tại sao không dùng instanceof List<String>?

Java không cho kiểm tra đầy đủ tham số generic bằng:

~~~java
// if (candidate instanceof List<String>) { } // Không hợp lệ.
~~~

Cách dùng đúng ở bước đầu:

~~~java
if (candidate instanceof List<?> list) {
    for (Object value : list) {
        // Kiểm tra từng phần tử phù hợp với kiểu mong muốn.
    }
}
~~~

`List<?>` có thể kiểm tra ở runtime dưới dạng raw List, trong khi wildcard vẫn thể hiện sự không biết của kiểu phần tử ở mức source. Các type arguments như String không trở thành điều kiện runtime mà JVM tự kiểm định khi dùng `instanceof`.

## 11. Type token của nested generics và so sánh khóa

Với các token:

~~~java
TypeRef<List<String>> a = new TypeRef<List<String>>() {};
TypeRef<List<String>> b = new TypeRef<List<String>>() {};
TypeRef<List<Integer>> c = new TypeRef<List<Integer>>() {};
~~~

Ta thường có:

~~~java
a.type().equals(b.type()); // true
a.type().equals(c.type()); // false
~~~

Nếu kho sử dụng chính **Type** làm key, a và b truy cập cùng mục lưu bởi chúng mô tả cùng generic type. Điều này đúng với nhu cầu “một cấu trúc kiểu xác định là một khóa”.

Nhưng lưu ý hai trường hợp:

- Nếu dùng **chính đối tượng TypeRef** làm key mà không định nghĩa `equals/hashCode` theo `type()`, hai instance TypeRef khác nhau có thể không bằng nhau.
- Nếu nhiều giá trị khác nhau cần cùng kiểu, ví dụ hai danh sách List<String> ứng với “ngôn ngữ học” và “ngôn ngữ dịch”, **chỉ Type không đủ phân biệt**. Cần key kết hợp tên/định danh và Type, ví dụ `record TypedKey<T>(String name, Type type)` cùng API có kiểm tra kiểu. Không nên giả định một kho gắn khóa theo Type đáp ứng mọi trường hợp có nhiều bản ghi cùng kiểu.

Trong chương trình chính, `Map<Type,Object>` thể hiện rõ mô hình **mỗi Type có tối đa một giá trị**; lời gọi put kế tiếp với cùng Type sẽ thay thế value trước đó.

## 12. Khi super type token không ghi được kiểu cụ thể

Kỹ thuật đọc generic superclass không tự biến mọi type variable thành type argument runtime cụ thể.

Xét mẫu:

~~~java
static <X> TypeRef<List<X>> captureInGenericMethod() {
    return new TypeRef<List<X>>() {};
}
~~~

Phần Type được giữ có thể chứa **TypeVariable X**, vì khi javac biên dịch phương thức generic, X vẫn là biến kiểu. Gọi phương thức với String ở call site không tự tái tạo một class ẩn danh mới có `List<String>` cố định ở Reflection metadata.

Vì vậy, nếu hệ thống cần key chính xác `List<String>` hay `Map<String,List<Integer>>`, hãy tạo token ở nơi các type arguments **đã được viết cụ thể** hoặc sử dụng cơ chế xây dựng `Type` từ các token được cung cấp.

Một số thư viện xây dựng Type tokens hỗ trợ giải kiểu biến dựa trên context, wildcard, arrays và generic supertypes phức tạp. Bản TypeRef tối giản trong bài chỉ đủ để trình bày **anonymous subclass trực tiếp** với kiểu cụ thể; nó chưa thực hiện giải TypeVariable, kiểm tra generic owner type, canonicalization toàn diện hoặc validation runtime tổng quát.

Đừng hứa hẹn rằng `getGenericSuperclass()` luôn là ParameterizedType: một subclass raw, một lớp trung gian không generic hoặc cấu trúc kế thừa không phù hợp có thể làm phép kiểm tra thất bại. Constructor trong ví dụ chủ động ném lỗi nếu không gặp dạng đã dự kiến.

## 13. Những giới hạn an toàn của kho

### 13.1. Không chống được alias mutable chứa dữ liệu sai kiểu

Nếu ta lưu một `ArrayList<String>` mutable, mã khác giữ tham chiếu đến cùng object có thể làm nó thay đổi; những mã raw/unchecked có thể thêm dữ liệu không đúng kiểu.

~~~java
List<String> mutable = new ArrayList<>(List.of("A"));
store.put(new TypeRef<List<String>>() {}, mutable);

// Nếu bên khác dùng raw alias để thêm Integer, bất biến bị phá.
// Cách khắc phục: sao chép và xác thực ở ranh giới cần tin cậy.
~~~

Trong bản demo, các list từ `List.of` và bản sao từ `List.copyOf` không cho sửa đổi thông thường. Nhưng kho generic tối giản không tự chuyển mọi collection caller truyền vào thành immutable.

### 13.2. TypeRef ghi lại mô tả kiểu, không là validator

Hai câu sau hoàn toàn khác nhau:

- “Tôi có token đại diện cho `List<String>`.”
- “Tôi đã xác minh giá trị runtime thực sự là List chỉ chứa String.”

Câu đầu là metadata; câu sau yêu cầu **duyệt/kiểm tra dữ liệu** hoặc đặt niềm tin vào một API có bất biến được kiểm soát.

### 13.3. Type erasure vẫn tồn tại

Trình biên dịch có thể chèn cast ở nơi nhận kết quả từ generic method `get`. Với `List<String>`, runtime có thể kiểm tra top-level List nhưng không xác minh toàn bộ các phần tử thông qua cast của caller. Lỗi có thể xuất hiện khi đọc phần tử như String.

### 13.4. Missing key và null không đồng nghĩa vấn đề kiểu

`get` trong demo trả null cho key không tồn tại, còn `put` từ chối null để tránh phân biệt hai trạng thái. Một hệ thống thực tế nên quyết định rõ hành vi khi key vắng: Optional, exception, default value hoặc API contains. Đừng dùng cast hay raw types để giải quyết bài toán missing keys.

### 13.5. Thread safety và versioning

`HashMap` trong demo **không thread-safe** khi nhiều thread ghi hoặc đồng thời đọc/ghi. Nếu kho dùng trong môi trường concurrent, cần cơ chế đồng bộ thích hợp hoặc `ConcurrentHashMap` kèm thiết kế về atomic operations và publication.

Nếu dùng Type của Reflection làm key trong dữ liệu persist, không nên mặc định `Type.getTypeName()` là một định dạng serialization ổn định qua refactor, classloader hoặc thay đổi tên package. Persistence cần schema và versioning riêng.

## 14. Các lỗi thường gặp và cách sửa

| Lỗi | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Dùng `List.class` để phân biệt List<String> và List<Integer> | Type arguments không có trong Class literal | Dùng TypeRef và `ParameterizedType` làm key. |
| Dùng raw `TypeRef` để gọi put | Mất ràng buộc T giữa token và value | Giữ `TypeRef<T>`, không ép raw. |
| Dùng token nhưng tin rằng JVM tự kiểm tra phần tử lồng nhau | Type erasure; token là metadata | Viết validator/checking adapter. |
| Dùng `Map<Type,Object>` rồi trả Object công khai | Caller phải cast thủ công | Bọc bằng phương thức generic get/put với token. |
| Lưu dữ liệu mutable rồi tin kho luôn đúng | Alias có thể thay đổi giá trị | Sao chép/đóng băng khi thích hợp; giới hạn lối ghi. |
| Dùng TypeRef như key theo identity | Hai token cùng type có thể là hai object khác nhau | Key bằng `type()` hoặc tự định nghĩa equals/hashCode. |
| Tạo `new TypeRef() {}` | Raw subclass không giữ type argument T | Viết `new TypeRef<List<String>>() {}`. |
| Capturing `List<X>` trong generic method rồi mong X biến thành String | Reflection vẫn có TypeVariable X | Cấp token cụ thể từ caller, hoặc resolve biến kiểu chính thức. |
| Muốn lưu hai giá trị khác nhau cùng List<String> | Key chỉ bằng Type bị trùng | Dùng typed key có định danh cộng Type. |
| Trộn null và missing entry | API không nêu hợp đồng null | Chọn Optional/exception/contains và thống nhất chính sách. |

### Sai kiểu được compiler phát hiện trước

Ví dụ minh họa:

~~~java
TypeRef<Map<String, List<Integer>>> matrix =
        new TypeRef<Map<String, List<Integer>>>() {};

// store.put(matrix, Map.of("x", List.of("not a number")));
// Phải sửa dữ liệu thành List<Integer>.
~~~

### Sai kiểu compiler không thể biết vì đầu vào là Object

~~~java
Object externalData = receiveFromExternalSource();

// Không được ép ngay externalData thành Map<String,List<Integer>>.
Map<String, List<Integer>> checked = checkedNested(externalData);
store.put(matrix, checked);
~~~

`receiveFromExternalSource()` là tên placeholder của một API nguồn dữ liệu, không phải chương trình Java độc lập. Điểm học tập là phải **kiểm tra và chuyển** Object trước khi gắn nó với token generic có ý nghĩa nghiệp vụ.

## 15. Hướng phát triển một kho nhiều kiểu thực tế

Tùy yêu cầu, có thể phát triển từ mẫu demo:

1. **TypedKey<T>** có id và `Type`: phân biệt các vai trò khác nhau dù cùng kiểu cấu trúc.
2. **Validator<T>** đi cùng key: kiểm tra runtime tại put, đặc biệt cho JSON, config và dữ liệu từ API ngoài.
3. **Bản sao dữ liệu/immutability**: không để alias mutable hoặc raw API phá bất biến sau khi kiểm tra.
4. **Missing-value contract**: chọn Optional, exception hoặc giá trị mặc định được định nghĩa rõ.
5. **Quy tắc kế thừa/variance**: xem rõ khi nào chấp nhận subtype, wildcard hay đúng bằng kiểu token; không dùng unchecked cast để giả lập covariance.
6. **Thread safety**: dùng đồng bộ, immutable snapshot hoặc concurrent map nếu có truy cập song song.
7. **Kiểm thử âm tính**: xem các lệnh put sai kiểu có bị compiler chặn và các nguồn dữ liệu không đúng cấu trúc có bị validator từ chối.
8. **Định dạng khi lưu trữ**: schema và codec được phiên bản hóa nếu cần ghi/đọc dữ liệu ra ngoài JVM.

Các cải tiến đó giải quyết những yêu cầu ngoài phạm vi kho demo; **TypeRef chỉ là thành phần nhận diện kiểu**, không thay thế validation, authorization, concurrency control hay persistence schema.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao kho `Map<Class<?>,Object>` không thể dùng `List.class` để phân biệt List<String> và List<Integer>?

**Đáp án:** Cả hai có cùng runtime raw class List. Tham số generic không có trong `List.class`, nên chúng tạo cùng một key.

**Câu 2.** Hai token `new TypeRef<List<String>>() {}` được tạo riêng có thể dẫn đến cùng mục lưu trong Map<Type,Object> không?

**Đáp án:** Có. Khi `type()` trả hai `ParameterizedType` mô tả cùng List<String>, chúng có thể so sánh bằng `equals` và được dùng làm cùng key. Đây không phải so sánh identity của hai wrapper TypeRef.

**Câu 3.** Vì sao `TypeRef<Map<String,List<Integer>>>` không tự bắt được phần tử String trong List<Integer> ở runtime?

**Đáp án:** TypeRef là Reflection metadata của cấu trúc kiểu. Do erasure, JVM không thực hiện kiểm tra mọi phần tử lồng nhau chỉ dựa trên TypeRef. Cần validator duyệt Map, List và từng phần tử.

**Câu 4.** Một typed store vẫn dùng Map<Type,Object>. Điều đó có mâu thuẫn với việc API là generic không?

**Đáp án:** Không. Kho cần chỗ chứa chung cho các kiểu khác nhau; các phương thức generic ràng buộc token và dữ liệu ở compile time. An toàn của cast nội bộ dựa trên bất biến ghi đúng kiểu và kiểm soát nguồn dữ liệu.

**Câu 5.** `new TypeRef<List<X>>() {}` bên trong generic method có luôn đại diện cho List<String> nếu caller sử dụng X=String không?

**Đáp án:** Không. Generic superclass có thể chứa TypeVariable X, vì mã anonymous subclass đã được biên dịch khi X chưa có kiểu cụ thể. Cần token cụ thể hoặc giải biến kiểu theo ngữ cảnh.

**Câu 6.** Làm thế nào để lưu hai danh sách List<String> có cùng kiểu nhưng vai trò khác nhau?

**Đáp án:** Tạo key kết hợp một tên/định danh và Type thay vì chỉ dùng Type. API vẫn phải giữ liên hệ giữa `TypedKey<T>` với value T.

**Câu 7.** Trong ví dụ, chuỗi “wrong” bị phát hiện ở đâu?

**Đáp án:** Trong `checkedNested` khi duyệt phần tử của List và gọi `Integer.class.cast(element)`, không phải do `get` hoặc TypeRef tự kiểm tra.

**Câu 8.** Nếu kho dùng trong multi-thread environment, thay HashMap bằng ConcurrentHashMap đã đủ để mọi vấn đề an toàn được giải quyết chưa?

**Đáp án:** Chưa. ConcurrentMap hỗ trợ truy cập đồng thời vào chính map, nhưng vẫn cần xử lý immutability của giá trị, hợp đồng key/value, validation và các thao tác nghiệp vụ nhiều bước nếu muốn atomicity.

## 17. Ghi nhớ

Một kho nhiều kiểu kiểu an toàn ở **mức API** dùng ràng buộc `<T> put(Token<T>, T)` và `<T> T get(Token<T>)` để nối kiểu key với value.

- **`Class<T>`** phù hợp với các lớp runtime có thể kiểm tra trực tiếp như String/Integer.
- **`TypeRef<T>` dựa trên `ParameterizedType`** giúp phân biệt `List<String>`, `List<Integer>` và `Map<String,List<Integer>>` như các key có cấu trúc.
- **`Map<Type,Object>`** là cơ chế lưu hỗn hợp; cast nội bộ cần một bất biến ghi đúng kiểu và không nên phơi raw storage ra caller.
- **Metadata không bằng validation**: nguồn dữ liệu lồng nhau, raw alias hoặc unchecked cast vẫn có thể phá kiểu, nên cần kiểm tra và sao chép ở ranh giới dữ liệu phù hợp.
- **Key chỉ bằng Type** chỉ lưu được một giá trị cho mỗi cấu trúc kiểu; khi cần nhiều bản ghi cùng kiểu, hãy thêm định danh mang nghĩa nghiệp vụ.

Nắm được ranh giới giữa **compile-time generic safety**, **Reflection type metadata** và **runtime validation** là điều quan trọng nhất khi thiết kế kho heterogeneous trong Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-integrated-practice-009">Lập bảng lựa chọn T, ?, extends, super và tự đánh giá tình huống</a>

<details>
<summary>Xem chi tiết</summary>

# Lập bảng lựa chọn `T`, `?`, `? extends`, `? super` và tự đánh giá tình huống

## 1. Mục tiêu: chọn cú pháp Generics từ yêu cầu dữ liệu

Khi viết một API Java, câu hỏi quan trọng không phải “nên dùng wildcard ở đâu cho nhiều?”, mà là **mối quan hệ kiểu nào cần được hợp đồng phương thức thể hiện**. Bốn lựa chọn thường gặp là:

- **`T`**: một *type variable có tên*, được khai báo ở class hoặc method và có thể xuất hiện tại nhiều vị trí để **liên kết các kiểu**.
- **`?`**: một *wildcard chưa xác định*, thường phù hợp khi code chỉ cần thao tác không phụ thuộc element type.
- **`? extends T`**: một kiểu chưa biết nhưng là subtype phù hợp của `T`, thường dùng khi **đọc dữ liệu như `T`**.
- **`? super T`**: một kiểu chưa biết nhưng là `T` hoặc supertype phù hợp, thường dùng khi **đưa giá trị kiểu `T` vào**.

Ví dụ ngắn:

```java
static void inspect(List<?> items) { ... }

static double total(List<? extends Number> items) { ... }

static void append(List<? super Integer> items) { ... }

static <T> T first(List<T> items) { ... }
```

Đây là **mẫu chữ ký minh họa**, không phải một file Java hoàn chỉnh. Cần import `java.util.List`, định nghĩa thân method và đặt trong một class.

Đối với bài thực hành tổng hợp này, hãy tập trung trả lời ba câu hỏi trước khi chọn cú pháp:

1. **Phương thức có cần ghi phần tử vào cấu trúc hay chỉ đọc?**
2. **Kiểu ở tham số này có phải liên hệ với tham số khác hoặc kiểu trả về không?**
3. **Caller cần đưa được những concrete parameterizations nào vào API?**

Chọn sai thường không gây lỗi lúc chạy ngay: nhiều trường hợp compiler từ chối lời gọi hữu ích, hoặc API trở nên quá rộng và phải cast không an toàn. Chọn đúng giúp giữ **type safety** mà caller vẫn linh hoạt.

## 2. Bảng quyết định nhanh

| Nhu cầu ở biên API | Lựa chọn thường hợp lý | Điều đảm bảo |
|---|---|---|
| Chỉ cần đọc, đếm, in hoặc xóa toàn bộ, không quan tâm element type | `List<?>` | Chấp nhận `List<String>`, `List<Integer>`, ... |
| Đọc số và gọi `doubleValue()` | `List<? extends Number>` | Mỗi phần tử đọc ra dùng được như `Number` |
| Thêm các `Integer` vào list do caller cung cấp | `List<? super Integer>` | Chấp nhận `List<Integer>`, `List<Number>`, `List<Object>` |
| Input và output cần liên kết cùng một type parameter | `<T> T first(List<T>)` | Trả type `T` được suy ra từ input |
| Chuyển phần tử từ nguồn sang đích | `<T> void copy(List<? extends T>, List<? super T>)` | Nguồn cấp T, đích nhận T |
| Trả một `List` có element type tường minh cho caller | `List<T>` hoặc `List<String>` | Caller biết kiểu chính xác theo contract |
| So sánh các object kiểu `T` bằng comparator của T hoặc supertype | `Comparator<? super T>` | Comparator tiêu thụ T |
| Chuyển các T thành các R bằng một hàm | `Function<? super T, ? extends R>` | Hàm nhận T, tạo R |
| Giới hạn type variable khai báo phải thuộc họ số | `<T extends Number>` | Bound trên type parameter |
| Chỉ đọc nested lists số | `List<? extends List<? extends Number>>` | Mỗi cấp có hướng đọc và giới hạn riêng |

**Từ khóa nhớ:** *Producer Extends, Consumer Super* — thường viết là **PECS**. Đây là mẹo chọn **wildcard tại vị trí dùng generic type**, không thay thế việc phân tích data flow.

Khi một tham số vừa phải nhận lẫn trả một `T` cụ thể, một type variable `<T>` hoặc invariant `List<T>` có thể cần thiết. Không cố đặt wildcard vào mọi chỗ.

## 3. Bảng đọc/ghi chính xác với `List`

Giả sử các list **mutable** và không có ràng buộc nghiệp vụ khác:

| Kiểu tham chiếu | Đọc phần tử an toàn như | Được `add` giá trị kiểu nào? | Có thể trỏ tới |
|---|---|---|---|
| `List<String>` | `String` | `String` | Một list có static element type `String` |
| `List<Number>` | `Number` | `Number` và subtypes phù hợp | `List<Number>` |
| `List<Object>` | `Object` | Mọi reference value | `List<Object>` |
| `List<?>` | `Object` | Chỉ `null` tùy mutability; không add giá trị non-null tùy ý | `List<String>`, `List<Integer>`, ... |
| `List<? extends Number>` | `Number` | Chỉ `null` tùy mutability; không add một `Number` tùy ý | `List<Integer>`, `List<Double>`, `List<Number>`, ... |
| `List<? super Integer>` | `Object` | `Integer` và các subtype hợp lệ của `Integer` (vì `Integer` final, chính `Integer`) | `List<Integer>`, `List<Number>`, `List<Object>` |
| `List<? super Number>` | `Object` | `Number` và các subtype của `Number`, như `Integer`, `Double` | `List<Number>`, `List<Object>` |

Cần phân biệt **“compiler cho phép lời gọi `add`”** với **“đối tượng list thực có thể chỉnh sửa”**. `List.of(...)` là unmodifiable: ngay cả `List<String>` cũng không thêm phần tử được lúc chạy. Các bảng ở trên nói về **kiểm tra kiểu tĩnh**, không hứa mọi list mutable.

`List<?>` và `List<? extends Number>` vẫn cho phép nhiều thao tác không yêu cầu cung cấp một giá trị mới của element type: `size`, `get`, `clear`, `remove` theo index hoặc `add(null)` nếu implementation cho phép. Vì vậy gọi chúng là “read-only list” là cách nói tắt không hoàn toàn chính xác; chúng chủ yếu **không cho phép thêm phần tử non-null có kiểu tùy chọn**.

## 4. Vì sao `List<Integer>` không phải `List<Number>`?

Trong Java, generic types như `List<T>` mặc định **invariant**:

```java
List<Integer> integers = new ArrayList<>();
// CỐ Ý KHÔNG BIÊN DỊCH:
// List<Number> numbers = integers;
```

Nếu phép gán bị cấm ở trên được cho phép, ta có thể gọi `numbers.add(3.14)` rồi làm ô nhiễm một list vốn cần chỉ `Integer`.

Kế thừa của phần tử **không tự lan sang** quan hệ kế thừa của hai generic instantiations:

```text
Integer <: Number <: Object

List<Integer> KHÔNG phải subtype của List<Number>
List<Integer> là subtype phù hợp của List<? extends Number>
List<Number> là subtype phù hợp của List<? super Integer>
```

Từ khóa `extends` và `super` ở **wildcard** tạo một tập những parameterized types mà method có thể chấp nhận. Nó không đổi class hierarchy thực sự.

Dùng `List<? extends Number>` khi cần **đọc như Number** từ nhiều loại list. Dùng `List<? super Integer>` khi cần **ghi Integer** vào nhiều loại list.

## 5. Ví dụ chạy được: `?`, `extends`, `super` trong cùng chương trình

Tạo tệp **`WildcardChoiceDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardChoiceDemo {

    static int count(List<?> values) {
        return values.size();
    }

    static double sum(List<? extends Number> values) {
        double result = 0;
        for (Number number : values) {
            result += number.doubleValue();
        }
        return result;
    }

    static void appendIntegers(List<? super Integer> target) {
        target.add(10);
        target.add(20);
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(1, 2, 3);
        List<Double> decimals = List.of(1.5, 2.5);
        List<Number> target = new ArrayList<>();

        appendIntegers(target);

        System.out.println(count(integers));
        System.out.println(sum(integers));
        System.out.println(sum(decimals));
        System.out.println(target);
        System.out.println(target.get(0) instanceof Number);
    }
}
```

Lệnh JDK 17+:

```bash
javac WildcardChoiceDemo.java
java WildcardChoiceDemo
```

Stdout:

```text
3
6.0
4.0
[10, 20]
true
```

Giải thích:

- `count(List<?>)` không cần biết element type, chỉ gọi `size`.
- `sum(List<? extends Number>)` đọc được mọi phần tử như `Number`, áp dụng được cho cả `List<Integer>` và `List<Double>`.
- `appendIntegers(List<? super Integer>)` được phép thêm `Integer` vào `List<Number>`.
- Phương thức không cần trả một element type cụ thể từ `target`; kết quả `get` khi nhìn qua `List<? super Integer>` chỉ bảo đảm kiểu `Object`.

Nếu `target` là `List.of(...)` unmodifiable, `appendIntegers` có thể ném `UnsupportedOperationException` dù kiểu tĩnh khớp. Trong ví dụ, `target` dùng `ArrayList`.

## 6. Khi nào cần type variable `<T>` thay vì `?`

Type variable có tên giúp **liên kết hai hoặc nhiều vị trí kiểu**.

Ví dụ:

```java
static <T> T first(List<T> values) {
    return values.get(0);
}
```

Caller:

```java
List<String> words = List.of("A", "B");
String firstWord = first(words);
```

Ở đây compiler suy luận `T = String` từ `List<String>` và cho return type `String`. Nếu thay bằng:

```java
static Object first(List<?> values) {
    return values.get(0);
}
```

method vẫn đọc đúng object nhưng caller chỉ biết return type là `Object`. Quan hệ đầu vào/đầu ra đã không còn trong signature.

**Quy tắc thực dụng:** Nếu một kiểu xuất hiện tại **chỉ một vị trí và không cần đặt tên để liên kết**, wildcard thường làm API ngắn hơn. Nếu kiểu xuất hiện ở nhiều vị trí ràng buộc lẫn nhau, `<T>` thường có giá trị.

Lưu ý tinh tế: `<T>` có thể được suy luận thành một **common supertype** của các actual arguments trong một lời gọi. Nó không đảm bảo hai runtime objects có cùng concrete class. `<T>` là một ràng buộc kiểu tĩnh, không phải phép kiểm tra runtime class equality.

## 7. Ví dụ: `<T>` giữ return type và copy dữ liệu

Tệp **`TypeVariableDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class TypeVariableDemo {

    static <T> T first(List<T> values) {
        return values.get(0);
    }

    static <T> List<T> duplicate(List<? extends T> source) {
        return new ArrayList<>(source);
    }

    public static void main(String[] args) {
        List<String> words = List.of("An", "Binh");
        List<Integer> numbers = List.of(4, 5);

        String word = first(words);
        Integer number = first(numbers);
        List<String> copy = duplicate(words);

        System.out.println(word);
        System.out.println(number);
        System.out.println(copy);
    }
}
```

Chạy:

```bash
javac TypeVariableDemo.java
java TypeVariableDemo
```

Stdout:

```text
An
4
[An, Binh]
```

Phân tích:

- Method `first` dùng `T` trong **tham số list** và **return type**.
- Method `duplicate` dùng `T` để mô tả **return list**, còn input `? extends T` cho phép một producer thuộc subtype phù hợp.
- `new ArrayList<>(source)` tạo list mới có thể thay đổi; đây là **shallow copy**, không clone phần tử.
- Nếu chỉ cần in tất cả phần tử dưới dạng `Object`, không có lý do bắt buộc khai báo `<T>`: `List<?>` đơn giản hơn.

## 8. `<T extends Bound>` không phải `? extends Bound`

Đây là nhầm lẫn quan trọng nhất:

```java
static <T extends Number> T identity(T value) {
    return value;
}
```

`<T extends Number>` **khai báo type variable T** và hạn chế T phải là `Number` hoặc subtype hợp lệ.

Ngược lại:

```java
static double total(List<? extends Number> values) {
    // ...
    return 0;
}
```

`? extends Number` **không khai báo type variable**. Nó là wildcard type argument ở chỗ dùng `List`.

So sánh:

| Cú pháp | Nằm ở đâu? | Ý nghĩa |
|---|---|---|
| `<T>` | Khai báo type parameters | Đặt tên một biến kiểu |
| `<T extends Number>` | Khai báo generic class/method | Bound trên type variable |
| `List<T>` | Chỗ dùng kiểu, T đã khai báo | Gắn element type với T |
| `List<? extends Number>` | Chỗ dùng kiểu | Một unknown element type dưới Number |
| `List<? super Integer>` | Chỗ dùng kiểu | Một unknown element type là Integer hoặc supertype |
| `<T super Number>` | Không hợp lệ trong Java | Không có lower bound syntax cho type parameter |
| `List<? extends T>` | Chỗ dùng kiểu, T đã có | Producer của T hoặc subtype |
| `List<? super T>` | Chỗ dùng kiểu, T đã có | Consumer của T hoặc supertype |

Java không cho khai báo `<T super Number>`. `super` nằm ở **wildcard type argument**, không phải cách khai báo lower bound cho type parameter.

Một type variable còn có thể có nhiều upper bounds:

```java
<T extends Number & Comparable<T>>
```

nhưng wildcard `? extends Number & Comparable<...>` không phải cách tương đương để đặt intersection bounds trong type argument Java thông thường.

## 9. Kết hợp `T`, `extends` và `super`: chuyển nguồn sang đích

Bài toán điển hình của PECS là copy các phần tử từ một collection sang collection khác. API cần đủ linh hoạt:

- `List<Integer>` → `List<Number>`: hợp lệ.
- `List<Integer>` → `List<Object>`: hợp lệ.
- `List<Double>` → `List<Number>`: hợp lệ.
- `List<Number>` → `List<Integer>`: **không** an toàn: nguồn có thể chứa Double.

Chữ ký:

```java
static <T> void transfer(
        List<? extends T> source,
        List<? super T> target) {
    for (T value : source) {
        target.add(value);
    }
}
```

Đây là một method minh họa; đặt trong class và import `List`.

Ý nghĩa:

- `T` liên kết nguồn và đích.
- Nguồn **producer**, nên `? extends T`.
- Đích **consumer**, nên `? super T`.

Compiler có thể tìm một `T` phù hợp từ các constraints của lời gọi. Cấu trúc này không yêu cầu nguồn và đích có element types giống hệt nhau.

## 10. Bài kiểm tra thực hành: di chuyển một phần tử theo PECS

Khác với append toàn bộ nguồn, phương thức sau **lấy phần tử đầu khỏi nguồn** và đưa sang đích. Đây là một bài kiểm tra tốt vì generic type đúng vẫn chưa đủ: nguồn phải thực sự hỗ trợ `remove`, đích phải hỗ trợ `add`.

Tệp **`PecsMoveDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class PecsMoveDemo {
    static <T> void moveFirst(
            List<? extends T> source,
            List<? super T> destination) {
        if (!source.isEmpty()) {
            T element = source.remove(0);
            destination.add(element);
        }
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>(List.of(1, 2, 3));
        List<Number> numbers = new ArrayList<>(List.of(0.5));
        moveFirst(integers, numbers);

        System.out.println(integers);
        System.out.println(numbers);
    }
}
```

Chạy:

```bash
javac --release 17 PecsMoveDemo.java
java PecsMoveDemo
```

Stdout:

```text
[2, 3]
[0.5, 1]
```

**Tại sao biên dịch được?** `source` cho đọc phần tử có kiểu tương thích `T`; `destination` có thể nhận `T`. Hai collection không cần cùng type argument: `Integer` được đọc để thêm vào `List<Number>`.

**Tại sao chưa đủ bảo đảm runtime?** Nếu thay `integers` bằng `List.of(1, 2, 3)`, `remove(0)` có thể ném `UnsupportedOperationException`; generic signature không cam kết mutability. Nếu `destination.add` thất bại sau khi đã xóa nguồn, thao tác không tự rollback: đây là **partial failure** mà một API chuyển dữ liệu production phải xử lý.

Thử sửa code bằng cách truyền `List<Number>` làm nguồn và `List<Integer>` làm đích. Compiler cần từ chối quan hệ ngược này; không dùng raw cast để ép thông qua.

## 11. Bảng kiểm từng tình huống producer/consumer

| Nguồn hoặc đích thực tế | API `List<? extends Number>` | API `List<? super Integer>` | Giải thích |
|---|---|---|---|
| `List<Integer>` | Chấp nhận | Chấp nhận | Integer là subtype Number và chính Integer |
| `List<Double>` | Chấp nhận | Không | Double không phải supertype của Integer |
| `List<Number>` | Chấp nhận | Chấp nhận | Number vừa bound trên, vừa supertype của Integer |
| `List<Object>` | Không | Chấp nhận | Object không phải subtype Number nhưng là supertype Integer |
| `List<String>` | Không | Không | String không thuộc quan hệ bounds này |

Đây là bảng về **type compatibility của đối số list**, không khẳng định một method dùng wildcard tương ứng có thể thực hiện tùy ý mọi thao tác trên list.

Ví dụ, API `List<? extends Number>` cho phép nhận `List<Integer>` nhưng **không được** tùy tiện gọi `add(3.14)`; vì object list có thể chỉ chấp nhận Integer.

Đối với `List<? super Integer>`, khi đọc `get` compiler chỉ bảo đảm `Object`: list thực có thể là `List<Object>`, nên không được giả định phần tử nào cũng là Integer.

## 12. `? extends` không nghĩa là “list chỉ có thể đọc”

Nói “producer extends” giúp chọn type argument nhưng không phải định nghĩa khả năng mutability toàn bộ list.

Ví dụ:

```java
static void removeEverything(List<? extends Number> values) {
    values.clear();
}
```

Đây là một method hợp lệ trong class (giả định import `List`). `clear()` không yêu cầu truyền một phần tử kiểu chưa biết, nên compiler cho phép. Tại runtime, nếu implementation là mutable `ArrayList`, method có thể xóa hết; nếu là `List.of`, có thể ném `UnsupportedOperationException`.

Các thao tác như `size`, `isEmpty`, `contains`, `get`, `clear` không cần cung cấp một giá trị mới loại unknown element. Ngược lại:

```java
// CỐ Ý KHÔNG BIÊN DỊCH:
void bad(List<? extends Number> values) {
    values.add(42);
}
```

Một `Number` bất kỳ không chắc phù hợp với concrete captured element type, chẳng hạn khi `values` thật sự tham chiếu `List<Double>`.

`null` là trường hợp đặc biệt có thể đi qua generic compile-time check của `add`, nhưng implementation có thể không hỗ trợ thêm hoặc cấm null. Không nên xây một API producer dựa trên thao tác `add(null)` trừ khi hợp đồng nghiệp vụ đòi hỏi.

## 13. `? super` không nghĩa là “mọi thứ đều có thể thêm”

Xét:

```java
List<? super Integer> target = new ArrayList<Number>();
```

Các thao tác:

```java
target.add(1);                // Hợp lệ: Integer
target.add(Integer.valueOf(2)); // Hợp lệ
Object item = target.get(0); // Hợp lệ
```

Nhưng:

```java
// CỐ Ý KHÔNG BIÊN DỊCH:
target.add(3.5);              // Double không phải Integer
Integer x = target.get(0);   // get chỉ bảo đảm Object
```

Vì sao compiler không cho đọc thành Integer? `target` có thể trỏ tới `List<Object>` với một object khác kiểu Integer đã có sẵn. Mặc dù **những giá trị chính phương thức vừa thêm** đều là Integer, các phần tử khác của list không bị hợp đồng wildcard ràng buộc là Integer.

Nếu thực sự cần đọc lại các giá trị chính mình vừa thêm như Integer, có thể giữ reference tới các object đó, hoặc thiết kế API khác; không ép toàn bộ `List<? super Integer>` sang `List<Integer>`.

## 14. Ví dụ: tác vụ vừa đọc vừa thêm và ảnh hưởng của wildcard

Tệp **`ConsumerReadBackDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class ConsumerReadBackDemo {

    static void append(List<? super Integer> target) {
        target.add(7);
        target.add(8);
    }

    public static void main(String[] args) {
        List<Object> objects = new ArrayList<>();
        objects.add("old");

        append(objects);

        Object first = objects.get(0);

        System.out.println(first);
        System.out.println(objects);
        System.out.println(objects.get(1) instanceof Integer);
    }
}
```

Biên dịch/chạy:

```bash
javac ConsumerReadBackDemo.java
java ConsumerReadBackDemo
```

Stdout:

```text
old
[old, 7, 8]
true
```

List thực là `List<Object>`, và đã chứa String trước khi thêm Integer. Vì thế method consumer chỉ nên coi `get` là `Object`.

Nếu implementation cần xử lý dữ liệu hỗn hợp, có thể dùng `instanceof` hoặc một logic validation riêng — không giả định `? super Integer` biến toàn bộ existing elements thành Integer.

## 15. Khi chỉ cần in/đếm: `?` tốt hơn type variable không cần thiết

So sánh:

```java
static <T> void printAll(List<T> values) {
    values.forEach(System.out::println);
}
```

với:

```java
static void printAll(List<?> values) {
    values.forEach(System.out::println);
}
```

Cả hai là method minh họa, cần import `List`.

Nếu `T` không xuất hiện ở kiểu trả về, không liên hệ với tham số khác và không dùng trong phần logic cần quan hệ cụ thể, **wildcard ngắn và diễn đạt đúng ý định hơn**.

Dùng `?` giúp người gọi đưa `List<String>`, `List<Integer>`, `List<LocalDate>` mà không cần method khai báo một `<T>` chỉ để trông có vẻ generic.

Nhưng đừng máy móc thay `List<T>` bằng `List<?>` nếu helper bên trong cần truyền một phần tử kiểu T sang API khác ràng buộc T. Khi có **mối quan hệ kiểu cần giữ**, type variable có tên vẫn có vai trò.

## 16. Wildcard capture: kiểm tra khả năng giữ cùng kiểu qua hai lần truy cập

Một `List<?>` có **một element type chưa biết**, không phải mặc nhiên là `List<Object>`. Nếu lấy một phần tử rồi ghi lại vào **chính list ấy**, có thể nhờ generic helper giữ mối quan hệ kiểu mà không cast.

Thay vì lặp thuật toán hoán đổi, thử yêu cầu mới: **thay phần tử cuối bằng chính phần tử đầu**, giữ nguyên kích thước list.

Tệp **`CaptureLastDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class CaptureLastDemo {
    static void copyFirstToLast(List<?> values) {
        copyCaptured(values);
    }

    private static <T> void copyCaptured(List<T> values) {
        if (values.size() >= 2) {
            T first = values.get(0);
            values.set(values.size() - 1, first);
        }
    }

    public static void main(String[] args) {
        List<String> words = new ArrayList<>(List.of("A", "B", "C"));
        List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3));

        copyFirstToLast(words);
        copyFirstToLast(numbers);

        System.out.println(words);
        System.out.println(numbers);
    }
}
```

Chạy:

```bash
javac --release 17 CaptureLastDemo.java
java CaptureLastDemo
```

Stdout:

```text
[A, B, A]
[1, 2, 1]
```

Ở ranh giới công khai, `List<?>` cho phép caller dùng list của bất kỳ element type. Trong helper, compiler **capture đúng dấu wildcard của một object list cho mỗi lời gọi**, suy luận `T` rồi cho dùng `get` và `set` dưới cùng type. Không được biến `T` thành một `String` do người viết tự đoán; `List<Integer>` cũng phải chạy an toàn.

Bài tập tự kiểm tra: (1) tại sao `Object first = values.get(0); values.set(0, first);` trực tiếp trong method nhận `List<?>` không hợp lệ? (2) nếu muốn ghi phần tử từ một list khác, điều kiện nào cần thêm? **Đáp án:** (1) `Object` không bảo đảm đúng captured element type; (2) cần type variable/wildcard ràng buộc nguồn–đích như PECS, không ghép hai `?` độc lập tùy ý.

`List.of(...)` là unmodifiable; phương thức cần `set` nên ví dụ dùng `ArrayList`. Generic capture bảo vệ **quan hệ kiểu**, không tự bảo đảm mutability hoặc chỉ số hợp lệ.

## 17. Chọn `Collections.copy` hay `addAll`: hai hợp đồng khác nhau

Cả hai có thể chuyển `Integer` vào `List<Number>` một cách type-safe, nhưng **hành vi thay đổi list** không giống nhau:

| Ý định | Lựa chọn | Điều kiện/ảnh hưởng |
|---|---|---|
| **Nối thêm** các phần tử từ nguồn | `destination.addAll(source)` | Đích phải hỗ trợ thêm; size thường tăng |
| **Ghi đè** các slot đã tồn tại | `Collections.copy(destination, source)` | Đích phải có **size ≥ source.size()** và hỗ trợ `set` |
| Chỉ dành capacity mà chưa có element | `new ArrayList<>(capacity)` | `size() == 0`; không đáp ứng `Collections.copy` với nguồn không rỗng |

Ví dụ Java 17 trong method có import `java.util.*`:

```java
List<Integer> source = List.of(7, 8);
List<Number> overwrite = new ArrayList<>(List.of(0, 0, 99));
List<Number> append = new ArrayList<>(List.of(99));

Collections.copy(overwrite, source);
append.addAll(source);

System.out.println(overwrite); // [7, 8, 99]
System.out.println(append);    // [99, 7, 8]
```

Nếu viết `List<Number> empty = new ArrayList<>(source.size());`, rồi gọi `Collections.copy(empty, source)`, generic types vẫn tương thích nhưng runtime có thể ném `IndexOutOfBoundsException` vì **capacity không phải size**. Đây là lỗi về **hợp đồng API**, không phải PECS.

Để ghi nhớ đúng thứ tự tham số, `Collections.copy(destination, source)` nhận **đích trước, nguồn sau**. Quyết định đúng cần cả hai lớp: *kiểu dữ liệu có thể chuyển an toàn không?* và *thao tác runtime cần append hay overwrite, có đủ phần tử/khả năng sửa không?*

## 18. Method `sort`: vì sao comparator dùng `? super T`?

Chữ ký của `List.sort`:

```java
void sort(Comparator<? super E> c)
```

với `E` là element type của List.

Comparator là một đối tượng **tiêu thụ hai E** để so sánh, nên comparator dành cho một supertype phù hợp vẫn có thể so các E.

Tệp **`ComparatorWildcardDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ComparatorWildcardDemo {
    record Person(String name, int age) {
    }

    static <T> void sortWith(
            List<T> values, Comparator<? super T> comparator) {
        values.sort(comparator);
    }

    public static void main(String[] args) {
        List<Person> people = new ArrayList<>(List.of(
                new Person("Binh", 21),
                new Person("An", 20)
        ));

        Comparator<Person> byName =
                Comparator.comparing(Person::name);

        sortWith(people, byName);

        System.out.println(
                people.stream().map(Person::name).toList());
    }
}
```

Chạy:

```bash
javac ComparatorWildcardDemo.java
java ComparatorWildcardDemo
```

Stdout:

```text
[An, Binh]
```

Một `Comparator<Object>` có thể có type phù hợp cho `Comparator<? super Person>`, nhưng **hợp lệ về generic type không chứng minh comparator ấy có thứ tự nghiệp vụ đúng**.

Không thay thành `Comparator<? extends T>`: comparator cần **nhận** T để `compare`, chứ không phải sản sinh ra T.

## 19. `Function<? super T, ? extends R>`: hai hướng trong một chữ ký

`Function<T, R>` **nhận T** và **trả R**. Một helper linh hoạt có thể nhận:

```java
Function<? super T, ? extends R>
```

Vì:

- Đầu vào của function là consumer của T → `? super T`.
- Đầu ra của function là producer của R → `? extends R`.

Tệp **`FunctionVarianceDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class FunctionVarianceDemo {

    static <T, R> List<R> transform(
            List<? extends T> source,
            Function<? super T, ? extends R> mapper) {

        List<R> result = new ArrayList<>();

        for (T element : source) {
            result.add(mapper.apply(element));
        }

        return result;
    }

    public static void main(String[] args) {
        List<Integer> source = List.of(2, 3);

        Function<Number, String> description =
                n -> "value=" + n.intValue();

        List<String> output = transform(source, description);

        System.out.println(output);
    }
}
```

Biên dịch/chạy:

```bash
javac FunctionVarianceDemo.java
java FunctionVarianceDemo
```

Stdout:

```text
[value=2, value=3]
```

`List<Integer>` sản sinh những phần tử tương thích với `T = Number`; `Function<Number,String>` tiêu thụ chúng; list kết quả có `R = String`.

Hợp đồng này minh họa rằng **PECS cũng áp dụng cho generic functional interfaces** — không chỉ collections.

## 20. Nested Generics: phải xét từng cấp

Có hai lớp generic:

```java
List<List<Integer>>
```

Không thể tự coi đây là `List<List<Number>>`, vì generic invariance áp dụng ở **mỗi cấp**.

Nhưng nếu API chỉ cần đọc các nhóm số, có thể dùng:

```java
List<? extends List<? extends Number>>
```

Cấp ngoài `? extends` nói “các phần tử là một dạng List phù hợp”; cấp trong `? extends Number` nói “các phần tử của từng list có thể được đọc như Number”.

Tệp **`NestedWildcardDemo.java`**:

```java
import java.util.List;

public class NestedWildcardDemo {

    static double sumAll(
            List<? extends List<? extends Number>> groups) {

        double sum = 0;

        for (List<? extends Number> group : groups) {
            for (Number value : group) {
                sum += value.doubleValue();
            }
        }

        return sum;
    }

    public static void main(String[] args) {
        List<List<Integer>> batches =
                List.of(List.of(1, 2), List.of(3));

        System.out.println(sumAll(batches));
    }
}
```

Chạy:

```bash
javac NestedWildcardDemo.java
java NestedWildcardDemo
```

Stdout:

```text
6.0
```

`List<List<Integer>>` không phải subtype của `List<List<Number>>`, nhưng phù hợp với cấu trúc wildcard nhiều cấp cho bài toán **chỉ đọc**.

Cần cẩn thận nếu muốn thêm một list mới vào outer collection: outer wildcard cũng có captured element type chưa biết, nên compiler không cho chèn tùy ý một `List<Number>`.

## 21. Return type: vì sao tránh `List<?>` khi caller cần dữ liệu typed?

Một API có thể trả:

```java
List<?> load();
```

Nếu thực sự không có schema element type, đây là cách trả trung thực: caller chỉ biết có một list phần tử chưa xác định.

Nhưng nếu producer biết danh sách là String, chữ ký:

```java
List<String> loadNames();
```

truyền nhiều thông tin hơn; caller có thể đọc phần tử như `String` mà không cast.

Với generic factory:

```java
static <T> List<T> empty() {
    return new ArrayList<>();
}
```

caller có thể viết:

```java
List<String> names = empty();
```

Compiler dựa trên target type để suy luận T.

Một `List<? extends Number>` return type có thể phù hợp nếu API chủ đích giấu subtype nhưng cho phép đọc như Number. Tuy nhiên nó gây khó khăn cho caller khi muốn thêm giá trị Number. Đừng dùng wildcard return type chỉ để chứng tỏ mã “generic hơn” nếu một result type cụ thể hoặc named type variable phù hợp hơn.

## 22. Khi nào phải dùng `T` ở nhiều input?

Xét hai biến cùng element type:

```java
static <T> void moveOne(List<T> source, List<T> target) {
    target.add(source.remove(0));
}
```

Method có thể dùng trong một class với import `List`. Nó yêu cầu hai list được xem dưới cùng một `T` cho thao tác đọc–ghi; tuy nhiên cách viết này có thể **quá chặt** nếu nguồn là `List<Integer>` và đích là `List<Number>`.

Linh hoạt hơn:

```java
static <T> void moveOne(
        List<? extends T> source,
        List<? super T> target) {
    target.add(source.remove(0));
}
```

Sự thay đổi cho phép quan hệ subtype hợp lệ mà không mất safety. Nếu mục đích là “sao chép rồi xóa một phần tử”, source phải mutable; generic type checking không bảo đảm remove thành công trên `List.of`.

**Bài học:** Dùng T để **liên kết**, nhưng xem xét wildcard quanh T để mô tả variance theo data flow.

## 23. `?` và `Object` không tương đương

Ví dụ:

```java
List<String> strings = List.of("a", "b");

List<?> unknown = strings; // Hợp lệ.

// CỐ Ý KHÔNG BIÊN DỊCH:
// List<Object> objects = strings;
```

`List<?>` chấp nhận list của một type argument bất kỳ. `List<Object>` lại là list có chính element type `Object`; nó không nhận trực tiếp `List<String>`.

Một method nhận `List<Object>` có thể thêm một `Integer`; nếu compiler cho truyền `List<String>` vào thì contract của `List<String>` sẽ hỏng. Vì vậy Java không cho phép phép chuyển ấy.

Nếu chỉ duyệt rồi in, `List<?>` là lựa chọn đúng hơn `List<Object>`. Nếu muốn một kho hỗn hợp thực sự mà code có quyền thêm `String`, `Integer`, v.v., `List<Object>` mới thể hiện đúng hợp đồng.

## 24. Không dùng raw type để “linh hoạt hơn”

Raw type:

```java
List raw = new ArrayList();
```

bỏ type arguments thay vì mô tả quan hệ kiểu linh hoạt. Nó có thể sinh warnings `rawtypes` và khiến một số method invocations trở thành unchecked.

Một thiết kế:

```java
static void show(List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

giữ compiler checking mà vẫn nhận các list element types khác nhau.

Không thay `List<? extends Number>` bằng raw `List` rồi cast từng element sang Number để tránh viết wildcard. Khi cần đọc số, bounded wildcard đã mô tả được ràng buộc tại compile time, với diagnostic tốt hơn.

Bật:

```bash
javac -Xlint:rawtypes,unchecked YourClass.java
```

để tìm các ranh giới raw/unchecked trong code thực tế. `@SuppressWarnings` không phải cách sửa quan hệ kiểu sai.

## 25. `extends`/`super` không quyết định `null` và mutability

Một `List<? super Integer>` có thể là `ArrayList<Object>` mutable hoặc một collection có thao tác thêm bị cấm. Một `List<? extends Number>` có thể có phần tử null nếu implementation cho phép.

Kiểu generic chỉ ràng buộc **element type**, không xác định đầy đủ:

- Có cho phép `null` không?
- Có cho phép thêm/xóa/set không?
- Có đồng thời nhiều threads thay đổi không?
- Có được phép sửa dữ liệu từ reference khác không?
- Có bị ràng buộc thứ tự hoặc uniqueness không?

Ví dụ `List.of(1,2)` không cho `add` vì unmodifiable, không liên quan việc nó có type `List<Integer>` hay `List<? extends Number>` khi nhìn qua một tham chiếu.

Khi viết API production, hãy ghi các điều kiện mutability/ownership/nullability **riêng** ngoài generic signature, và kiểm thử các đường thao tác tương ứng.

## 26. Bảng lựa chọn theo API Java thường gặp

| API hoặc tình huống | Chữ ký/mẫu quan trọng | Vì sao |
|---|---|---|
| `Collections.copy` | `<T> copy(List<? super T> dest, List<? extends T> src)` | Source producer, destination consumer |
| `List.sort` | `sort(Comparator<? super E>)` | Comparator nhận E để so |
| `Collection.addAll` | `addAll(Collection<? extends E>)` | Collection nguồn tạo các E |
| `Stream.map` | `Function<? super T, ? extends R>` | Hàm tiêu thụ T, sản sinh R |
| `Collections.max` (với comparator) | `<T> T max(Collection<? extends T>, Comparator<? super T>)` | Source đọc T, comparator nhận T, return T |
| Duyệt không cần quan hệ kiểu | `void inspect(Collection<?>)` | Không cần đặt tên T |
| Lấy một phần tử đúng element type | `<T> T first(List<T>)` | Input liên kết với return |
| Chuyển `List<Integer>` sang `List<Number>` | `<T> transfer(List<? extends T>, List<? super T>)` | Phù hợp quan hệ con–cha |
| Nhận một list chứa Object thật sự và thêm nhiều loại | `List<Object>` | Element type cụ thể Object |

Các chữ ký trong bảng là **mẫu rút gọn để học**. Khi thực hiện API cụ thể, nên đọc javadoc của overload đúng; ngoài Generics còn có preconditions như capacity/size, mutability, `null`, comparator contract.

## 27. Chọn cú pháp bằng 5 câu hỏi

Khi thiết kế một method, có thể tự thực hiện trình tự sau:

**Câu 1: Có cần gọi operation dựa vào element type cụ thể không?**

- Nếu chỉ `size`, `isEmpty`, logging chung: cân nhắc `<?>`.
- Nếu đọc như `Number` để gọi `doubleValue`: cân nhắc `<? extends Number>`.

**Câu 2: Có cần đưa object kiểu X vào collection do caller cung cấp không?**

- Nếu cần thêm `Integer`: `<? super Integer>` phù hợp hơn `<? extends Integer>`.
- Nếu chỉ thêm cùng element type được khai báo cụ thể: có thể dùng `List<T>`.

**Câu 3: Kiểu ở hai vị trí phải liên hệ với nhau không?**

- Input và return cùng type: khai báo `<T>`.
- Source và sink liên hệ: `<T>` kết hợp `? extends T`/`? super T`.

**Câu 4: Caller cần đưa subtype/supertype vào không?**

- Chữ ký quá chặt `List<T>` ở mọi nơi có thể loại bỏ các cuộc gọi an toàn như `List<Integer>` → `List<Number>`.
- Dùng variance thích hợp khi data flow cho phép.

**Câu 5: Điều gì nằm ngoài generic type checking?**

- Null, mutability, index, concurrency, type erasure, nested content.
- Bổ sung contract và test phù hợp.

Nên chọn chữ ký **đơn giản nhất diễn đạt đúng các quan hệ cần thiết**, không chọn dạng có nhiều wildcard nhất.

## 28. Tự đánh giá — phần A: chọn một trong bốn hướng

Với mỗi yêu cầu, hãy thử chọn từ **`T` / `?` / `? extends` / `? super`** trước khi xem đáp án.

| Mã | Tình huống | Gợi ý API cần điền |
|---|---|---|
| A1 | Nhận mọi danh sách để in số phần tử | `void count(List<...> list)` |
| A2 | Tính tổng mọi `Number` từ các list chuyên biệt | `sum(List<...> list)` |
| A3 | Cho phép caller đưa `List<Object>` để thêm `Integer` | `append(List<...> dest)` |
| A4 | Trả đúng element type của list truyền vào | `static <...> ... first(List<...>)` |
| A5 | Đưa `List<Double>` sang `List<Number>` | `transfer(List<...> src, List<...> dst)` |
| A6 | Đọc mọi list chỉ như `Object` | `inspect(List<...>)` |
| A7 | Sort `List<Person>` bằng `Comparator<Object>` phù hợp | `sort(List<T>, Comparator<...>)` |
| A8 | Hàm nhận T và tạo R, cho phép input supertype/output subtype | `Function<..., ...>` |
| A9 | Type variable chỉ chấp nhận `Number` và subtype | `static <...> ...` |
| A10 | Chuyển vị trí hai phần tử trong một `List<?>` | Helper dùng một type variable captured |

### Đáp án phần A

| Mã | Lựa chọn | Lý do ngắn |
|---|---|---|
| A1 | `List<?>` | Không liên hệ tới element type |
| A2 | `List<? extends Number>` | Đọc các phần tử như Number |
| A3 | `List<? super Integer>` | Đích nhận Integer |
| A4 | `<T> T first(List<T>)` | T liên kết input và output |
| A5 | `<T> transfer(List<? extends T>, List<? super T>)` | PECS hai phía |
| A6 | `List<?>` | Element type chưa biết, chỉ đọc Object |
| A7 | `Comparator<? super T>` | Comparator tiêu thụ T |
| A8 | `Function<? super T, ? extends R>` | Function nhận T, trả R |
| A9 | `<T extends Number>` | Upper bound ở khai báo type variable |
| A10 | `<T>` trong helper nhận `List<T>` | Capture wildcard để `set` lại cùng element type |

Tự chấm: mỗi lựa chọn đúng và có giải thích theo luồng dữ liệu được **2 điểm**, đúng nhưng chưa giải thích được **1 điểm**, sai **0 điểm**. Tối đa **20 điểm** cho phần A.

Đây là thang tự kiểm tra kiến thức, không phải kết quả từ một công cụ chấm tự động.

## 29. Tự đánh giá — phần B: có biên dịch không?

Xét các mảnh code với imports thích hợp, chạy trong một method, và giả định các biến được khai báo ngay trước khi dùng. Đánh dấu **hợp lệ** hay **không hợp lệ**, sau đó nêu lý do.

**B1**

```java
List<Integer> xs = new ArrayList<>();
List<? extends Number> ys = xs;
```

**B2**

```java
List<Integer> xs = new ArrayList<>();
List<Number> ys = xs;
```

**B3**

```java
List<Object> objects = new ArrayList<>();
List<? super Integer> sink = objects;
sink.add(10);
```

**B4**

```java
List<? super Integer> sink = new ArrayList<Number>();
Integer n = sink.get(0);
```

**B5**

```java
List<? extends Number> source = List.of(1, 2);
Number n = source.get(0);
```

**B6**

```java
List<? extends Number> source = new ArrayList<Integer>();
source.add(10);
```

**B7**

```java
List<?> any = new ArrayList<String>();
any.add(null);
```

**B8**

```java
List<?> any = new ArrayList<String>();
any.add("new");
```

**B9**

```java
List<? super Number> sink = new ArrayList<Object>();
sink.add(1.5);
```

**B10**

```java
List<? super Number> sink = new ArrayList<Integer>();
```

**B11**

```java
List<List<Integer>> batches = List.of(List.of(1));
List<? extends List<? extends Number>> readOnly = batches;
```

**B12**

```java
List<String> names = new ArrayList<>();
names.sort(Comparator.naturalOrder());
```

### Đáp án phần B và lý do

| Mã | Kết quả | Lý do |
|---|---|---|
| B1 | Hợp lệ | `Integer` nằm dưới `Number`; wildcard upper bound chấp nhận |
| B2 | Không hợp lệ | Generic invariance |
| B3 | Hợp lệ | `Object` là supertype của `Integer`; thêm Integer an toàn |
| B4 | Không hợp lệ về kiểu | `get` chỉ bảo đảm `Object`; không gán thẳng vào `Integer`. Nếu sửa thành `Object`, `get(0)` còn có thể ném `IndexOutOfBoundsException` vì list mới rỗng |
| B5 | Hợp lệ | Đọc Number từ upper-bounded wildcard |
| B6 | Không hợp lệ | Không thêm Integer tùy ý vào `? extends Number` |
| B7 | Hợp lệ về kiểu | `null` có thể được truyền cho `add` của wildcard unknown; backing `ArrayList` ở đây mutable |
| B8 | Không hợp lệ | Không thêm String non-null khi captured element type chưa biết |
| B9 | Hợp lệ | `Number` là lower bound; có thể thêm `Double` là subtype của Number |
| B10 | Không hợp lệ | `Integer` không phải supertype của `Number` |
| B11 | Hợp lệ | Upper-bounded wildcard ở cả hai cấp cho phép đọc nested lists |
| B12 | Hợp lệ | `String` có natural order, comparator có kiểu phù hợp với sort |

Câu B4 đặc biệt hữu ích: phải phân biệt **lỗi compile-time** do gán `Object` vào Integer với **lỗi runtime** do đọc index 0 của list rỗng nếu đổi kiểu biến.

Tự chấm mỗi câu đúng **1 điểm**, tổng tối đa **12 điểm**.

## 30. Tự đánh giá — phần C: sửa API sai hoặc quá chặt

### C1. Chỉ đọc nhưng chữ ký quá cụ thể

Ban đầu:

```java
static void printAll(List<Object> items) {
    for (Object item : items) {
        System.out.println(item);
    }
}
```

Vấn đề: caller không thể đưa trực tiếp `List<String>` dù method chỉ đọc.

**Đáp án:** Đổi thành `List<?>`.

### C2. Tính tổng nhưng dùng raw List

Ban đầu:

```java
static double sum(List numbers) {
    double result = 0;
    for (Object n : numbers) {
        result += ((Number) n).doubleValue();
    }
    return result;
}
```

**Đáp án ưu tiên:** `static double sum(List<? extends Number> numbers)`. Khi đó duyệt bằng `for (Number n : numbers)` để compiler bảo vệ contract.

### C3. Chỉ thêm Integer nhưng tham số đích quá hẹp

Ban đầu:

```java
static void addDefaults(List<Integer> dest) {
    dest.add(1);
}
```

Nếu muốn nhận cả `List<Number>` và `List<Object>`:

**Đáp án:** Đổi thành `List<? super Integer>`.

### C4. Cần trả lại đúng element type

Ban đầu:

```java
static Object first(List<?> items) {
    return items.get(0);
}
```

Nếu caller mong `String` khi truyền `List<String>`:

**Đáp án:** `static <T> T first(List<T> items)`.

### C5. Copy nguồn Integer vào đích Number

Ban đầu:

```java
static <T> void copy(List<T> src, List<T> dst) {
    dst.addAll(src);
}
```

Nếu muốn hỗ trợ quan hệ subtype an toàn:

**Đáp án:** `static <T> void copy(List<? extends T> src, List<? super T> dst)`.

### C6. Comparator bị đặt `extends`

Ban đầu:

```java
static <T> void sort(
        List<T> values, Comparator<? extends T> cmp) {
    // Không dùng trực tiếp được cho values.sort(cmp).
}
```

**Đáp án:** Comparator nhận T, nên dùng `Comparator<? super T>`.

### C7. Mapper bị đảo variance

Ban đầu:

```java
Function<? extends T, ? super R> mapper
```

Đây thường không phù hợp khi method muốn **đưa T vào function và đọc R ra**.

**Đáp án:** `Function<? super T, ? extends R>`.

### C8. Nested list chỉ để đọc

Ban đầu:

```java
static double sumGroups(List<List<Number>> groups) {
    // ...
    return 0;
}
```

Nếu muốn nhận `List<List<Integer>>`:

**Đáp án:** Dùng `List<? extends List<? extends Number>>` nếu chỉ cần đọc cả hai cấp.

Phần C không cần viết lại cả class; mục tiêu là lý giải **chữ ký public API**. Tự chấm 0–2 điểm cho mỗi tình huống (lựa chọn + giải thích), tối đa **16 điểm**.

## 31. Ba thử thách bổ sung không nên giải bằng cast

### Thử thách 1 — Sao chép rồi sắp xếp

Cần nhận `Collection<? extends T>`, tạo một `List<T>` và sort với `Comparator<? super T>`.

Gợi ý:

```java
static <T> List<T> sortedCopy(
        Collection<? extends T> source,
        Comparator<? super T> order) {
    List<T> result = new ArrayList<>(source);
    result.sort(order);
    return result;
}
```

Đặt method trong class và import `java.util.Collection`, `List`, `ArrayList`, `Comparator`.

Không ép `Collection<? extends T>` thành `Collection<T>`: constructor nhận producer phù hợp mà không cần cast.

### Thử thách 2 — Đổi chỗ trong `List<?>`

Yêu cầu public method nhận `List<?>` nhưng không dùng raw type/unchecked cast.

**Đáp án:** Gọi một helper `<T> void swapCaptured(List<T>, int, int)` từ wrapper `swapAny(List<?>, int, int)` như chương trình ở phần 16. Đây là wildcard capture.

### Thử thách 3 — Cần lưu các giá trị khác nhau vào một cấu trúc

Không dùng `List<? super Integer>` nếu mục tiêu là vừa lưu String vừa Integer mà element type mong muốn thật sự là Object.

**Đáp án:** `List<Object>` là chính xác nếu code có quyền thêm nhiều reference types và đọc như Object. PECS không phải giải pháp cho mọi bài toán collection.

## 32. Thang tự đánh giá tổng hợp

Ba phần trắc nghiệm và sửa API có tối đa:

| Phần | Số mục | Điểm tối đa |
|---|---:|---:|
| A. Chọn hướng cú pháp | 10 | 20 |
| B. Có biên dịch không? | 12 | 12 |
| C. Sửa chữ ký API | 8 | 16 |
| **Tổng** | **30** | **48** |

Gợi ý tự diễn giải (chỉ là rubric học tập):

- **0–20 điểm:** cần ôn lại invariance và khác biệt giữa đọc/ghi.
- **21–32 điểm:** nắm cú pháp cơ bản, cần luyện type inference và nhiều tham số.
- **33–41 điểm:** chọn đúng phần lớn tình huống; chú ý nested generics, capture, mutability.
- **42–48 điểm:** có nền tảng tốt; thử tự thiết kế API từ yêu cầu thực và review edge cases.

Điểm số không có ý nghĩa chứng chỉ; quan trọng hơn là **mỗi lựa chọn phải có lời giải thích theo data flow** và không dựa vào unchecked casts.

Để kiểm chứng, hãy đặt các ví dụ được đánh dấu **không hợp lệ** vào file thử riêng, chạy `javac` và tự đọc diagnostic. Không đưa các dòng cố ý sai vào file chương trình đã gắn stdout chạy được.

## 33. Những lỗi tư duy phổ biến

**Lỗi 1 — “Có extends thì list read-only”.** Không chính xác; các operation không nhận giá trị element type như `clear()` vẫn có thể biên dịch, tùy mutability runtime.

**Lỗi 2 — “Có super thì đọc được T”.** Không; đọc từ `List<? super T>` chỉ có kiểu an toàn chung là `Object`.

**Lỗi 3 — “`List<Integer>` là `List<Number>`”.** Sai vì invariance. Dùng `List<? extends Number>` nếu chỉ cần producer.

**Lỗi 4 — “`List<?>` chính là `List<Object>`”.** Không; wildcard chưa biết khác element type Object cụ thể.

**Lỗi 5 — “`?` làm API luôn tốt hơn `<T>`”.** Không; type variable liên kết input/output và nhiều tham số.

**Lỗi 6 — “`<T extends Number>` và `? extends Number` giống nhau”.** Không; một cái khai báo type variable, cái kia là type argument ở vị trí dùng.

**Lỗi 7 — “Có thể khai báo `<T super Integer>`”.** Không hợp lệ trong Java.

**Lỗi 8 — “`List<? super Number>` chấp nhận `List<Integer>`”.** Không; Integer không phải supertype của Number.

**Lỗi 9 — “Method thêm Integer vào `List<? super Integer>` bảo đảm mọi element đều Integer”.** Không; list có thể chứa object khác từ trước.

**Lỗi 10 — “Dùng `Comparator<? extends T>` vì comparator liên quan T”.** Sai hướng: comparator **tiêu thụ** T, nên cần `? super T`.

**Lỗi 11 — “Function đầu vào nên extends, đầu ra nên super”.** Sai theo hướng flow thông thường; dạng linh hoạt là `Function<? super T, ? extends R>`.

**Lỗi 12 — “Wildcard lồng một cấp đủ cho mọi nested generics”.** Không; phải xét variance từng lớp của `List<List<T>>`.

**Lỗi 13 — “Có thể dùng raw List rồi cast để hết lỗi compile”.** Có thể mất safety và tạo unchecked warnings/heap pollution.

**Lỗi 14 — “`List<T>` bắt buộc cùng runtime concrete class ở mọi chỗ”.** Không. Compiler có thể suy luận T là common supertype; Java generic type variables không phải runtime equality assertions.

**Lỗi 15 — “Generic signature cho biết list mutable”.** Không; `List.of` vẫn unmodifiable.

**Lỗi 16 — “`Collections.copy` tự kéo dài destination”.** Không; destination phải có đủ phần tử để thay.

**Lỗi 17 — “Wildcard capture cần unchecked cast”.** Không; generic helper có thể capture wildcard an toàn.

**Lỗi 18 — “Return type dùng wildcard là luôn linh hoạt nhất”.** Không; caller có thể cần type argument rõ để thao tác typed mà không cast.

**Lỗi 19 — “`? extends T` cho phép thêm bất kỳ subtype của T”.** Không; captured subtype thực có thể hẹp hơn giá trị được thêm.

**Lỗi 20 — “PECS bảo đảm thread safety hoặc null safety”.** Không; chúng là hợp đồng kiểu, không phải chính sách đồng thời/dữ liệu.

## 34. Checklist trước khi chốt generic API

1. Nêu rõ method **đọc**, **ghi** hay **vừa đọc vừa ghi** qua mỗi tham số.
2. Hỏi element type có cần xuất hiện ở return type hoặc tham số khác không.
3. Nếu không cần quan hệ type, cân nhắc wildcard `?`.
4. Nếu đọc dưới một upper bound, cân nhắc `? extends Bound`.
5. Nếu ghi các giá trị kiểu T vào một consumer, cân nhắc `? super T`.
6. Nếu cần liên kết input/output, khai báo `<T>` hoặc `<T, R>` rõ ràng.
7. Khi kết hợp source và sink, thử chữ ký PECS với các subtype/supertype thực tế.
8. Thử compile cả `List<Integer>`, `List<Number>`, `List<Object>` để phát hiện bounds bị đảo.
9. Không dùng `List<Object>` thay `List<?>` khi API chỉ đọc list bất kỳ.
10. Không dùng raw types để bỏ qua lỗi suy luận generic.
11. Với nested types, phân tích từng mức wildcard riêng.
12. Nếu gặp capture error, cân nhắc helper `<T>` thay vì unchecked cast.
13. Nếu dùng comparator, xác nhận chiều **consumer** của `Comparator<? super T>`.
14. Nếu dùng function, xác nhận input `super`, output `extends`.
15. Kiểm tra null, mutability, ownership và index preconditions ngoài chữ ký type.
16. Chọn return type giúp caller có đủ thông tin, tránh wildcard nếu không cần.
17. Viết bài test cho cả accepted và rejected calls ở compile-time.
18. Đọc API Java chuẩn có cùng patterns để đối chiếu.
19. Bật `javac -Xlint:unchecked,rawtypes` khi làm việc với legacy code.
20. Ghi rõ vì sao mỗi wildcard được chọn, thay vì chỉ ghi “dùng PECS”.

## 35. Tóm tắt và nguồn chuẩn

Một cách ghi nhớ nhanh là:

```text
Không quan tâm kiểu phần tử           → ?
Đọc các giá trị theo kiểu Bound       → ? extends Bound
Thêm các giá trị kiểu T vào đích      → ? super T
Liên kết input/output hoặc nhiều vị trí → <T>
```

Đừng chọn theo hình thức. Hãy **vẽ hướng dữ liệu**: đâu là nơi sản sinh giá trị, đâu là nơi nhận giá trị, và đâu là mối quan hệ kiểu phải được giữ giữa các tham số và kết quả.

Trong Java, **`T` là type variable được đặt tên; `?` là wildcard chưa xác định; `? extends` tạo góc nhìn producer, `? super` tạo góc nhìn consumer**. Generic invariance giải thích vì sao cần wildcard để cho phép subtype/supertype ở những nơi an toàn.

Bài thực hành tổng hợp nên kết thúc khi bạn có thể tự viết và giải thích các chữ ký:

```java
static void inspect(List<?> values)

static double sum(List<? extends Number> values)

static void append(List<? super Integer> destination)

static <T> T first(List<T> values)

static <T> void copy(
        List<? extends T> source,
        List<? super T> destination)

static <T, R> List<R> map(
        List<? extends T> source,
        Function<? super T, ? extends R> mapper)
```

Đây là **mẫu chữ ký minh họa**; không copy thành những declarations thiếu method bodies rồi mong biên dịch. Hãy dựa vào các chương trình hoàn chỉnh ở phần trước để thực hành.

Tài liệu Java 17 để đối chiếu:

- [JLS §4.5 — Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5): type arguments và wildcard.
- [JLS §4.4 — Type Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.4): T và upper bounds.
- [JLS §4.10 — Subtyping](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.10): quan hệ generic subtype.
- [JLS §5.1.10 — Capture Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.10): wildcard capture.
- [JLS §18 — Type Inference](https://docs.oracle.com/javase/specs/jls/se17/html/jls-18.html): inference cho generic methods.
- [Java SE 17 `java.util.Collections`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html): `copy`, `max`, `sort`.
- [Java SE 17 `java.util.List`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html): `addAll`, `sort`, `get`, `set`.
- [Java SE 17 `java.util.function.Function`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/Function.html): functional interface nhận T và trả R.
- [Java SE 17 `java.util.Comparator`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html): comparator generic và `? super T`.

</details>

- [Quay lại đầu trang](#back-to-top)
